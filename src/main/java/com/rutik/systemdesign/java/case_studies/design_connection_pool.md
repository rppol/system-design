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
