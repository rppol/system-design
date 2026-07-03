# Database Fundamentals

## 1. Concept Overview

Database fundamentals cover the core theoretical guarantees and models that govern how database systems behave under concurrent access, failures, and network partitions. These concepts — ACID, BASE, CAP, PACELC, and isolation levels — form the vocabulary for reasoning about database correctness in distributed systems.

---

## 2. Intuition

Think of a database as a shared ledger that thousands of accountants access simultaneously. ACID guarantees that each accountant's transaction is all-or-nothing, isolated from others, and durable once committed. CAP says that during a network split, the ledger must choose between being accurate (consistent) or always accessible (available) — it cannot be both.

- **Why it matters**: Choosing the wrong consistency model leads to money being double-spent, inventory oversold, or user data corrupted.
- **Key insight**: Most applications need "sufficient" consistency, not perfect consistency — understanding the tradeoffs lets you pick the right point on the spectrum.

---

## 3. Core Principles

### ACID Properties

**Atomicity**: A transaction is all-or-nothing. If a bank transfer debits account A and credits account B, both must succeed or neither does. On failure, the database rolls back to the pre-transaction state. Implemented via undo logs.

**Consistency**: A transaction brings the database from one valid state to another. Constraints (foreign keys, CHECK, UNIQUE) are enforced at commit time. Note: consistency in ACID is about application-defined invariants, NOT the "C" in CAP.

**Isolation**: Concurrent transactions appear to execute serially. The degree of isolation is configurable (see isolation levels below). Implemented via MVCC or locking.

**Durability**: Once committed, a transaction survives crashes. Implemented via Write-Ahead Log (WAL) — changes are written to a durable log before being applied to data pages.

### BASE Model

Stands for: **B**asically **A**vailable, **S**oft state, **E**ventually consistent.

Used by distributed NoSQL systems (Cassandra, DynamoDB) where strong consistency would require coordination that hurts availability and latency.

- **Basically Available**: System responds to every request, possibly with stale data.
- **Soft state**: State can change over time even without input (due to replication convergence).
- **Eventually consistent**: All replicas will converge to the same value given no new updates.

### CAP Theorem

A distributed system can guarantee at most two of three properties simultaneously:

```
          Consistency (C)
               /\
              /  \
             /    \
            /  CA  \
           /--------\
          / CP |  AP \
         /     |      \
        /      |       \
Availability (A)------Partition Tolerance (P)
```

- **CA** (no partition tolerance): Single-node RDBMS. If network partitions can occur, CA is not viable in distributed systems.
- **CP** (consistency + partition tolerance): System refuses to serve stale data during partition. Example: HBase, ZooKeeper, etcd.
- **AP** (availability + partition tolerance): System serves potentially stale data during partition. Example: Cassandra (with eventual consistency), DynamoDB.

**Real production scenario**: A Cassandra cluster with RF=3 and CL=QUORUM experiences a network partition. Two nodes are on one side, one on the other. QUORUM requires 2/3 responses. The minority side (1 node) cannot serve QUORUM reads/writes — it sacrifices availability for consistency at QUORUM. Switch to CL=ONE and you get AP behavior with potential stale reads.

### PACELC Extension

CAP only covers behavior during partitions. PACELC adds the else case: even when no partition, there is a tradeoff between **Latency** and **Consistency**.

```
PAC: if Partition → choose Availability or Consistency
ELC: ELse       → choose Latency or Consistency
```

Examples:
- PostgreSQL with sync replication: PA/EC (partitions → consistent, no partition → low latency sacrificed for consistency)
- Cassandra default: PA/EL (partitions → available, no partition → low latency, eventual consistency)
- DynamoDB: PA/EL (similar to Cassandra)

---

## 4. Consistency Models

From strongest to weakest:

```
Linearizability (strongest)
    └── Real-time ordering. Every read reflects the latest write.
        Cost: coordination on every read. Used in: etcd, ZooKeeper, Spanner.

Sequential Consistency
    └── Operations appear to execute in some sequential order, consistent
        with program order per process. No real-time guarantee.

Causal Consistency
    └── Causally related operations appear in correct order.
        Concurrent ops can be seen in different orders by different nodes.
        Used in: MongoDB (sessions), some Cassandra configurations.

Read-Your-Writes
    └── After a write, the same client always sees that write.
        Implemented by sticky sessions to primary, or by versioned reads.

Monotonic Read
    └── If you read a value, you never read an older value in future reads.

Eventual Consistency (weakest)
    └── All replicas eventually converge. No ordering guarantee.
        Used in: DNS, S3 (historically), Cassandra at CL=ONE.
```

---

## 5. Transaction Isolation Levels

SQL standard defines four isolation levels, each preventing certain anomalies:

```
+------------------+---------------+-------------------+---------------+----------------+
| Isolation Level  | Dirty Read    | Non-Repeatable    | Phantom Read  | Write Skew     |
|                  |               | Read              |               |                |
+------------------+---------------+-------------------+---------------+----------------+
| READ UNCOMMITTED | Possible      | Possible          | Possible      | Possible       |
| READ COMMITTED   | Not possible  | Possible          | Possible      | Possible       |
| REPEATABLE READ  | Not possible  | Not possible      | Possible (*)  | Possible       |
| SERIALIZABLE     | Not possible  | Not possible      | Not possible  | Not possible   |
+------------------+---------------+-------------------+---------------+----------------+

(*) InnoDB prevents phantom reads at REPEATABLE READ via next-key locks.
    PostgreSQL REPEATABLE READ (Snapshot Isolation) allows phantom reads technically,
    but prevents them in practice for most workloads.
```

**Anomaly Definitions:**

- **Dirty read**: Transaction reads uncommitted data from another transaction.
- **Non-repeatable read**: Re-reading a row returns different data because another transaction committed between the two reads.
- **Phantom read**: A query returning a set of rows returns different rows when re-executed (insert/delete by another transaction).
- **Lost update**: Two transactions read a value, both modify it, one update overwrites the other.
- **Write skew**: Two transactions read overlapping data and make decisions that together violate a constraint (classic: two on-call doctors both check "at least one on call" and both go off-call).

**PostgreSQL default**: READ COMMITTED
**MySQL/InnoDB default**: REPEATABLE READ

---

## 6. How It Works — Detailed Mechanics

### ACID Implementation in PostgreSQL

```sql
-- Atomicity: if the debit succeeds but credit fails, entire transaction rolls back
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT; -- Both or neither

-- If crash occurs after debit but before commit:
-- PostgreSQL reads WAL on restart, finds uncommitted xact, applies UNDO
```

### MVCC Mechanics

Multi-Version Concurrency Control maintains multiple versions of rows:

```
PostgreSQL Row Header:
+--------+--------+--------+--------+
| xmin   | xmax   | cmin   | ctid   |
+--------+--------+--------+--------+
  txn that  txn that  cmd id   pointer
  inserted  deleted           to row
```

- When a row is updated: old row gets `xmax = current_txn_id`, new row gets `xmin = current_txn_id`
- A read at snapshot time T sees rows where `xmin <= T` and (`xmax = 0` or `xmax > T`)
- No locks required for reads — readers never block writers, writers never block readers

### Isolation Level Anomaly: Write Skew

```sql
-- Two on-call doctors, constraint: at least one must be on-call
-- Session 1                        Session 2
BEGIN;                              BEGIN;
SELECT COUNT(*) FROM oncall         SELECT COUNT(*) FROM oncall
WHERE on_duty = true;               WHERE on_duty = true;
-- Returns 2                        -- Returns 2 (reads same snapshot)

UPDATE oncall SET on_duty = false   UPDATE oncall SET on_duty = false
WHERE doctor_id = 1;                WHERE doctor_id = 2;

COMMIT;                             COMMIT;
-- Result: 0 doctors on call — constraint violated!
-- Fix: Use SERIALIZABLE isolation or SELECT FOR UPDATE
```

---

## 7. Real-World Examples

- **Banking**: ACID required. PostgreSQL SERIALIZABLE for double-entry bookkeeping ledger. Any weaker isolation risks lost updates.
- **Shopping cart**: Read-your-writes sufficient. Session-pinned to primary replica.
- **Social media likes count**: Eventual consistency acceptable. Counter can lag by seconds — Redis INCR with async sync.
- **DNS**: Eventual consistency. TTL-based propagation across resolvers.
- **etcd (Kubernetes state store)**: Linearizability required. Raft consensus for every write.
- **Cassandra (sensor data)**: BASE/AP. 1000s of writes/second from IoT sensors. Stale reads acceptable.

---

## 8. Tradeoffs

### ACID vs BASE

| Dimension | ACID | BASE |
|-----------|------|------|
| Consistency guarantee | Strong | Eventual |
| Availability | Lower (coordination required) | Higher |
| Latency | Higher (sync replication) | Lower |
| Scalability | Vertical / limited horizontal | Horizontal |
| Use case | Financial, inventory, healthcare | Social, analytics, IoT |
| Examples | PostgreSQL, MySQL, Oracle | Cassandra, DynamoDB, CouchDB |

### CAP: CP vs AP

| Scenario | CP | AP |
|----------|----|----|
| Network partition | Reject requests | Serve stale data |
| Split-brain risk | No (sacrifices availability) | Yes (must reconcile) |
| Best for | Banking, coordination services | Social features, shopping |
| Examples | HBase, etcd, CockroachDB | Cassandra, DynamoDB, Riak |

---

## 9. When to Use / When NOT to Use

**Require ACID/SERIALIZABLE**:
- Financial transactions (payments, ledger, accounting)
- Inventory management where overselling is unacceptable
- Booking systems (seat reservation, appointment scheduling)

**Eventual consistency acceptable**:
- Social media feeds, likes, follower counts
- Metrics, analytics, logging
- Product catalog reads (tolerate 1-2 second lag)
- DNS, CDN caching

**Require linearizability (strongest)**:
- Distributed lock managers
- Leader election
- Configuration stores (etcd, ZooKeeper)
- Any "exactly once" coordination

**Do not use SERIALIZABLE when**:
- Write throughput is the primary concern
- Operations are naturally idempotent
- Business logic can tolerate slight staleness

---

## 10. Common Pitfalls

**Pitfall 1: Confusing ACID Consistency with CAP Consistency**
ACID's C = application invariants (constraints). CAP's C = linearizability (all nodes see same data at same time). They are completely different concepts using the same word.

**Pitfall 2: Assuming READ COMMITTED prevents write skew**
Production incident: An airline overbooking system ran at READ COMMITTED. Two booking transactions both read "1 seat available," both booked it. Fix: SERIALIZABLE isolation or pessimistic locking with `SELECT ... FOR UPDATE`.

**Pitfall 3: Thinking "eventually consistent" is eventually wrong**
Eventual consistency means data converges — it does not mean data can be permanently wrong. In Cassandra's LWW model, the last write (by timestamp) wins. If clocks drift, the "last" write may not be the intended one. Fix: NTP synchronization, or use conditional writes (CAS operations).

**Pitfall 4: Ignoring PACELC**
Teams choosing Cassandra for "high availability" don't realize they also chose high latency over consistency in the PACELC else-branch. For same-datacenter applications, a well-tuned PostgreSQL with connection pooling often has lower latency than Cassandra.

**Pitfall 5: Treating isolation levels as global**
MySQL/InnoDB uses REPEATABLE READ by default. A long-running reporting query takes a snapshot. Updates accumulate as garbage versions. VACUUM cannot reclaim them. Table bloat grows. Fix: Set `idle_in_transaction_session_timeout`, use `pg_stat_activity` to find long transactions.

---

## 11. Technologies & Tools

| Category | Options | Notes |
|----------|---------|-------|
| ACID RDBMS | PostgreSQL, MySQL, Oracle, SQL Server | Full ACID with configurable isolation |
| NewSQL (global ACID) | CockroachDB, Spanner, TiDB | ACID + horizontal scale |
| AP NoSQL | Cassandra, DynamoDB, Riak | Tunable consistency |
| CP NoSQL | HBase, MongoDB (with j:true), etcd | Consistency over availability |
| Coordination | ZooKeeper, etcd | Linearizable KV, leader election |
| Testing isolation | PgTAP, SQL test harnesses | Verify isolation level behavior |

---

## 12. Interview Questions with Answers

**Q: What are the four ACID properties and what failure does each prevent?**
Atomicity prevents partial writes (all-or-nothing). Consistency prevents constraint violations. Isolation prevents concurrency anomalies (dirty reads, lost updates). Durability prevents data loss after commit. Each is implemented by a different mechanism: undo logs for atomicity, constraint checking for consistency, MVCC/locking for isolation, WAL for durability.

**Q: Explain CAP theorem with a concrete production scenario.**
During a network partition in a Cassandra cluster with RF=3, CL=QUORUM requires 2/3 nodes. If the partition splits into [2 nodes] and [1 node], the minority partition cannot form a quorum and must choose: go unavailable (CP behavior) or serve stale reads with CL=ONE (AP behavior). Cassandra defaults to AP — it returns stale data rather than erroring. Zookeeper makes the opposite choice: minority nodes stop serving reads to preserve consistency.

**Q: What is PACELC and why is it a better model than CAP?**
CAP only describes behavior during network partitions. PACELC extends it: when there IS a partition (P), choose A or C; ELSE (no partition), choose between Latency and Consistency. Most production systems face the latency vs consistency tradeoff far more often than actual partitions. PACELC reveals that synchronous replication (low latency sacrifice for consistency) vs asynchronous (low latency, eventual consistency) is an ELC tradeoff.

**Q: What is the difference between READ COMMITTED and REPEATABLE READ isolation levels?**
READ COMMITTED takes a new snapshot before each statement in a transaction — reads see committed data from concurrent transactions. REPEATABLE READ takes a snapshot at transaction start — all reads in the transaction see the same consistent view regardless of concurrent commits. In practice, PostgreSQL REPEATABLE READ implements snapshot isolation; MySQL InnoDB implements it using next-key locks to additionally prevent phantom reads.

**Q: What is write skew and how do you prevent it?**
Write skew occurs when two concurrent transactions read overlapping data and make decisions that together violate a constraint, even though each transaction individually is consistent. Classic example: two doctors both check "at least one on-call" and both go off-call. Prevention options: (1) SERIALIZABLE isolation (PostgreSQL uses Serializable Snapshot Isolation, SSI), (2) explicit locking with SELECT FOR UPDATE, (3) application-level coordination.

**Q: When is eventual consistency acceptable, and when is it dangerous?**
Acceptable when: stale data causes only a degraded user experience (social like counts, feed order, search index lag). Dangerous when: stale data causes financial loss (balance reads), security bypass (permission checks), or inventory oversell (stock reads). The test: ask "what is the worst case if two clients see different values simultaneously?" If the answer involves money or safety, require strong consistency.

**Q: How does MVCC differ from pessimistic locking for concurrency control?**
MVCC maintains multiple row versions — readers see a consistent snapshot without blocking writers; writers create new versions without blocking readers. Pessimistic locking blocks reads when a write lock is held (in 2PL) — readers wait for writers and vice versa. MVCC has lower contention but higher storage cost (dead tuples require VACUUM). Pessimistic locking has higher contention but simpler storage. PostgreSQL and MySQL InnoDB use MVCC.

**Q: What is linearizability and why is it expensive?**
Linearizability guarantees that every operation appears to take effect atomically at some point between its start and completion, and the global order is consistent with real time. It's expensive because: every write must be seen by all nodes before acknowledging, requiring at minimum one round-trip to a majority of nodes (Raft commit). This adds latency proportional to network RTT — typically 1–10ms in a local cluster, 50–200ms across regions.

**Q: Explain read-your-writes consistency and when it breaks.**
Read-your-writes guarantees that after you write, you always see your own write. It breaks with: (1) load balancers routing your read to a replica that hasn't received the write yet (replication lag), (2) session cookie loss (server-side state cleared), (3) switching from session-sticky read routing to round-robin. Fix: route reads for same user session to primary for a short TTL after writes, or use synchronous replication.

**Q: What is the difference between durability and availability?**
Durability: once committed, a write survives any single-node crash (WAL ensures this). Availability: the system can serve requests at any time. They are orthogonal — a system can be durable but unavailable (committed writes survive crash but system is down for recovery), or available but not durable (in-memory store loses data on crash but was always responsive).

**Q: How does the 2PC protocol relate to ACID and what is its failure mode?**
2PC (two-phase commit) implements distributed atomicity. Phase 1: coordinator sends PREPARE to all participants, each votes yes/no. Phase 2: if all voted yes, coordinator sends COMMIT. Failure mode: coordinator crashes after PREPARE but before COMMIT — participants are stuck in uncertain state (holding locks) until coordinator recovers. This blocking window is typically 30 seconds to 5 minutes depending on timeout settings.

**Q: What is the difference between soft state and eventual consistency in BASE?**
Soft state means the state of the system can change over time without any input, due to replication processes converging in the background. Eventual consistency is the guarantee that this convergence will eventually reach the same value across all replicas given no new updates. Soft state describes the mechanism; eventual consistency is the liveness guarantee.

**Q: In PostgreSQL, what is the MVCC visibility rule for a row?**
A row version R is visible to transaction T if: xmin(R) is committed AND xmin(R) < txid_snapshot_min(T's snapshot) AND (xmax(R) = 0 OR xmax(R) is not committed in T's snapshot OR xmax(R) > txid_snapshot_max). In plain terms: the inserting transaction committed before this snapshot, and the deleting transaction (if any) either hasn't committed yet or committed after this snapshot.

**Q: What is causal consistency and how is it stronger than eventual consistency?**
Causal consistency preserves happens-before relationships: if operation A causally preceded operation B (A's result influenced B), then all nodes see A before B. But concurrent operations (no causal link) can be seen in different orders. Stronger than eventual because it prevents read-your-own-writes violations and "going back in time" anomalies. MongoDB sessions provide causal consistency within a session.

**Q: How do you choose between SERIALIZABLE and REPEATABLE READ in production?**
Use REPEATABLE READ (or Snapshot Isolation) for: reporting queries needing a consistent view, typical OLTP workloads without cross-row constraint invariants. Use SERIALIZABLE for: financial transactions, booking systems, any logic like "if count < N then insert." PostgreSQL's SSI (Serializable Snapshot Isolation) adds minimal overhead for non-conflicting transactions; only truly conflicting transactions are aborted and retried.

---

## 13. Best Practices

1. Default to READ COMMITTED for OLTP, upgrade to SERIALIZABLE only where business logic requires it.
2. Set `idle_in_transaction_session_timeout = '30s'` to prevent long-held locks from idle transactions.
3. Monitor `pg_stat_activity` and `pg_locks` for blocked queries in production.
4. Understand your database's actual isolation level — MySQL/InnoDB defaults differ from PostgreSQL.
5. Test write skew scenarios explicitly before going to production for booking or financial systems.
6. For read-your-writes in microservices, use a short-lived primary read window after writes, not permanent primary reads.
7. Design data models with the consistency model in mind — do not use eventual-consistent Cassandra for inventory that cannot oversell.
8. Use `pg_stat_statements` to identify long-running transactions causing MVCC bloat.

---

## 14. Case Study

**Scenario**: An e-commerce platform runs flash sales where a limited-quantity item (100 units) is available. Under load (10,000 concurrent users), items are being oversold. The team is using PostgreSQL with READ COMMITTED.

**Root cause**: Read-modify-write at READ COMMITTED allows lost updates. Two transactions both read `stock = 1`, both decrement to 0, both commit — stock goes to -1.

**Solution applied**:

```sql
-- Broken: read-modify-write at READ COMMITTED
-- Session 1:
BEGIN;
SELECT stock FROM products WHERE id = 42; -- Returns 1
-- (Session 2 also reads stock = 1 and decrements)
UPDATE products SET stock = stock - 1 WHERE id = 42 AND stock > 0;
COMMIT; -- Overwrites Session 2's decrement? No — both succeed, stock = -1

-- Fix 1: Pessimistic lock (SELECT FOR UPDATE)
BEGIN;
SELECT stock FROM products WHERE id = 42 FOR UPDATE; -- Blocks Session 2
UPDATE products SET stock = stock - 1 WHERE id = 42 AND stock > 0;
COMMIT;

-- Fix 2: Optimistic concurrency with row version
BEGIN;
SELECT stock, version FROM products WHERE id = 42;
-- Check stock > 0 in application
UPDATE products SET stock = stock - 1, version = version + 1
WHERE id = 42 AND version = :read_version AND stock > 0;
-- If rows_affected = 0, retry
COMMIT;

-- Fix 3: Atomic update (best for this case)
UPDATE products SET stock = stock - 1
WHERE id = 42 AND stock > 0
RETURNING stock;
-- If returns no row, stock was 0 — handle in application
```

The team chose Fix 3 (atomic update with check) as it requires no explicit transaction management and is the lowest-latency option. They added a database-level CHECK constraint `CHECK (stock >= 0)` as a safety net. Flash sale throughput reached 8,000 TPS on the same hardware after removing unnecessary read queries.
