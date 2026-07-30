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
| `error_handling_and_validation/` | 2 | `HTTPException`, custom handlers, `RequestValidationError`, RFC 9457 Problem Details |
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

Every module page (`<module>/<module>.md`) follows the standard 14-section template (see root `CLAUDE.md`):

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

**15-Q&A floor everywhere** — every module page must carry at least 15 Q&As in
§12, ordered gotchas/traps first, then internals, then edge cases (see root
`CLAUDE.md` Interview Q&A Rules). Deep modules below need 15-18.

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 19 modules = "3-Phase Learning Path") plus
two curated tiers: **Senior** (13 modules) and **Principal** (8). They are different
cuts, not nested depths — senior is the craft (build the endpoint, fix the blocking call
on the event loop), principal is the judgment (which deployment shape at what cost, what
you tell a team *not* to do), so principal is the smaller list and much of it is material
senior never sees. Do not enumerate the members here: membership is declared ONCE per
module, in a `<!-- study-paths -->` block in that module's own page (`<module>.md`) naming the files
each tier takes; listing a tier joins it, omitting the tier opts out, and the module page
(`<module>.md`) must always be listed. Order is never declared — it comes from `STUDY_ORDER.fastapi` in
`game/app.js`, so a tier is an ordered subset by construction. **There is no path array
in `app.js` to edit**: `extract.py` walks the markers and emits the gitignored
`questions/paths.json`, which the game fetches at boot. The tier tables in `README.md`
sit between `<!-- study-path-table <tier> -->` markers and are **generated** — regenerate
with `python3 game/extract.py --write-paths`; a hand-edited or stale block fails
`extract.py --strict` and the Pages deploy. Case studies are tiered the same way from a
block in `case_studies/case_studies.md` (5 senior / 2 principal), driving the Level filter on
the game's Case Studies tab. The README also carries a Knowledge-Question Map and a
3-week Study Plan (prose; no path impact).

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
- Pydantic 2.0+ (2023): `@field_validator`, `.model_dump()`, `pydantic-core` Rust. Faster than v1, but quote a
  multiplier only with a measurement beside it — the speedup is model-shaped (measured ~2.7x on a mixed model,
  ~2.9x on a 3-field flat model, ~13x on one with a dict and a list), so a blanket "5-50x" is not a fact
- SQLAlchemy 2.0 (2023): unified 2.0-style only; `AsyncSession`/`async_engine` stable

Format: `[FastAPI 0.110+]` inline or `Added in FastAPI 0.110`.

## Q&A Minimums (FastAPI-specific)

- Standard modules: 15+ Q&As (the hard floor in root `CLAUDE.md`)
- Deep modules (15–18 required): `pydantic_v2_deep_dive`, `dependency_injection_in_fastapi`, `async_database_sqlalchemy`, `authentication_and_security`

## Adding a New FastAPI Module

1. Create `<module_name>/<module_name>.md` — 14-section clean template
2. All code in Python 3.13/3.14 + FastAPI with type hints (3.10+ syntax: `X | None` not `Optional[X]`)
3. Add version tags for features introduced in specific FastAPI/Pydantic/SQLAlchemy versions
4. Meet the Q&A minimum for the module's depth level (15-Q&A floor; 15-18 for the four deep modules above)
5. Update this file's Module List table
6. Update `README.md` §2 Module Table and §3 Phase Learning Path diagram
7. Add the module dir to `STUDY_ORDER.fastapi` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
8. Write a `<!-- study-paths -->` block at the top of the new module's page (`<module_name>.md`) naming the tiers it
   belongs to (or none, for Full-path-only). Every tier line must list `<module_name>.md`
   itself — the module page is never optional and omitting it is fatal under `--strict`, then run `python3 game/extract.py --write-paths`
   to regenerate `README.md`'s tier tables (never hand-edit them)
9. Update root `README.md` Phase table under the FastAPI section
10. Update root `CLAUDE.md` FastAPI module count/table if present

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
