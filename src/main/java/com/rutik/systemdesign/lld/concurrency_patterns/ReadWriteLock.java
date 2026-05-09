package com.rutik.systemdesign.lld.concurrency_patterns; /**
 * READ-WRITE LOCK PATTERN
 *
 * Problem: A resource is read frequently but written rarely.
 *          Multiple concurrent readers are safe (no mutation).
 *          Writers need exclusive access (no readers, no other writers).
 *
 * Principle:
 *   - Many readers can hold the read lock simultaneously
 *   - A writer must wait until ALL readers release, then gets exclusive lock
 *   - While a writer holds the lock, no readers can acquire it
 *
 * Three implementations:
 *   1. Manual ReadWriteLock (educational — shows the mechanism)
 *   2. Java's ReentrantReadWriteLock (production)
 *   3. StampedLock with optimistic reads (Java 8+, high-performance)
 *
 * Real-world example: Hot-reloadable configuration
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 1: Manual ReadWriteLock (educational)
// ══════════════════════════════════════════════════════════════

class ManualReadWriteLock {
    private int readers    = 0;
    private int writers    = 0;
    private int writeWaiters = 0;

    /**
     * Acquire read lock.
     * Block if a writer is active or waiting (prevent writer starvation).
     */
    public synchronized void readLock() throws InterruptedException {
        // Prefer writers — prevent writer starvation
        while (writers > 0 || writeWaiters > 0) {
            wait();
        }
        readers++;
    }

    /**
     * Release read lock. If last reader, wake up a waiting writer.
     */
    public synchronized void readUnlock() {
        readers--;
        if (readers == 0) {
            notifyAll(); // wake up waiting writers
        }
    }

    /**
     * Acquire write lock. Must wait for all readers and other writers.
     */
    public synchronized void writeLock() throws InterruptedException {
        writeWaiters++;
        try {
            while (readers > 0 || writers > 0) {
                wait();
            }
        } finally {
            writeWaiters--;
        }
        writers++;
    }

    /**
     * Release write lock. Wake up all (readers or next writer).
     */
    public synchronized void writeUnlock() {
        writers--;
        notifyAll(); // wake up both readers and writers; readers check condition
    }

    public synchronized int getReaderCount() { return readers; }
    public synchronized int getWriterCount() { return writers; }
}

class ManualRWLockDemo {
    private final ManualReadWriteLock lock = new ManualReadWriteLock();
    private int sharedData = 0;

    public int read(String threadName) throws InterruptedException {
        lock.readLock();
        try {
            System.out.printf("  [%s] Reading: %d (active readers: %d)%n",
                    threadName, sharedData, lock.getReaderCount());
            Thread.sleep(50); // simulate read time
            return sharedData;
        } finally {
            lock.readUnlock();
        }
    }

    public void write(String threadName, int value) throws InterruptedException {
        lock.writeLock();
        try {
            System.out.printf("  [%s] Writing: %d → %d%n", threadName, sharedData, value);
            Thread.sleep(100); // simulate write time
            sharedData = value;
        } finally {
            lock.writeUnlock();
        }
    }

    public static void demonstrate() throws InterruptedException {
        System.out.println("=== Manual ReadWriteLock ===");
        ManualRWLockDemo demo = new ManualRWLockDemo();
        List<Thread> threads = new ArrayList<>();

        // 5 readers + 2 writers
        for (int i = 1; i <= 5; i++) {
            final int id = i;
            threads.add(new Thread(() -> {
                try { demo.read("Reader-" + id); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
        }
        for (int i = 1; i <= 2; i++) {
            final int id = i;
            threads.add(new Thread(() -> {
                try { demo.write("Writer-" + id, id * 100); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();
        System.out.println("  Final value: " + demo.sharedData);
    }
}

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 2: ReentrantReadWriteLock — Thread-safe Cache
// ══════════════════════════════════════════════════════════════

class ReadWriteCache<K, V> {
    private final Map<K, V> cache = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(
            true // fair mode — prevents starvation
    );
    private final Lock readLock  = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    public V get(K key) {
        readLock.lock();    // multiple threads can hold this simultaneously
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();  // ALWAYS in finally
        }
    }

    public void put(K key, V value) {
        writeLock.lock();   // exclusive — all readers blocked
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    public V computeIfAbsent(K key, java.util.function.Function<K, V> loader) {
        // First try with read lock (optimistic path)
        readLock.lock();
        try {
            V existing = cache.get(key);
            if (existing != null) return existing;
        } finally {
            readLock.unlock();
        }

        // Must upgrade to write lock — but ReentrantReadWriteLock does NOT support
        // direct upgrade (would deadlock). Must release read, acquire write.
        writeLock.lock();
        try {
            // Double-check after acquiring write lock
            V existing = cache.get(key);
            if (existing != null) return existing;

            V computed = loader.apply(key);
            cache.put(key, computed);
            return computed;
        } finally {
            writeLock.unlock();
        }
    }

    public void remove(K key) {
        writeLock.lock();
        try { cache.remove(key); }
        finally { writeLock.unlock(); }
    }

    public int size() {
        readLock.lock();
        try { return cache.size(); }
        finally { readLock.unlock(); }
    }

    /** Lock downgrade: from write to read (allowed in ReentrantReadWriteLock) */
    public V putAndRead(K key, V value) {
        writeLock.lock();
        V result;
        try {
            cache.put(key, value);
            readLock.lock();  // acquire read lock BEFORE releasing write lock
            result = cache.get(key);
        } finally {
            writeLock.unlock(); // release write lock — read lock still held
        }
        try {
            return result;  // reading while holding read lock only
        } finally {
            readLock.unlock();
        }
    }

    // Monitoring
    public int getReadLockCount()   { return lock.getReadLockCount(); }
    public boolean isWriteLocked()  { return lock.isWriteLocked(); }
    public int getQueueLength()     { return lock.getQueueLength(); }
}

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 3: StampedLock — Optimistic Reading (Java 8+)
// ══════════════════════════════════════════════════════════════

/**
 * StampedLock is more flexible but lower-level than ReentrantReadWriteLock.
 * Key difference: optimistic reads — try to read WITHOUT acquiring a lock,
 * then validate. If data changed, fall back to real read lock.
 *
 * Performance: optimistic reads are ~3x faster than ReentrantReadWriteLock
 * when reads rarely conflict with writes.
 */
class Point {
    private double x, y;
    private final StampedLock lock = new StampedLock();

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Optimistic read — no lock acquired if data is stable */
    public double distanceFromOrigin() {
        long stamp = lock.tryOptimisticRead(); // returns non-zero if no writer
        double currX = x;  // read without lock
        double currY = y;

        if (!lock.validate(stamp)) {
            // Data changed while we were reading — fall back to real read lock
            stamp = lock.readLock();
            try {
                currX = x;
                currY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return Math.sqrt(currX * currX + currY * currY);
    }

    /** Write operation */
    public void move(double deltaX, double deltaY) {
        long stamp = lock.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /** Convert read lock to write lock */
    public void moveToOriginIfFarAway(double threshold) {
        long stamp = lock.readLock();
        try {
            while (Math.sqrt(x*x + y*y) > threshold) {
                long writeStamp = lock.tryConvertToWriteLock(stamp);
                if (writeStamp != 0L) {
                    stamp = writeStamp;
                    x = 0;
                    y = 0;
                    break;
                } else {
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
            }
        } finally {
            lock.unlock(stamp);
        }
    }

    @Override public String toString() { return String.format("Point(%.2f, %.2f)", x, y); }
}

// ══════════════════════════════════════════════════════════════
// REAL-WORLD: Hot-Reloadable Configuration
// ══════════════════════════════════════════════════════════════

class HotReloadableConfig {
    private Map<String, String> config = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Lock readLock  = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private volatile long lastReloadTime = System.currentTimeMillis();

    public HotReloadableConfig() {
        // Initial config
        config.put("db.host", "localhost");
        config.put("db.port", "5432");
        config.put("cache.ttl", "3600");
        config.put("feature.dark_mode", "false");
    }

    /** Called by many application threads — concurrent reads allowed */
    public String get(String key) {
        readLock.lock();
        try {
            return config.getOrDefault(key, "");
        } finally {
            readLock.unlock();
        }
    }

    public String get(String key, String defaultValue) {
        readLock.lock();
        try {
            return config.getOrDefault(key, defaultValue);
        } finally {
            readLock.unlock();
        }
    }

    /** Called by a single reload thread every N seconds */
    public void reload(Map<String, String> newConfig) {
        writeLock.lock();
        try {
            System.out.println("  [Config] Reloading configuration (blocking " +
                    lock.getReadLockCount() + " readers)...");
            this.config = new HashMap<>(newConfig);
            this.lastReloadTime = System.currentTimeMillis();
            System.out.println("  [Config] Reload complete. New config: " + newConfig);
        } finally {
            writeLock.unlock();
        }
    }

    public long getLastReloadTime() { return lastReloadTime; }

    public static void demonstrate() throws InterruptedException {
        System.out.println("\n=== Hot-Reloadable Config ===");
        HotReloadableConfig config = new HotReloadableConfig();
        CountDownLatch latch = new CountDownLatch(1);

        // Simulate many app threads reading config
        ExecutorService readers = Executors.newFixedThreadPool(5);
        for (int i = 1; i <= 5; i++) {
            final int id = i;
            readers.submit(() -> {
                try {
                    latch.await();
                    for (int j = 0; j < 3; j++) {
                        String value = config.get("feature.dark_mode");
                        System.out.printf("  [AppThread-%d] feature.dark_mode = %s%n", id, value);
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }

        // Reload thread
        Thread reloadThread = new Thread(() -> {
            try {
                latch.await();
                Thread.sleep(150); // wait a bit then reload
                Map<String, String> newConfig = new HashMap<>();
                newConfig.put("db.host", "prod-db.internal");
                newConfig.put("db.port", "5432");
                newConfig.put("cache.ttl", "7200");
                newConfig.put("feature.dark_mode", "true");  // changed!
                config.reload(newConfig);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "ConfigReloader");
        reloadThread.start();

        latch.countDown(); // start all threads
        readers.shutdown();
        readers.awaitTermination(5, TimeUnit.SECONDS);
        reloadThread.join();
    }
}

// ─────────────────────────────────────────────────────────────
// Main Demo
// ─────────────────────────────────────────────────────────────

class ReadWriteLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ManualRWLockDemo.demonstrate();

        System.out.println("\n=== ReentrantReadWriteLock Cache ===");
        ReadWriteCache<String, String> cache = new ReadWriteCache<>();
        cache.put("user:1", "Alice");
        cache.put("user:2", "Bob");
        System.out.println("  user:1 = " + cache.get("user:1"));
        System.out.println("  user:2 = " + cache.get("user:2"));
        String loaded = cache.computeIfAbsent("user:3",
                k -> { System.out.println("  Loading " + k + " from DB..."); return "Carol"; });
        System.out.println("  user:3 = " + loaded);
        System.out.println("  user:3 again = " + cache.computeIfAbsent("user:3", k -> "SHOULD_NOT_LOAD"));

        System.out.println("\n=== StampedLock Point ===");
        Point p = new Point(3.0, 4.0);
        System.out.println("  Initial: " + p + ", distance = " + p.distanceFromOrigin());
        p.move(1.0, 0.0);
        System.out.println("  After move(1,0): " + p + ", distance = " + p.distanceFromOrigin());

        HotReloadableConfig.demonstrate();
    }
}
