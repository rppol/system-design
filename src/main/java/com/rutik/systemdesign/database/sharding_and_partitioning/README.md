# Sharding and Partitioning

## 1. Concept Overview

Sharding (horizontal scaling) splits data across multiple independent database instances so that no single server holds all the data or handles all requests. Partitioning splits data within a single database instance into logical segments (partitions) for query performance and manageability. Both involve dividing data by a key, but sharding crosses server boundaries while partitioning stays within one database.

The fundamental challenge: every design decision that improves write distribution (uniform hashing) tends to worsen query locality (scatter-gather for range queries), and vice versa. Choosing the right shard/partition key is the most consequential architectural decision in a sharded system.

---

## 2. Intuition

Imagine a library that has grown too large for one building. Sharding is putting half the books in a second building — you now have twice the shelf space, but finding a book may require knowing which building it is in. A good sharding scheme (books by author last name: A–M in building 1, N–Z in building 2) makes the lookup predictable. A bad scheme (random assignment) requires asking both buildings every time. Partitioning is like reorganizing shelves within a single building — faster to maintain (vacuum/archive sections), same total capacity.

---

## 3. Core Principles

**Shard key determines data locality**: Every row belongs to exactly one shard based on its shard key. Queries that filter on the shard key go to one shard; queries without the shard key must go to all shards (scatter-gather).

**Uniform data distribution**: A good shard key distributes data and write load evenly. Hotspots (one shard receiving 90% of writes) negate horizontal scaling benefits.

**Minimal cross-shard operations**: Joins, foreign keys, and transactions across shards require distributed coordination — expensive and complex. Schema design should minimize cross-shard operations.

**Re-sharding is expensive**: Changing the number of shards typically requires moving large amounts of data. Plan shard count and key for future scale from the beginning.

---

## 4. Types / Architectures / Strategies

```
Strategy            | Mechanism                        | Hotspot Risk | Range Query
--------------------|----------------------------------|--------------|------------
Range sharding      | Key range → shard                | High (skewed)| Efficient
Hash sharding       | Hash(key) % N → shard            | Low          | Scatter-gather
Directory sharding  | Lookup table key → shard         | None (custom)| Depends
Consistent hashing  | Hash ring, virtual nodes         | Low          | Scatter-gather
Geographic sharding | Region/country → shard           | Medium       | Regional only
```

**Partitioning types (within one DB)**:
```
Type      | PostgreSQL syntax                     | Use case
----------|---------------------------------------|----------------------------
Range     | PARTITION BY RANGE (created_at)       | Time-series, date-based
List      | PARTITION BY LIST (region)            | Categorical, small enum
Hash      | PARTITION BY HASH (user_id)           | Even distribution, OLTP
```

---

## 5. Architecture Diagrams

```
Hash Sharding: 4 shards
========================

Write: user_id=12345
  hash(12345) % 4 = 1  →  Shard 1

Write: user_id=99999
  hash(99999) % 4 = 3  →  Shard 3

Read: SELECT * FROM users WHERE user_id = 12345
  hash(12345) % 4 = 1  →  Shard 1 (single-shard read)

Read: SELECT * FROM users WHERE age > 30
  No shard key → scatter to all 4 shards, merge results (scatter-gather)


Consistent Hashing Ring with Virtual Nodes
==========================================

                0
               /\
          315 /  \ 45
             /    \
        270 ──────── 90
             \    /
          225 \  / 135
               \/
               180

Physical nodes: A, B, C (3 nodes)
Virtual nodes per physical node: 150

Node A owns:  [0-45], [90-135], [225-270]
Node B owns:  [45-90], [180-225], [315-360]
Node C owns:  [135-180], [270-315]

Key "user:12345" → hash = 210 → falls in [180-225] → Node B

Adding Node D: takes ~25% of virtual nodes from A, B, C
  → only ~25% of data moves (vs 50% in naive hash % N resizing)


PostgreSQL Range Partitioning
==============================

CREATE TABLE orders (
    id BIGINT,
    created_at TIMESTAMPTZ,
    amount DECIMAL
) PARTITION BY RANGE (created_at);

CREATE TABLE orders_2024_q1 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');
CREATE TABLE orders_2024_q2 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');
-- etc.

Query with partition pruning:
  SELECT * FROM orders WHERE created_at >= '2024-02-01' AND created_at < '2024-03-01';
  → Planner accesses only orders_2024_q1 (one partition, not all)
  → "Partitions: orders_2024_q1" in EXPLAIN output
```

---

## 6. How It Works — Detailed Mechanics

### Consistent Hashing and Virtual Nodes

Consistent hashing places both data keys and server nodes on a hash ring (0 to 2^32). Each key is assigned to the first node clockwise on the ring. When a node is added, only the keys between the new node and its predecessor need to move — not all keys.

**Virtual nodes (vnodes)**: Without virtual nodes, 3 physical nodes have 3 points on the ring, causing uneven distribution. Virtual nodes assign each physical node ~150 random positions on the ring. Each physical node handles ~150/N of the ring. Distribution is now statistically uniform. Adding a physical node by giving it M virtual nodes from each existing node moves ~M/total_vnodes fraction of data from each existing node.

```
Cassandra defaults: 256 virtual nodes per physical node
DynamoDB: virtual node placement managed internally
Redis Cluster: 16384 hash slots (not traditional consistent hashing)
  - slot = CRC16(key) % 16384
  - Slots evenly distributed across cluster nodes
  - Adding a node: migrate a subset of slots from existing nodes
```

### Vitess: MySQL Sharding Middleware

Vitess adds a sharding layer on top of MySQL. Key components:

```
Architecture:

Client → VTGate (routing proxy) → VTTablet (per MySQL shard) → MySQL

VTGate:
  - Receives queries using MySQL protocol
  - Parses query, reads VSchema (sharding metadata)
  - Routes shard-specific queries to one VTTablet
  - Scatter-gathers for cross-shard queries

VTTablet:
  - Sidecar process per MySQL instance
  - Connection pooling
  - Query rewriting (adding shard filters)
  - Metrics and health checks

MoveTables:
  - Online re-sharding without downtime
  - Copies data from source shards to target shards
  - VReplication tracks change stream from source
  - Cutover = brief read-only window + atomic routing switch
```

```sql
-- VSchema example: shard orders table by customer_id
{
  "sharded": true,
  "vindexes": {
    "hash": { "type": "hash" }
  },
  "tables": {
    "orders": {
      "column_vindexes": [
        { "column": "customer_id", "name": "hash" }
      ]
    }
  }
}
```

### Hotspot Problem and Solutions

A hotspot occurs when one shard receives disproportionately more reads/writes than others:

**Sequential primary key hotspot**: All inserts go to the shard containing the max key value. Fix: UUID, ULID, or hash-sharded sequences.

**Celebrity/firehose hotspot**: User ID 1 (celebrity with 100M followers) generates 1000x more write traffic than average users. A single shard hosts all of user 1's data and gets overwhelmed.

```
Fix options for celebrity hotspot:
1. Write sharding with random suffix:
   key: "post:{user_id}:{random_suffix(0..N)}"
   Write: randomly pick one of N sub-shards
   Read: scatter-gather all N sub-shards, aggregate

2. Dedicated shard for hot tenants:
   user_id 1 → dedicated shard with extra capacity
   Directory-based routing: lookup shard for user_id before routing

3. Tiered fan-out:
   Hot users: fan-out at write time (push to followers)
   Cold users: fan-in at read time (pull from author)
   Threshold: > 10K followers → hot user
```

### PostgreSQL Declarative Partitioning

```sql
-- Hash partitioning for OLTP workload
CREATE TABLE users (
    id BIGINT NOT NULL,
    email TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
) PARTITION BY HASH (id);

CREATE TABLE users_p0 PARTITION OF users FOR VALUES WITH (modulus 4, remainder 0);
CREATE TABLE users_p1 PARTITION OF users FOR VALUES WITH (modulus 4, remainder 1);
CREATE TABLE users_p2 PARTITION OF users FOR VALUES WITH (modulus 4, remainder 2);
CREATE TABLE users_p3 PARTITION OF users FOR VALUES WITH (modulus 4, remainder 3);

-- Each partition can be on different tablespaces (different disks)
-- Indexes must be created on each partition (or use ONLY on parent)

-- Partition pruning in action
EXPLAIN SELECT * FROM users WHERE id = 12345;
-- Output: Seq Scan on users_p1 (12345 % 4 = 1)
-- Only partition p1 accessed

-- Range partition for time-series: archive by dropping old partitions
ALTER TABLE orders DETACH PARTITION orders_2022_q1;
DROP TABLE orders_2022_q1;
-- Much faster than DELETE (no WAL per row, no VACUUM needed)
```

### Cross-Shard Queries and Distributed SQL

```
Cross-shard query patterns:
  Scatter-gather: send query to all shards, merge results in router
    - Cost: O(N shards) network calls + merge overhead
    - Problematic for pagination: must gather from all, sort, offset

  Partition-wise join (PostgreSQL 11+):
    - Planner joins matching partition pairs locally
    - No cross-partition data movement if both tables partitioned on join key

  Application-layer join:
    - Fetch from shard 1, use IDs to query shard 2
    - Explicit, controllable, but verbose code

  Denormalization:
    - Duplicate relevant foreign data into the shard's table
    - Eliminates join at query time, costs storage and sync complexity
```

---

## 7. Real-World Examples

**Instagram**: Sharded PostgreSQL (5000+ logical shards on fewer physical servers) using a home-grown framework (Django-Shard-Library). Shard key = user ID. All user data co-located on one shard to eliminate cross-shard joins for user-centric queries.

**Shopify**: Uses Vitess (MySQL) for sharding. Each shop is assigned to a pod (a group of shards). Shops do not share a shard. Cross-shop operations (e.g., merchant analytics) are handled in a separate reporting database fed by CDC.

**Uber**: Migrated from PostgreSQL to MySQL with a custom sharding framework (Schemaless) for their trip data. Row key = trip UUID. Reads and writes by trip ID always go to one shard.

**Discord**: Messages table uses Cassandra with `channel_id` as partition key and `timestamp` as clustering key. Each channel's messages are co-located on one or more Cassandra nodes.

---

## 8. Tradeoffs

```
Concern              | Single DB + partitioning | Sharded (horizontal)
---------------------|--------------------------|----------------------
Max write throughput | 1 primary's capability   | N primaries' capability
Max dataset size     | Single server RAM+disk   | N servers combined
Cross-partition join | Efficient (local)        | Scatter-gather (expensive)
ACID transactions    | Full                     | Limited to single shard
Operational overhead | Low                      | High (N×operations)
Schema changes       | Single ALTER TABLE       | Apply to N shards
Monitoring           | Single DB to watch       | N DBs, aggregate metrics
Cost                 | Lower                    | Higher
```

---

## 9. When to Use / When NOT to Use

**Use sharding when**: Write throughput exceeds what a single primary can handle (typically > 50K TPS sustained), or dataset size exceeds a single server's storage (> 10TB active), or you need geographic data distribution for latency or compliance.

**Use partitioning without sharding when**: Dataset is large but write throughput is manageable on one primary; you need fast partition-level operations (drop old time partitions, tablespace relocation); query plans benefit from partition pruning.

**Avoid premature sharding**: Most applications never need it. A single PostgreSQL server with read replicas handles millions of users. Sharding adds significant operational complexity — migrate to it only when profiling proves you have exhausted vertical scaling and read replica options.

---

## 10. Common Pitfalls

**Hotspot shard from sequential IDs**: Team migrates to sharded MySQL. They keep `AUTO_INCREMENT` as the shard key. 90% of writes hit the last shard (max ID range). Scale does not improve. Fix on day one: hash-based shard key.

**Cross-shard transaction nightmare**: Application code performs a payment: deduct from user A (shard 1) and credit user B (shard 3). Both operations succeed — but a crash between them leaves the system inconsistent. No 2PC was implemented. Fix: use the outbox pattern or saga pattern; accept eventual consistency for cross-shard operations.

**Re-sharding from 4 to 8 shards at scale**: Team initially shards into 4 databases. After growth, they need 8. Moving 50% of data (each shard splits) takes weeks and requires complex dual-write logic. Fix: use Vitess `MoveTables` for online re-sharding, or use consistent hashing with virtual nodes so shard count is logical and remapping is data-movement-minimal.

**Partition bloat from too many tiny partitions**: Team creates daily partitions for a table with 10M rows/day. After 3 years: 1095 partitions. PostgreSQL's planner overhead for partition pruning with 1000+ partitions becomes significant (~10ms plan time). Fix: use monthly partitions and sub-partition if needed; or use range partitions with rolling windows and detach/drop old partitions automatically.

**Forgetting to create indexes on new partitions**: PostgreSQL CREATE INDEX on parent table creates indexes on existing partitions but not new partitions created later (before PG 11). After PG 11, indexes on parent propagate automatically. Before PG 11: scripts must CREATE INDEX on each new partition at creation time.

---

## 11. Technologies & Tools

| Tool            | Purpose                                      | Use Case
|-----------------|----------------------------------------------|---------------------------
| Vitess          | MySQL sharding middleware                    | Shopify, YouTube-scale MySQL
| Citus           | PostgreSQL sharding extension                | Multi-tenant PostgreSQL
| pg_partman      | Automatic partition management (PG)          | Time-based partition creation/dropping
| ProxySQL        | MySQL query routing + read/write split       | MySQL connection routing
| Amazon DynamoDB | Managed sharded key-value store              | Serverless, auto-scaling
| Cassandra       | Leaderless sharded wide-column store         | Write-heavy, time-series
| MongoDB Atlas   | Managed sharded document store               | Flexible schema sharding

---

## 12. Interview Questions with Answers

**How do you choose a shard key for a multi-tenant SaaS database?**
Use `tenant_id` as the shard key. All data belonging to a tenant is co-located on one shard, eliminating cross-shard joins for the overwhelming majority of queries (which are tenant-scoped). Write distribution depends on tenant size distribution — if a few enterprise tenants generate 80% of writes, use directory-based sharding to assign dedicated shards to large tenants and share a shard among small tenants. Avoid shard keys based on sequential IDs or timestamps — they create hotspots on the most recently written shard.

**Explain consistent hashing and why virtual nodes solve the uneven distribution problem.**
Consistent hashing maps both keys and nodes to a circular ring using a hash function. A key is assigned to the first node clockwise on the ring. When a node joins or leaves, only the keys between it and its predecessor move. Without virtual nodes (3 physical nodes = 3 points on the ring), ring arcs are unequal in size, causing uneven data distribution. With 150 virtual nodes per physical node, each physical node has 150 random positions, making arc lengths statistically uniform. Adding a new node means giving it virtual nodes taken evenly from all existing nodes, moving ~1/N of total data with each addition.

**How does Vitess enable online resharding without downtime?**
Vitess MoveTables copies data from source shards to target shards in the background using VReplication (which reads the MySQL binlog as a change stream). During the copy: (1) Historical data is bulk-copied. (2) Incremental changes are captured via binlog and applied to target. (3) Once the target catches up (lag < 1 second), a brief "traffic cutover" switches reads and writes to the new shards. The cutover involves a short read-only window (typically < 5 seconds) to drain in-flight transactions, then atomically updating VTGate's routing table. The old shards are kept for a rollback window before being decommissioned.

**What is the celebrity shard problem and how do you handle it?**
A celebrity user (10M followers, high activity) generates orders of magnitude more read/write traffic than average users. Since all their data is on one shard, that shard becomes a bottleneck regardless of cluster size. Solutions: (1) Dedicated shard with additional replicas for heavy reads. (2) Write sharding with random suffix keys for fan-out data (posts, activity) — writes go to N sub-shards randomly, reads scatter-gather. (3) Application-level caching (Redis) absorbs celebrity read traffic before it hits the database shard. (4) Hybrid fan-out: pre-compute and push celebrity posts to follower feeds at write time, so feed reads are local to each follower's shard.

**What is the difference between vertical and horizontal partitioning?**
Vertical partitioning splits a table by columns: frequently accessed columns stay in one table, rarely accessed or large columns (e.g., BLOB, large TEXT) move to a separate table joined by primary key. This reduces I/O for queries that don't need the large columns and keeps hot data dense in pages. Horizontal partitioning (sharding) splits a table by rows: all columns for a row stay together, but different rows go to different partitions/shards based on a partition key. Both can be combined: a sharded table can also vertically separate columns (e.g., user profile vs user settings vs user media).

**How do you handle cross-shard joins in a sharded system?**
Options in order of preference: (1) Redesign schema to co-locate related data on the same shard (denormalization, embedding). (2) Application-layer join: fetch data from shard A, extract IDs, query shard B by those IDs (two sequential queries). (3) Scatter-gather: send the join query to all shards, aggregate results in the application or proxy layer. (4) Use a distributed SQL layer (Vitess, Citus) that handles scatter-gather automatically. (5) Maintain a read-optimized denormalized view in a separate query database (Elasticsearch, data warehouse) that is updated via CDC. The goal is to make cross-shard operations rare by aligning shard keys with the primary access pattern.

**What is consistent hashing's key advantage over mod-N hashing when adding nodes?**
With mod-N hashing (`hash(key) % N`), adding a new node changes N, causing almost all keys to remap to different nodes — effectively requiring a full data migration. With consistent hashing, adding a node to the ring moves only the keys in the arc between the new node and its predecessor (approximately 1/N of total keys). For a 10-node cluster adding the 11th node, only ~9% of keys move instead of ~91%.

**How does PostgreSQL partition pruning work and what are its limitations?**
The query planner examines the partition key's constraint exclusion metadata. If a query's WHERE clause includes a condition on the partition key that can exclude certain partitions (e.g., `created_at >= '2024-01-01'` excludes all partitions with upper bounds before 2024), those partitions are not scanned. Limitations: (1) Partition pruning requires the partition key in the WHERE clause — queries without it access all partitions. (2) Dynamic pruning at execution time (for parameterized queries) was added in PostgreSQL 11. (3) With 1000+ partitions, planning overhead increases noticeably. (4) Partitioned table inheritance trees have planner overhead proportional to partition count.

**How do you perform zero-downtime re-partitioning of a large table?**
Use the following approach: (1) Create the new partitioned table structure alongside the existing table. (2) Use `INSERT INTO new_table SELECT ... FROM old_table` in batches during off-peak hours (or use `pg_partman` for managed migration). (3) After historical data is loaded, set up logical replication or triggers to capture ongoing changes from old_table to new_table. (4) Once new_table is fully caught up, swap the tables using a brief lock window: rename old_table to old_table_archive, rename new_table to the production name. (5) Update application queries if needed (partition key now required for pruning). Alternative: use `ATTACH PARTITION` to attach existing table segments as partitions of a new partitioned parent, which is instantaneous.

**What metrics indicate a sharding hotspot?**
Watch per-shard metrics: (1) CPU utilization per shard node — one shard at 90% while others are at 20% indicates a hotspot. (2) QPS (queries per second) per shard — compare max to average; max:average ratio > 3x is a hotspot. (3) Replication lag on the hot shard's replica — high write throughput increases lag. (4) Lock wait time on the hot shard. (5) `pg_stat_user_tables.n_tup_ins` per partition — one partition with 10x more inserts than others. Alert on per-shard CPU variance: if standard deviation across shards exceeds 30% of mean, investigate.

**How does DynamoDB handle sharding internally?**
DynamoDB partitions data using a hash of the partition key. Each partition holds up to 10GB of data and handles up to 3000 Read Capacity Units and 1000 Write Capacity Units. When a partition exceeds these limits, DynamoDB automatically splits it, migrating data in the background transparently to the user. DynamoDB Adaptive Capacity (enabled by default) redistributes throughput from underutilized partitions to overloaded ones to handle moderate hotspots. For extreme hotspots (one partition key generating 100% of traffic), DynamoDB Adaptive Capacity cannot help — schema redesign (sharding key at application level with random suffix) is required.

**What is the difference between application-level sharding and middleware-level sharding?**
Application-level sharding: the application code contains the shard routing logic — it hashes the key, connects to the appropriate database shard directly. Advantages: no extra network hop through a proxy, full control. Disadvantages: every service that touches sharded data must implement shard routing; schema changes and re-sharding require coordinated application deployments. Middleware-level sharding (Vitess, ProxySQL, Citus): a proxy layer handles routing, appearing as a single database to the application. Advantages: application code is shard-unaware, schema changes can be managed centrally. Disadvantages: additional network hop through the proxy, proxy becomes a bottleneck and single point of failure (requires its own HA).

**How do you handle global sequence generation across shards?**
Options: (1) UUID v4 (random): guaranteed globally unique, no coordination needed, but non-sortable and larger than BIGINT. (2) ULID: globally unique, time-sortable (milisecond precision + random suffix). (3) Twitter Snowflake: 64-bit ID = timestamp (41 bits) + machine ID (10 bits) + sequence (12 bits) = 4096 IDs/ms/machine, globally unique with no coordination. (4) Dedicated sequence service: a lightweight service hands out blocks of IDs (e.g., 1000 at a time) from a Redis `INCR` counter; each shard preallocates a block. Avoid: a centralized database sequence — it becomes a hotspot and single point of failure for the entire cluster.

**What is Citus and how does it extend PostgreSQL for sharding?**
Citus is a PostgreSQL extension (now open source, by Microsoft) that transforms PostgreSQL into a distributed database. A Citus cluster has one coordinator node and N worker nodes. The coordinator stores table distribution metadata (shard key → worker node mapping). Tables are distributed across workers using hash or range distribution. Queries arrive at the coordinator, which creates a distributed execution plan, sends sub-queries to the relevant worker nodes, and aggregates results. Colocation groups ensure that tables sharded on the same key store matching rows on the same worker, enabling co-located joins without cross-node data transfer. Citus is particularly popular for multi-tenant SaaS workloads where `tenant_id` is the distribution column.

**How does re-sharding differ from initial sharding?**
Initial sharding: data migration from an unsharded system to a sharded one. Typically a one-time bulk migration with a cutover window. Re-sharding: changing the number of shards in an already-sharded system (e.g., 4 → 8 shards). Every key's assignment changes. With mod-N hashing, this means moving ~50% of data (split each shard). With consistent hashing + virtual nodes, only ~12.5% of data moves (new node takes from each existing node evenly). The operational challenge is doing this without downtime: dual-write to old and new location during migration, then cut over routing, then clean up old locations. Vitess MoveTables and Citus's shard rebalancing automate this.

**Explain partition-wise joins in PostgreSQL and when they help.**
Partition-wise joins (PostgreSQL 11+, enabled with `enable_partitionwise_join = on`) allow the query planner to join two partitioned tables by matching partition pairs locally rather than assembling all data first. If orders and order_items are both partitioned by `month` with the same partition bounds, and you join them with a filter on month, PostgreSQL can join `orders_2024_01` with `order_items_2024_01` locally (both partitions on the same server), avoiding full table scans. This is most effective when both tables are co-partitioned on the same column and the query filters on the partition key. Without it, the planner must process all partition combinations.

---

## 13. Best Practices

- **Choose the shard key that matches your primary access pattern** — changing it later requires re-sharding all data.
- **Use UUIDs, ULIDs, or Snowflake IDs** as primary keys from day one to avoid sequential ID hotspots.
- **Over-provision logical shards relative to physical nodes** — 1024 logical shards on 4 physical nodes allows adding physical nodes without re-sharding (just rebalance logical shard assignment).
- **Co-locate related tables** by using the same shard key — tenant_id for orders, order_items, shipments all enables cross-table local joins.
- **Test re-sharding procedures** in a staging environment before you need them in production under pressure.
- **Monitor per-shard metrics independently** — aggregate cluster metrics hide hotspots; per-shard CPU, QPS, and lag must be tracked.
- **Use pg_partman** for automated time-based partition creation and maintenance in PostgreSQL to avoid manual partition management.
- **Design for scatter-gather fallback** — some queries will always scatter; design their result sets to be small and their merge to be efficient.

---

## 14. Case Study

**Scenario**: A multi-tenant B2B SaaS platform with 5000 business tenants (10 enterprise, 100 mid-market, 4890 small) uses a single unsharded PostgreSQL database. The top 10 enterprise tenants generate 70% of write traffic. The database is approaching write saturation at 40K TPS. Schema: `orders`, `order_items`, `invoices`, `payments` — all keyed by `tenant_id`.

**Sharding strategy selected**: Directory-based sharding with Citus extension.

```
Shard assignment:
  Enterprise tenants (10): each gets a dedicated worker node (10 nodes)
  Mid-market tenants (100): 5 tenants per node (20 nodes)
  Small tenants (4890): 10 tenants per node (489 nodes, but use 30 nodes with ~163 tenants each)

Total: 60 worker nodes (overprovisioned to allow node removal of small-tenant nodes as they grow)

Citus VSchema:
  Distribution column: tenant_id (all tables)
  Colocation group: orders, order_items, invoices, payments all on same worker for same tenant_id
```

**Query behavior**:
```sql
-- Tenant-scoped query (99% of queries): single worker
SELECT * FROM orders WHERE tenant_id = 42 AND created_at > now() - interval '7 days';
-- Routed to the worker holding tenant 42's data

-- Cross-tenant admin report (rare): scatter-gather
SELECT tenant_id, SUM(amount) FROM orders GROUP BY tenant_id;
-- Coordinator distributes to all workers, aggregates results
-- Acceptable: run this on read replicas during off-peak hours
```

**Results**:
- Write throughput: 40K TPS → 200K TPS (5 writers for top 5 enterprise shards)
- Enterprise tenant P99 write latency: 45ms → 8ms (less contention per shard)
- Cross-tenant reports: 5s → 8s (scatter-gather overhead, acceptable for scheduled reports)
- Operational change: each shard needs its own Patroni HA, monitoring, backup — ops burden 5x higher
- Decision validated: Citus coordinator handles transparent routing; application code is unchanged
