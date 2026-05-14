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

**Q: How do you evaluate a RAG system?**
A: Evaluate two independent components: (1) Retrieval quality: context recall (did we retrieve all relevant docs?), context precision (fraction of retrieved docs that are relevant). (2) Generation quality: faithfulness (does the answer contradict retrieved context?), answer relevance (does the answer address the question?). Use frameworks like RAGAS or TruLens. Build a golden test set with hand-curated (question, context, answer) triples. Track all four metrics separately so you can diagnose whether failures are retrieval failures or generation failures.

**Q: When should you apply advanced RAG vs. standard RAG?**
A: Start with standard RAG and evaluate accuracy on a labeled query set. Apply advanced techniques only when standard RAG falls below your accuracy target. Multi-query or HyDE are low-cost additions (1-2 extra LLM calls, 10-30% recall improvement). Agentic RAG adds 5-30× latency but handles multi-hop queries. Graph RAG requires expensive indexing ($10-100/1M tokens) but unlocks global/thematic queries. Choose the lowest-complexity technique that achieves your accuracy goal.

**Q: What are the four RAGAS metrics and what does each measure?**
A: Context recall — fraction of ground-truth relevant information that was retrieved; measures retrieval completeness. Context precision — fraction of retrieved content that is actually relevant; measures retrieval focus. Faithfulness — fraction of answer statements supported by the retrieved context; measures hallucination rate. Answer relevance — how directly the answer addresses the question; measures response quality. Faithfulness can be measured without ground-truth answers (compare answer to retrieved context); context recall requires ground-truth (what should have been retrieved). Diagnose: if faithfulness is low → LLM hallucination problem; if context recall is low → retrieval problem.

**Q: How does the complexity-latency-quality tradeoff differ across advanced RAG strategies?**
A: Multi-query and HyDE: 1.5-2× latency, 10-30% recall improvement, low complexity — best starting point. Agentic RAG: 5-30× latency, significant accuracy gain on multi-hop queries, high complexity — justified for research/analyst workflows. Graph RAG: 100× indexing cost, 10× query latency, major quality gain on thematic/global queries, very high complexity — justified only for large stable corpora where global queries are critical. Self-RAG: requires fine-tuning, adaptive latency, strong faithfulness — justified when faithfulness is the top priority.

**Q: When should you use Graph RAG instead of standard vector-based RAG?**
Graph RAG excels when your data has rich entity relationships that vector similarity alone cannot capture — for example, organizational hierarchies, citation networks, supply chains, or knowledge graphs. Standard vector RAG finds semantically similar chunks but misses structural relationships ("Who reports to the VP of Engineering?" requires traversing an org graph, not embedding similarity). Graph RAG constructs a knowledge graph from documents (entities as nodes, relationships as edges), then combines graph traversal with vector retrieval. Use Graph RAG when: (1) queries require multi-hop reasoning across entities ("What products does Company X's main competitor sell?"); (2) data has inherent graph structure (legal case citations, medical drug interactions); (3) summarization across many documents is needed (Microsoft's Graph RAG uses community detection to create hierarchical summaries). Avoid Graph RAG for simple factual lookups or when entity extraction quality is poor — garbage-in-garbage-out is amplified in graph construction.

**Q: How does Corrective RAG (CRAG) work and what problem does it solve?**
CRAG adds a self-correction loop that evaluates retrieval quality before generating an answer, solving the problem of LLMs generating confident but wrong answers from irrelevant retrieved chunks. The workflow: (1) retrieve documents normally; (2) a lightweight evaluator (fine-tuned model or LLM prompt) scores each retrieved document as "Correct," "Incorrect," or "Ambiguous"; (3) if documents are "Correct," proceed to generation; (4) if "Incorrect," trigger a web search or alternative retrieval to find better sources; (5) if "Ambiguous," combine original retrieval with web search results. This is critical because standard RAG has no quality gate — if the retriever returns irrelevant chunks, the LLM hallucinates an answer from them rather than saying "I don't know." CRAG reduces hallucination by 20-30% compared to naive RAG on knowledge-intensive benchmarks. The tradeoff is added latency (100-300ms for the evaluation step) and complexity.

**Q: What training is required for Self-RAG and is it practical for production?**
Self-RAG requires training the LLM to generate special reflection tokens ([Retrieve], [IsREL], [IsSUP], [IsUSE]) that control retrieval decisions and quality assessment inline during generation. The model learns when to retrieve (not every query needs it), whether retrieved content is relevant, whether the generation is supported by retrieved evidence, and whether the response is useful. Training requires: (1) a base LLM (7B+), (2) ~150K training examples with reflection token annotations (generated by GPT-4 in the original paper), (3) standard instruction fine-tuning. For production practicality: Self-RAG adds complexity but enables the model to be its own quality controller without external evaluator components. The main challenge is the annotation pipeline — generating reliable reflection token labels requires a capable teacher model. Consider Self-RAG when you need tight integration between retrieval and generation decisions and can afford the fine-tuning investment. For most production cases, CRAG (no training required) provides 80% of Self-RAG's benefit with much less effort.

**Q: How do you design termination conditions for agentic RAG systems?**
Agentic RAG systems that iteratively retrieve, reason, and refine need explicit termination conditions to prevent infinite loops and control costs. Strategies: (1) maximum iteration count — hard limit of 3-5 retrieval-reasoning cycles; (2) confidence threshold — stop when the model's self-assessed confidence exceeds a threshold (e.g., "I am confident this answer is complete"); (3) information gain — stop when new retrievals don't add information not already in context (measure by embedding similarity of new chunks to existing context); (4) answer stability — stop when the generated answer doesn't change between iterations; (5) token budget — cap total tokens consumed across all iterations. In practice, combine multiple conditions: stop at the earliest of (confidence > 0.9, 5 iterations, or 10K total retrieval tokens). Monitor the distribution of iteration counts in production — if most queries terminate at the maximum, your initial retrieval is likely insufficient. Cost control: agentic RAG can cost 3-10x more per query than single-shot RAG.

**Q: How does query transformation improve RAG retrieval quality?**
Query transformation rewrites the user's original query into one or more forms that are better suited for retrieval, addressing the vocabulary mismatch between user language and document language. Techniques: (1) HyDE (Hypothetical Document Embeddings) — generate a hypothetical answer, then embed that answer to find similar real documents (the hypothetical answer's embedding is closer to relevant documents than the question's embedding); (2) query decomposition — split a complex query into sub-queries ("Compare X and Y" becomes "What is X?" and "What is Y?"); (3) step-back prompting — abstract the query to a more general form ("Why did the 2008 financial crisis happen?" becomes "What causes financial crises?"); (4) query expansion — add synonyms and related terms. HyDE improves retrieval recall by 10-25% on average. The cost is one additional LLM call per query (50-100ms). Always evaluate the impact on your specific domain — query transformation can sometimes hurt performance if the LLM's hypothetical answer is misleading.

**Q: How do you handle multimodal documents (text + tables + images) in a RAG pipeline?**
Multimodal RAG requires separate processing pipelines for each modality, unified indexing, and a multimodal LLM for generation. Approach: (1) document parsing — use layout-aware parsers (Unstructured.io, LlamaParse, Adobe Extract API) to separate text, tables, and images from documents; (2) table handling — convert tables to markdown or structured text, embed as separate chunks with table context (caption, column headers); (3) image handling — generate text descriptions using a VLM (GPT-4o, Claude 3.5), embed the description alongside the image for retrieval; (4) unified index — store all chunk types (text, table-as-text, image-description) in the same vector index with metadata tags for modality type; (5) generation — pass retrieved chunks to a multimodal LLM (GPT-4o, Gemini) that can process both text and images natively. For tables specifically, consider text-to-SQL if the table data is in a database — direct SQL retrieval often outperforms embedding-based retrieval for structured data queries. Main challenge: maintaining alignment between images and their surrounding text context during chunking.

---

## Strategy Deep-Dives

Each strategy has a comprehensive standalone reference with 10+ senior-AI-engineer-level Q&As:

| Strategy | File | Key Topics |
|---------|------|-----------|
| Query Transformation | [query_transformation.md](query_transformation.md) | Query rewriting, HyDE, multi-query expansion, step-back prompting |
| Agentic RAG | [agentic_rag.md](agentic_rag.md) | Iterative retrieval, sufficiency checks, FLARE, loop prevention |
| Graph RAG | [graph_rag.md](graph_rag.md) | Entity/relation extraction, Leiden clustering, community summaries, global vs local |
| Multimodal RAG | [multimodal_rag.md](multimodal_rag.md) | PDF tables/charts, CLIP embeddings, vision-LLM descriptions |
| Self-RAG | [self_rag.md](self_rag.md) | Fine-tuned reflection tokens, adaptive retrieval, faithfulness checking |
| Corrective RAG | [corrective_rag.md](corrective_rag.md) | Relevance scoring, web-search fallback, CRAG vs Self-RAG |

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
