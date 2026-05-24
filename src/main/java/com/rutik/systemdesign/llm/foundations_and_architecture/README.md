# Foundations & Architecture

## Deep Dive Files

| File | Topic | Q&As |
|------|-------|------|
| [attention_mechanisms.md](attention_mechanisms.md) | Flash Attention internals, MQA/GQA/MLA, sparse/linear attention, derivations | 15+ |
| [positional_encoding.md](positional_encoding.md) | RoPE derivation, ALiBi, YaRN, NTK scaling, context extension | 15+ |
| [training_dynamics.md](training_dynamics.md) | Warmup theory, loss spikes, BF16/FP16, batch scaling, muP, data mixing | 15+ |

---

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
- **Layer Normalization**: Stabilizes training by normalizing activations across the hidden dimension. Two placement strategies exist:
  - **Post-LN** (original Transformer): `output = LN(x + sublayer(x))` — LayerNorm after residual addition. Gradients at early layers can explode or vanish, requiring careful learning rate warmup.
  - **Pre-LN** (modern standard): `output = x + sublayer(LN(x))` — LayerNorm before the sublayer. The residual path stays "clean" (no normalization on it), producing more stable gradient flow. Tradeoff: some studies show slightly lower final performance, but dramatically easier training.
  - Nearly all modern LLMs use Pre-LN: GPT-2+, LLaMA, Mistral, Falcon, Qwen.
  - **RMSNorm** (LLaMA, Mistral, Gemma): Simplified LayerNorm that normalizes by root-mean-square only, skipping mean centering. ~10-15% faster than standard LayerNorm with equivalent quality. Used in all LLaMA variants.
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

### 3.5 Attention Variants by Span

Standard self-attention computes O(n^2) interactions — every token attends to every other token. Several architectural variants reduce this cost:

| Variant | Attention Span | Complexity | Key Idea |
|---------|---------------|------------|----------|
| **Full causal attention** | All previous tokens | O(n^2) | Standard decoder-only attention |
| **Sliding window attention** | W previous tokens only | O(n * W) | Each token attends to a fixed local window |
| **Longformer** | Local window + global on special tokens | O(n * W + n * g) | Combines local sliding window with global attention on [CLS] or task-specific tokens |
| **BigBird** | Local + global + random | O(n * (W + g + r)) | Adds random attention connections for theoretical guarantees |

**Sliding Window Attention** is the most widely adopted variant in production LLMs:
- Each token attends only to the W previous tokens (e.g., W=4096 in Mistral 7B)
- Complexity drops from O(n^2) to O(n * W) — linear in sequence length for a fixed window size
- Information propagation across layers: with L layers and window W, information can flow across L * W tokens total through stacking
- Mistral 7B: W=4096, 32 layers -> effective attention span of 131,072 tokens despite each layer seeing only 4096
- Combines naturally with a **rolling KV cache**: only store W entries per layer instead of the full sequence length
- KV cache savings: at 32K sequence length with W=4096, the rolling cache saves ~87% KV cache memory compared to full attention

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

### Sliding Window Attention — Detailed Mechanics

Standard causal attention at position `i` computes scores against all positions `0..i-1`, producing an `[n x n]` lower-triangular matrix. Sliding window attention restricts this to positions `max(0, i-W)..i-1`:

```
Full causal mask (n=8):        Sliding window mask (n=8, W=3):
T1: X . . . . . . .           T1: X . . . . . . .
T2: X X . . . . . .           T2: X X . . . . . .
T3: X X X . . . . .           T3: X X X . . . . .
T4: X X X X . . . .           T4: . X X X . . . .
T5: X X X X X . . .           T5: . . X X X . . .
T6: X X X X X X . .           T6: . . . X X X . .
T7: X X X X X X X .           T7: . . . . X X X .
T8: X X X X X X X X           T8: . . . . . X X X

Full: 36 active cells             Window: 22 active cells (39% fewer)
At n=32K, W=4096: ~87% fewer attention computations
```

**Information propagation through layer stacking:**
```
Layer 1: Token at position i sees positions [i-W, i]
Layer 2: Token at position i sees positions [i-2W, i]  (because tokens at i-W already saw i-2W)
...
Layer L: Token at position i sees positions [i-L*W, i]

Mistral 7B: W=4096, L=32 layers
  -> Effective span = 32 * 4096 = 131,072 tokens
  -> Far exceeds the 32K context window
```

**Rolling KV cache** pairs naturally with sliding window attention. Instead of caching all past keys/values (growing linearly with sequence length), maintain a circular buffer of size W per layer:

```
Standard KV cache at position 10000:  stores 10000 entries per layer
Rolling KV cache at position 10000:   stores 4096 entries per layer (W=4096)
  -> Position 10000 overwrites the slot at index (10000 mod 4096) = 1712
  -> Memory: fixed at W * num_layers * 2 * d_head * bytes regardless of sequence length
```

For Mistral 7B serving at 32K context: standard KV cache needs ~2.1GB per sequence; rolling cache needs ~270MB per sequence — a fixed cost that does not grow with sequence length.

---

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
| **Sliding Window Attention** | Full attention wasteful for local tasks | Each token attends to W previous tokens; O(n*W) not O(n^2); rolling KV cache stores only W entries per layer | Cannot directly attend beyond window; relies on layer stacking for long-range flow (L layers * W = effective span) |

**2. Positional Encoding**

| Approach | How It Works | Tradeoff |
|---------|-------------|---------|
| **Sinusoidal APE** (original) | Fixed sin/cos functions of position | Cannot extrapolate beyond training length |
| **RoPE** (Rotary PE) | Rotate Q, K by position; relative position = rotation difference | Graceful extrapolation with YaRN/LongRoPE; used in LLaMA, Mistral, Qwen |
| **ALiBi** | Subtract linear bias m×|i-j| from attention scores | Simple, extrapolates, but penalizes long-range; used in MPT, BLOOM |

**3. Normalization**

| Approach | Formula | Tradeoff |
|---------|---------|---------|
| **Post-LN** (original) | `LN(x + sublayer(x))` — normalize after residual | Gradient instability in early layers; requires careful warmup; slightly higher final quality in some studies |
| **Pre-LN** (modern standard) | `x + sublayer(LN(x))` — normalize before sublayer | Clean residual path, stable gradients, easier training; used in GPT-2+, LLaMA, Mistral, Falcon |
| **RMSNorm** | Normalize only by RMS (no mean subtraction) | ~10-15% faster than LayerNorm; equivalent quality; used in LLaMA, Mistral, Gemma |

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

**Q: How does sliding window attention achieve long-range information flow despite each layer only seeing a local window?**
Sliding window attention restricts each token to attend only to the W previous tokens within a single layer, reducing per-layer complexity from O(n^2) to O(n * W). Long-range information flows through layer stacking: at layer 1, token i sees positions [i-W, i]; at layer 2, it effectively sees [i-2W, i] because tokens at position i-W already incorporated information from i-2W in the previous layer. With L layers and window W, the effective attention span is L * W tokens. Mistral 7B uses W=4096 and 32 layers, giving an effective span of 131,072 tokens — far exceeding its 32K context window. The practical benefit extends to memory: a rolling KV cache stores only W entries per layer in a circular buffer instead of the full sequence length, saving ~87% KV cache memory at 32K context. This fixed-size cache means memory does not grow with sequence length, which is critical for streaming and long-document inference.

**Q: What is the difference between Pre-LN and Post-LN, and why do nearly all modern LLMs use Pre-LN?**
Post-LN (original Transformer) applies LayerNorm after the residual addition: `output = LN(x + sublayer(x))`. Pre-LN applies LayerNorm before the sublayer: `output = x + sublayer(LN(x))`. The critical difference is gradient flow stability. In Post-LN, the residual path passes through LayerNorm, which can distort gradients — early layers receive unstable gradients that require careful learning rate warmup (often 10K+ steps) to avoid divergence. In Pre-LN, the residual path is "clean" (identity connection with no normalization), so gradients flow directly to early layers without distortion. This makes Pre-LN dramatically easier to train at scale. The tradeoff: some studies show Post-LN achieves marginally higher final quality when training succeeds, but the training instability makes it impractical for billion-parameter models. GPT-2+, LLaMA, Mistral, and Falcon all use Pre-LN. Most modern models further replace standard LayerNorm with RMSNorm (normalizing by root-mean-square only, skipping mean centering), which is ~10-15% faster with no quality loss.

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


## 14. Case Study

**Scenario:** A consumer AI company serves GPT-4-scale transformer inference to 1M daily active users. The model is a 70B parameter dense decoder-only transformer (LLaMA-3-70B architecture: 80 layers, hidden dim 8192, 64 attention heads, 8 KV heads via GQA, context window 8192 tokens). Traffic: 500 RPS peak, average prompt 600 tokens, average output 400 tokens, p99 TTFT SLA 700ms, p99 decode latency 45ms/token, monthly GPU budget $180k.

**Architecture:**

```
  User Request
       |
       v
  ┌─────────────────────────────────────────────────────────┐
  │  API Gateway + Load Balancer                            │
  │  - Request validation, auth, rate limiting              │
  │  - Route to least-loaded inference pod                  │
  └──────────────────────────┬──────────────────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
   ┌──────▼──────┐    ┌──────▼──────┐   ┌──────▼──────┐
   │  Pod 0      │    │  Pod 1      │   │  Pod 2      │
   │  TP=4       │    │  TP=4       │   │  TP=4       │
   │  4×H100 80G │    │  4×H100 80G │   │  4×H100 80G │
   │  vLLM v1    │    │  vLLM v1    │   │  vLLM v1    │
   └─────────────┘    └─────────────┘   └─────────────┘

  Memory Layout (per Pod, 4×H100 80GB NVLink):
  ┌──────────────────────────────────────────┐
  │  GPU 0 (80 GB)                           │
  │  Model shard (FP8, TP=4): 70GB/4/2 = 8.75GB│
  │  KV cache (paged, 16-tok blocks):        │
  │    Remaining: ~70 GB                     │
  │    Each block: 80 layers × 2 × 8 KV heads│
  │    × 128 head dim × 16 tokens × 1 byte  │
  │    = 80×2×8×128×16 = 3.28 MB/block      │
  │    Blocks available: 70,000 MB / 3.28 MB │
  │    = ~21,000 blocks = 336,000 KV tokens  │
  └──────────────────────────────────────────┘

  Attention Bottleneck Analysis:
  Prefill (compute-bound):
    FLOPs for 600-token prefill: 2 × seq² × hidden (self-attn)
      = 2 × 600² × 8192 = 5.9B FLOPs for attention alone
    + FFN: 2 × seq × 4 × hidden² = 2 × 600 × 32768 × 8192 = 322B FLOPs
    Total per layer: ~328B FLOPs; 80 layers: 26 TFLOPs per request
    H100 BF16: 989 TFLOPS → 80 requests fill GPU compute at 500 RPS

  Decode (memory-bandwidth-bound):
    Each decode step loads all 70B weights once: 70B bytes (FP8) = 70 GB
    H100 HBM3 bandwidth: 3.35 TB/s → 70 GB loads in 20.9ms
    → Decode speed ceiling: 1 / 20.9ms = 47.8 tokens/sec/GPU (single request)
    With batching 16 requests: amortize weight loads → 16×47.8 = 765 tok/s
```

**Key implementation — 3 Python code blocks:**

Block 1 — Attention mechanism with Flash Attention 2 and GQA:

```python
from __future__ import annotations
import math
import torch
import torch.nn as nn
import torch.nn.functional as F


class GroupedQueryAttention(nn.Module):
    """
    GQA (Grouped Query Attention) as used in LLaMA-3-70B.
    num_heads=64 query heads, num_kv_heads=8 KV heads.
    Each KV head is shared by 64/8=8 query heads.
    Reduces KV cache size by 8× vs MHA with no quality loss at 70B scale.
    Uses Flash Attention 2 for memory-efficient attention computation.
    """

    def __init__(
        self,
        hidden_dim: int = 8192,
        num_heads: int = 64,
        num_kv_heads: int = 8,
        head_dim: int = 128,
        dropout: float = 0.0,
    ) -> None:
        super().__init__()
        assert num_heads % num_kv_heads == 0
        self.num_heads = num_heads
        self.num_kv_heads = num_kv_heads
        self.head_dim = head_dim
        self.kv_groups = num_heads // num_kv_heads  # 8

        self.q_proj = nn.Linear(hidden_dim, num_heads * head_dim, bias=False)
        self.k_proj = nn.Linear(hidden_dim, num_kv_heads * head_dim, bias=False)
        self.v_proj = nn.Linear(hidden_dim, num_kv_heads * head_dim, bias=False)
        self.o_proj = nn.Linear(num_heads * head_dim, hidden_dim, bias=False)
        self.dropout = dropout

    def forward(
        self,
        x: torch.Tensor,                    # (batch, seq, hidden)
        position_ids: torch.Tensor,
        kv_cache: tuple[torch.Tensor, torch.Tensor] | None = None,
    ) -> tuple[torch.Tensor, tuple[torch.Tensor, torch.Tensor]]:
        B, S, _ = x.shape

        q = self.q_proj(x).view(B, S, self.num_heads, self.head_dim).transpose(1, 2)
        k = self.k_proj(x).view(B, S, self.num_kv_heads, self.head_dim).transpose(1, 2)
        v = self.v_proj(x).view(B, S, self.num_kv_heads, self.head_dim).transpose(1, 2)

        # Apply RoPE (simplified)
        q, k = _apply_rope(q, k, position_ids)

        # Append to KV cache (for incremental decode)
        if kv_cache is not None:
            k = torch.cat([kv_cache[0], k], dim=2)
            v = torch.cat([kv_cache[1], v], dim=2)
        new_cache = (k, v)

        # Expand KV heads to match Q heads (repeat each KV head kv_groups times)
        # Memory stays compact in KV cache; expansion only for attention compute
        k_expanded = k.repeat_interleave(self.kv_groups, dim=1)   # (B, num_heads, S, head_dim)
        v_expanded = v.repeat_interleave(self.kv_groups, dim=1)

        # Flash Attention 2 (memory-efficient O(S) memory, not O(S²))
        # In production: use flash_attn.flash_attn_func directly
        scale = 1.0 / math.sqrt(self.head_dim)
        attn_out = F.scaled_dot_product_attention(
            q, k_expanded, v_expanded,
            scale=scale,
            dropout_p=self.dropout if self.training else 0.0,
            is_causal=True,
        )   # Uses Flash Attention 2 kernel under torch.compile on CUDA

        attn_out = attn_out.transpose(1, 2).contiguous().view(B, S, -1)
        return self.o_proj(attn_out), new_cache


def _apply_rope(
    q: torch.Tensor, k: torch.Tensor, position_ids: torch.Tensor
) -> tuple[torch.Tensor, torch.Tensor]:
    """RoPE positional encoding (simplified). Production uses fused kernel."""
    head_dim = q.shape[-1]
    theta = 10000.0
    freq = 1.0 / (theta ** (torch.arange(0, head_dim, 2, dtype=torch.float) / head_dim))
    # Full RoPE implementation omitted for brevity — use transformers RoPE class
    return q, k   # passthrough for illustration
```

Block 2 — KV cache memory planning and eviction policy (production concern):

```python
from __future__ import annotations
from dataclasses import dataclass, field
import math


@dataclass
class KVCacheConfig:
    """
    KV cache memory planning for a 70B GQA model on 4×H100 80GB (TP=4).
    Goal: maximize concurrent sequences without OOM.
    """

    num_layers: int = 80
    num_kv_heads: int = 8
    head_dim: int = 128
    block_size_tokens: int = 16          # PagedAttention block granularity
    dtype_bytes: int = 1                 # FP8 KV cache
    gpu_count_per_pod: int = 4
    gpu_vram_gb: float = 80.0
    model_weights_gb: float = 35.0       # 70B FP8 / 4 GPUs = 8.75 GB/GPU; total 35 GB

    @property
    def kv_bytes_per_block(self) -> int:
        """KV bytes per PagedAttention block."""
        # 2 = key + value; block_size_tokens tokens per block
        return (
            self.num_layers
            * 2
            * self.num_kv_heads
            * self.head_dim
            * self.block_size_tokens
            * self.dtype_bytes
        )

    @property
    def available_kv_gb(self) -> float:
        """VRAM available for KV cache after model weights."""
        # 8% headroom for fragmentation and misc buffers
        return (self.gpu_vram_gb * self.gpu_count_per_pod - self.model_weights_gb) * 0.92

    @property
    def total_blocks(self) -> int:
        available_bytes = self.available_kv_gb * 1024**3
        return int(available_bytes / self.kv_bytes_per_block)

    @property
    def max_concurrent_tokens(self) -> int:
        return self.total_blocks * self.block_size_tokens

    def max_concurrent_sequences(self, avg_seq_len: int = 1000) -> int:
        return self.max_concurrent_tokens // avg_seq_len

    def report(self) -> dict[str, object]:
        return {
            "kv_bytes_per_block_MB": self.kv_bytes_per_block / 1024**2,
            "available_kv_GB": self.available_kv_gb,
            "total_blocks": self.total_blocks,
            "max_concurrent_tokens": self.max_concurrent_tokens,
            "max_concurrent_seqs_at_1k_avg": self.max_concurrent_sequences(1000),
        }


# Example output for 70B on 4×H100 with FP8 KV:
# kv_bytes_per_block_MB: 3.28 MB
# available_kv_GB: 266 GB (4×80 - 35) × 0.92
# total_blocks: 81,091 blocks
# max_concurrent_tokens: 1,297,462 tokens
# max_concurrent_seqs_at_1k_avg: 1,297 sequences per pod
```

Block 3 — BROKEN -> FIX: naive attention in decode and prefill batching collision:

```python
from __future__ import annotations
import torch


# BROKEN: Standard O(N²) attention — stores full N×N attention matrix in GPU memory.
# For 8192-token context, attention matrix = 8192² × 2 bytes = 128 MB per head.
# 64 heads × 80 layers = 655 GB per request — completely infeasible.
def broken_standard_attention(
    q: torch.Tensor, k: torch.Tensor, v: torch.Tensor
) -> torch.Tensor:
    scale = q.shape[-1] ** -0.5
    attn_weights = torch.softmax(q @ k.transpose(-2, -1) * scale, dim=-1)
    # attn_weights shape: (batch, heads, seq, seq) — O(seq²) memory
    return attn_weights @ v   # OOM for seq > 4096 with multiple heads


# FIX: Flash Attention 2 — computes attention in tiles, never materializes
# the full N×N matrix. Memory is O(N) not O(N²).
# In production: use flash_attn library or torch.compile with SDPA.
def fixed_flash_attention(
    q: torch.Tensor, k: torch.Tensor, v: torch.Tensor, causal: bool = True
) -> torch.Tensor:
    return torch.nn.functional.scaled_dot_product_attention(
        q, k, v,
        is_causal=causal,
        # torch automatically dispatches to Flash Attention 2 on CUDA
        # when inputs are float16/bfloat16 and CUDA is available
    )


# BROKEN: Batch prefill and decode requests together naively.
# A 8192-token prefill takes ~80ms compute; a 1-token decode takes 2ms.
# Batching them: decode requests wait 80ms for the prefill to finish.
# p99 decode latency: 80ms → violates 45ms/token SLA.
def broken_batch_mixed(prefill_requests: list, decode_requests: list) -> list:
    batch = prefill_requests + decode_requests  # mix them — decode starved
    return _process_batch(batch)


# FIX: Chunked prefill — break long prefills into chunks of 2048 tokens.
# Scheduler interleaves prefill chunks with decode steps.
# Decode requests get a step every 2048 prefill tokens instead of waiting for 8192.
# P99 decode latency drops from 80ms to 12ms (one 2048-chunk ≈ 20ms, shared).
def fixed_chunked_prefill(
    prefill_request: dict, decode_requests: list, chunk_size: int = 2048
) -> list:
    prompt_tokens = prefill_request["tokens"]
    results = []
    for chunk_start in range(0, len(prompt_tokens), chunk_size):
        chunk = prompt_tokens[chunk_start : chunk_start + chunk_size]
        # Process this prefill chunk
        results.extend(_process_batch([{"tokens": chunk, "type": "prefill"}]))
        # Interleave with decode requests
        results.extend(_process_batch([{**r, "type": "decode"} for r in decode_requests]))
    return results


def _process_batch(batch: list) -> list:
    return []   # placeholder
```

**Pitfall 1 — KV cache thrashing from long prompt requests:**

```python
# BROKEN: Accept all request lengths without limit.
# A 8192-token prompt for one request uses 8192/16=512 KV blocks.
# At 21,000 blocks per GPU, one long request takes 2.4% of total KV cache.
# 50 concurrent long requests = 25,600 blocks — KV cache exhausted, others preempted.

# FIX: Tiered queue with per-tier max_model_len limits.
# Long requests (> 4096 tokens) go to dedicated low-concurrency pods.
# Standard requests (< 2048 tokens) share high-concurrency pods.
def route_by_prompt_length(prompt_tokens: int) -> str:
    if prompt_tokens > 4096:
        return "long_context_pod"   # max_num_seqs=50, TP=8
    return "standard_pod"           # max_num_seqs=512, TP=4
```

**Pitfall 2 — Tensor parallel AllReduce over PCIe instead of NVLink:**

```python
# BROKEN: TP=4 across GPUs on different PCIe switches.
# Each transformer layer requires 2 AllReduce operations.
# PCIe bandwidth: 32 GB/s; each AllReduce for 70B layer: ~200 MB.
# Latency per AllReduce: 200MB / 32GB/s = 6.25ms; 80 layers × 2 = 1 second added per step.
# Decode becomes compute-and-communication-bound — unusable.

# FIX: Ensure all TP GPUs are within same NVLink domain.
# NVLink: 600 GB/s; same AllReduce = 200MB / 600GB/s = 0.33ms; 80 layers × 2 = 53ms.
# Verify with: nvidia-smi topo -m — NVLink connections shown as NV4/NV18.
```

**Pitfall 3 — Using BF16 KV cache instead of FP8:**

```python
# BROKEN: KV cache stored in BF16 (2 bytes per element).
# 70B model, 4×H100: KV bytes/block = 80×2×8×128×16×2 = 6.55 MB/block.
# Available blocks: 266 GB / 6.55 MB = 40,610 blocks → 649,760 max tokens.
# Max concurrent 1k-token sequences: 649.

# FIX: Use FP8 KV cache (1 byte per element).
# KV bytes/block: 3.28 MB; blocks: 81,091; max tokens: 1.3M; sequences: 1,297.
# 2× the concurrent capacity at same GPU count, same model quality.
# vLLM: --kv_cache_dtype fp8; requires H100 (FP8 tensor core support).
```

**Metrics:**

| Metric | Naive (BF16 KV, no Flash Attn) | Optimized (FP8 KV, Flash Attn, GQA) |
|--------|-------------------------------|--------------------------------------|
| p50 TTFT | 1,200 ms | 180 ms |
| p99 TTFT | 3,800 ms | 650 ms |
| p99 decode latency | 120 ms/token | 38 ms/token |
| Max concurrent sequences | 320 | 1,280 |
| GPU utilization | 41% | 74% |
| KV cache hit rate (prefix) | 0% | 68% |
| Monthly GPU cost | $180K | $72K (same load, fewer pods) |
| Throughput | 180 RPS | 520 RPS |

**Interview Q&As:**

**Q: Why does the decode phase have fundamentally different performance characteristics than the prefill phase?**
Prefill processes all prompt tokens in parallel — it is compute-bound (high arithmetic intensity). Decode generates one token at a time, requiring a full forward pass through all model weights to produce each token — it is memory-bandwidth-bound (low arithmetic intensity: load 70 GB of weights to produce 1 token). This is why batching dramatically improves decode throughput: processing 16 decode requests together loads the weights once, amortizing the 20ms memory load across 16 tokens. Compute bottlenecks benefit from faster GPUs; memory bandwidth bottlenecks benefit from larger batches.

**Q: How does Grouped Query Attention (GQA) reduce KV cache memory without significantly hurting quality?**
GQA uses fewer KV heads than query heads — LLaMA-3-70B has 64 query heads but only 8 KV heads. Each KV head is shared by 8 query heads. The KV cache stores only 8 sets of keys and values, reducing KV cache size by 8× compared to Multi-Head Attention. During attention computation, each KV head is replicated across the 8 query heads that use it — this expansion is cheap (a tensor repeat_interleave) and happens entirely in registers. Research (Ainslie et al. 2023) shows GQA achieves 95-99% of MHA quality at 70B+ scale while reducing KV cache 8×.

**Q: What is chunked prefill and how does it improve decode latency fairness?**
Without chunked prefill, a long 8192-token prompt monopolizes the GPU for ~80ms while all other decode requests wait. Chunked prefill breaks the prompt into chunks of 2048 tokens; the scheduler interleaves one prefill chunk with decode steps from waiting requests. Decode requests get a turn every ~20ms (one chunk) instead of waiting 80ms, reducing p99 decode latency by 4×. The tradeoff is slightly higher TTFT for the long prompt (it now takes 4 scheduling rounds instead of 1), but TTFT fairness is generally more important than minimizing a single long prompt's prefill time.

**Q: How does Flash Attention 2 reduce memory complexity from O(N²) to O(N)?**
Standard attention materializes the full N×N attention weight matrix — for N=8192 tokens and 64 heads, this is 8192² × 64 × 2 bytes ≈ 8.6 GB per layer, infeasible. Flash Attention 2 (Dao 2023) tiles the attention computation — processes Q, K, V in small blocks that fit in SRAM (on-chip cache), computing the softmax denominator incrementally without materializing the full matrix. The final output is computed by accumulating tiled attention-weighted values. Peak SRAM usage is O(block_size²) which is constant; total VRAM usage for activations is O(N). For a 8192-token sequence, Flash Attention 2 reduces attention memory from 8.6 GB to ~64 MB per layer.

**Q: Why is prefix caching important for production LLM serving and what is its cache hit rate bound?**
Prefix caching reuses KV cache blocks from previous requests that share a common prefix (typically a system prompt). If the system prompt is 512 tokens and all requests share it, those 512 tokens' KV blocks are computed once and reused by all subsequent requests — saving 512 tokens of prefill computation per request. At 500 RPS with a shared system prompt, this saves 256,000 tokens of prefill per second, reducing TTFT by ~100ms and cutting GPU compute by 30-40%. Cache hit rate is bounded by prefix sharing ratio — if 90% of requests share the same system prompt, hit rate approaches 90% for that prefix.

**Q: What determines whether to use TP=2, TP=4, or TP=8 for serving a 70B model?**
The primary drivers are: (1) Model fit — 70B in FP8 is 70 GB; one H100 (80 GB) can barely hold it with no KV cache. TP=2 allows 35 GB per GPU + 45 GB for KV, which is viable. (2) Communication overhead — AllReduce per layer adds latency proportional to TP degree over the available bandwidth. With NVLink (600 GB/s), TP=4 adds ~2ms per layer vs 0.5ms for TP=2. For low-latency SLAs (p99 < 50ms/token), TP=2 with FP8 often wins. (3) Throughput — higher TP allows more KV cache headroom, supporting more concurrent sequences. For throughput-optimized serving (high RPS), TP=4 or TP=8 on NVLink systems is preferred.
