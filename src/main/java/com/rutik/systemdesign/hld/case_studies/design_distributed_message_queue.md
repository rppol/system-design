# System Design: Distributed Message Queue

## Intuition

> **Design intuition**: A distributed message queue is fundamentally **a distributed, append-only log pretending to be a queue**. Producers don't "send" messages to consumers — they append records to the end of a file. Consumers don't "receive" messages — they read sequentially from that file at whatever position they last stopped, and remember that position themselves. Everything else in this design — partitions, replication, consumer groups, exactly-once semantics — exists to make that one simple idea (a sequential file, read by independent readers tracking their own bookmarks) durable, horizontally scalable, and safe under partial failure. The reason this is more useful than a "real" queue (where the broker tracks per-message state and deletes on ACK) is that **a log can be replayed**: a new consumer, a buggy consumer that needs to reprocess, or an analytics job built six months later can all start reading from offset 0 without the producer ever knowing they exist.

**Key insight**: The single hardest tradeoff in this design is **ordering vs. parallelism**, and the partition is the unit that resolves it. A topic-wide total order would require a single writer and a single reader — no horizontal scaling at all. Splitting a topic into N independent, totally-ordered logs (partitions) lets you scale ingestion and consumption N-ways, at the cost of giving up cross-partition ordering. Every downstream guarantee in this system — "messages for the same key are processed in order," "exactly-once," "consumer group parallelism" — is really a statement about what happens *within* a partition, because that is the only scope in which Kafka-style systems make ordering promises at all. Producers therefore spend their entire design effort (§4.2) deciding *which partition* a message goes to, because that single routing decision is what determines every ordering and parallelism property the rest of the system can offer.

---

## 1. Requirements Clarification

### Functional Requirements

- **Publish**: producers publish messages (key + value + headers) to a named **topic**. The broker appends each message to the topic's underlying partitioned log and returns an offset (and partition) once durably written.
- **Subscribe / consume**: consumers read messages from one or more topics. Consumers are organized into **consumer groups**; each partition of a topic is consumed by exactly one consumer within a given group, enabling parallel processing while each group independently sees the full stream.
- **Ordering**: messages with the same partition key are delivered to consumers in the order they were written — but ordering is guaranteed **only within a partition**, never across an entire topic.
- **Offset tracking**: each consumer group tracks its own read position (offset) per partition, persisted by the broker (in an internal compacted topic, §4.1) so that a restarted consumer resumes where it left off rather than re-reading the entire log or losing its place.
- **Configurable retention**: messages are retained for a configurable **time** (e.g., 7 days) or **size** (e.g., 100GB per partition), independent of whether any consumer has read them — retention is a property of the topic, not of consumption state.
- **Configurable delivery semantics**: the system must support **at-most-once** (fire-and-forget, fastest), **at-least-once** (default — retries can duplicate), and **exactly-once** (idempotent producers + transactional writes, §4.5) on a per-producer/per-consumer basis.
- **Horizontal scalability**: both ingestion and consumption throughput scale by adding **partitions** (more independent logs to write/read in parallel) and **brokers** (more machines to host those partitions).
- **Durability via replication**: each partition is replicated across multiple brokers (typically 3x); a producer can require acknowledgment from a configurable number of replicas before considering a write durable (`acks`, §4.3).

### Non-Functional Requirements

- **High sustained throughput**: cluster-wide ingestion on the order of **1 million messages/sec**, with individual partitions sustaining roughly **10MB/sec** of write throughput (§10).
- **Low end-to-end latency**: p99 producer-to-consumer latency in the **tens of milliseconds** for `acks=1`, low hundreds of milliseconds for `acks=all` with `min.insync.replicas=2` (§4.3, §5).
- **Durability**: zero data loss for committed writes under a single-broker failure, when configured with `acks=all` and `min.insync.replicas >= 2` (§5, War Story 2).
- **High availability**: the cluster continues accepting writes and serving reads during a single-broker outage via automatic leader re-election (§4.3, §8 runbook).
- **Operational observability**: consumer lag, under-replicated partition count, and ISR health must be first-class metrics (§8) — these are the leading indicators of every major incident class in this design.

### Out of Scope

- **Schema registry and serialization format governance** — Avro/Protobuf schema evolution is a real operational concern but is a layered concern on top of the wire protocol described here, not part of the core broker/partition/replication design.
- **Stream processing (joins, windowed aggregations, stateful transforms)** — frameworks like Kafka Streams or Flink consume from this queue and are themselves a separate system; this design covers the queue they read from and write to, not the processing layer.
- **Cross-datacenter / multi-region replication (MirrorMaker-style)** — geo-replication between independent clusters is an extension of the single-cluster replication model in §4.3, but its own topology, conflict, and lag concerns are a distinct design problem.

Each of these is a real system in its own right that *consumes* or *extends* the queue designed here — they're called out explicitly so an interview discussion doesn't drift into designing a schema registry or a stream-processing engine instead of the queue itself.

### API Shape — What "Publish" and "Subscribe" Actually Look Like

Concretely, the functional requirements above translate to two client-facing surfaces:

```
Producer surface:
  send(ProducerRecord(topic, key, value, headers)) -> Future<RecordMetadata(partition, offset, timestamp)>
  - "key" determines partition routing (§4.2)
  - Future completes according to `acks` (§4.3) - immediately (acks=0),
    after leader write (acks=1), or after ISR write (acks=all)

Consumer surface:
  subscribe(List<topic>)                     -> joins a consumer group, triggers rebalance (§4.4)
  poll(timeout)                              -> ConsumerRecords (batch, possibly empty if no new data)
  commitSync() / commitAsync()               -> persist current offsets to __consumer_offsets (§3)
  seek(partition, offset)                    -> jump to an arbitrary offset (replay, §11)
```

Every later design decision in this document is, at its core, a statement about what these four consumer operations and one producer operation actually guarantee under failure — `send()`'s durability is §4.3/§4.5, `poll()`'s ordering is §4.1/§4.2, `commitSync()`'s relationship to "exactly-once" is §4.5, and `seek()`'s relationship to retention is §4.1/§11.

---

## 2. Scale Estimation

### Ingestion Throughput

- Target cluster-wide ingestion: **1,000,000 messages/sec**
- Average message size: **1KB** (a typical event: IDs, timestamps, a small JSON or Avro payload)
- Raw write throughput: `1,000,000 msgs/sec x 1KB` = **~1GB/sec** of producer-to-broker traffic, before replication

### Replication Amplification

- Replication factor (RF) = **3** is the standard production default (tolerates 1 broker failure with `min.insync.replicas=2`, or 2 failures with `min.insync.replicas=1`)
- Each byte written by a producer is written once by the leader and copied to 2 followers -> **total disk-write throughput across the cluster ~= 3GB/sec** (1GB/sec from producers, plus 2GB/sec of inter-broker replication traffic)
- Network: producer-to-leader traffic (~1GB/sec = ~8Gbps) plus leader-to-follower replication traffic (~2GB/sec = ~16Gbps) -> **~24Gbps of network traffic cluster-wide just for the write path**, before counting consumer fetch traffic

### Retention and Storage

- 7-day retention is a common default for operational topics (long enough to recover from a multi-day consumer outage by replaying, short enough to bound disk usage)
- Logical data volume at 1GB/sec sustained: `1GB/sec x 86,400 sec/day x 7 days` ~= **~605TB logical** (uncompressed, single copy)
- At RF=3: `605TB x 3` ~= **~1.8PB of physical disk** across the cluster
- With typical LZ4/Snappy compression (2-4x for JSON/Avro payloads), effective disk usage might be **450TB-900TB physical** — but capacity planning should be done on uncompressed numbers as a safety margin, since compression ratios vary by payload type

### Partition and Broker Counts

- Rule of thumb: a single partition sustains roughly **10MB/sec of write throughput** (§10) before its single-threaded log-append and replication-fetch become the bottleneck
- To sustain 1GB/sec cluster-wide: `1GB/sec / 10MB/sec` = **~100 partitions minimum** just for raw throughput headroom — in practice, spread across many topics, so a busy cluster commonly runs **several thousand partitions** total (e.g., 50 topics x 50-100 partitions each)
- Per-broker partition limits: each partition consumes file handles (multiple per active segment, §4.1) and a slice of controller/metadata overhead; a broker comfortably hosts **2,000-4,000 partition replicas** before metadata and rebalance overhead becomes a problem (§9, War Story 1 is the rebalance-cost end of this tradeoff)
- For a cluster hosting 5,000 partition-replicas at RF=3 (so ~1,667 "logical" partitions), spread across **tens of brokers** (e.g., 30-50 brokers at ~100-170 replicas/broker) keeps each broker well within the per-broker partition ceiling while leaving room to grow

### Consumer Side

- A consumer group processing the full 1M msgs/sec firehose needs enough consumer instances such that `partition_count >= consumer_count` (a partition can only be read by one consumer per group) — if a topic has 100 partitions, the group can scale to at most 100 consumer instances
- Per-consumer throughput depends entirely on processing cost; a lightweight consumer (write to a cache, emit a metric) might handle 10,000-50,000 msgs/sec, while a heavyweight consumer (ML inference per message) might handle only 50-500 msgs/sec — this asymmetry is why partition count must be sized for the **slowest** consumer group expected to read the topic (§10)

### Read Amplification from Multiple Consumer Groups

A single message written once is commonly read multiple times — once per independent consumer group subscribed to the topic. This is the queue's equivalent of "read-heavy fan-out" and must be counted separately from the write-path estimates above:

- A topic with **1GB/sec** of write traffic, read by **3 independent consumer groups** (e.g., `order-processor`, `fraud-detection`, and `analytics-ingest` from §3's architecture diagram), produces **~3GB/sec of fetch traffic** cluster-wide — three times the write-path bandwidth, even though each byte was written only once.
- Unlike replication traffic (§2's ~2GB/sec, which is inter-broker), fetch traffic flows from brokers to *external* consumer hosts — this is the traffic that competes with replication and produce traffic on each broker's NIC, and is the dominant reason §10 sizes broker NICs with headroom well beyond the raw write-path requirement.
- A consumer group reading from the **end of the log** (the common case — real-time processing) generates fetch traffic roughly proportional to write traffic. A consumer group **catching up from an old offset** (e.g., a newly-deployed analytics job backfilling 7 days of history) can temporarily generate fetch traffic far exceeding live write traffic, bounded only by disk read throughput and network bandwidth — this "backfill spike" is a capacity-planning scenario worth provisioning for separately (§10), since it's self-inflicted and schedulable, unlike a genuine traffic surge.

---

## 3. High-Level Architecture

```
                              PRODUCERS
        +-------------+   +-------------+   +-------------+
        | Producer A   |   | Producer B   |   | Producer C   |
        | (key=user_1) |   | (key=user_2) |   | (key=null,   |
        |              |   |              |   |  round-robin)|
        +------+-------+   +------+-------+   +------+-------+
               |                  |                   |
               |  Partitioner (key hash / round-robin / sticky) - §4.2
               v                  v                   v
   +---------------------------------------------------------------+
   |                     KAFKA CLUSTER (Topic: orders, 3 partitions, RF=3)|
   |                                                                 |
   |   Broker 1              Broker 2              Broker 3          |
   |   +-------------+       +-------------+       +-------------+   |
   |   | P0 [LEADER] |<----->| P0 [FOLLOWER]|<----->| P0 [FOLLOWER]|   |
   |   | P1 [FOLLOWER]|<---->| P1 [LEADER] |<------>| P1 [FOLLOWER]|   |
   |   | P2 [FOLLOWER]|<---->| P2 [FOLLOWER]|<------>| P2 [LEADER] |   |
   |   +-------------+       +-------------+       +-------------+   |
   |        ISR(P0)={B1,B2,B3}  ISR(P1)={B1,B2,B3}  ISR(P2)={B1,B2,B3}|
   +---------------------------------------------------------------+
               |                  |                   |
               |  Pull-based fetch (consumer-driven, §5)
               v                  v                   v
   +---------------------------------------------------------------+
   |              CONSUMER GROUP: order-processor (3 consumers)      |
   |                                                                 |
   |   Consumer 1 <--- reads P0    Consumer 2 <--- reads P1          |
   |   Consumer 3 <--- reads P2                                      |
   |                                                                 |
   |   Offsets committed to internal topic: __consumer_offsets       |
   |   {group=order-processor, P0: 104233, P1: 98871, P2: 110456}    |
   +---------------------------------------------------------------+

                    (a second, independent consumer group
                     "analytics" can read the same topic
                     from its own offsets, at its own pace)
```

### Request Flow

1. **Produce**: a producer constructs a `ProducerRecord(topic, key, value)`. The **Partitioner** (§4.2) maps the key to a partition number — `hash(key) % numPartitions` for keyed records, round-robin or sticky-batch assignment for `key=null`. The record is sent to the **leader broker** for that partition.
2. **Replicate**: the leader appends the record to its local log (§4.1) and the **follower replicas** (§4.3) fetch the new data, appending it to their own copies. Depending on `acks` (§4.3), the producer's `send()` future completes after the leader write (`acks=1`), after all in-sync replicas (ISR) have replicated (`acks=all`), or immediately without waiting (`acks=0`).
3. **Consume**: each consumer in a consumer group (§4.4) is assigned a disjoint subset of the topic's partitions by the **Group Coordinator** (a designated broker). The consumer issues `poll()` calls, which are long-pull fetch requests against the partition leaders, returning batches of records starting from the consumer's current offset.
4. **Commit offsets**: after processing a batch (or before, depending on delivery-semantics configuration, §1), the consumer commits its new offset per partition to the internal `__consumer_offsets` topic — itself a Kafka topic, replicated and durable like any other.
5. **Retention enforcement**: independently of consumption, each partition's log is periodically checked against its retention policy (time- or size-based, §4.1); old log segments are deleted regardless of whether any consumer group has read them — a slow consumer that falls behind retention simply loses access to the oldest messages (§9, edge case discussed in §11).

### Single-Partition View: Log, Replicas, and Independent Consumer Offsets

The cluster-level diagram above shows *where* partitions live; this diagram shows what a single partition's log looks like from the inside, and how multiple independent readers (different consumer groups) relate to the same underlying data:

```
Partition 0 log (leader, Broker 1):

  offset: 0    1    2    3    4    5    6    7    8    9   10   11   12   ...  latest=110456
          [m0] [m1] [m2] [m3] [m4] [m5] [m6] [m7] [m8] [m9] [m10][m11][m12] ... [m110456]
          ^                                        ^                    ^
          |                                        |                    |
   earliest offset                      order-processor group      analytics group
   (oldest retained                     committed offset: 9         committed offset: 12
    segment, §4.1)                      (lag = 110456 - 9)          (lag = 110456 - 12)

  Follower replicas (Brokers 2 & 3) hold an identical copy of offsets 0..110456,
  continuously fetching new records appended to the leader (§4.3).

  retention.ms=604800000 (7 days): segments whose newest record is older
  than 7 days are deleted - this can advance "earliest offset" past a
  consumer group's committed offset if that group falls far enough behind (§11).
```

Three things this diagram makes concrete: (1) **the log itself has no notion of "consumed" or "unconsumed"** — every record from `earliest offset` to `latest offset` is physically present regardless of who has read it; (2) **each consumer group's position is just a number stored elsewhere** (`__consumer_offsets`, §3) — the `order-processor` group being "behind" the `analytics` group has zero effect on either group's ability to keep reading; and (3) **retention moves the left edge of the log independently of any consumer's position** — if `earliest offset` advances past a group's committed offset before that group reads those records, §11's "fell behind retention" edge case occurs.

---

## 4. Component Deep Dives

### 4.1 Topic / Partition / Offset Model and Log-Structured Storage

A **topic** is a logical name (e.g., `orders`). Physically, a topic is divided into **partitions** — each partition is an independent, **append-only, ordered log** of records, identified by an integer index (0, 1, 2, ...). Every record within a partition has a monotonically increasing **offset** (0, 1, 2, ... — a simple integer, not a timestamp), which is the record's permanent address within that partition.

**Why append-only matters**: writes are always sequential I/O — appending to the tail of a file. Sequential writes to spinning disks (and even SSDs) are an order of magnitude faster than random writes, because there's no seek cost and the OS page cache can batch and flush efficiently. This is the single biggest reason a log-structured broker can sustain ~10MB/sec/partition (§2, §10) on commodity disks — a traditional queue that deletes individual messages on ACK incurs random I/O (or requires an index structure to track "holes"), which a pure append-only log never needs.

**Segment files**: a partition's log is not one giant file — it's split into **segments**, each a bounded-size file (e.g., 1GB) named by the offset of its first record (`00000000000000368769.log`). Two operations depend on segmentation:

- **Segment rolling**: when the active segment reaches its size limit (or a configured time limit, e.g., one segment per day), the broker closes it (making it immutable and eligible for retention deletion) and opens a new active segment for subsequent writes.
- **Retention-based deletion**: a background thread periodically scans each partition's *closed* segments and deletes any whose **newest record** is older than the retention period (time-based, e.g., `retention.ms=604800000` for 7 days) or whose deletion would bring total partition size under the size-based limit (`retention.bytes`). Deletion is a cheap `unlink()` of an entire segment file — never a per-record operation. This is why retention is segment-granular, not record-granular: a single record older than the retention window doesn't get deleted until its *entire segment* ages out.

```
Partition 0 on disk:
/data/kafka-logs/orders-0/
  00000000000000000000.log   (segment 1: offsets 0-99999)      <- closed, eligible for deletion
  00000000000000000000.index (offset -> byte-position sparse index)
  00000000000000100000.log   (segment 2: offsets 100000-199999) <- closed
  00000000000000100000.index
  00000000000000200000.log   (segment 3: offsets 200000-...)    <- ACTIVE (being appended to)
  00000000000000200000.index
```

**Offset lookups**: each segment has a paired **sparse index file** mapping a sampled set of offsets to byte positions within the `.log` file. A consumer fetch for offset 150,042 binary-searches the index for the nearest indexed offset <= 150,042, seeks to that byte position, then scans forward linearly (a few KB at most) to the exact record — O(log n) to find the segment plus a small constant scan, never a full-log scan.

**Per-key compacted retention (an alternative cleanup policy)**: instead of time/size-based deletion, a topic can use `cleanup.policy=compact`, which retains only the **latest record per key**, garbage-collecting older values for the same key in the background. This is used for "changelog"-style topics (e.g., the internal `__consumer_offsets` topic itself, or a topic representing "current state of entity X") where only the most recent value matters — §5 compares this against time/size retention.

### 4.2 Producer Partitioning

The partitioner decides which partition a record lands in, which in turn determines its ordering guarantees (records to the same partition are ordered; records to different partitions are not) and which broker/consumer handles it.

```java
package com.rutik.systemdesign.hld.case_studies.mq;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simplified producer-side partitioner mirroring Kafka's default
 * partitioning strategy:
 *  - Keyed records: hash(key) % numPartitions (stable, deterministic)
 *  - Null-key records: "sticky" partitioning - stick to one partition
 *    for the duration of a batch (linger.ms window), then rotate.
 *    This produces larger batches than pure round-robin without
 *    sacrificing even load distribution over time.
 */
public class Partitioner {

    private final Map<String, Long> stickyPartitionCounters = new ConcurrentHashMap<>();
    private final AtomicLong roundRobinCounter = new AtomicLong(0);

    /**
     * Returns the partition index for the given record.
     *
     * @param topic          topic name (used to scope sticky-partition state)
     * @param key            record key, or null for unkeyed records
     * @param numPartitions  number of partitions for this topic
     * @param currentBatchPartition the partition the current in-flight
     *                               batch is sticking to, or -1 if no
     *                               batch is currently open
     * @param batchIsFull    whether the current batch for
     *                       currentBatchPartition is full / has lingered
     *                       long enough to be sent
     */
    public int partitionFor(String topic, byte[] key, int numPartitions,
                             int currentBatchPartition, boolean batchIsFull) {
        if (key != null) {
            // Keyed record: deterministic hash-based partitioning.
            // murmur2 is Kafka's actual default hash; we use a simple
            // positive-modulo hash here for illustration.
            int hash = murmur2(key);
            return Math.abs(hash) % numPartitions;
        }

        // Null key: sticky partitioning.
        // Stay on the current batch's partition until it's full/flushed,
        // then pick a new "sticky" partition (round-robin among all
        // partitions) for the next batch.
        if (currentBatchPartition >= 0 && !batchIsFull) {
            return currentBatchPartition;
        }

        long next = roundRobinCounter.getAndIncrement();
        return (int) (next % numPartitions);
    }

    /**
     * Simplified murmur2 hash - real Kafka uses the full murmur2
     * algorithm for even distribution and cross-language compatibility
     * with consumers in other languages.
     */
    private int murmur2(byte[] data) {
        int h = 0x9747b28c;
        for (byte b : data) {
            h ^= b;
            h *= 0x5bd1e995;
            h ^= (h >>> 15);
        }
        return h;
    }

    public record PartitionAssignment(String topic, int partition, byte[] key) {}
}
```

**Why sticky partitioning beats pure round-robin for null keys**: pure round-robin sends record 1 to partition 0, record 2 to partition 1, record 3 to partition 2, etc. — under low produce rates, this means every partition receives a trickle of tiny, mostly-unbatched requests, multiplying the number of in-flight produce requests by the partition count. Sticky partitioning sends an entire `linger.ms` batch worth of records to the *same* partition before rotating, producing fewer, larger, more compressible batches — directly improving the throughput-per-partition numbers from §2 (the ~10MB/sec/partition target assumes reasonably large batches; many tiny requests can't reach it even on an otherwise-idle partition).

**Why a keyed record always goes to the same partition (until the partition count changes)**: `hash(key) % numPartitions` is deterministic — the same key always maps to the same partition, which is *why* per-key ordering is achievable at all (§1's "ordering within a partition" guarantee is really "ordering within `hash(key) % numPartitions`"). The parenthetical matters: if `numPartitions` changes (a topic is expanded from 12 to 24 partitions), the modulo changes for every key, and a key that was guaranteed to land on partition 5 might now land on partition 17 — breaking the "same key, same partition" invariant for any records produced after the resize. This is one reason partition counts are chosen conservatively up front (§5, §10) and changed rarely.

### 4.3 Replication and the In-Sync Replica (ISR) Set

Each partition has one **leader** replica and `RF - 1` **follower** replicas. All producer writes and consumer reads for that partition go through the leader; followers exist purely for durability and failover.

**ISR (In-Sync Replicas)**: the set of replicas (including the leader) that have replicated up to (within a small lag tolerance of) the leader's latest offset. A replica that falls too far behind (configurable via `replica.lag.time.max.ms`) is removed from the ISR — it still exists and keeps trying to catch up, but is no longer counted toward durability guarantees until it rejoins.

**`min.insync.replicas`**: the minimum ISR size required for the leader to accept a write when `acks=all`. With `RF=3` and `min.insync.replicas=2`, the leader plus at least one follower must acknowledge before the write is considered committed — tolerating one slow/down follower without blocking writes, while still requiring at least 2 copies to exist before acknowledging.

**The `acks` setting — the central producer durability/latency knob**:

| `acks` value | Leader behavior | Durability | Latency | Failure mode |
|---|---|---|---|---|
| `acks=0` | Producer doesn't wait for any broker response | None — write may never reach the leader at all | Lowest (no round trip) | A leader crash before the write reaches disk loses the message silently; producer never knows |
| `acks=1` | Producer waits for the **leader only** to write to its local log | Survives consumer-side issues, but not leader crash before replication | Low (one round trip to leader) | If the leader crashes before followers replicate, and an out-of-sync follower becomes the new leader (unclean election), the message is lost (War Story 2) |
| `acks=all` (`-1`) | Producer waits for **all members of the current ISR** to acknowledge | Survives any single broker failure, given `min.insync.replicas >= 2` | Highest (round trip to leader + replication to followers) | If ISR shrinks below `min.insync.replicas`, the leader rejects writes (`NotEnoughReplicasException`) rather than risk under-replicated commits — an availability/durability tradeoff, not data loss |

**Leader election on broker failure**: when a broker hosting a partition leader fails (detected via the cluster's metadata/controller layer — ZooKeeper historically, KRaft in modern Kafka), the controller picks a new leader from the partition's current ISR — by construction, every member of the ISR has all committed records, so any ISR member can become leader with zero data loss. **Unclean leader election** (`unclean.leader.election.enable=true`) allows a replica *outside* the ISR (one that was lagging) to become leader if no ISR members are available — this restores availability faster but can silently drop the records the new leader never received (War Story 2 walks through exactly this scenario).

```
Before failure:                     After Broker 2 (leader of P1) fails:

Broker 1: P0[F] P1[F] P2[L]          Broker 1: P0[F] P1[L*] P2[L]   (*newly elected
Broker 2: P0[L] P1[L] P2[F]          Broker 2: (down)                  from ISR={B1,B3})
Broker 3: P0[F] P1[F] P2[F]          Broker 3: P0[F] P1[F] P2[F]

ISR(P1) = {B1, B2, B3}                ISR(P1) = {B1, B3}  (B2 removed, will
                                                            rejoin as follower
                                                            once it recovers
                                                            and catches up)
```

### 4.4 Consumer Groups and Partition Rebalancing

A **consumer group** is a named set of consumer instances that collectively consume a topic, with Kafka guaranteeing each partition is assigned to **at most one consumer within the group** at a time. The **Group Coordinator** (one broker, designated per group) tracks group membership and triggers a **rebalance** — reassigning partitions among current members — whenever membership changes (a consumer joins, leaves, or is declared dead via missed heartbeats).

```java
package com.rutik.systemdesign.hld.case_studies.mq;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simplified Consumer Group Coordinator illustrating partition
 * assignment on join/leave, and the difference between:
 *  - Eager rebalancing: ALL partitions are revoked from ALL members,
 *    then reassigned from scratch (every member pauses).
 *  - Incremental cooperative rebalancing: only the partitions that
 *    actually need to MOVE are revoked; members keep processing
 *    their unaffected partitions throughout.
 */
public class ConsumerGroupCoordinator {

    private final List<Integer> allPartitions;          // e.g., [0..95] for a 96-partition topic
    private final Map<String, List<Integer>> assignment = new LinkedHashMap<>(); // consumerId -> partitions

    public ConsumerGroupCoordinator(int numPartitions) {
        this.allPartitions = new ArrayList<>();
        for (int i = 0; i < numPartitions; i++) {
            allPartitions.add(i);
        }
    }

    /** EAGER: every member is fully revoked, then partitions are
     *  redistributed from scratch among the new membership set. */
    public RebalanceResult eagerRebalance(Set<String> members) {
        Set<Integer> revoked = new HashSet<>();
        for (List<Integer> partitions : assignment.values()) {
            revoked.addAll(partitions);   // ALL partitions revoked from ALL members
        }
        assignment.clear();

        List<String> sortedMembers = members.stream().sorted().collect(Collectors.toList());
        assignRoundRobin(sortedMembers);

        Set<Integer> reassigned = allPartitions.stream().collect(Collectors.toSet());
        return new RebalanceResult(revoked, reassigned, copyOfAssignment());
    }

    /** INCREMENTAL COOPERATIVE: only partitions whose owner actually
     *  changes are revoked. Members keep their unchanged partitions
     *  and continue processing them during the rebalance. */
    public RebalanceResult cooperativeRebalance(Set<String> members) {
        Map<String, List<Integer>> previousAssignment = copyOfAssignment();

        List<String> sortedMembers = members.stream().sorted().collect(Collectors.toList());
        assignRoundRobin(sortedMembers);

        Set<Integer> revoked = new HashSet<>();
        Set<Integer> newlyAssigned = new HashSet<>();
        for (String member : previousAssignment.keySet()) {
            List<Integer> before = previousAssignment.getOrDefault(member, List.of());
            List<Integer> after = assignment.getOrDefault(member, List.of());
            for (int p : before) {
                if (!after.contains(p)) revoked.add(p);    // moved away from this member
            }
        }
        for (String member : assignment.keySet()) {
            List<Integer> before = previousAssignment.getOrDefault(member, List.of());
            List<Integer> after = assignment.getOrDefault(member, List.of());
            for (int p : after) {
                if (!before.contains(p)) newlyAssigned.add(p); // moved to this member
            }
        }
        return new RebalanceResult(revoked, newlyAssigned, copyOfAssignment());
    }

    private void assignRoundRobin(List<String> members) {
        assignment.clear();
        for (String member : members) {
            assignment.put(member, new ArrayList<>());
        }
        for (int i = 0; i < allPartitions.size(); i++) {
            String member = members.get(i % members.size());
            assignment.get(member).add(allPartitions.get(i));
        }
    }

    private Map<String, List<Integer>> copyOfAssignment() {
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        for (var entry : assignment.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    /** revokedPartitions: partitions consumers must stop processing.
     *  movedOrNewPartitions: partitions consumers must start processing.
     *  finalAssignment: complete new assignment, for reference. */
    public record RebalanceResult(Set<Integer> revokedPartitions,
                                   Set<Integer> movedOrNewPartitions,
                                   Map<String, List<Integer>> finalAssignment) {}
}
```

**Eager vs. cooperative, concretely**: with 96 partitions and 16 consumers (6 partitions each), if one consumer crashes, `eagerRebalance` revokes **all 96 partitions from all 16 consumers** — every consumer stops processing entirely — then reassigns ~6.4 partitions to each of the 15 survivors. `cooperativeRebalance`, by contrast, only revokes the ~6 partitions that belonged to the dead consumer, redistributing *those* among the 15 survivors — the other ~90 partitions never stop being processed. The cost difference scales with group size: for a 16-consumer group the eager "stop everything" window might be a second or two; for a **300-consumer group** (a realistic large deployment), it's the difference between a brief blip and War Story 1's multi-minute, repeated-rebalance stall.

**Static membership** (`group.instance.id`): normally, a consumer that restarts (e.g., during a rolling deploy) gets a *new* member ID, triggering a rebalance both when it leaves and again when it rejoins — two rebalances per restart, times however many instances are being rolled. Configuring a stable `group.instance.id` per consumer instance lets the coordinator recognize "this is the same logical member, just reconnecting" within a grace period (`session.timeout.ms`), skipping the rebalance entirely for a clean restart — directly addressed in War Story 1.

### 4.5 Delivery Semantics — Idempotent Producers and Transactions

**The duplicate problem**: with `acks=all`, a producer sends a batch, the leader writes it and replicates it, but the **acknowledgment is lost** (network blip) before reaching the producer. The producer, having received no ack, retries — and now the broker has the batch **twice**. At-least-once delivery is "correct" in the sense that no message is lost, but the topic now contains a duplicate.

**Idempotent producer (`enable.idempotence=true`)**: the producer is assigned a **Producer ID (PID)** by the broker at session start, and tags every batch sent to a given partition with a monotonically increasing **sequence number**, starting from 0. The broker tracks the last sequence number it committed per `(PID, partition)`. On a retry, the broker sees a sequence number it has already committed and **silently discards the duplicate** (returning the original offset as if the write succeeded) rather than appending it again.

```
Producer (PID=1001) -> Partition 0:
  send seq=0  -> broker appends at offset 500, acks... but ack is lost on the wire
  [retry] send seq=0 -> broker sees seq=0 already committed for PID=1001 -> DISCARD, return offset=500
  send seq=1  -> broker appends at offset 501, acks successfully
```

This makes retries safe **within a single producer session, for a single partition** — exactly the failure mode `acks=all` retries create. It does *not* by itself make a multi-partition, multi-topic write atomic — that's what transactions add.

**Transactions for exactly-once across partitions/topics**: a producer can wrap multiple `send()` calls (potentially to different topics/partitions) plus, critically, a **consumer offset commit**, in a transaction:

```java
producer.initTransactions();
try {
    producer.beginTransaction();

    // 1. Process input record, produce derived output(s)
    producer.send(new ProducerRecord<>("orders.enriched", key, enrichedValue));
    producer.send(new ProducerRecord<>("orders.audit", key, auditEntry));

    // 2. Commit the INPUT topic's consumer offset as part of the SAME transaction
    producer.sendOffsetsToTransaction(currentOffsets, consumerGroupMetadata);

    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction(); // none of the writes above become visible
}
```

Downstream consumers reading with `isolation.level=read_committed` never see the writes from an aborted transaction — they're physically present in the log (as "transaction markers" bracket them) but filtered out at read time. This is what lets a **consume -> transform -> produce** pipeline stage be exactly-once: either the entire "read this input offset, write these outputs" unit happens, or none of it is visible, even if the process crashes mid-way and a retry re-executes the same input record.

**Where exactly-once still breaks (the honest caveat)**: this guarantee is scoped to **within the Kafka cluster**. The moment a consumer writes a *side effect outside Kafka* — calling a payment API, writing to an external database not using the same transactional coordination — that side effect is not covered by the Kafka transaction. A crash between "Kafka transaction committed" and "external API called" (or vice versa) reintroduces at-least-once (or at-most-once) semantics for that external system, which must then be made idempotent on its own terms (e.g., an idempotency key on the external API call) — see §11 for the interview framing of this caveat.

### 4.6 Backpressure and Consumer Lag Monitoring

**Consumer lag** is the single most important operational signal in this entire system: `lag = (latest offset written to the partition) - (last offset committed by the consumer group for that partition)`. Lag measured in messages tells you "how far behind is this consumer," but the more actionable form is **lag in time**: `lag_messages / consumption_rate` or, more directly, `current_time - timestamp_of_last_consumed_record` — "how stale is the data this consumer just processed."

**Backpressure is implicit, not explicit**: unlike a push-based broker that must actively throttle a producer when a queue fills up, a pull-based log has no "queue full" state for the broker to signal — the log just keeps growing (up to its retention/size limits, §4.1). Backpressure manifests entirely as **growing consumer lag**, which is why lag monitoring isn't just "nice to have" observability (cross-ref [`../observability/README.md`](../observability/README.md)) — it is the *only* signal that a consumer can't keep up, and the system will not protect itself otherwise. A consumer that falls behind doesn't get an error; it just silently accumulates lag until either (a) it catches up, (b) it's scaled out, or (c) it falls behind retention and starts losing access to old data (§9, §11).

**Mitigations when lag grows unbounded**:
- **Scale consumers** up to the partition count (§4.4) — the only way to add parallelism within a single consumer group
- **Increase partition count** (if already at `consumers == partitions`) — but this is a one-way operation with rebalancing cost (§5, §9)
- **Shed non-critical work** — route a sampled subset of the topic to a lighter-weight processing path, falling back to full processing once lag recovers
- **Alert on lag *trend*, not just absolute value** — a lag of 50,000 messages that's been flat for an hour (consumer keeping pace with bursty traffic) is very different from a lag of 5,000 that's growing by 1,000/sec (consumer falling behind in real time)

A simplified lag-tracking component, periodically polled by an external monitoring system, illustrates the calculation concretely:

```java
package com.rutik.systemdesign.hld.case_studies.mq;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-partition consumer lag for a single consumer group and
 * flags partitions whose lag is both large AND growing - the
 * combination that distinguishes "slow but keeping pace with bursty
 * traffic" from "falling behind in real time" (§4.6).
 */
public class ConsumerLagMonitor {

    /** Latest offset written to each partition (from broker metadata). */
    private final Map<Integer, Long> logEndOffsets = new HashMap<>();

    /** Last committed offset per partition for this consumer group. */
    private final Map<Integer, Long> committedOffsets = new HashMap<>();

    /** Previous sample's lag, used to compute the trend. */
    private final Map<Integer, Long> previousLag = new HashMap<>();

    private static final long LAG_ALERT_THRESHOLD = 100_000;     // messages
    private static final long LAG_GROWTH_ALERT_THRESHOLD = 1_000; // messages/sample

    public void recordSample(int partition, long logEndOffset, long committedOffset) {
        logEndOffsets.put(partition, logEndOffset);
        committedOffsets.put(partition, committedOffset);
    }

    public long currentLag(int partition) {
        long end = logEndOffsets.getOrDefault(partition, 0L);
        long committed = committedOffsets.getOrDefault(partition, 0L);
        return Math.max(0, end - committed);
    }

    /**
     * Returns true if this partition's lag is large AND growing -
     * the actionable "consumer is falling behind right now" signal,
     * as opposed to a large-but-stable lag from a bursty traffic
     * pattern the consumer is otherwise keeping pace with.
     */
    public boolean isFallingBehind(int partition) {
        long lag = currentLag(partition);
        long prior = previousLag.getOrDefault(partition, lag);
        long growth = lag - prior;
        previousLag.put(partition, lag);

        return lag > LAG_ALERT_THRESHOLD && growth > LAG_GROWTH_ALERT_THRESHOLD;
    }
}
```

The key design point this class encodes: **alerting on absolute lag alone produces both false positives (a healthy consumer that legitimately buffers 100K messages during a nightly batch spike) and false negatives (a consumer with only 5K lag, but growing by 2K/sample, will breach the retention window in minutes if nothing changes)**. Combining a magnitude threshold with a trend threshold catches the genuinely actionable case — this is the logic underlying the `Page if lag is *growing* for more than 10 minutes` alert in §8.

### 4.7 Tiered Storage and Log Compaction in Practice

Two extensions to the basic segment-file model (§4.1) matter for both cost and correctness at scale:

**Tiered storage**: in the base design, every byte of retained data lives on every broker's local disk for every replica — at §2's ~1.8PB physical footprint, that's an expensive amount of local SSD/NVMe across the fleet, most of which is **cold** (a 7-day-old segment is read rarely, if ever, compared to the active segment). Tiered storage (a feature in modern Kafka and the default architecture in Pulsar, §6) automatically moves **closed segments** (§4.1 — immutable, no longer being appended to) to cheaper object storage (S3-equivalent) after a configurable age, while keeping only the **active segment plus a recent window** on local disk. A fetch request for an offset within the tiered range transparently reads from object storage instead of local disk — slower per-request, but for cold data that's read rarely (replay, reprocessing, compliance audits), the latency tradeoff is favorable against a 5-10x reduction in local-disk footprint. This directly changes the §10 disk-sizing math: local disk only needs to hold the "hot" window (e.g., 1-2 days), while the full 7-day (or longer) retention lives in object storage at object-storage prices.

**Log compaction's background "cleaner" mechanics**: §5 introduces compacted topics conceptually (latest-value-per-key retention); the mechanism is a background **cleaner thread** that periodically rewrites a partition's older segments into new, smaller segments containing only each key's most recent value as of that rewrite. Between cleaner passes, a partition can temporarily contain *multiple* values for the same key (the "dirty" portion — recently written, not yet compacted — plus the "clean" portion — already deduplicated). A consumer reading the clean portion sees one value per key; a consumer reading the dirty portion may see several. This is why compacted topics document a `min.cleanable.dirty.ratio` (the fraction of a partition's data that must be "dirty" before a compaction pass runs) — too aggressive a setting wastes CPU on frequent recompaction; too lax a setting lets the dirty portion (and therefore the topic's effective size) grow large between passes.

---

## 5. Design Decisions & Tradeoffs

### Pull-Based (Kafka) vs. Push-Based (RabbitMQ / SQS) Consumption

| Dimension | Pull-based (Kafka) | Push-based (RabbitMQ / SQS) |
|---|---|---|
| Who controls flow rate | Consumer — calls `poll()` when ready | Broker — pushes messages as fast as the consumer's prefetch/visibility settings allow |
| Backpressure mechanism | Implicit — consumer simply polls less often; lag grows (§4.6) | Explicit — broker respects prefetch limits, visibility timeouts; can overwhelm a slow consumer if misconfigured |
| Replay / re-read | Trivial — seek to any offset, even one already "consumed" by this group | Generally not possible — once ACKed, the message is gone (SQS) or requeued from scratch (RabbitMQ), no arbitrary seek |
| Batching efficiency | Excellent — a single `poll()` returns a large batch from a contiguous log range | Limited by prefetch count; smaller, more frequent network round trips |
| Multiple independent readers of the same data | Natural — each consumer group has its own offset; N groups read the same log N times independently | Awkward — typically requires fanning out to N separate queues (e.g., SNS -> multiple SQS queues) |
| Latency floor | Slightly higher — `poll()` interval and fetch batching add a small delay vs. an immediate push | Lower for single-consumer, low-volume cases — broker pushes the instant a message arrives |

**Why this design chose pull**: the requirements in §1 — replay, multiple independent consumer groups reading the same topic at different paces, and very high throughput via large batched fetches — are exactly pull's strengths. Push-based brokers excel at lower-latency, simpler point-to-point task distribution (a worker pool draining a job queue) where replay and multi-group fan-out aren't requirements — see §6 for where RabbitMQ/SQS remain the better choice.

### `acks=all` vs. `acks=1` vs. `acks=0`

Already tabulated mechanically in §4.3; the design-decision framing is: **`acks` is the single knob that trades latency for durability, and the right value is a per-topic decision, not a cluster-wide one**. A topic carrying payment events or order state (where loss is a correctness incident, War Story 2) should use `acks=all` with `min.insync.replicas=2` and unclean leader election disabled, accepting the extra replication round-trip latency. A topic carrying high-volume, loss-tolerant telemetry (raw clickstream events, where losing 0.01% during a rare broker failure is invisible in aggregate) can use `acks=1` for lower latency and higher throughput, since the *aggregate* signal survives individual record loss.

### Partition Count vs. Rebalance Cost and Open-File-Handle Overhead

| Dimension | Fewer partitions | More partitions |
|---|---|---|
| Max consumer parallelism per group | Lower — bounded by partition count | Higher |
| Per-broker file handles (each partition = multiple open segment files, §4.1) | Lower | Higher — thousands of partitions per broker means tens of thousands of file handles |
| Rebalance time (§4.4) | Faster — less metadata to redistribute | Slower — more partition-assignment work per rebalance, especially with eager rebalancing |
| Producer batching efficiency | Larger batches per partition (more data funneled into fewer logs) | Smaller batches per partition unless overall throughput scales proportionally |
| End-to-end latency for a single key's ordered stream | Same (one partition is one partition) | Same — partition count doesn't affect per-partition latency |
| Resizing cost | Cheap to *add* later if under-provisioned... | ...but **cannot be decreased**, and adding changes `hash(key) % numPartitions` for every key (§4.2), breaking the "same key, same partition" history |

**Why "more partitions" isn't free**: it's tempting to over-provision partitions "for future scale," but each partition is a real cost — an open file handle (or several, across segments and indexes, §4.1) on every broker that hosts a replica, a slot in every consumer group's rebalance computation, and a slice of controller/metadata bookkeeping. A topic provisioned with 1,000 partitions "just in case" when 50 would suffice doesn't just waste resources — it makes every rebalance for every consumer group of that topic 20x more expensive in computation, and that cost is paid on every deploy, every consumer crash, every scale-out event (§9, War Story 1).

### Compacted Topics (Key-Based Retention) vs. Time/Size-Based Retention

| Dimension | Time/size-based retention (`cleanup.policy=delete`) | Compacted (`cleanup.policy=compact`) |
|---|---|---|
| What's retained | All records within the time/size window | Only the **latest** record per key, regardless of age |
| Use case | Event streams — order events, clickstream, logs (§1's default model) | "Current state" topics — `__consumer_offsets` itself, a changelog of "current value of entity X" |
| Can a consumer replay the full event history? | Yes, within the retention window | No — intermediate values for a key are garbage-collected; only "latest known state" survives |
| Storage growth | Bounded by time/size config, independent of key cardinality | Bounded by **number of distinct keys**, independent of update frequency — a key updated 1M times/day still contributes only its latest value |
| Tombstones (deleting a key) | N/A — records simply age out | A null-value record for a key signals "delete this key's entry," itself eventually garbage-collected after a grace period |

**This design's default**: the primary `orders`-style topics in §3 use time-based retention (7 days, §2) — they represent an event stream where history matters for replay (§1). The internal `__consumer_offsets` topic (§3, §4.4) is itself compacted — only the latest committed offset per `(group, topic, partition)` matters, and compaction keeps that topic's size proportional to `(number of groups) x (number of partitions)`, not to the total number of offset-commit messages ever sent (which, at a commit every few seconds per consumer, would otherwise grow unbounded).

### Eager vs. Incremental Cooperative Rebalancing

| Dimension | Eager (`range`/`round-robin` assignor) | Incremental Cooperative (`cooperative-sticky`) |
|---|---|---|
| Partitions revoked on membership change | All partitions, from all members | Only the partitions whose owner actually changes |
| Group-wide processing pause | Yes — every rebalance, regardless of size of change | No — unaffected consumers keep processing throughout |
| Implementation complexity | Simpler — single "stop, reassign, resume" phase | More complex — two-phase protocol (revoke known-moving partitions first, then assign) |
| Best fit | Small, stable groups where rebalances are rare | Large groups (dozens-to-hundreds of consumers) and/or environments with frequent membership churn (rolling deploys, autoscaling) |
| Failure mode if misused | "Stop the world" cost scales with group size — fine for 5 consumers, painful for 300 (War Story 1) | None inherent — strictly dominates eager for any group size, which is why it's the modern default |

**Why this isn't really a tradeoff in practice**: unlike `acks` or partition count (genuine cost/benefit tradeoffs where the "right" answer depends on the topic), incremental cooperative rebalancing has essentially **no downside** relative to eager — it's strictly better for any group with more than a handful of members, which is why War Story 1's fix was simply "turn this on," not "weigh the costs." The one reason it's listed here rather than assumed by default is historical: `eager` (`range` assignor) was Kafka's long-standing default for backward-compatibility reasons, and many production consumer groups still run with it unless explicitly reconfigured — making "is this group using `cooperative-sticky`?" one of the highest-value, lowest-effort questions in any operational review of an existing Kafka deployment.

### Single-Cluster Replication vs. Cross-Cluster (Geo) Replication

This design's `acks`/ISR/`min.insync.replicas` model (§4.3) is **synchronous, within-cluster** replication — followers in the same cluster (typically the same region, often spread across availability zones) replicate the leader's log with latencies low enough that `acks=all` is viable for latency-sensitive producers. Cross-cluster replication (§6's MirrorMaker example) is a fundamentally different, **asynchronous, eventually-consistent** mechanism layered on top:

| Dimension | Within-Cluster Replication (§4.3) | Cross-Cluster / Geo Replication (§6) |
|---|---|---|
| Mechanism | Followers fetch from leader; ISR membership tracked by the controller | A separate consumer (MirrorMaker/Connect) reads from the source cluster and produces to the destination cluster |
| Consistency | Synchronous option available (`acks=all`) — producer can wait for durability | Always asynchronous — the source cluster acknowledges writes independently of replication progress |
| Offset compatibility | Identical — a replica's offsets match the leader's exactly | **Not preserved** — the destination cluster assigns its own offsets; an offset-mapping topic translates between the two |
| Failure-domain isolation | Shares fate with the cluster (a cluster-wide outage affects all replicas) | Independent — a destination-cluster outage doesn't affect the source cluster's availability |
| Primary use case | Tolerating single-broker failures within a region (§8, §10 DR table) | Tolerating *cluster*-or-region failures, and serving geo-local reads without cross-region fetch latency |

**The boundary in one sentence**: within-cluster replication is what makes `acks=all` mean something (§4.3); cross-cluster replication is what makes "the entire cluster is gone" (§10's last DR row) survivable — and the two are deliberately decoupled because coupling them (e.g., requiring synchronous cross-region acks) would push write latency from low-hundreds-of-ms to the round-trip time between regions, often 50-150ms one-way, an unacceptable cost for the throughput targets in §2.

---

## 6. Real-World Implementations

- **Apache Kafka** (LinkedIn, now Apache Software Foundation / Confluent): the reference architecture for this entire design. Kafka originated at LinkedIn around 2010 to solve exactly the "O(n^2) point-to-point pipeline" problem described in [`../message_queues/README.md`](../message_queues/README.md) — every team's service talking directly to every other team's service. Kafka's partitioned-log model, consumer groups, and (later) idempotent producers and transactions (§4.5) are Confluent/Apache Kafka features in production use at LinkedIn, Netflix, Uber, and thousands of other companies, often at clusters processing **trillions of events per day** in aggregate (Netflix's Keystone pipeline alone processes roughly 500 billion events/day).
- **Amazon Kinesis Data Streams**: Amazon's managed equivalent, where a **shard** plays the role of a Kafka partition — each shard supports up to 1MB/sec or 1,000 records/sec write throughput and 2MB/sec read throughput, numbers directly comparable to the ~10MB/sec/partition rule of thumb in §2/§10 (Kinesis shards are deliberately sized smaller, pushing customers toward more shards for the same throughput). Kinesis's API model differs meaningfully from Kafka's: there's no separate "consumer group" concept with broker-managed partition assignment — instead, the **Kinesis Client Library (KCL)** implements client-side lease-based partition assignment using DynamoDB as a coordination store, conceptually similar to but architecturally distinct from Kafka's Group Coordinator (§4.4).
- **Google Cloud Pub/Sub**: a fully managed service that deliberately **does not expose partitions** to the user — a topic is a single logical entity, and Pub/Sub handles sharding internally and auto-scales transparently. The cost of this simplicity is **ordering**: by default, Pub/Sub provides no ordering guarantee at all; an opt-in "ordering key" feature provides per-key ordering (conceptually similar to Kafka's per-partition-key ordering, §4.2) but at the cost of capping throughput for that key to a single "ordering region" at a time. This is the cleanest illustration of §5's pull-based, partition-exposed tradeoff taken to its logical extreme in the other direction — Pub/Sub trades partition-level control (and the ordering/replay guarantees that come with it) for zero-operational-overhead auto-scaling.
- **Apache Pulsar**: separates the **compute tier** (stateless "brokers" that serve produce/consume traffic) from the **storage tier** (Apache BookKeeper, a separate distributed log storage system using "segments" called ledgers). This means a Pulsar broker can fail and be replaced without any data movement — the ledgers it was serving are immediately servable by any other broker, since storage was never broker-local. Contrast with Kafka's traditional model (§4.1, §4.3), where each partition's data lives on the disks of the brokers hosting its replicas, and broker replacement requires data to be physically re-replicated onto the new broker (a process directly relevant to §8's "Broker Failure" runbook). KRaft-based Kafka (replacing ZooKeeper) doesn't change this storage coupling — Pulsar's segment-based, storage/compute-separated architecture is a genuinely different point in the design space, not just a metadata-layer difference.
- **RabbitMQ**: the canonical contrast case for §5's pull-vs-push discussion. RabbitMQ implements a **"smart broker, dumb consumer"** model — the broker (via AMQP exchanges, bindings, and routing keys) makes routing decisions, tracks per-message acknowledgment state, and pushes messages to consumers based on prefetch counts; the consumer's job is simply to process whatever arrives and ACK/NACK it. Kafka inverts this entirely: a **"dumb broker, smart consumer"** model, where the broker is a mostly-passive sequential-log store (it doesn't know or care which consumers exist or what they've read), and all the "intelligence" — tracking position, deciding when to fetch more, deciding whether to commit — lives in the consumer. This single architectural inversion explains nearly every other difference between the two systems: RabbitMQ's rich routing topology (exchanges, multiple exchange types, per-message TTL) is broker-side intelligence that Kafka simply doesn't have, while Kafka's replay, multi-group fan-out, and extreme throughput (§2) stem from the broker doing as little per-message bookkeeping as possible.
- **LinkedIn's MirrorMaker 2 for cross-cluster replication**: LinkedIn — Kafka's birthplace — runs **hundreds of Kafka clusters** across multiple datacenters, and uses **MirrorMaker 2** (itself built on Kafka Connect) to replicate topics between them for both disaster recovery and geo-local read access (consumers in a region read a mirrored *replica* topic rather than crossing datacenters for every fetch). MirrorMaker 2 preserves partitioning (a source partition `N` maps to destination partition `N`) and translates consumer-group offsets between clusters via an offset-mapping topic, so a consumer group can **fail over from a primary cluster to a DR cluster** and resume from approximately the right position — directly operationalizing §10's Disaster Recovery table for the "entire cluster unavailable" row, but at the *cross-cluster* rather than *cross-broker* level. The key caveat operators learn quickly: replication lag between clusters means the DR cluster's offsets are *not* identical to the primary's, so failover is "resume from approximately here," not "resume from exactly here" — an at-least-once, not exactly-once, cross-cluster guarantee even when the primary cluster itself runs `acks=all`.

### Comparison at a Glance

| System | Partition Unit | Ordering Scope | Replay | Storage/Compute Coupling | Smart Side |
|---|---|---|---|---|---|
| Apache Kafka | Partition (§4.1) | Per-partition | Yes — seek to any offset | Coupled — partition data lives on its replica brokers' disks | Consumer (pull-based, §5) |
| Amazon Kinesis | Shard (1MB/sec or 1,000 records/sec) | Per-shard | Yes — up to retention (default 24h, extendable to 365 days) | Managed — AWS-internal | Consumer (KCL, lease-based) |
| Google Cloud Pub/Sub | None exposed | None by default; per-key with "ordering keys" (opt-in, throughput-capped) | Yes — seek by timestamp (with seek enabled) | Fully managed, auto-scaled | Managed service (push or pull delivery) |
| Apache Pulsar | Topic partition, backed by BookKeeper segments/ledgers | Per-partition | Yes | Decoupled — brokers stateless, BookKeeper holds data (§6) | Consumer (pull) for streams; supports push-like "shared" subscriptions too |
| RabbitMQ | Queue | Per-queue (single consumer) or none (competing consumers) | No — ACKed messages are gone | Coupled — queue data lives on the broker that owns it | Broker (push-based, smart routing) |

This table is the fastest way to answer "how does X compare to Kafka" in an interview: locate X's row, and the differences from Kafka's row are usually the differences that matter for the specific follow-up question being asked (ordering scope differences explain replay/fan-out tradeoffs; storage/compute coupling explains failure-recovery speed, §8).

---

## 7. Technologies & Tools

| Component | Representative Technologies | Notes |
|---|---|---|
| Broker / log storage | Apache Kafka (local disk, segment files, §4.1) | This design's reference implementation |
| Managed alternative | Amazon Kinesis, Confluent Cloud, Azure Event Hubs | Removes broker-ops burden; shard/partition limits differ (§6) |
| Coordination / metadata | KRaft (modern Kafka) or ZooKeeper (legacy) | Tracks broker membership, partition leader assignments, ISR state (§4.3) |
| Serialization | Avro, Protobuf with Schema Registry | Out of scope for this design (§1) but a near-universal companion |
| Consumer client libraries | `kafka-clients` (Java), `confluent-kafka` (Python/Go/C), `KafkaJS` (Node) | Implement the poll/commit/rebalance protocol described in §4.4 |
| Monitoring | Burrow, Confluent Control Center, Prometheus + `kafka_exporter`, Datadog | Consumer lag (§4.6), ISR/under-replication metrics (§8) |
| Stream processing (downstream, out of scope) | Kafka Streams, Apache Flink, ksqlDB | Consume from / produce to the topics this design defines |
| Tiered storage backend | S3-compatible object storage | §4.7 — holds closed segments beyond the local-disk "hot" window |
| Change Data Capture producers | Debezium (MySQL/Postgres -> Kafka) | A common producer pattern feeding topics in this design from upstream databases |
| Topic/ACL/quota management | `kafka-topics.sh`, `kafka-configs.sh`, Terraform providers (Confluent, Aiven) | Infrastructure-as-code for the topic configs referenced throughout §4 (retention, `min.insync.replicas`, `cleanup.policy`) |

### Reference Topic Configuration

The following configuration values, drawn from earlier sections, represent a reasonable starting point for a correctness-sensitive topic (e.g., the `orders` topic from §3):

```bash
kafka-topics.sh --create \
  --topic orders \
  --partitions 24 \
  --replication-factor 3 \
  --config min.insync.replicas=2 \
  --config retention.ms=604800000 \
  --config unclean.leader.election.enable=false \
  --config compression.type=lz4 \
  --config segment.bytes=1073741824
```

Mapping each setting back to the sections that motivate it: `partitions=24` (§2/§10 — throughput plus consumer-parallelism headroom for a topic well under the §2 cluster total), `replication-factor=3` with `min.insync.replicas=2` (§4.3, War Story 2 — survives one broker failure without data loss), `retention.ms=604800000` = 7 days (§2 — the default planning baseline), `unclean.leader.election.enable=false` (§4.3, War Story 2 — availability-for-durability tradeoff for correctness-sensitive data), `compression.type=lz4` (§10 — reduces the disk-footprint multiplier), and `segment.bytes=1073741824` = 1GB (§4.1 — the segment-rolling threshold that bounds how much of a partition's data is "active" at once).

### Build vs. Buy Considerations

| Component | Self-Hosted (Build) | Managed (Buy) | This Design's Framing |
|---|---|---|---|
| Broker cluster | Self-managed Kafka (full control over partition layout, retention, ACLs) | Confluent Cloud, MSK, Kinesis | Self-hosted gives the partition/replication control this design relies on (§4.1-§4.3); managed trades that control for reduced ops burden — the **architectural concepts (§4) remain identical either way**, only the operational playbook (§8) shifts to the vendor |
| Monitoring/lag alerting | Prometheus + `kafka_exporter` + custom dashboards | Confluent Control Center, Datadog Kafka integration | Either way, consumer lag (§4.6) and ISR health (§8) are the two metrics that must exist on day one — this is not optional regardless of build-vs-buy |
| Schema governance | Self-hosted Schema Registry | Confluent Cloud Schema Registry | Out of scope for this design (§1), but every real deployment needs one |

---

## 8. Operational Playbook

### Key Metrics

| Metric | What It Measures | Alert Threshold (Illustrative) |
|---|---|---|
| **Consumer lag per partition** (§4.6) | How far behind a consumer group is, in messages and/or time | Page if lag-in-time exceeds a few minutes for latency-sensitive groups, or if lag is *growing* for more than 10 minutes regardless of absolute value |
| **Under-replicated partition count** | Number of partitions where ISR size < replication factor | Page if > 0 and sustained for more than a few minutes — this is the leading indicator for both War Stories in §9 |
| **ISR shrink/expand rate** | Frequency of replicas entering/leaving the ISR | Investigate if shrink rate spikes — often correlates with broker GC pauses, disk I/O saturation, or network partition |
| **p99 produce latency** | End-to-end time for `send()` to complete (depends on `acks`, §4.3, §5) | Page if p99 exceeds 2-3x the `acks`-appropriate baseline (tens of ms for `acks=1`, low hundreds for `acks=all`) |
| **p99 fetch latency** | End-to-end time for a consumer `poll()` to return | Page if sustained increase correlates with broker-side issues (disk, GC) rather than expected `fetch.max.wait.ms` |
| **Disk usage vs. retention** | Actual disk consumption vs. configured retention-implied capacity (§2, §10) | Page at 80% of provisioned capacity — running out of disk on a broker causes write failures for every partition it leads |

### Runbook: Broker Failure and Leader Re-Election

1. **Detect**: the under-replicated-partition-count metric spikes for every partition the failed broker was leading or following; the controller detects the broker's session expiry (heartbeat timeout) and marks it down.
2. **Automatic leader re-election**: the controller selects a new leader from each affected partition's ISR (§4.3) — by definition, every ISR member has all committed records, so this is zero-data-loss. Producers and consumers receive a `NotLeaderForPartitionException` on their next request, refresh their metadata (discovering the new leader), and reconnect — typically a **few seconds of disruption**, not an outage.
3. **Verify `min.insync.replicas` is still satisfied**: with RF=3 and `min.insync.replicas=2`, losing 1 broker still leaves 2 ISR members for most partitions — writes continue. If a *second* broker is already down or degraded when this one fails, some partitions' ISR may drop to 1, at which point `acks=all` writes to those partitions start failing with `NotEnoughReplicasException` — this is the system correctly choosing unavailability over an under-durable commit (§5).
4. **Replace the failed broker**: bring up a replacement broker (same broker ID if using a Kafka-managed reassignment, or a new ID with a partition-reassignment plan). The new broker's replicas start as out-of-sync followers and must fully replicate each partition's log from the current leader before rejoining the ISR — this replication traffic is itself a load spike on the leaders, so reassignment is throttled (`leader.replication.throttled.rate`) to avoid degrading live traffic.
5. **Confirm recovery**: under-replicated-partition count returns to 0; ISR membership for all affected partitions includes the replacement broker.

### Runbook: Consumer Group Rebalance Storm

1. **Detect**: consumer lag spikes sharply and simultaneously across *many or all* partitions of a topic, correlating with a deploy, autoscaling event, or a wave of consumer restarts — distinct from the "one partition's consumer is slow" pattern, which shows lag growth isolated to a subset of partitions.
2. **Confirm it's a rebalance, not a processing slowdown**: check the consumer group's rebalance rate/count metric and broker logs for repeated `JoinGroup`/`SyncGroup` requests from the same group — a "storm" is characterized by **rebalances triggering more rebalances** (each completed rebalance is immediately followed by another, because more members are still joining/leaving).
3. **Immediate mitigation**: if the storm is caused by an in-progress rolling deploy, **pause the deploy** — let the currently-restarting batch of consumers finish joining and let the group stabilize before resuming the rollout. Resuming a deploy mid-storm adds fuel to the cycle described in War Story 1.
4. **Root-cause check**: verify whether the consumer group is using `cooperative-sticky` (incremental cooperative, §4.4) vs. the legacy `eager` (`range`/`round-robin`) assignor, and whether `group.instance.id` (static membership, §4.4) is configured. Both are the structural fixes for War Story 1 — if neither is in place, this incident will recur on every future deploy.
5. **Verify recovery**: lag returns to baseline across all partitions, and the rebalance-count metric returns to its idle rate (ideally near zero outside of genuine membership changes).

### Runbook: Broker Disk Usage Approaching Retention-Implied Capacity

1. **Detect**: the disk-usage-vs-retention metric (§8 Key Metrics) crosses 80% on one or more brokers — note that disk usage is rarely uniform across brokers (§10's hot-partition discussion), so this often affects a handful of brokers before the cluster average looks concerning.
2. **Identify the dominant contributor**: per-topic, per-partition disk usage breakdown almost always reveals one of three causes — (a) a single topic's traffic grew faster than its retention budget anticipated, (b) a hot partition within a topic (poor key cardinality, §11) is accumulating disproportionately, or (c) a topic intended to be compacted (`cleanup.policy=compact`, §5) is instead using `cleanup.policy=delete` (or vice versa) due to a configuration error, and its dirty-portion (§4.7) has grown unchecked.
3. **Immediate mitigation (buys time, doesn't fix root cause)**: temporarily reduce `retention.ms` or `retention.bytes` for the offending topic — this triggers the next retention-enforcement pass (§4.1) to delete older segments sooner, freeing disk within one segment-roll interval. This is a **lossy** mitigation for any consumer group that hasn't yet read the segments being deleted early — check consumer lag (§4.6) for that topic *before* applying this, and prefer option 4 if any consumer group's lag would be affected.
4. **Structural fix**: either (a) enable tiered storage (§4.7) for the topic, moving closed segments to object storage and reclaiming local disk without reducing logical retention, (b) fix the partition key if a hot partition is the cause (§11 — though this requires the same "ordering breaks across the resize" caveat as any partition-count change), or (c) correct the `cleanup.policy` misconfiguration if that's the root cause.
5. **Verify recovery**: disk usage on affected brokers trends back below 80%, and confirm no consumer group's lag increased as a side effect of step 3's early-deletion mitigation (cross-check against §11's "fell behind retention" edge case).

---

## 9. Common Pitfalls & War Stories

### War Story 1: A Rolling Deploy Triggers a Rebalance Storm — Broken, Then Fixed

**Broken**: A consumer group with **300 consumer instances** reading a 300-partition topic (one partition per consumer — no spare capacity) used the default `eager` rebalance protocol (`range` assignor) with no static membership configured. A routine rolling deploy restarted instances in batches of 30 at a time, with no special coordination with the consumer group.

**Impact**: Each batch of 30 instances restarting triggered `eager` rebalancing — which, recall from §4.4, revokes **all 300 partitions from all 300 consumers**, not just the 30 being restarted. While the rebalance computed and propagated the new assignment, **all 300 consumers stopped processing simultaneously**. Before the rebalance could fully stabilize, the *next* batch of 30 instances began restarting (the deploy didn't wait for consumer-group stability, only for the instances' own health checks to pass) — triggering *another* full-group rebalance on top of the first, which hadn't finished propagating. This cascaded: each new batch's restart re-triggered a stop-the-world rebalance before the previous one settled, and the group spent the **entire ~15-minute deploy window** in a near-continuous rebalancing state, processing almost nothing. End-to-end consumer lag, normally near-zero, climbed to **over 2 million messages** by the time the deploy completed and the group finally stabilized, taking another 20 minutes to drain — a 35-minute SLA breach, recurring on **every deploy** of this service.
 
**Fixed**: Two changes, applied together:
1. **Switched to incremental cooperative rebalancing** (`partition.assignment.strategy=cooperative-sticky`). With this assignor, restarting 30 of 300 consumers only revokes and reassigns the partitions those 30 instances owned (~30 of 300) — the other 270 consumers continue processing their partitions, completely unaffected, throughout the deploy.
2. **Enabled static group membership** (`group.instance.id` set to a stable per-instance identifier, e.g., derived from the pod's ordinal index in a StatefulSet) with `session.timeout.ms` tuned to comfortably exceed the time a single instance takes to restart during a deploy (e.g., 45 seconds). A consumer that restarts within this window is recognized by the coordinator as "the same member reconnecting," and **no rebalance is triggered at all** for that instance's restart — only a genuinely new or permanently-departed member triggers reassignment.

Combined, these two changes reduced the rebalance count for a typical 15-minute rolling deploy from **roughly 10 full-group rebalances** (one per restart batch, each cascading) to **zero** — every instance restart within the session-timeout window is a silent reconnect, and the rare case of an instance that genuinely doesn't come back (a failed deploy) triggers exactly one incremental rebalance affecting only that instance's partitions. Consumer lag during deploys now stays within normal operating range (low thousands, draining within seconds), eliminating the recurring SLA breach.

### War Story 2: `acks=1` Plus Unclean Leader Election Causes Silent Data Loss — Broken, Then Fixed

**Broken**: A topic carrying order-state-change events (the kind of data War Story 4 in [`design_google_maps.md`](./design_google_maps.md) would call "structural, not cosmetic" — losing one of these events means an order's state machine has a gap) was configured with `RF=3`, `min.insync.replicas=1`, producers using `acks=1`, and `unclean.leader.election.enable=true` (the historical Kafka default at the time).

**Impact**: During a routine infrastructure maintenance window, **two of the three brokers hosting this partition's replicas** were taken down in quick succession for OS patching — the maintenance tooling didn't check `min.insync.replicas` or ISR membership before proceeding to the second broker, because `min.insync.replicas=1` meant the partition was technically still "available" after the first broker went down. With only one broker (the leader) left, that broker's local replica was the *only* copy. Producers, using `acks=1`, had already received successful acknowledgments for a batch of roughly 800 order-state events the instant the (sole remaining) leader wrote them to its local log — **before** the other two replicas, now down, had replicated them. Then the leader broker itself crashed (an unrelated disk issue, coincidentally during the same maintenance window). When the cluster recovered, the controller had no ISR members for this partition with the missing 800 records — `unclean.leader.election.enable=true` allowed one of the two recovering-but-behind replicas to become the new leader anyway, **truncating the log to that replica's (earlier) end offset**. The 800 events, already acknowledged to producers and already consumed-and-acted-upon by some downstream consumers (which had read them before the crash), **simply ceased to exist** in the topic. The gap was discovered **four days later** during a routine reconciliation between the order-state topic and the source-of-truth database, which showed 800 orders with state-machine gaps that no amount of replaying the topic could fill — the events were gone.

**Fixed**: A three-part change applied to all correctness-sensitive topics:
1. **`acks=all`** — producers now wait for the full ISR to acknowledge, not just the leader. This alone would have prevented the 800-event loss: the events would never have been acknowledged in the first place if only 1 of 3 replicas had them.
2. **`min.insync.replicas=2`** — combined with `acks=all`, this guarantees every acknowledged write exists on at least 2 brokers. The maintenance-tooling scenario above — two brokers down simultaneously — would now make the partition **reject writes** (`NotEnoughReplicasException`) rather than silently accept under-replicated ones; an availability hit during maintenance, but a correctness guarantee always.
3. **`unclean.leader.election.enable=false`** — if all ISR members are genuinely unavailable, the partition becomes **unavailable for both reads and writes** rather than electing a behind-the-times leader and silently truncating history. This is an explicit availability-for-durability tradeoff (§5), accepted for this topic class specifically because a silent multi-day-undetected data gap is a worse outcome than a bounded outage.

A fourth, process-level change: **alerting on ISR-shrink events** (§8) now pages on-call *immediately* when any correctness-sensitive partition's ISR drops below `min.insync.replicas + 1` (i.e., one failure away from blocking writes), giving operators a chance to intervene *before* a second failure compounds — rather than discovering the consequence days later through reconciliation.

### War Story 3: An Unthrottled Partition Reassignment During Scale-Out Saturates the Cluster — Broken, Then Fixed

**Broken**: A cluster running near its per-broker partition ceiling (§2, §10) needed to add 10 new brokers to absorb sustained growth. The operations team kicked off a partition-reassignment plan to rebalance roughly 1,500 partition-replicas onto the new brokers — moving a replica means the destination broker must fully replicate that partition's log from its current leader, from offset 0 to the current end, regardless of retention size (§4.1). The reassignment was submitted with no throttle configured (`leader.replication.throttled.rate` and `follower.replication.throttled.rate` left at their unset/unlimited defaults).

**Impact**: The 10 new brokers immediately began fetching full-history replicas for their assigned partitions **as fast as the network and source brokers' disks would allow** — for partitions with several hundred GB of retained data each (§10's ~53TB/broker figures imply many large individual partitions), this meant the existing brokers' disks and NICs were suddenly serving *both* their normal produce/replicate/fetch traffic from §2's ~24Gbps write-path estimate *and* an additional bulk-copy load that, unthrottled, consumed the **majority of available disk I/O and network bandwidth** on the source brokers. Within minutes, p99 produce latency for `acks=all` topics — normally low hundreds of milliseconds (§2) — spiked to **over 10 seconds**, and several partitions' ISRs shrank as existing follower replicas fell behind their leaders (competing for the same disk I/O the reassignment was consuming). For correctness-sensitive topics with `min.insync.replicas=2` (War Story 2's fix), some partitions' ISRs dropped to exactly `min.insync.replicas`, one more slow disk away from rejecting writes entirely. The reassignment itself took **over 18 hours** to complete — far longer than the operations team's maintenance-window estimate — because the bulk-copy traffic was itself competing with, and being slowed by, the production traffic it was degrading.

**Fixed**: Reassignments for large partitions are now **always submitted with an explicit replication throttle** (`leader.replication.throttled.rate` and `follower.replication.throttled.rate`, e.g., capped at a fraction of each broker's provisioned NIC capacity — leaving headroom for produce/consume traffic to stay within its §2 latency budget). The throttled reassignment took **longer in wall-clock time** (roughly 30 hours instead of 18) but **produce latency and ISR health remained within normal operating bounds throughout** — the explicit tradeoff being "slower migration, zero customer impact" instead of "faster migration, partial outage." A second change: large reassignments are now broken into **batches of a few hundred partition-replicas at a time**, each batch's completion verified (under-replicated-partition count back to 0, §8) before the next batch starts — bounding the blast radius of any single batch's throttle miscalculation to a fraction of the cluster rather than all 1,500 replicas moving simultaneously.

---

## 10. Capacity Planning

### Sizing Partition Count from Target Throughput

The starting point is the **~10MB/sec/partition** sustained-write rule of thumb from §2 (a conservative number; well-tuned partitions with large batches and compression can exceed this, but 10MB/sec is a safe planning baseline that leaves headroom for replication and consumer-fetch traffic sharing the same disk/network).

```
required_partitions = ceil(target_throughput_MB_per_sec / 10 MB_per_sec_per_partition)
```

For the §2 target of ~1GB/sec (1,000MB/sec) cluster-wide, spread across multiple topics rather than one: a single dominant topic carrying, say, 200MB/sec needs `ceil(200 / 10)` = **20 partitions** minimum for throughput alone. The *actual* partition count for that topic is then the **max** of this throughput-derived number and the consumer-parallelism-derived number from §1/§2 (whichever requirement is larger governs).

### Sizing Broker Count from Partition Count

```
total_partition_replicas = sum over topics of (partitions x replication_factor)
brokers_needed = ceil(total_partition_replicas / target_replicas_per_broker)
```

Using the §2 example of ~5,000 total partition-replicas (roughly 1,667 logical partitions at RF=3) and a conservative **150 replicas/broker** target (comfortably under the 2,000-4,000 per-broker ceiling mentioned in §2, leaving headroom for uneven distribution and future growth): `ceil(5,000 / 150)` ~= **34 brokers**. Real deployments round up further for fault-tolerance — losing one broker out of 34 redistributes ~150 replicas across the remaining 33 (~4.5 extra replicas/broker), a manageable bump; losing one of 5 would redistribute ~1,000 replicas across 4, a much larger per-broker spike.

### Sizing Disk from Retention x Ingestion Rate x Replication Factor

```
disk_per_broker = (cluster_ingestion_rate x retention_seconds x replication_factor) / broker_count
```

From §2: `1GB/sec x 604,800 sec (7 days) x 3 (RF)` ~= **1.8PB cluster-wide**. Across 34 brokers: `1.8PB / 34` ~= **~53TB/broker** — a realistic figure for a broker with multiple large SSDs/NVMe drives in a JBOD or RAID configuration. Two adjustments matter in practice:
- **Uneven topic distribution**: not every topic produces at a steady rate, and not every partition within a topic receives equal traffic (a poorly-chosen partition key, §4.2, can create a "hot partition" that's 10x the size of its siblings on the same broker) — provision per-broker disk with at least 20-30% headroom above the evenly-divided average.
- **Compression**: the 1.8PB figure is uncompressed-equivalent; with typical 2-3x compression for JSON/Avro payloads (`compression.type=lz4` or `zstd`), *actual* disk usage might be 600GB-900GB-equivalent per broker — but planning on the uncompressed number means a sudden shift to less-compressible payloads (e.g., already-compressed binary blobs) doesn't blow the disk budget.

### Sizing Network Bandwidth for Replication Traffic

From §2: producer-to-leader traffic (~1GB/sec = ~8Gbps cluster-wide) plus leader-to-follower replication traffic (~2GB/sec = ~16Gbps cluster-wide at RF=3) totals **~24Gbps** for the write path alone, *before* consumer fetch traffic (which can be comparable to or larger than produce traffic if multiple consumer groups each read the full stream — §3's "analytics" group reading the same topic as "order-processor" effectively doubles read-side network load per additional group).

```
per_broker_replication_bandwidth = (broker's share of produce traffic) x (RF - 1)
```

For a broker leading an even 1/34th share of the cluster's 1GB/sec produce traffic (~29MB/sec ~= 235Mbps as a leader), it additionally sends/receives `~29MB/sec x 2` ~= ~58MB/sec ~= ~470Mbps of replication traffic to/from its peers as a follower for *other* brokers' partitions — a 10Gbps NIC per broker provides comfortable headroom (roughly 700Mbps of the write path against a 10Gbps link, leaving the overwhelming majority of bandwidth for consumer fetch traffic and burst absorption).

### Summary Table

| Component | Sizing Basis | Estimated Footprint |
|---|---|---|
| Partition count (dominant topic) | ~200MB/sec target / ~10MB/sec/partition | ~20 partitions minimum (throughput-bound) |
| Total cluster partition-replicas | Sum across topics x RF=3 | ~5,000 replicas (~1,667 logical partitions) |
| Broker count | ~5,000 replicas / ~150 replicas/broker | ~34 brokers |
| Disk per broker | 1.8PB cluster / 34 brokers | ~53TB/broker (uncompressed-equivalent) |
| Network per broker (write path) | ~29MB/sec lead + ~58MB/sec replication | ~700Mbps of a 10Gbps NIC |
| Consumer instances (per group) | `min(partition_count, throughput_needed / per_consumer_throughput)` | Varies — sized for the *slowest* consumer group reading a topic |

### Cold-Start: Bootstrapping a New High-Throughput Topic

When a new topic is provisioned for a workload expected to reach the §2-scale targets, the topic doesn't start at steady state — both the producer and the broker side have ramp-up considerations:

1. **Partition count is fixed at creation time, but should be set for the *target* throughput, not the launch-day throughput.** Recall from §4.2 and §11 that increasing partition count later changes `hash(key) % numPartitions` for every key, breaking per-key ordering continuity for records produced after the resize. A topic expected to reach 200MB/sec within 6 months should be created with ~20+ partitions on day one (§10's throughput-derived sizing), even if launch-day traffic is a fraction of that — the cost of "too many partitions early" (some rebalance/file-handle overhead, §5) is far smaller than the cost of "resize later and break ordering."
2. **Producer-side batching takes time to tune.** `linger.ms` and `batch.size` (§4.2) are tuned against *observed* traffic patterns — a topic's first few days of production traffic are the input to this tuning, not a one-time calculation done in advance. Under-tuned batching (too-small batches) early in a topic's life manifests as higher-than-expected per-partition request rates without a corresponding throughput benefit — worth checking before concluding a topic "needs more partitions" when it may just need better batching.
3. **Consumer group sizing should start conservative and scale via lag, not forecast.** Rather than provisioning a consumer group at the *predicted* steady-state instance count from day one, start with a smaller group and scale out reactively based on consumer lag (§4.6, §8) — this avoids over-provisioning for a forecast that, as in the cold-start discussion of [`design_google_maps.md`](./design_google_maps.md) §10, frequently misses (in either direction) on launch day.
4. **Replication catch-up for a brand-new topic is trivial** — unlike War Story 3's reassignment-of-existing-data scenario, a new topic's partitions start empty, so follower replicas reach ISR membership (§4.3) almost instantly. The cold-start risk is entirely about *configuration* (partition count, retention, `acks`/`min.insync.replicas`) being right from the first message, not about replication catch-up time.

### Disaster Recovery: Failure Modes and Recovery Paths

| Failure | Detection | Mitigation | Recovery Time / Degraded Behavior |
|---|---|---|---|
| Single broker fails | Controller detects heartbeat timeout; under-replicated-partition count spikes (§8) | Automatic leader re-election from ISR (§4.3, §8 runbook) | Seconds — producers/consumers reconnect to the new leader; zero data loss if ISR-based election |
| Follower replica falls behind (slow disk, GC pause) | ISR-shrink metric (§8) | Replica continues catching up in background; removed from ISR until caught up | None visible if `min.insync.replicas` still satisfied by remaining ISR members |
| Two brokers fail simultaneously, `min.insync.replicas=2`, RF=3 | `NotEnoughReplicasException` on produce for affected partitions | Partition becomes unavailable for `acks=all` writes until a broker recovers | Correctness-preserving unavailability (§5) — the system refuses to accept under-durable writes rather than risk War Story 2 |
| Consumer group falls behind retention | Consumer lag approaching retention window (§4.6, §8) | Scale consumers, or accept `auto.offset.reset` jump (§11) | Permanent loss of unread records from this consumer group's perspective — broker data was never lost, just aged out before this group read it |
| Entire cluster (all brokers) unavailable | Cluster-wide alerting; producers' `send()` calls fail/buffer | Producers buffer (bounded by `buffer.memory`) and retry; if outage exceeds buffer capacity, producer-side application must apply its own backpressure/circuit-breaking (cross-ref [`../resilience_patterns/README.md`](../resilience_patterns/README.md)) | Producing applications degrade to whatever their own circuit-breaker/fallback path is (e.g., write to local disk, drop non-critical events) — this design's cluster-level HA (RF=3 across racks/AZs) is intended to make this scenario rare, not to eliminate the need for a producer-side fallback |

The key DR design point for an interview: **replication factor and `min.insync.replicas` (§4.3, War Story 2) are what make single- and even dual-broker failures non-events from a data-loss perspective; everything beyond that (full-cluster outage) is a blast-radius problem that the producing *applications*, not the queue itself, must have a fallback for** — a message queue cannot guarantee delivery if it is, in its entirety, unreachable, so resilient producers always pair "the queue is durable" with "what do I do if the queue is unreachable" (cross-ref [`../resilience_patterns/README.md`](../resilience_patterns/README.md)).

---

## 11. Interview Discussion Points

**Q: Why is ordering only guaranteed within a partition, not across an entire topic?**
Because a partition is, by construction, a single append-only log written by a single leader and read sequentially — there's exactly one "next position," so "order of writes" and "order of reads" trivially agree. A topic, by contrast, is N independent partitions; guaranteeing order *across* them would require either a single global writer (destroying the parallelism that's the entire point of partitioning, §1) or an expensive cross-partition coordination protocol on every write. The practical resolution (§4.2) is that you choose a partition key such that everything that *needs* relative ordering (e.g., all events for one order ID) lands in the same partition via `hash(key) % numPartitions` — ordering is a property you design into your key choice, not a property the system gives you globally for free.

**Q: What does "exactly-once" actually guarantee end-to-end, and where can it still break?**
Within the Kafka cluster, idempotent producers (§4.5) deduplicate retried writes via per-partition sequence numbers, and transactions atomically bundle "produce to output topics" with "commit input-topic offsets" — so a consume-transform-produce stage either fully happens or is fully invisible, even across crashes. It breaks the moment a side effect crosses the Kafka boundary: if a consumer calls an external payment API as part of processing, that call isn't covered by the Kafka transaction — a crash between "Kafka transaction committed" and "API called" (or the reverse) reintroduces at-least-once/at-most-once for that external call, which then needs its own idempotency mechanism (e.g., an idempotency key). The honest framing for an interview: "exactly-once" means "exactly-once **within Kafka**," and any external system touched by a consumer needs to be reasoned about separately.

**Q: Why isn't more partitions always better?**
More partitions increase consumer parallelism ceiling, but every partition is a standing cost: open file handles on every broker hosting a replica (§4.1), a unit of work in every consumer group's rebalance computation (§4.4, §5), and controller/metadata overhead. A topic over-provisioned to 1,000 partitions when 50 would suffice makes every rebalance ~20x more expensive — and that cost recurs on every deploy and every scale event, not just once. Worse, partition count **cannot be decreased**, and increasing it changes `hash(key) % numPartitions` for every existing key, breaking the historical "same key, same partition" guarantee for records produced afterward. The right sizing approach (§10) is `max(throughput-derived count, consumer-parallelism-derived count)`, chosen deliberately rather than "as many as possible."

**Q: `acks=all` vs `acks=1` vs `acks=0` — what's the actual tradeoff?**
It's a direct latency-for-durability trade (§4.3, §5): `acks=0` doesn't wait for any broker response (lowest latency, can lose data silently on a leader crash before the write even lands), `acks=1` waits for the leader's local write only (low latency, but a leader crash before follower replication can lose acknowledged data — War Story 2), and `acks=all` waits for the full ISR (highest latency, survives any single-broker failure given `min.insync.replicas >= 2`). The right choice is per-topic based on the cost of losing a message: payment/order-state topics use `acks=all` + `min.insync.replicas=2`; high-volume loss-tolerant telemetry can use `acks=1` for the throughput/latency win, since aggregate signals survive individual record loss.

**Q: Consumer lag vs. broker/replication lag — what's the difference?**
Consumer lag (§4.6) is `latest_offset - committed_offset` for a **consumer group** — it measures how far behind a *reader* is, and is entirely about application-side processing speed; it has no effect on the broker's durability guarantees. Replication lag is about how far behind a **follower replica** is from its partition's leader (§4.3) — it determines ISR membership and is purely a broker-internal durability concern. The two are completely independent: a topic can have zero replication lag (all replicas perfectly in sync) while a consumer group has millions of messages of lag (a slow consumer), or vice versa (a consumer keeping up in real time while a follower replica struggles to replicate due to a slow disk). Conflating the two is a common mistake — "the topic is lagging" needs to specify *which* kind.

**Q: Why pull-based instead of push-based?**
Pull puts the consumer in control of its own flow rate — backpressure is implicit (a slow consumer just polls less, accumulating lag, §4.6) rather than requiring the broker to track and enforce per-consumer delivery limits. Pull also enables **replay** (seek to any offset, even one already read) and **independent multi-group consumption** (N consumer groups read the same log at N different paces, each with its own offsets, §3) — both of which are awkward or impossible in push-based models where the broker deletes or marks messages "delivered" as it pushes them (§5, §6's RabbitMQ contrast). The cost is a slightly higher latency floor (a `poll()` cycle vs. an instant push) and the requirement that lag monitoring (§4.6) be a first-class operational concern, since the broker won't proactively tell you a consumer is falling behind.

**Q: How does the ISR mechanism actually decide when a replica is "in sync," and what happens when it falls behind?**
A replica is in the ISR if it has fetched up to (within `replica.lag.time.max.ms` of) the leader's latest offset (§4.3). A replica that stops fetching — due to a GC pause, disk slowness, or network issue — is removed from the ISR once it exceeds that lag threshold; it continues trying to catch up in the background (still replicating, just not counted toward `acks=all`/`min.insync.replicas` durability). Once it catches up, it's added back to the ISR. The practical effect: a *temporarily* slow follower doesn't block `acks=all` writes (the ISR shrinks to exclude it, and writes proceed against the remaining ISR members, as long as `min.insync.replicas` is still met) — but it does reduce the failure tolerance until it rejoins, which is why ISR-shrink-rate is an alerting metric (§8) even when it doesn't immediately block writes.

**Q: Walk through what happens during partition rebalancing — eager vs. incremental cooperative.**
Eager rebalancing (§4.4) revokes **every partition from every member** of the consumer group whenever membership changes, recomputes the full assignment from scratch, and reassigns — meaning the entire group stops processing during the rebalance, regardless of how small the membership change was. Incremental cooperative rebalancing computes the new assignment but only revokes the partitions whose **owner actually changes** — if 1 of 16 consumers leaves, only that consumer's ~6% of partitions are revoked and reassigned; the other 94% of partitions continue being processed by their current owners throughout. War Story 1 is the canonical illustration of why this distinction matters at scale: for a 300-consumer group, eager rebalancing's "stop everything" cost, repeated across a multi-batch rolling deploy, compounds into a multi-minute, group-wide stall.

**Q: What is the segment file structure, and why does retention operate on segments rather than individual records?**
Each partition's log is split into bounded-size segment files (e.g., 1GB each), each paired with a sparse offset-to-byte-position index (§4.1). The *active* segment (the newest) is the only one being appended to; older segments are immutable. Retention deletion is a background scan of *closed* segments — a segment is deleted (via `unlink()`, an O(1) filesystem operation) once its newest record exceeds the time-based retention window, or once total partition size exceeds the size-based limit. This is why retention is segment-granular: a record that's individually "too old" doesn't get deleted until its entire containing segment ages out — deleting individual records would require rewriting segment files (random I/O, the exact thing the append-only design avoids).

**Q: A consumer falls so far behind that the messages it hasn't read yet have already aged out of retention — what happens?**
The consumer's committed offset now points to a position **before the earliest available offset** in the partition (the oldest segments containing those records have been deleted, §4.1/§4.6). On its next `poll()`, the consumer gets an out-of-range error; depending on `auto.offset.reset` configuration, it either fails loudly (`none` — forcing manual intervention, the safer default for correctness-sensitive consumers) or silently jumps to the earliest *available* offset (`earliest`), which means it has **permanently lost** the records between its old committed offset and the new earliest offset — a silent data-loss-from-the-consumer's-perspective event, even though the broker never "lost" anything (the data simply aged out before this consumer read it). This is the sharpest edge case connecting §1's retention policy to §4.6's lag monitoring: lag monitoring must alert *before* lag-in-time approaches the retention window, not after.

**Q: A topic needs to scale out — partition count is increased from 50 to 100. What breaks, and what doesn't?**
What doesn't break: new messages are distributed across the new 100-partition space using `hash(key) % 100`, and consumer groups simply get reassigned (via a rebalance, §4.4) across the new partition count — existing consumer offsets for partitions 0-49 remain valid, since those partitions still exist with all their history intact. What *does* break: for keyed records, `hash(key) % 50` and `hash(key) % 100` generally produce **different** results for the same key — a key that mapped to partition 12 under the old scheme might map to partition 62 under the new one. Any ordering guarantee that depended on "all events for key K are in the same partition, in order" is now split across two partitions (old events for K in partition 12, new events for K possibly in partition 62) — a consumer reading "partition 12" no longer sees K's complete history in one place. This is why partition count is sized conservatively up front (§5, §10) and increased rarely, with affected downstream consumers explicitly made aware that per-key ordering continuity is not preserved across the resize.

**Q: How would you design the partition key for a topic carrying e-commerce order events, and what's the failure mode of a bad choice?**
The partition key should be the entity whose events need relative ordering and whose cardinality is high enough to spread evenly across partitions — `order_id` is a strong choice: all state-transition events for one order land in the same partition (ordering preserved per order, §4.2), and with potentially millions of distinct order IDs, load spreads evenly. A bad choice would be something low-cardinality like `region` (5-10 distinct values funneling all traffic into 5-10 partitions regardless of how many partitions the topic has — a hot-partition problem) or `null` keys when ordering *is* required (sticky/round-robin partitioning, §4.2, gives good load distribution but provides zero ordering guarantee for related events). The failure mode of a low-cardinality key is **hot partitions**: a handful of partitions receive disproportionate traffic, their brokers become bottlenecks while other brokers sit idle, and the cluster-wide throughput numbers from §2/§10 (predicated on roughly even partition load) don't materialize even though the partition *count* looks sufficient on paper.

**Q: How do you reprocess the last 24 hours of a topic for a new consumer or after fixing a bug, without affecting existing consumers?**
Because offsets are tracked **per consumer group** (§4.4, §3), a brand-new consumer group ID starts fresh and can have its offsets explicitly reset to a timestamp-derived position 24 hours in the past before its first `poll()` — it then replays that window independently, with zero impact on any other consumer group's offsets (each group's position in `__consumer_offsets` is a separate row, §5's compacted-topic discussion). This is the practical payoff of pull-based consumption with per-group offset tracking (§5, §11): "replay for analysis" or "reprocess after a bug fix" is a normal, supported operation — create a new group (or reset an existing one's offsets, if that group is fully stopped first to avoid a confusing partial-rewind), not a special recovery procedure.

**Q: You're scaling out a cluster by adding new brokers and need to move partition replicas onto them — what can go wrong, and how do you do it safely?**
Moving a partition replica means the destination broker must replicate that partition's entire retained log from scratch — for a large partition, potentially hundreds of GB (§10). Done without a replication throttle, this bulk-copy traffic competes for the same disk I/O and network bandwidth that production produce/consume traffic depends on, which is exactly War Story 3: p99 produce latency spiked from hundreds of milliseconds to over 10 seconds, and several partitions' ISRs shrank as existing followers fell behind while competing with the reassignment for disk I/O. The safe approach is to **always set `leader.replication.throttled.rate` / `follower.replication.throttled.rate`** to a fraction of available bandwidth before submitting a reassignment, and to **batch large reassignments** (a few hundred partition-replicas at a time), verifying under-replicated-partition count returns to 0 (§8) between batches. The reassignment takes longer in wall-clock time, but production traffic stays within its latency budget throughout — the correct tradeoff for an operation that, unlike a broker failure, is entirely under your control and doesn't need to complete in the next five minutes.

**Q: What's the difference between an idempotent producer and a transactional producer, and when do you need each?**
An idempotent producer (`enable.idempotence=true`, §4.5) solves exactly one problem: **retries of the same batch to the same partition don't create duplicates**, via per-`(PID, partition)` sequence numbers the broker uses to silently discard already-committed retries. This is sufficient for a single producer writing to a single partition who just wants "no duplicates from network-level retries" — a common and relatively cheap guarantee, often enabled by default. A transactional producer (`initTransactions()`/`beginTransaction()`/`commitTransaction()`) is a strictly bigger guarantee: it makes a **set of writes across multiple partitions/topics, plus a consumer offset commit, atomic** — either all of it becomes visible to `read_committed` consumers, or none of it does, even across a crash mid-transaction. You need transactions specifically for **consume-transform-produce** pipeline stages where "I processed this input and produced these outputs" must be all-or-nothing (§4.5); a simple producer that only ever writes to one topic and doesn't need atomicity with an offset commit only needs idempotence, which is cheaper (no transaction coordinator round trips).

**Q: How does sticky partitioning for null-key records interact with the "ordering within a partition" guarantee — does it break anything?**
No, because sticky partitioning (§4.2) only applies to records with **no key** — and the ordering guarantee in §1 is specifically about records that share a key landing in the same partition, in order. Null-key records, by definition, have no per-key ordering requirement to begin with (there's no "key" whose history needs to stay together), so distributing them across partitions — whether via pure round-robin or sticky batching — doesn't violate any guarantee that was ever made for them. What sticky partitioning *does* change is **batch efficiency**: by sending a `linger.ms` window's worth of null-key records to the same partition before rotating, it produces fewer, larger, more-compressible batches than strict round-robin, directly helping the ~10MB/sec/partition throughput target (§2, §10) without affecting any ordering semantics at all.

---

## Cross-References

- **General pub/sub theory, delivery guarantees, and DLQ patterns this case study applies concretely (§1, §4.5, §9)** -> [`../message_queues/README.md`](../message_queues/README.md)
- **Kafka internals — partition replication, ISR mechanics, and exactly-once semantics in greater depth (§4.3, §4.5, §9)** -> [`../../backend/kafka_deep_dive/README.md`](../../backend/kafka_deep_dive/README.md)
- **Partition assignment as a hashing/sharding problem, and the resize-breaks-ordering edge case (§4.2, §5, §11)** -> [`../consistent_hashing/README.md`](../consistent_hashing/README.md)
- **Transactional exactly-once writes and the outbox pattern for crossing the Kafka boundary (§4.5, §11)** -> [`../distributed_transactions/README.md`](../distributed_transactions/README.md)
- **Backpressure, retries, and dead-letter handling for slow or failing consumers (§4.6, §9)** -> [`../resilience_patterns/README.md`](../resilience_patterns/README.md)
- **Horizontal scaling principles (partitioning, replication) underlying §2 and §10's capacity model** -> [`../scalability/README.md`](../scalability/README.md)
- **Lag and ISR metrics as RED-method observability signals, alerting thresholds in §8** -> [`../observability/README.md`](../observability/README.md)
- **Event-sourcing use of a compacted/append-only log as the system of record (§4.1, §5)** -> [`../event_sourcing_cqrs/README.md`](../event_sourcing_cqrs/README.md)
