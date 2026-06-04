# FastAPI + Production Python — Senior Engineer Guide

FastAPI-specific content covering Phases 4-6 of the Python + FastAPI study section. These modules assume familiarity with the [pure-Python fundamentals](../README.md).

## Phase 4 — FastAPI Core (6 modules)
| Module | Key Concepts |
|--------|-------------|
| [fastapi_fundamentals_asgi](fastapi_fundamentals_asgi/) | ASGI 3 protocol, Starlette, Uvicorn, lifespan, auto OpenAPI |
| [pydantic_v2_deep_dive](pydantic_v2_deep_dive/) | pydantic-core Rust, @field_validator, v1→v2 migration, BaseSettings |
| [routing_and_request_handling](routing_and_request_handling/) | Path operations, APIRouter, response models, BackgroundTasks |
| [dependency_injection_in_fastapi](dependency_injection_in_fastapi/) | Depends, yield deps, caching scopes, dependency_overrides |
| [middleware_and_lifecycle](middleware_and_lifecycle/) | Middleware stack, lifespan [0.93+], CORS/GZip, exception handlers |
| [configuration_and_settings_management](configuration_and_settings_management/) | pydantic-settings, @lru_cache singleton, SecretStr, 12-factor |

## Phase 5 — Production Concerns (8 modules)
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

## Phase 6 — Deployment, Observability & Scale (5 modules)
| Module | Key Concepts |
|--------|-------------|
| [production_deployment_and_scaling](production_deployment_and_scaling/) | Gunicorn+Uvicorn workers, K8s HPA, graceful shutdown, uvloop |
| [observability_and_monitoring](observability_and_monitoring/) | OpenTelemetry, structlog, Prometheus metrics, health probes |
| [caching_and_performance](caching_and_performance/) | Redis cache-aside, stampede prevention, orjson, lru_cache trap |
| [api_design_and_versioning](api_design_and_versioning/) | Cursor pagination, idempotency keys, slowapi, OpenAPI |
| [security_hardening_and_owasp](security_hardening_and_owasp/) | OWASP API Top 10, BOLA, SSRF, pip-audit, CORS |

## Case Studies
| Case Study | Core Challenge |
|------------|---------------|
| [Design a Rate-Limited API](case_studies/design_rate_limited_api_fastapi.md) | Token-bucket Redis Lua, DI-injected rate limiter |
| [Design a Multi-Tenant SaaS API](case_studies/design_multi_tenant_saas_api.md) | PostgreSQL RLS, tenant JWT, async SQLAlchemy |
| [Design a Real-Time Chat System](case_studies/design_realtime_chat_fastapi.md) | WebSockets, Redis pub/sub, horizontal scaling |
| [Design an Async Task Queue](case_studies/design_async_task_queue.md) | ARQ vs Celery, idempotency, DLQ |
| [Design an Async Web Scraper](case_studies/design_async_web_scraper.md) | asyncio Semaphore, producer/consumer, politeness |
| [Design an ML Inference API](case_studies/design_ml_inference_api_fastapi.md) | lifespan model loading, micro-batching, SSE streaming |

---

**Back to Python master index:** [../README.md](../README.md)
