# QLoRA (Quantized LoRA)

## 1. Concept Overview

QLoRA (Dettmers et al. 2023) combines two techniques to dramatically reduce GPU memory requirements for LLM fine-tuning: quantize the frozen base model to 4-bit NF4 (NormalFloat4) precision, and train [LoRA](lora.md) adapters in full BF16 precision on top of the quantized base. A 7B model that requires 28GB for standard LoRA training requires only 5-6GB with QLoRA — enabling fine-tuning on a single 16GB consumer GPU.

QLoRA made fine-tuning accessible beyond research labs. Before QLoRA (April 2023), fine-tuning a 7B model required at least a 40GB A100 for LoRA or significantly more for full fine-tuning. After QLoRA, an RTX 4090 (24GB) or even an RTX 4080 (16GB) can fine-tune 7B models, and a single A100 80GB can fine-tune 65B+ models.

---

## Intuition

> **One-line analogy**: QLoRA is like storing your textbooks in compressed PDFs to save shelf space, then printing only the pages you need to annotate.

**Mental model**: Standard LoRA loads the full-precision (BF16) base model weights to GPU — 7B model × 2 bytes/param = 14GB just for weights. QLoRA compresses those 14GB to ~3.5GB using 4-bit quantization, leaving 12GB more headroom for adapters, gradients, and activations. The LoRA adapters themselves are still trained in full BF16 precision — the quantization only affects the frozen base weights, not the learning process.

**Why it matters**: Hardware access is the primary barrier to LLM fine-tuning. QLoRA removed the A100 requirement and made 7B-13B model fine-tuning accessible on consumer hardware, dramatically expanding who can train specialized models.

**Key insight**: The key innovation is NF4 quantization — a 4-bit format specifically optimized for normally distributed neural network weights, minimizing information loss for the specific distribution that pre-trained LLM weights follow.

---

## 2. Core Principles

- **Quantize frozen weights, not adapters**: The 4-bit compression applies only to the frozen base model weights; LoRA adapters are trained at full BF16 precision.
- **NF4 is designed for LLM weights**: LLM weights are approximately normally distributed; NF4 assigns more quantization levels near zero (where most weights cluster) for minimal information loss.
- **Double quantization reduces metadata overhead**: Quantization itself has scaling factors (metadata); NF4 quantizes those scaling factors too, saving additional memory.
- **Paged optimizers prevent OOM crashes**: CUDA unified memory is used for optimizer states, paging them to CPU RAM when GPU memory is full — preventing training interruptions.
- **Dequantization on-the-fly**: During the forward pass, 4-bit weights are dequantized to BF16 for computation, then discarded — only 4-bit weights are stored, not 16-bit.

---

## 3. How It Works — Detailed Mechanics

### 3.1 NF4 Quantization

Standard 4-bit integer quantization (INT4) uniformly divides the weight range into 16 levels. NF4 uses non-uniform levels optimized for normal distributions:

```
Uniform INT4 quantization:
  Weights range: [-1.0, +1.0]
  4-bit → 16 levels: -1.0, -0.867, -0.733, ..., 0.0, ..., +0.733, +0.867, +1.0
  Equal spacing; mismatched to weight distribution

NF4 (NormalFloat4):
  16 levels chosen so each level captures equal probability mass
  of a standard normal distribution N(0, 1)
  Level boundaries:
    -1.0000, -0.6962, -0.5252, -0.3949, -0.2844, -0.1848, -0.0933, 0.0000,
     0.0791,  0.1609,  0.2461,  0.3379,  0.4407,  0.5626,  0.7230,  1.0000

  More levels near zero (where most LLM weights cluster)
  Fewer levels at extremes (rare weight values)

Why NF4 works:
  LLM pre-trained weights approximately follow N(0, 1) after per-block normalization
  NF4's equal-probability-mass quantization minimizes quantization error
    for normally distributed weights

Memory: 4 bits = 0.5 bytes per weight
  vs. BF16: 2 bytes per weight
  Compression ratio: 4×
  7B model: 14GB (BF16) → 3.5GB (NF4)
```

**In plain terms.** "Four bits buys you exactly sixteen distinct numbers per weight — so the only design question is *where on the number line you put those sixteen*, and NF4 puts them where the weights actually live."

| Symbol | What it is |
|--------|------------|
| 4 bits | The storage budget per weight. `2^4 = 16` — the entire vocabulary of values a weight may take |
| the 16 levels | The lookup table. Every stored weight is an index `0..15` into it, not a number |
| NF4 quantile spacing | Levels placed so each covers `1/16 = 6.25%` of the probability mass of `N(0,1)` |
| INT4 uniform spacing | Levels placed at equal *value* intervals instead, ignoring where the mass is |
| bytes/weight | `4 bits / 8 = 0.5 bytes`. Against BF16's `2 bytes`, exactly `4×` compression |
| per-block absmax | The scale that maps a real block of weights onto the fixed `[-1, +1]` table range |

Note what is and is not stored: a 4-bit weight is an *index*, and the 16 float values it indexes are a constant shared by the whole model. That is why the compression is exactly 4× and not "4× minus a table" — the table costs 16 floats total, once.

**Walk one example.** Base-weight memory at each precision, computed as `params × bytes/param`:

```
  model     fp16 (2 B)      int8 (1 B)     NF4 (0.5 B)     NF4 saves vs fp16
    7B        14.0 GB          7.0 GB         3.50 GB          10.5 GB
   13B        26.0 GB         13.0 GB         6.50 GB          19.5 GB
   65B       130.0 GB         65.0 GB        32.50 GB          97.5 GB
   70B       140.0 GB         70.0 GB        35.00 GB         105.0 GB

  (1 GB = 1e9 bytes; base weights only, before scales/adapters/activations)

  The threshold that matters is a single 80 GB A100:
    70B at fp16 = 140.0 GB  -> does not fit, not even close
    70B at int8 =  70.0 GB  -> "fits", but 10 GB left for everything else -> OOM
    70B at NF4  =  35.0 GB  -> 45 GB left for adapters, grads, activations -> works
```

That last block is the whole reason QLoRA exists as a named technique rather than a footnote. The compression ratio is a boring constant 4×; what is not boring is that 4× is precisely the factor that moves a 70B model across the one-GPU line. Halving again to int8 leaves no headroom, and the section's own case study confirms it — 70B at 8-bit is 70 GB against an 80 GB card, which the Tradeoffs section flags as still too large once gradients and activations are stacked on top.

**Why the level placement matters more than the bit count.** Both INT4 and NF4 spend the same 4 bits. Since roughly 68% of a standard normal's mass sits inside `±1σ`, uniform INT4 spacing spends a large share of its 16 levels on tail regions holding almost no weights, while the dense center gets coarse resolution. NF4's equal-probability construction guarantees every level is responsible for the same `6.25%` of weights, so no level is wasted and none is overloaded. Same storage, better-placed levels, `~0.5-1%` less quantization error — a free win, which is why Pitfall 2 says never to leave the quantization type at INT4.

### NF4 vs INT4 — Match the Levels to the Weights
```
Why NF4 beats INT4: put the 16 quantization levels where the weights actually are.

LLM weights ≈ N(0,1) — most mass piled near zero, thin tails:
            ▁▂▃▅▇█████▇▅▃▂▁
       -1.0        0.0        +1.0

INT4 — 16 evenly spaced levels (wastes resolution on the near-empty tails):
       |   |   |   |   |   |   |   |
       -1.0        0.0        +1.0

NF4 — 16 levels at equal-probability quantiles (packed near zero):
             | || ||||||||| || |
       -1.0        0.0        +1.0

Each NF4 level carries equal probability mass, so resolution is finest exactly where
weights cluster → ~0.5-1% less quantization error than INT4 at the same 4 bits.
```

### 3.2 Double Quantization

NF4 quantization stores a scaling factor per block of weights (typically 64 weights per block) to normalize to the NF4 range before quantizing. These scaling factors add overhead:

```
Without double quantization:
  64 weights per block → 1 scaling factor (BF16, 2 bytes)
  Overhead: 2 bytes / 64 weights = 3.1% overhead on top of 4-bit weights

Double quantization:
  Group scaling factors (e.g., 256 per super-block)
  Quantize scaling factors themselves to 8-bit
  Second-level scaling factor (FP32): 1 per super-block
  Overhead reduced to: (256 × 0.5 bytes [8-bit scale] + 4 bytes [FP32 super-scale])
                       / (256 × 64 weights × 0.5 bytes)
                     ≈ 0.4% overhead

  Memory savings: ~0.37 bits per parameter
  For a 7B model: ~300MB saved (small but meaningful)
```

**The idea behind it.** "You compressed the weights to 4 bits, but the scales you needed to do that are still full-precision — so compress the scales too, with exactly the same trick applied one level up."

The right unit for this whole discussion is **bits per parameter**, not percentages. Percentages hide the fact that the scale overhead is a fixed additive cost, and additive costs are what you can actually convert to gigabytes.

| Symbol | What it is |
|--------|------------|
| block size 64 | How many weights share one scale. Smaller blocks track local weight magnitude better but multiply the number of scales |
| absmax scale | The largest absolute weight in the block. Divide by it and the block lands in `[-1, +1]`, the NF4 table's range |
| 32 bits | Size of one FP32 absmax scale — the thing being paid for, once per 64 weights |
| `32 / 64` | First-level scale cost amortized per parameter: `0.5` bits/param |
| super-block 256 | How many *scales* share one second-level scale under double quantization |
| `8 / 64` | Cost of the now-FP8 first-level scales: `0.125` bits/param |
| `32 / (64 × 256)` | Cost of the FP32 second-level scale, amortized over `64 × 256 = 16,384` weights |

**Walk one example.** Both overhead figures, computed exactly:

```
  SINGLE QUANTIZATION (FP32 absmax, block 64)

    32 bits / 64 weights                    =  0.500000 bits/param

  DOUBLE QUANTIZATION (FP8 absmax, block 64; FP32 super-scale, super-block 256)

     8 bits / 64 weights                    =  0.125000 bits/param
    32 bits / (64 x 256 = 16,384 weights)   =  0.001953 bits/param
                                               --------
    total                                   =  0.126953 bits/param

  SAVED = 0.500000 - 0.126953              =  0.373047 bits/param
```

That `0.373` is exactly the "~0.37 bits per parameter" the block above quotes — now derived rather than asserted. Converting to memory:

```
  scale-metadata memory = params x bits/param / 8

  model      single quant      double quant      saved
    7B          437.5 MB          111.1 MB       326.4 MB
   65B         4062.5 MB         1031.5 MB      3031.0 MB   (3.03 GB)
   70B         4375.0 MB         1110.8 MB      3264.2 MB

  Total footprint on the 65B model (payload + scales):
    payload            4.000000 bits/param  ->  32.50 GB
    + single quant     4.500000 bits/param  ->  36.56 GB
    + double quant     4.126953 bits/param  ->  33.53 GB   <- 3.03 GB reclaimed
```

The 7B figure of `326.4 MB` reproduces the `~325MB` quoted in the Interview section, confirming the derivation. Note how the two levels differ in importance: the FP8 first-level scales cost `0.125` bits/param while the FP32 super-scales cost `0.001953` — **64× less**. The second level is essentially free, which is why nobody bothers with a third.

**Why the effective bit-width is never 4.0.** Marketing says "4-bit"; the honest number is `4.127` bits/param with double quantization, or `4.5` without. On a 65B model that gap between `4.0` and `4.5` is `4.06 GB` of pure bookkeeping — larger than the entire LoRA adapter, gradients, and optimizer state combined. Turning double quantization off does not just cost `0.37` bits abstractly; on the case study's 70B run it costs `3.26 GB`, which is the difference between fitting the A100 and not. This is also why the block above notes the scale overhead varies with block size: shrink the block from 64 to 32 for better fidelity and the single-quant overhead *doubles* to `1.0` bits/param.

### 3.3 Paged Optimizer

```
Problem: Training with Adam optimizer requires two momentum state tensors
  per parameter (m and v), each the same size as the parameter.
  Even for LoRA (8M params), optimizer states = 2 × 8M × 4 bytes = 64MB
  For larger LoRA: potentially 500MB-1GB of optimizer state

  If GPU memory is tight, optimizer states can cause OOM during peak batch

Paged optimizer solution:
  Optimizer states stored in CUDA unified memory
    → Initially allocated in GPU RAM
    → When GPU runs low, CUDA automatically "pages" some optimizer states
       to CPU RAM (16GB+ available)
    → Paged-in back to GPU when needed for parameter update

Implementation in BitsAndBytes:
  optimizer = bnb.optim.PagedAdamW8bit(model.parameters(), lr=2e-4)
  "Paged": uses CUDA unified memory for OOM prevention
  "8bit": additional memory savings by quantizing optimizer states themselves

Combined memory savings of paged 8-bit Adam vs. standard Adam:
  Standard Adam: 2 × 8M params × 4 bytes = 64MB
  PagedAdamW8bit: 2 × 8M params × 1 byte (8-bit) = 16MB
```

**What it means.** "Optimizer state is small but arrives all at once at the worst possible moment, so instead of reserving room for it permanently, let it spill to CPU RAM and pay a bus transfer only on the steps where it would otherwise have killed the run."

| Symbol | What it is |
|--------|------------|
| `m`, `v` | Adam's two per-parameter momentum tensors. Both exist only for *trainable* params, so only for the adapter |
| `2 × P × bytes` | Optimizer state size. The `2` is `m` and `v`; `bytes` is 4 for fp32, 1 for 8-bit |
| unified memory | An allocation the GPU addresses normally but that CUDA may physically place in CPU RAM |
| page event | One spill-or-restore round trip across PCIe. Cost is `size / bandwidth`, nothing more |
| PCIe 4.0 x16 | The pipe, ~16 GB/s usable. The only term that turns megabytes into milliseconds |

**Walk one example.** Optimizer state for the 8M-parameter adapter above, and the cost of moving it:

```
  OPTIMIZER STATE SIZE  (P = 8,000,000 trainable adapter params)

    Adam fp32   2 x 8e6 x 4 B  =  64 MB
    Adam bf16   2 x 8e6 x 2 B  =  32 MB
    AdamW8bit   2 x 8e6 x 1 B  =  16 MB     <- 4x smaller than fp32

  PAGE-EVENT COST at 16 GB/s

     16 MB  ->   1.00 ms
     64 MB  ->   4.00 ms
    160 MB  ->  10.00 ms
    320 MB  ->  20.00 ms

  8-bit states do double duty: they are 4x smaller to HOLD and 4x faster to MOVE.
  A page event on 8-bit state costs 1 ms; the same state in fp32 costs 4 ms.
```

Paging is worth understanding as an *insurance policy*, not an optimization. It never makes a run faster. Its entire value is that the alternative outcome is not "slower" but "CUDA OOM, process dead, 14 hours of training lost." The case study's run paged 23 times across three epochs — a handful of millisecond-scale stalls bought a training run that would otherwise have crashed.

**The gradient-checkpointing tradeoff, in the same units.** Activation memory is the term QLoRA does *not* shrink, and it is usually the one that actually OOMs you. Checkpointing keeps only one tensor per layer boundary and recomputes the interior during the backward pass:

```
  ACTIVATION MEMORY   7B model: 32 layers, hidden 4096, seq 2048, batch 1, bf16

    one layer-boundary tensor  =  1 x 2048 x 4096 x 2 B   =   16.78 MB
    x 32 layers stored         =                              537 MB    <- checkpointed
    all intermediates kept     =                              3-4 GB    <- not checkpointed

  COMPUTE PAID FOR IT   (units of one forward pass)

    without checkpointing :  fwd 1  +  bwd 2            =  3 units
    with checkpointing    :  fwd 1  +  bwd 2  +  refwd 1 =  4 units
                                                            -> +33% compute
```

So the trade is roughly **6x less activation memory for 33% more compute**, and under QLoRA you take it every time. The reason is asymmetry: the 4-bit weights already bought the memory headroom, and if activations then blow past what is left, the run does not get slower — it dies. Compute overruns are survivable; memory overruns are not. That asymmetry is why Pitfall 3 makes checkpointing mandatory rather than optional, and it stacks with the `~15-30%` dequantization overhead from Pitfall 1 — a QLoRA step is meaningfully slower than a LoRA step on both counts, which is the real price of the memory savings.

### 3.4 Full QLoRA Memory Layout

```
GPU Memory for 7B model fine-tuning with QLoRA (r=16, all-attn+FFN):

+----------------------------------------+
| Base model weights (NF4 4-bit)         |  ~3.5GB (7B × 0.5 bytes)
+----------------------------------------+
| Quantization metadata (scaling factors)|  ~150MB (double-quantized)
+----------------------------------------+
| LoRA adapter A (BF16)                  |  ~80MB
| LoRA adapter B (BF16)                  |  ~80MB
+----------------------------------------+
| Gradients (A, B only)                  |  ~160MB (same size as adapters)
+----------------------------------------+
| PagedAdamW8bit optimizer states        |  ~320MB (8-bit, 2 × 160MB)
+----------------------------------------+
| Activations + input batch              |  ~1-2GB (depends on seq length)
| (gradient checkpointing reduces this)  |
+----------------------------------------+
TOTAL: ~5.5-6.5GB for 7B model QLoRA

Comparison:
  QLoRA 7B:           5.5-6.5GB → RTX 4080 (16GB) ✓, even RTX 3080 (10GB) with small batches
  LoRA 7B (BF16):     ~15GB     → RTX 4090 (24GB) ✓
  Full FT 7B (BF16):  ~56GB     → 2× A100 40GB ✓, single A100 80GB ✓
```

### 3.5 BitsAndBytes Configuration

```python
from transformers import AutoModelForCausalLM, BitsAndBytesConfig
from peft import LoraConfig, get_peft_model
import torch

# QLoRA-specific quantization config
bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,                      # enable 4-bit loading
    bnb_4bit_quant_type="nf4",             # use NF4 quantization
    bnb_4bit_compute_dtype=torch.bfloat16, # dequantize to BF16 for compute
    bnb_4bit_use_double_quant=True,        # double quantization for scaling factors
)

# Load model in 4-bit
model = AutoModelForCausalLM.from_pretrained(
    "meta-llama/Meta-Llama-3-8B-Instruct",
    quantization_config=bnb_config,
    device_map="auto"                       # automatic device placement
)

# Prepare model for gradient checkpointing (reduces activation memory)
model.gradient_checkpointing_enable()
model = prepare_model_for_kbit_training(model)

# Add LoRA adapters (trained at BF16)
lora_config = LoraConfig(
    r=16, lora_alpha=32,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                    "gate_proj", "up_proj", "down_proj"],
    lora_dropout=0.05,
    bias="none",
    task_type="CAUSAL_LM"
)
model = get_peft_model(model, lora_config)

# Paged optimizer for OOM prevention
from transformers import TrainingArguments
training_args = TrainingArguments(
    output_dir="./qlora_output",
    per_device_train_batch_size=4,
    gradient_accumulation_steps=8,   # effective batch = 32
    learning_rate=2e-4,
    num_train_epochs=3,
    optim="paged_adamw_8bit",        # paged optimizer
    bf16=True,                       # BF16 compute
    logging_steps=25,
    warmup_ratio=0.03,
    lr_scheduler_type="cosine"
)
```

---

## 4. Architecture Diagram

### QLoRA Memory Layout During Training
```
GPU RAM (16GB RTX 4080):

[Base Model Weights — 4-bit NF4]
 |  3.5GB for 7B model  |
 | Dequantized to BF16  |
 | during forward pass  |
 +----------------------+
[LoRA Adapters — BF16]
 |  A matrix ~80MB      |
 |  B matrix ~80MB      |
 +----------------------+
[Gradients — only LoRA]
 |  ~160MB              |
 +----------------------+
[PagedAdamW States — 8-bit]
 |  ~320MB              |
 +---------+------------+
           |
           | (paged to CPU if needed)
           v
[CPU RAM (pageable)]
  Optimizer states overflow here
```

### Dequantization On-the-Fly
```
Forward Pass:
  4-bit NF4 weights ─[dequantize]─> BF16 weights ─[matmul]─> result
                                     (temporary)     (compute)
                                     (discarded immediately after use)

  No BF16 weight copy stored permanently
  4-bit storage, BF16 compute — best of both
```

**What this actually says.** "Nothing about the arithmetic changes — the matmul is the same BF16 matmul it always was. All that changed is how many bytes had to cross the memory bus to feed it."

| Symbol | What it is |
|--------|------------|
| dequantize | Table lookup: index `0..15` to its NF4 float, times the block's absmax scale. A few FLOPs per weight |
| "temporary" | The BF16 tile lives in registers/SRAM for the duration of one tile's matmul, then is gone |
| matmul FLOPs | `2 × d_out × d_in` per token. **Identical** for NF4 and BF16 storage — compute dtype is BF16 either way |
| weight traffic | Bytes pulled from HBM: `d_out × d_in × bytes_per_weight`. This is the term NF4 divides by 4 |
| arithmetic intensity | `FLOPs / bytes`. Low = starved by the bus; high = limited by the math units |
| ridge point | The intensity at which a GPU flips from memory-bound to compute-bound |

**Walk one example.** One 4096×4096 projection, one token, on an A100 80GB (312 TFLOP/s BF16, 2039 GB/s HBM):

```
  FLOPs are constant :  2 x 4096 x 4096  =  33,554,432   for every storage format

  storage    weight traffic     arithmetic intensity     vs A100 ridge (153 FLOP/B)
   BF16        33.55 MB            1.00 FLOP/byte          153x below  -> memory-bound
   INT8        16.78 MB            2.00 FLOP/byte           77x below  -> memory-bound
   NF4          8.39 MB            4.00 FLOP/byte           38x below  -> memory-bound

  Everything is FAR left of the ridge point. The math units are idle either way;
  the only thing that moves the clock is how fast weights arrive.

  Whole-model decode, weight-fetch bound, 7B on 2039 GB/s:
    BF16 : 14.0 GB / 2039 GB/s  =  6.87 ms/token  ->  146 tok/s
    NF4  :  3.5 GB / 2039 GB/s  =  1.72 ms/token  ->  583 tok/s     4x faster
```

**Why this is a bandwidth win and not a FLOPs win.** The FLOPs column above never changes — quantization does not remove a single multiply-add, and it *adds* the dequantization work on top. NF4 wins because at intensity `1.00 FLOP/byte` the A100 is running its math units at roughly `1/153` of peak, waiting on HBM. Cutting weight traffic 4× cuts the wait 4×, and the extra dequant FLOPs are absorbed for free in compute the GPU was going to spend idling anyway. Storing weights in 4 bits does not make the GPU compute faster; it makes the GPU wait less.

Which is exactly why the sign of the effect flips during training. Training runs large batches, so the same weight tile is reused across many tokens: weight traffic is amortized, intensity climbs toward the ridge, and the kernel becomes compute-bound. In that regime there is no idle time left to hide the dequantization in, so it shows up directly as wall-clock — the `~15-30%` training slowdown Pitfall 1 warns about. Same technique, same hardware, opposite verdict: **NF4 is a speedup for memory-bound single-stream decode and a tax for compute-bound batched training.** You accept the tax because you are not buying speed, you are buying the ability to run at all.

---

## 5. Real-World Examples

### Guanaco (QLoRA paper model, Dettmers 2023)
- QLoRA fine-tuned LLaMA 65B on a single A100 80GB in 24 hours
- Training cost: ~$36 on cloud GPU
- Guanaco 65B evaluated at 99.3% ChatGPT quality on MT-Bench
- Proved that QLoRA enables frontier-model-quality fine-tuning on single GPU

### Community fine-tuning ecosystem
- Thousands of community QLoRA adapters on HuggingFace Hub
- LLAMA-3-8B adapters for specific tasks: medical Q&A, legal summarization, code generation
- Typical training setup: 1× RTX 4090 (24GB), 8-16 hours, $4-10 cloud cost

### Axolotl QLoRA production pipelines
- Axolotl configuration YAML for reproducible QLoRA training
- Widely used in production fine-tuning; handles data formatting, evaluation, checkpoint management
- Default configuration uses QLoRA + NF4 + paged_adamw_8bit

---

## 6. Tradeoffs

| Dimension | LoRA (BF16) | QLoRA (NF4 4-bit) |
|-----------|-------------|-------------------|
| VRAM (7B) | ~15GB | ~5.5-6.5GB |
| VRAM (13B) | ~28GB | ~10-11GB |
| VRAM (70B) | ~140GB | ~36-40GB |
| Quality vs. full FT | -1.5-3% | -2-4% |
| Quality vs. LoRA | baseline | -0.5-2% |
| Training speed | Fast | Moderate (dequant overhead) |
| Hardware needed (7B) | RTX 4090 (24GB) | RTX 4080 (16GB) |
| Inference: can merge | Yes | After dequantize or separate |

---

## 7. When to Use / When NOT to Use

### Use QLoRA When:
- GPU VRAM is the primary constraint (16GB consumer GPU)
- Fine-tuning a 13B+ model where LoRA alone doesn't fit
- Quality loss of ~1-2% vs. standard LoRA is acceptable
- Cost-sensitive cloud training (smaller GPU = cheaper per hour)

### Use Standard LoRA When:
- VRAM is not the bottleneck (24GB+ GPU)
- Quality is the primary concern (eliminate the quantization noise)
- Inference framework requires non-quantized adapters
- Very small training runs where simplicity is valued

### Use Full Fine-Tuning When:
- Multi-GPU cluster is available
- Maximum possible quality required
- Cannot accept any quantization artifacts

---

## 8. Common Pitfalls

**1. Dequantization overhead underestimated**
QLoRA requires dequantization from NF4 to BF16 on every forward pass. This adds ~15-30% training time compared to standard LoRA. At scale (multiple epochs, large datasets), this is meaningful.
Fix: Profile training throughput with and without QLoRA; if latency is not the bottleneck, use standard LoRA on a larger GPU.

**2. Using INT4 instead of NF4**
INT4 quantization (uniform levels) causes higher quality degradation than NF4 (normal-distribution-optimized levels) for LLM weights.
Fix: Always specify `bnb_4bit_quant_type="nf4"` in BitsAndBytesConfig, not "int4".

**3. Not enabling gradient checkpointing**
Without gradient checkpointing, activation memory scales with sequence length × batch size. For long sequences (4096 tokens), activations can exceed remaining GPU memory.
Fix: Always enable `model.gradient_checkpointing_enable()` before QLoRA training. This recomputes activations during the backward pass (trading compute for memory).

**4. Merging adapters without dequantizing base model**
After QLoRA training, if you try to merge the LoRA adapter directly with the 4-bit base model, the merge produces a 4-bit model — fine for quantized inference but loses quality vs. merging with the BF16 model.
Fix: For best quality after training: load the BF16 base model, apply the LoRA adapter, merge, then optionally re-quantize for inference. For deployment in quantized format, merge then quantize.

**5. Batch size too small without gradient accumulation**
With 4 examples per batch (limited by memory) and no gradient accumulation, optimizer updates are very noisy.
Fix: Use gradient_accumulation_steps=8 or more to achieve an effective batch size of 32+. Total effective batch size = per_device_batch_size × num_gpus × accumulation_steps.

---

## 9. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **BitsAndBytes** | 4-bit and 8-bit quantization | Required for QLoRA; `bnb_4bit_quant_type="nf4"` |
| **HuggingFace PEFT** | LoRA adapter layer | Works with quantized models via `prepare_model_for_kbit_training` |
| **Unsloth** | Optimized QLoRA training | 2× faster, 70% less VRAM than standard PEFT; highly recommended |
| **Axolotl** | Training orchestration | YAML config; default QLoRA support |
| **TRL SFTTrainer** | SFT + PEFT integration | `SFTTrainer(model=model, peft_config=lora_config)` |
| **torchao** | Alternative quantization | PyTorch-native; newer alternative to BitsAndBytes |
| **AutoGPTQ** | Inference quantization | For merging QLoRA and re-quantizing for inference |

---

## 10. Interview Questions with Answers

**Q: What is QLoRA and how does it work?**
A: QLoRA (Quantized LoRA) combines base model weight quantization with LoRA adapter training. The frozen base model is quantized to 4-bit NF4 precision — reducing a 7B model from 14GB (BF16) to ~3.5GB. LoRA adapters are then trained on top in standard BF16 precision. During the forward pass, 4-bit weights are dequantized to BF16 on-the-fly for matrix multiplication and immediately discarded — the GPU stores 4-bit but computes in 16-bit. Three additional innovations: double quantization (quantizes the quantization scaling factors themselves to save ~0.37 bits/param), paged optimizer (pages Adam states to CPU RAM to prevent OOM), and NF4 format optimized for normally-distributed LLM weights. Result: 7B model fine-tuning in ~6GB VRAM vs. 15GB for standard LoRA.

**Q: What is NF4 quantization and why is it better than INT4 for LLM weights?**
A: INT4 quantization divides the weight value range into 16 uniformly spaced levels. LLM pre-trained weights are approximately normally distributed (most weights near zero, few large values). Uniform levels waste resolution on the extremes where few weights cluster, and sacrifice resolution near zero where most weights cluster. NF4 (NormalFloat4) uses 16 levels chosen so each captures equal probability mass of a standard normal distribution N(0,1). This places more quantization levels near zero (where most LLM weights are) and fewer at the extremes. NF4 minimizes quantization error for the specific distribution of LLM weights. Empirically: NF4 causes ~0.5-1% less quality degradation than INT4 at 4-bit precision.

**Q: How does paged optimizer prevent OOM during QLoRA training?**
A: During training, Adam optimizer maintains two momentum states (m and v) per parameter, each the same size as the parameter. Even for LoRA adapters (~50-160MB), optimizer states add another ~100-300MB. In tight memory situations (especially with long sequences), peak memory usage during optimizer updates can cause CUDA out-of-memory errors that terminate training. Paged optimizer stores optimizer states in CUDA unified memory — a region that appears to the GPU as GPU memory but can overflow to CPU RAM. When GPU memory is tight, CUDA automatically "pages" optimizer states to CPU RAM and pages them back when needed for updates. This prevents OOM crashes at the cost of PCIe bus transfer overhead (~5-10ms per page event, acceptable since it's infrequent).

**Q: What quality difference should you expect between QLoRA and standard LoRA fine-tuning?**
A: The quality gap is approximately 0.5-2% on most benchmarks, but it's task-dependent. For general instruction following: typically <1% difference (the QLoRA paper showed Guanaco 65B QLoRA at 99.3% of ChatGPT quality). For precision-critical tasks (SQL generation, structured output): 1-3% difference, as quantization introduces noise that affects consistent formatting. For creative/open-ended generation: often imperceptible. The quality delta comes from two sources: NF4 quantization introduces ~0.3-0.5% quality loss regardless of fine-tuning; the dequantize-requantize cycle during training adds a small additional perturbation. For most production applications, this quality loss is acceptable given the 2.5-3× memory savings.

**Q: How does double quantization work and how much memory does it save?**
A: NF4 quantization normalizes weights in blocks (64 weights per block) to the NF4 range using a BF16 scaling factor. These scaling factors occupy: 2 bytes (BF16) / 64 weights = 3.1% overhead on top of the 4-bit weights. Double quantization groups these scaling factors into super-blocks of 256, then quantizes the scaling factors themselves to FP8 (8-bit floating point), using one FP32 scale per super-block. Overhead reduces to: (256 × 1 byte FP8 + 4 bytes FP32) / (256 × 64 × 0.5 bytes) ≈ 0.38%. Memory saved by double quantization: approximately 0.37 bits per parameter. For a 7B model: 7B × 0.37 / 8 ≈ 325MB saved — not enormous but meaningful for tight memory budgets.

**Q: When is QLoRA inappropriate and you should use full-precision LoRA instead?**
A: Four scenarios where standard LoRA is preferred over QLoRA. (1) Quality is critical and a ~1-2% degradation is unacceptable — e.g., a production model where every percentage point of eval accuracy matters. (2) Inference framework compatibility — some quantization pipelines, GGUF conversion tools, and serving frameworks expect non-quantized adapters; QLoRA adapters require careful handling during merge/export. (3) Very long sequences (8K+) — gradient checkpointing + QLoRA dequantization overhead makes training significantly slower; if a 24GB GPU is available, standard LoRA is faster. (4) Iterative research — frequent model loading/swapping is faster without quantization overhead; use QLoRA only when you need the memory savings.

**Q: How do you export a QLoRA-trained model for inference?**
A: Two main approaches. Merge-then-inference: (1) load the BF16 base model (requires 14GB, usually done on a larger machine post-training); (2) apply the LoRA adapter via `PeftModel.from_pretrained`; (3) merge with `model.merge_and_unload()`; (4) save the merged BF16 model; (5) optionally re-quantize for serving (GPTQ, AWQ, or llama.cpp GGUF). Adapter-with-quantized-base: load the same 4-bit quantized base model for inference and dynamically apply the LoRA adapter at runtime (PEFT inference mode). The first approach produces a clean merged model compatible with all inference frameworks. The second keeps adapter flexibility but requires BitsAndBytes at inference.

**Q: How does gradient checkpointing interact with QLoRA's memory savings?**
A: Gradient checkpointing recomputes activations during the backward pass rather than storing them through the forward pass. This trades compute (recompute activations) for memory (don't store activations). For a 7B model with 32 transformer layers and sequence length 2048, activations can be 3-4GB without checkpointing. With gradient checkpointing, activation memory drops to ~300-500MB. QLoRA is especially dependent on gradient checkpointing because the base model's 4-bit weights already use most of the memory savings on weights — without checkpointing, activation memory easily causes OOM during training. Always enable gradient checkpointing before applying QLoRA: `model.gradient_checkpointing_enable()` must be called before `prepare_model_for_kbit_training(model)`.

**Q: What is the difference between QLoRA and GPTQ for model quantization?**
A: QLoRA and GPTQ are both 4-bit quantization techniques but designed for different phases. QLoRA is a training-time quantization technique: the base model is quantized to NF4 during fine-tuning to reduce training memory. The quantized weights are never finalized as a stand-alone model — they're dequantized during forward passes. GPTQ is a post-training inference quantization technique: after fine-tuning, the merged model is quantized to 4-bit using a second-order optimization procedure (Hessian-based calibration). GPTQ produces a standalone quantized inference model with minimal quality loss. In a QLoRA workflow: train with QLoRA (NF4) → merge to BF16 → re-quantize with GPTQ for deployment. The quantization at inference (GPTQ) is separate from training quantization (QLoRA).

**Q: How does Unsloth improve QLoRA training efficiency?**
A: Unsloth (Tim Dettmers' recommended library for QLoRA training) achieves 2× faster training and 70% less VRAM through four optimizations: (1) Custom triton kernels for fused quantized matmul operations — avoids the standard BitsAndBytes NF4-to-BF16 dequantization path, replacing it with fused kernels that compute directly without creating temporary BF16 tensors; (2) Optimized backward pass — rewrites gradient computation for LoRA adapters to avoid redundant operations; (3) Custom FlashAttention implementation for quantized attention weights; (4) Memory-efficient sequence packing. Result: on a 16GB GPU, Unsloth can train a 7B model with longer sequences than standard PEFT+BitsAndBytes, and finishes in half the wall-clock time. Recommended for any production QLoRA pipeline.

**Q: What is the difference between NF4 and INT4 quantization for LLM weights, and why does it matter?**
A: NF4 (NormalFloat4) is information-theoretically optimal for normally distributed data, while INT4 uses uniform level spacing regardless of the data distribution. INT4 divides the weight value range into 16 equal intervals. LLM pre-trained weights cluster near zero following approximately N(0, 1) after per-block normalization, meaning uniform INT4 wastes most of its 16 levels on the extremes where very few weights exist, while allocating only a few levels to the dense zero-region. NF4 assigns levels at the quantiles of a standard normal distribution — each of the 16 levels captures equal probability mass, so more levels are near zero where weights actually cluster. The practical impact: NF4 introduces approximately 0.5-1% less quantization error than INT4 at 4-bit precision, translating to 0.3-0.7% better downstream task quality. Always specify `bnb_4bit_quant_type="nf4"` in BitsAndBytesConfig; the default in some older versions was INT4, which produces meaningfully worse results.

**Q: How does double quantization work and what is its memory overhead?**
A: Standard NF4 quantization normalizes each block of 64 weights using a BF16 scaling factor before mapping to NF4 levels. These scaling factors themselves occupy 2 bytes (BF16) per block of 64 weights, which is 2/64 = 3.1% overhead on top of the 4-bit weights. Double quantization eliminates most of this overhead by quantizing the scaling factors themselves to 8-bit floating point, grouping them into super-blocks of 256 scaling factors each with one FP32 scale. The resulting overhead is (256 × 1 byte [8-bit scale] + 4 bytes [FP32 super-scale]) / (256 × 64 × 0.5 bytes [4-bit weights]) ≈ 0.4% — a reduction from 3.1% to 0.4%. The memory saving is approximately 0.37 bits per parameter, totaling roughly 325MB for a 7B model. Double quantization adds negligible compute overhead because scaling factor dequantization is a tiny fraction of total forward-pass compute. Enable it with `bnb_4bit_use_double_quant=True`.

**Q: When does the paged optimizer actually trigger and what is its performance cost?**
A: The paged optimizer triggers when GPU memory is under pressure during the optimizer update step, which happens after each gradient accumulation cycle. Peak memory moments occur when: (1) the optimizer simultaneously holds gradients, parameters, and both Adam momentum states (m and v); (2) long sequences create large activation tensors before gradient checkpointing discards them. When GPU memory drops below a CUDA-configured threshold, the paged optimizer spills the least-recently-used optimizer state tensors to CPU RAM via the CUDA unified memory mechanism. Each spill-and-restore cycle costs approximately 10-30ms of PCIe transfer time (at 16 GB/s PCIe 4.0 bandwidth, 160MB of optimizer states transfers in ~10ms). In typical QLoRA runs on tight hardware (16GB GPU), paging triggers a few times per epoch, adding roughly 5-10% total training time overhead. The alternative — OOM crash terminating training — makes this overhead completely acceptable. Use `optim="paged_adamw_8bit"` to activate both paging and 8-bit optimizer state quantization simultaneously.

**Q: What is the quality gap between QLoRA and full fine-tuning, and when does it widen?**
A: QLoRA achieves 95-99% of full fine-tuning quality on most standard NLP benchmarks, including instruction following, summarization, and conversational tasks. The original QLoRA paper demonstrated Guanaco 65B (QLoRA fine-tuned) at 99.3% of ChatGPT quality on MT-Bench. The quality gap widens in three specific scenarios. First, tasks requiring precise numerical reasoning (arithmetic, unit conversion, financial calculations) show 3-5% larger gaps because quantization noise accumulates across the multiple forward passes needed for chain-of-thought reasoning. Second, very long context tasks (8K+ tokens) show larger gaps because quantization error in early attention layers propagates and compounds across many transformer layers with extended sequences. Third, structured output tasks with strict formatting (code generation, JSON schemas, SQL) show 1-3% larger gaps because consistent symbol placement requires fine-grained weight precision that NF4 rounding partially degrades. For most production applications outside these categories, the gap is imperceptible to end users.

**Q: How do you diagnose and fix 4-bit training instability in QLoRA?**
A: Training instability in QLoRA manifests as loss spikes (sudden jumps of 0.5-2.0 in training loss followed by partial or no recovery) or divergence (loss trend consistently increasing after the first few hundred steps). Diagnosis: enable per-layer gradient norm logging and identify which layers produce abnormally large gradients immediately before loss spikes — these are typically the layers where NF4 quantization error is highest relative to the weight magnitude. The most reliable fixes: (1) enable double quantization (`bnb_4bit_use_double_quant=True`) — double quantization reduces the quantization constants' error, which is the most common source of instability; (2) reduce the learning rate by 2-3× (from 2e-4 to 7e-5) — lower LR gives the adapter more time to compensate for quantization noise in the base model; (3) use `bf16=True` with `tf32=False` to ensure full BF16 precision in adapter computations; (4) lower the gradient clipping threshold from 1.0 to 0.3, which prevents single large gradient steps from destabilizing the adapter. If instability persists after these changes, the model has quantization sensitivity in critical layers — switch to 8-bit quantization (`load_in_8bit=True`) or standard BF16 LoRA if the hardware permits.

---

## 11. Best Practices

1. **Use `bnb_4bit_quant_type="nf4"` always** — NF4 consistently outperforms INT4 for LLM weights; never use generic 4-bit quantization.
2. **Enable gradient checkpointing** — essential for fitting training in memory with QLoRA; always call before `prepare_model_for_kbit_training`.
3. **Use paged_adamw_8bit optimizer** — prevents OOM crashes during optimizer updates at peak memory usage.
4. **Use Unsloth in production** — 2× faster, 70% less VRAM vs. standard PEFT+BitsAndBytes; well-tested and production-ready.
5. **Use effective batch ≥ 32** — compensate for small physical batch with gradient accumulation (gradient_accumulation_steps = 32 / batch_size).
6. **Benchmark with Unsloth before committing to cloud hardware** — QLoRA memory requirements vary by sequence length; measure empirically on your data before choosing GPU type.
7. **Export via merge-then-requantize** — merge QLoRA adapter to BF16 first, then re-quantize with GPTQ or AWQ for inference deployment; cleaner and more widely compatible. (GPTQ/AWQ inference quantization mechanics: [Optimization & Quantization](../optimization_and_quantization/README.md).)

---

## 12. Case Study: Fine-Tuning LLaMA 3 70B on a Single A100 80GB with QLoRA

**Problem Statement**: A legal-tech company needs to fine-tune LLaMA 3 70B to perform contract clause classification and risk summarization. The 70B parameter scale is required because smaller models (7B, 13B) produce unacceptable hallucination rates on legal terminology. Standard LoRA on a 70B model requires ~140GB GPU memory (70B params × 2 bytes BF16), which means at least two A100 80GB GPUs. Budget and infrastructure constraints limit the training run to a single A100 80GB (80GB VRAM). The task: classify contract clauses into 47 categories and generate a one-paragraph risk summary for each clause.

**Architecture Overview**:
```
Single A100 80GB Training Setup:

GPU Memory Layout (peak ~48GB):
+------------------------------------------+
| 70B Base Model Weights (NF4 4-bit)       |  ~35GB (70B × 0.5 bytes)
+------------------------------------------+
| NF4 Scaling Factors (double-quantized)   |  ~700MB
+------------------------------------------+
| LoRA Adapter A matrices (BF16)           |  ~200MB (r=16, all-attn)
| LoRA Adapter B matrices (BF16)           |  ~200MB
+------------------------------------------+
| Adapter Gradients (BF16)                 |  ~400MB
+------------------------------------------+
| PagedAdamW8bit Optimizer States          |  ~800MB (8-bit, pageable)
+------------------------------------------+
| Activations (gradient checkpointing)     |  ~2-3GB
+------------------------------------------+
| Input batch + misc buffers               |  ~500MB
+------------------------------------------+
TOTAL PEAK: ~40-42GB (well within 80GB)

Post-training Export:
  Adapter (BF16, ~400MB) ──> merge onto BF16 base (CPU, 140GB RAM) ──> GPTQ 4-bit ──> deploy
```

**Key Design Decisions**:
1. NF4 4-bit with double quantization reduces 70B model from 140GB (BF16) to ~35GB — the only configuration that fits on a single A100 80GB with room for gradients and activations.
2. Gradient checkpointing mandatory: without it, 70B model activations at sequence length 1024 would consume 18-24GB, causing OOM even with quantized weights.
3. Rank r=16 targeting all attention projections (q_proj, k_proj, v_proj, o_proj) only — not FFN — because the 47-category classification task is a behavior change (output structure) rather than new knowledge injection; keeping FFN frozen also reduces adapter memory.
4. Effective batch size of 32 via gradient accumulation (batch_size=2, accumulation_steps=16) — physical batch size limited to 2 by activation memory at sequence length 512.
5. Merge-then-GPTQ export strategy: merge adapter to BF16 on a CPU instance with 256GB RAM, then re-quantize to GPTQ 4-bit for production inference on a single A100; avoids runtime dependency on BitsAndBytes at inference.

**Implementation**:
```python
from transformers import AutoModelForCausalLM, BitsAndBytesConfig, TrainingArguments
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from trl import SFTTrainer
import torch

bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_compute_dtype=torch.bfloat16,
    bnb_4bit_use_double_quant=True,    # saves ~700MB on 70B model
)

model = AutoModelForCausalLM.from_pretrained(
    "meta-llama/Meta-Llama-3-70B-Instruct",
    quantization_config=bnb_config,
    device_map="auto"
)

model.gradient_checkpointing_enable()
model = prepare_model_for_kbit_training(model)

lora_config = LoraConfig(
    r=16,
    lora_alpha=32,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
    lora_dropout=0.05,
    bias="none",
    task_type="CAUSAL_LM"
)
model = get_peft_model(model, lora_config)
# Trainable: ~83M / 70,000M = 0.12%

training_args = TrainingArguments(
    output_dir="./legal70b_qlora",
    per_device_train_batch_size=2,
    gradient_accumulation_steps=16,  # effective batch = 32
    num_train_epochs=3,
    learning_rate=1e-4,              # conservative LR for 70B quantized base
    lr_scheduler_type="cosine",
    warmup_ratio=0.05,
    optim="paged_adamw_8bit",
    bf16=True,
    max_grad_norm=0.5,               # tighter gradient clipping for stability
    logging_steps=25,
    evaluation_strategy="steps",
    eval_steps=100,
    max_seq_length=512
)

trainer = SFTTrainer(
    model=model,
    train_dataset=train_dataset,
    eval_dataset=eval_dataset,
    args=training_args
)
trainer.train()
model.save_pretrained("./legal70b_adapter")

# Post-training export (run on CPU instance with 256GB RAM):
# base = AutoModelForCausalLM.from_pretrained("meta-llama/Meta-Llama-3-70B-Instruct")
# peft_model = PeftModel.from_pretrained(base, "./legal70b_adapter")
# merged = peft_model.merge_and_unload()
# merged.save_pretrained("./legal70b_merged_bf16")
# # Then quantize with AutoGPTQ for production deployment
```

**Results**:
- Peak GPU memory during training: 41.3GB (within 80GB budget with comfortable headroom)
- Training time: 14 hours for 3 epochs on 8,000 training examples (512 tokens each)
- Training cost: ~$42 at A100 cloud rates ($3/hr)
- Clause classification accuracy: 89.2% on 47-category holdout set
- Risk summary quality (human evaluation): 4.2/5.0 average score (vs. 3.1/5.0 for 13B model)
- Quality vs. hypothetical full fine-tune (estimated): ~2% gap on classification accuracy (89.2% vs. estimated 91%)
- Paged optimizer triggered: 23 times across the full training run; added approximately 8 minutes total overhead

**Tradeoffs and Alternatives**:
- Standard LoRA on 70B (BF16) was impossible on a single A100 80GB — would require 140GB weight memory alone, far exceeding 80GB.
- Two-GPU LoRA (BF16 with model parallelism) was evaluated: achieves ~91% classification accuracy (vs. 89.2% QLoRA) but doubles infrastructure cost and requires NVLink for efficient gradient synchronization.
- 13B model with full fine-tuning (alternative that fits on single A100): achieved only 81% classification accuracy — the 70B QLoRA model provides an 8-percentage-point improvement critical for this legal application.
- 8-bit quantization (bitsandbytes load_in_8bit) would reduce memory from 35GB to ~70GB for 70B model (8 bits × 70B / 8 = 70GB) — still too large for a single 80GB A100 when combined with adapter gradients and activations.
