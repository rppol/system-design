# Database Design

## 1. Concept Overview

Database design is the process of structuring how data is stored, organized, accessed, and maintained. Good database design directly determines a system's scalability, consistency, query performance, and maintainability. Poor design creates technical debt that compounds exponentially as data volume and traffic grow.

At the highest level, the two dominant paradigms are:
- **Relational databases (SQL)**: Structured data in tables with a fixed schema, powerful querying via SQL, and strict consistency guarantees (ACID).
- **Non-relational databases (NoSQL)**: Flexible schemas, optimized for specific access patterns (documents, key-value, column-family, graph), and typically trade consistency for availability and partition tolerance.

Understanding when to use each, how to model data effectively, and how to scale both paradigms is foundational to system design.

---

## Intuition

> **One-line analogy**: Choosing between SQL and NoSQL is like choosing between a filing cabinet with labeled folders (SQL — organized, queryable) and a storage unit where you can put anything anywhere (NoSQL — flexible, scalable).

**Mental model**: SQL databases organize data into tables with rigid schemas and give you ACID guarantees — you always get consistent, correct data. NoSQL databases trade these guarantees for flexibility (any schema), scalability (horizontal sharding), and speed (optimized for specific access patterns). Neither is universally better; the choice depends on your access patterns, scale requirements, and consistency needs.

**Why it matters**: Database design decisions are the hardest to change later. A poorly chosen database type or data model creates performance problems that can't be fixed without expensive migrations. Getting this right at the design stage is critical.

**Key insight**: The SQL vs NoSQL decision hinges on one question: do you need flexible querying (arbitrary WHERE, JOIN, GROUP BY) or do you know exactly how you'll access data? If you need flexible queries, use SQL; if you have a fixed access pattern that needs scale, consider NoSQL.

---

## 2. Core Principles

- **Data Integrity**: Enforcing rules (constraints, foreign keys, types) to prevent corrupt or inconsistent data.
- **Normalization vs. Denormalization**: Eliminating redundancy (normalize for writes, denormalize for reads).
- **Indexing**: Trading write overhead and storage for faster reads.
- **Consistency Models**: ACID (strong) vs. BASE (eventual) — choosing the right model for the use case.
- **Replication**: Keeping copies of data on multiple nodes for availability and read scaling.
- **Partitioning/Sharding**: Splitting data across nodes for horizontal write scaling.
- **Access Pattern First**: Design schemas around how data will be queried, not just how it's structured (especially critical for NoSQL).

---

## 3. SQL vs. NoSQL

### SQL (Relational)

**Examples:** PostgreSQL, MySQL, Amazon Aurora, CockroachDB, Google Spanner

**Characteristics:**
- Tabular structure with a defined schema (columns, types, constraints).
- Relationships via foreign keys; joins across tables.
- Full SQL query language — flexible, ad-hoc queries.
- ACID transactions across multiple tables/rows.
- Vertical scaling primary; horizontal via read replicas or distributed SQL (Spanner, CockroachDB).

**Best for:** Financial systems, ERP, e-commerce orders, any domain requiring complex relational queries and strong consistency.

---

### NoSQL

#### Key-Value Stores
**Examples:** Redis, DynamoDB (also document), Memcached

- Simplest model: key maps to opaque blob.
- O(1) reads/writes. No schema.
- Best for: sessions, caches, feature flags, shopping carts.

#### Document Stores
**Examples:** MongoDB, Couchbase, Firestore

- Stores JSON/BSON documents. Flexible schema.
- Rich query support within documents; limited cross-document joins.
- Best for: content management, catalogs, user profiles, event data.

#### Wide-Column (Column-Family)
**Examples:** Apache Cassandra, HBase, Amazon Keyspaces

- Rows identified by a partition key. Columns are dynamic per row.
- Optimized for write-heavy, time-series, append workloads.
- Best for: IoT telemetry, activity logs, time-series data, recommendation events.

#### Graph Databases
**Examples:** Neo4j, Amazon Neptune, JanusGraph

- Nodes and edges with properties. Optimized for traversal queries.
- Best for: social networks, fraud detection, knowledge graphs, recommendation engines.

#### Search Engines (Specialized NoSQL)
**Examples:** Elasticsearch, Apache Solr, OpenSearch

- Inverted indexes for full-text search.
- Best for: log analytics, product search, document retrieval.

---

## 4. ACID vs. BASE

### ACID (SQL / Relational)

| Property | Meaning |
|----------|---------|
| **Atomicity** | A transaction is all-or-nothing. Either all operations commit or all roll back. |
| **Consistency** | A transaction moves the database from one valid state to another, respecting all constraints. |
| **Isolation** | Concurrent transactions behave as if they ran sequentially (configurable via isolation levels). |
| **Durability** | Committed transactions survive crashes (persisted to durable storage, WAL). |

**Isolation Levels (SQL):**
- **Read Uncommitted**: Dirty reads possible.
- **Read Committed**: No dirty reads; non-repeatable reads possible. (PostgreSQL default)
- **Repeatable Read**: No dirty/non-repeatable reads; phantom reads possible. (MySQL InnoDB default)
- **Serializable**: Full isolation — no anomalies. Lowest throughput.

### BASE (NoSQL / Distributed)

| Property | Meaning |
|----------|---------|
| **Basically Available** | System guarantees availability (per CAP), possibly serving stale data. |
| **Soft State** | State may change over time even without input (due to eventual consistency propagation). |
| **Eventually Consistent** | The system will become consistent over time, given no new updates. |

---

## 5. Normalization

Organizing tables to reduce redundancy and improve data integrity.

### Normal Forms

| Form | Rule |
|------|------|
| **1NF** | Atomic column values; no repeating groups. Each cell has one value. |
| **2NF** | 1NF + no partial dependencies (non-key column depends on whole composite key). |
| **3NF** | 2NF + no transitive dependencies (non-key column depends only on the primary key, not another non-key column). |
| **BCNF** | Stricter 3NF — every determinant is a candidate key. |
| **4NF** | No multi-valued dependencies. |

### When to Denormalize

Denormalization intentionally introduces redundancy for read performance:
- Store computed aggregates (total order count on user table).
- Duplicate data to avoid expensive joins (embed category name in product table).
- Pre-join tables for frequently queried combinations.

Trade-off: Faster reads, but updates must propagate to multiple places — risk of inconsistency.

---

## 6. Indexing

Indexes are data structures that trade storage and write overhead for faster read queries.

### B-Tree Index (Default)
- Balanced tree; O(log n) lookups, range queries.
- Excellent for equality and range predicates on ordered data.
- Used by: PostgreSQL, MySQL, SQL Server.

### Hash Index
- O(1) exact lookups. No range queries.
- Used by: Redis, some memory-optimized tables.

### Composite Index
- Index on (col_a, col_b). Usable for queries on col_a alone OR (col_a, col_b). NOT col_b alone (leftmost prefix rule).

### Covering Index
- Index contains all columns needed for a query — no table row lookup needed. Very fast.

### Partial Index
- Index only rows matching a condition: `CREATE INDEX ON orders(user_id) WHERE status = 'active'`.

### Full-Text Index
- Inverted index for text search. PostgreSQL `tsvector`, MySQL FULLTEXT, Elasticsearch.

### Index Pitfalls
- Over-indexing: Each index slows writes and uses storage.
- Index on low-cardinality columns (boolean, status with 3 values) — often ignored by the query planner.
- Not using `EXPLAIN ANALYZE` to verify index usage.

---

## 7. Replication

Replication copies data from one node (primary) to others (replicas).

### Primary-Replica (Master-Slave)

```
Writes --> Primary --> WAL/binlog --> Replica 1
                                  --> Replica 2
                                  --> Replica 3 (read traffic)
```

- **Synchronous replication**: Primary waits for at least one replica to confirm before acknowledging write. Stronger durability, higher write latency.
- **Asynchronous replication**: Primary acknowledges immediately; replicas catch up. Lower latency, risk of data loss on failover.
- **Semi-synchronous**: At least one replica must acknowledge.

### Read Replicas
- Direct read traffic to replicas, writes to primary.
- Replicas may lag (replication lag) — stale reads possible.
- Scale reads horizontally without scaling writes.

### Multi-Primary (Multi-Master)
- Multiple nodes accept writes. Conflict resolution required.
- Examples: MySQL Group Replication, CockroachDB, Cassandra (leaderless).

### Leaderless Replication (Dynamo-style)
- Any node accepts writes. Uses quorum writes/reads (W + R > N for consistency).
- `N` = replication factor, `W` = write quorum, `R` = read quorum.
- Examples: Cassandra, DynamoDB, Riak.

---

## 8. Architecture Diagrams

### Primary-Replica Setup
```
         Application
        /     |      \
       /      |       \
   Write    Read     Read
     |        |        |
  Primary  Replica1  Replica2
     |
     +---async replication---> Replica1
     +---async replication---> Replica2
```

### Cassandra Quorum
```
N=3 replicas, W=2, R=2 (W+R > N => consistent reads)

Write: Client --> Node A (coord) --> Node A, B, C
                                     (2 of 3 ack required)

Read:  Client --> Node A (coord) --> Node A, B
                                     (2 of 3, latest wins)
```

### ACID Transaction Flow
```
BEGIN TRANSACTION
  UPDATE accounts SET balance = balance - 100 WHERE id = 1;
  UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT
    |
    v
WAL (Write-Ahead Log) persisted to disk BEFORE commit acks
    |
    v
Data pages updated in buffer pool
    |
    v
Committed (durable, atomic)
```

---

## 9. Real-World Examples

### Amazon
- **DynamoDB**: Key-value/document store for shopping cart, sessions, product catalog.
- **Aurora**: MySQL/PostgreSQL-compatible relational DB with 6-way replication across 3 AZs.
- **Redshift**: Columnar data warehouse for analytics.
- Orders/payments use ACID relational DBs; catalog/sessions use DynamoDB for scale.

### Netflix
- **Cassandra**: Stores viewing history, playback state, user activity (write-heavy, append).
- **MySQL**: Account, billing, and subscription data (ACID required).
- **EVCache (Redis)**: Session and metadata caching.

### Twitter
- **MySQL** with heavy sharding for tweet storage (moved to Manhattan, a proprietary store).
- **Cassandra** for social graph and timelines.
- **Snowflake** (their ID generator) for globally unique, roughly time-ordered tweet IDs.

### Google
- **Bigtable**: Wide-column store for Search indexes, Maps, Gmail.
- **Spanner**: Globally distributed relational DB with external consistency (TrueTime API).
- **Firestore**: Document store for Firebase apps.

### Uber
- **Schemaless** (MySQL-based): Custom wide-column layer on top of MySQL.
- **PostgreSQL** for core trip data with heavy replication.
- Migrated from PostgreSQL to MySQL due to write-ahead log efficiency differences at scale.

---

## 10. Tradeoffs

| Dimension | SQL | NoSQL |
|-----------|-----|-------|
| Schema | Rigid, enforced | Flexible, schemaless |
| Consistency | Strong (ACID) | Eventual (BASE) |
| Query flexibility | High (SQL) | Limited (access-pattern-driven) |
| Horizontal scaling | Hard (distributed SQL helps) | Easier (built for distribution) |
| Joins | Native | Application-level or denormalized |
| Transactions | Multi-row, multi-table | Often single-row only |
| Maturity | Decades, well-understood | Varies by system |

---

## 11. When to Use / When NOT to Use

### SQL — Use When:
- Data has complex relationships requiring joins.
- Strong consistency and ACID transactions are required (payments, inventory).
- Ad-hoc querying needs are unknown at design time.
- Team is more familiar with relational modeling.

### SQL — Avoid When:
- Massive write throughput exceeds a single primary's capacity.
- Data is hierarchical/nested (JSON documents) — schema rigidity becomes a burden.
- Access pattern is purely key-value lookups.

### NoSQL — Use When:
- Extremely high write throughput (IoT, logging, analytics).
- Access patterns are well-known and simple (lookup by ID, range by timestamp).
- Schema flexibility is needed (evolving product attributes).
- Horizontal scalability is a primary requirement.

### NoSQL — Avoid When:
- Complex transactional integrity across multiple entities is required.
- Team needs ad-hoc analytical queries.
- Strong consistency is non-negotiable.

---

## 12. Common Pitfalls

1. **Missing indexes on foreign keys**: Every join condition should have an index. Unmissed FK indexes cause full table scans.
2. **N+1 query problem**: Fetching a list, then querying each item individually. Use JOINs or batch fetches.
3. **Selecting all columns (`SELECT *`)**: Fetches unnecessary data; prevents covering indexes.
4. **No connection pooling**: New DB connection per request = 5-50ms overhead + connection limit exhaustion.
5. **Over-normalization for read-heavy workloads**: Joins are expensive at scale; consider selective denormalization.
6. **Schema migrations without a plan**: Adding a column to a billion-row table locks the table in MySQL. Use pt-online-schema-change or pg_repack.
7. **Ignoring replication lag**: Read replicas can be seconds behind. Reading immediately after writing to replica returns stale data.
8. **Not using EXPLAIN**: Assuming queries are using indexes without verifying via query planner.
9. **Storing large blobs in DB**: Images, PDFs in database columns waste memory and slow replication. Use object storage (S3) and store URLs.
10. **Poor shard key selection**: Monotonically increasing keys (auto-increment) cause hot partitions in distributed systems.

---

## 13. Technologies & Tools

| Technology | Type | Notes |
|------------|------|-------|
| PostgreSQL | Relational SQL | Feature-rich, JSONB support, great for most use cases |
| MySQL / Aurora | Relational SQL | Web workloads, AWS-native Aurora adds replication |
| CockroachDB | Distributed SQL | Geo-distributed ACID, Postgres-compatible |
| Google Spanner | Distributed SQL | Global consistency via TrueTime |
| MongoDB | Document | Flexible schema, rich queries, ACID in v4+ |
| Cassandra | Wide-column | Massive write scale, tunable consistency |
| DynamoDB | Key-value + document | Fully managed, predictable performance |
| Redis | Key-value | In-memory, also used as primary DB for some use cases |
| Neo4j | Graph | Cypher query language, ACID |
| Elasticsearch | Search + analytics | Inverted index, full-text search, log analytics |
| ClickHouse | Columnar OLAP | Extremely fast analytical queries |
| Snowflake | Cloud data warehouse | OLAP at scale, separation of compute/storage |

---

## 14. Interview Questions with Answers

**Q1: When would you choose NoSQL over SQL?**
A: When the access pattern is well-defined and simple (key-value or range queries), when horizontal write scalability is paramount, when schema flexibility is needed (evolving documents), or when the data model naturally fits a non-relational structure (graphs, time-series, documents).

**Q2: What is the N+1 query problem and how do you fix it?**
A: Fetching a list of N entities, then making one additional query per entity. Fix: use a JOIN in a single query, or use an ORM eager-loading feature (`include`/`select_related`), or batch fetch with a WHERE id IN (...) clause.

**Q3: Explain the difference between clustered and non-clustered indexes.**
A: A clustered index defines the physical order of rows on disk (there can only be one per table — in MySQL InnoDB, the primary key is always clustered). A non-clustered index is a separate structure that stores the indexed column(s) plus a pointer to the actual row. Clustered indexes make range scans on the primary key very fast.

**Q4: What is a covering index?**
A: An index that contains all columns required to satisfy a query — the query planner never needs to access the actual table rows (index-only scan). Example: query `SELECT name FROM users WHERE email = ?` — an index on `(email, name)` covers it entirely.

**Q5: What is database sharding and when would you use it?**
A: Horizontal partitioning of data across multiple database nodes, each holding a subset of rows. Use it when a single DB node cannot handle the write throughput or data volume. The shard key determines which node stores each row.

**Q6: What is eventual consistency and when is it acceptable?**
A: A consistency model where, given no new updates, all replicas will converge to the same value over time. Acceptable when slight staleness is tolerable: social media likes/counts, product view counts, recommendation data. Not acceptable for: bank balances, inventory, authentication tokens.

**Q7: How would you handle schema migrations on a live production database?**
A: Use backward-compatible migrations: add columns before removing them, use tools like Flyway/Liquibase, use online schema change tools (pt-osc, gh-ost for MySQL; pg_repack for PostgreSQL) to avoid table locks, deploy application code that handles both old and new schema, then clean up old schema in a follow-up migration.

**Q8: What are the tradeoffs of read replicas?**
A: Pros: Scale read throughput, geographic distribution, offload analytics. Cons: Replication lag causes stale reads, adds operational complexity, failover to replica requires application reconfiguration or use of a proxy (ProxySQL, RDS Proxy).

**Q9: Explain the difference between optimistic and pessimistic locking.**
A: Pessimistic locking: lock the row when reading, preventing concurrent modifications until the lock is released (`SELECT FOR UPDATE`). Suitable when conflicts are frequent. Optimistic locking: no lock on read; on write, verify a version counter hasn't changed — if it has, retry. Better for low-contention scenarios.

**Q10: What is a write-ahead log (WAL)?**
A: A durability mechanism where changes are written to a sequential log (WAL) before being applied to data pages. On crash recovery, the WAL is replayed to restore committed transactions. It enables ACID durability and is the foundation of streaming replication in PostgreSQL.

**Q11: How does Cassandra achieve high write throughput?**
A: Cassandra uses LSM-trees (Log-Structured Merge trees). Writes go to an in-memory memtable + commit log (sequential disk write). Memtable is periodically flushed to immutable SSTable files on disk. SSTables are periodically compacted. Sequential writes are extremely fast; reads are more complex (merge multiple SSTables).

**Q12: What is the difference between OLTP and OLAP databases?**
A: OLTP (Online Transaction Processing): high-throughput short transactions, normalized schemas, row-oriented storage, low latency. Examples: PostgreSQL, MySQL. OLAP (Online Analytical Processing): complex aggregations over large datasets, denormalized star/snowflake schemas, columnar storage, high throughput analytical queries. Examples: Redshift, BigQuery, ClickHouse.

---

## 15. Best Practices

1. **Design for access patterns**: In NoSQL, design your schema around the queries you'll run, not the entity relationships.
2. **Use connection pooling**: PgBouncer (PostgreSQL), ProxySQL (MySQL) — never create raw connections per request.
3. **Index foreign keys**: Any column used in a JOIN or WHERE clause should be indexed.
4. **Use EXPLAIN ANALYZE**: Always verify query plans on production-like data volumes.
5. **Set up replication before you need it**: Retrospectively enabling replication on a large DB is painful.
6. **Avoid `SELECT *`**: Always specify required columns; it prevents table bloat from affecting query performance.
7. **Use pagination**: Never return unbounded result sets; use LIMIT/OFFSET or cursor-based pagination.
8. **Enforce constraints in the DB**: Don't rely solely on application logic; use DB-level NOT NULL, UNIQUE, and FK constraints.
9. **Monitor slow query log**: Enable and review regularly.
10. **Test with production-like data volume**: Queries that work fine on 10K rows may fail on 100M rows.

---

## Cross-Perspective: LLD Connections

**LLD View — Design Patterns That Implement Database Design**

- **Repository** — Abstracts data access behind an interface (`UserRepository`, `OrderRepository`). The service layer depends on the interface; the concrete class uses JDBC, JPA, or a NoSQL driver. Swapping databases requires only a new Repository implementation, not touching business logic.
- **Factory** — A connection factory creates and pools database connections, hiding driver-specific construction details (URL, credentials, pool size) from callers.
- **Strategy** — Query routing (read replica vs primary write), transaction isolation levels, and index selection strategies are encapsulated as interchangeable Strategy implementations configured per-operation.
- **Decorator** — Caching and query-logging decorators wrap Repository calls, adding observability and performance without modifying the Repository implementations.

---

## 16. Case Study: Designing a Database for an E-Commerce Platform

**Requirements:** 10M products, 50M users, 100M orders/year. Reads: 100K/sec. Writes: 5K/sec. Latency: <50ms for product page, <200ms for order placement.

**Design:**

1. **Product Catalog** → MongoDB (document store): Products have variable attributes (a shoe has size/color, a book has ISBN/author). Flexible schema is ideal. Index on category, price, brand for filtering.

2. **User Accounts + Auth** → PostgreSQL: Structured, relational. User → Address (1:N), User → PaymentMethod (1:N). ACID for account updates.

3. **Orders + Payments** → PostgreSQL with ACID: Critical financial data. Tables: orders, order_items, payments, refunds. Use transactions to ensure atomicity of order placement + inventory decrement.

4. **Inventory** → PostgreSQL with pessimistic locking: `SELECT FOR UPDATE` on inventory row during order placement to prevent overselling.

5. **Session + Cart** → Redis: Fast key-value, TTL-based expiry, write-back persistence to DB.

6. **Search** → Elasticsearch: Full-text search across product name, description. Sync from MongoDB via change streams.

7. **Analytics** → Redshift/BigQuery: Nightly ETL from OLTP stores. Ad-hoc queries on orders, revenue, funnel analysis.

8. **Read scaling** → PostgreSQL read replicas for user/order reads. Redis caching for product detail pages (cache-aside, 5-minute TTL).

**Result:** Product pages at 8ms P99 (Redis hit), order placement at 120ms P99 (DB write), full-text search at 30ms P99 (Elasticsearch).
