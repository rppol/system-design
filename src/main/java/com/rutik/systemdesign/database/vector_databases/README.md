# Vector Databases

## 1. Concept Overview

Vector databases store and query high-dimensional vector embeddings — dense numerical representations of text, images, audio, and other data produced by machine learning models. They enable semantic similarity search: finding items that are conceptually similar even when they share no keywords. This is the core technology behind RAG (Retrieval-Augmented Generation), semantic search, recommendation systems, and image search.

---

## 2. Intuition

A text embedding maps meaning to position in high-dimensional space. "King minus Man plus Woman equals Queen" is the classic demonstration: vector arithmetic preserves semantic relationships. Two documents about database indexing will have embeddings close together in this space, even if they use different words.

- **Key insight**: Exact nearest-neighbor search in 1536 dimensions over 100M vectors requires comparing against every vector — O(n) operations. ANN (Approximate Nearest Neighbor) algorithms like HNSW achieve O(log n) time with ~95%+ recall via hierarchical graph structures.

---

## 3. Core Principles

### Vector Embeddings

```
Text → Embedding Model → Dense Vector

"The database uses B+tree indexing"
           ↓ (text-embedding-3-small, OpenAI)
[0.023, -0.145, 0.892, ..., -0.034]  ← 1536-dimensional vector

Dimensions:
  OpenAI text-embedding-3-small:  1536 dimensions
  OpenAI text-embedding-3-large:  3072 dimensions
  OpenAI ada-002:                 1536 dimensions (older)
  Sentence-transformers/all-MiniLM-L6-v2: 384 dimensions
  Google text-embedding-004:      768 dimensions
  Cohere embed-v3:                1024 dimensions

Memory per vector:
  1536 dimensions × 4 bytes (float32) = 6144 bytes ≈ 6KB per vector
  1M vectors × 6KB = 6GB RAM
  100M vectors × 6KB = 600GB RAM (too large for single node → distributed or quantization)
```

### Similarity Metrics

```
Cosine Similarity: measures angle between vectors (ignores magnitude)
  sim(a, b) = (a · b) / (||a|| × ||b||)
  Range: [-1, 1], 1 = identical direction, 0 = orthogonal, -1 = opposite
  Use: text embeddings (OpenAI, Cohere) — magnitude is not meaningful, only direction

Dot Product: similar to cosine but magnitude matters
  sim(a, b) = a · b = Σ(aᵢ × bᵢ)
  Use: when embeddings are normalized to unit length (dot product = cosine similarity)
       OpenAI recommends dot product for normalized embeddings (fastest computation)

Euclidean Distance (L2): straight-line distance in vector space
  dist(a, b) = √(Σ(aᵢ - bᵢ)²)
  Use: image embeddings, embeddings where magnitude matters
  Smaller = more similar (opposite convention to similarity scores)

Note: for normalized vectors (||v|| = 1), cosine similarity = dot product
      OpenAI embeddings are normalized → use dot product (avoids division)
```

---

## 4. Types / Architectures / Strategies

### HNSW (Hierarchical Navigable Small World)

```
HNSW is a multi-layer graph where:
- Layer 0 (bottom): contains ALL nodes, most densely connected
- Layer 1: subset of nodes (logarithmic reduction)
- Layer 2: smaller subset
- ...
- Top layer: very few nodes (starting point for search)

Construction (index building):
For each new vector:
  1. Randomly select max layer for this vector (exponential distribution, most go to L0)
  2. Enter from top layer, find nearest neighbor (ef_construction candidate set)
  3. For each layer from top to bottom: connect to M nearest neighbors (M=16 default)

Search (query):
  1. Enter at top layer, greedily navigate to the nearest neighbor
  2. Descend to lower layer, continue navigating
  3. At L0: explore ef candidates, return the closest k

Parameters:
  ef_construction=200 (build): larger = better recall, slower build
  ef=50 (search):              larger = better recall, slower search
  M=16 (connections per layer): larger = better recall, more memory

Performance:
  Build time: O(N × M × log N) — slow for large datasets
  Query time: O(log N) — fast at query time
  Memory: O(N × M) — extra edges stored in graph
  Typical recall@10: 95-99% vs exact search
```

### IVF (Inverted File Index)

```
IVF clusters vectors into K groups (Voronoi cells) using K-means.
Each cluster has a centroid vector.

Index:
  centroid_1 → [vectors in cluster 1]
  centroid_2 → [vectors in cluster 2]
  ...

Search:
  1. Find the nprobe closest centroids to the query vector
  2. Search only those nprobe clusters (linear scan within each)
  3. Return global top-k

Parameters:
  nlist = K = number of clusters. Typical: sqrt(N) to 16*sqrt(N)
    For 1M vectors: nlist=1000-4000
  nprobe = number of clusters to search.
    nprobe=1: fastest, lowest recall
    nprobe=nlist: exact search (defeats purpose)
    nprobe=32: good balance, ~95% recall for typical data
```

### Product Quantization (PQ) — Compression

```
Compresses 1536-dim float32 vectors (6KB each) to ~64 bytes per vector.

Method:
1. Split the 1536-dim vector into M=96 sub-vectors of 16 dims each
2. For each sub-space: run k-means with k=256 centroids
3. For each sub-vector, store only the centroid ID (1 byte per sub-space)
4. Reconstruction: look up centroid for each sub-space

Compression: 1536 × 4 bytes → M=96 × 1 byte = 6144 bytes → 96 bytes (64x compression)
Recall loss: ~5-15% at k=256 centroids per sub-space

IVF+PQ: combine for large-scale (>100M vectors)
  - IVF partitions into clusters (coarse quantization)
  - PQ compresses within each cluster (fine quantization)
  - Query: O(nprobe × cluster_size × M) operations (fast, mostly integer ops)
```

### pgvector — Vector Search in PostgreSQL

```sql
-- Extension setup:
CREATE EXTENSION vector;

-- Table with vector column:
CREATE TABLE documents (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT,
    embedding   vector(1536),  -- 1536-dimensional vector
    tenant_id   BIGINT,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- IVFFlat index (faster to build, good for large datasets):
CREATE INDEX idx_documents_embedding_ivf
ON documents USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
-- lists = number of clusters (default 100; recommend sqrt(N) for up to 1M rows)
-- After building: SET ivfflat.probes = 10 (default 1) for better recall

-- HNSW index (better recall, slower to build, more memory):
CREATE INDEX idx_documents_embedding_hnsw
ON documents USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
-- m = max connections per layer (16 default)
-- ef_construction = candidate set size during build (64 default)

-- Query: semantic search
SELECT id, content, 1 - (embedding <=> $1) AS similarity
FROM documents
WHERE tenant_id = $2  -- Pre-filter by tenant
ORDER BY embedding <=> $1  -- <=> = cosine distance operator
LIMIT 10;

-- Operators:
-- <=>   cosine distance   (1 - cosine_similarity)
-- <->   Euclidean distance
-- <#>   negative dot product (for dot product similarity: ORDER BY embedding <#> query_vec)
```

### Vector Database Options

| Database | Architecture | Best For | Max Scale |
|----------|-------------|---------|-----------|
| pgvector | PostgreSQL extension | SQL integration, ACID, < 10M vectors | 100M+ with HNSW |
| Pinecone | Managed, proprietary | Serverless, no-ops | Billions |
| Weaviate | Open-source, modular | Multi-modal, GraphQL | Billions (distributed) |
| Qdrant | Rust-based, open-source | High performance, filtering | Billions (distributed) |
| Milvus | Distributed, open-source | Large-scale production | Trillions |
| Chroma | Embedded, simple | Prototyping, small datasets | Millions |
| Redis | RedisVL extension | Low-latency cache + vector | Millions |

---

## 5. Architecture Diagrams

```
HNSW GRAPH STRUCTURE:

Layer 2:  [A] ─────────────────── [G]
                \                 /
Layer 1:  [A] ─ [C] ─ [E] ─── [G] ─ [I]
               /   \         /
Layer 0:  [A][B][C][D][E][F][G][H][I][J]
          (all vectors, dense connections)

Search for query Q:
1. Enter at Layer 2, nearest: G
2. Descend to Layer 1 at G, navigate: G → I → G (G is nearest at L1)
3. Descend to Layer 0 at G, explore ef=50 candidates:
   [G, H, I, F, E] → sorted by distance to Q → return top k

RAG PIPELINE WITH VECTOR DATABASE:

User Query
    ↓ (embedding model)
Query Vector (1536 dims)
    ↓ (ANN search)
Top-K Documents (k=5-10)
    ↓ (reranking, optional)
Top-N Reranked (N=3-5)
    ↓ (inject into prompt)
LLM → Answer
```

---

## 6. How It Works — Detailed Mechanics

### Hybrid Search (Vector + Keyword)

Pure vector search has limitations: rare entity names (product IDs, proper nouns) may not be well-represented in embedding space. Hybrid search combines vector similarity with BM25 keyword scoring.

```python
# Hybrid search in Elasticsearch/OpenSearch:
{
  "query": {
    "bool": {
      "should": [
        {
          "knn": {
            "field": "embedding",
            "query_vector": [0.023, -0.145, ...],
            "k": 50,
            "num_candidates": 100
          }
        },
        {
          "match": {
            "content": "database B+tree indexing"
          }
        }
      ]
    }
  }
}
# Result: RRF (Reciprocal Rank Fusion) merges both result lists
```

```python
# Qdrant: built-in hybrid search (sparse + dense)
client.search(
    collection_name="documents",
    query_vector=NamedVector(name="dense", vector=query_embedding),
    query_sparse_vector=NamedSparseVector(
        name="sparse",
        vector=SparseVector(indices=[1, 2, 3], values=[0.1, 0.5, 0.3])
    ),
    limit=10,
)
```

### Multi-Tenancy in Vector Databases

```sql
-- pgvector: filter by tenant_id (pre-filter, most efficient)
SELECT id, content, embedding <=> $query_vector AS distance
FROM documents
WHERE tenant_id = $1  -- Filter BEFORE ANN search narrows candidates
ORDER BY embedding <=> $query_vector
LIMIT 10;
-- IMPORTANT: index must be on embedding column; tenant_id filter applied via PostgreSQL WHERE
-- For large tables: composite index or partial index per tenant

-- Pinecone: namespace isolation
index.upsert(vectors=[...], namespace=f"tenant_{tenant_id}")
results = index.query(vector=query_vector, top_k=10, namespace=f"tenant_{tenant_id}")

-- Weaviate: multi-tenancy mode (each tenant gets isolated shard)
client.collections.create("Document", multi_tenancy_config=Configure.multi_tenancy(enabled=True))
client.collections.get("Document").with_tenant("tenant-42").query.near_vector(...)

-- Qdrant: per-tenant collections or filtered search
client.search(
    collection_name="documents",
    query_vector=query_embedding,
    query_filter=Filter(must=[FieldCondition(key="tenant_id", match=MatchValue(value=tenant_id))]),
    limit=10,
)
```

---

## 7. Real-World Examples

- **Notion AI**: Semantic search across user notes using vector embeddings. pgvector for smaller workloads, dedicated vector DB for scale.
- **GitHub Copilot**: Retrieves relevant code snippets from the repository using code embeddings (CodeBERT). Repository-scale vector search to provide context.
- **Spotify**: Audio embeddings for music recommendation — songs with similar sound are near in embedding space.
- **Pinterest**: Image embeddings for visual similarity search (find similar products from photos).
- **Airbnb**: Listing embeddings combining text descriptions and image features for semantic property recommendations.

---

## 8. Tradeoffs

| Approach | Recall | Query Speed | Memory | Build Speed | Best For |
|----------|--------|-------------|--------|-------------|---------|
| Exact (brute force) | 100% | O(N) slow | O(N) | Instant | < 100K vectors |
| HNSW | 95-99% | O(log N) fast | O(N × M) high | Slow | 1M-10B vectors, high recall |
| IVF | 90-97% | O(K + nprobe×M) | O(N) medium | Fast | 1M-1B vectors, large scale |
| IVF+PQ | 80-95% | Very fast | O(N × M/64) low | Medium | 100M+ vectors, memory-limited |
| Exact + GPU | 100% | Very fast (GPU) | GPU RAM | Instant | < 10M, GPU available |

---

## 9. When to Use / When NOT to Use

**Use pgvector when**:
- Vectors < 10M (HNSW) or < 100M (IVFFlat with tuning)
- Need to join with relational data (users, products, permissions)
- ACID compliance required (vector + metadata atomicity)
- Team already uses PostgreSQL
- Cost-sensitive (no additional infrastructure)

**Use dedicated vector database (Pinecone, Qdrant, Weaviate) when**:
- Vectors > 50M
- Team wants managed service (no ops overhead)
- Need advanced features (multi-modal, automatic embedding, graph+vector)
- Performance is critical at scale

**Avoid vector databases when**:
- You just need keyword search (BM25 is better)
- All queries use exact filters with no semantic component
- Dataset is small (< 10K vectors) — brute force is fast enough

---

## 10. Common Pitfalls

**Pitfall 1: Using wrong similarity metric for the model**
OpenAI embeddings are normalized to unit length. Using Euclidean distance (L2) instead of cosine similarity: technically equivalent for normalized vectors, but if the implementation doesn't normalize, L2 gives wrong results. Always check the embedding model documentation. OpenAI recommends cosine similarity; `<=>` in pgvector = cosine distance.

**Pitfall 2: Not setting ivfflat.probes for recall**
```sql
-- Default: ivfflat.probes = 1 (searches only 1 cluster)
-- For 100 clusters (lists=100): probes=1 means 1% of vectors searched
-- Recall at probes=1: ~70% (poor)
-- Fix:
SET ivfflat.probes = 10;  -- Search 10% of clusters, recall ~95%
-- Set per-session or globally: ALTER SYSTEM SET ivfflat.probes = 10;
```

**Pitfall 3: HNSW index build OOM**
HNSW index build requires all vectors in memory simultaneously (at M connections per node). For 10M vectors at 1536 dimensions: 10M × (6KB data + 16 connections × 8 bytes) ≈ 61GB RAM just for index build. Fix: use IVFFlat (can build incrementally) or increase server RAM. In pgvector, HNSW build memory can be reduced with `maintenance_work_mem`.

**Pitfall 4: No chunking strategy for long documents**
Embedding models have token limits (8191 tokens for OpenAI ada-002). Long documents must be chunked. Naive fixed-size chunking splits mid-sentence → poor embeddings. Fix: semantic chunking (split at paragraph or sentence boundaries), sliding window chunking (chunks with 20% overlap to preserve context across boundaries).

**Pitfall 5: Embedding model mismatch**
Query embedded with model A, documents embedded with model B → completely different embedding space → no meaningful similarity. Always use the same embedding model for all data in the same collection. When upgrading embedding models: re-embed ALL documents before switching query embedding.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| pgvector | PostgreSQL vector extension (HNSW + IVFFlat) |
| Pinecone | Managed vector database (serverless, pod-based) |
| Weaviate | Open-source vector DB with modules (OpenAI, Cohere auto-embedding) |
| Qdrant | Rust-based, high-performance, rich filtering |
| Milvus | Distributed vector database for billion-scale |
| Chroma | Embedded vector DB for prototyping and development |
| FAISS (Facebook AI) | Research library for ANN algorithms (CPU + GPU) |
| Annoy (Spotify) | Tree-based ANN, read-only index |
| LangChain VectorStores | Abstraction layer over multiple vector DBs |
| LlamaIndex VectorStoreIndex | Index abstraction for RAG pipelines |

---

## 12. Interview Questions with Answers

**Q: How does HNSW achieve sub-linear query time?**
HNSW builds a multi-layer graph where higher layers have fewer nodes and longer connections ("highways"), lower layers have more nodes and shorter connections. Search starts at the top layer (few nodes) and greedily navigates toward the query vector. At each layer, the algorithm moves to the current best neighbor, then descends to the next layer. This hierarchical approach: at the top layer, large steps eliminate most of the search space. At lower layers, fine-grained navigation within the relevant region. The total path length through layers is O(log N) — logarithmic in the number of vectors. The ef parameter controls how many candidates are tracked during descent — larger ef = better recall at cost of more computation.

**Q: What is the recall-vs-speed tradeoff in ANN search?**
ANN (Approximate Nearest Neighbor) algorithms sacrifice some recall (percentage of true nearest neighbors returned) for faster search. For HNSW: `ef` parameter controls this tradeoff at query time — ef=10 (fast, ~90% recall) vs ef=200 (slow, ~99% recall). For IVF: `nprobe` controls how many clusters are searched — nprobe=1 (~70% recall) vs nprobe=100 (near-exact, very slow). In practice for RAG: 95% recall is sufficient — missing 5% of relevant chunks rarely affects answer quality significantly. For medical or legal use cases requiring near-perfect recall: increase ef/nprobe or use exact search (small enough datasets). Benchmark your specific data and embedding model — recall degrades differently depending on data distribution.

**Q: How do you handle multi-tenancy in a vector database?**
Three approaches: (1) Namespace/collection isolation: each tenant gets a separate namespace or collection. Best for: strong isolation, different schemas per tenant, enterprise customers. Cost: cannot do cross-tenant queries, many collections have overhead. (2) Metadata filtering: single collection with `tenant_id` metadata field. Queries add `WHERE tenant_id = X` pre-filter. Best for: many small tenants, shared infrastructure. Risk: filter effectiveness depends on index structure — some vector DBs apply filter after ANN (post-filter, poor recall for large tenants) vs before (pre-filter, good recall). Use Qdrant or pgvector which support efficient pre-filtering. (3) Hybrid: shared collection for small tenants, dedicated collections for large enterprise tenants. Each approach has operational trade-offs.

**Q: When would you use pgvector vs a dedicated vector database?**
Use pgvector when: (1) Vectors are joinable to relational data (products, users, documents — query: "find similar products to X that are also in stock and available in user's region" — one query in pgvector, multiple round trips in a dedicated vector DB). (2) Transactional writes: embedding a document and its metadata atomically. (3) Compliance requirements favor single database system. (4) Scale is under 10M vectors for HNSW (scalable to 100M+ with IVFFlat and tuning). (5) Team is PostgreSQL-only and doesn't want to operate another database. Use dedicated vector DB when: (1) > 100M vectors on a single node. (2) Need managed service, auto-scaling, zero-ops. (3) Need features like multi-modal search, automatic embedding, advanced similarity functions. (4) Performance at extreme scale is critical.

**Q: What is hybrid search and when is it necessary?**
Hybrid search combines dense vector similarity (semantic) with sparse keyword similarity (BM25). Pure vector search fails for: exact keyword matches (product IDs, proper nouns, rare technical terms — the embedding model may not represent "CUDA-12.3" vs "CUDA-12.4" as distinct), domain-specific jargon, and queries where exact term match matters more than semantic similarity. Example: query "CUDA 12.3 installation error" — vector search finds semantically related documents, but BM25 helps ensure "CUDA 12.3" is specifically matched. Implementation: (1) Run both searches, merge results using RRF (Reciprocal Rank Fusion) which weights results by their rank in each list. (2) Use a single model that produces both dense and sparse vectors (SPLADE, SparseEmbed). Weaviate and Qdrant support hybrid search natively; pgvector requires combining with tsvector for hybrid.

**Q: How do embedding models affect retrieval quality in RAG systems?**
Embedding model choice affects recall (finding relevant documents) and precision (avoiding irrelevant ones). Key factors: (1) Dimensionality: higher dims (3072 vs 384) = more information but more memory and slower search. (2) Domain match: a model fine-tuned on code embeddings (e.g., CodeBERT) dramatically outperforms general models for code retrieval. (3) Context window: sentence-transformers process up to 512 tokens; OpenAI ada-002 up to 8191 tokens — longer context = can embed entire pages instead of chunks. (4) Task: asymmetric search (short queries vs long documents) needs models designed for asymmetric pairs (E5, BGE). Evaluation: use MTEB (Massive Text Embedding Benchmark) as reference, but always evaluate on your specific domain with your actual queries.

**Q: What is product quantization and what is its recall-speed tradeoff?**
Product Quantization (PQ) compresses high-dimensional vectors into compact codes (e.g., 1536 dims × 4 bytes = 6144 bytes → 96 bytes with 64x compression) by: (1) Splitting the vector into M sub-spaces. (2) For each sub-space, learning K centroids via k-means. (3) Replacing each sub-vector with the index of the nearest centroid (1 byte per sub-space at K=256). Distance computation between query and PQ-compressed vectors: look up precomputed distance tables (fast, integer operations). Recall-speed tradeoff: more sub-spaces (M) = less compression, better recall. More centroids (K) = larger codebook, better accuracy. Typical configuration: M=96, K=256 achieves 64x compression with ~90-95% recall vs exact search. IVF+PQ (used by FAISS, Milvus): IVF narrows to relevant clusters, PQ compresses for fast distance computation within clusters.

**Q: How do you measure embedding quality and which metrics matter for RAG?**
Key metrics for RAG retrieval: (1) Recall@K: percentage of queries where the relevant document appears in the top K results. For RAG, recall@5 or recall@10 matters most. (2) MRR (Mean Reciprocal Rank): 1/rank of the first relevant result, averaged across queries. (3) NDCG@K (Normalized Discounted Cumulative Gain): accounts for graded relevance and position. Evaluation approach: create a test set of (query, relevant_document) pairs from your domain. Run retrieval, measure recall@5, MRR@10. Compare embedding models on this test set. Also measure: embedding latency (query embedding time), index build time, memory per vector, and query throughput. BEIR and MTEB are public benchmarks, but domain-specific evaluation on your own data is essential for production decisions.

**Q: What is the "lost in the middle" problem for RAG and how does vector search help?**
When many relevant documents are injected into an LLM's context window, the LLM performs poorly on information in the middle of the context — it focuses on the beginning and end (attention sparsity for mid-context). Vector search helps by providing precisely the most relevant chunks (top-K) rather than entire documents. Best practices: (1) Use reranking after retrieval to ensure the most relevant chunk is at the beginning of the context. (2) Limit context to 3-5 chunks (not 20). (3) Use hybrid retrieval + reranking (e.g., Cohere Rerank, BGE-Reranker) to improve chunk precision. (4) For multi-document queries, structure prompts so critical information is at the beginning.

**Q: How does the IVFFlat index build and what happens when vectors are added after building?**
IVFFlat index build: (1) Run K-means on a sample of training vectors to learn `lists` centroids. K-means requires training data — pgvector requires 3×lists vectors minimum for training. (2) Each vector is assigned to its nearest centroid and stored in that centroid's inverted list. When new vectors are added after index build: they are assigned to existing centroids (centroids do NOT move — the centroids are frozen post-build). Over time, if the data distribution shifts significantly, the fixed centroids become suboptimal → recall degrades. Fix: periodically rebuild the index (`REINDEX INDEX CONCURRENTLY`) to recompute centroids on the current data distribution.

**Q: What is the HNSW ef_construction parameter and how does it differ from ef at query time?**
`ef_construction` (build time): the size of the dynamic candidate list when building the HNSW graph. When inserting a new vector: start a greedy search, maintaining `ef_construction` candidates. The `M` best candidates become the new vector's connections. Larger `ef_construction` = more candidates considered during build = better connections = better recall at query time. Trade-off: slow build time (O(N × ef_construction × log N)), more memory during build. `ef` (query time): the size of the dynamic candidate list during search traversal. Larger `ef` = more candidates evaluated = better recall = slower query. Default pgvector: `ef_construction=64, hnsw.ef_search=40`. For high-recall production: `ef_construction=200, hnsw.ef_search=100`.

**Q: How does cosine similarity differ from dot product similarity for normalized vectors?**
For unit-normalized vectors (||v|| = 1, where the vector has been divided by its magnitude): cosine similarity = dot product. `cos(a,b) = (a·b)/(||a||×||b||)`. If ||a|| = ||b|| = 1, this simplifies to `cos(a,b) = a·b`. OpenAI embeddings are normalized to unit length. Therefore: use dot product (`<#>` in pgvector) which avoids the division and is computationally cheaper. For non-normalized embeddings (image models, some sentence transformers): use cosine similarity (`<=>` in pgvector) which normalizes during comparison. Practical rule: check the embedding model documentation. If it says "normalized" or "unit vectors," use dot product for 10-20% faster distance computation.

---

## 13. Best Practices

1. Use the same embedding model for all vectors in a collection — mixing models destroys similarity.
2. Set `ivfflat.probes = sqrt(lists)` as a starting point for balanced recall/speed.
3. Use HNSW for production with < 10M vectors and high recall requirements.
4. Use IVFFlat for initial prototyping and larger datasets (faster build).
5. Pre-filter by metadata (tenant_id, category, date) BEFORE ANN search for multi-tenant systems.
6. Normalize embeddings before storing if using dot product similarity (pgvector: embedding / ||embedding||).
7. Monitor recall with offline evaluation before and after index parameter changes.
8. For RAG: use hybrid search (dense + sparse) for production — pure vector search misses exact keyword matches.
9. Implement reranking after top-K retrieval for precision-critical applications.
10. Choose chunk size to match the embedding model's context window and typical answer granularity.

---

## 14. Case Study

**Scenario**: A legal firm needs semantic search across 50M legal documents (briefs, contracts, case law). Requirement: find semantically similar precedents for a new case, even when different terminology is used. Query: "contract breach due to force majeure" should find cases about "acts of God" and "impossibility of performance."

**Architecture: pgvector + hybrid search**:

```sql
-- Schema:
CREATE TABLE legal_documents (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      TEXT UNIQUE NOT NULL,
    content     TEXT,
    embedding   vector(1536),
    full_text_search tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    jurisdiction TEXT,
    case_date   DATE,
    doc_type    TEXT
);

-- HNSW index (50M vectors — requires tuning):
CREATE INDEX idx_legal_hnsw ON legal_documents
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 128);

-- Full-text search index:
CREATE INDEX idx_legal_fts ON legal_documents USING GIN (full_text_search);

-- Hybrid search query:
WITH vector_search AS (
    SELECT id, 1 - (embedding <=> $1::vector) AS vector_score
    FROM legal_documents
    WHERE jurisdiction = $2 AND doc_type = ANY($3)
    ORDER BY embedding <=> $1::vector
    LIMIT 50  -- Get top-50 vector candidates
),
text_search AS (
    SELECT id, ts_rank(full_text_search, plainto_tsquery('english', $4)) AS text_score
    FROM legal_documents
    WHERE full_text_search @@ plainto_tsquery('english', $4)
      AND jurisdiction = $2 AND doc_type = ANY($3)
    LIMIT 50  -- Get top-50 text candidates
),
rrf AS (
    SELECT id,
           COALESCE(1.0/(60 + ROW_NUMBER() OVER (ORDER BY v.vector_score DESC)), 0) +
           COALESCE(1.0/(60 + ROW_NUMBER() OVER (ORDER BY t.text_score DESC)), 0) AS rrf_score
    FROM vector_search v FULL OUTER JOIN text_search t USING (id)
)
SELECT d.id, d.doc_id, d.content, r.rrf_score
FROM rrf r JOIN legal_documents d ON d.id = r.id
ORDER BY r.rrf_score DESC
LIMIT 10;
```

**Reranking pass** (Cohere Rerank API):
- Send top-20 documents to reranker
- Reranker uses cross-attention (query-document pair scoring) for precision
- Return top-5 most relevant

**Result**: Retrieved relevant precedents using "acts of God" language for a "force majeure" query (vector similarity). Combined with keyword matching for specific case numbers and legal citations (BM25). Precision improved by 40% over keyword-only search. Average query time: 120ms (vector search 80ms + reranking 40ms).
