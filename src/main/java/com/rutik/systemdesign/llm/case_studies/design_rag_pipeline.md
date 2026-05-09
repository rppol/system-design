# Case Study: Design a Production RAG Pipeline

## Intuition

> **Design intuition**: A RAG pipeline is a document retrieval system + LLM generation — the retrieval quality bottleneck is the dominant engineering challenge. Most RAG failures are retrieval failures (wrong chunks retrieved), not generation failures (LLM given good context hallucinates less).

**Key insight for this design**: Three-tier architecture is essential: fast vector search for recall, cross-encoder reranking for precision, and LLM generation only after high-quality context is assembled. The indexing pipeline (chunking strategy, embedding model choice, metadata schema) determines retrieval ceiling — improve retrieval before improving generation.

---

## 1. Requirements Clarification

### Functional Requirements
- Users ask natural language questions and receive accurate, grounded answers
- Answers cite specific source documents with page/section references
- Knowledge base: 10M enterprise documents (PDFs, Word, HTML, Markdown)
- Supports incremental updates: new documents indexed within 5 minutes
- Multi-tenant: 500 enterprise clients, each with isolated document sets
- Handles multi-hop questions ("Which contracts mention clause X AND vendor Y?")

### Non-Functional Requirements
- **Latency**: End-to-end < 3 seconds for simple queries; < 8 seconds for complex
- **Accuracy**: > 85% correct answers on internal evaluation set
- **Scale**: 10,000 concurrent users; 5M queries per day
- **Availability**: 99.9% uptime
- **Security**: Tenant isolation (Client A cannot see Client B documents)

### Out of Scope
- Document OCR pipeline (handled by separate Document Processing service)
- User authentication and authorization
- Model fine-tuning

---

## 2. Scale Estimation

### Document Processing Scale
```
Total documents: 10M
Average document size: 50KB (text content after extraction)
Total text: 10M × 50KB = 500GB of text

Chunking: 512-token chunks with 50-token overlap
Average tokens per document: 10,000 tokens
Chunks per document: ~22 chunks
Total chunks: 10M × 22 = 220M chunks

Embedding dimension: 1,536 (OpenAI text-embedding-3-large)
Storage per chunk embedding: 1,536 × 4 bytes = 6KB
Total embedding storage: 220M × 6KB = 1.32TB

Metadata per chunk: 256 bytes average
Total metadata: 220M × 256B = 56GB
```

### Query Scale
```
Daily queries: 5M
Peak QPS: 5M / 86,400 × 3 = ~174 req/sec

Per query resource:
  Embedding generation: 10ms (user query → 1536-dim embedding)
  ANN search (220M vectors): 50ms
  Document fetch + reranking (top-50 → top-5): 100ms
  LLM generation (1,000 input + 300 output tokens): 800ms
  Total: ~960ms ... needs optimization

10K concurrent users: connection pooling, async processing
```

---

## 3. High-Level Architecture

```
                        User Query
                             |
                             v
                    [API Gateway + Auth]
                             |
                             v
              ┌──────────────────────────────┐
              │        Query Service         │
              │  - Query preprocessing       │
              │  - Multi-query expansion     │
              │  - Tenant context injection  │
              └──────────────────────────────┘
                             |
              ┌──────────────┼──────────────┐
              │              │              │
              v              v              v
       [Embedding      [Keyword         [Metadata
        Service]        Service]         Filter]
       Dense search    BM25/Sparse      Date, type,
       (semantic)      (lexical)        author filters
              │              │              │
              └──────────────┼──────────────┘
                             │ Hybrid results (RRF fusion)
                             v
                    [Reranking Service]
                    Cross-encoder reranker
                    Top-50 → Top-5
                             │
                             v
                  [Context Assembly Service]
                  - Fetch full chunk text
                  - Add surrounding context
                  - Format with citations
                             │
                             v
                    [LLM Generation Service]
                    GPT-4o / Claude 3.5
                    Grounded response generation
                             │
                             v
                  [Response Post-processing]
                  - Citation formatting
                  - Factuality check
                  - Source verification
                             │
                             v
                         User Response
                         (answer + citations)

───────────────────────────────────────────────────────
                   INDEXING PIPELINE
───────────────────────────────────────────────────────

New Document → [Document Ingestion Queue (Kafka)]
                    │
                    v
             [Document Parser]
             PDF/Word/HTML → clean text
                    │
                    v
             [Chunking Service]
             Semantic/hierarchical chunking
                    │
                    ├──→ [Embedding Service] → Vector DB (Weaviate/Qdrant)
                    │
                    ├──→ [BM25 Indexer] → Elasticsearch
                    │
                    └──→ [Metadata Indexer] → PostgreSQL
```

---

## 4. Component Deep Dives

### 4.1 Document Chunking Strategies

```
Strategy selection by document type:

PDFs / Academic papers:
  Strategy: Hierarchical chunking
  Level 1: Section → title + first 200 chars (for navigation)
  Level 2: Paragraph → 256-512 tokens (for retrieval)
  Level 3: Sentence → 50-100 tokens (for exact fact retrieval)

  Retrieve at Level 2; expand to Level 1 for context (parent retrieval)

Contracts / Legal documents:
  Strategy: Structure-aware chunking
  - Preserve clause boundaries (don't split clause mid-sentence)
  - Chunk = one complete clause
  - Overlap: include clause header in each chunk

  Why: Legal meaning depends on complete clauses; partial clauses mislead model

Web / HTML documents:
  Strategy: Semantic chunking
  - Split by HTML structure (<h1>, <h2>, <p>, <table>)
  - Each chunk: heading + body = natural semantic unit
  - Tables kept whole (don't split rows across chunks)

Code documentation:
  Strategy: Function-level chunking
  - Each function/class = one chunk
  - Include: signature + docstring + body
  - Size variable (some functions 10 lines, some 200 lines)

Overlap strategy:
  Fixed overlap: 50-token overlap ensures context at chunk boundaries
  Sentence overlap: always end/start at sentence boundary (no mid-sentence splits)
```

### 4.2 Hybrid Retrieval with RRF

```
Dense retrieval (semantic):
  1. Embed query: "contracts with unlimited liability clauses"
     → q_emb = embed(query)  # 1,536-dim vector
  2. ANN search in Qdrant (HNSW index):
     → top_dense = qdrant.search(q_emb, top_k=50, filter=tenant_id)
  3. Results: documents similar in meaning (even without keyword overlap)

Sparse retrieval (lexical/keyword):
  1. BM25 tokenization: ["contracts", "unlimited", "liability", "clauses"]
  2. BM25 search in Elasticsearch:
     → top_sparse = es.search(keywords, top_k=50, filter=tenant_id)
  3. Results: documents with exact keyword matches

Reciprocal Rank Fusion:
  For each document d:
    rrf_score(d) = Σ [ 1 / (k + rank_dense(d)) + 1 / (k + rank_sparse(d)) ]
    where k = 60 (smoothing constant)

  dense_results:  [doc_A(rank=1), doc_C(rank=2), doc_B(rank=3), ...]
  sparse_results: [doc_B(rank=1), doc_A(rank=2), doc_D(rank=3), ...]

  RRF scores:
    doc_A: 1/(60+1) + 1/(60+2) = 0.01639 + 0.01613 = 0.03252
    doc_B: 1/(60+3) + 1/(60+1) = 0.01587 + 0.01639 = 0.03226
    doc_C: 1/(60+2) + 0        = 0.01613
    doc_D: 0        + 1/(60+3) = 0.01587

  Final ranking: [doc_A, doc_B, doc_C, doc_D, ...]

Why hybrid beats either alone:
  Dense alone misses: "unlimited liability" (exact legal term must match)
  Sparse alone misses: "contracts with no cap on damages" (semantic, no keyword overlap)
  Hybrid catches both
```

### 4.3 Reranking

```
Two-stage retrieval:
  Stage 1: ANN search → top-50 candidates (fast, approximate)
  Stage 2: Cross-encoder reranker → top-5 (slow, precise)

Stage 1 (bi-encoder): milliseconds
  - Query and document encoded independently
  - Comparison: dot product in embedding space
  - Fast: one-time document encoding; query encoded at retrieval time

Stage 2 (cross-encoder): ~100ms for 50 documents
  - Concatenate query + document as single input
  - Full attention between query and document tokens
  - Score: single relevance probability [0, 1]
  - Much more accurate but cannot pre-compute

Cross-encoder models:
  Open source: ms-marco-MiniLM-L-12-v2 (fastest, good quality)
              bge-reranker-large (best quality, slower)
  Commercial: Cohere Rerank 3 (best overall; $1/1K calls)

Reranker input:
  Query: "unlimited liability clause"
  Document chunk: "Section 8.2: Indemnification. The Vendor shall not be
  liable for any indirect, consequential, or incidental damages..."

  Score: 0.12 (low — this is a limitation clause, not unlimited liability)

  Compare with another chunk:
  "4.1: LIABILITY. The Vendor accepts unlimited liability for all damages
  arising from..."
  Score: 0.94 (high — direct match)
```

### 4.4 Context Assembly and Prompt Design

```
After reranking, top-5 chunks assembled into LLM prompt:

[System]
You are a document Q&A assistant. Answer questions based ONLY on the
provided context. Always cite the source document and section.
If the answer is not in the context, say "I don't have enough information."
Do not make up information.

[Context]
--- Source 1: NDA Agreement (Acme Corp), Section 4.1, Page 7 ---
"The Vendor accepts unlimited liability for all damages arising from
deliberate misconduct or gross negligence."

--- Source 2: Software License Agreement, Section 8.2, Page 15 ---
"Vendor's total liability shall not exceed the fees paid in the 12 months
preceding the incident."

--- Source 3: Master Services Agreement, Section 12.1, Page 22 ---
"In no event shall either party be liable for indirect or consequential
damages regardless of the form of action."

[User Question]
Which of our contracts have unlimited liability clauses?

[Expected Output]
Based on the provided documents, only the NDA Agreement with Acme Corp
(Section 4.1) contains an unlimited liability clause, specifically for
"deliberate misconduct or gross negligence."

The Software License Agreement (Section 8.2) and Master Services Agreement
(Section 12.1) both contain liability limitations.

Sources: [1] NDA Agreement, Section 4.1, p.7; [2] Software License,
Section 8.2, p.15; [3] MSA, Section 12.1, p.22
```

### 4.5 Multi-Tenant Isolation

```
Security requirement: Client A's documents must never appear in Client B's results

Implementation:

1. Indexing: Tag all chunks with tenant_id
   {
     "chunk_id": "abc123",
     "tenant_id": "client_A",
     "text": "...",
     "embedding": [...],
     "metadata": {...}
   }

2. Vector DB filtering: All queries include mandatory tenant filter
   qdrant.search(
     query_vector=q_emb,
     query_filter=Filter(
       must=[FieldCondition(key="tenant_id", match=MatchValue(value=user.tenant_id))]
     ),
     top_k=50
   )

3. Elasticsearch filter: Same for keyword search
   {
     "query": {"bool": {
       "must": [{"match": {"text": query_terms}}],
       "filter": [{"term": {"tenant_id": user.tenant_id}}]
     }}
   }

4. Application layer validation: Before returning results, verify each chunk's
   tenant_id matches the requesting user's tenant

Defense in depth: three independent isolation mechanisms
  - DB-level filter (primary)
  - Application-level check (secondary)
  - Audit logging of all cross-tenant access attempts

For enterprise: dedicated Qdrant collection per tenant (strongest isolation)
  - Higher cost but complete physical separation
  - Required for compliance (GDPR, HIPAA, FedRAMP)
```

### 4.6 Incremental Indexing

```
Requirement: new documents indexed within 5 minutes of upload

Pipeline:

1. Document upload → S3 bucket
   S3 triggers Lambda or Kafka event

2. Kafka message: {doc_id, tenant_id, s3_path, upload_time}
   Consumer group: document-processor (3 consumers, auto-scaled)

3. Document processor:
   a. Download from S3
   b. Parse: PDF → text (pdfplumber), HTML → text (trafilatura)
   c. Chunk: semantic chunking with 512-token target
   d. Embed: batch embed all chunks (OpenAI batch API or local model)
   e. Upsert: Qdrant upsert (update if exists, insert if new)
   f. Index: Elasticsearch bulk index
   g. Metadata: PostgreSQL upsert

4. Processing time per document:
   Parse: 5-30s (depends on PDF complexity)
   Embed 22 chunks: 2s (batch embedding API)
   Upsert to vector DB: 1s
   Total: ~10-35s per document (well within 5 minute SLA)

5. On failure:
   Kafka consumer commits offset only after successful processing
   Failed documents go to dead-letter queue
   Retry with exponential backoff (3 attempts)
   Alert on DLQ accumulation
```

---

## 5. RAG Evaluation with RAGAS

```
Four key metrics:

1. Context Precision (0-1):
   Of retrieved chunks, how many were actually relevant?
   precision = relevant_retrieved / total_retrieved
   Low precision → model confused by irrelevant context

2. Context Recall (0-1):
   Of all relevant chunks in corpus, how many were retrieved?
   recall = relevant_retrieved / total_relevant
   Low recall → missing key information → incomplete answers

3. Answer Relevancy (0-1):
   Does the answer address the question?
   Measured by: generate questions from answer, compare to original question
   Low relevancy → answer went off-topic

4. Faithfulness (0-1):
   Are all claims in the answer supported by retrieved context?
   Low faithfulness → model hallucinated beyond provided context

Evaluation pipeline:
  eval_dataset = [
    {"question": "...", "ground_truth": "...", "contexts": [...], "answer": "..."},
    ...
  ]

  ragas_score = ragas.evaluate(eval_dataset, metrics=[
    context_precision, context_recall, answer_relevancy, faithfulness
  ])

Target scores:
  Context Precision: > 0.75
  Context Recall: > 0.80
  Answer Relevancy: > 0.85
  Faithfulness: > 0.90
```

---

## 6. Advanced Techniques

### Multi-query Expansion
```
Original: "liability caps in vendor contracts"

LLM generates 3 variants:
  1. "maximum liability limits for vendors"
  2. "damage limitation clauses in supplier agreements"
  3. "vendor indemnification and liability provisions"

Run retrieval for all 4 queries, union results, deduplicate, rerank
Result: 40% improvement in recall for complex queries
```

### HyDE (Hypothetical Document Embedding)
```
For poorly-worded or ambiguous queries:
  1. Query: "how do i make my code faster"
  2. LLM generates hypothetical answer:
     "Performance optimization techniques include profiling with cProfile,
     using numpy vectorization instead of loops, caching with functools.lru_cache..."
  3. Embed the hypothetical answer (not the original question)
  4. Search: the hypothetical answer is in the same embedding space as real documents

Benefit: hypothetical answer contains technical vocabulary that matches documentation
```

---

## 7. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Chunking | Semantic boundaries | Fixed size | Semantic chunks have higher retrieval quality |
| Retrieval | Hybrid (dense + sparse) | Dense only | +15-20% recall; minimal added complexity |
| Reranker | Cross-encoder (top-50 → 5) | None | +25% accuracy; adds 100ms acceptable for quality |
| Vector DB | Qdrant | Pinecone, Weaviate | Open source; tenant filtering; self-hostable |
| Tenant isolation | Filter at query time | Separate indexes | Balance: performance vs cost (separate indexes for high-compliance) |
| LLM | GPT-4o | Claude 3.5, Llama 3 | Quality on complex queries; grounding instructions |
| Citations | Inline + summary | None | Increases user trust; enables source verification |

---

## 8. Production Failure Modes

```
1. Retrieval failure (wrong chunks retrieved):
   Symptom: model answers incorrectly but confidently
   Detection: faithfulness score drops in continuous eval
   Fix: tune chunking, add more overlap, improve hybrid weights

2. Context overflow (too much context → lost in middle):
   Symptom: model ignores relevant middle chunks
   Fix: reorder context (most relevant first and last), reduce top-k

3. Hallucination on empty retrieval:
   Symptom: no relevant chunks found, but model invents answer
   Fix: add retrieval confidence threshold; if max score < 0.6, return "no info"
   Prompt: "If the answer is not in the context, say so explicitly."

4. Stale data:
   Symptom: document updated but old version returned
   Fix: on document update, delete all old chunks by doc_id, re-index

5. Tenant bleed-through bug:
   Symptom: cross-tenant data appears
   Fix: multiple isolation layers; automated cross-tenant access tests in CI
```

---

## 9. Cost Analysis

```
For 5M queries/day, 10M documents:

Embedding costs (query time):
  5M queries × 1 embedding × $0.0001/1K tokens × 100 tokens = $50/day

Reranking costs (Cohere Rerank):
  5M queries × 50 docs × $0.001/1K docs = $250/day

LLM generation costs (GPT-4o, $5/1M input, $15/1M output):
  5M queries × 1,000 input tokens × $5/1M = $25,000/day
  5M queries × 300 output tokens × $15/1M = $22,500/day
  LLM total: $47,500/day

Infrastructure:
  Qdrant cluster (3 nodes, 16 cores, 64GB RAM each): ~$300/day
  Elasticsearch cluster (3 nodes): ~$200/day
  GPU servers for embedding generation: ~$100/day
  Total infra: ~$600/day

TOTAL: ~$48,400/day ≈ $1.45M/month

Optimization strategies:
  - Semantic caching (30% repeat queries) → cache LLM responses → save $14,250/day
  - Use Claude Haiku for simple queries (80% of traffic) → 10× cheaper LLM
  - Use local embedding model (E5-large) → eliminate $50/day embedding cost
  Optimized total: ~$15,000/day ≈ $450K/month (70% cost reduction)
```

---

## 10. Interview Discussion Points

**The hardest problem is chunking.** Most RAG failures trace back to poor chunking: splitting a sentence mid-thought, separating a table from its caption, or chunking a contract clause that only makes sense in context. Before adding complex retrieval strategies, get chunking right for your document types.

**Hybrid retrieval is almost always better.** The incremental cost (add Elasticsearch) is low, and the recall improvement (+15-20%) is significant. The only exception: extremely semantic queries where keywords are meaningless.

**Faithfulness vs. completeness tension.** If you only allow the model to use retrieved context, you get high faithfulness but may miss answers that require knowledge beyond the documents. For enterprise RAG, faithfulness wins (hallucinations are worse than "I don't know").

**Monitoring for RAG drift.** Unlike static software, RAG quality degrades silently as: (1) documents go stale, (2) query distribution shifts, (3) embedding model updates change the embedding space. Run RAGAS evaluation weekly and alert on degradation.

**When to upgrade the pipeline.** Start simple: single retrieval pass + GPT-4. Add complexity only when measurements show where quality fails: low recall → add hybrid; low faithfulness → improve grounding prompts; slow latency → add caching; complex queries → add multi-query expansion.
