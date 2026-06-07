# Python Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/python/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §8 — check NEXT UP pointer before starting a new module.

---

## Module List — 40 Modules

| Module Directory | Phase | Key Concepts |
|-----------------|-------|-------------|
| `data_model_and_objects/` | 1 | Dunder methods, `__slots__`, MRO/C3 linearization, operator overloading, hashing/equality contract |
| `core_language_idioms/` | 1 | Mutability vs identity, EAFP vs LBYL, comprehensions, walrus `:=` (3.8), `match`/`case` (3.10) |
| `iterators_and_generators/` | 1 | Iterator protocol, `yield`/`yield from`, lazy pipelines, `itertools`, generator coroutines |
| `decorators_and_closures/` | 1 | Closures, free variables, function/class/parametrized decorators, `functools.wraps/lru_cache/cached_property` |
| `context_managers_and_exceptions/` | 1 | `contextlib`, `ExitStack`, async CMs, `ExceptionGroup`/`except*` (3.11) |
| `collections_and_data_structures/` | 1 | `list`/`dict`/`set` internals + Big-O, `collections`, `heapq`, `bisect` |
| `strings_bytes_encoding_and_regex/` | 1 | `str` vs `bytes`, Unicode, `re` engine, catastrophic backtracking |
| `file_io_and_serialization/` | 1 | `pathlib`, text/binary I/O, `json`/`csv`, `pickle` security |
| `cpython_memory_model/` | 2 | Reference counting, generational GC, `PyObject` header, arenas/pools/blocks |
| `the_gil_and_free_threading/` | 2 | GIL mechanics, GIL release points, PEP 703 free-threading (3.13), PEP 684 sub-interpreters |
| `metaclasses_and_metaprogramming/` | 2 | `type()`, metaclasses, `__init_subclass__`, `__set_name__`, descriptors |
| `the_type_system_and_typing/` | 2 | Type hints, generics, `Protocol`, `TypeVar`/`ParamSpec`, variance, PEP 695 (3.12), mypy |
| `performance_and_profiling/` | 2 | `cProfile`/`dis`, CPython 3.11+ speedups, Cython/mypyc, common slow patterns |
| `functional_programming/` | 2 | `map`/`filter`/`reduce`, `functools`, immutability, currying, comprehension vs generator perf |
| `threading_and_multiprocessing/` | 3 | `threading`, GIL impact, `multiprocessing`, `concurrent.futures`, shared memory |
| `asyncio_and_event_loop/` | 3 | Coroutines, event-loop internals, `gather`/`wait`, `TaskGroup` (3.11), `anyio` |
| `async_patterns_and_pitfalls/` | 3 | Blocking-in-async, `run_in_executor`, async generators, `Semaphore`, backpressure |
| `design_patterns_in_python/` | 3 | Pythonic GoF patterns, anti-patterns — cross-links `lld/` |
| `stdlib_datetime_and_logging/` | 3 | `datetime`/`zoneinfo`, structured logging, `argparse`, `subprocess` |
| `testing_with_pytest/` | 3 | pytest, fixtures, `parametrize`, `monkeypatch`, `hypothesis`, `pytest-asyncio` |
| `packaging_and_project_tooling/` | 3 | `pyproject.toml`, `uv`/poetry/pip, `ruff`/mypy, wheels, dependency resolution |
| `fastapi_fundamentals_asgi/` | 4 | ASGI vs WSGI, Starlette, Uvicorn, `lifespan`, ASGI 3 scope/receive/send, auto OpenAPI |
| `pydantic_v2_deep_dive/` | 4 | Validation, serialization, `@field_validator`, `pydantic-core` Rust, v1→v2 migration |
| `routing_and_request_handling/` | 4 | Path operations, `APIRouter`, response models, status codes, content negotiation |
| `dependency_injection_in_fastapi/` | 4 | `Depends`, sub-dependencies, `yield` deps, caching/scopes, `dependency_overrides` |
| `middleware_and_lifecycle/` | 4 | Middleware stack, `BackgroundTasks`, CORS/GZip, custom middleware, exception handler ordering |
| `configuration_and_settings_management/` | 4 | `pydantic-settings`, 12-factor config, env vars/secrets, layered settings |
| `async_database_sqlalchemy/` | 5 | SQLAlchemy 2.0 async, `AsyncSession`, async engine, Alembic, SQLModel, N+1 |
| `authentication_and_security/` | 5 | OAuth2 password flow, JWT, scopes, passlib/bcrypt/argon2, OIDC, CSRF/CORS |
| `error_handling_and_validation/` | 5 | `HTTPException`, custom handlers, `RequestValidationError`, RFC 7807 Problem Details |
| `testing_fastapi/` | 5 | `TestClient`, `httpx.AsyncClient`, `pytest-asyncio`, `dependency_overrides`, rollback |
| `websockets_sse_and_streaming/` | 5 | WebSockets, SSE, `StreamingResponse`, Redis pub/sub fan-out, backpressure |
| `background_jobs_and_task_queues/` | 5 | `BackgroundTasks` vs Celery vs ARQ vs Dramatiq, idempotency, retries, DLQ |
| `http_clients_and_external_apis/` | 5 | `httpx`/`aiohttp`, connection pooling, retries/backoff, circuit breakers |
| `message_queues_and_event_driven/` | 5 | `aiokafka`/`aio-pika`, outbox pattern, consumer groups, idempotent consumers |
| `production_deployment_and_scaling/` | 6 | Gunicorn+Uvicorn workers, K8s, graceful shutdown, ASGI scaling, blue-green |
| `observability_and_monitoring/` | 6 | Structured logging, OpenTelemetry, Prometheus metrics, health/readiness probes |
| `caching_and_performance/` | 6 | Redis caching, response/in-process caching, connection pooling, async pitfalls |
| `api_design_and_versioning/` | 6 | REST best practices, versioning, cursor pagination, rate limiting, idempotency keys |
| `security_hardening_and_owasp/` | 6 | OWASP API Top 10 in FastAPI, injection/SSRF, secrets handling, pip-audit |

---

## Sub-Files (5 total — 14-section template, 15+ Q&As each)

| File | Parent Module |
|------|--------------|
| `asyncio_and_event_loop/event_loop_internals.md` | asyncio_and_event_loop/ |
| `asyncio_and_event_loop/structured_concurrency.md` | asyncio_and_event_loop/ |
| `pydantic_v2_deep_dive/pydantic_core_and_performance.md` | pydantic_v2_deep_dive/ |
| `the_type_system_and_typing/protocols_and_structural_typing.md` | the_type_system_and_typing/ |
| `dependency_injection_in_fastapi/yield_dependencies_and_scopes.md` | dependency_injection_in_fastapi/ |

---

## Case Studies — 6 Total

`case_studies/` — all use the 7-section legacy template (Problem Statement → Architecture Overview → Key Design Decisions → Implementation → Python/FastAPI Components Used → Tradeoffs and Alternatives → Interview Discussion Points).

design_rate_limited_api_fastapi, design_multi_tenant_saas_api, design_realtime_chat_fastapi, design_async_task_queue, design_async_web_scraper, design_ml_inference_api_fastapi

---

## Python Version Tags

When covering a feature, include the version it was introduced:
- Python 3.8: walrus operator `:=`, `TypedDict`, `Literal`
- Python 3.9: `list[int]` built-in generics, `dict | dict` merge operators
- Python 3.10: `match`/`case`, `X | Y` union type syntax, `ParamSpec`
- Python 3.11: `TaskGroup`, `ExceptionGroup`/`except*`, 10–60% performance gains, `tomllib`
- Python 3.12: `type` statement (PEP 695), `@override`, `f-string` improvements
- Python 3.13: free-threading (PEP 703, experimental), JIT compilation (experimental)

Format: `[3.11]` inline or `Added in Python 3.11`.

## Q&A Minimums (Python-specific)

- Standard modules: 10+ Q&As
- Deep modules (15–18 required): `the_gil_and_free_threading`, `asyncio_and_event_loop`, `the_type_system_and_typing`, `pydantic_v2_deep_dive`, `dependency_injection_in_fastapi`, `async_database_sqlalchemy`, `authentication_and_security`

## Adding a New Python Module

1. Create `<module_name>/README.md` — 14-section clean template
2. All code in Python 3.11/3.12 with type hints (3.10+ syntax: `X | None` not `Optional[X]`)
3. Add version tags for features introduced in specific Python versions
4. Meet the Q&A minimum for the module's depth level
5. Flip the module's Status from `pending` → `done` in `README.md` §8 tracker; advance "NEXT UP" pointer
6. Update root `README.md` Phase table under the Python section
7. Update root `CLAUDE.md` Python module table
