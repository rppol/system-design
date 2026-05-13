# Query Transformation

## 1. Concept Overview

Query transformation is a pre-retrieval technique that rewrites, expands, or decomposes the user's raw query into a form better suited for retrieval. Raw user queries are often ambiguous, terse, colloquial, or phrased differently from the documents that answer them. Transforming the query before sending it to the retrieval system bridges the vocabulary and intent gap.

Techniques include: query rewriting (make the query explicit and retrieval-friendly), HyDE (generate a hypothetical answer and embed that), multi-query expansion (generate alternative phrasings and merge results), and step-back prompting (retrieve general context alongside specific answers).

---

## Intuition

> **One-line analogy**: Query transformation is like a skilled reference librarian who rephrases your vague question into precise search terms before looking anything up.

**Mental model**: The gap between how users phrase questions and how documents are written is the root cause of retrieval failures. A user asks "What did they decide about the budget?" — but the document says "The executive team approved a $4.2M R&D allocation for Q4." No embedding model bridges this gap without query transformation.

**Why it matters**: Improving retrieval recall by 15-30% is often achievable through query transformation alone, with no changes to the document index or embedding model. It is the highest-leverage, lowest-cost improvement available in a RAG pipeline.

**Key insight**: The LLM already knows what a good answer looks like — HyDE exploits this by generating a hypothetical answer and embedding it, moving from "query space" into "document space" for retrieval.

---

## 2. Core Principles

- **Query-document vocabulary gap**: Users ask terse questions; documents contain declarative prose. Transformation closes this gap.
- **Multiple perspectives improve recall**: Different phrasings of the same question retrieve different documents. Merging results covers the space better.
- **Context enrichment**: Adding implicit context the user assumes ("they" → "the executive team") makes queries self-contained and retrieval-accurate.
- **Specificity vs. generality tradeoff**: Step-back prompting deliberately introduces generality to retrieve background context that narrow queries miss.
- **Cost vs. recall tradeoff**: Each transformation adds one or more LLM calls (latency + cost). Choose techniques based on measured recall improvement on your query distribution.

---

## 3. How It Works — Detailed Mechanics

### 3.1 Query Rewriting

An LLM rewrites the user's raw query to be more explicit, self-contained, and aligned with how documents phrase information.

```
System: "Rewrite the following user query to be more explicit and retrieval-friendly.
         Remove pronouns, add context, use formal language."

User query: "What did they decide about the budget last quarter?"

Rewritten: "What budget decisions were made by the executive team in Q4 2024
            regarding the company's annual operating plan?"
```

Implementation:
```python
def rewrite_query(raw_query: str, llm) -> str:
    prompt = f"""Rewrite this search query to be more explicit and retrievable.
    Remove ambiguous pronouns. Add domain context. Keep it a single sentence.

    Original query: {raw_query}
    Rewritten query:"""
    return llm.generate(prompt).strip()
```

When to use: when users ask follow-up questions with pronouns, when the domain has jargon, when query phrasing differs sharply from document language.

### 3.2 HyDE (Hypothetical Document Embeddings)

Instead of embedding the query and comparing to document embeddings, generate a hypothetical document that would answer the query, then embed that:

```
Step 1: Query → LLM → Hypothetical Answer
  Query: "What is the capital gains tax rate for long-term investments in 2024?"
  Hypothetical: "Long-term capital gains in 2024 are taxed at 0%, 15%, or 20%
                 depending on your taxable income bracket. For individuals earning
                 under $44,625, the rate is 0%..."

Step 2: Embed the hypothetical answer (not the original query)
Step 3: ANN search: find documents similar to the hypothetical answer

Why: The hypothetical answer is in "document space" — it resembles how a real
     document would discuss this topic, bridging the query-document gap.
```

HyDE pseudocode:
```python
def hyde_retrieve(query: str, llm, embed, vector_db, top_k: int = 10):
    # Generate hypothetical answer
    hypothetical = llm.generate(
        f"Write a detailed paragraph that would answer: {query}"
    )
    # Embed hypothetical (document-like text)
    hyp_embedding = embed(hypothetical)
    # Retrieve documents similar to hypothetical answer
    return vector_db.search(hyp_embedding, top_k=top_k)
```

Quality vs. risk: If the LLM's hypothetical answer is factually wrong, retrieval will find documents that match the wrong hypothesis. Always test HyDE vs. direct embedding on your eval set.

### 3.3 Multi-Query Expansion

Generate multiple alternative phrasings of the query, retrieve for each, merge and deduplicate:

```
Original: "How does React handle state updates?"

LLM generates 4 alternatives:
  1. "React useState hook and state management mechanisms"
  2. "How component re-renders are triggered in React"
  3. "React state batching and asynchronous updates"
  4. "setState behavior in React functional components"

Retrieve top-20 for each → merge → deduplicate → re-rank top-10
Result: 40-60% more recall than single-query retrieval
```

```python
def multi_query_retrieve(query: str, llm, retriever, n_queries: int = 4):
    prompt = f"""Generate {n_queries} different phrasings of this query.
    Return as a numbered list. Cover different angles and terminology.
    Query: {query}"""

    alternatives = llm.generate(prompt)
    all_queries = [query] + parse_list(alternatives)

    all_results = []
    seen_ids = set()
    for q in all_queries:
        for doc in retriever.retrieve(q, top_k=20):
            if doc.id not in seen_ids:
                all_results.append(doc)
                seen_ids.add(doc.id)

    return all_results  # rerank before returning
```

Deduplication is critical: the same document retrieved by multiple query variants should appear only once in the final candidate set.

### 3.4 Step-Back Prompting

Generate a more general "step-back" question to retrieve background context that helps answer the specific question:

```
Specific: "What was Brazil's GDP growth rate in Q2 2023?"
Step-back: "What are the main economic indicators used to measure Brazil's growth?"

Specific: "Why did the Lehman Brothers collapse?"
Step-back: "How did the 2008 subprime mortgage crisis develop?"

Retrieve both specific + step-back queries
Provide both specific facts + background context to the LLM
```

Step-back is particularly effective for:
- Technical questions requiring conceptual background
- Historical questions requiring causal context
- "Why" questions requiring understanding of mechanism

### 3.5 Query Decomposition

Break a complex multi-hop question into sub-questions, answer each independently, then synthesize:

```
Complex: "Which companies in our portfolio had revenue growth >20% AND
          decreased headcount in 2024?"

Decomposed:
  Q1: "Which portfolio companies had >20% revenue growth in 2024?"
  Q2: "Which portfolio companies decreased headcount in 2024?"
  Q3: (after answering Q1, Q2) "Intersection of Q1 and Q2 results"
```

Decomposition is the foundation of agentic RAG (see agentic_rag.md).

---

## 4. Architecture Diagram

### Query Transformation Pipeline
```
User Query
    |
    v
[Query Analysis]
  "Is this query ambiguous? Too terse? Multi-hop?"
    |
    +-- Ambiguous/pronoun-heavy --> [Query Rewriting]
    |                                    |
    |                                    v
    +-- Knowledge gap likely    --> [HyDE Generation]
    |                                    |
    |                                    v
    +-- Broad topic coverage    --> [Multi-Query Expansion]
    |                                    |
    |                                    v
    +-- Complex/multi-hop       --> [Decomposition]
    |
    v
[Transformed Query/Queries]
    |
    v
[Retrieval System]
  Dense + Sparse search for each query
    |
    v
[Merge + Deduplicate]
  Combine all retrieved candidates
    |
    v
[Reranker]
  Score all candidates against original query
    |
    v
Top-K Final Candidates → LLM Generation
```

### HyDE vs. Direct Embedding Space
```
Direct:    [User Query]      →  embed  →  query vector
                                            |
                                            | distance (can be far)
                                            |
           [Document chunk]  →  embed  →  doc vector

HyDE:      [User Query]  →  LLM  →  [Hypothetical Answer]
                                            |
                                            v
                                     embed  →  hyp vector
                                            |
                                            | distance (closer — same space)
                                            |
           [Document chunk]  →  embed  →  doc vector
```

---

## 5. Real-World Examples

### LlamaIndex Query Transformers
- `HyDEQueryTransform`: generates hypothetical document, embeds it for retrieval
- `DecomposeQueryTransform`: uses LLM to split complex queries into sub-questions
- `StepDecomposeQueryTransform`: step-by-step decomposition for multi-hop queries
- Production use: enterprise document Q&A pipelines with mixed-quality queries

### Perplexity Query Understanding
- Implicit query rewriting: expands user queries with inferred context before web search
- Multi-query for broad topics: fires 3-5 parallel searches for multi-faceted queries
- Measurably improves citation coverage for ambiguous queries

### RAG-Fusion (Arxiv 2023)
- Generates 4-6 query variations with an LLM
- Retrieves for each, applies Reciprocal Rank Fusion to merge rankings
- Demonstrated consistent 15-20% improvement in NDCG@10 over single-query RAG

---

## 6. Tradeoffs

| Technique | Recall Improvement | Added Latency | Added Cost | Failure Mode |
|-----------|-------------------|---------------|------------|--------------|
| Query rewriting | 10-20% | +200ms | Low (1 LLM call) | Over-transformation loses user intent |
| HyDE | 15-30% (topic-dependent) | +300ms | Low (1 LLM call) | Hallucinated hypothesis misleads retrieval |
| Multi-query (4x) | 30-50% | +400ms parallel | 4× retrieval cost | Excessive noise; dedup critical |
| Step-back | 10-25% | +300ms | Low | Too general; retrieves irrelevant background |
| Decomposition | 40-60% (complex queries) | +500ms-2s | High (N LLM calls) | Wrong decomposition misses key sub-questions |

---

## 7. When to Use / When NOT to Use

### Use Query Transformation When:
- Retrieval recall on your eval set is below 80%
- User queries are short and ambiguous (mobile search, conversational interfaces)
- Document vocabulary differs from user query vocabulary (medical, legal, technical)
- Queries span multiple topics or require multi-hop reasoning

### Prefer Direct Retrieval When:
- Queries are already well-formed and precise (developer API queries)
- Latency budget is under 200ms (transformation adds 200-500ms)
- Query volume is very high (cost of N LLM calls per query scales poorly)
- Eval shows transformation doesn't improve recall on your specific data

### Never Use HyDE When:
- Domain where LLM hallucinations are likely (rare facts, recent events, private data)
- The hypothetical answer diverges significantly from document style
- You haven't measured HyDE vs. direct embedding on a held-out eval set

---

## 8. Common Pitfalls

**1. Transforming away user intent**
Query rewriting that over-generalizes loses the user's actual intent. "What's the AWS Lambda cold start time?" rewritten to "What are the performance characteristics of serverless computing?" retrieves irrelevant documents.
Fix: Preserve specific numbers, proper nouns, and technical terms during rewriting.

**2. HyDE on factual queries with recent events**
LLM generates a hypothetical that contains wrong facts about recent events. Retrieval finds documents that match the wrong hypothesis.
Fix: Test HyDE recall vs. direct retrieval. If HyDE underperforms on factual queries, use direct retrieval for that query type.

**3. Multi-query without deduplication**
The same document retrieved 3x by different query variants ends up 3x in the candidate set, inflating its apparent relevance.
Fix: Always deduplicate by document ID before reranking.

**4. No reranking after multi-query merge**
Merging 4×20 = 80 candidates without reranking provides 80 documents to the LLM — too much noise, context window exceeded.
Fix: Always rerank the merged set; pass only top-5 to 10 to the LLM.

**5. Generating too many alternative queries**
10+ query variants retrieves 200+ candidates. Dedup and rerank become expensive; latency climbs.
Fix: 3-5 variants is the sweet spot; beyond 5 shows diminishing returns.

**6. Not caching transformed queries**
Popular queries get transformed on every request. Each transformation costs 200-300ms and an LLM call.
Fix: Cache (original_query → transformed_queries) with a short TTL (1-24 hours).

---

## 9. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **LlamaIndex** | Query transformation abstractions | HyDEQueryTransform, DecomposeQueryTransform, built-in |
| **LangChain** | Multi-query retriever | MultiQueryRetriever; generates N queries, merges results |
| **DSPy** | Automated query optimization | Learn optimal query transformations from examples |
| **Cohere** | Reranking post-expansion | Essential to reduce 80+ candidates to top-5 |
| **OpenAI GPT-4o-mini** | Cheap transformation LLM | ~$0.00015/1K tokens; use small model for rewrites |
| **RAGAS** | Evaluate transformation impact | Measure context recall before/after transformation |

---

## 10. Interview Questions with Answers

**Q: What is HyDE and when would you use it?**
A: HyDE (Hypothetical Document Embeddings) generates a hypothetical answer to the query using an LLM, then embeds that hypothetical answer for retrieval instead of the query itself. It works because the hypothetical answer is in "document space" — it resembles how a real document discusses the topic, bridging the vocabulary gap between terse queries and verbose documents. Best suited for domains where query phrasing differs strongly from document phrasing (academic papers, legal text, technical documentation). Avoid when the LLM is likely to generate factually incorrect hypotheses (recent events, proprietary data, rare facts), as a wrong hypothesis will steer retrieval toward unrelated documents.

**Q: What are the main failure modes of HyDE?**
A: Three primary failure modes. First, hallucinated hypotheses: if the LLM generates a factually wrong hypothetical answer, retrieval finds documents matching the wrong facts — the system confidently retrieves the wrong thing. Second, distributional mismatch: if the hypothetical answer is in a different style or register than the target documents (e.g., LLM writes in a casual tone but documents are formal legal text), the embedding space alignment breaks. Third, length mismatch: hypothetical answers that are much longer or shorter than typical document chunks sit in different regions of embedding space. Mitigation: always A/B test HyDE vs. direct retrieval on a labeled eval set before deploying.

**Q: How does multi-query expansion improve recall, and what are its tradeoffs?**
A: Multi-query expansion generates N alternative phrasings of the query (typically 3-5), retrieves candidates for each, then merges and deduplicates before reranking. It improves recall because different phrasings of the same question match different document phrasings — a document discussing "useState hook behavior" may not match "React state management" but does match "React useState hook." Empirically, 3-5 variants improve recall@10 by 30-50% for broad queries. Tradeoffs: N× retrieval cost, added LLM latency for generation, and the deduplication + reranking step becomes critical — without it you overwhelm the LLM with redundant context.

**Q: What is the difference between step-back prompting and query decomposition?**
A: Step-back prompting generates a more general version of the query to retrieve background context alongside the specific answer. It widens the retrieval scope. Query decomposition breaks a complex multi-hop question into specific sub-questions, each answered independently before synthesis. Step-back is additive (retrieve specific + general); decomposition is sequential (answer each sub-question in order). Use step-back for "why" questions needing background context; use decomposition for multi-hop questions like "who runs the company that acquired X?"

**Q: When should query rewriting not be used?**
A: Three scenarios. First, when queries are already precise and technical — developer API queries or structured filter queries don't benefit from rewriting and can lose precision. Second, when latency is critical (under 200ms) — each rewrite LLM call adds 150-300ms. Third, when the query rewrite model hallucinates context — if the rewriter adds incorrect assumed context ("the executive team" when the user meant a different group), retrieval silently goes wrong. Always validate rewriting with your specific LLM on your query distribution before deploying.

**Q: How do you measure whether query transformation is actually helping?**
A: Build a golden evaluation set: 100-200 (query, expected_documents) pairs where expected_documents are the ground truth relevant chunks. Measure context recall@K (fraction of expected documents in top-K retrieved) with and without transformation. Run this comparison for each technique (HyDE, multi-query, step-back) separately. A transformation should show >5% recall improvement to justify its latency/cost. If it doesn't improve recall on your eval set, it won't help in production — domain matters enormously for which techniques work.

**Q: How should you handle deduplication in multi-query expansion?**
A: Dedup by chunk/document ID before reranking. Keeping duplicates causes two problems: (1) the reranker sees the same document multiple times and may over-score it relative to other candidates; (2) the final context passed to the LLM contains repeated information, wasting context window tokens. Implementation: use a dict keyed by document ID, taking the first occurrence (from the most relevant query) or the highest retrieval score across all queries. After dedup, rerank the merged set against the original query using a cross-encoder.

**Q: What is RAG-Fusion and how does it combine multi-query with RRF?**
A: RAG-Fusion generates 4-6 query variations, retrieves top-K documents for each, then combines rankings using Reciprocal Rank Fusion (RRF): `score(doc) = Σ 1/(k + rank_i)` where k=60 and rank_i is the rank of the document in each individual retrieval result. Documents that appear in top positions across multiple query variants get boosted scores. Compared to simple merge-and-dedup, RRF uses position information (a document ranked #1 in 3 queries is better than one ranked #15 in 3 queries). Demonstrated 15-20% improvement in NDCG@10 over single-query RAG.

**Q: How do you choose which query transformation technique to use for a given application?**
A: Start by characterizing your query distribution. If queries are typically terse and ambiguous (conversational), rewriting helps most. If queries are domain-specific with vocabulary mismatch (medical, legal), HyDE bridges the gap. If queries are broad and multi-faceted, multi-query improves coverage. If queries are multi-hop (require chaining facts), decomposition is essential. In practice: run all techniques on your eval set and measure context recall improvement independently. Use the highest-gain technique, or combine (e.g., rewrite + multi-query) if the recall improvement justifies the latency.

**Q: How do query transformations interact with metadata filtering?**
A: Query transformations improve semantic similarity-based retrieval but don't affect metadata filters. A rewritten or HyDE query still needs the correct metadata filters applied (date range, source, department) to scope results. One consideration: multi-query expansion can generate queries with different implicit metadata scopes (one variant might reference "2023 data," another "2024 data"). Apply the original query's metadata filters to all variants — don't let LLM-generated variants override the user's intended scope.

**Q: What are the cost implications of query transformation at production scale?**
A: At 10,000 queries/day, each transformation adds one LLM call. With GPT-4o-mini at $0.00015/1K tokens and 500-token transformation prompts, rewriting costs ~$0.075/day per technique — negligible. At 1M queries/day, rewriting costs ~$7.50/day — still cheap. Multi-query at 4 variants costs 4× more ($30/day at 1M queries). The dominant cost at scale is usually the retrieval and reranking, not the transformation LLM call. Use a small fast model (gpt-4o-mini, claude-haiku) for transformations; save the capable model for final generation.

---

## 11. Best Practices

1. **Measure first** — run retrieval with and without each transformation on a labeled eval set before deploying. A transformation that doesn't improve recall@10 won't help production.
2. **Use a cheap model for transformation** — GPT-4o-mini or claude-haiku-4-5 is sufficient for query rewriting and multi-query expansion; save stronger models for generation.
3. **Always rerank after multi-query expansion** — never pass 80+ candidates directly to the LLM; rerank to top-5 to 10.
4. **Deduplicate by document ID, not text hash** — texts may differ slightly (different retrieval scores) but represent the same chunk.
5. **Cache transformed queries** — many users ask the same questions; cache (raw_query → transformed_queries) with a 1-24 hour TTL.
6. **Preserve proper nouns and numbers in rewriting** — query rewriters can paraphrase away specificity ("AWS Lambda" → "serverless functions"); enforce that entities and numbers survive transformation.
7. **Evaluate HyDE independently per query type** — HyDE performs differently on factual vs. conceptual vs. procedural queries; do not apply uniformly without per-type evaluation.
