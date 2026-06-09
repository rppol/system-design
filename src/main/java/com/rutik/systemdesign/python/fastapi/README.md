# FastAPI — Senior Engineer Module

FastAPI-specific content covering Phases 4–6 of the Python + FastAPI study section.
These modules assume familiarity with the [pure-Python fundamentals](../README.md).

---

## 1. Concept Overview

FastAPI is a modern Python web framework for building HTTP APIs and WebSocket services. It is built
on three foundations:

- **ASGI (Starlette)** — asynchronous server gateway interface; handles HTTP/1.1, HTTP/2,
  WebSockets, and long-polling on a single event loop instead of a thread per request.
- **Pydantic v2** — validation and serialisation engine whose core (`pydantic-core`) is written in
  Rust, making model validation 5–50x faster than Pydantic v1 for complex schemas.
- **Python native async/await** — coroutine-based concurrency; a single Uvicorn worker can handle
  50,000+ simple requests/second on modern hardware with uvloop + httptools.

FastAPI reads Python type annotations at application startup to:
1. Validate and coerce every incoming value (path params, query params, headers, request bodies).
2. Inject dependencies via `Depends()`.
3. Generate an OpenAPI 3.1 specification automatically — served as Swagger UI at `/docs` and
   ReDoc at `/redoc` with zero additional configuration.

**Position relative to alternatives:**

| Framework | Async | Type-safe validation | OpenAPI | ORM bundled |
|-----------|-------|---------------------|---------|-------------|
| FastAPI | Native (ASGI) | Yes — Pydantic v2 automatic | Automatic | No |
| Flask | Limited (WSGI threads) | Manual | Manual | No |
| Django REST Framework | Partial (ASGI from 3.1+) | Django serializers | Manual | Yes (Django ORM) |
| gRPC / protobuf | Yes | Yes — proto schema | No (Protobuf IDL) | No |

FastAPI targets the microservice and API-first domain. It is not a full-stack framework — there is
no ORM, no admin interface, and no template engine included.

---

## 2. Intuition

**One-line analogy:** FastAPI is to Python APIs what Spring Boot is to Java — convention-over-
configuration, production-ready defaults, built-in dependency injection, and a strong opinion on how
to structure an application.

**Mental model:** Every FastAPI route is a plain Python `async def` (or `def`) function with
type-annotated parameters. FastAPI reads those annotations once at startup to build three artefacts:
(1) a Pydantic validation pipeline for incoming data, (2) a dependency resolution graph via
`Depends()`, and (3) an OpenAPI schema entry. At request time it executes all three in the correct
order with no additional developer ceremony — no XML config, no decorator metadata strings.

**Why it matters:** FastAPI's throughput advantage over Flask and Django REST Framework is
architectural, not incidental. The three foundations — an async event loop, non-blocking I/O via
asyncpg/httpx, and Pydantic v2's Rust validation core — each contribute independently. A Pydantic v2
validation of a 10-field model takes approximately 2 microseconds; the same model in v1 took
approximately 50 microseconds. At 10,000 requests/second, that 48μs difference saves nearly half a
second of CPU per second of traffic.

**Key insight:** Understanding which of the three foundations is the bottleneck in a given service
explains almost every FastAPI performance and debugging problem. CPU-bound work is never helped by
async alone; I/O-bound work is never helped by multiprocessing alone; validation-heavy endpoints
benefit the most from upgrading to Pydantic v2.

---

## 3. Core Principles

**ASGI protocol**
The ASGI 3 specification defines a single interface: `async def app(scope, receive, send)`. Uvicorn
parses raw HTTP bytes and calls this triple for each connection. Starlette implements routing,
middleware, and WebSocket support on top of that interface. FastAPI wraps Starlette's `Router` to
add dependency injection and Pydantic validation.

**Type-annotation-driven**
Route parameters, request bodies, response models, and dependencies are all declared via standard
Python type hints. FastAPI introspects function signatures at import time using `inspect` and
`typing.get_type_hints()`. No runtime reflection per request.

**Dependency injection via `Depends()`**
FastAPI resolves a directed acyclic graph (DAG) of `Depends()` callables before invoking the route
handler. Dependencies are cached within request scope by default — two route parameters that both
declare `Depends(get_current_user)` resolve the user exactly once per request.

**Pydantic v2 validation architecture**
pydantic-core is a compiled Rust extension. Validation is done by a schema compiled once at class
definition time. The Python layer only prepares data and interprets results; no pure-Python
per-field loops run at request time.

**Lifespan context manager [FastAPI 0.93+]**
Startup and shutdown logic belongs in an `asynccontextmanager` passed to `FastAPI(lifespan=...)`.
This is the correct place to initialise connection pools, load ML models, and acquire external
resources. The deprecated `@app.on_event("startup")` / `@app.on_event("shutdown")` pattern was
removed from the recommended documentation in 2023.

**Automatic OpenAPI generation**
Every decorated route is introspected: path, method, path parameters, query parameters, request
body schema (from Pydantic models), and response schema all become OpenAPI 3.1 entries. The spec is
available at `/openapi.json` and rendered by Swagger UI at `/docs`.

---

## 4. Types / Architectures / Strategies

This section is the master module index for the FastAPI sub-section. Each module is a full
14-section README covering the topic listed.

### 4.1 Phase 4 — FastAPI Core (6 modules)

| Module | Key Concepts |
|--------|-------------|
| [fastapi_fundamentals_asgi](fastapi_fundamentals_asgi/) | ASGI 3 protocol, Starlette, Uvicorn, lifespan, auto OpenAPI |
| [pydantic_v2_deep_dive](pydantic_v2_deep_dive/) | pydantic-core Rust, @field_validator, v1 to v2 migration, BaseSettings |
| [routing_and_request_handling](routing_and_request_handling/) | Path operations, APIRouter, response models, BackgroundTasks |
| [dependency_injection_in_fastapi](dependency_injection_in_fastapi/) | Depends, yield deps, caching scopes, dependency_overrides |
| [middleware_and_lifecycle](middleware_and_lifecycle/) | Middleware stack, lifespan [0.93+], CORS/GZip, exception handlers |
| [configuration_and_settings_management](configuration_and_settings_management/) | pydantic-settings, @lru_cache singleton, SecretStr, 12-factor |

### 4.2 Phase 5 — Production Concerns (8 modules)

| Module | Key Concepts |
|--------|-------------|
| [async_database_sqlalchemy](async_database_sqlalchemy/) | SQLAlchemy 2.0 async, AsyncSession, N+1, Alembic |
| [authentication_and_security](authentication_and_security/) | OAuth2, JWT, bcrypt/argon2, refresh rotation, scopes |
| [error_handling_and_validation](error_handling_and_validation/) | HTTPException, RFC 7807, domain exceptions, 422 shaping |
| [testing_fastapi](testing_fastapi/) | TestClient, AsyncClient, dependency_overrides, rollback |
| [websockets_sse_and_streaming](websockets_sse_and_streaming/) | WS lifecycle, Redis pub/sub fan-out, SSE, backpressure |
| [background_jobs_and_task_queues](background_jobs_and_task_queues/) | BackgroundTasks vs Celery vs ARQ, idempotency, DLQ |
| [http_clients_and_external_apis](http_clients_and_external_apis/) | httpx pool, circuit breaker, retry+jitter |
| [message_queues_and_event_driven](message_queues_and_event_driven/) | aiokafka, aio-pika, outbox pattern, idempotent consumers |

### 4.3 Phase 6 — Deployment, Observability & Scale (5 modules)

| Module | Key Concepts |
|--------|-------------|
| [production_deployment_and_scaling](production_deployment_and_scaling/) | Gunicorn+Uvicorn workers, K8s HPA, graceful shutdown, uvloop |
| [observability_and_monitoring](observability_and_monitoring/) | OpenTelemetry, structlog, Prometheus metrics, health probes |
| [caching_and_performance](caching_and_performance/) | Redis cache-aside, stampede prevention, orjson, lru_cache trap |
| [api_design_and_versioning](api_design_and_versioning/) | Cursor pagination, idempotency keys, slowapi, OpenAPI |
| [security_hardening_and_owasp](security_hardening_and_owasp/) | OWASP API Top 10, BOLA, SSRF, pip-audit, CORS |

### 4.4 Case Studies (6)

| Case Study | Core Challenge |
|------------|---------------|
| [Design a Rate-Limited API](case_studies/design_rate_limited_api_fastapi.md) | Token-bucket Redis Lua, DI-injected rate limiter |
| [Design a Multi-Tenant SaaS API](case_studies/design_multi_tenant_saas_api.md) | PostgreSQL RLS, tenant JWT, async SQLAlchemy |
| [Design a Real-Time Chat System](case_studies/design_realtime_chat_fastapi.md) | WebSockets, Redis pub/sub, horizontal scaling |
| [Design an Async Task Queue](case_studies/design_async_task_queue.md) | ARQ vs Celery, idempotency, DLQ |
| [Design an Async Web Scraper](case_studies/design_async_web_scraper.md) | asyncio Semaphore, producer/consumer, politeness |
| [Design an ML Inference API](case_studies/design_ml_inference_api_fastapi.md) | lifespan model loading, micro-batching, SSE streaming |

---

## 5. Architecture Diagrams

### 5.1 Full Request Pipeline

```
HTTP Request (bytes)
        |
        v
+---------------------------------------+
|   ASGI Server: Uvicorn / Hypercorn    |
|   httptools parses HTTP bytes          |
|   uvloop drives the event loop        |
+---------------------------------------+
        |  ASGI scope dict created:
        |  {type: "http", method: "POST",
        |   path: "/orders", headers: [...]}
        v
+---------------------------------------+
|   Starlette Application               |
|   - Routing                           |
|   - Middleware chain management       |
|   - WebSocket lifecycle               |
|   - Exception handling                |
+---------------------------------------+
        |
        v
+---------------------------------------+
|   FastAPI Middleware Stack            |
|   (each middleware wraps the next)    |
|                                       |
|   CORSMiddleware                      |
|       -> GZipMiddleware               |
|           -> Custom middleware A      |
|               -> Custom middleware B  |
+---------------------------------------+
        |
        v
+---------------------------------------+
|   Route Matching                      |
|   Path parameters extracted           |
|   Matched route: POST /orders         |
+---------------------------------------+
        |
        v
+-------------------------------------------+
|   Dependency Resolution Tree              |
|                                           |
|   create_order(                           |
|     db: AsyncSession = Depends(get_db),   |
|     user: User = Depends(get_current_user)|
|   )                                       |
|                                           |
|   get_db() -----> yield AsyncSession      |
|   get_current_user()                      |
|       -> Depends(oauth2_scheme)           |
|           -> Depends(get_settings)        |
|                                           |
|   Results cached within request scope     |
+-------------------------------------------+
        |
        v
+---------------------------------------+
|   Pydantic v2 Validation              |
|   (pydantic-core Rust extension)      |
|                                       |
|   - Path params: order_id: int        |
|   - Query params: include_tax: bool   |
|   - Request body: OrderCreate model   |
|   - Headers: Authorization: str      |
|                                       |
|   Validation failure -> 422 response  |
+---------------------------------------+
        |
        v
+---------------------------------------+
|   async def create_order(...)         |
|   Your route handler coroutine        |
|   Awaited by the event loop           |
+---------------------------------------+
        |
        v
+---------------------------------------+
|   Response Serialization              |
|   response_model applied              |
|   Pydantic serializes return value    |
|   -> dict -> JSON bytes               |
+---------------------------------------+
        |
        v
HTTP Response (bytes) -> client
```

### 5.2 Dependency Injection Resolution Tree

```
POST /orders
    |
    Depends(get_current_user)
    |   |
    |   Depends(oauth2_scheme)          <- cached: token string
    |   Depends(get_settings)           <- cached: Settings singleton
    |   [DB query for user]             <- result cached in request scope
    |
    Depends(get_db)
    |   Depends(get_async_engine)       <- cached: engine (app-level)
    |   [yield AsyncSession]            <- session created per request
    |   [cleanup runs after response]
    |
    Depends(get_rate_limiter)
    |   Depends(get_redis_client)       <- cached: same client as elsewhere
    |
    route handler invoked with resolved values
    |
    yield deps run cleanup (finally block)
        -> session.close()
        -> redis.close() [if yield dep]
```

### 5.3 Gunicorn + Uvicorn Worker Layout

```
+-----------------------------------------------------+
|  Gunicorn (process manager)                         |
|  main process: monitors workers, respawns failures  |
|                                                     |
|  Worker formula: (2 x CPU_cores) + 1               |
|  Example: 4-core machine -> 9 Uvicorn workers       |
|                                                     |
|  +----------------+  +----------------+             |
|  | UvicornWorker  |  | UvicornWorker  |  ...        |
|  | pid: 1234      |  | pid: 1235      |             |
|  | event loop     |  | event loop     |             |
|  | uvloop         |  | uvloop         |             |
|  | 1000s req/s    |  | 1000s req/s    |             |
|  +----------------+  +----------------+             |
+-----------------------------------------------------+
         |
         v
+-----------------------------------------------------+
|  Load balancer / Kubernetes Service                 |
|  (nginx, Envoy, or cloud LB)                        |
+-----------------------------------------------------+
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Request Lifecycle — Step by Step

1. Uvicorn receives raw bytes on the socket. httptools parses the HTTP/1.1 or HTTP/2 framing.
2. An ASGI `scope` dict is constructed:
   ```python
   scope = {
       "type": "http",
       "method": "POST",
       "path": "/orders",
       "query_string": b"include_tax=true",
       "headers": [(b"content-type", b"application/json"), ...],
   }
   ```
3. Starlette's middleware chain executes. Each middleware calls `await call_next(request)` to pass
   control to the next layer. Middleware can inspect and mutate the request before passing it, or
   the response on the way back.
4. Starlette's router matches the path and method. Path parameters are extracted.
5. FastAPI resolves the dependency DAG. It topologically sorts `Depends()` callables, executes them
   in dependency order, and caches results keyed by the callable object within the request scope.
6. Pydantic v2 validates incoming data. Path params, query params, headers, cookies, and request
   body all pass through compiled Rust validators. A 422 `RequestValidationError` is raised
   immediately on any failure.
7. The route handler coroutine is awaited. The event loop is free to process other requests during
   any `await` in the handler.
8. The return value passes through `response_model` serialisation: Pydantic converts the Python
   object to a dict (applying field exclusions), then `orjson` or the standard `json` module
   converts it to bytes.
9. Uvicorn sends the response bytes and calls cleanup on any `yield`-based dependencies (the
   `finally` block after `yield`).

### 6.2 Pydantic v2 Performance Numbers

| Operation | Pydantic v1 (pure Python) | Pydantic v2 (Rust core) | Speedup |
|-----------|--------------------------|------------------------|---------|
| Validate 10-field model | ~50 μs | ~2 μs | 25x |
| Validate nested model (3 levels) | ~200 μs | ~8 μs | 25x |
| Serialise model to dict | ~30 μs | ~1 μs | 30x |
| Parse and validate list of 100 items | ~5 ms | ~0.2 ms | 25x |

These gains compound: at 10,000 requests/second with a 10-field body, v2 saves approximately
480ms of CPU per second of traffic compared to v1.

### 6.3 BROKEN to FIX — Blocking Call in Async Handler

The most common FastAPI production mistake is calling synchronous I/O inside an `async def` route.
This does not raise an error — it silently blocks the entire event loop, stalling all concurrent
requests.

```python
# BROKEN: sync psycopg2 call blocks the event loop thread
# All other requests queue behind this DB call
# On a 4-core machine with 9 workers, one slow sync DB call
# can reduce throughput from ~50k rps to ~500 rps on that worker.

from sqlalchemy.orm import Session
from app.models import User

@app.get("/users/{user_id}")
async def get_user_broken(
    user_id: int,
    db: Session = Depends(get_sync_db),  # psycopg2 under the hood
) -> dict:
    # This blocks the event loop for the full DB round-trip (~1-5ms)
    user = db.query(User).filter(User.id == user_id).first()
    return {"id": user.id, "email": user.email}
```

```python
# FIX: async SQLAlchemy 2.0 with asyncpg releases the event loop
# during the I/O wait — other requests continue executing

from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import User

@app.get("/users/{user_id}")
async def get_user_fixed(
    user_id: int,
    db: AsyncSession = Depends(get_async_db),  # asyncpg under the hood
) -> UserResponse:
    # Event loop is freed during network I/O to PostgreSQL
    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    return UserResponse.model_validate(user)
```

Additional blocking traps to avoid:

```python
# BROKEN: requests library is synchronous
import requests

@app.get("/proxy")
async def proxy_broken() -> dict:
    resp = requests.get("https://api.example.com/data")  # blocks event loop
    return resp.json()

# FIX: httpx AsyncClient
import httpx

@app.get("/proxy")
async def proxy_fixed() -> dict:
    async with httpx.AsyncClient() as client:
        resp = await client.get("https://api.example.com/data")
    return resp.json()

# BROKEN: time.sleep in async context
@app.get("/delay")
async def delay_broken() -> dict:
    time.sleep(1)  # blocks event loop for 1 second
    return {"ok": True}

# FIX: asyncio.sleep yields control
@app.get("/delay")
async def delay_fixed() -> dict:
    await asyncio.sleep(1)  # event loop serves other requests during this second
    return {"ok": True}
```

### 6.4 Lifespan Pattern — Deprecated on_event vs Current Standard

```python
# BROKEN (deprecated since FastAPI 0.93, removed from docs 2023):
@app.on_event("startup")
async def startup():
    app.state.db_engine = create_async_engine(settings.database_url)

@app.on_event("shutdown")
async def shutdown():
    await app.state.db_engine.dispose()
```

```python
# FIX: lifespan context manager — startup before yield, shutdown after
from contextlib import asynccontextmanager
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from app.config import get_settings

@asynccontextmanager
async def lifespan(app: FastAPI):
    # --- startup ---
    settings = get_settings()
    engine = create_async_engine(
        settings.database_url,
        pool_size=10,       # same tuning as HikariCP default
        max_overflow=20,    # allows burst to 30 connections
        pool_timeout=30,    # wait up to 30s for a connection
        echo=False,
    )
    app.state.engine = engine
    app.state.session_factory = async_sessionmaker(engine, expire_on_commit=False)
    yield
    # --- shutdown ---
    await engine.dispose()  # closes all connections gracefully

app = FastAPI(lifespan=lifespan)
```

### 6.5 Dependency Caching Mechanics

FastAPI uses the callable object itself as the cache key within a request. Two injected parameters
that both declare `Depends(get_current_user)` will resolve the coroutine exactly once; the result
is reused for the second parameter without calling the function again.

```python
from fastapi import Depends, FastAPI
from app.auth import get_current_user
from app.models import User

async def get_user_profile(user: User = Depends(get_current_user)) -> dict:
    return {"id": user.id}

async def get_user_permissions(user: User = Depends(get_current_user)) -> list[str]:
    return user.permissions  # same User object — get_current_user called once

@app.get("/dashboard")
async def dashboard(
    profile: dict = Depends(get_user_profile),
    permissions: list[str] = Depends(get_user_permissions),
) -> dict:
    # get_current_user executed exactly once despite two Depends paths
    return {**profile, "permissions": permissions}
```

To disable caching and force a fresh call per injection point, pass `use_cache=False`:

```python
Depends(get_current_user, use_cache=False)
```

---

## 7. Real-World Examples

**Uber**: Uber engineering blog posts and PyCon talks describe FastAPI used for internal Python
microservices in their marketplace platform. The async event loop model maps naturally to services
that aggregate calls to multiple downstream APIs — a common pattern in Uber's service mesh.

**Netflix**: FastAPI cited in PyConUS presentations for metadata and recommendation API services.
Netflix's Python teams favour FastAPI's type annotation approach because it integrates with their
mypy-enforced type checking pipelines — the same annotations that drive Pydantic validation also
drive static analysis.

**Microsoft Azure**: Microsoft's official Azure documentation recommends FastAPI for Python APIs
deployed to Azure App Service and Azure Container Apps. Azure's ML serving examples (Azure Machine
Learning online endpoints) use FastAPI as the inference server framework.

**Anthropic / OpenAI**: LLM inference serving endpoints commonly use FastAPI-style ASGI applications
for their async streaming responses. Server-Sent Events (SSE) with `StreamingResponse` is the
standard pattern for token-by-token streaming from language model APIs.

**Robinhood**: Async Python APIs for real-time market data feeds use ASGI frameworks. FastAPI's
native WebSocket support makes it suitable for pushing live bid/ask updates to clients without
long-polling overhead.

**Community adoption numbers (2024)**: FastAPI is the third most-used Python web framework
according to the Python Developer Survey 2023 (behind Django and Flask), but it is the
fastest-growing, with 57% year-over-year growth in usage among survey respondents.

---

## 8. Tradeoffs

### 8.1 FastAPI vs Alternatives

| Concern | FastAPI | Flask | Django REST Framework | gRPC |
|---------|---------|-------|----------------------|------|
| Async support | Native (ASGI) | Limited (WSGI threads) | Partial (ASGI from 3.1+) | Native |
| Validation | Pydantic v2, automatic | Manual | Django serializers | Protobuf schema |
| OpenAPI generation | Automatic | Manual (flask-swagger) | Manual (drf-spectacular) | No (Protobuf IDL) |
| ORM bundled | No | No | Yes (Django ORM) | No |
| WebSocket support | Native (ASGI) | Via flask-socketio | Limited | Streaming RPCs |
| Learning curve | Medium | Low | High | High |
| Admin interface | None | None | django-admin (excellent) | None |
| Ecosystem maturity | 2018 | 2010 | 2011 | 2015 |
| Throughput (simple req, 1 worker) | ~50k rps | ~8k rps | ~5k rps | ~100k rps |
| Protobuf / binary encoding | Optional | No | No | Yes |

### 8.2 Uvicorn Workers vs Threads

| Model | Concurrency unit | Shared memory | CPU parallelism | Best for |
|-------|-----------------|---------------|-----------------|----------|
| Uvicorn (async) | Coroutine | Yes (same process) | No (single thread) | I/O-bound |
| Gunicorn (sync) | OS thread or process | Depends on config | Yes | CPU-bound |
| Gunicorn + UvicornWorker | Process (async inside) | No (separate processes) | Yes | I/O-bound + multi-core |
| Celery worker | Process | No | Yes | Background tasks |

### 8.3 Pydantic v2 Migration Tradeoffs

| Aspect | Pydantic v1 | Pydantic v2 |
|--------|-------------|-------------|
| Validator syntax | `@validator` | `@field_validator` |
| Model config | inner `class Config` | `model_config = ConfigDict(...)` |
| `.dict()` method | Yes | Deprecated — use `.model_dump()` |
| `.json()` method | Yes | Deprecated — use `.model_dump_json()` |
| Strict mode | Limited | Full strict mode via `ConfigDict(strict=True)` |
| Performance | Baseline | 5–50x faster |
| `Optional[X]` | Common | `X \| None` preferred (PEP 604) |

---

## 9. When to Use / When NOT to Use

### Use FastAPI when:

- Building I/O-bound APIs where async concurrency provides measurable throughput gains — database
  calls, external HTTP calls, Redis reads.
- Microservices requiring strong type safety and automatically maintained API documentation.
- ML inference serving — lifespan loads the model once; async streaming returns tokens as they
  generate.
- WebSocket endpoints alongside REST on the same process.
- Team members already use Python with type hints and `mypy`.
- The service needs to expose an OpenAPI spec consumed by other teams or clients.

### Do NOT use FastAPI when:

- Building server-side rendered HTML applications — use Django with templates.
- Running a monolithic application that needs a built-in admin interface — use Django.
- The team is unfamiliar with Python's async model. Debugging event loop blocking, improper
  `async def` usage, and yield dependency cleanup requires async expertise that takes time to build.
- The primary workload is CPU-bound (image processing, numerical computation). Async does not help
  CPU work. Use multiprocessing or offload to a task queue instead.
- You need gRPC and binary protocol efficiency — FastAPI's protobuf support is an add-on, not
  native. Use a gRPC framework directly.
- You need a battle-tested monolith framework with 15+ years of third-party packages — Flask or
  Django have broader ecosystems.

---

## 10. Common Pitfalls

**Pitfall 1: Blocking calls in async handlers**
Using `requests`, sync `psycopg2`, `time.sleep()`, or any blocking library inside `async def`
freezes the entire event loop. All other concurrent requests stall until the blocking call returns.
This is a silent correctness error — no exception, just degraded throughput.
Fix: use `httpx.AsyncClient`, `asyncpg`, `asyncio.sleep()`. For unavoidable sync libraries, offload
via `asyncio.get_event_loop().run_in_executor(None, sync_fn)` to a thread pool.

**Pitfall 2: Reusing one Pydantic model for input and output**
A `User` model containing `password_hash` used as a response model leaks the hash in the JSON
response. This is a data exposure vulnerability.
Fix: define separate models: `UserCreate` (password, email as input), `UserInDB` (password_hash
for ORM), `UserResponse` (id, email, created_at for output). Never let the ORM model be the
response model.

**Pitfall 3: Missing lifespan for connection pool**
Creating a new database engine or HTTP client session per request is 100–500x slower than reusing
a pool. A new `create_async_engine()` call allocates connection objects on every request; the pool
connection handshake adds ~5–50ms latency.
Fix: initialise the engine in `lifespan`, store it on `app.state`, inject it via `Depends()`.

**Pitfall 4: `dependency_overrides` leaking between tests**
If one test sets `app.dependency_overrides[get_db] = get_test_db` and does not reset it, the next
test in the session inherits the override. This causes false positives — tests pass against the
test database even when they should exercise the real dependency logic.
Fix: use a pytest fixture with `yield` and reset in teardown:
```python
@pytest.fixture(autouse=True)
def reset_overrides():
    yield
    app.dependency_overrides = {}
```

**Pitfall 5: `response_model` field exclusions surprising in Pydantic v2**
Pydantic v2's `model_config` defaults differ from v1. Fields set to `None` are included by default
in v2 but not if `exclude_none=True` is set. Unset fields behave differently with
`exclude_unset=True`. Response shapes that worked in v1 can silently change after migration.
Fix: add integration tests that assert exact JSON response shapes. Test with `response_model_exclude_unset`
explicitly where needed.

**Pitfall 6: Using `def` (sync) routes thinking they are simpler for DB calls**
FastAPI runs `def` routes in a threadpool executor automatically. This prevents event loop blocking
but still blocks a thread for the duration of the call. Under high concurrency the threadpool can
exhaust (default 40 threads), and sync DB calls queue. Developers sometimes choose `def` routes
thinking "it's simpler" without realising the thread exhaustion risk.
Fix: use `async def` with async DB drivers for all I/O-bound routes. Reserve `def` for genuinely
CPU-bound routes that benefit from thread-level parallelism.

**Pitfall 7: Forgetting `await` on an async function — returns a coroutine object**
Python does not raise an error if you call an async function without `await` inside another async
function — it returns a coroutine object. Returning that object from a route produces a
serialisation error, not the data you intended.
Fix: enable `asyncio.set_event_loop_policy(asyncio.DefaultEventLoopPolicy())` and use `mypy` with
`--strict`. The type checker catches unawaited coroutine patterns at static analysis time.

**Pitfall 8: `lru_cache` on a function that creates a new database connection**
Using `@lru_cache` on `get_settings()` is correct — settings are immutable. Using `@lru_cache` on
`get_db()` or any resource-creating function caches the first resource forever, leaking connections
and preventing cleanup.
Fix: `@lru_cache` is for pure, stateless, deterministic functions. Connection factories belong in
lifespan.

---

## 11. Technologies & Tools

| Tool | Role | Key Config |
|------|------|-----------|
| **Uvicorn** | ASGI server | `--workers 1` for dev; Gunicorn+UvicornWorker for prod |
| **Gunicorn** | Process manager | `--worker-class uvicorn.workers.UvicornWorker` |
| **uvloop** | Fast event loop (libuv) | `pip install uvloop`; Uvicorn uses it automatically |
| **httptools** | Fast HTTP parser | Uvicorn uses it automatically when installed |
| **Starlette** | ASGI framework beneath FastAPI | `TestClient` for sync tests; `WebSocketTestSession` |
| **Pydantic v2** | Validation + serialisation | `pip install pydantic>=2.0`; pydantic-core is a Rust wheel |
| **pydantic-settings** | `BaseSettings` with env/dotenv | `pip install pydantic-settings` |
| **SQLAlchemy 2.0** | Async ORM | `create_async_engine`, `AsyncSession`, `async_sessionmaker` |
| **asyncpg** | Async PostgreSQL driver | Used by SQLAlchemy async; ~3x faster than psycopg2 |
| **Alembic** | DB migrations | `alembic upgrade head`; integrates with SQLAlchemy models |
| **httpx** | Async HTTP client | `AsyncClient` with connection pool; replaces `requests` |
| **slowapi** | Rate limiting | `limits` library backend; Redis for distributed counters |
| **structlog** | Structured JSON logging | `structlog.configure()`; outputs JSON per log event |
| **OpenTelemetry** | Traces, metrics, logs | `opentelemetry-instrumentation-fastapi` auto-instruments |
| **Prometheus** | Metrics scraping | `prometheus-fastapi-instrumentator` exposes `/metrics` |
| **pytest-asyncio** | Async test runner | `@pytest.mark.asyncio`; `asyncio_mode = "auto"` in `pytest.ini` |
| **orjson** | Fast JSON serialisation | Drop-in replacement; `pip install orjson`; ~10x faster |
| **aioredis / redis.asyncio** | Async Redis client | `redis.asyncio.from_url(...)` |
| **aiokafka** | Async Kafka producer/consumer | `AIOKafkaConsumer`, `AIOKafkaProducer` |
| **aio-pika** | Async RabbitMQ client | `aio_pika.connect_robust(...)` |
| **ARQ** | Async Redis-backed task queue | Lighter than Celery; built for async workers |
| **pip-audit** | Dependency vulnerability scan | `pip-audit --requirement requirements.txt` |

**Gunicorn worker formula:**
```
workers = (2 x CPU_cores) + 1
Example: 4-core machine -> 9 workers
Example: 8-core machine -> 17 workers
```
Each worker is an independent Python process with its own event loop. Workers do not share memory.
State that must be shared across workers (sessions, rate limit counters, pub/sub) must live in an
external store (Redis, database).

**SQLAlchemy async engine recommended config:**
```python
create_async_engine(
    settings.database_url,
    pool_size=10,      # matches HikariCP default; base pool kept alive
    max_overflow=20,   # burst capacity to 30 total connections
    pool_timeout=30,   # seconds to wait for connection from pool
    pool_recycle=1800, # recycle connections idle > 30 minutes
    echo=False,        # True for SQL debug logging only
)
```

---

## 12. Interview Questions with Answers

**Q: What is ASGI and how does it differ from WSGI?**
ASGI (Asynchronous Server Gateway Interface) defines an async callable `app(scope, receive, send)`
that handles arbitrary connection types — HTTP/1.1, HTTP/2, WebSockets, lifespan events — on a
single event loop thread. WSGI (Web Server Gateway Interface) defines a synchronous callable
`app(environ, start_response)` that handles one HTTP request per OS thread. WSGI cannot natively
handle WebSockets or long-lived connections without hacks like gevent monkey-patching. The
practical consequence: a WSGI server running 200 threads can handle 200 concurrent blocking requests;
a single ASGI event loop can handle tens of thousands of concurrent I/O-bound requests.

**Q: What happens when you call a blocking function inside an `async def` FastAPI route?**
The blocking call executes on the event loop thread — the same thread driving all other coroutines.
No exception is raised. Every other request on that worker stalls until the blocking call returns.
A 5ms blocking DB call at 1,000 requests/second means the event loop is blocked 5 seconds of every
second — effectively making the worker single-threaded with sequential request processing. The fix
is to use async I/O libraries (`httpx`, `asyncpg`, `asyncio`) or offload to a thread pool executor
via `run_in_executor` for unavoidable sync code.

**Q: How does FastAPI's dependency injection work? How is it different from Spring's IoC?**
FastAPI's DI is request-scoped and resolved at function call time. At startup, FastAPI introspects
route function signatures and identifies `Depends()` parameters. At request time it builds and walks
the dependency DAG, calling each dependency in topological order and caching results by callable
object within the request. Spring's IoC resolves dependencies at application context creation (or
prototype scope at call time) and injects them via constructor, field, or setter injection using
class-level scanning and XML/annotation metadata. FastAPI's model is lighter: no reflection of class
hierarchies, no circular dependency detection at startup (it fails at request time), no application
context object — just function composition.

**Q: What is the lifecycle of a request in FastAPI from bytes-in to bytes-out?**
(1) Uvicorn parses HTTP bytes with httptools. (2) An ASGI scope dict is created. (3) Starlette's
middleware chain executes top-to-bottom; each middleware calls `await call_next(request)`.
(4) The router matches path and method, extracts path parameters. (5) FastAPI resolves the
dependency DAG, executing and caching `Depends()` callables in dependency order. (6) Pydantic v2
validates all incoming data — a 422 is raised here on failure. (7) The route handler coroutine is
awaited. (8) The return value is serialised through `response_model` by Pydantic, then converted to
JSON bytes. (9) The response bytes are sent via Uvicorn. (10) Yield-based dependency cleanup runs
(`finally` blocks after `yield`).

**Q: How does Pydantic v2 improve on v1? What changed in the validation architecture?**
Pydantic v1 executed validation in pure Python: for each field, it called a Python validator
function. Pydantic v2 compiles a validation schema from the model class definition into Rust data
structures in `pydantic-core`. At validation time, the Rust extension processes the input with no
Python-level per-field dispatch. Benchmarks show 5–50x speedups for complex models. API changes:
`@validator` becomes `@field_validator`, `class Config` becomes `model_config = ConfigDict(...)`,
`.dict()` becomes `.model_dump()`, `.json()` becomes `.model_dump_json()`.

**Q: What is the difference between `BackgroundTasks` and Celery/ARQ?**
`BackgroundTasks` runs a function in the same process after the HTTP response is sent. It is
in-process, has no persistence, no retry on failure, and no visibility into task status. If the
process restarts, the task is lost. Celery and ARQ are distributed task queues: tasks are
serialised and stored in a broker (Redis, RabbitMQ); workers pick them up independently; retries,
dead-letter queues, and task status tracking are built in. Use `BackgroundTasks` for lightweight
fire-and-forget work (sending a non-critical email, logging). Use Celery/ARQ for work that must
complete reliably, requires retry, or takes more than a few seconds.

**Q: How do you handle database transactions in FastAPI with SQLAlchemy async?**
Use an `AsyncSession` injected via a `yield`-based `Depends()`. The session is created at the start
of the dependency, the `yield` hands it to the route handler, and cleanup in the `finally` block
handles commit-or-rollback:

```python
from sqlalchemy.ext.asyncio import AsyncSession

async def get_db() -> AsyncSession:
    async with app.state.session_factory() as session:
        async with session.begin():
            yield session
        # session.begin() commits on successful exit, rolls back on exception
```

For explicit control: call `await session.commit()` in the handler and `await session.rollback()`
in the `except` block. Never call `session.commit()` from inside a `Depends()` that wraps multiple
route calls — each request should own exactly one transaction.

**Q: What is the lifespan event and why is it better than `@app.on_event('startup')`?**
The lifespan context manager (`@asynccontextmanager async def lifespan(app)`) makes startup and
shutdown logic a single coherent unit. The `yield` separates setup from teardown; exceptions in
startup propagate naturally; cleanup always runs because it is in a `finally` block (implicit in the
context manager protocol). `@app.on_event` registered two separate functions with no guaranteed
pairing — if the startup handler created a resource, the shutdown handler had to know the variable
name to clean it up. The lifespan pattern also works in tests: `async with LifespanManager(app):`
exercises both sides in one context.

**Q: How do you test FastAPI endpoints with a real (rolled-back) database?**
Use `httpx.AsyncClient` with `ASGITransport` for async tests, and override the `get_db` dependency
to return a session bound to a test transaction. Roll back the transaction after each test:

```python
@pytest.fixture
async def client(db_session: AsyncSession) -> AsyncClient:
    async def override_get_db() -> AsyncSession:
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as ac:
        yield ac
    app.dependency_overrides = {}

@pytest.fixture
async def db_session(engine: AsyncEngine) -> AsyncSession:
    async with engine.connect() as conn:
        await conn.begin()
        async with AsyncSession(bind=conn) as session:
            yield session
        await conn.rollback()  # all test data discarded
```

**Q: What is BOLA (Broken Object Level Authorization) and how do you prevent it?**
BOLA (OWASP API Security Top 10 #1) occurs when an endpoint accepts a user-supplied object ID and
fetches the object without verifying the requesting user is authorised to access it. Example: `GET
/orders/{order_id}` returns the order for any authenticated user if the handler does
`db.get(Order, order_id)` without checking `order.user_id == current_user.id`. Prevention: always
filter queries by the authenticated user's identity, never trust the client-supplied ID alone.
In SQLAlchemy: `select(Order).where(Order.id == order_id, Order.user_id == current_user.id)` —
if the result is `None`, return 404 (not 403, to avoid leaking existence).

**Q: How do you scale FastAPI horizontally? What must be stateless?**
FastAPI workers (Gunicorn processes) scale horizontally behind a load balancer. Each worker is an
independent process with no shared memory. Anything stored in `app.state`, module-level variables,
or in-process caches is local to one worker and invisible to others. For horizontal scaling, all
shared state must move to external stores: user sessions to Redis, rate limit counters to Redis,
database state to PostgreSQL, pub/sub channels to Redis or Kafka. Kubernetes HPA scales the
Deployment replica count based on CPU utilisation (target ~70%); each pod runs one Gunicorn
process with `(2 x cores) + 1` Uvicorn workers.

**Q: What is the `def` vs `async def` route distinction and when does it matter?**
`async def` routes run directly on the event loop thread. Any blocking call in them blocks the
entire loop. `def` routes are run by FastAPI in a thread pool executor (default: `concurrent.futures.ThreadPoolExecutor`
with up to 40 threads), so they do not block the event loop. However, 40 concurrent blocking `def`
routes exhaust the threadpool and new requests queue. The correct rule: use `async def` for all
I/O-bound routes (DB, HTTP, Redis) with async libraries; use `def` for genuinely CPU-bound routes
(number crunching, image processing) where thread-level parallelism is acceptable.

**Q: How do you configure Gunicorn + Uvicorn workers for production? What is the formula?**
Formula: `workers = (2 x CPU_cores) + 1`. For a 4-core machine: 9 workers. Each worker is a
separate process with its own event loop and memory space. Start Gunicorn with:

```bash
gunicorn app.main:app \
  --worker-class uvicorn.workers.UvicornWorker \
  --workers 9 \
  --bind 0.0.0.0:8000 \
  --timeout 120 \
  --graceful-timeout 30 \
  --keep-alive 5
```

In Kubernetes, run one Gunicorn process per pod (workers = `(2 x pod_cpu_limit) + 1`) and scale
pods with HPA. Do not over-provision workers beyond available CPU — beyond `2N+1` workers, context
switching overhead reduces throughput.

**Q: How do you add rate limiting to a FastAPI route?**
Use `slowapi` with a Redis backend for distributed rate limiting across workers:

```python
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

limiter = Limiter(key_func=get_remote_address, storage_uri="redis://localhost:6379")
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

@app.get("/api/search")
@limiter.limit("100/minute")
async def search(request: Request, q: str) -> list[dict]:
    ...
```

The `key_func` determines what to rate-limit by (IP, user ID, API key). For authenticated routes,
key by `current_user.id` to prevent shared-IP clients from blocking each other. The Redis backend
ensures the counter is consistent across all Gunicorn workers and pods.

**Q: What is `response_model` and why should you always set it?**
`response_model` is the Pydantic model FastAPI uses to serialise the route handler's return value
before sending the response. It filters the output to only the fields declared in the model, applies
field aliases, excludes `None` values if configured, and documents the response schema in OpenAPI.
Without `response_model`, FastAPI returns the raw Python object serialised as-is — no field
filtering, no documentation. This risks leaking internal fields (password hashes, internal IDs,
audit timestamps). Always set `response_model` on every route, even if the return type annotation
is the same model — the annotation only helps type checkers, not runtime serialisation.

**Q: How does yield-based dependency cleanup work and when does it run?**
A dependency with `yield` acts as a context manager. FastAPI awaits the dependency up to the
`yield`, injects the yielded value, then after the response is sent, resumes the dependency's
generator to run the code after `yield`. This cleanup always runs — both on successful responses
and on unhandled exceptions. It is the correct place for `session.close()`, releasing locks, and
logging request completion. The cleanup runs after middleware teardown (the middleware's `call_next`
has already returned), so middleware cannot observe the dependency cleanup.

**Q: How does FastAPI handle 422 Unprocessable Entity errors?**
FastAPI raises `RequestValidationError` (a Starlette/FastAPI exception) when Pydantic validation
fails for any incoming data — path params, query params, headers, or request body. The default
exception handler converts this to a 422 response with a JSON body listing every validation error
with its location (`body`, `query`, `path`), field name, and error message. You can override the
handler to produce RFC 7807 Problem Details format:

```python
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.exceptions import RequestValidationError

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={
            "type": "https://example.com/errors/validation",
            "title": "Validation Error",
            "detail": exc.errors(),
        },
    )
```

---

## 13. Best Practices

**Application structure:**
- Always use `lifespan` for connection pool initialisation — never `@app.on_event`.
- Store shared resources on `app.state` (engine, Redis client) in lifespan; inject via `Depends()`.
- Disable Swagger UI and ReDoc in production: `FastAPI(docs_url=None, redoc_url=None)`.

**Pydantic model discipline:**
- Define separate models: `XCreate` (input), `XUpdate` (partial update), `XResponse` (output),
  `XInDB` (ORM representation). Never use the ORM model as a response model.
- Set `model_config = ConfigDict(from_attributes=True)` on response models that are built from ORM
  objects (`model_validate(orm_obj)`).
- Use `X | None` not `Optional[X]`; use `str | int` not `Union[str, int]`.

**Routing and responses:**
- Set `response_model` on every route — never return raw dicts from a route handler.
- Return `None` with `status_code=204` for DELETE and other no-content responses.
- Use `APIRouter` with `prefix` and `tags` for every feature; never add routes directly to `app`
  in feature modules.

**Dependency injection:**
- Use `dependency_overrides` for test isolation; reset with `app.dependency_overrides = {}` in
  pytest fixture teardown (`autouse=True`).
- Use `@lru_cache` only on pure, stateless functions (`get_settings()`). Never on resource factories.

**Database:**
- Configure `pool_size=10, max_overflow=20` on `create_async_engine` — same reasoning as HikariCP.
- Use `async_sessionmaker(..., expire_on_commit=False)` to avoid lazy-loading errors after commit.
- Always add `selectinload()` or `joinedload()` for related collections — never trigger N+1 by
  accessing an unloaded relationship attribute in an async context.

**Observability:**
- Enable structured logging with `structlog` and JSON renderer from day one.
- Add OpenTelemetry auto-instrumentation: `FastAPIInstrumentor().instrument_app(app)`.
- Expose a `/health` (liveness) and `/ready` (readiness) endpoint; K8s probes require both.

**Security:**
- Use `python-jose` or `PyJWT` for JWT; validate `iss`, `aud`, `exp` on every request.
- Store passwords with `argon2-cffi` (argon2id algorithm) — never bcrypt for new systems unless
  legacy compatibility is required.
- Run `pip-audit` in CI to catch dependency CVEs before deployment.
- Set `CORS` to an explicit allowlist — never `allow_origins=["*"]` in production.

**Performance:**
- Install `uvloop` and `httptools` — Uvicorn uses them automatically for a 2–4x event loop speedup.
- Use `orjson` as the JSON encoder for 5–10x faster serialisation: `ORJSONResponse` as the default
  response class.
- Run `(2 x CPU_cores) + 1` Uvicorn workers under Gunicorn — do not under-provision.

---

## 14. Case Study

The six case studies in `case_studies/` each target a specific production engineering challenge.
All use the 11-section principal template (Requirements, Scale Estimation, Architecture, Component
Deep Dives, Design Decisions, Real-World Implementations, Technologies, Operational Playbook,
Pitfalls, Capacity Planning, Interview Discussion Points).

| Case Study | Core Challenge | Key Patterns |
|------------|---------------|--------------|
| [Design a Rate-Limited API](case_studies/design_rate_limited_api_fastapi.md) | Enforcing per-user and per-IP request limits without adding latency | Token-bucket algorithm in Redis Lua script; `slowapi` DI integration; sliding window vs fixed window tradeoffs |
| [Design a Multi-Tenant SaaS API](case_studies/design_multi_tenant_saas_api.md) | Isolating tenant data and preventing cross-tenant data leakage | PostgreSQL Row Level Security; tenant context injected via JWT claim; async SQLAlchemy session with `set_role` |
| [Design a Real-Time Chat System](case_studies/design_realtime_chat_fastapi.md) | Pushing messages to thousands of concurrent WebSocket connections across multiple workers | Redis pub/sub fan-out; one asyncio task per connection; horizontal scaling requires external broker |
| [Design an Async Task Queue](case_studies/design_async_task_queue.md) | Running background jobs reliably with retry, visibility, and dead-letter handling | ARQ vs Celery comparison; idempotency keys for at-least-once delivery; DLQ pattern for poison messages |
| [Design an Async Web Scraper](case_studies/design_async_web_scraper.md) | Crawling thousands of URLs concurrently while respecting robots.txt and rate limits | `asyncio.Semaphore` for concurrency control; producer/consumer with bounded queue; politeness delay |
| [Design an ML Inference API](case_studies/design_ml_inference_api_fastapi.md) | Serving a large language model with low tail latency and token-streaming responses | `lifespan` model loading (no per-request load overhead); micro-batching for GPU utilisation; `StreamingResponse` with SSE for token streaming |

**Reading sequence for interview preparation:**
1. Start with the Rate-Limited API — covers core DI, middleware, and Redis patterns.
2. Multi-Tenant SaaS — covers auth, database isolation, and async SQLAlchemy at depth.
3. Real-Time Chat — covers WebSockets, pub/sub, and horizontal scaling constraints.
4. ML Inference API — covers lifespan, async streaming, and production deployment patterns.

---

**Back to Python master index:** [../README.md](../README.md)
