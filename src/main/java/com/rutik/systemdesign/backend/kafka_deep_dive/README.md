# Kafka Deep Dive

---

## 1. Concept Overview

Apache Kafka is a distributed, durable, high-throughput event streaming platform. It was originally built at LinkedIn to handle activity streams and operational metrics, open-sourced in 2011, and is now the de facto backbone for event-driven architectures at scale.

Kafka's fundamental design choices differ radically from traditional message brokers: the broker is "dumb" (it does not route or transform messages), the consumer is "smart" (it tracks its own position in the log), messages are immutable and retained on disk for configurable periods, and consumers can replay the stream from any point. These choices make Kafka ideal for event streaming, event sourcing, CQRS projections, audit logs, and stream processing.

As of Kafka 3.x, ZooKeeper has been replaced by KRaft (Kafka Raft Metadata) mode, eliminating the external dependency and simplifying operations. KRaft became production-ready in Kafka 3.3 and is the default in Kafka 3.7+.

---

## 2. Intuition

One-line analogy: Kafka is a commit log — an infinitely extensible journal of facts that anyone can read, at any position, at any time, without the journal caring who you are.

Mental model: Imagine a distributed append-only logbook. Every writer appends entries at the end. Every reader has a bookmark (offset) indicating the last entry they read. Readers advance their bookmark at their own pace. The logbook retains entries for a configurable period. Multiple readers can have different bookmarks — one might be reading today's entries, another re-reading entries from last week for a reprocessing job.

Why it matters: Kafka decouples producers and consumers in time, space, and scale. A producer publishing 1 million events per second does not need to wait for any consumer. A consumer that was offline for 6 hours can replay from exactly where it left off. A new service can be added tomorrow and process the event history back to any point.

Key insight: Kafka's durability and replay capability transform it from a message queue into a system of record for event streams. The log is the database.

---

## 3. Core Principles

**The log is the source of truth.** Kafka's core abstraction is the append-only, immutable log. Topics are divided into partitions, each a separate ordered log. Once written, records are never modified.

**Consumers pull, brokers do not push.** Consumers control the rate of consumption via the poll loop. This prevents broker-side back-pressure complexity and allows consumers to slow down, batch, or pause without signaling the broker.

**Partitioning enables parallelism.** A topic with P partitions can be consumed in parallel by up to P consumers within the same consumer group. One consumer per partition is the maximum parallelism.

**Replication provides durability.** Each partition has a leader and zero or more followers. `replication.factor=3` means the partition data is stored on 3 brokers. The ISR (In-Sync Replicas) set tracks which followers are fully caught up.

**Offsets are consumer-side state.** Kafka stores committed offsets in the `__consumer_offsets` internal topic. Consumers commit their progress; the broker does not track what each consumer has read.

**Exactly-once semantics require coordination.** At-most-once: commit before processing (risk loss). At-least-once: commit after processing (risk duplicates). Exactly-once: requires idempotent producer + transactional API + `isolation.level=read_committed` on consumers.

---

## 4. Types / Architectures / Strategies

### Deployment Modes

**KRaft Mode (Kafka 3.3+ production, Kafka 3.7+ default)**
- Internal Raft consensus replaces ZooKeeper.
- Controller nodes (dedicated or combined with brokers) manage cluster metadata via a `__cluster_metadata` topic.
- Eliminates ZooKeeper as an external dependency.
- Faster controller failover (sub-second vs seconds with ZooKeeper).
- Supports clusters up to millions of partitions (ZooKeeper was a bottleneck at ~200k partitions).

**Classic Mode (ZooKeeper-based, pre-3.x)**
- ZooKeeper manages cluster metadata, controller election, topic configs.
- Still supported but deprecated. Migration to KRaft is provided via `kafka-storage.sh` migration tool.

### Topic Retention Strategies

**Delete policy (default)**
- Records are deleted after `retention.ms` (default: 7 days) or when the log reaches `retention.bytes`.
- Suitable for event streams where old data is no longer relevant.

**Compact policy (log compaction)**
- Kafka retains the latest value for each key indefinitely.
- Records with null values (tombstones) delete the key entirely.
- The compacted topic always contains the latest state for every key.
- Suitable for KTable in Kafka Streams, changelog topics, configuration stores.
- Compaction runs asynchronously — recent records are always available even during compaction.

### Producer Delivery Semantics

**acks=0** — fire and forget. Producer does not wait for any broker acknowledgement. Maximum throughput, zero durability. Suitable only for metrics where occasional loss is acceptable.

**acks=1** — leader acknowledges. The partition leader writes to its local log and responds. Followers may not have replicated before the leader fails. Risk: message loss on leader failure before replication.

**acks=all (or acks=-1)** — all in-sync replicas must acknowledge. Combined with `min.insync.replicas=2` (recommended), the producer waits until at least 2 replicas have written the record. Zero message loss under normal conditions.

### Consumer Group Rebalancing Strategies

**Eager rebalancing (default before Kafka 2.4)**
- All consumers stop consuming (revoke all partitions).
- Coordinator reassigns all partitions.
- All consumers resume.
- Downside: full stop-the-world pause during rebalance. At high consumer counts, this can take 30+ seconds.

**Cooperative-sticky rebalancing (Kafka 2.4+, recommended)**
- Only partitions that need to move are revoked.
- Other consumers continue processing uninterrupted.
- Implemented via `CooperativeStickyAssignor`.
- Dramatically reduces rebalance impact in large consumer groups.

---

## 5. Architecture Diagrams

### Kafka Cluster Architecture (KRaft Mode)

```
                         KRaft Controller Quorum
                    ┌──────────────────────────────┐
                    │  Controller-1  Controller-2  │
                    │  Controller-3 (Raft leader)  │
                    │  Metadata log: __cluster_metadata
                    └──────────────────────────────┘
                                  │ metadata replication
          ┌───────────────────────┼───────────────────────┐
          ▼                       ▼                       ▼
   ┌────────────┐          ┌────────────┐          ┌────────────┐
   │  Broker-1  │          │  Broker-2  │          │  Broker-3  │
   │            │          │            │          │            │
   │ Topic-A P0 │ ◄──rep── │ Topic-A P0 │          │ Topic-A P0 │
   │  (leader)  │          │ (follower) │          │ (follower) │
   │            │          │            │          │            │
   │ Topic-A P1 │          │ Topic-A P1 │ ◄──rep── │ Topic-A P1 │
   │ (follower) │          │  (leader)  │          │ (follower) │
   └────────────┘          └────────────┘          └────────────┘
```

### Topic, Partition, Segment Structure

```
Topic: orders.placed  (4 partitions, replication-factor=3)

Partition 0 (Leader: Broker-1)
  Segment 0: offsets 0-999        [CLOSED, on disk]
  Segment 1: offsets 1000-1999    [CLOSED, on disk]
  Segment 2: offsets 2000-...     [ACTIVE, writes here]
  └─ Index file (offset→position mapping for O(1) seek)

Partition 1 (Leader: Broker-2)  ...
Partition 2 (Leader: Broker-3)  ...
Partition 3 (Leader: Broker-1)  ...
```

### Producer → Topic → Consumer Group Flow

```
                    ┌─────────────────────────────────────┐
                    │         Producer                    │
                    │  batch: 64KB, linger: 5ms           │
                    │  acks=all, idempotent=true           │
                    └─────────────────────────────────────┘
                         │ partition(key) → hash(key) % P
          ┌──────────────┼──────────────┬───────────────┐
          ▼              ▼              ▼               ▼
   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
   │Partition 0 │ │Partition 1 │ │Partition 2 │ │Partition 3 │
   │  offset→  │ │  offset→  │ │  offset→  │ │  offset→  │
   │  0,1,2,.. │ │  0,1,2,.. │ │  0,1,2,.. │ │  0,1,2,.. │
   └────────────┘ └────────────┘ └────────────┘ └────────────┘
          │              │              │               │
          ▼              ▼              ▼               ▼
   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
   │Consumer A  │ │Consumer B  │ │Consumer C  │ │Consumer D  │
   │(Group: G1) │ │(Group: G1) │ │(Group: G1) │ │(Group: G1) │
   └────────────┘ └────────────┘ └────────────┘ └────────────┘
   1 consumer per partition = maximum parallelism within a group

   Group G2 (analytics) reads ALL partitions independently:
   ┌───────────────────────────────────────────────────────┐
   │ Consumer X — reads P0, P1, P2, P3 (1 consumer, G2)   │
   └───────────────────────────────────────────────────────┘
```

### Schema Registry Integration

```
Producer                   Schema Registry              Kafka Broker
   │                             │                           │
   │── register schema ─────────►│                           │
   │◄─ schema ID: 42 ────────────│                           │
   │                             │                           │
   │── [magic byte][schema_id=42][avro_bytes] ──────────────►│
   │                             │                 store in partition
   │                             │                           │
Consumer                         │                           │
   │◄────────────────────────────────── poll records ────────│
   │── fetch schema ID=42 ───────►│                           │
   │◄─ Avro schema ──────────────│                           │
   │── deserialize bytes ────────────────────────────────────│
```

### Exactly-Once Semantics (EOS) Architecture

```
Producer (idempotent + transactional)
  enable.idempotence=true
  transaction.id=producer-instance-1
        │
        │ BEGIN TRANSACTION
        │ send to partition-0 (seq=0)
        │ send to partition-1 (seq=0)
        │ COMMIT TRANSACTION (atomic across partitions)
        ▼
Kafka Broker
  Transaction Coordinator manages two-phase commit
  Writes transaction marker (COMMITTED) to all affected partitions
        │
        ▼
Consumer (read_committed isolation)
  isolation.level=read_committed
  Skips uncommitted records and aborted transactions
  Only sees committed data → exactly-once visible effect
```

---

## 6. How It Works — Detailed Mechanics

### Producer Configuration

```java
Properties props = new Properties();
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);

// Delivery guarantee
props.put(ProducerConfig.ACKS_CONFIG, "all");                    // ISR must confirm
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);       // eliminates duplicates per session
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // safe with idempotence

// Throughput tuning
props.put(ProducerConfig.LINGER_MS_CONFIG, 5);                   // wait 5ms to fill batch
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);              // 64 KB batch size
props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);        // 32 MB send buffer
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");     // snappy: good ratio, low CPU

// Schema Registry
props.put("schema.registry.url", "http://schema-registry:8081");
props.put("specific.avro.reader", true);

// Retries
props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);     // retry indefinitely
props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);    // 2 min total timeout
```

### Transactional Producer (Exactly-Once)

```java
props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-producer-" + instanceId);
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

KafkaProducer<String, OrderEvent> producer = new KafkaProducer<>(props);
producer.initTransactions();

try {
    producer.beginTransaction();

    // Send to multiple partitions atomically
    producer.send(new ProducerRecord<>("orders.placed", order.getId(), orderPlacedEvent));
    producer.send(new ProducerRecord<>("audit.log", order.getId(), auditEvent));

    producer.commitTransaction();
} catch (ProducerFencedException | OutOfOrderSequenceException e) {
    // Fatal: close and restart producer
    producer.close();
} catch (KafkaException e) {
    producer.abortTransaction();  // clean rollback
}
```

### Consumer Configuration

```java
Properties props = new Properties();
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-fulfillment-service");
props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);

// Cooperative-sticky rebalancing — reduces stop-the-world pauses
props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
    CooperativeStickyAssignor.class.getName());

// Polling
props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);          // 500 records per poll
props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);   // 5 min max between polls

// Exactly-Once: read only committed data
props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

// Reset: where to start if no committed offset exists
props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

// NEVER auto-commit in production for at-least-once guarantees
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

props.put("schema.registry.url", "http://schema-registry:8081");
```

### Poll Loop with Manual Commit

```java
KafkaConsumer<String, OrderPlacedEvent> consumer = new KafkaConsumer<>(props);
consumer.subscribe(List.of("orders.placed"));

try {
    while (running) {
        ConsumerRecords<String, OrderPlacedEvent> records =
            consumer.poll(Duration.ofMillis(100));

        for (ConsumerRecord<String, OrderPlacedEvent> record : records) {
            try {
                processOrder(record.value());
            } catch (NonRetryableException e) {
                // send to DLQ, continue — do not let one bad message block the partition
                sendToDlq(record, e);
            }
        }

        // Commit only after all records in the batch are processed
        // commitSync blocks until broker confirms — guarantees at-least-once
        consumer.commitSync();

        // Alternative: commitAsync for higher throughput, with retry callback
        // consumer.commitAsync((offsets, exception) -> {
        //     if (exception != null) log.error("Commit failed", exception);
        // });
    }
} finally {
    // commitSync on close to flush final offsets before leaving group
    consumer.commitSync();
    consumer.close();
}
```

### Kafka Streams: KStream and KTable

```java
StreamsBuilder builder = new StreamsBuilder();

// KStream: unbounded stream of events (one record per event occurrence)
KStream<String, OrderPlacedEvent> orders =
    builder.stream("orders.placed", Consumed.with(Serdes.String(), orderSerde));

// KTable: changelog stream, latest value per key (like a database table)
KTable<String, CustomerProfile> customers =
    builder.table("customers.profiles", Consumed.with(Serdes.String(), customerSerde));

// Stateful join: enrich order stream with customer profile
KStream<String, EnrichedOrder> enrichedOrders = orders.join(
    customers,
    (order, customer) -> new EnrichedOrder(order, customer),
    Joined.with(Serdes.String(), orderSerde, customerSerde)
);

// Windowed aggregation: count orders per customer per hour
KTable<Windowed<String>, Long> ordersPerHour = orders
    .groupByKey()
    .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofHours(1)))
    .count(Materialized.as("orders-per-hour-store"));  // backed by RocksDB

// Output enriched orders to downstream topic
enrichedOrders.to("orders.enriched", Produced.with(Serdes.String(), enrichedOrderSerde));

KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfig);
streams.start();
```

### GlobalKTable vs KTable

```java
// KTable: each Streams instance holds only the partitions assigned to it
// Suitable for large tables, co-partitioned joins
KTable<String, InventoryItem> inventory =
    builder.table("inventory.items");

// GlobalKTable: ALL partitions replicated to EVERY Streams instance
// Enables joins without co-partitioning requirement
// Suitable for small-to-medium reference data tables (country codes, configs)
// Warning: replicated to every instance — do not use for large tables
GlobalKTable<String, ProductCatalog> catalog =
    builder.globalTable("product.catalog");

// Join KStream with GlobalKTable — no co-partitioning required
KStream<String, EnrichedOrder> enriched = orders.join(
    catalog,
    (key, order) -> order.getProductId(),  // key extractor for GlobalKTable lookup
    (order, product) -> new EnrichedOrder(order, product)
);
```

### Log Compaction

```java
// Topic configured for compaction (retain latest value per key)
// AdminClient configuration:
Map<String, String> configs = new HashMap<>();
configs.put("cleanup.policy", "compact");
configs.put("min.cleanable.dirty.ratio", "0.1");   // compact when 10% is dirty
configs.put("segment.ms", "3600000");               // 1 hour segment roll

NewTopic compactedTopic = new NewTopic("product.prices", 12, (short) 3)
    .configs(configs);

// Tombstone: null value deletes the key from the compacted log
producer.send(new ProducerRecord<>("product.prices", "product-123", null));
```

### Consumer Lag Monitoring

```bash
# Check consumer group lag via CLI
kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --describe \
  --group order-fulfillment-service

# Output:
# GROUP                      TOPIC          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# order-fulfillment-service  orders.placed  0          10000           10050           50
# order-fulfillment-service  orders.placed  1          9800            10050           250
# order-fulfillment-service  orders.placed  2          10001           10050           49
# order-fulfillment-service  orders.placed  3          9950            10050           100
```

---

## 7. Real-World Examples

**LinkedIn — Kafka's origin**: LinkedIn built Kafka to handle activity stream data (page views, likes, searches) from 400+ million users. A single cluster processes trillions of messages per day. The unified log architecture replaced point-to-point pipelines between dozens of data systems.

**Uber — Real-time surge pricing**: Uber's surge pricing engine consumes GPS events from millions of active drivers via Kafka at hundreds of thousands of events per second. Kafka Streams aggregates events in sliding time windows to compute supply/demand ratios per geohash cell in near real-time.

**Netflix — Change Data Capture to Kafka**: Netflix uses Debezium to capture MySQL binary log changes and publish them to Kafka topics. Downstream services subscribe to these CDC events to keep read caches, Elasticsearch indexes, and analytics pipelines in sync with the database of record.

**Robinhood — Exactly-Once Financial Events**: Robinhood uses Kafka with EOS (idempotent producers + transactions + `read_committed`) for trade event processing where duplicate debits or credits are catastrophic. The transactional producer guarantees that a trade event is written to exactly one offset in exactly one partition with no duplicates.

**Confluent Schema Registry in Production**: At scale, a breaking schema change (removing a required field from an Avro schema) brought down a downstream consumer service at a major bank during trading hours. After recovery, the team enabled BACKWARD compatibility enforcement in Schema Registry. The CI pipeline now rejects non-compatible schema PRs before they reach production.

---

## 8. Tradeoffs

| Configuration | Latency | Throughput | Durability | Use Case |
|--------------|---------|------------|------------|----------|
| acks=0 | Lowest | Highest | None | Metrics, logs (loss-tolerant) |
| acks=1 | Low | High | Leader only | Semi-important events |
| acks=all + min.insync=2 | Higher | Medium | High | Business events, financial |

| Rebalancing Strategy | Stop-the-World | Complexity | Kafka Version |
|---------------------|---------------|------------|---------------|
| Eager (Range/RoundRobin) | Full pause | Low | < 2.4 |
| Cooperative-Sticky | Partial — only moved partitions | Medium | 2.4+ |

| Compression | CPU Cost | Ratio | Latency | Best For |
|------------|---------|-------|---------|----------|
| none | None | 1x | Lowest | Dev/test |
| snappy | Low | ~2x | Low | Throughput-optimized pipelines |
| lz4 | Low | ~2x | Very low | Latency-sensitive pipelines |
| gzip | High | ~4x | Higher | Storage-constrained, cold data |
| zstd | Medium | ~4x | Low | Best balance: use in production |

| Mode | ZooKeeper | Max Partitions | Failover Speed | Production Status |
|------|-----------|----------------|----------------|-------------------|
| Classic (ZK) | Required | ~200k | Seconds | Deprecated in 3.x |
| KRaft | None | Millions | Sub-second | Default in 3.7+ |

---

## 9. When to Use / When NOT to Use

**Use Kafka when:**
- You need durable, replayable event streams.
- Multiple independent consumers must process the same events (fan-out).
- Throughput requirements exceed millions of messages per day.
- You need event sourcing or CQRS projection rebuilding (replay from offset 0).
- You need stream processing with Kafka Streams or ksqlDB.
- You need to decouple producer and consumer rate (back-pressure buffering).

**Do NOT use Kafka when:**
- You need complex routing logic (RabbitMQ with exchange types is better).
- You need request/reply semantics with short timeouts (use gRPC or REST).
- Your team is small and operational overhead of a Kafka cluster is not justified (use Amazon SQS or RabbitMQ).
- Messages must be delivered to specific consumers based on content-based routing (RabbitMQ headers exchange or SNS filter policies are simpler).
- You need sub-millisecond latency (Kafka's minimum latency is ~2–5ms under optimal conditions).

**Use log compaction when:**
- The topic represents the latest state of a key (product prices, user preferences).
- Consumers need to rebuild current state on startup without processing full history.

**Use delete policy when:**
- Events are time-bounded and older events are irrelevant after the retention window.
- Topic represents a stream of discrete occurrences, not state.

---

## 10. Common Pitfalls

**Pitfall 1 — Consumer group imbalance causing hot partitions.**
A team deployed 8 consumer instances but their topic had only 4 partitions. Four consumers were idle, contributing nothing. The other four each processed one partition. Throughput did not scale with additional consumers. Fix: the number of partitions is the maximum parallelism for a consumer group. Partition count can only be increased (not decreased) without repartitioning. Plan partition count based on target peak throughput divided by per-consumer throughput capacity.

**Pitfall 2 — Auto-commit with processing errors causing data loss.**
A team used `enable.auto.commit=true` (the default). When a consumer processed 500 records and one threw a NullPointerException, the exception was caught and logged, but the auto-commit timer had already committed the offset. The failed record was silently lost. Fix: always disable auto-commit in business event consumers. Use `commitSync()` only after all records in the batch are confirmed processed.

**Pitfall 3 — max.poll.interval.ms exceeded causing rebalances.**
A consumer batch included a record that triggered a slow external HTTP call (5 seconds per record). With 500 records per poll, one poll took 2500 seconds. The default `max.poll.interval.ms=300000` (5 minutes) was exceeded. The broker assumed the consumer was dead, triggered a rebalance, reassigned the partition to another instance, which re-processed the same records. Fix: reduce `max.poll.records` to a size that can be processed within `max.poll.interval.ms`. For slow processing, use asynchronous processing with manual flow control, or increase the interval.

**Pitfall 4 — Using string keys with high cardinality causing partition skew.**
A team used the full UUID of the user as the partition key. With 12 partitions and 10,000 active users, the hash distribution should be even. However, 90% of their traffic came from 50 enterprise accounts. All enterprise account events hashed to 3 partitions, creating a severe skew. Fix: choose partition keys that distribute load evenly — consider composite keys or a tier-based routing key rather than raw entity IDs for power-law distributed workloads.

**Pitfall 5 — Schema evolution without compatibility check breaking consumers.**
A producer team changed an Avro field type from `string` to `long` (the field held a numeric ID). They registered the new schema without checking compatibility. Every consumer service was on a fixed schema version and began throwing `SchemaParseException` immediately. The incident lasted 47 minutes. Fix: enforce Schema Registry compatibility mode (BACKWARD or FULL) and add schema compatibility verification as a mandatory CI check before any schema change is merged.

**Pitfall 6 — Not configuring min.insync.replicas with acks=all.**
A team set `acks=all` believing they had full durability. However, `min.insync.replicas` defaulted to 1. With a 3-broker cluster, `acks=all` with `min.insync.replicas=1` means only the leader needs to acknowledge — identical to `acks=1`. When the leader failed before replication, messages were lost. Fix: set `min.insync.replicas=2` for a 3-replica topic. This ensures data is on at least 2 brokers before the producer receives an acknowledgement.

**Pitfall 7 — Ignoring consumer lag until it cascades.**
A streaming pipeline had consistent 0-lag during normal operations. During a Black Friday traffic spike, producers sent 10x normal volume. Consumers could not keep up and lag grew to 50 million records over 6 hours. Downstream systems dependent on near-real-time data (inventory, pricing) were serving 6-hour-old state. Fix: set lag-based autoscaling (HPA on consumer group lag in Kubernetes via KEDA) with alerting thresholds at 10k records lag. Treat consumer lag as a primary SLA metric, not an afterthought.

---

## 11. Technologies and Tools

**Kafka Ecosystem**
- Apache Kafka — core broker (KRaft mode in 3.7+).
- Kafka Streams — embedded Java library for stateful stream processing. No separate cluster required.
- ksqlDB — SQL-like query engine for Kafka streams. Suitable for simpler aggregations without full Java code.
- Kafka Connect — scalable framework for source and sink connectors. 200+ connectors available (JDBC, Elasticsearch, S3, Debezium CDC).
- Kafka MirrorMaker 2 — cross-cluster replication for disaster recovery and geo-replication.

**Schema Management**
- Confluent Schema Registry — supports Avro, Protobuf, JSON Schema. REST API for schema management. Compatibility modes: NONE, BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE, FULL, FULL_TRANSITIVE.
- AWS Glue Schema Registry — managed equivalent for AWS deployments. Integrates with MSK (Managed Streaming for Kafka).

**Managed Kafka**
- Confluent Cloud — fully managed Kafka with enterprise features (RBAC, audit logs, cluster linking).
- Amazon MSK — managed Kafka on AWS. MSK Serverless for unpredictable workloads.
- Aiven for Kafka — managed Kafka across AWS, GCP, Azure.

**Monitoring**
- Confluent Control Center — commercial UI for consumer lag, broker health, Schema Registry.
- Kafdrop — open-source web UI for topic/message inspection.
- Burrow (LinkedIn) — consumer lag monitoring with rule-based alerting.
- KEDA (Kubernetes) — event-driven autoscaling based on Kafka consumer group lag.
- JMX metrics exposed by brokers — integrate with Prometheus via JMX Exporter.

**Spring Integration**
- Spring Kafka (`spring-kafka`) — `@KafkaListener`, `KafkaTemplate`, `KafkaTransactionManager`.
- Spring Cloud Stream — binder abstraction for Kafka and RabbitMQ with `@StreamListener` (deprecated in favor of functional model with `Consumer<T>` beans).

---

## 12. Interview Questions with Answers

**Q: What is the role of a partition in Kafka and how does it enable parallelism?**
A partition is the fundamental unit of parallelism and ordering in Kafka. Each topic is split into N partitions, each an independent ordered log stored on a single broker (the partition leader). Within a consumer group, each partition is consumed by exactly one consumer at a time. Therefore, a topic with 12 partitions can be consumed by at most 12 consumers in parallel within one group. Ordering is guaranteed within a partition but not across partitions. Choose a partition key (e.g., orderId) that maps the records you need ordered together to the same partition.

**Q: What is the ISR (In-Sync Replicas) and how does it relate to acks=all?**
The ISR is the set of replicas that are fully caught up with the partition leader within `replica.lag.time.max.ms` (default 30 seconds). With `acks=all`, the producer waits for all replicas in the ISR to confirm the write. If `min.insync.replicas=2` and the ISR has 3 replicas, all 3 must confirm. If one broker is slow and falls out of the ISR, the producer only waits for the remaining ISR members (as long as ISR size >= min.insync.replicas). If the ISR shrinks below `min.insync.replicas`, the producer receives a `NotEnoughReplicasException`.

**Q: What is the difference between at-most-once, at-least-once, and exactly-once delivery semantics in Kafka?**
At-most-once commits the offset before processing — if processing fails, the message is lost but never duplicated. At-least-once commits after processing — if the consumer crashes after processing but before committing, the message is reprocessed on restart, potentially causing duplicates. Exactly-once is achieved by combining three features: `enable.idempotence=true` on the producer (eliminates duplicates caused by producer retries), `transactional.id` + `producer.beginTransaction()` / `commitTransaction()` for atomic multi-partition writes, and `isolation.level=read_committed` on consumers so they only see committed data. Without all three, you cannot guarantee exactly-once.

**Q: What does enable.idempotence=true do in the producer?**
The idempotent producer assigns a producer ID (PID) and a monotonically increasing sequence number to each message. The broker tracks the last sequence number per (PID, partition). If the producer retries a message (e.g., due to a network timeout), the broker detects the duplicate sequence number and discards the duplicate, returning success to the producer. This eliminates duplicates caused by producer retries within a single producer session. Note: the PID is reassigned on producer restart, so idempotence is per-session only. For cross-session deduplication, use transactional producers or consumer-side idempotency.

**Q: What is log compaction and when would you use it instead of the default delete policy?**
Log compaction retains the latest value for each record key indefinitely, deleting older records with the same key. A null value (tombstone) causes the key to be deleted entirely after the compaction runs. Use it for topics that represent current state rather than event history — for example, a `product.prices` topic where only the latest price matters, or a Kafka Streams changelog topic backing a state store. Use the delete policy when events are time-bounded and older events are irrelevant after a retention period.

**Q: What is the difference between KStream and KTable in Kafka Streams?**
A KStream represents an unbounded stream of events where each record is an independent fact. Multiple records with the same key coexist and are all processed. A KTable represents a changelog stream where each record is an update to a keyed value — only the latest value per key matters, similar to a database table. Internally, a KTable is backed by a state store (RocksDB by default). Use KStream for event processing (every occurrence matters). Use KTable for current-state lookups (latest value per key). A KStream can be aggregated into a KTable.

**Q: What is a GlobalKTable and when should you use it instead of a KTable?**
A GlobalKTable is replicated to every Kafka Streams instance in the application, regardless of which partitions that instance is assigned. This means any instance can join any record against a GlobalKTable without co-partitioning requirements. Use it for small-to-medium reference data (country codes, product catalog with <100k entries) that every instance needs. Never use GlobalKTable for large tables — the full dataset is stored locally on every instance. For large tables with co-partitioned keys, use a regular KTable join.

**Q: Explain the producer batching mechanism and how to tune it.**
The producer accumulates records in an in-memory batch per partition (up to `batch.size` bytes, default 16384 = 16 KB). When the batch is full or `linger.ms` elapses (default 0), the batch is sent. With `linger.ms=0`, each record is sent as soon as possible (low latency, poor batching). With `linger.ms=5`, the producer waits 5ms for additional records to fill the batch (higher latency, better throughput and compression ratio). For throughput-optimized pipelines: set `batch.size=65536` (64 KB), `linger.ms=5–20`, and enable compression. For latency-sensitive pipelines: keep `linger.ms` at 0–1.

**Q: What is cooperative-sticky rebalancing and why is it better than eager rebalancing?**
In eager rebalancing, all consumers in a group revoke all partitions simultaneously, then the coordinator reassigns all partitions. This causes a full stop-the-world pause — no consumer processes any message during the rebalance, which can take 10–30+ seconds in large groups. In cooperative-sticky rebalancing (`CooperativeStickyAssignor`), only the partitions that need to move are revoked, and only the affected consumers pause briefly. Unaffected consumers continue processing uninterrupted. The rebalance runs in multiple rounds. This dramatically reduces the impact of rebalancing caused by rolling deployments or scaling events.

**Q: What is the Schema Registry and what compatibility modes does it support?**
The Schema Registry is a centralized service that stores and enforces schemas for Kafka messages. Producers register a schema and receive a numeric schema ID; the ID and serialized bytes are published to Kafka. Consumers fetch the schema by ID and deserialize. Compatibility modes: BACKWARD — new schema can read data written with old schema (safe: add optional fields with defaults); FORWARD — old schema can read data written with new schema (safe: only add fields that old consumers will ignore); FULL — both backward and forward; BACKWARD_TRANSITIVE / FORWARD_TRANSITIVE / FULL_TRANSITIVE — check against all historical versions, not just the latest. Use FULL_TRANSITIVE for the strongest guarantee.

**Q: How does Kafka handle message ordering guarantees?**
Kafka guarantees order within a single partition. Records with the same partition key always land in the same partition (hash(key) % numPartitions) and are consumed in order by the assigned consumer. There is no ordering guarantee across partitions. To maintain order for an entity (e.g., all events for order-123), always use the entity ID as the partition key. With `enable.idempotence=true`, setting `max.in.flight.requests.per.connection=5` (up from 1) is safe because the idempotent producer reorders retried batches correctly using sequence numbers.

**Q: What is the purpose of the transaction.id configuration in the producer?**
The `transaction.id` is a static, application-assigned identifier that enables the broker to fence zombie producers. If a producer instance crashes and a new instance starts with the same `transaction.id`, the broker increments the producer epoch and rejects writes from the old instance (the zombie). This prevents two producer instances from writing to the same transactional stream simultaneously, which would break the exactly-once guarantee. The `transaction.id` must be unique per partition subset the producer writes to and stable across restarts.

**Q: How would you implement a consumer that processes messages exactly once, end-to-end?**
You need: producer-side EOS (`enable.idempotence=true`, `transactional.id`, `acks=all`) to guarantee the event is written exactly once to Kafka. Consumer-side `isolation.level=read_committed` so the consumer only reads committed transactional records. If the consumer writes results to Kafka (Kafka-to-Kafka), use consumer-producer transactions: `consumer.poll()`, process, `producer.beginTransaction()`, produce result, send offsets with `producer.sendOffsetsToTransaction(offsets, groupMetadata)`, `producer.commitTransaction()`. This atomically commits both the result and the offset. If the consumer writes to an external database, use idempotent upserts keyed on the Kafka record's offset+partition as the idempotency key.

**Q: What metrics should you monitor in a production Kafka deployment?**
Producer metrics: `record-error-rate` (should be 0), `record-send-rate`, `request-latency-avg`. Consumer metrics: consumer group lag per partition (most critical — alert at 10k+ records), `fetch-rate`, `commit-rate`. Broker metrics: `UnderReplicatedPartitions` (should be 0 — indicates ISR degradation), `ActiveControllerCount` (should be 1), `OfflinePartitionsCount` (should be 0), disk utilization, network throughput, `RequestHandlerAvgIdlePercent` (below 30% indicates broker is overloaded). Topic metrics: message rate per partition, bytes in/out per broker.

**Q: What is the difference between consumer group rebalancing and partition reassignment?**
Consumer group rebalancing is a runtime event triggered when a consumer joins or leaves a group, or when partition count changes. It redistributes partition assignments among the live consumers in the group without moving data. Partition reassignment (via `kafka-reassign-partitions.sh` or Admin API) is an administrative operation that moves partition replicas between brokers — it physically copies partition data to new brokers. Partition reassignment is used for broker decommissioning, rack-aware rebalancing, or restoring replication factor after broker failure.

**Q: How does KRaft mode change Kafka's architecture compared to ZooKeeper-based Kafka?**
In ZooKeeper-based Kafka, ZooKeeper manages controller election, stores topic metadata, broker registrations, and consumer group offsets (in older versions). The active controller is a single broker elected via ZooKeeper. In KRaft mode, a subset of brokers designated as controllers form a Raft quorum. The active controller is elected via Raft consensus. All cluster metadata is stored in an internal `__cluster_metadata` topic replicated via the Raft log. Eliminating ZooKeeper removes an external operational dependency, reduces the number of processes to manage, improves controller failover speed from seconds to sub-seconds, and removes the ~200k partition scalability limit that ZooKeeper imposed.

**Q: What is the significance of the linger.ms and batch.size settings together?**
These two settings jointly control when the producer sends a batch. `batch.size` sets the maximum size of a batch in bytes — the batch is sent immediately when full. `linger.ms` sets the maximum time the producer waits for the batch to fill before sending regardless of size. They work together: with `batch.size=64KB` and `linger.ms=5`, the producer sends when either 64KB is accumulated OR 5ms elapses, whichever comes first. At high throughput, batches fill quickly (batch.size dominates — near-zero extra latency). At low throughput, linger.ms governs (adds up to 5ms latency but groups more records together for compression efficiency). Setting both `linger.ms=0` and a large `batch.size` is counterproductive — batches will rarely fill.

**Q: How do you monitor and alert on consumer lag in production?**
Consumer lag = Log End Offset - Consumer Committed Offset per partition. Monitor it via JMX (`kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*,attribute=records-lag-max`), Burrow (LinkedIn's consumer lag monitor), or by querying the Kafka Admin API. Export to Prometheus via the Kafka JMX Exporter. Set Grafana alerts: warn at 10,000 records lag, critical at 100,000. For Kubernetes deployments, use KEDA (Kubernetes Event-Driven Autoscaling) with the Kafka scaler to automatically scale consumer pod count based on lag. Treat lag as a latency SLA — if your SLA is 30-second processing freshness, 300,000 records at 10,000 records/sec processing speed means 30 seconds of lag before SLA breach.

---

## 13. Best Practices

- **Always set acks=all and min.insync.replicas=2 for business events**: the default `acks=1` and `min.insync.replicas=1` provides no real durability guarantee under broker failure.

- **Use cooperative-sticky rebalancing in all new consumer deployments**: add `CooperativeStickyAssignor` to `partition.assignment.strategy`. The performance improvement during rolling deployments is immediate and significant.

- **Set transaction.id to a stable, unique identifier per producer instance**: for Kubernetes deployments, use a combination of the pod name and a stable hash. This enables the broker to fence zombie producers on restart.

- **Never use auto.offset.reset=latest in production for business-critical consumers**: if your consumer group is new or has lost its committed offsets, `latest` silently drops all messages produced while the consumer was down. Use `earliest` and implement idempotent processing instead.

- **Enforce Schema Registry compatibility in CI**: use the Confluent Maven plugin or a REST API check in your PR pipeline to verify schema compatibility before any schema change is merged. A compatibility failure in CI is a 10-minute fix; in production it is a 30–60-minute incident.

- **Size partitions based on target throughput, not current throughput**: adding partitions later requires repartitioning downstream state stores in Kafka Streams. Plan for 2x–5x current peak throughput when setting initial partition count.

- **Monitor UnderReplicatedPartitions as a P1 alert**: this metric indicates that a partition replica is not in sync. It is the earliest warning of broker degradation and data durability risk before an actual outage.

- **Use compression in production**: enable `snappy` or `zstd` compression. At typical Avro payload sizes (500–2000 bytes), compression ratios of 2–4x reduce broker disk usage, network I/O, and end-to-end latency under load.

- **Design for consumer idempotency even with EOS**: exactly-once semantics in Kafka apply to Kafka-to-Kafka flows. Any external side effects (database writes, HTTP calls, emails) must be idempotent because consumer restarts can re-execute processing logic.

- **Use separate consumer groups for separate concerns**: do not share a consumer group between a real-time processing pipeline and a batch analytics job. They have different throughput, latency, and replay requirements. Sharing a group prevents either from scaling independently.

---

## 14. Case Study

### Real-Time Order Processing Pipeline with Exactly-Once Semantics

**Scenario**: A fintech company processes stock trade orders. Each order triggers inventory reservation, risk assessment, and audit logging. Duplicate processing of a trade (double execution) or missed processing (silent loss) both cause regulatory and financial consequences. The team must achieve exactly-once processing end-to-end.

**Architecture**:

```
Order API         Kafka (EOS)      Risk Svc          Audit DB
    │                  │               │                  │
    │─── OrderPlaced ─►│               │                  │
    │   (transactional │               │                  │
    │    producer)     │               │                  │
    │                  │──── poll ────►│                  │
    │                  │               │── beginTx        │
    │                  │               │── process risk   │
    │                  │               │── produce to     │
    │                  │               │   orders.approved│
    │                  │               │── sendOffsets    │
    │                  │               │── commitTx ─────►│ write audit (idempotent upsert)
    │                  │               │                  │
```

**Producer Configuration**:
```java
props.put(ProducerConfig.ACKS_CONFIG, "all");
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-api-" + podOrdinal);
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
// min.insync.replicas=2 set at topic level
```

**Consumer + Produce Transaction**:
```java
consumer.subscribe(List.of("orders.placed"));
producer.initTransactions();

while (running) {
    ConsumerRecords<String, TradeOrder> records = consumer.poll(Duration.ofMillis(100));
    if (records.isEmpty()) continue;

    producer.beginTransaction();
    try {
        for (ConsumerRecord<String, TradeOrder> record : records) {
            RiskDecision decision = riskEngine.evaluate(record.value());
            ProducerRecord<String, RiskDecision> result =
                new ProducerRecord<>("orders.approved", record.key(), decision);
            producer.send(result);
        }
        // Atomically commit offsets AND produced records
        producer.sendOffsetsToTransaction(
            getOffsets(records),
            consumer.groupMetadata()
        );
        producer.commitTransaction();
    } catch (Exception e) {
        producer.abortTransaction();
        // records will be reprocessed from last committed offset
    }
}
```

**Schema Evolution**:
- `TradeOrder` Avro schema registered with `FULL_TRANSITIVE` compatibility.
- When the team needed to add a `regualtoryRegion` field (initially absent), they added it with a default value of `"UNKNOWN"`. All existing messages deserialized correctly with the default. All new messages carried the field explicitly.
- Schema Registry CI check: any PR touching `.avsc` files triggers a `GET /compatibility/subjects/{subject}/versions/latest` check against the registry staging environment. Non-compatible schemas fail the build.

**Outcomes**:
- Zero duplicate trade executions in 18 months of production operation.
- Consumer lag monitored via KEDA: at peak trading hours (market open), consumer pods autoscale from 4 to 16 based on lag threshold of 5,000 records.
- Rebalancing during rolling deployments with cooperative-sticky: measured partition unavailability reduced from 18 seconds (eager) to under 2 seconds per rebalance round.
