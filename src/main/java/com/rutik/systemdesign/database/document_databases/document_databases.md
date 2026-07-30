# Document Databases

<!-- study-paths
senior: document_databases.md
files this module contributes to each curated path; omit a tier to leave it out
-->
## 1. Concept Overview

Document databases store data as semi-structured documents (typically JSON/BSON), allowing each document to have a different structure. The document model is natural for hierarchical data and objects that map directly to application code. MongoDB is the dominant document database, with WiredTiger as its storage engine since 3.2.

---

## 2. Intuition

A document database is like a filing cabinet where each folder (document) can contain any papers (fields) in any format — unlike a relational table where every row must have the same columns. Documents eliminate the impedance mismatch between object-oriented application code and normalized relational tables.

- **Key insight**: The embedding vs referencing decision is the most critical design choice in document databases. Get it wrong and you face either 16MB document limits or N+1 equivalent query patterns.

---

## 3. Core Principles

### MongoDB Architecture

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    P(["Primary"]) --> WT["WiredTiger engine<br/>B+tree + LSM-tree hybrid"]
    WT --> J["Journaling WAL<br/>durability"]
    WT --> C["Cache<br/>50% of RAM-1GB"]
    WT --> DL["Doc limit<br/>16MB"]
    P -.->|"oplog async replication"| S1(["Secondary 1"])
    P -.->|"oplog async replication"| S2(["Secondary 2"])

    class P train
    class WT,J,C,DL base
    class S1,S2 frozen
```
*The primary's WiredTiger engine journals every write and caches dirty pages (50% of RAM minus 1GB by default), capped at 16MB per document; secondaries replicate asynchronously by tailing the oplog — a capped collection that new replicas copy on join and that change streams (CDC) also tail.*

### Embedding vs Referencing Decision Matrix

| Factor | Embed | Reference |
|--------|-------|-----------|
| Relationship cardinality | One-to-few (< 16 sub-docs) | One-to-many (> 100) |
| Access pattern | Always access together | Access separately |
| Sub-document update frequency | Low (re-write parent) | High (update independently) |
| Sub-document size | Small (total < 16MB) | Large |
| Example | User → addresses | Blog post → comments |

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Q{"Relationship<br/>cardinality?"} -->|"one-to-few<br/>under 16 sub-docs"| A1{"Always accessed<br/>together, low<br/>update freq?"}
    Q -->|"one-to-many<br/>over 100"| REF["Reference<br/>e.g. post to comments"]
    A1 -->|"yes"| EMB["Embed<br/>e.g. user to addresses"]
    A1 -->|"no, updates often<br/>or too large"| REF

    class Q,A1 mathOp
    class EMB train
    class REF frozen
```
*This is the single most critical design choice in document databases: start from relationship cardinality, then check access pattern and update frequency — get it wrong and you hit either the 16MB document limit or an N+1-style query pattern.*

```javascript
// Embed (good for one-to-few, always accessed together):
{
  "_id": ObjectId("..."),
  "name": "Alice Smith",
  "addresses": [  // Embedded array
    { "type": "home", "city": "NYC", "zip": "10001" },
    { "type": "work", "city": "NYC", "zip": "10002" }
  ]
}

// Reference (good for one-to-many, accessed independently):
// users collection:
{ "_id": ObjectId("user1"), "name": "Alice" }

// comments collection:
{ "_id": ObjectId("comment1"), "user_id": ObjectId("user1"),
  "post_id": ObjectId("post1"), "body": "Great post!" }
// Query: db.comments.find({post_id: ObjectId("post1")}) — indexed lookup
```

**In plain terms.** The 16MB document limit converts the embed/reference decision into arithmetic:
"a parent document has a fixed byte budget, so `max_children = 16MB / bytes_per_child` — and if your
cardinality can exceed that, embedding is not a style choice you will get to keep." The limit is a
hard server-side error, not a warning, so the number you must know is the divisor.

| Symbol | What it is |
|--------|------------|
| 16MB | `16 x 1024 x 1024 = 16,777,216` bytes. BSON's hard per-document ceiling |
| bytes/child | Serialised size of one embedded sub-document, field names included |
| `max_children` | `16,777,216 / bytes_per_child` — the cardinality at which inserts start failing |
| "< 16 sub-docs" | The matrix's *embed* heuristic. Unrelated to the 16MB figure despite the shared 16 |

**Walk one example.** The Section 14 e-learning schema, sized before it was built:

```
  one enrollment sub-document
    student_id  ObjectId                       12 bytes
    enrolled_at ISODate                          8
    progress    double                           8
    BSON field names + type bytes + overhead   ~170
    total                                      ~200 bytes

  budget          16,777,216 / 200            =  83,886 enrollments per course

  the actual course              50,000 enrollments
    projected size  50,000 x 200              =  10,000,000 bytes = 9.54 MB
    headroom left   16,777,216 - 10,000,000   =   6.46 MB  ->  33,886 more students

  add one field, e.g. a 150-byte "last_quiz_scores" array, to each sub-document:
    bytes/child     350                       ->  max_children = 47,934
    the SAME 50,000-student course   50,000 x 350  =  17,500,000 bytes = 16.69 MB
    17,500,000 > 16,777,216                   ->  BSONObjectTooLarge
```

**Why this is the failure mode teams hit.** The document worked for years at 50,000 x 200 bytes.
Nothing about the cardinality changed — a *schema* change did, and the limit was breached by adding
a field, not a student. That is why the practice rule is "switch to referencing at 1MB", far below
the actual ceiling: the 16x margin absorbs the schema growth you have not thought of yet.

---

## 4. Types / Architectures / Strategies

### Aggregation Pipeline

MongoDB's aggregation framework processes documents through a pipeline of stages:

```javascript
db.orders.aggregate([
  // Stage 1: Filter early (reduces documents flowing to next stages)
  { $match: { status: "completed", date: { $gte: new Date("2024-01-01") } } },

  // Stage 2: Join with products collection ($lookup = left outer join)
  { $lookup: {
    from: "products",
    localField: "product_id",
    foreignField: "_id",
    as: "product"
  }},

  // Stage 3: Unwind array from lookup
  { $unwind: "$product" },

  // Stage 4: Group and aggregate
  { $group: {
    _id: "$product.category",
    total_revenue: { $sum: { $multiply: ["$quantity", "$product.price"] } },
    order_count: { $sum: 1 }
  }},

  // Stage 5: Sort result
  { $sort: { total_revenue: -1 } },

  // Stage 6: Limit output
  { $limit: 10 }
]);

// Performance tip: $match and $project early to reduce data flowing through pipeline
// Index on: { status: 1, date: 1 } for the $match stage
```

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    ALL(["orders<br/>collection"]) --> M{"$match<br/>status, date"}
    M --> L["$lookup<br/>join products"]
    L --> U["$unwind<br/>product array"]
    U --> G["$group<br/>by category"]
    G --> S["$sort<br/>by revenue"]
    S --> LM(["$limit 10"])

    class ALL,LM io
    class M mathOp
    class L,U,G,S base
```
*Order matters: an indexed `$match` shrinks the document set before the expensive `$lookup` join and `$group` aggregation run, so filtering early means `$lookup`/`$group` process far fewer documents than filtering late would.*

### Indexing in MongoDB

```javascript
// Compound index:
db.orders.createIndex({ customer_id: 1, created_at: -1 });

// Multikey index (on array fields): automatically created
db.products.createIndex({ tags: 1 });
// Query: db.products.find({ tags: "electronics" }) — uses multikey index

// Text index (full-text search):
db.articles.createIndex({ title: "text", body: "text" });
db.articles.find({ $text: { $search: "database internals" } });

// 2dsphere (geospatial):
db.stores.createIndex({ location: "2dsphere" });
db.stores.find({ location: { $near: { $geometry: { type: "Point", coordinates: [-73.9, 40.7] }, $maxDistance: 1000 } } });

// TTL index (auto-expire documents):
db.sessions.createIndex({ expires_at: 1 }, { expireAfterSeconds: 0 });
// Documents with expires_at in the past are automatically deleted

// Partial index:
db.users.createIndex({ email: 1 }, { partialFilterExpression: { status: "active" } });

// Sparse index (only indexes documents that have the field):
db.users.createIndex({ phone: 1 }, { sparse: true });

// Wildcard index (MongoDB 4.2+):
db.products.createIndex({ "attributes.$**": 1 });
// Indexes all fields under "attributes" dynamically
```

### Sharding

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    SK{"Shard key<br/>choice"} -->|"range, e.g. order_id"| RG["Range sharding<br/>good ranges,<br/>hotspot-prone"]
    SK -->|"hash of key"| HS["Hash sharding<br/>uniform,<br/>poor range queries"]
    SK -->|"low cardinality<br/>e.g. status"| BAD1["2 shards max<br/>can't scale"]
    SK -->|"monotonic<br/>e.g. timestamp"| BAD2["all writes to<br/>one shard"]
    SK -->|"user_id or<br/>region+user_id"| GOOD["uniform writes,<br/>scales cleanly"]

    class SK,RG,HS mathOp
    class BAD1,BAD2 lossN
    class GOOD train
```
*Shard key choice drives scalability: range and hash sharding route documents differently, but low-cardinality or monotonically increasing keys create write hotspots on one shard, while a high-cardinality key like `user_id` or a compound `(region, user_id)` spreads writes evenly. Jumbo chunks — ranges that outgrow the 128MB default range size and cannot be split further — monitor with `sh.status()` and fix via `clearJumboFlag` or a shard-key change.*

### Change Streams

MongoDB change streams provide real-time notifications of data changes:

```javascript
// Watch a collection for changes (CDC):
const changeStream = db.orders.watch([
  { $match: { "operationType": { $in: ["insert", "update"] } } }
]);

changeStream.on("change", (change) => {
  console.log("Change:", change.operationType, change.fullDocument);
  // Use for: cache invalidation, event sourcing, audit, real-time sync
});

// Change streams are built on oplog tailing
// Resumable: store change.id (resume token) and resume from that point after reconnect
// Requires replica set or sharded cluster (not standalone)
```

### Multi-Document Transactions (MongoDB 4.0+)

```javascript
const session = client.startSession();
session.startTransaction({
  readPreference: 'primary',
  readConcern: { level: 'snapshot' },
  writeConcern: { w: 'majority' }
});
try {
  await db.accounts.updateOne(
    { _id: userId1 }, { $inc: { balance: -100 } }, { session }
  );
  await db.accounts.updateOne(
    { _id: userId2 }, { $inc: { balance: 100 } }, { session }
  );
  await session.commitTransaction();
} catch (e) {
  await session.abortTransaction();
} finally {
  await session.endSession();
}
// Cost vs a single-document write: snapshot setup, per-transaction state,
// one grouped oplog entry at commit, and a majority commit round trip
// Multi-document transactions in MongoDB use WiredTiger MVCC
// Recommended only when cross-document atomicity is truly required
```

---

## 5. Architecture Diagrams

**MongoDB Replica Set**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    CL(["Client<br/>write"]) --> PR["Primary"]
    PR -.->|"oplog async replication"| S1["Secondary 1<br/>reads, failover"]
    PR -.->|"oplog async replication"| S2["Secondary 2<br/>reads, failover"]

    class CL io
    class PR train
    class S1,S2 frozen
```
*Writes always go to the primary; secondaries replicate asynchronously by tailing the oplog and serve reads or stand ready for failover.*

```mermaid
stateDiagram-v2
    [*] --> Healthy
    Healthy --> Healthy: heartbeat ok
    Healthy --> Detecting: heartbeat timeout 10s
    Detecting --> Electing: Raft-like protocol
    Electing --> NewPrimary: majority vote
    NewPrimary --> Healthy: old primary rejoins as secondary
```
*On a missed heartbeat (10s timeout) the replica set runs a Raft-like election; the newly elected primary takes writes while the old primary rejoins as a secondary.*

```
Read Preferences:
  primary          → Always read from primary (strong consistency)
  primaryPreferred → Read from primary if available, else secondary
  secondary        → Always read from secondary (stale reads allowed)
  nearest          → Read from nearest node (lowest latency)
  secondaryPreferred → Read from secondary if available
```

**Sharded Cluster**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    CL2(["Client"]) --> MG{"mongos<br/>query router"}
    MG --> SH1["Shard 1<br/>replica set"]
    MG --> SH2["Shard 2<br/>replica set"]
    MG --> SH3["Shard 3<br/>replica set"]
    CFG[["Config Servers<br/>metadata + chunk map"]] -.-> MG

    class CL2 io
    class MG mathOp
    class SH1,SH2,SH3 base
    class CFG frozen
```
*mongos routes each query using the chunk map from the config servers (a replica set, 3 nodes minimum) — each shard is itself a replica set, so the shard key strategy decides which shard's chunk range a document lands in.*

---

## 6. How It Works — Detailed Mechanics

### WiredTiger Cache and Journaling

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    W(["Write"]) --> J["Journal WAL<br/>sync ~100ms"]
    W --> CA["Cache<br/>50% of RAM-1GB<br/>dirty pages"]
    J --> DK["Disk"]
    CA -->|"checkpoint<br/>every 60s"| DK
    DK -.->|"crash: replay journal<br/>from last checkpoint"| RC(["Recovered"])

    class W io
    class J,CA base
    class DK frozen
    class RC train
```
*Every write lands in both the journal (synced every 100ms by `storage.journal.commitIntervalMs`, or immediately when a write carries `j: true`) and the WiredTiger cache; dirty cache pages checkpoint to disk at a fixed 60-second interval, and a crash recovers by replaying the journal from the last checkpoint.*

### Read Concern — The Other Half of the Durability Contract

Write concern decides when a write is *acknowledged*; read concern decides what a read is
*allowed to see*. They are independent dials, and getting the write side right while
leaving the read side at its default is a very common half-fix. The default read concern
is `"local"`, which returns whatever the queried node has — including writes that a
failover could still roll back.

| Level | What it returns | Cost / restriction |
|-------|-----------------|--------------------|
| `local` | Whatever this node has. Default. May be rolled back on failover | Cheapest |
| `available` | Same, and on a sharded cluster skips the orphan-document filter | Fastest sharded read; not usable in transactions or causal sessions |
| `majority` | Only data acknowledged by a majority — durable, never rolled back | Waits for the majority commit point |
| `linearizable` | Reflects every majority-acknowledged write that finished before the read began | Primary only; barred from transactions and causal sessions; always pair with `maxTimeMS` |
| `snapshot` | Majority-committed data as of one consistent point in time, across shards | Transactions and causally consistent sessions |

The pairing that matters in an interview: **`w: "majority"` plus `readConcern: "local"` is
still a read-your-writes hazard**, because a durable write can be read from a secondary
that has not applied it yet. The two supported fixes are a causally consistent session
(the driver carries a cluster time, and the server waits for the node to catch up) or
`readConcern: "majority"` on the read path.

```javascript
// Read-your-writes across primary and secondaries, without pinning to primary
const session = client.startSession({ causalConsistency: true });
await db.orders.insertOne({ _id: 7, status: "PAID" },
                          { session, writeConcern: { w: "majority" } });
// Same session -> the server will not serve this read from a node behind that write
await db.orders.findOne({ _id: 7 },
                        { session, readConcern: { level: "majority" } });
```

Note the asymmetry with `linearizable`: it is the only level that also rules out a stale
read from a *deposed* primary, which is why it is restricted to the primary and needs a
timeout — it must confirm it is still the primary before answering.

### MongoDB vs Relational Transaction Overhead

```mermaid
xychart-beta
    title "Write latency: MongoDB vs PostgreSQL (ms)"
    x-axis [Mongo-1doc, Postgres-1row, Mongo-txn, Postgres-txn]
    y-axis "Latency (ms)" 0 --> 16
    bar [0.55, 2.55, 9, 5.5]
```
*Illustrative bands, not benchmark figures. Single-document MongoDB writes (about 0.1 to 1ms) are comparable to single-row PostgreSQL updates (about 0.1 to 5ms). A multi-document MongoDB transaction is not a small percentage on top of that: it pays snapshot setup, per-transaction state tracking, one grouped oplog entry written at commit, and a majority commit round trip — so it lands roughly an order of magnitude higher (the 3 to 15ms bar) against PostgreSQL's roughly 1 to 10ms for a simple multi-row transaction. Recommendation: reserve multi-document transactions for genuine cross-document atomicity and default to single-document operations with embedding otherwise.*

---

## 7. Real-World Examples

- **Catalog systems**: MongoDB for product catalogs (varying attributes per product type). Indexing on `category`, `price`, `availability`. Change streams sync to Elasticsearch for full-text search.
- **Content management**: Blog posts with embedded author info and tags. TTL indexes for draft cleanup. Text indexes for search.
- **IoT device data**: Each device document stores recent readings in an embedded array (bounded size). Older readings archived to a separate collection.
- **User profiles**: Flexible schema handles different user types (buyer vs seller vs admin) without nullable columns. Embedded preferences and settings.

---

## 8. Tradeoffs

| Feature | MongoDB | PostgreSQL |
|---------|---------|------------|
| Schema flexibility | Excellent | Poor (requires migrations) |
| Query flexibility | Good (aggregation pipeline) | Excellent (SQL, any join) |
| Multi-document transactions | Available (4.0+, overhead) | Native (lower overhead) |
| Horizontal sharding | Built-in | Requires Citus or manual |
| Full ACID | Yes (with majority write concern) | Yes |
| Full-text search | Basic (text index) | Good (tsvector + GIN) |
| Mature ecosystem | Yes | Yes |
| JSON support | Native (BSON) | JSONB (also excellent) |

---

## 9. When to Use / When NOT to Use

**Use MongoDB when**:
- Schema varies significantly per document type (product catalog, CMS, user profiles)
- Object model maps naturally without normalization (embedded documents avoid joins)
- Need horizontal scaling built in (sharding)
- Team uses document-oriented thinking (JSON APIs)

**Do not use MongoDB when**:
- Complex joins between many entity types (relational is better)
- Heavy aggregation with cross-collection joins at query time (data warehouse better)
- Financial systems where full ACID on multiple collections is required (PostgreSQL is simpler)
- Simple key-value access patterns (Redis is faster)

---

## 10. Common Pitfalls

**Pitfall 1: Unbounded embedded arrays**
```javascript
// Broken: embed all comments in post document
{ _id: "post1", comments: [...10000 comments...] }
// At ~500 bytes per comment the 16MB ceiling lands near 33,000 comments —
// and long before that, every new comment rewrites the whole document

// Fix: reference model
{ _id: "comment1", post_id: "post1", body: "...", created_at: ISODate(...) }
// Index: { post_id: 1, created_at: -1 }
// Query: db.comments.find({post_id: "post1"}).sort({created_at:-1}).limit(20)
```

**Pitfall 2: Missing index on shard key**
A sharded collection's shard key must be indexed (required). Additionally, all queries in a sharded cluster that don't include the shard key result in scatter-gather: the query fan-out goes to ALL shards. For a 10-shard cluster, a non-shard-key query hits 10 shards × the query cost = 10x overhead. Always include the shard key in hot query predicates.

**Pitfall 3: Using $where or JavaScript in queries**
`db.users.find({$where: "this.age > 18"})` executes JavaScript per document — no index, full collection scan, single-threaded JavaScript engine. Always use native MongoDB query operators instead.

**Pitfall 4: Downgrading write concern to w:1 for throughput**
The implicit default is `w: "majority"`, which also implies journaling because `writeConcernMajorityJournalDefault` is true — so a fresh deployment is durable out of the box. Teams then override it to `w: 1` chasing write latency, and `w: 1` acknowledges as soon as the primary has the write in memory, not once it is journaled or replicated. If the primary crashes immediately after the ack, that write is rolled back on failover. Keep `w: "majority"` for anything financial; the one replica-set shape where MongoDB itself falls back to `w: 1` is a set with arbiters where the data-bearing voting members do not form a majority.

**Pitfall 5: Not monitoring oplog size and replication lag**
The oplog is a capped collection. If a secondary falls too far behind (e.g., slow network, heavy write burst), it may fall off the oplog — it can no longer replicate. Fix: monitor replication lag (`rs.printReplicationInfo()`). Set `oplogSizeMB` large enough to cover at least 24 hours of operations. Alert when lag > 30 seconds.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| MongoDB Compass | GUI for queries, schema analysis, explain plans |
| `explain("executionStats")` | Query plan and actual execution stats |
| `db.currentOp()` | Active operations, long-running queries |
| `mongotop` | Top collection activity (reads/writes per collection) |
| `mongostat` | Server-wide stats (ops/s, connections, memory) |
| `mongodump/mongorestore` | Logical backup/restore |
| Atlas | Managed MongoDB (AWS/Azure/GCP), auto-sharding, atlas search |
| Mongoose (Node.js) | ODM for MongoDB with schema validation |
| Spring Data MongoDB | Java ODM for MongoDB |
| Debezium MongoDB connector | CDC from MongoDB change streams |

---

## 12. Interview Questions with Answers

**Q: How do you choose a shard key in MongoDB and what are the consequences of a bad one?**
**Short:** A good MongoDB shard key needs high cardinality and even write distribution, since a monotonically increasing key sends all inserts to one shard.

A good shard key has high cardinality, spreads writes evenly across shards, and colocates documents that are queried together. High cardinality means enough distinct values to fill every shard; good write distribution means avoiding monotonically increasing keys like timestamps, which send every insert to one shard; colocation means a compound key such as `{region, user_id}` keeps one user's data on one shard. Bad shard key consequences: monotonically increasing key → all inserts go to the last shard (insert hotspot, all other shards idle); low cardinality (e.g., status) → only a few possible shards, cannot scale beyond cardinality count; querying without shard key → scatter-gather to all shards (10x overhead for 10-shard cluster).

**Q: When should you embed documents vs reference them in MongoDB?**
**Short:** Embed one-to-few data that is always read with its parent; reference one-to-many or independently-changing data to avoid document bloat.

Embed when: the data has a one-to-few relationship (user has 2-5 addresses), the embedded data is always accessed with the parent (user profile always needs address), and the total document size stays well under 16MB. Reference when: the data has a one-to-many or many-to-many relationship (post has thousands of comments), the child data changes frequently and independently (updating one comment shouldn't rewrite the entire post), or the child data is large enough to risk document size limits. General rule: start with embedding, switch to referencing when documents exceed 2MB consistently or when update patterns become problematic.

**Q: How do MongoDB transactions compare to PostgreSQL in terms of overhead?**
**Short:** MongoDB multi-document transactions cost several times a single-document write, while PostgreSQL keeps per-operation transaction overhead lower.

MongoDB multi-document transactions use WiredTiger's MVCC to provide snapshot isolation, and cost several times a single-document write rather than a small percentage on top of it. They arrived in 4.0 for replica sets and 4.2 for sharded clusters. The overhead comes from starting an MVCC snapshot, tracking per-transaction state, writing the transaction's changes as one grouped oplog entry at commit, and waiting on the majority commit round trip. PostgreSQL single transactions: ~1-10ms, similar MVCC mechanism but more mature implementation with less overhead per operation. For cross-document atomicity requiring consistency, PostgreSQL transactions are generally faster for complex multi-table operations because its cost-based planner handles joins better than MongoDB's $lookup + $match pipeline. MongoDB's advantage: single-document operations (no transaction) are very fast and naturally atomic at the document level.

**Q: Explain how MongoDB change streams work and what they can be used for.**
**Short:** Change streams expose MongoDB's oplog as a resumable, filterable event stream for CDC, notifications, and cache invalidation.

Change streams use MongoDB's oplog as the source of truth. The oplog records all write operations to replica set members. Change streams expose this as a subscribable stream with filtering, resumability, and full document pre/post images. Use cases: (1) CDC to sync MongoDB data to Elasticsearch, Redis, or data warehouses. (2) Real-time notifications (user gets notified when their order status changes). (3) Cache invalidation (when a product document changes, invalidate Redis cache). (4) Audit logging (every change recorded). Resumability: the change stream returns a `resumeToken` with each event. On reconnect, pass the token to resume exactly from where it left off — no events missed. Requires: replica set or sharded cluster (change streams read the oplog).

**Q: What is the WiredTiger cache and how does it differ from PostgreSQL's buffer pool?**
**Short:** WiredTiger's cache defaults to about half of RAM minus 1GB, compresses pages, and is per-process unlike PostgreSQL's shared buffer pool.

WiredTiger cache (default: the larger of 50% of (RAM - 1GB) and 256MB — so a 64GB host gets 31.5GB, not 32GB): stores recently accessed pages from data files, including B+tree nodes for indexes and document pages. Similar to PostgreSQL's `shared_buffers`. Key differences: (1) WiredTiger uses a CLOCK eviction algorithm instead of PostgreSQL's clock-sweep. (2) WiredTiger compresses pages both in cache and on disk (Snappy compression by default). (3) WiredTiger cache is per-process (the mongod process), while PostgreSQL's shared_buffers is shared across all connections. (4) On Linux, the OS page cache also caches MongoDB data files — double-buffering similar to PostgreSQL. Set `storage.wiredTiger.engineConfig.cacheSizeGB` to tune.

**Q: How does MongoDB replication differ from PostgreSQL streaming replication?**
**Short:** MongoDB replication re-executes logical oplog operations on secondaries, while PostgreSQL streams physical WAL byte changes.

MongoDB replication: each write is recorded in the oplog (a capped collection of logical operations). Secondaries tail the oplog and re-execute operations. Replication is logical (re-execute operations), not physical (copy byte changes). This allows filtering, selective replication, and cross-version compatibility. Failover: Raft-like election (majority of nodes must agree on new primary). PostgreSQL streaming replication: physical WAL stream (byte-level page changes). Faster replication (no re-execution overhead), but requires same major version. Logical replication (PostgreSQL) is the equivalent of MongoDB's oplog for cross-version compatibility. Both support multi-region, but PostgreSQL's physical replication is typically lower-latency for identical hardware/version.

**Q: What are TTL indexes in MongoDB and when do you use them?**
**Short:** A TTL index automatically deletes documents whose date field has aged past a configured number of seconds, ideal for sessions and caches.

A TTL (Time-To-Live) index on a BSON date field causes MongoDB to automatically delete documents where the date field is older than the specified time. `db.sessions.createIndex({expires_at: 1}, {expireAfterSeconds: 0})` — documents where `expires_at < now()` are deleted. Use cases: session storage, cache documents, soft-expiring events, rate limiting records. Background: TTL expiration runs in a background thread every 60 seconds; for each TTL index it deletes until it has removed 50,000 documents, or spent one second on that index, or exhausted the expired set — then moves to the next index (so deletions can lag under high insertion rates). Limitation: TTL index must be on a Date field; the `expireAfterSeconds` is relative to the field's value (or can be 0 to use the field value as the absolute expiry time).

**Q: What is the oplog and why does its size matter?**
**Short:** The oplog's fixed size matters because a secondary that falls too far behind before catching up needs a full resync instead of resuming.

The oplog (operations log) is a capped collection in the `local` database that records all write operations in a replication set. Secondaries tail the oplog to apply changes. Capped collection: has a fixed size in bytes; old entries are overwritten when full. Why size matters: if a secondary falls behind (network partition, slow network, heavy write burst) and the oplog wraps around before the secondary reads those entries, the secondary can no longer catch up — it needs a full resync. Recommended oplog size: able to hold at least 24-72 hours of write operations. Default: 5% of free disk space, floored at 990MB and capped at 50GB (that cap is often too small for high-write deployments). Set with `mongod --oplogSizeMB`, the `replication.oplogSizeMB` YAML key, or resize a running node with `db.adminCommand({replSetResizeOplog: 1, size: <MB>})`.

**Q: How does MongoDB schema validation work?**
**Short:** MongoDB's $jsonSchema validator enforces required fields and types server-side on every insert and update in strict mode.

MongoDB 3.6+ supports JSON Schema validation on collections. Define a schema with required fields, types, and constraints:
```javascript
db.createCollection("users", {
  validator: { $jsonSchema: {
    bsonType: "object",
    required: ["email", "created_at"],
    properties: {
      email: { bsonType: "string", pattern: "^.+@.+$" },
      age: { bsonType: "int", minimum: 0, maximum: 150 }
    }
  }},
  validationLevel: "strict",   // "strict" (default) = every insert and update validated
                               // "moderate" = inserts, plus updates to documents that
                               //              ALREADY satisfy the rules; updates to
                               //              pre-existing invalid documents are exempt
  validationAction: "error"    // "error" = reject invalid; "warn" = allow with warning
});
```
Validation runs on INSERT and UPDATE and is enforced server-side by `mongod` — the shell cannot bypass it, and neither can a driver. `moderate` exists specifically so you can add a schema to a collection full of legacy documents without breaking writes to the non-conforming ones; it is not "new documents only". Use it as a safety net for required fields and basic type constraints.

**Q: Explain MongoDB's aggregation pipeline performance optimization.**
**Short:** Aggregation pipelines run fastest when $match and $project run early and $limit is placed right after $sort.

The pipeline processes documents stage by stage. Optimization rules: (1) Put `$match` as early as possible — it reduces the number of documents flowing to later stages. If the match uses an indexed field, it avoids a collection scan. (2) Put `$project` early to remove fields not needed downstream — reduces document size in memory. (3) Put `$limit` immediately AFTER `$sort` when you only need top-N — the optimizer coalesces the pair into a single sort that keeps only n items in memory. Putting `$limit` first would truncate an arbitrary set of documents and then sort those, which is a different query. (4) `$lookup` is expensive (cross-collection join) — filter before it to minimize the joined set. (5) The per-stage memory limit is 100MB; since MongoDB 6.0 the `allowDiskUseByDefault` parameter decides whether an over-limit stage spills to temporary files or raises an error, and `{ allowDiskUse: false }` / `{ allowDiskUse: true }` overrides it for one command. (6) Use `$facet` for multiple aggregations in one pipeline pass instead of separate queries. Check performance with `.explain("executionStats")` on the aggregate call.

**Q: What is MongoDB Atlas Search and when should you use it instead of a text index?**
**Short:** Atlas Search adds Lucene-based relevance scoring, fuzzy matching, and facets that MongoDB's native text index cannot provide.

MongoDB Atlas Search (built on Lucene) provides full-text search with relevance scoring, fuzzy matching, phrase matching, facets, and autocomplete — features missing from MongoDB's native text indexes. Native text index limitations: all fields weighted equally (unless specified), no fuzzy matching, no autocomplete, no custom analyzers, limited relevance scoring, no facets. It does handle exact phrases, via escaped double quotes (`$search: "\"ssl certificate\""`), so phrase matching is not the reason to leave it. Atlas Search: Lucene-based, full BM25 scoring, facets, fuzzy matching (`fuzziness` parameter), autocomplete, search-as-you-type, synonym groups, custom analyzers. Trade-off: Atlas Search requires Atlas (managed MongoDB) and is eventually consistent (replication delay of ~1-2 seconds for indexed changes). Use Atlas Search when: advanced relevance ranking needed, user-facing search with autocomplete, faceted navigation. Use native text index for: simple keyword matching, self-hosted MongoDB, cases where search is not a primary use case.

**Q: When would you use MongoDB's $lookup instead of embedding and what are the limitations?**
**Short:** $lookup performs a left outer join across collections but cannot use sharding to colocate data, making it expensive on sharded sources.

`$lookup` performs a left outer join between collections in an aggregation pipeline. Use when: referencing is the right data model (one-to-many relationship where child data is large or changes independently), but you need to fetch related data in one query. Limitations: (1) `$lookup` cannot use sharding to colocate data — if the "from" collection is sharded, every lookup requires scatter-gather to all shards (expensive). (2) `$lookup` returns an array of matching documents — must use `$unwind` to join cardinality. (3) The equality-match form does use an index on `foreignField`, and MongoDB's docs warn that without one performance "will likely be poor" — but the correlated-subquery form with `$expr` only uses an index when the `let` operand resolves to a constant, and never uses a multikey, partial or sparse index. (4) Performance degrades significantly at scale vs an embedded approach or pre-computed joins. Use embedding when: performance is critical and data fits in document size limits.

**Q: What is the write concern in MongoDB and when should you use w: majority?**
**Short:** w: 'majority' guarantees a write survives failover by requiring replica-set majority acknowledgment, at the cost of one replication round trip.

Write concern specifies how many replica set members must acknowledge a write before the driver considers it successful. Options: `w: 'majority'` (the implicit default — a majority of replica set members must acknowledge, and journaling is implied by `writeConcernMajorityJournalDefault`), `w:1` (primary only), `w: N` (specific N members). `w:1` risk: if primary crashes before replicating to any secondary, the write is lost on failover (rollback). `w: 'majority'` guarantees that even if the primary crashes and a new primary is elected, the write was replicated to a majority — it will be present. Latency cost: `w: 'majority'` adds one replication round trip (~1-10ms LAN, ~30-100ms WAN). Use `w: 'majority'` for: financial transactions, user account changes, any data that cannot afford to be lost. Use `w:1` for: analytics events, logs, metrics where occasional loss is acceptable.

**Q: Why is `w: "majority"` alone not enough for read-your-writes, and what read concern fixes it?**
**Short:** Write concern controls durability while read concern controls staleness, so majority-durable writes can still be read from a stale secondary.

Write concern controls when a write is acknowledged; read concern controls what a read may see, and the default read concern is `local`. So a write can be majority-durable while a subsequent read is served by a secondary that has not applied it yet — the write is safe, the read is stale, and no write concern setting changes that. MongoDB offers five read concerns: `local` (default, may be rolled back), `available` (same, plus it skips the orphan filter on sharded reads, and is unusable inside transactions or causal sessions), `majority` (only majority-acknowledged, therefore never rolled back), `linearizable` (reflects every majority-acknowledged write that completed before the read started, primary only, always pair with `maxTimeMS`), and `snapshot` (a single consistent point in time across shards, for transactions and causally consistent sessions). The two supported fixes are a causally consistent session, where the driver carries a cluster time and the server refuses to answer from a node behind that write, or `readConcern: "majority"` on the read path. Reach for `linearizable` only when you must also exclude a stale answer from a primary that has already been deposed — that guarantee is exactly why it costs a round trip to confirm primacy.

**Q: What happens when you embed an unbounded array in a MongoDB document, like comments on a post?**
**Short:** An unbounded embedded array eventually exceeds MongoDB's 16MB document limit and makes every small update rewrite the whole document.

An embedded array that grows without bound eventually pushes the document past MongoDB's 16MB hard limit, and every insert past that point fails outright with a BSONObjectTooLarge error. A course document that embedded all of its student enrollments hit this ceiling at around 50,000 embedded entries, and worse, every update to a single student's progress had to rewrite the entire multi-megabyte document even before the limit was reached. Switching to a reference model — a separate `enrollments` collection with a compound index on `(course_id, student_id)` — dropped a single-student update from over 10ms to about 0.1ms, because it now touches a document a few hundred bytes wide instead of the whole parent. Bound any embedded array explicitly, and move genuinely unbounded one-to-many relationships into a referenced collection from the start.

**Q: What do MongoDB's read preference modes trade off, and when is `secondary` unsafe?**
**Short:** MongoDB read preference trades staleness for latency, and 'secondary' unsafely throws rather than falling back when no secondary is available.

Read preference chooses which replica set member serves a read, trading staleness against latency and primary load. `primary` always reads the latest write; `secondaryPreferred` shifts load off the primary but can return data that lags by the current replication delay; `nearest` picks the lowest-latency member regardless of role. `secondary` forces every read onto a secondary even if all secondaries are down, which throws rather than silently falling back to the primary — a real risk if a maintenance window takes secondaries offline. `primaryPreferred` and `secondaryPreferred` both have a preferred target with automatic fallback, so they're safer defaults than the non-preferred variants for production traffic. Use `primary` for read-your-writes flows like an order confirmation page, and `secondaryPreferred` or `nearest` for analytics and reporting queries that can tolerate a few seconds of replication lag.

**Q: Why should you avoid `$where` and JavaScript expressions in MongoDB queries?**
**Short:** $where forces a full collection scan by running JavaScript per document instead of letting the query planner use an index.

`$where` executes a JavaScript expression against every document using a single-threaded JavaScript engine, so it cannot use an index and forces a full collection scan. A query like `db.users.find({$where: "this.age > 18"})` runs interpreted JavaScript per document instead of the native comparison MongoDB's query planner can optimize with a B-tree index, and on a large collection this is dramatically slower while also blocking other JavaScript-engine work since it's single-threaded. There is effectively no query `$where` can express that a native operator can't, so the performance cost buys nothing in return. Use native query operators, such as `{age: {$gt: 18}}`, which the planner can satisfy with an index in logarithmic time instead of a linear scan.

---

## 13. Best Practices

1. Default to embedding; switch to referencing when documents grow beyond 1MB or update patterns become complex.
2. Limit embedded arrays to a bounded size; use references for unbounded relationships.
3. Index all fields used in query predicates and sort operations.
4. Include the shard key in all frequently-run queries to avoid scatter-gather.
5. Use `w: 'majority'` and `j: true` for writes that cannot be lost.
6. Monitor oplog size: ensure it can hold 48+ hours of write operations.
7. Use change streams for CDC instead of periodic polling.
8. Avoid multi-document transactions when single-document atomicity can be designed in.
9. Use Atlas Search for user-facing search; native text index for basic keyword matching.
10. Run `db.collection.explain("executionStats")` on all query patterns during development.

---

## 14. Case Study

**Scenario**: An e-learning platform uses MongoDB to store courses. Original design embedded all student enrollments in the course document:

```javascript
{
  "_id": ObjectId("course1"),
  "title": "Database Internals",
  "enrollments": [
    { "student_id": "s1", "enrolled_at": ISODate(), "progress": 0.45 },
    ... // 50,000 students
  ]
}
```

**Problem**: After 50,000 enrollments, course document exceeded 16MB. New enrollments failed with `BSONObjectTooLarge`. Queries updating individual student progress rewrote the entire 16MB document.

**Redesign**:
```javascript
// courses collection (lightweight):
{ "_id": ObjectId("course1"), "title": "Database Internals", "instructor_id": "i1",
  "enrollment_count": 50000 }

// enrollments collection (references):
{ "_id": ObjectId("e1"), "course_id": ObjectId("course1"), "student_id": ObjectId("s1"),
  "enrolled_at": ISODate("2024-01-15"), "progress": 0.45, "last_accessed": ISODate() }

// Indexes:
db.enrollments.createIndex({ course_id: 1, student_id: 1 }, { unique: true });
db.enrollments.createIndex({ student_id: 1, enrolled_at: -1 });
db.enrollments.createIndex({ course_id: 1, last_accessed: -1 });

// Update progress (now single document update):
db.enrollments.updateOne(
  { course_id: ObjectId("course1"), student_id: ObjectId("s1") },
  { $set: { progress: 0.50, last_accessed: new Date() } }
);
// 0.1ms vs 10ms+ for the 16MB document update

// Enrollment count: maintained with $inc on courses or via change stream
```

**Result**: Document size capped at ~200 bytes per enrollment. Update operations: 0.1ms vs 10ms. No more size limit errors. The enrollment count is maintained with atomic `$inc` on the course document, preserving the frequently-displayed counter without aggregating.
