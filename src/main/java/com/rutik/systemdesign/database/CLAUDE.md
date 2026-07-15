# Database Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/database/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 29 Modules (7 Phases)

### Phase 1 — Foundations

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `database_fundamentals/` | Intermediate | 15 | ACID, BASE, CAP, PACELC, isolation levels, MVCC |
| `storage_engines_internals/` | Expert | 17 | B+tree, LSM-tree, WAL, buffer pool, row vs columnar |
| `indexing_deep_dive/` | Advanced | 18 | B+tree, GIN, BRIN, covering, partial, composite, index bloat |
| `concurrency_control_and_locking/` | Advanced | 15 | MVCC, deadlocks, gap locks, SELECT FOR UPDATE, advisory locks |

### Phase 2 — Relational Databases

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `postgresql_internals/` | Expert | 17 | VACUUM, autovacuum, EXPLAIN, TOAST, replication slots, partitioning |
| `mysql_innodb_internals/` | Advanced | 15 | Clustered index, redo/undo log, binary log, online DDL, GTID |
| `sql_query_optimization/` | Advanced | 15 | Join algorithms, CBO statistics, keyset pagination, N+1, window functions |
| `schema_design_and_normalization/` | Intermediate | 16 | Normal forms, temporal data, audit trails, multi-tenancy, JSONB |
| `database_migrations_zero_downtime/` | Intermediate | 16 | Flyway, Liquibase, expand-contract, gh-ost, ADD INDEX CONCURRENTLY |

### Phase 3 — NoSQL Databases

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `document_databases/` | Advanced | 16 | MongoDB WiredTiger, embedding vs referencing, aggregation, sharding, change streams |
| `key_value_stores/` | Expert | 16 | Redis data structures, persistence (RDB/AOF), Cluster, Streams, Redlock |
| `wide_column_databases/` | Advanced | 13 | Cassandra ring, partition key, compaction, consistency levels, tombstones |
| `search_engines/` | Advanced | 13 | Inverted index, BM25, Elasticsearch ILM, aggregations, deep pagination |
| `graph_databases/` | Intermediate | 11 | Property graph, Neo4j index-free adjacency, Cypher, fraud detection |
| `time_series_databases/` | Intermediate | 12 | TimescaleDB, InfluxDB, ClickHouse, Prometheus, Gorilla compression |

### Phase 4 — Emerging Databases

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `vector_databases/` | Advanced | 16 | HNSW, IVF, PQ, pgvector, hybrid search, multi-tenancy, RAG integration |
| `newsql_and_distributed_sql/` | Expert | 15 | Spanner TrueTime, CockroachDB Raft, TiDB, YugabyteDB, global ACID |
| `in_memory_databases/` | Intermediate | 16 | Redis vs Memcached, VoltDB, Ignite, eviction, durability modes |

### Phase 5 — Distributed Database Concepts

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `replication_and_high_availability/` | Expert | 16 | Sync vs async, Patroni, split-brain, replication slots, multi-region |
| `sharding_and_partitioning/` | Expert | 16 | Consistent hashing, shard key selection, Vitess, hotspot, resharding |
| `distributed_transactions/` | Expert | 17 | 2PC, Saga, outbox pattern, idempotency, XA, distributed locks |
| `consistency_models_and_consensus/` | Expert | 15 | Linearizability, Raft, Paxos, CRDTs, vector clocks, fencing tokens |
| `database_caching_patterns/` | Advanced | 16 | Cache-aside, write-through, write-behind, stampede, hot key, invalidation |

### Phase 6 — Production Operations

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `connection_pool_management/` | Advanced | 12 | HikariCP internals, pool sizing, PgBouncer, ProxySQL, K8s connection storm |
| `database_performance_tuning/` | Expert | 14 | shared_buffers, work_mem, checkpoint tuning, lock monitoring, slow queries |
| `backup_recovery_and_disaster_recovery/` | Intermediate | 12 | PITR, WAL-G, pg_basebackup, RPO/RTO, restore drills |
| `database_security_and_compliance/` | Intermediate | 12 | RLS, scram-sha-256, pgAudit, Vault, GDPR erasure, TDE |

### Phase 7 — Architecture & Selection

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `database_selection_framework/` | Expert | 16 | Selection matrix, benchmark traps, TCO, migration risk, vertical vs horizontal |
| `polyglot_persistence_patterns/` | Advanced | 13 | CQRS, CDC (Debezium), dual-write, event sourcing, data mesh |

---

## Learning Paths (Full + Interview-Specific)

`README.md` documents two routes: the **Full Path** (all 29 modules — the 7-phase
order in "## Learning Path — 7 Phases" / "## Phase Diagram (ASCII)") and a curated
**Interview-Specific Path** (16 modules). The interview subset is a **dual-source
list** — it lives in both `README.md` ("## Learning Paths") and `game/app.js`
(`STUDY_PATHS.database.interview`, which drives the game's Study Full/Interview
toggle). **Change one, change the other** — same modules, same order. Non-Q&A
narrative only; no `extract.py` re-run needed. The README also carries a
Knowledge-Question Map and a 6-week Study Plan (interview-readiness prose; no
toggle impact).

---

## Case Studies — 6 Total

`case_studies/` directory. Learning-path index: `case_studies/README.md` (mandatory; update with every new case study).

| Case Study | Key Databases | Level |
|------------|---------------|-------|
| `design_banking_ledger/` | PostgreSQL, Redis | Expert |
| `design_ecommerce_catalog/` | PostgreSQL, Elasticsearch, Redis | Advanced |
| `design_social_media_feed_storage/` | Cassandra, Redis, PostgreSQL | Advanced |
| `design_realtime_analytics_platform/` | ClickHouse, Kafka, Redis | Expert |
| `design_multitenant_saas_database/` | PostgreSQL (RLS), PgBouncer | Advanced |
| `design_monolith_to_polyglot_migration/` | Debezium, dual-write, CDC | Expert |

---

## Cross-Reference Map

| Topic | Primary Module | See Also |
|-------|---------------|----------|
| ACID transactions | `database_fundamentals/` | `distributed_transactions/` |
| B+tree internals | `storage_engines_internals/` | `indexing_deep_dive/`, `postgresql_internals/` |
| Raft consensus | `consistency_models_and_consensus/` | `newsql_and_distributed_sql/`, `replication_and_high_availability/` |
| Outbox pattern | `distributed_transactions/` | `polyglot_persistence_patterns/` |
| Connection pool | `connection_pool_management/` | `../backend/connection_pooling_deep_dive/` |
| Sharding | `sharding_and_partitioning/` | `../hld/database_sharding/` |
| CAP theorem | `database_fundamentals/` | `../hld/cap_theorem/`, `consistency_models_and_consensus/` |
| Cache patterns | `database_caching_patterns/` | `../backend/caching_strategies_deep_dive/`, `key_value_stores/` |
| Vector search | `vector_databases/` | `../llm/embeddings_and_similarity_search/` |
| N+1 query | `sql_query_optimization/` | `../spring/spring_data_jpa/`, `../backend/query_optimization/` |
| Schema migration | `database_migrations_zero_downtime/` | `../backend/database_migrations/` |

---

## Database Version Matrix

| Database | Version |
|----------|---------|
| PostgreSQL | 17 (2024) |
| MySQL | 8.4 (2024 LTS) |
| MongoDB | 7.0 (2023) |
| Redis | 7.2 (2023) |
| Cassandra | 4.1 (2023) |
| Elasticsearch | 8.x (2022+) |
| ClickHouse | 23.x+ |

---

## Adding a New Database Module

1. Create `<module_name>/README.md` — 14-section template
2. Concrete numbers everywhere (query times, memory, sizes)
3. At least 1 BROKEN→FIX block in §10 (Common Pitfalls) and §14 (Case Study)
4. Update `README.md` module table
5. Update root `README.md` Database phase table

## Adding a New Database Case Study

1. Write the case study — 11-section principal template
2. Update `case_studies/README.md` — add to correct phase, update dependency map, add interview prep row
3. Update `README.md` case study count

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
