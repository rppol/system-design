# System Design: Digital Wallet

## Intuition

> **Design intuition**: A digital wallet is a **bank account you don't need a bank for** — a stored-value balance that lives entirely inside your platform's database, where moving money from Alice to Bob is not "ask a card network to move money between two banks" (that's [`./design_payment_system.md`](./design_payment_system.md)'s problem) but **"decrement one row and increment another row, in the same database, in the same transaction."** The hard part isn't talking to the outside world — it's that the *inside* world (your own ledger) must never let two concurrent transfers see a balance that's stale by even one row, because unlike a merchant charge that fails safely and gets retried, a wallet transfer that double-spends $50 has already "succeeded" twice from the sender's perspective before anyone notices.

**Key insight**: A payment system's hardest problem is the **boundary with the outside world** — the PSP, the card network, the bank — and its answer is `Idempotency-Key` plus a saga, because the outside world is slow, unreliable, and outside your control. A digital wallet's hardest problem is the **opposite**: the transfer is entirely *inside* your system (both wallets, the ledger, the balance check, all in one database), so it *should* be instant and atomic — and the design challenge is making sure "should be" is actually "is" under thousands of concurrent transfers per second touching the same hot accounts. The wallet only touches the slow, unreliable outside world at its two edges — **top-up** (bank -> wallet) and **withdrawal** (wallet -> bank) — which is exactly where this design reuses the payment system's saga/outbox machinery, while the P2P transfer core (§4.1-§4.3) is a new problem: **concurrency control on a hot row, not consistency-across-services**.

---

## 1. Requirements Clarification

### Functional Requirements
- **Peer-to-peer wallet transfer**: User A sends money instantly from their wallet balance to User B's wallet balance (Venmo "pay a friend," PayPal "Friends & Family," Cash App "Cashtag" transfer, Alipay/WeChat transfer to a contact).
- **Top-up ("add money")**: User links a bank account or debit card and moves money **into** their wallet — an inbound transfer from an external bank rail (ACH, card network) that settles asynchronously and starts in a `PENDING` state.
- **Withdrawal ("cash out")**: User moves money **out of** their wallet to a linked bank account — an outbound transfer to an external bank rail, also asynchronous, often with a "standard" (1-3 business days, free) vs. "instant" (minutes, fee) tier.
- **Balance inquiry**: User can see their current available balance, and a paginated transaction history (transfers in/out, top-ups, withdrawals).
- **Split / request money**: A user can request money from one or more other users (a "request" creates a pending obligation that the payer can accept, triggering a normal P2P transfer).
- **Multi-wallet per user (optional)**: A user may hold balances in more than one currency (scoped explicitly in §4.5).

### Non-Functional Requirements
- **Strong consistency for wallet balances**: A wallet's balance must never go negative (absent an explicit overdraft/credit product) and must never reflect a "lost" or "duplicated" transfer — this is the single hardest NFR and drives nearly every design decision in §4.
- **Instant P2P transfers**: a wallet-to-wallet transfer should complete and be reflected in both balances in **well under 1 second (p99 < 500ms)** — this is the headline UX differentiator vs. a bank transfer that takes days.
- **Idempotent retries**: a client retry of a transfer request (after a timeout) must never produce a second transfer — directly analogous to [`./design_payment_system.md`](./design_payment_system.md) §4.1, reused here.
- **High availability**: 99.95%+ — a wallet that can't be read or written to is a wallet that can't be trusted with money.
- **Full auditability**: every balance change must be reconstructable from an immutable, append-only ledger — corrections are new entries, never edits (same principle as [`./design_payment_system.md`](./design_payment_system.md) §4.2, reapplied to a different ledger schema, §4.1).
- **Fraud/AML controls**: velocity limits on transfer frequency/amount, KYC-tiered balance and transfer caps, and suspicious-pattern detection (§4.6).

### Out of Scope
- **Merchant checkout / "pay with wallet at a merchant"** — that flow is "customer's wallet balance funds a merchant charge," which is the *payment system's* problem once the funding source is selected ([`./design_payment_system.md`](./design_payment_system.md) covers the merchant side); this design covers only wallet-to-wallet and wallet-to-bank movements.
- **Credit/lending products** (e.g., Alipay's Huabei "buy now pay later" credit line, discussed as a real-world extension in §6) — this design assumes a wallet can only hold and move money it actually has (no overdraft).
- **Card-issuing** (a physical/virtual debit card backed by the wallet balance, e.g., Cash Card or the Venmo debit card) — a real product feature, but it re-enters payment-system territory (card network integration) and is out of scope here.

### Why "Strong Consistency" Means Something Different Here Than in a Payment System

[`./design_payment_system.md`](./design_payment_system.md) §1 frames strong consistency as "the ledger and the charge must agree" — a property that's checked, in the worst case, by a *nightly* reconciliation job, because the PSP is the actual source of truth for whether money moved and the platform's ledger is catching up to that truth. A digital wallet has no such external source of truth for a P2P transfer — **the platform's database is the only record that Alice's $50 went to Bob, full stop.** There is no PSP statement to reconcile against for a P2P transfer (only for the top-up/withdrawal edges, §4.4, where the bank rail *is* an external source of truth). This is why §4.1-§4.3's concurrency control — not the saga/outbox machinery — is this design's central engineering problem, in direct contrast to the payment system's center of gravity.

---

## 2. Scale Estimation

### User Base and Activity
- **80 million monthly active users (MAU)**, of which **20 million are daily active (DAU)** — a 25% DAU/MAU ratio typical of a financial app (used in bursts: payday, splitting a bill, paying rent).
- Each DAU performs roughly **0.5 wallet-affecting actions/day** on average (most days a user *checks* their balance without moving money) -> **10 million transfers+top-ups+withdrawals/day**.

### Transfer QPS
- 10,000,000 / 86,400 sec ~= **~116 transfers/sec average**
- Peak (lunchtime bill-splitting, Friday-night Venmo spikes, end-of-month rent payments runs **4-5x average**) -> **~500-600 transfers/sec peak**
- Of these, **P2P transfers dominate** (~70%), with top-ups (~20%) and withdrawals (~10%) making up the rest:

| Flow | Share | Avg QPS | Peak QPS |
|---|---|---|---|
| P2P wallet-to-wallet transfer | 70% | ~81/sec | ~400/sec |
| Top-up (bank -> wallet) | 20% | ~23/sec | ~115/sec |
| Withdrawal (wallet -> bank) | 10% | ~12/sec | ~60/sec |

### Balance Read:Write Ratio
- Every transfer (write) is preceded by the client showing the user's current balance (read) — and users check their balance far more often than they move money (opening the app, viewing the home screen).
- Estimate **20 balance-reads per balance-write** -> at 116 writes/sec average, that's **~2,320 balance reads/sec average**, peaking around **~10,000 reads/sec** — this read-heavy skew is why the design caches the `balance` column aggressively (§4.1) rather than computing `SUM(ledger_entries)` on every read.

### Ledger Volume
- Every P2P transfer produces **exactly 2 ledger rows** (one debit, one credit) — same double-entry principle as [`./design_payment_system.md`](./design_payment_system.md) §4.2, but the *accounts* on either side of the entry are both **user wallet accounts**, not customer/merchant/PSP-clearing accounts.
- 10M transfers/day x 2 rows = **20M ledger rows/day**
- Ledger row size: ~150 bytes (`entry_id` BIGSERIAL 8B, `transaction_id` UUID 16B, `wallet_id` 8B, `direction` 1B, `amount_minor` 8B, `currency` 3B, `created_at` 8B, plus index overhead) -> 20M x 150B ~= **3 GB/day**, **~1.1 TB/year** before replication (RF=3 -> ~3.3 TB/year)

### Idempotency-Key Store
- 10M transfer requests/day, each with a client-generated `Idempotency-Key`
- ~100 bytes/key (key + status + cached response) x 10M = **~1 GB resident** with a 24h TTL — same sizing as [`./design_payment_system.md`](./design_payment_system.md) §2's idempotency cache, reused verbatim

### Transaction History Query Volume

- Each DAU views their transaction history roughly **0.3 times/day** (less frequent than a balance check, but a common "did that payment go through" lookup) -> 20M DAU x 0.3 ~= **6 million history queries/day** ~= **~70 queries/sec average**, peaking around **~350/sec**.
- A history query is a paginated `SELECT ... FROM ledger_entries WHERE wallet_id = ? ORDER BY created_at DESC LIMIT 20` against `idx_ledger_wallet` (§4.1) — an indexed range scan, not a full-table operation, so this volume is comfortably absorbed by the same per-shard infrastructure that serves balance reads (§10), with read replicas absorbing the bulk of it (history is tolerant of a few seconds of replica lag, unlike the transfer-write path's need for a current `version`).

### Latency Targets

| Operation | p50 | p99 |
|---|---|---|
| P2P transfer (wallet-to-wallet) | < 150ms | < 500ms |
| Balance read | < 20ms | < 100ms |
| Transaction history page | < 50ms | < 200ms |
| Top-up initiation (sync part) | < 200ms | < 800ms |
| Withdrawal initiation (sync part) | < 200ms | < 800ms |

---

## 3. High-Level Architecture

```
                          +---------------------+
                          |       Clients        |
                          |  (mobile / web app)   |
                          +-----------+-----------+
                                       |
                                       | POST /transfers
                                       | Idempotency-Key: <uuid>
                                       v
                          +---------------------+
                          |    API Gateway        |
                          | (authn, rate limit,   |
                          |  TLS termination)     |
                          +-----------+-----------+
                                       |
              +------------------------+------------------------+
              |                        |                          |
              v                        v                          v
   +-------------------+   +----------------------+   +----------------------+
   | Wallet Transfer    |   | Top-Up Service        |   | Withdrawal Service    |
   | Service            |   | (bank/card -> wallet, |   | (wallet -> bank,      |
   | (P2P, §4.1-§4.3)   |   | async, §4.4)          |   | async, §4.4)          |
   +---------+----------+   +-----------+-----------+   +-----------+-----------+
             |                          |                            |
             | sharded by               | saga + outbox              | saga + outbox
             | wallet_id (§4.3)         | (cross-ref                 | (cross-ref
             v                          |  distributed_transactions) |  distributed_transactions)
   +-------------------+                v                            v
   | Wallet Ledger DB   |     +----------------------+   +----------------------+
   | (sharded, ~64      |     |  PSP / Bank Rail      |   |  PSP / Bank Rail      |
   |  shards, §10)      |     |  Adapter (ACH/card)   |   |  Adapter (ACH/RTP)    |
   |  - accounts        |     +-----------+-----------+   +-----------+-----------+
   |  - ledger_entries  |                 |                            |
   |  - idempotency_keys|                 v                            v
   +---------+----------+      +-------------------+         +-------------------+
             |                  |  Bank / Card       |         |  Bank (ACH/RTP)    |
             | outbox           |  Network           |         |  settlement,       |
             v                  |  (settles in       |         |  1-3 days standard,|
   +-------------------+        |   minutes-days)    |         |  minutes if instant|
   |   Kafka            |        +-------------------+         +-------------------+
   |  topic: wallet.*   |
   +----+------+----+---+
        |      |    |
        v      v    v
  +--------+ +--------+ +----------------------+
  | Notif. | | Fraud/ | | Reconciliation        |
  | Service| | AML    | | Service (nightly:     |
  | (push  | | Engine | | sum(ledger) == sum    |
  |  "you  | | (§4.6) | |  (balances) per       |
  |  got   | |        | |  shard + cross-check  |
  |  paid")| |        | |  vs. bank settlement) |
  +--------+ +--------+ +----------------------+
```

### Request Flow

1. **P2P transfer** (the headline flow, §4.1-§4.3): the client sends `POST /transfers {from_wallet, to_wallet, amount, currency}` with an `Idempotency-Key`. The Wallet Transfer Service checks the idempotency table, then performs an **atomic intra-database transfer** — debit one wallet, credit the other, write two ledger rows — all in one transaction against the shard owning both wallets (cross-shard transfers are the exception, discussed in §5). Because both balance updates and both ledger writes happen in **one local ACID transaction**, there is no saga, no outbox, no "eventual" anything on this path — it's the opposite design point from the payment system's PSP-bound charge flow.
2. **Top-up** (§4.4): the client requests to add money from a linked bank account or card. This *is* a saga — the wallet doesn't actually have the money yet, so the flow writes a `PENDING` ledger entry, calls out to the bank/card rail (which can take minutes to days to settle for ACH, or seconds for an instant debit card top-up), and only marks the funds available once the rail confirms. This reuses the outbox + saga pattern from [`./design_payment_system.md`](./design_payment_system.md) §4.3-§4.4 and [`../distributed_transactions/README.md`](../distributed_transactions/README.md) almost unchanged — the *only* difference is the destination of funds is a wallet account, not a merchant payable account.
3. **Withdrawal** (§4.4): the inverse of top-up — the wallet balance is debited **immediately** (so the user can't spend money that's mid-withdrawal) and a `PENDING` external transfer is initiated to the bank. If the bank rail later fails (invalid account, etc.), the wallet is **credited back** via a new ledger entry (never by editing the original debit) — the same "compensation is a new transaction" principle as a payment refund.
4. **Fraud/AML checks** (§4.6) run inline on every P2P transfer (velocity limits, KYC-tier caps) and asynchronously (via the Kafka event stream) for pattern-based detection (structuring, money-laundering layering patterns).
5. **Reconciliation** (§8) runs nightly per-shard: `SUM(ledger_entries for shard)` must equal `SUM(accounts.balance_minor for shard)`, and the sum of top-up/withdrawal ledger entries must reconcile against the bank rail's settlement files — directly analogous to [`./design_payment_system.md`](./design_payment_system.md) §4.5, but checking wallet-shard balances instead of merchant payables.

---

## 4. Component Deep Dives

### 4.1 Wallet Balance Model: Ledger-Derived `balance` Column with Optimistic Locking

**The core design question**: is a wallet's balance a number you *store* (a `balance` column, updated on every transfer) or a number you *compute* (`SELECT SUM(...) FROM ledger_entries WHERE wallet_id = ?`)?

This design uses **both**, with the ledger as the source of truth and the `balance` column as a **maintained, authoritative cache** kept in lockstep via the same transaction that writes the ledger rows — never updated independently.

```sql
CREATE TABLE wallets (
    wallet_id      BIGINT PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    currency       CHAR(3)     NOT NULL,        -- ISO 4217: USD, EUR, INR, ...
    balance_minor  BIGINT      NOT NULL DEFAULT 0,  -- integer minor units (cents)
    version        BIGINT      NOT NULL DEFAULT 0,  -- optimistic-lock counter
    kyc_tier       SMALLINT    NOT NULL DEFAULT 1,  -- gates limits, §4.6
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, FROZEN, CLOSED
    CONSTRAINT non_negative_balance CHECK (balance_minor >= 0),
    CONSTRAINT one_wallet_per_currency UNIQUE (user_id, currency)
);

CREATE TABLE ledger_entries (
    entry_id        BIGSERIAL PRIMARY KEY,
    transaction_id  UUID        NOT NULL,   -- groups the debit+credit pair
    wallet_id       BIGINT      NOT NULL REFERENCES wallets(wallet_id),
    direction       CHAR(1)     NOT NULL CHECK (direction IN ('D','C')),
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        CHAR(3)     NOT NULL,
    entry_type      VARCHAR(20) NOT NULL,   -- P2P_TRANSFER, TOPUP, WITHDRAWAL, FEE, REVERSAL
    balance_after   BIGINT      NOT NULL,   -- snapshot of balance_minor AFTER this entry
                                              -- (cheap point-in-time audit, avoids replay)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    description     TEXT
);
-- INVARIANT, checked nightly (§8): for every transaction_id with entry_type
-- in ('P2P_TRANSFER'), SUM(CASE direction WHEN 'D' THEN amount_minor
-- ELSE -amount_minor END) = 0  (top-up/withdrawal entries are single-sided
-- by design, §4.4 -- their "other side" is the external bank rail, not
-- another wallet)
CREATE INDEX idx_ledger_wallet ON ledger_entries (wallet_id, created_at DESC);
CREATE INDEX idx_ledger_txn ON ledger_entries (transaction_id);
```

**Why a maintained `balance` column, not pure `SUM(ledger_entries)`**: §2 establishes a **20:1 balance-read:write ratio** (~2,320 reads/sec average vs. ~116 writes/sec). Summing potentially thousands of historical ledger rows on every balance check — at 2,320 reads/sec — would mean the *read* path, not the write path, dominates database load, which is backwards for a financial product where users check their balance far more often than they move money. The `balance_minor` column turns every balance read into a single indexed point-lookup (`< 20ms p50`, §2), while `ledger_entries` remains the append-only audit trail that the reconciliation job (§8) uses to **verify** the cached balance is correct — if `SUM(ledger_entries WHERE wallet_id = X) != wallets.balance_minor` for any wallet, that's the same class of CRITICAL alert as [`./design_payment_system.md`](./design_payment_system.md)'s "ledger doesn't balance to zero."

**Optimistic locking (`version` column) is the primary concurrency-control mechanism** (full comparison in §5). The transfer below:

```java
@Service
public class WalletLedgerService {

    private final JdbcTemplate jdbc;
    private static final int MAX_RETRIES = 3;

    /**
     * Atomically transfers amountMinor from one wallet to another, writing
     * the matching double-entry ledger rows, with optimistic-lock retry on
     * the debit side (the side that can fail due to a concurrent update).
     * Idempotency is enforced by the caller (§4.2) BEFORE this is invoked --
     * this method assumes the idempotency key has already been claimed.
     */
    @Transactional
    public TransferResult transfer(UUID transactionId, long fromWallet, long toWallet,
                                    long amountMinor, String currency) {

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (fromWallet == toWallet) {
            throw new IllegalArgumentException("cannot transfer to self");
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            // Read current balance + version for the debit side. This is the
            // ONLY read in the hot path -- everything else is conditional
            // writes.
            WalletRow from = jdbc.queryForObject("""
                SELECT balance_minor, version, status FROM wallets
                WHERE wallet_id = ? AND currency = ?
                """, WalletRow::map, fromWallet, currency);

            if (!"ACTIVE".equals(from.status())) {
                throw new WalletNotActiveException(fromWallet, from.status());
            }
            if (from.balanceMinor() < amountMinor) {
                throw new InsufficientFundsException(fromWallet, amountMinor, currency);
            }

            // Optimistic conditional UPDATE: succeeds only if `version` is
            // STILL what we just read. If another transfer committed in
            // between, version has moved on and rowsUpdated == 0.
            int debited = jdbc.update("""
                UPDATE wallets
                SET balance_minor = balance_minor - ?, version = version + 1
                WHERE wallet_id = ? AND currency = ? AND version = ?
                  AND balance_minor >= ?
                """, amountMinor, fromWallet, currency, from.version(), amountMinor);

            if (debited == 0) {
                // Lost the race -- another transfer touched fromWallet
                // between our SELECT and our UPDATE. Re-read and retry.
                // This is the OPTIMISTIC-LOCK RETRY at the heart of §5's
                // chosen concurrency strategy.
                continue;
            }

            // Credit side: a plain balance increment never fails on
            // insufficient funds, so no version check is strictly required
            // for correctness here -- but we still bump `version` for
            // audit/cache-invalidation consistency.
            jdbc.update("""
                UPDATE wallets
                SET balance_minor = balance_minor + ?, version = version + 1
                WHERE wallet_id = ? AND currency = ?
                """, amountMinor, toWallet, currency);

            long fromBalanceAfter = from.balanceMinor() - amountMinor;
            long toBalanceAfter = jdbc.queryForObject(
                "SELECT balance_minor FROM wallets WHERE wallet_id = ?",
                Long.class, toWallet);

            // Double-entry ledger rows -- SUM TO ZERO for this transaction_id.
            jdbc.update("""
                INSERT INTO ledger_entries
                  (transaction_id, wallet_id, direction, amount_minor, currency,
                   entry_type, balance_after, description)
                VALUES (?, ?, 'D', ?, ?, 'P2P_TRANSFER', ?, 'Transfer out')
                """, transactionId, fromWallet, amountMinor, currency, fromBalanceAfter);

            jdbc.update("""
                INSERT INTO ledger_entries
                  (transaction_id, wallet_id, direction, amount_minor, currency,
                   entry_type, balance_after, description)
                VALUES (?, ?, 'C', ?, ?, 'P2P_TRANSFER', ?, 'Transfer in')
                """, transactionId, toWallet, amountMinor, currency, toBalanceAfter);

            return new TransferResult(transactionId, fromBalanceAfter, toBalanceAfter);
        }

        // All MAX_RETRIES attempts lost the optimistic-lock race -- the
        // fromWallet is under extreme contention (rare for a P2P wallet,
        // common for, e.g., a giveaway/promo wallet sending to thousands
        // of recipients -- §9 War Story 1 covers this case).
        throw new TransferContentionException(fromWallet, MAX_RETRIES);
    }

    public record WalletRow(long balanceMinor, long version, String status) {
        static WalletRow map(ResultSet rs, int rowNum) throws SQLException {
            return new WalletRow(rs.getLong("balance_minor"), rs.getLong("version"), rs.getString("status"));
        }
    }
}
```

### 4.2 Idempotency: Client-Generated Key + Dedup Table

Identical contract to [`./design_payment_system.md`](./design_payment_system.md) §4.1: the client generates an `Idempotency-Key` UUID once per logical transfer attempt, and the server's dedup table's UNIQUE constraint is the arbiter of "first attempt vs. retry."

```sql
CREATE TABLE transfer_idempotency_keys (
    idempotency_key  VARCHAR(64)  PRIMARY KEY,
    request_hash     VARCHAR(64)  NOT NULL,   -- hash of (from, to, amount, currency)
    status           VARCHAR(20)  NOT NULL,   -- IN_PROGRESS | COMPLETED | FAILED
    transaction_id   UUID,                    -- set once COMPLETED
    response_body    JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- TTL: 24h, mirrored in Redis for sub-ms lookups (same as payment system §2/§6)
```

```java
@RestController
@RequestMapping("/transfers")
public class TransferController {

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TransferRequest req) {

        var outcome = idempotencyService.claim(idempotencyKey, toJson(req));

        switch (outcome.type()) {
            case REPLAY:
                // Client retried after a timeout. The transfer ALREADY
                // happened -- return the original result, move ZERO money.
                return ResponseEntity.status(outcome.cachedStatus()).body(outcome.cachedBody());

            case IN_PROGRESS:
                return ResponseEntity.status(409).body(TransferResponse.retryShortly());

            case CLAIMED:
                UUID transactionId = UUID.randomUUID();
                var result = walletLedgerService.transfer(transactionId,
                    req.fromWalletId(), req.toWalletId(), req.amountMinor(), req.currency());
                var response = TransferResponse.from(result);
                idempotencyService.complete(idempotencyKey, 200, response, transactionId);
                return ResponseEntity.ok(response);

            default:
                throw new IllegalStateException("unreachable");
        }
    }
}
```

The one wallet-specific wrinkle vs. the payment system: **`request_hash`** must include `from_wallet_id`, `to_wallet_id`, `amount_minor`, and `currency` — if a client reuses an `Idempotency-Key` for a *different* recipient or amount (a client bug, e.g., a key generated once and reused across a "send to multiple friends" UI), the hash mismatch must reject loudly (§4.1's `IdempotencyKeyReusedException` pattern), because silently replaying the *first* recipient's transfer for a *different* intended recipient would send money to the wrong person.

### 4.3 Concurrency Control — Sequence Diagram and Alternatives

**Chosen approach: optimistic locking with version-column retry** (§4.1's `WalletLedgerService`). The sequence for "Alice sends Bob $50 while Alice's balance is also being debited by another transfer (Alice -> Carol, $30)":

```
Time  Thread 1 (Alice->Bob $50)         Thread 2 (Alice->Carol $30)
----  --------------------------         --------------------------
t0    SELECT balance, version
      FROM wallets WHERE id=Alice
      -> balance=100, version=7
                                          SELECT balance, version
                                          FROM wallets WHERE id=Alice
                                          -> balance=100, version=7

t1    UPDATE wallets SET
      balance = balance - 50,
      version = version + 1
      WHERE id=Alice AND version=7
        AND balance >= 50
      -> 1 row updated.
         balance=50, version=8

t2                                       UPDATE wallets SET
                                          balance = balance - 30,
                                          version = version + 1
                                          WHERE id=Alice AND version=7
                                            AND balance >= 30
                                          -> 0 ROWS updated!
                                             (version is now 8, not 7 --
                                              someone else committed first)

t3    [commits debit + credit +          Thread 2's transaction RETRIES:
       2 ledger rows for Alice->Bob]      re-reads balance=50, version=8
                                          UPDATE ... WHERE version=8
                                            AND balance >= 30 -> 1 row.
                                          balance=20, version=9
                                          [commits debit + credit + ledger
                                           rows for Alice->Carol]

RESULT: Alice's final balance = 100 - 50 - 30 = 20. Correct. Thread 2 paid
a small retry cost (one extra round trip) but NEVER read a stale balance
that let it overdraw Alice's account.
```

```
+-------+                +----------------------+              +-------+
| Alice |                | Wallet Ledger Service |              |  Bob  |
| client|                | (shard owning Alice & |              | wallet|
+---+---+                |        Bob)           |              +---+---+
    |  POST /transfers              |                                |
    |  {from=Alice, to=Bob, $50}     |                                |
    |  Idempotency-Key: k1 --------->|                                |
    |                                | claim(k1) -> CLAIMED            |
    |                                | SELECT balance,version (Alice)  |
    |                                | UPDATE wallets SET balance-=50, |
    |                                |   version+=1 WHERE version=7    |
    |                                |   AND balance>=50  -> 1 row     |
    |                                | UPDATE wallets SET balance+=50  |
    |                                |   WHERE id=Bob                  |
    |                                | INSERT ledger (D, Alice, 50)    |
    |                                | INSERT ledger (C, Bob, 50)      |
    |                                | COMMIT                          |
    |<--- 200 OK {balances} ---------|                                |
    |                                |--- publish WalletCredited ----->|
    |                                |    (outbox -> Kafka -> push     |
    |                                |     notification to Bob)        |
```

**Alternatives considered**:

- **Pessimistic row locks (`SELECT ... FOR UPDATE`)**: acquire an exclusive lock on the `fromWallet` row (and, in a consistent global order, the `toWallet` row if it's also being locked) before reading the balance, hold it until commit. Guarantees no lost updates with **zero retries** — every transaction either gets the lock or queues behind it. The cost is **lock-wait queueing under contention**: a wallet that's a popular split-bill destination (a "house account" used by a group of roommates) serializes every transfer through it, and a slow transaction (network blip mid-transaction) holds the lock for the *entire* slow duration, blocking everyone behind it. Optimistic locking degrades more gracefully here — a slow reader doesn't block a fast writer, because there's nothing to block; the fast writer just wins the version race and the slow one retries.
- **Per-wallet sharded actor/queue serialization**: route all operations on `wallet_id = X` through a single in-memory actor (or a per-wallet queue/partition) that processes them strictly one at a time — no database-level locking at all, because there's never more than one in-flight operation per wallet by construction. This is attractive for **extremely hot wallets** (a viral giveaway account sending to 100,000 recipients in an hour, War Story 1 in §9) where optimistic-lock retries would thrash continuously, but it adds an entire actor-lifecycle/routing layer (which node owns which wallet's actor right now, what happens on actor-node failure) that's unjustified complexity for the **99.99% of wallets that see a handful of transfers per day**.

| Dimension | Optimistic locking (chosen) | Pessimistic `FOR UPDATE` | Per-wallet actor/queue |
|---|---|---|---|
| Normal-case overhead | One extra UPDATE-with-WHERE per attempt; usually 0 retries | Lock acquisition on every transaction, even uncontended ones | Routing + queue overhead on every operation |
| Behavior under contention | Retries (bounded, §4.1's `MAX_RETRIES`); throughput degrades gracefully | Lock-wait queueing; a slow holder blocks all waiters | No contention possible by construction — fully serialized per wallet |
| Failure mode for a "hot" wallet | Retry storm (War Story 1, §9) if `MAX_RETRIES` too low | Long queue, rising p99 latency, possible timeout cascade | None for that wallet, but adds operational complexity everywhere |
| Operational complexity | Low — standard SQL, no extra infrastructure | Low — standard SQL | High — actor placement, failover, rebalancing |
| Best fit | The overwhelming majority of wallets (low per-wallet contention) | Workloads where retries are unacceptable and contention is rare | A small, identifiable set of pathologically hot wallets (handled as a special case, not the default) |

**Why optimistic wins as the primary mechanism**: at ~116 transfers/sec average across tens of millions of distinct wallets (§2), the probability that *two* transfers touch the *same* wallet within the same few-millisecond window is low for a typical user — optimistic locking's retry path is **rarely exercised** in the common case, making its low normal-case overhead the dominant factor. The pathological hot-wallet case (§9 War Story 1) is handled as an **exception path** (a feature-flagged "high fan-out sender" mode that falls back to a queue), not by paying the actor-infrastructure cost for every wallet.

### 4.4 Top-Up and Withdrawal — Async Flows on Bank Rails

Unlike the instant, single-transaction P2P transfer (§4.1-§4.3), top-up and withdrawal touch an **external bank rail** (ACH, debit card network, RTP/instant-payment rails) that the wallet platform does not control — this is precisely the saga + outbox territory that [`./design_payment_system.md`](./design_payment_system.md) §4.3-§4.4 and [`../distributed_transactions/README.md`](../distributed_transactions/README.md) cover in depth; this section focuses on the wallet-specific sequencing.

**Top-up (bank/card -> wallet)**:

1. User initiates "Add $100 from linked bank account." The Top-Up Service writes a `topups` row with `status='PENDING'` and an `outbox` row (`TopUpInitiated`) **in one local transaction** — no ledger entry yet, because the wallet doesn't have the money.
2. The PSP/bank-rail adapter initiates an ACH debit (or an instant card-network debit) against the linked account, carrying the **same idempotency key** through to the rail (mirroring [`./design_payment_system.md`](./design_payment_system.md) §4.3's PSP idempotency-header passthrough).
3. **ACH path (slow)**: the debit is submitted to the ACH network and typically takes **1-3 business days** to settle (and can still be reversed via an ACH return for up to 60 days for consumer accounts — a critical fraud surface, §4.6). The wallet balance is credited **optimistically** at step 2 (most users expect "Add Money" to reflect immediately) but the `topups` row stays `PENDING` until settlement, and a **reversal** (ACH return) triggers a new offsetting ledger entry (`entry_type='REVERSAL'`) that debits the wallet — which can drive the balance negative if the user already spent the optimistically-credited funds (handled via the `non_negative_balance` CHECK being relaxed for `REVERSAL` entries specifically, with the resulting negative balance frozen for collection, §8).
4. **Instant debit-card path (fast)**: the card network authorizes in seconds; the wallet credit becomes `CONFIRMED` immediately, no optimistic-credit risk window.

**Withdrawal (wallet -> bank)**:

1. User initiates "Withdraw $200 to linked bank account." The Withdrawal Service **debits the wallet immediately** (via the same `WalletLedgerService.transfer`-style optimistic-locked UPDATE, §4.1, but crediting an internal `PENDING_WITHDRAWALS` clearing account instead of another user's wallet) — this happens **synchronously**, so the user's available balance reflects the withdrawal instantly and they cannot double-spend the withdrawn funds.
2. An `outbox` row (`WithdrawalInitiated`) is written in the same transaction; the bank-rail adapter picks it up and initiates the outbound transfer.
3. **Standard tier**: ACH credit to the user's bank, settling in **1-3 business days**, free.
4. **Instant tier** (Visa Direct / RTP-style push-to-card or push-to-bank, settling in **minutes**, for a fee — this is the rail Venmo and Cash App use for "Instant Transfer to bank," §6): the adapter calls the instant-rail API synchronously-ish (a few seconds), and on success marks the withdrawal `COMPLETED`; on failure, a **new ledger entry credits the wallet back** (`entry_type='REVERSAL'`) — the original debit entry is never modified.
5. If a standard-tier ACH credit later fails (invalid account number, closed account — discovered days later via an ACH return), the same reversal-credit pattern applies, only days after the original debit.

```
Top-Up (bank -> wallet)                  Withdrawal (wallet -> bank)
------------------------                  ----------------------------
1. topups row PENDING + outbox            1. wallet DEBITED immediately
   (no ledger entry yet)                     (sync, optimistic-locked)
2. bank rail debit initiated               2. outbox row WithdrawalInitiated
3a. ACH: optimistic wallet CREDIT          3a. standard: ACH credit,
    now, settles in 1-3 days,                  1-3 days, free
    reversal risk window 60 days           3b. instant: push-to-bank,
3b. instant card: confirmed CREDIT             minutes, fee, sync-ish
    in seconds, no reversal risk           4. on rail failure: NEW ledger
4. on ACH return: NEW ledger entry             entry CREDITS wallet back
   (REVERSAL) debits wallet,                   (never edits original debit)
   may go negative -> frozen (§8)
```

The unifying principle, stated once: **the wallet ledger only ever gains entries — it never edits or deletes them. "Undo" is always a new, offsetting entry of `entry_type='REVERSAL'` linked to the original via `transaction_id`** — identical in spirit to [`./design_payment_system.md`](./design_payment_system.md)'s refund-as-new-transaction principle (§4.3 there), applied to bank-rail reversals here.

### 4.5 Multi-Currency: Scoped Out, with an Extension Note

This design's primary schema (§4.1) is **single-currency per wallet row**, enforced by `CONSTRAINT one_wallet_per_currency UNIQUE (user_id, currency)` — a user who holds both USD and EUR has **two separate `wallets` rows** (two `wallet_id`s), each with its own `balance_minor`, `version`, and ledger entries, rather than one wallet row with a "currency" field that changes meaning.

**Why this is the right primary scope**: a P2P transfer between two wallets of the *same* currency (§4.1's `transfer()`) is a pure intra-database operation — no exchange rate, no FX risk, no rate-lock window. The overwhelming majority of P2P transfers (a friend paying back a friend in their shared home currency) fit this model exactly, and keeping the hot path free of FX logic is what makes the sub-500ms p99 target (§2) achievable.

**How a cross-currency transfer would extend this design** (not built here, but the extension point is clear):
- A transfer from a USD wallet to a EUR wallet becomes **two linked transactions**: (1) a normal same-currency debit from the USD wallet to an internal `FX_CLEARING_USD` account, and (2) a normal same-currency credit from an internal `FX_CLEARING_EUR` account to the EUR wallet, with the **exchange rate and FX spread captured as a separate ledger entry type** (`entry_type='FX_CONVERSION'`) showing the platform's FX margin explicitly — the same "treasury/FX operations as separate, clearly-labeled transactions" pattern Uber's multi-currency platform uses ([`./design_payment_system.md`](./design_payment_system.md) §6).
- The rate-lock window (the FX rate is fixed for some number of seconds while the user confirms the transfer) becomes the **new** source of "pending state" in what was previously an instant flow — structurally similar to top-up's optimistic-credit window (§4.4), but on the *milliseconds-to-seconds* scale rather than days.

### 4.6 Fraud, AML, and KYC Tiers

A digital wallet is a regulated money-transmission product in most jurisdictions, and **KYC (Know Your Customer) tier gates both the maximum balance a wallet can hold and the maximum amount it can transfer/withdraw per day** — this is the wallet-specific fraud surface that a merchant-payment system (where the *merchant* is KYC'd, not every individual customer) doesn't have to the same degree.

| KYC Tier | Verification Required | Max Wallet Balance | Max Single Transfer | Max Daily Transfer Volume |
|---|---|---|---|---|
| Tier 0 (unverified) | Phone number + email only | $0 (no stored value — top-up/transfer disabled) | N/A | N/A |
| Tier 1 (basic) | Name, DOB, SSN/national ID (or last-4) | $1,000 | $500 | $1,000 |
| Tier 2 (verified) | Government ID + selfie match | $10,000 | $5,000 | $10,000 |
| Tier 3 (enhanced) | Proof of address, source-of-funds for large amounts | $50,000+ | $25,000 | $50,000+ |

**Velocity limits** (checked inline, before `WalletLedgerService.transfer()` is invoked, so a rejected transfer never touches the ledger):
- **Transfer-count velocity**: more than N transfers in a rolling 10-minute window from one wallet (e.g., N=20) — flags automated/bot behavior, common in account-takeover fraud where an attacker drains a compromised wallet via many small transfers to evade single-transfer limits.
- **New-recipient velocity**: a wallet sending to more than M *distinct new* recipients in 24 hours (e.g., M=10) — a classic "money mule" / structuring pattern, where stolen funds are rapidly fanned out to many accounts to complicate recovery.
- **Round-trip detection**: A sends to B, B immediately sends back to A (or to C, who sends to A) — a layering pattern. Flagged for async review, not blocked inline (false-positive rate too high to block real-time, e.g., splitting a dinner bill and then someone realizing they double-paid).

**KYC-tier enforcement is a `CHECK`-adjacent application-layer gate**, not a database constraint (tier limits change by jurisdiction and over time, and need human-reviewable overrides for edge cases) — but it is checked **synchronously, before the idempotency claim** (§4.2), so a transfer that would exceed a limit is rejected before any state is written, keeping the rejection itself idempotent-safe (retrying a rejected-for-limits request just gets rejected again, with no risk of partial state).

---

## 5. Design Decisions & Tradeoffs

### Maintained `balance` Column vs. Pure `SUM(ledger_entries)`

| Dimension | Maintained `balance_minor` column (chosen) | Pure `SUM(ledger_entries)` on read |
|---|---|---|
| Balance-read cost | O(1) indexed point lookup | O(n) aggregation over all historical entries for that wallet |
| Fits the 20:1 read:write ratio (§2)? | Yes — reads stay cheap regardless of write volume | No — read cost grows with the wallet's lifetime transaction count |
| Risk of drift | Possible if a code path updates balance without writing matching ledger rows (mitigated: both happen in `WalletLedgerService.transfer()`'s single transaction, §4.1) | None — by definition, balance IS the sum |
| Auditability | Ledger remains the source of truth; balance is verified against it nightly (§8) | Maximal — there is nothing to verify against |
| Chosen because | §2's read-heavy access pattern makes O(1) reads non-negotiable; the nightly reconciliation check closes the drift-risk gap | — |

### Concurrency Control: Optimistic Locking vs. Pessimistic Locks vs. Sharded Actors

Covered in full in §4.3; summary: **optimistic locking is the default for all wallets**, with a feature-flagged per-wallet-queue fallback reserved for a small, identifiable set of pathologically high-fan-out sender wallets (War Story 1, §9).

### Optimistic Wallet Credit on Top-Up vs. Wait for Bank Settlement

| Dimension | Optimistic credit (chosen for ACH top-up, §4.4) | Wait for full settlement (1-3 days) |
|---|---|---|
| User experience | "Add Money" reflects in the balance within seconds | User waits days before the money is usable — terrible UX for a wallet product |
| Risk | ACH return (up to 60 days later) can drive balance negative; requires a collections/freeze process (§8) | None — money is real before it's spendable |
| Mitigation | KYC-tier limits (§4.6) bound the optimistic-credit exposure per user; new users (lower trust) get a *shorter* optimistic window or no optimistic credit at all | — |
| Chosen because | The instant-availability expectation is the entire value proposition of a wallet vs. a bank transfer — a wallet that behaves like a 3-day ACH transfer isn't a wallet | — |

### Same-Database Atomic Transfer vs. Cross-Service Saga for P2P

| Dimension | Same-database atomic transaction (chosen, §4.1) | Saga across "debit service" and "credit service" |
|---|---|---|
| Consistency | Immediate, ACID — both balances update or neither does | Eventual — a window exists where the debit happened but the credit hasn't |
| Complexity | Low — one `@Transactional` method | High — compensation logic for "debit succeeded, credit failed" |
| When this breaks down | Both wallets must live on the **same database shard** (§10) | Works across shards/services by design |
| Chosen because | The overwhelming majority of transfers are between two users on the same shard (shard key derived from `wallet_id`, consistent hashing, §10); the rare cross-shard case is the explicit exception below | — |

**Cross-shard P2P transfers** (Alice and Bob's wallets land on different shards): handled as a **two-phase local-transaction saga** — debit Alice's wallet on shard A (writing a `PENDING_CROSS_SHARD` ledger entry and an outbox event), then credit Bob's wallet on shard B upon receiving that event, then a confirmation event marks shard A's entry `COMPLETED`. This is structurally identical to [`../distributed_transactions/README.md`](../distributed_transactions/README.md)'s saga pattern — the design accepts a small (typically sub-second) propagation delay for the minority of transfers that cross shard boundaries, in exchange for keeping the common (same-shard) case a single ACID transaction with zero saga machinery.

### Per-Currency Wallet Rows vs. a Single Multi-Currency Wallet Row

| Dimension | Separate `wallets` row per currency (chosen, §4.5) | One wallet row with a JSON/map of `currency -> balance` |
|---|---|---|
| Concurrency control | `version` column applies cleanly to one `(balance_minor, currency)` pair — optimistic-lock retries (§4.1) are scoped to a single currency | A transfer in USD and an unrelated transfer in EUR for the *same user* would contend on the same row's `version`, causing spurious retries between operations that have nothing to do with each other |
| Schema simplicity | `CHECK (balance_minor >= 0)` is a simple scalar constraint per row | A non-negative-balance constraint over a JSON map's individual keys is awkward in standard SQL and database-specific |
| Query patterns | "Get all of user X's balances" is `SELECT * FROM wallets WHERE user_id = X` — a small, indexed multi-row fetch | "Get user X's EUR balance" requires extracting one key from a JSON blob — works, but loses the benefit of per-currency indexing/constraints |
| FX-transfer extension (§4.5) | Each currency's wallet is a normal same-currency `transfer()` participant; FX conversion is a separate ledger entry type between two wallets | FX conversion would need to atomically update two keys within one JSON blob — re-implements the multi-row transaction the relational model already provides |
| Chosen because | Decomposing "a user's multi-currency holdings" into multiple single-currency rows lets every other part of this design (§4.1's `transfer()`, §4.3's concurrency control, §10's sharding) treat "a wallet" as **always single-currency**, with zero special-casing | — |

### Async Notification Delivery vs. Synchronous "Notify Recipient" in the Transfer Path

| Dimension | Async (outbox -> Kafka -> Notification Service, chosen, §3) | Synchronous push-notification call inside `transfer()` |
|---|---|---|
| Transfer latency impact | None — the transfer commits and returns before any notification is sent | Adds the push-provider's round-trip (50-300ms, and provider-outage-dependent) to every transfer's critical path, directly threatening the p99 < 500ms target (§2) |
| Failure isolation | A push-notification-provider outage (APNs/FCM down) has zero effect on transfer correctness — Bob's balance is credited regardless of whether he's *told* about it immediately | A provider outage in the synchronous path either fails the transfer (unacceptable — Bob's money is real, the notification is not) or requires the transfer code to swallow notification errors (which then makes notification failures invisible) |
| Consistency model | Eventually consistent — Bob's balance updates instantly; his "you got paid!" push arrives moments later | Both happen "together," but coupling a financial state change to a best-effort delivery channel is the wrong dependency direction |
| Chosen because | This is the exact same "ledger commit is strongly consistent, downstream notification is eventually consistent" dividing line as [`./design_payment_system.md`](./design_payment_system.md) §5 draws between the ledger and webhook notifications — a wallet credit is *real* the instant it commits; a push notification is a courtesy, not a guarantee | — |

---

## 6. Real-World Implementations

| Product | Distinguishing Feature | Relevance to This Design |
|---|---|---|
| **Venmo** | Social feed (transfers are semi-public by default) + "Instant Transfer to bank" via **Visa Direct** (minutes, ~1.5% fee) vs. standard ACH (1-3 days, free) | The standard-vs-instant withdrawal tiering in §4.4 is modeled directly on Venmo's two withdrawal options; the social feed is an orthogonal feature layered on top of the same ledger (each P2P transfer's `description` field and a `visibility` flag would drive the feed, not shown in §4.1's minimal schema) |
| **PayPal** | Two-sided ledger with a hard distinction between **"Goods & Services"** (buyer protection, fees apply, dispute-eligible) and **"Friends & Family"** (no protection, no fees, P2P) transfers | The `entry_type` field in §4.1's `ledger_entries` schema is exactly the extension point for this distinction — a `P2P_TRANSFER` entry with `entry_type='GOODS_AND_SERVICES'` would carry different fee and dispute-eligibility logic downstream, without changing the core debit/credit mechanics |
| **Alipay** | **Huabei** (a Buy-Now-Pay-Later credit line integrated into the wallet, letting a "balance" be partially credit-funded) and **QR-code offline payments** (a static or dynamic QR code encodes a wallet ID, scannable without a live network connection at the point of scan) | Huabei is the canonical example of why §1 scopes out "credit/lending products" — a credit-funded balance breaks the `non_negative_balance` invariant (§4.1) in a fundamentally different way than an ACH-reversal-driven negative balance (§4.4), requiring a separate "credit line" ledger account type entirely; QR offline payments are a *client-side* UX feature that still resolves to the same online `transfer()` call (§4.1) once connectivity returns |
| **Cash App** | Wallet balance can be used to buy **Bitcoin and stocks** directly from the balance — the wallet is a funding source for an investment account, not just a P2P tool | This is the cleanest real-world example of "wallet balance as a funding source for a *different* product" — structurally, a Cash App "buy $50 of Bitcoin" debits the wallet via the same `WalletLedgerService.transfer()`-style atomic debit (§4.1), crediting an internal `BROKERAGE_CLEARING` account instead of another user's wallet, then the brokerage system takes over from there |
| **WeChat Pay** | **Red envelopes (hongbao)** — a sender can create a "lucky money" gift that splits a fixed total randomly among up to 100 recipients, claimed asynchronously over hours | A red envelope is structurally a **single debit** (sender's wallet, full amount, immediately) followed by **N pending credits** that get claimed (converted from pending to actual `ledger_entries`) as each recipient opens the envelope — a real-world instance of the "one debit, many eventual credits" pattern that's the *opposite* of this design's "exactly one debit, exactly one credit" P2P transfer, and would extend §4.1's schema with an `entry_type='RED_ENVELOPE_CLAIM'` and a claims-tracking table |

### Funding-Source Tiering Across Products

A pattern visible across every product in the table above: **the wallet balance is never the *only* funding source** — it's the fastest, free one, sitting alongside cards and bank accounts as alternatives. The product-level decision of "which funding source does this transfer draw from" is orthogonal to this design's `transfer()` core (§4.1), which only ever moves money that's *already* a wallet balance.

| Product | Wallet Balance | Linked Bank/ACH | Linked Card | Credit Line |
|---|---|---|---|---|
| Venmo | Yes — primary P2P funding source | Yes — top-up and instant withdrawal (§4.4) | Yes — for sending (incurs a fee), not for receiving | No (Venmo Credit Card is a separate product, not integrated into the wallet balance) |
| PayPal | Yes — "PayPal Balance" | Yes — standard transfer in/out | Yes — funding source for "Goods & Services" payments | PayPal Credit (separate ledger account type, §6's Alipay row) |
| Alipay | Yes | Yes | Yes | Huabei (integrated credit line — the explicit out-of-scope case, §1) |
| Cash App | Yes — and a funding source for Bitcoin/stock purchases (§6) | Yes — standard and instant ("Instant Deposit") withdrawal | Yes (Cash Card draws directly from the balance) | No |
| WeChat Pay | Yes — including red-envelope balances (§6) | Yes | Yes | Weilidai (a separate lending product, analogous to Huabei) |

The consistent thread: **every product that adds a credit-line feature (Huabei, PayPal Credit, Weilidai) implements it as a *separate ledger account type* with its own balance and repayment ledger**, never by relaxing the `non_negative_balance` constraint (§4.1) on the wallet account itself — which is precisely why §1 scopes credit/lending products out as a distinct extension rather than a variant of the core wallet schema.

---

## 7. Technologies & Tools

| Component | Representative Technologies | Notes |
|---|---|---|
| Wallet ledger of record (`wallets`, `ledger_entries`) | PostgreSQL / MySQL, sharded (§10) | ACID transactions, optimistic-lock `UPDATE ... WHERE version = ?` (§4.1) |
| Idempotency-key cache | Redis (`SET key val NX EX 86400`) | Same sizing and pattern as [`./design_payment_system.md`](./design_payment_system.md) §7 |
| Shard routing | Consistent hashing on `wallet_id` (cross-ref [`../consistent_hashing/README.md`](../consistent_hashing/README.md)) | Determines which shard a given wallet (and most P2P transfers) lands on, §10 |
| Top-up / withdrawal bank rails | ACH (Plaid/Dwolla-style aggregators for account linking), Visa Direct / RTP for instant transfers | §4.4 — async, saga + outbox |
| Outbox relay | Kafka + Debezium (CDC) | Identical pattern to [`./design_payment_system.md`](./design_payment_system.md) §4.4 |
| Fraud/AML velocity checks | Redis sliding-window counters (per-wallet transfer count, new-recipient count) | §4.6 — checked inline before idempotency claim |
| Async fraud pattern detection | Kafka consumer + rules engine / ML scoring service | §4.6 — round-trip/structuring detection, async, doesn't block transfers |
| Reconciliation | Nightly batch job, columnar warehouse for cross-shard aggregation | §8 — per-shard ledger-vs-balance checks plus bank-settlement cross-check |
| Encryption at rest for PII (SSN, bank account numbers) | HSM-backed KMS (cross-ref [`../security_and_auth/README.md`](../security_and_auth/README.md)) | KYC data and linked bank account numbers are highly sensitive |

### Build vs. Buy

| Component | Build | Buy / Third-Party | This Design's Choice |
|---|---|---|---|
| Wallet ledger + P2P transfer engine | Custom (§4.1-§4.3) | — | Build — this is the core differentiated logic of the product |
| Bank account linking + ACH initiation | Custom ACH origination (requires ODFI relationship) | Plaid/Dwolla/Stripe Treasury-style aggregators | Buy for account linking and ACH initiation — the bank-relationship and compliance overhead of becoming an ODFI is rarely justified before significant scale |
| Instant withdrawal rail | — | Visa Direct, RTP network via a payments processor | Buy — same reasoning as the payment system's PSP choice ([`./design_payment_system.md`](./design_payment_system.md) §7), one more reason not to build card-network integrations in-house |
| KYC/identity verification | — | Persona, Onfido, Jumio-style identity verification providers | Buy — identity verification (§4.6) is a commodity capability with significant regulatory tooling already built by specialists |
| Fraud/AML rules engine | Custom rules (velocity limits, §4.6) | Sift, Feedzai-style ML fraud platforms for pattern detection | Hybrid — simple velocity limits built in-house (cheap, fast, full control); complex pattern detection (structuring, account-takeover) bought, since training effective fraud models requires data volume most platforms don't have alone |

---

## 8. Operational Playbook

### Key Metrics

| Metric | What It Measures | Alert Threshold |
|---|---|---|
| **P2P transfer success rate** | `succeeded / (succeeded + failed)` over a rolling 5-min window | Drop > 2 percentage points from 7-day baseline -> page |
| **P2P transfer p99 latency** | End-to-end `/transfers` response time | > 500ms sustained for 5 min -> page (§2's NFR) |
| **Optimistic-lock retry rate** | `retries / total transfer attempts` | Sustained > 1% -> investigate hot-wallet contention (War Story 1, §9) |
| **Ledger-vs-balance drift (per shard)** | `SUM(ledger_entries.signed_amount) - wallets.balance_minor`, per wallet | **Any non-zero value -> CRITICAL, page immediately** |
| **Negative-balance count** | Wallets with `balance_minor < 0` (should be zero except `REVERSAL`-pending, §4.4) | > 0 outside the expected ACH-return-collections set -> page |
| **Outbox lag (top-up/withdrawal)** | Age of oldest unpublished `outbox` row | > 60s -> warn; > 5 min -> page |
| **ACH return rate** | `ACH returns / ACH top-ups` over rolling 24h | > baseline (typically <1%) by 2x -> investigate, possible fraud wave |
| **Velocity-limit rejection rate** | `rejected-for-velocity / total transfer attempts` | Sudden spike -> possible coordinated attack; sudden drop -> possible bypass bug |

### Runbook: Ledger-vs-Balance Drift (CRITICAL)

A non-zero `SUM(ledger_entries) - wallets.balance_minor` for any wallet means the cached balance and the audit trail disagree — money exists in one place but not the other.

1. **Page immediately** — same severity class as [`./design_payment_system.md`](./design_payment_system.md)'s "ledger doesn't balance to zero" runbook.
2. **Freeze the affected wallet** (`status = 'FROZEN'`) — blocks new transfers in or out while investigating, without affecting other wallets on the shard.
3. Identify whether the drift is a **missing ledger entry** (balance changed but no corresponding row — likely a code path that updated `balance_minor` outside `WalletLedgerService.transfer()`) or a **missing balance update** (ledger rows exist but `balance_minor` wasn't adjusted — likely a crash mid-transaction, which `@Transactional` should make impossible, so finding this means the transaction boundary itself is broken).
4. Correct via a **new offsetting `REVERSAL` ledger entry** that brings `balance_minor` back into agreement with `SUM(ledger_entries)` — never edit `balance_minor` directly without a corresponding entry.
5. Unfreeze the wallet once corrected and verified.

### Runbook: Optimistic-Lock Retry Storm

1. Identify the contended `wallet_id`(s) via the retry-rate metric, broken down per-wallet.
2. If a small number of wallets account for the majority of retries (typical signature of War Story 1, §9 — a high-fan-out sender), **enable the per-wallet-queue fallback** (§4.3) for those specific wallets via feature flag — this routes their transfers through a serialized queue instead of optimistic retries.
3. If retries are broadly distributed (not concentrated), this may indicate a **traffic spike** rather than a hot-wallet problem — check overall transfer QPS against §2's peak estimates and consider whether shard-level capacity (§10) needs scaling.
4. Once the queue fallback is active (or the spike subsides), confirm the retry rate returns to baseline before closing out.

### Runbook: ACH Return Spike (Possible Fraud Wave)

1. A sudden 2x+ increase in the ACH-return-rate metric (top-ups that were optimistically credited and later reversed, §4.4) often indicates a **coordinated fraud pattern** — e.g., stolen bank account numbers used to "top up" wallets, with the funds spent via P2P transfers before the ACH return arrives days later.
2. Cross-reference affected wallets against the fraud/AML async pipeline (§4.6) — look for a cluster of accounts created in a short window, all performing "top-up then immediately transfer out" within minutes.
3. For affected wallets with negative balances post-reversal (§4.4's collections case): freeze the wallet, do not allow further transfers out, and route to the collections process.
4. Consider **temporarily shortening or removing the optimistic-credit window** for new (low-KYC-tier) accounts platform-wide while the fraud wave is investigated — trading UX for risk exposure, a decision made jointly with the fraud/risk team, not unilaterally by on-call engineering.

---

## 9. Common Pitfalls & War Stories

### War Story 1: A Viral Giveaway Account Triggers an Optimistic-Lock Retry Storm — Broken, Then Fixed

**Broken**: A marketing promotion gave a single corporate wallet account a balance of $500,000 and instructed it to send $5 to each of the first 100,000 users who completed a sign-up action, as a one-time "welcome bonus." The promotion went viral; within a 20-minute window, roughly **8,000 transfer requests/sec** targeted the *same* `from_wallet_id` (the promo account), each one a `WalletLedgerService.transfer()` call (§4.1) attempting the optimistic-locked `UPDATE wallets SET balance = balance - 500, version = version + 1 WHERE wallet_id = PROMO AND version = ?`.

**Impact**: At that request rate against a single row, the optimistic-lock `version` column became a **write hotspot** — every one of the 8,000 concurrent transactions/sec read some `version = V`, but by the time each one issued its conditional `UPDATE`, the version had already advanced past `V` (often many times over) due to the sheer concurrency. The overwhelming majority of attempts hit `rowsUpdated == 0` and retried; with `MAX_RETRIES = 3` (§4.1), a large fraction of requests **exhausted all retries and threw `TransferContentionException`**, surfacing to users as "transfer failed, please try again" — for a $5 welcome bonus that the *system* was trying to send *to* them, not the other way around. Users retried manually, multiplying the request rate further. The promo account's row became so contended that even *unrelated* read queries against it (balance checks for the promo dashboard) experienced elevated latency due to lock contention on the underlying database page. The promotion had to be paused after 35 minutes, with roughly 60% of intended recipients having received their bonus and the rest needing a manual backfill.

**Fixed**: Two changes, one tactical and one structural:
1. **Tactical (immediate)**: the promo account was switched to the **per-wallet sharded queue fallback** described in §4.3/§4.5 — all outbound transfers from `wallet_id = PROMO` were routed through a single ordered queue (one consumer, strictly sequential processing), eliminating concurrent writes to that row entirely. Throughput dropped to the queue's sequential processing rate (~500-1,000 transfers/sec, bounded by per-transfer latency, not by contention), but **zero requests failed** — they simply queued and were processed in order, each completing in well under the original system's worst-case retry-exhaustion latency.
2. **Structural (post-incident)**: a new **"high fan-out sender" wallet flag** was added to the `wallets` schema (§4.1), settable by an internal admin tool. Any wallet flagged this way automatically routes through the per-wallet queue (§4.3's alternative) instead of optimistic locking, and the platform's promo/payroll/bulk-disbursement tooling **requires** this flag to be set before allowing a bulk-send operation to begin — turning "did anyone think about contention" from a per-promotion judgment call into a structural gate.

### War Story 2: A Missing Idempotency Key Causes a Duplicate P2P Transfer on Client Retry — Broken, Then Fixed

**Broken**: An early version of the mobile client's "Pay" button implementation did not persist a generated `Idempotency-Key` across app states — the key was generated fresh, in memory, each time the "Confirm Payment" screen was rendered. On a slow or flaky mobile network, the sequence was:

```
1. User taps "Confirm Payment" ($200, Alice -> Bob).
   Client generates key=K1, sends POST /transfers {Idempotency-Key: K1, ...}
2. Server processes the transfer fully: debits Alice $200, credits Bob
   $200, writes ledger rows, marks idempotency_keys[K1] = COMPLETED.
3. Response is sent, but the mobile device's connection drops at exactly
   this moment -- the 200 OK never arrives at the client.
4. The client's UI, seeing no response after its timeout (10s), shows a
   "Something went wrong, tap to retry" screen.
5. User taps "Confirm Payment" AGAIN. Because the key was generated fresh
   on screen-render (not persisted from step 1), the client generates a
   NEW key=K2 and sends POST /transfers {Idempotency-Key: K2, ...} --
   same amount, same recipient, but a DIFFERENT idempotency key.
6. The server has never seen K2 before -> claim() returns CLAIMED ->
   WalletLedgerService.transfer() runs AGAIN: debits Alice ANOTHER $200,
   credits Bob ANOTHER $200.
```

**Impact**: Alice was debited $400 for a single intended $200 payment to Bob, with two entirely legitimate-looking (different idempotency keys, both successfully completed) transactions in the ledger. Because **both transfers succeeded** — there was no error, no failed-request signal, nothing for an error-rate alert to catch — this was invisible to the operational metrics in §8 entirely. It was first reported by users as "I sent money twice for one payment" support tickets, and the pattern (specifically correlated with users on poor network connections, e.g., subway commuters) wasn't recognized as systemic until a support-ticket-clustering analysis several weeks later identified ~3,200 affected transactions totaling approximately $410,000 in unintended duplicate transfers.

**Fixed**: Three changes:
1. **Client-side key persistence**: the `Idempotency-Key` is now generated **once when the user's transfer intent is first formed** (the moment "Confirm Payment" is tapped) and **persisted to local device storage** before the network request is sent — any retry, including retries after an app restart or a "tap to retry" UI flow, reuses the **same persisted key** until the server returns a definitive success or failure response, at which point the persisted key is cleared.
2. **Server-side "duplicate transfer shape" detection (defense in depth)**: independent of idempotency keys, the Wallet Transfer Service now checks for an existing `COMPLETED` `P2P_TRANSFER` ledger entry with the **same `(from_wallet_id, to_wallet_id, amount_minor, currency)`** within the **last 60 seconds** before processing a new, differently-keyed request. A match doesn't auto-reject (legitimate rapid repeat payments happen — "splitting three separate $20 items with the same friend") but **surfaces a confirmation prompt to the client**: "You sent $200 to Bob 12 seconds ago — send again?" This catches the *specific* failure mode of War Story 2 (different keys, same intent) without blocking legitimate rapid repeats, at the cost of one extra indexed query (`idx_ledger_wallet`, §4.1) per transfer.
3. **Refund/clawback tooling**: the ~3,200 affected users were proactively refunded via new offsetting `REVERSAL` ledger entries (§4.4's pattern) — never by editing the duplicate transactions' original entries — and the incident retroactively populated a new "duplicate transfer" category in the reconciliation job's discrepancy report (§8) so any future recurrence (from a *different* root cause) would be caught within 24 hours rather than weeks.

### War Story 3: An ACH Reversal Window Lets a Wallet "Spend Money It Never Had" — Broken, Then Fixed

**Broken**: The original top-up flow (§4.4) credited a user's wallet balance the instant an ACH debit was *initiated* against their linked bank account, with the `topups` row remaining `PENDING` for 1-3 business days until ACH settlement confirmed. Crucially, the **optimistic credit had no per-user limit distinct from the user's normal KYC-tier transfer limits** (§4.6) — a Tier 1 user (max balance $1,000) could initiate a $1,000 ACH top-up from a bank account with a $40 balance (insufficient funds), see their wallet balance jump to $1,000 immediately, and **send that $1,000 to other users via P2P transfers** (§4.1) within minutes, all before the ACH network had any chance to reject the debit.

**Impact**: A coordinated group of accounts (created within the same 48-hour window, later identified as a single fraud ring via the async pattern-detection pipeline, §4.6) executed exactly this sequence at scale: each account initiated a top-up from a bank account with insufficient funds, immediately transferred the optimistically-credited balance out to a small set of "collector" wallets via P2P transfers, and then — 2-3 days later — every one of those ACH debits returned as `R01: Insufficient Funds`. Each "source" wallet's `WalletLedgerService.transfer()` correctly debited based on its *then-current* `balance_minor` (the optimistic credit was real data in the row, §4.1's optimistic locking worked exactly as designed) — the bug wasn't in the transfer mechanics, it was in **allowing the optimistic credit to exist at an amount the user had no track record to justify**. By the time the ACH returns arrived, roughly **$180,000** had already moved through the source wallets to collector wallets, most of which had themselves already been withdrawn (§4.4's withdrawal flow, which has its own — separate — limits) to bank accounts that were closed or under different identities by the time investigation began. The reconciliation job (§8) correctly flagged the resulting negative balances on the source wallets (per War Story-class "negative balance" alerts), but by then the money was gone — reconciliation is a detection control, not a prevention control, and this fraud pattern was designed specifically to exploit the **gap between optimistic credit and settlement confirmation**.

**Fixed**: Three layered controls, each independently sufficient to have blocked this specific pattern:
1. **Tiered optimistic-credit caps, separate from KYC transfer limits**: a *new* (low-tenure) account's optimistic top-up credit is capped far below its nominal KYC-tier balance limit (e.g., a Tier 1 account's *transfer* limit stays $1,000/day per §4.6, but its *optimistic top-up credit* is capped at $100 until the account has at least one successfully *settled* — not just initiated — top-up). Subsequent top-ups from the same linked bank account, after at least one has fully settled, can use progressively higher optimistic limits — a trust-building ramp, not a one-time check.
2. **Outbound-transfer hold on freshly-optimistic funds**: funds credited via an *unsettled* optimistic top-up are tagged in the ledger (`entry_type='TOPUP_PENDING'`) and are **available for spending at a merchant or for the user's own withdrawal request, but not for P2P transfer to a *new* (first-time) recipient** until either the top-up settles or 24 hours pass, whichever is sooner. This specifically targets the "fan out to collector wallets before settlement" pattern without restricting the legitimate "I added money to immediately pay my roommate I pay every month" case (an *existing*, established recipient relationship is exempt from the hold).
3. **Velocity correlation across the top-up -> transfer boundary**: the async fraud pipeline (§4.6) now specifically flags the sequence "top-up initiated" followed by "P2P transfer to a new recipient for >80% of the top-up amount" within a short window (e.g., 1 hour) as a high-priority review pattern — even for accounts whose individual top-up and transfer amounts each fall within normal per-action limits, the *sequence* is the signal.

The broader lesson: **optimistic-credit windows (§4.4, §5) trade settlement risk for UX, and that risk must be bounded by the *recipient's track record*, not just the sender's KYC tier** — a brand-new account's first top-up is the highest-risk moment in its entire lifecycle, and the controls that are appropriate for an established account's hundredth top-up are insufficient for its first.

---

## 10. Capacity Planning

### Wallet Shard Count and Sizing

- Total wallets: 80M users (§2), with most holding a single-currency wallet (multi-currency users hold 2-3 rows, §4.5) -> estimate **~100M wallet rows**.
- Per-wallet row size: `wallets` table row ~80 bytes (`wallet_id` 8B, `user_id` 8B, `currency` 3B, `balance_minor` 8B, `version` 8B, `kyc_tier` 2B, `status` ~10B + overhead) -> 100M x 80B ~= **8 GB** for the `wallets` table alone — small enough to be **entirely cached in memory** across the shard fleet, which is what makes the `< 20ms p50` balance-read target (§2) achievable without per-read disk I/O.
- Ledger table: ~1.1 TB/year (§2), growing — this is the table that drives shard sizing over time, not `wallets`.
- **Shard count**: targeting roughly **64 shards**, each holding ~1.5M wallets (~120MB of `wallets` table) and ~17GB/year of `ledger_entries` growth — chosen so that (a) each shard's `wallets` table comfortably fits in a modest instance's memory with room for the hot working set of `ledger_entries`, and (b) 64 is a convenient power-of-two for consistent-hashing virtual-node distribution ([`../consistent_hashing/README.md`](../consistent_hashing/README.md)).
- **Shard key**: `hash(wallet_id) % 64` (via consistent hashing with virtual nodes for rebalancing, §10's cross-reference) — chosen over `hash(user_id)` so that a user's multiple currency-wallets (§4.5) can land on different shards if needed, though in practice co-locating a user's wallets on one shard (minimizing cross-shard FX-transfer sagas, §5) is the default placement heuristic.

### Same-Shard vs. Cross-Shard Transfer Ratio

- For two **independently chosen** wallet IDs hashed across 64 shards, the probability both land on the same shard is `1/64 ≈ 1.6%` — naively, ~98.4% of P2P transfers would be cross-shard, which would make the "common case is same-shard, atomic transaction" framing of §5 backwards.
- In practice, **P2P transfer graphs are highly clustered** — most transfers are between friends/contacts who tend to have signed up around the same time, in the same region, often via the same referral chains, which correlates with `user_id` ranges and thus (if shard placement considers signup cohort/region as a placement hint, not pure hash) with shard co-location. Production systems achieve **same-shard rates of 80-90%+** for P2P-heavy products by incorporating a lightweight **affinity hint** (e.g., initial shard placement weighted by region) into otherwise consistent-hash-based placement — at 80% same-shard, only ~20% of the ~81 transfers/sec average (§2) — about **16/sec** — require the cross-shard saga path (§5), comfortably within the saga infrastructure's capacity.

### Database Connection Pool and Throughput

- At ~116 transfers/sec average (§2, peak ~600/sec) spread across 64 shards, each shard sees roughly **2-10 transfers/sec average**, peaking at **~10-15/sec** — each `transfer()` (§4.1) taking ~3-8ms (one SELECT, two conditional UPDATEs, two INSERTs, in-region commit).
- Following the HikariCP default pool size of 10 (this repo's convention): a pool of 10 connections per shard, each sustaining 100-300 transfers/sec, gives **1,000-3,000 transfers/sec capacity per shard** — vastly exceeds the per-shard peak of ~15/sec, meaning **connection pool size is not the bottleneck** at this scale; the bottleneck (if any) would be the optimistic-lock retry rate on individual hot wallets (War Story 1), not aggregate throughput.

### Idempotency Cache and Velocity-Limit Counters

- Idempotency cache: ~1GB resident (§2), same as the payment system's sizing — trivial.
- Velocity-limit counters (§4.6): per-wallet sliding-window counters in Redis, one key per wallet per 10-minute window -> ~100M wallets x ~50 bytes/counter = **~5GB**, with a 10-minute TTL keeping this self-bounding regardless of total wallet count.

### Summary Table

| Component | Sizing Basis | Estimated Footprint |
|---|---|---|
| `wallets` table (all shards) | 100M wallets x ~80 bytes | ~8GB total, fully memory-cacheable |
| `ledger_entries` (all shards) | 20M entries/day x ~150 bytes | ~3GB/day, ~1.1TB/year (RF=3: ~3.3TB/year) |
| Shard count | ~1.5M wallets/shard, power-of-two for consistent hashing | 64 shards |
| Cross-shard transfer rate | 80% same-shard (affinity-aware placement) | ~16/sec average requiring saga path |
| Idempotency cache | 10M keys/day x ~100 bytes, 24h TTL | ~1GB |
| Velocity-limit counters | 100M wallets x ~50 bytes, 10-min TTL | ~5GB |
| DB connections per shard | ~15/sec peak / ~100-300/sec per connection | 10 connections (HikariCP default) per shard |

### Disaster Recovery: A Wallet Shard Fails

Unlike a stateless service or a read-heavy cache, a wallet shard going down means **a slice of users temporarily cannot send, receive, or check the balance of money they own** — there is no "serve a slightly stale copy" option for a write path that must read the current `version` (§4.1) to avoid spurious optimistic-lock failures.

| Failure | Detection | Mitigation | Degraded Behavior |
|---|---|---|---|
| Single shard's primary database fails | Health check / replication-lag alert | Synchronous standby (§7) is promoted to primary — same pattern as [`./design_payment_system.md`](./design_payment_system.md) §5's single-region synchronous replica for the ledger of record | Brief (seconds) unavailability for the ~1.5M wallets on that shard during failover; zero data loss because the standby was synchronously caught up |
| Shard's standby also unavailable (rare, correlated failure) | Promotion fails / no healthy replica | Shard's wallets go **read-only** from the most recent durable snapshot — balance reads work (slightly stale), but `transfer()` (§4.1), top-up, and withdrawal are rejected with a "temporarily unavailable" response for affected wallets only | Other 63 shards are completely unaffected — this is the entire point of sharding for blast-radius containment |
| Idempotency-key Redis cache unavailable | Cache lookups time out | **Fail-closed** (same posture as [`./design_payment_system.md`](./design_payment_system.md) §10): if the idempotency check can't be performed, return `503` rather than risk processing a transfer twice. A transfer that's briefly unavailable is recoverable; a duplicate transfer is not |
| Cross-shard saga's destination shard is down (§5) | Outbox event for the credit-side fails to apply | The source shard's debit remains `PENDING_CROSS_SHARD` — retried with backoff once the destination shard recovers; the sender's funds are **debited but in transit**, visible in their transaction history as "pending," not lost |

The key design point for an interview: **64 independent shards mean a single shard's outage affects roughly 1/64th of wallets, not the whole platform** — and within an affected shard, the system degrades from "fully available" to "balance reads only" rather than to "fully unavailable," because read-only access to the most recent durable state is strictly safer than either blocking reads entirely or allowing writes against potentially-stale data.

---

## 11. Interview Discussion Points

**Q: How is "Alice sends Bob $50 while Alice's balance is also being debited by a different concurrent transfer" handled correctly?**
A: Via **optimistic locking** (§4.1, §4.3) — the debit `UPDATE` includes `WHERE version = ? AND balance_minor >= ?`, so it only succeeds if the row hasn't changed since it was read AND there are sufficient funds. If a concurrent transfer committed first, `version` has already advanced, `rowsUpdated == 0`, and the losing transaction **retries** by re-reading the now-current balance and version. This guarantees no lost updates and no overdraws without ever holding a lock across a network round-trip — the entire check-and-update is one atomic SQL statement, identical in spirit to [`./design_payment_system.md`](./design_payment_system.md) War Story 2's `UPDATE ... WHERE balance >= ?` fix, with the added `version` check to detect *any* concurrent modification, not just insufficient-funds ones.

**Q: A client retries a transfer request after a timeout — how do you guarantee Bob doesn't get paid twice?**
A: The client sends a persisted `Idempotency-Key` (generated once when the transfer intent is formed and stored on-device, §4.2 and War Story 2) with every attempt. The server's dedup table has a UNIQUE constraint on this key — the first attempt's `INSERT` succeeds and proceeds with `WalletLedgerService.transfer()`; the retry's `INSERT` fails with a unique-constraint violation, which the server interprets as "already done" and returns the **cached response from the first attempt** without calling `transfer()` again. War Story 2 (§9) is the cautionary tale of what happens when the client *doesn't* persist the key across retries — a different key per attempt means the dedup table never recognizes the retry as a retry, and the duplicate transfer goes through cleanly.

**Q: Why is a wallet-to-wallet P2P transfer architecturally simpler than a merchant payment charge?**
A: Because the entire operation — checking the sender's balance, debiting it, crediting the recipient, writing both ledger rows — happens **inside one database, in one ACID transaction** (§4.1), with no external system in the critical path. A merchant charge ([`./design_payment_system.md`](./design_payment_system.md)) *must* call out to a PSP/card network — a slow, unreliable, third-party system — which is why that design needs a saga, an outbox, and webhook-based async confirmation. A wallet transfer's only saga-like complexity appears at its **edges** (top-up and withdrawal, §4.4), where it genuinely does touch an external bank rail — the P2P core is "just" a database transaction with careful concurrency control.

**Q: How would you detect and fix a wallet whose cached `balance` column has drifted from its ledger entries?**
A: A nightly reconciliation job (§8) computes `SUM(ledger_entries.signed_amount)` per wallet and compares it to `wallets.balance_minor` — **any non-zero difference is a CRITICAL page**, not a warning, because it means the system's two sources of truth for "how much money does this person have" disagree. The fix is to freeze the affected wallet, determine whether a ledger entry is missing or a balance update was skipped, and write a new offsetting `REVERSAL` entry that brings the two back into agreement — never directly edit `balance_minor` without a corresponding ledger row, preserving the append-only audit guarantee.

**Q: What's the difference between this design's top-up flow and a merchant "charge a card" flow?**
A: Structurally, both are sagas with an outbox and async settlement confirmation — but the **destination of funds differs**: a merchant charge's successful settlement credits a `MERCHANT_PAYABLE` account ([`./design_payment_system.md`](./design_payment_system.md) §4.2-§4.3), while a wallet top-up's settlement credits the **user's own wallet** (§4.4). The wallet-specific wrinkle is the **optimistic-credit window**: a wallet top-up via ACH typically credits the user's balance *before* the ACH debit actually settles (because users expect "Add Money" to be instant), creating a reversal-risk window of up to 60 days that a merchant-payment system doesn't have in the same form (a merchant charge's settlement webhook arrives, then the ledger entry is written — no "credit first, confirm later" ordering).

**Q: Why does this design use a maintained `balance` column instead of computing the balance as `SUM(ledger_entries)` on every read, given that the ledger is supposed to be the source of truth?**
A: Because of the **20:1 balance-read:write ratio** (§2) — users check their balance far more often than they move money, so optimizing the read path with an O(1) indexed lookup on `balance_minor` is the right tradeoff, while `SUM(ledger_entries)` would make read latency grow with each wallet's lifetime transaction count. The ledger remains the *audit* source of truth — the nightly reconciliation job (§8) verifies `balance_minor` against `SUM(ledger_entries)` for every wallet, so "the ledger is the source of truth" is preserved as a **verified invariant**, not violated by the existence of a cache.

**Q: How would you extend this design to support a feature like WeChat's "red envelope" — one sender, many recipients, claimed asynchronously?**
A: A red envelope is structurally **one immediate debit** from the sender's wallet for the full amount (a normal `WalletLedgerService.transfer()`-style debit, §4.1, crediting an internal `RED_ENVELOPE_ESCROW` account) followed by **N pending claims**, each of which — when a recipient opens the envelope — becomes its own credit ledger entry crediting that recipient's wallet from the escrow account. This is the "one debit, many eventual credits" pattern, the inverse of this design's "exactly one debit, exactly one credit" P2P transfer — it would need a new `entry_type='RED_ENVELOPE_CLAIM'` and a claims-tracking table recording which recipients have claimed, with unclaimed amounts after an expiry window returned to the sender via a `REVERSAL` entry (§4.4's pattern).

**Q: A promotional account needs to send $5 to 100,000 users in a short window. What goes wrong with the default design, and how do you fix it?**
A: The default optimistic-locking path (§4.1, §4.3) suffers a **retry storm** under extreme single-row contention — at thousands of concurrent transfer attempts/sec against the *same* `from_wallet_id`, the `version` column changes faster than individual transactions can complete their conditional `UPDATE`, exhausting `MAX_RETRIES` for a large fraction of attempts (War Story 1, §9). The fix is a **per-wallet sharded queue fallback**: route all transfers from that specific wallet through a single serialized queue, eliminating concurrent writes to the row entirely. The structural fix is a "high fan-out sender" flag (§9) that routes flagged wallets through the queue automatically, turning hot-wallet handling into policy rather than a per-incident scramble.

**Q: How do KYC tiers interact with the transfer flow — where exactly is a tier limit checked?**
A: KYC-tier limits (§4.6) are checked **synchronously, before the idempotency-key claim** (§4.2) — a transfer that would exceed the sender's tier limit (max single transfer, max daily volume) or the recipient's max-balance cap is rejected immediately, before any database state is written. Checking before the idempotency claim matters for idempotency-safety itself: a rejected-for-limits request, if retried with the same key, simply gets rejected again with no risk of "half-claimed" idempotency state — the rejection path never enters `WalletLedgerService.transfer()` at all.

**Q: What happens if a top-up's ACH debit is later returned (reversed) after the user has already spent the optimistically-credited funds?**
A: A **new `REVERSAL` ledger entry** debits the wallet for the returned amount (§4.4) — never editing the original optimistic-credit entry. If the user already spent the funds, this reversal can drive `balance_minor` **negative**, which is handled as an explicit exception to the `non_negative_balance` CHECK constraint (§4.1) scoped to `REVERSAL` entries specifically. A negative-balance wallet is automatically frozen (no new transfers in or out) and routed to a collections process (§8's negative-balance-count alert) — the user owes the platform money, which is a fundamentally different state than "insufficient funds for this transfer" and requires its own handling outside the normal transfer path.

**Q: How does sharding affect P2P transfers, and what happens when sender and recipient are on different shards?**
A: Wallets are sharded via consistent hashing on `wallet_id` across ~64 shards (§10, cross-ref [`../consistent_hashing/README.md`](../consistent_hashing/README.md)). When both wallets are on the same shard (the common case, ~80% with affinity-aware placement, §10), the transfer is a single ACID transaction (§4.1) — debit, credit, and both ledger rows commit together. When they're on different shards, the transfer becomes a **two-phase saga** (§5): debit on shard A with a `PENDING_CROSS_SHARD` ledger entry and an outbox event, then credit on shard B upon receiving that event, then a confirmation marks shard A's entry `COMPLETED` — the same saga/outbox machinery as top-up/withdrawal (§4.4), just triggered by shard placement rather than by an external bank rail.

**Q: How would you scale the read path if balance-check traffic grew 10x?**
A: The `wallets` table is small (~8GB for 100M wallets, §10) and fully memory-cacheable, so a 10x read increase (to ~100,000 reads/sec, §2's extrapolation) is primarily a **connection/throughput** scaling question, not a data-size one — add read replicas per shard for balance-check traffic (which tolerates a few milliseconds of replica lag far better than the transfer-write path, which must read the *current* `version` to avoid spurious optimistic-lock failures, §4.1). The transfer-write path itself stays on the primary regardless of read scaling, since its SELECT-then-conditional-UPDATE sequence requires up-to-date `version` values.

**Q: Why does the ledger use `entry_type` (P2P_TRANSFER, TOPUP, WITHDRAWAL, FEE, REVERSAL) instead of just debit/credit direction?**
A: Direction (`D`/`C`) tells you *which way* money moved for a given account; `entry_type` tells you *why* — and "why" is what powers transaction-history UIs ("You paid Bob $50" vs. "You added $100 from your bank" vs. "Your $100 top-up was reversed"), fraud analysis (§4.6's round-trip detection needs to distinguish P2P transfers from top-ups), and reconciliation (§8's invariant — "P2P_TRANSFER entries sum to zero per transaction_id" — does NOT hold for TOPUP/WITHDRAWAL entries, which are intentionally single-sided because their "other side" is an external bank rail, not another ledger row). Conflating these into a single field would make every downstream consumer re-derive "why" from context, which is exactly the kind of implicit business logic that becomes a bug magnet.

**Q: A user reports their balance shows $0 but they remember sending money successfully yesterday — what do you check first?**
A: First, check the `ledger_entries` for that `wallet_id` directly (`idx_ledger_wallet`, §4.1) — if the debit entry for yesterday's transfer exists and `balance_after` on that entry shows the *expected* post-transfer balance, but `wallets.balance_minor` shows something different *today*, that's the ledger-vs-balance drift CRITICAL case (§8's runbook) and the wallet should be frozen pending investigation. If instead the ledger shows **additional** entries the user doesn't recognize (e.g., a withdrawal or a second transfer), the most likely explanations are either a duplicate-transfer bug (War Story 2's class of issue, though that one is now mitigated) or — if the additional entries are withdrawals to an unfamiliar bank account — a potential account-takeover, which should immediately trigger the fraud/AML escalation path (§4.6) in addition to the balance investigation.

**Q: A user tops up their wallet and immediately sends most of it to someone else. Should that be allowed, and what's the risk if it is?**
A: It depends entirely on **whether the top-up has settled**. War Story 3 (§9) is the case where it was allowed unconditionally — a brand-new account could optimistically credit its balance from an ACH top-up (§4.4) and fan that balance out to other wallets via normal, individually-limit-compliant P2P transfers, *before* the ACH debit had any chance to bounce. The fix layers three controls: cap the *optimistic* credit amount for low-tenure accounts well below their nominal KYC transfer limit (§4.6) until at least one top-up has fully settled; tag optimistically-credited funds as unavailable for transfer to *new* recipients until settlement or 24 hours; and have the async fraud pipeline specifically flag the "top-up then fan-out to a new recipient" sequence as a pattern, even when each individual action is within limits. The general principle: **an optimistic-credit window is a loan from the platform to the user, and a brand-new user's "credit limit" on that loan should be small until they've established a settlement track record.**

**Q: How would you support a user holding balances in multiple currencies, and why doesn't this design build that as the default?**
A: Each currency is a **separate `wallets` row** for the same `user_id` (enforced by `UNIQUE (user_id, currency)`, §4.1/§4.5) — a user with USD and EUR balances has two `wallet_id`s, each independently subject to optimistic locking, KYC limits, and ledger entries. This is the default (rather than one row with a currency-keyed balance map) because it lets every other part of the design — `transfer()`'s `WHERE currency = ?` clauses, the `non_negative_balance` CHECK, per-currency ledger reconciliation — treat "a wallet" as always single-currency, with zero conditional logic. A same-currency P2P transfer (the overwhelming majority) never needs to know multi-currency exists. Cross-currency transfers (§4.5) would extend this with an `entry_type='FX_CONVERSION'` ledger entry between two internal FX-clearing accounts, plus a rate-lock window — additional complexity intentionally kept out of the hot path.

---

## Cross-References

- **Merchant-side payment flows, PSP integration, and the original idempotency-key/double-entry-ledger patterns this design adapts** -> [`./design_payment_system.md`](./design_payment_system.md)
- **Saga and outbox patterns underlying top-up, withdrawal, and cross-shard transfers (§4.4, §5)** -> [`../distributed_transactions/README.md`](../distributed_transactions/README.md)
- **ACID guarantees for ledger writes and cross-shard consistency at scale (§4.1, §5, §10)** -> [`../../database/distributed_transactions/README.md`](../../database/distributed_transactions/README.md)
- **Consistent hashing for wallet shard placement and rebalancing (§5, §10)** -> [`../consistent_hashing/README.md`](../consistent_hashing/README.md)
- **Sharding and partitioning strategy for the wallet ledger fleet (§10)** -> [`../../database/sharding_and_partitioning/README.md`](../../database/sharding_and_partitioning/README.md)
- **KYC, encryption at rest for PII (SSN, bank account numbers), and secrets management (§4.6, §7)** -> [`../security_and_auth/README.md`](../security_and_auth/README.md)
- **Reconciliation, drift alerting, and SLI/SLO framing for the operational playbook (§8)** -> [`../observability/README.md`](../observability/README.md)
- **Event-driven propagation of wallet-credited/debited events via outbox/Kafka (§3, §4.4)** -> [`../event_sourcing_cqrs/README.md`](../event_sourcing_cqrs/README.md)
