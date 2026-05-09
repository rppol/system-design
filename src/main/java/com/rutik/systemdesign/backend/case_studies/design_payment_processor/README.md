# Case Study: Payment Processor with Saga Orchestration

## Problem Statement

Design a payment processing system for an e-commerce platform that handles payment flows spanning multiple services: order management, payment gateway, inventory reservation, and notification. The system must guarantee:
- No double-charges — even if the client retries or a service crashes mid-flow
- No partial states — if payment fails, inventory must be released and order must be cancelled
- Complete audit trail of every state transition
- Ability to handle partial failures (payment succeeds, notification fails) without rolling back the entire saga
- Throughput of 1000 payment requests per second at p99 < 500ms

The core challenge: a payment flow touches at least 4 services and 4 databases. Traditional 2PC (two-phase commit) across these services is impractical — the coordinator is a single point of failure and all participants block during coordinator failure.

---

## Architecture Overview

```
Payment Flow — Saga Orchestration
===================================

Client
  |
  | POST /api/payments
  | Idempotency-Key: uuid-from-client
  v
[Payment API]
  |--- Check idempotency table (return cached result if duplicate)
  |--- Validate request
  |--- Save PaymentSaga with status=PENDING
  |--- Publish command to Kafka: InitiatePaymentCommand
  |
  v
[Payment Saga Orchestrator]
  |
  | State Machine:
  | PENDING → INVENTORY_RESERVING → INVENTORY_RESERVED
  |         → PAYMENT_CHARGING    → PAYMENT_CHARGED
  |         → ORDER_CONFIRMING    → COMPLETED
  |         → (any failure)       → COMPENSATING → COMPENSATED
  |
  +--- Step 1: ReserveInventoryCommand -----> [Inventory Service]
  |             InventoryReservedEvent <-----
  |
  +--- Step 2: ChargePaymentCommand --------> [Payment Gateway Service]
  |             PaymentChargedEvent <---------
  |
  +--- Step 3: ConfirmOrderCommand ---------> [Order Service]
  |             OrderConfirmedEvent <---------
  |
  +--- Step 4: SendNotificationCommand -----> [Notification Service]
               (best-effort — failure does NOT trigger compensation)


Compensation Flow (on PaymentCharged failure):
  [Orchestrator] --> ReleaseInventoryCommand --> [Inventory Service]
  [Orchestrator] --> CancelOrderCommand -------> [Order Service]
  [Orchestrator] --> status = COMPENSATED


Data Flow:
  Each command saved to outbox_events table in SAME transaction as saga state update
  Outbox relay publishes commands to Kafka
  Responses arrive as events on reply topics
```

---

## Key Design Decisions

**1. Saga Orchestration vs Choreography**

Orchestration chosen because: payment flow has complex compensation logic that is difficult to trace in choreography, a central orchestrator makes the flow state visible and auditable, and payment workflows have strict compliance requirements that benefit from explicit state machines.

**2. Idempotency Key Table**

Every payment request includes a client-generated idempotency key (UUID). Before processing, the API layer checks the `payment_idempotency` table. If the key exists, it returns the cached response (the exact same response as the original request). This prevents double-charges from client retries. The idempotency key has a 24-hour TTL.

**3. Outbox Pattern for Command Publishing**

Every saga state update and corresponding command publication happen atomically: the saga state is updated in the DB AND the command is written to the `outbox_events` table in the same `@Transactional` method. The outbox relay publishes commands to Kafka. This ensures no command is lost if Kafka is temporarily unavailable.

**4. Compensating Transactions**

Each saga step has a defined compensating transaction:
- Inventory reservation → Release inventory
- Payment charge → Issue refund
- Order confirmation → Cancel order
- Notification → No compensation (best-effort, non-critical)

Compensating transactions must be idempotent (safe to retry) and do not have to be perfect undos (e.g., a refund is a new debit transaction, not a deletion of the original charge).

**5. External Payment Gateway Idempotency**

Calls to the external payment gateway (Stripe-like API) include the `sagaId` as the idempotency key. If the saga retries the charge step after a timeout, the gateway returns the same result for the same idempotency key rather than charging twice.

---

## Implementation

### Schema

```sql
-- Saga state table
CREATE TABLE payment_sagas (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    amount          DECIMAL(12, 2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    status          VARCHAR(50) NOT NULL,          -- PENDING, INVENTORY_RESERVING, ...
    current_step    VARCHAR(100),
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Idempotency table
CREATE TABLE payment_idempotency (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    saga_id         UUID NOT NULL REFERENCES payment_sagas(id),
    response_status INTEGER NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);

CREATE INDEX idx_idempotency_expires ON payment_idempotency(expires_at);

-- Audit log (immutable, append-only)
CREATE TABLE payment_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    saga_id         UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    previous_status VARCHAR(50),
    new_status      VARCHAR(50),
    details         JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Outbox events
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(36) NOT NULL,
    event_type      VARCHAR(200) NOT NULL,
    payload         JSONB NOT NULL,
    published_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(id) WHERE published_at IS NULL;
```

### Payment API with Idempotency Check

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentSagaService sagaService;
    private final PaymentIdempotencyRepository idempotencyRepo;

    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        // Check if already processed
        Optional<PaymentIdempotency> existing = idempotencyRepo.findById(idempotencyKey);
        if (existing.isPresent()) {
            PaymentIdempotency cached = existing.get();
            return ResponseEntity.status(cached.getResponseStatus())
                .body(cached.getResponseBody());
        }

        // New request — start saga
        PaymentSaga saga = sagaService.initiateSaga(request);

        PaymentResponse response = PaymentResponse.accepted(saga.getId());

        // Store idempotency record
        idempotencyRepo.save(PaymentIdempotency.builder()
            .idempotencyKey(idempotencyKey)
            .sagaId(saga.getId())
            .responseStatus(202)
            .responseBody(response)
            .build());

        return ResponseEntity.accepted().body(response);
    }
}
```

### Saga Orchestrator

```java
@Service
@RequiredArgsConstructor
public class PaymentSagaOrchestrator {

    private final PaymentSagaRepository sagaRepository;
    private final OutboxEventRepository outboxRepository;
    private final PaymentAuditLogRepository auditLog;

    // Step transitions as explicit state machine
    private static final Map<SagaStatus, SagaStatus> NEXT_STATUS = Map.of(
        SagaStatus.PENDING,               SagaStatus.INVENTORY_RESERVING,
        SagaStatus.INVENTORY_RESERVING,   SagaStatus.INVENTORY_RESERVED,
        SagaStatus.INVENTORY_RESERVED,    SagaStatus.PAYMENT_CHARGING,
        SagaStatus.PAYMENT_CHARGING,      SagaStatus.PAYMENT_CHARGED,
        SagaStatus.PAYMENT_CHARGED,       SagaStatus.ORDER_CONFIRMING,
        SagaStatus.ORDER_CONFIRMING,      SagaStatus.COMPLETED
    );

    @Transactional
    public PaymentSaga initiateSaga(PaymentRequest request) {
        PaymentSaga saga = PaymentSaga.builder()
            .id(UUID.randomUUID())
            .orderId(request.getOrderId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .status(SagaStatus.PENDING)
            .build();

        sagaRepository.save(saga);
        appendAuditLog(saga, null, SagaStatus.PENDING, "Saga initiated");

        // Emit first command via outbox
        publishCommand(saga, new ReserveInventoryCommand(saga.getId(), request.getOrderId(), request.getItems()));
        saga.setStatus(SagaStatus.INVENTORY_RESERVING);
        sagaRepository.save(saga);

        return saga;
    }

    @KafkaListener(topics = "inventory-reply-topic")
    @Transactional
    public void onInventoryReply(InventoryReplyEvent event) {
        PaymentSaga saga = sagaRepository.findById(event.getSagaId())
            .orElseThrow(() -> new SagaNotFoundException(event.getSagaId()));

        if (event.isSuccess()) {
            SagaStatus previous = saga.getStatus();
            saga.setStatus(SagaStatus.INVENTORY_RESERVED);
            appendAuditLog(saga, previous, SagaStatus.INVENTORY_RESERVED, "Inventory reserved");

            // Proceed to next step
            publishCommand(saga, new ChargePaymentCommand(saga.getId(), saga.getAmount(), saga.getCurrency()));
            saga.setStatus(SagaStatus.PAYMENT_CHARGING);
            sagaRepository.save(saga);
        } else {
            startCompensation(saga, "Inventory reservation failed: " + event.getFailureReason());
        }
    }

    @KafkaListener(topics = "payment-gateway-reply-topic")
    @Transactional
    public void onPaymentGatewayReply(PaymentGatewayReplyEvent event) {
        PaymentSaga saga = sagaRepository.findById(event.getSagaId())
            .orElseThrow();

        if (event.isSuccess()) {
            saga.setStatus(SagaStatus.PAYMENT_CHARGED);
            appendAuditLog(saga, SagaStatus.PAYMENT_CHARGING, SagaStatus.PAYMENT_CHARGED,
                "Payment charged: " + event.getChargeId());

            publishCommand(saga, new ConfirmOrderCommand(saga.getId(), saga.getOrderId()));
            saga.setStatus(SagaStatus.ORDER_CONFIRMING);
            sagaRepository.save(saga);
        } else {
            // Payment failed — compensate inventory reservation
            startCompensation(saga, "Payment charge failed: " + event.getFailureReason());
        }
    }

    @KafkaListener(topics = "order-reply-topic")
    @Transactional
    public void onOrderReply(OrderReplyEvent event) {
        PaymentSaga saga = sagaRepository.findById(event.getSagaId()).orElseThrow();

        if (event.isSuccess()) {
            saga.setStatus(SagaStatus.COMPLETED);
            appendAuditLog(saga, SagaStatus.ORDER_CONFIRMING, SagaStatus.COMPLETED, "Order confirmed");

            // Notification is best-effort — failure does not trigger compensation
            publishCommand(saga, new SendNotificationCommand(saga.getId(), saga.getUserId(), saga.getOrderId()));
            sagaRepository.save(saga);
        } else {
            startCompensation(saga, "Order confirmation failed: " + event.getFailureReason());
        }
    }

    @Transactional
    public void startCompensation(PaymentSaga saga, String reason) {
        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setFailureReason(reason);
        appendAuditLog(saga, saga.getStatus(), SagaStatus.COMPENSATING, reason);

        // Compensate in reverse order of execution
        if (saga.wasInventoryReserved()) {
            publishCommand(saga, new ReleaseInventoryCommand(saga.getId(), saga.getOrderId()));
        }
        publishCommand(saga, new CancelOrderCommand(saga.getId(), saga.getOrderId()));
        sagaRepository.save(saga);
    }

    private void publishCommand(PaymentSaga saga, Object command) {
        // Save to outbox in SAME transaction as saga state update
        outboxRepository.save(OutboxEvent.builder()
            .aggregateId(saga.getId().toString())
            .eventType(command.getClass().getSimpleName())
            .payload(toJson(command))
            .build());
    }

    private void appendAuditLog(PaymentSaga saga, SagaStatus previous, SagaStatus next, String detail) {
        auditLog.save(PaymentAuditEntry.builder()
            .sagaId(saga.getId())
            .eventType("STATUS_TRANSITION")
            .previousStatus(previous)
            .newStatus(next)
            .details(Map.of("detail", detail, "timestamp", Instant.now()))
            .build());
    }
}
```

---

## Technologies Used

| Technology | Usage |
|------------|-------|
| Spring Boot 3.2 | REST API, Kafka consumers, transaction management |
| Spring Kafka | `@KafkaListener`, `KafkaTemplate` for command publishing |
| Spring Data JPA | Saga state, idempotency table, audit log persistence |
| PostgreSQL | Transactional storage for saga state and outbox |
| Apache Kafka | Command bus between orchestrator and services |
| Outbox pattern | Atomic command publication with state transitions |
| Resilience4j | Retry + circuit breaker around external payment gateway |
| Micrometer | Saga state transition metrics, p99 latency per step |

---

## Tradeoffs and Alternatives

**Orchestration vs Choreography**:
Orchestration provides a single place to see and manage the entire payment flow. The orchestrator's state machine is explicit and auditable. The downside: the orchestrator is a coupled component — if it is down, no new payment sagas can progress. Choreography would have each service react to events independently (more resilient to orchestrator failure) but tracing a payment flow requires correlating events across multiple services.

**2PC Alternative**:
Two-phase commit would provide stronger consistency (all-or-nothing) but the coordinator failure leaves all participants blocked. In a distributed system with external services (payment gateway), 2PC is impractical. The saga pattern achieves eventual consistency with explicit compensation.

**Synchronous vs Asynchronous**:
The payment flow could be fully synchronous (API waits for all steps to complete, returns final status). This is simpler but requires holding the HTTP connection open for potentially 5-10 seconds across multiple service calls. The asynchronous approach returns 202 Accepted immediately and delivers the final result via webhook or polling — better for reliability and user experience.

**Exactly-Once Charge Guarantee**:
The external payment gateway is called with `sagaId` as the idempotency key. If the saga retries the charge step after a timeout, the gateway returns the same charge ID rather than charging again. This, combined with the saga's own idempotency key on the API layer, provides end-to-end exactly-once charging semantics.

---

## Interview Discussion Points

- **How do you prevent double-charging if the client retries?** Idempotency key table at the API layer: first check, return cached response if key exists. The key expires after 24 hours.

- **What happens if the orchestrator crashes mid-saga?** The saga state is persisted in PostgreSQL. On restart, a `@Scheduled` job scans for sagas in non-terminal states that have not been updated in > 5 minutes and resubmits the current step's command. Kafka consumer idempotency in each service prevents double-processing.

- **How do you ensure the compensation transactions are executed even if the orchestrator crashes during compensation?** Same recovery mechanism: on restart, sagas in COMPENSATING state have their compensation commands re-submitted. Compensation commands are idempotent: releasing already-released inventory is a no-op, cancelling an already-cancelled order is a no-op.

- **What is the audit log used for?** Regulatory compliance (financial services require complete audit trail of every payment state transition), debugging (trace exactly what happened for a disputed charge), and analytics (measure saga step latency to identify bottlenecks).

- **How do you scale the orchestrator?** The orchestrator is stateless (all state in DB). Multiple instances can run simultaneously. Each Kafka consumer group has one active consumer per partition. Partition key = `sagaId` ensures one saga is always processed by the same consumer instance (partition affinity), preventing concurrent processing of the same saga.
