# LoRA (Low-Rank Adaptation)

## 1. Concept Overview

LoRA (Low-Rank Adaptation, Hu et al. 2021) is a parameter-efficient fine-tuning method that adds small trainable matrices alongside frozen pre-trained weights. Instead of updating all d×k parameters in a weight matrix W, LoRA decomposes the update ΔW into a product of two low-rank matrices: ΔW = B × A, where A ∈ ℝ^(r×k) and B ∈ ℝ^(d×r) with rank r ≪ min(d, k).

For a 7B model where a typical attention weight matrix is 4096×4096, the full update is 16.7M parameters. LoRA at rank 16 decomposes this into a 16×4096 + 4096×16 = 131K parameter update — 127× fewer parameters for one weight matrix. Across all target modules, LoRA adds ~0.1-1% of the total model parameters as trainable.

---

## Intuition

> **One-line analogy**: LoRA is like adding a thin correction layer over a painting — instead of repainting the whole canvas, you add a transparent overlay that adjusts specific parts.

**Mental model**: Pre-trained model weights encode general language and world knowledge. Fine-tuning teaches the model new task-specific behavior. The key insight is that the necessary weight updates have low intrinsic rank — the "direction of change" during fine-tuning can be captured by a low-dimensional subspace. LoRA explicitly enforces this: ΔW = B×A forces the update to live in an r-dimensional subspace. Because fine-tuning changes are empirically low-rank, this constraint loses little quality while reducing trainable parameters by 100-1000×.

**Why it matters**: LoRA reduced the compute and memory requirements for LLM fine-tuning from "requires a compute cluster" to "works on two to four consumer GPUs." This democratized fine-tuning: researchers, startups, and individuals can now produce production-quality specialized models from open-source base models.

**Key insight**: The intrinsic dimensionality hypothesis — that the important variation in weight updates during fine-tuning lies in a low-rank subspace — is what makes LoRA work. It's not an approximation: for most tasks, rank 16 captures 90%+ of the representational change needed.

---

## 2. Core Principles

- **Frozen base weights, trainable adapters**: W_original is never updated; only A and B matrices are trained.
- **Low-rank decomposition captures task-relevant updates**: The hypothesis that ΔW ≈ B×A (low rank) holds empirically for most fine-tuning tasks.
- **No inference overhead with merging**: After training, W_merged = W + B×A×(alpha/r) can be computed and stored, eliminating any inference overhead.
- **Modular and swappable**: Multiple LoRA adapters can be maintained and swapped at runtime for different tasks, without storing multiple full model copies.
- **Alpha controls the effective learning rate**: The alpha/r scaling factor acts as an adapter-specific learning rate multiplier; alpha=32 with r=16 doubles the effective scale.

---

## 3. How It Works — Detailed Mechanics

### 3.1 Mathematical Foundation

```
Standard linear layer:
  h = W × x
  W ∈ ℝ^(d×k): pre-trained frozen weight matrix

LoRA modification:
  h = W × x + (B × A) × x × (alpha / r)
  W: frozen (d×k), not updated during training
  A: trainable (r×k), initialized from Gaussian N(0, σ²)
  B: trainable (d×r), initialized to zeros (so ΔW = B×A = 0 at start)

Why initialize B to zero?
  At training start: h = W×x + 0×x = W×x
  Model behaves exactly like base model initially
  Stable training start without disruption to base model behavior

Scaling factor alpha/r:
  Equivalent to adjusting the learning rate of the LoRA module
  Common setting: alpha = 2×r (e.g., r=16, alpha=32)
  This normalizes the update magnitude regardless of rank choice
```

### 3.2 Parameter Count Comparison

```
7B LLaMA-3 model:
  Total parameters: 7,000,000,000
  Attention matrices per layer: Q, K, V, O projections
    Each: 4096 × 4096 = 16,777,216 parameters

LoRA at rank r=16, targeting Q and V projections in 32 layers:
  Per projection, per layer:
    A matrix: 16 × 4096 = 65,536 parameters
    B matrix: 4096 × 16 = 65,536 parameters
    Total per projection: 131,072 parameters

  For Q + V × 32 layers:
    2 projections × 32 layers × 131,072 = 8,388,608 ≈ 8.4M parameters

  Trainable fraction: 8.4M / 7,000M = 0.12%

Full fine-tuning: 7,000M parameters updated
LoRA r=16 (Q+V): 8.4M parameters updated — 833× fewer

Memory savings:
  Full FT:   weights 14GB + gradients 14GB + Adam states 28GB = 56GB
  LoRA r=16: weights 14GB (frozen, no grad) + adapters ~50MB + Adam ~200MB ≈ 15GB
  (Frozen weights still loaded to GPU; only adapter gradients computed)
```

### 3.3 Rank Selection

```
r=4:   ~2M params (Q+V, 32 layers); good for format/style changes
       Example: "always respond in JSON format"
       Training: fast; 1 epoch sufficient for simple adaptation

r=8:   ~4M params; standard for instruction following, chat alignment
       Example: domain-specific chat style, persona adaptation
       Default for most community fine-tuning recipes

r=16:  ~8M params; best balance for task learning and domain adaptation
       Example: SQL generation, code completion in specific style
       Start here for production fine-tuning

r=32:  ~17M params; complex task learning, significant behavior change
       Example: step-by-step reasoning chains, specialized multi-step tasks

r=64:  ~33M params; approaching full fine-tune quality; diminishing returns
       Use when r=32 is insufficient; rare in practice

r=128: ~67M params; almost never justified; use full fine-tune instead

Rule: start with r=16, alpha=32. If quality insufficient, double r.
If r=64 still insufficient, switch to full fine-tuning.
```

### 3.4 Target Module Selection

```
Typical transformer attention block:
  q_proj: query projection (d×d)
  k_proj: key projection (d×d)
  v_proj: value projection (d×d)
  o_proj: output projection (d×d)
  + FFN: gate_proj, up_proj, down_proj

LoRA target configurations (increasing coverage):

Minimal — style/format only (fast training):
  target_modules: ["q_proj", "v_proj"]
  Trainable: ~8M params (7B model)
  Use when: teaching format, style, simple instruction following

Standard — task adaptation:
  target_modules: ["q_proj", "k_proj", "v_proj", "o_proj"]
  Trainable: ~17M params
  Use when: task-specific behavior, Q&A style adaptation

Full attention + FFN — domain adaptation:
  target_modules: ["q_proj", "k_proj", "v_proj", "o_proj",
                   "gate_proj", "up_proj", "down_proj"]
  Trainable: ~30M params
  Use when: significant domain shift, specialized knowledge

Research finding: v_proj contributes most to quality; q_proj second.
Including k_proj and o_proj adds marginal improvement over q+v.
```

### 3.5 Merging LoRA Weights

```
After training, merge LoRA into base model weights:

W_merged = W_frozen + B × A × (alpha / r)
         = W_frozen + ΔW

Result: a standard model file with no adapter overhead
  Same inference performance as base model
  No architectural change during inference
  Fully compatible with any inference framework (vLLM, llama.cpp, etc.)

Code:
  from peft import PeftModel
  model = PeftModel.from_pretrained(base_model, adapter_path)
  merged_model = model.merge_and_unload()
  merged_model.save_pretrained("merged_model_path")

Keep adapters separate when:
  - Multiple task-specific adapters for the same base model
    (swap adapters at runtime without storing multiple full models)
  - Continuing fine-tuning with additional data
  - Experimenting with different rank combinations
  - The base model is much larger than the adapter (70B model, 50MB adapter)
```

### 3.6 PEFT Configuration Code

```python
from peft import LoraConfig, get_peft_model, TaskType
from transformers import AutoModelForCausalLM

# Load base model
base_model = AutoModelForCausalLM.from_pretrained(
    "meta-llama/Meta-Llama-3-8B-Instruct",
    torch_dtype=torch.bfloat16,
    device_map="auto"
)

# Configure LoRA
lora_config = LoraConfig(
    r=16,                     # rank
    lora_alpha=32,            # scaling factor (alpha = 2×r convention)
    target_modules=[          # which weight matrices to adapt
        "q_proj", "k_proj", "v_proj", "o_proj",
        "gate_proj", "up_proj", "down_proj"
    ],
    lora_dropout=0.05,        # dropout on LoRA layers (regularization)
    bias="none",              # don't train bias terms
    task_type=TaskType.CAUSAL_LM
)

# Wrap base model with LoRA adapters
model = get_peft_model(base_model, lora_config)

# Print trainable parameters
trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
all_params = sum(p.numel() for p in model.parameters())
print(f"Trainable: {trainable_params:,} / {all_params:,} = {100*trainable_params/all_params:.2f}%")
# Output: Trainable: 41,943,040 / 8,030,261,248 = 0.52%
```

---

## 4. Architecture Diagram

### LoRA Applied to Transformer Attention
```
Input x
   |
   v
Pre-trained W (frozen) ─────────────────┐
   "no gradient flows through W"        │
                                         ├─> + ─> h (output)
LoRA adapter:                            │
   x ──[A: r×k, trainable]──> low-rank  │
         ──[B: d×r, trainable]──> ΔW×x  │
   scale by (alpha / r) ────────────────┘


At inference after merge:
Input x
   |
   v
W_merged = W + B×A×(alpha/r)  ← single matrix, no branching
   |
   v
h (output)
```

### LoRA Parameter Flow During Training
```
Forward pass:
  x → W×x → + → h
               ^
  x → A → B ──┘
  (gradients flow through A and B only)

Backward pass:
  Loss → ∂L/∂B → update B
  Loss → ∂L/∂A → update A
  Loss → ∂L/∂W = 0 (W is frozen, no gradient accumulation needed)
```

---

## 5. Real-World Examples

### LLaMA → Alpaca (2023)
- Stanford: fine-tuned LLaMA 7B with LoRA r=8 on 52K instruction-following examples
- Training cost: ~$100 on cloud GPUs; 3 hours on 4× A100
- Achieved instruction-following capability comparable to text-davinci-003

### LLaMA → Code-focused adapters
- Community LoRA adapters for code generation: WizardCoder, Phind-CodeLlama
- r=16 to r=64 targeting all attention + FFN modules
- Released as separate adapter weights on HuggingFace; swappable over the same base model

### Multi-adapter serving (vLLM)
- vLLM supports serving multiple LoRA adapters from a single base model
- Different adapters loaded per request based on a routing tag
- Enables serving 50+ specialized models from 1 GPU serving one base model

---

## 6. Tradeoffs

| Configuration | Trainable % | Memory | Quality | Training Speed |
|--------------|-------------|--------|---------|----------------|
| Full fine-tune | 100% | ~56GB (7B) | Best | Slowest |
| LoRA r=4 (Q+V) | 0.06% | ~15GB | Good (style) | Fastest |
| LoRA r=16 (all attn) | 0.24% | ~16GB | Very good | Fast |
| LoRA r=16 (all+FFN) | 0.52% | ~16GB | Excellent | Fast |
| LoRA r=64 (all+FFN) | 2.1% | ~18GB | Near-full | Moderate |

---

## 7. When to Use / When NOT to Use

### Use LoRA When:
- Single GPU or limited GPU memory (LoRA at r=16 fits in 16GB)
- Multiple task-specific adaptations needed from the same base model
- Want to preserve base model capabilities (frozen weights)
- Need to ship adapter files rather than full model copies (50MB vs. 14GB)

### Use Full Fine-Tuning When:
- Fundamental domain shift requiring changes to all model layers
- Access to large GPU cluster makes full FT practical
- Maximum possible quality is required regardless of cost

### Use LoRA with Higher Rank When:
- r=16 quality is insufficient on your task-specific eval set
- Domain shift is significant (medical, legal text with specific structure)
- Training data is large enough (>50K high-quality examples) to benefit from more capacity

---

## 8. Common Pitfalls

**1. Wrong alpha/rank ratio**
Setting alpha = r (instead of alpha = 2×r or alpha = r) halves the effective learning rate of the adapter. This silently reduces training effectiveness.
Fix: Use alpha = 2×r as the default starting point (r=16, alpha=32). If training is unstable, reduce alpha. If adapters don't learn fast enough, increase alpha.

**2. Targeting only Q and V for complex tasks**
Minimal target_modules (q_proj + v_proj) is sufficient for style changes but insufficient for knowledge-intensive tasks or significant behavior changes.
Fix: For task-specific fine-tuning, include all attention projections (Q, K, V, O). For domain adaptation, also include FFN modules (gate_proj, up_proj, down_proj).

**3. Learning rate too high for LoRA**
LoRA's adapter matrices start near zero. A high learning rate (1e-3) causes large early updates that destabilize training before meaningful learning occurs.
Fix: Use 1e-4 to 3e-4 for LoRA (lower than standard LM training). Some recipes use 2e-4 for LoRA specifically; this is well-tested.

**4. Forgetting to normalize gradients with gradient accumulation**
With batch_size=1 and gradient_accumulation_steps=8, the effective batch size is 8. Setting the LR for batch_size=1 but not adjusting for the larger effective batch overestimates the LR.
Fix: Scale LR by √(effective_batch_size / reference_batch_size). Or use a learning rate scheduler that accounts for gradient accumulation.

**5. Loading LoRA adapter with mismatched base model version**
A LoRA adapter trained on LLaMA-3-8B-Instruct will not work correctly with LLaMA-3-8B-Base (different vocabulary, different system prompt structure).
Fix: Always record the exact base model checkpoint used for training. Store this as metadata with the adapter. Validate base model compatibility before serving.

---

## 9. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **HuggingFace PEFT** | LoRA implementation | Standard library; `LoraConfig`, `get_peft_model` |
| **Unsloth** | Fast LoRA training | 2× faster than PEFT; lower VRAM; uses FlashAttention |
| **Axolotl** | Training orchestration | YAML-configured; supports LoRA, QLoRA, full FT |
| **TRL SFTTrainer** | Supervised fine-tuning | Integrates PEFT + HuggingFace Trainer |
| **LLaMA-Factory** | All-in-one fine-tuning | Web UI; multi-model support; easy data handling |
| **vLLM** | LoRA serving | Efficient multi-adapter serving from single base model |
| **HuggingFace Hub** | Adapter storage/sharing | Standard repository for LoRA adapter weights |

---

## 10. Interview Questions with Answers

**Q: What is LoRA and why is it efficient?**
A: LoRA (Low-Rank Adaptation) adds trainable low-rank matrices ΔW = B×A to frozen pre-trained weights, where A ∈ ℝ^(r×k) and B ∈ ℝ^(d×r) with rank r ≪ min(d,k). For a 4096×4096 weight matrix, full fine-tuning updates 16.7M parameters; LoRA at r=16 updates only 2 × (16×4096) = 131K parameters — 127× fewer. Efficiency comes from two sources: fewer parameters means much less gradient computation and memory for optimizer states; frozen base weights need no gradient accumulation, saving ~2× the weight memory. For a 7B model: full FT requires ~56GB; LoRA r=16 requires ~15-16GB.

**Q: What is the difference between LoRA and full fine-tuning?**
A: Full fine-tuning updates every parameter in the model, providing maximum flexibility and quality but requiring the same memory as pre-training (~56GB for a 7B model). LoRA freezes all pre-trained parameters and trains only small adapter matrices, updating ~0.1-1% of parameters. Full fine-tuning: higher risk of catastrophic forgetting (all weights change), requires large GPU cluster, best quality. LoRA: much lower memory (fits in 16GB GPU), lower forgetting risk (base weights preserved), comparable quality for most tasks, adapters are modular and swappable (50MB file vs. 14GB model). The choice is primarily driven by budget, quality requirements, and whether multiple task-specific adapters are needed.

**Q: How does the intrinsic rank hypothesis justify LoRA?**
A: The intrinsic rank hypothesis (Aghajanyan et al. 2020) states that fine-tuning objectives have low intrinsic dimensionality — the optimal fine-tuned model lives in a low-dimensional subspace of the full parameter space. Concretely: when you train a full fine-tuned model and analyze the weight update matrix ΔW = W_finetuned - W_pretrained, the singular value decomposition shows that most of the "work" is done by the top-r singular components. LoRA exploits this by directly parameterizing ΔW as a rank-r matrix. The empirical validation: LoRA at r=16 achieves 95-98% of full fine-tuning quality on most benchmarks, confirming that the update is approximately rank-16.

**Q: How do you choose the right LoRA rank for your use case?**
A: Rank determines the expressiveness of the adapter. Rule of thumb: start with r=16, alpha=32 for most production tasks. r=4 to r=8: style/format changes, simple instruction alignment, small dataset (<1K examples). r=16: standard instruction tuning, task-specific adaptation, most production fine-tuning scenarios. r=32 to r=64: domain adaptation, complex reasoning chains, significant behavior change. Above r=64 shows diminishing returns relative to cost. Empirically: train with r=16 first, evaluate on your task metric, then try r=32 if quality is insufficient. The quality improvement from r=16 to r=32 is meaningful; from r=64 to r=128 is minimal.

**Q: What does the alpha parameter in LoRA control?**
A: Alpha is a scaling factor applied to the LoRA output: h = W×x + (B×A)×x×(alpha/r). The ratio alpha/r controls the effective learning rate of the adapter — larger alpha/r means the adapter's output contributes more strongly to the final activation. Convention: alpha = 2×r (e.g., r=16, alpha=32) gives alpha/r = 2, which empirically produces good training stability. Setting alpha=r (alpha/r=1) effectively halves the adapter's contribution. Setting alpha=4×r can cause training instability. The alpha parameter exists to decouple the scaling from the rank choice: you can change r without changing the effective scale by adjusting alpha accordingly.

**Q: When should you merge LoRA adapters vs. keep them separate?**
A: Merge when: the final production model is a single-task deployment and inference efficiency is critical (no adapter overhead); deploying to inference frameworks that don't support adapters (llama.cpp, some quantization pipelines). Keep separate when: serving multiple task-specific adapters from the same base model (vLLM multi-adapter serving); the base model is extremely large (70B — storing 14GB of adapters beats 280GB of merged models); continuing fine-tuning with additional data; distributing on HuggingFace (users apply the adapter to their own copy of the base model). Merging is mathematically lossless: W_merged = W + B×A×(alpha/r) is exact, not an approximation.

**Q: How does LoRA prevent catastrophic forgetting?**
A: LoRA prevents forgetting through the frozen weights mechanism: the original pre-trained weights W are never updated. All gradient flow is through A and B matrices only. Since W is unchanged, the base model's representations and capabilities are fully preserved. The only change is in the adapter output B×A×x, which is additive — the base model's contribution W×x is always present. In practice, LoRA rarely causes forgetting even for large rank values, unlike full fine-tuning where high learning rates can overwrite general capabilities. The one exception: if training data is heavily one-sided (only domain text, no general text), the adapter can "steer" the model's outputs in ways that appear to reduce general capability.

**Q: How do target module choices affect LoRA quality?**
A: Each target module captures a different aspect of the transformer's computation. Q and V projections control what information is attended to and what's extracted from attended positions — most task-specific behavior changes. O projection controls how attention heads are combined. K projection controls key representations for attention scoring. FFN modules (gate, up, down) control feedforward transformations that encode most of the model's "factual knowledge." For format and style tuning: Q+V is sufficient. For task adaptation: Q+K+V+O captures full attention behavior. For domain knowledge: add FFN modules. Research shows V contributes most to quality improvements, followed by Q; K adds less; O and FFN add incrementally.

**Q: What is the difference between LoRA and adapter layers (Houlsby adapters)?**
A: Adapter layers (Houlsby et al. 2019) insert small bottleneck modules (down-projection → activation → up-projection) after each attention and FFN block. They're always active during inference (unlike merged LoRA). LoRA modifies existing weight matrices in place and can be merged post-training. Key differences: (1) Inference overhead — adapters always add computational cost; merged LoRA has zero overhead; (2) Position — adapters are inserted; LoRA modifies in-place; (3) Mergeability — LoRA merges cleanly; adapters cannot merge; (4) Quality — comparable for most tasks. LoRA has become the dominant PEFT method because of mergeability and the ability to serve multiple adapters from one base model.

**Q: How is LoRA applied to vision and multimodal models?**
A: LoRA applies identically to vision and multimodal models. In Vision Transformers (ViT): apply LoRA to attention Q, K, V, O projections in the image encoder. In multimodal models (LLaVA, InstructBLIP): can apply LoRA to both the language model decoder and the vision encoder independently, or jointly. The rank and target module choices follow the same guidelines as language-only models. One consideration for multimodal fine-tuning: the vision encoder often requires lower ranks (r=4 to r=8) because visual representations change less than language representations during task adaptation. Keep separate LoRA configurations for vision and language components if they require different learning dynamics.

---

## 11. Best Practices

1. **Start with r=16, alpha=32** — the most well-tested defaults; adjust only after measuring quality on your eval set.
2. **Include all attention modules for task-specific fine-tuning** — Q, K, V, O projections; add FFN for domain adaptation.
3. **Use LR 1e-4 to 3e-4** — higher LRs destabilize LoRA training even though fewer parameters are being updated.
4. **Always mask instruction tokens from loss** — only compute cross-entropy loss on the response portion, not the instruction.
5. **Validate before merging** — evaluate merged model vs. adapter model to confirm merge was lossless; occasional numerical precision issues can degrade quality.
6. **Track adapter metadata** — store base model name, exact checkpoint, rank, alpha, target modules; enables correct loading and reproduction.
7. **Test on general benchmarks after fine-tuning** — even with LoRA's frozen base, verify the adapter doesn't degrade the model's general capabilities before production deployment.
