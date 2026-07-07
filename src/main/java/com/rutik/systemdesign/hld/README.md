# High-Level Design (HLD) — Master Overview

## What is High-Level Design?

High-Level Design (HLD) describes the **overall architecture** of a system — the major components, how they interact, what technologies are used, and how the system satisfies non-functional requirements like scalability, availability, and performance.

HLD answers: *"What are the components and how do they fit together?"*
LLD answers: *"How is each component implemented internally?"*

---

## Intuition

> **One-line analogy**: HLD is like designing a city — deciding where the roads, bridges, and districts go; LLD is like designing a single building within that city.

**Mental model**: A system design interview starts HLD: What are the services? How do they communicate? Where does data live? How do we scale? Once the blueprint is clear, LLD fills in the implementation: what classes exist within each service, what patterns do they use, how are edge cases handled? You can't skip HLD to get to LLD — you'd be designing rooms without knowing if the building is one floor or ten.

**Why it matters**: HLD failures (wrong database, wrong communication model, wrong sharding strategy) are expensive to fix in production. Getting HLD right is a force multiplier on everything that follows.

**Key insight**: Every HLD decision is a tradeoff — consistency vs availability (CAP), latency vs throughput, storage cost vs query speed. The best HLD answers don't just say "use Redis" — they say "use Redis because the access pattern is 95% reads, data fits in memory, and sub-millisecond latency matters here."

---

## HLD vs LLD

| Aspect | HLD | LLD |
|--------|-----|-----|
| Focus | System architecture | Class/module design |
| Audience | Architects, senior engineers | Developers |
| Output | Architecture diagrams, component specs | Class diagrams, sequence diagrams, code |
| Patterns | Sharding, replication, caching, CDN | Design patterns (GoF), SOLID |
| Questions | "What databases?", "How do we scale?" | "What classes?", "What methods?" |

---

## The System Design Interview Framework

Use this 5-step approach in every system design interview:

### Step 1: Clarify Requirements (5 min)
**Functional requirements** — what the system does:
- Who are the users? What actions can they perform?
- What are the core use cases? (focus on top 3-5)
- What are explicit non-goals?

**Non-functional requirements** — how the system performs:
- Scale: DAU, QPS, data size
- Availability: 99.9%? 99.99%?
- Latency: p50, p99 targets
- Consistency: strong vs eventual
- Durability: can we lose data?

### Step 2: Estimate Scale (3 min)
Back-of-envelope calculations:
- **Users**: DAU → concurrent users → peak QPS
- **Storage**: message size × messages/day × retention
- **Bandwidth**: read QPS × response size + write QPS × payload

Quick reference:
| Metric | Value |
|--------|-------|
| 1M requests/day | ~12 requests/sec |
| 1B requests/day | ~12,000 requests/sec |
| 1KB × 1M users | ~1 GB |
| 1KB × 1B users | ~1 TB |
| Video 1GB/hour, compressed ~100MB/hour | — |

### Step 3: High-Level Design (10 min)
Draw the major components and data flow:
- Client(s) → Load Balancer → API Servers
- API Servers → Databases, Caches, Message Queues
- Background workers, CDN, search indexes

### Step 4: Deep Dive (15-20 min)
Pick 2-3 components to go deep on (interviewer often guides):
- Core algorithm (feed ranking, URL encoding, driver matching)
- Database schema and access patterns
- Bottleneck identification and solutions
- Failure modes and recovery

### Step 5: Wrap Up (5 min)
- Monitoring and alerting strategy
- Failure scenarios and mitigations
- Future scaling (10x traffic)
- What you would do differently

---

## Repository Contents

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [Scalability](scalability/README.md) | Horizontal vs vertical, stateless services, replication | Beginner |
| [Load Balancing](load_balancing/README.md) | Round-robin, consistent hash, L4 vs L7, health checks | Beginner |
| [Caching](caching/README.md) | Redis, Memcached, cache-aside, write-through, invalidation | Intermediate |
| [Database Design](database_design/README.md) | ACID, CAP, SQL vs NoSQL, indexing, normalization | Intermediate |
| [API Design](api_design/README.md) | REST, GraphQL, gRPC, versioning, rate limiting | Intermediate |
| [CAP Theorem](cap_theorem/README.md) | Consistency, availability, partition tolerance | Intermediate |
| [Message Queues](message_queues/README.md) | Kafka, RabbitMQ, pub/sub, delivery guarantees | Intermediate |
| [Microservices](microservices/README.md) | Service discovery, circuit breaker, saga, CQRS | Advanced |
| [Consistent Hashing](consistent_hashing/README.md) | Hash ring, virtual nodes, Dynamo-style | Advanced |
| [Rate Limiting](rate_limiting/README.md) | Token bucket, sliding window, distributed | Intermediate |
| [CDN](cdn/README.md) | Edge caching, push vs pull, TTL, geographic routing | Intermediate |
| [Database Sharding](database_sharding/README.md) | Shard key, range vs hash sharding, resharding | Advanced |
| [Consensus Algorithms](consensus_algorithms/README.md) | Raft leader election + log replication, Paxos variants, PBFT, etcd/ZooKeeper, fencing tokens | Advanced |
| [Event Sourcing & CQRS](event_sourcing_cqrs/README.md) | Event sourcing (append-only log, projections, snapshots), CQRS (command/query separation), Saga pattern, eventual consistency | Advanced |
| [Distributed Transactions](distributed_transactions/README.md) | 2PC, 3PC, Saga (orchestration vs choreography), TCC, outbox pattern, idempotency keys | Advanced |
| [Observability](observability/README.md) | Three pillars (metrics, logs, traces), RED/USE methods, SLI/SLO/error budgets, cardinality, distributed tracing | Intermediate |
| [Security and Authentication/Authorization](security_and_auth/README.md) | AuthN vs AuthZ, OAuth2/OIDC, JWT vs sessions, mTLS, RBAC vs ABAC, encryption at rest/in transit | Intermediate |
| [Resilience Patterns](resilience_patterns/README.md) | Circuit breaker, bulkhead, retry with backoff + jitter, timeouts, graceful degradation, failover | Intermediate |

---

## Recommended Learning Order

**Phase 1 — Foundations** (start here):
1. Scalability — understand why distributed systems are needed
2. Load Balancing — how traffic is distributed
3. Caching — the most impactful optimization
4. Database Design — SQL vs NoSQL decisions

**Phase 2 — Core Concepts**:
5. CAP Theorem — the fundamental tradeoff
6. API Design — how components communicate
7. Message Queues — async communication patterns
8. Rate Limiting — protecting your system

**Phase 3 — Advanced Topics**:
9. CDN — global content delivery
10. Consistent Hashing — elegant distributed data placement
11. Database Sharding — scaling writes
12. Microservices — large-scale architecture
13. Distributed Transactions — consistency across service boundaries (2PC, Saga, TCC, outbox)
14. Observability — instrumenting and operating what you've built (metrics, logs, traces, SLOs)
15. Security and Authentication/Authorization — securing the system end-to-end
16. Resilience Patterns — designing for graceful failure (circuit breakers, bulkheads, retries)
17. Consensus Algorithms — Raft/Paxos leader election, log replication, quorum sizing, etcd/ZooKeeper
18. Event Sourcing & CQRS — append-only event log, projections, snapshots, command/query separation

**Phase 4 — Apply to Case Studies**:
- [Design Twitter](case_studies/design_twitter.md)
- [Design Netflix](case_studies/design_netflix.md)
- [Design Uber](case_studies/design_uber.md)
- [Design URL Shortener](case_studies/design_url_shortener.md)
- [Design WhatsApp](case_studies/design_whatsapp.md)
- [Design a Notification System](case_studies/design_notification_system.md)
- [Design a Payment System](case_studies/design_payment_system.md)
- [Design Google Docs](case_studies/design_google_docs.md)
- [Design a Web Crawler](case_studies/design_web_crawler.md)
- [Design Search Autocomplete (Typeahead)](case_studies/design_search_autocomplete.md)
- [Design Google Maps](case_studies/design_google_maps.md)
- [Design a Distributed Key-Value Store](case_studies/design_key_value_store.md)
- [Design a Distributed Unique ID Generator](case_studies/design_distributed_unique_id.md)
- [Design a Distributed Message Queue](case_studies/design_distributed_message_queue.md)
- [Design Object Storage (S3)](case_studies/design_object_storage_s3.md)
- [Design a Proximity Service](case_studies/design_proximity_service.md)
- [Design an Ad Click Event Aggregation System](case_studies/design_ad_click_aggregation.md)
- [Design a Leaderboard](case_studies/design_leaderboard.md)
- [Design a Digital Wallet](case_studies/design_digital_wallet.md)
- [Design a Stock Exchange](case_studies/design_stock_exchange.md)
- [Design a Hotel Reservation System](case_studies/design_hotel_reservation.md)
- [Design a Metrics Monitoring System](case_studies/design_metrics_monitoring.md)
- [Design Google Drive](case_studies/design_google_drive.md)

See [case_studies/README.md](case_studies/README.md) for the full learning path, cross-cutting primitives map, and "Design X" interview shortcuts across all 23 case studies.

---

## Learning Paths

This section is exhaustive by design — 18 modules spanning the full architectural-overview curriculum, from scalability fundamentals through consensus algorithms and event sourcing. That is the right depth for a reference and the wrong shape for someone two weeks from an interview. So there are **two ways through it**; the browser learning game's **Study** view surfaces both as a **Full / Interview** toggle (Full is the default).

### Full Path (18 modules)

The complete curriculum in the order above — see [Recommended Learning Order](#recommended-learning-order). Use it for genuine mastery: all four phases, including the advanced-topics row (consensus algorithms, event sourcing/CQRS, observability, security and auth) and the full run of 23 case studies. Nothing is dropped.

### Interview-Specific Path (14 modules)

A ruthless cut to what a **senior system design interview** actually probes — the building blocks that assemble into nearly every "design X at scale" question. Same relative order as the Full Path, 4 modules dropped. Each group below says why it earns interview time.

| Group | Modules | Why it's tested |
|-------|---------|-----------------|
| Foundations | [Scalability](scalability/README.md), [Load Balancing](load_balancing/README.md), [Caching](caching/README.md), [Database Design](database_design/README.md) | The vocabulary every interview opens with — horizontal vs vertical scaling, traffic distribution, cache-aside/write-through, and SQL vs NoSQL schema tradeoffs |
| Core Tradeoffs & Communication | [CAP Theorem](cap_theorem/README.md), [API Design](api_design/README.md), [Message Queues](message_queues/README.md) | The theorem you must state correctly (CP vs AP, PACELC), how services talk synchronously (REST/gRPC/GraphQL, versioning), and asynchronously (delivery guarantees, DLQ, backpressure) |
| Protecting the System | [Rate Limiting](rate_limiting/README.md), [CDN](cdn/README.md) | The two standard "how do you protect and scale reads at the edge" follow-ups — token bucket vs sliding window, push vs pull edge caching |
| Distributed Data Placement | [Consistent Hashing](consistent_hashing/README.md), [Database Sharding](database_sharding/README.md) | The standard answer to "how do you scale writes past one machine" — hash ring + virtual nodes, shard-key selection, resharding cost |
| Large-Scale Architecture | [Microservices](microservices/README.md) | Service decomposition, bounded contexts, and data ownership — the senior-level "monolith vs microservices, and why" discussion |
| Cross-Service Consistency & Failure Handling | [Distributed Transactions](distributed_transactions/README.md), [Resilience Patterns](resilience_patterns/README.md) | 2PC/Saga/TCC/outbox for consistency across service boundaries, plus circuit breakers/bulkheads/retries for the "what if this call fails" probe every interviewer asks |

**Deliberately deferred to the Full Path** (valuable, lower interview yield): [Observability](observability/README.md), [Security and Authentication/Authorization](security_and_auth/README.md), [Consensus Algorithms](consensus_algorithms/README.md), and [Event Sourcing & CQRS](event_sourcing_cqrs/README.md). These matter a great deal in production, and do surface in senior/staff loops or infra-focused teams, but they are depth beyond the baseline gate. A niche flagged in an interview (e.g. "have you worked with Raft, or event sourcing?") is a bonus, not a gate — reach for these once the 14 above are solid.

---

## Knowledge-Question Map

The highest-frequency HLD interview questions mapped to the module that answers them. For *system design* ("design X") questions, use the interview-prep shortcuts in [case_studies/README.md](case_studies/README.md).

| Interview question | Where the answer lives |
|--------------------|------------------------|
| Horizontal vs vertical scaling — why does horizontal scaling dominate at internet scale? | [Scalability](scalability/README.md) |
| Round-robin vs least-connections vs consistent-hash balancing — when does each break down? | [Load Balancing](load_balancing/README.md) |
| L4 vs L7 load balancing — what can an L7 balancer inspect that L4 cannot? | [Load Balancing](load_balancing/README.md) |
| Cache-aside vs write-through vs write-behind — what are the consistency and latency tradeoffs? | [Caching](caching/README.md) |
| How do you avoid a thundering herd on cache expiry or invalidation? | [Caching](caching/README.md) |
| SQL vs NoSQL — what access-pattern signals push you toward each? | [Database Design](database_design/README.md) |
| Normalization vs denormalization — when do you trade write complexity for read speed? | [Database Design](database_design/README.md) |
| State the CAP theorem precisely — why can't a partitioned system have both consistency and availability? | [CAP Theorem](cap_theorem/README.md) |
| What is PACELC, and how does it extend CAP to the no-partition case? | [CAP Theorem](cap_theorem/README.md) |
| REST vs gRPC vs GraphQL — how do you choose for a given API? | [API Design](api_design/README.md) |
| How do you version an API without breaking existing clients? | [API Design](api_design/README.md) |
| At-least-once vs exactly-once vs at-most-once delivery — how do message queues implement each? | [Message Queues](message_queues/README.md) |
| Token bucket vs sliding window vs leaky bucket — how do they behave under bursty traffic? | [Rate Limiting](rate_limiting/README.md) |
| Push vs pull CDN, and how does edge cache invalidation work? | [CDN](cdn/README.md) |
| How does consistent hashing avoid remapping every key when a node joins or leaves the ring? | [Consistent Hashing](consistent_hashing/README.md) |
| Range vs hash sharding — what does each do to hot-shard risk and range queries? | [Database Sharding](database_sharding/README.md) |
| How do you reshard a live system without downtime? | [Database Sharding](database_sharding/README.md) |
| How do you decompose a monolith into microservices — what defines a bounded context? | [Microservices](microservices/README.md) |
| 2PC vs Saga vs TCC — how do you keep data consistent across service boundaries? | [Distributed Transactions](distributed_transactions/README.md) |
| Circuit breaker vs bulkhead vs retry-with-backoff — which failure mode does each defend against? | [Resilience Patterns](resilience_patterns/README.md) |

---

## Study Plan

A 6-week plan over the Interview-Specific Path. Each week pairs modules with one case study to rehearse the "design X" format.

| Week | Focus | Modules | Case study |
|------|-------|---------|------------|
| 1 | Foundations | Scalability, Load Balancing, Caching, Database Design | [URL Shortener](case_studies/design_url_shortener.md) |
| 2 | Core Tradeoffs & Communication | CAP Theorem, API Design, Message Queues | [Distributed Message Queue](case_studies/design_distributed_message_queue.md) |
| 3 | Protecting the System | Rate Limiting, CDN | [Netflix](case_studies/design_netflix.md) |
| 4 | Distributed Data Placement | Consistent Hashing, Database Sharding | [Distributed Key-Value Store](case_studies/design_key_value_store.md) |
| 5 | Large-Scale Architecture | Microservices | [Uber](case_studies/design_uber.md) |
| 6 | Cross-Service Consistency & Failure Handling | Distributed Transactions, Resilience Patterns | [Payment System](case_studies/design_payment_system.md) |

---

## Key Tradeoffs in System Design

| Decision | Option A | Option B | Deciding Factor |
|----------|----------|----------|-----------------|
| Consistency vs Availability | Strong consistency (SQL) | High availability (NoSQL) | Can users see stale data? |
| Read vs Write optimization | Read replicas | Write sharding | Read-heavy vs write-heavy |
| Sync vs Async | Synchronous API | Message queue | Latency tolerance |
| Push vs Pull | Push notifications | Polling | Frequency, battery, complexity |
| Monolith vs Microservices | Monolith | Microservices | Team size, domain complexity |
| SQL vs NoSQL | Relational DB | Document/KV store | Schema flexibility, scale |
| Cache-aside vs Write-through | Cache-aside | Write-through | Write frequency, consistency need |
| CDN Push vs Pull | Push (pre-populate) | Pull (lazy load) | Content predictability |

---

## Non-Functional Requirements Cheatsheet

### Availability
| SLA | Downtime/Year | Downtime/Month |
|-----|--------------|----------------|
| 99% | 3.65 days | 7.2 hours |
| 99.9% | 8.7 hours | 43 minutes |
| 99.99% | 52 minutes | 4.4 minutes |
| 99.999% | 5.2 minutes | 26 seconds |

### Latency Targets (typical)
- User-facing APIs: p99 < 200ms
- Internal services: p99 < 50ms
- Database queries: p99 < 10ms
- Cache lookups: p99 < 1ms

### Storage Estimates
```
1 million users × 1 KB profile = 1 GB
1 billion users × 1 KB profile = 1 TB
1 million tweets/day × 300 bytes = 300 MB/day = ~100 GB/year
1 million photos/day × 1 MB = 1 TB/day
```

### QPS Estimates
```
1 million requests/day  ÷ 86,400 ≈ 12 RPS
10 million requests/day ÷ 86,400 ≈ 120 RPS
1 billion requests/day  ÷ 86,400 ≈ 12,000 RPS
Peak = 2-3× average
```

---

## Common Interview Mistakes

1. **Jumping to solutions** — clarify requirements first
2. **Ignoring scale** — always estimate before designing
3. **Not discussing tradeoffs** — every decision has costs
4. **Single point of failure** — always ask "what if X fails?"
5. **Over-engineering** — match the solution to the scale
6. **Forgetting monitoring** — observability is part of the design
7. **Ignoring security** — authentication, authorization, encryption

---

## Deep-Dive Companions

HLD stays at architectural-overview depth. For production-grade implementation details, each HLD concept maps to a richer companion in `backend/` or `database/`:

| HLD Concept | Backend Deep-Dive | Database Deep-Dive |
|-------------|------------------|--------------------|
| Microservices | [Microservices Fundamentals](../backend/microservices_fundamentals/README.md), [API Gateway Patterns](../backend/api_gateway_patterns/README.md), [Service Mesh](../backend/service_mesh_and_service_discovery/README.md) | — |
| Message Queues | [Kafka Deep Dive](../backend/kafka_deep_dive/README.md), [Event-Driven Fundamentals](../backend/event_driven_fundamentals/README.md), [Messaging Patterns](../backend/messaging_patterns/README.md) | — |
| Caching | [Caching Strategies Deep Dive](../backend/caching_strategies_deep_dive/README.md) | [Database Caching Patterns](../database/database_caching_patterns/README.md), [Key-Value Stores](../database/key_value_stores/README.md) |
| Database Design | [Database Internals & Indexing](../backend/database_internals_and_indexing/README.md), [Query Optimization](../backend/query_optimization/README.md) | [Database Engineering (all 29 modules)](../database/README.md) |
| Database Sharding | — | [Sharding & Partitioning](../database/sharding_and_partitioning/README.md) |
| CAP Theorem | — | [Consistency Models & Consensus](../database/consistency_models_and_consensus/README.md), [Database Fundamentals](../database/database_fundamentals/README.md) |
| Consensus Algorithms | [Kafka Deep Dive](../backend/kafka_deep_dive/README.md) | [Consistency Models & Consensus](../database/consistency_models_and_consensus/README.md) |
| Rate Limiting | [Rate Limiting In Depth](../backend/rate_limiting_in_depth/README.md) | — |
| API Design | [REST API Design](../backend/rest_api_design/README.md), [gRPC & Protobuf](../backend/grpc_and_protobuf/README.md), [GraphQL](../backend/graphql/README.md) | — |
| Consistent Hashing | — | [Sharding & Partitioning](../database/sharding_and_partitioning/README.md) |
| [Distributed Transactions](distributed_transactions/README.md) | [Distributed Transactions & Consistency](../backend/distributed_transactions_and_consistency/README.md) | [Distributed Transactions](../database/distributed_transactions/README.md) |
| Event Sourcing / CQRS | [Event Sourcing & CQRS](../backend/event_sourcing_and_cqrs/README.md) | [Polyglot Persistence Patterns](../database/polyglot_persistence_patterns/README.md) |
| [Observability](observability/README.md) | [Observability & Monitoring](../backend/observability_and_monitoring/README.md), [Observability: Tracing & OTel](../devops/observability_tracing_and_otel/README.md), [Observability: Metrics (Prometheus)](../devops/observability_metrics_prometheus/README.md), [Observability: Logging](../devops/observability_logging/README.md) | — |
| [Security and Authentication/Authorization](security_and_auth/README.md) | [Auth & Authorization Systems](../backend/auth_and_authorization_systems/README.md), [Backend Security & OWASP](../backend/backend_security_owasp/README.md), [Spring Security Architecture](../spring/spring_security_architecture/README.md), [Spring Security: JWT & OAuth2](../spring/spring_security_jwt_oauth/README.md) | — |
| [Resilience Patterns](resilience_patterns/README.md) | [Fault Tolerance Patterns](../backend/fault_tolerance_patterns/README.md), [Resilience4j Patterns](../spring/case_studies/cross_cutting/resilience4j_patterns.md) | — |
