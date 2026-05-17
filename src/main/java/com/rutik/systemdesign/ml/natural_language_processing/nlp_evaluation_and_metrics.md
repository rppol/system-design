# NLP Evaluation Metrics and Text Augmentation

> This file is a deep-dive sub-file of the [Natural Language Processing](README.md) module.
> It covers generation evaluation metrics (BLEU, ROUGE, METEOR, BERTScore), sequence labeling metrics
> (NER entity-level F1), calibration for NLP classifiers, and text augmentation techniques.
> LLM-specific evaluation (MMLU, HumanEval, LLM-as-judge) is covered in `llm/evaluation_and_benchmarks/`.

---

## 1. Concept Overview

Evaluating NLP models is harder than evaluating image classifiers. A translated sentence "The feline rested" is equally valid as "The cat sat" — but standard accuracy metrics would score it zero. NLP evaluation requires metrics that capture semantic equivalence, not just exact string match.

The landscape of NLP evaluation metrics:

1. **Reference-based generation metrics** (BLEU, ROUGE, METEOR): Compare model output against human reference strings using n-gram overlap, recall, or alignment. Fast, reproducible, but correlate imperfectly with human judgment.
2. **Learned metrics** (BERTScore): Use contextual embeddings to measure similarity, capturing paraphrase.
3. **Intrinsic language model metrics** (perplexity): Measure how well the model predicts held-out text. Correlates with fluency but not task performance.
4. **Task-specific metrics** (entity-level F1 for NER, EM/F1 for QA, Accuracy/F1 for classification): Directly measure what matters for the task.
5. **Calibration metrics** (ECE, reliability diagrams): Measure whether model confidence scores are trustworthy.

Understanding when each metric is appropriate — and when it is misleading — is a core senior ML engineer skill.

---

## 2. Intuition

One-line analogy: BLEU is a precision expert (did you say the right things?), ROUGE is a recall expert (did you cover the important things?), BERTScore is a semantic similarity expert (are the meanings equivalent?), and entity-level F1 is an exact-match expert (did you find the right named entities?).

Mental model: Imagine grading a translation exam. A teacher who counts matching words (BLEU) would penalize valid paraphrases. A teacher who checks whether key concepts from the source are mentioned (ROUGE recall) would reward extractive copying. A teacher who reads both and judges semantic equivalence (BERTScore) is most similar to human judgment. A teacher grading NER would mark wrong if you found the right entity at the wrong span boundaries — even one character off counts as wrong (entity-level F1).

Why it matters: Choosing the wrong metric can mislead model development. A translation model optimized purely for BLEU produces shorter, more repetitive output. A summarization model optimized for ROUGE-1 recall produces extractive output that copies source text verbatim. Knowing what each metric actually measures determines which one to use.

Key insight: Perplexity does not correlate with downstream task performance for fine-tuned models. A fine-tuned BERT with higher MLM perplexity can achieve better NER F1 than a lower-perplexity version — because perplexity measures language modeling fitness, not task fitness.

---

## 3. Core Principles

**Precision vs Recall for generation:** BLEU measures precision (of the generated text, how much is correct?), suitable for tasks where outputs should be concise and accurate (machine translation). ROUGE measures recall (of the reference text, how much important content was covered?), suitable for summarization where comprehensive coverage matters.

**Corpus-level vs sentence-level BLEU:** Sentence-level BLEU is unreliable due to the brevity penalty being applied per sentence. Always use corpus-level BLEU when comparing systems. The `sacrebleu` library provides a standard, reproducible implementation.

**n-gram precision clipping:** Raw n-gram precision can be gamed by repeating high-frequency words. BLEU clips each n-gram count to the maximum count in any reference: for "the the the the the" with reference "the cat sat on the mat," "the" is clipped to count 2 (max occurrences in any reference).

**Brevity penalty:** BLEU's brevity penalty (BP) penalizes outputs shorter than the reference. `BP = exp(1 - r/c)` where r=reference length, c=candidate length. If c >= r, BP = 1 (no penalty). This prevents trivial cheating by generating a single common word with high precision.

**Entity-level vs token-level F1:** For NER, token-level F1 inflates performance because ~90% of tokens are class O (not an entity). A model predicting all-O achieves 90% token accuracy. Entity-level F1 requires that both the entity type AND the exact span boundaries are correct — a single-character offset counts as wrong. Always report entity-level F1 for NER.

**Expected Calibration Error (ECE):** Measures whether confidence scores match actual accuracy. Group predictions by confidence buckets (0.0-0.1, 0.1-0.2, ..., 0.9-1.0); compute |accuracy - confidence| per bucket; take weighted average. A perfectly calibrated model has ECE=0. Production classifiers used for decision-making must be calibrated.

---

## 4. Types / Architectures / Strategies

### 4.1 Generation Metrics

| Metric | Type | What It Measures | Key Weakness |
|--------|------|-----------------|--------------|
| **BLEU** | Precision-based | Modified n-gram precision (1-4) with brevity penalty | Penalizes valid paraphrases; poor at sentence level |
| **ROUGE-1** | Recall-based | Unigram recall (word overlap) | Rewards extractive copying |
| **ROUGE-2** | Recall-based | Bigram recall | Better than ROUGE-1 for fluency |
| **ROUGE-L** | LCS-based | Longest common subsequence as F-measure | Captures sentence structure without order constraint |
| **METEOR** | Alignment-based | Unigram F-measure with stemming, synonyms, and alignment | Complex; language-dependent resources |
| **BERTScore** | Embedding-based | Contextual embedding precision, recall, F1 | Sensitive to BERT model version |
| **chrF** | Character n-gram | Character n-gram F-score | Good for morphologically rich languages |

### 4.2 Task-Specific Metrics

| Task | Metric | Notes |
|------|--------|-------|
| NER | Entity-level F1 | Exact span + type match; computed by seqeval |
| Extractive QA | EM (Exact Match) + F1 | EM: exact string match; F1: token-level overlap |
| Classification | Accuracy, Macro-F1, Weighted-F1 | Use Macro-F1 for imbalanced classes |
| Language modeling | Perplexity | `exp(H)` where H is average cross-entropy per token |
| Semantic similarity | Pearson/Spearman correlation with human ratings | STS benchmark standard |
| Information retrieval | NDCG@K, MRR@K, Recall@K | See text_representation_and_retrieval.md |

### 4.3 Text Augmentation Strategies

| Strategy | Description | Best For |
|----------|-------------|---------|
| **EDA (Easy Data Augmentation)** | Synonym replacement, random insertion, swap, deletion | Short text classification |
| **Back-translation** | Translate to language X, translate back | Semantic paraphrase generation |
| **Contextual augmentation (BERT MLM)** | Use BERT to replace tokens with contextually plausible alternatives | Improved over synonym replacement |
| **Mixup (in embedding space)** | Interpolate between two examples in embedding space | Regularization, calibration |
| **Label-preserving token deletion** | Delete tokens that don't affect label (non-entity tokens for NER) | NER with limited data |
| **Template-based augmentation** | Fill slots in templates with entity substitutes | Low-resource NER, relation extraction |

---

## 5. Architecture Diagrams

### BLEU Computation Flow

```
Candidate: "The cat is sitting on the mat"
Reference: "The cat sat on the mat"

Step 1: n-gram extraction (n=1,2,3,4)
  Candidate 1-grams: {The:1, cat:1, is:1, sitting:1, on:1, the:1, mat:1}
  Reference 1-grams: {The:1, cat:1, sat:1, on:1, the:1, mat:1}

Step 2: Clipped counts (min of candidate count and max reference count)
  The: min(1,1)=1, cat: min(1,1)=1, is: min(1,0)=0, sitting: min(1,0)=0
  on: min(1,1)=1, the: min(1,1)=1, mat: min(1,1)=1
  Clipped total: 6, Candidate total: 7

Step 3: n-gram precision for n=1: p_1 = 6/7 = 0.857

Step 4: Repeat for n=2,3,4; geometric mean
  p_1=0.857, p_2=0.6, p_3=0.4, p_4=0.2

Step 5: Brevity penalty
  c=7 (candidate), r=6 (reference) -> c > r -> BP = 1

Step 6: BLEU = BP × exp(sum_n(w_n × log(p_n))) with w_n=0.25
  = 1 × exp(0.25 × (log(0.857)+log(0.6)+log(0.4)+log(0.2)))
  = 1 × exp(0.25 × (-0.154 - 0.511 - 0.916 - 1.609))
  = exp(-0.797) ≈ 0.450
```

### BERTScore Computation

```
Candidate: "The feline rested"
Reference: "The cat sat"

Step 1: Tokenize and encode with BERT
  Candidate tokens: [the, feline, rested]   -> embeddings: [c1, c2, c3]
  Reference tokens: [the, cat, sat]         -> embeddings: [r1, r2, r3]

Step 2: Cosine similarity matrix (3×3)
  sim(c_i, r_j) for all i,j:
        the   cat   sat
  the  [0.99, 0.61, 0.51]
  feline[0.60, 0.87, 0.55]   <- "feline" closest to "cat"
  rested[0.51, 0.54, 0.91]   <- "rested" closest to "sat"

Step 3: Precision: for each candidate token, max similarity to any reference token
  P = (max(0.99,0.61,0.51) + max(0.60,0.87,0.55) + max(0.51,0.54,0.91)) / 3
    = (0.99 + 0.87 + 0.91) / 3 = 0.923

Step 4: Recall: for each reference token, max similarity to any candidate token
  R = (max(0.99,0.60,0.51) + max(0.61,0.87,0.54) + max(0.51,0.55,0.91)) / 3
    = (0.99 + 0.87 + 0.91) / 3 = 0.923

Step 5: F1 = 2PR/(P+R) = 0.923
```

### ECE Reliability Diagram

```
Confidence vs Accuracy:

1.0 |                          *
    |                    *
Acc |              *
    |        *
0.5 |  *
    |
    |  --- Perfect calibration diagonal
    |
0.0 +--+--+--+--+--+--+--+--+--+--
    0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0
                   Confidence

Model 1 (*): Points on diagonal -> ECE ≈ 0 (well calibrated)
Model 2: Points above diagonal -> overconfident (ECE > 0)
Model 3: Points below diagonal -> underconfident (ECE > 0)
```

---

## 6. How It Works — Detailed Mechanics

### BLEU from Scratch

```python
import math
import re
from collections import Counter
from typing import List, Tuple


def tokenize(text: str) -> List[str]:
    """Lowercase whitespace tokenization."""
    return text.lower().split()


def get_ngrams(tokens: List[str], n: int) -> Counter:
    """Count n-grams in a token list."""
    return Counter(tuple(tokens[i:i+n]) for i in range(len(tokens) - n + 1))


def clipped_precision(
    candidate_tokens: List[str],
    reference_tokens_list: List[List[str]],
    n: int,
) -> Tuple[int, int]:
    """
    Compute clipped n-gram precision counts for BLEU.
    Clips each n-gram count to max count across all references.
    Returns (clipped_count, total_candidate_count).
    """
    candidate_ngrams = get_ngrams(candidate_tokens, n)
    if not candidate_ngrams:
        return 0, 0

    # Max count of each n-gram across all references
    max_ref_counts: Counter = Counter()
    for ref_tokens in reference_tokens_list:
        ref_ngrams = get_ngrams(ref_tokens, n)
        for ngram, count in ref_ngrams.items():
            max_ref_counts[ngram] = max(max_ref_counts[ngram], count)

    # Clip candidate counts
    clipped = sum(
        min(count, max_ref_counts.get(ngram, 0))
        for ngram, count in candidate_ngrams.items()
    )
    total = sum(candidate_ngrams.values())
    return clipped, total


def brevity_penalty(candidate_len: int, reference_len: int) -> float:
    """
    BP = 1 if candidate >= reference, else exp(1 - r/c).
    Uses closest reference length (not average or max).
    """
    if candidate_len >= reference_len:
        return 1.0
    return math.exp(1 - reference_len / candidate_len)


def corpus_bleu(
    candidates: List[str],
    references: List[List[str]],   # references[i] is a list of reference strings for candidate i
    max_n: int = 4,
    weights: Tuple[float, ...] = (0.25, 0.25, 0.25, 0.25),
) -> float:
    """
    Corpus-level BLEU score.
    Always use corpus-level; sentence-level BLEU is unreliable.

    candidates: list of generated strings
    references: list of lists of reference strings (multiple refs supported)
    """
    clipped_counts = [0] * max_n
    total_counts = [0] * max_n
    candidate_len = 0
    reference_len = 0

    for cand_str, ref_strs in zip(candidates, references):
        cand_tokens = tokenize(cand_str)
        ref_tokens_list = [tokenize(r) for r in ref_strs]

        candidate_len += len(cand_tokens)

        # Closest reference length (not average)
        ref_lengths = [len(t) for t in ref_tokens_list]
        closest_ref_len = min(ref_lengths, key=lambda r: abs(r - len(cand_tokens)))
        reference_len += closest_ref_len

        for n in range(1, max_n + 1):
            clipped, total = clipped_precision(cand_tokens, ref_tokens_list, n)
            clipped_counts[n-1] += clipped
            total_counts[n-1] += total

    # Compute precisions with smoothing for n-grams with 0 matches
    precisions = []
    for n in range(max_n):
        if total_counts[n] == 0:
            precisions.append(0.0)
        elif clipped_counts[n] == 0:
            precisions.append(0.0)  # BLEU = 0 if any n-gram precision is 0
        else:
            precisions.append(clipped_counts[n] / total_counts[n])

    if any(p == 0 for p in precisions):
        return 0.0

    bp = brevity_penalty(candidate_len, reference_len)
    log_avg = sum(w * math.log(p) for w, p in zip(weights, precisions))
    return bp * math.exp(log_avg)
```

### ROUGE Computation

```python
from typing import List, Tuple


def rouge_n(
    candidate: str,
    reference: str,
    n: int = 2,
) -> Tuple[float, float, float]:
    """
    Compute ROUGE-N precision, recall, F1.
    ROUGE is recall-oriented: designed for summarization evaluation.
    """
    cand_ngrams = get_ngrams(tokenize(candidate), n)
    ref_ngrams = get_ngrams(tokenize(reference), n)

    if not ref_ngrams:
        return 0.0, 0.0, 0.0

    # Overlap (clipped to min of candidate and reference counts)
    overlap = sum(
        min(count, ref_ngrams.get(ngram, 0))
        for ngram, count in cand_ngrams.items()
    )

    precision = overlap / sum(cand_ngrams.values()) if cand_ngrams else 0.0
    recall = overlap / sum(ref_ngrams.values())
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

    return precision, recall, f1


def lcs_length(a: List[str], b: List[str]) -> int:
    """Compute length of longest common subsequence."""
    m, n = len(a), len(b)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if a[i-1] == b[j-1]:
                dp[i][j] = dp[i-1][j-1] + 1
            else:
                dp[i][j] = max(dp[i-1][j], dp[i][j-1])
    return dp[m][n]


def rouge_l(
    candidate: str,
    reference: str,
    beta: float = 1.0,
) -> Tuple[float, float, float]:
    """
    ROUGE-L based on Longest Common Subsequence.
    beta=1.0 gives equal weight to precision and recall (standard ROUGE-L).
    """
    cand_tokens = tokenize(candidate)
    ref_tokens = tokenize(reference)

    lcs = lcs_length(cand_tokens, ref_tokens)
    if lcs == 0:
        return 0.0, 0.0, 0.0

    precision = lcs / len(cand_tokens) if cand_tokens else 0.0
    recall = lcs / len(ref_tokens)
    f1 = (
        (1 + beta**2) * precision * recall
        / (beta**2 * precision + recall)
        if (precision + recall) > 0 else 0.0
    )
    return precision, recall, f1
```

### BERTScore Implementation

```python
import torch
import torch.nn.functional as F
from transformers import AutoTokenizer, AutoModel
from typing import List, Tuple
import numpy as np


def bertscore(
    candidates: List[str],
    references: List[str],
    model_name: str = "microsoft/deberta-xlarge-mnli",  # recommended model
    rescale_with_baseline: bool = True,
    device: str = "cpu",
) -> Tuple[List[float], List[float], List[float]]:
    """
    Compute BERTScore precision, recall, F1.
    Uses contextual embeddings from BERT-family model.

    rescale_with_baseline: rescales scores to [0, 1] range using
    baseline values from human evaluation correlation studies.
    Without rescaling, F1 scores cluster in [0.85, 0.95] making
    differences hard to interpret.

    Recommended model: microsoft/deberta-xlarge-mnli or
    bert-base-uncased (faster, slightly lower correlation with human judgment).
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModel.from_pretrained(model_name).to(device)
    model.eval()

    precisions, recalls, f1s = [], [], []

    with torch.no_grad():
        for cand, ref in zip(candidates, references):
            # Encode candidate
            cand_inputs = tokenizer(cand, return_tensors="pt",
                                    max_length=512, truncation=True).to(device)
            cand_emb = model(**cand_inputs).last_hidden_state[0]  # (c_len, hidden)

            # Encode reference
            ref_inputs = tokenizer(ref, return_tensors="pt",
                                   max_length=512, truncation=True).to(device)
            ref_emb = model(**ref_inputs).last_hidden_state[0]   # (r_len, hidden)

            # L2 normalize for cosine similarity
            cand_emb = F.normalize(cand_emb, p=2, dim=-1)
            ref_emb = F.normalize(ref_emb, p=2, dim=-1)

            # Similarity matrix: (c_len, r_len)
            sim_matrix = torch.matmul(cand_emb, ref_emb.T)

            # Precision: for each candidate token, max over reference tokens
            p = sim_matrix.max(dim=1).values.mean().item()
            # Recall: for each reference token, max over candidate tokens
            r = sim_matrix.max(dim=0).values.mean().item()
            # F1
            f = 2 * p * r / (p + r) if (p + r) > 0 else 0.0

            precisions.append(p)
            recalls.append(r)
            f1s.append(f)

    return precisions, recalls, f1s
```

### NER Entity-Level F1 (seqeval-style)

```python
from typing import List, Set, Tuple


def extract_entities(
    bio_labels: List[str],
) -> Set[Tuple[str, int, int]]:
    """
    Extract (entity_type, start, end) tuples from BIO label sequence.
    Handles edge cases: consecutive entities of the same type.
    """
    entities = set()
    start = None
    entity_type = None

    for i, label in enumerate(bio_labels):
        if label.startswith("B-"):
            if start is not None:
                entities.add((entity_type, start, i))
            start = i
            entity_type = label[2:]
        elif label.startswith("I-"):
            if start is None or label[2:] != entity_type:
                # Invalid I- without preceding B- or type mismatch
                if start is not None:
                    entities.add((entity_type, start, i))
                start = None
                entity_type = None
        else:  # O label
            if start is not None:
                entities.add((entity_type, start, i))
                start = None
                entity_type = None

    if start is not None:
        entities.add((entity_type, start, len(bio_labels)))

    return entities


def entity_level_f1(
    true_labels: List[List[str]],   # list of BIO label sequences
    pred_labels: List[List[str]],   # list of predicted BIO label sequences
) -> Tuple[float, float, float]:
    """
    Compute entity-level precision, recall, F1.

    Entity-level: BOTH span boundaries AND entity type must be correct.
    This is stricter than token-level F1 and is the correct metric for NER.

    Returns (precision, recall, f1).
    """
    tp = 0
    fp = 0
    fn = 0

    for true_seq, pred_seq in zip(true_labels, pred_labels):
        true_entities = extract_entities(true_seq)
        pred_entities = extract_entities(pred_seq)

        # True positives: in both true and pred
        tp += len(true_entities & pred_entities)
        # False positives: in pred but not in true
        fp += len(pred_entities - true_entities)
        # False negatives: in true but not in pred
        fn += len(true_entities - pred_entities)

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

    return precision, recall, f1
```

### ECE and Calibration

```python
import numpy as np
from typing import List


def expected_calibration_error(
    confidences: List[float],   # Model's predicted probability for the chosen class
    correctness: List[int],     # 1 if prediction was correct, 0 otherwise
    num_bins: int = 10,
) -> float:
    """
    Expected Calibration Error (Naeini et al., 2015).

    ECE = sum_b (|B_b| / n) * |acc(B_b) - conf(B_b)|

    Perfect calibration: ECE = 0.
    Well-calibrated NLP classifier: ECE < 0.05.
    Overconfident BERT fine-tuned without calibration: ECE ~ 0.15-0.25.
    """
    confidences = np.array(confidences)
    correctness = np.array(correctness)
    n = len(confidences)

    bin_edges = np.linspace(0, 1, num_bins + 1)
    ece = 0.0

    for i in range(num_bins):
        low, high = bin_edges[i], bin_edges[i + 1]
        in_bin = (confidences >= low) & (confidences < high)

        if in_bin.sum() == 0:
            continue

        bin_accuracy = correctness[in_bin].mean()
        bin_confidence = confidences[in_bin].mean()
        bin_weight = in_bin.sum() / n

        ece += bin_weight * abs(bin_accuracy - bin_confidence)

    return ece


def temperature_scale_calibration(
    logits: np.ndarray,   # (n_samples, n_classes) pre-softmax logits
    labels: np.ndarray,   # (n_samples,) true class indices
    val_fraction: float = 0.1,
) -> float:
    """
    Temperature scaling calibration (Guo et al., 2017).
    A single scalar T scales all logits before softmax.
    T > 1: soften (underconfident), T < 1: sharpen (overconfident).
    """
    import torch
    import torch.nn.functional as F
    from scipy.optimize import minimize_scalar

    n_val = max(1, int(len(logits) * val_fraction))
    val_logits = torch.tensor(logits[-n_val:], dtype=torch.float32)
    val_labels = torch.tensor(labels[-n_val:], dtype=torch.long)

    def nll(temperature: float) -> float:
        scaled = val_logits / max(temperature, 1e-6)
        loss = F.cross_entropy(scaled, val_labels).item()
        return loss

    result = minimize_scalar(nll, bounds=(0.1, 10.0), method="bounded")
    return result.x  # Optimal temperature
```

### Text Augmentation

```python
import random
import re
from typing import List, Tuple


def eda_augment(
    text: str,
    labels: List[str] | None = None,  # BIO labels for NER (None for classification)
    alpha_sr: float = 0.1,    # fraction of words to replace with synonyms
    alpha_ri: float = 0.1,    # fraction of words to randomly insert
    alpha_rs: float = 0.1,    # fraction of word pairs to randomly swap
    alpha_rd: float = 0.1,    # fraction of words to randomly delete
    n_aug: int = 4,            # number of augmented examples to generate
) -> List[str]:
    """
    EDA: Easy Data Augmentation (Wei & Zou, 2019).
    Four operations: synonym replacement, random insertion, random swap, random delete.
    Effective for text classification with <500K examples.
    Modest gain: ~1-2% accuracy improvement on small datasets.

    WARNING: Do NOT use with NLTK synonym replacement for NER — replacing entity
    names with synonyms corrupts BIO labels. Use only for the non-entity context.
    """
    import nltk
    from nltk.corpus import wordnet

    words = text.split()
    n = len(words)
    augmented = []

    for _ in range(n_aug):
        aug_words = words.copy()

        # Synonym replacement
        n_sr = max(1, int(alpha_sr * n))
        replace_indices = random.sample(range(n), min(n_sr, n))
        for idx in replace_indices:
            synsets = wordnet.synsets(aug_words[idx])
            if synsets:
                synonyms = [lemma.name() for lemma in synsets[0].lemmas()
                            if lemma.name() != aug_words[idx]]
                if synonyms:
                    aug_words[idx] = random.choice(synonyms).replace("_", " ")

        # Random insertion (insert a synonym of a random word at a random position)
        n_ri = max(1, int(alpha_ri * n))
        for _ in range(n_ri):
            random_word = random.choice(words)
            synsets = wordnet.synsets(random_word)
            if synsets:
                synonyms = [lemma.name() for lemma in synsets[0].lemmas()]
                if synonyms:
                    insert_pos = random.randint(0, len(aug_words))
                    aug_words.insert(insert_pos, random.choice(synonyms))

        # Random swap
        n_rs = max(1, int(alpha_rs * n))
        for _ in range(n_rs):
            if len(aug_words) >= 2:
                i, j = random.sample(range(len(aug_words)), 2)
                aug_words[i], aug_words[j] = aug_words[j], aug_words[i]

        # Random deletion
        n_rd = max(1, int(alpha_rd * n))
        aug_words = [w for w in aug_words if random.random() > alpha_rd]

        augmented.append(" ".join(aug_words))

    return augmented


def back_translation_augment(
    texts: List[str],
    intermediate_language: str = "de",  # German is commonly used
) -> List[str]:
    """
    Back-translation via HuggingFace Helsinki-NLP models.
    Translate en->de->en to generate paraphrases.
    Higher quality than synonym replacement; requires inference.
    Typical improvement: 1-3% F1 on small NLP datasets.
    """
    from transformers import MarianMTModel, MarianTokenizer

    # Load en->de and de->en models
    en_de_model_name = "Helsinki-NLP/opus-mt-en-de"
    de_en_model_name = "Helsinki-NLP/opus-mt-de-en"

    en_de_tokenizer = MarianTokenizer.from_pretrained(en_de_model_name)
    en_de_model = MarianMTModel.from_pretrained(en_de_model_name)
    de_en_tokenizer = MarianTokenizer.from_pretrained(de_en_model_name)
    de_en_model = MarianMTModel.from_pretrained(de_en_model_name)

    def translate_batch(texts_batch, tokenizer, model) -> List[str]:
        inputs = tokenizer(texts_batch, return_tensors="pt", padding=True,
                           truncation=True, max_length=512)
        translated = model.generate(**inputs, num_beams=4)
        return [tokenizer.decode(t, skip_special_tokens=True) for t in translated]

    # en -> de
    german_texts = translate_batch(texts, en_de_tokenizer, en_de_model)
    # de -> en (paraphrase)
    back_translated = translate_batch(german_texts, de_en_tokenizer, de_en_model)
    return back_translated
```

---

## 7. Real-World Examples

**WMT Translation Competition:** BLEU is the primary competition metric for machine translation (WMT 2014-2023). Models are ranked by tokenized BLEU on newstest datasets. However, SacreBLEU (Post, 2018) standardized BLEU computation after years of incomparable results from different tokenization choices. Lesson: use `sacrebleu` library, not custom BLEU implementations.

**CNN/DailyMail Summarization Benchmark:** ROUGE-1, ROUGE-2, ROUGE-L are the standard evaluation metrics. Models are compared on these three numbers. A common failure: models optimize ROUGE-2 by copying long n-grams verbatim from the source, achieving high ROUGE but failing at abstractive understanding. BERTScore and factual consistency metrics are increasingly used alongside ROUGE to catch this.

**SQuAD Question Answering:** Uses Exact Match (EM) and token-level F1 simultaneously. EM requires character-perfect match after normalization (lowercase, strip punctuation, articles). F1 measures token overlap between predicted and gold answer spans. On SQuAD 2.0, a model may score EM=65% but F1=78% because many predictions have the right span boundary off by one word.

**CoNLL-2003 NER Benchmark:** The gold standard NER benchmark reports entity-level F1. BERT-base achieves 91.1 F1; DeBERTa-v3-large achieves 93.0. The 2-point gap matters for production because each false negative entity is a missed extraction. In financial document processing with 100K entities/day, a 2% improvement prevents 2,000 missed entities.

**Google's production calibration:** Google's SafeSearch and content moderation classifiers use temperature scaling to calibrate output probabilities. Without calibration, BERT fine-tuned models show ECE of 0.15-0.25 (predictions of 90% confidence are only correct 70-80% of the time). After temperature scaling, ECE drops to <0.03.

---

## 8. Tradeoffs

| Metric | Correlation with Human Judgment | Computational Cost | Reproducibility |
|--------|--------------------------------|-------------------|-----------------|
| BLEU | ~0.5 Pearson on sentence level, ~0.8 corpus level | O(n) | High (sacrebleu) |
| ROUGE-L | ~0.6 on summarization | O(n²) for LCS | High |
| METEOR | ~0.7 (best traditional metric) | O(n × vocab) | Medium (language resources needed) |
| BERTScore | ~0.75 (best for paraphrase tasks) | O(n × model forward) | Medium (model version matters) |
| Human evaluation | 1.0 | Very expensive ($20-50K for 1K examples) | Low (annotator disagreement) |

| BLEU vs ROUGE for Task Selection | |
|-----------------------------------|--|
| Machine translation | BLEU (precision matters: don't add wrong words) |
| Summarization | ROUGE (recall matters: cover all key information) |
| Image captioning | BLEU-4 (standard benchmark, precision-oriented) |
| Dialogue response | Neither is ideal; use BERTScore or human eval |

| Perplexity Correlation | |
|------------------------|--|
| Pre-training evaluation | Good correlation: lower perplexity = better language model |
| Fine-tuned model evaluation | Poor correlation: fine-tuned models optimize task loss, not MLM |
| Cross-model comparison | Only valid when models use the same tokenizer and vocabulary |

---

## 9. When to Use / When NOT to Use

### Use BLEU when:

- Evaluating machine translation (established benchmark; comparability matters)
- Output should be concise and precise (precision-oriented task)
- Multiple references are available (BLEU handles multiple references correctly)
- Comparing systems rather than judging absolute quality

### Use ROUGE when:

- Evaluating text summarization
- Recall of key content matters (coverage task)
- Rapid iteration — ROUGE is faster than BERTScore

### Use BERTScore when:

- Paraphrase quality matters (the model generates valid restatements of references)
- Comparing outputs where surface form varies significantly
- Reference text is in a different style from expected output

### Use entity-level F1 when:

- Evaluating any sequence labeling task (NER, POS, chunking)
- Never use token-level accuracy for NER — the O class dominance makes it meaningless

### Use ECE/reliability diagrams when:

- Model confidence scores are used for business decisions (not just argmax)
- Alert thresholds are calibrated to confidence levels
- Comparing classifiers where the decision boundary is adjustable

### Do NOT use BLEU when:

- Evaluating dialogue (responses have high valid variation, BLEU severely penalizes)
- Evaluating code (BLEU is for natural language; use pass@k or CodeBLEU)
- Evaluating single-sentence outputs (sentence-level BLEU is unreliable; use BERTScore)

### Do NOT use perplexity to compare:

- Models with different tokenizers (perplexity depends on the tokenization)
- Fine-tuned vs pretrained models (different objectives)

---

## 10. Common Pitfalls

### Pitfall 1: Sentence-level BLEU for translation evaluation

```python
# BROKEN: Sentence-level BLEU has high variance and is unreliable
from nltk.translate.bleu_score import sentence_bleu
scores = [sentence_bleu([ref.split()], cand.split()) for cand, ref in pairs]
avg_bleu = sum(scores) / len(scores)   # Do NOT do this

# FIXED: Always use corpus-level BLEU (aggregate n-gram statistics)
import sacrebleu
# sacrebleu computes corpus-level BLEU by default
result = sacrebleu.corpus_bleu(candidates, [references])
print(result.score)  # 0-100 scale (multiply our 0-1 result by 100)
```

### Pitfall 2: ROUGE gaming by extractive copying

```python
# SYMPTOM: Model achieves ROUGE-2 = 0.42 on summarization but summaries are
# verbatim copies of source sentences. "Summarization" task, but no abstraction.

# DETECTION: Check abstractiveness
def abstractiveness_ratio(summary: str, source: str) -> float:
    """Fraction of summary n-grams NOT in source (higher = more abstractive)."""
    source_bigrams = set(zip(source.split(), source.split()[1:]))
    summary_bigrams = set(zip(summary.split(), summary.split()[1:]))
    if not summary_bigrams:
        return 0.0
    novel = summary_bigrams - source_bigrams
    return len(novel) / len(summary_bigrams)

# ALSO EVALUATE with BERTScore and factual consistency
# BERTScore penalizes extractive-looking outputs less than ROUGE
# Factual consistency: use NLI model to check if summary entails source
```

### Pitfall 3: Token-level F1 for NER instead of entity-level

```python
# BROKEN: sklearn classification_report for NER
from sklearn.metrics import classification_report
# This gives per-token metrics, inflated by 'O' class
report = classification_report(
    [label for seq in true_labels for label in seq],
    [label for seq in pred_labels for label in seq],
)
# Might show F1=0.95, but entity-level F1 could be 0.72

# FIXED: seqeval for entity-level F1
from seqeval.metrics import classification_report as seq_report, f1_score

entity_f1 = f1_score(true_labels, pred_labels)
print(seq_report(true_labels, pred_labels))
# Shows per-entity-type precision, recall, F1
# Correctly penalizes boundary errors
```

### Pitfall 4: BERTScore model version instability

```python
# BROKEN: Different BERTScore results on different runs due to model version
# BERTScore is sensitive to the underlying BERT model's version

# BERTScore(candidate, reference, model="bert-base-uncased") might give 0.89
# BERTScore(candidate, reference, model="roberta-large") might give 0.92
# These are NOT comparable — always fix the model and version

# FIXED: Always specify model AND hash for reproducibility
from bert_score import BERTScorer
scorer = BERTScorer(
    model_type="microsoft/deberta-xlarge-mnli",  # Best correlation with human judgment
    lang="en",
    rescale_with_baseline=True,
    use_fast_tokenizer=True,
)
P, R, F1 = scorer.score(candidates, references)
# Report model name alongside scores: "BERTScore-F1: 0.87 (DeBERTa-xlarge-mnli, rescaled)"
```

### Pitfall 5: Comparing perplexity across models with different tokenizers

```python
# BROKEN: GPT-2 perplexity vs LLaMA-2 perplexity on the same text
# GPT-2 uses 50K BPE vocabulary; LLaMA-2 uses 32K SentencePiece
# Same text might have 100 GPT-2 tokens vs 80 LLaMA tokens
# Perplexity is computed per-token, so these are incomparable

# GPT-2 perplexity: 35  ← meaningless to compare to:
# LLaMA perplexity: 12  ← different tokenization

# FIXED: Only compare perplexity between models with identical tokenization
# OR normalize to characters/words:
def bits_per_character(
    log_likelihood: float,   # Total log-likelihood on corpus
    n_chars: int,            # Total characters in corpus
) -> float:
    """BPC is comparable across different tokenization schemes."""
    return -log_likelihood / (n_chars * math.log(2))
```

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `sacrebleu` | BLEU computation | Standard, reproducible; use this, not NLTK bleu |
| `rouge_score` (Google) | ROUGE computation | `pip install rouge-score`; fast, pure Python |
| `bert_score` | BERTScore | `pip install bert-score`; use with `rescale_with_baseline=True` |
| `seqeval` | NER/sequence labeling metrics | Entity-level F1, per-type breakdown |
| `nltk` (WordNet) | EDA synonym replacement | Requires `nltk.download('wordnet')` |
| `sentence-transformers` | Semantic similarity evaluation | STS benchmark evaluation |
| `calibration-library` | ECE, reliability diagrams | Post-hoc calibration tools |
| `evaluate` (HuggingFace) | Unified metric computation | Wraps BLEU, ROUGE, BERTScore, seqeval |
| `checklist` (Ribeiro et al.) | Behavioral testing for NLP | Tests capability slices beyond aggregate metrics |

---

## 12. Interview Questions with Answers

**Q: Why is BLEU a precision metric and ROUGE a recall metric?**
BLEU computes how much of the candidate (generated) text consists of n-grams found in the reference(s). The numerator is overlap with reference; the denominator is candidate length. Precision measures quality of what was generated: "did the model say correct things?" ROUGE computes how much of the reference text's n-grams appear in the candidate. The denominator is reference length. Recall measures coverage of important content: "did the model cover everything important?" For translation, precision is more important — adding wrong words is problematic. For summarization, recall is more important — missing key information fails the user.

**Q: What is the brevity penalty in BLEU and why is it necessary?**
The brevity penalty (BP = exp(1 - reference_len / candidate_len) when candidate is shorter) penalizes outputs shorter than the reference. Without it, a model could achieve 100% precision by generating a single word that appears in the reference ("the") — one word, always in reference, precision = 1.0. The brevity penalty assigns BP = exp(1 - 6/1) ≈ 0.007 to a single-word candidate against a 6-word reference, reducing the BLEU score to near zero. In practice, BP activates when candidates are systematically shorter than references, which indicates the model generates truncated or incomplete outputs.

**Q: Explain BERTScore and when it is more informative than BLEU.**
BERTScore (Zhang et al., 2020) computes the similarity between candidate and reference using contextual embeddings: for each candidate token, find the maximum cosine similarity to any reference token (precision); for each reference token, find the maximum cosine similarity to any candidate token (recall); combine as F1. The contextual embeddings mean "feline" and "cat" have high similarity (~0.87 in BERT space), so BERTScore gives partial credit for valid paraphrases that BLEU scores as zero. BERTScore is more informative than BLEU in three cases: (1) dialogue evaluation, where responses have high valid variation; (2) creative text generation, where exact n-gram matches are unlikely; (3) cross-lingual evaluation, where multilingual BERT can compare translations of different quality independent of exact word overlap.

**Q: Why does perplexity correlate poorly with downstream task performance for fine-tuned models?**
Perplexity measures how well the model assigns probability to held-out text under its generative distribution (lower perplexity = model assigns higher probability = better language model). Fine-tuning optimizes a task-specific objective (cross-entropy for classification, token-level loss for NER), not next-token prediction. A model fine-tuned on NER data may update weights in ways that improve entity boundary detection (task objective) while degrading next-token prediction (MLM objective). The two objectives are not aligned. Empirically: BERT fine-tuned for NER for too many epochs shows decreasing MLM perplexity (lower) but peak NER F1 occurs earlier and then drops. Always evaluate fine-tuned models on the task metric directly, not perplexity.

**Q: What is the difference between entity-level F1 and token-level F1 for NER?**
Token-level F1 treats each token as an independent classification problem: was the label O, B-PER, I-PER, etc. predicted correctly? The 'O' class dominates (typically 85-95% of tokens), so even a model predicting all-O achieves 85%+ token accuracy. Entity-level F1 requires that both the entity span (start and end positions) AND the entity type are exactly correct. "Steve Jobs" as a PERSON entity means both "Steve" (B-PER) and "Jobs" (I-PER) must be predicted, at the exact positions, with the exact type. A prediction of "Steve" as PER but "Jobs" as O counts as finding a different (incorrect) entity. This strictness is appropriate because downstream NLP pipelines consume full entity strings, not individual tokens.

**Q: How do you evaluate a text summarization model in production beyond ROUGE?**
ROUGE is the standard offline metric but fails to detect factual inconsistencies and extractive copying. Three production-ready additions: (1) FactCC / DAE (factual consistency evaluation): use an NLI model fine-tuned on (article, claim) pairs to classify whether each sentence in the summary is entailed by the source. Summaries containing hallucinated facts score low. (2) Abstractiveness ratio: fraction of summary bigrams not present in the source — high score indicates genuine abstraction, low score indicates copying. (3) Length distribution: check that summary length distribution matches expected (e.g., target 100±20 words). In A/B testing, user "read time" on recommended articles and "story completion" rates correlate with summary quality better than ROUGE for news summarization.

**Q: What is Expected Calibration Error and when does it matter?**
ECE measures whether confidence scores reflect actual accuracy: if the model says "90% confident," it should be correct 90% of the time. Compute ECE by binning predictions by confidence (10 bins from 0-0.1 to 0.9-1.0), computing the absolute difference between mean confidence and mean accuracy per bin, then taking a weighted average by bin size. ECE=0 means perfect calibration; ECE=0.1 means confidence is systematically 10% off. ECE matters when: (1) confidence scores drive business decisions (show result only if P>0.8); (2) alert thresholds are based on model confidence (escalate to human if P<0.7); (3) combining multiple classifiers using their probability scores. BERT fine-tuned without regularization typically has ECE=0.15-0.25. Temperature scaling (single scalar T applied to logits before softmax) reduces ECE to <0.03 with zero retraining.

**Q: What is back-translation augmentation and what are its limitations?**
Back-translation generates paraphrases by translating text to an intermediate language and back. "The cat sat on the mat" → (en→de) "Die Katze saß auf der Matte" → (de→en) "The cat was sitting on the rug" — a valid paraphrase preserving the label. Advantages: creates semantically equivalent but lexically diverse examples; no hand-crafted synonym lists needed; works for any language pair. Limitations: (1) label-sensitive content may change during translation — for NER, entity mentions can be translated/transliterated incorrectly; (2) translation quality bottleneck — low-quality intermediate translations produce noisy augmented data; (3) computational cost: requires two translation model inferences per example; (4) limited diversity: German→English back-translation produces German-influenced English patterns, may introduce translation artifacts. Best results: use 2-3 different intermediate languages to increase diversity; filter augmented examples with a quality model.

**Q: How would you evaluate a named entity recognition system before production deployment?**
Five evaluation checks: (1) Entity-level F1 overall and per entity type — report separately for PER, ORG, LOC, MISC; a model with 88% overall F1 might have 50% F1 on MISC entities, which is unacceptable for that category. (2) Boundary error analysis: how often is the entity type correct but boundaries are off by one token? Distinguish "nearly right" from "completely wrong" predictions. (3) OOV entity performance: evaluate separately on entities that don't appear in training data — this exposes the model's generalization. (4) Error breakdown: false positives (entities predicted but not in ground truth) vs false negatives (entities in ground truth but not predicted). For production, false negatives may matter more (missing a person name in a contract). (5) Latency profile: entity-level F1 at various sequence lengths to ensure performance does not degrade on long documents.

**Q: Derive METEOR's scoring function and explain why it outperforms BLEU on sentence level.**
METEOR (Denkowski & Lavie, 2014) computes: (1) Find the best alignment between candidate and reference, using exact match, then stemmed match, then synonym match (via WordNet). (2) Compute unigram precision P = matched / len(candidate) and recall R = matched / len(reference). (3) Combine as F_alpha = P × R / (alpha × P + (1-alpha) × R) with alpha=0.9 (strongly recall-weighted). (4) Penalize fragmented alignments: penalty = gamma × (# of contiguous matched chunks / # matched unigrams) ^ beta, where gamma=0.5, beta=3. Fewer chunks (more contiguous alignment) = lower penalty. Final score = F_alpha × (1 - penalty). METEOR outperforms BLEU on sentence level because: (1) it handles stemming and synonyms explicitly (BLEU does not); (2) it combines precision and recall at the word level rather than n-gram level, giving meaningful scores even for short candidates; (3) the brevity penalty in BLEU is coarse; METEOR's recall component directly penalizes uncovering reference content.

**Q: How do you set up a reliable offline evaluation pipeline for NLP?**
Five practices: (1) Freeze a test set before development begins — any data viewed during development becomes implicitly part of training. (2) Use statistical significance testing: McNemar's test for classification, bootstrap resampling for generation metrics — never report metric differences without significance tests (n=100 examples, 0.5 point BLEU difference is not significant). (3) Multi-metric reporting: for translation, report BLEU + BERTScore + chrF; for NER, report per-type entity-level F1; do not reduce to a single number when multiple aspects matter. (4) Error analysis on at least 100 failures: manually inspect model errors to identify systematic failure modes (e.g., long-tail entity types, negation handling, domain-specific vocabulary). (5) Slice-based evaluation: report metrics separately on easy/medium/hard examples — a model with identical average F1 to a baseline may be failing on hard cases that matter more in production.

**Q: What are the limitations of automatic NLP metrics and when should you use human evaluation?**
Automatic metrics have three systematic limitations: (1) Reference bias — metrics assume the reference text is the gold standard, but in creative tasks (dialogue, story generation) there are many valid outputs; high-quality responses dissimilar from the reference are penalized. (2) Metric gaming — models optimized to maximize BLEU/ROUGE produce text that scores high on these metrics but reads poorly (repetitive, generic, over-extractive). (3) Task mismatch — ROUGE was designed for single-document summarization but is applied to dialogue (wrong), question answering (wrong), and image captioning (wrong convention). Use human evaluation when: (1) the task is creative or open-ended (dialogue, story, creative writing); (2) you are making a deployment decision that affects many users; (3) automatic metrics have plateaued and you need to distinguish between close systems; (4) a production incident has occurred and you need to understand the actual quality degradation. Human evaluation cost: $0.05-0.50 per judgment via crowdsourcing, $5-50 via expert annotators.

**Q: How does temperature scaling calibration work and why is it effective for BERT fine-tuned models?**
Temperature scaling (Guo et al., 2017) is a post-hoc calibration method: after training, find a single scalar temperature T that minimizes negative log-likelihood on a validation set. At inference, scale logits by 1/T before softmax. If T > 1: logits are scaled down → probabilities become more uniform (underconfident) → corrects overconfident models. If T < 1: probabilities become more peaked (overconfident) → corrects underconfident models. BERT fine-tuned models are typically overconfident (T>1 needed): the cross-entropy training objective encourages the model to push probability mass to the correct class, often resulting in 95%+ confidence predictions that are only 80-85% accurate. Temperature scaling is effective because: (1) it has only one parameter (no overfitting risk); (2) it does not change the argmax prediction (accuracy unchanged); (3) it can be computed in seconds on a small validation set; (4) it consistently reduces ECE from ~0.15-0.25 to <0.03 across BERT-family models. Limitation: it applies a global calibration and cannot fix per-class calibration differences.

**Q: What is the extractive oracle score for summarization and how do you use it?**
The extractive oracle finds the subset of source sentences that maximizes ROUGE-2 against the reference summary. For CNN/DailyMail: pick the top 3 source sentences by greedy ROUGE-2 maximization. This gives the upper bound of any extractive summarization system on this dataset. Oracle ROUGE-2 is typically ~17-18 on CNN/DailyMail; abstractive models achieve ~22-24 (above oracle), confirming abstraction is needed. Use the oracle to: (1) understand whether your task requires abstraction (if oracle ≥ reference, extractive approach suffices); (2) diagnose model failures — if a model fails to beat oracle, the abstraction mechanism is broken; (3) as a strong baseline (extractive oracle is hard to beat on informativeness, though it can be verbose). Computing oracle: `from rouge_score import rouge_scorer; scorer = rouge_scorer.RougeScorer(['rouge2']); greedy select sentences maximizing marginal ROUGE-2 gain`.

---

## 13. Best Practices

1. Always use corpus-level BLEU computed with `sacrebleu` — sentence-level BLEU has high variance and custom implementations produce incomparable results.
2. Report ROUGE-1, ROUGE-2, and ROUGE-L together for summarization — different systems may excel on different n-gram granularities.
3. Include BERTScore F1 (DeBERTa-xlarge, rescaled) alongside ROUGE for any generation task where paraphrase quality matters.
4. Never report NER results without specifying entity-level vs token-level — they are not comparable and entity-level is always the correct metric.
5. Split entity-level F1 by entity type in every NER evaluation report — overall F1 hides failures on rare entity types that matter in production.
6. Calibrate production classifiers with temperature scaling before deployment — run on a held-out validation set, apply the learned T at inference; reduces ECE from ~0.2 to <0.03 in 10 minutes.
7. When using EDA augmentation, keep alpha values low (0.05-0.1) for NER and sequence labeling — aggressive augmentation corrupts label alignment.
8. Back-translation augmentation improves the most on small datasets (<5K examples) and diverse domains; diminishing returns above 10K labeled examples where data quality matters more than quantity.
9. For QA evaluation (SQuAD-style), report both EM and F1 — a model with high F1 but low EM is extracting the right region but not exactly; useful for debugging span boundary prediction.
10. When comparing two NLP systems, use paired bootstrap resampling to compute statistical significance — always report p-values alongside metric differences, especially for small test sets (<500 examples).

---

## 14. Case Study

### Problem: Evaluating a Multi-Task NLP Pipeline for Legal Document Processing

**Context:** A system processes legal contracts with three tasks: (1) clause-level sentiment classification (favorable/neutral/unfavorable), (2) named entity recognition (PARTY, DATE, AMOUNT, JURISDICTION), (3) extractive clause summarization.

**Evaluation framework design:**

```python
# Task 1: Classification — Macro-F1 (imbalanced: 30% favorable, 50% neutral, 20% unfavorable)
from sklearn.metrics import f1_score
macro_f1 = f1_score(true_labels, pred_labels, average="macro")
# Also compute ECE on production distribution
ece = expected_calibration_error(pred_confidences, correctness)

# Task 2: NER — Per-type entity-level F1
from seqeval.metrics import classification_report
report = classification_report(true_labels, pred_labels)
# Report separate F1 for: PARTY, DATE, AMOUNT, JURISDICTION
# Production alert: if any entity type drops below F1=0.80, page on-call

# Task 3: Summarization — ROUGE-2 + BERTScore + Abstractiveness
from rouge_score import rouge_scorer
scorer = rouge_scorer.RougeScorer(['rouge2', 'rougeL'])
rouge_scores = [scorer.score(ref, cand) for ref, cand in pairs]

from bert_score import BERTScorer
bs = BERTScorer(model_type="microsoft/deberta-xlarge-mnli", rescale_with_baseline=True)
P, R, F1_bert = bs.score(candidates, references)

abstract_ratios = [abstractiveness_ratio(s, src) for s, src in zip(summaries, sources)]
```

**Results and actions taken:**

| Task | Metric | Initial | After Fix |
|------|--------|---------|-----------|
| Classification | Macro-F1 | 0.74 | 0.86 |
| Classification | ECE | 0.19 | 0.03 |
| NER: PARTY | Entity F1 | 0.91 | 0.93 |
| NER: DATE | Entity F1 | 0.89 | 0.90 |
| NER: AMOUNT | Entity F1 | 0.73 | 0.84 |
| NER: JURISDICTION | Entity F1 | 0.58 | 0.79 |
| Summarization | ROUGE-2 | 0.38 | 0.41 |
| Summarization | BERTScore-F1 | 0.82 | 0.86 |
| Summarization | Abstractiveness | 0.12 | 0.31 |

**Root causes and fixes:**

1. **Classification ECE=0.19**: Temperature scaling (T=1.8) reduced ECE to 0.03. Required: 200 calibration examples, 5 minutes.

2. **NER AMOUNT F1=0.73**: Currency amounts mixed with percentages and non-monetary numbers. Root cause: training data labeling inconsistency — "$1M revenue" was labeled AMOUNT, "1% interest rate" was sometimes AMOUNT and sometimes not. Fix: clarify annotation guidelines, re-label 300 ambiguous examples, retrain.

3. **NER JURISDICTION F1=0.58**: Jurisdictions specified as state abbreviations ("NY law applies") were missed — model was trained primarily on full-name jurisdictions. Fix: augmented with 500 abbreviation examples using template-based augmentation: "[STATE_ABBR] law applies" + full name variants.

4. **Summarization abstractiveness=0.12**: ROUGE optimization led to extractive copying. Fix: added coverage penalty to training + ROUGE-L recall weighting. BERTScore increased more than ROUGE-2 (0.04 vs 0.03), confirming the extractive copying was masking semantic quality.
