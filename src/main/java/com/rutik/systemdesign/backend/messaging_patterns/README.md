# Messaging Patterns

## 1. Concept Overview

Messaging patterns solve the fundamental challenges of reliable asynchronous communication between services: how to ensure a message is published exactly when a business operation completes, how to avoid processing the same message twice, how to handle messages that repeatedly fail processing, and how to evolve message schemas without breaking consumers. The outbox pattern, transactional inbox, dead letter queue, and schema evolution strategies address these challenges systematically.

---

## 2. Intuition

Imagine a bank that mails a withdrawal receipt only after the money is debited. If the teller sends the mail before debiting, the mail may go out but the debit may fail. If the teller debits first and then the mail system is down, the customer gets no receipt. The outbox pattern solves this: the teller writes the withdrawal AND a note to "send receipt" in the same ledger entry. A separate mail clerk reads the ledger and sends the mail. The mail and the debit are now atomic.

---

## 3. Core Principles

- **Atomicity between state change and event publication**: never publish an event outside the transaction that changes business state
- **At-least-once delivery is the default**: design consumers to be idempotent — processing the same message twice must produce the same result as processing it once
- **Poison pills are inevitable**: some messages will always fail; the system must handle them without blocking healthy messages
- **Schema evolution is continuous**: consumers and producers deploy independently; schemas must evolve backward-compatibly

---

## 4. Types / Architectures / Strategies

**Outbox implementation approaches**:
- **Polling relay**: a scheduled job polls the `outbox_events` table for unpublished events and publishes them; simple but introduces polling latency (typically 1-10 seconds)
- **CDC (Change Data Capture) via Debezium**: reads the database's transaction log (WAL/binlog) and emits change events; near-real-time (< 100ms), no polling overhead

**Inbox/deduplication approaches**:
- **Idempotency key table**: store processed message IDs; INSERT before processing, skip if duplicate key
- **Conditional processing**: check preconditions before processing (e.g., only process if order is in PENDING status); inherently idempotent for many domain operations
- **Natural idempotency**: some operations are naturally idempotent (SET status = 'SHIPPED' is safe to repeat; INCREMENT quantity by 1 is NOT)

**Dead letter queue strategies**:
- **Exponential backoff**: retry immediately, then after 1s, 2s, 4s, 8s... until max retries; then move to DLQ
- **Separate DLQ per source**: avoids DLQ processing from one topic affecting another
- **DLQ consumer**: monitoring, alerting, root cause analysis, manual replay after fix

---

## 5. Architecture Diagrams

```
Outbox Pattern (Polling Relay)
================================

[Order Service]
    |
    | @Transactional
    +--- INSERT INTO orders (id, status, ...) VALUES (...)
    +--- INSERT INTO outbox_events (aggregate_id, event_type, payload) VALUES (...)
    |    (same DB transaction — atomic)
    |
    v
[Outbox Relay] (scheduled every 500ms)
    |--- SELECT * FROM outbox_events WHERE published_at IS NULL LIMIT 100
    |--- FOR EACH event: publish to Kafka topic
    |--- UPDATE outbox_events SET published_at = NOW() WHERE id = event.id
    |
    v
[Kafka Topic: order-events]
    |
    v
[Downstream Consumers]


Outbox Pattern (CDC with Debezium)
====================================

[PostgreSQL WAL]
    |--- Logical replication slot
    v
[Debezium Connector]
    |--- Reads INSERT/UPDATE/DELETE from outbox_events table
    |--- Transforms: route to Kafka topic based on aggregate_type column
    v
[Kafka Topic: orders] (near-real-time, < 100ms)
    |
    v
[Downstream Consumers]


Transactional Inbox (Deduplication)
=====================================

[Kafka Consumer]
    |
    | @Transactional
    +--- INSERT INTO inbox_idempotency (message_id, processed_at)
    |    ON CONFLICT DO NOTHING
    |    -- if duplicate: returns 0 rows affected, skip
    |
    +--- IF rows_affected == 1:
    |      process the message (business logic)
    |      update application state
    |
    COMMIT (idempotency record + business state in same transaction)
```

---

## 6. How It Works — Detailed Mechanics

### Outbox Table Schema

```sql
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,   -- "Order", "Payment"
    aggregate_id    VARCHAR(36) NOT NULL,    -- aggregate root ID
    event_type      VARCHAR(200) NOT NULL,   -- "OrderCreated", "PaymentProcessed"
    payload         JSONB NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}',
    occurred_on     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ                          -- NULL = not yet published
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(occurred_on) WHERE published_at IS NULL;
```

### Outbox Pattern — JPA Implementation

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional  // both saves in ONE transaction — atomic
    public Order createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
            .id(UUID.randomUUID())
            .userId(request.getUserId())
            .status(OrderStatus.PENDING)
            .totalAmount(request.getTotalAmount())
            .build();

        orderRepository.save(order);

        // Outbox event saved in SAME transaction
        OutboxEvent event = OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(order.getId().toString())
            .eventType("OrderCreated")
            .payload(serializeToJson(new OrderCreatedEvent(
                order.getId(), order.getUserId(), order.getTotalAmount()
            )))
            .build();

        outboxEventRepository.save(event);

        return order;
        // Transaction commits: both order AND outbox event are persisted
        // If Kafka is down, order is still saved; relay will publish when Kafka recovers
    }
}
```

### Outbox Polling Relay

```java
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 500)  // every 500ms
    @Transactional
    public void relayOutboxEvents() {
        List<OutboxEvent> pending = outboxEventRepository
            .findTop100ByPublishedAtIsNullOrderByOccurredOnAsc();

        for (OutboxEvent event : pending) {
            String topic = resolveKafkaTopic(event.getAggregateType());
            String partitionKey = event.getAggregateId(); // same aggregate = same partition = ordered

            kafkaTemplate.send(topic, partitionKey, event.getPayload())
                .addCallback(
                    result -> markPublished(event),
                    failure -> log.error("Failed to publish event {}: {}", event.getId(), failure.getMessage())
                );
        }
    }

    private void markPublished(OutboxEvent event) {
        event.setPublishedAt(Instant.now());
        outboxEventRepository.save(event);
    }

    private String resolveKafkaTopic(String aggregateType) {
        return switch (aggregateType) {
            case "Order" -> "order-events";
            case "Payment" -> "payment-events";
            default -> throw new IllegalArgumentException("Unknown aggregate type: " + aggregateType);
        };
    }
}
```

### Debezium CDC Connector Configuration

```json
{
  "name": "outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "debezium",
    "database.password": "${file:/secrets/postgres-password}",
    "database.dbname": "orderdb",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_outbox_slot",
    "table.include.list": "public.outbox_events",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}-events",

    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "io.confluent.connect.avro.AvroConverter",
    "value.converter.schema.registry.url": "http://schema-registry:8081"
  }
}
```

### Transactional Inbox — Consumer with Deduplication

```java
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final InboxRepository inboxRepository;
    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-events", groupId = "inventory-consumer-group")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        String messageId = record.headers().lastHeader("messageId") != null
            ? new String(record.headers().lastHeader("messageId").value())
            : record.topic() + "-" + record.partition() + "-" + record.offset();

        // Attempt to insert deduplication record
        boolean isNew = inboxRepository.insertIfNotExists(messageId);

        if (!isNew) {
            log.info("Duplicate message {} - skipping", messageId);
            return; // idempotent: already processed
        }

        // Process the message (in same transaction as inbox record)
        OrderCreatedEvent event = deserialize(record.value(), OrderCreatedEvent.class);
        inventoryService.reserveStock(event.getOrderId(), event.getItems());
        // Both inboxRepository insert AND inventory update commit atomically
    }
}
```

```sql
-- Inbox table
CREATE TABLE inbox_idempotency (
    message_id   VARCHAR(200) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Repository method
INSERT INTO inbox_idempotency (message_id)
VALUES (:messageId)
ON CONFLICT (message_id) DO NOTHING
RETURNING message_id;
-- Returns row if inserted (new), returns empty if duplicate
```

### Dead Letter Queue (DLQ) — Kafka Configuration

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Dead letter publishing with retry
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(
                    record.topic() + ".DLT",  // dead-letter topic name convention
                    record.partition()
                )
            ),
            new FixedBackOff(1000L, 3L)  // retry 3 times with 1s delay
        );

        // Non-retryable exceptions — go straight to DLT
        errorHandler.addNotRetryableExceptions(
            JsonParseException.class,       // bad payload, retry won't help
            IllegalArgumentException.class  // invalid data, retry won't help
        );

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
```

### Message Schema Evolution with Avro

```json
// v1: OrderCreatedEvent
{
  "type": "record",
  "name": "OrderCreatedEvent",
  "namespace": "com.example.events",
  "fields": [
    {"name": "orderId", "type": "string"},
    {"name": "userId", "type": "string"},
    {"name": "totalAmount", "type": "double"}
  ]
}

// v2: Added optional "currency" field (BACKWARD compatible — v1 reader can read v2 data)
{
  "type": "record",
  "name": "OrderCreatedEvent",
  "namespace": "com.example.events",
  "fields": [
    {"name": "orderId", "type": "string"},
    {"name": "userId", "type": "string"},
    {"name": "totalAmount", "type": "double"},
    {"name": "currency", "type": ["null", "string"], "default": null}
  ]
}
```

```yaml
# Schema Registry compatibility mode
# Set per-subject or globally

# BACKWARD: new schema reads old data (deploy consumers first, then producers)
# FORWARD:  old schema reads new data (deploy producers first, then consumers)
# FULL:     both directions (safest, most restrictive)
# NONE:     no compatibility checking (dangerous in production)

# Set subject-level compatibility:
curl -X PUT http://schema-registry:8081/config/order-events-value \
  -H "Content-Type: application/json" \
  -d '{"compatibility": "BACKWARD"}'
```

---

## 7. Real-World Examples

- **Uber**: outbox pattern for all trip lifecycle events; Debezium CDC from MySQL to Kafka; processes billions of events per day
- **Zalando**: open-sourced their outbox-based approach (Nakadi event bus); all microservice communication via durable events with schema registry
- **Confluent**: Schema Registry as a standard component in Kafka deployments; Avro evolution used by thousands of companies for inter-service contracts
- **Amazon**: SQS dead letter queues built into the platform; SQS + SNS fan-out for pub/sub at scale across AWS services

---

## 8. Tradeoffs

| Pattern | Pros | Cons |
|---------|------|------|
| Polling relay outbox | Simple, no extra infrastructure | Polling latency (500ms-1s), additional DB load |
| CDC outbox (Debezium) | Near-real-time, no polling | Requires replication slot, additional Debezium infra |
| Transactional inbox | Strong deduplication guarantee | Extra DB table, INSERT per message |
| Conditional dedup | No extra table | Only works for naturally idempotent operations |
| Exponential backoff retry | Handles transient failures | Delays for genuinely unprocessable messages |
| DLQ | Isolates bad messages | Requires DLQ monitoring and replay tooling |

| Broker | Throughput | Ordering | Replay | Routing | Best For |
|--------|-----------|---------|--------|---------|---------|
| Kafka | 1M+ msg/s | Per-partition | Yes (offset) | By partition key | Event streaming, audit log, high throughput |
| RabbitMQ | ~50K msg/s | Per queue | Limited | Complex (exchanges) | Task queues, RPC, complex routing |
| SQS | Elastic | FIFO queues | No (visibility timeout) | Topics via SNS | Serverless, AWS-native, decoupled tasks |

---

## 9. When to Use / When NOT to Use

Use the outbox pattern whenever a service must publish an event as a side effect of a database transaction. Any direct `kafkaTemplate.send()` outside a transaction creates a dual-write problem — either the DB write or the Kafka write may succeed while the other fails.

Use the transactional inbox whenever downstream message processing must be exactly-once (payment processing, inventory decrement, financial ledger updates). For idempotent operations like updating a cache or sending a notification, simpler deduplication (check-then-act) may be sufficient.

Use Kafka when: you need replay capability, high throughput (> 50K msg/s), ordered processing per entity, or log-based event streaming. Use RabbitMQ when: you need complex routing (header-based, topic patterns), low latency message delivery, or per-message TTL. Use SQS when: you are fully on AWS, need serverless-friendly messaging, or want managed FIFO ordering without Kafka operational overhead.

---

## 10. Common Pitfalls

**Publishing events outside the transaction (dual-write)**:
```java
// BROKEN: kafkaTemplate.send() is OUTSIDE the @Transactional boundary
// The DB commit and Kafka publish are two separate operations
// If Kafka is down at send time, the event is lost permanently
@Transactional
public Order createOrder(CreateOrderRequest request) {
    Order order = orderRepository.save(buildOrder(request));
    kafkaTemplate.send("order-events", order.getId().toString(), toJson(order)); // WRONG
    return order;
}

// FIX: use the outbox pattern — insert event into outbox table within the same transaction
@Transactional
public Order createOrder(CreateOrderRequest request) {
    Order order = orderRepository.save(buildOrder(request));
    outboxEventRepository.save(buildOutboxEvent(order)); // SAME transaction
    return order; // relay will publish to Kafka asynchronously
}
```

**Missing DLQ monitoring**: A team added a DLQ but never monitored it. Over 3 months, 12,000 unprocessable messages accumulated (a bug in the JSON schema had been deployed to production). When the bug was discovered, replaying 12,000 messages caused a spike that overwhelmed downstream services. Fix: alert when DLQ depth > 0; review DLQ messages daily; replay in controlled batches.

**Outbox table growing unbounded**: The outbox relay ran but forgot to delete or mark processed events. After 6 months, the `outbox_events` table had 50 million rows. The `SELECT ... WHERE published_at IS NULL` query did a sequential scan despite the partial index (index was not maintained when rows were only updated, not deleted). Fix: either DELETE processed rows after 7 days, or verify the partial index on `WHERE published_at IS NULL` is used by EXPLAIN.

**Using wrong Avro compatibility mode**: A team set compatibility to NONE. A producer added a new required field without a default. All existing consumers that did not have the new schema version crashed on deserialization. Fix: always use BACKWARD compatibility (consumers can read newer schema with defaults for missing fields), deploy consumers before producers.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Debezium | CDC from PostgreSQL/MySQL WAL to Kafka |
| Kafka Connect | Source/sink connectors framework |
| Schema Registry (Confluent) | Avro/Protobuf/JSON schema versioning |
| Spring Kafka | KafkaTemplate, @KafkaListener, error handling |
| Testcontainers Kafka | Integration testing with real Kafka |
| RabbitMQ | AMQP broker with exchange-based routing |
| AWS SQS / SNS | Managed message queue and pub/sub |
| KEDA | Kubernetes autoscaler based on Kafka consumer lag |

---

## 12. Interview Questions with Answers

**Q: What is the dual-write problem and how does the outbox pattern solve it?**
The dual-write problem occurs when a service must write to two systems atomically — for example, update a database and publish a Kafka message. These two writes cannot be wrapped in a single ACID transaction spanning different systems. If the DB write succeeds but Kafka publish fails, the event is lost (downstream services miss the update). If Kafka publish succeeds but DB write fails (or rolls back), a phantom event is published for a transaction that never happened. The outbox pattern solves this by writing the event to an `outbox_events` table in the same DB transaction as the business data. A separate relay (polling or CDC) then reads the outbox and publishes to Kafka. The relay can retry until success — events are published at least once after the DB transaction commits.

**Q: What is exactly-once delivery and how do you achieve it with Kafka?**
Exactly-once delivery means each message is processed precisely once, producing the same result as if processed once — no duplicates, no losses. Achieving this requires coordination at producer, broker, and consumer levels. Producer: `enable.idempotence=true` (sequence numbers prevent duplicates from retries), transactional API for atomic multi-partition writes. Broker: `acks=all`, `min.insync.replicas=2`. Consumer: `isolation.level=read_committed` (skip uncommitted transactional messages). Application level: transactional inbox with deduplication table. Full end-to-end exactly-once within Kafka Streams is achievable with `processing.guarantee=exactly_once_v2`. Across service boundaries (Kafka + external DB), exactly-once requires the transactional outbox + inbox pattern.

**Q: What is a dead letter queue and how should you handle messages in it?**
A DLQ (dead letter topic/queue) receives messages that have exhausted all retry attempts. Messages end up in the DLQ because they are unprocessable: bad payload format, invalid data, downstream service unavailable for too long, or application bugs. DLQ consumers should: (1) log and alert on every DLQ message — DLQ depth > 0 is always an alert condition, (2) analyze root cause — is it a bad message, an application bug, or an infrastructure issue, (3) after fixing the root cause, replay DLQ messages back to the original topic in controlled batches. Never delete DLQ messages until you understand why they failed. Keep DLQ messages for at least 7 days.

**Q: What is the transactional inbox pattern and when is it necessary?**
The transactional inbox stores a record of processed message IDs in the same database as the application state, with the deduplication INSERT and business logic update in the same transaction. If the consumer crashes after processing but before committing the Kafka offset, Kafka will redeliver the message. Without an inbox, the business logic runs twice (double charge, double inventory decrement). With the inbox, the second processing attempt finds the message_id already in the inbox table and skips processing. It is necessary for non-idempotent operations: financial transactions, inventory decrements, counters, and any operation where "process twice" has different results than "process once."

**Q: What is Avro BACKWARD compatibility and why should you default to it?**
BACKWARD compatibility means a newer schema version can read data written with an older schema version. When you add an optional field with a default value, new consumers can read old messages (the field defaults to null/0) and new messages (the field has a value). Deploy consumers first with the new schema (they can read old messages with defaults), then deploy producers that start writing the new field. This is the standard deployment order: consumers before producers. FORWARD compatibility means the opposite (old schema reads new data) — needed when you must deploy producers first. FULL is both directions and is the safest but most restrictive.

**Q: What is the difference between Kafka and RabbitMQ for event-driven architectures?**
Kafka is log-based: messages are retained for a configured duration (default 7 days) and consumers maintain their own offset. Any consumer can replay from any offset. Kafka preserves order within a partition. Throughput is very high (1M+ msg/s per cluster). Kafka is ideal for event streaming, audit logs, and scenarios requiring replay. RabbitMQ is queue-based: messages are removed from the queue once acknowledged. Complex routing is available (topic exchanges, header exchanges). Throughput is lower (~50K msg/s) but latency is lower (push-based vs poll-based). RabbitMQ is better for task queues, RPC patterns, and complex routing requirements. Use Kafka when you need replay and high throughput; use RabbitMQ when you need complex routing and low latency task distribution.

**Q: How do you handle message ordering with Kafka when multiple consumers process in parallel?**
Kafka guarantees ordering within a partition, not across partitions. To maintain order for a specific entity (e.g., all events for order-123), always use the entity ID as the partition key: `kafkaTemplate.send(topic, orderId.toString(), payload)`. All events for the same order will go to the same partition, processed in order by the same consumer thread. For consumers, within a partition, messages are processed sequentially. Across partitions, messages are processed in parallel. Never change the partition count of an existing topic — it changes the hash mapping and breaks ordering guarantees for existing consumers until they process through the re-partitioned data.

**Q: What happens when the Debezium CDC connector loses its replication slot?**
PostgreSQL creates a replication slot for Debezium to read WAL changes. If Debezium is down for too long, PostgreSQL cannot clean up WAL files because Debezium's slot is holding them. This causes disk space to fill up. If disk fills, PostgreSQL stops accepting writes — a production outage. Mitigations: (1) set `max_slot_wal_keep_size` in PostgreSQL to limit WAL retention per slot; (2) monitor replication slot lag — alert when lag exceeds 1GB; (3) if Debezium is down for more than a few hours, manually drop the slot and let Debezium reinitialize (this causes the outbox relay approach to be safer as a fallback). Always monitor `pg_replication_slots` in production.

---

## 13. Best Practices

- Always use partition key = aggregate ID for ordered event processing per entity
- Purge processed outbox records after 7 days to prevent unbounded table growth
- Use separate DLQ topics per source topic (not a shared catch-all DLQ)
- Monitor outbox table depth and DLQ depth as operational metrics in Grafana
- Implement the outbox relay with idempotent Kafka producer (`enable.idempotence=true`)
- Use `BACKWARD` schema compatibility in Schema Registry as the default
- Test consumer restart and redelivery scenarios in integration tests (Testcontainers)
- Keep outbox events small — store only the event envelope; if large payloads are needed, store in S3 and reference the URL
- Document which services consume each event in an event catalog or API documentation

---

## 14. Case Study

**Problem**: An inventory service processed `OrderCreated` events from Kafka. During a deployment, the service was restarted mid-processing. Kafka redelivered the last uncommitted batch. Without deduplication, the inventory was decremented twice for the same order. Customer orders were fulfilled but the inventory count went negative.

**Fix applied**:
1. Added `inbox_idempotency` table with `message_id PRIMARY KEY`
2. Wrapped Kafka consumer in `@Transactional` with inbox INSERT + inventory decrement in same transaction
3. Added monitoring: alert on `inbox_idempotency` table size growing faster than expected (indicates duplicates)
4. Added integration test: process same message twice, assert inventory decremented exactly once

**Root cause analysis also found**: The OrderService was using `kafkaTemplate.send()` outside the `@Transactional` boundary. If the DB commit succeeded but Kafka publish failed, the event was lost. Fixed with the outbox pattern — now events are published at-least-once, consumer handles deduplication for exactly-once semantics.
