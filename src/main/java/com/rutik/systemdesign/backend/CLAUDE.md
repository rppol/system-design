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
| `tcp_ip_deep_dive/` | 3-way handshake, congestion control, TIME_WAIT, socket tuning | 19 |
| `udp_and_quic/` | UDP characteristics, QUIC 0-RTT, HTTP/3, DTLS | 12 |
| `http_protocols/` | HTTP/1.1 vs /2 vs /3, TLS 1.3, ALPN, SNI, HSTS | 15 |

### Phase 2 — API Design & Protocols

| Module | Topic | Q&As |
|--------|-------|------|
| `rest_api_design/` | REST constraints, versioning, idempotency, pagination, ETag, RFC 9457 | 15 |
| `grpc_and_protobuf/` | Protobuf wire format, 4 RPC modes, interceptors, deadlines | 15 |
| `graphql/` | Schema design, DataLoader N+1, subscriptions, depth limiting | 12 |
| `websockets_and_sse/` | WS upgrade, frame structure, SSE, long polling, scaling WS | 13 |

### Phase 3 — Performance Engineering

| Module | Topic | Q&As |
|--------|-------|------|
| `performance_profiling/` | async-profiler, JFR, flamegraphs, heap/thread dumps, GC analysis | 14 |
| `connection_pooling_deep_dive/` | HikariCP internals, pool sizing formula, leak detection, PgBouncer | 16 |
| `caching_strategies_deep_dive/` | Cache-aside/read-through/write-behind, stampede, Redis structures | 16 |
| `async_and_concurrency_patterns/` | Thread pool sizing, CompletableFuture pitfalls, virtual threads, bulkhead | 15 |

### Phase 4 — Database Engineering (overview; `database/` has the deep-dives)

| Module | Topic | Q&As |
|--------|-------|------|
| `database_internals_and_indexing/` | B+tree, WAL, MVCC, index types, VACUUM, query planner | 16 |
| `query_optimization/` | EXPLAIN ANALYZE, N+1 detection, pagination, batch inserts | 16 |
| `database_migrations/` | Flyway vs Liquibase, zero-downtime patterns, expand-contract | 12 |
| `distributed_transactions_and_consistency/` | 2PC problems, Saga, outbox pattern, idempotency keys | 15 |
| `database_types_deep_dive/` | Relational, Document, Key-Value, Wide-Column, Time-Series, Search, Graph, NewSQL | 15 |

> For deeper coverage: see [`../database/README.md`](../database/README.md) — 29 modules, 7 phases, principal-engineer level.

### Phase 5 — Resilience & Reliability

| Module | Topic | Q&As |
|--------|-------|------|
| `fault_tolerance_patterns/` | Circuit breaker states, Resilience4j, retry with jitter, bulkhead | 15 |
| `rate_limiting_in_depth/` | Token bucket, sliding window, Redis Lua, adaptive throttling | 16 |
| `observability_and_monitoring/` | Metrics/logs/traces, Micrometer, MDC, OpenTelemetry, SLO/SLI | 16 |

### Phase 6 — Security

| Module | Topic | Q&As |
|--------|-------|------|
| `backend_security_owasp/` | OWASP Top 10:2025, SQL injection, CSRF, SSRF, secret management | 15 |
| `auth_and_authorization_systems/` | JWT internals, OAuth2 flows, OIDC, RBAC vs ABAC, token revocation | 16 |

### Phase 7 — Testing & Quality

| Module | Topic | Q&As |
|--------|-------|------|
| `backend_testing_strategies/` | Testing pyramid, test doubles, contract testing, mutation testing | 12 |
| `load_and_performance_testing/` | k6, Gatling, JMeter, percentile analysis, coordinated omission | 16 |
| `chaos_engineering/` | Steady-state hypothesis, fault injection, blast radius, GameDay | 16 |

### Phase 8 — Event-Driven Architecture

| Module | Topic | Q&As |
|--------|-------|------|
| `event_driven_fundamentals/` | Events vs commands, choreography vs orchestration, event storming | 15 |
| `kafka_deep_dive/` | Producer/consumer internals, EOS, Kafka Streams, Schema Registry | 18 |
| `event_sourcing_and_cqrs/` | Event store, aggregates, snapshots, CQRS read models, Axon | 11 |
| `messaging_patterns/` | Outbox, inbox, DLQ, poison pill, schema evolution, RabbitMQ vs Kafka | 16 |

### Phase 9 — Microservices Architecture

| Module | Topic | Q&As |
|--------|-------|------|
| `microservices_fundamentals/` | Decomposition, bounded contexts, data ownership, strangler fig | 15 |
| `api_gateway_patterns/` | Gateway responsibilities, BFF, Spring Cloud Gateway, Kong | 15 |
| `service_mesh_and_service_discovery/` | Istio/Envoy, mTLS, service discovery mechanisms, health probes | 16 |
| `distributed_system_operational_patterns/` | Bulkhead, sidecar, ACL, correlation ID, feature flags, graceful shutdown | 16 |
| `container_and_deployment_patterns/` | 12-factor, Docker multi-stage, Kubernetes strategies, HPA, PDB | 16 |

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 34 modules = the "9-Phase Learning Path")
plus two curated tiers: **Senior** (21 modules) and **Principal** (18). They are
different cuts, not nested depths — senior is the craft (build it, debug it at 3am),
principal is the judgment (which approach at what cost, what you tell a team *not* to
do), so principal is usually the smaller list and much of it is material senior never
sees. Membership is declared ONCE per module, in a `<!-- study-paths -->` block in that
module's own page (`<module>.md`) naming the files each tier takes; listing a tier joins it, omitting
the tier opts out, and the module page (`<module>.md`) must always be listed. Order is never declared — it
comes from `STUDY_ORDER.backend` in `game/app.js`, so a tier is an ordered subset by
construction. **There is no path array in `app.js` to edit**: `extract.py` walks the
markers and emits the gitignored `questions/paths.json`, which the game fetches at boot.
The tier tables in `README.md` sit between `<!-- study-path-table <tier> -->` markers and
are **generated** — regenerate with `python3 game/extract.py --write-paths`; a
hand-edited or stale block fails `extract.py --strict` and the Pages deploy. Case studies
are tiered the same way from a block in `case_studies/case_studies.md` (3 senior / 3
principal), driving the Level filter on the game's Case Studies tab. The README also
carries a Knowledge-Question Map and a 5-week Study Plan (prose; no path impact).

---

## Case Studies — 5 Total

`case_studies/` directory. Learning-path index: `case_studies/case_studies.md` (mandatory; update with every new case study).

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
| Java | 25 (LTS) |
| Spring Boot | 4.1.x |
| Spring Framework | 7.x |
| Spring Cloud | 2025.1.x "Oakwood" |
| Kafka | 4.2+ (KRaft-only) |
| Resilience4j | 2.4+ (`resilience4j-spring-boot4`) |
| Micrometer | 1.17+ |
| OpenTelemetry | 1.x |

---

## Adding a New Backend Module

1. Create `<module_name>/<module_name>.md` — 14-section template
2. Code in Java 25 or relevant tech (YAML, Bash, etc.) — no pseudocode
3. Concrete numbers everywhere; at least 1 BROKEN→FIX block in §10 and §14
4. Update `README.md` module table
5. Add the module dir to `STUDY_ORDER.backend` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
6. Write a `<!-- study-paths -->` block at the top of the new module's page (`<module_name>.md`) naming the tiers it
   belongs to (or none, for Full-path-only). Every tier line must list `<module_name>.md`
   itself — the module page is never optional and omitting it is fatal under `--strict`, then run `python3 game/extract.py --write-paths`
   to regenerate the section README's tier tables
7. Update `case_studies/case_studies.md` cross-reference map if the module is a dependency for a case study
8. Update root `README.md` Backend phase table

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
