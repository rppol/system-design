# asyncio & Event Loop

---

## 1. Concept Overview

Python's `asyncio` is a single-threaded concurrency framework built on cooperative multitasking. A
coroutine voluntarily suspends itself at every `await` point, returning control to the event loop,
which then decides what to run next. The event loop uses the OS-level I/O multiplexing primitive
(`select`/`epoll`/`kqueue`) to watch file descriptors and timers, waking up exactly the coroutine
that has pending data, never burning a thread per connection.

Key abstractions:

| Abstraction | Role |
|---|---|
| Coroutine (`async def`) | Suspendable function; not scheduled until wrapped in a Task |
| Task (`asyncio.Task`) | Wraps a coroutine; schedules it on the running event loop |
| Future (`asyncio.Future`) | Low-level result placeholder; Task is a Future subclass |
| Event loop | Single-threaded scheduler; polls I/O, runs callbacks, drives Tasks |
| `asyncio.TaskGroup` | Structured concurrency scope; cancels siblings on any failure (3.11+) |

Python version scope: this module targets 3.11 and 3.12. All examples use `X | None` union syntax
and 3.11+ APIs (`TaskGroup`, `asyncio.timeout()`).

---

## 2. Intuition

> An async event loop is like a single chef in a kitchen: instead of waiting at each stove burner,
> the chef puts a pot on to boil, immediately moves to chop vegetables, and only comes back when the
> boiling water buzzes — doing many things without ever hiring extra staff.

**Mental model.** There is exactly one thread running your Python code at any instant. Every `await`
is a polite "I am blocked on I/O; please run someone else". The event loop is the maitre-d that
decides who gets to run next. No two coroutines run truly simultaneously; they interleave at
`await` boundaries.

**Why it matters.** A FastAPI application under `uvicorn` handles thousands of concurrent HTTP
connections on a single OS thread because every network read/write is an `await`. A
thread-per-request server would exhaust OS thread limits (~tens of thousands) at much lower
concurrency. Single-threaded cooperative scheduling eliminates the need for most locks.

**Key insight.** `async def` does not make code concurrent. Two coroutines only overlap if both are
wrapped in `asyncio.Task` objects (via `create_task`, `gather`, or `TaskGroup`). A bare `await
slow_coroutine()` is sequential — it suspends the caller but does not start any other work.

---

## 3. Core Principles

**Coroutine = generator with send().** Under the hood, `async def` creates a coroutine object that
behaves like a PEP 342 generator. `await expr` desugars to `yield from expr.__await__()`. The event
loop drives coroutines by calling `.send(None)` repeatedly; when the coroutine hits an `await` on a
not-yet-ready Future it yields that Future up to the loop, which registers the Future's callback
with the I/O poller and moves on.

**Event loop runs in a single thread.** It calls `select()`/`epoll_wait()`/`kqueue` with a timeout
equal to the nearest scheduled callback. Each iteration: (1) collect ready I/O events, (2) run all
ready callbacks, (3) run expired timers. A single iteration takes ~5–50 µs on idle; under load with
thousands of ready sockets it can stretch to low milliseconds.

**Tasks wrap coroutines and schedule them.** `asyncio.create_task(coro)` wraps the coroutine in a
`Task`, immediately schedules it on the running loop's ready queue, and returns the `Task`. The
coroutine does not execute until the current coroutine yields.

**`await` suspends the caller, not the loop.** When you `await future`, the current coroutine
suspends. If the future is already resolved the coroutine resumes on the next iteration with zero
OS syscalls. If it is pending, the event loop continues running other ready callbacks.

**Cancellation propagates via `CancelledError`.** `task.cancel()` schedules a `CancelledError`
injection at the coroutine's next `await`. `CancelledError` inherits from `BaseException`, not
`Exception`. Code that catches `Exception` will accidentally swallow it, silently preventing
cancellation.

**Structured concurrency requires that no Task outlive its scope.** `asyncio.TaskGroup` enforces
this: the `async with` block does not exit until every spawned Task has finished or been cancelled.
If any Task raises, the group cancels all remaining Tasks and re-raises all errors together as an
`ExceptionGroup`.

---

## 4. Types / Architectures / Strategies

### asyncio.gather()

Runs an arbitrary number of awaitables concurrently. Returns results as a list in input order.
With `return_exceptions=True`, failed awaitables return their exception object instead of
propagating. With `return_exceptions=False` (default), the first exception cancels remaining
awaitables and propagates.

```python
results: list[str] = await asyncio.gather(fetch(url1), fetch(url2), fetch(url3))
```

### asyncio.wait()

Lower-level than `gather`. Returns `(done: set[Task], pending: set[Task])`. Accepts
`return_when=asyncio.FIRST_COMPLETED | FIRST_EXCEPTION | ALL_COMPLETED`. Useful when you want to
process results as they arrive or cancel remaining work after the first success.

```python
done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
```

### asyncio.TaskGroup (3.11+)

Structured concurrency primitive. All tasks created inside the group are cancelled if any one
raises. All exceptions are collected into a single `ExceptionGroup`, allowing callers to handle
multiple simultaneous failures.

```python
async with asyncio.TaskGroup() as tg:
    t1 = tg.create_task(fetch(url1))
    t2 = tg.create_task(fetch(url2))
# both tasks are done (or group raised ExceptionGroup)
```

### asyncio.timeout() (3.11+)

Context manager that cancels the enclosed code block after a deadline. Replaces the older
`asyncio.wait_for()` pattern. Raises `asyncio.TimeoutError` (which is now a subclass of
`TimeoutError`, the built-in).

```python
async with asyncio.timeout(5.0):
    result = await slow_operation()
```

### Synchronization Primitives

- `asyncio.Lock` — mutual exclusion for shared state across coroutines
- `asyncio.Semaphore(n)` — limit concurrency to n at a time; essential for rate limiting
- `asyncio.Event` — one-shot notification between coroutines
- `asyncio.Queue(maxsize)` — bounded producer/consumer channel; `put()` blocks when full, providing
  backpressure

### Async Generators

`async def` functions that contain `yield` are async generators. They are consumed with `async for`
and can `await` inside the body. Useful for streaming results (e.g., paginated API responses, SSE).

```python
async def paginate(url: str) -> AsyncGenerator[dict, None]:
    page = 1
    while True:
        data = await fetch_page(url, page)
        if not data:
            return
        yield data
        page += 1
```

### anyio

Backend-agnostic async library. Runs on top of `asyncio` or `trio`. Provides `TaskGroup`,
`move_on_after()`, `fail_after()`, and a consistent cancellation model. FastAPI's dependency on
`anyio` means all FastAPI background tasks run under the anyio runtime.

---

## 5. Architecture Diagrams

### Event Loop Main Loop

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Start(["asyncio.run(main())"]) --> Create("Create loop<br/>run_until_complete(main)")
    Create --> S1

    subgraph Iteration["each iteration, ~5-50 µs on idle"]
        S1("1. epoll_wait<br/>collect ready I/O events") --> S2("2. resolve Futures<br/>schedule callbacks on _ready")
        S2 --> S3("3. run _ready callbacks<br/>Task.__step drives coro.send")
        S3 --> S4("4. run expired timers<br/>call_later callbacks")
    end

    S4 -.->|"repeat"| S1
    S4 -->|"no tasks remain"| Done(["loop exits"])

    class Start,Done io
    class Create base
    class S1 req
    class S2,S4 mathOp
    class S3 train
```
*Each iteration polls for ready I/O, resolves and runs the `_ready` callback queue, then fires expired timers before looping back — roughly 5–50 µs per cycle on an idle loop.*

### Coroutine State Machine

```mermaid
stateDiagram-v2
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    [*] --> PENDING: asyncio.create_task(coro)
    PENDING --> RUNNING: loop calls task.__step()
    RUNNING --> SUSPENDED: awaits a<br/>pending Future
    RUNNING --> DONE: returns value<br/>or raises
    SUSPENDED --> RUNNING: future resolves<br/>(I/O ready or timer)
    RUNNING --> CANCELLED: task.cancel() injects<br/>CancelledError at next<br/>suspension point
    DONE --> [*]
    CANCELLED --> [*]

    class PENDING base
    class RUNNING train
    class SUSPENDED req
    class DONE io
    class CANCELLED lossN
```
*A Task starts PENDING, moves to RUNNING once the loop drives it, and oscillates between RUNNING and SUSPENDED on every `await` until it reaches the terminal DONE or CANCELLED state.*

### TaskGroup Error Propagation

```mermaid
sequenceDiagram
    participant TG as TaskGroup
    participant A as Task A
    participant B as Task B
    participant C as Task C

    TG->>A: create_task(task_A)
    TG->>B: create_task(task_B)
    TG->>C: create_task(task_C)
    Note over A,C: all three running concurrently

    B-->>TG: raises ValueError
    Note over TG: notices the failure

    TG->>A: cancel()
    TG->>C: cancel()
    Note over A,C: run cleanup, then finish

    Note over TG: raises ExceptionGroup with<br/>all errors, at the async with exit
```
*When Task B raises, the TaskGroup cancels its siblings A and C, waits for their cleanup, then re-raises everything together as a single ExceptionGroup at the `async with` exit.*

---

## 6. How It Works — Detailed Mechanics

### How asyncio.run() Works Under the Hood

```python
import asyncio

# What asyncio.run(main()) actually does (simplified):
def run(coro):
    loop = asyncio.new_event_loop()        # 1. Create a fresh event loop
    asyncio.set_event_loop(loop)           # 2. Set as current for this thread
    try:
        return loop.run_until_complete(coro)  # 3. Drive loop until coro finishes
    finally:
        try:
            _cancel_all_tasks(loop)        # 4. Cancel any lingering Tasks
            loop.run_until_complete(loop.shutdown_asyncgens())
            loop.run_until_complete(loop.shutdown_default_executor())
        finally:
            asyncio.set_event_loop(None)
            loop.close()                   # 5. Release OS resources (epoll fd, etc.)
```

`asyncio.run()` is the correct entry point for production code. It always creates a **new** event
loop. Calling it inside an already-running loop raises `RuntimeError` because you cannot nest
`run_until_complete` calls.

### How `await` Works: yield from Under the Hood

```python
# These two blocks are semantically equivalent:

# High-level (Python 3.5+):
async def fetch(url: str) -> bytes:
    reader, writer = await asyncio.open_connection(url, 80)
    return await reader.read(1024)

# Low-level equivalent (illustrative — not actually how you write it):
def fetch_lowlevel(url: str):
    future = asyncio.open_connection(url, 80)
    # yield from suspends this generator and passes the Future to the caller
    reader, writer = yield from future.__await__()
    result_future = reader.read(1024)
    return (yield from result_future.__await__())
```

When a coroutine hits `await asyncio.sleep(1)`, `asyncio.sleep` creates a `Future`, registers a
`call_later(1, future.set_result, None)` timer, and yields the future up the chain. The event loop
receives the Future, notes its callback, and moves on to other Tasks. After 1 second the timer
fires, resolves the Future, and the original coroutine is re-queued on `_ready`.

### Turning the 5-50 microsecond iteration into a capacity number

Section 3 states one loop iteration costs roughly 5–50 µs. That figure is not trivia — it is the
entire capacity model of an asyncio service, and it converts into throughput by one division.

**The idea behind it.** "The loop is a single cashier. Its ceiling is not how long customers shop,
it is how many seconds it spends per customer at the register."

| Symbol | What it is |
|--------|------------|
| `t_step` | Loop CPU spent per task resumption — the 5–50 µs figure, pure Python overhead |
| `1 / t_step` | Ceiling on task-steps per second. The register's throughput |
| `L` | Wall-clock latency of one request, almost all of it I/O the loop does not pay for |
| `C` | Concurrent in-flight requests the single loop is carrying at any instant |
| `C = RPS x L` | Little's Law — the count of things in flight is arrival rate times how long each stays |

**Walk one example.** Take a mid-range `t_step` of 20 µs and a 100 ms downstream call:

```
  step-rate ceiling      1 / 20e-6            =  50,000 task-steps/sec
  request cost           1 step in, 1 step out (rounded to 1 for the estimate)

  RPS ceiling            50,000 req/s         <- CPU-side limit of ONE loop
  in-flight at ceiling   50,000 x 0.100 s     =  5,000 concurrent connections

  same math, pessimistic t_step = 50 us
  RPS ceiling            1 / 50e-6            =  20,000 req/s
  in-flight              20,000 x 0.100       =  2,000 concurrent connections

  actual service doing 500 req/s at 100 ms
  loop CPU consumed      500 x 20 us          =  0.010 s of CPU per wall second  = 1%
  in-flight              500 x 0.100          =  50 coroutines parked on I/O
```

The last three lines are the point: a real 500 req/s service spends about 1% of one core inside
the loop and parks 50 coroutines. At ~2–5 KB per Task (Section 8's table), those 50 coroutines cost
roughly 100–250 KB — which is why the "thousands of concurrent connections on a single thread"
claim in Section 2 is arithmetic, not marketing.

**Why `C = RPS x L` is the formula to carry into an interview.** It is Little's Law, and it answers
capacity questions in one step in either direction. Given a semaphore limit and a latency, it gives
throughput (`RPS = C / L`). Given a target throughput and a measured latency, it gives the
concurrency you must permit. Every `Semaphore(n)` value in this module is really an assertion about
this equation.

### get_event_loop() vs get_running_loop()

```python
# AVOID: asyncio.get_event_loop()
# In Python 3.10+, this emits a DeprecationWarning if there is no current event loop.
# In Python 3.12, it raises RuntimeError when called outside a running loop.
loop = asyncio.get_event_loop()   # unreliable inside library code

# PREFER: asyncio.get_running_loop()
# Raises RuntimeError immediately if called outside a running loop — fast-fail semantics.
# Returns exactly the loop that is currently executing. No implicit creation.
loop = asyncio.get_running_loop()  # safe and unambiguous inside async def
```

Rule of thumb: only application entry points call `asyncio.run()`; library code uses
`get_running_loop()` when it needs the loop reference (e.g., to schedule a `call_later`).

### Task Scheduling: create_task()

```python
import asyncio

async def worker(name: str, delay: float) -> str:
    await asyncio.sleep(delay)
    return f"{name} done"

async def main() -> None:
    # create_task schedules immediately; does NOT await yet
    t1 = asyncio.create_task(worker("A", 0.3))
    t2 = asyncio.create_task(worker("B", 0.1))

    # Both tasks are running concurrently from this point.
    # Without create_task, sequential awaits would take 0.3 + 0.1 = 0.4s.
    # With create_task, the total wall time is max(0.3, 0.1) = 0.3s.
    r1, r2 = await t1, await t2
    print(r1, r2)  # "A done B done"

asyncio.run(main())
```

A `Task` is a `Future` subclass. It drives its wrapped coroutine by calling `coro.send(None)` in
`Task.__step`. Each time the coroutine yields a Future, the Task adds itself as a callback on that
Future.

**What this actually says.** "Awaiting one thing at a time makes the clock add. Starting everything
first makes the clock stop at the slowest one." The comment above compresses the single most
important rule in async programming into two expressions: `0.3 + 0.1` versus `max(0.3, 0.1)`.

| Symbol | What it is |
|--------|------------|
| `sum(latency_i)` | Wall time when each `await` happens *after* the previous one returns |
| `max(latency_i)` | Wall time when every coroutine is already a Task before the first `await` |
| `create_task(coro)` | The thing that moves you from the first column to the second |
| `await t1, await t2` | Two *collection* points, not two start points — both were started earlier |

**Walk one example.** Push the module's own workers `A` (0.3 s) and `B` (0.1 s) through both shapes,
then scale to the ten 1-second URLs from Section 14:

```
  shape                       arithmetic                     wall time
  --------------------------- ------------------------------ ----------
  await worker(A)             0.3                            0.3 s
  then await worker(B)        0.3 + 0.1                      0.4 s

  t1 = create_task(A)         both already running
  t2 = create_task(B)         when the first await lands
  await t1; await t2          max(0.3, 0.1)                  0.3 s

  ten 1-second fetches
    sequential                1 + 1 + ... + 1  (10 terms)    10.0 s
    all as Tasks              max(1, 1, ..., 1)              1.0 s
```

Sequential-to-concurrent buys `0.4 -> 0.3` here, only 1.33x, because one task dominates. The same
change buys `10.0 -> 1.0`, a 10x cut, when the ten latencies are equal. That is the general shape:
the payoff of concurrency is `sum / max`, so it is largest when the work is *uniform* and shrinks
toward 1x when a single straggler already sets the floor.

**Why `max` and not something smaller.** Nothing here makes any individual call faster — B still
takes its full 0.1 s of network time. The event loop only removes the *waiting-in-line* portion.
The slowest call is therefore an irreducible floor: no amount of extra concurrency beats
`max(latency_i)`, which is why tail-latency work (timeouts, hedging) is the only lever left once
you are already concurrent.

### gather vs TaskGroup

```python
import asyncio

# ---- gather approach (Python 3.8+) ----
async def gather_example() -> None:
    results = await asyncio.gather(
        fetch("http://example.com/a"),
        fetch("http://example.com/b"),
        fetch("http://example.com/c"),
    )
    print(results)  # [resp_a, resp_b, resp_c] in input order

# ---- TaskGroup approach (Python 3.11+) ----
async def taskgroup_example() -> None:
    async with asyncio.TaskGroup() as tg:
        ta = tg.create_task(fetch("http://example.com/a"))
        tb = tg.create_task(fetch("http://example.com/b"))
        tc = tg.create_task(fetch("http://example.com/c"))
    # All three tasks are complete here.
    print(ta.result(), tb.result(), tc.result())

# ---- TaskGroup with multiple errors ----
async def multi_error_example() -> None:
    try:
        async with asyncio.TaskGroup() as tg:
            tg.create_task(fail_with(ValueError("bad value")))
            tg.create_task(fail_with(KeyError("missing key")))
            tg.create_task(succeed("ok"))
    except* ValueError as eg:
        print(f"ValueError group: {eg.exceptions}")
    except* KeyError as eg:
        print(f"KeyError group: {eg.exceptions}")
    # 'except*' syntax (3.11+) matches individual exception types within ExceptionGroup
```

Key differences: `gather` with `return_exceptions=False` cancels all remaining awaitables on the
first failure and raises only that exception. `TaskGroup` always cancels siblings and raises an
`ExceptionGroup` containing all exceptions, enabling handling each type separately with `except*`.

### Cancellation Deep Dive

```python
import asyncio

async def cleanup_aware() -> None:
    try:
        await asyncio.sleep(10)         # long-running operation
    except asyncio.CancelledError:
        print("Cancellation received — cleaning up resources")
        # Do cleanup: close connections, flush buffers, etc.
        raise                           # ALWAYS re-raise CancelledError

async def with_finally() -> None:
    try:
        await asyncio.sleep(10)
    finally:
        # finally blocks run even on cancellation — preferred for cleanup
        print("Cleanup in finally (always runs)")

async def demo_cancel() -> None:
    task = asyncio.create_task(cleanup_aware())
    await asyncio.sleep(0.1)
    task.cancel()                       # injects CancelledError at next await
    try:
        await task
    except asyncio.CancelledError:
        print("Task was cancelled")

asyncio.run(demo_cancel())
```

`task.cancel(msg="reason")` (3.9+) accepts an optional message. `task.cancelling()` (3.11+) returns
the number of pending cancel requests, allowing coroutines to distinguish cancellation from other
`CancelledError` sources.

### asyncio.Semaphore for Rate Limiting

```python
import asyncio
import httpx

async def fetch_with_limit(
    client: httpx.AsyncClient,
    url: str,
    semaphore: asyncio.Semaphore,
) -> str:
    async with semaphore:               # blocks if N tasks already running
        resp = await client.get(url)
        return resp.text

async def fetch_all(urls: list[str], max_concurrent: int = 3) -> list[str]:
    semaphore = asyncio.Semaphore(max_concurrent)
    async with httpx.AsyncClient(timeout=10.0) as client:
        tasks = [
            asyncio.create_task(fetch_with_limit(client, url, semaphore))
            for url in urls
        ]
        return await asyncio.gather(*tasks)
```

The Semaphore internal counter starts at `max_concurrent`. Each `async with semaphore` decrements
it; the coroutine blocks if the counter reaches 0, resuming when another coroutine exits its
`async with` block and increments the counter.

### asyncio.Queue for Backpressure

```python
import asyncio

async def producer(queue: asyncio.Queue[int], items: list[int]) -> None:
    for item in items:
        await queue.put(item)           # blocks when queue is full (backpressure)
    await queue.put(None)               # sentinel to signal completion

async def consumer(queue: asyncio.Queue[int], consumer_id: int) -> None:
    while True:
        item = await queue.get()        # blocks when queue is empty
        if item is None:
            await queue.put(None)       # re-queue sentinel for other consumers
            break
        print(f"Consumer {consumer_id} processed {item}")
        queue.task_done()

async def pipeline() -> None:
    queue: asyncio.Queue[int] = asyncio.Queue(maxsize=5)  # bounded: max 5 items buffered
    async with asyncio.TaskGroup() as tg:
        tg.create_task(producer(queue, list(range(20))))
        tg.create_task(consumer(queue, 1))
        tg.create_task(consumer(queue, 2))

asyncio.run(pipeline())
```

A `maxsize=5` queue means the producer can be at most 5 items ahead of the slowest consumer. If
the producer is faster, `queue.put()` suspends it, preventing unbounded memory growth.

### anyio Backend-Agnostic Timeouts

```python
import anyio

async def with_move_on() -> str | None:
    # Returns None if the operation exceeds the deadline (no exception raised)
    with anyio.move_on_after(5.0) as cancel_scope:
        result = await slow_operation()
    if cancel_scope.cancelled_caught:
        return None
    return result

async def with_fail_after() -> str:
    # Raises TimeoutError if the operation exceeds the deadline
    with anyio.fail_after(5.0):
        return await slow_operation()
```

`anyio.move_on_after` is the anyio equivalent of `asyncio.timeout()` with `return None` semantics.
Both work identically on asyncio and trio backends, making library code portable.

---

## 7. Real-World Examples

### FastAPI Dependency with Async DB Session

FastAPI routes are `async def` functions driven by uvicorn's event loop (which runs on anyio). A
request handler that awaits a database query suspends, freeing the event loop to serve other
requests during the I/O wait.

```python
from fastapi import FastAPI, Depends
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker

engine = create_async_engine("postgresql+asyncpg://user:pass@localhost/db")
SessionLocal = async_sessionmaker(engine, expire_on_commit=False)

async def get_db() -> AsyncSession:
    async with SessionLocal() as session:
        yield session

app = FastAPI()

@app.get("/users/{user_id}")
async def get_user(user_id: int, db: AsyncSession = Depends(get_db)) -> dict:
    result = await db.execute(select(User).where(User.id == user_id))
    return result.scalar_one_or_none()
```

### Async Generator for Server-Sent Events

```python
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import asyncio

async def event_stream(topic: str) -> AsyncGenerator[str, None]:
    async for message in subscribe(topic):          # async generator from message queue
        yield f"data: {message}\n\n"
        await asyncio.sleep(0)                      # yield control to allow flush

@app.get("/stream/{topic}")
async def stream(topic: str) -> StreamingResponse:
    return StreamingResponse(event_stream(topic), media_type="text/event-stream")
```

### Background Task Supervisor

```python
import asyncio

class TaskSupervisor:
    def __init__(self) -> None:
        self._tasks: set[asyncio.Task] = set()

    def spawn(self, coro) -> asyncio.Task:
        task = asyncio.create_task(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    async def shutdown(self) -> None:
        for task in list(self._tasks):
            task.cancel()
        await asyncio.gather(*self._tasks, return_exceptions=True)
```

Holding a strong reference to Tasks prevents them from being garbage-collected mid-execution (a
common silent bug when fire-and-forget tasks are created without storing the reference).

---

## 8. Tradeoffs

| Dimension | asyncio (single-thread) | Threading | Multiprocessing |
|---|---|---|---|
| Concurrency model | Cooperative, I/O-bound | Preemptive, I/O-bound | Parallel, CPU-bound |
| GIL impact | None (single thread) | Limited by GIL | Bypasses GIL |
| Memory per worker | ~2–5 KB per Task | ~1–8 MB per thread | ~10–30 MB per process |
| Context switch cost | ~0.1–1 µs (Python call) | ~1–10 µs (OS syscall) | ~10–100 µs (process) |
| Shared state | Simple (no races at `await`) | Requires locks | Requires IPC |
| CPU-bound work | Blocks loop (bad) | Blocked by GIL | Full parallelism |
| Error isolation | CancelledError propagation | Thread exceptions lost | Process crash isolated |
| Debugging | Harder (stack traces span loops) | Easier with thread names | Easiest isolation |
| Best for | 1000s I/O-bound connections | Few threads + I/O | CPU-heavy computation |

| gather() | wait() | TaskGroup |
|---|---|---|
| Returns ordered list | Returns (done, pending) sets | Structured scope |
| First exception cancels all | Fine-grained control | All exceptions collected |
| Python 3.4+ | Python 3.4+ | Python 3.11+ |
| `return_exceptions=True` for soft errors | `FIRST_COMPLETED` for races | `except*` for multi-error |
| Simple, common | Complex control flow | Preferred for new code |

---

## 9. When to Use / When NOT to Use

### Use asyncio when:

- The workload is I/O-bound: HTTP calls, database queries, file reads, WebSocket connections
- You need to serve thousands of concurrent connections on a single process (e.g., FastAPI + uvicorn)
- You are building streaming pipelines: SSE, WebSockets, async generators
- You need cooperative cancellation with cleanup guarantees (structured concurrency)
- Library code must remain portable across asyncio and trio (use anyio)

### Do NOT use asyncio when:

- The workload is CPU-bound (image processing, ML inference, numerical computation): the event loop
  will be blocked during computation. Use `asyncio.to_thread()` or `ProcessPoolExecutor` instead.
- You are writing a short CLI script with one sequential operation: the overhead of an event loop is
  unjustified; use synchronous code.
- You must integrate with a large synchronous codebase that uses blocking I/O throughout: wrapping
  every call in `run_in_executor` produces tangled code. Consider a thread pool instead.
- Your team is unfamiliar with cooperative concurrency: accidental blocking (calling `time.sleep`,
  CPU loops, or synchronous DB drivers in `async def`) silently degrades performance with no error.

### asyncio.sleep(0) — the explicit yield

`await asyncio.sleep(0)` yields control to the event loop for exactly one iteration and resumes on
the next. Use it inside CPU-intensive `async def` loops to prevent starving other tasks. It is not
equivalent to `await asyncio.sleep(0.001)` — the latter adds a minimum 1 ms timer delay.

---

## 10. Common Pitfalls

### Pitfall 1: Blocking the Event Loop with Synchronous I/O

```python
# BROKEN: requests.get() is synchronous — it blocks the entire OS thread
# while waiting for the network, freezing every other coroutine for the duration
import requests
import asyncio

async def fetch_bad(url: str) -> str:
    resp = requests.get(url)            # blocks event loop for network RTT (~100ms)
    return resp.text
```

```python
# FIX: use an async HTTP client; httpx.AsyncClient uses asyncio sockets internally
import httpx
import asyncio

async def fetch_good(url: str) -> str:
    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.get(url)    # suspends coroutine, not the thread
    return resp.text

# FIX alternative: wrap sync code in executor (for third-party sync libs you cannot replace)
import asyncio
from concurrent.futures import ThreadPoolExecutor

_executor = ThreadPoolExecutor(max_workers=10)

async def fetch_executor(url: str) -> str:
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(_executor, requests.get, url)
```

For blocking CPU work use `asyncio.to_thread(fn, *args)` (3.9+) which internally calls
`run_in_executor` with the default thread pool.

---

### Pitfall 2: Swallowing CancelledError

`CancelledError` sits directly under `BaseException` as a sibling of `Exception`, not a subclass of
it — this one fact is why a bare `except Exception` looks safe but `except BaseException` is not:

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Root("BaseException") --> Exc("Exception<br/>ValueError, ConnectError, ...")
    Root --> Canc("CancelledError")

    Exc -->|"except Exception:"| Caught(["caught<br/>handled normally"])
    Canc -.->|"except Exception:<br/>no match, propagates"| Safe(["Task stays<br/>cancellable"])
    Canc -->|"except BaseException:<br/>matches too"| Swallowed(["silently swallowed<br/>Task never cancels"])

    class Root base
    class Exc mathOp
    class Canc lossN
    class Caught,Safe train
    class Swallowed lossN
```
*`download_broken`'s bare `except Exception` correctly lets `CancelledError` propagate, but
`download_broken_v2`'s `except BaseException` catches it too — silently swallowing the
cancellation and leaving the Task permanently hung.*

```python
# BROKEN: bare `except Exception` catches CancelledError in Python < 3.8
# and in Python 3.8+ it still works if you explicitly re-raise — but many
# developers forget, leaving tasks permanently hung.

async def download_broken(url: str) -> bytes:
    try:
        return await fetch(url)
    except Exception:                   # CancelledError is BaseException — NOT caught here
        pass                            # but ValueError, httpx.ConnectError, etc. are caught
    # ^ The issue: all errors are silently swallowed, including transient ones.
    # Real broken version that DOES swallow cancellation:

async def download_broken_v2(url: str) -> bytes:
    try:
        return await fetch(url)
    except BaseException:               # catches CancelledError
        print("something failed")
        return b""                      # silently swallows cancellation — task never cancels
```

```python
# FIX: catch only the exceptions you handle; always re-raise CancelledError
import asyncio

async def download_fixed(url: str) -> bytes | None:
    try:
        return await fetch(url)
    except asyncio.CancelledError:
        print("Cancelled — releasing resources")
        raise                           # MUST re-raise so the Task marks itself cancelled
    except (httpx.ConnectError, httpx.TimeoutException) as exc:
        print(f"Network error: {exc}")
        return None
```

---

### Pitfall 3: run_until_complete Inside a Running Loop

```python
# BROKEN: raises RuntimeError: "This event loop is already running"
# in Python 3.10+. Common mistake in Jupyter notebooks or nested async frameworks.

async def inner() -> str:
    return "result"

async def outer_broken() -> None:
    loop = asyncio.get_event_loop()
    result = loop.run_until_complete(inner())   # RuntimeError inside running loop
```

```python
# FIX option 1: just await the coroutine directly — you are already in async context
async def outer_fixed() -> None:
    result = await inner()                      # correct

# FIX option 2: from synchronous code in another thread, use run_coroutine_threadsafe
import asyncio
import threading

running_loop: asyncio.AbstractEventLoop | None = None

def sync_caller() -> str:
    assert running_loop is not None
    future = asyncio.run_coroutine_threadsafe(inner(), running_loop)
    return future.result(timeout=5.0)
```

---

### Pitfall 4: Creating Tasks After TaskGroup Shutdown Has Started

```python
# BROKEN: tg.create_task() after the group has begun its exit protocol
# raises RuntimeError: "TaskGroup is shutting down"

async def broken_dynamic() -> None:
    tasks: list[asyncio.Task] = []
    async with asyncio.TaskGroup() as tg:
        tasks.append(tg.create_task(fetch("http://a.com")))
    # At this point the TaskGroup has exited — its internal state is invalid.
    # Any attempt to spawn more tasks on 'tg' here will raise RuntimeError.
    tasks.append(tg.create_task(fetch("http://b.com")))   # RuntimeError
```

```python
# FIX: create all tasks inside the async with block
async def fixed_dynamic(urls: list[str]) -> list[str]:
    async with asyncio.TaskGroup() as tg:
        task_objs = [tg.create_task(fetch(url)) for url in urls]
    # All tasks are done here; access results safely
    return [t.result() for t in task_objs]
```

---

## 11. Technologies & Tools

| Tool / Library | Role | Key Notes |
|---|---|---|
| `asyncio` (stdlib) | Core async runtime | Ships with CPython; event loop, Tasks, Futures, primitives |
| `uvloop` | High-performance asyncio event loop | Drop-in replacement using libuv; 2-4x throughput on I/O-heavy workloads; Linux/macOS only |
| `anyio` | Backend-agnostic async layer | Supports asyncio and trio; used internally by FastAPI/Starlette; TaskGroup, move_on_after |
| `trio` | Alternative async runtime | Strict structured concurrency; no `create_task` escape hatch; better for correctness |
| `httpx` | Async HTTP client | `AsyncClient` for async code; also has sync API; HTTP/2 support |
| `asyncpg` | Async PostgreSQL driver | Fastest Postgres driver in Python; used by SQLAlchemy async engine |
| `motor` | Async MongoDB driver | Wraps PyMongo with asyncio support |
| `aioredis` | Async Redis client | Superseded by `redis.asyncio` (bundled in redis-py 4.2+) |
| `aiokafka` | Async Kafka producer/consumer | asyncio-native Kafka client |
| `nest_asyncio` | Run event loops in Jupyter | Patches `asyncio` to allow nested `run_until_complete`; dev-only |
| `greenlet` | Low-level coroutine primitive | Used by SQLAlchemy's async bridge; not a direct asyncio substitute |

### Debugging Tools

- `asyncio.set_event_loop_policy(asyncio.DefaultEventLoopPolicy())` — reset policy; on Windows
  use `WindowsSelectorEventLoopPolicy` for compatibility with older libraries
- `asyncio.get_event_loop().set_debug(True)` — logs slow callbacks (>100 ms), unawaited coroutines
- `sys.set_coroutine_origin_tracking_depth(10)` — adds coroutine creation traceback to
  "coroutine was never awaited" warnings
- `PYTHONASYNCIODEBUG=1` environment variable — enables debug mode globally
- `aiodebug` / custom slow-callback detection via `loop.slow_callback_duration = 0.05`

---

## 12. Interview Questions with Answers

**Q1: What is a Python coroutine and how does it differ from a regular function?**
A coroutine is a function declared with `async def` that can suspend execution at `await` points
and resume later, preserving its local state between suspensions. Unlike a regular function which
runs to completion when called, calling an `async def` function returns a coroutine object that
does not execute until driven by an event loop or awaited by another coroutine. Under the hood,
`async def` compiles to a generator-based state machine; `await expr` desugars to
`yield from expr.__await__()`. The practical guidance: never call a coroutine without `await` or
`create_task` — you will get a RuntimeWarning and the body will never run.

**Q2: How does the asyncio event loop schedule and run coroutines?**
The event loop maintains two queues: `_ready` (callbacks to run immediately) and `_scheduled`
(timers). Each iteration it calls `epoll_wait()` (Linux), `kqueue` (macOS), or `select()` with a
timeout equal to the nearest scheduled callback, collects I/O-ready file descriptors, resolves
their associated Futures, runs all `_ready` callbacks (which includes `Task.__step` calls that
drive coroutines forward), and then processes expired timers. One event loop iteration takes
roughly 5–50 µs on an idle modern server; a heavily loaded loop with thousands of ready sockets
can stretch to low milliseconds. The key point for interviews: it is single-threaded and
cooperative — there are no preemptions, only voluntary `await`-point yields.

**Q3: What is the difference between asyncio.gather() and asyncio.TaskGroup?**
`gather()` runs awaitables concurrently and returns an ordered list of results; on the first
exception it cancels all remaining awaitables and re-raises only that one exception (unless
`return_exceptions=True`). `TaskGroup` (3.11+) is a structured concurrency scope: all tasks
spawned inside the `async with` block are cancelled when any one fails, and all exceptions are
collected into a single `ExceptionGroup` so callers can handle multiple simultaneous failures with
`except*`. The practical guidance: prefer `TaskGroup` for new Python 3.11+ code because it
enforces task lifetimes, makes error handling exhaustive, and composes correctly with cancellation.

**Q4: What exactly happens when task.cancel() is called?**
`task.cancel()` does not immediately stop the task. It schedules a `CancelledError` to be raised
at the task's next `await` point. If the coroutine is currently suspended waiting on a Future, the
Future is cancelled and `CancelledError` is thrown into the coroutine on its next `send()`. The
coroutine can catch `CancelledError` in a `try/finally` block to perform cleanup, but must re-raise
it so the Task marks itself as cancelled. `task.cancelling()` (3.11+) returns the count of
pending cancel requests. The practical guidance: always use `try/finally` (not `try/except
CancelledError: return`) for resource cleanup in long-running tasks.

**Q5: What is structured concurrency and why does it matter?**
Structured concurrency is the principle that a spawned task cannot outlive the scope in which it
was created. In asyncio, `TaskGroup` enforces this: the `async with asyncio.TaskGroup()` block does
not exit until every task spawned inside it has finished or been cancelled. This eliminates entire
classes of bugs: dangling tasks that consume memory after the parent has returned, fire-and-forget
tasks whose exceptions are silently lost, and tasks that hold resources beyond their intended
lifetime. Without structured concurrency (bare `create_task` everywhere), tracking which tasks are
still running and ensuring clean shutdown requires manual bookkeeping. The practical guidance: use
`TaskGroup` for any concurrent fan-out where you need to wait for all results or guarantee cleanup.

**Q6: How does asyncio handle CPU-bound work?**
It does not handle CPU-bound work well by default. A CPU-bound operation inside `async def` blocks
the entire event loop thread for its duration, preventing any other coroutine from running. The
correct approach is to offload CPU work to a thread pool via `asyncio.to_thread(fn, *args)` (3.9+)
or `loop.run_in_executor(executor, fn, *args)` with a `ProcessPoolExecutor` for true parallelism.
For ML inference at scale, a common pattern is a dedicated inference process that communicates
over a queue, with the event loop acting only as a dispatcher. The practical guidance: if a
function takes more than ~5 ms of CPU time, do not call it directly in `async def`; wrap it with
`asyncio.to_thread`.

**Q7: What is the difference between asyncio.sleep(0) and asyncio.sleep(n)?**
`asyncio.sleep(0)` yields control to the event loop for exactly one iteration and reschedules the
current coroutine on `_ready` immediately. It has no timer overhead and resumes as fast as the
event loop can cycle (~5–50 µs). `asyncio.sleep(n)` registers a timer callback at `now + n`
seconds and the coroutine does not resume until that timer fires, regardless of how quickly the
event loop iterates. Use `sleep(0)` inside CPU-intensive `async def` loops to prevent task
starvation (give other tasks a chance to run). Use `sleep(n)` for actual time-based delays. The
practical guidance: sprinkle `await asyncio.sleep(0)` in tight async loops that do not otherwise
contain natural I/O yield points.

**Q8: How do async generators work and where are they useful?**
An `async def` function containing `yield` is an async generator. It returns an
`AsyncGenerator[YieldType, SendType]` object consumed with `async for`. Each iteration calls
`__anext__()`, which drives the generator until the next `yield`, allowing `await` inside the
body. They are useful for lazy streaming: paginated API responses, database cursor iteration,
SSE event streams, and WebSocket message feeds. Unlike regular generators, they cannot be used
with `next()` — only `async for` or explicit `await gen.__anext__()`. The practical guidance:
use `AsyncGenerator[T, None]` as the return type annotation and always ensure the consumer
iterates to exhaustion or explicitly closes the generator to trigger cleanup in `finally` blocks.

**Q9: What is anyio and why would you use it instead of asyncio directly?**
`anyio` is an async compatibility layer that provides a unified API over asyncio and trio backends.
Its `TaskGroup`, `move_on_after`, and `fail_after` primitives work identically on both runtimes.
FastAPI and Starlette use anyio internally, so application code that uses anyio primitives runs
correctly regardless of which backend uvicorn or hypercorn is configured with. anyio also provides
better cancellation semantics aligned with structured concurrency. The practical guidance: use
anyio in library code that should remain backend-agnostic; in application code that is strictly
asyncio (e.g., a service that runs only under uvicorn), direct asyncio is fine and simpler.

**Q10: How do you debug a misbehaving async application?**
Start with `PYTHONASYNCIODEBUG=1` or `loop.set_debug(True)` — this logs slow callbacks (>100 ms
by default), coroutines that were created but never awaited, and resources not properly closed.
Set `sys.set_coroutine_origin_tracking_depth(10)` before creating coroutines to get full
tracebacks in "coroutine was never awaited" warnings. For Jupyter notebooks, `import nest_asyncio;
nest_asyncio.apply()` patches the loop to allow `asyncio.run()` inside a running loop. For
production, instrument with OpenTelemetry spans around `create_task` calls to trace task fan-out.
The practical guidance: always run with debug mode on in development; in production, set
`slow_callback_duration = 0.05` (50 ms) to catch accidental blocking code early.

**Q11: How does asyncio interact with threads?**
asyncio and threads can coexist via three patterns: (1) `asyncio.to_thread(fn, *args)` (3.9+)
runs a synchronous function in the default `ThreadPoolExecutor` and returns a coroutine that
resolves when the thread finishes — this is the canonical way to call blocking code from async.
(2) `loop.run_in_executor(executor, fn, *args)` is the lower-level equivalent with a custom
executor. (3) `asyncio.run_coroutine_threadsafe(coro, loop)` submits a coroutine from a non-async
thread to a running event loop and returns a `concurrent.futures.Future` (not `asyncio.Future`).
The practical guidance: when integrating a legacy synchronous library, prefer `asyncio.to_thread`
for simplicity; only use `run_coroutine_threadsafe` when you need to bridge from a synchronous
thread (e.g., a callback-based framework) into an already-running event loop.

**Q12: What causes "RuntimeWarning: coroutine was never awaited"?**
This warning means a coroutine object was created (by calling `async def fn()`) but `.send(None)`
was never called on it — the function body never executed. The most common cause is calling a
coroutine function without `await`: `result = fetch(url)` instead of `result = await fetch(url)`.
In Python 3.11+, the traceback includes the creation site (if `sys.set_coroutine_origin_tracking_depth`
is set). The warning does not become an error by default, so it can hide bugs silently. The
practical guidance: treat this warning as an error in CI by setting
`PYTHONWARNINGS=error::RuntimeWarning`.

**Q13: How does backpressure work in asyncio?**
`asyncio.Queue(maxsize=N)` is the primary backpressure mechanism. When the queue is full,
`await queue.put(item)` suspends the producer until a consumer calls `queue.get()`. This prevents
a fast producer from buffering unlimited items in memory. For network-level backpressure,
`asyncio.StreamWriter.write()` buffers data internally; `await writer.drain()` suspends the
coroutine if the transport's write buffer exceeds its high-water mark. The practical guidance: any
producer/consumer pipeline with mismatched speeds should use a bounded `Queue`; calling
`write()` in a loop without `drain()` is a common memory leak pattern in WebSocket servers.

**Q14: What is asyncio.timeout() and how does it differ from asyncio.wait_for()?**
`asyncio.timeout(delay)` (3.11+) is a context manager that cancels any awaitable inside its block
after `delay` seconds, raising `asyncio.TimeoutError`. `asyncio.wait_for(coro, timeout)` wraps a
single coroutine and raises `TimeoutError` on expiry. The key behavioral difference: `timeout()` is
composable — you can nest multiple `timeout()` context managers, and the inner one raises
`TimeoutError` independently of the outer one. `wait_for` also has a historical bug in Python
< 3.12 where cancellation of the outer task could cause the wrapped coroutine to be swallowed
rather than properly cancelled. The practical guidance: prefer `asyncio.timeout()` for new 3.11+
code; use `anyio.fail_after()` for portable code.

**Q15: How do you implement a graceful shutdown for a FastAPI application?**
FastAPI exposes a lifespan context manager (3.0+) where you initialize resources on entry and
perform cleanup on exit. During shutdown, uvicorn sends SIGTERM, which triggers the lifespan
context manager's exit. Inside the exit, cancel all background tasks, wait for in-flight requests
to complete with a deadline, and close database connection pools. Use `asyncio.gather(*tasks,
return_exceptions=True)` to wait for all background tasks, then flush any pending queue items.
The practical guidance: always store `asyncio.Task` references in a set and call `task.cancel()`
with `await asyncio.gather` in the shutdown path — stray tasks that hold DB connections are the
most common cause of connection pool exhaustion on redeploys.

**Q16: What is the difference between a Future and a Task?**
`asyncio.Future` is a low-level result placeholder: it can be in pending, done, or cancelled state,
and callbacks can be added to it with `add_done_callback`. It is manually resolved by calling
`future.set_result(value)` or `future.set_exception(exc)`. `asyncio.Task` is a `Future` subclass
that wraps a coroutine and drives it automatically by calling `coro.send(None)` in `Task.__step`;
the Task resolves itself when the coroutine returns or raises. In practice, application code almost
never creates bare Futures; they are created by low-level transport and protocol implementations.
The practical guidance: always use `create_task` for coroutines; only use bare `Future` when
bridging with callback-based code (e.g., wrapping a callback-style API into an awaitable).

**Q17: How does asyncio perform I/O multiplexing?**
On Linux, asyncio uses `epoll` via the `selectors.EpollSelector`; on macOS and BSD, it uses
`kqueue`; on Windows, it uses `IOCP` (I/O Completion Ports) via the ProactorEventLoop (default
on Windows since 3.8). The event loop registers file descriptors with the selector and calls
`select()` with a timeout. When a file descriptor becomes readable or writable, the selector
returns it, the loop resolves the associated Future (e.g., a `StreamReader` future), which
schedules the waiting coroutine. The latency from a file descriptor becoming ready to the
coroutine resuming is roughly one event loop iteration: 5–50 µs on idle, dominated by the
Python overhead of `Task.__step`. The practical guidance: `uvloop` replaces the selector with
libuv's event loop, reducing per-connection overhead by 2–4x on I/O-heavy workloads.

**Q18: What is shield() and when should you use it?**
`asyncio.shield(coro_or_future)` wraps an awaitable so that external cancellation of the outer
Task does not propagate into the shielded operation. If the outer task is cancelled, the `await
asyncio.shield(inner)` raises `CancelledError` in the outer task, but `inner` continues running.
This is useful for operations that must complete regardless of the caller's lifecycle: database
commits, audit log writes, payment captures. Important caveat: the shielded task keeps running
even after the outer task is cancelled, potentially leaking resources. The practical guidance: use
`shield` sparingly and only for truly non-cancellable operations; prefer designing around
structured concurrency where possible, and always ensure the shielded task is tracked and awaited
during shutdown.

---

## 13. Best Practices

**Always use `asyncio.run()` as the entry point.** Never call `loop.run_until_complete()` directly
in application code; it does not perform proper cleanup and is deprecated for application use.

**Prefer `TaskGroup` over bare `create_task` for fan-out.** `TaskGroup` guarantees that all spawned
tasks are awaited before the scope exits, preventing dangling tasks and making exception handling
exhaustive.

**Hold strong references to Tasks.** `asyncio.create_task()` returns a Task, but if you do not
store the reference, it can be garbage-collected mid-execution. Store tasks in a `set` and use a
`done_callback` to discard them when complete.

**Use `asyncio.Semaphore` for concurrency limits.** Never fire off 1000 concurrent HTTP requests
without a semaphore — you will exhaust file descriptor limits and overwhelm remote servers. A
semaphore of 10–50 concurrent requests is a sensible default for most APIs.

**Never call synchronous blocking code inside `async def` without an executor.** This includes
`time.sleep`, synchronous file I/O, CPU-heavy computation, and any synchronous DB driver. Use
`asyncio.to_thread` for blocking I/O and `ProcessPoolExecutor` for CPU work.

**Re-raise `CancelledError` always.** Catch it only to perform cleanup, then re-raise. Use
`try/finally` as the default pattern since finally blocks run on both normal return and
cancellation.

**Prefer `asyncio.timeout()` over `asyncio.wait_for()` on Python 3.11+.** The new context manager
API is composable, has correct cancellation semantics, and is easier to reason about.

**Use `asyncio.get_running_loop()` in library code.** Never call `asyncio.get_event_loop()` in
library code; it creates implicit loops in old Python versions and raises in new ones.

**Instrument with `loop.set_debug(True)` in development.** This catches slow callbacks, unawaited
coroutines, and improperly closed transports early, before they cause production incidents.

**Bound all producer queues.** `asyncio.Queue()` without `maxsize` is unbounded and will grow until
OOM. Set `maxsize` to the maximum acceptable buffer depth.

---

## 14. Case Study

### Building a Concurrent HTTP Fetcher with Rate Limiting and Structured Cancellation

**Scenario.** A price aggregation service must fetch current prices from 10 supplier APIs on every
user request. Each HTTP call takes ~100–200 ms. The requirement: complete all fetches within 2
seconds, limit concurrent outbound connections to 3 (API rate limit), handle individual failures
gracefully, and cancel all in-flight requests if the caller disconnects.

---

#### Step 1: Naive Sequential Approach (the baseline problem)

```python
import asyncio
import time
import httpx

URLS = [f"https://httpbin.org/delay/1?id={i}" for i in range(10)]

async def fetch_sequential(urls: list[str]) -> list[str]:
    results: list[str] = []
    async with httpx.AsyncClient(timeout=5.0) as client:
        for url in urls:
            resp = await client.get(url)     # awaits each one before starting the next
            results.append(resp.text[:20])
    return results

start = time.monotonic()
asyncio.run(fetch_sequential(URLS))
print(f"Sequential: {time.monotonic() - start:.1f}s")   # ~10.0s (10 × 1s each)
```

10 URLs × ~1 second each = ~10 seconds total. Unacceptable for a user-facing request.

---

#### Step 2: gather() Approach — All Concurrent (too aggressive)

```python
async def fetch_all_gather(urls: list[str]) -> list[str]:
    async with httpx.AsyncClient(timeout=5.0) as client:
        results = await asyncio.gather(
            *[client.get(url) for url in urls],
            return_exceptions=True,
        )
    return [
        r.text[:20] if isinstance(r, httpx.Response) else f"ERROR: {r}"
        for r in results
    ]

start = time.monotonic()
asyncio.run(fetch_all_gather(URLS))
print(f"gather (all concurrent): {time.monotonic() - start:.1f}s")  # ~1.0s
```

Wall time drops to ~1 second, but this fires all 10 requests simultaneously, violating the API's
concurrency limit of 3.

---

#### Step 3: BROKEN — Missing CancelledError Handling

```python
# BROKEN: if the outer task is cancelled (e.g. client disconnect in FastAPI),
# CancelledError propagates into gather but the except block swallows it,
# leaving the HTTP client in an unknown state and the Task silently hanging.

async def fetch_with_rate_limit_broken(
    urls: list[str],
    max_concurrent: int = 3,
) -> list[str]:
    semaphore = asyncio.Semaphore(max_concurrent)
    results: list[str] = []

    async def _fetch(url: str) -> str:
        async with semaphore:
            try:
                async with httpx.AsyncClient(timeout=5.0) as client:
                    resp = await client.get(url)
                    return resp.text[:20]
            except Exception:            # BROKEN: swallows CancelledError (BaseException)
                return "ERROR"

    return await asyncio.gather(*[_fetch(url) for url in urls])
```

When the parent task is cancelled (FastAPI request disconnected), `CancelledError` is raised inside
`_fetch` at `await client.get(url)`. The bare `except Exception` does not catch it (since
`CancelledError` inherits from `BaseException`), so it propagates — which is actually fine in this
case. But if the developer changes it to `except BaseException: return "ERROR"`, cancellation is
silently swallowed and the parent task never terminates.

---

#### Step 4: FIX — TaskGroup + Semaphore + Proper Cancellation

```python
import asyncio
import httpx
from dataclasses import dataclass, field

@dataclass
class FetchResult:
    url: str
    body: str | None = None
    error: str | None = None

async def fetch_one(
    client: httpx.AsyncClient,
    url: str,
    semaphore: asyncio.Semaphore,
) -> FetchResult:
    async with semaphore:                   # max_concurrent slots
        try:
            resp = await client.get(url)
            return FetchResult(url=url, body=resp.text[:20])
        except asyncio.CancelledError:
            # Cleanup: the httpx client handles connection closing in __aexit__
            raise                           # FIX: always re-raise
        except httpx.HTTPError as exc:
            return FetchResult(url=url, error=str(exc))

async def fetch_with_taskgroup(
    urls: list[str],
    max_concurrent: int = 3,
    overall_timeout: float = 2.0,
) -> list[FetchResult]:
    semaphore = asyncio.Semaphore(max_concurrent)
    task_refs: dict[asyncio.Task, str] = {}

    async with httpx.AsyncClient(timeout=5.0) as client:
        try:
            async with asyncio.timeout(overall_timeout):       # 3.11+: 2s hard deadline
                async with asyncio.TaskGroup() as tg:
                    for url in urls:
                        t = tg.create_task(fetch_one(client, url, semaphore))
                        task_refs[t] = url
        except* httpx.HTTPError as eg:
            # TaskGroup collected all HTTP errors into ExceptionGroup;
            # individual FetchResults already handle per-URL errors above,
            # so this branch handles unexpected aggregate failures.
            print(f"Aggregate HTTP errors: {eg.exceptions}")
        except asyncio.TimeoutError:
            print(f"Overall 2s timeout exceeded; partial results available")

    return [t.result() for t in task_refs if t.done() and not t.cancelled()]

# Usage
import time
start = time.monotonic()
results = asyncio.run(fetch_with_taskgroup(URLS, max_concurrent=3))
elapsed = time.monotonic() - start
print(f"TaskGroup + Semaphore(3): {elapsed:.1f}s")   # ~4.0s (10 URLs / 3 concurrent × 1s = ~4s)
# With max_concurrent=10: ~1.0s
# With max_concurrent=3 and overall_timeout=2.0: partial results after 2s

for r in results:
    status = r.body if r.body else f"ERR({r.error})"
    print(f"  {r.url[-10:]} → {status}")
```

The winning shape gates all ten calls through one semaphore, keeps every worker inside a single
structured-concurrency scope, and lets one outer deadline cut the whole batch short:

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Urls(["10 supplier URLs"]) --> Sem{"Semaphore(3)<br/>gate"}

    subgraph Scope["asyncio.timeout(2.0s) wraps TaskGroup scope"]
        Sem --> W1("fetch_one<br/>worker")
        Sem --> W2("fetch_one<br/>worker")
        Sem --> W3("fetch_one<br/>worker")
    end

    W1 --> Res(["FetchResult list<br/>~4.0s full · ~2.0s w/ timeout"])
    W2 --> Res
    W3 --> Res

    class Urls io
    class Sem mathOp
    class W1,W2,W3 train
    class Res io
```
*Ten calls funnel through a `Semaphore(3)` gate, run as `fetch_one` workers inside a `TaskGroup`
bounded by a 2-second `asyncio.timeout`, and the caller always gets back whatever `FetchResult`s
finished in time.*

**Performance summary:**

| Approach | Wall time (10 × 1s URLs) | Concurrency | Error handling |
|---|---|---|---|
| Sequential | ~10.0s | 1 | Full, sequential |
| gather (unlimited) | ~1.0s | 10 | First exception cancels all |
| TaskGroup + Semaphore(3) | ~4.0s | 3 (rate-limited) | All exceptions collected |
| TaskGroup + Semaphore(3) + timeout(2s) | ~2.0s | 3 | Partial results, deadline enforced |

**Stated plainly.** Every wall time in that table comes out of one expression:
`ceil(N / k) x L` — "how many full rounds of `k` does it take to clear `N` items, times how long a
round lasts." The semaphore does not slow any single request; it converts a fan-out into rounds.

| Symbol | What it is |
|--------|------------|
| `N` | Total awaitables to run — 10 supplier URLs here |
| `k` | Semaphore permits. The width of one round |
| `L` | Latency of a single call, ~1.0 s for these URLs |
| `ceil(N / k)` | Number of rounds. `ceil`, not `/`, because a partial final round still costs a full `L` |
| `D` | Outer deadline (`asyncio.timeout`). Caps rounds at `floor(D / L)` |

**Walk one example.** Reproduce all four rows of the table from that one expression:

```
  row                       k    ceil(10/k)   x L      wall time
  ------------------------- ---- ------------ -------- ----------
  sequential                 1    10           x 1.0    10.0 s
  gather, unlimited         10     1           x 1.0     1.0 s
  TaskGroup + Semaphore(3)   3     4           x 1.0     4.0 s
  + timeout(2.0s)            3     4 -> capped at floor(2.0/1.0) = 2 rounds
                                                         2.0 s

  what the deadline costs you
    rounds completed in 2 s      2
    results returned             2 rounds x 3 permits  =  6 of 10 FetchResults
    the other 4 were cancelled mid-flight by the TaskGroup
```

Note `ceil(10 / 3) = 4`, not `3.33`. The tenth URL runs alone in a fourth round while two permits
sit idle — a 25% waste of the last round that is invisible if you divide instead of rounding up.
Choosing `k` so that `N / k` is close to a whole number is the cheapest tuning win available.

**Why the deadline row is the one that ships.** The first three rows trade only speed against
politeness. The fourth changes the *contract*: it converts "however long 10 fetches take" into
"whatever finished in 2 seconds", which is the only form a user-facing SLA can take. The cost is
explicit and computable — `floor(D / L) x k` results instead of `N`.

The production pattern is the last row: structured concurrency scope (`TaskGroup`) ensures no task
outlives the request, a `Semaphore` enforces the API rate limit, `asyncio.timeout` enforces the
user-facing SLA, and individual task errors are isolated via `FetchResult` — never propagated as
uncaught exceptions that would cancel sibling fetches unnecessarily.

**Cross-references:**
- For blocking-in-async anti-patterns see `../async_patterns_and_pitfalls/README.md`
- For FastAPI's use of anyio internally, see `../fastapi/fastapi_fundamentals_asgi/README.md`
- For LLM streaming with asyncio SSE, see `../../llm/case_studies/cross_cutting/streaming_at_scale.md`
