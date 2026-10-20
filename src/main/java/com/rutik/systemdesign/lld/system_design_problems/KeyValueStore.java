package com.rutik.systemdesign.lld.system_design_problems;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

// =============================================================================
//  IN-MEMORY KEY-VALUE STORE WITH TRANSACTIONS AND TTL - Low-Level Design
//  Patterns used:
//    - Command / Unit of Work : KvTxLayer buffers writes until commit
//    - Memento-like stack     : nested transactions are a stack of write layers
//    - Optimistic concurrency : first-touch versions are validated at commit
//  The committed map is guarded by one ReentrantReadWriteLock. A KvSession is
//  owned by one thread at a time. Expiry is lazy on read plus an explicit sweep
//  over a min-heap of deadlines. The clock is injected so the demo is exact.
// =============================================================================

record KvEntry(String value, long version, long expiresAtNanos) {
    static final long NO_EXPIRY = Long.MAX_VALUE;

    boolean expiredAt(long nowNanos) {
        return nowNanos >= expiresAtNanos;
    }
}

/** A pending change inside a transaction; a null value means delete. */
record KvWrite(String value, long expiresAtNanos) {
    boolean isDelete() {
        return value == null;
    }
}

record KvExpiry(String key, long version, long atNanos) {}

final class KvConflictException extends RuntimeException {
    KvConflictException(String key) {
        super("write conflict on key '" + key + "'");
    }
}

final class KvTxLayer {
    final Map<String, KvWrite> writes = new LinkedHashMap<>();
}

final class KvFakeClock implements LongSupplier {
    private long nowNanos;

    void advance(Duration d) {
        nowNanos += d.toNanos();
    }

    @Override
    public long getAsLong() {
        return nowNanos;
    }
}

/** One client's view of the store. Not thread-safe: one session per thread. */
final class KvSession {
    private final KeyValueStore store;
    private final Deque<KvTxLayer> layers = new ArrayDeque<>();      // innermost transaction first
    private final Map<String, Long> baseVersions = new HashMap<>();  // version seen at first touch

    KvSession(KeyValueStore store) {
        this.store = store;
    }

    public Optional<String> get(String key) {
        long now = store.now();
        for (KvTxLayer layer : layers) {
            KvWrite w = layer.writes.get(key);
            if (w != null) {
                return w.isDelete() || now >= w.expiresAtNanos() ? Optional.empty() : Optional.of(w.value());
            }
        }
        KvEntry e = store.live(key, now);
        if (inTransaction()) baseVersions.putIfAbsent(key, e == null ? 0L : e.version());
        return e == null ? Optional.empty() : Optional.of(e.value());
    }

    public void set(String key, String value) {
        write(key, new KvWrite(value, KvEntry.NO_EXPIRY));
    }

    public void setWithTtl(String key, String value, Duration ttl) {
        write(key, new KvWrite(value, store.now() + ttl.toNanos()));
    }

    public void delete(String key) {
        write(key, new KvWrite(null, KvEntry.NO_EXPIRY));
    }

    public void begin() {
        layers.push(new KvTxLayer());
    }

    public void commit() {
        KvTxLayer top = popLayer();
        if (!layers.isEmpty()) {
            layers.peek().writes.putAll(top.writes);   // inner commit folds into the parent only
            return;
        }
        try {
            store.apply(top.writes, baseVersions);
        } finally {
            baseVersions.clear();
        }
    }

    public void rollback() {
        popLayer();
        if (layers.isEmpty()) baseVersions.clear();
    }

    public boolean inTransaction() {
        return !layers.isEmpty();
    }

    private void write(String key, KvWrite w) {
        if (!inTransaction()) {
            store.apply(Map.of(key, w), Map.of());
            return;
        }
        if (!baseVersions.containsKey(key)) {
            KvEntry e = store.live(key, store.now());
            baseVersions.put(key, e == null ? 0L : e.version());
        }
        layers.peek().writes.put(key, w);
    }

    private KvTxLayer popLayer() {
        if (layers.isEmpty()) throw new IllegalStateException("no transaction");
        return layers.pop();
    }
}

public final class KeyValueStore {
    private final Map<String, KvEntry> data = new HashMap<>();
    private final PriorityQueue<KvExpiry> deadlines =
            new PriorityQueue<>(Comparator.comparingLong(KvExpiry::atNanos));
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private final LongSupplier clock;
    private long lastVersion;

    public KeyValueStore(LongSupplier clock) {
        this.clock = clock;
    }

    public KvSession openSession() {
        return new KvSession(this);
    }

    long now() {
        return clock.getAsLong();
    }

    /** Committed entry, or null if absent or expired (lazy expiry: hidden, removed by sweep). */
    KvEntry live(String key, long nowNanos) {
        rw.readLock().lock();
        try {
            KvEntry e = data.get(key);
            return e == null || e.expiredAt(nowNanos) ? null : e;
        } finally {
            rw.readLock().unlock();
        }
    }

    /** Validates first-touch versions, then applies every write atomically. */
    void apply(Map<String, KvWrite> writes, Map<String, Long> expectedVersions) {
        rw.writeLock().lock();
        try {
            long now = now();
            for (Map.Entry<String, Long> exp : expectedVersions.entrySet()) {
                KvEntry cur = data.get(exp.getKey());
                long curVersion = cur == null || cur.expiredAt(now) ? 0L : cur.version();
                if (curVersion != exp.getValue()) throw new KvConflictException(exp.getKey());
            }
            for (Map.Entry<String, KvWrite> w : writes.entrySet()) {
                String key = w.getKey();
                KvWrite write = w.getValue();
                if (write.isDelete()) {
                    data.remove(key);
                    continue;
                }
                long version = ++lastVersion;
                data.put(key, new KvEntry(write.value(), version, write.expiresAtNanos()));
                if (write.expiresAtNanos() != KvEntry.NO_EXPIRY) {
                    deadlines.add(new KvExpiry(key, version, write.expiresAtNanos()));
                }
            }
        } finally {
            rw.writeLock().unlock();
        }
    }

    /** Removes every entry whose deadline has passed; stale heap entries are skipped by version. */
    public int sweepExpired() {
        rw.writeLock().lock();
        try {
            long now = now();
            int removed = 0;
            while (!deadlines.isEmpty() && deadlines.peek().atNanos() <= now) {
                KvExpiry due = deadlines.poll();
                KvEntry e = data.get(due.key());
                if (e != null && e.version() == due.version()) {
                    data.remove(due.key());
                    removed++;
                }
            }
            return removed;
        } finally {
            rw.writeLock().unlock();
        }
    }

    public int storedKeys() {
        rw.readLock().lock();
        try {
            return data.size();
        } finally {
            rw.readLock().unlock();
        }
    }

    public static void main(String[] args) {
        KvFakeClock clock = new KvFakeClock();
        KeyValueStore store = new KeyValueStore(clock);
        KvSession s = store.openSession();

        System.out.println("== basic ==");
        s.set("a", "1");
        System.out.println("get a -> " + s.get("a").orElse("(nil)"));
        s.delete("a");
        System.out.println("after delete, get a -> " + s.get("a").orElse("(nil)"));

        System.out.println("== nested transactions ==");
        s.set("balance", "100");
        s.begin();
        s.set("balance", "150");
        s.begin();
        s.set("balance", "999");
        System.out.println("inner tx sees balance -> " + s.get("balance").orElseThrow());
        s.rollback();
        System.out.println("after inner rollback -> " + s.get("balance").orElseThrow());
        KvSession other = store.openSession();
        System.out.println("other session before commit -> " + other.get("balance").orElseThrow());
        s.commit();
        System.out.println("other session after commit -> " + other.get("balance").orElseThrow());

        System.out.println("== TTL ==");
        s.setWithTtl("session:42", "alice", Duration.ofSeconds(30));
        s.setWithTtl("otp:42", "731904", Duration.ofSeconds(60));
        clock.advance(Duration.ofSeconds(31));
        System.out.println("t=31s session:42 -> " + s.get("session:42").orElse("(nil)"));
        System.out.println("t=31s otp:42 -> " + s.get("otp:42").orElse("(nil)"));
        System.out.println("stored before sweep = " + store.storedKeys());
        System.out.println("swept = " + store.sweepExpired() + ", stored after sweep = " + store.storedKeys());

        System.out.println("== optimistic conflict ==");
        s.set("stock:sku-1", "5");
        KvSession buyer1 = store.openSession();
        KvSession buyer2 = store.openSession();
        buyer1.begin();
        buyer2.begin();
        int seen1 = Integer.parseInt(buyer1.get("stock:sku-1").orElseThrow());
        int seen2 = Integer.parseInt(buyer2.get("stock:sku-1").orElseThrow());
        buyer1.set("stock:sku-1", String.valueOf(seen1 - 1));
        buyer2.set("stock:sku-1", String.valueOf(seen2 - 1));
        buyer1.commit();
        System.out.println("buyer1 committed, stock -> " + s.get("stock:sku-1").orElseThrow());
        try {
            buyer2.commit();
        } catch (KvConflictException e) {
            System.out.println("buyer2 rejected: " + e.getMessage() + ", stock stays " + s.get("stock:sku-1").orElseThrow());
        }
    }
}
