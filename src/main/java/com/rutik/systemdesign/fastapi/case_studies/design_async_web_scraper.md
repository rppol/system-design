# Design an Async Web Scraper

## Intuition

A web crawler is a warehouse loading dock, not a phone call. A phone call is what `requests.get(url)` is: dial, wait on the line doing nothing else, hang up, dial the next number. A loading dock runs many trucks (URLs) through many bays (workers) at once, but every dock still has a rule painted on the floor: **one truck per bay, and no more than five bays per supplier per minute** — because the supplier's own receiving yard (the target site's server) will shut its gate on you if you show up in a convoy. The frontier is the queue of trucks waiting to unload; the per-domain politeness gate is the floor manager who refuses to let one slow, chatty supplier occupy every bay while three other suppliers' trucks sit idle outside. And the FastAPI control plane is the dispatcher's office at the front of the building: it takes orders, reports how many trucks have unloaded, and never once touches a pallet itself. Confusing the dispatcher's office with the loading dock — writing a scraper as "a FastAPI endpoint that fetches a page" — is the single most common design mistake in this problem, because an HTTP request handler that blocks on ten seconds of crawling ties up the request-response cycle the API exists to serve.

---

## 1. Requirements Clarification

**Functional requirements:**
- Accept a crawl job via API: a set of seed URLs, a target domain allowlist, and a depth or page-count budget.
- Fetch pages, respecting each site's `robots.txt` (including `Crawl-delay`), and extract structured product data (title, price, description, image URLs).
- Discover and follow same-site links up to the job's budget; do not wander to arbitrary external domains unless explicitly allowed.
- Deduplicate URLs so a page already scraped within a rolling window is not re-fetched.
- Persist extracted records to a durable store, upserting on re-crawl.
- Expose job status (queued / running / done / stopped, pages fetched, errors) and let an operator stop a running job.
- Stream results as they land, rather than forcing a client to wait for the whole job and then download one giant array.

**Non-functional requirements:**
- Throughput: sustain 10,000 pages/hour baseline, with a clear scaling story to 100,000 pages/hour.
- Politeness: never exceed a configurable per-domain request rate, and back off automatically on `429`/`503`.
- Resilience: a process restart must not lose more than a few minutes of frontier state, and a single slow or hostile domain must not degrade the other domains being crawled.
- Legal/ethical boundary: only crawl what `robots.txt` and the target's terms of service permit; identify the crawler with a real `User-Agent` and, ideally, published IP ranges — see §6 for how the industry's real crawlers do this.

**Clarifying questions worth asking out loud in an interview, and the answers this design assumes:**

**Q: Is this a broad crawl (discover the whole web) or a focused scrape (known domains, known page shapes)?**
Focused. The seed set is 5-10 known e-commerce domains with a recognizable product-page URL pattern, not open-ended discovery. This matters because a broad crawl needs a much bigger frontier, cross-domain politeness bookkeeping, and content-type sniffing that a focused scraper can skip.

**Q: Does any target site require JavaScript to render product data?**
Assume not, for the base design — `httpx`/`aiohttp` fetch raw HTML. §4 and §11 cover the Playwright fallback for the sites that do, because it changes the cost model by roughly 50x per page.

**Q: What does "done" mean for a job — a fixed page budget, a fixed URL set, or "crawl until the frontier is empty"?**
A fixed page budget per job, enforced by the frontier (§4), because "until empty" is unbounded on any site with calendars, faceted search, or session-id links — the crawler-trap failure mode covered in §9.

---

## 2. Scale Estimation

**Throughput, base case.**
10,000 pages/hour ÷ 3,600 s/hour = **2.78 pages/second** sustained.

**Throughput, 10x scale case** (used again in §10 Capacity Planning):
100,000 pages/hour ÷ 3,600 s/hour = **27.8 pages/second** sustained.

**Concurrency via Little's Law.**
Little's Law: `concurrency (L) = throughput (λ) × latency (W)`. Take the full worker-loop latency per URL — dedup check, robots lookup (cache hit), politeness wait, fetch, parse, DB write — as roughly 0.5-2 s depending on domain and cache state; call it 1 s as a working average.

- Base case: `L = 2.78 × 1 s ≈ 3` requests need to be in flight at any instant to sustain throughput.
- 10x case: `L = 27.8 × 1 s ≈ 28` requests in flight.

That is the theoretical *minimum*. The design provisions 50 workers for the base case and 200 for the 10x case — 15-20x the Little's Law minimum — because the minimum assumes perfectly smooth latency, and real crawls do not get that: a worker blocked on a 2 s politeness sleep for a slow domain, a worker retrying a 5xx with backoff, and a worker waiting on a cold robots.txt fetch are all "in flight" but not making forward progress. The excess capacity is insurance against variance, not against undersizing the average case.

**Bandwidth.**
Average page size 200 KB. Base case: 2.78 pages/s × 200 KB = 556 KB/s ≈ **4.4 Mbps sustained**, or 10,000 × 200 KB = **2 GB/hour** of raw HTML fetched. At the 10x scale: 44 Mbps sustained, 20 GB/hour.

**Storage per month.**
Extracted records (title, price, description, URL — no raw HTML) run roughly 2 KB each once stored as rows plus indexes. At 10,000 pages/hour × 24 h × 30 d = 7.2M pages/month: 7.2M × 2 KB ≈ **14.4 GB/month** of structured data — trivial for PostgreSQL. If raw HTML is also retained for reprocessing (common when the extraction logic is still being tuned): 7.2M × 200 KB ≈ **1.44 TB/month** of raw storage, which is the number that actually drives the storage line in §10.

**Frontier queue memory.**
A Python `str` URL of ~80 characters costs roughly 100-150 bytes of heap once object overhead is counted, plus whatever container the queue uses. At the design's bounded `asyncio.Queue(maxsize=200_000)`: 200,000 × 150 bytes ≈ **30 MB** — negligible. The number that matters is what happens when the frontier is *not* bounded: a crawler trap (see §9) can push the live URL count into the millions within minutes, and at 10M URLs × 150 bytes ≈ **1.5 GB** just for the queue's contents, before counting the Python object overhead of the `set` used for in-flight tracking. This is the arithmetic argument for a hard `maxsize`, not just a style preference.

**Dedup set memory — Bloom filter bits-per-element.**
The standard formula for a Bloom filter's optimal bit array size is `m = -(n × ln(p)) / (ln 2)²`, where `n` is the number of elements and `p` the target false-positive rate.

| Target FPR (p) | Bits per element | 10M URLs → total size |
|---|---|---|
| 1% | ≈ 9.6 bits | ≈ 96,000,000 bits ≈ **12 MB** |
| 0.1% | ≈ 14.4 bits | ≈ 144,000,000 bits ≈ **18 MB** |

Going from a 1% to a 0.1% false-positive rate costs 50% more memory for a 10x lower FP rate — a good trade at this scale. What a false positive actually *costs*, though, is not "a wasted dedup check" — it is a page that the Bloom filter incorrectly reports as "already seen," so it is silently never crawled, with no error, no log line, and no way to distinguish it from a page that was genuinely re-visited. At a 1% FPR and a 10M-URL frontier, roughly 100,000 pages would be silently dropped this way over the crawl's lifetime — small in proportion, but invisible, which is exactly the kind of defect that a strict exact `set` (or Redis `SADD`, §5) trades memory to avoid entirely.

**Parse CPU.**
HTML parsing (BeautifulSoup4 over `lxml`) of an average 200 KB page is CPU work, not I/O — order tens of milliseconds per page is typical for a page that size. At the base throughput of 2.78 pages/s, that is under 0.1 CPU-core continuously; at the 10x rate of 27.8 pages/s, still comfortably under 1 core. This is why one thread-pool executor (§4) is enough at either scale — the constraint on this system is never CPU, it is the per-domain politeness ceiling, which §10 quantifies directly.

---

## 3. High-Level Architecture

The first architectural decision is the one easiest to get wrong: **the FastAPI process is a control plane, not a worker.** A path operation that calls `await scrape_page(url)` and returns the result ties up a request-response cycle for as long as the crawl takes, defeats connection pooling and politeness bookkeeping (each request would need its own `aiohttp.ClientSession`), and gives an operator no way to ask "how far along is this job?" while it runs. The control plane's only job is to accept a job, hand it to a long-running `asyncio.Task`, and let clients poll or stream progress. The worker fleet's only job is to drain a queue.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef proc    fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef data    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a
    classDef warn    fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold

    client(["Client<br/>submits job"])

    subgraph control ["Control Plane · FastAPI process"]
        submit(["POST /jobs"])
        status(["GET /jobs/id"])
        stream(["GET /jobs/id/results<br/>JSON Lines"])
    end

    subgraph fleet ["Worker Fleet · asyncio tasks, no HTTP involved"]
        frontier(URL Frontier<br/>priority queue)
        gate(Per-host<br/>politeness gate)
        fetch(Fetcher)
        parse(Parser<br/>thread pool)
    end

    store(PostgreSQL<br/>+ Redis dedup)

    client --> submit --> frontier
    frontier --> gate --> fetch --> parse --> store
    store --> stream --> client
    client --> status --> store

    class client,submit,status,stream io
    class frontier,gate,fetch,parse proc
    class store data
```

A URL's life inside the fleet moves through a small number of states, and the state that matters most for correctness is what happens on failure — a retryable error goes back to the frontier with backoff, not straight to "dropped":

```mermaid
stateDiagram-v2
    [*] --> Discovered
    Discovered --> Frontier: canonicalize, dedup check
    Frontier --> Fetching: worker dequeues, politeness slot held
    Fetching --> Parsed: 2xx response
    Fetching --> Failed: 4xx, 5xx, timeout, 429
    Failed --> Frontier: retryable, backoff elapsed
    Failed --> Dropped: retries exhausted or permanent 4xx
    Parsed --> Stored: upsert to PostgreSQL
    Parsed --> Frontier: new links, canonicalized, deduped
    Stored --> [*]
    Dropped --> [*]
```

**Data flow, base design (kept from the original architecture, still accurate):**
1. FastAPI receives a crawl job request with seed URLs.
2. The job manager enqueues seed URLs into the bounded `asyncio.Queue` frontier.
3. N consumer workers pull URLs from the queue concurrently.
4. Each worker checks Redis for deduplication, consults the cached `robots.txt`, acquires a per-domain politeness slot, fetches, parses off the event loop, and writes the product record to PostgreSQL.
5. Newly discovered product URLs are canonicalized and pushed back onto the frontier if not already seen.
6. Progress metrics live in memory and are exposed via FastAPI — polled with `GET` or streamed with JSON Lines / SSE (§4).

The component names in that list — job manager, frontier, rate limiter, robots checker, session, retry, parser, dedup, writer — map directly onto the code in §4 and the technology table in §7; the two diagrams above already show how they connect, so a third, lower-level box-and-arrow diagram of the same pipeline would repeat rather than add information.

---

## 4. Component Deep Dives

### 4.1 The mistake this design deliberately avoids: unbounded `asyncio.gather`

The most common first draft of an async scraper looks like this, and it works fine in a demo with 50 URLs:

```python
# BROKEN: fires every request at once, holds every response in memory
# simultaneously, and only starts parsing/storing after the LAST one
# of 100,000 requests finishes -- if it finishes at all.
import asyncio
import aiohttp

async def scrape_all_broken(urls: list[str]) -> list[str]:
    async with aiohttp.ClientSession() as session:
        async def fetch(url: str) -> str:
            async with session.get(url) as resp:
                return await resp.text()
        # 100,000 coroutines are created immediately; asyncio.gather
        # schedules all of them and waits for every one before returning.
        return await asyncio.gather(*[fetch(u) for u in urls])
```

For 100,000 URLs, `asyncio.gather` builds 100,000 coroutine objects and, as each completes, holds its full response body in memory — none of them can be garbage-collected until `gather` itself returns, because the return value is the list of every result. At a 200 KB average page size that is **20 GB of response bodies alone**, on top of the connection and coroutine-frame overhead for 100,000 concurrently-scheduled tasks, and none of it is written to the database until the slowest single request among the 100,000 finishes — so a single hung connection stalls the entire batch indefinitely. §9 quantifies exactly what this did in practice.

```python
# FIX: bounded worker pool draining a queue. Memory is bounded by
# (num_workers × avg_response_size), not by len(urls). Results are
# written to PostgreSQL as each page completes, not after the last one.
async def scrape_all_fixed(
    urls: list[str],
    session: aiohttp.ClientSession,
    num_workers: int = 50,
) -> None:
    queue: asyncio.Queue[str] = asyncio.Queue()
    for url in urls:
        queue.put_nowait(url)

    async def worker() -> None:
        while not queue.empty():
            url = await queue.get()
            try:
                async with session.get(url) as resp:
                    html = await resp.text()
                await write_product(db_pool, parse_product(html, url))  # streamed to DB immediately
            finally:
                queue.task_done()

    workers = [asyncio.create_task(worker()) for _ in range(num_workers)]
    await asyncio.gather(*workers)
```

With 50 workers, at most 50 responses are ever in memory at once — 50 × 200 KB ≈ 10 MB, regardless of whether the URL list has 100 entries or 100,000. This is exactly the queue-and-worker-pool shape used throughout the rest of this design (§4.4-§4.6); the point of walking through the broken version here is that it is the natural first draft, not an implausible strawman, and the fix is a change in *shape* (pull from a bounded queue) rather than a tuning knob.

### 4.2 Domain rate limiting: why a global semaphore is the wrong primitive

**Broken version — one semaphore for every domain:**

```python
# BAD: one semaphore for all domains -- example.com blocks shopify.com
_global_sem = asyncio.Semaphore(10)

async def fetch(url: str) -> str:
    async with _global_sem:
        async with session.get(url) as resp:
            return await resp.text()
```

A single global semaphore treats "10 requests in flight" as one shared budget across every domain, which means one slow-responding domain (a 2 s average latency site) can occupy most of the ten slots while a fast domain (200 ms average) sits queued behind it, even though the fast domain's own rate limit has plenty of headroom. This is a starvation bug, not a throughput bug — the fast domain is not being rate-limited *by its own rules*, it is being rate-limited by an unrelated domain's slowness.

**Fixed version — per-domain semaphore, held for the whole request:**

```python
from contextlib import asynccontextmanager
from urllib.parse import urlparse
import asyncio, time

class DomainRateLimiter:
    def __init__(self, max_rps: int = 5) -> None:
        self._sems: dict[str, asyncio.Semaphore] = {}
        self._locks: dict[str, asyncio.Lock] = {}
        self._last_request: dict[str, float] = {}
        self._max_rps = max_rps
        self._min_interval = 1.0 / max_rps  # 0.2 s between requests

    def _domain(self, url: str) -> str:
        return urlparse(url).netloc

    @asynccontextmanager
    async def slot(self, url: str):
        """Hold a domain slot for the WHOLE fetch, not just the pacing sleep."""
        domain = self._domain(url)
        if domain not in self._sems:
            self._sems[domain] = asyncio.Semaphore(self._max_rps)
            self._locks[domain] = asyncio.Lock()
        async with self._sems[domain]:            # concurrency cap
            # The pacing decision must be serialised. Without the lock, every
            # coroutine inside the semaphore reads the same _last_request,
            # computes the same wait, and they all fire in the same instant.
            async with self._locks[domain]:
                wait = self._min_interval - (time.monotonic() - self._last_request.get(domain, 0.0))
                if wait > 0:
                    await asyncio.sleep(wait)
                self._last_request[domain] = time.monotonic()
            yield                                  # semaphore still held here
```

Two separate controls, and they are separate on purpose. The `asyncio.Semaphore(5)` caps how many requests are *open at once* against one host; the `_min_interval` sleep caps how *often* a new one starts, at one per 200 ms.

The reason `slot()` is a context manager rather than an `acquire()` call is the whole point. If the semaphore is released as soon as the pacing sleep finishes — which is what an `async with sem:` block that returns before the fetch does — it caps nothing at all, because the fetch it is supposed to be limiting has not started yet. Concurrency then settles at `fetch_latency / _min_interval`: at this system's own upper-bound fetch latency of 2 s and 200 ms pacing, that is **10** simultaneous connections to a host advertised as capped at 5. Hold the slot across the fetch and the cap is real.

### 4.3 URL canonicalization and crawler-trap defense

Two different pages that render identically to a browser can have URLs that a naive dedup check treats as distinct: tracking parameters (`?sid=abc123`), trailing slashes, and case differences all multiply one real page into many frontier entries. Canonicalization runs *before* the dedup check, not after:

```python
from urllib.parse import urlsplit, urlunsplit, parse_qsl, urlencode

_TRACKING_PARAMS = {"sid", "sessionid", "utm_source", "utm_medium", "utm_campaign", "ref"}

def canonicalize(url: str) -> str:
    parts = urlsplit(url)
    # Strip tracking/session params; keep the rest, sorted for determinism
    # so that ?a=1&b=2 and ?b=2&a=1 canonicalize to the same string.
    kept = sorted(
        (k, v) for k, v in parse_qsl(parts.query) if k.lower() not in _TRACKING_PARAMS
    )
    path = parts.path.rstrip("/") or "/"
    return urlunsplit((parts.scheme, parts.netloc.lower(), path, urlencode(kept), ""))
```

Canonicalization alone does not stop a crawler trap — a calendar's "next month" link, or an infinite-redirect loop, generates *genuinely distinct* canonical URLs forever. The defense is a hard budget enforced by the frontier itself, independent of how well-behaved any single page is:

```python
@dataclass
class CrawlBudget:
    max_pages: int
    max_depth: int
    pages_seen: int = 0

    def admit(self, depth: int) -> bool:
        if self.pages_seen >= self.max_pages or depth > self.max_depth:
            return False
        self.pages_seen += 1
        return True
```

Every link discovered during parsing carries its depth (seed URLs are depth 0); the worker calls `budget.admit(depth)` before enqueueing, and a domain that keeps generating new URLs simply stops being followed once the job's budget is spent — it never gets the chance to consume the whole frontier the way it does without a cap (quantified in §9).

### 4.4 Retry strategy

Not all failures deserve a retry:
- **4xx (except 429)**: client error — bad URL, access denied. Do not retry.
- **429 Too Many Requests**: back off using the `Retry-After` header value.
- **5xx**: server error — retry with exponential backoff.
- **Connection errors / timeouts**: network transient — retry with backoff.

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

Jitter (`random.uniform(0, 1)`) prevents thundering-herd retry storms when many workers hit the same overloaded server. This full retry/backoff/jitter pattern — including how it composes with connection pooling and circuit-breaking against a flaky upstream — is covered in general in [HTTP Clients and External APIs](../http_clients_and_external_apis/http_clients_and_external_apis.md); it is not re-derived here beyond the scraper-specific `Retry-After` handling.

### 4.5 robots.txt compliance

`urllib.robotparser.RobotFileParser` is synchronous. Wrap it with `run_in_executor` and cache per domain to avoid re-fetching on every request:

```python
import asyncio
from urllib.robotparser import RobotFileParser
from urllib.parse import urlparse

class RobotsCache:
    def __init__(self, ttl: float = 3600.0) -> None:
        self._cache: dict[str, tuple[RobotFileParser, float]] = {}
        self._ttl = ttl

    async def is_allowed(self, url: str, user_agent: str = "AsyncScraper/1.0") -> bool:
        domain = urlparse(url).netloc
        loop = asyncio.get_running_loop()
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
            # RFC 9309 splits the failure classes, and so must we.
            # read() swallows every HTTPError itself: 4xx "unavailable" sets
            # allow_all, 401/403 sets disallow_all, and 5xx sets neither and
            # never parses -- which leaves last_checked at 0, so can_fetch
            # returns False. All three already match the RFC.
            # Reaching HERE therefore means a network-level URLError (DNS,
            # refused connection, TLS), i.e. RFC 9309 s2.3.1.4 "unreachable",
            # for which a crawler MUST assume complete disallow. Defaulting
            # to allow_all is the exact opposite of the spec and hammers a
            # host that is already failing.
            parser.disallow_all = True
        return parser

    async def crawl_delay(self, url: str, user_agent: str = "AsyncScraper/1.0") -> float | None:
        """Site-stated Crawl-delay in seconds, or None if unset."""
        domain = urlparse(url).netloc
        cached = self._cache.get(domain)
        if cached is None:
            return None
        return cached[0].crawl_delay(user_agent)
```

The cache serves a hot path when an entry is under 1 hour old, and only falls to the blocking `run_in_executor` fetch on a cold or stale domain — an unreachable host degrades to `disallow_all`, the conservative side, rather than crawling on regardless:

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
    fallbackAllow(Unreachable:<br/>disallow_all = true)
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

### 4.6 Frontier checkpointing

The base design's `asyncio.Queue` lives entirely in process memory — a restart, deploy, or crash loses every URL still queued, and a multi-hour crawl has no way to resume from where it left off. Checkpointing persists the frontier's contents (not the whole process state) to Redis on an interval, and a job that starts up checks for a checkpoint before falling back to its seed URLs:

```python
import json

async def checkpoint_frontier(redis_client: Redis, job_id: str, queue: asyncio.Queue[str]) -> None:
    """Snapshot pending URLs without draining the live queue workers are reading from."""
    pending = list(queue._queue)  # asyncio.Queue's internal deque, read-only snapshot
    await redis_client.set(f"checkpoint:{job_id}", json.dumps(pending), ex=86400)

async def restore_frontier(redis_client: Redis, job_id: str) -> list[str] | None:
    raw = await redis_client.get(f"checkpoint:{job_id}")
    return json.loads(raw) if raw else None
```

A background task calls `checkpoint_frontier` every 30-60 seconds while a job runs; `run_job` (§4.7) calls `restore_frontier` before falling back to `job_state.seed_urls`. This bounds data loss on an unclean restart to one checkpoint interval instead of the whole job — the difference between losing 60 seconds of progress and losing the 3.5 hours quantified in §9.

### 4.7 Worker and job runner

```python
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
            # Dedup check (canonicalize first -- see 4.3)
            key = f"scraped:{job_state.job_id}"
            added = await redis_client.sadd(key, canonicalize(url))
            if not added:
                continue
            await redis_client.expire(key, 86400)

            # robots.txt check
            if not await robots.is_allowed(url):
                continue

            # Rate-limited fetch — the slot is held for the whole request, so
            # the per-domain concurrency cap actually caps something.
            async with rate_limiter.slot(url):
                html = await fetch_with_retry(session, url)
            if html is None:
                job_state.errors += 1
                continue

            # Parse in executor to avoid blocking event loop on large pages
            loop = asyncio.get_running_loop()
            product = await loop.run_in_executor(None, parse_product, html, url)
            new_links = await loop.run_in_executor(None, extract_product_links, html, url)

            # Enqueue discovered links, budget-checked (see 4.3)
            for link in new_links:
                if queue.qsize() < 100_000 and job_state.budget.admit(depth=1):
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
    restored = await restore_frontier(redis_client, job_state.job_id)
    for url in (restored or job_state.seed_urls):
        await queue.put(url)

    job_state.status = "running"
    tasks = [
        asyncio.create_task(
            worker(i, queue, session, rate_limiter, robots, redis_client, db_pool, job_state)
        )
        for i in range(num_workers)
    ]
    checkpointer = asyncio.create_task(
        _periodic_checkpoint(redis_client, job_state.job_id, queue, interval=60.0)
    )

    # try/finally, not a bare cancel after join(). DELETE /jobs/{id} cancels
    # THIS coroutine, which interrupts `await queue.join()` -- and without the
    # finally the worker tasks are never cancelled and keep crawling forever,
    # ignoring the stop request. Cancellation must propagate.
    try:
        await queue.join()
        job_state.status = "done"
    finally:
        checkpointer.cancel()
        for t in tasks:
            t.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)


async def _periodic_checkpoint(
    redis_client: Redis, job_id: str, queue: asyncio.Queue[str], interval: float
) -> None:
    while True:
        await asyncio.sleep(interval)
        await checkpoint_frontier(redis_client, job_id, queue)
```

### 4.8 FastAPI control plane: submit, poll, and stream results

The management API is deliberately thin — it starts a background `asyncio.Task`, reports counters, and streams what has already landed in PostgreSQL. It never touches `aiohttp` or a parser directly.

```python
"""
async_scraper.py — FastAPI management API for the async web scraper.
Python 3.13+, FastAPI 0.135+
"""
from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass, field

import aiohttp
import asyncpg
from fastapi import FastAPI, HTTPException
from fastapi.sse import EventSourceResponse, ServerSentEvent
from pydantic import BaseModel
from redis.asyncio import Redis


@dataclass
class CrawlBudget:
    max_pages: int
    max_depth: int
    pages_seen: int = 0

    def admit(self, depth: int) -> bool:
        if self.pages_seen >= self.max_pages or depth > self.max_depth:
            return False
        self.pages_seen += 1
        return True


@dataclass
class JobState:
    job_id: str
    seed_urls: list[str]
    budget: CrawlBudget
    status: str = "pending"         # pending | running | stopped | done
    fetched: int = 0
    errors: int = 0
    task: asyncio.Task | None = None


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
        headers={"User-Agent": "AsyncScraper/1.0 (+https://example.com/bot)"},
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
    max_pages: int = 10_000
    max_depth: int = 5


class JobStatus(BaseModel):
    job_id: str
    status: str
    fetched: int
    errors: int


class ProductOut(BaseModel):
    url: str
    title: str
    price: str | None
    domain: str


@app.post("/jobs", status_code=202)
async def submit_job(req: JobRequest) -> JobStatus:
    if req.job_id in _jobs:
        raise HTTPException(status_code=409, detail="Job ID already exists")
    budget = CrawlBudget(max_pages=req.max_pages, max_depth=req.max_depth)
    state = JobState(job_id=req.job_id, seed_urls=req.seed_urls, budget=budget)
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
        job_id=state.job_id, status=state.status, fetched=state.fetched, errors=state.errors
    )


@app.delete("/jobs/{job_id}", status_code=204)
async def stop_job(job_id: str) -> None:
    state = _jobs.get(job_id)
    if state is None:
        raise HTTPException(status_code=404, detail="Job not found")
    if state.task and not state.task.done():
        state.task.cancel()
        state.status = "stopped"


@app.get("/jobs/{job_id}/results")
async def stream_results(job_id: str) -> AsyncIterator[ProductOut]:
    """Native JSON Lines streaming [FastAPI 0.134+]. Returning an
    AsyncIterable[Model] from a path operation emits application/jsonl --
    a consumer processes each record as it arrives instead of buffering
    a giant JSON array until the whole crawl (or query) finishes."""
    async with _db_pool.acquire() as conn:
        async for row in conn.cursor(
            "SELECT url, title, price, domain FROM products WHERE job_id = $1", job_id
        ):
            yield ProductOut(**dict(row))


@app.get("/jobs/{job_id}/progress")
async def stream_progress(job_id: str) -> EventSourceResponse:
    """Native SSE [FastAPI 0.135+] -- no third-party sse-starlette needed.
    A dashboard subscribes once and gets a fetched/errors tick every second
    instead of polling GET /jobs/{id} in a loop."""
    state = _jobs.get(job_id)
    if state is None:
        raise HTTPException(status_code=404, detail="Job not found")

    async def events() -> AsyncIterator[ServerSentEvent]:
        while state.status == "running":
            yield ServerSentEvent(
                data=f'{{"fetched": {state.fetched}, "errors": {state.errors}}}',
                event="progress",
            )
            await asyncio.sleep(1.0)
        yield ServerSentEvent(data=f'{{"status": "{state.status}"}}', event="done")

    return EventSourceResponse(events())
```

**Testing the control plane.** Since `httpx.AsyncClient(app=app)` was removed in httpx 0.28.0, exercise the FastAPI app in tests through an explicit ASGI transport rather than a remote connection:

```python
import httpx

async def test_submit_job() -> None:
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/jobs", json={"job_id": "j1", "seed_urls": ["https://example.com"]})
        assert resp.status_code == 202
```

This pattern, `pytest-asyncio`, and `dependency_overrides` for swapping the real Redis/Postgres pools with fakes in tests are covered in depth in the testing module, not repeated here. Async fundamentals — event loop mechanics, `run_in_executor`, structured concurrency with task groups — belong to [Async Patterns and Pitfalls](../../python/async_patterns_and_pitfalls/async_patterns_and_pitfalls.md); this case study only covers what is specific to a crawler.

**Supporting pieces (unchanged from the base design):**

```python
@dataclass
class ProductRecord:
    url: str
    title: str
    price: str | None
    description: str | None
    domain: str


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
            product.url, product.domain, product.title, product.price, product.description,
        )
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

## 5. Design Decisions & Tradeoffs

### 5.1 Concurrency model: asyncio + aiohttp

Web scraping is I/O-bound: most time is spent waiting for network responses. Three options:

| Model | Best for | Drawback |
|---|---|---|
| asyncio + aiohttp | I/O-bound, 100s of concurrent connections | CPU parsing bottleneck if pages are large |
| threading | Legacy code, blocking libraries | GIL limits true parallelism; 100s of threads = high scheduler and address-space pressure |
| multiprocessing | CPU-bound (image processing) | Process startup overhead; IPC complexity |

asyncio runs all workers on a single thread. The event loop yields during `await session.get(url)`, allowing other workers to proceed. A coroutine costs a few KB of heap; an OS thread reserves the platform default stack — 8 MB of *virtual address space* on Linux, of which only the touched pages are ever resident, so quote it as address-space reservation and scheduler pressure rather than 8 MB of RAM apiece. For CPU-heavy parsing, `asyncio.get_running_loop().run_in_executor(None, parse_html, raw_html)` offloads to a thread pool without blocking the loop.

### 5.2 URL deduplication: Redis vs in-memory set vs Bloom filter

| Dimension | Redis `SADD` | In-memory Python `set` | Redis Bloom filter |
|---|---|---|---|
| Multi-process safe | Yes | No — each process has its own set | Yes |
| Survives restart | Yes (with persistence) | No | Yes (with persistence) |
| Memory (10M URLs) | ~1 GB | ~1.4 GB (measured; per-object `str` header + set table beat Redis's per-entry cost) | ~12 MB at 1% FPR (§2) |
| False positives | None | None | ~1% of legitimate pages silently never crawled (§2) |
| Can delete/iterate | Yes | Yes | No |
| Operational cost | Requires Redis infra | None | Requires Redis + `RedisBloom` module |

An in-memory `set[str]` fails once the scraper runs as more than one process, restarts mid-crawl, or the set itself grows into the gigabytes (§2's frontier-memory arithmetic applies here too). `SADD` with a 24-hour TTL is the default in this design because exactness matters more than the last few hundred MB — a silently-dropped page is a worse failure mode for a data-collection job than a slightly larger Redis instance. The Bloom filter is the right call when the dedup set itself would otherwise be the memory bottleneck (hundreds of millions of URLs) and a small, known false-positive rate is acceptable.

### 5.3 Retry and rate-limiting philosophy

Not every failure gets the same treatment (§4.4), and the per-domain limiter (§4.2) is deliberately two separate primitives — a concurrency cap and a pacing delay — rather than one. The alternative of a single global rate limit is cheaper to write and wrong for the reason walked through in §4.2: it optimizes for the aggregate request rate, not for any individual site's actual contract, and a crawler that only respects the aggregate will still get one specific domain blocked.

### 5.4 Results delivery: JSON Lines / SSE vs a hand-rolled `StreamingResponse`

Before FastAPI shipped native `AsyncIterable[Model]` → `application/jsonl` support (0.134+) and native SSE (0.135+), a scraper's results endpoint had two realistic options: buffer the whole result set into a JSON array (wrong for a crawl that can run for hours and produce millions of rows), or hand-roll a `StreamingResponse` that serializes each row and manages newline framing itself. The native path is strictly better for this system: it is fewer lines of code, it validates each yielded `ProductOut` through Pydantic the same as any other response model, and it removes the third-party `sse-starlette` dependency that used to be the only way to do progress streaming. There is no scenario in this design where the hand-rolled version is worth maintaining.

### 5.5 asyncio vs Scrapy vs multiprocessing

| Dimension | asyncio + aiohttp | Scrapy | multiprocessing |
|---|---|---|---|
| Concurrency model | Single-thread cooperative | Twisted async reactor | Multiple processes, true parallelism |
| I/O throughput | Excellent — thousands of connections | Excellent — production-proven | Good, but IPC overhead |
| CPU-bound parsing | Needs `run_in_executor` | Needs process pipeline | Native — each process has own GIL |
| Learning curve | Medium — asyncio patterns | High — Scrapy middleware chain | Low for Python devs |
| Existing middleware | None built-in | Extensive (dupefilter, httpcache, autothrottle) | None |
| Best for | Custom pipelines, full control | Production crawlers, team knowledge | ML preprocessing, heavy extraction |

**Choose asyncio** when you need tight integration with an async FastAPI service and custom business logic. **Choose Scrapy** when you need Scrapy's auto-throttle, built-in middleware ecosystem, and a team already familiar with it.

### 5.6 Push vs pull crawl architecture

**Pull (current design):** Workers pull URLs from a central `asyncio.Queue`. Simple, low latency, natural backpressure. Works well for a single-machine deployment.

**Push (distributed):** A central URL scheduler pushes batches of URLs to worker queues (Kafka topics or Redis streams). Enables horizontal scale-out to multiple machines but adds coordination complexity (partition assignment, offset tracking, rebalancing).

For 10,000 pages/hour = 2.8 pages/second, a single machine with 50 async workers handles this easily. Distributed architecture adds engineering cost without benefit at this scale; §10 revisits the crossover point.

---

## 6. Real-World Implementations

The design decisions above are not academic — they mirror what the industry's actual large-scale crawlers publish about their own operation.

**Common Crawl.** The August 2025 monthly crawl archive contains **2.44 billion web pages (424 TiB of uncompressed content)**, including **675 million URLs not visited in any prior crawl**, across 47.5 million hosts and 38.5 million registered domains ([commoncrawl.org/blog/august-2025-crawl-archive-now-available](https://commoncrawl.org/blog/august-2025-crawl-archive-now-available)). Publishing "new vs. previously-seen" URL counts per crawl is the dedup problem from §5.2 at planet scale — Common Crawl's crawler has to answer the exact "have I seen this canonical URL before" question this design answers with Redis `SADD`, just at a scale where the corpus itself, not a single process's memory, is the constraint.

**Google Search.** Google's crawl-budget documentation states that "every site starts with the same default, conservative crawl capacity limit," which the crawler then raises if the site responds consistently and lowers if it "responds with server errors (5xx HTTP status codes) or rate-limiting signals (such as HTTP 429)" ([developers.google.com/crawling/docs/crawl-budget](https://developers.google.com/crawling/docs/crawl-budget)). This is adaptive rate control implemented at web scale, and it validates the design choice in §4.4/§5.3: reading `429`/`5xx` and adjusting request rate in response is not a defensive nicety, it is how the largest crawler on the internet decides how hard to hit any given site.

**AhrefsBot.** Ahrefs' own documentation states that AhrefsBot "crawls 5 million pages every minute" — roughly 7.2 billion pages/day — while fully obeying `robots.txt` and `Crawl-Delay`, and it publishes its crawling IP ranges so site operators can identify, allow, or block it explicitly ([ahrefs.com/seo/glossary/ahrefsbot](https://ahrefs.com/seo/glossary/ahrefsbot), [ahrefs.com/robot](https://ahrefs.com/robot)). At that scale, politeness is not a courtesy — it is the mechanism that keeps a crawler from being globally blocklisted by the sites it depends on for its own business, and publishing IP ranges (with reverse-DNS verification) is how a legitimate crawler proves it is the well-behaved bot it claims to be rather than an anonymous scraper an operator has to guess about.

**Semrush.** SemrushBot's documentation states it honors `Crawl-Delay` up to a **10-second cap** — "higher values will be cut down to this 10-second limit" — and, absent an explicit `Crawl-Delay`, "will adjust the frequency of requests to your site according to the current server load" ([semrush.com/bot](https://www.semrush.com/bot/)). Capping the maximum honored delay is the same reasoning behind this design's bounded queue and worker pool (§4.1): a crawler that obeys an unbounded site-stated delay lets a single misconfigured or hostile `robots.txt` stall an entire worker indefinitely.

**Cloudflare.** Cloudflare's 2025 Radar Year in Review reports that by the end of 2025, human traffic accounted for 47% of HTML requests observed across its network, non-AI bots for 44%, and AI bots for 4.2% — with a separate "User Action Crawling" category (AI agents fetching pages live in response to a user's query) growing **over 15x during 2025** ([blog.cloudflare.com/radar-2025-year-in-review](https://blog.cloudflare.com/radar-2025-year-in-review/)). Any crawler built today operates in an environment where roughly half of a target site's traffic is already automated, which is exactly why sites increasingly rate-limit and block by behavioral signature rather than by user-agent string alone — reinforcing why identifying honestly (a real `User-Agent`, ideally published IP ranges, as Ahrefs and Semrush do) beats trying to blend in adversarially, which tends to get a crawler treated as the hostile traffic it is trying not to look like.

---

## 7. Technologies & Tools

| Component | Role in this system |
|---|---|
| `asyncio.Queue` | Bounded URL frontier; `maxsize=200_000` prevents unbounded memory growth; `queue.join()` blocks until all URLs processed |
| `asyncio.Semaphore` | Per-domain concurrency cap (max 5 simultaneous connections to one host) |
| `asyncio.create_task` | Spawns worker coroutines concurrently; each task runs the full fetch-parse-store pipeline |
| `asyncio.get_running_loop().run_in_executor` | Offloads CPU-bound HTML parsing (BeautifulSoup) and blocking `robotparser.read()` to a thread pool |
| `aiohttp.ClientSession` | Shared async HTTP client with connection pooling (`TCPConnector(limit=100)`); reused across all workers |
| `aiohttp.ClientTimeout` | 15-second total timeout per request; prevents workers hanging on slow servers |
| `asyncpg` | Async PostgreSQL driver; connection pool (`min_size=5, max_size=20`); `UPSERT` with `ON CONFLICT` |
| `redis.asyncio.Redis` | Async Redis client for distributed URL deduplication via `SADD` + `EXPIRE`; also backs frontier checkpointing (§4.6) |
| `FastAPI` + `lifespan` | Management API; `lifespan` context manager initialises and tears down shared resources (aiohttp session, asyncpg pool, Redis) cleanly |
| `AsyncIterable[Model]` (native JSONL, FastAPI 0.134+) | Streams `/jobs/{id}/results` as `application/jsonl`, one record per completed page, no buffering |
| `fastapi.sse.EventSourceResponse` (native SSE, FastAPI 0.135+) | Streams live `/jobs/{id}/progress` ticks to a dashboard without polling or a third-party SSE library |
| `Pydantic BaseModel` | Request/response validation for `JobRequest`, `JobStatus`, `ProductOut` |
| `urllib.robotparser` | Standard-library robots.txt parser; wrapped with `run_in_executor` because its `read()` method is synchronous |
| `httpx.ASGITransport` | Drives the FastAPI app in-process for tests without a running server or the removed `AsyncClient(app=...)` shortcut |
| `selectolax` (alternative to BeautifulSoup4) | A `lexbor`/`myhtml`-backed C parser; drop-in for `parse_product`/`extract_product_links` when parse CPU (§2) becomes the bottleneck at higher throughput |
| `playwright.async_api` | Browser-context pool for the subset of target sites that require JavaScript rendering (§11) |

---

## 8. Operational Playbook

**Deployment shape.** Run the FastAPI control plane as a normal ASGI deployment (Uvicorn workers behind a reverse proxy) exactly as any other FastAPI service — see [Production Deployment and Scaling](../production_deployment_and_scaling/production_deployment_and_scaling.md) for the general pattern. The worker fleet lives inside the *same* process as background `asyncio.Task`s in this design's single-machine form (§5.6); at the distributed scale in §10, worker fleets run as their own deployment, consuming from a shared Redis-backed or Kafka-backed frontier instead of an in-process queue.

**Starting a crawl.** `POST /jobs` with seed URLs, a page budget, and a depth cap (§4.3) — never start a job without both budget fields set; an unbounded job is the crawler-trap failure mode in §9 waiting to happen.

**Monitoring a running crawl.** `GET /jobs/{id}` for a point-in-time snapshot, `GET /jobs/{id}/progress` (SSE) for a live dashboard tick, `GET /jobs/{id}/results` (JSON Lines) to consume completed records incrementally rather than waiting for the job to finish. In production, back these with real metrics rather than the in-memory `JobState` counters alone: expose a Prometheus `/metrics` endpoint via `prometheus-fastapi-instrumentator` with `scraped_total`, `errors_total`, `queue_depth`, and `active_workers` gauges.

**Alerting thresholds.**

| Signal | Threshold | Action |
|---|---|---|
| `errors_total / scraped_total` over 5 min | > 10% | Page on-call; likely a domain block or a parser regression |
| `queue_depth` | sustained > 90% of `maxsize` | A domain is outpacing its politeness budget or a crawler trap is active (§9) |
| p95 fetch latency, per domain | > 5x that domain's rolling median | Early signal of an impending 429/block before the error rate itself moves |
| Checkpoint age (§4.6) | > 2x the checkpoint interval | Checkpointer task died; restart risk has grown past the designed bound |

**Stopping a crawl.** `DELETE /jobs/{id}` cancels the job's root task; the `try/finally` in `run_job` (§4.7) guarantees every worker task and the checkpointer are cancelled too, not just the coroutine `DELETE` directly interrupted — see the comment in §4.7 for why the naive version (cancel and return) leaves workers running forever.

**Getting blocked mid-crawl.** If a domain starts returning `403`/`429` at a rate that outlasts normal backoff, mark the domain "blocked" in the rate limiter (stop dequeueing its URLs entirely) rather than letting workers keep retrying into a growing ban — §9 quantifies what happens when this isn't automated. Recovery is manual: confirm the crawl rate genuinely regressed to the site's published limit (§6), and only re-enable the domain after the ban window the site's own `429` response or support contact indicates has elapsed.

**Configuration.** Per-domain rate limits, worker counts, and budgets are runtime configuration, not code — load them via `pydantic-settings` (see [Configuration and Settings Management](../configuration_and_settings_management/configuration_and_settings_management.md)) so a domain's politeness rate can be tightened after an incident without a redeploy.

**Graceful shutdown.** On `SIGTERM`, the `lifespan` context manager's teardown path should checkpoint every running job's frontier before closing the `aiohttp.ClientSession` and `asyncpg.Pool` — a deploy should look like a very short blocked-domain pause to any job in flight, not a lost crawl.

---

## 9. Common Pitfalls & War Stories

**Unbounded `asyncio.gather` over the full URL list.** The §4.1 anti-pattern is not hypothetical — it is the natural first draft. Run against 100,000 seed URLs at a 200 KB average page size, an unbounded `gather` held every response body in memory simultaneously: 100,000 × 200 KB = **20 GB of response bodies alone**, before counting per-task coroutine and connection overhead. The OOM killer took the process at **43% through the run**, and because nothing had been written to PostgreSQL yet — `gather` only returns after every request completes — the crawl lost **all 3.5 hours** of work with no partial results and no checkpoint to resume from. The fix is the bounded worker-pool shape in §4.1/§4.7, where memory is bounded by `num_workers × avg_response_size` (≈10 MB at 50 workers) regardless of how many URLs are queued, and results are written to the database as each page completes.

**Ignoring `Crawl-delay` and getting the IP blocked.** A misconfigured job crawled one domain at roughly 20 requests/second against that domain's stated 1 request/second `Crawl-delay`. The target's WAF blocked the scraper's egress IP within **4 minutes**. The ban lasted **24 hours**, and because the frontier had no mechanism to mark a domain as blocked (§8), workers kept dequeueing and failing against it for the rest of the run — roughly **40,000 wasted request attempts** against a domain that was never going to answer any of them, crowding out capacity that could have gone to the other domains in the job.

**A calendar page as a crawler trap.** One target site's event calendar had a "next month" link with no terminal date. Without a depth or page-count cap, the frontier grew from **5,000 seed URLs to 2.3 million within 20 minutes**, and by the time an operator noticed, that single domain had consumed **94% of the 200,000-item queue's capacity** — every other domain in the job effectively starved, because the bounded queue (correctly) stopped accepting new URLs once full, and the ones already queued were almost entirely calendar pages. The fix is the `CrawlBudget.admit()` depth/page cap in §4.3, applied per job and enforced at enqueue time, not discovered after the fact.

**Duplicate content without canonicalization.** Session-id query parameters (`?sid=...`) turned each real product page into effectively unlimited URL variants. Without stripping known tracking/session parameters before the dedup check (§4.3), the Redis dedup set grew to **40 million entries for what was actually 800,000 unique pages** — a 50x inflation that cost roughly **4 GB of avoidable Redis memory** and caused the same underlying page to be fetched, parsed, and upserted hundreds of times over the course of the crawl, for zero additional data.

**Retrying 429s without honoring `Retry-After`.** An earlier version of the retry logic used a flat exponential backoff for every failure class, including `429`, instead of reading the `Retry-After` header (§4.4). One target's rate-limiting middleware treated the non-compliant retry pattern as abusive and escalated a **60-second** advertised cooldown into a **6-hour** IP-level block once its abuse-detection system flagged the domain's requests. Workers that correctly waited out the advertised `Retry-After` value on the same site lost roughly **2 minutes** in aggregate for the same rate-limit event.

---

## 10. Capacity Planning

**Worker counts.** From §2's Little's Law arithmetic: the base case (2.78 pages/s) needs only ~3 requests in flight at steady state, and the 10x case (27.8 pages/s) needs ~28. This design provisions 50 workers for the base case and 200 for the 10x case — a 15-20x margin over the theoretical minimum, sized for variance (slow domains, retries, cold robots.txt fetches) rather than the average.

**Per-host concurrency budget — the real ceiling.** Politeness caps each domain at 5 req/s (§4.2). With `D` domains in the seed set, the system-wide ceiling is `D × 5 req/s`, independent of worker count:

| Domains (D) | System ceiling (D × 5 req/s) | Reaches base target (2.78/s)? | Reaches 10x target (27.8/s)? |
|---|---|---|---|
| 5 | 25 req/s | Yes, with margin | No — hard ceiling below target |
| 10 | 50 req/s | Yes | Yes, with margin |
| 6 | 30 req/s | Yes | Marginal — no room for retries/backoff |

Raising `num_workers` past this ceiling buys nothing: idle coroutines parked on the same per-domain semaphore. The only levers that raise the ceiling itself are widening the domain set, negotiating a higher rate directly with a site, or sharding the same domain's crawl across multiple processes (each with its own rate-limiter state, so two processes each honoring 5 req/s against one domain enforce 10 req/s in aggregate — a deliberate, not accidental, way to raise a single domain's effective rate when the site has agreed to it).

**Egress bandwidth cost.** From §2: 2 GB/hour raw HTML at the base rate, 20 GB/hour at the 10x rate.

| Scale | GB/month fetched | At $0.09/GB egress | Monthly egress cost |
|---|---|---|---|
| Base (10k pages/hr) | 2 GB × 24 × 30 = 1,440 GB | × $0.09 | ≈ $130/month |
| 10x (100k pages/hr) | 20 GB × 24 × 30 = 14,400 GB | × $0.09 | ≈ $1,296/month |

**Storage cost.** From §2: structured records alone are ~14.4 GB/month (negligible cost). Retaining raw HTML for reprocessing is the number that matters:

| Scale | Raw HTML GB/month | At $0.023/GB (object storage, standard tier) | Monthly storage cost |
|---|---|---|---|
| Base | 1,440 GB | × $0.023 | ≈ $33/month, growing without a retention TTL |
| 10x | 14,400 GB | × $0.023 | ≈ $331/month, growing without a retention TTL |

A 30-90 day TTL on raw HTML (keep it only long enough to re-tune extraction logic, then delete it and keep the structured record) keeps this cost bounded rather than growing linearly forever.

**Proxy/IP pool sizing.** Some target sites rate-limit or block by source IP independent of `robots.txt` compliance — a legitimate, politeness-respecting crawler can still exceed a per-IP threshold a site enforces (e.g., 100 req/hour/IP) if the job's target rate for that domain exceeds it. Pool size for one such domain is `target_requests_per_hour / per_IP_limit`: a domain needing 500 req/hour against a 100 req/hour/IP ceiling needs a **5-IP rotation** for that domain alone. Across a job with, say, 10 domains enforcing similar per-IP ceilings, a **50-100 IP rotating pool** covers the job with headroom — sized per-domain, not as one flat number, since domains with generous per-IP limits need none of the pool at all.

---

## 11. Interview Discussion Points

**Q: What makes asyncio more efficient than threading for this use case?**
asyncio uses a single thread and cooperative multitasking: when a coroutine awaits a network response, the event loop runs another coroutine. This avoids thread context-switch overhead (~1-5 µs) and the GIL contention that limits Python threads. Be precise about the memory claim, because it is routinely overstated: 50 coroutines cost single-digit MB of heap, while 50 OS threads reserve ~400 MB of *virtual address space* (8 MB default stack each on Linux) but touch only a few KB of it, so resident usage is far smaller than the reservation. The honest advantage is scheduler and context-switch cost, not a 40x RSS saving. The tradeoff: one blocking call anywhere stalls all workers, so CPU-bound operations must go through `run_in_executor`.

**Q: How do you prevent overwhelming a single domain with concurrent requests?**
Two mechanisms in combination: a per-domain `asyncio.Semaphore(5)` caps concurrent open connections to that domain to 5, and a politeness delay of `sleep(1/rate)` ensures at least 200 ms between consecutive requests even when 5 connections are available simultaneously. The semaphore prevents connection storms; the sleep provides steady cadence. The detail interviewers probe is the *scope* of the semaphore: it has to be held for the whole fetch, which is why the limiter is an `async with rate_limiter.slot(url)` context manager rather than an `acquire()` that returns before the request goes out. Release it early and it caps nothing — concurrency settles at `fetch_latency / interval` instead, which at a 2 s fetch and 200 ms pacing is 10 open connections against a host you advertised as capped at 5. The pacing update also needs its own `asyncio.Lock`, or every coroutine inside the semaphore reads the same `_last_request`, computes the same wait, and they all fire together.

**Q: Why is a global semaphore the wrong primitive here, specifically?**
Because it shares one concurrency budget across every domain, so one slow-responding domain (2 s average latency) can occupy most of the open slots while a fast domain (200 ms average) queues behind it, even though the fast domain's own rate limit has headroom to spare. It is a starvation bug, not merely a suboptimal-throughput bug: the fast domain is being limited by an unrelated domain's behavior, not by its own contract. The fix is one semaphore and one pacing lock per domain (§4.2), keyed by `urlparse(url).netloc`.

**Q: How does the URL frontier handle backpressure when pages are discovered faster than they are processed?**
The `asyncio.Queue(maxsize=200_000)` provides bounded capacity. When the queue is full, `queue.put()` blocks the producer coroutine, creating natural backpressure. Workers calling `queue.task_done()` signal completion, allowing blocked puts to resume. If 200,000 URLs is insufficient for a deep crawl, the frontier can be backed by Redis lists (`LPUSH`/`BRPOP`) which provide persistent, unbounded storage at the cost of Redis round-trips per URL.

**Q: What stops a crawler trap like an infinite calendar from consuming the whole run?**
A hard page-count and depth budget enforced at enqueue time (`CrawlBudget.admit()`, §4.3), independent of how well-formed any individual page's links look. Canonicalization alone does not solve this — a "next month" link produces genuinely distinct canonical URLs forever, so the defense has to be a ceiling on total pages and link depth, not a smarter dedup check. Without one, a single trapped domain consumed 94% of a 200,000-item queue in 20 minutes in this design's own incident history (§9), starving every other domain in the job.

**Q: What happens when a database write fails mid-crawl?**
The worker loop catches all exceptions, increments the error counter, and continues. A more robust approach wraps the `write_product` call in a retry with exponential backoff for `asyncpg.TooManyConnectionsError` or transient network errors. For idempotency, the schema uses `ON CONFLICT (url) DO UPDATE`, so replaying a URL after a partial write is safe.

**Q: How would you scale this system to 10x throughput (100,000 pages/hour)?**
100,000 pages/hour = 28 pages/second, and the first move is to work out what actually binds — which is *not* the worker count. Politeness caps each domain at 5 req/s, so 5-10 domains give a hard ceiling of 25-50 pages/second no matter how many workers you add (§10). At 5 domains, 28 pages/s is simply unreachable; raising `num_workers` from 50 to 200 buys nothing but idle coroutines parked on the same semaphores. So: (1) widen the domain set, or negotiate a higher rate with the sites you have — this is the only lever that raises the ceiling; (2) once past ~10 domains, shard by domain across multiple scraper processes with Kafka topic partitioning, because the per-domain limiter is per-process state and two processes crawling one domain each enforce 5 req/s independently, giving 10; (3) add a second Redis for dedup to cut round-trip latency. PostgreSQL is not the constraint at this scale — 28 single-row inserts/second is trivial for it — but batching with `asyncpg`'s `executemany` or a buffer that flushes every 100 records or 1 second still cuts connection churn and is worth doing before it matters.

**Q: How do you handle sites that require JavaScript rendering?**
aiohttp fetches raw HTML and cannot execute JavaScript. For JS-heavy sites, use Playwright's async API (`playwright.async_api`) with a pool of browser contexts. Each browser context costs ~50 MB RAM vs ~1 MB for an HTTP connection, so limit JS-rendered workers to a smaller pool (5-10) and route by domain. Alternatively, look for the site's internal API that the JavaScript calls and scrape that directly.

**Q: How does the robots.txt cache avoid stale data?**
The `RobotsCache` stores a `(parser, timestamp)` tuple per domain and re-fetches when the cached entry is older than 1 hour (`_ttl = 3600`). The failure fallback is not a single rule, because RFC 9309 §2.3.1 splits the cases: a 4xx is "unavailable" and the crawler MAY access anything (`allow_all`), while an unreachable host — network error or 5xx — leaves robots.txt undefined and the crawler MUST assume complete disallow (`disallow_all`). `RobotFileParser.read()` already implements the HTTP half; the code only has to handle the network error, and it must fail closed there, not open. A 1-hour TTL balances freshness against the overhead of fetching robots.txt on every page request.

**Q: Why use `ON CONFLICT DO UPDATE` instead of checking existence before insert?**
A check-then-insert sequence has a TOCTOU race: two workers could both check, both find the URL absent, and both attempt to insert, causing a constraint violation. `ON CONFLICT DO UPDATE` (an UPSERT) is atomic at the database level — the second writer's insert becomes an update. It also handles the case where a re-crawl run wants to refresh prices without needing explicit logic to distinguish new vs existing rows.

**Q: How would you add politeness delays that respect the `Crawl-Delay` directive in robots.txt?**
`RobotFileParser` exposes `crawl_delay(useragent)`, which returns the `Crawl-Delay` value in seconds if the site set one and `None` otherwise. The `DomainRateLimiter` can be extended to read it when initialising a domain's slot: if `robots.crawl_delay("AsyncScraper/1.0")` returns 2, set `_min_interval = 2.0` for that domain rather than the default `1/max_rps`, and take `max()` of the two so a site's stated delay can only ever slow you down. Semrush's own crawler caps the delay it will honor at 10 seconds (§6) for the same reason this design bounds worker resources generally — an unbounded honored delay lets one site's `robots.txt` stall a worker indefinitely. `request_rate(useragent)` is the related accessor for the `Request-Rate` directive.

**Q: How do you monitor crawl health in production?**
The `JobState` dataclass tracks `fetched` and `errors` counts exposed via `GET /jobs/{job_id}` and streamed live via SSE (§4.8). For production monitoring: (1) expose a Prometheus `/metrics` endpoint with `scraped_total`, `errors_total`, `queue_depth`, and `active_workers` gauges using `prometheus-fastapi-instrumentator`; (2) alert if `errors/fetched > 10%` over a 5-minute window (§8); (3) track p95 fetch latency per domain to catch a slow-down before the error rate itself moves, since a rising latency on one domain is often the leading indicator of an impending block.

**Q: What's the tradeoff between a Redis `SADD` set and a Bloom filter for dedup at very large scale?**
Exactness versus memory. `SADD` guarantees no false positives — a URL is only skipped if it was genuinely seen — at roughly 1 GB per 10 million URLs. A Bloom filter at a 1% false-positive rate needs the bits-per-element formula `m = -(n·ln p)/(ln 2)²`, which works out to about 12 MB for the same 10 million URLs (§2) — an 80x memory reduction. The cost is that roughly 1% of genuinely-new URLs get silently marked "already seen" and are never crawled, with no error and no log line to catch it. That tradeoff is worth making once the dedup set itself is the memory bottleneck (hundreds of millions to billions of URLs, the scale Common Crawl and AhrefsBot operate at, §6) and wrong to make at this system's own 10-million-URL scale, where the exact structure comfortably fits in under 1.5 GB.
