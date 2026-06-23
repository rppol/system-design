# Chapter 12: The Future of Data Systems

> Part III — Derived Data · DDIA (Kleppmann) · the synthesis chapter; builds on all of Ch 1–11

## Chapter Map

The closing chapter is Kleppmann's synthesis and opinion piece. Having shown that no single tool
does everything (Ch 1) and that all derived data is a materialized view of an event log (Ch 11), he
argues for a future where applications are built by **composing** specialized systems through
**dataflow**, treats the whole architecture as an **unbundled database**, confronts what
**correctness** actually requires in such systems, and ends — unusually for a technical book — on the
**ethics** of building data systems that increasingly shape people's lives.

**TL;DR:**
- **Data integration:** no tool fits all needs, so combine specialized tools, using **batch and
  stream processing** to keep all the derived datasets in sync from a common event log.
- **Unbundling the database:** compose the pieces a database does internally (storage, indexes,
  materialized views, replication) as separate systems wired together by dataflow — the database
  "turned inside out."
- **Aiming for correctness:** distributed correctness needs the **end-to-end argument** —
  application-level idempotence and integrity checks (exactly-once via dedup, "trust but verify"
  auditing) — not just lower-layer guarantees.
- **Doing the right thing:** with great data power come real harms (biased predictions, surveillance,
  loss of privacy and consent); engineers are responsible for the systems they build.

## The Big Question

> "I've learned all these techniques in isolation. How do they fit together into a coherent
> architecture for the next decade — and what's my responsibility for what that architecture *does*
> to people?"

Analogy: a modern data system is less like a single appliance and more like a city's plumbing — many
specialized pipes and reservoirs (databases, indexes, caches, queues) connected so water (data) flows
reliably to where it's needed. Chapter 12 is the city planner's view: how to lay the pipes
(dataflow), how to think of the whole network as one system (unbundling), how to guarantee the water
is clean (correctness), and the recognition that the plumbing now carries things powerful enough to
help or harm the people downstream (ethics).

---

## 12.1 Data Integration

The recurring reality: for any given problem there are several tools, each good at *some* access
patterns, and most non-trivial applications must **combine** them — a database of record, a search
index for full-text, a cache for hot reads, a warehouse for analytics, maybe a recommendation engine.
The integration challenge is keeping all these copies of (overlapping) data **consistent** as it
changes.

**Combining specialized tools by deriving data.** The answer, built up through Part III, is to pick
one authoritative source (the system of record / event log) and **derive** every other dataset from
it via batch and stream processing. This gives a clear **dataflow**: writes go to the log, and every
derived system (index, cache, warehouse) updates by consuming that log in order. The alternative —
applications doing scattered **dual writes** to each store — is fragile (races, partial failures) and
should be replaced by ordered derivation from a single source.

**Batch and stream processing unified.** Batch (bounded, reprocess everything) and stream (unbounded,
process incrementally) are increasingly two ends of one spectrum, not separate worlds: the same
derivation logic should ideally run both ways. **Stream processing** keeps derived data fresh with low
latency; **batch (re)processing** rebuilds derived datasets from scratch — essential for fixing bugs,
adopting new schemas, or creating brand-new views by replaying the full history. The ability to
**reprocess** existing data is what lets a system *evolve*: you can maintain the old derived view while
gradually building a new one from the same log, then switch over — schema migration without downtime,
at the architecture level. (This is the lambda-architecture idea — run batch and stream in parallel —
which Kleppmann notes is being simplified as engines unify the two.)

## 12.2 Unbundling Databases

A database bundles several features internally: durable storage, secondary indexes, materialized
views, replication, caching, a query language. Kleppmann's central architectural vision is to
**unbundle** these — to build a system where those features are provided by *separate*, specialized
components wired together by **dataflow / event logs**, rather than all locked inside one monolithic
database. He calls it turning the **database inside out**: the replication log (usually a hidden
internal detail) becomes the public, primary interface, and indexes/caches/views become explicit,
independently-managed derived systems subscribing to it.

**Composing data storage technologies.** The log-based approach makes the whole architecture analogous
to the internals of a single database, but at the scale of an organization: the **event log is the
"transaction log,"** and each derived store is like a database **index** kept in sync — except they're
separate products (Postgres + Elasticsearch + Redis + a warehouse) chosen for their strengths and
connected by CDC/streams. This is a **federation** of storage technologies that, together, behave like
one coherent database.

**Designing applications around dataflow.** In this model, application code becomes the **derivation
functions** that transform one dataset into another (the map/reduce/stream operators) — the wiring
between systems — while the systems themselves handle storage and serving. Reads and writes are
separated: **writes** append to the log; **reads** are served by whichever derived view is optimized
for that query (echoing event sourcing and CQRS — Command Query Responsibility Segregation, where the
write model and read models are deliberately different).

**Observing derived state.** Even the **read path** can be seen as a dataflow: a materialized view is
write-time precomputation; a query is read-time computation; and there's a spectrum between them
(precompute everything vs compute on demand). Kleppmann extends this to the client: a user's screen is
itself a derived view of server state, and technologies that **push** updates (subscribing to a stream
of changes rather than polling) extend the write-side dataflow all the way to the end user's device,
making the UI a live, continuously-updated view of the log.

## 12.3 Aiming for Correctness

In a loosely-coupled dataflow architecture, you can't rely on a single database's ACID transaction to
guarantee correctness across many systems — so how do you stay correct?

**The end-to-end argument.** A classic systems principle: a guarantee (like exactly-once delivery or
deduplication) is only truly correct if enforced at the **application level**, end to end — lower-layer
mechanisms (TCP retransmission, a database's internal retries) help but cannot fully substitute,
because they don't understand the application's notion of a duplicate or a valid operation. Example:
TCP deduplicates *packets*, but if a user clicks "pay" twice or a request is retried after a timeout,
only an application-level **idempotency key / unique request ID** can recognize and suppress the
*business* duplicate. Correctness must be designed at the boundary where the meaning lives.

**Enforcing constraints.** Uniqueness (one username), no-double-spend, and similar constraints are
exactly the consensus-flavored problems of Chapter 9. In a log-based system you can enforce them by
routing all operations on the same constrained value to the **same log partition** and processing them
**in order** by a single consumer — turning a distributed-uniqueness problem into a local, sequential
decision (the first request for a username in the ordered log wins; later ones are rejected). This is
how you get strong constraints without a global distributed transaction.

**Timeliness and integrity.** Kleppmann splits "consistency" into two needs that are often conflated:
**timeliness** (a read sees recent data — being *up to date*; staleness here is temporary and
self-correcting) and **integrity** (data is correct and not corrupted/contradictory — no lost or
duplicated updates; violations here are *permanent* and far worse). Dataflow systems often *relax
timeliness* (derived views lag a little) but must *never sacrifice integrity*. The good news:
exactly-once processing on a replayable log, with idempotent operations, preserves integrity even
while timeliness is eventual — so you can build correct systems on asynchronous dataflow.

**Trust, but verify.** Don't blindly trust that storage and transmission are perfect — disks corrupt
data silently, software has bugs, "durable" isn't absolute. Build systems that **audit** themselves:
continuously check integrity (checksums, reconciling derived views against the source by reprocessing,
end-to-end verification) so corruption is *detected* rather than silently propagated. An immutable
event log makes this auditing natural — you can always recompute a derived view and compare.

## 12.4 Doing the Right Thing

The book closes on **ethics**, arguing engineers are responsible for the consequences of the systems
they build, not just their technical correctness.

- **Predictive analytics and bias.** Algorithms increasingly make consequential decisions (credit,
  insurance, hiring, policing, parole). They can **encode and amplify existing bias** — a model trained
  on biased historical data perpetuates that discrimination at scale and with a false veneer of
  objectivity, creating self-reinforcing feedback loops (deny a loan → worse circumstances → "confirms"
  the model). "The algorithm decided" is not an absolution of responsibility.
- **Privacy, surveillance, and consent.** Mass data collection is **surveillance**, even when framed as
  "personalization" or "improving the service." Users rarely give meaningful informed **consent**
  (terms-of-service no one reads; data used for purposes never disclosed; data sold or breached). The
  data being collected is about *people*, and treating it as a neutral resource ("data is the new oil")
  ignores the power asymmetry and the harm of breaches and misuse.
- **Accountability and a call to responsibility.** Data has inertia — once collected, it persists, gets
  combined, and is repurposed beyond the original intent. Kleppmann urges engineers to practice **data
  minimization** (collect only what's needed), be honest about what's collected and why, build in
  deletion and security, and take ethical responsibility rather than hiding behind "we just build the
  tools." Just because something *can* be built doesn't mean it *should* be.

---

## Visual Intuition

```
THE DATABASE, TURNED INSIDE OUT (unbundling)

  BUNDLED (one monolithic DB):        UNBUNDLED (dataflow architecture):
  ┌──────────────────────────┐         writes ─▶ [ EVENT LOG ]  (the "transaction log",
  │  storage  indexes         │                       │            now the public interface)
  │  views    replication     │          ┌────────────┼────────────┬──────────────┐
  │  cache    query engine    │          ▼            ▼            ▼              ▼
  │   (all hidden internals)  │      [Postgres]  [Elasticsearch] [Redis]     [Warehouse]
  └──────────────────────────┘      system of    search index   cache       analytics
                                      record     (derived)      (derived)    (derived)
                                      each is a derived materialized view, kept in sync by the log
```

```
TIMELINESS vs INTEGRITY (relax one, never the other)

  TIMELINESS  = "is this read up to date?"   violation = TEMPORARY staleness (self-heals)
  INTEGRITY   = "is this data correct?"        violation = PERMANENT corruption (lost/dup)

  asynchronous dataflow:  timeliness  ──relaxed──▶ derived views lag a bit         OK
                          integrity   ──preserved─▶ exactly-once + idempotence      MUST
  ⇒ you CAN be eventually-timely AND always-correct; never trade away integrity for speed
```

Caption: the architectural thesis (the database inside out) and the correctness rule (eventual
timeliness is fine; broken integrity is not) — the two ideas that tie all of Part III together.

---

## Key Concepts Glossary

- **Data integration** — keeping multiple specialized stores consistent as data changes.
- **Derivation / derived dataset** — computing one dataset from an authoritative source.
- **Dataflow** — wiring systems together so changes propagate from a source log to derived stores.
- **Dual writes** — application writing to several stores directly (fragile; to be avoided).
- **Reprocessing** — rebuilding a derived dataset from the full history (enables schema evolution).
- **Lambda architecture** — running batch and stream paths in parallel (being unified away).
- **Unbundling the database** — providing a database's features as separate composed systems.
- **Database inside out** — making the (normally hidden) log the primary public interface.
- **Federation** — combining heterogeneous storage technologies to behave as one system.
- **Derivation function** — application code transforming one dataset into another.
- **CQRS (Command Query Responsibility Segregation)** — separate write model and read models.
- **Materialized view vs query** — write-time vs read-time computation (a spectrum).
- **End-to-end argument** — correctness guarantees must be enforced at the application level.
- **Idempotency key / unique request ID** — application-level dedup of business operations.
- **Constraint enforcement via ordered log** — route to one partition + process in order.
- **Timeliness** — being up to date (temporary, self-correcting if violated).
- **Integrity** — being correct/uncorrupted (permanent damage if violated; must be preserved).
- **Trust but verify / auditing** — continuously check integrity rather than assume perfection.
- **Predictive analytics bias** — models encoding and amplifying historical discrimination.
- **Surveillance / consent / data minimization** — the ethics of collecting data about people.

---

## Tradeoffs & Decision Tables

| | Bundled (monolithic DB) | Unbundled (dataflow) |
|---|---|---|
| Features | All inside one system | Composed specialized systems |
| Source of truth | The database | The event log |
| Flexibility | Limited to one tool's strengths | Best tool per access pattern |
| Complexity | Lower (one system) | Higher (integration, ordering) |
| Evolvability | Schema migrations | Reprocess log into new views |

| | Timeliness | Integrity |
|---|---|---|
| Question | Up to date? | Correct / uncorrupted? |
| Violation | Temporary staleness | Permanent corruption |
| In async dataflow | Acceptable to relax | Must always preserve |

| Correctness need | Lower-layer help | Required end-to-end mechanism |
|------------------|------------------|------------------------------|
| No duplicate operation | TCP dedups packets | Idempotency key / request ID |
| Uniqueness constraint | — | Ordered single-partition processing |
| Detect corruption | Disk/network checksums | Application auditing / reprocessing |

---

## Common Pitfalls / War Stories

- **Dual writes instead of derivation.** Application code writing the same change to the database and
  the search index (and the cache, and the warehouse) drifts into permanent inconsistency under races
  and partial failures. Derive every secondary store from one ordered log (CDC/streams) instead.
- **Relying on lower layers for application-level correctness.** Assuming TCP, or a database's internal
  retries, gives you exactly-once *business* semantics — then a retried "pay" request double-charges
  because no layer below the application understands what a duplicate *payment* is. Add idempotency keys
  at the application boundary.
- **Conflating timeliness and integrity.** Teams panic over slightly stale derived views (a timeliness
  issue that self-heals) while under-investing in integrity (lost/duplicated updates that corrupt data
  permanently). Relax timeliness deliberately; never compromise integrity.
- **Trusting "durable" storage blindly.** Disks bit-rot, "fsync" lies on some hardware, replication can
  propagate corruption. Without auditing, silent corruption spreads to every derived view. Build
  continuous integrity checks and use the immutable log to recompute-and-compare.
- **Inability to reprocess.** If your architecture can't rebuild derived data from history (non-
  replayable broker, no retained log), you can't fix a processing bug retroactively or migrate to a new
  schema without a painful, error-prone ad-hoc backfill. Retain the log; design for reprocessing.
- **Ethical abdication.** Shipping a biased predictive model or an over-collecting data pipeline and
  rationalizing "the algorithm decided" or "we just build the tools." Kleppmann's explicit warning:
  engineers bear responsibility; practice data minimization, transparency, and consent.

---

## Real-World Systems Referenced

Apache Kafka (the unifying log), CDC tooling (Debezium, Kafka Connect), Apache Samza/Kafka Streams/
Flink (dataflow derivation), Elasticsearch/Postgres/Redis/data warehouses (federated specialized
stores), the lambda architecture (Storm + Hadoop), CQRS/event-sourcing frameworks, Datomic
(read-as-of, immutable history); the broader discussion cites predictive-analytics, credit-scoring,
and surveillance systems as cautionary cases rather than products to emulate.

---

## Summary

The final chapter synthesizes the whole book into an architectural vision and an ethical charge.
Because **no single tool fits every need**, applications must integrate specialized systems — and the
clean way is to designate one authoritative **event log** and **derive** every other dataset (search
index, cache, warehouse) from it via **batch and stream processing**, replacing fragile dual writes;
the ability to **reprocess** history is what lets the architecture evolve and adopt new schemas.
Kleppmann frames this as **unbundling the database** — turning it "inside out" so the replication log
becomes the public interface and indexes/views/caches become explicit, independently-managed derived
systems, with application code acting as the **derivation functions** wiring them together and reads
served by whichever view fits the query (CQRS). Correctness in such loosely-coupled systems comes from
the **end-to-end argument** (enforce idempotence and constraints at the application level, e.g. via
unique request IDs and ordered single-partition processing), from distinguishing **timeliness**
(relaxable, self-healing) from **integrity** (must always be preserved), and from **auditing** ("trust
but verify") rather than assuming storage is perfect. Finally, Kleppmann insists engineers are
**ethically responsible** for what their systems do — confronting algorithmic bias, surveillance, and
the erosion of privacy and consent — because just because a system *can* be built does not mean it
*should* be.

---

## Interview Questions

**What does "unbundling the database" mean?**
It means taking the features a database normally provides internally — durable storage, secondary indexes, materialized views, replication, caching — and providing them as *separate*, specialized systems wired together by dataflow (event logs / change streams), instead of locking them inside one monolithic database. Kleppmann calls it turning the database "inside out": the replication log, normally a hidden implementation detail, becomes the primary public interface, and each index, cache, or view becomes an explicit derived system that subscribes to that log and is kept in sync — a federation of best-of-breed tools behaving together like one coherent database.

**Why does the book advocate deriving data from a single log instead of dual writes?**
Because keeping multiple stores (database, search index, cache, warehouse) consistent by having the application write to each one directly is fragile: the writes aren't atomic, so they can race (applied in different orders to different stores) or partially fail (one succeeds, another doesn't), causing permanent, hard-to-detect divergence. Deriving from a single authoritative log means all changes flow through one ordered stream, and every derived store applies the same changes in the same order, so they converge — and any derived store can be rebuilt from the log if it gets out of sync.

**What is the end-to-end argument, and how does it apply to exactly-once processing?**
The end-to-end argument states that a correctness guarantee is only truly achieved if it's enforced at the application level, end to end, because lower layers don't understand the application's semantics. Applied to exactly-once: TCP can deduplicate *packets* and a database can retry internally, but neither knows what a duplicate *business operation* is — so a user double-clicking "pay" or a client retrying after a timeout will double-charge unless the application itself recognizes the duplicate via an idempotency key or unique request ID. Real exactly-once must be designed at the boundary where the operation's meaning lives.

**Distinguish timeliness from integrity, and explain which one asynchronous dataflow may relax.**
Timeliness is whether a read reflects recent writes — being up to date; if violated, the staleness is temporary and self-corrects as derived views catch up. Integrity is whether the data is correct and uncorrupted — no lost or duplicated updates, no contradictions; if violated, the damage is permanent and far more serious. Asynchronous dataflow architectures routinely *relax timeliness* (derived views lag slightly behind the source) but must *never* relax integrity. The encouraging result is that exactly-once processing on a replayable log with idempotent operations preserves integrity even while timeliness is only eventual.

**How can you enforce a uniqueness constraint in a log-based dataflow system without a distributed transaction?**
By routing all operations that touch the same constrained value to the *same* log partition and having a single consumer process that partition's events *in order*. This converts a distributed agreement problem into a local, sequential decision: the consumer sees requests for a given username one at a time in a defined order, grants the first, and rejects every later one — achieving the consensus-like guarantee of uniqueness without a global distributed transaction. It works because ordering within a partition plus single-threaded processing gives a total order over all decisions about that value.

**Why does the book end on ethics, and what is its central argument there?**
Because data systems increasingly make or inform consequential decisions about people — credit, insurance, hiring, policing — so their design has real human impact beyond technical correctness. The central argument is that engineers are responsible for the consequences of what they build: predictive models can encode and amplify historical bias under a false veneer of objectivity; mass data collection is surveillance that users rarely meaningfully consent to; and "the algorithm decided" or "we just build the tools" is not an absolution. Kleppmann urges data minimization, transparency, consent, and the recognition that something being buildable doesn't make it ethical to build.

**How do batch and stream processing relate in the book's vision of data integration?**
They're presented as two ends of one spectrum rather than separate worlds: stream processing keeps derived data fresh with low latency by handling events incrementally, while batch processing rebuilds derived datasets from the full history. Ideally the same derivation logic runs both ways. The crucial shared capability is *reprocessing* — replaying historical data — which lets you fix a bug, adopt a new schema, or build an entirely new derived view by reprocessing the log, all while keeping the old view running until you switch over. This is what makes the architecture evolvable (and simplifies the older lambda architecture as engines unify the two paths).

**What is CQRS, and how does it fit the unbundling vision?**
CQRS (Command Query Responsibility Segregation) separates the write model from the read model: writes (commands) are handled one way — appended to an event log — while reads (queries) are served by separate, possibly multiple, read-optimized views derived from that log. It fits unbundling perfectly because the unbundled architecture *is* this separation at scale: writes append to the log, and any number of derived stores (a relational view, a search index, a cache) materialize the read side, each optimized for its query pattern. It frees the read and write sides to use entirely different data models and storage technologies.

**What does "trust, but verify" mean for data systems, and why is auditing important?**
It means you should not blindly assume that storage and transmission are perfect — disks silently corrupt data, "durable" writes can be lost on some hardware, and software has bugs — so systems should continuously check their own integrity rather than trust the lower layers. Auditing (checksums, reconciling derived views against the source by reprocessing, end-to-end verification) ensures corruption is *detected* instead of silently propagating into every derived dataset. An immutable, replayable event log makes auditing natural: you can always recompute a derived view from the log and compare it against the live one to catch drift or corruption.

**How can algorithmic decision-making encode and amplify bias?**
A model trained on historical data learns the patterns in that data, including the discriminatory ones — so if past lending, hiring, or policing decisions were biased, the model reproduces that bias at scale while appearing objective and data-driven. Worse, it can create self-reinforcing feedback loops: denying someone a loan worsens their circumstances, which the model then reads as "confirmation" it was right, entrenching the discrimination. The veneer of mathematical neutrality makes the bias harder to challenge than an obviously prejudiced human decision, which is why Kleppmann insists "the algorithm decided" doesn't remove human responsibility.

**Why does the book treat the user's screen as part of the dataflow?**
Because a user interface displays a view of server-side state, which makes it just another *derived* view in the same sense as a cache or a materialized view — and the spectrum from precomputed materialized views to on-demand queries extends all the way to the client. Technologies that *push* changes to the device (subscribing to a stream of updates rather than polling) extend the write-side dataflow end to end, so the screen becomes a live, continuously-updated materialization of the event log. This reframes "real-time UI" as the natural endpoint of the same derivation pipeline that keeps indexes and caches fresh.

**What role does reprocessing play in evolving a system's schema or derived views?**
Reprocessing — replaying the full event-log history through new derivation logic — lets you build a new derived dataset (a differently-structured index, a new materialized view, a migrated schema) from scratch while the existing view keeps serving traffic. Once the new view is fully built and validated, you switch reads over to it and retire the old one, achieving a schema migration with no downtime and no risky in-place mutation. This is only possible if the log is retained and replayable, which is why the book emphasizes durable, replayable logs as the foundation of an evolvable architecture.

**What is data minimization, and why does the book recommend it?**
Data minimization is the practice of collecting and retaining only the data actually needed for a clear purpose, rather than hoarding everything "in case it's useful later." The book recommends it because data has inertia — once collected it persists, gets combined with other datasets, and is repurposed far beyond the original intent, while every stored record is a liability that can be breached, misused, or turned into surveillance. Minimizing collection reduces the potential for harm, limits the blast radius of breaches, and respects that the data is about real people who didn't consent to open-ended use.

**How is an unbundled architecture analogous to the internals of a single database?**
In a single database, a write goes to the transaction/replication log, and internal indexes and materialized views are kept in sync with that log automatically. The unbundled architecture does exactly this but at organizational scale and with separate products: the **event log** plays the role of the transaction log, and each external derived store (Elasticsearch as a "secondary index," Redis as a "cache," a warehouse as an "analytical view") is kept in sync by consuming the log via CDC/streams. So the whole heterogeneous system behaves like one big database whose components you assembled from the best tool for each job.

**Why is the ability to relax timeliness while preserving integrity such a powerful result?**
Because it means you can build correct systems on top of asynchronous, loosely-coupled dataflow — which is far more scalable, available, and partition-tolerant than synchronous distributed transactions — without giving up the guarantee that actually matters most. Strong, synchronous coordination (linearizability, distributed transactions) is expensive and fragile, but most applications can tolerate derived views being a little behind (relaxed timeliness) as long as no data is ever lost, duplicated, or corrupted (preserved integrity). Exactly-once processing with idempotence on a replayable log delivers exactly that combination, so you get scalability and correctness together.

---

## Cross-links in this repo

- [book/.../11_stream_processing/ — the event log, CDC, and event sourcing this chapter builds on](../11_stream_processing/README.md)
- [database/distributed_transactions/ — outbox, idempotency, sagas (end-to-end correctness in practice)](../../../database/distributed_transactions/README.md)
- [hld/ — composing systems and CQRS/event-sourcing in the interview framework](../../../hld/README.md)
- [llm/ — modern derived-data and retrieval systems built on these foundations](../../../llm/README.md)

## Further Reading

- Kleppmann, DDIA Ch 12 — original text and references.
- Saltzer, Reed & Clark, "End-to-End Arguments in System Design," 1984 — the correctness principle.
- Kreps, "Questioning the Lambda Architecture," 2014 — unifying batch and stream.
- Cathy O'Neil, *Weapons of Math Destruction*, 2016 — algorithmic bias and accountability.
