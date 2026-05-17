# Model Compression and Efficiency

## 1. Concept Overview

Model compression reduces the size, memory footprint, and compute requirements of a trained neural network while preserving as much predictive accuracy as possible. As models scale to billions of parameters, uncompressed deployment becomes cost-prohibitive or physically impossible on target hardware (mobile devices, edge chips, cost-constrained cloud instances).

The four primary compression families are quantization, pruning, knowledge distillation, and low-rank factorization. They are often combined: a model may be distilled first (student is 10x smaller), then quantized (INT8 for 4x size reduction), then pruned (20% sparsity for additional throughput gains).

Practical impact: a 70% size reduction with less than 1% accuracy drop is achievable for most production CNN and tabular models using PTQ + structured pruning alone. For NLP models, QAT or distillation may be needed to recover accuracy.

---

## 2. Intuition

Think of a trained model as a complex legal contract with 10,000 pages. Compression is like hiring a paralegal to summarize it into 500 pages that retain all the binding clauses (accuracy) but discard redundant boilerplate (redundant weights). The summarized contract is faster to read (faster inference), cheaper to store (smaller model size), and fits in your briefcase (edge device).

One-line analogy: Compression is the art of saying the same thing with fewer words — fewer bits, fewer parameters, fewer operations.

Why it matters: A 175B parameter GPT-3 model in FP32 requires 700GB of memory. INT8 quantization reduces this to 175GB. QAT-trained INT4 reduces it further to ~87GB, enabling deployment on a cluster that would otherwise be infeasible.

Key insight: Most neural network weights are redundant — studies show 50–90% of weights can be zeroed with under 1% accuracy loss on image classification tasks.

---

## 3. Core Principles

**Accuracy-efficiency Pareto frontier**: Every compression technique trades accuracy for efficiency. The goal is to operate on the Pareto frontier — maximum efficiency for a given accuracy budget.

**Calibration data required for quantization**: PTQ requires a small representative dataset (100–1,000 samples) to measure activation ranges. Using unrepresentative calibration data causes accuracy collapse.

**Structured vs unstructured sparsity**: Unstructured pruning (individual weights) achieves higher sparsity but requires sparse matrix libraries to realize speed gains. Structured pruning (channels, heads, layers) produces dense subnetworks that benefit from standard hardware without specialized libraries.

**Temperature in distillation**: Soft labels at temperature T=3–5 carry more information than hard one-hot labels. The teacher's "wrong" probabilities (e.g., 0.01 for horse, 0.005 for car) encode class similarity that hard labels discard.

**Iterative refinement**: Single-shot compression often underperforms iterative compress-fine-tune cycles. Alternating between compression and brief fine-tuning recovers accuracy incrementally.

---

## 4. Types / Architectures / Strategies

### Post-Training Quantization (PTQ)
- Quantize weights and activations to INT8 (or INT4) after training completes
- No retraining required; works on any frozen model
- Calibration set: 100–1,000 representative samples to compute activation min/max ranges
- Accuracy drop: < 1% for most CNNs and tabular models; larger drops for small models or activation-sensitive architectures (transformers at INT4 without care)
- Dynamic quantization: quantize weights only; activations remain FP32 at runtime (simpler, good for RNNs/LSTMs)
- Static quantization: calibrate activation ranges offline; fixed quantization applied at runtime (better throughput)

### Quantization-Aware Training (QAT)
- Insert "fake quantization" nodes during forward pass: simulate INT8 rounding effects while keeping FP32 weights for gradient updates
- Backward pass uses straight-through estimator to pass gradients through non-differentiable rounding
- Recovers accuracy when PTQ drops > 1% (e.g., MobileNetV2 on ImageNet: PTQ drops 1.8%, QAT recovers to 0.3% below FP32)
- Requires retraining for 5–10% of original training steps (fine-tuning mode is sufficient for most models)

### Weight Pruning
- Magnitude-based (unstructured): zero out weights with |w| below threshold; achieves 50–90% sparsity; requires sparse inference kernels
- Structured (channel/filter pruning): remove entire convolutional filters or attention heads; produces dense smaller model; immediately benefits on any hardware
- Iterative pruning (Lottery Ticket Hypothesis): prune 20% → retrain → prune 20% → retrain; outperforms one-shot pruning
- Gradual magnitude pruning: linearly increase sparsity target over training, avoid accuracy cliff

### Knowledge Distillation
- Teacher model (large, high-accuracy) supervises student model (small, fast) training
- Loss function: `L = alpha * CE(student_logits, hard_labels) + (1 - alpha) * T^2 * KL(softmax(student_logits/T), softmax(teacher_logits/T))`
- T = 3–5 (temperature), alpha = 0.1–0.5 (hard label weight)
- Intermediate distillation (FitNets): align student's intermediate features to teacher's via auxiliary regression losses
- BERT distillation (DistilBERT): 40% smaller, 60% faster, retains 97% of BERT performance on GLUE

### Low-Rank Factorization
- Decompose weight matrix W (n x m) as product of two smaller matrices A (n x r) and B (r x m) where r << min(n, m)
- Parameter reduction: n*m → r*(n+m); for n=m=1024 and r=64: 1M → 128K (87% reduction)
- SVD-based: initialize A and B via truncated SVD of W; fine-tune to recover accuracy
- LoRA (for fine-tuning): fix original weights, learn low-rank delta; does not reduce inference cost unless merged

### TensorRT Optimization
- NVIDIA's inference optimizer: layer fusion, kernel auto-tuning, precision calibration (FP32 → FP16 → INT8)
- Engine serialization: compile once, load at serving time (no JIT overhead)
- Benchmark: ResNet-50 ImageNet — PyTorch FP32: 7ms; TensorRT FP16: 1.5ms; TensorRT INT8: 0.9ms (7.7x)

---

## 5. Architecture Diagrams

### PTQ Calibration and Quantization Flow

```
Trained FP32 Model
        |
        | Collect activation statistics
        v
+-------------------+
| Calibration Pass  |  (100-1000 representative samples)
| - record min/max  |
| - compute scale/  |
|   zero_point      |
+-------------------+
        |
        v
+--------------------+
| Quantized INT8     |  weights: INT8, activations: INT8
| Model              |  scale factors stored as FP32
+--------------------+
        |
        v
  4x smaller, ~3x faster on CPU (VNNI instructions)
  <1% accuracy drop for CNN/tabular models
```

### Knowledge Distillation Architecture

```
Training Data
     |
     +---> Teacher Model (FP32, large)  ---> Soft Labels (temperature T=4)
     |                                                    |
     +---> Student Model (small)        <----- KL divergence loss
                    |
                    +---> Hard Label Cross-Entropy loss
                    |
                    v
              Combined Loss = alpha*CE + (1-alpha)*T^2*KL
```

### Structured Pruning Flow

```
Original CNN: 64 filters in Conv layer
        |
        | Rank filters by L1 norm of weights
        v
Filter ranking: [f_3(0.9), f_1(0.8), ..., f_11(0.01), f_47(0.005)]
        |
        | Remove bottom 30% by magnitude
        v
Pruned CNN: 45 filters  (dense model, hardware-friendly)
        |
        | Fine-tune 1-5 epochs to recover accuracy
        v
  Final model: ~30% fewer MACs, same hardware path
```

---

## 6. How It Works — Detailed Mechanics

### PyTorch Dynamic Quantization (PTQ)

```python
import torch
import torch.nn as nn
from typing import Any

class LSTMClassifier(nn.Module):
    def __init__(self, input_dim: int, hidden_dim: int, num_classes: int) -> None:
        super().__init__()
        self.lstm = nn.LSTM(input_dim, hidden_dim, batch_first=True)
        self.classifier = nn.Linear(hidden_dim, num_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        _, (h_n, _) = self.lstm(x)
        return self.classifier(h_n.squeeze(0))


def apply_dynamic_quantization(model: nn.Module) -> nn.Module:
    """Dynamic PTQ: quantize weights only; activations remain FP32."""
    quantized_model = torch.quantization.quantize_dynamic(
        model,
        qconfig_spec={nn.LSTM, nn.Linear},  # layers to quantize
        dtype=torch.qint8,
    )
    return quantized_model


def compare_model_sizes(fp32_model: nn.Module, int8_model: nn.Module) -> None:
    import os, tempfile

    with tempfile.NamedTemporaryFile(suffix=".pt", delete=False) as f:
        torch.save(fp32_model.state_dict(), f.name)
        fp32_size = os.path.getsize(f.name) / 1e6
        os.unlink(f.name)

    with tempfile.NamedTemporaryFile(suffix=".pt", delete=False) as f:
        torch.save(int8_model.state_dict(), f.name)
        int8_size = os.path.getsize(f.name) / 1e6
        os.unlink(f.name)

    print(f"FP32 size: {fp32_size:.1f} MB")
    print(f"INT8 size: {int8_size:.1f} MB")
    print(f"Reduction: {(1 - int8_size / fp32_size) * 100:.1f}%")
```

### PyTorch Static Quantization with Calibration

```python
import torch.quantization as tq

class QuantizableResidual(nn.Module):
    def __init__(self, dim: int) -> None:
        super().__init__()
        self.linear = nn.Linear(dim, dim)
        self.relu = nn.ReLU()
        # Required stubs for static quantization graph tracing
        self.quant = tq.QuantStub()
        self.dequant = tq.DeQuantStub()

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = self.quant(x)
        x = self.relu(self.linear(x))
        return self.dequant(x)


def static_quantize(
    model: nn.Module,
    calibration_loader: torch.utils.data.DataLoader,
) -> nn.Module:
    model.eval()
    model.qconfig = tq.get_default_qconfig("fbgemm")  # CPU x86
    tq.prepare(model, inplace=True)

    # Calibration pass — no gradient needed
    with torch.no_grad():
        for batch, _ in calibration_loader:
            model(batch)

    tq.convert(model, inplace=True)
    return model
```

### Knowledge Distillation Training Loop

```python
import torch.nn.functional as F

def distillation_loss(
    student_logits: torch.Tensor,
    teacher_logits: torch.Tensor,
    hard_labels: torch.Tensor,
    temperature: float = 4.0,
    alpha: float = 0.3,
) -> torch.Tensor:
    """
    Combined distillation loss.
    alpha: weight of hard label cross-entropy (1-alpha for soft KL term).
    temperature: softens probability distributions for richer soft labels.
    """
    # Soft targets: KL divergence between softened distributions
    soft_student = F.log_softmax(student_logits / temperature, dim=-1)
    soft_teacher = F.softmax(teacher_logits / temperature, dim=-1)
    # Multiply by T^2 to maintain gradient magnitude after softening
    kl_loss = F.kl_div(soft_student, soft_teacher, reduction="batchmean") * (temperature ** 2)

    # Hard targets: standard cross-entropy with ground truth
    ce_loss = F.cross_entropy(student_logits, hard_labels)

    return alpha * ce_loss + (1 - alpha) * kl_loss


def train_student(
    teacher: nn.Module,
    student: nn.Module,
    loader: torch.utils.data.DataLoader,
    optimizer: torch.optim.Optimizer,
    epochs: int = 5,
    temperature: float = 4.0,
    alpha: float = 0.3,
) -> None:
    teacher.eval()  # Teacher is frozen
    student.train()

    for epoch in range(epochs):
        total_loss = 0.0
        for inputs, labels in loader:
            with torch.no_grad():
                teacher_logits = teacher(inputs)

            student_logits = student(inputs)
            loss = distillation_loss(
                student_logits, teacher_logits, labels, temperature, alpha
            )

            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        print(f"Epoch {epoch + 1}: loss={total_loss / len(loader):.4f}")
```

### Structured Pruning (Channel Pruning)

```python
import torch.nn.utils.prune as prune

def apply_structured_pruning(
    model: nn.Module,
    pruning_ratio: float = 0.3,
) -> nn.Module:
    """Remove 30% of convolutional filters by L1 norm (structured)."""
    for name, module in model.named_modules():
        if isinstance(module, nn.Conv2d):
            prune.ln_structured(
                module,
                name="weight",
                amount=pruning_ratio,
                n=1,        # L1 norm
                dim=0,      # prune output channels (dim=0)
            )
            prune.remove(module, "weight")  # make pruning permanent

    return model


def count_parameters(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters() if p.requires_grad)
```

---

## 7. Real-World Examples

**DistilBERT (Hugging Face)**: Knowledge distillation from BERT-base to a 6-layer student. Result: 40% fewer parameters, 60% faster inference, retains 97% of BERT's GLUE benchmark performance. Used in production NLP pipelines where BERT latency is unacceptable.

**MobileNet family**: Depthwise separable convolutions (a form of structured factorization) reduce computation by 8–9x vs standard convolutions with less than 1% accuracy drop on ImageNet. Widely used for on-device inference (iOS, Android).

**GPT-3 INT8 via LLM.int8() (bitsandbytes)**: Mixed-precision quantization — keep outlier dimensions in FP16, quantize others to INT8. Enables 175B model on 4x A100 80GB instead of 8x, with < 1% accuracy degradation on most tasks.

**TensorRT at NVIDIA**: Production ResNet-50 serving at 1.5ms FP16 vs 7ms PyTorch CPU FP32. Used in autonomous vehicle perception pipelines where inference must complete within a 10ms control loop budget.

---

## 8. Tradeoffs

| Method | Size Reduction | Accuracy Drop | Retraining Needed | Hardware Req |
|--------|---------------|---------------|-------------------|-------------|
| PTQ INT8 | ~4x | < 1% (CNN/tabular) | No | Standard CPU/GPU |
| PTQ INT4 | ~8x | 1–5% (model-dependent) | No | Specialized (GPTQ) |
| QAT INT8 | ~4x | < 0.3% | Yes (5–10% of training) | Standard |
| Structured pruning 30% | ~1.4x | < 0.5% after fine-tune | Yes (brief) | Any |
| Unstructured pruning 80% | ~5x (with sparse) | < 1% | Yes | Sparse hardware |
| Knowledge distillation | 5–20x | 1–5% vs teacher | Yes (full student train) | Any |
| Low-rank factorization | 2–10x | 0.5–2% | Yes (fine-tune) | Any |

| Concern | PTQ | QAT |
|---------|-----|-----|
| Ease of use | High (no retraining) | Low (requires training pipeline) |
| Accuracy recovery | Moderate | High |
| Deployment speed | Fast | Slow |
| Best for | Large models, CNNs | Small models, accuracy-critical |

---

## 9. When to Use / When NOT to Use

**Use PTQ when:**
- Model is large (>10M parameters) and calibration dataset is available
- Accuracy drop tolerance is < 1% and model is a CNN or tabular model
- No training pipeline access or compute budget for retraining

**Use QAT when:**
- PTQ causes > 1% accuracy drop (common for very small models or transformers in INT4)
- Model is being actively fine-tuned anyway; adding QAT adds marginal cost
- Target hardware has INT8 SIMD instructions (x86 with VNNI, ARM NEON)

**Use knowledge distillation when:**
- Target latency requires a model 5–20x smaller than the best single model
- A high-quality large model (teacher) already exists
- Labeled data is abundant or teacher can generate pseudo-labels

**Use structured pruning when:**
- Model has clearly redundant filters (confirmed by low activation variance)
- Hardware is standard (no sparse tensor cores available)
- Need a dense, immediately deployable smaller model

**Do NOT use unstructured pruning when:**
- Target hardware does not support sparse matrix operations (most production CPUs/GPUs without specific libraries)
- Sparsity ratio is below 50% — below this, dense computation is equally fast

---

## 10. Common Pitfalls

**War story 1: Unrepresentative calibration data causes INT8 collapse.** A team calibrated an image classification model on 100 samples from their development set, which happened to be all daytime outdoor images. The model served nighttime images in production. Activation ranges computed during calibration did not cover dark pixel distributions, causing INT8 to saturate and clip. Accuracy dropped from 91% to 67%. Fix: calibration data must be statistically representative of production inputs; use at least 500–1,000 samples spanning all known input distributions.

**War story 2: Saving quantized model with torch.save(model) loses quantization info.**
```python
# BROKEN: saves Python object; quantization metadata lost on different PyTorch versions
torch.save(quantized_model, "model.pt")

# FIXED: save state_dict; reload with same architecture
torch.save(quantized_model.state_dict(), "model_int8.pt")
# At load time:
model = build_architecture()
apply_static_quantize_skeleton(model)  # must match quantization configuration
model.load_state_dict(torch.load("model_int8.pt"))
```

**War story 3: Teacher model in train mode during distillation contaminates soft labels.** A team forgot to call `teacher.eval()` before distillation training. Dropout layers in the teacher produced different soft labels each forward pass for the same input, injecting noise into the KL divergence target. Student accuracy plateaued 3% below expected. Fix: always call `teacher.eval()` and wrap teacher forward pass in `torch.no_grad()`.

**War story 4: Pruning before fine-tuning removes accuracy non-trivially; team skips fine-tuning step.** A 40% structured pruning pass dropped accuracy from 92% to 84%. Team deployed without fine-tuning because "pruning is supposed to be drop-in." Fix: structured pruning always requires at least 1–5 epochs of fine-tuning at low learning rate (1e-4) to recover from the accuracy cliff.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| torch.quantization | PTQ/QAT | PyTorch native; fbgemm (CPU x86), qnnpack (ARM) |
| bitsandbytes | LLM quantization | LLM.int8(), NF4 (QLoRA), GPU-focused |
| GPTQ | LLM PTQ | Weight-only INT4 quantization; OBD-based |
| AWQ | LLM PTQ | Activation-aware weight quantization; better than GPTQ on many models |
| TensorRT | NVIDIA optimization | FP16/INT8, layer fusion, engine serialization |
| OpenVINO | Intel optimization | INT8 PTQ for Intel CPUs, VPUs, iGPUs |
| ONNX Runtime | Cross-platform | Quantization APIs for ONNX graphs |
| Optimum (HuggingFace) | NLP compression | QAT, pruning, distillation for Transformers |
| torch.nn.utils.prune | Pruning | Magnitude-based, structured, iterative |
| Distiller (Intel) | Pruning/distillation | Research-grade compression framework |

---

## 12. Interview Questions with Answers

**Q: What is the difference between PTQ and QAT, and how do you choose between them?**
PTQ (Post-Training Quantization) quantizes a frozen model without retraining using a calibration set to determine activation ranges; it is fast to apply but can drop accuracy 1–3% for sensitive models. QAT (Quantization-Aware Training) simulates quantization during training using fake quantization nodes, allowing the model to adapt its weights to the quantization error; it typically keeps accuracy within 0.3% of FP32. Choose PTQ when no training pipeline is available or when accuracy drop is acceptable; choose QAT when PTQ accuracy loss exceeds your budget (typically > 1%).

**Q: How does INT8 quantization achieve ~4x size reduction and ~3x speedup?**
FP32 uses 4 bytes per weight; INT8 uses 1 byte — a 4x reduction in model file size and memory bandwidth. Speedup comes from two sources: (1) INT8 SIMD instructions (VNNI on Intel Cascade Lake, NEON on ARM) pack 4 INT8 multiply-accumulates into a single instruction that would otherwise take 4 FP32 operations; (2) smaller tensors fit better in CPU cache, reducing memory latency. The 3x figure is typical for CPU inference; GPU speedups vary (1.5–4x) depending on tensor core support.

**Q: Explain the knowledge distillation loss function and the role of temperature.**
The distillation loss combines a hard label term (cross-entropy between student logits and ground truth) and a soft label term (KL divergence between student and teacher softened distributions). Temperature T divides logits before softmax, flattening the probability distribution. Higher T (3–5) makes the teacher's "dark knowledge" — small probabilities assigned to wrong classes — more visible and informative. The T^2 factor rescales the KL term to match the gradient magnitude of the CE term, balancing the two losses. Without T^2, the soft label term would be negligible.

**Q: What is the Lottery Ticket Hypothesis and how does it influence pruning strategy?**
The Lottery Ticket Hypothesis (Frankle & Carlin, 2019) states that a randomly initialized dense network contains a sparse subnetwork (the "winning ticket") that, when trained in isolation from the same initialization, reaches comparable accuracy to the full network in the same number of steps. Practically, this means iterative pruning (prune → retrain → prune) with weight rewinding to the original initialization consistently outperforms one-shot pruning. The hypothesis explains why pruned fine-tuned models underperform: fine-tuning does not recover the winning ticket's initialization.

**Q: Why is structured pruning preferred over unstructured pruning in production?**
Unstructured pruning zeros individual weights, creating sparse matrices. Standard CPU and GPU matrix multiplication kernels are optimized for dense computation; sparse matrices do not accelerate unless the sparsity exceeds ~80% and specialized sparse BLAS libraries (cuSPARSE, FBGEMM sparse) are used. Structured pruning removes entire filters, heads, or layers, producing a smaller dense model that runs on any hardware with immediate latency and throughput gains proportional to the removed computation.

**Q: How does low-rank factorization reduce parameters without retraining?**
A weight matrix W of shape (n, m) has n*m parameters. Replacing it with the product AB where A is (n, r) and B is (r, m) and r << min(n, m) reduces parameters to r*(n+m). For a 1024x1024 linear layer with rank r=64: 1,048,576 → 131,072 (87% reduction). The factorization is initialized via truncated SVD of the original W (keeping top-r singular values), which minimizes the reconstruction error ||W - AB||_F. Brief fine-tuning then recovers accuracy lost from the approximation.

**Q: What is mixed-precision quantization and when is it necessary?**
Mixed-precision quantization applies different bit-widths to different layers based on sensitivity. Layers close to the input/output or with large activation variance are kept in FP16 or FP32; less sensitive layers are quantized to INT8 or INT4. LLM.int8() specifically identifies "outlier" dimensions in transformer attention that cause INT8 saturation and keeps those in FP16 while quantizing the rest to INT8. It is necessary for large language models where uniform INT8 causes significant accuracy degradation due to activation outliers in attention layers.

**Q: How do you validate that a compressed model is production-safe before deploying?**
Run a three-part evaluation: (1) accuracy gate — compressed model must exceed a minimum threshold (e.g., baseline - 1%) on a held-out test set; (2) latency benchmark — measure P50/P99 on representative batch sizes and input shapes; (3) output distribution comparison — plot prediction score histograms for compressed vs original model on a sample of production-like inputs; large divergence indicates a quantization issue even when aggregate accuracy looks fine. Additionally, run integration tests with actual serving infrastructure (not just local benchmarks) as ONNX conversion bugs sometimes appear only in the deployed runtime.

**Q: What is calibration in the context of PTQ, and what happens if it is done incorrectly?**
Calibration is the process of running the model on a representative dataset (100–1,000 samples) to collect statistics (min, max, or percentile distributions) on activation values at each layer. These statistics determine the quantization scale and zero-point that map the FP32 range to INT8. If calibration data is unrepresentative (e.g., only daytime images when production includes nighttime), the computed ranges will clip or saturate production inputs, causing severe accuracy degradation. Best practice: use stratified samples covering all known production input distributions.

**Q: How does QAT use the straight-through estimator?**
QAT inserts fake quantization nodes: during the forward pass, activations and weights are rounded to the nearest INT8 value (simulating quantization), then the computation continues in FP32. The rounding operation is non-differentiable (gradient is zero almost everywhere). The straight-through estimator replaces the true gradient of the rounding function with 1 (pass the upstream gradient through unchanged) when the pre-rounded value is within the quantization range, and 0 otherwise. This allows standard backpropagation to update weights despite the non-differentiable rounding.

**Q: Give concrete size and latency numbers for a compressed ResNet-50.**
ResNet-50 FP32: ~98 MB model file, ~7ms inference on Intel Xeon CPU. After INT8 PTQ: ~25 MB (~4x smaller), ~2.5ms CPU inference (~2.8x faster). After TensorRT FP16 on V100 GPU: ~49 MB, ~1.5ms. After TensorRT INT8 on V100: ~25 MB, ~0.9ms (7.7x vs FP32 CPU). DistilResNet via knowledge distillation (18 layers vs 50): ~45 MB FP32, ~3.5ms CPU, with ~1.5% ImageNet top-1 accuracy drop vs full ResNet-50.

---

## 13. Best Practices

- Always benchmark PTQ before attempting QAT; PTQ is sufficient for most CNNs and tabular models and requires no retraining
- Calibration set must represent production input distribution; use at least 500 samples; verify with summary statistics (mean, std, percentiles)
- For INT8 quantization on x86, use the `fbgemm` backend; for ARM (mobile), use `qnnpack`
- After structured pruning, always fine-tune for at least 1 epoch at a reduced learning rate (10x lower than original) before evaluating accuracy
- Distillation works best when the teacher and student share the same architecture family (both transformers, both CNNs); cross-architecture distillation requires intermediate feature alignment
- Use TensorRT for NVIDIA GPU production deployments; rebuild the engine after any model change (engines are not portable across GPU generations)
- Validate compressed models with the exact same serving stack as production; ONNX conversion and TRT engine compilation can introduce numerical differences
- Track model compression metadata in your model registry: compression method, compression ratio, accuracy delta, calibration dataset hash
- For LLMs, prefer AWQ over GPTQ for weight-only INT4 quantization — AWQ typically achieves better perplexity by accounting for activation magnitudes during weight quantization

---

## 14. Case Study

### Compressing a BERT-based Sentiment Classifier for Mobile Deployment

**Problem**: A product team trained a BERT-base sentiment classifier (110M parameters, 440MB FP32) for in-app review analysis. Target: deploy on-device (iOS, Android) with < 50MB model size, < 20ms inference on mid-range phone (Snapdragon 778G), and < 1% accuracy drop vs cloud BERT-base (accuracy: 91.3% on test set).

**Compression strategy**:

Step 1 — Knowledge distillation to DistilBERT-style student:
- Teacher: BERT-base (12 layers, 110M params)
- Student: 6-layer BERT (66M params, 264MB FP32)
- Training: 5 epochs distillation on training set, T=4, alpha=0.2
- Result: student accuracy 90.1% (1.2% drop), 40% smaller, 1.9x faster

Step 2 — Dynamic PTQ INT8:
- Applied `torch.quantization.quantize_dynamic` to all `nn.Linear` layers
- No calibration set needed for dynamic quantization
- Result: 66MB (4x reduction vs student FP32), accuracy 89.8% (0.3% additional drop from student)
- Total accuracy drop vs BERT-base: 1.5% (within budget)

Step 3 — ONNX export and CoreML conversion for iOS:
- Exported 6-layer INT8 student to ONNX (opset 17, dynamic axes)
- Converted ONNX → CoreML using coremltools; applied FP16 weight precision in CoreML
- Final model size on iOS: 41MB (under 50MB target)
- Inference on Snapdragon 778G via ONNX Runtime Mobile: 14ms (under 20ms target)

**Results summary**:
```
Model              Size     Accuracy    Latency (mobile)
BERT-base FP32     440 MB   91.3%       N/A (too large)
Student FP32       264 MB   90.1%       52ms
Student INT8       66 MB    89.8%       18ms
Student INT8+ONNX  41 MB    89.7%       14ms
```

**Key lessons**:
- Distillation was necessary first; PTQ alone on BERT-base dropped accuracy 2.3% (exceeded 1% budget)
- Dynamic quantization was preferred over static because the activation distributions for text inputs are variable; static calibration with 500 samples did not cover all token patterns adequately
- CoreML FP16 conversion from ONNX added only 0.1% accuracy drop but cut model size by another 37%
- The combined pipeline (distillation + dynamic PTQ + ONNX + CoreML FP16) achieved 70% size reduction with 1.6% total accuracy drop
