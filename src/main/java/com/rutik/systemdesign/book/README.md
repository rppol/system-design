# Book Summaries — Read the Section, Skip the Book

> In-depth, chapter-by-chapter summaries of foundational engineering books — written so
> that reading the summary is as close as possible to reading the book itself, then mapped
> back into the deep-dive modules elsewhere in this repository.

---

## What This Section Is

Most sections in this repo are organized by *topic* (caching, replication, indexing). This
section is organized by *book*. Each book gets its own folder, and inside it every chapter
gets its own in-depth write-up that **follows the book's own narrative and section order**.

The goal is twofold:

1. **Standalone:** you can read a book's folder cover-to-cover and walk away with the
   author's full argument — every concept, example, tradeoff, and pitfall — without owning
   the book.
2. **A map back into the repo:** every chapter ends with cross-links into the deeper
   "how to build it" modules (`database/`, `hld/`, `backend/`, `devops/`) so you can drill
   from the book's framing into production-grade detail.

This is deliberately different from the topic sections: it preserves *the author's lens*.
Kleppmann's chapter on transactions teaches isolation levels through anomalies (lost
update, write skew, phantoms); our `database/concurrency_control_and_locking/` module
teaches the same locks through PostgreSQL internals. Both are valuable; this section is the
former.

---

## Books

| Book | Author | Folder | Chapters | Status |
|------|--------|--------|----------|--------|
| Designing Data-Intensive Applications | Martin Kleppmann | [designing_data_intensive_applications/](designing_data_intensive_applications/README.md) | 12 (+ preface) | In progress |

More books may be added over time (e.g. *Database Internals*, *Streaming Systems*). Each
new book follows the same folder-per-chapter convention — see [CLAUDE.md](CLAUDE.md).

---

## How to Read a Book Folder

Open the book's `README.md` first: it carries the book's thesis, a part map, a chapter
table, and a recommended reading path. Then read chapters in order — each one opens with a
**Chapter Map** (where it sits, what it builds on) and a **Big Question** (the problem it
exists to answer), so you always know why you're reading it.

---

## Cross-Reference Map

| When the book discusses… | Drill deeper in… |
|--------------------------|------------------|
| Replication, leaders/followers, quorums | [database/replication_and_high_availability/](../database/replication_and_high_availability/README.md) |
| Partitioning / sharding | [database/sharding_and_partitioning/](../database/sharding_and_partitioning/README.md) |
| Transactions, isolation, MVCC | [database/concurrency_control_and_locking/](../database/concurrency_control_and_locking/README.md) |
| Storage engines (B-tree / LSM) | [database/storage_engines_internals/](../database/storage_engines_internals/README.md) |
| Consensus, linearizability | [database/consistency_models_and_consensus/](../database/consistency_models_and_consensus/README.md) |
| Distributed-system theory (CAP, etc.) | [hld/](../hld/README.md) |
| Streaming / messaging / Kafka | [backend/](../backend/CLAUDE.md), [devops/](../devops/README.md) |

---

## Related Sections

- [Database Engineering](../database/README.md) — production-depth on storage, replication, sharding, transactions
- [High-Level Design](../hld/README.md) — distributed-systems concepts and the interview framework
- [Backend Engineering](../backend/CLAUDE.md) — networking, messaging, microservices, resilience
