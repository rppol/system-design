# Inference & Decoding

## 1. Concept Overview

LLM inference is the process of generating tokens from a trained model. Unlike training (which is parallelizable across the sequence), inference is inherently sequential — each token can only be generated after the previous one. This fundamental constraint drives most of the complexity in LLM serving systems.

Understanding inference mechanics is critical for system design: the KV cache, batching strategies, speculative decoding, and quantization are all responses to the unique computational characteristics of autoregressive generation. Getting inference right is the difference between a model that costs $10/hour to serve and one that costs $1,000/hour.

---

## Intuition

> **One-line analogy**: LLM inference is like a chef cooking one dish at a time — each plate (token) must finish before the next starts, so you optimize by keeping the kitchen (GPU) constantly busy with multiple orders.

**Mental model**: Training is parallel — process the whole sequence at once. Inference is sequential — generate one token, feed it back, generate the next. Each token requires a full model forward pass (loading 140GB of weights). The bottleneck isn't the math; it's loading weights from GPU memory. Solutions: batch many users together (amortize the weight load), cache intermediate results (KV cache), use small draft models to speculatively generate multiple tokens (speculative decoding), and manage memory efficiently (PagedAttention).

**Why it matters**: Inference is where 90%+ of LLM compute cost occurs post-training. Getting inference right means serving 10× more users at 10× less cost. The gap between naive serving (50 tok/s, $25/hr) and optimized serving (600 tok/s, $2/hr) is entirely engineering.

**Key insight**: The fundamental constraint is that decoding is memory-bandwidth bound, not compute bound — GPUs are 150× better at compute than memory bandwidth for LLM decode. Every optimization targets reducing or amortizing memory access.

---

## 2. Core Principles

- **Sequential bottleneck**: Each output token depends on all previous tokens — inference can't be parallelized across tokens.
- **Two phases**: Prefill (process input tokens, one forward pass) and decode (generate output tokens one at a time).
- **Memory bandwidth bound**: For most models, GPU memory bandwidth is the bottleneck during decoding, not compute.
- **Batching is key**: Sharing computation across multiple requests dramatically improves throughput.
- **KV cache**: The key-value tensors computed during prefill can be cached to avoid recomputation during decoding.

---

## 3. Concepts

### 3.1 Autoregressive Generation

```
Prefill phase: Process all input tokens in parallel
  Input: "The capital of France is"
  → One forward pass through transformer
  → Produces KV cache for each layer
  → Generates first output token

Decode phase: Generate one token at a time
  Token 1: "Paris"  → KV cache updated
  Token 2: "."      → KV cache updated
  Token 3: EOS      → stop

Latency decomposition:
  TTFT (Time To First Token) = prefill time (proportional to input length)
  TPOT (Time Per Output Token) = decode time per token
  Total latency = TTFT + TPOT × output_length
```

### 3.2 Sampling Strategies

**Greedy decoding**: Always pick highest probability token
```python
next_token = argmax(logits)
# Deterministic, fast, but repetitive
```

**Temperature sampling**: Scale logits before softmax
```python
logits_scaled = logits / temperature
probs = softmax(logits_scaled)
next_token = sample(probs)
# temperature=0.7: creative but coherent
# temperature=1.5: very creative, sometimes incoherent
```

**Top-k sampling**: Only sample from top-k tokens
```python
top_k_probs = top_k(probs, k=50)
next_token = sample(top_k_probs)
# Prevents very low-probability "garbage" tokens
```

**Top-p (nucleus) sampling**: Sample from smallest set of tokens whose cumulative probability ≥ p
```python
sorted_probs = sort(probs, descending=True)
cumsum = cumulative_sum(sorted_probs)
nucleus = sorted_probs[cumsum <= p]  # p=0.9 typical
next_token = sample(nucleus)
# Adaptive: large nucleus for uncertain positions, small for confident
```

**Min-p sampling** (newer): Only keep tokens with probability ≥ min_p × max_token_prob
```python
# min_p = 0.05 means: keep tokens with prob ≥ 5% of the top token's probability
threshold = min_p * max(probs)
valid_tokens = probs[probs >= threshold]
```

### 3.3 KV Cache — Internals & Memory

The most important optimization for efficient inference:

```
Without KV cache (naive):
  At each decode step, recompute attention over ALL tokens (input + generated so far)
  Step 1: compute KV for [input + token1] → O(n²) attention
  Step 2: compute KV for [input + token1 + token2] → O(n²+1) attention
  → Redundant computation grows quadratically

With KV cache:
  During prefill: compute and SAVE K, V tensors for all input tokens
  During decode: ONLY compute K, V for new token; attention over cached K, V
```

**Per-layer storage details:**
The KV cache is stored **per transformer layer**. Each layer has its own K and V tensors:

```
KV cache memory formula:
  2 (K + V)
  × num_layers
  × num_kv_heads          (GQA: fewer heads than query heads)
  × head_dim
  × max_seq_len
  × bytes_per_element     (2 bytes for BF16, 1 for INT8)

LLaMA 3 70B worked example:
  layers     = 80
  kv_heads   = 8      (GQA: 8 KV heads shared across 64 query heads)
  head_dim   = 128
  BF16       = 2 bytes

  Per token: 2 × 80 × 8 × 128 × 2 = 327,680 bytes ≈ 320 KB/token

  At 8K context, 1 request:   320 KB × 8192  = 2.56 GB
  At 32K context, 1 request:  320 KB × 32768 = 10.2 GB
  At 128K context, 1 request: 320 KB × 131072 = 40.9 GB
  At 128K context, 10 users:  409 GB ← requires ~6× H100 just for KV cache
```

This is why KV cache is the **primary memory bottleneck** in production LLM serving, not model weights.

### 3.7 Q/K/V Roles During Inference

Understanding why the KV cache exists requires understanding what happens to Q, K, V during decode:

```
Prefill phase (processing input tokens):
  ALL tokens in input are processed SIMULTANEOUSLY
  → Q, K, V computed for ALL positions in one forward pass
  → K and V are SAVED to the KV cache (layer by layer)
  → Q is used immediately for attention, then discarded

Decode phase (generating output, one token at a time):
  New token is added at position n
  → Only the NEW token's Q is computed
  → Only the NEW token's K, V are computed and appended to cache
  → Attention: Q[new] × (K[0:n+1] from cache) → weights × V[0:n+1]
  → Output: new token prediction

Why only Q is "live":
  The new token asks: "Given everything that came before (K_cache),
  what should I attend to?" The K and V of previous tokens never change —
  that's why they can be cached. Only the new token's Q changes each step.
```

This asymmetry is fundamental: K and V can be cached because past context doesn't change; Q cannot because each new token asks a different "question."

### 3.8 Prompt Caching (Anthropic-Style)

For applications with repeated system prompts or shared prefixes across requests, recomputing KV for the shared prefix on every request wastes compute:

```
Without prompt caching:
  Request 1: [system_prompt (2000 tokens)] + [user_query_1]
    → compute KV for all 2000 + user tokens
  Request 2: [system_prompt (2000 tokens)] + [user_query_2]
    → recompute KV for all 2000 tokens AGAIN
  → 2000 tokens × TTFT × N_requests wasted

With prompt caching (Anthropic Claude):
  Request 1: KV for system_prompt computed and STORED server-side
  Request 2+: system_prompt KV loaded from server cache
    → only compute KV for new user query tokens
```

**How it works in the API:**
- Mark cache breakpoints in your prompt with `cache_control: {type: "ephemeral"}`
- Cache persists for ~5 minutes (Anthropic) or up to 1 hour with refreshes
- Cached tokens billed at 10% of base cost; cache reads at ~10% latency

**Practical savings:**
```
System prompt: 2,000 tokens, billed at $3/1M tokens
Per-request cost without caching: 2,000 × $3/1M = $0.006
Per-request cost with caching: 2,000 × $0.30/1M = $0.0006 (cache write: 1.25×, cache read: 0.1×)
Effective: 90% cost reduction on shared prefix for high-traffic applications
```

**Best for**: AI assistants with large system prompts, applications with shared document context, multi-turn conversations with long histories.

### 3.9 Prefix Caching (SGLang RadixAttention)

SGLang (Stanford) takes prefix caching further with **RadixAttention** — automatic, fine-grained prefix reuse without explicit API breakpoints:

```
Radix tree structure (indexed by token sequences):
  Root
    └── "You are a helpful assistant. " (tokens 0-8)
          ├── "Translate to French: " → [request A KV blocks]
          └── "Summarize: " → [request B KV blocks]
                  └── "The paper discusses..." → [request C KV blocks]

Any new request shares KV blocks for all matching prefix tokens.
Match is at block granularity (e.g., 16 tokens per block).
```

**Contrast with Anthropic's approach:**
| | Anthropic Prompt Caching | SGLang RadixAttention |
|---|---|---|
| Breakpoints | Explicit (user marks them) | Automatic |
| Granularity | Coarse (whole prefix) | Fine (per-block) |
| Cache scope | Cross-request, server-side | Within-instance, per-engine |
| Use case | API users, shared prefixes | Inference engines, tree-structured workloads |

RadixAttention is especially effective for **tree-structured programs** (e.g., LLM-generated code that branches: same setup, different function implementations to evaluate).

### 3.10 KV Cache Eviction Strategies

When KV cache fills GPU memory, older or less-important cache entries must be evicted:

**LRU (Least Recently Used):**
```
Evict the KV cache of the request that was used longest ago
Simple, widely used (vLLM default for preemption)
Problem: may evict a long, expensive-to-recompute prefix
```

**Attention-score-based eviction (H2O, SnapKV):**
```
Track cumulative attention scores for each token in the KV cache
Evict tokens that received the least attention (least "important")
Keep: recent tokens + tokens with high historical attention
Result: 20× KV cache compression with <1% quality loss on some tasks
```

**Sliding window + sink tokens (StreamingLLM):**
```
Observation: LLMs always pay high attention to the first few tokens ("attention sinks")
  → these tokens must stay in cache or model degrades

StreamingLLM cache structure:
  [attention sink tokens (first 4)] + [sliding window of last W tokens]

Enables:  infinite-length generation without recomputation
Tradeoff: loses mid-context information; only safe for streaming generation
          where mid-context is less important than recent + initial tokens
```

**PagedAttention eviction (vLLM):**
```
Physical KV blocks swapped to CPU when GPU memory full
Request preempted, blocks saved to CPU RAM (slower)
Request resumed by loading blocks back to GPU
Priority: preempt requests with most remaining to generate (LRU policy)
```

### 3.4 Continuous Batching (PagedAttention)

**Problem with naive batching:**
```
Request A: input=100 tokens, output=50 tokens  → done at step 50
Request B: input=200 tokens, output=500 tokens → done at step 500

Naive batch: A and B together from step 1 to step 500
  A is done at step 50 but GPU keeps allocating memory for it until B finishes
  New Request C can't start until the batch finishes
```

**Continuous batching (used by vLLM):**
```
Step 1-50:  Process [A, B] together
Step 50:    A finishes → immediately add Request C to the batch
Step 51+:   Process [B, C] together
No wasted compute; new requests fill slots as old ones complete
```

**PagedAttention** (vLLM innovation):
```
KV cache is managed like OS virtual memory
Physical GPU memory divided into "blocks" (pages)
  Each block: 16 contiguous tokens × KV tensors for all layers
  Block size tunable (default: 16 tokens)

Logical blocks assigned on demand as sequence grows
Non-contiguous physical memory → contiguous logical view
Benefits:
  - Near-zero KV cache fragmentation
  - Share KV cache across parallel sampling (same prefix → same blocks)
  - Enable preemption: swap out KV cache to CPU when GPU full
```

### 3.5 Speculative Decoding

Use a small draft model to speculatively generate multiple tokens; verify with large model in parallel:

```
Large model: LLaMA 3 70B (target)
Small model: LLaMA 3 8B (draft)

Step 1: Draft model generates K tokens speculatively (fast)
  Draft: ["Paris", "is", "the", "capital", "city"]

Step 2: Large model verifies all K tokens in ONE forward pass
  (Can verify K tokens in parallel because it's a prefill pass)
  Target: ["Paris", "is", "the", "capital"] ✓ ✓ ✓ ✓, "city" ✗ → "of"

Step 3: Accept all matching prefix tokens; reject first mismatch
  Accepted: ["Paris", "is", "the", "capital"] = 4 tokens per round
  Generated: "of" (from target)
  Net: 4-5 tokens generated per large model forward pass (vs. 1)

Speedup: 2-3× if draft and target agree often (same distribution)
Best for: long outputs with predictable structure (code, boilerplate)
```

### 3.6 Flash Attention

Reorders attention computation to be memory-bandwidth efficient:

```
Standard attention:
  1. Compute Q×Kᵀ → store [seq × seq] matrix in HBM (slow)
  2. Compute softmax → read/write HBM (slow)
  3. Compute attention × V → read/write HBM (slow)

Flash Attention:
  Divide Q, K, V into tiles that fit in SRAM (fast)
  Compute attention in a single fused kernel, never writing full matrix to HBM
  Use online softmax algorithm to accumulate results across tiles

Memory: O(n) instead of O(n²)
Speed: 2-4× faster for long sequences (memory bandwidth is the bottleneck)
Flash Attention 2 (2023): 2× faster than Flash Attention 1
Flash Attention 3 (2024): further optimizations for H100
```

---

## 4. Architecture Diagrams

### Prefill vs Decode Phases
```
Prefill: "What is the capital of France?"
  |
  +-- All 8 tokens processed in PARALLEL → one forward pass
  |   Q, K, V computed for all 8 tokens simultaneously
  |   KV cache: K[0:8], V[0:8] stored
  |   Output: first generated token = "Paris"

Decode: generating each subsequent token
  Token 1 "Paris":    Q[8] × (K[0:9], V[0:9]) → "is"      [cached K/V reused]
  Token 2 "is":       Q[9] × (K[0:10], V[0:10]) → "the"    [cached K/V reused]
  Token 3 "the":      Q[10] × (K[0:11], V[0:11]) → "capital" [...]
  ...
  (Only one new token's Q computed each step; all previous K,V reused from cache)
```

### Speculative Decoding Timeline
```
Without speculative decoding (large model only):
  |--T1--| |--T2--| |--T3--| |--T4--|   (4 separate forward passes)

With speculative decoding:
  Small model: |---draft 4 tokens fast---|
  Large model: |-------verify all 4 in one pass + correct---------|
  Net: ~4 tokens per large model pass → 3-4× speedup
```

---

## 5. How It Works — Detailed Mechanics

### Memory Bandwidth Bottleneck

```
During decoding, each step requires:
  - Load ALL model weights from HBM: 70B × 2 bytes = 140GB
  - Perform ~140B FLOPs of computation per token
  - A100 80GB HBM bandwidth: 2 TB/s
  - A100 compute: 312 TFLOPS (BF16)

Time to load weights: 140GB / 2TB/s = 70ms
Time to compute: 140B FLOPs / 312 TFLOPS = 0.45ms

→ Memory bandwidth is 150× slower than compute
→ GPU is starved for data, not compute
→ Solution: better batching (amortize weight loading across more requests)
→ Solution: quantization (load less data per weight)
```

### Latency vs Throughput Optimization

```
Optimize for latency (single user):
  - Small batch size (1-4)
  - Large model → single query, fast response
  - Streaming: send tokens as generated

Optimize for throughput (many users):
  - Large batch size (32-256)
  - Continuous batching
  - PagedAttention: maximize KV cache utilization
  - Token/second × GPU is the key metric
```

---

## 6. Real-World Examples

### vLLM (UC Berkeley, 2023)
- PagedAttention + continuous batching
- 24× higher throughput than naive serving
- Used by Anyscale, Scale AI, Together AI, and thousands of self-hostings
- Open source; de facto standard for open-source model serving

### OpenAI API
- Continuous batching across thousands of users
- Speculative decoding for common patterns
- Dynamic model routing (easy → gpt-4o-mini, hard → gpt-4o)
- Custom CUDA kernels for attention and matmul

### Anthropic Claude
- Flash Attention 2/3 for long context (200K)
- Custom inference stack (not public)
- Streaming response delivery; tokens arrive in real-time

---

## 7. Tradeoffs

| Strategy | Latency | Throughput | Memory | Complexity |
|----------|---------|-----------|--------|------------|
| Greedy decode | Fastest | High | Low | None |
| Sampling (top-p) | Fast | High | Low | Low |
| Beam search | Slow | Low | High | Medium |
| Speculative decode | ~Same | 2-3× more | +draft model | High |
| Continuous batching | Medium | Very high | Efficient | Medium |
| PagedAttention | Medium | Highest | Most efficient | High |

---

## 8. When to Use / When NOT to Use

### Use Greedy Decoding (temp=0) When:
- Factual Q&A, code generation, structured outputs
- Reproducibility is important
- Deterministic behavior is expected

### Use Sampling When:
- Creative writing, brainstorming, conversational AI
- Diversity in outputs is valuable
- Single "best" answer doesn't exist

### Use Speculative Decoding When:
- Serving large models (>70B) with throughput constraints
- Output distribution is predictable (code, templates)
- You have a compatible small model available

---

## 9. Common Pitfalls

1. **KV cache OOM**: Underestimating KV cache memory consumption causes OOM in production. Plan for 30-40% of GPU memory for KV cache.
2. **Not streaming**: Buffering full response before sending → perceived latency is very high. Always stream tokens.
3. **Ignoring TTFT**: Optimizing TPOT while ignoring Time-to-First-Token. For chat, TTFT < 1s is critical for perceived responsiveness.
4. **Wrong batch size**: Very small batches (1-2) waste GPU utilization. Very large batches increase latency. Find the sweet spot.
5. **Beam search in production**: Beam search (k=5) uses 5× KV cache memory. Only use for offline batch jobs.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **vLLM** | High-throughput serving | PagedAttention; de facto standard |
| **Flash Attention 2/3** | Efficient attention | Tri Dao; required for long context |
| **SGLang** | Structured generation | Radix attention; constraint generation |
| **TensorRT-LLM** | NVIDIA inference | INT8/FP8; H100-optimized |
| **llama.cpp** | CPU/GPU inference | Quantized; edge; MacOS Metal |
| **ExLlamaV2** | Fast quantized inference | Best speed/quality for consumer GPUs |
| **GGUF** | Model format | llama.cpp compatible; quantized |
| **Safetensors** | Model serialization | Fast, safe loading |

---

## 11. Interview Questions with Answers

**Q: What is the KV cache and why is it important?**
A: The KV cache stores the key and value tensors computed for each token during the prefill phase. During decoding, each new token only needs to compute its own query, then attend to all cached K, V tensors. Without KV cache, you'd recompute K and V for all past tokens at every decoding step — O(n²) work for a length-n response. With KV cache, it's O(n) total. The trade-off: KV cache consumes significant GPU memory (hundreds of GB for large models serving long contexts).

**Q: What is the difference between prefill and decode phases?**
A: Prefill processes all input tokens in parallel in one forward pass — it's compute-bound (similar to training). Decode generates one output token at a time, attending to all previous tokens (cached in KV) — it's memory-bandwidth-bound (loading weights dominates). Prefill latency ≈ proportional to input length; decode latency ≈ proportional to output length × TPOT. For large inputs, prefill dominates total latency (TTFT); for long outputs, decoding dominates.

**Q: How does speculative decoding work and what are its requirements?**
A: A small draft model quickly generates K tokens. The large target model verifies all K tokens in a single forward pass (parallel, like prefill). Tokens matching the draft are accepted; at the first mismatch, the target's token replaces the draft's, and the process restarts. Requirements: (1) draft and target must share the same tokenizer; (2) same distribution (similar models); (3) acceptance rate must be high enough for speedup (>70% is typical target). Best speedup on predictable outputs (code, structured text): 2-3×.

**Q: What is continuous batching and why does it improve throughput?**
A: Naive batching waits for all requests in a batch to complete before accepting new ones. Short requests finish early but hold their GPU slot until the longest request completes. Continuous batching adds new requests to the batch as soon as any request finishes — "iteration-level scheduling." This dramatically improves GPU utilization because the GPU is never waiting for slow requests to finish before starting fast new ones. vLLM's implementation showed up to 24× throughput improvement.

---

## 12. Best Practices

1. **Always stream responses** — don't buffer; latency perception improves dramatically with streaming.
2. **Size your KV cache budget** — 30-40% of total GPU memory is a good rule of thumb.
3. **Monitor TTFT and TPOT separately** — they have different optimization levers.
4. **Use PagedAttention (vLLM)** for any production serving with variable-length outputs.
5. **Profile before optimizing** — use NVIDIA Nsight or vLLM's metrics endpoint to identify the actual bottleneck.
6. **Set max_tokens** — unbounded generation can consume unbounded KV cache memory.

---

## 13. Case Study: Optimizing LLM Serving for a Chat Application

**Problem:** Chat app serves 1000 concurrent users with GPT-4-size model (70B). Naive serving: P99 TTFT = 8 seconds, throughput = 50 tokens/sec/GPU.

**Optimization steps:**

**Step 1: Switch to vLLM + PagedAttention**
- Before: HuggingFace generate() with naive batching
- After: vLLM with continuous batching + PagedAttention
- Result: 4× throughput improvement, P99 TTFT drops to 3 seconds

**Step 2: Quantization (INT4 with GPTQ)**
- Before: BF16 weights = 140GB → requires 2× A100 80GB
- After: INT4 weights = 35GB → fits on 1× A100 80GB
- Result: 2× more concurrent users per GPU, 2× cost reduction

**Step 3: Speculative Decoding**
- Deploy LLaMA 3 8B as draft model alongside 70B
- Acceptance rate: 72% for conversational responses
- Result: 2.3× throughput improvement for decode phase

**Step 4: Request batching with dynamic batching**
- Group requests by similar input length (±20%)
- Minimize padding waste
- Result: 15% additional throughput

**Final result:**
- P99 TTFT: 1.2 seconds (from 8 seconds, 6.7× improvement)
- Throughput: 620 tokens/sec/GPU (from 50, 12.4× improvement)
- Cost per 1K tokens: $0.002 (from $0.025, 12.5× reduction)
