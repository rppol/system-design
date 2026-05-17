# Self-Supervised Learning for Vision

## 1. Concept Overview

Self-supervised learning (SSL) for vision trains visual representations using the structure of the data itself, without human-provided labels. The model is given a pretext task (contrastive matching, masked reconstruction, self-distillation) that forces it to learn semantically meaningful features as a side effect of solving the pretext problem.

SSL is motivated by a fundamental bottleneck in supervised learning: annotating large image datasets is expensive (ImageNet-1k required ~25,000 person-hours; COCO took ~70,000), while unlabeled images are effectively unlimited. SSL can leverage web-scale unlabeled corpora to produce representations that match or exceed supervised ImageNet pretraining on many downstream tasks.

The field has converged on five dominant frameworks: SimCLR (contrastive, large batch), MoCo (contrastive, memory queue), DINO/DINOv2 (self-distillation), MAE (masked autoencoder), and BYOL (non-contrastive, no negatives).

---

## 2. Intuition

**Contrastive learning** (SimCLR, MoCo): teach the model that two augmented views of the same image should produce similar embeddings ("pull together"), while views of different images should be dissimilar ("push apart"). Like learning that a photograph taken from slightly different angles is still the same object.

**Self-distillation** (DINO, BYOL): a teacher network (updated slowly via momentum) generates pseudo-labels for what the student network should output on the same image. The student learns to match the teacher without collapse, because the teacher's gradual updates prevent the trivial solution.

**Masked reconstruction** (MAE): hide 75% of image patches and train the model to reconstruct pixel values of the missing patches. To do so accurately, the model must learn rich semantic features — you cannot reconstruct a face patch without understanding that it belongs to a face.

Key insight: the best SSL methods learn representations that are at least as good as supervised ImageNet pretraining when evaluated by linear probing — and often better, because they are not overfit to 1000 specific ImageNet classes.

---

## 3. Core Principles

**Augmentation invariance**: SSL models should produce identical embeddings for two differently augmented views of the same image. The augmentations (crop, color jitter, blur) are carefully chosen to preserve semantic content while discarding style — the "positive pair" are semantically identical but visually different.

**Preventing collapse**: without supervision, a trivial solution is for the model to output the same constant vector for every input (collapsed representation). Different methods prevent collapse differently: contrastive methods use negative pairs; BYOL uses asymmetric architecture with a predictor MLP and stop-gradient; DINO uses centering and sharpening; MAE uses reconstruction loss that cannot be minimized by a constant.

**Linear probing vs fine-tuning**: the standard SSL evaluation protocol trains a linear classifier on top of a frozen backbone (linear probe) and reports accuracy. This measures the quality of the learned representation independently of the downstream task. Full fine-tuning allows the backbone to adapt and typically gives 3–8% higher accuracy.

**Temperature**: contrastive losses use a temperature parameter τ to control the sharpness of the similarity distribution. τ=0.07 (SimCLR) makes the distribution sharper, increasing the gradient signal from hard negatives but destabilizing training if too small.

---

## 4. Types / Architectures / Strategies

### Contrastive: SimCLR

SimCLR requires large batches (4096–8192) to have enough in-batch negatives. For each image, two augmented views are created. A CNN backbone encodes both views; a projection head (2-layer MLP) maps to a lower-dimensional space (128-d). NT-Xent loss (normalized temperature-scaled cross-entropy) maximizes agreement between the two views while pushing apart all other 2(N-1) views in the batch.

After pretraining, the projection head is discarded. The backbone is either frozen (linear probe) or fine-tuned.

### Contrastive: MoCo (Momentum Contrast)

MoCo solves SimCLR's large-batch requirement using a memory queue of negative embeddings maintained across batches. Key components:
- **Online encoder**: updated by gradient descent.
- **Momentum encoder** (key encoder): updated as a weighted average of the online encoder: k = m·k + (1-m)·q, with m=0.999. This makes the momentum encoder a slowly-evolving version of the online encoder, ensuring queue features are consistent.
- **Queue**: stores the last K=65,536 encoded keys. Each query is contrasted against all queue entries. After each step, the oldest batch of keys is dequeued and the new batch is enqueued.

MoCov2 adds a projection head and stronger augmentation (matching SimCLR). MoCov3 replaces the CNN backbone with ViT.

### Self-Distillation: DINO

DINO (Self-DIstillation with NO labels) trains a student-teacher pair where both are identical ViTs:
- **Teacher**: updated as EMA of the student (momentum m=0.9995).
- **Student**: trained via gradient descent to match the teacher's output distribution.
- **Centering**: teacher outputs are centered (mean subtracted) to prevent collapse to a single dimension.
- **Sharpening**: teacher uses a lower temperature (0.04) than the student (0.1) to produce sharper target distributions.
- **Multi-crop**: 2 global views (224×224) + 8 local views (96×96). Student sees all 10 views; teacher sees only the 2 global views. This forces the student to predict global from local ("local-to-global correspondence").

DINO emergent behavior: attention maps from the last layer reliably segment foreground objects without any segmentation training. DINOv2 scales this up with a curated dataset, adding iBOT (masked token prediction) objective and register tokens.

### Masked Autoencoder: MAE

MAE applies to ViT exclusively:
- **Masking**: 75% of patches are randomly masked.
- **Encoder**: standard ViT processes only the 25% visible patches (efficient — no computation on masked tokens).
- **Decoder**: a lightweight transformer (smaller than encoder) takes the encoder output for visible tokens and learned mask tokens, and reconstructs pixel values of all masked patches.
- **Loss**: MSE between predicted and true pixel values of masked patches (normalized per patch).

At downstream fine-tuning, the decoder is discarded and only the encoder is used. The high mask ratio (75%) forces the encoder to understand the global image structure — simply copying nearby pixel values is insufficient when 3 of every 4 patches are missing.

### Non-Contrastive: BYOL

BYOL (Bootstrap Your Own Latent) removes negative pairs entirely:
- **Online network**: encoder + projector + predictor MLP. Only the online network receives gradients.
- **Target network**: encoder + projector only (no predictor). Updated as EMA of online network.
- **Loss**: MSE between online predictor output and target projector output (stop-gradient on target).

Without negatives, the trivial solution is for both networks to output a constant. Collapse is prevented by the asymmetry: the predictor on the online side must predict the target, but the target has no corresponding predictor and is updated only via EMA. Batch normalization in the predictor MLP is also critical.

---

## 5. Architecture Diagrams

### SimCLR Training

```
One image x
    |
[Augment ×2]
    |
  x_i (crop A)          x_j (crop B)
    |                        |
[Backbone f]             [Backbone f]   (shared weights)
    |                        |
  h_i                       h_j          (representations)
    |                        |
[Projector g: 2-layer MLP]  [Projector g]
    |                        |
  z_i                       z_j          (projections, 128-d)
    |
[NT-Xent Loss]
  - z_i and z_j are the positive pair
  - All other z_k in batch (k ≠ i,j) are negatives
  - Loss = -log[ exp(sim(z_i,z_j)/τ) / Σ_{k≠i} exp(sim(z_i,z_k)/τ) ]
  - τ = 0.07, batch = 4096

At downstream: projector g is discarded, backbone f is fine-tuned or linearly probed
```

### MoCo Queue Mechanism

```
Batch of queries (online encoder)    Memory Queue (65,536 keys)
        |                                     |
  q = f_online(x_aug_1)               keys from past batches
        |                             (updated via momentum encoder)
        |
  [Dot product: q × all queue keys]
        |
  InfoNCE loss (1 positive, 65535 negatives)
        |
  Gradient → online encoder only
        |
  Momentum update: f_momentum ← 0.999 * f_momentum + 0.001 * f_online
        |
  Enqueue new keys (from f_momentum(x_aug_2)), dequeue oldest
```

### MAE Training vs Inference

```
TRAINING:
  Image (224×224)
      |
  [Divide into 196 patches of 16×16]
      |
  [Random mask 75%: hide 147 patches, keep 49]
      |
  Visible patches → [ViT Encoder (large, e.g., ViT-L)] → encoded tokens (49)
      |
  [MAE Decoder: small transformer]
      inputs: 49 encoded tokens + 147 learnable MASK tokens
      output: pixel values for all 196 patches
      |
  [MSE Loss on masked 147 patches only]

INFERENCE / DOWNSTREAM:
  Image → [ViT Encoder] (no masking) → full 196 token embeddings
      |
  Decoder is DISCARDED
      |
  CLS token or avg-pool → [Linear head or full fine-tune]
```

### BYOL Architecture

```
     Input x
    /        \
[Augment]    [Augment]
    |              |
 view 1         view 2
    |              |
[Online Enc]   [Target Enc]   ← EMA of Online Enc (no grad)
    |              |
[Online Proj]  [Target Proj]  ← EMA of Online Proj (no grad)
    |              |
[Predictor]    STOP GRADIENT
    |              |
    └── MSE Loss ──┘

Loss = MSE(normalize(predictor(online_proj(view1))),
           normalize(target_proj(view2)))
     + MSE(normalize(predictor(online_proj(view2))),
           normalize(target_proj(view1)))

Key: NO negative pairs. No memory queue. Batch size 4096.
```

---

## 6. How It Works — Detailed Mechanics

### NT-Xent Loss (SimCLR)

```python
import torch
import torch.nn.functional as F
from torch import Tensor


def nt_xent_loss(z_i: Tensor, z_j: Tensor,
                  temperature: float = 0.07) -> Tensor:
    """
    Normalized Temperature-scaled Cross Entropy Loss (SimCLR).
    z_i, z_j: (B, D) L2-normalized projection embeddings
    Returns scalar loss.
    """
    B = z_i.size(0)

    # Concatenate to get all 2B embeddings
    z = torch.cat([z_i, z_j], dim=0)  # (2B, D)

    # Cosine similarity matrix
    sim = F.cosine_similarity(z.unsqueeze(1), z.unsqueeze(0), dim=2)  # (2B, 2B)
    sim = sim / temperature

    # Mask out self-similarity (diagonal)
    mask = torch.eye(2 * B, dtype=torch.bool, device=z.device)
    sim.masked_fill_(mask, float("-inf"))

    # Positive pairs: (i, i+B) and (i+B, i)
    labels = torch.cat([
        torch.arange(B, 2 * B, device=z.device),  # positive for first B views
        torch.arange(0, B, device=z.device),        # positive for last B views
    ])

    loss = F.cross_entropy(sim, labels)
    return loss


class SimCLRProjectionHead(torch.nn.Module):
    """2-layer MLP projector used in SimCLR."""

    def __init__(self, in_dim: int = 2048,
                 hidden_dim: int = 2048,
                 out_dim: int = 128) -> None:
        super().__init__()
        self.mlp = torch.nn.Sequential(
            torch.nn.Linear(in_dim, hidden_dim, bias=False),
            torch.nn.BatchNorm1d(hidden_dim),
            torch.nn.ReLU(inplace=True),
            torch.nn.Linear(hidden_dim, out_dim, bias=False),
            torch.nn.BatchNorm1d(out_dim, affine=False),
        )

    def forward(self, x: Tensor) -> Tensor:
        return F.normalize(self.mlp(x), dim=-1)
```

### MoCo Momentum Update

```python
import torch
import torch.nn as nn
from torch import Tensor
import copy


class MoCoModel(nn.Module):
    """MoCo v2 simplified implementation."""

    def __init__(self, backbone: nn.Module,
                 projection_dim: int = 128,
                 queue_size: int = 65536,
                 momentum: float = 0.999,
                 temperature: float = 0.07) -> None:
        super().__init__()
        self.momentum = momentum
        self.temperature = temperature
        self.queue_size = queue_size

        # Online encoder
        self.encoder_q = backbone
        self.projector_q = self._build_mlp(projection_dim)

        # Momentum encoder (no gradients)
        self.encoder_k = copy.deepcopy(backbone)
        self.projector_k = copy.deepcopy(self.projector_q)
        for p in list(self.encoder_k.parameters()) + list(self.projector_k.parameters()):
            p.requires_grad_(False)

        # Initialize queue
        self.register_buffer("queue",
            F.normalize(torch.randn(projection_dim, queue_size), dim=0))
        self.register_buffer("queue_ptr", torch.zeros(1, dtype=torch.long))

    def _build_mlp(self, out_dim: int) -> nn.Module:
        return nn.Sequential(
            nn.Linear(2048, 2048), nn.ReLU(), nn.Linear(2048, out_dim))

    @torch.no_grad()
    def _momentum_update(self) -> None:
        for p_q, p_k in zip(self.encoder_q.parameters(),
                              self.encoder_k.parameters()):
            p_k.data = self.momentum * p_k.data + (1 - self.momentum) * p_q.data
        for p_q, p_k in zip(self.projector_q.parameters(),
                              self.projector_k.parameters()):
            p_k.data = self.momentum * p_k.data + (1 - self.momentum) * p_q.data

    @torch.no_grad()
    def _dequeue_and_enqueue(self, keys: Tensor) -> None:
        batch_size = keys.size(0)
        ptr = int(self.queue_ptr)
        # Overwrite oldest entries (circular buffer)
        self.queue[:, ptr:ptr + batch_size] = keys.T
        self.queue_ptr[0] = (ptr + batch_size) % self.queue_size

    def forward(self, x_q: Tensor, x_k: Tensor) -> Tensor:
        # Online forward
        q = F.normalize(self.projector_q(self.encoder_q(x_q)), dim=1)

        # Momentum encoder forward (no grad)
        with torch.no_grad():
            self._momentum_update()
            k = F.normalize(self.projector_k(self.encoder_k(x_k)), dim=1)

        # Positive logit: (B, 1)
        l_pos = (q * k).sum(dim=1, keepdim=True) / self.temperature

        # Negative logits: (B, K) from queue
        l_neg = (q @ self.queue.clone().detach()) / self.temperature

        logits = torch.cat([l_pos, l_neg], dim=1)  # (B, K+1)
        labels = torch.zeros(logits.size(0), dtype=torch.long,
                              device=logits.device)  # positive = index 0

        self._dequeue_and_enqueue(k)
        return F.cross_entropy(logits, labels)
```

### MAE Training Step

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch import Tensor


class MAEMasking(nn.Module):
    """Random patch masking for MAE."""

    def __init__(self, mask_ratio: float = 0.75) -> None:
        super().__init__()
        self.mask_ratio = mask_ratio

    def forward(self, x: Tensor) -> tuple[Tensor, Tensor, Tensor]:
        """
        x: (B, N, D) patch embeddings
        Returns: (visible_tokens, mask, ids_restore)
          - visible_tokens: (B, N_visible, D)
          - mask: (B, N) bool, True = masked
          - ids_restore: (B, N) indices to restore original order
        """
        B, N, D = x.shape
        N_visible = int(N * (1 - self.mask_ratio))

        # Random shuffle and take first N_visible as visible
        noise = torch.rand(B, N, device=x.device)
        ids_shuffle = noise.argsort(dim=1)
        ids_restore = ids_shuffle.argsort(dim=1)

        ids_visible = ids_shuffle[:, :N_visible]
        visible_tokens = torch.gather(
            x, 1, ids_visible.unsqueeze(-1).expand(-1, -1, D))

        # Binary mask: 1 = masked
        mask = torch.ones(B, N, device=x.device)
        mask[:, :N_visible] = 0
        mask = torch.gather(mask, 1, ids_restore)

        return visible_tokens, mask.bool(), ids_restore


def mae_loss(pred_pixels: Tensor,
              target_pixels: Tensor,
              mask: Tensor,
              patch_size: int = 16,
              normalize_target: bool = True) -> Tensor:
    """
    MSE loss on masked patches only, with optional per-patch normalization.
    pred_pixels:   (B, N, patch_size^2 * 3)
    target_pixels: (B, N, patch_size^2 * 3)
    mask:          (B, N) bool, True = masked
    """
    if normalize_target:
        # Normalize each patch's pixel values to zero-mean unit-variance
        mean = target_pixels.mean(dim=-1, keepdim=True)
        var  = target_pixels.var(dim=-1, keepdim=True)
        target_pixels = (target_pixels - mean) / (var + 1e-6).sqrt()

    loss_per_patch = ((pred_pixels - target_pixels) ** 2).mean(dim=-1)  # (B, N)
    # Average only over masked patches
    loss = (loss_per_patch * mask).sum() / mask.sum()
    return loss
```

### Linear Probing Evaluation

```python
import torch
import torch.nn as nn
from torch import Tensor
from torch.utils.data import DataLoader


@torch.no_grad()
def extract_features(backbone: nn.Module,
                      loader: DataLoader,
                      device: torch.device) -> tuple[Tensor, Tensor]:
    """Extract features from frozen backbone for linear probing."""
    backbone.eval()
    all_features: list[Tensor] = []
    all_labels: list[Tensor] = []

    for images, labels in loader:
        images = images.to(device)
        features = backbone(images)  # (B, D)
        # Use CLS token or global average pool depending on model
        all_features.append(features.cpu())
        all_labels.append(labels)

    return torch.cat(all_features), torch.cat(all_labels)


def linear_probe_eval(backbone: nn.Module,
                       train_loader: DataLoader,
                       val_loader: DataLoader,
                       num_classes: int,
                       feature_dim: int,
                       device: torch.device,
                       epochs: int = 100) -> float:
    """Train a linear classifier on frozen SSL features."""
    # Freeze backbone
    for p in backbone.parameters():
        p.requires_grad_(False)

    # Extract train and val features once (efficient)
    train_features, train_labels = extract_features(backbone, train_loader, device)
    val_features, val_labels = extract_features(backbone, val_loader, device)

    # Train linear head
    head = nn.Linear(feature_dim, num_classes).to(device)
    optimizer = torch.optim.LBFGS(head.parameters(), lr=0.1, max_iter=20)
    criterion = nn.CrossEntropyLoss()

    train_features = train_features.to(device)
    train_labels = train_labels.to(device)

    def closure() -> Tensor:
        optimizer.zero_grad()
        logits = head(train_features)
        loss = criterion(logits, train_labels)
        loss.backward()
        return loss

    for _ in range(epochs):
        optimizer.step(closure)

    # Evaluate
    head.eval()
    val_features = val_features.to(device)
    with torch.no_grad():
        logits = head(val_features)
        pred = logits.argmax(dim=1).cpu()
    accuracy = (pred == val_labels).float().mean().item()
    return accuracy
```

---

## 7. Real-World Examples

**Meta DINOv2 for dense prediction**: Meta deployed DINOv2 ViT-g/14 as the backbone for production semantic segmentation and depth estimation on AR glasses (Aria). The model's linear probing quality (86.5% ImageNet) means simple linear decoders achieve production-quality depth estimation without expensive annotation.

**Apple's visual search (Spotlight, Photos search)**: CLIP-like SSL pretraining on proprietary image-text pairs. Embeddings are computed on-device using the Neural Engine, enabling private visual search without server round-trips. Model is quantized to INT8 (< 100MB) for on-device storage.

**Google Universal Sentence/Image Encoder**: BYOL-style contrastive pretraining on 3B image-text pairs from web crawl. Used for product recommendation and image clustering in Google Shopping.

**BioMedCLIP**: CLIP trained on 15M medical image-text pairs from PubMed Central. Zero-shot classification across radiology, pathology, and dermatology tasks; 2-3% below supervised fine-tuned baselines without any target-domain labels.

**SimCLR for unlabeled satellite imagery**: Remote sensing companies use SimCLR on unlabeled Sentinel-2 satellite images (~1TB/day of global coverage) to pretrain backbones for land classification, crop monitoring, and deforestation detection. Labeled data for each downstream task is scarce; SSL pretraining provides 10–25% F1 improvement over supervised ImageNet transfer.

---

## 8. Tradeoffs

| Method | Negative pairs | Large batch required | Training memory | Eval (linear probe) | Convergence speed |
|--------|---------------|---------------------|-----------------|--------------------|--------------------|
| SimCLR | Yes (in-batch) | Yes (4096+) | High | 71.7% (ResNet-50) | Slow |
| MoCo v2 | Yes (queue) | No (256) | Medium | 71.1% (ResNet-50) | Medium |
| BYOL | No | No (4096) | Medium | 74.3% (ResNet-50) | Fast |
| DINO | No (centering) | No (1024) | Medium | 77.0% (ViT-S/16) | Medium |
| MAE | No | No (1024) | Low (encoder only) | 73.5% (ViT-B fine-tune 83.1%) | Fast |
| DINOv2 | No | No | High (14M dataset curation) | 86.5% (ViT-g/14) | Very fast downstream |

| Evaluation Protocol | When to Use | Typical Gap vs Full Fine-Tune |
|---------------------|-------------|-------------------------------|
| Linear probing | Measure representation quality, fast | 3–8% below fine-tuning |
| k-NN probe (k=20) | No training needed, instant | 5–10% below fine-tuning |
| Full fine-tuning | Maximize downstream accuracy | Baseline |
| Attentive probing | Better than linear for ViT | 1–3% below fine-tuning |

---

## 9. When to Use / When NOT to Use

**Use SSL when**:
- You have a large corpus of unlabeled domain-specific images (medical scans, satellite imagery, industrial inspection frames) where labels are expensive.
- Your downstream task has very few labels (< 1000) — SSL pretrained features + linear probe outperform supervised pretraining in the low-data regime.
- You need general-purpose visual features for multiple diverse downstream tasks.
- Label distribution is unknown or shifts frequently (SSL features are not overfit to fixed class taxonomy).

**Use SimCLR/MoCo when**: you have a large GPU cluster (8-64 GPUs) and want well-understood contrastive representations. MoCo is preferable on compute budget since it does not need large batches.

**Use DINO/DINOv2 when**: you want the best transfer features without labels. Use the pretrained DINOv2 checkpoints directly — reproducing DINO training is expensive (8× A100, 2–5 days).

**Use MAE when**: your backbone is ViT and you want efficient pretraining (encoder only sees 25% of patches, so training is ~3× faster than full-image methods at the same parameter count). Fine-tuning outperforms linear probing significantly for MAE.

**Do NOT use SSL when**:
- Labeled data is abundant (> 1M samples) — supervised pretraining on ImageNet-21k is simpler and often competitive.
- Inference latency is critical on edge — SSL pretrained models are not inherently smaller; you still need to compress/distill.
- Your images are very different from natural photos and no SSL checkpoint exists for your domain — SSL on a tiny dataset (< 50k domain images) rarely outperforms supervised ImageNet transfer.

---

## 10. Common Pitfalls

**Pitfall 1: SimCLR with insufficient batch size**
A team ran SimCLR with batch size 256 on 4 GPUs (effective batch 256, not 4096). With so few negatives per query, the loss had minimal gradient signal to distinguish hard negatives. After 200 epochs, linear probe accuracy was 58% vs expected 71%. Fix: either increase to 4096+ batch size or switch to MoCo which has 65k negatives regardless of batch size.

**Pitfall 2: Augmentation too weak — random resize crop is critical**
An engineer replaced `RandomResizedCrop(scale=(0.08, 1.0))` with `CenterCrop` in SimCLR, thinking it was simpler. The contrastive task became trivially easy (two crops from the same center are nearly identical), and the model learned near-zero-information features. Linear probe accuracy: 38%. The large scale variation in random resize crop is what makes the contrastive task hard enough to force semantic learning.

**Pitfall 3: Using MAE features without fine-tuning**
MAE's linear probing accuracy (73.5% for ViT-B) is significantly lower than fine-tuning accuracy (83.1%). A team deployed an MAE-pretrained model with a linear head and observed mediocre performance, concluded SSL didn't work, and abandoned it. MAE is designed for fine-tuning, not linear probing — its encoder sees only 25% of patches during training, so the representation is deliberately under-specified until fine-tuned with full patches.

**Pitfall 4: BYOL collapse without BatchNorm in predictor**
A team reimplemented BYOL without BatchNorm in the predictor MLP (replacing with LayerNorm). The model immediately collapsed — both networks output the same constant vector within the first 10 epochs. BatchNorm in the predictor is not optional in BYOL; it provides an implicit centering mechanism that prevents collapse. With Layer Norm or no norm, collapse happens consistently.

**Pitfall 5: Evaluating SSL with accuracy instead of linear probe accuracy**
A team compared a supervised ResNet-50 (76.1% top-1) vs a BYOL-pretrained ResNet-50 evaluated with KNN (only 74.3%) and concluded supervised was better. But BYOL fine-tuned achieves 79.6% — better than supervised. The comparison must use matched protocols. Linear probe and KNN evaluate representation quality; fine-tuning evaluates maximum downstream accuracy. These answer different questions and should not be compared directly.

---

## 11. Technologies & Tools

| Tool | Method Supported | Notes |
|------|-----------------|-------|
| Lightly AI | SimCLR, MoCo, BYOL, DINO, MAE | Best all-in-one SSL library |
| solo-learn | 20+ SSL methods | Research-oriented, PyTorch Lightning |
| VISSL (Meta) | SimCLR, MoCo, BYOL, DINO, BarlowTwins | Meta's production SSL library |
| timm | DINOv2, CLIP, MAE checkpoints | Best for loading pretrained SSL models |
| transformers (HF) | MAE, DINO, BEiT | HF API for masked autoencoders |
| open_clip | CLIP, SigLIP | Best variety of CLIP-style models |
| DINOv2 (Meta) | DINOv2 ViT-S/B/L/g checkpoints | Via torch.hub |
| FAISS | k-NN evaluation of SSL features | Standard for SSL representation evaluation |
| Weights & Biases | Training monitoring | Track SSL-specific metrics: kNN acc, collapse detection |

---

## 12. Interview Questions with Answers

**Q: What is the core idea behind contrastive learning?**
Contrastive learning trains a model by pulling together representations of different augmented views of the same image (positive pairs) and pushing apart representations of different images (negative pairs). The model is forced to learn semantically meaningful features — the only invariance that persists under augmentation (crop, color jitter, blur) corresponds to semantic content. The NT-Xent or InfoNCE loss formalizes this: it maximizes the cosine similarity between positive pairs while minimizing it between negatives, normalized by a temperature τ.

**Q: Why does SimCLR require large batch sizes and how does MoCo solve this?**
SimCLR's NT-Xent loss uses all 2(N-1) other samples in the batch as negatives for each query. With small batches (256), there are few hard negatives, the gradient signal is weak, and the learned representation is poor. Linear probe accuracy drops from 71.7% (batch 4096) to ~55% (batch 256). MoCo maintains a FIFO memory queue of 65,536 encoded keys from past batches. Every query is contrasted against 65,536 negatives regardless of the current batch size, making MoCo effective with batches as small as 256. The momentum encoder ensures queue features are consistent despite being generated at different training steps.

**Q: What is the momentum encoder in MoCo and BYOL and why is it needed?**
The momentum encoder is a copy of the main encoder updated as an exponential moving average: theta_k = m * theta_k + (1-m) * theta_q, with m=0.999 or 0.9999. It provides stable, slowly-changing targets for the online encoder to match. Without momentum: if the key encoder were updated by gradient descent, it would change rapidly, making the keys in the queue inconsistent (early keys encoded by a different encoder than current keys) — the contrastive loss would be unstable. In BYOL, the momentum target also prevents collapse by ensuring the target network changes slowly enough that the predictor always has a meaningful learning signal.

**Q: How does BYOL avoid representation collapse without negative pairs?**
BYOL prevents collapse through architectural asymmetry: the online network has a predictor MLP that the target network lacks. The online network must learn to predict the target's output through this predictor. If both collapsed to a constant, the predictor could minimize loss trivially, but the asymmetry means the gradient pushes the online network to produce informative outputs. Additionally, BatchNorm in the predictor implicitly centers the outputs across the batch, preventing any single dimension from dominating. The EMA update (not gradient descent) of the target network means it provides a slowly-moving, stable target that the online network cannot "hack."

**Q: What is the masking ratio in MAE and why is 75% better than 50%?**
MAE uses a 75% masking ratio — 3 of every 4 patches are hidden. At 75% masking, the remaining 25% of patches carry insufficient local context to reconstruct the masked patches by interpolating nearby pixels. The encoder must learn global image semantics to infer masked content (e.g., reconstructing an eye requires knowing it is a face). At 50% masking, adjacent visible patches provide enough local context that reconstruction can be done by simple texture copying, requiring no semantic understanding. The 75% ratio forces semantic learning by making the task hard enough that trivial solutions fail.

**Q: What is the difference between linear probing and full fine-tuning evaluation for SSL?**
Linear probing freezes the entire SSL pretrained backbone and trains only a linear classification head (or uses L-BFGS on extracted features) on the labeled downstream dataset. It measures representation quality in isolation: the features must be linearly separable for the downstream task. Full fine-tuning unfreezes the backbone and adapts all parameters to the downstream task. For contrastive methods (SimCLR, MoCo, BYOL), both evaluations give similar results (3–5% gap). For MAE, fine-tuning significantly outperforms linear probing (83.1% vs 73.5% for ViT-B) because the masked pretraining task produces features that need task-specific adaptation.

**Q: What are the augmentations used in contrastive learning and why are they chosen?**
The standard SimCLR augmentation pipeline: (1) RandomResizedCrop with scale (0.08–1.0) and ratio (0.75–1.33) — the most impactful; (2) RandomHorizontalFlip; (3) ColorJitter (strength 0.8) applied with probability 0.8; (4) RandomGrayscale with probability 0.2; (5) GaussianBlur with probability 0.5. These augmentations are chosen to be semantically invariant (flipping a car is still a car; changing color does not change the object) while creating sufficient visual diversity to make the contrastive task non-trivial. Augmentations like MixUp or CutMix are avoided as they change the underlying semantics of the image.

**Q: How does DINO prevent collapse without negative pairs?**
DINO uses two mechanisms: (1) Centering — the teacher's output logits are centered by subtracting an exponential moving average of the batch mean, preventing the teacher from assigning high probability to one mode consistently. (2) Sharpening — the teacher uses a lower temperature (0.04) than the student (0.1), producing sharper (more confident) teacher distributions. The student learns to match the teacher's sharp, centered distribution. These two effects counteract each other: sharpening causes collapse, centering prevents it. The precise interplay keeps training stable without negatives.

**Q: Why does DINO produce semantically meaningful attention maps without segmentation supervision?**
DINO's multi-crop augmentation (2 global views + 8 local crops) with the local-to-global correspondence objective forces the model to infer global object structure from local crops. This causes the attention heads to specialize: some heads attend to object boundaries, others to textures, others to object interiors. When visualizing the CLS token's attention to patch tokens in the last layer, the patches corresponding to the foreground object receive the highest attention weights — effectively performing unsupervised object segmentation. This emergent behavior does not appear in supervised ViT training because the supervision signal does not require spatially distinguishing object from background.

**Q: What is the projection head in contrastive SSL and why is it discarded at downstream use?**
The projection head (typically a 2–3 layer MLP) maps the backbone features to a lower-dimensional space (e.g., 128-d) where the contrastive loss is computed. The contrastive loss in the projection space encourages representation collapse along dimensions not captured by the 128-d projection — dimensions containing fine-grained classification-relevant information are lost. The backbone representations (before the projection head) retain this information because they were not directly constrained by the loss. Discarding the projection head and using the backbone features directly gives much better downstream accuracy (71.7% vs 56% for linear probe in SimCLR).

**Q: When should you use SSL instead of supervised ImageNet pretraining?**
Use SSL when: (1) your target domain has large unlabeled corpora but few labels (medical, satellite, industrial) — SSL on domain data beats ImageNet transfer; (2) you need to support many diverse tasks from one backbone, since SSL features are not overfit to ImageNet's 1000 classes; (3) you are in a low-data fine-tuning regime (< 1000 labels) — SSL features linearly probe better than supervised features in this regime; (4) you can use a strong pretrained SSL checkpoint (DINOv2, CLIP) rather than training from scratch. Do not use SSL when: labeled data is abundant (> 1M samples); supervised pretraining on ImageNet-21k is simpler and competitive. Do not train SSL from scratch on fewer than 100k images — the representations will be weaker than supervised ImageNet transfer.

**Q: What is BarlowTwins and how does it differ from BYOL and SimCLR?**
BarlowTwins (Zbontar et al., 2021) trains a network by making the cross-correlation matrix between embeddings of two augmented views as close to the identity matrix as possible. The diagonal terms (self-correlation) are pushed to 1 (making representations invariant to augmentation); the off-diagonal terms are pushed to 0 (decorrelating different dimensions, preventing redundancy). BarlowTwins does not use negative pairs (like BYOL) and does not require large batches or a memory queue (unlike SimCLR, MoCo). It is conceptually related to information maximization: encouraging each dimension to carry independent information prevents collapse. Linear probe: 73.2% (ResNet-50), competitive with SimCLR and MoCo.

**Q: How do you evaluate the quality of SSL representations before fine-tuning?**
Three standard protocols in increasing computational cost: (1) k-NN probe (k=20): extract features for the entire training set, compute k-nearest neighbors for each validation sample using cosine similarity, majority-vote for the label. No training required, ~10 min on 1 GPU. (2) Linear probe: train a linear head (or use L-BFGS) on frozen features, ~1 hour. (3) Attentive probe: train a small multi-head attention pooling layer on top of frozen patch tokens, better than linear for ViT. All three should be computed after each SSL training run. kNN probe accuracy correlates well with fine-tuning accuracy and is the fastest signal for monitoring SSL training progress.

**Q: What is SigLIP and how does it improve on CLIP?**
SigLIP (Sigmoid Loss for Language Image Pretraining, Zhai et al., 2023) replaces CLIP's softmax normalization with a sigmoid loss. In CLIP's InfoNCE, the softmax denominator includes all other in-batch samples, requiring large batches (32k) to have enough negatives. SigLIP applies a binary cross-entropy loss to each (image, text) pair independently: positives pushed to 1, negatives pushed to 0. This removes the dependence on batch composition, enabling training with smaller batches and better scaling. SigLIP achieves better zero-shot performance than CLIP at matched compute, and the sigmoid logits have better calibration for retrieval. SigLIP-SO400M achieves 83.2% zero-shot on ImageNet.

**Q: What is the difference between instance discrimination and clustering-based SSL?**
Instance discrimination (SimCLR, MoCo, BYOL, DINO) treats each image as its own class — two augmented views of the same image are positives; all other images are negatives. This works well but can push semantically similar images apart. Clustering-based methods (SwAV, DeepCluster) assign images to learned prototypes (cluster centroids) and enforce consistency between cluster assignments of different views. SwAV swaps cluster assignments between views: predict the cluster of view 2 from the embedding of view 1, and vice versa. This uses semantic clusters as implicit positives, avoiding the need for negative pairs while naturally grouping similar images together. SwAV achieves 75.3% linear probe with ResNet-50.

**Q: How does the DINOv2 data curation pipeline contribute to its strong performance?**
DINOv2's data curation, not just the training objective, is the key differentiator. The pipeline: (1) self-supervised deduplication — remove near-duplicate images using embeddings from a previous SSL checkpoint; (2) image quality filtering — remove images with low perceptual quality scores; (3) domain balancing — cluster images and ensure balanced coverage across visual domains; (4) dataset assembly — combine LVD-142M curated from web crawls with curated datasets (ImageNet-22k, Google Landmarks, etc.). The resulting 142M image dataset has higher quality and diversity than simply using all available internet images. The same DINO training objective on uncurated data achieves 3–5% lower linear probe accuracy.

---

## 13. Best Practices

1. Use existing pretrained SSL checkpoints (DINOv2, CLIP, OpenCLIP) before training SSL from scratch. Reproducing DINO or MAE training requires 8× A100 and 2–5 days; the checkpoints are publicly available.
2. For contrastive methods, use large batch sizes (4096+) or MoCo's queue to ensure sufficient in-batch diversity. The number of effective negatives determines representation quality.
3. RandomResizedCrop with scale=(0.08, 1.0) is the single most important augmentation for contrastive SSL. Do not replace it with fixed crops or center crops.
4. Monitor representation collapse during training: check if the standard deviation of embeddings across the batch approaches zero (all embeddings similar) or if kNN accuracy on a small validation set stagnates early.
5. For MAE, prefer fine-tuning over linear probing in production — MAE representations are designed to be fine-tuned. The 10% linear probe accuracy gap versus fine-tuning is expected and not a failure.
6. For domain-specific SSL (medical, satellite), pretrain on your domain data even with a small dataset (50k–200k images) — domain-specific features outperform generic ImageNet transfer when target domain statistics differ significantly.
7. Use LBFGS (not SGD) for linear probe evaluation — it converges in fewer iterations and gives more reliable accuracy estimates. Scikit-learn's LogisticRegression with solver='lbfgs' works on CPU for up to ~1M samples.
8. Report both linear probe and k-NN accuracy in research comparisons — kNN requires no hyperparameter tuning and is more reproducible; linear probe is the community standard but sensitive to optimizer and hyperparameter choice.
9. For BYOL and DINO, do not remove BatchNorm from the predictor MLP — it is a collapse prevention mechanism, not optional regularization.
10. When adapting a pretrained SSL model to a new domain, always start with linear probing to establish a baseline, then progressively unfreeze layers (last block → last 3 blocks → full model) to find the minimal fine-tuning needed to meet accuracy requirements.

---

## 14. Case Study

**Problem**: A radiology clinic has 200,000 unlabeled chest CT scans and only 3,000 labeled scans (normal vs 5 pathology categories). Supervised ImageNet transfer achieves 71% macro-F1. Target: > 80% macro-F1.

**Approach**: SSL pretraining on 200k unlabeled scans (CT slices as 2D images, 3 channel = slice-1 / slice / slice+1), then fine-tuning on 3k labeled scans.

**Method selection**: DINO was chosen over SimCLR/MoCo because CT scans have strong local texture correlation — DINO's emergent spatial attention is particularly useful for localizing pathology regions. MAE was the runner-up but DINO's linear probe quality is higher.

**Domain-specific augmentations**: Standard color jitter was replaced with Hounsfield unit (HU) window jitter (simulating different radiologist viewing windows: lung window -500 to +1500 HU, bone window -100 to +2000 HU). RandomRotation ± 30 degrees (CT scans have real anatomical rotation variation). Elastic deformations to simulate breathing motion artifact.

**Pretraining**: ViT-B/16 backbone, 100 epochs, 1024 batch size, 8× V100. Total wall time: 48 hours. Linear probe after pretraining: 68% macro-F1 (vs 71% supervised ImageNet transfer — near parity with 200k unlabeled data).

**Fine-tuning**: full fine-tuning with layer-wise LR decay (0.65), base LR 5e-5, 100 epochs. Results: 82% macro-F1 — 11% improvement over supervised baseline. Per-class analysis: rare pathology classes (pleural effusion: 180 labels) improved from 42% to 71% F1 — the SSL features captured relevant features from unlabeled data with that pathology.

**Production**: model exported to ONNX, quantized to INT8 (< 1% F1 drop), deployed on CPU inference cluster. 4 slices processed in parallel per scan; full scan (150 slices) classified in 2.5 seconds.
