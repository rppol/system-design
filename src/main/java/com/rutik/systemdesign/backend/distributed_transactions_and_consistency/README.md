# Distributed Transactions and Consistency

**Cross-references:**
- [Spring Transactions](../../spring/spring_transactions/README.md)
- [Kafka Deep Dive](../kafka_deep_dive/README.md)
- [Messaging Patterns](../messaging_patterns/README.md)
- [Event-Driven Fundamentals](../event_driven_fundamentals/README.md)

---

## 1. Concept Overview

Distributed transactions coordinate data mutations that span multiple databases, services, or nodes, ensuring that a logical unit of work either fully completes or fully rolls back — even when any participant can fail independently.

In a monolith backed by a single relational database, the database engine handles ACID atomicity. Once a system is decomposed into microservices, each service owns its own datastore. A business operation such as "debit account A and credit account B across two different services" can no longer rely on a single database transaction. Network partitions, node crashes, and message loss mean that some participants may commit while others do not, leaving the system in an inconsistent state.

The core tension is stated by the CAP theorem (Gilbert and Lynch's 2002 proof of Brewer's conjecture): **when a network partition occurs**, a system cannot be both linearizable and available — it must give up one. CAP is not a standing "pick two of three" menu, and no system "chooses CA"; outside a partition a system can be both consistent and available, and the C-vs-A choice can be made per operation (Brewer, "CAP Twelve Years Later", 2012). Distributed transaction protocols differ in which side of that partition-time choice they take, and at what cost in complexity.

Key concerns:
- **Atomicity across services**: either all local transactions commit or all are compensated.
- **Durability of intermediate state**: surviving coordinator crashes without losing intent.
- **Eventual vs. immediate consistency**: tolerating temporary inconsistency in exchange for availability.
- **Idempotency**: compensating transactions and message consumers must be safe to re-execute.

---

## 2. Intuition

**One-line analogy**: A distributed transaction is like a multi-party contract signing — all parties must sign before the contract is valid, but if one party's pen runs dry mid-process, you need a clear procedure to either get everyone to sign again or to legally void the partial signatures already collected.

**Mental model**: Imagine a bank wire transfer spanning three correspondent banks. Each bank applies a hold on funds, confirms the hold to a central clearing coordinator, and only releases/moves money after every bank confirms. If one bank's network drops after applying the hold but before confirming, the coordinator must decide: wait indefinitely (blocking) or adopt a timeout-and-abort policy with compensating reversals.

**Why it matters**: Payment systems, order fulfillment, inventory reservation, and any workflow touching more than one bounded context require a strategy here. Getting it wrong means double charges, phantom inventory, or permanently inconsistent ledgers.

**Key insight**: No distributed transaction protocol is free. The goal is to pick the protocol whose failure modes are acceptable for the business domain — and to design compensating actions that are idempotent, retryable, and "approximately correct" when perfect reversal is impossible.

---

## 3. Core Principles

**1. Atomicity at the saga level, not the DB level**
In microservices, you abandon 2-phase commit across service boundaries and instead sequence local ACID transactions with explicit compensation logic.

**2. Idempotency is non-negotiable**
Every step — forward and compensating — must be safe to apply multiple times. Message delivery guarantees are at-least-once; if step 3 is retried, it must not double-charge a customer.

**3. Compensating transactions are first-class design artifacts**
Compensation is not a rollback. A payment already sent to an external processor cannot be "un-sent". Compensation issues a refund — a new forward operation that semantically reverses the effect. Designs that do not account for compensation complexity fail in production.

**4. Transactional outbox over dual writes**
Never write to a database AND publish to a message broker in the same code path without the outbox pattern. Any crash between the two operations produces divergence: data committed to DB but event never published, or event published but DB write rolled back.

**5. Exactly-once is a myth at the transport layer; achieve it with idempotent consumers**
Brokers offer at-most-once (fire-and-forget) or at-least-once (acknowledged delivery with retry). Exactly-once semantics at the application layer are achieved by storing a deduplication/idempotency key alongside the processing result in a transactional write.

**6. Eventual consistency is a contract, not an accident**
Services that tolerate eventual consistency must communicate this clearly to users: "Your order is being processed" rather than "Your order is confirmed." Read-your-writes, monotonic reads, and causal consistency are progressively stronger guarantees that may require sticky sessions, version vectors, or read-from-primary routing.

---

## 4. Types / Architectures / Strategies

### 4.1 Two-Phase Commit (2PC)

The canonical distributed commit protocol.

**Phase 1 — Prepare (Voting phase)**
The coordinator sends a `PREPARE` message to all participants. Each participant:
1. Acquires all necessary locks.
2. Writes an undo log entry (so it can abort if needed).
3. Writes a redo log entry (so it can commit if instructed).
4. Replies `VOTE_COMMIT` or `VOTE_ABORT`.

**Phase 2 — Commit/Abort (Decision phase)**
- If all votes are `VOTE_COMMIT`: coordinator writes `COMMIT` to its log, sends `COMMIT` to all participants, participants release locks.
- If any vote is `VOTE_ABORT`: coordinator sends `ABORT`, participants roll back using undo logs and release locks.

**Coordinator crash scenarios**

| Crash point | Effect |
|---|---|
| Before sending PREPARE | Participants never voted; safe abort on timeout |
| After some PREPAREs sent, before all | Some participants locked and waiting; coordinator recovers and re-sends or aborts |
| After all votes received, before writing COMMIT to log | Recovery: coordinator sees no decision log; aborts the transaction |
| After writing COMMIT to log, before sending to participants | Recovery: coordinator re-sends COMMIT to all participants (idempotent) |
| After sending COMMIT to some but not all | Recovery: re-sends to remaining participants; they apply COMMIT idempotently |

**The blocking problem**
A participant that has voted `VOTE_COMMIT` is in the *prepared* (uncertain) state: it may neither commit nor abort on its own, so it holds its locks and waits. It can run a cooperative termination protocol and ask its peers, but that only helps if some reachable peer already knows the decision — if every peer it can reach is also prepared-and-uncertain, none of them can decide, and the transaction is stuck until the coordinator (or its durable decision log) comes back. Note precisely what is blocked: only the prepared participants and anything contending for their locks; a participant that voted `VOTE_ABORT`, or that never received `PREPARE`, can time out and abort unilaterally. In a system with hundreds of participants and an SLA of 100ms, a coordinator failure can hold locks for minutes, causing cascading timeouts. This is why 2PC is rarely acceptable in microservices.

**Why 2PC is rarely used in microservices**
- Participants hold locks across a network round-trip (2× latency minimum).
- Coordinator is a single point of failure.
- Recovery requires the coordinator log to be durable and accessible.
- Heterogeneous services (different databases, messaging systems) rarely expose the XA interface needed for 2PC.
- CAP: 2PC keeps consistency and gives up availability during a partition (it is CP, not "not partition-tolerant") — a participant that cannot reach the coordinator blocks rather than deciding on its own.

```
lock_hold             = 2 x RTT
contended_throughput  = 1 / lock_hold
```

**Read it like this.** "A participant holds its locks for up to the full width of two network round trips, because it cannot know the outcome until the slowest participant has voted and the coordinator has told everyone."

The "2× latency minimum" line is doing a lot of work. It is not the coordinator's latency budget that hurts — it is that the number becomes the *lock hold time* on every row the transaction touched, and lock hold time is the denominator of contended throughput.

| Symbol | What it is |
|--------|------------|
| RTT | One network round trip between coordinator and a participant |
| Phase 1 | PREPARE out, votes back. One RTT, paced by the *slowest* participant |
| Phase 2 | COMMIT/ABORT out, acks back. One more RTT |
| `2 × RTT` | Upper bound on lock hold at a participant, measured from its own PREPARE to the decision. The first participant prepared pays close to the full 2 RTT; the slowest voter pays about 1. Size capacity off the bound |
| Blocking window | If the coordinator dies after phase 1, lock hold becomes "until coordinator recovery" — unbounded |
| Contended throughput | `1 / lock hold time` — serial transactions per second on any single hot row |

**Walk one example.** A 10 ms RTT between services, against the 100 ms SLA named above:

```
  Phase 1 (PREPARE + votes)     10 ms
  Phase 2 (COMMIT + acks)       10 ms
  ------------------------------------
  Lock hold (worst case)        20 ms      vs ~1 ms for a purely local transaction  = 20x

  Against a 100 ms SLA:  100 - 20 = 80 ms left for all business logic

  Throughput on one contended row:
    local  1 ms hold  ->  1000 / 1   = 1,000 txn/s
    2PC   20 ms hold  ->  1000 / 20  =    50 txn/s      20x collapse

  Coordinator crashes after phase 1, recovers in 5 minutes:
    lock hold = 300,000 ms  =  15,000x the normal hold
```

Note that the participant count barely moves the first number — 3 participants or 100, it is
still two round trips, paced by the slowest voter. What the participant count changes is the
*probability* of the last row: every additional participant is another process that can vanish
mid-protocol, and another set of locks frozen when the coordinator does. The 20 ms is the
advertised price; the 300,000 ms is the one that ends up in the incident review, and no timeout
setting on the participant side can shorten it, because a participant that voted `VOTE_COMMIT`
has surrendered its right to decide.



### 4.2 Three-Phase Commit (3PC)

Adds a `PRE-COMMIT` phase between prepare and commit to allow participants to determine the coordinator's decision even if it crashes.

**Phases**: `PREPARE` → `PRE-COMMIT` (ack that coordinator decided COMMIT) → `COMMIT`

**What it solves**: In 2PC, a participant in the prepared state cannot distinguish between "coordinator crashed before deciding" and "coordinator decided ABORT". In 3PC, if a participant has received `PRE-COMMIT`, it knows the coordinator decided COMMIT, so it can complete the commit independently.

**What it does NOT solve**: 3PC is non-blocking only under a *synchronous* model — bounded message delay, bounded clock drift, and fail-stop crashes — because its timeouts must be able to distinguish "crashed" from "slow". Real networks are asynchronous, and non-blocking atomic commitment there is as hard as consensus, which FLP (Fischer, Lynch and Paterson, 1985) proved no deterministic protocol can guarantee — agreement and termination together, with even one crash failure. So 3PC does not "solve" 2PC's blocking in a real network; it only moves the failure. Worse, its timeout-driven unilateral commit trades safety away: during a network partition two groups can time out and decide different outcomes (split-brain), so 3PC can end up *inconsistent* where 2PC would merely have stalled. That, not a lack of partition tolerance, is why 3PC is essentially never used in practice; production systems reach for consensus-replicated coordinators (Paxos/Raft) instead.

### 4.3 Saga Pattern

A saga (Garcia-Molina and Salem, "Sagas", SIGMOD 1987) is a sequence of local transactions. Each local transaction updates a single service's database and publishes an event or sends a command. If step N fails, steps N-1 through 1 are compensated in reverse order. As the original paper defines it, a compensating transaction undoes a step **semantically** — it restores an acceptable approximation of the prior state, not the exact prior state.

**Choreography-based saga**
- No central coordinator.
- Each service listens for events and publishes events.
- Service A completes its local transaction → publishes `OrderCreated` → Service B listens → completes its work → publishes `InventoryReserved` → Service C listens, etc.
- Compensation: Service C publishes `PaymentFailed` → Service B listens and publishes `InventoryReleased` → Service A listens and publishes `OrderCancelled`.

Pros: loose coupling, no single point of failure.
Cons: difficult to track overall saga state, cyclic event dependencies are hard to reason about, debugging requires correlating events across many service logs, business logic is scattered.

**Orchestration-based saga**
- A central saga orchestrator sends commands to services and receives replies.
- The orchestrator maintains an explicit state machine for the saga.
- All business logic for the workflow is in one place.
- Easier to monitor, debug, and add compensations.

Pros: centralized state, clear workflow visibility, straightforward error handling.
Cons: orchestrator is a central dependency (though it can be replicated and stateless with persistent state in DB), risk of anemic services if orchestrator absorbs too much domain logic.

**Compensating transaction design rules**
1. Compensations must be idempotent: if the compensation message is delivered twice, the second delivery must have no additional effect.
2. Compensations must be retryable: network failures during compensation must not leave the saga in a partially compensated state.
3. Compensations may be approximate: a "cancel subscription" compensation might not restore a user's deleted data; the business must accept this semantic limitation.
4. Never compensate a compensation: if compensating transaction T_i fails after several retries, alert an operator for manual intervention rather than trying to compensate the compensation.
5. Design forward recovery as well: before compensating, consider whether retrying the failed forward step is safer (e.g., a transient downstream timeout).

### 4.4 Transactional Outbox Pattern

Solves the dual-write problem between a database and a message broker.

**The dual-write problem (broken pattern)**
```java
// BROKEN: crash between DB commit and broker publish loses the event
orderRepository.save(order);          // commits
messageBroker.publish(orderCreated);  // if this fails, event is lost forever
```

**The fix: transactional outbox**
```java
// CORRECT: both writes happen in one DB transaction
@Transactional
public void placeOrder(Order order) {
    orderRepository.save(order);
    outboxRepository.save(new OutboxEvent(
        UUID.randomUUID(),
        "OrderCreated",
        serialize(order),
        Instant.now()
    ));
    // Both committed atomically. Event will be published by relay.
}
```

A separate **relay process** polls the `outbox_events` table and publishes to the broker, then marks records as published (or deletes them). The relay guarantees at-least-once delivery; consumers must be idempotent.

**CDC alternative**: Change Data Capture tools (Debezium) tail the database write-ahead log (WAL/binlog) and stream row changes to Kafka without polling. This avoids polling overhead and captures changes with sub-second latency, but adds operational complexity (Kafka Connect cluster, connector configuration, schema registry).

### 4.5 Transactional Inbox (Idempotent Consumer)

The flip side of the outbox: the consumer side.

When a service receives an event, it must process it exactly once. At-least-once delivery from the broker means the same event may arrive multiple times (network retry, consumer restart, rebalance).

**Pattern**: before processing, attempt to insert the message's idempotency key into an `inbox_events` table within the same transaction that applies the business operation. If the insert fails (duplicate key), skip processing. If both succeed, commit atomically.

```sql
CREATE TABLE inbox_events (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    received_at     TIMESTAMP NOT NULL,
    processed_at    TIMESTAMP
);

-- Unique index on idempotency_key prevents double-processing
CREATE UNIQUE INDEX idx_inbox_key ON inbox_events(idempotency_key);
```

### 4.6 Idempotency Keys for API Requests

HTTP APIs that trigger mutations (payment, order placement) must be safe to retry from clients.

**Pattern**:
1. Client generates a UUID v4 and sends it in `Idempotency-Key: <uuid>` header.
2. Server checks a `idempotency_requests` table for this key.
3. If found and processing is complete, return the cached response immediately.
4. If found and processing is in-flight, return `409 Conflict` (or wait and retry).
5. If not found, process the request, store the result alongside the key in a single transaction.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    client(["Client sends<br/>Idempotency-Key"]) --> lookup{"key in<br/>idempotency_requests?"}
    lookup -->|"not found"| process("Process request,<br/>store result + key<br/>in one transaction")
    lookup -->|"found, complete"| cached("Return cached<br/>response")
    lookup -->|"found, in-flight"| conflict("Return 409 Conflict<br/>or wait and retry")
    process --> respond(["Response to client"])
    cached --> respond
    conflict --> respond

    class client io
    class lookup mathOp
    class process train
    class cached base
    class conflict lossN
    class respond io
```

The single lookup is the whole pattern: a new key falls through to normal processing, a completed key short-circuits to the stored response, and an in-flight key — the retry racing its own original request — gets a 409 instead of double-processing, which matches Stripe's production behaviour: Stripe's HTTP status reference defines 409 Conflict as "the request conflicts with another request (perhaps due to using the same idempotent key)", and keys are retained for at least 24 hours (section 7).

Idempotency keys should expire after a reasonable window (24 hours for payments, 7 days for long-running orders) to prevent unbounded table growth.

### 4.7 Delivery Semantics

| Semantic | Mechanism | Risk | Use Case |
|---|---|---|---|
| At-most-once | Fire-and-forget; no retry | Data loss on failure | Metrics, telemetry, notifications |
| At-least-once | Retry until ack; idempotent consumer required | Duplicate processing | Payments, inventory, orders |
| Exactly-once | Idempotent consumer + transactional inbox | Complexity, latency | Financial ledgers, billing |

Note: Kafka's "exactly-once semantics" (EOS) with transactions and idempotent producers prevents duplicates within the Kafka cluster, but the end-to-end guarantee still requires idempotent consumer logic when writing to external systems.

---

## 5. Architecture Diagrams

### 2PC Flow — Normal and Failure Case

**Normal flow — every participant votes to commit:**

```mermaid
sequenceDiagram
    participant Coord as Coordinator
    participant A as Participant A
    participant B as Participant B
    participant C as Participant C

    Coord->>A: PREPARE
    Coord->>B: PREPARE
    Coord->>C: PREPARE
    A-->>Coord: VOTE_COMMIT
    B-->>Coord: VOTE_COMMIT
    C-->>Coord: VOTE_COMMIT
    Note over Coord: all votes COMMIT<br/>write COMMIT to log
    Coord->>A: COMMIT
    Coord->>B: COMMIT
    Coord->>C: COMMIT
```

All three participants vote `VOTE_COMMIT`, so the coordinator durably logs the decision before fanning out `COMMIT` — the happy path most 2PC discussions stop at.

**Coordinator crash after PREPARE, before the decision is sent:**

```mermaid
sequenceDiagram
    participant Coord as Coordinator
    participant A as Participant A
    participant B as Participant B
    participant C as Participant C

    Coord->>A: PREPARE
    Coord->>B: PREPARE
    Coord->>C: PREPARE
    A-->>Coord: VOTE_COMMIT
    B-->>Coord: VOTE_COMMIT
    C-->>Coord: VOTE_ABORT
    Note over Coord: Coordinator CRASHES<br/>before deciding
    Note over A,B: locks still held,<br/>waiting...<br/>cannot commit or abort<br/>unilaterally
```

A and B already voted `VOTE_COMMIT` and are holding locks when the coordinator crashes; with no decision to act on, they block until it recovers — this is the blocking problem that makes 2PC unsuitable once participants span independently-operated microservices.

### Saga Choreography Flow

```mermaid
sequenceDiagram
    participant O as Order Service
    participant I as Inventory Service
    participant P as Payment Service

    Note over O: Place Order
    O->>I: OrderCreated event
    Note over I: Reserve Inventory
    I->>P: InventoryReserved event
    Note over P: Charge Payment (fails)
    P-->>I: PaymentFailed event
    Note over I: Release Inventory
    I-->>O: ReleaseInventory event
    Note over O: Cancel Order<br/>publishes OrderCancelled event
```

Each service reacts only to the events it subscribes to — there is no central coordinator watching the whole flow, so when Charge Payment fails the compensation cascades in reverse (Payment to Inventory to Order), exactly as section 4.3 describes.

### Saga Orchestration Flow

```mermaid
stateDiagram-v2
    [*] --> STARTED
    STARTED --> ORDER_CREATED: send CreateOrderCmd<br/>recv OrderCreatedReply
    ORDER_CREATED --> INVENTORY_RESERVED: send ReserveInventoryCmd<br/>recv InventoryReservedReply
    INVENTORY_RESERVED --> PAYMENT_FAILED: send ChargePaymentCmd<br/>recv PaymentFailedReply
    PAYMENT_FAILED --> COMPENSATING: send ReleaseInventoryCmd<br/>recv InventoryReleasedReply
    COMPENSATING --> CANCELLED: send CancelOrderCmd<br/>recv OrderCancelledReply
    CANCELLED --> [*]
```

The orchestrator sends exactly one command and waits for exactly one reply per transition; Order Service, Inventory Svc, and Payment Svc never talk to each other directly, only to this state machine — which is what makes the workflow easy to monitor and debug.

### Transactional Outbox + CDC Flow

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    svc("Service write path<br/>DB Transaction:<br/>INSERT orders +<br/>INSERT outbox_events") -->|"DB WAL<br/>/ binlog"| connector("Debezium Connector<br/>tails WAL")
    connector --> topic
    topic@{ icon: "logos:kafka", form: "square", label: "Kafka Topic", pos: "b", h: 44 }
    topic --> consumer(["Consumer<br/>idempotent inbox"])

    class svc req
    class connector mathOp
    class consumer io
```

Debezium tails the WAL/binlog directly instead of polling the outbox table, so the order write and its outbox insert commit in one local transaction while the CDC connector streams changes to Kafka with no added query load on the database. End-to-end lag is bounded by the WAL flush plus connector and broker latency rather than by a poll interval — typically well under a second, though Debezium publishes no latency guarantee and the real figure is workload- and deployment-specific.

---

## 6. How It Works — Detailed Mechanics

### 6.1 Outbox Pattern with Spring / JPA

```java
// OutboxEvent entity
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean published;

    @Column
    private Instant publishedAt;

    // standard constructors, getters, setters
}

// Service layer — atomic write of domain entity + outbox record
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional  // single DB transaction — both writes commit or both roll back
    public Order placeOrder(PlaceOrderRequest request) {
        Order order = new Order(request.getCustomerId(), request.getItems());
        orderRepository.save(order);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setEventType("OrderCreated");
        outboxEvent.setPayload(serialize(order));
        outboxEvent.setCreatedAt(Instant.now());
        outboxEvent.setPublished(false);
        outboxRepository.save(outboxEvent);

        return order;
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}

// Relay process — polls outbox and publishes to broker
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 500)  // poll every 500ms
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository
            .findTop100ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {
                // Since spring-kafka 3.0 send() returns CompletableFuture<SendResult>,
                // not the removed ListenableFuture — there is no addCallback(). Block
                // here on purpose: the "published" flag must be set inside THIS
                // transaction, so it cannot be set from an async producer thread.
                kafkaTemplate.send("domain-events", event.getId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);

                event.setPublished(true);      // managed entity — flushed on commit
                event.setPublishedAt(Instant.now());
            } catch (Exception ex) {
                // Leave published = false; the next poll retries. At-least-once by design.
                log.error("Failed to publish event {}, will retry next poll", event.getId(), ex);
            }
        }
    }
}
```

```sql
-- Outbox table DDL
CREATE TABLE outbox_events (
    id          UUID        PRIMARY KEY,
    event_type  VARCHAR(100) NOT NULL,
    payload     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    published   BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at)
    WHERE published = FALSE;
```

### 6.2 Saga Orchestrator with Explicit State Machine

```java
// Saga state enum
public enum OrderSagaState {
    STARTED,
    ORDER_CREATED,
    INVENTORY_RESERVED,
    PAYMENT_CHARGED,
    COMPLETED,
    PAYMENT_FAILED,
    INVENTORY_RELEASING,
    ORDER_CANCELLING,
    CANCELLED,
    FAILED
}

// Saga entity persisted to DB
@Entity
@Table(name = "order_sagas")
public class OrderSaga {

    @Id
    private UUID sagaId;

    private UUID orderId;

    @Enumerated(EnumType.STRING)
    private OrderSagaState state;

    private Instant createdAt;
    private Instant updatedAt;
    private int failureCount;
    private String failureReason;
}

// Orchestrator service
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final OrderSagaRepository sagaRepository;
    private final CommandGateway commandGateway;   // sends commands to downstream services

    @Transactional
    public void start(UUID orderId, PlaceOrderRequest request) {
        OrderSaga saga = new OrderSaga(UUID.randomUUID(), orderId, OrderSagaState.STARTED);
        sagaRepository.save(saga);
        commandGateway.send(new CreateOrderCommand(saga.getSagaId(), request));
    }

    @Transactional
    public void onOrderCreated(UUID sagaId, UUID orderId) {
        OrderSaga saga = sagaRepository.findById(sagaId).orElseThrow();
        saga.setState(OrderSagaState.ORDER_CREATED);
        sagaRepository.save(saga);
        commandGateway.send(new ReserveInventoryCommand(sagaId, orderId));
    }

    @Transactional
    public void onInventoryReserved(UUID sagaId) {
        OrderSaga saga = sagaRepository.findById(sagaId).orElseThrow();
        saga.setState(OrderSagaState.INVENTORY_RESERVED);
        sagaRepository.save(saga);
        commandGateway.send(new ChargePaymentCommand(sagaId, saga.getOrderId()));
    }

    @Transactional
    public void onPaymentFailed(UUID sagaId, String reason) {
        OrderSaga saga = sagaRepository.findById(sagaId).orElseThrow();
        saga.setState(OrderSagaState.PAYMENT_FAILED);
        saga.setFailureReason(reason);
        sagaRepository.save(saga);
        // Begin compensation
        commandGateway.send(new ReleaseInventoryCommand(sagaId, saga.getOrderId()));
    }

    @Transactional
    public void onInventoryReleased(UUID sagaId) {
        OrderSaga saga = sagaRepository.findById(sagaId).orElseThrow();
        saga.setState(OrderSagaState.ORDER_CANCELLING);
        sagaRepository.save(saga);
        commandGateway.send(new CancelOrderCommand(sagaId, saga.getOrderId()));
    }

    @Transactional
    public void onOrderCancelled(UUID sagaId) {
        OrderSaga saga = sagaRepository.findById(sagaId).orElseThrow();
        saga.setState(OrderSagaState.CANCELLED);
        sagaRepository.save(saga);
    }
}
```

### 6.3 Idempotent Consumer with Transactional Inbox

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InboxRepository inboxRepository;
    private final InventoryService inventoryService;

    @KafkaListener(topics = "domain-events", groupId = "inventory-service")
    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        String idempotencyKey = record.topic() + "-" + record.partition() + "-" + record.offset();

        // Cheap fast path for an obvious redelivery. It is NOT the safety mechanism:
        // check-then-insert is a race. The PRIMARY KEY on idempotency_key is what
        // actually prevents double-processing — a concurrent duplicate fails the
        // save() below with DataIntegrityViolationException and rolls this transaction back.
        if (inboxRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Duplicate event, skipping: {}", idempotencyKey);
            return;
        }

        // Record the inbox entry and apply business logic in one transaction
        inboxRepository.save(new InboxEvent(idempotencyKey, Instant.now()));
        DomainEvent event = deserialize(record.value());

        if ("OrderCreated".equals(event.getType())) {
            inventoryService.reserve(event.getOrderId(), event.getItems());
        }
    }
}
```

### 6.4 Broken 2PC vs. Saga Compensation

```java
// BROKEN: attempting 2PC across microservices via XA — not feasible in practice
// XA requires all resources to implement the XA interface (javax.transaction.xa.XAResource)
// External payment processors, NoSQL stores, and most microservice REST APIs do NOT.
@Transactional  // this @Transactional only covers a single DataSource
public void brokenTransfer(UUID fromAccount, UUID toAccount, BigDecimal amount) {
    // This does NOT coordinate across two separate microservice DBs
    accountServiceClient.debit(fromAccount, amount);   // remote HTTP call — OUTSIDE transaction scope
    paymentServiceClient.credit(toAccount, amount);    // if this fails, debit is NOT rolled back
}

// FIX: Saga with compensating transaction
@Transactional
public void startTransferSaga(UUID sagaId, UUID fromAccount, UUID toAccount, BigDecimal amount) {
    TransferSaga saga = new TransferSaga(sagaId, fromAccount, toAccount, amount, SagaState.STARTED);
    sagaRepository.save(saga);

    // Step 1: debit source account (local transaction in account service)
    commandGateway.send(new DebitAccountCommand(sagaId, fromAccount, amount));
}

// If credit fails, issue compensating debit reversal
@Transactional
public void onCreditFailed(UUID sagaId) {
    TransferSaga saga = sagaRepository.findById(sagaId).orElseThrow();
    // Compensating transaction: reverse the debit
    commandGateway.send(new ReverseDebitCommand(sagaId, saga.getFromAccount(), saga.getAmount()));
    saga.setState(SagaState.COMPENSATING);
    sagaRepository.save(saga);
}
```

### 6.5 Delivery Semantics Comparison — Concrete Numbers

| Semantic | Kafka Config | Throughput Impact | Duplicate Risk |
|---|---|---|---|
| At-most-once | `acks=0`, no retry | Highest — no ack wait | High data loss |
| At-least-once | `acks=all`, `retries=MAX_INT` (already the default), `enable.idempotence=false` — note this is an explicit *downgrade*, since idempotence is enabled by default when no conflicting config is set | Medium | Low; duplicates possible |
| Exactly-once (producer) | `enable.idempotence=true`, `acks=all`, `transactional.id=x` | ~3% throughput loss at 1 KB records committing every 100 ms (Confluent's own measurement); materially worse with smaller records or shorter commit intervals | None within Kafka cluster |
| End-to-end exactly-once | Above + transactional inbox in consumer | Additional DB write per message | None |

The transaction overhead is per commit, not per message, so it is independent of how many
messages a transaction contains — batching more messages per commit amortises it. Confluent
also reports no throughput degradation for consumers reading in `read_committed` mode, since
zero-copy reads are preserved.

---

## 7. Real-World Examples

**AWS (reference architecture)**: AWS Prescriptive Guidance publishes the saga orchestration pattern as its answer for transactions spanning multiple service-owned databases — "a central coordinator (an orchestrator) to preserve data integrity in distributed transactions that span multiple services" — with a reference implementation on Step Functions standard workflows plus DynamoDB, and notes that using Step Functions removes the orchestrator single-point-of-failure by spreading it across Availability Zones. Applied to an order flow: compensation for a failed payment releases reserved inventory and cancels the order record, and every compensation command needs explicit dead-letter handling routing to a manual review queue. (Amazon's own internal retail order pipeline is not publicly documented — treat any specific claim about it as unverified.)

**Uber**: Gulfstream, Uber's fifth-generation payment platform, is built on double-entry accounting and idempotency: the order-insertion service "creates the payment orders, publishes the order data to messaging topics and persists it to OrderStore", persists every account change to an entity change log versioned per user, and uses deterministically generated unique order identifiers to guarantee once-only order processing. Uber's published account is a *message-queue plus change-log* design; it does not describe a Debezium-style outbox relay, so do not attribute one to it.

**Stripe**: Every API call that creates a charge or transfer accepts an `Idempotency-Key` header. Stripe saves "the resulting status code and body of the first request made for any given idempotency key, regardless of whether it succeeds or fails", compares incoming parameters against the original request, and removes keys after they are at least 24 hours old. Duplicate requests within that window return the original response without reprocessing; a request that collides with one still executing returns 409 Conflict. This is the production-grade idempotency key pattern.

**Google Spanner**: Uses TrueTime and 2PC for cross-shard transactions, layered over Paxos. One of the participant groups is chosen as the coordinator, and — this is the key part — the coordinator's transaction-manager state is stored in that group's Paxos log and therefore replicated, so a coordinator crash is survived by leader election rather than blocking every participant. It is not fast: the OSDI 2012 paper's Table 4 measures mean commit latency of **17.0 ms ± 1.4 for one participant**, 42.7 ms for 50 and 71.4 ms for 100. This works because Spanner controls both the coordinator and all participants within its own infrastructure — not feasible for cross-organisation microservices.

**Netflix**: Built and published **DBLog**, its own change-data-capture framework (Andreakis and Papapanagiotou, arXiv 2010.12597, submitted 23 October 2020), which interleaves transaction-log events with watermarked table selects so a full-state dump never stalls log progress. The paper states DBLog "is currently used in production by tens of microservices at Netflix". The general lesson holds — put the outbox table in the same database as the domain tables and let CDC tail the log — but the specific stack is Netflix's own, and no public Netflix source describes a Debezium-plus-wal2json outbox pipeline.

---

## 8. Tradeoffs

### Protocol Comparison

The "Partition Tolerance" column that usually appears here is dropped on purpose: every one of
these protocols runs on a network that can partition, so none of them can opt out of P. What
actually differs is the *behaviour during* a partition, which is the only thing CAP constrains.

| Protocol | Consistency | Availability | Blocking | Behaviour during a partition | Complexity |
|---|---|---|---|---|---|
| 2PC | Strong | Low (coordinator SPOF) | Yes — on coordinator crash | Stays safe, stops serving: prepared participants block (CP) | Medium |
| 3PC | Strong only if the network is synchronous | Medium | Reduced | Can lose safety: two sides may time out and decide differently (split-brain) | High |
| Saga (Choreography) | Eventual | High | No | Stays available, each side commits locally and converges after | Medium (distributed logic) |
| Saga (Orchestration) | Eventual | High (orchestrator is stateless) | No | Stays available; orchestrator resumes from persisted state | Medium (centralised logic) |
| Outbox + CDC | Eventual | High | No | Stays available; events buffer in the outbox until the relay reconnects | Low–Medium |

```mermaid
quadrantChart
    title Distributed transaction protocols: consistency vs availability
    x-axis Low Availability --> High Availability
    y-axis Eventual Consistency --> Strong Consistency
    quadrant-1 Ideal rarely achievable
    quadrant-2 Strong but blocking
    quadrant-3 Avoid this corner
    quadrant-4 Eventual and resilient
    2PC: [0.15, 0.95]
    3PC: [0.4, 0.85]
    Saga Choreography: [0.8, 0.25]
    Saga Orchestration: [0.75, 0.32]
    Outbox + CDC: [0.9, 0.15]
```

Plotting the table above onto the CAP tension named in section 1 shows the split cleanly: 2PC buys strong consistency by giving up availability when a partition hits (coordinator SPOF, blocking on crash), and 3PC buys back part of that availability by risking the consistency — so its position on the vertical axis is optimistic, holding only while the network stays synchronous. Every saga variant and outbox + CDC land in the eventual-and-resilient corner. No protocol here reaches the empty top-right quadrant, because CAP forbids exactly that corner during a partition.

### Consistency Model Comparison

These are *single-object* consistency models — they constrain the ordering of reads and writes.
They are not isolation levels, which constrain the interleaving of multi-object transactions.
Serializability is the transaction-level property (some serial order exists); linearizability
is the recency property (that order respects real time). Strict serializability is both.

| Model | Guarantee | Latency | Implementation |
|---|---|---|---|
| Linearizability | Every operation appears to take effect instantaneously at some point between its call and its return, so a read never returns a value older than a completed write — a real-time guarantee | Highest | Consensus (Raft/Paxos); 2PC only when layered over consensus-replicated participants, as in Spanner |
| Sequential consistency | All nodes see one common order that respects each process's own program order — but that order need not match real time, so a read may return a stale value | High | Total-order broadcast |
| Causal consistency | Causally related ops seen in order | Medium | Vector clocks, causal tokens |
| Read-your-writes | You always see your own writes | Low–Medium | Sticky sessions or sync replication |
| Eventual consistency | All nodes converge given no new writes | Lowest | Async replication |

### BASE vs. ACID

| Property | ACID | BASE |
|---|---|---|
| Atomicity | All or nothing per transaction | Not guaranteed across services |
| Consistency | DB constraints always valid | Application-level invariants, eventually |
| Isolation | Serializable to read-committed | Not isolated by default; conflicts possible |
| Durability | Committed writes survive crashes | Durable per service; cross-service durability via sagas |
| Availability | Lower (locks, coordinator wait) | Higher (no blocking protocols) |

---

## 9. When to Use / When NOT to Use

### When to Use 2PC
- All participants implement XA (e.g., multiple relational databases owned by the same team).
- Operations are short-lived (sub-second) and participant availability is high.
- Strong consistency is a hard requirement and partition probability is low (same data center).
- Example: transferring funds between two *separate* PostgreSQL instances owned by one team, via an XA transaction manager. Two schemas inside a single instance need no 2PC at all — one local transaction already spans them. Note that PostgreSQL ships with `max_prepared_transactions = 0`, which disables `PREPARE TRANSACTION` entirely; you must raise it (at least to `max_connections`) and restart before any XA coordinator can prepare against it, and orphaned prepared transactions then hold locks and pin the oldest xmin until they are resolved.

### When NOT to Use 2PC
- Cross-organisation services or third-party APIs (no XA support).
- High-availability requirements — coordinator failure blocks all participants.
- Long-running operations (inventory holds of minutes/hours).
- Any scenario involving heterogeneous datastores (Redis + PostgreSQL + Kafka).

### When to Use Sagas
- Microservices architecture where each service owns its own database.
- Business operations span 3 or more services.
- Long-running workflows (order fulfillment over hours/days).
- When you can define meaningful compensating transactions for all steps.

### When NOT to Use Sagas
- When a compensating transaction does not exist or is impractical (e.g., launching a missile — there is no "undo").
- When strict isolation is required (saga intermediate states are visible to other requests during execution — a saga is **ACD**: it keeps Atomicity, Consistency and Durability and gives up Isolation).
- Simple two-table updates within a single service — use a local ACID transaction instead.

### When to Use the Outbox Pattern
- Any time a service writes to a database and must publish an event to a message broker.
- This is essentially always the correct approach; dual writes should never be used.

### When NOT to Use Orchestration Sagas
- When the number of services in the flow is small (2–3) and choreography is simpler to reason about.
- When the orchestrator introduces a new dependency that creates circular coupling.

---

## 10. Common Pitfalls

Every pitfall below is an **illustrative composite**: the failure mode, the broken code and the
fix are real and recurrent, but the companies, volumes, durations and losses are constructed to
make the mechanics concrete. None of them is a citation to a published incident report.

### Pitfall 1: Dual Write Without Outbox — The Production Nightmare

A team ships order placement with this code:

```java
// BROKEN production code — dual write
@Transactional
public Order placeOrder(PlaceOrderRequest req) {
    Order order = orderRepository.save(new Order(req));
    kafkaTemplate.send("order-events", order.getId().toString(), serialize(order));
    return order;
}
```

On the peak traffic day, a Kafka broker rolls over for a leader election and sends start failing for roughly 90 seconds. Note the actual mechanism, because it is the opposite of what most people assume: `kafkaTemplate.send()` is **asynchronous**. It appends the record to the producer's accumulator and returns a `CompletableFuture` immediately, so `placeOrder` returns normally and the `@Transactional` boundary **commits**. The delivery failure surfaces later, on a producer I/O thread, in a future that this code never inspects — so it is swallowed. Result: about 4,200 orders committed to the database with no corresponding events. The inventory service never reserved stock. Orders showed as "confirmed" to customers but had no inventory allocation, and the cleanup was a reconciliation job.

The mirror-image failure exists too and is worth naming: when the send *succeeds* and the record reaches the broker, but the surrounding transaction then rolls back — a later exception in the method, or a failure at commit time — you get the divergence the other way round: an event on the topic for an order that does not exist, and consumers acting on it. (Note also that a send can fail *synchronously*, throwing `TimeoutException` if metadata is unavailable or the buffer stays full past `max.block.ms`, default 60 s; that path does at least roll the transaction back, because the record never enters the accumulator.) Whichever way you order the two writes, one of these two divergences is reachable — which is the whole argument for the outbox.

Fix: transactional outbox. The DB write and the outbox record commit atomically. The relay/CDC process publishes events independently.

### Pitfall 2: Non-Idempotent Compensating Transactions

A payment team built a saga with this compensation:

```java
// BROKEN: compensation is not idempotent
public void compensateCharge(UUID orderId) {
    paymentService.refund(orderId, amount);  // no idempotency key
}
```

When the compensation command is retried (the first attempt timed out in transit), the payment processor issues two refunds and the customer receives double the refund. Left unnoticed across a batch of retries, the loss accumulates silently until someone reads the billing anomaly report.

Fix: pass an idempotency key derived from the saga ID and step:

```java
public void compensateCharge(UUID sagaId, UUID orderId) {
    String idempotencyKey = "compensation-" + sagaId + "-refund";
    paymentService.refund(orderId, amount, idempotencyKey);
}
```

### Pitfall 3: Saga Without Dead-Letter Handling for Compensations

A team assumed compensating transactions always succeed. A saga entered the `COMPENSATING` state and sent a `ReleaseInventoryCommand`. The inventory service was down for 20 minutes. The saga message queue had no dead-letter configuration. After 3 retries with exponential backoff, the command was dropped silently. The saga was stuck in `COMPENSATING` state indefinitely, inventory was never released, and the order was never formally cancelled in the order service.

Fix: every compensation command must have a dead-letter queue. A monitoring alert must fire if a saga has been in a compensating state for more than N minutes. Manual operator workflows must exist for stuck sagas.

### Pitfall 4: Large Outbox Table Causing Read Latency

An outbox relay polls `SELECT * FROM outbox_events WHERE published = FALSE ORDER BY created_at` every 500ms. Over 3 months the outbox table accumulates 50 million rows, because the `published = TRUE` rows are never deleted. No index supports the query: the predicate matches only the small unpublished backlog, but with nothing indexed the planner must sequentially scan all 50 million rows and then sort them by `created_at`, and the poll takes seconds.

The trap is subtle enough to be worth stating precisely, because "just add an index on `published`" is the wrong lesson. A plain B-tree on a boolean *would* be picked here — `published = FALSE` is a highly selective predicate once almost every row is `TRUE`, so the estimated row count is tiny. What that index still cannot do is supply the `ORDER BY created_at`, and it carries an entry for all 50 million rows, so it bloats and has to be maintained on every insert and every publish flip. The partial index solves both at once: it indexes `created_at` (giving the ordering for free) and only over the rows matching `published = FALSE` (so it stays roughly the size of the backlog, not the table).

Fix:
1. Create a partial index: `CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published = FALSE`.
2. Schedule a cleanup job: `DELETE FROM outbox_events WHERE published = TRUE AND published_at < NOW() - INTERVAL '7 days'`.

**Put simply.** "An outbox table without a retention policy is not a queue, it is an append-only log of everything that ever happened — and the relay's scan cost grows with the log, not with the backlog."

The subtle part is that the relay only ever *cares about* the unpublished rows, which stay a tiny constant. It is the rows it does not care about that destroy it, because without a partial index the planner still has to walk them.

| Symbol | What it is |
|--------|------------|
| Poll interval | `fixedDelay = 500` ms — how often the relay scans for unpublished rows |
| Publish latency | `0` to one full interval. Averages half the interval |
| Ingest rate | Events written per second by the application |
| Table size | `ingest rate × retention` — governed entirely by the cleanup job |
| Partial index | `... WHERE published = FALSE` — indexes `created_at` over only the rows the relay reads, so it supplies the ORDER BY *and* stays backlog-sized |
| Selectivity | Fraction of rows matching the predicate. A small fraction favours an index scan, a large fraction favours a sequential scan — but selectivity alone never supplies an ordering |

**Walk one example.** The incident's own numbers, and what retention changes:

```
  Publish latency added by polling:
    average  =  500 ms / 2  =  250 ms
    worst    =  500 ms

  Table growth, 50,000,000 rows over 3 months (90 days):
    per day     =  50,000,000 / 90        =  555,556 rows/day
    per second  =  50,000,000 / 7,776,000 =      6.4 rows/s

  With 7-day retention instead of forever:
    resident rows  =  555,556 x 7  =  3,888,889
    reduction      =  50,000,000 / 3,888,889  =  12.9x smaller
```

A sustained 6.4 rows/second is a trivially small write rate — which is the whole lesson. Nothing
about the traffic was extreme; the table reached 50 million rows purely because nothing ever
deleted from it, and once a poll takes longer than the 500 ms interval the relay is permanently
behind, overlapping its own runs and pinning the database. Both fixes are needed and they fix
different things: the partial index makes the *scan* cheap regardless of table size, and the
retention job makes the *table* small. Ship only the index and storage grows without bound
(and the index is still rebuilt on every publish flip); ship only the cleanup and the relay is
still doing an unindexed sort on every poll.

### Pitfall 5: Missing Idempotency on Forward Steps

A saga's `ReserveInventoryCommand` consumer was not idempotent. During a Kafka consumer group rebalance, the same message was redelivered to a second consumer instance before the first had committed its offset. Both instances processed the command concurrently, each checking stock availability and reserving — both succeeded. The inventory for one item was double-reserved, causing an oversell.

Fix: transactional inbox (idempotency key insert before processing). All saga command consumers must be idempotent.

---

## 11. Technologies & Tools

### Saga Orchestration Frameworks

| Tool | Language | Description |
|---|---|---|
| Axon Framework | Java | Full CQRS + event sourcing + saga orchestration, built-in saga lifecycle management |
| Temporal | Go/Java/Python/TypeScript | Durable workflow engine; saga logic written as code with automatic retry/compensation |
| Conductor OSS | Java | Workflow engine with visual designer; JSON-DSL for orchestration. Created at Netflix, which stopped maintaining the OSS repo in December 2023; now stewarded by Orkes and the Conductor OSS community |
| Apache Camel Saga EIP | Java | Lightweight saga support in Camel route DSL |
| Spring State Machine | Java | State machine framework; useful for saga orchestrator state management |
| Eventuate Tram | Java | Library by Chris Richardson specifically for the saga pattern + outbox pattern |

### CDC / Outbox Relay

| Tool | Description |
|---|---|
| Debezium | Open-source CDC connector for PostgreSQL, MySQL, MongoDB, Oracle; runs on Kafka Connect |
| Maxwell's Daemon | MySQL binlog CDC; simpler than Debezium, fewer connectors |
| Postgres logical replication + custom | A custom relay consuming a replication slot. `pgoutput` is built into PostgreSQL 10+ and needs no extension; any other output plugin is a separate extension you install and maintain yourself |
| AWS DMS | Managed CDC service for databases hosted on AWS |

### Message Brokers

| Broker | Delivery | Ordering | Retention | Ideal For |
|---|---|---|---|---|
| Apache Kafka | At-least-once (EOS available) | Per partition | Configurable (log) | High-throughput event streaming, outbox relay |
| RabbitMQ | At-least-once or at-most-once | Per queue | Message TTL | Task queues, saga commands |
| AWS SQS | At-least-once (FIFO: no duplicates introduced within a **5-minute** deduplication interval) | FIFO queues | 14 days max | AWS-native microservices |
| AWS SNS + SQS | At-least-once | No guarantee (fan-out) | SQS retention | Event fan-out to multiple consumers |

### Distributed Transaction Databases

| Database | Protocol | Notes |
|---|---|---|
| Google Spanner | External consistency via TrueTime | Globally distributed; 2PC layered over Paxos, so the coordinator's state is replicated. OSDI 2012 reports 17.0 ms mean commit latency for one participant, rising to 71.4 ms at 100 |
| CockroachDB | Serializable via Raft + MVCC | Open-source Spanner-like, PostgreSQL-compatible |
| TiDB | Percolator (2PC with TSO) | MySQL-compatible, HTAP |
| YugabyteDB | Distributed SQL, Raft | PostgreSQL-compatible wire |

---

## 12. Interview Questions with Answers

**Q1: What is the blocking problem in 2PC and why does it make 2PC unsuitable for microservices?**
**Short:** A prepared 2PC participant must hold its locks until the coordinator decides, so a coordinator crash blocks other transactions indefinitely.

In 2PC, once a participant votes `VOTE_COMMIT`, it cannot unilaterally abort — it must hold all its locks until it hears the coordinator's decision. If the coordinator crashes after the prepare phase, every prepared participant stays uncertain — unable to commit or abort, holding locks that block other transactions — until the coordinator or its durable decision log comes back, or until a peer that already knows the outcome can tell it. In a microservices environment, services are independently deployable and may use different datastores that do not expose an XA interface. The coordinator is a single point of failure, network partitions can leave participants waiting for minutes, and lock hold times across network round-trips degrade throughput unacceptably for most SLAs.

**Q2: How does the saga pattern achieve atomicity without distributed locking?**
**Short:** A saga chains local ACID transactions with compensating transactions to undo prior steps on failure, sacrificing isolation instead of locking.

A saga replaces distributed atomic commits with a sequence of local transactions and compensating transactions. Each service performs an ACID local transaction and publishes an event or responds to a command. If any step fails, all previously completed steps are reversed by executing their compensating transactions in reverse order. There are no distributed locks; intermediate states are visible (sacrificing isolation), and compensating transactions must be idempotent to handle retries. The guarantee achieved is ACD — Atomicity, Consistency and Durability, with Isolation dropped.

**Q3: What is the difference between choreography and orchestration sagas? When would you choose each?**
**Short:** Choreography suits simple linear flows via events, while orchestration suits complex branching workflows via a central state machine.

In a choreography saga, each service reacts to domain events published by the previous step, with no central coordinator. The workflow emerges from event subscriptions. In an orchestration saga, a central orchestrator explicitly sends commands to each service and receives replies, maintaining the workflow state in a persistent state machine. Choreography favors loose coupling and suits simpler linear flows with 3–4 steps. Orchestration suits complex workflows with many branches, conditional paths, and compensation sequences, because all business logic is in one place and much easier to debug and monitor. That is why complex payment and order-fulfillment workflows usually end up orchestrated — and why AWS Prescriptive Guidance publishes saga *orchestration* (on Step Functions) as its reference pattern for transactions spanning several service-owned databases.

**Q4: Why is dual write dangerous and how does the transactional outbox pattern solve it?**
**Short:** The outbox pattern writes the entity and its event in one local transaction, avoiding the inconsistency a crash between two separate writes causes.

Dual write means writing to a database and publishing to a message broker in two separate operations. Any crash between them leaves the two systems inconsistent — the DB committed but the event never published, or the event was published but the DB write was rolled back. The outbox pattern solves this by writing both the domain entity and an `outbox_events` record in a single local ACID database transaction. A separate relay process reads the unpublished outbox records and publishes them to the broker, then marks them published. Because the relay publishes at-least-once and consumers are idempotent, the system achieves effectively-once processing: delivery is still at-least-once, but each event's effect is applied exactly once.

**Q5: What is Change Data Capture (CDC) and how does Debezium work?**
**Short:** Debezium tails a database's WAL or binlog to publish row-level changes to Kafka, avoiding the need to poll an outbox table.

CDC captures every insert, update, and delete from a database by tailing its write-ahead log or binary log — the same log the database uses for replication. Debezium connects as a logical replication client to PostgreSQL — using `pgoutput`, which PostgreSQL 10+ ships natively, or the Protobuf-emitting `decoderbufs` plugin — or as a binlog consumer for MySQL. It reads row-level change events and publishes them to Kafka topics. This means applications do not need to poll an outbox table; Debezium sees each change as the WAL is decoded, so end-to-end lag is bounded by flush plus network rather than by a poll interval. It requires no application code changes for the outbox emit step — only the outbox table structure matters.

**Q6: What makes a compensating transaction "approximately correct" rather than a true rollback?**
**Short:** A compensating transaction reverses business effect with a new forward operation, but some side effects like a sent email cannot truly be undone.

A true database rollback is a physical undo: the exact bytes written are removed, as if the operation never happened. A compensating transaction is a new forward operation with the opposite business effect. "Approximately correct" means the end business state is acceptable, but some side effects cannot be reversed. For example, if step 3 sent a confirmation email to a customer and step 4 fails, the compensation cannot "un-send" the email. The order is cancelled (compensated at the DB level), but the customer received a confirmation that is now incorrect. The business must decide whether to send a follow-up cancellation email or accept the inconsistency. Compensation design must explicitly document these irreversible side effects.

**Q7: What are idempotency keys and how should they be scoped and expired?**
**Short:** An idempotency key deduplicates retries by returning the original response, scoped per operation type with an expiry matching the retry window.

An idempotency key is a client-generated unique identifier (typically UUID v4) attached to a mutating request. The server uses this key to detect and deduplicate retries, returning the original response without reprocessing. Keys should be scoped per operation type (not reused across different endpoint types). They should be stored with the operation's result in a single atomic transaction. Expiry should match the retry window: 24 hours for payment APIs (matching typical client retry timeouts), 7 days for long-running workflows. After expiry, the same key can be used for a genuinely new request. Keys stored without TTL cause unbounded table growth.

**Q8: What is the difference between at-least-once, at-most-once, and exactly-once delivery, and how do you achieve each in Kafka?**
**Short:** Kafka achieves exactly-once internally via idempotent producers and transactions, but exactly-once with external systems needs a transactional inbox.

At-most-once delivery means messages may be lost but are never duplicated; achieved by committing consumer offsets before processing (`enable.auto.commit=true` with short intervals, or manual pre-processing commit). At-least-once means messages are never lost but may be duplicated; achieved with `acks=all`, `retries=MAX_INT`, and committing offsets only after successful processing. Exactly-once within Kafka is achieved with idempotent producers (`enable.idempotence=true` — enabled by default since Kafka 3.0 when no conflicting config is set; it stamps a producer ID and per-partition sequence number so the broker discards a retried duplicate) and Kafka transactions (`transactional.id`, `isolation.level=read_committed`). End-to-end exactly-once with external systems (databases) requires the transactional inbox pattern because Kafka transactions do not span external systems.

**Q9: How do you handle a saga that is stuck in the compensating state?**
**Short:** Alert on sagas stuck compensating past a threshold, give operators a console to retry or mark FAILED, and never silently drop compensation failures.

First, all compensation commands must be sent to queues with configured dead-letter queues (DLQs) and retry policies (exponential backoff with max retries). A scheduled monitoring job should query for sagas in COMPENSATING state for longer than a threshold (e.g., 30 minutes) and alert an oncall engineer. The system should provide an operator console to manually trigger individual compensation commands or mark a saga as FAILED after human review. For truly unrecoverable compensations (downstream service permanently gone), the saga is marked FAILED with audit trail, and a data reconciliation report is generated for manual resolution. The key principle: never silently drop compensation failures.

**Q10: What is causal consistency and when is it not sufficient?**
**Short:** Causal consistency orders only dependent operations, so it cannot reconcile concurrent independent writes needing a global total order.

Causal consistency guarantees that if operation B is causally dependent on operation A (B happened after A, and B's author saw A's result), then all observers see A before B. It is not sufficient when independent concurrent writes must be reconciled — causal consistency does not impose an order on causally unrelated operations. Example: if user A and user B both independently update their profile photos at the same time, causal consistency does not guarantee all nodes see these updates in the same order. For applications requiring a global total order of operations (financial ledgers, counter increments), linearizability or sequential consistency is required, at higher latency cost.

**Q11: How does the transactional inbox prevent double-processing in a Kafka consumer?**
**Short:** The consumer inserts the message key into an inbox table alongside the business write in one transaction, so redeliveries hit a unique-constraint skip.

The consumer inserts the message's idempotency key into an `inbox_events` table inside the same transaction as the business write, so a redelivery hits the unique constraint and is skipped. The key is derived from topic + partition + offset. If the insert succeeds and the business operation succeeds, both commit atomically. If the message is redelivered (consumer rebalance, retry), the insert fails with a unique constraint violation; the consumer catches this exception and skips processing. Because the inbox insert and business operation are in the same local transaction, there is no window where one can commit without the other.

**Q12: Why are sagas described as ACD, and how do microservices deal with their lack of isolation?**
**Short:** Sagas keep Atomicity, Consistency and Durability but expose intermediate state, so services use semantic locking or optimistic versioning to compensate for lost isolation.

Sagas are called ACD because they keep Atomicity, Consistency and Durability but drop Isolation. Each step is a local transaction that commits immediately, so intermediate state is visible to other requests before the saga finishes. Unlike a database transaction running at SERIALIZABLE isolation, there is no saga-level isolation. This creates anomalies: a second request might read the inventory reservation made in step 2 of a saga, but the saga later compensates step 2 (reverses the reservation). The second request made a decision based on state that no longer exists — a "dirty read" at the saga level. Mitigations include: semantic locking (reserving records with a PENDING status that other operations treat as unavailable), optimistic locking with version numbers, and commutative operations that are safe to reorder.

**Q13: Compare the operational complexity of Debezium CDC outbox vs. polling outbox.**
**Short:** Polling outbox needs only a scheduled job, while Debezium CDC needs Kafka Connect infrastructure but removes the poll interval from latency.

The polling outbox is simpler to operate: one scheduled job, no external infrastructure beyond the existing DB and broker. It introduces polling latency (500ms to 5s typical), load on the DB with repeated queries, and potential for stale reads if the partial index is not properly maintained. Debezium CDC requires Kafka Connect infrastructure (at least 3 nodes for production HA), connector management, schema registry for Avro/Protobuf serialization, WAL retention configuration (PostgreSQL `wal_level=logical`, `max_replication_slots`, `max_wal_senders`), and slot lag monitoring (unbounded WAL growth if the slot consumer falls behind). In exchange, Debezium removes the poll interval from the latency budget entirely, adds no polling load on the DB, and supports any table change not just the outbox. Choose polling for simplicity and low volume; choose CDC for latency-sensitive, high-volume, or multi-table change capture.

**Q14: What is semantic locking in the context of sagas, and give a concrete example?**
**Short:** Semantic locking marks a record with a pending status field so concurrent sagas treat it as unavailable without a database-level lock.

Semantic locking is an application-level lock that signals to other transactions that a record is in a pending state as part of an in-flight saga. Unlike a database lock, it is implemented by setting a status field. For example, when a saga begins an inventory reservation, the inventory record is set to `status = PENDING_RESERVATION`. Other sagas or queries that attempt to reserve the same inventory check for this status and either wait (polling) or fail fast (return "temporarily unavailable"). When the saga completes or compensates, the status changes to `RESERVED` or `AVAILABLE`. This prevents concurrent sagas from operating on the same resource simultaneously without database-level locking.

**Q15: What happens to in-flight sagas during a rolling deployment of the orchestrator service?**
**Short:** A stateless orchestrator survives a rolling deployment safely, since any instance can resume a saga by reading its persisted state.

If the orchestrator is stateless (saga state persisted entirely in the database), a rolling deployment is safe: new orchestrator instances pick up saga processing by reading state from the DB. The key requirements are: (1) saga state transitions must be optimistically locked (version column) to prevent two instances from processing the same saga concurrently during overlap, (2) all commands and events must carry the saga ID so any orchestrator instance can handle replies, (3) idempotency keys prevent duplicate command dispatch if a timeout triggers a retry and a new instance also attempts the same step. Frameworks like Temporal handle this with native workflow state management and versioning to support backward-compatible changes to running workflow logic.

---

## 13. Best Practices

**1. Always use the transactional outbox pattern.** Never write to a database and publish to a broker in two separate operations. This rule has no exceptions in production microservices.

**2. Design compensating transactions before forward transactions.** If you cannot define an idempotent, retryable compensation for a step, reconsider whether that step belongs in a saga or whether the business process needs to be redesigned.

**3. Use correlation IDs and structured logging across all saga steps.** Every command, event, and compensation must carry the saga ID and correlation ID. Centralised logging (ELK, Splunk, Loki) must support querying by saga ID to reconstruct the full execution history.

**4. Set explicit timeouts and dead-letter queues for every saga step.** A saga command that times out should go to a DLQ with alerting. Never let a saga block indefinitely.

**5. Monitor saga state distribution.** Dashboards should show sagas by state: STARTED, IN_PROGRESS, COMPENSATING, CANCELLED, COMPLETED, FAILED. A spike in COMPENSATING or FAILED states indicates a downstream service degradation.

**6. Version saga state machines.** When adding steps to a saga, ensure that in-flight sagas using the old state machine can complete. Use explicit state machine versioning or make the orchestrator backward-compatible with older saga records.

**7. Prefer orchestration for anything beyond 3 services.** The debugging and auditing benefits of a centralised orchestrator outweigh the loose-coupling benefits of choreography for complex flows.

**8. Use idempotency keys on all external API calls.** Payment processors, SMS gateways, and email services must receive idempotency keys on every request. Retries without idempotency keys cause double-charges, double-sends, and duplicate side effects.

**9. Implement a saga reconciliation job.** Run a daily or hourly job that queries for sagas in terminal-pending states (COMPENSATING for more than 1 hour, STARTED for more than 24 hours) and pages an operator. This is a safety net, not a primary mechanism.

**10. Test compensation paths explicitly.** Inject failures at each step in integration tests and verify that compensation produces the expected final state. Compensation paths are rarely exercised in development and are most likely to contain bugs.

---

## 14. Case Study

### Airline Seat Booking Saga

**Context**: An airline booking system decomposes into four services: Reservation Service (holds seats), Pricing Service (locks fare), Payment Service (charges card), and Notification Service (sends confirmation email). A booking must atomically reserve a seat, lock the price, and charge the customer. Any failure must fully compensate.

**Architecture**:

```mermaid
stateDiagram-v2
    state "SEAT_RELEASED / CANCELLED" as CANCELLED

    [*] --> STARTED
    STARTED --> SEAT_RESERVED: ReserveSeatCmd<br/>to Reservation Svc
    SEAT_RESERVED --> FARE_LOCKED: LockFareCmd<br/>to Pricing Svc
    FARE_LOCKED --> PAYMENT_CHARGED: ChargePaymentCmd<br/>to Payment Svc (approved)
    FARE_LOCKED --> PAYMENT_FAILED: ChargePaymentCmd<br/>to Payment Svc (declined)
    PAYMENT_CHARGED --> COMPLETED: SendConfirmationCmd<br/>to Notification Svc
    PAYMENT_FAILED --> FARE_UNLOCKED: UnlockFareCmd<br/>to Pricing Svc
    FARE_UNLOCKED --> CANCELLED: ReleaseSeatCmd<br/>to Reservation Svc
    COMPLETED --> [*]
    CANCELLED --> [*]
```

The happy path (STARTED to COMPLETED) and the compensation path (PAYMENT_FAILED to CANCELLED) share the same FARE_LOCKED branch point — ChargePaymentCmd is the one step with two outcomes, and the illustrative figures below assume it fails 2–5% of the time versus under 0.5% for the seat and fare steps.

**Key design decisions**:

1. **Semantic locking on seat**: The seat record has a `status` field: `AVAILABLE`, `PENDING` (during saga), `RESERVED`. Other bookings cannot reserve a `PENDING` seat. The `PENDING` status times out after 15 minutes if no resolution occurs, returning the seat to `AVAILABLE`.

2. **Outbox in Reservation Service**: The seat reservation update and the `SeatReservedEvent` in the outbox are committed in one transaction. Debezium streams the outbox event to Kafka. The orchestrator consumes `SeatReservedEvent` and advances state.

3. **Idempotent payment**: `ChargePaymentCmd` carries `idempotencyKey = sagaId + "-payment"`. The Payment Service stores this key with the charge record. Retries caused by orchestrator timeout return the original charge response.

4. **Notification is non-compensatable**: Sending a confirmation email cannot be undone. If payment succeeds but notification fails, the orchestrator retries the notification up to 5 times, then marks the saga COMPLETED_WITH_NOTIFICATION_FAILURE and queues a background retry job. It does NOT compensate the payment.

5. **Fare lock expiry**: Price locks expire in 10 minutes on the Pricing Service side. The orchestrator must complete the payment step within this window. If the payment step takes more than 8 minutes (timeout approaching), the orchestrator aborts and compensates rather than risking a race with fare expiry.

**Saga state table DDL**:

```sql
CREATE TABLE booking_sagas (
    saga_id         UUID        PRIMARY KEY,
    booking_ref     VARCHAR(20) NOT NULL,
    flight_id       UUID        NOT NULL,
    seat_id         UUID        NOT NULL,
    customer_id     UUID        NOT NULL,
    fare_amount     NUMERIC(10,2) NOT NULL,
    state           VARCHAR(50) NOT NULL,
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0  -- optimistic lock
);

CREATE INDEX idx_booking_sagas_state ON booking_sagas (state, updated_at)
    WHERE state NOT IN ('COMPLETED', 'CANCELLED');
```

**Failure rate and compensation statistics** — illustrative planning figures for this worked example, not measurements from a published system; use them to size retries and alert thresholds, not as citable industry benchmarks:
- Seat reservation failure: <0.1% (inventory conflicts)
- Fare lock failure: <0.5% (pricing service restarts)
- Payment failure: 2–5% (declined cards, processor timeouts)
- Notification failure: <1% (email gateway issues)
- Compensation success rate for payment failures: 99.8%
- Manual operator intervention required: <0.2% of all bookings

**Lesson learned** (illustrative, consistent with the worked example above rather than measured from a public system): the team starts with choreography. Within a few months the event dependency graph has grown to 14 event types across 4 services with 6 distinct compensation paths, and debugging one failed booking means correlating 8–12 events across 4 service logs. Moving to orchestration collapses that to a single state row per booking, and mean time to resolution drops by roughly an order of magnitude — the mechanism (one queryable saga state instead of an event archaeology exercise) is the transferable part, not the specific minutes.
