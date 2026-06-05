# Case Study: Design a Connection Pool in Pure Java

## Intuition

> Think of a connection pool as a parking garage with a fixed number of spaces: cars (queries) drive in, take a space, drive out, and release the space. Without the garage, every car would dig its own driveway — expensive, slow, and finite. The pool manages that shared finite resource so the application never pays the 20–200 ms TCP + TLS + auth handshake on every query.

**Key insight**: a JDBC connection is not a lightweight object. Each one represents a TCP socket, a server process on the database, and 5–10 MB of server RAM. The pool's job is to amortize that cost across thousands of queries while enforcing the invariant that no more connections exist than the database can serve efficiently.

A well-designed pool answers three questions: how many connections to keep (capacity), how long to wait if all are busy (timeout / backpressure), and how to detect and replace dead ones (health). Get those three wrong and you get either starvation, connection exhaustion, or stale-socket errors.

See also:
- [Backpressure & Bounded Resources](cross_cutting/backpressure_and_bounded_resources.md) — queue-saturation maths and bounded-executor patterns
- [JVM Tuning & GC for Services](cross_cutting/jvm_tuning_and_gc_for_services.md) — object-pool GC impact, survivor-space sizing

---

## 1. Requirements Clarification

### Functional requirements
- `acquire(timeout)` — blocks calling thread up to `timeout`, returns a live connection or `null` on expiry
- `release(conn)` — non-blocking, returns connection to pool for reuse
- Health-check — background validation replaces stale connections before they are handed to callers
- Graceful shutdown — `close()` drains all held connections; no data loss
- Leak detection — report connections held longer than a configured threshold with the borrower's stack trace

### Non-functional requirements
| Dimension | Target |
|-----------|--------|
| Borrow latency (pool hit) | < 50 µs p99 |
| Pool size | Fixed, configurable (default: `2 × DB cores + 1`) |
| Concurrency | Safe for any number of simultaneous borrowers |
| Fairness | FIFO — first waiter gets next returned connection |
| Memory overhead | O(maxSize) — no unbounded growth |
| Recovery from DB failover | < 2 s once replica is promoted |

### Out of scope
- Wire protocol — uses `java.sql.Connection` (JDBC); driver is pluggable
- Connection sharding / partitioning per tenant (see Evolution section)
- XA / distributed transactions

---

## 2. Scale Estimation

### Fleet-level connection math

```
20 app instances × pool_size 10    = 200 logical connections
PostgreSQL default max_connections  = 100  (RDS db.t3.medium: 170)
=> 200 > 100 => "FATAL: sorry, too many clients already"
```

### Correct per-instance pool size (HikariCP formula)

```
pool_size = (DB_core_count × 2) + effective_spindle_count

4-core DB host on SSD  (effective_spindle ≈ 1):
pool_size = (4 × 2) + 1 = 9

20 instances × 9 = 180 connections  — at the ceiling of a 200-limit host.
> 50 instances × 9 = 450 connections  — must add PgBouncer proxy.
```

### Throughput from Little's Law

```
Throughput = Pool_size / Avg_query_latency
           = 9 / 0.005 s  =  1,800 q/s per instance
20 instances => 36,000 q/s aggregate (DB bound, not pool bound)
```

### Acquire-wait budget

```
SLA = 50 ms end-to-end
Query time = 5 ms
Borrow budget = SLA − query − serialize = 50 − 5 − 5 = 40 ms
→ set connectionTimeout = 30 ms (fail fast, not pile up)
```

### Memory overhead per connection object

```
PooledConnection wrapper ≈ 80 bytes
JDBC Connection object   ≈ 2 KB
OS TCP socket buffer     ≈ 4-8 KB (kernel)
DB backend process       ≈ 5-10 MB (Postgres)

Pool of 10: JVM heap impact < 100 KB; DB RAM impact ~100 MB.
The limiting resource is always the database, not the pool itself.
```

---

## 3. High-Level Architecture

```
  ┌────────────────────────────────────────────────────────────────┐
  │                     Application Threads                        │
  │   thread-1  thread-2  thread-3  ...  thread-N                  │
  └───────┬──────────┬──────────────────────┘
          │ acquire()│                   release()
          ▼          ▼                      │
  ┌───────────────────────────────────────────────────────────────┐
  │                      ConnectionPool                            │
  │                                                                │
  │   available: ArrayBlockingQueue<PooledConnection>  (bounded)  │
  │   allConnections: ConcurrentHashMap.newKeySet()               │
  │   poolSize: AtomicInteger  (CAS-guarded creation)             │
  │   leakTracker: ConcurrentHashMap<PooledConnection, LeakInfo>  │
  │   closed: volatile boolean                                     │
  └──────────────────┬────────────────────────────────────────────┘
                     │ create / validate / close
                     ▼
  ┌──────────────────────────────────┐
  │  ScheduledExecutorService        │
  │  (daemon, "pool-health-check")   │
  │  runs every 30 s                 │
  │  drainTo → isValid(2) → requeue  │
  └──────────────────────────────────┘
                     │ JDBC DriverManager
                     ▼
  ┌──────────────────────────────────┐
  │  Database (PostgreSQL / MySQL)   │
  │  each PooledConnection = 1 TCP   │
  │  socket + 1 backend process      │
  └──────────────────────────────────┘
```

### Class diagram

```
ConnectionPool implements AutoCloseable
  |-- available: ArrayBlockingQueue<PooledConnection>
  |-- allConnections: Set<PooledConnection>
  |-- poolSize: AtomicInteger
  |-- maxSize: int
  |-- closed: volatile boolean
  |-- leakTracker: ConcurrentHashMap<PooledConnection, LeakInfo>
  |-- healthCheck: ScheduledExecutorService

PooledConnection implements AutoCloseable
  |-- delegate: Connection          (real JDBC connection)
  |-- pool: ConnectionPool          (back-reference for release)
  |-- lastUsedAt: volatile long     (idle-timeout tracking)
  |-- close() -> pool.release(this) (try-with-resources support)

Client usage:
  try (PooledConnection conn = pool.acquire(5, TimeUnit.SECONDS)) {
      if (conn == null) throw new TimeoutException("No connection");
      conn.prepareStatement("SELECT ...").executeQuery();
  }  // auto-release via close()
```

### Data flow

1. **Acquire (pool hit)**: `available.poll()` returns immediately; `validateOrReplace` calls `isValid(2)`; if valid, return to caller.
2. **Acquire (pool empty, below max)**: CAS on `poolSize` claims the right to create; `DriverManager.getConnection(url)` establishes the TCP + auth handshake; wrapped in `PooledConnection`.
3. **Acquire (pool full, all borrowed)**: `available.poll(timeout, unit)` blocks the calling thread in the `ArrayBlockingQueue` park queue; returns `null` on expiry.
4. **Release**: `conn.close()` calls `pool.release(this)`; connection pushed back onto `available`; any waiting thread is unparked by the queue.
5. **Health check**: background executor drains `available` every 30 s; calls `isValid(2)` on each; replaces dead connections; requeues live ones.

---

## 4. Component Deep Dives

### 4.1 Acquire path — CAS pool growth

The core challenge: multiple threads may simultaneously find the pool empty and below `maxSize`. Only one thread should create each new connection.

```java
public PooledConnection acquire(long timeout, TimeUnit unit) throws InterruptedException {
    if (closed) throw new IllegalStateException("Pool is closed");

    // Fast path: grab from available queue immediately
    PooledConnection conn = available.poll();
    if (conn != null) return validateOrReplace(conn);

    // Slow path 1: CAS-claim the right to create a new connection
    while (true) {
        int current = poolSize.get();
        if (current >= maxSize) break;          // at cap; fall through to wait
        if (poolSize.compareAndSet(current, current + 1)) {
            // We exclusively own one creation slot
            try {
                return createConnection();
            } catch (SQLException e) {
                poolSize.decrementAndGet();     // give back the slot on failure
                throw new RuntimeException("Failed to create connection", e);
            }
        }
        // CAS missed (another thread incremented first); retry the loop
    }

    // Slow path 2: wait for a released connection (blocking)
    conn = available.poll(timeout, unit);
    return conn != null ? validateOrReplace(conn) : null;   // null = timeout
}
```

**Why CAS instead of `synchronized`**: the creation slot is a single integer increment. CAS handles it in ~5 ns; a `synchronized` block adds monitor-enter/exit overhead and OS scheduling if contended. For pool creation (rare path) the difference is small but the CAS version is simpler to reason about — no lock ordering issues.

### 4.2 Validate-on-borrow

BROKEN — the pool hands out a dead socket because it skips validation:

```java
// BROKEN: borrow does no validation; dead sockets handed to callers
public PooledConnection acquire(long timeout, TimeUnit unit) throws SQLException {
    Connection raw = available.poll(timeout, unit);  // may be a dead TCP socket
    if (raw == null) throw new SQLException("pool timeout");
    return new PooledConnection(raw, this);           // caller's first query throws
}
```

FIX — `isValid(1)` is the JDBC-standard liveness check:

```java
// FIX — Java 11 LTS: isValid(timeout) sends a lightweight ping to the DB
public PooledConnection acquire(long timeoutMs, TimeUnit unit) throws SQLException {
    long deadline = System.nanoTime() + unit.toNanos(timeoutMs);
    while (System.nanoTime() < deadline) {
        Connection raw = available.poll(remaining(deadline), TimeUnit.NANOSECONDS);
        if (raw == null) throw new SQLException("pool timeout after " + timeoutMs + "ms");

        if (raw.isValid(1)) {                          // 1-second validation timeout
            return new PooledConnection(raw, this);
        }
        // Dead socket: discard, decrement, let creation re-establish against promoted replica
        quietClose(raw);
        poolSize.decrementAndGet();
        ensureCapacity();                              // re-create against effective JDBC URL
    }
    throw new SQLException("pool timeout: no valid connection available");
}
```

This is the critical fix after a DB primary failover: the pool drains dead sockets within one borrow cycle, and new connections land on the promoted standby if the JDBC URL includes a failover list (`jdbc:postgresql://primary,standby/db?targetServerType=primary`).

### 4.3 Release and shutdown coordination

```java
void release(PooledConnection conn) {
    if (closed) {
        // Pool is shutting down: close the physical connection immediately
        closeQuietly(conn.delegate);
        allConnections.remove(conn);
        poolSize.decrementAndGet();
        return;
    }
    conn.lastUsedAt = System.currentTimeMillis();
    if (!available.offer(conn)) {
        // Queue full — cannot happen with a correct maxSize invariant,
        // but defensive: discard rather than silently leak.
        closeQuietly(conn.delegate);
        allConnections.remove(conn);
        poolSize.decrementAndGet();
    }
}

@Override
public void close() {
    closed = true;                          // volatile write: all threads see immediately
    healthCheckExecutor.shutdownNow();
    for (PooledConnection conn : allConnections) {
        closeQuietly(conn.delegate);
    }
    allConnections.clear();
    available.clear();
}
```

The `volatile closed` write happens-before any subsequent `closed` read in other threads (JMM happens-before rule for volatile). Threads blocked in `available.poll(timeout, unit)` will either timeout or receive a connection that the release path immediately closes.

### 4.4 Full implementation

```java
public class ConnectionPool implements AutoCloseable {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int maxSize;
    private final Duration acquireTimeout;

    private final ArrayBlockingQueue<PooledConnection> available;
    private final Set<PooledConnection> allConnections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger poolSize = new AtomicInteger(0);
    private volatile boolean closed = false;
    private final ScheduledExecutorService healthCheckExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "connection-pool-health-check");
            t.setDaemon(true);
            return t;
        });

    public ConnectionPool(String jdbcUrl, String user, String pass,
                          int maxSize, Duration timeout) {
        this.jdbcUrl = jdbcUrl;
        this.username = user;
        this.password = pass;
        this.maxSize = maxSize;
        this.acquireTimeout = timeout;
        this.available = new ArrayBlockingQueue<>(maxSize, true); // fair = FIFO waiters

        // Pre-warm with min connections (25% of max)
        int minSize = Math.max(1, maxSize / 4);
        for (int i = 0; i < minSize; i++) {
            try {
                PooledConnection conn = createConnection();
                available.offer(conn);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize pool", e);
            }
        }

        healthCheckExecutor.scheduleAtFixedRate(
            this::healthCheck, 30, 30, TimeUnit.SECONDS);
    }

    public PooledConnection acquire() throws InterruptedException {
        return acquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public PooledConnection acquire(long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) throw new IllegalStateException("Pool is closed");

        PooledConnection conn = available.poll();
        if (conn != null) return validateOrReplace(conn);

        if (poolSize.get() < maxSize) {
            int size;
            do {
                size = poolSize.get();
                if (size >= maxSize) break;
            } while (!poolSize.compareAndSet(size, size + 1));

            if (size < maxSize) {
                try {
                    return createConnection();
                } catch (SQLException e) {
                    poolSize.decrementAndGet();
                    throw new RuntimeException("Failed to create connection", e);
                }
            }
        }

        conn = available.poll(timeout, unit);
        return conn != null ? validateOrReplace(conn) : null;
    }

    private PooledConnection validateOrReplace(PooledConnection conn) {
        try {
            if (conn.delegate.isValid(2)) return conn;
        } catch (SQLException ignored) {}
        closeQuietly(conn.delegate);
        allConnections.remove(conn);
        try {
            return createConnection();
        } catch (SQLException e) {
            poolSize.decrementAndGet();
            throw new RuntimeException("Failed to replace stale connection", e);
        }
    }

    private void healthCheck() {
        List<PooledConnection> toCheck = new ArrayList<>();
        available.drainTo(toCheck);
        for (PooledConnection conn : toCheck) {
            available.offer(validateOrReplace(conn));
        }
    }

    private PooledConnection createConnection() throws SQLException {
        Connection delegate = DriverManager.getConnection(jdbcUrl, username, password);
        PooledConnection conn = new PooledConnection(delegate, this);
        allConnections.add(conn);
        return conn;
    }

    void release(PooledConnection conn) {
        if (closed) {
            closeQuietly(conn.delegate);
            allConnections.remove(conn);
            poolSize.decrementAndGet();
            return;
        }
        conn.lastUsedAt = System.currentTimeMillis();
        if (!available.offer(conn)) {
            closeQuietly(conn.delegate);
            allConnections.remove(conn);
            poolSize.decrementAndGet();
        }
    }

    @Override
    public void close() {
        closed = true;
        healthCheckExecutor.shutdownNow();
        for (PooledConnection conn : allConnections) closeQuietly(conn.delegate);
        allConnections.clear();
        available.clear();
    }

    private static void closeQuietly(Connection c) {
        try { if (c != null && !c.isClosed()) c.close(); } catch (SQLException ignored) {}
    }
}

public class PooledConnection implements AutoCloseable {
    final Connection delegate;
    final ConnectionPool pool;
    volatile long lastUsedAt = System.currentTimeMillis();

    PooledConnection(Connection delegate, ConnectionPool pool) {
        this.delegate = delegate;
        this.pool = pool;
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return delegate.prepareStatement(sql);
    }

    @Override
    public void close() {
        pool.release(this);  // return to pool; NOT a physical close
    }
}

// Correct client usage:
try (PooledConnection conn = pool.acquire()) {
    if (conn == null) throw new TimeoutException("No connection available");
    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id=?")) {
        ps.setLong(1, userId);
        try (ResultSet rs = ps.executeQuery()) { /* process */ }
    }
}  // conn.close() -> pool.release(conn) -> back to available queue
```

---

## 5. Design Decisions & Tradeoffs

| Decision | Chosen | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Available-connections structure | `ArrayBlockingQueue(maxSize, fair=true)` | `LinkedBlockingQueue`, `ConcurrentLinkedDeque`, `SynchronousQueue` | Bounded (prevents OOM from unbounded growth); `fair=true` ensures FIFO for waiters, preventing starvation in multi-tenant scenarios |
| Pool-size growth guard | CAS on `AtomicInteger` | `synchronized` block, `Semaphore` | Lock-free; correct without monitor ownership; `Semaphore` would work equally well but adds an extra object |
| Validation | `isValid(2)` on borrow + background periodic drain | Validation only on borrow, only in background | Hybrid: background keeps the borrow path fast for healthy connections; on-borrow catches primaries that failed since last background run |
| Leak detection | `ConcurrentHashMap<PooledConnection, LeakInfo>` with caller stack trace | `WeakReference` | Stack trace identifies the call site; `WeakReference` loses the reference and can't tell you who forgot to release |
| Shutdown | `closed = true` then drain all | Timeout-based drain | Simple; connections are stateless resources; no user data loss from immediate physical close |

**Throughput vs fairness**: `ArrayBlockingQueue(fair=true)` uses a single `ReentrantLock` with a condition queue — ~620k borrow+release ops/sec at 100 threads. `ConcurrentLinkedDeque` (lock-free) achieves ~850k ops/sec but has no fairness guarantee and requires a separate `AtomicInteger` for size bounding. Choose `ArrayBlockingQueue` when fairness matters (shared multi-tenant pools); choose `ConcurrentLinkedDeque` + MRU-ordering when raw throughput dominates.

---

## 6. Real-World Implementations

**HikariCP** (the standard): uses `ConcurrentBag` — a custom lock-free structure with thread-local lists that gives each borrower thread an affinity slot, eliminating contention entirely for the common case. Result: ~1.4M borrow+release ops/sec. Adds clock-move detection (handles VM pause / NTP jumps resetting `System.nanoTime()`), keepalive pings, and `minimumIdle` housekeeping. HikariCP is what this custom pool evolves into at production scale.

**c3p0**: uses `synchronized` throughout — significantly slower than HikariCP. Notable for its `testConnectionOnCheckout` option, which maps to validate-on-borrow above. Largely superseded by HikariCP.

**PgBouncer (proxy-level)**: eliminates the fleet-total-connections problem by multiplexing thousands of logical connections onto ~100 server connections in transaction mode. The trade-off: session-level features (server-side prepared statements, `SET` session variables, advisory locks, `LISTEN/NOTIFY`) break in transaction mode. Stateless HTTP APIs work fine; stateful session apps must use session mode (lower multiplexing ratio).

**Vitess** (MySQL sharding + pooling): maintains per-shard connection pools and implements backpressure by rejecting queries when pool queues exceed a depth threshold. This is the same bounded-queue backpressure pattern from this design applied at fleet scale. See [Backpressure & Bounded Resources](cross_cutting/backpressure_and_bounded_resources.md) for the general pattern.

**AWS RDS Proxy**: managed proxy that sits between the app and RDS, handles connection multiplexing, failover routing, and IAM auth. Removes the need for a PgBouncer layer for AWS-native workloads; adds ~0.5 ms latency per query.

---

## 7. Technologies & Tools

| Tool | Pool Type | Throughput (100 threads) | Key Feature | Avoid When |
|------|-----------|--------------------------|-------------|------------|
| **HikariCP** | JDBC connection pool | ~1.4M ops/sec | ConcurrentBag, clock-move protection | Never — use it |
| **Custom (this design)** | JDBC connection pool | ~620k ops/sec | Minimal dependencies, embedded use | Production fleet > 5 instances |
| **c3p0** | JDBC connection pool | ~200k ops/sec | testConnectionOnCheckout | New projects — superseded |
| **PgBouncer** | Proxy-level connection multiplexer | 10K–100K clients → 100 server | Transaction-mode multiplexing | Apps relying on session state |
| **Vitess** | MySQL sharding + pooling | Horizontal scale | Sharded pools, backpressure | Non-MySQL, simpler workloads |
| **AWS RDS Proxy** | Managed proxy | Scales with RDS | IAM auth, failover routing | Self-hosted / non-AWS stacks |

Internal queue benchmark (JMH, 100 threads, borrow+release, Java 17, 16-core):

| Queue | Throughput | P99 latency | Notes |
|-------|-----------|-------------|-------|
| `ConcurrentLinkedDeque` | ~850k ops/s | 0.9 µs | Lock-free CAS; MRU cache-warm via `pollFirst/offerFirst` |
| `LinkedTransferQueue` | ~780k ops/s | 1.2 µs | Lock-free; per-offer node allocation (GC pressure) |
| `ArrayBlockingQueue(fair)` | ~620k ops/s | 3.1 µs | Single `ReentrantLock`; fairness adds handoff cost |

---

## 8. Operational Playbook

### a) Key metrics to expose

```java
// Micrometer-style metrics to register on startup
registry.gauge("pool.size.total",     Tags.of("pool", name), poolSize,    AtomicInteger::get);
registry.gauge("pool.size.available", Tags.of("pool", name), available,   ArrayBlockingQueue::size);
registry.gauge("pool.size.active",    Tags.of("pool", name), this,        p -> poolSize.get() - available.size());
registry.counter("pool.acquire.timeout", Tags.of("pool", name));   // inc on null return
registry.timer("pool.acquire.time",   Tags.of("pool", name));      // measure borrow latency
```

Alert thresholds:
- `pool.size.available < 2` for > 60 s → pool starving; raise borrow timeout alarm
- `pool.acquire.timeout > 1/min` → SLA risk; investigate N+1 / leak
- `pool.size.active == pool.size.total` for > 5 min → pool exhausted

### b) Observability: distributed trace span

```
HTTP request span (10 ms)
  ├── pool.acquire (0.05 ms)       ← fast path; tag: hit/miss/timeout
  ├── connection.validate (0.5 ms) ← isValid round trip
  └── db.query (8 ms)              ← the actual work
```

Every acquire emits an OpenTelemetry span with attributes: `pool.name`, `db.system` (`postgresql`), `acquire.result` (`hit` | `created` | `waited` | `timeout`). See [OTel Observability for Spring](../spring/case_studies/cross_cutting/otel_observability_for_spring.md) for the full instrumentation pattern.

### c) Incident Runbooks

**Runbook 1 — Pool exhaustion (acquire timeout spike)**

Symptom: `pool.acquire.timeout` counter climbing; HTTP 503s; `pool.size.available == 0`.

Diagnosis:
1. Check `pool.size.active == pool.size.total` — confirms exhaustion not misconfiguration.
2. Check DB CPU: if low, exhaustion is caused by held connections, not DB slowness.
3. Enable leak detection log: look for "Connection held > 30s by <stack trace>".
4. Check for N+1: trace with `pool.acquire` spans — many rapid acquires inside one request = nested borrow bug.

Mitigation:
- Short-term: increase `maxSize` by 50% (will not fix leaks, buys time).
- Root cause: use try-with-resources at every borrow site; fix N+1 with batched IN-query.
- Verify: `pool.size.active` drops back below 80% of `pool.size.total` within 5 min of deploy.

Resolution: add borrow-site assertion in tests: `assertConnectionsReleasedAfter(request)`.

---

**Runbook 2 — DB primary failover (dead socket storm)**

Symptom: `pool.acquire.timeout` spikes; errors contain "Connection reset" or "broken pipe".

Diagnosis:
1. Check DB cluster status — is the primary down? Is the standby being promoted?
2. Check `pool.size.available` — should be dropping to 0 as validation drains dead sockets.

Mitigation:
1. Force health-check immediately: `pool.triggerHealthCheck()` (add as a management endpoint).
2. All new connections go to the promoted replica (if JDBC URL includes failover list).
3. Expect 2–15 s downtime while Patroni/RDS promotes the standby.

Resolution: set JDBC URL to `jdbc:postgresql://primary,standby/db?targetServerType=primary`; validate `isValid(1)` on every borrow.

---

**Runbook 3 — Pool leak (slow drain over hours)**

Symptom: `pool.size.available` trending to 0 over hours; restart restores it temporarily.

Diagnosis:
1. Enable leak logging: `leakDetectionThreshold = 30_000` (30 s). Look for "Connection leak detected" lines with stack traces.
2. Find the call site in the stack trace that acquires but never releases (missing try-with-resources, or exception path that skips `finally`).

Mitigation: deploy hotfix that wraps the identified borrow site in try-with-resources.
Resolution: add integration test asserting pool size invariant after 1,000 request cycles.

---

**Runbook 4 — Health check thread stuck**

Symptom: `pool.size.available` stops recovering after failover; validation queries time out.

Diagnosis: health-check thread is blocked waiting for `isValid(n)` with a too-long timeout on a dead connection.

Mitigation: reduce validation timeout to 1 s (`isValid(1)`). If network-level TCP timeout is longer, set `socket_timeout` in the JDBC URL (`?socketTimeout=2000`).

---

## 9. Common Pitfalls & War Stories

### War story 1 — N+1 query holding nested connections (pool exhausted at 1/10th load)

**Scenario**: e-commerce platform, 50 concurrent HTTP requests, pool size 50, DB CPU near idle.

**Symptom**: `pool.acquire.timeout` thrown on every request despite DB being idle; 503 cascade.

BROKEN — each order request opens a new connection per line item while still holding the outer connection:

```java
// BROKEN: nested borrow inside a loop -> re-entrant acquisition
try (Connection outer = pool.acquire(50, MILLISECONDS)) {
    List<Order> orders = loadOrders(outer);           // 1 connection held
    for (Order o : orders) {                           // 50 orders
        try (Connection inner = pool.acquire(50, MILLISECONDS)) {  // +1 each!
            o.setLines(loadLines(inner, o.id()));      // N+1 pattern
        }
    }
}
// 50 requests × (1 outer + up to 50 inner) → ~2,500 connections demanded
// against a pool of 50 → all 50 requests queue → timeout cascade
```

FIX — batch the inner query; one connection per request:

```java
// FIX: one connection, one round trip, IN-list batch fetch
try (Connection c = pool.acquire(50, MILLISECONDS)) {
    List<Order> orders = loadOrders(c);
    Map<Long, List<Line>> linesByOrder =
        loadLinesForOrders(c, orders.stream().map(Order::id).toList());
    orders.forEach(o -> o.setLines(linesByOrder.getOrDefault(o.id(), List.of())));
}
// 50 requests × 1 connection = 50. Fits pool. DB does 2 queries not 51.
```

**Root cause**: access-pattern bug, not pool-size bug. Raising the pool would mask it temporarily.
**Impact**: 100% error rate under 50 concurrent users on a service rated for 5,000.

---

### War story 2 — Connection leak from a finally block that itself throws

**Scenario**: fintech transaction service, pool of 30; pool drained to 0 over 6 hours; restart fixed it temporarily.

BROKEN — if `rs.close()` throws, the connection is never returned:

```java
// BROKEN: if rs.close() throws, conn.close() (return-to-pool) never runs
Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
try {
    conn = pool.acquire(50, MILLISECONDS);
    ps = conn.prepareStatement("SELECT ...");
    rs = ps.executeQuery();
    process(rs);
} finally {
    rs.close();     // if this throws (e.g., network reset mid-ResultSet)...
    ps.close();     // ...these lines are skipped
    conn.close();   // LEAK: connection never returned to pool
}
```

FIX — try-with-resources guarantees each resource closes independently:

```java
// FIX (Java 7+): each resource closed in reverse declaration order
try (Connection conn = pool.acquire(50, MILLISECONDS);
     PreparedStatement ps = conn.prepareStatement("SELECT ...");
     ResultSet rs = ps.executeQuery()) {
    process(rs);
}
// Even if rs.close() throws, conn.close() still runs.
// Secondary exceptions attach as suppressed exceptions, not swallowed.
```

**Root cause**: hand-written `finally` with sequential closes — one failure skips all subsequent. `PooledConnection.close()` returning to pool must be unconditionally reachable.
**Impact**: pool silently drained in 6 hours; 100% error rate at peak; ~$40k revenue missed during outage window.

---

### Failure scenarios summary

| Component | Failure | Symptom | Recovery | Time-to-Recovery |
|-----------|---------|---------|----------|------------------|
| DB primary | Crash mid-request | `getConnection()` blocks then `SQLException` | Validate-on-borrow drains dead sockets; new connections hit promoted standby | 2–15 s (replica promotion) |
| ConnectionValidator | Validation query hangs | Borrow path stalls | `isValid(n)` timeout + `socketTimeout` in JDBC URL | Bounded by `n` seconds |
| EvictionPolicy | Evicts live connections | Spurious reconnects, latency spike | `min-idle` floor + grace period before eviction | Immediate — connections re-created |
| Pool itself | Exhaustion | Borrow timeouts, 503 cascade | Backpressure via `connectionTimeout`; fix N+1 / leak | Until load drops or fix deployed |

---

## 10. Capacity Planning

### Primary bottleneck: DB server process count

Each Postgres backend is an OS process. Process context switching and lock-latch contention dominate at high connection counts — throughput peaks near `2 × DB_cores`, then degrades:

```
4-core DB host:
  Pool = 9  → ~9 active queries → 4 run, 5 wait briefly.
              Low context-switch overhead. P99 = ~5 ms.
  Pool = 50 → 50 active queries → 4 run, 46 thrash OS scheduler.
              Context-switch + lock-latch overhead. P99 = ~25 ms at LOWER throughput.
```

### Sizing formula

```
Per-instance pool size:
  pool = (DB_core_count × 2) + effective_spindle_count
       = (4 × 2) + 1  =  9  (4-core DB on SSD)

Fleet total:
  fleet_connections = instances × pool_size
  Must satisfy: fleet_connections ≤ DB max_connections × 0.9  (10% headroom)

  20 instances × 9  = 180  (fits a 200-connection DB)
  50 instances × 9  = 450  → MUST add PgBouncer proxy (maps 450 → ~100 server connections)
```

### Borrow-wait timeout sizing

```
connectionTimeout = SLA_budget − avg_query_time − network_RTT − processing_time
                  = 50 ms − 5 ms − 2 ms − 3 ms  =  40 ms

Set 30 ms (10% safety margin). Requests that cannot borrow within 30 ms get a fast 503
rather than stacking behind each other and making the problem worse.
```

### Worked hardware example (PostgreSQL on db.r6g.xlarge, 4 vCPUs, 32 GB RAM)

```
DB max_connections: 500 (RDS parameter group default for r6g.xlarge)
Recommended server-side pool: 9 connections per app instance
Instances supportable without proxy: 500 × 0.9 / 9 ≈ 50 instances
At 50 instances and above: add PgBouncer in transaction mode
PgBouncer → DB physical connections: ~50 (5× multiplexing headroom)
PgBouncer server: t3.small (2 vCPU, 2 GB RAM) handles 10,000 logical connections
Monthly cost delta: ~$15/month (t3.small) saves ~$800/month in RDS class upgrades
```

---

## 11. Interview Discussion Points

**Q: Why use `ArrayBlockingQueue` over `LinkedBlockingQueue` for the available-connections store?**
`LinkedBlockingQueue` is unbounded — if connections are acquired faster than released (e.g., leaks), the queue grows without limit toward OOM. `ArrayBlockingQueue(maxSize, true)` structurally bounds the queue to `maxSize` entries, matching the pool's invariant, and `fair=true` ensures FIFO ordering for waiting threads, preventing starvation. The structural bound is the key: it makes the OOM bug a compile-time impossibility rather than a runtime risk.

**Q: How do you prevent more than `maxSize` connections from being created under concurrency?**
Use a CAS loop on an `AtomicInteger`: read current size; if below `maxSize`, `compareAndSet(current, current+1)`. If the CAS succeeds, you exclusively own one creation slot — create the connection. If the CAS fails, another thread incremented first; retry the loop. This is lock-free and correct without monitor ownership. A `Semaphore(maxSize)` works equally well and is slightly simpler; choose based on whether you want to embed size tracking in the semaphore itself.

**Q: What happens when `close()` is called while threads are blocked waiting to acquire?**
`volatile closed = true` is written first — this is visible to all threads immediately via the JMM volatile happens-before rule. Threads calling `acquire()` after this throw `IllegalStateException`. Threads already parked in `available.poll(timeout, unit)` will either timeout (return `null`) or receive a connection released by another thread; the release path sees `closed=true` and physically closes that connection rather than returning it to the queue. No connection is leaked and no thread hangs indefinitely.

**Q: How does try-with-resources work with the pool? Why not just call `conn.close()` in `finally`?**
`PooledConnection implements AutoCloseable`. Its `close()` method calls `pool.release(this)` — returning to the pool, not physically closing. Try-with-resources guarantees that `conn.close()` runs even if the body throws, and that each resource closes independently in reverse declaration order. A hand-written `finally` with sequential closes breaks if an earlier close throws — all subsequent closes including the connection return are skipped, leaking the connection permanently.

**Q: Why does a larger connection pool often reduce throughput?**
A JDBC connection is useful only while a DB CPU core executes its query. A 4-core DB can truly run ~4–8 queries concurrently; extra connections just queue inside PostgreSQL (each backend is an OS process) adding context-switch and lock-latch overhead. Throughput peaks near `2 × cores` and degrades beyond it: a pool of 9 beats a pool of 50 on a 4-core host because contention collapses throughput past the parallelism ceiling.

**Q: How do you size a pool for a 4-core database on SSD?**
Use `pool_size = (core_count × 2) + effective_spindle_count`. For 4 cores on SSD (effective spindle ≈ 1) that is `(4×2)+1 = 9` per app instance. Then validate the fleet total: 20 instances × 9 = 180 connections — within a 200-connection ceiling. At 50 instances you exceed the ceiling and must add a PgBouncer proxy rather than increase per-instance pool size.

**Q: Validate-on-borrow vs background health-check — which do you use and why?**
Use both. Background validation runs every 30 s and cheaply reaps idle-dead connections without touching the borrow path latency. Validate-on-borrow (`isValid(1)`) catches the connection that died in the gap between the last background run and the current borrow — critical after a DB primary failover. Hybrid: background for idle connections, lightweight `isValid` on borrow for liveness guarantee. The `isValid(1)` round trip costs ~0.5 ms — negligible against a 5 ms query.

**Q: Transaction-mode vs session-mode pooling in PgBouncer — what breaks in transaction mode?**
Transaction mode assigns a server connection only for one transaction, enabling large client:server multiplexing ratios. What breaks: server-side prepared statements (each logical client sees different server state), `SET` session variables (reset between transactions), session advisory locks, and `LISTEN/NOTIFY`. Choose transaction mode for stateless HTTP APIs (disable client-side prepared-statement caching in the JDBC driver). Use session mode for applications relying on session state.

**Q: A connection pool drains slowly over hours, and a restart fixes it. How do you diagnose and fix?**
Enable leak detection: on every `acquire()`, store `{borrowedAt: Instant, callerStack: StackTraceElement[]}` in a `ConcurrentHashMap`; on `release()`, remove the entry. A scheduled task at 60 s intervals reports any connection held longer than `maxBorrowTime`. The captured stack trace points at the exact call site that failed to release. The structural fix is try-with-resources at every borrow site — `PooledConnection.close()` returning to the pool is then unconditionally guaranteed on every code path.

**Q: How would you extend this pool for a multi-tenant SaaS application?**
Use a bulkhead pattern: give each large tenant its own `ConnectionPool` instance with its own `maxSize`, backed by its own dedicated database or schema. Small/free-tier tenants share a default pool. This prevents one noisy tenant from exhausting connections for others. The routing layer reads the tenant ID from the request context and selects the correct pool. Use `AbstractRoutingDataSource` (Spring) or a simple `ConcurrentHashMap<TenantId, ConnectionPool>` at the service layer.

**Q: How do you handle the case where the DB primary goes down mid-flight?**
In-flight requests holding connections to the dead primary get `SQLException` (TCP reset or read timeout) — they cannot be saved and must be retried by the caller (idempotent operations) or surfaced as 503. The pool recovers by: (1) validating on borrow drains dead sockets within one borrow cycle, and (2) the JDBC URL includes a failover list (`jdbc:postgresql://primary,standby/db?targetServerType=primary`) so new connections land on the promoted standby. Total pool recovery time is dominated by replica promotion (Patroni: 2–15 s), not by pool internals.

**Q: When would you replace this custom pool with HikariCP in production?**
Immediately for any production workload beyond embedded/testing use. HikariCP's `ConcurrentBag` achieves ~1.4M borrow+release ops/sec (vs ~620k for `ArrayBlockingQueue(fair)`) via thread-local affinity slots that eliminate contention for the common case. It also handles clock-move detection (VM pause resets `System.nanoTime()`), keepalive pings, `minimumIdle` housekeeping, and configurable leak detection with stack traces — all production edge cases this custom pool leaves unhandled.
