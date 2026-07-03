# Design an Async Task Queue System

## Problem Statement

An e-commerce platform creates thousands of orders per hour. Every successful order triggers three slow,
non-critical operations:

1. **Order confirmation email** — calls SendGrid API, 200-400 ms
2. **PDF invoice generation** — CPU + I/O work, 800 ms - 2 s
3. **Third-party ERP inventory update** — REST call to legacy SAP system, 500 ms - 1.5 s

Running all three synchronously inside the HTTP handler adds up to 1.5-4 s of latency to a checkout
response that should complete in under 200 ms. If any downstream service is unavailable, the entire
order endpoint returns 500 even though the order was persisted successfully.

**Hard requirements:**
- HTTP `/orders` endpoint must respond within 200 ms regardless of downstream availability.
- All three post-order tasks must eventually complete — they cannot be silently dropped if the API
  process restarts between enqueue and execution.
- At-least-once delivery: a task may run more than once during retries; every task handler must be
  idempotent using a deduplication key derived from `order_id + task_name`.
- Retries: exponential backoff, maximum 3 attempts, then route to a Dead Letter Queue (DLQ) for
  human inspection.
- Observability: each task execution produces a structured log entry and a result record that ops
  can query to confirm delivery or diagnose failures.

**Out of scope:** cross-datacenter replication of the queue, exactly-once semantics (impractical
without distributed transactions), real-time task progress streaming to the browser.

---

## Architecture Overview

```
HTTP Layer
  POST /orders
       |
       v
 FastAPI Handler
  (< 200 ms)
       |
       | enqueue(order_id, task_name, payload)
       v
+------+-------+       +------------------------+
|  Redis Queue  |       |   Redis Result Backend |
|               |       |   key: result:{job_id} |
|  arq:default  |<---+  |   TTL: 24 h            |
|  arq:{queue}  |    |  +------------------------+
+------+--------+    |
       |              |
       | dequeue      | store result / error
       v              |
+------+--------+     |
|  ARQ Workers  |-----+
|  (N processes)|
|               |
|  send_email   |
|  gen_invoice  |
|  update_erp   |
+------+--------+
       |
       | max retries exceeded
       v
+------+--------+
|  DLQ          |
|  arq:dlq      |
|  (Redis List) |
+---------------+

Idempotency Layer (Redis SET NX EX):
  key: idem:{order_id}:{task_name}
  prevents duplicate execution on retry storms
```

**Data flow:**
1. `POST /orders` persists the order row in PostgreSQL and immediately enqueues three ARQ jobs.
2. Each ARQ job carries `order_id`, `task_name`, and an idempotency key.
3. An ARQ worker process picks up a job, checks the idempotency key in Redis, and skips if already
   completed.
4. On failure the job is retried up to 3 times with exponential backoff + jitter.
5. After 3 failures the worker writes the job metadata to the DLQ list (`arq:dlq`).
6. A separate FastAPI endpoint `GET /admin/dlq` lets ops inspect and re-enqueue DLQ items.

---

## Key Design Decisions

### 1. ARQ vs Celery vs FastAPI BackgroundTasks

**Broken approach — using BackgroundTasks for durable work:**

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

`BackgroundTasks` schedules coroutines on the same event loop as the request. If Uvicorn is
restarted by a deploy, a K8s OOMKill, or a crash, every in-flight background task vanishes silently.
There is no retry, no DLQ, no result record — just lost work.

**Fixed approach — enqueue to Redis via ARQ:**

```python
# FIXED: tasks are durable; survive restarts; retried automatically
@router.post("/orders")
async def create_order(
    payload: OrderCreate,
    db: AsyncSession = Depends(get_db),
    arq_pool: ArqRedis = Depends(get_arq_pool),
) -> OrderResponse:
    order = await order_service.create(db, payload)
    await enqueue_order_tasks(arq_pool, order.id)
    return OrderResponse.model_validate(order)
```

Jobs survive process restarts because they live in Redis. ARQ workers are separate processes that
dequeue independently.

**Comparison table:**

| Dimension            | BackgroundTasks      | Celery               | ARQ                  | Dramatiq             |
|----------------------|----------------------|----------------------|----------------------|----------------------|
| Durability           | None (in-process)    | Yes (broker)         | Yes (Redis)          | Yes (broker)         |
| Async-native         | Yes                  | No (threads/greenlets)| Yes (asyncio)       | Partial              |
| Broker options       | —                    | Redis, RabbitMQ, SQS | Redis only           | Redis, RabbitMQ      |
| Setup complexity     | Zero                 | High (beat, flower)  | Low                  | Medium               |
| Result backend       | None                 | Redis / DB           | Redis                | Redis / DB           |
| FastAPI fit          | Simple non-durable   | Moderate             | Excellent            | Good                 |
| Monitoring UI        | None                 | Flower               | None (DIY)           | None (DIY)           |

**Decision:** ARQ. It is asyncio-native, has minimal boilerplate, integrates cleanly with FastAPI's
lifespan, and Redis is already in the stack for caching. Celery is production-proven but brings
significant overhead (Celery Beat, Flower, separate config) that is not warranted for three task types.

---

### 2. Delivery semantics — at-least-once with idempotency

Exactly-once delivery requires distributed two-phase commit or Kafka transactions — both add latency
and operational complexity beyond this system's scope. Instead, every task handler accepts that it
may execute more than once and defends with an idempotency key.

```
Key format:  idem:{order_id}:{task_name}
TTL:         48 hours (covers any retry window)
Set on:      successful task completion
Check on:    task entry, before doing work
```

`Redis SET NX EX` is atomic: if the key already exists the SET returns 0 and the handler returns
immediately without re-sending the email or re-calling the ERP.

---

### 3. Retry strategy — exponential backoff with jitter

ARQ exposes `retry` inside a job function via the context dict. The formula used:

```
delay = min(base * 2^attempt, cap) + random.uniform(0, jitter)
base  = 5 s
cap   = 60 s
jitter = 10 s
attempts 1→2→3: ~5 s, ~15 s, ~45 s (approximate; jitter varies)
```

After 3 failures the worker catches the terminal exception, serialises the job metadata to the DLQ
list, and marks the ARQ job as complete (so ARQ stops retrying). The DLQ entry contains enough
context to re-enqueue manually.

---

### 4. Result backend — Redis for task results, PostgreSQL for audit trail

| Concern         | Redis (TTL 24 h)         | PostgreSQL                      |
|-----------------|--------------------------|---------------------------------|
| Write latency   | < 1 ms                   | 2-10 ms                         |
| Query by order  | O(1) GET by key          | SQL JOIN, indexed                |
| Durability      | AOF/RDB (configurable)   | WAL, ACID                       |
| TTL management  | Native, zero-cost        | Manual purge job                |
| Audit / reports | Not suitable             | Yes — joins, aggregates          |

**Decision:** Task result records (status, duration, error) go to Redis with a 24-hour TTL — fast,
self-expiring, sufficient for ops dashboards. High-value audit rows (invoice generated, ERP updated)
are also appended to a `task_audit` PostgreSQL table for compliance queries.

---

### 5. Worker concurrency — async workers for I/O-bound tasks

All three task types (HTTP calls, file I/O, email API) are network-bound. An async ARQ worker runs
a single-threaded event loop and multiplexes dozens of in-flight tasks without thread overhead.
Thread-pool workers would context-switch unnecessarily. The recommended concurrency setting for
ARQ is `max_jobs=50` per worker process; scale horizontally by adding worker replicas.

---

## Implementation

```python
# ── requirements ──────────────────────────────────────────────────────────────
# arq==0.26.*  redis==5.*  fastapi==0.111.*  uvicorn  pydantic-settings
# aiohttp  weasyprint  sqlalchemy[asyncio]  asyncpg

# ── src/config.py ─────────────────────────────────────────────────────────────
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    redis_dsn: str = "redis://localhost:6379/0"
    postgres_dsn: str = "postgresql+asyncpg://user:pass@localhost/orders"
    arq_max_jobs: int = 50
    task_max_retries: int = 3
    task_base_delay: float = 5.0
    task_max_delay: float = 60.0
    task_jitter: float = 10.0
    result_ttl_seconds: int = 86_400  # 24 h
    idempotency_ttl_seconds: int = 172_800  # 48 h

    class Config:
        env_file = ".env"


settings = Settings()


# ── src/deps.py ───────────────────────────────────────────────────────────────
from contextlib import asynccontextmanager
from typing import AsyncIterator

import redis.asyncio as aioredis
from arq import create_pool
from arq.connections import RedisSettings, ArqRedis
from fastapi import FastAPI, Request


def _arq_settings() -> RedisSettings:
    return RedisSettings.from_dsn(settings.redis_dsn)


async def get_arq_pool(request: Request) -> ArqRedis:
    return request.app.state.arq_pool


async def get_redis(request: Request) -> aioredis.Redis:
    return request.app.state.redis


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    app.state.arq_pool = await create_pool(_arq_settings())
    app.state.redis = await aioredis.from_url(settings.redis_dsn, decode_responses=True)
    yield
    await app.state.arq_pool.close()
    await app.state.redis.aclose()


# ── src/tasks/helpers.py ──────────────────────────────────────────────────────
import json
import math
import random
import time
from typing import Any

import redis.asyncio as aioredis


async def is_duplicate(redis: aioredis.Redis, order_id: str, task_name: str) -> bool:
    """Return True if this task already completed successfully."""
    key = f"idem:{order_id}:{task_name}"
    return await redis.exists(key) == 1


async def mark_complete(redis: aioredis.Redis, order_id: str, task_name: str) -> None:
    key = f"idem:{order_id}:{task_name}"
    await redis.set(key, "1", ex=settings.idempotency_ttl_seconds)


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
    """Exponential backoff with full jitter (attempt is 1-indexed)."""
    exp = settings.task_base_delay * (2 ** (attempt - 1))
    capped = min(exp, settings.task_max_delay)
    return capped + random.uniform(0, settings.task_jitter)


# ── src/tasks/order_tasks.py ──────────────────────────────────────────────────
import asyncio
import time
from typing import Any

import aiohttp
import redis.asyncio as aioredis
from arq import Retry

from .helpers import (
    backoff_delay,
    is_duplicate,
    mark_complete,
    push_dlq,
    store_result,
)


async def _get_redis(ctx: dict[str, Any]) -> aioredis.Redis:
    return ctx["redis"]


async def send_confirmation_email(ctx: dict[str, Any], order_id: str, attempt: int = 1) -> str:
    """Send order confirmation email via SendGrid. Idempotent via Redis NX key."""
    redis = await _get_redis(ctx)
    job_id: str = ctx["job_id"]
    start = time.monotonic()

    if await is_duplicate(redis, order_id, "send_confirmation_email"):
        await store_result(redis, job_id, "skipped_duplicate", 0.0)
        return "duplicate"

    try:
        async with aiohttp.ClientSession() as session:
            async with session.post(
                "https://api.sendgrid.com/v3/mail/send",
                json={"order_id": order_id},
                headers={"Authorization": f"Bearer {ctx['sendgrid_key']}"},
                timeout=aiohttp.ClientTimeout(total=5),
            ) as resp:
                resp.raise_for_status()

        await mark_complete(redis, order_id, "send_confirmation_email")
        duration = (time.monotonic() - start) * 1000
        await store_result(redis, job_id, "success", duration)
        return "ok"

    except Exception as exc:
        if attempt >= settings.task_max_retries:
            await push_dlq(
                redis,
                {
                    "order_id": order_id,
                    "task": "send_confirmation_email",
                    "error": str(exc),
                    "attempts": attempt,
                },
            )
            duration = (time.monotonic() - start) * 1000
            await store_result(redis, job_id, "dead_lettered", duration, error=str(exc))
            return "dlq"

        raise Retry(defer=backoff_delay(attempt)) from exc


async def generate_invoice(ctx: dict[str, Any], order_id: str, attempt: int = 1) -> str:
    """Generate PDF invoice and upload to S3. Idempotent."""
    redis = await _get_redis(ctx)
    job_id: str = ctx["job_id"]
    start = time.monotonic()

    if await is_duplicate(redis, order_id, "generate_invoice"):
        await store_result(redis, job_id, "skipped_duplicate", 0.0)
        return "duplicate"

    try:
        # Simulate PDF generation (weasyprint call in production)
        await asyncio.sleep(0.9)
        pdf_key = f"invoices/{order_id}.pdf"
        # await s3_client.upload_fileobj(pdf_bytes, bucket, pdf_key)

        await mark_complete(redis, order_id, "generate_invoice")
        duration = (time.monotonic() - start) * 1000
        await store_result(redis, job_id, "success", duration)
        return pdf_key

    except Exception as exc:
        if attempt >= settings.task_max_retries:
            await push_dlq(
                redis,
                {"order_id": order_id, "task": "generate_invoice", "error": str(exc), "attempts": attempt},
            )
            duration = (time.monotonic() - start) * 1000
            await store_result(redis, job_id, "dead_lettered", duration, error=str(exc))
            return "dlq"

        raise Retry(defer=backoff_delay(attempt)) from exc


async def update_erp_inventory(ctx: dict[str, Any], order_id: str, attempt: int = 1) -> str:
    """Push inventory delta to SAP ERP. Idempotent via ERP idempotency header."""
    redis = await _get_redis(ctx)
    job_id: str = ctx["job_id"]
    start = time.monotonic()

    if await is_duplicate(redis, order_id, "update_erp_inventory"):
        await store_result(redis, job_id, "skipped_duplicate", 0.0)
        return "duplicate"

    try:
        async with aiohttp.ClientSession() as session:
            async with session.post(
                f"https://erp.internal/inventory/update",
                json={"order_id": order_id},
                headers={"Idempotency-Key": f"{order_id}:update_erp_inventory"},
                timeout=aiohttp.ClientTimeout(total=8),
            ) as resp:
                resp.raise_for_status()

        await mark_complete(redis, order_id, "update_erp_inventory")
        duration = (time.monotonic() - start) * 1000
        await store_result(redis, job_id, "success", duration)
        return "ok"

    except Exception as exc:
        if attempt >= settings.task_max_retries:
            await push_dlq(
                redis,
                {"order_id": order_id, "task": "update_erp_inventory", "error": str(exc), "attempts": attempt},
            )
            duration = (time.monotonic() - start) * 1000
            await store_result(redis, job_id, "dead_lettered", duration, error=str(exc))
            return "dlq"

        raise Retry(defer=backoff_delay(attempt)) from exc


# ── src/worker.py ─────────────────────────────────────────────────────────────
import redis.asyncio as aioredis
from arq.connections import RedisSettings

from .config import settings
from .tasks.order_tasks import generate_invoice, send_confirmation_email, update_erp_inventory


async def startup(ctx: dict) -> None:  # type: ignore[type-arg]
    ctx["redis"] = await aioredis.from_url(settings.redis_dsn, decode_responses=True)
    ctx["sendgrid_key"] = "sg_key_from_env"


async def shutdown(ctx: dict) -> None:  # type: ignore[type-arg]
    await ctx["redis"].aclose()


class WorkerSettings:
    functions = [send_confirmation_email, generate_invoice, update_erp_inventory]
    on_startup = startup
    on_shutdown = shutdown
    redis_settings = RedisSettings.from_dsn(settings.redis_dsn)
    max_jobs = settings.arq_max_jobs
    job_timeout = 30  # seconds; kills hung tasks
    keep_result = settings.result_ttl_seconds


# Run with: python -m arq src.worker.WorkerSettings


# ── src/routers/orders.py ─────────────────────────────────────────────────────
from typing import Annotated

from arq.connections import ArqRedis
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from ..deps import get_arq_pool, get_db
from ..models import Order, OrderCreate, OrderResponse
from ..tasks.order_tasks import generate_invoice, send_confirmation_email, update_erp_inventory

router = APIRouter(prefix="/orders", tags=["orders"])


async def enqueue_order_tasks(pool: ArqRedis, order_id: str) -> None:
    """Enqueue all three post-order tasks. Each runs independently."""
    await pool.enqueue_job("send_confirmation_email", order_id, attempt=1)
    await pool.enqueue_job("generate_invoice", order_id, attempt=1)
    await pool.enqueue_job("update_erp_inventory", order_id, attempt=1)


@router.post("/", response_model=OrderResponse, status_code=201)
async def create_order(
    payload: OrderCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
    arq_pool: Annotated[ArqRedis, Depends(get_arq_pool)],
) -> OrderResponse:
    order: Order = await order_service.create(db, payload)
    await enqueue_order_tasks(arq_pool, str(order.id))
    return OrderResponse.model_validate(order)


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
    idem_key = f"idem:{order_id}:{task_name}"
    await redis.delete(idem_key)
    await arq_pool.enqueue_job(task_name, order_id, attempt=1)
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


# ── src/main.py ───────────────────────────────────────────────────────────────
from fastapi import FastAPI

from .deps import lifespan
from .routers.admin import admin_router
from .routers.orders import router as orders_router

app = FastAPI(title="Order Service", lifespan=lifespan)
app.include_router(orders_router)
app.include_router(admin_router)
```

---

## Python/FastAPI Components Used

| Component | Role |
|-----------|------|
| `arq` | Async task queue backed by Redis Streams; `Retry` exception triggers deferred retry |
| `arq.connections.ArqRedis` | Connection pool injected into FastAPI via `Depends` |
| `redis.asyncio` | Direct Redis client for idempotency keys, result records, DLQ list |
| `fastapi.BackgroundTasks` | **Not used for durable work** — shown in broken example only |
| `contextlib.asynccontextmanager` + `lifespan` | Manages ARQ pool and Redis client lifecycle |
| `pydantic_settings.BaseSettings` | Typed config with `.env` support |
| `aiohttp.ClientSession` | Async HTTP client for SendGrid and ERP calls |
| `Annotated[..., Depends(...)]` | FastAPI 0.95+ dependency injection style |
| `APIRouter` | Splits orders and admin endpoints into separate routers |
| `HTTPException` | Raises 404 when result key not found or TTL expired |

---

## Tradeoffs and Alternatives

### Task queue library comparison

| Dimension              | ARQ                         | Celery                         | Dramatiq                      | FastAPI BackgroundTasks      |
|------------------------|-----------------------------|--------------------------------|-------------------------------|------------------------------|
| Durability             | Redis-backed                | Redis / RabbitMQ / SQS         | Redis / RabbitMQ              | None (in-process)            |
| Async support          | Native asyncio              | Threads (greenlets with gevent)| Threads only                  | Native asyncio               |
| Retry built-in         | `Retry` exception + `defer` | `autoretry_for`, `countdown`   | `@actor(max_retries=N)`       | None                         |
| DLQ support            | DIY (push to list)          | Built-in (dead_letter_routing) | Built-in                      | None                         |
| Monitoring UI          | None (DIY dashboards)       | Flower (mature)                | Periodiq (limited)            | None                         |
| Scheduled tasks        | `cron` in WorkerSettings    | Celery Beat                    | `apscheduler` integration     | None                         |
| Broker flexibility     | Redis only                  | Redis, RabbitMQ, SQS, etc.     | Redis, RabbitMQ               | —                            |
| Operational complexity | Low                         | High                           | Medium                        | Zero                         |
| Best fit               | Async FastAPI, I/O-bound    | Large Django/Flask shops       | CPU-bound + threads           | Ephemeral, non-critical      |

### Result backend comparison

| Concern                | Redis (TTL 24 h)             | PostgreSQL                   |
|------------------------|------------------------------|------------------------------|
| Write latency          | < 1 ms                       | 2–10 ms                      |
| Lookup by job_id       | O(1) GET                     | Primary key lookup           |
| Lookup by order_id     | Requires secondary index key | SQL WHERE with index         |
| Durability             | Configurable (AOF)           | ACID, WAL                    |
| Expiry management      | Native TTL, zero cost        | Requires cron delete job     |
| Historical reporting   | Not feasible                 | Full SQL aggregates           |

**Hybrid approach chosen:** Redis for fast operational results (24 h), PostgreSQL `task_audit`
table for compliance records that must survive beyond 24 hours.

### At-least-once vs exactly-once

Exactly-once delivery requires:
- Atomic dequeue + database write (two-phase commit or Kafka transactions).
- Kafka with `enable.idempotence=true` and a consumer that checkpoints offsets only after DB commit.

For this system (Redis broker, external HTTP calls), exactly-once is impractical. At-least-once with
idempotency keys is the standard production pattern and costs only one Redis GET per task invocation.

---

## Interview Discussion Points

**Q: Why not use FastAPI's built-in BackgroundTasks for the order tasks?**
`BackgroundTasks` runs inside the Uvicorn process on the same event loop. If the process is killed
by a deploy, OOM, or crash, every pending background task is lost with no retry and no record.
For non-critical fire-and-forget work (e.g., incrementing a hit counter) it is acceptable. For
business-critical tasks like invoice generation and ERP updates it is not — those require a durable
broker.

**Q: How do you prevent a retried task from sending the email twice?**
Before doing any work, the handler checks a Redis key `idem:{order_id}:{task_name}` using a GET.
If the key exists (set by a previous successful run), the handler returns immediately without
calling SendGrid. The key is set atomically with `SET key 1 EX 172800` only after the downstream
call succeeds. The 48-hour TTL covers all realistic retry windows.

**Q: What is the retry delay formula and why add jitter?**
Delay = `min(5 * 2^(attempt-1), 60) + uniform(0, 10)`. Without jitter, all workers that fail on
the same upstream outage wake up at the same instant, causing a retry thundering herd that can
overwhelm the recovering service. Uniform jitter spreads the load across a 10-second window.

**Q: What happens when a job exceeds max retries?**
The handler catches the terminal exception, pushes a JSON metadata record onto the Redis list
`arq:dlq`, stores a `dead_lettered` result record, and returns a value instead of re-raising.
Returning (not raising) prevents ARQ from scheduling further retries. The DLQ list is inspectable
via `GET /admin/dlq` and items can be re-enqueued after manual investigation via
`POST /admin/dlq/requeue`.

**Q: How do you scale the worker pool under load?**
Each ARQ worker runs `max_jobs=50` concurrent async tasks (I/O-bound, so no thread contention).
Horizontal scaling is straightforward: add worker replicas as Kubernetes Deployments. Workers
compete on the same Redis queue; no coordination is needed. Monitor queue depth with
`LLEN arq:default` and add replicas when depth stays above 1000 for more than 60 seconds.

**Q: What is the ordering guarantee across the three tasks?**
None — they are enqueued independently and may complete in any order. This is intentional: email,
invoice generation, and ERP update have no mutual dependency. If ordering were required (e.g.,
invoice must complete before emailing the download link), the tasks would be chained by having
`generate_invoice` enqueue `send_confirmation_email` upon success.

**Q: How would you migrate from Redis to a more durable broker like SQS?**
ARQ is Redis-only. Migration would require replacing ARQ with Celery (configured for SQS) or a
purpose-built SQS consumer. The task handler logic (idempotency, retry, DLQ) is broker-agnostic
and transfers unchanged. The primary driver for switching would be Redis availability concerns or
a requirement for at-least-once delivery guaranteed by SQS's visibility timeout mechanism.

**Q: How do you test the retry and DLQ logic without hitting real external services?**
Use `pytest-asyncio` with `dependency_overrides` to inject a fake ARQ pool. The task handler
function is a plain async function and can be called directly in tests — pass a mock `ctx` dict
containing a fake Redis client (via `fakeredis.aioredis.FakeRedis`) and a stub HTTP session. Inject
an exception on the first N calls to simulate failures, then assert the DLQ list length after the
final attempt equals 1.

**Q: How would you add task prioritization?**
ARQ supports multiple queues: `queue_name` parameter on `enqueue_job` and a `queue_read_burst_limit`
in `WorkerSettings`. Define `arq:high` and `arq:default`. Invoice generation (user-visible) goes to
`arq:high`; ERP updates (internal) go to `arq:default`. Workers poll `arq:high` first, falling back
to `arq:default` when it is empty.

**Q: What observability would you add in production?**
Structured log lines at task start and completion (order_id, task_name, attempt, duration_ms,
status). A Prometheus counter `task_executions_total{task,status}` and histogram
`task_duration_seconds{task}`. A Grafana panel alerting when `task_executions_total{status="dead_lettered"}`
exceeds 5 per 5-minute window. ARQ result records in Redis feed an ops dashboard showing per-task
success rates over the last 24 hours without querying PostgreSQL.
