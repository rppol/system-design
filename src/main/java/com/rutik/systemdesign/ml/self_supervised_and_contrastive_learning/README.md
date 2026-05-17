# Self-Supervised and Contrastive Learning

## 1. Concept Overview

Self-supervised learning (SSL) is a machine learning paradigm where supervisory signals are automatically generated from the input data, eliminating the need for human-annotated labels. The model learns general-purpose representations through pretext tasks, which are auxiliary objectives designed so that solving them requires understanding the underlying structure of the data.

SSL is the foundation of modern pre-trained models: BERT, GPT, SimCLR, MAE, and DINO all use self-supervised objectives. The learned representations are then transferred to downstream tasks via linear evaluation or fine-tuning, often matching or exceeding supervised baselines with a fraction of labeled data.

Note: this module covers the general SSL landscape — theoretical foundations, NLP SSL, graph SSL, and tabular SSL. Vision-specific SSL (SimCLR, MAE, DINO, MoCo) is covered in `computer_vision/self_supervised_vision.md`.

---

## 2. Intuition

One-line analogy: SSL is how humans learn language as children — by predicting what comes next, filling in missing words, and understanding context — without anyone labeling each sentence with its meaning.

Mental model: create a game the model must play using only the raw data. The game is designed so the model cannot win by memorizing patterns — it must understand the data's structure. The understanding generalizes.

Why it matters: labeled data is expensive and scarce. ImageNet has 1.2M labeled images. The internet has trillions of unlabeled images. SSL unlocks this data. GPT-4 was trained on trillions of tokens with no human labels — the next-token prediction objective is self-supervised.

Key insight: the pretext task is a proxy. What matters is not solving the pretext task well, but what representations are learned in the process. A poorly designed pretext task can be solved by shortcuts (e.g., detecting JPEG artifacts) that do not generalize.

---

## 3. Core Principles

**Pretext task:** the auxiliary task used to generate training signal from unlabeled data. Must be solvable only by learning useful representations.

**Downstream task:** the real task of interest. The quality of SSL is measured by how well the learned representations perform on downstream tasks with limited labels.

**Data augmentation:** for contrastive learning, augmentation defines what "similar" means. If crop + color jitter produces two views that must match, the model learns to be invariant to crops and color — a useful visual prior. Poor augmentation choices can cause the model to ignore task-relevant features.

**Representation collapse:** contrastive methods without proper design collapse to constant representations (all embeddings identical). Solutions: negative samples (SimCLR), momentum encoder (MoCo), stop-gradient + predictor (BYOL), centering + sharpening (DINO).

**Linear evaluation protocol:** freeze the encoder after SSL pretraining, train only a linear classifier on top. This measures the quality of representations independent of fine-tuning capability. A high linear evaluation score indicates the encoder has separated class structure without labels.

**InfoMax principle:** good representations maximize mutual information I(Z; X) between the representation Z and input X, while discarding task-irrelevant information (noise). Contrastive loss is a lower bound on mutual information.

---

## 4. Types / Architectures / Strategies

**Taxonomy of SSL by mechanism:**

| Category | Mechanism | Examples | Negatives Needed |
|---|---|---|---|
| Predictive (masked) | Mask + predict | BERT (MLM), MAE, BEiT | No |
| Autoregressive | Predict next token/patch | GPT, VideoGPT | No |
| Contrastive | Pull positives, push negatives | SimCLR, MoCo, CPC | Yes |
| Non-contrastive | Asymmetric networks or constraints | BYOL, DINO, Barlow Twins | No |
| Generative | Reconstruct input | VAE, diffusion pretraining | No |
| Graph-specific | Graph augmentations | DGI, GraphCL, GRACE | Depends |
| Tabular-specific | Feature corruption/subset | SCARF, SubTab, VIME | Depends |

**NLP SSL architectures:**

- BERT (MLM): mask 15% of tokens randomly (80% replace with [MASK], 10% random word, 10% unchanged). Predict original tokens at masked positions. Learns bidirectional context.
- GPT (CLM): predict token t+1 given tokens 1..t. Causal attention mask. Naturally scales to any sequence length. Generative capability is a bonus.
- ELECTRA: trains a small generator (BERT-like) to produce plausible replacements. A discriminator (the actual model) predicts which tokens were replaced. All positions are supervised — 4x more sample-efficient than BERT.
- SpanBERT: mask contiguous spans (2–10 tokens) instead of random tokens. Span boundary objective. Better for span-extraction tasks (QA, NER).

**Contrastive learning objectives:**

InfoNCE loss (also called NT-Xent in SimCLR):
```
L = -log( exp(sim(z_i, z_j) / tau) / sum_{k=1}^{2N} 1[k != i] exp(sim(z_i, z_k) / tau) )
```
Where sim is cosine similarity, tau is temperature (0.07–0.2), and the 2N denominator includes 2(N-1) negatives from the batch plus the positive. Lower temperature sharpens the distribution — the model is penalized more for near-misses.

**Graph SSL:**

- DGI (Deep Graph Infomax): maximize mutual information between node embeddings and a global graph summary. Negatives: corrupted graph (shuffle node features). Does not need class labels.
- GraphCL: apply four augmentation types (node dropout, edge dropout, attribute masking, subgraph sampling) to create two views of the same graph. Apply SimCLR contrastive loss at graph level.
- GRACE: node-level contrastive learning with two augmented graph views. Same node in both views is positive pair; other nodes are negatives.

**Tabular SSL:**

- SCARF: randomly corrupt a fraction (20–40%) of features by sampling from their marginal distributions. Predict which features were corrupted. Learns feature correlations.
- SubTab: split features into two subsets (views). Learn representations that reconstruct the other subset from each view.

---

## 5. Architecture Diagrams

```
Contrastive Learning (SimCLR-style)
=====================================

  Input x
    |
  [Augmentation 1]    [Augmentation 2]
    |                       |
  View x_i              View x_j
    |                       |
  Encoder f(.)           Encoder f(.)    <- shared weights
    |                       |
  h_i [D]               h_j [D]
    |                       |
  Projector g(.)         Projector g(.)  <- MLP, 2 layers, 128-dim output
    |                       |
  z_i [128]             z_j [128]
         \               /
          [InfoNCE Loss]
           positive pair (z_i, z_j) vs
           2(N-1) negatives from batch


BERT Masked Language Modeling
===============================

  Input:   "The cat sat on the [MASK] ."
             |    |   |   |    |       |
           tok1 tok2 tok3 tok4 [M]   tok6

  Transformer (bidirectional self-attention)
             |    |   |   |    |       |
           h1   h2  h3  h4   h5     h6

  Loss: cross-entropy at masked position only
  Predict: "mat" at position 5


BYOL (No Negatives)
=====================

  Online Network:                  Target Network:
    x --[aug]--> f_o ---> g_o         x --[aug]--> f_t
                   \                          |
                    q_o (predictor)        z_t (stop-grad)
                         \                   |
                          MSE( q_o(z_o), z_t )

  Target network: exponential moving average of online network
    theta_t = m * theta_t + (1-m) * theta_o   (m = 0.996)

  Asymmetry (predictor on online only) + EMA prevents collapse.


Graph Contrastive Learning (GraphCL)
======================================

  Original Graph G
     /           \
  [Aug 1]       [Aug 2]
  (edge drop)  (attr mask)
     |               |
  View G1        View G2
     |               |
  GNN Encoder    GNN Encoder   <- shared
     |               |
  h_G1           h_G2          <- graph-level pooling
     |               |
  Projector      Projector
     |               |
  z_G1           z_G2
         \       /
       NT-Xent loss
```

---

## 6. How It Works — Detailed Mechanics

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset
from typing import Tuple, Optional
import numpy as np


# ── NT-Xent (InfoNCE) Loss ───────────────────────────────────────────────────

class NTXentLoss(nn.Module):
    """
    NT-Xent loss from SimCLR.
    Batch of N samples produces 2N augmented views.
    For view i, the positive is view i+N (or i-N).
    All other 2(N-1) views are negatives.
    """
    def __init__(self, temperature: float = 0.07) -> None:
        super().__init__()
        self.temperature = temperature

    def forward(self, z1: torch.Tensor, z2: torch.Tensor) -> torch.Tensor:
        """
        z1, z2: [N, D] — L2-normalized projection vectors
        """
        N = z1.size(0)
        # Concatenate both views: [2N, D]
        z = torch.cat([z1, z2], dim=0)

        # Similarity matrix: [2N, 2N]
        sim = torch.mm(z, z.T) / self.temperature

        # Mask diagonal (self-similarity)
        mask = torch.eye(2 * N, dtype=torch.bool, device=z.device)
        sim = sim.masked_fill(mask, -1e9)

        # Positive pairs: (i, i+N) and (i+N, i)
        # For row i (0..N-1), positive is at col i+N
        # For row i+N (N..2N-1), positive is at col i
        labels = torch.cat([
            torch.arange(N, 2 * N),   # positives for first N rows
            torch.arange(0, N),        # positives for last N rows
        ]).to(z.device)

        loss = F.cross_entropy(sim, labels)
        return loss


# ── SimCLR-style SSL encoder ─────────────────────────────────────────────────

class SimCLR(nn.Module):
    def __init__(
        self,
        encoder: nn.Module,    # backbone (ResNet, transformer, etc.)
        encoder_dim: int,      # output dim of encoder
        projection_dim: int = 128,
        hidden_dim: int = 2048,
    ) -> None:
        super().__init__()
        self.encoder = encoder
        # 2-layer MLP projector (discarded after pretraining)
        self.projector = nn.Sequential(
            nn.Linear(encoder_dim, hidden_dim),
            nn.BatchNorm1d(hidden_dim),
            nn.ReLU(inplace=True),
            nn.Linear(hidden_dim, projection_dim),
        )

    def forward(
        self, x1: torch.Tensor, x2: torch.Tensor
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        h1 = self.encoder(x1)   # [N, encoder_dim]
        h2 = self.encoder(x2)
        z1 = F.normalize(self.projector(h1), dim=1)  # [N, projection_dim]
        z2 = F.normalize(self.projector(h2), dim=1)
        return z1, z2


# ── BYOL (Bootstrap Your Own Latent) — no negatives ─────────────────────────

class BYOL(nn.Module):
    def __init__(
        self,
        encoder: nn.Module,
        encoder_dim: int,
        projection_dim: int = 256,
        prediction_dim: int = 128,
        ema_decay: float = 0.996,
    ) -> None:
        super().__init__()
        # Online network
        self.online_encoder = encoder
        self.online_projector = self._build_mlp(encoder_dim, projection_dim)
        self.online_predictor = self._build_mlp(projection_dim, prediction_dim)

        # Target network: separate copy, updated via EMA (not gradient)
        import copy
        self.target_encoder = copy.deepcopy(encoder)
        self.target_projector = copy.deepcopy(self.online_projector)

        # Freeze target network
        for p in self.target_encoder.parameters():
            p.requires_grad_(False)
        for p in self.target_projector.parameters():
            p.requires_grad_(False)

        self.ema_decay = ema_decay

    @staticmethod
    def _build_mlp(in_dim: int, out_dim: int) -> nn.Sequential:
        return nn.Sequential(
            nn.Linear(in_dim, 4096),
            nn.BatchNorm1d(4096),
            nn.ReLU(inplace=True),
            nn.Linear(4096, out_dim),
        )

    @torch.no_grad()
    def update_target(self) -> None:
        """EMA update: theta_target = m * theta_target + (1-m) * theta_online"""
        m = self.ema_decay
        for p_o, p_t in zip(
            self.online_encoder.parameters(), self.target_encoder.parameters()
        ):
            p_t.data = m * p_t.data + (1 - m) * p_o.data
        for p_o, p_t in zip(
            self.online_projector.parameters(), self.target_projector.parameters()
        ):
            p_t.data = m * p_t.data + (1 - m) * p_o.data

    def forward(
        self, x1: torch.Tensor, x2: torch.Tensor
    ) -> torch.Tensor:
        # Online: encode -> project -> predict
        z1_online = self.online_projector(self.online_encoder(x1))
        z2_online = self.online_projector(self.online_encoder(x2))
        p1 = self.online_predictor(z1_online)  # predictor only on online
        p2 = self.online_predictor(z2_online)

        # Target: encode -> project (stop gradient)
        with torch.no_grad():
            z1_target = self.target_projector(self.target_encoder(x1))
            z2_target = self.target_projector(self.target_encoder(x2))

        # Symmetrized MSE loss (on L2-normalized vectors)
        def regression_loss(q: torch.Tensor, z: torch.Tensor) -> torch.Tensor:
            q = F.normalize(q, dim=1)
            z = F.normalize(z, dim=1)
            return 2 - 2 * (q * z.detach()).sum(dim=1).mean()

        loss = regression_loss(p1, z2_target) + regression_loss(p2, z1_target)
        return loss


# ── SCARF: Self-supervised Contrastive Learning for tabular data ──────────────

class SCARFEncoder(nn.Module):
    def __init__(self, input_dim: int, emb_dim: int = 256,
                 hidden_dim: int = 256, n_layers: int = 4) -> None:
        super().__init__()
        layers: list[nn.Module] = []
        in_d = input_dim
        for _ in range(n_layers):
            layers += [nn.Linear(in_d, hidden_dim), nn.ReLU()]
            in_d = hidden_dim
        layers.append(nn.Linear(hidden_dim, emb_dim))
        self.net = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)


class SCARF(nn.Module):
    """
    SCARF: corrupt a fraction of features by sampling from marginal distributions.
    Apply contrastive loss between clean and corrupted views.
    """
    def __init__(
        self,
        encoder: SCARFEncoder,
        corruption_rate: float = 0.6,
        projection_dim: int = 128,
    ) -> None:
        super().__init__()
        self.encoder = encoder
        self.corruption_rate = corruption_rate
        emb_dim = encoder.net[-1].out_features  # type: ignore[union-attr]
        self.projector = nn.Sequential(
            nn.Linear(emb_dim, emb_dim),
            nn.ReLU(),
            nn.Linear(emb_dim, projection_dim),
        )

    def corrupt(
        self, x: torch.Tensor, marginals: torch.Tensor
    ) -> torch.Tensor:
        """
        x: [N, D] — original batch
        marginals: [M, D] — random reference batch drawn from dataset
        For each sample and feature, with probability corruption_rate,
        replace the feature value with a random sample from marginals.
        """
        N, D = x.shape
        mask = torch.rand(N, D, device=x.device) < self.corruption_rate
        # Sample marginals: for each corrupted position, pick a random row
        idx = torch.randint(0, marginals.size(0), (N,), device=x.device)
        x_corrupted = x.clone()
        x_corrupted[mask] = marginals[idx].expand_as(x)[mask]
        return x_corrupted

    def forward(
        self, x: torch.Tensor, marginals: torch.Tensor
    ) -> torch.Tensor:
        x_corrupted = self.corrupt(x, marginals)
        z_clean = F.normalize(self.projector(self.encoder(x)), dim=1)
        z_corrupt = F.normalize(self.projector(self.encoder(x_corrupted)), dim=1)
        loss_fn = NTXentLoss(temperature=0.07)
        return loss_fn(z_clean, z_corrupt)


# ── Graph SSL: DGI (Deep Graph Infomax) ──────────────────────────────────────

class DGI(nn.Module):
    """
    Maximize mutual information between node embeddings and global graph summary.
    Negatives: corrupted graph (shuffled node features).
    """
    def __init__(self, gnn_encoder: nn.Module, hidden_dim: int) -> None:
        super().__init__()
        self.encoder = gnn_encoder
        # Readout: mean of node embeddings -> graph summary
        self.discriminator = nn.Bilinear(hidden_dim, hidden_dim, 1)

    def forward(
        self,
        x: torch.Tensor,           # [N, F] node features
        edge_index: torch.Tensor,
    ) -> torch.Tensor:
        # Positive: real graph
        h_pos = self.encoder(x, edge_index)               # [N, hidden]
        s = h_pos.mean(dim=0, keepdim=True)               # [1, hidden] summary

        # Negative: shuffle node features (breaks node-graph alignment)
        perm = torch.randperm(x.size(0))
        h_neg = self.encoder(x[perm], edge_index)         # [N, hidden]

        # Discriminate: does (node_emb, graph_summary) come from real graph?
        pos_scores = self.discriminator(h_pos, s.expand_as(h_pos))  # [N, 1]
        neg_scores = self.discriminator(h_neg, s.expand_as(h_neg))  # [N, 1]

        pos_loss = F.binary_cross_entropy_with_logits(
            pos_scores, torch.ones_like(pos_scores)
        )
        neg_loss = F.binary_cross_entropy_with_logits(
            neg_scores, torch.zeros_like(neg_scores)
        )
        return pos_loss + neg_loss


# ── Training loop skeleton ────────────────────────────────────────────────────

def ssl_pretrain(
    model: SimCLR,
    loader: DataLoader,
    epochs: int = 200,
    lr: float = 3e-4,
    temperature: float = 0.07,
) -> None:
    criterion = NTXentLoss(temperature=temperature)
    optimizer = torch.optim.Adam(model.parameters(), lr=lr,
                                 weight_decay=1e-6)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer, T_max=epochs
    )

    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        for x1, x2 in loader:   # augmented view pairs
            z1, z2 = model(x1, x2)
            loss = criterion(z1, z2)
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
        scheduler.step()
        if epoch % 10 == 0:
            print(f"Epoch {epoch:03d} | Loss: {total_loss / len(loader):.4f} "
                  f"| LR: {scheduler.get_last_lr()[0]:.6f}")
```

**Key hyperparameters and their effects:**
- Temperature tau: lower (0.05) sharpens distribution, harder negatives dominate, risk of unstable gradients. Higher (0.2) softens, easier optimization but weaker representation.
- Batch size: SimCLR needs large batches (4096+) for enough negatives. MoCo maintains a separate queue (65536 negatives) enabling small batches.
- EMA decay (BYOL): 0.996 at start, linear ramp to 0.9999 over training. Too low: target updates too fast, instability. Too high: target barely changes, collapse.
- Projection head: 2-layer MLP with hidden 2048, output 128. Critical insight: use the encoder output (before projector) for downstream tasks — projector throws away task-relevant info to satisfy the invariance objective.

---

## 7. Real-World Examples

**BERT — NLP foundation:** Trained with MLM on 3.3B tokens (BooksCorpus + Wikipedia) for 1M steps on 64 TPU chips (~4 days). The 15% masking rate is a balance: too few masked tokens = weak signal; too many = insufficient context for prediction. BERT-base (110M params) achieves 84.6 GLUE, outperforming all prior task-specific models simultaneously.

**GPT-4 — autoregressive SSL at scale:** Next-token prediction on ~13 trillion tokens. No explicit label — the next token is the label. At trillion-token scale, the model encounters enough context to implicitly learn reasoning, math, and code. Emergent capabilities arise from scale, not architecture changes.

**MoCo (Facebook AI) — memory bank for negatives:** Addresses SimCLR's large batch requirement. Maintains a FIFO queue of 65536 past encoder outputs as negatives. Encoder for query uses gradient. Encoder for keys uses EMA-updated copy (momentum encoder). Query key pairs are positive; queue entries are negatives. Trains effectively with batch size 256.

**SCARF (Snap Inc.) — tabular SSL:** Applied to click-through rate prediction. 6M+ rows, 200 features, ~0.1% labeled. SCARF pretraining + fine-tuning with 1% labels matched XGBoost trained on 100% labels. Key: feature corruption must sample from marginal distributions (not just zero out) to prevent the model from detecting corruption by absence rather than value.

**GraphCL — molecule property prediction:** Pretrained on 2M unlabeled molecules from ZINC database. Downstream: HIV antiviral activity (1.5K labeled). GraphCL + fine-tuning improved ROC-AUC from 0.74 (supervised only) to 0.80. Subgraph augmentation was most effective for molecular graphs — preserves local chemistry.

---

## 8. Tradeoffs

| Method | Needs Negatives | Batch Size | Collapse Risk | Compute | Downstream Quality |
|---|---|---|---|---|---|
| SimCLR | Yes (batch) | Very large (4096+) | Low (negatives) | High | High |
| MoCo v2 | Yes (queue) | Small (256) | Low | Medium | High |
| BYOL | No | Medium (512) | Medium (need EMA) | Medium | High |
| DINO | No | Medium | Low (centering) | Medium | Very High |
| Barlow Twins | No | Medium | Low (redundancy) | Medium | High |
| BERT MLM | No | Medium | N/A (generative) | High | Very High (NLP) |
| ELECTRA | No | Medium | N/A | Medium | Higher than BERT/compute |

**Representation collapse:** contrastive methods collapse without negatives because the trivially optimal solution (all embeddings identical) minimizes positive distances. BYOL avoids this via the predictor asymmetry + EMA target — the online network must predict a moving target, creating a non-trivial optimization landscape. Barlow Twins avoids collapse via cross-correlation matrix regularization (penalize redundancy between dimensions).

---

## 9. When to Use / When NOT to Use

**Use SSL when:**
- Large unlabeled dataset available, few labeled samples
- Annotation is expensive (medical images, legal documents)
- You want to pretrain a general encoder reused across multiple downstream tasks
- Domain shift is expected — pretrained representations transfer better
- Data augmentation can be designed to preserve task-relevant semantics

**Do NOT use SSL when:**
- Small dataset (<10K samples) with full labels — supervised training directly is simpler
- Data augmentations cannot be designed without destroying task-relevant signal (e.g., tabular data with all features equally important)
- Latency budget prohibits large pretrained encoders
- Task is simple enough for a shallow model (logistic regression on well-engineered features)

**NLP-specific:** always use a pretrained LLM rather than training SSL from scratch. Pretraining cost is prohibitive. Fine-tuning is the standard.

---

## 10. Common Pitfalls

**Pitfall 1 — Augmentation collapse (semantic violation):**
A team trained SimCLR on medical chest X-rays using standard image augmentations including horizontal flips and color jitter. The model learned flip-invariant representations — but left/right asymmetry is diagnostically critical (dextrocardia, pneumothorax laterality). Linear evaluation on pathology classification: 51% accuracy, worse than random for asymmetric conditions. Fix: domain augmentations must be semantically valid. For X-rays: only mild rotation (<15 deg), no flip, no aggressive color distortion.

**Pitfall 2 — Using projector head for downstream tasks:**
A team froze the SimCLR encoder including the projector, then trained a linear head on the projector's 128-dim output. Performance was 12% below the same architecture trained with features from before the projector. The projector is trained to maximize invariance — it discards information useful for fine-grained classification. Fix: always strip the projector after pretraining; use the encoder's final representation (h, not z).

**Pitfall 3 — Representation collapse in BYOL with wrong EMA schedule:**
Setting EMA decay m=0.5 (too low) caused the target network to track the online network too closely, collapsing both to degenerate representations within 10 epochs. Loss reached zero but linear evaluation was 23% (near-random). Fix: start EMA at 0.996, cosine-schedule it to 0.9999 over training. The slow-moving target provides stable pseudo-labels.

**Pitfall 4 — BERT masked token bias:**
The [MASK] token appears only during pretraining (not fine-tuning), creating a train/test mismatch. BERT partially mitigates this by only replacing 80% of chosen tokens with [MASK], 10% with a random word, and 10% with the original. Teams that modify this ratio (e.g., masking 100% with [MASK]) see degraded fine-tuning performance. ELECTRA avoids this entirely — no [MASK] token, uses replaced token detection.

**Pitfall 5 — Ignoring the linear evaluation protocol:**
A team evaluated SSL quality by fine-tuning the full model and comparing to supervised baselines. Fine-tuning can mask poor representations — the additional supervised signal can compensate. They reported excellent results, deployed, and found the pretrained features were not actually generalized. Fix: always report linear evaluation alongside fine-tuning results to diagnose representation quality independently.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|---|---|---|
| HuggingFace Transformers | BERT, GPT, ELECTRA pretraining/fine-tuning | Industry standard |
| VISSL (Facebook) | Vision SSL: SimCLR, MoCo, BYOL, DINO | PyTorch-based, production-ready |
| Lightly AI | SSL for computer vision, active learning | Commercial + open-source |
| PyTorch Lightning | SSL training boilerplate | Simplifies distributed training |
| PyTorch Geometric | Graph SSL: DGI, GraphCL | GNN-specific |
| sentence-transformers | Contrastive learning for text embeddings | SimCSE, NLI supervision |
| SimCSE | Contrastive sentence embeddings | Dropout as augmentation, strong baseline |
| FAISS | ANN search for evaluating embedding quality | Used in retrieval benchmarks |

---

## 12. Interview Questions with Answers

**Q: What is self-supervised learning and how does it differ from unsupervised learning?**
Self-supervised learning is a subset of unsupervised learning that creates supervisory signals automatically from the input data. Traditional unsupervised learning methods (clustering, PCA, autoencoders) aim to model the data distribution or find low-dimensional structure. SSL explicitly trains on a pretext task with generated pseudo-labels — next token prediction, masked token prediction, or predicting one view from another. The distinction matters because SSL produces representations directly optimized for prediction tasks, which transfer significantly better to downstream classification and regression.

**Q: What is the InfoNCE loss and what does it optimize?**
InfoNCE (noise-contrastive estimation) loss is a lower bound on the mutual information I(z_i; z_j) between two views. It optimizes the encoder to make the positive pair's similarity score higher than any negative pair's score. The temperature parameter tau controls sharpness: lower temperature creates a harder classification problem (more focus on near-miss negatives), higher temperature distributes gradient more uniformly. InfoNCE is equivalent to cross-entropy classification where the correct class is the positive pair among 2N-1 candidates.

**Q: How does BYOL avoid representation collapse without negative samples?**
BYOL uses three mechanisms together: (1) an asymmetric predictor network on the online branch only — the online network must predict the target's projection, which is a harder, non-trivial task, (2) a momentum (EMA) target network — the target moves slowly, providing a stable but non-trivial prediction target, and (3) stop-gradient on the target — gradients do not flow through the target network, preventing the trivial solution where both networks collapse together. Remove any single mechanism and collapse occurs.

**Q: What is the difference between linear evaluation and fine-tuning for evaluating SSL representations?**
Linear evaluation freezes the pretrained encoder and trains only a linear classifier on top. This measures the quality of representations independent of subsequent adaptation. Fine-tuning updates all weights with labeled data — the additional capacity can compensate for poor representations. Linear evaluation scores are more diagnostic: if linear evaluation is high (>70% on ImageNet), representations have learned class structure without labels. A high fine-tuning score with low linear evaluation suggests the fine-tuning process is doing the heavy lifting, not the SSL pretraining.

**Q: Why does BERT use a 15% masking rate and what happens if you change it?**
15% is a balance between signal strength and context availability. Too low (5%): too few positions receive gradient signal, learning is slow. Too high (40%): insufficient context remains for the model to accurately predict masked tokens — the task becomes too hard, and the model learns to rely on local statistical patterns rather than long-range semantics. The 15% rate was determined empirically and has remained standard across BERT variants. SpanBERT found that masking contiguous spans (rather than random tokens) is more effective for span-extraction tasks.

**Q: How does the temperature parameter affect contrastive learning?**
Temperature tau (typically 0.07–0.2) controls the sharpness of the softmax distribution over similarities. Low tau: the loss is dominated by the hardest negatives (near-miss samples with high similarity to the anchor). This creates strong gradients when the model nearly confuses a negative for the positive, pushing apart similar-looking negatives aggressively. High tau: softer distribution, more uniform gradient across all negatives, slower convergence but more stable. SimCLR uses tau=0.07, MoCo uses 0.07–0.1. Setting tau too low (<0.03) causes gradient explosion and instability.

**Q: What is ELECTRA and why is it more efficient than BERT?**
ELECTRA trains a discriminator model to detect replaced tokens in a sequence. A small generator (MLM-style) produces plausible token replacements. The discriminator sees every token and predicts which are genuine vs. generated. All N tokens receive gradient signal (not just 15% as in BERT MLM). This makes ELECTRA ~4x more compute-efficient: the same model performance is achieved in 1/4 the compute. The discriminator (the actual model) never sees [MASK] tokens, eliminating the pretraining/fine-tuning mismatch. ELECTRA-base matches BERT-large with 1/4 the FLOPs.

**Q: What augmentation strategies work for graph SSL and why are they different from vision SSL?**
Graph augmentations must preserve graph semantics. Standard augmentations: (1) edge dropout — randomly remove 10–20% of edges, models learn robust to missing connections, (2) node feature masking — zero out 10–30% of node feature dimensions, (3) subgraph sampling — random walk to extract a subgraph, (4) node dropout — remove nodes with low degree. Unlike vision SSL where all augmentations are roughly equivalent, graph augmentations are highly domain-dependent: for molecular graphs, removing edges means breaking bonds (semantically invalid for drug-like properties), so attribute masking works better. For social networks, edge dropout is appropriate.

**Q: How would you apply SSL to a tabular dataset with no natural augmentations?**
Use SCARF (feature corruption): for each training sample, randomly select 20–60% of features and replace their values by sampling from that feature's marginal distribution (computed over the full dataset). This simulates "what if some measurements were taken from a different patient." The model must predict which features were corrupted or use contrastive loss between the clean and corrupted views. Alternative: SubTab — split features randomly into two halves, learn representations that reconstruct each half from the other. Both approaches exploit feature correlations for representation learning.

**Q: What is the SimCSE approach for sentence embeddings?**
SimCSE (Simple Contrastive Learning of Sentence Embeddings) creates positive pairs from a single sentence passed through the encoder twice with different dropout masks. The stochastic nature of dropout (p=0.1) produces two slightly different representations of the same sentence — these are the positive pair. All other sentences in the batch are negatives. This minimal augmentation is sufficient because sentence meaning is preserved, while dropout creates just enough variation. Hard negative version adds NLI-contradictory sentences as explicit negatives. SimCSE improved STS benchmarks by 2–3 points over supervised SimCSE baselines.

**Q: Why do we need a projector in SimCLR and why is it discarded after pretraining?**
The projector (MLP head mapping encoder output to lower-dimensional z) serves two purposes: (1) it absorbs the invariance objective — the contrastive loss forces z to be invariant to augmentations. If this invariance were imposed directly on the encoder, the encoder would lose discriminative information needed for downstream tasks. The projector acts as a "sacrifice layer" that becomes maximally invariant. (2) It prevents the encoder from collapsing — the encoder only needs to encode enough information for the projector to succeed, not be perfectly invariant itself. Empirically, using h (encoder output) instead of z (projector output) for linear evaluation improves accuracy by 10+ points.

---

## 13. Best Practices

- Design augmentations first — the augmentation strategy defines the invariances the encoder learns. Wrong augmentations (semantically invalid) produce useless representations.
- Use the encoder output (h), not the projector output (z), for all downstream tasks. The projector is a training artifact.
- For contrastive methods, batch size matters more than epochs. Larger batch = more negatives per step. If GPU memory limits batch size, use MoCo's queue instead.
- Linear evaluation is mandatory for reporting SSL quality. Do not report only fine-tuning results.
- For tabular SSL with SCARF, corruption rate 0.6 is a strong default; tune on a validation set.
- Monitor embedding collapse early: compute average cosine similarity across the batch every epoch. If it exceeds 0.95 before epoch 20, collapse is occurring.
- Use cosine learning rate schedule with warmup (5% of steps) for all SSL methods — loss landscapes are non-convex and sharp early.
- For NLP, do not pretrain from scratch unless you have >10B tokens and >100 GPU-days of compute. Fine-tune existing pretrained models.
- Temperature sweep: try tau in {0.05, 0.07, 0.1, 0.2} on a validation task. Optimal tau is dataset-dependent.
- Projection head dimension 128 is sufficient; increasing to 256 rarely helps after the encoder is >1M parameters.

---

## 14. Case Study

**Problem: Semi-supervised fraud detection on e-commerce transactions**

Context: 10M daily transactions, 0.05% labeled as fraud (5K labels/day), remainder unlabeled. Goal: improve fraud recall by 15% over supervised-only baseline (XGBoost on 60-day labeled history).

**Approach: SCARF pretraining + fine-tuning**

```
Data pipeline
==============
  10M transactions/day
  Features: [amount, merchant_id_embedding (16d), category (one-hot 200d),
             hour_of_day, day_of_week, device_type, country, velocity_1h,
             velocity_24h, card_age_days, account_age_days]
  Total: 238 features after encoding

SSL Pretraining
===============
  SCARF encoder: MLP [238 -> 512 -> 512 -> 512 -> 256]
  Corruption rate: 0.6 (60% of features replaced from marginals)
  Projector: [256 -> 256 -> 128]
  NT-Xent temperature: 0.07
  Batch size: 2048
  Optimizer: AdamW, lr=1e-3, weight_decay=1e-5
  Schedule: cosine decay, 100 epochs over 5M samples
  Hardware: 2x A100, ~8 hours

Fine-tuning
===========
  Freeze encoder, train linear head: 2 epochs (fast, high precision)
  Fine-tune full model: 20 epochs with lr=1e-4 (higher recall)
  Loss: weighted BCE (fraud weight = 20x to address imbalance)
  Data: 60 days * 5K labels/day = 300K labeled samples
```

**Results:**

| Metric | XGBoost (supervised) | SCARF + fine-tune | Improvement |
|---|---|---|---|
| Recall @ Precision=85% | 61% | 74% | +13pp |
| AUC-ROC | 0.934 | 0.963 | +0.029 |
| AUC-PR | 0.71 | 0.83 | +0.12 |
| P99 inference latency | 3ms | 8ms | 2.7x slower |

Key finding: SCARF pretraining helped most for rare fraud patterns (account takeover: +22% recall) where labeled examples are sparse. Common fraud patterns (CNP fraud) saw minimal improvement — sufficient labels existed for XGBoost to learn those patterns directly.

The 8ms inference latency was acceptable for the batch-scoring use case (transactions scored every 5 minutes). For real-time (<50ms) blocking, a distilled version of the fine-tuned model (2-layer MLP) retained 90% of the recall gain at 2ms latency.
