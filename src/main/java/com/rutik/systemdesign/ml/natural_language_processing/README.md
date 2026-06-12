# Natural Language Processing (Classical + Pre-Transformer Deep Learning)

> This module covers classical and pre-transformer NLP. Transformer-based LLMs are covered in the LLM section (`llm/foundations_and_architecture/`).

---

## Deep Dive Files

| File | Topic | Q&As |
|------|-------|------|
| [bert_and_pretrained_models.md](bert_and_pretrained_models.md) | BERT, RoBERTa, DeBERTa, DistilBERT, ModernBERT fine-tuning | 15+ |
| [attention_and_seq2seq.md](attention_and_seq2seq.md) | Attention mechanisms, encoder-decoder transformers, beam search | 15+ |
| [text_representation_and_retrieval.md](text_representation_and_retrieval.md) | BM25, Sentence-BERT, ColBERT, hybrid search, RRF | 15+ |
| [nlp_evaluation_and_metrics.md](nlp_evaluation_and_metrics.md) | BLEU, ROUGE, BERTScore, entity-level F1, calibration | 15+ |
| [tokenization_deep_dive.md](tokenization_deep_dive.md) | BPE, WordPiece, Unigram/SentencePiece, byte-level BPE, vocabulary sizing, fertility, train/inference parity | 15+ |

---

## 1. Concept Overview

Natural Language Processing (NLP) is the field of enabling machines to understand, interpret, and generate human language. Classical NLP builds from hand-crafted features and statistical models; deep learning NLP uses learned representations (embeddings) and sequence models (RNNs, LSTMs, CNNs) before the transformer era.

Core tasks: text classification, named entity recognition (NER), part-of-speech tagging, sentiment analysis, machine translation, question answering, topic modeling.

---

## 2. Intuition

One-line analogy: NLP teaches a machine to read — first by memorizing a dictionary (bag of words), then by understanding context (word embeddings), then by tracking sentences in order (LSTMs).

Mental model: Language is sequence + context + structure. Classical methods capture frequency statistics. Word embeddings capture semantic similarity. Sequence models capture order dependencies. Each layer adds a richer view of the same text.

Why it matters: 80% of enterprise data is unstructured text. Search engines, spam filters, customer-support bots, and autocomplete all began with pre-transformer NLP techniques — many of which still run in production because they are fast, interpretable, and require no GPU.

Key insight: TF-IDF and logistic regression still outperform fine-tuned BERT on short-text classification with fewer than 10K training samples due to overfitting risk.

---

## 3. Core Principles

1. Represent text as numbers — bag of words, TF-IDF, embeddings, or character n-grams.
2. Normalize before representing — tokenize, lowercase, remove noise, then stem or lemmatize.
3. Capture co-occurrence — words that appear in similar contexts have similar meanings (distributional hypothesis).
4. Structural prediction needs structured models — sequence labeling tasks (NER, POS) benefit from CRF output layers that enforce label consistency (e.g., I-PER cannot follow B-LOC).
5. Evaluation is task-specific — accuracy for classification; F1 for NER; perplexity for language models; coherence for topic models.

---

## 4. Types / Architectures / Strategies

### 4.1 Text Preprocessing Pipeline

| Step | Method | Notes |
|------|--------|-------|
| Tokenization | Whitespace split, regex, spaCy, SentencePiece | Subword tokenization handles morphologically rich languages |
| Normalization | Lowercase, unicode normalization (NFKC) | Critical for consistency |
| Stop word removal | NLTK stopwords, custom list | Hurts performance on sentiment tasks (negations removed) |
| Stemming | Porter, Snowball | Fast, but produces non-words ("studies" -> "studi") |
| Lemmatization | WordNet lemmatizer, spaCy | Slower, but produces valid words ("studies" -> "study") |
| Noise removal | Regex for URLs, HTML tags, emojis | Domain-specific; remove or keep based on task |

### 4.2 Feature Representations

| Method | Dimensionality | Pros | Cons |
|--------|---------------|------|------|
| Bag of Words | Vocabulary size (~50K–500K) | Simple, interpretable | No order, sparse, high-dim |
| TF-IDF | Vocabulary size | Downweights common terms | Still sparse, no semantics |
| Word2Vec (SGNS) | 300d dense | Semantic similarity, analogies | No OOV handling, polysemy ignored |
| GloVe | 300d dense | Global co-occurrence captured | Static, no OOV |
| FastText | 300d + subword | Handles OOV, morphology | Larger model size |
| Sentence-BERT | 768d dense | Semantic sentence similarity, retrieval | Requires GPU, domain fine-tuning needed |

See [text_representation_and_retrieval.md](text_representation_and_retrieval.md) for BM25 derivation, Sentence-BERT siamese training, ColBERT, and hybrid search.

### 4.3 Text Classification Models

| Model | Complexity | Best For |
|-------|-----------|---------|
| Naive Bayes | O(n*d) | Short text, spam, small data |
| Logistic Regression + TF-IDF | O(n*d) | Production baseline, interpretable |
| TextCNN | O(n*k*d) | Phrase-level features, fast inference |
| BiLSTM + Attention | O(n*h) | Longer sequences, sentiment |
| BERT fine-tuned | O(n²*L) | >10K labeled examples, highest accuracy |

See [bert_and_pretrained_models.md](bert_and_pretrained_models.md) for BERT/RoBERTa/DeBERTa fine-tuning, variants, and production guidance.

### 4.4 Sequence Labeling (NER)

BIO tagging: B-entity (Beginning), I-entity (Inside), O (Outside). For "New York City": B-LOC I-LOC I-LOC. CRF layer enforces valid transitions (I-LOC cannot follow B-PER).

### 4.5 Topic Modeling

LDA (Latent Dirichlet Allocation): each document is a mixture of topics; each topic is a distribution over words. Hyperparameters: alpha (document-topic sparsity, typically 1/K), beta (topic-word sparsity, typically 0.01).

---

## 5. Architecture Diagrams

### TF-IDF + Logistic Regression Pipeline

```
Raw Text
   |
   v
[Tokenize -> Lowercase -> Remove stopwords]
   |
   v
[TF-IDF Vectorizer]   <-- fit on train corpus
   |  sparse matrix (n_docs x vocab_size)
   v
[LogisticRegression]
   |
   v
Class Probabilities
```

### Word2Vec Skip-gram Architecture

```
Input word (one-hot, V-dim)
   |
   v
[Embedding matrix W: V x 300]  <-- weights we learn
   |
   v
Word vector (300-dim)
   |
   v
[Output matrix W': 300 x V]
   |
   v
Softmax over vocabulary -> predict context words
```

### BiLSTM + CRF for NER

```
Tokens:    ["Apple"  "is"   "in"   "NYC"]
              |        |       |      |
[Embedding layer: 300d per token]
              |        |       |      |
[BiLSTM]  ->  ->  ->  ->  ->  ->  ->  (forward)
          <-  <-  <-  <-  <-  <-  <-  (backward)
              |        |       |      |
         [Concatenate fwd + bwd hidden: 512d]
              |        |       |      |
         [Linear -> tag scores]
              |        |       |      |
         [CRF layer: enforces valid BIO transitions]
              |        |       |      |
          B-ORG       O       O    B-LOC
```

### TextCNN Architecture

```
Sentence: [w1, w2, w3, w4, w5]   (each word = 300d embedding)
   |
[Embedding matrix: n_words x 300]
   |
[Conv1D filters: size 2,3,4 x 300 -> feature maps]
   |
[MaxPool over time -> scalar per filter]
   |
[Concatenate all pooled features]
   |
[Dropout 0.5 -> Dense -> Softmax]
```

---

## 6. How It Works — Detailed Mechanics

### TF-IDF + Logistic Regression (sklearn)

```python
from sklearn.pipeline import Pipeline
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
from typing import List, Tuple
import numpy as np

def build_tfidf_classifier(
    texts: List[str],
    labels: List[int],
    max_features: int = 50_000,
    ngram_range: Tuple[int, int] = (1, 2),
) -> Pipeline:
    """
    Build and fit a TF-IDF + LogisticRegression pipeline.
    ngram_range=(1,2) captures unigrams and bigrams.
    max_features=50_000 caps vocabulary to avoid memory issues on large corpora.
    """
    pipeline = Pipeline([
        ("tfidf", TfidfVectorizer(
            max_features=max_features,
            ngram_range=ngram_range,
            sublinear_tf=True,       # apply log(1+tf) instead of raw tf
            strip_accents="unicode",
            analyzer="word",
            min_df=2,                # ignore terms appearing in fewer than 2 docs
        )),
        ("clf", LogisticRegression(
            C=1.0,                   # inverse regularization strength
            max_iter=1000,
            solver="lbfgs",
            multi_class="auto",
            n_jobs=-1,
        )),
    ])

    X_train, X_test, y_train, y_test = train_test_split(
        texts, labels, test_size=0.2, random_state=42, stratify=labels
    )
    pipeline.fit(X_train, y_train)
    y_pred = pipeline.predict(X_test)
    print(classification_report(y_test, y_pred))
    return pipeline
```

### Word2Vec with Gensim

```python
from gensim.models import Word2Vec
from gensim.utils import simple_preprocess
from typing import List
import logging

logging.basicConfig(level=logging.INFO)

def train_word2vec(
    sentences: List[str],
    vector_size: int = 300,
    window: int = 5,
    min_count: int = 5,
    sg: int = 1,          # 1 = Skip-gram, 0 = CBOW
    workers: int = 4,
    epochs: int = 10,
) -> Word2Vec:
    """
    Train Word2Vec on a corpus.
    sg=1 (Skip-gram) gives better quality for rare words.
    sg=0 (CBOW) trains ~3x faster, preferred for large corpora.
    window=5: context window of 5 words each side.
    min_count=5: ignore words appearing fewer than 5 times.
    """
    tokenized = [simple_preprocess(s) for s in sentences]
    model = Word2Vec(
        sentences=tokenized,
        vector_size=vector_size,
        window=window,
        min_count=min_count,
        sg=sg,
        workers=workers,
        epochs=epochs,
    )
    return model

def demo_embeddings(model: Word2Vec) -> None:
    # Semantic similarity
    print(model.wv.most_similar("king", topn=5))
    # Analogy: king - man + woman ~ queen
    result = model.wv.most_similar(
        positive=["king", "woman"], negative=["man"], topn=1
    )
    print(f"king - man + woman = {result[0][0]}")  # "queen"
    # Cosine similarity
    sim = model.wv.similarity("car", "automobile")
    print(f"car <-> automobile similarity: {sim:.3f}")  # ~0.85 on large corpus
```

### spaCy NER Example

```python
import spacy
from spacy.tokens import Doc
from typing import List, Tuple

# Load pre-trained model (en_core_web_lg: 685MB, GloVe vectors + NER)
# en_core_web_sm: 12MB, no vectors, faster
nlp = spacy.load("en_core_web_lg")

def extract_entities(text: str) -> List[Tuple[str, str, int, int]]:
    """
    Returns list of (entity_text, label, start_char, end_char).
    Labels: PERSON, ORG, GPE (geo-political), DATE, MONEY, etc.
    """
    doc: Doc = nlp(text)
    return [(ent.text, ent.label_, ent.start_char, ent.end_char) for ent in doc.ents]

def demo_ner() -> None:
    text = "Apple Inc. was founded by Steve Jobs in Cupertino in 1976."
    entities = extract_entities(text)
    for ent_text, label, start, end in entities:
        print(f"  [{label}] '{ent_text}' at chars {start}-{end}")
    # Output:
    # [ORG] 'Apple Inc.' at chars 0-10
    # [PERSON] 'Steve Jobs' at chars 25-35
    # [GPE] 'Cupertino' at chars 39-48
    # [DATE] '1976' at chars 52-56
```

### TextCNN in PyTorch

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from typing import List

class TextCNN(nn.Module):
    """
    Kim (2014) TextCNN for sentence classification.
    Multiple filter sizes capture n-gram features.
    """
    def __init__(
        self,
        vocab_size: int,
        embed_dim: int = 300,
        num_classes: int = 2,
        filter_sizes: List[int] = [2, 3, 4],
        num_filters: int = 100,
        dropout: float = 0.5,
        pretrained_embeddings: torch.Tensor | None = None,
    ) -> None:
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, embed_dim, padding_idx=0)
        if pretrained_embeddings is not None:
            self.embedding.weight.data.copy_(pretrained_embeddings)

        self.convs = nn.ModuleList([
            nn.Conv1d(
                in_channels=embed_dim,
                out_channels=num_filters,
                kernel_size=fs,
            )
            for fs in filter_sizes
        ])
        self.dropout = nn.Dropout(dropout)
        self.fc = nn.Linear(len(filter_sizes) * num_filters, num_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (batch, seq_len)
        emb = self.embedding(x)                    # (batch, seq_len, embed_dim)
        emb = emb.permute(0, 2, 1)                # (batch, embed_dim, seq_len)

        pooled = []
        for conv in self.convs:
            out = F.relu(conv(emb))                # (batch, num_filters, seq_len - fs + 1)
            out = F.max_pool1d(out, out.size(2))   # (batch, num_filters, 1)
            pooled.append(out.squeeze(2))          # (batch, num_filters)

        cat = torch.cat(pooled, dim=1)             # (batch, len(filter_sizes)*num_filters)
        return self.fc(self.dropout(cat))          # (batch, num_classes)
```

### LDA Topic Modeling

```python
from gensim import corpora
from gensim.models import LdaModel
from gensim.models.coherencemodel import CoherenceModel
from typing import List, Tuple
import re

def preprocess_for_lda(texts: List[str]) -> List[List[str]]:
    """Remove short words and punctuation; return tokenized docs."""
    tokenized = []
    for text in texts:
        tokens = re.findall(r'\b[a-z]{3,}\b', text.lower())
        tokenized.append(tokens)
    return tokenized

def train_lda(
    texts: List[str],
    num_topics: int = 20,
    passes: int = 10,
    random_state: int = 42,
) -> Tuple[LdaModel, corpora.Dictionary]:
    tokenized = preprocess_for_lda(texts)
    dictionary = corpora.Dictionary(tokenized)
    dictionary.filter_extremes(no_below=5, no_above=0.5)  # remove rare and ubiquitous
    corpus = [dictionary.doc2bow(doc) for doc in tokenized]

    model = LdaModel(
        corpus=corpus,
        id2word=dictionary,
        num_topics=num_topics,
        passes=passes,
        alpha="auto",    # learn document-topic distribution
        eta="auto",      # learn topic-word distribution
        random_state=random_state,
        per_word_topics=True,
    )

    # Evaluate coherence (C_v score; higher is better; typically 0.4-0.7 is acceptable)
    coherence_model = CoherenceModel(
        model=model, texts=tokenized, dictionary=dictionary, coherence="c_v"
    )
    score = coherence_model.get_coherence()
    print(f"Coherence Score (C_v): {score:.4f}")
    return model, dictionary
```

---

## 7. Real-World Examples

**Gmail spam filter (pre-2015):** TF-IDF features + Naive Bayes + logistic regression ensemble. Still processes billions of emails per day. Rule-based preprocessing handles Unicode abuse (e.g., "V1agra" variants).

**LinkedIn job title normalization:** FastText subword embeddings map "Sr. SWE" and "Senior Software Engineer" to nearby vectors. OOV handling via subword n-grams is critical because job titles contain abbreviations not in any pre-trained vocabulary.

**Bloomberg News NER:** BiLSTM + CRF trained on financial text. Identifies TICKER symbols, executive names, and organization names where general-purpose spaCy models underperform. Domain-specific training data yields 10-15 F1-point improvement over off-the-shelf models.

**NY Times topic discovery:** LDA with 150 topics over 20 years of articles. Topics drift over time (e.g., "internet" topic words shift from "modem/dial-up" to "broadband/streaming"). Requires periodic retraining or dynamic topic models.

**Stack Overflow search (hybrid retrieval):** BM25 over title+body indexes for exact keyword matching, combined with Sentence-BERT bi-encoder for semantic similarity via FAISS. BM25 alone achieves NDCG@10 of 0.45 on technical queries; adding dense retrieval and RRF fusion (k=60) pushes NDCG@10 to 0.58. Cross-encoder reranking of top-20 results raises MRR@10 by a further 12%. See [text_representation_and_retrieval.md](text_representation_and_retrieval.md) for implementation.

**Salesforce customer intent classification with DeBERTa-v3-base:** A support ticket router with 47 intent classes (billing, outage, feature request, etc.) replaced a LogisticRegression+TF-IDF baseline. DeBERTa-v3-base fine-tuned for 5 epochs (LR 2e-5, batch 32) on 80K labeled tickets achieved 94.1% accuracy vs 87.4% for the baseline. Inference served via TorchServe with dynamic batching (max_batch_delay=50ms) sustains 2,000 RPS on two A10G GPUs. See [bert_and_pretrained_models.md](bert_and_pretrained_models.md) for fine-tuning details.

---

## 8. Tradeoffs

| Dimension | Bag of Words / TF-IDF | Word2Vec / GloVe | FastText | BiLSTM |
|-----------|----------------------|-----------------|---------|--------|
| Training speed | Milliseconds | Hours (large corpus) | Hours | Days |
| Inference speed | Sub-millisecond | Sub-millisecond | Sub-millisecond | 10-50ms |
| OOV handling | None | None | Yes (subword) | Depends on embedding |
| Semantic similarity | No | Yes | Yes | Yes |
| Interpretability | High | Low | Low | Very low |
| Data requirement | 1K docs | 1M+ sentences | 1M+ sentences | 10K+ labeled |

| Stemming vs Lemmatization | |
|--------------------------|--|
| Porter stemming | Fast (regex rules), non-words ("running" -> "run", "studies" -> "studi"), good for IR |
| WordNet lemmatization | Slow (dictionary lookup), valid words, requires POS tag for accuracy |

---

## 9. When to Use / When NOT to Use

### When to Use Classical NLP

- Fewer than 50K labeled training examples (BERT overfits; LR + TF-IDF generalizes better)
- Latency under 1ms required (embeddings and LSTMs are too slow without batching)
- CPU-only deployment with no GPU budget
- Interpretability required (regulators need feature importance)
- Domain-specific text with specialized vocabulary where pre-trained models underperform

### When NOT to Use Classical NLP

- Tasks requiring long-range dependencies beyond 512 tokens
- Multi-lingual or cross-lingual tasks (use multilingual transformers)
- Zero-shot or few-shot settings (use LLMs with prompting)
- Complex reasoning tasks (reading comprehension, entailment)
- When labeled data is abundant (>100K examples) and GPU is available — transformers dominate

---

## 10. Common Pitfalls

### Pitfall 1: Leaking test data into TF-IDF fit

```python
# BROKEN: fit on entire dataset before splitting
vectorizer = TfidfVectorizer()
X = vectorizer.fit_transform(all_texts)   # test vocabulary leaks into vectorizer
X_train, X_test = train_test_split(X, ...)

# FIXED: fit only on training data
X_train_raw, X_test_raw, y_train, y_test = train_test_split(all_texts, labels, ...)
vectorizer = TfidfVectorizer()
X_train = vectorizer.fit_transform(X_train_raw)
X_test = vectorizer.transform(X_test_raw)   # transform only, never fit on test
```

Production incident: A spam classifier showed 97% test accuracy. When deployed, accuracy dropped to 71%. Root cause: TF-IDF was fit on the full dataset, so test-document word frequencies influenced IDF weights. The model memorized test-set vocabulary rather than generalizing.

### Pitfall 2: Stop word removal breaking sentiment

```python
# "This film is not good" -> remove "not" -> "film good" -> positive sentiment
# Fix: do NOT remove stop words for sentiment analysis tasks
# Negation words ("not", "never", "no") are semantically critical
```

### Pitfall 3: Using MAPE when actuals include zeros

Applies here for text-length or frequency metrics: MAPE (Mean Absolute Percentage Error) divides by actual value. When actual count is 0 (e.g., zero mentions of a term), MAPE is undefined or infinite. Use MAE or SMAPE instead.

### Pitfall 4: BIO tag inconsistency without CRF

Without a CRF layer, a neural model can predict I-LOC following B-PER — a structurally invalid sequence. Adding a CRF output layer costs ~5% extra training time and reliably enforces valid BIO transitions, improving NER F1 by 1-3 points.

### Pitfall 5: Word2Vec model size in production

A gensim Word2Vec model trained on 1B tokens with 300d vectors and vocabulary 500K words requires ~600MB RAM. In a microservice handling 1K RPS, loading this model per request is fatal. Fix: load once at startup, share across threads with read-only access; or use a quantized 100d model that fits in 200MB with minimal quality loss.

---

## 11. Technologies & Tools

| Tool | Use Case | Notes |
|------|---------|-------|
| spaCy | Tokenization, NER, POS tagging | Industrial-strength; en_core_web_lg has GloVe vectors |
| NLTK | Preprocessing, WordNet lemmatization, corpora | Research-oriented; slower than spaCy |
| Gensim | Word2Vec, FastText, LDA, Doc2Vec | Best-in-class for word embeddings and topic models |
| scikit-learn | TF-IDF, Naive Bayes, LogisticRegression, pipelines | Production ML pipelines |
| PyTorch | TextCNN, BiLSTM, custom models | Research and custom architectures |
| HuggingFace Tokenizers | Fast BPE/WordPiece tokenization | 100x faster than pure Python |
| fastText (Meta CLI) | Sub-word embeddings, fast text classification | Ships as a single binary; 1M sentences/second |
| Vowpal Wabbit | Online learning for text classification | Handles datasets that don't fit in RAM |

---

## 12. Interview Questions with Answers

**Q: What is TF-IDF and why is it better than raw term frequency?**
TF-IDF (Term Frequency * Inverse Document Frequency) weights each term by how often it appears in a document relative to how common it is across all documents. Raw TF heavily weights common words like "the" and "is" which carry no discriminative signal. IDF penalizes words that appear in many documents, so domain-specific rare terms get higher weight. Practically, sublinear_tf=True (log normalization) further reduces the gap between frequent and rare terms within a document.

**Q: Explain the difference between Word2Vec Skip-gram and CBOW.**
Skip-gram predicts surrounding context words given a center word; CBOW predicts a center word given surrounding context words. Skip-gram is slower to train but produces higher-quality vectors for rare words because each rare word's representation is updated whenever it is the center word. CBOW is ~3x faster and works better for frequent words. On large corpora (>1B tokens), CBOW is preferred for speed; on small or domain-specific corpora, Skip-gram produces better embeddings.

**Q: How does FastText handle out-of-vocabulary words?**
FastText represents each word as the sum of its character n-gram vectors (typically n=3 to 6). For "unhappiness", it computes vectors for substrings "unh", "nha", "hap", "app", "ppi", "pin", "ine", "nes", "ess", then sums them to form the word vector. An OOV word like "unhappily" shares most n-grams with "unhappiness" and receives a meaningful vector rather than a zero or random fallback. This is critical for morphologically rich languages (Turkish, Finnish) and domain-specific text with abbreviations and misspellings.

**Q: What is the BIO tagging scheme in NER?**
BIO stands for Beginning, Inside, Outside. B-{entity} marks the first token of an entity span, I-{entity} marks subsequent tokens of the same span, and O marks non-entity tokens. For "New York City": B-LOC I-LOC I-LOC. BIO is important because it disambiguates consecutive entities of the same type: "Steve Jobs Tim Cook" becomes B-PER I-PER B-PER I-PER, not four I-PER tokens.

**Q: Why add a CRF layer on top of BiLSTM for NER instead of just softmax?**
Softmax predicts each token's label independently, so it can output structurally invalid sequences like I-LOC following B-PER. A CRF layer learns transition scores between label pairs (e.g., the transition I-LOC -> B-PER is valid but I-PER -> B-LOC is unusual) and finds the globally optimal label sequence using Viterbi decoding. This typically improves NER F1 by 1-3 points and eliminates illegal tag sequences entirely.

**Q: What is LDA and how do you evaluate topic model quality?**
LDA (Latent Dirichlet Allocation) models each document as a mixture of K topics, where each topic is a probability distribution over vocabulary words. Inference finds the posterior distribution of topics given observed words. Quality is evaluated using coherence score (C_v): how often the top-N words of each topic co-occur in the training corpus. A C_v of 0.4-0.5 is acceptable; 0.6+ is good. Perplexity measures held-out likelihood but correlates poorly with human-judged topic quality — always prefer coherence.

**Q: How does the TextCNN architecture capture phrase-level features?**
TextCNN applies 1D convolutional filters of varying sizes (e.g., widths 2, 3, 4 tokens) over word embedding sequences. A filter of width 3 can learn to activate on trigrams like "not very good" or "highly recommend". Max-pooling over time picks the most significant activation of each filter regardless of position in the sentence, making the model position-invariant. Multiple filter sizes capture different n-gram granularities simultaneously.

**Q: When would you choose Naive Bayes over Logistic Regression for text classification?**
Naive Bayes trains in O(n*d) with a single pass over data, making it ideal when new training data arrives continuously (e.g., spam that evolves daily) and when training time is constrained. It is also more robust with very small training sets (fewer than 500 examples) because it makes a strong independence assumption that prevents overfitting. Logistic Regression is preferable when you have more than a few thousand examples, correlated features (n-grams), or need calibrated probabilities for downstream decision making.

**Q: What is the difference between stemming and lemmatization? When would you prefer each?**
Stemming applies rule-based suffix stripping to produce a root form ("studies" -> "studi", "running" -> "run"). It is fast (O(1) per word) but produces non-words. Lemmatization uses a morphological dictionary to produce the canonical base form ("studies" -> "study", "running" -> "run"). Lemmatization is ~10x slower than stemming but produces valid words with correct part-of-speech awareness. Use stemming for information retrieval (search engines) where recall matters and the query also gets stemmed. Use lemmatization for tasks where word validity matters (text generation seeds, vocabulary-limited models).

**Q: How do you handle class imbalance in text classification?**
Three main approaches: (1) class_weight="balanced" in scikit-learn LogisticRegression, which weights each sample inversely proportional to class frequency; (2) oversampling the minority class using SMOTE in the embedding space (not on raw text); (3) adjusting the decision threshold post-training based on precision-recall curve analysis. For extreme imbalance (1:100+), reframe as anomaly detection using one-class classifiers. Always evaluate with F1 or AUPR, never accuracy, which is misleading on imbalanced datasets.

**Q: What is the distributional hypothesis and why does it underpin word embeddings?**
The distributional hypothesis states that words appearing in similar contexts have similar meanings (Firth, 1957: "You shall know a word by the company it keeps"). Word2Vec exploits this by training a neural network to predict context words from a center word (or vice versa). The intermediate weight matrix becomes the word embedding — words with similar context distributions (e.g., "cat" and "dog" both appear near "pet", "food", "vet") end up geometrically close in the embedding space, enabling semantic arithmetic like king - man + woman = queen.

**Q: Explain why pre-trained word embeddings can hurt performance on specialized domains.**
Pre-trained embeddings (Word2Vec on Google News, GloVe on Common Crawl) embed words according to general English usage. In a biomedical context, "cold" primarily means a respiratory infection, but in general English it means low temperature. Fine-tuning only the output layer on top of frozen general embeddings will not correct this semantic mismatch. Solutions: (1) train domain-specific embeddings from scratch on domain corpus; (2) continue training (fine-tune) pre-trained embeddings on domain data; (3) use subword models like FastText which are more robust to domain terminology.

**Q: When would you use BERT fine-tuning over TF-IDF + Logistic Regression for text classification?**
Use BERT fine-tuning when you have more than 10K labeled examples, a GPU is available, and accuracy is more important than latency. BERT's bidirectional context captures meaning that TF-IDF cannot — "bank" near "river" vs "bank" near "loan" get different representations. For fewer than 5K examples, TF-IDF + LR usually generalizes better because BERT overfits without sufficient data. For latency under 5ms or CPU-only environments, TF-IDF + LR is the only practical choice. DistilBERT is a middle ground: 60% of BERT's parameter count, 40% faster inference, and within 3% of BERT accuracy on most GLUE tasks. See [bert_and_pretrained_models.md](bert_and_pretrained_models.md) for detailed guidance.

**Q: What is BM25 and when does it outperform dense embedding retrieval?**
BM25 (Best Match 25) is a probabilistic term-frequency ranking function that scores documents by TF saturation (k1=1.2, so additional occurrences of a term yield diminishing returns), length normalization (b=0.75, penalizing verbose documents), and IDF (inverse document frequency, downweighting common terms). BM25 outperforms dense retrieval when queries contain rare terms, product codes, or proper nouns not seen in the embedding model's training data; when queries are exact keyword lookups ("error code 0x80070057"); and when the document collection is very large and un-indexed (FAISS requires memory proportional to corpus size). Dense retrieval wins on paraphrase and semantic matching tasks. Hybrid (BM25 + dense, fused with RRF) is the production standard for general search. See [text_representation_and_retrieval.md](text_representation_and_retrieval.md) for full derivation.

**Q: How do sentence embeddings differ from word embeddings, and why can't you just average word2vec vectors?**
Sentence embeddings represent an entire sentence as a single fixed-length vector capturing sentence-level semantics, not individual word semantics. Averaging word2vec vectors loses word order and composition — "dog bites man" and "man bites dog" have the same average embedding. Additionally, word2vec embeddings are context-independent, so "bank" always has one vector regardless of whether it means river bank or financial institution. Sentence-BERT (SBERT) uses a siamese BERT network trained on NLI (natural language inference) triplets with contrastive loss, producing embeddings where cosine similarity directly measures semantic similarity. SBERT cosine similarity achieves Spearman r=0.86 on STS-B vs 0.20 for averaged GloVe. See [text_representation_and_retrieval.md](text_representation_and_retrieval.md) for training details.

**Q: What is BLEU and what are its most significant weaknesses?**
BLEU (Bilingual Evaluation Understudy) measures the geometric mean of modified n-gram precision (n=1 to 4) between a hypothesis and one or more reference translations, multiplied by a brevity penalty to penalize short outputs. Modified precision clips each n-gram count by its maximum occurrence in any reference, preventing trivial repetition. Weaknesses: BLEU is a corpus-level metric that correlates poorly with human judgment at the sentence level; it penalizes valid paraphrases not matching any reference (a correct translation using "automobile" when the reference says "car" scores zero for that bigram); it rewards n-gram overlap regardless of grammatical coherence; and it is computed differently across implementations (smoothing methods vary). ROUGE-L is preferred for summarization (recall-oriented, based on longest common subsequence). BERTScore is preferred when semantic equivalence matters over lexical matching. See [nlp_evaluation_and_metrics.md](nlp_evaluation_and_metrics.md) for full derivations.

---

## 13. Best Practices

1. Always build a TF-IDF + LogisticRegression baseline before investing in deep learning. On many production tasks, it achieves 85-90% of deep-learning performance at 1% of the cost and latency.
2. Use sklearn Pipeline to wrap preprocessing, vectorization, and classification — this prevents data leakage and simplifies serialization with joblib.
3. For NER, always use a CRF output layer or at minimum post-process outputs to enforce valid BIO sequences.
4. Evaluate topic models with coherence score (C_v), not perplexity. Tune number of topics K by sweeping from 10 to 100 in steps of 10 and picking the elbow.
5. For FastText/Word2Vec, keep min_count >= 5 to filter noise; lowering it below 2 significantly degrades embedding quality.
6. Load large embedding models once at process startup. A 600MB model loaded per request will exhaust memory at 100 RPS.
7. When using pretrained embeddings in a classification model, start with frozen embeddings and fine-tune the classifier first; then unfreeze embeddings with a lower learning rate (1e-4 vs 1e-3) to avoid catastrophic forgetting of pre-trained structure.
8. Strip HTML, normalize unicode, and handle encoding errors (errors="replace") before any linguistic processing. Dirty input invalidates clean downstream models.
9. For production NER pipelines, track per-entity-type F1 separately. A model reporting 88% overall F1 may have 40% F1 on the MONEY entity type, which is catastrophic for financial applications.
10. Never remove negation words (not, never, no) in sentiment analysis pipelines. They are the highest-information tokens in the vocabulary for polarity tasks.

---

## 14. Case Study

**Scenario: Customer-support ticket classification.** A SaaS company routes incoming tickets to 47 queues (billing, auth, API errors, etc.). A fine-tuned bert-base-uncased model, trained on 500k labeled historical tickets, predicts the queue. It is served behind FastAPI with the HuggingFace `transformers` pipeline and dynamic batching. The system handles 800 tickets/sec on 2x A10G GPUs at macro-F1 0.89.

```
ticket text
        |
   tokenizer (bert-base-uncased, max_length=512)
        |
   dynamic batcher (max_batch=32, max_wait=10ms)
        |
   BERT classifier (47-way softmax head)   GPU, batched
        |
   argmax + confidence
        |
   confident? --yes--> route to queue
              --no---> human triage
```

Macro-F1 0.89 across 47 classes, p99 latency 45ms under load, throughput 800 tickets/sec. Dynamic batching trades up to 10ms of queueing for far higher GPU utilization, the key lever that turns single-digit to 800 req/s.

**Sliding-window tokenization for long tickets:**

```python
from transformers import AutoTokenizer
import torch

tok = AutoTokenizer.from_pretrained("bert-base-uncased")

def encode_long(text: str, max_len: int = 512, stride: int = 128) -> dict:
    """Split long tickets into overlapping windows instead of truncating,
    so information at the end of a long ticket is not lost."""
    return tok(
        text, max_length=max_len, truncation=True, stride=stride,
        return_overflowing_tokens=True, return_tensors="pt", padding="max_length",
    )
```

**Class-weighted training for imbalanced queues:**

```python
import torch
from torch import nn
from transformers import AutoModelForSequenceClassification

def make_loss(label_counts: torch.Tensor) -> nn.Module:
    # inverse-frequency weights so rare queues are not ignored
    weights = label_counts.sum() / (len(label_counts) * label_counts)
    return nn.CrossEntropyLoss(weight=weights)

model = AutoModelForSequenceClassification.from_pretrained(
    "bert-base-uncased", num_labels=47
)
```

**FastAPI serving with dynamic batching and window pooling:**

```python
import numpy as np
import torch

class TicketClassifier:
    def __init__(self, model, tokenizer, id2label: dict[int, str]) -> None:
        self.model = model.eval().cuda()
        self.tok = tokenizer
        self.id2label = id2label

    @torch.inference_mode()
    def predict(self, text: str) -> tuple[str, float]:
        enc = encode_long(text)
        logits = self.model(**{k: v.cuda() for k, v in enc.items()
                               if k in ("input_ids", "attention_mask")}).logits
        probs = logits.softmax(-1).mean(dim=0)   # pool over windows
        idx = int(probs.argmax())
        return self.id2label[idx], float(probs[idx])
```

**Pitfall 1 — Right-truncation on long tickets.** Default truncation cuts the end of the text; for tickets where the actual problem is described last (after a long greeting/log dump), the signal is discarded.

```python
# BROKEN: truncate to 512 from the right -> loses the end of long tickets
enc = tok(text, max_length=512, truncation=True)

# FIX: sliding window with stride, then mean-pool the per-window logits so
# every part of a long ticket contributes (see encode_long + window pooling).
enc = encode_long(text, max_len=512, stride=128)
```

**Pitfall 2 — Label imbalance.** Some queues are 1000x rarer than "general inquiry"; with plain cross-entropy the model never predicts the rare classes and their recall is near zero.

```python
# BROKEN: unweighted loss -> model collapses onto majority queues
loss_fn = nn.CrossEntropyLoss()

# FIX: inverse-frequency class weights (and/or oversample rare queues in the
# sampler) so rare-but-important queues are learned.
loss_fn = make_loss(label_counts)
```

**Pitfall 3 — CPU-only inference too slow.** Running BERT on a CPU-only server gives ~15s/ticket, far too slow for live routing.

```python
# BROKEN: PyTorch BERT on CPU -> ~15s/ticket, unusable online
out = model(**enc)   # on CPU

# FIX: export to ONNX and run with ONNX Runtime (graph optimizations +
# quantization) for ~3x CPU speedup, or serve on GPU with batching.
import onnxruntime as ort
sess = ort.InferenceSession("bert.onnx",
                            providers=["CPUExecutionProvider"])
```

**Interview Q&A:**

**Why fine-tune BERT instead of using a zero-shot LLM for ticket routing?** With 500k labeled tickets and 47 fixed classes, a fine-tuned encoder is far cheaper and faster at inference (tens of ms on a small GPU), gives calibrated class probabilities, and is easy to monitor. A zero-shot LLM costs more per call, is slower, and is harder to keep stable across prompt changes; it is better reserved for low-volume or open-ended labels.

**What is dynamic batching and what does it trade off?** The server waits up to a small window (10ms) to collect requests into one batch before running the GPU, dramatically improving throughput because GPUs are most efficient on batches. The trade-off is added latency, bounded by the max-wait. Tuning max_batch and max_wait balances p99 latency against throughput.

**How do you handle tickets longer than the 512-token limit?** Use a sliding window with overlap (stride) to split the ticket into chunks, encode each, and pool the per-chunk logits (mean or max). This preserves information across the whole ticket rather than truncating. Alternatively use a long-context encoder (Longformer), at higher compute cost.

**How do you evaluate a 47-class imbalanced classifier?** Macro-F1 (unweighted mean of per-class F1) is the headline metric because it weights rare queues equally with common ones, exposing collapse on rare classes that accuracy would hide. Inspect the confusion matrix for systematic confusions between semantically close queues, and track per-class recall for the business-critical rare queues.

**Why and how would you export to ONNX?** ONNX decouples the model from PyTorch and lets ONNX Runtime apply graph fusion, constant folding, and quantization, giving roughly 3x CPU speedup and portability across hardware. You export with `torch.onnx.export` (or the transformers ONNX exporter), validate output parity against the PyTorch model, then serve with onnxruntime.

**How do you set a confidence threshold for auto-routing vs human triage?** Calibrate the softmax probabilities (temperature or isotonic), then choose a threshold where the auto-routed precision meets the business SLA (e.g. 97% correct routing). Tickets below the threshold go to human triage. This trades automation rate against routing errors; monitor both in production and adjust as the model and ticket mix drift.

**Pitfall — Tokenizing at inference with a different tokenizer than training.**

```python
# BROKEN: training uses 'bert-base-uncased' tokenizer (lowercases + WordPiece)
# inference accidentally uses 'bert-base-cased' (case-sensitive + different vocab)
# Token IDs diverge → model output is nonsense

from transformers import AutoTokenizer, AutoModel

# Training:
tokenizer = AutoTokenizer.from_pretrained("bert-base-uncased")

# Inference (wrong model name — easy typo in a config file):
tokenizer = AutoTokenizer.from_pretrained("bert-base-cased")  # MISMATCH!

# FIX: save tokenizer alongside model checkpoint
model.save_pretrained("./saved_model")
tokenizer.save_pretrained("./saved_model")   # always save tokenizer with model
# Inference: load both from the same directory
tokenizer = AutoTokenizer.from_pretrained("./saved_model")
```

**How does BERT's masked language model pre-training differ from GPT's causal language model, and which is better for classification?** BERT uses bidirectional attention — every token attends to all others in both directions — making it optimal for tasks requiring full-context understanding (NER, classification, question answering). GPT uses causal (left-to-right) attention — each token attends only to previous tokens — making it optimal for text generation. For classification, BERT-style models (RoBERTa, DeBERTa) consistently outperform GPT-style because they encode the entire input context before producing a representation. GPT-style models can do classification via prompt engineering ("Is this review positive? Answer:"), but fine-tuned BERT-style models win on precision at production scale.

**What is the difference between BPE, WordPiece, and SentencePiece tokenization, and why does it matter for production?** BPE (Byte Pair Encoding) merges the most frequent character pairs iteratively — used by GPT-2/3, RoBERTa. WordPiece uses a likelihood-based merge criterion — used by BERT. SentencePiece is language-agnostic, treating text as a raw byte stream without pre-tokenization — used by T5, LLaMA, enabling multilingual models. In production: tokenizer choice affects vocabulary size (32k-100k), OOV rate for domain-specific terms, and inference speed (longer sequences after tokenization = more compute). For code or structured data with rare symbols, SentencePiece or BPE with a large vocabulary (100k) handles OOV better than WordPiece.
