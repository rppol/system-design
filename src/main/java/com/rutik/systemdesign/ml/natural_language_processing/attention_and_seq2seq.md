# Attention Mechanisms and Sequence-to-Sequence Models

> This file is a deep-dive sub-file of the [Natural Language Processing](README.md) module.
> It covers attention mechanism evolution, encoder-decoder architectures, and decoding strategies.
> Self-attention and transformer internals are covered in the LLM section (`llm/foundations_and_architecture/`).
> LSTM/GRU internals and Bahdanau attention implementation are covered in `ml/recurrent_neural_networks/`.

---

## 1. Concept Overview

Attention mechanisms are the bridge between classical sequence models (RNNs/LSTMs) and modern transformers. Before attention, encoder-decoder RNN models compressed an entire input sequence into a single fixed-size context vector — a bottleneck that degraded performance on long sequences because all information had to flow through one vector of dimension 256-512.

Bahdanau et al. (2015) introduced attention: instead of a fixed context vector, the decoder at each step dynamically queries the encoder to retrieve a weighted combination of all encoder hidden states. This gives the decoder a "soft alignment" over the input — it can focus on the relevant parts when generating each output token.

The evolution: Bahdanau (additive) attention → Luong (multiplicative) attention → self-attention (transformer attention) → modern decoding strategies. Understanding this evolution is essential for senior ML engineer interviews because it explains why transformers work, what attention is actually computing, and when to choose different decoding strategies.

---

## 2. Intuition

One-line analogy: Attention is a differentiable soft alignment — like highlighting relevant words in a source sentence before translating each target word, where the highlights are learned end-to-end rather than hand-engineered.

Mental model: Imagine translating "The cat sat on the mat" to French. To generate "chat" (cat), you intuitively focus on "cat" in the source. To generate "assis" (sat), you focus on "sat". Attention formalizes this: for each target word, compute a learned alignment weight over all source words, then take a weighted average of their representations.

Why it matters: The attention pattern is directly interpretable — visualizing attention weights shows which source tokens the model "looked at" when producing each output. This was the first interpretable component in neural sequence models.

Key insight: Attention doesn't just solve the bottleneck problem — it fundamentally changes what the model learns. An encoder-decoder without attention must learn a representation that simultaneously encodes all information needed for all output positions. With attention, the encoder only needs to produce per-position representations; the decoder's attention mechanism handles the alignment.

---

## 3. Core Principles

**Encoder-decoder architecture:** The encoder processes the input sequence and produces a sequence of hidden states (not a single vector). The decoder generates the output sequence token by token, conditioned on the decoder's own state and a context vector computed by attention over encoder states.

**Alignment model (energy function):** Given decoder hidden state `s_t` and encoder hidden state `h_i`, compute an alignment score (energy) `e_{t,i}` that measures how relevant position i is for generating output at time t.

**Attention weights (soft alignment):** Apply softmax over all alignment scores to get a probability distribution `α_{t,i} = softmax(e_{t,i})`. These weights sum to 1 and represent the "fraction of attention" the decoder pays to each encoder position.

**Context vector:** `c_t = Σ_i α_{t,i} * h_i` — a weighted sum of encoder hidden states, emphasizing positions most relevant to the current decoding step.

**Exposure bias:** During training with teacher forcing, the decoder receives the ground-truth previous token at each step. During inference, it receives its own (potentially wrong) prediction. This mismatch — exposure bias — is a systematic error source. Scheduled sampling (gradually mixing predicted and ground-truth tokens during training) partially mitigates it.

**Label smoothing:** Replaces the hard 0/1 target distribution with a soft distribution (ε/(V-1) for non-target tokens, 1-ε for the target token, typically ε=0.1). Prevents the model from being overconfident on training targets and improves calibration and generalization.

---

## 4. Types / Architectures / Strategies

### 4.1 Attention Mechanisms

| Type | Energy Function | Parameters | Notes |
|------|----------------|------------|-------|
| **Bahdanau (additive)** | `v^T · tanh(W_1·h_i + W_2·s_t)` | W_1, W_2, v | O(d) per pair, handles different encoder/decoder dims |
| **Luong (dot)** | `h_i^T · s_t` | None | Fast, requires same dimensions for encoder/decoder |
| **Luong (general)** | `h_i^T · W · s_t` | W (d×d) | Learnable scaling between spaces |
| **Luong (concat)** | `v^T · tanh(W·[h_i; s_t])` | W, v | Closer to Bahdanau but with concatenation |
| **Self-attention (transformer)** | `Q·K^T / sqrt(d_k)` | W_Q, W_K, W_V per head | Operates within the same sequence (not cross-attention) |
| **Multi-head cross-attention** | Same QKV formula | Q from decoder, K/V from encoder | Used in encoder-decoder transformers for seq2seq |

### 4.2 Encoder-Decoder Attention vs Self-Attention

| Property | Cross-Attention (seq2seq) | Self-Attention (transformer) |
|----------|--------------------------|------------------------------|
| Q source | Decoder hidden state | Same sequence |
| K/V source | Encoder hidden states | Same sequence |
| Direction | Encoder → Decoder | Can be bidirectional (BERT) or causal (GPT) |
| Purpose | Align input to output | Contextualize within a sequence |

### 4.3 Decoding Strategies

| Strategy | Description | Pros | Cons |
|----------|-------------|------|------|
| **Greedy decoding** | Argmax at each step | Fast, deterministic | Low quality; misses globally better sequences |
| **Beam search** | Keep top-K hypotheses at each step | Better quality than greedy | Repetitive, generic outputs; expensive at large K |
| **Top-k sampling** | Sample from top-K most probable tokens | Diversity, creativity | Can produce incoherent text at high k |
| **Nucleus (top-p) sampling** | Sample from smallest set covering probability p | Adaptive diversity | Requires tuning p |
| **Temperature sampling** | Scale logits by 1/T before softmax | Simple diversity control | Higher T = more random |
| **Diverse beam search** | Group beams into G groups with diversity penalty | Multiple distinct outputs | More expensive than standard beam |
| **Length-penalized beam** | Add length penalty to beam scores | Avoids short-sequence bias | Requires tuning alpha |

### 4.4 Local vs Global Attention (Luong)

**Global attention:** At each decoder step, attend to all encoder positions (default). O(src_len) alignment per decoder step.

**Local attention:** At each decoder step, predict an alignment position p_t (using a small MLP), then attend only to a window [p_t - D, p_t + D]. O(D) alignment computation. Useful for very long source sequences where global attention is expensive and monotonic alignment holds (e.g., speech recognition).

---

## 5. Architecture Diagrams

### Bahdanau Attention Flow

```
Encoder (BiLSTM):
  Input: [x1, x2, x3, x4]
  States: [h1, h2, h3, h4]   (forward + backward concatenated)

Decoder at step t=1:
  State: s_0 (initialized from encoder final state)

  Alignment scores:
    e_{1,1} = v^T · tanh(W_1·h1 + W_2·s_0)
    e_{1,2} = v^T · tanh(W_1·h2 + W_2·s_0)
    e_{1,3} = v^T · tanh(W_1·h3 + W_2·s_0)
    e_{1,4} = v^T · tanh(W_1·h4 + W_2·s_0)

  Attention weights:
    [α_{1,1}, α_{1,2}, α_{1,3}, α_{1,4}] = softmax([e_{1,1}...e_{1,4}])

  Context vector:
    c_1 = α_{1,1}·h1 + α_{1,2}·h2 + α_{1,3}·h3 + α_{1,4}·h4

  Decoder output:
    s_1 = LSTM(s_0, [y_0; c_1])   (concat input embedding with context)
    y_1 = softmax(W_o · tanh(W_c · [s_1; c_1]))
```

### Encoder-Decoder Transformer (seq2seq)

```
Source: "The cat sat"               Target: "<bos> Le chat"
    |                                          |
[Encoder: N transformer blocks]         [Decoder: N transformer blocks]
  [Self-attention: bidirectional]         [Causal self-attention: left-to-right]
  [FFN]                                   [Cross-attention: Q=decoder, K/V=encoder]
    |                                     [FFN]
    v                                          |
Encoder outputs: [h_1, h_2, h_3]         Decoder outputs
                     |                         |
                     +----> Cross-attention <---+
                           (K, V from encoder;
                            Q from decoder)
                                |
                           Output logits
                                |
                           "assis" predicted
```

### Beam Search Tree (width=3, length=4)

```
Step 0:                 <bos>
                       / | \
Step 1:           "Le" "Un" "La"    (keep top-3)
                  / \    |    \
Step 2:     "chat" "chien"  "chat" "la"  ...  (expand and keep top-3 global)
              |       |        |
Step 3:     "est"   "est"    "est"
              |       |
Step 4:     "beau" "grand"

Final beams (sorted by score/length):
  "Le chat est beau"   score: -2.1 / length: 4 = -0.525
  "Le chien est grand" score: -2.8 / length: 4 = -0.700
  "La chat est belle"  score: -3.1 / length: 4 = -0.775
  -> Select: "Le chat est beau"
```

---

## 6. How It Works — Detailed Mechanics

### Bahdanau Attention from Scratch

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from typing import Tuple


class BahdanauAttention(nn.Module):
    """
    Bahdanau (additive) attention.
    Energy: e_{ti} = v^T · tanh(W1·h_i + W2·s_t)
    Context: c_t = sum_i(alpha_{ti} * h_i)
    """
    def __init__(self, encoder_dim: int, decoder_dim: int, attention_dim: int) -> None:
        super().__init__()
        self.W1 = nn.Linear(encoder_dim, attention_dim, bias=False)  # encoder projection
        self.W2 = nn.Linear(decoder_dim, attention_dim, bias=False)  # decoder projection
        self.v = nn.Linear(attention_dim, 1, bias=False)             # energy projection

    def forward(
        self,
        encoder_outputs: torch.Tensor,  # (batch, src_len, encoder_dim)
        decoder_state: torch.Tensor,    # (batch, decoder_dim)
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        """
        Returns context vector (batch, encoder_dim) and attention weights (batch, src_len).
        """
        # Project encoder outputs: (batch, src_len, attention_dim)
        enc_proj = self.W1(encoder_outputs)

        # Project decoder state: (batch, 1, attention_dim) for broadcasting
        dec_proj = self.W2(decoder_state).unsqueeze(1)

        # Compute energy scores: (batch, src_len, 1) -> (batch, src_len)
        energy = self.v(torch.tanh(enc_proj + dec_proj)).squeeze(-1)

        # Softmax to get attention weights
        attention_weights = F.softmax(energy, dim=-1)  # (batch, src_len)

        # Compute context vector: (batch, encoder_dim)
        context = torch.bmm(
            attention_weights.unsqueeze(1),   # (batch, 1, src_len)
            encoder_outputs                   # (batch, src_len, encoder_dim)
        ).squeeze(1)                          # (batch, encoder_dim)

        return context, attention_weights
```

### Luong Attention Variants

```python
class LuongAttention(nn.Module):
    """
    Luong attention with three score variants.
    Unlike Bahdanau, Luong computes energy AFTER decoder step (not before).
    """
    def __init__(
        self,
        hidden_dim: int,
        method: str = "general",  # "dot" | "general" | "concat"
    ) -> None:
        super().__init__()
        self.method = method
        if method == "general":
            self.W = nn.Linear(hidden_dim, hidden_dim, bias=False)
        elif method == "concat":
            self.W = nn.Linear(hidden_dim * 2, hidden_dim, bias=False)
            self.v = nn.Linear(hidden_dim, 1, bias=False)

    def score(
        self,
        decoder_state: torch.Tensor,     # (batch, hidden_dim)
        encoder_outputs: torch.Tensor,   # (batch, src_len, hidden_dim)
    ) -> torch.Tensor:
        """Returns energy scores (batch, src_len)."""
        if self.method == "dot":
            # Requires encoder_dim == decoder_dim
            return torch.bmm(
                encoder_outputs,                    # (batch, src_len, hidden)
                decoder_state.unsqueeze(-1)         # (batch, hidden, 1)
            ).squeeze(-1)                           # (batch, src_len)

        elif self.method == "general":
            # Learnable linear transformation of decoder state
            dec_transformed = self.W(decoder_state)  # (batch, hidden)
            return torch.bmm(
                encoder_outputs,
                dec_transformed.unsqueeze(-1)
            ).squeeze(-1)

        elif self.method == "concat":
            # Concatenate and project (closest to Bahdanau)
            dec_expanded = decoder_state.unsqueeze(1).expand_as(encoder_outputs)
            combined = torch.cat([encoder_outputs, dec_expanded], dim=-1)
            return self.v(torch.tanh(self.W(combined))).squeeze(-1)
        else:
            raise ValueError(f"Unknown method: {self.method}")

    def forward(
        self,
        decoder_state: torch.Tensor,
        encoder_outputs: torch.Tensor,
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        scores = self.score(decoder_state, encoder_outputs)
        weights = F.softmax(scores, dim=-1)
        context = torch.bmm(weights.unsqueeze(1), encoder_outputs).squeeze(1)
        return context, weights
```

### Beam Search with Length Penalty

```python
import heapq
from dataclasses import dataclass, field
from typing import List, Tuple


@dataclass(order=True)
class Hypothesis:
    """Beam search hypothesis with normalized score."""
    neg_score: float          # negative log-prob (for min-heap)
    tokens: List[int] = field(default_factory=list, compare=False)

    @property
    def score(self) -> float:
        return -self.neg_score


def length_penalized_score(
    log_prob: float,
    length: int,
    alpha: float = 0.6,
) -> float:
    """
    Wu et al. (2016) length penalty: lp = ((5 + len) / 6)^alpha
    alpha=0.6 is the Google NMT default.
    alpha=0: no penalty (favors short sequences)
    alpha=1: strong penalty (strongly favors longer sequences)
    """
    lp = ((5 + length) / 6) ** alpha
    return log_prob / lp


def beam_search(
    initial_log_probs: torch.Tensor,   # (vocab_size,) — log probs for first token
    step_fn,                           # callable(token_ids, state) -> (log_probs, state)
    beam_width: int = 5,
    max_length: int = 50,
    eos_token_id: int = 2,
    bos_token_id: int = 1,
    alpha: float = 0.6,
) -> List[Tuple[List[int], float]]:
    """
    Beam search decoding.
    Returns list of (token_ids, score) sorted by penalized score descending.
    """
    # Initialize with BOS token
    beams: List[Hypothesis] = [Hypothesis(neg_score=0.0, tokens=[bos_token_id])]
    completed: List[Hypothesis] = []

    for step in range(max_length):
        candidates: List[Hypothesis] = []

        for hyp in beams:
            if hyp.tokens[-1] == eos_token_id:
                completed.append(hyp)
                continue

            # Get log probs for next token (model-specific)
            log_probs, _ = step_fn(hyp.tokens)  # (vocab_size,)

            # Expand top beam_width tokens
            top_k_log_probs, top_k_ids = log_probs.topk(beam_width)

            for log_prob, token_id in zip(top_k_log_probs.tolist(), top_k_ids.tolist()):
                new_tokens = hyp.tokens + [token_id]
                new_log_prob = hyp.score + log_prob
                candidates.append(Hypothesis(
                    neg_score=-new_log_prob,
                    tokens=new_tokens,
                ))

        # Keep top beam_width candidates by raw log-prob
        candidates.sort()
        beams = candidates[:beam_width]

        if len(beams) == 0:
            break

    # Add remaining beams to completed
    completed.extend(beams)

    # Sort by length-penalized score
    results = [
        (hyp.tokens[1:], length_penalized_score(hyp.score, len(hyp.tokens) - 1, alpha))
        for hyp in completed
    ]
    results.sort(key=lambda x: x[1], reverse=True)
    return results


def nucleus_sampling(
    log_probs: torch.Tensor,   # (vocab_size,)
    p: float = 0.9,
    temperature: float = 1.0,
) -> int:
    """
    Nucleus (top-p) sampling (Holtzman et al., 2020).
    Sample from the smallest set of tokens whose cumulative probability >= p.
    """
    probs = torch.exp(log_probs / temperature)
    sorted_probs, sorted_indices = probs.sort(descending=True)
    cumulative_probs = sorted_probs.cumsum(dim=-1)

    # Remove tokens once cumulative probability exceeds p
    sorted_indices_to_remove = cumulative_probs > p
    # Shift right: keep the first token that exceeds p
    sorted_indices_to_remove[..., 1:] = sorted_indices_to_remove[..., :-1].clone()
    sorted_indices_to_remove[..., 0] = 0

    # Zero out removed tokens
    sorted_probs[sorted_indices_to_remove] = 0.0
    sorted_probs /= sorted_probs.sum()  # renormalize

    # Sample from the filtered distribution
    sampled_idx = torch.multinomial(sorted_probs, num_samples=1)
    return sorted_indices[sampled_idx].item()
```

### Encoder-Decoder Transformer for Translation

```python
import torch
import torch.nn as nn
from typing import Optional


class TranslationTransformer(nn.Module):
    """
    Full encoder-decoder transformer for sequence-to-sequence tasks.
    Uses PyTorch's built-in Transformer which implements the architecture from
    "Attention Is All You Need" (Vaswani et al., 2017).
    """
    def __init__(
        self,
        src_vocab_size: int,
        tgt_vocab_size: int,
        d_model: int = 512,
        nhead: int = 8,
        num_encoder_layers: int = 6,
        num_decoder_layers: int = 6,
        dim_feedforward: int = 2048,
        dropout: float = 0.1,
        max_seq_len: int = 512,
        pad_idx: int = 0,
    ) -> None:
        super().__init__()
        self.d_model = d_model
        self.pad_idx = pad_idx

        # Shared embedding if vocab sizes are identical (common in translation)
        self.src_embed = nn.Embedding(src_vocab_size, d_model, padding_idx=pad_idx)
        self.tgt_embed = nn.Embedding(tgt_vocab_size, d_model, padding_idx=pad_idx)

        # Sinusoidal positional encoding
        pe = self._build_sinusoidal_pe(max_seq_len, d_model)
        self.register_buffer("pe", pe)

        self.transformer = nn.Transformer(
            d_model=d_model,
            nhead=nhead,
            num_encoder_layers=num_encoder_layers,
            num_decoder_layers=num_decoder_layers,
            dim_feedforward=dim_feedforward,
            dropout=dropout,
            batch_first=True,
        )
        self.output_proj = nn.Linear(d_model, tgt_vocab_size)

        # Initialize weights
        nn.init.xavier_uniform_(self.src_embed.weight)
        nn.init.xavier_uniform_(self.tgt_embed.weight)
        nn.init.xavier_uniform_(self.output_proj.weight)

    def _build_sinusoidal_pe(self, max_len: int, d_model: int) -> torch.Tensor:
        """Build sinusoidal positional encodings: PE(pos,2i)=sin(pos/10000^(2i/d))."""
        position = torch.arange(max_len).unsqueeze(1).float()
        div_term = torch.exp(
            torch.arange(0, d_model, 2).float() * (-torch.log(torch.tensor(10000.0)) / d_model)
        )
        pe = torch.zeros(1, max_len, d_model)
        pe[0, :, 0::2] = torch.sin(position * div_term)
        pe[0, :, 1::2] = torch.cos(position * div_term)
        return pe

    def encode(
        self,
        src: torch.Tensor,               # (batch, src_len)
        src_key_padding_mask: Optional[torch.Tensor] = None,  # (batch, src_len)
    ) -> torch.Tensor:
        """Encode source sequence to memory."""
        src_emb = self.src_embed(src) * (self.d_model ** 0.5)  # scale by sqrt(d_model)
        src_emb = src_emb + self.pe[:, :src.size(1), :]
        return self.transformer.encoder(
            src_emb, src_key_padding_mask=src_key_padding_mask
        )

    def decode(
        self,
        tgt: torch.Tensor,               # (batch, tgt_len)
        memory: torch.Tensor,            # (batch, src_len, d_model)
        tgt_mask: Optional[torch.Tensor] = None,              # (tgt_len, tgt_len) causal mask
        memory_key_padding_mask: Optional[torch.Tensor] = None,
    ) -> torch.Tensor:
        """Decode target sequence given encoder memory."""
        tgt_emb = self.tgt_embed(tgt) * (self.d_model ** 0.5)
        tgt_emb = tgt_emb + self.pe[:, :tgt.size(1), :]
        output = self.transformer.decoder(
            tgt_emb, memory,
            tgt_mask=tgt_mask,
            memory_key_padding_mask=memory_key_padding_mask,
        )
        return self.output_proj(output)  # (batch, tgt_len, tgt_vocab_size)

    def forward(
        self,
        src: torch.Tensor,
        tgt: torch.Tensor,
        src_key_padding_mask: Optional[torch.Tensor] = None,
    ) -> torch.Tensor:
        """Full forward pass with causal mask on target."""
        tgt_mask = nn.Transformer.generate_square_subsequent_mask(
            tgt.size(1), device=tgt.device
        )
        memory = self.encode(src, src_key_padding_mask)
        return self.decode(tgt, memory, tgt_mask, src_key_padding_mask)
```

---

## 7. Real-World Examples

**Google Neural Machine Translation (GNMT, 2016):** Eight-layer encoder-decoder LSTM with attention. Bahdanau attention with a projection W_a. Beam width 8, length penalty alpha=0.6. This system reduced translation errors by 60% compared to phrase-based MT. The coverage mechanism (penalizing attention distributions that focus repeatedly on the same source tokens) prevented repetition.

**Facebook FAIR WMT competition:** The original "Attention Is All You Need" transformer (6 layers, 8 heads, d_model=512) set the WMT14 En-De BLEU record at 28.4 in 2017. The same architecture with beam width 4 and alpha=0.6 achieved results that previously required ensembles of 8 LSTM models.

**Speech recognition (Listen, Attend and Spell, 2015):** First application of attention to speech. Pyramidal BiLSTM encoder (reduces sequence length by 4x at each layer) + attention decoder. Beam search width 32 was needed because the output space is phoneme sequences, sparser than word translation. Local attention (window ±10 frames) outperformed global attention for alignment with the monotonic left-to-right nature of speech.

**Document summarization (BART, 2019):** Encoder-decoder transformer fine-tuned for summarization. Beam width 4, length penalty 2.0 (stronger than translation to prevent copying), no-repeat-ngram-size=3 (prevents repeating any 3-gram). The strong length penalty forces generating concise summaries rather than extracting long verbatim spans.

---

## 8. Tradeoffs

| Decoding Strategy | Quality | Diversity | Speed | Best For |
|------------------|---------|-----------|-------|---------|
| Greedy | Low | None | Fast (1x) | Latency-critical, acceptable quality |
| Beam search (k=4) | Good | Low | 4x slower | Translation, summarization |
| Beam search (k=20) | Marginal gain | Very low | 20x slower | Research, reranking candidates |
| Top-k (k=50) | Good | Medium | 1x | Story generation, code |
| Nucleus (p=0.9) | Good | High | 1x | Creative tasks, diverse outputs |
| Temperature (T=0.7) | Good | Medium | 1x | General purpose generation |

| Additive vs Multiplicative Attention | |
|--------------------------------------|--|
| Additive (Bahdanau) | Handles different encoder/decoder dims; 3 learned projections; better on small datasets |
| Multiplicative (Luong dot) | No learned parameters; faster; requires same dims; less stable at high d_model without sqrt(d_k) scaling |
| Multiplicative (general) | One learned matrix; compromise between dot and additive |

| Teacher Forcing vs Scheduled Sampling | |
|---------------------------------------|--|
| Teacher forcing | Fast convergence; exposure bias at inference (distribution shift) |
| Scheduled sampling | Reduces bias; slower convergence; probability p of using model output increases over training |

---

## 9. When to Use / When NOT to Use

### Use Bahdanau/Luong attention (RNN-based encoder-decoder) when:

- Short to medium sequences (< 200 tokens), especially when exact alignment is needed
- Computational budget is very limited (CPU-only, edge deployment)
- Task has strong monotonic alignment (speech recognition, character-level translation)
- Training data is small (<100K examples) and transformer would overfit

### Use encoder-decoder transformer when:

- Translation, summarization, or any seq2seq task with large data (>1M examples)
- Task benefits from bidirectional encoding (full source context for each step)
- Quality is more important than latency

### Use beam search when:

- Output quality and fluency are critical (translation, caption generation)
- You need multiple diverse hypotheses for downstream reranking
- Temperature sampling would produce incoherent text (structured outputs, code)

### Use nucleus/temperature sampling when:

- Diversity or creativity is required (story generation, brainstorming)
- Factual accuracy matters less than variety
- Generating multiple unique candidates for human selection

### Do NOT use large beam widths when:

- Serving at >100 RPS — beam width 20 is 5x slower than beam width 4 with marginal quality gain
- Generating long sequences (>200 tokens) — beam search on long outputs produces generic, repetitive text (the "dull" output problem)
- Task benefits from diversity (use sampling instead)

---

## 10. Common Pitfalls

### Pitfall 1: Beam search repetition without n-gram blocking

```python
# BROKEN: Standard beam search for summarization
# Output: "The company announced the product. The company announced the product."

# FIXED: no-repeat-ngram-size=3 in HuggingFace generation
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

model = AutoModelForSeq2SeqLM.from_pretrained("facebook/bart-large-cnn")
tokenizer = AutoTokenizer.from_pretrained("facebook/bart-large-cnn")

inputs = tokenizer(long_article, return_tensors="pt", truncation=True, max_length=1024)
summary_ids = model.generate(
    **inputs,
    num_beams=4,
    length_penalty=2.0,
    no_repeat_ngram_size=3,    # Block any 3-gram that has appeared before
    min_length=56,
    max_length=142,
)
```

### Pitfall 2: Exposure bias causing compounding errors

```python
# During training (teacher forcing):
# Input to decoder step t: [ground truth y_{t-1}]  <- always correct
# At inference:
# Input to decoder step t: [model's prediction y_{t-1}]  <- may be wrong

# If y_{t-1} prediction is wrong, subsequent tokens are generated from
# a distribution the model was never trained on -> error compounds

# PARTIAL FIX: Scheduled sampling
import random

def scheduled_sampling_decode(model, encoder_output, tgt, epsilon: float = 0.3):
    """
    With probability epsilon, use model's own previous prediction instead of ground truth.
    Increase epsilon gradually during training (0.0 -> 0.5 over first 100K steps).
    """
    decoder_input = tgt[:, :1]  # start with BOS token
    outputs = []
    for t in range(1, tgt.size(1)):
        logits = model.decode(decoder_input, encoder_output)  # (batch, t, vocab)
        last_logit = logits[:, -1, :]
        predicted = last_logit.argmax(dim=-1, keepdim=True)   # (batch, 1)

        if random.random() < epsilon:
            # Use model's prediction
            next_input = predicted
        else:
            # Use ground truth
            next_input = tgt[:, t:t+1]

        decoder_input = torch.cat([decoder_input, next_input], dim=1)
        outputs.append(logits[:, -1, :])

    return torch.stack(outputs, dim=1)
```

### Pitfall 3: Padding mask not applied in cross-attention

```python
# BROKEN: Cross-attention attends to padding tokens in encoder output
# Padding positions carry zero embedding but still receive non-zero attention weights
# This dilutes the context vector and degrades performance on variable-length inputs

model.forward(src, tgt)  # missing src_key_padding_mask

# FIXED: Always pass source padding mask to encoder and decoder
src_key_padding_mask = (src == pad_idx)   # True where padding
model.forward(src, tgt, src_key_padding_mask=src_key_padding_mask)

# PyTorch convention: True = masked (ignored), False = attend
# HuggingFace convention: 1 = attend, 0 = masked (reversed!)
# Always check which convention your framework uses.
```

### Pitfall 4: Length penalty misconfiguration for tasks

```python
# alpha=0.6 works for translation but is wrong for:
# - Summarization: needs strong penalty (alpha=2.0) to avoid verbatim copying
# - QA answer extraction: needs no penalty (alpha=0.0) since answers are short
# - Creative writing: needs negative penalty to reward longer outputs

# Tuning guidance:
ALPHA_MAP = {
    "translation": 0.6,       # Wu et al. 2016 default
    "summarization": 1.5,     # Force concise summaries
    "qa_extraction": 0.0,     # Short answers preferred
    "story_generation": -0.2, # Reward longer outputs
}
```

### Pitfall 5: Attention collapse in very long sequences

```python
# Symptom: attention weights nearly uniform across all positions
# (attention_weights.entropy() close to log(src_len))
# The model cannot focus — equivalent to using no attention

# Root cause: QK dot products become very large for high d_model without sqrt(d_k) scaling
# A 512-dimensional dot product has variance ~512x a 1-dimensional product
# softmax of large values approaches uniform (zero gradient region)

# Check: ensure d_k scaling is applied
def scaled_dot_product_attention(Q, K, V, d_k: int):
    scores = torch.bmm(Q, K.transpose(1, 2)) / (d_k ** 0.5)   # critical!
    weights = F.softmax(scores, dim=-1)
    return torch.bmm(weights, V), weights
```

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `torch.nn.Transformer` | Built-in encoder-decoder transformer | `batch_first=True` required in modern PyTorch |
| `transformers` (HuggingFace) | Pretrained seq2seq models (BART, T5, mBART, OPUS-MT) | `model.generate()` supports beam, nucleus, top-k |
| `sacrebleu` | BLEU score computation | Standard benchmark evaluation; use `corpus_bleu` not `sentence_bleu` |
| `fairseq` (Meta) | Efficient seq2seq training | Used for WMT competition models, fast beam search |
| `OpenNMT-py` | Reference NMT implementation | Academic research, reproducible NMT baselines |
| `BerViz` | Attention visualization | Visualize encoder-decoder cross-attention patterns |
| `ctranslate2` | Fast inference for seq2seq | 2-4x speedup over HuggingFace inference |

---

## 12. Interview Questions with Answers

**Q: What is the attention bottleneck problem and how does attention solve it?**
Without attention, an encoder-decoder RNN compresses the entire source sequence into a single fixed-size vector (the last encoder hidden state). For a 50-word sentence mapped to a 512-dimensional vector, all positional information must coexist in that vector. Performance degrades significantly for sequences >15-20 tokens. Attention solves this by producing one encoder hidden state per input position — a 50-word sentence produces 50 vectors of 512 dimensions. The decoder at each step computes a weighted sum of these 50 vectors (the context vector), dynamically "selecting" the most relevant positions. The bottleneck is eliminated: information from any position can be directly accessed in O(1) steps.

**Q: Explain the difference between Bahdanau and Luong attention mathematically.**
Bahdanau uses an additive energy function: `e_{ti} = v^T tanh(W_1 h_i + W_2 s_t)`, where `W_1`, `W_2`, and `v` are learned parameters. The tanh introduces nonlinearity, allowing the model to capture complex interactions between encoder states and decoder states. It can handle different encoder and decoder dimensions via the projection matrices. Luong computes the energy after the decoder step (versus before in Bahdanau) and offers three variants. The dot product variant: `e_{ti} = s_t^T h_i` has no learned parameters and requires encoder and decoder to have the same dimension. The general variant: `e_{ti} = s_t^T W h_i` adds a learnable matrix. Practically, Luong general is slightly faster (fewer parameters, no tanh) while Bahdanau is more expressive and works well when encoder/decoder dimensions differ.

**Q: Why do we divide by sqrt(d_k) in transformer attention?**
The dot product `Q · K^T` between two vectors of dimension d_k has variance approximately equal to d_k when the vectors have unit variance. For d_k=512, the dot products have standard deviation ~22 — large enough that the softmax becomes nearly one-hot (all weight on the maximum element), causing vanishing gradients for non-maximum keys. Dividing by sqrt(d_k) rescales the variance to 1 regardless of dimension, keeping softmax in a regime with meaningful gradients. At d_k=64 (standard for multi-head with d_model=512, 8 heads), unscaled variance would be 8, which already causes significant gradient issues during training.

**Q: What is beam search and when does it fail?**
Beam search maintains K candidate sequences (beams) at each decoding step, expanding each by one token and keeping the top K by cumulative log-probability. Unlike greedy decoding (K=1), it finds better global sequences by not committing prematurely to locally highest-probability choices. Beam search fails in three scenarios: (1) repetition — beam search tends to produce generic, repetitive output because common n-grams have high probability and are reinforced across beams; (2) length bias — beams are scored by log-probability which decreases with length, so short sequences are unfairly favored without length penalty; (3) diversity collapse — all beams converge to similar sequences since they share the same scoring objective. For creative text generation, nucleus sampling outperforms beam search because diversity is desired. For structured outputs (translation, code), beam search with n-gram blocking is typically preferred.

**Q: What is the coverage mechanism in neural machine translation and why was it needed?**
In early attention-based NMT, the attention mechanism could repeatedly focus on the same source tokens (over-translation, e.g., "cat" translated three times) or skip source tokens entirely (under-translation, e.g., a word is never translated). The coverage mechanism adds a coverage vector `C_t = Σ_{t'<t} α_{t'}` tracking cumulative attention over source positions. A penalty term `Σ_i min(α_{t,i}, C_{t,i})` in the training objective penalizes attending to positions that have already received significant attention. This forces the model to "cover" all source positions and not revisit already-translated tokens. Coverage reduced Google NMT translation errors by ~20% specifically on long sentences.

**Q: How does teacher forcing cause exposure bias and what strategies mitigate it?**
Teacher forcing provides the ground-truth previous token as decoder input at each training step: `P(y_t | y_1^*, ..., y_{t-1}^*, x)`. At inference, the model instead conditions on its own previously generated tokens: `P(y_t | y_1, ..., y_{t-1}, x)`. If the model generates an incorrect token at step t-1, subsequent predictions are conditioned on an input the model has never seen during training, causing compounding errors. Three mitigation strategies: (1) scheduled sampling — with increasing probability during training, use the model's own prediction instead of ground truth; (2) minimum risk training — optimize for expected BLEU directly using Monte Carlo sampling, training on the actual inference distribution; (3) contrastive decoding — train a small negative model and use log(P_pos) - log(P_neg) as the generation objective, naturally penalizing the common errors of the base model.

**Q: Compare nucleus sampling vs beam search for code generation.**
Beam search favors high-probability sequences — for code, this often means generating boilerplate that is syntactically correct but functionally wrong (the model is confident about common patterns). Nucleus sampling introduces stochasticity, which can generate novel code structures but also syntactic errors. In practice for code generation: beam search with a small width (4-8) produces more consistent, idiomatic code; nucleus sampling with p=0.95 is useful when generating multiple diverse candidates for a pass@k evaluation. GitHub Copilot uses temperature sampling (T≈0.8) rather than beam search because latency is critical (beam width 5 is 5x slower) and they evaluate a sample of outputs rather than a single best.

**Q: What is label smoothing in sequence generation and why does it help?**
Label smoothing replaces the one-hot target distribution (1 for correct token, 0 for others) with a soft distribution: ε/(V-1) for all wrong tokens, 1-ε for the correct token. ε=0.1 is standard. This prevents the model from becoming overconfident on training data — an overconfident model produces poorly calibrated probabilities and overfits to noise in the training labels. For sequence generation, label smoothing reduces cross-entropy loss by ~0.1 and improves BLEU by 1-2 points on WMT translation benchmarks. The downside: perplexity (evaluated against one-hot targets) appears higher even when actual generation quality improves — so always evaluate BLEU rather than perplexity when label smoothing is used.

**Q: Why does beam search width have diminishing returns past k=4-8?**
The theoretical bound on beam search is exponential improvement with width — but in practice, quality plateaus quickly. Studies on WMT translation show: k=1 (greedy) BLEU ~26; k=4 BLEU ~28; k=8 BLEU ~28.2; k=64 BLEU ~28.3. Beyond k=4-8, the additional hypotheses explored are near-duplicates of already-covered paths (they differ in low-probability branches that the model correctly avoided). The marginal gain of increasing k from 4 to 64 is 0.3 BLEU at 16x the inference cost. For production: k=4 or k=5 is the standard practical choice, balancing quality and latency.

**Q: Explain the difference between self-attention and cross-attention in an encoder-decoder transformer.**
In self-attention (both in the encoder and in the decoder's first sublayer), queries, keys, and values all come from the same sequence: `Q = K = V = X · W_{Q/K/V}`. Each token attends to all other tokens in the same sequence to build contextual representations. In cross-attention (the decoder's second sublayer), queries come from the decoder's current representations and keys/values come from the encoder's output: `Q = decoder_state · W_Q`, `K = encoder_output · W_K`, `V = encoder_output · W_V`. This is the attention mechanism that performs alignment — each decoder position queries the encoder memory to extract relevant source information. The causal mask in the decoder's self-attention prevents positions from attending to future positions; the cross-attention has no causal constraint because the full encoder output is always available.

**Q: How does a coverage penalty prevent repetition in beam search for summarization?**
Repetition in beam search occurs because high-frequency n-grams dominate beam scores. A length-n phrase repeated k times contributes the same log-probability as k independent occurrences, but is far less informative. The `no_repeat_ngram_size=n` heuristic (HuggingFace) sets the log-probability of any token that would create a repeated n-gram to -infinity, effectively blocking repetition. The coverage penalty (used in GNMT) is softer: it penalizes the total score proportionally to `Σ_i log(min(Σ_{t} α_{t,i}, 1))`, encouraging each source token to receive attention exactly once. The n-gram blocking is more aggressive and simpler; the coverage penalty is differentiable and can be incorporated into training. For extractive summarization (PEGASUS), n-gram blocking (n=3) is standard.

**Q: How do you debug a poor attention alignment in an encoder-decoder model?**
Step 1: Visualize attention weights as a heatmap (src_len x tgt_len). Expected pattern for translation: near-diagonal alignment (the i-th target word should attend most to source words around position i, accounting for reordering). Step 2: Check for attention collapse — if all rows are nearly uniform (high entropy), the model is not learning to focus. Common causes: learning rate too high in early training, attention dimension too large without scaling, insufficient training data. Step 3: Check for attention concentration — if all rows concentrate on one or two tokens (usually period or BOS), the model is using those as "garbage collection" tokens. This indicates the encoder representations are poor. Step 4: Add coverage penalty to the training objective to force distributed attention. Step 5: Check that the padding mask is correctly applied — if padding tokens are attended to, they pollute the context vector.

**Q: What is diverse beam search and when would you use it?**
Standard beam search finds multiple high-probability sequences, but they tend to be very similar (differing in the last 1-2 words). Vijayakumar et al. (2018) proposed Diverse Beam Search: divide the K beams into G groups of size K/G, then add a diversity penalty that discourages beams in different groups from attending to the same tokens. Within each group, standard beam search applies; between groups, a similarity penalty is added to the score. Use case: generating multiple distinct descriptions for an image (G=5 groups might yield "a dog playing ball," "a golden retriever in a park," "an energetic dog outdoors," etc.). Also useful in code generation when you want to evaluate K syntactically different solutions rather than K near-identical ones.

**Q: What is the role of the sinusoidal positional encoding in seq2seq transformers and how does it enable extrapolation?**
Sinusoidal positional encoding adds a fixed encoding PE(pos, 2i) = sin(pos/10000^(2i/d)) and PE(pos, 2i+1) = cos(pos/10000^(2i/d)) to each position's embedding. The key property: PE(pos+k) can be expressed as a linear function of PE(pos) — specifically, a 2D rotation matrix with angle k/10000^(2i/d). This means relative position differences are representable as linear transformations, making it easier for the attention mechanism to learn position-relative patterns. Unlike learned absolute encodings, sinusoidal encodings can be computed for any position at inference, enabling limited length extrapolation. However, extrapolation beyond training length still degrades significantly because the model learns attention patterns for the positions seen during training. Modern solutions (RoPE, ALiBi) provide much better extrapolation.

---

## 13. Best Practices

1. Always apply length penalty (alpha=0.6-1.0) in beam search for translation; without it, beam search pathologically favors short sequences that terminate early.
2. Use `no_repeat_ngram_size=3` for abstractive summarization to prevent copying artifacts; disable it for extractive QA where repeating key phrases is expected.
3. Monitor attention entropy during training — if per-position entropy exceeds log(src_len/2), the attention is not focusing; lower the learning rate or add attention regularization.
4. Apply the padding mask in both encoder self-attention and decoder cross-attention — missing either causes attention to "waste weight" on padding tokens.
5. For production seq2seq inference, pre-compute and cache encoder outputs; decoder autoregressive steps depend on encoder memory which is fixed per input.
6. Use label smoothing (ε=0.1) in seq2seq training — the BLEU improvement (1-2 points) comes at zero inference cost.
7. Calibrate beam width to your latency budget: k=4 gives 95% of maximum beam quality at 4x greedy cost; k>8 is rarely worth the added latency in production.
8. When switching from RNN-based attention to transformer attention, verify that the `sqrt(d_k)` scaling is applied — it is easy to accidentally omit and causes silent performance degradation.
9. For multilingual translation, use language token prefixes (e.g., `<2de>` for German) in the decoder; without explicit target language conditioning, mBART-style models hallucinate wrong languages.
10. Profile memory usage: cross-attention materializes an (encoder_len × decoder_len) matrix per head per layer — for long source documents (2048 tokens) and 6 decoder layers, this becomes the dominant memory cost.

---

## 14. Case Study

### Problem: Multilingual Document Summarization

**Context:** A news agency summarizes articles in 12 languages into English for global editorial distribution. Articles average 800 tokens; target summaries average 80 tokens (10:1 compression ratio). 2M parallel article-summary pairs in English; 50K-200K pairs per language.

**Architecture:** mBART-large (12-layer encoder-decoder transformer, 680M params, pretrained on 25 languages). Cross-attention (6 decoder layers each attending to 800 encoder positions) is the memory bottleneck.

**Training configuration:**

```python
# HuggingFace Trainer config
training_args = {
    "per_device_train_batch_size": 4,
    "gradient_accumulation_steps": 8,        # effective batch = 32
    "learning_rate": 3e-5,
    "warmup_steps": 500,
    "max_steps": 100_000,
    "fp16": True,
    "label_smoothing_factor": 0.1,
    "predict_with_generate": True,
    "generation_max_length": 128,
    "generation_num_beams": 4,
}

# Generation config
gen_config = {
    "num_beams": 4,
    "length_penalty": 1.5,
    "no_repeat_ngram_size": 3,
    "min_length": 30,
    "max_length": 128,
    "forced_bos_token_id": tokenizer.lang_code_to_id["en_XX"],
}
```

**Key findings:**

- Beam width 4 vs 8: +0.3 ROUGE-L at 2x latency cost. Deployed with k=4.
- Length penalty 1.5 vs 0.6: summaries at lp=0.6 were 40% shorter than gold; lp=1.5 matched gold length distribution.
- Coverage attention monitoring: 3% of Arabic articles produced near-uniform attention (entropy >6.0) indicating alignment failure. Root cause: Arabic RTL encoding required explicit language token. Fixed by prepending `<ar_AR>` to source.
- Cross-attention memory: 800 (source) × 128 (max target) × 6 layers × 16 heads × 4 bytes = 590MB per sequence in FP32. Using FP16 reduced to 295MB, enabling batch size 4 on 40GB A100.
- Production throughput: 45 articles/second (800-token source, 80-token target) on 4x A100 GPUs. Target: 50/second. Optimization: ctranslate2 FP16 inference achieved 67 articles/second.
