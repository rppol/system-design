# Python + FastAPI — Case Studies Learning Path

End-to-end system design case studies demonstrating production Python and FastAPI architecture. Each case study follows the repo-wide 11-section principal template: Intuition, 1. Requirements Clarification, 2. Scale Estimation, 3. High-Level Architecture, 4. Component Deep Dives, 5. Design Decisions & Tradeoffs, 6. Real-World Implementations, 7. Technologies & Tools, 8. Operational Playbook, 9. Common Pitfalls & War Stories, 10. Capacity Planning, 11. Interview Discussion Points.

---

## Quick Start

If you only have time for three case studies, read these in order:

1. **[Design a Rate-Limited API with FastAPI](design_rate_limited_api_fastapi.md)** — Foundation case study. Establishes the `Depends`-injected service-layer pattern used by all other FastAPI case studies. Covers Redis Lua atomic check-and-decrement, 429 error handling, and async middleware design.
2. **[Design an Async Task Queue System](design_async_task_queue.md)** — Most commonly asked in distributed systems interviews. Covers ARQ vs Celery decision, idempotency keys, at-least-once semantics, DLQ, and result-backend trade-offs.
3. **[Design an ML Inference API with FastAPI](design_ml_inference_api_fastapi.md)** — Bridges the Python/FastAPI section with the ML and LLM sections. Shows model loading via `lifespan`, micro-batching, and Server-Sent Events streaming. Cross-links `ml/model_serving_and_inference/` and `llm/case_studies/cross_cutting/streaming_at_scale.md`.

---

## Full Learning Path

### Concern 1 — API Design & Rate Limiting

| Case Study | Primary Engineering Concern | What It Teaches |
|------------|----------------------------|----------------|
| [Design a Rate-Limited API](design_rate_limited_api_fastapi.md) | Concurrency control, Redis atomicity | Token-bucket implementation as a `Depends`-injected class; Redis Lua scripts for atomic check-and-decrement (preventing TOCTOU races); 429 response shaping with `Retry-After`; middleware vs dependency injection trade-offs for cross-cutting concerns. |

### Concern 2 — Multi-Tenancy & Authorization

| Case Study | Primary Engineering Concern | What It Teaches |
|------------|----------------------------|----------------|
| [Design a Multi-Tenant SaaS API](design_multi_tenant_saas_api.md) | Tenant isolation, async SQLAlchemy, RBAC | Row-level security vs schema-per-tenant vs database-per-tenant, decided on the connection budget; setting the RLS context transaction-locally so a pooled connection cannot leak tenant state; why `FORCE ROW LEVEL SECURITY` is required when the app owns its tables; DB-fetched roles via `Depends` rather than roles baked into the JWT; a single Alembic migration covering every tenant. |

### Concern 3 — Real-Time & Streaming

| Case Study | Primary Engineering Concern | What It Teaches |
|------------|----------------------------|----------------|
| [Design a Real-Time Chat System](design_realtime_chat_fastapi.md) | WebSockets, pub/sub fan-out, horizontal scaling | WebSocket connection lifecycle in FastAPI; `ConnectionManager` class for registering and broadcasting; Redis pub/sub for multi-pod fan-out (stateless workers); backpressure when a slow consumer falls behind; graceful disconnect and reconnect handling. |
| [Design an ML Inference API](design_ml_inference_api_fastapi.md) | Streaming responses, model serving, caching | `lifespan` context manager for model loading at startup; `StreamingResponse` + SSE for token-by-token streaming; async request coalescing / micro-batching; Redis semantic cache for repeated prompts; async perf profiling. |

### Concern 4 — Async & Background Work

| Case Study | Primary Engineering Concern | What It Teaches |
|------------|----------------------------|----------------|
| [Design an Async Task Queue](design_async_task_queue.md) | Durability, idempotency, retry semantics | ARQ vs Celery vs Dramatiq decision matrix; idempotency key pattern with Redis; at-least-once vs exactly-once semantics; dead-letter queue design; result-backend trade-offs (Redis vs PostgreSQL). |
| [Design an Async Web Scraper](design_async_web_scraper.md) | asyncio concurrency control, rate limiting | `asyncio.Semaphore` for politeness limiting; `aiohttp.ClientSession` connection pooling; producer/consumer with `asyncio.Queue`; crawl budget and robots.txt enforcement; retry with exponential backoff + jitter. |

---

### LLM and AI Serving

| Case Study | Primary Concern | What It Teaches |
|---|---|---|
| [Design an LLM Gateway](design_llm_gateway_fastapi.md) | A FastAPI front door for model traffic | Streaming completions over SSE, cancellation propagated to the backend, per-tenant token budgets, provider failover, and semantic caching with its invalidation trap. |

### File Uploads and Media

| Case Study | Primary Concern | What It Teaches |
|---|---|---|
| [Design a File Upload and Media API](design_file_upload_and_media_api.md) | Large request bodies, object storage | `UploadFile` spill threshold, multipart DoS limits, presigned S3 PUT, S3 multipart and resumable uploads, content sniffing, virus scanning before promotion, and off-request post-processing. |

## Cross-Cutting / Shared Primitives

No `cross_cutting/` directory here — the shared primitives live as deep-dive modules in the section itself. The same primitives recur across these case studies; read the deep dive once, then recognize it everywhere:

| Primitive | Used By | Deep Dive | Phase |
|---|---|---|---|
| `Depends`-injected service layer | all case studies (established by Rate-Limited API) | `../dependency_injection_in_fastapi/dependency_injection_in_fastapi.md` | 1 — Core Framework |
| Redis Lua atomicity (TOCTOU-free counters) | Rate-Limited API | `../caching_and_performance/caching_and_performance.md` | 3 — Production |
| `lifespan` startup loading | ML Inference, Rate-Limited API | `../fastapi_fundamentals_asgi/fastapi_fundamentals_asgi.md` | 1 |
| Async SQLAlchemy session-per-request | Multi-Tenant SaaS | `../async_database_sqlalchemy/async_database_sqlalchemy.md` | 2 — Data & Integration |
| JWT-carried identity + DB-fetched roles | Multi-Tenant SaaS | `../authentication_and_security/authentication_and_security.md` | 2 |
| WebSocket lifecycle + Redis pub/sub fan-out | Real-Time Chat, ML Inference (SSE) | `../websockets_sse_and_streaming/websockets_sse_and_streaming.md` | 2 |
| Idempotency keys, retries, DLQ | Async Task Queue | `../background_jobs_and_task_queues/background_jobs_and_task_queues.md` | 2 |
| `asyncio.Semaphore` / `asyncio.Queue` producer-consumer | Async Web Scraper | `../../python/asyncio_and_event_loop/asyncio_and_event_loop.md` (cross-section) | 2 |
| Rate limiting + `Retry-After` shaping | Rate-Limited API | `../api_design_and_versioning/api_design_and_versioning.md` | 3 |

---

## Dependency Map

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    RL("design_rate_limited_api_fastapi<br/>read first — DI + Redis + error patterns")
    MT("design_multi_tenant_saas_api<br/>+ async SQLAlchemy + RBAC")
    RC("design_realtime_chat_fastapi<br/>+ WebSocket + Redis pub/sub layer")
    ML("design_ml_inference_api_fastapi<br/>+ lifespan + SSE + micro-batching")
    ATQ("design_async_task_queue<br/>independent — BackgroundTasks first principles")
    AWS("design_async_web_scraper<br/>shares Semaphore / producer-consumer patterns")

    RL --> MT
    RL --> RC
    RL --> ML
    ATQ --> AWS

    class RL,ATQ base
    class MT,RC,ML,AWS train
```

**Always read `design_rate_limited_api_fastapi` first.** It establishes the `Depends`-injected service layer pattern that all other FastAPI case studies extend.

---

## Interview Prep Shortcuts

| "Design X" question | Best case study |
|---------------------|----------------|
| Design a rate limiter / API throttler | [design_rate_limited_api_fastapi.md](design_rate_limited_api_fastapi.md) |
| Design a multi-tenant SaaS backend | [design_multi_tenant_saas_api.md](design_multi_tenant_saas_api.md) |
| Design a real-time chat / notification system | [design_realtime_chat_fastapi.md](design_realtime_chat_fastapi.md) |
| Design a job queue / background task system | [design_async_task_queue.md](design_async_task_queue.md) |
| Design a web crawler / async scraper | [design_async_web_scraper.md](design_async_web_scraper.md) |
| Design an ML model serving API | [design_ml_inference_api_fastapi.md](design_ml_inference_api_fastapi.md) |
| How would you build a streaming API? | [design_realtime_chat_fastapi.md](design_realtime_chat_fastapi.md) + [design_ml_inference_api_fastapi.md](design_ml_inference_api_fastapi.md) |
| How would you handle distributed shared state in FastAPI? | [design_rate_limited_api_fastapi.md](design_rate_limited_api_fastapi.md) |
| How would you do zero-downtime deploys? | [design_ml_inference_api_fastapi.md](design_ml_inference_api_fastapi.md) (covers `lifespan` pattern) |
| How would you implement tenant isolation at the DB layer? | [design_multi_tenant_saas_api.md](design_multi_tenant_saas_api.md) |
| Design an LLM gateway / proxy in front of model providers | [design_llm_gateway_fastapi](design_llm_gateway_fastapi.md) |
| Design a file / media upload service | [design_file_upload_and_media_api](design_file_upload_and_media_api.md) |
