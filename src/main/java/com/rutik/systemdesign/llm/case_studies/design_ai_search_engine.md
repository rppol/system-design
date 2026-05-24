# Case Study: Design an AI Search Engine (Perplexity-Style)

## Intuition

> **Design intuition**: An AI search engine (Perplexity-style) is RAG over live web search — the novelty is real-time retrieval (web crawl → search API → retrieved pages) rather than a static document corpus. The challenge is latency: searching the web + reading pages + generating an answer must complete in 2-4 seconds.

**Key insight for this design**: Pipeline parallelism is essential — search multiple engines in parallel, fetch and parse top results concurrently, and begin LLM generation as soon as enough context is assembled (streaming synthesis). Source attribution and credibility ranking differentiate a trustworthy AI search from a hallucinating chatbot.

---

## 1. Requirements Clarification

### Functional Requirements
- Users ask natural language questions and receive synthesized answers
- Answers cite sources from real-time web search results
- Live web search: index freshness matters (not stale data)
- Follow-up questions maintain conversation context
- Source credibility ranking (prefer authoritative sources)
- Multiple answer modes: quick answer, detailed answer, academic mode
- Image/media results alongside text answers

### Non-Functional Requirements
- **Latency**: First meaningful content < 1 second; full answer < 5 seconds
- **Freshness**: Sources should be < 24 hours old for news queries
- **Scale**: 10M daily active users; 50M queries per day
- **Accuracy**: Factual accuracy > 90%; source attribution accuracy 100%
- **Availability**: 99.9% uptime

### Out of Scope
- Building our own web crawler (use Bing Search API or Google Custom Search)
- Social media real-time indexing
- Video transcription and indexing

---

## 2. Scale Estimation

### Query Scale
```
Daily queries: 50M
Average query length: 15 words = ~20 tokens
Peak QPS: 50M / 86,400 × 3 = ~1,740 req/sec

Per-query breakdown:
  Web search API calls: 1 request (10 results)
  HTML fetching & parsing: 5-10 URLs
  Chunk extraction: ~50 chunks from fetched pages
  Embedding + reranking: ~50 vectors
  LLM synthesis: ~2,000 input tokens + 400 output tokens
  Total latency: 2-4 seconds (dominated by web fetching)
```

### Storage Estimates
```
Cached web content (24-hour cache):
  50M queries × 5 fetched pages × 50KB average = 12.5TB/day
  With 70% cache hit rate → only 30% fetch new content
  Effective storage: 3.75TB new content daily
  Retention: 24 hours → rolling 3.75TB cache

Search result cache:
  Query → results mapping
  50M queries × 20% cacheable (repeat queries within 1 hour) = 10M cached
  Per entry: ~5KB = 50GB cache (fits in Redis cluster)
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
[Query Analysis Service]
  - Language detection
  - Query classification (factual/opinion/news/academic)
  - Query expansion + rephrasing
  - Safe search filtering
    |
    ├──────────────────────────────────────────────────┐
    │                                                  │
    v                                                  v
[Web Search Orchestrator]                    [Conversation Context]
  - Call Bing/Google Search API              - Retrieve prior turns
  - Get top-10 URLs + snippets               - Merge with current query
  - Filter by domain reputation
    |
    v
[Content Fetcher (parallel)]
  - Fetch top 5-7 URLs simultaneously
  - Timeout: 2 seconds per URL
  - HTML parsing: extract main content
  - Cache: store fetched content for 24 hours
    |
    v
[Relevance Processor]
  - Extract relevant passages per URL
  - Score passages by relevance to query
  - Rerank combined passage set
    |
    v
[Context Assembler]
  - Select top-N passages (fit in LLM context)
  - Format with source citations
  - Add conversation history if follow-up
    |
    v
[LLM Synthesis Engine]
  - GPT-4o / Claude 3.5 streaming
  - Generate grounded answer with inline citations
  - Stream tokens as generated
    |
    v
[Post-processing]
  - Extract citation markers
  - Enrich with source metadata (title, URL, date)
  - Safety filter on output
    |
    v
[Response + Sources Panel]
  Text answer (streamed)
  Source cards (title, URL, snippet, relevance score)
```

---

## 4. Component Deep Dives

### 4.1 Query Analysis and Classification

```
Query type classification drives pipeline behavior:

Factual query: "Who invented the telephone?"
  → Action: find authoritative sources (Wikipedia, encyclopedias)
  → Cache TTL: 7 days (fact doesn't change)
  → Answer style: brief, direct

News query: "latest news on AI regulation"
  → Action: prioritize recent sources (< 24 hours), news outlets
  → Cache TTL: 30 minutes (news is time-sensitive)
  → Answer style: bullet points with timestamps

Opinion/analysis: "is Python or JavaScript better for backend?"
  → Action: balance multiple perspectives
  → Cache TTL: 1 day
  → Answer style: present pros/cons, avoid strong takes

Academic: "mechanism of action of GLP-1 receptor agonists"
  → Action: prefer scholarly sources (PubMed, arxiv)
  → Cache TTL: 7 days
  → Answer style: detailed, technical

Conversational: "hi, how are you?"
  → Action: LLM-only response, no web search needed
  → No search API call (saves $0.005/query)

Classifier: fine-tuned BERT-small on labeled queries
  Latency: 5ms (tiny model, runs locally)
  Accuracy: 93% on internal benchmark
```

### 4.2 Web Search and Content Fetching

```
Tier 1: Search API results (instant, but only snippets)
  - Bing Search API / Brave Search API
  - Returns: URL, title, snippet (100-200 chars)
  - Latency: 100-300ms
  - Cost: $5 per 1,000 queries (Bing) = $0.005/query

Tier 2: Full content fetching (slower, richer)
  - Fetch top 5 URLs from search results
  - Parallel fetch with 2-second timeout
  - HTML parsing: trafilatura (best content extraction library)

Content extraction pipeline:
  Raw HTML → trafilatura → main content text
    (removes: navigation, ads, footers, sidebars)
  Result: 500-5,000 words of main article text

Fallback hierarchy:
  1. Cache hit (24-hour content cache) → use cached
  2. Fetch URL → parse with trafilatura
  3. Fetch timeout → use search snippet only
  4. Parse failure → skip URL, try next in list

Robots.txt compliance:
  - Check robots.txt before crawling any domain
  - Respect Crawl-Delay headers
  - User-Agent: "PerplexityBot (+https://perplexity.ai/bot)"

JavaScript-rendered content:
  - Most sites: static HTML sufficient
  - Some SPAs: require headless browser (Playwright)
  - Approach: try static fetch first → if no content → Playwright fallback
  - Playwright cost: 10× slower, 5× more resource-intensive → only for top results
```

### 4.3 Relevance Ranking

```
Two-stage ranking:

Stage 1: BM25 passage ranking (fast, keyword-based)
  Input: query + all extracted passages (50-200 passages from 5-7 URLs)
  Process: BM25 scores passage relevance to query
  Output: top-30 passages for reranking

Stage 2: Neural reranker (accurate, semantic)
  Input: query + top-30 passages
  Model: Cohere Rerank 3 or bge-reranker-large
  Process: cross-encoder scores each (query, passage) pair
  Output: top-8 passages for LLM context

Source credibility adjustment:
  Domain whitelist with credibility multipliers:
    academic.edu, nature.com, arxiv.org: × 1.5
    reuters.com, apnews.com, bbc.com: × 1.3
    wikipedia.org: × 1.2 (good for facts, not breaking news)
    medium.com, blogspot.com: × 0.8
    unknown domains: × 0.7

  Final score = reranker_score × credibility_multiplier

Freshness boost for news queries:
  Passage from today: × 1.4
  Passage from last week: × 1.2
  Passage > 1 month old: × 0.8 (news context)
```

### 4.4 LLM Synthesis and Citation Generation

```
Prompt structure for grounded synthesis:

[System]
You are an expert research assistant. Generate a comprehensive answer
to the user's question using ONLY the provided sources. For every claim,
cite the source using [1], [2], etc. Do not make claims not supported
by the provided sources. If sources conflict, present both perspectives.

[Sources]
[1] Title: "AI Act Passes EU Parliament" | Source: reuters.com | Date: 2024-03-14
Content: The European Union's Artificial Intelligence Act passed with 523 votes
in favor... [key relevant excerpt]

[2] Title: "EU AI Act Technical Requirements" | Source: ec.europa.eu | Date: 2024-03-15
Content: High-risk AI systems must implement risk management systems... [excerpt]

[3] ... (up to 8 sources)

[Query]
What are the main requirements of the EU AI Act?

[Expected output]
The EU AI Act, passed by the European Parliament in March 2024 [1], introduces
a risk-based framework for AI regulation. High-risk AI systems must implement
risk management systems and maintain technical documentation [2]...

Post-processing: extract citation markers [1][2][3]
Map citations → source metadata → render in Sources panel
```

### 4.5 Streaming Response Architecture

```
Timeline for a 3-second response:

T=0ms: User submits query
T=50ms: Query analyzed, classified as "factual"
T=150ms: Bing Search API returns 10 URLs + snippets
T=200ms: Content fetching starts (5 URLs in parallel)
T=200ms: STREAMING STARTS: Show search result cards to user
          "Searching the web..." → show URLs as they're fetched
T=600ms: 3-4 URLs fetched and parsed (fastest ones)
T=800ms: Reranking complete on available passages
T=850ms: LLM synthesis starts with available context
T=900ms: FIRST TOKEN streamed to user
T=1200ms: Remaining URLs arrive (slower servers)
T=2,500ms: LLM finishes generating answer
T=2,600ms: Source attribution processed
T=2,700ms: Full response displayed with citations

Key insight: Don't wait for all URLs before starting LLM generation.
             Start with available content after 600ms; add late-arriving
             content only if LLM hasn't started yet.

Progressive disclosure:
  1. Search UI: show "Searching..." + URL cards immediately
  2. Streaming answer: render tokens as they arrive
  3. Sources panel: populate as pages are fetched
  4. Related questions: compute async, show after main answer
```

### 4.6 Caching Strategy

```
Three cache layers:

L1: Query → Full response cache (Redis)
  TTL: varies by query type (factual: 6 hours, news: 30 min)
  Key: hash(normalized_query + user_language)
  Size: ~5KB per cached response
  Hit rate: ~20% (many unique queries)

L2: URL → Extracted content cache (Redis + S3)
  TTL: 24 hours for all content
  Key: hash(URL)
  Hot tier (Redis): 10M most recent URLs (~50GB)
  Cold tier (S3): older fetched content (retrieve on cache miss if < 24h old)
  Hit rate: ~50% (popular URLs fetched by many users)

L3: Search query → URLs cache (Redis)
  TTL: 1 hour for news, 6 hours for factual
  Key: hash(search_query)
  Cache the Bing API response (list of URLs + snippets)
  Saves $0.005 per cached query
  Hit rate: ~30% (reduces Bing API calls by 30%)

Cost impact:
  Without caching: 50M queries × $0.005 (Bing) = $250K/day
  With L3 cache: 50M × 70% miss rate × $0.005 = $175K/day
  L1 cache: 50M × 80% miss rate for LLM costs
    = $0.03/query × 50M × 80% = $1.2M/day (still a lot!)
  Best savings: L1 cache for repeat popular queries
```

---

## 5. Source Quality and Safety

### Misinformation Filtering
```
Domain blacklist:
  - Known misinformation sites (manually curated list)
  - Sites flagged by NewsGuard, Google SafeBrowsing
  - New domains < 30 days old: penalized

Content-level checks:
  - Detect contradictions between sources (flag for user)
  - Satire detection classifier (The Onion-type sites)
  - Paywalled content: skip if can't access full text

For health/medical queries:
  - Mandatory disclaimer: "Consult a healthcare professional"
  - Prioritize: Mayo Clinic, WebMD, NIH, CDC, PubMed
  - Deprioritize: anonymous blogs, anecdotal sources
```

---

## 6. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Web search | Bing API | Build own crawler | Time to market; freshness; breadth |
| Content fetch | On-demand per query | Pre-crawl all URLs | Only fetch what's needed; scale with queries |
| Retrieval | Neural reranker | BM25 only | +30% answer quality; +100ms acceptable |
| LLM | GPT-4o streaming | Specialized model | Quality; speed; reliability |
| Citations | Inline [1][2] | Footnotes | Inline builds trust during reading |
| Caching | Multi-tier (L1-L3) | No cache | 60% cost reduction; freshness preserved by TTL |
| Context window | Top-8 sources × ~250 tokens | More/fewer | Empirical: 8 sources balance quality vs latency |

---

## 7. Comparison with Traditional Search

```
Traditional Search (Google/Bing):
  - Returns: list of 10 links + snippets
  - User effort: click links, synthesize answer manually
  - Latency: 200ms (just ranking, no content)
  - Freshness: real-time index
  - Reliability: no single point of failure (links still work even if ranking wrong)

AI Search (Perplexity-style):
  - Returns: synthesized answer + source citations
  - User effort: zero (answer is ready)
  - Latency: 2-5 seconds (fetch + LLM synthesis)
  - Freshness: semi-real-time (fetched on demand, cached 24 hours)
  - Failure mode: model can hallucinate even with sources

When AI search wins:
  - Research questions requiring synthesis across sources
  - Complex factual questions with authoritative answers
  - Questions needing nuanced explanation

When traditional search wins:
  - Shopping / finding a specific website
  - Very recent breaking news (< 1 hour)
  - When user wants to evaluate sources themselves
  - Long-tail queries with few good sources
```

---

## 8. Related Questions and Session Management

```
After answer generation, compute "related questions" async:

LLM prompt: "Given the question '{query}' and this answer '{answer}',
generate 4 relevant follow-up questions that the user might want to explore."

Display: clickable follow-up questions below the answer
  "What are the penalties for violating the EU AI Act?"
  "How does the EU AI Act compare to US AI regulation?"
  "Which companies are most affected by the EU AI Act?"
  "When does the EU AI Act go into effect?"

Session continuity:
  - Maintain conversation history (last 5 turns) in session cookie
  - Follow-up questions: include prior context in new query
  - Example: "what about in the US?" → resolved using prior context to
    "What are the US AI regulations compared to the EU AI Act?"
  - Session timeout: 1 hour of inactivity
```

---

## 9. Cost Analysis

```
50M queries/day, optimized pipeline:

Search API (Bing): 50M × 70% miss rate × $0.005 = $175,000/day
Content fetching: server costs for parsing 50M × 3 URL fetches = ~$10,000/day
LLM synthesis (GPT-4o):
  50M × 80% non-cached × 2,000 input tokens × $5/1M = $400,000/day
  50M × 80% non-cached × 400 output tokens × $15/1M = $240,000/day
  LLM total: $640,000/day
Reranking (Cohere Rerank): 50M × 30 passages × $0.001/1K = $1,500/day
Infrastructure (servers, Redis, CDN): ~$20,000/day

TOTAL: ~$846,500/day ≈ $25.4M/month

Revenue model (Perplexity pricing):
  Free tier: ad-supported, limited queries
  Pro: $20/month, unlimited queries, GPT-4 class model
  Enterprise: custom pricing

With 10% paying Pro users:
  1M users × $20/month = $20M/month
  Cost: $25.4M/month
  Still slightly loss-making at these numbers — need scale or cost reduction

Cost reduction levers:
  - Claude Haiku or Llama 3 for simple queries (10× cheaper LLM)
  - Better caching (get hit rate to 50%) → cut LLM costs in half
  - Self-host reranker model → eliminate Cohere cost
  Optimized: ~$10M/month at 50M queries/day → profitable at scale
```

---

## 10. Interview Discussion Points

**Why is Perplexity faster than you'd expect?** Progressive streaming and parallelism. The UI shows search cards immediately (T=200ms) while fetching happens in background. The LLM starts generating before all URLs are fetched. The user sees progress at every step — the perceived latency is much lower than the actual end-to-end latency.

**The freshness vs. quality trade-off.** Fresh web content (fetched on demand) is noisier than a curated knowledge base. The system must balance recency (users want latest information) with quality (unreliable sources degrade answer quality). Domain credibility scoring and source diversification help manage this.

**Citation as a trust mechanism.** The most important UX innovation of AI search over chatbots is citations. Without citations, users can't verify claims. With citations, users can spot-check answers and build trust. This also limits hallucination: the model is instructed to only use provided sources, and claims are attributable to sources.

**Scaling the search API cost.** At 50M queries/day × $0.005/query = $250K/day in search API costs alone. This forces either a deal with a search provider (Perplexity uses Bing in a partnership) or building a crawling infrastructure. At scale, most AI search companies are moving toward hybrid: commercial API for rare queries, proprietary crawler for high-volume query patterns.

---

## Query Understanding Pipeline

Before retrieval, AI search engines run a multi-stage query understanding pipeline to transform the raw user query into a retrieval-optimized form:

```
Raw query: "best noise cancelling headphones under 300 with good bass"
                          │
                          ▼
            ┌─────────────────────────┐
            │  Intent Classification  │
            │  (product search, Q&A,  │
            │  navigational, etc.)    │
            └────────────┬────────────┘
                         │ product_search (0.94)
                         ▼
            ┌─────────────────────────┐
            │  Entity Extraction      │
            │  headphones, $300 max,  │
            │  noise cancelling, bass │
            └────────────┬────────────┘
                         │
                         ▼
            ┌─────────────────────────┐
            │  Query Rewriting        │
            │  + query expansion      │
            │  (add: ANC, Bluetooth,  │
            │   audiophile, review)   │
            └────────────┬────────────┘
                         │ 3 rewritten query variants
                         ▼
            ┌─────────────────────────┐
            │  Retrieval              │
            │  BM25 + Dense (HNSW)   │
            │  per rewritten variant  │
            └────────────┬────────────┘
                         │ top-50 documents (union)
                         ▼
            ┌─────────────────────────┐
            │  Cross-Encoder Rerank   │
            │  (top-50 → top-10)      │
            └────────────┬────────────┘
                         │ top-10 documents
                         ▼
            ┌─────────────────────────┐
            │  LLM Generation +       │
            │  Citation Attribution   │
            └─────────────────────────┘
```

**Query expansion implementation:**

```python
import anthropic
import json

client = anthropic.Anthropic()

def expand_query(raw_query: str, intent: str) -> list[str]:
    """Generate 3 semantically diverse query variants for broader retrieval coverage."""
    response = client.messages.create(
        model="claude-3-haiku-20240307",  # fast, cheap for query expansion
        max_tokens=256,
        system="You are a search query optimizer. Generate diverse query variants.",
        messages=[{
            "role": "user",
            "content": f"""Generate 3 different search queries to find information about:
"{raw_query}" (intent: {intent})

Make variants diverse: one literal, one conceptual, one comparison-focused.
Return JSON: {{"queries": ["variant1", "variant2", "variant3"]}}"""
        }]
    )
    result = json.loads(response.content[0].text)
    return result["queries"]

# Example output for "best noise cancelling headphones under 300 with good bass":
# queries = [
#   "noise cancelling headphones under $300 best bass response 2024",
#   "active noise cancellation headphones audiophile bass quality budget",
#   "Sony WH1000XM5 vs Bose QC45 vs Jabra Evolve2 price comparison bass",
# ]
```

---

## Re-Ranking with Cross-Encoder

After BM25 + dense retrieval returns 50 candidate documents, a cross-encoder reranker scores each query-document pair jointly (not independently like bi-encoder retrieval models):

```python
from sentence_transformers import CrossEncoder
from dataclasses import dataclass

@dataclass
class RankedDocument:
    content: str
    url: str
    cross_encoder_score: float
    original_retrieval_rank: int

cross_encoder = CrossEncoder("cross-encoder/ms-marco-MiniLM-L-12-v2")  # 12ms inference

def rerank_documents(
    query: str,
    candidates: list[dict],
    top_k: int = 10,
) -> list[RankedDocument]:
    """Rerank top-50 retrieval candidates using cross-encoder."""
    pairs = [(query, doc["content"][:512]) for doc in candidates]  # truncate to 512 tokens
    scores = cross_encoder.predict(pairs, batch_size=50)  # batch all 50 at once

    ranked = sorted(
        [
            RankedDocument(
                content=doc["content"],
                url=doc["url"],
                cross_encoder_score=float(scores[i]),
                original_retrieval_rank=i,
            )
            for i, doc in enumerate(candidates)
        ],
        key=lambda x: x.cross_encoder_score,
        reverse=True,
    )
    return ranked[:top_k]
```

**Why cross-encoders outperform bi-encoders for reranking:** Bi-encoders embed query and document independently (fast, but missing interaction signals). Cross-encoders process the full query+document pair jointly through all attention layers, capturing query-document interaction (e.g., "headphones" in query attends to "over-ear" in document). Cross-encoders are 40x slower but 15-25% more accurate on NDCG@10. The two-stage architecture uses bi-encoder for recall (top-50 candidates, fast) and cross-encoder for precision (top-10 reranked, slow).

---

## Index Freshness vs Quality Trade-off

AI search engines face a fundamental tension: freshness requires frequent crawling and re-indexing, but high-quality retrieval requires accurate, clean embeddings from trusted sources.

```
Index update frequency options:
┌──────────────────┬──────────────────┬──────────────────┬──────────────────┐
│ Strategy         │ Freshness lag    │ Embedding quality│ Cost             │
├──────────────────┼──────────────────┼──────────────────┼──────────────────┤
│ Real-time crawl  │ Minutes          │ Low (raw HTML)   │ Very high        │
│ Daily batch      │ 24 hours         │ Medium           │ High             │
│ Weekly batch     │ 7 days           │ High (curated)   │ Low              │
│ Hybrid (live +   │ Minutes for news,│ High for stable  │ Medium           │
│  curated cache)  │ days for stable  │  content         │                  │
└──────────────────┴──────────────────┴──────────────────┴──────────────────┘
```

Perplexity's approach: real-time web fetch at query time (not pre-indexed) for the top-10 URLs from the Bing API. This trades embedding quality (raw HTML is noisy) for perfect freshness. The quality gap is compensated by the cross-encoder reranker, which handles noisy retrieved content better than embedding-based retrieval alone.

---

## Hallucination Mitigation in AI Search

The most common hallucination failure mode in AI search is the model generating a claim that combines information from two different sources (cross-source hallucination), producing a statement not supported by any single retrieved document.

```python
def generate_with_strict_grounding(
    query: str,
    documents: list[RankedDocument],
    client: anthropic.Anthropic,
) -> dict:
    """Generate answer that ONLY uses information from provided documents."""
    doc_context = "\n\n".join([
        f"[Source {i+1}] {doc.url}\n{doc.content[:1000]}"
        for i, doc in enumerate(documents[:10])
    ])

    response = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=1024,
        system="""You are a search assistant that answers questions based ONLY on provided sources.
RULES:
1. Every factual claim must be supported by a cited source [1], [2], etc.
2. If sources conflict, acknowledge the conflict: "Sources disagree: [1] says X, [3] says Y."
3. If the answer is not in the sources, say "The provided sources do not contain this information."
4. NEVER use knowledge from your training that is not confirmed by provided sources.
5. Do not combine information from two sources into a single claim without citing both.""",
        messages=[{
            "role": "user",
            "content": f"Sources:\n{doc_context}\n\nQuestion: {query}"
        }]
    )

    return {
        "answer": response.content[0].text,
        "sources_used": [doc.url for doc in documents[:10]],
    }
```

---

## Failure Scenarios and Recovery

**Failure 1 — Search API Rate Limiting During Traffic Spike Causing 503 Errors**

During a major news event (election results, sports championship), query volume spiked 8x in 10 minutes. The Bing Search API's rate limit (10,000 queries/second for the subscribed tier) was hit within 2 minutes. Queries that exceeded the limit received 429 errors; the system propagated these as 503 errors to users.

**Detection:** Search API error rate alert fired at 2% (threshold: 0.5%). Correlated with traffic spike in CDN logs.

**Recovery:** (1) Implement exponential backoff and retry for transient 429s. (2) Fall back to cached index (pre-crawled documents up to 7 days old) for queries that fail live search. (3) Add a second search API provider (Google Custom Search, Brave Search) as a circuit breaker fallback. Target: <500ms additional latency for fallback path.

**Failure 2 — Cross-Encoder Reranker OOM Killing Inference Pods at 500 Concurrent Requests**

The cross-encoder was deployed on CPU-only pods (cost optimization) with 4GB RAM each. At 500 concurrent reranking requests × 50 documents × 512 tokens, the memory requirement spiked to 6.2GB per pod, causing OOM kills and pod restarts. Service degraded for 8 minutes before the Kubernetes HPA added replacement pods.

**Recovery:** Reduced batch size from 50 to 20 documents per cross-encoder call (three sequential calls instead of one). This trades 30ms additional latency for 60% memory reduction. Added pod memory limits at 6GB with OOM alerts at 80% usage.

---

## Additional Interview Questions

**How does an AI search engine decide which sources to trust and include in the answer?** Source credibility scoring combines: domain authority (Moz/Ahrefs DA score), content freshness (publication date), source category (Wikipedia, government sites, peer-reviewed journals score higher than user forums), and citation graph density (how many other trusted documents link to this source). Perplexity's domain credibility database classifies millions of domains into tiers; the retrieval pipeline applies a credibility floor — documents from domains below a credibility threshold are excluded from the reranking candidates even if they score high on embedding similarity.

**What is the difference between AI search and semantic search, and when does AI search provide additional value?** Semantic search retrieves documents based on embedding similarity (understanding intent beyond keywords) but returns links — the user must read the documents themselves. AI search adds a generation layer: it reads the retrieved documents and synthesizes a direct answer, citing sources. AI search adds value when: (1) the answer requires synthesis across multiple documents; (2) the user wants a direct answer, not a list of links; (3) the query is complex (multi-part, comparative). AI search does NOT add value over semantic search when the user wants to read primary sources (researchers, journalists), needs to validate all facts independently, or is browsing exploratorially with no specific question.

**How do you handle queries that require real-time information (stock prices, weather, sports scores) in an AI search system?** Real-time structured data queries bypass the document retrieval pipeline and route to dedicated APIs: stock price queries → financial data API (Polygon.io, Alpha Vantage), weather → Open-Meteo or weather.gov, sports → ESPN or sports data API. These APIs return structured JSON; the LLM generates a natural language answer from the structured data without web retrieval. The routing decision is made by the intent classifier (adds 5ms); if the classifier detects a real-time data intent with >85% confidence, the structured API path is taken. Hallucination risk drops to near-zero for real-time queries because the LLM is grounded in structured facts, not noisy web documents.

**How do you measure retrieval quality in production when you don't have ground truth relevance labels for most queries?** Proxy metrics: (1) citation click-through rate (CTR) — if users click the cited source after reading the AI answer, the source was likely relevant; track CTR per source per intent category; (2) follow-up query rate — if users immediately ask a follow-up question, the initial answer likely failed to satisfy the information need (poor retrieval quality); (3) LLM-as-judge on a sampled 5% of queries — an LLM scores retrieved documents for relevance to the query; (4) implicit dwell time — users who spend >30 seconds reading the answer (long dwell) vs those who immediately bounce (short dwell, likely irrelevant result). Establish weekly baselines for all four metrics and alert on >10% regression.

**What is the cost breakdown for a 100M query/month AI search product and where are the optimization levers?** Cost per query (rough): search API ($0.003/query) + embedding model ($0.0001) + cross-encoder rerank ($0.0002) + LLM generation at 2,000 tokens average ($0.006) = ~$0.009/query. At 100M queries/month: $900,000/month. Optimization levers: (1) cache popular queries (top 20% of queries are typically repeated — semantic cache reduces LLM calls by 25-30%); (2) use cheaper models for simple queries (intent classifier routes factual/short queries to Claude Haiku instead of Sonnet — 50% cost reduction for routed queries); (3) reduce LLM output length (most answers can be 400 tokens, not 1,000 — constrain max_tokens); (4) pre-embed a high-quality curated document set and reduce live web fetching for evergreen queries.

---

## Production Failure Scenarios

### Incident: Stale Knowledge Served for Rapidly-Changing Topics

**What happened:** A breaking news event (major corporate acquisition) generated 800,000 queries within 2 hours. The web retrieval pipeline hit rate limits on the search API (Bing: 1,000 queries/second cap). The semantic cache returned answers based on pre-acquisition content for 40% of queries, because the cache TTL for "technology company" queries was set at 7 days. Users received confidently stated false information.

**Fix applied:**
```python
from datetime import timedelta
from enum import Enum

class TopicVolatility(Enum):
    STATIC = timedelta(days=30)      # historical facts, scientific constants
    SLOW = timedelta(days=7)         # company info, product specs
    DYNAMIC = timedelta(hours=6)     # news, market data
    REAL_TIME = timedelta(minutes=0) # sports scores, stock prices — never cache

def compute_cache_ttl(query: str, intent: QueryIntent) -> timedelta:
    if intent == QueryIntent.REAL_TIME_DATA:
        return TopicVolatility.REAL_TIME.value
    if any(kw in query.lower() for kw in ["today", "now", "latest", "breaking", "just announced"]):
        return TopicVolatility.DYNAMIC.value
    return TopicVolatility.SLOW.value
```

**Prevention:** Temporal signal detection must influence cache TTL, not just query routing. Time-sensitive queries ("just announced," "today," names of entities with recent news) should bypass the cache entirely. Alert when cache hit rate spikes above 60% for a trending topic — this pattern indicates stale cache serving viral queries.

---

### Capacity Planning Math (100M queries/month, Perplexity-scale)

```
Peak query rate (10× average for trending topics):
  Average: 100M / (30 × 86,400) = 38.6 req/s
  Peak: 386 req/s

Cost per query at peak:
  Web retrieval API (Bing/SerpAPI): $0.003/query × 386 = $1.16/s
  Embedding (query + 20 retrieved docs): $0.00025/query
  Cross-encoder reranking (top 20): self-hosted, $0.0002/query
  LLM generation (Claude Haiku, avg 800 output tokens):
    $0.0008/1k input × 3k tok + $0.004/1k output × 0.8k tok = $0.0056/query
  Total per query: ~$0.009/query
  Monthly cost: 100M × $0.009 = $900K/month

Search API rate limit mitigations:
  Semantic cache (25% hit rate): saves 25M queries × $0.009 = $225K/month
  Popular query pre-fetching (top 10K queries pre-run nightly): saves $30K/month
  Net cost: $645K/month

Infrastructure for self-hosted components:
  Embedding server: 2 × A10G (batch embedding), $1,200/month
  Cross-encoder reranker: 4 × A10G, $2,400/month
  Redis semantic cache: 2 × r6g.2xlarge (32GB each), $800/month
  API gateway + routing: 8 × c6g.xlarge, $600/month
  Total infrastructure: $5,000/month (< 1% of total cost — LLM API dominates)
```

---

### Additional Interview Q&As

**Q: How do you prevent an AI search engine from becoming an "answer machine" that displaces traffic from the original sources it cites?**
This is a publisher relations and business model question as much as a technical one. Mitigations: (1) always display the source URL prominently, not just the extracted quote — make clicking through to the source the obvious next step; (2) limit the portion of source content extracted to a few sentences, never the full article; (3) implement a publisher opt-out mechanism (similar to robots.txt) that prevents indexing their content in the AI answer layer; (4) revenue sharing programs with publishers based on citation frequency (Perplexity has announced such programs). The technical implementation: a `noai-excerpt` directive in robots.txt that the retrieval crawler honors.

**Q: How would you design the answer generation to handle conflicting information across multiple retrieved sources?**
When retrieved sources contradict each other (e.g., different publication dates cite different statistics), the LLM must not silently pick one — it must surface the conflict. System prompt instruction: "If retrieved sources contain conflicting information, explicitly state the conflict: 'Source A (2023) reports X while Source B (2024) reports Y. The discrepancy may reflect...'" The generation pipeline detects conflicts programmatically by embedding all extracted claims and computing pairwise cosine similarity — claims that are semantically similar (similarity > 0.8) but numerically different trigger a "conflict flag" that is injected into the prompt context, instructing the model to address the discrepancy explicitly.

---

### Web Crawling and Freshness Architecture

**Crawl priority tiering:**

```
Tier 1 — Real-time (crawl within minutes):
  News sites, government health/safety alerts, financial disclosures
  Trigger: RSS feed + sitemap change detection
  Freshness SLA: < 5 minutes from publication

Tier 2 — Daily (crawl every 24 hours):
  Wikipedia, reference sites, product pages, documentation
  Trigger: scheduled crawl + ETL change detection
  Freshness SLA: < 24 hours

Tier 3 — Weekly (crawl every 7 days):
  Blog posts, academic papers, evergreen content
  Trigger: scheduled only
  Freshness SLA: < 7 days

Tier 4 — On-demand (crawl when queried):
  URLs explicitly provided by users in queries
  Trigger: user query contains URL
  Freshness SLA: real-time (live fetch at query time)
```

**Freshness signal in ranking:**

```python
import math
from datetime import datetime, timezone

def freshness_score(
    publication_date: datetime,
    query_requires_freshness: bool,
    now: datetime | None = None,
) -> float:
    """
    Exponential decay freshness score: recent content scores higher.
    Half-life: 30 days for general queries, 1 day for news queries.
    """
    now = now or datetime.now(timezone.utc)
    age_days = (now - publication_date).total_seconds() / 86400.0
    half_life_days = 1.0 if query_requires_freshness else 30.0
    # Score: 1.0 for just-published, 0.5 at half-life, approaching 0 for old content
    score = math.exp(-0.693 * age_days / half_life_days)
    return score

def blend_relevance_and_freshness(
    semantic_score: float,
    freshness: float,
    freshness_weight: float = 0.3,    # higher for news queries
) -> float:
    return (1 - freshness_weight) * semantic_score + freshness_weight * freshness
```

**Q: How do you handle paywalled content that appears in web search results?**
Three-tier handling: (1) detect paywalled content by HTTP status patterns (200 with snippet but redirect on click, 402, or metered paywall signals in HTML); (2) if content is paywalled, do not attempt to extract full text — use only the publicly-visible snippet + metadata in retrieval; (3) in the answer, cite the source but include a note: "[Source: WSJ — subscription may be required for full article]." Never present paywalled content as if it were fully accessible. For quality: paywalled sources often have higher credibility scores — preserve them in retrieval ranking but adjust the answer generation to acknowledge limited content access.
