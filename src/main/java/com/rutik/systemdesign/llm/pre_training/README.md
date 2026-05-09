# Pre-Training

## 1. Concept Overview

Pre-training is the first and most expensive phase of building an LLM — the process of training a neural network on massive amounts of text data so it learns language, world knowledge, reasoning patterns, and common-sense understanding. A pre-trained model is a general-purpose "foundation" that can be specialized for downstream tasks through fine-tuning.

Pre-training is fundamentally a self-supervised learning problem: the training signal comes from the data itself (predicting the next token), not from human-labeled examples. This allows training on virtually unlimited amounts of text.

The scale of pre-training is staggering: GPT-4 was trained on trillions of tokens; LLaMA 3 405B on 15+ trillion tokens; total compute often costs tens of millions of dollars. Getting pre-training right — data quality, training stability, hyperparameter choices — has an outsized impact on the final model's capability.

---

## Intuition

> **One-line analogy**: Pre-training is like reading every book, article, and website ever written — the model doesn't memorize, it absorbs patterns, facts, and reasoning styles at enormous scale.

**Mental model**: The model starts with random weights and is shown trillions of tokens of text. Its only task: predict the next token. Over billions of updates, it learns grammar, facts, code syntax, reasoning patterns, world knowledge — all encoded in its weights. It's self-supervised because the "labels" (the next token) come from the data itself. No human annotation needed.

**Why it matters**: Pre-training is the "expensive once" foundation that everything else builds on. The quality of pre-training data and the scale of training compute determines the ceiling of what the model can ever learn. Fine-tuning and alignment just redirect capabilities — they can't create capabilities that weren't learned during pre-training.

**Key insight**: Predicting the next token is a deceptively powerful objective — to predict text well, the model must implicitly learn almost everything about the world that can be expressed in language.

---

## 2. Core Principles

- **Self-supervised learning**: Training signal from predicting next tokens; no human labels needed at scale.
- **Data quality > quantity**: A well-curated 1T token dataset beats a poorly filtered 10T token dataset (the LIMA insight).
- **Training dynamics matter**: Loss curves, gradient norms, and learning rate schedules determine stability and final quality.
- **Irreversibility**: Pre-training mistakes are expensive to fix — a contaminated training set or wrong architectural choice is hard to undo at scale.
- **Compute-optimal training**: Per Chinchilla, the optimal strategy allocates compute equally between model size and tokens trained.
- **Emergent capabilities**: Many capabilities (arithmetic, code, reasoning) emerge only at sufficient scale — they're not explicitly trained but arise from scale.

---

## 3. Training Objectives

### 3.1 Causal Language Modeling (CLM) — GPT-style

Predict the next token given all previous tokens. The loss is the cross-entropy over the full sequence:

```
Text: "The quick brown fox"

Inputs:  [BOS] "The" "quick" "brown"
Targets:        "The" "quick" "brown" "fox"

Loss = -1/T × Σ log P(token_t | token_1, ..., token_{t-1})
```

Properties:
- Naturally autoregressive — model generates text by repeating this prediction
- All tokens in a batch contribute to loss (efficient)
- Used by: GPT, LLaMA, Mistral, Claude, Gemini, all modern generation models

### 3.2 Masked Language Modeling (MLM) — BERT-style

Randomly mask 15% of tokens; predict the masked tokens:

```
Input:  "The [MASK] brown fox [MASK] over"
Target:      "quick"           "jumps"
```

Properties:
- Bidirectional context — better for understanding tasks
- Only ~15% of tokens contribute to loss (less efficient)
- Cannot generate text autoregressively
- Used by: BERT, RoBERTa, DeBERTa, embedding models

### 3.3 Fill-in-the-Middle (FIM) — Code models

Rearrange training examples so the model learns to complete a middle section given prefix + suffix:

```
Original: [PREFIX] [MIDDLE] [SUFFIX]
FIM-SPM:  [SUFFIX] [PREFIX] [MIDDLE]   (suffix-prefix-middle)

Example:
Prefix:  "def factorial(n):\n    if n == 0:\n"
Middle:  "        return 1\n"
Suffix:  "    return n * factorial(n-1)"

Model must predict the middle given prefix and suffix
```

Used by: CodeLLaMA, Starcoder, DeepSeek-Coder. Enables IDE completion features where the cursor is in the middle of existing code.

---

## 4. Architecture Diagrams

### Pre-Training Data Pipeline
```
Raw Data Sources:
  Web crawls (Common Crawl)
  Books (Books3, Project Gutenberg)
  Code (GitHub, The Stack)
  Scientific papers (arXiv, PubMed)
  Wikipedia, Wikidata
  Curated datasets
          |
          v
[Data Collection & Deduplication]
  - URL-level dedup (remove exact URL duplicates)
  - MinHash document dedup (near-duplicate removal)
  - Exact substring dedup
          |
          v
[Quality Filtering]
  - Language identification (keep target language(s))
  - Perplexity filtering (remove gibberish / low-quality)
  - Classifier-based filtering (hate speech, adult content)
  - Heuristic filtering (too short, too repetitive)
  - Removal of PII (emails, phone numbers)
          |
          v
[Data Mixing & Sampling]
  - Set mixing ratios (web: 50%, code: 20%, books: 15%, etc.)
  - Oversample high-quality sources
  - Upsample underrepresented languages
          |
          v
[Tokenization & Packing]
  - Tokenize all documents
  - Pack tokens into fixed-length sequences (e.g., 4096 tokens)
  - Shuffle across documents
          |
          v
[Training]
```

### Learning Rate Schedule
```
Loss
  ^
  |         /-----\
  |        /       \
  |       /         \------------>
  |      / warmup    cosine decay
  |     /
  +----+-----------------------------------> Steps
  0   N_warmup                           N_total

Peak LR: 1e-4 to 3e-4 (depends on model size)
Warmup: 1-2% of total steps
Final LR: ~10% of peak LR (or 0)
```

---

## 5. How It Works — Detailed Mechanics

### Data Quality and Filtering

**Web data (Common Crawl) quality pipeline:**
```
Raw CC crawl: ~100T tokens/year
  |
  v  URL filtering (known-quality domains upweighted)
  |
  v  Language identification (fastText or CLD3)
  |
  v  Deduplication:
     MinHash with k=9 n-grams, Jaccard threshold 0.8
     Remove documents with >80% overlap with any other
  |
  v  Quality classifier (trained on curated positive examples):
     Reddit upvotes as proxy for quality (WebText/OpenWebText)
     Wikipedia/books as high-quality reference
  |
  v  ~3-5% of raw CC survives (but that's still trillions of tokens)
```

### Training Dynamics

**Gradient clipping**: Clip gradient norm to ~1.0. Prevents gradient explosion, especially early in training.

**Loss spikes**: Loss occasionally spikes up then recovers. Usually indicates a problematic batch (bad data). Can be mitigated with data filtering or by rolling back a few hundred steps.

**Batch size ramp-up**: Start with small batch size (256K tokens), linearly increase to target (4M tokens) over first few billion tokens. Improves training stability.

**BF16 vs FP16 training**: BF16 (Brain Float16) has the same exponent range as FP32 but fewer mantissa bits. More numerically stable than FP16 for training. Standard for modern LLM training.

### Compute Scaling

The Chinchilla (Hoffman et al. 2022) formula for compute-optimal training:
```
For compute budget C (in FLOPs):
  N_optimal ≈ (C / 6)^0.5  (model params)
  D_optimal ≈ (C / 6)^0.5 × 20  (training tokens)

For 1e24 FLOPs:
  N ≈ 70B parameters
  D ≈ 1.4T tokens

In practice:
  LLaMA 3 8B trained on 15T tokens (10x Chinchilla-optimal for inference efficiency)
  Rationale: inference on a smaller, longer-trained model is cheaper per token
```

---

## 6. Real-World Examples

### GPT-3 (OpenAI, 2020)
- 175B parameters, 570GB of text data (~300B tokens)
- Data mix: CommonCrawl (60%), WebText2 (22%), Books (16%), Wikipedia (3%)
- Training: 3.14 × 10²³ FLOPs on V100 GPUs
- ~$4-5M estimated training cost
- Launched the LLM era; demonstrated few-shot learning at scale

### LLaMA 3 (Meta, 2024)
- 8B, 70B, 405B variants; 15T+ tokens training data
- Data: curated web, code, math, multilingual
- 128K context via RoPE scaling
- Open weights (community license)
- 405B trained on 16K H100 GPUs for ~77 days

### Mistral 7B (2023)
- 7B params, outperforms LLaMA 2 13B
- Sliding window attention (SWA) for memory efficiency
- GQA for fast inference
- Apache 2.0 license — fully open

### DeepSeek-V3 (2024)
- 671B parameters, 37B active (MoE)
- Trained for $5.5M total (shocked the industry with cost efficiency)
- Multi-token prediction training objective (predict multiple next tokens simultaneously)
- FP8 mixed precision training

---

## 7. Tradeoffs

| Decision | Option A | Option B | Consider |
|----------|----------|----------|---------|
| Model size | Larger (better quality) | Smaller (cheaper inference) | Inference budget |
| Training tokens | More (better quality) | Fewer (cheaper training) | Is model undertrained? |
| Data filtering | Aggressive (cleaner) | Permissive (more data) | Model quality vs. diversity |
| Context length | Short (4K, cheaper) | Long (128K, expensive) | Use case requirements |
| Precision | BF16 (faster) | FP32 (exact) | Always use BF16 for training |

---

## 8. When to Use / When NOT to Use

### Pre-Train From Scratch When:
- Building a truly domain-specialized model (medical, legal, finance) where the knowledge base differs fundamentally
- You have access to billions of domain tokens not available elsewhere
- Regulatory/IP requirements prevent using third-party model weights
- You can afford $1M+ in compute

### Fine-Tune Instead When:
- Adapting a general model for a specific task (cheaper by 100-1000x)
- You have <100B tokens of domain data
- The task is about format/style/following instructions (not learning new knowledge)
- You need results in weeks not months

---

## 9. Common Pitfalls

1. **Training data contamination**: If benchmark test sets are in your training data, evaluation scores are inflated. Run deduplication between training data and all evaluation sets.
2. **Epoch repetition**: For very large models, repeating data (>1 epoch) degrades quality. Use different data mixes across passes.
3. **Imbalanced domain sampling**: Too much low-quality web content drowns out high-quality signal. Careful mixing ratios matter.
4. **Ignoring context packing artifacts**: Naively packing documents can create cross-document attention (token at end of doc A attends to doc B). Use attention masks to prevent this.
5. **Not monitoring training loss curves**: A flat loss for many steps indicates a learning rate issue or data issue.
6. **Hardware failure planning**: With 1000+ GPUs, some will fail. Have checkpointing every 30-60 minutes and automatic restart scripts.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Megatron-LM | Large-scale LLM training | NVIDIA; tensor/pipeline parallel |
| DeepSpeed | ZeRO optimization, mixed precision | Microsoft; works with PyTorch |
| FSDP | Fully Sharded Data Parallel | PyTorch native; replaces DDP for large models |
| GPT-NeoX | Open-source LLM training | EleutherAI framework |
| Nanotron | LLM training framework | HuggingFace; modern replacement |
| torchtitan | Meta's PyTorch-native training | Experimental; clean implementation |
| Common Crawl | Web data source | ~100TB compressed / crawl |
| The Pile | Curated training dataset | EleutherAI; 825GB diverse text |
| DCLM | DataComp for LM | New curated CC dataset, strong quality |
| RedPajama-v2 | Open training dataset | Together AI; 30T tokens |

---

## 11. Interview Questions with Answers

**Q: What is the difference between CLM and MLM training objectives?**
A: CLM (Causal Language Modeling) predicts the next token given only previous tokens — unidirectional, autoregressive, enables text generation. MLM (Masked Language Modeling) masks random tokens and predicts them using bidirectional context — better for understanding tasks but can't generate text. Modern LLMs use CLM; embedding/classification models use MLM.

**Q: What are Chinchilla scaling laws and what did they change?**
A: Chinchilla (Hoffman et al. 2022) showed that previous models like GPT-3 were over-parametrized relative to their training data. The optimal compute allocation splits equally between model size and training tokens. For a given compute budget, training a smaller model on more tokens is better than a larger model on fewer tokens. This led to LLaMA-style training: smaller models trained on much more data.

**Q: How do you handle training instability / loss spikes?**
A: First line of defense is gradient clipping (clip norm to 1.0). For spikes, roll back to the last checkpoint (every 30-60 min) and skip or filter the problematic batch. Long-term, improve data quality filtering to remove pathological examples. Some teams also use gradient norm monitoring to detect spikes before they destabilize training.

**Q: What is data contamination and why is it a problem?**
A: Data contamination occurs when evaluation benchmark examples appear in the training set. The model has "seen" the answers, inflating benchmark scores. This is why LLM evaluation is difficult to trust — most teams don't fully audit their training data. Mitigation: run n-gram deduplication between training data and all benchmarks before training.

**Q: Why is BF16 preferred over FP16 for LLM training?**
A: BF16 has the same 8-bit exponent range as FP32 (handles the dynamic range of gradients and activations), while FP16 has a smaller 5-bit exponent and frequently overflows/underflows during training. FP16 requires loss scaling to avoid underflow; BF16 doesn't. On modern GPUs (A100, H100), BF16 is as fast as FP16 but more numerically stable.

---

## 12. Best Practices

1. **Deduplicate aggressively** — both exact duplicates (substring match) and near-duplicates (MinHash). Repeated data hurts generalization.
2. **Use high-quality data for the final 10% of training** — LIMA-style: the last few billion tokens of high-quality data disproportionately shapes the model's "final personality."
3. **Checkpoint frequently** — every 30-60 minutes at scale; rolling restarts after hardware failures are inevitable.
4. **Monitor per-domain losses** — track validation loss separately on code, math, web text to detect if any domain is being under/over-fit.
5. **Run eval benchmarks every N billion tokens** — validate that capabilities emerge and don't regress as training progresses.
6. **Plan for multi-epoch carefully** — repeating data more than twice at scale hurts; plan data volume upfront.

---

## 13. Case Study: Pre-Training a 7B Parameter Model for Legal Domain

**Problem:** Law firm wants an LLM specialized for legal reasoning. Publicly available legal text: 50B tokens (case law, statutes, contracts). Budget: ~$200K compute.

**Decision: Fine-tune LLaMA 3 8B vs. pre-train from scratch?**

At 50B tokens of domain data, fine-tuning is clearly better:
- Pre-training 7B on 50B tokens: ~1e23 FLOPs = ~$50K compute, but model won't converge well with only legal data (no world knowledge)
- Continued pre-training LLaMA 3 on 50B legal tokens: ~$20K compute, retains world knowledge, adds legal expertise

**Architecture:**
```
Phase 1: Continued pre-training
  Base: LLaMA 3 8B (15T tokens of general data)
  Additional training: 50B legal tokens
  LR: 1e-5 (lower than original to avoid forgetting)
  Context: 8K (legal documents are long)
  Cost: ~$18K on H100s

Phase 2: Instruction fine-tuning
  50K legal Q&A pairs (synthetic + human curated)
  LoRA fine-tuning (r=64, alpha=128)
  Cost: ~$2K

Phase 3: Alignment
  1000 human preference pairs (legal professionals)
  DPO training
  Cost: ~$1K
```

**Results:**
- LexGLUE benchmark: +15% over base LLaMA 3
- Legal professionals rated output quality: 8.2/10 vs. 6.1/10 for base model
- Hallucination rate on legal citations: -40% (from 12% to 7%)
