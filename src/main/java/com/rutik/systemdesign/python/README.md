# Python + FastAPI — Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **Python** — from CPython internals and the GIL through asyncio, the type system, and FastAPI's ASGI stack, to production concerns (async SQLAlchemy, JWT/OAuth2, task queues, observability, K8s deployment, and OWASP hardening). Covers everything a senior Python software engineer is expected to know in technical interviews.

> **No runtime application** — all content is Markdown with executable-shaped Python 3.11/3.12 code blocks.

---

## 1. Section Overview

This section covers:

- **Pure Python internals** — object model (everything is an object, dunder methods, `__slots__`, MRO/C3 linearization), CPython memory management (reference counting, generational GC, arenas/pools/blocks), the GIL and free-threading (PEP 703), metaclasses and descriptors, the type system (`Protocol`, `TypeVar`, `ParamSpec`, PEP 695 generics)
- **Concurrency and async** — `threading`, `multiprocessing`, `concurrent.futures`; `asyncio` event loop internals, coroutines, `TaskGroup` (3.11), structured concurrency; blocking-in-async pitfalls, backpressure, retries
- **Python ecosystem** — `itertools`, `collections`, `functools`, `contextlib`; `pytest` + `hypothesis`; `pyproject.toml`, `uv`, `ruff`, `mypy`; design patterns in Pythonic idioms
- **FastAPI and ASGI** — the ASGI 3 protocol (scope/receive/send), Starlette routing, Uvicorn event loop, `lifespan`, auto-OpenAPI; Pydantic v2 (`pydantic-core` Rust backend, `@field_validator`, v1→v2 migration); dependency injection (`Depends`, `yield` deps, caching scopes)
- **Production API concerns** — async SQLAlchemy 2.0, Alembic migrations, N+1 avoidance; JWT/OAuth2 auth; WebSockets/SSE; Celery/ARQ task queues; `httpx`/`aiohttp` clients; `aiokafka` event-driven patterns
- **Deployment and operations** — Gunicorn+Uvicorn worker sizing, K8s probes, graceful shutdown; OpenTelemetry tracing; Prometheus metrics; Redis caching; OWASP API Top 10 hardening

**Primary language:** Python 3.11/3.12. Version tags mark features introduced in earlier or later versions.

---

## 2. Module Table

| # | Module Directory | Phase | Difficulty | Key Topics |
|---|-----------------|-------|-----------|-----------|
| 1 | [data_model_and_objects](data_model_and_objects/) | 1 — Language Core | Intermediate | Dunder methods, `__slots__`, MRO/C3, operator overloading, hashing/equality contract |
| 2 | [core_language_idioms](core_language_idioms/) | 1 — Language Core | Intermediate | Mutability vs identity, EAFP vs LBYL, comprehensions, walrus `:=` (3.8), `match` (3.10) |
| 3 | [iterators_and_generators](iterators_and_generators/) | 1 — Language Core | Intermediate | Iterator protocol, `yield`/`yield from`, lazy pipelines, `itertools`, generator coroutines |
| 4 | [decorators_and_closures](decorators_and_closures/) | 1 — Language Core | Intermediate | Closures, free variables, function/class/parametrized decorators, `functools.wraps/lru_cache/cached_property` |
| 5 | [context_managers_and_exceptions](context_managers_and_exceptions/) | 1 — Language Core | Intermediate | `contextlib`, `ExitStack`, async CMs, `ExceptionGroup`/`except*` (3.11), traceback manipulation |
| 6 | [collections_and_data_structures](collections_and_data_structures/) | 1 — Language Core | Intermediate | `list`/`dict`/`set` internals + Big-O, `collections` (deque/Counter/defaultdict), `heapq`, `bisect` |
| 7 | [strings_bytes_encoding_and_regex](strings_bytes_encoding_and_regex/) | 1 — Language Core | Intermediate | `str` vs `bytes`, Unicode/codecs, `memoryview`, `re` engine, catastrophic backtracking |
| 8 | [file_io_and_serialization](file_io_and_serialization/) | 1 — Language Core | Beginner | `pathlib`, text/binary I/O, `json`/`csv`, `pickle` security, `struct` |
| 9 | [cpython_memory_model](cpython_memory_model/) | 2 — CPython Internals | Advanced | Refcounting, generational GC, cyclic GC, `PyObject` header, arenas/pools/blocks, `sys.getsizeof` |
| 10 | [the_gil_and_free_threading](the_gil_and_free_threading/) | 2 — CPython Internals | Advanced | GIL mechanics, GIL release points, contention profiling, PEP 703 (3.13), PEP 684 sub-interpreters |
| 11 | [metaclasses_and_metaprogramming](metaclasses_and_metaprogramming/) | 2 — CPython Internals | Advanced | `type()`, metaclasses, `__init_subclass__`, `__set_name__`, descriptors (non-data/data), `__getattr__` |
| 12 | [the_type_system_and_typing](the_type_system_and_typing/) | 2 — CPython Internals | Advanced | Type hints, generics, `Protocol` (structural), `TypeVar`/`ParamSpec`, variance, PEP 695 (3.12), mypy/pyright |
| 13 | [performance_and_profiling](performance_and_profiling/) | 2 — CPython Internals | Advanced | `cProfile`/`line_profiler`/`dis`, CPython 3.11+ speedups, Cython/mypyc/C extensions, slow patterns |
| 14 | [functional_programming](functional_programming/) | 2 — CPython Internals | Intermediate | `map`/`filter`/`reduce`, `functools`/`operator`, immutability, currying/partial, comprehension vs generator perf |
| 15 | [threading_and_multiprocessing](threading_and_multiprocessing/) | 3 — Concurrency & Quality | Advanced | `threading`, GIL impact, `multiprocessing`, `concurrent.futures`, shared memory, pickling cost |
| 16 | [asyncio_and_event_loop](asyncio_and_event_loop/) | 3 — Concurrency & Quality | Advanced | Coroutines, event-loop internals, tasks/futures, `gather`/`wait`, `TaskGroup` (3.11), `anyio` |
| 17 | [async_patterns_and_pitfalls](async_patterns_and_pitfalls/) | 3 — Concurrency & Quality | Advanced | Blocking-in-async detection, `run_in_executor`, async generators, `Semaphore`, backpressure, retries |
| 18 | [design_patterns_in_python](design_patterns_in_python/) | 3 — Concurrency & Quality | Intermediate | Pythonic GoF (singleton/strategy/observer/factory), anti-patterns — cross-links `lld/` |
| 19 | [stdlib_datetime_and_logging](stdlib_datetime_and_logging/) | 3 — Concurrency & Quality | Intermediate | `datetime`/`zoneinfo`/tz pitfalls, structured `logging`, `argparse`, `subprocess`, `os`/`sys` |
| 20 | [testing_with_pytest](testing_with_pytest/) | 3 — Concurrency & Quality | Intermediate | pytest, fixtures/scopes, `parametrize`, `monkeypatch`, `hypothesis`, coverage, `pytest-asyncio` |
| 21 | [packaging_and_project_tooling](packaging_and_project_tooling/) | 3 — Concurrency & Quality | Intermediate | `pyproject.toml`, `uv`/poetry/pip, venv, wheels/sdist, `ruff`/mypy, semantic versioning |
| 22 | [fastapi_fundamentals_asgi](fastapi/fastapi_fundamentals_asgi/) | 4 — FastAPI Core | Intermediate | ASGI vs WSGI, Starlette, Uvicorn, `lifespan`, ASGI 3 scope/receive/send, auto OpenAPI/Swagger |
| 23 | [pydantic_v2_deep_dive](fastapi/pydantic_v2_deep_dive/) | 4 — FastAPI Core | Advanced | Validation, serialization, `@field_validator`/`@model_validator`, `pydantic-core` Rust, v1→v2, `BaseSettings` |
| 24 | [routing_and_request_handling](fastapi/routing_and_request_handling/) | 4 — FastAPI Core | Intermediate | Path operations, path/query/body/form params, `APIRouter`, response models, status codes, content neg. |
| 25 | [dependency_injection_in_fastapi](fastapi/dependency_injection_in_fastapi/) | 4 — FastAPI Core | Advanced | `Depends`, sub-dependencies, `yield` deps (setup/teardown), caching/scopes, class-based deps, overrides |
| 26 | [middleware_and_lifecycle](fastapi/middleware_and_lifecycle/) | 4 — FastAPI Core | Intermediate | Middleware stack order, `BackgroundTasks`, CORS/GZip, custom middleware, exception handler positioning |
| 27 | [configuration_and_settings_management](fastapi/configuration_and_settings_management/) | 4 — FastAPI Core | Intermediate | `pydantic-settings`, 12-factor config, env vars/secrets, layered settings, per-env overrides |
| 28 | [async_database_sqlalchemy](fastapi/async_database_sqlalchemy/) | 5 — Production | Advanced | SQLAlchemy 2.0 async, `AsyncSession`, async engine, pool sizing, Alembic, SQLModel, N+1 avoidance |
| 29 | [authentication_and_security](fastapi/authentication_and_security/) | 5 — Production | Advanced | OAuth2 password flow, JWT (PyJWT), scopes, passlib/bcrypt/argon2, OIDC, CSRF/CORS |
| 30 | [error_handling_and_validation](fastapi/error_handling_and_validation/) | 5 — Production | Intermediate | `HTTPException`, custom exception handlers, `RequestValidationError`, RFC 7807 Problem Details |
| 31 | [testing_fastapi](fastapi/testing_fastapi/) | 5 — Production | Intermediate | `TestClient`, `httpx.AsyncClient`, `pytest-asyncio`, `dependency_overrides`, transactional rollback |
| 32 | [websockets_sse_and_streaming](fastapi/websockets_sse_and_streaming/) | 5 — Production | Advanced | WebSockets, SSE, `StreamingResponse`, Redis pub/sub fan-out, connection registry, backpressure |
| 33 | [background_jobs_and_task_queues](fastapi/background_jobs_and_task_queues/) | 5 — Production | Advanced | `BackgroundTasks` vs Celery vs ARQ vs Dramatiq, scheduling, idempotency, retries, DLQ |
| 34 | [http_clients_and_external_apis](fastapi/http_clients_and_external_apis/) | 5 — Production | Intermediate | `httpx`/`aiohttp` async clients, connection pooling, retries/backoff, circuit breakers, timeouts |
| 35 | [message_queues_and_event_driven](fastapi/message_queues_and_event_driven/) | 5 — Production | Advanced | `aiokafka`/`aio-pika`, outbox pattern, consumer groups, idempotent consumers |
| 36 | [production_deployment_and_scaling](fastapi/production_deployment_and_scaling/) | 6 — Deployment & Ops | Advanced | Gunicorn+Uvicorn workers, worker tuning, container/K8s, graceful shutdown, ASGI scaling |
| 37 | [observability_and_monitoring](fastapi/observability_and_monitoring/) | 6 — Deployment & Ops | Advanced | Structured logging, OpenTelemetry tracing, Prometheus metrics, health/readiness probes |
| 38 | [caching_and_performance](fastapi/caching_and_performance/) | 6 — Deployment & Ops | Advanced | Redis caching, response/in-process caching, connection pooling, profiling FastAPI, async pitfalls |
| 39 | [api_design_and_versioning](fastapi/api_design_and_versioning/) | 6 — Deployment & Ops | Intermediate | REST best practices, versioning strategies, cursor pagination, rate limiting, idempotency keys |
| 40 | [security_hardening_and_owasp](fastapi/security_hardening_and_owasp/) | 6 — Deployment & Ops | Advanced | OWASP API Top 10 in FastAPI, injection/SSRF, deserialization risk, secrets handling, pip-audit |

---

## 3. 6-Phase Learning Path

```
Phase 1 — Language Core & Data Model (8 modules)
+------------------------------------------------------------+
|  data_model_and_objects       core_language_idioms         |
|  iterators_and_generators     decorators_and_closures      |
|  context_managers_and_exceptions                           |
|  collections_and_data_structures                           |
|  strings_bytes_encoding_and_regex                          |
|  file_io_and_serialization                                 |
+------------------------------------------------------------+
                               |
                               v
Phase 2 — CPython Internals & Type System (6 modules)
+------------------------------------------------------------+
|  cpython_memory_model         the_gil_and_free_threading   |
|  metaclasses_and_metaprogramming                           |
|  the_type_system_and_typing   performance_and_profiling    |
|  functional_programming                                    |
+------------------------------------------------------------+
                               |
                               v
Phase 3 — Concurrency, Async & Quality (7 modules)
+------------------------------+  +--------------------------+
|  threading_and_              |  |  design_patterns_in      |
|  multiprocessing             |  |  python                  |
|  asyncio_and_event_loop      |  |  stdlib_datetime_and     |
|  async_patterns_and_pitfalls |  |  logging                 |
|                              |  |  testing_with_pytest     |
|                              |  |  packaging_and_project   |
|                              |  |  tooling                 |
+------------------------------+  +--------------------------+
               |                               |
               +-----------v-------------------+
Phase 4 — FastAPI Core & ASGI (6 modules)
+------------------------------------------------------------+
|  fastapi_fundamentals_asgi    pydantic_v2_deep_dive        |
|  routing_and_request_handling                              |
|  dependency_injection_in_fastapi                           |
|  middleware_and_lifecycle                                  |
|  configuration_and_settings_management                    |
+------------------------------------------------------------+
                               |
                               v
Phase 5 — FastAPI Production Concerns (8 modules)
+------------------------------+  +--------------------------+
|  async_database_sqlalchemy   |  |  testing_fastapi         |
|  authentication_and_security |  |  http_clients_and_       |
|  error_handling_and_         |  |  external_apis           |
|  validation                  |  |  message_queues_and_     |
|  websockets_sse_and_         |  |  event_driven            |
|  streaming                   |  +--------------------------+
|  background_jobs_and_        |
|  task_queues                 |
+------------------------------+
                               |
                               v
Phase 6 — Deployment, Observability & Scale (5 modules)
+------------------------------------------------------------+
|  production_deployment_and_scaling                         |
|  observability_and_monitoring                              |
|  caching_and_performance                                   |
|  api_design_and_versioning                                 |
|  security_hardening_and_owasp                              |
+------------------------------------------------------------+
```

**Dependencies to note:**
- Phase 3 `asyncio` modules require Phase 1 generators/iterators (coroutines are generators under the hood).
- Phase 4 `pydantic_v2_deep_dive` benefits from Phase 2 `the_type_system_and_typing` (TypeVar, Protocol, Annotated).
- Phase 4 `dependency_injection_in_fastapi` requires Phase 3 async generators (`yield` deps use async generator protocol).
- Phase 5 `async_database_sqlalchemy` requires Phase 4 DI (session-per-request via `Depends`).
- Phase 5 `authentication_and_security` requires Phase 4 DI + routing (OAuth2 flows modeled as `Depends` chains).
- Phase 6 has no strict ordering within itself; study in parallel once Phase 5 is complete.

---

## 4. Python Version Matrix

| Feature | Version | Notes |
|---------|---------|-------|
| f-strings | 3.6 | Widely deployed baseline |
| `dataclasses` | 3.7 | `@dataclass`, `field()` |
| `asyncio.run()` | 3.7 | Stable public entry point |
| walrus operator `:=` | 3.8 | PEP 572, assignment expressions |
| Positional-only params `/` | 3.8 | PEP 570 |
| `TypedDict` | 3.8 | `typing.TypedDict` |
| `Union[X, Y]` as `X \| Y` | 3.10 | PEP 604 |
| `ParamSpec`, `Concatenate` | 3.10 | PEP 612 |
| `match`/`case` structural pattern matching | 3.10 | PEP 634 |
| `typing.TypeAlias` | 3.10 | PEP 613 |
| `tomllib` (stdlib TOML reader) | 3.11 | PEP 680 |
| Exception Groups + `except*` | 3.11 | PEP 654 |
| `asyncio.TaskGroup` | 3.11 | PEP 654, structured concurrency |
| `typing.Self` | 3.11 | PEP 673 |
| `typing.LiteralString` | 3.11 | PEP 675, SQL injection safety |
| CPython 3.11 speedup | 3.11 | ~25% faster than 3.10 (specializing adaptive interpreter, faster frame eval) |
| `typing.TypeVarTuple` | 3.11 | PEP 646, variadic generics |
| PEP 695 generics `type X[T] = ...` | 3.12 | New type alias syntax, inline TypeVar |
| `@override` decorator | 3.12 | PEP 698 |
| `asyncio.eager_task_factory` | 3.12 | Reduce event-loop overhead for short coroutines |
| Sub-interpreters (stable C API) | 3.12 | PEP 684 |
| Free-threading GIL-optional (`python3.13t`) | 3.13 | PEP 703, experimental |
| JIT compiler (copy-and-patch) | 3.13 | Experimental, ~5% speedup in benchmarks |

### FastAPI / Pydantic / SQLAlchemy Version Notes

| Library | Version | Key Changes |
|---------|---------|------------|
| Pydantic | 1.x | `@validator`, `.dict()`, `orm_mode = True` in `Config` class |
| Pydantic | 2.0+ (2023) | `@field_validator`, `.model_dump()`, `model_config = ConfigDict(...)`, `pydantic-core` Rust — 5–50x faster |
| FastAPI | 0.95+ | First `lifespan` context manager support |
| FastAPI | 0.100+ | Official Pydantic v2 support |
| FastAPI | 0.110+ | `lifespan` replaces `on_startup`/`on_shutdown` as recommended pattern |
| Starlette | 0.27+ | ASGI 3 protocol standardized; `lifespan` event type |
| SQLAlchemy | 1.4 | Legacy `Query` API + new 2.0-style `select()` statements, both supported |
| SQLAlchemy | 2.0 (2023) | Unified 2.0-style only; `AsyncSession`/`async_engine` stable; typed ORM models |
| SQLModel | 0.0.14+ | Pydantic v2 + SQLAlchemy 2.0 unified models |
| Alembic | 1.13+ | `--autogenerate` with async support via `run_sync` |
| httpx | 0.24+ | `AsyncClient` with connection limits, event hooks |
| anyio | 3.x / 4.x | Backend-agnostic structured concurrency; FastAPI/Starlette use anyio internally |

---

## 5. Top Interview Topics by Category

### Python Object Model & Internals
1. **How does Python's attribute lookup work?** Python checks (in order): data descriptors on the class/MRO, then instance `__dict__`, then non-data descriptors and class attributes. `property` is a data descriptor (has `__set__`), so it always intercepts instance attribute access.
2. **Explain `__slots__` and when to use it.** `__slots__` replaces per-instance `__dict__` with a compact C-level array. Saves 50–200 bytes per instance, speeds attribute access, prevents accidental attribute creation. Use when creating millions of instances; trade-off is loss of dynamic attributes.
3. **What is C3 linearization?** The MRO algorithm ensuring consistency in multiple inheritance. It respects local precedence order and monotonicity. `ClassName.__mro__` shows the resolution order; `super()` follows it cooperatively.
4. **What is a descriptor?** An object implementing `__get__`/`__set__`/`__delete__`. Python's attribute machinery calls `__get__` on class attribute access. `property`, `classmethod`, `staticmethod` are all implemented as descriptors.
5. **How does Python's `__hash__` contract work?** Objects that compare equal (`__eq__`) must have the same hash. If you override `__eq__`, Python sets `__hash__ = None` (unhashable) unless you also define `__hash__`. Mutable objects should not be hashable.

### GIL, Concurrency & asyncio
1. **When is the GIL released?** CPython releases the GIL every `sys.getswitchinterval()` (default 5 ms) of bytecode execution, and on every blocking I/O system call (socket, file, `time.sleep`). Pure CPU-bound Python bytecode holds the GIL continuously.
2. **When does multi-threading help despite the GIL?** I/O-bound workloads. While one thread blocks on I/O, the GIL is released and other threads can run. For CPU-bound work, use `multiprocessing` (separate processes = separate GILs).
3. **Explain the async/await execution model.** `async def` defines a coroutine. `await expr` suspends the coroutine, yielding control to the event loop, which uses `select()`/`epoll`/`kqueue` to multiplex I/O readiness. No OS threads are involved; a single thread executes all coroutines interleaved.
4. **What does `asyncio.TaskGroup` improve over `gather`?** TaskGroup (3.11, PEP 654) provides structured concurrency: if any child task raises, remaining tasks are cancelled immediately, and ALL exceptions are collected into an `ExceptionGroup`. `gather` propagates only the first exception by default, leaving others silently swallowed.
5. **What happens if you call `requests.get()` inside `async def`?** It blocks the event loop thread for the entire network round-trip — all other coroutines stall. Fix: use `httpx.AsyncClient` or `await asyncio.get_event_loop().run_in_executor(None, requests.get, url)`.

### FastAPI & Pydantic
1. **How does FastAPI's dependency injection work?** FastAPI inspects `Depends(fn)` signatures recursively at startup to build a dependency graph. At request time it topologically resolves the graph, caching values within the same request scope by default. `yield`-based deps run setup code, yield the value, then run teardown (in a `finally` block) after the response is sent.
2. **Difference between `async def` and `def` path operations?** `async def` routes run on the event loop thread. `def` routes run in a Starlette thread-pool executor (`run_in_threadpool`). Use `async def` for async I/O; use `def` for CPU-bound work (gets its own thread, won't block the loop). Using `def` for blocking I/O wastes threadpool threads.
3. **How does Pydantic v2 differ from v1?** Validator core rewritten in Rust (`pydantic-core`). API changes: `@validator` → `@field_validator`, `.dict()` → `.model_dump()`, `orm_mode = True` → `model_config = ConfigDict(from_attributes=True)`, `__fields__` → `model_fields`. Validation is 5–50x faster for validation-heavy workloads.
4. **How does the ASGI protocol work?** An ASGI app is `async def app(scope, receive, send)`. `scope` is a dict with connection metadata (type, path, headers). `receive` returns the next event (body chunk, disconnect). `send` writes response events (start, body chunk). Starlette/FastAPI implement this interface; Uvicorn is the server that calls it.

### Architecture & Production
1. **How do you scale a FastAPI service to 100k RPS?** Horizontal pod autoscaling with stateless pods; `(2 × CPU) + 1` Uvicorn workers per pod via Gunicorn; Redis for shared rate-limit/session state; async DB connection pool sized to `DB max_connections / pod_count`; CDN edge cache for static responses; async I/O end-to-end.
2. **How do you implement distributed rate limiting in FastAPI?** Token-bucket or sliding-window counter in Redis. Implement as `Depends(RateLimiter(...))`. Redis operations must be atomic (Lua script or pipeline) to prevent TOCTOU races. Return `HTTP 429` with `Retry-After` header.
3. **How do you handle N+1 in SQLAlchemy 2.0?** Use `selectinload()` or `joinedload()` in `select()` statements. For bulk access use `lazy="selectin"` on relationships. Detect N+1 in tests by enabling `echo=True` on the engine and counting emitted SQL statements.
4. **Zero-downtime deploys with FastAPI?** K8s rolling update + `lifespan` context manager for startup/teardown; `terminationGracePeriodSeconds` > P99 request latency; pre-stop hook sleeps 5s to drain load balancer; readiness probe returns 503 after `SIGTERM`; in-flight requests complete before process exit.

---

## 6. Cross-Reference Map

| Module | Also See |
|--------|---------|
| `data_model_and_objects` | [`lld/behavioral/`](../../lld/behavioral/) — observer/strategy as Python callables |
| `the_gil_and_free_threading` | [`java/concurrency/`](../../java/concurrency/) — compare JVM threading vs CPython GIL |
| `asyncio_and_event_loop` | [`backend/async_and_concurrency_patterns/`](../../backend/async_and_concurrency_patterns/); [`llm/case_studies/cross_cutting/streaming_at_scale.md`](../../llm/case_studies/cross_cutting/streaming_at_scale.md) |
| `the_type_system_and_typing` | [`java/generics_and_type_system/`](../../java/generics_and_type_system/) — erasure vs Python runtime generics |
| `design_patterns_in_python` | [`lld/creational/`](../../lld/creational/); [`lld/structural/`](../../lld/structural/); [`lld/behavioral/`](../../lld/behavioral/) |
| `fastapi_fundamentals_asgi` | [`backend/rest_api_design/`](../../backend/rest_api_design/); [`backend/api_gateway_patterns/`](../../backend/api_gateway_patterns/) |
| `pydantic_v2_deep_dive` | `the_type_system_and_typing` — TypeVar, Protocol, Annotated types |
| `dependency_injection_in_fastapi` | [`spring/dependency_injection/`](../../spring/dependency_injection/) — compare Spring DI to FastAPI `Depends` |
| `async_database_sqlalchemy` | [`database/connection_pool_management/`](../../database/connection_pool_management/); [`database/sql_query_optimization/`](../../database/sql_query_optimization/) |
| `authentication_and_security` | [`backend/auth_and_authorization_systems/`](../../backend/auth_and_authorization_systems/); [`spring/spring_security_architecture/`](../../spring/spring_security_architecture/) |
| `websockets_sse_and_streaming` | [`backend/websockets_and_sse/`](../../backend/websockets_and_sse/); [`llm/case_studies/cross_cutting/streaming_at_scale.md`](../../llm/case_studies/cross_cutting/streaming_at_scale.md) |
| `background_jobs_and_task_queues` | [`spring/spring_messaging/`](../../spring/spring_messaging/); [`hld/`](../../hld/) message queues |
| `http_clients_and_external_apis` | [`backend/api_gateway_patterns/`](../../backend/api_gateway_patterns/) — circuit breaker, retry budgets |
| `message_queues_and_event_driven` | [`hld/`](../../hld/) queues/event-sourcing; [`spring/spring_messaging/`](../../spring/spring_messaging/) |
| `caching_and_performance` | [`hld/`](../../hld/) caching; [`database/database_caching_patterns/`](../../database/database_caching_patterns/) |
| `api_design_and_versioning` | [`backend/rest_api_design/`](../../backend/rest_api_design/); [`backend/rate_limiting_in_depth/`](../../backend/rate_limiting_in_depth/) |
| `security_hardening_and_owasp` | [`backend/security_and_authorization/`](../../backend/security_and_authorization/) |
| `production_deployment_and_scaling` | [`backend/container_and_kubernetes_patterns/`](../../backend/container_and_kubernetes_patterns/) |
| `observability_and_monitoring` | [`backend/observability_and_monitoring/`](../../backend/observability_and_monitoring/); [`llm/llm_observability_and_monitoring/`](../../llm/llm_observability_and_monitoring/) |
| `fastapi/case_studies/design_ml_inference_api_fastapi.md` | [`ml/model_serving_and_inference/`](../../ml/model_serving_and_inference/); [`llm/case_studies/cross_cutting/streaming_at_scale.md`](../../llm/case_studies/cross_cutting/streaming_at_scale.md) |

---

## 7. Case Studies

| Case Study | Core Concepts | Difficulty |
|------------|--------------|-----------|
| [Design a Rate-Limited API with FastAPI](fastapi/case_studies/design_rate_limited_api_fastapi.md) | Token-bucket via Redis Lua, `Depends`-injected rate limiter, async middleware, 429 error handling | Advanced |
| [Design a Multi-Tenant SaaS API](fastapi/case_studies/design_multi_tenant_saas_api.md) | Async SQLAlchemy tenant isolation, JWT/RBAC, `Depends` scoping, schema-per-tenant pattern | Advanced |
| [Design a Real-Time Chat System with FastAPI](fastapi/case_studies/design_realtime_chat_fastapi.md) | WebSockets, Redis pub/sub fan-out, connection registry, backpressure, horizontal scaling | Advanced |
| [Design an Async Task Queue System](fastapi/case_studies/design_async_task_queue.md) | ARQ/Celery/Dramatiq comparison, idempotency, retries + exponential backoff, dead-letter queues | Advanced |
| [Design an Async Web Scraper](fastapi/case_studies/design_async_web_scraper.md) | asyncio + aiohttp, `Semaphore` rate limiting, producer/consumer, crawl budget/politeness | Intermediate |
| [Design an ML Inference API with FastAPI](fastapi/case_studies/design_ml_inference_api_fastapi.md) | Async model serving, micro-batching, async Redis cache, `lifespan` model loading, SSE streaming | Advanced |

---

## 8. Build Status & Implementation Tracker

> **ALL BATCHES COMPLETE — Section fully built.** 40 modules + 6 case studies + 5 sub-files. No pending work.

### Batch Plan

| Batch | Files | Status |
|-------|-------|--------|
| **Batch 1 — Core** | `data_model_and_objects`, `the_gil_and_free_threading`, `asyncio_and_event_loop`, `decorators_and_closures`, `the_type_system_and_typing`, `fastapi/fastapi_fundamentals_asgi`, `fastapi/pydantic_v2_deep_dive`, `fastapi/dependency_injection_in_fastapi`, `fastapi/case_studies/design_rate_limited_api_fastapi.md` | done |
| **Batch 2 — Phase 1 finish** | `core_language_idioms`, `iterators_and_generators`, `context_managers_and_exceptions`, `collections_and_data_structures`, `strings_bytes_encoding_and_regex`, `file_io_and_serialization` | done |
| **Batch 3 — Phase 2 finish** | `cpython_memory_model`, `metaclasses_and_metaprogramming`, `performance_and_profiling`, `functional_programming` | done |
| **Batch 4 — Phase 3 finish** | `threading_and_multiprocessing`, `async_patterns_and_pitfalls`, `design_patterns_in_python`, `stdlib_datetime_and_logging`, `testing_with_pytest`, `packaging_and_project_tooling` | done |
| **Batch 5 — Phase 4 finish** | `fastapi/routing_and_request_handling`, `fastapi/middleware_and_lifecycle`, `fastapi/configuration_and_settings_management` | done |
| **Batch 6 — Phase 5** | `fastapi/async_database_sqlalchemy`, `fastapi/authentication_and_security`, `fastapi/error_handling_and_validation`, `fastapi/testing_fastapi`, `fastapi/websockets_sse_and_streaming`, `fastapi/background_jobs_and_task_queues`, `fastapi/http_clients_and_external_apis`, `fastapi/message_queues_and_event_driven` | done |
| **Batch 7 — Phase 6** | `fastapi/production_deployment_and_scaling`, `fastapi/observability_and_monitoring`, `fastapi/caching_and_performance`, `fastapi/api_design_and_versioning`, `fastapi/security_hardening_and_owasp` | done |
| **Batch 8 — Case studies + sub-files** | `fastapi/case_studies/design_multi_tenant_saas_api.md`, `fastapi/case_studies/design_realtime_chat_fastapi.md`, `fastapi/case_studies/design_async_task_queue.md`, `fastapi/case_studies/design_async_web_scraper.md`, `fastapi/case_studies/design_ml_inference_api_fastapi.md`; sub-files: `asyncio_and_event_loop/event_loop_internals.md`, `asyncio_and_event_loop/structured_concurrency.md`, `fastapi/pydantic_v2_deep_dive/pydantic_core_and_performance.md`, `the_type_system_and_typing/protocols_and_structural_typing.md`, `fastapi/dependency_injection_in_fastapi/yield_dependencies_and_scopes.md` | done |

### All Files Status

| # | Module / File | Phase | Batch | Status | Q&A Target |
|---|--------------|-------|-------|--------|-----------|
| 1 | `data_model_and_objects/README.md` | 1 | 1 | done | 15+ |
| 2 | `core_language_idioms/README.md` | 1 | 2 | done | 12+ |
| 3 | `iterators_and_generators/README.md` | 1 | 2 | done | 12+ |
| 4 | `decorators_and_closures/README.md` | 1 | 1 | done | 15+ |
| 5 | `context_managers_and_exceptions/README.md` | 1 | 2 | done | 12+ |
| 6 | `collections_and_data_structures/README.md` | 1 | 2 | done | 12+ |
| 7 | `strings_bytes_encoding_and_regex/README.md` | 1 | 2 | done | 10+ |
| 8 | `file_io_and_serialization/README.md` | 1 | 2 | done | 10+ |
| 9 | `cpython_memory_model/README.md` | 2 | 3 | done | 15+ |
| 10 | `the_gil_and_free_threading/README.md` | 2 | 1 | done | 18+ |
| 11 | `metaclasses_and_metaprogramming/README.md` | 2 | 3 | done | 15+ |
| 12 | `the_type_system_and_typing/README.md` | 2 | 1 | done | 15+ |
| 13 | `performance_and_profiling/README.md` | 2 | 3 | done | 12+ |
| 14 | `functional_programming/README.md` | 2 | 3 | done | 10+ |
| 15 | `threading_and_multiprocessing/README.md` | 3 | 4 | done | 15+ |
| 16 | `asyncio_and_event_loop/README.md` | 3 | 1 | done | 18+ |
| 17 | `async_patterns_and_pitfalls/README.md` | 3 | 4 | done | 15+ |
| 18 | `design_patterns_in_python/README.md` | 3 | 4 | done | 12+ |
| 19 | `stdlib_datetime_and_logging/README.md` | 3 | 4 | done | 10+ |
| 20 | `testing_with_pytest/README.md` | 3 | 4 | done | 12+ |
| 21 | `packaging_and_project_tooling/README.md` | 3 | 4 | done | 10+ |
| 22 | `fastapi/fastapi_fundamentals_asgi/README.md` | 4 | 1 | done | 15+ |
| 23 | `fastapi/pydantic_v2_deep_dive/README.md` | 4 | 1 | done | 18+ |
| 24 | `fastapi/routing_and_request_handling/README.md` | 4 | 5 | done | 12+ |
| 25 | `fastapi/dependency_injection_in_fastapi/README.md` | 4 | 1 | done | 18+ |
| 26 | `fastapi/middleware_and_lifecycle/README.md` | 4 | 5 | done | 12+ |
| 27 | `fastapi/configuration_and_settings_management/README.md` | 4 | 5 | done | 10+ |
| 28 | `fastapi/async_database_sqlalchemy/README.md` | 5 | 6 | done | 15+ |
| 29 | `fastapi/authentication_and_security/README.md` | 5 | 6 | done | 18+ |
| 30 | `fastapi/error_handling_and_validation/README.md` | 5 | 6 | done | 12+ |
| 31 | `fastapi/testing_fastapi/README.md` | 5 | 6 | done | 13 |
| 32 | `fastapi/websockets_sse_and_streaming/README.md` | 5 | 6 | done | 12+ |
| 33 | `fastapi/background_jobs_and_task_queues/README.md` | 5 | 6 | done | 12+ |
| 34 | `fastapi/http_clients_and_external_apis/README.md` | 5 | 6 | done | 12+ |
| 35 | `fastapi/message_queues_and_event_driven/README.md` | 5 | 6 | done | 12+ |
| 36 | `fastapi/production_deployment_and_scaling/README.md` | 6 | 7 | done | 12+ |
| 37 | `fastapi/observability_and_monitoring/README.md` | 6 | 7 | done | 12+ |
| 38 | `fastapi/caching_and_performance/README.md` | 6 | 7 | done | 12+ |
| 39 | `fastapi/api_design_and_versioning/README.md` | 6 | 7 | done | 12+ |
| 40 | `fastapi/security_hardening_and_owasp/README.md` | 6 | 7 | done | 12+ |
| CS1 | `fastapi/case_studies/design_rate_limited_api_fastapi.md` | — | 1 | done | — |
| CS2 | `fastapi/case_studies/design_multi_tenant_saas_api.md` | — | 8 | done | — |
| CS3 | `fastapi/case_studies/design_realtime_chat_fastapi.md` | — | 8 | done | — |
| CS4 | `fastapi/case_studies/design_async_task_queue.md` | — | 8 | done | — |
| CS5 | `fastapi/case_studies/design_async_web_scraper.md` | — | 8 | done | — |
| CS6 | `fastapi/case_studies/design_ml_inference_api_fastapi.md` | — | 8 | done | — |
| SF1 | `asyncio_and_event_loop/event_loop_internals.md` | 3 | 8 | done | 15+ |
| SF2 | `asyncio_and_event_loop/structured_concurrency.md` | 3 | 8 | done | 15+ |
| SF3 | `fastapi/pydantic_v2_deep_dive/pydantic_core_and_performance.md` | 4 | 8 | done | 15+ |
| SF4 | `the_type_system_and_typing/protocols_and_structural_typing.md` | 2 | 8 | done | 15+ |
| SF5 | `fastapi/dependency_injection_in_fastapi/yield_dependencies_and_scopes.md` | 4 | 8 | done | 15+ |

### Conventions Reminder (for future batch agents)

```
MODULE TEMPLATE — 14-section canonical clean scheme:
  ## 1. Concept Overview
  ## 2. Intuition     ("> blockquote analogy" + **Mental model** + **Why it matters** + **Key insight**)
  ## 3. Core Principles
  ## 4. Types / Architectures / Strategies
  ## 5. Architecture Diagrams            (ASCII art only — no Mermaid, no image files)
  ## 6. How It Works — Detailed Mechanics   (real Python code, concrete numbers)
  ## 7. Real-World Examples
  ## 8. Tradeoffs                        (comparison tables)
  ## 9. When to Use / When NOT to Use
  ## 10. Common Pitfalls                 (# BROKEN / # FIX pattern, at least 1 required)
  ## 11. Technologies & Tools            (comparison table)
  ## 12. Interview Questions with Answers  (bold Q as "**Q1:**", plain text A; Q&A targets below)
  ## 13. Best Practices
  ## 14. Case Study   (scenario + ASCII diagram + real code + BROKEN/FIX + metrics + Discussion Qs)

QUALITY BAR:
  - 700-1000 lines per module README
  - Q&A minimum: 10+ standard; 15-18 for:
      the_gil_and_free_threading, asyncio_and_event_loop, the_type_system_and_typing,
      pydantic_v2_deep_dive, dependency_injection_in_fastapi, async_database_sqlalchemy,
      authentication_and_security, threading_and_multiprocessing
  - At least 1 BROKEN->FIX block in §10 and at least 1 in §14
  - Python 3.11/3.12 primary; mark new features with "[3.X]" version tags inline
  - Type hints on ALL function signatures (use X | None not Optional[X])
  - Concrete numbers everywhere (no "a few", "some", "significant")
  - ASCII diagrams in fenced code blocks
  - Horizontal rules (---) between every top-level section
  - Em-dash in §6 heading: "## 6. How It Works — Detailed Mechanics"
  - No emojis in any content
  - Cross-link to other sections via relative paths: ../../backend/..., ../../lld/..., etc.

CASE STUDY TEMPLATE — 7-section legacy:
  ## Problem Statement
  ## Architecture Overview      (ASCII diagram required)
  ## Key Design Decisions
  ## Implementation             (detailed Python code with type hints)
  ## Python/FastAPI Components Used
  ## Tradeoffs and Alternatives
  ## Interview Discussion Points

MAINTENANCE RULE when completing a batch:
  1. Flip Status "pending" -> "done" for each completed file in this tracker
  2. Advance the NEXT UP pointer at the top of §8
  3. Update case_studies/README.md if new case studies were added
  4. Update root README.md module counts if the total changed
  5. Update CLAUDE.md Python section module list if needed
```

---

## Getting Started

**Week 1 — Python Internals Foundation** (highest interview signal-to-noise)
- `data_model_and_objects` — covers ~30% of Python interview questions on its own
- `decorators_and_closures` — appears in almost every senior Python interview
- `the_gil_and_free_threading` — key differentiator for principal-level interviews

**Week 2 — Async and Type System** (the hard ones)
- `asyncio_and_event_loop` — deepest async module; study alongside `async_patterns_and_pitfalls`
- `the_type_system_and_typing` — `Protocol`, `TypeVar`, `ParamSpec` tested at FAANG+
- `cpython_memory_model` + `metaclasses_and_metaprogramming` for deep CPython questions

**Week 3 — FastAPI Core** (framework layer, read in order)
- `fastapi_fundamentals_asgi` → `pydantic_v2_deep_dive` → `dependency_injection_in_fastapi`
- `authentication_and_security` — OAuth2/JWT in FastAPI, asked in 80%+ of backend interviews

**Week 4 — Production & Case Studies** (integration)
- `async_database_sqlalchemy` + `testing_fastapi`
- All 6 case studies — they integrate every phase end-to-end

See individual module READMEs for per-module learning objectives and cross-references.
