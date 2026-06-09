# HLD Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/hld/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

HLD is the architectural overview layer. The `backend/` section contains production-depth implementations; use HLD for the "what and why" and backend for the "how". Cross-reference aggressively.

---

## Module List — 14 Modules

| Module | Key Concepts |
|--------|-------------|
| `scalability/` | Vertical vs horizontal, stateless services, replication, partitioning strategies |
| `load_balancing/` | L4 vs L7, algorithms (round-robin, least-conn, IP hash), health checks, sticky sessions |
| `caching/` | Cache-aside, write-through, TTL, eviction, Redis vs Memcached, CDN as cache |
| `database_design/` | Schema design principles, denormalization, indexing strategy, OLTP vs OLAP |
| `api_design/` | REST maturity levels, versioning, pagination, idempotency, backward compatibility |
| `cap_theorem/` | CAP, PACELC, consistency models, partition tolerance, CP vs AP systems |
| `message_queues/` | Producer-consumer, pub/sub, at-least-once vs exactly-once, DLQ, backpressure |
| `microservices/` | Decomposition, bounded contexts, inter-service communication, data ownership |
| `consistent_hashing/` | Ring, virtual nodes, hotspot prevention, rebalancing cost |
| `rate_limiting/` | Token bucket, sliding window, leaky bucket, distributed rate limiting |
| `cdn/` | Edge caching, cache invalidation, geo-routing, origin shield, cache-control headers |
| `database_sharding/` | Horizontal partitioning, shard key selection, resharding, cross-shard queries |
| `consensus_algorithms/` | Raft leader election + log replication, Paxos variants, PBFT, ZAB, etcd/ZooKeeper internals, quorum sizing, term numbers |
| `event_sourcing_cqrs/` | Event sourcing (append-only log, projections, snapshots), CQRS (command/query separation), Saga pattern, eventual consistency |
| `case_studies/` | HLD case study problems |

---

## Planned / Missing Topics (not yet created)

No topics currently planned. All expected senior engineer topics have been created.

---

## Cross-Reference Map (HLD → Deep Dives)

HLD modules intentionally stay at architectural-overview depth. For implementation details, point readers to:

| HLD Module | Backend Deep-Dive | Database Deep-Dive |
|-----------|------------------|--------------------|
| `microservices/` | `../backend/microservices_fundamentals/`, `../backend/api_gateway_patterns/`, `../backend/service_mesh_and_service_discovery/` | — |
| `message_queues/` | `../backend/kafka_deep_dive/`, `../backend/event_driven_fundamentals/`, `../backend/messaging_patterns/` | — |
| `caching/` | `../backend/caching_strategies_deep_dive/` | `../database/database_caching_patterns/`, `../database/key_value_stores/` |
| `database_design/` | `../backend/database_internals_and_indexing/`, `../backend/query_optimization/` | `../database/README.md` (all 29 modules) |
| `database_sharding/` | — | `../database/sharding_and_partitioning/` |
| `cap_theorem/` | — | `../database/consistency_models_and_consensus/`, `../database/database_fundamentals/` |
| `rate_limiting/` | `../backend/rate_limiting_in_depth/` | — |
| `api_design/` | `../backend/rest_api_design/`, `../backend/grpc_and_protobuf/`, `../backend/graphql/` | — |
| `consistent_hashing/` | — | `../database/sharding_and_partitioning/` |
| `consensus_algorithms/` | `../backend/kafka_deep_dive/` | `../database/consistency_models_and_consensus/` |
| `load_balancing/` | `../backend/fault_tolerance_patterns/` | — |
| `event_sourcing_cqrs/` | `../backend/event_sourcing_and_cqrs/` | `../database/polyglot_persistence_patterns/` |

---

## Adding a New HLD Module

1. Create `<module_name>/README.md` — 14-section template
2. Stay at architectural-overview depth — no framework-specific code
3. Minimum 10 Q&As
4. Add cross-references in the Cross-Reference Map above pointing to backend/ and database/ for deeper dives
5. Update `README.md` HLD module table
6. Update root `README.md` HLD section
7. Update `hld/CLAUDE.md` module table (this file)
