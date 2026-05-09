package com.rutik.systemdesign.lld.creational.singleton;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SINGLETON PATTERN — Real World Example
 *
 * Scenario: Database Connection Pool
 *
 * Problem:
 *   An application has many services (OrderService, UserService, InventoryService)
 *   that all need to query the database. Creating a new connection on each query
 *   is expensive (TCP handshake, authentication, memory allocation). Creating
 *   one pool per service wastes connections and bypasses pool limits.
 *
 * Solution:
 *   A singleton DatabaseConnectionPool that all services share. The pool holds
 *   a fixed number of pre-opened connections. Services borrow a connection,
 *   use it, and return it. The Singleton guarantee ensures the pool limit is
 *   respected globally.
 *
 * Pattern used: Initialization-on-Demand Holder (thread-safe, lazy, no lock overhead)
 *
 * Run: javac RealWorldExample.java && java DatabaseConnectionPoolDemo
 */

// ─────────────────────────────────────────────────────────────────────────────
// Simulated Connection — represents a real DB connection
// ─────────────────────────────────────────────────────────────────────────────
class Connection {

    private static final AtomicInteger idSequence = new AtomicInteger(0);
    private final int id;
    private boolean inUse;

    public Connection() {
        this.id = idSequence.incrementAndGet();
        this.inUse = false;
        System.out.println("  [Pool] Opened connection #" + id);
    }

    public void query(String sql) {
        System.out.println("  [Connection #" + id + "] Executing: " + sql);
    }

    public boolean isInUse() { return inUse; }
    public void setInUse(boolean inUse) { this.inUse = inUse; }
    public int getId() { return id; }

    public void close() {
        System.out.println("  [Pool] Closed connection #" + id);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Singleton: DatabaseConnectionPool
// ─────────────────────────────────────────────────────────────────────────────
class DatabaseConnectionPool {

    private static final int MAX_POOL_SIZE = 3;
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/appdb";

    private final Deque<Connection> availableConnections = new ArrayDeque<>();
    private final Deque<Connection> usedConnections = new ArrayDeque<>();

    // Private constructor — initializes the pool with pre-opened connections
    private DatabaseConnectionPool() {
        System.out.println("[Pool] Initializing DatabaseConnectionPool (max=" + MAX_POOL_SIZE + ", url=" + DB_URL + ")");
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            availableConnections.add(new Connection());
        }
        System.out.println("[Pool] Pool ready with " + MAX_POOL_SIZE + " connections.");
    }

    // Holder class — loaded lazily, only when getInstance() is first called
    private static final class Holder {
        static final DatabaseConnectionPool INSTANCE = new DatabaseConnectionPool();
    }

    // Global access point
    public static DatabaseConnectionPool getInstance() {
        return Holder.INSTANCE;
    }

    // Borrow a connection from the pool
    public synchronized Connection acquire() {
        if (availableConnections.isEmpty()) {
            throw new RuntimeException("[Pool] No connections available! Pool exhausted (max=" + MAX_POOL_SIZE + ").");
        }
        Connection conn = availableConnections.poll();
        conn.setInUse(true);
        usedConnections.add(conn);
        System.out.println("[Pool] Acquired connection #" + conn.getId()
                + " | available=" + availableConnections.size()
                + " | in-use=" + usedConnections.size());
        return conn;
    }

    // Return a connection back to the pool
    public synchronized void release(Connection conn) {
        conn.setInUse(false);
        usedConnections.remove(conn);
        availableConnections.add(conn);
        System.out.println("[Pool] Released connection #" + conn.getId()
                + " | available=" + availableConnections.size()
                + " | in-use=" + usedConnections.size());
    }

    public int getAvailableCount() { return availableConnections.size(); }
    public int getUsedCount() { return usedConnections.size(); }
    public int getMaxSize() { return MAX_POOL_SIZE; }
}

// ─────────────────────────────────────────────────────────────────────────────
// Application Services — all use the shared pool via getInstance()
// ─────────────────────────────────────────────────────────────────────────────
class OrderService {

    public void createOrder(int userId, String item) {
        System.out.println("\n[OrderService] Creating order for user " + userId + ": " + item);
        Connection conn = DatabaseConnectionPool.getInstance().acquire();
        try {
            conn.query("INSERT INTO orders (user_id, item) VALUES (" + userId + ", '" + item + "')");
            conn.query("UPDATE inventory SET stock = stock - 1 WHERE item = '" + item + "'");
        } finally {
            DatabaseConnectionPool.getInstance().release(conn);
        }
    }
}

class UserService {

    public void getUser(int userId) {
        System.out.println("\n[UserService] Fetching user " + userId);
        Connection conn = DatabaseConnectionPool.getInstance().acquire();
        try {
            conn.query("SELECT * FROM users WHERE id = " + userId);
        } finally {
            DatabaseConnectionPool.getInstance().release(conn);
        }
    }
}

class InventoryService {

    public void checkStock(String item) {
        System.out.println("\n[InventoryService] Checking stock for: " + item);
        Connection conn = DatabaseConnectionPool.getInstance().acquire();
        try {
            conn.query("SELECT stock FROM inventory WHERE item = '" + item + "'");
        } finally {
            DatabaseConnectionPool.getInstance().release(conn);
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Demo Entry Point
// ─────────────────────────────────────────────────────────────────────────────
public class RealWorldExample {

    public static void main(String[] args) {

        System.out.println("=== Singleton Pattern: Database Connection Pool Demo ===\n");

        // ── Verify Singleton Identity ─────────────────────────────────────────
        DatabaseConnectionPool pool1 = DatabaseConnectionPool.getInstance();
        DatabaseConnectionPool pool2 = DatabaseConnectionPool.getInstance();
        System.out.println("pool1 == pool2: " + (pool1 == pool2));  // Must be true
        System.out.println("pool1 hashCode: " + System.identityHashCode(pool1));
        System.out.println("pool2 hashCode: " + System.identityHashCode(pool2));
        System.out.println();

        // ── Services sharing the same pool ────────────────────────────────────
        OrderService orderService = new OrderService();
        UserService userService = new UserService();
        InventoryService inventoryService = new InventoryService();

        // Sequential usage — connections are borrowed and returned
        userService.getUser(42);
        orderService.createOrder(42, "Laptop");
        inventoryService.checkStock("Laptop");

        // ── Pool exhaustion demonstration ─────────────────────────────────────
        System.out.println("\n--- Demonstrating pool exhaustion ---");
        DatabaseConnectionPool pool = DatabaseConnectionPool.getInstance();

        Connection c1 = pool.acquire();
        Connection c2 = pool.acquire();
        Connection c3 = pool.acquire();

        System.out.println("\nAll connections in use. Attempting to acquire a 4th...");
        try {
            Connection c4 = pool.acquire(); // Should throw
        } catch (RuntimeException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        }

        // Release and re-acquire
        System.out.println("\n--- Releasing c1, then re-acquiring ---");
        pool.release(c1);
        Connection c4 = pool.acquire();
        System.out.println("Successfully acquired connection #" + c4.getId() + " after release.");

        // Clean up
        pool.release(c2);
        pool.release(c3);
        pool.release(c4);

        System.out.println("\n--- Final pool state ---");
        System.out.println("Available: " + pool.getAvailableCount() + "/" + pool.getMaxSize());
        System.out.println("In use:    " + pool.getUsedCount());

        System.out.println("\n=== Demo complete ===");
    }
}
