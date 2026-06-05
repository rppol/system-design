# Spring Case Studies — Learning Path

Nine end-to-end case studies covering senior Spring engineer and backend system design interview scenarios. All nine use the 11-section principal template (requirements → scale → architecture → deep dives → design decisions → real world → tools → playbook → pitfalls → capacity → interview). Four cross-cutting shared-primitive files in `cross_cutting/` cover operational patterns referenced by multiple case studies.

---

## Quick Start

If you have time for three case studies before an interview, read these:

1. **[API Gateway](design_api_gateway.md)** — covers routing, rate limiting, auth delegation, circuit breaking, and observability at the gateway layer; the most broadly applicable Spring Cloud architecture question.
2. **[Event-Driven Microservice](design_event_driven_microservice.md)** — covers Kafka producer/consumer, transactional outbox, idempotent consumption, and saga orchestration; asked at virtually every company using microservices.
3. **[Idempotent Payment API](design_idempotent_payment_api.md)** — covers exactly-once semantics, idempotency keys in Redis, distributed locks, and two-phase commit alternatives; the canonical fintech design question.

---

## Full Learning Path

Studies are grouped by primary engineering concern, not product category.

### Group 1: API Layer and Gateway

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [API Gateway](design_api_gateway.md) | Request routing, cross-cutting concerns | Spring Cloud Gateway predicates and filters, Resilience4j circuit breakers, JWT validation at the edge, dynamic route configuration via Config Server |
| [Distributed Rate Limiter](design_distributed_rate_limiter_spring.md) | Token bucket, Redis sliding window | Lua scripting for atomic Redis operations, leaky-bucket vs sliding-window comparison, per-tenant and per-endpoint limits, Resilience4j RateLimiter integration |
| [Multi-Tenant API](design_multitenant_api.md) | Tenant isolation, schema-per-tenant | TenantContext via ThreadLocal, AbstractRoutingDataSource for schema routing, JWT claim extraction, row-level-security fallback, Spring Security tenant filter chain |

### Group 2: Security and Identity

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [OAuth2 Authorization Server](design_oauth2_authorization_server.md) | OAuth2 / OIDC issuer, token lifecycle | Spring Authorization Server setup, PKCE flow, refresh token rotation, opaque vs JWT tokens, client credentials and authorization_code flows side by side |

### Group 3: Data Integrity and Transactions

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [Idempotent Payment API](design_idempotent_payment_api.md) | Exactly-once semantics, distributed locks | Idempotency key hashing in Redis, conditional writes, Redisson distributed lock, saga rollback on partial failure, reconciliation job design |
| [Distributed Caching](design_distributed_caching.md) | Cache consistency, stampede prevention | Spring Cache abstraction, RedisCacheManager, cache-aside vs write-through, Redisson read/write lock for stampede prevention, multi-tier L1+L2 cache topology |

### Group 4: Messaging and Events

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [Event-Driven Microservice](design_event_driven_microservice.md) | Transactional outbox, saga | Spring Kafka producer/consumer, @TransactionalEventListener + outbox table, saga orchestrator with compensation, dead-letter topic handling, schema registry |
| [Real-Time Notification Service](design_realtime_notification_service.md) | Fan-out, WebSocket at scale | Spring WebSocket + STOMP broker relay, Kafka consumer group fan-out, push channel preference (SSE vs WebSocket vs FCM), Redis pub/sub for horizontal scaling |

### Group 5: Batch Processing

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [Batch Pipeline](design_batch_pipeline.md) | Chunk-oriented processing, partitioning | Spring Batch Job/Step/chunk model, remote partitioning across worker pods, skip/retry policies, JobRepository on PostgreSQL, idempotent re-run strategy |

---

## Cross-Cutting / Shared Primitives

These files in `cross_cutting/` cover infrastructure patterns used across multiple case studies. Consume each file just before (or alongside) the case studies that rely most heavily on it.

| File | Best Read With | What It Covers |
|---|---|---|
| [OTel Observability for Spring](cross_cutting/otel_observability_for_spring.md) | API Gateway, Event-Driven Microservice, Batch Pipeline | Micrometer Observation API, OTel SDK auto-instrumentation, W3C traceparent propagation across Kafka and HTTP, structured logging with trace correlation |
| [Resilience4j Patterns](cross_cutting/resilience4j_patterns.md) | API Gateway, Distributed Rate Limiter | Circuit breaker state machine, bulkhead (semaphore + thread pool), retry with exponential backoff, TimeLimiter, combining decorators correctly |
| [Testcontainers and Test Strategy](cross_cutting/testcontainers_and_test_strategy.md) | All case studies (read early) | @SpringBootTest slice strategy, Testcontainers for Postgres/Redis/Kafka, WireMock for external services, contract testing with Spring Cloud Contract |
| [Zero-Downtime Deploys and Config](cross_cutting/zero_downtime_deploys_and_config.md) | Multi-Tenant API, Idempotent Payment API, Batch Pipeline | Rolling deploy with schema migration sequencing, @RefreshScope + Spring Cloud Config Bus, feature flags via environment properties, graceful shutdown drain |

---

## Dependency Map

Read from top to bottom; an arrow means "the lower study builds on patterns introduced above."

```
design_oauth2_authorization_server
    |
    +---> design_multitenant_api         (tenant identity encoded in JWT claims)
    |
    +---> design_api_gateway             (JWT validation delegated to auth server)
              |
              +---> design_distributed_rate_limiter_spring
                                         (rate limiting embedded at gateway layer)

design_distributed_caching
    |
    +---> design_idempotent_payment_api  (Redis patterns: atomic check-and-set, locks)
    |
    +---> design_realtime_notification_service
                                         (Redis pub/sub for WebSocket fan-out)

design_event_driven_microservice
    |
    +---> design_realtime_notification_service
                                         (Kafka topic as fan-out source for push notifications)

design_batch_pipeline                    (standalone; no direct upstream dependency)
```

---

## Interview Prep Shortcuts

| "Design X" interview question | Best case study to read |
|---|---|
| Design an API gateway for microservices | [API Gateway](design_api_gateway.md) |
| Design a rate limiter / throttling service | [Distributed Rate Limiter](design_distributed_rate_limiter_spring.md) |
| Design a multi-tenant SaaS platform | [Multi-Tenant API](design_multitenant_api.md) |
| Design an OAuth2 / SSO authorization server | [OAuth2 Authorization Server](design_oauth2_authorization_server.md) |
| Design a payment processing API (idempotency) | [Idempotent Payment API](design_idempotent_payment_api.md) |
| Design a distributed caching layer | [Distributed Caching](design_distributed_caching.md) |
| Design an event-driven order processing system | [Event-Driven Microservice](design_event_driven_microservice.md) |
| Design a real-time notification / push system | [Real-Time Notification Service](design_realtime_notification_service.md) |
| Design a data pipeline / ETL batch system | [Batch Pipeline](design_batch_pipeline.md) |
| How do you handle exactly-once delivery in Kafka? | [Event-Driven Microservice](design_event_driven_microservice.md) |
| How do you prevent cache stampede under load? | [Distributed Caching](design_distributed_caching.md) |
| How do you prevent double-charge in payments? | [Idempotent Payment API](design_idempotent_payment_api.md) |
| How do you isolate tenants in a shared database? | [Multi-Tenant API](design_multitenant_api.md) |
| How do you implement circuit breaking in Spring? | [API Gateway](design_api_gateway.md) + [Resilience4j Patterns](cross_cutting/resilience4j_patterns.md) |

---

## Build Manifest

| File | Template | Q&As | Status |
|---|---|---|---|
| [design_multitenant_api.md](design_multitenant_api.md) | 11-section principal | ~10 | DONE |
| [design_event_driven_microservice.md](design_event_driven_microservice.md) | 11-section principal | ~10 | DONE |
| [design_api_gateway.md](design_api_gateway.md) | 11-section principal | ~10 | DONE |
| [design_batch_pipeline.md](design_batch_pipeline.md) | 11-section principal | ~10 | DONE |
| [design_distributed_caching.md](design_distributed_caching.md) | 11-section principal | ~11 | DONE |
| [design_distributed_rate_limiter_spring.md](design_distributed_rate_limiter_spring.md) | 11-section principal | ~10 | DONE |
| [design_oauth2_authorization_server.md](design_oauth2_authorization_server.md) | 11-section principal | ~10 | DONE |
| [design_idempotent_payment_api.md](design_idempotent_payment_api.md) | 11-section principal | ~8 | DONE |
| [design_realtime_notification_service.md](design_realtime_notification_service.md) | 11-section principal | ~8 | DONE |
| [cross_cutting/otel_observability_for_spring.md](cross_cutting/otel_observability_for_spring.md) | 14-section module | — | DONE |
| [cross_cutting/resilience4j_patterns.md](cross_cutting/resilience4j_patterns.md) | 14-section module | — | DONE |
| [cross_cutting/testcontainers_and_test_strategy.md](cross_cutting/testcontainers_and_test_strategy.md) | 14-section module | — | DONE |
| [cross_cutting/zero_downtime_deploys_and_config.md](cross_cutting/zero_downtime_deploys_and_config.md) | 14-section module | — | DONE |
