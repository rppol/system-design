# Design an Async Task Queue System

## Intuition

A checkout endpoint that emails a receipt, renders a PDF, and calls SAP synchronously is a
200 ms request wearing a 4-second coat. The fix is not "make those calls faster" — SendGrid,
WeasyPrint, and a legacy ERP have their own latency budgets you do not control. The fix is to
separate **"the order is safely recorded"** from **"the order is fully processed"**: persist
first, hand the slow work to a durable queue, and let a fleet of workers drain it independently
of the request/response cycle. The one idea that makes everything else in this file necessary is
that a message broker can and will deliver a task more than once — a crashed worker, a network
blip, or a visibility-timeout race all look identical from the consumer's side — so every task
handler must be safe to run twice. Design for that from line one and retries, DLQs, and
idempotency keys stop being extra features and become the load-bearing part of the system.

---

## 1. Requirements Clarification

**Functional requirements**
- `POST /orders` persists an order and returns in well under 200 ms regardless of downstream
  availability.
- Every order triggers three independent post-processing tasks: confirmation email (SendGrid),
  PDF invoice generation, and an ERP inventory update (legacy SAP over REST).
- A task that fails is retried automatically with backoff; after a bounded number of attempts it
  moves to a dead-letter queue (DLQ) for human inspection, never dropped silently.
- Ops can query the status of any task by ID and re-enqueue a dead-lettered task after a fix.
- A scheduled/periodic sweep re-checks orders whose invoice generation never completed (defense
  in depth beyond the DLQ).

**Non-functional requirements**
- **Durability**: an enqueued task must survive an API process restart, a worker crash, and a
  rolling deploy — it cannot exist only in the memory of the process that created it.
- **At-least-once delivery**, not exactly-once. Exactly-once needs a distributed transaction
  across the broker and every downstream system (SendGrid, SAP) that none of them can offer, so
  it is out of scope; every task handler is required to be idempotent instead.
- **Ordering**: none required across the three task types (they are independent); ordering *is*
  required within a single order's retries of the same task (a stale retry must not run after a
  newer attempt already succeeded — the idempotency key handles this).
- **Backpressure**: a slow SAP endpoint must not let the queue grow unbounded — priority lanes
  and depth alerts are load-bearing, not cosmetic.
- **Observability**: every attempt produces a structured log line and a queryable result record.

**Clarifying questions worth asking in an interview**
- Is a lost email acceptable if the customer views the order in-app? (Changes the DLQ SLA for that
  one task type — usually yes for email, never for the ERP write, since inventory must reconcile.)
- Can the ERP call be made idempotent on the SAP side, or only on ours? (SAP has no native
  idempotency key support here, so the design must supply one via a request header.)
- Peak traffic pattern — steady, or a marketing-driven spike? (Drives §2's peak multiplier and
  §10's autoscaling policy.)

For the generic vocabulary — broker types, delivery semantics, backoff formulas — see
[Background Jobs & Task Queues](../background_jobs_and_task_queues/background_jobs_and_task_queues.md)
and [Messaging Patterns](../../backend/messaging_patterns/messaging_patterns.md); this file is
the FastAPI-wired, end-to-end version of that theory.

---

## 2. Scale Estimation

**Order volume.** The platform processes 5 million orders/day at steady state. Each order fans
out to 3 tasks, so:

```
tasks/day        = 5,000,000 orders x 3 tasks/order        = 15,000,000 tasks/day
tasks/sec (avg)  = 15,000,000 / 86,400 s                   ≈ 174 tasks/sec
```

Traffic is not flat — checkout concentrates around lunch and evening in each timezone. Using a
peak-to-average ratio of 6x (typical for consumer e-commerce diurnal + flash-sale spikes):

```
peak tasks/sec   = 174 x 6                                 ≈ 1,050 tasks/sec
```

**Retry multiplier.** At-least-once delivery plus a non-zero failure rate on external calls means
the broker sees more enqueues than the business-logic task count. With a measured 4% first-attempt
failure rate on the SAP call (the least reliable dependency) and roughly half of retries needing a
second attempt:

```
effective load  = 1,050 tasks/sec x 1.06                   ≈ 1,113 tasks/sec at peak
```

That 6% is small enough to absorb with existing headroom — it becomes material only when a
downstream outage pushes the failure rate to 100% for a window (covered in §9's thundering-herd
war story, where the multiplier stops being 1.06 and becomes "the entire backlog retries").

**Required worker count — Little's Law.** Little's Law states `L = λ x W`: the number of tasks
in flight (`L`, which determines concurrency needed) equals arrival rate (`λ`) times average time
in the system (`W`). Average task duration across the three types, weighted by mix:

```
email     : 300 ms  (SendGrid API call)
invoice   : 1,200 ms (render + upload)
erp       : 900 ms  (SAP REST call)
weighted average W ≈ (300 + 1200 + 900) / 3 = 800 ms = 0.8 s
```

```
L (concurrent tasks in flight at peak) = λ x W = 1,113 tasks/sec x 0.8 s ≈ 890 concurrent tasks
```

With each async worker process running `max_jobs=50` concurrent coroutines (I/O-bound work, so
one event loop multiplexes many in-flight calls without thread overhead — see §5 for why this
number is 50 and not ARQ's default of 10):

```
worker processes required = 890 / 50 ≈ 18 processes
```

Round up for headroom and to survive one worker replica being drained during a deploy:
**24 worker processes** at steady peak, split roughly 18 on the default queue and 6 on the
high-priority invoice queue (§5, §10).

**Broker memory.** Each enqueued job is a small JSON payload — `order_id` (UUID, 36 bytes),
`task_name` (≤24 bytes), and metadata (job ID, enqueue time, retry count) — call it 400 bytes
per job serialized in Redis, plus ARQ's own per-job key overhead (~150 bytes for the `arq:job:<id>`
string and its expiry). At a sustained queue depth of 5,000 jobs (a comfortable buffer, not a
backlog — see §10 for the alert threshold that says otherwise):

```
queue memory = 5,000 jobs x 550 bytes/job ≈ 2.75 MB
```

Trivial at steady state. The number that matters is the **worst case**: a 30-minute SAP outage at
peak load queues `1,113 tasks/sec x 1,800 s ≈ 2,000,000` backlogged jobs:

```
worst-case queue memory = 2,000,000 x 550 bytes ≈ 1.1 GB
```

That single number is why the Redis instance is sized in §10 with several GB of headroom above
steady-state usage, not against the happy path.

**Result-backend storage.** Each task result record (status, duration, error, timestamp) is
about 200 bytes JSON. At 15M tasks/day with a 24-hour TTL:

```
result backend size = 15,000,000 tasks x 200 bytes ≈ 3 GB resident at any moment
```

The audit table in PostgreSQL keeps invoice and ERP records (not email — audit interest is
"was inventory correctly decremented," not "was the email sent") indefinitely for compliance:

```
audit rows/day    = 5,000,000 orders x 2 audited tasks = 10,000,000 rows/day
row size          ≈ 250 bytes (order_id, task_name, status, timestamps, error text)
storage/year      = 10,000,000 x 365 x 250 bytes ≈ 912 GB/year (pre-index, pre-compression)
```

That is large enough to need partitioning by month and an archival policy past 18 months — sized
concretely in §10.

---

## 3. High-Level Architecture

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#282c34,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#282c34
    classDef train   fill:#98c379,stroke:#27ae60,color:#282c34
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#282c34,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#282c34,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#282c34
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#282c34

    Client(["POST /orders"])
    Handler("FastAPI Handler<br/>under 200 ms")
    Outbox[("Postgres<br/>orders + outbox<br/>same transaction")]
    Relay("Outbox Relay<br/>polls, enqueues, marks sent")
    Queue@{ icon: "logos:redis", form: "square", label: "Redis Queue<br/>arq:queue sorted set", pos: "b", h: 44 }
    QueueHi@{ icon: "logos:redis", form: "square", label: "Redis Queue<br/>arq:high", pos: "b", h: 44 }
    Workers("ARQ Workers<br/>18-24 processes<br/>send_email . gen_invoice . update_erp")
    Idem@{ icon: "logos:redis", form: "square", label: "Redis<br/>Idempotency Store", pos: "b", h: 44 }
    Results@{ icon: "logos:redis", form: "square", label: "Redis<br/>Result Backend", pos: "b", h: 44 }
    DLQ@{ icon: "logos:redis", form: "square", label: "Redis DLQ<br/>arq:dlq list", pos: "b", h: 44 }
    Audit[("Postgres<br/>task_audit")]

    Client --> Handler
    Handler -->|"insert order + outbox row"| Outbox
    Outbox -->|poll unsent rows| Relay
    Relay -->|"enqueue(order_id, task)"| Queue
    Relay -->|"enqueue(gen_invoice)"| QueueHi
    Queue --> Workers
    QueueHi --> Workers
    Workers -.->|"check / set key"| Idem
    Workers -.->|"store result"| Results
    Workers -.->|"compliance row"| Audit
    Workers -->|"max retries exceeded"| DLQ

    class Client io
    class Handler mathOp
    class Outbox req
    class Relay mathOp
    class Workers mathOp
    class DLQ lossN
```

The handler no longer talks to the broker directly — it writes the order and an outbox row in
one PostgreSQL transaction, and a separate relay process reads unsent outbox rows and enqueues
them (§4 covers exactly why this indirection exists: it closes the dual-write gap between "the
order committed" and "the task was enqueued"). Workers dequeue from a low-priority default queue
and a high-priority invoice queue, guard against duplicates via the idempotency store, and either
record a result or fall through to the DLQ after 3 failed attempts.

**Data flow:**
1. `POST /orders` inserts the order row **and** three outbox rows in a single PostgreSQL
   transaction, then returns immediately — no broker call is on the request's critical path.
2. The outbox relay (a lightweight background process, one per region) polls
   `WHERE sent_at IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED`, enqueues each row to ARQ,
   and marks it sent in the same relay transaction.
3. An ARQ worker process picks up a job, claims the idempotency key in Redis, and skips if
   already completed.
4. On failure the job is retried up to 3 times with exponential backoff + jitter.
5. After 3 failures the worker writes the job metadata to the DLQ list (`arq:dlq`).
6. `GET /admin/dlq` and `POST /admin/dlq/requeue` let ops inspect and replay DLQ items.

```mermaid
stateDiagram-v2
    classDef req    fill:#56b6c2,stroke:#0097a7,color:#282c34
    classDef mathOp fill:#d19a66,stroke:#e67e22,color:#282c34,font-weight:bold
    classDef frozen fill:#c678dd,stroke:#9b59b6,color:#282c34
    classDef train  fill:#98c379,stroke:#27ae60,color:#282c34
    classDef lossN  fill:#e06c75,stroke:#c0392b,color:#282c34,font-weight:bold

    state "Checking idempotency" as Checking
    state "Executing task" as Executing
    state "Retrying, backoff wait" as Retrying
    state "Dead lettered" as DeadLettered
    state "Skipped, duplicate" as Skipped

    [*] --> Enqueued
    Enqueued --> Checking: worker dequeues
    Checking --> Skipped: key already set
    Checking --> Executing: key absent
    Executing --> Done: success
    Executing --> Retrying: failure, attempts 1-2
    Retrying --> Executing: backoff delay elapses
    Executing --> DeadLettered: failure, 3rd attempt
    DeadLettered --> Enqueued: admin requeue
    Skipped --> Done
    Done --> [*]

    class Enqueued req
    class Checking mathOp
    class Executing mathOp
    class Retrying frozen
    class Done train
    class Skipped train
    class DeadLettered lossN
```

Every task moves through this state machine on each attempt: a duplicate short-circuits straight
to `Done`, a failure below the 3-attempt ceiling loops back through a backoff wait, and only
`POST /admin/dlq/requeue` can return a dead-lettered job to the queue.

---

## 4. Component Deep Dives

### 4.1 The boundary: why `BackgroundTasks` stops being adequate

`BackgroundTasks` schedules a coroutine on the *same* event loop, inside the *same* Uvicorn
worker process, as the HTTP request. It is the right tool for disposable work — bumping a view
counter — and the wrong tool the instant a task must survive the process that created it. That
boundary is absolute, not a matter of tuning: no timeout or worker-count knob fixes it, because
the failure mode is "the process running the coroutine no longer exists."

```python
# BROKEN: tasks are lost if the process restarts mid-flight
@router.post("/orders")
async def create_order(
    payload: OrderCreate,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
) -> OrderResponse:
    order = await order_service.create(db, payload)
    # These run in the same process; no persistence, no retry, no DLQ.
    background_tasks.add_task(send_confirmation_email, order.id)
    background_tasks.add_task(generate_invoice, order.id)
    background_tasks.add_task(update_erp_inventory, order.id)
    return OrderResponse.model_validate(order)
```

A rolling Kubernetes deploy sends SIGTERM to the pod running this handler. Uvicorn's graceful
shutdown drains in-flight HTTP requests, but a `BackgroundTasks` coroutine that was scheduled
*after* the response was already sent is not an in-flight request — it is silently cancelled with
the event loop. There is no retry, no DLQ, no result record: the invoice for that order is simply
never generated, and nothing in the system knows it needs to be.

### 4.2 The transactional outbox — closing the dual-write gap

Enqueueing directly from the request handler looks safe but has a race baked in: the handler
commits the order to PostgreSQL, then calls `arq_pool.enqueue_job(...)` against Redis. Those are
two independent writes to two independent systems with no shared transaction. If the process
crashes between them — or the enqueue call times out but actually succeeded server-side — the
order and its tasks can permanently disagree.

```mermaid
sequenceDiagram
    participant C as Client
    participant H as FastAPI Handler
    participant PG as PostgreSQL
    participant R as Outbox Relay
    participant Q as Redis Queue

    C->>H: POST /orders
    H->>PG: BEGIN
    H->>PG: INSERT order
    H->>PG: INSERT outbox rows, task=email, invoice, erp
    H->>PG: COMMIT
    H-->>C: 201 Created, order committed
    loop every 250 ms
        R->>PG: SELECT unsent outbox rows FOR UPDATE SKIP LOCKED
        R->>Q: enqueue_job for each row
        R->>PG: UPDATE outbox SET sent_at = now, in same relay transaction
    end
```

The outbox row is written in the *same* database transaction as the order, so it is atomic with
the fact the design cares about: "the order exists." A separate relay process — decoupled from
the request path, so its own crash never blocks checkout — polls for unsent rows and enqueues
them. If the relay dies mid-batch, the unsent rows are simply picked up again on restart; the
`SKIP LOCKED` clause lets multiple relay replicas run concurrently without double-claiming a row,
and enqueueing an already-sent row twice is harmless because every task handler is idempotent
(§4.3). This is strictly better than enqueueing from the handler directly: it trades "the broker
call is on the request's critical path and can fail independently of the commit" for "the broker
call is decoupled, retried by the relay's own loop, and can never leave an order without its
tasks."

```python
# BROKEN: dual write — the enqueue can fail or run against a row that never commits
@router.post("/orders")
async def create_order_broken(
    payload: OrderCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
    arq_pool: Annotated[ArqRedis, Depends(get_arq_pool)],
) -> OrderResponse:
    order = await order_service.create(db, payload)   # COMMIT #1: PostgreSQL
    await db.commit()
    # If the process crashes here, the order exists but no task is ever enqueued.
    # If Redis is unreachable, the customer gets a 500 for an order that DID succeed.
    await arq_pool.enqueue_job("send_confirmation_email", order.id)
    await arq_pool.enqueue_job("generate_invoice", order.id)
    await arq_pool.enqueue_job("update_erp_inventory", order.id)
    return OrderResponse.model_validate(order)

# FIXED: outbox row is atomic with the order; a separate relay does the enqueue
@router.post("/orders")
async def create_order(
    payload: OrderCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> OrderResponse:
    async with db.begin():
        order = await order_service.create(db, payload)
        for task_name in ("send_confirmation_email", "generate_invoice", "update_erp_inventory"):
            db.add(OutboxRow(order_id=order.id, task_name=task_name))
    # COMMIT is one transaction: order + all three outbox rows, or none of them.
    return OrderResponse.model_validate(order)
```

### 4.3 Idempotency — the mandatory consequence of at-least-once

Exactly-once delivery would need a two-phase commit spanning Redis and every downstream (SendGrid
has no such API), so this system accepts at-least-once and makes every handler safe to run twice.
The claim must be a **single atomic command**, not a check followed by a write:
`EXISTS` then `SET` is a check-then-act race — two workers handed the same at-least-once
redelivery both see the key absent, both proceed, and the customer gets two emails.
`SET key 1 NX EX 172800` collapses both steps into one server-side operation: it returns a truthy
value for exactly one caller and `None` for every other, so only one worker ever does the work.

```python
# ── src/tasks/helpers.py ──────────────────────────────────────────────────────
import json
import random
import time
from typing import Any

import redis.asyncio as aioredis

from ..config import settings

async def claim(redis: aioredis.Redis, order_id: str, task_name: str) -> bool:
    """Atomically claim this (order, task) pair. True for exactly one caller."""
    key = f"idem:{order_id}:{task_name}"
    return await redis.set(key, "1", nx=True, ex=settings.idempotency_ttl_seconds) is True

async def release(redis: aioredis.Redis, order_id: str, task_name: str) -> None:
    """Drop the claim so a retry may run. Called on every failure path."""
    await redis.delete(f"idem:{order_id}:{task_name}")

async def store_result(
    redis: aioredis.Redis,
    job_id: str,
    status: str,
    duration_ms: float,
    error: str | None = None,
) -> None:
    key = f"result:{job_id}"
    value = json.dumps(
        {"status": status, "duration_ms": duration_ms, "error": error, "ts": time.time()}
    )
    await redis.set(key, value, ex=settings.result_ttl_seconds)

async def push_dlq(redis: aioredis.Redis, metadata: dict[str, Any]) -> None:
    await redis.rpush("arq:dlq", json.dumps(metadata))

def backoff_delay(attempt: int) -> float:
    """Capped exponential backoff plus additive jitter (attempt is 1-indexed)."""
    exp = settings.task_base_delay * (2 ** (attempt - 1))
    capped = min(exp, settings.task_max_delay)
    return capped + random.uniform(0, settings.task_jitter)
```

**Broken vs. fixed idempotency check — the double-charge scenario.** The task most exposed to
this mistake is the ERP inventory update: a worker that decrements stock, crashes before ARQ
records success, and gets redelivered will decrement stock *twice* for one order unless the
claim happens before the SAP call and survives the crash.

```python
# BROKEN: check-then-act race — two redeliveries can both pass the check
async def update_erp_inventory_broken(ctx: dict[str, Any], order_id: str) -> str:
    redis = ctx["redis"]
    key = f"idem:{order_id}:update_erp_inventory"
    already_done = await redis.get(key)          # READ
    if already_done:
        return "duplicate"
    # A second redelivery of this same job can interleave here, before either
    # caller has written the key — both proceed, SAP decrements stock twice.
    await call_sap_inventory_update(order_id)
    await redis.set(key, "1", ex=172_800)         # WRITE, after the fact
    return "ok"

# FIXED: atomic claim before any work; release only on failure
async def update_erp_inventory(ctx: dict[str, Any], order_id: str) -> str:
    redis = ctx["redis"]
    if not await claim(redis, order_id, "update_erp_inventory"):  # atomic SET NX EX
        return "duplicate"
    try:
        await call_sap_inventory_update(order_id)
        return "ok"
    except Exception:
        await release(redis, order_id, "update_erp_inventory")   # only on failure
        raise
```

Claiming *before* the SAP call, rather than after, changes the failure semantics deliberately:
because the key is set before the work happens, a task that fails must delete its own key on the
way out, or a legitimate retry would see its own claim and skip the work forever. The handlers in
§4.4 do exactly that.

### 4.4 Task handlers, worker process, and admin endpoints

ARQ counts attempts for you: the worker puts a 1-indexed `job_try` into the job context dict on
every execution. **Read the attempt number from `ctx["job_try"]`, never from a job argument** —
raising `Retry` re-queues the *same* job with the *same* serialised arguments, so a handler that
took `attempt: int = 1` as a parameter would see `attempt == 1` on every execution and never
dead-letter anything.

```python
# ── requirements ──────────────────────────────────────────────────────────────
# arq==0.28.*  fastapi==0.140.*  uvicorn  pydantic-settings
# redis>=4.2,<6   <- do NOT pin redis 8 here: arq itself requires
#                    redis[hiredis]>=4.2.0,<6, so a redis==8 pin is
#                    unresolvable alongside arq
# aiohttp  weasyprint  "sqlalchemy[asyncio]"  asyncpg
#   ^ SQLAlchemy 2.1 will not install greenlet by default; async support
#     requires the [asyncio] extra explicitly.

# ── src/config.py ─────────────────────────────────────────────────────────────
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    redis_dsn: str = "redis://localhost:6379/0"
    postgres_dsn: str = "postgresql+asyncpg://user:pass@localhost/orders"
    arq_max_jobs: int = 50
    task_max_retries: int = 3
    task_base_delay: float = 5.0
    task_max_delay: float = 60.0
    task_jitter: float = 10.0
    result_ttl_seconds: int = 86_400          # 24 h
    idempotency_ttl_seconds: int = 172_800    # 48 h

settings = Settings()

# ── src/tasks/order_tasks.py ──────────────────────────────────────────────────
import asyncio
import time
from typing import Any, Awaitable, Callable

import aiohttp
import redis.asyncio as aioredis
from arq import Retry

from ..config import settings
from .helpers import backoff_delay, claim, push_dlq, release, store_result

async def _run_idempotent(
    ctx: dict[str, Any], order_id: str, task_name: str, work: Callable[[], Awaitable[str]]
) -> str:
    """Shared claim/retry/DLQ envelope — each handler below is just `work()` plus this."""
    redis: aioredis.Redis = ctx["redis"]
    job_id: str = ctx["job_id"]
    # ARQ owns the attempt counter. Do NOT pass one as a job argument: a
    # Retry re-runs the job with identical arguments, so it would never move.
    attempt: int = ctx["job_try"]
    start = time.monotonic()

    if not await claim(redis, order_id, task_name):
        await store_result(redis, job_id, "skipped_duplicate", 0.0)
        return "duplicate"

    try:
        result = await work()
        await store_result(redis, job_id, "success", (time.monotonic() - start) * 1000)
        return result
    except Exception as exc:
        # The claim was taken before the work; drop it so the retry can run.
        await release(redis, order_id, task_name)
        if attempt >= settings.task_max_retries:
            await push_dlq(
                redis, {"order_id": order_id, "task": task_name,
                        "error": str(exc), "attempts": attempt},
            )
            await store_result(
                redis, job_id, "dead_lettered", (time.monotonic() - start) * 1000, error=str(exc)
            )
            return "dlq"
        raise Retry(defer=backoff_delay(attempt)) from exc

async def send_confirmation_email(ctx: dict[str, Any], order_id: str) -> str:
    """Send order confirmation email via SendGrid. Idempotent via Redis NX key."""
    async def work() -> str:
        # Session is built once in startup(ctx) below. Building one per job
        # would tear down the TCP/TLS pool 50 times over at max_jobs=50.
        session: aiohttp.ClientSession = ctx["session"]
        async with session.post(
            "https://api.sendgrid.com/v3/mail/send",
            json={"order_id": order_id},
            headers={"Authorization": f"Bearer {ctx['sendgrid_key']}"},
            timeout=aiohttp.ClientTimeout(total=5),
        ) as resp:
            resp.raise_for_status()
        return "ok"

    return await _run_idempotent(ctx, order_id, "send_confirmation_email", work)

async def generate_invoice(ctx: dict[str, Any], order_id: str) -> str:
    """Generate PDF invoice and upload to S3. Idempotent."""
    async def work() -> str:
        await asyncio.sleep(0.9)  # WeasyPrint render in production
        return f"invoices/{order_id}.pdf"

    return await _run_idempotent(ctx, order_id, "generate_invoice", work)

async def update_erp_inventory(ctx: dict[str, Any], order_id: str) -> str:
    """Push inventory delta to SAP ERP. Idempotent via claim-before-work."""
    async def work() -> str:
        session: aiohttp.ClientSession = ctx["session"]
        async with session.post(
            "https://erp.internal/inventory/update",
            json={"order_id": order_id},
            headers={"Idempotency-Key": f"{order_id}:update_erp_inventory"},
            timeout=aiohttp.ClientTimeout(total=8),
        ) as resp:
            resp.raise_for_status()
        return "ok"

    return await _run_idempotent(ctx, order_id, "update_erp_inventory", work)

# ── src/worker.py ─────────────────────────────────────────────────────────────
import aiohttp
import redis.asyncio as aioredis
from arq.connections import RedisSettings

from .config import settings
from .tasks.order_tasks import generate_invoice, send_confirmation_email, update_erp_inventory

async def startup(ctx: dict) -> None:  # type: ignore[type-arg]
    ctx["redis"] = await aioredis.from_url(settings.redis_dsn, decode_responses=True)
    ctx["sendgrid_key"] = "sg_key_from_env"
    # One HTTP connection pool per worker process, shared by all max_jobs coroutines.
    ctx["session"] = aiohttp.ClientSession(
        connector=aiohttp.TCPConnector(limit=100, limit_per_host=20)
    )

async def shutdown(ctx: dict) -> None:  # type: ignore[type-arg]
    await ctx["session"].close()
    await ctx["redis"].aclose()

class WorkerSettings:
    functions = [send_confirmation_email, generate_invoice, update_erp_inventory]
    on_startup = startup
    on_shutdown = shutdown
    redis_settings = RedisSettings.from_dsn(settings.redis_dsn)
    max_jobs = settings.arq_max_jobs        # ARQ's own default is 10
    job_timeout = 30                        # seconds; ARQ's own default is 300
    keep_result = settings.result_ttl_seconds
    max_tries = settings.task_max_retries   # backstop; ARQ's own default is 5
    queue_name = "arq:queue"
    # ARQ's default SIGTERM handling drains in-flight jobs up to job_timeout
    # before force-cancelling — no extra code needed; see §8 for the deploy
    # config this depends on and §9 for what breaks without it.

# Run with: python -m arq src.worker.WorkerSettings

# ── src/routers/orders.py ─────────────────────────────────────────────────────
from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from ..deps import get_db
from ..models import Order, OrderCreate, OrderResponse, OutboxRow

router = APIRouter(prefix="/orders", tags=["orders"])

@router.post("/", response_model=OrderResponse, status_code=201)
async def create_order(
    payload: OrderCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> OrderResponse:
    async with db.begin():
        order: Order = await order_service.create(db, payload)
        for task_name in ("send_confirmation_email", "generate_invoice", "update_erp_inventory"):
            db.add(OutboxRow(order_id=order.id, task_name=task_name))
    return OrderResponse.model_validate(order)

# ── src/outbox_relay.py ─────────────────────────────────────────────────────────
"""Standalone process: python -m src.outbox_relay. One or more replicas are safe
because SKIP LOCKED prevents two replicas from claiming the same row."""
import asyncio

from arq import create_pool
from arq.connections import RedisSettings
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker

from .config import settings
from .models import OutboxRow

HIGH_PRIORITY_TASKS = {"generate_invoice"}

async def relay_loop() -> None:
    engine = create_async_engine(settings.postgres_dsn)
    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    arq_pool = await create_pool(RedisSettings.from_dsn(settings.redis_dsn))

    while True:
        async with session_factory() as session, session.begin():
            rows = (
                await session.execute(
                    select(OutboxRow)
                    .where(OutboxRow.sent_at.is_(None))
                    .order_by(OutboxRow.id)
                    .limit(100)
                    .with_for_update(skip_locked=True)
                )
            ).scalars().all()

            for row in rows:
                queue = "arq:high" if row.task_name in HIGH_PRIORITY_TASKS else "arq:queue"
                await arq_pool.enqueue_job(row.task_name, row.order_id, _queue_name=queue)
                row.sent_at = _now()
        await asyncio.sleep(0.25)

# ── src/routers/admin.py ──────────────────────────────────────────────────────
import json
from typing import Annotated, Any

import redis.asyncio as aioredis
from fastapi import APIRouter, Depends, HTTPException, Query

from ..deps import get_arq_pool, get_redis

admin_router = APIRouter(prefix="/admin", tags=["admin"])

@admin_router.get("/dlq")
async def list_dlq(
    redis: Annotated[aioredis.Redis, Depends(get_redis)],
    limit: int = Query(default=50, le=200),
) -> list[dict[str, Any]]:
    """Return up to `limit` DLQ entries (oldest first)."""
    raw: list[str] = await redis.lrange("arq:dlq", 0, limit - 1)
    return [json.loads(r) for r in raw]

@admin_router.post("/dlq/requeue")
async def requeue_dlq_item(
    order_id: str,
    task_name: str,
    redis: Annotated[aioredis.Redis, Depends(get_redis)],
    arq_pool: Annotated[Any, Depends(get_arq_pool)],
) -> dict[str, str]:
    """Remove idempotency key and re-enqueue a DLQ task."""
    await redis.delete(f"idem:{order_id}:{task_name}")
    await arq_pool.enqueue_job(task_name, order_id)
    return {"status": "requeued", "task": task_name, "order_id": order_id}

@admin_router.get("/results/{job_id}")
async def get_result(
    job_id: str,
    redis: Annotated[aioredis.Redis, Depends(get_redis)],
) -> dict[str, Any]:
    raw = await redis.get(f"result:{job_id}")
    if raw is None:
        raise HTTPException(status_code=404, detail="Result not found or TTL expired")
    return json.loads(raw)
```

---

## 5. Design Decisions & Tradeoffs

### 5.1 Broker/framework choice — Celery vs ARQ vs Dramatiq vs TaskIQ vs Temporal

The async-native row is the load-bearing one for a FastAPI codebase. Celery's worker model
predates `asyncio` and runs tasks on threads or gevent greenlets even when the body is
`async def`, so it shares neither FastAPI's event loop nor its connection pools. ARQ and
**TaskIQ** are asyncio-native end to end — a TaskIQ worker `await`s task coroutines on its own
loop, the same execution model as the app that enqueued them. Temporal is a different tool: it
durably executes a whole workflow function, replaying history to resume where it left off — the
right choice when task 2 chains off task 1's *result*, not just its completion (§11).

| Dimension            | BackgroundTasks | Celery                          | ARQ            | Dramatiq              | TaskIQ                    | Temporal                        |
|-----------------------|-----------------|----------------------------------|-----------------|-------------------------|-----------------------------|-----------------------------------|
| Durability            | None            | Yes (broker)                     | Yes (Redis)     | Yes (broker)            | Yes (broker)                | Yes (event-sourced history)      |
| Async-native          | Yes             | No — threads/gevent even for `async def` tasks | Yes (asyncio)   | Async actors (1.15+), dedicated loop thread | Yes (asyncio), pluggable broker | Yes (async SDK)                  |
| Broker options        | —               | Redis, RabbitMQ, SQS              | Redis only      | Redis, RabbitMQ         | Redis, RabbitMQ, Kafka, NATS | Own server (Temporal Cluster)    |
| Retry built-in        | None            | `autoretry_for`, `countdown`      | `Retry` + `defer` | `@actor(max_retries=N)` | `@task(retry_on_error=...)` | Automatic, policy-configurable   |
| DLQ support            | None            | Configure broker's own DLX        | DIY (push to list) | Built in, 7-day retention | DIY (broker-dependent)   | N/A — failed workflow visible in history, retried or terminated explicitly |
| Monitoring UI          | None            | Flower                            | None (DIY)      | dramatiq-dashboard      | TaskIQ Admin (early)         | Temporal Web UI (mature)         |
| Scheduled tasks         | None            | Celery Beat                       | `cron` in WorkerSettings | periodiq / APScheduler | `@schedule(...)` decorator  | Native cron workflows            |
| Setup complexity       | Zero            | High (beat, flower, broker)       | Low             | Medium                  | Low                          | High (separate cluster + SDK)    |
| Best fit               | Ephemeral, non-critical | Large existing Django/Flask shops | Async FastAPI, I/O-bound | CPU-bound + threads | Async FastAPI, wants broker flexibility | Multi-step workflows needing state/replay |

**Decision:** ARQ — asyncio-native, minimal boilerplate, clean `lifespan` integration, and Redis
is already in the stack. TaskIQ is the strongest alternative: same async-native model plus broker
portability (start on Redis, move to Kafka without a rewrite), at the cost of a smaller ecosystem
and a younger admin UI. Celery still wins for a shop already running Flower and Beat; Temporal
earns its cost only once tasks chain on results, which these do not.

### 5.2 Delivery semantics — at-least-once with idempotency

Exactly-once requires an atomic dequeue + downstream write — two-phase commit, or Kafka
transactions with `enable.idempotence=true` and offset commits gated on the DB write — and both
add latency and operational complexity this system does not need. At-least-once with an
idempotency key costs one `SET NX EX` per invocation (§4.3).

### 5.3 Retry strategy — exponential backoff with jitter

```
delay = min(base * 2^(attempt-1), cap) + random.uniform(0, jitter)
base = 5 s, cap = 60 s, jitter = 10 s
attempt 1 fails -> defer 5 s  + jitter
attempt 2 fails -> defer 10 s + jitter
attempt 3 fails -> dead-letter, no further defer
```

```mermaid
xychart-beta
    title "Deferred delay after each failure (base 5 s, jitter 0-10 s)"
    x-axis ["After attempt 1", "After attempt 2", "After attempt 3"]
    y-axis "Delay before next run (seconds)" 0 --> 30
    bar [5, 10, 0]
```

The third bar is zero because attempt 3 dead-letters rather than deferring. Without jitter every
worker retrying a recovering downstream wakes at the same instant (§9 quantifies the resulting
thundering herd); uniform jitter spreads that load across a 10-second
window instead.

### 5.4 Result backend — Redis for hot results, PostgreSQL for audit

| Concern         | Redis (TTL 24 h)         | PostgreSQL                      |
|-----------------|---------------------------|-----------------------------------|
| Write latency   | < 1 ms                    | 2-10 ms                           |
| Query by order  | O(1) GET by key           | SQL JOIN, indexed                 |
| Durability      | AOF/RDB, configurable     | WAL, ACID                         |
| TTL management  | Native, zero cost         | Manual purge job                  |
| Audit / reports | Not suitable              | Yes, joins and aggregates          |

**Decision:** operational status (status, duration, error) goes to Redis with a 24-hour TTL, fast
and self-expiring, sufficient for ops dashboards. Invoice and ERP outcomes are also appended to a
`task_audit` PostgreSQL table for compliance — email is not audited, since there is no compliance
interest in whether a marketing-adjacent email sent.

### 5.5 Worker concurrency — async workers sized against the slowest downstream

All three task types are network-bound, so a single-threaded async event loop multiplexes dozens
of in-flight tasks without thread overhead. This design sets `max_jobs=50` per worker — ARQ's own
default is 10, a deliberate raise justified by §2's Little's Law arithmetic. Size it against the
slowest downstream, not the queue: 50 concurrent ERP calls at 8-second timeouts is 50 sockets SAP
must survive, and that is the real constraint, not Redis or ARQ.

### 5.6 Priority queues, dead-letter queues, and the DLQ operational procedure

ARQ has no built-in priority concept — `queue_name` is one string per `WorkerSettings`, so
prioritization is a deployment-topology decision: route invoice generation (user-visible, and
what customers ask about first) to `arq:high`, everything else to `arq:queue`, and run more
replicas against the high-priority queue. That is real isolation, not best-effort ordering — a
flood of ERP retries cannot starve invoices, since they are consumed by different processes
entirely. Without it, a burst of slow/failing low-value tasks pushes out fast, high-value ones
indefinitely, because a FIFO/sorted-set queue has no concept of "this one matters more."

Draining a DLQ is a procedure, not a button: (1) `GET /admin/dlq`, group entries by `error` to
find the common cause — one bad ERP payload schema usually explains 90% of entries; (2) fix the
root cause first (§9 shows what happens when this step is skipped); (3) `POST /admin/dlq/requeue`
per entry, or a batch script for a large drain, watching
`task_executions_total{status="dead_lettered"}` return to zero; (4) keep raw entries a week for
audit before purging.

---

## 6. Real-World Implementations

- **Slack — Redis job queue fronted by Kafka.** Slack's original queue ran entirely on Redis,
  processing on the order of **1.4 billion jobs per day** across ~50 Redis clusters — but a
  cluster failure could silently drop queued jobs. Their fix mirrors this file's core lesson: put
  a durable log in front of the in-memory queue rather than trust the queue's own durability.
  They put Kafka (50 topics x 32 partitions) ahead of Redis and reported peaks of roughly
  **33,000 jobs/second**. Source: <https://slack.engineering/scaling-slacks-job-queue/>

- **Dropbox — ATF (Asynchronous Task Framework).** Dropbox consolidated dozens of bespoke,
  team-specific schedulers into one framework, reporting roughly **9,000 async tasks
  scheduled/second**, adopted by **28 engineering teams** across **100+ use cases**. Matches
  §5.1's lesson: a shared, centrally-handled idempotency/retry/DLQ abstraction beats N teams each
  re-solving the same problem. Source:
  <https://dropbox.tech/infrastructure/asynchronous-task-scheduling-at-dropbox>

- **Uber — Cadence, later spun out as Temporal.** Uber built Cadence to durably execute
  long-running, stateful workflows where steps depend on each other's *output*, not just
  completion — a trip's full lifecycle, driver onboarding. Uber benchmarked it to roughly
  **10,000 workflow starts/second** and **200 million concurrently open workflows**, running
  across **1,000+ services at Uber**; the same engine later shipped independently as Temporal.
  Lesson: reach for a workflow engine only once tasks genuinely chain on each other's results
  (§11) — for three independent tasks, a plain queue is the cheaper, correct tool. Source:
  <https://www.uber.com/en-AU/blog/announcing-cadence/>

- **Segment (Twilio Segment) — Centrifuge.** Centrifuge fans events out to third-party APIs,
  replacing a design where one shared queue let a single customer's retries starve every other
  customer's deliveries — the exact starvation mode §5.6's priority lanes prevent here. Segment
  reported handling **hundreds of thousands of events/second**, with **~1.5% of events
  succeeding only on retry** and **99.9% resolved within 30 minutes**. Source:
  <https://segment.com/blog/introducing-centrifuge/>

- **DoorDash — replacing RabbitMQ with Kafka.** DoorDash's async task processing on RabbitMQ hit
  recurring broker-failure outages; a gradual, zero-downtime cutover to Kafka eliminated that
  outage pattern entirely and let the tier scale horizontally — the same worst-case argument as
  §2: a durable, horizontally-scalable log absorbs a backlog an in-memory broker cannot. Source:
  <https://careersatdoordash.com/blog/eliminating-task-processing-outages-with-kafka/>

- **Stripe — idempotency keys as a first-class primitive.** Stripe popularized the
  `Idempotency-Key` header now copied across payment and API platforms: a client supplies a
  unique key (a V4 UUID), and Stripe returns the original request's stored result — status code
  and body, even a 500 — for any retry with the same key. Stripe's own key-retention window is
  roughly 24 hours, directly analogous to this file's 48-hour idempotency TTL (§4.3). Source:
  <https://docs.stripe.com/api/idempotency>

---

## 7. Technologies & Tools

| Component | Role |
|-----------|------|
| `arq` | Async task queue backed by a Redis **sorted set** (`arq:queue`, score = scheduled run time) plus one `arq:job:<id>` string per job — not Redis Streams; `Retry` exception triggers a deferred re-run |
| `arq.connections.ArqRedis` | Connection pool injected into FastAPI via `Depends` |
| `redis.asyncio` | Direct Redis client for idempotency keys, result records, DLQ list — the maintained module (never the archived standalone `aioredis`) |
| `fastapi.BackgroundTasks` | **Not used for durable work** — shown in the broken example in §4.1 only |
| `contextlib.asynccontextmanager` + `lifespan` | Manages ARQ pool and Redis client lifecycle |
| `pydantic_settings.BaseSettings` | Typed config with `.env` support |
| `aiohttp.ClientSession` | Async HTTP client for SendGrid and ERP calls — built once in `startup(ctx)`, read from `ctx["session"]`, so the TCP/TLS pool is reused across jobs |
| `Annotated[..., Depends(...)]` | FastAPI dependency injection style |
| `APIRouter` | Splits orders and admin endpoints into separate routers |
| `sqlalchemy[asyncio]` + `asyncpg` | Async ORM/engine for orders and the outbox table; the `[asyncio]` extra is already valid on 2.0 and becomes required in 2.1, which stops installing `greenlet` by default |
| `SELECT ... FOR UPDATE SKIP LOCKED` | PostgreSQL row-locking clause that lets multiple outbox-relay replicas poll concurrently without double-claiming a row |
| **TaskIQ** | Async-native alternative to ARQ with pluggable brokers (Redis, RabbitMQ, Kafka, NATS) — the comparison point most likely to come up in a 2026 interview; see §5.1 |
| **Temporal** | Durable workflow execution engine — the right escalation once tasks must chain on each other's results, not just completion; see §5.1 and §11 |
| `httpx.AsyncClient` + `ASGITransport` | Used in tests to drive the FastAPI app without a real socket — see §11 for the exact construction |

---

## 8. Operational Playbook

**Deploying a worker fleet.** Roll worker deployments one region/AZ at a time. Because ARQ's
default SIGTERM handling waits for in-flight jobs up to `job_timeout` (30 s here) before force
cancelling, a rolling deploy with `terminationGracePeriodSeconds` set at or above `job_timeout`
drains cleanly — no in-flight ERP call is killed mid-request. Verify this in staging by starting
a deploy while a synthetic long-running task is in flight and confirming it completes rather than
appearing in the DLQ with a generic cancellation error.

**Alerting — two signals, not one.** Queue *depth* alone is a lagging, misleading signal at high
throughput: a queue holding 5,000 jobs is healthy at 1,000 tasks/sec (5 seconds of buffer) and a
crisis at 10 tasks/sec (500 seconds of buffer). Alert on **task age** — the time since the oldest
unprocessed job was enqueued — as the primary signal, and depth as a secondary confirmation:

```
ZCOUNT arq:queue -inf <now_ms>              # jobs whose scheduled time has arrived
                                             # (excludes retries still in backoff)
oldest_ready_job_age = now_ms - ZRANGEBYSCORE arq:queue -inf <now_ms> LIMIT 0 1 WITHSCORES
```

- Warning: oldest ready job age > 60 s, or `ZCARD arq:queue` > 5,000 for 5 consecutive minutes.
- Page: oldest ready job age > 300 s, or DLQ writes exceed 5/minute (signals a systemic failure,
  not isolated bad data).

**DLQ drain runbook.** See §5.6 — group by `error`, fix root cause, replay via
`POST /admin/dlq/requeue`, confirm the dead-letter rate returns to zero, retain raw entries a
week before purge.

**Scheduled task safety.** The periodic sweep for stuck invoices (§1) must run from exactly one
scheduler instance at a time — ARQ's `cron` jobs are scheduled per-worker-process, so running the
sweep function in every worker replica's `WorkerSettings.cron_jobs` list would run it N times per
tick. Use a single dedicated scheduler deployment (replica count 1, or a leader-election sidecar)
rather than adding the cron job to the general worker fleet's settings.

**Capacity review cadence.** Re-run the §2 arithmetic monthly against actual `task_executions_total`
and P99 duration metrics; the 6x peak multiplier and 800 ms weighted average duration are
estimates that should be replaced with measured values as soon as they exist.

---

## 9. Common Pitfalls & War Stories

**The poison message with no DLQ.** A malformed webhook payload from a legacy partner integration
produced an ERP task whose handler threw an uncaught `KeyError` on every single attempt — not a
transient failure, a permanent one. Before this system's DLQ ceiling existed, the retry policy was
"retry forever," and that one message retried roughly **40,000 times over 6 hours**, consuming an
estimated **80% of the worker fleet's capacity** and delaying every legitimate task behind it by
an average of **25 minutes**. Fix: exactly §5.3's 3-attempt ceiling plus a DLQ — a permanently
failing task costs the system 3 attempts, then stops.

**Retry without jitter — the thundering herd.** Before jitter existed, backoff was pure
`base * 2^attempt`. During a 90-second SAP outage, roughly 8,000 ERP tasks failed within the same
few seconds and were all scheduled to retry at *exactly* `now + 5s`. When SAP recovered, all 8,000
retries landed in the same one-second window — enough to trip SAP's own rate limiter and cause a
**second, self-inflicted outage lasting another 4 minutes**, worse than the original failure.
Adding `random.uniform(0, 10)` jitter spreads that same burst across a 10-second window (roughly
800/second), comfortably under SAP's limit.

**A queue that silently grew because nobody alerted on depth or age.** A Helm values typo shipped
`max_jobs=5` instead of `50` for three days before anyone noticed — throughput dropped to a tenth
of required capacity, and the queue grew by roughly **270,000 unprocessed jobs** before an
unrelated on-call engineer happened to notice Redis memory climbing toward the eviction threshold.
There was no alert on depth or age at the time; §8's two-signal rule exists directly because of
this incident.

**A long task killed mid-flight by a deploy with no graceful drain.** Before
`terminationGracePeriodSeconds` was aligned with `job_timeout`, a routine deploy sent SIGKILL to
worker pods 10 seconds after SIGTERM — inside the 30-second ERP call timeout. An in-flight ERP
call was killed mid-request, with SAP's side sometimes still processing the write. Because the
idempotency claim had already been taken and never released (the process died before its `except`
block ran), the redelivered job saw the key as already claimed and **skipped the retry
entirely** — the inventory update was lost silently, no DLQ entry, no alert. It surfaced two weeks
later as an inventory reconciliation mismatch. Fix: align the grace period with the job timeout
(§8), and add a TTL-based sweep that alerts on any claimed-but-never-resolved idempotency key.

**Visibility-timeout-style duplicate execution.** ARQ's sorted-set model has no SQS-style
visibility timeout, but the same bug class appears whenever a worker runs longer than a monitor's
stuck-job threshold. An ops script that re-enqueued any job whose result had not appeared within
15 seconds — under the invoice task's occasional 20+ second P99 during a slow S3 upload — caused
**duplicate invoice generation for roughly 1,200 orders** in one weekend. The idempotency claim
prevented double-billing, but the wasted S3 uploads and doubled worker load were pure overhead.
Fix: any "is this stuck" threshold must sit well above measured P99, not the median, and should
prefer checking the idempotency key over blindly re-enqueueing.

---

## 10. Capacity Planning

**Worker fleet, from §2's Little's Law result.** 24 worker processes at `max_jobs=50` gives
1,200 concurrent task capacity against a required 890 at peak — roughly 35% headroom, enough to
absorb one worker replica being drained mid-deploy (§8) without falling under the required
capacity. Split as 18 processes on `arq:queue` (default) and 6 on `arq:high` (invoice), matching
the priority-lane design in §5.6.

**Redis broker sizing.** Steady-state queue memory is trivial (~2.75 MB at a 5,000-job buffer,
§2), so size against the **worst case**: a 30-minute downstream outage at peak produces roughly
1.1 GB of backlogged jobs. Budget ~4 GB for that (3.5x), plus the idempotency key set (48-hour
TTL, `15,000,000 x 2 x 80 bytes ≈ 2.4 GB` resident) and the 24-hour result backend (~3 GB, §2) —
none of which may evict during exactly the outage that most needs the buffer to hold. One
`cache.r7g.xlarge`-class instance (26 GB) covers all three; a Redis Cluster split
(idempotency/results on one shard group, the queue on another) is the next step.

**Queue-depth and task-age alert thresholds, derived from the arithmetic above.**

| Signal | Warning | Page | Basis |
|--------|---------|------|-------|
| `ZCARD arq:queue` | > 5,000 for 5 min | > 20,000 | 5,000 is the steady-state buffer size used in §2's memory estimate; 20,000 is roughly 18 seconds of peak throughput's worth, or a sign the backlog is compounding |
| Oldest ready job age | > 60 s | > 300 s | 300 s is roughly 5x a customer's tolerance for a delayed confirmation email before it reads as "broken" |
| DLQ write rate | > 1/min sustained | > 5/min | 5/min for 5 minutes (25 dead-lettered tasks) is the threshold past which the poison-message war story (§9) becomes a capacity problem, not just a data-quality one |

**Monthly cost.** Illustrative list-price estimates for sizing discussions, not a vendor quote.

```
Worker fleet:   24 x c6g.large-class (2 vCPU, 4 GB) at ~$0.068/hr
                = 24 x $0.068 x 730 hr/month ≈ $1,191/month

Redis:          1 x cache.r7g.xlarge (26 GB) at ~$0.315/hr
                = $0.315 x 730 hr/month ≈ $230/month

PostgreSQL:     task_audit table adds ~912 GB/year (§2) to the existing orders DB;
                incremental storage at ~$0.115/GB-month
                = 912,000 MB / 1000 x $0.115 / 12 months-amortized-view
                ≈ 912 GB x $0.115/GB-month ≈ $105/month incremental

Outbox relay:   2 x t4g.small-class (redundant pair) at ~$0.0168/hr
                = 2 x $0.0168 x 730 hr/month ≈ $25/month

Total incremental infrastructure: ≈ $1,191 + $230 + $105 + $25 ≈ $1,551/month
```

The worker fleet dominates the bill, the expected shape for an I/O-bound task system — which
argues for §5.1's async-native choice (fewer, denser worker processes per dollar than
thread-per-task) more than for shrinking any one component.

---

## 11. Interview Discussion Points

**Q: Why not use FastAPI's built-in BackgroundTasks for the order tasks?**
It runs on the same event loop as the request, inside the same process. If that process is
killed by a deploy, an OOM kill, or a crash, every pending task is lost with no retry and no
record. Fine for disposable work like a hit counter; wrong for invoice generation and ERP
updates, which need a durable broker outside the request process.

**Q: What problem does the transactional outbox solve that enqueueing directly from the handler does not?**
It closes the dual-write gap: writing the order and enqueueing to Redis are two independent
operations with no shared transaction, so a crash between them can leave the order committed
with no tasks ever enqueued. Writing an outbox row in the *same* transaction as the order makes
"the order exists" and "its tasks will eventually be enqueued" atomic; a separate relay,
decoupled from the request path, does the actual enqueue and can retry safely because every
task handler is idempotent.

**Q: How do you prevent a retried task from sending the email twice?**
Before doing any work, the handler claims `idem:{order_id}:{task_name}` with a single
`SET key 1 NX EX 172800`, which returns a value for exactly one caller and `None` for every
duplicate delivery. A `GET` then `SET` would not do — it is a check-then-act race, and two
concurrent redeliveries would both read the key as absent. Because the claim happens before the
call, every failure path deletes the key so a legitimate retry can claim it again.

**Q: Walk through the double-charge bug this design is defending against.**
If a worker decrements ERP inventory then crashes before recording success, the broker's
at-least-once guarantee redelivers the same task. Without a claim taken *before* the SAP call,
the redelivered task has no way to know the first attempt already ran and decrements stock
twice. The claim itself is the record of "this attempt owns the work," and it survives a crash
because it lives in Redis, not in the crashed process's memory.

**Q: What is the retry delay formula and why add jitter?**
`delay = min(5 * 2^(attempt-1), 60) + uniform(0, 10)`. Without jitter, every worker that failed
against the same outage retries at the identical instant, producing a synchronized thundering
herd that can re-trip the service that just recovered — an 8,000-task herd did exactly that
(§9). Jitter spreads the same retry volume across a window instead of a single instant.

**Q: What happens when a job exceeds max retries?**
The handler catches the terminal exception, pushes a JSON record onto the Redis list `arq:dlq`,
stores a `dead_lettered` result, and returns a value instead of re-raising — returning is what
stops ARQ scheduling further retries. `GET /admin/dlq` inspects entries; `POST /admin/dlq/requeue`
replays one after investigation, following §5.6's drain procedure.

**Q: How do you scale the worker pool under load, and how did you size it?**
Little's Law: concurrent tasks in flight equals arrival rate times average duration. At a peak
of ~1,113 tasks/sec and ~800 ms average, roughly 890 tasks are in flight; at `max_jobs=50` per
worker that needs about 18 processes, rounded to 24 for deploy headroom (§2, §10). Scaling from
there is horizontal — workers compete on the same queue with no coordination needed. Monitor
with `ZCOUNT arq:queue -inf <now_ms>` (excludes retries still in backoff) rather than raw
`ZCARD`, and page on oldest-ready-job age, not depth alone — depth lies at high throughput (§8).

**Q: What is the ordering guarantee across the three tasks, and when would that not be enough?**
None — they are enqueued independently and may complete in any order, correct here since none
depends on another's output. If a real dependency existed — the email must link the *generated*
invoice — a plain queue stops being sufficient, because "enqueue B when A completes" is a
workflow, not an independent task. That is where Temporal (§5.1, §6) earns its cost: it durably
executes the whole chain and replays from the correct step after a crash.

**Q: How would you add task prioritization, and what failure does it prevent?**
ARQ has no priority queue — `queue_name` is one string per `WorkerSettings`, so prioritization is
a deployment decision: route invoice generation to `arq:high` via `_queue_name` on
`enqueue_job`, and run more replicas against it. This prevents starvation — failing ERP retries
sharing a queue with invoice jobs would otherwise delay every invoice indefinitely, since a
FIFO/sorted-set queue has no notion that one task type matters more.

**Q: How do you test the retry and DLQ logic without hitting real external services?**
`pytest-asyncio` with `dependency_overrides` injects a fake ARQ pool, and
`httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test")` drives the
app without a real socket (the older `AsyncClient(app=app)` raises `TypeError` — `app=` was
removed in httpx 0.28.0). Task handlers are plain async functions callable directly in tests:
pass a mock `ctx` with `fakeredis.aioredis.FakeRedis` and a stub HTTP session, inject a failure
on the first N calls, and assert the DLQ length equals 1 after the final attempt.

**Q: What observability would you add in production?**
Structured logs at task start/completion (order_id, task_name, attempt, duration_ms, status); a
Prometheus counter `task_executions_total{task,status}` and histogram
`task_duration_seconds{task}`; and two alerts, not one — oldest-ready-job age and queue depth
together (§8, §10), because depth alone gave no warning in the silent-growth incident (§9). A
panel on `task_executions_total{status="dead_lettered"}` catches a poison message before it
reaches the 40,000-retry scale seen in that same incident.
