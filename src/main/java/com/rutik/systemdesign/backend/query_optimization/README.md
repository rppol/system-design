# Query Optimization

## 1. Concept Overview

Writing a correct SQL query is the beginning. Writing a query that scales to millions of rows, stays fast as data grows, and does not destroy database performance under load requires understanding how the database executes queries, what makes certain patterns catastrophically expensive, and how to diagnose problems in production. Query optimization is the skill that separates a developer who makes the database slow from one who keeps it fast.

This module covers EXPLAIN ANALYZE plan reading, the N+1 detection and fix workflow, pagination strategies at scale, batch insert patterns, and prepared statement plan caching.

---

## 2. Intuition

> **One-line analogy**: A query is a request to the database; the execution plan is the database's chosen route. EXPLAIN ANALYZE is the GPS that shows you the route it actually took, how long each road segment took, and whether it got stuck in traffic (seq scan through millions of rows). Optimization is about finding a faster route.

**Mental model**: The query planner generates multiple candidate plans and chooses the one with the lowest estimated cost. EXPLAIN shows the plan and estimated costs. EXPLAIN ANALYZE executes the query and shows actual rows and timing. The gap between estimated and actual rows is your first diagnostic signal — large gaps indicate stale statistics or complex predicates the planner misestimates.

**Why it matters**: A query that returns the same result in 1ms (index seek) or 30 seconds (sequential scan of 50M rows) is the single biggest performance lever in a backend system. A single missing index can push a service's database CPU from 5% to 95%. The N+1 problem can turn a sub-second page load into a 10-second ordeal. These are fixable with knowledge.

**Key insight**: The most common production performance problem is not an algorithm — it is missing indexing, wrong query shape, or N+1. Profile first, optimize second. Do not guess what is slow.

---

## 3. Core Principles

- **Selectivity**: The fraction of rows matching a predicate. High selectivity (few matching rows) → index scan. Low selectivity (many matching rows) → sequential scan.
- **Join algorithms**: Hash join (build hash table from smaller side, probe with larger), Merge join (both inputs sorted), Nested loop join (for small inputs). Wrong join choice causes catastrophic slowdown.
- **Push predicates early**: Filter as early as possible in the plan to minimize rows flowing through subsequent operations.
- **Statistics**: The planner uses column statistics (histograms, n_distinct, correlation). Stale statistics → wrong cardinality estimates → wrong plans.

---

## 4. Types / Architectures / Strategies

### 4.1 EXPLAIN Node Types

| Node | Description | Good/Bad |
|------|-------------|---------|
| Seq Scan | Full table scan | OK for small tables or >15% selectivity |
| Index Scan | B-tree traverse + heap fetch | Good for high selectivity |
| Bitmap Heap Scan | Bitmap of pages, then heap fetch | Good for moderate selectivity |
| Index Only Scan | B-tree only, no heap | Best (with covering index) |
| Hash Join | Build hash table from smaller side | Good for large unsorted inputs |
| Merge Join | Merge two sorted inputs | Good if inputs already sorted |
| Nested Loop | For each outer row, scan inner | Good when outer is small |
| Sort | In-memory or disk sort | Watch for disk spills |
| Hash Aggregate | Group-by using hash table | Watch for memory pressure |
| Gather/Gather Merge | Parallel query aggregation | Modern parallelism |

### 4.2 N+1 Detection Tools

| Tool | What it Shows |
|------|--------------|
| Hibernate Statistics | Total query count, per-query counts |
| p6spy | SQL intercept with stack trace |
| datasource-proxy | Slow query logging, query counting |
| Spring Boot Actuator + p6spy | Query count per HTTP request |
| pg_stat_statements | Top queries by total time, execution count |
| MySQL slow_query_log | Queries exceeding threshold |

### 4.3 Batch Insert Strategies (Fastest to Slowest)

| Strategy | Throughput | Notes |
|---------|-----------|-------|
| COPY (PostgreSQL) | Highest | Bypasses WAL; for bulk load |
| multi-row VALUES INSERT | Very high | `INSERT INTO t VALUES (a),(b),(c),...` |
| JDBC batch execute | High | Groups statements, still goes through WAL |
| Individual INSERTs | Low | One round-trip per row |

---

## 5. Architecture Diagrams

### EXPLAIN ANALYZE Output Anatomy

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT u.name, COUNT(o.id) AS order_count
FROM users u
JOIN orders o ON u.id = o.user_id
WHERE u.created_at > '2024-01-01'
GROUP BY u.id, u.name
ORDER BY order_count DESC
LIMIT 10;

-- Output:
Limit  (cost=5678.34..5678.36 rows=10) (actual time=234.123..234.125 rows=10)
  -> Sort  (cost=5678.34..5698.34 rows=8000) (actual time=234.123..234.124 rows=10)
        Sort Key: (count(o.id)) DESC
        Sort Method: top-N heapsort  Memory: 25kB          <- efficient
  -> HashAggregate  (cost=5234.00..5314.00 rows=8000)
                    (actual time=233.789..233.950 rows=8213)
        -> Hash Join  (cost=1234.00..4234.00 rows=200000)
                      (actual time=56.789..200.456 rows=245678)
              Hash Cond: (o.user_id = u.id)
              -> Seq Scan on orders o                      <- missing index
                    (actual rows=1245678 loops=1)
              -> Hash  (cost=1100.00..1100.00 rows=10720)
                    -> Index Scan on users u               <- index used
                          (actual rows=10720 loops=1)
                          Index Cond: (created_at > '2024-01-01')

Reading the output:
  (cost=X..Y):  estimated startup cost X, total cost Y
  (actual time=A..B): actual startup ms A, total ms B
  rows=N: estimated rows. actual rows=M: actual rows.
  Large gap between N and M → bad statistics or complex predicate

  "Seq Scan on orders" with 1.2M rows = bottleneck!
  Fix: CREATE INDEX ON orders (user_id) or covering index
```

### N+1 Problem Pattern and Fix

```
N+1 Pattern:
  Query 1: SELECT * FROM users LIMIT 10
  Query 2: SELECT * FROM orders WHERE user_id = 1
  Query 3: SELECT * FROM orders WHERE user_id = 2
  Query 4: SELECT * FROM orders WHERE user_id = 3
  ...
  Query 11: SELECT * FROM orders WHERE user_id = 10
  Total: 11 queries for 10 users

Fix with JOIN:
  SELECT u.*, o.*
  FROM users u
  LEFT JOIN orders o ON o.user_id = u.id
  LIMIT 10 users (with subquery or window function)
  Total: 1 query

Fix with IN:
  SELECT * FROM users LIMIT 10                  -> 10 users
  SELECT * FROM orders WHERE user_id IN (1,2,...,10)  -> 1 query
  Total: 2 queries (N+1 → 1+1 = 2)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 N+1 Detection and Fix with Spring Data JPA

```java
// BROKEN: N+1 — one query per user to load orders
@Entity
public class User {
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)  // LAZY by default
    private List<Order> orders;
}

// Service method that triggers N+1:
@Service
public class UserService {
    public List<UserDto> getUsersWithOrders() {
        List<User> users = userRepository.findAll();  // Query 1: SELECT users
        return users.stream()
            .map(user -> {
                user.getOrders();  // Query 2..N+1: SELECT orders WHERE user_id=?
                return toDto(user);
            })
            .toList();
    }
}

// Fix 1: JOIN FETCH in JPQL
@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.active = true")
List<User> findActiveUsersWithOrders();

// Fix 2: @EntityGraph
@EntityGraph(attributePaths = "orders")
List<User> findByActive(boolean active);

// Fix 3: Batch size (compromise — N/batchSize + 1 queries instead of N+1)
@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
@BatchSize(size = 50)  // load orders in batches of 50 users
private List<Order> orders;

// Fix 4: Separate query with IN clause (avoids Cartesian product for multiple collections)
@Query("SELECT u FROM User u WHERE u.active = true")
List<User> findActiveUsers();

@Query("SELECT o FROM Order o WHERE o.userId IN :userIds")
List<Order> findOrdersByUserIds(@Param("userIds") List<Long> userIds);

// Service:
List<User> users = userRepository.findActiveUsers();
List<Long> userIds = users.stream().map(User::getId).toList();
Map<Long, List<Order>> ordersByUserId = orderRepository
    .findOrdersByUserIds(userIds)
    .stream()
    .collect(Collectors.groupingBy(Order::getUserId));

users.forEach(user ->
    user.setOrders(ordersByUserId.getOrDefault(user.getId(), List.of()))
);
```

### 6.2 Detecting N+1 with Statistics

```java
// Enable Hibernate statistics logging
// application.yml:
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true

// Or use datasource-proxy to count queries per HTTP request:
@Bean
public DataSource dataSource() {
    return ProxyDataSourceBuilder.create(actualDataSource)
        .name("DB-Query")
        .logQueryToSysOut()
        .countQuery()  // count queries
        .build();
}

// Query count assertion in tests:
@Test
public void getUsersWithOrders_shouldNot_triggerNPlusOne() {
    int queryCount = queryCountInterceptor.getQueryCount();
    userService.getUsersWithOrders();
    int newCount = queryCountInterceptor.getQueryCount();
    assertThat(newCount - queryCount).isLessThanOrEqualTo(2);  // 1 for users, 1 for orders
}
```

### 6.3 Pagination Performance

```sql
-- OFFSET pagination: performance degrades with deep pages
-- Page 1: fast
SELECT * FROM orders ORDER BY created_at DESC LIMIT 20 OFFSET 0;
-- Page 100: slow (skip 2000 rows)
SELECT * FROM orders ORDER BY created_at DESC LIMIT 20 OFFSET 2000;
-- Page 10000: very slow (skip 200000 rows)
SELECT * FROM orders ORDER BY created_at DESC LIMIT 20 OFFSET 200000;
-- Database must produce rows 0-200019, then discard 0-199999

-- KEYSET pagination: O(1) regardless of depth
-- First page:
SELECT * FROM orders ORDER BY created_at DESC, id DESC LIMIT 20;
-- Returns: last row's (created_at, id) = ('2024-05-01', 456)

-- Next page (use cursor from last item):
SELECT * FROM orders
WHERE (created_at, id) < ('2024-05-01', 456)  -- composite comparison
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Or separate conditions (more readable, same effect):
SELECT * FROM orders
WHERE created_at < '2024-05-01'
   OR (created_at = '2024-05-01' AND id < 456)
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Required index for keyset:
CREATE INDEX ON orders (created_at DESC, id DESC);

-- Performance comparison:
-- OFFSET 200000: ~800ms
-- Keyset with same data: ~1ms
```

### 6.4 Batch Inserts with JDBC and Spring

```java
// BROKEN: individual inserts (one round trip per row)
orders.forEach(order -> orderRepository.save(order));  // N queries

// FIX 1: Spring Data JPA saveAll (uses batching if configured)
// application.yml:
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 100     # batch INSERT statements
          batch_versioned_data: true  # batch UPDATE/DELETE with versioning
          order_inserts: true         # group inserts by entity type
          order_updates: true

// Then: saveAll() batches 100 at a time
orderRepository.saveAll(orders);  // batchSize inserts at a time

// FIX 2: Direct JDBC batch for maximum performance
@Autowired
private JdbcTemplate jdbcTemplate;

public void batchInsert(List<Order> orders) {
    String sql = "INSERT INTO orders (user_id, amount, status, created_at) " +
                 "VALUES (?, ?, ?, ?)";

    jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            Order order = orders.get(i);
            ps.setLong(1, order.getUserId());
            ps.setBigDecimal(2, order.getAmount());
            ps.setString(3, order.getStatus());
            ps.setTimestamp(4, Timestamp.from(order.getCreatedAt()));
        }
        @Override
        public int getBatchSize() { return orders.size(); }
    });
}

// For very large inserts (millions of rows): use COPY with PostgreSQL
public void bulkCopy(List<Order> orders) throws SQLException {
    Connection conn = dataSource.getConnection();
    CopyManager copyManager = new CopyManager((BaseConnection) conn);
    StringBuilder sb = new StringBuilder();
    for (Order order : orders) {
        sb.append(order.getUserId()).append('\t')
          .append(order.getAmount()).append('\t')
          .append(order.getStatus()).append('\n');
    }
    copyManager.copyIn(
        "COPY orders (user_id, amount, status) FROM STDIN",
        new StringReader(sb.toString())
    );
}
```

### 6.5 Prepared Statement Plan Caching

```java
// PreparedStatement: SQL parsed and planned once, executed many times
// Avoids repeated parse+plan overhead for identical query shapes

// BROKEN: String concatenation defeats PreparedStatement
String sql = "SELECT * FROM orders WHERE user_id = " + userId;  // SQL injection risk + no caching
Statement stmt = conn.createStatement();
stmt.executeQuery(sql);

// FIX: PreparedStatement with parameter binding
String sql = "SELECT * FROM orders WHERE user_id = ?";
PreparedStatement ps = conn.prepareStatement(sql);  // plan cached at driver level
ps.setLong(1, userId);
ps.executeQuery();

// PostgreSQL server-side prepared statements:
// The driver sends PREPARE once, then EXECUTE for each invocation
// Avoids repeated parse+plan on the DB server
// HikariCP + PgSQL driver uses server-side PreparedStatements by default

// Spring JdbcTemplate uses PreparedStatement internally:
jdbcTemplate.query(
    "SELECT * FROM orders WHERE user_id = ?",
    new Object[]{userId},
    rowMapper
);

// Check server-side prepared statements:
SELECT * FROM pg_prepared_statements;
```

---

## 7. Real-World Examples

**GitHub's query optimization**: GitHub's Rails application historically had severe N+1 issues in their contribution graph. They instrumented every controller action with query counting and set a budget of N queries per action. Any action exceeding the budget required an architectural fix — not a band-aid. This discipline, applied consistently, kept their database load manageable despite massive data growth.

**Offset pagination cliff**: A SaaS company's "export all records" feature worked fine in development (1,000 records). In production with 10M records, the last pages (OFFSET 9,900,000 LIMIT 100) each took 45 seconds, timing out and causing user complaints. Migrating to keyset pagination reduced export time from hours to minutes.

---

## 8. Tradeoffs

| Pagination | Consistency | Performance at scale | Jump to page |
|------------|------------|---------------------|-------------|
| OFFSET | Poor | O(OFFSET) — degrades | Yes |
| Keyset | Good | O(1) | No |
| Cursor (keyset variant) | Good | O(1) | No |

| N+1 Fix | Performance | Memory | Complexity |
|---------|------------|--------|------------|
| JOIN FETCH | Best (1 query) | Higher (Cartesian for multiple collections) | Low |
| Separate IN query | Good (2 queries) | Medium | Medium |
| @BatchSize | Acceptable (N/batch+1) | Low | Low |

---

## 9. When to Use / When NOT to Use

**JOIN FETCH**: Use for loading a single collection with a fixed bounded size. Avoid for multiple collections (creates Cartesian product multiplying row count). For `User + Orders + Tags`, use separate IN queries, not JOIN FETCH for both collections simultaneously.

**Keyset pagination**: Use for any API that returns sequential data that clients page through (activity feeds, transaction history, search results). Avoid if users need to jump to arbitrary page numbers (admin reports, export with known page count).

**JDBC batch updates**: Use for bulk operations (data migrations, import jobs, bulk status updates). Do not use for single-row updates in transactional business logic — the benefit is amortizing round-trip overhead, which only matters at batch scale.

---

## 10. Common Pitfalls

**Eager loading causing Cartesian product**: Loading a User with both orders (100 per user) and tags (20 per user) via JOIN FETCH produces 100 * 20 = 2,000 duplicate rows per user. Hibernate deduplicates them in memory, but the query transferred 2,000 rows where you needed 120. Use separate queries with IN for multiple collections.

**N+1 hidden by LazyInitializationException fix**: When Hibernate throws LazyInitializationException (session closed, lazy collection accessed), the common "fix" is changing fetch to EAGER. This solves the exception but creates a permanent N+1 (now eager-loading the collection everywhere, even when not needed). The right fix is JOIN FETCH or EntityGraph scoped to the query that needs the data.

**Query inside a loop**: Even without ORM, a query inside a for loop is N+1:
```java
// BROKEN: query inside loop
for (Long userId : userIds) {
    List<Order> orders = jdbcTemplate.query(
        "SELECT * FROM orders WHERE user_id = ?", userId);  // N queries
}
// FIX:
jdbcTemplate.query(
    "SELECT * FROM orders WHERE user_id IN (?)",
    userIds);  // 1 query
```

**Sort without index causes sort spill to disk**: `ORDER BY last_name, first_name` on a 10M row table without an index on those columns does an in-memory sort (up to `work_mem` bytes). If the sort exceeds work_mem, PostgreSQL spills to disk — dramatically slower. EXPLAIN output shows "Sort Method: external merge Disk: 45678kB". Fix: add index on the sorted columns, or increase work_mem for specific queries (`SET LOCAL work_mem = '256MB'` inside a transaction).

**Implicit type casting preventing index use**: A query `WHERE user_id = '123'` on a column of type integer may not use the index — the database casts each row's integer to text for comparison, making the index unusable. Always use the correct type in parameters. In JPA, this is handled by parameter binding; in raw JDBC, use the correct setXxx method.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `EXPLAIN (ANALYZE, BUFFERS)` | Query plan with actual timing and buffer usage |
| `pg_stat_statements` | Top queries by total time and execution count |
| `auto_explain` | Auto-log slow query plans to PostgreSQL logs |
| `pgBadger` | Parse PostgreSQL logs, generate slow query reports |
| `datasource-proxy` | Java DataSource proxy for query counting |
| `p6spy` | SQL interceptor with stack traces |
| `Hibernate generate_statistics` | Query count and timing statistics |
| `EXPLAIN ANALYZE` in DBeaver/DataGrip | GUI plan visualization |
| depesz EXPLAIN | Online PostgreSQL plan formatter |
| `USE INDEX` / `pg_hint_plan` | Force index hints when planner chooses wrong plan |

---

## 12. Interview Questions with Answers

**Q: How do you read a PostgreSQL EXPLAIN ANALYZE output?**
Each line is a plan node (operation). Read from the innermost (deepest indentation) outward — inner nodes execute first. Key fields: cost=X..Y (estimated startup..total cost), rows=N (estimated), actual time=A..B ms (measured startup..total), actual rows=M. Large gap between rows=N (estimated) and actual rows=M indicates outdated statistics. The widest actual time lines are bottlenecks. Look for Seq Scan on large tables (should be Index Scan for high-selectivity queries), Sort with Disk methods (sort exceeded work_mem), and Hash Join with memory pressure.

**Q: What is the N+1 problem and how do you detect it in a Spring application?**
N+1: a query fetches N entities (1 query), then for each entity fetches a related collection (N queries) = N+1 queries total. In JPA, this occurs with LAZY-loaded collections accessed outside the repository. Detection: enable `hibernate.generate_statistics=true`, use datasource-proxy to count queries per HTTP request, or use p6spy to log all SQL with stack traces. In tests, assert a maximum query count per operation.

**Q: How would you fix N+1 for a User with Orders in Spring Data JPA?**
Option 1: `@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.id IN :ids")` — JOIN FETCH fetches users and orders in one query. Option 2: `@EntityGraph(attributePaths = "orders")` on the repository method. Option 3: For multiple collections (orders + tags), use separate queries with IN: fetch all users, collect IDs, `SELECT o FROM Order o WHERE o.userId IN :userIds`, map by userId in Java. Avoid JOIN FETCH for multiple collections simultaneously (Cartesian product).

**Q: What is the performance cliff with OFFSET pagination?**
OFFSET N requires the database to generate all rows 0 through N+LIMIT-1 and discard 0 through N-1. For OFFSET 1,000,000 LIMIT 20, the database generates 1,000,020 rows and discards 1,000,000. Performance is O(OFFSET) — doubling the page number doubles the query time. At deep pages (export, large dataset), this becomes unbearably slow. Keyset pagination avoids this: `WHERE id > last_id LIMIT 20` uses the index directly, O(1) regardless of depth.

**Q: How does keyset pagination work?**
Keyset pagination uses the values from the last row of the current page as the cursor for the next page. For a list sorted by `(created_at DESC, id DESC)`, the next page query is: `WHERE (created_at, id) < (last_created_at, last_id) ORDER BY created_at DESC, id DESC LIMIT 20`. The composite WHERE condition acts as a cursor. An index on (created_at DESC, id DESC) makes this O(1). The tradeoff: cannot jump to arbitrary pages — you can only page forward (or backward with reversed comparison).

**Q: What JDBC patterns should you use for bulk inserts?**
Best performance: COPY (PostgreSQL) or LOAD DATA INFILE (MySQL) — bypasses row-by-row WAL overhead. For general use: JDBC batch execute (`PreparedStatement.addBatch(); executeBatch()`) groups statements for fewer round trips. With Hibernate: set `hibernate.jdbc.batch_size=100`, `hibernate.order_inserts=true`, and use `saveAll()`. Never: loop calling `save()` individually — this is N round trips for N rows.

**Q: How does a PreparedStatement improve performance?**
PreparedStatement separates query planning from execution. First call: `prepareStatement(sql)` sends the SQL to the database for parsing and planning, returning a statement handle. Subsequent executions: send only the handle and parameters — the database uses the cached plan. Benefits: (1) eliminates repeated parse and plan overhead for the same query shape; (2) prevents SQL injection (parameters cannot change the query structure). With PostgreSQL: after the 5th execution of the same prepared statement, the server switches from parameter-specific plans to generic plans that are cached more aggressively.

**Q: What is a Cartesian product in JPA and when does it occur?**
When JOIN FETCHing multiple collections on the same entity (e.g., `User FETCH JOIN orders FETCH JOIN tags`), the SQL JOIN multiplies the result set: each user row is combined with each order and each tag. A user with 100 orders and 20 tags produces 100 * 20 = 2,000 rows, which Hibernate deduplicates in memory. The network traffic and memory used is 2,000 rows even though only 120 entities exist. Fix: use separate queries for each collection, connected via IN clause.

**Q: How do you find slow queries in production?**
PostgreSQL: (1) Enable `pg_stat_statements` — aggregates all executed queries with total time, execution count, and average time. `SELECT query, total_exec_time/calls AS avg_ms, calls FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20`. (2) `log_min_duration_statement = 1000` logs all queries taking >1s. (3) `auto_explain` automatically logs plans for slow queries. MySQL: slow query log with `long_query_time`. Application: micrometer timer on repository methods, distributed tracing span for DB calls.

**Q: What is the work_mem setting and when does it cause disk spills?**
`work_mem` (PostgreSQL) is the memory available per sort or hash operation per query execution. If a sort exceeds work_mem, PostgreSQL spills to disk (external merge sort). EXPLAIN ANALYZE shows "Sort Method: external merge Disk: XKIB". Signs of work_mem pressure: sorts and hash joins taking unexpectedly long, high temp file usage in pg_stat_database. Fix: increase work_mem for specific queries (`SET LOCAL work_mem = '256MB'`), or add an index to eliminate the sort, or redesign the query. Be careful: work_mem is per-operation per-query, and a complex query can have many operations.

**Q: What query hints are available in PostgreSQL?**
PostgreSQL has limited native hints. `enable_seqscan = off` (session level) disables seq scans — forces index use. `enable_hashjoin = off` disables hash joins. These are global settings and dangerous in production. Better: use `pg_hint_plan` extension for per-query hints: `/*+ SeqScan(orders) */` or `/*+ IndexScan(orders orders_user_id_idx) */`. Also: `SET LOCAL enable_nestloop = off` inside a transaction. For most cases, fixing statistics (ANALYZE) or adjusting planner cost parameters is better than hints.

**Q: How do you count queries in a Spring test to prevent N+1 regression?**
Use datasource-proxy or a custom JDBC connection wrapper. Configure a QueryCountHolder ThreadLocal that counts SQL executions. In tests:
```java
QueryCountHolder.clear();
userService.getUsersWithOrders();
assertThat(QueryCountHolder.getGrandTotal()).isLessThanOrEqualTo(2);
// 1 query for users, 1 for orders = max 2
```
This prevents regression: if someone adds a lazy access that triggers N+1, the test fails. Run these in all repository/service integration tests.

**Q: What is the difference between INNER JOIN and LEFT JOIN performance-wise?**
INNER JOIN: only returns rows where the join condition matches in both tables. LEFT JOIN: returns all rows from the left table, with NULLs for unmatched right table rows. Performance: INNER JOIN generally performs better — it can eliminate non-matching rows earlier in the plan. LEFT JOIN must preserve all left rows, preventing some filter pushdowns. When you use LEFT JOIN but then filter on the right table's columns in WHERE (making it effectively an INNER JOIN), use INNER JOIN explicitly to allow the planner to optimize better.

**Q: How do you debug a query that is suddenly slow in production?**
Systematic approach: (1) Get the execution plan from production: `EXPLAIN (ANALYZE, BUFFERS) <query>`. (2) Compare estimated vs actual rows — large gap = stale statistics. Run `ANALYZE tablename`. (3) Check if an index is being used: look for Seq Scan where an Index Scan is expected. (4) Check buffer hit rate in EXPLAIN BUFFERS output: low hit rate = working set exceeds buffer pool. (5) Check for lock contention: `SELECT * FROM pg_locks JOIN pg_stat_activity ON pg_locks.pid = pg_stat_activity.pid WHERE granted = false`. (6) Compare with a known-good plan: `EXPLAIN (ANALYZE)` from before the slowdown and compare cardinality estimates.

---

## 13. Best Practices

- Run EXPLAIN ANALYZE (not just EXPLAIN) — EXPLAIN estimates can be misleading without actuals.
- After any large data operation (bulk insert, delete, ETL), run ANALYZE on affected tables.
- Always use PreparedStatement (never string concatenation) for security and performance.
- Detect N+1 in integration tests with query count assertions; prevent regressions.
- Use keyset pagination for all user-facing paginated endpoints from day one.
- Set a batch_size in Hibernate for all bulk operations; never loop with individual saves.
- Use pg_stat_statements to find the top 10 queries by total execution time — these are the best optimization targets.
- Do not optimize without profiling — find the actual bottleneck first.

---

## 14. Case Study

**Problem**: An order management system had a "Get Orders with Customer Details" endpoint that was taking 8 seconds for managers reviewing 50 orders. The service handled 200 managers simultaneously, causing 40,000 DB queries per second.

**Investigation**:
1. datasource-proxy logging showed 51 queries per request (50 orders + 1 for order list).
2. Classic N+1: `orderRepository.findByStatus("open")` (1 query), then for each order `customerRepository.findById(order.getCustomerId())` (50 queries).
3. Each customer fetch was a separate round-trip to the DB.

**Fix 1: JOIN FETCH**:
```java
@Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.status = :status")
List<Order> findByStatusWithCustomer(@Param("status") String status);
```
Result: 51 queries → 1 query. 8s → 180ms.

**Fix 2: Cache customer data** (customers change rarely):
```java
@Cacheable(value = "customers", key = "#id")
public Customer getCustomer(Long id) { return customerRepo.findById(id).orElseThrow(); }
```
Subsequent requests: 1 query (orders) + cache hits (customers). 180ms → 25ms.

**Fix 3: Pagination**:
Original endpoint returned all open orders (up to 10,000). Added cursor-based pagination (20 per page). Most managers only look at page 1. Query time stable regardless of total open orders.

**Final result**: 40,000 DB queries/s → 200 queries/s. p99 latency 8s → 90ms. Database CPU 85% → 12%.
