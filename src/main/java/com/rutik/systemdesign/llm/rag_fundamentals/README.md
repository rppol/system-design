# RAG Fundamentals

## 1. Concept Overview

RAG (Retrieval-Augmented Generation) solves a fundamental problem with LLMs: they have a fixed knowledge cutoff and can't access private or up-to-date information. RAG extends LLMs with a retrieval component that fetches relevant documents at query time, injecting them into the prompt as context before generation.

The intuition: instead of asking an LLM to "remember" an answer from training (which may be stale or absent), RAG turns the LLM into a reader that synthesizes information from provided documents — much like how a human researcher reads relevant sources before answering.

RAG is now the dominant architecture for enterprise LLM applications: customer support, internal knowledge bases, documentation Q&A, research assistants. It enables LLMs to answer questions accurately over millions of private documents without expensive fine-tuning.

---

## Intuition

> **One-line analogy**: RAG turns an LLM from a closed-book exam (relying only on memorized knowledge) to an open-book exam (reading relevant sources before answering).

**Mental model**: A base LLM knows only what was in its training data (knowledge cutoff). RAG adds a retrieval step: when you ask a question, first search a document corpus for relevant chunks, stuff those chunks into the context window, then let the LLM synthesize an answer from the provided text. The LLM acts as a reader and synthesizer, not a memory bank. If the answer isn't in the retrieved chunks, a well-aligned LLM will say "I don't know" rather than hallucinating.

**Why it matters**: RAG is the dominant architecture for enterprise LLM applications — it enables accurate answers over private, up-to-date knowledge bases without expensive fine-tuning. It's why corporate chatbots can answer questions about internal documents, and why AI search engines can cite recent sources.

**Key insight**: The quality of RAG is determined by retrieval quality — if the right documents aren't retrieved, the LLM can't generate the right answer no matter how capable it is. "Garbage in, garbage out" applies to retrieval.

---

## 2. Core Principles

- **Separation of concerns**: Retrieval handles access to current/private information; generation handles synthesis and reasoning.
- **Context window as the interface**: Retrieved documents are injected into the LLM's context — the retrieval quality directly bounds RAG quality.
- **Chunking strategy determines recall**: How you split documents determines what can be retrieved. Poor chunking = poor recall.
- **Retrieval quality > Generation quality**: If the right context isn't retrieved, even GPT-4 can't produce a correct answer.
- **Grounding reduces hallucination**: LLMs anchored to retrieved documents hallucinate significantly less than relying on parametric memory alone.
- **Evaluation is hard**: RAG evaluation requires measuring both retrieval quality (is the right doc found?) and generation quality (is the answer correct and grounded?).

---

## 3. Components

### 3.1 Chunking

Documents must be split into chunks before embedding. Chunk size and strategy significantly impact retrieval quality.

**Fixed-size chunking** (simplest):
```
text = "Long document..."
chunks = [text[i:i+500] for i in range(0, len(text), 500)]
# Problem: splits mid-sentence, destroys context
```

**Sentence/paragraph chunking** (better):
```
Split on: paragraph breaks, sentence boundaries, section headers
Target: 100-500 tokens per chunk
Overlap: 20-50 tokens between consecutive chunks (preserves boundary context)
```

**Semantic chunking** (best):
```
1. Embed consecutive sentences
2. When cosine similarity drops significantly between adjacent sentences:
   → mark as chunk boundary (topic shift detected)
Produces variable-size chunks aligned with semantic content
```

**Hierarchical chunking**:
```
Parent chunk: entire section (~1000 tokens)
Child chunks: paragraphs within section (~200 tokens)

Retrieve: small child chunks (precise)
Context: provide parent chunk (complete context)
```

**Typical chunk sizes:**
```
Q&A tasks:      100-300 tokens (precise, specific answers)
Summarization:  500-1500 tokens (more context needed)
Code:           1 function or class per chunk (semantic boundaries)
Legal docs:     1 clause per chunk (natural legal unit)
```

### 3.2 Embedding

Convert text chunks to dense vectors for similarity search:

```python
from sentence_transformers import SentenceTransformer

model = SentenceTransformer("BAAI/bge-base-en-v1.5")

chunks = ["Paris is the capital of France.", "The Eiffel Tower is in Paris."]
embeddings = model.encode(chunks)  # shape: [2, 768]
```

At query time, embed the user query and find nearest neighbor chunks.

### 3.3 Retrieval

**Dense retrieval** (semantic):
- Query embedding → ANN search in vector DB
- Finds semantically similar chunks even with different wording
- Best for: conceptual questions, paraphrased queries

**Sparse retrieval** (BM25):
- Classical term-frequency scoring
- Exact keyword matching; excellent for rare proper nouns, IDs, technical terms
- Best for: "Find all mentions of regulation X.Y.Z"

**Hybrid retrieval** (recommended):
```
Dense score + Sparse score combined via:
  RRF (Reciprocal Rank Fusion):
    final_score = Σ 1/(k + rank_i)  where k=60

  Weighted combination:
    final_score = α × dense_score + (1-α) × sparse_score
```

### 3.4 Reranking

After initial retrieval (top-100 candidates), a cross-encoder reranker selects the best top-K (K=3-10):

```
Query + Candidate → Cross-Encoder → Relevance Score

Cross-encoders read query and candidate together → much better relevance judgment
  than comparing separate embeddings

Models:
  - BGE-reranker-large (best open source)
  - Cohere Rerank API (best managed)
  - ColBERT (token-level matching, faster than full cross-encoder)
```

Cost: ~50ms for reranking top-100; worth it for precision-critical tasks.

### 3.5 Context Injection

Retrieved chunks are inserted into the LLM prompt:

```
System: You are a helpful assistant. Answer based on the provided context only.
        If the answer is not in the context, say "I don't know."

Context:
[Chunk 1]: Paris, officially the City of Paris, is the capital and largest city of France...
[Chunk 2]: The Eiffel Tower is a wrought-iron lattice tower on the Champ de Mars in Paris...

User: What is the capital of France?
Assistant: Based on the provided context, Paris is the capital of France.
```

---

## 4. Architecture Diagrams

### Standard RAG Pipeline
```
User Query
     |
     v
[Query Processing]
  Optional: query rewriting, expansion, decomposition
     |
     v
[Retrieval]
  ┌─────────────────────────────────────────┐
  │  Dense: embed query → ANN search        │
  │  Sparse: BM25 keyword matching          │
  │  → Merge via RRF → top-100 candidates  │
  └─────────────────────────────────────────┘
     |
     v
[Reranking]
  Cross-encoder scores top-100 → top-5 final
     |
     v
[Context Assembly]
  Format retrieved chunks with metadata
  Respect context window limit
     |
     v
[LLM Generation]
  Generate answer grounded in context
     |
     v
Response + [Source Citations]
```

### Indexing Pipeline
```
Documents (PDFs, HTML, Docs, DBs)
     |
     v
[Parsing & Extraction]
  PDF → text (pdfminer, PyMuPDF)
  HTML → text (BeautifulSoup)
  Handle tables, figures separately
     |
     v
[Chunking]
  Strategy: semantic / paragraph / hierarchical
  Add metadata: source URL, page, section title
     |
     v
[Embedding Generation]
  Batch embed chunks (GPU-accelerated)
     |
     v
[Vector DB Upsert]
  Store: {chunk_id, embedding, metadata, text}
  Index: HNSW for fast ANN search
```

### Query Routing (Multi-Source)
```
User Query
     |
     v
[Query Router]
  ↓ "What is X?" (factual)  → Knowledge Base RAG
  ↓ "Find order #12345"      → SQL Database
  ↓ "Current stock price?"   → Web Search API
  ↓ "Summarize last meeting"  → Meeting Notes RAG
```

---

## 5. How It Works — Detailed Mechanics

### Chunking Strategies in Detail

**Choosing chunk size:**
```
Too small (50 tokens):
  + Precise retrieval
  - Missing surrounding context; sentences cut off mid-thought

Too large (2000 tokens):
  + Full context preserved
  - Embedding represents average of many ideas; low retrieval precision

Sweet spot (200-500 tokens):
  + Enough context for coherent meaning
  + Precise enough for semantic matching
```

**Chunk overlap:**
```
Without overlap:
  "...the model was trained on [CHUNK BOUNDARY] datasets from 2023..."
  → Key information split across chunks

With 100-token overlap:
  Chunk 1: "...the model was trained on datasets..."
  Chunk 2: "...trained on datasets from 2023..."
  → Same boundary appears in both chunks
  → Both chunks contain complete information
```

### Metadata Filtering

Beyond semantic similarity, filter by metadata to scope retrieval:

```python
results = vector_db.query(
    embedding=query_embedding,
    filter={
        "source": {"$eq": "Q4_2024_earnings.pdf"},
        "page": {"$gte": 5, "$lte": 20},
        "date": {"$gte": "2024-01-01"}
    },
    top_k=10
)
```

Metadata filtering is often more important than embedding quality for precision-critical applications.

### Handling Context Window Limits

For long-context questions with many relevant chunks:

```
Strategy 1: Truncation (simple, loses context)
  Sort by relevance, take top-K that fit in window

Strategy 2: Map-reduce (for long document Q&A)
  Map: answer question independently for each chunk
  Reduce: combine partial answers into final answer

Strategy 3: Hierarchical (for hierarchical content)
  Retrieve summary first, drill down only if needed

Strategy 4: Long context models
  Use Gemini 1.5 Pro (1M tokens) or Claude 3.5 (200K)
  Load entire document set into context (expensive but simple)
```

---

## 6. Real-World Examples

### Notion AI
- RAG over user's workspace: notes, databases, pages
- Chunking: paragraph-level with section metadata
- Dense retrieval + metadata filtering by workspace/page
- Context: top-3 chunks injected before LLM synthesis

### GitHub Copilot Chat
- RAG over repository: open files, recently edited files, imports
- Semantic search over codebase for relevant functions/classes
- Prioritizes: current file > imported files > same-language files

### Perplexity AI
- Web search at query time → parse top web results
- Clean, chunk, embed in real-time
- LLM synthesizes with citations
- Recency filter: prioritize recent content for time-sensitive queries

### AWS Bedrock Knowledge Bases
- Managed RAG with S3 data sources
- Automated chunking, embedding (Titan or Cohere), storage in OpenSearch/pgvector
- Handles incremental updates automatically

---

## 7. Tradeoffs

| Approach | Quality | Freshness | Cost | Complexity |
|----------|---------|-----------|------|------------|
| Pure LLM (no RAG) | Lower | Fixed (training cutoff) | Low | Low |
| RAG | High | Real-time | Medium | Medium |
| Fine-tuning | High for domain style | Fixed | High upfront | High |
| RAG + Fine-tuning | Best | Real-time | High | High |
| Long context (no retrieval) | High | Fresh | Very high (per call) | Low |

| Retrieval Method | Recall | Latency | Setup |
|-----------------|--------|---------|-------|
| Dense only | Good | Fast | Medium |
| Sparse (BM25) only | Medium (keyword exact) | Fast | Simple |
| Hybrid | Best | Medium | Medium |
| Hybrid + Rerank | Best+ | Slower | Complex |

---

## 8. When to Use / When NOT to Use

### Use RAG When:
- Knowledge changes frequently (news, product catalogs, documentation)
- Private/proprietary knowledge base (can't train model on it)
- Need source attribution (user wants to verify answers)
- Multiple knowledge domains (route to different indices)
- Fast iteration needed (no training cycle)

### Use Fine-Tuning Instead When:
- Teaching the model a specific style or format (not knowledge)
- Repeated, high-volume use of the same domain (amortize training cost)
- Latency requirements prevent long context injection

### Combine RAG + Fine-Tuning When:
- Need best quality on domain (fine-tune for style/format + RAG for knowledge)
- Examples: medical Q&A (fine-tune for clinical tone + RAG for drug info)

---

## 9. Common Pitfalls

1. **Poor chunking**: Splitting mid-sentence destroys meaning. Use sentence-boundary chunking at minimum.
2. **Chunk too large**: Large chunks embed poorly (embedding averages away specifics). 500 tokens is often the max.
3. **No reranking**: Initial retrieval has 70-80% precision; reranking gets to 90%+. Skipping costs significantly.
4. **Ignoring metadata filtering**: Without filtering, a query about "Q4 2024 revenue" might return Q4 2021 documents.
5. **Context stuffing**: More retrieved chunks isn't always better — model may hallucinate more with irrelevant context. Keep to 3-5 focused chunks.
6. **Missing source attribution**: Users should be able to verify answers. Always return source metadata with responses.
7. **No fallback for no-answer**: If retrieved context doesn't contain the answer, model should say "I don't know" not hallucinate. Enforce with system prompt.

---

## 10. Technologies & Tools

| Tool | Type | Notes |
|------|------|-------|
| **LlamaIndex** | RAG framework | Best-in-class RAG abstractions |
| **LangChain** | RAG framework | More general; complex abstraction |
| **Haystack** | RAG pipeline | Production-focused; pipeline-based |
| **Pinecone** | Vector DB | Best managed option; serverless |
| **Weaviate** | Vector DB | Built-in hybrid search; open source |
| **Qdrant** | Vector DB | Fast; Rust-based; open source + cloud |
| **Chroma** | Vector DB | Best for local development |
| **pgvector** | PostgreSQL extension | No new infra; SQL queries |
| **Cohere Rerank** | Managed reranker | Best-in-class managed reranking API |
| **BGE-reranker** | Open source reranker | Excellent; free to self-host |
| **RAGAS** | RAG evaluation | Faithfulness, relevance, context recall |

---

## 11. Interview Questions with Answers

**Q: What is RAG and why is it preferred over fine-tuning for knowledge-intensive tasks?**
A: RAG (Retrieval-Augmented Generation) retrieves relevant documents at query time and injects them into the LLM's context. It's preferred for knowledge-intensive tasks because: (1) knowledge changes frequently — RAG serves fresh data; (2) private data can't be in training data; (3) source attribution is built-in; (4) no training cycle needed. Fine-tuning teaches the model *style* and *behavior*, not knowledge — RAG handles the knowledge.

**Q: How would you choose the right chunk size for a RAG system?**
A: Depends on the task. For precise Q&A (specific facts), smaller chunks (100-300 tokens) work better — embeddings represent specific information. For multi-hop reasoning or summarization, larger chunks (500-1000 tokens) preserve context. Key factors: (1) What's the granularity of questions? (2) How dense is the text? (3) What's the context window budget? Always test empirically on your data with your retrieval model.

**Q: What is hybrid search and why does it outperform dense-only retrieval?**
A: Hybrid search combines dense (vector) retrieval and sparse (BM25/keyword) retrieval. Dense retrieval handles semantic similarity — paraphrased queries find semantically similar documents even with different words. BM25 handles exact keyword matching — rare proper nouns, product IDs, and technical terms match exactly. They complement each other: combining via RRF (Reciprocal Rank Fusion) consistently outperforms either alone by 5-15% recall.

**Q: What is a cross-encoder reranker and when should you use it?**
A: A cross-encoder takes the query and a candidate document together as a single input and outputs a relevance score. This joint encoding is much more accurate than comparing separately embedded vectors (bi-encoder). Use reranking as a second stage: bi-encoder retrieves top-100 candidates (fast), cross-encoder reranks to top-5 (slow but accurate). Adds ~50-100ms but significantly improves precision.

**Q: How do you handle questions that span multiple documents?**
A: Multi-hop retrieval: (1) Decompose the question into sub-questions; (2) retrieve and answer each sub-question; (3) combine sub-answers for the final answer. Alternative: retrieve top-K documents, use long-context LLM to synthesize across all of them. For structured queries across many documents, use a map-reduce approach: answer the question for each document independently, then combine. This is the "multi-doc summarization" or "map-reduce" RAG pattern.

---

## 12. Best Practices

1. **Evaluate retrieval separately from generation** — use retrieval metrics (recall@K, MRR) to debug retrieval before blaming the LLM.
2. **Always use hybrid search** — pure dense or pure sparse consistently underperforms hybrid.
3. **Add metadata to every chunk** — source, date, section, page number; enables filtering and source attribution.
4. **Keep a small, focused context** — 3-5 high-quality chunks beats 20 mediocre chunks.
5. **Test your chunking** — run test queries and inspect what chunks get retrieved; bad chunks are often obvious.
6. **Use a "no answer" fallback** — instruct the LLM to say it doesn't know if context doesn't contain the answer.
7. **Monitor retrieval quality in production** — track user feedback ("was this helpful?") and correlate with retrieval scores.

---

## 13. Case Study: Building a RAG System for 10,000 Internal Documents

**Context:** Enterprise with 10,000 internal documents (policies, SOPs, HR guides, product docs). Employees spend 2 hours/day searching for information.

**Architecture:**

**Indexing (one-time + incremental):**
```
Documents: PDFs, Word docs, Confluence pages, Notion pages
Parser: Apache Tika for PDFs; native APIs for Confluence/Notion
Chunking: 512-token chunks, 50-token overlap, sentence boundaries
  + section headers as metadata
Embedding: BGE-base-en-v1.5 (768d, fast, good quality)
Storage: Qdrant (self-hosted, 10K × 512 chunks = ~5M vectors, ~2GB)
BM25 index: Elasticsearch (same documents, keyword search)
```

**Query pipeline:**
```
Query → query rewriting (LLM expands acronyms, adds context)
      → Dense retrieval top-100 + BM25 top-100
      → RRF merge → top-100 combined
      → BGE-reranker-base → top-5
      → Inject into GPT-4o with source citations
      → Return answer + links to source documents
```

**Results:**
- Information retrieval accuracy: 87% (vs. 42% with keyword search alone)
- Avg time to find information: 12 seconds (vs. 2 hours manual)
- Employee satisfaction score: 8.7/10
- Hallucination rate: 4% (reduced to 1% by adding "say I don't know" instruction)
