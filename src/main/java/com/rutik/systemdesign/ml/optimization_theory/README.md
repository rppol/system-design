# Optimization Theory for ML

## 1. Concept Overview

Optimization is the process of finding parameter values that minimize (or maximize) an objective function. In machine learning, the objective is typically a loss function measuring how poorly the model's predictions match the ground truth. Training a neural network is entirely an optimization problem: starting from random weights, iteratively adjust them to reduce loss.

The dominant approach is first-order gradient-based optimization — using the gradient of the loss to determine the direction and magnitude of each parameter update. Second-order methods (using curvature information) are more powerful but computationally infeasible for models with billions of parameters. Modern optimizers like Adam approximate curvature adaptively, capturing most of the benefit at first-order cost.

Understanding optimization theory explains why models sometimes fail to train (learning rate too high, gradient explosion), why they converge slowly (learning rate too low, ill-conditioned loss landscape), and which optimizer is appropriate for which setting.

---

## 2. Intuition

> **One-line analogy**: Training a neural network is like hiking down a foggy mountainous terrain — you can only feel the local slope (gradient) underfoot, so you must choose your step size and direction wisely to reach the valley.

**Mental model**: Imagine the loss landscape as a high-dimensional terrain. A valley is a local minimum; a bowl-shaped landscape is convex and has one global minimum. In the fog (you cannot see far), you take a step proportional to the local slope (gradient descent). If the step is too large, you overshoot into a higher region; if too small, you take forever. Momentum is like gaining speed rolling downhill — it helps you coast through shallow flat regions. Adam equips every parameter with its own adaptive step size based on how noisy its gradient has been.

**Why it matters**: The optimizer determines whether a model trains at all, how fast it converges, and its final performance. Using SGD with default learning rate on a transformer will diverge; using Adam with the wrong beta values will underfit. Practical ML engineering requires knowing the optimizer, the schedule, gradient clipping thresholds, and when to switch.

**Key insight**: Most neural network loss landscapes are non-convex and high-dimensional, but they rarely have problematic local minima — overparameterized networks tend to have many global (or near-global) minima connected by flat paths. The real obstacles are saddle points, sharp valleys (ill-conditioning), and gradient pathologies (exploding/vanishing).

---

## 3. Core Principles

- **Gradient descent**: theta = theta - lr * grad_theta(L). The gradient points toward steepest ascent; we go the opposite direction.
- **Learning rate (lr)**: the most important hyperparameter; too large = divergence; too small = slow convergence.
- **Stochastic gradient**: instead of computing the gradient on the entire dataset (expensive), use a mini-batch of 32-256 samples. Gradient estimate is noisy but unbiased.
- **Convergence for convex functions**: gradient descent converges to the global minimum; for strongly convex functions, the convergence rate is geometric (linear convergence).
- **Non-convex landscape**: neural network loss has many local minima, saddle points, and plateaus. SGD noise helps escape saddle points.
- **Gradient clipping**: if ||gradient|| > threshold, rescale gradient so its norm = threshold. Prevents gradient explosion in RNNs and transformers during early training.
- **Weight initialization**: critical for training dynamics; bad initialization puts you in pathological regions of the loss landscape before training begins.
- **Adaptive learning rates**: scale the effective learning rate per parameter based on gradient history; parameters with noisy gradients get smaller effective steps.

---

## 4. Types / Architectures / Strategies

### 4.1 Gradient Descent Variants

| Variant | Batch Size | Gradient Estimate | Pros | Cons |
|---------|-----------|------------------|------|------|
| Batch GD | All N samples | Exact | Stable convergence | Expensive per step; no noise to escape saddle points |
| SGD | 1 sample | Very noisy | Cheapest per step; noise helps generalization | High variance; hard to tune lr |
| Mini-batch SGD | 32-256 | Approximately unbiased | Balance of speed and stability | Still requires lr tuning |

### 4.2 Optimizer Families

**Momentum-based**: Add a velocity term to dampen oscillations in high-curvature directions and accelerate in low-curvature directions. Nesterov momentum (NAG) uses lookahead gradient.

**Adaptive learning rate**: RMSProp divides lr by running average of squared gradients. Adam combines momentum + RMSProp. AdaGrad sums all past squared gradients (lr monotonically decreases — good for NLP word embeddings, bad for deep nets).

**Second-order**: Newton's method uses Hessian. L-BFGS approximates inverse Hessian with limited memory. Good for small models and smooth convex objectives; infeasible for large neural networks.

### 4.3 Learning Rate Schedules

| Schedule | Formula | When to Use |
|----------|---------|-------------|
| Constant | lr = lr_0 | Baseline; rarely best |
| Step decay | lr = lr_0 * gamma^(epoch/step_size) | Image classifiers; gamma=0.1 every 30 epochs |
| Exponential decay | lr = lr_0 * exp(-k * step) | Smooth decrease over training |
| Cosine annealing | lr = lr_min + 0.5*(lr_max-lr_min)*(1+cos(pi*t/T)) | Widely used for transformers |
| Linear warmup | lr increases linearly for first W steps | Always use with Adam for transformers |
| Cyclical LR | Cycles between lr_min and lr_max | Helps escape local minima; SGDR |

---

## 5. Architecture Diagrams

### Optimizer Update Comparison

```
Vanilla SGD:
  g = gradient(L, theta)
  theta = theta - lr * g

SGD + Momentum (beta=0.9):
  v = beta * v - lr * g
  theta = theta + v
  (v accumulates gradient history; smooths updates)

RMSProp (beta2=0.999):
  s = beta2 * s + (1 - beta2) * g^2
  theta = theta - lr * g / (sqrt(s) + eps)
  (s = running mean of squared gradients; scales lr per parameter)

Adam (beta1=0.9, beta2=0.999, eps=1e-8):
  m = beta1 * m + (1 - beta1) * g      (first moment = momentum)
  v = beta2 * v + (1 - beta2) * g^2    (second moment = RMSProp)
  m_hat = m / (1 - beta1^t)             (bias correction)
  v_hat = v / (1 - beta2^t)             (bias correction)
  theta = theta - lr * m_hat / (sqrt(v_hat) + eps)
```

### Learning Rate Schedule Shapes

```
Loss
 |
 |  Step decay:               Cosine annealing:       Warmup + cosine:
 |  lr_0 ___                  lr_0  .                  lr_0        .
 |       |   |__              lr_m   .  .            lr_min  .  .    .
 |       |      |__                   .  .  . . .           |  .        .
 |  0    step   step  epoch   0     T/2      T       0   W      T
```

### Convex vs Non-Convex Loss Landscape

```
Convex (logistic regression):         Non-convex (neural network):

Loss                                   Loss
  |                                      |    _      _
  |      .                               |   / \    / \
  |    .   .                             |  /   .  /   .
  |  .       .                           | /     ..     \___
  |.           .                         |/
  |___________                           |____________________
              theta                                          theta

  One global minimum.                   Many local minima, saddle points,
  GD always converges.                  and plateaus. SGD noise helps.
```

---

## 6. How It Works — Detailed Mechanics

### SGD, Momentum, and Adam from Scratch

```python
import numpy as np
from typing import Optional


class SGD:
    """Vanilla stochastic gradient descent."""

    def __init__(self, lr: float = 0.01):
        self.lr = lr

    def step(self, params: np.ndarray, grads: np.ndarray) -> np.ndarray:
        return params - self.lr * grads


class SGDMomentum:
    """
    SGD with momentum.
    v = beta * v - lr * g
    theta = theta + v

    beta=0.9 means velocity decays by 10% per step.
    Effective learning rate in consistent gradient direction: lr / (1 - beta) = 10 * lr.
    """

    def __init__(self, lr: float = 0.01, beta: float = 0.9):
        self.lr = lr
        self.beta = beta
        self.velocity: Optional[np.ndarray] = None

    def step(self, params: np.ndarray, grads: np.ndarray) -> np.ndarray:
        if self.velocity is None:
            self.velocity = np.zeros_like(params)
        self.velocity = self.beta * self.velocity - self.lr * grads
        return params + self.velocity


class Adam:
    """
    Adam optimizer (Kingma & Ba, 2015).
    Combines momentum (first moment) with RMSProp (second moment).
    Includes bias correction for early steps where m and v are close to 0.

    Default hyperparameters from the paper:
      lr=0.001, beta1=0.9, beta2=0.999, eps=1e-8
    For transformers, lr is often 1e-4 to 3e-4 with warmup.
    """

    def __init__(
        self,
        lr: float = 1e-3,
        beta1: float = 0.9,
        beta2: float = 0.999,
        eps: float = 1e-8
    ):
        self.lr = lr
        self.beta1 = beta1
        self.beta2 = beta2
        self.eps = eps
        self.m: Optional[np.ndarray] = None   # first moment (mean of gradients)
        self.v: Optional[np.ndarray] = None   # second moment (mean of squared gradients)
        self.t: int = 0                         # step counter for bias correction

    def step(self, params: np.ndarray, grads: np.ndarray) -> np.ndarray:
        if self.m is None:
            self.m = np.zeros_like(params)
            self.v = np.zeros_like(params)

        self.t += 1

        # Update biased first and second moment estimates
        self.m = self.beta1 * self.m + (1 - self.beta1) * grads
        self.v = self.beta2 * self.v + (1 - self.beta2) * (grads ** 2)

        # Bias-corrected estimates (important in early steps when t is small)
        m_hat = self.m / (1 - self.beta1 ** self.t)
        v_hat = self.v / (1 - self.beta2 ** self.t)

        # Parameter update: adaptive per-parameter learning rate
        update = self.lr * m_hat / (np.sqrt(v_hat) + self.eps)
        return params - update


class AdamW:
    """
    AdamW: Adam with decoupled weight decay (Loshchilov & Hutter, 2019).
    Standard Adam applies L2 regularization by adding lambda*theta to the gradient,
    which is then scaled by the adaptive learning rate — incorrect behavior.
    AdamW decouples weight decay from the gradient adaptation:
      theta = theta - lr * (adam_update + weight_decay * theta)
    This is the standard optimizer for transformers (GPT, BERT, LLaMA).
    """

    def __init__(
        self,
        lr: float = 1e-3,
        beta1: float = 0.9,
        beta2: float = 0.999,
        eps: float = 1e-8,
        weight_decay: float = 0.01
    ):
        self.lr = lr
        self.beta1 = beta1
        self.beta2 = beta2
        self.eps = eps
        self.weight_decay = weight_decay
        self.m: Optional[np.ndarray] = None
        self.v: Optional[np.ndarray] = None
        self.t: int = 0

    def step(self, params: np.ndarray, grads: np.ndarray) -> np.ndarray:
        if self.m is None:
            self.m = np.zeros_like(params)
            self.v = np.zeros_like(params)

        self.t += 1
        self.m = self.beta1 * self.m + (1 - self.beta1) * grads
        self.v = self.beta2 * self.v + (1 - self.beta2) * (grads ** 2)
        m_hat = self.m / (1 - self.beta1 ** self.t)
        v_hat = self.v / (1 - self.beta2 ** self.t)

        # Decoupled weight decay: applied directly to params, not through gradient
        params = params * (1 - self.lr * self.weight_decay)
        return params - self.lr * m_hat / (np.sqrt(v_hat) + self.eps)
```

### Learning Rate Schedules

```python
def cosine_annealing_lr(
    step: int,
    total_steps: int,
    lr_min: float = 1e-6,
    lr_max: float = 3e-4
) -> float:
    """
    Cosine annealing learning rate schedule.
    Decays from lr_max to lr_min following a cosine curve.
    Widely used for transformer training (GPT-3, LLaMA).
    """
    return lr_min + 0.5 * (lr_max - lr_min) * (
        1 + np.cos(np.pi * step / total_steps)
    )


def linear_warmup_cosine_decay(
    step: int,
    warmup_steps: int,
    total_steps: int,
    lr_max: float = 3e-4,
    lr_min: float = 3e-5
) -> float:
    """
    Linear warmup followed by cosine annealing decay.
    Standard schedule for transformer training:
    - Warmup: 5-10% of total steps (e.g., 2000 of 40000 steps for GPT-style)
    - Prevents Adam divergence at step 0 when second moment v is near 0 and
      bias correction gives unstable effective learning rates.
    """
    if step < warmup_steps:
        # Linear warmup from 0 to lr_max
        return lr_max * step / warmup_steps
    else:
        # Cosine decay from lr_max to lr_min
        decay_steps = total_steps - warmup_steps
        current_step = step - warmup_steps
        return lr_min + 0.5 * (lr_max - lr_min) * (
            1 + np.cos(np.pi * current_step / decay_steps)
        )


def lr_range_test(
    loss_fn,
    params: np.ndarray,
    data: np.ndarray,
    labels: np.ndarray,
    lr_start: float = 1e-7,
    lr_end: float = 1.0,
    n_steps: int = 100
) -> tuple[list[float], list[float]]:
    """
    Smith's LR range test: increase LR exponentially and record loss.
    Optimal LR is slightly before the point where loss starts increasing.
    Returns (lr_values, loss_values) for plotting.
    """
    lr_multiplier = (lr_end / lr_start) ** (1 / n_steps)
    lr = lr_start
    lrs, losses = [], []

    params_copy = params.copy()
    for step in range(n_steps):
        # Single gradient step
        loss = loss_fn(params_copy, data, labels)
        grads = np.gradient(np.array([loss_fn(params_copy + 1e-5 * np.ones_like(params_copy),
                                               data, labels)]))[0]
        params_copy -= lr * grads

        lrs.append(lr)
        losses.append(float(loss))
        lr *= lr_multiplier

    return lrs, losses
```

---

## 7. Real-World Examples

**Transformer training with warmup**: GPT-3 was trained with AdamW (beta1=0.9, beta2=0.95, eps=1e-8, weight_decay=0.1) and a cosine learning rate schedule with 375 million warmup tokens out of 300 billion total. Without warmup, Adam diverges at initialization because the second moment v starts at 0, making bias-corrected v_hat very small, which makes the adaptive lr extremely large.

**Gradient clipping in RNNs**: LSTM training for language modeling clips gradient norm to 1.0. Without clipping, a single bad batch can cause gradient magnitudes of 10^6, making weights jump to NaN. The clip is applied to the global gradient norm (all parameters combined), not per-parameter, so relative directions are preserved: `g = g * (max_norm / max(norm, max_norm))`.

**Adam vs SGD generalization gap**: ResNet-50 trained on ImageNet with SGD + momentum achieves ~76% top-1 accuracy; the same architecture with Adam typically achieves ~74-75%. This 1-2% gap is well-documented: SGD with momentum finds flatter minima (wider basins) that generalize better, while Adam's adaptive learning rates find sharper, narrower minima. For NLP tasks the gap reverses — Adam converges faster and often to better solutions than SGD.

**Saddle points in high dimensions**: A network with 10^8 parameters has a loss landscape with critical points in 10^8-dimensional space. A saddle point in high dimensions has positive curvature in many directions and negative curvature in at least one. Gradient noise from mini-batches helps escape saddle points; in practice, true local minima (positive curvature in all directions) are extremely rare in overparameterized networks.

---

## 8. Tradeoffs

| Optimizer | Convergence Speed | Memory Overhead | Generalization | Best For |
|-----------|-----------------|----------------|---------------|---------|
| SGD | Slow | Zero (no state) | Best (for vision) | CNNs with batch norm |
| SGD + Momentum | Moderate | 1x params | Good | Image classification |
| Adam | Fast | 2x params (m, v) | Slightly worse (vision) | NLP, transformers |
| AdamW | Fast | 2x params | Better than Adam | All transformers |
| L-BFGS | Very fast | O(n*m) for m vectors | Good | Small models, full batch |
| Adagrad | Fast initially | 1x params | Poor (lr decays to 0) | Sparse features (word2vec) |

| Schedule | Pros | Cons |
|----------|------|------|
| Constant | Simple | Suboptimal final performance |
| Cosine annealing | Smooth decay; widely validated | Hard to extend training |
| Warmup + cosine | Prevents early divergence | Two hyperparameters to tune |
| Cyclical LR | Escapes local minima | Harder to tune; noisy loss curves |

---

## 9. When to Use / When NOT to Use

**Use AdamW when**: training transformers, LLMs, BERT-style encoders, or any deep model on NLP tasks. Default hyperparameters (lr=1e-4 to 3e-4, beta1=0.9, beta2=0.999, weight_decay=0.01) work well as a starting point.

**Use SGD + momentum when**: training CNNs for image classification where generalization is paramount; research has repeatedly shown SGD finds flatter minima with better test accuracy for vision tasks.

**Use L-BFGS when**: the dataset fits in memory (< 100,000 samples), the model has fewer than ~10,000 parameters, and the objective is smooth and convex (logistic regression, SVMs, certain GPs). PyTorch supports `torch.optim.LBFGS`.

**Do NOT use Adagrad for deep network training**: the accumulated squared gradients in the denominator grow monotonically, causing the effective learning rate to shrink to near-zero during training. This is acceptable for sparse problems (word embeddings, where infrequent words get larger effective updates) but fatal for dense networks.

**Always use warmup with Adam** when training transformers from scratch. The first few hundred steps with small beta2 lead to highly variable effective learning rates; warmup prevents the early chaos from setting weights in bad regions.

**Do NOT use a constant learning rate for long training runs**: learning rate decay consistently improves final performance because large lr is good for exploration early, while small lr is good for fine-grained convergence late.

---

## 10. Common Pitfalls

**Pitfall 1 — Adam weight decay bug (L2 vs AdamW)**: A team added L2 regularization to their transformer by adding `lambda * theta` to the gradient before passing to Adam. This is incorrect: the adaptive scaling divides the L2 term by sqrt(v_hat), which reduces the effective regularization strength non-uniformly across parameters. AdamW was invented specifically to fix this. The team observed their model overfitting despite L2 regularization being "on"; switching to AdamW and decoupled weight decay resolved it.

```python
# Broken: L2 added to gradient, then adaptively scaled (wrong)
grad = compute_gradient(loss) + weight_decay * params
params = adam.step(params, grad)  # weight decay is scaled by 1/sqrt(v)

# Fixed: AdamW decouples weight decay
adam_update = adam.step(params, compute_gradient(loss))
params = params * (1 - lr * weight_decay) - adam_update  # weight decay unscaled
```

**Pitfall 2 — Learning rate too high without warmup causes NaN**: A team trained a GPT-style model with lr=3e-4 and no warmup. At step 1, the second moment v is 0; after bias correction, v_hat = g^2 / (1 - beta2^1) = g^2 / 0.001, which is very small for small g. The effective step size = lr * m_hat / sqrt(v_hat) is enormous. Large initial updates sent weights to NaN within 10 steps. Fix: always warm up lr linearly for 1-2% of total training steps.

**Pitfall 3 — Gradient accumulation with wrong loss scaling**: When using gradient accumulation (accumulating gradients over K micro-batches before updating), the loss must be divided by K before calling backward(), otherwise the gradient is K times too large. A production training run used accumulation steps=8 without dividing the loss, resulting in an effective learning rate 8x higher than intended. The model appeared to converge quickly but had poor generalization — the sharp minimum was due to the oversized steps.

```python
# Broken: gradient accumulates K times without division
for micro_batch in micro_batches:
    loss = compute_loss(micro_batch)
    loss.backward()  # gradients accumulate
optimizer.step()   # effective grad = K * true_grad

# Fixed: divide loss by accumulation steps
accumulation_steps = 8
for i, micro_batch in enumerate(micro_batches):
    loss = compute_loss(micro_batch) / accumulation_steps
    loss.backward()
optimizer.step()
```

**Pitfall 4 — Learning rate schedule not accounting for gradient accumulation**: When using gradient accumulation with K=8, you take 1 optimizer step per 8 forward passes. If the lr schedule is defined in terms of optimizer steps, the warmup is too short in wall-clock terms. If defined in gradient steps (forward passes), the warmup is too long. Standardize all schedules on optimizer steps and document clearly.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| PyTorch torch.optim | SGD, Adam, AdamW, L-BFGS; get_last_lr(), param_groups |
| PyTorch lr_scheduler | CosineAnnealingLR, OneCycleLR, LinearLR, SequentialLR |
| Optax (JAX) | Composable optimizer library; chain transforms; gradient clipping |
| transformers (HuggingFace) | get_linear_schedule_with_warmup, get_cosine_schedule_with_warmup |
| Apex (NVIDIA) | FusedAdam: faster Adam for GPU; mixed precision training |
| Sophia | Second-order optimizer for LLMs; uses diagonal Hessian estimate |
| Adan | Novel optimizer from 2023; claims faster convergence than Adam |

---

## 12. Interview Questions with Answers

**Q: What is the difference between batch gradient descent, SGD, and mini-batch gradient descent?**
Batch GD computes the exact gradient using all N training samples before each update — very accurate gradient but expensive per step, and cannot use the mini-batch pipeline parallelism of modern GPUs. SGD uses a single sample per update — cheapest per step but very noisy gradients with high variance. Mini-batch SGD uses 32-256 samples per update — the practical standard because it is unbiased like batch GD, parallelizable across GPU cores, and the gradient noise is low enough for stable training. Almost all modern deep learning uses mini-batch with batch size tuned for GPU memory.

**Q: What problem does momentum solve in gradient descent?**
Momentum addresses two issues: oscillation and slow convergence in ravine-shaped loss surfaces. Without momentum, gradient descent oscillates across a narrow valley because the gradient perpendicular to the valley bottom is large, leading to zig-zag steps. Momentum accumulates velocity — updates in consistent directions grow while oscillating directions cancel out. It also accelerates convergence when the gradient consistently points in the same direction. The effective learning rate in a consistent direction is lr / (1 - beta) = 10x for beta=0.9.

**Q: Explain Adam's two moment estimates and why bias correction is needed.**
Adam maintains m (first moment — exponential moving average of gradients, like momentum) and v (second moment — exponential moving average of squared gradients, like RMSProp). At step t, both are initialized to 0, so early values are biased toward 0. Bias correction computes m_hat = m / (1 - beta1^t) and v_hat = v / (1 - beta2^t). At t=1 with beta1=0.9: m_hat = m / 0.1, which counteracts the 90% discounting of the first gradient. Without bias correction, the effective learning rate at early steps would be nearly 0 for v (since 1 - beta2^1 = 0.001 makes v_hat very small), then explode as t grows.

**Q: Why does Adam sometimes generalize worse than SGD for vision tasks?**
Adam's adaptive learning rates find sharp minima (narrow valleys in the loss landscape) because it can take large steps along low-curvature directions and small steps along high-curvature directions, converging to tighter local minima. SGD with momentum, lacking this adaptivity, tends to find flatter minima with wider basins that generalize better — the flat minima hypothesis (Hochreiter & Schmidhuber, 1997) suggests parameters in flat regions are more robust to small perturbations. For NLP tasks where the loss landscape geometry differs, this trade-off reverses and Adam is superior.

**Q: What is AdamW and why is it preferred over Adam with weight decay?**
Standard Adam with L2 regularization adds lambda * theta to the gradient before the adaptive update. This means weight decay is divided by sqrt(v_hat), making its effective strength different for each parameter and time step — parameters with small squared gradients get less effective regularization. AdamW (decoupled weight decay) applies weight decay directly to the parameters: theta = theta * (1 - lr * lambda) - adam_update. This makes the effective regularization strength consistent and independent of the adaptive scaling. AdamW is the standard for all transformer training (GPT, BERT, LLaMA use AdamW).

**Q: What is gradient clipping and when should you use it?**
Gradient clipping rescales the gradient when its norm exceeds a threshold: g = g * (clip_norm / max(||g||, clip_norm)). This prevents gradient explosions — sudden large gradient magnitudes that send weights to NaN or far out of a good region. Always use gradient clipping for RNNs (vanishing/exploding gradients are the norm) and during early transformer training. Typical clip value is 1.0. Clip based on global gradient norm (all parameters together), not per-parameter, to preserve the relative gradient direction. Use per-layer gradient norms as a diagnostic metric during training.

**Q: What is a saddle point and why is it not usually a problem in deep learning?**
A saddle point is a point where the gradient is zero but which is not a local minimum — curvature is positive in some directions and negative in others (like the center of a saddle). In low dimensions, saddle points can trap first-order methods indefinitely. In high-dimensional parameter spaces (millions to billions of parameters), the probability that all eigenvalues of the Hessian are positive at a critical point decreases exponentially with dimension. Near saddle points, gradient noise from mini-batches provides perturbations that push parameters along the negatively-curved escape directions.

**Q: What is the difference between convex and non-convex optimization, and how does it affect training?**
A function is convex if the line segment between any two points on the function lies above the function: f(lambda*x + (1-lambda)*y) <= lambda*f(x) + (1-lambda)*f(y). For convex functions, every local minimum is a global minimum, and gradient descent is guaranteed to converge. Logistic regression loss is convex; neural network loss is not. Non-convex optimization offers no global convergence guarantee, but in practice overparameterized networks seem to have many equivalent global minima, and SGD noise helps avoid poor local minima.

**Q: How does the learning rate schedule affect training, and what is warmup?**
The learning rate controls how large each gradient step is. Early in training, large steps help explore the loss landscape quickly; late in training, small steps allow fine-grained convergence to a good minimum. Learning rate decay (step, exponential, cosine) implements this intuition. Warmup increases lr from near 0 to the target lr over the first 1-5% of training steps. This is critical for Adam because at initialization v is near 0, making the effective step size very large; warmup gives v time to accumulate before taking large steps, preventing early divergence.

**Q: Why is second-order optimization not used for large neural networks?**
Newton's method computes the parameter update as theta = theta - H^{-1} * g where H is the n x n Hessian. For a model with n = 10^9 parameters, the Hessian has 10^18 entries — storing it requires petabytes of memory. Even computing the Hessian-vector product (Hv) without explicitly forming H takes O(n) time but requires a second backward pass. L-BFGS approximates the inverse Hessian with m previous gradient differences (m=10-20), reducing memory to O(m*n) but still O(n^2) per step. Adam approximates the diagonal of the Hessian via the second moment v — a rough but practical substitute that captures per-parameter curvature.

**Q: What is the effect of batch size on optimization and generalization?**
Larger batch sizes produce lower-variance gradient estimates, allowing larger learning rates and faster wall-clock convergence. However, large batches tend to find sharper minima with worse generalization (the "generalization gap" for large batches, Keskar et al. 2017). Empirically, linear scaling rule: when batch size is multiplied by k, multiply lr by k (with warmup). Small batches (16-32) regularize implicitly through gradient noise and often find flatter minima, but are slower due to GPU underutilization. GPT-3 used batch size 3.2M tokens — large batch enabled by learning rate scaling.

---

## 13. Best Practices

- Default to AdamW with lr=3e-4, beta1=0.9, beta2=0.999, eps=1e-8, weight_decay=0.01 for transformers; tune lr first.
- Always use linear warmup for the first 1-5% of training steps when using Adam/AdamW.
- Use cosine annealing learning rate decay; it consistently outperforms step decay and is easy to implement.
- Clip gradient norm to 1.0 as a safeguard; log the gradient norm metric to detect instability before it becomes NaN.
- Run the LR range test (increase lr exponentially, find the point where loss starts increasing) to select the maximum lr before long training runs.
- For vision tasks, try SGD + momentum before Adam; the generalization benefit can outweigh the slower convergence.
- Monitor the ratio of update norm to parameter norm (should be ~0.001); if too high, lr is too large; if too low, lr is too small.
- When resuming training from a checkpoint, reload optimizer state (m and v moments) to avoid warmup restart artifacts.
- Use gradient accumulation to simulate larger batch sizes when GPU memory is limited; divide loss by accumulation steps before backward.
- Log per-layer gradient norms to identify dead layers (near-zero gradients) or exploding layers (large gradients) during training.

---

## 14. Case Study

**Problem**: A team trains a 350M-parameter transformer language model on 50B tokens. Early training runs diverged after 1000 steps — loss spiked to NaN. A second attempt converged but generalized poorly. The team needs a stable training recipe.

**Diagnosis and solution**:

```python
import numpy as np


def training_recipe_config() -> dict:
    """
    Stable training configuration for a 350M parameter transformer.
    Based on GPT-3 and LLaMA training recipes.
    """
    total_tokens = 50_000_000_000          # 50B tokens
    tokens_per_batch = 2048 * 512          # seq_len=2048, batch_size=512 = 1M tokens/step
    total_steps = total_tokens // tokens_per_batch  # ~48,800 steps

    warmup_fraction = 0.01                  # 1% = 488 warmup steps
    warmup_steps = int(total_steps * warmup_fraction)

    return {
        # Optimizer: AdamW with decoupled weight decay
        "optimizer": "AdamW",
        "lr": 3e-4,
        "beta1": 0.9,
        "beta2": 0.95,          # lower than default 0.999 for LM training
        "eps": 1e-8,
        "weight_decay": 0.1,    # stronger regularization for large model

        # Schedule: linear warmup + cosine decay to 10% of peak lr
        "warmup_steps": warmup_steps,         # 488
        "total_steps": total_steps,            # ~48,800
        "lr_min": 3e-5,                        # 10% of peak lr at end

        # Gradient clipping: prevents NaN from early spikes
        "gradient_clip_norm": 1.0,

        # Batch: gradient accumulation to simulate 512 batch size on 8 GPUs
        "micro_batch_size": 64,
        "gradient_accumulation_steps": 8,
        "effective_batch_size": 512,

        # Initialization: critical for stable training
        # Output projection weights scaled by 1/sqrt(n_layers)
        # Prevents residual stream from growing with depth
        "output_proj_init_scale": 1.0 / np.sqrt(24),  # for 24-layer model
    }


def compute_lr(step: int, config: dict) -> float:
    """Compute learning rate at a given step."""
    warmup_steps = config["warmup_steps"]
    total_steps = config["total_steps"]
    lr_max = config["lr"]
    lr_min = config["lr_min"]

    if step < warmup_steps:
        return lr_max * step / warmup_steps
    else:
        progress = (step - warmup_steps) / (total_steps - warmup_steps)
        return lr_min + 0.5 * (lr_max - lr_min) * (1 + np.cos(np.pi * progress))
```

**Root cause of divergence**: No warmup + Adam default beta2=0.999. At step 1, v_hat = g^2 / (1-0.999) = 1000 * g^2, making sqrt(v_hat) large and the effective lr tiny — until gradients accumulated and v_hat stabilized, at which point the effective lr jumped. This instability combined with the 350M parameter scale produced divergence at step ~1000 when a large gradient batch was encountered.

**Root cause of poor generalization**: Adam with L2 regularization (not AdamW) was used. Weight decay was unevenly applied — parameters with large gradient history received less effective regularization. Switching to AdamW and increasing weight_decay from 0.01 to 0.1 improved held-out perplexity by 2.1 points.

**Lesson**: For any new training run at scale, use: AdamW + warmup (1% of steps) + cosine decay + gradient clipping (1.0) + gradient norm logging. These four changes together prevent the most common training failure modes.
