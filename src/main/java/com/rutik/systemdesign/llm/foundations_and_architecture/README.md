# Foundations & Architecture

## 1. Concept Overview

Large Language Models (LLMs) are neural networks trained on massive text corpora to predict the next token in a sequence. The "large" refers to both the number of parameters (billions to trillions) and the scale of training data (trillions of tokens). The dominant architecture powering all modern LLMs is the **Transformer**, introduced by Vaswani et al. in "Attention Is All You Need" (2017).

Before transformers, sequence modeling relied on RNNs and LSTMs, which processed tokens sequentially — fundamentally limiting parallelism during training and struggling with long-range dependencies. The transformer replaced recurrence with **self-attention**: a mechanism where every token can directly attend to every other token in the sequence, computed in parallel.

This architectural shift enabled:
- **Massive parallelism** during training on GPUs/TPUs
- **Long-range dependency capture** without vanishing gradients
- **Scalability** — performance consistently improves with more data and more parameters (scaling laws)

---

## Intuition

> **One-line analogy**: A transformer is like a room of experts where everyone can instantly consult everyone else, then each person synthesizes the group's knowledge to refine their understanding.

**Mental model**: Imagine a sentence as a group of people in a meeting. Each person (token) sends out a "question" (Query) about what context they need, broadcasts what they know (Key), and holds their contribution (Value). Everyone simultaneously reads everyone else's question and decides how much of their knowledge to share. After this group consultation, each person updates their understanding. Repeat this 32-96 times (layers), and you get deeply contextualized representations.

**Why it matters**: This architecture is why LLMs can understand long-range dependencies ("the pronoun 'it' refers to the antecedent 20 words earlier"), generate coherent text, and scale predictably with compute. Every modern AI system — GPT, Claude, Gemini — is built on this same foundation.

**Key insight**: The magic isn't in any single component — it's that Q/K/V attention + residual connections + layer norm creates a stable, parallelizable, infinitely scalable architecture that learns richer representations the more data and parameters you throw at it.

---

## 2. Core Principles

- **Self-Attention**: Each token's representation is a weighted sum of all other tokens, where weights represent relevance. Captures context across arbitrary distances.
- **Multi-Head Attention**: Running multiple attention "heads" in parallel allows the model to attend to different types of relationships simultaneously (syntactic, semantic, coreference).
- **Positional Encoding**: Since attention is order-agnostic, positions are injected via sinusoidal functions (original) or learned embeddings or RoPE (modern).
- **Feed-Forward Layers**: Two-layer MLP applied independently to each token after attention. Stores factual knowledge.
- **Residual Connections**: `output = x + sublayer(x)` — prevents vanishing gradients, enables depth.
- **Layer Normalization**: Stabilizes training; applied before (pre-norm) or after (post-norm) each sublayer.
- **Token Prediction**: Autoregressive models predict the next token given all previous tokens (causal LM).

---

## 3. Types / Architectures

### 3.1 Encoder-Only (BERT-style)
- Sees the full bidirectional context during training (Masked Language Modeling)
- Best for: classification, NER, semantic search, embeddings
- Examples: BERT, RoBERTa, DeBERTa, ModernBERT

### 3.2 Decoder-Only (GPT-style)
- Causal (left-to-right) attention mask; each token only attends to previous tokens
- Best for: text generation, completion, chat, reasoning — almost all modern LLMs
- Examples: GPT-4, LLaMA 3, Mistral, Gemma, Qwen, Claude, DeepSeek

### 3.3 Encoder-Decoder (T5-style)
- Encoder processes input; decoder generates output attending to encoder representations
- Best for: translation, summarization, structured generation (seq2seq tasks)
- Examples: T5, FLAN-T5, BART, mT5

### 3.4 Mixture of Experts (MoE)
- Instead of a single FFN, multiple "expert" FFNs exist; a router selects K of N for each token
- Total parameters >> active parameters → cheaper inference at same quality
- Examples: Mixtral 8x7B, GPT-4 (rumored), DeepSeek-V3 (671B params, 37B active)

---

## 4. Architecture Diagrams

### Single Transformer Block
```
Input Tokens
     |
     v
[Token Embeddings + Positional Encoding]
     |
     v
+-----------------------------+
|  Transformer Block (x N)   |
|                             |
|  +---------------------+   |
|  | Layer Norm (pre)    |   |
|  +---------------------+   |
|           |                 |
|  +---------------------+   |
|  | Multi-Head Self-    |   |
|  | Attention           |   |
|  | Q=K=V=x*W           |   |
|  | Attn = softmax(QKᵀ  |   |
|  |         /√d) * V    |   |
|  +---------------------+   |
|           |                 |
|  +-- Residual Connection -- |
|           |                 |
|  +---------------------+   |
|  | Layer Norm (pre)    |   |
|  +---------------------+   |
|           |                 |
|  +---------------------+   |
|  | Feed-Forward Layer  |   |
|  | FFN(x) = GELU(xW1)W2|  |
|  +---------------------+   |
|           |                 |
|  +-- Residual Connection -- |
+-----------------------------+
     |
     v
[LM Head: Linear + Softmax over vocab]
     |
     v
Next Token Probabilities
```

### Multi-Head Attention Detail
```
Input x (seq_len × d_model)
     |
     +-----> Q = x * W_Q  (seq_len × d_k)
     +-----> K = x * W_K  (seq_len × d_k)
     +-----> V = x * W_V  (seq_len × d_v)
                   |
                   v
         Attention(Q,K,V) = softmax(QKᵀ / √d_k) * V
                   |
     [Repeat for h heads, concatenate, project]
                   |
                   v
             Output (seq_len × d_model)
```

### Decoder-Only Causal Mask
```
Token:    T1   T2   T3   T4   T5
T1:       ✓    ✗    ✗    ✗    ✗
T2:       ✓    ✓    ✗    ✗    ✗
T3:       ✓    ✓    ✓    ✗    ✗
T4:       ✓    ✓    ✓    ✓    ✗
T5:       ✓    ✓    ✓    ✓    ✓
(✓ = can attend, ✗ = masked out)
```

---

## 5. How It Works — Detailed Mechanics

### Q/K/V Intuition & Setup

Think of attention as a **library search system**:
- **Query (Q)**: "What am I looking for?" — the search query you type
- **Key (K)**: "What do I contain?" — the book's index/title
- **Value (V)**: "What information do I carry?" — the book's actual content

When you search a library (Q), you match your query against book indexes (K), and retrieve the content (V) from the best-matching books. A token "attending" to another token does exactly this.

**How Q, K, V are created from the same input:**

Every token starts as the same input embedding `x`. Three separate learned projection matrices transform it into different semantic spaces:

```
Q = x · W_Q    shape: [seq_len, d_k]    "what I'm searching for"
K = x · W_K    shape: [seq_len, d_k]    "what I offer as a match"
V = x · W_V    shape: [seq_len, d_v]    "what information I carry"

W_Q, W_K, W_V are all [d_model × d_k] — learned during training
```

The matrices project the same input embedding `x` into three different representation spaces. `W_Q` learns to extract "what this token needs", `W_K` learns "what this token can provide", and `W_V` learns "what content to aggregate when matched". They are trained jointly through backpropagation.

---

### Step-by-Step Data Flow Through a Transformer

Complete walkthrough with tensor shapes (LLaMA 3 8B config: d_model=4096, 32 heads, 8 KV heads, N=32 layers):

```
Input:  "Hello world"
        │
        ▼ Tokenizer (BPE)
Token IDs: [15496, 995]                    dtype: int64, shape: [2]
        │
        ▼ Embedding lookup  E[token_id, :]
Token embeddings: shape [seq_len=2, d_model=4096]   dtype: float32/bf16
        │
        ▼ Enter N=32 Transformer Blocks:
        │
        ├─► LayerNorm (RMSNorm)             [2, 4096] → [2, 4096]
        │
        ├─► Multi-Head Attention:
        │     Q = x · W_Q   [2,4096]·[4096,4096] = [2, 4096]  (32 heads × 128)
        │     K = x · W_K   [2,4096]·[4096,1024] = [2, 1024]  (8 KV heads × 128)
        │     V = x · W_V   [2,4096]·[4096,1024] = [2, 1024]  (8 KV heads × 128)
        │     Apply RoPE to Q, K (rotate by position)
        │     A = softmax(Q·Kᵀ / √128)     [2, 2] per head (causal mask)
        │     out = A · V   → concat heads → proj  [2, 4096]
        │
        ├─► Residual:  x = x + attention_out       [2, 4096]
        │
        ├─► LayerNorm (RMSNorm)             [2, 4096] → [2, 4096]
        │
        ├─► Feed-Forward (SwiGLU):
        │     gate = SiLU(x · W_gate)   [2,4096]·[4096,14336] = [2, 14336]
        │     up   = x · W_up           [2,4096]·[4096,14336] = [2, 14336]
        │     ffn  = (gate ⊙ up) · W_down [2,14336]·[14336,4096]=[2, 4096]
        │
        ├─► Residual:  x = x + ffn_out             [2, 4096]
        │
        └─► (repeat 32 times)
        │
        ▼ Final RMSNorm                    [2, 4096]
        ▼ Take last token position:        [1, 4096]
        ▼ LM Head: Linear(4096 → 128256)   [1, 128256]  (logits)
        ▼ Softmax                          [1, 128256]  (probabilities)
        ▼ Sample next token ID             → decode → "!"
```

---

### How Embeddings Work Internally

The embedding layer is a **learned lookup table**: a matrix `E` of shape `[vocab_size × d_model]`.

```python
# Conceptually:
token_ids = [15496, 995]       # token indices
embeddings = E[token_ids, :]   # shape: [2, d_model] — just indexing rows of E

# Each row E[i] is the d_model-dimensional vector for token i
# These vectors are learned during training via backpropagation
```

**Why embeddings capture meaning**: During training, tokens appearing in similar contexts receive similar gradient updates, pulling their vectors closer together. "king" and "queen" appear in similar contexts → their vectors become close. This enables the famous arithmetic: `king - man + woman ≈ queen`.

The embedding matrix `E` is `[vocab_size × d_model]` — for LLaMA 3 8B: `[128,256 × 4,096]` = 524M parameters (the single largest weight matrix in many models).

---

### How the LM Head Works

The LM head converts the final hidden state into a probability distribution over the vocabulary:

```
LM Head: Linear(d_model → vocab_size)  — no bias, no activation

hidden state: [d_model=4096]
     ↓  W_lm_head  shape: [vocab_size=128256, d_model=4096]
logits:       [128256]   — one unnormalized score per vocabulary token
     ↓  softmax (or divide by temperature first)
probs:        [128256]   — sums to 1.0
     ↓  sample (or argmax for greedy)
next token ID
```

**Weight tying**: In most modern LLMs, the LM head weight matrix is **shared with (transposed from) the embedding matrix** `E`:
```python
logits = hidden @ E.T    # shape: [vocab_size]
```
This works because E already encodes good token representations. The LM head is asking "how similar is my hidden state to each token's embedding?" — the token with the highest similarity is the most likely next token. Weight tying saves ~500M parameters and often improves quality.

---

### Softmax Temperature Mechanics

Temperature `T` controls the "sharpness" of the output distribution:

```
softmax_T(z_i) = exp(z_i / T) / Σ exp(z_j / T)
```

Effect of T on distribution shape:
- `T < 1` (e.g., 0.3): **sharpens** distribution → model picks high-probability tokens reliably, repetitive
- `T = 1`: **unchanged** — standard softmax
- `T > 1` (e.g., 1.5): **flattens** distribution → more random, creative, sometimes incoherent
- `T → 0`: **argmax** (greedy decoding, always picks top-1)
- `T → ∞`: **uniform** distribution (completely random)

**Numerical example** with logits `[3.0, 1.0, 0.5]`:
```
T = 0.5: softmax([6.0, 2.0, 1.0]) → [0.879, 0.119, 0.002]  ← sharp, confident
T = 1.0: softmax([3.0, 1.0, 0.5]) → [0.744, 0.100, 0.062]  ← normal
T = 2.0: softmax([1.5, 0.5, 0.25])→ [0.516, 0.260, 0.224]  ← flat, diverse
```

For factual tasks, use T≈0.0–0.3. For creative writing, T≈0.7–1.2. Above T=2.0, outputs degrade rapidly.

---

### Gradient Flow Through Residual Connections

Residual connections are the key reason transformers can be 100+ layers deep without vanishing gradients:

```
Forward:   y = x + F(x)               (F = attention or FFN sublayer)

Backward (chain rule):
  dL/dx = dL/dy × dy/dx
         = dL/dy × (1 + dF/dx)
         = dL/dy·1  +  dL/dy·dF/dx
           ↑               ↑
        "highway"     "learned path"
```

The `1` term creates a **gradient highway**: the gradient of the loss flows back through the residual connection unchanged, regardless of what `F(x)` does. Even if `dF/dx ≈ 0` (small or saturated gradients), the gradient still reaches early layers.

Without residuals, gradient for layer 1 = product of 100 Jacobians — any of them < 1 → exponential decay → vanishing gradient. With residuals, each layer adds to a running sum rather than multiplying, enabling stable training of models with 100+ layers.

---

### Attention Head Visualization

Different attention heads specialize in different linguistic patterns:

```
Diagonal pattern (local context):     Vertical stripe (anchor tokens):
  Each token attends to itself          All tokens attend to one key token
  T1 ████░░░░░░                         T1 ░░░░████
  T2 ░████░░░░                          T2 ░░░░████    e.g., [SEP], period,
  T3 ░░████░░░                          T3 ░░░░████    or the first token
  T4 ░░░████░░                          T4 ░░░░████

Syntactic (grammatical):               Global (holistic):
  "the" → "cat" (determiner→noun)       Uniform attention across all tokens
  verb → object, pronoun → antecedent   T1 ████████
                                        T2 ████████
```

Empirically, heads in lower layers tend to capture local syntax; heads in higher layers capture more semantic/global patterns. Some heads reliably attend to specific grammatical relationships (subject-verb, possessives) — these are interpretable.

**Visualization tools**: BertViz (interactive attention visualization), TransformerLens (interpretability toolkit for mechanistic analysis), Attention Rollout (aggregate attention across layers).

---

### Attention Computation
For a single head with queries Q, keys K, values V (all shape `[seq, d_k]`):

```
Attention(Q, K, V) = softmax(QKᵀ / √d_k) · V
```

- `QKᵀ` produces an `[seq × seq]` similarity matrix (dot product of query and key)
- Dividing by `√d_k` prevents vanishing gradients in softmax for large dimensions
- Softmax normalizes to attention weights (sum to 1 per row)
- Multiply by V: each token's output is a weighted sum of all value vectors

### Key Architectural Improvements in Modern LLMs

The original 2017 transformer has been refined substantially. Here is a comprehensive compilation:

**1. Attention Mechanisms**

| Improvement | Problem Solved | How It Works | Tradeoff |
|------------|---------------|--------------|---------|
| **MQA** (Multi-Query Attention) | KV cache too large | All Q heads share a single K, V head | Quality drop at high sharing ratios |
| **GQA** (Grouped Query Attention) | MQA too lossy, MHA too large | G groups of Q heads share K, V heads | Sweet spot: LLaMA 2+, Mistral |
| **Flash Attention 1** (2022) | O(n²) HBM memory for attention | Tile Q, K, V into SRAM; fused kernel; online softmax | Requires recompute in backward pass |
| **Flash Attention 2** (2023) | FA-1 poor GPU utilization | Better parallelism across warps; 2× faster than FA-1 | — |
| **Flash Attention 3** (2024) | H100 underutilized by FA-2 | Async pipeline; FP8 support; 75% peak FLOP utilization | H100-specific |
| **Sliding Window Attention** | Full attention wasteful for local tasks | Each token attends to ±w/2 neighbors; O(n×w) not O(n²) | Cannot directly attend far-away tokens |

**2. Positional Encoding**

| Approach | How It Works | Tradeoff |
|---------|-------------|---------|
| **Sinusoidal APE** (original) | Fixed sin/cos functions of position | Cannot extrapolate beyond training length |
| **RoPE** (Rotary PE) | Rotate Q, K by position; relative position = rotation difference | Graceful extrapolation with YaRN/LongRoPE; used in LLaMA, Mistral, Qwen |
| **ALiBi** | Subtract linear bias m×|i-j| from attention scores | Simple, extrapolates, but penalizes long-range; used in MPT, BLOOM |

**3. Normalization**

| Approach | Formula | Tradeoff |
|---------|---------|---------|
| **LayerNorm** (original) | Normalize by mean and variance | More expensive; pre-norm more stable than post-norm |
| **RMSNorm** | Normalize only by RMS (no mean subtraction) | ~8% faster; equally effective; used in LLaMA, Mistral, Gemma |

**4. Activations**

| Approach | Formula | Tradeoff |
|---------|---------|---------|
| **ReLU** (original) | `max(0, x)` | Simple; dying neuron problem |
| **GELU** | `x × Φ(x)` (smooth approximation) | Better for language; used in BERT, GPT-2 |
| **SwiGLU** | `SiLU(xW₁) ⊙ xW₂` (gated) | Best empirically; requires 2 weight matrices but expansion ratio 2/3 of standard FFN; used in LLaMA, PaLM |

**5. Sparsity & Scale**

| Improvement | Problem Solved | How It Works | Tradeoff |
|------------|---------------|--------------|---------|
| **Mixture of Experts (MoE)** | Dense FFN uses all parameters per token | N expert FFNs + router; K active per token | All experts must fit in memory; load balancing required |
| **Expert Choice Routing** | Top-K token routing causes load imbalance | Experts choose their top-K tokens (not vice versa) | Tokens may be processed by wrong number of experts |

**6. Alternative Architectures (Beyond Transformers)**

| Architecture | Key Idea | Strength | Weakness |
|-------------|---------|---------|---------|
| **Mamba / SSMs** (State Space Models) | Linear-time recurrence; selective state update | O(n) inference; no KV cache needed | Less effective on in-context learning than attention |
| **Jamba** (AI21) | Hybrid: interleaved Transformer + Mamba layers | Long context + strong quality | More complex training |
| **RWKV** | RNN-style but trainable like Transformer | Fast inference; no KV cache | Weaker on tasks requiring arbitrary lookback |

### Scaling Laws (Chinchilla)
Hoffman et al. (2022) showed optimal compute budget splits roughly equally between:
- Model parameters (N)
- Training tokens (D)

Optimal: `D ≈ 20 × N` (train 7B model on ~140B tokens for compute-optimal)

But in practice, models are often undertrained relative to Chinchilla-optimal because inference costs matter — training a smaller model on more data gives a model that is cheaper to run at equal quality (LLaMA's philosophy).

---

## 6. Real-World Examples

### OpenAI GPT Series
- GPT-1 (2018): 117M params, pre-training on BooksCorpus, first demonstration of transfer learning
- GPT-2 (2019): 1.5B params, "too dangerous to release" — first time the world noticed LLM capabilities
- GPT-3 (2020): 175B params, few-shot learning, API-first — changed the industry
- GPT-4 (2023): ~1.8T MoE (rumored), multimodal, RLHF-aligned — SOTA across benchmarks

### Meta LLaMA Series
- LLaMA 1 (2023): Open weights 7B-65B, Chinchilla-optimal training, sparked open-source LLM movement
- LLaMA 2 (2023): GQA, RLHF, 70B chat model
- LLaMA 3.1 (2024): 8B/70B/405B, 128K context, multilingual, strong reasoning

### Google
- PaLM/PaLM-2: Pathways architecture, multilingual, coding excellence
- Gemini 1.5 Pro: 1M token context via ring attention, multimodal natively

### Anthropic Claude
- Constitutional AI alignment, ~100K-200K context, strong instruction following

### DeepSeek
- DeepSeek-V3: 671B MoE, 37B active params, trained for $5.5M — challenged assumptions about training cost

---

## 7. Tradeoffs

| Aspect | Encoder-Only | Decoder-Only | Encoder-Decoder |
|--------|-------------|--------------|-----------------|
| Training objective | MLM (bidirectional) | CLM (causal) | Seq2seq |
| Generation ability | None (directly) | Excellent | Excellent |
| Understanding | Best | Good | Good |
| Inference speed | Fast (no generation) | Token by token | Slower |
| Best use case | Classification, embeddings | Chat, reasoning | Translation, summarization |

| Model Size | Parameters | Typical Use | Inference Cost |
|-----------|-----------|-------------|---------------|
| Small | 1-7B | Edge, latency-sensitive | <$0.001/1K tokens |
| Medium | 13-34B | Balanced tasks | ~$0.001-0.01/1K tokens |
| Large | 70-90B | Complex reasoning | ~$0.01-0.1/1K tokens |
| Giant | 200B+ | State of the art | >$0.1/1K tokens |

---

## 8. When to Use / When NOT to Use

### Use Decoder-Only When:
- You need open-ended text generation
- You're building a chatbot, code assistant, or reasoning system
- You want the simplest fine-tuning path (add new tasks with instruction tuning)

### Use Encoder-Only When:
- You need semantic embeddings (not generation)
- You're doing classification, NER, or reranking
- Latency is critical and no generation is needed

### Use Encoder-Decoder When:
- Your task is inherently seq2seq (translation, summarization with fixed source)
- You need to condition generation on structured input

### Do NOT Use LLMs When:
- Task is well-solved by simpler models (simple classifier, regex, lookup table)
- You need 100% deterministic output (use symbolic systems)
- Latency requirement is sub-millisecond (LLMs need >100ms minimum)
- Data is too small to benefit from LLM generalization (<1000 examples → classical ML)

---

## 9. Common Pitfalls

1. **Ignoring tokenization effects**: Model sees tokens, not characters. "1000000" might be 3 tokens; Chinese characters have different density. Affects prompt length estimation.
2. **Confusing temperature=0 with determinism**: Temperature=0 is greedy but not perfectly reproducible across different hardware/batching.
3. **Forgetting context window is quadratic cost**: Attention is O(n²) in sequence length. Long contexts are expensive.
4. **Assuming bigger is always better**: A well-prompted 7B model often outperforms a poorly-prompted 70B model.
5. **Not accounting for KV cache memory**: A 70B model serving 10 concurrent users at 32K context needs ~60GB for KV cache alone.

---

## 10. Technologies & Tools

| Tool / Framework | Purpose | Notes |
|-----------------|---------|-------|
| PyTorch | Training framework | De facto standard; dynamic graphs |
| Hugging Face Transformers | Model loading, inference | Largest model hub |
| Flash Attention 2 | Fast attention kernel | Required for efficient long-context |
| xFormers | Memory-efficient transformers | Meta's library |
| Megatron-LM | Large-scale training | NVIDIA's framework |
| DeepSpeed | ZeRO optimizer, distributed training | Microsoft |
| JAX/Flax | TPU training | Google's ecosystem |
| GGUF format | Quantized model format | llama.cpp compatible |
| SafeTensors | Safe model serialization | Replaces .bin/.pt files |

---

## 11. Interview Questions with Answers

**Q: What is self-attention and why is it better than RNNs?**
A: Self-attention computes relationships between all token pairs in O(n²) operations but O(1) sequential steps — fully parallelizable during training. RNNs process tokens sequentially (O(n) sequential steps), causing vanishing gradients for long-range dependencies. Self-attention captures any distance relationship in one step.

**Q: What is the difference between GPT and BERT architectures?**
A: BERT uses a bidirectional encoder — each token sees all other tokens (both left and right context) via masked language modeling. GPT uses a unidirectional decoder — each token only attends to previous tokens (causal mask) via next-token prediction. BERT excels at understanding tasks; GPT excels at generation.

**Q: What are scaling laws and why do they matter?**
A: Scaling laws (Kaplan et al., Hoffman et al.) show that LLM performance follows power laws with respect to model size (N), data (D), and compute (C). This means performance is predictable — you can estimate how much a model will improve before training. Chinchilla showed compute-optimal training requires balancing N and D equally.

**Q: Why do modern LLMs use RoPE instead of absolute positional encoding?**
A: RoPE (Rotary Position Embedding) encodes position as rotation in the complex plane. This naturally captures relative positions (the dot product of two rotated vectors depends only on their position difference, not absolute positions). It also extrapolates more gracefully to longer sequences than absolute encodings.

**Q: What is Grouped Query Attention (GQA)?**
A: GQA reduces the number of key/value heads while keeping query heads unchanged. Multiple query heads share the same K/V heads. This shrinks the KV cache size significantly (e.g., 8 query heads sharing 2 KV heads = 4x smaller KV cache) with minimal quality degradation.

**Q: What is a Mixture of Experts (MoE) and what's the key tradeoff?**
A: MoE replaces the single FFN in each transformer block with N expert FFNs plus a router that selects K experts per token. Total parameter count scales with N experts, but compute scales with K (active experts). You get large model capacity at smaller inference cost. Tradeoff: higher memory bandwidth (all experts must fit in memory even if not all are active), communication overhead in distributed settings.

**Q: How do you calculate KV cache memory requirements for a transformer model?**
KV cache memory per token = 2 (K and V) x num_layers x hidden_dim x bytes_per_param. For LLaMA 3 70B with 80 layers, 8192 hidden dim, FP16: 2 x 80 x 8192 x 2 bytes = 2.6MB per token per sequence. For a batch of 32 sequences at 4096 context length: 32 x 4096 x 2.6MB = 327GB — more than the model weights themselves (140GB in FP16). This is why KV cache management (PagedAttention, FP8 KV cache, GQA) is the critical bottleneck in LLM serving, not model computation.

**Q: What is Grouped Query Attention (GQA) and what concrete memory savings does it provide?**
GQA shares key-value heads across multiple query heads, reducing KV cache size proportionally. Standard multi-head attention (MHA) uses equal numbers of Q, K, V heads (e.g., 64 each). GQA groups query heads to share fewer KV heads — LLaMA 3 70B uses 64 query heads but only 8 KV heads (8:1 ratio), reducing KV cache by 8x compared to MHA. Multi-Query Attention (MQA) is the extreme case with a single KV head shared across all query heads. GQA provides the best quality-efficiency tradeoff: near-MHA quality with near-MQA memory savings. Practically, GQA enables serving 8x more concurrent users on the same GPU memory.

**Q: Why have decoder-only models dominated over encoder-decoder and encoder-only architectures?**
Decoder-only models dominate because they are the most versatile architecture for generative AI. Encoder-only models (BERT) excel at classification and understanding but cannot generate text autoregressively. Encoder-decoder models (T5, BART) can generate but require separate encoder and decoder stacks, doubling parameters for a given compute budget. Decoder-only models (GPT) use a single stack for both understanding and generation, scale more efficiently (all parameters contribute to every task), and naturally support in-context learning through autoregressive prediction. The scaling laws research (Chinchilla, Kaplan et al.) demonstrated that decoder-only models benefit most predictably from increased compute and data.

**Q: What is the "attention sink" phenomenon and why does it matter for long-context inference?**
Attention sink refers to the observation that transformer models assign disproportionately high attention weight to the first few tokens in the sequence, regardless of their semantic relevance. StreamingLLM (Xiao et al., 2023) showed that the first 4 tokens act as "attention sinks" — removing them causes catastrophic quality degradation even when remaining tokens are relevant. This matters for long-context inference because: (1) KV cache eviction strategies must never evict the first few tokens; (2) sliding window attention must retain initial tokens; (3) it explains why naive context truncation from the beginning breaks model quality. Practical implication: always keep a small "sink" window at the start of the context when doing any KV cache optimization.

---

## 13. Best Practices

1. **Use Flash Attention** — always enable it; saves 2-4x memory and speeds training significantly.
2. **Pre-norm over post-norm** — pre-norm (normalize before sublayer) is more stable for deep models.
3. **Use SwiGLU/GELU activations** — outperform ReLU empirically across tasks.
4. **Set weight decay on non-normalization parameters** — standard regularization for transformer training.
5. **Monitor gradient norms** — sudden spikes indicate training instability before loss diverges.
6. **Use learning rate warmup** — at least 1% of total steps with linear warmup, then cosine decay.
7. **Choose context length wisely** — longer context = quadratic attention cost; use Flash Attention 2 and GQA for long contexts.

---

## 14. Case Study: Designing a Scaled Transformer for 100B Parameters

**Problem:** Team wants to train a 100B parameter model on 2T tokens of text. Design the training setup.

**Architecture Decisions:**
- Decoder-only (GPT-style): best for general-purpose generation
- 96 transformer layers, hidden dim 12288, 96 attention heads with GQA (8 KV heads)
- SwiGLU FFN with expansion ratio 4/3 × 2 (gated), RMSNorm, RoPE
- Vocabulary: 100K BPE tokens (balanced for multilingual coverage)
- Flash Attention 2 for memory efficiency

**Infrastructure:**
- 1024 × H100 80GB GPUs across 128 nodes (8 GPUs/node)
- 3D parallelism: tensor parallel degree 8 (within node), pipeline parallel degree 8 (across 8 nodes), data parallel degree 16
- ZeRO Stage 2 for optimizer state sharding
- Mixed precision: BF16 for forward/backward, FP32 for optimizer states
- Gradient checkpointing to reduce activation memory

**Training Dynamics:**
- Batch size: 4M tokens (ramp up over first 1B tokens)
- Learning rate: 1e-4 peak, linear warmup 2000 steps, cosine decay to 1e-5
- Gradient clipping: 1.0
- Data: 2T tokens from curated web + books + code + math

**Estimated Cost:** ~2,000 GPU-days at ~$2/GPU-hour = ~$96K (rough estimate)

**Outcome:** Compute-optimal per Chinchilla at this scale; surpasses GPT-3 performance with modern architectural improvements.
