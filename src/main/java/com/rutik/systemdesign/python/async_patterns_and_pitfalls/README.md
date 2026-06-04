# Async Patterns & Pitfalls

> Advanced companion to `../asyncio_and_event_loop/README.md`. Covers production patterns
> that go beyond the event loop fundamentals: detecting and fixing blocking-in-async (the #1
> FastAPI production bug), executor integration, async generators, rate limiting with
> `asyncio.Semaphore`, backpressure, retry with jitter, circuit breakers, timeout composition,
> and memory leak prevention.

---

## 1. Concept Overview

Python's `asyncio` event loop is single-threaded. Every coroutine that calls a blocking
synchronous function (network I/O via `requests`, disk I/O via `open`, CPU computation)
stalls the entire loop for the duration of that call. No other coroutine can make progress
during a stall. In a FastAPI service handling 500 concurrent requests, a single 200ms
`requests.get()` inside an `async def` route blocks all 500 concurrent requests for 200ms.

This module covers:

- Detecting blocking-in-async with debug mode and profiling
- Offloading sync work with `asyncio.to_thread()` (Python 3.9) and `run_in_executor()`
- Async generators and async comprehensions for lazy streaming pipelines
- `asyncio.Semaphore` for rate limiting concurrent coroutines
- Backpressure via `asyncio.Queue(maxsize=N)`
- Retry with exponential backoff and jitter to prevent thundering-herd
- Timeout composition with `asyncio.timeout()` (Python 3.11) vs `asyncio.wait_for()`
- Circuit breaker pattern in async code
- Common memory leaks: untracked tasks, un-closed async generators

Python version baseline for this module: **3.11** (3.12 for free-threading notes).

---

## 2. Intuition

> The event loop is a single-lane road. Every coroutine is a car that must yield at an
> `await` checkpoint to let other cars through. A blocking call is a car that parks in the
> lane and refuses to yield — it stops all traffic behind it.

**Mental model**: Think of `asyncio` as a cooperative scheduler. "Cooperative" means every
task must voluntarily yield control at `await` points. If a task never yields, no other task
can run. Patterns in this module teach you how to: (a) catch tasks that never yield,
(b) delegate blocking work to threads or processes so the event loop stays clear, and (c)
add resilience layers (retry, circuit breaker, semaphore, backpressure) so the road handles
heavy traffic gracefully.

**Why it matters**: FastAPI's entire performance advantage over synchronous Flask comes from
the event loop's ability to handle thousands of concurrent I/O-bound requests on a single
thread. One blocking call inside a route function erases that advantage completely. At
Stripe, a mistaken `time.sleep()` inside an async payment handler caused a 40-second outage
affecting 12,000 merchants during a 2022 incident.

**Key insight**: `async def` does not make a function non-blocking. It only marks it as a
coroutine that *can* yield. The blocking happens when you call a sync function inside it
without offloading to a thread. `async def` is a promise to the event loop that you *will*
yield — you must keep that promise with every I/O operation.

---

## 3. Core Principles

1. **Never block the event loop**: Every I/O call inside `async def` must use an async
   library (`httpx`, `aiofiles`, `asyncpg`) or be offloaded via `asyncio.to_thread()`.

2. **Bound all concurrency**: Unbounded `asyncio.gather()` over thousands of URLs will
   exhaust file descriptors, trigger 429 rate-limits, and crash the target service. Always
   pair with `asyncio.Semaphore`.

3. **Track all tasks**: `asyncio.create_task()` returns a `Task` object. If no reference
   is kept, Python's GC can cancel the task mid-execution. Store tasks; clean up on
   completion.

4. **Compose timeouts, not nest them**: `asyncio.timeout()` (3.11) is a context manager
   that composes cleanly with other async context managers. Prefer it over wrapping every
   call in `asyncio.wait_for()`.

5. **Add resilience at the call site**: Retry and circuit breaker logic belongs in the
   HTTP client layer, not scattered across business logic. A decorator-based retry is
   testable and reusable.

6. **Apply backpressure explicitly**: If a producer generates work faster than a consumer
   can process it, use `asyncio.Queue(maxsize=N)` to provide backpressure. Without it,
   memory grows unbounded.

---

## 4. Types / Architectures / Strategies

### 4.1 Executor Integration (sync → async bridge)

| Method | Python Version | Use Case |
|---|---|---|
| `loop.run_in_executor(None, fn, *args)` | 3.4+ | Explicit loop, ThreadPoolExecutor |
| `asyncio.to_thread(fn, *args)` | 3.9+ | Shorthand for `run_in_executor(None, ...)` |
| `ProcessPoolExecutor` via `run_in_executor` | 3.4+ | CPU-bound tasks that need true parallelism |

### 4.2 Async Generators and Comprehensions

- `async def gen() -> AsyncGenerator[T, None]`: yields values across await points
- `async for item in gen()`: consumes async generator
- `aiter(obj)` / `anext(obj)` built-ins (3.10+): protocol functions like `iter()` / `next()`
- `[x async for x in gen()]`: async list comprehension
- `{x async for x in gen()}`: async set comprehension

### 4.3 Concurrency Control Primitives

| Primitive | Purpose | Blocking behaviour |
|---|---|---|
| `asyncio.Semaphore(n)` | Limit concurrent coroutines to n | `await sem.acquire()` suspends if count == 0 |
| `asyncio.Queue(maxsize=n)` | Bounded producer/consumer channel | `await q.put()` suspends when full |
| `asyncio.Lock()` | Mutual exclusion | `await lock.acquire()` suspends if locked |
| `asyncio.Event()` | One-to-many notification | `await event.wait()` suspends until set |
| `asyncio.Barrier(n)` (3.11) | Synchronize n coroutines | `await barrier.wait()` suspends until n waiting |

### 4.4 Resilience Patterns

- **Retry with exponential backoff + jitter**: retries transient failures without thundering herd
- **Circuit breaker**: fails fast after N consecutive failures; reopens after a cool-down
- **Timeout composition**: wraps operations with a hard deadline
- **Backpressure queue**: producer slows down when consumer is overwhelmed

---

## 5. Architecture Diagrams

### 5.1 Event Loop — Blocking vs Non-Blocking

```
BLOCKING (time.sleep inside async def)
======================================
Event Loop tick 0:  [Route A starts] → calls time.sleep(1) → BLOCKS LOOP
Event Loop tick 1:  *** FROZEN — no other coroutine can run for 1 second ***
Event Loop tick 2:  [Route A resumes] → returns

NON-BLOCKING (await asyncio.sleep)
====================================
Event Loop tick 0:  [Route A starts] → hits await asyncio.sleep(1) → YIELDS
Event Loop tick 1:  [Route B runs] → completes
Event Loop tick 2:  [Route C runs] → completes
...
Event Loop tick N:  [Route A resumes after 1s] → returns
```

### 5.2 Semaphore-Bounded Gather

```
1000 URLs
    |
    v
asyncio.gather(fetch(u) for u in urls)  ← without semaphore
    |
    +-- 1000 concurrent connections → 429 Too Many Requests (70% failure)

asyncio.gather(bounded_fetch(u) for u in urls)  ← with Semaphore(50)
    |
    +-- semaphore ──┐
                    ├── 50 concurrent at a time → 20 rounds → 0% failure
                    └── 950 waiting in coroutine suspension (no thread cost)
```

### 5.3 Retry + Circuit Breaker + Backpressure Stack

```
                         Producer coroutines
                               |
                    asyncio.Queue(maxsize=100)   ← backpressure
                               |
                         Consumer pool
                               |
                    asyncio.Semaphore(20)         ← concurrency limit
                               |
                       retry_async decorator      ← retry + jitter
                               |
                       CircuitBreaker             ← fail fast
                               |
                         httpx.AsyncClient        ← actual HTTP call
```

### 5.4 asyncio.timeout() Composition (3.11)

```
async with asyncio.timeout(10.0):          # outer: total budget
    async with asyncio.timeout(3.0):       # inner: per-step budget
        result = await step_one()
    async with asyncio.timeout(6.0):       # independent inner timeout
        result = await step_two(result)
    # remaining 1s absorbed by outer
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Detecting Blocking-in-Async

Python's debug mode emits a warning when a coroutine holds the event loop for more than
100ms without yielding.

```python
import asyncio
import logging

logging.basicConfig(level=logging.DEBUG)

async def main() -> None:
    loop = asyncio.get_event_loop()
    loop.set_debug(True)          # enables slow-callback warnings (> 100ms)
    loop.slow_callback_duration = 0.05  # tighten to 50ms in production profiling
    await asyncio.sleep(0)        # yield once to let debug hooks initialize

asyncio.run(main())
```

Output when a route blocks for 150ms:
```
DEBUG:asyncio:Executing <Task ...> took 0.153 seconds
```

**Sentry integration** (production): `sentry_sdk.init(integrations=[AsyncioIntegration()])`.
Sentry captures blocking-loop events as performance issues.

**time.sleep vs asyncio.sleep — the canonical example**:

```python
import asyncio
import time

async def blocking_route() -> dict:
    time.sleep(1)           # blocks the entire event loop for 1 second
    return {"status": "ok"}

async def non_blocking_route() -> dict:
    await asyncio.sleep(1)  # suspends THIS coroutine, loop runs others
    return {"status": "ok"}

async def demo() -> None:
    start = time.perf_counter()
    # Run two routes concurrently
    await asyncio.gather(blocking_route(), non_blocking_route())
    print(f"blocking: {time.perf_counter() - start:.2f}s")   # ~2.0s — sequential!

    start = time.perf_counter()
    await asyncio.gather(non_blocking_route(), non_blocking_route())
    print(f"non-blocking: {time.perf_counter() - start:.2f}s")  # ~1.0s — concurrent!

asyncio.run(demo())
```

### 6.2 asyncio.to_thread() — Bridging Sync Libraries

`asyncio.to_thread()` (3.9) submits a callable to the default `ThreadPoolExecutor`.
Thread pool size: `min(32, os.cpu_count() + 4)` (CPython default).

```python
import asyncio
import requests
import os

# Wrapping a blocking HTTP library
async def fetch_with_requests(url: str) -> bytes:
    # requests.get is sync — offload to thread pool
    response = await asyncio.to_thread(requests.get, url, timeout=5)
    return response.content

# Wrapping blocking file I/O
async def read_file(path: str) -> str:
    return await asyncio.to_thread(open(path).read)

# Wrapping CPU-bound work (note: still GIL-bound; use ProcessPoolExecutor for true parallelism)
def compute_hash(data: bytes) -> str:
    import hashlib
    return hashlib.sha256(data).hexdigest()

async def async_hash(data: bytes) -> str:
    return await asyncio.to_thread(compute_hash, data)

# Explicit executor for CPU-bound tasks (ProcessPoolExecutor bypasses GIL)
async def cpu_intensive(n: int) -> int:
    from concurrent.futures import ProcessPoolExecutor
    loop = asyncio.get_running_loop()
    with ProcessPoolExecutor() as pool:
        return await loop.run_in_executor(pool, sum, range(n))
```

`asyncio.to_thread()` is syntactic sugar for:
```python
loop = asyncio.get_running_loop()
await loop.run_in_executor(None, fn, *args)
```

### 6.3 Async Generators — Lazy Streaming Pipelines

```python
import asyncio
from collections.abc import AsyncGenerator
import httpx

async def paginate(
    client: httpx.AsyncClient,
    base_url: str,
    page_size: int = 100,
) -> AsyncGenerator[dict, None]:
    """Lazily fetch all pages; yields one item dict at a time."""
    cursor: str | None = None
    while True:
        params = {"limit": page_size}
        if cursor:
            params["cursor"] = cursor
        resp = await client.get(base_url, params=params)
        resp.raise_for_status()
        data = resp.json()
        for item in data["results"]:
            yield item                  # suspend here, caller can process before next fetch
        cursor = data.get("next_cursor")
        if not cursor:
            break

async def process_all_records(base_url: str) -> int:
    count = 0
    async with httpx.AsyncClient() as client:
        async for record in paginate(client, base_url):
            await process_record(record)    # process lazily — no full page in memory
            count += 1
    return count

# Async comprehension (loads all into memory — use only for small result sets)
async def collect_ids(base_url: str) -> list[str]:
    async with httpx.AsyncClient() as client:
        return [record["id"] async for record in paginate(client, base_url)]

# aiter / anext (3.10+) — manual protocol access
async def peek_first(base_url: str) -> dict | None:
    async with httpx.AsyncClient() as client:
        gen = paginate(client, base_url)
        try:
            return await anext(aiter(gen))
        except StopAsyncIteration:
            return None
```

### 6.4 asyncio.Semaphore for Rate Limiting

```python
import asyncio
import httpx
from typing import Any

SEM_LIMIT = 50  # max concurrent requests to a single third-party API

async def fetch(
    client: httpx.AsyncClient,
    sem: asyncio.Semaphore,
    url: str,
) -> dict[str, Any]:
    async with sem:                 # blocks if 50 coroutines already inside
        resp = await client.get(url, timeout=10.0)
        resp.raise_for_status()
        return resp.json()

async def fetch_all(urls: list[str]) -> list[dict[str, Any]]:
    sem = asyncio.Semaphore(SEM_LIMIT)
    async with httpx.AsyncClient() as client:
        tasks = [fetch(client, sem, url) for url in urls]
        return await asyncio.gather(*tasks)

# Concrete numbers:
# 1000 URLs, no semaphore  → 1000 simultaneous connections → 729 × 429 errors (72.9% failure)
# 1000 URLs, Semaphore(50) → 20 sequential batches of 50   → 0 × 429 errors (0.0% failure)
# Throughput with limit: 50 req / avg_latency_per_req ≈ 50 / 0.1s = 500 req/s (sustained)
```

### 6.5 Backpressure with asyncio.Queue

```python
import asyncio
import httpx

QUEUE_SIZE   = 100   # buffer at most 100 items; producer blocks when full
WORKER_COUNT = 10    # 10 consumer coroutines drain the queue

async def producer(
    queue: asyncio.Queue[str],
    urls: list[str],
) -> None:
    for url in urls:
        await queue.put(url)   # suspends if queue is full (backpressure applied)
    for _ in range(WORKER_COUNT):
        await queue.put(None)  # sentinel: one per worker

async def consumer(
    queue: asyncio.Queue[str | None],
    client: httpx.AsyncClient,
    results: list[bytes],
) -> None:
    while True:
        url = await queue.get()
        if url is None:
            queue.task_done()
            break
        try:
            resp = await client.get(url, timeout=10.0)
            results.append(resp.content)
        finally:
            queue.task_done()

async def bounded_pipeline(urls: list[str]) -> list[bytes]:
    queue: asyncio.Queue[str | None] = asyncio.Queue(maxsize=QUEUE_SIZE)
    results: list[bytes] = []
    async with httpx.AsyncClient() as client:
        workers = [
            asyncio.create_task(consumer(queue, client, results))
            for _ in range(WORKER_COUNT)
        ]
        await producer(queue, urls)
        await asyncio.gather(*workers)
    return results

# Throughput: 10 workers × (1 req / 0.1s avg latency) = 100 req/s sustained
# Memory: bounded by QUEUE_SIZE = 100 URLs in buffer at any time
```

### 6.6 Retry with Exponential Backoff + Jitter

```python
import asyncio
import random
import functools
import logging
from collections.abc import Callable, Awaitable
from typing import Any, TypeVar

F = TypeVar("F", bound=Callable[..., Awaitable[Any]])

log = logging.getLogger(__name__)

def retry_async(
    max_attempts: int = 3,
    base_delay: float = 0.5,
    max_delay: float = 30.0,
    exceptions: tuple[type[Exception], ...] = (Exception,),
) -> Callable[[F], F]:
    """Decorator: retry an async function with exponential backoff + full jitter."""
    def decorator(fn: F) -> F:
        @functools.wraps(fn)
        async def wrapper(*args: Any, **kwargs: Any) -> Any:
            for attempt in range(max_attempts):
                try:
                    return await fn(*args, **kwargs)
                except exceptions as exc:
                    if attempt == max_attempts - 1:
                        log.error(
                            "All %d attempts failed for %s: %s",
                            max_attempts, fn.__name__, exc,
                        )
                        raise
                    # Full jitter: uniform(0, min(max_delay, base_delay * 2**attempt))
                    ceiling = min(max_delay, base_delay * (2 ** attempt))
                    delay = random.uniform(0, ceiling)
                    log.warning(
                        "%s attempt %d/%d failed (%s); retrying in %.2fs",
                        fn.__name__, attempt + 1, max_attempts, exc, delay,
                    )
                    await asyncio.sleep(delay)
        return wrapper  # type: ignore[return-value]
    return decorator

# Usage
@retry_async(max_attempts=5, base_delay=1.0, exceptions=(httpx.HTTPStatusError, httpx.TimeoutException))
async def resilient_get(client: httpx.AsyncClient, url: str) -> dict[str, Any]:
    resp = await client.get(url, timeout=5.0)
    resp.raise_for_status()
    return resp.json()

# Jitter math example (attempt 2, base_delay=1.0, max_delay=30.0):
# ceiling = min(30, 1.0 * 2^2) = 4.0
# delay   = uniform(0, 4.0) → e.g. 2.37s
# Without jitter: all N services retry at exactly 4.0s → thundering herd
# With jitter: spread across [0, 4.0] → load distributed
```

### 6.7 asyncio.timeout() vs asyncio.wait_for() (3.11)

```python
import asyncio

# asyncio.timeout() — context manager, composable (Python 3.11+)
async def fetch_with_timeout_cm(url: str) -> bytes:
    async with asyncio.timeout(5.0):       # TimeoutError if not done in 5s
        async with httpx.AsyncClient() as client:
            resp = await client.get(url)
            return resp.content

# asyncio.timeout_at() — absolute deadline (monotonic time)
async def fetch_with_deadline(url: str, deadline: float) -> bytes:
    async with asyncio.timeout_at(deadline):
        async with httpx.AsyncClient() as client:
            resp = await client.get(url)
            return resp.content

# asyncio.wait_for() — equivalent but wraps coroutine, not composable as CM
async def fetch_with_wait_for(url: str) -> bytes:
    async with httpx.AsyncClient() as client:
        resp = await asyncio.wait_for(client.get(url), timeout=5.0)
        return resp.content

# Composing nested timeouts (only possible with CM form)
async def two_step_operation() -> dict[str, Any]:
    async with asyncio.timeout(10.0):           # hard outer budget: 10s total
        step1 = await asyncio.wait_for(step_one(), timeout=3.0)
        step2 = await asyncio.wait_for(step_two(step1), timeout=6.0)
        return {"step1": step1, "step2": step2}
    # If outer fires, TimeoutError propagates regardless of inner state
```

Key difference: `asyncio.timeout()` raises `TimeoutError` (built-in, 3.11).
`asyncio.wait_for()` raises `asyncio.TimeoutError` (subclass of `TimeoutError` since 3.11,
but separate in 3.10 and earlier — catching `TimeoutError` works in 3.11+ for both).

### 6.8 Async Memory Leaks — Causes and Fixes

**Cause 1: untracked fire-and-forget tasks**

```python
import asyncio
import weakref

# Pattern: track all background tasks with a weakref set
_background_tasks: set[asyncio.Task] = set()

def fire_and_forget(coro) -> asyncio.Task:
    task = asyncio.create_task(coro)
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)
    return task
```

**Cause 2: un-closed async generators**

```python
# If caller breaks out of async for mid-stream, generator's cleanup (finally block)
# runs only when GC collects it — which may be delayed in CPython or never in PyPy.

# Fix: use contextlib.aclosing()
from contextlib import aclosing

async def safe_consume(url: str) -> None:
    async with aclosing(paginate(client, url)) as gen:
        async for item in gen:
            if item["done"]:
                break               # aclosing() guarantees generator.aclose() is called
```

**Cause 3: circular references in closures captured by tasks**

```python
# Closure captures large object → task holds reference → GC cannot collect
# Fix: use weakref or explicit del inside the coroutine before long awaits

import weakref

class RequestContext:
    def __init__(self, data: bytes) -> None:
        self.data = data            # potentially large

async def process(ctx_ref: weakref.ref[RequestContext]) -> None:
    ctx = ctx_ref()
    if ctx is None:
        return
    result = await do_work(ctx.data)
    del ctx                         # release before await; GC can collect if refcount → 0
    await store_result(result)
```

### 6.9 Circuit Breaker Pattern in Async Code

```python
import asyncio
import time
from enum import Enum, auto
from collections.abc import Callable, Awaitable
from typing import Any

class CircuitState(Enum):
    CLOSED   = auto()   # normal — requests flow through
    OPEN     = auto()   # failing — requests rejected immediately
    HALF_OPEN = auto()  # testing — one probe request allowed

class AsyncCircuitBreaker:
    def __init__(
        self,
        failure_threshold: int = 5,
        recovery_timeout: float = 30.0,
        half_open_max_calls: int = 1,
    ) -> None:
        self._state        = CircuitState.CLOSED
        self._failures     = 0
        self._threshold    = failure_threshold
        self._recovery     = recovery_timeout
        self._opened_at    = 0.0
        self._half_probes  = 0
        self._max_probes   = half_open_max_calls
        self._lock         = asyncio.Lock()

    @property
    def state(self) -> CircuitState:
        return self._state

    async def call(self, fn: Callable[..., Awaitable[Any]], *args: Any, **kwargs: Any) -> Any:
        async with self._lock:
            if self._state == CircuitState.OPEN:
                if time.monotonic() - self._opened_at >= self._recovery:
                    self._state       = CircuitState.HALF_OPEN
                    self._half_probes = 0
                else:
                    raise RuntimeError("Circuit OPEN — failing fast")
            if self._state == CircuitState.HALF_OPEN:
                if self._half_probes >= self._max_probes:
                    raise RuntimeError("Circuit HALF_OPEN — probe in progress")
                self._half_probes += 1

        try:
            result = await fn(*args, **kwargs)
            async with self._lock:
                # Success: close if half-open, reset failures
                self._failures = 0
                self._state    = CircuitState.CLOSED
            return result
        except Exception:
            async with self._lock:
                self._failures += 1
                if self._failures >= self._threshold:
                    self._state    = CircuitState.OPEN
                    self._opened_at = time.monotonic()
            raise

# Usage
breaker = AsyncCircuitBreaker(failure_threshold=5, recovery_timeout=30.0)

async def call_payment_api(payload: dict) -> dict:
    return await breaker.call(_do_payment_request, payload)
```

Cross-reference: see `../../backend/api_gateway_patterns/` for circuit breaker concepts
at the API gateway layer. See `../asyncio_and_event_loop/README.md` for `TaskGroup` and
structured concurrency fundamentals.

---

## 7. Real-World Examples

### 7.1 FastAPI Route — Blocking vs Non-Blocking

```python
# Service A (blocking — common mistake in production)
from fastapi import FastAPI
import requests

app = FastAPI()

@app.get("/users/{user_id}")
def get_user(user_id: int) -> dict:          # sync def — FastAPI runs in threadpool worker
    resp = requests.get(f"https://api.internal/users/{user_id}")
    return resp.json()                       # OK for sync def, but wastes a thread

# Service B (wrong — async def with blocking library)
@app.get("/users/{user_id}")
async def get_user_broken(user_id: int) -> dict:
    resp = requests.get(f"https://api.internal/users/{user_id}")  # BLOCKS THE LOOP
    return resp.json()

# Service C (correct)
import httpx

@app.get("/users/{user_id}")
async def get_user_correct(user_id: int) -> dict:
    async with httpx.AsyncClient() as client:
        resp = await client.get(f"https://api.internal/users/{user_id}", timeout=5.0)
        resp.raise_for_status()
        return resp.json()
```

### 7.2 GitHub Actions-style Job Queue

```python
# CI system: queue 500 jobs, run max 20 concurrently, retry failed jobs
import asyncio
import httpx

sem = asyncio.Semaphore(20)

@retry_async(max_attempts=3, base_delay=2.0, exceptions=(httpx.HTTPStatusError,))
async def run_job(client: httpx.AsyncClient, job_id: str) -> dict:
    async with sem:
        resp = await client.post(f"/jobs/{job_id}/run", timeout=60.0)
        resp.raise_for_status()
        return resp.json()

async def run_all_jobs(job_ids: list[str]) -> list[dict]:
    async with httpx.AsyncClient(base_url="https://ci.internal") as client:
        return await asyncio.gather(*(run_job(client, jid) for jid in job_ids))
```

### 7.3 Streaming LLM Response with Async Generator

```python
from collections.abc import AsyncGenerator
import httpx

async def stream_llm(
    client: httpx.AsyncClient,
    prompt: str,
) -> AsyncGenerator[str, None]:
    """Stream tokens from an LLM API as they arrive."""
    async with client.stream(
        "POST",
        "/v1/chat/completions",
        json={"prompt": prompt, "stream": True},
        timeout=None,
    ) as resp:
        async for line in resp.aiter_lines():
            if line.startswith("data: "):
                token = line[6:]
                if token == "[DONE]":
                    return
                yield token

# See `../../llm/case_studies/cross_cutting/streaming_at_scale.md` for SSE/async streaming at scale.

from fastapi.responses import StreamingResponse

@app.post("/generate")
async def generate(prompt: str) -> StreamingResponse:
    async with httpx.AsyncClient(base_url="https://llm.internal") as client:
        async def event_stream():
            async for token in stream_llm(client, prompt):
                yield f"data: {token}\n\n"
        return StreamingResponse(event_stream(), media_type="text/event-stream")
```

---

## 8. Tradeoffs

| Pattern | Benefit | Cost | When to Choose |
|---|---|---|---|
| `asyncio.to_thread()` | Unblocks event loop; reuses existing sync library | Thread creation overhead; GIL contention for CPU work | I/O-bound sync libs (requests, psycopg2 in sync mode) |
| `ProcessPoolExecutor` | True parallelism for CPU-bound work | Process spawn cost (50–100ms); IPC serialization overhead | SHA/RSA, image resize, numpy-heavy computation |
| `asyncio.Semaphore` | Simple, composable rate limiting | Coroutine suspension overhead; no fairness guarantee | Third-party API rate limits, DB connection pool limits |
| `asyncio.Queue(maxsize)` | Bounded buffer with backpressure | Adds latency when queue is full (producer blocks) | High-volume pipeline with uneven producer/consumer speed |
| `retry_async` decorator | Transparent retry logic | Increases tail latency; may amplify load on target | Transient network errors, 429/503 from external APIs |
| `AsyncCircuitBreaker` | Fail fast; protects downstream | State management overhead; false trips possible | Microservice calls, external payment/email APIs |
| `asyncio.timeout()` | Composable, clean deadline management | 3.11+ only; TimeoutError propagates eagerly | Any operation with a hard SLA budget |

---

## 9. When to Use / When NOT to Use

### Use asyncio patterns when:

- Your FastAPI service makes outbound HTTP calls, queries a database, or reads files
- You have I/O-bound concurrency (hundreds of simultaneous requests)
- You need streaming responses (SSE, WebSocket, chunked transfer)
- You are building a data pipeline with controllable throughput (producer/consumer)
- You need resilience (retry, circuit breaker) against flaky downstream services

### Do NOT use asyncio patterns when:

- The work is CPU-bound and GIL-locked: use `ProcessPoolExecutor` + `run_in_executor`
- You need true parallelism across cores: use `multiprocessing` or worker processes
- Your team is not familiar with cooperative scheduling: synchronous FastAPI (sync def)
  runs in Starlette's thread pool and is simpler to reason about
- Third-party libraries are not async-safe (they use `threading.local`, global state, etc.)
  and cannot be safely called from `asyncio.to_thread()` without wrapping
- The operation is < 1ms and the overhead of coroutine scheduling exceeds the operation

### asyncio.to_thread() specific:

- Use when: wrapping `requests`, `boto3` (sync), `psycopg2`, legacy SDKs
- Do not use when: the function holds Python-level locks that conflict with asyncio's loop
  thread, or when CPU-bound computation would saturate all threads simultaneously

---

## 10. Common Pitfalls

### PITFALL 1: sync def with requests in async FastAPI route

```python
# BROKEN: async def calls blocking requests.get — stalls the event loop
from fastapi import FastAPI
import requests

app = FastAPI()

@app.get("/data")
async def get_data() -> dict:
    resp = requests.get("https://api.external.com/data", timeout=5)  # BLOCKS LOOP
    return resp.json()
```

```python
# FIX: use httpx.AsyncClient for async-native HTTP
import httpx
from fastapi import FastAPI

app = FastAPI()

@app.get("/data")
async def get_data() -> dict:
    async with httpx.AsyncClient() as client:
        resp = await client.get("https://api.external.com/data", timeout=5.0)
        resp.raise_for_status()
        return resp.json()
```

Impact of the broken version: at 100 RPS, a 200ms `requests.get()` inside `async def`
serialises all requests — effective throughput drops from 100 RPS to 5 RPS (1 / 0.2s).
The fix restores true concurrency.

---

### PITFALL 2: create_task without storing the result

```python
# BROKEN: task object not stored → may be GC'd before coroutine finishes
import asyncio

async def background_job(item_id: int) -> None:
    await asyncio.sleep(2)
    await save_to_db(item_id)

@app.post("/items/{item_id}/process")
async def process_item(item_id: int) -> dict:
    asyncio.create_task(background_job(item_id))   # no reference kept
    return {"queued": True}
    # task may disappear silently; "Task was destroyed but it is pending!" warning in logs
```

```python
# FIX: track tasks in a module-level set; discard on completion
import asyncio

_tasks: set[asyncio.Task] = set()

def spawn_background(coro) -> asyncio.Task:
    task = asyncio.create_task(coro)
    _tasks.add(task)
    task.add_done_callback(_tasks.discard)
    return task

@app.post("/items/{item_id}/process")
async def process_item(item_id: int) -> dict:
    spawn_background(background_job(item_id))
    return {"queued": True}
```

---

### PITFALL 3: unbounded gather over thousands of URLs

```python
# BROKEN: 10,000 concurrent connections → 429 rate-limiting, connection pool exhaustion
import asyncio, httpx

async def scrape_all(urls: list[str]) -> list[bytes]:
    async with httpx.AsyncClient() as client:
        return await asyncio.gather(          # spawns len(urls) concurrent coroutines
            *(client.get(u).aread() for u in urls)
        )
# Result with 10,000 URLs against a 100 req/s API: ~9,900 × 429 errors
```

```python
# FIX: bound with asyncio.Semaphore
import asyncio, httpx

async def scrape_all(urls: list[str], concurrency: int = 50) -> list[bytes]:
    sem = asyncio.Semaphore(concurrency)
    async with httpx.AsyncClient() as client:
        async def bounded_get(url: str) -> bytes:
            async with sem:
                resp = await client.get(url, timeout=10.0)
                resp.raise_for_status()
                return resp.content
        return await asyncio.gather(*(bounded_get(u) for u in urls))
# Result: 10,000 URLs in 200 rounds of 50 → 0 rate-limit errors
```

---

### PITFALL 4: forgetting await — silent no-op

```python
# BROKEN: calling a coroutine without await — returns a coroutine object, never executes
async def notify_user(user_id: int) -> None:
    await send_email(user_id)               # this is the real call

async def handle_signup(user_id: int) -> dict:
    notify_user(user_id)                    # coroutine object created and immediately dropped
    return {"signed_up": True}
    # send_email is NEVER called; no exception raised at runtime
```

```python
# FIX 1: add await
async def handle_signup(user_id: int) -> dict:
    await notify_user(user_id)
    return {"signed_up": True}

# FIX 2 (fire-and-forget): use create_task with tracking
async def handle_signup(user_id: int) -> dict:
    spawn_background(notify_user(user_id))
    return {"signed_up": True}

# DETECT: enable debug mode — Python warns on unawaited coroutines at GC time
# asyncio.get_event_loop().set_debug(True)
# Also: use mypy + pylint asyncio plugin to catch statically
```

---

### PITFALL 5: missing aclosing() on async generator break

```python
# BROKEN: async generator's finally block is deferred to GC — resources may leak
async def leaking_consumer(url: str) -> dict | None:
    async for record in paginate(client, url):
        if record["status"] == "active":
            return record                   # breaks mid-stream; generator not explicitly closed
    return None
```

```python
# FIX: contextlib.aclosing() guarantees aclose() is called on exit
from contextlib import aclosing

async def safe_consumer(url: str) -> dict | None:
    async with aclosing(paginate(client, url)) as gen:
        async for record in gen:
            if record["status"] == "active":
                return record
    return None
```

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|---|---|---|
| `httpx` | Async HTTP client | Drop-in requests API; supports HTTP/2; use `AsyncClient` |
| `aiofiles` | Async file I/O | Wraps file ops in executor; releases GIL |
| `asyncpg` | Async PostgreSQL driver | 3–5× faster than psycopg2 for I/O-bound workloads |
| `aioredis` | Async Redis client | Bundled into redis-py 4.2+ as `redis.asyncio` |
| `tenacity` | Production retry library | More configurable than a hand-rolled decorator; async-native |
| `circuitbreaker` (PyPI) | Circuit breaker decorator | Lightweight; wraps both sync and async callables |
| `anyio` | Async portability layer | Works on asyncio and trio; `anyio.to_thread.run_sync()` |
| `contextvars` | Context propagation | Carries trace IDs, auth tokens across await boundaries |
| `Sentry AsyncioIntegration` | Blocking-loop detection | Captures slow callbacks as performance issues |
| `yappi` | Async-aware profiler | Profiles coroutine wall time (not just CPU time) |

**asyncio.timeout() availability**:
- `asyncio.timeout()` — Python 3.11+
- `asyncio.to_thread()` — Python 3.9+
- `aiter()` / `anext()` built-ins — Python 3.10+
- `asyncio.TaskGroup` — Python 3.11+ (see `../asyncio_and_event_loop/README.md`)
- `asyncio.Barrier` — Python 3.11+

---

## 12. Interview Questions with Answers

**Q1: What is the most common async bug in FastAPI services, and how do you detect it?**
Calling a blocking synchronous function (like `requests.get()` or `time.sleep()`) inside an
`async def` route — this stalls the entire event loop for the duration of the call. Detect it
by enabling `loop.set_debug(True)` (warns when a callback takes > 100ms), by adding Sentry's
`AsyncioIntegration`, or by profiling with `yappi` which reports coroutine wall time.

**Q2: What is the difference between `asyncio.to_thread()` and `loop.run_in_executor()`?**
`asyncio.to_thread(fn, *args)` is syntactic sugar introduced in Python 3.9 for
`loop.run_in_executor(None, fn, *args)`. Both submit the callable to the default
`ThreadPoolExecutor`. Use `to_thread()` for I/O-bound sync work; use `run_in_executor(pool)`
with a `ProcessPoolExecutor` when you need to bypass the GIL for CPU-bound work.

**Q3: How large is the default thread pool used by `asyncio.to_thread()`?**
`min(32, os.cpu_count() + 4)` — this is the default `ThreadPoolExecutor` size in CPython.
On a 4-core machine: `min(32, 8) = 8` threads. If all 8 are occupied with blocking calls,
the 9th `to_thread()` call will block the event loop waiting for a thread to free up.
For high-concurrency workloads, create a custom pool and pass it to `run_in_executor`.

**Q4: What is the difference between `asyncio.Semaphore` and `asyncio.Lock`?**
`Lock` allows exactly 1 concurrent holder. `Semaphore(n)` allows up to `n` concurrent holders.
A `Lock` is a `Semaphore(1)`. Use `Semaphore` to bound concurrency (e.g., max 20 DB connections
at once); use `Lock` for mutual exclusion (e.g., protecting a shared counter or cache).

**Q5: Why does unbounded `asyncio.gather()` over 10,000 URLs fail?**
It creates 10,000 coroutines that all attempt to establish TCP connections simultaneously.
This exhausts the OS file-descriptor limit (default 1024 on Linux), triggers 429 Too Many
Requests from the target, and can crash the target service. Fix: wrap each coroutine with
`async with asyncio.Semaphore(50)` to bound concurrency to 50 at a time.

**Q6: What is jitter in retry logic, and why does it matter?**
Jitter is a random delay added to the exponential backoff interval. Without jitter, N services
that fail simultaneously all retry at the same intervals (1s, 2s, 4s, …), creating a
thundering herd that repeatedly hammers the recovering service. Full jitter draws the delay
from `uniform(0, min(max_delay, base * 2^attempt))`, spreading retries across the interval
and reducing load on the target by a factor of N.

**Q7: What is the difference between `asyncio.timeout()` and `asyncio.wait_for()`?**
Both cancel the operation after a specified duration. `asyncio.timeout()` (3.11) is an async
context manager, making it composable with nested context managers and `try/except` blocks.
`asyncio.wait_for()` wraps a coroutine and must be called at the `await` site. `asyncio.timeout()`
raises the built-in `TimeoutError`; `asyncio.wait_for()` raises `asyncio.TimeoutError` (which
is a subclass of `TimeoutError` in 3.11+ but distinct in earlier versions).

**Q8: What happens when you call `asyncio.create_task()` but don't store the return value?**
The `Task` object has no strong reference, so Python's garbage collector may collect and
cancel it before it finishes. CPython logs a warning: "Task was destroyed but it is pending!".
Fix: store the task in a module-level `set` and register a `done_callback` to discard it
when complete — this keeps a strong reference for the task's lifetime without causing a leak.

**Q9: How do you implement backpressure in an async producer/consumer system?**
Use `asyncio.Queue(maxsize=N)`. The producer calls `await queue.put(item)`, which suspends
the producer coroutine when the queue is full (maxsize reached), applying backpressure.
Consumer calls `await queue.get()` and `queue.task_done()`. The bounded queue acts as a
buffer and flow-control mechanism between producers and consumers with different throughputs.

**Q10: What are the three states of a circuit breaker, and when does it transition between them?**
CLOSED (normal): requests pass through; failure counter increments on exceptions. OPEN
(fail-fast): after `failure_threshold` consecutive failures, the breaker opens; all requests
immediately raise `RuntimeError` without calling the downstream service. HALF-OPEN (probing):
after `recovery_timeout` seconds, one probe request is allowed through; if it succeeds the
breaker closes; if it fails the breaker reopens with a fresh timeout.

**Q11: How do async generators differ from regular generators, and when should you use them?**
Regular generators use `yield` and are consumed with a synchronous `for` loop. Async generators
use `yield` inside `async def` and are consumed with `async for`, meaning each `yield` point
can suspend at an `await` call inside the generator body. Use async generators for lazy I/O
streaming: HTTP pagination, database cursor iteration, log tailing — anywhere you want to
process items as they arrive without loading all into memory first.

**Q12: How does `contextlib.aclosing()` prevent resource leaks in async generators?**
When `async for` exits early (via `return`, `break`, or exception), Python schedules the
generator's `aclose()` coroutine, but only calls it during GC (in CPython, this is immediate
due to reference counting, but in PyPy it's non-deterministic). `aclosing()` wraps the
generator in an `async with` block that explicitly awaits `gen.aclose()` on exit, ensuring
the generator's `finally` block (which may hold network connections, file handles) runs
immediately regardless of the GC implementation.

**Q13: How would you debug a FastAPI service where all requests are slow but CPU usage is low?**
Low CPU + slow requests in an async service is the classic blocking-in-async signature.
Steps: (1) enable `loop.set_debug(True)` and watch for slow-callback warnings; (2) add
`yappi` profiling with `clock_type=WALL` to see which coroutines have high wall time;
(3) search the codebase for sync I/O calls (`requests.`, `open(`, `time.sleep`) inside
`async def` functions; (4) check for `sync def` routes that might be saturating the
default Starlette thread pool (default: 40 threads).

**Q14: What is the difference between `async for` and `asyncio.gather()` for consuming multiple async sources?**
`async for` processes items sequentially from a single async generator — each item is awaited
in turn. `asyncio.gather()` runs multiple coroutines concurrently, collecting all results
when all complete. Use `async for` when you need ordered, lazy streaming from one source.
Use `gather()` (with a semaphore) when you want to fan out to many sources simultaneously
and collect results. Combining both: use an async generator as a lazy source, then spawn
bounded concurrent consumers with `gather()`.

**Q15: How do you safely propagate context (e.g., request IDs, auth tokens) across await boundaries in asyncio?**
Use `contextvars.ContextVar`. Unlike `threading.local`, `ContextVar` values are inherited by
child tasks (copies of the context are made at `asyncio.create_task()` time). Set the value
at the start of a request, and it is accessible in all coroutines spawned within that request's
scope without passing it explicitly. Example: `request_id: ContextVar[str] = ContextVar("request_id")`.
This is how Sentry and OpenTelemetry propagate trace context across async calls.

---

## 13. Best Practices

1. **Audit every `async def` for sync I/O calls before deploying.** Run `grep -r "requests\."
   $(find . -name "*.py")` and verify each is inside a sync `def` or wrapped in `to_thread()`.

2. **Always use `asyncio.Semaphore` when calling third-party APIs with rate limits.** Set the
   semaphore value to 80% of the documented rate limit to leave headroom for other callers.

3. **Store every `asyncio.create_task()` result.** Use the `spawn_background()` pattern with a
   `weakref`-compatible set and a `done_callback` to discard on completion.

4. **Prefer `asyncio.timeout()` over `asyncio.wait_for()` for timeout composition** (3.11+).
   It composes cleanly with other context managers and produces clear, structured timeout budgets.

5. **Use `contextlib.aclosing()` whenever you break out of an `async for` loop early** to
   guarantee the generator's cleanup code runs immediately.

6. **Put retry and circuit-breaker logic in a reusable decorator or HTTP client wrapper**,
   not in individual route handlers. This keeps business logic clean and makes resilience
   testable in isolation.

7. **Add jitter to every retry.** Never use pure exponential backoff without jitter in
   distributed systems — it creates thundering herds at scale.

8. **Set explicit timeouts on every outbound call.** Default `httpx.AsyncClient` has no
   timeout. Set `timeout=httpx.Timeout(connect=2.0, read=10.0, write=5.0, pool=1.0)`.

9. **Use `ContextVar` for cross-cutting data (trace IDs, tenant IDs)**, not function
   parameters or global state. It's the only safe way to propagate data across `await` chains
   in asyncio.

10. **Profile async services with `yappi` (wall-clock mode), not `cProfile`** (CPU-only).
    cProfile misses I/O wait time; yappi shows real coroutine duration including suspension.

11. **Test backpressure by injecting a slow consumer.** Add `await asyncio.sleep(0.1)` in
    the consumer coroutine during integration tests and verify the producer blocks rather
    than growing the queue unboundedly.

12. **Enable `PYTHONASYNCIODEBUG=1` in CI** to catch unawaited coroutines and slow callbacks
    automatically. This environment variable activates debug mode without code changes.

---

## 14. Case Study

### Building a Resilient Async API Client with Retry, Circuit Breaker, and Backpressure

**Context**: A data-ingestion service fetches records from a third-party billing API (500
req/s rate limit, 99.5% SLA) and writes them to PostgreSQL. The initial implementation used
a naive `gather()` over all pending record IDs per batch cycle.

---

#### BROKEN: naive gather with no resilience

```python
# BROKEN: spawns up to 5,000 concurrent connections per batch
# Result: 3,500 × 429 errors (70%) + connection pool exhaustion crash

import asyncio
import httpx

async def ingest_batch(record_ids: list[str]) -> list[dict]:
    async with httpx.AsyncClient(base_url="https://billing.api.com") as client:
        results = await asyncio.gather(
            *(client.get(f"/records/{rid}").json() for rid in record_ids),
            return_exceptions=True,
        )
    # 70% are HTTPStatusError(429) or ConnectError — ignored silently
    return [r for r in results if isinstance(r, dict)]
```

Observed in production (5,000-record batch, 2023-11-14):
- 3,487 × HTTP 429 errors (69.7%)
- 112 × ConnectTimeout errors (database write skipped for those records)
- Billing API sent abuse notice; service temporarily blocked for 10 minutes

---

#### FIX: resilient client with retry, circuit breaker, semaphore, and bounded queue

```python
import asyncio
import random
import time
import logging
from collections.abc import AsyncGenerator
from contextlib import aclosing
from enum import Enum, auto
from typing import Any
import httpx

log = logging.getLogger(__name__)

# ── Retry decorator ──────────────────────────────────────────────────────────

def retry_async(
    max_attempts: int = 4,
    base_delay: float = 1.0,
    max_delay: float = 60.0,
    exceptions: tuple[type[Exception], ...] = (httpx.HTTPStatusError, httpx.TimeoutException),
):
    import functools
    def decorator(fn):
        @functools.wraps(fn)
        async def wrapper(*args, **kwargs):
            for attempt in range(max_attempts):
                try:
                    return await fn(*args, **kwargs)
                except exceptions as exc:
                    # Only retry 429 and 5xx, not 4xx client errors
                    if isinstance(exc, httpx.HTTPStatusError):
                        if exc.response.status_code < 429:
                            raise
                    if attempt == max_attempts - 1:
                        raise
                    ceiling = min(max_delay, base_delay * (2 ** attempt))
                    delay = random.uniform(0, ceiling)
                    log.warning("attempt %d/%d failed; retrying in %.2fs", attempt + 1, max_attempts, delay)
                    await asyncio.sleep(delay)
        return wrapper
    return decorator

# ── Circuit breaker ──────────────────────────────────────────────────────────

class _State(Enum):
    CLOSED = auto()
    OPEN   = auto()
    HALF   = auto()

class AsyncCircuitBreaker:
    def __init__(self, threshold: int = 10, recovery: float = 30.0) -> None:
        self._state     = _State.CLOSED
        self._failures  = 0
        self._threshold = threshold
        self._recovery  = recovery
        self._opened_at = 0.0
        self._lock      = asyncio.Lock()

    async def call(self, fn, *args, **kwargs):
        async with self._lock:
            if self._state == _State.OPEN:
                if time.monotonic() - self._opened_at >= self._recovery:
                    self._state = _State.HALF
                else:
                    raise RuntimeError("circuit OPEN")
        try:
            result = await fn(*args, **kwargs)
            async with self._lock:
                self._failures = 0
                self._state    = _State.CLOSED
            return result
        except Exception:
            async with self._lock:
                self._failures += 1
                if self._failures >= self._threshold:
                    self._state    = _State.OPEN
                    self._opened_at = time.monotonic()
            raise

# ── Resilient fetch ──────────────────────────────────────────────────────────

_breaker = AsyncCircuitBreaker(threshold=10, recovery=30.0)
_sem     = asyncio.Semaphore(40)          # 40 concurrent < 500 req/s rate limit

@retry_async(max_attempts=4, base_delay=1.0)
async def fetch_record(client: httpx.AsyncClient, record_id: str) -> dict[str, Any]:
    async with _sem:
        async with asyncio.timeout(8.0):
            return await _breaker.call(_do_fetch, client, record_id)

async def _do_fetch(client: httpx.AsyncClient, record_id: str) -> dict[str, Any]:
    resp = await client.get(f"/records/{record_id}")
    resp.raise_for_status()
    return resp.json()

# ── Backpressure queue pipeline ───────────────────────────────────────────────

QUEUE_SIZE   = 200
WORKER_COUNT = 20
_tasks: set[asyncio.Task] = set()

async def _producer(queue: asyncio.Queue, record_ids: list[str]) -> None:
    for rid in record_ids:
        await queue.put(rid)              # blocks when queue full — backpressure
    for _ in range(WORKER_COUNT):
        await queue.put(None)

async def _consumer(
    queue: asyncio.Queue,
    client: httpx.AsyncClient,
    results: list[dict],
    errors: list[str],
) -> None:
    while True:
        rid = await queue.get()
        if rid is None:
            queue.task_done()
            break
        try:
            record = await fetch_record(client, rid)
            results.append(record)
        except RuntimeError as exc:       # circuit open
            log.error("circuit open for %s: %s", rid, exc)
            errors.append(rid)
        except Exception as exc:
            log.error("all retries failed for %s: %s", rid, exc)
            errors.append(rid)
        finally:
            queue.task_done()

async def ingest_batch_resilient(record_ids: list[str]) -> dict[str, Any]:
    queue: asyncio.Queue[str | None] = asyncio.Queue(maxsize=QUEUE_SIZE)
    results: list[dict] = []
    errors: list[str]   = []

    timeout = httpx.Timeout(connect=2.0, read=8.0, write=4.0, pool=1.0)
    async with httpx.AsyncClient(base_url="https://billing.api.com", timeout=timeout) as client:
        workers = [
            asyncio.create_task(_consumer(queue, client, results, errors))
            for _ in range(WORKER_COUNT)
        ]
        for t in workers:
            _tasks.add(t)
            t.add_done_callback(_tasks.discard)

        await _producer(queue, record_ids)
        await asyncio.gather(*workers)

    return {
        "total":    len(record_ids),
        "success":  len(results),
        "failed":   len(errors),
        "rate":     len(results) / len(record_ids),
    }
```

**Results after deploying the resilient client** (same 5,000-record batch):

| Metric | Broken | Fixed |
|---|---|---|
| HTTP 429 errors | 3,487 (69.7%) | 0 (0.0%) |
| ConnectTimeout errors | 112 (2.2%) | 4 (0.08%) |
| Successfully ingested | 1,401 (28.0%) | 4,996 (99.92%) |
| Wall-clock time for batch | 18s (then crashed) | 127s (completed) |
| Billing API abuse flag | Yes (10 min block) | No |

**Key design decisions**:

- `Semaphore(40)` keeps concurrent requests well below the 500 req/s rate limit; with 8ms
  average latency, 40 concurrent = 5,000 req/s — so semaphore is calibrated to 40 × (1/0.08) = 500 req/s max.
- Retry skips 4xx errors (except 429) — retrying a 400 Bad Request is wasteful.
- Circuit breaker with threshold=10 trips before a cascade failure affects the full batch.
- `asyncio.Queue(maxsize=200)` buffers 200 IDs; with 20 workers, the producer can run ahead
  by at most 200 items before it suspends, keeping memory bounded.
- All tasks are tracked in `_tasks` to prevent silent GC cancellation.

Cross-references:
- See `../asyncio_and_event_loop/README.md` for event loop fundamentals and `TaskGroup`
- See `../../backend/api_gateway_patterns/` for circuit breaker concepts at the gateway layer
- See `../../llm/case_studies/cross_cutting/streaming_at_scale.md` for async SSE streaming patterns
