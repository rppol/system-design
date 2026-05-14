# Context Windows & Long Context

## 1. Concept Overview

The context window is the maximum number of tokens an LLM can process in a single forward pass — including both input and output. Early LLMs had 2K-4K context; modern models support 128K (LLaMA 3), 200K (Claude), up to 1M (Gemini 1.5 Pro). This expansion has fundamentally changed what LLM systems can do: process entire codebases, hour-long conversations, entire books, or multiple lengthy documents simultaneously.

Long context creates both opportunities (simpler architecture — just put everything in context) and challenges (quadratic attention cost, "lost in the middle" phenomenon, higher token costs, and the ongoing debate: when should you use long context vs. RAG?).

---

## Intuition

> **One-line analogy**: The context window is like working memory — the more you can hold in mind at once, the more sophisticated the reasoning you can do, but filling it up gets exponentially expensive.

**Mental model**: An LLM can only "see" what's in its context window. 4K tokens (GPT-3) = a few pages; 200K (Claude) = an entire book. Bigger context enables richer reasoning but costs quadratically more compute (O(n²) attention). The "lost in the middle" effect means models attend less to information buried in the middle of long contexts. Long context is simpler to build with (just stuff everything in) but expensive and imperfect; RAG is efficient but retrieval can miss the right context.

**Why it matters**: Long context changes what LLM systems can do — entire codebases, full legal contracts, complete conversation histories become accessible. But the cost structure matters: 200K tokens × $0.003/1K = $0.60 per query. For high-frequency applications, that cost is prohibitive and RAG is necessary.

**Key insight**: Long context and RAG are not competing approaches but complementary tools — use RAG for large corpora and cost sensitivity, use long context for holistic reasoning, and use both together (retrieve relevant chunks → put them in long context) for the best results.

---

## 2. Core Principles

- **Attention is O(n²)**: Standard attention grows quadratically with sequence length — 4× longer sequence = 16× compute. Optimizations (Flash Attention, GQA, ring attention) are critical.
- **Positional encoding determines extrapolation**: Models trained on 4K context don't automatically generalize to 128K. Positional encoding design determines how well models extrapolate.
- **"Lost in the middle"**: Models pay more attention to the beginning and end of long contexts; information in the middle receives less attention. Critical information should be at the extremes.
- **Long context ≠ perfect recall**: Even 1M context models can miss information. Performance degrades with input length.
- **Long context vs. RAG tradeoff**: Putting everything in context is simpler but more expensive; RAG is more efficient but more complex.

---

## 3. Positional Encoding Strategies

### 3.1 Absolute Positional Encoding (APE)

Original transformer approach: add learned or sinusoidal position vectors to token embeddings:

```
Input embedding: token_embedding + position_embedding[position]
position_embedding: shape [max_length × d_model]

Sinusoidal: PE(pos, 2i) = sin(pos / 10000^(2i/d_model))
            PE(pos, 2i+1) = cos(pos / 10000^(2i/d_model))

Problem: model trained on 512 tokens has no embedding for position 513
  → Cannot extrapolate beyond training length at all
Used by: BERT, original GPT
```

### 3.2 Relative Positional Encoding (RPE)

Encode relative distance between positions rather than absolute:

```
Attention weight: A(i,j) ∝ (q_i + r_{i-j})^T k_j

r_{i-j}: learned embedding for the relative distance (i-j)
  r_0: same position
  r_1: adjacent
  r_10: 10 positions apart
  r_{-5}: 5 positions before

Benefits: position patterns generalize better; model sees "how far apart" not "where"
Problem: still needs learned embeddings for all distances seen in training
Used by: T5, DeBERTa
```

### 3.3 RoPE (Rotary Position Embedding)

The dominant approach in modern LLMs. Encodes position as rotation in complex number space:

```
Key idea: Rotate query and key vectors by their positions
  The dot product q_m^T k_n depends only on (m-n) — relative position!

Formula:
  RoPE(x, position) = x ⊗ cos(θ × position) + x̃ ⊗ sin(θ × position)
  where x̃ is x with alternating pairs swapped, θ_i = 10000^(-2i/d)

Properties:
  - Dot product naturally encodes relative positions
  - Extrapolates better than learned positional embeddings
  - Computationally efficient (just multiply)

Long-context extensions:
  YaRN: dynamic scaling; best for extrapolation beyond training length
  LongRoPE: progressive scaling; handles 2M context
  RoPE scaling (simple): compress positions by constant factor to fit longer sequences
    e.g., scale_factor=4 → treat 4K-context model as 16K-context model

Used by: LLaMA (all versions), Mistral, Qwen, DeepSeek, most modern models
```

### 3.4 ALiBi (Attention with Linear Biases)

Instead of positional embeddings, add a linear bias to attention scores:

```
Attention score: q_i^T k_j - m × |i - j|
  m: head-specific slope (steeper = more locality)
  |i - j|: absolute distance

Effect:
  Recent tokens get full attention
  Distant tokens penalized proportionally to distance
  Very distant tokens effectively ignored

Benefits:
  - No position embeddings needed
  - Naturally handles longer sequences than training length
  - Linear bias computation is fast

Limitations:
  - Fixed decay rate may not be optimal for all tasks
  - Long-range dependencies are harder
Used by: MPT, BLOOM (older models)
```

---

## 4. Architecture Diagrams

### Context Window Comparison
```
Model         | Context | Use Case
─────────────────────────────────────────
BERT (2018)   |   512   | Classification, embeddings
GPT-3 (2020)  |  4,096  | General completion
GPT-4 (2023)  | 128,000 | Long documents, complex tasks
Claude 3.5    | 200,000 | Very long documents, codebases
Gemini 1.5Pro |1,000,000| Books, movies, large codebases
```

### "Lost in the Middle" Effect
```
Retrieval accuracy of a fact inserted at different positions
in a 20-document context (Liu et al. 2023):

Position 0 (start):   92%  ████████████████████████
Position 5:           70%  ██████████████████
Position 10 (middle): 54%  ██████████████
Position 15:          69%  █████████████████
Position 19 (end):    90%  ███████████████████████

→ Models strongly favor beginning and end of context
→ For critical information, place at START or END
```

### Long Context Attention Mechanisms
```
Standard Attention:    O(n²) memory and compute

Sparse Attention (GPT-3 Sparse):
  Local window + global tokens: O(n √n)
  Each token attends to: local neighborhood + global "summary" tokens

Sliding Window (Mistral/Longformer):
  Each token attends to ±w/2 surrounding tokens
  O(n × w) instead of O(n²)
  "Dilated" windows at deeper layers for global context

Ring Attention (Sequence Parallelism for Long Context):
  Distribute sequence across devices: each GPU handles seq/N tokens
  Pass KV blocks in a "ring" between devices
  Enables 1M+ token training across many GPUs
```

---

## 5. How It Works — Detailed Mechanics

### Prompt & Prefix Caching for Long Context

Long context makes KV cache the dominant cost — recomputing 100K tokens of shared context on every request is prohibitively expensive. Prompt and prefix caching are the primary mitigation:

**Why long context amplifies the caching benefit:**
```
Short context (2K tokens):
  KV compute cost: 2K tokens × prefill_cost = small
  Caching benefit: modest (~$0.006 savings per cached request)

Long context (200K tokens):
  KV compute cost: 200K tokens × prefill_cost = significant
  Caching benefit: 90%+ cost + latency reduction for shared prefix
  TTFT without caching: 10-30 seconds for 200K prefill
  TTFT with caching: 0.5-2 seconds (only new tokens computed)
```

**Cross-reference → see [Inference & Decoding](../inference_and_decoding/README.md) §3.8 and §3.9 for full details on:**
- Anthropic prompt caching (explicit breakpoints, ~10% cost for cached reads)
- SGLang RadixAttention (automatic tree-indexed prefix reuse)

**Key insight for long-context system design:**
```
KV cache memory at 200K context for 1 user (LLaMA 3 70B):
  320 KB/token × 200K tokens = 64 GB ← entire H100 just for one user's KV cache

Solutions:
  1. Offload KV to CPU RAM between requests (latency cost on resume)
  2. KV quantization (INT8: 32 GB, INT4: 16 GB)
  3. Prefix caching: compute once, cache server-side, amortize across requests
  4. Sliding window + sink tokens: discard mid-context (quality tradeoff)
```

For high-frequency, long-context applications (codebase assistants, document analysis):
the only viable production approach is **prefix caching + KV quantization + GQA**.

### Ring Attention — Sequence Parallelism for Ultra-Long Context

Ring Attention distributes the sequence across devices in a ring topology, enabling training and inference on sequences that are too long for a single device:

**How it works:**

```
Sequence: 1M tokens split across 8 GPUs
  GPU 0: tokens [0, 125K)
  GPU 1: tokens [125K, 250K)
  ...
  GPU 7: tokens [875K, 1M)

Algorithm (per attention layer):
  Each GPU holds its Q, K, V chunks

  Step 1: GPU_i computes attention of its Q chunk against its local K, V
  Step 2: GPU_i sends its K, V to the NEXT GPU in the ring
          (GPU_i+1 mod 8) while receiving K, V from previous GPU
  Step 3: GPU_i computes attention of its Q chunk against the received K, V
          (overlapped with communication via CUDA streams)
  Repeat: until all KV chunks have circulated through the ring (8 steps)
  Final: GPU_i has the complete attention output for its Q chunk

Communication: point-to-point (ring), O(seq/N × d) per step × N steps
Compute: O(seq²/N) per GPU (1/N of full attention per device)
Memory: O(seq/N) per GPU for K, V
```

**Gemini 1.5 Pro (1M context):**
- Uses ring attention (or equivalent sequence parallelism) across TPU pods
- 8 devices handling 128K tokens each → 1M effective context
- Flash Attention within each device; ring topology across devices

**Requirements for ring attention:**
- High-bandwidth interconnect (NVLink ≥ 600 GB/s, or fast InfiniBand)
- Overlap compute and communicate (pipeline the ring steps)
- Flash Attention within each GPU chunk (to maintain O(n) memory per device)

**Practical thresholds:**
- < 128K tokens: single GPU with Flash Attention 2/3 (H100)
- 128K–512K tokens: 2-4 GPU ring attention
- 512K–1M tokens: 8+ GPU ring attention
- > 1M tokens: multi-node ring attention (requires fast interconnect)

### Long Context Fine-Tuning

Training a model to handle longer sequences than its base training:

```
Method 1: Direct training on long documents
  Simply include long documents in continued pre-training
  Simple but compute-expensive
  Risk: catastrophic forgetting of short-context capabilities

Method 2: Position interpolation (Chen et al. 2023)
  Compress positions: treat position p as p × (original_length / new_length)
  A position in the new longer context maps to a fractional position
  in the original space (the model has seen nearby positions)

Method 3: YaRN (Yet another RoPE extensioN)
  Decompose RoPE into different frequency bands
  High-frequency (local) components: no scaling
  Low-frequency (global) components: scale appropriately
  Achieves best quality on long-context benchmarks

Method 4: LongLLaMA, MemGPT
  Augment with external memory
  Not extending the context window but adding external retrieval
```

### When Long Context Helps vs. Fails

```
Long context WORKS WELL for:
  "Find the bug in this 10K-line codebase"
    → Entire codebase in context; model reasons holistically
  "Summarize this 200-page report"
    → Full document in context; no chunking artifacts
  "Based on this entire conversation history, what's the user's real need?"
    → Full context; no retrieval needed

Long context STRUGGLES with:
  "Find specific fact buried in 500-page document"
    → Lost in the middle; RAG with retrieval is more reliable
  "Compare all 50 items in this list"
    → Attention dilution; model may miss some
  Very repetitive content
    → Model "zones out"; attention spreads too thinly
```

### Long Context vs. RAG Decision Framework

```
Use LONG CONTEXT when:
  ✓ Full document understanding required (not just specific facts)
  ✓ Synthesizing across many documents simultaneously
  ✓ Full codebase reasoning (dependencies, patterns, architecture)
  ✓ Latency is important (no retrieval delay)
  ✓ Budget allows ($0.01-0.05/1K tokens × 100K tokens = $1-5/query)
  ✓ Document is small enough: <100K tokens

Use RAG when:
  ✓ Specific fact lookup from large corpus
  ✓ Real-time updates (can't put new documents in context)
  ✓ Cost-sensitive (1M documents → RAG is 1000× cheaper than long context)
  ✓ Privacy (don't want entire document in one prompt)
  ✓ Corpus > 200K tokens (exceeds even the largest context windows)

Use BOTH (hybrid approach):
  ✓ Primary retrieval: find top-K relevant chunks
  ✓ Long context: put all retrieved chunks in full context
  ✓ Best of both worlds: efficiency of RAG + coherence of long context
```

---

## 6. Real-World Examples

### Gemini 1.5 Pro (1M Context)
- Processed entire Apollo 11 transcript (400K tokens) to find specific quotes
- Analyzed entire 402-page document without chunking
- "Needle in a haystack" eval: finds specific fact in 1M random tokens with 98%+ accuracy
- Used for: long video analysis, codebase understanding, multi-document research

### Claude 3.5 (200K Context)
- Processes entire codebases for code review
- Analyzes complete legal contracts (most <100K tokens)
- Maintains coherent conversation over long sessions
- "Project" feature: upload 200K tokens of files → persistent context across sessions

### Cursor IDE (Repository Context)
- LLM with 200K context receives: current file + most relevant files from repo
- Uses embedding-based retrieval to fill remaining context budget
- Hybrid: RAG for initial selection + long context for reasoning

---

## 7. Tradeoffs

| Approach | Quality | Cost | Complexity | Freshness |
|----------|---------|------|-----------|-----------|
| Long context | Best (when it fits) | Expensive | Simple | Static |
| RAG | Very good | Cheap | Complex | Dynamic |
| RAG + Long context | Best | Medium | Medium | Dynamic |
| Short context + chunking | Lower | Cheapest | Medium | Static |

| Context Size | Attention Cost | Memory | Use Case |
|-------------|---------------|--------|---------|
| 4K | 16M ops | 1× | General chat |
| 32K | 1B ops | 8× | Long documents |
| 128K | 16B ops | 32× | Full books, codebases |
| 1M | 1T ops | 250× | Entire document corpora |

---

## 8. When to Use / When NOT to Use

### Use Long Context When:
- Full document coherence required (can't chunk)
- Latency is not critical (longer contexts take longer)
- Budget allows for high per-token costs
- Document fits within the model's context window

### Use RAG Instead When:
- Large, dynamic document corpus (impossible to fit in any context window)
- Need to answer from millions of documents
- Cost is the primary constraint
- Queries target specific facts (not holistic understanding)

---

## 9. Common Pitfalls

1. **Trusting long context for high-stakes fact retrieval**: "Lost in the middle" is real. For exact fact retrieval from long documents, RAG is more reliable.
2. **Forgetting token cost**: 100K token context × $0.03/1K = $3 per query. High-frequency applications need RAG.
3. **Assuming linear quality vs. length**: Quality can degrade non-linearly beyond the model's "effective context window."
4. **Not tuning for long context**: A 4K-trained model with naive position scaling may have 20% accuracy at 100K. Use proper long-context fine-tuning (YaRN, etc.).
5. **Ignoring TTFT at long context**: 200K token prefill takes 10-30 seconds even on H100. Users notice.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Flash Attention 2/3** | Memory-efficient attention | Required for any long context |
| **YaRN** | RoPE long-context extension | Best for extrapolating beyond training |
| **Ring Attention** | Distributed long-context | Distribute sequence across GPUs |
| **LongBench** | Long-context evaluation | Standardized benchmark |
| **RULER** | Long-context needle eval | More rigorous than simple needle test |
| **Gemini 1.5 Pro API** | 1M context | Best long-context managed API |
| **Claude 3.5 API** | 200K context | Best for long documents |
| **Jamba (AI21)** | SSM + Transformer hybrid | Efficient long context |
| **Mamba** | State Space Model | Linear-time; alternative to attention |

---

## 11. Interview Questions with Answers

**Q: What is RoPE and why is it better than absolute positional encoding for long context?**
A: RoPE (Rotary Position Embedding) encodes position by rotating query and key vectors in the complex plane. The key property: the inner product of rotated vectors depends only on their relative position (m-n), not absolute positions m and n. This enables: (1) better generalization to unseen positions (the model understands "5 tokens apart" regardless of absolute position); (2) graceful extrapolation with scaling techniques (YaRN, LongRoPE); (3) efficient computation (element-wise rotation is fast). Absolute positional encoding fails to extrapolate — position 5000 has no learned embedding if the model was trained on 4096 tokens.

**Q: What is the "lost in the middle" problem?**
A: Liu et al. (2023) showed that LLMs pay significantly more attention to information at the beginning and end of their context window, with accuracy dropping sharply for information in the middle of a long context. For a 20-document prompt, recall of a fact at position 10 (middle) was ~54% vs. ~92% at position 0 (start). Mitigation: place critical information at the beginning or end of the prompt, not in the middle of many documents.

**Q: When would you choose long context over RAG?**
A: Long context is preferable when: (1) the task requires holistic document understanding (not just specific fact retrieval) — e.g., "summarize the key decisions in this 200-page report" vs. "find the exact revenue figure"; (2) document is short enough to fit (<100K tokens practically); (3) latency is acceptable; (4) cost budget allows. RAG is better for: large corpora (millions of documents), high-frequency queries (cost matters), specific fact lookup (RAG is more reliable than long-context for exact retrieval), and dynamic content (can update index without changing the model).

**Q: How does Flash Attention enable long-context training?**
A: Standard attention requires materializing the full [seq × seq] attention matrix in HBM, consuming O(n²) memory. For 128K tokens: 128K × 128K × 2 bytes = 32GB per attention head — impossible on a single GPU. Flash Attention tiles the computation using on-chip SRAM, never writing the full matrix to HBM. Memory is O(n), not O(n²). Flash Attention 2/3 further optimizes parallelism. Combined with GQA (fewer KV heads), it makes 128K+ context practical.

**Q: How does RoPE (Rotary Position Embedding) work and why is it the dominant position encoding?**
RoPE encodes position information by rotating the query and key vectors in 2D subspaces by angles proportional to their position, making attention scores naturally decay with distance. Unlike absolute position embeddings (learned vectors added at each position) or ALiBi (linear attention bias), RoPE is applied multiplicatively through rotation matrices in the attention computation. RoPE dominates because: (1) it naturally supports relative position encoding (attention depends on the distance between tokens, not their absolute position); (2) it extrapolates to longer sequences with techniques like NTK-aware scaling and YaRN; (3) it doesn't add parameters (the rotations are deterministic); (4) it works with grouped query attention and KV caching. LLaMA, Mistral, Qwen, and most modern models use RoPE. The base frequency (typically 10,000) determines the maximum effective context — increasing it (LLaMA 3 uses 500,000) extends context length at the cost of reduced position resolution for nearby tokens.

**Q: How does YaRN differ from ALiBi for extending context length?**
YaRN (Yet another RoPE extensioN) modifies RoPE's rotation frequencies to extend context without retraining, while ALiBi adds a linear attention bias that penalizes distant tokens. YaRN works by partitioning RoPE dimensions into three groups: low-frequency dimensions (interpolated — these encode long-range position), medium-frequency (NTK-interpolated), and high-frequency (unchanged — these encode local position). This allows extending context from 4K to 128K+ with minimal quality loss and only a few hundred steps of fine-tuning. ALiBi takes a different approach: it adds a position-dependent bias to attention scores (slope * |i-j|), causing attention to naturally attend more to nearby tokens. ALiBi advantages: no fine-tuning needed, simple implementation. YaRN advantages: better quality at very long contexts, works with existing RoPE models. In practice, YaRN has become more popular because most models already use RoPE, and YaRN extends them more effectively than ALiBi would require architectural changes.

**Q: What is the "lost in the middle" problem and how do long-context models address it?**
The "lost in the middle" problem (Liu et al., 2023) shows that LLMs retrieve information less accurately from the middle of long contexts compared to the beginning and end. For a 20-document retrieval context, information placed at positions 5-15 is recalled 10-20% less accurately than positions 1-3 or 18-20. This is a fundamental attention pattern issue — self-attention distribution tends to concentrate on initial tokens (attention sinks) and recent tokens (recency bias). Mitigations: (1) place the most important information at the beginning and end of the context; (2) reduce context size — 5 highly relevant chunks beat 20 mixed-relevance chunks; (3) models trained on long-context data (Claude 200K, Gemini 1M) show reduced but not eliminated middle-loss; (4) explicit instruction like "read all sections carefully before answering" helps marginally; (5) iterative summarization — process long documents in sections, then synthesize. For production RAG: rerank to keep only top-5 most relevant chunks rather than stuffing the context.

**Q: When should you use long context instead of RAG, and vice versa?**
Use long context when: (1) the entire document set fits in context (<200K tokens) and you need reasoning across all documents simultaneously; (2) the task requires understanding document structure, cross-references, or holistic summarization; (3) you need to answer arbitrary questions about a small, fixed document set (e.g., a single contract). Use RAG when: (1) the corpus is too large for any context window (millions of documents); (2) the corpus changes frequently (new documents added daily); (3) you need to scale to many users with different document access; (4) cost matters — processing 100K tokens per query is expensive ($0.50-$1.50 per query with GPT-4o). Hybrid approach: use RAG to retrieve relevant chunks, then use long context to process them together. Concrete numbers: Claude 3.5 at 200K tokens costs ~$0.60 per query; RAG with 5 chunks of 500 tokens costs ~$0.01. Long context is 60x more expensive but avoids retrieval errors.

**Q: How does context window size affect KV cache memory and inference cost?**
KV cache memory scales linearly with context length: memory = 2 * num_layers * hidden_dim * context_length * bytes_per_param * batch_size. For LLaMA 3 8B (32 layers, 4096 hidden, GQA 8 KV heads) in FP16: each token uses ~0.5MB of KV cache. At 128K context: 128K * 0.5MB = 64GB per sequence — nearly the entire A100 80GB, leaving minimal room for batching. Implications: (1) longer context means fewer concurrent sequences per GPU; (2) prefill cost is quadratic in context length (O(n^2) attention); (3) FP8 KV cache halves memory, doubling effective capacity; (4) techniques like sliding window attention (Mistral) limit KV cache to a fixed window, trading long-range attention for memory efficiency. Cost impact: doubling context length roughly doubles per-query cost (more prefill compute + more KV cache memory = fewer concurrent queries). This is why prompt caching (reuse KV cache for shared prefixes) provides 50-90% cost reduction for repeated system prompts.

**Q: What is sliding window attention and how does Mistral use it?**
Sliding window attention limits each token to attend only to the W most recent tokens (window size W), rather than all previous tokens. Mistral 7B uses W=4096, meaning each token attends to the nearest 4096 tokens. Benefits: (1) KV cache memory is bounded at W instead of growing with sequence length — at W=4096, KV cache stays constant regardless of total context length; (2) prefill compute is O(n*W) instead of O(n^2); (3) enables theoretically unlimited context length with fixed memory. How information propagates beyond the window: through multiple layers — if layer L attends to position P, and layer L+1 attends to layer L's output, information from position P-W can reach the current token through 2 layers. With 32 layers, effective receptive field is 32*4096 = 131K tokens. Limitations: information must be passed indirectly through intermediate layers, which is lossy — long-range dependencies are weaker than full attention. Mistral's approach works well for most tasks but struggles with tasks requiring precise recall of specific information from distant positions.

---

## 12. Best Practices

1. **Use Flash Attention 2 or 3** — no reason not to; required for any context >8K.
2. **Place most important information at start and end** — counteract "lost in the middle."
3. **Benchmark your context extension** — test RULER or LongBench before deploying long-context models.
4. **Use caching for repeated long contexts** — Anthropic's Prompt Caching, OpenAI's context caching — amortize prefill cost.
5. **Consider SSMs for very long context** — Mamba and Jamba (hybrid SSM + attention) offer linear-time alternatives.
6. **Monitor TTFT separately for long inputs** — 200K token prefill takes minutes; alert on TTFT SLA violations.

---

## 13. Case Study: Codebase Analysis System

**Problem:** Enterprise team wants an AI assistant that understands their entire 500K-token codebase and answers architecture questions, traces data flows, and suggests refactorings.

**Architecture:**
```
Codebase: 500K tokens (2500 files × 200 tokens avg)

Option 1: Pure long context (Gemini 1.5 Pro, 1M context)
  Put all 500K tokens in context
  Cost per query: 500K × $0.007/1K = $3.50 per question
  TTFT: 15-20 seconds for 500K prefill
  Quality: excellent for holistic architecture questions

Option 2: RAG only
  Index codebase in vector DB
  Retrieve top-20 relevant files (20K tokens)
  Cost per query: 20K × $0.015/1K = $0.30
  TTFT: 1-2 seconds
  Quality: poor for architecture questions (context too fragmented)

Option 3: Hybrid (chosen approach)
  1. Query analysis: classify as "specific" or "architectural"
  2. Specific queries (e.g., "what does function X do?"):
     → RAG: retrieve 3-5 relevant files → 10K context → cheap ($0.15)
  3. Architectural queries (e.g., "explain the payment flow"):
     → Smart selection: dependency graph → select 50 most relevant files
     → 100K context: Claude 3.5 Sonnet → medium cost ($1.50)
  4. Full codebase queries (e.g., "identify all god classes"):
     → Full context: Gemini 1.5 Pro 1M → expensive but necessary ($3.50)

Prompt caching:
  Repository stays fairly stable → cache the codebase prefix
  Anthropic prompt caching: 90% cost reduction on cached tokens
  Effective cost: ~$0.20 for cached architectural queries

Result:
  Specific query cost: $0.15 avg
  Architecture query cost: $0.35 avg (with caching)
  Full codebase query: $0.50 (with caching)
  Architecture accuracy: 91% (vs. 73% with RAG-only)
```
