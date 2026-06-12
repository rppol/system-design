# System Design: Payment System

## Intuition

> **Design intuition**: A payment system is the financial nervous system of every e-commerce, ride-share, or marketplace platform — and it is the one place in a system design interview where "eventually consistent" and "best effort" are not acceptable answers for the *ledger*, even though they're perfectly fine for the *notifications*. The entire design is an exercise in drawing a hard line between the parts that must be exactly-once and strongly consistent (the ledger, the charge itself) and the parts that can be asynchronous and eventually consistent (webhooks, analytics, reconciliation). Get that line wrong in either direction and you either build something that can't scale or something that can double-charge a customer.

**Key insight**: Networks fail in the worst possible place — *after* the payment processor has charged the card but *before* the response reaches your server. From the caller's point of view this is indistinguishable from "the request never arrived." The caller's only safe move is to retry, and the system's only safe move is to make that retry a no-op. Every other requirement in this case study — the double-entry ledger, the outbox pattern, the saga, the reconciliation job — exists in service of that one guarantee: **a retried request must never produce a second charge.** This is `Idempotency-Key` (§4.1), and it is the single most-tested concept in payment system interviews.

---

## 1. Requirements Clarification

### Functional Requirements
- **Charge a customer**: Accept a payment via card, digital wallet (Apple Pay/Google Pay), or bank transfer (ACH/SEPA), authorize and capture funds.
- **Refunds**: Full or partial refund of a previously captured charge, linked back to the original transaction.
- **Payouts to merchants**: Periodically (daily/weekly) transfer accumulated merchant balances to their bank accounts, net of platform fees.
- **Double-entry ledger**: Every money movement (charge, refund, payout, fee) is recorded as a balanced set of debit/credit entries — the system of record for "where did the money go."
- **Async webhook notifications**: Notify merchants/downstream services when a payment's status changes (`succeeded`, `failed`, `refunded`) via signed HTTP callbacks.
- **Multi-currency support**: Accept and settle payments in multiple currencies (USD, EUR, GBP, INR, ...), with currency-aware rounding and FX conversion for cross-currency payouts.

### Non-Functional Requirements
- **Exactly-once semantics from the caller's perspective**: A client retry after a network timeout must never result in a duplicate charge — this is the defining NFR of the entire system (§4.1).
- **Strong consistency for ledger balances**: Account balances and ledger entries must be ACID-consistent within a region — no "eventually consistent" account balance.
- **PCI-DSS compliance**: The platform's own servers must **never** store, log, or transmit a raw card PAN (Primary Account Number). Card data is tokenized by a PCI-compliant Payment Service Provider (PSP — Stripe, Adyen, Braintree); the platform stores only opaque tokens.
- **99.99% availability**: ~52 minutes of downtime/year budget — payments are on the critical path of every transaction on the platform.
- **Full auditability**: The ledger is **append-only and immutable** — corrections are made via new offsetting entries, never by editing or deleting existing rows. Every entry must be traceable to the API request that caused it.
- **Idempotent client retries**: Every state-changing endpoint (`POST /charges`, `POST /refunds`, `POST /payouts`) accepts an `Idempotency-Key` header and is safe to retry.

### Out of Scope
- **Building a card network**: We integrate with existing card networks (Visa/Mastercard) via a PSP — we are not implementing ISO 8583 messaging or acquiring bank relationships.
- **Fraud/ML model internals**: We define the *integration point* (a synchronous risk-scoring call in the charge path with a latency budget) but do not design the fraud model itself.
- **Tax calculation**: Sales tax / VAT calculation is delegated to a tax-calculation service (e.g., Avalara, Stripe Tax) and is out of scope for this design.

---

## 2. Scale Estimation

### Transaction Volume
- **10M transactions/day**
- 10M / 86,400 sec = **~116 transactions/sec average**
- Peak (5x average, e.g., Black Friday/holiday traffic): **~580 transactions/sec**

### Ledger Volume
- Double-entry bookkeeping: every transaction produces **at least 2 ledger entries** (one debit, one credit) — often more (e.g., a charge also books a platform-fee entry and a payment-processing-fee entry, so 3-4 entries per transaction is realistic, but we'll use the conservative double-entry minimum for the headline number).
- 10M transactions/day x 2 entries = **20M ledger entries/day**
- 20M / 86,400 = **~232 ledger writes/sec average**, peak **~1,160/sec**

### Storage Calculation
- **Transaction record**: ~1 KB (payment_id, customer_id, merchant_id, amount, currency, status, PSP reference, idempotency key, timestamps, metadata JSON)
  - 10M/day x 1 KB = **~10 GB/day**
- **Ledger entry**: ~200 bytes (entry_id, account_id, transaction_id, amount_minor_units, currency, debit/credit flag, created_at)
  - 20M/day x 200 bytes = **~4 GB/day**
- **Combined raw**: ~14 GB/day
- **Annual**: 14 GB x 365 = **~5.1 TB/year** (before replication factor; with RF=3 for the ledger's primary store, ~15 TB/year)

### Idempotency-Key Cache
- 10M idempotency keys created/day
- Each key + cached response: ~100 bytes (key string + status + small JSON response)
- TTL: 24 hours (matches Stripe's documented retention — §6)
- 10M keys x 100 bytes = **~1 GB** resident in Redis at any time — trivially small, fits comfortably in a single Redis node with room to spare.

### Webhook Volume
- Each transaction generates 1-3 webhook events (e.g., `payment.created` -> `payment.succeeded`, plus `charge.refunded` for the ~2-5% of transactions that get refunded).
- ~15-20M webhook deliveries/day = **~175-230/sec average**, peak **~1,000/sec** — well within Kafka + a modest consumer pool's capacity.

---

## 3. High-Level Architecture

```
                                +-------------------+
                                |     Client         |
                                | (mobile/web app)   |
                                +---------+----------+
                                          |
                                          | POST /charges
                                          | Idempotency-Key: <uuid>
                                          v
                                +-------------------+
                                |   API Gateway      |
                                | (authn, TLS term,  |
                                |  rate limiting)    |
                                +---------+----------+
                                          |
                                          v
        +---------------------------------------------------------------+
        |                  Payment Orchestrator                          |
        |                                                                 |
        |  1. Idempotency-Key check (Redis + DB unique constraint)       |
        |  2. Validate request (amount > 0, currency supported, etc.)    |
        |                                                                 |
        |  +-----------------------------------------------------------+ |
        |  |        LOCAL DB TRANSACTION (single ACID commit)           | |
        |  |                                                             | |
        |  |   INSERT INTO payments (id, status='PENDING', ...)         | |
        |  |   INSERT INTO outbox   (event_type='PaymentInitiated',...) | |
        |  |   INSERT INTO idempotency_keys (key, status='IN_PROGRESS') | |
        |  |                                                             | |
        |  +-----------------------------------------------------------+ |
        +---------+-------------------------------------------------------+
                  |
                  | (sync, <100ms budget)
                  v
        +-------------------+
        |  Risk / Fraud      |   <-- sync scoring call; ALLOW / REVIEW / DENY
        |  Service           |       (model internals out of scope, §1)
        +---------+----------+
                  | ALLOW
                  v
        +-------------------+        +----------------------------+
        |  PSP Adapter       | -----> |  PSP (Stripe / Adyen)       |
        |  (tokenized card,  |        |  - tokenizes card (PCI      |
        |   no raw PAN ever  |        |    scope stays with PSP)    |
        |   touches us)      | <----- |  - returns auth result      |
        +---------+----------+  sync  +-------------+----------------+
                  |                                  |
                  | update payments.status           | async webhook
                  | = AUTHORIZED / FAILED            | (final settlement,
                  v                                  |  chargebacks, etc.)
        +-------------------+                        |
        |  Ledger Service    | <----------------------+
        |  (append-only      |  on settlement webhook:
        |   double-entry     |  POST /webhooks/psp
        |   write, ACID)     |  - verify HMAC signature
        +---------+----------+  - dedupe on PSP event_id
                  |                - update payments.status = SUCCEEDED
                  | (same local txn as ledger write)
                  v
        +-------------------+
        |  outbox table      |
        |  (event:           |
        |   PaymentSucceeded)|
        +---------+----------+
                  |
                  | polled by / CDC-tailed by
                  v
        +-------------------+
        |  Outbox Relay      |
        |  (Debezium or      |
        |   polling process) |
        +---------+----------+
                  |
                  v
        +-------------------+
        |   Kafka            |
        |  topic: payments.* |
        +----+------+----+---+
             |      |    |
             v      v    v
       +--------+ +--------+ +------------------+
       | Order  | |Notif.  | | Reconciliation   |
       | Service| |Service | | Service          |
       |        | |(sends  | | (nightly batch:  |
       | marks  | | merchant| | ledger totals    |
       | order  | | webhook)| | vs PSP settlement|
       | paid   | |        | | reports)         |
       +--------+ +--------+ +------------------+
```

The single most important box in this diagram is the **LOCAL DB TRANSACTION** in the Payment Orchestrator: the payment row, the outbox row, and the idempotency-key claim are written **atomically in one transaction**. This is the Outbox pattern from [`../distributed_transactions/README.md`](../distributed_transactions/README.md) (§4.5/§6.3) applied to its canonical use case — without it, "save the payment" and "publish the event that downstream services depend on" would be a dual write, and a crash between them would leave an order that thinks it's unpaid forever, or a payment that nothing downstream ever hears about.

---

## 4. Component Deep Dives

### 4.1 Idempotency-Key Handling

**The contract**: the client generates a UUID once per *logical* payment attempt (not per HTTP request) and sends it as the `Idempotency-Key` header on every retry of that attempt. The server's job is: "have I seen this key before? If yes, return the exact same response without doing any work. If no, claim the key, do the work, cache the response."

This is the application-layer realization of the "at-most-once effect on top of at-least-once delivery" guarantee described in [`../distributed_transactions/README.md`](../distributed_transactions/README.md) §4.6. The schema is a dedup table with a **UNIQUE constraint** on the key — the database itself is the arbiter of "who got here first" under concurrent retries.

```sql
CREATE TABLE idempotency_keys (
    idempotency_key   VARCHAR(64)  PRIMARY KEY,   -- UNIQUE by definition (PK)
    request_hash      VARCHAR(64)  NOT NULL,      -- hash of request body, to
                                                    -- detect key reuse with a
                                                    -- DIFFERENT payload (= error)
    status            VARCHAR(20)  NOT NULL,      -- IN_PROGRESS | COMPLETED
    response_status   INT,
    response_body     JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ
);
-- TTL enforcement: a background job deletes rows older than 24h (Stripe's
-- documented retention window, §6). Redis mirrors this table for fast reads.
```

**The unique-constraint-violation-as-cache-hit pattern** — this is the core trick:

```java
@Service
public class IdempotencyService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    /**
     * Atomically claims an idempotency key. Returns:
     *  - CLAIMED: this caller owns the key, proceed with the charge
     *  - REPLAY: a previous COMPLETED response is returned verbatim
     *  - IN_PROGRESS: another request with this key is currently executing
     *  - KEY_REUSE_CONFLICT: same key, different request body — client bug
     */
    public IdempotencyOutcome claim(String key, String requestBodyJson) {
        String requestHash = sha256(requestBodyJson);

        try {
            // Atomic claim: succeeds only if the key does not exist yet.
            // The UNIQUE constraint on idempotency_key is what makes this
            // safe under concurrent requests with the SAME new key.
            jdbc.update("""
                INSERT INTO idempotency_keys (idempotency_key, request_hash, status)
                VALUES (?, ?, 'IN_PROGRESS')
                """, key, requestHash);
            return IdempotencyOutcome.claimed();

        } catch (DuplicateKeyException e) {
            // Someone (possibly this same client, retrying) already has
            // this key. Look up what state it's in.
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT request_hash, status, response_status, response_body
                FROM idempotency_keys WHERE idempotency_key = ?
                """, key);

            String existingHash = (String) row.get("request_hash");
            if (!existingHash.equals(requestHash)) {
                // Same key, DIFFERENT payload -> this is a client-side bug
                // (key reuse across logically different requests), not a
                // safe retry. Reject loudly rather than silently charging
                // the wrong amount.
                throw new IdempotencyKeyReusedException(key);
            }

            String status = (String) row.get("status");
            if ("COMPLETED".equals(status)) {
                // THE REPLAY PATH: return the cached response, do NOT
                // re-execute the charge. This is the entire point.
                return IdempotencyOutcome.replay(
                    (Integer) row.get("response_status"),
                    parseJson((String) row.get("response_body")));
            }

            // status == IN_PROGRESS: a concurrent request is mid-flight.
            // Caller should return 409 Conflict ("retry shortly") rather
            // than block indefinitely.
            return IdempotencyOutcome.inProgress();
        }
    }

    /** Called once the charge completes (success OR failure) to cache the result. */
    public void complete(String key, int httpStatus, Object responseBody) {
        jdbc.update("""
            UPDATE idempotency_keys
            SET status = 'COMPLETED', response_status = ?,
                response_body = ?::jsonb, completed_at = now()
            WHERE idempotency_key = ?
            """, httpStatus, toJson(responseBody), key);
    }
}
```

```java
@RestController
@RequestMapping("/charges")
public class ChargeController {

    @PostMapping
    public ResponseEntity<ChargeResponse> charge(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ChargeRequest req) {

        var outcome = idempotencyService.claim(idempotencyKey, toJson(req));

        switch (outcome.type()) {
            case REPLAY:
                // Second (or third, or tenth) attempt after a network
                // timeout on the FIRST attempt's response. The customer
                // is NOT charged again — they get the original result.
                return ResponseEntity.status(outcome.cachedStatus())
                                      .body(outcome.cachedBody());

            case IN_PROGRESS:
                return ResponseEntity.status(409)
                                      .body(ChargeResponse.retryShortly());

            case CLAIMED:
                ChargeResponse result = paymentOrchestrator.processCharge(req, idempotencyKey);
                idempotencyService.complete(idempotencyKey, 200, result);
                return ResponseEntity.ok(result);

            default:
                throw new IllegalStateException("unreachable");
        }
    }
}
```

### 4.2 Double-Entry Ledger

**Why double-entry, not a balance column**: a `balance` column on an `accounts` table is a *cache* of the sum of all transactions affecting that account — and like any cache, it can drift from the truth (a missed update, a race condition, a bug in a migration). A double-entry ledger makes the **ledger itself the source of truth**; the balance is always *derivable* by summing entries, and — critically — every transaction is recorded as a debit to one account and a matching credit to another, so **the sum of all entries across the whole ledger is always exactly zero**. If it's ever not zero, that's not a "metric to investigate later" — it's a CRITICAL invariant violation (§8).

```sql
CREATE TABLE accounts (
    account_id    BIGINT PRIMARY KEY,
    account_type  VARCHAR(30) NOT NULL,   -- CUSTOMER, MERCHANT, PLATFORM_FEES,
                                           -- PSP_CLEARING, PAYOUT_PENDING
    currency      CHAR(3)     NOT NULL,   -- ISO 4217: USD, EUR, GBP, ...
    balance_minor BIGINT      NOT NULL DEFAULT 0,  -- INTEGER MINOR UNITS (cents)
                                                     -- NEVER a float/double
    version       BIGINT      NOT NULL DEFAULT 0,  -- optimistic-lock counter
    CONSTRAINT non_negative_unless_platform
        CHECK (balance_minor >= 0 OR account_type = 'PSP_CLEARING')
);

CREATE TABLE ledger_entries (
    entry_id        BIGSERIAL PRIMARY KEY,
    transaction_id  UUID        NOT NULL,   -- groups the debit+credit pair
    account_id      BIGINT      NOT NULL REFERENCES accounts(account_id),
    direction       CHAR(1)     NOT NULL CHECK (direction IN ('D','C')),
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        CHAR(3)     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    description     TEXT
);
-- INVARIANT (checked by the reconciliation job, §4.5, and ideally by a
-- DB trigger): for every transaction_id, SUM(CASE direction WHEN 'D' THEN
-- amount_minor ELSE -amount_minor END) = 0
CREATE INDEX idx_ledger_txn ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at);
```

Note `balance_minor BIGINT` and `amount_minor BIGINT` — **all money is stored as integer minor units (cents)**, never `float`/`double`. A `double` cannot represent `$0.10` exactly (it's `0.1000000000000000055511151231257827021181583404541015625` in IEEE 754), and summing millions of such values produces a ledger that doesn't balance to zero — see War Story 4 (§9). Java code uses `BigDecimal` only at the API boundary (for display/serialization) and `long` minor units internally for ledger arithmetic.

**Atomic transfer with row-level locking** — this is the code that moves money from one account to another (e.g., customer -> merchant on a successful charge):

```java
@Service
public class LedgerService {

    private final JdbcTemplate jdbc;

    /**
     * Atomically transfers `amountMinor` from `fromAccount` to `toAccount`
     * and writes the corresponding double-entry ledger rows, all within a
     * single SQL transaction. Uses UPDATE ... WHERE balance >= ? so the
     * row-level lock and the balance check happen as one atomic operation
     * (no separate SELECT-then-UPDATE — see War Story 2, §9, for why that's
     * broken).
     */
    @Transactional
    public void transfer(UUID transactionId, long fromAccount, long toAccount,
                          long amountMinor, String currency, String description) {

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        // Debit the source account. Row lock acquired implicitly by the
        // UPDATE; the WHERE clause makes "check balance" and "acquire lock
        // and decrement" a SINGLE atomic statement.
        int debited = jdbc.update("""
            UPDATE accounts
            SET balance_minor = balance_minor - ?, version = version + 1
            WHERE account_id = ? AND currency = ? AND balance_minor >= ?
            """, amountMinor, fromAccount, currency, amountMinor);

        if (debited == 0) {
            // Either insufficient funds, or the account/currency doesn't
            // exist. Either way, ROLL BACK — no partial transfer.
            throw new InsufficientFundsException(fromAccount, amountMinor, currency);
        }

        // Credit the destination account. PLATFORM_FEES / PSP_CLEARING
        // accounts are allowed to go negative transiently (they represent
        // "money owed to us by the PSP"), per the CHECK constraint above.
        jdbc.update("""
            UPDATE accounts
            SET balance_minor = balance_minor + ?, version = version + 1
            WHERE account_id = ? AND currency = ?
            """, amountMinor, toAccount, currency);

        // Write the matching debit/credit ledger entries. Both rows share
        // transaction_id and SUM TO ZERO -- this pair is the audit record.
        jdbc.update("""
            INSERT INTO ledger_entries
              (transaction_id, account_id, direction, amount_minor, currency, description)
            VALUES (?, ?, 'D', ?, ?, ?)
            """, transactionId, fromAccount, amountMinor, currency, description);

        jdbc.update("""
            INSERT INTO ledger_entries
              (transaction_id, account_id, direction, amount_minor, currency, description)
            VALUES (?, ?, 'C', ?, ?, ?)
            """, transactionId, toAccount, amountMinor, currency, description);
    }
}
```

A note on lock ordering: when a transfer involves *two* row-level updates (debit + credit), always acquire locks in a **consistent global order** (e.g., always lock the lower `account_id` first) to avoid deadlocks between two concurrent transfers that touch the same pair of accounts in opposite directions. At 580 TPS peak with a small number of hot accounts (e.g., the platform's own fee-collection account is touched by *every* transaction), this is not a theoretical concern — it is the most common source of deadlock-retry storms in a ledger service.

### 4.3 Saga for the Multi-Step Charge Flow

The end-to-end "charge a customer" flow — **reserve funds -> call PSP -> confirm order** — is exactly the Saga pattern described in [`../distributed_transactions/README.md`](../distributed_transactions/README.md) §4.3/§4.4, applied here as:

- **Step 1 (Reserve)**: Payment Orchestrator writes `payments.status = PENDING` and an `outbox` row in one local transaction (§4.4 below). This is the "Try" in TCC terms — no money has moved yet, but the *intent* is durably recorded.
- **Step 2 (Call PSP)**: The PSP adapter calls Stripe/Adyen with the tokenized payment method and the **same idempotency key**, threaded through as the PSP's own idempotency header (PSPs like Stripe support this natively — §6). This step is the one that can fail for reasons entirely outside our control (card declined, PSP timeout, 3-D Secure challenge) and is the "external network call" that [`../distributed_transactions/README.md`](../distributed_transactions/README.md)'s case study (§14) calls out as needing to live *outside* any database transaction.
- **Step 3 (Confirm)**: On a `succeeded` PSP response (sync) or settlement webhook (async), the Ledger Service runs the atomic `transfer()` from §4.2, moving funds from `CUSTOMER_RECEIVABLE` to `MERCHANT_PAYABLE` (minus platform fee, which goes to `PLATFORM_FEES`). This is the "Confirm" in TCC terms.
- **Compensation (if Step 2 fails)**: `payments.status = FAILED`, an outbox event `PaymentFailed` is published, and the Order Service's saga step reacts by cancelling the order — no ledger entries were ever written for a failed charge, so there's nothing to "undo" in the ledger itself. This is the cheap case: **failure before Step 3 needs no ledger compensation at all**, because the ledger is the *last* step, not the first.
- **Compensation (refund, after Step 3 succeeded)**: A refund is a **new transaction**, not an undo of the original — `transfer()` runs again with accounts reversed (`MERCHANT_PAYABLE` -> `CUSTOMER_RECEIVABLE`), producing a *new* pair of ledger entries linked to the original `transaction_id` via a `refund_of` foreign key. The original entries are never modified, preserving the immutable-append-only guarantee (§1 NFR).

This sequencing — **ledger writes happen only after the PSP has confirmed success** — is the single biggest design decision in this case study, and it's why the architecture diagram (§3) places the Ledger Service *after* the PSP Adapter, not before.

### 4.4 Outbox Pattern Implementation

Every state transition that downstream services need to know about (`PaymentInitiated`, `PaymentSucceeded`, `PaymentFailed`, `RefundIssued`) is written to the `outbox` table in the **same local transaction** as the state change itself — collapsing the dual-write problem exactly as described in [`../distributed_transactions/README.md`](../distributed_transactions/README.md) §4.5/§6.3.

```sql
CREATE TABLE outbox (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,   -- 'Payment'
    aggregate_id   UUID         NOT NULL,   -- payment_id
    event_type     VARCHAR(100) NOT NULL,   -- 'PaymentSucceeded'
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  DEFAULT now(),
    published_at   TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;
```

```java
@Service
public class PaymentOrchestrator {

    private final JdbcTemplate jdbc;

    @Transactional
    public Payment initiateCharge(ChargeRequest req, String idempotencyKey) {
        UUID paymentId = UUID.randomUUID();

        // Write the payment row AND the outbox row in ONE transaction.
        jdbc.update("""
            INSERT INTO payments
              (id, customer_id, merchant_id, amount_minor, currency,
               status, idempotency_key, created_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', ?, now())
            """, paymentId, req.customerId(), req.merchantId(),
            req.amountMinor(), req.currency(), idempotencyKey);

        jdbc.update("""
            INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload)
            VALUES ('Payment', ?, 'PaymentInitiated', ?::jsonb)
            """, paymentId, toJson(Map.of(
                "payment_id", paymentId,
                "amount_minor", req.amountMinor(),
                "currency", req.currency(),
                "merchant_id", req.merchantId())));

        return new Payment(paymentId, "PENDING");
        // COMMIT happens here. If the process crashes one line earlier,
        // NEITHER row exists -- no inconsistency. If it crashes one line
        // later, BOTH exist -- the outbox relay (below) WILL eventually
        // publish the event. There is no state where the payment exists
        // but the event is silently lost.
    }
}
```

```java
/**
 * Outbox relay: polls for unpublished rows and publishes to Kafka. In
 * production this is typically replaced by Debezium tailing the Postgres
 * WAL (sub-second latency, zero polling load) -- the table schema and the
 * "mark published after send" semantics are identical either way.
 */
@Component
public class OutboxRelay {

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelay = 100)  // ~10 polls/sec when not using CDC
    public void relay() {
        List<OutboxRow> rows = jdbc.query("""
            SELECT id, event_type, payload FROM outbox
            WHERE published_at IS NULL
            ORDER BY id LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, this::mapRow);

        for (OutboxRow row : rows) {
            kafka.send("payments." + row.eventType(), row.aggregateId(), row.payload())
                 .join();  // wait for broker ack before marking published
            jdbc.update("UPDATE outbox SET published_at = now() WHERE id = ?", row.id());
        }
        // If the process crashes between kafka.send() and the UPDATE, the
        // row is republished on restart -> consumers see the event TWICE.
        // This is why downstream consumers (Order Service, Notification
        // Service) MUST be idempotent on (aggregate_id, event_type) --
        // at-least-once delivery + idempotent consumer = effectively-once.
    }
}
```

### 4.5 Reconciliation Job

Even with idempotency keys, an outbox, and a double-entry ledger, the system's internal view of "what happened" and the PSP's view can drift — a webhook can be lost in transit, a settlement can post a day later than expected, or a bug can cause a ledger entry to be written with the wrong amount. The **reconciliation job** is the safety net that catches this drift before it becomes a customer-facing or regulatory problem.

```
Nightly batch (runs at 02:00 UTC, after PSP settlement files are available):

1. Download the PSP's settlement report for the previous day
   (Stripe: "Balance Transactions" API export; Adyen: settlement batch CSV).

2. For each settled transaction in the PSP report:
     - Look up the corresponding `payments` row by psp_reference_id.
     - Compare amount_minor, currency, and status.
     - If MATCH: mark as reconciled (reconciliation_status = 'MATCHED').
     - If MISMATCH (amount differs, or status differs):
         -> flag as DISCREPANCY, write to discrepancies table,
            DO NOT auto-correct the ledger.

3. For each `payments` row with status = SUCCEEDED but NOT present in
   the PSP settlement report:
     -> flag as ORPHAN (we think we got paid, PSP has no record)
        -> CRITICAL: this could mean a forged/replayed webhook (§4.6)

4. For each PSP settlement entry with NO corresponding `payments` row:
     -> flag as UNRECORDED (PSP charged, we have no record)
        -> CRITICAL: customer was charged but our system doesn't know it

5. Independently, sum all ledger_entries grouped by transaction_id and
   assert SUM(debits) - SUM(credits) = 0 for every transaction AND for
   the ledger as a whole. A non-zero global sum is a P0 (§8).

6. Publish a daily reconciliation report: total transactions, total
   matched, total flagged, total discrepancy amount. Alert finance team
   if discrepancy count > 0 or discrepancy amount > $1.
```

The job is intentionally conservative: it **flags, never auto-corrects**. An automated "fix" to a financial ledger is itself a risk — every correction goes through the same `transfer()` code path (§4.2) as a normal transaction, with its own audit trail, after a human (finance/ops) reviews the discrepancy.

### 4.6 Webhook Handling from PSP

PSPs deliver final settlement status, chargebacks, and disputes asynchronously via signed HTTP webhooks. Two failure modes must be handled: **forged webhooks** (an attacker POSTs a fake "payment succeeded" to your webhook endpoint) and **replayed webhooks** (the PSP retries a webhook because it didn't get a 200 response in time, even though your service *did* process it).

```java
@RestController
@RequestMapping("/webhooks/psp")
public class PspWebhookController {

    private static final String WEBHOOK_SECRET = vault.get("psp.webhook.secret");

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader("PSP-Signature") String signatureHeader,
            @RequestBody String rawBody) {

        // 1. Verify HMAC signature BEFORE parsing or trusting anything in
        //    the body. This defeats forged webhooks -- only the PSP knows
        //    the shared secret used to compute the HMAC.
        String expected = hmacSha256(WEBHOOK_SECRET, rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(UTF_8), signatureHeader.getBytes(UTF_8))) {
            return ResponseEntity.status(401).build();
        }

        WebhookEvent event = parseJson(rawBody, WebhookEvent.class);

        // 2. Dedupe on the PSP's event_id -- handles the replay case.
        //    UNIQUE constraint on processed_events.event_id is the same
        //    "claim before processing" pattern as §4.1.
        int claimed = jdbc.update("""
            INSERT INTO processed_webhook_events (event_id, event_type, received_at)
            VALUES (?, ?, now())
            ON CONFLICT (event_id) DO NOTHING
            """, event.id(), event.type());

        if (claimed == 0) {
            // Already processed this exact event_id -- PSP retried because
            // our previous 200 response was lost in transit. Acknowledge
            // again WITHOUT reprocessing (no double ledger entry).
            return ResponseEntity.ok().build();
        }

        switch (event.type()) {
            case "charge.succeeded" -> ledgerService.confirmCharge(event.paymentId(), event.amountMinor());
            case "charge.failed"    -> paymentService.markFailed(event.paymentId(), event.failureReason());
            case "charge.refunded"  -> ledgerService.processRefund(event.paymentId(), event.amountMinor());
            default -> log.info("Unhandled webhook event type: {}", event.type());
        }

        return ResponseEntity.ok().build();
    }
}
```

---

## 5. Design Decisions & Tradeoffs

### Synchronous PSP Call for Auth vs. Fully Async
- **Choice**: The initial authorization call to the PSP is **synchronous** — the client's `POST /charges` blocks (with a tight timeout, e.g., 5-10s) until the PSP returns an auth result. Final settlement, chargebacks, and disputes arrive **asynchronously** via webhook.
- **Reason**: The user is sitting at a checkout screen and needs to know *immediately* whether their card was accepted — "we'll let you know later" is not an acceptable UX for "did my payment go through?" But final settlement (the money actually moving between banks) genuinely takes hours to days, and no user is staring at a screen waiting for it.
- **Trade-off**: The sync call ties up a request thread/connection for the PSP's round-trip latency (typically 200-800ms, more with 3-D Secure challenges) — this is why the Risk/Fraud check (§3) has a strict <100ms budget, to keep the *total* synchronous critical path under ~1 second.

### Double-Entry Immutable Ledger vs. Mutable Balance Column
- **Choice**: Double-entry ledger as the source of truth; account balances are derived (and cached for read performance, but the cache is provably reconstructable from the ledger).
- **Reason**: Auditability and correctness under concurrent writes. A mutable `balance` column updated via `balance = balance + delta` has no record of *why* it changed if the application code that wrote the delta has a bug — the ledger entries are the only artifact that survives a bug in the balance-update logic.
- **Trade-off**: Every money movement is **2x writes minimum** (one debit row, one credit row) instead of one `UPDATE`. At 232 ledger writes/sec average (§2), this is a non-issue for Postgres; the cost is justified entirely by the auditability win.

### Saga + Outbox vs. Two-Phase Commit (2PC) Across Services
- **Choice**: Saga (§4.3) + Outbox (§4.4), not 2PC.
- **Reason**: 2PC requires every participant to be able to "prepare" (hold a lock) and wait for a coordinator's decision — but **the PSP is a third-party system we don't control and cannot ask to hold a lock indefinitely**. You cannot 2PC Stripe. Even setting that aside, 2PC's blocking behavior under coordinator failure (locks held until recovery — see [`../distributed_transactions/README.md`](../distributed_transactions/README.md) §10 War Story 1, where a 2PC coordinator crash held connection-pool locks for 45 minutes and took down *all* checkout traffic) is operationally unacceptable for a system with a 99.99% availability target.
- **Trade-off**: Saga accepts a brief window where `payments.status = PENDING` and no ledger entry exists yet — this window is bounded (PSP timeout, typically <10s) and is exactly what the reconciliation job (§4.5) and a "stuck PENDING payments" alert (§8) exist to catch.

### Strong Consistency for the Ledger of Record vs. Eventually-Consistent Read Replicas
- **Choice**: The `accounts` and `ledger_entries` tables live on a **single-region, synchronously-replicated primary** (e.g., Postgres with synchronous standby) — every `transfer()` (§4.2) commits to this primary. Read replicas (asynchronous, eventually consistent) serve reporting dashboards, merchant balance displays in non-critical UI, and analytics queries.
- **Reason**: A merchant's "available balance" *must* reflect every committed transfer the instant it commits — an eventually-consistent ledger could let a merchant initiate a payout for funds that a concurrent refund is about to claw back, producing a negative balance. Reporting/analytics, by contrast, can tolerate a few seconds of replica lag with zero business impact.
- **Trade-off**: The synchronous-replica write path adds latency to every `transfer()` (an extra network round-trip to the standby before commit acknowledges) — typically 1-5ms in-region, which is negligible against the PSP's 200-800ms round-trip but would NOT be negligible if the standby were cross-region (hence "single-region" for the ledger of record).

---

## 6. Real-World Implementations

- **Stripe's `Idempotency-Key`**: Stripe's Payments API documents this exact pattern from §4.1 — every `POST` request accepts an `Idempotency-Key` header, Stripe stores the key alongside the resulting response for **24 hours**, and a repeated key within that window returns the *original* response (including the original HTTP status code) without re-executing the operation. Stripe explicitly recommends generating the key with a V4 UUID *once per logical operation* (not per HTTP attempt) — precisely the client-side contract described in §4.1. This is the most copy-pasted idempotency design in the industry and the reference point interviewers expect.

- **Square's ledger-centric architecture**: Square's internal payments platform is built around an append-only ledger as the system of record for every seller's balance, processing fee, and payout — described in Square engineering's public talks as treating "the ledger" the way a bank treats its general ledger: nothing is ever deleted or overwritten, corrections are new entries, and a seller's "balance" displayed in the Square Dashboard is a *materialized view* computed from the ledger, not a primary-key lookup on a mutable balance row. This mirrors §4.2's "balance is derived, ledger is truth" design exactly.

- **Uber's multi-currency driver/rider payment platform**: Uber operates in 70+ countries with dozens of currencies and local payment methods (cash, cards, digital wallets, and region-specific rails like Paytm in India or Alipay in China). Uber's payments platform abstracts "charge the rider" and "pay the driver" behind a currency-aware ledger so that a trip priced in INR, paid by a rider's card in INR, and paid out to a driver's bank account in INR all settle through the same internal accounting primitives — while Uber's own corporate treasury operations (moving money between countries, FX conversion) happen as *separate*, clearly-labeled ledger transactions, never silently folded into a trip's transaction record. This separation — "the customer-facing transaction" vs. "the treasury/FX operations behind it" — is the multi-currency analog of §4.2's debit/credit separation.

- **PayPal's reconciliation pipeline**: PayPal, operating one of the highest-volume payment platforms globally, runs large-scale automated reconciliation between its internal ledger and the settlement files from the card networks and banking partners it interfaces with — flagging mismatches for its operations teams rather than auto-correcting, the same conservative "flag, don't auto-fix" philosophy as §4.5. At PayPal's volume (hundreds of millions of transactions/day, multiple orders of magnitude above this case study's 10M/day), even a 0.001% discrepancy rate represents thousands of transactions/day requiring review — which is why reconciliation tooling at this scale is itself a substantial internal platform, not a side script.

---

## 7. Technologies & Tools

| Component | Technology | Why |
|---|---|---|
| Ledger of record (`accounts`, `ledger_entries`) | PostgreSQL (synchronous standby) | ACID transactions, row-level locking (`SELECT ... FOR UPDATE` / `UPDATE ... WHERE balance >= ?`), strong consistency for the system of record |
| Outbox relay | Kafka + Debezium (CDC) | Sub-second, near-zero-overhead propagation of `outbox` rows to downstream consumers; avoids a polling loop's latency/load tradeoff |
| Idempotency-key cache | Redis (`SET key val NX EX 86400`) | Sub-millisecond key claim/check on the hot path; TTL matches the 24h retention window (§6) |
| PSP integration | Stripe / Adyen SDKs | PCI-compliant tokenization (§4 — raw PAN never touches our servers), built-in idempotency-key support on their own APIs |
| Secrets / webhook signing keys | HashiCorp Vault / AWS KMS | Webhook HMAC secrets and PSP API keys never live in application config or source control |
| Card-data tokenization boundary | PSP-hosted (Stripe Elements / Adyen Drop-in) | Card form fields are rendered in an iframe controlled by the PSP — the platform's own frontend/backend never receives the PAN, minimizing PCI-DSS scope (§11) |
| Key management for at-rest encryption | HSM (Hardware Security Module) | Encryption keys for any stored sensitive data (tokens, bank account numbers for payouts) are generated and used inside an HSM, never exported in plaintext |
| Event bus | Kafka | Decouples the Ledger Service's commit from Order/Notification/Reconciliation consumers; absorbs traffic bursts |
| Reporting / analytics | Read replicas + a columnar warehouse (e.g., Snowflake/BigQuery via CDC) | Eventually-consistent reporting workload kept off the ledger's primary (§5) |

---

## 8. Operational Playbook

### Key Metrics to Monitor

| Metric | What It Tells You | Alert Threshold |
|---|---|---|
| Payment success rate | `succeeded / (succeeded + failed)` over a rolling 5-min window | Drop > 5 percentage points from 7-day baseline -> page |
| PSP p99 latency | Time from "send auth request" to "receive response" | > 2s sustained for 5 min -> warn; > 5s -> page (approaching client timeout) |
| Reconciliation discrepancy count | Output of the nightly job (§4.5) | > 0 -> alert finance team same-day; > 10 or > $100 total -> page on-call |
| Ledger global balance check | `SUM(debits) - SUM(credits)` across all `ledger_entries` | **Any non-zero value -> CRITICAL, page immediately** (§ below) |
| Outbox lag (unpublished rows, oldest age) | Health of the outbox relay (§4.4) | Oldest unpublished row > 60s old -> warn; > 5 min -> page |
| Idempotency-key cache hit rate on retries | Are retries actually being deduped? | Sustained drop -> investigate Redis health (fail-open risk, §9) |
| Webhook signature verification failure rate | Possible forged-webhook attack, or a rotated secret not yet propagated | > 1% of webhook requests -> page |

### Runbook: Ledger Doesn't Balance to Zero (CRITICAL)

A non-zero `SUM(debits) - SUM(credits)` across the ledger is **not a "data quality" issue** — it means money is being created or destroyed in the system of record, which is either a severe bug or active fraud.

1. **Page immediately** — this is a P0/SEV1, treated with the same urgency as a full outage.
2. Freeze new `transfer()` calls for the affected currency (feature flag) to stop the bleeding while investigating — existing `payments.status = PENDING` transactions queue rather than fail outright.
3. Identify the offending `transaction_id`(s) via `GROUP BY transaction_id HAVING SUM(...) != 0` — this query should never return rows under normal operation, so any row found is the smoking gun.
4. Determine root cause: a bug in `transfer()` that wrote only one side of a pair (e.g., a crash between the two `INSERT`s in §4.2 — note that `@Transactional` should make this impossible, so finding it means the transaction boundary itself is broken), or a manual/migration script that bypassed `transfer()` entirely.
5. Correct via a **new offsetting transaction** (never edit/delete the broken rows) once the root cause is understood and the fix is deployed.
6. Post-incident: add a database-level trigger or constraint that makes the broken state structurally impossible (e.g., a deferred constraint trigger that checks the per-transaction sum at commit time).

### Runbook: PSP Outage

1. PSP health-check endpoint or a sustained spike in PSP p99 latency / 5xx responses triggers the alert.
2. **Failover to secondary PSP** (if configured — many platforms integrate two PSPs, e.g., Stripe primary + Adyen secondary, specifically for this scenario): route new charge attempts to the secondary, with the same `Idempotency-Key` semantics applied independently per-PSP.
3. If no secondary PSP is configured: **queue-and-retry** — `payments.status = PENDING`, charge requests are queued (not failed outright) and retried with exponential backoff once the PSP recovers. Communicate degraded-but-not-failed status to clients (e.g., "Payment processing — we'll confirm shortly" rather than an immediate error).
4. Once the PSP recovers, the queued retries drain — each retry uses the *original* idempotency key, so if the PSP actually *did* process an earlier attempt during the "outage" (a partial outage where requests succeeded but responses were lost), the retry safely returns the cached result instead of double-charging.

### Runbook: Reconciliation Mismatches

1. Nightly job (§4.5) flags discrepancies -> alert finance team with the discrepancy report (transaction IDs, amounts, type of mismatch).
2. For `ORPHAN` (we think we got paid, PSP disagrees) or `UNRECORDED` (PSP charged, we have no record) flags specifically: **freeze the affected merchant/customer accounts** (no payouts, no further charges against the same payment method) pending manual review — these patterns can indicate a forged webhook (§4.6) slipping past signature verification, or a PSP-side data issue.
3. Finance reviews each flagged transaction against PSP dashboards directly; corrections are applied via new offsetting `transfer()` calls (§4.2), never by editing existing ledger rows.
4. Track discrepancy rate as a trend metric — a sudden spike (even if each individual discrepancy is small) often precedes discovery of a systemic bug (e.g., a currency-rounding issue, §9 War Story 4) before it's large enough to be caught any other way.

---

## 9. Common Pitfalls & War Stories

### War Story 1 (PRIMARY): The Double Charge from a Client Retry

**This is the canonical payment-system bug, and the reason §4.1 exists.**

**The broken sequence**:

```
Client                          API Server                    PSP
  |                                  |                          |
  |--- POST /charges (no            |                          |
  |     idempotency key)            |                          |
  |     amount=$49.99 ------------->|                          |
  |                                  |--- charge $49.99 ------->|
  |                                  |                          |  (PSP processes
  |                                  |                          |   the charge --
  |                                  |                          |   card IS billed)
  |                                  |<-- 200 OK ---------------|
  |        X  <-- response lost      |                          |
  |        (network timeout          |                          |
  |         on the way back)         |                          |
  |                                  |                          |
  |  Client sees a timeout.          |                          |
  |  From the client's view, it      |                          |
  |  has NO IDEA whether the         |                          |
  |  charge succeeded. The only      |                          |
  |  "safe" thing to do is retry.    |                          |
  |                                  |                          |
  |--- POST /charges (RETRY,        |                          |
  |     no idempotency key,          |                          |
  |     amount=$49.99 -------------->|                          |
  |                                  |--- charge $49.99 ------->|
  |                                  |                          |  (PSP has no idea
  |                                  |                          |   this is a retry --
  |                                  |                          |   card IS BILLED
  |                                  |                          |   AGAIN)
  |                                  |<-- 200 OK ---------------|
  |<-- 200 OK ------------------------|                          |
  |                                  |                          |

RESULT: customer billed $49.99 TWICE for one purchase. Support ticket,
chargeback, refund processing, and a very unhappy customer -- all because
a network blip happened at exactly the wrong millisecond.
```

**The fixed sequence** (with `Idempotency-Key`, §4.1):

```
Client                          API Server                    PSP
  |                                  |                          |
  |--- POST /charges                |                          |
  |     Idempotency-Key: abc-123     |                          |
  |     amount=$49.99 ------------->|                          |
  |                                  |--- INSERT INTO           |
  |                                  |    idempotency_keys      |
  |                                  |    (key='abc-123',       |
  |                                  |     status=IN_PROGRESS)  |
  |                                  |    -- SUCCEEDS, claimed  |
  |                                  |--- charge $49.99 ------->|
  |                                  |                          |  (card IS billed)
  |                                  |<-- 200 OK ---------------|
  |                                  |--- UPDATE idempotency_   |
  |                                  |    keys SET status=      |
  |                                  |    COMPLETED, response=  |
  |                                  |    {...} WHERE key=      |
  |                                  |    'abc-123'             |
  |        X  <-- response lost      |                          |
  |                                  |                          |
  |--- POST /charges (RETRY,        |                          |
  |     SAME Idempotency-Key:        |                          |
  |     abc-123,                     |                          |
  |     amount=$49.99 -------------->|                          |
  |                                  |--- INSERT INTO           |
  |                                  |    idempotency_keys      |
  |                                  |    (key='abc-123', ...)  |
  |                                  |    -- FAILS: UNIQUE      |
  |                                  |    constraint violation  |
  |                                  |--- SELECT ... WHERE      |
  |                                  |    key='abc-123'         |
  |                                  |    -> status=COMPLETED,  |
  |                                  |    response={...}        |
  |                                  |--- NO CALL TO PSP --------|  (none!)
  |<-- 200 OK (cached response) -----|                          |
  |                                  |                          |

RESULT: customer billed $49.99 ONCE. The retry is a pure database read
(one INSERT that fails fast on a unique-constraint violation, one SELECT)
-- no PSP call, no second charge, response time ~1-2ms.
```

**Fix**: Every state-changing endpoint requires `Idempotency-Key`; the dedup table's UNIQUE constraint is the enforcement mechanism, and "unique constraint violation" is treated as "cache hit," not "error" (§4.1 code).

### War Story 2: Wallet Balance Race Condition

**Broken** — the classic "read, compute, write" race:

```
Initial state: customer wallet balance = $100

Thread A (withdraw $80)              Thread B (withdraw $80)
  SELECT balance FROM accounts          SELECT balance FROM accounts
  WHERE id = 42  --> returns 100        WHERE id = 42  --> returns 100
  (in application code:                 (in application code:
   newBalance = 100 - 80 = 20)           newBalance = 100 - 80 = 20)
  UPDATE accounts SET balance = 20      UPDATE accounts SET balance = 20
  WHERE id = 42                         WHERE id = 42

RESULT: balance = $20, but $160 was withdrawn from a $100 balance.
$80 "vanished" -- the account is overdrawn by $60 with NO record of it,
because both withdrawals "succeeded" according to the application logic
that read balance=100 before either write happened.
```

**Fixed** — atomic conditional update, no separate SELECT:

```sql
-- Both threads run this EXACT statement. The database, not the
-- application, evaluates "balance >= amount" AND performs the
-- decrement as one atomic, row-locked operation.
UPDATE accounts
SET balance_minor = balance_minor - 8000   -- $80.00 in cents
WHERE account_id = 42 AND balance_minor >= 8000;

-- Thread A runs first: balance_minor (10000) >= 8000 -> TRUE.
--   Row updated to 2000. 1 row affected.
-- Thread B runs second (after A's row lock releases):
--   balance_minor is now 2000. 2000 >= 8000 -> FALSE.
--   0 rows affected -- Thread B's application code checks
--   "rows affected == 0" and throws InsufficientFundsException.

RESULT: exactly one withdrawal succeeds. The second is correctly
rejected. Balance ends at $20, matching the ONE successful $80
withdrawal from $100.
```

**Fix**: `UPDATE accounts SET balance_minor = balance_minor - ? WHERE account_id = ? AND balance_minor >= ?`, checking rows-affected — this is exactly the `transfer()` debit statement in §4.2. (`SELECT ... FOR UPDATE` followed by an application-level check-and-update is an equally valid fix, but the single-statement `UPDATE ... WHERE` is preferred because it cannot be implemented incorrectly — there's no "read" step for application code to get wrong.)

### War Story 3: Webhook Replay Causing a Double-Posted Ledger Entry

**What happened**: A PSP sent a `charge.succeeded` webhook. The payment service's webhook handler took 6 seconds to process (it ran the full `transfer()` ledger write synchronously inside the HTTP handler, including the two `INSERT INTO ledger_entries` statements plus a downstream notification call) — longer than the PSP's 5-second response timeout. The PSP, having received no 200 response in time, retried the webhook 30 seconds later. The handler had no dedup logic, so it ran `transfer()` *again* for the same payment — posting a **second** debit/credit pair to the ledger for a transaction that had already settled once.

**Impact**: The merchant's account was credited twice for one $250 transaction. The error wasn't caught until the nightly reconciliation job (§4.5) flagged a `DISCREPANCY` — the PSP's settlement report showed one $250 settlement, but the internal ledger showed $500 credited to the merchant for that `transaction_id`.

**Fix**: Dedup on the PSP's `event_id` (§4.6) — a `processed_webhook_events` table with a UNIQUE constraint on `event_id`, claimed via `INSERT ... ON CONFLICT DO NOTHING` *before* any ledger writes happen. A retried webhook with the same `event_id` returns `200 OK` immediately without calling `transfer()` a second time. Additionally, the handler was changed to **acknowledge the webhook (200 OK) immediately after claiming the event_id**, then process the ledger write asynchronously — removing the 6-second processing time from the PSP's response-timeout budget entirely, which independently reduces the *rate* of retries (and thus the surface area for this class of bug) even with the dedup fix in place.

### War Story 4: Currency Rounding Drift from `double`

**What happened**: An early version of the ledger stored `amount` as a Java `double` (and the corresponding SQL column as `DOUBLE PRECISION`). Individual transactions looked fine — `$19.99` displayed as `19.99`, charges matched receipts. But `double` cannot represent most decimal fractions exactly in binary floating point: `0.1 + 0.2 == 0.30000000000000004` in IEEE 754 double-precision arithmetic. Across millions of transactions, these sub-cent representation errors accumulated.

**Impact**: After approximately 8 months and ~2.4 billion ledger entries, the nightly reconciliation job's "global ledger sums to zero" check (§8) started failing — not by a dramatic amount, but by a few dollars, then tens of dollars, growing slowly. The discrepancy was *real money* the platform's books couldn't account for, distributed as fractions of a cent across millions of rows — individually invisible, in aggregate a four-figure unexplained variance that took finance and engineering two weeks to trace to its root cause, because no single transaction looked wrong in isolation.

**Fix**: Migrated `amount` columns from `DOUBLE PRECISION` to `BIGINT` storing **integer minor units** (cents) — `$19.99` is stored as the integer `1999`, full stop, with no floating-point representation involved at any point. All arithmetic (`balance_minor - amount_minor`) is integer arithmetic, which is exact by construction. (An alternative is `NUMERIC`/`BigDecimal` with a fixed scale, e.g., `NUMERIC(19,4)` — also exact, but integer minor units are simpler, faster, and are what §4.2's schema uses.) The migration itself required a careful backfill: every existing `double` value was rounded to the nearest cent (`Math.round(value * 100)`) and the *difference* between the old double-summed total and the new integer-summed total was booked as a one-time correcting ledger entry, reviewed and approved by finance before being applied — exactly the "flag, then a human applies the fix via the normal `transfer()` path" pattern from §4.5/§8.

**Lesson, stated as a hard rule**: **never use `double` or `float` for any monetary value, anywhere — not in the database, not in application code, not in JSON serialization (use a string or integer minor units in API payloads, never a JSON number for currency amounts above a few dollars).** This is called out explicitly in §4.2's schema comments for exactly this reason.

---

## 10. Capacity Planning

### Database Connection Pool Sizing for Ledger Writes
- At 232 ledger writes/sec average (§2, peak ~1,160/sec), and each `transfer()` (§4.2) taking ~2-5ms (two `UPDATE`s + two `INSERT`s, in-region synchronous replica ack), a single connection can sustain roughly 200-500 transfers/sec.
- Following this repo's HikariCP convention of a **default pool size of 10** as a starting point: a pool of 10 connections, each handling ~100-200 transfers/sec comfortably, gives **1,000-2,000 transfers/sec capacity** — well above the 1,160/sec peak, leaving headroom for reconciliation queries and connection contention from lock waits.
- Rule of thumb validated under load testing, not assumed: size the pool to `target_peak_tps / sustainable_tps_per_connection`, then add 30-50% headroom for lock-wait variance (the debit-then-credit lock ordering in §4.2 means some transfers wait briefly for a hot account's lock, e.g., the `PLATFORM_FEES` account touched by every transaction).

### PSP Rate Limits and Retry Queue
- PSPs impose per-account rate limits (e.g., Stripe's default is in the hundreds of requests/sec for standard accounts, with higher limits available on negotiated enterprise plans). At 580 TPS peak (§2), a single PSP account may approach or exceed default limits during traffic spikes.
- Mitigation: a **retry queue** (a Kafka topic or a database-backed queue) absorbs `429 Too Many Requests` responses from the PSP — the charge request is requeued with exponential backoff (e.g., 1s, 2s, 4s, ...) rather than failed outright. Because every retry carries the *same* idempotency key (§4.1), requeuing is safe even if an earlier attempt is still in flight at the PSP.
- For sustained high volume, negotiate increased rate limits with the PSP *and* maintain a secondary PSP (§8 PSP Outage runbook) — splitting traffic across two PSPs both increases effective throughput and provides the failover path.

### Idempotency-Cache Sizing
- From §2: ~1 GB resident in Redis for a 24-hour window of 10M keys/day at ~100 bytes/key.
- At 5x peak growth (50M transactions/day, a plausible 5-year horizon for a growing platform), this becomes ~5 GB — still comfortably within a single Redis node (or a small 3-node cluster for HA), with the 24-hour TTL ensuring the dataset size is self-bounding regardless of total historical transaction volume.
- Redis availability for the idempotency cache is itself a critical-path dependency (§8) — a Redis outage that causes idempotency checks to **fail-open** (treat "cache unavailable" as "key not seen, proceed with charge") reintroduces the exact double-charge risk this entire case study exists to prevent. The recommended posture is **fail-closed**: if the idempotency check cannot be performed, return `503 Service Unavailable` rather than risk a duplicate charge — a temporary unavailability is recoverable; a duplicate charge is not.

---

## 11. Interview Discussion Points

**Q: How do you prevent double-charging a customer when their client retries a request after a timeout?**
A: The client generates a unique `Idempotency-Key` (a UUID) once per logical payment attempt and sends it with every retry of that attempt. The server maintains a dedup table with a UNIQUE constraint on the key — the first request claims the key (via `INSERT`) and proceeds with the charge; any retry with the same key hits a unique-constraint violation, which the server interprets as "I've already handled this" and returns the cached response from the first attempt without contacting the PSP again. The key insight is that the database's UNIQUE constraint, not application-level locking, is what makes this safe under concurrent retries — see §4.1 and War Story 1 (§9) for the full broken-vs-fixed sequence.

**Q: Why use a double-entry ledger instead of just a `balance` column on the accounts table?**
A: A `balance` column is a mutable cache of "the sum of everything that's happened to this account" with no record of *why* it's the value it is — if a bug double-applies an update or a migration script touches it directly, there's no audit trail to detect or correct the error. A double-entry ledger makes every money movement an immutable, append-only pair of debit/credit rows that sum to zero, so the ledger *is* the source of truth and any derived balance is always reconstructable and verifiable by summing entries (§4.2). The cost is 2x writes per transaction, which is negligible at this system's scale (~232 writes/sec average) compared to the auditability and correctness guarantees gained.

**Q: This system can't use Two-Phase Commit across the order service, the ledger, and the PSP — why not, and what replaces it?**
A: 2PC requires every participant to "prepare" (acquire and hold locks) and then wait for a coordinator's commit/abort decision — but the PSP is a third-party system that cannot be asked to hold an authorization in a "prepared but not committed" limbo state indefinitely, so you literally cannot 2PC an external payment processor. Even ignoring the PSP, 2PC's blocking behavior under coordinator failure (participants hold locks until the coordinator recovers) is a known cause of cascading outages (see [`../distributed_transactions/README.md`](../distributed_transactions/README.md) §10's 45-minute lock-hold incident). The replacement is Saga + Outbox (§4.3/§4.4): each step is a local transaction that commits independently, the PSP call is an isolated, retryable, idempotent step outside any database transaction, and compensations (refunds) are new transactions rather than rollbacks.

**Q: A PSP webhook arrives twice for the same event — how do you avoid processing it twice?**
A: Every PSP webhook event carries a unique `event_id`. The handler atomically claims this ID in a `processed_webhook_events` table via `INSERT ... ON CONFLICT (event_id) DO NOTHING` *before* doing any ledger writes — if the insert affects 0 rows, the event has already been processed, and the handler returns `200 OK` immediately without re-running any business logic. This is the same "claim before processing" pattern as the idempotency-key check (§4.1), applied to inbound events instead of inbound requests. War Story 3 (§9) shows the concrete double-ledger-posting bug this prevents.

**Q: How do you guarantee currency amounts are correct down to the cent across millions of transactions?**
A: Store every monetary amount as an **integer in minor units** (cents) — `BIGINT` in the database, `long` in application arithmetic — never `double`/`float`, and never a `NUMERIC`/`BigDecimal` with a variable or unspecified scale. `double` cannot represent most decimal fractions exactly in binary floating point, and while a single transaction's rounding error is invisible, summing millions of them produces a ledger that doesn't balance to zero — exactly what happened in War Story 4 (§9), where ~2.4 billion entries accumulated a four-figure discrepancy over 8 months before reconciliation caught it.

**Q: Walk me through your reconciliation strategy — what does the nightly job actually check, and what does it do when it finds a problem?**
A: The job downloads the PSP's settlement report for the previous day and cross-references it against the internal `payments` table by PSP reference ID, flagging three categories: amount/status mismatches, "orphans" (we recorded success but the PSP has no matching settlement — possibly a forged webhook), and "unrecorded" (the PSP settled a charge we have no record of). Independently, it verifies the global ledger invariant — `SUM(debits) - SUM(credits) == 0` across all `ledger_entries`. The job **flags discrepancies for human review and never auto-corrects** — any correction goes through the normal `transfer()` code path (§4.2) as a new, audited transaction, reviewed by finance, because an automated "fix" to a financial ledger is itself a risk (§4.5/§8).

**Q: What happens if the outbox relay process crashes in the middle of a batch?**
A: The relay polls unpublished `outbox` rows with `FOR UPDATE SKIP LOCKED`, publishes each to Kafka, then marks it `published_at = now()`. If the process crashes after `kafka.send()` succeeds but before the `UPDATE` commits, that row remains unpublished and is republished on restart (or by another relay instance) — the consumer receives the event a second time. This is *at-least-once* delivery, which is why downstream consumers (Order Service, Notification Service) must be idempotent on `(aggregate_id, event_type)` — at-least-once delivery plus an idempotent consumer equals effectively-once processing, the same principle underlying §4.1's client-facing idempotency keys (§4.4, and [`../distributed_transactions/README.md`](../distributed_transactions/README.md) §6.3/§10 War Story 3).

**Q: Where exactly does this design draw the line between strong consistency and eventual consistency?**
A: The `accounts` and `ledger_entries` tables — the ledger of record — are strongly consistent: every `transfer()` is an ACID transaction on a synchronously-replicated primary, and a merchant's available balance reflects every committed transfer instantly (§5). Everything downstream of the outbox — webhook notifications to merchants, the Order Service marking an order as paid, analytics dashboards, reporting read replicas — is eventually consistent, typically converging within milliseconds to low seconds. The dividing line is precisely "does an incorrect/stale value here let someone take an action that creates a financial inconsistency" — a stale balance could let a merchant over-withdraw; a stale "order paid" notification just means a UI updates a moment late.

**Q: How would you add support for a new payment method or a second PSP?**
A: The PSP Adapter (§3) is designed as a pluggable interface — `authorize(token, amount, currency, idempotencyKey) -> AuthResult` and `processWebhook(rawBody, signature) -> WebhookEvent` — implemented once per PSP (Stripe adapter, Adyen adapter). Adding a new PSP means implementing this interface against the new PSP's SDK, registering its webhook signature secret in Vault/KMS, and adding routing logic (e.g., "route EUR transactions to Adyen, USD to Stripe" or "Adyen as failover when Stripe's circuit breaker is open," §8). Critically, the **ledger schema, idempotency-key handling, and outbox pattern are entirely PSP-agnostic** — a new PSP is purely an adapter-layer addition, with zero changes to §4.2-§4.5.

**Q: How do you handle a partial refund?**
A: A refund — partial or full — is a **new transaction**, not a modification of the original. `transfer()` (§4.2) runs again with the accounts reversed (e.g., `MERCHANT_PAYABLE -> CUSTOMER_RECEIVABLE` for the refunded amount) and a new `transaction_id`, linked to the original via a `refund_of` foreign key on the `payments` table. For a partial refund, the refunded `amount_minor` is simply less than the original charge's `amount_minor` — the ledger entries for the original charge are never touched, so the full history ("charged $100, later refunded $30") remains visible and auditable. The PSP call for the refund itself also carries its own idempotency key, following the exact same pattern as the original charge.

**Q: How do you reduce PCI-DSS compliance scope for this system?**
A: By ensuring raw card data (the PAN, CVV, expiry) **never reaches your servers at all** — the card input form is rendered by the PSP's hosted fields/iframe (e.g., Stripe Elements, Adyen Drop-in) directly in the client, which tokenizes the card and sends only an opaque token to your backend. Your servers handle tokens, never PANs, which moves the bulk of your infrastructure out of PCI-DSS's strictest scope (SAQ A or SAQ A-EP, "card data never touches your systems," instead of SAQ D, "we store/process/transmit cardholder data," which requires far more extensive controls, audits, and network segmentation). This is why §3's architecture diagram explicitly labels the PSP Adapter as "tokenized card, no raw PAN ever touches us."

**Q: How do you test a payment system without making real charges against a real PSP?**
A: PSPs provide sandbox/test-mode environments with well-documented "magic" test card numbers that deterministically trigger specific outcomes (successful charge, declined card, insufficient funds, requires 3-D Secure, etc.) without moving real money — Stripe's test mode is the canonical example. For unit and integration tests of the orchestrator/ledger logic itself (independent of the PSP), the PSP Adapter interface (from the "new PSP" question above) is mocked/stubbed so that `transfer()`, the outbox, and idempotency-key logic can be tested deterministically and quickly, with a smaller suite of true end-to-end tests against the PSP's sandbox run less frequently (e.g., nightly) to catch integration drift (API version changes, webhook payload shape changes) without making every CI run dependent on an external service's availability and latency.

---

## Cross-References

- **Saga, TCC, outbox pattern, idempotency keys (theory underlying §4.1, §4.3, §4.4)** -> [`../distributed_transactions/README.md`](../distributed_transactions/README.md)
- **PCI-DSS, tokenization, secrets management, HMAC webhook verification (§4.6, §7, §11)** -> [`../security_and_auth/README.md`](../security_and_auth/README.md)
- **PSP outage failover, retry with backoff, circuit breakers (§8 runbooks)** -> [`../resilience_patterns/README.md`](../resilience_patterns/README.md)
- **Event-driven propagation of payment status changes via the outbox/Kafka pipeline (§3, §4.4)** -> [`../event_sourcing_cqrs/README.md`](../event_sourcing_cqrs/README.md)
- **Database-internals view of cross-shard/cross-service transactions (ledger consistency at scale)** -> [`../../database/distributed_transactions/README.md`](../../database/distributed_transactions/README.md)
- **Production implementation of XA drivers, Kafka transactional producers, and `@Transactional` boundaries used in §4.2/§4.4** -> [`../../backend/distributed_transactions_and_consistency/README.md`](../../backend/distributed_transactions_and_consistency/README.md)
