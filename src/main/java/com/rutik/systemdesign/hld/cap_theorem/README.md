# CAP Theorem

## 1. Concept Overview

The CAP Theorem, proven by Eric Brewer in 2000 and formally proven by Gilbert and Lynch in 2002, states that a distributed data store can only guarantee two of the following three properties simultaneously:

- **Consistency (C)** — Every read receives the most recent write or an error. All nodes see the same data at the same time.
- **Availability (A)** — Every request receives a response (not an error), though it may not contain the most recent write.
- **Partition Tolerance (P)** — The system continues to operate despite an arbitrary number of messages being dropped or delayed by the network between nodes.

The critical insight that is often misunderstood: **network partitions are not optional**. In any real distributed system, network failures will occur. Therefore, partition tolerance is not a choice — it is a requirement. The real tradeoff is always between Consistency and Availability during a partition.

This means the meaningful question is: when a network partition occurs, do you prioritize returning consistent (possibly stale) data or prioritize staying available (even with potentially inconsistent data)?

Why it matters:
- It directly shapes the architecture of every database, cache, and message queue you build on
- It forces explicit decisions about what failure modes are acceptable for your use case
- It explains why distributed systems behave unexpectedly under failure conditions
- Understanding it prevents building systems that promise guarantees they cannot keep

---

## Intuition

> **One-line analogy**: CAP theorem is like a bank during a communication outage — you can either refuse all transactions until the network recovers (Consistency), or keep processing with possibly stale balances (Availability). You can't do both simultaneously.

**Mental model**: In a distributed system, network partitions (nodes losing connection to each other) are inevitable. When a partition occurs, your only choice is: stop serving requests to guarantee consistency (CA databases during partition), or keep serving requests with potentially stale data (AP databases). Partition tolerance is not optional — so the real choice is always between C and A when a partition happens.

**Why it matters**: CAP theorem explains why no distributed database is perfect. Postgres (CP) gives you consistency but may reject requests during failures. DynamoDB (AP) stays available during failures but may serve stale data. Understanding this tradeoff drives every distributed database choice.

**Key insight**: CAP is often misunderstood as "pick any two." The right framing is: "You must tolerate partitions, so you must decide between consistency and availability during a partition." This is why PACELC (extends CAP with latency/consistency tradeoffs during normal operation) is a more complete model.

---

## 2. Core Principles

**Partition Tolerance is Non-Negotiable**
If you have two nodes and a network between them, partitions will happen. Hardware fails, cables are cut, routers crash, cloud availability zones lose connectivity. A system that stops working entirely on a partition is not partition tolerant — and in practice, stopping entirely is often unacceptable.

**The Partition Decision is Binary Under Active Partition**
During a partition, you must choose: do you return possibly stale data (choose Availability) or return an error/wait until nodes reconcile (choose Consistency)?

**Consistency in CAP is Linearizability**
The "C" in CAP specifically means linearizable consistency (also called atomic consistency) — the strongest consistency model. It is not the "C" in ACID (which means something different). Every read sees the effect of every write that completed before it, globally across all nodes.

**Availability in CAP is Total Availability**
Every non-failing node must respond to queries. A system that returns errors for some nodes during a partition is not "available" in the CAP sense.

**Normal Operation vs Partition**
CAP only describes behavior during a partition. During normal operation (no partition), you can have both consistency and availability. The theorem only constrains you when things go wrong.

---

## 3. Types / Strategies

### CP Systems (Consistency + Partition Tolerance)
During a partition, CP systems refuse to return potentially stale data. They may become unavailable (return errors or block) until consistency can be re-established.

**Examples:** HBase, Zookeeper, etcd, MongoDB (with majority write concern), Redis (single-master mode), CockroachDB

**Pattern:** Uses a consensus protocol (Raft, Paxos, Zab) to ensure all writes are acknowledged by a quorum before being considered committed. Reads that cannot be served from a quorum node return an error.

### AP Systems (Availability + Partition Tolerance)
During a partition, AP systems continue serving requests, potentially returning stale data. They resolve conflicts after the partition heals using reconciliation strategies.

**Examples:** Cassandra, DynamoDB, CouchDB, Riak, DNS, most CDN caches

**Pattern:** Uses eventual consistency. Nodes accept writes independently. After partition heals, nodes reconcile via last-write-wins, vector clocks, CRDTs, or application-level merge logic.

### CA Systems (Consistency + Availability, No Partition Tolerance)
Theoretically possible only in a single-node or fully connected system where partitions are impossible. Traditional RDBMS (Postgres, MySQL) on a single node fits here — but the moment you add replication across network boundaries, you re-enter CAP territory.

**Reality:** CA is not a practical distributed system category. It describes single-node systems.

### Consistency Models (Beyond CAP's Binary)

CAP defines linearizability, but real systems operate on a spectrum:

| Model | Description | Example |
|-------|-------------|---------|
| Linearizable (Strong) | All reads see latest write globally | ZooKeeper, etcd |
| Sequential | All operations appear in some sequential order, consistent per-client | Single-leader DB with sync replication |
| Causal | Causally related operations are seen in order | MongoDB causal sessions |
| Read-your-writes | Client always reads its own writes | Sticky sessions, session tokens |
| Monotonic reads | Once a value is seen, older values are never returned | Read from same replica |
| Eventual | Writes will propagate to all nodes eventually | Cassandra, DNS |

---

## 4. Architecture Diagrams

### CAP Triangle

```
                Consistency
                    /\
                   /  \
                  /    \
                 /  CA  \
                / (single \
               /   node)   \
              /             \
             /_______________\
  CP Systems                  AP Systems
(ZooKeeper,               (Cassandra,
 HBase, etcd)              DynamoDB,
                           CouchDB)
         P (Partition Tolerance)
```

### CP System During Partition (ZooKeeper)

```
Normal Operation:
  Client
    |
    v
  Node A (Leader) <---sync---> Node B (Follower)
                 <---sync---> Node C (Follower)
  All writes go to leader, replicated to quorum

During Partition:
  Client
    |
    v
  Node A (Leader) |X X X| Node B (isolated)
                  |X X X| Node C (isolated)

  Node A: still serves (has quorum with itself if 2/3 quorum met)
  OR
  Client --> Node B: "I cannot reach quorum, returning error"
  (CP: prefers error over stale data)
```

### AP System During Partition (Cassandra)

```
Normal Operation (Replication Factor=3):
  Client WRITE
    |
    v
  Node A <---replication---> Node B
         <---replication---> Node C

During Partition:
  Client WRITE --> Node A  (write succeeds locally)
                           |X X X| Node B (isolated, stale)
                           |X X X| Node C (isolated, stale)

  Client READ --> Node B  (returns stale data -- AP choice)

After Partition Heals:
  Node A, B, C reconcile via hinted handoff + read repair
```

### PACELC Diagram

```
                    Is there a Partition?
                   /                     \
                 YES                      NO
                 /                         \
         P+A or P+C?              Else: Latency or Consistency?
          |        |                    |              |
          v        v                    v              v
         PA      PC                  EL             EC
    (Cassandra) (HBase)         (Cassandra,      (Spanner,
   Available   Consistent       DynamoDB)         MongoDB)
   on partition, error on        Low latency      Strong
   partition    partition        eventual         consistency
                                                  higher latency
```

---

## 5. How It Works — Detailed Mechanics

### Why Partitions Force a Choice

Consider two nodes, A and B, separated by a network partition:
1. Client writes X=1 to Node A. A cannot replicate to B.
2. Client reads X from Node B.

Options:
- **Return stale X=0** (AP: available, inconsistent)
- **Return error or block** (CP: consistent, unavailable)
- **There is no third option** that satisfies both

### Quorum-Based Consistency

Many CP systems use quorum reads and writes. With N replicas:
- Write quorum W: write must be acknowledged by W nodes
- Read quorum R: read must query R nodes

For strong consistency: `R + W > N`

Example (N=3, W=2, R=2):
- Write: node A and B acknowledge → committed
- Read: query nodes A and B → at least one has the latest write
- During partition where A is isolated: write to B+C (quorum), read from B+C (quorum) — consistent
- If A is the only reachable node (W=2 not met): write fails — CP system says no

### Vector Clocks (AP Conflict Resolution)

Used by DynamoDB (original Dynamo paper), Riak. Each value carries a version vector:
- `{ A: 1, B: 0, C: 0 }` — written by node A
- `{ A: 1, B: 1, C: 0 }` — node B modified A's value

When two conflicting versions are detected (neither dominates the other), the system stores both and returns them to the client for application-level resolution. Amazon shopping cart used this: "add to cart" conflicts were resolved by merging (unioning) the two carts.

### Last-Write-Wins (LWW)

Cassandra's default conflict resolution. Each write has a timestamp. On conflict, the write with the higher timestamp wins.

Risk: clock skew between nodes means older writes can overwrite newer ones. Mitigated by NTP and hybrid logical clocks.

### CRDTs (Conflict-free Replicated Data Types)

Data structures that can be merged without conflict resolution logic:
- **G-Counter**: grow-only counter, merge by taking max of each node's count
- **OR-Set**: observed-remove set, supports add and remove without conflict
- **LWW-Register**: last-write-wins register with logical timestamps

Used by Riak, Redis CRDT mode, collaborative editing tools (like Figma's multiplayer).

### Eventual Consistency Mechanisms

**Anti-entropy / Gossip Protocol:** Nodes periodically exchange state and reconcile differences. Cassandra uses gossip for cluster membership and Merkle trees for data repair.

**Hinted Handoff:** If a target node is down, the write is temporarily stored on another node with a "hint." When the target recovers, hints are replayed.

**Read Repair:** When a read detects inconsistency across replicas (stale data on one), it triggers a background write to bring the stale replica up to date.

---

## 6. Real-World Examples

**Apache Cassandra (AP)** — Designed for active-active multi-datacenter deployments. No single point of failure. Tunable consistency: you can request `QUORUM` reads/writes for stronger guarantees at the cost of availability, or `ONE` for maximum availability. Used by Netflix, Instagram, Apple (for iCloud metadata).

**Apache ZooKeeper (CP)** — Distributed coordination service. Used for leader election, distributed locks, configuration management. Uses ZAB (ZooKeeper Atomic Broadcast) protocol — a Paxos variant. During partition where leader cannot reach quorum, ZooKeeper stops serving requests. Used by Hadoop, Kafka (older versions).

**etcd (CP)** — Uses Raft consensus. Backbone of Kubernetes control plane. All writes go through the Raft leader. Reads can be stale (linearizable reads are more expensive). Prefers consistency over availability.

**Amazon DynamoDB (AP)** — Default: eventual consistency with low latency. Optional: strongly consistent reads (read from a quorum, slower and more expensive). Global Tables (multi-region) are AP — eventual consistency across regions.

**MongoDB (tunable, CP by default with majority)** — With `writeConcern: majority` and `readConcern: majority`, provides linearizable reads. Without it, stale secondary reads are possible. Replica sets use Raft-like election. Sharded clusters add complexity.

**Google Spanner (CP + external consistency)** — Uses TrueTime (GPS + atomic clocks) to assign globally consistent timestamps. Provides external consistency (stronger than linearizability) across globally distributed data. CP, low latency because of hardware-assisted time.

**DNS (AP)** — Classic AP system. DNS records are cached globally with TTL. Changes propagate slowly (eventual consistency). During partition, cached (stale) responses are returned. Availability is prioritized over freshness.

---

## 7. Tradeoffs

### Choosing CP
| Gain | Lose |
|------|------|
| Users always see accurate data | System may return errors or block during partitions |
| Easier to reason about correctness | Lower availability (potentially) |
| Simpler application logic (no conflict resolution) | Higher write latency (quorum coordination) |
| Safe for financial transactions, inventory | Not suitable for always-on global systems |

### Choosing AP
| Gain | Lose |
|------|------|
| System stays up during network failures | Users may see stale or conflicting data |
| Low latency writes (no coordination) | Complex conflict resolution logic |
| Scales horizontally with ease | Harder to reason about correctness |
| Good for social feeds, shopping carts | Risk of data anomalies (e.g., overselling inventory) |

---

## 8. When to Use

**CP Systems:**
- Financial transactions (bank balances, payment processing)
- Inventory systems (you cannot oversell the last item)
- Distributed locks and leader election
- Configuration management (Kubernetes control plane)
- Any system where serving stale data has legal/financial consequences

**AP Systems:**
- Social media feeds, likes, follower counts (stale is acceptable)
- Shopping carts (temporary inconsistency is tolerable)
- DNS, CDN content (cached, eventually consistent is fine)
- Global user-facing systems that must remain online during regional outages
- Metrics and analytics ingestion (high write volume, eventual aggregation)
- Recommendation engines (slight staleness has zero user impact)

---

## 9. When NOT to Use

Do not use a pure AP system for:
- Account balances or anything with strong correctness requirements
- Systems where conflicting writes cannot be automatically merged
- Workflows that require strict ordering (e.g., a state machine with invalid transitions)

Do not use a pure CP system for:
- Systems that must serve traffic even when the majority of nodes are unreachable
- Globally distributed systems where cross-region coordination latency is unacceptable
- High-throughput write workloads where quorum acknowledgment adds too much latency

---

## 10. Common Pitfalls

**Confusing CAP Consistency with ACID Consistency**
ACID's "C" means transactions leave the database in a valid state (referential integrity, constraints). CAP's "C" means all nodes see the same data at the same time (linearizability). Different concepts, same letter.

**Thinking CA is a distributed option**
CA systems only exist as single-node systems. As soon as you have replication over a network, you must handle partitions.

**Treating CAP as binary**
Real systems offer tunable consistency. Cassandra lets you choose `ONE`, `QUORUM`, or `ALL` per operation. MongoDB lets you tune write concern. The tradeoff is a dial, not a switch.

**Ignoring the PACELC extension**
CAP only describes partition scenarios. PACELC adds: Even without a Partition, there is a tradeoff between Latency and Consistency. Spanner chooses consistency + higher latency. DynamoDB chooses low latency + eventual consistency under normal operation.

**Assuming eventual consistency "just works"**
Eventual consistency requires careful application design: idempotent writes, conflict resolution strategies, and handling of read-your-own-writes. Ignoring this leads to data corruption bugs that are hard to reproduce.

**Using LWW without understanding clock skew**
Last-write-wins assumes accurate clocks. In distributed systems, clocks drift. NTP corrections can cause time to jump backward. This means a "later" write can have a lower timestamp and be overwritten by an "earlier" write.

---

## 11. Technologies & Tools

| Category | Technologies |
|----------|-------------|
| CP Databases | HBase, ZooKeeper, etcd, CockroachDB, Spanner, FoundationDB |
| AP Databases | Cassandra, DynamoDB, CouchDB, Riak, Voldemort |
| Tunable | MongoDB, Redis (with Sentinel/Cluster), ScyllaDB |
| Consensus Protocols | Raft (etcd, CockroachDB), Paxos (Spanner), ZAB (ZooKeeper) |
| CRDT Libraries | Akka Distributed Data, Riak Data Types, Automerge |
| Distributed Coordination | ZooKeeper, etcd, Consul |

---

## 12. Interview Questions

**Q1: Explain the CAP theorem in plain language.**
In a distributed system, when a network partition occurs, you can either return consistent data (potentially refusing requests) or stay available (potentially returning stale data). You cannot do both.

**Q2: Is Cassandra CP or AP?**
Cassandra is AP by default. It prioritizes availability and partition tolerance, returning potentially stale data during partitions. However, it offers tunable consistency — requesting `QUORUM` reads and writes effectively makes it behave more like a CP system at the cost of availability.

**Q3: What does "eventual consistency" actually mean?**
If no new updates are made to an item, all reads will eventually return the last written value. There is no guarantee on how long "eventually" takes. The system will converge to a consistent state after partitions heal via mechanisms like gossip, read repair, and hinted handoff.

**Q4: Why is partition tolerance non-negotiable?**
Real networks fail. Packets are dropped, cables are cut, availability zones go down. Any distributed system must handle the case where some nodes cannot communicate with others. A system that simply stops working on a partition is not useful in production.

**Q5: What is the PACELC theorem and how does it extend CAP?**
PACELC: if there is a Partition, choose between Availability and Consistency; Else (no partition), choose between Latency and Consistency. It captures the latency-consistency tradeoff that exists in normal operation, which CAP ignores.

**Q6: How does ZooKeeper handle network partitions?**
ZooKeeper uses the ZAB protocol (leader election + atomic broadcast). If the leader cannot reach a quorum of followers, it steps down and a new election starts. During election, ZooKeeper is unavailable. This is a CP choice: it refuses to serve potentially stale data.

**Q7: How do you implement read-your-own-writes in an AP system?**
Options: (1) Route all reads and writes for a user to the same replica (sticky sessions). (2) Track the write timestamp and refuse to serve reads from replicas that haven't caught up. (3) Use a session token that encodes the write timestamp, and the read includes this as a minimum-version requirement.

**Q8: What are vector clocks and when are they used?**
Vector clocks are a versioning mechanism where each update carries a vector of `{nodeId: counter}` pairs. They allow the system to detect whether two writes are causally related or concurrent. Concurrent writes (neither dominates the other) represent a conflict requiring resolution. Used in DynamoDB's original Dynamo design and Riak.

**Q9: How does Google Spanner achieve CP with low latency globally?**
TrueTime: Google uses GPS receivers and atomic clocks in every datacenter to bound global clock uncertainty to milliseconds. Spanner assigns commit timestamps within the uncertainty bound, ensuring global consistency without waiting for arbitrary clock synchronization. This hardware investment eliminates most of the latency penalty of CP systems.

**Q10: What consistency guarantees does DynamoDB provide?**
By default, reads are eventually consistent (may return stale data). Strongly consistent reads are available (cost 2x read capacity units, slightly higher latency). Transactions (TransactGetItems, TransactWriteItems) provide ACID guarantees within a single region.

**Q11: What is the difference between linearizability and serializability?**
Linearizability is a consistency model for individual operations — once an operation completes, its effect is immediately visible to all future operations (single-object, single-operation scope). Serializability is a transaction isolation level — concurrent transactions produce results equivalent to some serial execution (multi-object, multi-operation scope). Strict serializability combines both.

---

## 13. Best Practices

- Explicitly document the consistency model your system provides — never let it be implicit
- Design for the failure case first: what should happen when a partition occurs?
- Use tunable consistency to match the tradeoff to the operation (strong consistency for reads of account balance, eventual for activity feeds)
- Implement idempotent writes in AP systems — replayed writes during reconciliation must be safe
- Monitor replication lag as a leading indicator of consistency issues
- Use CRDTs for data types that need merge semantics (counters, sets, flags)
- Never use wall-clock time as the sole conflict resolution mechanism — add logical clocks
- Test partition scenarios explicitly in staging (use tools like Toxiproxy, Chaos Monkey)
- Keep session affinity for read-your-own-writes guarantees in eventually consistent systems
- Design compensation logic for cases where AP systems allow invalid states (e.g., oversold inventory)

---

## 14. Metrics & Monitoring

| Metric | What It Indicates | Alert Condition |
|--------|-------------------|-----------------|
| Replication Lag | How far behind replicas are | > 5 seconds |
| Consistency Errors | Reads returning stale data (if detectable) | Any non-zero spike |
| Partition Events | Network splits detected by gossip | Any occurrence |
| Quorum Failures | Writes/reads that couldn't meet quorum | > 0.1% of requests |
| Reconciliation Rate | Rate of conflict resolution events | Sustained high rate |
| Hinted Handoff Queue Depth | Backlog of writes waiting to replay | Growing queue |
| Node Availability | % of cluster nodes healthy | < 100% (alert immediately) |
| Read Repair Rate | Background repairs triggered | High rate = high inconsistency |

---

## Cross-Perspective: LLD Connections

**LLD View — Design Patterns That Implement CAP Tradeoffs**

- **Strategy** — Consistency level is a Strategy: eventual, strong, and linearizable consistency are interchangeable behaviors. The datastore holds a `ConsistencyStrategy` reference that can be configured per-operation or per-client.
- **State** — A cluster node transitions through states (Leader → Follower → Candidate in Raft; Normal → Partitioned → Recovering). Each state handles events (heartbeat, vote request, election timeout) differently — a classic State machine where the state drives the response.
- **Observer** — Leader election events and partition detection events are broadcast to Observer subscribers (replication managers, metrics collectors, failover handlers) without tight coupling between the cluster coordinator and its dependents.

---

## 15. Case Study: Designing a Global Shopping Cart

**Problem:** Build a shopping cart system for a global e-commerce platform. The system must handle 100,000 concurrent users across 5 continents. Cart data must never be lost, but brief inconsistency is acceptable.

**CAP Analysis:**
- Partition tolerance: Required (global multi-region deployment guarantees partitions)
- Consistency: "Cart must not be lost" suggests we want to prevent data loss, not necessarily strong consistency
- Availability: Cart operations must work even during regional outages

**Decision: AP with conflict resolution**

**Architecture:**
- Use DynamoDB Global Tables (AP, multi-region replication)
- Cart is keyed by `user_id`
- Each cart item is an OR-Set CRDT: adds and removes are tracked with unique tags

**Conflict Resolution:**
- Two regions accept concurrent writes (user adds item in US, also adds item in EU — both writes succeed)
- On reconciliation, merge by union of all items
- Removes are tracked with "remove wins" semantics: if any region removed an item, it stays removed

**Consistency Guarantees Provided:**
- Read-your-own-writes: Achieved by routing reads to the same region as writes during a session
- Eventual consistency: Both regional writes propagate within seconds after partition heals
- No data loss: AP system writes succeed locally even during partition

**What We Accept:**
- A user checking out during a partition might have a cart that doesn't include items added on another device in the last few seconds — we handle this by requiring a final cart review step before payment
- The payment step uses a CP service (inventory lock + payment processing) — the AP cart feeds into a CP checkout

**Outcome:** Cart is always available, never loses data, eventual consistency is acceptable for the browsing/adding flow. The CP boundary is drawn at checkout, where consistency is critical.
