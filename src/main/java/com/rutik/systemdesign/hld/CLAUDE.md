# HLD Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/hld/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

HLD is the architectural overview layer. The `backend/` section contains production-depth implementations; use HLD for the "what and why" and backend for the "how". Cross-reference aggressively.

---

## Module List — 32 Modules

| Module | Key Concepts |
|--------|-------------|
| `scalability/` | Vertical vs horizontal, stateless services, replication, partitioning strategies |
| `capacity_estimation/` | Back-of-the-envelope QPS, storage, bandwidth and connection math derived from DAU |
| `load_balancing/` | L4 vs L7, algorithms (round-robin, least-conn, IP hash), health checks, sticky sessions |
| `caching/` | Cache-aside, write-through, TTL, eviction, Redis vs Memcached, CDN as cache |
| `database_design/` | Schema design principles, denormalization, indexing strategy, OLTP vs OLAP |
| `api_design/` | REST maturity levels, versioning, pagination, idempotency, backward compatibility |
| `realtime_updates/` | Polling vs SSE vs WebSockets, connection budgets, fan-out cost, gap-fill and resume |
| `cap_theorem/` | CAP, PACELC, consistency models, partition tolerance, CP vs AP systems |
| `message_queues/` | Producer-consumer, pub/sub, at-least-once vs exactly-once, DLQ, backpressure |
| `microservices/` | Decomposition, bounded contexts, inter-service communication, data ownership |
| `consistent_hashing/` | Ring, virtual nodes, hotspot prevention, rebalancing cost |
| `rate_limiting/` | Token bucket, sliding window, leaky bucket, distributed rate limiting |
| `cdn/` | Edge caching, cache invalidation, geo-routing, origin shield, cache-control headers |
| `database_sharding/` | Horizontal partitioning, shard key selection, resharding, cross-shard queries |
| `geospatial_indexing/` | Geohash, S2, H3, quadtrees; proximity and k-nearest search, cell-boundary handling |
| `consensus_algorithms/` | Raft leader election + log replication, Paxos variants, PBFT, ZAB, etcd/ZooKeeper internals, quorum sizing, term numbers |
| `multi_region_and_data_residency/` | Active-active vs active-passive, write locality, consensus latency, residency constraints |
| `event_sourcing_cqrs/` | Event sourcing (append-only log, projections, snapshots), CQRS (command/query separation), Saga pattern, eventual consistency |
| `stream_processing/` | Watermarks, windowing, exactly-once, state backends, rescaling a stateful job |
| `distributed_transactions/` | 2PC, 3PC, Saga (orchestration vs choreography), TCC, outbox pattern, idempotency keys |
| `coordination_and_contention/` | Distributed locks, leases, fencing tokens, hot keys, when coordination is the bottleneck |
| `observability/` | Three pillars (metrics, logs, traces), RED/USE methods, SLI/SLO/error budgets, cardinality, sampling, distributed tracing |
| `security_and_auth/` | AuthN vs AuthZ, OAuth2/OIDC, JWT vs sessions, mTLS, RBAC vs ABAC, encryption at rest/in transit, secrets management |
| `resilience_patterns/` | Circuit breaker, bulkhead, retry with backoff + jitter, timeouts, graceful degradation, failover, load shedding |
| `cell_based_architecture/` | Cells as blast-radius containment, cell routing, poison-pill isolation, per-cell capacity |
| `large_blobs_and_media_pipelines/` | Proxy vs presigned upload, multipart and resumable sessions, content-defined chunking, download strategies, processing pipelines, metadata-to-blob consistency |
| `scaling_reads_and_writes/` | The read ladder and the write ladder (which rung next, what it costs), the hot-key toolkit |
| `search_systems/` | The system around the search engine: getting changes into the index, freshness, index partitioning, cluster topology, query types, ranking stages |
| `long_running_tasks_and_workflows/` | Sync-with-timeout vs async job, job table + worker pool, database as queue, poll/push/callback delivery, orchestration vs choreography, workflow engines |
| `time_clocks_and_ordering/` | Physical and monotonic clocks, Lamport and vector clocks, HLC, TrueTime-style bounds, last-writer-wins, sequencers, the ordering-guarantee ladder |
| `failure_detection_and_membership/` | Heartbeat vs ping, gossip, phi accrual, SWIM and Lifeguard, membership views, anti-entropy and repair, leader leases |
| `data_platform_architecture/` | Batch vs stream, Lambda vs Kappa, warehouse vs lake vs lakehouse, ingestion patterns, medallion layering, serving layers |
| `case_studies/` | HLD case study problems — 37 (see `case_studies/case_studies.md`) |

**Ownership notes for the 2026-11-25 modules.** Each stays at architectural-overview depth and
links out for the mechanics: `search_systems/` owns the system around the engine (indexing
pipeline, fan-out, topology); inverted indexes, BM25 and Lucene internals live in
`../database/search_engines/` and `../database/elasticsearch_internals/`, and the service-to-index
sync code in `../backend/search_integration_patterns/`. `large_blobs_and_media_pipelines/` owns the
architecture; the upload API contract and resume protocol live in
`../backend/file_upload_and_media_handling/`. `long_running_tasks_and_workflows/` owns the
decision; deterministic replay and workflow versioning live in
`../backend/durable_execution_and_workflows/` and `../technologies/temporal_durable_execution/`.
`time_clocks_and_ordering/` owns clock engineering; `../cs_fundamentals/distributed_systems_theory/`
owns the theory (FLP, impossibility results) and links here. `data_platform_architecture/` owns
the platform shape; engines and table formats live in `../database/analytical_databases_and_olap/`
and `../database/lakehouse_and_table_formats/`.

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 32 modules = "Recommended Learning Order")
plus two curated tiers: **Senior** (24 modules) and **Principal** (19). They are
different cuts, not nested depths — senior is the craft, principal is the judgment
(which approach at what cost, what failure domains, what you tell a team *not* to do),
so principal is usually the smaller list and much of it is material senior never sees.
Membership is declared ONCE per section, in the `<!-- study-paths -->` block in this
section's `README.md`, naming the files each tier takes; listing a tier joins it, omitting
the tier opts out, and the module page (`<module>.md`) must always be listed. Order is never declared — it
comes from `STUDY_ORDER.hld` in `game/app.js`, so a tier is an ordered subset by
construction. **There is no path array in `app.js` to edit**: `extract.py` walks the
markers and emits the gitignored `questions/paths.json`, which the game fetches at boot.
The tier tables in `README.md` sit between `<!-- study-path-table <tier> -->` markers and
are **generated** — regenerate with `python3 game/extract.py --write-paths`; a
hand-edited or stale block fails `extract.py --strict` and the Pages deploy. Case studies
are tiered in the same README block under `case_studies` (27 senior / 13 principal of
the 37), driving the Level filter on the game's Case Studies tab. The README
also carries a Knowledge-Question Map and a 6-week Study Plan (prose; no path impact).

---

## Planned / Missing Topics (not yet created)

Nothing is queued right now. The 2026-11-25 gap pass found 14 missing topics and all 14
were built that day: 7 modules (`large_blobs_and_media_pipelines/`, `scaling_reads_and_writes/`,
`search_systems/`, `long_running_tasks_and_workflows/`, `time_clocks_and_ordering/`,
`failure_detection_and_membership/`, `data_platform_architecture/`) and 7 case studies
(`design_distributed_cache`, `design_instagram_photo_sharing`, `design_local_delivery_service`,
`design_online_auction`, `design_online_judge`, `design_tinder`, `design_video_conferencing`).
This does not mean the section is complete. A later gap pass may find more. Record any new gap
here before you create its page, and do not link to a page that is only listed here.

---

## Cross-Reference Map (HLD → Deep Dives)

HLD modules intentionally stay at architectural-overview depth. For implementation details, point readers to:

| HLD Module | Backend Deep-Dive | Database Deep-Dive |
|-----------|------------------|--------------------|
| `microservices/` | `../backend/microservices_fundamentals/`, `../backend/api_gateway_patterns/`, `../backend/service_mesh_and_service_discovery/` | — |
| `message_queues/` | `../backend/kafka_deep_dive/`, `../backend/event_driven_fundamentals/`, `../backend/messaging_patterns/` | — |
| `caching/` | `../backend/caching_strategies_deep_dive/` | `../database/database_caching_patterns/`, `../database/key_value_stores/` |
| `database_design/` | `../backend/database_internals_and_indexing/`, `../backend/query_optimization/` | `../database/README.md` (all 34 modules) |
| `database_sharding/` | — | `../database/sharding_and_partitioning/` |
| `cap_theorem/` | — | `../database/consistency_models_and_consensus/`, `../database/database_fundamentals/` |
| `rate_limiting/` | `../backend/rate_limiting_in_depth/` | — |
| `api_design/` | `../backend/rest_api_design/`, `../backend/grpc_and_protobuf/`, `../backend/graphql/` | — |
| `consistent_hashing/` | — | `../database/sharding_and_partitioning/` |
| `consensus_algorithms/` | `../backend/kafka_deep_dive/` | `../database/consistency_models_and_consensus/` |
| `load_balancing/` | `../backend/fault_tolerance_patterns/` | — |
| `event_sourcing_cqrs/` | `../backend/event_sourcing_and_cqrs/` | `../database/polyglot_persistence_patterns/` |
| `distributed_transactions/` | `../backend/distributed_transactions_and_consistency/` | `../database/distributed_transactions/` |
| `observability/` | `../backend/observability_and_monitoring/`, `../devops/observability_tracing_and_otel/`, `../devops/observability_metrics_prometheus/`, `../devops/observability_logging/` | — |
| `security_and_auth/` | `../backend/auth_and_authorization_systems/`, `../backend/backend_security_owasp/`, `../spring/spring_security_architecture/`, `../spring/spring_security_jwt_oauth/` | — |
| `resilience_patterns/` | `../backend/fault_tolerance_patterns/`, `../spring/case_studies/cross_cutting/resilience4j_patterns.md` | — |
| `large_blobs_and_media_pipelines/` | `../backend/file_upload_and_media_handling/` | — |
| `scaling_reads_and_writes/` | `../backend/workload_characterization_and_load_modelling/` | `../database/indexing_deep_dive/`, `../database/replication_and_high_availability/` |
| `search_systems/` | `../backend/search_integration_patterns/` | `../database/search_engines/`, `../database/elasticsearch_internals/` |
| `long_running_tasks_and_workflows/` | `../backend/durable_execution_and_workflows/`, `../backend/messaging_patterns/` | — |
| `time_clocks_and_ordering/` | — | `../database/consistency_models_and_consensus/` |
| `failure_detection_and_membership/` | `../backend/service_mesh_and_service_discovery/` | — |
| `data_platform_architecture/` | `../backend/privacy_engineering_and_data_lifecycle/` | `../database/analytical_databases_and_olap/`, `../database/lakehouse_and_table_formats/` |

The payment, wallet and exchange case studies (`case_studies/design_payment_system.md`, `case_studies/design_digital_wallet.md`, `case_studies/design_stock_exchange.md`) stay here. The domain concepts and the dated rule facts live in `../fintech/` (see `../fintech/CLAUDE.md`). Do not add rail or regulation facts to these studies. Link the `fintech/` module that owns the fact.

---

## Adding a New HLD Module

1. Create `<module_name>/<module_name>.md` — 14-section template
2. Stay at architectural-overview depth — no framework-specific code
3. Minimum 15 Q&As (the hard floor in root `CLAUDE.md`)
4. Add cross-references in the Cross-Reference Map above pointing to backend/ and database/ for deeper dives
5. Update `README.md` HLD module table
6. Add the module dir to `STUDY_ORDER.hld` in `game/app.js` at its learning-path position —
   a module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
7. Add the module and EVERY file it owns to the `<!-- study-paths -->` block in
   this section's `README.md`, tagging each file with the tiers it belongs to (`-` for
   Full path only; the module page must carry every tier the module is in). **Do NOT put
   a block in the module page — content files hold only content.** Then run
   `python3 game/extract.py --write-paths` to regenerate the README's tier tables
8. Update root `README.md` HLD section
9. Update `hld/CLAUDE.md` module table (this file)

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
