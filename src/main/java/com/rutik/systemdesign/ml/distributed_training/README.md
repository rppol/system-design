# Distributed Training for ML

## 1. Concept Overview

Distributed training splits the work of training a machine learning model across multiple devices (GPUs) or multiple machines (nodes). It is necessary when either the dataset is too large to iterate over in reasonable time on a single GPU, or the model itself is too large to fit in a single GPU's VRAM.

There are two fundamental decomposition strategies:

- **Data parallelism**: each GPU holds a full copy of the model, processes a different batch of data, and gradients are synchronized across all GPUs after each backward pass. Linear throughput scaling: N GPUs = N times the batch per second.
- **Model parallelism**: the model is partitioned across GPUs so each GPU holds only a portion of the parameters. Required when the model does not fit on a single device. Subdivides into tensor parallelism (split within a layer) and pipeline parallelism (split between layers).

Modern large model training combines all three: data parallelism across nodes, tensor parallelism within a node (fast NVLink), and pipeline parallelism across nodes (slower interconnect).

The primary communication primitive underlying distributed training is **AllReduce**: every GPU sends its gradient tensor, the reduce operation (sum) is applied across all GPUs, and every GPU receives the result. NCCL (NVIDIA Collective Communications Library) implements AllReduce efficiently over NVLink and InfiniBand.

---

## 2. Intuition

One-line analogy: training a model across multiple GPUs is like having N employees each read a different chapter of a book, then voting on the best summary — each employee's "vote" (gradient) must be collected, averaged, and broadcast back to everyone before the next chapter begins.

Mental model: think of DDP (DistributedDataParallel) as a synchronized ensemble. At each step: (1) each GPU independently computes a forward pass and backward pass on its local data batch; (2) the NCCL AllReduce operation sums and averages gradients across all GPUs — this is the synchronization point; (3) every GPU applies the identical gradient update to its model copy. All copies stay in sync because they start from the same weights and receive the same gradient delta.

Why it matters: GPT-3 (175B parameters) training required approximately 3.14 × 10^23 FLOP — on a single A100 GPU (312 TFLOP/s, ~40% utilization) this would take 8,000 years. With 1,024 A100 GPUs and 45% MFU (model FLOP utilization), actual training time was ~34 days.

Key insight: communication is the bottleneck in distributed training, not compute. GPU compute has scaled 100x in 10 years; network bandwidth has scaled 10x. Reducing communication volume (gradient compression, mixed precision, FSDP sharding) is as important as increasing FLOP/s.

---

## 3. Core Principles

**Synchronous vs asynchronous training**: synchronous (the default) requires all GPUs to complete their backward pass before AllReduce — stragglers slow everyone down. Asynchronous (parameter server pattern) allows parameter updates without waiting — risk of stale gradients causing convergence instability. Synchronous DDP is the production standard.

**Linear scaling rule**: when scaling from 1 GPU to N GPUs, the global batch size scales proportionally (local_batch × N), and the learning rate should scale by the same factor. For large scale-ups (> 32x), linear LR scaling diverges; use warmup (ramp LR over 5 epochs) to stabilize.

**Gradient accumulation**: simulate a larger batch without more GPUs by accumulating gradients over M steps before calling `optimizer.step()`. Effective batch = local_batch × n_gpus × accumulation_steps. Useful when a single step's batch does not fit in VRAM.

**Checkpointing discipline**: in distributed training, only rank 0 should write checkpoints to avoid N processes writing the same file simultaneously. With FSDP, each rank holds a shard — use FSDP's built-in `state_dict_type` context manager to gather and save.

**MFU (Model FLOP Utilization)**: actual FLOP/s ÷ peak theoretical FLOP/s. A100 peak is 312 TFLOP/s (BF16 with sparsity). Well-tuned LLM training achieves 38-45% MFU. Memory-bound operations (layer norms, embeddings) drag MFU down; computation-bound (large matmuls) push it up.

---

## 4. Types / Architectures / Strategies

**DataParallel (DP) — deprecated for multi-GPU**
Single-process, multi-thread, one GPU is the master. Master sends model to other GPUs, collects gradients back — creates a bottleneck on the master GPU. Memory imbalanced (master holds activations + gradients for all GPUs). Not recommended; use DDP instead.

**DistributedDataParallel (DDP)**
Multi-process (one process per GPU), symmetric AllReduce — no master bottleneck. Uses NCCL backend over NVLink/InfiniBand. Overlaps gradient communication with backward computation (gradient buckets: default bucket size 25 MB). The production standard for data-parallel training on 1-64 GPUs.

**FSDP (Fully Sharded Data Parallel)**
PyTorch 1.12+. Shards model parameters, gradients, and optimizer states across all ranks. Each rank holds 1/N of every parameter. Before each forward pass, parameters are gathered via AllGather; after backward pass, gradients are reduced via ReduceScatter. Memory per GPU scales as ~1/N of model size, enabling training models that exceed single-GPU VRAM. Overhead: 2x communication compared to DDP, but enables models 10-100x larger.

**DeepSpeed ZeRO**
Stage 1: shard only optimizer states — 4x memory reduction. Stage 2: + gradient sharding — 8x memory reduction. Stage 3: + parameter sharding — equal to FSDP in memory but with more communication. ZeRO-Infinity offloads to CPU RAM and NVMe SSD for near-infinite model capacity.

**Tensor Parallelism (TP)**
Split individual matrix multiplications across GPUs. A linear layer (A × W) with weight W split column-wise: each GPU computes partial results, then AllReduce to combine. Requires fast NVLink (within a node, 600 GB/s A100). Megatron-LM implements column-parallel and row-parallel linear layers.

**Pipeline Parallelism (PP)**
Split model layers into stages, each stage on a different node. Data flows forward through stages (forward pass) then backward through stages (backward pass). GPUs are idle while waiting for the next micro-batch — "pipeline bubble." GPipe, PipeDream, and 1F1B (interleaved) schedules minimize bubble overhead.

**3D Parallelism**
DP × TP × PP: data parallel across super-nodes, tensor parallel within a node, pipeline parallel across nodes. Used for 100B+ parameter models. Megatron-Turing NLG (530B) used 280 × 8-way TP × 35-way PP.

---

## 5. Architecture Diagrams

```
DDP: Data Parallel (2 nodes x 4 GPUs = 8 processes)

Node 0                              Node 1
+--GPU0---+ +--GPU1---+            +--GPU4---+ +--GPU5---+
| Model   | | Model   |            | Model   | | Model   |
| copy    | | copy    |            | copy    | | copy    |
| batch_0 | | batch_1 |            | batch_4 | | batch_5 |
+----+----+ +----+----+            +----+----+ +----+----+
     |           |     AllReduce        |           |
     +-----------+------(NCCL)----------+-----------+
                  (sum gradients across all 8 GPUs)
                  (broadcast averaged gradient back)
                  Each GPU applies identical update


FSDP: Fully Sharded Data Parallel (4 GPUs, simplified)

     GPU 0        GPU 1        GPU 2        GPU 3
  +--------+   +--------+   +--------+   +--------+
  | Param  |   | Param  |   | Param  |   | Param  |
  | shard0 |   | shard1 |   | shard2 |   | shard3 |
  | Grad   |   | Grad   |   | Grad   |   | Grad   |
  | shard0 |   | shard1 |   | shard2 |   | shard3 |
  | Optim  |   | Optim  |   | Optim  |   | Optim  |
  | shard0 |   | shard1 |   | shard2 |   | shard3 |
  +---+----+   +---+----+   +---+----+   +---+----+
      |             |             |             |
  AllGather before forward pass (gather full param tensor)
  ReduceScatter after backward (scatter reduced gradients)


Pipeline Parallelism (4 stages, 4 micro-batches, 1F1B schedule)

Stage 0 [F1][F2][F3][F4][B4][B3][B2][B1]
Stage 1      [F1][F2][F3][F4][B4][B3][B2][B1]
Stage 2           [F1][F2][F3][F4][B4][B3][B2][B1]
Stage 3                [F1][F2][F3][F4][B4][B3][B2][B1]
                                    ^--- steady state: all stages active
F=forward, B=backward, pipeline bubble at start/end
```

---

## 6. How It Works — Detailed Mechanics

### PyTorch DDP — Correct Setup

```python
import os
import torch
import torch.distributed as dist
from torch.nn.parallel import DistributedDataParallel as DDP
from torch.utils.data import DataLoader, DistributedSampler
from torch.cuda.amp import GradScaler, autocast
from torch import nn
from typing import Optional


def setup_distributed(backend: str = "nccl") -> tuple[int, int]:
    """
    Initialize process group. Called once per process.
    Reads RANK, LOCAL_RANK, WORLD_SIZE from environment (set by torchrun).
    Returns (rank, world_size).
    """
    dist.init_process_group(backend=backend)
    rank = dist.get_rank()
    world_size = dist.get_world_size()
    local_rank = int(os.environ["LOCAL_RANK"])
    torch.cuda.set_device(local_rank)
    return rank, world_size


def cleanup_distributed() -> None:
    dist.destroy_process_group()


def train_ddp(
    model: nn.Module,
    dataset,
    epochs: int = 10,
    batch_size: int = 64,
    lr: float = 1e-3,
    gradient_accumulation_steps: int = 4,
) -> None:
    rank, world_size = setup_distributed()
    device = torch.device(f"cuda:{int(os.environ['LOCAL_RANK'])}")

    # Move model to device BEFORE wrapping with DDP
    model = model.to(device)
    model = DDP(
        model,
        device_ids=[int(os.environ["LOCAL_RANK"])],
        # find_unused_parameters=True adds overhead; only enable if needed
        find_unused_parameters=False,
        # Gradient bucket size: larger = fewer AllReduce calls, more memory
        bucket_cap_mb=25,
    )

    # DistributedSampler ensures each rank gets a different data slice
    sampler = DistributedSampler(
        dataset,
        num_replicas=world_size,
        rank=rank,
        shuffle=True,
        drop_last=True,  # avoid uneven batch sizes across ranks
    )
    loader = DataLoader(dataset, batch_size=batch_size, sampler=sampler, num_workers=4, pin_memory=True)

    optimizer = torch.optim.AdamW(model.parameters(), lr=lr)
    # BF16: better numerical range than FP16, no GradScaler needed on A100/H100
    # FP16: use GradScaler to handle underflow
    scaler = GradScaler()  # for FP16 training

    for epoch in range(epochs):
        # Must set epoch so DistributedSampler reshuffles correctly each epoch
        sampler.set_epoch(epoch)
        optimizer.zero_grad()

        for step, (inputs, labels) in enumerate(loader):
            inputs, labels = inputs.to(device), labels.to(device)

            # Gradient accumulation: disable sync on all but the last step
            sync_context = (
                model.no_sync()
                if (step + 1) % gradient_accumulation_steps != 0
                else contextlib.nullcontext()
            )

            with sync_context:
                with autocast(dtype=torch.float16):  # use torch.bfloat16 on A100
                    outputs = model(inputs)
                    loss = criterion(outputs, labels)
                    loss = loss / gradient_accumulation_steps

                scaler.scale(loss).backward()

            if (step + 1) % gradient_accumulation_steps == 0:
                # Unscale before gradient clipping
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                scaler.step(optimizer)
                scaler.update()
                optimizer.zero_grad()

        # Only rank 0 saves checkpoint
        if rank == 0:
            torch.save({
                "epoch": epoch,
                "model_state_dict": model.module.state_dict(),  # .module to unwrap DDP
                "optimizer_state_dict": optimizer.state_dict(),
            }, f"checkpoint_epoch_{epoch}.pt")

    cleanup_distributed()


# Launch with: torchrun --nproc_per_node=8 --nnodes=2 --node_rank=0 \
#              --master_addr=<ip> --master_port=29500 train.py
```

### PyTorch FSDP — Large Model Training

```python
import torch
from torch.distributed.fsdp import (
    FullyShardedDataParallel as FSDP,
    MixedPrecision,
    ShardingStrategy,
    StateDictType,
    FullStateDictConfig,
)
from torch.distributed.fsdp.wrap import size_based_auto_wrap_policy
import functools
from transformers import AutoModelForCausalLM


def setup_fsdp_model(model_name: str, min_num_params: int = 1_000_000) -> FSDP:
    """
    Wrap a HuggingFace model with FSDP.
    size_based_auto_wrap_policy wraps any submodule with >= min_num_params parameters.
    For a 7B model with 32 transformer blocks, each block is ~218M params — all wrapped.
    """
    model = AutoModelForCausalLM.from_pretrained(model_name, torch_dtype=torch.bfloat16)

    # Auto-wrap policy: wrap modules with >= 1M parameters
    auto_wrap_policy = functools.partial(
        size_based_auto_wrap_policy,
        min_num_params=min_num_params,
    )

    # BF16 mixed precision: safe on A100/H100, avoids FP16 overflow
    bf16_policy = MixedPrecision(
        param_dtype=torch.bfloat16,
        reduce_dtype=torch.bfloat16,
        buffer_dtype=torch.bfloat16,
    )

    fsdp_model = FSDP(
        model,
        auto_wrap_policy=auto_wrap_policy,
        mixed_precision=bf16_policy,
        sharding_strategy=ShardingStrategy.FULL_SHARD,  # ZeRO Stage 3 equivalent
        # SHARD_GRAD_OP = ZeRO Stage 2 (shard grads + optimizer, not params)
        device_id=torch.cuda.current_device(),
        use_orig_params=True,  # required for torch.compile compatibility
    )
    return fsdp_model


def save_fsdp_checkpoint(fsdp_model: FSDP, save_path: str, rank: int) -> None:
    """
    Save FSDP model checkpoint. Must gather shards to rank 0 first.
    """
    save_policy = FullStateDictConfig(offload_to_cpu=True, rank0_only=True)
    with FSDP.state_dict_type(fsdp_model, StateDictType.FULL_STATE_DICT, save_policy):
        cpu_state = fsdp_model.state_dict()

    if rank == 0:
        torch.save(cpu_state, save_path)
```

### DeepSpeed ZeRO Configuration

```python
# deepspeed_config.json — ZeRO Stage 2
ds_config = {
    "train_batch_size": 256,
    "train_micro_batch_size_per_gpu": 8,     # effective batch = 256 across 32 GPUs
    "gradient_accumulation_steps": 1,
    "bf16": {"enabled": True},
    "zero_optimization": {
        "stage": 2,                           # Stage 2: optimizer + gradient sharding
        "allgather_partitions": True,
        "allgather_bucket_size": 500_000_000, # 500 MB AllGather bucket
        "overlap_comm": True,                 # overlap gradient comm with backward pass
        "reduce_scatter": True,
        "reduce_bucket_size": 500_000_000,
        "contiguous_gradients": True,         # reduces memory fragmentation
    },
    "gradient_clipping": 1.0,
    "optimizer": {
        "type": "AdamW",
        "params": {"lr": 1e-4, "betas": [0.9, 0.95], "eps": 1e-8, "weight_decay": 0.1}
    },
    "scheduler": {
        "type": "WarmupDecayLR",
        "params": {"warmup_min_lr": 0, "warmup_max_lr": 1e-4, "warmup_num_steps": 2000, "total_num_steps": 100000}
    }
}
```

---

## 7. Real-World Examples

**Meta — LLaMA 3 (70B)**: trained on 15 trillion tokens using 2,048 H100 GPUs. Used 3D parallelism: 8-way tensor parallelism within a node (NVLink), 16-way pipeline parallelism across nodes, data parallelism across super-nodes. Achieved ~38% MFU. Training took approximately 3.4 million GPU-hours.

**Google — PaLM (540B)**: trained on 6,144 TPU v4 chips using Pathways (a cross-accelerator system). 12-way model parallelism, 24-way pipeline parallelism, data parallel across pods. TPU interconnect (ICI, 600 GB/s) enabled aggressive tensor parallelism.

**OpenAI — GPT-4 (rumored ~1.8T MoE)**: distributed training details not disclosed, but inference is served from multi-node A100 clusters with tensor parallelism.

**Hugging Face — community fine-tuning**: standard pattern is DDP with 8 A100 GPUs for 7B-13B models, FSDP or DeepSpeed ZeRO-3 for 70B models. QLoRA + DDP fits 70B fine-tuning on 4x A100 40GB.

---

## 8. Tradeoffs

| Strategy | Memory/GPU | Communication | Complexity | Max Model Size | Use When |
|---|---|---|---|---|---|
| DDP | Full model × N | AllReduce (gradients only) | Low | Fits in 1 GPU | Standard multi-GPU training |
| FSDP SHARD_GRAD_OP | Full params + 1/N optimizer+grad | ReduceScatter + AllGather | Medium | ~1.5x single GPU | Model just exceeds GPU memory |
| FSDP FULL_SHARD | 1/N params + 1/N optimizer+grad | 2× communication | Medium | N × GPU VRAM | Large models (7B-70B) |
| DeepSpeed ZeRO-3 | Same as FSDP FULL_SHARD | Similar | High (JSON config) | N × GPU VRAM | Large models, DeepSpeed ecosystem |
| ZeRO-Infinity | ~1/N GPU + CPU/NVMe | Much slower | Very high | Virtually unlimited | Research, very large models |

| Precision | Memory | Stability | Hardware | Notes |
|---|---|---|---|---|
| FP32 | 4 bytes/param | Very stable | All GPUs | Too slow and large for LLMs |
| FP16 | 2 bytes/param | Overflow risk | All GPUs | Needs GradScaler; master weights in FP32 |
| BF16 | 2 bytes/param | Stable (same range as FP32) | A100/H100/TPU | Preferred for modern LLM training |

---

## 9. When to Use / When NOT to Use

**Use DDP when**: model fits on a single GPU, you want maximum simplicity, training on 2-64 GPUs, team is familiar with PyTorch but not distributed systems.

**Use FSDP when**: model parameters exceed single GPU VRAM, training 7B-70B parameter models, using PyTorch 1.12+ and want native PyTorch solution without external dependencies.

**Use DeepSpeed ZeRO when**: working in a DeepSpeed ecosystem (Megatron-DeepSpeed), need ZeRO-Infinity (NVMe offload) for very large models, need CPU-Adam optimizer for massive optimizer state offload.

**Use tensor parallelism when**: working within a single node with fast NVLink interconnect, model width (hidden dimension) is large enough to split (Megatron-style TP works well for hidden_dim >= 4096), using Megatron-LM or compatible framework.

**Do NOT use DDP when**: model does not fit in a single GPU's VRAM — DDP keeps a full copy on each GPU.

**Do NOT use FSDP with tiny models**: the AllGather/ReduceScatter overhead dominates for small models (< 1B parameters). DDP is faster.

**Do NOT mix tensor parallelism with slow interconnect**: tensor parallelism requires multiple AllReduce operations per layer during forward and backward passes — running TP across nodes over Ethernet (10 Gbps) instead of NVLink (600 Gbps) will make training slower than single-GPU.

---

## 10. Common Pitfalls

**Pitfall 1 — Forgetting sampler.set_epoch(epoch)**
Production incident: a team used `DistributedSampler` but did not call `sampler.set_epoch(epoch)` at the start of each epoch. The sampler uses the epoch number to seed its shuffle — without updating it, all epochs trained on the same data order. The model converged to a worse local minimum (0.7% lower accuracy) and the team spent 2 weeks investigating optimizer hyperparameters before discovering the root cause. Fix: always call `sampler.set_epoch(epoch)` at the top of the epoch loop.

**Pitfall 2 — Saving checkpoint from all ranks**
In DDP training, a team had every process call `torch.save(model.state_dict(), path)`. With 64 processes all writing to the same NFS path simultaneously, they hit filesystem corruption (partial overwrites). Fix: gate checkpoint writes with `if dist.get_rank() == 0`. For FSDP, use `StateDictType.FULL_STATE_DICT` with `rank0_only=True` to gather the full model only on rank 0 before saving.

**Pitfall 3 — Using DataParallel instead of DDP**
A team's training throughput scaled from 1 GPU to 4 GPUs as: 1 GPU → 3.2x (not 4x). They were using `nn.DataParallel` (single-process, all GPUs on one thread). The master GPU held all activations for all sub-batches before the gather step — causing 70% GPU memory utilization on rank 0 and only 30% on ranks 1-3. Migrating to `torchrun` + DDP achieved 3.8x speedup with 4 GPUs. DP is single-process; DDP is multi-process — always use DDP.

**Pitfall 4 — FSDP and non-wrapped shared parameters**
A model had an embedding layer shared (tied weights) between the input embedding and the output projection (standard in transformer LMs). FSDP wrapped them separately, creating two shards of what should be one tensor. Gradient updates desynchronized the tied weights over training — loss oscillated instead of decreasing. Fix: use `FSDP.set_state_dict_type` and handle tied weights explicitly; or apply `ignored_modules` to one of the tied layers so FSDP does not shard it independently.

**Pitfall 5 — Gradient accumulation without no_sync()**
A team implemented gradient accumulation with FSDP but did not use `model.no_sync()` for the non-update steps. Each backward pass triggered a ReduceScatter (gradient sync), not just the final step. With 4 accumulation steps, they paid 4x the communication cost instead of 1x. This made training 2.8x slower than DDP despite the memory savings. Fix: wrap intermediate backward passes with `model.no_sync()` context manager.

---

## 11. Technologies & Tools

| Tool | Purpose |
|---|---|
| PyTorch DDP | Standard data-parallel training (multi-process) |
| PyTorch FSDP | Fully sharded data parallel (ZeRO-3 equivalent) |
| DeepSpeed | ZeRO optimizer, ZeRO-Infinity, pipeline parallelism |
| Megatron-LM | Tensor + pipeline parallelism for LLMs (NVIDIA) |
| torchrun | PyTorch distributed launcher (replaces torch.multiprocessing.spawn) |
| NCCL | GPU collective communication library (AllReduce, AllGather) |
| Horovod | Uber's distributed training library (less common now) |
| Accelerate (HuggingFace) | Abstraction over DDP/FSDP/DeepSpeed with minimal code change |
| PyTorch Lightning | Higher-level training loop with distributed strategy plugins |
| TensorFlow MirroredStrategy | TF equivalent of DDP |
| Vertex AI Training | Managed distributed training on Google Cloud |
| AWS SageMaker Training | Managed distributed training with SMP library |
| Weights & Biases | Experiment tracking for distributed runs |
| NVIDIA Nsight Systems | GPU profiling for identifying bottlenecks |

---

## 12. Interview Questions with Answers

**Q: What is the difference between DataParallel and DistributedDataParallel?**
DataParallel uses a single process with multiple threads — one GPU is the master that scatters input data, gathers outputs and gradients, and applies updates. This creates a memory imbalance (master GPU uses more VRAM) and a GIL bottleneck (Python's GIL limits parallelism). DistributedDataParallel uses one process per GPU with no master — each process computes its own forward and backward pass, then NCCL performs AllReduce to synchronize gradients symmetrically. DDP consistently achieves 90-95% linear scaling efficiency; DataParallel typically achieves 60-75%. Always use DDP for new training code.

**Q: How does AllReduce work and which backend should you use?**
AllReduce is a collective operation: every process sends its tensor, the operation (typically sum) is applied element-wise across all tensors, and every process receives the result. In Ring-AllReduce (the standard algorithm), data is sent around a logical ring of processes in two phases: ReduceScatter (each process sends and accumulates a chunk) then AllGather (each process broadcasts its chunk). Use NCCL backend for GPU-to-GPU communication — it leverages NVLink (600 GB/s, A100) and InfiniBand (400 Gb/s HDR). Use Gloo backend only for CPU training. MPI backend is available but NCCL outperforms it for GPU workloads.

**Q: What is gradient accumulation and when should you use it?**
Gradient accumulation postpones the optimizer update by accumulating gradients across M steps before calling `optimizer.step()`. The effective batch size becomes `local_batch × n_gpus × M`. Use it when: (1) the target batch size exceeds what fits in VRAM for a single step; (2) you want to match a larger-batch baseline without adding GPUs; (3) training with FSDP/ZeRO where per-GPU batch is small. Critical implementation detail: in DDP, use `model.no_sync()` for all non-update steps to suppress AllReduce on each intermediate backward pass — only AllReduce on the final accumulation step.

**Q: How does FSDP reduce memory usage compared to DDP?**
In DDP, each GPU holds: full model parameters (M bytes) + full gradients (M bytes) + full optimizer states (for Adam: 8M bytes for momentum and variance) = ~10M bytes total. With FSDP FULL_SHARD across N GPUs, each GPU holds: M/N bytes for parameters + M/N bytes for gradients + 8M/N bytes for optimizer states ≈ 10M/N bytes total — a linear memory reduction. Before each forward pass, FSDP issues AllGather to temporarily materialize the full parameter tensor on each GPU (then frees after use). This means peak memory during forward pass exceeds 1/N, but the steady-state footprint is 1/N.

**Q: What is ZeRO and how do its stages differ?**
ZeRO (Zero Redundancy Optimizer, from DeepSpeed) progressively eliminates memory redundancy across data-parallel ranks: Stage 1 shards optimizer states (Adam m/v) — each rank holds 1/N of optimizer state, reducing optimizer memory by N×. Stage 2 additionally shards gradients — gradients are immediately discarded on each rank after ReduceScatter, saving another ~M bytes. Stage 3 additionally shards parameters — equivalent to FSDP FULL_SHARD. ZeRO-Infinity extends Stage 3 by offloading parameter and optimizer shards to CPU RAM and NVMe storage, enabling trillion-parameter models at the cost of slower training (PCIe bandwidth ~32 GB/s vs NVLink 600 GB/s).

**Q: Why is BF16 preferred over FP16 for training large language models?**
BF16 (Brain Float 16) and FP16 both use 2 bytes per value, but BF16 allocates 8 exponent bits (same as FP32) and 7 mantissa bits; FP16 allocates 5 exponent bits and 10 mantissa bits. The larger exponent range in BF16 (same as FP32) prevents the gradient underflow/overflow that requires GradScaler in FP16 training. LLM training frequently encounters large gradient values during attention head training — FP16 overflows to infinity, causing NaN loss and training instability. BF16 handles these ranges natively. BF16 is supported natively on A100, H100, TPU v3+, and RTX 30/40 series. For older GPUs (V100), FP16 + GradScaler is the only option.

**Q: How do you implement checkpointing in distributed training without bugs?**
Three rules: (1) only rank 0 writes checkpoints — gate all save calls with `if dist.get_rank() == 0`; (2) add a `dist.barrier()` after the save so no other rank proceeds until rank 0 finishes writing (prevents processes from loading a partially-written checkpoint); (3) save `model.module.state_dict()` (unwrap DDP) not `model.state_dict()`. For FSDP: use `FSDP.state_dict_type(model, StateDictType.FULL_STATE_DICT, FullStateDictConfig(offload_to_cpu=True, rank0_only=True))` context manager to gather all shards to rank 0's CPU RAM before saving — this keeps checkpoint files identical to non-FSDP checkpoints.

**Q: What is the linear scaling rule and when does it break down?**
The linear scaling rule states: when multiplying the number of data-parallel workers by N, multiply the learning rate by N (keeping the number of steps constant). The intuition: N workers process N times the data per step, so each gradient update is based on N times more signal and should take N times larger steps. It holds reliably for scale factors up to ~8x. Beyond 8x, gradient noise increases (larger batch means less noisy gradient, but LR cannot scale indefinitely without divergence). Practical fix: use a warmup phase — start with 1× LR, linearly increase to N× LR over the first 5-10 epochs, then apply the schedule normally.

**Q: How does pipeline parallelism work and what is the pipeline bubble?**
Pipeline parallelism assigns consecutive transformer layers to different GPUs (e.g., layers 0-7 on GPU 0, layers 8-15 on GPU 1). Data flows through stages in sequence. The "pipeline bubble" is the startup/teardown idle time: in a standard schedule, GPU 1 cannot start until GPU 0 finishes its forward pass on the first micro-batch; during the backward pass, GPU 0 is idle while GPU 1 computes backward. The bubble fraction is (p-1)/m where p = number of pipeline stages and m = number of micro-batches. With p=4 stages and m=8 micro-batches, bubble = 3/8 = 37.5% wasted. 1F1B (one-forward-one-backward) interleaving reduces bubble to (p-1)/(m×k) where k = number of virtual stages.

**Q: What is model FLOP utilization (MFU) and what numbers should you target?**
MFU = (actual FLOP/s achieved) / (peak theoretical FLOP/s of hardware). For an A100 80GB with BF16 Tensor Cores, peak is 312 TFLOP/s. Actual FLOP/s is estimated from the number of forward and backward pass operations per step (approximately 6 × parameter_count × tokens_per_step). Well-tuned transformer LLM training on A100 clusters achieves 38-45% MFU. Values below 30% indicate a bottleneck: data loading (IO-bound), excessive communication overhead (poorly tuned TP/PP), memory-bound operations (layer norms on small batches), or suboptimal parallelism configuration. Target: ≥ 40% MFU for production training runs.

**Q: What is gradient checkpointing and when should you use it?**
Gradient checkpointing (also called activation recomputation) trades compute for memory: instead of storing all intermediate activations in memory during the forward pass, only a subset of "checkpoint" tensors are stored. During the backward pass, the omitted activations are recomputed from the nearest checkpoint. This reduces activation memory from O(layers × batch × sequence × hidden) to O(sqrt(layers) × batch × sequence × hidden) with optimal placement — roughly a 33-40% reduction in activation memory for transformers. Use it when VRAM is the bottleneck (not compute) — it adds approximately 33% extra compute (one extra forward pass per layer) but reduces peak memory enough to increase batch size or train a larger model.

---

## 13. Best Practices

- Always use `torchrun` (not `torch.multiprocessing.spawn`) for launching distributed jobs — torchrun handles rank assignment, fault tolerance signaling, and rendezvous automatically
- Set `NCCL_DEBUG=INFO` in early development to verify NCCL is using the correct transport (NVLink for intra-node, InfiniBand for inter-node) and detect misconfiguration
- Use `find_unused_parameters=False` in DDP unless the model has conditional computation paths — the default True adds a hook per parameter and up to 10% overhead
- Apply BF16 mixed precision on A100/H100 hardware — do not use FP16 for training LLMs (overflow risk); use FP32 for loss computation and accumulation in scaler if FP16 is unavoidable
- Profile with `torch.profiler.profile()` for 2-3 steps before tuning — identify whether training is compute-bound or communication-bound before adding nodes
- Match global batch size to the `O(sqrt(dataset_size))` heuristic for stable training; do not increase batch size purely because more GPUs are available without adjusting LR
- Use `torch.compile()` (PyTorch 2.0+) together with FSDP and `use_orig_params=True` for up to 20-30% additional throughput from kernel fusion and graph optimization
- Monitor GPU utilization per rank with `nvidia-smi dmon` or W&B system metrics — uneven utilization across ranks indicates data skew or communication bottlenecks
- Set `drop_last=True` in DistributedSampler to avoid incomplete batches that cause hangs when all-reduce operations expect equal-size inputs from all ranks

---

## 14. Case Study

**Scenario: Training a 7B-parameter LLaMA-style model with DeepSpeed ZeRO-3.** The cluster is 64 A100 80GB GPUs (8 nodes x 8 GPUs), NVLink within a node and InfiniBand between nodes. ZeRO-3 partitions optimizer states, gradients, AND parameters across all 64 ranks, so each GPU holds 7B/64 ~ 110M parameters resident. Global batch size 512 with gradient accumulation 4 and micro-batch 2 per GPU.

```
8 nodes x 8 A100 = 64 ranks
  intra-node: NVLink (fast all-reduce within node)
  inter-node: InfiniBand (200Gb/s)

ZeRO-3 sharding (per rank holds 1/64 of):
  parameters  (fp16)   : 7B/64  ~ 110M
  gradients   (fp16)   : 7B/64
  optimizer   (fp32 m,v): 7B/64    <- Adam states dominate memory

forward/backward: all-gather params for the layer in use, then free
global batch = micro(2) x grad_accum(4) x ranks(64) = 512
```

Throughput 85 TFLOPS/GPU = ~57% Model FLOPs Utilization (MFU), strong for a 7B run at this scale. Checkpoints every 500 steps to object storage. Without ZeRO-3, a 7B model with Adam (params + grads + fp32 m/v + fp32 master ~ 16 bytes/param = 112GB) would not fit on a single 80GB GPU.

**DeepSpeed ZeRO-3 config and launch:**

```python
ds_config = {
    "train_micro_batch_size_per_gpu": 2,
    "gradient_accumulation_steps": 4,
    "gradient_clipping": 1.0,                # prevents grad-norm explosion
    "bf16": {"enabled": True},               # bf16 mixed precision
    "zero_optimization": {
        "stage": 3,
        "overlap_comm": True,                # overlap all-gather with compute
        "contiguous_gradients": True,
        "stage3_max_live_parameters": 1_000_000_000,
        "offload_optimizer": {"device": "none"},  # keep on GPU at this scale
    },
}
```

```python
import deepspeed
import torch

def setup(model: torch.nn.Module, params, ds_config: dict):
    engine, optimizer, _, _ = deepspeed.initialize(
        model=model, model_parameters=params, config=ds_config,
    )
    return engine, optimizer
```

**Training loop with periodic checkpointing:**

```python
import deepspeed

def train(engine, loader, total_steps: int, ckpt_dir: str,
          ckpt_every: int = 500) -> None:
    step = 0
    for batch in loader:
        loss = engine(batch["input_ids"], labels=batch["labels"]).loss
        engine.backward(loss)        # grad accumulation handled internally
        engine.step()                # all-reduce + clip + optimizer step
        step += 1
        if step % ckpt_every == 0:
            # DeepSpeed saves sharded optimizer + params across all ranks
            engine.save_checkpoint(ckpt_dir, tag=f"step_{step}")
        if step >= total_steps:
            break
```

**Pitfall 1 — Gradient-norm explosion.** Long-context language modeling can produce occasional huge gradients; without clipping the loss spikes to NaN and the run is dead.

```python
# BROKEN: no gradient clipping -> a single bad batch sends loss to NaN
engine.backward(loss)
engine.step()

# FIX: clip global grad norm (set in ds_config so DeepSpeed applies it
# correctly across the ZeRO shards).
ds_config["gradient_clipping"] = 1.0
```

**Pitfall 2 — Checkpointing too infrequently.** A node fails 6 hours into training; if the last checkpoint was at the start, 6 hours of GPU time (and thousands of dollars) is lost.

```python
# BROKEN: checkpoint once per epoch -> a mid-epoch node failure loses hours
if epoch_done:
    engine.save_checkpoint(ckpt_dir)

# FIX: checkpoint every N steps so worst-case loss is bounded to N steps,
# and write asynchronously to object storage to hide I/O latency.
if step % 500 == 0:
    engine.save_checkpoint(ckpt_dir, tag=f"step_{step}")
```

**Pitfall 3 — Communication bottleneck on slow links.** Putting all 64 GPUs in one flat all-reduce over the slowest link wastes the fast NVLink and stalls on inter-node bandwidth.

```python
# BROKEN: topology-unaware collectives saturate the slow inter-node link
# (all ranks treated identically, NVLink advantage wasted)

# FIX: hierarchical communication, NVLink within node, InfiniBand between
# nodes; enable overlap_comm so all-gather overlaps with compute.
ds_config["zero_optimization"]["overlap_comm"] = True
# launch with NCCL topology awareness:  NCCL_NET_GDR_LEVEL, NCCL_IB_HCA set
```

**Interview Q&A:**

**What does each ZeRO stage partition, and why does stage 3 enable 7B on 80GB GPUs?** Stage 1 partitions optimizer states, stage 2 adds gradients, stage 3 adds the parameters themselves. With Adam, optimizer states (fp32 momentum + variance + master weights) are the largest memory consumer; sharding all three across 64 ranks means each holds only 1/64 of everything, so the per-GPU footprint drops from ~112GB to single-digit GB plus transient all-gathered layers.

**What is MFU and why is 57% considered good?** Model FLOPs Utilization is achieved FLOPs divided by the hardware peak. Communication, memory stalls, data loading, and bubble time all reduce it. For large distributed training, 50-60% MFU is strong; below 35% usually signals a data-loading or communication bottleneck worth profiling.

**Why bf16 instead of fp16 for the 7B run?** bf16 has the same exponent range as fp32, so it avoids the gradient-underflow and overflow problems that force fp16 to use loss scaling. On A100 the throughput is comparable, and bf16 training is more numerically stable for large models, which is why it is the default for modern LLM pretraining.

**How does gradient accumulation interact with global batch size?** Global batch = micro-batch x accumulation steps x number of ranks. Accumulation lets you reach a large effective batch (512 here) without the memory of holding it all at once: gradients sum over several micro-batches before a single optimizer step. The all-reduce happens once per optimizer step, not per micro-batch.

**Why checkpoint every 500 steps rather than per epoch?** At 64-GPU scale, epochs are long and hardware failures are frequent enough that per-epoch checkpointing risks losing hours of compute. Step-level checkpointing bounds the worst-case loss to N steps. Writing asynchronously to durable object storage keeps the checkpoint off the critical path so it does not stall training.

**How do you diagnose a communication bottleneck?** Profile with the PyTorch profiler or DeepSpeed's wall-clock breakdown and look for high all-gather/all-reduce time relative to compute. Fixes include enabling overlap_comm so communication hides behind compute, ensuring NVLink is used intra-node and InfiniBand inter-node, tuning NCCL environment variables, and increasing micro-batch size to improve the compute-to-communication ratio.

**Pitfall — DDP bucket size too small causes excessive AllReduce communication.**

```python
# BROKEN: default DDP bucket_cap_mb=25 creates 40 small AllReduce calls
# for a 1B-param model → communication overhead dominates at scale
from torch.nn.parallel import DistributedDataParallel as DDP
model = DDP(model)   # default 25MB buckets → 40 AllReduce calls per backward pass

# FIX: increase bucket size to reduce AllReduce frequency
model = DDP(model, bucket_cap_mb=100)  # 10 AllReduce calls → 2× throughput
# Tune: larger buckets reduce communication calls but delay gradient synchronization
# Target: bucket_cap_mb = total_params_bytes / (desired_allreduce_calls × num_gpus)
```

**How does ZeRO Stage 3 achieve linear memory scaling with the number of GPUs?** ZeRO Stage 3 shards optimizer states (Stage 1), gradients (Stage 2), and model parameters (Stage 3) across all GPU workers. With N GPUs and a 70B-parameter model: each GPU holds only 1/N of the parameters in memory at any time (70B/N × 2 bytes/param for BF16). During the forward pass, parameters are gathered from all GPUs via `all_gather`, used locally, then freed. This reduces per-GPU memory from O(model_size) to O(model_size/N), enabling models larger than single-GPU VRAM. The cost: 3× more `all_gather` communication volume vs. DDP.

**When should you prefer FSDP over DDP for model training?** Use DDP when the model fits in single-GPU memory with a reasonable batch size — DDP is simpler and faster (no sharding overhead). Switch to FSDP (or DeepSpeed ZeRO) when: (1) the model doesn't fit in single-GPU memory (e.g., > 40GB on an 80GB A100 with activations + optimizer states); (2) you want to scale to hundreds of GPUs without pipeline parallelism complexity; (3) you need gradient checkpointing combined with sharding. FSDP's `auto_wrap_policy` with `transformer_auto_wrap_policy` is the standard setup for LLM training.

**What is gradient accumulation and when does it substitute for a larger batch size?** Gradient accumulation runs N forward-backward passes before calling `optimizer.step()`, simulating an effective batch size of `batch_size × N`. It allows training with effective batch sizes (e.g., 4096) on GPUs with limited VRAM (which can only fit 256 samples per step) without multi-GPU infrastructure. The trade-off: N sequential passes take N× longer than one large batch would — throughput is identical to a smaller batch but memory is lower. Use gradient accumulation when: you need a large batch size for training stability (LLM training typically uses effective batch 1M+ tokens), but GPU VRAM limits the physical batch size.

**What is tensor parallelism and when is it preferable to data parallelism?** Tensor parallelism (TP) splits individual weight matrices across GPUs — for a linear layer `Y = XW`, TP splits `W` column-wise across GPUs, each computing part of the output. TP requires `all_reduce` after every linear layer (per-layer synchronization), so it is only efficient with high-bandwidth interconnects (NVLink: 600GB/s, vs. PCIe: 32GB/s). Use TP when a single layer's weights don't fit in one GPU's VRAM (e.g., a 40,000×40,000 attention projection at FP16 = 3.2GB per matrix). Data parallelism scales easily across nodes with slower interconnects (Ethernet); TP is for intra-node scaling on NVLink-connected GPU clusters.

**How does pipeline parallelism differ from tensor parallelism, and when do you combine them?** Pipeline parallelism (PP) splits model layers across GPUs — GPU 0 runs layers 1-8, GPU 1 runs layers 9-16, etc. Each micro-batch flows through the pipeline sequentially; multiple micro-batches are interleaved to reduce pipeline bubbles (idle time). PP requires only forward activations to be transferred between pipeline stages (lower bandwidth than TP's all-reduce), making it suitable for slower inter-node connections. In Megatron-LM and DeepSpeed, both are combined (3D parallelism): TP within a node (NVLink), PP across nodes (InfiniBand), DP across replica groups — enabling training of models with 100B+ parameters across 1000+ GPUs.

---

**Quick-reference comparison table:**

| Approach | When to use | Trade-off |
|---|---|---|
| Rule-based baseline | Always — establish before ML | Interpretable, brittle on edge cases |
| Simple ML (LR, RF) | < 100k rows, tabular, fast iteration | Lower ceiling than deep models |
| Deep learning | Large data, unstructured input (images/text) | Expensive training, needs GPU |
| Ensembling | Final 1-2% accuracy gain in competition | Complexity, inference latency |
| Distillation/quantization | Inference cost reduction | Accuracy-efficiency trade-off |

---

## See Also
- [Training Infrastructure (LLM)](../../llm/training_infrastructure/README.md) — tensor/pipeline parallelism, ZeRO, FSDP at LLM scale (70B+ parameters)
- [ML Platform & GPU Infrastructure (DevOps)](../../devops/ml_platform_and_gpu_infrastructure/README.md) — GPU node lifecycle, cluster orchestration, cost management
