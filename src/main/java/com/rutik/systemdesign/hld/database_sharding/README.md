# Database Sharding

## Table of Contents
1. [What is Sharding?](#what-is-sharding)
2. [Why Shard?](#why-shard)
3. [Sharding vs Replication vs Partitioning](#sharding-vs-replication-vs-partitioning)
4. [Shard Key Selection](#shard-key-selection)
5. [Sharding Strategies](#sharding-strategies)
   - [Range-Based Sharding](#range-based-sharding)
   - [Hash-Based Sharding](#hash-based-sharding)
   - [Directory-Based Sharding](#directory-based-sharding)
   - [Geographic / Zone-Based Sharding](#geographic--zone-based-sharding)
6. [Cross-Shard Queries](#cross-shard-queries)
7. [Resharding](#resharding)
8. [Hotspot Handling](#hotspot-handling)
9. [Real-World Examples](#real-world-examples)
10. [Shard Proxies](#shard-proxies)
11. [Interview Questions](#interview-questions)
12. [Best Practices](#best-practices)

---

## Intuition

> **One-line analogy**: Database sharding is like splitting a huge phone book into volumes A-M and N-Z — each volume is smaller and can be accessed independently, but you need to know which volume to look in.

**Mental model**: A single database server has limits — CPU, disk, memory. When your dataset hits tens of TBs, no single machine suffices. Sharding splits the data horizontally across multiple databases (shards), each owning a partition of the data. The shard key (e.g., user_id, geographic region) determines which shard a given record lives on. Queries to a specific user/record go directly to the right shard. Cross-shard queries are expensive — avoid them by choosing the shard key that makes the most common queries single-shard.

**Why it matters**: Sharding is the primary technique for scaling writes beyond what a single database can handle. Read replicas scale reads; sharding scales writes and storage. Instagram, Twitter, and most large-scale databases use horizontal sharding.

**Key insight**: Shard key selection is the most consequential decision — the wrong key leads to hotspots (one shard handling 90% of traffic) and expensive cross-shard queries. Design for your dominant access patterns, not theoretical flexibility.

---

## What is Sharding?

Sharding is a database scaling technique that horizontally partitions data across multiple independent database instances (shards). Each shard holds a subset of the total data set and is hosted on a separate server. Together, the shards hold the complete dataset.

The key characteristic: each piece of data lives on exactly one shard. Shards are independent — they do not replicate each other's data (though each shard may have its own replicas for HA within itself).

```
Without Sharding:
+------------------+
|   Single DB      |  <-- All 10TB of user data
|   Max ~500GB SSD |  <-- Physically limited
|   Max ~50k QPS   |  <-- CPU/memory bottleneck
+------------------+

With Sharding (4 shards):
+----------+  +----------+  +----------+  +----------+
| Shard 1  |  | Shard 2  |  | Shard 3  |  | Shard 4  |
| Users    |  | Users    |  | Users    |  | Users    |
| 0-24M    |  | 25M-49M  |  | 50M-74M  |  | 75M-99M  |
| 2.5TB    |  | 2.5TB    |  | 2.5TB    |  | 2.5TB    |
| 12.5k QPS|  | 12.5k QPS|  | 12.5k QPS|  | 12.5k QPS|
+----------+  +----------+  +----------+  +----------+
              Total: 10TB, ~50k QPS (scalable to more shards)
```

---

## Why Shard?

### Single Database Limits

**Storage Limits**
A single machine's storage is bounded by physical hardware — typically a few TB for NVMe SSDs. Datasets at companies like Facebook (petabytes of user data) or Uber (billions of trip records) far exceed single-machine storage.

**Write Throughput**
A single DB primary handles all writes. At 50k+ writes/second, the primary becomes a bottleneck regardless of read replicas (replicas only help with reads). Sharding distributes write load across multiple primaries.

**Connection Limits**
Each DB connection consumes memory. A single PostgreSQL instance typically supports 100-500 connections before performance degrades. At large scale, connection pools help but have limits. Multiple shards multiply the available connection capacity.

**Index Size**
Large tables have large indexes that don't fit in memory (buffer pool), causing index traversals to hit disk. Smaller sharded tables have proportionally smaller indexes, improving cache efficiency.

**Operational Limits**
Large database backups, migrations, and VACUUM operations are slow and disruptive at scale. Smaller shards are easier to operate.

### Growth Trajectory

```
Users: 1M      -> Single DB fine
Users: 10M     -> Read replicas + caching
Users: 100M    -> Sharding becomes necessary
Users: 1B+     -> Deep sharding + specialized stores
```

---

## Sharding vs Replication vs Partitioning

| Technique | Definition | Primary Goal | Data Distribution |
|-----------|------------|-------------|------------------|
| Replication | Copy same data to multiple nodes | Read scalability, HA | Same data on all replicas |
| Partitioning | Split data within a single DB instance | Manageability, query performance | Different partitions, same DB server |
| Sharding | Distribute data across separate DB instances | Write scalability, storage scale | Different data on different servers |

**Replication** (one primary, N replicas):
- Reads can go to any replica
- All writes go to primary
- If primary fails, promote a replica
- Does NOT scale writes or storage beyond a single machine

**Partitioning** (table partitioning in PostgreSQL, MySQL):
- Splits a large table into smaller physical segments on the same server
- Same server handles all partitions
- Improves query performance (partition pruning)
- Does not help with storage or write throughput at the server level

**Sharding** (across separate machines):
- Truly distributes both storage and write throughput
- Each shard is an independent DB instance (often with its own replicas)
- Application must know which shard to route requests to

**In practice, combine all three:**
```
[Shard 1 Primary] --> [Shard 1 Replica A]
                  --> [Shard 1 Replica B]

[Shard 2 Primary] --> [Shard 2 Replica A]
                  --> [Shard 2 Replica B]

Each shard is internally partitioned by date range.
```

---

## Shard Key Selection

The shard key is the most important architectural decision in a sharded system. It determines which shard a piece of data belongs to. A poor shard key choice leads to hotspots, uneven distribution, and expensive cross-shard queries.

### Shard Key Properties to Evaluate

**Cardinality**
The shard key must have high cardinality (many distinct values). A key with only 10 distinct values can have at most 10 shards. Low cardinality = limited scalability.

- Low cardinality (bad): `status` (active/inactive), `country` (200 values), `user_type`
- High cardinality (good): `user_id` (millions), `order_id` (billions), `email`

**Frequency / Distribution**
Values should be uniformly distributed across shards. Skewed distributions create "fat shards" that are overloaded while others sit idle.

- Skewed (bad): username starting with 'A'-'E' vs. 'X'-'Z' (letter frequency is uneven)
- Uniform (good): hash of user_id

**Monotonic Keys**
Auto-incrementing IDs and timestamps are monotonically increasing. New data always goes to the "last" shard, creating a write hotspot on the newest shard while all others are cold.

```
Timestamps as shard key (bad):
  Shard 1: Jan-Mar 2024 (cold, read-only mostly)
  Shard 2: Apr-Jun 2024 (cold, read-only mostly)
  Shard 3: Jul-Sep 2024 (cold, read-only mostly)
  Shard 4: Oct+ 2024   (HOT - all writes go here)
```

**Access Pattern Alignment**
Ideally, the shard key matches your most frequent query pattern. If you primarily query by `user_id`, shard by `user_id`. This ensures most queries hit one shard.

### Good Shard Key Examples

- **user_id**: Uniform, high cardinality, aligns with user-centric access patterns. Used by most social platforms.
- **tenant_id**: For multi-tenant SaaS, shard by customer. Tenant isolation, easy to move tenants between shards.
- **geographic region**: When data locality is important (GDPR, latency). Query patterns often regional.
- **entity_id (hashed)**: Generic approach — hash any high-cardinality ID for uniform distribution.

### Bad Shard Key Examples

- **Timestamp**: Monotonic, creates write hotspot on newest shard.
- **Status field**: Low cardinality, can't scale past 2-3 shards.
- **Mutable field**: Shard keys should be immutable. If user changes email, their data would need to move shards — extremely expensive.
- **Non-query-aligned key**: Sharding by `product_id` when most queries are by `user_id` forces scatter-gather.

### Hotspot Shards

A hotspot occurs when one shard receives disproportionately more traffic than others:

```
Shard 1: 5% traffic   (cold)
Shard 2: 5% traffic   (cold)
Shard 3: 85% traffic  (HOT - bottleneck!)
Shard 4: 5% traffic   (cold)

Adding more shards doesn't help — hotspot moves to the new "latest" shard.
```

**Solutions:**
- Use hash-based sharding instead of range-based for write-heavy workloads
- Add random suffix to hot keys: `user:12345:suffix_1`, `user:12345:suffix_2` (scatter writes across sub-shards)
- Identify celebrity/hot entities and route them specially (dedicated shard for top 1% entities)

---

## Sharding Strategies

### Range-Based Sharding

#### Concept
Divide the key space into contiguous ranges. Assign each range to a shard.

```
user_id range -> shard mapping:

Shard 1: user_id  1       - 10,000,000
Shard 2: user_id  10,000,001 - 20,000,000
Shard 3: user_id  20,000,001 - 30,000,000
Shard 4: user_id  30,000,001 - 40,000,000
```

#### ASCII Diagram

```
user_id: 1                                              100M
         |---------|---------|---------|---------|
         | Shard 1 | Shard 2 | Shard 3 | Shard 4 |
         | 0-25M   | 25-50M  | 50-75M  | 75-100M |
         |---------|---------|---------|---------|

Range query: user_id BETWEEN 10M AND 30M
  --> Only hits Shard 1 and Shard 2 (efficient!)

Range query: SELECT * ORDER BY user_id LIMIT 100
  --> Start at Shard 1, sequential (efficient!)
```

#### Routing Logic
```python
def get_shard(user_id: int, shard_ranges: list) -> int:
    for shard_id, (min_id, max_id) in enumerate(shard_ranges):
        if min_id <= user_id <= max_id:
            return shard_id
    raise ValueError(f"No shard found for user_id {user_id}")

shard_ranges = [
    (1, 25_000_000),        # Shard 1
    (25_000_001, 50_000_000), # Shard 2
    (50_000_001, 75_000_000), # Shard 3
    (75_000_001, 100_000_000), # Shard 4
]
```

#### Pros
- Range queries and ordered scans are efficient (hit one or few shards)
- Sequential data access is locality-aware
- Easy to reason about data placement

#### Cons
- Uneven distribution if data is skewed (e.g., most users have low IDs from early days)
- Monotonic keys cause write hotspots (all new users go to the last shard)
- Requires careful range planning; re-ranging is complex

---

### Hash-Based Sharding

#### Concept
Apply a hash function to the shard key, then use modulo to determine the shard number.

```
shard_id = hash(key) % num_shards
```

The hash function distributes keys pseudo-randomly across shards, achieving uniform distribution.

#### ASCII Diagram

```
hash(user_id) % 4:

user_id: 1001  -> hash = 7238 -> 7238 % 4 = 2  -> Shard 2
user_id: 1002  -> hash = 1847 -> 1847 % 4 = 3  -> Shard 3
user_id: 1003  -> hash = 9201 -> 9201 % 4 = 1  -> Shard 1
user_id: 1004  -> hash = 4356 -> 4356 % 4 = 0  -> Shard 0
user_id: 1005  -> hash = 6712 -> 6712 % 4 = 0  -> Shard 0

Distribution: ~25% of users per shard (uniform)

Shard 0: [user1004, user1005, ...]
Shard 1: [user1003, ...]
Shard 2: [user1001, ...]
Shard 3: [user1002, ...]
```

#### Routing Logic
```python
import hashlib

def get_shard(key: str, num_shards: int) -> int:
    hash_value = int(hashlib.md5(key.encode()).hexdigest(), 16)
    return hash_value % num_shards

# Usage
shard = get_shard(str(user_id), num_shards=4)
```

#### The Resharding Problem
When you change `num_shards` from 4 to 5, almost every key maps to a different shard:
```
user_id: 1001 -> hash % 4 = 2 -> Shard 2 (old)
user_id: 1001 -> hash % 5 = 3 -> Shard 3 (new)

~80% of data must be moved to different shards during resharding!
This is why Consistent Hashing was invented (see that module).
```

#### Pros
- Excellent uniform distribution (no hotspots for uniform access patterns)
- Simple, deterministic routing
- Works well for point queries (lookup by exact key)

#### Cons
- Range queries require scatter-gather across all shards
- Resharding is expensive: large fraction of data must migrate
- Related data may end up on different shards (e.g., user and their orders)

---

### Directory-Based Sharding

#### Concept
Maintain a lookup table (directory) that maps each key (or key range) to its shard. Routing requires consulting this directory.

#### Architecture

```
                    +------------------+
                    |  Shard Directory |
                    |  (Lookup Table)  |
                    |                  |
                    | user_1001 -> S3  |
                    | user_1002 -> S1  |
                    | tenant_A  -> S2  |
                    | tenant_B  -> S4  |
                    +------------------+
                            |
              +-------------+-------------+
              |             |             |
           +------+      +------+      +------+
           |  S1  |      |  S3  |      |  S4  |
           +------+      +------+      +------+

Application queries directory -> gets shard ID -> queries that shard
```

#### Routing Logic
```python
class DirectoryShardRouter:
    def __init__(self, directory_db):
        self.directory = directory_db  # e.g., Redis or metadata DB

    def get_shard(self, entity_id: str) -> str:
        shard = self.directory.get(f"shard_map:{entity_id}")
        if not shard:
            # Assign to a shard (e.g., least-loaded)
            shard = self.assign_to_shard(entity_id)
            self.directory.set(f"shard_map:{entity_id}", shard)
        return shard

    def migrate_entity(self, entity_id: str, new_shard: str):
        # Move data, then update directory atomically
        migrate_data(entity_id, old_shard=self.get_shard(entity_id), new_shard=new_shard)
        self.directory.set(f"shard_map:{entity_id}", new_shard)
```

#### Pros
- Maximum flexibility: any entity can be moved to any shard at any time
- Can accommodate hotspots: move a hot entity to a dedicated shard
- No formula to change during resharding — just update the directory

#### Cons
- Directory is a bottleneck: every request needs a directory lookup
- Directory is a single point of failure (must be HA)
- Adds one network round-trip per request (mitigated by caching directory entries)
- Directory must be kept consistent with actual data placement

---

### Geographic / Zone-Based Sharding

#### Concept
Partition data by geographic region or logical zone. Users in Europe are stored in EU shards; users in the US are stored in US shards.

```
[EU Shard]          [US Shard]           [APAC Shard]
Users in:           Users in:            Users in:
France              USA                  Japan
Germany             Canada               Australia
UK                  Mexico               India
Italy               Brazil               Singapore
```

#### Routing Logic
```python
def get_shard(user_id: str, user_country: str) -> str:
    region_map = {
        'DE': 'shard_eu', 'FR': 'shard_eu', 'GB': 'shard_eu',
        'US': 'shard_us', 'CA': 'shard_us',
        'JP': 'shard_apac', 'AU': 'shard_apac', 'IN': 'shard_apac',
    }
    return region_map.get(user_country, 'shard_us')  # default
```

#### Use Cases
- **GDPR Compliance**: EU user data must remain in EU data centers. Geo-sharding enforces this at the infrastructure level.
- **Data Sovereignty**: Many countries (Russia, China, India) require local data residency.
- **Latency Optimization**: Serve EU users from EU database servers.
- **Multi-region Active-Active**: Each region's shard is the primary for its users, reducing write latency.

#### Pros
- Compliance with data residency laws (GDPR, CCPA)
- Reduced latency for users (data near them)
- Natural disaster isolation (US outage doesn't affect EU shard)

#### Cons
- Cross-region users (travelers, expats) may get worse performance
- Global analytics queries require cross-shard aggregation across regions
- Uneven shard sizes if user distribution is skewed geographically

---

## Cross-Shard Queries

Cross-shard queries are one of the most significant operational challenges of sharding. Any query that cannot be satisfied by a single shard requires a more complex approach.

### Scatter-Gather Pattern

The most common approach: send the query to all relevant shards in parallel, then merge results at the application layer.

```
Query: "Find all orders from user_id IN (list of 1000 users)"
       -> users are on 4 different shards

Application:
  parallel_results = await gather([
      shard1.query("SELECT * FROM orders WHERE user_id IN (...)"),
      shard2.query("SELECT * FROM orders WHERE user_id IN (...)"),
      shard3.query("SELECT * FROM orders WHERE user_id IN (...)"),
      shard4.query("SELECT * FROM orders WHERE user_id IN (...)"),
  ])
  merged = flatten(parallel_results)
  sorted_results = sort(merged, key='created_at')
```

**Cost**: Latency is max(shard latencies), not sum. But all shards are loaded simultaneously.

### Aggregation Challenges

```sql
-- Cross-shard COUNT (scatter-gather then sum)
SELECT COUNT(*) FROM users WHERE country = 'US'
-- -> Run on all shards, sum the counts

-- Cross-shard ORDER BY + LIMIT (expensive!)
SELECT * FROM posts ORDER BY created_at DESC LIMIT 20
-- -> Must get top 20 from each shard, merge all, re-sort, take top 20
-- -> With N shards: N * 20 rows fetched, only 20 kept
```

### Global / Broadcast Tables

Some small, frequently-joined lookup tables (countries, categories, config) should be replicated to all shards. This avoids cross-shard joins for common lookups.

```python
# In Vitess: broadcast tables are replicated to all shards
# In application: use local cache or local shard copy

# Example: "categories" table replicated to all shards
# "posts" table sharded by user_id
# JOIN posts with categories works locally on each shard
SELECT p.title, c.name
FROM posts p JOIN categories c ON p.category_id = c.id
WHERE p.user_id = 12345
-- Shard for user 12345 has a local copy of categories -> no cross-shard join
```

### Application-Level Joins

When joins are unavoidable across shards:
```python
# Fetch user from user shard
user = user_shard.get(user_id=12345)

# Fetch orders from order shard (sharded by order_id, not user_id)
# Must scatter-gather across all order shards
orders = scatter_gather(order_shards,
    query=f"SELECT * FROM orders WHERE user_id = {user_id}")

# Join in application
result = {**user, 'orders': orders}
```

---

## Resharding

Resharding is the process of redistributing data across a different number of shards. It is unavoidable as systems grow but is operationally complex and risky.

### The Problem with Simple Hash Resharding

```
4 shards: shard = hash(id) % 4
5 shards: shard = hash(id) % 5

For user_id=1234:
  hash(1234) = 7839
  7839 % 4 = 3  (was on shard 3)
  7839 % 5 = 4  (now belongs on shard 4)

~80% of all keys change shards when going from N to N+1 shards!
This requires moving ~80% of all data — extremely disruptive.
```

### Consistent Hashing Approach

Consistent hashing maps keys to a ring and adds/removes shards with minimal data movement. See the `consistent_hashing` module for full details.

```
4 shards: Only ~25% of keys affected when adding the 5th shard
          (1/N data moves when adding the Nth shard)
```

### Online Resharding (Zero-Downtime)

Production resharding must be done online, without service interruption:

```
Phase 1: Dual-write
  - Write new data to both old shard and new target shard
  - Old shard is still authoritative for reads

Phase 2: Backfill
  - Migrate historical data from old shard to new shard
  - Run as a background job with throttling to not impact production

Phase 3: Verify
  - Compare checksums / row counts between old and new shards
  - Shadow-read from new shard and compare results

Phase 4: Cutover
  - Switch reads to new shard
  - Monitor for errors
  - Stop writes to old shard

Phase 5: Cleanup
  - Delete migrated data from old shard after verification period
```

### Vitess and CockroachDB Approaches

**Vitess** (used by YouTube, Slack):
- Supports online resharding via "VReplication"
- Tracks changes to source shards using binlog
- Zero-downtime resharding with automated cutover

**CockroachDB**:
- Automatic re-balancing — no manual resharding needed
- Ranges (shards) split and merge automatically based on size
- Rebalancing happens in the background with no user intervention

---

## Hotspot Handling

### The Celebrity Problem

A celebrity user (Beyoncé, Elon Musk) has millions of followers. Any action they take — posting, going live — triggers millions of fan requests to the same shard.

```
Normal user write: 1 notification -> 1 shard hit
Celebrity write: 10M notifications -> millions of reads on 1 shard
```

### Solutions for Write Hotspots

**Key Salting / Suffix Randomization**
```python
# Instead of writing to one key:
write_key = f"celebrity:{user_id}"

# Write to N sub-keys, distributed across shards:
num_splits = 100
sub_shard = random.randint(0, num_splits - 1)
write_key = f"celebrity:{user_id}:{sub_shard}"

# Reading: must read all N sub-keys and merge
all_keys = [f"celebrity:{user_id}:{i}" for i in range(num_splits)]
results = scatter_read(all_keys)
merged = merge(results)
```

**Special-Case Hot Entities**
Identify top-N hot users/items and route them to dedicated "hot shards" with more resources.

**Caching Layer**
Add Redis/Memcached in front of the hot shard. Reads for celebrity profiles/content are served from cache, dramatically reducing DB load.

### Write Amplification

Some designs cause a single user action to trigger writes to many shards:

```
User posts a tweet:
  1. Write tweet to tweet shard (user_id shard)
  2. Fan-out: write to timeline table for each follower
     - If user has 1M followers on 4 shards -> 250K writes per shard
     - This is write amplification!

Solution: Hybrid fan-out
  - Small accounts: push model (fan-out on write, fast reads)
  - Large accounts (celebrities): pull model (fan-out on read, compute on read)
```

---

## Real-World Examples

### Instagram: PostgreSQL Sharding

Instagram started with a single PostgreSQL instance. As they scaled:
1. **Early sharding**: Manually sharded PostgreSQL across multiple servers, sharding by `user_id`
2. **Schema design insight**: Used UUIDs that encode shard ID, making routing deterministic
3. **ID format**: `id = timestamp_ms | shard_id | sequence` — first 41 bits timestamp, next 13 bits shard ID, last 10 bits local sequence
4. **Migration to Cassandra**: Eventually moved media metadata to Cassandra for better write scalability, while keeping social graph in PostgreSQL shards

### Discord: Cassandra Sharding

- Stores billions of messages in Apache Cassandra
- Shard key: `(channel_id, bucket)` — bucket groups messages by time range
- Cassandra's consistent hashing handles node additions automatically
- Challenge: "Discord server going down" (large guilds cause hot partitions)
- Solution: Moved to ScyllaDB for better per-shard performance

### YouTube: MySQL + Vitess

YouTube uses Vitess (MySQL sharding layer):
- Vitess acts as a MySQL-compatible proxy in front of many MySQL instances
- Transparent sharding: applications use standard SQL, Vitess routes to the right shard
- VSchema defines sharding rules (which tables are sharded, by what key)
- Online schema changes without locking tables
- Horizontal re-sharding without service interruption

### MongoDB Sharding

MongoDB has built-in sharding (called "sharded cluster"):
```
Components:
  mongos (query router): routes client queries to correct shards
  Config servers: store cluster metadata and shard mapping
  Shards: each is a replica set (primary + secondary)

Sharding strategies in MongoDB:
  - Hashed sharding: hash(shard_key) for even distribution
  - Ranged sharding: range-based for range queries
  - Zone sharding: geographic or logical zones

mongos handles scatter-gather transparently for cross-shard queries.
```

---

## Shard Proxies

Shard proxies sit between the application and the database shards, abstracting the routing logic. Applications connect to the proxy using a standard DB protocol.

### Vitess

- Open-source, MySQL-compatible sharding proxy (CNCF project)
- Used by YouTube, Slack, GitHub, Pinterest
- Features: connection pooling, query routing, online schema changes, online resharding, row-based replication
- VTGate: query routing layer; VTTablet: per-shard agent

### ProxySQL

- High-performance MySQL proxy
- Can route queries to different shards based on rules (regex on query, user, schema)
- Not a full sharding solution — routing rules must be manually defined
- Excellent for read/write splitting and connection pooling

### Amazon RDS Proxy

- Managed proxy for RDS and Aurora
- Handles connection pooling to prevent connection exhaustion
- Not a sharding proxy per se — used within a single DB instance
- Useful in Lambda-heavy architectures where thousands of functions open DB connections

### Citus (PostgreSQL Extension)

- Transforms PostgreSQL into a distributed database
- Coordinator node routes queries to worker shards
- Worker nodes each hold a subset of data
- Supports distributed joins and aggregations natively
- Used by Microsoft Azure (managed offering: Azure Cosmos DB for PostgreSQL)

---

## Interview Questions

**Q1: What is the difference between sharding and replication?**

A: Replication copies the same data to multiple nodes for read scalability and high availability. All replicas hold identical data; writes go to the primary. Sharding distributes different data across nodes for write scalability and storage scaling. Each shard holds a unique subset of data. In production, you combine both: each shard has its own replica set.

**Q2: How do you choose a shard key?**

A: A good shard key must be: high cardinality (many distinct values), uniformly distributed (to avoid hotspots), non-monotonic (to avoid write hotspots on the newest shard), immutable (changing the key would require moving the record to a different shard), and aligned with query patterns (most queries should hit one shard). Common good choices: user_id, tenant_id. Common bad choices: timestamp, status, low-cardinality fields.

**Q3: What is a hotspot shard and how do you fix it?**

A: A hotspot occurs when one shard receives disproportionately more traffic than others. Causes: bad shard key (monotonic or low cardinality), "celebrity" entities that are extremely popular. Fixes: (1) Use hash-based sharding for even write distribution. (2) Key salting: write hot entities to multiple sub-keys spread across shards. (3) Caching layer in front of the hot shard. (4) Dedicated "VIP shard" for identified hot entities with more resources.

**Q4: How would you handle a cross-shard JOIN query?**

A: Options: (1) Scatter-gather: run the query on all relevant shards in parallel, merge results at the application layer. This works but requires application code to handle merging and sorting. (2) Denormalize data: store redundant data on the same shard so JOINs are local. (3) Broadcast/global tables: replicate small lookup tables to all shards. (4) Avoid cross-shard JOINs in the schema design by co-locating related data on the same shard (e.g., both user and user's orders share the same user_id shard key).

**Q5: How does resharding work and what are the challenges?**

A: Resharding redistributes data when adding or removing shards. With simple hash modulo, changing N to N+1 shards moves ~80% of data. Consistent hashing reduces this to ~1/N. Online resharding steps: (1) dual-write to old and new shard, (2) backfill historical data in background, (3) verify consistency, (4) cutover reads, (5) clean up old shard. Challenges: maintaining consistency during migration, handling in-flight requests during cutover, throttling migration to avoid impacting production.

**Q6: What is the celebrity problem in social media platforms and how is it handled?**

A: When a celebrity user with millions of followers posts something, the system generates millions of notifications/updates simultaneously, creating a massive spike on the shard storing that celebrity's data. Solutions: (1) Pull-based fan-out for celebrities — don't pre-compute timelines for large accounts, compute on read. (2) Key salting — distribute writes across multiple sub-shards. (3) Identify hot entities and give them dedicated shards or move them to a hot-shard cluster with more resources.

**Q7: How would you design a globally distributed database that complies with GDPR?**

A: Use geographic sharding to ensure EU user data stays in EU data centers. Shard by user_id with a metadata lookup that records which region a user belongs to. EU users are assigned EU shard IDs, US users are assigned US shard IDs. The EU shards physically reside in EU AWS/GCP regions. Ensure no cross-region replication of EU data. For global analytics, use anonymization/aggregation pipelines that don't expose individual data cross-region.

**Q8: What is consistent hashing and why is it used in sharding?**

A: Consistent hashing maps both shards and keys to positions on a ring. A key is assigned to the nearest shard in clockwise direction. Adding a shard only requires moving data from one neighboring shard — approximately 1/N of total data (not 80%+ as with modulo hashing). Virtual nodes (each physical shard occupies multiple ring positions) ensure even distribution even with few shards. Used by DynamoDB, Cassandra, and consistent-hashing-based sharding implementations.

**Q9: What are the tradeoffs between range-based and hash-based sharding?**

A: Range-based: good for range queries and ordered scans (hits one or few shards), but risks hotspots with monotonic keys and uneven distribution with skewed data. Hash-based: excellent uniform distribution, no hotspots for uniform access, but range queries require scatter-gather across all shards, and resharding is expensive (most keys remap). Choosing depends on workload: time-series / range-heavy queries favor range-based; random point lookups favor hash-based.

**Q10: How does Vitess enable online resharding?**

A: Vitess uses VReplication, which streams MySQL binlog changes from source shards to target shards in real-time. Process: (1) Create new target shards. (2) VReplication copies existing data via backfill. (3) VReplication continuously applies binlog changes to keep target in sync. (4) Once target is caught up, pause traffic, do final sync, update routing in VTGate, resume traffic. (5) Old shards become inactive and can be cleaned up. Total downtime is seconds.

**Q11: How would you design the sharding strategy for an e-commerce platform like Amazon?**

A: Separate different data types with different strategies: (1) User data: shard by user_id (hash-based), co-locate user profiles, addresses, preferences. (2) Product catalog: shard by product_id (hash-based), replicate popular products to all shards. (3) Orders: shard by user_id (so a user's orders are on one shard), not order_id. (4) Inventory: shard by product_id and warehouse_id, keep inventory updates local to each warehouse shard. (5) Search index: Elasticsearch with its own sharding handles product search. The hardest query is "all orders for a product" — requires a secondary index or scatter-gather since orders are sharded by user.

**Q12: What is a "scatter-gather" query and when does it become a problem?**

A: Scatter-gather sends a query to all shards simultaneously and merges results. It becomes problematic when: (1) Low selectivity — returning large result sets from all shards. (2) Complex aggregations — like global ORDER BY with LIMIT requires fetching many rows from each shard. (3) Deep pagination — page 1000 of results requires fetching 1000 * page_size rows from each shard. (4) Number of shards is large — latency is still bounded by slowest shard, but resource usage scales linearly. Mitigations: avoid unbounded queries, add query execution limits, use materialized aggregates, choose shard keys that align with common query patterns.

---

## Cross-Perspective: LLD Connections

**LLD View — Design Patterns That Implement Database Sharding**

- **Strategy** — Sharding strategies (hash sharding, range sharding, directory/lookup sharding, geographic sharding) are the textbook Strategy pattern: each encapsulates a shard assignment algorithm behind a `ShardingStrategy` interface — swappable as requirements evolve.
- **Proxy** — Shard proxies (ProxySQL, Vitess) are Proxy pattern: applications connect to the proxy with a standard connection string; the proxy routes queries to the correct shard transparently based on the shard key in the query.
- **Factory** — A `ShardConnectionFactory` creates and manages connections to the correct shard given a shard key, hiding connection pool management and shard topology from service code.
- **Observer** — Resharding events (adding nodes, rebalancing) broadcast to Observer subscribers: connection pool managers refresh their routing tables; cache invalidators clear stale shard-local caches; monitoring systems update topology dashboards.

---

## Best Practices

### 1. Shard Key Immutability
The shard key should never change for a record's lifetime. Changing the shard key requires deleting the record from the old shard and inserting it in the new one — this is a distributed transaction and extremely difficult to do safely at scale. Design your data model so the natural identifier is immutable.

### 2. Avoid Cross-Shard Transactions
Distributed transactions (two-phase commit) across shards are slow, complex, and reduce availability. Design your shard key so related data that must be updated atomically lives on the same shard. Accept eventual consistency for data that spans shards.

### 3. Co-locate Related Data
If orders always belong to a user, shard orders by user_id (not order_id). This ensures a user's orders are always on the same shard as their profile, enabling efficient local queries without cross-shard operations.

### 4. Build in Shard Awareness from Day One
Retrofitting sharding onto an existing system that assumed a single database is extremely painful. Design your data access layer to accept a shard ID or to route via a shard router from the beginning. This doesn't mean you need multiple shards on day one — just ensure the abstraction exists.

### 5. Monitor Per-Shard Metrics
Track per-shard: QPS, latency, connection count, storage utilization, replication lag. A single overloaded shard will degrade overall system performance. Alert on any shard that is >80% of its capacity limits.

### 6. Plan for Resharding
Even with consistent hashing, resharding is operationally complex. Have a tested, documented resharding runbook before you need it. Practice resharding in staging. Automate as much as possible (Vitess VReplication, CockroachDB automatic rebalancing).

### 7. Keep Shards Small Enough to Manage
A shard that holds 5TB of data takes days to rebuild from backup or migrate. Aim for shards of reasonable size (100-500GB) that can be operated quickly. More, smaller shards are easier to manage than fewer, larger shards.

### 8. Use a Sharding Proxy
Putting sharding logic in application code creates a maintenance nightmare as routing tables change. Use a sharding proxy (Vitess, Citus) to abstract this. The proxy handles routing, connection pooling, and schema changes transparently.
