# Connection Pool Management

## 1. Concept Overview

A database connection is expensive: TCP handshake (~0.3ms), TLS negotiation (~1ms), PostgreSQL authentication + session init (~2–5ms) add up to ~5–10ms per connection. Under load, establishing a new connection per request would consume more time than many queries take. A connection pool maintains a pre-established set of connections that are checked out, used, and returned — amortizing the connection overhead across thousands of requests.

At production scale, connection pool sizing and configuration become critical: too few connections cause request queuing; too many connections overwhelm the database server (PostgreSQL: 5–10MB RAM per backend process, plus scheduling overhead). The right pool size is a function of your database server's CPU count, I/O characteristics, and application concurrency.

---

## 2. Intuition

A connection pool is like a taxi fleet. Each taxi (connection) takes time to summon (connection setup). A pool pre-stations N taxis at the building (pre-established connections). When a passenger (request) arrives, they grab an available taxi instantly. When done, the taxi returns to the station. If all taxis are in use, new passengers wait. Too many taxis idle at the station waste space; too few and passengers queue.

---

## 3. Core Principles

**Pool sizing: small is better than large**: Counter-intuitively, larger pools beyond a certain point reduce throughput. The database processes queries using OS threads or processes. Beyond its concurrency capacity, additional simultaneous connections cause context switching and I/O wait overhead. The HikariCP documentation cites the formula: `(core_count × 2) + effective_spindle_count`.

**Connection validation**: A pooled connection may become invalid (network timeout, DB server restart, idle TCP RST). Pools validate connections before handing them out (test-on-borrow) or periodically (keep-alive heartbeat).

**Pool per service instance**: In Kubernetes with N pods each holding a pool of M connections, the database receives N×M simultaneous connections. With 100 pods and pool size 10, that is 1000 connections to PostgreSQL — which has a default max_connections=100. PgBouncer as a proxy is essential.

---

## 4. Types / Architectures / Strategies

```
Layer                | Technology          | Role
---------------------|---------------------|---------------------------
Application pool     | HikariCP, c3p0,     | Per-application-instance pool
                     | DBCP2               | Java-managed connections
External proxy pool  | PgBouncer           | Multiplexes many app connections
                     | ProxySQL            | to few DB connections
                     | Odyssey             |
Pooling modes        | Session pooling      | 1 server conn per client session
(PgBouncer)          | Transaction pooling  | Server conn released after each txn
                     | Statement pooling    | Server conn released after each stmt
```

---

## 5. Architecture Diagrams

```
HikariCP Connection Pool — Internal Structure
=============================================

Thread 1 → HikariPool.getConnection()
Thread 2 → HikariPool.getConnection()
Thread 3 → HikariPool.getConnection()
                    │
          ┌─────────▼──────────┐
          │   ConcurrentBag    │   ← lock-free bag of PoolEntry objects
          │                    │
          │  PoolEntry 1 (IN USE by Thread 1)
          │  PoolEntry 2 (IN USE by Thread 2)
          │  PoolEntry 3 (IDLE)
          │  PoolEntry 4 (IDLE)
          │  PoolEntry 5 (IDLE)
          └────────────────────┘
                    │
              ┌─────┴──────┐
              │ PostgreSQL │ ← 5 backend processes (one per PoolEntry)
              └────────────┘

Thread 4 arrives, all entries in use → waits up to connectionTimeout (30s)
If none freed in 30s → SQLTimeoutException

HikariCP pool eviction: idleTimeout (10 min), maxLifetime (30 min),
  keepaliveTime (30s) — sends keepalive query to prevent TCP RST


PgBouncer Transaction Pooling — Multiplexing
=============================================

Client connections (many)         Server connections (few)
  App Instance 1  \
  App Instance 2   ────► PgBouncer ────► PostgreSQL primary (10 server conns)
  App Instance 3  /        pool
  ...100 more...

Transaction pooling:
  Client 1 borrows server conn → executes transaction → returns conn
  Client 2 gets that same conn immediately for the next transaction
  100 client connections share 10 server connections
  Multiplexing ratio: 10:1


Connection Pool Sizing — Kubernetes Scale-Out Problem
=====================================================

Before PgBouncer:
  50 pods × HikariCP pool size 10 = 500 PostgreSQL connections
  PostgreSQL max_connections = 500 → at limit from application alone

  Scale to 200 pods during peak → 2000 connections → PostgreSQL crash

With PgBouncer (DaemonSet or sidecar):
  50 pods × HikariCP pool size 10 = 500 PgBouncer connections (clients)
  PgBouncer server pool size = 50 → 50 PostgreSQL connections
  Scale to 200 pods → 2000 PgBouncer client connections, still 50 PG connections
```

---

## 6. How It Works — Detailed Mechanics

### HikariCP Configuration

```yaml
# Spring Boot application.yml — HikariCP settings
spring:
  datasource:
    url: jdbc:postgresql://db:5432/mydb
    username: app_user
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      # Pool sizing
      maximum-pool-size: 10        # Total connections in pool
      minimum-idle: 5              # Minimum idle connections maintained

      # Connection lifecycle
      connection-timeout: 30000    # Max wait for connection (30s); throw if exceeded
      idle-timeout: 600000         # Idle connections removed after 10 min
      max-lifetime: 1800000        # Connection forcibly replaced after 30 min (prevent stale conns)
      keepalive-time: 30000        # Send keepalive query every 30s to prevent TCP RST

      # Validation
      connection-test-query: SELECT 1  # Validation query (for old JDBC drivers)
      # PostgreSQL driver supports isValid() — no need for test-query with PG JDBC 42+

      # Leak detection (development only — high overhead)
      leak-detection-threshold: 60000  # Warn if connection held > 60s

      # Pool name for JMX/metrics
      pool-name: HikariPool-Main
```

**Pool sizing formula**:
```
For a CPU-bound PostgreSQL workload:
  pool_size = (cpu_cores × 2) + effective_spindle_count

  CPU: 8 cores, SSD (spindle_count=1):
    pool_size = 8 × 2 + 1 = 17 (round to 20)

  CPU: 4 cores, no spindle (pure SSD):
    pool_size = 4 × 2 + 1 = 9 (round to 10)

Note: This is per-database-instance, not per-app-instance.
  If 10 app instances each need to talk to this DB:
  each instance pool_size = 20 / 10 = 2  (very small per-instance!)
  → Justify using PgBouncer instead of per-app pools
```

### Pool Exhaustion Diagnosis

```java
// HikariCP exposes metrics via Micrometer
// Prometheus metrics:
//   hikaricp_connections_active    → connections in use
//   hikaricp_connections_idle      → idle connections
//   hikaricp_connections_pending   → threads waiting for connection
//   hikaricp_connections_timeout_total → timed-out connection requests

// Alert: hikaricp_connections_pending > 0 for > 30 seconds → pool exhausted
// Alert: hikaricp_connections_timeout_total rate > 0 → requests failing

// JMX monitoring (development):
// Connect JConsole to app → HikariCP MBean → view pool stats
```

**Pool exhaustion causes**:
1. Pool size too small for QPS
2. Slow queries holding connections for too long
3. Connection leak (connection not returned to pool after use)
4. Database slow-down causing queries to take longer, pooling up connections

**Leak detection**:
```java
// Set leak-detection-threshold = 60000 (60s)
// HikariCP logs WARN when a connection is held > 60s with stack trace
// Shows exactly which code path leaked the connection

// Common leak pattern:
Connection conn = dataSource.getConnection();
// ... exception thrown before conn.close() ...
// conn is never returned to pool

// Fix: always use try-with-resources
try (Connection conn = dataSource.getConnection()) {
    // conn auto-closed on exit, even on exception
}
```

### PgBouncer Configuration

```ini
# pgbouncer.ini
[databases]
mydb = host=localhost port=5432 dbname=mydb

[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = scram-sha-256
auth_file = /etc/pgbouncer/userlist.txt

# Pooling mode — critical decision
pool_mode = transaction    # Connection returned after each transaction

# Server connection limits
server_pool_size = 20      # Max server connections per user+database pair
max_client_conn = 1000     # Max total client connections accepted

# Server connection lifecycle
server_idle_timeout = 600  # Remove idle server connections after 10 min
server_lifetime = 3600     # Replace server connections after 1 hour
server_connect_timeout = 15

# Client connection
client_login_timeout = 60
query_timeout = 0          # 0 = no timeout (use PostgreSQL statement_timeout)
```

**Transaction pooling vs session pooling**:
```
Session pooling:
  Client connection → server connection for entire session duration
  Client logs out → server connection returned to pool
  Use case: when clients are long-lived sessions (not microservices)
  Problem: if 100 clients connect but only 10 are active, 90 server conns wasted

Transaction pooling:
  Client connection → borrows server connection for each transaction → returns immediately
  100 clients can share 10 server connections if transactions are short
  Problem: cannot use session-level features:
    - SET LOCAL (session variable) — resets on next client
    - PREPARE statement — prepared statements bound to server connection
    - Temporary tables — bound to server connection
    - Advisory locks (session-level pg_advisory_lock)

Statement pooling:
  Extreme mode: server connection returned after each statement
  Most restrictive: no multi-statement transactions
  Rarely used
```

### ProxySQL for MySQL

```ini
# proxysql.cnf — MySQL read/write split
mysql_servers:
  - address: primary-host
    port: 3306
    hostgroup: 10   # Write hostgroup

  - address: replica1-host
    port: 3306
    hostgroup: 20   # Read hostgroup

  - address: replica2-host
    port: 3306
    hostgroup: 20   # Read hostgroup

mysql_query_rules:
  - rule_id: 1
    match_pattern: "^SELECT"
    destination_hostgroup: 20   # Route SELECT to read hostgroup
    apply: 1

  - rule_id: 2
    match_pattern: ".*"
    destination_hostgroup: 10   # Route all else to write hostgroup
    apply: 1

# Connection pool per hostgroup
mysql_hostgroup_attributes:
  - hostgroup_id: 10
    max_num_online_servers: 1    # Only one primary
  - hostgroup_id: 20
    max_num_online_servers: 10   # Up to 10 read replicas
```

### Connection Storm on Kubernetes Scale-Out

```
Problem:
  Kubernetes HPA scales from 10 pods to 100 pods in 30 seconds (traffic spike)
  Each pod starts with minimum-idle=5 connections → 500 new connections to DB
  PostgreSQL: max_connections=200 → overwhelmed immediately

  Effect: connection establishment itself fails → pod health checks fail
  → pods restart → more connections → cascade failure

Solution 1: PgBouncer as DaemonSet
  One PgBouncer per Kubernetes node
  All pods on that node connect to local PgBouncer (localhost socket — fast)
  PgBouncer maintains a fixed server pool to PostgreSQL
  Pod scale-out adds PgBouncer clients, not PostgreSQL connections

Solution 2: PgBouncer as sidecar
  One PgBouncer container per pod
  App connects to localhost:6432 (sidecar)
  Sidecar connects to PostgreSQL with pool_size=2-5 per pod
  100 pods × 5 pool size = 500 PgBouncer connections → 500 PG connections
  (Not much better than without PgBouncer — sidecar not ideal for connection reduction)
  Better use: DaemonSet (one per node, N pods share)

Solution 3: Connection ramp-up limit
  HikariCP: minimum-idle=1 (start with fewer connections)
  initializationFailTimeout=60000 (longer startup tolerance)
  Deploy pods with a startup delay (Kubernetes maxSurge=25%)
  Distribute connection establishment over time
```

---

## 7. Real-World Examples

**Zalando**: Uses PgBouncer in transaction mode in front of every PostgreSQL cluster. Standard setup: 500 PgBouncer client connections → 50 PostgreSQL server connections (10:1 multiplexing). Maximum application pool size per service is set based on service-level concurrency needs, not arbitrary defaults.

**Instagram**: Used Gevent (Python async) + PgBouncer. The async model meant thousands of concurrent requests per process, but each released the DB connection immediately after the query. PgBouncer transaction mode was essential for this pattern.

**GitHub**: Uses ProxySQL in front of MySQL for read/write splitting. ProxySQL routes SELECT statements to replicas automatically, reducing primary write node load by 70% during read-heavy operations.

---

## 8. Tradeoffs

```
Concern              | No pooling          | App-level pool     | PgBouncer proxy
---------------------|---------------------|--------------------|-----------------
Connection overhead  | Per-request         | Amortized          | Amortized (lower)
DB connections held  | 1 per active req    | pool_size per inst | server_pool_size
Prepared statements  | Per connection      | Per connection     | Lost in txn mode
Session variables    | Per connection      | Per connection     | Lost in txn mode
Operational overhead | None               | Low                | Medium (extra service)
Kubernetes compat.   | Poor               | Medium             | Good (DaemonSet)
```

---

## 9. When to Use / When NOT to Use

**Always use connection pooling** — no production application should open a new database connection per request.

**Use HikariCP** as the application-level pool for Java/Spring Boot applications. It is the fastest and most widely deployed.

**Add PgBouncer when**: (1) Application instances × pool size exceeds PostgreSQL max_connections, (2) Kubernetes horizontal scaling causes connection storms, (3) Short-lived connections (serverless functions, scripts) that would otherwise connect/disconnect frequently.

**Avoid PgBouncer transaction mode when**: Application uses PostgreSQL prepared statements bound to server connections, session-level advisory locks, or temporary tables. Use session pooling in those cases.

---

## 10. Common Pitfalls

**Pool size too large**: A team sets maximum-pool-size=50 on 20 application instances → 1000 connections to PostgreSQL. PostgreSQL spawns 1000 backend processes (5MB each = 5GB RAM). Context switching degrades query throughput. Database CPU drops to serving background overhead instead of queries. Rule: start with pool_size = (CPU cores × 2) + 1 per database instance, then measure.

**Connection leak with manual `getConnection()`**: Service code calls `dataSource.getConnection()` inside a utility method. An exception path exits without calling `close()`. Connections accumulate in "active" state, eventually exhausting the pool. Fix: always use try-with-resources; enable `leakDetectionThreshold` in HikariCP to catch leaks during testing.

**PgBouncer transaction mode with prepared statements**: Application uses `PreparedStatement` (JDBC) in PgBouncer transaction mode. PgBouncer does not forward `PREPARE` statements to the server in transaction mode (each transaction can go to a different server connection). The prepared statement is not found → `PREPARE "X" does not exist` error. Fix: either use `prepareThreshold=0` in PostgreSQL JDBC driver (disables server-side prepared statements, uses client-side), or switch PgBouncer to session pooling for this use case.

**max_connections exhaustion during deployment restart**: Rolling deployment restarts 100 pods, each establishing connections. Old pods hold connections while new pods establish new ones. During the window, connection count doubles. Fix: use lifecycle hooks to drain connections before pod termination (`preStop: sleep 15s`), and set `minimum-idle=1` to reduce baseline connections.

**idle_in_transaction connections**: A connection is checked out from the pool, begins a transaction, then the application code stalls (waiting for an external API call). The connection sits in `idle in transaction` state in PostgreSQL, holding locks and preventing VACUUM. Set `idle_in_transaction_session_timeout = 30000` (30 seconds) in PostgreSQL to kill stale idle-in-transaction connections. HikariCP's `connection-timeout` does not cover this — it only covers initial acquisition.

---

## 11. Technologies & Tools

| Tool          | Language | Purpose                              |
|---------------|----------|--------------------------------------|
| HikariCP      | Java     | Application-level connection pool    |
| c3p0          | Java     | Older application pool (use HikariCP)|
| DBCP2         | Java     | Apache Commons DB pool               |
| PgBouncer     | C        | PostgreSQL connection pooler/proxy   |
| Odyssey       | C        | PostgreSQL advanced pooler (by Yandex)|
| ProxySQL      | C++      | MySQL-compatible pool + routing      |
| pgpool-II     | C        | PostgreSQL pool + HA (older)         |
| AWS RDS Proxy | Managed  | Managed connection pooler for RDS    |

---

## 12. Interview Questions with Answers

**Q: Why does increasing connection pool size beyond a certain point hurt throughput?**
A database server processes queries using OS threads or processes. Each additional concurrent connection beyond the server's natural concurrency capacity (roughly 2× CPU cores for I/O-bound queries) causes increased context switching and memory pressure. PostgreSQL spawns a backend process per connection (~5–10MB RAM each). At 500 simultaneous connections on a 16-core server, the OS scheduler juggles 500 processes but can only run 16 at once — most of the scheduler's time is spent context switching rather than executing queries. Benchmarks consistently show throughput peaking around pool_size = (cores × 2) + 1 and declining with larger pools.

**Q: What is the difference between PgBouncer transaction mode and session mode and why does it matter for prepared statements?**
Session mode: a client connection maps to a dedicated server connection for its entire lifetime. All session-level features (prepared statements, SET variables, temporary tables, advisory locks) work correctly. Transaction mode: a client connection borrows a server connection only for the duration of each transaction and returns it afterward. The same client connection can execute subsequent transactions on different server connections. Prepared statements are bound to server connections — in transaction mode, a `PREPARE` is executed on one server connection, but the subsequent `EXECUTE` may arrive on a different server connection where the prepared statement does not exist. Fix: set PostgreSQL JDBC `prepareThreshold=0` to disable server-side prepared statements, using client-side parameter binding instead.

**Q: How do you handle connection storms during Kubernetes pod scale-out?**
Three strategies: (1) PgBouncer as DaemonSet: one PgBouncer per Kubernetes node; pods connect to the local PgBouncer (Unix socket or localhost). The DaemonSet maintains a fixed server pool to PostgreSQL, so pod scale-out adds PgBouncer client connections without increasing PostgreSQL connections. (2) Slow pod startup: configure HikariCP `minimumIdle=1` so each pod starts with only one connection; connections increase gradually under load rather than all at startup. Use Kubernetes pod readiness probes to stagger traffic cutover. (3) AWS RDS Proxy: managed connection pooler that absorbs connection surges, especially for Lambda-to-RDS scenarios where thousands of function invocations would otherwise create thousands of connections.

**Q: What is HikariCP's ConcurrentBag and why is it faster than other pool implementations?**
ConcurrentBag is HikariCP's custom lock-free data structure for managing pool entries. Traditional pools use blocking queues or synchronized collections that require monitors and context switches for every borrow/return. ConcurrentBag uses thread-local caching: each thread gets a list of connections it has previously used and tries to borrow from its own list first (without any locking). Only if no thread-local connection is available does it check the shared bag (using a compare-and-set operation). This eliminates lock contention on the critical hot path of connection borrow/return. Benchmarks show HikariCP is 10–100x faster than c3p0 or DBCP for high-concurrency borrow/return cycles.

**Q: How do you monitor connection pool health in production?**
With Micrometer (Spring Boot Actuator) and Prometheus: (1) `hikaricp_connections_active`: connections currently in use — alert if consistently at maximum. (2) `hikaricp_connections_pending`: threads waiting for a connection — any sustained non-zero value indicates pool exhaustion. (3) `hikaricp_connections_timeout_total`: total connection acquisition timeouts — alert on any occurrence. (4) `hikaricp_connection_acquired_nanos` (P99 and P999): time to acquire a connection — alert if P99 > 500ms. (5) PostgreSQL `pg_stat_activity`: `state = 'idle in transaction'` count — alert if persistent idle-in-transaction sessions. Dashboard these four metrics together; pool exhaustion typically shows as a correlated spike in pending + timeout metrics.

**Q: What happens when a pooled connection becomes invalid (TCP RST, DB restart)?**
Without connection validation, the invalid connection is returned to the pool as apparently idle. The next request checks it out, attempts a query, receives a `Connection reset by peer` or `JDBC Communication link failure`. The application sees a sporadic database error on what should be a healthy request. HikariCP mitigates this: (1) `keepaliveTime`: sends a keepalive query (`SELECT 1`) every N seconds to keep the TCP connection alive. (2) `maxLifetime`: forcibly replaces connections older than N milliseconds to prevent long-lived connections from accumulating state or hitting server-side idle timeouts. (3) `connectionTestQuery` / `isValid()`: validates the connection before handing it to the application. Choose keepalive + maxLifetime over test-on-borrow (which adds latency to every borrow).

**Q: What is the connection overhead of PostgreSQL and why does it matter for serverless workloads?**
Establishing a PostgreSQL connection requires: TCP three-way handshake (~0.3ms), TLS handshake (~1–2ms), PostgreSQL authentication (md5/scram-sha-256 challenge-response, ~1–2ms), PostgreSQL session initialization (process fork, shared memory setup, pg_hba.conf check, ~2–3ms). Total: 5–10ms per connection establishment. For a Lambda function with a 5ms execution time that establishes a new PostgreSQL connection per invocation, connection setup is 2× the function's own execution time. With 1000 Lambda invocations/second, 1000 connections are established/torn down per second — PostgreSQL forks and kills 1000 backend processes per second, overwhelming the server. Fix: AWS RDS Proxy or a long-lived PgBouncer instance absorbs Lambda connection spikes.

**Q: How does idle_in_transaction_session_timeout protect the database?**
A connection in `idle in transaction` state has begun a transaction but is waiting (possibly indefinitely) without executing statements. While waiting, it holds: (1) row-level locks on any rows it has written or locked with `SELECT FOR UPDATE`; (2) transaction ID (XID) open, preventing VACUUM from collecting dead tuples older than this XID. A stalled `idle in transaction` connection can block other transactions and cause table bloat accumulation. Setting `idle_in_transaction_session_timeout = 30000` (30 seconds) in PostgreSQL automatically terminates any connection in this state for longer than 30 seconds, releasing its locks and XID. This is separate from `statement_timeout` (kills running statements) and `lock_timeout` (kills statements waiting for locks).

**Q: How do you right-size a connection pool for a microservice with mixed query types?**
Profile the workload: (1) Measure the average query duration under production load. (2) Calculate required QPS: queries/second = request_rate × queries_per_request. (3) Calculate minimum connections: connections = QPS × avg_query_duration_seconds. Example: 1000 req/s × 2 queries/req × 0.005s avg = 10 connections minimum. (4) Apply headroom factor (1.5–2×): pool_size = 15–20 to absorb bursts. (5) Verify against the database's capacity: if 10 app instances × 20 connections = 200, check PostgreSQL max_connections. For mixed fast (1ms) and slow queries (500ms), the slow queries dominate pool occupancy — profile p99 query duration, not average.

**Q: What is Odyssey and how does it differ from PgBouncer?**
Odyssey is a PostgreSQL connection pooler developed by Yandex, designed for higher performance and more advanced routing than PgBouncer. Key differences: (1) Multi-threaded architecture: Odyssey uses event-driven I/O on multiple threads, handling more client connections per CPU than PgBouncer's single-threaded model. (2) Per-user/database pool configuration: fine-grained pool rules per user+database combination. (3) Built-in TLS termination and certificate-based auth. (4) Advanced client routing: route different users or query patterns to different server pools. PgBouncer remains simpler to configure and operate; Odyssey is preferred when PgBouncer becomes a throughput bottleneck (typically > 10K client connections).

**Q: Explain AWS RDS Proxy and when to use it.**
AWS RDS Proxy is a managed connection pooler that sits between your application and an RDS/Aurora database. It uses IAM authentication for security, stores database credentials in Secrets Manager, and maintains a persistent connection pool to the database. Applications (especially Lambda functions) connect to the Proxy endpoint, which handles pooling transparently. Key features: (1) Absorbs Lambda connection spikes (thousands of function invocations → dozens of database connections). (2) Automatic failover: RDS Proxy preserves client connections during RDS Multi-AZ failover, reducing application-visible downtime. (3) IAM authentication: no database passwords in application code. Use when: Lambda-to-RDS, ECS tasks that scale rapidly, or any workload where managing PgBouncer infrastructure is undesirable.

**Q: How do you configure HikariCP for optimal performance with Spring Data JPA?**
Spring Boot auto-configures HikariCP when `spring-boot-starter-data-jpa` is on the classpath. Key settings for JPA: (1) `maximum-pool-size`: match to concurrency needs, not JPA default (10). (2) `max-lifetime = 1800000` (30 min): prevents connections from staling; should be less than PostgreSQL's `tcp_keepalives_idle` and any firewall idle timeout. (3) `connection-timeout = 20000` (20s): shorter than typical HTTP request timeout to fail fast. (4) Disable `autoCommit = false` if using JPA transactions (Spring manages transactions explicitly). (5) JPA `spring.jpa.open-in-view = false`: prevents holding connections during view rendering (common source of pool exhaustion in web apps). The Open Session in View anti-pattern holds a connection from the start of the HTTP request to the end of view rendering — potentially blocking for seconds while HTML is rendered.

---

## 13. Best Practices

- **Never use default pool sizes in production** — the default `maximum-pool-size=10` in HikariCP is rarely correct; calculate based on your concurrency and query duration.
- **Monitor pending connections continuously** — any sustained pool waiters indicate under-provisioning or slow queries.
- **Set `max-lifetime` below the database/firewall idle timeout** — prevents connections from being silently closed by a firewall or DB server timeout.
- **Enable `leak-detection-threshold` in development** — catches connection leaks before they reach production.
- **Deploy PgBouncer before Kubernetes scale-out** — add PgBouncer as infrastructure before your application scales past a handful of instances.
- **Use try-with-resources for all JDBC operations** — prevents connection leaks on exception paths.
- **Set `idle_in_transaction_session_timeout` at the PostgreSQL level** — defend against application bugs that leave transactions open.
- **Run `SHOW POOLS` in PgBouncer regularly** — check the ratio of `cl_active` (client connections in use) to `sv_active` (server connections in use) to verify multiplexing is working.

---

## 14. Case Study

**Scenario**: A Spring Boot e-commerce application with 50 Kubernetes pods, each with HikariCP pool size 20. Total: 1000 connections to PostgreSQL. PostgreSQL `max_connections = 500` — at twice the limit. Developers observe sporadic `ConnectionAcquisitionTimeoutException` errors during peak traffic. Database CPU is 90% even though QPS is only 5K/second (a 4-core database should handle 20K+ QPS).

**Root cause analysis**:
```
pg_stat_activity during peak:
  idle: 600 connections (not executing)
  active: 300 connections (executing queries)
  idle in transaction: 100 connections (stalled transactions)

PostgreSQL backend count: 1000 processes × 8MB RAM = 8GB RAM consumed by connections alone
Context switching: scheduler overhead from 1000 processes on 4 cores

Stalled transactions: application code calling an external payment API with 30s timeout
  while holding a DB connection inside a @Transactional method
  → 100 connections stuck for 30s each = 100 connections blocked permanently during high load
```

**Fixes applied**:
1. PgBouncer DaemonSet (4 nodes × 1 PgBouncer each):
   - Each PgBouncer: `server_pool_size = 25` → 4 PgBouncer × 25 = 100 PostgreSQL connections
   - App pods connect to node-local PgBouncer (Unix socket)
   - PostgreSQL connections: 1000 → 100

2. HikariCP reconfigured:
   - `maximum-pool-size = 5` (per pod to PgBouncer — PgBouncer handles multiplexing)
   - `connection-timeout = 10000` (fail fast — don't queue for 30s)
   - `leak-detection-threshold = 30000`

3. Application fix:
   - External API calls moved outside `@Transactional` boundary
   - `idle_in_transaction_session_timeout = 10000` added to PostgreSQL

4. PostgreSQL:
   - `max_connections = 200` (sufficient for 100 PgBouncer + 100 direct admin connections)

**Results**:
- PostgreSQL backend processes: 1000 → 100 (80% RAM reduction)
- Database CPU: 90% → 35%
- Connection timeout errors: 0 (during normal operations)
- P99 query latency: 450ms → 25ms (context switching eliminated)
