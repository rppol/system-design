# BERT and Pretrained Language Models

> This file is a deep-dive sub-file of the [Natural Language Processing](README.md) module.
> It covers encoder-only pretrained models: BERT, RoBERTa, DeBERTa, ALBERT, DistilBERT, and ModernBERT.
> Transformer-based generative (decoder-only) models are covered in the LLM section.

---

## 1. Concept Overview

BERT (Bidirectional Encoder Representations from Transformers, Devlin et al. 2018) marked the inflection point in NLP. Before BERT, the paradigm was **feature-based**: train a model like Word2Vec or ELMo, extract features, then train a task-specific model on top. BERT introduced the **fine-tuning paradigm**: pretrain a large bidirectional transformer on raw text, then add a simple output head and fine-tune end-to-end on each downstream task.

The result: a single pretrained model achieves state-of-the-art performance across 11 NLP tasks with minimal task-specific architecture. BERT-base achieved 80.5% on GLUE; GPT scored 72.8%. The delta wasn't engineering — it was bidirectionality.

The key innovation: BERT reads the full sequence left-to-right AND right-to-left simultaneously (via self-attention), giving every token context from both directions. GPT-style models at the time used causal (left-to-right only) attention, which lost information.

---

## 2. Intuition

One-line analogy: BERT is like a reader who reads an entire paragraph before answering any question about it — versus a reader who can only see words to the left and must predict each next word.

Mental model: Imagine fill-in-the-blank questions. "The bank can guarantee deposits will eventually cover future ___." Both "losses" and "bonuses" are plausible if you only read left-to-right. But if you also see "...will eventually cover future fines and penalties," the context makes "losses" the clear answer. BERT sees the full bidirectional context at every position, making it far better at understanding than predicting.

Why it matters: Bidirectionality enables better representations for understanding tasks — classification, NER, question answering — where the full context is available at inference time.

Key insight: BERT's `[CLS]` token representation (after fine-tuning) encodes a sentence-level summary because all other tokens attend to it and it attends to all of them through 12 layers of self-attention. Without fine-tuning, `[CLS]` is nearly useless for sentence similarity.

---

## 3. Core Principles

**Masked Language Modeling (MLM):** Randomly mask 15% of input tokens and train the model to predict the masked tokens from bidirectional context. The 15% are split: 80% replaced with `[MASK]`, 10% replaced with a random token, 10% left unchanged. The 10/10 split prevents the model from learning to only handle `[MASK]` tokens — it must always maintain good representations for all tokens.

**Next Sentence Prediction (NSP):** Given two sentence segments A and B, predict whether B actually follows A in the original document (50% positive, 50% random negative). Intended to teach inter-sentence coherence. RoBERTa later showed NSP hurts more than it helps — it forces shorter sequences that reduce context per training step.

**Bidirectionality:** Unlike GPT's causal mask (triangle), BERT uses a full attention matrix — every token attends to every other token. This is why BERT cannot be used for autoregressive generation (no causal constraint), but excels at understanding.

**WordPiece tokenization:** Splits unknown words into subword pieces from a learned vocabulary of ~30K tokens. "unaffordable" → ["un", "##afford", "##able"]. The `##` prefix indicates a continuation subword. WordPiece minimizes the probability of training data under a language model (unlike BPE which uses merge frequency).

**Special tokens:**
- `[CLS]`: Prepended to every sequence. Its final hidden state is used as the aggregate sequence representation for classification tasks.
- `[SEP]`: Separates sentence A from sentence B in two-sequence tasks (QA, NLI, NSP).
- `[MASK]`: Used during MLM pretraining; should never appear at inference time.

---

## 4. Types / Architectures / Strategies

### 4.1 Base BERT Models

| Model | Layers | Hidden Size | Attention Heads | Parameters | GLUE |
|-------|--------|-------------|-----------------|------------|------|
| BERT-base | 12 | 768 | 12 | 110M | 79.6 |
| BERT-large | 24 | 1024 | 16 | 340M | 82.1 |

### 4.2 BERT Variants

| Model | Key Change vs BERT | When to Use |
|-------|--------------------|-------------|
| **RoBERTa** (Liu et al., 2019) | No NSP, dynamic masking (mask changes every epoch), larger batches (8K), more data (160GB vs 16GB), longer training | Better general-purpose encoder; default choice when BERT is considered |
| **ALBERT** (Lan et al., 2019) | Cross-layer parameter sharing + factorized embedding decomposition (vocab_embed 128 → project to 768) + SOP (sentence order prediction) instead of NSP | When parameter count matters; 12M vs 110M params with competitive performance |
| **DeBERTa** (He et al., 2020) | Disentangled attention: separate content and position embeddings with two attention matrices; enhanced mask decoder for MLM pretraining | Best encoder for tasks requiring precise positional understanding; DeBERTa-v3-large scores 91.9 on SQuAD 2.0 |
| **DeBERTa-v3** (He et al., 2021) | RTD (Replaced Token Detection, ELECTRA-style) training objective instead of MLM; parameter-efficient | Current top encoder for most NLU tasks; 86.8 GLUE with 183M params |
| **DistilBERT** (Sanh et al., 2019) | Knowledge distillation from BERT-base to 6-layer student; 40% smaller, 60% faster, 97% of BERT performance | Production latency-constrained applications |
| **ModernBERT** (Warner et al., 2024) | Flash Attention 2, unpadding (removes padding tokens from computation), RoPE positional encoding, alternating local/global attention, extended context (8192 tokens), trained on 2T tokens | State-of-the-art as of 2024; ~2x faster than DeBERTa-v3 on GPU, same or better quality |

### 4.3 Fine-Tuning Heads

| Task Type | Head Architecture | Output |
|-----------|-------------------|--------|
| Text classification | Linear on `[CLS]` → softmax | class probabilities |
| Token classification (NER, POS) | Linear on each token → softmax | per-token label |
| Extractive QA (start/end span) | Two linear layers on all tokens | start logit, end logit per token |
| Sentence pair similarity | Linear on `[CLS]` of [A, SEP, B] → sigmoid | similarity score |
| Masked LM head (pretraining) | Linear + GELU + LayerNorm + output → vocab_size | token probabilities |

---

## 5. Architecture Diagrams

### BERT Input Construction

```
Input:    "The cat sat"         "on the mat"
Tokens:   [CLS] The cat sat [SEP] on the mat [SEP]
Segment:   0    0   0   0    0    1  1   1    1
Position:  0    1   2   3    4    5  6   7    8

Final input embedding = token_embed + segment_embed + position_embed
```

### WordPiece Tokenization Flow

```
Raw text: "unhappiness"
   |
   v
[WordPiece Tokenizer]
   |
   v
["un", "##happ", "##iness"]
   |
   v
[Lookup in 30K vocab] --> token IDs: [2379, 12199, 7985]
   |
   v
[Embedding layer: shape (30522, 768)] --> embeddings
```

### Fine-Tuning for NER (Token Classification)

```
Input: [CLS] Apple is in NYC [SEP]
          |     |   |  |   |    |
       [BERT 12-layer bidirectional transformer]
          |     |   |  |   |    |
       h_CLS  h_1  h_2 h_3 h_4  h_SEP
                |   |  |   |
            [Linear(768, num_labels)]
                |   |  |   |
              O   O  O  B-LOC   (BIO labels)
```

### Fine-Tuning for Extractive QA

```
Input: [CLS] question tokens [SEP] passage tokens [SEP]
                                      |
                          [BERT outputs: h_i for each token]
                                      |
                     [Two linear layers: start_logits, end_logits]
                                      |
                          argmax(start_logits), argmax(end_logits)
                                      |
                          Extract span from passage
```

---

## 6. How It Works — Detailed Mechanics

### Fine-Tuning for Text Classification

```python
from transformers import AutoTokenizer, AutoModelForSequenceClassification
from torch.optim import AdamW
from torch.utils.data import DataLoader, Dataset
import torch
from typing import List

class TextDataset(Dataset):
    def __init__(self, texts: List[str], labels: List[int], tokenizer, max_length: int = 128):
        self.encodings = tokenizer(
            texts,
            truncation=True,
            padding="max_length",
            max_length=max_length,
            return_tensors="pt",
        )
        self.labels = torch.tensor(labels, dtype=torch.long)

    def __len__(self) -> int:
        return len(self.labels)

    def __getitem__(self, idx: int) -> dict:
        return {
            "input_ids": self.encodings["input_ids"][idx],
            "attention_mask": self.encodings["attention_mask"][idx],
            "labels": self.labels[idx],
        }


def fine_tune_bert(
    model_name: str = "bert-base-uncased",
    train_texts: List[str] = [],
    train_labels: List[int] = [],
    num_labels: int = 2,
    num_epochs: int = 3,
    lr: float = 2e-5,
    batch_size: int = 16,
) -> AutoModelForSequenceClassification:
    """
    Fine-tune BERT for sequence classification.

    Critical hyperparameters:
    - lr: 2e-5 to 5e-5 ONLY. Higher causes catastrophic forgetting.
    - batch_size: 16 or 32. Smaller = noisier gradients.
    - num_epochs: 3-5. More causes overfitting on small datasets.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSequenceClassification.from_pretrained(
        model_name, num_labels=num_labels
    )

    dataset = TextDataset(train_texts, train_labels, tokenizer)
    loader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

    # Separate weight decay: apply to weight matrices, not biases/LayerNorm
    no_decay = ["bias", "LayerNorm.weight"]
    optimizer_grouped_parameters = [
        {
            "params": [p for n, p in model.named_parameters()
                       if not any(nd in n for nd in no_decay)],
            "weight_decay": 0.01,
        },
        {
            "params": [p for n, p in model.named_parameters()
                       if any(nd in n for nd in no_decay)],
            "weight_decay": 0.0,
        },
    ]
    optimizer = AdamW(optimizer_grouped_parameters, lr=lr)

    model.train()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)

    for epoch in range(num_epochs):
        total_loss = 0.0
        for batch in loader:
            optimizer.zero_grad()
            outputs = model(
                input_ids=batch["input_ids"].to(device),
                attention_mask=batch["attention_mask"].to(device),
                labels=batch["labels"].to(device),
            )
            loss = outputs.loss
            loss.backward()
            # Gradient clipping: prevents exploding gradients in early fine-tuning
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            total_loss += loss.item()
        print(f"Epoch {epoch+1}: avg loss = {total_loss / len(loader):.4f}")

    return model
```

### Embedding Extraction with Pooling Strategies

```python
from transformers import AutoTokenizer, AutoModel
import torch
import torch.nn.functional as F
from typing import List
import numpy as np


def extract_embeddings(
    texts: List[str],
    model_name: str = "bert-base-uncased",
    pooling: str = "mean",   # "cls" | "mean" | "max"
    max_length: int = 512,
) -> np.ndarray:
    """
    Extract sentence embeddings from BERT.

    Pooling strategies:
    - "cls": Take [CLS] token hidden state. Good ONLY after fine-tuning on sentence tasks.
             Without fine-tuning, [CLS] is a poor sentence representation.
    - "mean": Average over all non-padding token hidden states. Generally best for
              semantic similarity on raw (not fine-tuned) BERT.
    - "max": Max pooling over token dimension. Captures most "salient" features.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModel.from_pretrained(model_name)
    model.eval()

    inputs = tokenizer(
        texts,
        padding=True,
        truncation=True,
        max_length=max_length,
        return_tensors="pt",
    )

    with torch.no_grad():
        outputs = model(**inputs)
        hidden_states = outputs.last_hidden_state  # (batch, seq_len, hidden_size)

    if pooling == "cls":
        embeddings = hidden_states[:, 0, :]  # [CLS] is always position 0
    elif pooling == "mean":
        # Mask out padding tokens before averaging
        mask = inputs["attention_mask"].unsqueeze(-1).float()  # (batch, seq_len, 1)
        embeddings = (hidden_states * mask).sum(1) / mask.sum(1)
    elif pooling == "max":
        mask = inputs["attention_mask"].unsqueeze(-1).bool()
        hidden_states[~mask] = -1e9  # mask padding before max
        embeddings, _ = hidden_states.max(dim=1)
    else:
        raise ValueError(f"Unknown pooling strategy: {pooling}")

    # L2 normalize for cosine similarity computation
    embeddings = F.normalize(embeddings, p=2, dim=-1)
    return embeddings.numpy()
```

### Token Classification (NER)

```python
from transformers import AutoTokenizer, AutoModelForTokenClassification
import torch
from typing import List, Tuple


def predict_ner(
    text: str,
    model_name: str = "dslim/bert-base-NER",
) -> List[Tuple[str, str]]:
    """
    Run NER with a fine-tuned BERT model.
    Returns list of (word, label) pairs.

    dslim/bert-base-NER: fine-tuned on CoNLL-2003
    Labels: O, B-PER, I-PER, B-ORG, I-ORG, B-LOC, I-LOC, B-MISC, I-MISC
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForTokenClassification.from_pretrained(model_name)
    model.eval()

    inputs = tokenizer(text, return_tensors="pt", return_offsets_mapping=True)
    offset_mapping = inputs.pop("offset_mapping")

    with torch.no_grad():
        outputs = model(**inputs)

    logits = outputs.logits  # (1, seq_len, num_labels)
    predictions = logits.argmax(dim=-1)[0]  # (seq_len,)
    id2label = model.config.id2label

    # Align subword predictions back to original words
    results = []
    word_ids = inputs.word_ids()  # subword -> word index mapping
    previous_word_id = None

    for idx, word_id in enumerate(word_ids):
        if word_id is None or word_id == previous_word_id:
            # Skip [CLS], [SEP], and continuation subwords
            continue
        label = id2label[predictions[idx].item()]
        # Get original word by using offset mapping
        start, end = offset_mapping[0][idx]
        word = text[start:end]
        results.append((word, label))
        previous_word_id = word_id

    return results
```

### Knowledge Distillation (DistilBERT pattern)

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from transformers import AutoModelForSequenceClassification, AutoTokenizer
from typing import Optional


def distillation_loss(
    student_logits: torch.Tensor,    # (batch, num_classes)
    teacher_logits: torch.Tensor,    # (batch, num_classes)
    true_labels: torch.Tensor,       # (batch,)
    temperature: float = 4.0,
    alpha: float = 0.7,
) -> torch.Tensor:
    """
    Compute combined distillation + task loss.

    alpha: weight for soft-label (distillation) loss
    (1 - alpha): weight for hard-label (cross-entropy) loss
    temperature: higher T softens teacher distribution, revealing
                 relative similarities between classes

    DistilBERT uses T=4, alpha=0.5 with cosine embedding loss as third term.
    """
    # Soft labels from teacher (temperature-scaled)
    soft_teacher = F.softmax(teacher_logits / temperature, dim=-1)
    soft_student = F.log_softmax(student_logits / temperature, dim=-1)

    # KL divergence loss (soft labels) — multiply by T^2 to restore gradient scale
    distill_loss = F.kl_div(soft_student, soft_teacher, reduction="batchmean") * (temperature ** 2)

    # Hard label cross-entropy
    task_loss = F.cross_entropy(student_logits, true_labels)

    return alpha * distill_loss + (1 - alpha) * task_loss
```

---

## 7. Real-World Examples

**Google Search (2019):** BERT deployed in Google Search to better understand natural language queries. "Can you get medicine for someone pharmacy" — before BERT, "for someone" was ignored; BERT understood the query as about getting medicine on behalf of another person, changing result ranking significantly. This was called one of the biggest leaps in Search history.

**Hugging Face BERT for finance:** FinBERT (ProsusAI/finbert) — BERT-base fine-tuned on 10K financial news articles for sentiment (positive/negative/neutral). Production use at hedge funds for earnings call sentiment scoring. Key finding: general BERT scores 72% F1 on financial sentiment; FinBERT scores 88% F1. Domain-specific fine-tuning adds 16 F1 points.

**DeBERTa in medical NLP:** DeBERTa-v3-large fine-tuned on clinical notes for ICD-10 code extraction. Disentangled attention is particularly valuable for clinical text where positional context matters (e.g., "history of" before a condition means it's past, not current). DeBERTa outperforms RoBERTa by 4 F1 points on the task.

**ModernBERT for code search:** ModernBERT-large processes 8192-token contexts, enabling retrieval over entire function bodies and docstrings — vs BERT's 512 limit that required truncation. GitHub Copilot's embedding-based retrieval can now index complete file contexts rather than just snippets.

---

## 8. Tradeoffs

| Model | Size | Speed (V100) | GLUE | Best For |
|-------|------|-------------|------|---------|
| BERT-base | 110M | 1x (baseline) | 79.6 | Baseline, well-documented |
| RoBERTa-large | 355M | 0.3x | 86.4 | Best quality in BERT family |
| ALBERT-xxlarge | 235M | 0.2x | 91.0 | Research, small deployment footprint |
| DeBERTa-v3-large | 183M | 0.4x | 91.4 | Production NLU, best quality/size ratio |
| DistilBERT | 67M | 1.6x | 77.0 | Latency-critical production |
| ModernBERT-large | 395M | 0.6x (FA2) | 90.0+ | Long contexts (8K+), modern hardware |

| Fine-Tuning vs Feature-Based | |
|-------------------------------|--|
| Fine-tuning | Better performance (all weights adapt), simpler pipeline, risk of catastrophic forgetting on small data |
| Feature-based | Faster training (frozen BERT), useful when target task training data is tiny (<500 examples) |

| NSP (BERT) vs No NSP (RoBERTa) | |
|----------------------------------|--|
| NSP forces shorter sequences | Each training example is split across two sentences, reducing per-example context |
| No NSP = full sequence MLM | RoBERTa packs full 512-token sequences → better contextual representations |
| NSP misleading signal | Negative examples are random sentences, too easy to discriminate via topic alone, not coherence |

---

## 9. When to Use / When NOT to Use

### Use BERT/encoder models when:

- Task is classification, NER, extractive QA, or semantic similarity — not generation
- You need bidirectional context at inference time (full document is available)
- Latency budget allows 10-50ms GPU inference
- You want to fine-tune on labeled data rather than few-shot prompt

### Use DeBERTa-v3 specifically when:

- Maximizing NLU accuracy (leaderboard-competitive performance)
- Tasks where positional information matters (clinical notes, legal contracts)
- Dataset is small (DeBERTa's disentangled attention prevents overfitting better)

### Use DistilBERT when:

- Serving >1000 RPS on CPU without GPU
- Memory budget under 500MB per model instance
- Willing to trade 3-5 F1 points for 1.6x throughput

### Do NOT use encoder-only models when:

- Task requires text generation (use decoder-only models)
- Zero-shot classification without labeled data (use zero-shot prompting with LLM)
- Task involves reasoning chains (use chain-of-thought with generative models)
- You need to generate explanations for predictions (encoder models produce classifications, not text)

---

## 10. Common Pitfalls

### Pitfall 1: Learning rate too high

```python
# BROKEN: LR from standard training is orders of magnitude too high
optimizer = AdamW(model.parameters(), lr=1e-3)   # Normal DL LR → catastrophic forgetting

# FIXED: Use tiny learning rates for fine-tuning pretrained transformers
optimizer = AdamW(model.parameters(), lr=2e-5)   # Recommended range: 2e-5 to 5e-5
```

Production incident: Team fine-tuned BERT for intent classification on 50K examples. Used lr=1e-3 (standard). Training loss dropped fast — looked successful. Evaluation showed random performance (25% on 4-class task). Root cause: pretrained weights were destroyed in epoch 1 at high LR. The model re-learned the task from scratch as a 110M-parameter classification model without pretrained knowledge — effectively identical to a random initialization. Reducing to lr=2e-5 achieved 91% accuracy.

### Pitfall 2: Using [CLS] without fine-tuning for similarity

```python
# BROKEN: Raw BERT [CLS] for sentence similarity is poor
model = AutoModel.from_pretrained("bert-base-uncased")
cls_embed = model(**inputs).last_hidden_state[:, 0, :]
similarity = cosine_similarity(cls_embed[0], cls_embed[1])
# Correlation with human similarity: ~0.2 — worse than random word2vec average

# FIXED: Use mean pooling OR use a model fine-tuned for similarity (SBERT)
from sentence_transformers import SentenceTransformer
sbert = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
embs = sbert.encode(["sentence1", "sentence2"])
# Spearman correlation with human similarity: ~0.85
```

### Pitfall 3: Sequence length 512 OOM on large batches

```python
# BROKEN: batch_size=32 with max_length=512 on 16GB GPU → OOM
# BERT-base memory: 32 * 512 * 768 * 12 layers * 4 bytes * overhead ≈ 16GB+

# FIXED option 1: Reduce batch size, increase grad accumulation
batch_size = 8
gradient_accumulation_steps = 4   # effective batch = 32

# FIXED option 2: Use dynamic padding (pad to longest in batch, not max_length)
tokenizer(texts, padding="longest", truncation=True, max_length=512)
# For short-text tasks (avg 40 tokens), this cuts memory ~12x vs padding to 512
```

### Pitfall 4: Catastrophic forgetting on small datasets

```python
# For <1000 examples: BERT often forgets pretrained representations
# within the first fine-tuning epoch.

# FIXED: Layer-wise learning rate decay (lower LR for lower layers)
def get_layerwise_optimizer(model, base_lr: float = 2e-5, decay: float = 0.95):
    layers = [model.bert.embeddings] + list(model.bert.encoder.layer)
    params = []
    for i, layer in enumerate(layers):
        lr = base_lr * (decay ** (len(layers) - i))
        params.append({"params": layer.parameters(), "lr": lr})
    params.append({"params": model.classifier.parameters(), "lr": base_lr * 10})
    return AdamW(params)
```

### Pitfall 5: Evaluating at the wrong granularity for NER

```python
# BROKEN: Token-level accuracy inflated by 'O' class dominance
# In a document where 90% of tokens are 'O', a model predicting all 'O'
# achieves 90% token accuracy but finds no entities.

# FIXED: Always use entity-level (span-level) F1, not token-level
from seqeval.metrics import f1_score, classification_report

# seqeval.f1_score computes exact span match (both type AND boundaries must match)
f1 = f1_score(true_labels, pred_labels)   # entity-level
# vs sklearn which computes per-token accuracy/F1 (incorrect for NER)
```

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `transformers` (HuggingFace) | Load, fine-tune, and inference with BERT variants | `AutoModel`, `Trainer`, `pipeline` API |
| `sentence-transformers` | SBERT and sentence embedding models | Built on HuggingFace; handles pooling and training |
| `datasets` (HuggingFace) | Download and preprocess NLP benchmark datasets | GLUE, SuperGLUE, CoNLL-2003, SQuAD included |
| `seqeval` | Sequence labeling evaluation (entity-level F1 for NER) | pip install seqeval; computes span-level metrics |
| `PEFT` (HuggingFace) | LoRA/QLoRA fine-tuning of BERT-scale models | Reduces GPU memory for fine-tuning by 4-8x |
| `optimum` (HuggingFace) | ONNX export, quantization, hardware-optimized inference | INT8 quantization gives 2x speedup with <1% quality loss |
| BerViz | Attention visualization for BERT | Interactive head-level attention patterns |
| ModernBERT | Latest (2024) encoder model | Flash Attention 2, unpadding, 8192 token context |

---

## 12. Interview Questions with Answers

**Q: What is Masked Language Modeling and why does BERT use 15% with the 80/10/10 split?**
MLM randomly masks 15% of tokens and trains the model to predict them from bidirectional context. The 15% rate balances efficiency (enough masked tokens to learn from) against representation quality (context tokens must remain meaningful). The 80/10/10 split — 80% replace with `[MASK]`, 10% random word, 10% unchanged — prevents the model from only learning to handle `[MASK]` at fine-tune time. At inference, `[MASK]` never appears, so the model must maintain good representations for real tokens (the 20% of masked positions where it sees the real token or a random token teach it this).

**Q: Why did RoBERTa remove NSP and what evidence supported this?**
NSP was removed because it forces shorter sequences: each training example is split into two shorter sentence segments rather than one full 512-token sequence. This reduces the average context BERT sees per training step. RoBERTa's ablation study showed that removing NSP and packing full sequences improved downstream task performance consistently across GLUE benchmarks. The NSP task itself was too easy — negative pairs are random sentences, easily distinguished by topic signals rather than actual coherence understanding.

**Q: Explain DeBERTa's disentangled attention mechanism.**
Standard BERT attention computes dot products of token embeddings that implicitly include both content and position (since content + positional embeddings are summed before the attention projection). DeBERTa separates content and position into two independent embedding vectors per token. The attention score between token i and token j is computed as the sum of four terms: (1) content-to-content: `H_i · H_j` (2) content-to-position: `H_i · P_{i|j}` (3) position-to-content: `P_{j|i} · H_j` (4) position-to-position is dropped as redundant. The relative position `P_{i|j}` encodes the signed distance from i to j. This gives the model explicit, separate control over content and relative position, improving tasks that require precise positional reasoning.

**Q: How does DistilBERT achieve 97% of BERT performance at 60% of the parameters?**
DistilBERT uses knowledge distillation: a 6-layer student is trained to match the output distribution of the 12-layer BERT teacher. The distillation loss has three terms: (1) soft-label KL divergence from teacher's temperature-scaled logits (captures relative class similarities), (2) cosine embedding similarity loss between student and teacher hidden states (forces internal representations to match), (3) standard cross-entropy on hard labels. The student initializes weights from every other layer of the teacher, keeping even-numbered layers — a practical trick that provides a strong warm start. Training takes 90 hours on 8 V100s. The key insight: teacher's soft outputs encode richer information than one-hot labels, allowing the student to learn more from fewer parameters.

**Q: What is WordPiece tokenization and how does it differ from BPE?**
WordPiece (used by BERT) and BPE (used by GPT) are both subword tokenization algorithms, but they use different merge objectives. BPE merges the most frequent adjacent pair of bytes/characters at each step. WordPiece merges pairs that maximize the language model likelihood of the training data (equivalently, merges pairs where the frequency of the pair divided by the product of individual frequencies is highest). In practice, WordPiece tends to produce more linguistically meaningful splits for inflected languages. BPE is simpler to implement and train, which is why GPT-style models adopted it. Both handle OOV by decomposing unknown words into subword pieces from a fixed vocabulary (30K for BERT, 50K for GPT-2).

**Q: Why is [CLS] a poor sentence representation without fine-tuning?**
During BERT pretraining, `[CLS]` is trained only for NSP (predicting whether two segments are consecutive). This trains it to capture coarse topic/coherence features, not semantic sentence similarity. The `[CLS]` representation is "anisotropic" — token embeddings cluster in a cone rather than uniformly occupying the embedding space. Cosine similarities between random sentence `[CLS]` embeddings cluster around 0.9+ rather than varying with actual semantic similarity. Mean pooling over all non-padding tokens produces more isotropic representations that better reflect semantic content. Sentence-BERT solves this definitively by fine-tuning BERT on sentence pairs with contrastive loss (NLI data), teaching `[CLS]` or mean-pooled representations to be geometrically meaningful.

**Q: How do you fine-tune BERT efficiently on GPUs with limited memory?**
Four strategies: (1) Gradient accumulation — compute gradients over N micro-batches before a single optimizer step, reducing peak memory by N while maintaining effective batch size; (2) Dynamic padding — pad each batch to the longest sequence in that batch rather than a fixed max_length (saves 3-10x memory for short-text tasks); (3) Mixed precision training — use FP16 for forward/backward pass, FP32 for optimizer state (saves ~50% memory, requires loss scaling to prevent underflow); (4) Gradient checkpointing — recompute intermediate activations during backward pass instead of storing them (saves 60% activation memory at cost of ~30% more compute). For BERT-large, combining all four allows fine-tuning on 11GB consumer GPUs.

**Q: What is the difference between BERT-base and BERT-large, and when does larger help?**
BERT-base has 12 layers, 768 hidden units, 12 attention heads, 110M parameters. BERT-large has 24 layers, 1024 hidden, 16 heads, 340M parameters. BERT-large scores 2-3 points higher on GLUE (82.1 vs 79.6) and ~1.5 EM points higher on SQuAD 2.0. The benefit of BERT-large is most pronounced on complex tasks requiring multi-step reasoning or long-range dependency capture (reading comprehension, coreference). On simple classification with >10K examples and short texts, BERT-base typically matches BERT-large because the simpler tasks do not require the full representational capacity. Practical rule: start with BERT-base (3x faster inference), upgrade to BERT-large or DeBERTa-v3-large only if accuracy is below target.

**Q: How does ModernBERT handle 8192-token inputs when BERT is limited to 512?**
ModernBERT uses three architectural changes: (1) RoPE positional encoding — rotary embeddings encode relative position and can be interpolated to longer contexts (unlike BERT's absolute sinusoidal encodings that cannot extrapolate); (2) Alternating local/global attention — most layers use local sliding window attention (window size 128) for efficiency, with global attention layers every N layers for cross-document coherence; (3) Unpadding — removes padding tokens from computation entirely using custom CUDA kernels, so a batch of variable-length sequences is processed as a single concatenated sequence with special attention masking. The combination reduces computation from O(n²) to near-O(n) for most layers while maintaining quality at 8192 tokens.

**Q: What is the enhanced mask decoder in DeBERTa and why does it help?**
Standard BERT's MLM head predicts masked tokens using only the token's hidden state, which combines content and position information from self-attention. DeBERTa's Enhanced Mask Decoder (EMD) adds absolute positional encodings back into the hidden state before the MLM prediction head, using a second tiny transformer layer. This gives the model explicit absolute position information at prediction time, complementing the relative position information from disentangled attention. The intuition: relative positions are great for understanding context between tokens, but absolute positions carry syntactic information (e.g., "the first word of a sentence is more likely a noun") that helps predict masked words. EMD improved DeBERTa's MLM accuracy by ~3% and downstream task F1 by ~1%.

**Q: How do you handle domain shift when deploying a pretrained BERT model?**
Three approaches in order of cost: (1) Fine-tune on target domain data if labeled data exists (most effective; 5K+ examples recommended); (2) Continued pretraining — run MLM on unlabeled domain text first (BioBERT, LegalBERT, FinBERT all use this); (3) Use a domain-pretrained model if one exists (SciBERT for scientific text, ClinicalBERT for medical). Continued pretraining is the most cost-effective for specialized domains: train for 10-100K steps on domain corpus with the same MLM objective before fine-tuning on downstream task. Empirically, domain pretraining adds 3-8 F1 points on biomedical NER and legal classification tasks. Skip it if target domain vocabulary overlaps well with general English (consumer reviews, news).

**Q: What is sentence-order prediction (SOP) in ALBERT and why is it better than NSP?**
ALBERT replaces NSP with Sentence Order Prediction: given two consecutive sentences from the corpus, predict whether they are in the original order (positive) or swapped (negative). Unlike NSP where negatives are random sentences (trivially distinguished by topic), SOP negatives require understanding local discourse coherence — does sentence A logically precede B or vice versa? ALBERT's ablation shows SOP improves performance on discourse-level tasks (NLI, STS) while NSP adds noise. The key insight: any inter-sentence understanding task should use SOP or a similar coherence-aware pretraining objective rather than NSP's topic-detection proxy.

**Q: Describe a production incident caused by BERT's 512 token limit.**
A legal document processing pipeline used BERT-base to classify contract clauses as risky or benign. Legal contracts have clauses ranging from 50 to 2000 tokens. BERT silently truncated all inputs to 512 tokens — the truncation was not logged. Clauses beginning with non-controversial boilerplate (the first 512 tokens) were classified as benign even when the critical risk language appeared at token 600-800. The pipeline was tested only on short sample contracts; production contracts were 3-5x longer. Fix: (1) log truncation events as warnings; (2) use sliding window: classify in 512-token overlapping windows, aggregate predictions by max-risk; (3) migrate to ModernBERT-large for 8192-token contexts. Discovering this cost 6 weeks of incorrect contract analysis in production before a compliance audit caught it.

**Q: How does ALBERT's cross-layer parameter sharing reduce parameters without losing quality?**
ALBERT shares the same weight matrices across all 12 (or 24) transformer layers — every layer uses identical W_Q, W_K, W_V, and FFN weights but processes different input representations. This drops parameter count from 110M (BERT-base) to 12M (ALBERT-base) — a 9x reduction. The model compensates by using larger hidden dimensions (ALBERT-xxlarge: 4096 hidden) with 235M total parameters but only 89M unique. Critically, ALBERT factorizes the embedding matrix: vocabulary embedding uses a small dimension E=128 (rather than 768), then projects to hidden dimension H=4096. This decouples vocabulary size from model depth. The quality tradeoff: ALBERT-xxlarge scores higher than BERT-large (91.0 vs 82.1 GLUE) but is 3x slower at inference because the large hidden dimension is expensive despite fewer unique weights.

**Q: Why does BERT require separate segment embeddings and what happens if you remove them?**
BERT's segment embeddings (0 for sentence A, 1 for sentence B) are the only signal distinguishing the two input sequences in tasks like question answering (question vs passage) or NLI (premise vs hypothesis). Without segment embeddings, the model must rely only on the `[SEP]` token position to distinguish sequences — which is insufficient when sequences have variable lengths. Removing segment embeddings drops SQuAD performance by ~2 F1 points and NLI accuracy by 1-3%. Modern models (RoBERTa, DeBERTa) retain segment embeddings but make them optional in single-sentence tasks. ModernBERT uses no segment embeddings, relying instead on the `[SEP]` token and positional encoding differences — viable because RoPE encodes absolute positions relative to the beginning of the full combined sequence.

---

## 13. Best Practices

1. Start with DeBERTa-v3-base before BERT-base — it consistently outperforms BERT-base by 3-5 GLUE points with similar inference speed on modern hardware.
2. Use the HuggingFace `Trainer` with `evaluation_strategy="epoch"` and `load_best_model_at_end=True` — prevents returning a model from an overfit epoch.
3. Apply weight decay (0.01) to all parameters except biases and LayerNorm weights — these should use 0.0 weight decay, as regularizing them degrades performance.
4. Use dynamic padding (padding="longest") for variable-length text — reduces GPU memory and speeds training by 2-5x for short-text tasks.
5. Monitor gradient norms during fine-tuning — if they spike above 5.0 in early epochs, the learning rate is too high.
6. For NER, always evaluate with seqeval entity-level F1, not sklearn per-token accuracy — per-token accuracy is misleading due to 'O' class dominance.
7. Reserve 10% of training data for validation to monitor overfitting — BERT can overfit to 1000-example datasets within 2 epochs at lr=5e-5.
8. For production deployment, export to ONNX with dynamic axes and use INT8 quantization — reduces model size from 400MB to 100MB with <1% quality loss and 2x latency improvement.
9. Log sequence lengths and truncation rates in production — silent 512-token truncation is a common source of silent quality degradation on long documents.
10. When fine-tuning on <500 examples, freeze the bottom 6 layers (train only top 6 + classifier) — prevents catastrophic forgetting while still adapting upper-layer representations.

---

## 14. Case Study

### Problem: Legal Contract Risk Classification at Scale

**Context:** A legal tech company processes 50K contracts daily. Each contract contains 20-200 clauses. Task: classify each clause as High-Risk, Medium-Risk, or Low-Risk for automated red-flagging before human review.

**Data:** 15K labeled clauses from 6 months of human annotations (3K High, 4K Medium, 8K Low). Imbalanced distribution.

**Phase 1 — Baseline (Week 1)**

TF-IDF (bigrams, 100K features) + Logistic Regression. Weighted F1: 0.74. High-Risk recall: 0.61 (missed 39% of risky clauses — unacceptable for legal). Training time: 8 minutes. Inference: 0.1ms.

**Phase 2 — BERT Fine-Tuning (Week 3)**

```python
# Model selection: DeBERTa-v3-base chosen over BERT-base and RoBERTa-base
# Rationale: Highest GLUE, better positional reasoning for legal language

model_name = "microsoft/deberta-v3-base"

# Training config:
# lr = 2e-5 (lower end — 15K examples is moderate, safer LR)
# batch_size = 16, gradient_accumulation = 4 (effective batch = 64)
# num_epochs = 5 with early stopping (patience = 2)
# class_weights = [3.0, 2.0, 1.0] for [High, Medium, Low]
# weight_decay = 0.01 on non-LayerNorm, non-bias params
# max_length = 512 (covers 95% of clauses)
# warmup_ratio = 0.1 (warmup first 10% of steps)
```

Weighted F1: 0.89. High-Risk recall: 0.84. Training: 4 hours on 1x A100.

**Phase 3 — Long Clause Handling (Week 6)**

5% of clauses exceeded 512 tokens. Two approaches evaluated:
- Sliding window (3 overlapping 512-token windows, max-risk aggregation): +0.02 weighted F1
- ModernBERT-large (8192 context): +0.04 weighted F1, 3x slower

Decision: ModernBERT-large for contracts flagged as "long" (>512 tokens), DeBERTa-v3-base for the rest (95% of volume). Two-model routing via length check.

**Phase 4 — Production Deployment**

```
Contract ingestion
      |
      v
[Clause segmentation service: spaCy sentence boundaries]
      |
      v
[Length check: tokens > 512?]
     /                  \
  No (95%)            Yes (5%)
    |                    |
[DeBERTa-v3-base]  [ModernBERT-large]
    |                    |
    +---------+----------+
              |
     [Risk classification]
              |
     [Threshold: P(High) > 0.7 → High-Risk flag]
              |
     [Priority queue: High-Risk to human review]
```

**Results:**

- Weighted F1: 0.91 (vs 0.74 baseline — +17 points)
- High-Risk recall: 0.87 (vs 0.61 baseline — catches 26% more risky clauses)
- False positive rate (Low flagged as High): 4.2% (acceptable for human-reviewed queue)
- Throughput: 2,000 clauses/second on 2x A100 GPUs
- Monthly GPU cost: $3,800 (vs $28K estimated for GPT-4 at same volume)

**Key Decisions:**

- DeBERTa over BERT-base: +7 weighted F1 points for same inference cost
- Entity-level evaluation caught silent failures on "LIMITATION OF LIABILITY" clause type (only 120 examples, token F1 looked fine, entity F1 was 0.52)
- Kept LR at 2e-5 after an incident at 5e-5 that caused >30% High-Risk recall collapse on epoch 2 validation
