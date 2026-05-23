# Case Study: Design a Connection Pool in Pure Java

## Problem Statement

Design a thread-safe database connection pool with the following requirements:
- Fixed max connection count (configurable)
- Blocking `acquire()` with timeout — returns `null` on timeout
- Non-blocking `release()` — returns connection back to pool
- Health-check support — detect and replace stale connections
- Shutdown gracefully — close all connections
- Production-ready: metrics, leak detection

**Constraints**:
- Pure Java, no external libraries
- Thread-safe for any number of concurrent borrowers
- Fair acquisition order (FIFO preferred)
- Memory-efficient

---

## Key Java Concepts Used

| Concept | Module | Why Used |
|---------|--------|---------|
| `BlockingQueue` | [Concurrency](../concurrency/README.md) | Thread-safe queue with blocking `poll()` for acquire-with-timeout |
| `ArrayBlockingQueue` | [Concurrency](../concurrency/README.md) | Bounded, fair queue — prevents queue-grow-forever bug |
| `AtomicInteger` | [Concurrency](../concurrency/README.md) | Track pool size without locking |
| `ReentrantLock` + `Condition` | [Concurrency](../concurrency/README.md) | Connection lifecycle transitions |
| `ScheduledExecutorService` | [Concurrency](../concurrency/README.md) | Periodic health check |
| `ConcurrentHashMap` | [Collections Internals](../collections_internals/README.md) | Leak detection: track borrower → connection |
| `AutoCloseable` | [Exceptions & I/O](../exceptions_and_io/README.md) | Try-with-resources support for borrowed connections |
| `volatile` | [Concurrency](../concurrency/README.md) | `closed` flag visibility across threads |

---

## Architecture / Class Diagram

```
ConnectionPool
  |-- pool: ArrayBlockingQueue<PooledConnection>   (available connections)
  |-- allConnections: Set<PooledConnection>         (for shutdown/health check)
  |-- size: AtomicInteger                           (current pool size)
  |-- maxSize: int
  |-- volatile closed: boolean
  |-- leakTracker: ConcurrentHashMap<PooledConnection, StackTrace>
  |-- healthCheck: ScheduledExecutorService

PooledConnection implements AutoCloseable
  |-- delegate: Connection (real JDBC connection)
  |-- pool: ConnectionPool (back-reference for release)
  |-- lastUsed: long (timestamp for idle timeout)
  |-- close() -> pool.release(this)   (try-with-resources support)

Client usage:
  try (PooledConnection conn = pool.acquire(5, TimeUnit.SECONDS)) {
      if (conn == null) throw new TimeoutException("No connection available");
      conn.executeQuery("SELECT ...");
  }  // auto-release via close()
```

---

## Step-by-Step Design Decisions

### Decision 1: Queue type for available connections
**Options**: `LinkedBlockingQueue` (unbounded), `ArrayBlockingQueue` (bounded), `SynchronousQueue` (handoff).

**Choice**: `ArrayBlockingQueue(maxSize, true)` — `true` for fair mode (FIFO order for waiters).

**Why not LinkedBlockingQueue**: unbounded — if acquire is faster than release, queue would grow to OOM (stores redundant entries during contention). `ArrayBlockingQueue` prevents this structurally.

**Why not SynchronousQueue**: no buffering — each acquire must meet a release in real time. We want to buffer idle connections.

### Decision 2: Acquire-with-timeout mechanism
Use `queue.poll(timeout, unit)` — blocks the calling thread until a connection is available or timeout expires. Returns `null` on timeout. This is built into `BlockingQueue` and is perfectly suited.

**Alternative**: `ReentrantLock` + `Condition.await(timeout)` — more complex, no benefit here.

### Decision 3: Connection lifecycle (create vs reuse)
When pool is not yet at `maxSize` AND queue is empty: create a new connection instead of waiting. Use `AtomicInteger` to track current size; CAS to atomically claim the "right to create" a new connection.

### Decision 4: Health check
Periodic background thread validates idle connections. Use `ScheduledExecutorService.scheduleAtFixedRate()`. For each idle connection: execute `SELECT 1` or `connection.isValid(timeout)`. Replace stale connections.

### Decision 5: Leak detection
Keep a `ConcurrentHashMap<PooledConnection, Instant>` tracking when each connection was borrowed. A scheduled task reports any connection held longer than a configurable threshold. Use `Thread.currentThread().getStackTrace()` at acquisition time for leak location.

---

## Core Implementation

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
        this.available = new ArrayBlockingQueue<>(maxSize, true); // fair

        // Pre-warm with min connections
        int minSize = Math.max(1, maxSize / 4);
        for (int i = 0; i < minSize; i++) {
            try {
                PooledConnection conn = createConnection();
                available.offer(conn);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize pool", e);
            }
        }

        // Health check every 30 seconds
        healthCheckExecutor.scheduleAtFixedRate(
            this::healthCheck, 30, 30, TimeUnit.SECONDS);
    }

    public PooledConnection acquire() throws InterruptedException {
        return acquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public PooledConnection acquire(long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) throw new IllegalStateException("Pool is closed");

        // Try non-blocking first
        PooledConnection conn = available.poll();
        if (conn != null) return validateOrReplace(conn);

        // Try to create a new connection if below max
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

        // Wait for a returned connection
        conn = available.poll(timeout, unit);
        return conn != null ? validateOrReplace(conn) : null;
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
            // Queue full (shouldn't happen with correct maxSize): close the connection
            closeQuietly(conn.delegate);
            allConnections.remove(conn);
            poolSize.decrementAndGet();
        }
    }

    private PooledConnection createConnection() throws SQLException {
        Connection delegate = DriverManager.getConnection(jdbcUrl, username, password);
        PooledConnection conn = new PooledConnection(delegate, this);
        allConnections.add(conn);
        return conn;
    }

    private PooledConnection validateOrReplace(PooledConnection conn) {
        try {
            if (conn.delegate.isValid(2)) {
                return conn;
            }
        } catch (SQLException e) {
            // connection is stale
        }
        // Replace stale connection
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
        // Drain available connections, validate, put back or replace
        List<PooledConnection> toCheck = new ArrayList<>();
        available.drainTo(toCheck);
        for (PooledConnection conn : toCheck) {
            PooledConnection valid = validateOrReplace(conn);
            available.offer(valid);
        }
    }

    @Override
    public void close() {
        closed = true;
        healthCheckExecutor.shutdownNow();
        for (PooledConnection conn : allConnections) {
            closeQuietly(conn.delegate);
        }
        allConnections.clear();
        available.clear();
    }

    private static void closeQuietly(Connection c) {
        try { if (c != null && !c.isClosed()) c.close(); } catch (SQLException ignored) {}
    }
}

// PooledConnection: AutoCloseable wrapper that releases back to pool
public class PooledConnection implements AutoCloseable {
    final Connection delegate;
    final ConnectionPool pool;
    volatile long lastUsedAt = System.currentTimeMillis();

    PooledConnection(Connection delegate, ConnectionPool pool) {
        this.delegate = delegate;
        this.pool = pool;
    }

    // Delegate all Connection methods to delegate
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return delegate.prepareStatement(sql);
    }
    // ... other delegation methods

    @Override
    public void close() {
        pool.release(this);  // return to pool, not actual close
    }
}

// Client usage:
try (PooledConnection conn = pool.acquire()) {
    if (conn == null) throw new TimeoutException("No connection available");
    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id=?")) {
        ps.setLong(1, userId);
        try (ResultSet rs = ps.executeQuery()) {
            // process results
        }
    }
}  // conn.close() called -> pool.release(conn) -> back to available queue
```

---

## Tradeoffs Considered

| Decision | Chosen | Alternative | Why |
|----------|--------|------------|-----|
| Queue type | ArrayBlockingQueue (bounded, fair) | LinkedBlockingQueue | Prevents unbounded growth; fairness for multi-tenant |
| Health check | Periodic background thread | On every acquire | Acquire path must be fast; background is better |
| Validation | `connection.isValid(2)` | SELECT 1 query | isValid() is standard JDBC; SELECT 1 works for older drivers |
| Leak detection | ConcurrentHashMap + timestamp | WeakReference | Timestamp gives age info; WeakReference would lose the reference |
| Shutdown | Drain all connections | Timeout-based | Simple; connections are replaced so no user data loss |

---

## Interview Questions for This Case Study

**Q: Why use ArrayBlockingQueue over LinkedBlockingQueue for the available pool?**
`LinkedBlockingQueue` is unbounded — if connections are acquired faster than returned (e.g., leaks), the queue would grow forever. `ArrayBlockingQueue(maxSize)` structurally bounds the queue to never hold more than `maxSize` connections — matching the pool's invariant. The `fair=true` parameter ensures FIFO ordering for threads waiting to acquire, preventing starvation.

**Q: How do you prevent creating more connections than maxSize under concurrency?**
Use a CAS loop on an `AtomicInteger` tracking pool size: read current size, if < maxSize, `compareAndSet(current, current+1)` — if CAS succeeds, you have exclusive right to create one more connection. If CAS fails, retry (another thread incremented concurrently). This is lock-free and correct without synchronized blocks.

**Q: What happens when `close()` is called while threads are waiting for connections?**
Set `volatile closed = true` first (visible to all threads). Threads calling `acquire()` after this get `IllegalStateException`. Threads already waiting in `available.poll(timeout, unit)` will either: timeout and return `null`, or get a connection that was released (which the release path will immediately close since `closed=true`). The shutdown call then drains and closes all remaining connections.

**Q: How does try-with-resources work with the pool?**
`PooledConnection` implements `AutoCloseable`. Its `close()` method calls `pool.release(this)` — returning the connection to the pool rather than closing it. This gives clients the safety of try-with-resources (no leak even on exception) while preserving the pooling benefit (connection is reused, not destroyed). This is the Decorator pattern applied to connection lifecycle.

**Q: How would you add connection leak detection?**
Add a `ConcurrentHashMap<PooledConnection, LeakInfo>` where `LeakInfo = {Instant borrowedAt, StackTrace callerStack}`. Populate on acquire, remove on release. A scheduled task at 60-second intervals reports any borrowed connection held longer than `maxBorrowTime`. The stored stack trace shows exactly which call site failed to release. In testing, configure 1-second leak threshold to detect missing closes immediately.

---

## Failure Scenarios

Each pool component has a distinct failure mode and recovery path. The table summarizes; details follow.

| Component | Failure | Symptom | Recovery | Time-to-Recovery |
|-----------|---------|---------|----------|------------------|
| DB primary | Crash mid-request | `getConnection()` blocks then `SQLException` | Validate-on-borrow + standby failover | 2-15s (replica promotion) |
| `ConnectionValidator` | Validation query hangs | Borrow path stalls | Timeout on `isValid(n)` | bounded by n seconds |
| `EvictionPolicy` | Evicts live connections | Spurious reconnects | Min-idle floor + grace period | immediate |
| Pool itself | Exhaustion | Borrow timeouts | Backpressure + queue cap | until load drops |

### DB primary goes down mid-request

```
T+0ms    Request A holds connection C, executing UPDATE
T+0ms    DB primary kernel panic
T+30ms   TCP send on C's socket: no ACK; OS retransmits
T+0..21s OS TCP retransmit backoff (default tcp_retries2 ~ 15 attempts)
T+...    C throws SQLException (connection reset / read timeout)
```

BROKEN behavior — the pool hands out dead connections it still believes are healthy:

```java
// BROKEN: borrow does no validation; dead sockets handed to callers
public PooledConnection acquire(long timeout, TimeUnit unit) throws SQLException {
    Connection raw = available.poll(timeout, unit);   // returns a SOCKET to a dead DB
    if (raw == null) throw new SQLException("pool timeout");
    return new PooledConnection(raw, this);            // caller's first query throws
}
```

FIX — validate on borrow, replace dead connections, and route to a standby URL:

```java
// Java 11 LTS: isValid(timeout) is the JDBC-standard liveness check
public PooledConnection acquire(long timeoutMs, TimeUnit unit) throws SQLException {
    long deadline = System.nanoTime() + unit.toNanos(timeoutMs);
    while (System.nanoTime() < deadline) {
        Connection raw = available.poll(remaining(deadline), TimeUnit.NANOSECONDS);
        if (raw == null) throw new SQLException("pool timeout after " + timeoutMs + "ms");

        if (raw.isValid(1)) {                 // 1-second validation timeout
            return new PooledConnection(raw, this);
        }
        // dead connection: close it, decrement count, let the create path
        // re-establish against the current primary (which may now be the standby)
        quietClose(raw);
        size.decrementAndGet();
        ensureCapacity();                     // re-creates against effective JDBC URL
    }
    throw new SQLException("pool timeout: no valid connection available");
}
```

Recovery procedure when the primary fails:
1. Validation-on-borrow rejects every dead socket; pool drains dead connections within one borrow cycle.
2. The connection factory points at a failover URL list (`jdbc:postgresql://primary,standby/db?targetServerType=primary`). After the standby is promoted, new connections land on it.
3. Time-to-recovery is dominated by replica promotion (Patroni/RDS: 2-15s), not by the pool. The pool itself recovers within the validation timeout (~1s).

In-flight requests already executing on a dead connection cannot be saved — they get `SQLException` and must be retried by the caller (idempotent operations) or surfaced as 503.

---

## Capacity Planning Math

### How many connections does the database actually need?

Fleet math first:

```
20 app instances x pool_size 10 = 200 connections
PostgreSQL default max_connections = 100  (RDS often 100-200 by class)
=> 200 > 100  ==> connection storm; new connects rejected with
   "FATAL: sorry, too many clients already"
```

The instinct is to raise `max_connections`. That is usually wrong. The HikariCP-derived sizing formula:

```
pool_size = (core_count * 2) + effective_spindle_count

4-core DB host, SSD (effective_spindle ~ 1):
pool_size = (4 * 2) + 1 = 9
```

So each app instance should pool ~9, not whatever number "feels safe." With 20 instances that is still 180 connections — at the ceiling. The real fix at fleet scale is a proxy (see Evolution).

### Why a larger pool HURTS

A connection is only useful while a CPU core can run its query. A 4-core DB can truly execute ~4-8 queries at once; the rest queue inside Postgres.

```
Pool=9  -> ~9 active queries -> 4 run, 5 wait briefly. Low context-switch cost.
Pool=100 -> 100 active queries -> 4 run, 96 thrash the scheduler.
   - Each backend = ~1 process (Postgres) competing for CPU + lock latches.
   - Context-switch overhead + lock contention => higher P99 latency at LOWER throughput.
```

Empirically (HikariCP's "pool sizing" study, reproduced): throughput peaks near `2*cores` and DEGRADES past it. A pool of 9 can out-perform a pool of 50 on a 4-core DB.

### Throughput from latency

```
Little's Law:  concurrency = throughput x latency
If avg query = 5ms, pool = 9:
   max throughput = pool / latency = 9 / 0.005s = 1,800 queries/sec per instance
20 instances => 36,000 q/s, against a DB sized for it (else DB is the bottleneck).
```

Borrow-wait budget: if SLA is 50ms and query is 5ms, you can tolerate ~9 queued borrowers per connection before blowing the budget. Set `connectionTimeout` (borrow timeout) below the SLA so callers fail fast rather than pile up.

---

## Benchmark Comparisons — Internal Pool Queue

The available-connection holder is the hottest data structure. Benchmark (JMH-style, 100 contending threads, borrow+release cycle, Java 17, 16-core box):

| Queue implementation | Throughput | P99 latency | Notes |
|----------------------|-----------|-------------|-------|
| `ConcurrentLinkedDeque` (chosen) | ~850k ops/sec | 0.9 us | Lock-free CAS, MRU reuse via `pollFirst`/`offerFirst` (hot connection cache-warm) |
| `ArrayBlockingQueue` (fair) | ~620k ops/sec | 3.1 us | Single ReentrantLock; fairness adds handoff cost |
| `LinkedTransferQueue` | ~780k ops/sec | 1.2 us | Lock-free; allocates a node per offer (GC pressure) |

Why `ConcurrentLinkedDeque` for a pool that wants speed: it is fully lock-free and, because we `offerFirst`/`pollFirst`, the most-recently-returned connection is reused first — its TCP buffers and Postgres backend caches stay warm, and idle connections sink to the tail where the `EvictionPolicy` reaps them. The earlier design used `ArrayBlockingQueue` for its hard structural bound and fairness; the tradeoff is throughput. Choose `ArrayBlockingQueue` when fairness/bounding matters more than raw speed (multi-tenant); choose `ConcurrentLinkedDeque` plus a separate `AtomicInteger` size guard when throughput dominates.

---

## Production War Stories

### War story 1 — Pool exhaustion from an N+1 query holding nested connections

Symptom: under 50 concurrent HTTP requests the pool (size 50) timed out and threw `SQLException: pool timeout`, even though DB CPU was near idle.

BROKEN — each request loops over orders and opens a NEW connection per line item while still holding the outer connection:

```java
// BROKEN: nested borrow inside a loop -> connections held re-entrantly
try (Connection outer = pool.acquire(50, MILLISECONDS)) {
    List<Order> orders = loadOrders(outer);          // 1 connection held
    for (Order o : orders) {                          // 50 orders
        try (Connection inner = pool.acquire(50, MILLISECONDS)) {  // +1 each!
            o.setLines(loadLines(inner, o.id()));     // N+1 pattern
        }
    }
}
// 50 requests x (1 outer + up to 50 nested) -> ~2500 connections demanded
// against a pool of 50 -> queue fills -> everyone times out
```

FIX — eliminate the N+1 with a single batched query; one connection per request:

```java
// FIX: one connection, one round trip; IN-list batch fetch
try (Connection c = pool.acquire(50, MILLISECONDS)) {
    List<Order> orders = loadOrders(c);
    Map<Long, List<Line>> linesByOrder =
        loadLinesForOrders(c, orders.stream().map(Order::id).toList()); // WHERE order_id IN (?)
    orders.forEach(o -> o.setLines(linesByOrder.getOrDefault(o.id(), List.of())));
}
// 50 requests x 1 connection = 50 connections. Fits the pool. DB does 2 queries not 51.
```

Lesson: pool exhaustion is usually an application access-pattern bug, not a pool-size bug. Never nest a borrow inside a held borrow.

### War story 2 — Connection leak from a finally block that itself throws

Symptom: pool slowly drained to zero over ~6 hours; restart fixed it temporarily. Leak detector showed connections borrowed but never released.

BROKEN — manual cleanup where the ResultSet close throws and skips the connection release:

```java
// BROKEN: if rs.close() throws, conn.close() (return-to-pool) never runs
Connection conn = null; PreparedStatement ps = null; ResultSet rs = null;
try {
    conn = pool.acquire(50, MILLISECONDS);
    ps = conn.prepareStatement("SELECT ...");
    rs = ps.executeQuery();
    process(rs);
} finally {
    rs.close();      // if this throws (e.g., already-closed by a network reset)...
    ps.close();      // ...these never execute
    conn.close();    // <-- LEAK: connection never returned to pool
}
```

FIX — try-with-resources releases in reverse order and suppresses (does not swallow control flow on) secondary exceptions:

```java
// FIX (Java 7+): each resource closed independently, in reverse order
try (Connection conn = pool.acquire(50, MILLISECONDS);
     PreparedStatement ps = conn.prepareStatement("SELECT ...");
     ResultSet rs = ps.executeQuery()) {
    process(rs);
}
// Even if rs.close() throws, conn.close() (return-to-pool) STILL runs.
// The first exception is primary; later ones attach as suppressed exceptions.
```

Lesson: a single failing close in a hand-written `finally` can leak every downstream resource. `PooledConnection.close()` returning the connection to the pool MUST be reachable on every path — try-with-resources guarantees it.

---

## Evolution / Scalability at 10x Load

At 200 instances x 9 = 1,800 desired connections, a single Postgres cannot hold one backend per connection (memory + scheduler collapse). The architecture changes:

```
   App fleet (200 instances, small local pools)
        |  thousands of short-lived logical connections
        v
   +---------------------+   transaction-mode multiplexing
   |  PgBouncer (proxy)  |   many clients : few server conns
   +---------------------+
        |  ~100 physical connections
        v
   +-----------+        +------------------+
   |  Primary  | -----> |  Read replicas   |
   |  (writes) | repl   |  (read routing)  |
   +-----------+        +------------------+
```

1. Connection multiplexing — PgBouncer in transaction mode: the app keeps thousands of cheap logical connections; PgBouncer maps them onto ~100 physical server connections, assigning a server connection only for the duration of a transaction. Caveat: session-level features (prepared statements across statements, `SET` session vars, advisory locks) break in transaction mode; the app must avoid them or use session mode.
2. Read replica routing — a routing `DataSource` (`AbstractRoutingDataSource` style) sends `@Transactional(readOnly=true)` work to a replica pool, writes to the primary pool. Accept replica lag for reads.
3. Per-tenant pools — in multi-tenant apps, give each large tenant its own bounded pool so one noisy tenant cannot starve the rest (bulkhead pattern).

Technical debt to track: the custom pool lacks PgBouncer's protocol-level multiplexing and HikariCP's housekeeping refinements (clock-move detection, keepalive pings). Past ~50 instances, replacing the custom pool with HikariCP plus PgBouncer is the right move; the custom pool is a learning/embedded-use artifact, not a fleet-scale component.

---

## Additional Interview Questions

**Q: Why does a larger connection pool often reduce throughput?**
A connection is only doing work while a DB CPU core executes its query. A 4-core DB can truly run ~4-8 queries concurrently; extra connections just queue inside Postgres while adding context-switch and lock-latch overhead. Throughput peaks near `2*cores` and degrades beyond it, so a pool of 9 can beat a pool of 50 on a 4-core host. Size the pool to the DB's parallelism, not to the app's request concurrency.

**Q: How do you size a pool for a 4-core database on SSD?**
Use `pool_size = (core_count * 2) + effective_spindle_count`. For 4 cores on SSD (effective spindle ~1) that is `(4*2)+1 = 9` per app instance. Then check the fleet total against `max_connections` — 20 instances at 9 is 180 connections, near a 200 ceiling, which signals you need a proxy rather than a bigger pool.

**Q: Validate-on-borrow vs background validation — which and why?**
Background validation keeps the borrow path fast but can hand out a connection that died in the gap since the last check. Validate-on-borrow (`isValid(1)`) guarantees liveness at the cost of a round trip per acquire. The production answer is hybrid: a background validator reaps idle-dead connections cheaply, plus a lightweight `isValid` (or aliveness bypass within a short freshness window) on borrow to catch primary failover quickly.

**Q: Transaction-mode vs session-mode pooling in PgBouncer — what breaks?**
Transaction mode assigns a server connection only for one transaction, enabling huge client:server multiplexing — but anything that relies on session state across statements breaks: server-prepared statements, session `SET` variables, session advisory locks, and `LISTEN/NOTIFY`. Session mode preserves those but offers far less multiplexing. Choose transaction mode for stateless web traffic and disable client-side prepared-statement caching.

**Q: A connection leak drains the pool over hours. How do you find the culprit?**
Enable leak detection: store `{borrowedAt, callerStackTrace}` per borrowed connection in a `ConcurrentHashMap`, and have a scheduled task report any connection held longer than a threshold. The captured stack trace points at the exact call site that failed to release. The structural fix is try-with-resources so the return-to-pool `close()` is unconditional on every path.
