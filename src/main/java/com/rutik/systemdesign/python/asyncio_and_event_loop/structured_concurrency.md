# Structured Concurrency in Python

Deep-dive sub-file extending [asyncio & Event Loop](../README.md).

---

## 1. Concept Overview

Structured concurrency is a programming paradigm that constrains the lifetime of concurrent tasks
to a well-defined lexical scope. Every task spawned inside a scope must complete — or be cancelled —
before the scope exits. The scope is called a **nursery** (Trio) or **task group** (asyncio, anyio).

The core guarantee: a parent coroutine never advances past the closing brace of its concurrency
scope until every child task has either returned, raised, or been cancelled. No task can outlive its
creator's scope. This eliminates the entire class of bugs associated with fire-and-forget tasks.

Python implementations:

| Library | API | Minimum Python |
|---------|-----|---------------|
| `trio` | `trio.open_nursery()` | 3.8 |
| `anyio` | `anyio.create_task_group()` | 3.8 |
| `asyncio` | `asyncio.TaskGroup` | 3.11 |

Python version scope: this sub-file targets Python 3.11 and 3.12. All examples use `X | None`
union syntax, `asyncio.TaskGroup`, `asyncio.timeout()`, and `ExceptionGroup`.

---

## 2. Intuition

> A task group is like a project sprint: every ticket (task) must be resolved before the sprint
> closes, and if one ticket blocks the whole sprint, all others are cancelled so the team can
> regroup.

**Mental model.** Unstructured concurrency lets you launch tasks like fireworks — you light the
fuse, they fly off, and you have no guarantee they land safely. Structured concurrency gives every
task a container: the container does not dissolve until every task inside it has finished or been
deliberately cancelled.

**Why it matters.** In production code the most common source of memory leaks, hung connections,
and silent data loss in async Python is `asyncio.create_task()` without tracking: a background
task raises an exception, the exception is swallowed by the default `Task` exception handler, and
the caller has no idea. `asyncio.TaskGroup` makes this impossible at the language level.

**Key insight.** Structured concurrency is not just a convenience wrapper around `gather` — it is
a fundamental shift in ownership. Under `TaskGroup`, every task has one clear owner (the group),
the group has one clear owner (the enclosing coroutine), and exception handling, cancellation, and
cleanup propagate bottom-up through this ownership tree, exactly like stack unwinding in synchronous
code.

---

## 3. Core Principles

**Lexical scope = task lifetime.** A task group is an async context manager. All tasks created
inside it must finish before the `async with` block exits. This gives tasks the same "you can
reason about their lifetime from the code structure" guarantee that local variables have.

**Exception propagation.** If any task raises an unhandled exception, the task group immediately
cancels all remaining tasks, then re-raises. When multiple tasks fail simultaneously, all exceptions
are bundled into an `ExceptionGroup` [3.11]. The caller can inspect each sub-exception via
`except*` syntax.

**Cancellation propagation.** When the outer task is cancelled (e.g., request timeout, client
disconnect), `CancelledError` propagates into the task group, which cancels every child task. There
is no way for a child to outlive a cancelled parent — cancellation flows down the ownership tree.

**No implicit fire-and-forget.** There is no `detach()` or `daemonize()` on a `TaskGroup` task.
If you want a long-lived background task, it must be created in a scope whose lifetime matches the
background task's intended lifetime (e.g., application lifespan, not request scope).

**Fail-fast semantics.** Task groups default to fail-fast: the first exception cancels siblings.
This avoids the half-success, half-failure states that `gather(return_exceptions=True)` can produce
silently.

---

## 4. Types / Architectures / Strategies

### 4.1 asyncio.TaskGroup [3.11]

The standard library implementation. Fail-fast by design. Raises `ExceptionGroup` when one or more
tasks fail.

```python
import asyncio

async def main() -> None:
    async with asyncio.TaskGroup() as tg:
        task_a = tg.create_task(fetch_user(1))
        task_b = tg.create_task(fetch_user(2))
    # Both tasks completed here; results accessible via task_a.result()
    print(task_a.result(), task_b.result())
```

### 4.2 anyio.create_task_group()

Cross-backend API that works identically on asyncio and Trio. The recommended choice for library
code that should not couple to a specific backend.

```python
import anyio

async def main() -> None:
    async with anyio.create_task_group() as tg:
        tg.start_soon(fetch_user, 1)
        tg.start_soon(fetch_user, 2)
```

`tg.start_soon()` schedules the coroutine but does not return a handle. Use
`tg.start(coroutine)` (awaitable) when you need to wait for the task to signal readiness before
the scope continues.

### 4.3 trio.open_nursery() — the original

The nursery pattern was invented in Trio by Nathaniel J. Smith (2018). asyncio TaskGroup and anyio
both implement the same semantics. Trio's nursery has one extension not present in asyncio:
`cancel_scope.shield`, which protects a block from external cancellation.

### 4.4 Cancellation Scopes

anyio provides `CancelScope`, a primitive for deadline-based cancellation that composes with
task groups:

```python
import anyio

async def with_deadline() -> None:
    with anyio.CancelScope(deadline=anyio.current_time() + 5.0) as scope:
        await long_operation()
    if scope.cancelled_caught:
        # Deadline was hit; handle gracefully
        ...
```

Convenience wrappers:
- `anyio.move_on_after(seconds)` — scope exits silently when deadline passes
- `anyio.fail_after(seconds)` — scope raises `TimeoutError` when deadline passes

asyncio [3.11] provides the equivalent with `asyncio.timeout()`:

```python
async def with_timeout() -> None:
    try:
        async with asyncio.timeout(5.0):
            await long_operation()
    except TimeoutError:
        ...
```

### 4.5 gather vs TaskGroup comparison

| Dimension | `asyncio.gather` | `asyncio.TaskGroup` [3.11] |
|-----------|-----------------|--------------------------|
| Exception handling | Cancels others by default; `return_exceptions=True` swallows all | Always fail-fast; `ExceptionGroup` wraps multiple failures |
| Return values | Returns list in call order | Via `task.result()` on each handle |
| Cancellation of group | No built-in scope; must cancel individually | Outer task cancel propagates to all children |
| Task lifetime guarantee | None — tasks can outlive their launch site | Guaranteed: scope exit = all tasks done |
| Multiple exceptions | `gather` loses all but the first (without `return_exceptions`) | All captured in `ExceptionGroup` |
| Python version | 3.4+ | 3.11+ |
| Recommended for | Simple parallel calls in 3.8–3.10 code | All new 3.11+ code |

---

## 5. Architecture Diagrams

### Structured vs Unstructured Task Lifetime

```
UNSTRUCTURED (create_task)
───────────────────────────────────────────────────
Caller:  [====== run =======>]  <-- exits here
Task A:  [============ still running =========>]
Task B:  [== done ==]
Task C:  [======== RAISED EXCEPTION (lost) ===>]
                                ^
                       caller never sees this

STRUCTURED (TaskGroup)
───────────────────────────────────────────────────
TaskGroup scope:  [══════════════════════════]  <-- waits
Task A:           [===== done ============]
Task B:           [== done ==]
Task C:           [== RAISED ===]
                              ^
                   cancels A; ExceptionGroup raised
                   to TaskGroup owner; scope exits
Caller:                                        [resumes with exception or clean exit]
```

### Exception Group Hierarchy

```
ExceptionGroup("unhandled errors in TaskGroup", [
    ValueError("invalid user id 99"),
    ConnectionError("upstream timeout on shard 3"),
])

    ExceptionGroup
    ├── ValueError
    └── ConnectionError

Handled with:
    try:
        async with asyncio.TaskGroup() as tg:
            ...
    except* ValueError as eg:
        for e in eg.exceptions:
            log.error("Validation error: %s", e)
    except* ConnectionError as eg:
        for e in eg.exceptions:
            log.error("Connection error: %s", e)
```

### Cancellation Propagation Tree

```
Request Handler (asyncio Task)
│
└── TaskGroup scope
    ├── fetch_user()      Task
    ├── fetch_permissions() Task
    └── fetch_config()    Task

Client disconnects → request handler receives CancelledError
→ TaskGroup.__aexit__ catches it
→ cancels fetch_user, fetch_permissions, fetch_config
→ waits for each to acknowledge cancellation
→ re-raises CancelledError up to uvicorn
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 TaskGroup Internals (CPython 3.11)

`asyncio.TaskGroup` is implemented in `Lib/asyncio/taskgroups.py`. On `__aexit__`:

1. If an exception occurred in the body (before task launch), cancel all tasks and wait.
2. Wait for all tasks: `await asyncio.wait(self._tasks)` inside an inner cancel scope.
3. Collect exceptions from completed tasks via `task.exception()`.
4. If any tasks raised, build an `ExceptionGroup` and raise it.
5. If the group itself was cancelled externally, re-raise `CancelledError`.

The internal wait uses `asyncio.wait` with `return_when=ALL_COMPLETED` in a loop that re-cancels
stragglers if a new exception arrives while waiting — ensuring fail-fast semantics even when a
second task fails after the first.

### 6.2 CancelledError and Shielding

`asyncio.CancelledError` is a subclass of `BaseException` (not `Exception`) since Python 3.8, so
bare `except Exception` does not accidentally catch it. Cancellation flows like this:

```
parent.cancel()
  └─ task.__step() raises CancelledError at current await point
     └─ if task is in TaskGroup: TaskGroup catches CancelledError
        └─ cancels all sibling tasks
        └─ waits for siblings
        └─ re-raises CancelledError
           └─ parent handles it (e.g., uvicorn closes connection)
```

To protect a critical section from cancellation (e.g., a database commit):

```python
import asyncio

async def safe_commit(session) -> None:
    # Shield prevents CancelledError from interrupting the commit.
    # If the outer task is cancelled, commit still completes;
    # CancelledError is delivered after shield exits.
    await asyncio.shield(session.commit())
```

`asyncio.shield` is a blunt instrument — it does not set a deadline. Prefer anyio's
`CancelScope(shield=True)` when you need to also set a timeout on the shielded block.

### 6.3 asyncio.timeout() vs asyncio.wait_for()

Both enforce a deadline but differ in composability:

```python
# asyncio.wait_for() [3.1+] — functional, wraps a single coroutine
# Problem: wraps only one coroutine; not composable with TaskGroup
result = await asyncio.wait_for(fetch_data(), timeout=2.0)

# asyncio.timeout() [3.11] — context manager; wraps any block including TaskGroup
# This is the modern approach
async with asyncio.timeout(2.0):
    async with asyncio.TaskGroup() as tg:
        tg.create_task(fetch_data())
        tg.create_task(fetch_metadata())
# If either task takes > 2 s total, TimeoutError is raised;
# TaskGroup cancels the other task automatically.
```

`wait_for` re-cancels the wrapped coroutine if cancellation arrives during timeout cleanup, which
can cause double-cancellation in some edge cases. `asyncio.timeout()` avoids this because it is
a proper cancel scope — it intercepts `CancelledError` and translates it to `TimeoutError` only
when it was the scope that triggered the cancellation, not the parent.

### 6.4 BROKEN → FIX: fire-and-forget task leaks

```python
from dataclasses import dataclass
import asyncio

@dataclass
class Item:
    id: int
    payload: str

async def process_item(item: Item) -> None:
    await asyncio.sleep(0.1)  # Simulate I/O
    if item.id == 3:
        raise ValueError(f"Bad item: {item.id}")
    print(f"Processed {item.id}")

# BROKEN: fire-and-forget create_task — exceptions are silently swallowed
async def process_batch_broken(items: list[Item]) -> None:
    for item in items:
        asyncio.create_task(process_item(item))  # Tasks outlive function; exceptions lost
    # Function returns immediately; tasks still running in the background.
    # If process_item raises, the default Task done callback logs a warning
    # to stderr but the caller never knows. The batch may be half-processed.

# FIX: TaskGroup ensures all tasks complete and exceptions propagate  [3.11]
async def process_batch(items: list[Item]) -> None:
    async with asyncio.TaskGroup() as tg:
        for item in items:
            tg.create_task(process_item(item))
    # Execution only reaches here when ALL tasks are done.
    # If any task raised, ExceptionGroup is raised to the caller.
    # If multiple tasks raised, all exceptions are in ExceptionGroup.exceptions.

# Calling code handles the exception group:
async def main() -> None:
    items = [Item(i, f"data_{i}") for i in range(5)]
    try:
        await process_batch(items)
    except* ValueError as eg:
        for exc in eg.exceptions:
            print(f"Validation failure: {exc}")
```

### 6.5 Fan-out with Early Termination

Use case: search across multiple backends; return as soon as any one succeeds.

```python
import asyncio
from collections.abc import Coroutine
from typing import Any, TypeVar

T = TypeVar("T")

class FirstResultFound(Exception):
    def __init__(self, result: Any) -> None:
        self.result = result

async def race(*coros: Coroutine[Any, Any, T]) -> T:
    """Return the result of the first coroutine to succeed."""
    outer_task = asyncio.current_task()
    assert outer_task is not None

    async def _wrapper(coro: Coroutine[Any, Any, T]) -> None:
        result = await coro
        # Signal success by raising into the task group.
        # All siblings will be cancelled.
        raise FirstResultFound(result)

    try:
        async with asyncio.TaskGroup() as tg:
            for coro in coros:
                tg.create_task(_wrapper(coro))
    except* FirstResultFound as eg:
        return eg.exceptions[0].result

    raise RuntimeError("All coroutines completed without a result")

# Usage:
async def search_all(query: str) -> dict:
    return await race(
        search_elastic(query),
        search_postgres(query),
        search_redis_cache(query),
    )
```

anyio provides `anyio.create_task_group()` with `tg.cancel_scope.cancel()` for a cleaner early-exit
pattern that avoids the sentinel-exception trick.

### 6.6 Parallel API Calls with Per-Task Timeout

```python
import asyncio
import httpx

async def fetch_with_timeout(
    client: httpx.AsyncClient,
    url: str,
    timeout: float,
) -> dict:
    async with asyncio.timeout(timeout):
        response = await client.get(url)
        response.raise_for_status()
        return response.json()

async def fetch_all(urls: list[str]) -> list[dict | BaseException]:
    results: dict[str, dict | BaseException] = {}
    async with httpx.AsyncClient() as client:
        async with asyncio.TaskGroup() as tg:
            tasks = {
                url: tg.create_task(
                    fetch_with_timeout(client, url, timeout=3.0),
                    name=f"fetch:{url}",
                )
                for url in urls
            }
    # All tasks complete (or group raises ExceptionGroup on failure)
    return [tasks[url].result() for url in urls]
```

---

## 7. Real-World Examples

**FastAPI request handler.** Every request spawns multiple I/O operations: DB query, cache lookup,
external enrichment API. A `TaskGroup` runs them in parallel and guarantees cleanup if the client
disconnects mid-request:

```python
from fastapi import FastAPI
import asyncio

app = FastAPI()

@app.get("/user/{user_id}")
async def get_user_profile(user_id: int) -> dict:
    async with asyncio.TaskGroup() as tg:
        user_task = tg.create_task(db.fetch_user(user_id))
        perms_task = tg.create_task(db.fetch_permissions(user_id))
        prefs_task = tg.create_task(cache.get_preferences(user_id))
    # All three completed; if any failed, ExceptionGroup propagates to 500 handler
    return {
        "user": user_task.result(),
        "permissions": perms_task.result(),
        "preferences": prefs_task.result(),
    }
```

**Celery-style worker with structured fanout.** A task processor fans out sub-tasks using a
`TaskGroup` so the worker cannot advance to `ack()` until all sub-tasks are done — preventing
partial processing acknowledgment:

```python
async def process_order(order_id: str) -> None:
    async with asyncio.TaskGroup() as tg:
        tg.create_task(update_inventory(order_id))
        tg.create_task(charge_payment(order_id))
        tg.create_task(send_confirmation_email(order_id))
    await queue.ack(order_id)  # Only reached when ALL sub-tasks succeeded
```

**aiohttp / httpx batch downloader.** Download N URLs with bounded concurrency using
`asyncio.Semaphore` inside a `TaskGroup`:

```python
import asyncio
import httpx

async def download_all(urls: list[str], max_concurrent: int = 20) -> list[bytes]:
    sem = asyncio.Semaphore(max_concurrent)
    results: dict[int, bytes] = {}

    async def _download(idx: int, url: str) -> None:
        async with sem:
            async with httpx.AsyncClient() as client:
                resp = await client.get(url)
                results[idx] = resp.content

    async with asyncio.TaskGroup() as tg:
        for i, url in enumerate(urls):
            tg.create_task(_download(i, url))

    return [results[i] for i in range(len(urls))]
```

**anyio in library code.** Libraries like httpx, FastAPI, and SQLAlchemy internally use anyio so
they work under both asyncio and Trio:

```python
import anyio

async def parallel_queries(queries: list[str]) -> list[list[dict]]:
    results: list[list[dict]] = [[] for _ in queries]

    async def _run(idx: int, q: str) -> None:
        results[idx] = await db.execute(q)

    async with anyio.create_task_group() as tg:
        for i, q in enumerate(queries):
            tg.start_soon(_run, i, q)

    return results
```

---

## 8. Tradeoffs

### asyncio.TaskGroup vs asyncio.gather

| Dimension | `gather` | `TaskGroup` |
|-----------|---------|-------------|
| Exception semantics | Cancels others by default; `return_exceptions=True` silently captures | Fail-fast always; `except*` for selective handling |
| Multiple exceptions | Only first raised (without `return_exceptions`) | All in `ExceptionGroup` |
| Task handles | Returns results in call order | `task.result()` on individual handles |
| Cancellation scope | None built-in | Outer task cancel propagates automatically |
| Composability with `timeout` | Must nest `wait_for` around `gather` | `async with asyncio.timeout(...)` wraps the whole group |
| Early exit pattern | Requires workaround | Raise sentinel exception to cancel siblings |
| Available since | 3.4 | 3.11 |
| Recommendation | Legacy 3.8–3.10 code | All new 3.11+ production code |

### asyncio vs anyio TaskGroup

| Dimension | `asyncio.TaskGroup` | `anyio.create_task_group()` |
|-----------|--------------------|-----------------------------|
| Backend | asyncio only | asyncio + Trio |
| `start_soon` semantics | `create_task` returns handle | `start_soon` does not return handle |
| `start()` for initialization | Not built-in | `await tg.start(coro)` — waits for `task_status.started()` |
| Cancel scope | `asyncio.timeout()` | `anyio.CancelScope`, `move_on_after`, `fail_after` |
| Shield | `asyncio.shield()` | `CancelScope(shield=True)` |
| Library compatibility | asyncio ecosystem only | httpx, FastAPI, SQLAlchemy |
| `ExceptionGroup` | Yes | Yes (anyio wraps Trio exceptions into ExceptionGroup) |

### Trio Nursery vs asyncio TaskGroup

| Feature | `trio.open_nursery()` | `asyncio.TaskGroup` |
|---------|----------------------|---------------------|
| Cancel scope as first-class primitive | Yes | Via `asyncio.timeout()` [3.11] |
| `nursery.start()` | Yes — waits for initialization | Not natively available |
| Strict structured concurrency | Yes — no way to detach | Yes — no detach |
| Ecosystem | Trio-only | asyncio (dominant ecosystem) |

---

## 9. When to Use / When NOT to Use

**Use `asyncio.TaskGroup` when:**
- Running 2+ I/O-bound coroutines that should all complete before proceeding.
- You need guaranteed exception propagation — no silent failures acceptable.
- Python 3.11+ is available (production is already 3.11 for most teams as of 2026).
- Cancellation of the outer task (e.g., HTTP request timeout) must propagate to all children.
- Building FastAPI endpoints that fan out to multiple services.

**Use `anyio.create_task_group()` when:**
- Writing library code that must run under both asyncio and Trio.
- You need `tg.start()` — the "wait for task to signal readiness" pattern used in server startup.
- You want `move_on_after` / `fail_after` cancel scopes without importing `asyncio` directly.

**Use `asyncio.gather` when:**
- Supporting Python 3.8–3.10 and cannot upgrade.
- You need `return_exceptions=True` and are deliberately handling partial failure.
- You have a small number of static coroutines known at call time.

**Do NOT use `asyncio.create_task` without tracking when:**
- You need to know if the task succeeded.
- The task performs mutations (DB writes, external calls) — partial execution is dangerous.
- The function that launches the task may be called in a request context that ends before the task.

**Do NOT use `TaskGroup` for persistent background workers.** A task group waits for all tasks
before the `async with` block exits. For an application-lifetime background task (e.g., a queue
consumer), create the task in a lifespan context manager, not inside a request handler's TaskGroup.

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
import asyncio

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Background worker lives for the application lifetime, not request lifetime
    async with asyncio.TaskGroup() as tg:
        tg.create_task(kafka_consumer_loop())
        yield  # Application serves requests here
        # On shutdown, TaskGroup cancels kafka_consumer_loop and waits

app = FastAPI(lifespan=lifespan)
```

---

## 10. Common Pitfalls

**Pitfall 1: Using `except Exception` to catch ExceptionGroup.**
`ExceptionGroup` is a subclass of `Exception`, so `except Exception as eg` catches it, but you
lose type-specific handling. Always use `except* SpecificError` [3.11] to handle each error type
selectively. `except Exception` catches the whole group as a single opaque blob.

```python
# WRONG: catches ExceptionGroup as a single opaque Exception
try:
    async with asyncio.TaskGroup() as tg:
        ...
except Exception as e:
    log.error("something failed: %s", e)  # Hides which tasks failed

# RIGHT: except* routes each exception type to the right handler
try:
    async with asyncio.TaskGroup() as tg:
        ...
except* ValueError as eg:
    for e in eg.exceptions:
        log.warning("Validation: %s", e)
except* httpx.HTTPStatusError as eg:
    for e in eg.exceptions:
        log.error("HTTP error: %s", e)
```

**Pitfall 2: Blocking the event loop inside a task.**
A task that calls `time.sleep()`, reads a large file with `open()`, or performs CPU-heavy work
blocks the entire event loop — all other tasks in the TaskGroup stall. The practical rule: never
call blocking I/O or CPU work inside an `async def` without offloading to a thread pool.

```python
import asyncio

# WRONG: blocks event loop for all tasks in the group
async def process_file(path: str) -> bytes:
    return open(path, "rb").read()  # Synchronous; blocks event loop

# RIGHT: offload to thread pool
async def process_file(path: str) -> bytes:
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(None, lambda: open(path, "rb").read())
```

**Pitfall 3: Misusing `asyncio.shield` in a TaskGroup.**
`asyncio.shield` prevents the inner coroutine from receiving `CancelledError`, but it does not
prevent the TaskGroup from being cancelled. The shielded task keeps running detached from the
original scope — effectively becoming a fire-and-forget task if the TaskGroup is cancelled. Use
`asyncio.shield` only for short critical sections (DB commits, cleanup), never for long operations.

**Pitfall 4: Creating TaskGroup tasks after the group body exits.**
`tg.create_task()` raises `RuntimeError: This TaskGroup is not active` if called after the `async
with` block exits. This happens when a callback or thread tries to submit work to a TaskGroup that
has already collected results. Cache task handles before the group closes.

**Pitfall 5: TaskGroup in wrong scope causes deadlock on shutdown.**
A FastAPI application that creates a `TaskGroup` inside an endpoint handler but then tries to
gracefully drain tasks on shutdown will deadlock if the shutdown signal arrives while a request
handler is blocked waiting for its TaskGroup. Always match TaskGroup lifetime to the corresponding
work scope (request → request handler, application → lifespan).

**Pitfall 6: anyio `start_soon` vs asyncio `create_task` return values.**
`anyio.TaskGroup.start_soon()` does not return a handle. There is no way to get the task's result
after the group exits with `start_soon`. If you need results, collect them via a shared list
(thread-safe within a single-threaded event loop) or use `tg.start()` with `task_status`.

```python
import anyio

async def collect_results(items: list[str]) -> list[dict]:
    results: list[dict] = []

    async def _fetch(item: str) -> None:
        data = await fetch(item)
        results.append(data)  # Safe: single-threaded asyncio event loop

    async with anyio.create_task_group() as tg:
        for item in items:
            tg.start_soon(_fetch, item)

    return results  # All tasks done; results fully populated
```

---

## 11. Technologies & Tools

| Tool | Role | Structured Concurrency API | Notes |
|------|------|---------------------------|-------|
| `asyncio` (stdlib) | Event loop + tasks | `asyncio.TaskGroup` [3.11], `asyncio.timeout` [3.11] | No external dependency; asyncio only |
| `anyio` 4.x | Backend-agnostic async | `anyio.create_task_group()`, `CancelScope`, `move_on_after` | Works on asyncio + Trio; recommended for libraries |
| `trio` 0.25 | Alternative async framework | `trio.open_nursery()`, `trio.CancelScope` | Origin of structured concurrency in Python; strict nursery model |
| `asyncio.TaskGroup` | Stdlib SC | Built-in | No pip install needed; Python 3.11+ |
| `taskiq` | Distributed task queue | Uses asyncio internals | Structured dispatch per worker cycle |
| `aiormq` / `aio-pika` | AMQP client | Per-message TaskGroup | Combine with TaskGroup for parallel message processing |
| `httpx` | HTTP client | Async client compatible with TaskGroup | httpx internally uses anyio |
| `uvicorn` / `hypercorn` | ASGI servers | Cancel on disconnect propagates to TaskGroup | Disconnect triggers `CancelledError` on the handler task |

---

## 12. Interview Questions with Answers

**Q: What is structured concurrency and how does it differ from unstructured concurrency?**
Structured concurrency means every spawned task has a well-defined scope: the scope does not exit
until all tasks inside it complete or are cancelled. Unstructured concurrency (bare `create_task`)
lets tasks outlive their launch site, silently swallowing exceptions and preventing reliable
cancellation. The difference is analogous to structured vs `goto`-based control flow in imperative
code.

**Q: Why is `asyncio.create_task()` dangerous without tracking?**
`create_task` schedules a coroutine as a Task but does not hold a reference to the Task object if
the caller discards it. Python's garbage collector can destroy the Task before it completes, and any
exception raised by the Task is only logged to stderr via a warning — it is never raised to the
caller. Use `TaskGroup` or explicitly store and await task handles to avoid this.

**Q: What does `asyncio.TaskGroup` do when one task raises an exception?**
It immediately schedules cancellation of all remaining tasks, waits for each to acknowledge
cancellation (`CancelledError`), then raises an `ExceptionGroup` containing all exceptions (the
original one plus any raised by tasks that failed before cancellation). If exactly one task failed,
the `ExceptionGroup` contains one exception.

**Q: What is `ExceptionGroup` and how do you handle it with `except*`?**
`ExceptionGroup` [3.11] is a new built-in that wraps multiple exceptions into a single value.
`except* ValueError as eg` catches all `ValueError` instances inside any nested `ExceptionGroup`
(recursively), giving them to you as `eg.exceptions` — a tuple of matching exceptions. Multiple
`except*` clauses can handle different types from the same group. Unlike regular `except`, `except*`
does not short-circuit after the first match; all clauses run.

**Q: How does `asyncio.timeout()` [3.11] differ from `asyncio.wait_for()`?**
`asyncio.timeout()` is a context manager (a cancel scope) that can wrap any block of code,
including a `TaskGroup`. `wait_for()` is a function that wraps a single coroutine and does not
compose well with `TaskGroup`. Additionally, `timeout()` correctly interacts with the cancellation
mechanism — it translates only its own cancellation to `TimeoutError`, not external cancellations,
avoiding double-cancel edge cases present in older `wait_for` implementations.

**Q: What happens to a `TaskGroup` when the outer task is cancelled (e.g., client disconnects)?**
`CancelledError` is delivered to the task currently awaiting inside the `TaskGroup`. The
`TaskGroup.__aexit__` handler catches this, cancels all child tasks, waits for them to finish, then
re-raises `CancelledError`. The result is that client disconnect reliably cleans up all in-flight
sub-tasks — database cursors, HTTP connections, locks — before the request coroutine exits.

**Q: What is `anyio.CancelScope` and when should you use it over `asyncio.timeout()`?**
`anyio.CancelScope` is anyio's cancel scope primitive. Use it when you need: (a) backend-agnostic
code (anyio supports both asyncio and Trio), (b) `scope.cancel()` to cancel a block from within
(early-exit pattern), (c) `scope.shield = True` to protect from external cancellation, or (d)
`move_on_after` / `fail_after` convenience wrappers. Use `asyncio.timeout()` in asyncio-only code
where anyio is not a dependency.

**Q: How do you run tasks with bounded concurrency using TaskGroup?**
Combine `asyncio.Semaphore` with `TaskGroup`. The semaphore limits how many tasks run concurrently
while the TaskGroup ensures all tasks complete before the scope exits. Do not use a separate
semaphore in conjunction with `gather`; that pattern does not propagate exceptions reliably.

**Q: What is the `tg.start()` pattern in anyio and when is it needed?**
`anyio.TaskGroup.start(coro)` is an awaitable that starts `coro` and waits until the coroutine
calls `task_status.started(value)`. This is the standard pattern for servers and background workers
that need to signal "I am ready and listening" before the caller proceeds. `asyncio.TaskGroup` does
not have a direct equivalent; the closest is using an `asyncio.Event` to synchronize readiness.

**Q: Why should `asyncio.shield` not be used for long operations?**
`asyncio.shield` prevents the inner coroutine from receiving `CancelledError`, but the outer
awaiter does receive it. If the outer task is cancelled, the awaiter exits immediately; the shielded
task continues running as a detached fire-and-forget task. This is exactly the unstructured
concurrency problem `TaskGroup` is designed to prevent. Use `shield` only for short, critical
cleanup operations (milliseconds, not seconds).

**Q: How do you detect that a `CancelScope` absorbed a cancellation in anyio?**
Check `scope.cancelled_caught` after the `with` block exits. If `True`, the scope's deadline or
explicit cancel absorbed a cancellation. Use this to implement fallback logic:

```python
with anyio.move_on_after(1.0) as scope:
    result = await slow_api_call()
if scope.cancelled_caught:
    result = default_value  # Deadline hit; use fallback
```

**Q: What is the difference between `start_soon` and `create_task` return values?**
`asyncio.TaskGroup.create_task()` returns an `asyncio.Task` handle; call `.result()` after the
group exits to get the return value. `anyio.TaskGroup.start_soon()` returns `None` — there is no
handle. To collect results with anyio, use a shared container (list, dict) mutated from inside
each task, which is safe because asyncio is single-threaded and tasks only run one at a time.

**Q: How does TaskGroup interact with `asyncio.timeout()` when both are nested?**
`asyncio.timeout()` creates a cancel scope that, when its deadline expires, cancels the current
task. If the current task is waiting inside a `TaskGroup`, the TaskGroup's `__aexit__` receives the
`CancelledError`, cancels all child tasks, waits for them, then re-raises `CancelledError`. The
`asyncio.timeout()` context manager then translates this specific `CancelledError` (from its own
deadline) into `TimeoutError`. The result: a single `TimeoutError` is raised to the caller with
all child tasks cleanly cancelled.

**Q: In production, how do you prevent a single slow downstream from holding up all tasks in a group?**
Apply `asyncio.timeout()` inside each individual task's coroutine, not around the entire
`TaskGroup`. Each task gets its own deadline. If one task times out, it raises `TimeoutError`
inside the group, which cancels all siblings — the caller gets an `ExceptionGroup` containing one
`TimeoutError`. If you want the group to complete even when one task times out, use
`return_exceptions`-style try/except inside each task and collect errors manually.

**Q: What changed in Python 3.11 that makes it the baseline for structured concurrency?**
Three features landed simultaneously: (1) `asyncio.TaskGroup` — the structured concurrency
primitive, (2) `asyncio.timeout()` — composable cancel scope, (3) `ExceptionGroup` and `except*` —
multi-exception handling syntax. All three are interdependent; `TaskGroup` raises `ExceptionGroup`,
which requires `except*` to handle properly. Backporting is possible via `exceptiongroup` on PyPI,
but the native 3.11 implementation is tightly integrated with the CPython C extension layer.

---

## 13. Best Practices

**Always use `TaskGroup` (or anyio equivalent) for parallel I/O in production code on 3.11+.**
Treat bare `asyncio.create_task` without tracking as a code smell in new code. The only legitimate
use of untracked `create_task` is for truly fire-and-forget work where failure is acceptable (e.g.,
telemetry span export), and even then prefer `asyncio.ensure_future` with an explicit error
callback.

**Match TaskGroup lifetime to work scope.** Request-scoped work goes in the request handler's
TaskGroup. Application-lifetime work (queue consumers, health-check loops) goes in the lifespan
context manager's TaskGroup. Mixing scopes causes subtle shutdown and exception-propagation bugs.

**Prefer `anyio.create_task_group()` in library code.** Applications can choose their event loop
backend; libraries must support both asyncio and Trio. Using anyio makes your library composable
with the entire Python async ecosystem.

**Use `asyncio.timeout()` instead of `asyncio.wait_for()` for all new code.** `asyncio.timeout()`
is composable, correct in cancellation edge cases, and can wrap arbitrary blocks. Reserve
`wait_for` only when targeting Python 3.10 or earlier.

**Handle `ExceptionGroup` with `except*` per exception type.** Map each exception type to a
specific recovery strategy. Log unexpected types and re-raise so they surface in your error
tracker. Never silently swallow an `ExceptionGroup` with `except Exception`.

**Use `asyncio.Semaphore` inside tasks, not outside the TaskGroup.** Placing the semaphore inside
each task's coroutine (as `async with sem`) is the correct pattern. Placing it outside the
TaskGroup startup loop does not limit concurrency — it only serializes task creation.

**Test cancellation explicitly.** Use `pytest-anyio` or `asyncio.wait_for(coro, timeout=0)` to
confirm your coroutines handle `CancelledError` cleanly: run cleanup, do not suppress the error,
release acquired resources. A task that does `except BaseException: pass` is broken; it swallows
cancellation and prevents the TaskGroup from exiting.

**Prefer `tg.start()` for background services in anyio.** When starting a background worker that
must be ready before the outer scope continues, use `await tg.start(my_worker)` with
`task_status.started()` in the worker. This avoids fragile `asyncio.Event` + `create_task`
synchronization patterns.

---

## 14. Case Study

### Parallel Enrichment Pipeline in FastAPI

**Scenario.** A B2B SaaS API receives a `POST /enrich-company` request with a company domain.
It must in parallel: (1) fetch firmographic data from Clearbit (~300 ms), (2) check the company's
tech stack from BuiltWith (~500 ms), (3) query an internal graph DB for connections (~150 ms). The
SLA is p99 < 800 ms. Prior implementation used sequential `await` calls, giving p99 of ~1100 ms.

**Problem.** The engineering team replaced sequential calls with `asyncio.gather` — latency dropped
to ~550 ms. However, production revealed two issues: when the Clearbit API returned HTTP 429, the
exception was silently swallowed by `gather(return_exceptions=True)`, and the response was returned
with `clearbit: None` (missing data), which downstream consumers treated as "no data found" rather
than a transient error. Additionally, when a request timed out at the Nginx layer (800 ms), the
three sub-requests continued running for their full duration, holding open three HTTP connections
for up to 500 ms after the client had disconnected.

**Solution with TaskGroup + asyncio.timeout().**

```python
import asyncio
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

class EnrichmentResult(BaseModel):
    domain: str
    firmographics: dict | None
    tech_stack: list[str] | None
    connections: list[dict] | None

async def fetch_clearbit(client: httpx.AsyncClient, domain: str) -> dict:
    resp = await client.get(
        f"https://company.clearbit.com/v2/companies/find?domain={domain}",
        headers={"Authorization": "Bearer CLEARBIT_KEY"},
    )
    resp.raise_for_status()  # httpx.HTTPStatusError on 4xx/5xx
    return resp.json()

async def fetch_builtwith(client: httpx.AsyncClient, domain: str) -> list[str]:
    resp = await client.get(f"https://api.builtwith.com/v21/api.json?KEY=...&LOOKUP={domain}")
    resp.raise_for_status()
    return [t["tag"] for t in resp.json().get("Results", [])]

async def fetch_graph_connections(domain: str) -> list[dict]:
    return await graph_db.query("MATCH (c:Company {domain: $d})-[:CONNECTED]->(p) RETURN p", d=domain)

@app.post("/enrich-company")
async def enrich_company(domain: str) -> EnrichmentResult:
    firmographics: dict | None = None
    tech_stack: list[str] | None = None
    connections: list[dict] | None = None

    async with httpx.AsyncClient(timeout=httpx.Timeout(6.0)) as client:
        # Total SLA: 700 ms (leaves 100 ms for serialization + overhead)
        try:
            async with asyncio.timeout(0.7):
                async with asyncio.TaskGroup() as tg:
                    firm_task = tg.create_task(fetch_clearbit(client, domain))
                    tech_task = tg.create_task(fetch_builtwith(client, domain))
                    conn_task = tg.create_task(fetch_graph_connections(domain))
            firmographics = firm_task.result()
            tech_stack = tech_task.result()
            connections = conn_task.result()

        except TimeoutError:
            # Overall deadline exceeded; return whatever completed
            # (TaskGroup cancelled all tasks; .result() raises on incomplete tasks)
            if not firm_task.cancelled():
                firmographics = firm_task.result() if not firm_task.exception() else None
            if not tech_task.cancelled():
                tech_stack = tech_task.result() if not tech_task.exception() else None
            if not conn_task.cancelled():
                connections = conn_task.result() if not conn_task.exception() else None

        except* httpx.HTTPStatusError as eg:
            for e in eg.exceptions:
                if e.response.status_code == 429:
                    raise HTTPException(status_code=503, detail="Rate limited by upstream provider")
                if e.response.status_code >= 500:
                    raise HTTPException(status_code=502, detail="Upstream provider error")
            raise

    return EnrichmentResult(
        domain=domain,
        firmographics=firmographics,
        tech_stack=tech_stack,
        connections=connections,
    )
```

**Results after migration.**
- p99 latency: 540 ms (down from 1,100 ms sequential; comparable to `gather` but with correct error semantics).
- Silent data-loss incidents: 0 (previously ~3 per week from swallowed `gather` exceptions).
- Connection leak on client disconnect: eliminated — `asyncio.timeout` triggers `CancelledError`,
  TaskGroup cancels all three sub-requests, `httpx.AsyncClient` closes the underlying connections
  in its `__aexit__`.
- Error budget: explicit `except* HTTPStatusError` maps upstream 429s to 503 (retriable), which
  the API gateway retries automatically — client sees a successful eventual response instead of a
  silent `None`.

**Key lesson.** `TaskGroup` + `asyncio.timeout()` is not faster than `gather` for the happy path.
The win is correctness: exceptions surface deterministically, cancellation propagates reliably, and
the code structure reflects the ownership model — the request handler owns the enrichment tasks,
and the enrichment tasks do not outlive the request.
