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

**Phase 4 — Apply to Case Studies**:
- [Design Twitter](case_studies/design_twitter.md)
- [Design Netflix](case_studies/design_netflix.md)
- [Design Uber](case_studies/design_uber.md)
- [Design URL Shortener](case_studies/design_url_shortener.md)
- [Design WhatsApp](case_studies/design_whatsapp.md)

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
| Distributed Transactions | [Distributed Transactions & Consistency](../backend/distributed_transactions_and_consistency/README.md) | [Distributed Transactions](../database/distributed_transactions/README.md) |
| Event Sourcing / CQRS | [Event Sourcing & CQRS](../backend/event_sourcing_and_cqrs/README.md) | [Polyglot Persistence Patterns](../database/polyglot_persistence_patterns/README.md) |
