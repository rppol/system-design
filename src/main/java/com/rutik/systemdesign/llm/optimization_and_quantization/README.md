# Optimization & Quantization

## 1. Concept Overview

Model optimization encompasses all techniques that make LLM inference faster, cheaper, or more memory-efficient without retraining from scratch. The primary lever is quantization — reducing the numerical precision of weights from 32-bit floats to 8-bit or 4-bit integers, cutting memory by 4-8× and often speeding inference 2-4×.

Beyond quantization: Flash Attention dramatically reduces attention memory, Mixture of Experts reduces active compute, pruning removes unimportant weights, and knowledge distillation trains smaller models to match larger ones.

These techniques are not mutually exclusive — production deployments often combine several (e.g., quantized MoE model + Flash Attention + speculative decoding).

---

## Intuition

> **One-line analogy**: Model quantization is like JPEG compression for model weights — you reduce precision (quality) to dramatically reduce size and speed up loading, with a controlled quality tradeoff.

**Mental model**: A 70B model in full precision (BF16) weighs 140GB — too large for many GPU configurations. Quantization represents each weight with fewer bits: INT8 → 70GB, INT4 → 35GB. The math is the same; only the precision is reduced. Smart quantization (GPTQ, AWQ) minimizes the quality loss by carefully choosing how to round weights, using calibration data to protect the most "important" weights. Flash Attention takes a different approach: not reducing precision, but reordering computation to minimize slow GPU memory access.

**Why it matters**: Quantization determines how many GPUs you need, your hardware cost, and how many users you can serve per dollar. INT4 quantization can reduce a 4-GPU deployment to 1 GPU — 4× cost reduction with ~1-2% quality loss. For most business applications, this tradeoff is entirely acceptable.

**Key insight**: The quality loss from INT4 quantization on MMLU (~1%) is usually far smaller than the quality gain from using a larger model — INT4 70B typically outperforms BF16 13B, giving you more capability at less memory.

---

## 2. Core Principles

- **Quantization degrades quality** — always evaluate on domain-specific benchmarks, not just general benchmarks. INT4 may have 1% general benchmark drop but 10% domain drop.
- **Memory bandwidth is the bottleneck** — quantization helps primarily because it reduces data transferred from HBM per inference step.
- **Activation quantization is harder than weight quantization** — activations have outliers; weights are more predictable.
- **Calibration data matters** — post-training quantization quality depends heavily on the calibration dataset used.
- **Architecture choices are permanent** — MoE, attention design, and hidden dimensions are set at pre-training time; fine-tuning can't change them.

---

## 3. Quantization Methods

### 3.1 Post-Training Quantization (PTQ)

Quantize a trained model without retraining. The most practical approach.

**Simple rounding (ROUND):**
```
float32 weight: 0.1234
scale = max(abs(weights)) / 127  (INT8)
INT8 value = round(0.1234 / scale)

Dequantize at inference:
float32_approx = INT8_value × scale

Quality loss: noticeable at INT8; significant at INT4 (without calibration)
```

**GPTQ (Accurate Post-Training Quantization for Generative Pre-trained Transformers):**
```
Key idea: minimize quantization error layer by layer using Hessian information

For each layer W:
  1. Compute Hessian H = 2XX^T (sensitivity of loss to weight changes)
  2. Quantize weights column by column
  3. For each quantized weight, compensate remaining weights:
     w_remaining -= (quantization_error × H_inv)
  4. This propagates error to remaining unquantized weights

Result: Much lower error than simple rounding at INT4
Tradeoff: Requires calibration data (~128 samples); one-time offline process
Time: 30min for 7B, 3hrs for 70B on one GPU
```

**AWQ (Activation-aware Weight Quantization):**
```
Key insight: Not all weights are equally important for quantization
  Channels with larger input activations are more sensitive to quantization error

Algorithm:
  1. Profile activation magnitudes using small calibration dataset
  2. Identify "salient" channels (high activation magnitude)
  3. Apply per-channel scaling: scale important channels UP before quantization
     (this concentrates more quantization precision on important weights)
  4. Quantize scaled weights
  5. Rescale at inference

Result: Better than GPTQ at very low bit-widths (2-3 bit); comparable at 4-bit
No Hessian computation needed; faster calibration
Used by: LLaMA 70B serving at Together AI, AWS Bedrock
```

### 3.2 Quantization-Aware Training (QAT)

Train or fine-tune with simulated quantization in the forward pass:

```
Forward pass: use fake-quantized weights (quantize then dequantize)
  w_q = dequantize(quantize(w))  (float but quantization noise added)
Backward pass: straight-through estimator (gradient passes through quantize op)

Result: model learns to be robust to quantization noise
Quality: best INT4 quality; near-BF16 performance
Cost: requires GPU training time (~1-10% of original training compute)
Used for: production deployments where quality at INT4 is critical
```

### 3.3 KV Cache Quantization

Quantize not the model weights but the KV cache:

```
KV cache stores: float16 K, V tensors per layer per token
Memory: 2 × num_layers × num_kv_heads × head_dim × seq_len × 2 bytes

With INT8 KV quantization:
  Memory: 2 × num_layers × num_kv_heads × head_dim × seq_len × 1 byte
  → 50% reduction in KV cache memory
  → 2× more concurrent users at same GPU memory

With INT4 KV quantization:
  → 75% reduction
  → Some quality loss; monitor perplexity carefully

vLLM supports: --kv-cache-dtype int8 or fp8
```

**KIVI (2-bit KV cache):**
- Channels of K/V have different value ranges → per-channel quantization
- 2-bit KV with group quantization: ~8× smaller KV cache vs BF16
- Best for long context (128K+) where KV cache otherwise dominates memory

**When KV cache quantization matters most:**
- Long context (>16K tokens): KV cache > model weights in memory
- High concurrency: more users → more KV cache → sooner OOM
- GQA already helps (fewer KV heads), but KV quantization compounds the savings

### 3.4 GGUF Quantization (llama.cpp)

llama.cpp's quantization format optimized for CPU/metal inference:

| Format | Bits | Quality | Speed | Size (7B) |
|--------|------|---------|-------|-----------|
| Q2_K | 2.5 | Lowest | Fastest | 2.7GB |
| Q3_K_M | 3.3 | Low | Fast | 3.3GB |
| Q4_0 | 4 | Good | Fast | 3.8GB |
| Q4_K_M | 4.5 | Very good | Medium | 4.1GB |
| Q5_K_M | 5.3 | Excellent | Medium | 4.8GB |
| Q6_K | 6.6 | Near perfect | Slower | 5.5GB |
| Q8_0 | 8 | Near BF16 | Slowest | 7.2GB |

`Q4_K_M` is the community standard recommendation: best quality/size/speed balance.

---

## 4. Flash Attention

### What It Solves

Standard attention materializes the full `[seq × seq]` attention matrix in HBM (GPU memory):

```
Standard attention cost:
  Memory: O(n²) in HBM
  For seq_len=8192, BF16: 8192 × 8192 × 2 bytes = 134MB per head
  For 32 heads: 4.3GB just for attention matrices

  Time: multiple HBM reads/writes per step (S = softmax(QK^T/√d), O = SV)
```

Flash Attention tiles the computation to fit in SRAM:

```
Flash Attention algorithm:
  Divide Q into blocks of size B_r
  Divide K, V into blocks of size B_c

  For each block of Q:
    Load Q_block from HBM → SRAM
    For each block of K, V:
      Load K_block, V_block from HBM → SRAM
      Compute partial attention: S_block = Q_block × K_block^T
      Update running softmax numerically stable (online algorithm)
      Accumulate output O_block
    Write O_block → HBM

Memory: O(n) - never stores full [n×n] matrix
Speed: 2-4× faster (fewer HBM accesses)
```

### Flash Attention Versions

```
FlashAttention-1 (2022): Original; 2-4× faster; 5-20× memory reduction
FlashAttention-2 (2023): 2× faster than FA-1; better parallelization
FlashAttention-3 (2024): H100-specific; FP8 support; asynchronous computation
  FA-3 on H100 achieves ~75% of theoretical FLOPs utilization
```

---

## 5. Mixture of Experts (MoE)

### Architecture

Replace each dense FFN layer with N expert FFNs + a router:

```
Standard FFN:  x → FFN(x) = W2 × GELU(W1 × x)
  Every token goes through every weight

MoE FFN:       x → Router(x) = top-K experts
               x → Σ gate_score_i × Expert_i(x)  (for selected K experts)

Example: Mixtral 8x7B
  8 expert FFNs per layer, 2 active per token (top-2 routing)
  Total params: 46.7B
  Active params per token: ~12.9B (similar to a 13B dense model)

  Quality: ~comparable to 70B dense model
  Inference cost: ~comparable to 13B dense model
```

### Router Design

```
Router = Linear(hidden_dim → num_experts)
         followed by softmax + top-K selection

Auxiliary loss during training:
  Load balancing loss: penalize if one expert gets much more traffic than others
  Expert capacity: each expert can handle at most C tokens per batch
  If expert is at capacity: tokens overflow to next expert (token routing noise)
```

---

## 6. Architecture Diagrams

### Quantization Quality/Memory Tradeoff
```
Quality
  ^
  |  BF16 ●
  |       ● INT8
  |          ● AWQ INT4
  |             ● GPTQ INT4
  |                 ● INT3
  |                     ● INT2 ●
  +-----------------------------------------> Memory (GB, 70B model)
     140  70    35    17    13    7
```

### MoE Architecture Per Layer
```
Input x (seq_len × hidden_dim)
     |
     v
[Router: Linear(d_model → N_experts)]
     |
     v
[Top-K selection: pick k=2 experts]
     |
     +--- Token 1 → Expert 3, Expert 7
     +--- Token 2 → Expert 1, Expert 5
     +--- Token 3 → Expert 2, Expert 3
     |
     v
[Expert FFNs] (each is a standard FFN, but only 2 active per token)
Expert 1: W1_1, W2_1
Expert 2: W1_2, W2_2
...
Expert N: W1_N, W2_N
     |
     v
[Weighted sum: gate_score × expert_output]
     |
     v
Output (seq_len × hidden_dim)
```

---

## 7. Other Optimization Techniques

### Pruning

Remove low-importance weights:

```
Unstructured pruning: zero out individual weights based on magnitude
  70B model, 50% sparsity → 35B non-zero weights
  Problem: sparse arithmetic is hard to accelerate on GPU
  Sparse GPU support: NVIDIA 2:4 sparsity (2 non-zero per 4 weights) → 2× speedup on A100

Structured pruning: remove entire heads, layers, or neurons
  Attention head pruning: analyze head importance, prune least important
  Layer dropping: for models >40 layers, remove ~25% of layers
  Result: smaller, faster model with some quality loss

LLM.int8() / SparseGPT: combined quantization + pruning
```

### GQA as Inference Optimization

Grouped Query Attention (GQA) reduces the number of K/V heads while keeping all Q heads:

```
Standard MHA: 32 Q heads, 32 K heads, 32 V heads
  KV cache: 2 × 32 × head_dim × seq_len × layers  (large)

GQA (LLaMA 3 8B): 32 Q heads, 8 K heads, 8 V heads
  KV cache: 2 × 8 × head_dim × seq_len × layers   (4× smaller)

MQA (extreme): 32 Q heads, 1 K head, 1 V head
  KV cache: 2 × 1 × head_dim × seq_len × layers   (32× smaller, quality drop)
```

GQA is an **architectural** optimization (set at pre-training time), not a post-hoc optimization — but it dramatically reduces the inference memory pressure of the KV cache with minimal quality degradation (as validated in LLaMA 2/3, Mistral).

### Sliding Window Attention for Inference

For tasks where most useful context is local (most language modeling tasks), sliding window attention reduces attention cost from O(n²) to O(n×w):

```
Full attention (seq_len=128K):
  128K × 128K attention matrix = 16B elements × 2 bytes = 32GB per head

Sliding window (window_size=4096):
  128K × 4096 attention matrix = 512M elements × 2 bytes = 1GB per head
  → 32× memory reduction for long-context inference

Mistral 7B: sliding window = 4096 tokens per layer
Combined with Flash Attention → efficient 32K effective context
```

Deeper layers often use larger windows (or full attention) to capture global context; shallow layers use small local windows for efficiency.

### Paged KV Cache (vLLM PagedAttention)

Traditional KV cache suffers from **fragmentation** — pre-allocated contiguous memory per sequence wastes space:

```
Naive allocation:  [seq_A: 4096 tokens allocated] ← uses 1024, wastes 3072
                   [seq_B: 4096 tokens allocated] ← uses 2048, wastes 2048

PagedAttention: fixed-size blocks (e.g., 16 tokens each)
  Seq A: [block_0][block_1] ... (allocated on demand, non-contiguous OK)
  Seq B: [block_7][block_8] ... (shares physical memory with A when A is done)

Benefits:
  - Near-zero fragmentation (< 4% wasted vs. 60-80% in naive)
  - Block sharing: same prefix → same blocks (copy-on-write for parallel sampling)
  - Preemption: swap individual blocks to CPU, not whole sequence
```

### Activation Checkpointing Tradeoffs

Activation checkpointing (also called gradient checkpointing) trades compute for memory **during training**:

```
Without checkpointing:
  All intermediate activations saved for backward pass
  Memory: O(layers × batch × seq × d_model) — can be 10-40GB for large models
  Speed: fastest (no recomputation)

With checkpointing:
  Save only activations at "checkpoint" boundaries (e.g., every N layers)
  Recompute non-saved activations during backward pass
  Memory: O(√layers × batch × seq × d_model) — 3-5× memory reduction
  Speed: ~30-40% slower (recomputes ~N/2 layers on average)

Selective checkpointing:
  Checkpoint only memory-intensive operations (attention) not cheap ones (norm)
  Better tradeoff: 20-30% memory reduction, 5-10% slowdown
```

Not applicable during inference (no backward pass needed).

### Tensor vs Pipeline Parallelism for Inference

When a single GPU can't hold the full model, parallelism strategies differ significantly in inference vs training:

**Tensor Parallelism (intra-layer):**
```
Split each weight matrix across GPUs:
  GPU 0: Q_heads[0:16], K[0:4], V[0:4], FFN[0:half]
  GPU 1: Q_heads[16:32], K[4:8], V[4:8], FFN[half:full]

Each GPU processes its slice → all-reduce to combine
Latency: adds communication overhead (all-reduce per layer)
Best for: latency-sensitive applications, small batch sizes
Scales: usually up to 4-8 GPUs per node (intra-node NVLink only)
```

**Pipeline Parallelism (inter-layer):**
```
Split model layers across GPUs:
  GPU 0: layers 0-19    GPU 1: layers 20-39    GPU 2: layers 40-59

Micro-batching: split batch into micro-batches to fill the pipeline
  Stage 0: process micro-batch 1 → pass to stage 1
  Stage 1: process micro-batch 1 + stage 0 starts micro-batch 2
  ...

Latency: high (pipeline bubble ~(num_stages-1)/num_stages compute)
Throughput: high with enough micro-batches
Best for: throughput-sensitive serving (large batch sizes)
Scales: across nodes (P2P transfers, not all-reduce)
```

**Recommendation for serving:**
- Single node (NVLink): tensor parallelism degree = num GPUs
- Multi-node: tensor × pipeline (TP within node, PP across nodes)
- Small model + many GPUs: data parallelism (replicas)

### Knowledge Distillation

Train a small student model to mimic a large teacher:

```
Standard distillation:
  Loss = α × cross_entropy(student, labels) + (1-α) × KL(student, teacher)
  Teacher outputs: soft probability distribution (richer signal than hard labels)
  Example: DistilBERT (66M) → 97% of BERT (110M) quality at 60% speed

Sequence-level distillation:
  Teacher generates training data: 100K (prompt, teacher_response) pairs
  Student fine-tuned on teacher responses
  Used by: Alpaca (LLaMA fine-tuned on GPT-3.5 outputs), Vicuna, etc.

Feature distillation:
  Student matches intermediate hidden states of teacher
  More complex but captures richer representations
```

---

## 8. Real-World Examples

### Together AI GPTQ/AWQ Serving
- Serves models at INT4 (AWQ) for most 70B+ models
- 4× memory reduction enables serving 70B on 2×A100 instead of 4×A100
- Quality difference: <2% on MMLU; acceptable for most applications

### Mixtral 8x7B (Mistral AI)
- 46.7B total params, 12.9B active per token
- 2 out of 8 experts active per token
- Quality: exceeds LLaMA 2 70B on most benchmarks
- Cost: inference at ~2.5× the cost of 7B model (not 7× like dense 46B)

### DeepSeek-V3 (2024)
- 671B total params, 37B active
- 64 experts, 8 active per token
- Fine-grained expert routing with auxiliary-loss-free load balancing
- Trained for $5.5M total (H100 clusters)
- Per-token inference cost comparable to a ~40B dense model

---

## 9. Tradeoffs

| Optimization | Memory | Speed | Quality | Complexity |
|-------------|--------|-------|---------|------------|
| INT8 weight quant | 2× less | 1.5× faster | -0.5% | Low |
| INT4 GPTQ | 4× less | 2× faster | -1-2% | Medium |
| INT4 AWQ | 4× less | 2× faster | -0.5-1% | Medium |
| Flash Attention | Much less | 2-4× faster | 0% (exact) | None (library) |
| MoE (8 experts) | Same total | Same active compute | +quality | Training change |
| Pruning (50%) | 2× less | Depends | -3-5% | High |
| Distillation | Model-dependent | Faster | Modest loss | High |

---

## 10. When to Use / When NOT to Use

### Use Quantization When:
- GPU memory is the bottleneck (almost always)
- Cost is a concern (reducing cloud GPU hours)
- Model quality degradation is within tolerance

### Don't Quantize When:
- Model is already small (7B in BF16 fits in 14GB; no need for INT4)
- Task is very quality-sensitive (legal, medical decisions)
- You're running fine-tuning (quantized weights can't be efficiently trained)

### Use Flash Attention When:
- Always — there's essentially no downside; use Flash Attention 2 everywhere

### Use MoE When:
- Training a new model (architectural choice at pre-training time)
- Want large model capability at small model inference cost

---

## 11. Common Pitfalls

1. **Evaluating only on general benchmarks**: INT4 may lose only 1% on MMLU but 10% on your domain task. Always evaluate on domain benchmarks.
2. **Wrong calibration data**: GPTQ/AWQ quality is calibration-data-dependent. Calibrate on data similar to your use case.
3. **Forgetting KV cache quantization**: Quantizing weights but not KV cache misses 30-50% of memory savings.
4. **MoE expert imbalance**: Without load balancing loss, some experts get all traffic; others get none. Always use auxiliary loss during MoE training.
5. **Applying pruning without NVIDIA 2:4 sparsity**: Random unstructured pruning on GPU ≠ speedup. Only structured pruning (or 2:4 sparsity on A100+) gives actual speedup.

---

## 12. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **AutoGPTQ** | GPTQ quantization | pip install auto-gptq |
| **AutoAWQ** | AWQ quantization | pip install autoawq |
| **bitsandbytes** | INT4/INT8 load-time quantization | Used by QLoRA; easy API |
| **llama.cpp** | GGUF quantization | Best for CPU/metal |
| **Flash Attention** | Efficient attention | pip install flash-attn |
| **NVIDIA TransformerEngine** | FP8 quantization | H100 hardware |
| **Optimum** | HuggingFace optimization | Quantization + hardware export |
| **Intel OpenVINO** | CPU inference optimization | Best for Intel CPUs |
| **TensorRT** | NVIDIA TRT inference | Maximum speed on NVIDIA |
| **ONNX Runtime** | Cross-platform optimization | Export to ONNX then optimize |

---

## 13. Interview Questions with Answers

**Q: What is the difference between GPTQ and AWQ?**
A: GPTQ (Accurate PTQ) uses second-order information (Hessian of the loss) to compensate for quantization error as it quantizes column by column. AWQ (Activation-aware Weight Quantization) profiles activation magnitudes to identify important weight channels, then scales those channels before quantization to preserve their precision. AWQ is faster to apply and tends to give better quality at very low bit-widths (2-3 bit); GPTQ is competitive at 4-bit and has been more widely deployed.

**Q: Why does Flash Attention improve both memory and speed?**
A: Standard attention materializes the full `[seq × seq]` attention matrix in HBM (GPU slow memory): O(n²) memory. Flash Attention tiles the computation using SRAM (GPU fast memory) — it never writes the full attention matrix to HBM, only the final output. This reduces memory from O(n²) to O(n). Speed improves because fewer HBM reads/writes (the bottleneck) are needed; computation happens in faster on-chip SRAM.

**Q: What is Mixture of Experts and what's its key benefit?**
A: MoE replaces the FFN in each transformer block with N expert FFNs and a router that selects K experts per token. Total model parameters are N times larger than a dense model, but only K/N fraction of parameters are computed per token. This gives large model capacity (better quality than a comparably-sized dense model) at much lower inference compute (similar to a smaller dense model). The trade-off: all experts must fit in memory even if only K are active; load balancing across experts requires careful training.

**Q: What is knowledge distillation and when would you use it over fine-tuning?**
A: Knowledge distillation trains a small student model using a large teacher model's soft probability outputs (not just hard labels) as training signal. The soft probabilities carry richer information about the teacher's confidence and uncertainty. Use distillation when: (1) you want a smaller, faster model with high quality; (2) you have unlimited compute to generate teacher outputs; (3) the target architecture is different from the teacher. Use fine-tuning when: adapting an existing model's behavior rather than creating a smaller version.

---

## 14. Case Study: Deploying Mixtral 8x22B at Production Scale

**Problem:** Company wants to serve Mixtral 8x22B (high quality) for enterprise customers. Total params: 141B. At BF16: 282GB — requires 4× H100 80GB, expensive.

**Optimization strategy:**
```
Step 1: AWQ INT4 quantization
  141B params × 0.5 bytes/param = 70.5GB
  Fits on 1× H100 80GB (with room for KV cache)
  Quality check: MMLU 78.6% → 77.2% (acceptable)

Step 2: Flash Attention 2
  Max context: 64K tokens
  Without FA: 64K² × 2 bytes × 56 layers = catastrophic memory
  With FA: linear memory scaling; manageable

Step 3: Speculative decoding with Mixtral 8x7B as draft
  Accept rate: 68% for enterprise text generation
  Speedup: 2.1× throughput improvement

Step 4: vLLM with PagedAttention
  KV cache: efficiently managed; 60 concurrent users on 1× H100

Final configuration:
  1× H100 80GB per deployment unit
  Serve 60 concurrent users with P99 TTFT < 800ms
  Cost: ~$4/hr → $2,880/month per deployment unit

vs. naive BF16 serving:
  4× H100 needed (282GB weights)
  ~$16/hr → $11,520/month per deployment unit
  4× cost savings
```
