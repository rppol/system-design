# Corrective RAG (CRAG)

## 1. Concept Overview

Corrective RAG (CRAG, Yan et al. 2024) introduces an automatic quality evaluation step between retrieval and generation. After standard retrieval, a lightweight relevance evaluator scores each retrieved document against the query. Based on these scores, CRAG decides one of three actions: proceed to generation (high-quality retrieval), refine the context (mixed quality), or trigger a web search fallback (all retrieved documents are irrelevant).

CRAG addresses a critical gap in standard RAG: the retrieval step is treated as infallible, but in practice retrieval often returns partially irrelevant or completely off-topic documents, especially for queries that span the knowledge base boundary. Using low-quality retrieved context as grounding actively degrades generation quality and increases hallucination rates.

---

## Intuition

> **One-line analogy**: CRAG is like a fact-checker who reviews sources before the journalist writes the story — if all sources are bad, they go find better ones before writing begins.

**Mental model**: Standard RAG retrieves top-K documents and feeds them to the LLM regardless of quality. A query about a recent acquisition might retrieve vaguely related documents about M&A in general. The LLM then generates an answer grounded in wrong context, producing a confident but incorrect response. CRAG intercepts at this point: evaluates each retrieved document's relevance, and if quality is too low, fires a web search to get fresh, relevant information before generation proceeds.

**Why it matters**: Knowledge base gaps are inevitable — the corpus doesn't contain everything, documents go stale, or the query is genuinely outside the indexed content. CRAG's web search fallback gracefully handles these gaps rather than generating hallucinations from irrelevant context.

**Key insight**: A fast, lightweight relevance evaluator (not the full generation LLM) can detect retrieval failures and trigger correction before they propagate to the generated answer.

---

## 2. Core Principles

- **Retrieval quality is variable**: Even good retrieval systems return low-quality results for out-of-distribution queries.
- **Quality evaluation should be fast and separate**: A lightweight relevance classifier is cheaper and faster than using the full generation LLM for this purpose.
- **Web search as the correction mechanism**: When the knowledge base is insufficient, the web provides a broader fallback that can answer most factual queries.
- **Context refinement, not context rejection**: For mixed-quality retrieval (some relevant, some not), refine by keeping the relevant parts rather than discarding all context.
- **Early correction prevents cascading errors**: A wrong retrieved document caught before generation is far less costly than hallucinated content caught after user sees it.

---

## 3. How It Works — Detailed Mechanics

### 3.1 Relevance Evaluation

```
Step 1: Standard retrieval
  Vector DB ANN search → top-K documents (typically K=5-10)

Step 2: Relevance scoring per document
  Relevance evaluator: cross-encoder or LLM-based classifier
  Input:  (query, document)
  Output: relevance score ∈ [0, 1]

  Thresholds (tunable):
    score > 0.7 → Correct (high confidence relevant)
    0.3 < score ≤ 0.7 → Ambiguous (partially relevant)
    score ≤ 0.3 → Incorrect (not relevant)

Step 3: Decision logic
  All documents Correct → proceed to generation with retrieved context
  Mix of Correct + Ambiguous → refine (keep Correct, refine Ambiguous)
  All documents Incorrect/Ambiguous → trigger web search
```

### 3.2 Context Refinement

When documents are partially relevant (Ambiguous), CRAG refines them rather than discarding:

```
Ambiguous document handling:
  1. Extract the most relevant sentences/passages from the document
     Using extractive summarization: identify sentences with highest
     relevance score to the query
  2. Strip irrelevant surrounding content
  3. Use only the relevant excerpts as context

Implementation:
  Use the relevance evaluator on sentence-level chunks of the document
  Keep only sentences with score > threshold
  Combine selected sentences into refined context
```

### 3.3 Web Search Fallback

```
Trigger condition: all retrieved documents score below threshold
  OR no documents retrieved (empty knowledge base)

Web search:
  1. Generate web search query (may differ from original query)
     LLM reformulates for web search: "site:sec.gov Apple Q3 2024 10-Q"
     or add recency filter: "Apple earnings report 2024 Q3"

  2. Execute web search
     Google Search API, Bing Search API, Serper.dev, Tavily

  3. Parse web results
     Fetch top-3 URLs, extract text, clean HTML
     Respect robots.txt and rate limits

  4. Filter web results for relevance
     Apply same relevance evaluator to web results
     Keep only relevant web content

  5. Combine refined knowledge base content (if any) with web content
     Merge and dedup before generation
```

### 3.4 Full CRAG Pipeline

```python
def crag_pipeline(
    query: str,
    retriever,
    relevance_evaluator,
    web_searcher,
    llm,
    correct_threshold: float = 0.7,
    incorrect_threshold: float = 0.3
) -> str:

    # Step 1: Retrieve from knowledge base
    kb_docs = retriever.retrieve(query, top_k=5)

    # Step 2: Score each document
    scored_docs = []
    for doc in kb_docs:
        score = relevance_evaluator.score(query, doc.text)
        label = (
            "correct" if score > correct_threshold else
            "incorrect" if score < incorrect_threshold else
            "ambiguous"
        )
        scored_docs.append((doc, score, label))

    # Step 3: Decision
    any_correct = any(label == "correct" for _, _, label in scored_docs)
    all_incorrect = all(label == "incorrect" for _, _, label in scored_docs)

    context_parts = []

    if not all_incorrect:
        # Keep or refine knowledge base documents
        for doc, score, label in scored_docs:
            if label == "correct":
                context_parts.append(doc.text)
            elif label == "ambiguous":
                refined = refine_document(query, doc.text, relevance_evaluator)
                context_parts.append(refined)

    if all_incorrect or not any_correct:
        # Trigger web search
        web_query = generate_web_query(query, llm)
        web_results = web_searcher.search(web_query, num_results=3)
        for result in web_results:
            score = relevance_evaluator.score(query, result.content)
            if score > incorrect_threshold:
                context_parts.append(result.content)

    if not context_parts:
        return "I cannot find sufficient information to answer this question."

    # Step 4: Generate
    context = "\n\n".join(context_parts)
    return llm.generate(query, context=context)
```

---

## 4. Architecture Diagram

### CRAG Decision Flow
```
User Query
    |
    v
[Knowledge Base Retrieval]
    |
    v
[Relevance Evaluator]  ← fast cross-encoder or LLM classifier
  Score each retrieved document [0.0 - 1.0]
    |
    +-- All CORRECT (scores > 0.7) ---------> [Generation] → Answer
    |
    +-- MIXED (some correct, some ambiguous)
    |        |
    |        v
    |   [Context Refinement]
    |   Extract relevant sentences from ambiguous docs
    |        |
    |        v
    |   [Generation with refined context] → Answer
    |
    +-- All INCORRECT (scores < 0.3)
             |
             v
        [Web Search Fallback]
             |
        [Parse + Filter web results]
             |
        [Combine with any relevant KB content]
             |
        [Generation] → Answer with web citations
```

### CRAG vs. Standard RAG
```
Standard RAG:
  Query → Retrieve → [Always use context, even if bad] → Generate

CRAG:
  Query → Retrieve → [Evaluate quality] → {
    Good:   Generate directly
    Mixed:  Refine then Generate
    Bad:    Web Search → Generate with web results
  }
```

---

## 5. Real-World Examples

### CRAG with LangGraph (Community Implementation)
- Widely implemented as a LangGraph workflow: retrieval → grading node → conditional web search → generation
- Grading uses LLM (GPT-4o-mini) to classify documents as relevant or not
- Used in enterprise pipelines where knowledge base freshness is a concern

### Enterprise Knowledge Base with Time-Sensitive Queries
- Internal policy Q&A system: policies updated quarterly; users asking about recent changes
- CRAG detects when KB has stale/irrelevant policy documents
- Falls back to SharePoint or web search for recent policy updates
- Prevents confidently wrong answers about outdated policies

### Research Assistant Systems
- Academic research assistant: knowledge base of curated papers (updated monthly)
- User queries about recent papers (last 30 days) not yet in the index
- CRAG detects low relevance → triggers web search of arXiv/Semantic Scholar
- Returns answer citing recent papers not in the local index

---

## 6. Tradeoffs

| Dimension | Standard RAG | CRAG |
|-----------|-------------|------|
| Answer quality (good KB coverage) | High | High (same) |
| Answer quality (bad KB coverage) | Low (hallucinates) | High (web fallback) |
| Latency (good retrieval) | Fast | Fast + ~50ms evaluator |
| Latency (web fallback triggered) | N/A | +1-3 seconds (web fetch) |
| Web search cost | None | Variable (when triggered) |
| Implementation complexity | Low | Medium |
| External dependency | None | Web search API |
| Knowledge currency | Limited to index | Can be real-time (via web) |

---

## 7. When to Use / When NOT to Use

### Use CRAG When:
- Knowledge base has known gaps or coverage limitations
- Queries may span beyond indexed content (time-sensitive, niche topics)
- False confidence from irrelevant context is a high-risk failure mode
- Web search is available and acceptable as a data source

### Use Standard RAG When:
- Knowledge base is comprehensive and well-maintained
- Web search is not appropriate (privacy, air-gapped environments)
- Latency budget cannot accommodate web search fallback
- Query distribution is well within knowledge base coverage

### Do Not Use CRAG When:
- Web search is blocked by policy (regulated industries with strict data sources)
- All answers must come from curated, auditable sources
- The relevance evaluator itself is unreliable (miscalibrated thresholds degrade performance)

---

## 8. Common Pitfalls

**1. Miscalibrated relevance thresholds**
If the threshold is too high, most documents are labeled "incorrect," triggering web search unnecessarily. If too low, genuinely irrelevant documents pass as "correct," defeating the purpose.
Fix: Calibrate thresholds on a labeled (query, document, relevant: T/F) evaluation set. Measure precision and recall of the evaluator at different threshold values; choose the threshold that minimizes false negatives (missing good documents) while limiting false positives.

**2. Web search returns untrustworthy content**
A web search fallback may retrieve biased, inaccurate, or outdated web pages, which is worse than using marginally relevant KB content.
Fix: Filter web results by domain reputation; add source metadata to the LLM context ("Source: Wikipedia, Wikipedia.org, government websites preferred"); use a knowledge-graph-verified web search tool (Perplexity, Tavily with citation reliability scores).

**3. Infinite refinement loop**
Ambiguous documents sent through sentence-level refinement may produce near-empty context if no individual sentences score above the threshold.
Fix: Set a minimum refined context size; if the refined ambiguous document has fewer than 100 tokens, discard it rather than passing empty/trivial context.

**4. Evaluator adds significant latency for all queries**
Running a cross-encoder evaluator on 5-10 retrieved documents adds 50-200ms even when all documents are highly relevant.
Fix: Use a fast lightweight evaluator (bi-encoder similarity score, or small cross-encoder like cross-encoder/ms-marco-MiniLM-L-6-v2). Reserve expensive evaluators for the ambiguous range; use simple threshold on embedding similarity for clear-cut cases.

**5. Web search queries copied from user query**
A user query like "What does our internal policy say about remote work?" is a terrible web search query.
Fix: Use an LLM to reformulate the query for web search, removing internal/private references and adding context that would help a web search engine: "Remote work policy corporate best practices 2024" or "remote work policy employee guidelines."

**6. No source tracking through the pipeline**
After combining KB context with web content, the generation LLM produces an answer but there's no way to trace which source each statement came from.
Fix: Tag each context chunk with its source (KB document ID, URL for web results). Inject source metadata into the LLM prompt and instruct it to cite sources. Return citations alongside the answer.

---

## 9. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **LangGraph** | CRAG workflow implementation | Graph-based conditional routing between retrieval, evaluation, web search |
| **cross-encoder/ms-marco-MiniLM-L-6-v2** | Fast relevance evaluator | 6-layer MiniLM; fast, good for binary relevance; self-hosted |
| **Cohere Rerank API** | Managed relevance evaluator | Best managed option; returns relevance scores per document |
| **Tavily Search API** | Web search for RAG | RAG-optimized web search; returns clean text; respects content |
| **Serper.dev** | Google Search API wrapper | Low-cost Google search; needs HTML parsing layer |
| **Bing Web Search API** | Web search | Reliable; better enterprise terms than scraping |
| **RAGAS** | Evaluate CRAG quality | Context relevance, faithfulness for CRAG-specific evaluation |

---

## 10. Interview Questions with Answers

**Q: What problem does Corrective RAG solve that standard RAG doesn't handle?**
A: Corrective RAG solves the retrieval quality gap: standard RAG feeds all retrieved documents to the LLM regardless of relevance, treating retrieval as infallible. When queries fall outside the knowledge base coverage (recent events, niche topics, stale documents), the retriever returns marginally related documents. The LLM then generates a confident answer grounded in wrong context — a hallucination that's hard to detect because it appears well-cited. CRAG intercepts this by evaluating retrieved document relevance before generation. If quality is too low, CRAG triggers a web search fallback, ensuring the LLM always has relevant context before generating.

**Q: How does CRAG decide between using knowledge base results vs. triggering web search?**
A: CRAG uses a relevance evaluator (cross-encoder or LLM classifier) to score each retrieved document against the query. Based on scores: all documents above the threshold (e.g., 0.7) → proceed with KB results; mixed quality (some above, some below) → refine the ambiguous documents (extract only relevant sentences) and combine with high-quality ones; all documents below threshold → trigger web search. In practice, the thresholds must be calibrated on a labeled evaluation set — miscalibrated thresholds either over-trigger web search (adding latency) or under-trigger (failing to correct bad retrieval).

**Q: How do you calibrate the relevance evaluation thresholds in CRAG?**
A: Build a labeled evaluation set: 200-300 (query, document, label) triples where label ∈ {relevant, irrelevant} as judged by domain experts. Run the relevance evaluator on all triples and plot the precision-recall curve at different threshold values. Choose the threshold that maximizes F1 on this set, with a bias toward recall (prefer false positives — triggering web search unnecessarily — over false negatives — using irrelevant context). Validate: after deploying, monitor the web search trigger rate; if it's above 30%, the threshold may be too strict; if below 5% on a knowledge-base-limited corpus, the threshold may be too lenient.

**Q: What are the risks of using web search as a fallback in enterprise RAG?**
A: Three main risks. First, source reliability: web search returns content from unvetted sources; a web article may be biased, outdated, or simply wrong. Mitigate by filtering to trusted domains (gov, edu, Wikipedia, established news) and including source metadata in the LLM context. Second, data privacy: if the query contains sensitive business information, using it as a web search query leaks information. Mitigate by scrubbing queries before web search (remove identifiers, company names) or using a private search API. Third, compliance: regulated industries (healthcare, finance) may prohibit citing non-approved external sources. In these cases, CRAG's web fallback may be contractually prohibited; substitute with a secondary curated knowledge base or report inability to answer.

**Q: How does CRAG differ from Self-RAG in handling low-quality retrieval?**
A: CRAG and Self-RAG both address retrieval quality but at different levels and through different mechanisms. CRAG is an external pipeline: a separate relevance evaluator module assesses document quality before generation; the LLM itself is unmodified and can be any model. CRAG corrects retrieval quality before generation starts. Self-RAG is an intrinsic model capability: the model itself (fine-tuned with reflection tokens) evaluates passage relevance ([Relevant]/[Irrelevant]) and statement-level support ([Supported]/[No Support]) during generation. Self-RAG is more fine-grained (sentence-level) but requires fine-tuning; CRAG is coarser (document-level) but works with any LLM. They're complementary: CRAG handles pre-generation document quality; Self-RAG handles in-generation faithfulness.

**Q: How should you design the web search query reformulation in CRAG?**
A: The raw user query is often a poor web search query for three reasons: (1) it may contain private/internal context ("our Q3 product launch"); (2) it may be phrased as a conversational question rather than search terms; (3) it may lack the specificity needed for good web results. Reformulation process: use an LLM prompt to strip internal references, convert to search-engine-optimized keywords, add relevant domain/topic context, and optionally add recency filters ("2024"). Validate by running test queries and inspecting whether web results are on-topic. For time-sensitive queries, adding year or month helps enormously.

**Q: How do you evaluate CRAG-specific quality metrics?**
A: Evaluate at each pipeline stage. (1) Evaluator quality: precision and recall of the relevance evaluator on labeled (query, document, relevant) test triples — this is the core CRAG mechanism. (2) Trigger rate accuracy: for queries genuinely outside the KB, does CRAG trigger web search? For queries within KB coverage, does CRAG correctly use KB without web search? Measure on a labeled (query, should_use_web: T/F) test set. (3) End-to-end quality: compare standard RAG vs. CRAG on queries with known KB gaps using faithfulness and answer accuracy metrics. Track the reduction in hallucination rate on out-of-KB queries as the primary production metric.

**Q: What is the context refinement step in CRAG and when does it apply?**
A: Context refinement applies to "Ambiguous" documents — those with relevance scores between the correct and incorrect thresholds. Rather than discarding these documents (losing potentially useful information) or using them whole (adding noise), CRAG extracts the most relevant sentences from each. Implementation: apply the relevance evaluator at sentence granularity; keep only sentences above a minimum relevance threshold; discard the rest. The refined document contains only the relevant portions. This is especially effective for long documents where the relevant part is a single paragraph: the full document would dilute the LLM's attention, but the refined version focuses on the right content.

**Q: How do you handle the latency impact of CRAG in production?**
A: CRAG adds two potential latency sources: (1) relevance evaluation — 50-100ms for a fast cross-encoder on 5 documents; (2) web search fallback — 500ms-3s for web fetch and parsing. For the evaluator latency: use a fast lightweight evaluator (MiniLM-based cross-encoder runs in under 50ms on GPU); parallelize evaluation across all K retrieved documents. For web search latency: accept the tradeoff for queries that need it (users expect slower responses when searching the web); set a strict timeout (3s) and fall back to best available KB context if web search times out. Monitor web search trigger rate — if above 20-30%, consider expanding the knowledge base rather than over-relying on web search.

**Q: How would you combine CRAG with FLARE or agentic RAG?**
A: These can stack at different pipeline stages. CRAG operates pre-generation: evaluates and corrects retrieval quality before the LLM starts generating. FLARE operates mid-generation: triggers additional retrieval when the LLM is uncertain at a specific generation point. Combining them: use CRAG for the initial retrieval quality gate (correct the retrieval before generation begins), then use FLARE's mid-generation retrieval trigger for additional lookups needed during generation. With agentic RAG: CRAG can serve as the relevance evaluator within each iteration of the agentic loop — each retrieved document batch is scored, and low-quality batches trigger web search within the iteration.

**Q: What's the most common production failure mode in CRAG systems?**
A: The most common failure is the relevance evaluator being too conservative, triggering web search for queries that are actually within the knowledge base. This happens when the evaluator's threshold is set too high during development on a small, clean test set that doesn't represent the messier production query distribution. Symptoms: web search API costs spike; latency increases across the board; users get web-sourced answers for queries that the KB could answer accurately. Diagnosis: log (query, KB score, used_web_search) for all requests and manually review a sample of web-search-triggered queries — if >50% could have been answered from the KB, the threshold is too strict.

---

## 11. Best Practices

1. **Calibrate thresholds on representative data** — use a sample of actual production queries (not just the clean test set) to tune correct/incorrect thresholds.
2. **Use a fast evaluator** — MiniLM-based cross-encoder or bi-encoder similarity; cross-encoder/ms-marco-MiniLM-L-6-v2 balances speed and accuracy well.
3. **Reformulate before web search** — never use the raw user query as a web search query; strip private context and optimize for search engine effectiveness.
4. **Tag all context with source** — whether KB or web, every context chunk should carry source metadata for citation and tracing.
5. **Set web search timeouts** — hard timeout of 3 seconds; fall back to best available KB context to protect user experience.
6. **Monitor trigger rates** — track % of queries hitting each CRAG path (all-correct, mixed, all-incorrect); anomalies indicate evaluator or KB coverage issues.
7. **Expand the KB before increasing web search** — if web search trigger rate is consistently above 20%, it means the KB has coverage gaps that should be filled rather than perpetually patched with web search.
