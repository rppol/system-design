# Backend Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/backend/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Why This Section Exists

Java and Spring cover language/framework mechanics. This section covers the **engineering discipline**: how to design, optimize, secure, observe, and operate backend systems at scale. It answers the questions senior engineers face in system design interviews and on-call rotations.

---

## Module List — 34 Modules (9 Phases)

### Phase 1 — Networking Fundamentals

| Module | Topic | Q&As |
|--------|-------|------|
| `osi_model_and_networking/` | 7 layers, TCP/IP mapping, packet encapsulation, ARP, NAT, MTU | 15 |
| `tcp_ip_deep_dive/` | 3-way handshake, congestion control, TIME_WAIT, socket tuning | 18 |
| `udp_and_quic/` | UDP characteristics, QUIC 0-RTT, HTTP/3, DTLS | 12 |
| `http_protocols/` | HTTP/1.1 vs /2 vs /3, TLS 1.3, ALPN, SNI, HSTS | 15 |

### Phase 2 — API Design & Protocols

| Module | Topic | Q&As |
|--------|-------|------|
| `rest_api_design/` | REST constraints, versioning, idempotency, pagination, ETag, RFC 7807 | 15 |
| `grpc_and_protobuf/` | Protobuf wire format, 4 RPC modes, interceptors, deadlines | 15 |
| `graphql/` | Schema design, DataLoader N+1, subscriptions, depth limiting | 12 |
| `websockets_and_sse/` | WS upgrade, frame structure, SSE, long polling, scaling WS | 12 |

### Phase 3 — Performance Engineering

| Module | Topic | Q&As |
|--------|-------|------|
| `performance_profiling/` | async-profiler, JFR, flamegraphs, heap/thread dumps, GC analysis | 15 |
| `connection_pooling_deep_dive/` | HikariCP internals, pool sizing formula, leak detection, PgBouncer | 15 |
| `caching_strategies_deep_dive/` | Cache-aside/read-through/write-behind, stampede, Redis structures | 15 |
| `async_and_concurrency_patterns/` | Thread pool sizing, CompletableFuture pitfalls, virtual threads, bulkhead | 15 |

### Phase 4 — Database Engineering (overview; `database/` has the deep-dives)

| Module | Topic | Q&As |
|--------|-------|------|
| `database_internals_and_indexing/` | B+tree, WAL, MVCC, index types, VACUUM, query planner | 15 |
| `query_optimization/` | EXPLAIN ANALYZE, N+1 detection, pagination, batch inserts | 15 |
| `database_migrations/` | Flyway vs Liquibase, zero-downtime patterns, expand-contract | 12 |
| `distributed_transactions_and_consistency/` | 2PC problems, Saga, outbox pattern, idempotency keys | 15 |
| `database_types_deep_dive/` | Relational, Document, Key-Value, Wide-Column, Time-Series, Search, Graph, NewSQL | 18 |

> For deeper coverage: see [`../database/README.md`](../database/README.md) — 29 modules, 7 phases, principal-engineer level.

### Phase 5 — Resilience & Reliability

| Module | Topic | Q&As |
|--------|-------|------|
| `fault_tolerance_patterns/` | Circuit breaker states, Resilience4j, retry with jitter, bulkhead | 15 |
| `rate_limiting_in_depth/` | Token bucket, sliding window, Redis Lua, adaptive throttling | 12 |
| `observability_and_monitoring/` | Metrics/logs/traces, Micrometer, MDC, OpenTelemetry, SLO/SLI | 15 |

### Phase 6 — Security

| Module | Topic | Q&As |
|--------|-------|------|
| `backend_security_owasp/` | OWASP Top 10 2021, SQL injection, CSRF, SSRF, secret management | 15 |
| `auth_and_authorization_systems/` | JWT internals, OAuth2 flows, OIDC, RBAC vs ABAC, token revocation | 15 |

### Phase 7 — Testing & Quality

| Module | Topic | Q&As |
|--------|-------|------|
| `backend_testing_strategies/` | Testing pyramid, test doubles, contract testing, mutation testing | 12 |
| `load_and_performance_testing/` | k6, Gatling, JMeter, percentile analysis, coordinated omission | 12 |
| `chaos_engineering/` | Steady-state hypothesis, fault injection, blast radius, GameDay | 10 |

### Phase 8 — Event-Driven Architecture

| Module | Topic | Q&As |
|--------|-------|------|
| `event_driven_fundamentals/` | Events vs commands, choreography vs orchestration, event storming | 15 |
| `kafka_deep_dive/` | Producer/consumer internals, EOS, Kafka Streams, Schema Registry | 18 |
| `event_sourcing_and_cqrs/` | Event store, aggregates, snapshots, CQRS read models, Axon | 15 |
| `messaging_patterns/` | Outbox, inbox, DLQ, poison pill, schema evolution, RabbitMQ vs Kafka | 15 |

### Phase 9 — Microservices Architecture

| Module | Topic | Q&As |
|--------|-------|------|
| `microservices_fundamentals/` | Decomposition, bounded contexts, data ownership, strangler fig | 15 |
| `api_gateway_patterns/` | Gateway responsibilities, BFF, Spring Cloud Gateway, Kong | 15 |
| `service_mesh_and_service_discovery/` | Istio/Envoy, mTLS, service discovery mechanisms, health probes | 15 |
| `distributed_system_operational_patterns/` | Bulkhead, sidecar, ACL, correlation ID, feature flags, graceful shutdown | 15 |
| `container_and_deployment_patterns/` | 12-factor, Docker multi-stage, Kubernetes strategies, HPA, PDB | 15 |

---

## Case Studies — 5 Total

`case_studies/` directory. Learning-path index: `case_studies/README.md` (mandatory; update with every new case study).

| Case Study | Key Concepts |
|------------|-------------|
| `design_booking_system/` | Optimistic locking, distributed lock, idempotency |
| `design_feed_service/` | Fan-out strategies, Redis sorted sets, cursor pagination |
| `design_payment_processor/` | Saga orchestration, outbox pattern, idempotency keys |
| `design_event_driven_order_system/` | CQRS, Kafka EOS, transactional outbox, DLQ handling |
| `design_microservices_migration/` | Strangler fig, shared DB migration, traffic cutover |

---

## Cross-Reference Map

| This Module | See Also |
|-------------|---------|
| `async_and_concurrency_patterns/` | `../java/concurrency/` |
| `connection_pooling_deep_dive/` | `../java/jdbc_and_database/`, `../spring/spring_data_jpa/` |
| `distributed_transactions_and_consistency/` | `../spring/spring_transactions/`, `../database/distributed_transactions/` |
| `kafka_deep_dive/` | `../spring/spring_messaging/` |
| `auth_and_authorization_systems/` | `../spring/spring_security_architecture/`, `../spring/spring_security_jwt_oauth/` |
| `caching_strategies_deep_dive/` | `../spring/spring_caching/`, `../database/database_caching_patterns/` |
| `backend_testing_strategies/` | `../spring/spring_testing/` |
| `query_optimization/` | `../spring/spring_data_jpa/`, `../java/jdbc_and_database/`, `../database/sql_query_optimization/` |
| `microservices_fundamentals/` | `../hld/microservices/` — HLD-level overview |
| `api_gateway_patterns/` | `../hld/api_design/` — HLD-level overview |
| `service_mesh_and_service_discovery/` | `../hld/microservices/` |
| `event_sourcing_and_cqrs/` | `../hld/` — architectural overview |

---

## Version Matrix

| Technology | Version |
|------------|---------|
| Java | 21 (LTS) |
| Spring Boot | 3.2+ |
| Spring Framework | 6.1 |
| Kafka | 3.6+ (KRaft mode) |
| Resilience4j | 2.x |
| Micrometer | 1.12+ |
| OpenTelemetry | 1.x |

---

## Adding a New Backend Module

1. Create `<module_name>/README.md` — 14-section template
2. Code in Java 21 or relevant tech (YAML, Bash, etc.) — no pseudocode
3. Concrete numbers everywhere; at least 1 BROKEN→FIX block in §10 and §14
4. Update `README.md` module table
5. Update `case_studies/README.md` cross-reference map if the module is a dependency for a case study
6. Update root `README.md` Backend phase table
