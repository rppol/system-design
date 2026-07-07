# Backend Engineering — Senior Engineer & Interview Prep Guide

A comprehensive, production-focused reference for **backend engineering** — networking internals, API design, performance engineering, database deep dives, resilience patterns, security, testing, event-driven architecture, and microservices. Primary language is Java 21 with Spring Boot 3.x. Emphasis: interview Q&As, production war stories, tradeoff matrices, and design decisions.

---

## Why This Section Exists

The Java and Spring sections cover language and framework mechanics. This section covers the **engineering discipline**: how to design, optimize, secure, observe, and operate backend systems at scale. It answers the questions senior engineers face in system design interviews and on-call rotations — not "how does @Transactional work?" but "how do I prevent a thundering herd from destroying my cache layer at 3 AM?"

---

## 9-Phase Learning Path

```
Phase 1                Phase 2               Phase 3
Networking             API Design            Performance
Fundamentals           & Protocols           Engineering
 OSI Model  ---------> REST Design -------> Profiling
 TCP/IP Deep Dive ----> gRPC/Protobuf -----> Connection Pooling
 UDP & QUIC ----------> GraphQL -----------> Caching Deep Dive
 HTTP Protocols ------> WS & SSE ----------> Async Patterns
      |                      |                    |
      v                      v                    v
Phase 4                Phase 5               Phase 6
Database               Resilience &          Security
Engineering            Reliability
 DB Internals -------> Circuit Breaker ----> OWASP Top 10
 Query Opt. ----------> Rate Limiting ------> Auth & AuthZ
 Migrations ----------> Observability
 Dist. Transactions
 DB Types Deep Dive
      |                      |                    |
      v                      v                    v
Phase 7                Phase 8               Phase 9
Testing &              Event-Driven          Microservices
Quality                Architecture          Architecture
 Testing Strategy ---> EDA Fundamentals ---> MS Fundamentals
 Load Testing -------> Kafka Deep Dive ----> API Gateway
 Chaos Engineering --> Event Sourcing ------> Service Mesh
                    --> Messaging Patterns -> Operational Patterns
                                          -> Container Patterns
                                                 |
                                                 v
                                          Case Studies
                                    (Booking, Feed, Payment,
                                     Order System, Migration)
```

---

## Learning Paths

This section is exhaustive by design — 34 modules across 9 phases, from OSI-layer networking through microservices architecture and container orchestration. That is the right depth for a reference and the wrong shape for someone two weeks from an interview. So there are **two ways through it**; the browser learning game's **Study** view surfaces both as a **Full / Interview** toggle (Full is the default).

### Full Path (34 modules)

The complete curriculum in the order above — see [9-Phase Learning Path](#9-phase-learning-path). Use it for genuine mastery: every networking layer (OSI, TCP/IP, UDP/QUIC), the full API surface (GraphQL, WebSockets/SSE), performance profiling, database migrations and the full database-types survey, the testing trio, event sourcing/CQRS and messaging patterns, service mesh, and container/deployment patterns. Nothing is dropped.

### Interview-Specific Path (18 modules)

A ruthless cut to what a **senior backend engineering interview** actually probes — the protocols, performance levers, database internals, resilience patterns, security, and distributed-systems building blocks that come up in nearly every loop. Same learning order, ~47% fewer modules. Each group below says why it earns interview time.

| Group | Modules | Why it's tested |
|-------|---------|-----------------|
| Protocols & API Design | [HTTP Protocols](http_protocols/), [REST API Design](rest_api_design/), [gRPC & Protobuf](grpc_and_protobuf/) | HTTP/2 vs /3, TLS handshakes, idempotency, versioning, and REST-vs-RPC tradeoffs open almost every backend screen |
| Performance Engineering | [Connection Pooling Deep Dive](connection_pooling_deep_dive/), [Caching Strategies Deep Dive](caching_strategies_deep_dive/), [Async & Concurrency Patterns](async_and_concurrency_patterns/) | Pool sizing formulas, cache stampede, and thread-pool sizing are the "why is prod slow at 3 AM" questions every senior candidate must answer |
| Database Engineering | [Database Internals & Indexing](database_internals_and_indexing/), [Query Optimization](query_optimization/), [Distributed Transactions & Consistency](distributed_transactions_and_consistency/) | B+tree/MVCC internals, N+1 detection, and 2PC-vs-Saga are the deepest, highest-frequency backend-specific probes |
| Resilience & Observability | [Fault Tolerance Patterns](fault_tolerance_patterns/), [Rate Limiting In Depth](rate_limiting_in_depth/), [Observability & Monitoring](observability_and_monitoring/) | Circuit breaker states, token bucket vs sliding window, and metrics/logs/traces separate "writes code" from "operates a system" |
| Security | [Backend Security & OWASP](backend_security_owasp/), [Auth & Authorization Systems](auth_and_authorization_systems/) | OWASP Top 10, JWT/OAuth2 internals, and RBAC vs ABAC are near-universal, regardless of company or stack |
| Event-Driven Architecture | [Event-Driven Fundamentals](event_driven_fundamentals/), [Kafka Deep Dive](kafka_deep_dive/) | Choreography vs orchestration and Kafka's EOS/rebalancing internals anchor most "design an async pipeline" prompts |
| Microservices Architecture | [Microservices Fundamentals](microservices_fundamentals/), [API Gateway Patterns](api_gateway_patterns/) | Bounded contexts, the strangler fig pattern, and gateway/BFF responsibilities are the default frame for "design X at scale" |

**Deliberately deferred to the Full Path** (valuable, lower interview yield): the networking deep-dives (OSI Model & Networking, TCP/IP Deep Dive, UDP & QUIC), GraphQL, WebSockets & SSE, Performance Profiling, Database Migrations, Database Types Deep Dive, the testing trio (Backend Testing Strategies, Load & Performance Testing, Chaos Engineering), Event Sourcing & CQRS, Messaging Patterns, Service Mesh & Service Discovery, Distributed System Operational Patterns, and Container & Deployment Patterns. A niche flagged in an interview (e.g. "have you worked with a service mesh?") is a bonus, not a gate — reach for these once the 18 above are solid.

---

## Knowledge-Question Map

The highest-frequency backend *knowledge* questions mapped to the module that answers them. For *system design* ("design X") questions, pair these with the interview-prep shortcuts in [case_studies/README.md](case_studies/README.md).

| Interview question | Where the answer lives |
|---------------------|------------------------|
| HTTP/1.1 vs HTTP/2 vs HTTP/3 — what specific problem does each generation solve, and how does TLS 1.3 cut handshake round trips? | [HTTP Protocols](http_protocols/) |
| What makes an endpoint idempotent, and why do idempotency keys matter for POST/payment retries? | [REST API Design](rest_api_design/) |
| gRPC vs REST — when do you actually pick gRPC, and what are its four RPC modes? | [gRPC & Protobuf](grpc_and_protobuf/) |
| How do you size a database connection pool (the HikariCP formula), and what causes a pool leak in production? | [Connection Pooling Deep Dive](connection_pooling_deep_dive/) |
| Cache-aside vs write-through vs write-behind — what are the tradeoffs, and what is a cache stampede? | [Caching Strategies Deep Dive](caching_strategies_deep_dive/) |
| How do you correctly size a thread pool, and how do virtual threads change that math? | [Async & Concurrency Patterns](async_and_concurrency_patterns/) |
| How does a B+tree index work, and how does MVCC let readers avoid blocking on writers? | [Database Internals & Indexing](database_internals_and_indexing/) |
| How do you diagnose and fix an N+1 query problem using EXPLAIN ANALYZE? | [Query Optimization](query_optimization/) |
| Why does two-phase commit fail to scale across services, and what problem does the outbox pattern solve? | [Distributed Transactions & Consistency](distributed_transactions_and_consistency/) |
| Explain the circuit breaker's closed/open/half-open states, and why retry backoff always needs jitter. | [Fault Tolerance Patterns](fault_tolerance_patterns/) |
| Token bucket vs sliding window rate limiting — which fits bursty traffic, and how do you enforce it across instances? | [Rate Limiting In Depth](rate_limiting_in_depth/) |
| What's the difference between metrics, logs, and traces, and how do you define an SLO and error budget? | [Observability & Monitoring](observability_and_monitoring/) |
| Walk through preventing SQL injection and SSRF beyond "use prepared statements." | [Backend Security & OWASP](backend_security_owasp/) |
| Explain the OAuth2 authorization code flow with PKCE, and how RBAC differs from ABAC. | [Auth & Authorization Systems](auth_and_authorization_systems/) |
| Choreography vs orchestration — how do you choose for a multi-service workflow? | [Event-Driven Fundamentals](event_driven_fundamentals/) |
| How does Kafka guarantee exactly-once semantics, and what happens when a consumer group rebalances mid-batch? | [Kafka Deep Dive](kafka_deep_dive/) |
| How do you decompose a monolith into microservices using bounded contexts, and what is the strangler fig pattern? | [Microservices Fundamentals](microservices_fundamentals/) |
| What does an API gateway centralize that shouldn't live in every service, and what is a BFF? | [API Gateway Patterns](api_gateway_patterns/) |

---

## Study Plan

A 5-week plan over the Interview-Specific Path. Each week pairs modules with one case study to rehearse the "design X" format.

| Week | Focus | Modules | Case study |
|------|-------|---------|------------|
| 1 | Protocols & API Design | HTTP Protocols, REST API Design, gRPC & Protobuf | [Design a Booking System](case_studies/design_booking_system/) (idempotency keys + optimistic concurrency) |
| 2 | Performance Engineering | Connection Pooling Deep Dive, Caching Strategies Deep Dive, Async & Concurrency Patterns | [Design a Feed Service](case_studies/design_feed_service/) (Redis caching + fan-out under concurrent load) |
| 3 | Database & Distributed Transactions | Database Internals & Indexing, Query Optimization, Distributed Transactions & Consistency | [Design a Payment Processor](case_studies/design_payment_processor/) (Saga orchestration + outbox pattern) |
| 4 | Resilience, Observability & Security | Fault Tolerance Patterns, Rate Limiting In Depth, Observability & Monitoring, Backend Security & OWASP, Auth & Authorization Systems | [Design a Microservices Migration](case_studies/design_microservices_migration/) (circuit breakers, rate limits, and auth translation guard the cutover) |
| 5 | Event-Driven & Microservices Architecture | Event-Driven Fundamentals, Kafka Deep Dive, Microservices Fundamentals, API Gateway Patterns | [Design an Event-Driven Order System](case_studies/design_event_driven_order_system/) (Kafka EOS + CQRS + outbox + DLQ capstone) |

---

## Module Table — 34 Modules

### Phase 1 — Networking Fundamentals (MAJOR DEEP DIVE)

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [OSI Model & Networking](osi_model_and_networking/) | 7 layers, TCP/IP mapping, packet encapsulation, ARP, NAT, MTU | 15 | Intermediate |
| [TCP/IP Deep Dive](tcp_ip_deep_dive/) | 3-way handshake, congestion control, TIME_WAIT, socket tuning | 18 | Advanced |
| [UDP & QUIC](udp_and_quic/) | UDP characteristics, QUIC 0-RTT, HTTP/3, DTLS | 12 | Intermediate |
| [HTTP Protocols](http_protocols/) | HTTP/1.1 vs /2 vs /3, TLS 1.3, ALPN, SNI, HSTS | 15 | Intermediate |

### Phase 2 — API Design & Protocols (DEEP DIVE)

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [REST API Design](rest_api_design/) | REST constraints, versioning, idempotency, pagination, ETag, RFC 7807 | 15 | Intermediate |
| [gRPC & Protobuf](grpc_and_protobuf/) | Protobuf wire format, 4 RPC modes, interceptors, deadlines | 15 | Advanced |
| [GraphQL](graphql/) | Schema design, DataLoader N+1, subscriptions, depth limiting | 12 | Intermediate |
| [WebSockets & SSE](websockets_and_sse/) | WS upgrade, frame structure, SSE, long polling, scaling WS | 12 | Intermediate |

### Phase 3 — Performance Engineering

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [Performance Profiling](performance_profiling/) | async-profiler, JFR, flamegraphs, heap/thread dumps, GC analysis | 15 | Advanced |
| [Connection Pooling Deep Dive](connection_pooling_deep_dive/) | HikariCP internals, pool sizing formula, leak detection, PgBouncer | 15 | Advanced |
| [Caching Strategies Deep Dive](caching_strategies_deep_dive/) | Cache-aside/read-through/write-behind, stampede, Redis structures | 15 | Advanced |
| [Async & Concurrency Patterns](async_and_concurrency_patterns/) | Thread pool sizing, CompletableFuture pitfalls, virtual threads, bulkhead | 15 | Advanced |

### Phase 4 — Database Engineering

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [Database Internals & Indexing](database_internals_and_indexing/) | B+tree, WAL, MVCC, index types, VACUUM, query planner | 15 | Advanced |
| [Query Optimization](query_optimization/) | EXPLAIN ANALYZE, N+1 detection, pagination, batch inserts | 15 | Advanced |
| [Database Migrations](database_migrations/) | Flyway vs Liquibase, zero-downtime patterns, expand-contract | 12 | Intermediate |
| [Distributed Transactions & Consistency](distributed_transactions_and_consistency/) | 2PC problems, Saga, outbox pattern, idempotency keys | 15 | Expert |
| [Database Types Deep Dive](database_types_deep_dive/) | Relational, Document, Key-Value, Wide-Column, Time-Series, Search, Graph, NewSQL — internals, tradeoffs, selection criteria | 18 | Expert |

> For deeper coverage of every database topic above — storage engine internals, NoSQL deep dives, distributed consensus, polyglot persistence, production operations, and 6 end-to-end case studies — see the [Database Engineering](../database/README.md) section (28 modules, 7 phases, principal-engineer level).

### Phase 5 — Resilience & Reliability

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [Fault Tolerance Patterns](fault_tolerance_patterns/) | Circuit breaker states, Resilience4j, retry with jitter, bulkhead | 15 | Advanced |
| [Rate Limiting In Depth](rate_limiting_in_depth/) | Token bucket, sliding window, Redis Lua, adaptive throttling | 12 | Advanced |
| [Observability & Monitoring](observability_and_monitoring/) | Metrics/logs/traces, Micrometer, MDC, OpenTelemetry, SLO/SLI | 15 | Advanced |

### Phase 6 — Security

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [Backend Security & OWASP](backend_security_owasp/) | OWASP Top 10 2021, SQL injection, CSRF, SSRF, secret management | 15 | Advanced |
| [Auth & Authorization Systems](auth_and_authorization_systems/) | JWT internals, OAuth2 flows, OIDC, RBAC vs ABAC, token revocation | 15 | Advanced |

### Phase 7 — Testing & Quality

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [Backend Testing Strategies](backend_testing_strategies/) | Testing pyramid, test doubles, contract testing, mutation testing | 12 | Intermediate |
| [Load & Performance Testing](load_and_performance_testing/) | k6, Gatling, JMeter, percentile analysis, coordinated omission | 12 | Intermediate |
| [Chaos Engineering](chaos_engineering/) | Steady-state hypothesis, fault injection, blast radius, GameDay | 10 | Advanced |

### Phase 8 — Event-Driven Architecture (MAJOR DEEP DIVE)

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [Event-Driven Fundamentals](event_driven_fundamentals/) | Events vs commands, choreography vs orchestration, event storming | 15 | Intermediate |
| [Kafka Deep Dive](kafka_deep_dive/) | Producer/consumer internals, EOS, Kafka Streams, Schema Registry | 18 | Expert |
| [Event Sourcing & CQRS](event_sourcing_and_cqrs/) | Event store, aggregates, snapshots, CQRS read models, Axon | 15 | Expert |
| [Messaging Patterns](messaging_patterns/) | Outbox, inbox, DLQ, poison pill, schema evolution, RabbitMQ vs Kafka | 15 | Advanced |

### Phase 9 — Microservices Architecture (MAJOR DEEP DIVE)

| Module | Topic | Q&As | Difficulty |
|--------|-------|------|------------|
| [Microservices Fundamentals](microservices_fundamentals/) | Decomposition, bounded contexts, data ownership, strangler fig | 15 | Intermediate |
| [API Gateway Patterns](api_gateway_patterns/) | Gateway responsibilities, BFF, Spring Cloud Gateway, Kong | 15 | Advanced |
| [Service Mesh & Service Discovery](service_mesh_and_service_discovery/) | Istio/Envoy, mTLS, service discovery mechanisms, health probes | 15 | Advanced |
| [Distributed System Operational Patterns](distributed_system_operational_patterns/) | Bulkhead, sidecar, ACL, correlation ID, feature flags, graceful shutdown | 15 | Advanced |
| [Container & Deployment Patterns](container_and_deployment_patterns/) | 12-factor, Docker multi-stage, Kubernetes strategies, HPA, PDB | 15 | Advanced |

---

## Case Studies — 5

| Case Study | Scenario | Key Concepts |
|------------|----------|-------------|
| [Design a Booking System](case_studies/design_booking_system/) | Seat reservation under concurrency | Optimistic locking, distributed lock, idempotency |
| [Design a Feed Service](case_studies/design_feed_service/) | Social feed at scale | Fan-out strategies, Redis sorted sets, cursor pagination |
| [Design a Payment Processor](case_studies/design_payment_processor/) | Payment saga with rollback | Saga orchestration, outbox pattern, idempotency keys |
| [Design an Event-Driven Order System](case_studies/design_event_driven_order_system/) | Order lifecycle via Kafka | CQRS, Kafka EOS, transactional outbox, DLQ handling |
| [Design a Microservices Migration](case_studies/design_microservices_migration/) | Strangler fig from monolith | Decomposition steps, shared DB migration, traffic cutover |

---

## Version Matrix

| Technology | Version | Key Changes |
|------------|---------|------------|
| Java | 21 (LTS) | Virtual threads GA, structured concurrency preview, sequenced collections |
| Spring Boot | 3.2+ | Virtual threads GA (spring.threads.virtual.enabled=true), RestClient, problems detail |
| Spring Framework | 6.1 | AOT processing, Micrometer Observation API, HTTP interface clients |
| Kafka | 3.6+ | KRaft mode (no ZooKeeper), tiered storage, new group coordinator |
| Resilience4j | 2.x | Spring Boot 3 compatible, virtual thread support |
| Micrometer | 1.12+ | Observation API, OTLP registry, exemplars |
| OpenTelemetry | 1.x | W3C TraceContext, OTLP protocol, Spring integration |

---

## Cross-Reference Map

| This Module | See Also |
|-------------|---------|
| Async & Concurrency Patterns | [java/concurrency](../java/concurrency/) |
| Connection Pooling Deep Dive | [java/jdbc_and_database](../java/jdbc_and_database/), [spring/spring_data_jpa](../spring/spring_data_jpa/) |
| Distributed Transactions | [spring/spring_transactions](../spring/spring_transactions/) |
| Kafka Deep Dive | [spring/spring_messaging](../spring/spring_messaging/) |
| Auth & Authorization Systems | [spring/spring_security_architecture](../spring/spring_security_architecture/), [spring/spring_security_jwt_oauth](../spring/spring_security_jwt_oauth/) |
| Caching Strategies Deep Dive | [spring/spring_caching](../spring/spring_caching/) |
| Backend Testing Strategies | [spring/spring_testing](../spring/spring_testing/) |
| Query Optimization | [spring/spring_data_jpa](../spring/spring_data_jpa/), [java/jdbc_and_database](../java/jdbc_and_database/) |

---

## Key Production Numbers

Concrete numbers appear throughout modules. Quick reference:

| Topic | Number |
|-------|--------|
| TCP TIME_WAIT | 2*MSL = 60–240s; tcp_tw_reuse=1 to recycle |
| HikariCP pool formula | (core_count * 2) + effective_spindle_count |
| Kafka linger.ms default | 0 (send immediately); recommended: 5–20ms for throughput |
| Kafka batch.size default | 16384 bytes (16 KB) |
| Kafka max.poll.records | 500 |
| Kafka acks default | 1 (leader only); acks=all for durability |
| Circuit breaker defaults | failureRateThreshold=50%, waitDurationInOpenState=60s, ringBufferSize=100 |
| HTTP/2 max concurrent streams | 100 (default SETTINGS_MAX_CONCURRENT_STREAMS) |
| gRPC max message size | 4 MB receive (default); unlimited send |
| Redis default max connections | 10,000 |
| k6 common API SLO | p99 < 200ms |
| Virtual thread stack | ~few KB vs platform thread ~1 MB |
| G1GC default pause target | 200ms |
| ZGC pause time | sub-1ms |
| BCrypt cost factor | 10–12 |
| HikariCP default pool size | 10 |
| Tomcat default threads | 200 |
