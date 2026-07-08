# Design an Async Web Scraper

## Problem Statement

Build a web scraper that processes 10,000 product pages per hour from multiple e-commerce sites. The system must:

- Fetch product data (title, price, description, images) from 5-10 e-commerce domains
- Respect each site's `robots.txt` — skip disallowed paths
- Rate-limit requests per domain to a maximum of 5 requests per second
- Deduplicate URLs so already-scraped pages are not re-fetched within a 24-hour window
- Retry on transient failures (5xx, connection errors) with exponential backoff and jitter
- Persist extracted product records to PostgreSQL
- Expose a FastAPI management API: submit crawl jobs, monitor progress, stop a running crawl

**Scale targets:**
- 10,000 pages/hour = ~2.8 pages/second sustained throughput
- Average page size 200 KB; 2 GB/hour raw HTML (parse in memory, discard)
- Target latency per fetch: 300 ms–2 s depending on site
- Concurrency: 50–100 simultaneous open TCP connections across all domains

---

## Architecture Overview

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    api([FastAPI Management API])
    postJobs([POST /jobs])
    getJobs([GET /jobs])
    delJobs(["DELETE /jobs/{id}"])
    jobMgr(Job Manager<br/>asyncio.Task coordinator)
    frontier(URL Frontier<br/>asyncio.Queue)

    subgraph pool ["Consumer Workers · N=50"]
        w0(Consumer Worker 0)
        w1(Consumer Worker 1)
        wN(Consumer Worker N)
    end

    rateLimiter(DomainRateLimiter<br/>semaphore + politeness sleep)
    robots(robots.txt Checker<br/>cached 1h per domain)
    session(aiohttp Session<br/>shared connection pool)
    retry(Retry Decorator<br/>backoff + jitter)
    parser(HTML Parser<br/>BeautifulSoup4)
    dedup[(URL Dedup<br/>Redis SADD)]
    writer(DB Writer<br/>asyncpg upsert)
    pg[(PostgreSQL<br/>products table)]

    api --> postJobs
    api --> getJobs
    api --> delJobs
    postJobs --> jobMgr
    jobMgr --> frontier
    frontier --> w0
    frontier --> w1
    frontier --> wN
    w0 --> rateLimiter
    w1 --> rateLimiter
    wN --> rateLimiter
    rateLimiter --> robots
    robots --> session
    session --> retry
    retry --> parser
    retry --> dedup
    parser --> writer
    dedup --> writer
    writer --> pg

    class api,postJobs,getJobs,delJobs io
    class frontier,w0,w1,wN req
    class jobMgr,rateLimiter,robots,retry,parser mathOp
    class session frozen
    class dedup,pg base
    class writer train
```

**Data flow:**
1. FastAPI receives a crawl job request with seed URLs.
2. Job Manager enqueues seed URLs into the asyncio.Queue frontier.
3. 50 consumer workers pull URLs from the queue concurrently.
4. Each worker checks Redis for deduplication, consults robots.txt, acquires a domain rate-limit slot, fetches via aiohttp, parses HTML, and writes the product record to PostgreSQL.
5. Newly discovered product URLs from each page are pushed back onto the frontier.
6. Progress metrics are tracked in memory and exposed via FastAPI.

---

## Key Design Decisions

### 1. Concurrency Model: asyncio + aiohttp

Web scraping is I/O-bound: most time is spent waiting for network responses. Three options:

| Model | Best for | Drawback |
|---|---|---|
| asyncio + aiohttp | I/O-bound, 100s of concurrent connections | CPU parsing bottleneck if pages are large |
| threading | Legacy code, blocking libraries | GIL limits true parallelism; 100s of threads = high memory |
| multiprocessing | CPU-bound (image processing) | Process startup overhead; IPC complexity |

asyncio runs all 50 workers on a single thread. The event loop yields during `await session.get(url)`, allowing other workers to proceed. Memory footprint is ~1 MB per process vs ~8 MB per thread for 50 threads. For CPU-heavy parsing, `asyncio.get_event_loop().run_in_executor(None, parse_html, raw_html)` offloads to a thread pool without blocking the loop.

### 2. Rate Limiting Per Domain

**Broken version — global semaphore blocks all domains equally:**

```python
# BAD: one semaphore for all domains — example.com blocks shopify.com
_global_sem = asyncio.Semaphore(10)

async def fetch(url: str) -> str:
    async with _global_sem:
        async with session.get(url) as resp:
            return await resp.text()
```

This violates per-domain rate contracts and can starve fast-responding domains waiting behind a slow one.

**Fixed version — per-domain semaphore + politeness sleep:**

```python
# GOOD: per-domain control
from urllib.parse import urlparse
import asyncio, time

class DomainRateLimiter:
    def __init__(self, max_rps: int = 5) -> None:
        self._sems: dict[str, asyncio.Semaphore] = {}
        self._last_request: dict[str, float] = {}
        self._min_interval = 1.0 / max_rps  # 0.2 s between requests

    def _domain(self, url: str) -> str:
        return urlparse(url).netloc

    async def acquire(self, url: str) -> None:
        domain = self._domain(url)
        if domain not in self._sems:
            self._sems[domain] = asyncio.Semaphore(max_rps)  # concurrency cap
        sem = self._sems[domain]
        async with sem:
            now = time.monotonic()
            last = self._last_request.get(domain, 0.0)
            wait = self._min_interval - (now - last)
            if wait > 0:
                await asyncio.sleep(wait)
            self._last_request[domain] = time.monotonic()
```

Each domain gets its own `asyncio.Semaphore(5)` limiting to 5 concurrent requests. The `_min_interval` sleep ensures at least 200 ms between requests to the same domain even if all 5 semaphore slots are available simultaneously.

### 3. URL Deduplication: Redis vs In-Memory Set

An in-memory `set[str]` works for a single-process scraper but fails when:
- Multiple scraper processes run in parallel
- The process restarts mid-crawl — all dedup state is lost
- URL set grows to millions of entries consuming gigabytes of RAM

Redis `SADD` with a 24-hour TTL solves all three:

```python
# Redis key: "scraped:{job_id}"
# SADD returns 1 if new, 0 if already in set
async def is_new_url(redis: Redis, job_id: str, url: str) -> bool:
    key = f"scraped:{job_id}"
    added = await redis.sadd(key, url)
    if added:
        await redis.expire(key, 86400)  # 24-hour TTL
    return bool(added)
```

A 64-byte URL stored in Redis uses ~100 bytes. 10 million URLs = ~1 GB — acceptable for a dedicated Redis instance. For even tighter memory, use a Redis Bloom filter (10 million items, 1% FPR = ~12 MB) at the cost of no deletions.

### 4. Retry Strategy

Not all failures deserve a retry:
- **4xx (except 429)**: client error — bad URL, access denied. Do not retry.
- **429 Too Many Requests**: back off using the `Retry-After` header value.
- **5xx**: server error — retry with exponential backoff.
- **Connection errors / timeouts**: network transient — retry with backoff.

The status code determines whether `fetch_with_retry` gives up immediately, waits for the server-specified cooldown, or backs off exponentially — the same branching the code below implements:

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    attempt(["Send fetch request"])
    status{"Response<br/>outcome?"}
    ok(2xx success)
    returnHtml(["Return HTML text"])
    ret4xx(4xx except 429<br/>client error)
    permFail(["Return None<br/>permanent, no retry"])
    rl429(429<br/>too many requests)
    waitRA(Wait Retry-After<br/>header seconds)
    err5xx(5xx<br/>server error)
    connErr(Connection error<br/>or timeout)
    backoff(Exponential backoff<br/>2^attempt + jitter)
    attemptsLeft{"Attempts<br/>remaining?"}
    giveUp(["Return None<br/>attempts exhausted"])

    attempt --> status
    status --> ok
    status --> ret4xx
    status --> rl429
    status --> err5xx
    status --> connErr
    ok --> returnHtml
    ret4xx --> permFail
    rl429 --> waitRA
    err5xx --> backoff
    connErr --> backoff
    waitRA --> attemptsLeft
    backoff --> attemptsLeft
    attemptsLeft -.->|"yes"| attempt
    attemptsLeft -->|"no"| giveUp

    class attempt,returnHtml,permFail,giveUp io
    class status,attemptsLeft,waitRA,backoff mathOp
    class ok train
    class ret4xx,rl429,err5xx,connErr lossN
```

```python
import random

async def fetch_with_retry(
    session: aiohttp.ClientSession,
    url: str,
    max_attempts: int = 4,
) -> str | None:
    for attempt in range(max_attempts):
        try:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=15)) as resp:
                if resp.status == 429:
                    retry_after = int(resp.headers.get("Retry-After", "60"))
                    await asyncio.sleep(retry_after)
                    continue
                if 400 <= resp.status < 500:
                    return None  # permanent failure, don't retry
                resp.raise_for_status()
                return await resp.text()
        except (aiohttp.ClientError, asyncio.TimeoutError):
            if attempt == max_attempts - 1:
                return None
            backoff = (2 ** attempt) + random.uniform(0, 1)
            await asyncio.sleep(backoff)
    return None
```

Jitter (`random.uniform(0, 1)`) prevents thundering-herd retry storms when many workers hit the same overloaded server.

### 5. robots.txt Compliance

`urllib.robotparser.RobotFileParser` is synchronous. Wrap it with `run_in_executor` and cache per domain to avoid re-fetching on every request:

```python
import asyncio
from urllib.robotparser import RobotFileParser
from urllib.parse import urlparse

class RobotsCache:
    def __init__(self) -> None:
        self._cache: dict[str, tuple[RobotFileParser, float]] = {}
        self._ttl = 3600.0  # 1 hour

    async def is_allowed(self, url: str, user_agent: str = "*") -> bool:
        domain = urlparse(url).netloc
        now = asyncio.get_event_loop().time()
        cached = self._cache.get(domain)
        if cached is None or (now - cached[1]) > self._ttl:
            parser = await self._fetch_robots(domain)
            self._cache[domain] = (parser, now)
        else:
            parser = cached[0]
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(None, parser.can_fetch, user_agent, url)

    async def _fetch_robots(self, domain: str) -> RobotFileParser:
        robots_url = f"https://{domain}/robots.txt"
        parser = RobotFileParser(robots_url)
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, parser.read)
        return parser
```

The cache serves a hot path when an entry is under 1 hour old, and only falls to the blocking `run_in_executor` fetch on a cold or stale domain — a failed fetch degrades safely to `allow_all` rather than blocking the crawl:

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    start(["Check URL permission"])
    cacheCheck{"Cached and<br/>under 1h old?"}
    useCached(Use cached parser)
    fetchNew(Fetch robots.txt<br/>via executor)
    fetchOk{"Fetch<br/>succeeded?"}
    cacheParser(Cache parser<br/>+ timestamp)
    fallbackAllow(Fallback:<br/>allow_all = true)
    permCheck{"parser.can_fetch<br/>user_agent, url"}
    allowed(["Allowed<br/>fetch proceeds"])
    disallowed(["Disallowed<br/>skip URL"])

    start --> cacheCheck
    cacheCheck -->|"yes"| useCached
    cacheCheck -->|"no"| fetchNew
    useCached --> permCheck
    fetchNew --> fetchOk
    fetchOk -->|"yes"| cacheParser
    fetchOk -->|"no"| fallbackAllow
    cacheParser --> permCheck
    fallbackAllow --> permCheck
    permCheck -->|"allowed"| allowed
    permCheck -->|"blocked"| disallowed

    class start io
    class cacheCheck,fetchOk,permCheck mathOp
    class useCached train
    class fetchNew frozen
    class cacheParser base
    class fallbackAllow lossN
    class allowed train
    class disallowed lossN
```

---

## Implementation

```python
"""
async_scraper.py — Full async web scraper with FastAPI management API.
Python 3.11+
"""
from __future__ import annotations

import asyncio
import random
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from urllib.parse import urljoin, urlparse
from urllib.robotparser import RobotFileParser

import aiohttp
import asyncpg
from bs4 import BeautifulSoup
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from redis.asyncio import Redis

# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------

@dataclass
class ProductRecord:
    url: str
    title: str
    price: str | None
    description: str | None
    domain: str


@dataclass
class JobState:
    job_id: str
    seed_urls: list[str]
    status: str = "pending"         # pending | running | stopped | done
    fetched: int = 0
    errors: int = 0
    task: asyncio.Task | None = None


# ---------------------------------------------------------------------------
# Domain Rate Limiter
# ---------------------------------------------------------------------------

class DomainRateLimiter:
    """Per-domain semaphore + politeness delay."""

    def __init__(self, max_rps: int = 5) -> None:
        self._sems: dict[str, asyncio.Semaphore] = {}
        self._locks: dict[str, asyncio.Lock] = {}
        self._last_request: dict[str, float] = {}
        self._max_rps = max_rps
        self._min_interval = 1.0 / max_rps

    def _domain(self, url: str) -> str:
        return urlparse(url).netloc

    async def acquire(self, url: str) -> None:
        domain = self._domain(url)
        if domain not in self._sems:
            self._sems[domain] = asyncio.Semaphore(self._max_rps)
            self._locks[domain] = asyncio.Lock()
        async with self._sems[domain]:
            async with self._locks[domain]:
                now = time.monotonic()
                wait = self._min_interval - (now - self._last_request.get(domain, 0.0))
                if wait > 0:
                    await asyncio.sleep(wait)
                self._last_request[domain] = time.monotonic()


# ---------------------------------------------------------------------------
# robots.txt Cache
# ---------------------------------------------------------------------------

class RobotsCache:
    def __init__(self, ttl: float = 3600.0) -> None:
        self._cache: dict[str, tuple[RobotFileParser, float]] = {}
        self._ttl = ttl

    async def is_allowed(self, url: str, user_agent: str = "AsyncScraper/1.0") -> bool:
        domain = urlparse(url).netloc
        loop = asyncio.get_event_loop()
        now = loop.time()
        cached = self._cache.get(domain)
        if cached is None or (now - cached[1]) > self._ttl:
            parser = await self._load(domain, loop)
            self._cache[domain] = (parser, now)
        else:
            parser = cached[0]
        return await loop.run_in_executor(None, parser.can_fetch, user_agent, url)

    async def _load(self, domain: str, loop: asyncio.AbstractEventLoop) -> RobotFileParser:
        robots_url = f"https://{domain}/robots.txt"
        parser = RobotFileParser(robots_url)
        try:
            await loop.run_in_executor(None, parser.read)
        except Exception:
            parser.allow_all = True  # if robots.txt unreachable, allow all
        return parser


# ---------------------------------------------------------------------------
# Fetch with retry
# ---------------------------------------------------------------------------

async def fetch_page(
    session: aiohttp.ClientSession,
    url: str,
    max_attempts: int = 4,
) -> str | None:
    for attempt in range(max_attempts):
        try:
            async with session.get(
                url, timeout=aiohttp.ClientTimeout(total=15)
            ) as resp:
                if resp.status == 429:
                    retry_after = int(resp.headers.get("Retry-After", "60"))
                    await asyncio.sleep(retry_after)
                    continue
                if 400 <= resp.status < 500:
                    return None
                resp.raise_for_status()
                return await resp.text()
        except (aiohttp.ClientError, asyncio.TimeoutError):
            if attempt == max_attempts - 1:
                return None
            backoff = (2 ** attempt) + random.uniform(0, 1)
            await asyncio.sleep(backoff)
    return None


# ---------------------------------------------------------------------------
# HTML parser
# ---------------------------------------------------------------------------

def parse_product(html: str, url: str) -> ProductRecord:
    soup = BeautifulSoup(html, "lxml")
    title = soup.find("h1")
    price_tag = soup.find(class_=lambda c: c and "price" in c.lower() if c else False)
    description_tag = soup.find("meta", attrs={"name": "description"})
    return ProductRecord(
        url=url,
        title=title.get_text(strip=True) if title else "",
        price=price_tag.get_text(strip=True) if price_tag else None,
        description=description_tag.get("content") if description_tag else None,
        domain=urlparse(url).netloc,
    )


def extract_product_links(html: str, base_url: str) -> list[str]:
    soup = BeautifulSoup(html, "lxml")
    links: list[str] = []
    for a in soup.find_all("a", href=True):
        href = urljoin(base_url, a["href"])
        if "/product" in href or "/item" in href or "/p/" in href:
            links.append(href)
    return links


# ---------------------------------------------------------------------------
# DB writer
# ---------------------------------------------------------------------------

async def write_product(pool: asyncpg.Pool, product: ProductRecord) -> None:
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO products (url, domain, title, price, description, scraped_at)
            VALUES ($1, $2, $3, $4, $5, NOW())
            ON CONFLICT (url) DO UPDATE
                SET price = EXCLUDED.price,
                    scraped_at = EXCLUDED.scraped_at
            """,
            product.url,
            product.domain,
            product.title,
            product.price,
            product.description,
        )


# ---------------------------------------------------------------------------
# Worker
# ---------------------------------------------------------------------------

async def worker(
    worker_id: int,
    queue: asyncio.Queue[str],
    session: aiohttp.ClientSession,
    rate_limiter: DomainRateLimiter,
    robots: RobotsCache,
    redis_client: Redis,
    db_pool: asyncpg.Pool,
    job_state: JobState,
) -> None:
    while True:
        url = await queue.get()
        try:
            # Dedup check
            key = f"scraped:{job_state.job_id}"
            added = await redis_client.sadd(key, url)
            if not added:
                continue
            await redis_client.expire(key, 86400)

            # robots.txt check
            if not await robots.is_allowed(url):
                continue

            # Rate-limited fetch
            await rate_limiter.acquire(url)
            html = await fetch_page(session, url)
            if html is None:
                job_state.errors += 1
                continue

            # Parse in executor to avoid blocking event loop on large pages
            loop = asyncio.get_event_loop()
            product = await loop.run_in_executor(None, parse_product, html, url)
            new_links = await loop.run_in_executor(None, extract_product_links, html, url)

            # Enqueue discovered links
            for link in new_links:
                if queue.qsize() < 100_000:  # bounded frontier
                    await queue.put(link)

            # Persist
            await write_product(db_pool, product)
            job_state.fetched += 1

        except Exception:
            job_state.errors += 1
        finally:
            queue.task_done()


# ---------------------------------------------------------------------------
# Job runner
# ---------------------------------------------------------------------------

async def run_job(
    job_state: JobState,
    session: aiohttp.ClientSession,
    rate_limiter: DomainRateLimiter,
    robots: RobotsCache,
    redis_client: Redis,
    db_pool: asyncpg.Pool,
    num_workers: int = 50,
) -> None:
    queue: asyncio.Queue[str] = asyncio.Queue(maxsize=200_000)
    for url in job_state.seed_urls:
        await queue.put(url)

    job_state.status = "running"
    tasks = [
        asyncio.create_task(
            worker(i, queue, session, rate_limiter, robots, redis_client, db_pool, job_state)
        )
        for i in range(num_workers)
    ]

    await queue.join()
    for t in tasks:
        t.cancel()
    job_state.status = "done"


# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------

_jobs: dict[str, JobState] = {}
_session: aiohttp.ClientSession | None = None
_db_pool: asyncpg.Pool | None = None
_redis: Redis | None = None
_rate_limiter = DomainRateLimiter(max_rps=5)
_robots = RobotsCache()


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _session, _db_pool, _redis
    _session = aiohttp.ClientSession(
        headers={"User-Agent": "AsyncScraper/1.0"},
        connector=aiohttp.TCPConnector(limit=100),
    )
    _db_pool = await asyncpg.create_pool(
        "postgresql://user:pass@localhost/scraper", min_size=5, max_size=20
    )
    _redis = Redis(host="localhost", port=6379, decode_responses=True)
    yield
    await _session.close()
    await _db_pool.close()
    await _redis.aclose()


app = FastAPI(lifespan=lifespan)


class JobRequest(BaseModel):
    job_id: str
    seed_urls: list[str]
    workers: int = 50


class JobStatus(BaseModel):
    job_id: str
    status: str
    fetched: int
    errors: int


@app.post("/jobs", status_code=202)
async def submit_job(req: JobRequest) -> JobStatus:
    if req.job_id in _jobs:
        raise HTTPException(status_code=409, detail="Job ID already exists")
    state = JobState(job_id=req.job_id, seed_urls=req.seed_urls)
    _jobs[req.job_id] = state
    state.task = asyncio.create_task(
        run_job(state, _session, _rate_limiter, _robots, _redis, _db_pool, req.workers)
    )
    return JobStatus(job_id=state.job_id, status=state.status, fetched=0, errors=0)


@app.get("/jobs/{job_id}")
async def get_job(job_id: str) -> JobStatus:
    state = _jobs.get(job_id)
    if state is None:
        raise HTTPException(status_code=404, detail="Job not found")
    return JobStatus(
        job_id=state.job_id,
        status=state.status,
        fetched=state.fetched,
        errors=state.errors,
    )


@app.delete("/jobs/{job_id}", status_code=204)
async def stop_job(job_id: str) -> None:
    state = _jobs.get(job_id)
    if state is None:
        raise HTTPException(status_code=404, detail="Job not found")
    if state.task and not state.task.done():
        state.task.cancel()
        state.status = "stopped"
```

**Database schema:**

```sql
CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    url         TEXT        NOT NULL UNIQUE,
    domain      TEXT        NOT NULL,
    title       TEXT,
    price       TEXT,
    description TEXT,
    scraped_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_domain ON products (domain);
CREATE INDEX idx_products_scraped_at ON products (scraped_at);
```

---

## Python/FastAPI Components Used

| Component | Role in this system |
|---|---|
| `asyncio.Queue` | Bounded URL frontier; `maxsize=200_000` prevents unbounded memory growth; `queue.join()` blocks until all URLs processed |
| `asyncio.Semaphore` | Per-domain concurrency cap (max 5 simultaneous connections to one host) |
| `asyncio.create_task` | Spawns 50 worker coroutines concurrently; each task runs the full fetch-parse-store pipeline |
| `asyncio.get_event_loop().run_in_executor` | Offloads CPU-bound HTML parsing (BeautifulSoup) and blocking `robotparser.read()` to thread pool |
| `aiohttp.ClientSession` | Shared async HTTP client with connection pooling (`TCPConnector(limit=100)`); reused across all workers |
| `aiohttp.ClientTimeout` | 15-second total timeout per request; prevents workers hanging on slow servers |
| `asyncpg` | Async PostgreSQL driver; connection pool (`min_size=5, max_size=20`); `UPSERT` with `ON CONFLICT` |
| `redis.asyncio.Redis` | Async Redis client for distributed URL deduplication via `SADD` + `EXPIRE` |
| `FastAPI` + `lifespan` | Management API; `lifespan` context manager initialises and tears down shared resources (aiohttp session, asyncpg pool, Redis) cleanly |
| `Pydantic BaseModel` | Request/response validation for `JobRequest` and `JobStatus` |
| `urllib.robotparser` | Standard-library robots.txt parser; wrapped with `run_in_executor` because its `read()` method is synchronous |

---

## Tradeoffs and Alternatives

### asyncio vs Scrapy vs multiprocessing

| Dimension | asyncio + aiohttp | Scrapy | multiprocessing |
|---|---|---|---|
| Concurrency model | Single-thread cooperative | Twisted async reactor | Multiple processes, true parallelism |
| I/O throughput | Excellent — thousands of connections | Excellent — production-proven | Good, but IPC overhead |
| CPU-bound parsing | Needs `run_in_executor` | Needs process pipeline | Native — each process has own GIL |
| Learning curve | Medium — asyncio patterns | High — Scrapy middleware chain | Low for Python devs |
| Existing middleware | None built-in | Extensive (dupefilter, httpcache, autothrottle) | None |
| Best for | Custom pipelines, full control | Production crawlers, team knowledge | ML preprocessing, heavy extraction |

**Choose asyncio** when you need tight integration with an async FastAPI service and custom business logic. **Choose Scrapy** when you need Scrapy's auto-throttle, built-in middleware ecosystem, and a team already familiar with it.

### Redis vs In-Memory Set for URL Deduplication

| Dimension | Redis SADD | In-memory Python set |
|---|---|---|
| Multi-process safe | Yes | No — each process has its own set |
| Survives restart | Yes (with persistence) | No |
| Memory (10M URLs) | ~1 GB | ~800 MB (Python set overhead higher) |
| Latency per check | ~0.1 ms (local Redis) | ~50 ns |
| Operational cost | Requires Redis infra | None |

For a single-process scraper that restarts cleanly, an in-memory set is simpler. For production with multiple workers or restarts, Redis is required.

**Redis Bloom Filter alternative:** `BF.ADD` uses ~12 MB for 10 million URLs at 1% false positive rate. A false positive means a URL is skipped that wasn't actually scraped — acceptable for best-effort dedup. Cannot delete entries, cannot iterate.

### Push vs Pull Crawl Architecture

**Pull (current design):** Workers pull URLs from a central `asyncio.Queue`. Simple, low latency, natural backpressure. Works well for a single-machine deployment.

**Push (distributed):** A central URL scheduler pushes batches of URLs to worker queues (Kafka topics or Redis streams). Enables horizontal scale-out to multiple machines but adds coordination complexity (partition assignment, offset tracking, rebalancing).

For 10,000 pages/hour = 2.8 pages/second, a single machine with 50 async workers handles this easily. Distributed architecture adds engineering cost without benefit at this scale.

---

## Interview Discussion Points

**Q: What makes asyncio more efficient than threading for this use case?**
asyncio uses a single thread and cooperative multitasking: when a coroutine awaits a network response, the event loop runs another coroutine. This avoids thread context-switch overhead (~1-5 µs) and the GIL contention that limits Python threads. 50 async workers consume ~10 MB total vs ~400 MB for 50 OS threads. The tradeoff: one blocking call anywhere stalls all workers, so CPU-bound operations must go through `run_in_executor`.

**Q: How do you prevent overwhelming a single domain with concurrent requests?**
Two mechanisms in combination: a per-domain `asyncio.Semaphore(5)` caps concurrent open connections to that domain to 5, and a politeness delay of `sleep(1/rate)` ensures at least 200 ms between consecutive requests even when 5 connections are available simultaneously. The semaphore prevents connection storms; the sleep provides steady cadence.

**Q: How does the URL frontier handle backpressure when pages are discovered faster than they are processed?**
The `asyncio.Queue(maxsize=200_000)` provides bounded capacity. When the queue is full, `queue.put()` blocks the producer coroutine, creating natural backpressure. Workers calling `queue.task_done()` signal completion, allowing blocked puts to resume. If 200,000 URLs is insufficient for a deep crawl, the frontier can be backed by Redis lists (`LPUSH`/`BRPOP`) which provide persistent, unbounded storage at the cost of Redis round-trips per URL.

**Q: What happens when a database write fails mid-crawl?**
The current implementation catches all exceptions in the worker loop, increments the error counter, and continues. A more robust approach wraps the `write_product` call in a retry with exponential backoff for `asyncpg.TooManyConnectionsError` or transient network errors. For idempotency, the schema uses `ON CONFLICT (url) DO UPDATE`, so replaying a URL after a partial write is safe.

**Q: How would you scale this system to 10x throughput (100,000 pages/hour)?**
100,000 pages/hour = 28 pages/second. Options: (1) increase `num_workers` from 50 to 200 — asyncio handles this with minimal overhead; (2) shard by domain across multiple scraper processes using Kafka topic partitioning; (3) add a second Redis for dedup to reduce round-trip latency. The PostgreSQL write path becomes the bottleneck at ~28 inserts/second — use `asyncpg` batch inserts or a write buffer that flushes every 100 records or 1 second.

**Q: How do you handle sites that require JavaScript rendering?**
aiohttp fetches raw HTML and cannot execute JavaScript. For JS-heavy sites, use Playwright's async API (`playwright.async_api`) with a pool of browser contexts. Each browser context costs ~50 MB RAM vs ~1 MB for an HTTP connection, so limit JS-rendered workers to a smaller pool (5-10) and route by domain. Alternatively, look for the site's internal API that the JavaScript calls and scrape that directly.

**Q: How does the robots.txt cache avoid stale data?**
The `RobotsCache` stores a `(parser, timestamp)` tuple per domain and re-fetches when the cached entry is older than 1 hour (`_ttl = 3600`). If the `robots.txt` fetch fails (site unreachable), the parser is set to `allow_all = True` as a safe fallback, matching the robots.txt spec's guidance that crawlers should not retry indefinitely on failure. A 1-hour TTL balances freshness against the overhead of fetching robots.txt on every page request.

**Q: Why use `ON CONFLICT DO UPDATE` instead of checking existence before insert?**
A check-then-insert sequence has a TOCTOU race: two workers could both check, both find the URL absent, and both attempt to insert, causing a constraint violation. `ON CONFLICT DO UPDATE` (an UPSERT) is atomic at the database level — the second writer's insert becomes an update. It also handles the case where a re-crawl run wants to refresh prices without needing explicit logic to distinguish new vs existing rows.

**Q: How would you add politeness delays that respect the `Crawl-Delay` directive in robots.txt?**
`RobotFileParser` exposes `crawler_delay(user_agent)` which returns the `Crawl-Delay` value in seconds if set. The `DomainRateLimiter` can be extended to read this value when initialising a domain's semaphore: if `robots.crawler_delay("AsyncScraper/1.0")` returns 2, set `_min_interval = 2.0` for that domain rather than the default `1/max_rps`. This integrates robots.txt compliance directly into the rate-limiting layer.

**Q: How do you monitor crawl health in production?**
The `JobState` dataclass tracks `fetched` and `errors` counts exposed via `GET /jobs/{job_id}`. For production monitoring: (1) expose a Prometheus `/metrics` endpoint with `scraped_total`, `errors_total`, `queue_depth`, and `active_workers` gauges using `prometheus-fastapi-instrumentator`; (2) set an alert if `errors/fetched > 0.1` (10% error rate) over a 5-minute window; (3) track p95 fetch latency with a histogram to detect slow domains early.
