# Chapter 11: Stream Processing

> Part III — Derived Data · DDIA (Kleppmann) · builds on Ch 10, leads to Ch 12

## Chapter Map

Batch processing (Ch 10) assumes the input is *bounded* — but most data is **unbounded**: events
arrive continuously and forever. Stream processing applies the batch mindset to never-ending input,
processing each event shortly after it happens so derived data (indexes, caches, aggregates,
notifications) stays continuously up to date instead of being rebuilt nightly. The chapter covers
how events are *transported* (message brokers, and especially **log-based** brokers like Kafka), the
profound idea that **databases and streams are the same thing** (change data capture, event
sourcing), and how to *process* streams correctly (time, windows, joins, and the hard problem of
fault tolerance / exactly-once).

**TL;DR:**
- A **stream** is an unbounded sequence of immutable **events**; you process each as it arrives.
- **Log-based message brokers** (Kafka) combine durable, replayable storage with messaging — unlike
  traditional brokers (JMS/AMQP) that delete a message once acknowledged.
- **Change data capture (CDC)** and **event sourcing** treat the database's *write log* as the
  primary stream, from which all other state is a derived, rebuildable view.
- Stream processing must handle **event time vs processing time**, **windowing**, **stream joins**,
  and achieve **exactly-once** semantics via idempotence + checkpointing despite failures.

## The Big Question

> "Batch jobs run on yesterday's data, so my search index, dashboards, and caches are always hours
> stale. How do I keep all that derived data fresh *continuously* as events stream in — and still be
> correct when a processor crashes mid-stream?"

Analogy: batch is reading the morning newspaper (a complete, fixed snapshot of yesterday); streaming
is a live news ticker (an endless feed you react to as it scrolls). The deep realization of this
chapter is that a database table is just *the newspaper* and the replication log is *the ticker* —
and the ticker is the more fundamental of the two.

---

## 11.1 Transmitting Event Streams

A **record** in batch becomes an **event** in streaming: a small, self-contained, **immutable**
object recording something that happened, usually with a timestamp (a user click, a sensor reading, a
purchase). An event is generated once by a **producer** (publisher) and processed by potentially
many **consumers** (subscribers); related events form a **topic** or **stream**. Polling a datastore
for new events is inefficient, so streaming uses **push**-based delivery via messaging systems.

### Messaging systems

A **publish/subscribe** messaging system delivers events from producers to consumers. Two design
questions define its behavior: *what if producers outpace consumers?* (drop messages, buffer in a
queue, or apply backpressure/flow control), and *what if a node crashes?* (durability via disk/
replication, or accept message loss for speed). **Traditional message brokers** (RabbitMQ/AMQP, JMS)
are the classic answer: a broker holds a queue; consumers acknowledge messages; **once acknowledged,
the broker deletes the message**. Two delivery patterns: **load balancing** (each message to one of
several consumers, to parallelize work) and **fan-out** (each message to all consumers). Their
weakness for stream processing: messages are transient (gone after delivery), so a new consumer can't
replay history, and complex per-message acknowledgment with redelivery can reorder messages.

### Partitioned logs (the log-based broker)

**Apache Kafka** (and Amazon Kinesis, others) takes a fundamentally different approach: a **log** — an
append-only sequence of records on disk, **partitioned** across machines and replicated. A producer
appends; each consumer reads the log sequentially and tracks its own **offset** (its position). The
defining differences from a traditional broker:

- **Messages are retained, not deleted on consumption.** The log keeps events (until a retention
  limit), so a consumer can **replay** from any past offset — new consumers can reprocess all
  history, and you can re-run a processing job after fixing a bug (the batch-style re-runnability,
  applied to streams).
- **Ordering within a partition is guaranteed**, and consumers in a group are assigned partitions
  (parallelism by partition). The offset is a simple, cheap acknowledgment (no per-message bookkeeping).
- **High throughput** from sequential disk I/O.

Tradeoff vs traditional brokers: a log-based broker parallelizes only up to the number of partitions
and works best when each message is fast to process and order matters; traditional brokers suit
slow/variable per-message work where you want fine-grained per-message redelivery. If one slow message
shouldn't block the partition, traditional broker semantics may fit better.

## 11.2 Databases and Streams

The chapter's conceptual core: **a replication log is a stream of events**, and conversely a stream
can be **materialized** into a database. The write to a database *is* an event; recognizing this
unifies the two worlds.

### Keeping systems in sync — change data capture (CDC)

Real applications keep the same data in several systems — the database of record, a search index, a
cache, a warehouse. Dual writes from the application (write to DB *and* to the index) are a recipe
for inconsistency (race conditions, partial failures). **Change data capture (CDC)** solves it: tap
the database's own change log (the logical replication log, Ch 5) and turn each committed write into
an event stream that downstream systems (index, cache, warehouse) consume *in order*. The database of
record is the single source of truth; every other store is a **derived**, eventually-consistent
follower kept in sync by the CDC stream — no dual writes. Tools: Debezium, Kafka Connect, database
logical decoding. **Log compaction** (Kafka) keeps only the latest value per key, so a CDC topic can
serve as a complete, replayable snapshot to rebuild a derived store from scratch.

### Event sourcing

A related idea from a different community: instead of storing *current state* and overwriting it,
**event sourcing** stores the full, **immutable** sequence of state-*changing* events as the source of
truth (e.g. "cart: item added", "item removed", "checked out" — not just "cart has 1 item"). Current
state is a **derived view** computed by replaying the events. Benefits: a complete **audit log** and
history; you can reconstruct *any* past state; you can build *new* derived views retroactively (decide
later you want a new index/report and replay history to build it); and it captures user *intent*
("added to cart" carries more meaning than the resulting count). Events are immutable — you never
update or delete them, you append new events.

### State, streams, and immutability

The unifying principle: **state is a materialized view of an immutable event log; the log is primary.**
"Current state" is whatever you get by folding (replaying) the change events up to now — like a
ledger in accounting, where the immutable list of transactions is the truth and the balance is just a
derived summary. Because the log is immutable and retained, you can derive *several different* read
views from the same event log (a SQL table for one query pattern, a search index for another, a cache
for a third), each optimized for its purpose and each rebuildable. This **separates writes (append to
the log) from reads (any number of derived views)** — the explicit bridge to Chapter 12's "unbundling
the database." It also makes deletion genuinely hard (the whole point is the log never forgets), which
matters for privacy/GDPR.

## 11.3 Processing Streams

What you actually *do* with a stream: maintain derived state (search indexes, materialized views,
caches), trigger actions (alerts, emails), or do analytics.

### Uses of stream processing

- **Complex event processing (CEP):** search for specific *patterns* of events in the stream (e.g.
  three failed logins then a success) and emit a match — like a database query, but the query is
  long-lived and the data flows past it (inverted from a normal database).
- **Stream analytics:** aggregate over windows (rate, average, percentiles over the last 5 minutes),
  often with approximation algorithms (HyperLogLog, Bloom filters) for efficiency.
- **Materialized views / search indexes:** keep a derived dataset continuously current (CDC →
  search index is the canonical example).

### Reasoning about time

The subtlest part. **Event time** (when the event actually occurred, set by the producer) differs from
**processing time** (when the stream processor handles it), and they diverge due to network delays,
queueing, broker backlogs, and consumer restarts (which suddenly process a burst of buffered events).
Using *processing time* for windows gives wrong results when there's any lag.

The hard problem is **stragglers / "when is a window complete?"**: a window for 10:00–10:05 might still
receive a late event at 10:07 (a phone that was offline). You never *know* you've seen all events for a
window. Options: ignore stragglers (track how many you drop), or emit a **correction** (a retraction +
updated value) when a late event arrives. Systems use **watermarks** ("all events with event-time
before T have arrived") to decide when to (tentatively) close a window. Which clock's timestamp to
trust is itself fraught (the device clock may be wrong, à la Ch 8) — a common technique logs *three*
timestamps (event time per device, time sent per device, time received per server) to estimate and
correct the device's clock offset.

Window types: **tumbling** (fixed, non-overlapping, e.g. every 1-minute block), **hopping** (fixed
length, overlapping), **sliding** (events within a moving interval of each other), **session** (group
a user's events until a gap of inactivity).

### Stream joins

Three kinds, increasingly tricky because *both* sides are moving:

- **Stream-stream join (window join):** join two event streams over a time window (e.g. correlate a
  search-results event with a later click event on the same query within N minutes). The processor must
  keep recent events from both streams in state, indexed by the join key, and expire them when the
  window passes.
- **Stream-table join (enrichment):** join a stream against a (slowly changing) table — e.g. enrich
  each activity event with the user's profile. The processor keeps a **local copy of the table** and
  updates it via a CDC stream of the table's changes, so the join doesn't hit the database per event.
- **Table-table join (materialized view maintenance):** both inputs are change streams of tables;
  the output is a change stream of a join/materialized view kept continuously up to date.

A subtlety: joins are **time-dependent** — if the table being joined against changes, replaying the
same stream later can produce a different result (which profile version was current *at that event's
time*?). Deterministic reprocessing requires being explicit about which version of the table to join.

### Fault tolerance — exactly-once

Batch got fault tolerance for free (re-run a failed task; the bounded input is still there). Streams
are unbounded, so you can't just "wait for the job to finish and retry." The goal is
**exactly-once semantics** (more precisely **effectively-once**): even if a processor crashes and
re-processes some events, the *effect* on the output is as if each event were processed exactly once.
Techniques:

- **Microbatching (Spark Streaming):** break the stream into small (e.g. 1-second) batches and apply
  batch-style retry to each — simple, at the cost of latency tied to batch size.
- **Checkpointing (Flink):** periodically snapshot the operator state and the input offsets durably;
  on failure, restart from the last checkpoint and replay the log from the saved offset. The replayable
  log (Kafka) is what makes this possible.
- **Idempotence:** make the effect of reprocessing an event harmless — e.g. write with a key derived
  from the offset so a duplicate overwrites rather than double-applies, or dedupe by event ID. This is
  what handles the side effects (writing to an external DB) that checkpointing alone can't undo.
- **Atomic commit / transactions:** frameworks (Kafka transactions, Flink) commit output *and* the
  consumed offset together atomically, so reprocessing doesn't double-emit.

The throughline: reprocessing after a crash is only safe if the input is **replayable** (log-based
broker) and the output is **idempotent or transactional**.

---

## Visual Intuition

```
LOG-BASED BROKER (Kafka) vs TRADITIONAL BROKER (JMS/AMQP)

  TRADITIONAL: message delivered + acked ─▶ DELETED from broker
     producer ─▶ [ queue ] ─▶ consumer (ack) ─▶ (gone forever; no replay)

  LOG-BASED:  append-only, RETAINED; consumers track their own offset
     producer ─▶ [ e0 e1 e2 e3 e4 e5 ... ]  (durable, replayable)
                        ▲offsetA   ▲offsetB     a NEW consumer can start at e0
                 consumer A reads behind consumer B; both replay independently
```

```
EVENT TIME vs PROCESSING TIME (why processing-time windows lie)

  events actually occurred:   e@10:01  e@10:02  e@10:03  ... e@10:04 (phone was offline)
  processor RECEIVES them:    10:01    10:02    10:03         10:09  ◀── 5-min late!
                                                                │
  window [10:00–10:05] by PROCESSING time misses the late event (counts it in 10:05–10:10) ✗
  window by EVENT time + WATERMARK can still place it correctly (or emit a correction)   ✓
```

```
STATE = A MATERIALIZED VIEW OF AN IMMUTABLE LOG (the ledger principle)

  immutable event log (truth):  [+5] [+3] [-2] [+10] [-4]  (append-only, never edited)
                                              │ fold/replay
                                              ▼
  derived current state (view):  balance = 12     ◀── rebuildable any time;
  can ALSO derive: a search index, a cache, a report — all from the SAME log
```

Caption: the chapter's three big pictures — retained replayable logs enable reprocessing; event time
plus watermarks fix windowing; and treating the immutable log as primary makes all state a rebuildable
derived view.

---

## Key Concepts Glossary

- **Stream** — an unbounded, continuously arriving sequence of events.
- **Event** — a small, immutable record of something that happened, usually timestamped.
- **Producer / consumer (publisher / subscriber)** — who emits / who reads events.
- **Topic / partition** — a named stream; partitions enable parallelism and ordering.
- **Publish/subscribe** — messaging pattern delivering events to subscribers.
- **Backpressure / flow control** — slowing producers when consumers can't keep up.
- **Traditional message broker (JMS/AMQP)** — deletes a message once acknowledged.
- **Load balancing vs fan-out** — one consumer per message vs all consumers per message.
- **Log-based broker (Kafka)** — append-only, partitioned, *retained*, replayable log.
- **Offset** — a consumer's position in a partition; cheap acknowledgment.
- **Replay / reprocessing** — re-reading the log from an earlier offset.
- **Log compaction** — keep only the latest value per key; replayable snapshot.
- **Change data capture (CDC)** — turning a DB's change log into an event stream.
- **Event sourcing** — store immutable state-change events as the source of truth.
- **Materialized view of a log** — current state derived by replaying events.
- **Complex event processing (CEP)** — matching event patterns with long-lived queries.
- **Event time vs processing time** — when it happened vs when it was handled.
- **Watermark** — a marker asserting all events before time T have arrived.
- **Straggler / late event** — an event arriving after its window was thought complete.
- **Window (tumbling / hopping / sliding / session)** — ways to bucket events over time.
- **Stream-stream / stream-table / table-table join** — the three streaming join types.
- **Exactly-once (effectively-once) semantics** — output as if each event processed once.
- **Microbatching / checkpointing / idempotence / atomic commit** — fault-tolerance techniques.

---

## Tradeoffs & Decision Tables

| | Traditional broker (JMS/AMQP) | Log-based broker (Kafka) |
|---|---|---|
| After consumption | Message deleted | Retained (replayable) |
| Replay / new consumer | No | Yes |
| Acknowledgment | Per message | Offset (position) |
| Ordering | Can be lost on redelivery | Guaranteed within partition |
| Best for | Slow/variable per-message work | High throughput, order matters, reprocessing |

| | Event time | Processing time |
|---|---|---|
| Meaning | When it happened (producer) | When it was processed |
| Correct under lag? | Yes (with watermarks) | No (mis-buckets late events) |
| Cost | Must handle stragglers/corrections | Simple but wrong on delays |

| Join type | Inputs | Use |
|-----------|--------|-----|
| Stream-stream | two event streams + window | correlate related events (search→click) |
| Stream-table | stream + CDC-maintained table | enrich events with reference data |
| Table-table | two change streams | maintain a joined materialized view |

| Exactly-once technique | Mechanism |
|------------------------|-----------|
| Microbatching | small batches with batch retry (Spark Streaming) |
| Checkpointing | snapshot state + offsets; restart + replay (Flink) |
| Idempotence | key writes by offset/event id so duplicates are harmless |
| Atomic commit | commit output and consumed offset together (Kafka txns) |

---

## Common Pitfalls / War Stories

- **Dual writes from the application.** Writing to the database *and* directly to the search index/
  cache from app code races and partially fails, leaving the stores permanently inconsistent. Use CDC:
  write only to the database of record and let downstream stores follow its change log in order.
- **Windowing by processing time.** Counting events into windows by when they were *processed* gives
  wrong numbers whenever there's any lag — a consumer restart processes a backlog and dumps thousands
  of old events into the "current" window. Use event time with watermarks (and decide your straggler
  policy explicitly).
- **Trusting the device's event-time clock.** A mobile client's clock can be wrong or deliberately
  skewed (Ch 8), so its event timestamps are unreliable; log multiple timestamps (device-sent,
  server-received) to estimate and correct the offset rather than blindly bucketing by device time.
- **Assuming exactly-once comes for free.** A processor *will* crash and reprocess events; without
  idempotent or transactional output, that double-applies side effects (sends the email twice, double-
  charges). Combine a replayable log with idempotent writes or atomic output+offset commits.
- **Non-replayable broker + reprocessing.** Trying to re-run a stream job for a bug fix or a new
  derived view on a traditional broker is impossible — the messages were deleted on ack. Choose a
  log-based broker if you need reprocessing/history.
- **Time-dependent joins that aren't reproducible.** Enriching a stream with "the current profile"
  means replaying the same stream later yields different results as the profile changes. If you need
  deterministic reprocessing, join against the version of the reference data that was current at each
  event's time.
- **Forgetting that immutable logs make deletion hard.** Event sourcing / retained logs never forget
  by design, which collides with GDPR "right to be forgotten" — plan for crypto-shredding or compaction
  strategies if you must truly delete.

---

## Real-World Systems Referenced

Apache Kafka, Amazon Kinesis, Apache Pulsar (log-based brokers); RabbitMQ, ActiveMQ, IBM MQ / JMS,
AMQP (traditional brokers); Debezium, Kafka Connect, database logical decoding (CDC); Apache Flink,
Spark Streaming, Kafka Streams, Apache Samza, Apache Storm, Google Cloud Dataflow / Apache Beam
(stream processors); Esper (CEP); event-sourcing frameworks; HyperLogLog/Bloom filters (approximate
stream analytics).

---

## Summary

Stream processing brings the batch mindset to **unbounded** input: events are immutable records
processed shortly after they occur, keeping derived data continuously fresh. Events travel through
messaging systems; **traditional brokers** (JMS/AMQP) delete a message once acknowledged, while
**log-based brokers** (Kafka) keep an append-only, partitioned, *retained* log where each consumer
tracks an **offset** and can **replay** history — bringing batch-style re-runnability to streams. The
chapter's central insight is that **databases and streams are dual**: a write is an event, a
replication log is a stream, **change data capture** turns a database's change log into a stream that
keeps search indexes/caches/warehouses in sync (eliminating fragile dual writes), and **event sourcing**
stores the immutable log of state changes as the source of truth, with current state a rebuildable
**materialized view** (the ledger principle). Processing streams demands care with **event time vs
processing time** (use watermarks and a straggler policy), **windowing** (tumbling/hopping/sliding/
session), and three kinds of **joins** (stream-stream, stream-table, table-table). Because streams
never end, **exactly-once (effectively-once)** semantics require a *replayable* input plus
*idempotent or transactional* output, achieved via microbatching, checkpointing, idempotence, and
atomic output+offset commits.

---

## Interview Questions

**How does a log-based message broker like Kafka differ from a traditional broker like RabbitMQ?**
A traditional broker (JMS/AMQP) holds messages in a queue and *deletes* each one once a consumer acknowledges it, using per-message acknowledgment and redelivery, so messages are transient and can't be replayed. A log-based broker keeps an append-only, partitioned, durable log where messages are *retained* (until a retention limit) and each consumer simply tracks its own offset (position). The crucial difference is replayability: with Kafka a new consumer can read all history and you can reprocess from any past offset, which traditional brokers can't do because the message is gone after acknowledgment.

**What is an offset, and why is it a better acknowledgment mechanism for high-throughput streams?**
An offset is a monotonically increasing position number identifying a consumer's place within a partition's log. It's a better acknowledgment than per-message acks because a consumer only needs to periodically record a single number ("I've processed up to offset N") rather than track and acknowledge each message individually, which is far cheaper and enables sequential, high-throughput reads. The offset also doubles as a replay pointer: to reprocess, you simply reset it backward, something per-message acknowledgment can't support.

**What is change data capture, and what problem does it solve?**
Change data capture (CDC) taps a database's own change log (its logical replication log) and turns every committed write into an ordered event stream that downstream systems — search indexes, caches, data warehouses — consume to stay in sync. It solves the dual-write problem: if application code writes to the database *and* separately to the search index, races and partial failures leave them permanently inconsistent. With CDC there's a single source of truth (the database of record) and every other store is a derived, eventually-consistent follower of its change stream, so no dual writes are needed.

**What is event sourcing, and how does it differ from storing current state?**
Event sourcing stores the full, immutable, append-only sequence of state-*changing* events as the source of truth — "item added to cart," "item removed," "order placed" — rather than storing and overwriting the current state. Current state is then a derived view obtained by replaying the events. It differs from the conventional approach in that you never update or delete records; you only append new events. This yields a complete audit trail and history, the ability to reconstruct any past state, the freedom to build new derived views retroactively by replaying, and a record of user *intent* rather than just outcomes.

**Explain the principle "state is a materialized view of an immutable event log."**
It means the authoritative truth is an append-only log of change events, and any "current state" is just what you get by folding (replaying) those events up to now — exactly like accounting, where the immutable list of transactions is the truth and an account balance is merely a derived summary. Because the log is immutable and retained, you can derive *multiple different* read-optimized views from the same log (a relational table, a search index, a cache), each rebuildable from scratch. This cleanly separates writes (append to the log) from reads (any number of derived views), the foundation for "unbundling the database" in Chapter 12.

**What is the difference between event time and processing time, and why does it matter?**
Event time is when an event actually occurred (stamped by the producer); processing time is when the stream processor handles it. They diverge because of network delays, broker backlogs, and especially consumer restarts that suddenly process a burst of buffered events. It matters because windowed aggregations (counts, rates over the last 5 minutes) computed by *processing* time are simply wrong whenever there's lag — a backlog drains thousands of old events into the "current" window. Correct results require bucketing by event time, which then forces you to handle late-arriving events.

**What is a watermark, and what problem does it address?**
A watermark is a marker in the stream asserting "all events with an event-time earlier than T have now arrived," which lets the processor decide when it's reasonable to consider an event-time window complete and emit its result. It addresses the fundamental "when is a window done?" problem: because events can arrive late (a phone that was offline), you can never be certain you've seen them all, so a watermark provides a principled, if approximate, cutoff. Events arriving after the watermark are stragglers, handled by either dropping them (and counting drops) or emitting a correction to the already-published result.

**Describe the three types of stream joins.**
A stream-stream (window) join correlates two event streams over a time window — e.g. matching a search-results event with a click event for the same query within N minutes — by keeping recent events from both streams in state and expiring them as the window passes. A stream-table join enriches each event with reference data (e.g. attaching a user profile to an activity event) by keeping a local copy of the table updated via the table's CDC change stream, avoiding a database lookup per event. A table-table join takes two change streams and continuously maintains the change stream of their joined materialized view.

**Why are stream joins time-dependent, and what's the implication for reprocessing?**
They're time-dependent because the data being joined against can change over time: when you enrich an event with "the user's profile," the correct answer depends on which version of the profile was current *at the event's time*. The implication is that replaying the same stream later can produce different results if the reference table has since changed, breaking deterministic reprocessing. To make reprocessing reproducible, you must join against the version of the reference data that was valid at each event's timestamp (e.g. by versioning the table changes alongside the stream), rather than always using the latest.

**What does exactly-once (effectively-once) semantics mean, and why is it hard in streaming?**
It means that even though a processor may crash and reprocess some events, the *effect* on the output is as if each event were processed exactly once — no lost and no duplicated effects. It's hard because streams are unbounded, so unlike batch you can't just "wait for the job to finish and retry"; a crash mid-stream forces you to resume and reprocess events that may have already had side effects (a written row, a sent email). Achieving it requires the input to be replayable and the output to be idempotent or committed transactionally, so reprocessing doesn't double-apply.

**What techniques achieve exactly-once semantics in stream processors?**
Microbatching (Spark Streaming) splits the stream into small batches and applies batch-style retry to each, trading some latency for simplicity. Checkpointing (Flink) periodically snapshots operator state and input offsets durably, so after a crash it restarts from the last checkpoint and replays the log from the saved offset. Idempotence makes reprocessing harmless by keying writes so duplicates overwrite rather than accumulate (or by deduplicating on event ID). Atomic commit (Kafka transactions, Flink) commits the output and the consumed offset together, so a failure can't leave output emitted without the offset advanced or vice versa.

**Why does fault-tolerant stream processing require a replayable log?**
Because recovering from a crash means resuming processing from a known-good point and re-reading the events that hadn't been durably accounted for — which is only possible if those events are still available to read again. A traditional broker that deletes messages on acknowledgment can't provide this: once consumed, the message is gone, so a crashed processor can't replay it. A log-based broker retains events and lets the processor reset its offset backward to a checkpoint, replaying from there, which is what makes checkpoint-based exactly-once recovery feasible.

**What is complex event processing (CEP), and how is it "inverted" compared to a normal database?**
CEP searches a stream for specific *patterns* of events — such as three failed logins followed by a success, or a temperature reading above a threshold sustained for five minutes — and emits a match when the pattern occurs. It's inverted relative to a normal database because in a database the data sits still and you run transient queries against it, whereas in CEP the *query* (the pattern) is long-lived and persistent while the *data* flows past it continuously. The processor maintains the query's matching state as events stream through, firing whenever the pattern completes.

**What are the main window types in stream processing?**
Tumbling windows are fixed-length and non-overlapping, so each event falls into exactly one (e.g. one-minute buckets). Hopping windows are fixed-length but overlap by advancing in steps smaller than their length, so an event can belong to several. Sliding windows group events that fall within a moving interval of each other rather than fixed boundaries. Session windows group a user's events together until a gap of inactivity longer than a timeout, then start a new session — useful for analyzing bursts of related activity like a browsing session.

**What does log compaction do, and why is it useful for CDC?**
Log compaction is a Kafka retention policy that, instead of deleting old messages by age, keeps only the *latest* value for each key and discards superseded older values for that key. It's useful for CDC because it lets a change-log topic double as a complete, current snapshot of the source table: replaying the compacted topic from the beginning reconstructs the latest state of every key, so a new or rebuilt derived store (a fresh search index or cache) can be fully populated by reading the topic, without needing a separate full database dump.

**How do databases and streams turn out to be "two sides of the same coin"?**
Because a write to a database *is* an event, and a database's replication log *is* a stream of those events — so the two are dual representations of the same information. You can go from database to stream via change data capture (emit each write as an event) and from stream to database by materializing the stream into a table (apply each event to build current state). Recognizing this unifies the worlds: the immutable event log is the primary truth, and tables, indexes, and caches are all derived, rebuildable materialized views of it — the conceptual bridge to unbundling the database in Chapter 12.

**Why are dual writes from application code dangerous, and what's the correct alternative?**
Dual writes — having the application write the same change to two systems, like the database and the search index — are dangerous because the two writes aren't atomic: they can race (concurrent updates applied in different orders to each store) or partially fail (one succeeds, the other doesn't), leaving the systems permanently inconsistent with no easy way to detect or repair the drift. The correct alternative is to write only to a single source-of-truth database and propagate changes to all other stores via that database's ordered change log (CDC), so every derived store applies the same changes in the same order and converges.

---

## Cross-links in this repo

- [book/.../05_replication/ — the replication log that CDC consumes](../05_replication/README.md)
- [database/distributed_transactions/ — the outbox pattern, an alternative to dual writes](../../../database/distributed_transactions/README.md)
- [backend/ — message brokers, Kafka, event-driven microservices](../../../backend/CLAUDE.md)
- [devops/ — Kafka operations, stream-processing platforms](../../../devops/README.md)

## Further Reading

- Kleppmann, DDIA Ch 11 — original text and references.
- Kreps, "The Log: What every software engineer should know about real-time data's unifying
  abstraction," 2013 — the log-as-foundation manifesto.
- Akidau et al., "The Dataflow Model," VLDB 2015 — event time, watermarks, windowing (Apache Beam).
