# NewSQL and Distributed SQL

## 1. Concept Overview

NewSQL and Distributed SQL systems provide horizontally scalable databases that preserve the full ACID guarantees and SQL interface of traditional relational databases. They emerged to solve the conflict between two unsatisfactory choices: PostgreSQL/MySQL (strong consistency, limited scale) and NoSQL systems (horizontal scale, weakened consistency and expressiveness). The defining characteristic is distributed ACID transactions — linearizable reads and serializable writes across multiple nodes without the developer burden of application-level sharding.

Key systems: Google Spanner (2012), CockroachDB (2015), TiDB (2015), YugabyteDB (2018), Amazon Aurora (shared storage), PlanetScale (Vitess-based).

---

## 2. Intuition

A traditional RDBMS is a single brilliant accountant who can't be cloned — they know every transaction perfectly but can only do so much work. NewSQL is a partnership of accountants with a synchronization protocol: they can work in parallel across the globe, yet they all agree on the exact order every transaction happened. The price is that agreeing on order takes time — that time is network latency.

Mental model: NewSQL = distributed Raft consensus + SQL planner + MVCC, stacked on top of a replicated key-value store.

---

## 3. Core Principles

**Consensus-based replication**: Every write goes through a consensus algorithm (Raft or Paxos) so no single node is a single point of failure and every replica agrees on log order.

**Distributed MVCC**: Each transaction gets a globally consistent timestamp. Reads see a snapshot at that timestamp, so readers never block writers.

**Distributed SQL execution**: The SQL layer pushes computation to data nodes (predicate pushdown, aggregation pushdown) to minimize data movement over the network.

**Automatic sharding**: Data is split into ranges or tablets; the database auto-splits hot ranges and migrates them for load balancing. Developers do not manage shards manually.

**External consistency (linearizability)**: Even across nodes and data centers, if transaction T1 commits before T2 starts (in wall-clock time), T2 will always observe T1's writes.

---

## 4. Types / Architectures / Strategies

```
System          | Storage Layer         | Consensus   | SQL Compat     | CAP Position
----------------|----------------------|-------------|----------------|-------------
Google Spanner  | Colossus (proprietary)| Paxos       | Google SQL     | CP (TrueTime)
CockroachDB     | RocksDB + Raft        | Raft        | PostgreSQL     | CP
TiDB            | TiKV (RocksDB+Raft)   | Raft        | MySQL          | CP
YugabyteDB      | DocDB (RocksDB+Raft)  | Raft        | PostgreSQL+YCQL| CP
Amazon Aurora   | Shared log store      | Custom      | MySQL/Postgres  | CP (regional)
PlanetScale     | Vitess + MySQL        | External    | MySQL          | Eventual (by shard)
```

**Aurora vs true distributed SQL**: Aurora uses a shared log-structured storage layer with a single writer and read replicas — it scales reads but not writes across regions the way Spanner does.

---

## 5. Architecture Diagrams

```
CockroachDB Architecture
========================

Client (JDBC/psql)
       |
   [SQL Gateway / Distributed SQL Planner]
       |
   [Transaction Layer]  ←── Heartbeating for long-running txns
       |
   [Distribution Layer] ←── Range descriptor cache, retry logic
       |
   ┌───────────────────────────────────────┐
   │  Raft Groups (one per range = 64MB)   │
   │                                       │
   │  Range A [Raft]   Range B [Raft]      │
   │  Leader  Follower  Leader  Follower   │
   │  Node1   Node2     Node3   Node1      │
   └───────────────────────────────────────┘
       |
   [RocksDB on each node]


Google Spanner TrueTime
=======================

Commit request arrives at leader
          |
   [1] Acquire Paxos write lock
   [2] Generate timestamp T = TT.now().latest  (TrueTime upper bound)
   [3] Wait until TT.now().earliest > T         (commit-wait: ~7ms GPS, ~14ms atomic clock)
   [4] Commit log entry with timestamp T
   [5] Return success to client

TrueTime interval: [earliest, latest]
  GPS satellites  → ~1ms uncertainty
  Atomic clocks   → ~7ms drift between syncs
  Combined        → ε (epsilon) ≈ 1–7ms

External consistency guarantee:
  If T1 commits at real time r1 and T2 starts at real time r2 > r1,
  then timestamp(T2) > timestamp(T1) always.


TiDB Architecture
=================

  MySQL clients
       |
   [TiDB] ──────────────── stateless SQL layer (MySQL wire protocol)
       |
   [Placement Driver (PD)] ── metadata, leader election, scheduling, TSO (Timestamp Oracle)
       |
   ┌──────────┐  ┌──────────┐  ┌──────────┐
   │  TiKV    │  │  TiKV    │  │  TiKV    │
   │ RocksDB  │  │ RocksDB  │  │ RocksDB  │
   │ + Raft   │  │ + Raft   │  │ + Raft   │
   └──────────┘  └──────────┘  └──────────┘
                        |
   ┌──────────┐  ┌──────────┐  (optional HTAP)
   │ TiFlash  │  │ TiFlash  │  ← columnar replica for analytics
   └──────────┘  └──────────┘
```

---

## 6. How It Works — Detailed Mechanics

### TrueTime API (Spanner)

Every Spanner server has GPS receivers and atomic clocks. TrueTime provides `TT.now()` which returns an interval `[earliest, latest]` representing the true current time within bounded uncertainty (epsilon ≈ 1–7ms). Instead of using a centralized timestamp oracle (single point of failure), Spanner uses physical time directly.

**Commit-wait**: After choosing a commit timestamp T, the leader waits until `TT.now().earliest > T` before returning success. This guarantees no future transaction can receive a timestamp ≤ T. The wait duration equals epsilon (the clock uncertainty), not full round-trip time. This is why Spanner achieves external consistency without 2PC coordination across regions.

### CockroachDB Raft Ranges

Data is split into 64MB ranges. Each range is a Raft group with its own leader (on one of the 3+ replicas). The distributed SQL planner decomposes a query into sub-plans targeting the ranges that own the relevant data. Each sub-plan executes as a local RocksDB read/write.

**Transaction heartbeating**: Long-running transactions send periodic heartbeats to their transaction record. If a conflicting transaction detects an expired heartbeat, it can abort the timed-out transaction and proceed. Default heartbeat interval = 2s; default txn expiry = 24h.

**Write intents**: CockroachDB writes "intents" (uncommitted values) directly to the MVCC data. Readers encountering an intent either wait, push the transaction's timestamp, or abort depending on priority.

### Distributed Transaction Latency

```
Scenario                             | Latency
-------------------------------------|------------------
Spanner single-region read           | ~2ms
Spanner single-region write          | ~5ms (commit-wait ε)
Spanner multi-region write           | ~100–200ms (cross-continental Paxos)
CockroachDB single-region write      | ~2–5ms (single Raft RTT)
CockroachDB multi-region write       | ~50–150ms (cross-region Raft)
TiDB single-region write             | ~2–5ms (TiKV Raft + PD TSO)
PostgreSQL (local) write             | ~0.1–1ms (no distributed coordination)
```

**Implication**: For a 10K TPS single-region OLTP application, CockroachDB is competitive with PostgreSQL. For a globally distributed application requiring linearizability across continents, Spanner at ~100ms per cross-region write is still the only production-proven option at Google scale.

### Hotspot Problem with Sequential Primary Keys

In a distributed database, data ranges are assigned to different nodes. If you use `SERIAL`/`AUTO_INCREMENT` as a primary key, all writes go to the node owning the highest key range — a single hotspot node handles 100% of inserts regardless of cluster size.

```sql
-- PROBLEMATIC: All inserts hit the same node (max key range leader)
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,    -- sequential: hotspot!
    user_id BIGINT,
    amount DECIMAL
);

-- FIXED: Random UUID distributes writes uniformly
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT,
    amount DECIMAL
);

-- BETTER: ULID — time-sortable but random enough to spread writes
-- First 10 bits of millisecond timestamp + 80 random bits
-- Example: 01ARZ3NDEKTSV4RRFFQ69G5FAV
-- Sortable by time, but random suffix prevents single-range concentration

-- CockroachDB specific: hash-sharded indexes
CREATE TABLE orders (
    id BIGINT PRIMARY KEY USING HASH WITH (bucket_count = 8),
    user_id BIGINT,
    amount DECIMAL
);
-- CockroachDB splits SERIAL into 8 buckets, each on a different node
```

### TiDB HTAP: Separating OLTP and OLAP

TiKV (row store, Raft-replicated) handles OLTP writes. TiFlash (columnar store, Raft learner) receives the same data asynchronously, replicated directly from TiKV Raft log. TiDB's optimizer detects whether a query is OLTP (index lookup, small result set) or OLAP (full scan, aggregation) and routes to TiKV or TiFlash respectively.

```sql
-- OLTP query: optimizer routes to TiKV row store
SELECT * FROM orders WHERE id = 12345;

-- OLAP query: optimizer routes to TiFlash columnar store
SELECT DATE(created_at), SUM(amount)
FROM orders
WHERE created_at >= '2025-01-01'
GROUP BY 1;

-- Force TiFlash (override optimizer)
SELECT /*+ READ_FROM_STORAGE(tiflash[orders]) */ ...
```

---

## 7. Real-World Examples

**Google (Spanner)**: F1 (Google Ads), Spanner replaced ~1000 MySQL/Vitess shards with a single globally replicated database. Schema changes are online and propagate across datacenters within minutes while maintaining consistency.

**Cockroach Labs**: Multi-region e-commerce with `REGIONAL BY ROW` table locality — each row is pinned to the region closest to the user. Reads from US users hit US nodes, EU users hit EU nodes, while global ACID is maintained.

**PingCAP (TiDB)**: Used by JD.com (largest Chinese e-commerce) for 500B+ rows, 100K+ TPS. TiFlash enables real-time analytics on the same data without ETL to a separate warehouse.

**Shopify (Vitess/PlanetScale)**: Not true distributed SQL, but horizontal MySQL sharding — each shard is strongly consistent but cross-shard transactions require application logic. Illustrates the alternative approach.

---

## 8. Tradeoffs

```
Aspect                 | PostgreSQL + replicas | CockroachDB/TiDB     | Google Spanner
-----------------------|-----------------------|----------------------|------------------
Write throughput       | Vertical limit        | Horizontal            | Horizontal
Cross-region writes    | Not supported         | High latency (~100ms) | High latency (~100ms)
Single-region latency  | ~0.1-1ms              | ~2-5ms               | ~5ms (commit-wait)
ACID guarantees        | Node-local            | Distributed          | Distributed
SQL compatibility      | Full PostgreSQL        | 95% PostgreSQL/MySQL | Subset SQL
Operational complexity | Low                   | Medium               | Low (managed only)
Cost (self-hosted)     | Low                   | Medium               | N/A (cloud only)
Maturity               | Decades               | ~10 years            | ~12 years (internal)
```

---

## 9. When to Use / When NOT to Use

**Use NewSQL when**:
- Data outgrows a single PostgreSQL instance (>10TB active, >50K TPS sustained)
- Application requires global distribution with strong consistency (global user data, financial transactions)
- You want automatic sharding without application-level shard management
- HTAP workload — simultaneous OLTP and real-time analytics on same dataset (TiDB)
- Compliance requires data residency: specific rows in specific regions (CockroachDB REGIONAL BY ROW)

**Do NOT use NewSQL when**:
- Single-region application — PostgreSQL with read replicas is simpler, faster (~5x), and cheaper
- You need full PostgreSQL extension ecosystem (PostGIS, pgvector, TimescaleDB) — limited in distributed SQL
- Write latency below 2ms is required — the distributed coordination floor makes this impossible
- Budget is constrained — distributed SQL licensing/cloud costs are significantly higher
- Team lacks distributed systems expertise for operations and debugging
- Application is read-heavy — PostgreSQL + caching + read replicas handles most read-heavy workloads

---

## 10. Common Pitfalls

**Sequential primary keys causing hotspots**: Every team migrating from MySQL to CockroachDB hits this. Inserts slow to a single-node rate because all writes target the max-key range. Fix: UUID, ULID, or hash-sharded indexes on day one.

**Underestimating cross-region latency**: A team running Spanner multi-region expects it to feel like a local database. Cross-region commits are 100–200ms. A checkout flow with 5 serialized distributed writes becomes a 500ms–1s operation. Fix: batch writes, reduce cross-region transaction frequency with locality tables.

**2PC overhead misconception**: Spanner does NOT use two-phase commit for external consistency — it uses commit-wait on TrueTime. But CockroachDB uses a transaction coordinator pattern that can have similar overhead when transactions span many ranges. Minimize range fan-out per transaction.

**Raft leadership contention**: In CockroachDB, a table's Raft leaders should be co-located with the gateway node handling most writes. If leaders are scattered across regions, every write incurs cross-region Raft replication. Use `ALTER TABLE ... CONFIGURE ZONE USING lease_preferences` to pin leaders.

**Schema changes in Spanner are slow**: Adding a column to a 10B-row Spanner table can take hours because Spanner needs to backfill across all splits and validate schema compatibility. Plan migrations with lead time.

**Not testing at distributed failure scenarios**: Networks partition. A test against a single-node CockroachDB cluster passes; a 3-region deployment with a region partition surfaces transaction aborts that need retry logic. Build retry loops from day one.

---

## 11. Technologies & Tools

| System         | Language | License      | Managed Option          | Key Differentiator           |
|----------------|----------|--------------|-------------------------|------------------------------|
| Google Spanner | Proprietary | Proprietary | Cloud Spanner (GCP)     | TrueTime external consistency |
| CockroachDB    | Go       | BSL/CCL      | CockroachDB Serverless  | PostgreSQL wire protocol      |
| TiDB           | Go/Rust  | Apache 2.0   | TiDB Cloud              | HTAP (TiKV + TiFlash)        |
| YugabyteDB     | C++      | Apache 2.0   | Yugabyte Managed        | PostgreSQL + Cassandra APIs   |
| Amazon Aurora  | C++      | Proprietary  | AWS Aurora              | Shared log, fast failover     |
| PlanetScale    | Go       | Proprietary  | PlanetScale Cloud       | Vitess-based, branch model    |
| Vitess         | Go       | Apache 2.0   | Self-hosted             | MySQL sharding middleware      |

---

## 12. Interview Questions with Answers

**Q: How does Google Spanner achieve external consistency without two-phase commit?**
Spanner uses TrueTime commit-wait instead of 2PC coordination. After choosing a commit timestamp T using `TT.now().latest`, the leader waits until `TT.now().earliest > T` (the commit-wait period, ~1–7ms). This wait ensures no future transaction can receive a timestamp ≤ T, guaranteeing that any transaction starting after this commit will observe it. The key insight is that the wait is bounded by clock uncertainty (epsilon), not by network round trips to remote sites, so external consistency is maintained without cross-datacenter coordination messages.

**Q: What is the TrueTime API and why is it necessary for Spanner?**
TrueTime is Google's globally synchronized clock API that returns a time interval `[earliest, latest]` with bounded uncertainty (epsilon ≈ 1–7ms, maintained by GPS receivers and atomic clocks in each datacenter). It is necessary because distributed databases need globally ordered timestamps without a centralized timestamp oracle (which would be a single point of failure and latency bottleneck). TrueTime lets each Spanner leader independently choose a commit timestamp that is guaranteed to be strictly greater than any timestamp assigned before it, enabling external consistency without coordination.

**Q: What is the hotspot problem with auto-increment keys in CockroachDB and how do you fix it?**
Auto-increment keys are monotonically increasing, so all new rows are written to the range with the highest key values. That range's Raft leader handles 100% of inserts regardless of cluster size — the other nodes sit idle for writes. Fix by using random UUIDs (`gen_random_uuid()`), ULIDs (sortable but random suffix), or CockroachDB's hash-sharded indexes which split a sequential key space across N buckets automatically.

**Q: Explain how CockroachDB's distributed SQL execution works.**
The SQL gateway node decomposes a query into a physical plan targeting specific key ranges. For a join between two tables, the planner estimates whether to shuffle one table to the other (similar to a distributed hash join) or to co-locate the computation where both tables share a Raft leader. Predicates are pushed down so each TiKV/RocksDB node filters data locally before sending results to the coordinator. The gateway assembles partial results into the final output.

**Q: When would you choose CockroachDB over PostgreSQL with read replicas?**
Choose CockroachDB when you need write scalability beyond a single node (typically above 50K TPS sustained or 10TB+ active data), when you need multi-region active-active writes with strong consistency, or when you want automatic re-sharding without downtime. PostgreSQL with read replicas is preferable for single-region deployments (5x lower latency, full extension ecosystem, lower cost, simpler operations) and for read-heavy workloads where replicas absorb load.

**Q: How does TiDB achieve HTAP — serving both OLTP and analytical queries on the same data?**
TiDB stores data in TiKV (row-oriented, RocksDB-backed, Raft-replicated) for OLTP, and asynchronously replicates it to TiFlash (columnar store) via Raft learner replication. The TiFlash replica receives the same Raft log entries as regular TiKV replicas but stores them in columnar format. TiDB's cost-based optimizer detects query type and routes to TiKV (index scans, small point reads) or TiFlash (full scans, aggregations). The replication lag is typically under 100ms, so analytics run on near-real-time data without ETL.

**Q: What is the CAP position of Spanner and CockroachDB, and what does that mean in practice?**
Both are CP systems: they choose consistency over availability during a network partition. In practice, this means: if a region is partitioned, writes to that region's data halt rather than proceeding with potentially conflicting writes that would create divergence. For Spanner, a zone outage causes that zone's data to become unavailable for writes (the Paxos majority is gone). For CockroachDB, a range's Raft group needs a quorum (2 of 3 replicas); if 2 replicas are unreachable, that range's writes block. Applications using these systems need retry logic and circuit breakers for partition scenarios.

**Q: How does Spanner handle schema changes across a globally distributed table?**
Spanner supports online schema changes that apply gradually across all replicas. When you add a column, Spanner marks it as a new schema version and begins backfilling the column's default value across all splits. Old and new schema versions coexist during the migration. Reads and writes continue against the old schema, with the database transparently translating between versions. The migration completes when all splits finish backfilling. This can take hours for billion-row tables and cannot be significantly accelerated.

**Q: What is Raft and how does it differ from Paxos in distributed SQL systems?**
Raft is a consensus algorithm designed to be more understandable than Paxos while providing the same safety guarantees. Raft separates leader election, log replication, and safety into distinct modules. A Raft group has one leader that receives all writes and replicates to followers; a quorum (majority) must acknowledge each log entry before it is committed. Paxos allows more flexible quorum structures but is harder to implement correctly. CockroachDB and TiDB use Raft; Spanner uses Paxos. Both achieve the same correctness guarantees: a committed entry is never lost as long as a quorum of replicas survives.

**Q: What are write intents in CockroachDB and how do they affect read performance?**
Write intents are uncommitted values written directly to the MVCC keyspace by in-progress transactions. They are records of "I plan to set this key to this value but haven't committed yet." When a reader encounters an intent, it must resolve the intent's status: if the transaction has committed, the intent is cleaned up and the value is returned; if aborted, the intent is removed and the previous MVCC version is returned; if still pending, the reader must wait or push the transaction to a higher timestamp (priority-based). High contention scenarios with many simultaneous writers create intent storms that slow reads. Mitigate by keeping transactions short.

**Q: Explain YugabyteDB's dual API approach.**
YugabyteDB exposes two SQL APIs over the same DocDB storage engine: YSQL (PostgreSQL-compatible, port 5433) and YCQL (Cassandra Query Language-compatible, port 9042). DocDB uses RocksDB as the storage layer, with Raft for replication. YSQL targets OLTP use cases requiring joins and transactions; YCQL targets time-series and wide-column access patterns. This lets teams consolidate PostgreSQL and Cassandra workloads on one system. The tradeoff is that neither API is 100% feature-complete with its reference implementation.

**Q: How does Aurora's shared log differ from true distributed SQL?**
Aurora uses a single-writer, shared-log architecture. All writes go through one primary instance (no write scale-out). The primary writes to a distributed log store (6 copies across 3 AZs), and up to 15 read replicas can replay this log for reads. Failover is fast (<30s) because replicas share the log — there is no full replica sync needed. This is not true distributed SQL because writes do not scale horizontally. Aurora Limitless (announced 2023) adds sharding on top of Aurora to support distributed writes, moving toward distributed SQL.

**Q: What retry strategy should applications use with distributed SQL systems?**
Applications must implement exponential backoff with jitter for transaction retries. CockroachDB returns error code `40001` (serialization failure) when a transaction is aborted due to conflict or clock skew; the application must retry the entire transaction from the beginning. Spanner clients (via the client library) handle retries transparently for many cases. TiDB similarly returns retryable errors. Pattern: catch retryable error → wait `min(base * 2^attempt + random_jitter, max_wait)` → retry full transaction. Never retry individual statements; always retry the full transaction unit.

**Q: How does CockroachDB's REGIONAL BY ROW feature work?**
`REGIONAL BY ROW` is a table locality mode where each row has a `crdb_region` column that determines which region owns (has the Raft leader for) that row. Writes to a row are committed by the Raft group local to that row's region, reducing cross-region latency to a single regional RTT. Reads from the same region are fast (local); reads that cross regions incur cross-region latency. Combined with a `home_region` concept per user, applications can pin each user's data to their nearest datacenter for single-digit millisecond read/write latency globally.

**Q: What is PlanetScale's vitess-based approach and how does it differ from Spanner?**
PlanetScale uses Vitess, which is a sharding middleware for MySQL. Each shard is a strongly consistent MySQL instance (with Raft via Orchestrator or external HA). Cross-shard transactions are NOT ACID in the traditional sense — PlanetScale discourages cross-shard foreign keys and joins, pushing developers to design schemas that avoid cross-shard operations. Spanner, by contrast, supports cross-shard ACID transactions natively with external consistency. PlanetScale's tradeoff: better MySQL compatibility and lower cost; Spanner's: true distributed ACID at the cost of flexibility.

---

## 13. Best Practices

- **Use UUIDs or ULIDs as primary keys** from day one to prevent write hotspots on sequential key ranges.
- **Design for locality**: with CockroachDB REGIONAL BY ROW or Spanner's interleaved tables, co-locate related rows in the same region and range.
- **Keep transactions short**: distributed MVCC contention grows with transaction duration. Aim for < 1 second; anything > 10 seconds is a design problem.
- **Test with 3+ nodes from the start**: single-node distributed SQL behaves differently from production multi-node clusters. Failure scenarios (Raft leader unavailability, node crashes) must be tested.
- **Instrument retries**: log every transaction retry with the error code, attempt number, and latency. A spike in retries signals a hot key or a schema design issue.
- **Benchmark with production-realistic concurrency**: distributed SQL overhead is concurrency-dependent. A benchmark at 1 connection proves nothing; test at peak concurrency.
- **Evaluate the extension ecosystem**: if you need PostGIS, pgvector, or TimescaleDB, verify they are available in the distributed SQL system before committing.
- **Understand your TCO**: managed Spanner at Google-scale is ~$0.30/node/hour plus storage. Self-hosted CockroachDB or TiDB requires significant ops investment. Compare against PostgreSQL + read replicas + PgBouncer before deciding.

---

## 14. Case Study

**Scenario**: A global fintech startup processes cross-currency payments for users in North America, Europe, and Asia. The current PostgreSQL single-master setup (us-east-1) shows 150ms average write latency for EU users, 250ms for APAC users, and begins to saturate at 30K TPS during peak hours. The engineering team must evaluate whether to move to CockroachDB with REGIONAL BY ROW.

**Architecture Before**:
```
All regions → us-east-1 PostgreSQL primary (sync writes)
           → us-east-1 read replicas (async reads)
```

**Architecture After (CockroachDB REGIONAL BY ROW)**:
```
NA users → us-east-1 CRDB node  (Raft leader for NA rows)
EU users → eu-west-1 CRDB node  (Raft leader for EU rows)
AP users → ap-east-1 CRDB node  (Raft leader for AP rows)

Each region: 3 nodes (Raft group, tolerates 1 node failure)
Total: 9 nodes
```

**Schema Design**:
```sql
-- Global payments table with row-level locality
CREATE TABLE payments (
    id UUID DEFAULT gen_random_uuid(),
    crdb_region crdb_internal_region NOT NULL,
    payer_id UUID NOT NULL,
    payee_id UUID NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
) LOCALITY REGIONAL BY ROW;

-- Index on payer for regional lookup
CREATE INDEX ON payments (payer_id, created_at DESC) LOCALITY REGIONAL BY ROW;
```

**Key Design Decisions**:
- Domestic payments (payer and payee in same region) commit in ~3ms (single regional Raft round-trip)
- Cross-region payments (EU payer → NA payee) still require multi-region coordination (~100ms); these are batched into async settlement rather than synchronous commitment
- Idempotency keys stored in the payments table prevent duplicate payments on retry
- Circuit breaker wraps the payment API; if a region's CRDB nodes are unreachable (losing quorum), the circuit opens and queues payments locally with eventual settlement

**Results**:
- NA latency: 150ms → 3ms (Raft local to region)
- EU latency: 150ms → 3ms (Raft local to region)
- Write throughput: 30K TPS → 90K TPS (3x, 3 independent regional write paths)
- Operational cost: +40% higher than PostgreSQL (more nodes, CockroachDB licensing)
- Trade-off accepted: cross-region payments (5% of volume) are async, which simplified settlement reconciliation
