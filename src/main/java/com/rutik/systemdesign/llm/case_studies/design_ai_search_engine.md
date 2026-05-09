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
