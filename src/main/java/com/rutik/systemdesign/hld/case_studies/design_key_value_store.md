# System Design: Distributed Key-Value Store

## Intuition

> **Design intuition**: A distributed key-value store (Dynamo, Cassandra, Riak, Voldemort) is what you get when you take a single-node hash map — `put(key, value)`, `get(key)`, `delete(key)` — and ask "what happens when the machine holding this hash map dies?" The answer is **replication**, and replication immediately collides with the **CAP theorem**: if a network partition separates a client from two of a key's three replicas, do you (a) refuse the write/read until the partition heals (favoring Consistency), or (b) serve it from whatever replica is reachable and reconcile later (favoring Availability)? The Dynamo-lineage systems covered here choose **(b)** — they are **AP systems** — and the entire design is the set of mechanisms needed to make "write now, reconcile later" safe, fast, and operationally tractable at a scale of hundreds of nodes and hundreds of terabytes.

**Key insight**: Almost every component in this design exists to answer one of two questions: **"which nodes own this key?"** (consistent hashing with virtual nodes, §4.1) and **"what do we do when those nodes disagree about the value?"** (quorums, vector clocks, read repair, anti-entropy, hinted handoff — §4.2-§4.5). A single-node key-value store is just a hash map; a *distributed* one is a hash map plus a conflict-resolution protocol, and the quality of that protocol — not the hash map — is what separates a toy implementation from DynamoDB or Cassandra.

---

## 1. Requirements Clarification

### Functional Requirements

- **`put(key, value)`**: store a value under a key, replicated to `N` nodes for durability
- **`get(key)`**: retrieve the value(s) for a key, with the caller able to tune how many replicas must agree before a result is returned
- **`delete(key)`**: remove a key — implemented as a **tombstone** write (a special marker value) rather than an immediate physical delete, so the deletion itself can be replicated and reconciled like any other write
- **Configurable replication factor `N`**: every key is stored on `N` nodes (typically `N = 3`), chosen so the system tolerates `N - 1` simultaneous node failures without losing the key entirely
- **Tunable consistency via quorums**: callers choose a write quorum `W` (replicas that must acknowledge a `put` before it returns success) and a read quorum `R` (replicas that must respond to a `get` before it returns a result), trading latency and availability against consistency on a per-request basis
- **Versioning / conflict detection**: when concurrent writes to the same key produce divergent replica states, the system must detect the divergence (via vector clocks or timestamps) and either resolve it automatically (last-write-wins) or surface multiple versions to the application

### Non-Functional Requirements

- **High availability over strict consistency (AP per CAP)**: a `put` or `get` should succeed even if some replicas are unreachable, as long as enough replicas (per the tunable quorum) respond — the system never returns a hard error just because the cluster is "partially down"
- **Horizontal scalability**: the cluster grows by adding commodity nodes — from a handful of nodes to **hundreds of nodes** and **hundreds of terabytes** of data — without a coordinated rebalance of the entire keyspace
- **Low, predictable latency**: single-digit-millisecond p99 for both `get` and `put` under normal conditions, because this store sits in the hot path of other services (session stores, shopping carts, product catalogs)
- **No single point of failure**: no node — including any "master" or "coordinator" — is special; any node can serve as the entry point (coordinator) for any request
- **Self-healing**: temporary node failures, network partitions, and divergent replicas are detected and repaired automatically (hinted handoff, anti-entropy) without operator intervention for the common case

### Out of Scope

- **Secondary indexes and joins** — this is a pure key-value interface; querying "all users where `age > 30`" is not supported natively (Cassandra's wide-column model and secondary indexes are a related but distinct extension, cross-referenced in §7)
- **ACID multi-key transactions** — a `put` affects exactly one key's replica set; there is no cross-key atomicity or isolation. Systems needing multi-key transactions either layer an application-level saga on top (cross-ref [`../distributed_transactions/README.md`](../distributed_transactions/README.md)) or choose a CP system with a transaction manager (e.g., Spanner, CockroachDB) instead
- **Strong global ordering / linearizability** — by design (AP), this system does not guarantee that all clients see writes in the same order. CP alternatives (HBase/Bigtable-style, §5) are discussed for contrast but not designed in depth here

### Framing the Tradeoff: PACELC, Not Just CAP

CAP describes behavior **during a partition** (P): choose Availability or Consistency. But partitions are rare; the more common question is what happens **Else** — in normal operation, with no partition — and that's where **PACELC** (cross-ref [`../cap_theorem/README.md`](../cap_theorem/README.md)) adds the missing half of the picture: even with no partition, there is a tradeoff between **Latency** and **Consistency** (L vs. C). This design is **PA/EL**: during a Partition, choose Availability (sloppy quorums, §4.2); Else (normal operation), choose Latency over strict Consistency (asynchronous replication beyond the write quorum, tunable `R` < `N` reads, §4.2 and §4.8). The CP alternative (§5) is typically **PC/EC**: during a partition, choose Consistency (the minority side becomes unavailable); else, still choose Consistency over latency (reads and writes route through a leader/coordinator even when there's no partition at all). Framing the choice this way in an interview signals that the AP-vs-CP decision isn't a one-time partition-handling detail — it's a *pervasive*, every-single-request tradeoff that shapes the system's everyday latency profile, not just its behavior during rare partition events.

---

## 2. Scale Estimation

### Traffic Volume

- Target cluster-wide throughput: **10,000,000 reads/sec** and **1,000,000 writes/sec** (a roughly 10:1 read/write ratio, typical for session stores, product catalogs, and feature-flag stores backing this kind of system)
- Average value size: **1-10 KB** (session blobs, user profile fragments, product attributes) — use **5 KB** as the working average
- Write bandwidth: `1,000,000 writes/sec * 5 KB` = **5 GB/sec** of incoming write payload before replication
- Read bandwidth: `10,000,000 reads/sec * 5 KB` = **50 GB/sec** of outgoing read payload (note: with `R`-way fan-out per read, the *coordinator-to-replica* internal traffic is `R x` this, but client-facing egress is the number above)

### Storage Volume

- Total logical dataset: **~100 TB** (the actual unique key-value data, before replication)
- With replication factor `N = 3`: raw storage = `100 TB * 3` = **~300 TB**
- At an average **node capacity of ~1-2 TB of usable SSD** (leaving headroom for compaction overhead on an LSM-tree storage engine, §4.6), a cluster needs roughly `300 TB / 1 TB` = **~300 nodes** for storage alone — §10 reconciles this with the throughput-driven node count

### Per-Node Load (Sanity Check)

- With `N = 3` and a write quorum `W = 2`, every write is sent to all 3 replicas (and acknowledged by at least 2): `1,000,000 writes/sec * 3` = **3,000,000 replica-writes/sec** cluster-wide
- Spread across **~300-500 nodes** (§10's final sizing), that is **6,000-10,000 replica-writes/sec per node** — well within the range of a properly tuned LSM-tree storage engine (§4.6), which can sustain tens of thousands of writes/sec per node thanks to sequential write batching

### Object Count

- At 5 KB average value size and 100 TB logical data: `100 TB / 5 KB` ~= **20 billion keys**
- Spread across **128-256 virtual nodes per physical node** (§4.1) on a 300-500 node cluster, that's tens of thousands of virtual node "token ranges," each holding tens of millions of keys — small enough that anti-entropy (§4.4) over any single range is tractable

### Hot-Key Skew

The averages above assume a roughly uniform key distribution, but real workloads follow a **power-law / Zipfian** distribution — a small fraction of keys (a viral product, a celebrity's profile, a popular feature flag) receive a disproportionate share of traffic. If the top 0.01% of keys (roughly 2 million keys, at 20 billion total) account for 10% of read traffic — `10,000,000 reads/sec x 0.10` = **1,000,000 reads/sec** — that's `1,000,000 / 2,000,000` = **0.5 reads/sec/key** on average for that "hot" set, but the single hottest key within it can dominate further. Because consistent hashing (§4.1) maps a single key to a *fixed* set of `N` replicas regardless of that key's traffic, a sufficiently hot key can overwhelm its `N` replicas even while the cluster overall is far from saturated — this is the well-known **hot-partition problem**, and it's why production deployments pair this design with an additional caching tier (cross-ref [`../caching/README.md`](../caching/README.md)) in front of the cluster for read-heavy hot keys, rather than relying on the KV store's own replication factor to absorb arbitrary skew.

---

## 3. High-Level Architecture

```
                          +---------------------+
                          |       Clients        |
                          | (app servers, using  |
                          |  a smart client lib)  |
                          +-----------+-----------+
                                       |
                                       v
                          +---------------------+
                          |  Coordinator Node     |   <- any node can act as
                          |  (request entry point) |      coordinator; no
                          +-----------+-----------+      special "master"
                                       |
                       hash(key) -> position on ring
                                       |
                                       v
        +------------------------------------------------------------+
        |                    Consistent Hash Ring                      |
        |               (with virtual nodes, §4.1)                     |
        |                                                                |
        |     ...--[vnode A7]--[vnode B2]--[vnode C9]--[vnode A3]--...   |
        |                          |            |            |          |
        |                          v            v            v          |
        |                     +---------+  +---------+  +---------+     |
        |                     | Node B   |  | Node C   |  | Node A   |     |
        |                     | Replica 1|  | Replica 2|  | Replica 3|     |
        |                     +---------+  +---------+  +---------+     |
        |                     (3 replicas for key "user:42", N=3)        |
        +------------------------------------------------------------+
                                       ^
                                       |
                          +------------+------------+
                          |  Gossip Protocol         |
                          |  (membership + failure   |
                          |   detection, §4.5)        |
                          +--------------------------+

Quorum read/write path (N=3, W=2, R=2):
  put(key,val):  coordinator -> [B, C, A]  wait for >=2 ACKs -> return success
  get(key):      coordinator -> [B, C, A]  wait for >=2 responses -> reconcile -> return
```

### Request Flow

1. **Client request arrives** at any node in the cluster (the **coordinator** for this request) — there is no dedicated routing tier or master; a client library typically caches the ring topology and routes directly to a node likely to own the key, but any node can forward correctly if the client's view is stale.
2. **Key-to-replica mapping**: the coordinator computes `hash(key)` and walks the consistent-hash ring (§4.1) clockwise to find the first `N` distinct physical nodes — these are the key's **replica set** (e.g., `[B, C, A]` for `N = 3` in the diagram above).
3. **Write path (`put`)**: the coordinator sends the write to all `N` replicas in parallel (each tagged with a vector clock, §4.3) and waits for `W` acknowledgments before returning success to the client. If fewer than `N` replicas are reachable, **sloppy quorum + hinted handoff** (§4.5) lets the write succeed by temporarily storing it on a non-owning node.
4. **Read path (`get`)**: the coordinator sends the read to all `N` replicas (or at least enough to satisfy `R`) and waits for `R` responses. If the responses disagree, the coordinator reconciles them (vector clocks or last-write-wins, §4.3), returns the reconciled value to the client, and triggers **read repair** (§4.4) to push the reconciled value back to the stale replicas.
5. **Membership and failure detection**: nodes continuously exchange state via **gossip** (§4.5) — every node eventually learns which other nodes are up, down, or newly joined, without any centralized coordinator or external service (no ZooKeeper-equivalent in the AP design, contrast with §5's CP alternative).
6. **Background anti-entropy**: independent of any client request, nodes periodically compare **Merkle trees** (§4.4) of the data they hold for overlapping key ranges with their replica peers, detect divergence, and stream the missing/differing data — this is how the system heals from divergence that read repair alone wouldn't catch (keys that are written but never read again).

### The "Coordinator" Is a Role, Not a Server

A subtlety worth emphasizing: **"coordinator" describes a per-request role that any node can play**, not a distinguished server type. The node a client happens to connect to (often chosen by the client library based on its cached ring topology, or simply round-robin/least-loaded) becomes the coordinator for that one request — computing the replica set, fanning out, and aggregating responses — and a different request seconds later might be coordinated by a completely different node, including one of the very replicas it's coordinating for. This statelessness is what makes "no single point of failure" true at the request-routing layer: there is no load balancer or routing tier whose failure takes down request handling, because *every* node can serve as the entry point. The only state that matters for correctness is the **ring topology** (§4.1, propagated via gossip, §4.5) and each node's local data — both of which are replicated/distributed by construction.

---

## 4. Component Deep Dives

### 4.1 Consistent Hashing Ring with Virtual Nodes

The foundational question every request answers first: **given `hash(key)`, which physical nodes hold this key's `N` replicas?** A naive `hash(key) % numNodes` scheme remaps almost every key whenever a node is added or removed — a catastrophic amount of data movement at the 100 TB scale from §2. **Consistent hashing** fixes this: both nodes and keys are hashed onto a fixed circular keyspace (a "ring," typically `0` to `2^64 - 1`), and a key belongs to the first node found walking clockwise from the key's hash position. Adding or removing one node only affects the keys between that node and its predecessor on the ring — roughly `1/numNodes` of the keyspace, not all of it.

**Virtual nodes (vnodes)** solve a second problem: with only one ring-position per physical node, the *size* of the keyspace segment each node owns depends entirely on the random gap between adjacent hash positions — some nodes end up owning much larger segments than others purely by chance, and this gets worse on a heterogeneous cluster where some machines have more capacity than others. By giving each physical node **128-256 virtual positions** scattered around the ring, the law of large numbers smooths out the per-node share to within a few percent of the ideal `1/numNodes`, and a node with double the capacity can simply be assigned twice as many vnodes.

```java
package com.rutik.systemdesign.hld.case_studies.kvstore;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.security.MessageDigest;
import java.nio.ByteBuffer;

/**
 * Consistent hash ring with virtual nodes. Each physical node is mapped to
 * {@code vnodesPerNode} positions on a 64-bit ring. getNodesForKey walks the
 * ring clockwise from hash(key) and collects the first N *distinct physical
 * nodes* it encounters (skipping repeated vnodes of a node already chosen).
 */
public class ConsistentHashRing {

    private final int vnodesPerNode;
    // ring position (unsigned 64-bit, stored as signed long) -> physical node id
    private final NavigableMap<Long, String> ring = new TreeMap<>();

    public ConsistentHashRing(int vnodesPerNode) {
        this.vnodesPerNode = vnodesPerNode;
    }

    /** Adds a physical node, scattering its virtual nodes across the ring. */
    public void addNode(String nodeId) {
        for (int v = 0; v < vnodesPerNode; v++) {
            long position = hash(nodeId + "#vnode" + v);
            ring.put(position, nodeId);
        }
    }

    /** Removes a physical node and all of its virtual node positions. */
    public void removeNode(String nodeId) {
        ring.entrySet().removeIf(e -> e.getValue().equals(nodeId));
    }

    /**
     * Returns the N distinct physical nodes responsible for replicating
     * {@code key}, in ring order starting from hash(key)'s position.
     * The first node returned is the "primary" / coordinator-preferred
     * replica; the rest are the additional N-1 replicas.
     */
    public List<String> getNodesForKey(String key, int replicationFactor) {
        if (ring.isEmpty()) {
            return List.of();
        }
        long keyHash = hash(key);
        Set<String> distinctNodes = new LinkedHashSet<>();

        // Start at the first vnode at or after keyHash; wrap around the ring.
        NavigableMap<Long, String> tail = ring.tailMap(keyHash, true);
        for (String nodeId : tail.values()) {
            distinctNodes.add(nodeId);
            if (distinctNodes.size() == replicationFactor) {
                break;
            }
        }
        if (distinctNodes.size() < replicationFactor) {
            for (String nodeId : ring.values()) { // wrap to the start of the ring
                distinctNodes.add(nodeId);
                if (distinctNodes.size() == replicationFactor) {
                    break;
                }
            }
        }
        return new ArrayList<>(distinctNodes);
    }

    /** Returns the fraction of the ring (0.0-1.0) each physical node currently owns. */
    public double ownedFraction(String nodeId) {
        long total = 0;
        long owned = 0;
        Long prevKey = ring.lastKey();
        for (var entry : ring.entrySet()) {
            long span = entry.getKey() - prevKey; // wrap-around handled by unsigned arithmetic in practice
            if (span < 0) {
                span += Long.MAX_VALUE; // simplified; production code uses unsigned long math
            }
            total += span;
            if (entry.getValue().equals(nodeId)) {
                owned += span;
            }
            prevKey = entry.getKey();
        }
        return total == 0 ? 0.0 : (double) owned / total;
    }

    private long hash(String input) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(input.getBytes("UTF-8"));
            return ByteBuffer.wrap(digest).getLong(); // first 8 bytes as a long
        } catch (Exception e) {
            throw new IllegalStateException("hash failure", e);
        }
    }
}
```

With **256 vnodes per node** on a 300-node cluster, each physical node owns roughly `1 / 300` ~= 0.33% of the ring, but spread across 256 small, non-contiguous segments rather than one large one. This matters for two reasons covered later: (1) **rebalancing smoothness** — adding one new node redistributes 256 small segments taken from many different existing nodes, rather than one large segment taken from a single neighbor (§10), and (2) **anti-entropy parallelism** — each vnode's Merkle tree (§4.4) covers a small, bounded key range, so repair work for one node is naturally chunked into hundreds of independent, parallelizable units.

**Concretely, for the §2 dataset**: ~20 billion keys spread across `500 nodes x 256 vnodes` = 128,000 vnode token ranges means each range holds, on average, `20,000,000,000 / 128,000` ~= **~156,000 keys** (at 5 KB average, ~780 MB per range). A range of this size is small enough that a Merkle tree (§4.4) over it has a modest number of leaves, a full streaming transfer of one range completes in seconds even at modest throughput, and 128,000 independent ranges give the cluster enormous parallelism headroom for both rebalancing (§10) and anti-entropy (§4.4, §8) — no single "unit of work" is ever large enough to dominate a node's resources for an extended period.

### 4.2 Quorum Reads and Writes — the W + R > N Rule

Once the replica set `[A, B, C]` for a key is known (§4.1), the coordinator must decide how many of those replicas must participate in a `put` (`W`) and a `get` (`R`) before responding to the client. The famous **`W + R > N`** rule guarantees that every read quorum and every write quorum **overlap in at least one replica** — meaning any `get` is guaranteed to see at least one replica that participated in the most recent successful `put`. With `N = 3`, the common choices are:

| W | R | W + R | Guarantee | Latency Profile |
|---|---|---|---|---|
| 1 | 1 | 2 (< N) | Fast, but reads can miss the latest write entirely | Lowest latency, weakest consistency |
| 2 | 2 | 4 (> N) | Read-your-writes-ish: every read overlaps every write | Balanced — the most common production default |
| 3 | 1 | 4 (> N) | Writes are durable on all replicas before ack; reads are fast | Slow writes, fast reads |
| 1 | 3 | 4 (> N) | Writes return immediately; reads always see all replicas | Fast writes, slow/less-available reads |
| 3 | 3 | 6 (> N) | Strongest — every replica must respond to both | Highest latency, lowest availability (any single replica down blocks everything) |

Note that `W + R > N` guarantees **overlap**, not **linearizability** — two concurrent reads can still observe different "latest" values if they hit different overlapping replicas during a window where writes are still propagating (this is the eventual-consistency tradeoff explored in §11).

**A concrete walkthrough with `N=3, W=2, R=2`**: a `put("session:abc", v1)` is acknowledged once 2 of `{A, B, C}` — say `A` and `B` — have durably written `v1` (the write to `C` is still in flight or, if `C` was unreachable, queued as a hint, §4.5). A `get("session:abc")` immediately afterward queries `{A, B, C}` and returns once 2 respond. Because `W=2` and `R=2` out of `N=3`, **any** 2-out-of-3 read quorum must include at least one of `{A, B}` — the two nodes that have `v1` — so the read is guaranteed to see `v1` (or something newer). If instead `W=1` (only `A` acknowledged) and `R=1`, a `get` that happens to query only `C` would return the *old* value `v0`, because `C` hasn't received `v1` yet and `R=1` doesn't force the read to "try again" with a different replica. This is the mechanical reason `W+R>N` is the rule of thumb, not `W+R>=N` or some other variant — strict inequality is what forces every possible read-quorum/write-quorum pair to share a member.

```java
package com.rutik.systemdesign.hld.case_studies.kvstore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Fans a put/get out to a key's N replicas and returns as soon as W (for
 * writes) or R (for reads) responses are collected, or fails the operation
 * if that threshold can't be met within the timeout.
 */
public class QuorumCoordinator {

    private final int replicationFactor; // N
    private final int writeQuorum;       // W
    private final int readQuorum;        // R
    private final long timeoutMillis;
    private final ExecutorService executor;
    private final ReplicaClient replicaClient; // RPC stub to a replica node

    public QuorumCoordinator(int replicationFactor, int writeQuorum, int readQuorum,
                              long timeoutMillis, ExecutorService executor,
                              ReplicaClient replicaClient) {
        if (writeQuorum + readQuorum <= replicationFactor) {
            throw new IllegalArgumentException("W + R must be > N for read-your-writes guarantees");
        }
        this.replicationFactor = replicationFactor;
        this.writeQuorum = writeQuorum;
        this.readQuorum = readQuorum;
        this.timeoutMillis = timeoutMillis;
        this.executor = executor;
        this.replicaClient = replicaClient;
    }

    /** Writes {@code value} to all N replicas; succeeds once W ACKs arrive. */
    public WriteResult put(String key, byte[] value, VectorClock clock, List<String> replicas) {
        return fanOut(replicas, writeQuorum,
            replica -> replicaClient.putOnReplica(replica, key, value, clock),
            "put");
    }

    /** Reads from all N replicas; succeeds once R responses arrive, then reconciles. */
    public ReadResult get(String key, List<String> replicas) {
        WriteResult fanout = fanOut(replicas, readQuorum,
            replica -> replicaClient.getFromReplica(replica, key).asWriteResult(),
            "get");
        if (!fanout.success()) {
            return ReadResult.failure(fanout.acks(), readQuorum);
        }
        List<VersionedValue> versions = fanout.collectedValues();
        return ReadResult.success(versions); // reconciliation happens one layer up (§4.3)
    }

    /**
     * Generic fan-out: submits one task per replica, returns success once
     * {@code quorum} tasks complete, but still lets the remaining tasks run
     * to completion in the background (for read-repair / hinted handoff bookkeeping).
     */
    private WriteResult fanOut(List<String> replicas, int quorum,
                                Function<String, ReplicaResponse> op, String opName) {
        CompletionService<ReplicaResponse> ecs = new ExecutorCompletionService<>(executor);
        for (String replica : replicas) {
            ecs.submit(() -> op.apply(replica));
        }

        List<ReplicaResponse> acks = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMillis;
        for (int i = 0; i < replicas.size(); i++) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            try {
                Future<ReplicaResponse> future = ecs.poll(remaining, TimeUnit.MILLISECONDS);
                if (future == null) {
                    break; // timed out waiting for the next response
                }
                ReplicaResponse response = future.get();
                if (response.success()) {
                    acks.add(response);
                }
                if (acks.size() >= quorum) {
                    // Quorum met for THIS operation. We return now, but the
                    // remaining in-flight futures continue on the executor —
                    // their results feed read repair (§4.4) and hinted
                    // handoff (§4.5) bookkeeping rather than being discarded.
                    return WriteResult.success(acks, replicationFactor);
                }
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return WriteResult.failure(acks, quorum); // fewer than `quorum` replicas responded in time
    }

    public record ReplicaResponse(boolean success, VersionedValue value) {
        public WriteResult asWriteResult() {
            return success ? WriteResult.success(List.of(this), 1) : WriteResult.failure(List.of(), 1);
        }
    }
    public record WriteResult(boolean success, List<ReplicaResponse> acks, int required) {
        public static WriteResult success(List<ReplicaResponse> acks, int required) {
            return new WriteResult(true, acks, required);
        }
        public static WriteResult failure(List<ReplicaResponse> acks, int required) {
            return new WriteResult(false, acks, required);
        }
        public List<VersionedValue> collectedValues() {
            return acks.stream().map(ReplicaResponse::value).toList();
        }
    }
    public record ReadResult(boolean success, List<VersionedValue> versions, int required) {
        public static ReadResult success(List<VersionedValue> versions) {
            return new ReadResult(true, versions, versions.size());
        }
        public static ReadResult failure(List<VersionedValue> versions, int required) {
            return new ReadResult(false, versions, required);
        }
    }

    /** Minimal RPC abstraction — implementations talk to actual replica processes. */
    public interface ReplicaClient {
        ReplicaResponse putOnReplica(String nodeId, String key, byte[] value, VectorClock clock);
        ReplicaResponse getFromReplica(String nodeId, String key);
    }
}
```

**Sloppy quorum vs. strict quorum**: the `fanOut` logic above assumes all `N` replicas from `getNodesForKey` are healthy and reachable. A **strict quorum** refuses the operation if fewer than `N` of the *designated* replicas are reachable, even if `W` or `R` of them respond — this maximizes consistency (every successful write touches only the "correct" owners) at the cost of availability during a partition. A **sloppy quorum** instead walks *past* unreachable designated replicas on the ring and uses the next healthy node(s) as substitutes, paired with **hinted handoff** (§4.5) so the substitute later forwards the data to the rightful owner once it recovers. Dynamo-lineage systems default to sloppy quorums — availability wins — which is precisely why `W + R > N` is a *consistency* guarantee only when no sloppy substitution occurred for that particular write (§11 covers this gotcha in depth).

### 4.3 Conflict Resolution — Vector Clocks vs. Last-Write-Wins

When sloppy quorums and partitions are in play, two different coordinators can both accept a `put` for the same key, each believing it has a valid write quorum, while neither is aware of the other's write. The result is **concurrent versions** of the same key sitting on different replicas. The system needs a way to (a) detect that two versions are concurrent (neither "happened after" the other) versus one simply being stale, and (b) decide what the reconciled value should be.

**Last-Write-Wins (LWW)** is the simplest approach: tag every write with a timestamp, and when replicas disagree, the value with the higher timestamp wins; the other is discarded. LWW is cheap (a single `long`) and requires no merge logic, but it has a sharp failure mode: it silently **drops data** whenever two writes are truly concurrent (neither one "knew about" the other) — there is no way to tell "concurrent" apart from "sequential," so a node with a fast clock can make an objectively older write look newer (War Story 2, §9).

**Vector clocks** solve this by tracking, per key, a map of `{nodeId: counter}` — every time a node writes a value, it increments *its own* counter in the vector clock attached to that value. Comparing two vector clocks `(A, B)` can yield one of three outcomes: `A` **dominates** `B` (every entry in `A` is `>=` the corresponding entry in `B`, and at least one is strictly greater — `B` is stale and can be discarded), `B` dominates `A` (symmetric), or `A` and `B` are **concurrent** (neither dominates — both must be kept as **siblings** until the application or a subsequent write resolves them).

```java
package com.rutik.systemdesign.hld.case_studies.kvstore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A vector clock: a per-key map of nodeId -> monotonically increasing counter.
 * Used to detect whether one version of a value causally dominates, is
 * dominated by, or is concurrent with another version.
 */
public final class VectorClock {

    public enum Comparison { BEFORE, AFTER, EQUAL, CONCURRENT }

    private final Map<String, Long> counters;

    public VectorClock() {
        this.counters = new HashMap<>();
    }

    private VectorClock(Map<String, Long> counters) {
        this.counters = counters;
    }

    /** Returns a new clock with this node's counter incremented by 1. */
    public VectorClock increment(String nodeId) {
        Map<String, Long> next = new HashMap<>(counters);
        next.merge(nodeId, 1L, Long::sum);
        return new VectorClock(next);
    }

    /**
     * Compares this clock to {@code other}.
     * - BEFORE: every entry in this <= other, and at least one strictly less
     *           (this version is causally older -> can be discarded)
     * - AFTER:  the reverse (other is older)
     * - EQUAL:  identical clocks (same write, e.g., a retried/duplicated message)
     * - CONCURRENT: neither dominates -> both versions must be kept as siblings
     */
    public Comparison compare(VectorClock other) {
        boolean thisLessOrEqual = true;
        boolean otherLessOrEqual = true;

        for (String node : unionOfKeys(other)) {
            long thisVal = counters.getOrDefault(node, 0L);
            long otherVal = other.counters.getOrDefault(node, 0L);
            if (thisVal > otherVal) {
                otherLessOrEqual = false;
            }
            if (thisVal < otherVal) {
                thisLessOrEqual = false;
            }
        }

        if (thisLessOrEqual && otherLessOrEqual) return Comparison.EQUAL;
        if (thisLessOrEqual) return Comparison.BEFORE;   // this is dominated by other
        if (otherLessOrEqual) return Comparison.AFTER;   // this dominates other
        return Comparison.CONCURRENT;
    }

    public boolean isConcurrentWith(VectorClock other) {
        return compare(other) == Comparison.CONCURRENT;
    }

    /**
     * Merges two clocks by taking the element-wise maximum of each node's
     * counter. Used when a coordinator reconciles sibling versions: the
     * merged clock "knows about" both writes, so a subsequent write that
     * increments from the merged clock will dominate both prior siblings.
     */
    public VectorClock merge(VectorClock other) {
        Map<String, Long> merged = new HashMap<>(counters);
        for (var entry : other.counters.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        return new VectorClock(merged);
    }

    private Iterable<String> unionOfKeys(VectorClock other) {
        var union = new java.util.HashSet<>(counters.keySet());
        union.addAll(other.counters.keySet());
        return union;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VectorClock vc && counters.equals(vc.counters);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(counters);
    }

    @Override
    public String toString() {
        return counters.toString();
    }
}
```

In practice, when a `get` returns multiple **CONCURRENT** versions (siblings), the coordinator returns all of them to the application — for many use cases (shopping carts, set-like data) the application can merge siblings semantically (union the cart items), and the *next* write carries a vector clock that is the **merge** of all the siblings' clocks, which causally dominates all of them and collapses the sibling set back to one version. DynamoDB's original design and Riak both expose siblings this way; Cassandra instead defaults to LWW with millisecond timestamps (plus a tie-breaker), trading away sibling-based conflict detection for simplicity (§5).

### 4.4 Anti-Entropy — Merkle Trees and Read Repair

Quorum reads (§4.2) only repair divergence for keys that are *actually read*. A key written once and never read again can sit divergent across replicas indefinitely — if one replica missed the write (a transient network blip during the original `put`), it silently holds stale data forever unless something proactively checks. **Anti-entropy** is that proactive check, run periodically and independently of client traffic.

**Merkle trees** make this efficient: each node builds a binary hash tree over the key range owned by one of its vnodes (§4.1) — leaves are hashes of individual key-value pairs (or small key ranges), and each internal node is the hash of its two children's hashes, up to a single root hash representing the entire range. Two replicas compare their Merkle trees for the same key range **top-down**: if the root hashes match, the entire range is identical and the comparison stops immediately (one hash comparison covers potentially millions of keys). If the roots differ, the comparison recurses into the children whose hashes differ, narrowing down to the specific divergent sub-ranges in `O(log n)` comparisons rather than transferring or hashing the entire range.

```
Replica A's Merkle tree for vnode range [0x4000..0x8000):
                    H(root) = hash(H_L + H_R)
                   /                        \
            H_L = hash(...)            H_R = hash(...)
           /            \              /            \
      H_LL          H_LR          H_RL          H_RR     <- if H_RL differs
     (match)       (match)       (DIFFERS)      (match)       from Replica B's
                                       |                        H_RL, recurse only
                                  recurse into                  into that subtree
                                  this subtree
```

Once the divergent leaf-level key ranges are identified, the replica with the out-of-date data **streams the missing/differing key-value pairs** from its peer. This typically runs as a scheduled background job (e.g., every few hours per vnode pair) — at the scale from §2 (tens of thousands of vnode ranges), the per-range comparison cost is small, but the *aggregate* anti-entropy traffic across a 300-500 node cluster is a real capacity-planning input (§10) and the source of "repair storms" after extended outages (§8's runbook).

**Read repair** is the complementary, request-driven mechanism: whenever a `get`'s fan-out (§4.2) returns `R` (or more) responses and they **disagree**, the coordinator — after reconciling via vector clocks or LWW (§4.3) — asynchronously sends the reconciled value to the replica(s) that returned the stale version. Read repair is cheap (it piggybacks on traffic that's already happening) but only covers keys that are read; Merkle-tree anti-entropy is the backstop for everything else. War Story 1 (§9) is what happens when read repair is the *only* mechanism and a large fraction of keys go unread for a long time after a partition.

**Concrete example — how fast does each mechanism close a gap?** Suppose node `C` misses a write to key `K` during a 30-second network blip. If `K` is in the "hot" set from §2's hot-key skew discussion (read multiple times/sec), read repair fixes `C`'s copy within milliseconds of the blip ending — the very next `get` that fans out to `C` detects the mismatch and repairs it inline. But if `K` is a "cold" key — written once, rarely or never read again — `C`'s copy stays stale until the next scheduled Merkle-tree pass for `K`'s vnode range, which (at "every few hours per vnode pair") could be **hours** later. This gap is exactly why both mechanisms are required: read repair gives sub-second convergence for the hot working set that dominates read traffic, while anti-entropy bounds the worst-case staleness for the long tail of cold keys to the anti-entropy cycle time, not to infinity.

### 4.5 Hinted Handoff and Gossip-Based Failure Detection

**Hinted handoff** is the write-side complement to sloppy quorum (§4.2): if one of a key's `N` designated replicas (say, node `C`) is down when a `put` arrives, the coordinator writes the value to a substitute node `D` (the next healthy node on the ring) **along with a "hint"** — metadata recording that this data actually belongs to `C` and should be forwarded once `C` recovers. `D` stores the hinted write in a separate local queue. When `D`'s gossip-based failure detector (below) observes that `C` is healthy again, `D` streams its queued hints to `C` and, once `C` acknowledges, removes them from its queue. This lets writes succeed during a transient node outage *without* waiting for anti-entropy's next scheduled pass to eventually notice and fix the gap — hinted handoff is fast (triggered the moment the failed node rejoins) while Merkle-tree anti-entropy is the slow, thorough backstop.

**Gossip** is how every node learns the liveness state of every other node without a centralized membership service (no ZooKeeper-equivalent — contrast with §5's CP design). Each node periodically (e.g., once per second) picks one or a few random peers and exchanges a compact summary of its view of the cluster: `{nodeId: (heartbeatCounter, lastUpdateTimestamp, status)}`. If node `X` hasn't incremented its heartbeat counter (as observed transitively through gossip) for longer than a **suspicion threshold**, peers mark `X` as `SUSPECT`; if it remains silent past a **failure threshold**, peers mark it `DOWN` and route around it (sloppy quorum kicks in for its key ranges). Because gossip is **transitive** — `A` tells `B` what `A` heard from `C`, even if `B` and `C` never talk directly — information about a node's status (or a new node joining) propagates through an `N`-node cluster in roughly `O(log N)` gossip rounds, typically a few seconds even on a 300-500 node cluster. Many production systems (Cassandra) use a **Phi Accrual Failure Detector** instead of a fixed timeout: rather than a binary up/down threshold, it computes a continuous suspicion level `phi` based on the historical distribution of inter-arrival times for a node's heartbeats, adapting automatically to nodes (or network paths) that are simply slower rather than dead.

**Gossip overhead at scale**: each gossip round's message is small — a few hundred bytes to a few KB for a `{nodeId: (heartbeat, timestamp, status)}` summary across 500 nodes, well within a single UDP packet. At one round/second per node, a 500-node cluster generates roughly `500 x 1` = **500 gossip messages/sec** cluster-wide (each node gossiping with 1-3 peers) — three to four orders of magnitude below the cluster's 1,000,000+ writes/sec client-facing load (§2), which is why gossip overhead is essentially never a capacity-planning concern even at hundreds of nodes. The `O(log N)` propagation bound is what keeps this true as the cluster grows: doubling the cluster size from 500 to 1,000 nodes adds only one additional gossip round (roughly one extra second) to full-cluster propagation time, not a doubling of it.

### 4.6 Storage Engine — LSM-Tree vs. B-Tree (Brief)

Each node's local storage engine determines how the per-vnode data (§4.1) is actually persisted to disk. The two dominant families are **B-trees** (in-place updates, optimized for read-heavy point lookups and range scans — PostgreSQL, InnoDB) and **LSM-trees** (Log-Structured Merge trees — all writes go to an in-memory **memtable** plus a write-ahead log, periodically flushed to immutable on-disk **SSTables**, which are later **compacted** in the background to reclaim space from overwrites and tombstones).

Dynamo-lineage key-value stores overwhelmingly choose **LSM-trees**: at the write throughput from §2 (thousands of writes/sec per node), an LSM-tree's append-only, sequential-write pattern is dramatically friendlier to both spinning disks and SSDs than a B-tree's random in-place page updates. The cost is **read amplification** (a `get` may need to check the memtable plus several SSTables before finding the latest version of a key — mitigated by **bloom filters** per SSTable to skip ones that definitely don't contain the key) and **compaction overhead** (background CPU/IO to merge SSTables, which is why §2's storage estimate budgets extra headroom beyond the raw 300 TB). Cassandra, RocksDB (which underlies many KV stores), and LevelDB are all LSM-tree implementations. For the full mechanics of memtables, SSTables, compaction strategies (size-tiered vs. leveled), and bloom filter sizing, see [`../../database/storage_engines_internals/README.md`](../../database/storage_engines_internals/README.md) — this design treats the storage engine as a pluggable per-node component and focuses on the distributed-systems layer above it.

### 4.7 Tombstones and the `delete` Path

A `delete(key)` cannot simply remove the key's rows from local storage on each replica, because a physical delete is **invisible to anti-entropy** (§4.4): if replica `A` physically removes a key but replica `B` is partitioned away during the delete, `B` still has the old value. When `B` and `A` later run a Merkle-tree comparison, `A` has *nothing* for that key and `B` has *something* — from `A`'s perspective, this looks identical to "`B` has data that `A` is missing," and anti-entropy will dutifully **copy the deleted value back to `A`**, resurrecting a delete that the application believed had succeeded. This is the single most common correctness bug in homegrown Dynamo-style implementations.

The fix is to make `delete` a **write**, not a removal: the coordinator writes a special **tombstone** value (with its own vector clock / timestamp, exactly like a normal `put`) to all `N` replicas via the same quorum path (§4.2). A tombstone participates in read repair and anti-entropy exactly like any other value — if `A` is missing the tombstone that `B` has, anti-entropy copies the tombstone *to* `A`, not the other way around, because the tombstone's vector clock/timestamp dominates the old value's. Only after a **grace period** (`gc_grace_seconds` in Cassandra terms — long enough that anti-entropy is statistically certain to have propagated the tombstone to every replica, commonly several days) is the tombstone itself eligible for physical removal during compaction (§4.6).

```
put("session:abc", {...})           -> stored on replicas [A, B, C], clock V1
delete("session:abc")                -> tombstone written to [A, B, C], clock V2 (dominates V1)
  ... C was partitioned during the delete, still holds {..., V1} ...
  anti-entropy compares A/B (tombstone, V2) vs C (value, V1)
  V2 dominates V1 -> tombstone is copied TO C, not the value copied FROM C
  ... after gc_grace_seconds on all three replicas ...
compaction physically purges the tombstone from A, B, C
```

The grace period creates a real operational constraint: a node that has been down **longer than `gc_grace_seconds`** and then rejoins may still hold pre-tombstone values for keys whose tombstones have *already been purged* elsewhere — anti-entropy can then resurrect deleted data from that node, because the tombstone that would have "won" no longer exists anywhere to compare against. This is why §8's node-failure runbook treats "down longer than the grace period" as a case requiring a full rebuild from a fresh replica stream (a `removeNode` + `addNode` round-trip) rather than a normal hinted-handoff drain.

### 4.8 Multi-Datacenter Replication

Everything in §3-§4.6 describes replication *within* a single ring/datacenter. Production Dynamo-lineage deployments commonly span **multiple datacenters or cloud regions**, both for disaster recovery and to serve reads with low latency from the region nearest the client. The replication factor `N` from §1 is typically interpreted **per datacenter** — e.g., `N=3` in `us-east` and `N=3` in `eu-west` gives 6 total copies of every key, with each datacenter's 3 replicas chosen independently by that datacenter's local ring (§4.1).

Cross-datacenter replication is **asynchronous**: a write's quorum (`W`, §4.2) is satisfied by replicas in the **local** datacenter only — waiting for a remote datacenter's acknowledgment before returning to the client would add the cross-region RTT (commonly 50-150ms) to every write's latency, defeating the low-latency goal. Instead, the write is queued for asynchronous replication to remote datacenters' replicas, which apply it (and run it through the same vector-clock/LWW reconciliation as any other concurrent write, §4.3) typically within hundreds of milliseconds to a few seconds under normal conditions. This means a `get` with `R` satisfied entirely by local replicas can return a value that a *remote* datacenter hasn't received yet — an additional axis of "eventual" beyond the single-datacenter eventual consistency already discussed in §11.

Two consistency levels commonly distinguish "local quorum" from "global quorum" (cross-ref [`../../database/consistency_models_and_consensus/README.md`](../../database/consistency_models_and_consensus/README.md) for the general LOCAL_QUORUM vs. EACH_QUORUM framing):

| Consistency Level | Replicas Contacted | Cross-DC Latency Cost | Typical Use |
|---|---|---|---|
| `LOCAL_QUORUM` | `W` or `R` replicas in the *local* datacenter only | None — purely intra-DC RTT | Default for latency-sensitive reads/writes |
| `EACH_QUORUM` | `W` or `R` replicas in **every** datacenter | Full cross-region RTT, every request | Rare — only for data where every region must agree before acknowledging (e.g., a global uniqueness check) |

```
                         put("user:42", v, LOCAL_QUORUM)
                                       |
                                       v
   +----------------------------------------------------------------+
   |                         us-east (N=3)                           |
   |   coordinator ---> [Replica A] [Replica B] [Replica C]          |
   |                         |  ack     |  ack       (slow/unreached) |
   |                         +----------+                             |
   |                    W=2 satisfied -> ACK to client (no cross-DC   |
   |                    RTT on the critical path)                     |
   +----------------------------------------------------------------+
                                       |
                         async replication stream
                          (queued, applied via §4.3
                           reconciliation on arrival)
                                       |
                                       v
   +----------------------------------------------------------------+
   |                         eu-west (N=3)                           |
   |             [Replica D] [Replica E] [Replica F]                 |
   |   a LOCAL_QUORUM read here, issued before the async stream       |
   |   arrives, will NOT see "user:42" yet -> additional eventual-     |
   |   consistency window beyond intra-DC (§11)                       |
   +----------------------------------------------------------------+
```

The practical takeaway for capacity planning (§10): a multi-DC deployment roughly **multiplies storage and replica-write counts by the number of datacenters** (6 total copies for `N=3` x 2 DCs), but does **not** multiply client-facing latency, because `LOCAL_QUORUM` keeps the hot path entirely intra-region — the cross-DC replication traffic is a background cost, similar in spirit to anti-entropy's background streaming (§4.4, §10). Choosing `LOCAL_QUORUM` everywhere except for a small number of genuinely global invariants (and handling those few cases with a different mechanism entirely, e.g., a dedicated CP service for uniqueness checks) is the standard way multi-DC Dynamo-lineage deployments avoid paying cross-region RTT on their hot path while still offering disaster recovery and region-local latency.

---

## 5. Design Decisions & Tradeoffs

### AP (Dynamo/Cassandra-style) vs. CP (HBase/Bigtable-style)

| Dimension | AP (this design — Dynamo, Cassandra, Riak) | CP (HBase, Bigtable, CockroachDB) |
|---|---|---|
| Membership / metadata | Gossip — fully decentralized, no special nodes | Centralized metadata service (ZooKeeper for HBase, Chubby for Bigtable) |
| Behavior during a partition | Both sides remain available; writes accepted, reconciled later (vector clocks / LWW) | Minority side (without the metadata quorum) becomes unavailable for writes |
| Conflict resolution | Required — vector clocks or LWW (§4.3) | Not needed in steady state — a single elected leader per key range serializes writes |
| Read consistency | Tunable per request (R) — can be eventually consistent or "read your writes" with R high enough | Strong by default — reads go to the current leader/region-server for that key range |
| Failure recovery | Self-healing via gossip + hinted handoff + anti-entropy — no operator step required for transient failures | Requires the centralized metadata service to detect failure and reassign region ownership |
| Best fit | High write availability, geographically distributed, can tolerate eventual consistency (shopping carts, session stores, IoT telemetry) | Strong consistency requirements, willing to sacrifice some availability during partitions (financial ledgers, inventory counts requiring exact values) |

### Vector Clocks vs. Last-Write-Wins (LWW)

| Dimension | Vector Clocks | Last-Write-Wins (LWW) |
|---|---|---|
| Storage overhead | A counter per node that has ever written the key — grows with write fan-out, typically pruned/truncated | A single timestamp — fixed, tiny overhead |
| Detects true concurrency | Yes — concurrent writes are surfaced as siblings, nothing is silently dropped | No — concurrent writes are resolved by timestamp comparison alone |
| Failure mode | Sibling explosion if many nodes write the same key without reconciling (requires pruning policy) | **Silent data loss** when clocks are skewed or two writes are truly concurrent (War Story 2, §9) |
| Application complexity | Higher — application may need to merge siblings | Lower — the store always returns exactly one value |
| Used by | Original Dynamo paper, Riak | Cassandra (default; can be paired with client-supplied timestamps or, increasingly, hybrid logical clocks) |

### Virtual Nodes vs. Fixed (Single-Token) Partitioning

| Dimension | Virtual Nodes (this design, §4.1) | Fixed / Single-Token Partitioning |
|---|---|---|
| Load distribution across heterogeneous hardware | Smooth — assign more vnodes to higher-capacity nodes | Coarse — one token range per node regardless of capacity differences |
| Rebalance granularity when adding a node | Many small, scattered range transfers from many existing nodes (§10) | One large contiguous range transfer from a single neighbor |
| Anti-entropy parallelism | High — hundreds of independent small Merkle-tree ranges per node | Low — one large range per node, harder to parallelize repair |
| Operational complexity | Slightly higher (more metadata: which physical node owns which of its 256 token ranges) | Simpler mental model (one node = one contiguous range) |
| Used by | Cassandra (default), DynamoDB (internally), Riak | Early Cassandra versions (pre-1.2 default), some simpler sharded systems |

### Sloppy Quorum vs. Strict Quorum

| Dimension | Sloppy Quorum (this design's default, §4.2) | Strict Quorum |
|---|---|---|
| Behavior when a designated replica is down | Substitute the next healthy node on the ring; pair with hinted handoff (§4.5) | Operation fails (or blocks) if fewer than `W`/`R` *designated* replicas are reachable |
| Availability during a partition | High — `put`/`get` almost always succeeds if *any* `W`/`R` nodes anywhere are reachable | Lower — unreachable designated replicas directly cause failures |
| `W + R > N` overlap guarantee (§4.2, §11) | Weakened temporarily — a sloppy write's `W` acks may include non-designated nodes that a strict read won't contact | Holds precisely — every acking node is one of the `N` designated replicas |
| Recovery mechanism needed | Yes — hinted handoff (§4.5) to migrate sloppy-written data to its rightful owners | None — data is always on its correct owners by construction |
| Operational complexity | Higher — must monitor hinted-handoff queue depth (§8) as a leading indicator of degraded availability | Lower — no extra queue to monitor, but failure-mode visibility shifts to client-side error rates |
| Best fit | AP systems prioritizing "never reject a request" (this design) | Systems where "fail loudly rather than risk a consistency gap" is preferred for specific high-value operations |

Most Dynamo-lineage systems default to sloppy quorum cluster-wide but expose a per-keyspace or per-table override for strict quorum — useful for a small number of keys (e.g., account balances, feature-flag configs) where "fail the request and let the client retry against a fully-caught-up replica set" is preferable to "succeed against a possibly-incomplete replica set and reconcile later."

---

## 6. Real-World Implementations

- **Amazon DynamoDB**: the direct commercial descendant of the 2007 Dynamo paper, now a fully managed service. DynamoDB exposes **tunable consistency** at the API level — `GetItem` defaults to eventually consistent reads (cheaper, lower latency) but supports `ConsistentRead=true` for strongly consistent reads from the partition's leader, and **Global Tables** extend the AP model across regions with last-writer-wins conflict resolution based on timestamps.
- **Apache Cassandra**: originated at Facebook (combining Dynamo's partitioning/replication model with Bigtable's wide-column data model and SSTable storage format), Cassandra is the most widely deployed open-source Dynamo-lineage system. **Netflix** runs Cassandra at a scale of thousands of nodes across multiple AWS regions for viewing history, recommendations data, and account data — explicitly choosing AP because a brief delay in "Netflix knows you finished episode 4" is acceptable, but the service being unavailable during an AWS AZ outage is not. **Apple** has historically operated some of the largest known Cassandra deployments (tens of thousands of nodes, multiple petabytes) for iCloud and related services.
- **Riak (Basho)**: the implementation closest in spirit to the original Dynamo paper — Riak exposes vector clocks and sibling resolution directly to the application, and its `bucket`-level configuration of `N`, `W`, `R` mirrors the paper's terminology almost exactly. Basho (the company) shut down in 2017, but Riak remains in production at some long-standing deployments and is frequently cited in interviews and papers as the "textbook" Dynamo implementation.
- **Voldemort (LinkedIn)**: built at LinkedIn specifically for read-heavy, low-latency lookups backing features like "People You May Know" — notable for pluggable storage engines (BDB, MySQL, read-only stores built from offline Hadoop jobs) layered under the same consistent-hashing/replication core described in §4.1-§4.2.
- **ScyllaDB**: a from-scratch C++ rewrite that is wire-protocol-compatible with Cassandra, using a **shard-per-core** architecture (each CPU core owns a fixed slice of the data and runs its own event loop, avoiding cross-core locking entirely) to achieve substantially higher throughput per node than JVM-based Cassandra on the same hardware — same distributed-systems design (§3-§5), different single-node execution model.

### Adoption at a Glance

| System | Origin | Conflict Resolution | Default Consistency Posture | Notable At-Scale Users |
|---|---|---|---|---|
| DynamoDB | Amazon (managed) | LWW (timestamp-based) | Eventually consistent by default, strongly consistent reads optional | Amazon retail, countless AWS customers |
| Cassandra | Facebook (open-source) | LWW (default), pluggable | Tunable per query (`ONE`, `QUORUM`, `ALL`) | Netflix, Apple, Instagram |
| Riak | Basho (open-source) | Vector clocks + siblings | Tunable `N`/`W`/`R` per bucket | Comcast, legacy Riak deployments |
| Voldemort | LinkedIn (open-source) | Vector clocks (Dynamo-derived) | Tunable per store | LinkedIn (historically) |
| ScyllaDB | ScyllaDB Inc. (open-source/commercial) | LWW (Cassandra-compatible) | Tunable per query (Cassandra-compatible) | Discord, Comcast, Samsung |

### Lessons from the Original Dynamo Paper (2007)

Amazon's "Dynamo: Amazon's Highly Available Key-value Store" paper introduced the combination of techniques this entire design is built from — consistent hashing with virtual nodes, sloppy quorums with hinted handoff, vector clocks for conflict detection, and Merkle trees for anti-entropy — as a single integrated system built to keep Amazon's shopping cart service available during the holiday shopping season **even during partial datacenter failures**. The paper's central thesis, still the core argument for AP systems in this space, was that for Amazon's shopping cart, **"always writable"** mattered more than strict consistency: a customer being able to add an item to their cart, even if a stale cart later needs merging, was judged better than an error message. The paper's biggest internal controversy at the time was vector clocks and sibling resolution — exposing "multiple versions of the truth" to application developers was a significant API complexity cost, which is part of why later systems (Cassandra, and DynamoDB-the-product itself) defaulted to LWW for simplicity, accepting the data-loss tradeoff from §5 as the price of a simpler API. Two decades later, the techniques have diverged from their original integration — Cassandra kept consistent hashing and gossip but dropped vector clocks; DynamoDB-the-product kept the quorum model but is now a fully managed service with a completely different storage substrate internally — but nearly every "design a distributed cache/KV store" interview question is, at its core, asking a candidate to rediscover some subset of this paper's ideas.

---

## 7. Technologies & Tools

| Component | Representative Technologies | Notes |
|---|---|---|
| Hash ring / partitioning | Custom consistent-hash ring with vnodes (§4.1) | Murmur3 is the common production hash function (faster, better-distributed than MD5 for this purpose) |
| Storage engine | LSM-tree (RocksDB, custom SSTable implementations) | §4.6 — cross-ref [`../../database/storage_engines_internals/README.md`](../../database/storage_engines_internals/README.md) |
| Gossip / membership | Custom gossip protocol, Phi Accrual Failure Detector | §4.5 |
| Client library | Smart client with ring-topology caching, retries with backoff | Cross-ref [`../resilience_patterns/README.md`](../resilience_patterns/README.md) |
| Wide-column extension | Cassandra's CQL + column families on top of the same KV core | Cross-ref [`../../database/wide_column_databases/README.md`](../../database/wide_column_databases/README.md) |
| Coordination (CP alternative) | ZooKeeper / etcd-style consensus (not used in this AP design) | Cross-ref [`../consensus_algorithms/README.md`](../consensus_algorithms/README.md) for the contrast in §5 |
| Cross-DC replication transport | Async replication stream over a dedicated inter-region link | §4.8 — separate from intra-DC replica-write traffic for capacity planning (§10) |
| Monitoring / alerting | Time-series metrics store + dashboards for §8's metric set | Cross-ref [`../observability/README.md`](../observability/README.md) |

### Client Library Design

The "smart client" referenced above is not optional polish — it materially affects the latency numbers in §2 and §10. A **naive client** (one that knows nothing about the ring) must send every request to an arbitrary node, which then acts as coordinator and forwards to the actual replica set (§4.1) — adding one extra network hop to every request. A **ring-aware smart client** caches the ring topology (kept fresh via periodic gossip-state queries, §4.5) and computes `getNodesForKey` (§4.1) **client-side**, sending the request directly to one of the `N` replicas — that replica then acts as its own coordinator, fanning out to the other `N-1`. This single optimization removes one full network hop from the common case, which at single-digit-millisecond p99 targets (§1) is not a rounding error — it can be 20-30% of the total budget. The tradeoff is that the smart client must handle **topology staleness**: if the client's cached ring is out of date (a node was just added or removed), it may compute the "wrong" replica set — any node that receives a request it doesn't recognize as its own can still forward correctly (every node has an up-to-date view via gossip), so correctness is preserved, but the extra hop reappears for the (small, transient) fraction of requests issued against a stale client-side cache.

### Build vs. Buy Considerations

| Component | Build | Buy / Open-Source | This Design's Choice |
|---|---|---|---|
| Entire key-value store | Custom Dynamo-clone | Cassandra, ScyllaDB, Riak, or managed DynamoDB | Buy in almost all cases — the gossip/quorum/vector-clock/anti-entropy machinery (§4) is exactly the kind of subtle distributed-systems code that benefits enormously from years of production hardening elsewhere |
| Storage engine | Custom LSM-tree | RocksDB (embeddable, battle-tested) | Buy — RocksDB underlies many production KV stores and is rarely worth reimplementing |
| Client library | Custom smart client | Driver provided by the chosen store (DataStax driver for Cassandra, AWS SDK for DynamoDB) | Buy — ring-topology-aware routing and retry logic is store-specific and already provided |

---

## 8. Operational Playbook

### Key Metrics

| Metric | What It Measures | Alert Threshold (Illustrative) |
|---|---|---|
| **Read/Write p99 latency** | End-to-end coordinator response time for `get`/`put` | Page if p99 read > 10ms or p99 write > 15ms sustained over 5 minutes |
| **Quorum failure rate** | Fraction of requests where fewer than `W`/`R` replicas responded in time | Page if > 0.1% of requests fail quorum — indicates either widespread node trouble or a misconfigured `N`/`W`/`R` |
| **Anti-entropy / repair lag** | Time since each vnode range's last successful Merkle-tree comparison with its peers | Page if any range hasn't completed repair in > 24 hours |
| **Hinted-handoff queue depth** | Number of queued hints per node awaiting delivery to a recovering replica | Page if depth exceeds a few hundred thousand entries or queue age exceeds the hint TTL (commonly ~3 hours) |
| **Node-down detection time** | Time from actual node failure to cluster-wide `DOWN` status via gossip (§4.5) | Investigate if consistently > 30 seconds — may indicate gossip fan-out or Phi Accrual thresholds need tuning |
| **Compaction backlog** | Pending SSTable compaction work per node (§4.6) | Page if backlog grows unboundedly — read amplification will degrade latency |
| **Cross-DC replication lag** | Time for a write acknowledged via `LOCAL_QUORUM` (§4.8) to apply in a remote datacenter | Page if sustained lag > a few seconds — feeds directly into how stale a `LOCAL_QUORUM` read in the *other* DC can be |
| **Tombstone ratio** | Fraction of SSTable entries that are tombstones vs. live values (§4.7) | Investigate if a single table's tombstone ratio exceeds ~20% — indicates either a delete-heavy workload outgrowing `gc_grace_seconds` tuning, or an application bug issuing redundant deletes |

### Runbook: Node Failure and Hinted-Handoff Drain

1. **Detect**: gossip (§4.5) marks the failed node `DOWN` across the cluster within seconds; alert fires on the node-down metric and on a spike in hinted-handoff queue depth for the failed node's neighbors (the nodes now acting as sloppy-quorum substitutes, §4.2).
2. **Confirm scope**: check whether this is a single-node hardware failure (most common) or a broader issue (rack/AZ-level network partition affecting multiple nodes simultaneously) — the response differs significantly.
3. **Single-node case**: if the node is recoverable (e.g., a restart fixes it), bring it back online. Once gossip propagates its `UP` status, neighboring nodes automatically begin **draining their hinted-handoff queues** to it — monitor queue-depth metrics on the neighbors to confirm drain progresses and completes (drain rate depends on the volume of writes accumulated during the outage and the configured handoff throttle).
4. **Unrecoverable node**: if the node cannot be recovered within the hint TTL (commonly a few hours), **decommission** it properly (a controlled `removeNode`, §4.1) rather than letting hints expire silently — expired hints represent writes that are now under-replicated until the next anti-entropy pass (§4.4) catches up, which for a long-TTL-expired range could take until the next scheduled repair cycle.
5. **Verify**: confirm hinted-handoff queue depths return to baseline and, for any node that was down longer than the hint TTL, manually trigger (or wait for the next scheduled) anti-entropy repair (§4.4) for the ranges that node owns, to close any gap hints didn't cover.

### Runbook: Repair Storm After Extended Outage

1. **Trigger**: a node (or several) was down for longer than the hinted-handoff TTL, or an entire rack/AZ was partitioned for hours. When it rejoins, every vnode range it owns is now significantly divergent from its peers, and the next scheduled anti-entropy pass (§4.4) for *all* of those ranges may fire at once.
2. **Symptom**: a sudden spike in inter-node streaming traffic (Merkle-tree comparison + data streaming for many vnode ranges simultaneously), competing with normal client read/write traffic for disk I/O and network bandwidth — client-facing p99 latency degrades cluster-wide, not just on the recovered node.
3. **Immediate mitigation**: throttle anti-entropy/repair concurrency — most implementations support limiting how many vnode-range repairs run in parallel per node and cluster-wide. Reduce this limit temporarily to relieve pressure on client-facing latency, accepting that full repair will take longer.
4. **Stagger, don't disable**: do not disable anti-entropy entirely (War Story 1, §9, is what happens when repair is skipped) — instead, schedule the recovered node's repairs to run **sequentially across its vnode ranges** rather than all at once, and avoid scheduling repairs for *other* unrelated ranges during the same window.
5. **Verify**: track anti-entropy/repair-lag metric (above) back to baseline (< 24h for all ranges) and confirm client-facing p99 latency has returned to normal before considering the incident closed.

### Runbook: Cross-Datacenter Replication Lag Spike

1. **Detect**: the cross-DC replication lag metric (above) climbs past its alert threshold for one direction (e.g., `us-east -> eu-west`) while the reverse direction remains normal — directional asymmetry is the first useful diagnostic clue, since a genuine link-level network issue usually affects both directions roughly symmetrically.
2. **Check the asynchronous replication queue**: cross-DC replication (§4.8) is asynchronous and queued — confirm whether the queue is growing because of (a) reduced network throughput between the two regions, (b) the destination region's nodes being individually overloaded (high CPU/compaction backlog, §4.6, slowing their ability to apply incoming replicated writes), or (c) a genuine traffic spike in the source region outpacing steady-state replication capacity.
3. **Assess blast radius**: while lag is elevated, `LOCAL_QUORUM` reads (§4.8) in the lagging destination region will not reflect recent writes from the source region — this is "more eventual than usual" eventual consistency, not an outage. Confirm whether any consumers of this cluster assume cross-DC freshness tighter than the current lag (a common false assumption that surfaces during incidents like this).
4. **Mitigate**: if the cause is (b) — destination-region overload — apply the repair-storm throttling principles above to relieve general load before the replication queue can drain. If the cause is (a) — network throughput — this is typically outside the cluster's control; the queue will drain once the network issue resolves, but operators should confirm the queue has bounded retention (it should not silently drop queued replication entries if the backlog grows very large).
5. **Verify**: confirm lag returns to its normal sub-second-to-low-seconds baseline in both directions, and spot-check a sample of recently-written keys via `EACH_QUORUM` (§4.8) reads in both regions to confirm convergence before closing the incident.

---

## 9. Common Pitfalls & War Stories

Both war stories below share a common shape worth recognizing as a pattern: **a mechanism that is "usually redundant" (read repair given anti-entropy; clock-based timestamps given that clocks "should" be in sync) turns out to be load-bearing the moment its assumptions are violated** — and both violations (a long network partition; a slow clock drift) are exactly the kind of slow-burning, easy-to-miss condition that doesn't trigger an immediate alert on its own. The fixes in both cases are the same shape too: turn an implicit assumption ("anti-entropy will catch it eventually," "clocks are roughly synced") into an explicitly monitored, alertable invariant (§8's metrics).

### War Story 1: Disabled Read Repair Causes Stale-Read Amplification After a Partition Heals — Broken, Then Fixed

**Broken**: An operations team, chasing a latency regression, noticed that read-repair writes (§4.4) — the asynchronous "push the reconciled value back to stale replicas" step that follows every quorum read with disagreement — were adding measurable extra write load to the cluster. Under pressure to hit a latency SLA, they disabled read repair cluster-wide as a "temporary" mitigation, reasoning that the **scheduled Merkle-tree anti-entropy job (§4.4)** would eventually catch any divergence anyway. The anti-entropy job, however, had a much longer cycle time per range (on the order of days, given the cluster's size) than anyone had verified — and "temporary" lasted for weeks.

**Impact**: shortly after, a multi-hour network partition split the cluster roughly in half. With sloppy quorums (§4.2) keeping both sides available, writes continued on both sides — but with `N = 3` replicas often split 2-1 or 1-2 across the partition, a large fraction of keys ended up with **divergent versions** once the partition healed (one side's replicas had the pre-partition value, the other side's had a newer value written during the partition). With read repair disabled, `get` requests for these keys returned **whichever version happened to satisfy the read quorum first** — and because quorum membership for a given request depends on which replicas respond fastest (a function of transient load, not a fixed assignment), **the same client, polling the same key in a loop, flip-flopped between the old and new value for hours**. Application-level caches downstream made this worse: a cache populated with the stale version held it for its full TTL, so even users who avoided the flip-flopping `get` path got a consistently *wrong* (but stable-looking) answer. The anti-entropy job, true to its days-long cycle time, hadn't reached the affected ranges yet.

**Fixed**: read repair was re-enabled immediately — the latency cost it added (a small async write on disagreement, not on the read's critical path) was real but far smaller than the cost of the incident. More importantly, the team added two structural fixes: (1) **read repair is now a non-negotiable part of the quorum-read path** and is excluded from "things that can be temporarily disabled for latency" — any future latency investigation must look elsewhere; and (2) the **Merkle-tree anti-entropy cycle time was reduced** from days to **under 24 hours per range** by parallelizing across vnodes (§4.1's "hundreds of independent small ranges" property made this straightforward once prioritized), so that even if read repair *were* somehow bypassed for a key, the backstop closes the gap within a bounded, alertable window (§8's repair-lag metric).

### War Story 2: Clock Skew Breaks Last-Write-Wins, Silently Dropping a Newer Write — Broken, Then Fixed

**Broken**: the cluster used **Last-Write-Wins** (§4.3) for conflict resolution, with timestamps assigned by each coordinator node at write time from its local system clock. NTP was configured but not actively monitored — "NTP is running" was treated as equivalent to "clocks are correct."

**Impact**: one node's NTP daemon silently stopped synchronizing after a configuration change (an unrelated infrastructure update inadvertently blocked outbound NTP traffic for that node's subnet). Over the following weeks, that node's clock drifted **about 90 seconds ahead** of the rest of the cluster — small enough that no human noticed, but large enough to matter for LWW. A customer updated their account's shipping address (a `put` coordinated by a different, correctly-synced node, timestamp `T`). Forty seconds later — well within any reasonable "concurrent enough to matter" window — the customer updated it *again* with a correction (a `put` coordinated by the clock-skewed node, but because of replica placement and request routing, this write's timestamp was computed as `T - 50 seconds` relative to true time, due to the skew making the skewed node's clock appear to be in the past **relative to where it actually was at write time** — the specific direction of the bug depended on which write landed on the skewed node, but the net effect was that **the chronologically later write carried the chronologically earlier timestamp**). When the two versions were compared under LWW, the **first** address update "won" — its (incorrectly later-appearing) timestamp beat the second update's (incorrectly earlier-appearing) timestamp. The customer's correction was **silently discarded**. The customer's order shipped to the old address. This was caught only because the customer filed a support complaint — there was no system-level signal that a write had been dropped, because from the system's perspective, LWW had worked exactly as designed: it picked "the" winner and discarded "the" loser, with no record that the loser had ever existed.

**Fixed**: three changes, layered:
1. **NTP monitoring became a first-class alert**: every node's clock offset from a trusted time source is now actively monitored, with paging alerts on drift exceeding a few hundred milliseconds — far tighter than the 90-second drift that caused the incident, giving enormous margin.
2. **Hybrid Logical Clocks (HLC)** were introduced for new conflict-resolution logic: an HLC combines a physical-time component with a logical counter, guaranteeing that the HLC timestamp of a causally-later event is always greater than that of any event it depends on, *even under bounded clock skew* — this directly closes the "later write, earlier timestamp" failure mode, because the logical-counter component cannot regress even if the physical-clock component does.
3. **For the highest-value data** (anything where silent loss has a real-world consequence like a misdirected shipment), the team migrated to **vector clocks** (§4.3) specifically *because* vector clocks make concurrent/conflicting writes **visible** rather than silently resolved — the application layer for those data types now explicitly handles sibling resolution (e.g., "two address updates within the last minute — ask the user which is correct" or "always prefer the update with the higher per-field-version counter") instead of trusting a timestamp comparison that clock skew can invalidate.

### From War Stories to Dashboards

Both incidents above ended with the same kind of fix: a previously-implicit assumption became an explicitly monitored number on a dashboard (§8). "Anti-entropy will eventually catch divergence" became a **repair-lag metric with a 24-hour SLO**. "NTP keeps clocks roughly in sync" became a **clock-offset metric with a sub-second alert threshold**. Neither fix required new distributed-systems algorithms — both required recognizing that a *qualitative* assumption ("this is fine," "this is rare") needed to become a *quantitative*, alertable one. This is a generally useful lens for reviewing any AP system's design: for every place the design says "eventually" or "usually" or "should rarely happen," ask what metric would tell you the moment that assumption stops holding, and whether that metric is on a dashboard today.

---

## 10. Capacity Planning

### Cluster Sizing from §2's Numbers

- **Storage-driven floor**: ~300 TB raw (100 TB logical x `N=3`) / ~1 TB usable per node ~= **~300 nodes** minimum for storage alone (§2)
- **Throughput-driven floor**: 1,000,000 writes/sec x `N=3` = 3,000,000 replica-writes/sec; at a sustainable **~8,000-10,000 writes/sec/node** for an LSM-tree engine under realistic compaction load (§4.6), `3,000,000 / 9,000` ~= **~333 nodes**
- **Read-driven check**: 10,000,000 reads/sec, with `R=2` of `N=3` replicas typically queried -> 20,000,000 replica-reads/sec; at a sustainable **~40,000-50,000 reads/sec/node** (reads are cheaper than writes on an LSM-tree thanks to bloom filters and OS page cache for hot data), `20,000,000 / 45,000` ~= **~445 nodes**
- **Combined sizing**: the read-driven number dominates -> **~450 nodes**, with the storage and write floors comfortably satisfied as a byproduct. Add **15-20% headroom** for compaction overhead, hinted-handoff queues during incidents, and rolling upgrades -> **~500-550 nodes** as the target steady-state cluster size, consistent with the "~300-500 nodes" range from §2's framing.

### Rebalance Time When Adding a Node

- Adding 1 node to a 500-node cluster means that node should end up owning roughly `1/501` ~= **0.2%** of the total data
- Data to stream to the new node: `total_data / num_nodes` (post-add) = `300 TB / 501` ~= **~0.6 TB**
- At a sustained inter-node streaming rate of **~200 MB/sec** (deliberately throttled to avoid competing with client traffic, per §8's repair-storm runbook philosophy applied to joins as well as repairs), streaming ~0.6 TB takes `600,000 MB / 200 MB/sec` ~= **~3,000 seconds, roughly 50 minutes**
- Because of **virtual nodes (§4.1)**, this ~0.6 TB doesn't come from one neighbor — it's assembled from **256 small chunks**, each roughly `0.6 TB / 256` ~= **~2.3 GB**, streamed from up to 256 *different* existing nodes. This is the practical payoff of vnodes for rebalancing: no single existing node experiences a disproportionate streaming burden, and the 256 transfers can proceed with significant parallelism (bounded by the throttle above, to protect client-facing latency) rather than being serialized through one source node.
- **Virtual-node count's effect on smoothness**: with too few vnodes per node (say, 4-8), adding a node moves a handful of large chunks from a handful of existing nodes — each of those nodes takes a noticeable, lumpy latency hit during the transfer. With 256 vnodes, the same total data is divided into 256 transfers small enough that each individual source node's contribution is a rounding error on its normal load — the rebalance becomes a background hum rather than an event.

### Shrinking the Cluster — Decommissioning Nodes

The reverse of §10's rebalance math applies when **removing** nodes (e.g., right-sizing after over-provisioning, or migrating to fewer/larger instance types). Decommissioning one node from a 500-node cluster requires that node's ~0.6 TB (its `1/500` share) to be **streamed out** to the nodes taking over its 256 vnode ranges *before* it leaves — the same ~50-minute, 256-chunk profile as a join, just in reverse (§4.1's `removeNode`). The operational hazard is decommissioning **multiple nodes too close together**: if 5 nodes are decommissioned within the same window, the cluster briefly has `5 x 256 = 1,280` vnode ranges all needing to stream their data to *fewer* remaining nodes simultaneously, each of which is also absorbing its normal share of the other 4 nodes' departures — the data-to-stream-per-remaining-node spikes well above the steady-state ~0.6 TB/event figure. The standard mitigation is the same "stagger, don't batch" principle as §8's repair-storm runbook: decommission nodes **one at a time**, each waiting for the previous node's stream-out to complete and cluster ownership to stabilize (confirmed via `ownedFraction`, §4.1) before starting the next.

### Repair / Anti-Entropy Bandwidth Budget

- ~500 nodes, each running scheduled Merkle-tree comparisons (§4.4) against its `N-1 = 2` replica peers for each of its 256 vnode ranges, on a cycle time of < 24 hours (War Story 1's fix)
- Steady-state divergence (normal operation, no extended outages) is typically a tiny fraction of a percent of data per range — repair traffic in steady state is dominated by the **hash-comparison** traffic (cheap: one root hash per range comparison in the common "no divergence" case) rather than data streaming
- Budget roughly **5-10% of a node's network capacity** for steady-state anti-entropy hash traffic plus occasional small streams; this headroom is what gets temporarily consumed (and why §8's repair-storm runbook throttles it) when an extended outage forces many ranges to stream real data simultaneously

### Multi-Datacenter Sizing

- A 2-datacenter deployment (§4.8) with `N=3` per DC effectively **doubles** the cluster footprint from §10's single-DC numbers: ~500-550 nodes per DC -> **~1,000-1,100 nodes total**, and ~300 TB raw storage per DC -> **~600 TB total** (6 total copies of the 100 TB logical dataset)
- Cross-DC replication bandwidth: at 1,000,000 writes/sec x 5 KB average (§2), the async replication stream carries roughly `1,000,000 x 5 KB` = **~5 GB/sec** between datacenters in steady state — this is in addition to (not instead of) the intra-DC replica-write traffic from §2, and is the dominant driver of inter-region network cost in a multi-DC deployment
- `LOCAL_QUORUM` (§4.8) keeps this 5 GB/sec off the client-facing latency path entirely — it's purely a background, asynchronous cost, sized the same way anti-entropy bandwidth is budgeted above (a steady-state percentage of inter-DC link capacity, with alerting on queue growth per §8's cross-DC lag runbook)

### Summary Table

| Component | Sizing Basis | Estimated Footprint |
|---|---|---|
| Cluster node count (single DC) | max(storage floor ~300, write floor ~333, read floor ~445) + 15-20% headroom | ~500-550 nodes |
| Cluster node count (2 DCs) | Single-DC footprint x 2 (§4.8) | ~1,000-1,100 nodes |
| Raw storage (single DC) | 100 TB logical x N=3 | ~300 TB |
| Raw storage (2 DCs) | Single-DC raw storage x 2 | ~600 TB |
| Per-node storage | 300 TB / 500 nodes | ~600 GB/node (well under the ~1TB/node budget, leaving compaction headroom) |
| Vnodes per node | Smoothing factor for load + rebalance | 128-256 |
| New-node rebalance | total_data / num_nodes, streamed in 256 chunks at ~200MB/sec aggregate throttle | ~0.6 TB, ~50 minutes |
| Anti-entropy cycle time | Per-vnode-range Merkle comparison with N-1 peers | < 24 hours/range (War Story 1) |
| Hinted-handoff TTL | Bridges transient outages before anti-entropy backstop | ~3 hours (illustrative) |
| Cross-DC replication bandwidth | 1M writes/sec x 5KB average | ~5 GB/sec (async, background) |
| Gossip overhead | ~1 round/sec/node, O(log N) propagation (§4.5) | ~500 msgs/sec cluster-wide — negligible vs. client traffic |

This table is the artifact a capacity-planning review actually produces: every row traces back to a number from §2 or §4, so when traffic projections change (say, the 10M reads/sec figure grows by 50% for next year's roadmap), the node-count and storage rows can be recomputed mechanically rather than re-derived from scratch — the read floor becomes `15M / (3 x 7,500)` ~= **667 nodes**, which then becomes the new dominant term in the `max()` and the new headroom-adjusted cluster target (~750-800 nodes).

---

## 11. Interview Discussion Points

**Q: Why is DynamoDB described as "eventually consistent" if it also supports strongly-consistent reads?**
A: "Eventually consistent" describes the *default* and the *underlying replication model*, not an absolute limit on every API call. DynamoDB's `GetItem` defaults to an eventually-consistent read (query one replica, cheaper and lower-latency), but `ConsistentRead=true` routes the read to the partition's current leader, returning the latest acknowledged write at the cost of higher latency and reduced availability if that leader is unreachable. The underlying replication is still asynchronous/quorum-based (§4.2) — "strongly consistent reads" is a *per-request* choice to pay more to talk to the authoritative replica, not a cluster-wide consistency guarantee that overrides the AP design.

**Q: What does `W + R > N` actually guarantee — and what does it NOT guarantee?**
A: It guarantees that any read quorum and any write quorum for the same key **share at least one replica in common**, so a `get` is guaranteed to contact at least one node that has the most recent successful `put`'s data (§4.2). It does **not** guarantee linearizability: two reads issued moments apart can still observe different "winners" if a write is still propagating and each read happens to overlap with a different subset of replicas, and it does not protect against **sloppy quorum** substitutions (§4.2) — if a write's `W` acknowledgments came partly from non-designated substitute nodes (because designated replicas were down), a read that only contacts the *designated* replicas might not overlap with that write until hinted handoff completes.

**Q: Sloppy quorum vs. strict quorum — what's the availability/consistency tradeoff?**
A: A strict quorum only counts acknowledgments from the key's `N` *designated* replicas (per the hash ring, §4.1) — if fewer than `W`/`R` of those specific nodes are reachable, the operation fails outright, even if other healthy nodes exist. A sloppy quorum allows the coordinator to substitute the next healthy node(s) on the ring when designated replicas are down, paired with hinted handoff (§4.5) to later deliver the data to its rightful owner. Sloppy quorum maximizes availability (the operation almost always succeeds if *any* `W`/`R` nodes are reachable) at the cost of temporarily weakening the `W+R>N` overlap guarantee above — strict quorum is the opposite tradeoff, favoring "fail loudly" over "succeed somewhere and reconcile."

**Q: Vector clocks vs. LWW — when does each cause data loss?**
A: LWW causes data loss whenever two writes are **truly concurrent** (or appear so due to clock skew, War Story 2) — the timestamp comparison picks one "winner" and the other write's data vanishes with no record it existed. Vector clocks don't lose data in the same way: concurrent writes are detected as **siblings** and both are preserved until explicitly merged (§4.3) — but vector clocks can cause a *different* kind of "loss" if the sibling-resolution policy is naive (e.g., an application that only reads `siblings.get(0)` and ignores the rest is functionally doing LWW anyway, just with extra steps). The practical guidance: LWW is fine for data where losing a rare concurrent write is acceptable (a "last viewed timestamp"); vector clocks (or an LWW variant with HLCs, War Story 2's fix) are needed where every write matters (financial-adjacent fields, anything a user would notice disappearing).

**Q: Why do virtual nodes matter, especially for a heterogeneous cluster?**
A: Without vnodes, each physical node owns exactly one contiguous arc of the hash ring, and the *size* of that arc is essentially random — some nodes get a disproportionately large share of the keyspace purely by chance, creating hotspots. Virtual nodes (§4.1) give each physical node 128-256 small, scattered positions, so the law of large numbers smooths ownership to near-`1/numNodes` regardless of random placement, and a higher-capacity node can simply be assigned more vnodes proportionally. The secondary benefit (§10) is rebalancing smoothness: adding a node redistributes many small chunks from many sources rather than one large chunk from one source.

**Q: Hinted handoff vs. read repair — what's the difference, and do you need both?**
A: Both repair under-replication, but on different triggers and timescales. Hinted handoff (§4.5) is **write-triggered and proactive**: when a designated replica is down at write time, a substitute node holds the data and forwards it the moment the replica recovers — fast, but only covers writes that happened *during* the outage. Read repair (§4.4) is **read-triggered**: whenever a `get`'s replicas disagree, the coordinator pushes the reconciled value to stale replicas — but only for keys that are actually read afterward. Yes, you need both, plus Merkle-tree anti-entropy (§4.4) as the backstop for keys that are neither written-during-an-outage nor read afterward — War Story 1 is what happens when you rely on read repair alone and anti-entropy's cycle time is too slow.

**Q: Walk through the gossip protocol mechanics — how does a node learn that a far-away peer just failed?**
A: Each node periodically (e.g., every second) exchanges a compact state summary — `{nodeId: (heartbeatCounter, timestamp, status)}` — with one or a few randomly chosen peers (§4.5). Gossip is **transitive**: if `A` gossips with `B`, `B` now knows everything `A` knew, including `A`'s most recent information about `C`, `D`, etc., even if `B` has never directly gossiped with them. This means information propagates through an `N`-node cluster in roughly `O(log N)` rounds — for a 500-node cluster, a handful of seconds. A node is marked `SUSPECT` when its heartbeat counter hasn't advanced (as observed transitively) for longer than expected, and `DOWN` after a further threshold; production systems often use a Phi Accrual Failure Detector that adapts the threshold to each node's historical heartbeat-timing variance rather than a fixed timeout.

**Q: What's the Merkle-tree repair cost for a vnode range with no divergence vs. one with significant divergence?**
A: For a range with **no divergence**, the cost is essentially **one hash comparison** — the root hashes of both replicas' Merkle trees match, and the comparison terminates immediately, regardless of how many keys the range contains (§4.4). For a range with **significant divergence** (e.g., after an extended outage), the comparison recurses down the tree wherever hashes differ, and at the leaves, the actual divergent key-value pairs must be **streamed** between replicas — the cost becomes proportional to the *amount of divergent data*, not the total range size. This is why anti-entropy is cheap in steady state (mostly root-hash comparisons) but can spike sharply after an extended outage (§8's repair-storm runbook) — the divergence accumulated during the outage must now be streamed, not just hashed.

**Q: How does the system handle adding a new node to the cluster?**
A: The new node is assigned its share of vnode positions on the ring (§4.1) — for a cluster targeting 256 vnodes/node, the new node "claims" 256 positions, each taking over a small slice of keyspace previously owned by whichever node held the adjacent position. For each claimed slice, the new node streams the relevant data from the node that previously owned it (§10: ~0.6 TB total for a 500-node cluster, in ~256 chunks of ~2.3 GB each). During the transfer, the *old* owner continues serving reads/writes for that range (it remains in the replica set until the transfer completes and gossip propagates the new ownership), so there's no availability gap — the new node simply becomes an additional/replacement replica once it's caught up.

**Q: How does the system handle removing a node — planned decommission vs. unplanned failure?**
A: **Planned decommission**: the node streams all the data it owns to the nodes that will take over its vnode ranges *before* leaving the ring, so replication factor `N` is maintained throughout — no window of under-replication. **Unplanned failure**: the node disappears immediately; gossip (§4.5) marks it `DOWN`, sloppy quorum (§4.2) routes its traffic to substitute nodes with hinted handoff (§4.5) queuing writes for it, and the cluster is **under-replicated** (`N-1` instead of `N` for its key ranges) until either the node recovers (hints drain) or an operator runs `removeNode` (§4.1) to permanently reassign its ranges and trigger anti-entropy (§4.4) to bring the new replicas up to `N`.

**Q: What happens if a replica is *permanently* down and nobody runs `removeNode`?**
A: Every key whose replica set includes the dead node is permanently running at `N-1` effective replicas for that slot — quorum operations still work as long as `W`/`R` can be satisfied by the remaining `N-1` (e.g., `N=3, W=2, R=2` still has 2 healthy replicas to draw from), but the **safety margin is gone**: a second failure among that key's remaining replicas now drops below quorum entirely. Hinted handoff (§4.5) will queue writes for the dead node indefinitely (or until the hint TTL expires, after which those writes are simply lost *for that replica* — though still present on the other `N-1`). The operational fix is always the same: `removeNode` (§4.1) reassigns the dead node's vnode ranges to other nodes and triggers anti-entropy to restore full `N`-way replication — leaving a dead node "in the ring" indefinitely is a latent risk that compounds with every additional failure.

**Q: A client reads a key twice in quick succession and gets two different values, neither of which is an error — what's happening?**
A: This is eventually-consistent reads working as designed (§5, §11's first question). With `R < N` (e.g., `R=2, N=3`), each `get` only needs to query 2 of the 3 replicas, and *which* 2 respond depends on transient latency/load — if the replicas haven't fully converged yet (a write is still propagating, or read repair/anti-entropy hasn't caught up), two different `R`-sized subsets can return two different "most recent" values. This is expected behavior for an AP system and is the direct tradeoff for the availability/latency benefits of `R < N` — applications that cannot tolerate this either use `R = N` (slower, less available, but every read sees every replica) or design for idempotent/commutative operations that are correct regardless of read order.

**Q: How would you extend this design to support secondary indexes (e.g., "find all users with `status = active`")?**
A: The pure key-value model (§1) has no native support for this — a secondary index is itself a separate key-value mapping (`indexKey -> set of primary keys`) that must be kept in sync with the primary data, which reintroduces a multi-key consistency problem this design explicitly avoids (§1's out-of-scope). Cassandra's wide-column extension (cross-ref [`../../database/wide_column_databases/README.md`](../../database/wide_column_databases/README.md)) layers secondary indexes and materialized views on top of the same partitioning/replication core (§4.1-§4.2), with the caveat that secondary-index consistency inherits the same eventual-consistency characteristics as everything else — an index update and the primary write it corresponds to are not atomic.

**Q: Compare this design's failure-detection approach to a CP system using ZooKeeper.**
A: This design uses gossip (§4.5) — fully decentralized, every node independently arrives at (eventually consistent) beliefs about every other node's status, with no single source of truth and no single point of failure for membership itself. A CP system (§5) instead relies on a small **consensus-based metadata service** (ZooKeeper for HBase, Chubby for Bigtable, cross-ref [`../consensus_algorithms/README.md`](../consensus_algorithms/README.md)) that maintains an authoritative, strongly-consistent view of which node owns which key range — region-server failure is detected by that service (typically via session timeout on an ephemeral node), and region reassignment is a coordinated, serialized operation. The tradeoff is exactly the CAP tradeoff: gossip has no single point of failure but only provides *eventually* consistent membership views (two nodes can briefly disagree about whether a third is up), while the ZooKeeper-style approach provides a single consistent answer but makes the metadata service itself a (small, carefully-engineered) critical dependency.

**Q: Your monitoring shows hinted-handoff queue depth growing without bound on several nodes — what do you check first?**
A: First, check whether the nodes those hints are *destined for* are actually down or just slow (§8's runbook) — gossip should have marked them `DOWN` if truly unreachable, in which case unbounded queue growth on their neighbors is *expected* during the outage and the question becomes "how long until the hint TTL expires and we lose durability for these writes" (§10). If the destination nodes appear `UP` in gossip but hints aren't draining, suspect a problem with the handoff mechanism itself — e.g., the handoff throttle is set so conservatively that drain rate is slower than accumulation rate even for a healthy destination, or the destination is `UP` for gossip purposes but its storage engine is unable to keep up with the combination of normal traffic plus incoming hint replay (a secondary overload, not the original failure).

**Q: How do you decide what to set N, W, and R to for a new use case on this cluster?**
A: Start from `N=3` as the default (tolerates 1 node failure with `W=R=2` still satisfiable, and 2 failures are rare enough that `N=3` is the standard industry default) unless the data's value justifies more replicas (financial records sometimes use `N=5`). Then choose `W` and `R` based on the read/write ratio and latency sensitivity from §2-style estimation: a write-heavy, latency-sensitive workload (e.g., activity logging) might use `W=1` (fast writes, accept some risk of loss on immediate node failure) with `R=3` to compensate on the read side; a read-heavy workload needing freshness (e.g., inventory counts) might use `W=2, R=2` (the balanced default, guaranteeing overlap per §4.2) or even `W=3, R=1` if writes are rare and reads must be fast. The one combination to avoid is `W+R <= N` for any data where staleness has real consequences — it gives up the overlap guarantee entirely for a latency gain that `W=R=2` with `N=3` usually achieves anyway.

---

## Cross-References

- **Consistent hash ring with virtual nodes as the partitioning mechanism (§4.1, §10)** -> [`../consistent_hashing/README.md`](../consistent_hashing/README.md)
- **Horizontal partitioning and shard-key selection generalized beyond a single hash ring (§4.1, §10)** -> [`../../database/sharding_and_partitioning/README.md`](../../database/sharding_and_partitioning/README.md)
- **Quorum-based replication, consistency models, and the CAP/PACELC framing underlying §4.2 and §5** -> [`../../database/consistency_models_and_consensus/README.md`](../../database/consistency_models_and_consensus/README.md)
- **CAP theorem and the AP-vs-CP framing of §1 and §5** -> [`../cap_theorem/README.md`](../cap_theorem/README.md)
- **Wide-column extensions (secondary indexes, column families) built on this same KV core (§1, §6, §11)** -> [`../../database/wide_column_databases/README.md`](../../database/wide_column_databases/README.md)
- **General key-value store data structures and persistence tradeoffs (§4.6, §7)** -> [`../../database/key_value_stores/README.md`](../../database/key_value_stores/README.md)
- **LSM-tree vs. B-tree storage engine internals referenced but not rebuilt in §4.6** -> [`../../database/storage_engines_internals/README.md`](../../database/storage_engines_internals/README.md)
- **Circuit breakers, retries with backoff/jitter for the smart client and quorum coordinator (§4.2, §7, §8)** -> [`../resilience_patterns/README.md`](../resilience_patterns/README.md)
- **Consensus-based metadata services (ZooKeeper/etcd-style) for the CP alternative in §5 and §11** -> [`../consensus_algorithms/README.md`](../consensus_algorithms/README.md)
- **RED-method metrics, alerting thresholds, and freshness/lag dashboards underlying §8's key metrics and runbooks** -> [`../observability/README.md`](../observability/README.md)
- **Caching tier in front of hot keys, complementing (not replacing) the replication factor's headroom (§2)** -> [`../caching/README.md`](../caching/README.md)
- **Saga-based application-level transactions for multi-key operations explicitly out of scope for this store (§1)** -> [`../distributed_transactions/README.md`](../distributed_transactions/README.md)

This case study is deliberately scoped to the storage layer's replication, partitioning, and consistency mechanics — the cross-references above are the on-ramps to the adjacent layers (query patterns, transactions, caching, observability) that a production deployment composes around this core.
