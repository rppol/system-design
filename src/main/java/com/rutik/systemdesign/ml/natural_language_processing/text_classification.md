# Text Classification

> This file is a deep-dive sub-file of the [Natural Language Processing](README.md) module.
> It covers the classifier side of NLP: task framing, feature representations, the classical linear
> baselines (Naive Bayes, logistic regression, linear SVM, fastText), the first neural text model
> (TextCNN), class imbalance, and multi-label strategies.
> For the algorithm math (NB derivation, logistic/SVM optimization) see
> [../supervised_learning/README.md](../supervised_learning/README.md),
> [../supervised_learning/linear_models.md](../supervised_learning/linear_models.md), and
> [../supervised_learning/bayesian_methods.md](../supervised_learning/bayesian_methods.md).
> For encoder fine-tuning see [bert_and_pretrained_models.md](bert_and_pretrained_models.md).
> For the production system (serving, cascade, active learning) see
> [../case_studies/design_nlp_classification_pipeline.md](../case_studies/design_nlp_classification_pipeline.md).

---

## 1. Concept Overview

Text classification assigns one or more predefined labels to a piece of text: spam vs ham, a support ticket's intent, a product review's sentiment, a news article's topic, a comment's toxicity types. It is the single most common NLP task in production — content moderation, ticket routing, document tagging, compliance screening, and product categorization are all text classification underneath.

The task decomposes into two decisions that this file treats as separable: **how you turn text into numbers** (feature representation: bag-of-words counts, TF-IDF weights, dense embeddings, or contextual embeddings) and **which classifier consumes those numbers** (Naive Bayes, logistic regression, linear SVM, fastText, TextCNN, or a fine-tuned encoder). The central engineering lesson, echoed in the [system-design case study](../case_studies/design_nlp_classification_pipeline.md), is that most text-classification problems do **not** need BERT: a TF-IDF + linear model baseline reaches within 5–15% of a fine-tuned transformer at a fraction of the training cost, serving latency, and operational complexity. You earn the right to fine-tune BERT by first proving a linear baseline is not good enough.

Text is the domain where the "naive" independence assumption of Naive Bayes and the linear-kernel-wins property of SVMs both hold up unusually well, because bag-of-words features are extremely high-dimensional (tens of thousands of vocabulary terms) and sparse (a document touches a few hundred of them). Understanding *why* linear models thrive in that regime is the intuition this file builds before layering on neural models.

---

## 2. Intuition

**One-line analogy:** A text classifier is a spam-scoring machine that adds up "guilt points" — each word contributes a learned weight toward or against a class, and the sum crosses a threshold.

**Mental model:** Picture a document as a giant sparse checklist over a 30,000-word vocabulary — mostly zeros, with a spike wherever a word appears. A linear classifier is a single learned weight per checkbox: `free` +2.1 toward spam, `meeting` −1.4, `viagra` +5.7. Score a document by summing the weights of the checked boxes. Naive Bayes learns those weights from per-class word frequencies (a generative story: "how would a spam author generate words?"); logistic regression and SVM learn them by directly optimizing the decision boundary (a discriminative story: "what weights best separate spam from ham?").

**Why it matters:** In a 30,000-dimensional sparse space, classes are almost always linearly separable — there is enough room to draw a flat hyperplane between them — so the extra capacity of a nonlinear kernel or a deep network buys little and overfits easily. This is why a `LinearSVC` beats an RBF-kernel SVM on text, and why a 40-line TF-IDF pipeline is the correct first move.

**Key insight:** The bag-of-words assumption throws away word *order* ("dog bites man" == "man bites dog"), yet works because word *identity* carries most of the topical signal. Order matters exactly where BoW fails — negation ("not good"), sarcasm, and syntax — and that gap is precisely what n-grams (fastText), convolution windows (TextCNN), and self-attention (BERT) exist to close.

---

## 3. Core Principles

**Generative vs discriminative.** Naive Bayes is *generative*: it models `P(x | y)` and `P(y)`, then applies Bayes' rule to rank classes by `P(y | x)`. Logistic regression, SVM, and neural nets are *discriminative*: they model `P(y | x)` (or a decision boundary) directly. Ng & Jordan (2002) showed generative NB has higher asymptotic error but reaches it with far fewer examples, so **NB wins at small data, discriminative models win at large data** — a crossover you can see in a learning curve.

**High dimensionality favors linear models.** Bag-of-words puts a document in `R^|V|` with `|V|` = 10K–1M. In such high dimensions, data is almost always linearly separable (Cover's theorem intuition), so a linear decision boundary suffices; nonlinear kernels add parameters that overfit the sparse tail. Linear SVM's hinge loss finds the max-margin separating hyperplane, which generalizes well in exactly this regime.

**Sparsity and the independence assumption.** Each document activates a few hundred of the `|V|` features. Naive Bayes' per-feature independence assumption is false (words co-occur) but the *ranking* of `P(y | x)` across classes is robust to the miscalibrated magnitudes, so `argmax` stays correct even when the probabilities are wildly overconfident.

**Feature weighting encodes document structure.** Raw counts overweight frequent function words. TF-IDF down-weights terms common across documents (`idf = log(N / df)`) and up-weights rare discriminative terms. Sublinear TF (`1 + log(tf)`) dampens the effect of a word repeated many times in one document.

**What this actually says.** "A word is worth listening to in proportion to how rare it is across the whole corpus, and repeating it in one document should add less and less each time."

| Symbol | What it is |
|--------|------------|
| `N` | Total number of documents in the training corpus |
| `df` | Document frequency — how many of those `N` documents contain this term at least once |
| `N / df` | Inverse document frequency, pre-log. `1.0` when a term is everywhere, large when it is rare |
| `log(N / df)` | The `log` compresses that ratio so a term 1000x rarer is worth ~3x more, not 1000x more |
| `tf` | Term frequency — count of this term inside *this one* document |
| `1 + log(tf)` | Sublinear TF. Grows with repetition but with sharply diminishing returns |

**Walk one example.** A corpus of `N = 10,000` support tickets, three terms of very different rarity:

```
  term       df      N / df     idf = log(N/df)    reading
  the      9,500      1.05          0.0513         in almost every doc -- near-zero weight
  meeting  1,200      8.33          2.1203         moderately common -- some signal
  viagra      12    833.33          6.7254         very rare -- 131x the weight of "the"

  Sublinear TF inside one document:
    tf =  1  ->  1 + log(1)  = 1.0000
    tf =  5  ->  1 + log(5)  = 2.6094      (5x the count, 2.6x the weight)
    tf = 20  ->  1 + log(20) = 3.9957      (20x the count, 4.0x the weight)
```

Without the `idf` factor, `the` (df 9,500) would dominate every document vector while carrying no class signal; without sublinear TF, one spam message screaming `free` twenty times would produce a vector 20x longer than a message using it once, and the classifier would key on repetition rather than vocabulary. Note that scikit-learn's default is the *smoothed* variant `log((1 + N) / (1 + df)) + 1`, which keeps the weight strictly positive so a term appearing in every document is down-weighted rather than annihilated.

**Class imbalance breaks accuracy.** Spam, fraud, and toxic-comment datasets are heavily skewed. Accuracy rewards predicting the majority class; you must switch to precision/recall/F1 on the minority class, apply class weights or resampling, and move the decision threshold rather than defaulting to 0.5.

**Decision boundary between a baseline and BERT.** Fine-tuning an encoder is justified when the linear baseline's errors are concentrated in cases that require word order, long-range context, or semantics that BoW cannot see, AND the accuracy gap clears the latency/cost bar. Otherwise the baseline ships. See [§9](#9-when-to-use--when-not-to-use).

---

## 4. Types / Architectures / Strategies

### 4.1 Task Framings

| Framing | Labels per document | Loss / output | Example |
|---------|---------------------|---------------|---------|
| **Binary** | Exactly 1 of 2 | Sigmoid + BCE, or softmax(2) | Spam vs ham |
| **Multiclass** | Exactly 1 of K | Softmax + cross-entropy | Ticket intent (billing/tech/sales) |
| **Multi-label** | 0..K simultaneously | K independent sigmoids + BCE | Toxic-comment types (toxic, threat, insult) |
| **Hierarchical** | 1 path in a taxonomy | Per-level softmax, or flat with masking | Product category tree (Electronics → Phones → Cases) |

Multiclass assumes labels are mutually exclusive; multi-label does not. A frequent bug is training a K-way softmax on a genuinely multi-label problem, which forces the labels to compete and suppresses all but the top one.

### 4.2 Feature Representations

| Representation | Dimensionality | Captures order? | Captures semantics? | Typical pairing |
|----------------|----------------|-----------------|---------------------|-----------------|
| Bag-of-words (counts) | `|V|` sparse | No | No | MultinomialNB |
| TF-IDF | `|V|` sparse | No | No | LogReg, LinearSVC |
| Char/word n-grams | `|V| + |ngrams|` sparse | Local only | No | fastText |
| Static embeddings (Word2Vec/GloVe) | 100–300 dense | No (avg pooling) | Yes (lexical) | TextCNN, averaged-vector LR |
| Contextual embeddings (BERT) | 768 dense | Yes | Yes (contextual) | Fine-tuned classifier head |

### 4.3 Classifier Families

| Model | Feature input | Strength | Weakness |
|-------|---------------|----------|----------|
| **MultinomialNB** | Counts / TF-IDF | Fastest to train, strong at tiny data | Independence assumption, poorly calibrated |
| **BernoulliNB** | Binary presence | Good for short text (tweets, titles) | Ignores counts |
| **LogisticRegression (MaxEnt)** | TF-IDF | Calibrated probabilities, interpretable weights | Needs more data than NB |
| **LinearSVC** | TF-IDF | Best accuracy of the linear family on long text | No native probabilities |
| **fastText** | Word + char n-grams | Sub-second training on millions of docs, robust to OOV | Shallow, no long-range context |
| **TextCNN** | Static embeddings | Learns local n-gram detectors, fast inference | No long-range dependency |
| **RNN/attention** | Embeddings | Models sequence and long-range order | Slow, largely superseded by transformers |
| **Fine-tuned BERT** | Sub-word tokens | State-of-the-art, handles order + semantics | Heavy training/serving cost |

### 4.4 Multinomial vs Bernoulli Naive Bayes

- **Multinomial NB** models each class as a bag from which words are drawn with class-conditional probabilities; the document likelihood multiplies `P(w | c)` once *per occurrence*. It uses word **counts**, so repeated words compound evidence. Default for medium/long documents.
- **Bernoulli NB** models each vocabulary term as an independent present/absent coin flip; it explicitly includes the probability of **absent** words as evidence. It uses binary **presence**, so it fits very short text (tweets, subject lines) where a word appearing twice is not more informative than once.
- **Laplace (add-one) smoothing, `alpha = 1`**, is mandatory: an unseen (word, class) pair yields `P(w | c) = 0`, which zeroes the entire product and makes the class impossible. Smoothing adds a pseudocount so no term can veto a class. `alpha` is tunable; smaller `alpha` (0.01–0.1) often helps on large vocabularies.

### 4.5 Multi-Label Strategies

| Strategy | Idea | Pros | Cons |
|----------|------|------|------|
| **Binary Relevance** | One independent binary classifier per label | Simple, parallelizable | Ignores label correlations |
| **Classifier Chains** | Feed earlier labels' predictions as features to later ones | Models correlations | Order-dependent, error propagation |
| **Label Powerset** | Treat each observed label-set as one class | Captures joint structure | Explodes combinatorially, sparse classes |
| **Neural BCE head** | K sigmoid outputs, per-label BCE loss | Scales, shares representation | Needs enough data per label |

---

## 5. Architecture Diagrams

### Text-classification pipeline (baseline path)

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    raw(["Raw text"]) --> clean["Preprocess: lowercase, strip, tokenize"]
    clean --> vec["TfidfVectorizer: n-grams 1-2, min_df=2"]
    vec --> clf["Linear classifier: NB / LogReg / LinearSVC"]
    clf --> score["Score per class"]
    score --> thr{"score > threshold?"}
    thr -->|"yes"| pos(["Label: positive"])
    thr -->|"no"| neg(["Label: negative"])

    class raw,pos,neg io
    class clean,vec mathOp
    class clf train
    class score base
    class thr req
```

*The vectorizer (`fit` on train only) and classifier are the two learned stages; the threshold is a tuned decision, not a fixed 0.5. Fitting the vectorizer before the train/test split is the classic data-leakage bug (see [§10](#10-common-pitfalls)).*

### Naive Bayes (generative) vs logistic regression (discriminative)

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    doc(["Document x"]) --> gen["Naive Bayes: model P(x given y) and P(y)"]
    doc --> disc["LogReg: model P(y given x) directly"]
    gen --> bayes["Apply Bayes rule, then argmax"]
    disc --> sig["Sigmoid / softmax of w·x"]
    bayes --> yhat(["Predicted label"])
    sig --> yhat
    gen --> note1["Fewer params, wins at small data"]
    disc --> note2["Lower asymptotic error, wins at large data"]

    class doc,yhat io
    class gen,disc train
    class bayes,sig mathOp
    class note1,note2 base
```

*Both reach a label, but from opposite directions: NB reasons about how the class generates words; logistic regression carves the boundary that best separates classes. The crossover point is the learning curve below.*

### TextCNN (Kim 2014) convolution + max-over-time pooling

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    emb(["Embedding matrix: seq_len × 300"]) --> c3["Conv width 3: 100 filters"]
    emb --> c4["Conv width 4: 100 filters"]
    emb --> c5["Conv width 5: 100 filters"]
    c3 --> p3["Max-over-time pool"]
    c4 --> p4["Max-over-time pool"]
    c5 --> p5["Max-over-time pool"]
    p3 --> cat["Concatenate: 300-dim vector"]
    p4 --> cat
    p5 --> cat
    cat --> drop["Dropout 0.5"]
    drop --> fc["Linear + softmax"]
    fc --> out(["Class probabilities"])

    class emb,out io
    class c3,c4,c5 train
    class p3,p4,p5 mathOp
    class cat,drop mathOp
    class fc train
```

*Three filter widths (3, 4, 5) act as learned trigram/4-gram/5-gram detectors; 100 filters each gives 300 features after max-over-time pooling, which keeps the single strongest activation of each filter regardless of position — the pooling is what makes TextCNN length-invariant.*

### Learning curve: accuracy vs training-set size

```mermaid
xychart-beta
    title "Test accuracy vs labeled examples (NB, LogReg, BERT)"
    x-axis "Labeled examples" [100, 500, 2000, 10000, 50000]
    y-axis "Test accuracy (%)" 60 --> 95
    line [72, 78, 82, 84, 85]
    line [66, 77, 84, 88, 90]
    line [82, 86, 89, 92, 93]
```

*Bottom-to-top by low-data behavior: MultinomialNB (starts high ~72%, plateaus ~85%), TF-IDF LogReg (starts lower ~66%, overtakes NB near 2K examples, reaches ~90%), fine-tuned BERT (best throughout ~82–93%, its pretraining advantage largest at small data). The NB↔LogReg crossover is the Ng & Jordan effect; BERT's flat-high curve is why it wins when labels are scarce but rarely justifies its cost once you have 50K+ examples.*

### Precision and recall as the decision threshold moves

```mermaid
xychart-beta
    title "Precision vs recall across thresholds (imbalanced spam)"
    x-axis "Decision threshold" [0.1, 0.3, 0.5, 0.7, 0.9]
    y-axis "Metric (%)" 40 --> 100
    line [55, 72, 84, 93, 98]
    line [97, 90, 78, 60, 35]
```

*Rising line = precision, falling line = recall. The default 0.5 threshold is rarely optimal on imbalanced data: raise it toward 0.7–0.9 when false positives are expensive (blocking a legitimate email), lower it toward 0.1–0.3 when misses are expensive (letting fraud through). Pick the threshold on a validation set to hit the operating point the product requires.*

---

## 6. How It Works — Detailed Mechanics

### 6.1 Multinomial Naive Bayes math

For document `x` with word counts, the class score under multinomial NB is:

```
log P(y=c | x) ∝ log P(c) + Σ_w  count(w, x) · log P(w | c)

with Laplace smoothing (alpha = 1):

P(w | c) = ( count(w, c) + alpha ) / ( Σ_w' count(w', c) + alpha · |V| )
```

**Read it like this.** "Start each class with its prior popularity, then walk the document word by word adding that class's guilt points for each word you see; whichever class ends up with the highest running total wins."

The `∝` (proportional to) is doing quiet work: Bayes' rule says `P(y=c | x) = P(x | c) · P(c) / P(x)`, and the denominator `P(x)` is the same for every class, so it cannot change which class wins the `argmax`. Dropping it is free. What remains — `P(c) · Π_w P(w | c)^count(w,x)` — is the conditional-independence product, and taking `log` turns that product into the sum written above.

| Symbol | What it is |
|--------|------------|
| `P(c)` | Class prior — what fraction of training documents were class `c` before you read a word |
| `count(w, x)` | How many times word `w` appears in the document being classified |
| `P(w \| c)` | How often word `w` shows up in class `c`'s training text. The learned per-word evidence |
| `log P(w \| c)` | That evidence as an additive score. Always negative, closer to `0` = stronger for `c` |
| `Σ_w` | Sum over the distinct words in *this* document, not the whole vocabulary |
| `∝` | "Ranks the same as." The shared `P(x)` denominator is dropped since it cannot flip the argmax |
| `count(w, c)` | Times word `w` appeared across all of class `c`'s training documents |
| `Σ_w' count(w', c)` | Total word tokens in class `c` — the normalizer that makes `P(w \| c)` a distribution |
| `alpha` | Laplace pseudocount added to every word's tally so nothing is ever exactly zero |
| `\|V\|` | Vocabulary size — the number of distinct terms the model knows |

**Walk one example.** A four-document toy corpus, `alpha = 1`, and one test document to classify:

```
  TRAINING
    spam:  "free money now"        ham:  "meeting at noon"
           "free free prize"             "lunch meeting now"

    vocabulary V = {free, money, now, prize, meeting, at, noon, lunch}   ->  |V| = 8
    spam token counts: free=3 money=1 now=1 prize=1              total = 6
    ham  token counts: meeting=2 at=1 noon=1 lunch=1 now=1       total = 6
    priors: P(spam) = 2/4 = 0.5      P(ham) = 2/4 = 0.5

  SMOOTHED LIKELIHOODS   P(w|c) = (count + 1) / (total + 1 x 8) = (count + 1) / 14

    word       P(w|spam)              P(w|ham)
    free       (3+1)/14 = 0.2857      (0+1)/14 = 0.0714
    money      (1+1)/14 = 0.1429      (0+1)/14 = 0.0714
    meeting    (0+1)/14 = 0.0714      (2+1)/14 = 0.2143

  CLASSIFY  x = "free money meeting"      (each word count = 1)

                        log P(w|spam)     log P(w|ham)
    log prior              -0.6931           -0.6931
    free                   -1.2528           -2.6391
    money                  -1.9459           -2.6391
    meeting                -2.6391           -1.5404
                        -----------       -----------
    total score            -6.5309           -7.5117    <- spam wins

    margin = -6.5309 - (-7.5117) = 0.9808  ->  odds e^0.9808 = 2.67 : 1 for spam
    normalized: P(spam|x) = 0.7273,  P(ham|x) = 0.2727
```

Note how `meeting` fights back hard (`-2.6391` vs `-1.5404`, a 1.10 swing toward ham) but is outvoted by `free` and `money` together. That additive tug-of-war is exactly the "guilt points" mental model from [§2](#2-intuition), and it is why the per-word log-ratios are the interpretable weights the tradeoff table calls "log-odds per word."

**What it means.** The smoothing formula reads: "pretend you saw every vocabulary word `alpha` extra times in every class before counting, and inflate the denominator by the same total pseudocount so the probabilities still sum to 1."

**Walk one example.** The same corpus with smoothing switched off, showing why `alpha = 0` is fatal:

```
  alpha = 0                        alpha = 1
    P(meeting|spam) = 0/6 = 0        P(meeting|spam) = 1/14 = 0.0714
    log(0) = -infinity               log(0.0714) = -2.6391

    spam score = -infinity           spam score = -6.5309
    -> spam is IMPOSSIBLE no         -> spam still loses ground on this word
       matter what "free" and           but the other evidence can outweigh it
       "money" said. One unseen
       word vetoed the class.
```

That single veto is Pitfall 4. Setting `alpha = 1` costs each class only `1 x |V| = 8` pseudo-tokens against a real 6-token budget here (a strong prior on a toy corpus), which is why real vocabularies of 30K+ terms often prefer `alpha = 0.01–0.1` — the pseudocounts must not drown the data.

**Why the sum, not the product.** The formula could equally be written as a product of `P(w | c)` factors, and mathematically it is the same ranking. In floating point it is not. A 400-word document multiplies 400 factors each around `0.001`; the true product is `10^-1200`, while float64 underflows to exactly `0.0` below roughly `10^-324`. Every class becomes `0.0`, `argmax` returns whichever came first, and the classifier silently degenerates into a constant predictor. Summing logs replaces `10^-1200` with `-2763`, a perfectly ordinary number. Every production Naive Bayes implementation works in log-space for this reason alone.

The `alpha · |V|` term in the denominator is what keeps the smoothed probabilities a valid distribution. Work in log-space and sum (never multiply raw probabilities — hundreds of factors below 1 underflow to 0). Cross-link to [../supervised_learning/bayesian_methods.md](../supervised_learning/bayesian_methods.md) for the full derivation and the Dirichlet-prior view of smoothing.

### 6.2 The three linear baselines, side by side

```python
from __future__ import annotations

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import MultinomialNB
from sklearn.svm import LinearSVC
from sklearn.pipeline import Pipeline
from sklearn.model_selection import train_test_split
from sklearn.metrics import f1_score


def build_linear_baselines(
    texts: list[str],
    labels: list[int],
    seed: int = 42,
) -> dict[str, float]:
    """Train MultinomialNB, LogisticRegression, and LinearSVC on the same
    TF-IDF features and return macro-F1 for each. The vectorizer lives INSIDE
    the Pipeline so it is fit on the training fold only (no leakage)."""
    X_train, X_test, y_train, y_test = train_test_split(
        texts, labels, test_size=0.2, stratify=labels, random_state=seed
    )

    # n-grams 1-2 capture short phrases ("not good"); min_df=2 drops hapax
    # legomena; sublinear_tf dampens repeated-word inflation.
    vectorizer = TfidfVectorizer(
        ngram_range=(1, 2),
        min_df=2,
        max_features=50_000,
        sublinear_tf=True,
        strip_accents="unicode",
    )

    models: dict[str, object] = {
        "MultinomialNB": MultinomialNB(alpha=1.0),                 # add-one smoothing
        "LogReg": LogisticRegression(C=1.0, max_iter=1000, class_weight="balanced"),
        "LinearSVC": LinearSVC(C=1.0, class_weight="balanced"),
    }

    results: dict[str, float] = {}
    for name, clf in models.items():
        pipe = Pipeline([("tfidf", vectorizer), ("clf", clf)])
        pipe.fit(X_train, y_train)
        preds = pipe.predict(X_test)
        results[name] = f1_score(y_test, preds, average="macro")
    return results
```

`LinearSVC` gives no `predict_proba`; wrap it in `CalibratedClassifierCV` if you need probabilities for thresholding. `class_weight="balanced"` reweights the loss inversely to class frequency — the first lever to pull under imbalance.

**Put simply.** `class_weight="balanced"` computes `w_c = n_samples / (K · n_c)`, which says: "charge the model as much for one mistake on a rare class as it would pay for a mistake on the average-sized class."

| Symbol | What it is |
|--------|------------|
| `n_samples` | Total training rows across all classes |
| `K` | Number of classes |
| `n_c` | Rows belonging to class `c` |
| `n_samples / K` | What each class *would* hold if the data were perfectly balanced |
| `w_c` | The resulting multiplier on class `c`'s loss terms. `1.0` = exactly average size |

**Walk one example.** The 40K-ticket, 6-intent distribution from the [§14](#14-case-study) case study:

```
  n_samples = 40,000     K = 6     balanced size = 40,000 / 6 = 6,666.7

  class              n_c     share    w_c = 40000 / (6 x n_c)
  How-To          13,600     34.0%       0.4902   <- penalized less than average
  Bug             10,400     26.0%       0.6410
  Billing          7,200     18.0%       0.9259   <- almost exactly average size
  Account-Access   4,800     12.0%       1.3889
  Feature-Request  2,800      7.0%       2.3810
  Other            1,200      3.0%       5.5556   <- each error costs 5.6x a How-To error

  Ratio between the extremes: 5.5556 / 0.4902 = 11.33x
                      (which is exactly 13,600 / 1,200 = 11.33 -- the raw class ratio)
```

The weights are just the inverse class frequencies rescaled so they average out to 1, which is why the extreme ratio equals the raw imbalance ratio. Without them, `Other` contributes 3% of the gradient and the optimizer rationally ignores it — precisely the "recall 0.41 on `Other`" symptom the case study reports even *with* balancing on, because reweighting fixes the loss signal but cannot manufacture the training examples that 1,200 rows fail to provide.

### 6.3 fastText: n-gram features + hierarchical softmax

fastText (Joulin et al., 2016) averages word and character-n-gram embeddings into a single document vector, then applies a linear classifier. Two tricks make it fast: **subword n-grams** give it robustness to OOV and typos, and **hierarchical softmax** replaces the `O(K)` softmax with an `O(log K)` Huffman-tree traversal, which matters when `K` is large (thousands of tags).

**The idea behind it.** "Instead of scoring all `K` labels to find the winner, play twenty questions: each node of a binary tree asks one yes/no question, and `log2(K)` questions identify any label."

| Symbol | What it is |
|--------|------------|
| `K` | Number of output labels (classes/tags) |
| `O(K)` | Flat softmax cost — one dot product and one exponential per label, every example |
| `log2(K)` | Depth of a balanced binary tree over `K` leaves — the number of nodes visited |
| Huffman tree | Binary tree built so frequent labels sit shallow, making the *average* depth below `log2(K)` |

**Walk one example.** Cost per training example as the label space grows:

```
  K            flat softmax   hierarchical   speedup
  6 intents         6 ops        2.58 ops       2.3x    <- not worth it
  1,000 tags    1,000 ops        9.97 ops     100.3x
  30,000 tags  30,000 ops       14.87 ops   2,017.1x    <- the Stack Overflow tag regime
```

The payoff is superlinear in the wrong direction for small `K`: at 6 classes the tree's bookkeeping overhead eats the 2.3x, which is why `loss="softmax"` is the better fastText setting on a handful of intents and `loss="hs"` only earns its place in the thousands-of-tags regime described in [§7](#7-real-world-examples). The Huffman construction is what makes it better than a plain balanced tree — putting the most-used tags near the root drops the *expected* traversal below `log2(K)` for skewed tag distributions, which real tag data always is.

```python
import fasttext

# Training file: one doc per line, labels prefixed with __label__
#   __label__spam  win a free prize click here now
#   __label__ham   are we still on for the meeting tomorrow
def train_fasttext(train_path: str) -> fasttext.FastText._FastText:
    model = fasttext.train_supervised(
        input=train_path,
        lr=1.0,
        epoch=25,
        wordNgrams=2,      # include word bigrams as features
        dim=100,           # embedding dimension
        loss="hs",         # hierarchical softmax: O(log K) instead of O(K)
        minCount=2,
        bucket=200_000,    # hashing bucket for char n-grams
    )
    return model


def predict_fasttext(model: fasttext.FastText._FastText, text: str) -> tuple[str, float]:
    labels, probs = model.predict(text, k=1)
    return labels[0].replace("__label__", ""), float(probs[0])
```

On a million-document tag-classification task, fastText trains in seconds on a CPU and matches deep models within a couple of accuracy points — its speed is the headline feature.

### 6.4 TextCNN (Kim 2014)

```python
from __future__ import annotations

import torch
import torch.nn as nn
import torch.nn.functional as F


class TextCNN(nn.Module):
    """Kim (2014) sentence classifier: parallel conv filters of widths 3/4/5,
    100 filters each, max-over-time pooling, dropout, softmax."""

    def __init__(
        self,
        vocab_size: int,
        embed_dim: int = 300,
        num_classes: int = 2,
        filter_sizes: tuple[int, ...] = (3, 4, 5),
        num_filters: int = 100,
        dropout: float = 0.5,
        pad_idx: int = 0,
    ) -> None:
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, embed_dim, padding_idx=pad_idx)
        # One Conv1d per filter width; in_channels = embed_dim (channels-first).
        self.convs = nn.ModuleList(
            [nn.Conv1d(embed_dim, num_filters, kernel_size=k) for k in filter_sizes]
        )
        self.dropout = nn.Dropout(dropout)
        # 3 widths × 100 filters = 300 features into the classifier.
        self.fc = nn.Linear(num_filters * len(filter_sizes), num_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (batch, seq_len) token ids
        emb = self.embedding(x)                 # (batch, seq_len, embed_dim)
        emb = emb.transpose(1, 2)               # (batch, embed_dim, seq_len)
        # Conv -> ReLU -> max-over-time pool for each filter width.
        pooled = [
            F.max_pool1d(F.relu(conv(emb)), kernel_size=conv(emb).shape[2]).squeeze(2)
            for conv in self.convs
        ]
        feats = torch.cat(pooled, dim=1)        # (batch, num_filters * num_widths)
        feats = self.dropout(feats)
        return self.fc(feats)                   # (batch, num_classes) logits
```

Max-over-time pooling keeps only the single strongest activation of each filter across the whole sentence, so the network is invariant to *where* an informative phrase appears and to sentence length. Initialize `self.embedding` from pretrained Word2Vec/GloVe and either freeze it (CNN-static) or fine-tune it (CNN-non-static); non-static usually wins by 1–2 points.

### 6.5 Handling class imbalance

```python
import torch
import torch.nn as nn
import torch.nn.functional as F


class FocalLoss(nn.Module):
    """Down-weights easy, well-classified examples so training focuses on the
    hard minority class. gamma=2 is the Lin et al. (2017) default; alpha
    reweights the positive class."""

    def __init__(self, alpha: float = 0.25, gamma: float = 2.0) -> None:
        super().__init__()
        self.alpha = alpha
        self.gamma = gamma

    def forward(self, logits: torch.Tensor, targets: torch.Tensor) -> torch.Tensor:
        ce = F.binary_cross_entropy_with_logits(logits, targets, reduction="none")
        p_t = torch.exp(-ce)                    # model's prob of the true class
        focal = self.alpha * (1 - p_t) ** self.gamma * ce
        return focal.mean()
```

**What the formula is telling you.** `focal = alpha · (1 - p_t)^gamma · CE` says: "keep the ordinary cross-entropy loss, but multiply it by how *wrong* the model still is, so examples it already gets right stop contributing gradient."

| Symbol | What it is |
|--------|------------|
| `CE` | Plain binary cross-entropy for this example, `-log(p_t)`. Zero when perfectly right |
| `p_t` | The model's predicted probability of the *true* class. `exp(-ce)` recovers it from the loss |
| `1 - p_t` | How much probability mass the model still has on the wrong answer — its "difficulty" |
| `gamma` | Focusing exponent, `2.0`. Higher = more aggressively silences easy examples |
| `alpha` | Class-balance factor, `0.25`. A flat scale on the positive class, same job as a class weight |
| `(1 - p_t)^gamma` | The modulating factor. Near `0` for easy examples, near `1` for hard ones |

**Walk one example.** Three examples of decreasing confidence, `alpha = 0.25`, `gamma = 2`:

```
  p_t     CE = -log(p_t)   (1 - p_t)^2   focal = 0.25 x mod x CE   kept fraction
  0.9         0.1054          0.0100            0.0003                0.25%
  0.5         0.6931          0.2500            0.0433                6.25%
  0.1         2.3026          0.8100            0.4663               20.25%

  Easy example (p_t = 0.9) vs hard example (p_t = 0.1):
    under plain CE   :  0.1054  vs  2.3026   ->  hard example is  21.8x louder
    under focal loss :  0.0003  vs  0.4663   ->  hard example is 1770x louder
```

That is the whole point: with 99% easy negatives, plain CE lets 99 quiet voices at `0.1054` each drown out the one hard positive at `2.3026`. The modulating factor scales the easy example down by 100x (`1 / 0.01`) while scaling the hard one down by only 1.23x (`1 / 0.81`), so the summed gradient finally points at the minority class. Set `gamma = 0` and focal loss collapses back to `alpha`-weighted CE — the class-weight lever from §6.2 — which is exactly why focal loss is listed *after* class weights in the escalation order below.

The imbalance toolkit, cheapest first: (1) **class weights** (`class_weight="balanced"` or a weighted loss), (2) **threshold moving** (tune the decision threshold on validation — see the PR diagram in [§5](#5-architecture-diagrams)), (3) **resampling** (SMOTE on TF-IDF vectors, or simple minority oversampling), (4) **focal loss** for neural models with extreme skew, (5) **ComplementNB** instead of MultinomialNB, which estimates parameters from the *complement* of each class and is markedly more robust to imbalance for text.

### 6.6 Reading precision, recall, and F1 off a confusion matrix

Both the `f1_score(..., average="macro")` call in §6.2 and the `classification_report` in [§10](#10-common-pitfalls) hide a small pile of arithmetic. Unpacking it is the difference between quoting a metric and understanding what it hides.

**In plain terms.** Precision answers "of everything I flagged, how much was right?"; recall answers "of everything I should have caught, how much did I catch?"; F1 is the harmonic mean that refuses to let either one hide behind the other.

| Symbol | What it is |
|--------|------------|
| `TP` | True positives — spam correctly flagged as spam |
| `FP` | False positives — legitimate mail wrongly blocked. The cost of over-flagging |
| `FN` | False negatives — spam that reached the inbox. The cost of under-flagging |
| `TN` | True negatives — legitimate mail correctly delivered |
| `precision = TP / (TP + FP)` | Purity of the positive predictions. Denominator = what you *flagged* |
| `recall = TP / (TP + FN)` | Coverage of the real positives. Denominator = what actually *existed* |
| `F1 = 2PR / (P + R)` | Harmonic mean. Pulled toward the *smaller* of the two, unlike an average |

**Walk one example.** A 10,000-email test set at the 98%-ham skew from Pitfall 2:

```
                       predicted spam    predicted ham      row total
    actually spam         TP =   150       FN =    50            200
    actually ham          FP =    30       TN = 9,770          9,800
                       --------------   --------------      ---------
    column total               180            9,820           10,000

    precision = 150 / (150 +  30) = 150 /  180 = 0.8333
    recall    = 150 / (150 +  50) = 150 /  200 = 0.7500
    F1        = 2 x 0.8333 x 0.7500 / (0.8333 + 0.7500) = 1.2500 / 1.5833 = 0.7895
    accuracy  = (150 + 9,770) / 10,000                              = 0.9920

    the trivial "always ham" model:  accuracy = 9,800 / 10,000       = 0.9800
                                     recall on spam                  = 0.0000
```

The real classifier beats the do-nothing baseline by 1.2 accuracy points (`0.9920` vs `0.9800`) while catching 75% of the spam the baseline catches none of. That 1.2-point gap is the entire signal accuracy is capable of showing you here, and it is why Pitfall 2 insists on the minority-class F1 of `0.7895` instead. Note also why F1 uses the harmonic mean rather than the ordinary average. Here the two numbers are close (`0.8333` and `0.7500`), so arithmetic `0.7917` and harmonic `0.7895` barely differ. The two diverge violently when the model games one metric: a paranoid filter that blocks only its single most confident message might reach precision `0.99` at recall `0.10`, which averages to a respectable-looking `0.545` but yields F1 `0.1817`. The harmonic mean is pulled toward the weaker number, so you cannot buy a good score by maxing one metric and abandoning the other.

**Stated plainly.** Macro-averaging says "score every class separately, then average the scores, one vote per class"; micro-averaging says "pool every prediction into one giant confusion matrix, one vote per example."

| Symbol | What it is |
|--------|------------|
| `macro-F1` | Mean of the per-class F1 scores. Every class weighs the same regardless of size |
| `micro-F1` | F1 computed from summed `TP`, `FP`, `FN` across classes. Big classes dominate |
| `weighted-F1` | Per-class F1 averaged with each class's support as its weight — a middle ground |
| support | Number of true examples of a class in the evaluation set |

**Walk one example.** A 4,000-ticket validation fold of the [§14](#14-case-study) 6-intent classifier, holding the case study's reported `Feature-Request` recall `0.58` and `Other` recall `0.41`:

```
  class              support     TP     FP     FN   precision  recall     F1
  How-To               1,360  1,265    210     95     0.858     0.930   0.892
  Bug                  1,040    915    150    125     0.859     0.880   0.869
  Billing                720    648     80     72     0.890     0.900   0.895
  Account-Access         480    408     55     72     0.881     0.850   0.865
  Feature-Request        280    162     33    118     0.831     0.579   0.682
  Other                  120     49     25     71     0.662     0.408   0.505
                       -----  -----   ----   ----
  totals               4,000  3,447    553    553

  macro-F1 = (0.892 + 0.869 + 0.895 + 0.865 + 0.682 + 0.505) / 6        = 0.785
  micro-P  = 3,447 / (3,447 + 553) = 0.8618
  micro-R  = 3,447 / (3,447 + 553) = 0.8618
  micro-F1 = 0.8618                                                     = 0.862
  accuracy = 3,447 / 4,000                                              = 0.862
```

The 7.7-point gap between micro `0.862` and macro `0.785` is the whole story: `Other` and `Feature-Request` together are 10% of the tickets, so their weak F1 scores (`0.505` and `0.682`) barely move the micro number but each carry a full one-sixth vote in the macro number. **Report macro-F1 when rare classes matter** — it is the metric that refuses to let a 3% class be rounded away, which is why §6.2 and Best Practice 4 both specify it.

Two properties worth committing to memory. First, in single-label multiclass, micro-precision, micro-recall, micro-F1, and accuracy are all the *same number* (`0.8618` four times above) — every error is simultaneously somebody's `FP` and somebody else's `FN`, so the two denominators are identical. Quoting "micro-F1" on a single-label problem is therefore just quoting accuracy in a lab coat. Second, total `FP` equals total `FN` equals 553 for the same reason: each of the 553 misrouted tickets was stolen from exactly one class and given to exactly one other.

---

## 7. Real-World Examples

**Gmail spam filtering.** Google's spam classifier began life as a Naive Bayes model over word and header features and still uses linear models in its ensemble for their speed and interpretability; the per-word weights make it auditable ("why was this flagged?"). Modern Gmail layers deep models on top, but the fast linear layer scores the overwhelming majority of clearly-ham mail cheaply.

**fastText at Meta.** fastText was built to classify billions of posts and tags at Facebook scale where training a deep model per label was infeasible. On the DBpedia and Yelp benchmarks it matches character-CNNs while training in seconds on CPU instead of hours on GPU — the canonical example of a shallow model winning on the throughput axis.

**Jigsaw toxic-comment classification (multi-label).** The Kaggle Jigsaw dataset labels each Wikipedia comment with up to six non-exclusive types (toxic, severe_toxic, obscene, threat, insult, identity_hate). It is the textbook multi-label problem: a BCE head over six sigmoids, evaluated with per-label AUC and macro-F1, with heavy imbalance (threats are <0.3% of comments) forcing threshold tuning per label.

**Stack Overflow tag prediction (extreme multi-label / hierarchical).** Tens of thousands of possible tags per question makes label powerset impossible; production systems use binary relevance with a shared TF-IDF or embedding backbone plus hierarchical softmax to keep inference sub-linear in the tag count.

**Yelp / Amazon review sentiment.** The standard benchmark where TF-IDF + LinearSVC reaches ~93–95% on binary polarity — within a few points of BERT — and is the reference case for "why fine-tune?" A fine-tuned encoder earns its keep mainly on the negation and sarcasm subset that BoW cannot represent.

---

## 8. Tradeoffs

### Model family tradeoffs

| Model | Train time (100K docs) | Inference | Accuracy tier | Interpretable? |
|-------|------------------------|-----------|---------------|----------------|
| MultinomialNB | ~1 s | <0.1 ms | Baseline | Yes (log-odds per word) |
| LogisticRegression | ~10 s | <0.1 ms | Good | Yes (weights) |
| LinearSVC | ~20 s | <0.1 ms | Good+ | Yes (weights) |
| fastText | ~5 s | <0.1 ms | Good | Partly |
| TextCNN | minutes (GPU) | ~1 ms | Better | No |
| Fine-tuned BERT | hours (GPU) | 10–80 ms | Best | No |

### Feature representation tradeoffs

| Representation | Memory | OOV handling | Order sensitivity |
|----------------|--------|--------------|-------------------|
| BoW counts | Low (sparse) | Ignores OOV | None |
| TF-IDF | Low (sparse) | Ignores OOV | None (n-grams give local) |
| fastText n-grams | Medium | Robust (subword) | Local only |
| Contextual (BERT) | High (dense) | Robust (WordPiece) | Full |

### Multinomial vs Bernoulli NB

| Aspect | MultinomialNB | BernoulliNB |
|--------|---------------|-------------|
| Feature | Word counts | Binary presence |
| Uses word absence as signal | No | Yes (explicit) |
| Best document length | Medium/long | Short (titles, tweets) |
| Repeated words | Compound evidence | No extra weight |

---

## 9. When to Use / When NOT to Use

### Start with a linear baseline (TF-IDF + LogReg/LinearSVC) when:

- You are building the first version — always establish this number before anything else.
- Latency budget is tight (<1 ms) or you serve on CPU at high RPS.
- Training data is modest (hundreds to low tens of thousands of examples).
- The signal is largely topical/lexical (spam, topic tagging, coarse intent).
- You need interpretable, auditable per-word weights (compliance, moderation appeals).

### Use fastText when:

- You have millions of documents and need training measured in seconds.
- The label space is large (thousands of tags) — hierarchical softmax pays off.
- Inputs are noisy/multilingual with heavy OOV — subword n-grams are robust.

### Use TextCNN / neural models when:

- Local word-order patterns matter (negation, short idioms) but you cannot afford BERT.
- You have pretrained embeddings and 10K+ labeled examples.

### Fine-tune BERT (see [bert_and_pretrained_models.md](bert_and_pretrained_models.md)) when:

- The linear baseline's residual errors demonstrably require context/semantics/order (sarcasm, coreference, subtle intent).
- The accuracy gap clears the latency and cost bar — a 5% F1 gain that costs 100× inference is often not worth it.
- You have GPU serving and 5K+ labeled examples (fewer, and NB may still win — see the learning curve in [§5](#5-architecture-diagrams)).

### Do NOT reach for BERT when:

- You have not measured the linear baseline yet.
- The dataset is <1K examples (BERT overfits; NB is stronger).
- Serving is CPU-only at high throughput and latency-critical.

---

## 10. Common Pitfalls

### Pitfall 1 (BROKEN → FIX): fitting the vectorizer before the split — data leakage

```python
# BROKEN: vectorizer learns IDF statistics (and vocabulary) from the FULL
# dataset, including the test rows. The test set has leaked into training.
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression

vectorizer = TfidfVectorizer()
X = vectorizer.fit_transform(all_texts)          # <-- fit sees test rows
X_train, X_test, y_train, y_test = train_test_split(X, all_labels, test_size=0.2)
clf = LogisticRegression().fit(X_train, y_train)
# Reported accuracy is optimistically biased; it drops in production.
```

```python
# FIXED: split first, then fit the vectorizer on TRAIN only. A Pipeline makes
# this the default because fit()/predict() re-fit only on the training fold.
from sklearn.pipeline import Pipeline

X_train, X_test, y_train, y_test = train_test_split(all_texts, all_labels, test_size=0.2)
pipe = Pipeline([("tfidf", TfidfVectorizer()), ("clf", LogisticRegression())])
pipe.fit(X_train, y_train)                        # vectorizer.fit sees TRAIN only
acc = pipe.score(X_test, y_test)                  # honest estimate
```

Production incident pattern: a team reported 96% offline accuracy on spam that fell to 88% in production. Root cause: `fit_transform` on the concatenated corpus leaked test-set IDF and vocabulary into training. Moving the vectorizer inside a `Pipeline` (and into each cross-validation fold) closed the 8-point gap. In cross-validation, always put the vectorizer inside the fold, never vectorize once outside `cross_val_score`.

### Pitfall 2 (BROKEN → FIX): reporting accuracy on imbalanced spam

```python
# BROKEN: 98% of email is ham. A model that predicts "ham" for EVERYTHING
# scores 98% accuracy and catches ZERO spam.
from sklearn.metrics import accuracy_score
preds = ["ham"] * len(y_test)                     # trivial majority predictor
print(accuracy_score(y_test, preds))              # 0.98 — looks great, is useless
```

```python
# FIXED: evaluate precision/recall/F1 on the MINORITY (spam) class, and tune
# the threshold for the operating point the product needs.
from sklearn.metrics import classification_report, precision_recall_curve
import numpy as np

proba = pipe.predict_proba(X_test)[:, 1]          # P(spam)
prec, rec, thr = precision_recall_curve(y_test, proba, pos_label="spam")
# choose the smallest threshold with precision >= 0.95 (few false blocks)
target = np.argmax(prec >= 0.95)
chosen = thr[target] if target < len(thr) else 0.5
preds = np.where(proba >= chosen, "spam", "ham")
print(classification_report(y_test, preds))       # look at spam recall, not accuracy
```

### Pitfall 3: K-way softmax on a multi-label problem

Training a K-class softmax when documents can carry several labels forces the labels to compete for one probability budget, so only the single top label ever fires. Fix: use K independent sigmoids with `BCEWithLogitsLoss` (binary relevance), and threshold each label independently.

### Pitfall 4: no smoothing in Naive Bayes

A single unseen (word, class) pair drives `P(w | c) = 0`, which zeroes the whole product and makes the class impossible regardless of other evidence. Always keep `alpha >= ` a small positive value (`MultinomialNB(alpha=1.0)` by default); never set `alpha=0`.

### Pitfall 5: over-aggressive preprocessing

Stripping stopwords and punctuation, and lowercasing indiscriminately, can destroy signal: `not` and `no` are stopwords critical to sentiment; `FREE!!!` vs `free` distinguishes spam; casing distinguishes `US` (country) from `us`. Test each preprocessing step against the baseline rather than assuming it helps.

### Pitfall 6: SMOTE on sparse TF-IDF interpolates nonsense

SMOTE creates synthetic minority points by interpolating between neighbors, but interpolating two sparse TF-IDF vectors yields a dense vector that corresponds to no real document and can hurt linear models. Prefer class weights, threshold moving, or `ComplementNB` for text; if resampling, simple minority oversampling is safer than SMOTE on BoW features.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `scikit-learn` | TF-IDF, MultinomialNB, LogisticRegression, LinearSVC, Pipeline, metrics | The linear-baseline workhorse; keep the vectorizer in a Pipeline |
| `fasttext` (Meta) | Ultra-fast n-gram linear classifier with hierarchical softmax | CPU training in seconds on millions of docs |
| `torch` / `torchtext` | TextCNN, RNN, custom neural classifiers | `nn.Conv1d` for TextCNN; pad and pack sequences |
| `transformers` (HuggingFace) | Fine-tuned encoder classifiers | See [bert_and_pretrained_models.md](bert_and_pretrained_models.md) |
| `imbalanced-learn` | Resampling (SMOTE, RandomOverSampler), pipeline integration | Prefer class weights first on sparse text |
| `scikit-multilearn` | Multi-label transformations (binary relevance, classifier chains, label powerset) | For non-neural multi-label |
| `spaCy` | Tokenization, sentence segmentation, lemmatization | Preprocessing front-end |
| `Optuna` | Hyperparameter search (C, alpha, ngram_range, thresholds) | Tune inside cross-validation folds |

---

## 12. Interview Questions with Answers

**Q: Why do linear models (logistic regression, linear SVM) usually beat nonlinear kernels on text?**
Text bag-of-words features are extremely high-dimensional and sparse, so classes are almost always linearly separable and a flat hyperplane suffices. Nonlinear kernels (RBF) add capacity that overfits the sparse tail and slow training from near-linear to `O(n^2)` in examples. A `LinearSVC` on TF-IDF typically matches or beats an RBF SVM at a fraction of the cost, which is why the linear kernel is the text default.

**Q: Why does Naive Bayes work well for text despite its independence assumption being false?**
The independence assumption produces miscalibrated probabilities but a correct class ranking, and classification only needs the `argmax`. Words in a spam email co-occur (violating independence), yet they all point toward the spam class, so the wrong magnitudes still yield the right winner. NB fails when you need the probability itself (thresholding, calibration), not just the top class.

**Q: When would you pick a TF-IDF baseline over fine-tuning BERT?**
Pick the baseline when the signal is topical/lexical, latency is tight, data is modest, or you have not yet measured a baseline. A TF-IDF + LinearSVC pipeline reaches within 5–15% of BERT on most classification tasks at <1 ms CPU inference versus 10–80 ms on GPU. You fine-tune BERT only when the residual errors demonstrably require order/context/semantics and the gap clears the cost bar.

**Q: What is the difference between Multinomial and Bernoulli Naive Bayes for text?**
Multinomial NB uses word counts and multiplies `P(w|c)` once per occurrence; Bernoulli NB uses binary presence and explicitly includes the probability that a word is absent. Multinomial suits medium/long documents where repeated words compound evidence; Bernoulli suits very short text (tweets, titles) where a word appearing twice is not more informative. Bernoulli's use of absence as a signal helps when the vocabulary is small and documents are short.

**Q: Why is Laplace smoothing necessary in Naive Bayes and what does alpha control?**
Without smoothing, an unseen (word, class) pair gives `P(w|c) = 0`, which zeroes the entire product and makes that class impossible no matter what other words say. Laplace smoothing adds a pseudocount `alpha` (default 1) to every count so no term can veto a class. Smaller `alpha` (0.01–0.1) trusts the data more and often helps on large vocabularies; `alpha=0` reintroduces the zero-probability bug.

**Q: You report 98% accuracy on a spam classifier. Why might that be meaningless?**
If 98% of email is ham, a model that always predicts "ham" scores 98% accuracy while catching zero spam. Accuracy is dominated by the majority class under imbalance, so you must report precision, recall, and F1 on the minority (spam) class, or PR-AUC. The fix is to evaluate the minority class and tune the decision threshold to the required operating point.

**Q: What is data leakage in a text-classification pipeline and how do you prevent it?**
Data leakage is when information from the test set influences training — most commonly fitting the TF-IDF vectorizer on the full corpus before splitting, which leaks test-set IDF statistics and vocabulary. It inflates offline metrics that then collapse in production. Prevent it by splitting first and fitting the vectorizer on the training fold only, ideally by putting it inside a `Pipeline` so cross-validation re-fits it per fold.

**Q: How do you handle class imbalance in text classification?**
Start with the cheapest lever and escalate: class weights, then threshold moving, then resampling, then focal loss. Concretely that is `class_weight="balanced"`, then a validation-tuned decision threshold, then oversampling, then focal loss for neural models, and `ComplementNB` in place of `MultinomialNB` for imbalanced text. Threshold moving is often the highest-leverage step because it directly targets the precision/recall operating point without retraining. Always tune the threshold on validation data, never on the test set.

**Q: Explain TextCNN's architecture and why max-over-time pooling matters.**
TextCNN (Kim 2014) runs parallel 1-D convolutions of widths 3, 4, and 5 (100 filters each) over the embedding sequence, acting as learned trigram/4-gram/5-gram detectors, then applies max-over-time pooling and a softmax. Max-over-time pooling keeps only each filter's single strongest activation across the whole sentence, making the model invariant to where an informative phrase occurs and to sentence length. The three filter widths concatenate to a 300-dim feature vector fed to the classifier.

**Q: How does fastText achieve such fast training and OOV robustness?**
fastText averages word and character-n-gram embeddings into one document vector and uses hierarchical softmax to reduce the output cost from `O(K)` to `O(log K)`. The character n-grams give it robustness to out-of-vocabulary words and typos because subwords of an unseen word are still known. On millions of documents it trains in seconds on CPU while matching deep models within a couple of accuracy points.

**Q: What is the generative-vs-discriminative distinction and how does it affect small-data behavior?**
Naive Bayes is generative — it models `P(x|y)` and `P(y)` — while logistic regression and SVM are discriminative, modeling `P(y|x)` or the boundary directly. Ng & Jordan (2002) showed generative NB has higher asymptotic error but converges to it with far fewer examples, so NB tends to win at small data and discriminative models win as data grows. This crossover is visible as the learning-curve intersection between NB and logistic regression.

**Q: How do you frame and train a multi-label classifier?**
Use K independent binary classifiers (binary relevance) or a neural head with K sigmoid outputs trained with per-label binary cross-entropy, then threshold each label independently. Do not use a K-way softmax, which forces labels to compete and suppresses all but the top one. To model label correlations, use classifier chains (feed earlier predictions as features) at the cost of order-dependence and error propagation.

**Q: Why prefer LinearSVC over an RBF-kernel SVM for text, and what does LinearSVC lack?**
LinearSVC trains in near-linear time and matches RBF accuracy on high-dimensional sparse text where a linear boundary already separates classes, whereas RBF is `O(n^2)` and overfits. The tradeoff: `LinearSVC` provides no `predict_proba`, so if you need calibrated probabilities for thresholding you wrap it in `CalibratedClassifierCV` (Platt scaling or isotonic). For pure top-1 prediction, the raw decision function is enough.

**Q: What does TF-IDF do and why is it better than raw counts?**
TF-IDF multiplies term frequency by inverse document frequency (`log(N / df)`), down-weighting words common across many documents (the, is) and up-weighting rare discriminative terms. Raw counts overweight frequent function words that carry little class signal. Sublinear TF (`1 + log(tf)`) further dampens a word repeated many times in one document so a single spammy repetition does not dominate the vector.

**Q: When is Bernoulli NB the right choice over Multinomial NB?**
Choose Bernoulli NB for very short documents — tweets, subject lines, product titles — where word presence matters more than count and the explicit absence signal is informative. Multinomial NB is better for medium/long documents where repeated words should compound evidence. Empirically Bernoulli wins on short-text tasks with small vocabularies and Multinomial wins as document length grows.

**Q: Why can preprocessing like stopword removal hurt a text classifier?**
Standard stopword lists remove words like not, no, and never that are decisive for sentiment and negation, and aggressive lowercasing collapses distinctions like US (country) vs us. Over-cleaning also drops punctuation such as the multiple exclamation marks that flag spam. Always A/B each preprocessing step against the baseline rather than assuming normalization helps.

**Q: How do you choose the decision threshold for a binary text classifier?**
Compute the precision-recall curve on a validation set and pick the threshold that meets the product's operating point. Use a high threshold when false positives are costly (blocking real email), a low one when misses are costly (letting fraud through). The default 0.5 is rarely optimal under imbalance. Fix the threshold on validation data and only then report metrics on the held-out test set.

**Q: Why is SMOTE risky on TF-IDF features?**
SMOTE synthesizes minority points by interpolating between sparse neighbor vectors, producing dense vectors that correspond to no real document and can degrade linear models. Text imbalance is better handled with class weights, threshold moving, or `ComplementNB`. If you must resample, simple minority oversampling is safer than SMOTE on bag-of-words features.

---

## 13. Best Practices

1. Always establish a TF-IDF + LinearSVC/LogReg baseline before any neural model — it is the number every later model must beat, and it often ships.
2. Keep the vectorizer inside a `Pipeline` so it is fit on the training fold only; this makes leakage the exception rather than the default.
3. Use `ngram_range=(1, 2)`, `min_df=2`, `sublinear_tf=True`, and `max_features` around 50K as strong TF-IDF defaults; tune with Optuna inside CV folds.
4. Under imbalance, report macro-F1 and minority-class recall, never accuracy; pull class weights, then threshold moving, before resampling.
5. Tune the decision threshold on a validation set to the product's precision/recall operating point instead of defaulting to 0.5.
6. For multi-label, use independent sigmoids + BCE and per-label thresholds; never a K-way softmax.
7. Keep `alpha >= ` a small positive value in Naive Bayes; default `alpha=1.0`, drop toward 0.1 on large vocabularies.
8. For neural text models, initialize embeddings from pretrained Word2Vec/GloVe and fine-tune them (CNN-non-static) for a 1–2 point gain.
9. A/B every preprocessing decision (stopwords, casing, punctuation) against the baseline; do not assume normalization helps.
10. Save the fitted vectorizer and classifier together (joblib) so training and serving share identical vocabulary and IDF — a mismatch silently corrupts predictions.

---

## 14. Case Study

### Problem: Support-Ticket Intent Classifier for a SaaS Helpdesk

**Context:** A B2B SaaS company routes 8,000 support tickets/day. Each ticket's free-text body must be classified into one of 6 intents (Billing, Bug, Feature-Request, How-To, Account-Access, Other) so it lands in the right agent queue. Misrouting costs an average 12-minute handoff delay; the SLA target is p99 < 50 ms classification so routing feels instant.

**Data:** 40K historically labeled tickets, heavily skewed: How-To 34%, Bug 26%, Billing 18%, Account-Access 12%, Feature-Request 7%, Other 3%.

**Phase 1 — Linear baseline (Day 1).**

```python
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.svm import LinearSVC
from sklearn.pipeline import Pipeline
from sklearn.calibration import CalibratedClassifierCV

pipe = Pipeline([
    ("tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=3,
                              max_features=50_000, sublinear_tf=True)),
    # Calibrated so we get probabilities for the low-confidence review queue.
    ("clf", CalibratedClassifierCV(
        LinearSVC(C=1.0, class_weight="balanced"), cv=3)),
])
pipe.fit(train_texts, train_labels)
```

Result: macro-F1 0.81, training 90 seconds on a laptop, inference 0.4 ms/ticket. The weak spot was the rare classes — Feature-Request recall 0.58, Other recall 0.41 — because 3% of data cannot teach a linear model much, even with `class_weight="balanced"`.

**Phase 2 — Imbalance and threshold work (Week 1).**

Switched the loss to focus on rare classes and moved to a **per-class abstention threshold**: if the top calibrated probability is below a class-specific threshold, route the ticket to a human triage queue instead of guessing. Thresholds tuned on validation to keep each queue's precision >= 0.90. This lifted overall precision to 0.90 while sending 9% of tickets (mostly ambiguous Other/Feature-Request) to human triage. Feature-Request precision reached 0.88 at the cost of recall — an acceptable trade because a missed feature-request is cheaper than a misrouted one.

**Phase 3 — Does BERT earn its place? (Week 3).**

Fine-tuned DistilBERT (see [bert_and_pretrained_models.md](bert_and_pretrained_models.md)) on the same 40K tickets: macro-F1 0.87, +6 points over the baseline, concentrated in the ambiguous short tickets ("it broke again") where lexical features are thin and word order/context matters. But CPU inference was 25 ms/ticket versus 0.4 ms, and the 50 ms p99 SLA held only with GPU serving at $745/month.

**Decision — cascade.** Serve the linear model to everything (0.4 ms). When its top calibrated probability is below threshold (the ambiguous ~15% of tickets), escalate that ticket to DistilBERT on a small GPU pool. This delivers near-BERT accuracy (macro-F1 0.86) at ~15% of the GPU cost of running BERT on everything, and the fast path keeps p99 well under the SLA. This mirrors the cascade in the full [system-design case study](../case_studies/design_nlp_classification_pipeline.md), which adds active learning and drift monitoring on top.

**Where those cascade numbers come from.** The cost and latency of a two-tier cascade are just an escalation-rate-weighted blend of the two tiers, `cost = escalation_rate × cost_of_tier_2` and `latency = (1 − rate) × fast + rate × slow`:

| Symbol | What it is |
|--------|------------|
| escalation rate | Fraction of traffic the fast model refuses to decide. Here `0.15` (the ambiguous ~15%) |
| tier-1 cost | Linear model on CPU — effectively free next to a GPU pool, so it drops out of the sum |
| tier-2 cost | `$745/month`, the GPU bill if DistilBERT scored 100% of traffic |
| tier-1 latency | `0.4 ms` per ticket |
| tier-2 latency | `25 ms` per ticket on CPU; batched on GPU in the deployed path |

**Walk one example.** 8,000 tickets/day, escalation rate 15%:

```
  traffic split
    fast path :  8,000 x 0.85  =  6,800 tickets/day on the linear model
    slow path :  8,000 x 0.15  =  1,200 tickets/day on DistilBERT

  GPU cost
    BERT on everything :             $745 / month
    cascade            : 0.15 x 745 = $111.75 -> ~$110 / month observed
    saving             : 745 - 110  =  $635 / month  =  85.2% cheaper

  mean latency (CPU figures, before GPU batching)
    0.85 x 0.4 ms  +  0.15 x 25 ms  =  0.34 + 3.75  =  4.09 ms

  accuracy retained
    baseline 0.81  ->  cascade 0.86  ->  full BERT 0.87
    the cascade captures (0.86 - 0.81) / (0.87 - 0.81) = 5/6 = 83% of BERT's gain
```

The cascade recovers 83% of the achievable accuracy gain for 15% of the GPU spend because escalation cost is *linear* in the escalation rate while accuracy gain is *concentrated* in exactly the examples that get escalated — the fast model was already right on the confident 85%, so sending them to BERT buys almost nothing. This asymmetry is the entire economic argument for cascades, and it only holds if the tier-1 confidence score is well calibrated; an uncalibrated `LinearSVC` decision function would escalate the wrong 15% and the whole structure collapses, which is why Phase 1 wrapped it in `CalibratedClassifierCV`.

**Results:**

- Macro-F1: 0.86 (vs 0.81 baseline, +5 points; +6 from full BERT at 6× the cost).
- p99 latency: 38 ms (fast path 0.4 ms; escalated path batched on GPU).
- 9% of tickets routed to human triage with per-queue precision >= 0.90.
- GPU cost: ~$110/month (cascade) vs ~$745/month (BERT on all traffic).

**Key decisions:**

- The linear baseline shipped first and still handles 85% of traffic — the cascade exists because a plain linear model was measured, not assumed inadequate.
- Per-class abstention thresholds, tuned on validation, turned an accuracy problem into a precision-with-coverage problem the business could reason about.
- BERT was adopted only for the ambiguous minority where its context modeling demonstrably helped, keeping the accuracy gain while avoiding 6× the serving bill.
