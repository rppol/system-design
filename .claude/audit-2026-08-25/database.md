# Database Audit — 2026-08-25

## 0. Snapshot

The `database/` section is, module-for-module, one of the strongest bodies of content in this
repo. The relational spine — `database_fundamentals`, `storage_engines_internals`,
`indexing_deep_dive`, `concurrency_control_and_locking`, `postgresql_internals`,
`mysql_innodb_internals`, `sql_query_optimization` — is uniformly excellent: every one of the
seven uses derived arithmetic ("walk one example") rather than quoted folklore numbers, all
seven carry a broken→fix pair, and the join-crossover, bloom-filter-FPR, B+tree-fanout and
write-amplification derivations are better than most published vendor material. The two product
deep dives (`elasticsearch_internals`, `redis_internals`) carry explicit dated version anchors
with inline `[9.5]` / `[8.10]` release tags, and `elasticsearch_internals` is genuinely current
to 4 Aug 2026 including the `columnar` tech-preview index mode. **Query optimizer internals,
execution plans, index internals, and formal isolation/concurrency — the four things the brief
asked me to press hardest on — are all strong, not thin.** They are the section's best asset.

The single biggest weakness is not in the modules at all: **all six case studies are
structurally non-compliant and roughly half the required size.** Every one uses an obsolete
6-heading skeleton (`Problem Statement / Architecture Overview / Key Design Decisions /
Implementation / Tradeoffs and Alternatives / Interview Discussion Points`) instead of the
mandated 11-section principal template; the same **6 of 11 sections are absent from all six**
(Intuition, Scale Estimation, Real-World Implementations, Technologies & Tools, Operational
Playbook, Capacity Planning, and Common Pitfalls as a section); **not one of the six names a
single real company**, in a set whose scenarios are literally Twitter's feed, Amazon's catalog
and Stripe's ledger; and they carry 4–5 Q&As each against a 10+ bar. They average 497 lines
against a 900–1100 bar (55%). The second-biggest weakness is a whole missing topic class:
**zero coverage of analytical/OLAP databases and the lakehouse** — `Iceberg`, `Delta Lake`,
`Hudi`, `lakehouse`, `Trino`, `Databricks`, `zero-ETL` and `reverse ETL` return **zero hits
across all 33 files.**

| Metric | Value |
|---|---|
| Modules | 31 |
| Case studies | 6 (+1 index) |
| Deep-dive sub-files | 1 (`surrogate_vs_natural_keys.md`) |
| Total lines — module content | 28,493 |
| Total lines — case studies | 2,980 (avg 497; bar is 900–1100) |
| Total Q&As — modules (in bank) | 568 |
| Total Q&As — case studies (excluded by design) | ~25 |
| Avg lines / module file | 890 |
| Modules with all 14 headings | 32 of 32 (one deviation: `database_fundamentals` §4/§5 renamed) |
| Modules with a `**Short:**` on every Q&A | 32 of 32 (5 files short by 1–2) |

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `case_studies/*` — rewrite all 6 to the 11-section template | case study ×6 | Repo's own contract: 11 sections, 900–1100 lines, named companies in §6, 10+ Q&As in §11. All six fail all four. This is a compliance defect, not a taste call. | **P0** | +2,600 total |
| `analytical_databases_and_olap/analytical_databases_and_olap.md` | module | Zero coverage of the whole OLAP class. ClickHouse is at v26.3.9.8-lts (Apr 2026) and the 2026 columnar landscape splits into four families — open-source real-time OLAP (ClickHouse/Druid/Pinot), cloud warehouses, embedded (DuckDB), TS specialists. https://clickhouse.com/resources/engineering/best-columnar-databases | **P0** | 950 |
| `lakehouse_and_table_formats/lakehouse_and_table_formats.md` | module | Iceberg v3 ratified mid-2025 (deletion vectors, variant type, row lineage, geospatial) and 1.10/1.11 shipped through May 2026; Iceberg v3 and Delta 4.0 now share compatible deletion-vector encodings; DuckLake hit 1.0 on 13 Apr 2026. Zero hits for any of it in `database/`. https://amdatalakehouse.substack.com/p/lakehouse-table-formats-in-2026-iceberg | **P0** | 900 |
| `case_studies/design_global_multiregion_oltp/` | case study | `newsql_and_distributed_sql` is the section's only Expert module with no case study, and multi-region strict-consistency OLTP is the top principal-level DB prompt. Aurora DSQL went GA 27 May 2025, making it a live design option. https://aws.amazon.com/about-aws/whats-new/2025/05/amazon-aurora-dsql-generally-available | **P0** | 1,000 |
| `case_studies/design_vector_search_platform/` | case study | `vector_databases` (16 Q&As, Advanced) has no case study. pgvectorscale's StreamingDiskANN hits 471 QPS @ 99% recall and lifts the per-node ceiling from ~10M to ~50M vectors — the exact sizing argument an interview asks for. https://github.com/timescale/pgvectorscale | **P0** | 1,000 |
| `graph_databases/graph_query_languages.md` | sub-file | GQL was published as ISO/IEC 39075:2024 in April 2024 — the first new ISO query-language standard since SQL in 1987 — and by 2026 Neptune, Neo4j, TigerGraph and Oracle have begun conformance work. The module has **zero** mentions of GQL. PostgreSQL 19 adds SQL/PGQ property-graph queries. https://neo4j.com/docs/cypher-manual/current/appendix/gql-conformance/ | **P1** | 750 |
| `vector_databases/disk_ann_and_filtered_search.md` | sub-file | DiskANN/Vamana, pgvectorscale, Turbopuffer, LanceDB and Matryoshka embeddings are all **zero-hit** in the module. Filtered ANN is the hard 2026 production problem and the module only covers pgvector post-filtering. https://effoma.com/blog/vector-database-performance-benchmark-comparison-2026/ | **P1** | 750 |
| `postgresql_internals/planner_and_parallel_execution.md` | sub-file | Four named zero-hit gaps in an otherwise excellent file: parallel query, JIT, extended statistics (`CREATE STATISTICS`), table access methods. PG18 shipped async I/O with io_uring (2–3× cold-cache reads) and PG19 beta adds `pg_plan_advice` for plan stability — both absent. https://www.postgresql.org/about/news/postgresql-19-beta-2-released-3350/ | **P1** | 750 |
| `search_engines/search_engines.md` — **rewrite as a true category module** | module (rewrite) | The file is currently an Elasticsearch primer at lower fidelity than its own sibling `elasticsearch_internals`, with §4 literally headed "Elasticsearch/OpenSearch Architecture". Zero hits for Vespa, Typesense, Meilisearch, Tantivy, SPLADE, or reciprocal rank fusion. The section CLAUDE.md assigns it the CATEGORY role; it does not fill it. | **P1** | 880 (replace) |
| `embedded_and_edge_databases/embedded_and_edge_databases.md` | module | DuckDB appears 3 times and SQLite once in 28,493 lines. MotherDuck crossed 10,000 paying teams in Q1 2026 and DuckLake 1.0 is production-ready — embedded analytics is now a real architectural choice, not a toy. https://motherduck.com/blog/duckdb-ecosystem-newsletter-april-2026/ | **P1** | 850 |
| `case_studies/design_metrics_platform/` | case study | `time_series_databases` (Intermediate, 16 Q&As) has no case study; "design a monitoring system" is a standing prompt and the module already carries the cardinality and Gorilla math to anchor it. | **P1** | 950 |
| `distributed_transactions/percolator_calvin_and_spanner.md` | sub-file | `hld/distributed_transactions.md` explicitly tells the reader that `database/distributed_transactions` is "the database-internals view: how Spanner/CockroachDB implement 2PC with a global clock" — and that file has **zero** mentions of Spanner, CockroachDB, Percolator or Calvin. A cross-link promising content that does not exist. | **P1** | 700 |
| `database_performance_tuning/wait_events_and_benchmarking.md` | sub-file | Wait-event taxonomy, pgbench/sysbench/HammerDB methodology, and PG18 `io_method`/io_uring are all absent from the tuning module (pgbench appears only in `database_selection_framework`). | **P2** | 700 |
| `case_studies/design_chat_message_store/` | case study | Structurally distinct from the feed case study (per-conversation ordering, receipts, offline sync); one of the most-asked "design X" prompts with no coverage. | **P2** | 950 |
| `case_studies/design_job_queue_on_postgres/` | case study | `SKIP LOCKED`, exactly-once dequeue, poison-pill backoff — maps to `concurrency_control_and_locking` + `connection_pool_management`, both strong modules with no case study. | **P2** | 900 |
| `case_studies/design_10tb_table_migration/` | case study | "Have you migrated a giant production table" is a top senior-DB question; today it only appears as a side effect of the polyglot-migration case study. | **P2** | 900 |

### What each P0 would actually contain

**`analytical_databases_and_olap/`** — §1 OLTP vs OLAP as a storage-and-execution split, not a
"reporting" split; §2 the column-as-array intuition and why vectorization beats row-at-a-time;
§3 columnar storage, vectorized execution, late materialization, predicate/aggregate pushdown,
sort keys vs indexes; §4 real-time OLAP (ClickHouse MergeTree family, Druid, Pinot) vs cloud
warehouses (Snowflake, BigQuery, Redshift) vs embedded (DuckDB) vs HTAP (TiDB TiFlash,
Aurora zero-ETL); §5 a MergeTree part/merge diagram plus a row-vs-column I/O bar chart; §6
MergeTree parts and background merges, sparse primary index with `index_granularity=8192`,
`AggregatingMergeTree` vs `SummingMergeTree`, dictionary/delta/LZ4/ZSTD encodings walked with
byte math; §7 Uber, Cloudflare, eBay, ByteDance on ClickHouse; §8 ClickHouse vs Druid vs Pinot
vs Snowflake on ingest rate, concurrency, cost, join capability; §9 when a Postgres materialized
view is still the right answer; §10 the JOIN trap, the mutation trap (`ALTER UPDATE` is
asynchronous), part explosion from too-fine partitioning; §11 tools; §12 15+ Q&As; §13 sort-key
before index thinking; §14 a customer-facing analytics dashboard that outgrew Postgres.

**`lakehouse_and_table_formats/`** — §1 the metadata-layer-over-object-storage idea; §2 "a table
is a pointer to a list of files"; §3 snapshot isolation via atomic metadata-pointer swap, schema
and partition evolution, time travel, compaction/small-file problem; §4 Iceberg v3 vs Delta 4.0
vs Hudi vs DuckLake vs Paimon, and the catalog tier (REST catalog, Polaris, Unity, Glue); §5
manifest-list → manifest → data-file tree diagram; §6 Iceberg v3 deletion vectors and row
lineage walked against a concrete MERGE, copy-on-write vs merge-on-read amplification math,
DuckLake's metadata-in-a-SQL-catalog inversion; §7 who runs which; §8 the convergence table —
compatible deletion-vector encodings, Delta UniForm, Hudi's native Iceberg support; §9 when a
warehouse still beats a lakehouse; §10 the small-file pathology, orphaned-snapshot storage
bills, concurrent-writer conflicts; §11 Spark/Flink/Trino/DuckDB engines; §12 15+ Q&As; §13
compaction and expiry as first-class ops; §14 a Postgres → Iceberg offload for cold history.

**The six case-study rewrites** — each keeps its existing (good) code and math, is re-cut into
the 11 sections, and gains the six absent ones: an Intuition paragraph, a derived Scale
Estimation (the numbers exist today but are asserted, never walked), a §6 naming real companies
(Stripe/Marqeta for the ledger, Amazon for the catalog, Twitter/Instagram for the feed,
Amplitude/Mixpanel for analytics, Salesforce for multi-tenant), a §7 tools table, a §8
operational playbook with alerts and runbooks, a §9 war story with a quantified impact, and a
§10 capacity plan. Q&As go from 4–5 to 10+.

**`design_global_multiregion_oltp/`** — Spanner TrueTime commit-wait vs CockroachDB's
uncertainty interval vs Aurora DSQL's optimistic-concurrency-plus-Time-Sync approach; regional
vs global tables; follower/stale reads as the latency escape hatch (a mechanism the section
never names today); the write-latency floor set by cross-region RTT; failure domains and
survivability goals.

**`design_vector_search_platform/`** — 200M chunks, hybrid BM25+dense with RRF, the
pgvector-vs-pgvectorscale-vs-dedicated-store decision with the ~10M / ~50M per-node ceilings,
filtered ANN for multi-tenant isolation, index rebuild cost and the delete/update problem in
HNSW, quantization ladder (float32 → int8 → BBQ) with a bytes-per-vector budget.

---

## 2. Stale — verified CORRECTIONS

| File | Line/heading | What it says | What is true in Aug 2026 | Source URL |
|---|---|---|---|---|
| `newsql_and_distributed_sql.md` | 451 (§11 tools table) | CockroachDB License = `BSL/CCL`, Managed = `CockroachDB Serverless` | BSL was retired **18 Nov 2024**: CockroachDB Core was discontinued and the code relicensed source-available-proprietary under a single **CockroachDB Enterprise** license — free for individuals and businesses under $10M revenue, with **mandatory telemetry and license keys**. Cloud tiers are now Basic / Standard / Advanced. | https://linuxiac.com/cockroachdb-shifts-to-enterprise-solution-only/ |
| `newsql_and_distributed_sql.md` | 523 (§12 Q&A on Aurora) | "Aurora Limitless (announced 2023) adds sharding on top of Aurora … **moving toward** distributed SQL" | Aurora Limitless reached **GA at re:Invent 2024**, and AWS's actual distributed-SQL product — **Aurora DSQL** — went **GA 27 May 2025**. Aurora DSQL is absent from the file entirely (zero hits), as is Neon. The "moving toward" framing is two product generations behind. | https://aws.amazon.com/about-aws/whats-new/2025/05/amazon-aurora-dsql-generally-available |
| `in_memory_databases.md` | 451 (§11 tools table) | Redis License = `RSALv2/SSPL` | Redis 8.0 (May 2025) added **AGPLv3** as a third option; Redis is OSI-open-source again and ships tri-licensed RSALv2 / SSPLv1 / AGPLv3. Listing only the two proprietary options is now wrong. `redis_internals.md` gets this right (dedicated licence timeline) — the two files contradict each other. | https://redis.io/blog/agplv3/ |
| `wide_column_databases.md` | whole file; §11, Q&As 707-710, LWT at 343-361 | Secondary indexes described only as the legacy per-node scatter-gather kind; LWT described purely as 4-round-trip Paxos; compaction covers STCS/LCS/TWCS only | The section version matrix pins **Cassandra 5.0**, but the file predates that generation entirely: 5.0 (Sept 2024) shipped **SAI (Storage-Attached Indexes)**, **native vector search / `VECTOR` type**, and **UCS (Unified Compaction Strategy)** — all **zero-hit**. **Cassandra 6.0 is in alpha in 2026 with the Accord protocol** for general-purpose ACID transactions without elected leaders — also zero-hit. | https://www.instaclustr.com/blog/apache-cassandra-6-accord-transactions-what-you-need-to-know/ |
| `redis_internals.md` | §4.10 new types, lines 468-481 | Lists only Array `[8.8]` and Compact hashes `[8.10]` as Redis 8 new types | Redis 8 **introduced Vector Sets as a new native data type** (`VADD`/`VSIM`) alongside folding JSON, Time Series, probabilistic structures and the Query Engine into core. The file is anchored at 8.10.0 and tracks dot-releases either side of where vector sets land, yet has zero hits for "vector set", `VADD`, or `VSIM`. | https://www.dragonflydb.io/blog/redis-8-lands-new-features-and-more-license-drama |
| `graph_databases.md` | whole file | Covers openCypher, Gremlin/TinkerPop, RDF/SPARQL; no query-language standard | **GQL was published as ISO/IEC 39075:2024 in April 2024** — the first ISO database query language since SQL in 1987 — and openCypher's stated mission is now to help implementors evolve toward it. Neptune, Neo4j, TigerGraph and Oracle have begun conformance work. Zero hits for "GQL" in the file. | https://neo4j.com/docs/cypher-manual/current/appendix/gql-conformance/ |
| `time_series_databases.md` | §6 Prometheus TSDB (line 703), §10 retention (line 655), §4 InfluxDB (line 184) | Prometheus discussed with **no version anchor at all**; `remote_write` referenced generically; InfluxDB 3.x gets one line | **Prometheus 3.0 shipped Nov 2024** (first major since 2017). **Remote-Write 2.0** adds metadata, exemplars, created timestamps and native histograms with string interning. **Native histograms reached stable in v3.8 (Nov 2025)**, opt-in via `scrape_native_histograms`. None of this is in the file. Also QuestDB is zero-hit and VictoriaMetrics is a name in a table. | https://prometheus.io/blog/2024/11/14/prometheus-3-0/ |
| `time_series_databases.md` | §4, §11 | "TimescaleDB" used as the company/platform name throughout | Not wrong about the extension — **the OSS extension is still named TimescaleDB** — but the company rebranded to **Tiger Data on 17 June 2025**, and the platform is Tiger Cloud with **Hypercore** (hybrid row-columnar) and Fluid Storage. This is an *addition*, not a correction: do not rename the extension. | https://www.tigerdata.com/newsroom/timescale-becomes-tiger-data-defining-a-new-standard-as-the-fastest-postgresql-platform-for-modern-applications |
| `key_value_stores.md` (822) vs `in_memory_databases.md` (489, 593) | §14 case studies | Redis sorted-set per-member cost stated as **~50 bytes** in one file and **~200 bytes** in the other, for the identical structure | Internal contradiction, not a currency issue — a 4× discrepancy in the same section with no stated differing assumption. One of the two is wrong; both derive it from scratch. | (internal) |
| `mysql_innodb_internals.md` | 557 (§14 case study) | Scenario runs on "MySQL 8.4 LTS" | 8.4 is still a maintained LTS so this is not *wrong*, but the section matrix pins **MySQL 9.7 LTS (21 Apr 2026)**, and MySQL has since switched to **calendar versioning — 26.7 Innovation shipped July 2026** (26.7 = July 2026, 27.7 = July 2027), introducing the Change Stream Applier for multithreaded replication. The file mentions none of it. | https://blogs.oracle.com/mysql/mysql-july-2026-ga-releases-now-available |
| `postgresql_internals.md`, `sql_query_optimization.md` | §6, §12 | PG18-current throughout (correct), but no forward look | **PostgreSQL 19 Beta 3 landed 13 Aug 2026**, GA expected H2 2026, with **SQL/PGQ property-graph queries**, **`REPACK` / `REPACK CONCURRENTLY`**, **`pg_plan_advice` / `pg_stash_advice`** for planner control, **parallel autovacuum**, **`ON CONFLICT DO SELECT`**, **`FOR PORTION OF`** — plus two default flips: **JIT is now disabled by default** and **LZ4 replaces pglz as the TOAST default**. `pg_plan_advice` in particular is the plan-stability answer `sql_query_optimization` currently says Postgres lacks. | https://www.postgresql.org/about/news/postgresql-19-beta-2-released-3350/ |

**Explicitly NOT findings** (checked and found current): `elasticsearch_internals.md` is anchored
at ES 9.5.0 / Lucene 10.5 (4 Aug 2026) and already carries the `columnar` `[9.5 tech preview]`
index mode and the ES|QL PromQL source command — it is the most current file in the repo.
`redis_internals.md`'s licence timeline, `surrogate_vs_natural_keys.md`'s PG18/PG19-beta and
MySQL 9.7 anchors, and the section CLAUDE.md's MongoDB 8.3 / ClickHouse 26.x / ES 9.5 rows all
verified correct.

---

## 3. Reordering — SEQUENCE and TIER changes

| Module | Current position / tier | Proposed | Why |
|---|---|---|---|
| `database_caching_patterns` | pos 25 (Phase 5, after consensus) / **Full only (`-`)** | **pos 8** (end of Phase 2) / **senior** | **The single most important change.** It is a 956-line STRONG module — the deepest Q&A set and tightest diagram-to-math coupling in the section — sitting on the Full path only, buried behind Raft and CRDTs. It depends on nothing beyond relational basics, and "how would you cache this" is a top-5 senior interview question. Being off the Senior path means most readers never reach it. |
| `mysql_innodb_internals` | pos 6 / **Full only (`-`)** | pos 6 / **senior** | MySQL is half the production world and the module is STRONG (redo-log wrap derivation, buffer-pool LRU math). A Senior "craft" path that teaches only Postgres internals is not the craft. |
| `time_series_databases` | pos 17 / **Full only (`-`)** | pos 17 / **senior** | Every backend engineer operates a metrics store. The Gorilla/delta-of-delta and cardinality-explosion math is exactly senior craft. |
| `vector_databases` | pos 18 / senior | pos 18 / **senior, principal** | "Which vector store, at what cost, with what recall target" is a 2026 *judgment* question, and the per-node ceiling economics (10M vs 50M vectors) is principal material. |
| `search_engines` | pos 14 / **Full only** | pos 14 / **senior** *(after the category rewrite in §1)* | Once it stops being an ES rehash and owns hybrid retrieval + relevance evaluation, it is senior craft. Keep it Full-only until the rewrite lands. |
| `connection_pool_management` | pos 26 / senior | pos 26 / **senior, principal** | Little's Law sizing and the K8s connection-storm feedback loop are the "what do you tell a team not to do" case; and it is the module with the highest overlap against `backend/`, so principal readers need the boundary. |
| `analytical_databases_and_olap` *(new)* | — | pos 18 (end of Phase 3) / **senior, principal** | Slots after `time_series_databases`, before the Emerging phase. |
| `lakehouse_and_table_formats` *(new)* | — | pos 22 (Phase 4) / **principal** | Judgment tier: table-format choice is a five-year architectural bet, not a craft skill. |
| `embedded_and_edge_databases` *(new)* | — | pos 23 (Phase 4) / **Full only** | Real but niche; earns Full-path presence, not a tier. |
| `in_memory_databases` | pos 20 / Full only | **keep position and tier, but trim** — see §4 | Not a reorder; a dedup. |
| Phase 3 block as a whole | 8 engine modules before any distributed-systems concept | **leave as is** | Tempting to move replication/sharding/consensus ahead of the engine tour, but the engine modules each teach their own quorum/partition story locally and the current order matches how people actually learn. Not worth churning. |

### Proposed `STUDY_ORDER["database"]`

```js
  database: [
    "database/database_fundamentals","database/storage_engines_internals","database/indexing_deep_dive","database/concurrency_control_and_locking",
    "database/postgresql_internals","database/mysql_innodb_internals","database/sql_query_optimization","database/database_caching_patterns",
    "database/schema_design_and_normalization","database/database_migrations_zero_downtime",
    "database/document_databases","database/key_value_stores","database/redis_internals","database/wide_column_databases","database/search_engines","database/elasticsearch_internals","database/graph_databases","database/time_series_databases","database/analytical_databases_and_olap",
    "database/vector_databases","database/newsql_and_distributed_sql","database/in_memory_databases","database/lakehouse_and_table_formats","database/embedded_and_edge_databases",
    "database/replication_and_high_availability","database/sharding_and_partitioning","database/distributed_transactions","database/consistency_models_and_consensus",
    "database/connection_pool_management","database/database_performance_tuning","database/backup_recovery_and_disaster_recovery","database/database_security_and_compliance",
    "database/database_selection_framework","database/polyglot_persistence_patterns",
  ],
```

Two moves only: `database_caching_patterns` jumps from position 25 to position 8, and three new
modules are inserted (`analytical_databases_and_olap` at the end of Phase 3;
`lakehouse_and_table_formats` and `embedded_and_edge_databases` in Phase 4). Everything else
holds its relative order, so the tier subsets stay ordered by construction.

---

## 4. Depth — THIN modules and BLOAT

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| **All 6 case studies** | 410–571 (avg 497) | 4–5 | 6 of 11 mandated sections absent from every file; zero named companies across all six; no derived scale estimation; no operational playbook; no capacity plan; no quantified war story | Rewrite to the 11-section template. Keep the existing code and math — they are good — and add the six missing sections. Target 900+ lines each. |
| `search_engines` | 877 | 16 | Zero hits for Vespa, Typesense, Meilisearch, Tantivy, SPLADE, reciprocal rank fusion. §4 is headed "Elasticsearch/OpenSearch Architecture". Overlaps `elasticsearch_internals` on shard sizing, deep pagination, ILM and force-merge at strictly lower fidelity. OpenSearch named but the fork never explained (its sibling explains it in depth). | **Rewrite as the category module the section CLAUDE.md says it is.** Delete the ES-mechanics duplication and cross-link to `elasticsearch_internals`; own inverted-index/BM25 theory, the engine landscape, hybrid lexical+vector retrieval with RRF, learned sparse retrieval, and relevance evaluation (NDCG, click models). |
| `wide_column_databases` | 805 | 15 | An entire Cassandra generation: SAI, `VECTOR`/vector search, UCS, Accord (all zero-hit). HBase is **zero-hit** in a module titled for the category. ScyllaDB is one table row. No version anchor anywhere. | Add a version-anchor banner; add a Cassandra-5/6 section (SAI vs legacy 2i, UCS, vector search, Accord vs Paxos LWT); add an HBase/BigTable-lineage section; promote ScyllaDB from a table row to a shard-per-core comparison. |
| `in_memory_databases` | 609 | 16 | Not thin — **bloated by duplication.** Roughly a third of its Redis content re-derives `key_value_stores` material: RDB fork/CoW pause (364-397 vs kv 108-149), the Redis-vs-Memcached table (125-140 vs kv 542-552), the rate-limiter Lua script (496-514 vs kv 721-748), and a second sorted-set leaderboard case study (§14 vs kv §14) — which is where the ~50 vs ~200 bytes/member contradiction lives. | **Trim, do not delete.** VoltDB, Ignite, Aerospike, the warming taxonomy and the in-memory-vs-disk cost table are genuinely unique. Replace the four duplicated blocks with cross-references, following the discipline `key_value_stores` already uses toward `redis_internals`. Reconcile the sorted-set byte figure. ~-180 lines. |
| `newsql_and_distributed_sql` | 668 | 16 | Shortest Expert module. Neon and Aurora DSQL zero-hit; "follower reads" never named despite extensive read-latency discussion; CockroachDB's licence story reduced to a table cell while its sibling files give theirs full timelines; separation of storage and compute never named as a pattern. | Add a serverless-Postgres / storage-compute-separation section (Neon copy-on-write branching and scale-to-zero, Aurora DSQL, Aurora Limitless), name and explain follower/stale reads, and give the licence story the same treatment `redis_internals` and `elasticsearch_internals` give theirs. +150 lines. |
| `document_databases` | 704 | 17 | MongoDB **time-series collections** and **queryable encryption** both zero-hit; no version anchor anywhere in the file despite scattered "4.2+" feature callouts. | Add a version-anchor banner (MongoDB 8.3, May 2026, `$scoreFusion` now GA); add time-series collections (the file's own IoT example needs them) and queryable encryption. +90 lines. |
| `postgresql_internals` | 753 | 19 | Parallel query, JIT, extended statistics, table AM — four named zero-hit topics in an otherwise excellent file. | Sub-file `planner_and_parallel_execution.md` (see §1). |
| `mysql_innodb_internals` | 656 | 15 | Adaptive hash index has **zero coverage in this file** (one passing sentence in `indexing_deep_dive`); histograms and hash join both zero-hit; MySQL join algorithms live nowhere (`sql_query_optimization` is Postgres-flavoured). | Add AHI mechanics, histogram-based statistics, and MySQL's hash join + join-order optimizer. +140 lines. Also the shortest module in Phase 2 and at the exact 15-Q&A floor — take it to 18. |
| `indexing_deep_dive` | 944 | 19 | SP-GiST entirely absent; MySQL index skip scan absent; bitmap index scan gets one passing Q&A line while GIN/BRIN get full walkthroughs. | Add SP-GiST, skip scan, and a proper bitmap-heap-scan walkthrough with `lossy` page behaviour. +80 lines. |
| `graph_databases` | 756 | 16 | GQL zero-hit; GraphRAG and vector+graph hybrid zero-hit; no Neo4j version pin. | Sub-file `graph_query_languages.md` (see §1); add a version anchor. |
| `database_migrations_zero_downtime` | 709 | 16 | `pg_repack` zero-hit; versioned-vs-declarative (Atlas, Skeema) never drawn; migrations-in-CI/CD not addressed; schema change on distributed SQL absent. | +120 lines into §4 and §6. |
| `connection_pool_management` | 796 | 16 | pgcat, Supavisor, AWS Data API all zero-hit — and all three are also absent from the `backend/` sibling, so this is a repo-wide gap. | +60 lines. |
| `backup_recovery_and_disaster_recovery` | 815 | 16 | Barman is a one-line table row while pgBackRest and WAL-G get worked examples. | +40 lines. |
| `polyglot_persistence_patterns` | 727 | 16 | Lakehouse/Iceberg, zero-ETL, reverse ETL, HTAP all zero-hit — this is the module that should be the on-ramp to the two new P0 modules. | +100 lines plus cross-links once `lakehouse_and_table_formats` exists. |
| `database_fundamentals` | 587 | 16 | Shortest module in the section. Isolation table covers the anomalies but not the Adya/Berenson vocabulary (`dirty write`, `read skew` as named terms). §4/§5 renamed away from the template. | +80 lines for the formal anomaly taxonomy; make the §4/§5 heading deviation a conscious owner decision rather than silent drift. |

**Merge / delete recommendations.** One deletion candidate, and I am recommending **against**
it: `in_memory_databases` overlaps `key_value_stores` + `redis_internals` heavily, but its
VoltDB / Ignite / Aerospike / cache-warming content exists nowhere else. Trim the duplicated
third rather than deleting the module. `search_engines` looks like a merge candidate into
`elasticsearch_internals`, but the right move is a rewrite, not a merge — the section genuinely
needs a search *category* module and currently has none.

**One recurring pattern worth naming:** hot-key mitigation is independently authored, with the
same three-fix taxonomy (L1 cache / key sharding / replica reads) and the same style of
derivation, in `key_value_stores` (584-622), `database_caching_patterns` (431-506) and
`in_memory_databases`'s case study — with different numbers and zero cross-links, despite the
section CLAUDE.md's own cross-reference map naming all three as related. Pick a canonical owner
(`database_caching_patterns`, which does it best) and cross-reference the other two.

---

## 5. Cross-section notes

- **`hld/` overlap is real and quantified.** `database/sharding_and_partitioning` vs
  `hld/database_sharding` (1,455 lines) + `hld/consistent_hashing` (1,293): ~40–50% conceptual
  overlap on shard-key strategy and ring math. `database/distributed_transactions` vs
  `hld/distributed_transactions` (1,011 lines): **~55–65%**, the highest pair in the repo.
  `database/consistency_models_and_consensus` vs `hld/cap_theorem` + `hld/consensus_algorithms`:
  ~30–40%. **The line should be: `hld/` owns the pattern as a system-design building block;
  `database/` owns the engine implementation.** Two of the six modules
  (`replication_and_high_availability`, `polyglot_persistence_patterns`) already carry explicit
  ownership-boundary notes; the four with the highest overlap carry none. Add them.
- **A cross-link that promises content which does not exist.** `hld/distributed_transactions.md`
  tells readers that `database/distributed_transactions` is "the database-internals view: how
  Spanner/CockroachDB implement 2PC with a global clock." That file has zero mentions of Spanner,
  CockroachDB, Percolator or Calvin. Either write the sub-file (§1, P1) or fix the hld sentence.
- **`backend/connection_pooling_deep_dive` (645 lines) vs `database/connection_pool_management`
  (796):** ~60–70% overlap — both independently derive the identical HikariCP `(cores×2)+spindles`
  formula, ConcurrentBag internals, the Little's Law reconciliation, and the same PgBouncer
  transaction-mode feature-loss list. Neither has a boundary note. This is the single clearest
  candidate in the repo for a "who owns what" edit.
- **`technologies/debezium`** — `polyglot_persistence_patterns` already defers Debezium *product*
  internals there explicitly and correctly. That is the model the other overlapping pairs should
  copy. No change needed; the streaming/CDC gap in `database/` is about Kafka 4.x KRaft, Flink
  2.0 and streaming-as-a-database (Materialize, RisingWave — the latter zero-hit), not Debezium.
- **`ml/` and `technologies/` already carry the only lakehouse content in the repo** (Iceberg 4
  files in `ml`, 3 in `technologies`; Delta Lake 8 in `ml`). The proposed
  `lakehouse_and_table_formats` module should own the *storage-format* view — snapshot isolation,
  deletion vectors, compaction economics — and cross-link to `ml/`'s pipeline view rather than
  restating it.
- **`llm/`** — the "database MCP server / text-to-SQL agent" topic is real for 2026 (Microsoft
  pulled its own MSSQL MCP sample in June 2026 as "unsafe"), but it belongs in `llm/`, not here.
  The only `database/` angle worth a paragraph is in `database_security_and_compliance`: giving
  an agent a database role, and why read-only + row-level + no-DDL is the whole answer.
- **Section CLAUDE.md is stale on Q&A counts for at least 8 modules**, all undercounts:
  `connection_pool_management` 12→16, `database_performance_tuning` 14→18,
  `backup_recovery_and_disaster_recovery` 12→16, `database_security_and_compliance` 12→16,
  `database_selection_framework` 16→19, `polyglot_persistence_patterns` 13→16,
  `wide_column_databases` 13→16, `search_engines` 13→16, `graph_databases` 11→16,
  `time_series_databases` 12→16. Fix in the same commit as anything else touching that table.
- **The `case_studies/case_studies.md` dependency map is decorative and contains a false edge.**
  No case study links to another (verified across all six), so every arrow is asserted. The edge
  `SMFS -->|"Cassandra wide-row model"| RTAP` is flatly wrong — the analytics case study uses
  Kafka + ClickHouse + Redis + S3 and contains zero Cassandra content. `BL -->|"SERIALIZABLE
  isolation"| MSDB` is likewise unsupported. The Cross-Cutting table's "Idempotency | all six"
  is an overclaim; only two files discuss it.

---

## 6. Execution plan

**Wave 1 — corrections and currency (can all run in parallel; no wiring changes).** ~2 sessions.
1. Fix the four verified factual errors: CockroachDB licence + managed-tier name and the Aurora
   Limitless/DSQL paragraph in `newsql_and_distributed_sql`; the Redis licence row in
   `in_memory_databases`; reconcile the ~50 vs ~200 bytes/sorted-set-member contradiction between
   `key_value_stores` and `in_memory_databases`. **Every one of these sits above or below a
   `**Short:**` line — re-read and update it in the same edit.**
2. Bring `wide_column_databases` up to Cassandra 5/6 (SAI, `VECTOR`, UCS, Accord) and add HBase.
   Add a version-anchor banner.
3. Add Redis 8 Vector Sets to `redis_internals` §4.10 and give Flex/tiering more than a name.
4. Anchor `time_series_databases` on Prometheus 3.x + remote-write 2.0 + native histograms; add
   the Tiger Data/Tiger Cloud/Hypercore context (without renaming the TimescaleDB extension);
   deepen InfluxDB 3.x (Arrow/DataFusion/Parquet); add QuestDB.
5. Add version-anchor banners to `document_databases`, `graph_databases`,
   `wide_column_databases`, `vector_databases`, `newsql_and_distributed_sql` — the five files
   with no currency marker. Copy the `elasticsearch_internals` / `redis_internals` pattern.
6. Fix the section CLAUDE.md Q&A counts (10 modules) and the false dependency-map edges in
   `case_studies/case_studies.md`.

**Wave 2 — the case-study rewrite (the largest single block; 6 files can run in parallel).**
~6 sessions.
7. Rewrite all six to the 11-section principal template, 900+ lines each, 10+ Q&As, named
   companies in §6, derived scale estimation in §2, operational playbook in §8, quantified war
   story in §9, capacity plan in §10. Keep the existing code and math verbatim where possible.
8. Rebuild `case_studies/case_studies.md`'s dependency map from actual cross-links added during
   the rewrite, so the arrows are cited rather than asserted.
   *Needs:* nothing structural — the six paths already exist in the `<!-- study-paths -->` block.

**Wave 3 — the two P0 modules (parallel with each other, and with Wave 2).** ~2.5 sessions.
9. `analytical_databases_and_olap/analytical_databases_and_olap.md` (~950 lines, 18 Q&As).
10. `lakehouse_and_table_formats/lakehouse_and_table_formats.md` (~900 lines, 16 Q&As).
    *Needs:* `STUDY_ORDER["database"]` entries, `<!-- study-paths -->` lines with tiers, section
    README module table, root README Database phase table, then
    `python3 game/extract.py --write-paths` and `--strict`.

**Wave 4 — the P0 case studies (parallel).** ~2 sessions.
11. `case_studies/design_global_multiregion_oltp/`.
12. `case_studies/design_vector_search_platform/`.
    *Needs:* `case_studies.md` index (phase, dependency map, interview-prep row),
    `<!-- study-paths -->` case-study lines with tiers, section README case-study table.

**Wave 5 — the `search_engines` rewrite and the P1 sub-files (parallel).** ~3 sessions.
13. Rewrite `search_engines` as the category module; strip the `elasticsearch_internals`
    duplication and cross-link instead. Promote to senior tier in the same commit.
14. `graph_databases/graph_query_languages.md` — GQL/ISO 39075, openCypher→GQL, SQL/PGQ in PG19,
    Gremlin, SPARQL, GraphRAG, vector+graph hybrid.
15. `vector_databases/disk_ann_and_filtered_search.md` — DiskANN/Vamana, pgvectorscale
    StreamingDiskANN, filtered ANN, Matryoshka, Turbopuffer/LanceDB.
16. `postgresql_internals/planner_and_parallel_execution.md` — parallel query, JIT (and PG19's
    default flip), extended statistics, table AM, `pg_plan_advice`.
17. `distributed_transactions/percolator_calvin_and_spanner.md` — closes the hld cross-link
    promise.
    *Needs:* a `<!-- study-paths -->` line per sub-file with tiers (sub-files need **no**
    `STUDY_ORDER` entry). A missing line here is fatal under `--strict`.

**Wave 6 — dedup, boundaries, tiers, wiring (sequential; must run last).** ~1.5 sessions.
18. Trim `in_memory_databases`'s four duplicated Redis blocks to cross-references (~-180 lines).
19. Pick `database_caching_patterns` as the canonical hot-key owner; cross-reference from
    `key_value_stores` and `in_memory_databases`.
20. Add ownership-boundary notes to the four high-overlap pairs:
    `database/sharding_and_partitioning` ↔ `hld/database_sharding`,
    `database/distributed_transactions` ↔ `hld/distributed_transactions`,
    `database/consistency_models_and_consensus` ↔ `hld/consensus_algorithms`,
    `database/connection_pool_management` ↔ `backend/connection_pooling_deep_dive`.
21. Apply the tier and sequence changes from §3 — move `database_caching_patterns` to position 8
    and into senior; `mysql_innodb_internals`, `time_series_databases`, `search_engines` → senior;
    `vector_databases`, `connection_pool_management` → +principal.
    *Needs:* `STUDY_ORDER["database"]` reorder, `<!-- study-paths -->` tier edits,
    `python3 game/extract.py --write-paths`, then **`scripts/build_banks.sh` in full** — not just
    `--strict`; the script's own verifier runs after it.

**Wave 7 — the P1/P2 depth fills (parallel, any order, no wiring).** ~3 sessions.
22. Per-module additions from §4: `embedded_and_edge_databases` (new module — needs wiring),
    `mysql_innodb_internals` (+AHI/histograms/hash join, +3 Q&As),
    `indexing_deep_dive` (+SP-GiST/skip scan/bitmap), `document_databases` (+time-series
    collections/queryable encryption), `newsql_and_distributed_sql` (+Neon/DSQL/follower reads),
    `database_migrations_zero_downtime` (+pg_repack/declarative/CI-CD/distributed DDL),
    `polyglot_persistence_patterns` (+lakehouse/zero-ETL/HTAP cross-links),
    `database_fundamentals` (+Adya anomaly taxonomy), `backup_recovery` (+Barman),
    `connection_pool_management` (+pgcat/Supavisor/Data API),
    `database_performance_tuning/wait_events_and_benchmarking.md` (sub-file).
23. The three P2 case studies (chat store, Postgres job queue, 10TB table migration).

**Parallelism.** Waves 1, 2 and 3 are fully independent and can run concurrently. Wave 4 depends
on nothing but is best after Wave 2 so the new case studies match the rewritten template. Wave 5
is independent. **Wave 6 must be last and must be sequential** — it touches `STUDY_ORDER` and the
`<!-- study-paths -->` block, and two agents editing that block concurrently will produce a red
build.

**Total effort: ~20 agent-sessions.** The distribution is lopsided — Wave 2 (case studies) alone
is ~30% of it, and it is the wave with the highest payoff per hour because it converts six
non-compliant files into six compliant ones without needing new research.

---

## 7. Confidence and gaps

**High confidence.** The four verified factual corrections (CockroachDB licence, Aurora
DSQL/Limitless, Redis licence, Cassandra 5/6) — each cited to a primary or well-sourced page and
each confirmed against the actual file text, with line numbers. The zero-hit gaps (Iceberg,
lakehouse, Trino, GQL, DiskANN, Vector Sets, HBase, QuestDB, Neon, Aurora DSQL, pgvectorscale,
Turbopuffer, LanceDB, Matryoshka, SP-GiST, io_uring, EPaxos, Calvin, Percolator, zero-ETL,
reverse ETL) are grep results across all 33 files, not impressions. The case-study structural
finding is a heading-level check on all six files.

**Medium confidence.** The overlap percentages against `hld/` and `backend/` (~40–70%) are a
subagent's read of both sides, not a line-diff — the direction is certain, the magnitude is an
estimate. The tier proposals are judgment calls against the repo's own senior=craft /
principal=judgment split; `database_caching_patterns` → senior is the one I would defend
hardest, `connection_pool_management` → +principal the one most open to argument.

**What I could not verify.**
- Whether the AWS RDS automated-backup retention maximum quoted in
  `backup_recovery_and_disaster_recovery` (35 days) has changed — my search for it was
  rate-limited and I did not want to assert an unverified correction. Worth one check.
- CockroachDB Cloud's current tier pricing quoted in `database_selection_framework` (from
  $146/mo) — the whole §TCO block is honestly self-dated to "July 2026, AWS us-east-1
  on-demand", which is the right way to handle it, but the CockroachDB row now sits under a
  changed licence and should be re-checked.
- PgBouncer 1.21's prepared-statement support claim in `design_multitenant_saas_database`
  (line 256) — specific and plausible, unverified.
- The `~50 vs ~200 bytes` sorted-set contradiction: I confirmed both numbers exist and conflict,
  but did not determine which is right. Redis's actual per-member skiplist cost depends on
  encoding (`listpack` vs `skiplist`) and member length, so the fix is probably to state the
  assumption in both places rather than to pick a winner.
- Whether `hld/` or `backend/` would object to the ownership boundaries I proposed in §5 — those
  are the other auditors' sections and the boundary has to be agreed, not declared unilaterally.
