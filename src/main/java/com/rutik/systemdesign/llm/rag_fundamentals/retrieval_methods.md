# Retrieval Methods

## 1. Concept Overview

Retrieval is the step in RAG that finds relevant document chunks from an index given a user query. The quality of this retrieval step directly bounds the quality of the generated answer — no LLM can synthesize correct answers from irrelevant context. Three main retrieval paradigms exist: dense retrieval (semantic similarity using embeddings), sparse retrieval (keyword-based using inverted indices like BM25), and hybrid retrieval (combining both).

Each paradigm has systematic strengths and blind spots. Dense retrieval handles semantic paraphrase ("automobile" finds documents about "car") but misses rare proper nouns. Sparse retrieval excels at exact keyword matching (product IDs, regulation numbers, technical acronyms) but fails at semantic similarity. Hybrid retrieval — combining dense and sparse via Reciprocal Rank Fusion — consistently outperforms either alone across diverse query distributions.

---

## Intuition

> **One-line analogy**: Dense retrieval is a semantic search engine that understands meaning; sparse retrieval is Ctrl+F across all documents. Hybrid combines both.

**Mental model**: Dense retrieval maps query and documents into the same embedding space and finds nearest neighbors by cosine similarity. A query about "heart attack" retrieves documents about "myocardial infarction" because they're semantically close. BM25 gives this same document a low score — "myocardial infarction" doesn't contain "heart attack." Conversely, BM25 excels at finding all documents containing a regulation ID like "IEC 62443-2-1" — dense retrieval may miss it if the query phrasing doesn't match that exact notation. Hybrid combines both signals: neither suffers from the other's blind spot.

**Why it matters**: The choice of retrieval method determines which queries your RAG system can answer correctly. Dense-only fails for exact-match queries (proper nouns, IDs, codes); sparse-only fails for semantic queries (synonyms, paraphrases, multilingual). For production systems serving real users with diverse query types, hybrid retrieval is the correct default.

**Key insight**: Hybrid retrieval via RRF is both simple to implement (two separate retrievers + rank fusion formula) and consistently delivers 5-15% better recall@10 than either dense or sparse alone, with no additional model training.

---

## 2. Core Principles

- **Dense retrieval is semantic, not lexical**: A dense retriever compares meaning via embedding geometry, not words via term overlap.
- **Sparse retrieval is exact and interpretable**: BM25 gives a well-defined score based on term frequency and document frequency; easy to debug.
- **The two failure modes are orthogonal**: Dense fails on rare/exact terms; sparse fails on semantic paraphrases. Their weaknesses don't overlap.
- **RRF is position-based, not score-based**: RRF uses ranks, not raw scores, making it robust to different score scales from different retrievers.
- **Metadata filtering is complementary**: Filter by structured metadata (date, source, category) to scope retrieval before or after semantic/keyword search.

---

## 3. How It Works — Detailed Mechanics

### 3.1 Dense Retrieval (Bi-Encoder + ANN)

**Bi-encoder architecture:**
```
Query encoder:    query_text → dense vector q (d-dimensional)
Document encoder: doc_text   → dense vector d (d-dimensional)

Similarity score: cosine_similarity(q, d) = (q·d) / (||q|| × ||d||)

Training: contrastive learning on (query, positive_doc, negative_docs) triples
  Maximize cosine(query, positive) while minimizing cosine(query, negatives)
```

**Approximate Nearest Neighbor (ANN) search:**
```
Exact search: compute cosine similarity to all N vectors → O(N×d) per query
  → Too slow for N > 1M vectors

ANN with HNSW (Hierarchical Navigable Small World):
  Build: multi-layer graph where close vectors are connected
  Search: start at top layer (coarse), traverse to bottom layer (fine)
  Time complexity: O(log N) per query
  Recall: 95-99% (misses ~1-5% of true nearest neighbors)
  Trade-off: 5% recall loss, 1000× speed gain vs. exact search

Pinecone, Weaviate, Qdrant all use HNSW internally.
```

**Dense retrieval example:**
```python
from sentence_transformers import SentenceTransformer
import numpy as np

model = SentenceTransformer("BAAI/bge-base-en-v1.5")

def dense_retrieve(query: str, chunks: list[dict], top_k: int = 20):
    query_embedding = model.encode(query, normalize_embeddings=True)
    chunk_embeddings = np.array([c["embedding"] for c in chunks])

    # Cosine similarity (embeddings are normalized, so dot product = cosine)
    scores = chunk_embeddings @ query_embedding
    top_indices = np.argsort(scores)[-top_k:][::-1]
    return [(chunks[i], float(scores[i])) for i in top_indices]
```

### 3.2 Sparse Retrieval (BM25)

BM25 (Best Match 25) is the industry-standard sparse retrieval formula:

```
BM25(q, d) = Σ_{t ∈ q} IDF(t) × (tf(t,d) × (k1 + 1)) / (tf(t,d) + k1 × (1 - b + b × |d|/avgdl))

Where:
  IDF(t)    = log((N - df(t) + 0.5) / (df(t) + 0.5) + 1)
              N: total documents; df(t): documents containing term t
              Common terms (low IDF): low weight
              Rare terms (high IDF): high weight

  tf(t, d)  = term frequency of t in document d

  k1        = 1.2-2.0 (saturation parameter; controls tf scaling)
              High tf(t,d) provides diminishing returns beyond k1

  b         = 0.75 (length normalization; reduces score for long docs)
              b=1: full length normalization; b=0: no normalization

  |d|       = document length (tokens); avgdl = average document length
```

BM25 strengths:
```
Query: "ISO 27001 certification requirements"
  → BM25 finds all documents containing "ISO", "27001", "certification", "requirements"
  → Rare term "27001" has very high IDF → heavily weighted
  → Dense retrieval might miss documents that use "information security standard"
     without the exact "27001" notation

Query: "Find error code E-2045 in the system logs"
  → BM25 matches "E-2045" exactly (rare term, high IDF)
  → Dense retrieval struggles with arbitrary identifier strings
```

```python
from rank_bm25 import BM25Okapi

def build_bm25_index(chunks: list[str]) -> BM25Okapi:
    tokenized_chunks = [chunk.lower().split() for chunk in chunks]
    return BM25Okapi(tokenized_chunks)

def sparse_retrieve(query: str, bm25: BM25Okapi,
                    chunks: list[str], top_k: int = 20):
    tokenized_query = query.lower().split()
    scores = bm25.get_scores(tokenized_query)
    top_indices = np.argsort(scores)[-top_k:][::-1]
    return [(chunks[i], float(scores[i])) for i in top_indices]
```

### 3.3 Hybrid Retrieval with RRF

Reciprocal Rank Fusion combines rankings from multiple retrieval systems:

```
RRF formula:
  RRF_score(doc, k=60) = Σ_{r ∈ retrievers} 1 / (k + rank_r(doc))

  k = 60 (constant that dampens the impact of top-ranked documents)

Example:
  doc_A ranked 1st in dense, 5th in BM25:
    RRF(doc_A) = 1/(60+1) + 1/(60+5) = 0.01639 + 0.01538 = 0.03177

  doc_B ranked 3rd in dense, 2nd in BM25:
    RRF(doc_B) = 1/(60+3) + 1/(60+2) = 0.01587 + 0.01613 = 0.03200

  doc_B ranks higher despite not being #1 in either — consistently high across both
```

```python
def reciprocal_rank_fusion(
    dense_results: list[tuple],    # [(doc, score), ...]
    sparse_results: list[tuple],   # [(doc, score), ...]
    k: int = 60
) -> list[tuple]:
    """
    Combine dense and sparse retrieval results using RRF.
    Returns merged list sorted by RRF score (descending).
    """
    doc_scores = {}

    for rank, (doc, _) in enumerate(dense_results):
        doc_id = doc.id
        if doc_id not in doc_scores:
            doc_scores[doc_id] = {"doc": doc, "rrf": 0.0}
        doc_scores[doc_id]["rrf"] += 1.0 / (k + rank + 1)

    for rank, (doc, _) in enumerate(sparse_results):
        doc_id = doc.id
        if doc_id not in doc_scores:
            doc_scores[doc_id] = {"doc": doc, "rrf": 0.0}
        doc_scores[doc_id]["rrf"] += 1.0 / (k + rank + 1)

    sorted_docs = sorted(doc_scores.values(),
                         key=lambda x: x["rrf"], reverse=True)
    return [(item["doc"], item["rrf"]) for item in sorted_docs]
```

### 3.4 Weighted Score Combination (Alternative to RRF)

```
Linear combination:
  hybrid_score(doc) = α × normalize(dense_score) + (1-α) × normalize(bm25_score)
  α = 0.5 (equal weight; tune based on eval)

Problem: dense scores (cosine similarity: [-1, 1]) and BM25 scores
  (unbounded positive) are on different scales
  → Must normalize each independently before combining

Normalization:
  dense_norm  = (score - min) / (max - min)     across all candidates
  sparse_norm = (score - min) / (max - min)     across all candidates

When to use weighted combination vs. RRF:
  RRF: when you don't want to tune α; more robust to score distribution differences
  Weighted: when you have query-type-dependent weights (factual → more BM25; semantic → more dense)
```

### 3.5 Metadata Filtering

Scope retrieval to a subset of the index using structured metadata:

```python
# Pinecone metadata filter syntax
results = pinecone_index.query(
    vector=query_embedding,
    filter={
        "document_date": {"$gte": "2024-01-01"},
        "document_type": {"$in": ["policy", "regulation"]},
        "department": {"$eq": "legal"}
    },
    top_k=20
)

# Weaviate metadata filter
results = (
    weaviate_client.query
    .get("Document", ["content", "source", "date"])
    .with_near_vector({"vector": query_embedding})
    .with_where({
        "operator": "And",
        "operands": [
            {"path": ["date"], "operator": "GreaterThanEqual", "valueText": "2024-01-01"},
            {"path": ["department"], "operator": "Equal", "valueText": "legal"}
        ]
    })
    .with_limit(20)
    .do()
)
```

Metadata filtering is often more impactful than retrieval algorithm choice for precision-sensitive applications. A filter for `document_date >= 2024-01-01` eliminates all stale documents regardless of embedding similarity.

---

## 4. Architecture Diagram

### Hybrid Retrieval Pipeline
```
User Query
    |
    +-----------> [Text Embedding]      → Dense Vector
    |                    |
    |             [ANN Search]          → Top-100 dense candidates
    |             (HNSW, cosine sim)
    |
    +-----------> [BM25 Tokenization]   → Token list
                         |
                  [BM25 Scoring]        → Top-100 sparse candidates
    |
    v
[RRF Fusion]
  For each document in either result set:
    score = Σ 1/(60 + rank_dense) + Σ 1/(60 + rank_sparse)
    |
    v
[Merged top-100 by RRF score]
    |
    v
[Metadata Filter]   ← applied pre- or post-ANN
  Filter by date, source, category
    |
    v
[Reranker]          ← see reranking.md
  Cross-encoder on top-100 → top-5
    |
    v
Final Context → LLM Generation
```

### HNSW Graph Structure
```
Layer 2 (coarse):   A ----- E
                    |
Layer 1 (medium):   A - B - E - F
                    |   |
Layer 0 (fine):     A-B-C-D-E-F-G-H

Search for Q:
  Start at layer 2: navigate to closest node (E)
  Drop to layer 1: navigate from E to closest (F)
  Drop to layer 0: navigate from F to exact nearest neighbors
  Result: approximate nearest neighbors in O(log N) steps
```

---

## 5. Real-World Examples

### Elasticsearch Hybrid Search (ELSER + BM25)
- Elasticsearch combines BM25 (inverted index) with ELSER (learned sparse embedding)
- RRF built into Elasticsearch `rrf` query type (added in 8.8)
- Used by large e-commerce platforms for product search

### Weaviate Native Hybrid Search
- Weaviate's `hybrid` parameter combines dense BM25 + vector search internally
- `alpha` parameter controls the balance: 0 = pure sparse, 1 = pure dense
- Automatic RRF fusion; no manual merge step needed

### Pinecone Hybrid Search
- Requires separate dense and sparse indices; RRF implemented client-side
- Sparse index uses Pinecone's built-in BM25; dense uses any embedding
- Widely deployed in enterprise RAG systems

---

## 6. Tradeoffs

| Method | Recall (semantic) | Recall (keyword) | Latency | Setup Complexity | Index Size |
|--------|------------------|-----------------|---------|-----------------|------------|
| Dense only | High | Low | Fast | Medium | Medium |
| Sparse (BM25) only | Low | High | Fast | Low | Small |
| Hybrid (RRF) | High | High | Moderate | Medium | Medium |
| Hybrid + metadata filter | High | High | Moderate | Medium | Medium |
| Hybrid + reranker | Best | Best | Slower | High | Medium |

---

## 7. When to Use / When NOT to Use

### Use Dense-Only When:
- All queries are semantic/conceptual (no exact keyword requirements)
- Infrastructure complexity is a constraint
- Query vocabulary aligns well with document vocabulary

### Use BM25-Only When:
- All queries contain exact terms (ID lookups, code search, log analysis)
- Latency requirements are extremely strict
- Semantic understanding not needed (database-style lookups)

### Use Hybrid When:
- Query distribution is mixed (both semantic and keyword queries)
- Production system serving real users with diverse query types
- Quality is the priority and infrastructure can handle two retrieval paths

---

## 8. Common Pitfalls

**1. Dense search on exact-match queries without BM25**
A user queries for a specific regulation number ("21 CFR 820.30"). Dense search may return documents about medical device regulations in general, not this specific section.
Fix: Use BM25 for exact-match recall; hybrid fusion ensures both signals contribute.

**2. BM25 without normalization for different document lengths**
Long documents have higher term frequencies and longer documents tend to dominate BM25 results even if less relevant.
Fix: BM25's `b` parameter (0.75 default) handles length normalization; ensure it's not set to 0.

**3. Mixing scores from dense and sparse without normalization**
Raw cosine similarity scores (e.g., 0.85) and raw BM25 scores (e.g., 12.3) are on incomparable scales; combining them directly produces biased results.
Fix: Use RRF (rank-based, scale-independent) or normalize each score set to [0,1] before linear combination.

**4. Metadata filters applied after full ANN search**
Searching all N vectors and then filtering by metadata wastes computation.
Fix: Apply metadata pre-filter at the ANN search level (Pinecone, Weaviate, Qdrant all support filtered ANN). Filters during search, not after, reduces wasted computation.

**5. Using top-10 without a reranker**
Initial retrieval returns top-10, but the 3-5 most relevant chunks may be ranked 4th-10th by the bi-encoder. Without reranking, the LLM sees lower-quality context.
Fix: Retrieve top-100 candidates, then rerank to top-5. See reranking.md for reranker choices.

**6. No query preprocessing**
Queries with stopwords ("what is the best way to handle...") or typos produce poor BM25 results.
Fix: Apply query preprocessing: lowercase, remove stopwords for BM25 (not dense), spell-check for critical applications.

---

## 9. Technologies & Tools

| Tool | Type | Notes |
|------|------|-------|
| **Pinecone** | Vector DB (dense) | Best managed option; native sparse index support |
| **Weaviate** | Hybrid DB | Built-in hybrid search; `alpha` parameter for balance |
| **Qdrant** | Vector DB | Fast; Rust-based; native hybrid support in v1.7+ |
| **Elasticsearch** | Hybrid DB | ELSER + BM25; enterprise-grade; RRF query support |
| **OpenSearch** | Hybrid DB | Fork of Elasticsearch; neural search plugin for hybrid |
| **pgvector** | PostgreSQL extension | Dense search only; pair with pg_trgm for text search |
| **rank-bm25** | Python BM25 | Simple BM25 for prototyping; not production-scale |
| **Chroma** | Vector DB | Good for local dev; no native hybrid |
| **FAISS** | ANN library | Gold standard for offline ANN; not a managed DB |

---

## 10. Interview Questions with Answers

**Q: What is hybrid search and why does it outperform dense-only retrieval?**
A: Hybrid search combines dense (vector/semantic) retrieval and sparse (BM25/keyword) retrieval. Dense retrieval handles semantic similarity — a query about "car purchase" retrieves documents about "automobile buying" because they're close in embedding space. BM25 handles exact keyword matching — product SKUs, regulation numbers, proper names that dense models don't reliably cluster. They have orthogonal failure modes: dense misses exact-match queries; BM25 misses semantic paraphrases. Combining via RRF consistently achieves 5-15% higher recall@10 than either alone. In practice, real user query distributions include both types — hybrid is the correct default for production.

**Q: How does Reciprocal Rank Fusion work and why is it preferred over score combination?**
A: RRF computes a fused score using position ranks: `score(doc) = Σ 1/(k + rank_i)` for each retriever i, where k=60 is a smoothing constant. Documents ranked high by multiple retrievers accumulate high RRF scores. RRF is preferred over weighted score combination for two reasons: (1) RRF is scale-invariant — dense scores (cosine similarity in [-1,1]) and BM25 scores (unbounded positive) are incomparable raw values; RRF uses only rank positions, avoiding the need for normalization; (2) RRF is parameter-free — no α weight to tune; it's robust to the score distributions of each retriever. Linear score combination requires careful per-dataset α tuning and normalization.

**Q: What is HNSW and how does it enable fast ANN search?**
A: HNSW (Hierarchical Navigable Small World) is the dominant ANN algorithm used in vector databases. It builds a multi-layer graph where layer 0 contains all vectors connected to their nearest neighbors, and higher layers contain progressively fewer nodes (a subset sampled as "shortcuts"). Search starts at the top layer (coarse navigation) and progressively descends to layer 0 (fine-grained nearest neighbor search), following the best edge at each step. This hierarchical structure enables O(log N) search time vs. O(N) for brute force. HNSW achieves 95-99% recall (missing ~1-5% of true nearest neighbors) — acceptable for RAG since the reranker compensates for the small number of missed retrievals.

**Q: What is the difference between pre-filtering and post-filtering in vector search?**
A: Pre-filtering applies metadata constraints before or during the ANN search: the index only considers vectors that satisfy the filter condition. Post-filtering retrieves the top-K vectors without constraints, then filters by metadata and potentially fetches more until K relevant results are found. Pre-filtering is more efficient (doesn't waste ANN computation on filtered-out vectors) but requires vector DB support for filtered ANN (Pinecone, Weaviate, Qdrant support this). Post-filtering is simpler but inefficient when the filter eliminates many results — if 90% of chunks are filtered out, you may need to retrieve top-10K to get 100 filtered results. For precision-critical applications with strict metadata scoping, pre-filtering is strongly preferred.

**Q: How do you handle the case where BM25 needs to match technical identifiers like "E-2045" or "ISO 27001"?**
A: Technical identifiers are high-IDF terms (they appear in very few documents) and should score very high in BM25. The main risk is tokenization: a tokenizer that splits "E-2045" into ["E", "2045"] or "ISO-27001" into ["ISO", "27001"] reduces the identifier's specificity to common substrings. Fix: (1) Use a tokenizer that preserves hyphenated terms and alphanumeric identifiers as single tokens; (2) Index both the original identifier and its hyphen-normalized form; (3) Use phrase matching in BM25 to require the full identifier as a sequence, not just individual tokens. Elasticsearch's `match_phrase` query handles this correctly.

**Q: What are the tradeoffs between BM25 and a learned sparse retrieval method like SPLADE?**
A: Traditional BM25 uses raw term frequencies and IDF; it's non-learned and requires exact keyword matches. SPLADE (Sparse Lexical and Expansion model) is a learned sparse retrieval method that expands both query and document with related terms (a document about "cardiac arrest" gets "heart attack" added to its sparse representation), enabling semantic matching while maintaining the efficiency of inverted index retrieval. SPLADE vs. BM25: SPLADE achieves ~20-30% better recall on semantic queries while maintaining BM25-level speed; requires a fine-tuned SPLADE model; needs the index to store expanded sparse vectors (larger than BM25 inverted index). In practice: for RAG systems, SPLADE + dense hybrid often outperforms BM25 + dense hybrid, at the cost of a learned sparse model.

**Q: How does metadata filtering interact with hybrid retrieval?**
A: Metadata filtering scopes the retrieval space; hybrid search operates within that scope. The order matters: pre-filter by metadata first (most vector DBs support this), then run both dense ANN and BM25 within the filtered subset. If the filtered subset is small (e.g., 100 documents after date filter), BM25 over 100 documents is trivially fast and ANN loses some quality (HNSW is less effective on small subsets — exact search may be preferable). A common pattern for enterprise RAG: strong metadata pre-filtering (date range, department, document type) to reduce to a manageable subset, then hybrid search within that subset, then reranking. The metadata filter does more heavy lifting than people expect — often reducing 10M vectors to 10K relevant ones.

**Q: When should you NOT use hybrid search?**
A: Hybrid retrieval adds operational complexity: two retrieval paths, a merge step, and potentially two indices. It's not justified when: (1) Query distribution is entirely semantic (a semantic-only product discovery system); (2) Query distribution is entirely exact-match (a database lookup system); (3) Infrastructure constraints don't allow two retrieval systems; (4) The corpus is very small (under 10K chunks) where brute-force exact similarity search is fast enough. Also avoid hybrid when the BM25 index is of poor quality (badly tokenized, not maintained). A good dense retriever often beats a poorly configured BM25+dense hybrid.

**Q: How do you tune the `k` parameter in RRF?**
A: The RRF constant k (default 60) controls how much the top ranks are boosted relative to lower ranks. k=60 means a document ranked 1st gets score 1/(60+1) ≈ 0.016 and rank 100th gets 1/160 ≈ 0.006 — a ~2.7× boost for the top rank. Smaller k (e.g., k=10) amplifies the top-rank advantage: rank 1st gets 0.09 vs rank 100th gets 0.009 — a 10× boost. Larger k (e.g., k=100) reduces the advantage. For most RAG applications, k=60 is the right default — the original RRF paper showed k=60 was robust across many TREC retrieval tasks. Only tune k if your eval shows a strong signal; otherwise the default is well-justified.

**Q: What is the impact of query length on dense vs. sparse retrieval quality?**
A: Short queries (1-3 words): BM25 works well (few high-IDF terms to match); dense retrieval may underperform because there's insufficient context for a meaningful embedding. Long queries (20+ words): dense retrieval excels because the embedding captures the full semantic intent; BM25 may be noisy (too many terms, including low-IDF stopwords, that dilute the signal). For RAG systems where users write full-sentence questions ("What are the requirements for ISO certification of a medical device manufacturer?"), dense retrieval is the primary contributor and BM25 adds value primarily for rare terms in the query.

---

## 11. Best Practices

1. **Default to hybrid retrieval in production** — pure dense or pure BM25 consistently underperforms hybrid on diverse real-world query distributions.
2. **Use RRF as the fusion method** — scale-invariant, parameter-free, and works well out of the box.
3. **Apply metadata pre-filters** — filter at the index level before ANN search; post-filtering wastes computation.
4. **Retrieve 50-100 candidates** — not top-5; feed a large candidate set to the reranker for final selection.
5. **Test both dense and sparse separately on your query distribution** — this diagnoses which retrieval mode is the bottleneck and whether hybrid helps.
6. **Monitor per-query retrieval scores** — if top-1 cosine similarity is below 0.6 for many queries, the embedding model or chunking is misaligned with your query distribution.
7. **Use BM25 to validate dense retrieval** — if BM25 consistently returns better results for certain query types, those queries should be weighted toward sparse in your hybrid.
