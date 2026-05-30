# Case Study: Design a Deep Research Agent

## Intuition

> **Design intuition**: A deep research agent is like a PhD research assistant who is given a question, disappears for 30 minutes, reads 150 papers and web pages, synthesizes contradictory sources, and returns with a fully cited 10-page report. The engineering challenge is entirely different from RAG-based chatbots: this agent runs for 5-30 minutes, traverses 50-200 URLs, resolves contradictions across sources, and produces report-grade output — not chat-grade. The user is paying for depth, not speed.

**Key insight**: Deep research is not RAG at scale. RAG retrieves and generates in one shot. Deep research is an iterative planning-and-retrieval agent: it decomposes the question into sub-questions, executes parallel web searches, evaluates source quality, detects gaps, generates new sub-questions to fill those gaps, and synthesizes findings into a coherent report with inline citations. Each step may take 30-90 seconds; the total pipeline is 5-30 minutes. The primary engineering bottleneck is not LLM inference but parallel URL fetching, content extraction, and citation verification at scale. An agent that fetches 200 URLs sequentially at 2 seconds each spends 400 seconds crawling before synthesis even begins — making parallelism the single most important architectural decision in the system.

---

## 1. Requirements Clarification

### Functional Requirements
- Accept a research question in natural language and produce a structured, fully cited report
- Decompose the research question into sub-questions with priority ordering before beginning retrieval
- Search real-time web (Bing, Brave, Exa), academic sources (Semantic Scholar, arXiv API), and optionally user-uploaded documents
- Fetch and extract content from 50-200 URLs per research task, handling both static and JavaScript-rendered pages
- Track source provenance for every factual claim; every statement in the final report must cite at least one source
- Produce a structured report: executive summary, section-by-section body, inline citations, source list with quality scores
- Allow the user to adjust scope via a breadth/depth dial before execution begins: breadth = wider sub-questions, depth = fewer topics explored more thoroughly
- Support follow-up questions on the same research session, reusing previously fetched sources from that session's cache
- Show a research plan to the user before execution and allow them to approve or edit it (Gemini Deep Research pattern)

### Non-Functional Requirements
- Complete a standard research task (10 sub-questions, 100 URLs) in under 20 minutes
- Process 100 URLs in parallel, not sequentially (target: 100 URLs in under 15 seconds using async I/O)
- Citation accuracy above 95%: every cited claim must be verifiably grounded in the cited source
- Handle paywalled and JavaScript-rendered pages gracefully: extract abstract and preview rather than failing
- Cost ceiling: $5 per task for consumer tier, $20 per task for enterprise tier (enforced with a hard token budget)
- Support 10,000 concurrent research sessions at peak; 50,000 research tasks per day
- Task durability: if a task fails mid-execution (network partition, LLM timeout), resume from the last checkpoint rather than restarting from scratch

### Out of Scope
- Real-time market data feeds or financial data APIs
- Code execution or sandboxed computation
- Image generation or multimodal output beyond inline charts from cited sources

---

## 2. Scale Estimation

### Traffic Estimates
```
Research tasks/day:              50,000
Peak concurrent sessions:        10,000
Average task duration:           12 minutes (median; 5th pct = 4 min, 95th pct = 28 min)

URLs fetched per task:
  Average:                       150 URLs
  Daily URL fetches:             50,000 x 150 = 7,500,000 fetches/day
  Peak URL fetch rate:           10,000 sessions x 150 / 720s (12min avg) = ~2,083 fetches/sec
  With async parallelism cap:    100 concurrent per session x 10,000 = 1,000,000 theoretical
                                 Practical (staggered): ~50,000 concurrent HTTP connections

Content extracted per task:
  Raw fetched text:              200 URLs x 5 KB avg extracted text = 1 MB raw
  After quality filtering:       Top 50 sources x 5 KB = 250 KB per task
  Daily extracted content:       50,000 x 250 KB = 12.5 TB/day (processed in-memory, not stored)
```

### LLM Token Estimates
```
Per task token budget:
  Planning (GPT-4o):          2,000 input + 500 output
  Summarization (GPT-4o-mini): 50 sources x 800 input + 200 output = 40K input + 10K output
  Gap detection (GPT-4o):     5,000 input + 1,000 output (2 iterations avg)
  Synthesis (GPT-4o):         15,000 input + 5,000 output
  Verification (GPT-4o-mini): 10,000 input + 500 output
  Total per task:             ~73K tokens input + ~17K tokens output = 90K tokens/task

Daily token demand:           50,000 x 90,000 = 4.5B tokens/day
Daily LLM spend (blended):
  Planning + synthesis at GPT-4o ($2.50/M input, $10/M output): ~$2,200/day
  Summarization + verification at GPT-4o-mini ($0.15/M, $0.60/M): ~$375/day
  Total daily LLM spend:      ~$2,575/day; per-task LLM cost: ~$0.052

URL fetch infrastructure:
  50 crawler pods x t3.xlarge @ $0.166/hr x 24hr = $199/day
  Jina Reader API fallback:   7.5M fetches x $0.01/1K = $75/day
  Total fetch cost:           ~$274/day
```

### Storage Estimates
```
Session state (task graph, sub-questions, source list):
  Per task:                      ~100 KB (JSON, stored in Redis for active tasks)
  10,000 active sessions x 100 KB = 1 GB Redis working set

Completed task storage (reports + source metadata):
  Per task:                      ~200 KB (report Markdown + citation list)
  50,000 tasks/day x 200 KB x 30-day retention = 300 GB/month in object storage

Research session cache (raw extracted content, reused for follow-up):
  Per session (24-hour TTL):     ~1 MB extracted source content
  10,000 concurrent sessions:    10 GB Redis/Valkey cache
```

---

## 3. High-Level Architecture

```
  User Browser / API Client
          |
          v
  +---------------------+
  |   Research API GW   |  (rate limiting, auth, task ID assignment)
  |   POST /research    |
  +---------------------+
          |
          v
  +---------------------+
  |  Task Orchestrator  |  (durable task graph; see agent_durability_patterns.md)
  |  - task state in    |
  |    Redis + Postgres |
  |  - checkpoint on    |
  |    every phase      |
  +---------------------+
          |
    +-----+-----+------------------+
    |           |                  |
    v           v                  v
+----------+ +----------+  +------------------+
| Query    | | Parallel |  | Source Evaluator |
| Planner  | | Web      |  | - domain quality |
| (LLM:    | | Crawler  |  | - recency score  |
|  GPT-4o) | | (asyncio |  | - relevance BM25 |
|          | |  semaphore|  | - NLI dedup      |
| outputs  | |  100)    |  +------------------+
| sub-Q    | |          |          |
| priority | | fetches  |          |
| list     | | 50-200   |  +------------------+
+----------+ | URLs in  |  | Citation-Grounded|
    |        | parallel |  | Synthesizer      |
    |        +----------+  | (section-by-     |
    |                |     |  section)        |
    +----------------+     +------------------+
                 |                 |
                 v                 v
         +------------------+
         |  Gap Detector    |
         |  completeness    |
         |  score per sub-Q |
         |  → follow-up Qs  |
         +------------------+
                 |
         [iterate max 3x or
          until budget/time]
                 |
                 v
         +------------------+
         | Citation         |
         | Validator        |
         | (NLI entailment  |
         |  per claim)      |
         +------------------+
                 |
                 v
         +------------------+
         | Report Formatter |
         | Markdown + PDF   |
         | export           |
         +------------------+
                 |
                 v
           User (SSE stream
            of progress +
            final report)
```

### Iterative Deepening Loop

```
Phase 1 — Plan:
  User query → QueryPlanner → 8-12 sub-questions with priority[1-5]

Phase 2 — Retrieve:
  For each sub-question (top priority first):
    → Web search API (Exa / Bing) → 10-20 candidate URLs
    → ParallelCrawler fetches all URLs (asyncio, semaphore 100)
    → SourceEvaluator scores and deduplicates
    → Top 5 sources per sub-question stored in session cache

Phase 3 — Synthesize:
  → GroundedSynthesizer generates each report section
  → CitationValidator verifies each factual claim
  → Draft report assembled

Phase 4 — Detect gaps:
  → GapDetector scores each sub-question completeness (0.0 - 1.0)
  → Sub-questions with score < 0.7 AND time budget remaining:
      generate follow-up sub-questions → Phase 2 (max 3 iterations)

Phase 5 — Finalize:
  → Final synthesis pass with complete source set
  → ReportFormatter produces structured Markdown
  → Streamed to user via SSE
```

See also: [Agent Durability Patterns](./cross_cutting/agent_durability_patterns.md) for checkpoint-resume task graph implementation and exactly-once progress guarantees in long-running agents.

---

## 4. Component Deep Dives

### 4.1 Query Planner and Sub-Question Decomposition

The query planner converts a user's natural language question into a structured research agenda. It uses a large reasoning model (GPT-4o or o3) to identify the sub-topics that must be covered for a complete answer. Each sub-question receives a priority score, a set of search terms optimized for web retrieval, and an estimate of how many credible sources should exist.

```python
from __future__ import annotations
from dataclasses import dataclass, field
from enum import IntEnum
import json
import openai


class Priority(IntEnum):
    CRITICAL = 5; HIGH = 4; MEDIUM = 3; LOW = 2; OPTIONAL = 1


@dataclass
class SubQuestion:
    text: str
    priority: Priority
    search_terms: list[str]          # web-optimized terms (not NL question form)
    estimated_sources_needed: int
    completeness_score: float = 0.0  # filled in by GapDetector after retrieval
    answer_summary: str = ""         # filled in after synthesis


@dataclass
class ResearchPlan:
    original_query: str
    sub_questions: list[SubQuestion]
    estimated_duration_minutes: int
    estimated_cost_usd: float
    breadth_depth_setting: str       # "breadth" | "balanced" | "depth"


PLANNER_SYSTEM_PROMPT = """You are a research planning expert. Given a research question,
decompose it into 8-12 specific sub-questions that together form a complete answer.
For each sub-question output JSON with keys: text, priority (1-5), search_terms (list of 3-5
web search queries), estimated_sources_needed (integer 2-10).
Output a JSON array. No prose, only JSON."""


class QueryPlanner:
    def __init__(self, client: openai.AsyncOpenAI, model: str = "gpt-4o") -> None:
        self._client = client
        self._model = model

    async def decompose(
        self, query: str, breadth_depth: str = "balanced"
    ) -> ResearchPlan:
        num_sub_questions = {"breadth": 12, "balanced": 10, "depth": 7}[breadth_depth]

        user_prompt = (
            f"Research question: {query}\n"
            f"Scope setting: {breadth_depth} (generate {num_sub_questions} sub-questions)\n"
            "Output JSON array of sub-questions."
        )

        response = await self._client.chat.completions.create(
            model=self._model,
            messages=[
                {"role": "system", "content": PLANNER_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            response_format={"type": "json_object"},
            temperature=0.2,
            max_tokens=2000,
        )

        raw = json.loads(response.choices[0].message.content or "[]")
        sub_qs: list[SubQuestion] = []
        for item in raw.get("sub_questions", raw) if isinstance(raw, dict) else raw:
            sub_qs.append(SubQuestion(
                text=item["text"],
                priority=Priority(item["priority"]),
                search_terms=item["search_terms"],
                estimated_sources_needed=item.get("estimated_sources_needed", 5),
            ))

        # Sort by priority descending so retrieval phase works highest-priority first
        sub_qs.sort(key=lambda q: q.priority, reverse=True)

        # Estimate cost: 150 URLs x $0.001 avg + 90K tokens x blended $0.0015/K
        estimated_cost = len(sub_qs) * 15 * 0.001 + 90_000 * 0.0015 / 1000
        return ResearchPlan(
            original_query=query,
            sub_questions=sub_qs,
            estimated_duration_minutes=max(5, len(sub_qs) * 2),
            estimated_cost_usd=round(estimated_cost, 2),
            breadth_depth_setting=breadth_depth,
        )
```

### 4.2 Parallel Web Crawler

This is the single most impactful component for task duration. Fetching 200 URLs sequentially is the primary failure mode in naive implementations.

**BROKEN — sequential fetching, taking 200 x 2s = 400 seconds per task:**

```python
# BROKEN: sequential URL fetching
import httpx


class NaiveCrawler:
    def fetch_all(self, urls: list[str]) -> list[dict]:
        results = []
        client = httpx.Client(timeout=5.0)
        for url in urls:
            try:
                resp = client.get(url, headers={"User-Agent": "ResearchBot/1.0"})
                results.append({"url": url, "content": resp.text, "status": resp.status_code})
            except Exception as e:
                results.append({"url": url, "content": "", "status": 0, "error": str(e)})
        return results

# At 200 URLs x 2s avg fetch time = 400s sequential crawl time.
# A 20-minute task budget would be consumed by crawling alone before synthesis begins.
# Also: all failures are independent — one slow URL blocks all subsequent fetches.
```

**FIX — asyncio with semaphore(100) concurrency limit, completing 200 URLs in ~10 seconds:**

```python
from __future__ import annotations
import asyncio
import time
from dataclasses import dataclass

import httpx
from playwright.async_api import async_playwright, Browser


@dataclass
class FetchResult:
    url: str
    content: str          # extracted plain text (HTML stripped)
    status_code: int
    fetch_time_ms: int
    method: str           # "httpx" | "playwright" | "jina" | "failed"
    token_estimate: int   # rough word count / 0.75


JS_HEAVY_DOMAINS = {
    "bloomberg.com", "wsj.com", "nytimes.com", "ft.com",
    "reuters.com", "techcrunch.com", "medium.com",
}

JINA_FALLBACK_DOMAINS = {
    "cloudflare.com",  # anti-bot; direct Playwright will also be blocked
}


class ParallelCrawler:
    # JS-rendered pages → Playwright; anti-bot-protected → Jina; static → httpx
    # Semaphore(100) caps concurrent connections to avoid Linux FD limit (default 1024)
    SEMAPHORE_LIMIT = 100
    FETCH_TIMEOUT_S = 5.0
    MAX_CONTENT_CHARS = 25_000   # ~6,250 tokens
    JINA_BASE_URL = "https://r.jina.ai/"

    def __init__(self, jina_api_key: str) -> None:
        self._jina_key = jina_api_key
        self._semaphore = asyncio.Semaphore(self.SEMAPHORE_LIMIT)

    async def fetch_all(self, urls: list[str]) -> list[FetchResult]:
        async with async_playwright() as pw:
            browser = await pw.chromium.launch(headless=True)
            async with httpx.AsyncClient(
                timeout=self.FETCH_TIMEOUT_S,
                headers={"User-Agent": "ResearchAgent/2.0 (+https://research.example.com/bot)"},
                follow_redirects=True,
            ) as http_client:
                tasks = [
                    self._fetch_one(url, http_client, browser)
                    for url in urls
                ]
                results = await asyncio.gather(*tasks, return_exceptions=False)

            await browser.close()

        return list(results)

    async def _fetch_one(
        self, url: str, client: httpx.AsyncClient, browser: Browser
    ) -> FetchResult:
        async with self._semaphore:
            domain = self._extract_domain(url)
            t0 = time.monotonic()

            if domain in JINA_FALLBACK_DOMAINS:
                return await self._fetch_via_jina(url, client, t0)

            if domain in JS_HEAVY_DOMAINS:
                return await self._fetch_via_playwright(url, browser, t0)

            return await self._fetch_via_httpx(url, client, t0)

    async def _fetch_via_httpx(self, url: str, client: httpx.AsyncClient, t0: float) -> FetchResult:
        try:
            resp = await client.get(url)
            text = self._strip_html(resp.text)[: self.MAX_CONTENT_CHARS]
            ms = int((time.monotonic() - t0) * 1000)
            return FetchResult(url=url, content=text, status_code=resp.status_code,
                               fetch_time_ms=ms, method="httpx",
                               token_estimate=len(text.split()) * 4 // 3)
        except Exception:
            return await self._fetch_via_jina(url, client, t0)   # retry via Jina on block

    async def _fetch_via_playwright(self, url: str, browser: Browser, t0: float) -> FetchResult:
        try:
            page = await browser.new_page()
            await page.goto(url, timeout=8000, wait_until="domcontentloaded")
            await page.wait_for_timeout(800)   # allow React/Next.js hydration
            body_text = await page.evaluate("() => document.body.innerText")
            text = body_text[: self.MAX_CONTENT_CHARS]
            await page.close()
            ms = int((time.monotonic() - t0) * 1000)
            return FetchResult(url=url, content=text, status_code=200,
                               fetch_time_ms=ms, method="playwright",
                               token_estimate=len(text.split()) * 4 // 3)
        except Exception:
            return FetchResult(url=url, content="", status_code=0,
                               fetch_time_ms=int((time.monotonic() - t0) * 1000),
                               method="failed", token_estimate=0)

    async def _fetch_via_jina(self, url: str, client: httpx.AsyncClient, t0: float) -> FetchResult:
        try:
            resp = await client.get(f"{self.JINA_BASE_URL}{url}",
                                    headers={"Authorization": f"Bearer {self._jina_key}"})
            text = resp.text[: self.MAX_CONTENT_CHARS]
            ms = int((time.monotonic() - t0) * 1000)
            return FetchResult(url=url, content=text, status_code=resp.status_code,
                               fetch_time_ms=ms, method="jina",
                               token_estimate=len(text.split()) * 4 // 3)
        except Exception:
            return FetchResult(url=url, content="", status_code=0,
                               fetch_time_ms=int((time.monotonic() - t0) * 1000),
                               method="failed", token_estimate=0)

    @staticmethod
    def _extract_domain(url: str) -> str:
        from urllib.parse import urlparse
        return urlparse(url).netloc.lstrip("www.")

    @staticmethod
    def _strip_html(html: str) -> str:
        # Production: use trafilatura or readability-lxml for main content extraction
        import re
        return re.sub(r"<[^>]+>", " ", html)
```

Throughput comparison:
- Sequential (BROKEN): 200 URLs x 2s = 400 seconds
- Parallel async (FIX): 200 URLs / semaphore(100) at 2s avg = ~4 seconds for first 100, ~4s for next 100 = ~8-12 seconds total for 200 URLs (practical: 10-15 seconds accounting for variance and slow outliers)

### 4.3 Source Quality Evaluator

Not all fetched URLs are equally valuable. A Wikipedia article about drug discovery, a peer-reviewed Nature Medicine paper, and a vendor blog post all appear as search results but carry vastly different evidential weight. The evaluator scores each source on four signals before deciding which 50 sources enter the synthesis context.

```python
from __future__ import annotations
from dataclasses import dataclass
import math
import re
import time


HIGH_AUTHORITY_DOMAINS = {
    "nature.com": 1.0, "science.org": 1.0, "nejm.org": 1.0,
    "arxiv.org": 0.90, "pubmed.ncbi.nlm.nih.gov": 0.90,
    "semanticscholar.org": 0.88,
    "wikipedia.org": 0.75,
    "nih.gov": 0.85, "who.int": 0.85, "cdc.gov": 0.82,
    "techcrunch.com": 0.55, "medium.com": 0.40,
}


@dataclass
class ExtractedSource:
    url: str
    content: str
    fetch_result_method: str
    fetched_at_unix: float = 0.0

    def domain(self) -> str:
        from urllib.parse import urlparse
        return urlparse(self.url).netloc.lstrip("www.")

    def estimated_pub_year(self) -> int | None:
        """Extract 4-digit year from URL or content snippet."""
        m = re.search(r"(20\d{2})", self.url)
        return int(m.group(1)) if m else None


@dataclass
class ScoredSource:
    source: ExtractedSource
    total_score: float            # 0.0 - 1.0 composite
    domain_authority: float
    recency_score: float
    relevance_score: float
    is_duplicate: bool = False


class SourceEvaluator:
    # Weights: domain authority 30%, recency 20%, relevance 50%
    RECENCY_HALF_LIFE_YEARS = 2.0   # halves score every 2 years
    TOP_N = 50

    def score(
        self, source: ExtractedSource, sub_question: SubQuestion
    ) -> ScoredSource:
        domain_auth = self._domain_authority(source)
        recency = self._recency_score(source)
        relevance = self._bm25_relevance(source.content, sub_question.search_terms)

        total = (
            0.30 * domain_auth
            + 0.20 * recency
            + 0.50 * relevance
        )
        return ScoredSource(
            source=source,
            total_score=round(total, 4),
            domain_authority=domain_auth,
            recency_score=recency,
            relevance_score=relevance,
        )

    def select_top_sources(
        self, scored: list[ScoredSource], max_sources: int = TOP_N
    ) -> list[ScoredSource]:
        deduped = self._dedup_by_content_similarity(scored)
        return sorted(deduped, key=lambda s: s.total_score, reverse=True)[:max_sources]

    def _domain_authority(self, source: ExtractedSource) -> float:
        domain = source.domain()
        score = HIGH_AUTHORITY_DOMAINS.get(domain)
        if score is None:
            for key, val in HIGH_AUTHORITY_DOMAINS.items():
                if domain.endswith(key):
                    score = val
                    break
        return score if score is not None else 0.35

    def _recency_score(self, source: ExtractedSource) -> float:
        year = source.estimated_pub_year()
        if year is None:
            return 0.5
        age_years = max(0.0, time.localtime().tm_year - year)
        return math.exp(-math.log(2) * age_years / self.RECENCY_HALF_LIFE_YEARS)

    def _bm25_relevance(self, content: str, search_terms: list[str]) -> float:
        # Simplified BM25; production uses rank-bm25 library
        if not content:
            return 0.0
        term_hits = sum(content.lower().count(t.lower()) for t in search_terms)
        normalized = term_hits / (len(content.split()) ** 0.5 + 1)
        return min(1.0, normalized / 5.0)

    def _dedup_by_content_similarity(
        self, scored: list[ScoredSource]
    ) -> list[ScoredSource]:
        # Jaccard on 5-gram shingles; production uses MinHash LSH (datasketch) for O(n)
        seen_shingles: list[set[str]] = []
        results: list[ScoredSource] = []
        for s in sorted(scored, key=lambda x: x.total_score, reverse=True):
            shingles = self._five_gram_shingles(s.source.content)
            if any(self._jaccard(shingles, prev) > 0.85 for prev in seen_shingles):
                s.is_duplicate = True
            else:
                seen_shingles.append(shingles)
                results.append(s)
        return results

    @staticmethod
    def _five_gram_shingles(text: str) -> set[str]:
        words = text.lower().split()
        return {" ".join(words[i:i+5]) for i in range(len(words) - 4)}

    @staticmethod
    def _jaccard(a: set[str], b: set[str]) -> float:
        if not a or not b:
            return 0.0
        return len(a & b) / len(a | b)
```

### 4.4 Citation-Grounded Synthesizer

The synthesizer generates report sections with strict attribution. Before generating each paragraph, it injects the most relevant source excerpts. After generation, it verifies that every factual claim is entailed by at least one cited source using an NLI model. Paragraphs that fail verification are regenerated with an explicit instruction to only state what the sources confirm.

```python
from __future__ import annotations
from dataclasses import dataclass, field
import json
import openai


@dataclass
class CitationVerification:
    claim: str
    cited_source_url: str
    entailment_score: float   # 0.0-1.0 from NLI model
    verified: bool            # True if entailment_score > 0.75


@dataclass
class SectionResult:
    title: str
    content: str
    citations: list[str]                   # list of source URLs cited
    verifications: list[CitationVerification]
    regenerated: bool = False              # True if first pass failed verification


SYNTHESIS_SYSTEM_PROMPT = """You are writing one section of a research report.
You have been given excerpts from {n_sources} sources. Write the section using ONLY
information present in these excerpts. For every factual claim, add an inline citation
in the format [Source N]. Do not add information from your training data that is not
present in the provided excerpts. Be specific: include numbers, dates, and names from sources."""

VERIFICATION_PROMPT = """Given this factual claim extracted from a report:
CLAIM: {claim}
And this excerpt from the cited source:
SOURCE: {source_excerpt}
Does the source excerpt entail (directly support) the claim?
Answer with JSON: {{"entails": true/false, "confidence": 0.0-1.0, "reason": "one sentence"}}"""


class GroundedSynthesizer:
    NLI_ENTAILMENT_THRESHOLD = 0.75
    MAX_REGENERATION_ATTEMPTS = 2

    def __init__(self, client: openai.AsyncOpenAI, model: str = "gpt-4o") -> None:
        self._client = client
        self._model = model

    async def generate_section(
        self,
        section_title: str,
        relevant_sources: list[ScoredSource],
        max_output_tokens: int = 1500,
    ) -> SectionResult:
        top_5 = relevant_sources[:5]
        system = SYNTHESIS_SYSTEM_PROMPT.format(n_sources=len(top_5))
        user = f"Section title: {section_title}\n\n{self._format_source_block(top_5)}\n\nWrite this section now."

        for attempt in range(self.MAX_REGENERATION_ATTEMPTS + 1):
            response = await self._client.chat.completions.create(
                model=self._model,
                messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
                max_tokens=max_output_tokens, temperature=0.3,
            )
            content = response.choices[0].message.content or ""
            verifications = await self._verify_citations(content, top_5)
            failed = [v for v in verifications if not v.verified]
            if not failed:
                return SectionResult(section_title, content,
                                     [s.source.url for s in top_5], verifications, attempt > 0)
            failed_claims = "; ".join(v.claim for v in failed)
            user += (f"\n\nUnverified claims: {failed_claims}. "
                     "Correct these. Only state what sources explicitly say.")

        return SectionResult(section_title, content, [s.source.url for s in top_5], verifications, True)

    async def _verify_citations(
        self, section_text: str, sources: list[ScoredSource]
    ) -> list[CitationVerification]:
        # Sentence-level claim extraction; production uses a dedicated claim-extraction model
        sentences = [s.strip() for s in section_text.split(".") if len(s.strip()) > 30]
        verifications: list[CitationVerification] = []
        import re
        for sentence in sentences[:20]:   # cap at 20 claims to bound verification latency
            match = re.search(r"\[Source (\d+)\]", sentence)
            if not match:
                continue
            idx = int(match.group(1)) - 1
            if idx >= len(sources):
                continue
            source = sources[idx]
            prompt = VERIFICATION_PROMPT.format(claim=sentence,
                                                source_excerpt=source.source.content[:1500])
            resp = await self._client.chat.completions.create(
                model="gpt-4o-mini",  # $0.15/M input; verifying 50 claims costs ~$0.006
                messages=[{"role": "user", "content": prompt}],
                response_format={"type": "json_object"},
                max_tokens=150, temperature=0.0,
            )
            raw = json.loads(resp.choices[0].message.content or "{}")
            confidence = float(raw.get("confidence", 0.0))
            verifications.append(CitationVerification(
                claim=sentence, cited_source_url=source.source.url,
                entailment_score=confidence,
                verified=raw.get("entails", False) and confidence > self.NLI_ENTAILMENT_THRESHOLD,
            ))
        return verifications

    @staticmethod
    def _format_source_block(sources: list[ScoredSource]) -> str:
        return "\n".join(
            f"[Source {i}] URL: {s.source.url}\n{s.source.content[:2000]}\n---"
            for i, s in enumerate(sources, 1)
        )
```

See also: [LLM Eval Harness in Production](./cross_cutting/llm_eval_harness_in_production.md) for the faithfulness evaluation pipeline used to regress-test citation accuracy across report versions.

### 4.5 Gap Detector and Iterative Deepening

After the initial synthesis pass, the gap detector evaluates how thoroughly each sub-question was answered. Sub-questions with a completeness score below 0.7 trigger an additional retrieval-and-synthesis cycle, up to a maximum of 3 iterations.

```python
from __future__ import annotations
from dataclasses import dataclass
import json
import openai


GAP_DETECTION_PROMPT = """You are evaluating the completeness of a research report draft.
For each sub-question below, score how well the draft answers it on a scale of 0.0 to 1.0.
Output JSON: {{"scores": [{{"sub_question": "...", "completeness": 0.0-1.0, "missing": "what is missing"}}]}}
Draft report excerpt:
{draft_excerpt}
Sub-questions to evaluate:
{sub_questions}"""


@dataclass
class GapReport:
    unanswered_sub_questions: list[SubQuestion]  # completeness < threshold
    follow_up_sub_questions: list[SubQuestion]   # generated to fill gaps
    iteration_number: int


class GapDetector:
    COMPLETENESS_THRESHOLD = 0.70
    THRESHOLD_RELAXATION_PER_ITERATION = 0.10  # 0.70 → 0.60 → 0.50 across 3 iterations
    MAX_FOLLOW_UP_QUESTIONS_PER_GAP = 2

    def __init__(self, client: openai.AsyncOpenAI) -> None:
        self._client = client

    async def evaluate_coverage(
        self,
        draft_report: str,
        sub_questions: list[SubQuestion],
        iteration_number: int,
        token_budget_remaining: int,
    ) -> GapReport:
        if token_budget_remaining < 20_000:   # preserve budget for final synthesis pass
            return GapReport([], [], iteration_number)

        effective_threshold = max(
            0.30,
            self.COMPLETENESS_THRESHOLD - iteration_number * self.THRESHOLD_RELAXATION_PER_ITERATION,
        )
        draft_excerpt = draft_report[:8000]
        prompt = GAP_DETECTION_PROMPT.format(
            draft_excerpt=draft_excerpt,
            sub_questions="\n".join(f"- {q.text}" for q in sub_questions),
        )
        resp = await self._client.chat.completions.create(
            model="gpt-4o",
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"},
            max_tokens=1500, temperature=0.1,
        )
        scores = json.loads(resp.choices[0].message.content or "{}").get("scores", [])
        unanswered: list[SubQuestion] = []
        for item in scores:
            completeness = float(item.get("completeness", 1.0))
            q = next((x for x in sub_questions if x.text == item.get("sub_question")), None)
            if q:
                q.completeness_score = completeness
                if completeness < effective_threshold:
                    unanswered.append(q)

        follow_ups = await self._generate_follow_up_questions(unanswered, draft_excerpt)
        return GapReport(unanswered, follow_ups, iteration_number)

    async def _generate_follow_up_questions(
        self, gaps: list[SubQuestion], draft_excerpt: str
    ) -> list[SubQuestion]:
        if not gaps:
            return []
        n = self.MAX_FOLLOW_UP_QUESTIONS_PER_GAP * len(gaps)
        prompt = (
            "Unanswered sub-questions:\n"
            + "\n".join(f"- {q.text}" for q in gaps)
            + "\nDraft:\n" + draft_excerpt[:2000]
            + f"\nGenerate {n} specific sub-questions to fill these gaps. JSON array of strings."
        )
        resp = await self._client.chat.completions.create(
            model="gpt-4o",
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"},
            max_tokens=800, temperature=0.3,
        )
        raw = json.loads(resp.choices[0].message.content or "[]")
        questions = raw if isinstance(raw, list) else raw.get("questions", [])
        return [
            SubQuestion(text=q, priority=Priority.HIGH, search_terms=[q], estimated_sources_needed=3)
            for q in questions[:n]
        ]
```

---

## 5. Design Decisions and Tradeoffs

| Decision | Chosen Approach | Alternative Considered | Rationale |
|---|---|---|---|
| Sub-question traversal order | Priority-first, then breadth-across-priorities | Pure breadth-first | High-priority sub-questions answered first ensures a complete report even if time budget runs out before all sub-questions are covered |
| LLM model assignment | GPT-4o for planning/synthesis; GPT-4o-mini for summarization and NLI verification | Single GPT-4o for all steps | Small model for per-source summarization reduces per-task LLM cost by ~60% (40K summarization tokens x $0.15/M vs $2.50/M); quality difference negligible for extractive summarization |
| Web data freshness | Real-time web search (Exa, Bing) per task | Pre-indexed proprietary web crawl | Real-time search provides freshness within hours; academic topics require citing papers published this month; indexed crawls would lag 2-4 weeks on breaking research |
| Synthesis strategy | Section-by-section sequential with rolling source context | Single-pass synthesis of all sources | Single-pass: 50 sources x 5KB = 250KB exceeds 128K token context limit; section-by-section allows selective source injection per section, reducing hallucination and token cost |
| Gap detection stopping criterion | Max 3 iterations plus token ceiling plus threshold relaxation | Fixed time budget only | Pure time budget causes variable quality; adding threshold relaxation (0.70 → 0.60 → 0.50) ensures convergence; token ceiling enforces hard cost cap |
| Citation verification | NLI entailment per claim using GPT-4o-mini judge | RAGAs faithfulness metric | GPT-4o-mini judge at $0.15/M input tokens costs ~$0.006 per 1,000 claims verified; RAGAs is equivalent quality but requires self-hosting a cross-encoder model; judge approach is zero-ops |
| Per-domain rate limiting | Redis sorted-set per domain, max 5 req/min shared across all tasks | Per-task rate limiting only | Per-task: each task independently limits itself, still allowing 10,000 tasks x 5 req/min = 50,000 req/min to a single domain simultaneously — enough to get IP-blocked; Redis shared rate limiter coordinates across all concurrent tasks |

### Breadth vs Depth Dial Impact

| Setting | Sub-questions | URLs fetched | LLM tokens | Duration | Cost |
|---|---|---|---|---|---|
| Breadth | 12 | 180 | 110K | 18 min | $0.17 |
| Balanced | 10 | 150 | 90K | 14 min | $0.14 |
| Depth | 7 | 105 | 65K | 10 min | $0.10 |

---

## 6. Real-World Implementations

**OpenAI Deep Research (Feb 2025)**: Uses the o3 reasoning model as the backbone, which applies chain-of-thought reasoning before each retrieval decision — effectively the model plans what to search next based on what it has already found. Runs for 5-30 minutes depending on task complexity. Generates reports with inline citations. Achieved 26.7% on Humanity's Last Exam benchmark (vs GPT-4o's 3.3%), demonstrating the gap that multi-step iterative retrieval opens over single-pass generation. Estimated per-task cost based on o3 token pricing ($15/M input, $60/M output) is $8-25 depending on depth. Available to ChatGPT Pro subscribers ($200/month) with a usage limit of 100 research tasks per month. Architecture is not public, but OpenAI's blog describes a specialized browser tool that the o3 model calls to navigate the web — the model decides which URLs to fetch based on reasoning traces, rather than executing a predetermined crawl plan.

**Perplexity Deep Research (Feb 2025)**: Built on top of Perplexity's existing real-time web search infrastructure, which already indexed billions of pages for Pro Search. Deep Research extends this by running 5-10 minutes of iterative search-and-synthesis rather than the single-shot retrieval of Pro Search. Produces shorter reports (typically 2-4 pages vs OpenAI's 10-15 pages) but finishes 3-5x faster. Exports to PDF and Markdown directly. Available to Pro users at no additional cost (bundled into $20/month subscription). Focused on consumer research tasks: product comparisons, news summaries, travel research.

**Google Gemini Deep Research (Dec 2024)**: Distinctive UX: generates and shows the research plan to the user for approval before executing. The user sees the list of sub-topics Gemini intends to research and can add, remove, or reorder them before execution begins. This is a significant UX advantage — users feel in control and the final report better matches their intent. Uses Gemini 1.5 or 2.0 with the 1M token context window, which means Gemini can hold all retrieved sources in a single context rather than chunking synthesis. Google Search integration gives breadth coverage not available to OpenAI or Perplexity. Available in Google One AI Premium plan ($19.99/month).

**Elicit (academic-focused, Y Combinator 2021)**: Specialized for scientific literature rather than general web research. Integrates the Semantic Scholar API and PubMed for source retrieval. Models are specifically fine-tuned for academic paper extraction: identify methodology, extract results tables, summarize limitations. Outputs structured evidence tables rather than prose — a row per paper with columns for sample size, effect size, confidence interval. Used by researchers at Pfizer, Genentech, and academic institutions. Charges per-task ($2-10 per research task) rather than subscription. Citation verification is stronger than consumer tools: every DOI is verified against Semantic Scholar's live index before inclusion.

**You.com ARI — Automated Research Intelligence (enterprise)**: Targets business intelligence use cases: competitive analysis, market research, regulatory monitoring. Integrates proprietary data sources (Crunchbase, Statista, court filings) alongside web search, providing data that is not publicly indexed. Produces PowerPoint-ready output and Excel-format evidence tables in addition to Markdown. Sells as an enterprise SaaS at $50K-500K/year per seat, competing with McKinsey Knowledge Centre and Gartner research subscriptions.

---

## 7. Technologies and Tools

### Web Crawling Options

| Tool | JS Rendering | Speed (per URL) | Cost | Anti-Bot Handling | Best For |
|---|---|---|---|---|---|
| httpx + BeautifulSoup | No | 0.5-2s | Free (self-hosted) | Basic user-agent rotation | Static pages, academic sites |
| Playwright headless | Yes | 2-5s | Free (self-hosted, CPU cost) | Stealth mode plugin, fingerprint randomization | JS SPAs, Bloomberg, Reuters |
| Jina Reader API | Yes (server-side) | 1-3s | $0.01/1K URLs | Handled by Jina's infrastructure | Anti-bot protected sites, rapid prototyping |
| Firecrawl | Yes | 1-4s | $0.015/page | Handles Cloudflare, paywalls | Full-stack research; handles 95% of sites |
| Browserless | Yes | 2-4s | $0.02/session | Fingerprint randomization | High-volume JS rendering at scale |

### Search APIs

| API | Freshness | Academic Coverage | Cost per 1K Queries | Rate Limit | Best For |
|---|---|---|---|---|---|
| Exa | Hours (neural search) | Good (indexes arXiv) | $5 | 1,000 req/min | AI/tech research topics |
| Bing Search API | Hours | Poor | $7 | 250 req/sec | General web coverage |
| Brave Search API | Hours | Moderate | $3 | 100 req/sec | Privacy-sensitive, no tracking |
| SerpApi | Hours (scrapes Google) | Moderate | $15 | 100 req/min | Google SERP coverage |
| Semantic Scholar API | Days (indexing lag) | Excellent (200M+ papers) | Free (rate-limited) | 100 req/5min | Academic research tasks |

### NLI and Faithfulness Verification

| Tool | Accuracy (FEVER) | Latency per Claim | Cost per 1K Claims | Deployment |
|---|---|---|---|---|
| GPT-4o-mini judge | 89% | 400ms | $0.15 | API (zero-ops) |
| Bespoke-Minicheck | 92% | 80ms | $0.02 (self-hosted) | GPU required |
| HHEM-2.1 (Vectara) | 87% | 60ms | $0.01 (self-hosted) | GPU required |
| cross-encoder/nli-deberta | 85% | 50ms | $0.008 (self-hosted) | CPU-capable |
| RAGAs faithfulness | 88% | 500ms | $0.20 (OpenAI backend) | API or self-hosted |

---

## 8. Operational Playbook

### (a) Eval Pipeline

Weekly automated evaluation runs every Monday at 03:00 UTC on 20 known research questions with human-labeled reference answers, covering diverse domains (science, medicine, history, technology, law). Any deployment of a new LLM model version (planner, synthesizer, or verifier) triggers an immediate out-of-band eval run before traffic migration.

Three metric dimensions (all tracked per model version):
- Faithfulness: fraction of factual claims entailed by a cited source (target >95%; alert <90%)
- Completeness: fraction of reference key facts present in the generated report (target >80%; alert <70%)
- Citation accuracy: cited source actually contains the attributed information (target >95%; alert <90%)

Each eval run emits a `ResearchEvalResult` record: `{question_id, faithfulness_score, completeness_score, citation_accuracy, task_duration_seconds, total_cost_usd, baseline_faithfulness, regression_pct}`. A deployment that causes `regression_pct > 5%` on any dimension is blocked automatically via CI gate.

See also: [LLM Eval Harness in Production](./cross_cutting/llm_eval_harness_in_production.md) for the LLM-judge rubric, golden dataset construction, and regression gate CI/CD integration.

### (b) Observability

Every research task produces a nested OpenTelemetry trace. Root span duration matches task wall-clock time; child spans reveal where time is spent.

```
Trace: research_task (trace_id: abc123, task_id: t_789)
  |
  +-- Span: query_planner.decompose              (8s)
  |     attrs: task_id, num_sub_questions=10, breadth_depth="balanced"
  |     attrs: plan_tokens_in=2100, plan_tokens_out=520
  |
  +-- Span: web_crawler.fetch_batch              (12s)
  |     attrs: urls_requested=150, urls_succeeded=138, urls_failed=12
  |     attrs: method_distribution={httpx:85, playwright:42, jina:11}
  |     attrs: p50_fetch_ms=1200, p99_fetch_ms=4800
  |
  +-- Span: source_evaluator.score_and_select    (2s)
  |     attrs: sources_in=138, sources_after_dedup=91, sources_selected=50
  |
  +-- Span: grounded_synthesizer.generate        (45s)   ← usually the longest span
  |     attrs: sections_generated=6, sections_regenerated=1
  |     attrs: claims_verified=48, claims_failed_verification=3
  |     attrs: synthesis_tokens_in=18500, synthesis_tokens_out=4200
  |
  +-- Span: gap_detector.evaluate                (6s)
  |     attrs: iteration=1, unanswered_sub_questions=2, threshold_used=0.70
  |
  +-- Span: web_crawler.fetch_batch [iter2]      (8s)
  |     attrs: urls_requested=40, iteration=2
  |
  +-- Span: grounded_synthesizer.generate [iter2] (22s)
  |     attrs: iteration=2
  |
  +-- Span: report_formatter.format              (1s)
  |     attrs: output_words=3200, sections=6, citations=47
  |
  +-- Span: task.complete
        attrs: total_duration_s=104, total_cost_usd=0.28, faithfulness_p90=0.97
```

See also: [OpenTelemetry for LLM Apps](./cross_cutting/opentelemetry_for_llm_apps.md) for the full `gen_ai.*` semantic convention mapping, token histogram configuration, and cost attribution per span using span attributes.

### (c) Incident Runbooks

**Runbook 1 — Crawler Blocked by Anti-Bot (Cloudflare)**

Symptoms: more than 50% of URLs for tasks on a specific news domain returning HTTP 403 or 429; `crawler.fetch_batch` spans showing `method="httpx"` with `status_code=403`; task completeness scores dropping below 60% for affected topics.

Diagnosis: check `url_fetch_status_distribution` metric grouped by domain — identify which domains are blocking. Confirm Cloudflare fingerprint detection by comparing user-agent header in logs.

Mitigation (within 5 minutes): add affected domains to `JINA_FALLBACK_DOMAINS` set via feature flag; Jina Reader API handles Cloudflare bypass. Rotate Playwright user-agent pool and enable stealth mode plugin on Playwright pods.

Resolution (within 24 hours): evaluate Firecrawl API for permanent integration with anti-bot domains; add blocked domain to routing table so future crawls default to Jina or Firecrawl. Add per-domain success-rate metric alert (threshold: >30% failure rate triggers routing change automatically).

**Runbook 2 — Cost Blowout (Task Exceeding $20 Ceiling)**

Symptoms: billing alert for a single task exceeding $20; task still running at 25-minute mark; `gap_detector.evaluate` span showing `iteration=7` in trace.

Diagnosis: check gap detector logs — is the completeness threshold being reached? Check `token_budget_remaining` attribute on gap detector spans: it should decrease each iteration. If it is not decreasing, the token counter has a bug.

Mitigation (immediate): enforce a hard token ceiling at the LLM client wrapper level: `if total_tokens_consumed > 500_000: raise TokenBudgetExhausted`. Budget exhaustion causes the task to finalize with the current draft rather than iterating. This is the last-resort safety valve.

Resolution: fix gap detector to pass `token_budget_remaining` correctly and check it before every iteration. Add maximum iteration hard ceiling (3) as an independent check from the token budget.

**Runbook 3 — Faithfulness Regression**

Symptoms: weekly eval shows citation accuracy dropping from 97% to 83%; `claims_failed_verification` metric up 3x; user complaints about inaccurate report content.

Diagnosis: check whether a synthesis model update was deployed in the last 7 days. Compare `synthesis_model_version` span attribute in traces from before and after the drop.

Mitigation (within 1 hour): roll back synthesis model version via feature flag — revert `SYNTHESIS_MODEL` env var to the previous version, restart synthesis pods. Confirm faithfulness score recovers on 5 manual test cases.

Resolution: root cause the new model version's failure mode (increased hallucination rate at lower temperature, or prompt format change). Re-tune synthesis prompt for the new model version. Eval-gate the new model behind a canary that requires 95%+ faithfulness on the golden dataset before routing production traffic.

**Runbook 4 — Task Timeout Cascade**

Symptoms: more than 20% of tasks exceeding the 20-minute SLA; `research_task_duration_p99` metric spiking to 32 minutes; crawler spans showing `p99_fetch_ms=9800` (normal is 4800ms).

Diagnosis: check external search API latency — Exa or Bing may be degraded. Check crawler pod CPU and memory — Playwright sessions may be leaking. Check if a spike in JS-heavy domains is routing too many URLs through Playwright (which is 3-5x slower than httpx).

Mitigation (within 10 minutes): reduce URL budget per task from 150 to 80 (feature flag); this reduces crawler duration by ~45%. If Playwright is the bottleneck, temporarily route all JS-heavy domains to Jina API instead.

Resolution: scale crawler pod count from 50 to 80 pods if load has permanently increased. Add circuit breaker on external search APIs: if Exa p99 latency exceeds 3s, fall back to Bing automatically.

---

## 9. Common Pitfalls and War Stories

**Perplexity Plagiarism Controversy (Jun 2024)**: Perplexity's research feature was found to reproduce Forbes article content near-verbatim without adequate transformation. The specific content from a Forbes article about a Wired journalist's reporting was reproduced with minimal paraphrasing. Root cause: the synthesis prompt did not explicitly instruct the model to paraphrase and synthesize rather than extract; the citation system added the Forbes link but did not prevent verbatim string reproduction. Impact: Forbes sent a cease-and-desist; significant press coverage (The Verge, Wired) with quantified reputational damage across the week; Perplexity deployed a synthesis prompt update within 48 hours that added an explicit "do not reproduce verbatim; synthesize and paraphrase" instruction. The lesson: citation systems and synthesis prompts must be co-designed — adding a link does not make verbatim reproduction acceptable.

**Citation Hallucination at Scale — Elicit (2023 independent audit)**: An independent audit of Elicit's citation system found that approximately 8% of citations in a sample of 500 research tasks referenced DOIs that did not exist or did not support the cited claim. The LLM was fabricating plausible-looking DOI strings (format: 10.1038/s41591-023-XXXXX). The fix deployed by Elicit: verify every DOI against the Semantic Scholar live API before including it in the report; DOIs that return 404 are flagged and excluded. Post-fix citation accuracy improved from 92% to 99.3% (Elicit blog, Nov 2023). The lesson: LLMs produce plausible-sounding citations with exactly the right format — format compliance is not evidence of accuracy.

**Crawler Feedback Loop — IP Block Cascade**: A production deep research agent sent 1,200 requests to a single news site (Reuters) within 90 seconds because 12 concurrent tasks simultaneously searched for the same news topic and all received Reuters URLs in their search results. Reuters' WAF rate-limited the entire IP range. All 12 concurrent tasks received empty content from Reuters URLs for the next 15 minutes, causing 12 incomplete reports. The failure cascade: empty Reuters content → low-quality sources used instead → completeness scores dropped below threshold → gap detector triggered extra iterations → more Reuters requests → deeper rate-limiting. Fix: implement a per-domain rate limiter shared across all concurrent tasks in Redis: sorted set keyed by `rate_limit:{domain}`, max 5 requests per minute per domain across all tasks. The fix reduced domain-level request density by 240x for popular domains.

**Context Window Overflow in Synthesis**: An early version of the synthesis step injected all 50 selected sources (50 × 5KB extracted text = 250KB = ~65,000 tokens) into a single synthesis prompt. When the research topic was broad and sources were dense, total context hit 95,000 tokens and exceeded GPT-4o's 128K context limit, causing the synthesis API call to return a 413 Content Too Large error. The task silently returned an empty report to the user. Fix: enforce a source selection cap (top 50 by quality score), chunked synthesis per section (inject only the 5 most relevant sources per section), and add a pre-call token count assertion: `assert total_tokens < 100_000, f"Context overflow: {total_tokens} tokens"`.

**Gap Detector Infinite Loop ($45 Task)**: A gap detector bug caused it to flag "insufficient sources on AI ethics in clinical trials" across all 3 iterations because the threshold was fixed at 0.70 and the available web content on that narrow sub-topic genuinely could not achieve a relevance score above 0.70 with the existing source quality signals. The agent iterated 8 times (bypassing the iteration cap due to an off-by-one error in the loop counter), spending $45 before the 30-minute wall-clock timeout terminated it. Fix: three independent stopping conditions — hard iteration cap (3), token budget ceiling (500K tokens consumed), and threshold relaxation (0.70 → 0.60 → 0.50 on each iteration). Any one of the three stops the loop.

**Verbatim User Query Leakage in Multi-Tenant Session Cache**: Research session content (extracted URL text, 1MB per session) was stored in Redis with a 24-hour TTL. Session cache keys used a hash of the user query as the key. Two different enterprise users submitted queries that hashed to the same Redis key (SHA-256 collision probability negligible, but URL-encoded query truncation was creating inadvertent key collisions). User B's follow-up question retrieved cached sources from User A's session — a cross-tenant data exposure. Impact: two enterprise accounts; no PII exposed (sources were public web pages) but the research task was incorrectly personalized to the wrong session context. Fix: prefix all session cache keys with `{tenant_id}:{user_id}:{session_id}` before hashing; add a session ownership assertion on every cache read.

See also: [Streaming at Scale](./cross_cutting/streaming_at_scale.md) for SSE delivery patterns for multi-hour research tasks with progress streaming, reconnection handling, and partial report delivery.

---

## 10. Capacity Planning

### Primary Bottleneck: Crawler Throughput

```
Crawler throughput formula:
  concurrent_urls_per_second = (crawler_pods x connections_per_pod) / avg_fetch_time_s

Where:
  crawler_pods            = number of crawler worker pods
  connections_per_pod     = asyncio semaphore limit (100 per pod)
  avg_fetch_time_s        = weighted average: 80% httpx at 1.5s + 20% Playwright at 3.5s
                          = 0.80 x 1.5 + 0.20 x 3.5 = 1.90s average

URLs-per-second capacity:
  1 crawler pod:         100 / 1.90 = 52.6 URLs/sec
  50 crawler pods:       50 x 52.6 = 2,632 URLs/sec

Peak URL demand at 10,000 concurrent tasks:
  10,000 sessions x 150 URLs / (12 min x 60 sec) = 2,083 URLs/sec (during crawl phase)

50 crawler pods: 2,632 URLs/sec capacity vs 2,083 URLs/sec demand = 26% headroom.
```

### Cost at Scale and Scaling Headroom

```
Monthly infrastructure (50K tasks/day baseline):
  50 crawler pods (t3.xlarge, $0.166/hr):            $6,059
  10 Playwright pods (c5.2xlarge, $0.34/hr):         $2,482
  Redis cluster 3-node (r6g.2xlarge, $0.40/hr):        $876
  LLM API (blended $2,575/day x 30):               $77,250
  Total monthly:                                    $86,667

Revenue model (10K consumer users x $10/mo + 200 enterprise x $500/mo):
  $100,000 + $100,000 = $200,000/month
  Gross margin: ($200K - $87K) / $200K = 56%

Scaling headroom:
  Max concurrent sessions (crawl phase) = (50 pods x 100 conn/pod / 1.90s avg) / (150 URL/720s)
                                        = 2,632 / 0.208 = 12,654 sessions
  Current target: 10,000 (26% headroom).
  To support 20,000 sessions: 80 crawler pods ($9,695/month marginal cost).
```

---

## 11. Interview Discussion Points

**How does a deep research agent differ architecturally from a RAG-based chatbot?**

RAG retrieves in one shot: query → vector search → top-K chunks → generate. Deep research is an iterative agent loop: plan → retrieve → evaluate → detect gaps → re-retrieve → synthesize, taking 5-30 minutes. The primary bottleneck shifts from LLM inference latency to URL fetch throughput and orchestration durability. RAG fits in a single request-response cycle; deep research requires a durable task execution model with checkpoints, progress streaming, and retry semantics. The cost profile also differs: RAG is dominated by LLM tokens; deep research splits cost roughly 60% LLM, 30% crawling infrastructure, 10% storage and orchestration.

**Why is parallelizing URL fetching the single most important performance decision?**

Sequential fetching at 200 URLs x 2s = 400 seconds consumes the entire 20-minute task budget before synthesis begins. Parallel async fetching with asyncio.Semaphore(100) reduces the same 200 URLs to 10-15 seconds — a 26x speedup that is the difference between a usable product and a broken one. The concurrency limit exists to prevent local file descriptor exhaustion (Linux default: 1024 open FDs per process) and to avoid triggering target sites' per-IP rate limits. At 100 concurrent connections, the system stays under both limits while achieving near-maximum throughput on modern cloud instances.

**How do you verify that citations in the report are faithful to the cited sources?**

A two-step process: first, the synthesizer generates each paragraph with source excerpts injected directly into context and explicit instructions to only state what sources confirm. Second, an NLI (natural language inference) model evaluates each factual claim against its cited source excerpt, producing an entailment confidence score. Claims scoring below 0.75 trigger regeneration with more explicit constraints. The NLI judge is GPT-4o-mini at $0.15/M tokens — verifying 50 claims per task costs $0.006, negligible against the $0.10-5 task cost. This achieves 95%+ citation accuracy in production evaluation, compared to 85% without post-hoc verification.

**How does gap detection decide when to stop iterating without looping forever?**

Three independent stopping conditions: (1) hard iteration ceiling (max 3 deepening iterations), (2) token budget ceiling (task terminates synthesis if 500K tokens consumed), and (3) threshold relaxation (completeness threshold drops from 0.70 on iteration 1 to 0.60 on iteration 2 to 0.50 on iteration 3, guaranteeing convergence even when sources are sparse on a sub-topic). All three conditions must be checked before triggering another retrieval cycle. A real production incident: without condition 3, an agent iterated 8 times on a topic where the web genuinely did not have sufficient coverage, consuming $45 before wall-clock timeout.

**How do you handle paywalled content gracefully?**

Three tiers: static pages use httpx and extract full text; JavaScript-rendered pages use Playwright headless (which also handles soft paywalls that render an abstract or preview before the paywall); hard-paywalled pages (Nature, WSJ, FT) get the abstract and first paragraph via Playwright before the paywall triggers — typically 300-500 words of extractable content. For academic papers, the Semantic Scholar API provides structured abstracts, methodology, and results for 200M+ papers with zero paywall issues. The system tracks which sources were partial extracts vs full content and surfaces this in the source quality score (partial extract receives 0.6x multiplier on domain authority).

**How do you prevent the crawler from getting the entire platform IP-banned by popular sites?**

Per-domain rate limiting enforced in Redis across all concurrent tasks, not just within a single task. A sorted set keyed by `rate_limit:{domain}` with a sliding 60-second window caps requests at 5 per minute per domain platform-wide. This means 10,000 concurrent tasks can collectively send at most 5 requests per minute to Reuters — not 10,000 x 5. High-value domains (Nature, arXiv, Semantic Scholar) use their official APIs with API key authentication rather than crawling. Anti-bot-detected domains fall back to Jina Reader API, which uses Jina's IP infrastructure and handles Cloudflare fingerprinting. The system monitors per-domain 403/429 error rates and automatically adds domains to the Jina fallback list when the error rate exceeds 30%.

**How do you enforce a $20 cost ceiling mid-execution without failing the task?**

A token budget counter is passed through the entire task graph. Every LLM call decrements the counter by the tokens used. The gap detector checks `token_budget_remaining` before triggering another iteration. If the counter drops below a 20K-token minimum (enough for one final synthesis pass), the gap detector returns no follow-up questions and the task proceeds directly to final report formatting. The hard safety valve is a token ceiling enforced at the LLM client wrapper: any call that would exceed the total budget throws `TokenBudgetExhausted`, which the orchestrator catches and converts to a graceful finalization rather than an error. This means users always receive a partial but coherent report, not an error message.

**When should you use a single large LLM for all steps vs specialized models for each step?**

Specialized models win on cost for extractive, repetitive steps. Summarizing 50 web pages is repetitive and extractive — GPT-4o-mini is adequate and costs 16x less than GPT-4o per token ($0.15 vs $2.50 per million input tokens). Planning (deciding what to research) and synthesis (integrating contradictory sources into coherent prose) are reasoning-intensive — GPT-4o or o3 quality is necessary. NLI verification is binary classification — a cross-encoder or small judge model works. In practice: planning 5% of tokens at $2.50/M + summarization 55% of tokens at $0.15/M + synthesis 20% at $2.50/M + verification 20% at $0.15/M = blended rate of $0.72/M vs $2.50/M single-model. Blended model routing reduces per-task LLM cost by 71%.

**How do you handle contradictory sources? Two sources say opposite things about the same claim.**

The synthesizer prompt explicitly instructs the model to identify and surface contradictions rather than silently picking one source. When the NLI model detects two sources with high relevance scores that have low mutual entailment (they contradict), the synthesis prompt injects both sources and uses a specific instruction: "Source A says X; Source B says Y. Present both claims with their citations and note that the evidence is conflicting." This is a feature, not a bug — acknowledging scientific uncertainty is more faithful than artificially resolving it. The report includes a "Conflicting Evidence" subsection for topics where major sources disagree. Domain authority signals inform which claim to present first (higher-authority source leads) but both are cited.

**Why is Gemini's "show the research plan first" UX pattern architecturally superior to just starting research immediately?**

Three concrete benefits: (1) User intent alignment — users can remove sub-questions the agent planned that are irrelevant to their actual intent, preventing wasted fetch and synthesis cycles. A 12-sub-question plan that the user trims to 7 reduces task cost by ~42%. (2) Scope agreement — enterprise users need to approve what information the agent will research before it starts fetching documents, especially for sensitive competitive analysis. (3) Time estimation — showing the plan with estimated duration and cost allows users to choose the right breadth/depth setting before committing. The architectural implication: the planner step and the execution step must be separate phases with a human approval gate between them, requiring the task orchestrator to support a WAITING_FOR_APPROVAL state with a TTL (plan expires if user does not approve within 10 minutes).

**How do you evaluate deep research quality without ground-truth reference answers?**

Two complementary approaches: (1) LLM-as-judge with a rubric — evaluate the report on faithfulness (claims grounded in cited sources), coherence (logical structure, no contradictions between sections), and completeness (covers the expected sub-topics for that research domain). These dimensions do not require a reference answer. (2) Partial ground-truth via known facts — seed the research question with a topic where specific facts are known (e.g., "What is the half-life of ibuprofen?" has a known answer: 2 hours). Check that the report includes this fact. Use 5-10 such anchor facts per research domain. For academic topics, Elicit's approach is strongest: compare the set of papers cited against a human-curated "must-cite" list for that topic (recall at K metric). Production systems combine all three approaches in their weekly eval pipeline.

**How does task durability work for a 20-minute agent that must survive infra failures?**

Each phase of the task graph writes a checkpoint to Postgres before starting the next phase: plan generated, batch N of URLs fetched, synthesis of section K complete, iteration M of gap detection complete. If the task worker dies (pod OOM, spot preemption, network partition), the task orchestrator detects the missed heartbeat (10-second TTL in Redis) and reassigns the task to a new worker. The new worker reads the checkpoint from Postgres and resumes from the last completed phase — it does not re-fetch URLs that were already fetched and cached. The session source cache (Redis, 24-hour TTL) is the re-entry point: if the crawler checkpoint is complete but synthesis crashed, the new worker reads sources from Redis and starts synthesis without re-crawling. See [Agent Durability Patterns](./cross_cutting/agent_durability_patterns.md) for the checkpoint schema and exactly-once phase execution guarantees.
