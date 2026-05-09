# Message Queues — High-Level Design

## Table of Contents
1. [Overview and Motivation](#overview-and-motivation)
2. [Core Concepts](#core-concepts)
3. [Message Queue vs Message Broker vs Event Streaming](#message-queue-vs-message-broker-vs-event-streaming)
4. [Delivery Guarantees](#delivery-guarantees)
5. [Messaging Patterns](#messaging-patterns)
6. [Kafka vs RabbitMQ vs SQS](#kafka-vs-rabbitmq-vs-sqs)
7. [Dead Letter Queues](#dead-letter-queues)
8. [Consumer Groups and Partitions](#consumer-groups-and-partitions)
9. [Message Ordering](#message-ordering)
10. [Architecture Diagrams](#architecture-diagrams)
11. [Real-World Use Cases](#real-world-use-cases)
12. [When to Use Message Queues](#when-to-use-message-queues)
13. [Tradeoffs and Considerations](#tradeoffs-and-considerations)
14. [Best Practices](#best-practices)
15. [Case Study: Ride-Matching Pipeline](#case-study-ride-matching-pipeline)
16. [Interview Questions](#interview-questions)

---

## Intuition

> **One-line analogy**: A message queue is like a postal system — the sender drops a letter in the mailbox (doesn't wait for delivery), the postal service guarantees delivery, and the recipient picks it up when ready.

**Mental model**: Synchronous systems are fragile — if Service B is slow or down, Service A waits or fails. A message queue decouples them: A drops a message into the queue and moves on. B processes messages at its own pace. If B is down, messages queue up; when B recovers, it processes the backlog. This enables services to scale independently and tolerates downstream failures gracefully.

**Why it matters**: Message queues are the backbone of asynchronous, event-driven architectures. They enable microservices to communicate without tight coupling, enable fan-out (one message → many consumers), and provide durability (messages survive crashes). Kafka's log-based design enables replaying events — crucial for stream processing and event sourcing.

**Key insight**: The choice between exactly-once, at-least-once, and at-most-once delivery determines your complexity. Exactly-once is very hard to implement; at-least-once with idempotent consumers is the practical standard for most systems.

---

## Overview and Motivation

A **message queue** is a form of asynchronous inter-service communication. A producer places a message on the queue; one or more consumers pick it up and process it independently. The producer does not wait for a response — it fires and continues.

### Why use them?

| Problem (without queues)           | Solution (with queues)                          |
|------------------------------------|-------------------------------------------------|
| Tight coupling between services    | Producer and consumer evolve independently      |
| Downstream slowness causes backpressure | Queue absorbs spikes; consumers drain at their own pace |
| Retry logic scattered in every caller | Queue handles redelivery centrally              |
| Hard to fan out to multiple systems | Pub-Sub: one message, many consumers            |
| Synchronous chains amplify latency | Async processing keeps p99 latency low          |

Message queues are a foundational building block in large distributed systems. They shift the system from **request-response** to **event-driven** architecture, unlocking independent scalability, fault isolation, and temporal decoupling.

---

## Core Concepts

### Producer
The service or application that creates and publishes messages. Producers are only responsible for getting the message onto the queue; they do not know who will consume it or when.

### Consumer
The service that reads and processes messages from a queue or topic. Consumers can be scaled independently of producers.

### Queue
A named buffer that holds messages until they are consumed. In point-to-point queuing, each message is delivered to exactly one consumer.

### Topic
A logical channel used in pub-sub systems. Multiple consumer groups can independently subscribe to the same topic and receive every message.

### Message
The unit of data transmitted. A message typically has:
- **Body**: the payload (JSON, Avro, Protobuf, raw bytes)
- **Headers / Metadata**: routing keys, timestamp, correlation ID, content-type
- **Message ID**: unique identifier to detect duplicates

### Acknowledgment (ACK)
A signal sent by the consumer to the broker after successful processing. Without an ACK the broker may redeliver the message. The ACK is the mechanism that separates "at-most-once" from "at-least-once" delivery.

### Offset
In log-based systems (Kafka), the position of a message within a partition. Consumers track their own offset, allowing replay and independent progress.

### Broker
The server (or cluster) that stores and routes messages between producers and consumers.

---

## Message Queue vs Message Broker vs Event Streaming

These terms are often used interchangeably, but they represent distinct models:

```
Traditional Queue          Message Broker             Event Streaming
------------------         ---------------            ---------------
- Simple FIFO buffer       - Routes messages          - Append-only log
- Messages deleted         - Supports routing         - Messages retained
  after consumption          rules, exchanges           for configurable period
- Point-to-point           - Pub-Sub + P2P            - Replay from any offset
- Examples: SQS,           - Examples:                - Consumers maintain
  ActiveMQ                   RabbitMQ, ActiveMQ         own read position
                                                      - Examples: Kafka,
                                                        Kinesis, Pulsar
```

| Dimension            | Queue (SQS)         | Message Broker (RabbitMQ) | Event Stream (Kafka)  |
|----------------------|---------------------|---------------------------|-----------------------|
| Storage model        | Delete on consume   | Delete on ACK             | Persistent log        |
| Replay messages      | No                  | No                        | Yes                   |
| Consumer model       | Competing consumers | Routing-based             | Consumer groups       |
| Ordering             | FIFO (optional)     | Per-queue                 | Per-partition         |
| Throughput           | Moderate            | Moderate                  | Very high             |
| Primary use case     | Task queues         | Complex routing           | Event sourcing, CDC   |

---

## Delivery Guarantees

### At-Most-Once (Fire and Forget)
The message is sent once. If the consumer crashes before processing, the message is lost. No retries.

- ACK is sent before processing begins (or not used at all).
- Suitable for: metrics, logs where occasional loss is acceptable.

```
Producer --> Broker --> Consumer
                          |
                        [ACK sent immediately on receipt]
                          |
                        [Consumer crashes]
                          |
                        [Message LOST — not redelivered]
```

### At-Least-Once (With Retries)
The broker holds the message until the consumer ACKs after processing. If processing fails or times out, the broker redelivers. The same message may arrive more than once.

- Consumers MUST be idempotent (processing the same message twice produces the same result).
- Most systems default to this.

```
Producer --> Broker --> Consumer (processes)
                          |
                        [Consumer crashes BEFORE ACK]
                          |
               Broker re-enqueues message
                          |
                        Consumer (re-processes) --> ACK --> Broker deletes message
```

### Exactly-Once
Each message is processed exactly once, even in the presence of failures. This is the hardest guarantee to achieve.

Two approaches:
1. **Idempotent producers + transactional consumers**: Kafka 0.11+ supports this within a Kafka cluster.
2. **Application-level deduplication**: consumers store processed message IDs in a database and skip duplicates.

True exactly-once across heterogeneous systems (queue + database) requires distributed transactions or the outbox pattern.

---

## Messaging Patterns

### 1. Point-to-Point (Queue)
One producer, one consumer per message. Used for task distribution — work items are load-balanced across a pool of workers.

```
Producer
   |
   v
[Queue]
   |
   +---> Consumer-1 (gets message A)
   +---> Consumer-2 (gets message B)
   +---> Consumer-3 (gets message C)
```

Use case: order processing, email sending, background jobs.

### 2. Publish-Subscribe (Topic)
One producer, many consumer groups. Every group gets a full copy of every message.

```
Producer
   |
   v
[Topic]
   |
   +---> Consumer Group A (analytics service) -- sees all messages
   +---> Consumer Group B (notification service) -- sees all messages
   +---> Consumer Group C (audit log service) -- sees all messages
```

Use case: event broadcasting, activity feeds, cache invalidation.

### 3. Request-Reply
Producer sends a request with a reply-to queue; consumer processes and sends response back to that queue. Simulates synchronous RPC over async infrastructure.

```
Producer                      Consumer
   |                              |
   |--[Request + replyTo=Q_reply]--> [Request Queue]
   |                              |
   |                          [Processes]
   |                              |
   | <--[Response]-- [Reply Queue Q_reply]
```

Use case: service-to-service calls when you still want decoupling; legacy system integration.

### 4. Fan-Out
One message triggers parallel processing in multiple independent consumers simultaneously. Often implemented with SNS (notification service) → multiple SQS queues.

```
Event Source
     |
     v
  [SNS Topic]
     |
     +---> [SQS Queue 1] --> Lambda (billing)
     +---> [SQS Queue 2] --> Lambda (inventory)
     +---> [SQS Queue 3] --> Lambda (notification)
```

Use case: e-commerce order placement triggering billing, inventory, and notification in parallel.

---

## Kafka vs RabbitMQ vs SQS

### Side-by-Side Comparison

| Feature                   | Apache Kafka                      | RabbitMQ                         | AWS SQS                          |
|---------------------------|-----------------------------------|----------------------------------|----------------------------------|
| **Type**                  | Distributed event log             | Message broker (AMQP)            | Managed queue service            |
| **Storage**               | Persistent log (configurable TTL) | In-memory + optional persistence | Managed (up to 14 days)          |
| **Throughput**            | Millions msg/sec                  | ~50k–100k msg/sec                | Scales automatically (AWS)       |
| **Ordering**              | Per-partition                     | Per-queue                        | FIFO queues only                 |
| **Consumer model**        | Pull (offset-based)               | Push (AMQP)                      | Pull (long polling)              |
| **Replay**                | Yes (seek to offset)              | No                               | No                               |
| **Routing**               | Topic + partition key             | Exchanges, routing keys, bindings| Queue URL-based                  |
| **Dead letter**           | Via config (DLT)                  | Built-in DLX                     | Built-in DLQ                     |
| **Protocol**              | Custom binary (TCP)               | AMQP 0-9-1, STOMP, MQTT          | HTTPS/REST + SQS API             |
| **Ops overhead**          | High (Zookeeper/KRaft, brokers)   | Medium                           | None (fully managed)             |
| **Exactly-once**          | Yes (Kafka transactions)          | No                               | No (at-least-once)               |
| **Best for**              | High-throughput event streaming   | Complex routing, RPC, tasks      | Simple decoupling on AWS         |

### Kafka Deep Dive

Kafka stores messages in an **append-only log** partitioned across brokers. Key concepts:

- **Topic**: a logical stream of messages, split into partitions.
- **Partition**: an ordered, immutable sequence of records. The unit of parallelism.
- **Offset**: each record's position in a partition. Consumers commit offsets to track progress.
- **Consumer Group**: a set of consumers sharing a group ID. Each partition is consumed by exactly one member of the group — enabling parallel processing while preserving order per partition.
- **Retention**: messages are kept for a configured period (e.g., 7 days) regardless of consumption.
- **Log compaction**: optional mode that retains only the latest value per key — useful for changelog topics.

### RabbitMQ Deep Dive

RabbitMQ implements the **AMQP** protocol with a flexible routing model:

- **Exchange types**:
  - `direct`: routes by exact routing key match.
  - `topic`: routes by wildcard pattern (`*.error`, `orders.#`).
  - `fanout`: broadcasts to all bound queues.
  - `headers`: routes by message header attributes.
- **Bindings**: rules connecting an exchange to a queue.
- **Dead Letter Exchange (DLX)**: messages that cannot be delivered or are rejected are forwarded to a DLX for inspection.
- **Priority queues**: consumers can assign priority to messages within a queue.
- **Acknowledgment modes**: auto-ack (at-most-once) or manual-ack (at-least-once).

### SQS Deep Dive

Amazon SQS is a fully managed queuing service with two variants:

- **Standard Queue**: at-least-once, best-effort ordering, near-unlimited throughput.
- **FIFO Queue**: exactly-once within a 5-minute deduplication window, 300 TPS (3000 with batching), strict ordering per message group.
- **Visibility Timeout**: when a consumer receives a message, it becomes invisible to others for a configured period. If not deleted in time, the message reappears for reprocessing.
- **Long Polling**: consumers wait up to 20 seconds for messages, reducing empty responses and cost.

---

## Dead Letter Queues

A **Dead Letter Queue (DLQ)** (or Dead Letter Topic in Kafka) is a special queue that receives messages that could not be successfully processed after a maximum number of retries.

### When a message ends up in the DLQ:
- Processing failed repeatedly (max retries exceeded).
- Message TTL (time-to-live) expired before processing.
- Consumer explicitly rejected the message (NACK without requeue in RabbitMQ).
- Message format was invalid or unparseable.

### DLQ Architecture:

```
[Main Queue]
     |
     | (delivery attempt 1)
     v
Consumer -- FAILS --> Retry attempt 1
                           |
                      FAILS --> Retry attempt 2
                                    |
                               FAILS --> Retry attempt 3
                                              |
                                         MAX RETRIES EXCEEDED
                                              |
                                              v
                                       [Dead Letter Queue]
                                              |
                              +---------------+---------------+
                              |               |               |
                           Alerting      Manual review    Reprocessing
                           (PagerDuty)   (dashboard)       (after fix)
```

### Best practices for DLQ handling:
1. **Always configure a DLQ** on production queues — never silently drop failed messages.
2. **Alert on DLQ depth** — even a single message in the DLQ signals a bug.
3. **Include metadata** in the message: original queue, failure reason, timestamp of first attempt.
4. **Build a reprocessing tool** to move messages from DLQ back to the main queue after fixing the bug.
5. **Inspect DLQ messages** before reprocessing — they may represent poisonous or malformed data.

---

## Consumer Groups and Partitions

This is Kafka-specific but the concept applies broadly to any partitioned system.

### The fundamental rule:
- Within a consumer group, each **partition** is assigned to exactly **one consumer**.
- One consumer can handle multiple partitions.
- You cannot have more active consumers than partitions (extras sit idle).

```
Topic: orders (4 partitions)

Consumer Group: order-processor (3 consumers)

Partition 0 -----> Consumer A
Partition 1 -----> Consumer A
Partition 2 -----> Consumer B
Partition 3 -----> Consumer C

Consumer Group: analytics (2 consumers)

Partition 0 -----> Consumer X
Partition 1 -----> Consumer X
Partition 2 -----> Consumer Y
Partition 3 -----> Consumer Y
```

### Rebalancing
When a consumer joins or leaves a group, partitions are reassigned. During rebalancing, consumption pauses. Kafka provides strategies:
- **Eager (stop-the-world)**: all consumers stop, partitions reassigned.
- **Cooperative (incremental)**: only moved partitions pause, others continue.

### Choosing partition count:
- More partitions = more parallelism, but more overhead (file handles, rebalancing time).
- Rule of thumb: `partitions = expected_consumers * 2` (room to scale).
- Partitions cannot be decreased after creation.

---

## Message Ordering

### The challenge:
Strict global ordering across a distributed queue is expensive and limits throughput. Most systems provide weaker ordering guarantees.

### Ordering levels:

| Level                   | How                                          | Cost                            |
|-------------------------|----------------------------------------------|---------------------------------|
| No ordering             | Any consumer, any order                      | Maximum throughput              |
| Per-partition ordering  | Kafka: messages with same key go same partition | Linear per partition           |
| Per-queue ordering      | RabbitMQ single queue with single consumer   | No parallelism                  |
| Global ordering         | Single partition / single consumer           | Throughput limited to one node  |
| FIFO ordering           | SQS FIFO + MessageGroupId                    | Up to 300 TPS per group         |

### How to guarantee ordering when you need it:
1. **Use a partition key / routing key**: ensure all related messages (same order ID, same user ID) go to the same partition.
2. **Sequence numbers**: include a sequence number in the message; consumers reorder if needed (out-of-order buffer).
3. **Idempotent + order-insensitive design**: redesign the consuming logic so order does not matter.

---

## Architecture Diagrams

### Kafka Cluster with Producers, Topics, Partitions, Consumer Groups

```
                        KAFKA CLUSTER
 +----------------------------------------------------------+
 |  Broker 1          Broker 2          Broker 3           |
 |  +-----------+     +-----------+     +-----------+      |
 |  | Topic:    |     | Topic:    |     | Topic:    |      |
 |  | orders    |     | orders    |     | orders    |      |
 |  | Part 0 [L]|     | Part 1 [L]|     | Part 2 [L]|      |
 |  | Part 1 [R]|     | Part 0 [R]|     | Part 1 [R]|      |
 |  +-----------+     +-----------+     +-----------+      |
 +----------------------------------------------------------+
         ^                  ^                 ^
         |                  |                 |
  Producer A          Producer B         Producer C
  (key: user_id)      (key: order_id)    (key: region)


Consumer Group: order-service                Consumer Group: analytics
+-------------------------+                  +--------------------+
| Consumer 1              |                  | Consumer X         |
|  reads Partition 0      |                  |  reads Part 0,1    |
| Consumer 2              |                  | Consumer Y         |
|  reads Partition 1      |                  |  reads Part 2      |
| Consumer 3              |                  +--------------------+
|  reads Partition 2      |
+-------------------------+

Offsets tracked per consumer group:
  order-service: {Part0: 1042, Part1: 987, Part2: 1105}
  analytics:     {Part0: 500,  Part1: 499, Part2: 501 }
                                         ^--- can lag independently
```

### RabbitMQ Routing via Exchanges

```
Producers
   |
   v
[Topic Exchange: events]
   |
   +--[routing: order.*]-----> [Queue: order_processing] --> Worker Pool
   |
   +--[routing: *.error]-----> [Queue: error_handling] --> Alert Service
   |
   +--[routing: #]-----------> [Queue: audit_log] --> Audit Service
                                                          |
                                              [DLX on failure]
                                                          |
                                                   [Queue: dlq]
```

---

## Real-World Use Cases

### Uber — Ride Matching Pipeline

Uber processes millions of location updates per second. Kafka is used to:
- Ingest GPS pings from drivers and riders into location topics.
- Fan out to multiple consumers: dispatch engine, surge pricing, ETA calculator.
- Decouple the mobile app (producer) from all downstream systems.
- Replay events for post-hoc analysis and ML feature engineering.

Key design: partition by city/region to keep related events together, enabling efficient geospatial queries per partition.

### LinkedIn — Activity Streams and Newsfeed

LinkedIn built Kafka internally to handle member activity (post, like, share, connection). Before Kafka, point-to-point pipelines grew to O(n^2) complexity. Kafka simplified to a single stream that all consumers subscribe to independently.

LinkedIn uses Kafka for:
- Real-time newsfeed ranking
- Offline analytics pipelines
- Change Data Capture from MySQL (Debezium → Kafka)

### Netflix — Event Pipeline

Netflix uses Apache Kafka as the backbone of its data pipeline, processing trillions of events per day:
- Client playback events (buffering, bitrate changes, errors)
- A/B test logging
- Alerting and anomaly detection (consumer reads stream, checks thresholds)
- ETL into data warehouses (Kafka → Flink → S3 / Redshift)

Netflix's "Keystone" pipeline processes ~500 billion events per day with Kafka at the center.

---

## When to Use Message Queues

### Use message queues when:
- **Workload spikes**: downstream services cannot handle peak throughput directly.
- **Async is acceptable**: the caller does not need an immediate response.
- **Multiple consumers**: different services need the same event (fan-out).
- **Decoupling deployments**: producer and consumer teams deploy independently.
- **Retry and durability**: you need guaranteed delivery with automatic retries.
- **Rate limiting downstream**: protect a slow or expensive service by queuing work.

### Prefer direct API calls when:
- **Immediate response required**: user is waiting for the result synchronously.
- **Simple, low-volume flows**: the overhead of a broker is not justified.
- **Strong consistency needed**: you need a transactional read-your-writes guarantee.

### Prefer event sourcing over queues when:
- You need a **complete audit history** of all state changes.
- Rebuilding state from events is a first-class requirement.
- You want to derive new read models from historical events.

---

## Tradeoffs and Considerations

| Consideration             | Notes                                                                         |
|---------------------------|-------------------------------------------------------------------------------|
| Operational complexity    | Kafka clusters require careful tuning (heap, disk, replication factor)        |
| Latency                   | Async inherently adds latency vs direct RPC; usually tens of ms               |
| Message ordering          | Hard to guarantee globally; design to not require it where possible           |
| Duplicate processing      | At-least-once means consumers must be idempotent                              |
| Consumer lag monitoring   | Unbounded lag means consumers are falling behind producers; needs alerting    |
| Schema evolution          | Use Avro + Schema Registry to evolve message formats without breaking consumers|
| Exactly-once complexity   | True end-to-end exactly-once is very hard; design for idempotency instead     |
| Backpressure              | Queue depth is the signal; auto-scaling consumers based on lag depth          |

---

## Best Practices

### Idempotency
Design consumers so that processing the same message multiple times produces the same outcome. Use:
- A deduplicated message ID stored in a database.
- Natural idempotency in the operation (e.g., `SET balance = X` vs `ADD balance += X`).
- Conditional writes / optimistic locking.

### Poison Messages
A poison message is one that consistently causes consumer failures and blocks the queue. Mitigations:
- Configure a max delivery count / retry limit.
- Route to DLQ after limit exceeded.
- Alert on DLQ depth immediately.
- Implement a circuit breaker in the consumer to stop retrying temporarily.

### Monitoring Consumer Lag
Consumer lag = (latest offset in partition) - (consumer's committed offset). High lag means:
- Consumers are too slow — scale out.
- A consumer is stuck — alert and investigate.
- A deployment issue — check consumer error logs.

Tools: Kafka's `kafka-consumer-groups.sh`, Burrow, Confluent Control Center, Datadog.

### Schema Management
- Use Apache Avro with a Schema Registry (Confluent) for structured messages.
- Never break the schema contract — add optional fields only, never remove or rename fields.
- Version your schemas; consumers should handle unknown fields gracefully.

### Partitioning Strategy
- Choose partition keys that distribute load evenly (avoid hot keys like `region=US` for all traffic).
- Co-locate related messages (same entity ID → same partition) to preserve ordering.
- Plan for future partition count increases — partitions can only be added, never reduced.

---

## Cross-Perspective: LLD Connections

**LLD View — Design Patterns That Implement Message Queues**

- **Observer / Producer-Consumer** — Message queues implement both: publishers emit events to a topic without knowing who subscribes; consumers receive asynchronously. This is Observer at infrastructure scale with persistence and guaranteed delivery.
- **Command** — Messages are serialized Command objects: they encapsulate an action, its parameters, and metadata (timestamp, correlation ID). Dead-letter queues hold failed commands for inspection or retry — the Command history pattern at system scale.
- **Strategy** — Routing strategies (topic-based, content-based, header-based), delivery guarantees (at-most-once, at-least-once, exactly-once), and consumer group assignment are interchangeable Strategy implementations.
- **Iterator** — Kafka consumers iterate over partition offsets. Committing the offset advances the iterator; seeking to an earlier offset replays messages — a stateful, resettable Iterator.

---

## Case Study: Ride-Matching Pipeline

### Context
A ride-hailing company needs to match rider requests to nearby drivers in real time, handling 100,000+ concurrent requests during peak hours.

### Architecture

```
[Rider App]  [Driver App]
     |              |
     v              v
[API Gateway]
     |
     v
[location-updates topic] (Kafka, 128 partitions, keyed by city)
     |
     +----> [Dispatch Engine] (Consumer Group: dispatch)
     |           |
     |       [Matches riders to drivers]
     |           |
     |           v
     |      [ride-events topic] (Kafka)
     |           |
     |           +---> [Notification Service] (push to apps)
     |           +---> [Billing Service] (fare calculation)
     |           +---> [Analytics Service] (surge pricing ML)
     |
     +----> [ETA Service] (Consumer Group: eta)
     |
     +----> [Surge Pricing] (Consumer Group: surge)
```

### Key Design Decisions
1. **Partition by city**: keeps location events local to a regional consumer, reducing cross-region latency.
2. **At-least-once with idempotent dispatch**: if dispatch processes the same ride request twice, the second attempt is a no-op (ride already matched).
3. **Separate consumer groups**: dispatch, ETA, and surge pricing each consume independently — a slow analytics job does not block real-time dispatch.
4. **DLQ for failed matches**: if dispatch cannot find a driver (no availability), the event goes to a DLQ for rider notification ("no cars nearby").
5. **Short retention (1 hour)**: location pings older than 1 hour are irrelevant, keeping Kafka storage manageable.

---

## Interview Questions

**Q1: What is the difference between a message queue and a message broker?**
A queue is a simple FIFO buffer; a message broker adds routing, exchange logic, protocol support (AMQP), and features like dead letter exchanges and priority queues. RabbitMQ is a broker; SQS is a queue.

**Q2: What is at-least-once delivery and why does it require idempotent consumers?**
The broker redelivers if no ACK is received before timeout. This can result in duplicate deliveries. Idempotent consumers handle duplicates safely — processing the same message twice has the same effect as processing it once.

**Q3: How does Kafka guarantee message ordering?**
Kafka guarantees ordering within a single partition. Assign a consistent partition key (e.g., order ID) so all related messages land in the same partition. Global ordering across partitions is not guaranteed.

**Q4: What is a Dead Letter Queue and when would you use it?**
A DLQ holds messages that could not be processed after max retries. Use it to prevent a poison message from blocking the queue, allow manual inspection, and enable replay after fixing the root cause.

**Q5: How does Kafka's consumer group model enable parallel processing?**
Each partition is consumed by at most one consumer within a group. Adding consumers up to the partition count increases parallelism linearly. Multiple consumer groups each receive all messages independently.

**Q6: How would you scale a message queue system to handle a 10x traffic spike?**
Scale consumers horizontally (add instances up to partition count). If more parallelism is needed, increase partition count (pre-plan this). Use auto-scaling based on consumer lag metrics. Kafka's broker layer scales by adding brokers and rebalancing partitions.

**Q7: How do you handle schema changes in a message queue without breaking consumers?**
Use a Schema Registry with Avro or Protobuf. Apply backward-compatible changes only (add optional fields). Deploy new consumers before changing producers (consumer-first deployment). Never rename or remove fields without a versioning strategy.

**Q8: What is the visibility timeout in SQS and why does it matter?**
When a consumer reads a message, it becomes invisible to others for the visibility timeout duration. If the consumer does not delete it in time, the message reappears. Set visibility timeout to slightly longer than max expected processing time to avoid duplicate processing.

**Q9: What is Kafka log compaction and when would you use it?**
Log compaction retains only the latest message per key. Older values for the same key are garbage collected. Use it for changelog topics that represent the current state of an entity (e.g., user profile updates), where only the latest state matters.

**Q10: How do you prevent a slow consumer from causing the message queue to grow indefinitely?**
Monitor consumer lag with alerts. Auto-scale consumers when lag exceeds a threshold. Apply backpressure to producers if lag is critical. Set message TTL so old messages expire rather than accumulating. Implement circuit breakers to stop producing to a queue when consumers are overwhelmed.

**Q11: How would you implement exactly-once processing end-to-end with Kafka?**
Use Kafka's idempotent producer (enable.idempotence=true) and transactional API to atomically write to Kafka and commit offsets. For the consumer-to-database leg, use the outbox pattern or transactional writes where the database operation and offset commit are in the same transaction.

**Q12: What happens when a Kafka broker fails?**
Kafka replicates each partition across multiple brokers (replication factor, typically 3). One replica is the leader; others are followers. If the leader fails, one of the in-sync replicas (ISR) is elected as the new leader. Producers and consumers reconnect and continue with minimal interruption.
