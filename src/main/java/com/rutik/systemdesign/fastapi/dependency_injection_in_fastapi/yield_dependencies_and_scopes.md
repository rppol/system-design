# Yield Dependencies and Scopes in FastAPI

---

## 1. Concept Overview

`yield` dependencies are the lifecycle management layer of FastAPI's dependency injection system. Where a plain `Depends()` callable returns a value and exits, a `yield` dependency suspends execution at the `yield` point: code before `yield` runs during request setup, the yielded value is injected into the route handler, and code after `yield` runs during teardown — after the response has been sent to the client.

FastAPI implements this using `contextlib.contextmanager` / `contextlib.asynccontextmanager` semantics. It detects whether a dependency callable is a generator function (contains `yield`) at import time by inspecting the function with `inspect.isgeneratorfunction()` and `inspect.isasyncgenfunction()`. At request time it wraps the generator in a context manager and drives it through the setup → handler → teardown lifecycle.

Key capabilities covered in this sub-file:

- Generator execution model and teardown guarantee via `finally`
- Exception propagation into teardown (how FastAPI calls `generator.throw(exc)`)
- Request-scoped caching: one instance per request, keyed by dependency function object
- `use_cache=False` for intentional multiple instances
- Nested `yield` dependencies and DAG teardown order (LIFO)
- Request scope vs app scope: why connection pools belong in `lifespan`, not `Depends`
- Class-based `yield` dependencies using `__call__` as an async generator
- Testing: `dependency_overrides` with generator replacements
- Performance characteristics: ~0.05 ms overhead per dependency per request

Python version: 3.11/3.12. FastAPI version: 0.110+. Pydantic version: v2.

Cross-reference: [Dependency Injection in FastAPI](../README.md)

---

## 2. Intuition

> A `yield` dependency is a hotel concierge who checks you in (setup), hands you the room key (the yielded value), and is guaranteed to check you out (teardown) no matter how the stay ended — whether you left quietly or triggered a fire alarm.

**Mental model.** Think of every `yield` dependency as a `with` block that wraps the route handler. FastAPI converts your generator function into a context manager automatically. The region before `yield` is `__enter__`; the region after `yield` (especially `finally`) is `__exit__`. The handler runs inside the `with` block.

**Why it matters.** Resources like database sessions, file handles, HTTP clients, and Redis connections must be released regardless of whether the handler succeeded, raised a validation error, or caused an unhandled 500. Without `yield` dependencies you either leak resources or write brittle try/finally blocks in every handler. With `yield`, the teardown contract is encoded once in the dependency.

**Key insight.** Teardown runs after the response is sent. This means client latency is unaffected by cleanup work. A slow `session.commit()` or `await client.aclose()` does not add to the observed response time — though it still consumes server resources. Design teardown accordingly: keep it fast; if cleanup is expensive, offload to a background task.

---

## 3. Core Principles

**1. Generator contract.** A `yield` dependency must yield exactly once. Yielding zero times (returning early) causes a `RuntimeError`. Yielding more than once causes `StopIteration` to be raised from FastAPI's driving code.

**2. Teardown guarantee.** FastAPI drives the generator inside a try/finally equivalent. Even if the route handler raises an unhandled exception, or an `HTTPException` is raised, the generator's teardown code executes. Use `finally:` not bare post-`yield` code to be explicit.

**3. Exception propagation.** If the route handler raises, FastAPI calls `generator.throw(exc)` rather than `next(generator)`. This means your `except` clause after `yield` can inspect the exception, perform compensating actions (e.g., rollback), and either handle it or let it propagate. Raising inside `except` replaces the original exception; swallowing it suppresses it.

**4. Request-scoped by default.** Every `yield` dependency is instantiated once per request, regardless of how many route handlers or sub-dependencies reference the same callable. The cache key is the dependency function object (identity, not name). Two distinct function objects that do the same thing are cached independently.

**5. LIFO teardown.** Nested `yield` dependencies tear down in reverse dependency order. If A depends on B, and B `yield`s first, B tears down before A. FastAPI builds a DAG at startup and executes teardown in leaf-first (reverse topological) order.

**6. Scope separation.** Request-scoped resources (DB session, per-request HTTP client, tenant context) belong in `Depends`. Application-scoped resources (connection pools, global HTTP clients, shared caches) belong in `lifespan`. Mixing them causes expensive re-initialization on every request.

---

## 4. Types / Architectures / Strategies

### Dependency Execution Models

| Model | How declared | Teardown | Caching scope | Typical use |
|-------|-------------|----------|--------------|-------------|
| Plain `Depends` | Regular function/async function | None | Per request | Parsed parameters, auth tokens |
| `yield` sync dep | Generator function (`def … yield`) | Yes, after response | Per request | SQLite sessions, file handles |
| `yield` async dep | Async generator function (`async def … yield`) | Yes, after response | Per request | asyncpg connections, aiohttp sessions |
| `lifespan` | `@asynccontextmanager` on FastAPI app | On app shutdown | App lifetime | Connection pools, global clients |
| Class `__call__` yield | Class instance whose `__call__` is an async gen | Yes, after response | Per request (one instance per request) | Configurable dep with injected config |

### Scope Decision Tree

```
Is the resource expensive to create (>1ms)?
  YES → Is it safe to share across requests?
    YES → lifespan (app scope)
    NO  → yield dep with its own creation
  NO  → yield dep or plain dep, either works

Does the resource have per-request identity (e.g., user session)?
  YES → yield dep (request scope, always)
  NO  → consider lifespan

Does the resource need cleanup (close/commit/rollback)?
  YES → yield dep
  NO  → plain dep
```

### Nested Dependency Patterns

**Linear chain** — A depends on B depends on C. Teardown order: C, B, A.

**Shared dependency** — Both A and B depend on C. FastAPI calls C once (cached) and tears it down once after both A and B are torn down.

**Diamond dependency** — A depends on B and C; both B and C depend on D. D is instantiated once (cached), torn down last.

---

## 5. Architecture Diagrams

### Single Yield Dependency Lifecycle

```
HTTP Request arrives
        |
        v
FastAPI resolves dependency graph
        |
        v
+-------------------------------+
|  yield dependency: setup      |  <-- code before yield executes
|  e.g., session = Session()   |
+-------------------------------+
        |
        | yields session
        v
+-------------------------------+
|  Route handler executes       |  <-- session injected as argument
|  result = handler(session)    |
+-------------------------------+
        |
        v
Response serialized and sent to client   <-- client receives response HERE
        |
        v
+-------------------------------+
|  yield dependency: teardown   |  <-- code after yield / finally block
|  e.g., session.close()       |
+-------------------------------+
        |
        v
Request context destroyed
```

### Nested Yield Dependencies — Execution Order

```
Request
  |
  +-- get_tenant()     [yield dep A — outer]
        |
        +-- get_db()   [yield dep B — inner, depends on A]
              |
              +-- get_user()  [yield dep C — innermost]
                    |
                    v
              Route Handler
                    |
              [teardown C]    <-- innermost tears down first
              [teardown B]
              [teardown A]    <-- outermost tears down last
```

### Exception Flow Through a Yield Dependency

```
Route Handler raises ValueError("bad input")
        |
        v
FastAPI catches exception
        |
        v
FastAPI calls generator.throw(ValueError("bad input"))
        |
        v  (inside yield dep)
except Exception as exc:
    await session.rollback()   <-- compensation
    raise                      <-- re-raise (or swallow to suppress)
        |
finally:
    await session.close()      <-- always runs
        |
        v
Exception propagates up → 500 response (or handled by exception handler)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Detection and Wrapping

FastAPI inspects each dependency at app startup:

```python
import inspect
from contextlib import asynccontextmanager, contextmanager

def is_gen_callable(call: Any) -> bool:
    # FastAPI's internal check (simplified)
    if inspect.isgeneratorfunction(call):
        return True
    # Handle class-based deps: check __call__
    call = getattr(call, "__call__", None)
    return inspect.isgeneratorfunction(call)

def is_async_gen_callable(call: Any) -> bool:
    if inspect.isasyncgenfunction(call):
        return True
    call = getattr(call, "__call__", None)
    return inspect.isasyncgenfunction(call)
```

When FastAPI identifies a generator callable, it wraps it in the appropriate context manager at resolve time:

```python
# Conceptual equivalent of what FastAPI does internally
if is_async_gen_callable(dependency_func):
    cm = asynccontextmanager(dependency_func)(*args, **kwargs)
    value = await cm.__aenter__()
    # ... handler runs with value ...
    await cm.__aexit__(exc_type, exc_val, exc_tb)
```

### 6.2 Basic Yield Dependency

```python
from collections.abc import AsyncGenerator
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

async_session_factory: async_sessionmaker[AsyncSession] = ...  # configured at startup

async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_factory() as session:
        try:
            yield session
            await session.commit()       # Only commits on clean exit
        except Exception:
            await session.rollback()     # Rolls back on any exception
            raise                        # Re-raise: caller sees the original error
        # session.close() handled by async_session_factory context manager
```

Usage in a route:

```python
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

router = APIRouter()

@router.post("/users/")
async def create_user(
    payload: UserCreate,
    session: AsyncSession = Depends(get_db),
) -> UserOut:
    user = User(**payload.model_dump())
    session.add(user)
    # commit happens in get_db teardown, not here
    return UserOut.model_validate(user)
```

### 6.3 BROKEN → FIX: App-Scoped Resource in a Request-Scoped Dependency

```python
# BROKEN: creates a new connection pool on every request — pool creation takes ~500ms
#         under load (100 req/s) this exhausts OS file descriptors and starves the event loop
import asyncpg
from fastapi import FastAPI, Request

DATABASE_URL = "postgresql://user:pass@host/db"

async def get_db_pool():
    # BROKEN: asyncpg.create_pool() is expensive (~500ms, opens min_size connections)
    pool = await asyncpg.create_pool(DATABASE_URL, min_size=5, max_size=20)
    try:
        yield pool
    finally:
        await pool.close()  # Pool destroyed after EACH request — 100 req/s = 100 pools/s

app = FastAPI()

@app.get("/data")
async def read_data(pool: asyncpg.Pool = Depends(get_db_pool)):
    async with pool.acquire() as conn:
        return await conn.fetchval("SELECT count(*) FROM records")
```

```python
# FIX: pool lives in lifespan (app scope); yield dep acquires a single connection from it
import asyncpg
from contextlib import asynccontextmanager
from collections.abc import AsyncGenerator
from fastapi import FastAPI, Request, Depends

DATABASE_URL = "postgresql://user:pass@host/db"

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Created ONCE at startup — ~500ms paid once, not per request
    app.state.pool = await asyncpg.create_pool(
        DATABASE_URL,
        min_size=5,
        max_size=20,
        command_timeout=30,
    )
    yield  # Application runs here
    await app.state.pool.close()  # Closed once on shutdown

app = FastAPI(lifespan=lifespan)

async def get_conn(request: Request) -> AsyncGenerator[asyncpg.Connection, None]:
    # Checkout takes ~0.1ms from an existing pool; no pool creation
    async with request.app.state.pool.acquire() as conn:
        yield conn  # Connection returned to pool after request (teardown is pool.release())

@app.get("/data")
async def read_data(conn: asyncpg.Connection = Depends(get_conn)):
    return await conn.fetchval("SELECT count(*) FROM records")
```

**Why it matters**: At 100 req/s the broken version creates and destroys 100 pools per second. Each pool opens 5–20 TCP connections to Postgres. That is 500–2000 TCP handshakes per second instead of 5–20 persistent connections. The fix reduces Postgres connection load by 99%.

### 6.4 Request-Scoped Caching

```python
call_count = 0

async def get_db() -> AsyncGenerator[AsyncSession, None]:
    global call_count
    call_count += 1
    print(f"get_db called: {call_count}")  # Prints ONCE per request even with 3 deps below
    async with async_session_factory() as session:
        yield session

async def get_user(session: AsyncSession = Depends(get_db)) -> User:
    return await session.get(User, 1)

async def get_tenant(session: AsyncSession = Depends(get_db)) -> Tenant:
    return await session.get(Tenant, 1)

async def get_permissions(session: AsyncSession = Depends(get_db)) -> list[str]:
    result = await session.execute(select(Permission))
    return [p.name for p in result.scalars()]

@router.get("/profile")
async def profile(
    user: User = Depends(get_user),
    tenant: Tenant = Depends(get_tenant),
    permissions: list[str] = Depends(get_permissions),
):
    # get_db() was called exactly ONCE; all three deps share the same session instance
    return {"user": user.name, "tenant": tenant.name, "permissions": permissions}
```

To force separate instances (e.g., two separate transactions):

```python
@router.post("/transfer")
async def transfer(
    debit_session: AsyncSession = Depends(get_db),                       # instance 1
    credit_session: AsyncSession = Depends(get_db, use_cache=False),     # instance 2
):
    # Two independent sessions; can commit independently
    ...
```

### 6.5 Class-Based Yield Dependency

```python
from typing import Any
from fastapi import Depends

class TenantDBSession:
    """Yield dependency that opens a session against the correct tenant shard."""

    def __init__(self, tenant_id: str) -> None:
        self.tenant_id = tenant_id

    async def __call__(self) -> AsyncGenerator[AsyncSession, None]:
        shard_url = shard_router.get_url(self.tenant_id)
        engine = create_async_engine(shard_url)
        async with AsyncSession(engine) as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise
            finally:
                await engine.dispose()

# Usage: instance is callable, FastAPI detects __call__ as async generator
tenant_session = TenantDBSession(tenant_id="acme")

@router.get("/tenant-data")
async def get_tenant_data(session: AsyncSession = Depends(tenant_session)):
    ...
```

For configurable class-based deps injected via `Depends`:

```python
class PaginationParams:
    def __init__(self, max_limit: int = 100) -> None:
        self.max_limit = max_limit

    def __call__(self, page: int = 1, limit: int = 20) -> dict[str, int]:
        # Plain dep (no yield) — no resource to clean up
        limit = min(limit, self.max_limit)
        return {"skip": (page - 1) * limit, "limit": limit}

strict_pagination = PaginationParams(max_limit=50)
loose_pagination = PaginationParams(max_limit=500)

@router.get("/items")
async def list_items(
    pagination: dict[str, int] = Depends(strict_pagination),
):
    ...
```

### 6.6 Exception Handling Patterns

```python
from sqlalchemy.exc import IntegrityError

async def get_db_with_error_translation() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_factory() as session:
        try:
            yield session
            await session.commit()
        except IntegrityError as exc:
            await session.rollback()
            # Translate DB-specific error to HTTP-meaningful error
            # Raising HTTPException here propagates correctly
            raise HTTPException(
                status_code=409,
                detail=f"Constraint violation: {exc.orig}",
            ) from exc
        except Exception:
            await session.rollback()
            raise  # Re-raise unknown exceptions unchanged
```

Inspecting the exception without re-raising (suppression — use carefully):

```python
async def auditing_dep() -> AsyncGenerator[None, None]:
    start = time.monotonic()
    error: Exception | None = None
    try:
        yield
    except Exception as exc:
        error = exc
        raise  # Must re-raise; swallowing converts 500 → 200
    finally:
        elapsed = time.monotonic() - start
        await audit_log.record(elapsed=elapsed, error=str(error) if error else None)
```

### 6.7 Redis Client Yield Dependency

```python
import redis.asyncio as aioredis
from fastapi import Depends, Request

async def get_redis(request: Request) -> AsyncGenerator[aioredis.Redis, None]:
    # Pool is app-scoped (created in lifespan); this dep checks out a connection
    client: aioredis.Redis = request.app.state.redis
    try:
        yield client
        # No close needed — aioredis connection pools manage their own connections
    except aioredis.RedisError as exc:
        # Log but don't suppress; caller decides how to handle
        logger.error("Redis error during request", exc_info=exc)
        raise

# In lifespan:
@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.redis = aioredis.from_url(
        "redis://localhost:6379",
        encoding="utf-8",
        decode_responses=True,
        max_connections=20,
    )
    yield
    await app.state.redis.close()
```

### 6.8 HTTP Client Yield Dependency

```python
import httpx

async def get_http_client() -> AsyncGenerator[httpx.AsyncClient, None]:
    # BROKEN approach would create a new client per request — recreates connection pool each time
    # CORRECT: if reuse is needed, use app.state; for short-lived scoped clients, yield is fine
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(connect=2.0, read=10.0, write=5.0, pool=2.0),
        limits=httpx.Limits(max_connections=10, max_keepalive_connections=5),
    ) as client:
        yield client
        # AsyncClient.__aexit__ calls aclose() automatically
```

For a truly shared HTTP client (connection reuse across requests):

```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.http = httpx.AsyncClient(
        base_url="https://api.example.com",
        timeout=10.0,
    )
    yield
    await app.state.http.aclose()

async def get_http(request: Request) -> httpx.AsyncClient:
    # Plain dep, no yield needed — no cleanup, just pass the shared client
    return request.app.state.http
```

---

## 7. Real-World Examples

### SQLAlchemy Async Session (Standard Pattern)

Every FastAPI + SQLAlchemy project uses a variant of this pattern. The session factory is created once (via `lifespan` or module-level), and `get_db` is a yield dep that wraps one `AsyncSession`:

```python
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

engine = create_async_engine("postgresql+asyncpg://user:pass@host/db", pool_size=20)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)

async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with AsyncSessionLocal() as session:
        yield session
        # async_sessionmaker's context manager commits on clean exit, rolls back on exception
```

FastAPI's own documentation uses this exact structure. `expire_on_commit=False` prevents lazy-loading errors after `commit()` when attributes are accessed in response serialization.

### Multi-Tenant DB Router

SaaS platforms with per-tenant databases use yield deps to route to the correct shard:

```python
async def get_tenant_session(
    tenant_id: str = Depends(extract_tenant_id),  # plain dep from JWT/header
    pool_registry: PoolRegistry = Depends(get_pool_registry),
) -> AsyncGenerator[AsyncSession, None]:
    pool = await pool_registry.get_or_create(tenant_id)
    async with AsyncSession(pool) as session:
        yield session
```

### Background Task + Yield Dep Interaction

Yield dependencies are scoped to the request, not to background tasks:

```python
@router.post("/send-email")
async def send_email(
    payload: EmailPayload,
    session: AsyncSession = Depends(get_db),
    background_tasks: BackgroundTasks,
):
    record = EmailRecord(**payload.model_dump())
    session.add(record)
    # session.commit() happens in teardown — BEFORE background task runs
    # Background task must open its own session; it cannot capture `session` from the closure
    background_tasks.add_task(deliver_email, record.id)  # Pass ID, not session object
    return {"status": "queued"}
```

---

## 8. Tradeoffs

### Yield Dep vs lifespan for Resource Management

| Dimension | yield dep (request scope) | lifespan (app scope) |
|-----------|--------------------------|---------------------|
| Creation cost paid | Every request | Once at startup |
| Isolation | Per-request (independent) | Shared across all requests |
| Cleanup | After each request | On app shutdown |
| Appropriate for | DB sessions, per-request state | Connection pools, global clients |
| Thread/async safety needed | No (single request path) | Yes (concurrent requests share) |
| Testability | `dependency_overrides` | Replace in test fixtures |

### Sync vs Async Yield Dependencies

| Dimension | `def get_db(): yield` | `async def get_db(): yield` |
|-----------|----------------------|----------------------------|
| Blocking I/O in setup/teardown | Blocks event loop thread | Non-blocking |
| CPU-bound setup | Fine | Fine (but yields control) |
| Use with async libraries | Cannot call `await` | Natural |
| Overhead | ~0.03ms | ~0.05ms |
| Use case | Sync ORMs (psycopg2/SQLite) | asyncpg, aioredis, httpx |

### use_cache=True vs use_cache=False

| Scenario | use_cache | Result |
|----------|-----------|--------|
| Single DB session per request | `True` (default) | One session, consistent transaction |
| Two independent transactions | `False` | Two separate sessions, separate commits |
| Read-write separation | `False` | Route to primary / replica independently |
| Idempotent lookup (e.g., config) | `True` | Function called once, result reused |

---

## 9. When to Use / When NOT to Use

### Use yield dependencies when:

- A resource requires both setup and teardown (database session, file handle, lock)
- The resource is request-scoped: different requests must get different instances
- You want teardown to run regardless of whether the handler succeeded or failed
- You want exception compensation (rollback on error) co-located with the resource
- You need to inject the resource into multiple route handlers without duplicating cleanup logic

### Do NOT use yield dependencies when:

- The resource is expensive to create and safe to share — use `lifespan` instead
- Teardown is irrelevant — plain `Depends` with a regular function is simpler
- The resource's lifetime needs to span multiple requests (e.g., a stateful WebSocket session manager) — use app state
- You need to run cleanup in a background task after the response — the background task runs after teardown; it cannot use the same resource instance
- You are creating framework-global singletons — module-level variables or `lifespan` are clearer

---

## 10. Common Pitfalls

### Pitfall 1: Teardown does not run for background tasks

**Symptom**: Background task accesses a closed session; `DetachedInstanceError` or `ResourceClosedError`.

**Cause**: The route handler's `yield` dependency tears down after the response is sent, which is before background tasks execute. The teardown closes the session; the background task's closure holds a reference to the now-closed session.

**Fix**: Pass only serializable IDs to background tasks, never live ORM objects or session references. The background task opens its own session via `async with AsyncSessionLocal() as session:`.

```python
# BROKEN: background task captures closed session from outer scope
@router.post("/process")
async def process(session: AsyncSession = Depends(get_db), tasks: BackgroundTasks):
    record = await session.get(Record, 1)
    tasks.add_task(do_work, record)  # BROKEN: record is detached after session closes

# FIX: pass the ID; background task opens its own session
@router.post("/process")
async def process(session: AsyncSession = Depends(get_db), tasks: BackgroundTasks):
    record = await session.get(Record, 1)
    tasks.add_task(do_work, record.id)  # FIX: ID is just an int; session-independent

async def do_work(record_id: int) -> None:
    async with AsyncSessionLocal() as session:  # Own session, own lifecycle
        record = await session.get(Record, record_id)
        ...
```

### Pitfall 2: Yielding inside a try without finally

**Symptom**: Resource leaks on exceptions; session never closed when handler raises.

**Cause**: Code after `yield` only executes on clean generator advancement. If an exception is thrown into the generator and there is no `try/finally`, the generator is garbage-collected without running teardown.

```python
# BROKEN: no finally; session.close() skipped on exception
async def get_db():
    session = AsyncSession(engine)
    yield session
    await session.close()  # BROKEN: never reached if handler raises

# FIX: finally guarantees close regardless
async def get_db():
    session = AsyncSession(engine)
    try:
        yield session
    finally:
        await session.close()  # FIX: always runs
```

FastAPI's `asynccontextmanager`-based wrapping does call `throw()` into the generator, so the `finally` block always fires. Without `finally`, the cleanup line is unreachable on the exception path.

### Pitfall 3: App-scoped resource in request-scoped dependency

See Section 6.3 for the complete BROKEN → FIX example. Creating a connection pool inside `get_db()` creates and destroys a pool on every request. At 100 req/s this is 100 pool creations per second, each costing ~500ms of I/O and opening 5–20 TCP sockets. Under load the app runs out of file descriptors (default 1024 on Linux) within seconds.

### Pitfall 4: Swallowing exceptions in teardown

**Symptom**: Route handler raises `ValueError`; client receives 200 OK with empty body.

**Cause**: A bare `except:` or `except Exception: pass` after `yield` swallows the exception. FastAPI sees no exception and generates a 200 response.

```python
# BROKEN: swallows the exception
async def get_db():
    async with async_session_factory() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            # BROKEN: missing `raise` — exception is silently swallowed

# FIX: always re-raise after compensation
async def get_db():
    async with async_session_factory() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise  # FIX: propagate the exception after cleanup
```

### Pitfall 5: Declaring dependency_overrides with wrong type

**Symptom**: Test override does not clean up; resource leak in tests; teardown assertions not triggered.

**Cause**: The original dependency is a generator function but the override is a plain function. FastAPI treats them differently — only generator overrides get teardown.

```python
# BROKEN: override is a plain function; teardown never runs
def override_db():
    return FakeSession()  # BROKEN: no teardown; FakeSession.close() never called

app.dependency_overrides[get_db] = override_db

# FIX: override must also be a generator if teardown matters
def override_db():
    session = FakeSession()
    try:
        yield session  # FIX: generator — teardown runs
    finally:
        session.close()

app.dependency_overrides[get_db] = override_db
```

### Pitfall 6: Blocking call in async yield dependency setup

**Symptom**: Event loop blocked for 50–500ms on every request; P99 latency spikes.

**Cause**: A synchronous blocking call (e.g., `psycopg2.connect()`, `open()`, `time.sleep()`) inside an `async def` generator setup blocks the entire event loop.

**Fix**: Use async equivalents (`asyncpg`, `aiofiles`) or offload to `asyncio.get_event_loop().run_in_executor()` for truly blocking operations. Better: move the blocking creation to `lifespan` and do it once.

---

## 11. Technologies & Tools

| Tool / Library | Role | yield dep integration |
|----------------|------|----------------------|
| SQLAlchemy 2.0 `async_sessionmaker` | Async ORM session factory | Wrap `AsyncSession` in yield dep; `async_sessionmaker` provides context manager |
| asyncpg | PostgreSQL async driver | Pool in lifespan; `pool.acquire()` context in yield dep |
| aioredis / redis-py 4+ | Redis async client | Pool in lifespan; client reference yielded directly |
| httpx `AsyncClient` | Async HTTP client | Shared client in lifespan or short-lived client per request in yield dep |
| `contextlib.asynccontextmanager` | FastAPI's underlying mechanism | FastAPI uses this internally; you can also wrap existing CMs manually |
| pytest + `httpx.AsyncClient` | Test client for async FastAPI | Use `dependency_overrides` to replace yield deps in tests |
| anyio | Async test backend | `anyio.pytest_plugin` enables `@pytest.mark.anyio` for testing async generators |

---

## 12. Interview Questions with Answers

**Q: What is a `yield` dependency in FastAPI and how does it differ from a plain `Depends`?**
A `yield` dependency is a generator function used with `Depends`; code before `yield` runs during setup and code after `yield` runs during teardown after the response is sent. A plain `Depends` callable just returns a value with no teardown phase. Use `yield` whenever the resource requires cleanup — DB sessions, file handles, HTTP clients.

**Q: How does FastAPI guarantee teardown even when the route handler raises an exception?**
FastAPI wraps the generator in an `asynccontextmanager` equivalent and drives teardown in a `finally` block. If the handler raises, FastAPI calls `generator.throw(exc)` which resumes execution inside the generator's `except`/`finally` block. The teardown code always runs as long as it is inside `finally:`.

**Q: What happens if you raise an exception inside the teardown of a yield dependency?**
FastAPI propagates it. If teardown raises a different exception than the one thrown in, the new exception replaces the original. This means careless teardown can mask handler errors. Always log and re-raise or use `finally` to keep teardown non-raising.

**Q: Explain the caching behavior of yield dependencies. When is a dependency called more than once?**
FastAPI caches the result of each dependency callable keyed by the function object within a single request. If three route parameters all declare `Depends(get_db)`, `get_db()` is called once; all three receive the same session. To force a new instance, pass `use_cache=False` to the second `Depends` call.

**Q: What is the teardown order for nested yield dependencies?**
LIFO — innermost first, outermost last. FastAPI builds a DAG at startup. At teardown, it tears down leaves before roots. If A depends on B which depends on C, teardown order is C → B → A.

**Q: Why should connection pools live in `lifespan` rather than in a yield dependency?**
Yield dependencies are request-scoped. Placing a pool in a yield dep recreates it on every request — an asyncpg pool takes ~500ms to create and opens 5–20 TCP connections. Under load this exhausts OS file descriptors and adds hundreds of milliseconds to request latency. `lifespan` creates the pool once at startup and shares it across all requests.

**Q: How do you test a route that uses a yield dependency?**
Use `app.dependency_overrides` to replace the original callable with a test generator. The override must also be a generator function if teardown assertions matter. Clean up with `app.dependency_overrides.clear()` in a pytest fixture teardown.

```python
import pytest
from fastapi.testclient import TestClient

@pytest.fixture
def client(app: FastAPI, db_session: AsyncSession):
    def override_get_db():
        yield db_session  # Use the test session

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()
```

**Q: Can a yield dependency be synchronous? What are the implications?**
Yes. A sync generator function (`def get_db(): yield session`) works with FastAPI. FastAPI wraps it in a `contextmanager` instead of `asynccontextmanager`. The limitation is that blocking I/O in the sync generator (e.g., opening a psycopg2 connection) blocks the event loop. For async apps, prefer async generator functions.

**Q: What is `use_cache=False` and when would you use it?**
`use_cache=False` passed to `Depends` tells FastAPI to bypass the per-request cache and call the dependency function again, creating a fresh instance. Use it when a route genuinely needs two independent instances — e.g., a transfer endpoint that requires two separate DB sessions for debit and credit operations so each can commit or roll back independently.

**Q: How does exception propagation differ between a handler raise and a dependency raise?**
If the route handler raises, FastAPI calls `throw(exc)` on the generator — the exception enters the generator's `except` block and `finally` block. If the dependency itself raises before `yield` (during setup), the exception propagates immediately without yielding — no teardown phase runs because the generator never reached the `yield` point.

**Q: How does FastAPI detect whether a dependency function is a generator?**
At app startup, FastAPI inspects each dependency using `inspect.isgeneratorfunction()` and `inspect.isasyncgenfunction()`. For class-based dependencies, it checks the `__call__` method. This inspection happens once at import/startup; at request time FastAPI follows a pre-computed execution plan.

**Q: What are the performance characteristics of yield dependencies?**
Dependency resolution adds approximately 0.05 ms per dependency per request for async generator deps. Caching makes repeated references to the same dep essentially free (a dict lookup). The dominant cost is always the I/O inside the dep (e.g., acquiring a DB connection from a pool: ~0.1–1ms), not FastAPI's wrapping overhead.

**Q: Can you use a yield dependency to manage a distributed lock?**
Yes — and it is a clean pattern:

```python
import asyncio

async def acquire_lock(
    key: str,
    redis: aioredis.Redis = Depends(get_redis),
) -> AsyncGenerator[bool, None]:
    lock = redis.lock(key, timeout=30)
    acquired = await lock.acquire(blocking=True, blocking_timeout=5)
    try:
        yield acquired
    finally:
        if acquired:
            await lock.release()
```

Teardown is guaranteed even if the handler raises, which prevents lock leaks.

**Q: Why can't you share a yield dependency instance between requests?**
The instance is bound to the request's dependency resolution context. FastAPI creates a fresh dependency graph per request. Sharing instances across requests would introduce concurrency hazards — two concurrent requests modifying the same SQLAlchemy session would corrupt internal state. App-scoped shared state must be thread/async-safe and belongs in `lifespan` or module-level singletons.

**Q: What happens if a yield dependency yields more than once?**
FastAPI drives the generator with `next()` (or `send()`) once to get the yielded value. After handler teardown it calls `next()` again expecting `StopIteration`. If the generator yields a second value instead of stopping, FastAPI's wrapping code raises `RuntimeError: generator didn't stop after throw()`. Always `yield` exactly once.

---

## 13. Best Practices

**Always use `finally:` in yield dependencies.** The teardown region after a bare `yield` is only reached on clean advancement. An exception thrown into the generator by FastAPI skips post-`yield` code that is not inside `finally:`. Make cleanup unconditional.

**Put the `yield` inside a context manager when possible.** `async with async_session_factory() as session: yield session` delegates cleanup to the context manager. This is safer than manual `session.close()` in `finally:` because the context manager handles nested exceptions correctly.

**Separate resource creation from resource acquisition.** Create pools in `lifespan`. Acquire connections in yield deps. This two-level pattern gives you fast per-request checkout (~0.1ms) without per-request pool creation overhead (~500ms).

**Keep teardown fast.** Teardown runs synchronously in the request processing path (even though the response has been sent, the event loop is occupied). A 50ms `session.commit()` or expensive audit log write in teardown reduces the effective request throughput. Use background tasks for expensive post-response work.

**Name yield dep functions descriptively.** `get_db` is conventional and unambiguous. Avoid `dependency` or `session_dep` — the function name appears in error tracebacks and OpenAPI schema names.

**Override must mirror original in tests.** If the production dep is a generator, the test override must also be a generator. If the production dep is a plain function, a plain override suffices. Mismatching causes teardown skips that produce false-passing tests with resource leaks.

**Avoid circular yield dependencies.** FastAPI detects circular deps at startup and raises `ValueError: Circular dependency detected`. Design dependency graphs as DAGs. If two resources truly need each other, extract a third dependency that creates both.

**Use `contextlib.suppress` judiciously in finally blocks.** Suppressing all exceptions in teardown can hide serious errors. At minimum, log suppressed exceptions. A session rollback failure that is silently swallowed means dirty data; you want to know about it.

**Document scope explicitly.** Add a comment above each yield dep indicating its scope: `# Request-scoped: one session per HTTP request`. This prevents future engineers from accidentally moving request-scoped logic to app scope or vice versa.

---

## 14. Case Study

**Scenario**: A multi-tenant SaaS API gateway with 12 microservices needs a consistent DB session management strategy. The platform serves 800 req/s across 3 FastAPI workers. Each request may touch: one PostgreSQL tenant shard, one Redis cluster, and one downstream HTTP API.

**Requirements**:
- DB session must roll back on any unhandled exception
- Redis connection must always be returned to pool
- HTTP client must use keep-alive connections (shared across requests)
- All three resources must be cleanly closed on shutdown

**Architecture**:

```
lifespan (app scope)
  |-- app.state.pool         asyncpg.Pool  (min=5, max=20, created once)
  |-- app.state.redis        aioredis.Redis  (pool, max_connections=20)
  |-- app.state.http_client  httpx.AsyncClient  (persistent, keep-alive)

yield deps (request scope)
  |-- get_conn()    asyncpg.Connection  (acquired from pool, returned after request)
  |-- get_redis()   aioredis.Redis      (shared reference, no per-request cleanup needed)
  |-- get_http()    httpx.AsyncClient   (shared reference — plain dep, no yield)
```

**Implementation**:

```python
from contextlib import asynccontextmanager
from collections.abc import AsyncGenerator
import asyncpg
import httpx
import redis.asyncio as aioredis
from fastapi import FastAPI, Request, Depends
import logging

logger = logging.getLogger(__name__)

DATABASE_URL = "postgresql://svc:secret@pg-primary:5432/saasdb"
REDIS_URL = "redis://redis-cluster:6379"
DOWNSTREAM_BASE = "https://internal-api.example.com"

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting up — creating shared resources")
    app.state.pool = await asyncpg.create_pool(
        DATABASE_URL,
        min_size=5,
        max_size=20,
        command_timeout=30,
        server_settings={"application_name": "gateway"},
    )
    app.state.redis = aioredis.from_url(
        REDIS_URL,
        encoding="utf-8",
        decode_responses=True,
        max_connections=20,
    )
    app.state.http_client = httpx.AsyncClient(
        base_url=DOWNSTREAM_BASE,
        timeout=httpx.Timeout(connect=2.0, read=10.0),
        limits=httpx.Limits(max_connections=50, max_keepalive_connections=20),
    )
    yield
    logger.info("Shutting down — closing shared resources")
    await app.state.pool.close()
    await app.state.redis.close()
    await app.state.http_client.aclose()

app = FastAPI(lifespan=lifespan)

async def get_conn(request: Request) -> AsyncGenerator[asyncpg.Connection, None]:
    async with request.app.state.pool.acquire() as conn:
        async with conn.transaction():  # Auto-rollback on exception
            yield conn
        # transaction commits here on clean exit; rolls back on exception

async def get_redis(request: Request) -> aioredis.Redis:
    # Plain dep — no teardown needed; pool manages connection lifecycle
    return request.app.state.redis

async def get_http(request: Request) -> httpx.AsyncClient:
    # Plain dep — shared client; no teardown needed per request
    return request.app.state.http_client

# Route using all three resources
@app.get("/order/{order_id}")
async def get_order(
    order_id: int,
    conn: asyncpg.Connection = Depends(get_conn),
    redis: aioredis.Redis = Depends(get_redis),
    http: httpx.AsyncClient = Depends(get_http),
):
    # 1. Check cache
    cached = await redis.get(f"order:{order_id}")
    if cached:
        return {"source": "cache", "data": cached}

    # 2. Load from DB
    row = await conn.fetchrow("SELECT * FROM orders WHERE id = $1", order_id)
    if not row:
        raise HTTPException(status_code=404, detail="Order not found")

    # 3. Enrich from downstream service
    resp = await http.get(f"/enrichment/{order_id}")
    resp.raise_for_status()
    enriched = resp.json()

    # 4. Cache result
    await redis.setex(f"order:{order_id}", 300, str(dict(row)))

    return {"source": "db", "data": dict(row), "enrichment": enriched}
```

**Outcome**:
- Pool created once at startup: 5 persistent connections to Postgres
- Under 800 req/s: max 20 concurrent DB connections, average checkout ~0.3ms
- Redis pool: 20 connections shared across all workers
- HTTP client: 20 keep-alive connections to downstream; no TCP handshake overhead per request
- Teardown cost per request: ~0.1ms (connection return to pool) vs ~500ms (pool creation/destruction)
- On handler exception: asyncpg transaction auto-rolls back; connection returns to pool cleanly
- On shutdown: all three resources close gracefully with zero leaked connections

**Testing**:

```python
import pytest
from httpx import AsyncClient, ASGITransport

@pytest.fixture
def fake_conn():
    """In-memory asyncpg-compatible fake connection."""
    ...  # Use asyncpg mock or testcontainers

@pytest.fixture
async def test_client(fake_conn):
    def override_get_conn():
        yield fake_conn  # Generator: teardown runs

    app.dependency_overrides[get_conn] = override_get_conn
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()

@pytest.mark.anyio
async def test_get_order_not_found(test_client):
    resp = await test_client.get("/order/99999")
    assert resp.status_code == 404
```

This pattern is used by production FastAPI deployments at companies including Weights & Biases (ML platform API), Pydantic's own internal services, and numerous fintech platforms handling PCI-scope database transactions.
