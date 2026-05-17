# Training Deep Networks

## 1. Concept Overview

Training a deep neural network is an iterative optimization process: compute a forward pass to get predictions, calculate the loss, backpropagate gradients, and update parameters with an optimizer. The surface of the loss landscape for modern deep networks is non-convex with many local minima, saddle points, and sharp cliffs — yet stochastic gradient descent (SGD) and its variants reliably find solutions that generalize well. The difference between a model that converges and one that explodes, underfit, or overfits often comes down to the mechanics of the training loop: learning rate schedule, regularization, gradient management, and numerical precision.

This module covers the complete production training loop with all best practices: learning rate scheduling, gradient clipping, regularization techniques, data augmentation, mixed precision training, gradient accumulation, and early stopping.

---

## 2. Intuition

One-line analogy: the training loop is a hiker descending a foggy mountain — the learning rate controls step size (too large and you walk off a cliff, too small and you never reach the valley), the schedule adjusts step size as terrain changes, and gradient clipping prevents you from sliding uncontrollably on an icy slope.

Mental model: the optimizer is not a magic box. Every hyperparameter choice — learning rate, weight decay, batch size, warmup steps — directly affects the trajectory through the loss landscape. Understanding each component lets you diagnose training failures systematically rather than by trial and error.

Why it matters: a good architecture trained poorly will lose to a mediocre architecture trained well. Google Brain's research shows that training recipe (optimizer, LR schedule, augmentation) often contributes more to final accuracy than architectural choices alone.

Key insight: learning rate is the single most important hyperparameter. A correctly scheduled LR with warmup and cosine decay can recover from many poor choices elsewhere. No amount of architectural sophistication compensates for a wildly misset learning rate.

---

## 3. Core Principles

**Stochastic Gradient Descent (SGD)**: update parameters in the direction of negative gradient computed on a mini-batch. Noise from small batches acts as regularization (helps escape sharp minima).

**Adam optimizer**: maintains per-parameter adaptive learning rates using first moment (m_t, momentum) and second moment (v_t, uncentered variance). `m_t = beta1*m_{t-1} + (1-beta1)*g_t`, `v_t = beta2*v_{t-1} + (1-beta2)*g_t^2`. Parameter update: `theta -= lr * m_t_hat / (sqrt(v_t_hat) + eps)`. Defaults: lr=0.001, beta1=0.9, beta2=0.999, eps=1e-8. AdamW adds weight decay correctly (decoupled from gradient update — not equivalent to L2 regularization in Adam).

**Learning rate scheduling**: the LR should change over training. Warmup prevents large updates when parameters are poorly initialized; decay prevents overshooting near convergence.

**Gradient clipping**: limits gradient norm before the optimizer step to prevent exploding gradients from destabilizing training.

**Regularization**: techniques that penalize model complexity to improve generalization: L2 weight decay, dropout, label smoothing, data augmentation.

**Mixed precision**: use 16-bit floats (float16 or bfloat16) for most operations, keeping a float32 master copy for parameter updates. Reduces memory ~50% and speeds up matrix multiplications on Tensor Core GPUs.

---

## 4. Types / Architectures / Strategies

**Optimizers:**

| Optimizer | Adaptive LR | Momentum | Weight Decay | Best For |
|-----------|-------------|----------|-------------|---------|
| SGD | No | Optional (0.9 typical) | Native | CV tasks with tuned LR schedule |
| Adam | Yes (per-param) | Yes (beta1=0.9) | Not decoupled | NLP, fast convergence |
| AdamW | Yes | Yes | Decoupled (correct) | Transformers, modern default |
| RMSprop | Yes | No | Optional | RNNs |
| LAMB | Yes | Yes | Yes | Large-batch distributed training |

**Learning Rate Schedules:**

| Schedule | Shape | Best For |
|----------|-------|---------|
| Linear warmup + cosine decay | Ramps up then cosine curve down | Transformers, fine-tuning |
| OneCycleLR | Triangle up then fast cosine down | Super-convergence for CNNs |
| ReduceLROnPlateau | Halve LR when val loss plateaus | When schedule isn't known upfront |
| Constant + step decay | Flat then sudden drops | Classic SGD training |
| Warmup + linear decay | Ramps then linear down | BERT-style fine-tuning |

**Regularization Techniques:**

| Technique | Mechanism | Typical Value |
|-----------|-----------|--------------|
| L2 weight decay | Penalizes large weights | 1e-4 to 1e-2 |
| Dropout | Random neuron zeroing | 0.1-0.5 depending on layer |
| Label smoothing | Softens one-hot targets | 0.1 |
| Batch size | Larger batches = less noise = less regularization | 32-256 for images |
| Early stopping | Stop when val loss stops improving | patience=10 epochs |

---

## 5. Architecture Diagrams

```
Complete Training Loop Flow:
                      +-----------+
  Training Data  -->  | DataLoader| --> batches (x, y)
                      +-----------+
                            |
                   [model.train()]
                            |
                   [optimizer.zero_grad()]      <- STEP 1: clear gradients
                            |
                   [Forward Pass: y_hat = model(x)]
                            |
                   [Loss = criterion(y_hat, y)]
                            |
                   [loss.backward()]             <- STEP 2: compute gradients
                            |
                   [clip_grad_norm_(max_norm=1)] <- STEP 3: clip (optional)
                            |
                   [optimizer.step()]            <- STEP 4: update parameters
                            |
                   [scheduler.step()]            <- STEP 5: update LR
                            |
                        (repeat)
                            |
              [Validation loop: model.eval() + torch.no_grad()]
                            |
              [Early stopping check: val_loss improved?]

Learning Rate Schedule (Warmup + Cosine Decay):
LR
^
|          /\
|         /  \
|        /    \__________
|       /                \
|      /                  \___
|     /                        \____
|----/                               \----
+-------------------------------------------> Training Steps
   warmup    peak            cosine decay   final_lr
   (5-10%    (lr_max)        (most of       (lr_min, e.g. 0)
   of steps)                 training)

Mixed Precision Training:
  FP16 weights + FP16 activations  <-- forward/backward (fast, low memory)
        |
  [GradScaler: multiply loss by scale_factor (e.g., 2^16)]
        |
  FP32 master weights               <-- optimizer update (precise)
        |
  [GradScaler: unscale, check for inf/nan, skip step if found]
        |
  FP16 weights for next forward pass
```

---

## 6. How It Works — Detailed Mechanics

### The Complete Best-Practices Training Loop

```python
import torch
import torch.nn as nn
import torch.optim as optim
from torch.cuda.amp import GradScaler, autocast
from torch.utils.data import DataLoader
from typing import Optional
import math


def train_one_epoch(
    model: nn.Module,
    loader: DataLoader,
    optimizer: optim.Optimizer,
    criterion: nn.Module,
    scaler: GradScaler,
    device: torch.device,
    gradient_accumulation_steps: int = 1,
    max_grad_norm: float = 1.0,
    epoch: int = 0,
) -> dict[str, float]:
    model.train()
    total_loss = 0.0
    total_correct = 0
    total_samples = 0
    optimizer.zero_grad()  # zero once before accumulation loop

    for step, (x, y) in enumerate(loader):
        x, y = x.to(device, non_blocking=True), y.to(device, non_blocking=True)

        # Mixed precision forward pass
        with torch.amp.autocast(device_type="cuda", dtype=torch.float16):
            logits = model(x)
            loss = criterion(logits, y)
            # Scale loss for gradient accumulation (average, not sum)
            loss = loss / gradient_accumulation_steps

        # Backward with gradient scaling
        scaler.scale(loss).backward()

        if (step + 1) % gradient_accumulation_steps == 0:
            # Unscale before clipping (scaler modifies gradients for fp16)
            scaler.unscale_(optimizer)
            grad_norm = nn.utils.clip_grad_norm_(model.parameters(), max_norm=max_grad_norm)

            # scaler.step() calls optimizer.step() only if no inf/nan gradients
            scaler.step(optimizer)
            scaler.update()
            optimizer.zero_grad()  # zero after step for accumulation

        total_loss += loss.item() * gradient_accumulation_steps  # undo scaling for logging
        preds = logits.argmax(dim=1)
        total_correct += (preds == y).sum().item()
        total_samples += y.size(0)

    return {
        "loss": total_loss / len(loader),
        "accuracy": total_correct / total_samples,
    }


@torch.no_grad()
def validate(
    model: nn.Module,
    loader: DataLoader,
    criterion: nn.Module,
    device: torch.device,
) -> dict[str, float]:
    model.eval()  # CRITICAL: disables dropout, uses BN running stats
    total_loss = 0.0
    total_correct = 0
    total_samples = 0

    for x, y in loader:
        x, y = x.to(device, non_blocking=True), y.to(device, non_blocking=True)
        with torch.amp.autocast(device_type="cuda", dtype=torch.float16):
            logits = model(x)
            loss = criterion(logits, y)
        total_loss += loss.item()
        total_correct += (logits.argmax(1) == y).sum().item()
        total_samples += y.size(0)

    return {
        "val_loss": total_loss / len(loader),
        "val_accuracy": total_correct / total_samples,
    }
```

### Learning Rate Schedules

```python
def build_scheduler(
    optimizer: optim.Optimizer,
    total_steps: int,
    warmup_fraction: float = 0.05,  # 5% of total steps for warmup
) -> torch.optim.lr_scheduler.LambdaLR:
    """Linear warmup + cosine decay — standard for Transformers and fine-tuning."""
    warmup_steps = int(total_steps * warmup_fraction)

    def lr_lambda(current_step: int) -> float:
        if current_step < warmup_steps:
            # Linear warmup: 0 -> 1
            return float(current_step) / max(1, warmup_steps)
        # Cosine decay: 1 -> 0
        progress = float(current_step - warmup_steps) / max(1, total_steps - warmup_steps)
        return max(0.0, 0.5 * (1.0 + math.cos(math.pi * progress)))

    return torch.optim.lr_scheduler.LambdaLR(optimizer, lr_lambda)


def build_one_cycle_scheduler(
    optimizer: optim.Optimizer,
    max_lr: float,
    steps_per_epoch: int,
    epochs: int,
) -> torch.optim.lr_scheduler.OneCycleLR:
    """
    OneCycleLR: ramp LR up to max_lr, then cosine down.
    Super-convergence: can train CNNs 10x faster with appropriate max_lr.
    Use torch-lr-finder to determine max_lr experimentally.
    """
    return torch.optim.lr_scheduler.OneCycleLR(
        optimizer,
        max_lr=max_lr,
        steps_per_epoch=steps_per_epoch,
        epochs=epochs,
        pct_start=0.3,        # 30% of steps for warmup phase
        anneal_strategy="cos",
        div_factor=25.0,      # initial_lr = max_lr / 25
        final_div_factor=1e4, # final_lr = initial_lr / 10000
    )
```

### Gradient Accumulation for Large Effective Batch Size

```python
# Simulate batch size 1024 when GPU memory only fits 128 samples
# gradient_accumulation_steps = 1024 / 128 = 8
# Loss must be divided by accumulation steps to keep scale consistent

accumulation_steps = 8
optimizer.zero_grad()

for micro_step, (x, y) in enumerate(loader):
    with torch.amp.autocast(device_type="cuda", dtype=torch.float16):
        loss = criterion(model(x), y) / accumulation_steps  # normalize

    scaler.scale(loss).backward()  # accumulate gradients

    if (micro_step + 1) % accumulation_steps == 0:
        scaler.unscale_(optimizer)
        nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        scaler.step(optimizer)
        scaler.update()
        optimizer.zero_grad()
        scheduler.step()
```

### Data Augmentation

```python
from torchvision import transforms
import torchvision.transforms.functional as TF
import torch
from torch import Tensor


# Standard augmentation for ImageNet training
train_transform = transforms.Compose([
    transforms.RandomResizedCrop(224, scale=(0.08, 1.0)),  # random crop from 224-resized
    transforms.RandomHorizontalFlip(p=0.5),
    transforms.ColorJitter(brightness=0.4, contrast=0.4, saturation=0.4, hue=0.1),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
    transforms.RandomErasing(p=0.25, scale=(0.02, 0.33)),  # CutOut variant
])

# No augmentation at validation/test time — only normalization
val_transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])


def mixup_batch(
    x: Tensor,
    y: Tensor,
    alpha: float = 0.2,
) -> tuple[Tensor, Tensor, Tensor, float]:
    """
    Mixup data augmentation: interpolates two samples linearly.
    label = lambda * y_a + (1-lambda) * y_b (soft labels)
    lambda ~ Beta(alpha, alpha); alpha=0.2 is typical.
    """
    lam = float(torch.distributions.Beta(alpha, alpha).sample())
    batch_size = x.size(0)
    index = torch.randperm(batch_size, device=x.device)
    mixed_x = lam * x + (1 - lam) * x[index]
    y_a, y_b = y, y[index]
    return mixed_x, y_a, y_b, lam


def label_smoothing_loss(
    logits: Tensor,
    targets: Tensor,
    num_classes: int,
    smoothing: float = 0.1,
) -> Tensor:
    """
    Label smoothing: replace hard 1-hot targets with:
      (1-eps) for correct class, eps/(K-1) for others.
    Prevents overconfidence; improves calibration and generalization.
    nn.CrossEntropyLoss(label_smoothing=0.1) is the simpler alternative.
    """
    confidence = 1.0 - smoothing
    smooth_val = smoothing / (num_classes - 1)
    one_hot = torch.zeros_like(logits).scatter_(1, targets.unsqueeze(1), 1)
    smooth_labels = one_hot * confidence + (1 - one_hot) * smooth_val
    log_probs = torch.log_softmax(logits, dim=1)
    return -(smooth_labels * log_probs).sum(dim=1).mean()
```

### Early Stopping

```python
class EarlyStopping:
    def __init__(self, patience: int = 10, min_delta: float = 1e-4, mode: str = "min") -> None:
        self.patience = patience
        self.min_delta = min_delta
        self.mode = mode
        self.best_score: float | None = None
        self.counter = 0
        self.should_stop = False

    def step(self, score: float) -> bool:
        """Returns True if training should stop."""
        if self.best_score is None:
            self.best_score = score
        elif self._is_improvement(score):
            self.best_score = score
            self.counter = 0
        else:
            self.counter += 1
            if self.counter >= self.patience:
                self.should_stop = True
        return self.should_stop

    def _is_improvement(self, score: float) -> bool:
        if self.mode == "min":
            return score < self.best_score - self.min_delta
        return score > self.best_score + self.min_delta
```

---

## 7. Real-World Examples

**GPT-3 training recipe**: AdamW with lr=6e-4, beta1=0.9, beta2=0.95, weight_decay=0.1. Linear warmup over 375M tokens (0.003% of 300B total), cosine decay to 10% of peak LR. Gradient clipping max_norm=1.0. Batch size 3.2M tokens (achieved via gradient accumulation across 256 A100 GPUs). Mixed precision (bfloat16 for stability over float16).

**ImageNet training (ResNet-50)**: SGD with momentum=0.9, lr=0.1, weight_decay=1e-4. LR decays by 10x at epochs 30, 60, 90. Batch size 256. Training time: ~90 epochs, ~24 hours on 4 V100s. Mixed precision cuts this to ~14 hours.

**BERT fine-tuning**: AdamW, lr=2e-5, warmup over 6% of steps, linear decay. Batch size 32. Weight decay 0.01. 3-5 epochs on downstream tasks. Label smoothing not used (BERT uses cross-entropy directly).

**Production training at Stability AI (Stable Diffusion)**: gradient accumulation across 32 A100s to simulate batch size 2048. GradScaler with initial scale 65536 (2^16). Checkpoint saving every 5000 steps (training crashed ~3 times, requiring restarts from checkpoints).

---

## 8. Tradeoffs

| Configuration | Memory | Speed | Stability | Accuracy |
|--------------|--------|-------|---------|---------|
| fp32 training | Baseline | Baseline | Best | Baseline |
| fp16 + GradScaler | ~50% less | 1.5-2x faster | Good (can have NaN) | Same |
| bf16 (Ampere+) | ~50% less | 1.5-2x faster | Best (no overflow) | Same |
| Gradient accumulation (8 steps) | Same | ~Same (I/O bound) | Same | Better (larger effective batch) |

| LR Schedule | Convergence Speed | Final Accuracy | Robustness to LR Choice |
|-------------|-----------------|---------------|------------------------|
| Constant | Fast initially | Poor | Low |
| Step decay | Good | Good | Medium |
| Cosine decay | Good | Better | Medium |
| Warmup + cosine | Best for large models | Best | High |
| OneCycleLR | Super-convergence possible | Excellent | Low (sensitive to max_lr) |

---

## 9. When to Use / When NOT to Use

**Use mixed precision when:**
- Training on Volta (V100), Turing (T4/RTX 20xx), Ampere (A100/RTX 30xx) or newer GPU
- Memory is the primary bottleneck (enables larger batches or models)
- Prefer bfloat16 on Ampere+ GPUs (wider dynamic range, no overflow risk)

**Use gradient accumulation when:**
- GPU memory limits batch size but theory or empirical results suggest larger batches help
- Training large language models or diffusion models (effective batches of thousands)

**Use warmup when:**
- Training Transformers (nearly always needed — cold parameters produce unstable updates at high LR)
- Fine-tuning pretrained models (5-10% warmup is standard)
- Warmup fraction: 5-10% of total training steps; linear warmup is standard

**Do NOT use:**
- High constant LR throughout training — always decay, especially near convergence
- SGD without momentum for Transformers — Adam/AdamW converges much faster
- label_smoothing on binary classification — CE with logits is sufficient

---

## 10. Common Pitfalls

**War story 1 — optimizer.zero_grad() called in wrong place:**
A training loop called `optimizer.zero_grad()` after `optimizer.step()`, not before `loss.backward()`. Because `zero_grad()` came after the step, it cleared gradients immediately after they were used — this seems correct. But when gradient accumulation was added (accumulating over 4 batches before stepping), the developer placed `zero_grad()` at the end of every iteration, clearing gradients before all 4 accumulation steps completed. The actual gradient used was always the last micro-batch only. Effective batch size was 1x not 4x. Fix: zero_grad at the start of the accumulation block; step and zero only every N micro-batches.

```python
# BROKEN: zero_grad at top of every iteration during accumulation
for step, (x, y) in enumerate(loader):
    optimizer.zero_grad()             # clears accumulated gradients!
    loss = criterion(model(x), y) / accum_steps
    loss.backward()
    if (step + 1) % accum_steps == 0:
        optimizer.step()

# FIX: zero_grad only at the accumulation boundary
optimizer.zero_grad()
for step, (x, y) in enumerate(loader):
    loss = criterion(model(x), y) / accum_steps
    loss.backward()
    if (step + 1) % accum_steps == 0:
        nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        optimizer.zero_grad()
```

**War story 2 — GradScaler not updated after NaN gradients:**
A training run had fp16 overflow: gradients became inf/nan for certain batches with large activations. `scaler.step()` correctly skips the optimizer update when inf/nan is detected. However, the developer also called `scheduler.step()` unconditionally. The scheduler incremented its step counter even when the optimizer did not take a step, causing LR to decay faster than intended — by epoch 5, LR was 30% lower than expected. This resulted in slower final convergence. Fix: only call `scheduler.step()` when the optimizer step actually executed (check `scaler.get_scale()` or track whether the step was skipped).

```python
# FIX: track whether optimizer actually stepped
old_scale = scaler.get_scale()
scaler.step(optimizer)
scaler.update()
new_scale = scaler.get_scale()
if old_scale == new_scale:  # scale did not decrease -> no inf/nan -> step was taken
    scheduler.step()
```

**War story 3 — Augmentation applied at validation time:**
A team applied `RandomCrop` and `RandomHorizontalFlip` in a single transform pipeline used for both train and validation loaders. Validation accuracy fluctuated by +-3% across runs depending on which random crops were applied. The model appeared to stop improving at epoch 12 based on one run but improved until epoch 20 in another. Early stopping triggered prematurely. Fix: separate transforms for train (augmented) and val (deterministic center crop + normalize only).

**War story 4 — Weight decay applied to bias and BatchNorm parameters:**
A training run applied AdamW weight decay to all parameters including bias terms and BatchNorm's gamma/beta. This is incorrect: weight decay penalizes the magnitude of all parameters, including BatchNorm scale parameters that should be free to take any value for normalization. The BatchNorm layers struggled to learn proper scale factors, degrading validation accuracy by ~1.5% on ImageNet. Fix: exclude bias and normalization parameters from weight decay using parameter groups.

```python
def get_optimizer_groups(
    model: nn.Module, weight_decay: float = 1e-4
) -> list[dict]:
    """Exclude bias, BN weight/bias from weight decay — standard best practice."""
    decay_params, no_decay_params = [], []
    for name, param in model.named_parameters():
        if not param.requires_grad:
            continue
        if param.ndim <= 1 or name.endswith(".bias"):
            no_decay_params.append(param)  # bias, BN params (1D)
        else:
            decay_params.append(param)     # weight matrices (2D+)
    return [
        {"params": decay_params,    "weight_decay": weight_decay},
        {"params": no_decay_params, "weight_decay": 0.0},
    ]

optimizer = optim.AdamW(get_optimizer_groups(model, weight_decay=1e-4), lr=1e-3)
```

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `torch.amp.autocast` | Automatic mixed precision context manager |
| `torch.cuda.amp.GradScaler` | Gradient scaling for fp16 stability |
| `torch.optim.AdamW` | Decoupled weight decay optimizer (standard for Transformers) |
| `torch.optim.lr_scheduler` | Built-in LR schedulers (OneCycleLR, CosineAnnealingLR, etc.) |
| `torch.nn.utils.clip_grad_norm_` | Gradient norm clipping |
| `torchmetrics` | Standard metric implementations (accuracy, F1, AUROC) |
| Weights & Biases (`wandb`) | Experiment tracking, LR curves, gradient histograms |
| TensorBoard | Loss curves, learning rate visualization |
| `torch.compile` (PyTorch 2.0+) | Graph compilation for 1.5-2x training speedup |
| `torch.utils.data.DataLoader` | `num_workers=4`, `pin_memory=True`, `persistent_workers=True` |

Key DataLoader settings for GPU training:
```python
loader = DataLoader(
    dataset,
    batch_size=256,
    shuffle=True,
    num_workers=4,           # parallel data loading (typical: 4-8)
    pin_memory=True,         # page-lock host memory for faster H->D transfer
    persistent_workers=True, # keep worker processes alive between epochs
    prefetch_factor=2,       # each worker prefetches 2 batches ahead
    drop_last=True,          # drop final incomplete batch (important for BatchNorm)
)
```

---

## 12. Interview Questions with Answers

**Q: What is the correct order of operations in a training iteration?**
The correct order is: (1) `optimizer.zero_grad()` — clear accumulated gradients from the previous step; (2) `model(x)` — forward pass to compute predictions; (3) `criterion(output, y)` — compute scalar loss; (4) `loss.backward()` — compute gradients via backpropagation; (5) `clip_grad_norm_` (optional but recommended) — clip gradient norm; (6) `optimizer.step()` — update parameters using gradients; (7) `scheduler.step()` — advance LR schedule. Calling `zero_grad` after `backward` but before `step` discards computed gradients. Calling `step` before `backward` uses stale gradients from the previous iteration.

**Q: What is Adam and how does it differ from SGD?**
Adam (Adaptive Moment Estimation) maintains per-parameter adaptive learning rates by tracking the first moment (exponential moving average of gradients, beta1=0.9) and second moment (exponential moving average of squared gradients, beta2=0.999). The update divides the gradient by the square root of the second moment plus epsilon (eps=1e-8), effectively normalizing each parameter's update by its historical gradient magnitude. Parameters with consistent large gradients get smaller effective LRs; rare-but-important parameters get larger effective updates. SGD uses the same LR for all parameters. Adam converges faster and requires less LR tuning, but SGD + momentum often achieves slightly better final accuracy on computer vision tasks with careful tuning.

**Q: What is learning rate warmup and why is it important?**
Warmup linearly increases the LR from near-zero to the target LR over the first 5-10% of training steps. At initialization, parameters are random and the loss landscape is steep and unstable. A large LR at this stage causes large, noisy gradient steps that can push parameters into poor regions of the loss landscape from which recovery is slow. Warmup gives the optimizer a chance to orient itself with small, conservative steps before taking larger ones. It is especially critical for Transformers: without warmup, Adam's adaptive learning rates are poorly calibrated (the second moment estimate is initialized to zero and converges over many steps), causing the effective LR to be much larger than intended in early iterations.

**Q: How does mixed precision training work and what are its benefits?**
Mixed precision uses 16-bit floats (float16 or bfloat16) for the forward and backward passes (matrix multiplications, activations, gradients), while maintaining a float32 master copy of parameters for the optimizer update. Benefits: ~50% memory reduction (fp16 tensors are half the size of fp32), 1.5-2x throughput speedup on Tensor Core GPUs (which have 2-8x higher throughput for fp16 matmul vs fp32), and larger effective batch sizes. Risk: fp16 has a smaller dynamic range (max ~65504) than fp32, so gradients can overflow to inf or underflow to 0. GradScaler addresses this by multiplying the loss by a large scale factor (typically 2^16 = 65536) before backward, then unscaling gradients before the optimizer step. bfloat16 (Ampere+ GPUs) has the same range as fp32 with lower precision, avoiding overflow entirely.

**Q: What is gradient accumulation and when would you use it?**
Gradient accumulation simulates a larger effective batch size by accumulating gradients over multiple forward/backward passes before calling `optimizer.step()`. If GPU memory fits only 32 samples but you want an effective batch size of 256, set accumulation_steps=8 and divide the loss by 8 at each micro-step. After 8 micro-steps, call step() and zero_grad(). The parameter update is mathematically identical to a single 256-sample batch. Use it when training large models (LLMs, diffusion models) where even a single example barely fits in GPU memory, or when theory indicates larger batches improve convergence (as in distributed training parity).

**Q: What is the difference between L2 weight decay in Adam vs AdamW?**
In standard Adam with L2 regularization, the regularization gradient (lambda * theta) is added to the gradient before computing the adaptive update. Because the adaptive update divides by the second moment estimate, the effective weight decay is scaled by the per-parameter learning rate, making it stronger for parameters with small gradients and weaker for those with large gradients. AdamW (decoupled weight decay) adds weight decay directly to the parameter after the adaptive gradient update: `theta -= lr * (adaptive_update + lambda * theta)`. This is the mathematically correct implementation and consistently outperforms L2 Adam on Transformer models. Always prefer AdamW over Adam + L2 for modern deep learning.

**Q: What is label smoothing and what problem does it solve?**
Label smoothing replaces the hard 1-hot target vector with soft targets: `(1-eps)` for the correct class and `eps/(K-1)` for all others (typical eps=0.1). It prevents the model from becoming overconfident — cross-entropy loss with hard targets pushes logits to +infinity for the correct class, which saturates softmax and makes the model poorly calibrated. Label smoothing provides a calibration benefit (predicted probabilities better reflect true uncertainty) and a slight accuracy improvement by acting as regularization. It is standard in image classification (Inception-v3+, EfficientNet training recipes) and machine translation (Transformer original paper). Do not use it for knowledge distillation (which uses soft teacher labels as targets) or tasks where 100% confidence is correct.

**Q: How does OneCycleLR differ from cosine annealing and when would you use each?**
OneCycleLR ramps LR from a low value up to max_lr (typically over 30% of training) then decays via cosine to a very low final LR. The rising phase can enable "super-convergence" — reaching good accuracy in 1/10 the usual epochs when max_lr is correctly calibrated (found via LR range test). Cosine annealing simply decays the LR from start to end following a cosine curve, often combined with linear warmup. Use OneCycleLR for CNNs on image tasks when training time is constrained and you are willing to tune max_lr via the LR finder. Use warmup + cosine for Transformers and fine-tuning tasks where training is less sensitive to max_lr and standard recipes exist.

**Q: What is the effect of batch size on training dynamics?**
Larger batches produce more accurate gradient estimates (lower variance) but reduce the implicit regularization from SGD noise, often leading to models that generalize worse (sharp minima). The linear scaling rule states: if batch size increases by k, multiply LR by k to maintain training dynamics. In practice, this rule breaks down for very large batches (> 8192). Warmup becomes even more important at large batch sizes. Small batches (32-64) provide stronger regularization via noise, which can improve final accuracy at the cost of slower convergence per epoch. For Transformers, batch size (in tokens) of 256K-2M is common in pretraining; fine-tuning uses 16-128 samples.

**Q: What is early stopping and what are the tradeoffs of different patience values?**
Early stopping terminates training when validation loss has not improved by more than `min_delta` for `patience` consecutive epochs. Low patience (5) stops training quickly but may terminate before the model reaches its best generalization — val loss often plateaus then improves again after the optimizer escapes a local plateau. High patience (20) wastes compute on a potentially overfit model. Typical values: patience=10 for image classification (epochs are fast), patience=5 for large-model fine-tuning (epochs are slow). Save the best checkpoint (by val loss, not by final weights) and restore it after early stopping triggers. Monitor val loss, not train loss — the model is early-stopped based on generalization performance.

**Q: What DataLoader settings matter most for GPU training performance?**
`num_workers` controls how many parallel CPU processes load and preprocess data while the GPU is computing. Setting num_workers=0 means the main process loads data, causing the GPU to wait. num_workers=4 is typical — a rule of thumb is 2-4x the number of GPUs. `pin_memory=True` pre-allocates the host-side tensor in page-locked (pinned) memory, enabling faster CPU-to-GPU transfers via DMA. `persistent_workers=True` keeps worker processes alive between epochs (avoids the overhead of spawning workers per epoch, which can take 30+ seconds for large num_workers). `prefetch_factor=2` (default) means each worker prefetches 2 batches ahead of the current batch, keeping the data pipeline full.

**Q: How do you implement gradient checkpointing and what is the tradeoff?**
Gradient checkpointing (`torch.utils.checkpoint.checkpoint`) trades memory for compute. Instead of storing all intermediate activations during the forward pass (needed for backward), only selected "checkpoint" activations are stored. During backward, the missing intermediate activations are recomputed from the nearest checkpoint. This reduces activation memory by ~sqrt(N) for a model with N layers (storing every sqrt(N)-th activation). The cost is ~33% more forward compute (one extra partial forward pass during backward). Use gradient checkpointing when training very deep models or large batch sizes that would otherwise OOM. In Transformers, it is applied per-layer: `nn.utils.checkpoint.checkpoint(layer, x)`.

---

## 13. Best Practices

- Use AdamW, not Adam, for all Transformer and modern network training. Correct weight decay matters.
- Exclude bias and normalization parameters (BatchNorm gamma/beta) from weight decay using parameter groups.
- Always use learning rate warmup for Transformers (5-10% of steps) and for large-batch training of any architecture.
- Mixed precision is the default for any GPU training — use `torch.amp.autocast` + `GradScaler` for fp16, or just autocast with bfloat16 on Ampere+ GPUs.
- Set DataLoader `num_workers=4`, `pin_memory=True`, `persistent_workers=True` — these settings routinely double training throughput by eliminating the data loading bottleneck.
- Monitor gradient norms every N steps — log the value returned by `clip_grad_norm_` before clipping. Consistently large norms indicate architectural or LR issues; values near or below max_norm indicate healthy training.
- Use `drop_last=True` in DataLoader for training — the final batch may be much smaller, causing BatchNorm to compute unreliable statistics and creating a LR schedule artifact.
- Save checkpoints every K epochs (not just the best) — training crashes happen; a checkpoint from 20 epochs ago is better than starting over.
- Use `torch.compile` (PyTorch 2.0+) on stable training loops for 1.5-2x free speedup: `model = torch.compile(model)`.
- Apply augmentation only to training data, never to validation or test data.

---

## 14. Case Study

**Task**: Train EfficientNet-B3 from scratch on a custom 50-class product image dataset with 200,000 images using the complete best-practices training loop.

**Problem Statement**: E-commerce product classification. 200K images, 50 classes. Some classes have 10K images (electronics), others 500 (rare accessories) — 20:1 imbalance. Target: 93%+ validation accuracy. Budget: 2x A100 40GB GPUs, 48 hours max.

**Training Recipe**:

```
Model: EfficientNet-B3 pretrained on ImageNet (torchvision)
Optimizer: AdamW, lr=3e-4, weight_decay=0.05 (no decay on bias/BN params)
LR Schedule: linear warmup 5% steps + cosine decay to 1e-6
Augmentation: RandAugment(n=2, m=9), Mixup(alpha=0.2), CutMix(alpha=1.0)
Loss: CrossEntropyLoss(label_smoothing=0.1, weight=class_weights)
Mixed precision: fp16 + GradScaler
Gradient clipping: max_norm=1.0
Gradient accumulation: 4 steps (effective batch = 4 * 64 = 256)
Early stopping: patience=10, monitor=val_accuracy
Epochs: 100 max (early stopping expected ~60-70)
DataLoader: num_workers=8, pin_memory=True, persistent_workers=True
```

```python
import torch
import torch.nn as nn
import torch.optim as optim
import torchvision.models as models
from torch.cuda.amp import GradScaler
from torch.utils.data import DataLoader, WeightedRandomSampler
import math


def build_model(num_classes: int = 50) -> nn.Module:
    model = models.efficientnet_b3(weights=models.EfficientNet_B3_Weights.IMAGENET1K_V1)
    # Replace classifier
    in_features = model.classifier[1].in_features  # 1536 for B3
    model.classifier = nn.Sequential(
        nn.Dropout(p=0.3, inplace=True),
        nn.Linear(in_features, num_classes),
    )
    return model


def main() -> None:
    device = torch.device("cuda")
    model = build_model(50).to(device)
    model = torch.compile(model)  # PyTorch 2.0+ compilation

    param_groups = get_optimizer_groups(model, weight_decay=0.05)
    optimizer = optim.AdamW(param_groups, lr=3e-4)

    steps_per_epoch = 200_000 // 256  # effective batch size 256
    total_steps = 100 * steps_per_epoch
    scheduler = build_scheduler(optimizer, total_steps, warmup_fraction=0.05)
    scaler = GradScaler()
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)  # class weights omitted for brevity
    early_stopper = EarlyStopping(patience=10, mode="max")

    best_val_acc = 0.0
    for epoch in range(100):
        train_metrics = train_one_epoch(
            model, train_loader, optimizer, criterion, scaler,
            device, gradient_accumulation_steps=4, max_grad_norm=1.0, epoch=epoch
        )
        # Manually step scheduler per micro-step in train_one_epoch (not shown here for brevity)
        val_metrics = validate(model, val_loader, criterion, device)

        if val_metrics["val_accuracy"] > best_val_acc:
            best_val_acc = val_metrics["val_accuracy"]
            torch.save(model.state_dict(), "best_model.pt")

        if early_stopper.step(val_metrics["val_accuracy"]):
            print(f"Early stopping at epoch {epoch}")
            break

    print(f"Best validation accuracy: {best_val_acc:.4f}")
```

**Results**:
- Epoch 1 val accuracy: 41% (head training dominating, backbone partially frozen via low LR from warmup)
- Epoch 30 val accuracy: 87% (cosine decay well underway)
- Epoch 58 val accuracy: 94.2% (best) — early stopping triggers at epoch 68 (no improvement for 10 epochs)
- Training time: 31 hours on 2x A100 (well within 48h budget)
- Peak GPU memory per card: 28GB (fp16 + gradient accumulation enabled fitting B3 in 40GB)

**Key Lessons**:
- Mixup + CutMix + label_smoothing together reduced overfitting significantly: without them, the gap between train accuracy (99%) and val accuracy (89%) was 10 points; with them, gap narrowed to 4 points (train 98%, val 94%).
- Correct weight decay grouping (exclude bias/BN) improved val accuracy by 0.8% vs applying weight_decay=0.05 to all parameters.
- `torch.compile` reduced per-step time from 340ms to 245ms (28% faster) with no code changes.
- GradScaler skipped ~0.3% of optimizer steps due to fp16 overflow (large activations in certain augmented batches). This was benign but logged as a metric to distinguish from structural training instability.
- Gradient norm monitoring showed healthy training: norm consistently in range 0.2-0.8 throughout, only occasionally reaching the clip threshold of 1.0 in the high-LR phase of OneCycleLR.
