# GPU Architecture & Roofline Analysis for LLMs

Deep-dive sub-file of [Optimization & Quantization](README.md). Covers the hardware mental model every senior AI engineer is expected to reason with: GPU memory hierarchy, the roofline model, arithmetic intensity of prefill vs decode, tensor cores and FP8/FP4, interconnect topology, and accelerator spec literacy (A100 → H100 → H200 → B200, TPUs). Economics layer: see [gpu_pool_economics.md](../case_studies/cross_cutting/gpu_pool_economics.md).

---

## 1. Concept Overview

Almost every LLM performance question — why decode is slow, why batching helps, why quantization speeds up inference, why FlashAttention exists, why H200 outperforms H100 on inference despite identical FLOPS — reduces to one model: the **roofline**. Performance is bounded by either compute (FLOPS) or memory bandwidth (bytes/s), and which bound applies depends on the workload's **arithmetic intensity** — FLOPs performed per byte moved from memory.

GPUs are extremely lopsided machines: an H100 SXM does ~989 TFLOPS of dense BF16 matrix math but moves only ~3.35 TB/s from HBM. The ratio — the **ridge point** — is ~295 FLOPs per byte. Any kernel doing fewer FLOPs per byte than that is memory-bound: the tensor cores idle while waiting for data. LLM decode does roughly 2 FLOPs per parameter byte read — two orders of magnitude below the ridge — which is the single most important number in inference engineering. Prefill, processing thousands of tokens in one pass, reuses each weight thousands of times and sits comfortably compute-bound.

This file builds that model quantitatively, walks the memory hierarchy and interconnects it depends on, and connects it to the optimizations (batching, quantization, GQA/MLA, FlashAttention, prefill/decode disaggregation) covered elsewhere in this section.

---

## 2. Intuition

> **One-line analogy**: A GPU is a stadium full of arithmetic savants fed by a single narrow conveyor belt of data — the art of LLM performance is keeping the savants busy per item the belt delivers.

**Mental model**: For each byte the conveyor (HBM) delivers, how many operations can you perform on it before needing the next byte? Multiply two large matrices and each loaded weight participates in many multiply-adds — high reuse, savants busy. Generate one token and every weight is read once for a couple of FLOPs — the belt is the bottleneck, and 99% of the compute sits idle. Every famous inference optimization is a scheme to either fatten the belt (HBM3e, KV-cache quantization), shorten the trips (FlashAttention tiling into SRAM), or batch more work per delivery (continuous batching, speculative decoding).

**Why it matters**: Interviewers separate senior candidates by whether they reason from hardware numbers or recite tool names. "Decode is memory-bound, so an H200's 4.8 TB/s gives ~1.4× decode throughput over H100 at identical FLOPS" is a hireable sentence; "vLLM makes it fast" is not.

**Key insight**: The question "is this workload compute-bound or memory-bound?" has a numeric answer you can compute on a napkin: arithmetic intensity = FLOPs / bytes, compare against ridge point = peak FLOPS / bandwidth. Memorize three numbers per chip (FLOPS, bandwidth, memory capacity) and you can derive most performance ceilings from first principles.

---

## 3. Core Principles

1. **Roofline**: attainable FLOPS = min(peak FLOPS, intensity × bandwidth). Below the ridge point you are paying for FLOPS you cannot use.
2. **Decode is a weight-streaming problem.** Each generated token reads every active parameter once (~2 FLOPs/param). Single-stream decode tokens/s ≈ bandwidth / model bytes — an *upper bound* you can compute before buying anything.
3. **Batching converts memory-bound to compute-bound.** B sequences decoding together share each weight read: intensity ≈ 2B FLOPs/byte for the GEMMs. On H100 BF16, B needs to approach ~150–300 before the GEMMs go compute-bound — which is exactly why continuous batching exists.
4. **The hierarchy spans ~5 orders of magnitude.** Registers (~TB/s aggregate, per-thread) → SRAM/shared memory (~19+ TB/s class, ~hundreds of KB per SM) → L2 (tens of MB) → HBM (TB/s, tens-to-hundreds of GB) → NVLink (~0.9–1.8 TB/s) → InfiniBand (~50–100 GB/s per NIC). IO-aware algorithms (FlashAttention) win by restructuring computation to live higher in this pyramid.
5. **Quantization is a bandwidth optimization first.** INT4 weights make decode ~3–4× faster not because INT4 math is faster but because 4× fewer bytes stream per token. Prefill (already compute-bound) speeds up far less.
6. **Interconnect dictates parallelism layout.** Tensor parallelism inserts all-reduces inside every layer → needs NVLink-class bandwidth → stays inside a node. Pipeline/data/expert parallelism communicates at layer or step boundaries → tolerates InfiniBand → goes across nodes.
7. **Attention memory traffic scales with context, not parameters.** KV cache bytes/token are fixed by architecture (layers × KV heads × head dim × 2 × precision); long contexts shift even batched decode back to memory-bound — the motivation for GQA, MLA, and KV quantization.

---

## 4. Types / Architectures — Accelerator Literacy

| Chip | BF16 dense | FP8 dense | Memory | Bandwidth | Ridge (BF16) | Interconnect |
|------|-----------|-----------|--------|-----------|--------------|--------------|
| A100 SXM 80GB | 312 TF | — | 80 GB HBM2e | 2.0 TB/s | ~156 | NVLink3 600 GB/s |
| H100 SXM | 989 TF | ~1,979 TF | 80 GB HBM3 | 3.35 TB/s | ~295 | NVLink4 900 GB/s |
| H200 SXM | 989 TF | ~1,979 TF | 141 GB HBM3e | 4.8 TB/s | ~206 | NVLink4 900 GB/s |
| B200 | ~2.25 PF | ~4.5 PF | 192 GB HBM3e | ~8 TB/s | ~280 | NVLink5 1.8 TB/s |
| GB200 (Grace+2×B200) | 2× B200 + 480GB LPDDR via NVLink-C2C | — | per above | per above | — | NVL72 rack: 72 GPUs in one NVLink domain |
| TPU v5p | 459 TF | — | 95 GB HBM | 2.76 TB/s | ~166 | ICI 3D torus, 8,960-chip pods |

(Marketing sheets often quote sparse FLOPS — 2× the dense numbers — and Blackwell adds FP4 at ~2× FP8. Always check dense vs sparse and per-GPU vs per-superchip.)

Reading the table like an engineer:
- **H100 → H200**: identical compute, +43% bandwidth, +76% capacity. Inference (decode-heavy, KV-hungry) is exactly what improves; training improves far less. This pair is the cleanest proof that LLM inference buys bandwidth and capacity, not FLOPS.
- **Memory capacity sets the fleet size before speed matters.** Llama-3.1-70B at FP16 needs ~140 GB of weights alone → 2×H100 minimum, or 1×H200, before a single token is served. MoE models (DeepSeek-V3: 671B total, 37B active) are the extreme: bandwidth cost of the *active* 37B, capacity cost of all 671B resident.
- **SRAM-first outliers**: Groq's LPU and Cerebras keep weights/activations in on-chip SRAM (~80 TB/s class), achieving extreme single-stream decode speed at the cost of needing many chips to fit a model — the roofline tradeoff taken to the opposite corner.

---

## 5. Architecture Diagrams

Memory hierarchy with real H100-class numbers:

```
            capacity            bandwidth         who lives here
 registers  ~256 KB/SM          ~100s TB/s agg    operands in flight
 SRAM/shmem ~228 KB/SM (132 SM) ~20+ TB/s class   FlashAttention tiles
 L2 cache   50 MB               ~10 TB/s class    hot KV blocks, weights en route
 HBM3       80 GB               3.35 TB/s         weights, KV cache, activations
 ─────────── chip boundary ───────────────────────────────────────────
 NVLink4    (8 GPUs/node)       900 GB/s/GPU      TP all-reduce traffic
 PCIe Gen5  host                ~64 GB/s/dir      weight loading, CPU offload
 InfiniBand cross-node          ~50 GB/s/NIC      PP/DP/EP, checkpoints
```

The roofline, annotated with LLM workloads (H100 BF16):

```
 attainable
 TFLOPS (log)
   989 ┤. . . . . . . . . . . ┌────────────────────  compute roof
       │                    ／:
       │   bandwidth      ／  :
       │   roof =       ／    :
       │   3.35TB/s × I／      :
   100 ┤            ／         :
       │          ／           :
    10 ┤        ／             :
       │      ／ ^decode B=1   : ^prefill / training GEMMs
       │    ／   (I≈1-2)       :  (I≈seq_len, thousands)
       └──┬────┬─────┬────────┬─────────┬──────> arithmetic
          1    10    100     295       1000     intensity (FLOPs/byte)
                            ridge
   batched decode slides right with batch size: I ≈ 2B
```

Node and cluster topology — why TP stays inside the box:

```
 ┌────────────────────── 8x H100 node ──────────────────────┐
 │  GPU0 ═ GPU1 ═ GPU2 ═ ... ═ GPU7    NVLink/NVSwitch       │
 │   ║ all-reduce EVERY layer (TP=8): needs 900 GB/s        │
 └───╫───────────────────────────────────────────────────────┘
     ║ InfiniBand 8×400Gb/s (~total 400 GB/s/node)
 ┌───╫───────────────────────────────────────────────────────┐
 │  node 2 ... node N: pipeline stages / data parallel /     │
 │  expert parallel — communicate only at boundaries          │
 └────────────────────────────────────────────────────────────┘
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Napkin math you should be able to do live

```python
from dataclasses import dataclass


@dataclass
class Chip:
    name: str
    flops_dense: float      # peak dense FLOPS at the precision in use
    hbm_bw: float           # bytes/s
    hbm_cap: float          # bytes

H100 = Chip("H100-SXM", 989e12, 3.35e12, 80e9)
H200 = Chip("H200-SXM", 989e12, 4.8e12, 141e9)


def ridge_point(c: Chip) -> float:
    return c.flops_dense / c.hbm_bw                  # H100 BF16: ~295 FLOPs/byte


def decode_tokens_per_s_upper_bound(c: Chip, model_bytes: float) -> float:
    """Single-stream decode ceiling: every token streams all weights once."""
    return c.hbm_bw / model_bytes
    # 70B @ FP16 (140 GB) on H100: doesn't fit -> TP=2, ~3.35e12/70e9 ≈ 48 tok/s/GPU-pair share
    # 70B @ INT4 (35 GB) on H100:  3.35e12 / 35e9  ≈ 96 tok/s  <- quantization = bandwidth win
    # 8B  @ FP16 (16 GB) on H100:  3.35e12 / 16e9  ≈ 209 tok/s


def batch_to_go_compute_bound(c: Chip, bytes_per_param: float = 2.0) -> float:
    """Decode GEMM intensity ≈ 2*B / bytes_per_param FLOPs per weight byte."""
    return ridge_point(c) * bytes_per_param / 2.0    # H100 BF16: ~295 -> B ≈ 295


def kv_bytes_per_token(layers: int, kv_heads: int, head_dim: int,
                       bytes_per_elem: float = 2.0) -> float:
    return 2 * layers * kv_heads * head_dim * bytes_per_elem   # K and V
    # Llama-3-70B (GQA): 2*80*8*128*2  = 327,680 B ≈ 320 KB/token
    # without GQA (64 KV heads):        ≈ 2.6 MB/token  -> GQA = 8x KV bandwidth/capacity
```

### 6.2 Prefill vs decode through the roofline

Prefill processes S prompt tokens in one pass: each weight loaded once serves S tokens → GEMM intensity ≈ 2S FLOPs per weight byte. At S=2,048, intensity ~4,096 ≫ 295: firmly compute-bound; time scales with FLOPs, and FP8 (doubling FLOPS) genuinely halves it. Decode emits one token per pass: intensity ≈ 2/bytes_per_param ≈ 1 — bandwidth-bound by ~300×; time scales with *bytes*, so quantization and bandwidth (H200) help, while more FLOPS does nothing. This asymmetry is why modern serving disaggregates: prefill on compute-optimized pools, decode on bandwidth/capacity-optimized pools, KV cache shipped between them (DistServe, Mooncake; see [vLLM Deep Dive](../vllm_deep_dive/README.md) and [gpu_pool_economics.md](../case_studies/cross_cutting/gpu_pool_economics.md) for the prefill/decode disaggregation economics).

### 6.3 FlashAttention as an IO argument

Standard attention materializes the S×S score matrix in HBM: O(S²) bytes written and re-read. FlashAttention tiles Q/K/V blocks into SRAM (~228 KB/SM, ~an order of magnitude faster than HBM), computes softmax incrementally (online softmax), and never writes the score matrix — same FLOPs, vastly fewer HBM bytes. It's the canonical proof that on modern GPUs *the memory traffic, not the math, is the algorithm*: a "slower" recomputing kernel beats a "minimal-FLOPs" one because the roofline says FLOPs were never the constraint. Details: [attention_mechanisms.md](../foundations_and_architecture/attention_mechanisms.md).

### 6.4 Tensor cores, FP8, FP4 — what the precision ladder buys

Tensor cores are matrix-multiply-accumulate units; each precision step down doubles their throughput and halves bytes moved: BF16 (989 TF on H100) → FP8 (~1,979 TF; E4M3 for forward/weights, E5M2's extra exponent range for gradients) → FP4 on Blackwell (~2× FP8, with microscaling/NVFP4 block-scaled formats making 4-bit usable). Two consequences worth stating precisely: (1) for *compute-bound* phases (prefill, training) lower precision buys FLOPS; for *decode* it buys bandwidth — both help, through different terms of the roofline; (2) tensor cores only deliver peak on well-shaped GEMMs — small, skinny decode-time matrices underutilize them, another reason batch size drives efficiency. Quantization formats and quality tradeoffs: [README](README.md).

### 6.5 Broken → fixed: a capacity plan

```python
# BROKEN: planning decode capacity from FLOPS
flops_per_token = 2 * 70e9                  # ~140 GFLOPs/token for a 70B
h100_tokens_per_s = 989e12 / flops_per_token   # ≈ 7,064 tok/s "per GPU"
# Plan: "one H100 serves ~7K tok/s, so 10 GPUs handle 70K tok/s"  -> off by ~100x
# for interactive traffic: decode never sees peak FLOPS; it sees the belt.
```

```python
# FIX: bound each phase by its own roofline term, then take the binding one
weights = 70e9 * 1.0                    # INT8 ≈ 70 GB (fits 1xH100? no - 80GB w/ KV: tight -> TP2)
bw = 3.35e12

single_stream = bw / weights            # ≈ 48 tok/s decode ceiling per replica
batch = 64                              # continuous batching
agg_decode = single_stream * batch      # ≈ 3,060 tok/s IF (a) KV fits: 64 seqs
kv_per_seq = 320e3 * 4096               # 320KB/token * 4K ctx ≈ 1.3 GB -> 64 seqs ≈ 84 GB KV(!)
# -> KV doesn't fit next to weights: shrink batch, quantize KV to FP8 (~42 GB),
#    or add TP. THEN check prefill pool sizing separately (compute-bound, ~989e12
#    / 140e9 ≈ 7K tok/s of prompt ingestion per GPU at high utilization).
# Real plans iterate: capacity (fits?) -> bandwidth (decode tok/s) -> compute (prefill).
```

This three-gate order — capacity, bandwidth, compute — is the reusable interview structure for any "how many GPUs do we need" question.

### 6.6 MFU and MBU — the honesty metrics

MFU (Model FLOPS Utilization) = achieved FLOPs/s ÷ peak: healthy LLM *training* lands ~35–50%; decode-heavy inference often <5% — and that is not a bug, it is the roofline. MBU (Model Bandwidth Utilization) = achieved bytes/s ÷ peak bandwidth: well-tuned decode hits 60–80%+. Reporting decode performance in MFU is a category error candidates are expected to catch — use MBU for decode, MFU for prefill/training.

### 6.7 FP8 training mechanics

§6.4 covered FP8 as an *inference*-time precision: weights (and sometimes activations) are cast to FP8 after training, purely for serving. **FP8 training** is a different, harder problem — the forward AND backward GEMMs of every training step run in FP8, for potentially hundreds of thousands of steps, where small per-step numerical error compounds and a single overflowed tensor can NaN an entire run. The roofline payoff is real (FP8 GEMMs run at ~2× the FLOPS of BF16 on H100/H200, §4's table) but capturing it without divergence requires the scaling machinery below.

```
 FP8 training data flow, one linear layer, one step (Transformer Engine pattern):

  master weights (FP32/BF16) ───────────────────────────────────┐
        │ cast w/ scale (E4M3)                                   │ optimizer.step()
        ▼                                                        │ updates MASTER
   weight_fp8 ─────────┐                                         │ weights only --
                        │  FP8 GEMM (FP32 accumulate)            │ FP8 never enters
   activation_fp8 ──────┴─────────────► output (BF16/FP32)       │ the optimizer state
        ▲                                                        │
        │ cast w/ scale (E4M3)                                   │
   activations (BF16) ── amax() ──► delayed-scaling history ─────┘
                                     (predicts NEXT step's scale,
                                      "delayed scaling" below)

  backward: grad_output cast to E5M2 (wider exponent range -- gradients
  span more orders of magnitude than activations) -> FP8 GEMM ->
  grad accumulated in FP32 -> optimizer.step() on master weights
```

**Two formats, two jobs** (introduced in §6.4): **E4M3** (4 exponent bits, 3 mantissa bits, max magnitude 448) carries forward activations and weights, where ~12.5% per-element rounding error (2^-3 mantissa) is tolerable because normalization layers keep these tensors' magnitudes roughly stable. **E5M2** (5 exponent bits, 2 mantissa bits, max magnitude 57,344) carries gradients, which — especially early in training and in layers far from the loss — can span several orders of magnitude more dynamic range than activations; E5M2 trades mantissa precision for that range.

```python
from dataclasses import dataclass, field
from collections import deque


@dataclass(frozen=True)
class Fp8Format:
    name: str
    max_magnitude: float       # largest finite |value| (OCP FP8 spec)
    mantissa_bits: int


E4M3 = Fp8Format("E4M3", max_magnitude=448.0, mantissa_bits=3)     # fwd activations, weights
E5M2 = Fp8Format("E5M2", max_magnitude=57344.0, mantissa_bits=2)   # gradients
```

**Per-tensor scaling.** FP8's representable range (E4M3: roughly 2^-9 to 448) is far narrower than FP32's (~1e-38 to 1e38). Every tensor cast to FP8 carries a separate FP32 **scale factor** `s`, chosen so the tensor's largest element (`amax`) lands near the format's max — using the full mantissa range instead of clustering near zero (underflow) or clipping (overflow):

```python
def compute_scale(amax: float, fmt: Fp8Format, margin: float = 0.9) -> float:
    """Choose s so amax/s sits at `margin` of fmt.max_magnitude -- the 10%
    headroom absorbs amax growth before the next recalibration (delayed
    scaling, below)."""
    if amax <= 0.0:
        return 1.0
    return amax / (fmt.max_magnitude * margin)


def to_fp8(values_fp32: list[float], scale: float, fmt: Fp8Format) -> list[float]:
    """values_fp8 = round(values_fp32 / scale), clamped to +/- fmt.max_magnitude.
    `scale` travels with the FP8 tensor for dequantization downstream."""
    bound = fmt.max_magnitude
    return [max(-bound, min(bound, round(v / scale))) for v in values_fp32]
```

**Delayed scaling (amax history).** Computing `amax` for the CURRENT tensor before casting it requires a full reduction over that tensor — a synchronization point inserted into every GEMM. **Delayed scaling** instead predicts this step's scale from a rolling window of *past* amax values (Transformer Engine defaults to a 1024-step history), so the cast and the GEMM proceed without waiting on the current tensor's reduction:

```python
@dataclass
class DelayedScalingState:
    fmt: Fp8Format
    margin: float = 0.9
    history: deque[float] = field(default_factory=lambda: deque(maxlen=1024))

    def current_scale(self) -> float:
        """Scale for THIS step, predicted from amax values seen so far."""
        if not self.history:
            return 1.0
        return compute_scale(max(self.history), self.fmt, self.margin)

    def record(self, observed_amax: float) -> None:
        """Called AFTER this step's tensor is available -- feeds the
        NEXT step's prediction."""
        self.history.append(observed_amax)
```

The "one step behind" tradeoff: if a tensor's magnitude jumps sharply between steps (a loss spike, a learning-rate warmup discontinuity), the predicted scale can be too small for the new amax — `to_fp8` clamps the overflow rather than producing `inf`, but the clamped values are wrong, and enough clamped elements visibly perturbs the loss for that step. The `margin=0.9` headroom (use only 90% of the format's range) exists specifically to absorb modest step-to-step amax growth without clamping.

**Microscaling / MXFP8.** A single scale factor per tensor assumes that tensor's elements share roughly one magnitude. MoE models violate this routinely: a layer's activation tensor mixes tokens routed to many experts, and different experts' activations can differ by 10-100x in magnitude. **Microscaling** (the OCP Microscaling spec; MXFP8 on Blackwell) assigns one scale factor per small block — typically 32 elements — instead of one per tensor:

```python
def per_block_scales(block_amaxes: list[float], fmt: Fp8Format, margin: float = 0.9) -> list[float]:
    """One independent scale per block (commonly 32 elements, MXFP8) -- a
    block with a small amax gets its own small scale, instead of being
    forced to share a large scale dictated by some OTHER block's amax."""
    return [compute_scale(amax, fmt, margin) for amax in block_amaxes]
```

The cost is bookkeeping: 8× more scale factors for 32-element blocks vs. one per 256-element tensor, consumed by the GEMM kernel per block. Blackwell's tensor cores natively support block-scaled FP8/FP4 operands, making the bookkeeping a hardware feature rather than a software tax.

**Mixed-precision recipe.** FP8 training reuses the "master weights" idea from BF16 mixed-precision training, one level deeper: the optimizer's weights and momentum/variance state stay in FP32 or BF16 — FP8 casts of the weights are produced fresh each step for the GEMM, used, and discarded. FP8's per-element rounding error therefore never accumulates in the optimizer state across steps; only the GEMM's *inputs* are low-precision, while the GEMM's *accumulation* (the sum over the reduction dimension) happens in FP32 regardless of input precision — tensor cores accumulate in FP32 even for FP8×FP8 inputs.

**Transformer Engine** (NVIDIA) is the library most teams use rather than hand-rolling the above: `te.Linear`, `te.LayerNormLinear`, etc. are drop-in replacements for their PyTorch equivalents that manage per-tensor delayed-scaling state internally, defaulting to E4M3 forward / E5M2 backward with a 1024-step amax history — the mechanics above describe what runs inside those modules.

**DeepSeek-V3** (671B total / 37B active parameters) reported training with FP8 GEMMs for the large majority of compute, using **fine-grained scaling** beyond simple per-tensor: 128×128 tile-wise scales for weights and 1×128 (per-token, per-channel-group) scales for activations — a middle ground between per-tensor and full microscaling, chosen to fit H800's reduced NVLink bandwidth (connecting to §7's interconnect-constrained training framing). Reported relative loss difference versus a BF16 baseline: under 0.25% — the most-cited public evidence that careful scaling closes FP8 training's historical stability gap at frontier scale.

**Divergence pitfalls** — BROKEN: a single global per-tensor scale, recalibrated only every 1024 steps with no margin (`margin=1.0`), on a model with MoE routing. For most steps this is fine; periodically, a step routes an unusually large fraction of tokens to one expert, that expert's activation amax spikes 50-100× above its recent history, `to_fp8` clamps a large fraction of that tensor's elements to `fmt.max_magnitude`, and the clamped GEMM output corrupts the loss for that step — visible as a sharp, intermittent loss spike that's hard to reproduce because it depends on the routing decisions of that specific step's batch.

FIX: (1) `margin=0.9` (used throughout the code above) absorbs moderate amax growth without clamping; (2) per-block scaling (microscaling) for the MoE activation tensors specifically, so one expert's outlier amax doesn't dictate the scale for all experts' activations in the same tensor; (3) keep gradients in E5M2 (wider range) even when forward tensors use E4M3, since gradient distributions are the most prone to sudden multi-order-of-magnitude shifts. All three are defaults in Transformer Engine's MoE-aware FP8 recipes and DeepSeek-V3's fine-grained scheme — the fix is "use the finer-grained scaling the library already provides for this case," not a new algorithm.

---

## 7. Real-World Examples

- **FlashAttention (Dao et al.)** — IO-aware exact attention; the founding example of roofline-driven kernel design; now the default in every serving stack.
- **vLLM PagedAttention** — KV cache paging targets *capacity* fragmentation (the third resource after FLOPS and bandwidth), lifting achievable batch size and therefore decode intensity.
- **DeepSeek-V3/R1 on H800** — export-control parts with cut NVLink bandwidth; DeepSeek's DualPipe and FP8 training, plus MLA's compressed KV (shrinking cache ~93% vs MHA), are explicit engineering around interconnect and bandwidth ceilings — the most instructive public case of roofline-constrained frontier training.
- **H200/GB200 product positioning** — NVIDIA markets H200 as an inference part on bandwidth/capacity alone (same FLOPS as H100); GB200 NVL72 puts 72 GPUs in one NVLink domain specifically so trillion-parameter MoE inference can run expert-parallel without touching InfiniBand.
- **Groq LPU** — weights in SRAM across hundreds of chips; ~500+ tok/s single-stream on 70B-class models by buying the top of the memory pyramid; the cost structure (chips scale with model size, not traffic) shows the same roofline from the opposite side.
- **Prefill/decode disaggregation (DistServe, Mooncake/Kimi)** — production architectures that schedule the two phases on separate pools because their rooflines differ; Mooncake reports significant goodput gains from KV-centric scheduling.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Inference chip | H200/B200 (bandwidth, capacity) | H100 (cheaper, compute-equal) | Decode-heavy vs prefill-heavy mix |
| Throughput lever | Bigger batch (free until compute-bound) | More replicas | Latency SLO — batch raises per-token latency |
| Bytes reduction | Weight quant INT4 (decode ~2-4x) | KV quant FP8 (long-context relief) | Where the bytes actually are at your context length |
| Parallelism | TP (intra-node, NVLink-bound) | PP/EP (cross-node, IB-tolerant) | Model size vs node memory; interconnect tier |
| Latency extreme | SRAM-first hardware (Groq/Cerebras) | GPU + speculative decoding | $/token at your traffic shape |
| Phase scheduling | Disaggregated prefill/decode pools | Unified continuous batching | Scale: disaggregation pays off at high QPS |
| Precision | FP8/FP4 (FLOPS + bytes) | BF16 (quality headroom, simplicity) | Eval-verified quality budget |

---

## 9. When to Use / When NOT to Use (Roofline Reasoning)

**Reach for roofline analysis when:**
- Sizing capacity ("how many GPUs for X tok/s at Y ms/token") — the three-gate order: capacity → bandwidth → compute.
- Choosing hardware (H100 vs H200 vs B200 vs TPU) — match the chip's strong axis to your binding constraint.
- Evaluating an optimization claim — "kernel X is 3× faster": on what intensity regime? A decode win says nothing about prefill.
- Debugging utilization — low MFU during decode is physics; low MFU during *prefill* is a real bug (bad shapes, host bottlenecks, scheduling gaps).

**Its limits — do NOT apply blindly when:**
- The bottleneck is above the kernel: scheduler gaps, tokenization, network/streaming overhead, or Python host code dominate many real services — profile end-to-end first.
- Latency, not throughput, is the SLO — roofline bounds throughput; TTFT involves queueing and prefill scheduling that the roofline alone doesn't capture.
- Communication-bound regimes — TP all-reduce or expert all-to-all can bind before HBM does; the "roofline" then needs the interconnect as its bandwidth term.
- Power/thermal caps in dense racks throttle below datasheet peaks — derate before promising datasheet math to capacity planning.

---

## 10. Common Pitfalls

1. **Planning decode from FLOPS** (the §6.5 broken example) — overestimates capacity by ~two orders of magnitude; the most common error in candidate answers and in real capacity docs alike.
2. **Quoting sparse/marketing FLOPS.** Datasheets headline 2:4-sparsity numbers (2× dense) and, for Blackwell, FP4; comparing one chip's sparse figure to another's dense figure corrupts every downstream estimate. State precision and density with every number.
3. **Forgetting the KV cache in memory budgets.** Weights fit, ship it — then batch×context KV (320 KB/token × 64 seqs × 4K ctx ≈ 84 GB on a 70B) evicts the plan. KV is the line item that grows with *traffic*, not model choice.
4. **Benchmarking batch=1 and extrapolating.** Single-stream decode showcases bandwidth; production throughput lives at batch 32–256 where the regime shifts. Vendors exploit this both directions (Groq demos vs GPU fleet economics).
5. **TP across nodes.** Tensor parallel's per-layer all-reduces over InfiniBand (50 GB/s) instead of NVLink (900 GB/s) crater throughput; war stories of 5–10× regressions from one wrong deployment flag are common. TP inside the NVLink domain; PP/DP/EP across.
6. **Low decode MFU treated as a bug.** Teams burn weeks "optimizing" 3% MFU decode that is already at 75% MBU — i.e., near-optimal. Wrong metric, wasted sprint. Conversely, 30% MBU decode *is* a real bug (fragmentation, bad kernels, host gaps).
7. **Ignoring power/thermals.** 700W×8 H100s per node: dense racks hit facility limits and throttle; sustained ≠ datasheet. Derate ~10–15% in plans, more in air-cooled DCs.
8. **Assuming quantization helps everywhere equally.** INT4 weights barely move prefill (compute-bound) and can *hurt* quality-sensitive long-form tasks; the win is decode bandwidth. Eval per phase, per task — see [README](README.md) for quality methodology.
9. **FP8 training with coarse, no-margin scaling on MoE models.** A single per-tensor scale recalibrated every 1024 steps with `margin=1.0` works fine until one step's routing sends an outsized fraction of tokens to one expert — that expert's activation amax spikes 50-100× above its history, the stale scale clamps a large fraction of the tensor, and the loss spikes that step (§6.7). The fix is finer-grained scaling (per-block/microscaling for MoE activations, §6.7), not abandoning FP8.

---

## 11. Technologies & Tools

| Tool | Role |
|------|------|
| NVIDIA Nsight Systems / Nsight Compute | Timeline + kernel-level roofline profiling (it literally plots the roofline) |
| PyTorch Profiler + Holistic Trace Analysis | Framework-level kernel/host gap analysis |
| nvidia-smi / DCGM | Fleet telemetry: SM occupancy, memory BW counters, power, thermals |
| FlashAttention / FlashInfer | IO-aware attention kernels |
| Transformer Engine | FP8 training: delayed-scaling state, E4M3/E5M2 management, drop-in `te.Linear` (§6.7) |
| vLLM / SGLang / TensorRT-LLM | Serving stacks embodying these optimizations (paged KV, continuous batching, FP8) |
| NCCL / nccl-tests | Interconnect bandwidth validation (all-reduce busbw) before blaming the model |
| Triton / CUTLASS | Writing roofline-conscious custom kernels |
| XLA / Pallas | The TPU-side equivalents |

---

## 12. Interview Questions with Answers

**Q1: Why is LLM decode memory-bound while prefill is compute-bound?**
Arithmetic intensity. Decode emits one token per forward pass: every active parameter is read once for ~2 FLOPs, so intensity ≈ 1–2 FLOPs/byte — versus an H100 ridge point of ~295. The GPU streams 70+ GB of weights per token while tensor cores idle. Prefill pushes the whole prompt through at once: each loaded weight serves S tokens, intensity ≈ 2S, and at S in the thousands the workload sits firmly against the compute roof. One mechanism, two regimes — and every serving optimization (batching, quantization, disaggregation) is an attempt to move decode rightward on the roofline or fatten its bandwidth roof.

**Q2: Compute the decode throughput ceiling for a 70B model on one H100.**
Single-stream decode ≈ bandwidth ÷ model bytes. H100: 3.35 TB/s. 70B at FP16 is 140 GB — doesn't fit in 80 GB, so TP=2 across two H100s: aggregate 6.7 TB/s ÷ 140 GB ≈ 48 tok/s. Quantize to INT4 (35 GB, fits one GPU): 3.35 TB/s ÷ 35 GB ≈ 96 tok/s single-stream. Real systems hit 60–80% of these ceilings (MBU); batch B multiplies aggregate throughput until intensity 2B approaches the ridge or KV capacity runs out. Showing this 30-second calculation, with the fits-in-memory check first, is precisely what the question screens for.

**Q3: What is the ridge point, and what are the numbers for H100?**
The intensity at which a kernel transitions from memory-bound to compute-bound: peak FLOPS ÷ peak bandwidth. H100 SXM BF16: 989 TF ÷ 3.35 TB/s ≈ 295 FLOPs/byte; at FP8 (~1,979 TF) it doubles to ~590 — lower precision raises the bar for being compute-bound even as it speeds both regimes. Workloads below the ridge get min(peak, I×BW); above it, the compute roof. Corollary worth volunteering: H200 keeps H100's FLOPS but lifts bandwidth to 4.8 TB/s, *lowering* the ridge to ~206 — more workloads become compute-bound, which is another way of saying it is an inference chip.

**Q4: MFU vs MBU — which do you report for which workload?**
MFU = achieved/peak FLOPS: meaningful for compute-bound phases — training (healthy: ~35–50%) and prefill. MBU = achieved/peak memory bandwidth: the honest metric for decode, where 60–80% is good and MFU of 2–5% is *expected physics*, not inefficiency. The interview trap is being shown "our inference MFU is 4%, help us fix it": the senior answer asks for the phase split and MBU first — if decode MBU is already 75%, the fix is batching/quantization/economics, not kernels. Using one number for both phases is how teams chase phantom optimizations.

**Q5: Size the KV cache for Llama-3-70B and explain why GQA exists.**
Per token: 2 (K and V) × layers × KV heads × head dim × bytes = 2×80×8×128×2 ≈ 320 KB at FP16. A 4K-context sequence: ~1.3 GB; batch 64: ~84 GB — more than the weights' share of an 80 GB card. Without GQA (64 query heads each with their own KV), it would be 8× that: ~2.6 MB/token. GQA shares one KV head across 8 query heads, an 8× cut in both KV capacity *and* the bandwidth to stream it during attention — it is a roofline optimization wearing an architecture costume. MLA (DeepSeek) compresses further (~93% vs full MHA) by caching a low-rank latent instead of full K/V. Whoever controls KV bytes controls long-context economics.

**Q6: Why does batching increase throughput so dramatically, and what stops it?**
Batched decode shares each weight read across B sequences: GEMM intensity rises from ~2 to ~2B FLOPs/byte, sliding the workload up the bandwidth roof — throughput grows almost linearly in B while per-step time stays flat (you were idle on compute anyway). Three things end the party: (1) intensity reaches the ridge (~B of 150–300 on H100 BF16) and you go compute-bound; (2) KV capacity — each sequence carries its cache (Q5), and HBM runs out long before the ridge at long contexts; (3) latency SLOs — batching trades per-token latency for throughput. Continuous batching (vLLM) exists to hold effective B high despite ragged sequence lengths; attention over per-sequence KV remains memory-bound even when the GEMMs aren't, which is why long-context batched decode still hungers for bandwidth.

**Q7: Why does INT4 quantization speed up decode ~3–4× but barely move prefill?**
Decode time ≈ bytes streamed ÷ bandwidth; INT4 cuts weight bytes 4× so the ceiling rises ~4× (realized ~3× after dequant overhead and the unquantized KV/activations). Prefill is compute-bound: its time is FLOPs ÷ FLOPS, and most INT4 schemes dequantize to BF16 for the actual GEMM — the FLOPs don't change, so prefill is nearly flat. If you want prefill faster, change the *compute* term: FP8 tensor-core GEMMs (2× FLOPS). This phase asymmetry is the single most diagnostic quantization question interviewers ask; the follow-up — "so what does quantization do to TTFT vs inter-token latency?" — answers itself once the regimes are stated (TTFT ≈ prefill: little change; ITL ≈ decode: big win).

**Q8: Why does tensor parallelism stay within a node while pipeline parallelism crosses nodes?**
TP splits every weight matrix, requiring an all-reduce of activations *inside every layer* — ~2 collectives per transformer block, latency-critical and bandwidth-heavy, feasible only on NVLink/NVSwitch (900 GB/s/GPU on H100 nodes). PP communicates once per stage boundary (a single activation tensor per microbatch) and DP once per step (gradients, overlappable) — both tolerate InfiniBand's ~50 GB/s/NIC. Hence the standard layout: TP ≤ 8 inside the NVLink domain, PP/DP/EP across nodes. The modern footnote: GB200 NVL72 extends one NVLink domain to 72 GPUs precisely to let MoE expert-parallel all-to-all — the most interconnect-hostile pattern — stay on NVLink; and DeepSeek's H800 work shows the inverse, heroic software (DualPipe) compensating for a cut-down interconnect.

**Q9: Explain FlashAttention through the roofline.**
Standard attention writes the S×S score matrix to HBM and reads it back for softmax and the V product: O(S²) HBM traffic at intensity far below the ridge — memory-bound, quadratically so. FlashAttention computes the same exact math but tiles Q/K/V into SRAM (~228 KB/SM, an order of magnitude faster than HBM), maintains a running (online) softmax so the score matrix never materializes, and recomputes tiles in the backward pass rather than storing them. FLOPs slightly *increase*; HBM bytes collapse; wall-clock improves 2–4×. It is the cleanest proof of the section's thesis: when you're below the ridge, the algorithm that minimizes *bytes* beats the algorithm that minimizes *FLOPs* — "IO-aware" is the design axis.

**Q10: H100 vs H200 vs B200 — same family, when does each win?**
H100→H200 changes only memory: 80→141 GB and 3.35→4.8 TB/s at identical FLOPS — so decode-heavy inference gains up to ~1.4×, big models/long contexts fit with less TP (a 70B FP16 fits one H200), and pure training gains little. B200 raises everything — ~2.3× FLOPS, 192 GB, ~8 TB/s, NVLink5 1.8 TB/s, plus FP4 — winning outright but at platform cost (power, cooling, new racks). Decision shape: prefill/training-bound → FLOPS chips (H100 fine, B200 best); decode/long-context-bound → bandwidth-capacity chips (H200 is the cheapest unit of decode); trillion-scale MoE serving → NVL72-class fabric is the feature, not the chip. Citing the H100/H200 identical-FLOPS fact is the quickest way to demonstrate spec literacy.

**Q11: Why are MoE models a memory-capacity play, and what does that do to serving topology?**
DeepSeek-V3: 671B parameters total, ~37B active per token. Per-token bandwidth cost ≈ the 37B active (decode like a 37B dense model) — but *all* 671B must sit in HBM because routing picks different experts per token. At FP8 that's ~671 GB: 5+ H200s of capacity for the FLOPs/bandwidth profile of a midsize model. So MoE shifts the binding constraint from bandwidth to capacity and interconnect: experts shard across GPUs (expert parallelism), and each token's hidden state must reach its experts and return — an all-to-all every MoE layer, which is why MoE serving wants the biggest possible NVLink domain (NVL72) and why batch size also fights expert load-balance. The roofline still applies; the bandwidth term just moved to the fabric.

**Q12: What does speculative decoding look like through the hardware lens?**
Decode wastes compute — the belt is full, the savants idle. Speculative decoding spends that idle compute: a small drafter proposes k tokens, the target model verifies all k in *one* forward pass — one streaming of the weights amortized over up to k accepted tokens, raising effective intensity roughly by the acceptance count. Bandwidth per *accepted* token drops, single-stream latency improves 2–3× at high acceptance. The hardware-aware caveats: at large batch the GPU is already near compute-bound, so speculation's benefit shrinks (and can go negative — verify FLOPs aren't free anymore); and drafter quality is workload-dependent, so measure acceptance rate per domain. It is the mirror image of batching: batching adds work per byte from *other users*; speculation manufactures it from a draft model.

**Q13: Your nodes show 95% "GPU utilization" but throughput is poor. Diagnose.**
`nvidia-smi` utilization only means "a kernel was resident" — it counts memory-stall time as busy. Real diagnosis: (1) phase split — decode-dominant? then check MBU (DCGM DRAM-bandwidth counters): if 60–80%, the node is at its physical ceiling and the fix is fleet-level (batching, quantization, more bandwidth per dollar); (2) if MBU is *also* low, profile the timeline (Nsight Systems) for host gaps — Python scheduling, tokenization, synchronous sampling between steps — and kernel quality (small/odd GEMM shapes missing tensor cores); (3) check collectives — TP misconfigured across nodes shows as long NCCL kernels (run nccl-tests busbw to confirm fabric health); (4) check power/thermal throttling (DCGM clocks vs rated). The senior signal is refusing to treat "GPU util" as evidence of anything.

**Q14: Why do Groq and Cerebras achieve extreme decode speed, and what's the catch?**
They relocate weights to the top of the memory pyramid: SRAM at tens of TB/s instead of HBM at ~3–8 TB/s. Decode's ceiling (BW ÷ model bytes) jumps an order of magnitude — hence 500+ tok/s single-stream on 70B-class models. The catch is capacity economics: SRAM is ~hundreds of MB per chip, so the model shards across hundreds of chips that must all be lit regardless of traffic — cost scales with *model size*, while GPU fleets scale with *traffic* and amortize via batching. So SRAM-first wins where single-stream latency is the product (interactive agents, voice) and loses on bulk $/token at scale; it's the same roofline, with capital allocated to the bandwidth term instead of the capacity term.

**Q15: Walk me through sizing a deployment: 50M tokens/hour generated, p50 inter-token latency <30ms, Llama-3.1-70B.**
Gate 1 — capacity: 70B FP8 ≈ 70 GB weights; choose H200 (141 GB) → ~60 GB headroom for KV; at 320 KB/token (FP16 KV) and ~3K average context ≈ 0.96 GB/seq → ~55 concurrent sequences per replica, more with FP8 KV (~110). Gate 2 — bandwidth: single-stream ceiling 4.8 TB/s ÷ 70 GB ≈ 68 tok/s → 15ms/token at MBU≈1; at realistic 70% MBU ≈ 21ms — meets the 30ms SLO with margin even before speculation. Aggregate per replica at batch ~48: ~48 × (4.8e12/70e9) × 0.7 ≈ 2,300 tok/s ≈ 8.3M tok/hr. Gate 3 — demand: 50M/hr ÷ 8.3M ≈ 6 replicas → ~7–8 H200s with N+1 and prefill pool (sized separately against *prompt* token volume, compute-bound math). Then state what napkin math omits: traffic peaks vs averages, TTFT SLO driving the prefill pool, KV precision choice, and that the whole plan gets validated with a load test before anyone signs a PO. The structure — capacity, bandwidth, compute, then demand division — matters more than the exact constants.

**Q16: FP8 *training* uses two different formats — E4M3 for activations/weights, E5M2 for gradients. Why not just pick one and use it everywhere?**
Because activations/weights and gradients have different precision-vs-range needs, and the two FP8 formats trade one for the other (§6.7). E4M3 (3 mantissa bits, max 448) gives ~12.5% per-element precision over a narrower range — fine for activations and weights, whose magnitudes normalization layers keep roughly stable step to step. E5M2 (2 mantissa bits, max 57,344) gives a much wider range at ~25% per-element precision — needed for gradients, which routinely span several more orders of magnitude than activations, especially early in training and in layers far from the loss. Using E5M2 everywhere wastes precision on activations/weights (slower convergence for no range benefit); using E4M3 for gradients risks clamping the rare large gradient values that *also* tend to be the ones carrying the most learning signal. The split is a direct application of "use the cheapest representation that doesn't clip the tensor's actual distribution," applied per tensor role rather than globally.

**Q17: What specific failure mode does "delayed scaling" introduce, and how do production FP8 training recipes mitigate it?**
Delayed scaling predicts this step's FP8 scale factor from a window of *past* amax values (Transformer Engine: last 1024 steps) rather than the current tensor's exact amax — avoiding a synchronizing reduction before every cast (§6.7). The failure mode is a one-step lag: if a tensor's magnitude jumps sharply between steps (a loss spike, an MoE routing imbalance sending a disproportionate share of tokens to one expert), the predicted scale is too small for the new amax, `to_fp8` clamps the overflowing elements, and the corrupted GEMM output perturbs the loss for that step — intermittently and batch-dependently, which makes it hard to reproduce. Production mitigations: a `margin` (Transformer Engine defaults around 0.9, i.e., target only 90% of the format's range, leaving headroom for amax to grow one step before clamping), per-block/microscaling for tensors prone to heavy-tailed magnitude distributions across blocks (MoE activations), and E5M2 specifically for gradients, whose distributions are the most prone to sudden multi-order-of-magnitude shifts. DeepSeek-V3's 128×128/1×128 fine-grained scaling is the production-scale instance of "go finer-grained where delayed scaling's lag is most likely to bite."

---

## 13. Best Practices

1. **Memorize three numbers per chip you operate** — dense FLOPS (per precision), HBM bandwidth, HBM capacity — and derive ceilings before benchmarking.
2. **Always classify the phase first** — prefill/training questions are FLOPs questions; decode questions are bytes questions; never let one metric speak for both.
3. **Plan capacity in three gates**: fits-in-memory → bandwidth ceiling → compute ceiling, then divide demand; validate with a load test.
4. **Report MBU for decode, MFU for prefill/training** — and put both on the fleet dashboard next to "GPU utilization" to keep the latter honest.
5. **Budget KV cache as a first-class line item** — it scales with batch×context and routinely exceeds weight memory in production plans; decide its precision (FP8 KV) deliberately.
6. **Keep TP inside the NVLink domain**; verify fabric health with nccl-tests before debugging "model slowness".
7. **State precision and sparsity with every FLOPS figure** — dense-vs-sparse confusion corrupts plans silently.
8. **Derate datasheet numbers 10–15%** for power/thermal reality; more for air-cooled racks.
9. **Match the chip to the binding constraint** — bandwidth/capacity parts (H200) for decode-heavy serving; FLOPS parts for training/prefill; big NVLink domains for MoE.
10. **Profile end-to-end before kernels** — most "GPU problems" found in production are host gaps, scheduling, or misconfigured parallelism, all visible in a timeline trace and invisible in a kernel benchmark.

---

## 14. Case Study

**Scenario**: A startup serves a fine-tuned Llama-3.1-70B chat product on 16×H100 (2 nodes). Decode-heavy traffic (avg 250 prompt / 600 completion tokens). Symptoms: p50 inter-token latency 85ms (SLO: 40ms), fleet "GPU utilization" 92%, finance asking why 16 GPUs serve only ~14M tokens/day.

**Investigation (roofline-driven)**:
1. Phase split from gateway logs: 81% of GPU-seconds in decode. So the governing metric is MBU, not the 92% utilization screenshot.
2. DCGM bandwidth counters: decode MBU ≈ 31% — *far* below the 60–80% healthy band. This is the rare case where decode genuinely is broken, not physics.
3. Timeline trace: TP=8 was deployed **across the two nodes** (4+4) after a node-failure remediation months earlier — every layer's all-reduce crossing InfiniBand (~40 GB/s effective) instead of NVLink (900 GB/s). nccl-tests confirmed: busbw 38 GB/s on the spanning group vs 740 GB/s intra-node.
4. Memory audit: FP16 weights (140 GB) + FP16 KV at batch 40 × 3K context ≈ 38 GB per group — original motivation for TP=8 had been capacity, not speed.

**Fixes, in deployed order**:
1. Re-layout: TP=4 *within* each node (35 GB weights/GPU at FP8 after quantizing weights — eval gate passed, MT-Bench delta −0.1), two independent replicas. Decode MBU 31% → 68%; inter-token p50 85ms → 24ms.
2. FP8 KV cache: per-token KV 320 KB → 160 KB; max concurrent sequences per replica 40 → 88; daily tokens 14M → 41M on the same 16 GPUs.
3. Capacity plan rewritten with the three-gate template and pinned to the dashboard: per-replica ceilings (single-stream 3.35e12×4/35e9 ≈ 380 tok/s ideal; measured 71% of that), prefill pool sized separately.
4. Procurement decision informed by the same math: next expansion went to 8×H200 (decode bandwidth per dollar) rather than 16 more H100s — modeled, then confirmed in canary, at ~1.35× decode throughput per GPU.

**Quantified outcome**: SLO met with margin (24ms vs 40ms target), ~2.9× tokens/day on unchanged hardware, expansion capex cut ~45% versus the original "just buy more H100s" plan. The postmortem's first line became the team's hiring question: *"GPU utilization was 92% the whole time — explain why that told us nothing."*

---

## Related

- [Optimization & Quantization README](README.md) — GPTQ/AWQ, FlashAttention, pruning, distillation
- [GPU Pool Economics](../case_studies/cross_cutting/gpu_pool_economics.md) — MFU/MBU math applied to fleet cost, spot blending, disaggregation economics
- [Inference & Decoding](../inference_and_decoding/README.md) — KV cache, speculative decoding, continuous batching
- [vLLM Deep Dive](../vllm_deep_dive/README.md) — PagedAttention and serving-stack realization of these ideas
- [Attention Mechanisms](../foundations_and_architecture/attention_mechanisms.md) — FlashAttention internals, MQA/GQA/MLA
- [DevOps: ML Platform & GPU Infrastructure](../../devops/ml_platform_and_gpu_infrastructure/README.md) — cluster-level GPU operations
