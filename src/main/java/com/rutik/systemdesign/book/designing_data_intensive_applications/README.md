# Designing Data-Intensive Applications (DDIA)

> Martin Kleppmann · O'Reilly · "The big ideas behind reliable, scalable, and maintainable
> systems." A chapter-by-chapter, in-depth summary — read this folder in order and you have
> read the book.

---

## The Book's Thesis

Modern applications are **data-intensive** rather than compute-intensive: the hard part is
not raw CPU but the *amount* of data, its *complexity*, and the *speed at which it changes*.
No single tool does it all anymore — applications stitch together datastores, caches,
search indexes, stream processors, and batch pipelines. DDIA is about the **principles**
that let you reason about, combine, and trust those tools, organized around three concerns
that recur on every page:

- **Reliability** — works correctly even when things (hardware, software, humans) go wrong.
- **Scalability** — has strategies to keep performance acceptable as load grows.
- **Maintainability** — many different people can work on it productively over time.

The deeper through-line of Parts II and III: **everything is a question of guarantees**.
What does the system promise when a node dies, a network drops packets, or two writes race?
Most of the book is spent making those guarantees precise and showing what they cost.

---

## The Three Parts (ASCII Map)

```
DESIGNING DATA-INTENSIVE APPLICATIONS
│
├─ PART I — FOUNDATIONS OF DATA SYSTEMS         (single machine, conceptual groundwork)
│   ├─ Ch 1  Reliable, Scalable, Maintainable   the three concerns; percentiles
│   ├─ Ch 2  Data Models & Query Languages       relational vs document vs graph
│   ├─ Ch 3  Storage & Retrieval                 LSM-trees vs B-trees; OLTP vs OLAP
│   └─ Ch 4  Encoding & Evolution                schema evolution; dataflow modes
│
├─ PART II — DISTRIBUTED DATA                    (many machines: why & how it breaks)
│   ├─ Ch 5  Replication                         leaders, lag, multi-leader, quorums
│   ├─ Ch 6  Partitioning                        sharding, secondary indexes, rebalancing
│   ├─ Ch 7  Transactions                        ACID, weak isolation, serializability
│   ├─ Ch 8  Trouble with Distributed Systems    networks, clocks, truth & lies
│   └─ Ch 9  Consistency & Consensus             linearizability, ordering, consensus
│
└─ PART III — DERIVED DATA                       (combining systems into a whole)
    ├─ Ch 10 Batch Processing                    MapReduce, dataflow engines
    ├─ Ch 11 Stream Processing                   logs, CDC, event time, exactly-once
    └─ Ch 12 The Future of Data Systems          unbundling, correctness, ethics
```

Part I reasons about a *single node*. Part II asks what changes when data lives on *many*
nodes (the answer: almost everything). Part III shows how to *compose* multiple systems —
batch and stream — into a correct, maintainable whole, then closes on ethics.

---

## Chapter Index

| # | Chapter | Folder | One-line summary | Repo deep-dive |
|---|---------|--------|------------------|----------------|
| — | Preface & Book Map | [00_preface_and_book_map/](00_preface_and_book_map/README.md) | What "data-intensive" means; how the book is structured | [hld/](../../hld/README.md) |
| 1 | Reliable, Scalable, Maintainable | [01_reliable_scalable_maintainable/](01_reliable_scalable_maintainable/README.md) | The three concerns; faults; load; percentiles | [hld/scalability/](../../hld/scalability/README.md) |
| 2 | Data Models & Query Languages | [02_data_models_and_query_languages/](02_data_models_and_query_languages/README.md) | Relational vs document vs graph; declarative queries | [database/schema_design_and_normalization/](../../database/schema_design_and_normalization/README.md) |
| 3 | Storage & Retrieval | [03_storage_and_retrieval/](03_storage_and_retrieval/README.md) | LSM-trees vs B-trees; OLTP vs OLAP; columnar | [database/storage_engines_internals/](../../database/storage_engines_internals/README.md) |
| 4 | Encoding & Evolution | [04_encoding_and_evolution/](04_encoding_and_evolution/README.md) | Avro/Protobuf/Thrift; backward/forward compat | [backend/](../../backend/CLAUDE.md) |
| 5 | Replication | [05_replication/](05_replication/README.md) | Leaders/followers; replication lag; quorums | [database/replication_and_high_availability/](../../database/replication_and_high_availability/README.md) |
| 6 | Partitioning | [06_partitioning/](06_partitioning/README.md) | Range vs hash; secondary indexes; rebalancing | [database/sharding_and_partitioning/](../../database/sharding_and_partitioning/README.md) |
| 7 | Transactions | [07_transactions/](07_transactions/README.md) | ACID; weak isolation; SSI; write skew | [database/concurrency_control_and_locking/](../../database/concurrency_control_and_locking/README.md) |
| 8 | Trouble with Distributed Systems | [08_trouble_with_distributed_systems/](08_trouble_with_distributed_systems/README.md) | Unreliable networks, clocks, process pauses | [hld/](../../hld/README.md) |
| 9 | Consistency & Consensus | [09_consistency_and_consensus/](09_consistency_and_consensus/README.md) | Linearizability; ordering; 2PC; consensus | [database/consistency_models_and_consensus/](../../database/consistency_models_and_consensus/README.md) |
| 10 | Batch Processing | [10_batch_processing/](10_batch_processing/README.md) | Unix tools; MapReduce; dataflow engines | [devops/](../../devops/README.md) |
| 11 | Stream Processing | [11_stream_processing/](11_stream_processing/README.md) | Logs; CDC; windowing; exactly-once | [backend/](../../backend/CLAUDE.md) |
| 12 | The Future of Data Systems | [12_future_of_data_systems/](12_future_of_data_systems/README.md) | Unbundling; correctness; ethics | [hld/](../../hld/README.md) |

---

## How to Read This (Reading Paths)

- **Cover to cover (recommended):** preface → Ch 1 → … → Ch 12, the book's intended order.
  Each part builds on the previous one.
- **Distributed-systems crash course:** Ch 5 → 6 → 7 → 8 → 9. The dense, interview-critical
  core. Read Ch 8 before Ch 9 — consensus only makes sense once you accept how badly
  networks and clocks behave.
- **"I build CRUD apps" path:** Ch 1 → 2 → 3 → 7. Data models, storage, and transactions
  cover most day-to-day backend work.
- **Data-engineering path:** Ch 3 (columnar) → 10 → 11 → 12. Batch and stream processing.

---

## Build Manifest

Per-file build status for this book. Update the row to `done` the moment a chapter file is
completed and diagram-linted.

| # | File | Status |
|---|------|--------|
| — | `00_preface_and_book_map/README.md` | done |
| 1 | `01_reliable_scalable_maintainable/README.md` | done |
| 2 | `02_data_models_and_query_languages/README.md` | done |
| 3 | `03_storage_and_retrieval/README.md` | done |
| 4 | `04_encoding_and_evolution/README.md` | done |
| 5 | `05_replication/README.md` | done |
| 6 | `06_partitioning/README.md` | done |
| 7 | `07_transactions/README.md` | done |
| 8 | `08_trouble_with_distributed_systems/README.md` | done |
| 9 | `09_consistency_and_consensus/README.md` | done |
| 10 | `10_batch_processing/README.md` | done |
| 11 | `11_stream_processing/README.md` | done |
| 12 | `12_future_of_data_systems/README.md` | done |

---

## Cross-Reference Map (DDIA → repo deep dives)

| DDIA concept | Primary deep-dive module |
|--------------|--------------------------|
| Percentiles, tail latency, load | [hld/scalability/](../../hld/scalability/README.md) |
| LSM-tree vs B-tree internals | [database/storage_engines_internals/](../../database/storage_engines_internals/README.md) |
| Indexing structures | [database/indexing_deep_dive/](../../database/indexing_deep_dive/README.md) |
| Replication topologies & failover | [database/replication_and_high_availability/](../../database/replication_and_high_availability/README.md) |
| Consistent hashing, shard keys | [database/sharding_and_partitioning/](../../database/sharding_and_partitioning/README.md) |
| Isolation levels, MVCC, locks | [database/concurrency_control_and_locking/](../../database/concurrency_control_and_locking/README.md) |
| 2PC, Saga, outbox | [database/distributed_transactions/](../../database/distributed_transactions/README.md) |
| Linearizability, Raft, CRDTs | [database/consistency_models_and_consensus/](../../database/consistency_models_and_consensus/README.md) |
| Caching patterns | [database/database_caching_patterns/](../../database/database_caching_patterns/README.md) |
