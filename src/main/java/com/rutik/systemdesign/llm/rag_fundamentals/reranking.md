# Reranking

## 1. Concept Overview

Reranking is a second-stage retrieval step that takes a large pool of candidate documents from initial retrieval (typically top-100) and reorders them to select the most relevant for LLM context (typically top-5 to 10). The key insight is a two-stage architecture: fast bi-encoders retrieve broadly with high recall; slower but more accurate cross-encoders rerank to achieve high precision.

Initial retrieval via bi-encoders encodes queries and documents independently — efficient but imprecise (they can't model fine-grained query-document interaction). Cross-encoder rerankers read the query and document together, enabling rich interaction modeling that bi-encoders fundamentally cannot do. This produces dramatically better relevance judgments at the cost of being too slow for full-index search.

---

## Intuition

> **One-line analogy**: Retrieval is a talent scout who screens hundreds of candidates quickly; the reranker is the expert interviewer who thoroughly evaluates the top 100 and picks the best 5.

**Mental model**: A bi-encoder encodes the query and each document separately into vectors, then compares vectors by cosine similarity. This is fast (compute once, compare with dot product) but misses fine-grained interaction — "What is the capital of France?" and a document saying "Paris is called the 'City of Light' and serves as France's political center" may have moderate cosine similarity even though the document perfectly answers the question. A cross-encoder concatenates query+document as a single input and produces a direct relevance score using full attention across both — it can detect that "political center" answers "capital" and score this document higher.

**Why it matters**: Adding a cross-encoder reranker to an existing retrieval pipeline typically improves precision@5 (the fraction of top-5 results that are relevant) from 70-80% to 90%+ — a significant quality improvement for a ~50-100ms latency cost. The reranker is the single highest-ROI addition to most production RAG pipelines.

**Key insight**: The two-stage retrieval pipeline (bi-encoder for recall, cross-encoder for precision) is the correct separation of concerns — each component does what it's architecturally suited for.

---

## 2. Core Principles

- **Bi-encoder vs. cross-encoder tradeoff**: Bi-encoder is fast (separate encoding) but imprecise; cross-encoder is slow (joint encoding) but highly accurate.
- **Reranking operates on a candidate pool, not the full index**: Cross-encoders can only evaluate hundreds of candidates (not millions) within reasonable latency.
- **Recall first, precision second**: Retrieval must recall broadly enough that the reranker has the relevant documents in its input pool.
- **Reranking score ≠ retrieval score**: The reranker is re-evaluating relevance from scratch, not just refining the retrieval score.
- **Context matters**: Cross-encoders can model context-dependent relevance that pure embedding similarity misses.

---

## 3. How It Works — Detailed Mechanics

### 3.1 Bi-Encoder Architecture (First Stage)

```
Bi-encoder:
  Query encoder:    [CLS] query tokens [SEP] → embedding q
  Document encoder: [CLS] doc tokens [SEP]   → embedding d
  Score: cosine_similarity(q, d)

Key property: encoders are INDEPENDENT
  → Can pre-compute all document embeddings at index time
  → Only compute query embedding at query time
  → Retrieve via ANN: O(log N) per query

Models:
  BAAI/bge-base-en-v1.5  (768d, 512 token limit)
  BAAI/bge-large-en-v1.5 (1024d, better quality)
  text-embedding-3-small  (1536d, OpenAI managed)
  nomic-embed-text-v1.5  (768d, 8192 token context)
```

### 3.2 Cross-Encoder Architecture (Reranker)

```
Cross-encoder:
  Input: [CLS] query tokens [SEP] document tokens [SEP]
  All tokens attend to each other via full self-attention
  Output: single relevance score ∈ [0, 1]

Key property: JOINT encoding
  → Can model fine-grained query-document interaction
  → Cannot pre-compute document representations (score depends on query)
  → Must run a full forward pass per (query, document) pair
  → O(n × L²) where n = number of candidates, L = sequence length

Latency:
  Cross-encoder on 100 candidates × 512 tokens: ~50-100ms on GPU
  Acceptable as second stage; not acceptable as first stage on millions of docs
```

### 3.3 Reranking Implementation

```python
from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

class CrossEncoderReranker:
    def __init__(self, model_name: str = "BAAI/bge-reranker-large"):
        self.tokenizer = AutoTokenizer.from_pretrained(model_name)
        self.model = AutoModelForSequenceClassification.from_pretrained(model_name)
        self.model.eval()
        if torch.cuda.is_available():
            self.model = self.model.cuda()

    def rerank(self, query: str, documents: list[str],
               top_k: int = 5) -> list[tuple[str, float]]:
        pairs = [[query, doc] for doc in documents]
        features = self.tokenizer(
            pairs,
            max_length=512,
            padding=True,
            truncation=True,
            return_tensors="pt"
        )
        if torch.cuda.is_available():
            features = {k: v.cuda() for k, v in features.items()}

        with torch.no_grad():
            scores = self.model(**features).logits.squeeze()
            scores = torch.sigmoid(scores).cpu().numpy()

        ranked = sorted(
            zip(documents, scores.tolist()),
            key=lambda x: x[1],
            reverse=True
        )
        return ranked[:top_k]
```

### 3.4 ColBERT: Late Interaction

ColBERT is a hybrid approach between bi-encoder (independent encoding) and cross-encoder (joint encoding):

```
ColBERT architecture:
  Query encoding:    [q_1, q_2, ..., q_m] = BERT([CLS] query tokens)
  Document encoding: [d_1, d_2, ..., d_n] = BERT([CLS] doc tokens)
  → Documents can be pre-encoded (offline)

  Score = Σ_{i∈query} max_{j∈doc} (q_i · d_j)
  "MaxSim": for each query token, find the best matching document token
  Sum MaxSim scores across all query tokens

Why it's faster than cross-encoder:
  Document embeddings are pre-computed (like bi-encoder)
  Only MaxSim computation at query time (fast matrix ops)

Why it's more accurate than bi-encoder:
  Token-level matching captures fine-grained interaction
  "capital" in query matches "political center" in document via MaxSim

Latency: ~5ms vs ~50ms for cross-encoder; quality between bi and cross-encoder
Storage: large (128d per token × all document tokens stored)
```

### 3.5 Cohere Rerank API

```python
import cohere

co = cohere.Client(api_key="...")

def cohere_rerank(query: str, documents: list[str], top_k: int = 5):
    results = co.rerank(
        model="rerank-english-v3.0",
        query=query,
        documents=documents,
        top_n=top_k,
        return_documents=True
    )
    return [(r.document.text, r.relevance_score) for r in results.results]
```

Cohere Rerank 3 properties:
- Multilingual: 100+ languages in a single model
- Best-in-class managed reranking API
- ~100ms latency; $2/1000 queries
- No GPU needed (fully managed)

---

## 4. Architecture Diagram

### Two-Stage Retrieval Pipeline
```
User Query
    |
    v
[Stage 1: Bi-Encoder Retrieval]
  Query → embedding → ANN search
  Returns: top-100 candidates (high recall, moderate precision)
  Latency: ~20-50ms
    |
    v
[Stage 2: Cross-Encoder Reranking]
  For each of 100 candidates:
    [CLS] query [SEP] document [SEP] → relevance score
  Returns: top-5 reranked candidates (high precision)
  Latency: ~50-100ms
    |
    v
[LLM Generation]
  Uses only top-5 documents
  Total retrieval latency: ~150ms (stage 1 + stage 2)
```

### Bi-Encoder vs. Cross-Encoder Comparison
```
Bi-Encoder:
  Query ─[BERT]─> q_vec ─────┐
                              ├─> cosine_sim ─> score
  Doc   ─[BERT]─> d_vec ─────┘
  (separate encoding; interaction is vector dot product only)

Cross-Encoder:
  [CLS] Q1 Q2 Q3 [SEP] D1 D2 D3 D4 [SEP]
    └─────────────────────────────────────┘
              Full self-attention
                     |
                 [Dense] ─> relevance_score
  (joint encoding; every query token attends to every document token)

ColBERT (Late Interaction):
  [q1 q2 q3]  pre-compute query tokens at query time
  [d1 d2 d3]  pre-compute document tokens at index time
  Score: Σ_i max_j(qi · dj)  ← MaxSim operation
```

---

## 5. Real-World Examples

### Cohere Rerank in Enterprise RAG
- AI coding assistant: 200 code snippet candidates → cross-encoder reranks → top-5 for generation
- Legal document search: 100 retrieved clauses → reranker → top-3 most relevant clauses cited

### Retrieval-Augmented Legal AI (Harvey, Lexis+)
- Dense retrieval over millions of case law documents → cross-encoder reranker
- Reranker specifically fine-tuned on legal relevance judgments
- Without reranker: 65% relevant in top-5. With reranker: 92% relevant in top-5.

### OpenAI File Search (Assistants API)
- Vector search retrieval → embedding-based reranking (as of 2024)
- Applied to user-uploaded documents before generating responses
- Significantly reduced hallucination from irrelevant retrieved context

---

## 6. Tradeoffs

| Model | Latency (100 docs) | Quality | Cost | Notes |
|-------|-------------------|---------|------|-------|
| Bi-encoder (no reranker) | 0ms (already done) | Moderate | Free | Baseline |
| BGE-reranker-base | ~30ms GPU | Good | Self-hosted | Compact model |
| BGE-reranker-large | ~80ms GPU | Very good | Self-hosted | Best open source |
| ColBERT | ~5ms GPU | Good | Self-hosted | Best latency |
| Cohere Rerank 3 | ~100ms API | Best | $2/1K queries | Best multilingual |
| GPT-4o-mini as reranker | ~500ms API | Excellent | Expensive | Not recommended for this purpose |

---

## 7. When to Use / When NOT to Use

### Always Use Reranking When:
- LLM context is limited to top-5 to 10 documents
- Precision-critical applications (medical, legal, financial)
- The retrieval model's top-5 precision is below 85% on your eval set

### Skip Reranking When:
- Latency budget is under 100ms total
- Only 20-30 candidate documents (reranking all of them is fine as the "retrieval")
- The bi-encoder retrieval already achieves >90% precision@5 on your eval set
- Very high query volume where the added cost per query is prohibitive

### When Reranking Hurts:
- Candidate pool is too small (top-10): reranker has too little to work with, diminishing marginal value.
- Very long documents (over 512 tokens): most cross-encoders truncate; critical information may be beyond the token limit.
- Domain mismatch: a general-purpose reranker applied to specialized domain (medical, legal, code) without fine-tuning.

---

## 8. Common Pitfalls

**1. Retrieval top-K too small for reranking**
Reranking top-10 after retrieval provides minimal improvement over just using the retrieval top-5. The reranker needs at least 50-100 candidates to show significant precision improvement.
Fix: Always retrieve top-50 to top-100 as input to the reranker. The quality improvement from reranking drops sharply below 30 candidates.

**2. Cross-encoder truncating relevant content**
BGE-reranker-large has a 512-token limit. If your chunks are 1000 tokens, the reranker only sees the first 512 — potentially missing the relevant content in the second half.
Fix: Ensure chunk size is under the reranker's context limit. For 512-token rerankers, use 350-400 token chunks. If longer chunks are necessary, use a reranker with a longer context window (Cohere Rerank 3 supports 4096 tokens).

**3. Using reranker score as confidence score**
Cross-encoder scores are calibrated for ranking (relative comparison), not as absolute confidence levels. A score of 0.8 doesn't mean "80% probability of being relevant."
Fix: Do not threshold on reranker score for "is this relevant?" decisions. Use reranker scores for ranking only; use a separate relevance classifier if you need a confidence threshold.

**4. Reranking after filtering, with too few candidates**
After metadata filtering (date, department), only 15 documents remain. Reranking 15 documents is marginally valuable.
Fix: If the filtered pool is small (under 30 documents), skip the reranker and return all filtered documents directly to the LLM.

**5. Domain-mismatched reranker**
A general web-text cross-encoder reranker applied to medical literature doesn't understand that "MI" in a cardiac context means "myocardial infarction," not "Michigan" or "military intelligence."
Fix: Fine-tune the reranker on domain-specific relevance judgments (query, relevant_doc, irrelevant_doc triples) or use domain-specific reranker variants (BioMedBERT-based cross-encoder for medical).

**6. Not batching cross-encoder inference**
Running cross-encoder on one document at a time (100 serial API calls) instead of batching all 100 candidates together.
Fix: Batch all candidates in a single model forward pass with padding. This is 10-50× faster than serial inference.

---

## 9. Technologies & Tools

| Tool | Type | Notes |
|------|------|-------|
| **BGE-reranker-large** | Open source cross-encoder | Best open-source; BAAI; 512 token limit |
| **BGE-reranker-v2-m3** | Open source cross-encoder | Multilingual; longer context than v1 |
| **Cohere Rerank 3** | Managed API | Best multilingual; 4096 token limit; $2/1K queries |
| **ColBERT v2** | Late interaction | Best latency; good quality; large index size |
| **SPLADE** | Learned sparse + rerank | Combined sparse + reranking approach |
| **LlamaIndex SentenceTransformerRerank** | Reranking module | Wraps cross-encoder models; integrates with LlamaIndex |
| **LangChain CrossEncoderReranker** | Reranking module | Wraps HuggingFace cross-encoders |
| **FlashRank** | Lightweight reranker | Optimized for speed; good for resource-constrained deployments |
| **Jina Reranker** | Managed + open source | Multi-modal reranking; image + text |

---

## 10. Interview Questions with Answers

**Q: What is a cross-encoder reranker and when should you use it?**
A: A cross-encoder takes the query and a candidate document together as a single input sequence and outputs a direct relevance score using full self-attention across both texts. This joint encoding captures fine-grained query-document interactions that bi-encoders miss — "capital" in a query correctly matching "political center" in a document. Use it as a second stage after initial retrieval: bi-encoder retrieves top-100 candidates (fast, high recall); cross-encoder reranks to top-5 (slow but high precision). Adding reranking to an existing RAG pipeline typically improves precision@5 from 70-80% to 90%+. The cost is ~50-100ms additional latency and self-hosting or API charges for the reranker model.

**Q: Why can't you use a cross-encoder as the primary retriever?**
A: Cross-encoders require a forward pass for each (query, document) pair — O(N) passes for a corpus of N documents, each taking ~1ms on GPU. For 1M documents: 1M passes × 1ms = 1000 seconds per query — completely impractical. Cross-encoders cannot pre-compute document representations (unlike bi-encoders) because the document representation depends on the specific query. Bi-encoders pre-compute all document embeddings once at index time, then only compute one query embedding at query time + ANN search. The fundamental constraint is that cross-encoder relevance depends on the query, preventing pre-computation.

**Q: How does ColBERT's late interaction differ from both bi-encoder and cross-encoder?**
A: Bi-encoders produce a single vector per document (pre-computable, fast) but interaction is limited to a dot product. Cross-encoders produce joint query-document representations (very accurate) but can't pre-compute documents. ColBERT is a middle ground: documents are pre-encoded at the token level (one embedding per token, stored at index time), queries are encoded at query time, and relevance is computed via "MaxSim" — for each query token, find the maximum similarity to any document token, then sum these MaxSim scores. This token-level matching is more expressive than bi-encoder dot product, while document token encodings are pre-computable. Tradeoff: much larger index (128 floats per token instead of 768 for the full document) and more complex retrieval infrastructure.

**Q: What is the ideal number of candidates to retrieve before reranking?**
A: 50-100 candidates is the standard range. The reranker's value is highest when it has a rich candidate set to work with — at top-10, it barely has room to improve over retrieval order; at top-100, it has the full benefit. The tradeoff: larger candidate sets increase cross-encoder latency linearly (100 candidates × 1ms = 100ms). For retrieval pipelines where the top-100 retrieval is fast (20-30ms), the full 50-100 → rerank → top-5 pipeline runs in 100-150ms total, which is acceptable for most applications. If retrieval is slower (due to metadata filtering or large corpus), consider top-30 candidates to save reranking time.

**Q: How do you fine-tune a cross-encoder reranker for a specific domain?**
A: Fine-tuning requires relevance-labeled pairs: (query, relevant_document, irrelevant_document) triples. Collect these by: (1) domain expert annotation — 500-2000 triples is usually sufficient; (2) LLM-generated pairs — use GPT-4 to generate (query, relevant passage) pairs from domain documents, and use random passages as negatives; (3) human click feedback — clicks indicate relevance (implicit labeling). Fine-tuning: use a cross-encoder base model (BGE-reranker-base), apply contrastive loss (maximize score for positive, minimize for negative pairs), 2-5 epochs with low learning rate (1e-5). Validate: measure MRR@10 and NDCG@10 on a held-out domain-specific test set.

**Q: How does Cohere Rerank 3 compare to self-hosted cross-encoders?**
A: Cohere Rerank 3 advantages: best multilingual support (100+ languages), 4096-token context window (vs. 512 for BGE-reranker-large), no GPU infrastructure to manage, consistently strong performance on BEIR benchmark. Disadvantages: $2/1000 queries (becomes significant at high volume), API latency ~100ms, data privacy concerns (sending documents to external API), offline/air-gapped deployments not possible. Self-hosted BGE-reranker-large: free inference on owned GPU (~80ms on A10G), data stays on-premise, can be fine-tuned, 512-token limit. Decision: API if multilingual, long documents, or team lacks GPU; self-hosted if cost at scale, privacy, or fine-tuning is needed.

**Q: When does adding a reranker not improve quality?**
A: Three scenarios where reranking provides no meaningful improvement. First, the initial retrieval already has >90% precision@5 — the reranker can't improve what's already nearly perfect. Second, all retrieved candidates are highly relevant — the reranker just shuffles excellent results. Third, the candidate pool is too small (under 20-30 docs) — the reranker can't overcome fundamentally poor initial retrieval recall. Additionally, reranking hurts when: (1) the reranker's context window is shorter than the chunk length (relevant content truncated); (2) domain mismatch between reranker training and deployment; (3) reranking adds critical latency in real-time streaming scenarios.

**Q: How do you evaluate whether a reranker is working correctly?**
A: Build a labeled test set: 100-200 (query, document_pool, relevance_labels) triples where relevance_labels rank each document in the pool. Compute MRR@5 (Mean Reciprocal Rank at 5) and NDCG@5 (Normalized Discounted Cumulative Gain at 5) before and after reranking. MRR@5 = mean of 1/rank_of_first_relevant_doc across all queries. NDCG@5 weights positions logarithmically — a relevant doc at rank 1 is much better than at rank 5. Minimum acceptable improvement: reranking should improve NDCG@5 by at least 10-15% over bi-encoder retrieval alone. If improvement is smaller, either the bi-encoder is already very strong or the candidate pool is too small.

**Q: How does reranking interact with RAG's context window management?**
A: Reranking determines which documents appear in the LLM's context, and context window constraints determine how many documents can be included. The typical pipeline: retrieve top-100 → rerank → take top-5 to 10 for LLM context. The reranker's output count (5, 10) is chosen based on the LLM's context window and the chunk size. With 4K context window and 500-token chunks: room for ~6-7 chunks. With 128K context: room for ~200 chunks. Higher reranker output (more chunks) fills the context window — but more context is not always better; irrelevant chunks in context degrade generation quality even when the top chunks are relevant. The optimal number is empirically 3-10 chunks for most applications.

**Q: What is "reranking" in the context of RAG evaluation and why does it complicate evaluation?**
A: Reranking introduces a source of improvement that's orthogonal to embedding quality — a weak bi-encoder + strong reranker can outperform a strong bi-encoder without reranker. This complicates evaluation: the question is not just "is this embedding model good?" but "is this embedding model + reranker combination good?" Evaluation must measure the end-to-end pipeline, not just retrieval in isolation. Additionally, evaluation metrics can be misleading: recall@100 (before reranking) matters more than precision@100; the reranker handles the precision-5 optimization. Standard evaluation: measure recall@100 (retrieval quality — are relevant docs in the pool?) and then precision@5 after reranking (does the reranker surface the right ones from the pool?).

---

## 11. Best Practices

1. **Retrieve 50-100 candidates before reranking** — don't rerank top-10; the benefit diminishes sharply with small candidate pools.
2. **Ensure chunk size fits within reranker's context window** — silent truncation by the reranker is the most common failure mode; check your reranker's token limit.
3. **Batch all candidates in a single forward pass** — serial single-document inference is 10-50× slower than batched; always process all candidates together.
4. **Use domain-fine-tuned reranker for specialized domains** — medical, legal, and code domains benefit significantly from fine-tuned rerankers; general models underperform.
5. **Measure NDCG@5 and MRR@5 before and after adding reranker** — quantify the improvement; if less than 10%, the retrieval is already good or the candidate pool is too small.
6. **Monitor reranker latency separately** — cross-encoder latency should be tracked as a distinct component in your pipeline metrics; it's the second-most common latency bottleneck after LLM generation.
7. **Choose BGE-reranker for self-hosted English workloads, Cohere for multilingual** — these are the right defaults for their respective use cases.
