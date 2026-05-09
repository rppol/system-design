# Advanced RAG

## 1. Concept Overview

Standard RAG (retrieve → generate) works well for simple Q&A but breaks down on complex queries: multi-hop questions, queries requiring synthesis across many documents, questions about structured data, or tasks where the LLM needs to iteratively refine its retrieval strategy.

Advanced RAG encompasses techniques that go beyond the basic pipeline: query transformation before retrieval, agentic/iterative retrieval where the LLM decides what to retrieve next, graph-based retrieval for structured knowledge, multi-modal retrieval for images and tables, and rigorous evaluation frameworks.

These techniques push RAG from a simple lookup system to a reasoning system that can answer complex questions that require synthesis, comparison, and multi-step inference.

---

## Intuition

> **One-line analogy**: Advanced RAG is like upgrading from a library card catalog to a research librarian who understands your question, fetches multiple sources, and iterates until they find what you actually need.

**Mental model**: Basic RAG retrieves once and generates. Advanced RAG recognizes that complex questions require multiple retrieval rounds, query reformulation, reasoning across a knowledge graph, or iterative verification. Agentic RAG lets the LLM decide what to retrieve next based on what it finds, like a researcher who reads one paper, decides they need the cited papers too, fetches those, and synthesizes across all sources.

**Why it matters**: Simple Q&A works with basic RAG; multi-hop questions ("who was the CEO of the company that acquired X?"), comparative analysis, and synthesis across heterogeneous sources require advanced techniques. Graph RAG also unlocks structured knowledge that flat vector search misses.

**Key insight**: The LLM should be a first-class participant in the retrieval process, not just a passive consumer of retrieved chunks — it knows best what information is still missing and what to retrieve next.

---

## 2. Core Principles

- **Query and context are rarely aligned**: User queries are often ambiguous, vague, or assume context the retriever doesn't have. Query transformation bridges this gap.
- **Retrieval should be iterative**: For complex questions, one retrieval step is insufficient. The LLM should decide what additional information to retrieve based on what it already has.
- **Structure matters**: Many knowledge sources are graphs (entity relationships), tables (structured data), or code — plain vector search misses these structures.
- **Evaluate RAG components independently**: Decompose into retrieval quality (context recall, context precision) and generation quality (faithfulness, answer relevance).
- **Diminishing returns on context**: Adding more retrieved documents doesn't always help; too much irrelevant context actively degrades generation quality.

---

## 3. Types / Strategies

### 3.1 Query Transformation

Transform the user's raw query before retrieval to improve recall.

**Query rewriting**: LLM rewrites query to be more explicit and retrieval-friendly:
```
Original: "What did they decide about the budget?"
Rewritten: "What budget decisions were made by the executive team in Q4 2024 strategy meeting?"
```

**HyDE (Hypothetical Document Embeddings)**:
```
1. Generate a hypothetical answer to the query
2. Embed the hypothetical answer (not the query)
3. Use this embedding for retrieval (documents similar to the hypothetical answer)

Why it works: The hypothetical answer is in "document space" not "query space"
  - Better alignment with how answers appear in documents
  - Especially useful when query phrasing differs from document phrasing
```

**Multi-query expansion**:
```
User query: "How does React handle state?"

Generate 3 alternative phrasings:
  1. "React useState hook and state management"
  2. "Component state in React functional components"
  3. "React state updates and re-rendering"

Retrieve for all 3 → merge results → deduplicate
Improves recall when the answer could be phrased many ways
```

**Step-back prompting**:
```
Original: "What was the GDP growth rate in Brazil in Q2 2023?"
Step-back: "What factors affect Brazil's GDP?"
Retrieve: both specific and general context
```

### 3.2 Agentic RAG (Iterative Retrieval)

The LLM decides what to retrieve, evaluates whether it has enough information, and retrieves again if needed:

```
Step 1: LLM analyzes query, generates retrieval query
Step 2: Retrieve → inject into context
Step 3: LLM evaluates: "Do I have enough information to answer?"
  If yes → generate final answer
  If no → generate new retrieval query → go to Step 2
  (Max iterations: 3-5 to prevent loops)

Example:
  Query: "Compare the revenue of Anthropic and OpenAI in 2023"
  Iteration 1: retrieve "Anthropic 2023 revenue" → found partial info
  Iteration 2: retrieve "OpenAI 2023 revenue" → found info
  Iteration 3: retrieve "Anthropic vs OpenAI revenue comparison" → confirm
  Generate: comparative answer with citations
```

### 3.3 Graph RAG (Microsoft, 2024)

Build a knowledge graph from documents; use graph traversal + LLM synthesis for complex queries:

```
Indexing phase:
  1. Extract entities and relationships from all documents
     "Microsoft" → [ACQUIRED] → "Activision Blizzard"
     "Microsoft" → [MADE_INVESTMENT_IN] → "OpenAI"
  2. Build a graph: entities as nodes, relationships as edges
  3. Cluster graph into communities
  4. Summarize each community (hierarchical summary tree)

Query phase:
  Global queries (e.g., "What are Microsoft's AI investments?"):
    → Query community summaries (efficient, broad coverage)
    → LLM synthesizes from community summaries

  Local queries (e.g., "Who did Microsoft acquire in gaming?"):
    → Traditional vector search within relevant community
    → Answer from specific documents

Advantage: Handles "What themes appear across all documents?" type queries
  that vector search completely fails on (no single chunk contains the answer)
```

### 3.4 Multi-Modal RAG

Extend retrieval beyond text to images, tables, charts, and PDFs with mixed content:

```
Document types:
  PDF with tables: extract table data as structured text + embed
  PDF with charts: OCR + chart-to-text conversion; or embed the image
  PowerPoint slides: slide → image → vision LLM caption → embed caption
  Diagrams: embed image directly using CLIP/SigLIP

Multi-modal retrieval:
  Text query → text embedding (compare with text chunks)
  Text query → CLIP text embedding (compare with image embeddings)
  Merge results, provide relevant images AND text to LLM

Generation:
  Use vision-capable LLM (GPT-4o, Gemini 1.5 Pro, Claude 3.5)
  LLM sees both text context and relevant images
```

### 3.5 Self-RAG

Model decides when to retrieve:

```
LLM is fine-tuned with special tokens:
  [Retrieve]: LLM decides a retrieval is needed
  [No Retrieve]: LLM answers from parametric knowledge
  [Relevant]: Retrieved passage is relevant
  [Not Relevant]: Passage not relevant
  [Supported]: LLM's statement is supported by context
  [Contradicts]: LLM's statement contradicts context

During inference:
  Generate → if [Retrieve] token appears → trigger retrieval → continue
  After generation → check [Supported] tokens

Benefits: adaptive retrieval (only when needed); built-in faithfulness checking
Requires: fine-tuning a specialized model variant
```

### 3.6 Corrective RAG (CRAG)

Evaluates retrieved documents and corrects the retrieval strategy if low quality:

```
Step 1: Retrieve top-K documents
Step 2: Relevance evaluator scores each document
  If score > threshold: proceed to generation
  If score < threshold:
    → Trigger web search for fresh/better information
    → Filter and refine retrieved content

CRAG handles knowledge base gaps by falling back to web search
Prevents using low-quality retrieved context as grounding
```

---

## 4. Architecture Diagrams

### Agentic RAG with Reflection
```
Query
  |
  v
[Query Analysis]
  "What sub-questions do I need to answer?"
  |
  v
[Retrieval Step 1] → Context 1
  |
  v
[Sufficiency Check]
  "Do I have enough information?"
  |
  +-- YES --> [Generation] --> Answer
  |
  +-- NO --> [Gap Analysis]
               "What am I still missing?"
               |
               v
             [Retrieval Step 2] → Context 2
               |
               v
             (loop, max N times)
```

### Graph RAG Architecture
```
Documents
  |
  v
[Entity/Relation Extraction] (LLM-based)
  |
  v
[Knowledge Graph]
  Nodes: entities (people, orgs, concepts)
  Edges: relationships with properties
  |
  +-----> [Community Detection] (Leiden algorithm)
  |         |
  |         v
  |       [Community Summaries] (LLM-generated)
  |
  v
Query
  |
  +-- Global query --> search community summaries
  |
  +-- Local query  --> entity search → subgraph extraction → context
  |
  v
[LLM Synthesis]
```

### RAG Evaluation Dimensions
```
Context Recall:    How much of the ground truth context was retrieved?
Context Precision: What fraction of retrieved context is actually relevant?
Faithfulness:      Does the answer contradict the context?
Answer Relevance:  Does the answer address the question asked?

Perfect RAG system:
  Context Recall = 1.0 (found all relevant docs)
  Context Precision = 1.0 (no irrelevant docs retrieved)
  Faithfulness = 1.0 (answer only uses provided context)
  Answer Relevance = 1.0 (answer directly addresses question)
```

---

## 5. How It Works — Detailed Mechanics

### Sentence Window Retrieval

Retrieve small child sentences, expand to parent window for context:

```
Index: sentence-level embeddings (very precise retrieval)
At query time:
  1. ANN search returns top-K sentences
  2. For each retrieved sentence, fetch surrounding ±2 sentences (window)
  3. Use expanded window as LLM context

Effect: precision of sentence-level retrieval + context of paragraph-level chunks
Best for: long documents with diverse content (technical manuals, research papers)
```

### Fusion Retrieval + FLARE

FLARE (Forward-Looking Active REtrieval): predict future content to determine when to retrieve:

```
LLM is generating response token by token
When LLM predicts a low-confidence continuation:
  → This signals the LLM needs more information
  → Trigger retrieval with the predicted continuation as query
  → Inject retrieved context and continue generation

Unlike standard RAG (retrieve once at the start), FLARE retrieves
  precisely when the model encounters a knowledge gap during generation
```

### Evaluation with RAGAS

```python
from ragas import evaluate
from ragas.metrics import (
    faithfulness,         # Is answer supported by context?
    answer_relevancy,     # Does answer address the question?
    context_recall,       # Did retrieval find all relevant docs?
    context_precision,    # Is retrieved context focused?
)

dataset = Dataset.from_dict({
    "question": [...],
    "answer": [...],     # LLM-generated answers
    "contexts": [...],   # Retrieved contexts
    "ground_truth": [...]  # Reference answers
})

result = evaluate(dataset, metrics=[faithfulness, answer_relevancy, ...])
```

---

## 6. Real-World Examples

### Microsoft Graph RAG (2024)
- Published as open source: graphrag Python library
- Demonstrated dramatically better performance on "global" queries
- Used in Microsoft Copilot for M365 document analysis
- Index time: hours (entity extraction, graph construction, community summarization)
- Particularly strong for: thematic analysis, trend discovery, entity relationship queries

### Perplexity Deep Research
- Agentic multi-step retrieval: searches web iteratively based on query analysis
- 5-20 web searches per complex query, each building on previous results
- LLM synthesizes with inline citations linking to source web pages

### LlamaIndex Advanced RAG
- Sentence window, auto-merging, recursive retrieval implementations
- RecursiveRetriever: first retrieves document summaries, then drills into specific chunks
- Multi-document agents: separate agent per document, orchestrator agent combines

---

## 7. Tradeoffs

| RAG Strategy | Quality | Latency | Cost | Complexity |
|-------------|---------|---------|------|------------|
| Standard RAG | Good | ~200ms | Low | Low |
| Multi-query | Better | ~2× | 2× | Low |
| HyDE | Better | ~1.5× | 1.5× | Low |
| Agentic RAG | Best | 5-30× | 5× | High |
| Graph RAG | Best (global) | 10× query | 100× index | Very High |
| Self-RAG | Very good | adaptive | Moderate | High |

---

## 8. When to Use / When NOT to Use

### Use Advanced RAG When:
- Simple RAG achieves <70% accuracy on your evaluation set
- Queries require multi-hop reasoning ("Which of X's products competed with Y's launch in 2022?")
- Dataset has complex structure (entities, relationships) not captured by text chunks
- Need adaptive retrieval (some queries need lots of context, some need none)

### Use Standard RAG When:
- Simple factual Q&A over well-structured documents
- Latency-sensitive (advanced RAG adds 2-10× latency)
- Cost-sensitive (multiple retrieval + generation rounds cost more)

---

## 9. Common Pitfalls

1. **Over-engineering early**: Start with standard RAG; only add complexity when you have evidence it's needed with evaluation data.
2. **Graph RAG indexing cost**: Building entity graph and community summaries costs $10-100+ per 1M tokens of documents. Not suitable for frequently-changing data.
3. **Agentic loops**: Without iteration limits, agentic RAG can loop indefinitely. Always set max_iterations.
4. **HyDE for factual queries**: HyDE works poorly when the hypothetical answer diverges from reality. Test before deploying.
5. **Evaluation without ground truth**: RAGAS faithfulness can be measured without ground truth; context recall requires ground truth contexts. Build a proper eval set.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **GraphRAG** | Microsoft Graph RAG | Open source; production-ready |
| **LlamaIndex** | Advanced RAG patterns | Best implementations of sentence window, recursive retrieval |
| **RAGAS** | RAG evaluation | Context recall, faithfulness, answer relevance |
| **LangGraph** | Agentic RAG flows | Stateful graphs for multi-step retrieval |
| **DSPy** | Programmatic RAG optimization | Auto-optimize prompts in RAG pipeline |
| **Cohere Rerank 3** | Semantic reranking | Best managed reranker for multi-lingual |
| **Weaviate** | Hybrid search + GraphQL | Built-in hybrid search; good for advanced queries |
| **TruLens** | RAG evaluation | RAG triad: context relevance, groundedness, answer relevance |
| **Arize Phoenix** | Observability | Trace RAG pipeline; identify failure modes |

---

## 11. Interview Questions with Answers

**Q: What is HyDE and when would you use it?**
A: HyDE (Hypothetical Document Embeddings) generates a hypothetical answer to the query using an LLM, then embeds that hypothetical answer for retrieval. This works because the hypothetical answer is in "document space" — it resembles how the real answer would appear in documents, bridging the vocabulary gap between queries (terse, question-like) and documents (verbose, declarative). Best for domains where query phrasing differs significantly from document phrasing (academic papers, legal text). Avoid when the LLM might generate a factually wrong hypothesis that leads retrieval astray.

**Q: What is Graph RAG and what problem does it solve?**
A: Graph RAG builds a knowledge graph from documents with entities and relationships, then clusters it into communities with LLM-generated summaries. Standard RAG struggles with "global" queries that span many documents ("What are the major themes in this corpus?") — no single chunk contains the answer. Graph RAG handles this by querying community summaries that capture themes across many documents. Tradeoff: expensive indexing (entity extraction + community summarization) and longer query latency.

**Q: How do you evaluate a RAG system?**
A: Evaluate two independent components: (1) Retrieval quality: context recall (did we retrieve all relevant docs?), context precision (fraction of retrieved docs that are relevant). (2) Generation quality: faithfulness (does the answer contradict retrieved context?), answer relevance (does the answer address the question?). Use frameworks like RAGAS or TruLens. Build a golden test set with hand-curated (question, context, answer) triples. Track all four metrics separately so you can diagnose whether failures are retrieval failures or generation failures.

**Q: When would you choose agentic RAG over standard RAG?**
A: Agentic RAG (iterative retrieval with LLM deciding what to retrieve next) is worth the added complexity when: (1) queries are multi-hop and require chaining multiple retrievals; (2) it's unclear upfront what sub-questions need answering; (3) initial retrieval often returns irrelevant results that require refinement. The cost is 5-10× latency and complexity. Start with standard RAG and measure accuracy first; only add agentic retrieval when standard RAG fails on your specific query distribution.

---

## 12. Best Practices

1. **Measure before upgrading** — build an evaluation set before adding complexity; quantify the gap advanced RAG closes.
2. **Use Graph RAG for knowledge graphs, standard RAG for documents** — don't over-apply Graph RAG.
3. **Combine multi-query + reranking** — generate 3 alternative queries, retrieve 100 candidates each, merge, rerank to top-5. Simple combination with big quality wins.
4. **Expose retrieval traces to users** — let users see which documents were retrieved; builds trust and helps debug.
5. **Cache query embeddings and common queries** — many users ask the same questions; cache embedding + results.
6. **Monitor latency per component** — separately track embedding time, ANN search, reranking, LLM generation to find bottlenecks.

---

## 13. Case Study: Multi-Hop Question Answering Over Financial Reports

**Problem:** Analysts need to answer complex questions like "Which of our portfolio companies increased revenue AND decreased headcount in Q3 2024?" across 200 earnings reports.

**Why Standard RAG Fails:**
- No single chunk contains a cross-company comparison
- Query requires retrieving from 200 different companies' reports
- Multi-criteria filter (revenue up AND headcount down) can't be expressed as a single embedding

**Advanced RAG Solution:**

```
Phase 1: Structured extraction during indexing
  For each earnings report:
  LLM extracts: company, quarter, revenue (and YoY change), headcount (and YoY change)
  Store as structured metadata alongside text embeddings

Phase 2: Hybrid query
  Step 1: Metadata filter → companies in portfolio in Q3 2024
  Step 2: For each matching company, retrieve "revenue" and "headcount" chunks
  Step 3: LLM evaluates each company's data: revenue up? headcount down?
  Step 4: LLM synthesizes final list with evidence

Phase 3: Multi-hop reasoning
  LLM uses agentic loop:
    Query 1: retrieve Q3 2024 revenue data for all portfolio companies
    Query 2: retrieve Q3 2024 headcount data for all portfolio companies
    Synthesis: join and filter
```

**Results:** 94% accuracy on multi-hop financial queries (vs. 23% for standard RAG), average response time 8 seconds.
