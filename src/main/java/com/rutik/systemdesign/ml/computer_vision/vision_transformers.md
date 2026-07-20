# Vision Transformers (ViT)

## 1. Concept Overview

Vision Transformers (ViT) apply the transformer architecture — originally designed for NLP — directly to images by splitting them into a sequence of fixed-size patches. Each patch is linearly projected to a vector (the patch embedding), position encodings are added, and the sequence is processed by standard transformer encoder blocks with multi-head self-attention.

ViT demonstrated in 2020 that pure self-attention, with sufficient training data, matches or surpasses convolutional neural networks on image classification. Subsequent work (DeiT, Swin, BEiT, DINOv2) extended ViT to work with less data, support dense prediction tasks (detection, segmentation), and provide general-purpose visual representations.

The paradigm shift: CNNs have strong spatial inductive biases (local connectivity, translation equivariance) built into their architecture. ViTs have almost none — they learn these biases from data, making them more flexible but requiring more data or stronger pretraining.

---

## 2. Intuition

Imagine reading a book vs. analyzing a photograph. Reading is sequential and local — you process words near each other. Analyzing a photograph is global — you immediately see relationships between distant parts (sky above, road below, car in middle). CNNs read images like text: local filters build up global understanding slowly through depth. ViTs analyze images more holistically: every patch attends to every other patch from the very first layer.

Key insight: the self-attention mechanism in ViT has O(n^2) complexity in the number of patches n. For a 224x224 image with 16x16 patches, n=196 — manageable. For higher resolutions or pixel-level tasks, this quadratic cost is a challenge that Swin Transformer solves with local windowed attention.

---

## 3. Core Principles

**Patch embedding**: divide the image into non-overlapping P×P patches. Flatten each patch to a vector and project with a linear layer to embedding dimension D. For ViT-B/16 on 224x224 images: P=16, patches=196, D=768.

**Positional encoding**: since self-attention is permutation-invariant, positional information must be injected explicitly. ViT uses 1D learnable position embeddings added to the patch embeddings. 2D sinusoidal and relative position encodings are used in variants for better interpolation to unseen resolutions.

**CLS token**: a learnable [class] token is prepended to the patch sequence. After passing through the transformer, the CLS token's output representation aggregates global image information and is fed into the classification head. DeiT adds a second [distillation] token.

**Multi-head self-attention (MHSA)**: each attention head computes Q, K, V projections from the token sequence. Attention weights A = softmax(QK^T / sqrt(d_k)) determine how much each token attends to each other. Multiple heads capture different relational patterns in parallel.

**Layer Normalization**: applied before (pre-norm in modern ViTs) rather than after each sub-layer, which stabilizes training of very deep models.

### Decoding the patch-count formula

```
N = (H / P) x (W / P)          patches
L = N + 1                      sequence length after prepending CLS
```

**In plain terms.** "Lay a grid of P-pixel squares over the image, count the squares, then add one extra slot for the token that will carry the whole-image summary."

That framing matters because `L` — not the pixel count — is what every downstream cost scales with. Halving the patch size does not double the work; it quadruples the token count, and attention squares that again.

| Symbol | What it is |
|--------|------------|
| `H`, `W` | Image height and width in pixels (224 for standard ViT) |
| `P` | Patch side length in pixels — the `/16` in "ViT-B/16" |
| `H / P` | How many patches fit across one axis; must divide evenly (the `assert` in the code) |
| `N` | Number of patch tokens the encoder sees |
| `L` | Actual sequence length, `N + 1`, because the learnable CLS token is prepended |
| `D` | Embedding width every token is projected to (768 for ViT-B) |

**Walk one example.** The three resolutions this file already mentions:

```
  config              H     P    H/P    N = (H/P)^2    L = N+1
  ViT-B/16 @ 224     224   16     14        196          197
  ViT-B/16 @ 384     384   16     24        576          577
  ViT-B/8  @ 224     224    8     28        784          785

  384px vs 224px at P=16:   576 / 196  = 2.94x more tokens
  P=8 vs P=16 at 224px:     784 / 196  = 4.00x more tokens
```

The `197` in every diagram above is just `14 x 14 + 1`. The `(197, 768)` position-embedding
shape in Pitfall 1 is `L x D` — which is exactly why moving to 384px breaks it: the tensor
now needs to be `577 x 768`, so the stored 197 rows must be bicubically interpolated to 577.

**Where the patch-embedding parameters go.** Flattening one patch gives `P x P x C` values,
and the projection maps that to `D`:

```
  flattened patch = P x P x C = 16 x 16 x 3            =     768 values
  projection W    = (P*P*C) x D = 768 x 768            = 589,824 weights
  projection bias = D                                  =     768
  patch embedding total                                = 590,592 params

  CLS token       = 1 x D                              =     768 params
  position embed  = L x D = 197 x 768                  = 151,296 params
```

For ViT-B/16 the flattened patch happens to be 768 values and `D` is also 768, so the
projection is a square `768 x 768` matrix — that is the `W: 768x768` label in the second
diagram, not a coincidence anyone designed for.

### Decoding the scaled dot-product attention formula

```
A = softmax( Q K^T / sqrt(d_k) ) ;    output = A V
d_k = D / num_heads = 768 / 12 = 64
```

**What this actually says.** "Score every token against every other token with a dot product, shrink those scores so they stay in a sane range, turn each row into a set of weights that sum to 1, and use those weights to average the value vectors."

The division is not cosmetic. It is the difference between a distribution the gradient can move and a one-hot spike that is effectively frozen.

| Symbol | What it is |
|--------|------------|
| `Q` | Queries — "what this token is looking for", shape `L x d_k` per head |
| `K` | Keys — "what each token offers", shape `L x d_k` per head |
| `V` | Values — the content actually mixed together, shape `L x d_k` per head |
| `Q K^T` | The `L x L` raw score matrix; entry `(i, j)` = how much token i cares about token j |
| `d_k` | Per-head dimension, `D / num_heads` (64 for ViT-B: 768 / 12) |
| `sqrt(d_k)` | Variance corrector — `self.scale = head_dim ** -0.5` in the code, i.e. 1/8 |
| `softmax(...)` | Turns each row of scores into non-negative weights summing to 1 |
| `A V` | Weighted average of value vectors — the actual output mixing |

**Walk one example.** Why `sqrt(d_k)` exists, in one row of four scores:

```
  If each q_i and k_i has mean 0 and variance 1, then a dot product of d_k terms has
    variance = d_k = 64      ->   standard deviation = sqrt(64) = 8

  So raw scores routinely land around +/- 8 to +/- 16. Take one such row:

  raw scores  ->        16.0      8.0      0.0     -8.0
  softmax(raw)     0.999665  0.000335   ~0.0     ~0.0     entropy 0.003 nats

  divide by sqrt(d_k) = 8:
  scaled       ->         2.0      1.0      0.0     -1.0
  softmax(scaled)    0.6439   0.2369   0.0871   0.0321    entropy 0.947 nats

  uniform-attention entropy for 4 tokens = ln(4) = 1.386 nats
```

Without the divisor the row is 99.97% on one patch — a hard argmax. Softmax gradients are
proportional to `p_i (1 - p_i)`, which at `p = 0.999665` is `0.000335`: the gradient has all
but vanished, so the model can never learn to attend anywhere else. Dividing by `sqrt(d_k)`
puts the standard deviation back at 1, keeps the row spread across patches, and keeps the
gradient alive. This is also the mechanism behind Best Practice 9's advice to monitor
attention entropy — an entropy near 0 is precisely the saturated row above.

---

## 4. Types / Architectures / Strategies

### ViT Variants

| Model | Patches | Depth | Heads | D | Params | ImageNet Top-1 | Pretraining |
|-------|---------|-------|-------|---|--------|----------------|-------------|
| ViT-S/16 | 16×16 | 12 | 6 | 384 | 22M | 81.4% | ImageNet-21k |
| ViT-B/16 | 16×16 | 12 | 12 | 768 | 86M | 84.2% | ImageNet-21k |
| ViT-L/16 | 16×16 | 24 | 16 | 1024 | 307M | 86.4% | ImageNet-21k |
| ViT-H/14 | 14×14 | 32 | 16 | 1280 | 632M | 88.6% | JFT-3B |

#### Where the 86M in that table comes from

```
params per encoder block ~ 4*D^2  (Q,K,V,out projections)
                         + 8*D^2  (MLP: D -> 4D -> D)
                         = 12*D^2
```

**Stated plainly.** "Almost every parameter in a ViT is a square `D x D` matrix, and each encoder block holds twelve of them — so the parameter count is essentially `12 x depth x D^2`."

Once you internalize `12*D^2` per block you can size any ViT variant in your head, and you can see immediately why width (`D`, squared) dominates depth (linear).

| Symbol | What it is |
|--------|------------|
| `D` | Embedding width — 384 for ViT-S, 768 for ViT-B, 1024 for ViT-L |
| `4*D^2` | The four attention projections: Q, K, V, and the output `self.proj` |
| `8*D^2` | The MLP's two layers, `D -> 4D` then `4D -> D`, each `4*D^2` |
| depth | Number of stacked encoder blocks (12 for ViT-B, 24 for ViT-L) |
| head_dim | `D / heads` = 64 for ViT-B; note `4*D^2` is independent of how you split heads |

**Walk one example.** ViT-B/16 with `D = 768`, depth 12:

```
  attention per block   4 * 768^2                    =  2,359,296
  MLP per block         2 * 768 * 3072               =  4,718,592
  block subtotal        12 * 768^2                   =  7,077,888
  x 12 blocks                                        = 84,934,656

  biases + LayerNorm per block  (3D + D + 4D + D + 4D) ~     9,984
  x 12 blocks                                        =    119,808

  patch embedding (from section 3)                   =    590,592
  position embeddings 197 x 768                      =    151,296
  CLS token                                          =        768
  final LayerNorm  2 * 768                           =      1,536
  ---------------------------------------------------------------
  total                                              = 85,798,656  ~ 86M
```

The 12 encoder blocks are 99.0% of the model; patch embedding, CLS, and position
embeddings together are under 0.9%. That is why fine-tuning tricks target the blocks and
why the classification head is treated as free.

### DeiT (Data-efficient Image Transformers)

DeiT achieves ViT-S-level accuracy training only on ImageNet-1k (1.28M images) without large proprietary datasets. Key innovations: (1) a distillation token that learns from a CNN teacher (RegNetY-16GF), receiving a "hard label" distillation loss alongside the standard classification loss; (2) stochastic depth regularization; (3) repeated augmentation (same image appears in the batch multiple times with different augmentations). DeiT-B reaches 83.4% top-1 on ImageNet-1k, competitive with ResNet-152.

### Swin Transformer

Swin addresses ViT's quadratic attention cost by computing self-attention within fixed local windows (7×7 patches by default) rather than globally. Shifted windows between layers enable cross-window connections. A hierarchical design (stages that merge patches) reduces the token count at each stage, enabling Swin to serve as a general-purpose backbone for detection and segmentation (unlike vanilla ViT, which only outputs a single-scale feature map).

| Model | Params | ImageNet Top-1 | COCO mAP | Notes |
|-------|--------|----------------|----------|-------|
| Swin-T | 29M | 81.3% | 50.5 | Tiny, comparable to ResNet-50 |
| Swin-B | 88M | 83.5% | 51.9 | Base |
| Swin-L | 197M | 86.3% | 53.5 | Large, SOTA before DINOv2 |

#### Decoding O(n^2) vs O(n · W^2)

```
global attention   cost ~ n^2          (every token scores every token)
window attention   cost ~ (n / W^2) x (W^2)^2 = n x W^2
speedup            = n^2 / (n x W^2)  = n / W^2
```

**Read it like this.** "Global attention pays for one giant `n x n` score matrix; window attention pays for many tiny `W^2 x W^2` matrices instead, and because those small matrices shrink quadratically while their count grows only linearly, the total collapses to linear in `n`."

The payoff is that the speedup is not a constant — it is `n / W^2`, so windowing helps *more* the larger the image gets, which is precisely the regime where global attention was breaking.

| Symbol | What it is |
|--------|------------|
| `n` | Number of tokens at the current stage (`56 x 56 = 3136` in Swin stage 1) |
| `W` | Window side in tokens — 7 by default, so a window holds `W^2 = 49` tokens |
| `n / W^2` | How many windows tile the feature map |
| `(W^2)^2` | Score-matrix size inside one window: 49 x 49 = 2,401 entries |
| `n x W^2` | Total window-attention score entries — linear in `n` |
| `n^2` | Total global-attention score entries — quadratic in `n` |

**Walk one example.** The quadratic jump first, then what windowing buys back:

```
  global attention, score-matrix entries per head
    tokens n = 196   (224px, P=16)      196^2   =        38,416
    tokens n = 576   (384px, P=16)      576^2   =       331,776    8.6x
    tokens n = 4096  (512px, P=8)      4096^2   =    16,777,216  436.7x

  2.3x the pixels (224 -> 384) costs 8.6x the attention.
  Going to 512px with P=8 costs 436.7x -- 16.8M entries per head, per layer.

  Swin stage 1, n = 3136 tokens, W = 7:
    global                3136^2                =     9,834,496
    windows               3136 / 49             =            64
    per window            49 x 49               =         2,401
    windowed total        64 x 2401 = 3136 x 49 =       153,664
    speedup               3136 / 49             =         64.0x
```

That `n / W^2` factor is why Swin scales: at `n = 196` windowing would only save 4x, but at
stage-1 resolution it saves 64x, and at `n = 4096` it saves 83.6x. The cost of the trick is
that a token can only see its own 49-token window — which is exactly the hole the shifted
windows (SW-MSA) in alternating blocks are there to patch.

### CLIP (Contrastive Language-Image Pretraining)

CLIP trains a ViT image encoder and a GPT-like text encoder jointly on 400M image-text pairs from the web. The training objective maximizes cosine similarity of matching image-text pairs and minimizes it for non-matching pairs (contrastive loss). The result: a visual encoder whose embedding space is aligned with language. Zero-shot classification: embed the image; embed text prompts ("a photo of a cat"); compute cosine similarity; take argmax. CLIP ViT-L/14@336px achieves 76.2% zero-shot on ImageNet without any fine-tuning on ImageNet labels.

### DINOv2

DINOv2 pretrains ViT using self-supervised learning (see self_supervised_vision.md for DINO details) on a curated 142M image dataset. The resulting ViT-g/14 achieves 86.5% linear probing on ImageNet — the best visual representation quality of any model as of 2024, including CLIP. DINOv2 features emerge without any labels: depth estimation, semantic correspondence, and segmentation appear from probing intermediate layers.

---

## 5. Architecture Diagrams

### ViT End-to-End Pipeline

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph enc["Transformer encoder block × 12 (pre-norm)"]
        res0((" split ")) --> ln1["LayerNorm"]
        ln1 --> mhsa["Multi-head self-attention\n12 heads · d_k=64"]
        mhsa --> add1((" + "))
        res0 --> add1
        add1 --> ln2["LayerNorm"]
        ln2 --> mlp["MLP 768 → 3072 → 768 · GELU"]
        mlp --> add2((" + "))
        add1 --> add2
    end
    img([Input image\n224×224×3]) --> tok["Patch embed + CLS + position\n197 tokens × 768"]
    tok --> res0
    add2 --> pick["Take CLS token → R^768"]
    pick --> head["MLP classification head"]
    head --> logits([num_classes logits])

    class img,logits io
    class tok,pick mathOp
    class ln1,ln2,mhsa,mlp train
    class res0,add1,add2 mathOp
    class head train
```

*The image becomes a 197-token sequence, every token attends to every other from the first layer, and the CLS token's final vector drives classification. The two `+` circles are the pre-norm residual adds — `x + MHSA(LN(x))` then `y + MLP(LN(y))` — which keep gradients flowing through the 12-block stack.*

### Patch, CLS, and Position Embedding

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    img([Image 224×224×3]) --> grid["14×14 grid\n196 patches of 16×16"]
    grid --> flat["Flatten each patch\n→ 768 values"]
    flat --> proj["Linear projection\nW: 768×768"]
    proj --> pe["196 patch embeddings"]
    cls["CLS token\n(learnable)"] --> cat(("concat"))
    pe --> cat
    cat --> seq["197 tokens × 768"]
    pos["Position embeddings\n197×768 (learnable)"] --> add((" + "))
    seq --> add
    add --> out([To transformer encoder])

    class img,out io
    class grid,flat,proj,pe mathOp
    class cls,pos train
    class cat,add mathOp
    class seq io
```

*Self-attention is permutation-invariant, so without the added position embeddings the 196 patches would be an unordered set and the model could not tell top from bottom. The learnable CLS token (green) is prepended before the encoder and later read out as the whole-image representation.*

### Swin Transformer Hierarchical Design

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph s1["Stage 1"]
        p1["Patch partition 4×4\n56×56 tokens · C=96"] --> b1["Swin block × 2\nW-MSA + SW-MSA"]
    end
    subgraph s2["Stage 2"]
        m2["Patch merging 2×2\n28×28 tokens · C=192"] --> b2["Swin block × 2"]
    end
    subgraph s3["Stage 3"]
        m3["Patch merging 2×2\n14×14 tokens · C=384"] --> b3["Swin block × 6"]
    end
    subgraph s4["Stage 4"]
        m4["Patch merging 2×2\n7×7 tokens · C=768"] --> b4["Swin block × 2"]
    end
    img([Input 224×224]) --> p1
    b1 --> m2
    b2 --> m3
    b3 --> m4
    b4 --> clshead["Classification\nGlobalAvgPool → Linear"]
    b4 --> fpn["Detection / segmentation\nFPN over 4 stage outputs"]

    class img io
    class p1,m2,m3,m4 mathOp
    class b1,b2,b3,b4 train
    class clshead,fpn io
```

*Patch merging halves spatial resolution and doubles channels each stage, so Swin emits the same 4-scale feature pyramid as a ResNet — which is why detection and segmentation heads plug straight in. Attention is computed inside fixed 7×7 windows (W-MSA), cutting cost from O(n²) to O(n·W²); alternate blocks shift the window grid by (W/2, W/2) so information crosses window boundaries.*

### CLIP Contrastive Pretraining

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    img([Image]) --> ie["Image encoder\n(ViT)"]
    txt(["Text: a photo of a cat"]) --> te["Text encoder\n(Transformer)"]
    ie --> ip["Linear projection → d_emb"]
    te --> tp["Linear projection → d_emb"]
    ip --> sim["Cosine similarity matrix\nB × B"]
    tp --> sim
    sim --> loss["Symmetric InfoNCE loss\ndiagonal = matching pair (maximize)\noff-diagonal = mismatch (minimize)"]

    class img,txt io
    class ie,te,ip,tp train
    class sim mathOp
    class loss lossN
```

*Training aligns the image and text embedding spaces so matching pairs land on the diagonal of the batch similarity matrix. At inference, zero-shot classification embeds the image once, embeds each class prompt ("a photo of a [class]"), and takes the argmax cosine similarity — no labeled fine-tuning required.*

---

## 6. How It Works — Detailed Mechanics

### Patch Embedding and Positional Encoding

```python
import torch
import torch.nn as nn
from torch import Tensor
import math


class PatchEmbedding(nn.Module):
    """
    Splits image into patches and projects to embedding dimension.
    Input:  (B, C, H, W)
    Output: (B, num_patches, embed_dim)
    """

    def __init__(self, img_size: int = 224,
                 patch_size: int = 16,
                 in_channels: int = 3,
                 embed_dim: int = 768) -> None:
        super().__init__()
        assert img_size % patch_size == 0, \
            f"Image size {img_size} must be divisible by patch size {patch_size}"
        self.num_patches = (img_size // patch_size) ** 2
        self.patch_size = patch_size
        # Equivalent to splitting + linear: use Conv2d with stride=patch_size
        self.projection = nn.Conv2d(in_channels, embed_dim,
                                     kernel_size=patch_size,
                                     stride=patch_size)

    def forward(self, x: Tensor) -> Tensor:
        # x: (B, C, H, W)
        x = self.projection(x)    # (B, embed_dim, H/P, W/P)
        x = x.flatten(2)          # (B, embed_dim, num_patches)
        x = x.transpose(1, 2)     # (B, num_patches, embed_dim)
        return x


class ViTEmbedding(nn.Module):
    """Full ViT input: patch embed + CLS token + position encoding."""

    def __init__(self, img_size: int = 224,
                 patch_size: int = 16,
                 in_channels: int = 3,
                 embed_dim: int = 768,
                 dropout: float = 0.0) -> None:
        super().__init__()
        self.patch_embed = PatchEmbedding(img_size, patch_size,
                                           in_channels, embed_dim)
        num_patches = self.patch_embed.num_patches

        self.cls_token = nn.Parameter(torch.zeros(1, 1, embed_dim))
        # Learnable 1D positional embeddings (num_patches + 1 for CLS)
        self.pos_embed = nn.Parameter(
            torch.zeros(1, num_patches + 1, embed_dim))
        self.dropout = nn.Dropout(dropout)
        self._init_weights()

    def _init_weights(self) -> None:
        nn.init.trunc_normal_(self.cls_token, std=0.02)
        nn.init.trunc_normal_(self.pos_embed, std=0.02)

    def forward(self, x: Tensor) -> Tensor:
        B = x.size(0)
        x = self.patch_embed(x)                            # (B, N, D)
        cls = self.cls_token.expand(B, -1, -1)             # (B, 1, D)
        x = torch.cat([cls, x], dim=1)                     # (B, N+1, D)
        x = x + self.pos_embed                             # (B, N+1, D)
        return self.dropout(x)
```

### Multi-Head Self-Attention Block

```python
class MultiHeadSelfAttention(nn.Module):
    """Standard MHSA for ViT with pre-norm."""

    def __init__(self, embed_dim: int = 768,
                 num_heads: int = 12,
                 attn_dropout: float = 0.0,
                 proj_dropout: float = 0.0) -> None:
        super().__init__()
        assert embed_dim % num_heads == 0
        self.num_heads = num_heads
        self.head_dim = embed_dim // num_heads
        self.scale = self.head_dim ** -0.5

        self.qkv = nn.Linear(embed_dim, embed_dim * 3, bias=True)
        self.attn_drop = nn.Dropout(attn_dropout)
        self.proj = nn.Linear(embed_dim, embed_dim)
        self.proj_drop = nn.Dropout(proj_dropout)

    def forward(self, x: Tensor) -> tuple[Tensor, Tensor]:
        B, N, D = x.shape
        qkv = self.qkv(x).reshape(B, N, 3, self.num_heads,
                                    self.head_dim).permute(2, 0, 3, 1, 4)
        q, k, v = qkv.unbind(0)  # each: (B, heads, N, head_dim)

        attn = (q @ k.transpose(-2, -1)) * self.scale  # (B, heads, N, N)
        attn = attn.softmax(dim=-1)
        attn = self.attn_drop(attn)

        out = (attn @ v).transpose(1, 2).reshape(B, N, D)
        out = self.proj_drop(self.proj(out))
        return out, attn  # return attn for visualization
```

### Fine-Tuning ViT with Layer-wise LR Decay

```python
from torch.optim import AdamW


def build_vit_optimizer(model: nn.Module,
                          base_lr: float = 1e-3,
                          layer_decay: float = 0.65,
                          weight_decay: float = 0.05) -> AdamW:
    """
    Layer-wise learning rate decay for ViT fine-tuning.
    Deeper layers (closer to input) get lower LR.
    layer_decay=0.65 is the standard value from ViT fine-tuning papers.
    """
    num_layers = 12  # ViT-B has 12 transformer blocks

    # Assign each parameter a layer index
    param_groups: list[dict] = []
    for name, param in model.named_parameters():
        if not param.requires_grad:
            continue

        # Determine layer depth
        if "patch_embed" in name or "cls_token" in name or "pos_embed" in name:
            layer_id = 0
        elif "blocks" in name:
            # e.g. "blocks.5.attn.qkv.weight" → layer 5
            block_idx = int(name.split(".")[1])
            layer_id = block_idx + 1
        else:  # head
            layer_id = num_layers + 1

        lr_scale = layer_decay ** (num_layers + 1 - layer_id)
        effective_lr = base_lr * lr_scale

        # Separate weight decay: do NOT apply to biases, norms, embeddings
        wd = 0.0 if (param.ndim == 1 or "bias" in name or
                      "norm" in name or "embed" in name) else weight_decay

        param_groups.append({
            "params": [param],
            "lr": effective_lr,
            "weight_decay": wd,
        })

    return AdamW(param_groups)
```

#### Decoding the layer-wise LR decay formula

```
lr_scale     = layer_decay ** (num_layers + 1 - layer_id)
effective_lr = base_lr * lr_scale
```

**Put simply.** "Give the head the full learning rate, then multiply the rate by 0.65 for every step you walk backwards toward the input — so the layers that hold the most general pretrained knowledge barely move."

The exponent is a *distance from the top*, not a depth index. That single sign flip is what people get backwards, and getting it backwards trains the pretrained early layers hardest — the exact catastrophic-forgetting failure Pitfall 2 describes.

| Symbol | What it is |
|--------|------------|
| `layer_id` | 0 for patch embed / CLS / pos embed, `block_idx + 1` for blocks, 13 for the head |
| `num_layers` | Number of transformer blocks — 12 for ViT-B |
| `num_layers + 1 - layer_id` | Distance from the head; 0 at the head, 13 at the patch embedding |
| `layer_decay` | Per-step shrink factor, 0.65 is the standard value used throughout this file |
| `lr_scale` | Multiplier in `(0, 1]`; equals exactly 1.0 only at the head |
| `base_lr` | Top-of-stack rate the head actually gets (1e-3 in this code, 1e-4 in Q&A guidance) |

**Walk one example.** ViT-B, `base_lr = 1e-3`, `layer_decay = 0.65`, `num_layers = 12`:

```
  component            layer_id   exponent   lr_scale     effective_lr
  head                    13          0      1.000000       1.00e-03
  block 11                12          1      0.650000       6.50e-04
  block 5                  6          7      0.049022       4.90e-05
  block 0                  1         12      0.005688       5.69e-06
  patch/CLS/pos embed      0         13      0.003697       3.70e-06

  head LR / patch-embed LR = 1 / 0.65^13 = 270.5x
```

The head learns 270x faster than the patch embedding. That spread is the whole mechanism:
edge and texture detectors in the earliest layers are already correct for any natural image,
so they should drift only slightly, while the randomly initialized head must move a long way.
Set `layer_decay = 1.0` and every row collapses to 1.00e-03 — that is the uniform-LR setup
that scored 74% instead of 81% in Pitfall 2.

### CLIP Zero-Shot Classification

```python
import torch
import torch.nn.functional as F
from torch import Tensor


def clip_zero_shot_classify(image_features: Tensor,
                              class_names: list[str],
                              clip_model,
                              clip_tokenizer,
                              device: torch.device,
                              templates: list[str] = None) -> Tensor:
    """
    Zero-shot classification using CLIP text embeddings.
    image_features: (B, D) L2-normalized image embeddings
    Returns: (B, num_classes) probability distribution
    """
    if templates is None:
        templates = [
            "a photo of a {}.",
            "a photograph of a {}.",
            "an image of a {}.",
        ]

    # Build text embeddings for each class using multiple templates
    text_embeddings: list[Tensor] = []
    with torch.no_grad():
        for class_name in class_names:
            class_embeds = []
            for template in templates:
                prompt = template.format(class_name)
                tokens = clip_tokenizer(prompt).to(device)
                embed = clip_model.encode_text(tokens)
                embed = F.normalize(embed, dim=-1)
                class_embeds.append(embed)
            # Average over templates
            class_embed = torch.stack(class_embeds).mean(dim=0)
            class_embed = F.normalize(class_embed, dim=-1)
            text_embeddings.append(class_embed)

    text_emb_matrix = torch.cat(text_embeddings, dim=0)  # (C, D)

    # Cosine similarity = dot product of L2-normalized vectors
    logits = image_features @ text_emb_matrix.T  # (B, C)
    logits = logits * 100  # CLIP temperature scaling
    return F.softmax(logits, dim=-1)
```

#### Decoding the `* 100` temperature scaling

```
cos(u, v) = (u . v) / (||u|| ||v||)   in [-1, 1] ; with L2-normalized u, v this is just u . v
probs     = softmax(100 * cosine_similarities)
```

**The idea behind it.** "Cosine similarities are trapped in a one-unit-wide band, which softmax reads as 'everything is roughly equally likely' — so multiply by 100 to stretch the real gaps into a range softmax can actually express."

The `100` is `1 / temperature` with temperature 0.01, and CLIP learns it during training rather than fixing it. It is a readability knob on the probabilities, not new information — the argmax never changes.

| Symbol | What it is |
|--------|------------|
| `u . v` | Dot product; after `F.normalize` both vectors have length 1 so this *is* the cosine |
| `\|\|u\|\|` | L2 norm — divided out by normalization, which is why the code can skip it |
| `image_features @ text_emb_matrix.T` | The `(B, C)` matrix of every image against every class prompt |
| `100` | Inverse temperature; CLIP's learned logit scale, clamped near 100 |
| `softmax` | Converts the scaled scores into a distribution over the C classes |

**Walk one example.** Three class prompts scored against one image embedding:

```
  class            cosine    x 100
  "a photo of a cat"   0.31      31
  "a photo of a dog"   0.28      28
  "a photo of a car"   0.24      24

  softmax on raw cosines:    0.3445   0.3343   0.3212   <- looks like a coin flip
  softmax on x 100 scores:   0.9517   0.0474   0.0009   <- the 0.03 gap now reads as 95%
```

Both rows rank cat > dog > car identically, so accuracy is unchanged — but only the second
row is usable as a confidence score or a threshold. This is also why averaging the text
embeddings across templates (and re-normalizing, as the code does) matters so much: at a
100x logit scale, a 0.01 shift in cosine moves the probability by a full point.

---

## 7. Real-World Examples

**Google Search visual search**: ViT-G/14 (1.8B params) generates image embeddings for visual similarity search across Google Images. The model is distilled to a smaller ViT-S/16 for latency-constrained mobile search.

**OpenAI DALL-E 3**: uses a CLIP ViT image encoder as the image understanding backbone for the text-conditional diffusion model. CLIP embeddings condition the denoiser on semantic image content.

**Apple Face ID**: EfficientNet-like CNN was the original architecture; newer versions use ViT variants for better accuracy on diverse face conditions (glasses, partial occlusion, aging). Runs on Neural Engine in < 1ms.

**Meta content moderation**: CLIP ViT-L/14 embeds all uploaded images into a shared vision-language space. Text queries ("graphic violence", "nudity") are matched against image embeddings for zero-shot detection, bootstrapping a new category without per-category annotation.

**Tesla occupancy networks**: Swin Transformer backbone processes 8-camera surround view images to produce 3D occupancy voxel grids for the vehicle's world model. Hierarchical features from Swin's 4 stages are fused with BEV (bird's-eye-view) projections.

---

## 8. Tradeoffs

| Dimension | ViT (global attention) | Swin (local + shifted window) | CNN (ResNet/EfficientNet) |
|-----------|----------------------|-------------------------------|---------------------------|
| Inductive bias | None | Locality + hierarchy | Strong (locality, translation equiv.) |
| Data efficiency | Low | Medium | High |
| Scaling behavior | Excellent | Good | Diminishing returns |
| Dense prediction | Hard (single scale) | Native (multi-scale) | Native (FPN) |
| Attention complexity | O(n^2) | O(n · W^2) | N/A |
| Throughput (A100) | ViT-B: ~900 img/s | Swin-B: ~750 img/s | ResNet-50: ~1200 img/s |
| VRAM (224px, bs=32) | ~8GB | ~10GB | ~5GB |

| Model | Zero-shot ImageNet | Fine-tuned ImageNet | Requires large pretraining |
|-------|-------------------|--------------------|-----------------------------|
| CLIP ViT-L/14 | 76.2% | 87.8% | 400M pairs |
| DINOv2 ViT-g/14 | — (linear probe 86.5%) | 88.0% | 142M curated |
| ViT-H/14 (sup) | N/A | 88.6% | JFT-3B (3B images) |

---

## 9. When to Use / When NOT to Use

**Use ViT (ViT-B/16 or larger) when**:
- Strong pretrained checkpoint is available (ImageNet-21k, CLIP, DINOv2).
- Task benefits from long-range global context (whole-image reasoning, document understanding).
- You have a large fine-tuning dataset (> 100k images) to exploit the model's capacity.

**Use Swin Transformer when**:
- You need multi-scale features for detection or segmentation (no FPN needed).
- Input resolution is high (> 384px) — local windows make it tractable.
- You want a drop-in CNN replacement backbone for detection/segmentation frameworks.

**Use CLIP when**:
- You need zero-shot or few-shot classification across many categories.
- Your labels are described in natural language (no hard class boundaries).
- Building a semantic image search or recommendation system.

**Do NOT use ViT (from scratch) when**:
- Dataset is small (< 100k images) and no pretrained checkpoint is available.
- Latency budget is under 5ms on mobile — use MobileNetV3 or EfficientNet-Lite instead.
- Memory is limited — ViT-B at 224px requires ~4GB VRAM for inference at batch 64.

---

## 10. Common Pitfalls

**Pitfall 1: Fine-tuning at wrong resolution without interpolating position embeddings**
ViT-B/16 pretrained at 224x224 has position embeddings of shape (197, 768). Fine-tuning at 384x384 produces 576 patches — the position embeddings size mismatch causes a runtime error or, if carelessly ignored, produces random positional encodings for new positions. Fix: bicubic interpolation of the 2D position embeddings. In timm, `model.set_input_size((384, 384))` handles this automatically.

**Pitfall 2: Not using layer-wise LR decay for ViT fine-tuning**
Fine-tuning all ViT layers with the same learning rate causes catastrophic forgetting of early-layer representations. Early attention heads that detect edges and textures get overwritten. A team fine-tuning ViT-B for medical imaging with uniform LR 1e-4 got 74%; adding layer decay (0.65 per layer) improved it to 81% with no other change.

**Pitfall 3: Underestimating CLIP's sensitivity to prompt engineering**
CLIP zero-shot performance varies dramatically with prompt phrasing. A team got 61% zero-shot accuracy with class names alone ("cat", "dog") and 76% with "a photo of a [class]". Using an ensemble of 80 ImageNet templates (OpenAI standard) raised it further to 80%. For production, always test multiple prompt templates and ensemble their text embeddings.

**Pitfall 4: Ignoring attention map artifacts in CLS token**
ViT CLS tokens sometimes attend to background tokens rather than the object of interest, especially when trained on datasets with cluttered backgrounds. This causes GradCAM-equivalent visualizations to highlight uninformative regions. DINOv2 and models trained with DINO self-supervision have cleaner attention maps that genuinely focus on object regions.

**Pitfall 5: BatchNorm in ViT (do not)**
Some engineers port CNN code patterns to ViT and accidentally add BatchNorm layers. ViT uses Layer Normalization, not Batch Normalization. BN computes statistics over the batch dimension, which is problematic with variable sequence lengths. LN computes statistics over the feature dimension per sample. Mixing the two causes subtle numerical instabilities and 2-4% accuracy drops.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| timm (Hugging Face) | 700+ pretrained ViT/Swin/CNN models | Gold standard for CV model zoo |
| transformers (HF) | CLIP, BEiT, ViT with HF API | Best for CLIP and NLP-aligned models |
| open_clip | OpenCLIP (open-source CLIP reproduction) | More model variants than official CLIP |
| flash-attention | Efficient attention (FlashAttention-2) | 2-4x faster, sub-linear memory for long sequences |
| xformers | Memory-efficient attention ops | Alternative to FlashAttention |
| DINOv2 (Meta) | Best general-purpose visual features | Available via torch.hub |
| torchvision.transforms.v2 | Augmentation for ViT (MixUp, CutMix) | Use v2 for modern augmentation API |
| Triton Inference Server | ViT production serving | TensorRT FP16 export recommended |
| ONNX Runtime | CPU/edge ViT inference | DeiT-S achieves ~40ms CPU with ONNX |

---

## 12. Interview Questions with Answers

**Q: How does ViT convert an image into a sequence of tokens?**
ViT splits the input image into a grid of non-overlapping P×P patches. Each patch is flattened to a 1D vector of length P×P×C (for a 3-channel image). This vector is projected to the embedding dimension D using a learnable linear layer (equivalently, a Conv2d with kernel_size=P, stride=P). A learnable CLS token is prepended, and learnable 1D positional embeddings are added element-wise. For a 224×224 image with P=16: num_patches = 196, sequence length = 197 (including CLS).

**Q: Why does ViT need positional embeddings and what happens without them?**
Self-attention is permutation-invariant: the attention mechanism treats the input as a set, not a sequence. Without positional embeddings, shuffling all 196 patches produces the exact same output, which means the model cannot learn where objects are relative to each other. Positional embeddings inject location information. Without them, ViT performs ~15% worse on ImageNet. ViT uses learnable 1D embeddings; Swin uses relative position bias; modern models often use 2D sinusoidal or RoPE (Rotary Position Embedding).

**Q: What is the CLS token and why is it used for classification?**
The CLS (classification) token is a learnable vector prepended to the patch sequence before the transformer encoder. Through self-attention, it is free to attend to all patches and aggregate information from the entire image. After L transformer blocks, the CLS token's output is a global representation of the image, which is fed into the classification MLP head. This design is borrowed from BERT. An alternative (used in some ViT variants) is global average pooling over all patch tokens, which sometimes gives marginally better results.

**Q: What makes Swin Transformer more suitable than ViT for detection and segmentation?**
ViT has a single-scale feature map (all patches at the same resolution throughout), making it hard to build an FPN-style multi-scale hierarchy. Swin uses patch merging between stages to progressively reduce resolution and increase channels, producing hierarchical features at 4 scales — identical in structure to ResNet's C2–C5 stages. Detection and segmentation frameworks (Faster R-CNN, Mask R-CNN) can plug Swin in as a drop-in CNN backbone without architectural changes. Swin also uses local windowed attention (O(n·W²) vs O(n²)), making it tractable for high-resolution inputs.

**Q: How does CLIP enable zero-shot classification?**
CLIP trains a visual encoder and a text encoder jointly to produce aligned embeddings: matching image-text pairs are pulled together; non-matching pairs are pushed apart (InfoNCE loss). At inference, for each class name, a text prompt ("a photo of a [class]") is encoded by the text encoder. An image is encoded by the image encoder. The predicted class is the one whose text embedding has the highest cosine similarity to the image embedding. This requires no task-specific fine-tuning — the model generalizes to any class that can be described in text.

**Q: What is DeiT and how does it train efficiently on ImageNet-1k?**
DeiT (Data-efficient Image Transformers, 2021) achieves ViT-level accuracy using only ImageNet-1k (1.28M images) by introducing: (1) knowledge distillation — a DeiT distillation token learns from a CNN teacher's hard label in parallel with the CLS token's classification loss; (2) stochastic depth (drop path) regularization — randomly drops entire residual branches during training, acting as ensemble training; (3) strong augmentation — RandAugment, CutMix, Mixup, and repeated augmentation. The result: DeiT-B reaches 83.4% without any external data.

**Q: What is the computational complexity of self-attention and why is it a problem for high-resolution images?**
Self-attention has O(n²·d) complexity where n is the sequence length (number of patches) and d is the embedding dimension. For 224×224 with patch size 16: n=196, manageable. For 512×512 with patch size 8: n=4096, the attention matrix is 4096×4096 — ~16M elements per head, making it 44× more expensive than 224×224. FlashAttention reduces memory from O(n²) to O(n) by tiling the computation, but the FLOPs are still O(n²). Swin's local window attention reduces this to O(n·W²) where W=7 is the window size.

**Q: How do you fine-tune a pretrained ViT on a small custom dataset (< 10k images)?**
Use a heavily pretrained checkpoint (ImageNet-21k, CLIP, DINOv2) rather than ImageNet-1k only. Freeze the backbone and train only the head for the first 10 epochs (linear probing) to establish a good starting point without corrupting pretrained features. Then unfreeze the full model with layer-wise LR decay (decay=0.65, base LR 1e-4) for 50–100 epochs. Use aggressive augmentation (RandAugment, CutMix) and dropout (p=0.2 in the head). Stochastic depth (drop_path_rate=0.1) reduces overfitting. Expect 3–5% less accuracy than equivalent data with a CNN due to weaker inductive bias.

**Q: What is FlashAttention and how does it help ViT training?**
FlashAttention (Dao et al., 2022) is an IO-aware exact attention algorithm that tiles the attention computation to keep data in GPU SRAM, avoiding expensive HBM reads/writes of the O(n²) attention matrix. FlashAttention-2 achieves ~2x throughput over standard PyTorch attention and reduces peak memory from O(n²) to O(n). For ViT-L fine-tuning at 384×384 resolution (n=576), FlashAttention reduces peak VRAM by ~4× and training time by ~2×. It is enabled via `torch.nn.functional.scaled_dot_product_attention` in PyTorch 2.0+.

**Q: What is the difference between relative and absolute positional encodings?**
Absolute positional encodings assign a fixed or learned embedding to each position index, which does not generalize to sequence lengths unseen during training. If ViT is pretrained at 224px and fine-tuned at 384px, absolute encodings must be interpolated. Relative positional encodings encode the offset between pairs of tokens rather than absolute positions. Swin uses relative position bias: a learnable bias matrix indexed by (row_offset, col_offset) between token pairs. Relative encodings generalize better to different resolutions and naturally capture spatial relationships. RoPE (Rotary Position Embedding) is the latest approach, used in modern language models and increasingly in vision models.

**Q: How does the Swin Transformer's shifted window mechanism work?**
In each Swin block pair, Window Multi-head Self-Attention (W-MSA) computes attention within fixed M×M windows (M=7). The next block uses Shifted Window Multi-head Self-Attention (SW-MSA), where windows are shifted by (M/2, M/2) pixels before partitioning. This means tokens at window boundaries in W-MSA are now in the interior of a shifted window, enabling cross-window information flow. Efficient cyclic shift + masking implements this without padding overhead. Without shifted windows, the model cannot capture global context across arbitrary token pairs.

**Q: What is DINOv2 and why does it produce the best general-purpose visual features?**
DINOv2 (Oquab et al., 2023) trains ViT using a self-supervised DINO objective on a curated 142M image dataset. The curation pipeline (deduplication, quality filtering, clustering) is as important as the training objective. The key insight: with a large, diverse, curated dataset and a self-distillation objective, ViTs learn representations that are superior to supervised ImageNet training for downstream tasks. DINOv2 ViT-g/14 achieves 86.5% linear probing on ImageNet — better than any supervised model on a linear probe, meaning the features encode more linearly separable information. Depth estimation, segmentation, and semantic correspondence emerge without any label supervision.

**Q: How does ViT attention differ from CNN feature maps in terms of interpretability?**
CNN feature maps can be visualized with Grad-CAM: compute gradients of the target class score with respect to the last convolutional feature map and weight-sum the feature channels. This produces a heatmap highlighting discriminative regions. ViT attention maps from the last transformer block (especially the CLS token's attention to patch tokens) can sometimes reveal the attended regions, but they are noisier and less reliable than Grad-CAM. DINO-pretrained ViTs have cleaner attention that genuinely segments objects. For production explainability with ViTs, Attention Rollout (averaging attention maps across layers and heads) or GradCAM applied to the patch tokens gives more reliable attribution.

**Q: What tradeoffs exist between patch size 16×16 vs 8×8 in ViT?**
Patch size 16×16 on 224×224 input gives 196 tokens — manageable attention cost (O(196²) per head). Patch size 8×8 gives 784 tokens — 16× more expensive attention. Smaller patches provide finer spatial resolution, which improves performance on dense prediction tasks and on images with small objects. ViT-B/8 outperforms ViT-B/16 on ImageNet by ~2% top-1 but requires 4× more computation and memory. For classification, 16×16 is standard. For segmentation and detection where spatial precision matters, 8×8 or Swin's hierarchical approach is preferred.

**Q: Why do Vision Transformers require more training data than CNNs?**
Vision Transformers lack the spatial inductive biases — locality and translation equivariance — that CNNs bake into their architecture, so they must learn those patterns from data instead. A convolution kernel hard-codes that nearby pixels are related and that a feature detector should behave identically everywhere in the image; ViT starts with none of this and can attend from any patch to any other from the first layer, giving more flexibility but no built-in priors. Trained from scratch on ImageNet-1k, ViT trails a comparable ResNet by several points; the advantage only appears once pretraining reaches ImageNet-21k (14M) or JFT (300M+) scale. In practice, always start from a strong pretrained checkpoint rather than training ViT from scratch on under 1M images.

**Q: Can you replace the CLS token with global average pooling, and does it matter?**
Yes, global average pooling over all patch tokens is a drop-in alternative to the CLS token and often gives marginally better accuracy. The original ViT used a BERT-style CLS token, but ablations show that mean-pooling the final patch tokens works about as well or slightly better, provided the head learning rate is tuned separately (GAP prefers a lower head LR). DINOv2 and several modern ViTs pool patch tokens rather than relying on a CLS token. The tradeoff: the CLS token gives one natural aggregation point for attention-map visualization, while GAP spreads the signal across all tokens and can be more robust when the CLS token attends to background clutter.

**Q: How do Vision Transformers scale with more data and parameters compared to CNNs?**
ViTs keep gaining accuracy as data and model size grow, while CNNs hit diminishing returns — ViT-H/14 pretrained on JFT-3B reaches 88.6% ImageNet top-1. This favorable scaling is the core reason ViT displaced CNNs at the high end: doubling parameters and data yields a predictable gain that does not plateau as early as the ResNet/EfficientNet families. The flip side is data hunger in the low regime — the same architecture that scales beautifully to billions of images is worse than a ResNet on 100k. Choose ViT when you can supply scale (data, compute, or a large pretrained checkpoint); choose a CNN when you cannot.

---

## 13. Best Practices

1. Always start from the strongest available pretrained checkpoint: for general vision tasks, DINOv2 ViT-L/14; for vision-language tasks, CLIP ViT-L/14 or OpenCLIP ViT-G/14. Training ViT from scratch on fewer than 1M images is almost never justified.
2. Use layer-wise LR decay with decay=0.65 for all ViT fine-tuning. This single change routinely gives 3–7% improvement over uniform LR.
3. Enable FlashAttention-2 (via `torch.nn.functional.scaled_dot_product_attention` in PyTorch 2.0+ or `xformers`) to reduce memory and speed up training by 2×.
4. For CLIP-based classification, always use an ensemble of 7–80 text prompt templates and average their text embeddings. The OpenAI ImageNet template set is publicly available and provides +5% over single templates.
5. When fine-tuning ViT at a different resolution from pretraining, interpolate positional embeddings: use `timm`'s built-in `resize_pos_embed` utility or call `model.set_input_size(new_size)`.
6. Apply stochastic depth (drop path) regularization: `drop_path_rate=0.1` for ViT-S, 0.2 for ViT-B, 0.3 for ViT-L. This significantly reduces overfitting on small-to-medium fine-tuning sets.
7. Use EMA (Exponential Moving Average) of model weights during fine-tuning: decay=0.9999, evaluated every epoch. EMA typically improves top-1 accuracy by 0.2–0.5% with zero inference overhead.
8. For Swin-based detection, the recommended fine-tuning schedule is 12 epochs (1x schedule) with AdamW, LR 1e-4, weight decay 0.05, cosine schedule. Adding a 36-epoch schedule rarely improves by more than 1 mAP for most practical datasets.
9. Monitor attention entropy during training — if all attention heads collapse to attending to a single patch (entropy near 0), the model is stuck. Reduce learning rate or add more dropout.
10. For production serving of ViT-B: export to ONNX, run through TensorRT FP16 on A100. Expected throughput: ~1400 images/sec at batch=64 vs ~900 imgs/sec in PyTorch eager.

---

## 14. Case Study

**Problem**: A fashion e-commerce platform needs to retrieve visually similar products from a catalog of 5M items. Queries come from user-uploaded photos (varied backgrounds, lighting, angles). Target: top-10 recall > 85% on a benchmark of 10k query-gallery pairs.

**Architecture**: CLIP ViT-L/14 as the image encoder (fine-tuned for fashion). The text encoder is discarded at inference. Image embeddings are stored in a FAISS IVF-PQ index for approximate nearest neighbor search.

**Fine-tuning**: Triplet loss with hard negative mining on 2M (anchor, positive, negative) triplets. Anchor: product catalog image; positive: user photo of the same product; negative: hard negative from same category (different product). LR 5e-5 with layer decay 0.75, 30 epochs, batch 512 on 8× A100.

**Baseline** (CLIP zero-shot, no fine-tuning): top-10 recall = 62%. After fine-tuning: 83%.

**Problem**: 62→83% leaves a gap to target 85%. Analysis: failures concentrated in subcategories with high visual similarity (blue jeans, plain white shirts). The model could not distinguish subtle differences (pocket style, fabric texture).

**Fix**: Added a second stage of contrastive fine-tuning on sub-category hard negatives, mined by running k-NN at the sub-category level. Recall reached 87%.

**Production deployment**: ViT-L/14 encoder exported to TensorRT FP16, 8ms per image on T4 GPU. FAISS IVFFlat with nlist=4096, nprobe=64: search over 5M vectors in ~3ms with recall@100 = 99.5% vs exact search. End-to-end query latency P99 = 25ms. Index stored in 5GB (d=768 float16 vectors, 5M items = 7.2GB raw, compressed by IVF-PQ to 5GB).

### Decoding the index-size arithmetic

```
raw index bytes = num_items x d x bytes_per_component
vectors scanned = (nprobe / nlist) x num_items
```

**What the formula is telling you.** "An embedding index costs one flat multiplication — how many things you stored, times how wide each vector is, times how many bytes each number takes — and IVF search cost is just the fraction of partitions you agree to open."

Both numbers are fixed before you write a line of serving code, which makes embedding width and dtype capacity-planning decisions rather than modelling ones.

| Symbol | What it is |
|--------|------------|
| `num_items` | Catalog size — 5M products here |
| `d` | Embedding dimension, 768 (ViT-L/14 projects to this before indexing) |
| `bytes_per_component` | 2 for float16, 4 for float32 — halving this halves the index |
| `nlist` | Number of IVF partitions the vectors are clustered into (4096) |
| `nprobe` | How many of those partitions a query actually scans (64) |
| `nprobe / nlist` | Fraction of the catalog touched per query — the recall/latency dial |

**Walk one example.** The 7.2GB and the ~3ms search quoted just above:

```
  raw storage
    5,000,000 x 768 x 2 bytes = 7,680,000,000 bytes
                              = 7.68 GB  (decimal)
                              = 7.15 GiB (binary)   <- the "7.2GB" figure
    float32 instead of float16 would be 15.36 GB.
    IVF-PQ compresses 7.2 -> 5.0, a 1.44x reduction.

  search work
    nprobe / nlist = 64 / 4096         = 1/64 = 1.56% of partitions
    vectors compared = 5,000,000 / 64  = 78,125 instead of 5,000,000
                                       = 64x less work than exact search
```

Scanning 1.56% of the catalog still returns 99.5% recall@100 because IVF clusters put
near-duplicates in the same partition, so the 64 probed lists contain nearly all true
neighbours. Push `nprobe` to 128 and you double the scan for a fraction of a recall point;
drop it to 16 and recall falls off a cliff — that tradeoff, not the encoder, is what sets
the P99 = 25ms budget.
