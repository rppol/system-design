# Positional Encoding — Deep Dive

> This file is a deep-dive sub-file of the [Foundations & Architecture](README.md) module.
> It covers positional encoding mathematical derivations, RoPE, ALiBi, YaRN, NTK-aware scaling,
> and context extension techniques.
> The parent README covers these at a survey level; this file provides full derivations.

---

## 1. Concept Overview

Transformers are permutation-equivariant by design: without additional information, `Attention(Q, K, V)` produces the same output regardless of token order. Feeding "cat sat the on mat" produces identical attention patterns as "the cat sat on the mat" — a fundamental problem for language understanding.

Positional encoding injects order information. The challenge is doing this in a way that:
1. Generalizes to sequences longer than training (extrapolation)
2. Preserves the ability to capture relative distances between tokens
3. Is computationally cheap and compatible with efficient attention kernels
4. Allows extension of trained models to longer contexts without full retraining

Three generations of solutions: Sinusoidal APE (2017, cannot extrapolate), RoPE (2021, graceful extrapolation via rotation), and context extension methods (2023, extending trained RoPE models to longer contexts).

---

## 2. Intuition

One-line analogy: Positional encoding is like adding timestamps to messages in a conversation — without them, you cannot tell if a response came before or after a question.

Mental model: A transformer without positional encoding is a "bag of tokens" model — it knows what words are present but not where. Adding positional encoding is like writing the page number in the margin of every page of a book. Sinusoidal encoding writes a fixed code per page. RoPE writes the code as a rotation of the content itself — so comparing two pages automatically reveals their distance (the rotation difference).

Why it matters: The transition from sinusoidal to RoPE (2021) and then the development of YaRN/LongRoPE (2023) directly enabled the scaling of context windows from 2K tokens (GPT-3) to 1M tokens (Gemini 1.5 Pro) without fundamental architecture changes.

Key insight: RoPE's key mathematical property — the dot product of two rotated position embeddings depends only on the relative position (m - n), not on the absolute positions m and n — makes it fundamentally more suitable for length generalization than sinusoidal APE, where the dot product depends on both m and n.

---

## 3. Core Principles

**Permutation equivariance:** `Attn(P·Q, P·K, P·V) = P·Attn(Q, K, V)` where P is any permutation matrix. This means any permutation of input tokens produces the same permuted output — order is completely ignored.

**Absolute vs relative positional encoding:** Absolute PE assigns each position a unique embedding added to or concatenated with the token embedding. It provides position information but the model must learn to extract relative distances from absolute positions. Relative PE directly encodes the relative distance between two positions — more natural for attention, which is inherently about pairwise relationships.

**Sinusoidal encoding extrapolation failure:** The original sinusoidal APE assigns fixed frequency-based codes to positions 0, 1, ..., N-1. At inference, positions > N-1 were never seen during training. The model's attention weights are not calibrated for these position codes — extrapolation degrades rapidly.

**RoPE motivation:** Instead of adding a positional code to the token embedding, RoPE encodes position as a rotation in the complex plane applied to query and key vectors. The key property: `(R_m · q) · (R_n · k) = q · (R_{m-n} · k)` — the dot product of rotated Q and K depends only on the relative position (m - n). This naturally encodes relative position within the attention score computation.

**Context extension tradeoff:** Extending a trained model's context requires the model to process positions it was not trained on. Position Interpolation (scaling positions to fit within training range) preserves relative position information but distorts the effective scale. NTK-aware scaling (changing the base frequency) better preserves high-frequency positional information.

---

## 4. Types / Architectures / Strategies

### 4.1 Positional Encoding Methods

| Method | Type | Extrapolation | Training | Used In |
|--------|------|---------------|----------|---------|
| Sinusoidal APE | Absolute, fixed | Poor (hard cutoff at train length) | None | Original Transformer, BERT |
| Learned APE | Absolute, learned | None (no generalization) | Yes | GPT-2, early GPT models |
| Shaw et al. (2018) | Relative | Good (within window) | Yes | Transformer-XL |
| **RoPE** | Relative (rotation) | Good with scaling | None (applied at inference) | LLaMA, Mistral, Qwen, GPT-NeoX |
| **ALiBi** | Relative (linear bias) | Good | None | MPT, BLOOM, Falcon-1 |
| **T5 relative bias** | Relative (learned buckets) | Limited | Yes | T5, FLAN-T5 |

### 4.2 Context Extension Methods

| Method | How | Quality | Requires Fine-tuning | Used In |
|--------|-----|---------|----------------------|---------|
| **Position Interpolation (PI)** | Scale position index by L/L' | Moderate degradation | Yes (~1000 steps) | CodeLLaMA 100K |
| **NTK-aware scaling** | Scale base θ by (L'/L)^(d/(d-2)) | Better than PI | Optional (often none) | Mistral/LLaMA community |
| **Dynamic NTK** | Scale NTK factor dynamically per-position | Better than static NTK | None | LLaMA-3.1 long context |
| **YaRN** | NTK + temperature + short-range integrity | Best quality | Yes (~400 steps) | Mistral-7B-v0.3, Qwen2.5 |
| **LongRoPE** | Non-uniform rescaling per frequency | State of the art | Yes | Phi-3-mini-128K |

---

## 5. Architecture Diagrams

### Sinusoidal APE: Frequency Spectrum

```
Position 0:   [sin(0/10000^0), cos(0/10000^0), sin(0/10000^(2/512)), ...]
Position 1:   [sin(1/10000^0), cos(1/10000^0), sin(1/10000^(2/512)), ...]
...
Position N:   [sin(N/10000^0), cos(N/10000^0), sin(N/10000^(2/512)), ...]

Frequency spectrum (d=512):
dim 0,1:  θ = 1/10000^0     = 1.0    (high frequency, period 2π ≈ 6)
dim 2,3:  θ = 1/10000^(2/512) ≈ 0.97  (slightly lower)
...
dim 510,511: θ = 1/10000^1  = 0.0001 (low frequency, period 62832)

Higher dimensions encode coarser position structure.
Lower dimensions encode fine-grained position structure.

Dot product PE_m · PE_n depends on |m-n| for nearby positions
but NOT for long distances -> extrapolation fails
```

### RoPE: Rotation in 2D Pairs

```
For a d-dimensional vector, group into d/2 pairs: (x_1, x_2), (x_3, x_4), ...

Each pair (x_{2i-1}, x_{2i}) is rotated by angle m * θ_i:

[x_{2i-1}']   [cos(m*θ_i)  -sin(m*θ_i)] [x_{2i-1}]
[x_{2i}  '] = [sin(m*θ_i)   cos(m*θ_i)] [x_{2i}  ]

where θ_i = 10000^(-2(i-1)/d)  for i = 1, ..., d/2

Frequency spectrum (matches sinusoidal but now as rotation angle):
i=1: θ_1 = 1.0      (fastest rotation per position)
i=d/4: θ = ~0.01    (medium)
i=d/2: θ = 0.0001   (slowest, period ~ 62K positions)

KEY PROPERTY:
(R_m · q)^T (R_n · k) = q^T R_{m-n} k

The inner product depends only on (m-n), not on m or n separately.
This is the "relative position" property that makes RoPE naturally
encode position differences in attention scores.
```

### Context Extension: Position Interpolation vs NTK

```
Training context: L = 4096 positions, trained with RoPE θ_i = 10000^(-2i/d)

Position Interpolation (scale factor s = 8 for 32K extension):
  New position m' = m / 8  (squeeze positions 0..32767 into 0..4095)
  Effective: all positions seen (0..4095), but relative spacing is 8x smaller
  Problem: high-frequency components (large θ_i) are compressed beyond resolution

NTK-aware scaling (base scaling):
  New θ_i = (10000 * s^(d/(d-2)))^(-2i/d)  where s = L'/L
  For s=8, d=128: multiply base by 8^(128/126) ≈ 8.13
  New base: 10000 * 8.13 = 81300
  Effect: low-frequency components extended (long-range positions preserved)
         high-frequency components less distorted than PI
```

### ALiBi Bias — Attention Score Penalty Grid

```
ALiBi attention bias matrix (head slope m = 0.25, seq_len = 6):
Added to raw attention scores QK^T/√d_k before softmax.

         T1       T2       T3       T4       T5       T6
T1:       0        -∞       -∞       -∞       -∞       -∞
T2:    -0.25       0        -∞       -∞       -∞       -∞
T3:    -0.50    -0.25       0        -∞       -∞       -∞
T4:    -0.75    -0.50    -0.25       0        -∞       -∞
T5:    -1.00    -0.75    -0.50    -0.25       0        -∞
T6:    -1.25    -1.00    -0.75    -0.50    -0.25       0

Penalty = m × (j − i).   Larger distance → larger negative score → less attention.

Compare to causal masking: causal mask is binary (attend or -∞).
ALiBi is graded: distant tokens are suppressed, not zeroed — the model
can still attend to them when truly necessary, but pays a mounting cost.
Each attention head uses a different slope m_h = 2^(−8h/H), so some heads
"zoom out" (small m, attend far) and others "zoom in" (large m, attend near).
```

### "Lost in the Middle" — U-Curve

```
Retrieval accuracy vs. position of relevant information in context
(Liu et al., 2023 — "Lost in the Middle"):

  100% ┤ ▓▓▓▓
   90% ┤ ▓▓▓▓▓▓                                                 ▓▓▓▓▓
   80% ┤ ▓▓▓▓▓▓▓▓                                           ▓▓▓▓▓▓▓▓
   70% ┤ ▓▓▓▓▓▓▓▓▓▓                                     ▓▓▓▓▓▓▓▓▓▓
   60% ┤ ▓▓▓▓▓▓▓▓▓▓▓▓                             ▓▓▓▓▓▓▓▓▓▓▓▓▓▓
   50% ┤ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
       └────────────────────────────────────────────────────────→
        start                    middle                    end
                           context position

Why the U-curve: causal attention + RoPE's positional bias favor recent tokens
(end) and heavily attended early tokens. Middle tokens have accumulated less
multi-layer "coverage." ALiBi's linear penalty exacerbates the dip.

Practical fix: place key facts at the very beginning or very end of context;
train with data that explicitly requires middle retrieval to flatten the curve.
```

---

## 6. How It Works — Detailed Mechanics

### RoPE Full Derivation

```python
import torch
import math
from typing import Tuple


def build_rope_freqs(
    d_head: int,
    max_seq_len: int,
    base: float = 10000.0,
    device: torch.device = torch.device("cpu"),
) -> Tuple[torch.Tensor, torch.Tensor]:
    """
    Build RoPE cosine and sine frequency tables.

    RoPE frequency for dimension pair i (i=0..d_head/2-1):
    θ_i = 1 / (base^(2i/d_head))    [Eq. 1 from Su et al. 2022]

    Rotation for position m, dimension pair i:
    angle_{m,i} = m * θ_i

    Applied to a vector at position m:
    RoPE(x, m)_{2i}   = x_{2i}   * cos(m*θ_i) - x_{2i+1} * sin(m*θ_i)
    RoPE(x, m)_{2i+1} = x_{2i+1} * cos(m*θ_i) + x_{2i}   * sin(m*θ_i)

    Returns cos_table: (max_seq_len, d_head/2)
            sin_table: (max_seq_len, d_head/2)
    """
    # θ_i = base^(-2i/d_head) for i = 0..d_head/2-1
    i = torch.arange(0, d_head, 2, device=device).float()   # [0, 2, 4, ..., d_head-2]
    freqs = 1.0 / (base ** (i / d_head))                     # (d_head/2,)

    # Position indices: 0, 1, ..., max_seq_len-1
    t = torch.arange(max_seq_len, device=device).float()     # (max_seq_len,)

    # Outer product: each position × each frequency
    freqs = torch.outer(t, freqs)                            # (max_seq_len, d_head/2)

    cos_table = freqs.cos()                                   # (max_seq_len, d_head/2)
    sin_table = freqs.sin()                                   # (max_seq_len, d_head/2)

    return cos_table, sin_table


def rotate_half(x: torch.Tensor) -> torch.Tensor:
    """
    Rearrange x for the rotate_half trick.
    Split x into first and second halves along last dim,
    return [-x2, x1] for efficient RoPE application.

    Instead of explicitly constructing rotation matrices,
    this computes rotation using element-wise ops:
    rotated = x * cos + rotate_half(x) * sin
    """
    x1 = x[..., : x.shape[-1] // 2]   # first half of d_head
    x2 = x[..., x.shape[-1] // 2 :]   # second half of d_head
    return torch.cat([-x2, x1], dim=-1)


def apply_rope(
    x: torch.Tensor,        # (batch, num_heads, seq_len, d_head)
    cos_table: torch.Tensor,  # (seq_len, d_head/2)
    sin_table: torch.Tensor,  # (seq_len, d_head/2)
    position_ids: torch.Tensor | None = None,  # (batch, seq_len) — for non-contiguous positions
) -> torch.Tensor:
    """
    Apply Rotary Positional Encoding to query or key tensor.

    The rotate_half trick avoids explicit 2D rotation matrices:
    For each pair (x_{2i}, x_{2i+1}) at position m:
      out_{2i}   = x_{2i}   * cos(m*θ_i) - x_{2i+1} * sin(m*θ_i)
      out_{2i+1} = x_{2i+1} * cos(m*θ_i) + x_{2i}   * sin(m*θ_i)

    Equivalently using rotate_half:
      out = x * cos + rotate_half(x) * sin
    where cos/sin are tiled to full d_head dimension.
    """
    seq_len = x.shape[-2]

    if position_ids is not None:
        # Gather frequencies for non-contiguous positions (e.g., after padding removal)
        cos = cos_table[position_ids]    # (batch, seq_len, d_head/2)
        sin = sin_table[position_ids]
    else:
        cos = cos_table[:seq_len]        # (seq_len, d_head/2)
        sin = sin_table[:seq_len]

    # Tile cos/sin from d_head/2 to d_head (each frequency applies to 2 dims)
    cos = torch.cat([cos, cos], dim=-1)  # (*, seq_len, d_head)
    sin = torch.cat([sin, sin], dim=-1)  # (*, seq_len, d_head)

    # Broadcast to (batch, num_heads, seq_len, d_head)
    if cos.dim() == 2:
        cos = cos.unsqueeze(0).unsqueeze(0)
        sin = sin.unsqueeze(0).unsqueeze(0)
    elif cos.dim() == 3:
        cos = cos.unsqueeze(1)
        sin = sin.unsqueeze(1)

    return x * cos + rotate_half(x) * sin


def verify_relative_position_property(d_head: int = 8) -> None:
    """
    Verify that RoPE dot product depends only on relative position (m-n),
    not on absolute positions m, n.

    Mathematical proof:
    (R_m q)^T (R_n k) = q^T R_m^T R_n k = q^T R_{n-m} k
    where R^T_m = R_{-m} for rotation matrices (orthogonal).
    """
    cos_table, sin_table = build_rope_freqs(d_head, max_seq_len=100)

    q = torch.randn(1, 1, 1, d_head)
    k = torch.randn(1, 1, 1, d_head)

    # Test: (RoPE(q, m=5))^T RoPE(k, n=8) == (RoPE(q, m=0))^T RoPE(k, n=3)
    # Because in both cases relative position = n - m = 3

    q_at_5 = apply_rope(q, cos_table, sin_table, position_ids=torch.tensor([[5]]))
    k_at_8 = apply_rope(k, cos_table, sin_table, position_ids=torch.tensor([[8]]))
    score_1 = (q_at_5 * k_at_8).sum()

    q_at_0 = apply_rope(q, cos_table, sin_table, position_ids=torch.tensor([[0]]))
    k_at_3 = apply_rope(k, cos_table, sin_table, position_ids=torch.tensor([[3]]))
    score_2 = (q_at_0 * k_at_3).sum()

    print(f"Score (pos 5,8): {score_1.item():.6f}")
    print(f"Score (pos 0,3): {score_2.item():.6f}")
    print(f"Are equal (relative pos = 3): {torch.allclose(score_1, score_2, atol=1e-5)}")
    # Output: True — RoPE encodes relative position, not absolute
```

### NTK-Aware Scaling

```python
def ntk_scaled_rope_freqs(
    d_head: int,
    max_seq_len: int,
    original_base: float = 10000.0,
    original_max_len: int = 4096,
    device: torch.device = torch.device("cpu"),
) -> Tuple[torch.Tensor, torch.Tensor]:
    """
    NTK-aware scaling (bloc97, 2023): extend context by scaling the base.

    Theory: RoPE can be viewed as a Neural Tangent Kernel (NTK) positional encoding.
    When we want to extend context from L to L', the highest frequency components
    in RoPE (small θ_i, large i) become compressed to the point where they cannot
    distinguish adjacent positions.

    Instead of compressing all positions (Position Interpolation),
    NTK-aware scaling modifies the base so that the new maximum frequency
    is scaled appropriately:

    New base = original_base × (L' / L)^(d / (d-2))

    The exponent d/(d-2) ≈ 1 for large d, so approximately:
    New base ≈ original_base × (L' / L)

    But the exact exponent matters for d=128 (common):
    128/(128-2) = 128/126 ≈ 1.016 → scale factor ≈ (L'/L)^1.016

    Effect:
    - Low-frequency dimensions (small i, large θ_i): period extends proportionally
    - High-frequency dimensions (large i, small θ_i): less distorted than PI
    """
    scale_factor = max_seq_len / original_max_len
    exponent = d_head / (d_head - 2)
    new_base = original_base * (scale_factor ** exponent)

    print(f"Original base: {original_base}")
    print(f"Scale factor (L'/L): {scale_factor}")
    print(f"NTK new base: {new_base:.1f}")

    return build_rope_freqs(d_head, max_seq_len, base=new_base, device=device)


def yarn_rope_freqs(
    d_head: int,
    max_seq_len: int,
    original_max_len: int = 4096,
    base: float = 10000.0,
    alpha: float = 1.0,        # Short-range factor (>1 = interpolate nearby positions less)
    beta: float = 32.0,        # Long-range factor (frequency below this: use NTK scaling)
    scale: float = 0.1,        # Temperature scaling parameter
    attn_factor: float = 0.1,  # Attention scaling (multiply by scale in attention)
    device: torch.device = torch.device("cpu"),
) -> Tuple[torch.Tensor, torch.Tensor]:
    """
    YaRN: Yet Another RoPE Extension (Peng et al., 2023).

    Three components:
    1. NTK-aware scaling for high-frequency components (same θ_i, scale base)
    2. Short-range integrity: don't interpolate nearby positions (local coherence)
    3. Temperature: scale attention scores to compensate for distribution shift

    Per-frequency decision:
    - If freq_i > alpha (high freq, captures local structure): no interpolation
    - If freq_i < beta (low freq, captures global structure): NTK-scale
    - Otherwise (medium freq): linear blend

    This is more nuanced than NTK-aware (which treats all frequencies equally).
    """
    s = max_seq_len / original_max_len  # extension ratio

    # Build original frequencies
    i = torch.arange(0, d_head, 2, device=device).float()
    original_freqs = 1.0 / (base ** (i / d_head))  # (d_head/2,)

    # NTK-scaled frequencies
    ntk_base = base * (s ** (d_head / (d_head - 2)))
    ntk_freqs = 1.0 / (ntk_base ** (i / d_head))

    # Position interpolation frequencies (just scale positions)
    # Equivalent to dividing original freqs by scale factor
    pi_freqs = original_freqs / s

    # Per-frequency blending based on wavelength
    low = 2 * math.pi / original_freqs   # wavelength in tokens
    # Frequencies with long wavelength (low freq): use NTK scaling
    # Frequencies with short wavelength (high freq): no interpolation
    # Medium: blend

    mask_low = low < alpha    # short wavelength -> no interpolation
    mask_high = low > beta    # long wavelength -> NTK scaling

    blended_freqs = torch.where(
        mask_low,
        original_freqs,         # short-range: keep as-is
        torch.where(
            mask_high,
            ntk_freqs,          # long-range: NTK scaling
            # Medium: linear interpolation between PI and NTK
            (1 - (low - alpha) / (beta - alpha)) * pi_freqs +
            (    (low - alpha) / (beta - alpha)) * ntk_freqs,
        )
    )

    t = torch.arange(max_seq_len, device=device).float()
    freqs = torch.outer(t, blended_freqs)

    # Temperature scaling: reduces attention score magnitude to compensate
    # for distribution shift from context extension
    # Applied at attention time: scores = QK^T / (sqrt(d_k) * attn_factor)
    # where attn_factor = sqrt(log(max_seq_len) / log(original_max_len))^(1/2)

    return freqs.cos(), freqs.sin()


def position_interpolation(
    x: torch.Tensor,        # (batch, num_heads, seq_len, d_head)
    cos_table: torch.Tensor,  # (original_max_len, d_head/2) — trained frequencies
    sin_table: torch.Tensor,
    original_max_len: int = 4096,
    current_seq_len: int | None = None,
) -> torch.Tensor:
    """
    Position Interpolation (Chen et al., 2023): scale positions to fit training range.

    Instead of using positions 0..L'-1 directly (which the model hasn't seen),
    map them to 0..L-1 by scaling: m' = m × (L-1)/(L'-1)

    This ensures all positions are within the trained range.
    Requires ~1000 steps of fine-tuning to adapt to the compressed scale.

    Used in: CodeLLaMA (4096 → 100K), LLaMA 2 Long (4096 → 32K)
    """
    if current_seq_len is None:
        current_seq_len = x.shape[-2]

    # Scale factor to interpolate positions
    scale = (original_max_len - 1) / (current_seq_len - 1) if current_seq_len > 1 else 1.0

    # Create scaled position indices
    positions = torch.arange(current_seq_len, device=x.device).float() * scale
    # positions now ranges 0..original_max_len-1 even for long sequences

    # Interpolate cos/sin values for fractional positions
    pos_floor = positions.long().clamp(0, original_max_len - 2)
    pos_frac = (positions - pos_floor.float()).unsqueeze(-1)

    cos = cos_table[pos_floor] * (1 - pos_frac) + cos_table[pos_floor + 1] * pos_frac
    sin = sin_table[pos_floor] * (1 - pos_frac) + sin_table[pos_floor + 1] * pos_frac

    cos = torch.cat([cos, cos], dim=-1).unsqueeze(0).unsqueeze(0)
    sin = torch.cat([sin, sin], dim=-1).unsqueeze(0).unsqueeze(0)

    return x * cos + rotate_half(x) * sin
```

### ALiBi Implementation

```python
import torch
import math


def build_alibi_bias(
    num_heads: int,
    max_seq_len: int,
    dtype: torch.dtype = torch.float32,
    device: torch.device = torch.device("cpu"),
) -> torch.Tensor:
    """
    ALiBi: Attention with Linear Biases (Press et al., 2022).

    Instead of positional encodings added to embeddings, ALiBi adds a
    position-dependent bias directly to attention scores:

    score_{i,j} = (q_i · k_j) / sqrt(d_k) + m_h × (j - i)

    where m_h is a head-specific slope: m_h = 2^(-8h/num_heads)
    For h=1..num_heads: slopes are geometric: 2^(-8/H), 2^(-16/H), ..., 2^(-8)

    Properties:
    - No positional encoding in embeddings (cleaner architecture)
    - Linear bias penalizes attending to distant tokens
    - Naturally extrapolates: longer distances simply get larger penalties
    - Slopes vary per head: some heads "zoom out" (small m, attend far), others "zoom in"

    Weakness: the linear bias penalizes ALL long-range attention, including important
    long-range dependencies (e.g., coreference over 1000 tokens). This makes ALiBi
    suboptimal for tasks requiring precise long-range dependencies.
    """
    # Head slopes: geometric sequence from 2^(-8/H) to 2^(-8)
    m_values = torch.tensor(
        [2 ** (-8 * (h + 1) / num_heads) for h in range(num_heads)],
        dtype=dtype, device=device,
    )  # (num_heads,)

    # Relative position matrix: (max_seq_len, max_seq_len)
    # position_i attends to position_j: relative pos = j - i (negative for future tokens)
    # For causal attention, j <= i always, so relative pos is always <= 0
    positions = torch.arange(max_seq_len, device=device)
    relative_positions = positions.unsqueeze(1) - positions.unsqueeze(0)  # (seq, seq)

    # ALiBi bias: m × (j - i), applied for causal (j <= i only)
    # Positive values (future) are masked out in causal attention anyway
    alibi = m_values.unsqueeze(1).unsqueeze(1) * relative_positions.unsqueeze(0)
    # alibi: (num_heads, max_seq_len, max_seq_len)

    # For causal: set future positions to -inf (will be masked)
    causal_mask = torch.tril(torch.ones(max_seq_len, max_seq_len, device=device)).bool()
    alibi = alibi.masked_fill(~causal_mask.unsqueeze(0), float('-inf'))

    return alibi  # Add to attention scores before softmax
```

---

## 7. Real-World Examples

**LLaMA 3 (Meta, 2024):** Uses RoPE with base=500,000 (vs original 10,000). The large base extends the natural frequency range, enabling 128K token context training from scratch. Higher base = slower rotation per position = longer effective "wavelength" for each dimension pair = better representation of long-range position differences.

**Mistral 7B context extension (community):** NTK-aware scaling applied to extend Mistral's 8K-trained model to 32K+ without fine-tuning. Setting `rope_scaling={"type": "dynamic", "factor": 4.0}` in HuggingFace config applies dynamic NTK. Quality degrades roughly 1-2 PPL points vs a model natively trained at 32K, but works without any fine-tuning cost.

**CodeLLaMA (Meta, 2023):** Position Interpolation from 4K to 100K contexts. Process: train LLaMA 2 at 4K, then fine-tune with PI for 1B tokens at 100K context (roughly 1000 steps). Position interpolation alone without fine-tuning degrades quality severely; the fine-tuning re-adapts the model to compressed position codes.

**Qwen2.5 (Alibaba, 2024):** Uses YaRN with base=1,000,000 and non-uniform frequency interpolation. Achieves 128K native context with strong perplexity across the full length (verified by "needle-in-a-haystack" tests). The non-uniform YaRN approach preserves short-range token relationships (critical for grammar/syntax) while enabling long-range context (critical for document-level reasoning).

**Gemini 1.5 Pro (Google, 2024):** Uses RingAttention for 1M token context across multiple TPU pods. Each pod handles a shard of the sequence; attention is computed by passing K/V rings between pods. The positional encoding challenge at 1M tokens is solved by using a large enough base frequency and sufficient training data at long context. ALiBi was considered but rejected — the linear penalty was too aggressive for Gemini's multilingual, multi-domain use case.

---

## 8. Tradeoffs

| Method | Extrapolation | Local accuracy | Long-range accuracy | No fine-tuning | Memory overhead |
|--------|---------------|----------------|---------------------|-----------------|-----------------|
| Sinusoidal APE | None | Good | Good (within train len) | Yes | O(seq × d) |
| Learned APE | None | Good | Good (within train len) | No | O(max_pos × d) |
| RoPE | Good with scaling | Excellent | Good | Yes | None (applied inline) |
| ALiBi | Excellent | Good | Penalized (intentional) | Yes | O(H × seq²) |
| PI | Moderate | Degraded | Poor (compressed) | No (needs FT) | None |
| NTK | Good | Good | Good | Often Yes | None |
| YaRN | Excellent | Excellent | Excellent | Sometimes | None |

| ALiBi vs RoPE for Long Context | |
|--------------------------------|--|
| ALiBi extrapolates naturally | Attention heads automatically assign smaller weight to distant tokens |
| RoPE requires explicit extension | NTK/YaRN needed; pure extrapolation degrades rapidly |
| ALiBi penalizes long-range | Reasoning tasks requiring 1000+ token dependencies hurt by linear penalty |
| RoPE at 128K+ | Requires YaRN or high base frequency; achieves better quality on complex tasks |
| Production verdict | RoPE + YaRN for high-quality models; ALiBi for latency-critical (no FT needed) |

---

## 9. When to Use / When NOT to Use

### Use RoPE when:

- Training a new model — RoPE is the de facto standard in 2024-2025
- Context extension is anticipated — easier to extend later via NTK/YaRN
- Tasks require precise relative position understanding (QA, reasoning, code)
- The model will be served with varying context lengths (RoPE handles any length when properly extended)

### Use ALiBi when:

- You want zero training cost for length extrapolation (ALiBi generalizes naturally without fine-tuning)
- Task predominantly uses local context (ALiBi's linear penalty is less harmful)
- Memory for storing ALiBi bias matrices is acceptable (O(H × seq²) added)
- The model does NOT need to handle precise very-long-range dependencies

### Use Position Interpolation when:

- You have a trained RoPE model and need to extend it cheaply (1000 fine-tuning steps)
- Quality degradation of 1-2 PPL points is acceptable
- You have sufficient compute for short fine-tuning

### Use YaRN when:

- Maximum quality at extended context is required
- ~400 steps of fine-tuning on long-context data is feasible
- The model will be deployed at context lengths significantly beyond training (>4x)

### Do NOT use sinusoidal APE for new models:

- It is strictly dominated by RoPE on every dimension that matters
- Cannot be extended without full retraining
- The only reason to use it is reproducibility of pre-2021 baselines

---

## 10. Common Pitfalls

### Pitfall 1: Extrapolating RoPE without scaling

```python
# BROKEN: Using a model trained at 4096 tokens with positions > 4096
# Positions beyond training range have never been seen; attention patterns break

model = LlamaForCausalLM.from_pretrained("meta-llama/Llama-2-7b-hf")
# Default config: max_position_embeddings=4096

# Generating at 8192 tokens without context extension:
output = model.generate(input_ids_8k_context, max_new_tokens=200)
# RESULT: near-random output after ~4096 tokens; model starts repeating/hallucinating

# FIXED: Set rope_scaling in the model config
from transformers import LlamaConfig
config = LlamaConfig.from_pretrained("meta-llama/Llama-2-7b-hf")
config.rope_scaling = {"type": "dynamic", "factor": 2.0}  # 2x extension: 4096 → 8192
model = LlamaForCausalLM.from_pretrained("meta-llama/Llama-2-7b-hf", config=config)
```

### Pitfall 2: Applying Position Interpolation without fine-tuning

```python
# BROKEN: PI without fine-tuning gives poor quality
# The model expects position codes in range [0, L/factor]; without fine-tuning,
# the dense attention patterns trained at [0, L] don't apply at scale [0, L/factor]

# Quality comparison on long-document QA (32K context):
# PI only (no FT): ROUGE-1 = 0.31  (worse than 4K chunking baseline)
# PI + 1K FT steps: ROUGE-1 = 0.44
# NTK (no FT): ROUGE-1 = 0.41
# YaRN + 400 FT steps: ROUGE-1 = 0.51

# GUIDELINE: If you cannot fine-tune, use NTK scaling (no FT required)
# If you can fine-tune briefly, YaRN + 400 steps is superior
```

### Pitfall 3: Position encoding and batch padding interaction

```python
# BROKEN: Padding tokens at the END get valid position IDs
# Model may attend to padding positions if attention mask is wrong

input_ids = tokenizer(["short sentence", "a much longer sentence here"],
                      padding=True, return_tensors="pt")
# input_ids: [[tok1, tok2, PAD, PAD], [tok1, tok2, tok3, tok4]]
# position_ids default: [[0, 1, 2, 3], [0, 1, 2, 3]]  <- WRONG for padded sequence
# Position 2 and 3 in the first sequence are padding, but get valid positions 2,3

# FIXED: Ensure position_ids=None and attention_mask is passed;
# the model should use attention_mask to zero out padding positions
# For RoPE specifically, HuggingFace models correctly generate position_ids
# from attention_mask when position_ids is None:
outputs = model(**input_ids)  # attention_mask automatically prevents padding positions
                               # from contributing to attention; position IDs are
                               # generated correctly based on non-padding positions
```

### Pitfall 4: ALiBi bias and Flash Attention incompatibility

```python
# BROKEN: ALiBi requires adding a bias to attention scores
# Flash Attention 2 does NOT support arbitrary attention bias matrices

from flash_attn import flash_attn_func
alibi_bias = build_alibi_bias(num_heads=32, max_seq_len=8192)
# flash_attn_func does not accept attn_bias parameter -> error or ignored

# FIXED option 1: Use PyTorch SDPA (slower, but correct)
output = torch.nn.functional.scaled_dot_product_attention(
    q, k, v, attn_mask=alibi_bias[:, :seq_len, :seq_len].unsqueeze(0)
)

# FIXED option 2: Switch from ALiBi to RoPE (preferred for new models)
# ALiBi is legacy; RoPE + YaRN is superior in quality and FA-2 compatible

# FIXED option 3: Use Flash Attention 3 (H100) which has limited bias support
# or maintain a FA-compatible ALiBi implementation
```

### Pitfall 5: YaRN attention factor not applied

```python
# YaRN includes a temperature adjustment to compensate for distribution shift
# after context extension. The paper recommends:
# attn_factor = sqrt(log(target_length) / log(original_length))^(1/scale_factor)

# BROKEN: YaRN frequencies applied but attention factor omitted
# Result: attention scores are systematically too large after extension,
# causing overconfident attention distributions and quality degradation

# FIXED: Apply YaRN attention factor in attention computation
def attention_with_yarn_factor(q, k, v, d_head, attn_factor: float = 1.0):
    scale = 1.0 / (d_head ** 0.5) / attn_factor  # attn_factor normalizes for extension
    return F.scaled_dot_product_attention(q, k, v)  # pass custom scale if API supports
    # Alternative: manually scale logits
    scores = torch.matmul(q, k.transpose(-2, -1)) * scale
    # ... apply causal mask, softmax, multiply V
```

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| HuggingFace `transformers` | RoPE config via `rope_scaling` | Supports "linear", "dynamic", "yarn" types |
| `rotary-embedding-torch` | RoPE reference implementation | pip install rotary-embedding-torch |
| `longrope` | LongRoPE implementation | Non-uniform frequency scaling |
| `yarn` (ggerganov fork) | YaRN implementation for llama.cpp | CLI flag: `--rope-scaling yarn --rope-scale 4` |
| `axolotl` | Fine-tuning framework with YaRN support | Configurable via YAML |
| LM-Eval Harness | Evaluate long-context models | Needle-in-haystack and RULER benchmarks |
| RULER benchmark | Long-context evaluation | 12 tasks from 4K to 128K tokens |

---

## 12. Interview Questions with Answers

**Q: Prove that a transformer without positional encoding is permutation-equivariant.**
Let P be a permutation matrix. Attention(Q,K,V) = softmax(QK^T/sqrt(d_k))V. For permuted input X' = PX: Q' = PX·W_Q = PQ, K' = PK, V' = PV. Attention(Q',K',V') = softmax(PQ·(PK)^T/sqrt(d_k))·PV = softmax(PQK^TP^T/sqrt(d_k))·PV. For any row stochastic matrix A: P·softmax(A)·... The softmax is applied row-wise. softmax(PAP^T) = P·softmax(A)·P^T. Therefore Attention(Q',K',V') = P·softmax(QK^T/sqrt(d_k))·P^T·PV = P·softmax(QK^T/sqrt(d_k))·V = P·Attention(Q,K,V). So the output is identically permuted — permutation equivariant.

**Q: Derive why RoPE satisfies the relative position property.**
RoPE applies rotation R_m to each d-dimensional query/key at position m. In 2D, R_m = [[cos(mθ), -sin(mθ)], [sin(mθ), cos(mθ)]]. For d-dimensional vectors, this is block-diagonal with d/2 such 2D rotation blocks (one per frequency θ_i). The inner product: (R_m·q)^T(R_n·k) = q^T·R_m^T·R_n·k. For orthogonal rotation matrices, R^T = R^{-1} = R_{-m}. So R_m^T·R_n = R_{-m}·R_n = R_{n-m}. Therefore (R_m·q)^T(R_n·k) = q^T·R_{n-m}·k — this depends only on (n-m), not on m or n individually. This is the relative position property: the attention score between token m (query) and token n (key) implicitly captures their relative distance n-m.

**Q: What is the difference between Position Interpolation and NTK-aware scaling?**
Position Interpolation scales the position index: position m is mapped to m×(L/L') where L is training length and L' is target length. All positions are compressed to fit within [0, L]. Problem: high-frequency RoPE dimensions (large θ_i, small rotation angles) are most affected — they lose the ability to distinguish adjacent positions when the scale compresses their frequency too much. NTK-aware scaling instead modifies the base frequency: new_base = original_base × (L'/L)^(d/(d-2)). This scales all frequencies uniformly in log-space, avoiding the disproportionate compression of high-frequency components. The key difference: PI compresses the position axis (what goes into the rotation angle computation); NTK scales the frequency axis (how fast things rotate per position unit). NTK preserves the model's ability to distinguish adjacent tokens; PI does not. In practice, NTK requires little or no fine-tuning while PI requires ~1000 fine-tuning steps to adapt.

**Q: What are the three components of YaRN and why is each necessary?**
YaRN (Peng et al., 2023) extends RoPE with: (1) NTK-aware base scaling for low-frequency dimensions — handles long-range position relationships; uses modified base to extend the effective wavelength of slow-rotating dimensions. (2) Short-range integrity — high-frequency dimensions (large θ_i) are left unchanged. These dimensions cycle rapidly and capture fine-grained local position differences. Scaling them (as in PI) would destroy local position information. YaRN identifies a threshold and applies no modification to dimensions above it. (3) Temperature adjustment — context extension shifts the distribution of attention scores. The model was trained expecting a certain range of QK^T values; after extension, positions farther apart produce unexpected values. YaRN applies a multiplicative temperature factor `1/sqrt(log(L'/L) / log(L/L))` to attention scores. This component alone adds 0.5-1 PPL point improvement over NTK-only extension. Together, these three components provide the best known quality for context extension.

**Q: Why does ALiBi extrapolate better than sinusoidal APE?**
Sinusoidal APE assigns each absolute position a fixed code via sin/cos frequencies. These codes are used as inputs to the attention computation. A model trained on positions 0..4095 has never seen position code 4096+; the attention weights were not calibrated for these codes. Extrapolation fails because the model encounters OOD inputs. ALiBi never modifies the token embeddings — positions are only reflected in the attention score bias: `m × (j - i)`. For positions beyond the training length, the model sees a larger negative bias for distant tokens. This is mathematically valid: the model was trained to understand that larger negative biases mean less relevant positions. Since the bias grows linearly (not discretely), positions 0..∞ all produce valid biases. The model generalizes: "this token is very far away → high penalty → attend to it less." This is semantically consistent extrapolation.

**Q: How does LLaMA 3 achieve 128K context with RoPE?**
LLaMA 3 was trained natively at 128K context using RoPE base=500,000 (vs LLaMA 2's 10,000). The higher base directly scales all frequencies: θ_i = base^(-2i/d) — higher base → smaller θ_i → slower rotation per position → effectively "zooms out" the frequency spectrum. At base=500,000, the slowest frequency (i=d/2-1) has period = 2π × 500,000^(d/(d-2)) ≈ 2.8M positions — more than enough for 128K context. LLaMA 3 also used graduated context window scaling during training: starting at 8K and expanding to 128K in later training phases (similar to curriculum learning for context). This avoids the attention pattern disruption that occurs when jumping directly to full context length. The key insight: choosing a large enough base frequency eliminates the need for post-hoc context extension methods entirely.

**Q: What happens to model quality when you extend context 10x without any fine-tuning?**
For pure position extrapolation (no extension method, no fine-tuning): quality degrades catastrophically beyond ~1.2-1.5x training length. At 4x training length: perplexity roughly doubles, generation becomes incoherent. At 10x: the model produces near-random output. For NTK-aware scaling (no fine-tuning): quality degrades gracefully. At 4x extension: PPL increases by ~1.5-2 points on standard benchmarks; generation is still coherent. At 10x: ~4-6 PPL increase; quality is useful but noticeably degraded. For YaRN (400 fine-tuning steps): at 4x: <0.5 PPL increase; at 10x: ~1-2 PPL increase; quality is nearly indistinguishable from natively trained. The practical recommendation: NTK scaling for zero-cost extension up to 4x; YaRN with 400 steps for up to 16x; train natively (with high base frequency) for extensions beyond 16x.

**Q: Explain the "lost in the middle" phenomenon and how positional encoding relates to it.**
"Lost in the middle" (Liu et al., 2023) is the empirical finding that LLMs have difficulty retrieving information placed in the middle of long contexts — they perform well on information at the beginning and end. This is partly a positional encoding issue and partly an attention pattern issue. For RoPE, information at early positions has seen many subsequent positions "attend" to it across layers (because attention is causal and cumulative). Information in the middle has been processed by fewer subsequent attention layers' cross-positional interactions by the time the model generates a response. ALiBi's linear bias exacerbates this: tokens in the middle are far from both the beginning and the end (relative to a query at the end), receiving high penalties. Architecturally, addressing "lost in the middle": (1) bi-directional attention (BERT-style, but for generation) helps but contradicts causal training; (2) positional re-weighting (emphasize middle positions in attention); (3) training with more examples that specifically require middle-position retrieval (improves attention patterns).

**Q: How would you extend a model from 4K to 32K context with minimal quality loss?**
Decision tree: (1) Check if the model uses RoPE (LLaMA, Mistral, Qwen) or ALiBi (MPT, BLOOM). ALiBi extrapolates naturally — no action needed, test quality. (2) For RoPE: apply NTK-aware scaling first (no fine-tuning, fast to test). Set `rope_scaling={"type": "dynamic", "factor": 8}` in HuggingFace config. Measure PPL on a long-context benchmark (PG-19 or SCROLLS). (3) If quality is insufficient and fine-tuning compute is available: apply YaRN with 400-1000 steps on long-context data. Use learning rate ~2e-5, sequence length 32K, batch such that ~1M tokens per step. (4) If compute is very limited: evaluate whether NTK quality meets requirements — often good enough for retrieval-augmented applications where high PPL doesn't matter (you're looking for specific content, not generating fluently). (5) After extension, run needle-in-a-haystack tests across multiple positions and depths to confirm quality throughout the extended range.

**Q: What is dynamic NTK scaling and when does it help?**
Dynamic NTK scaling (kaiokendev, 2023) applies NTK-aware scaling adaptively: the scale factor is computed from the actual sequence length at inference time, not from a fixed target length. At seq_len=4096 (training length): scale=1, no modification. At seq_len=8192: scale=2, apply NTK. At seq_len=32768: scale=8, apply NTK. This is useful for deployments where request lengths vary widely — a fixed NTK factor of 8 would distort position encodings even for short sequences (unnecessarily). Dynamic scaling applies the minimum necessary modification. Compared to static NTK: dynamic is slightly better for short sequences (no unnecessary distortion) but has the same quality at the target extension length. LLaMA 3.1 uses dynamic NTK scaling with base=500,000 — allowing graceful handling of any sequence length up to 128K without different model variants for different context lengths.

**Q: How does the sinusoidal encoding dot product encode relative position for nearby tokens?**
For sinusoidal APE, the dot product of two position encodings PE_m and PE_n is: `PE_m · PE_n = Σ_{i=0}^{d/2-1} [sin(m·θ_i)sin(n·θ_i) + cos(m·θ_i)cos(n·θ_i)] = Σ_i cos((m-n)·θ_i)`. This depends only on the difference (m-n) — geometrically, it is the sum of cosines at different frequencies evaluated at the relative offset. For nearby tokens (|m-n| small), the cosines are all near 1 (small argument) → high dot product. For distant tokens, high-frequency components oscillate rapidly, reducing the mean dot product. This gives a loose relative position signal. However, it is not as clean as RoPE: the relation holds for the dot product between position encodings, but position encodings are added to token embeddings — the attention score includes cross-terms between token content and position. These cross-terms contaminate the "relative position" information, making sinusoidal APE less clean than RoPE.

**Q: What is the practical difference in quality between RoPE, ALiBi, and T5 relative bias?**
On standard NLP benchmarks (MMLU, HellaSwag, BoolQ), models with RoPE and ALiBi show comparable quality at training context length (<1% difference). The divergence appears at: (1) long context (>training length): ALiBi extrapolates natively, RoPE requires NTK/YaRN; (2) tasks requiring precise long-range dependencies: ALiBi's linear penalty actively hurts on multi-hop reasoning spanning 2K+ tokens; RoPE preserves long-range information. T5 relative bias uses learned bucketed relative positions: there are ~32 buckets, and positions beyond the largest bucket share a bucket — providing some extrapolation within a fixed range. T5 relative bias is more expressive than ALiBi (learned, not fixed slope) but has higher memory cost (stored bias matrices per layer) and doesn't extrapolate beyond its bucket range. Modern consensus: RoPE + YaRN dominates for decoder-only LLMs; T5 relative bias remains viable for encoder-decoder models where bidirectionality matters; ALiBi is declining in use but still seen in compute-efficient deployments.

**Q: How does RingAttention extend context to 1M+ tokens?**
RingAttention (Liu et al., 2023) distributes the attention computation across multiple GPUs/TPUs arranged in a logical ring. Each device holds a chunk of the full sequence. In each communication round, one device's K/V chunk is passed to the next device in the ring (the "ring" of K/V blocks). Each device computes attention between its local Q chunk and the incoming K/V chunk, accumulating results using online softmax (same principle as Flash Attention). After N rounds (N = number of devices), each device has accumulated the full attention output for its Q chunk. Memory: O(seq_len / num_devices) per device. Communication: O(seq_len × d_model) total (each K/V block passes through the ring once). This enables context lengths limited only by total memory across the cluster. Gemini 1.5 Pro's 1M token context uses a form of distributed attention over TPU pods. The positional encoding challenge: RoPE works at 1M positions if the base frequency is high enough (Gemini uses a very large base).

---

## 13. Best Practices

1. Use RoPE for all new model training — it is the de facto standard in 2024-2025, supported by Flash Attention, compatible with KV cache, and extensible via YaRN.
2. Set a large RoPE base (500,000 or higher) if training at long context from scratch — eliminates the need for post-hoc extension methods.
3. Never extrapolate RoPE beyond 1.2x training length without at minimum NTK-aware scaling — the quality degradation is severe and often silent (model generates fluent but incorrect text).
4. Use `rope_scaling={"type": "dynamic", "factor": N}` in HuggingFace for zero-cost extension; test quality with needle-in-a-haystack before deploying.
5. For maximum quality at extended context, use YaRN + 400 fine-tuning steps on long-context data — the quality gap over NTK is significant for reasoning tasks.
6. When extending context, always run RULER or needle-in-a-haystack evaluation across depths (first, middle, last third of context) — average metrics hide "lost in the middle" failures.
7. ALiBi bias matrices must be added to attention scores before softmax, not to the output — a common implementation mistake that completely breaks the positional signal.
8. For production deployments with variable context lengths, use dynamic NTK scaling — it has zero cost for short sequences and applies appropriate scaling for long ones.
9. Do not mix positional encoding methods (e.g., ALiBi bias + RoPE embeddings) without understanding their interaction — the combination is not additive in a simple way.
10. Track RoPE configuration (base, max length, scaling type) in model cards — context extension methods vary across model versions and the configuration significantly affects quality at long context.

---

## 14. Case Study

### Problem: Extending a 7B Model from 4K to 32K Context for Legal Document Analysis

**Context:** A law firm deployed LLaMA-2-7B (4096 token context) for contract review. Lawyers need to analyze contracts with clauses referencing sections earlier in the document — requiring cross-document context of 20-30K tokens. The firm cannot afford to train a new model and wants to extend the existing model.

**Baseline evaluation (4096 tokens):**

- PPL on held-out legal corpus (4K context): 8.2
- Needle-in-haystack accuracy at 4K: 91%

**Extension approach evaluation:**

```python
# Three approaches tested:
# 1. Pure extrapolation (no extension): positions 0..32767, no modification
# 2. NTK-aware (factor=8): new_base = 10000 * 8^(128/126) ≈ 81,300
# 3. YaRN (factor=8) + 500 fine-tuning steps on legal text at 32K

# Results on held-out legal contracts at 32K context:
results = {
    "Pure extrapolation": {
        "ppl_32k": 312.4,   # catastrophic degradation
        "needle_32k": 0.18,  # near-random
        "cost": "$0",
    },
    "NTK-aware (no FT)": {
        "ppl_32k": 12.1,    # +3.9 PPL vs 4K baseline
        "needle_32k": 0.71,  # reasonable, some degradation
        "cost": "$0",
    },
    "YaRN + 500 steps FT": {
        "ppl_32k": 9.8,     # +1.6 PPL vs 4K baseline
        "needle_32k": 0.88,  # near-baseline quality
        "cost": "$180 (A100 time)",
    }
}
```

**Implementation:**

```python
from transformers import LlamaForCausalLM, LlamaConfig

config = LlamaConfig.from_pretrained("meta-llama/Llama-2-7b-hf")
config.max_position_embeddings = 32768
config.rope_scaling = {
    "type": "yarn",
    "factor": 8.0,
    "original_max_position_embeddings": 4096,
    "beta_fast": 32,    # short-range integrity threshold
    "beta_slow": 1,     # long-range NTK scaling threshold
    "mscale": 1.0,      # attention factor (YaRN temperature)
    "mscale_all_dim": 0.707,
}

model = LlamaForCausalLM.from_pretrained("meta-llama/Llama-2-7b-hf", config=config)

# Fine-tune with YaRN:
# Learning rate: 2e-5 (same as classification fine-tuning range)
# Batch: 1 sequence × 32768 tokens (memory-limited)
# Steps: 500 on legal corpus data (10K contracts at 32K context)
# Dataset: Mix 80% 32K examples, 20% 4K examples (prevent forgetting short-context quality)
```

**Production outcome:**

- Cross-section reference accuracy: 91% at 32K (vs 0% with 4K baseline — out-of-scope positions)
- Clause risk classification F1: 0.87 at 32K (vs 0.89 at 4K — minimal degradation)
- Lawyer satisfaction: eliminated 60% of manual document segmentation work
- Total extension cost: $180 GPU compute + 2 hours engineering
- Key lesson: YaRN fine-tuning on domain data was critical — NTK alone reduced clause reference accuracy to 72% (missing references in second half of long contracts), which was unacceptable for legal applications
