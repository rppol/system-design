# vLLM Deep Dive

## Intuition

> **One-line analogy**: vLLM is to LLM serving what a database's buffer pool manager is to query execution — it reimagines memory management from scratch to eliminate waste and maximize throughput.

**Mental model**: A naive LLM server allocates a fixed KV cache block per request at arrival time, holds it until completion, and serves one batch at a time. GPU memory fragments, utilization drops to 30-40%, and throughput plateaus. vLLM's PagedAttention borrows virtual memory concepts from OS design: KV cache is divided into fixed-size pages; pages are allocated on demand and can be non-contiguous; requests share pages when their prefixes match. The result: 24× higher throughput than Hugging Face Transformers on the same hardware in the original paper.

**Why it matters**: vLLM is the dominant open-source inference engine. Understanding it means understanding the engineering that makes production LLM serving economically viable — and being able to tune, debug, and architect around it.

**Key insight**: Almost every vLLM optimization (PagedAttention, continuous batching, prefix caching, chunked prefill, speculative decoding) attacks the same root problem: GPU memory bandwidth is the bottleneck during autoregressive decoding, not compute. Every feature is about keeping data on-chip longer, transferring less, or batching more requests to amortize the transfer cost.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [PagedAttention](#2-pagedattention)
3. [Continuous Batching](#3-continuous-batching)
4. [Scheduler](#4-scheduler)
5. [KV Cache Management](#5-kv-cache-management)
6. [Prefix Caching (APC)](#6-prefix-caching-apc)
7. [Chunked Prefill](#7-chunked-prefill)
8. [Speculative Decoding](#8-speculative-decoding)
9. [Quantization](#9-quantization)
10. [Distributed Inference](#10-distributed-inference)
11. [LoRA and Adapter Serving](#11-lora-and-adapter-serving)
12. [Structured Output](#12-structured-output)
13. [Multimodal Support](#13-multimodal-support)
14. [OpenAI-Compatible API](#14-openai-compatible-api)
15. [Metrics and Monitoring](#15-metrics-and-monitoring)
16. [Production Deployment](#16-production-deployment)
17. [Key Startup Flags](#17-key-startup-flags)
18. [vLLM v0 vs v1 Architecture](#18-vllm-v0-vs-v1-architecture)
19. [Performance Numbers](#19-performance-numbers)
20. [Interview Questions](#20-interview-questions)

---

## 1. Architecture Overview

vLLM separates concerns into three layers:

```
┌─────────────────────────────────────────────────────┐
│                   API Server                        │
│  FastAPI + OpenAI-compatible endpoints              │
│  /v1/completions  /v1/chat/completions  /v1/models  │
└────────────────────┬────────────────────────────────┘
                     │  AsyncEngine
┌────────────────────▼────────────────────────────────┐
│                LLM Engine                           │
│  ┌──────────────┐  ┌──────────────────────────────┐ │
│  │  Scheduler   │  │   KV Cache Manager           │ │
│  │  (FCFS/      │  │   (BlockAllocator,           │ │
│  │   Priority)  │  │    PagedAttention blocks)    │ │
│  └──────┬───────┘  └──────────────────────────────┘ │
│         │ sequence groups                            │
└─────────┼───────────────────────────────────────────┘
          │
┌─────────▼───────────────────────────────────────────┐
│                 Worker(s)                           │
│  ┌─────────────────────────────────────────────┐   │
│  │  ModelRunner                                │   │
│  │  - forward() with PagedAttention kernels    │   │
│  │  - Sampler (temperature, top-p, top-k)      │   │
│  └─────────────────────────────────────────────┘   │
│  GPU 0          GPU 1          GPU N                │
└─────────────────────────────────────────────────────┘
```

**Key objects:**
- **`LLMEngine`** — orchestrates scheduling and execution; the central coordinator
- **`Scheduler`** — decides which sequences to run each step (prefill vs decode, preemption)
- **`BlockSpaceManager`** — manages KV cache block allocation, mapping logical → physical blocks
- **`ModelRunner`** — executes the forward pass with paged attention CUDA kernels
- **`Sampler`** — applies sampling parameters (temperature, top-p, top-k, min-p, penalties) to logits

---

## 2. PagedAttention

### The Problem It Solves

In standard attention, the KV cache for a request must be pre-allocated as one contiguous block:
```
Request A (512 tokens):  [KKKKKK...VVVVVV...]  512 * 2 * layers * head_dim * 2 bytes
Request B (128 tokens):  [KK...VV...]           128 * ...
```

Problems:
- **Internal fragmentation**: allocate for max_len, use only current_len — wasted GPU RAM
- **External fragmentation**: gaps between blocks prevent fitting new requests
- **No sharing**: two requests with identical system prompts each store their own KV copy

### PagedAttention Solution

Divide KV cache into fixed-size **pages** (called blocks in vLLM, default 16 tokens each):

```
Physical GPU Memory (KV Cache Pool)
┌────┬────┬────┬────┬────┬────┬────┬────┐
│ B0 │ B1 │ B2 │ B3 │ B4 │ B5 │ B6 │ B7 │  ← physical blocks
└────┴────┴────┴────┴────┴────┴────┴────┘

Request A logical view:   [0][1][2]        → maps to physical [B0][B3][B7]
Request B logical view:   [0][1]           → maps to physical [B1][B4]
Shared prefix (A+B):      [0]              → maps to shared physical [B2]
```

**Block table** per sequence maps logical block index → physical block index. The attention kernel uses this table to gather K/V from non-contiguous physical memory.

### Memory Formula

```
KV cache size per token per layer:
  = 2 (K and V) × num_kv_heads × head_dim × bytes_per_element

Total KV cache pool:
  = num_layers × tokens_per_block × num_blocks × above_formula

Example: LLaMA 3 8B (FP16)
  = 32 layers × 8 KV heads × 128 head_dim × 2 bytes
  = 524,288 bytes per token = 512 KB per token
  Block size 16 tokens → 8 MB per block
  A100 80GB: reserve ~60GB for KV cache → ~7,500 blocks → ~120K token capacity
```

### Block Size Tradeoff

| Block size | Pros | Cons |
|---|---|---|
| Small (8) | Less internal fragmentation, finer sharing | More block table overhead, worse memory locality |
| Large (32) | Better locality, less bookkeeping | More wasted memory for short sequences |
| Default (16) | Balanced for most workloads | |

---

## 3. Continuous Batching

### Static vs Continuous Batching

**Static batching (naive):**
```
Batch step 1:  [Req A: 200 tokens] [Req B: 200 tokens]   ← wait for both to finish
Batch step 2:  [Req C: ...       ] [Req D: ...       ]   ← GPU idle while waiting
```
GPU sits idle waiting for the longest sequence in the batch. Throughput = min(slowest req).

**Continuous batching (vLLM):**
```
Step 1:  [A: decode] [B: decode] [C: prefill ←NEW]
Step 2:  [A: decode] [B: done → D: prefill] [C: decode]
Step 3:  [A: done → E: prefill] [D: decode] [C: decode]
```
New requests join the batch the moment a slot opens. GPU utilization stays near 100%.

### How It Works Internally

Each forward pass processes a **SchedulerOutput** containing:
- **Prefill sequences**: new tokens being processed (compute-heavy)
- **Decode sequences**: one new token generated per step per sequence (memory-bandwidth-heavy)

The engine iterates:
```
while True:
    scheduler_output = scheduler.schedule()        # decide which seqs to run
    model_output = model_runner.execute_model(scheduler_output)
    seq_group_metadata = process_outputs(model_output)  # sample, check stop
    scheduler.update(seq_group_metadata)           # free completed seqs, add new
```

---

## 4. Scheduler

The scheduler runs every step and answers: **which sequences get GPU time this step?**

### Scheduling Queues

```
WAITING → RUNNING → (done) or → SWAPPED
              ↑                      |
              └──────────────────────┘ (swap back in when memory available)
```

- **WAITING**: requests that have arrived but haven't started
- **RUNNING**: sequences currently being processed (in GPU KV cache)
- **SWAPPED**: sequences preempted — KV cache moved to CPU RAM to make room

### Preemption

When GPU KV cache is full and a new high-priority request arrives:
1. **Recomputation**: drop the lowest-priority running sequence entirely; recompute its KV cache when re-scheduled (wastes compute, saves memory bandwidth vs swap)
2. **Swapping**: copy KV cache blocks to CPU RAM via PCIe, restore later (saves compute, uses CPU RAM and PCIe bandwidth)

Default: recomputation for short sequences, swapping for long ones.

### Priority Scheduling

```bash
# FCFS (default)
--scheduling-policy fcfs

# Priority-based (v0.6+)
--scheduling-policy priority
# Per-request priority via API:
# {"priority": 5}  # lower = higher priority
```

---

## 5. KV Cache Management

### BlockAllocator

Two allocators:
- **GPU allocator**: manages physical blocks in GPU HBM
- **CPU allocator**: manages blocks in CPU RAM (for swapped sequences)

Block states:
```
FREE → ALLOCATED (ref_count=1) → SHARED (ref_count>1, copy-on-write) → FREE
```

### Copy-on-Write (CoW)

When prefix caching is active and two sequences share a physical block, writing a new token to that block would corrupt the other sequence's cache. vLLM uses CoW: before writing, allocate a new physical block and copy — same as OS virtual memory CoW.

### `gpu_memory_utilization`

```bash
--gpu-memory-utilization 0.9  # use 90% of GPU memory for model + KV cache
```

vLLM profiles actual model weight memory, then allocates all remaining GPU memory (up to this fraction) for KV cache blocks. More blocks = more concurrent requests = higher throughput.

---

## 6. Prefix Caching (APC)

**Automatic Prefix Caching** reuses KV cache across requests that share a common prefix (system prompt, few-shot examples, RAG context).

### How It Works

vLLM maintains a **radix tree** (hash trie) indexed by token sequences:

```
System prompt tokens: [1, 2, 3, 4, 5, 6, 7, 8]
                      └─ hashed → Block ID 42 (cached)

Request A: [sys_prompt] + [user_A]
  → Block 42 (HIT, reuse) + new blocks for user_A

Request B: [sys_prompt] + [user_B]
  → Block 42 (HIT, reuse) + new blocks for user_B

Request C: [sys_prompt] + [user_A] + [assistant_A] + [user_C]  (multi-turn)
  → Block 42 (HIT) + blocks for user_A+assistant_A (HIT if cached) + new
```

The block hash is computed over the token IDs in the block. Matching hash → the KV cache for that prefix is already computed.

### Enabling APC

```bash
# Server
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-8B-Instruct \
    --enable-prefix-caching

# Python API
llm = LLM(model="meta-llama/Meta-Llama-3-8B-Instruct", enable_prefix_caching=True)
```

### Performance Impact

| Scenario | Cache hit rate | Latency reduction |
|---|---|---|
| Same system prompt, different users | ~60-80% tokens cached | 40-70% TTFT reduction |
| Multi-turn conversation | Grows with turns | Up to 90% on long histories |
| RAG with fixed context | Very high | Near-instant prefill for cached context |
| Fully unique requests | 0% | No benefit, no overhead |

### APC vs SGLang RadixAttention

| | vLLM APC | SGLang RadixAttention |
|---|---|---|
| Granularity | Block-level (16 tokens) | Token-level |
| Sharing | Across requests | Across requests + within programs |
| Eviction | LRU | LRU with reference counting |
| API | Transparent | Transparent |

---

## 7. Chunked Prefill

### The Problem

Prefill (processing the prompt) and decode (generating tokens) compete for GPU resources. A long prompt (10K tokens) takes many milliseconds to prefill, during which decode requests stall — causing high TTFT (Time to First Token) for other users.

### Solution: Chunk the Prefill

Instead of processing a full prompt in one shot, break it into chunks of `--max-num-batched-tokens` and interleave with decode steps:

```
Without chunked prefill:
  Step 1: [prefill 8192 tokens]           ← decode requests stall
  Step 2: [decode] [decode] [decode]

With chunked prefill (chunk=512):
  Step 1: [prefill 0-511] [decode] [decode]
  Step 2: [prefill 512-1023] [decode] [decode]
  ...
  Step 16: [prefill 7680-8191] [decode] [decode]
```

**Effect:**
- TTFT for existing decode requests drops dramatically (no more stalls)
- TTFT for the chunked request increases slightly (more steps to finish prefill)
- Overall system latency distribution becomes more predictable

### Configuration

```bash
--enable-chunked-prefill \
--max-num-batched-tokens 512    # tokens processed per step (prefill + decode)
```

### Tradeoffs

| | Chunked Prefill ON | Chunked Prefill OFF |
|---|---|---|
| Decode TTFT | Low (interleaved) | High (blocked by long prefills) |
| Prefill TTFT | Slightly higher | Minimal |
| GPU utilization | More consistent | Bursty |
| Recommended when | Mixed short/long prompts | Mostly uniform prompts |

---

## 8. Speculative Decoding

Autoregressive decoding generates one token per forward pass. Speculative decoding generates multiple tokens per pass using a cheap draft model, then verifies them with the target model in parallel.

### How It Works

```
Step 1: Draft model generates 5 candidate tokens cheaply:
        [the] [cat] [sat] [on] [mat]

Step 2: Target model verifies all 5 in ONE forward pass:
        P(the|ctx)=0.9 ✓  P(cat|..the)=0.8 ✓  P(sat|..cat)=0.7 ✓
        P(on|..sat)=0.3 ✗  ← reject here

Step 3: Accept [the][cat][sat], reject [on][mat]
        Sample corrected token after [sat] from target distribution

Net: 3 tokens in ~1 target forward pass instead of 3 separate passes.
```

**Speedup condition**: draft acceptance rate must be high enough that the overhead of running the draft model is worth it. Typically achieves 1.5-3× speedup on repetitive text.

### vLLM Speculative Decoding Options

#### Option 1: Draft Model

```bash
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-70B-Instruct \
    --speculative-model meta-llama/Meta-Llama-3-8B-Instruct \
    --num-speculative-tokens 5 \
    --speculative-draft-tensor-parallel-size 1
```

The draft model must share the same tokenizer and vocabulary as the target model.

#### Option 2: N-gram Speculator (no draft model needed)

```bash
--speculative-model "[ngram]" \
--num-speculative-tokens 5 \
--ngram-prompt-lookup-min 4 \  # min n-gram length to match
--ngram-prompt-lookup-max 8    # max n-gram length to try
```

Predicts next tokens by finding matching n-grams in the prompt. Works well for:
- Code completion (variable names, boilerplate)
- Document continuation with repeated phrases
- RAG (model echoes retrieved text)

#### Option 3: MedusaHeads / EAGLE

```bash
--speculative-model /path/to/eagle-llama3-instruct-8b \
--speculative-draft-tensor-parallel-size 1
```

EAGLE adds a lightweight draft head trained on top of the target model's hidden states — higher acceptance rate than a separate small model.

### Performance Impact

| Method | Speedup | Memory overhead | Best for |
|---|---|---|---|
| Draft model (small) | 1.5–2.5× | Model weights for draft | General text |
| N-gram | 1.2–2× | None | Repetitive/structured text |
| EAGLE | 2–3× | Small head weights | Code, structured output |
| Medusa | 1.5–2.5× | Multiple head weights | Chat, instruction following |

---

## 9. Quantization

vLLM supports multiple quantization formats, affecting memory, throughput, and quality.

### Supported Formats

| Format | Bits | Hardware | Method | Quality loss |
|---|---|---|---|---|
| FP16 / BF16 | 16 | A100, H100 | None (baseline) | None |
| FP8 | 8 | H100, H200 | Per-tensor or per-channel | Minimal (<0.5%) |
| INT8 (SmoothQuant) | 8 | A100, H100 | Smooth activation outliers | Very small |
| GPTQ | 4 | All | Post-training, weight-only | Small–moderate |
| AWQ | 4 | All | Activation-aware weight quantization | Small |
| GGUF (via llama.cpp) | 2–8 | CPU + GPU | Mixed-precision | Varies by bits |
| QuIP# | 2 | H100 | Incoherence processing | Moderate |
| AQLM | 2 | H100 | Additive quantization | Moderate |

### Using Quantization

```bash
# FP8 (recommended for H100, minimal quality loss)
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-70B-Instruct \
    --dtype float16 \
    --quantization fp8

# GPTQ (load pre-quantized model)
python -m vllm.entrypoints.openai.api_server \
    --model TheBloke/Llama-2-70B-GPTQ \
    --quantization gptq \
    --dtype float16

# AWQ (better quality than GPTQ at same bit-width)
python -m vllm.entrypoints.openai.api_server \
    --model casperhansen/llama-3-70b-instruct-awq \
    --quantization awq

# INT8 (SmoothQuant, good balance)
python -m vllm.entrypoints.openai.api_server \
    --model neuralmagic/Meta-Llama-3-8B-Instruct-quantized.w8a8 \
    --quantization compressed-tensors
```

### KV Cache Quantization

Separate from weight quantization — quantizes the KV cache itself to save memory:

```bash
--kv-cache-dtype fp8_e5m2   # FP8 KV cache (H100 only)
--kv-cache-dtype int8        # INT8 KV cache
```

**Impact**: FP8 KV cache cuts KV memory in half vs FP16. For LLaMA 3 70B at 128K context: reduces KV memory from ~640GB to ~320GB, enabling longer contexts on the same hardware.

### Memory vs Quality Tradeoff

```
FP16     → 100% memory, 100% quality (baseline)
FP8      → 50% memory,  99.5% quality  ← recommended for H100
INT8     → 50% memory,  99%   quality
AWQ INT4 → 25% memory,  97-98% quality
GPTQ INT4→ 25% memory,  96-98% quality
INT2     → 12.5% memory, 90-95% quality (use with caution)
```

---

## 10. Distributed Inference

vLLM supports multi-GPU and multi-node serving for models too large for a single GPU or to increase throughput.

### Tensor Parallelism (TP)

Splits model weights across GPUs along the tensor dimension. Each GPU holds 1/N of each weight matrix; they communicate via AllReduce after each matmul.

```bash
# 4-GPU tensor parallelism (model split across 4 GPUs)
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-70B-Instruct \
    --tensor-parallel-size 4

# Requires 4 GPUs on the same node (NVLink preferred for bandwidth)
```

**When to use**: When the model doesn't fit on one GPU. Communication overhead requires NVLink or fast interconnect (PCIe TP is slow).

**Scaling**: TP=2 → ~1.8× throughput (communication overhead). TP=4 → ~3.2×. TP=8 → ~5-6×.

### Pipeline Parallelism (PP)

Splits model layers across GPUs (each GPU holds consecutive layers). Micro-batches flow through the pipeline.

```bash
# 2-node, 8 GPUs each: TP=8 within node, PP=2 across nodes
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-405B-Instruct \
    --tensor-parallel-size 8 \
    --pipeline-parallel-size 2 \
    --distributed-executor-backend ray
```

**When to use**: Multi-node serving. PP avoids high-bandwidth AllReduce across slow inter-node network; only activations (smaller) cross nodes.

**PP tradeoff**: Pipeline bubbles reduce utilization. PP=2 with micro-batches achieves ~85-90% efficiency.

### Expert Parallelism (EP) for MoE

For Mixture-of-Experts models (Mixtral, DeepSeek-V3), different experts run on different GPUs:

```bash
# Mixtral 8x7B: 8 experts distributed across 4 GPUs
python -m vllm.entrypoints.openai.api_server \
    --model mistralai/Mixtral-8x7B-Instruct-v0.1 \
    --tensor-parallel-size 4
# vLLM automatically applies EP for MoE layers
```

### Multi-Node with Ray

```bash
# Node 0 (head)
ray start --head --port=6379

# Node 1 (worker)
ray start --address='node0_ip:6379'

# Launch on head node
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-405B-Instruct \
    --tensor-parallel-size 8 \
    --pipeline-parallel-size 2 \
    --distributed-executor-backend ray
```

### Parallelism Strategy Guide

| Model size | Hardware | Strategy |
|---|---|---|
| ≤8B | 1× A100 80GB | TP=1 (single GPU) |
| 70B FP16 | 2× A100 80GB | TP=2 |
| 70B FP8 | 1× H100 80GB | TP=1 |
| 405B | 8× H100 80GB | TP=8 |
| 405B | 2 nodes × 8× H100 | TP=8, PP=2 |
| Mixtral 8x22B | 4× A100 80GB | TP=4 (EP auto) |

---

## 11. LoRA and Adapter Serving

vLLM supports serving multiple LoRA adapters on a single base model — crucial for multi-tenant deployments where different users need fine-tuned behavior.

### How It Works

The base model weights stay loaded in GPU. LoRA weights (A and B matrices) are loaded per-adapter and applied during the forward pass:

```
output = base_weight(x) + alpha/r * B(A(x))
```

Multiple adapters can be hot-swapped or served simultaneously per request.

### Configuration

```bash
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-8B-Instruct \
    --enable-lora \
    --max-lora-rank 64 \
    --max-loras 4 \           # max simultaneous LoRA adapters in memory
    --max-cpu-loras 16 \      # LoRAs cached on CPU (paged in/out as needed)
    --lora-modules \
        customer-support=/path/to/customer-lora \
        code-gen=/path/to/code-lora \
        legal=/path/to/legal-lora
```

### Per-Request Adapter Selection

```python
from openai import OpenAI

client = OpenAI(base_url="http://localhost:8000/v1", api_key="none")

# Use the customer support LoRA
response = client.chat.completions.create(
    model="customer-support",   # LoRA module name
    messages=[{"role": "user", "content": "Help me with my order"}]
)

# Use base model
response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Hello"}]
)
```

### LoRA Memory Management

- LoRA weights are small (rank 16 → ~8MB per adapter for 8B model)
- vLLM pages adapters between GPU and CPU as requests arrive
- `--max-loras` limits simultaneous GPU-resident adapters
- `--max-cpu-loras` limits CPU-cached adapters (LRU eviction after that)

---

## 12. Structured Output

vLLM can constrain generation to follow a JSON schema, regex, grammar, or choice list. Uses the **outlines** library internally for guided decoding.

### JSON Schema

```python
from openai import OpenAI
import json

client = OpenAI(base_url="http://localhost:8000/v1", api_key="none")

schema = {
    "type": "object",
    "properties": {
        "name": {"type": "string"},
        "age": {"type": "integer"},
        "email": {"type": "string", "format": "email"}
    },
    "required": ["name", "age"]
}

response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Extract user info: John Doe, 30, john@example.com"}],
    extra_body={"guided_json": json.dumps(schema)}
)
```

### Regex

```python
response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Generate a US phone number"}],
    extra_body={"guided_regex": r"\(\d{3}\) \d{3}-\d{4}"}
)
```

### Choice

```python
response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Is this review positive or negative?"}],
    extra_body={"guided_choice": ["positive", "negative", "neutral"]}
)
```

### Grammar (EBNF/GBNF)

```python
grammar = """
root ::= object
object ::= "{" pair ("," pair)* "}"
pair ::= string ":" value
value ::= string | number | "true" | "false" | "null"
string ::= '"' [^"]* '"'
number ::= [0-9]+
"""

response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Generate a JSON object"}],
    extra_body={"guided_grammar": grammar}
)
```

### How Guided Decoding Works

At each decoding step, outlines computes a **token mask** — a bitmask over the vocabulary where `1` means the token is valid given the current schema/grammar state. vLLM applies this mask to logits before sampling, forcing the model to only sample valid tokens:

```
logits[invalid_token_ids] = -inf   # force probability to 0
sampled_token = sample(softmax(logits))
```

**Performance note**: Building the FSM (finite state machine) for complex schemas adds overhead on the first request. vLLM caches compiled FSMs — subsequent requests with the same schema pay no compilation cost.

---

## 13. Multimodal Support

vLLM supports vision-language models (VLMs) via a unified multimodal input interface.

### Supported Models

- LLaMA 3.2 Vision (11B, 90B)
- Qwen2-VL (7B, 72B)
- InternVL2
- Phi-3-Vision
- LLaVA-1.5 / LLaVA-NeXT
- Pixtral (Mistral's vision model)
- Molmo
- Gemma3 (multimodal)

### Serving Vision Models

```bash
python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Llama-3.2-11B-Vision-Instruct \
    --max-model-len 8192
```

### Image Input

```python
import base64

# URL input
response = client.chat.completions.create(
    model="meta-llama/Llama-3.2-11B-Vision-Instruct",
    messages=[{
        "role": "user",
        "content": [
            {"type": "image_url", "image_url": {"url": "https://example.com/image.jpg"}},
            {"type": "text", "text": "What is in this image?"}
        ]
    }]
)

# Base64 input
with open("image.jpg", "rb") as f:
    image_data = base64.b64encode(f.read()).decode()

response = client.chat.completions.create(
    model="meta-llama/Llama-3.2-11B-Vision-Instruct",
    messages=[{
        "role": "user",
        "content": [
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_data}"}},
            {"type": "text", "text": "Describe the image"}
        ]
    }]
)
```

### Multi-Image Input

```python
response = client.chat.completions.create(
    model="Qwen/Qwen2-VL-7B-Instruct",
    messages=[{
        "role": "user",
        "content": [
            {"type": "image_url", "image_url": {"url": "https://example.com/img1.jpg"}},
            {"type": "image_url", "image_url": {"url": "https://example.com/img2.jpg"}},
            {"type": "text", "text": "Compare these two images"}
        ]
    }]
)
```

### Image Preprocessing

vLLM uses the model's built-in image processor (from the Hugging Face config). Images are:
1. Loaded and decoded (PIL)
2. Resized and normalized per model spec
3. Encoded to visual tokens (vision encoder forward pass)
4. Concatenated with text token embeddings

```bash
# Limit image resolution to control KV cache size
--max-num-seqs 16 \
--image-input-type pixel_values \
--image-token-id 128256 \  # model-specific image token ID
```

---

## 14. OpenAI-Compatible API

vLLM exposes a fully OpenAI-compatible REST API — any client using the OpenAI SDK can point at vLLM with only a `base_url` change.

### Endpoints

| Endpoint | Description |
|---|---|
| `GET /v1/models` | List available models and LoRA adapters |
| `POST /v1/completions` | Text completion (legacy) |
| `POST /v1/chat/completions` | Chat completion (primary) |
| `POST /v1/embeddings` | Text embeddings (embedding models only) |
| `GET /health` | Health check |
| `GET /metrics` | Prometheus metrics |
| `GET /v1/tokenize` | Tokenize text (vLLM extension) |
| `POST /v1/pooling` | Pooling for embedding models |

### Chat Completions

```python
from openai import OpenAI

client = OpenAI(base_url="http://localhost:8000/v1", api_key="token-abc")

# Basic completion
response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[
        {"role": "system", "content": "You are a helpful assistant."},
        {"role": "user", "content": "What is the capital of France?"}
    ],
    temperature=0.7,
    max_tokens=256,
    top_p=0.9,
    frequency_penalty=0.1,
    presence_penalty=0.0,
    stop=["<|eot_id|>", "\n\n"]
)

print(response.choices[0].message.content)

# Streaming
stream = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Tell me a story"}],
    stream=True
)

for chunk in stream:
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="", flush=True)
```

### vLLM-Specific Extensions

```python
# Beam search
response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Translate to French: Hello"}],
    extra_body={
        "use_beam_search": True,
        "best_of": 4,
        "early_stopping": True,
        "length_penalty": 1.0,
    }
)

# Skip special tokens
response = client.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    prompt="The quick brown fox",
    extra_body={"skip_special_tokens": False}
)

# Logprobs
response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Hello"}],
    logprobs=True,
    top_logprobs=5
)
for token_logprob in response.choices[0].logprobs.content:
    print(f"{token_logprob.token}: {token_logprob.logprob:.3f}")

# Min-p sampling (vLLM extension)
response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Write a poem"}],
    extra_body={"min_p": 0.05}   # filter tokens with p < 5% of max token prob
)
```

### Tool Calling / Function Calling

```python
tools = [{
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "Get current weather for a city",
        "parameters": {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "City name"},
                "unit": {"type": "string", "enum": ["celsius", "fahrenheit"]}
            },
            "required": ["city"]
        }
    }
}]

response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "What's the weather in Paris?"}],
    tools=tools,
    tool_choice="auto"
)

if response.choices[0].message.tool_calls:
    tool_call = response.choices[0].message.tool_calls[0]
    print(f"Tool: {tool_call.function.name}")
    print(f"Args: {tool_call.function.arguments}")
```

### Python API (Offline)

```python
from vllm import LLM, SamplingParams

llm = LLM(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    tensor_parallel_size=2,
    gpu_memory_utilization=0.9,
    max_model_len=8192,
    enable_prefix_caching=True,
    quantization="fp8"
)

sampling_params = SamplingParams(
    temperature=0.8,
    top_p=0.95,
    max_tokens=512,
    stop=["<|eot_id|>"]
)

outputs = llm.generate(
    ["What is machine learning?", "Explain quantum computing"],
    sampling_params
)

for output in outputs:
    print(output.outputs[0].text)
```

---

## 15. Metrics and Monitoring

vLLM exposes rich Prometheus metrics at `/metrics`.

### Key Metrics

```
# Throughput
vllm:prompt_tokens_total          # total prompt tokens processed
vllm:generation_tokens_total      # total tokens generated
vllm:request_success_total        # completed requests

# Latency
vllm:time_to_first_token_seconds  # TTFT histogram (p50, p95, p99)
vllm:time_per_output_token_seconds # TPOT histogram (inter-token latency)
vllm:request_latency_seconds       # end-to-end request latency

# Queue / Scheduling
vllm:num_requests_waiting          # requests in WAITING queue
vllm:num_requests_running          # requests in RUNNING state
vllm:num_requests_swapped          # requests swapped to CPU

# KV Cache
vllm:gpu_cache_usage_perc          # % of KV cache blocks used
vllm:cpu_cache_usage_perc          # % of CPU swap cache used
vllm:gpu_prefix_cache_hit_rate     # APC cache hit rate (0-1)

# GPU
vllm:num_preemptions_total         # scheduler preemption count
```

### Grafana Dashboard

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'vllm'
    static_configs:
      - targets: ['vllm-server:8000']
    metrics_path: '/metrics'
    scrape_interval: 5s
```

### Key SLO Targets (Production Guidance)

| Metric | Target |
|---|---|
| P50 TTFT | < 500ms |
| P99 TTFT | < 2s |
| P50 TPOT | < 50ms |
| GPU cache usage | 70–90% (higher = better utilization) |
| Requests waiting | < 10 (queue depth spike signals capacity issue) |
| Prefix cache hit rate | > 50% (for shared-system-prompt workloads) |

---

## 16. Production Deployment

### Docker

```bash
# Official vLLM image
docker run --runtime nvidia --gpus all \
    -p 8000:8000 \
    -v ~/.cache/huggingface:/root/.cache/huggingface \
    vllm/vllm-openai:latest \
    --model meta-llama/Meta-Llama-3-8B-Instruct \
    --tensor-parallel-size 2 \
    --gpu-memory-utilization 0.9 \
    --enable-prefix-caching \
    --max-model-len 8192
```

### Docker Compose

```yaml
version: '3.8'
services:
  vllm:
    image: vllm/vllm-openai:latest
    runtime: nvidia
    environment:
      - HUGGING_FACE_HUB_TOKEN=${HF_TOKEN}
      - CUDA_VISIBLE_DEVICES=0,1
    ports:
      - "8000:8000"
    volumes:
      - model-cache:/root/.cache/huggingface
    command: >
      --model meta-llama/Meta-Llama-3-70B-Instruct
      --tensor-parallel-size 2
      --gpu-memory-utilization 0.90
      --enable-prefix-caching
      --enable-chunked-prefill
      --max-num-seqs 256
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 2
              capabilities: [gpu]

volumes:
  model-cache:
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vllm-server
spec:
  replicas: 2
  selector:
    matchLabels:
      app: vllm
  template:
    metadata:
      labels:
        app: vllm
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8000"
        prometheus.io/path: "/metrics"
    spec:
      containers:
      - name: vllm
        image: vllm/vllm-openai:latest
        args:
        - "--model"
        - "meta-llama/Meta-Llama-3-8B-Instruct"
        - "--tensor-parallel-size"
        - "1"
        - "--gpu-memory-utilization"
        - "0.9"
        - "--enable-prefix-caching"
        - "--port"
        - "8000"
        ports:
        - containerPort: 8000
        env:
        - name: HUGGING_FACE_HUB_TOKEN
          valueFrom:
            secretKeyRef:
              name: hf-token
              key: token
        resources:
          limits:
            nvidia.com/gpu: "1"
            memory: "64Gi"
          requests:
            nvidia.com/gpu: "1"
            memory: "48Gi"
        livenessProbe:
          httpGet:
            path: /health
            port: 8000
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /health
            port: 8000
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: vllm-service
spec:
  selector:
    app: vllm
  ports:
  - port: 80
    targetPort: 8000
  type: ClusterIP
```

### Load Balancing Multiple vLLM Instances

For horizontal scaling, run multiple vLLM instances and load balance:

```nginx
# nginx.conf
upstream vllm_backends {
    least_conn;
    server vllm-0:8000;
    server vllm-1:8000;
    server vllm-2:8000;
    keepalive 32;
}

server {
    location /v1/ {
        proxy_pass http://vllm_backends;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_read_timeout 300s;   # long for streaming
    }
}
```

**Note**: For prefix caching to be effective with load balancing, route requests with the same system prompt to the same backend (sticky session by system-prompt hash). Otherwise cache hit rates will be low.

---

## 17. Key Startup Flags

### Essential Flags

```bash
python -m vllm.entrypoints.openai.api_server \

# Model
  --model meta-llama/Meta-Llama-3-8B-Instruct  # HF model ID or local path
  --tokenizer /path/to/tokenizer    # if different from model
  --revision main                   # git revision / branch
  --dtype bfloat16                  # bfloat16 | float16 | float32 | auto

# Memory & Context
  --gpu-memory-utilization 0.9      # fraction of GPU memory for model + KV cache
  --max-model-len 8192              # max context length (prompt + output)
  --max-num-seqs 256                # max concurrent sequences
  --max-num-batched-tokens 32768    # max tokens per scheduler step

# Parallelism
  --tensor-parallel-size 2         # GPUs for tensor parallelism
  --pipeline-parallel-size 1       # nodes for pipeline parallelism
  --distributed-executor-backend ray  # ray | mp (default: mp for single-node)

# Performance Features
  --enable-prefix-caching          # Automatic Prefix Caching (APC)
  --enable-chunked-prefill         # interleave prefill and decode
  --block-size 16                  # KV cache block size in tokens

# Quantization
  --quantization fp8               # fp8 | gptq | awq | squeezellm | none
  --kv-cache-dtype fp8_e5m2        # FP8 KV cache (H100 only)

# Speculative Decoding
  --speculative-model [ngram]      # draft model path or [ngram]
  --num-speculative-tokens 5       # draft tokens per step

# LoRA
  --enable-lora
  --max-loras 4
  --lora-modules name=/path/to/adapter

# Serving
  --port 8000
  --host 0.0.0.0
  --api-key secret-token           # optional auth
  --max-log-len 100                # truncate logged prompts

# Optimization
  --compilation-config 3           # torch.compile optimization level (0-3)
  --enforce-eager                  # disable CUDA graph (debug only)
  --disable-log-requests           # reduce logging overhead in production
```

### Flag Tuning Guide

| Goal | Key flags |
|---|---|
| Max throughput | `--max-num-seqs 512`, `--gpu-memory-utilization 0.95`, `--enable-prefix-caching` |
| Min TTFT | `--enable-chunked-prefill`, `--max-num-batched-tokens 512` |
| Long context | `--max-model-len 131072`, `--enable-prefix-caching`, `--kv-cache-dtype fp8_e5m2` |
| Multi-tenant LoRA | `--enable-lora`, `--max-loras 8`, `--max-cpu-loras 32` |
| Cost efficiency | `--quantization awq`, `--tensor-parallel-size 1` |
| Debug mode | `--enforce-eager`, `--max-num-seqs 4` |

---

## 18. vLLM v0 vs v1 Architecture

vLLM v1 (released late 2024, default from v0.8+) is a ground-up rewrite of the execution engine.

### Key Differences

| Aspect | v0 | v1 |
|---|---|---|
| Scheduler | Python, single-threaded | C++, zero-copy |
| KV cache | Block-based (PagedAttention) | Block-based + prefix caching by default |
| Prefill/decode | Separate forward passes | Unified with chunked prefill |
| CUDA graphs | Per-batch-size | Flexible capture |
| Multimodal | Limited | First-class, multi-image |
| Structured output | outlines integration | Faster FSM caching |
| CPU overhead | Higher (Python GIL) | Lower (async tokenizer, C++ paths) |
| TP communication | NCCL | NCCL + optimized collectives |

### Enabling v1

```bash
# v1 is default from vLLM 0.8+
# For earlier versions:
VLLM_USE_V1=1 python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-8B-Instruct
```

### v1 Performance Improvements (vs v0)

- 1.5–2× higher throughput on same hardware
- 40% lower CPU overhead per token
- Better chunked prefill integration
- Async tokenization off critical path
- Prefix caching enabled by default

---

## 19. Performance Numbers

### Throughput (tokens/second, single A100 80GB)

| Model | Method | Throughput |
|---|---|---|
| LLaMA 3 8B | HF Transformers (baseline) | ~80 tok/s |
| LLaMA 3 8B | vLLM FP16, batch=1 | ~250 tok/s |
| LLaMA 3 8B | vLLM FP16, continuous batch | ~1,200 tok/s |
| LLaMA 3 8B | vLLM FP8 + APC | ~1,800 tok/s |
| LLaMA 3 8B | vLLM FP8 + speculative (ngram) | ~2,400 tok/s |
| LLaMA 3 70B | vLLM FP16, TP=2 | ~350 tok/s |
| LLaMA 3 70B | vLLM FP8, TP=1 (H100) | ~800 tok/s |

### TTFT vs Context Length (LLaMA 3 8B, A100)

| Context length | TTFT (no APC) | TTFT (APC hit) |
|---|---|---|
| 1K tokens | ~50ms | ~5ms |
| 8K tokens | ~350ms | ~10ms |
| 32K tokens | ~1.4s | ~15ms |
| 128K tokens | ~5.5s | ~20ms |

### Engine Comparison (LLaMA 3 8B, A100 80GB, throughput-mode)

| Engine | Throughput | TTFT | Notes |
|---|---|---|---|
| HF Transformers | 80 tok/s | 50ms | Baseline |
| vLLM | 1,200 tok/s | 50ms | Best all-round |
| TensorRT-LLM | 1,500 tok/s | 40ms | NVIDIA-only, more setup |
| SGLang | 1,100 tok/s | 45ms | Better for structured outputs |
| llama.cpp | 50 tok/s | 60ms | CPU+consumer GPU |
| Ollama | 40 tok/s | 70ms | Ease of use |

---

## 20. Interview Questions

**Q1: What is PagedAttention and why was it necessary?**

Before PagedAttention, KV cache was allocated as one contiguous block per request sized for max_sequence_length. This caused severe internal fragmentation (allocated but unused memory) and external fragmentation (no contiguous block large enough for new requests). PagedAttention divides the KV cache into fixed-size pages (blocks), allocated on demand and non-contiguous. A block table maps logical positions to physical pages. This eliminates fragmentation and enables sharing of identical prefix pages across requests.

**Q2: How does continuous batching differ from static batching?**

Static batching waits for all requests in a batch to complete before starting a new batch — GPU idles waiting for the slowest request. Continuous batching inserts new requests into the batch the moment a slot opens (after any request completes). This keeps GPU utilization near 100% and dramatically increases throughput, especially for workloads with variable output lengths.

**Q3: What is chunked prefill and when should you enable it?**

Chunked prefill breaks long prompt processing into small chunks interleaved with decode steps. Without it, a 10K-token prefill blocks all decode requests for hundreds of milliseconds (high TTFT for existing users). With it, the 10K prefill is spread across 20 steps of 500 tokens each, interleaved with decode — existing users see much lower latency. Enable it for mixed workloads with both short and long prompts.

**Q4: How does automatic prefix caching work and when does it help?**

APC maintains a radix tree (hash trie) keyed by token block hashes. When a new request arrives, vLLM checks if its prefix blocks are already in the tree. If so, it reuses the cached KV pages — skipping recomputation. It helps significantly when many requests share the same system prompt, few-shot examples, or RAG context. It doesn't help for fully unique prompts.

**Q5: Explain tensor parallelism vs pipeline parallelism in vLLM.**

Tensor parallelism (TP) splits each weight matrix across N GPUs. Each GPU holds 1/N of the weights; they compute their shard and synchronize via AllReduce after each layer. Best for single-node (NVLink bandwidth). Pipeline parallelism (PP) splits layers across nodes — each node holds consecutive layers; activations flow through the pipeline. Best for multi-node (only activations cross the slow inter-node network, not all-reduce). Production large models use TP within a node and PP across nodes.

**Q6: How does speculative decoding achieve speedup without changing output distribution?**

The draft model generates K candidate tokens cheaply. The target model verifies all K in one forward pass (parallel, not sequential). For each token, if the draft's proposal matches the target's distribution (up to a rejection threshold), it's accepted. If rejected at position i, tokens 0..i-1 are accepted and a corrected token at i is sampled from the target. The modified rejection sampling algorithm guarantees the accepted tokens are distributed exactly as if the target model had generated them autoregressively — the distribution is unchanged, only the latency is reduced.

**Q7: What is the tradeoff between gpu_memory_utilization and throughput?**

Higher `gpu_memory_utilization` allocates more GPU RAM for KV cache blocks after model weights are loaded. More blocks = more concurrent sequences = higher throughput (up to a point). Too high risks OOM from memory spikes or CUDA context overhead. Too low leaves throughput on the table. Rule of thumb: 0.85-0.90 for stable production; 0.95 for maximum throughput with monitoring in place.

**Q8: When would you use LoRA serving vs separate model deployments?**

LoRA serving is better when: (1) you have many fine-tuned variants of the same base model, (2) adapters are small and can be paged in/out, (3) you want to avoid duplicating large base model weights. Separate deployments are better when: (1) adapters need different system-level configs (different quantization, TP degree), (2) one variant has dramatically different traffic patterns, (3) isolation for billing or SLA is required.
