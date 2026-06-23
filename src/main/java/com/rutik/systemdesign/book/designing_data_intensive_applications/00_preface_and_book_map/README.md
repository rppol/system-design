# Preface & Book Map

> DDIA (Kleppmann) · Front matter · the lens through which to read every chapter

## Chapter Map

The preface establishes *why* the book exists and *how* it is organized. You should read it
before the chapters because it gives you the vocabulary ("data-intensive", "data system",
"derived data") and the three-concern frame (reliability, scalability, maintainability)
that every later chapter quietly returns to.

**TL;DR:**
- Applications today are limited by **data** (amount, complexity, rate of change), not CPU.
- No single tool covers all needs; engineers **compose** datastores, caches, indexes,
  queues, and processors — and the glue logic is where the hard reasoning lives.
- The book is a tour of **principles and tradeoffs**, not a product manual; it teaches you
  to evaluate tools you've never seen by asking what guarantees they make.

## The Big Question

> "How do I combine a pile of specialized data tools into one application that is still
> reliable, scalable, and maintainable — and how do I know what it actually guarantees?"

Analogy: a single database used to be a Swiss Army knife. Today's stack is a full toolbox —
Postgres, Redis, Elasticsearch, Kafka, Spark. The preface argues the *skill* is no longer
"know one tool deeply" but "understand the shared principles so you can reason about any
combination of them." This book teaches those principles.

---

## P.1 What "Data-Intensive" Means

A system is **compute-intensive** when CPU cycles are the bottleneck, and **data-intensive**
when the bottleneck is the data: how much there is, how complex it is, or how fast it
changes. Kleppmann's claim is that the vast majority of modern applications are the latter —
raw CPU is rarely the limiting factor; coordinating, storing, moving, and trusting data is.

Typical building blocks a data-intensive app stitches together:

- **Databases** — store data so it (or another app) can find it again later.
- **Caches** — remember the result of an expensive operation to speed up reads.
- **Search indexes** — let users filter/search data by keyword or facets.
- **Stream processing** — send a message to another process, handled asynchronously.
- **Batch processing** — periodically crunch a large accumulated pile of data.

These categories blur (Redis is a cache *and* a message broker; Kafka is a log *and* a
database-like store), which is exactly why principles beat product knowledge.

## P.2 The Three Concerns (the book's spine)

Every design decision in the book is judged against three properties. They recur so often
that you should memorize them now:

| Concern | Plain-English question | Where it's developed |
|---------|------------------------|----------------------|
| **Reliability** | Does it keep working correctly when things go wrong? | Ch 1, then all of Part II |
| **Scalability** | As load grows, do we have reasonable ways to cope? | Ch 1 (load/percentiles), Ch 6 |
| **Maintainability** | Can many people work on it productively over years? | Ch 1, Ch 4 (evolvability), Ch 12 |

A "fault" is one component deviating from spec; a "failure" is the whole system stopping.
Reliability is about *tolerating faults so they don't become failures*. This vocabulary is
introduced here and made precise in Chapter 1.

## P.3 The Map of the Book (three parts)

Kleppmann lays out the three-part structure in the preface; internalizing it tells you what
question each chapter is answering.

```
PART I  — FOUNDATIONS (one machine)
   The fundamental ideas that apply whether you run on a single node or a cluster:
   data models, storage engines, and encoding. No distribution yet.

PART II — DISTRIBUTED DATA (many machines)
   What happens when data is spread or replicated across machines for scalability,
   fault tolerance, or low latency. This is where the hard problems live: replication,
   partitioning, transactions, unreliable networks/clocks, consistency & consensus.

PART III — DERIVED DATA (combining systems)
   Systems that derive one dataset from another: batch and stream processing, and how
   to integrate many storage systems into a coherent, correct application.
```

**Systems of record vs derived data** — a distinction introduced here and central to
Part III: a *system of record* holds the authoritative, canonical version of data (written
once, the source of truth). *Derived data* is the result of transforming or combining data
from a system of record — caches, denormalized values, indexes, materialized views. Derived
data is redundant by definition: you could rebuild it from the source. That redundancy is
what makes read performance good and is the unifying idea behind Part III.

## P.4 Who the Book Is For & What It Is Not

It targets engineers who build applications on top of data systems and want to understand
what's happening underneath. It is explicitly **not** a tutorial for any single tool, **not**
an operations manual, and **not** introductory — it assumes you've used a database and
written application code. It favors **timeless principles** over current product names,
which is why a summary like this one ages well.

---

## Visual Intuition

The single most useful mental model from the front matter is the data-system-as-composite:
your "application" is really a façade over many specialized stores, and you (the app code)
are responsible for the consistency between them.

```
            ┌──────────────────────────────────────────────┐
  client →  │              YOUR APPLICATION CODE            │   ← you own the glue & the
            │  (the "data system" the user actually sees)   │     guarantees BETWEEN stores
            └───┬───────┬──────────┬───────────┬────────────┘
                │       │          │           │
            ┌───▼──┐ ┌──▼───┐ ┌────▼────┐ ┌────▼─────┐
            │  DB  │ │Cache │ │ Search  │ │  Queue   │
            │(SoR) │ │(deriv)│ │ (deriv) │ │ (stream) │
            └──────┘ └──────┘ └─────────┘ └──────────┘
   SoR = system of record (authoritative)   deriv = derived data (rebuildable)
```

Caption: the preface's core picture. The DB is the system of record; cache and search index
are *derived* from it and could be rebuilt. Keeping derived data in sync with the system of
record is the application's job — and the subject of most of Parts II and III.

---

## Key Concepts Glossary

- **Data-intensive** — limited by data volume/complexity/change-rate, not CPU.
- **Data system** — the composite of databases, caches, indexes, queues, and app glue that
  together behave as one system to the user.
- **Reliability** — continuing to work correctly in the face of faults.
- **Fault** — one component deviating from its spec. **Failure** — the system as a whole
  stopping service. Goal: prevent faults from causing failures.
- **Scalability** — having strategies to keep performance acceptable as load grows.
- **Maintainability** — operability, simplicity, and evolvability over the system's life.
- **System of record (source of truth)** — the authoritative copy of data.
- **Derived data** — data computed from a system of record (caches, indexes, materialized
  views); redundant and rebuildable.

---

## Tradeoffs & Decision Tables

| Old world | New world (the book's premise) |
|-----------|-------------------------------|
| One database does everything | Many specialized tools composed together |
| "Learn this product" | "Learn the principles, evaluate any product" |
| Correctness = the DB's problem | Correctness = your problem, across stores |
| Compute is the bottleneck | Data (volume/complexity/velocity) is the bottleneck |

---

## Common Pitfalls / War Stories

- **Treating derived data as authoritative.** Writing to a cache or search index as if it
  were the source of truth leads to unrecoverable divergence. Derived data must always be
  rebuildable from the system of record; if you can't rebuild it, it's not derived — it's a
  second, unmanaged source of truth.
- **Assuming the database guarantees end-to-end correctness.** Once you have more than one
  store, no single product can promise that they agree. The application owns that contract.
- **Buying tools by buzzword.** The preface's whole motivation: without principles you
  cannot tell whether "eventually consistent" or "ACID" actually means what you need.

---

## Real-World Systems Referenced

The preface name-drops the categories rather than products, but the book as a whole runs on
PostgreSQL, MySQL, Oracle, MongoDB, Cassandra, Redis, Memcached, Elasticsearch, Lucene,
HBase, Riak, VoltDB, Kafka, Hadoop/HDFS, Spark, and ZooKeeper — drawn from the author's time
at LinkedIn building large-scale infrastructure.

---

## Summary

Applications are data-intensive: dominated by the challenge of storing, moving, and trusting
data spread across many specialized tools. Good engineering is no longer about mastering one
product but about understanding the shared **principles** and **tradeoffs** so you can reason
about any composition of tools and know what it guarantees. Three concerns — reliability,
scalability, maintainability — frame every decision. The book proceeds in three parts:
foundations on one machine, the hard realities of distributing data, and the art of deriving
and integrating data across systems.

---

## Interview Questions

**What does "data-intensive" mean, and why does the book claim most modern apps are data-intensive rather than compute-intensive?**
Data-intensive means the binding constraint is the *data* — its volume, its complexity, or how fast it changes — not CPU cycles. Kleppmann argues most apps hit data limits first: a typical web service spends its effort coordinating databases, caches, and indexes, not doing heavy math. The practical consequence is that the skills that matter are about storage, replication, and consistency, not micro-optimizing algorithms.

**Name the three core concerns the book is organized around and give a one-line definition of each.**
Reliability (works correctly despite faults), scalability (has strategies to cope as load grows), and maintainability (people can work on it productively over time). They're not independent — an unscalable system fails under load (a reliability problem), and an unmaintainable one accumulates bugs. The book returns to this triad in nearly every chapter.

**What is the difference between a fault and a failure, and why does the distinction matter?**
A fault is one component deviating from its spec (a disk dies, a function throws); a failure is the system as a whole stopping service. The distinction matters because the goal of reliability engineering is to *tolerate faults so they don't escalate into failures* — you build fault-tolerance, not fault-prevention, because faults are inevitable. Netflix's Chaos Monkey embodies this: inject faults deliberately to prove they don't become failures.

**What is the difference between a system of record and derived data?**
A system of record holds the authoritative, canonical copy of data — the source of truth, written once. Derived data is computed *from* a system of record: caches, denormalized fields, search indexes, materialized views. The key property is that derived data is redundant and rebuildable — if it's lost or corrupted, you regenerate it from the source. This distinction is the conceptual backbone of Part III.

**Why does the book teach principles instead of specific products?**
Because the tool landscape changes constantly and the categories blur (Redis is a cache and a broker; Kafka is a log and a store). If you only know products, you can't evaluate a new one. If you understand the underlying principles — what guarantees a quorum gives, what isolation level prevents which anomaly — you can read any system's docs and know what it actually promises. Principles age slowly; product names age fast.

**Why is "no single tool does it all anymore" the book's motivating premise?**
Different access patterns need different engines: OLTP databases for point reads/writes, search indexes for full-text, caches for hot reads, stream processors for real-time, batch systems for analytics. One tool optimized for all of these would be optimized for none. So applications compose specialized tools — and that composition creates the central problem: keeping them consistent. The whole book follows from this premise.

**If you compose several data stores, who is responsible for the consistency between them?**
Your application code is. No individual product can guarantee that the database, the cache, and the search index agree with each other, because none of them knows about the others. The application that writes to all of them owns the contract that they stay in sync — which is why Part III spends so much effort on dataflow and exactly-once semantics.

**What does the book mean by "maintainability", and what three sub-properties make it up?**
Maintainability is the ease with which different people can work on the system over its lifetime. It decomposes into operability (easy for operations to keep it running), simplicity (easy for new engineers to understand — minimizing accidental complexity), and evolvability (easy to change as requirements shift, also called extensibility or plasticity). Most of a system's cost is in maintenance, not initial build, so this concern is economically the largest.

**Why does the preface say correctness becomes "your problem" rather than the database's?**
Because once data lives in more than one store, no single store can promise the set agrees. A classic bug: you update the database but the cache still serves the old value, or the search index lags. The database did its job correctly; the *system* is still wrong. The application straddling multiple stores is the only place that can enforce cross-store correctness.

**What kinds of building blocks does the preface enumerate, and how do they blur together?**
Databases (store and retrieve), caches (remember expensive results), search indexes (filter/search), stream processing (async messaging), and batch processing (periodic bulk crunching). They blur because real products span categories — Redis is a key-value store, a cache, and a message broker; Kafka is a message queue and a durable log that can act like a database. This blurring is precisely why category labels are less useful than understanding guarantees.

**Who is the intended audience, and what is the book explicitly NOT?**
It targets engineers who build on top of data systems and want to understand the internals and tradeoffs. It is not a single-tool tutorial, not an ops runbook, and not introductory — it assumes prior experience with databases and application code. Knowing what it isn't keeps you from expecting step-by-step product instructions.

**How does the three-part structure map onto a difficulty curve?**
Part I (foundations) reasons about a single machine — conceptually rich but no distribution. Part II (distributed data) is the hard core, where networks and clocks betray you. Part III (derived data) is about composition and is conceptually unifying. Difficulty peaks in Part II (Chapters 7–9), so if you're studying for interviews, that's where to spend the most time.

**Why does the author put "derived data" in its own part rather than treating it as an implementation detail?**
Because the insight that caches, indexes, and materialized views are all *the same thing* — data derived from a source via a transformation — is genuinely unifying. Once you see batch and stream processing as two ways of maintaining derived data, the whole zoo of tools (Hadoop, Spark, Kafka Streams, Flink) collapses into one mental model. Part III earns its place by delivering that unification.

**Give an example where ignoring the system-of-record vs derived-data distinction causes a production incident.**
A team writes user profile edits directly to Elasticsearch (the search index) for speed and "later" syncs back to Postgres. The sync breaks; now the index has edits the database never saw, and there's no way to rebuild the index because it *is* the source for those fields. Treating a derived store as authoritative destroyed rebuildability — the cardinal sin the preface warns against.

**Why is "compute-intensive vs data-intensive" a useful framing even though some systems are both?**
It directs your optimization effort to the actual bottleneck. If you assume compute is the limit, you'll micro-optimize code while the real cost is a chatty database or replication lag. Naming the dominant constraint up front — usually data — keeps architectural attention on storage, movement, and consistency, which is where data-intensive systems live or die.

---

## Cross-links in this repo

- [hld/ — distributed systems concepts & the interview framework](../../../hld/README.md)
- [database/ — production depth on every store the book discusses](../../../database/README.md)
- [hld/scalability/ — load, percentiles, scaling strategies (Ch 1 deep dive)](../../../hld/scalability/README.md)
- [backend/ — networking, messaging, microservices (Parts II–III)](../../../backend/CLAUDE.md)

## Further Reading

- Martin Kleppmann, *Designing Data-Intensive Applications*, O'Reilly — the book this folder summarizes.
- Each chapter's own "Further Reading" section in this folder points to the primary papers
  Kleppmann cites (Dynamo, Bigtable, Spanner, Raft, Paxos, Kafka, MapReduce, etc.).
