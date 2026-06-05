# Spring Messaging

## 1. Concept Overview

Spring Messaging provides a unified abstraction layer over message-oriented middleware (MOM), enabling applications to send, receive, and process messages asynchronously across different transport technologies. The Spring ecosystem covers Kafka (via spring-kafka), RabbitMQ (via spring-amqp), and a binder-agnostic abstraction (Spring Cloud Stream). Additionally, Spring's @Async mechanism enables within-process asynchronous execution, and Spring WebSocket enables full-duplex, real-time communication over STOMP.

Asynchronous messaging decouples producers from consumers temporally and spatially — the producer does not wait for the consumer, and the consumer does not need to be running when the message is sent. This is the foundational property that enables resilient, scalable distributed systems.

Key capabilities covered in this module:

- Apache Kafka integration: KafkaTemplate, @KafkaListener, transactions, error handling
- RabbitMQ integration: RabbitTemplate, @RabbitListener, DLX, TTL, priority queues
- Spring Cloud Stream: binder abstraction over Kafka and RabbitMQ with functional programming model
- @Async: thread-pool-backed async method execution within a single JVM
- WebSocket: STOMP-over-WebSocket for real-time bidirectional communication
- Reliability patterns: Outbox pattern, exactly-once semantics, dead-letter queues

---

## 2. Intuition

One-line analogy: A messaging system is like a postal service — the sender drops a letter in the mailbox without knowing when or even if the recipient will pick it up, and the postal service (broker) handles routing, retry, and dead-letter handling.

Mental model: Think of a message broker as a reliable buffer between two processes. The producer's job ends at "write to the broker." The consumer's job starts at "read from the broker." The broker's job is to durably hold messages until they can be delivered. This separation lets you scale, upgrade, or restart consumers independently of producers.

Why it matters: In a monolith, a service call is synchronous — if the downstream service is slow or down, the caller is blocked or fails. With messaging, the caller deposits work into a durable queue and returns immediately. The downstream service processes the work when it is ready. This is how high-throughput systems decouple request handling from work execution.

Key insight: The hardest problem in messaging is not sending messages — it is delivery guarantees. At-most-once (fire and forget), at-least-once (acknowledge after processing), and exactly-once (idempotent consumer + transactional producer) each make different trade-offs between performance and correctness.

---

## 3. Core Principles

**Decoupling**: Producers and consumers are unaware of each other's implementation, location, or availability. They communicate only through the broker contract (topic/queue name and message schema).

**Durability**: Messages are persisted in the broker until acknowledged. This prevents data loss during consumer restarts.

**Delivery semantics**:
- At-most-once: message is sent once; if lost in transit, it is not retried. Fast but lossy.
- At-least-once: message is retried until acknowledged. Consumers must be idempotent.
- Exactly-once: achieved in Kafka with transactional producers + read_committed consumers, or in general with the Outbox pattern.

**Backpressure**: Consumers must not be overwhelmed. Kafka's max.poll.records and AMQP's prefetch count are the primary levers.

**Ordering**: Kafka preserves ordering within a partition. RabbitMQ preserves ordering within a single queue with a single consumer. Parallelism breaks ordering guarantees.

**Idempotency**: Any at-least-once system can deliver duplicates. Consumer logic must produce the same result for duplicate messages (use message ID deduplication, database unique constraints, or idempotency keys).

---

## 4. Types / Architectures / Strategies

### Kafka vs RabbitMQ — fundamental model difference

| Dimension | Kafka | RabbitMQ |
|-----------|-------|----------|
| Model | Log-based (append-only partitioned log) | Queue-based (message deleted on ack) |
| Retention | Time/size-based (default 7 days) | Until consumed (or TTL) |
| Replay | Yes — seek to any offset | No (once consumed, gone) |
| Ordering | Per-partition | Per-queue with 1 consumer |
| Throughput | Very high (millions/s per partition) | High (tens of thousands/s) |
| Routing | Topic + partition key | Exchange routing (direct, topic, fanout, headers) |
| Consumer model | Pull (poll loop) | Push (broker delivers) |
| Use case | Event streaming, audit log, analytics | Task queues, RPC, complex routing |

### Kafka Delivery Modes

- **Auto-commit (enable.auto.commit=true)**: offsets committed on a schedule. Risk: message processed but not committed if crash between processing and commit. Also risk: offset committed before processing completes if commit interval fires early.
- **Manual commit (enable.auto.commit=false)**: application controls when to commit. Use `Acknowledgment.acknowledge()` after successful processing.
- **Batch commit**: commit once per poll batch.

### RabbitMQ Acknowledgement Modes

- **AUTO**: broker removes message immediately on delivery. At-most-once.
- **MANUAL**: consumer explicitly calls `channel.basicAck()` or `channel.basicNack()`. At-least-once.
- **NONE**: equivalent to AUTO in Spring AMQP.

### Spring Cloud Stream Functional Model

Replaces the annotation-based (`@StreamListener`) model. Applications expose beans of type `Consumer<T>`, `Supplier<T>`, or `Function<T,R>`. The binder maps these to topics/queues automatically based on bean name and configuration.

### @Async Execution

Methods annotated with @Async are invoked on a separate thread from a configured `TaskExecutor`. The return type must be `void` or `Future<T>` / `CompletableFuture<T>`. @EnableAsync on a @Configuration class activates the proxy machinery.

### WebSocket / STOMP

STOMP (Simple Text Oriented Messaging Protocol) is a frame-based protocol over WebSocket. It provides subscribe/send semantics. Spring's `@MessageMapping` routes STOMP messages to handler methods, and `SimpMessagingTemplate` pushes messages from the server to subscribed clients.

---

## 5. Architecture Diagrams

### Kafka Producer / Consumer Flow in Spring

```
+-------------------+          +--------------------+          +--------------------+
|  Spring Service   |          |   Apache Kafka     |          |  @KafkaListener    |
|                   |          |   Broker Cluster   |          |  (Consumer Group)  |
|  KafkaTemplate    | -------> | Topic: orders      | -------> |  partition 0  [T1] |
|  .send(topic, k,v)|          |   partition 0      |          |  partition 1  [T2] |
|                   |          |   partition 1      |          |  partition 2  [T3] |
+-------------------+          |   partition 2      |          +--------------------+
                                +--------------------+
                                        |
                                  on processing failure
                                        |
                                        v
                               +--------------------+
                               | Dead Letter Topic  |
                               | orders.DLT         |
                               +--------------------+
```

### Exactly-Once Kafka Flow

```
  Producer                  Broker                  Consumer
     |                        |                        |
     |-- BEGIN TX ----------->|                        |
     |-- send(msg)  --------->| [msg: uncommitted]     |
     |-- COMMIT TX ---------->| [msg: committed]       |
     |                        |                        |
     |                        |<-- poll() -------------|
     |                        |-- msg (committed) ---->|
     |                        |                        | process
     |                        |<-- commit offset ------|
```

### RabbitMQ DLX Flow

```
  Publisher --> Exchange --> Queue (x-dead-letter-exchange=dlx)
                                  |
                              [NACK or TTL expired or queue full]
                                  |
                                  v
                             DLX Exchange --> Dead Letter Queue
                                                    |
                                              [Manual inspection
                                               or retry consumer]
```

### Spring Cloud Stream Functional

```
  Kafka Topic [orders-in]
         |
         v
  +-------------------+
  |  Function<Order,  |  <- Spring Cloud Stream binder
  |  Receipt> bean    |     maps input binding to topic
  +-------------------+
         |
         v
  Kafka Topic [orders-out]
```

### @Async Thread Pool

```
  Caller Thread
       |
       | @Async method call
       |
       v
  ThreadPoolTaskExecutor
  +----------------------------------+
  |  corePoolSize: 10                |
  |  maxPoolSize:  50                |
  |  queueCapacity: 100              |
  |  [T1][T2][T3]...[T10] (running)  |
  |  [Q1][Q2]...[Q100]    (queued)   |
  +----------------------------------+
       |
       | Returns CompletableFuture<T> immediately to caller
```

### WebSocket STOMP Architecture

```
  Browser                      Spring Backend
     |                               |
     |-- WS Upgrade ----------------->|
     |<- 101 Switching Protocols -----|
     |                               |
     |-- STOMP CONNECT -------------->|
     |-- SUBSCRIBE /topic/prices ---->|  SimpleBroker or
     |                               |  External STOMP Broker
     |                               |  (RabbitMQ STOMP plugin)
     |<-- MESSAGE /topic/prices ------|  SimpMessagingTemplate
     |<-- MESSAGE /topic/prices ------|  .convertAndSend(...)
```

---

## 6. How It Works — Detailed Mechanics

### KafkaTemplate.send()

```java
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, OrderEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // For exactly-once: enable idempotent producer
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "tx-orders-");
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, OrderEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

@Service
public class OrderPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrder(OrderEvent event) {
        // send() is non-blocking; returns CompletableFuture
        CompletableFuture<SendResult<String, OrderEvent>> future =
            kafkaTemplate.send("orders", event.getOrderId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send order {}: {}", event.getOrderId(), ex.getMessage());
            } else {
                log.info("Sent order {} to partition {} offset {}",
                    event.getOrderId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}
```

### @KafkaListener — Configuration

```java
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, OrderEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Disable auto-commit for manual control
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Limit records per poll — critical for back-pressure
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        // Must be > processing time per batch or rebalance triggers
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000); // 5 minutes
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // AckMode.MANUAL_IMMEDIATE: ack as soon as acknowledge() is called
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}

@Component
public class OrderConsumer {

    // concurrency = 3 creates 3 KafkaMessageListenerContainer instances
    // each consuming from a subset of partitions assigned by group coordinator
    @KafkaListener(
        topics = "orders",
        groupId = "order-service",
        concurrency = "3"
    )
    public void onOrder(
            ConsumerRecord<String, OrderEvent> record,
            Acknowledgment ack) {
        try {
            log.info("Processing order {} from partition {} offset {}",
                record.value().getOrderId(), record.partition(), record.offset());
            processOrder(record.value());
            ack.acknowledge(); // commit offset only after successful processing
        } catch (Exception e) {
            // do NOT ack — message will be redelivered after rebalance or restart
            // or configure DefaultErrorHandler for retry
            log.error("Failed processing order", e);
            throw e;
        }
    }
}
```

### Error Handling — DefaultErrorHandler with BackOff

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory(
        KafkaTemplate<String, OrderEvent> kafkaTemplate) {

    // Retry up to 3 times with exponential backoff before sending to DLT
    ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0); // 1s, 2s, 4s
    backOff.setMaxElapsedTime(10_000L);

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);

    // Do not retry on deserialization errors — they will never succeed
    errorHandler.addNotRetryableExceptions(DeserializationException.class);

    ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.setCommonErrorHandler(errorHandler);
    return factory;
}
```

### @RetryableTopic — Declarative Retry with Dead-Letter Topic

```java
@Component
public class OrderConsumer {

    // Creates retry topics: orders-retry-0, orders-retry-1, orders-retry-2
    // and dead letter topic: orders-dlt
    // Each retry topic has an increasing delay (backoff)
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "orders")
    public void onOrder(OrderEvent event) {
        processOrder(event); // throws exception to trigger retry
    }

    @DltHandler
    public void onDlt(OrderEvent event, @Header KafkaHeaders.RECEIVED_TOPIC String topic) {
        log.error("Message arrived in DLT from topic {}: {}", topic, event);
        // alert, manual intervention, or store for replay
    }
}
```

### Kafka Transactions — Exactly-Once

```java
@Configuration
public class KafkaTransactionConfig {

    @Bean
    public KafkaTransactionManager<String, OrderEvent> kafkaTransactionManager(
            ProducerFactory<String, OrderEvent> pf) {
        return new KafkaTransactionManager<>(pf);
    }
}

@Service
@Transactional("kafkaTransactionManager")
public class TransactionalOrderPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishInTransaction(List<OrderEvent> events) {
        // All sends in the same transaction — either all committed or all aborted
        events.forEach(e -> kafkaTemplate.send("orders", e.getOrderId(), e));
    }
}
```

Consumer must set `isolation.level=read_committed` to see only committed messages.

### RabbitMQ — RabbitTemplate and @RabbitListener

```java
@Configuration
public class RabbitConfig {

    // Dead Letter Exchange
    @Bean
    public DirectExchange dlx() {
        return new DirectExchange("orders.dlx");
    }

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable("orders.dlq").build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlq()).to(dlx()).with("orders.routing.key");
    }

    @Bean
    public Queue ordersQueue() {
        return QueueBuilder.durable("orders")
            .withArgument("x-dead-letter-exchange", "orders.dlx")
            .withArgument("x-dead-letter-routing-key", "orders.routing.key")
            .withArgument("x-message-ttl", 60_000)       // 60s TTL
            .withArgument("x-max-priority", 10)          // priority queue, 0-10
            .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10); // backpressure: max 10 unacked messages per consumer
        return factory;
    }
}

@Component
public class OrderRabbitConsumer {

    @RabbitListener(queues = "orders")
    public void onOrder(
            @Payload OrderEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            processOrder(event);
            channel.basicAck(deliveryTag, false); // false = single message ack
        } catch (BusinessException e) {
            // requeue=false sends to DLX
            channel.basicNack(deliveryTag, false, false);
        } catch (TransientException e) {
            // requeue=true — try again
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

### Spring Cloud Stream — Functional Style

```java
// application.yml binding:
// spring.cloud.stream.bindings.processOrder-in-0.destination=orders
// spring.cloud.stream.bindings.processOrder-out-0.destination=receipts

@Configuration
public class OrderStreamConfig {

    // Input: orders topic, Output: receipts topic
    @Bean
    public Function<OrderEvent, Receipt> processOrder() {
        return orderEvent -> {
            Receipt receipt = new Receipt();
            receipt.setOrderId(orderEvent.getOrderId());
            receipt.setTimestamp(Instant.now());
            return receipt;
        };
    }

    // Supplier polls and publishes every second (default)
    @Bean
    public Supplier<OrderEvent> orderSource(OrderRepository repo) {
        return () -> repo.findNextPendingOrder();
    }

    // Consumer with no output
    @Bean
    public Consumer<Receipt> receiptLogger() {
        return receipt -> log.info("Receipt: {}", receipt.getOrderId());
    }
}
```

### @Async — ThreadPoolTaskExecutor Configuration

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(10);
        exec.setMaxPoolSize(50);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("async-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
            log.error("Async exception in method {}: {}", method.getName(), throwable.getMessage());
    }
}

@Service
public class NotificationService {

    @Async("taskExecutor")
    public CompletableFuture<Void> sendEmail(String to, String body) {
        emailClient.send(to, body); // executes in taskExecutor thread
        return CompletableFuture.completedFuture(null);
    }
}
```

### WebSocket — STOMP

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS(); // SockJS fallback for browsers without WebSocket
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue"); // in-memory broker
        registry.setApplicationDestinationPrefixes("/app");
        // For production: use RabbitMQ as external STOMP broker
        // registry.enableStompBrokerRelay("/topic", "/queue")
        //     .setRelayHost("localhost").setRelayPort(61613);
    }
}

@Controller
public class PriceController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/price.subscribe")
    public void subscribe(@Payload String symbol, Principal principal) {
        // client subscribes; server will push prices
    }

    // Called by a scheduler or event listener
    public void broadcastPrice(PriceUpdate update) {
        messagingTemplate.convertAndSend("/topic/prices/" + update.getSymbol(), update);
    }

    // Send to a specific user's private queue
    public void sendToUser(String username, PortfolioUpdate update) {
        messagingTemplate.convertAndSendToUser(username, "/queue/portfolio", update);
    }
}
```

### Outbox Pattern — Atomic Write and Publish

```java
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepo;
    private final OutboxRepository outboxRepo;

    public void placeOrder(Order order) {
        orderRepo.save(order); // write business data

        OutboxEvent event = OutboxEvent.builder()
            .aggregateId(order.getId())
            .aggregateType("Order")
            .eventType("OrderPlaced")
            .payload(serialize(order))
            .status(OutboxStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        outboxRepo.save(event); // write event in SAME transaction
        // Both succeed or both fail — no dual-write inconsistency
    }
}

// Separate poller (or Debezium CDC) publishes PENDING outbox events to Kafka
@Scheduled(fixedDelay = 500)
@Transactional
public void publishOutboxEvents() {
    List<OutboxEvent> pending = outboxRepo.findByStatus(OutboxStatus.PENDING);
    pending.forEach(event -> {
        kafkaTemplate.send("orders", event.getAggregateId(), event.getPayload());
        event.setStatus(OutboxStatus.PUBLISHED);
    });
    outboxRepo.saveAll(pending);
}
```

---

## 7. Real-World Examples

**E-commerce order processing**: Order service writes to database and publishes `OrderPlaced` event via Outbox pattern to Kafka topic `orders`. Inventory service, fulfillment service, and notification service each consume from `orders` in their own consumer groups. Each group processes independently, scaling consumer instances to match partition count. Failure in one consumer does not affect others.

**Financial transaction audit**: Kafka's log-based retention means every transaction event is permanently available for replay. Audit service can restart from offset 0 and reconstruct the full event history. No message is deleted on consumption — this is the fundamental difference from queue-based systems.

**Real-time price feeds**: Trading platform pushes price updates over STOMP/WebSocket to subscribed clients. SimpMessagingTemplate broadcasts to `/topic/prices/{symbol}`. Server does not poll clients — it pushes when new data is available. SockJS ensures the connection degrades gracefully to long-polling when WebSocket is unavailable.

**Email notification queue**: RabbitMQ with priority queues ensures transactional emails (password reset, order confirmation) — published with priority 8 — are processed before marketing emails — published with priority 1. A single consumer processes the priority queue and routes to the appropriate email provider.

**Microservice saga orchestration**: Each step in a distributed saga publishes a command event to a dedicated Kafka topic. Each participant consumes commands, executes local work, and publishes a reply event. The orchestrator consumes reply events and decides next steps. Failures trigger compensating transactions via dedicated compensation topics.

---

## 8. Tradeoffs

### Kafka vs RabbitMQ

| Concern | Kafka | RabbitMQ |
|---------|-------|----------|
| Message replay | Yes — seek any offset | No — consumed messages gone |
| Routing flexibility | Low (topic + partition key) | High (exchange types, headers) |
| Ordering guarantee | Within partition | Within single-consumer queue |
| Message size | Best for small (<1MB) | Small to medium messages |
| Consumer scaling | Up to partition count | Many consumers on one queue |
| Operational complexity | Higher (ZooKeeper or KRaft) | Lower |
| Latency | ~1-5ms end-to-end | Sub-millisecond possible |

### At-Least-Once vs Exactly-Once

| Dimension | At-Least-Once | Exactly-Once (Kafka TXN) |
|-----------|--------------|--------------------------|
| Complexity | Low | High |
| Performance | Higher throughput | ~20-30% overhead |
| Correctness | Requires idempotent consumer | Guaranteed by protocol |
| Failure scope | Single producer/consumer | Full transaction scope |
| Use case | Most business events | Financial, billing, inventory |

### @Async vs Messaging Broker

| Dimension | @Async | Message Broker |
|-----------|--------|---------------|
| Durability | None — JVM crash loses work | Durable (broker persists) |
| Scalability | Single JVM thread pool | Multiple consumer instances |
| Observability | Limited | Full message tracking |
| Complexity | Simple | Requires broker infrastructure |
| Use case | Non-critical async work | Critical, distributed, durable work |

---

## 9. When to Use / When NOT to Use

### Use Spring Kafka when:
- You need high-throughput event streaming (>100k events/s)
- Event replay / audit trail is required
- Multiple independent consumer groups need the same event
- You are building an event-sourcing or CQRS system

### Do NOT use Spring Kafka when:
- You need complex routing (use RabbitMQ exchanges)
- Your use case is RPC-style request-reply (use REST or gRPC)
- You only need fire-and-forget async within one JVM (use @Async)
- Your team cannot operate Kafka infrastructure (use managed service or RabbitMQ)

### Use Spring AMQP when:
- Complex routing with exchange types (fanout, topic, headers) is needed
- Message TTL and priority queues are required
- Your team is already familiar with AMQP semantics
- You need request-reply patterns (RabbitMQ reply-to)

### Use @Async when:
- Async execution is within the same JVM
- Durability is not required (notification sending, cache warming)
- You want simple non-blocking execution without a broker

### Use WebSocket when:
- Real-time bidirectional communication is needed
- Server needs to push data to clients without polling
- Sub-second latency updates are required (trading, collaborative editing, chat)

### Do NOT use WebSocket when:
- One-way data flow (server-sent events suffice)
- Long-running, durable job submission (use message queue)
- Mobile clients with unreliable connectivity (polling may be more robust)

---

## 10. Common Pitfalls

### Pitfall 1 — Kafka consumer rebalance due to slow processing (broken)

```java
// BROKEN: max.poll.records is 500 (default) but each record takes 100ms to process
// 500 * 100ms = 50 seconds per poll cycle
// max.poll.interval.ms default is 300,000ms (5 minutes) — might be OK
// But with DB calls under load, processing slows to 500ms per record:
// 500 * 500ms = 250 seconds > 300,000ms? No — 250 seconds = 250,000ms, still OK
// BUT if processing is 1000ms per record: 500 * 1000ms = 500 seconds > 5 minutes
// Broker considers consumer dead, triggers REBALANCE
// All in-flight records are redelivered to another consumer — DUPLICATES

@KafkaListener(topics = "orders")
public void onOrder(OrderEvent event) {
    // slow: calls external API with 1000ms average latency
    externalApiClient.enrich(event); // can take >1000ms under load
    orderRepo.save(event);
    // No ack control — auto-commit fires before processing completes
}
```

```java
// FIXED: reduce max.poll.records to a safe value
// total_processing_time = maxPollRecords * avg_processing_time_per_record
// maxPollRecords = max.poll.interval.ms / avg_processing_time_per_record
// 300_000ms / 1000ms = 300 — use 100 for safety margin

config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);

// Also: use manual ack so you do not lose the offset on crash
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

@KafkaListener(topics = "orders", concurrency = "3")
public void onOrder(ConsumerRecord<String, OrderEvent> record, Acknowledgment ack) {
    externalApiClient.enrich(record.value());
    orderRepo.save(record.value());
    ack.acknowledge(); // commit only after successful processing
}
```

### Pitfall 2 — @Async on a self-invoked method (broken)

```java
// BROKEN: self-invocation bypasses the Spring proxy
// @Async is implemented via AOP proxy — calling this.sendEmail() from within
// the same bean does NOT go through the proxy, so @Async is ignored

@Service
public class NotificationService {

    public void notifyAll(List<String> recipients) {
        recipients.forEach(r -> sendEmail(r)); // calls this.sendEmail — proxy bypassed!
    }

    @Async
    public void sendEmail(String recipient) {
        emailClient.send(recipient, "Hello");
    }
}
```

```java
// FIXED: inject self-reference or extract @Async methods to a separate bean

@Service
public class NotificationService {

    @Autowired
    private NotificationService self; // Spring injects the proxied version of itself

    public void notifyAll(List<String> recipients) {
        recipients.forEach(r -> self.sendEmail(r)); // goes through proxy — @Async works
    }

    @Async("taskExecutor")
    public void sendEmail(String recipient) {
        emailClient.send(recipient, "Hello");
    }
}
// Preferred approach: extract to AsyncEmailSender bean and inject it here
```

### Pitfall 3 — Missing @EnableAsync (broken)

```java
// BROKEN: @Async annotated but @EnableAsync not declared anywhere
// Spring does NOT process @Async annotations without @EnableAsync
// Method executes synchronously — no error, just no async behavior

@SpringBootApplication // does NOT include @EnableAsync
public class Application { ... }

@Service
public class ReportService {
    @Async
    public void generateReport() { /* executes SYNCHRONOUSLY */ }
}
```

```java
// FIXED: add @EnableAsync to a @Configuration class
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(5);
        exec.setMaxPoolSize(20);
        exec.setQueueCapacity(50);
        exec.initialize();
        return exec;
    }
}
```

### Pitfall 4 — RabbitMQ infinite requeue loop (broken)

```java
// BROKEN: exception thrown, basicNack with requeue=true
// Message goes back to the front of the queue
// Consumer picks it up immediately, throws again
// Infinite loop at full CPU — broker and consumer thrash

@RabbitListener(queues = "orders")
public void onOrder(OrderEvent event, Channel channel,
                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
    try {
        processOrder(event);
        channel.basicAck(tag, false);
    } catch (Exception e) {
        channel.basicNack(tag, false, true); // requeue=true — INFINITE LOOP
    }
}
```

```java
// FIXED: requeue=false on non-transient errors; rely on DLX for dead-letter handling
// For transient errors: use Spring Retry with a delay before requeue

@RabbitListener(queues = "orders")
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
public void onOrder(OrderEvent event, Channel channel,
                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
    try {
        processOrder(event);
        channel.basicAck(tag, false);
    } catch (BusinessException e) {
        channel.basicNack(tag, false, false); // requeue=false -> DLX
    }
    // TransientException: @Retryable will retry up to 3 times with backoff
    // After 3 failures -> @Recover method or DLX
}
```

### Pitfall 5 — Dual-write inconsistency without Outbox pattern (broken)

```java
// BROKEN: save to DB, then send Kafka event
// If Kafka.send() succeeds but DB commit fails: event published but no order in DB
// If DB commit succeeds but Kafka.send() fails: order in DB but no event published
// These are the two faces of the dual-write problem

@Transactional
public void placeOrder(Order order) {
    orderRepo.save(order);
    kafkaTemplate.send("orders", order.getId(), new OrderPlaced(order)); // outside TX
    // commit happens here — but Kafka send is NOT part of DB transaction
}
```

```java
// FIXED: Outbox pattern — write event to DB table in same transaction
// A separate relay process reads PENDING events and publishes to Kafka
// Guarantees atomicity: both order and event-record succeed or both fail

@Transactional
public void placeOrder(Order order) {
    orderRepo.save(order);
    outboxRepo.save(OutboxEvent.of("Order", order.getId(), "OrderPlaced", serialize(order)));
    // Both in same DB transaction — atomic
}

// Transactional outbox relay: runs every 500ms, reads PENDING, publishes, marks PUBLISHED
```

---

## 11. Technologies & Tools

| Technology | Purpose | Notes |
|-----------|---------|-------|
| spring-kafka | Kafka integration | KafkaTemplate, @KafkaListener, KafkaTransactionManager |
| spring-amqp | RabbitMQ integration | RabbitTemplate, @RabbitListener, DLX support |
| Spring Cloud Stream | Binder abstraction | Supports Kafka, RabbitMQ, Azure Service Bus, AWS Kinesis |
| Apache Kafka | Distributed log / event streaming | Requires ZooKeeper or KRaft mode |
| RabbitMQ | AMQP message broker | STOMP plugin for WebSocket relay |
| spring-websocket | WebSocket + STOMP support | SockJS fallback included |
| Debezium | CDC for Outbox pattern | Reads DB transaction log, publishes to Kafka |
| Micrometer | Metrics for Kafka consumers / RabbitMQ | Consumer lag, queue depth, publish rate |
| Testcontainers | Kafka/RabbitMQ in integration tests | KafkaContainer, RabbitMQContainer |

---

## 12. Interview Questions with Answers

**Q: What is the difference between at-least-once and exactly-once delivery in Kafka, and how does Spring Kafka achieve exactly-once?**
At-least-once means the broker may deliver a message more than once — a duplicate will occur if the producer retries or the consumer crashes after processing but before committing the offset. Exactly-once in Kafka is achieved by combining an idempotent producer (enable.idempotence=true, which assigns sequence numbers to deduplicate retries at the broker), a transactional producer (transactional.id configured, which wraps sends in atomic transactions), and consumers with isolation.level=read_committed (which skip uncommitted messages). Spring Kafka configures all three via KafkaTransactionManager and appropriate ProducerFactory settings. The overhead is roughly 20-30% lower throughput compared to at-least-once. Use exactly-once only for financial or inventory updates where duplicates cause real-world harm.

**Q: A Kafka consumer in production is causing frequent rebalances. What are the likely causes and how do you fix them?**
The most common cause is the consumer taking longer than max.poll.interval.ms to process a batch. The broker interprets this as a dead consumer and triggers a rebalance, causing all in-progress messages to be redelivered — producing duplicates. The fix is to reduce max.poll.records so that total processing time per poll stays well below max.poll.interval.ms. A safe formula is: max.poll.records = max.poll.interval.ms / avg_processing_time_ms, with a 50% safety margin. Other causes include session.timeout.ms being too low for the network, GC pauses causing the poll loop to stall, or a consumer crashing. Monitoring consumer lag and rebalance events via Micrometer is essential for early detection.

**Q: Explain the role of the Dead Letter Topic (DLT) in Spring Kafka and how @RetryableTopic implements retry semantics.**
A Dead Letter Topic holds messages that could not be processed successfully after all retry attempts. @RetryableTopic creates a chain of retry topics (e.g., orders-retry-0, orders-retry-1) each with a configured delay. When processing fails, the framework publishes the message to the next retry topic rather than immediately retrying on the original topic. This prevents blocking the main partition while waiting for a backoff delay. After all retry attempts are exhausted, the message is published to the DLT. A @DltHandler method can alert operators or store the message for manual replay. The key advantage over synchronous retry is that the main consumer continues processing other messages during the backoff period.

**Q: What is the Outbox pattern and why is it needed with Kafka?**
The Outbox pattern solves the dual-write problem: when you need to write to a database and publish a Kafka event atomically. Without it, the two operations are independent — a crash between DB commit and Kafka send leaves the system in an inconsistent state. The Outbox pattern stores the event as a row in an outbox table within the same database transaction as the business write. A separate relay process (polling or Debezium CDC) reads pending outbox rows and publishes them to Kafka, then marks them as published. Because the business write and the outbox row are in the same transaction, they always succeed or fail together. Debezium CDC-based relay is preferred in production because it reads from the database write-ahead log rather than polling, providing near-real-time publishing.

**Q: What is the difference between concurrency in @KafkaListener and partition count?**
The concurrency attribute on @KafkaListener creates multiple KafkaMessageListenerContainer instances, each running its own poll loop. However, Kafka's consumer group protocol assigns at most one consumer per partition. So if concurrency=3 but the topic has only 2 partitions, one container will be idle. To utilize all three containers, the topic must have at least 3 partitions. In practice, partition count should be set to the maximum number of consumers you anticipate across all instances of the service. Adding partitions after the fact is possible but requires a rebalance.

**Q: How does RabbitMQ's Dead Letter Exchange (DLX) work, and what triggers a message to be dead-lettered?**
A DLX is a regular exchange designated to receive messages that are rejected by a queue. A message is sent to the DLX when it is NACK'd with requeue=false, when it expires (message TTL or queue-level x-message-ttl), or when the queue is full and x-overflow=reject-publish-dlx is set. The dead-lettered message is published to the DLX with the original routing key (or an x-dead-letter-routing-key if configured on the source queue) and routed to the Dead Letter Queue (DLQ). From the DLQ, operators can inspect, replay, or discard messages. Without a DLX, NACK'd messages with requeue=false are simply discarded.

**Q: What is the difference between @RabbitListener with AcknowledgeMode.AUTO vs MANUAL?**
In AUTO mode, Spring AMQP automatically acknowledges the message when the listener method returns without exception, and automatically NACK's (with requeue configured by the container) on exception. This is simpler but less flexible — you cannot conditionally NACK based on the exception type. In MANUAL mode, the consumer controls acknowledgement by injecting Channel and the delivery tag, then explicitly calling channel.basicAck() or channel.basicNack(). MANUAL mode is required when you need to differentiate transient failures (requeue=true) from permanent failures (requeue=false, route to DLX), or when you need to ack only after a side-effect (DB write) succeeds.

**Q: Explain Spring Cloud Stream's functional programming model. How does it differ from @StreamListener?**
The functional model replaced @StreamListener (deprecated in Spring Cloud Stream 3.x). Instead of annotation-driven listener methods, you expose standard Java functional beans: Consumer<T> for consuming messages, Supplier<T> for producing messages, and Function<T,R> for consuming and producing. The binder discovers these beans and wires them to topics or queues based on the bean name and spring.cloud.stream.bindings configuration. The advantage is that the business logic is pure Java functions with no framework annotations — they are easily unit-testable without a Spring context. The binder handles serialization, error handling, and retry. The functional model also supports reactive types (Flux<T>, Mono<T>) for reactive stream processing.

**Q: How does @Async work internally in Spring? What happens if @EnableAsync is missing?**
@Async is implemented via Spring AOP. When @EnableAsync is present, Spring creates a proxy for every bean that has @Async methods. When the @Async method is called through the proxy, the proxy submits a Runnable to the configured TaskExecutor and returns immediately with a CompletableFuture (or void). The actual method executes in the executor thread. If @EnableAsync is missing, no proxy is created — the @Async annotation is silently ignored and the method executes synchronously in the caller's thread. There is no exception or warning. This is a common source of silent bugs in production.

**Q: What is the SockJS fallback in Spring WebSocket, and when is it needed?**
SockJS is a JavaScript library and protocol that provides a WebSocket-like API with automatic fallback to HTTP long-polling or HTTP streaming when WebSocket is unavailable. This is needed in corporate environments with proxies or firewalls that block WebSocket upgrades. When SockJS is configured via registry.addEndpoint("/ws").withSockJS(), the Spring backend supports multiple transports on the same endpoint: native WebSocket, XHR-streaming, and XHR-polling. The client SockJS library negotiates the best available transport. In production, prefer an external STOMP broker relay (RabbitMQ with STOMP plugin) over the in-memory SimpleBroker for persistence and horizontal scalability.

**Q: What is the risk of prefetchCount in RabbitMQ being too high?**
A high prefetchCount (e.g., 250, the old default) causes the broker to send up to 250 unacknowledged messages to each consumer before waiting for acks. If the consumer is slow or crashes, all 250 messages are held in memory unprocessed, and other consumers cannot receive them. This reduces throughput by concentrating load on slow consumers. In Spring AMQP, the default prefetchCount was changed from 250 to 1 (fair dispatch) in older versions, but Spring Boot auto-configuration sets it to 250 unless overridden. For fair dispatch, set prefetchCount=1. For higher throughput with fast consumers, increase it — but monitor unacked message counts to find the optimal value.

**Q: How do you test a Kafka consumer in a Spring Boot integration test?**
Use Testcontainers with KafkaContainer to start a real Kafka broker in Docker. Annotate the test class with @Testcontainers and @SpringBootTest. Use @DynamicPropertySource to set spring.kafka.bootstrap-servers to the container's mapped port. Publish messages using KafkaTemplate in the test and verify consumer behavior via assertions on the database or a CountDownLatch that the listener decrements. For unit tests of listener logic, test the handler method directly without Spring context overhead. The EmbeddedKafkaBroker (@EmbeddedKafka) is an alternative that avoids Docker but is less production-representative.

**Q: What is exactly-once semantics in Kafka and how does Spring Kafka's `@Transactional` integration achieve it?**
Kafka's exactly-once semantics (EOS) prevents duplicates and lost messages across a produce → consume → produce pipeline. Spring Kafka supports EOS via `KafkaTransactionManager` and Spring's `@Transactional`. When enabled: (1) The `KafkaProducer` is configured as an idempotent transactional producer (`enable.idempotence=true`, `transactional.id=...`). (2) `@Transactional` on a `@KafkaListener` method wraps the entire consume-process-produce in a single Kafka transaction — the consumer offset commit and the downstream produce are both included in the transaction. If the method throws, both the offset commit and any produced messages are rolled back. On retry, the consumer re-processes the same message. Config: `spring.kafka.producer.transaction-id-prefix=tx-` and `spring.kafka.consumer.isolation-level=read_committed` (consumers only see committed messages). Limitation: EOS adds ~20–30% throughput overhead and requires a Kafka broker ≥ 0.11.

**Q: What is Spring AMQP's dead letter exchange (DLX) pattern and how do you implement retry with backoff?**
A dead letter exchange receives messages that are rejected (NACK + requeue=false), expired (TTL), or overflow a queue limit. Configure a per-queue DLX: when a `@RabbitListener` throws and exhausts retries, Spring AMQP sends the message to the DLX with the original exchange/routing key as dead-letter headers. Retry with backoff: configure `RetryInterceptorBuilder.stateless()` (or stateful for commit semantics) on the listener container with exponential back-off (`ExponentialBackOffPolicy`). After max attempts, the interceptor calls the recoverer which publishes to the DLQ. Production pattern:

```yaml
# Queue definition: original queue with 3-retry DLX
queue: orders.processing
arguments:
  x-dead-letter-exchange: orders.dlx
  x-dead-letter-routing-key: orders.failed
```

The DLQ is consumed by a separate listener for manual review or republish after the downstream issue is resolved. Always set `defaultRequeueRejected=false` on the listener container when using DLX — otherwise rejected messages re-enter the queue indefinitely.

**Q: How do `@Async` methods interact with Spring transactions and what is the "lost transaction context" problem?**
`@Async` executes the annotated method on a separate thread from a `TaskExecutor`. Spring's `@Transactional` stores the transaction in a `ThreadLocal` (via `TransactionSynchronizationManager`). When a `@Transactional` method calls an `@Async` method, the async method runs on a different thread — it has no access to the caller's `ThreadLocal` transaction context. Consequences: (1) The async method starts a new transaction (if `@Transactional` is present), independent of the caller's transaction. (2) If the caller rolls back, the async method's already-committed transaction is NOT rolled back — you have an orphaned write. Fix for tight coupling: use `@TransactionalEventListener(phase = AFTER_COMMIT)` — the event listener fires only after the outer transaction commits successfully, and runs asynchronously via `@Async` without needing to participate in the outer transaction. This is the correct pattern for triggering async side effects (emails, notifications) after a database write.

---

## 13. Best Practices

**Kafka topic naming**: Use dot-separated names: `<domain>.<entity>.<event>` — e.g., `payments.orders.placed`. Retry topics follow the pattern `<topic>-retry-<index>` and dead letter topic `<topic>-dlt`.

**Partition count**: Set partition count at creation time based on the maximum consumer concurrency you plan to run. Adding partitions later is disruptive. A common starting point is 6–12 partitions for moderate-throughput topics.

**Message schema**: Use Avro with Confluent Schema Registry or JSON with JSON Schema to enforce compatibility. Never serialize Java objects directly with Java serialization — it ties producer and consumer to the same JVM version and class structure.

**Consumer group IDs**: Each independent consumer group must have a unique group ID. Sharing a group ID between services means they compete for the same partitions — usually unintended.

**Manual acknowledgement over auto-commit for critical consumers**: Auto-commit can commit offsets before processing completes (if the commit timer fires) or after processing but before committing (if the JVM crashes). Manual ack gives precise control.

**Set max.poll.records conservatively**: Start at 10–50 records, measure processing time, then increase. A rebalance under load is far more disruptive than slightly lower throughput.

**Use @RetryableTopic instead of in-handler retry loops**: In-handler retry holds the partition while retrying, blocking other messages. @RetryableTopic publishes to a retry topic, freeing the partition immediately.

**Outbox pattern for microservices**: Never write to DB and Kafka in separate operations without a transactional guarantee. Use the Outbox pattern with a relay or Debezium CDC.

**RabbitMQ DLX on every queue**: Configure a DLX on every queue at creation time. A queue without a DLX silently discards rejected messages. DLX ensures every failed message is observable.

**@Async thread pool sizing**: Name the pool, set a bounded queue size, and configure a rejected execution handler. The default SimpleAsyncTaskExecutor creates a new thread per invocation — this is not a pool and will exhaust threads under load.

**WebSocket: use external broker in production**: The in-memory SimpleBroker does not survive application restart and cannot be shared across multiple application instances. Use RabbitMQ with the STOMP plugin or ActiveMQ as the external broker relay.

---

## 14. Case Study

### Scenario: Event-Driven Order Processing at 10k Events/sec with Exactly-Once Semantics

**Context.** An e-commerce platform consumes order events from Kafka at **10,000 events/sec** across a 24-partition topic. Each event must update the order ledger exactly once — a duplicate charge or a lost order is unacceptable. The consumer uses `@Transactional` with **manual offset commit** so the DB write and the offset advance succeed or fail together. Failures retry 3 times, then route to a dead-letter topic. A fire-and-forget notification is dispatched with `@Async`.

### Architecture

```
   orders topic (24 partitions, 10k events/sec)
        |
        v
   +----------------------------------------+
   | ConcurrentKafkaListenerContainerFactory|  concurrency=24 (1 thread/partition)
   |  AckMode = MANUAL                      |
   +-------------------+--------------------+
                       | @KafkaListener
                       v
   +----------------------------------------+
   | @Transactional process()               |
   |  1. write order ledger (DB)            |
   |  2. ack.acknowledge() AFTER commit     |
   +-------------------+--------------------+
          success      |        failure (3 retries, backoff)
          v            v
   @Async notify   DefaultErrorHandler ---> DeadLetterPublishingRecoverer
   (email/SMS)                              ---> orders.DLT
```

### Listener and Container Configuration

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory(
            ConsumerFactory<String, OrderEvent> cf, KafkaTemplate<String, Object> template) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderEvent>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(24);                                  // one consumer per partition
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 3 retries with backoff 1s,2s,4s; then publish to <topic>.DLT
        var recoverer = new DeadLetterPublishingRecoverer(template);
        var errorHandler = new DefaultErrorHandler(recoverer, new ExponentialBackOff(1000L, 2.0));
        errorHandler.setRetryListeners((rec, ex, attempt) ->
            log.warn("retry {} for offset {}", attempt, rec.offset()));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
```

```java
@Component
public class OrderListener {

    @KafkaListener(topics = "orders", containerFactory = "factory")
    @Transactional                                          // DB write + offset commit atomic
    public void onOrder(OrderEvent event, Acknowledgment ack) {
        if (ledger.existsByEventId(event.id())) {           // idempotency guard
            ack.acknowledge();
            return;
        }
        ledger.apply(event);                                // 1. durable DB write
        ack.acknowledge();                                  // 2. commit offset ONLY after success
        notifier.sendConfirmation(event);                   // 3. fire-and-forget @Async
    }
}
```

```java
@Service
public class Notifier {
    @Async("notifyExecutor")                                // does not block the consumer thread
    public void sendConfirmation(OrderEvent event) {
        emailClient.send(event.customerEmail(), template(event));
    }
}
```

### Metrics

- Throughput: **10,200 events/sec** sustained, p99 process latency **45ms**.
- Duplicate writes: **0** (idempotency key + ack-after-commit).
- DLT volume: **<0.01%** of traffic, all alerted and replayable.
- Notification thread pool: 8 core / 32 max, queue 500; rejection rate **0** after sizing.

### Pitfalls

**Pitfall 1 — Auto-commit before processing completes (at-most-once on crash).**
```java
// BROKEN: offset is committed on a timer BEFORE the DB write; a crash mid-process loses the event
spring.kafka.consumer.enable-auto-commit: true   // commits every 5s regardless of success
```
```java
// FIXED: manual ack, committed only after the transactional DB write succeeds
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
// ... ledger.apply(event); ack.acknowledge();  // commit follows success -> at-least-once + idempotency
```

**Pitfall 2 — Unchecked exception escaping the listener kills the consumer.**
```java
// BROKEN: no error handler; an unhandled RuntimeException stops the container,
// the partition is unassigned, and consumption silently halts
@KafkaListener(topics = "orders")
public void onOrder(OrderEvent e) { ledger.apply(e); }   // throws -> consumer dies
```
```java
// FIXED: DefaultErrorHandler retries then routes to the DLT, keeping the partition assigned
factory.setCommonErrorHandler(
    new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template),
                            new ExponentialBackOff(1000L, 2.0)));
```

**Pitfall 3 — `@Async` thread pool exhaustion and lost MDC context.**
```java
// BROKEN: default SimpleAsyncTaskExecutor spawns unbounded threads and drops the request's MDC (traceId)
@Async
public void sendConfirmation(OrderEvent e) { /* logs have no traceId; threads explode under load */ }
```
```java
// FIXED: bounded pool + TaskDecorator that copies MDC from the caller thread to the worker
@Bean("notifyExecutor")
ThreadPoolTaskExecutor notifyExecutor() {
    var ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(8); ex.setMaxPoolSize(32); ex.setQueueCapacity(500);
    ex.setTaskDecorator(runnable -> {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> { if (ctx != null) MDC.setContextMap(ctx); try { runnable.run(); } finally { MDC.clear(); } };
    });
    ex.initialize();
    return ex;
}
```

### Interview Q&A

**Why is ack-after-commit "at-least-once" rather than true "exactly-once"?** The DB write commits, then the offset is acknowledged; a crash between those two steps re-delivers the event on restart. True end-to-end exactly-once requires the consume and produce to share a Kafka transaction (read-process-write). The practical pattern here is at-least-once delivery plus an idempotency key, which yields exactly-once *effects*.

**What does `concurrency=24` on the container factory do?** It creates 24 consumer threads in one consumer group. Kafka assigns at most one consumer per partition, so with a 24-partition topic each thread owns one partition, maximizing parallelism without violating per-partition ordering.

**What happens to ordering when an event fails and goes to the DLT?** Within a partition, retries block subsequent messages until the failed one is retried or routed to the DLT. Once it lands on the DLT the partition proceeds, so cross-message ordering is preserved for successes but the failed message is processed out of band.

**Why must `@Async` work be fire-and-forget here?** Sending email/SMS is slow and externally rate-limited. Doing it inline would hold the consumer thread, reduce throughput, and couple consumption to a flaky third party. Offloading to a bounded async pool keeps the consumer hot; the notification is best-effort and separately retryable.

**Why does `@Async` lose the trace context, and how do you fix it?** `@Async` runs the method on a different thread, and `MDC` is thread-local, so the worker thread starts with an empty MDC and logs lose the `traceId`. A `TaskDecorator` copies the caller's MDC into the worker before running and clears it afterward.

**How do you size the consumer's `max.poll.records` and `max.poll.interval.ms`?** Set `max.poll.records` so that batch processing time stays well under `max.poll.interval.ms`; otherwise the broker considers the consumer dead and triggers a rebalance storm. At ~45ms/event, a poll of 50 records (~2.3s) is safely under the default 5-minute interval.

---

## Related / See Also

- [Spring Batch](../spring_batch/README.md) — event-driven batch triggers
- [Spring Events & Scheduling](../spring_events_and_scheduling/README.md) — @TransactionalEventListener
- [Case Study: Event-Driven Microservice](../case_studies/design_event_driven_microservice.md) — Kafka integration
