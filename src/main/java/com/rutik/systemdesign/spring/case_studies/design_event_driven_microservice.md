# Event-Driven Order Processing Microservice with Kafka and Saga Pattern

## Problem Statement

Design an order processing microservice that uses event-driven architecture to coordinate a distributed transaction across four services: Order, Payment, Inventory, and Fulfillment. The system must:

- Process 50,000 orders per hour with peak spikes of 5x
- Guarantee no order is lost even if a downstream service is temporarily unavailable
- Handle partial failures: if payment succeeds but inventory is unavailable, compensate by refunding the payment
- Ensure each event is processed exactly once (no duplicate charges, no double-shipping)
- Provide full auditability — every state transition must be logged

Constraints: Spring Boot 3.x, Apache Kafka 3.x, PostgreSQL for the order service database, choreography-based Saga (no orchestrator service).

---

## Architecture Overview

```
 [Client]
    |
    v
[Order Service]
    |
    |---(1) INSERT order (PENDING) + INSERT outbox_events in same TX
    |
    v
[Outbox Publisher] ---- scheduled every 100ms ---->  [Kafka: order.created]
                                                           |
                    +--------------------------------------+
                    |                                      |
                    v                                      v
         [Payment Service]                     [Inventory Service]
         (consumes order.created)              (consumes order.created)
                    |                                      |
          publish payment.completed            publish inventory.reserved
          or payment.failed                    or inventory.failed
                    |                                      |
                    +---------- both events ------------->  [Order Service saga handler]
                                                               |
                                                     If both succeeded:
                                                     publish order.confirmed
                                                           |
                                                           v
                                                  [Fulfillment Service]
                                                  (consumes order.confirmed)
                                                           |
                                                  publish fulfillment.shipped
                                                           |
                                                           v
                                                  [Order Service]
                                                  (marks order SHIPPED)

 Compensation path:
   payment.completed + inventory.failed
        --> Order Service publishes payment.refund.requested
        --> Payment Service refunds and publishes payment.refunded
        --> Order Service marks order FAILED
```

---

## Key Design Decisions

### 1. Transactional Outbox Pattern over Direct Kafka Publish

Publishing directly to Kafka inside a database transaction is impossible — the two resources are separate transactional domains. If the database commits but the Kafka publish fails, the event is lost. If Kafka publishes but the database rolls back, a phantom event is in the topic. The Outbox pattern resolves this: the event is written to an `outbox_events` table in the same database transaction as the business entity update, making both atomic. A separate scheduled publisher then reads unpublished events and sends them to Kafka, marking them published only after the Kafka acknowledgment. The worst case is at-least-once delivery, not loss.

### 2. Choreography Saga over Orchestration

An orchestrator service requires its own high-availability deployment, becomes a bottleneck, and adds a network hop. Choreography distributes the saga logic across services — each service reacts to events and publishes the next event. The tradeoff is that the overall flow is harder to visualize from a single place. This is mitigated by having each service log state transitions with a correlation ID (order ID), so a distributed trace query can reconstruct the full saga history.

### 3. Idempotent Consumer Using Processed Events Table

Kafka at-least-once delivery means a consumer may receive the same event multiple times (during rebalances, consumer restarts, or network retries). Without idempotency, a payment could be charged twice. A `processed_events` table stores `(event_id, consumer_group, processed_at)`. Each consumer checks this table before processing and inserts atomically. If the insert fails with a unique constraint violation, the event is a duplicate and is acknowledged without reprocessing. This check and the business update happen in the same database transaction.

### 4. Dead Letter Topic with @RetryableTopic

Transient failures (database connection timeout, downstream HTTP call failure) should be retried with backoff, not sent immediately to a dead letter topic. Spring Kafka's `@RetryableTopic` handles this by routing failed messages to intermediate retry topics (`order.created-retry-1`, `order.created-retry-2`) with configurable delays (1s, 10s, 60s) before the final dead letter topic. This avoids blocking the main partition while retrying.

### 5. Kafka Transactions for Exactly-Once in the Outbox Publisher

The outbox publisher reads an event from the database and writes it to Kafka. To prevent the same event from being published twice (publisher crash between publish and marking as published), the publisher uses a Kafka `KafkaTransactionManager` coordinated with a `ChainedTransactionManager`. The database UPDATE (marking event as published) and the Kafka send happen in the same logical transaction using Spring's `@Transactional` with the chained manager.

---

## Implementation

### Domain Entity and Outbox Event

```java
package com.rutik.systemdesign.spring.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    private String customerId;
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    // JPA requires no-arg constructor
    protected Order() {}

    public static Order create(String customerId, BigDecimal totalAmount) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.totalAmount = totalAmount;
        order.status = OrderStatus.PENDING;
        order.createdAt = Instant.now();
        order.updatedAt = Instant.now();
        return order;
    }

    public void transitionTo(OrderStatus newStatus) {
        // Enforce valid state transitions
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition from " + status + " to " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
```

```java
package com.rutik.systemdesign.spring.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID aggregateId;
    private String aggregateType;
    private String eventType;

    @Column(columnDefinition = "jsonb")
    private String payload;

    private boolean published;
    private Instant createdAt;
    private Instant publishedAt;

    protected OutboxEvent() {}

    public static OutboxEvent of(UUID aggregateId, String aggregateType,
                                  String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateId = aggregateId;
        event.aggregateType = aggregateType;
        event.eventType = eventType;
        event.payload = payload;
        event.published = false;
        event.createdAt = Instant.now();
        return event;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public boolean isPublished() { return published; }
}
```

### Order Service — Writes Order and Outbox in One Transaction

```java
package com.rutik.systemdesign.spring.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxEventRepository,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional  // Both inserts happen atomically
    public Order createOrder(String customerId, BigDecimal totalAmount) throws Exception {
        Order order = Order.create(customerId, totalAmount);
        orderRepository.save(order);

        // Write outbox event in the SAME transaction
        String payload = objectMapper.writeValueAsString(Map.of(
            "orderId", order.getId().toString(),
            "customerId", order.getCustomerId(),
            "totalAmount", order.getTotalAmount().toString()
        ));

        OutboxEvent outboxEvent = OutboxEvent.of(
            order.getId(), "Order", "ORDER_CREATED", payload
        );
        outboxEventRepository.save(outboxEvent);

        return order;
    }

    @Transactional
    public void handlePaymentCompleted(String orderId) {
        Order order = orderRepository.findById(java.util.UUID.fromString(orderId))
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.transitionTo(OrderStatus.PAYMENT_CONFIRMED);
        orderRepository.save(order);
    }

    @Transactional
    public void handlePaymentFailed(String orderId) {
        Order order = orderRepository.findById(java.util.UUID.fromString(orderId))
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.transitionTo(OrderStatus.FAILED);
        orderRepository.save(order);
    }
}
```

### Transactional Outbox Publisher (Scheduled Poller)

```java
package com.rutik.systemdesign.spring.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository,
                                 KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 100)  // Poll every 100ms
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
            .findTop100ByPublishedFalseOrderByCreatedAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                String topic = resolveKafkaTopic(event.getEventType());
                // Use aggregateId as the Kafka message key for partition ordering per order
                kafkaTemplate.send(topic,
                                   event.getAggregateId().toString(),
                                   event.getPayload())
                             .get(); // Synchronous send — ensures Kafka ack before marking published

                event.markPublished();
                outboxEventRepository.save(event);

                log.debug("Published outbox event id={} type={} topic={}",
                          event.getId(), event.getEventType(), topic);
            } catch (Exception e) {
                // Log and continue — this event will be retried in the next poll cycle
                log.error("Failed to publish outbox event id={}: {}",
                          event.getId(), e.getMessage());
            }
        }
    }

    private String resolveKafkaTopic(String eventType) {
        return switch (eventType) {
            case "ORDER_CREATED"    -> "order.created";
            case "ORDER_CONFIRMED"  -> "order.confirmed";
            case "ORDER_FAILED"     -> "order.failed";
            case "PAYMENT_REFUND_REQUESTED" -> "payment.refund.requested";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
```

### Idempotent Kafka Consumer (Payment Events)

```java
package com.rutik.systemdesign.spring.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(ProcessedEventRepository processedEventRepository,
                                 OrderService orderService,
                                 ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "payment.completed",
        groupId = "order-service-payment-consumer",
        containerFactory = "ackKafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentCompleted(ConsumerRecord<String, String> record,
                                    Acknowledgment ack) throws Exception {
        String eventId = extractEventId(record);

        // Idempotency check — insert-or-ignore
        if (processedEventRepository.existsByEventIdAndConsumerGroup(
                eventId, "order-service-payment-consumer")) {
            log.warn("Duplicate event received, skipping eventId={}", eventId);
            ack.acknowledge();
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String orderId = payload.get("orderId").asText();

            orderService.handlePaymentCompleted(orderId);

            // Mark as processed in the same transaction as the business update
            processedEventRepository.save(
                new ProcessedEvent(eventId, "order-service-payment-consumer"));

            ack.acknowledge();
            log.info("Processed payment.completed for orderId={}", orderId);
        } catch (Exception e) {
            log.error("Failed to process payment.completed eventId={}: {}", eventId, e.getMessage());
            // Do NOT ack — Spring Kafka will retry according to RetryableTopic config
            throw e;
        }
    }

    private String extractEventId(ConsumerRecord<String, String> record) {
        // Event ID is carried in a Kafka header set by the publishing service
        var header = record.headers().lastHeader("event-id");
        if (header != null) {
            return new String(header.value());
        }
        // Fallback: use topic + partition + offset as a synthetic event ID
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }
}
```

### Dead Letter Topic Configuration with @RetryableTopic

```java
package com.rutik.systemdesign.spring.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final SagaCompensationService sagaCompensationService;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(ProcessedEventRepository processedEventRepository,
                                   SagaCompensationService sagaCompensationService,
                                   ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.sagaCompensationService = sagaCompensationService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 10, maxDelay = 60000),
        // Creates: inventory.failed, inventory.failed-retry-1, inventory.failed-retry-2,
        //          inventory.failed-retry-3, inventory.failed-dlt
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = "-dlt",
        include = {RetriableKafkaException.class}  // Only retry transient errors
    )
    @KafkaListener(topics = "inventory.failed", groupId = "order-service-inventory-consumer")
    @Transactional
    public void onInventoryFailed(ConsumerRecord<String, String> record) throws Exception {
        String eventId = extractEventId(record);

        if (processedEventRepository.existsByEventIdAndConsumerGroup(
                eventId, "order-service-inventory-consumer")) {
            log.warn("Duplicate event skipped eventId={}", eventId);
            return;
        }

        var payload = objectMapper.readTree(record.value());
        String orderId = payload.get("orderId").asText();
        String reason = payload.get("reason").asText();

        // Saga compensation: if inventory fails, trigger payment refund
        sagaCompensationService.compensateForInventoryFailure(orderId, reason);

        processedEventRepository.save(
            new ProcessedEvent(eventId, "order-service-inventory-consumer"));

        log.info("Triggered compensation for orderId={} reason={}", orderId, reason);
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        // Exhausted all retries — store in dead_letter_orders for manual review
        log.error("Message sent to DLT topic={} key={} value={}",
                  topic, record.key(), record.value());
        // In production: persist to dead_letter_events table + alert
    }

    private String extractEventId(ConsumerRecord<String, String> record) {
        var header = record.headers().lastHeader("event-id");
        if (header != null) return new String(header.value());
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }
}
```

### Saga Compensation Service

```java
package com.rutik.systemdesign.spring.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class SagaCompensationService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public SagaCompensationService(OrderRepository orderRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void compensateForInventoryFailure(String orderId, String reason) throws Exception {
        Order order = orderRepository.findById(UUID.fromString(orderId))
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // Only compensate if payment was already confirmed (otherwise nothing to refund)
        if (order.getStatus() == OrderStatus.PAYMENT_CONFIRMED) {
            order.transitionTo(OrderStatus.COMPENSATION_IN_PROGRESS);
            orderRepository.save(order);

            // Write refund event to outbox — atomically with the status update
            String refundPayload = objectMapper.writeValueAsString(Map.of(
                "orderId", orderId,
                "customerId", order.getCustomerId(),
                "amount", order.getTotalAmount().toString(),
                "reason", "inventory_unavailable: " + reason
            ));

            outboxEventRepository.save(OutboxEvent.of(
                order.getId(), "Order", "PAYMENT_REFUND_REQUESTED", refundPayload
            ));

            log.info("Scheduled payment refund for orderId={}", orderId);
        } else {
            // Payment never succeeded — just fail the order
            order.transitionTo(OrderStatus.FAILED);
            orderRepository.save(order);
            log.info("Marked order FAILED (payment not yet confirmed) orderId={}", orderId);
        }
    }
}
```

### Kafka Producer Configuration (Exactly-Once)

```java
package com.rutik.systemdesign.spring.order;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Exactly-once semantics: enable idempotent producer
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Strongest durability: wait for all in-sync replicas
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // Retry on transient failures
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        // Transactional ID for exactly-once across partitions
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-service-producer-1");

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### Kafka Consumer Configuration (Manual Acknowledgment)

```java
package com.rutik.systemdesign.spring.order;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Manual offset commit — only ack after successful processing
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "ackKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String>
            ackKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // MANUAL_IMMEDIATE: offset committed when Acknowledgment.acknowledge() is called
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3); // 3 consumer threads per listener
        return factory;
    }
}
```

---

## Spring Components Used

| Spring Component | Purpose |
|---|---|
| `@KafkaListener` | Subscribes to Kafka topics with configurable consumer group and container factory |
| `@RetryableTopic` | Creates retry and dead-letter topics with exponential backoff — no manual retry logic |
| `@DltHandler` | Handles messages that exhausted all retries in the dead-letter topic |
| `KafkaTemplate` | Sends messages to Kafka topics; used by `OutboxEventPublisher` |
| `@Transactional` | Ensures the outbox write and business entity update are atomic in one database TX |
| `@Scheduled` | Polls the `outbox_events` table every 100ms for unpublished events |
| `ConcurrentKafkaListenerContainerFactory` | Configures consumer container with manual ack mode and concurrency |
| `ProducerFactory` | Builds Kafka producers with exactly-once settings (idempotence, transactions) |
| `ContainerProperties.AckMode.MANUAL_IMMEDIATE` | Offsets are only committed after explicit `Acknowledgment.acknowledge()` call |
| `ApplicationRunner` | Used by outbox publisher bootstrap to verify Kafka connectivity at startup |

---

## Tradeoffs and Alternatives

### Outbox Pattern vs Dual Write

| Approach | Consistency | Complexity | Latency |
|---|---|---|---|
| Transactional Outbox (chosen) | Strong — DB TX includes event | High — needs poller | +100ms (poll interval) |
| Dual write (DB + Kafka in sequence) | Weak — can lose event | Low | Zero |
| Kafka Streams with changelog | Strong — event sourcing | Very high | Zero |
| CDC (Debezium) | Strong — reads DB WAL | Medium — ops overhead | Low |

The outbox pattern was chosen over CDC/Debezium to keep the infrastructure simpler (no Debezium connector deployment). The 100ms polling latency is acceptable for order processing. A CDC-based approach would be preferred if the polling overhead became a bottleneck at very high write volumes.

### Choreography Saga vs Orchestration

| Approach | Centralized visibility | Coupling | Failure handling |
|---|---|---|---|
| Choreography (chosen) | Low | Low | Distributed — each service compensates |
| Orchestration (Saga orchestrator) | High | Higher | Centralized — orchestrator issues compensations |

Choreography was chosen to keep services independent. A payment service should not need to know the order service's URL. The tradeoff is that tracing the full saga requires correlating events by `orderId` across service logs.

### Exactly-Once vs At-Least-Once

Exactly-once delivery using Kafka transactions adds ~10% overhead on the producer side. For financial operations (payments), the idempotent consumer approach combined with a `processed_events` table was chosen because it provides idempotency even when messages are replayed from a different offset (which Kafka transactions alone do not protect against at the consumer side).

---

## Interview Discussion Points

**Q: What happens if the outbox publisher crashes between sending the Kafka message and marking the event as published?**

A: The event will be sent again on the next poll cycle, producing a duplicate message in Kafka. This is why consumers must be idempotent. The `processed_events` table unique constraint on `(event_id, consumer_group)` prevents double-processing. This is why the combination of the outbox pattern and idempotent consumers is required — neither alone is sufficient.

**Q: How do you prevent the outbox_events table from growing unboundedly?**

A: A separate cleanup job runs daily (via `@Scheduled`) and deletes rows where `published = true AND published_at < NOW() - INTERVAL '7 days'`. Partition the table by `created_at` using PostgreSQL table partitioning so the cleanup is a partition drop rather than a DELETE scan, which avoids table bloat and lock contention.

**Q: How does the Saga handle a situation where both payment and inventory fail simultaneously?**

A: In the choreography model, both `payment.failed` and `inventory.failed` events arrive. The order service's consumers for both topics are idempotent and independent. Each handler checks the current order status: if status is already FAILED, the second event is a no-op. The first consumer to arrive wins the status transition. No compensation is needed for `payment.failed` because no money was charged. The order is simply marked FAILED.

**Q: What is the maximum throughput of the outbox polling approach?**

A: With a 100ms poll interval and a batch of 100 events per poll, the theoretical maximum is 1,000 events/second per application instance. In practice, the Kafka `kafkaTemplate.send().get()` synchronous call adds 5–10ms per message. To increase throughput: (1) send messages asynchronously in parallel using `CompletableFuture`, (2) use Kafka producer batching with `linger.ms=5`, (3) increase the batch size, (4) run multiple application instances (each will poll non-overlapping events if using `SELECT ... FOR UPDATE SKIP LOCKED`).

**Q: How do you implement SELECT FOR UPDATE SKIP LOCKED to prevent multiple outbox pollers from processing the same event?**

A: In the `OutboxEventRepository`, use a native query with `FOR UPDATE SKIP LOCKED`:

```java
@Query(value = "SELECT * FROM outbox_events WHERE published = false ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
List<OutboxEvent> findAndLockUnpublished(@Param("limit") int limit);
```

This allows multiple application instances to poll concurrently without processing the same event. PostgreSQL's `SKIP LOCKED` immediately skips rows that another transaction has locked, rather than waiting.

**Q: How would you handle schema evolution — adding a new field to an event payload without breaking consumers?**

A: Use JSON for event payloads (as implemented) and follow consumer-driven contract testing. Consumers must ignore unknown fields (Jackson's `FAIL_ON_UNKNOWN_PROPERTIES = false` default). Producers may add new fields freely but must never remove or rename existing fields without a coordinated two-phase migration: (1) add the new field as optional, deploy all consumers that handle it, (2) once all consumers are deployed, make the field required in producers. Schema Registry with Avro and backward compatibility enforcement provides stronger guarantees at the cost of additional infrastructure.
