# FastAPI Audit — 2026-08-25

## 0. Snapshot

This is the most **structurally healthy** section I have profiled. All 19 module pages plus
both deep-dive sub-files carry every one of the 14 template headings in exact numbering; every
one clears the 15-Q&A floor (range 15–19); every one has at least one real `# BROKEN` → `# FIX`
code pair (most have 4–8); every one has 4–8 Mermaid fences; and the concrete-number density is
genuinely high and mostly *derived* rather than asserted (Little's Law pool arithmetic,
`pool_size=10 + max_overflow=20 → 30 conns × 9 workers vs Postgres max_connections=100`,
bcrypt cost 12 ≈ 200–250 ms, anyio's 40-token thread pool, `preStop 5s + drain 30s →
terminationGracePeriodSeconds 40`). The 2026-08-04 modernization pass clearly landed here:
`pwdlib` has already replaced `passlib`, `python-jose` is correctly demoted behind `PyJWT`,
`redis.asyncio` has replaced `aioredis`, `uvicorn-worker` is correctly the standalone package,
and the deprecated `tiangolo/uvicorn-gunicorn-fastapi` image is called out as deprecated.

**The single biggest weakness is not depth — it is that FastAPI moved underneath the section
in the last nine months and the section has not.** FastAPI shipped **native SSE in 0.135.0**
(`from fastapi.sse import EventSourceResponse, ServerSentEvent`, with automatic 15-second
keep-alive comments, `Cache-Control: no-cache`, `X-Accel-Buffering: no`, and `Last-Event-ID`
resume) and **native JSON Lines streaming in 0.134.0** (`AsyncIterable[Model]` →
`application/jsonl`), whose own documentation names "an AI LLM service" as the motivating use
case. The section still teaches third-party `sse-starlette` as "(recommended)" and has zero
mentions of JSONL. Behind that sits a second, larger gap: **serving LLM/AI workloads is now the
dominant real-world FastAPI job and the section has no module that owns it** — no admission
control for GPU-backed work, no cancellation propagation to an upstream provider, no
token/cost-based quotas, no long-request timeout budget, no MCP. Third, all **6 case studies
use a 7-section legacy template** rather than the mandated 11-section principal template — the
same finding the parallel `database` audit reported, and here it is *documented in the section's
own CLAUDE.md as if it were policy*.

| Metric | Value |
|---|---|
| Modules | 19 |
| Deep-dive sub-files | 2 |
| Case studies | 6 (+1 index) |
| Total lines (all `*/*.md`) | 31,580 |
| Module + sub-file lines | 27,052 |
| Case-study lines | 4,418 |
| Avg lines / module page | 1,288 (range 1,023–1,510) |
| Total Q&As in modules + sub-files | 342 (avg 16.3; min 15, max 19) |
| Case-study Q&As (bank-excluded by design) | 59 |
| Mermaid fences | 121 in modules/sub-files, 19 in case studies |

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Pri | Est. lines |
|---|---|---|---|---|
| `fastapi/serving_llm_and_ai_workloads/serving_llm_and_ai_workloads.md` | module | FastAPI's own docs added a Server-Sent Events tutorial and a JSON Lines tutorial in 0.134/0.135, both motivated by "an AI LLM service"; the section has no module owning the *economics* of long-lived GPU-backed requests. <https://fastapi.tiangolo.com/tutorial/stream-json-lines/>, <https://fastapi.tiangolo.com/tutorial/server-sent-events/> | **P0** | 1,300 |
| `fastapi/production_deployment_and_scaling/asgi_runtimes_and_free_threading.md` | sub-file | Python 3.14 promoted free-threading to a *supported* configuration and FastAPI merged explicit `3.14t` CI support (PR #15149, 2026-04-16); Granian is a real third runtime with native admission control (503 on overload) that the section never names; and SQLAlchemy 2.1 stopped auto-installing greenlet *because greenlet is not free-threading compatible* — which means async SQLAlchemy does not work on `3.14t`. <https://www.sqlalchemy.org/docs/21/changelog/migration_21.html>, <https://www.deployhq.com/blog/python-application-servers-wsgi-vs-asgi-guide> | **P0** | 900 |
| `fastapi/python_api_framework_landscape/python_api_framework_landscape.md` | module | The section is framework-parochial: Litestar appears only as two comparison-table *rows*, Django Ninja and plain-Starlette never. 2026 comparisons treat Litestar (msgspec serialization, built-in DI) and Django Ninja (Pydantic v2 ergonomics inside Django) as the real alternatives a principal is asked to justify against. <https://github.com/tanrax/python-api-frameworks-benchmark> | P1 | 1,100 |
| `fastapi/case_studies/design_llm_gateway_fastapi.md` | case study | AI gateways — token-aware rate limiting, cost quotas, provider failover, semantic cache — are a named 2026 category distinct from an API gateway ("AI gateways understand tokens, model equivalence, and prompt semantics"). <https://fast.io/resources/best-api-gateways-ai-agents/> | P1 | 1,000 |
| `fastapi/serving_llm_and_ai_workloads/mcp_servers_from_fastapi.md` | sub-file | Streamable HTTP replaced SSE as MCP's recommended plain-HTTP transport and is "the 2026 production standard"; FastMCP is at 3.x and reportedly powers ~70% of MCP servers. The section has **zero** mentions of MCP. <https://gofastmcp.com/getting-started/welcome> | P1 | 700 |
| `fastapi/api_design_and_versioning/openapi_31_and_client_generation.md` | sub-file | The module has **zero** occurrences of "3.1" or "3.0" despite FastAPI emitting OpenAPI 3.1 since 0.99, and never names Cadwyn (v6.2.0, "production-ready … Stripe-like API versioning in FastAPI"), the one FastAPI-specific versioning library. <https://github.com/zmievsa/cadwyn> | P1 | 750 |
| `fastapi/case_studies/design_file_upload_and_media_api.md` | case study | `UploadFile` appears in exactly one module and multipart limits/`max_part_size`, presigned-S3 offload, and resumable upload are absent section-wide. Starlette added `max_part_size` to `Request.form()` for exactly this DoS surface. <https://github.com/encode/starlette/blob/master/docs/release-notes.md> | P2 | 900 |

### P0 detail

**`serving_llm_and_ai_workloads/`** — the module that owns *long, expensive, cancellable*
requests, as distinct from `websockets_sse_and_streaming/` which owns the wire protocol.
§1 what makes an LLM request unlike an HTTP request (30–300 s, unbounded body, one GPU slot,
cost per token). §2 analogy: a restaurant with four ovens, not four waiters — admission control,
not concurrency. §3 principles: admit-or-shed beats queue-forever; cancellation must propagate
to the *provider*, not just the socket; cost is the real rate limit. §4 shapes: in-process model
(`lifespan`-loaded) vs sidecar (vLLM/Triton/Ollama over HTTP) vs hosted provider; sync-batch vs
stream. §5 diagrams: admission-control state machine; a token-timeline showing TTFT vs total.
§6 mechanics — the executable core: `fastapi.sse` streaming from an upstream async iterator; an
`anyio.CapacityLimiter` (not a bare `Semaphore`) sized to GPU slots with `asyncio.timeout()` as
the shed path returning 503 + `Retry-After`; propagating `CancelledError` through the generator
into the provider client's `aclose()`; a per-request token/cost accumulator that survives a
disconnect so you still bill/log; `Last-Event-ID` resume for a dropped stream; JSONL for
machine consumers. §7 real: OpenAI/Anthropic SSE wire shapes, `[DONE]` sentinel via
`ServerSentEvent(raw_data="[DONE]")`. §8 tradeoffs: SSE vs WebSocket vs JSONL vs poll-a-job-id;
in-process vs sidecar. §9 when not to: any request over ~5 min belongs in a queue with a job id.
§10 pitfalls: the streaming endpoint that hangs forever because nothing timed out the *idle gap
between tokens*; the disconnect that leaves the upstream generation billing; `StreamingResponse`
inside `BackgroundTasks`; a middleware that buffers the whole body and destroys TTFB. §11
tools. §12 15+ Q&As. §13 best practices. §14 case study pointing at the new LLM-gateway one.

**`asgi_runtimes_and_free_threading.md`** — sub-file under `production_deployment_and_scaling`.
§1–4: the runtime matrix (uvicorn + uvloop/httptools, uvicorn's own `--workers` manager,
gunicorn + `uvicorn_worker.UvicornWorker`, Granian's RSGI/ASGI Rust loop, Hypercorn for HTTP/2)
and which one each deployment shape actually wants. §5: a diagram of `SO_REUSEPORT` socket
sharding vs a pre-fork parent socket. §6: `--limit-concurrency` and `--limit-max-requests` as
the only in-process admission control uvicorn has, contrasted with Granian rejecting overload
with an immediate 503; measured numbers from the public benchmarks with the caveat that the gap
is CPU-bound-only and near-zero for I/O-bound. §7–10: **the free-threading section is the
payload** — `3.14t` is supported-not-default; CPU-bound endpoints gain roughly 8x with no code
change while I/O-bound gain nothing; single-thread overhead is now 5–10%; any non-updated C
extension silently re-enables the GIL; and **greenlet is not free-threading compatible, so
SQLAlchemy's async ORM does not run on `3.14t` at all** — the sharpest deployable fact in the
whole topic. Cross-link `python/the_gil_and_free_threading`, do not re-teach it. §11–14 as normal.
Name it exactly this — **not** `*_deep_dive` or `*_internals`, or it becomes FATAL under
`--strict` via the `PRODUCT_DEEP_DIVES`/`CONCEPT_DEEP_DIVES` pin guard in `build_tech.py`.

---

## 2. Stale — verified CORRECTIONS

| File | Line / heading | What it says | True in Aug 2026 | Source |
|---|---|---|---|---|
| `testing_fastapi/testing_fastapi.md` | L59 (prose) and L917 (live `async_client` fixture) | `httpx.AsyncClient(app=app, base_url="http://test")` — presented as working current code, not a broken→fix pair | The `app=` argument **was removed in httpx 0.28.0 (2024-11-28)**; latest httpx is **0.28.1**. This code now raises `TypeError`. Correct form: `AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test")`. `ASGITransport` appears **zero** times in the section. | <https://github.com/encode/httpx/blob/master/CHANGELOG.md>, <https://pypi.org/project/httpx/> |
| `websockets_sse_and_streaming/websockets_sse_and_streaming.md` | §6.4 heading "SSE with sse-starlette (recommended)" L350–369; tools table L913; Q9 L1002–1010; Best Practice #7 L1110 | third-party `sse-starlette`'s `EventSourceResponse` is the recommended SSE path; framing is "removes manual boilerplate vs raw StreamingResponse" | FastAPI ships **native SSE since 0.135.0**: `from fastapi.sse import EventSourceResponse, ServerSentEvent`, declared as `response_class=EventSourceResponse` on a `yield`ing path operation. It handles keep-alive comment pings every 15 s, `Cache-Control: no-cache`, `X-Accel-Buffering: no`, and `Last-Event-ID` resume automatically. The whole §6.4 framing is now an argument for a dependency FastAPI made unnecessary. | <https://fastapi.tiangolo.com/tutorial/server-sent-events/>, <https://fastapi.tiangolo.com/reference/sse/>, <https://github.com/fastapi/fastapi/pull/15030> |
| section-wide | absent | JSON Lines streaming is never mentioned (zero hits for `jsonl` / "json lines") | FastAPI **0.134.0** added it: return `AsyncIterable[Model]` from a path operation and FastAPI emits `application/jsonl`. Docs name "an **AI LLM** service, … logs or telemetry" as the use case. | <https://fastapi.tiangolo.com/tutorial/stream-json-lines/> |
| `README.md` L220 and L247; `fastapi_fundamentals_asgi/…md` L771 | Knowledge-Question Map row; §4 Version Notes row; §9 prose | "why is validation **5-50x** faster?" / "`pydantic-core` Rust — **5–50x** faster" / "5–50x faster than v1, per Pydantic's own published comparison" | **The section's own `CLAUDE.md` L119 explicitly forbids this exact string**: "a blanket '5-50x' is not a fact". Every other file already complies with measured, model-shaped figures (~2.7x flat, ~2.9x 3-field, ~13x dict+list, measured CPython 3.13.11 / pydantic 1.10.26 vs 2.13.4). Three live violations, one of them a *question the reader is taught to answer wrongly*. | internal contradiction; <https://docs.pydantic.dev/latest/changelog/> |
| `README.md` §4 Version Notes | "Starlette \| **0.27+** \| ASGI 3 protocol standardized" | Starlette is at **1.6.0**, `requires_python >=3.10`. Starlette left 0.x entirely. `middleware_and_lifecycle.md` compounds it with two mutually inconsistent floors in one file — "Starlette version: 0.20+" (L34) vs "[Starlette 0.12+]" (L118) — and `testing_fastapi.md` L606 says "Starlette 0.20+". Only `fastapi_fundamentals_asgi.md` L100 ("Starlette 1.3") is in the right era. | <https://pypi.org/project/starlette/> |
| `http_clients_and_external_apis/…md` L774 | §8.1 comparison row: "\| HTTPX 1.0 stability \| Stable \| Stable \|" | httpx has **never released 1.0**; the current release is **0.28.1** (2024-12-06) and there has been no release since. The row label asserts a version that does not exist. | <https://pypi.org/project/httpx/>, <https://github.com/encode/httpx/blob/master/CHANGELOG.md> |
| `README.md` §4 Version Notes | "FastAPI \| **0.95+** \| First `lifespan` context manager support" | The section's own `CLAUDE.md` L114 says **0.93+**, and FastAPI's release notes agree that `lifespan` landed in 0.93.0. One of the two is wrong and they are 2 minor versions apart. Also stale in the same table: "httpx \| 0.24+" (→ 0.28+, and the ASGITransport change is the reason to bump it) and §1's "executable-shaped Python **3.11/3.12** code blocks" against `CLAUDE.md`'s "Python 3.13/3.14" and 17 in-body "Python 3.13" claims. | <https://fastapi.tiangolo.com/release-notes/> |
| `routing_and_request_handling/…md` Q4, L963–970 | Same answer asserts the sync-route pool default is `min(32, os.cpu_count() + 4)` **and** "Uvicorn's default is 40 worker threads" | These are two different thread pools. `min(32, cpu+4)` is `concurrent.futures.ThreadPoolExecutor`'s default and is **not** the path a `def` handler takes; Starlette/FastAPI dispatch via `anyio.to_thread.run_sync`, whose default `CapacityLimiter` is **40 tokens**, process-wide and CPU-independent. §5.4 of the same file and `fastapi_fundamentals_asgi.md` §6.9 both state the 40-token version correctly, so this is a self-contradiction inside one Q&A — and it is the single highest-frequency FastAPI interview question. | <https://fastapi.tiangolo.com/async/>; internal contradiction |
| `async_database_sqlalchemy/…md` L274, L792, L816 | Recommends "put PgBouncer in transaction mode between the app and the server and let it multiplex" with no caveat | asyncpg auto-prepares statements by protocol default, which **breaks under PgBouncer transaction mode**. Setting `statement_cache_size=0` alone is *not sufficient* with SQLAlchemy — its own `prepared_statement_cache_size` must also be zeroed, and the issue is still open as of 2026. Zero hits for `statement_cache` or "prepared statement" in the file. The module recommends the exact configuration that fails. | <https://github.com/sqlalchemy/sqlalchemy/discussions/10246>, <https://github.com/sqlalchemy/sqlalchemy/issues/6467> |
| `dependency_injection_in_fastapi/yield_dependencies_and_scopes.md` | L25 | `Cross-reference: [Dependency Injection in FastAPI](../README.md)` | Links a bare section README from a content page. Root `CLAUDE.md` requires linking the module **page**; since the 2026-07-30 rename there is no folder-README fallback below a section root, so this points a reader at the wrong document. Should be `[…](dependency_injection_in_fastapi.md)`. | internal (root `CLAUDE.md` → Formatting Rules) |
| `background_jobs_and_task_queues/…md` §4, §8 | Compares `BackgroundTasks` vs Celery vs ARQ vs Dramatiq | **TaskIQ is absent** (zero hits). It is the async-native distributed task queue most directly comparable to ARQ and the one a 2026 FastAPI candidate is likeliest to have used. Not wrong — incomplete in exactly the place the module claims completeness. | <https://taskiq-python.github.io/> |
| `observability_and_monitoring/…md` | §6.2 structlog pipeline; §5.3 `CorrelationIdMiddleware` | Correlates logs with a hand-rolled `uuid4()` correlation ID in a `contextvar`, while separately instrumenting with `FastAPIInstrumentor`. The two never meet — OTel's own `trace_id`/`span_id` are never injected into the structlog processor chain, so a log line cannot be pivoted to its trace. Also zero mentions of `OTEL_SEMCONV_STABILITY_OPT_IN`, the opt-in that governs whether the instrumentation emits `http.method` or the stable `http.request.method` — a live breaking change for anyone's dashboards. | <https://opentelemetry.io/docs/specs/semconv/non-normative/http-migration/> |

**Verified as NOT stale** (checked, no action): `security_hardening_and_owasp` cites the OWASP
API Security Top 10 **2023** edition, which is still the current edition — there is no 2025/2026
API edition (<https://owasp.org/API-Security/>). `pwdlib` over `passlib`, `PyJWT` over
`python-jose`, `redis.asyncio` over `aioredis`, `uvicorn-worker` as a standalone package, and
the deprecation of `tiangolo/uvicorn-gunicorn-fastapi` are all correct as written. ASGI is still
at spec version 3.0 (docs rebuilt 2026-07-14), so no ASGI-spec correction exists.

---

## 3. Reordering — SEQUENCE and TIER changes

| Module | Current | Proposed | Why |
|---|---|---|---|
| `testing_fastapi` | pos 12, senior | **pos 7** (first of Phase 2), senior | `dependency_overrides` is taught at pos 4 and its only real purpose is testing; every Phase-2 module from pos 7 on ships code the reader cannot exercise until they can run an async test. It is currently 5 modules downstream of where it is first needed. |
| `websockets_sse_and_streaming` | pos 10, **Full-path only (no tier)** | pos 11, **senior** | Streaming is now the default shape of a FastAPI endpoint, not an exotic one — FastAPI put SSE and JSONL in core in 0.134/0.135. Leaving it out of both tiers means the Senior path teaches no streaming at all. |
| `serving_llm_and_ai_workloads` (new) | — | **pos 12**, senior + principal | Directly after the streaming protocol module: protocol first, then the economics of using it. |
| `api_design_and_versioning` | pos 18, **principal only** | pos 19, **senior + principal** | Cursor pagination, idempotency keys and 429 semantics are craft, probed in every senior loop; the versioning-strategy half stays the principal draw. This is the clearest tier misfile in the section. |
| `production_deployment_and_scaling` sub-file `asgi_runtimes_and_free_threading.md` (new) | — | **principal** only | Runtime selection and free-threading readiness are judgment calls with a migration cost, not day-to-day craft. A sub-file may not join a tier its module page skips — the module page is already senior+principal, so this is legal. |
| `python_api_framework_landscape` (new) | — | **pos 21, principal** only | "Why FastAPI and not Litestar/Django Ninja" is a justify-the-choice question, not a build-it question. |
| `message_queues_and_event_driven` | pos 14, Full only | pos 15, **stays Full only** | Confirming deliberately: four `backend/` modules already own this material (see §5). Do not promote it. |

**Proposed `STUDY_ORDER["fastapi"]`** (21 modules; the two adds and one move are the only changes):

```js
  fastapi: [
    "fastapi/fastapi_fundamentals_asgi","fastapi/pydantic_v2_deep_dive","fastapi/routing_and_request_handling","fastapi/dependency_injection_in_fastapi","fastapi/middleware_and_lifecycle","fastapi/configuration_and_settings_management",
    "fastapi/testing_fastapi","fastapi/async_database_sqlalchemy","fastapi/authentication_and_security","fastapi/error_handling_and_validation","fastapi/websockets_sse_and_streaming","fastapi/serving_llm_and_ai_workloads","fastapi/background_jobs_and_task_queues","fastapi/http_clients_and_external_apis","fastapi/message_queues_and_event_driven",
    "fastapi/production_deployment_and_scaling","fastapi/observability_and_monitoring","fastapi/caching_and_performance","fastapi/api_design_and_versioning","fastapi/security_hardening_and_owasp","fastapi/python_api_framework_landscape",
  ],
```

Every one of these changes also requires an edit to the `<!-- study-paths -->` block in
`fastapi/README.md` (tiers), followed by `python3 game/extract.py --write-paths` to regenerate
the two `<!-- study-path-table -->` blocks, then `--strict` to confirm. The Phase-1/2/3 Mermaid
diagram in README §3 and the §2 module table both need the same moves applied by hand.

---

## 4. Depth — THIN modules and BLOAT

**No module in this section is thin by the repo's own bar.** The floor cases are all comfortably
over it, and I am recording that explicitly so nobody re-opens it:

| Module | Lines | Q&As | Diagrams | Verdict |
|---|---|---|---|---|
| `configuration_and_settings_management` | 1,023 | 16 | 6 | passes; add SIGHUP/hot-reload demo (mentioned, never shown) |
| `message_queues_and_event_driven` | 1,050 | 16 | 6 | passes on form; see overlap below |
| `testing_fastapi` | 1,056 | 16 | 5 | passes; but carries the httpx defect and has no BlockBuster |
| `authentication_and_security` | 1,160 | 19 | 8 | strongest per-line file in the section |
| `fastapi_fundamentals_asgi` | 1,510 | 16 | 6 | longest; also the only "5-50x" offender |

Where depth is actually missing, it is a **topic** gap inside a healthy module, not a thin module:

| Module | Missing | Fix | Est. |
|---|---|---|---|
| `testing_fastapi` | `httpx.ASGITransport` (zero hits); BlockBuster (zero hits) — FastAPI's own test suite adopted it to catch blocking calls in coroutines (PR #13290); testing an SSE/streaming endpoint at all | Rewrite the `async_client` fixture; add a §6 subsection on BlockBuster in the pytest session; add a streaming-endpoint test that asserts on chunk boundaries | 3 h |
| `websockets_sse_and_streaming` | native `fastapi.sse`; JSONL; `Last-Event-ID` resume | Retitle §6.4 to native SSE with `sse-starlette` demoted to a one-line "pre-0.135 alternative"; add §6.6 JSONL; add the `Last-Event-ID` resume pattern; update Q9, BP #7, tools table | 4 h |
| `api_design_and_versioning` | OpenAPI 3.1 vs 3.0 (zero hits); Cadwyn (zero hits) | New sub-file (§1 additions table) covers both | see §1 |
| `async_database_sqlalchemy` | the asyncpg/PgBouncer prepared-statement trap; SQLAlchemy 2.1's `sqlalchemy[asyncio]` greenlet extra | Add a §10 pitfall with the broken config → both cache settings zeroed; add a 2.1 version-note row | 2 h |
| `observability_and_monitoring` | OTel `trace_id` → structlog binding; `OTEL_SEMCONV_STABILITY_OPT_IN` | Add the processor that pulls `trace.get_current_span().get_span_context()` into the log event; add a semconv-migration note | 2 h |
| `production_deployment_and_scaling` | Granian, `SO_REUSEPORT`, uvicorn's own `--workers` as a gunicorn alternative on bare VMs, free-threading | New sub-file (§1 additions table) | see §1 |

**Bloat / duplication — one real instance, and one non-instance.**

- **Real:** the `lifespan` pitfall is near-copy-paste across two files. `fastapi_fundamentals_asgi.md`
  Pitfall 1 and `middleware_and_lifecycle.md` Pitfall 1 both teach deprecated `on_event` →
  `lifespan` with the same `asyncpg.create_pool` BROKEN/FIX pair and the same
  "startup exceptions get swallowed" framing. **Cut it from `fastapi_fundamentals_asgi`** (which
  should introduce `lifespan` and hand off) and leave the full treatment in the module that owns
  lifecycle. Saves ~60 lines and removes a duplicate distractor source from the MCQ bank, since
  both files' first sentences feed the same section-wide distractor pool. ~1 h.
- **Non-instance, recorded so it is not re-raised:** the Pydantic v1→v2 benchmark repeated in
  `pydantic_v2_deep_dive.md` §6.9 and `pydantic_core_and_performance.md` §1/§12, and the LIFO
  teardown example repeated in `dependency_injection_in_fastapi.md` §6.3 and
  `yield_dependencies_and_scopes.md` §5, are **parent-page/sub-file pairs by design**. Leave them.

**No merge or delete is recommended.** `message_queues_and_event_driven` is the one candidate —
it duplicates four `backend/` modules and its genuinely FastAPI-specific content (aiokafka/
aio-pika consumers wired into `lifespan`, the outbox pattern from an `AsyncSession`) is maybe
half the file. But merging it into `background_jobs_and_task_queues` would change the module id
of every question in it, and question ids are `module#md5(module|question)[:12]` — the merge
would orphan the spaced-repetition state of 16 questions for every player. Cheaper and safer:
**trim the generic Kafka/RabbitMQ theory to cross-links into `backend/` and keep the module id.**
~2 h, no wiring change.

### Case studies — all 6 diverge from the mandated template

| File | Lines | Q&As | Template | Real companies | Scale math |
|---|---|---|---|---|---|
| `design_async_web_scraper.md` | 837 | 10 | DIVERGES | no | yes (100k pages/hr → 28 pages/s) |
| `design_async_task_queue.md` | 804 | 10 | DIVERGES | no | **no** |
| `design_multi_tenant_saas_api.md` | 775 | 10 | DIVERGES | no | partial |
| `design_ml_inference_api_fastapi.md` | 713 | 9 | DIVERGES | no | yes |
| `design_realtime_chat_fastapi.md` | 698 | 9 | DIVERGES | no | partial |
| `design_rate_limited_api_fastapi.md` | 591 | 11 | DIVERGES | no | yes |

All six use the identical 7-section legacy skeleton — Problem Statement, Architecture Overview,
Key Design Decisions, Implementation, Python/FastAPI Components Used, Tradeoffs and Alternatives,
Interview Discussion Points. Against the mandated 11-section principal template they are missing
**`Intuition`, `1. Requirements Clarification`, `2. Scale Estimation`, `6. Real-World
Implementations`, `8. Operational Playbook`, `9. Common Pitfalls & War Stories`, and
`10. Capacity Planning`** as named sections. Average length is **736 lines against the 900–1100
bar**, and **not one of the six names a real company with real production numbers** — the
closest thing in the whole section is a module page (`security_hardening_and_owasp` citing the
2021 Parler scrape at 99.9% of public posts).

This is the same finding the `database` audit reported, and it generalizes. The aggravating
detail here: **`fastapi/CLAUDE.md` documents the divergence as if it were the rule** — "all use
the 7-section legacy template" — and `README.md` §5 repeats it. So the section is not drifting
from its contract; it has written a second, conflicting contract. Fixing the files without also
deleting those two sentences will just re-diverge. The index `case_studies/case_studies.md` is
fully compliant with its own 5-section contract and needs no work.

---

## 5. Cross-section notes

- **`python/`** — the boundary is correct and should be *enforced*, not redrawn.
  `python/the_gil_and_free_threading`, `python/asyncio_and_event_loop`,
  `python/async_patterns_and_pitfalls`, `python/testing_with_pytest` all exist. The new
  free-threading sub-file must cross-link `python/the_gil_and_free_threading` and cover only the
  *ASGI deployment consequence* (worker model, greenlet/SQLAlchemy, C-extension GIL re-enable).
  Likewise `testing_fastapi` should link `python/testing_with_pytest` for pytest mechanics rather
  than restating them.
- **`backend/`** — eight fastapi modules have a `backend/` twin: `backend/rest_api_design` ↔
  `api_design_and_versioning`; `backend/websockets_and_sse` ↔ `websockets_sse_and_streaming`;
  `backend/rate_limiting_in_depth` ↔ the rate-limit half of `api_design_and_versioning`;
  `backend/backend_security_owasp` ↔ `security_hardening_and_owasp`;
  `backend/auth_and_authorization_systems` ↔ `authentication_and_security`;
  `backend/observability_and_monitoring` ↔ same name;
  `backend/caching_strategies_deep_dive` + `backend/connection_pooling_deep_dive` ↔
  `caching_and_performance` + the pool half of `async_database_sqlalchemy`; and
  `backend/event_driven_fundamentals` + `kafka_deep_dive` + `rabbitmq_deep_dive` +
  `messaging_patterns` ↔ `message_queues_and_event_driven`. The working rule is already
  approximately right — **`backend/` owns the protocol and the pattern, `fastapi/` owns the
  framework wiring** — but only `message_queues_and_event_driven` actually violates it (see §4).
  Worth stating the rule in `fastapi/CLAUDE.md` so the next author does not re-derive it.
- **`ml/model_serving_and_inference`, `llm/`, `technologies/nvidia_triton_inference_server`** —
  the proposed `serving_llm_and_ai_workloads` must NOT re-teach batching strategy, KV cache, or
  model compression. It owns exactly the HTTP/ASGI edge: admission, cancellation, streaming,
  cost accounting. Cross-link all three.
- **`hld/api_design`, `hld/rate_limiting`, `hld/security_and_auth`** — same rule one level more
  abstract; already loosely observed.
- **Game wiring caveat.** Pydantic's product deep dive lives in this section
  (`pydantic_v2_deep_dive/pydantic_core_and_performance.md` is pinned in `build_tech.py`'s
  `PRODUCT_DEEP_DIVES`). Any new sub-file whose name matches `*_deep_dive` or `*_internals` must
  be added to `PRODUCT_DEEP_DIVES` or `CONCEPT_DEEP_DIVES` or `--strict` fails fatally. Both
  sub-files proposed here are deliberately named to avoid the pattern.

---

## 6. Execution plan

### Wave 1 — corrections only, no wiring changes (fully parallel, ~14 h)

All six items touch different files and can run as six concurrent agents. None touches
`STUDY_ORDER`, the `<!-- study-paths -->` block, or the root README.

1. **`testing_fastapi` httpx fix** — replace both `AsyncClient(app=app)` occurrences with
   `ASGITransport`; add BlockBuster to §6 and §11; add one streaming-endpoint test. **2.5 h.**
   *Highest priority in the whole plan: it is the only place the section ships code that raises
   on a current install.*
2. **`websockets_sse_and_streaming` native-SSE retarget** — §6.4 rewrite, §6.6 JSONL,
   `Last-Event-ID`, update Q9 + BP #7 + tools table. **4 h.**
3. **"5-50x" purge** — `README.md` L220 + L247, `fastapi_fundamentals_asgi.md` L771. Replace with
   the section's own measured figures. **0.5 h.**
4. **Version-table sweep** — `README.md` §1 (Python 3.11/3.12 → 3.13/3.14) and §4 (Starlette
   0.27+ → 1.6; httpx 0.24+ → 0.28.1 + the ASGITransport note; FastAPI 0.95 → 0.93 to match
   CLAUDE.md; add rows for FastAPI 0.134 JSONL / 0.135 SSE and SQLAlchemy 2.1's asyncio extra);
   `middleware_and_lifecycle.md` L34/L118 and `testing_fastapi.md` L606 Starlette floors;
   `http_clients_and_external_apis.md` L774 "HTTPX 1.0" row. **2 h.**
5. **`routing_and_request_handling` Q4 thread-pool contradiction** — one Q&A, and its
   `**Short:**` line must be re-read in the same edit. **0.5 h.**
6. **Three module top-ups** — asyncpg/PgBouncer pitfall in `async_database_sqlalchemy`; OTel
   `trace_id`→structlog + semconv note in `observability_and_monitoring`; TaskIQ in
   `background_jobs_and_task_queues`; the `../README.md` cross-ref in
   `yield_dependencies_and_scopes.md` L25. **4.5 h.**

> **Every one of these edits an answer body. Per the audit-owns-the-Short rule, each MUST re-read
> and update the `**Short:**` line above any Q&A it touches, in the same edit.** Item 2 alone
> rewrites at least Q9. This is the one place a correct fix reliably introduces a new defect.

### Wave 2 — the case-study rebuild (parallel across the 6 files, ~24 h)

7. First, **delete the false contract**: the "7-section legacy template" sentence in
   `fastapi/CLAUDE.md` §Case Studies and in `README.md` §5. **0.25 h.** Do this before any file,
   or the rewrites will be re-diverged by the next author.
8. Rebuild all 6 to the 11-section principal template — 4 h each, six agents in parallel. Each
   gains `Intuition`, `1. Requirements Clarification`, `2. Scale Estimation` (with QPS/storage/
   bandwidth arithmetic — `design_async_task_queue` has none at all), `6. Real-World
   Implementations` **naming real companies with real numbers** (the universal gap),
   `8. Operational Playbook`, `9. Common Pitfalls & War Stories` with quantified impact, and
   `10. Capacity Planning`. Target 900–1,100 lines each, i.e. ~+170 lines average. Existing
   Implementation content maps into `4. Component Deep Dives`; existing Q&As stay in
   `11. Interview Discussion Points` and are still bank-excluded by design. **24 h.**

### Wave 3 — P0 additions (2 files, parallel, ~16 h) — REQUIRES WIRING

9. Write `serving_llm_and_ai_workloads/serving_llm_and_ai_workloads.md`, ~1,300 lines, 16+ Q&As
   each with an authored `**Short:**` line. Depends on Wave 1 item 2 landing first so the native
   SSE facts are settled in one place. **10 h.**
10. Write `production_deployment_and_scaling/asgi_runtimes_and_free_threading.md`, ~900 lines,
    15+ Q&As. **6 h.**
11. **Wiring, one commit, in this order:** add both files to the `<!-- study-paths -->` block in
    `fastapi/README.md` with tiers (`serving_llm_and_ai_workloads.md  senior, principal`;
    `asgi_runtimes_and_free_threading.md  principal`); add the new module to `STUDY_ORDER.fastapi`
    at position 12 and apply the `testing_fastapi` move to position 7; update README §2 module
    table and the §3 Phase Mermaid diagram by hand; update `fastapi/CLAUDE.md`'s module list and
    Sub-Files table; update root `README.md` and root `CLAUDE.md` (19 modules → 20). Then
    `python3 game/extract.py --write-paths`, then `python3 game/extract.py --strict`, then the
    whole of `scripts/build_banks.sh` — `--strict` passing does not mean CI passes. **2 h.**

### Wave 4 — P1 (~20 h, parallel, requires wiring)

12. `python_api_framework_landscape/` module (1,100 lines) — new `STUDY_ORDER` entry at
    position 21, principal tier. **8 h + wiring.**
13. `case_studies/design_llm_gateway_fastapi.md` — 11-section template from the start; update
    `case_studies/case_studies.md` (all 5 sections: quick start, learning path, cross-cutting
    table, dependency map, interview shortcuts) **in the same commit**, plus the block, README §5,
    and `CLAUDE.md`'s case-study list. **7 h.**
14. `serving_llm_and_ai_workloads/mcp_servers_from_fastapi.md` sub-file (700 lines) — no
    `STUDY_ORDER` entry needed (sub-files group under the parent), but the block line is
    mandatory. **5 h.**

### Wave 5 — P2 (~13 h)

15. `api_design_and_versioning/openapi_31_and_client_generation.md` (750 lines). **5 h.**
16. `case_studies/design_file_upload_and_media_api.md` (900 lines) + index update. **7 h.**
17. Trim `message_queues_and_event_driven`'s generic Kafka/RabbitMQ theory to `backend/`
    cross-links; add the backend-owns-the-pattern rule to `fastapi/CLAUDE.md`. **2 h.**
18. Cut the duplicated `lifespan` pitfall from `fastapi_fundamentals_asgi.md`. **1 h.**

**Total: ~87 h.** Waves 1 and 2 are independent of each other and of everything else, so with 3
concurrent agents the critical path is roughly **Wave 1 (5 h wall) → Wave 2 (8 h wall) → Wave 3
(12 h wall) → Waves 4–5 (12 h wall) ≈ 37 h wall-clock**. Wave 1 alone buys most of the value.

**Files that need touching outside the section:** `game/app.js` (`STUDY_ORDER.fastapi`), root
`README.md` (FastAPI phase table + module count), root `CLAUDE.md` (the FastAPI row: 19 modules
→ 20 → 21, and the sub-file count 2 → 5). Nothing in `build_tech.py` — both new sub-files are
named to avoid the `*_deep_dive` / `*_internals` pin guard.

---

## 7. Confidence and gaps

**High confidence** — every §2 correction is web-verified against a primary source (PyPI JSON,
the project's own CHANGELOG, or the framework's own docs), and the internal contradictions
(5-50x vs the section's own CLAUDE.md; two Starlette floors in one file; the thread-pool Q&A)
are verifiable by grep with no external dependency. The case-study template divergence is
mechanical and was confirmed by reading every `## ` heading in all six files.

**What I could not verify:**

- **The exact FastAPI version in which `lifespan` first shipped.** The release-notes page I
  fetched summarised 0.115 and 0.100 but did not surface the 0.93/0.95 entry directly. I am
  confident one of the two numbers in this repo is wrong because they disagree with each other,
  but not which. Check `fastapi.tiangolo.com/release-notes/` for the 0.93.0 entry before editing.
- **The uvicorn native `--workers` process manager's current maturity.** I confirmed uvicorn is
  at 0.52.4 (2026-08-19) and that FastAPI's docs de-emphasise gunicorn in orchestrated
  environments, but I did not read the uvicorn 0.49–0.52 release notes to see whether the
  built-in multi-process manager is now recommended over gunicorn on *bare VMs*. Item 10 needs
  that read before it asserts a recommendation.
- **Granian's benchmark numbers.** The 20–50% throughput figure and the 43,263 vs 7,899 RPS pair
  come from vendor/blog benchmarks, not a neutral harness. The sub-file should cite them as
  "published by the project" and lead with the *qualitative* difference (native backpressure →
  503 vs unbounded queueing) which is architectural and not benchmark-dependent.
- **The `aiohttp ~10-15% better at >100 concurrent` claim** in `http_clients_and_external_apis`
  L774–777 and the `>200 concurrent connections` switching threshold. These read as fabricated
  precision — I could not find a source. They are not in §2 because I could not verify them
  *wrong* either. Someone should either find the benchmark or soften them to a directional claim.
- **Whether `sse-starlette` should be removed entirely or kept as a fallback.** FastAPI 0.135 is
  recent enough that a reader on an older pin still needs the third-party path. I recommend
  demoting rather than deleting, but that is an editorial call I did not have grounds to settle.
- **`extract.py` output.** I did not run it — it writes generated artifacts, and the brief
  forbids `--write-paths`. All Q&A counts here are from `grep -c '^\*\*Q'`, which slightly
  over-counts if any file has a bold line starting with `**Q` outside a Q&A span. Run
  `python3 game/extract.py` before trusting the 342 figure to the digit.
