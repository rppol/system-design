# Database Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/database/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 34 Modules (7 Phases)

### Phase 1 — Foundations

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `database_fundamentals/` | Intermediate | 18 | ACID, BASE, CAP, PACELC, isolation levels, MVCC |
| `storage_engines_internals/` | Expert | 20 | B+tree, LSM-tree, WAL, buffer pool, row vs columnar |
| `indexing_deep_dive/` | Advanced | 19 | B+tree, GIN, BRIN, covering, partial, composite, index bloat |
| `concurrency_control_and_locking/` | Advanced | 18 | MVCC, deadlocks, gap locks, SELECT FOR UPDATE, advisory locks |

### Phase 2 — Relational Databases

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `postgresql_internals/` | Expert | 18 | VACUUM, autovacuum, EXPLAIN, TOAST, replication slots, partitioning; sub-file: `planner_and_parallel_execution` |
| `mysql_innodb_internals/` | Advanced | 15 | Clustered index, redo/undo log, binary log, online DDL, GTID |
| `sql_query_optimization/` | Advanced | 16 | Join algorithms, CBO statistics, keyset pagination, N+1, window functions |
| `schema_design_and_normalization/` | Intermediate | 16 | Normal forms, temporal data, audit trails, multi-tenancy, JSONB — sub-file: `surrogate_vs_natural_keys` |
| `database_migrations_zero_downtime/` | Intermediate | 19 | Flyway, Liquibase, expand-contract, gh-ost, ADD INDEX CONCURRENTLY |

### Phase 3 — NoSQL Databases

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `document_databases/` | Advanced | 20 | MongoDB WiredTiger, embedding vs referencing, aggregation, sharding, change streams |
| `key_value_stores/` | Expert | 20 | Redis data structures, persistence (RDB/AOF), Cluster, Streams, Redlock — the CATEGORY; Redis product internals belong in `redis_internals/` |
| `redis_internals/` | Expert | 28 | SDS/listpack/quicklist/intset/dict/skiplist encodings, `ae` loop + `io-threads`, the ten eviction policies (`allkeys-lrm`/`volatile-lrm` from 8.6) + approximated LRU/LFU, jemalloc fragmentation, RDB + multi-part AOF, fork/CoW + THP, `PSYNC`/backlog/`replid2`, live resharding + `ASK`, locks and fencing, Functions vs `EVALSHA`, vector sets (`VADD`/`VSIM`, HNSW + filtered ANN), Redis 8 vs Valkey 9 |
| `wide_column_databases/` | Advanced | 22 | Cassandra ring, partition key, compaction (STCS/LCS/TWCS/UCS), consistency levels, tombstones, SAI vs legacy 2i, `VECTOR` + ANN, trie memtables/BTI, Accord `[6.0-alpha]`, HBase/Bigtable lineage, ScyllaDB shard-per-core |
| `search_engines/` | Advanced | 16 | Inverted index, BM25, Elasticsearch ILM, aggregations, deep pagination |
| `elasticsearch_internals/` | Expert | 38 | Lucene segments and file extensions, refresh/flush/merge, translog durability, block-tree term index, inlined skip data, doc values vs stored vs `_source`, BM25 norms, per-shard IDF, PIT + `search_after`, routing factor and `_split`, seq-no/primary-term checkpoints, voting configuration, `doc_count_error_upper_bound`, logsdb/TSDS, frozen tier, `dense_vector` BBQ — the PRODUCT internals; the category lives in `search_engines/` |
| `graph_databases/` | Intermediate | 21 | Property graph, Neo4j index-free adjacency, Cypher 25, GQL (ISO/IEC 39075:2024), SQL/PGQ (Oracle AI Database 26ai; reverted from PostgreSQL 19 before release), fraud detection; sub-file: `graph_query_languages` |
| `time_series_databases/` | Intermediate | 21 | TimescaleDB, InfluxDB, ClickHouse, Prometheus, Gorilla compression |
| `analytical_databases_and_olap/` | Advanced | 18 | Columnar storage, vectorized execution, late materialization, MergeTree parts and the part-count cliff, sparse index granules, sort key vs index, ClickHouse/Druid/Pinot vs warehouses vs DuckDB, HTAP and zero-ETL |

### Phase 4 — Emerging Databases

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `vector_databases/` | Advanced | 18 | HNSW, IVF, PQ, pgvector, hybrid search, multi-tenancy, RAG integration; sub-file: `disk_ann_and_filtered_search` |
| `newsql_and_distributed_sql/` | Expert | 15 | Spanner TrueTime, CockroachDB Raft, TiDB, YugabyteDB, global ACID |
| `in_memory_databases/` | Intermediate | 16 | Redis vs Memcached, Volt Active Data, Ignite, eviction, durability modes |
| `embedded_and_edge_databases/` | Advanced | 20 | SQLite (WAL, single-writer, `busy_timeout`), DuckDB in-process OLAP, RocksDB/LMDB as libraries, litestream/LiteFS replication, local-first sync and CRDT reconciliation, edge read replicas |
| `lakehouse_and_table_formats/` | Expert | 16 | Metadata-pointer CAS commit, snapshot isolation, time travel and retention cost, column-ID schema evolution, hidden partitioning, CoW vs MoR, deletion vectors, small-file economics, Iceberg vs Delta vs Hudi, the catalog tier |

### Phase 5 — Distributed Database Concepts

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `replication_and_high_availability/` | Expert | 20 | Sync vs async, Patroni, split-brain, replication slots, multi-region |
| `sharding_and_partitioning/` | Expert | 17 | Consistent hashing, shard key selection, Vitess, hotspot, resharding |
| `distributed_transactions/` | Expert | 20 | 2PC, Saga, outbox pattern, idempotency, XA, distributed locks, Percolator/Spanner/CockroachDB Parallel Commits/Calvin |
| `consistency_models_and_consensus/` | Expert | 19 | Linearizability, Raft, Paxos, CRDTs, vector clocks, fencing tokens |
| `database_caching_patterns/` | Advanced | 19 | Cache-aside, write-through, write-behind, stampede, hot key, invalidation |

### Phase 6 — Production Operations

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `connection_pool_management/` | Advanced | 19 | HikariCP internals, pool sizing, PgBouncer, ProxySQL, K8s connection storm |
| `database_performance_tuning/` | Expert | 16 | shared_buffers, work_mem, checkpoint tuning, lock monitoring, slow queries; sub-file: `wait_events_and_benchmarking` |
| `backup_recovery_and_disaster_recovery/` | Intermediate | 18 | PITR, WAL-G, pg_basebackup, RPO/RTO, restore drills |
| `database_security_and_compliance/` | Intermediate | 17 | RLS, scram-sha-256, pgAudit, Vault, GDPR erasure, TDE |

### Phase 7 — Architecture & Selection

| Module | Level | Q&As | Key Concepts |
|--------|-------|------|--------------|
| `database_selection_framework/` | Expert | 20 | Selection matrix, benchmark traps, TCO, migration risk, vertical vs horizontal |
| `polyglot_persistence_patterns/` | Advanced | 16 | CQRS, CDC (Debezium), dual-write, event sourcing, data mesh |

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 34 modules — the 7-phase order in
"## Learning Path — 7 Phases" / "## Phase Diagram (ASCII)") plus two curated tiers:
**Senior** (24 modules) and **Principal** (16). They are different cuts, not nested
depths — senior is the craft, principal is the judgment (which engine at what cost,
what migration, what you tell a team *not* to do), so principal is usually the smaller
list and much of it is material senior never sees. Membership is declared ONCE per section, the `<!-- study-paths -->` block in this section's `README.md`, naming every module, every file it owns, and the tiers each file
each tier takes; listing a tier joins it, omitting the tier opts out, and the module page
(`<module>.md`) must always be listed. Order is never declared — it comes from `STUDY_ORDER.database` in
`game/app.js`, so a tier is an ordered subset by construction. **There is no path array
in `app.js` to edit**: `extract.py` walks the markers and emits the gitignored
`questions/paths.json`, which the game fetches at boot. The tier tables in `README.md`
sit between `<!-- study-path-table <tier> -->` markers and are **generated** —
regenerate with `python3 game/extract.py --write-paths`; a hand-edited or stale block
fails `extract.py --strict` and the Pages deploy. Case studies are tiered the same way
in the same `README.md` block (6 senior / 7 principal), driving the Level
filter on the game's Case Studies tab. The README also carries a Knowledge-Question Map
and a 6-week Study Plan (prose; no path impact).

---

## Case Studies — 11 Total

`case_studies/` directory. Learning-path index: `case_studies/case_studies.md` (mandatory; update with every new case study).

| Case Study | Key Databases | Level |
|------------|---------------|-------|
| `design_banking_ledger/` | PostgreSQL, Redis | Expert |
| `design_ecommerce_catalog/` | PostgreSQL, Elasticsearch, Redis | Advanced |
| `design_social_media_feed_storage/` | Cassandra, Redis, PostgreSQL | Advanced |
| `design_realtime_analytics_platform/` | ClickHouse, Kafka, Redis | Expert |
| `design_multitenant_saas_database/` | PostgreSQL (RLS), PgBouncer | Advanced |
| `design_monolith_to_polyglot_migration/` | Debezium, dual-write, CDC | Expert |
| `design_global_multiregion_oltp/` | CockroachDB, Spanner | Expert |
| `design_vector_search_platform/` | pgvector, Qdrant, Elasticsearch | Expert |
| `design_chat_message_store/` | Cassandra, PostgreSQL, Redis | Advanced |
| `design_job_queue_on_postgres/` | PostgreSQL (`SKIP LOCKED`) | Advanced |
| `design_10tb_table_migration/` | PostgreSQL, gh-ost, pg_repack | Expert |

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
| PostgreSQL | 18 (2025) — 19 RC1 on 15 Oct 2026, GA planned 19 Nov 2026; SQL/PGQ was reverted from 19 on 7 Sept 2026 |
| MySQL | 9.7 LTS (2026) — the last sequential version; Innovation moved to `YY.M` calendar versioning at 26.7 (July 2026). Oracle says future LTS lines will use CalVer too, but none has shipped — treat that half as forward-looking |
| MongoDB | 9.0 (9.0.0 on 28 Sept 2026, 9.0.4 on 13 Oct 2026); 8.1/8.2/8.3 were Rapid Releases |
| Redis | 8.10 (2026) |
| Valkey | 9.1 (2026) |
| Cassandra | 5.0 (Sept 2024 GA); 6.0 is alpha only — Accord, not production-ready |
| ScyllaDB | separate C++ shard-per-core codebase, Cassandra wire-compatible — not a Cassandra version |
| Neo4j | 2026.09 (21 Sept 2026; calendar versioning since 2025.01); Cypher 25 is the shipped default from 2026.02 |
| GQL | ISO/IEC 39075:2024 (April 2024) — the ISO standard graph query language |
| Elasticsearch | 9.5 (9.5.0 on 4 Aug 2026, latest patch 9.5.5), Lucene 10.5 — OpenSearch 3.9.0 (29 Sept 2026), forked at 7.10.2 |
| ClickHouse | 26.x (2026) — latest stable 26.9.21.9 (17 Oct 2026); newest LTS 26.8.30.8 (17 Oct 2026) |
| Apache Druid | 38.0.0 (1 Oct 2026); Java 17 no longer supported |
| DuckDB | 1.5.6 (28 Sept 2026); 2.0 is feature-frozen and unreleased |
| Apache Arrow | 26.0.0 (9 Oct 2026) |

---

## Adding a New Database Module

1. Create `<module_name>/<module_name>.md` — 14-section template
2. Concrete numbers everywhere (query times, memory, sizes)
3. At least 1 BROKEN→FIX block in §10 (Common Pitfalls) and §14 (Case Study)
4. Update `README.md` module table
5. Add the module dir to `STUDY_ORDER.database` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
6. Add the module and EVERY file it owns to the `<!-- study-paths -->` block in
   this section's `README.md`, tagging each file with the tiers it belongs to (`-` for
   Full path only; the module page must carry every tier the module is in). **Do NOT put
   a block in the module page — content files hold only content.** Then run
   `python3 game/extract.py --write-paths` to regenerate the README's tier tables
7. Update root `README.md` Database phase table

## Adding a New Database Case Study

1. Write the case study — 11-section principal template
2. Update `case_studies/case_studies.md` — add to correct phase, update dependency map, add interview prep row
3. If it belongs in a tier, add its `<dir>/<dir>.md` to that tier's line in the
   `case_studies` entry in this section's `README.md` `<!-- study-paths -->` block
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

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module, EVERY
file that module owns (the module page AND every deep-dive sub-file), and EVERY case
study, each tagged with the tiers it belongs to — `-` means Full path only. Reading that
one block tells you every file in the section and which paths it is on.

**A content file carries NO structural metadata.** A module page or a deep-dive sub-file
holds the content of its topic and nothing else — no `<!-- study-paths -->` block, no tier
declaration, no path membership. That metadata used to live in each module page; it was
moved here so there is one place to look and one place to change.

**Adding a file? Add its line to the section README's block in the same commit.** A file on
disk that is missing from the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red. That check exists
because the old failure was silent: a new sub-file was invisible to the curated paths, or
silently dragged into every tier its parent was in, with a green build either way.

Order is never declared in the block: it comes from `STUDY_ORDER` in `game/app.js`. The
tier TABLES further down the README are GENERATED from the block by
`python3 game/extract.py --write-paths` — never hand-edit them.
