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

**Scenario: Compressing a 13B model for edge deployment.** A 13B model in fp16 needs 26GB and cannot run on a laptop. The team applies 4-bit GPTQ quantization (26GB -> 6.5GB, 4x smaller, +15% throughput) and AWQ for better accuracy retention, then distills the 13B teacher into a 3B student. The 3B student at 4-bit fits in ~1.6GB and runs on a MacBook M2.

```
13B fp16 (26 GB)
   |
   +-- GPTQ 4-bit  -> 6.5 GB, +15% throughput, ppl 7.2 -> 7.5
   +-- AWQ 4-bit   -> 6.5 GB, ppl 7.2 -> 7.3 (activation-aware, better)
   |
   distillation (13B teacher -> 3B student)
   |     student keeps 94% of teacher MMLU
   v
 3B fp16 (6 GB)  --4-bit-->  1.6 GB  -> runs on MacBook M2
```

GPTQ degrades perplexity from 7.2 to 7.5 (acceptable); AWQ to 7.3 by protecting salient weights. Distillation produces a 3B student retaining 94% of the teacher's MMLU score; quantizing that student to 4-bit yields a 1.6GB model that runs locally with Flash-Attention-2 for memory-efficient attention.

**4-bit GPTQ quantization:**

```python
from transformers import AutoModelForCausalLM, GPTQConfig, AutoTokenizer

def quantize_gptq(model_id: str, out_dir: str) -> None:
    tok = AutoTokenizer.from_pretrained(model_id)
    cfg = GPTQConfig(
        bits=4, dataset="c4", tokenizer=tok,
        group_size=128,          # finer groups -> better accuracy
        desc_act=True,           # quantize in order of activation importance
    )
    model = AutoModelForCausalLM.from_pretrained(
        model_id, quantization_config=cfg, device_map="auto"
    )
    model.save_pretrained(out_dir)
```

**Knowledge distillation with a combined loss:**

```python
import torch
import torch.nn.functional as F

def distill_loss(student_logits: torch.Tensor, teacher_logits: torch.Tensor,
                 labels: torch.Tensor, T: float = 2.0,
                 alpha: float = 0.5) -> torch.Tensor:
    # soft targets: match the teacher's full distribution (dark knowledge)
    soft = F.kl_div(
        F.log_softmax(student_logits / T, dim=-1),
        F.softmax(teacher_logits / T, dim=-1),
        reduction="batchmean",
    ) * (T * T)
    # hard targets: keep task-specific supervision so the student stays grounded
    hard = F.cross_entropy(student_logits, labels)
    return alpha * soft + (1 - alpha) * hard
```

**Keeping sensitive layers in higher precision (mixed-precision quant):**

```python
def build_quant_config(skip_layers: list[str]) -> dict:
    # embeddings and the LM head are accuracy-critical; leave them in fp16
    return {
        "bits": 4,
        "group_size": 128,
        "modules_to_not_convert": skip_layers,   # e.g. ["lm_head", "embed_tokens"]
    }
```

**Pitfall 1 — Quantizing embedding and output layers too aggressively.** 4-bit on the embedding and LM-head layers causes a large quality drop because those layers are sensitive to precision.

```python
# BROKEN: quantize every layer to 4-bit, including embeddings and lm_head
cfg = GPTQConfig(bits=4)   # converts everything

# FIX: keep the first (embeddings) and last (lm_head) layers in fp16; quantize
# the bulk transformer blocks to 4-bit.
cfg = build_quant_config(skip_layers=["embed_tokens", "lm_head"])
```

**Pitfall 2 — Distillation without task supervision.** Training the student to match only the teacher's outputs loses domain-specific accuracy because the teacher's soft labels are imperfect on the target task.

```python
# BROKEN: pure soft-label distillation -> student drifts on the real task
loss = F.kl_div(student_soft, teacher_soft, reduction="batchmean")

# FIX: combine distillation (soft) with task cross-entropy (hard) so the
# student is grounded by ground-truth labels (see distill_loss).
loss = distill_loss(student_logits, teacher_logits, labels, T=2.0, alpha=0.5)
```

**Pitfall 3 — Ignoring the activation distribution.** Round-to-nearest quantization treats all weights equally, but a few salient weights (tied to large activations) carry disproportionate impact.

```python
# BROKEN: naive round-to-nearest 4-bit ignores which weights matter
w_q = round_to_nearest(w, bits=4)

# FIX: activation-aware quantization (AWQ) scales salient weight channels
# before quantizing, preserving the ones that drive large activations.
# ppl 7.2 -> 7.3 with AWQ vs 7.5 with naive GPTQ-style rounding
```

**Interview Q&A:**

**What is the difference between GPTQ and AWQ?** Both are post-training 4-bit weight quantization. GPTQ minimizes per-layer reconstruction error using second-order (Hessian) information, quantizing weights to best preserve each layer's output. AWQ observes that a small fraction of weight channels (those multiplying large activations) matter most and scales them before quantizing, protecting salient weights. AWQ often retains slightly more accuracy and is calibration-light.

**Why does 4-bit quantization give roughly 4x memory reduction and a throughput gain?** Storing weights in 4 bits instead of 16 cuts weight memory ~4x, so a memory-bandwidth-bound LLM moves far fewer bytes per token, raising throughput. The gain is bandwidth-driven; arithmetic is still done in higher precision after dequantization, so the speedup is less than 4x but real (here +15%).

**How does knowledge distillation transfer capability to a smaller model?** The student trains to match the teacher's full softened output distribution (the "dark knowledge" in the relative probabilities of wrong answers), not just the hard label. This richer signal lets a 3B student capture much of a 13B teacher's behavior, here 94% of MMLU, far better than training the 3B from scratch on the same data.

**Why keep embeddings and the LM head in higher precision?** These layers map between the vocabulary and the hidden space and are disproportionately sensitive to quantization error; small errors there propagate across every token. Leaving them in fp16 while 4-bit quantizing the transformer blocks recovers most of the lost accuracy at negligible extra memory.

**What is the temperature in distillation for?** Temperature softens the teacher's softmax, amplifying the relative probabilities of non-top classes so the student learns the teacher's similarity structure rather than just the argmax. The KL term is scaled by T^2 to keep gradient magnitudes comparable. Higher T reveals more dark knowledge but can blur the signal, so it is tuned (often 2-4).

**How do you decide how far to compress for a given deployment?** Set the target by the device memory and latency budget (e.g. fit in 2GB on an M2), then choose the least aggressive compression that meets it, since quality degrades with compression. Validate with task metrics (MMLU, perplexity) and reject configurations that drop below the acceptable threshold; combine techniques (distill then quantize) when a single method cannot reach the target without unacceptable loss.

**Pitfall — Calibration dataset size too small causes GPTQ weight distortion.**

```python
# BROKEN: calibrating GPTQ with only 32 samples — layer Hessian estimate is noisy
from auto_gptq import AutoGPTQForCausalLM, BaseQuantizeConfig

config = BaseQuantizeConfig(bits=4, group_size=128, desc_act=True)
model.quantize(calibration_data[:32])  # 32 samples → Hessian has high variance

# FIX: use 128-512 diverse calibration samples (mixed domains, varied lengths)
model.quantize(calibration_data[:256])  # perplexity drops from 8.4 → 6.9
```

**Why does INT4 quantization hurt attention heads more than FFN layers?** Attention projection weights are applied to token representations that vary widely in magnitude across heads. Symmetric INT4 quantization uses a single scale per group, losing the tail of the distribution where outlier activations live — directly degrading attention score quality. FFN weights are applied to more uniform activations, so quantization error matters less. Mitigation: use per-channel (not per-tensor) scales for QKV projections, or apply outlier-aware quantization (AWQ channels the quantization budget to sensitive weights).

**How do you verify that a quantized model is numerically equivalent enough for production?** Run the same 1,000 held-out prompts through both FP16 and INT4 models. Compute: (1) cosine similarity of output logit distributions (target > 0.99); (2) top-1 token agreement rate (target > 95%); (3) task-specific metric delta (MMLU accuracy drop < 1pp). Never deploy based on perplexity alone — perplexity can improve on the calibration domain while degrading on production queries.

**How does knowledge distillation differ from quantization for reducing model size?** Quantization reduces the bit-width of weights and activations (FP32 → INT8 → INT4), preserving the original model architecture. Knowledge distillation trains a smaller student model to mimic the larger teacher's output distribution (soft labels), resulting in a fundamentally different model with fewer parameters. They are complementary: distill a 70B model to a 7B student, then quantize the 7B student to INT4 — achieving a 4× size reduction from distillation × 8× from INT4 = 32× total compression. Distillation preserves more accuracy for large compression ratios; quantization is faster to apply (hours vs. days of distillation training).

**What is structured vs. unstructured pruning and why does hardware matter for the choice?** Unstructured pruning sets individual weights to zero based on magnitude (l1/l2 norm < threshold). The resulting sparse weight tensors require specialized sparse matrix operations to see inference speedups — standard dense GPU kernels run at the same speed on sparse matrices. Structured pruning removes entire neurons, attention heads, or layers, producing a smaller dense model that runs faster on standard hardware without sparse kernel support. For production GPU serving without custom CUDA kernels, prefer structured pruning (remove heads/layers) or quantization over unstructured sparsity.

**When should you use dynamic quantization vs. static quantization vs. QAT?** Dynamic quantization quantizes weights statically but activations dynamically at runtime — easiest to apply (one `torch.quantization.quantize_dynamic()` call), no calibration needed, 2-4× CPU speedup for LSTM/Transformer inference. Static quantization quantizes both weights and activations using a calibration dataset to determine activation ranges — requires representative calibration data, higher speedup than dynamic. Quantization-aware training (QAT) inserts fake quantization nodes during training, fine-tuning the model to be robust to quantization error — best accuracy recovery for aggressive quantization (INT4, INT2), but requires full training infrastructure. Use dynamic for NLP models on CPU; static for vision models; QAT when dynamic/static accuracy loss is unacceptable.

**What is Flash Attention and why does it improve both speed and memory for transformers?** Flash Attention rewrites the attention computation to avoid materializing the full N×N attention matrix in GPU HBM (high-bandwidth memory). Instead, it tiles the computation in SRAM (fast on-chip cache) and fuses the softmax + matmul into a single kernel pass. For a 4k-token sequence, the attention matrix is 4k×4k × 2 bytes = 32MB — moving it to/from HBM is the bottleneck, not compute. Flash Attention reduces memory complexity from O(N²) to O(N) and achieves 2-4× wall-clock speedup for long sequences. It is now the standard in all major LLM inference frameworks (vLLM, TGI, llama.cpp).

---

**Quick-reference comparison table:**

| Approach | When to use | Trade-off |
|---|---|---|
| Rule-based baseline | Always — establish before ML | Interpretable, brittle on edge cases |
| Simple ML (LR, RF) | < 100k rows, tabular, fast iteration | Lower ceiling than deep models |
| Deep learning | Large data, unstructured input (images/text) | Expensive training, needs GPU |
| Ensembling | Final 1-2% accuracy gain in competition | Complexity, inference latency |
| Distillation/quantization | Inference cost reduction | Accuracy-efficiency trade-off |
