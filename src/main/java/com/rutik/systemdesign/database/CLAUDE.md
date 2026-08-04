# Database Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/database/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 30 Modules (7 Phases)

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
| `schema_design_and_normalization/` | Intermediate | 16 | Normal forms, temporal data, audit trails, multi-tenancy, JSONB — sub-file: `surrogate_vs_natural_keys` |
| `database_migrations_zero_downtime/` | Intermediate | 16 | Flyway, Liquibase, expand-contract, gh-ost, ADD INDEX CONCURRENTLY |

### Phase 3 — NoSQL Databases

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `document_databases/` | Advanced | 16 | MongoDB WiredTiger, embedding vs referencing, aggregation, sharding, change streams |
| `key_value_stores/` | Expert | 17 | Redis data structures, persistence (RDB/AOF), Cluster, Streams, Redlock — the CATEGORY; Redis product internals belong in `redis_internals/` |
| `redis_internals/` | Expert | 28 | SDS/listpack/quicklist/intset/dict/skiplist encodings, `ae` loop + `io-threads`, the eight eviction policies + approximated LRU/LFU, jemalloc fragmentation, RDB + multi-part AOF, fork/CoW + THP, `PSYNC`/backlog/`replid2`, live resharding + `ASK`, locks and fencing, Functions vs `EVALSHA`, Redis 8 vs Valkey 9 |
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

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 30 modules — the 7-phase order in
"## Learning Path — 7 Phases" / "## Phase Diagram (ASCII)") plus two curated tiers:
**Senior** (20 modules) and **Principal** (13). They are different cuts, not nested
depths — senior is the craft, principal is the judgment (which engine at what cost,
what migration, what you tell a team *not* to do), so principal is usually the smaller
list and much of it is material senior never sees. Membership is declared ONCE per
module, in a `<!-- study-paths -->` block in that module's own page (`<module>.md`) naming the files
each tier takes; listing a tier joins it, omitting the tier opts out, and the module page
(`<module>.md`) must always be listed. Order is never declared — it comes from `STUDY_ORDER.database` in
`game/app.js`, so a tier is an ordered subset by construction. **There is no path array
in `app.js` to edit**: `extract.py` walks the markers and emits the gitignored
`questions/paths.json`, which the game fetches at boot. The tier tables in `README.md`
sit between `<!-- study-path-table <tier> -->` markers and are **generated** —
regenerate with `python3 game/extract.py --write-paths`; a hand-edited or stale block
fails `extract.py --strict` and the Pages deploy. Case studies are tiered the same way
from a block in `case_studies/case_studies.md` (3 senior / 3 principal), driving the Level
filter on the game's Case Studies tab. The README also carries a Knowledge-Question Map
and a 6-week Study Plan (prose; no path impact).

---

## Case Studies — 6 Total

`case_studies/` directory. Learning-path index: `case_studies/case_studies.md` (mandatory; update with every new case study).

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
| Cache patterns | `database_caching_patterns/` | `../backend/caching_strategies_deep_dive/`, `key_value_stores/`, `redis_internals/` |
| Redis internals | `redis_internals/` | `key_value_stores/`, `in_memory_databases/`, `database_caching_patterns/` |
| Vector search | `vector_databases/` | `../llm/embeddings_and_similarity_search/`, `../llm/faiss_deep_dive/` — the in-process library and the library-vs-service argument |
| N+1 query | `sql_query_optimization/` | `../spring/spring_data_jpa/`, `../backend/query_optimization/` |
| Schema migration | `database_migrations_zero_downtime/` | `../backend/database_migrations/` |

---

## Database Version Matrix

| Database | Version |
|----------|---------|
| PostgreSQL | 18 (2025) |
| MySQL | 9.7 (2026 LTS) |
| MongoDB | 8.3 (2026) |
| Redis | 8.10 (2026) |
| Valkey | 9.1 (2026) |
| Cassandra | 5.0 (2024) |
| Elasticsearch | 9.x (2025+) |
| ClickHouse | 26.x (2026) |

---

## Adding a New Database Module

1. Create `<module_name>/<module_name>.md` — 14-section template
2. Concrete numbers everywhere (query times, memory, sizes)
3. At least 1 BROKEN→FIX block in §10 (Common Pitfalls) and §14 (Case Study)
4. Update `README.md` module table
5. Add the module dir to `STUDY_ORDER.database` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
6. Write a `<!-- study-paths -->` block at the top of the new module's page (`<module_name>.md`) naming the tiers it
   belongs to (or none, for Full-path-only). Every tier line must list `<module_name>.md`
   itself — the module page is never optional and omitting it is fatal under `--strict`, then run `python3 game/extract.py --write-paths`
   to regenerate the section README's tier tables
7. Update root `README.md` Database phase table

## Adding a New Database Case Study

1. Write the case study — 11-section principal template
2. Update `case_studies/case_studies.md` — add to correct phase, update dependency map, add interview prep row
3. If it belongs in a tier, add its `<dir>/<dir>.md` to that tier's line in the
   `<!-- study-paths -->` block at the top of `case_studies/case_studies.md`
4. Update `README.md` case study count

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
