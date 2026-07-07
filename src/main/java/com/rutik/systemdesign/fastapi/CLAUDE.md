# FastAPI Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/fastapi/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

This section covers FastAPI itself — the ASGI protocol, Pydantic v2, dependency injection,
and the full production stack (async database access, auth, testing, deployment,
observability, caching, API design, security hardening). It was split out of `python/`
(previously "Python + FastAPI") on 2026-07-07 so each section has its own module count,
learning path, and index. These modules assume familiarity with the pure-Python
fundamentals in [`../python/`](../python/README.md) — particularly `asyncio_and_event_loop`
(async generators underpin `yield` dependencies) and `the_type_system_and_typing`
(`Protocol`/`TypeVar`/`Annotated` underpin Pydantic v2 generics).

---

## Module List — 19 Modules

| Module Directory | Phase | Key Concepts |
|-----------------|-------|-------------|
| `fastapi_fundamentals_asgi/` | 1 | ASGI vs WSGI, Starlette, Uvicorn, `lifespan`, ASGI 3 scope/receive/send, auto OpenAPI |
| `pydantic_v2_deep_dive/` | 1 | Validation, serialization, `@field_validator`, `pydantic-core` Rust, v1→v2 migration |
| `routing_and_request_handling/` | 1 | Path operations, `APIRouter`, response models, status codes, content negotiation |
| `dependency_injection_in_fastapi/` | 1 | `Depends`, sub-dependencies, `yield` deps, caching/scopes, `dependency_overrides` |
| `middleware_and_lifecycle/` | 1 | Middleware stack, `BackgroundTasks`, CORS/GZip, custom middleware, exception handler ordering |
| `configuration_and_settings_management/` | 1 | `pydantic-settings`, 12-factor config, env vars/secrets, layered settings |
| `async_database_sqlalchemy/` | 2 | SQLAlchemy 2.0 async, `AsyncSession`, async engine, Alembic, SQLModel, N+1 |
| `authentication_and_security/` | 2 | OAuth2 password flow, JWT, scopes, passlib/bcrypt/argon2, OIDC, CSRF/CORS |
| `error_handling_and_validation/` | 2 | `HTTPException`, custom handlers, `RequestValidationError`, RFC 7807 Problem Details |
| `testing_fastapi/` | 2 | `TestClient`, `httpx.AsyncClient`, `pytest-asyncio`, `dependency_overrides`, rollback |
| `websockets_sse_and_streaming/` | 2 | WebSockets, SSE, `StreamingResponse`, Redis pub/sub fan-out, backpressure |
| `background_jobs_and_task_queues/` | 2 | `BackgroundTasks` vs Celery vs ARQ vs Dramatiq, idempotency, retries, DLQ |
| `http_clients_and_external_apis/` | 2 | `httpx`/`aiohttp`, connection pooling, retries/backoff, circuit breakers |
| `message_queues_and_event_driven/` | 2 | `aiokafka`/`aio-pika`, outbox pattern, consumer groups, idempotent consumers |
| `production_deployment_and_scaling/` | 3 | Gunicorn+Uvicorn workers, K8s, graceful shutdown, ASGI scaling, blue-green |
| `observability_and_monitoring/` | 3 | Structured logging, OpenTelemetry, Prometheus metrics, health/readiness probes |
| `caching_and_performance/` | 3 | Redis caching, response/in-process caching, connection pooling, async pitfalls |
| `api_design_and_versioning/` | 3 | REST best practices, versioning, cursor pagination, rate limiting, idempotency keys |
| `security_hardening_and_owasp/` | 3 | OWASP API Top 10 in FastAPI, injection/SSRF, secrets handling, pip-audit |

---

## Sub-Files (2 total — 14-section template, 15+ Q&As each)

| File | Parent Module |
|------|--------------|
| `pydantic_v2_deep_dive/pydantic_core_and_performance.md` | pydantic_v2_deep_dive/ |
| `dependency_injection_in_fastapi/yield_dependencies_and_scopes.md` | dependency_injection_in_fastapi/ |

---

## Module Template

Every module README follows the standard 14-section template (see root `CLAUDE.md`):

```
## 1. Concept Overview
## 2. Intuition
## 3. Core Principles
## 4. Types / Architectures / Strategies
## 5. Architecture Diagrams
## 6. How It Works — Detailed Mechanics
## 7. Real-World Examples
## 8. Tradeoffs
## 9. When to Use / When NOT to Use
## 10. Common Pitfalls
## 11. Technologies & Tools
## 12. Interview Questions with Answers
## 13. Best Practices
## 14. Case Study
```

**15-Q&A floor everywhere** — every module README must carry at least 15 Q&As in
§12, ordered gotchas/traps first, then internals, then edge cases (see root
`CLAUDE.md` Interview Q&A Rules). Deep modules below need 15-18.

---

## Learning Paths (Full + Interview-Specific)

`README.md` documents two routes: the **Full Path** (all 19 modules = "3-Phase Learning
Path") and a curated **Interview-Specific Path** (9 modules). The interview subset is a
**dual-source list** — it lives in both `README.md` ("## Learning Paths") and
`game/app.js` (`STUDY_PATHS.fastapi.interview`, which drives the game's Study
Full/Interview toggle). **Change one, change the other** — same modules, same order:
`fastapi_fundamentals_asgi`, `pydantic_v2_deep_dive`, `dependency_injection_in_fastapi`,
`async_database_sqlalchemy`, `authentication_and_security`, `error_handling_and_validation`,
`production_deployment_and_scaling`, `observability_and_monitoring`, `caching_and_performance`.
Non-Q&A narrative only; no `extract.py` re-run needed. The README also carries a
Knowledge-Question Map and a 3-week Study Plan (interview-readiness prose; no toggle impact).

---

## Case Studies — 6 Total

`case_studies/` — all use the 7-section legacy template (Problem Statement → Architecture
Overview → Key Design Decisions → Implementation → Python/FastAPI Components Used →
Tradeoffs and Alternatives → Interview Discussion Points).

design_rate_limited_api_fastapi, design_multi_tenant_saas_api, design_realtime_chat_fastapi, design_async_task_queue, design_async_web_scraper, design_ml_inference_api_fastapi

---

## FastAPI / Pydantic / SQLAlchemy Version Tags

When covering a feature, include the version it was introduced:
- FastAPI 0.93+: first `lifespan` context manager support
- FastAPI 0.100+: official Pydantic v2 support
- FastAPI 0.110+: `lifespan` replaces `on_startup`/`on_shutdown` as the recommended pattern
- Pydantic 2.0+ (2023): `@field_validator`, `.model_dump()`, `pydantic-core` Rust — 5–50x faster than v1
- SQLAlchemy 2.0 (2023): unified 2.0-style only; `AsyncSession`/`async_engine` stable

Format: `[FastAPI 0.110+]` inline or `Added in FastAPI 0.110`.

## Q&A Minimums (FastAPI-specific)

- Standard modules: 10+ Q&As (15+ hard floor per root `CLAUDE.md`)
- Deep modules (15–18 required): `pydantic_v2_deep_dive`, `dependency_injection_in_fastapi`, `async_database_sqlalchemy`, `authentication_and_security`

## Adding a New FastAPI Module

1. Create `<module_name>/README.md` — 14-section clean template
2. All code in Python 3.11/3.12 + FastAPI with type hints (3.10+ syntax: `X | None` not `Optional[X]`)
3. Add version tags for features introduced in specific FastAPI/Pydantic/SQLAlchemy versions
4. Meet the Q&A minimum for the module's depth level (15-Q&A floor; 15-18 for the four deep modules above)
5. Update this file's Module List table
6. Update `README.md` §2 Module Table and §3 Phase Learning Path diagram; update the Learning Paths section if the module joins the Interview-Specific Path (and keep `STUDY_PATHS.fastapi` in `game/app.js` in sync — see above)
7. Update root `README.md` Phase table under the FastAPI section
8. Update root `CLAUDE.md` FastAPI module count/table if present

---

## Visual Intuition Diagrams

Section 5 (Architecture Diagrams) and any hard-to-picture concept should use an
**ASCII visual intuition diagram** that makes an abstract relationship visible
(constraint grid, before/after-with-delta, stacked flow, routing fan-out, bar
chart, or curve/sketch). Generate and validate them with the
`/visual-intuition-diagrams` skill. The full archetype catalog, conventions
(ASCII only, no tabs, no emojis, widest line <= 100 cols, caption every diagram),
and the `diagram_tools.py` validator live in root `CLAUDE.md` -> "Visual Intuition
Diagrams".
