# SQL Query Optimization

## 1. Concept Overview

SQL query optimization is the practice of making database queries execute faster by choosing better execution plans, improving data access patterns, and restructuring queries to match what the query planner can efficiently handle. The goal: transform O(n) sequential scans into O(log n) index lookups, eliminate redundant I/O, and push work down to the database rather than the application.

---

## 2. Intuition

The query planner is a cost estimator, not a mind reader. It uses statistics to estimate row counts and chooses the plan with the lowest estimated cost. When estimates are wrong (stale statistics, non-uniform distributions), plans are wrong. Understanding the plan lets you guide the optimizer.

- **Key insight**: The biggest wins come from eliminating N+1 queries (application-level), adding covering indexes (eliminate heap fetches), and fixing keyset pagination (eliminate O(n) OFFSET scans). Micro-optimizations rarely matter if these fundamentals are wrong.

---

## 3. Core Principles

### Reading EXPLAIN ANALYZE

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT u.name, COUNT(o.id) AS order_count
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.created_at > '2024-01-01'
GROUP BY u.id, u.name
ORDER BY order_count DESC
LIMIT 10;

-- Example output:
Limit  (cost=12500..12500 rows=10 width=36) (actual time=450.2..450.2 rows=10 loops=1)
  ->  Sort  (cost=12500..12600 rows=40000 width=36) (actual time=450.1..450.2 rows=10 loops=1)
        Sort Key: (count(o.id)) DESC
        Sort Method: top-N heapsort  Memory: 25kB
        ->  HashAggregate  (cost=10000..11000 rows=40000 width=36) (actual time=380.0..420.0 rows=40000 loops=1)
              ->  Hash Left Join  (cost=500..8000 rows=200000 width=20) (actual time=10.0..300.0 rows=200000 loops=1)
                    Hash Cond: (o.user_id = u.id)
                    ->  Seq Scan on orders o  (cost=0..5000 rows=200000 width=8) (actual time=0.05..100.0 rows=200000 loops=1)
                    ->  Hash  (cost=400..400 rows=40000 width=12) (actual time=9.0..9.0 rows=40000 loops=1)
                          ->  Seq Scan on users u  (cost=0..400 rows=40000 width=12) (actual time=0.05..6.0 rows=40000 loops=1)
                                Filter: (created_at > '2024-01-01')
                                Rows Removed by Filter: 5000

-- Key numbers:
-- cost=first_row..total (in planner cost units, not ms)
-- actual time=startup_ms..total_ms
-- rows=actual  (vs cost= estimate — mismatch means bad stats)
-- loops=N means this node executed N times (common in nested loop inner side)
-- Buffers: shared hit=N (buffer pool) read=N (disk)
```

---

## 4. Types / Architectures / Strategies

### Join Algorithms

**Nested Loop Join**: For each row in outer table, scan inner table (ideally via index).

```
FOR each outer_row IN outer_table:
    FOR each inner_row in index_scan(inner_table, outer_row.join_key):
        emit(outer_row, inner_row)
```

Best when: outer table is small (< ~1000 rows) AND inner table has an index on join column. Cost: O(outer_rows × inner_index_lookup_cost). Terrible without inner index.

**Hash Join**: Build a hash table from the smaller table, probe with the larger.

```
hash_table = {}
FOR each build_row IN smaller_table:
    hash_table[build_row.join_key].append(build_row)
FOR each probe_row IN larger_table:
    emit(probe_row, hash_table[probe_row.join_key])
```

Best when: both tables are large, no useful index, result needs many rows from both. Memory: `work_mem` (default 4MB in PostgreSQL). If hash table spills to disk, performance degrades significantly.

**Merge Join**: Both inputs sorted on join key, merge in one pass.

```
sorted_outer = sort(outer_table, join_key)  -- or use existing index order
sorted_inner = sort(inner_table, join_key)
merge(sorted_outer, sorted_inner)
```

Best when: both inputs are already sorted (index scan output), or sort is cheap. CPU efficient, minimal memory.

### CBO Statistics

The cost-based optimizer (CBO) estimates rows using:
- `pg_statistic`: per-column histograms (100 buckets default), most-common values (MCVs) with frequencies, n_distinct, correlation
- `pg_class.reltuples`: estimated total rows

```sql
-- Check statistics quality:
SELECT attname, n_distinct, correlation
FROM pg_stats
WHERE tablename = 'orders' AND attname = 'customer_id';
-- n_distinct: negative = fraction of total rows that are distinct
--             positive = absolute count
-- correlation: 1.0 = perfectly correlated with physical order (good for range scans)
--              0.0 = random (range scans have random I/O, planner prefers bitmap scan)
--             -1.0 = reverse order

-- Improve statistics resolution:
ALTER TABLE orders ALTER COLUMN customer_id SET STATISTICS 500; -- More histogram buckets
ANALYZE orders;
```

### CTEs as Optimization Fences (PostgreSQL < 12)

```sql
-- PostgreSQL < 12: CTEs are always materialized (calculated once, stored)
-- This prevents the planner from pushing predicates inside the CTE

WITH active_users AS (
    SELECT id, name FROM users WHERE status = 'active'  -- Materialized: scans ALL active users
)
SELECT * FROM active_users WHERE id = 42;
-- Planner CANNOT push "id = 42" inside the CTE → full scan of active users, then filter

-- Fix in PostgreSQL 11 and below: inline with NOT MATERIALIZED hint or use subquery
SELECT * FROM (
    SELECT id, name FROM users WHERE status = 'active'
) u WHERE id = 42;
-- Planner CAN push id=42 into the subquery

-- PostgreSQL 12+: CTEs are automatically inlined unless MATERIALIZED keyword used
WITH active_users AS MATERIALIZED (  -- Explicit materialization
    SELECT id, name FROM users WHERE status = 'active'
)
SELECT * FROM active_users WHERE id = 42;
```

---

## 5. Architecture Diagrams

```
QUERY OPTIMIZATION DECISION TREE:

Is query slow?
    |
    ├── EXPLAIN ANALYZE shows Seq Scan with millions of rows?
    |       → Missing index or wrong index column order
    |       → Fix: CREATE INDEX CONCURRENTLY on filtered/joined columns
    |
    ├── EXPLAIN shows correct index but estimated rows << actual rows?
    |       → Stale statistics (ANALYZE table_name)
    |       → Increase statistics target: ALTER TABLE SET STATISTICS 500
    |
    ├── N+1 pattern? (ORM logs show 1+100 queries for 100 items)
    |       → Fix: JOIN FETCH (JPA), includes (Rails), DataLoader (GraphQL)
    |
    ├── OFFSET/LIMIT with large OFFSET (> 10,000)?
    |       → Fix: keyset pagination (cursor-based)
    |
    ├── Hash Join showing Memory Usage + disk spill?
    |       → Increase work_mem for this session
    |       → SET work_mem = '256MB'; -- for this session only
    |
    └── Correct plan but slow due to data volume?
            → Partition table, add covering index, denormalize
```

---

## 6. How It Works — Detailed Mechanics

### Keyset Pagination vs OFFSET

```sql
-- OFFSET/LIMIT: O(n) problem
SELECT id, title, created_at FROM posts
ORDER BY created_at DESC
LIMIT 20 OFFSET 100000;
-- Database MUST fetch and discard 100,000 rows before returning 20
-- At page 1000: fetches and discards 20,000 rows each time
-- Latency grows linearly with page number

-- Keyset pagination: O(log n) always
-- First page:
SELECT id, title, created_at FROM posts
ORDER BY created_at DESC, id DESC
LIMIT 20;
-- Returns last row: created_at='2024-07-01 10:00', id=9876

-- Next page (cursor = last seen values):
SELECT id, title, created_at FROM posts
WHERE (created_at, id) < ('2024-07-01 10:00', 9876)  -- Row-value comparison
ORDER BY created_at DESC, id DESC
LIMIT 20;
-- Index on (created_at DESC, id DESC) → O(log n) to find cursor position, O(1) per page
-- Latency is constant regardless of page depth

-- Index to support this:
CREATE INDEX idx_posts_cursor ON posts (created_at DESC, id DESC);
```

### N+1 Detection and Fix

```java
// Broken (N+1): Hibernate/JPA
List<Order> orders = orderRepo.findByCustomerId(42L);
for (Order order : orders) {
    String productName = order.getProduct().getName(); // Fires a separate query per order!
}
// 1 query for orders + N queries for products = N+1

// Fix 1: JOIN FETCH
List<Order> orders = entityManager.createQuery(
    "SELECT DISTINCT o FROM Order o JOIN FETCH o.product WHERE o.customerId = :cid",
    Order.class).setParameter("cid", 42L).getResultList();
// 1 query with JOIN

// Fix 2: @EntityGraph
@EntityGraph(attributePaths = {"product", "customer"})
List<Order> findByCustomerId(Long customerId);

// Fix 3: Batch loading
@BatchSize(size = 100)  // Hibernate: load products in batches of 100
private Product product;
```

Detection: enable `spring.jpa.show-sql=true` or use DataDog APM with N+1 detection. Look for the same query repeated N times with different ID parameters.

### Window Functions

```sql
-- Without window function: correlated subquery (O(n²))
SELECT id, customer_id, total,
    (SELECT SUM(total) FROM orders o2 WHERE o2.customer_id = o.customer_id
     AND o2.created_at <= o.created_at) AS running_total
FROM orders o;

-- With window function: single pass (O(n log n))
SELECT id, customer_id, total,
    SUM(total) OVER (PARTITION BY customer_id ORDER BY created_at ROWS UNBOUNDED PRECEDING) AS running_total,
    ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY total DESC) AS rank_by_total,
    LAG(total, 1) OVER (PARTITION BY customer_id ORDER BY created_at) AS prev_order_total,
    LEAD(total, 1) OVER (PARTITION BY customer_id ORDER BY created_at) AS next_order_total
FROM orders;
-- Window functions computed in one scan, massively more efficient

-- Top-N per group (efficient with window functions):
SELECT * FROM (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY category ORDER BY revenue DESC) AS rn
    FROM products
) ranked WHERE rn <= 3;
-- Returns top 3 products per category
```

### Batch Inserts

```sql
-- Single row inserts: N network round trips
INSERT INTO events (user_id, type) VALUES (1, 'click');
INSERT INTO events (user_id, type) VALUES (2, 'view');
... × 10000 rows → 10000 network round trips

-- Multi-value INSERT: 1 network round trip
INSERT INTO events (user_id, type) VALUES
    (1, 'click'),
    (2, 'view'),
    ...; -- up to a few thousand rows per statement

-- COPY (PostgreSQL): fastest bulk load
COPY events (user_id, type) FROM stdin;
1	click
2	view
\.
-- COPY is 10-100x faster than INSERT for bulk loads
-- No statement parsing overhead, bypasses some constraint checking

-- JDBC batch (Java):
PreparedStatement ps = conn.prepareStatement("INSERT INTO events VALUES (?, ?)");
for (Event e : events) {
    ps.setLong(1, e.userId);
    ps.setString(2, e.type);
    ps.addBatch();
    if (++count % 1000 == 0) ps.executeBatch(); // Flush every 1000 rows
}
ps.executeBatch();
```

---

## 7. Real-World Examples

- **Keyset pagination**: Twitter's timeline API — cursor is the tweet ID of the last seen tweet. O(log n) regardless of how far back you scroll.
- **N+1 in production**: A Python/Django app making 500 queries per request — 1 for the order list + 499 for related data. Query time: 2.5s. After `select_related()`: 1 query, 50ms.
- **Statistics failure**: A SaaS app bulk-imported 50M new rows. Planner still thought the table had 500K rows (stats from before load). After ANALYZE: query plan changed from seq scan to index scan, latency dropped 10x.
- **work_mem too low**: A hash join query was spilling to disk (HashJoin node showing "Batches: 8"). Setting `SET work_mem = '512MB'` for the session: Batches dropped to 1, query time from 45s to 3s.

---

## 8. Tradeoffs

| Technique | Benefit | Cost |
|-----------|---------|------|
| Covering index | Eliminates heap fetches | Larger index, more write overhead |
| Keyset pagination | O(log n) vs O(n) | Less flexible (no random page access), requires cursor state |
| JOIN FETCH | Eliminates N+1 | May fetch too much data (Cartesian if multiple collections) |
| work_mem increase | Faster hash/sort | Memory pressure if many concurrent sessions × high work_mem |
| Partial index | Smaller, faster index | Only useful for constant filter conditions |
| Denormalization | Eliminate joins | Data duplication, update anomalies |
| Materialized views | Pre-computed aggregates | Stale if not refreshed; maintenance overhead |

---

## 9. When to Use / When NOT to Use

**Increase work_mem**: For a single heavy analytics session. Do not set globally to high values — each query can use N×work_mem (N = sort/hash operations in the plan), and at 100 concurrent sessions this exhausts RAM.

**Denormalization**: When join overhead is measured and proven to be the bottleneck, and consistency can be maintained via triggers or application logic. Do not denormalize prematurely.

**Materialized views**: For expensive aggregations needed by many queries. Refresh frequency depends on data freshness requirements. Do not use if data changes frequently and stale aggregates are unacceptable.

**Lateral joins**: For correlated top-N queries (e.g., top 3 products per category). Use `LATERAL` in PostgreSQL to reference outer query columns in a subquery FROM clause.

---

## 10. Common Pitfalls

**Pitfall 1: LIKE with leading wildcard prevents index use**
```sql
-- Cannot use index on (email):
SELECT * FROM users WHERE email LIKE '%@example.com';
-- Fix 1: pg_trgm extension + GIN trigram index (supports any LIKE)
CREATE EXTENSION pg_trgm;
CREATE INDEX idx_trgm_email ON users USING GIN (email gin_trgm_ops);
-- Now LIKE '%@example.com' uses index

-- Fix 2: Store domain separately and index it
ALTER TABLE users ADD COLUMN email_domain TEXT GENERATED ALWAYS AS (split_part(email, '@', 2)) STORED;
CREATE INDEX idx_email_domain ON users (email_domain);
```

**Pitfall 2: Implicit type cast breaks index**
```sql
-- Table: sessions, column: user_id INTEGER, index on (user_id)
-- ORM sends user_id as string:
SELECT * FROM sessions WHERE user_id = '42';  -- PostgreSQL: implicit cast, may skip index
-- Fix: ensure ORM sends correct type (integer) or cast in query:
SELECT * FROM sessions WHERE user_id = 42::integer;
```

**Pitfall 3: EXISTS vs IN — different behavior with NULLs**
```sql
-- IN with subquery that can return NULLs:
SELECT * FROM orders WHERE customer_id NOT IN (SELECT id FROM customers WHERE vip = false);
-- If ANY row in subquery returns NULL, NOT IN returns 0 rows (SQL NULL semantics)

-- Fix: use NOT EXISTS (handles NULLs correctly)
SELECT * FROM orders o WHERE NOT EXISTS (
    SELECT 1 FROM customers c WHERE c.id = o.customer_id AND c.vip = false
);
```

**Pitfall 4: UPDATE statistics_target too low for skewed data**
A table with a `country` column storing 99% 'US' and 1% other values. Default 100-bucket histogram cannot capture the 'US' spike. Planner estimates 1% for 'US' queries, chooses seq scan. Fix:
```sql
ALTER TABLE orders ALTER COLUMN country SET STATISTICS 1000;
ANALYZE orders;
-- More buckets → better estimate of 99% → planner chooses correct plan
```

**Pitfall 5: Forgetting EXPLAIN ANALYZE uses real data**
EXPLAIN without ANALYZE shows estimated costs only. EXPLAIN ANALYZE actually executes the query. For slow queries: always use `EXPLAIN (ANALYZE, BUFFERS)`. Check: if `actual rows` >> `rows` estimate by >10x, statistics are stale.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `EXPLAIN (ANALYZE, BUFFERS)` | Query plan with actual timing and I/O |
| `pg_stat_statements` | Top queries by total time, mean time, calls |
| `auto_explain` | Auto-log plans for slow queries |
| `pgBadger` | PostgreSQL log analyzer, slow query report |
| `pev2` (explain.dalibo.com) | Visual EXPLAIN ANALYZE rendering |
| `hypopg` | PostgreSQL: test hypothetical indexes without building |
| MySQL `pt-query-digest` | Analyze MySQL slow query log |
| MySQL `sys.statement_analysis` | Top queries by exec time |
| `DataDog APM` | Trace N+1 patterns in production |
| `New Relic` | Database query tracing and analysis |

---

## 12. Interview Questions with Answers

**Q: Walk me through how the query planner decides between hash join and nested loop.**
The planner estimates cost for each join algorithm using statistics. Nested loop: outer_rows × inner_index_cost — excellent when outer is small (< 1000 rows) and inner has an index. Hash join: cost to build hash table from smaller input + cost to probe with larger input — excellent when both tables are large and no sorting is needed. The planner compares: if nested loop cost < hash join cost → nested loop; otherwise hash join. It also considers the available `work_mem` — a hash join that spills to disk costs much more. Merge join is chosen when both inputs are already sorted on the join key (index scan output in sorted order).

**Q: How does keyset pagination outperform OFFSET/LIMIT at scale?**
OFFSET N requires the database to fetch and discard N rows before returning the requested page. At page 1000 of 20 results, that's discarding 20,000 rows — O(n) per page request. With an index on the sort column, the database still does an index scan to position N entries and then discards them. Keyset pagination: stores the last-seen values (e.g., `created_at` and `id`) as a cursor. The next query uses `WHERE (created_at, id) < (cursor_ts, cursor_id)` — the planner seeks directly to that position in the index using O(log n) traversal, then returns the next 20 rows. Constant time regardless of page depth.

**Q: What is the N+1 problem and how do you detect it in production ORM code?**
The N+1 problem: loading N parent records, then executing one query per parent to load associated child records. Example: fetching 100 orders then loading the product for each → 1 + 100 = 101 queries. Detection: (1) Enable SQL logging — look for the same query repeated N times with different IDs. (2) Database-side: `pg_stat_statements` — queries with 1000+ calls and identical normalized form. (3) APM tools (DataDog, New Relic) — N+1 detection alerts. (4) In tests: assert that only N queries fired for an operation. Fix: use JOIN FETCH (JPA), select_related/prefetch_related (Django), includes (ActiveRecord), or DataLoader pattern (GraphQL). The DataLoader batches N individual loads into one batch query per tick.

**Q: What are window functions and when do they replace subqueries?**
Window functions compute aggregations over a sliding window of rows related to the current row, without collapsing rows (unlike GROUP BY). They replace correlated subqueries that scan the same table multiple times: `ROW_NUMBER() OVER (PARTITION BY category ORDER BY revenue DESC)` replaces "SELECT MAX(revenue) FROM products WHERE category = t.category" repeated per row. Common functions: `ROW_NUMBER()` (unique sequential number), `RANK()` (handles ties), `DENSE_RANK()` (rank without gaps), `LAG(col, n)` (value from n rows before), `LEAD(col, n)` (value from n rows after), `SUM/AVG/MAX OVER (PARTITION BY ... ORDER BY ... ROWS ...)`. A correlated subquery with N rows executes N subqueries; the equivalent window function executes one pass.

**Q: What are the performance implications of CTEs in PostgreSQL?**
PostgreSQL < 12: CTEs (WITH clauses) are always materialized — the CTE result is computed once and stored in a temporary structure. The planner cannot push predicates from the outer query into the CTE, preventing index use. A query like `WITH base AS (SELECT * FROM large_table) SELECT * FROM base WHERE id = 42` results in a full scan of `large_table` regardless of the index on `id`. PostgreSQL 12+: CTEs are automatically inlined (treated like a subquery) unless you specify `WITH ... AS MATERIALIZED (...)`. Best practice for PostgreSQL < 12: use subqueries in FROM clause instead of CTEs for queries where predicate pushdown is needed.

**Q: How does the hash join use work_mem and what happens when it spills?**
The planner allocates `work_mem` (default 4MB) for the hash table of the smaller input. If the smaller input fits in `work_mem`, all rows are in memory and the probe phase is pure memory operations. If the input exceeds `work_mem`, PostgreSQL uses "batch hashing": splits the hash table into batches that fit in memory, processes one batch at a time (requires re-reading the probe side multiple times). EXPLAIN output: `Batches: 8` means 8 rounds, which means disk I/O for 7 re-reads of the probe side. Fix: `SET work_mem = 'N MB'` for the session before the query. Warning: work_mem applies per sort/hash operation, per query. At 100 concurrent sessions, global `work_mem = 1GB` could allocate 100GB.

**Q: What is the difference between EXISTS and IN for subqueries in performance terms?**
For correlated subqueries: EXISTS short-circuits (returns immediately when first match found), while IN must scan all rows. `WHERE id IN (SELECT user_id FROM premium_users)` — scans all premium_users rows and builds a hash set. `WHERE EXISTS (SELECT 1 FROM premium_users WHERE user_id = o.id)` — for each outer row, stops at the first match. Modern PostgreSQL optimizes both to hash semi-joins for non-correlated cases, so the performance difference is minimal for simple cases. The critical behavioral difference: `NOT IN` with NULLs in the subquery returns no results (NULL semantics), while `NOT EXISTS` handles NULLs correctly. Always prefer NOT EXISTS over NOT IN.

**Q: Explain lateral joins and when to use them.**
A lateral join allows a subquery in the FROM clause to reference columns from tables to its left. Without LATERAL, a subquery cannot reference outer columns. Use case: top-N per group without window functions, or calling a set-returning function per row. Example:
```sql
SELECT u.id, recent.title, recent.created_at
FROM users u
CROSS JOIN LATERAL (
    SELECT title, created_at FROM posts
    WHERE user_id = u.id
    ORDER BY created_at DESC
    LIMIT 3
) recent;
-- For each user, fetches the 3 most recent posts (using index)
-- Window function alternative works too but LATERAL can be simpler for joins
```
LATERAL is essential when the subquery result depends on the outer row.

**Q: How do you identify and fix a query causing excessive disk reads (Buffers: read=N in EXPLAIN)?**
High `shared read` in EXPLAIN BUFFERS means pages were fetched from disk (not from buffer pool). Steps: (1) Check if the table/index fits in `shared_buffers` — if not, the working set doesn't fit in memory. (2) Check if this is a first run (cold cache) vs steady-state — warm the cache with a dummy query or `pg_prewarm`. (3) Check index usage — seq scan reads entire table (all pages from disk); index scan reads only needed pages. (4) Check for unnecessary columns in SELECT — `SELECT *` reads more pages than `SELECT id, status`. Fix: add index, increase `shared_buffers`, or use a covering index so fewer pages are needed.

**Q: What is the purpose of ANALYZE and when should you run it manually?**
ANALYZE collects statistics about column distributions and stores them in `pg_statistic`. The planner uses these to estimate result sizes and choose optimal plans. Run manually: (1) After bulk loads (`COPY`, large INSERT) — autovacuum won't fire quickly enough. (2) After `pg_upgrade` — statistics are not transferred. (3) After changing `default_statistics_target` or per-column statistics. (4) When EXPLAIN shows estimate vs actual rows differ by >10x. In production, autovacuum handles ANALYZE automatically when `n_mod_since_analyze` > `autovacuum_analyze_threshold + autovacuum_analyze_scale_factor × reltuples`. Do not disable autovacuum for ANALYZE.

**Q: How do you optimize a GROUP BY query that runs too slowly?**
Analysis steps: (1) Check if there's an index on the GROUP BY + WHERE columns. If not, add one. (2) EXPLAIN: if "HashAggregate" node shows large memory usage or "Disk: NNN bytes" → increase `work_mem`. (3) If aggregating over nearly all rows, consider a materialized view that pre-aggregates and refreshes periodically. (4) Check if a partial aggregate pushdown is possible (PostgreSQL supports parallel aggregation). (5) For real-time dashboard queries, consider moving to ClickHouse or TimescaleDB continuous aggregates for heavy OLAP. (6) Ensure statistics are up-to-date — wrong cardinality estimate causes wrong join order in GROUP BY + JOIN queries.

**Q: What is the difference between seq_page_cost and random_page_cost in PostgreSQL?**
`seq_page_cost` (default 1.0) and `random_page_cost` (default 4.0) are cost model constants in arbitrary units. They represent the planner's estimate of I/O cost: sequential I/O (reading pages in order) vs random I/O (seeking to a random page). The ratio random/sequential = 4.0 reflects HDD seek time. For SSD storage, random I/O is much cheaper (10-50x faster than HDD random I/O). Set `random_page_cost = 1.1` for SSDs, `1.5` for NVMe RAID. Impact: with `random_page_cost=4`, the planner prefers sequential scans for queries touching > ~25% of rows. With `random_page_cost=1.1`, it prefers index scans for much lower selectivities — often the right choice for column-store-cached working sets.

**Q: What is partial aggregate and how does it enable parallel query?**
Parallel query: PostgreSQL spawns N worker processes to scan table partitions in parallel. Each worker computes a partial aggregate over its data (e.g., partial SUM, partial COUNT). The gather node in the leader process merges partial aggregates into the final result (e.g., SUM all partial SUMs). This enables linear speedup for aggregation queries proportional to the number of workers (`max_parallel_workers_per_gather`). Partial aggregate is only useful if the aggregate function is associative and commutative (SUM, COUNT, MIN, MAX are; user-defined aggregates may not be). `EXPLAIN` shows `Partial Aggregate` and `Gather` nodes.

**Q: How do you analyze and fix a query that performs well in development but slowly in production?**
Common causes: (1) Data volume: dev has 10K rows, prod has 100M. Plans that work for small data (sequential scan) are catastrophic for large data. Fix: always test with production-scale data or statistics-only clone. (2) Statistics difference: dev data is uniform, prod data is skewed. Fix: `ANALYZE` prod regularly, increase statistics target. (3) Connection pool settings: dev uses 5 connections, prod uses 200 — different `work_mem` effective usage. (4) Concurrent load: dev is single-user, prod has 500 concurrent. Lock waits, buffer contention. Fix: load test in staging with realistic concurrency. (5) Planner settings difference: dev uses defaults, prod uses custom settings. Always maintain parity.

**Q: What is the cost of a count(*) on a large table and how do you optimize it?**
`SELECT COUNT(*) FROM large_table` requires scanning all visible rows to count non-deleted ones (MVCC requirement: deleted rows are still physically present). For a 100M-row table, this can take 30-60 seconds. Optimizations: (1) Use `pg_class.reltuples` for approximate count (updated by VACUUM/ANALYZE, not exact): `SELECT reltuples::BIGINT FROM pg_class WHERE relname = 'large_table'`. (2) Add a WHERE clause so an index can narrow the scan: `SELECT COUNT(*) WHERE status = 'active' AND created_at > now() - interval '7 days'` — can use a partial index. (3) Maintain a counter table with triggers. (4) Use TimescaleDB continuous aggregates or materialized views for periodic count refreshes.

---

## 13. Best Practices

1. Run `EXPLAIN (ANALYZE, BUFFERS)` on every query added to production code paths.
2. Use keyset pagination for any paginated API — never use OFFSET > 10,000.
3. Detect N+1 in code review by enabling SQL logging in tests and asserting query count.
4. Set per-column statistics target to 500 for highly non-uniform columns (user_id, country).
5. For heavy analytic queries: set `work_mem = 'N MB'` at the session level, not globally.
6. After any bulk data load, run `ANALYZE table_name` explicitly.
7. Use EXISTS instead of IN for nullable subqueries; use NOT EXISTS instead of NOT IN always.
8. Index the join columns, not just the filter columns — poorly indexed joins cause full scans.
9. Use window functions instead of correlated subqueries for running totals, rankings, lag/lead.
10. Avoid `SELECT *` in application code — fetch only needed columns, especially for tables with TOAST.

---

## 14. Case Study

**Scenario**: An analytics API endpoint (`/api/reports/top-customers`) returns 500ms+ p99 latency. The query calculates the top 50 customers by total spend in the last 30 days.

**Original query (slow)**:
```sql
SELECT customer_id,
    (SELECT SUM(total) FROM orders WHERE customer_id = u.id
     AND created_at > now() - interval '30 days') AS spend_30d,
    (SELECT COUNT(*) FROM orders WHERE customer_id = u.id
     AND created_at > now() - interval '30 days') AS orders_30d
FROM customers u
ORDER BY spend_30d DESC NULLS LAST
LIMIT 50;
-- EXPLAIN: 2 correlated subqueries × 100,000 customers = 200,000 index lookups
-- Time: 4.2 seconds
```

**Optimization step 1: Replace correlated subqueries with window functions/GROUP BY**:
```sql
SELECT
    o.customer_id,
    SUM(o.total) AS spend_30d,
    COUNT(*) AS orders_30d
FROM orders o
WHERE o.created_at > now() - interval '30 days'
GROUP BY o.customer_id
ORDER BY spend_30d DESC
LIMIT 50;
-- EXPLAIN: one scan of orders (last 30 days with index), hash aggregate, sort
-- Time: 180ms (with index on created_at)
```

**Optimization step 2: Add covering index**:
```sql
CREATE INDEX CONCURRENTLY idx_orders_30d_covering
ON orders (created_at, customer_id) INCLUDE (total)
WHERE created_at > now() - interval '60 days';
-- Wait: partial index with now() is not immutable → cannot use as partial
-- Fix: use absolute date or no partial predicate:
CREATE INDEX CONCURRENTLY idx_orders_recent
ON orders (created_at DESC, customer_id) INCLUDE (total);
-- Query time: 25ms (index-only scan for last 30 days)
```

**Optimization step 3: Materialized view for sub-5ms response**:
```sql
CREATE MATERIALIZED VIEW customer_spend_30d AS
SELECT customer_id, SUM(total) AS spend_30d, COUNT(*) AS orders_30d
FROM orders WHERE created_at > now() - interval '30 days'
GROUP BY customer_id;

CREATE UNIQUE INDEX ON customer_spend_30d (customer_id);
CREATE INDEX ON customer_spend_30d (spend_30d DESC);

-- Refresh every 5 minutes (concurrently = no lock):
REFRESH MATERIALIZED VIEW CONCURRENTLY customer_spend_30d;

-- API query:
SELECT customer_id, spend_30d, orders_30d
FROM customer_spend_30d ORDER BY spend_30d DESC LIMIT 50;
-- Time: 1ms (pure index scan on small materialized view)
```

**Result**: 4,200ms → 1ms. The key insight was replacing correlated subqueries (exponential problem) with a single aggregation pass, then pre-computing the result with a materialized view for near-instant API responses.
