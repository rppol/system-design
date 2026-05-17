# Neural Network Fundamentals

## 1. Concept Overview

A neural network is a parameterized function approximator composed of stacked layers of interconnected units (neurons). Each neuron computes a weighted sum of its inputs followed by a nonlinear activation function. Networks with one hidden layer are universal approximators (Universal Approximation Theorem): they can represent any continuous function on a compact domain given enough hidden units. In practice, depth is more parameter-efficient than width, so modern networks stack many layers rather than making a single layer enormous.

Training consists of two passes: a forward pass that computes predictions, and a backward pass (backpropagation) that computes gradients of the loss with respect to every parameter via the chain rule. An optimizer (e.g., Adam) then updates parameters in the direction that decreases the loss.

---

## 2. Intuition

One-line analogy: a neural network is a factory assembly line where each station (layer) progressively refines raw material (input) into a finished product (prediction), and quality inspectors (backprop) send correction memos back through every station simultaneously.

Mental model: layers learn hierarchical representations. Early layers detect edges and textures; later layers detect objects and concepts. This compositionality is why deep networks outperform shallow ones on complex tasks.

Why it matters: neural networks power image classification, speech recognition, machine translation, and large language models. Understanding the fundamentals — activations, initialization, normalization, regularization — is prerequisite knowledge for all of deep learning.

Key insight: the chain rule makes it possible to attribute the network's error to every single parameter, no matter how many layers deep, in a single backward pass that costs roughly the same compute as the forward pass.

---

## 3. Core Principles

**Perceptron**: the simplest neuron. Computes `output = activation(w^T x + b)`. A single perceptron is a linear classifier; it cannot solve XOR. Stacking nonlinear perceptrons forms an MLP.

**Multilayer Perceptron (MLP)**: alternating linear (fully connected) layers and activation functions. Depth provides representational power; width provides capacity per layer.

**Backpropagation**: efficient algorithm for computing gradients by applying the chain rule on the computation graph in reverse. Requires storing intermediate activations from the forward pass.

**Chain rule**: if `L = f(g(x))`, then `dL/dx = (df/dg)(dg/dx)`. In neural networks this telescopes across all layers.

**Loss function**: scalar summary of model error. Cross-entropy for classification; MSE for regression. The optimizer minimizes this.

**Universal Approximation Theorem**: an MLP with a single hidden layer of sufficient width and a nonlinear activation can approximate any continuous function on a compact set to arbitrary precision. Depth replaces exponential width.

---

## 4. Types / Architectures / Strategies

| Component | Variants | Key Property |
|-----------|----------|-------------|
| Activation | ReLU, Leaky ReLU, GELU, Sigmoid, Tanh | Nonlinearity enabling learning |
| Layer | Linear (FC), Conv, RNN, Attention | Inductive bias for task type |
| Normalization | BatchNorm, LayerNorm, GroupNorm | Stabilizes gradient flow |
| Regularization | Dropout, Weight Decay, Label Smoothing | Prevents overfitting |
| Initialization | Xavier/Glorot, He/Kaiming | Prevents vanishing/exploding gradients |

**Activation Functions:**

- `ReLU(x) = max(0, x)`: gradient is 1 for x > 0, 0 for x <= 0. Fast and sparse. Suffers from dead neurons when units consistently output 0 (negative pre-activations, gradient becomes permanently 0).
- `Leaky ReLU(x) = max(0.01x, x)`: small slope for negative inputs, prevents dead neurons. Slope is a hyperparameter (typical: 0.01).
- `GELU(x) = x * Phi(x)` where Phi is the Gaussian CDF. Used in BERT, GPT, ViT. Smoother than ReLU; empirically outperforms ReLU on NLP tasks.
- `Sigmoid(x) = 1 / (1 + e^(-x))`: squashes to (0,1). Gradient max is 0.25 at x=0, decays toward 0 for large |x|. Causes vanishing gradients in deep networks. Use only in output layer for binary classification.
- `Tanh(x) = (e^x - e^(-x)) / (e^x + e^(-x))`: squashes to (-1,1). Zero-centered unlike sigmoid. Still suffers vanishing gradients in deep networks; max gradient is 1.0 at x=0.

---

## 5. Architecture Diagrams

```
Input Layer       Hidden Layer 1    Hidden Layer 2    Output Layer
    x1 ----w11----> h11 --w21----> h21 --w31----> y1
    x2 --           |                |
       \--w12----> h12 --w22----> h22 --w32----> y2
    x3 --           |                |
       \--w13----> h13 --w23----> h23

Forward pass: left to right (compute predictions)
Backward pass: right to left (compute gradients via chain rule)

Neuron Detail:
  inputs x1..xn
      |
      v
  [Weighted Sum]  z = w1*x1 + w2*x2 + ... + wn*xn + b
      |
      v
  [Activation]    a = ReLU(z)  (or GELU, Tanh, etc.)
      |
      v
  output a (fed to next layer)

Batch Normalization position in a layer:
  Linear -> BatchNorm -> Activation  (original paper)
  Linear -> Activation -> BatchNorm  (sometimes used, debated)
```

---

## 6. How It Works — Detailed Mechanics

### Basic MLP in PyTorch

```python
import torch
import torch.nn as nn
import torch.optim as optim
from torch import Tensor


class MLP(nn.Module):
    def __init__(
        self,
        input_dim: int,
        hidden_dims: list[int],
        output_dim: int,
        dropout_p: float = 0.5,
    ) -> None:
        super().__init__()
        layers: list[nn.Module] = []
        in_dim = input_dim
        for h_dim in hidden_dims:
            layers.extend([
                nn.Linear(in_dim, h_dim),
                nn.BatchNorm1d(h_dim),   # normalize across batch dimension
                nn.GELU(),
                nn.Dropout(p=dropout_p),  # disabled at eval time automatically
            ])
            in_dim = h_dim
        layers.append(nn.Linear(in_dim, output_dim))
        self.net = nn.Sequential(*layers)
        self._init_weights()

    def _init_weights(self) -> None:
        for module in self.modules():
            if isinstance(module, nn.Linear):
                # He/Kaiming for ReLU/GELU; Xavier/Glorot for Sigmoid/Tanh
                nn.init.kaiming_normal_(module.weight, nonlinearity="relu")
                nn.init.zeros_(module.bias)

    def forward(self, x: Tensor) -> Tensor:
        return self.net(x)
```

### Weight Initialization — Why Zero Init Fails

```python
# BROKEN: all neurons in a layer receive identical gradients -> symmetry never breaks
def broken_init(model: nn.Module) -> None:
    for p in model.parameters():
        nn.init.zeros_(p)  # all weights zero: all neurons compute same output

# FIX: random initialization breaks symmetry
def correct_init(model: nn.Module) -> None:
    for module in model.modules():
        if isinstance(module, nn.Linear):
            nn.init.kaiming_normal_(module.weight, nonlinearity="relu")
            nn.init.zeros_(module.bias)  # bias can start at zero
```

**Xavier/Glorot**: `std = sqrt(2 / (fan_in + fan_out))`. Best for Sigmoid and Tanh because it keeps gradient variance stable through layers with near-linear regime activations.

**He/Kaiming**: `std = sqrt(2 / fan_in)`. Best for ReLU/GELU because ReLU kills half the neurons on average, so variance needs to be doubled.

### Batch Normalization

```python
class BatchNorm1dManual(nn.Module):
    """Manual implementation to illustrate mechanics."""

    def __init__(self, num_features: int, momentum: float = 0.1, eps: float = 1e-5) -> None:
        super().__init__()
        self.gamma = nn.Parameter(torch.ones(num_features))   # learnable scale
        self.beta = nn.Parameter(torch.zeros(num_features))   # learnable shift
        self.momentum = momentum
        self.eps = eps
        # running stats for inference (not gradients, not parameters)
        self.register_buffer("running_mean", torch.zeros(num_features))
        self.register_buffer("running_var", torch.ones(num_features))

    def forward(self, x: Tensor) -> Tensor:
        if self.training:
            mean = x.mean(dim=0)          # mean across batch dimension
            var = x.var(dim=0, unbiased=False)
            # update running stats with momentum=0.1 (exponential moving average)
            self.running_mean = (1 - self.momentum) * self.running_mean + self.momentum * mean
            self.running_var  = (1 - self.momentum) * self.running_var  + self.momentum * var
        else:
            mean = self.running_mean      # use accumulated stats at inference
            var = self.running_var
        x_norm = (x - mean) / torch.sqrt(var + self.eps)
        return self.gamma * x_norm + self.beta
```

Key: `momentum=0.1` means each batch contributes 10% to running statistics update. At inference, the stored running stats are used — **never** batch statistics — so the model behaves deterministically.

### Dropout — Inverted Dropout

During training, each neuron is zeroed with probability `p`. To preserve expected value, surviving neurons are scaled by `1/(1-p)`. This is "inverted dropout" and is what PyTorch implements. At inference (`model.eval()`), dropout is a no-op — no scaling needed.

```python
# PyTorch handles this automatically when you switch model.train() / model.eval()
dropout = nn.Dropout(p=0.5)
model.train()
out_train = dropout(x)   # 50% zeroed, survivors scaled by 2.0
model.eval()
out_eval = dropout(x)    # identity: no zeroing, no scaling
```

### Training Loop

```python
def train_epoch(
    model: nn.Module,
    loader: torch.utils.data.DataLoader,
    optimizer: optim.Optimizer,
    criterion: nn.Module,
    device: torch.device,
) -> float:
    model.train()  # sets BatchNorm to use batch stats; enables Dropout
    total_loss = 0.0
    for batch_x, batch_y in loader:
        batch_x, batch_y = batch_x.to(device), batch_y.to(device)

        # CORRECT order: zero -> forward -> backward -> step
        optimizer.zero_grad()          # clear accumulated gradients
        logits = model(batch_x)
        loss = criterion(logits, batch_y)
        loss.backward()                # compute gradients
        optimizer.step()               # update parameters

        total_loss += loss.item()
    return total_loss / len(loader)
```

Common bug: calling `optimizer.zero_grad()` AFTER `loss.backward()` discards computed gradients before `optimizer.step()` can use them, or forgetting it altogether causes gradient accumulation across batches.

---

## 7. Real-World Examples

**Image classification (MNIST)**: MLP with 784 -> 512 -> 256 -> 10 with BatchNorm + GELU + Dropout(0.3) achieves ~98% accuracy. Baseline linear model achieves ~92%.

**Tabular data (fraud detection)**: MLPs with 4-8 layers often match or beat tree ensembles when features are well-engineered. Dropout p=0.3 and weight decay 1e-4 are typical regularization settings.

**Feature extraction backbone**: the final hidden layer of a trained MLP produces dense embeddings used for similarity search (cosine distance, FAISS retrieval).

**BatchNorm in production**: at Meta, BatchNorm in models serving recommendations caused silent bugs when batch sizes dropped to 1 during low-traffic periods. Running stats diverged from true statistics. Fix: switch to LayerNorm or GroupNorm for variable batch-size scenarios.

---

## 8. Tradeoffs

| Activation | Gradient Flow | Dead Neuron Risk | Speed | Typical Use |
|------------|--------------|-----------------|-------|-------------|
| ReLU | Good (no saturation for x>0) | High | Fastest | CNNs, simple MLPs |
| Leaky ReLU | Better | Low | Fast | CNNs when dead neurons observed |
| GELU | Excellent | None | Medium | Transformers, BERT, GPT |
| Sigmoid | Poor (vanishing) | None | Medium | Binary output only |
| Tanh | Moderate | None | Medium | RNN gates, zero-centered need |

| Normalization | Batch Dependency | Sequence Support | Small Batch |
|--------------|-----------------|-----------------|-------------|
| BatchNorm | Yes (batch mean/var) | Awkward (needs 3D) | Poor |
| LayerNorm | No | Excellent (per-token) | Excellent |
| GroupNorm | No | Yes | Good |

| Initialization | Best For | Problem Avoided |
|---------------|----------|----------------|
| Xavier/Glorot | Sigmoid, Tanh | Vanishing gradients |
| He/Kaiming | ReLU, GELU | Variance collapse due to ReLU zeroing |
| Zero init | None (avoid) | Nothing — causes symmetry |

---

## 9. When to Use / When NOT to Use

**Use MLPs when:**
- Tabular / structured data with no spatial or sequential structure
- Feature embedding layers within larger architectures
- Final classification head after CNN/Transformer backbone
- Small datasets where simpler models generalize better

**Do NOT use plain MLPs when:**
- Input has spatial structure (use CNNs) or sequential dependencies (use RNN / Transformer)
- Data is very high-dimensional with local structure (images, audio, video)
- You need interpretability (tree ensembles or linear models are preferable)

**Use BatchNorm when:**
- Training CNNs with batch sizes >= 16
- Residual networks (stabilizes training significantly)

**Use LayerNorm instead when:**
- Transformers or RNNs (batch-independent normalization)
- Batch size is small (< 8) or variable

---

## 10. Common Pitfalls

**War story 1 — Forgetting model.eval() at inference:**
A production image classifier was running in training mode (`model.train()`). BatchNorm was computing statistics from each inference batch instead of using the stored running stats. With a batch size of 1 (real-time API), the batch mean/var were noisy and the model's accuracy dropped from 94% to 71%. The fix is always calling `model.eval()` before inference and `model.train()` before training.

```python
# BROKEN: model left in training mode
def predict(model, x):
    return model(x)  # BatchNorm uses batch stats; Dropout randomly zeros neurons

# FIX
def predict(model, x):
    model.eval()
    with torch.no_grad():
        return model(x)
```

**War story 2 — Zero weight initialization causing no learning:**
A team initialized all linear layer weights to zero "for reproducibility." The model's loss did not decrease over 50 epochs. Because all neurons in each layer produce the same output, all neurons receive identical gradients. All weights update identically. The network never breaks symmetry and remains effectively a single neuron. Fix: use `kaiming_normal_` or `xavier_uniform_`.

**War story 3 — Gradient accumulation without zero_grad:**
A training script forgot `optimizer.zero_grad()` inside the loop. Gradients accumulated across all batches. After 100 batches the gradient norm was 10,000x larger than expected. The optimizer step moved parameters catastrophically far. Loss spiked to NaN. Fix: always zero gradients at the top of every iteration.

**War story 4 — Dead ReLU neurons at scale:**
A deep network (32 layers, ReLU activations) was initialized with a high learning rate (0.1). After 100 steps, ~40% of neurons had negative biases and were permanently outputting zero. The effective capacity of the network halved. Fix: use a lower initial learning rate (0.001), use He initialization, or switch to Leaky ReLU / GELU.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| PyTorch (`torch.nn`) | Building and training neural networks |
| PyTorch Lightning | Training loop boilerplate, multi-GPU, logging |
| TensorBoard / W&B | Loss curves, gradient norms, activation histograms |
| `torchinfo` / `torchsummary` | Layer shapes, parameter counts |
| ONNX | Model export for cross-framework serving |
| `torch.compile` (PyTorch 2.0+) | JIT compilation for 1.5-2x speedup |

Key PyTorch APIs:
- `nn.Module`, `nn.Linear`, `nn.BatchNorm1d`, `nn.Dropout`, `nn.GELU`
- `optim.Adam(lr=0.001, betas=(0.9, 0.999), eps=1e-8, weight_decay=1e-4)`
- `nn.CrossEntropyLoss()`, `nn.MSELoss()`
- `torch.no_grad()` context manager for inference
- `model.train()` / `model.eval()` mode switching

---

## 12. Interview Questions with Answers

**Q: What is the vanishing gradient problem and which activations cause it?**
Sigmoid and Tanh both saturate — their gradients approach zero for large absolute input values (Sigmoid max gradient is 0.25, Tanh max is 1.0 at x=0). When multiplied across many layers during backpropagation via the chain rule, gradients in early layers decay exponentially toward zero. Early layers stop learning while later layers continue updating. ReLU avoids saturation for positive inputs (gradient is exactly 1), which is why it became the default. Practical fix: use ReLU/GELU, proper initialization (He for ReLU), and batch normalization.

**Q: What is the dead ReLU problem and how do you fix it?**
A ReLU neuron becomes dead when its pre-activation is always negative, causing its output to be zero. The gradient of ReLU is zero for negative inputs, so the neuron receives no gradient and its weights never update. Common causes: high learning rates that overshoot, poor initialization, or large negative biases. Fixes: Leaky ReLU (small gradient for negative side), GELU (smooth approximation), lower initial learning rate, or careful initialization.

**Q: Why does zero initialization fail for neural networks?**
Zero initialization causes the symmetry problem: all neurons in a layer compute identical weighted sums and identical gradients. They update identically and remain identical forever, so the entire layer effectively acts as a single neuron. Random initialization (Xavier, He) breaks this symmetry so neurons can specialize. Bias terms can be initialized to zero because they do not participate in the symmetry argument.

**Q: What does batch normalization do and why does it help?**
BatchNorm normalizes layer inputs across the batch dimension to have zero mean and unit variance, then applies learnable scale (gamma) and shift (beta). Benefits: reduces internal covariate shift (distribution of layer inputs changes as earlier layers update, destabilizing training), allows higher learning rates, acts as slight regularizer. The momentum parameter (0.1 by default) controls the exponential moving average of running statistics used at inference.

**Q: What is the difference between model.train() and model.eval()?**
`model.train()` activates training-mode behavior for layers like BatchNorm (use batch statistics, update running stats) and Dropout (randomly zero neurons). `model.eval()` switches BatchNorm to use stored running statistics (deterministic, batch-independent) and disables Dropout (identity). Forgetting to call `model.eval()` before inference is one of the most common production bugs in deep learning.

**Q: What is inverted dropout and why is it used?**
During training, each neuron is zeroed with probability p. To keep the expected magnitude of activations the same at training and inference, surviving neurons are scaled by 1/(1-p). This is inverted dropout. At inference, no scaling is needed because all neurons are active. Without the scaling factor, activations at inference would be (1-p) times larger than at training, causing a distribution shift. PyTorch's `nn.Dropout` implements inverted dropout.

**Q: What is the Universal Approximation Theorem and what are its limitations?**
The theorem states that an MLP with one hidden layer and a nonlinear activation can approximate any continuous function on a compact domain to arbitrary precision, given sufficient hidden units. Limitations: it is an existence result, not a constructive one (gives no bound on how many units are needed, which can be exponentially large). It says nothing about generalization, training efficiency, or whether gradient descent can find the approximating weights. In practice, deep narrow networks are far more parameter-efficient than shallow wide ones.

**Q: What is the chain rule in the context of backpropagation?**
Backpropagation applies the chain rule of calculus to compute the gradient of the loss with respect to each parameter. If loss L = f(g(h(x))), then dL/dx = (dL/df)(df/dg)(dg/dh)(dh/dx). In a neural network with many layers, this telescope of derivatives allows computing gradients for all parameters in a single backward pass by reusing intermediate values stored during the forward pass.

**Q: When would you use Xavier initialization vs He initialization?**
Xavier (Glorot) initialization sets std = sqrt(2 / (fan_in + fan_out)). It is designed for activations that operate near the linear regime for small inputs (Sigmoid, Tanh). He (Kaiming) initialization sets std = sqrt(2 / fan_in). It is designed for ReLU which kills approximately half the neurons, so variance needs to be doubled to compensate. GELU is sufficiently ReLU-like that He initialization works well for it too. Using Xavier with ReLU tends to cause vanishing gradients in very deep networks.

**Q: How does dropout act as regularization?**
Dropout prevents co-adaptation: neurons cannot rely on specific other neurons always being present, so they must learn more robust, independent features. It can be viewed as training an exponential ensemble of 2^N different networks (one per dropout mask) and averaging them at inference. Typical values: p=0.5 for fully connected layers, p=0.1-0.2 for convolutional layers (which already have parameter sharing for regularization).

**Q: What is label smoothing and when would you use it?**
Label smoothing replaces hard targets (0 or 1) with soft targets (epsilon/(K-1) for negatives, 1 - epsilon for the correct class, typically epsilon=0.1). It prevents the model from becoming overconfident, which would push logits to large magnitudes and hurt generalization. It is most useful when training data has label noise or when the task has inherent ambiguity. It was popularized by Inception-v3 and used in most modern image classifiers.

---

## 13. Best Practices

- Always call `model.eval()` with `torch.no_grad()` during inference. Make this a checklist item in code review.
- Use He/Kaiming initialization for ReLU/GELU networks; Xavier/Glorot for Sigmoid/Tanh networks; never use zero init for weights.
- Prefer GELU over ReLU in new architectures — consistently better empirical performance at similar compute cost.
- Use `optimizer.zero_grad()` as the first line of every training iteration, not the last line.
- Prefer LayerNorm over BatchNorm in Transformers and variable-batch-size serving scenarios.
- Monitor gradient norms (log `torch.nn.utils.clip_grad_norm_` return value) to detect exploding gradients before they cause NaN losses.
- Set `DataLoader(num_workers=4, pin_memory=True)` for GPU training — this overlaps data loading with GPU computation and eliminates the CPU-GPU copy bottleneck.
- Typical batch sizes: 32–256 for images, 16–64 for NLP. Larger batches need learning rate scaling (linear rule: multiply LR by batch_scale factor).
- Weight decay (L2 regularization) belongs in the optimizer, not as an explicit loss term: `optim.Adam(model.parameters(), weight_decay=1e-4)`.

---

## 14. Case Study

**Task**: Train an MLP for tabular fraud detection on a 500K row dataset with 50 features.

**Problem Statement**: Binary classification (fraud / not fraud) with severe class imbalance (0.5% fraud rate). Raw features include transaction amount, merchant category, time-of-day, and behavioral history features. The model must have < 5ms latency at inference for real-time scoring.

**Architecture Decision**:
- Input: 50 features after standardization (StandardScaler fitted on train set only)
- Architecture: Linear(50, 256) -> BN -> GELU -> Dropout(0.3) -> Linear(256, 128) -> BN -> GELU -> Dropout(0.3) -> Linear(128, 1)
- Loss: `BCEWithLogitsLoss(pos_weight=torch.tensor([200.0]))` to handle 0.5% fraud rate
- Optimizer: Adam with lr=0.001, weight_decay=1e-4
- Scheduler: CosineAnnealingLR

**Key Design Decisions**:

1. BatchNorm over LayerNorm: batch sizes are large (256) and consistent during training. Inference uses running stats so batching is not required.
2. Dropout p=0.3 (not 0.5): tabular data has fewer redundant features than image pixels; too much dropout hurts.
3. pos_weight in BCE loss: upweights the positive class by 200x to compensate for imbalance instead of oversampling (which would inflate dataset size 200x).
4. No zero init: verified with gradient norm logging that gradients are healthy (norm ~0.1-1.0) from epoch 1.

```python
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset
from typing import Optional


class FraudMLP(nn.Module):
    def __init__(self, input_dim: int = 50, dropout_p: float = 0.3) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(input_dim, 256),
            nn.BatchNorm1d(256),
            nn.GELU(),
            nn.Dropout(p=dropout_p),
            nn.Linear(256, 128),
            nn.BatchNorm1d(128),
            nn.GELU(),
            nn.Dropout(p=dropout_p),
            nn.Linear(128, 1),
        )
        self._init_weights()

    def _init_weights(self) -> None:
        for m in self.modules():
            if isinstance(m, nn.Linear):
                nn.init.kaiming_normal_(m.weight, nonlinearity="relu")
                nn.init.zeros_(m.bias)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x).squeeze(-1)  # (B,)


def train(
    model: FraudMLP,
    loader: DataLoader,
    optimizer: optim.Optimizer,
    device: torch.device,
    pos_weight: float = 200.0,
) -> float:
    model.train()
    criterion = nn.BCEWithLogitsLoss(pos_weight=torch.tensor([pos_weight]).to(device))
    total_loss = 0.0
    for x, y in loader:
        x, y = x.to(device, dtype=torch.float32), y.to(device, dtype=torch.float32)
        optimizer.zero_grad()
        logits = model(x)
        loss = criterion(logits, y)
        loss.backward()
        optimizer.step()
        total_loss += loss.item()
    return total_loss / len(loader)


@torch.no_grad()
def predict(model: FraudMLP, x: torch.Tensor, device: torch.device) -> torch.Tensor:
    model.eval()  # critical: uses running BN stats, disables dropout
    x = x.to(device, dtype=torch.float32)
    logits = model(x)
    return torch.sigmoid(logits)
```

**Results**:
- AUROC: 0.94 on held-out test set
- Precision at 50% recall: 0.42 (vs 0.18 for logistic regression baseline)
- Inference latency: 1.2ms per batch of 64 on CPU (well under 5ms SLA)
- Training time: 8 minutes on a single V100 for 20 epochs over 500K samples

**Lessons Learned**:
- The single most impactful change was using `pos_weight` in BCE loss. Without it, the model predicted all negatives (99.5% accuracy, 0% fraud recall).
- BatchNorm was crucial: without it, training was unstable with Adam at lr=0.001 and required lr=0.0001 to converge, tripling training time.
- Monitoring gradient norms caught an early run where dead ReLU (originally used before switching to GELU) had zeroed 35% of layer-2 neurons after 5 epochs.
