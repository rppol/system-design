# Training Infrastructure

## 1. Concept Overview

Training large language models requires coordinating thousands of GPUs across hundreds of servers, managing hundreds of terabytes of training data, and maintaining numerical stability over days or weeks of computation. Training infrastructure is the engineering discipline that makes this possible.

A single H100 GPU can train roughly 1B tokens/day on a 7B model. Training LLaMA 3 405B on 15T tokens required ~16,000 H100 GPUs running for 2+ months. The infrastructure challenge is keeping all these GPUs busy (high utilization), communicating efficiently (minimizing bandwidth bottlenecks), and recovering gracefully from the inevitable hardware failures.

Modern training infrastructure centers on three forms of parallelism: splitting the model across devices (model parallelism), splitting the data across devices (data parallelism), and overlapping computation with communication. Getting this right is the difference between 50% GPU utilization and 90%+ MFU (Model FLOP Utilization).

---

## Intuition

> **One-line analogy**: Training infrastructure is like orchestrating a factory assembly line across thousands of workers — if any worker is idle or miscommunicating, the whole line slows down.

**Mental model**: A single GPU can only hold a fraction of a 70B+ parameter model. So you split the model across GPUs (model parallelism) and split the data across GPUs (data parallelism). But now GPUs must constantly share results — the communication becomes the bottleneck. Training infrastructure is the art of keeping all GPUs busy, minimizing idle time, and recovering gracefully when hardware fails (and at 16,000 GPUs, something fails every few hours).

**Why it matters**: Training infrastructure determines how quickly new models can be trained, how much compute is wasted on communication vs. useful math, and whether a training run crashes or completes. A 10% improvement in MFU (Model FLOP Utilization) can save millions of dollars on a large training run.

**Key insight**: The three-way interaction between tensor parallelism (within node), pipeline parallelism (across nodes), and data parallelism (replicas) must be tuned carefully — the optimal configuration depends on model size, cluster topology, and interconnect bandwidth.

---

## 2. Core Principles

- **Maximize GPU utilization**: Every GPU-hour costs money. Idle GPUs waste resources.
- **Memory hierarchy awareness**: HBM (GPU memory) → NVLink → PCIe → NVSwitch → InfiniBand — bandwidth drops 10-100x at each boundary.
- **Overlap compute and communication**: Use CUDA streams to overlap AllReduce with backward pass.
- **Fault tolerance**: At 10,000 GPUs, expect 1+ hardware failure per day. Checkpointing and restart must be fast.
- **Numerical stability**: BF16 arithmetic + gradient clipping + careful initialization prevents training divergence.
- **Communication efficiency**: Model parallelism strategies differ in their communication patterns; choose based on model shape and hardware topology.

---

## 3. Types of Parallelism

### 3.1 Data Parallelism (DP)

Each GPU holds a full copy of the model; each processes a different batch. Gradients are averaged across GPUs at the end of each step.

```
GPU 0: model copy + batch_0 --> grad_0 ]
GPU 1: model copy + batch_1 --> grad_1 ]--> AllReduce(grads) --> update all models
GPU 2: model copy + batch_2 --> grad_2 ]
GPU 3: model copy + batch_3 --> grad_3 ]
```

**Problem**: For a 70B model in BF16, each GPU needs ~140GB just for model weights. No single GPU has that much memory.

### 3.2 Tensor Parallelism (TP)

Split individual layers horizontally across GPUs. Each GPU computes a portion of each matrix multiplication.

```
Large linear layer (d_model × 4d_model):
  GPU 0: W[:, 0:d]    -- computes first quarter of output
  GPU 1: W[:, d:2d]   -- computes second quarter
  GPU 2: W[:, 2d:3d]  -- computes third quarter
  GPU 3: W[:, 3d:4d]  -- computes fourth quarter

AllReduce at end of each layer to combine partial outputs
```

- Communication: All-Reduce after each layer (expensive if inter-node)
- Best within a single node (NVLink bandwidth 600-900 GB/s)
- Typical TP degree: 4-8 within a server

### 3.3 Pipeline Parallelism (PP)

Split model layers across different GPUs. Each GPU handles a set of consecutive transformer layers.

```
Layers 0-11:   GPU 0 (Node 0)
Layers 12-23:  GPU 1 (Node 1)
Layers 24-35:  GPU 2 (Node 2)
Layers 36-47:  GPU 3 (Node 3)

Micro-batch pipeline:
  Step 1: GPU 0 processes micro-batch 1
  Step 2: GPU 0 processes micro-batch 2 || GPU 1 processes micro-batch 1
  Step 3: GPU 0 processes micro-batch 3 || GPU 1 processes micro-batch 2 || GPU 2 micro-batch 1
  ...
```

- Communication: Point-to-point between adjacent pipeline stages (cheap)
- "Pipeline bubble" — GPUs idle at start/end of pipeline; minimize with micro-batching
- Typical PP degree: 8-64 across nodes

### 3.4 Sequence Parallelism (SP)

Split the sequence dimension across GPUs. Each GPU processes a chunk of the sequence in attention layers.

```
Sequence length 8192, 8 GPUs:
  GPU 0: handles tokens 0-1023
  GPU 1: handles tokens 1024-2047
  ...
  GPU 7: handles tokens 7168-8191

All-gather for attention (each token needs all KV positions)
Reduce-scatter after attention
```

- Enables long-context training by distributing sequence across GPUs
- Ring Attention: extends SP to very long sequences without full all-gather

### 3.5 Expert Parallelism (EP) — for MoE models

In Mixture of Experts models, different GPUs host different expert FFNs:

```
GPU 0: Expert 0, Expert 1
GPU 1: Expert 2, Expert 3
...

Router selects expert per token --> all-to-all communication to route tokens to correct GPUs
Expert computation on assigned GPU
All-to-all again to return results
```

---

## 4. Architecture Diagrams

### 3D Parallelism (Standard for Large Model Training)
```
   +---------------------+   +---------------------+
   |  Node 0             |   |  Node 1             |
   |  GPUs 0-7 (TP=8)   |   |  GPUs 8-15 (TP=8)  |
   |  Layers 0-11  (PP)  |   |  Layers 12-23 (PP) |
   +---------------------+   +---------------------+
           |                           |
           +-- InfiniBand 800 Gbps ----+
           (Data parallel across node groups)

Total parallelism = TP × PP × DP
Example: TP=8, PP=8, DP=16 = 1024 GPUs
```

### ZeRO (Zero Redundancy Optimizer) Stages
```
ZeRO Stage 0 (DDP):     Each GPU stores: [params] [gradients] [optimizer states]
  Memory per GPU: 16 bytes/param (params 2B + grads 2B + Adam states 12B)

ZeRO Stage 1:           Each GPU stores: [params] [gradients] [1/N optimizer states]
  Memory reduction: ~4x for optimizer states

ZeRO Stage 2:           Each GPU stores: [params] [1/N gradients] [1/N optimizer states]
  Memory reduction: ~8x

ZeRO Stage 3 (FSDP):    Each GPU stores: [1/N params] [1/N gradients] [1/N optimizer states]
  Memory reduction: ~N/16x (where N = number of GPUs)
  Cost: All-gather parameters before each forward pass (extra communication)
```

### GPU Memory Budget (70B Model, BF16)
```
Model weights:      140 GB  (70B × 2 bytes)
Gradients:          140 GB  (equal to weights in BF16)
Adam optimizer:     560 GB  (4x weights for m, v in FP32)
Activations:      variable  (depends on batch size + gradient checkpointing)

Total (naive):     840+ GB  -- requires 11+ A100 80GB GPUs just for model/optim

With ZeRO-3 + 16 GPUs:
  Per GPU: 840 / 16 = 52.5 GB + activations (manageable on 80GB GPU)
```

---

## 5. How It Works — Detailed Mechanics

### FSDP (Fully Sharded Data Parallel)

PyTorch's built-in ZeRO-3 implementation:

```
Forward pass:
  1. All-gather parameters for current layer (collect shards from all GPUs)
  2. Run forward computation
  3. Discard gathered parameters (free memory)
  4. Move to next layer

Backward pass:
  1. All-gather parameters for current layer
  2. Compute gradients
  3. Reduce-scatter gradients (each GPU keeps 1/N of gradient shards)
  4. Discard parameters

Optimizer step:
  Each GPU updates only its 1/N parameter shard
  Using its 1/N gradient shard and 1/N optimizer state shard
```

### Gradient Checkpointing

Trade compute for memory: during forward pass, discard activations. During backward pass, recompute them.

```
Without checkpointing: Store all activations -> O(layers × batch × seq) memory
With checkpointing: Store activations at N checkpoints -> recompute between checkpoints
  Memory: O(√layers) -- recompute cost: +33% compute

Selective checkpointing: Only checkpoint expensive activations (attention, certain MLPs)
```

### Communication Topology

```
Within node (8× H100):
  NVLink bandwidth: 900 GB/s total
  All-Reduce of 1GB: ~2ms (all-reduce = 2× ring latency)

Across nodes (InfiniBand):
  HDR-200: 200 Gbps = 25 GB/s per link
  Typical: 8 IB links per node = 200 GB/s
  All-Reduce of 1GB across 16 nodes: ~40ms

Implication: Tensor parallelism (requires all-reduce every layer) MUST stay within node
  Pipeline parallelism (point-to-point) can cross nodes
  Data parallelism all-reduce happens once per step -- tolerable across nodes
```

### Mixed Precision Training

```
Forward/backward: BF16 (fast, 2 bytes/param)
Gradient accumulation: FP32 (numerical stability for small gradients)
Optimizer states (Adam m, v): FP32 (important: m,v must be precise)
Master weights: FP32 copy alongside BF16 (updated in FP32, cast to BF16 for compute)

FP8 training (emerging, H100+ only):
  FP8 forward/backward: 1 byte/param -- 2x faster than BF16
  Requires careful scaling; used by DeepSeek-V3
```

---

## 6. Real-World Examples

### Meta LLaMA 3 405B Training Infrastructure
- 16,384 H100 GPUs (2048 nodes × 8 GPUs)
- 3D parallelism: TP=8 (within node), PP=16, DP=128
- FSDP + custom all-to-all for MoE layers (future)
- NVLink within node + InfiniBand HDR-400 across nodes
- Checkpoint every 30 minutes to distributed filesystem
- Training time: ~77 days

### Google TPU Pod Architecture
- TPU v5e: 256 chips per pod connected via high-bandwidth TPU interconnect
- Multi-pod training using DCN (Data Center Network) for inter-pod communication
- XLA compilation for efficient computation graphs
- Gemini Ultra trained across multiple pod-scale supercomputers

### Microsoft/OpenAI Azure AI Infrastructure
- Custom ND-series Azure VMs with InfiniBand HDR-400 networking
- PyTorch + DeepSpeed with ZeRO-3
- Distributed optimizer with parameter server components
- Estimated 10,000-25,000 H100s for GPT-4 training

---

## 7. Tradeoffs

| Parallelism | Pros | Cons | Best For |
|-------------|------|------|---------|
| Data Parallel | Simple, linear scaling | Model must fit one GPU | Small/medium models |
| Tensor Parallel | Reduces per-GPU memory | All-reduce every layer | Within-node; all GPU types |
| Pipeline Parallel | Minimal communication | Pipeline bubble waste | Large models across nodes |
| ZeRO-3/FSDP | Maximum memory efficiency | All-gather overhead | When GPU memory is the limit |

| Hardware | Memory | FP16 TFLOPS | Price |
|---------|--------|------------|-------|
| A100 80GB | 80 GB | 312 | ~$2/hr cloud |
| H100 80GB SXM | 80 GB | 1979 | ~$3-4/hr cloud |
| H200 141GB | 141 GB | 1979 + faster HBM | ~$5-6/hr cloud |
| B200 192GB | 192 GB | ~4500 | New; ~$8-10/hr est |

---

## 8. When to Use / When NOT to Use

### Use Distributed Training When:
- Model + optimizer states exceed single GPU memory
- Training will take weeks on a single GPU
- You need to parallelize over large data volumes

### Simple DDP Suffices When:
- Fine-tuning small models (7B with LoRA fits on one GPU)
- Research experiments where speed matters more than scale

---

## 9. Common Pitfalls

1. **Not profiling before optimizing**: Profile GPU utilization and communication before tuning parallelism settings.
2. **Wrong TP/PP balance**: Too high TP across nodes kills performance due to slow inter-node all-reduce every layer.
3. **Ignoring pipeline bubble**: With PP=8, naively, 7/8 of GPUs are idle at start/end. Use micro-batching (at least PP_degree × 4 micro-batches) to fill the pipeline.
4. **Checkpointing to slow storage**: Checkpointing 140GB every 30 minutes requires fast parallel filesystem. Using NFS or slow object storage creates bottlenecks.
5. **Missing gradient accumulation steps**: If effective batch size requires gradient accumulation, ensure all-reduce only happens every N accumulation steps.
6. **Not accounting for activation memory**: Large batch sizes with long sequences create enormous activation memory. Use gradient checkpointing.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **DeepSpeed** | ZeRO optimization, mixed precision | Microsoft; most widely used at scale |
| **FSDP** | PyTorch-native ZeRO-3 | Facebook; increasingly preferred |
| **Megatron-LM** | Tensor/pipeline parallelism | NVIDIA; used for Megatron-Turing NLG |
| **Nanotron** | Modern training framework | HuggingFace; clean 3D parallel support |
| **Ray Train** | Distributed training orchestration | Abstracts cluster management |
| **SkyPilot** | Multi-cloud GPU orchestration | Run on cheapest available cloud GPUs |
| **NCCL** | GPU collective communication | NVIDIA; AllReduce, AllGather, ReduceScatter |
| **Flash Attention 2** | Memory-efficient attention | Tri Dao; required for long-context training |
| **Weights & Biases** | Training monitoring | Loss curves, gradient norms, GPU utilization |
| **LLM-Foundry** | MosaicML training stack | Now part of Databricks |

---

## 11. Interview Questions with Answers

**Q: What is the difference between tensor parallelism and pipeline parallelism?**
A: Tensor parallelism (TP) splits individual matrix operations across GPUs — each GPU computes part of each layer's output. Requires all-reduce after every layer, so needs high-bandwidth connections (NVLink within node). Pipeline parallelism (PP) assigns different layers to different GPUs — data flows sequentially through pipeline stages. Requires only point-to-point communication between adjacent stages, tolerates lower bandwidth (can cross nodes). In practice: TP within nodes, PP across nodes.

**Q: What is ZeRO and what problem does it solve?**
A: ZeRO (Zero Redundancy Optimizer) eliminates the memory redundancy in data parallel training. In standard DDP, each GPU stores full copies of model weights, gradients, and optimizer states — 16 bytes/param. ZeRO Stage 3 shards all three across GPUs: each GPU stores 1/N of each. Memory per GPU drops from O(total) to O(total/N). Trade-off: requires all-gather before each layer's forward pass (communication overhead ~20-30%).

**Q: How do you handle hardware failures during multi-week LLM training?**
A: Checkpoint model state (weights, optimizer state, dataloader position) every 30-60 minutes to a parallel filesystem (Lustre, GPFS). When a node fails, kill the job, replace the node, reload the last checkpoint, and resume. With 10K+ GPUs, expect 1-2 hardware failures per day. Advanced: elastic training frameworks (Torch Elastic) that can continue with N-1 nodes while a replacement is provisioned.

**Q: What is gradient checkpointing and what is the tradeoff?**
A: Gradient checkpointing (activation recomputation) saves memory by NOT storing intermediate activations during the forward pass. During backpropagation, it recomputes the forward pass from checkpoints to get the activations needed for gradients. Memory: reduces activation memory from O(layers) to O(√layers). Cost: adds ~33% computation overhead. Essential for training large models or with long sequences.

**Q: How do you calculate memory requirements for each ZeRO stage?**
ZeRO partitions optimizer states, gradients, and parameters across data-parallel GPUs. For a model with P parameters in FP16: baseline (no ZeRO) per GPU = 2P (params) + 2P (grads) + 12P (Adam states: FP32 params + FP32 momentum + FP32 variance) = 16P bytes. ZeRO-1 shards optimizer states: 2P + 2P + 12P/N. ZeRO-2 shards optimizer states + gradients: 2P + 2P/N + 12P/N. ZeRO-3 shards everything: 2P/N + 2P/N + 12P/N = 16P/N. For a 7B model (P=7B): baseline = 112GB per GPU; ZeRO-3 with 8 GPUs = 14GB per GPU. The tradeoff: ZeRO-3 requires all-gather communication to reconstruct parameters for each forward/backward pass, adding ~10-20% communication overhead.

**Q: When should you use tensor parallelism vs pipeline parallelism vs data parallelism?**
Use data parallelism (DP) when the model fits on a single GPU — it scales linearly with minimal communication (gradient all-reduce once per step). Use tensor parallelism (TP) when the model doesn't fit on one GPU and you have fast interconnects (NVLink at 900GB/s) — TP splits each layer across GPUs and requires all-reduce after every layer. Use pipeline parallelism (PP) when spanning multiple nodes with slow interconnects (InfiniBand at 200-400GB/s) — PP splits layers across stages and only sends activations between stages. Production pattern for large models: TP within a node (fast NVLink), PP across nodes (slower network), DP across replica groups. LLaMA 3 405B used TP=8 within a node, PP=4 across nodes, DP across the remaining GPUs. Rule: TP degree = GPUs per node, PP degree = number of nodes needed, DP fills the rest.

**Q: What are the key differences between FSDP (PyTorch) and DeepSpeed ZeRO?**
FSDP is PyTorch's native implementation of ZeRO-3 (full sharding), while DeepSpeed is Microsoft's library offering ZeRO stages 1-3 plus additional optimizations. Key differences: (1) FSDP is integrated into PyTorch core (no separate library), making it easier to adopt and debug; (2) DeepSpeed offers ZeRO-Infinity (offload to NVMe), ZeRO++ (quantized communication), and more granular memory optimization; (3) FSDP uses PyTorch's autograd natively while DeepSpeed wraps the engine; (4) DeepSpeed has better support for MoE training; (5) FSDP has better composability with PyTorch features (compile, DTensor). In practice: use FSDP for models up to 30B on standard GPU clusters (simpler setup), use DeepSpeed for 70B+ models or when you need ZeRO-Infinity offloading. Meta uses FSDP internally; Microsoft uses DeepSpeed.

**Q: What are the common pitfalls of mixed-precision training and how do you avoid them?**
Mixed-precision training uses FP16 or BF16 for forward/backward passes while keeping FP32 master weights. Pitfalls: (1) FP16 overflow — gradients or activations exceed FP16 max (65,504), causing NaN loss. Fix: use loss scaling (multiply loss before backward, divide gradients after) or use BF16 (same range as FP32); (2) underflow — small gradients round to zero in FP16. Fix: dynamic loss scaling that adjusts the scale factor; (3) BF16 precision loss — BF16 has only 7 bits of mantissa vs FP16's 10 bits, causing precision issues in accumulation. Fix: keep running sums in FP32; (4) batch norm statistics — must stay in FP32 to maintain accuracy. Modern recommendation: use BF16 on Ampere+ GPUs (A100, H100) — it avoids overflow/underflow issues entirely because it has the same exponent range as FP32. FP16 with loss scaling is only needed on older GPUs (V100).

**Q: How does gradient checkpointing trade compute for memory?**
Gradient checkpointing (activation checkpointing) saves memory by not storing intermediate activations during the forward pass, instead recomputing them during the backward pass. Without checkpointing: memory for activations = O(L * B * S * H) where L=layers, B=batch, S=sequence, H=hidden. With checkpointing: only store activations at checkpoint boundaries (every k layers), recompute the rest. This reduces activation memory from O(L) to O(sqrt(L)) with ~33% more compute (one extra forward pass per segment). For a 7B model with 32 layers and batch size 8 at 4096 context: without checkpointing, activations use ~30GB; with checkpointing every 4 layers, ~8GB. Always enable checkpointing when GPU memory is the bottleneck — the 33% compute overhead is almost always worth the memory savings for training larger batches.

**Q: What is the communication overhead of distributed training and how do you minimize it?**
Communication overhead in distributed training comes from gradient synchronization (DP), parameter gathering (ZeRO-3/FSDP), and layer activation passing (PP). For data parallelism: all-reduce communicates 2 * model_size bytes per step (ring all-reduce). For a 7B FP16 model on 8 GPUs: 2 * 14GB = 28GB all-reduce per step. Minimization strategies: (1) gradient compression — quantize gradients to INT8 or use TopK sparsification (keep only top 1% of gradients); (2) overlap communication with compute — start all-reduce for layer N while computing layer N+1 (DeepSpeed and FSDP do this automatically); (3) gradient accumulation — do N micro-batches locally before synchronizing (reduces communication frequency by N×); (4) reduce TP degree if NVLink bandwidth is insufficient. The compute-to-communication ratio must stay above 1.0 for efficient scaling — if communication time exceeds compute time, adding more GPUs slows training down.

---

## 13. Best Practices

1. **Profile first** — use PyTorch Profiler or NVIDIA Nsight to identify bottlenecks before optimizing.
2. **Maximize MFU** — aim for >40% Model FLOP Utilization; <30% indicates a communication or scheduling problem.
3. **Use 3D parallelism** — TP within node, PP across nodes, DP as the outer loop.
4. **Gradient checkpointing + Flash Attention** for any model above 13B parameters.
5. **Asynchronous checkpointing** — write checkpoints in background threads to avoid blocking training.
6. **Warmup cluster** — run all-reduce benchmarks before starting training to catch networking issues early.

---

## 14. Case Study: Training a 70B Model on 1000 GPUs

**Setup:** 1000 × H100 80GB (125 nodes × 8 GPUs)

**Parallelism Strategy:**
```
Tensor Parallel (TP) = 8       (within each node, NVLink)
Pipeline Parallel (PP) = 8     (across 8 nodes, InfiniBand)
Data Parallel (DP) = ~15       (125 / (8 TP nodes) × 8 PP stages... adjusted)

Effective: TP=8, PP=8, DP=15 → 960 GPUs active
  (40 GPUs spare for hot standby)
```

**Memory per GPU:**
- 70B params / 960 GPUs with FSDP: ~70B × 18 bytes / 960 = ~1.3GB/GPU for params+grads+optim
- Activations (batch=2, seq=4096): ~8GB per GPU
- Total: ~50GB per GPU (fits in 80GB with headroom)

**Expected throughput:**
- H100 BF16 TFLOPS: 1979 peak; ~50% MFU = ~990 TFLOPS per GPU
- 70B model FLOPs per token: 2 × 70B = 140B FLOPs
- Tokens/sec per GPU: 990e12 / 140e9 = ~7000 tokens/sec
- Total: 1000 GPUs × 7000 = 7M tokens/sec

**Training timeline for 1T tokens:** 1T / 7M = ~33 hours
