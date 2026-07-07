# Message Queues and Event-Driven Architecture

Phase 5 — FastAPI Production Concerns

Cross-links:
- [Background Jobs and Task Queues](../background_jobs_and_task_queues/README.md)
- [HLD Message Queue Patterns](../../hld/case_studies/README.md)

---

## 1. Concept Overview

Message queues decouple producers from consumers: a sender writes a message to a broker (Kafka, RabbitMQ, SQS), and one or more receivers process it independently of when it was sent. The broker absorbs traffic spikes, provides durability, and allows each side to scale, fail, and restart independently.

**Event-driven architecture** generalises this: components emit domain events (order.created, payment.failed) rather than calling each other directly. Downstream services subscribe to events they care about, enabling loose coupling across service boundaries.

In Python the two primary async clients are:
- **aiokafka** — async wrapper over the kafka-python driver; surfaces `AIOKafkaProducer` and `AIOKafkaConsumer`.
- **aio-pika** — async AMQP 0-9-1 client for RabbitMQ; surfaces `connect_robust`, exchange/queue/binding primitives, and `IncomingMessage.ack()`.

---

## 2. Intuition

> A message queue is a postal service for software: the sender drops a letter in the mailbox and walks away; the postal service guarantees delivery; the recipient reads and processes at their own pace.

**Mental model:** Think of a kitchen order-ticket rail at a restaurant. The waiter (producer) clips a ticket to the rail; multiple chefs (consumers) pull tickets and cook independently. If one chef is slow, tickets accumulate on the rail — they do not back-pressure the waiter.

**Why it matters:** Without a broker, service A calling service B directly means A must handle B's downtime, B must handle A's throughput spikes, and every new subscriber forces A to be modified. A broker eliminates all three problems simultaneously.

**Key insight:** Delivery semantics (at-most-once, at-least-once, exactly-once) are not free properties of the broker — they emerge from the combination of how the producer flushes, how the consumer commits offsets or acks, and whether the application logic is idempotent. The broker only provides the raw mechanism.

---

## 3. Core Principles

1. **Decoupling** — producers and consumers have no direct dependency on each other's uptime, address, or API surface.
2. **Durability** — messages survive broker restarts; replicated partitions (Kafka) or durable queues (RabbitMQ) prevent data loss.
3. **Backpressure** — consumers control their own pace; the broker absorbs bursts; producers block or drop when the broker is overwhelmed (configurable).
4. **Ordering** — Kafka guarantees per-partition order; RabbitMQ guarantees per-queue order for a single consumer; fanout sacrifices ordering for parallelism.
5. **Idempotency** — because at-least-once delivery is the practical default, consumer logic must be safe to execute more than once for the same message.
6. **Schema discipline** — producers and consumers share an implicit or explicit contract over message shape; breaking that contract crashes consumers silently at runtime without a registry.

---

## 4. Types / Architectures / Strategies

### 4.1 Broker Topologies

| Topology | Mechanism | Ordering | Fan-out | Use case |
|----------|-----------|----------|---------|----------|
| Point-to-point queue | Single consumer per message | FIFO per queue | No | Task offloading, RPC-style work queues |
| Publish-subscribe | Multiple independent consumers | Per partition / per queue | Yes | Event broadcasting, audit logs, cache invalidation |
| Stream | Persistent log, consumers replay | Per partition | Yes + replay | Event sourcing, analytics pipelines, ML feature stores |
| Priority queue | Higher-priority messages dequeued first | Priority, then FIFO | No | SLA tiers, VIP jobs |

### 4.2 Delivery Semantics

**At-most-once** — commit offset before processing. If the consumer crashes mid-processing the message is lost. Use when losing an event is acceptable (metrics, low-value notifications).

**At-least-once** — commit offset after successful processing. If the consumer crashes between processing and commit, the message is reprocessed. Requires idempotent handlers. This is the practical default.

**Exactly-once** — Kafka transactions (`isolation.level=read_committed`, `enable.idempotence=true`, `transactional.id`). Costly: ~30–50 % throughput reduction vs at-least-once. Use for financial transfers, inventory deduction.

### 4.3 Exchange Types (RabbitMQ)

- **Direct** — routes to queue whose binding key exactly matches the routing key.
- **Topic** — routes using wildcard patterns (`order.*`, `#.failed`).
- **Fanout** — broadcasts to all bound queues regardless of routing key.
- **Headers** — routes based on message header attributes instead of routing key.

### 4.4 Outbox Pattern

Write the event to an `outbox` table in the same database transaction as the business record. A separate poller (or CDC via Debezium) reads unprocessed outbox rows and publishes them to the broker. Once published, rows are marked as sent. This is the only reliable way to prevent dual-write inconsistency.

### 4.5 Consumer Groups (Kafka)

All consumers sharing the same `group_id` collaborate: each partition is assigned to exactly one consumer in the group. Adding consumers up to the partition count scales throughput linearly. Consumers in different groups each receive all messages independently (fan-out).

### 4.6 Dead-Letter Queue (DLQ)

Messages that repeatedly fail processing are moved to a dead-letter destination after `N` retries. In Kafka this is a dedicated topic (`orders.DLT`). In RabbitMQ it is a DLX (dead-letter exchange) binding. DLQ contents should trigger alerts, be inspectable, and support replay after the bug is fixed.

---

## 5. Architecture Diagrams

### 5.1 Kafka Consumer Group in FastAPI

```
  FastAPI App (3 instances)
  ┌───────────────────────────────────────────────┐
  │  Route: POST /orders                          │
  │    └─> AIOKafkaProducer.send("orders", msg)   │
  └───────────────────────────────────────────────┘
                    │
                    ▼
  ┌─────────────────────────────────────────────────┐
  │  Kafka Cluster                                  │
  │  Topic: orders  (6 partitions, RF=3)            │
  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐           │
  │  │ P-0  │ │ P-1  │ │ P-2  │ │ P-3  │  ...      │
  │  └──────┘ └──────┘ └──────┘ └──────┘           │
  └─────────────────────────────────────────────────┘
         │           │           │
         ▼           ▼           ▼
  ┌──────────┐ ┌──────────┐ ┌──────────┐
  │Consumer-1│ │Consumer-2│ │Consumer-3│   group_id="order-svc"
  │ P-0,P-1  │ │  P-2,P-3 │ │  P-4,P-5 │
  └──────────┘ └──────────┘ └──────────┘
         │
         ▼
  ┌─────────────┐
  │  DLT topic  │  orders.DLT
  │  (retries=3)│
  └─────────────┘
```

### 5.2 Outbox Pattern

```
  FastAPI Route
       │  (single SQLAlchemy transaction)
       ├──> INSERT INTO orders ...
       └──> INSERT INTO outbox (event_type, payload, sent=False)
                    │
                    │  (separate process / background task)
                    ▼
       ┌──────────────────────┐
       │  Outbox Poller       │
       │  SELECT * FROM outbox│
       │  WHERE sent = False  │
       │  LIMIT 100           │
       └──────────────────────┘
                    │
                    ▼
       AIOKafkaProducer.send()
                    │
       UPDATE outbox SET sent=True WHERE id=...
```

### 5.3 RabbitMQ Topic Exchange Fan-out

```
  Producer: routing_key = "order.created"
       │
       ▼
  ┌──────────────────┐
  │  Topic Exchange  │  "events"
  └──────────────────┘
        │          │
  binding: order.* │  binding: #.created
        │          │
        ▼          ▼
  ┌──────────┐  ┌──────────────┐
  │  Queue A │  │   Queue B    │
  │ invoicing│  │  audit-log   │
  └──────────┘  └──────────────┘
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 aiokafka Producer

```python
from aiokafka import AIOKafkaProducer
import asyncio, json

async def get_producer() -> AIOKafkaProducer:
    producer = AIOKafkaProducer(
        bootstrap_servers="kafka:9092",
        acks="all",               # wait for all in-sync replicas — strongest durability
        enable_idempotence=True,  # exactly-once producer semantics (deduplicates retries)
        compression_type="gzip",
        max_batch_size=16_384,    # bytes; batch for throughput
        linger_ms=5,              # wait up to 5 ms to fill a batch
    )
    await producer.start()
    return producer


async def publish_order_event(producer: AIOKafkaProducer, order_id: str) -> None:
    payload = json.dumps({"order_id": order_id, "event": "order.created"}).encode()
    # send_and_wait blocks until broker acknowledges (acks="all")
    record_metadata = await producer.send_and_wait("orders", value=payload, key=order_id.encode())
    print(f"offset={record_metadata.offset} partition={record_metadata.partition}")
```

`acks=0` — fire-and-forget, max throughput, no durability.
`acks=1` — leader acknowledges, followers might not have replicated.
`acks="all"` — all in-sync replicas acknowledge; survives a leader failure.

### 6.2 aiokafka Consumer — Manual Offset Commit

```python
from aiokafka import AIOKafkaConsumer
from aiokafka.errors import KafkaError

async def consume_orders() -> None:
    consumer = AIOKafkaConsumer(
        "orders",
        bootstrap_servers="kafka:9092",
        group_id="order-processing-svc",
        enable_auto_commit=False,   # manual commit for at-least-once
        auto_offset_reset="earliest",
        max_poll_records=100,
    )
    await consumer.start()
    try:
        async for msg in consumer:
            try:
                await process_message(msg.value)
                # Commit AFTER successful processing — at-least-once
                await consumer.commit()
            except Exception as exc:
                # Do NOT commit — message will be redelivered after rebalance / restart
                print(f"Processing failed, will retry: {exc}")
    finally:
        await consumer.stop()
```

### 6.3 FastAPI Lifespan Integration

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from aiokafka import AIOKafkaProducer, AIOKafkaConsumer
import asyncio

kafka_producer: AIOKafkaProducer | None = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global kafka_producer
    # Startup
    kafka_producer = AIOKafkaProducer(
        bootstrap_servers="kafka:9092",
        acks="all",
        enable_idempotence=True,
    )
    await kafka_producer.start()

    consumer_task = asyncio.create_task(run_consumer())

    yield  # Application runs here

    # Shutdown
    consumer_task.cancel()
    try:
        await consumer_task
    except asyncio.CancelledError:
        pass
    await kafka_producer.stop()


async def run_consumer() -> None:
    consumer = AIOKafkaConsumer(
        "payments",
        bootstrap_servers="kafka:9092",
        group_id="payment-svc",
        enable_auto_commit=False,
    )
    await consumer.start()
    try:
        async for msg in consumer:
            await handle_payment_event(msg)
            await consumer.commit()
    except asyncio.CancelledError:
        pass
    finally:
        await consumer.stop()


app = FastAPI(lifespan=lifespan)


@app.post("/orders")
async def create_order_endpoint(order_data: dict) -> dict:
    import json
    await kafka_producer.send_and_wait(
        "orders",
        value=json.dumps(order_data).encode(),
    )
    return {"status": "queued"}
```

### 6.4 aio-pika (RabbitMQ)

```python
import aio_pika
from aio_pika import Message, ExchangeType
from aio_pika.abc import AbstractIncomingMessage


async def publish_via_rabbitmq(routing_key: str, body: bytes) -> None:
    connection = await aio_pika.connect_robust("amqp://guest:guest@rabbitmq/")
    async with connection:
        channel = await connection.channel()
        exchange = await channel.declare_exchange(
            "events", ExchangeType.TOPIC, durable=True
        )
        await exchange.publish(
            Message(body, delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
            routing_key=routing_key,
        )


async def consume_via_rabbitmq() -> None:
    connection = await aio_pika.connect_robust("amqp://guest:guest@rabbitmq/")
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=10)  # backpressure: max 10 unacked messages

    exchange = await channel.declare_exchange("events", ExchangeType.TOPIC, durable=True)
    queue = await channel.declare_queue("invoicing", durable=True, arguments={
        "x-dead-letter-exchange": "events.dlx",  # DLQ on rejection
    })
    await queue.bind(exchange, routing_key="order.*")

    async with queue.iterator() as queue_iter:
        async for message in queue_iter:
            async with message.process(requeue=False):
                # If an exception escapes, message is nacked + sent to DLX
                await handle_invoice(message.body)
                # message.ack() called automatically by context manager on success
```

### 6.5 Idempotent Consumer with Redis

```python
import redis.asyncio as redis
import json


async def idempotent_handler(
    message_id: str,
    payload: bytes,
    redis_client: redis.Redis,
) -> bool:
    """Returns True if processed, False if duplicate."""
    dedup_key = f"processed:{message_id}"
    # SET NX EX: set only if key does not exist; expires after 24 h
    was_set = await redis_client.set(dedup_key, "1", nx=True, ex=86_400)
    if not was_set:
        # Duplicate — skip silently
        return False

    data = json.loads(payload)
    await do_business_logic(data)
    return True
```

### 6.6 Outbox Pattern Implementation

```python
# BROKEN: dual-write — produce to Kafka then write to DB (or vice versa);
# partial failure leaves inconsistency.
async def create_order_broken(order: dict) -> dict:
    await kafka_producer.send_and_wait("orders", json.dumps(order).encode())  # Step 1
    db_order = await db_save(order)                                            # Step 2
    # If Step 2 fails, the Kafka message was already published — duplicate event
    # If Step 1 fails, the DB write never happened — silent data loss
    return db_order


# FIX: outbox pattern — write event to DB in same transaction as business data,
# publish separately via a dedicated poller.
async def create_order_fixed(order: dict, session: AsyncSession) -> dict:
    async with session.begin():
        db_order = Order(**order)
        outbox_event = OutboxEvent(
            aggregate_id=str(db_order.id),
            event_type="order.created",
            payload=json.dumps(order),
            sent=False,
        )
        session.add(db_order)
        session.add(outbox_event)  # Atomic with the business record — one commit or both fail
    # The outbox poller (separate task) reads unsent rows and publishes to Kafka:
    #   SELECT * FROM outbox WHERE sent=False ORDER BY created_at LIMIT 100
    #   FOR UPDATE SKIP LOCKED;
    #   producer.send(...); UPDATE outbox SET sent=True WHERE id=...
    return db_order.__dict__
```

---

## 7. Real-World Examples

**Stripe** routes payment events through Kafka topics partitioned by merchant ID, guaranteeing that all events for a single merchant are processed in order by the same consumer. Consumer groups for billing, fraud, and analytics each receive independent copies.

**Uber Eats** uses RabbitMQ topic exchanges to fan out order-state-change events. The kitchen display, driver app, and customer app each bind to the same exchange with different routing-key patterns. Exactly-once is not needed; idempotent state machines on the consumer side handle redelivery.

**Shopify** implements the outbox pattern via a `outbox_events` Postgres table. A Debezium connector streams CDC events from the table to Kafka. This removes the polling latency (CDC fires within ~50 ms of the commit) and avoids a polling loop hammering the DB.

**LinkedIn** created Kafka: their internal use case was tracking page-view events at 7 million messages/second peak. Partitioned by member ID, consumer groups process feed generation, analytics, and notifications independently at different latencies (feed: 100 ms, analytics: 5 s batch).

---

## 8. Tradeoffs

### 8.1 Delivery Semantics

| Semantic | Offset commit timing | Failure behaviour | Throughput impact | Use case |
|----------|---------------------|------------------|-------------------|----------|
| At-most-once | Before processing | Message lost on crash | Highest | Metrics, telemetry |
| At-least-once | After processing | Reprocessed on crash | Moderate | Default for most workloads |
| Exactly-once | Transactional | No duplicates or loss | ~30–50 % lower | Financial, inventory |

### 8.2 Kafka vs RabbitMQ

| Dimension | Kafka | RabbitMQ |
|-----------|-------|----------|
| Retention | Log retained by time/size; consumers replay | Message deleted after ack |
| Throughput | Millions msg/s (sequential disk I/O) | ~50 k–200 k msg/s (per queue) |
| Ordering | Per partition | Per queue, single consumer |
| Routing | Topic + partition key | Exchange types (flexible) |
| Replay | Yes — reset consumer offset | No (once acked, gone) |
| Protocol | Proprietary (binary TCP) | AMQP 0-9-1 / AMQP 1.0 |
| Ops complexity | High (ZooKeeper/KRaft, partition mgmt) | Medium |

### 8.3 Outbox vs Saga Choreography

| Pattern | Consistency model | Complexity | Recovery |
|---------|------------------|------------|----------|
| Outbox | Strong (single DB txn) | Medium (poller/CDC) | Automatic via re-poll |
| Saga (choreography) | Eventual | High (compensating txns) | Manual / compensating |
| Saga (orchestration) | Eventual | Very high (central coordinator) | Coordinator-driven |

---

## 9. When to Use / When NOT to Use

### Use message queues when:
- Services need to scale independently (e.g., order intake vs order fulfilment at different rates).
- You need to absorb traffic spikes without dropping requests.
- Multiple downstream systems need to react to the same event.
- Long-running work should not block HTTP request handlers.
- You require audit trails or replay capability.

### Do NOT use message queues when:
- You need synchronous, low-latency responses where the result of the operation must be returned to the caller immediately (use direct RPC / HTTP instead).
- The system is simple enough that the added operational overhead (broker cluster, monitoring, consumer lag alerting) exceeds the benefit.
- Message ordering across multiple partitions is a hard requirement — Kafka only guarantees order per partition.
- Your team cannot yet operate a broker reliably; a broken Kafka cluster stops all event-driven flows simultaneously.

---

## 10. Common Pitfalls

### Pitfall 1: Dual-Write Without Outbox (Data Inconsistency)

```python
# BROKEN: Two independent writes — one to Kafka, one to Postgres.
# If Kafka succeeds but Postgres fails (or vice versa), the systems diverge.
async def process_payment_broken(payment: dict) -> None:
    await kafka_producer.send_and_wait("payments", json.dumps(payment).encode())
    await db.execute("INSERT INTO payments VALUES (...)", payment)
    # Network partition between lines 2 and 3 = ghost event in Kafka, no DB record


# FIX: Write to DB first (single source of truth), use outbox row in the same txn.
async def process_payment_fixed(payment: dict, session: AsyncSession) -> None:
    async with session.begin():
        session.add(Payment(**payment))
        session.add(OutboxEvent(
            event_type="payment.processed",
            payload=json.dumps(payment),
            sent=False,
        ))
    # Poller publishes the outbox row to Kafka; if it crashes, row is retried on next poll.
```

### Pitfall 2: Auto-Commit with enable_auto_commit=True Loses Messages

```python
# BROKEN: aiokafka default auto-commits periodically.
# If a message is polled, auto-committed, then processing crashes,
# the message is silently lost.
consumer = AIOKafkaConsumer(
    "orders",
    bootstrap_servers="kafka:9092",
    group_id="order-svc",
    # enable_auto_commit=True is the default — dangerous for critical workloads
)
async for msg in consumer:
    # auto-commit fires in background every auto_commit_interval_ms (5000 ms)
    await process_order(msg.value)  # crash here after auto-commit = lost message


# FIX: Disable auto-commit; commit manually after successful processing.
consumer = AIOKafkaConsumer(
    "orders",
    bootstrap_servers="kafka:9092",
    group_id="order-svc",
    enable_auto_commit=False,
)
async for msg in consumer:
    await process_order(msg.value)
    await consumer.commit()  # Commit only after processing succeeds
```

### Pitfall 3: Missing Prefetch Limit Causes Memory Exhaustion (RabbitMQ)

```python
# BROKEN: No QoS set — RabbitMQ pushes ALL queued messages to the consumer immediately.
# With 500 k messages in queue, consumer OOMs before processing any.
channel = await connection.channel()
queue = await channel.declare_queue("invoicing", durable=True)


# FIX: Set prefetch_count to limit in-flight unacked messages.
channel = await connection.channel()
await channel.set_qos(prefetch_count=50)  # Only 50 unacked messages in-flight
queue = await channel.declare_queue("invoicing", durable=True)
```

### Pitfall 4: No Idempotency — Reprocessing Causes Double-Billing

```python
# BROKEN: Consumer crashes after processing but before committing.
# On restart the message is redelivered and processed again.
async for msg in consumer:
    await charge_customer(msg.value)   # debits card
    await consumer.commit()            # if this line crashes, card charged twice on retry


# FIX: Check deduplication key before executing the side-effect.
async for msg in consumer:
    msg_id = msg.headers.get("idempotency_key", f"{msg.topic}-{msg.partition}-{msg.offset}")
    if not await redis_client.set(f"charged:{msg_id}", "1", nx=True, ex=86_400):
        await consumer.commit()
        continue  # Already processed — skip
    await charge_customer(msg.value)
    await consumer.commit()
```

### Pitfall 5: Ignoring Consumer Lag Until Queues Blow Up

Consumer lag (difference between latest offset and committed offset) is the primary health signal for a Kafka consumer. Teams that only monitor HTTP error rates miss growing backlogs. With a 6-partition topic and each consumer processing 500 msg/s, a consumer processing 400 msg/s accumulates 36 k messages of lag per minute. At that rate, a 30-minute outage creates an 18-million-message backlog requiring 10 hours to drain at normal throughput. Alert when lag exceeds a threshold that would breach your processing SLA.

---

## 11. Technologies & Tools

| Tool | Protocol | Throughput | Replay | Routing | Python client |
|------|----------|------------|--------|---------|---------------|
| Apache Kafka | Proprietary | Millions msg/s | Yes (log retention) | Topic + partition key | aiokafka |
| RabbitMQ | AMQP 0-9-1 | 50 k–200 k msg/s | No (DLQ only) | Exchange types | aio-pika |
| AWS SQS | HTTP/HTTPS | ~3 k msg/s per queue (standard) | No | Queue + SNS fan-out | aiobotocore |
| Redis Streams | RESP3 | ~500 k msg/s | Yes (XREAD > last ID) | Consumer groups | redis.asyncio |
| NATS JetStream | NATS | ~10 M msg/s | Yes | Subject matching | nats-py |
| Google Pub/Sub | HTTP/gRPC | Auto-scaled | 7-day replay | Topic + subscription | google-cloud-pubsub |

**Schema registry options:**
- **Confluent Schema Registry** — Avro/Protobuf/JSON Schema; producer/consumer validate before send/receive.
- **AWS Glue Schema Registry** — managed, integrates with MSK.
- **buf.build** — Protobuf schema management with breaking-change detection.

---

## 12. Interview Questions with Answers

**Q1: What is the difference between at-least-once and exactly-once delivery, and when would you pay for exactly-once?**
At-least-once commits the offset after successful processing, so a crash between processing and commit causes redelivery. Exactly-once uses Kafka transactions (`transactional.id`, `isolation.level=read_committed`) to atomically write output and commit input offsets. Pay the ~30–50 % throughput penalty only for financial debits, inventory deductions, or any side-effect that cannot be made idempotent cheaply.

**Q2: How does the outbox pattern prevent dual-write inconsistency?**
The outbox pattern writes the business record and the outbox event row in a single database transaction. Because both writes share one commit, either both land or neither does — eliminating the window where the external broker has the message but the DB does not (or vice versa). A separate poller or CDC connector publishes outbox rows to the broker after the fact.

**Q3: How do consumer groups work in Kafka, and what happens during partition rebalancing?**
All consumers sharing a `group_id` divide partitions among themselves: each partition is owned by exactly one consumer. When a consumer joins or leaves (including crashes), the group coordinator triggers a rebalance — partitions are redistributed. During rebalance, consumption pauses. Incremental cooperative rebalancing (available since Kafka 2.4) minimises the pause by revoking only the partitions that need to move.

**Q4: When should you use a topic exchange vs a direct exchange in RabbitMQ?**
Use a direct exchange when routing is 1:1 between routing key and queue — simple task queues. Use a topic exchange when multiple queues need to subscribe based on patterns (e.g., `order.*` for all order events, `*.failed` for all failures). Fanout exchange is appropriate when every bound queue must receive every message regardless of routing key — e.g., cache invalidation broadcast.

**Q5: How do you implement an idempotent consumer?**
Store a unique message ID (Kafka `topic-partition-offset` or a business-level idempotency key) in Redis using `SET NX EX`. Before executing the side-effect, attempt to set the key. If the key already exists the message was already processed — skip and commit the offset. Set TTL to at least the maximum possible retry window (e.g., 24 hours).

**Q6: What is `prefetch_count` in RabbitMQ and why does it matter?**
`prefetch_count` sets the maximum number of unacknowledged messages RabbitMQ will push to a consumer at once. Without it, RabbitMQ floods the consumer with the entire queue backlog, causing memory exhaustion. A value of 10–100 is typical; higher values improve throughput at the cost of re-queuing more messages if the consumer crashes.

**Q7: What is consumer lag, and how do you alert on it?**
Consumer lag is the difference between the latest available offset (log end offset) and the consumer group's committed offset per partition. It measures how far behind consumers are. Expose it via `kafka-consumer-groups.sh --describe` or the JMX metric `kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*,attribute=records-lag-max`. Alert when lag exceeds the number of messages that would accumulate during your acceptable recovery time (e.g., `> 10_000` for a queue that produces 500 msg/s and must drain within 20 s).

**Q8: How do you handle message ordering in Kafka when you need order per customer?**
Produce messages with the customer ID as the partition key (`key=customer_id.encode()`). Kafka hashes the key to a partition deterministically — all messages for the same customer land in the same partition and are consumed in order. Ordering is guaranteed per partition; if you need a total order across all customers you are limited to a single partition, which caps throughput to ~1 Gbps.

**Q9: Describe the dead-letter queue pattern and when you would replay from it.**
A DLQ captures messages that fail processing after `N` retry attempts. In Kafka, configure the consumer to produce to `<topic>.DLT` after retries. In RabbitMQ, bind a DLX exchange and a dead-letter queue on the primary queue declaration. Replay from the DLQ after fixing the bug: for Kafka reset the consumer group offset to the earliest offset in the DLT topic; for RabbitMQ use the management plugin to re-queue DLQ messages back to the main queue.

**Q10: What is `enable_idempotence=True` in the aiokafka producer and what does it guarantee?**
It enables exactly-once producer semantics: the broker assigns a producer ID and sequence number to each batch. If the producer retries due to a network timeout, the broker deduplicates by sequence number, ensuring the message is written exactly once even with retries. This does not extend to consumer-side semantics; you still need idempotent consumers for end-to-end exactly-once.

**Q11: How does schema evolution work with a schema registry?**
Producers register a schema version before first publish; the registry assigns a schema ID embedded in the message header. Consumers fetch the schema by ID before deserialising. The registry enforces compatibility modes: BACKWARD (new schema can read old messages), FORWARD (old schema can read new messages), FULL (both). Additive changes (new optional field) are BACKWARD compatible. Removing a required field is not. Always test schema changes in a staging registry before promoting to production.

**Q12: What is the `acks="all"` setting and what does it guarantee vs `acks=1`?**
`acks="all"` (or `acks=-1`) requires all in-sync replicas (ISR) to acknowledge the write before the producer call returns. If the leader fails, a follower in the ISR that received the message can be elected — no data loss. `acks=1` only requires the leader to acknowledge; if the leader fails before replicating, the message is lost. The latency cost of `acks="all"` is roughly the replication latency (typically 1–5 ms on LAN). For financial events or order creation, always use `acks="all"`.

**Q13: How do you safely shut down a Kafka consumer in an asyncio application?**
Cancel the consumer asyncio task on `SIGTERM`, catch `CancelledError`, and call `await consumer.stop()` in the finally block. `consumer.stop()` triggers a final offset commit (if using auto-commit) and sends a LeaveGroup request so the broker can rebalance the partition to another consumer immediately rather than waiting for the session timeout (default 10 s). For manual commits, commit before calling `stop()`.

---

## 13. Best Practices

1. **Always disable auto-commit** for workloads where message loss is unacceptable. Use `enable_auto_commit=False` and commit manually after processing.

2. **Use `acks="all"` with `enable_idempotence=True`** for producers writing to critical topics. The combination prevents both data loss (replication) and duplicate writes (sequence numbers).

3. **Implement the outbox pattern** for any write that must be consistent between a database and a message broker. Never produce to the broker inside a database transaction and never write to the database after a broker produce.

4. **Make all consumer handlers idempotent** before relying on at-least-once delivery. Use a Redis `SET NX EX` check keyed on a unique message ID derived from `topic-partition-offset` or a business idempotency key.

5. **Set `prefetch_count`** on every RabbitMQ consumer channel. A value of 10–50 is safe for CPU-bound tasks; 100–200 for I/O-bound tasks that complete quickly.

6. **Alert on consumer lag**, not just error rate. Add a Prometheus gauge for `records-lag-max` per consumer group. Page when lag crosses the threshold that would breach your SLA drain time.

7. **Provision a dead-letter queue** for every critical topic or queue at the time of creation, not after the first production incident. Configure automatic DLQ routing after 3–5 retries with exponential backoff.

8. **Use separate consumer group IDs** for logically independent consumers of the same topic (e.g., `billing-svc`, `notification-svc`, `analytics-svc`). Sharing a group ID means only one service receives each message.

9. **Validate schemas at the producer** using a schema registry. Failing fast at produce time is far cheaper than debugging a downstream consumer crash from an unexpected field type.

10. **Graceful shutdown** — on SIGTERM, stop accepting new messages, drain in-flight processing, commit offsets, call `consumer.stop()` / `producer.stop()`, then exit. Target a shutdown within `session.timeout.ms` (default 10 s) to minimise rebalance downtime.

---

## 14. Case Study

**Scenario:** An e-commerce platform needs to process 50 k orders/hour at peak. Each order triggers three downstream actions: invoice generation, inventory reservation, and notification dispatch. The operations team has had two production incidents where database records and Kafka events drifted out of sync.

### Architecture

```
  POST /orders  ─────────────────────────────────────────────┐
                                                              │
  ┌────────────────────────────────────────────────────────── ▼ ──┐
  │  FastAPI (3 replicas)                                         │
  │  create_order() ──> SQLAlchemy AsyncSession (one transaction) │
  │    INSERT INTO orders (id, ...)                               │
  │    INSERT INTO outbox (event_type="order.created", sent=False)│
  └──────────────────────────────────────────────────────────────┘
                                 │
                   DB commit (PostgreSQL)
                                 │
  ┌──────────────────────────────▼────────────────────────────────┐
  │  Outbox Poller (asyncio task in lifespan, poll interval 500ms)│
  │  SELECT ... FROM outbox WHERE sent=False LIMIT 100             │
  │  FOR UPDATE SKIP LOCKED                                        │
  │  AIOKafkaProducer.send_and_wait("orders", payload)             │
  │  UPDATE outbox SET sent=True WHERE id IN (...)                 │
  └──────────────────────────────────────────────────────────────┘
                                 │
  ┌──────────────────────────────▼────────────────────────────────┐
  │  Kafka: Topic "orders" (12 partitions, RF=3, retention=7d)    │
  └──────────────────────────────────────────────────────────────┘
              │                  │                  │
  ┌───────────▼──────┐  ┌────────▼──────┐  ┌───────▼──────────┐
  │  invoice-svc     │  │ inventory-svc │  │ notification-svc │
  │ group: invoice   │  │group: invnty  │  │ group: notify    │
  │ manual commit    │  │ manual commit │  │ at-most-once ok  │
  │ idempotent       │  │ idempotent    │  │                  │
  └──────────────────┘  └───────────────┘  └──────────────────┘
              │
  ┌───────────▼──────┐
  │ orders.DLT topic │  (after 3 retries via retry logic in consumer)
  └──────────────────┘
```

### Implementation

```python
import asyncio
import json
from contextlib import asynccontextmanager
from typing import Any
from uuid import uuid4

from aiokafka import AIOKafkaProducer
from fastapi import FastAPI, Depends
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker

DATABASE_URL = "postgresql+asyncpg://user:pass@db/orders"
engine = create_async_engine(DATABASE_URL, pool_size=10, max_overflow=5)
SessionLocal = async_sessionmaker(engine, expire_on_commit=False)

kafka_producer: AIOKafkaProducer | None = None


async def get_session() -> AsyncSession:
    async with SessionLocal() as session:
        yield session


# --- Outbox poller ---

async def outbox_poller(producer: AIOKafkaProducer) -> None:
    """Reads unsent outbox rows and publishes them to Kafka, then marks as sent."""
    async with SessionLocal() as session:
        while True:
            try:
                async with session.begin():
                    rows = (await session.execute(
                        text(
                            "SELECT id, event_type, payload FROM outbox "
                            "WHERE sent = false ORDER BY created_at LIMIT 100 "
                            "FOR UPDATE SKIP LOCKED"
                        )
                    )).fetchall()

                    for row in rows:
                        await producer.send_and_wait(
                            "orders",
                            value=row.payload.encode(),
                            key=row.event_type.encode(),
                        )
                        await session.execute(
                            text("UPDATE outbox SET sent = true WHERE id = :id"),
                            {"id": row.id},
                        )
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                print(f"Outbox poller error: {exc}")

            await asyncio.sleep(0.5)  # 500 ms polling interval


# --- Lifespan ---

@asynccontextmanager
async def lifespan(app: FastAPI):
    global kafka_producer
    kafka_producer = AIOKafkaProducer(
        bootstrap_servers="kafka:9092",
        acks="all",
        enable_idempotence=True,
        compression_type="gzip",
    )
    await kafka_producer.start()

    poller_task = asyncio.create_task(outbox_poller(kafka_producer))

    yield

    poller_task.cancel()
    try:
        await poller_task
    except asyncio.CancelledError:
        pass
    await kafka_producer.stop()


app = FastAPI(lifespan=lifespan)


# --- Route ---

@app.post("/orders", status_code=201)
async def create_order(
    order_data: dict[str, Any],
    session: AsyncSession = Depends(get_session),
) -> dict[str, Any]:
    order_id = str(uuid4())
    async with session.begin():
        await session.execute(
            text("INSERT INTO orders (id, data) VALUES (:id, :data)"),
            {"id": order_id, "data": json.dumps(order_data)},
        )
        await session.execute(
            text(
                "INSERT INTO outbox (id, event_type, payload, sent, created_at) "
                "VALUES (:id, :event_type, :payload, false, now())"
            ),
            {
                "id": str(uuid4()),
                "event_type": "order.created",
                "payload": json.dumps({"order_id": order_id, **order_data}),
            },
        )
    return {"order_id": order_id, "status": "accepted"}
```

### BROKEN / FIX Recap

```python
# BROKEN: Producer called inside the HTTP handler, outside any DB transaction.
# A crash between lines 1 and 2 corrupts state irrecoverably.
@app.post("/orders")
async def create_order_broken(order_data: dict) -> dict:
    await kafka_producer.send_and_wait("orders", json.dumps(order_data).encode())  # line 1
    await session.execute(text("INSERT INTO orders ..."))                           # line 2
    return {}


# FIX: DB write + outbox row in one transaction; producer called by the separate poller.
# See full implementation above.
```

### Discussion Questions

1. The outbox poller uses `FOR UPDATE SKIP LOCKED`. What does that clause accomplish and why is it essential when running multiple app replicas?

   SKIP LOCKED prevents two poller instances from picking the same outbox rows — each instance locks and processes a distinct subset. Without it, both instances would publish the same event to Kafka, creating duplicate messages.

2. How would you replace the polling-based outbox with CDC, and what latency improvement would you expect?

   Deploy Debezium connected to the PostgreSQL WAL. It streams row changes from the `outbox` table to Kafka within ~50 ms of the commit, eliminating the 500 ms polling interval and the DB polling load. The trade-off is operational complexity (Debezium cluster, connector configuration, schema compatibility).

3. If invoice-svc processes an `order.created` event, charges the customer, then crashes before committing the offset, what happens on restart?

   The consumer reads the uncommitted offset and redelivers the `order.created` event. Invoice-svc will attempt to charge the customer a second time. The fix is idempotent processing: store the order ID in Redis with `SET NX EX 86400` before charging; if the key already exists, skip the charge and commit the offset.

4. How would you scale this system to handle 500 k orders/hour?

   Increase the `orders` topic to 30 partitions. Scale invoice-svc, inventory-svc, and notification-svc to 10 replicas each (one per 3 partitions). Scale the FastAPI app to 10 replicas. At 500 k orders/hour (~139 msg/s), a 30-partition topic with 10 consumers per group gives each consumer ~14 msg/s — well within a single-process async Python consumer's capacity (~500–2 k msg/s for I/O-bound handlers).
