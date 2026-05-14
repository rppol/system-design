# Embeddings & Similarity Search

## 1. Concept Overview

Embeddings are dense vector representations that capture the semantic meaning of text, images, or other data. Similar content maps to nearby points in the embedding space — this is the foundation of semantic search, recommendation systems, clustering, and RAG pipelines.

Similarity search (also called vector search or nearest neighbor search) is the problem of finding the K vectors most similar to a query vector from a large collection (potentially billions of vectors). The challenge is doing this **fast** — brute-force comparison of a query against 1B vectors at 1536 dimensions would take seconds; production systems need it in milliseconds.

Together, embeddings + similarity search power the retrieval component in RAG systems, semantic deduplication, zero-shot classification, recommendation systems, and more.

---

## Intuition

> **One-line analogy**: Embeddings are like GPS coordinates for meaning — similar ideas live close together, so finding related content is just finding nearby points in meaning-space.

**Mental model**: Imagine a vast 1536-dimensional map where every sentence, paragraph, or document has a fixed address. "Dog training tips" and "how to teach your puppy" live at nearly the same address; "quantum physics" is in a different country. When you ask a question, you convert it to its GPS coordinates and find the 10 closest addresses. That's semantic search. The embedding model is the GPS function.

**Why it matters**: Embeddings power the retrieval layer of virtually every production LLM application — RAG systems, semantic search engines, recommendation systems, deduplication. Without efficient similarity search (HNSW, IVF), finding nearest neighbors in a billion-vector corpus would take seconds; with it, milliseconds.

**Key insight**: Approximate Nearest Neighbor (ANN) algorithms like HNSW deliberately sacrifice 1-5% recall for 100-1000× speedup — a tradeoff that makes billion-scale semantic search practical.

---

## 2. Core Principles

- **Semantic similarity**: Vectors that are "close" (high cosine similarity or low L2 distance) represent semantically similar content.
- **Representation learning**: Good embeddings are trained so that the geometry of the space reflects meaningful relationships.
- **Fixed dimensionality**: Each piece of content is represented as a vector of fixed size (e.g., 384, 768, 1536, 3072 dimensions).
- **Approximate Nearest Neighbor (ANN)**: For large-scale search, exact nearest neighbor is too slow. ANN trades a small accuracy loss for orders-of-magnitude speed improvement.
- **Embedding models ≠ LLMs**: Embedding models output a single vector per text; LLMs output token probabilities. Dedicated embedding models are typically much smaller and faster.

---

## 3. Types / Strategies

### 3.1 Sentence Embeddings

**all-MiniLM-L6-v2** (SentenceTransformers)
- 384 dimensions, 80M params, extremely fast
- Trained on 1B sentence pairs
- Best for low-latency production use

**GTE (General Text Embeddings, Alibaba)**
- GTE-base (768d), GTE-large (1024d), GTE-Qwen2-7B (4096d)
- Strong multilingual performance

**BGE (BAAI General Embeddings)**
- BGE-small/base/large; strong MTEB performance
- BGE-M3: multilingual, multi-granularity (dense + sparse + colbert)

**E5 (Microsoft)**
- Requires "query: " / "passage: " prefix — different query/passage representations
- Strong retrieval performance

**OpenAI text-embedding-3**
- text-embedding-3-small (1536d) / text-embedding-3-large (3072d)
- Supports Matryoshka truncation (see below)

### 3.2 Contrastive Learning

The dominant training paradigm for embedding models:

```
Anchor text:   "The capital of France"
Positive:      "Paris is the capital"   → PULL together
Negative:      "Berlin is a great city" → PUSH apart

Loss = -log[ exp(sim(anchor, pos)/τ) / Σ exp(sim(anchor, neg_i)/τ) ]
       (InfoNCE / NT-Xent loss, τ = temperature)
```

**SimCSE (2021)**: Uses the same sentence passed through the model twice with different dropout masks as a positive pair — extremely simple and effective self-supervised approach.

**Hard negatives**: The most important factor. Random negatives are easy; the model learns more from examples that are semantically similar but not correct answers.

**In-batch negatives**: Other examples in the same training batch serve as negatives — allows large effective negative count without explicit negative mining.

### 3.3 Matryoshka Representation Learning (MRL)

Train embeddings so that the first K dimensions already encode a good representation, and adding more dimensions improves quality monotonically.

```
Full embedding: [d1, d2, ..., d1536]  (1536 dim, best quality)
Truncated 512:  [d1, d2, ..., d512]   (512 dim, faster, ~2% quality drop)
Truncated 64:   [d1, d2, ..., d64]    (64 dim, much faster, ~5% quality drop)
```

Benefits:
- Store smaller vectors in production, full vectors for reranking
- Adaptive quality/cost tradeoff at inference time
- OpenAI's text-embedding-3 supports this natively with `dimensions` parameter

### 3.4 Bi-Encoder vs Cross-Encoder

**Bi-Encoder**: Query and document encoded separately → very fast (precompute document embeddings)
```
Query  → Encoder → q_vec  ]
                           ] → cosine_similarity → score
Doc    → Encoder → d_vec  ]
```

**Cross-Encoder**: Query and document encoded together → much more accurate but slow (can't precompute)
```
[Query + Document] → Encoder → score (single pass through model)
```

**Production pattern**: Bi-encoder for recall (fast ANN search), cross-encoder for reranking (top-K candidates)

---

## 4. Architecture Diagrams

### Embedding Retrieval Pipeline
```
Query: "How does attention work?"
     |
     v
[Query Encoder]  (bi-encoder, e.g., BGE-base)
     |
     v
Query Vector: [0.12, -0.34, ..., 0.89]  (768 dim)
     |
     v
[ANN Index]  (HNSW in Qdrant/Weaviate)
     |
     v
Top-100 candidate document IDs + scores
     |
     v
[Fetch document texts from store]
     |
     v
[Cross-Encoder Reranker]  (BGE-reranker-large)
     |
     v
Top-10 reranked results
     |
     v
[LLM generation with context]
```

### HNSW Index Structure
```
Layer 2 (sparse):   [A] --------- [E]
                     |
Layer 1:   [A] --- [B] --- [E] --- [G]
                     |         \
Layer 0:  [A]-[B]-[C]-[D]-[E]-[F]-[G]-[H]  (all vectors)

Query: find nearest to X
  Start at entry point in top layer
  Greedily descend to closest neighbor
  At Layer 0, explore neighborhood to find exact nearest
```

### IVF (Inverted File Index) Structure
```
K-means clustering of all vectors into K centroids (K=1000)

Cluster 1:   [v1, v5, v12, ...]   (nearby vectors)
Cluster 2:   [v3, v7, v99, ...]
...
Cluster K:   [v2, v8, v44, ...]

Query:
  1. Compute distance to all K centroids (fast, K << N)
  2. Search only top-N_probe clusters (e.g., N_probe=10)
  3. Exact nearest neighbor within selected clusters
```

---

## 5. How It Works — Detailed Mechanics

### Distance Metrics

| Metric | Formula | Best For |
|--------|---------|---------|
| Cosine Similarity | `A·B / (|A||B|)` | Normalized vectors; direction matters |
| Dot Product | `A·B` | When magnitude encodes relevance (OpenAI embeddings) |
| Euclidean (L2) | `√Σ(a_i - b_i)²` | When absolute position matters |
| Manhattan (L1) | `Σ|a_i - b_i|` | Sparse vectors; rare |

**Important**: For unit-normalized vectors, cosine similarity and dot product are equivalent. Many systems normalize embeddings at index time to use faster dot product operations.

### HNSW Deep Dive (Hierarchical Navigable Small World)

**Properties:**
- Insert/query time: O(log N)
- Memory: O(N × M) where M = connections per node (typically 16-64)
- Search quality parameter: `ef_construction` (index time) and `ef` (query time)

**HNSW vs Flat vs IVF:**
```
Dataset: 1M vectors, 768 dimensions, top-10 recall@10

Flat (brute force):
  - Recall: 100% (exact)
  - Latency: ~500ms per query
  - No build time

IVF-Flat (K=1000, probe=10):
  - Recall: ~95%
  - Latency: ~5ms per query
  - Build time: minutes

HNSW (M=32, ef=128):
  - Recall: ~98%
  - Latency: ~1ms per query
  - Build time: ~1 hour
  - Memory: ~5GB (vs 3GB flat)
```

### Product Quantization (PQ)

Compresses vectors from ~3KB (768 × float32) to ~96 bytes (24 × uint8):
1. Split 768-dim vector into 24 subspaces of 32 dims each
2. K-means cluster each subspace into 256 clusters
3. Store only the cluster ID (1 byte) per subspace

PQ enables storing billions of vectors on a single server at the cost of ~5-10% recall drop.

### Embedding Fine-Tuning for Domain Adaptation

```
Pre-trained embedding model (e.g., BGE-base)
     |
     v
Domain-specific training data:
  - Positive pairs: (query, relevant doc) from your domain
  - Hard negatives: (query, similar-but-irrelevant doc)
     |
     v
Fine-tune with InfoNCE loss (few hundred steps, small LR)
     |
     v
Domain-adapted embedding model
  - Better recall for domain vocabulary
  - Understands domain-specific relationships
```

### Embedding Drift Detection

Embeddings in production can drift when:
- The embedding model is updated (model version change)
- Domain distribution shifts (new products, events)
- Index was built with different preprocessing

**Detection**: Track cosine similarity distribution between daily query embeddings and the index distribution. Alert if mean similarity drops significantly.

---

## 6. Real-World Examples

### Pinecone at Scale
- Powers semantic search for thousands of production applications
- Uses HNSW as the core index with metadata filtering
- Handles 10B+ vectors across multiple namespaces
- Typical latency: 2-5ms for 1M vectors, 5-20ms for 100M vectors

### OpenAI Embeddings
- text-embedding-3-large: 3072 dimensions, Matryoshka training
- Used by ChatGPT for file search, by thousands of RAG applications
- Estimated 1B+ embedding API calls/day
- Supports native dimension reduction via `dimensions` parameter (new in v3)

### Google Semantic Search
- Universal Sentence Encoder (USE) powers Google's semantic search features
- Deployed at web scale with custom hardware (TPUs) for embedding generation
- Uses approximate search with learned quantization

### Facebook (Meta) FAISS
- Open-sourced in 2017, powers billions of similarity searches internally
- Used for recommendation systems (find similar content to what user engaged with)
- Powers content moderation (find near-duplicate violating content)
- Handles 1T+ vector operations per day

---

## 7. Tradeoffs

| Index Type | Recall | Speed | Memory | Scalability |
|------------|--------|-------|--------|-------------|
| Flat | 100% | Slow (O(N)) | Low | Poor (N>1M) |
| IVF-Flat | ~95% | Fast | Low | Good |
| IVF-PQ | ~85-90% | Very fast | Very low | Excellent (billions) |
| HNSW | ~97-99% | Very fast | Higher | Good (100M) |
| HNSW-PQ | ~92-95% | Fast | Medium | Excellent |

| Embedding Model | Dims | Speed | Quality | Cost |
|----------------|------|-------|---------|------|
| all-MiniLM-L6 | 384 | Fastest | Good | Free |
| BGE-base | 768 | Fast | Very good | Free |
| text-embedding-3-small | 1536 | API | Excellent | $0.02/1M tokens |
| text-embedding-3-large | 3072 | API | Best | $0.13/1M tokens |

---

## 8. When to Use / When NOT to Use

### Use Embeddings + ANN When:
- Semantic search (not keyword matching)
- RAG retrieval
- Recommendation (find similar items)
- Near-duplicate detection
- Zero-shot classification via nearest class centroid
- Dataset size > 100K items

### Use BM25 / Keyword Search When:
- Exact keyword matching matters (legal documents, product SKUs)
- No training data for embeddings
- Query contains rare technical terms not in embedding training

### Use Hybrid Search When:
- Best of both worlds — combine BM25 + dense retrieval scores
- Most production RAG systems use hybrid search (e.g., Weaviate, Elasticsearch 8.0+)

### Do NOT Use Vector Search When:
- Dataset fits in memory and query latency isn't critical (just use numpy)
- Queries are structured (SQL is better)
- You need exact match (use database index, not ANN)

---

## 9. Common Pitfalls

1. **Using the wrong embedding model for the task**: Embedding model trained on NLI may not work well for code retrieval. Match model to domain.
2. **Not normalizing vectors**: If your similarity metric assumes unit vectors but you store raw embeddings, results are wrong.
3. **Ignoring query/document asymmetry**: Models like E5 require specific prefixes ("query: " vs "passage: "). Skipping these degrades recall by 10-20%.
4. **Over-relying on ANN recall**: ANN recalls less than exact search. Set ef (HNSW) and nprobe (IVF) high enough for your recall target.
5. **Not handling chunking**: A 10,000-word document as a single embedding loses information. Chunk first.
6. **Stale embeddings**: If you update the embedding model, you must re-embed all documents in the index.
7. **Assuming cross-encoder reranking is free**: Cross-encoders are 50-200x slower than bi-encoders. Use for top-K only (K=50-100).

---

## 10. Technologies & Tools

| Tool | Type | Notes |
|------|------|-------|
| **FAISS** | ANN library | Facebook; flat/IVF/HNSW/PQ; CPU + GPU |
| **Pinecone** | Managed vector DB | Serverless + pod-based; 1B+ scale |
| **Weaviate** | Vector DB | Built-in hybrid search; GraphQL API; open source |
| **Qdrant** | Vector DB | Rust-based; high performance; open source + cloud |
| **Milvus** | Vector DB | Distributed; Kubernetes-native; large scale |
| **Chroma** | Vector DB | Embedded; perfect for development |
| **pgvector** | PostgreSQL extension | Exact + ANN search; no new infra needed |
| **Redis Vector** | Redis extension | In-memory; fast; integrated with Redis stack |
| **SentenceTransformers** | Embedding models | Large model hub, easy fine-tuning |
| **Annoy** | ANN library | Spotify's library; trees-based; read-only after build |

---

## 11. Interview Questions with Answers

**Q: What is the difference between HNSW and IVF indexing?**
A: HNSW (Hierarchical Navigable Small World) is a graph-based index that creates a multi-layered graph for efficient navigation. It offers high recall (~99%) with fast queries (O(log N)) but uses more memory. IVF (Inverted File Index) clusters vectors with K-means and searches only nearby clusters. Lower memory overhead, slightly lower recall, great for very large datasets. HNSW is preferred for <100M vectors; IVF-PQ is preferred for billions of vectors.

**Q: Why use cosine similarity over dot product for embeddings?**
A: Cosine similarity ignores vector magnitude, measuring only directional similarity. This is important when the magnitude isn't semantically meaningful (varies with text length, model confidence). Dot product is equivalent for unit-normalized vectors, and is faster to compute (no division). Most production systems normalize embeddings at index time to use faster dot product.

**Q: What is Matryoshka Representation Learning?**
A: MRL trains embeddings so that the first K dimensions already form a meaningful lower-dimensional representation. This allows using the same model with different dimension truncations for different latency/quality tradeoffs. You can store full vectors and truncate at query time — no reindexing needed to change quality level.

**Q: How would you scale a vector search system to 1 billion vectors?**
A: Use IVF-PQ: K-means cluster vectors into ~65K centroids (IVF) and compress individual vectors with Product Quantization to ~64-128 bytes each. 1B × 128 bytes = 128GB RAM, feasible on one large server. For higher recall, use IVFHNSW (HNSW-based coarse quantizer). Distribute across shards if query latency matters. Use GPU for batch similarity computations.

**Q: What is a cross-encoder and when would you use it instead of bi-encoder?**
A: A cross-encoder takes the concatenated query + document and produces a relevance score in a single forward pass — much higher quality than comparing separately encoded vectors, but cannot precompute document representations. Use bi-encoders for the first-stage retrieval (fast, precomputed) and cross-encoders for reranking the top-K candidates (slow, high quality). Typical setup: ANN retrieves top 100, cross-encoder reranks to top 5.

**Q: How do you tune HNSW parameters (ef_construction, M, ef_search) for optimal recall-latency tradeoff?**
HNSW has three critical parameters: M (max connections per node, typically 16-64), ef_construction (beam width during index build, typically 100-500), and ef_search (beam width during query, typically 50-200). Higher M increases recall but uses more memory (each connection stores a neighbor pointer); M=16 is good for <1M vectors, M=32-64 for larger datasets. ef_construction affects index quality — higher values build better graphs but take longer; set to at least 2x M. ef_search directly trades latency for recall at query time — start at 100, increase until recall@10 exceeds 0.95 on your validation set. Practical guidance: build with high ef_construction (200-500), then tune ef_search at query time for your latency budget. At 10M vectors with M=32, ef_search=100 typically gives 95%+ recall with <5ms latency.

**Q: What is IVF-PQ and how do you choose the number of partitions and subquantizers?**
IVF-PQ combines Inverted File Index (IVF) for coarse partitioning with Product Quantization (PQ) for compressed vector storage, enabling billion-scale search. IVF divides vectors into nlist clusters using k-means; at query time, only nprobe nearest clusters are searched. PQ splits each vector into m subvectors and quantizes each to a codebook of 256 entries (1 byte each), compressing a 768-dim FP32 vector (3KB) to m bytes. Rules of thumb: nlist = sqrt(N) to 4*sqrt(N) where N is dataset size; nprobe = 1-10% of nlist for 90%+ recall; m = dim/4 to dim/8 (e.g., 768-dim → 96-192 subquantizers). IVF-PQ with 100M vectors: 100M x 96 bytes = 9.6GB vs 300GB for raw FP32 — a 30x compression.

**Q: How does Matryoshka Representation Learning work and when should you use truncated dimensions?**
Matryoshka embeddings are trained so that the first d dimensions of a D-dimensional embedding form a valid d-dimensional embedding, enabling flexible dimension reduction without retraining. During training, the loss function is computed at multiple truncation points (e.g., 64, 128, 256, 512, 768 dimensions simultaneously), so the model learns to pack the most important information into the earliest dimensions. Use truncated dimensions when: (1) you need to reduce storage (768-dim → 256-dim = 3x less storage); (2) search speed matters (lower dimensions = faster distance computation); (3) you want adaptive precision — coarse search with 128-dim, then rerank with full 768-dim. OpenAI's text-embedding-3 models support Matryoshka truncation. Empirically, truncating from 768 to 256 dimensions retains 95%+ of the retrieval quality for most tasks.

**Q: When should you fine-tune an embedding model vs use an off-the-shelf model?**
Fine-tune when your domain has specialized vocabulary or relationships that general-purpose embeddings miss — typically when off-the-shelf retrieval recall drops below 80% on your evaluation set. Domains that benefit most: medical (clinical terminology), legal (case law relationships), code (API semantics), and any domain with jargon. Fine-tuning requires: (1) contrastive training data — (query, positive_passage, hard_negative) triples; (2) at least 10K-50K high-quality triples for meaningful improvement; (3) a hard negative mining strategy (use BM25 or the base embedding model to find near-miss negatives). Methods: full fine-tuning of a bi-encoder (e.g., sentence-transformers) or adapter-based fine-tuning (LoRA on the encoder). A fine-tuned E5 or BGE model on domain data typically improves recall@10 by 10-25% compared to the base model.

**Q: How do you evaluate embedding quality for a production RAG system?**
Evaluate embeddings on retrieval metrics specific to your RAG use case, not general benchmarks like MTEB. Key metrics: (1) Recall@K — what fraction of relevant documents appear in the top K retrieved (K=5 or K=10 for typical RAG); (2) MRR (Mean Reciprocal Rank) — how high the first relevant document ranks on average; (3) NDCG@K — accounts for graded relevance. Build an evaluation dataset: 100-500 (query, relevant_documents) pairs from your actual domain. Test multiple embedding models (OpenAI, Cohere, BGE, E5) on your evaluation set — the best model on MTEB is often not the best for your specific domain. Also measure: encoding latency (how fast queries are embedded), storage per vector, and index build time. Production tip: track retrieval quality over time as your document corpus grows — embedding quality can degrade as the vector space becomes denser.

---

## 12. Best Practices

1. **Use Matryoshka-trained models** for flexible dimension/quality tradeoffs in production.
2. **Always fine-tune embedding models** on domain-specific data if you have labeled pairs — even 1000 pairs helps significantly.
3. **Implement hybrid search** (BM25 + dense) — consistently outperforms either alone.
4. **Pre-normalize vectors** before indexing — avoids repeated normalization at query time.
5. **Set HNSW ef_construction high** (200-400) during index build for better graph quality; tune ef lower (50-100) at query time for speed.
6. **Monitor recall** by periodically running exact search on a sample and comparing results.
7. **Use batch embedding** with GPU acceleration for generating embeddings over large corpora.

---

## 13. Case Study: Semantic Search for 50M Product Catalog

**Problem:** E-commerce platform with 50M products. Users type natural language queries ("warm jacket for hiking in winter") but product descriptions use different vocabulary. BM25 keyword search misses semantically relevant results.

**Architecture:**
```
Query: "warm jacket for hiking in winter"
  |
  v
[BGE-M3 embedding, 1024 dim, query: prefix]
  |
  v
[HNSW Index in Milvus, 50M vectors, M=32, ef_construction=200]
  |
  v
Top-200 candidates (recall ~97%)
  |
  v
[BM25 scores fetched from Elasticsearch]
  |
  v
[RRF (Reciprocal Rank Fusion) hybrid merge]
  |
  v
Top-50 candidates
  |
  v
[BGE-reranker-large cross-encoder, GPU batch]
  |
  v
Top-10 final results
```

**Infrastructure:**
- 50M × 1024 × 4 bytes = 200GB RAM for flat index → too large
- Used HNSW-PQ with 256-byte compressed vectors: 50M × 256 = 12.8GB
- 4 Milvus nodes, 32GB RAM each, replicated
- Query latency: HNSW 3ms + BM25 5ms + reranker 80ms = ~90ms end-to-end P99

**Results:**
- NDCG@10: +31% vs pure BM25
- Click-through rate: +18% in A/B test
- Add-to-cart rate: +9%
