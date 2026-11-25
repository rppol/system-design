package com.rutik.systemdesign.lld.system_design_problems;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

// =============================================================================
//  CACHE MANAGER - Low-Level Design
//
//  Extends LRUCache.java. That file hand-builds one LRU and one LFU as two
//  unrelated classes. This file puts eviction behind a Strategy, adds TTL,
//  makes the cache safe under concurrency with lock striping, and adds a
//  manager that owns many named caches.
//
//  Core ideas:
//    - Storage (HashMap of entries) and ordering (EvictionPolicy) are separate.
//      Each policy owns its OWN key-indexed structure, so no node type leaks
//      across the interface. The cost is one extra hash lookup per operation.
//    - TTL is lazy on read plus an active sweep over a min-heap of deadlines.
//    - The cache is split into power-of-two segments, each with its own lock,
//      map, policy and expiry heap.
//    - Removal listeners run AFTER the segment lock is released.
//    - getOrLoad collapses concurrent misses for one key into a single load.
//
//  Patterns used:
//    - Strategy : EvictionPolicy (LRU, LFU, FIFO)
//    - Factory  : PolicyType creates the policy for each segment
//    - Builder  : CacheConfig
//    - Observer : RemovalListener with a RemovalCause
//    - Facade / Registry : CacheManager owns the named caches
// =============================================================================

public class CacheManager {

    // -------------------------------------------------------------------------
    //  CLOCK - injected so TTL is testable without sleeping
    // -------------------------------------------------------------------------

    interface Ticker {
        long nanos();
    }

    static final class ManualTicker implements Ticker {
        private final AtomicLong now = new AtomicLong();

        @Override
        public long nanos() {
            return now.get();
        }

        void advance(Duration d) {
            now.addAndGet(d.toNanos());
        }
    }

    // -------------------------------------------------------------------------
    //  STRATEGY - eviction policy. Each policy owns its own key ordering.
    //  Every method is called with the segment lock held.
    // -------------------------------------------------------------------------

    interface EvictionPolicy<K> {
        void recordInsert(K key);

        void recordAccess(K key);

        void recordRemoval(K key);

        /** The key to evict next, or null when nothing is tracked. */
        K victim();

        int trackedKeys();

        /** Non-destructive snapshot, next victim first. For tests and debugging. */
        List<K> evictionOrder();
    }

    /** LinkedHashSet keeps insertion order; remove + add moves a key to the tail in O(1). */
    static final class LruPolicy<K> implements EvictionPolicy<K> {
        private final LinkedHashSet<K> order = new LinkedHashSet<>();

        @Override
        public void recordInsert(K key) {
            order.add(key);
        }

        @Override
        public void recordAccess(K key) {
            if (order.remove(key)) {
                order.add(key);
            }
        }

        @Override
        public void recordRemoval(K key) {
            order.remove(key);
        }

        @Override
        public K victim() {
            Iterator<K> it = order.iterator();
            return it.hasNext() ? it.next() : null;
        }

        @Override
        public int trackedKeys() {
            return order.size();
        }

        @Override
        public List<K> evictionOrder() {
            return new ArrayList<>(order);
        }
    }

    /** FIFO is LRU that ignores reads. */
    static final class FifoPolicy<K> implements EvictionPolicy<K> {
        private final LinkedHashSet<K> order = new LinkedHashSet<>();

        @Override
        public void recordInsert(K key) {
            order.add(key);
        }

        @Override
        public void recordAccess(K key) {
            // insertion order only
        }

        @Override
        public void recordRemoval(K key) {
            order.remove(key);
        }

        @Override
        public K victim() {
            Iterator<K> it = order.iterator();
            return it.hasNext() ? it.next() : null;
        }

        @Override
        public int trackedKeys() {
            return order.size();
        }

        @Override
        public List<K> evictionOrder() {
            return new ArrayList<>(order);
        }
    }

    /**
     * LFU with frequency buckets in a TreeMap. The classic O(1) "minFreq" trick
     * assumes keys leave only through eviction. With TTL expiry and explicit
     * removal, the minimum bucket can empty at any time, so the smallest
     * non-empty bucket is found with firstEntry(): O(log F), F = distinct counts.
     * Ties inside a bucket break by recency (LinkedHashSet order).
     */
    static final class LfuPolicy<K> implements EvictionPolicy<K> {
        private final Map<K, Integer> frequency = new HashMap<>();
        private final TreeMap<Integer, LinkedHashSet<K>> buckets = new TreeMap<>();

        @Override
        public void recordInsert(K key) {
            frequency.put(key, 1);
            buckets.computeIfAbsent(1, f -> new LinkedHashSet<>()).add(key);
        }

        @Override
        public void recordAccess(K key) {
            Integer f = frequency.get(key);
            if (f == null) {
                return;
            }
            detach(key, f);
            frequency.put(key, f + 1);
            buckets.computeIfAbsent(f + 1, x -> new LinkedHashSet<>()).add(key);
        }

        @Override
        public void recordRemoval(K key) {
            Integer f = frequency.remove(key);
            if (f != null) {
                detach(key, f);
            }
        }

        private void detach(K key, int f) {
            LinkedHashSet<K> bucket = buckets.get(f);
            bucket.remove(key);
            if (bucket.isEmpty()) {
                buckets.remove(f);
            }
        }

        @Override
        public K victim() {
            Map.Entry<Integer, LinkedHashSet<K>> lowest = buckets.firstEntry();
            return lowest == null ? null : lowest.getValue().iterator().next();
        }

        @Override
        public int trackedKeys() {
            return frequency.size();
        }

        @Override
        public List<K> evictionOrder() {
            List<K> out = new ArrayList<>();
            for (LinkedHashSet<K> bucket : buckets.values()) {
                out.addAll(bucket);
            }
            return out;
        }
    }

    // -------------------------------------------------------------------------
    //  FACTORY - one fresh policy per segment
    // -------------------------------------------------------------------------

    enum PolicyType {
        LRU {
            @Override
            <K> EvictionPolicy<K> create() {
                return new LruPolicy<>();
            }
        },
        LFU {
            @Override
            <K> EvictionPolicy<K> create() {
                return new LfuPolicy<>();
            }
        },
        FIFO {
            @Override
            <K> EvictionPolicy<K> create() {
                return new FifoPolicy<>();
            }
        };

        abstract <K> EvictionPolicy<K> create();
    }

    // -------------------------------------------------------------------------
    //  OBSERVER - removal notifications with a cause
    // -------------------------------------------------------------------------

    enum RemovalCause { EXPLICIT, REPLACED, SIZE, EXPIRED }

    interface RemovalListener<K, V> {
        void onRemoval(String cache, K key, V value, RemovalCause cause);
    }

    record Removal<K, V>(K key, V value, RemovalCause cause) { }

    // -------------------------------------------------------------------------
    //  BUILDER - cache configuration
    // -------------------------------------------------------------------------

    static final class CacheConfig<K, V> {
        final int maximumSize;
        final PolicyType policy;
        final Duration defaultTtl;        // null = no expiry
        final int segments;               // power of two
        final RemovalListener<K, V> listener;
        final Ticker ticker;

        private CacheConfig(Builder<K, V> b) {
            this.maximumSize = b.maximumSize;
            this.policy = b.policy;
            this.defaultTtl = b.defaultTtl;
            this.segments = b.segments;
            this.listener = b.listener;
            this.ticker = b.ticker;
        }

        static <K, V> Builder<K, V> builder() {
            return new Builder<>();
        }

        static final class Builder<K, V> {
            private int maximumSize = 1_000;
            private PolicyType policy = PolicyType.LRU;
            private Duration defaultTtl;
            private int segments = 1;
            private RemovalListener<K, V> listener = (c, k, v, cause) -> { };
            private Ticker ticker = System::nanoTime;

            Builder<K, V> maximumSize(int n) {
                this.maximumSize = n;
                return this;
            }

            Builder<K, V> policy(PolicyType p) {
                this.policy = p;
                return this;
            }

            Builder<K, V> defaultTtl(Duration ttl) {
                this.defaultTtl = ttl;
                return this;
            }

            Builder<K, V> segments(int n) {
                this.segments = n;
                return this;
            }

            Builder<K, V> listener(RemovalListener<K, V> l) {
                this.listener = l;
                return this;
            }

            Builder<K, V> ticker(Ticker t) {
                this.ticker = t;
                return this;
            }

            CacheConfig<K, V> build() {
                if (maximumSize <= 0) {
                    throw new IllegalArgumentException("maximumSize must be positive");
                }
                if (segments <= 0 || Integer.bitCount(segments) != 1) {
                    throw new IllegalArgumentException("segments must be a power of two");
                }
                if (segments > maximumSize) {
                    throw new IllegalArgumentException("more segments than entries");
                }
                return new CacheConfig<>(this);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  SEGMENT - one lock, one map, one policy, one expiry heap
    // -------------------------------------------------------------------------

    record Entry<V>(V value, long expiresAt, long version) {
        boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    record Deadline<K>(K key, long expiresAt, long version) { }

    static final class Segment<K, V> {
        final ReentrantLock lock = new ReentrantLock();
        final Map<K, Entry<V>> map = new HashMap<>();
        final EvictionPolicy<K> policy;
        final PriorityQueue<Deadline<K>> deadlines =
                new PriorityQueue<>((a, b) -> Long.compare(a.expiresAt(), b.expiresAt()));
        final int capacity;

        Segment(EvictionPolicy<K> policy, int capacity) {
            this.policy = policy;
            this.capacity = capacity;
        }

        /** Removes a key from map and policy. Caller holds the lock. */
        Removal<K, V> unlink(K key, RemovalCause cause) {
            Entry<V> e = map.remove(key);
            policy.recordRemoval(key);
            return e == null ? null : new Removal<>(key, e.value(), cause);
        }
    }

    // -------------------------------------------------------------------------
    //  THE CACHE
    // -------------------------------------------------------------------------

    static final class ManagedCache<K, V> {
        final String name;
        private final CacheConfig<K, V> config;
        private final Segment<K, V>[] segments;
        private final AtomicLong versions = new AtomicLong();
        private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
        final LongAdder hits = new LongAdder();
        final LongAdder misses = new LongAdder();
        final LongAdder evictions = new LongAdder();
        final LongAdder expirations = new LongAdder();
        final LongAdder loads = new LongAdder();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ManagedCache(String name, CacheConfig<K, V> config) {
            this.name = name;
            this.config = config;
            this.segments = new Segment[config.segments];
            int perSegment = config.maximumSize / config.segments;
            for (int i = 0; i < segments.length; i++) {
                segments[i] = new Segment<>(config.policy.create(), perSegment);
            }
        }

        private Segment<K, V> segmentFor(K key) {
            int h = key.hashCode();
            h ^= (h >>> 16);                            // spread high bits, as HashMap does
            return segments[h & (segments.length - 1)];
        }

        V get(K key) {
            Segment<K, V> s = segmentFor(key);
            List<Removal<K, V>> removed = new ArrayList<>(1);
            V result = null;
            s.lock.lock();
            try {
                Entry<V> e = s.map.get(key);
                if (e != null && e.expired(config.ticker.nanos())) {
                    removed.add(s.unlink(key, RemovalCause.EXPIRED));   // lazy expiry
                    expirations.increment();
                } else if (e != null) {
                    s.policy.recordAccess(key);
                    result = e.value();
                }
            } finally {
                s.lock.unlock();
            }
            if (result == null) {
                misses.increment();
            } else {
                hits.increment();
            }
            notifyAll(removed);
            return result;
        }

        void put(K key, V value) {
            put(key, value, config.defaultTtl);
        }

        void put(K key, V value, Duration ttl) {
            if (key == null || value == null) {
                throw new NullPointerException("null keys and values are not cached");
            }
            Segment<K, V> s = segmentFor(key);
            List<Removal<K, V>> removed = new ArrayList<>(2);
            s.lock.lock();
            try {
                long now = config.ticker.nanos();
                long expiresAt = ttl == null ? Long.MAX_VALUE : now + ttl.toNanos();
                long version = versions.incrementAndGet();
                Entry<V> old = s.map.get(key);
                if (old == null) {
                    // Evict BEFORE inserting, so the newcomer can never be its own victim.
                    while (s.map.size() >= s.capacity) {
                        removed.add(evictOne(s, now));
                    }
                }
                s.map.put(key, new Entry<>(value, expiresAt, version));
                if (old != null) {
                    s.policy.recordAccess(key);
                    removed.add(new Removal<>(key, old.value(), RemovalCause.REPLACED));
                } else {
                    s.policy.recordInsert(key);
                }
                if (expiresAt != Long.MAX_VALUE) {
                    s.deadlines.add(new Deadline<>(key, expiresAt, version));
                }
            } finally {
                s.lock.unlock();
            }
            notifyAll(removed);
        }

        /** Prefer an already-expired entry over a live victim. Caller holds the lock. */
        private Removal<K, V> evictOne(Segment<K, V> s, long now) {
            Deadline<K> d;
            while ((d = s.deadlines.peek()) != null && d.expiresAt() <= now) {
                s.deadlines.poll();
                Entry<V> e = s.map.get(d.key());
                if (e != null && e.version() == d.version()) {
                    expirations.increment();
                    return s.unlink(d.key(), RemovalCause.EXPIRED);
                }
            }
            K victim = s.policy.victim();
            evictions.increment();
            return s.unlink(victim, RemovalCause.SIZE);
        }

        boolean remove(K key) {
            Segment<K, V> s = segmentFor(key);
            Removal<K, V> r;
            s.lock.lock();
            try {
                r = s.unlink(key, RemovalCause.EXPLICIT);
            } finally {
                s.lock.unlock();
            }
            notifyAll(r == null ? List.of() : List.of(r));
            return r != null;
        }

        /** Active expiry: drains due deadlines from every segment. Returns entries removed. */
        int sweepExpired() {
            int count = 0;
            for (Segment<K, V> s : segments) {
                List<Removal<K, V>> removed = new ArrayList<>();
                s.lock.lock();
                try {
                    long now = config.ticker.nanos();
                    Deadline<K> d;
                    while ((d = s.deadlines.peek()) != null && d.expiresAt() <= now) {
                        s.deadlines.poll();
                        Entry<V> e = s.map.get(d.key());
                        if (e != null && e.version() == d.version()) {   // skip stale deadlines
                            removed.add(s.unlink(d.key(), RemovalCause.EXPIRED));
                            expirations.increment();
                        }
                    }
                } finally {
                    s.lock.unlock();
                }
                count += removed.size();
                notifyAll(removed);
            }
            return count;
        }

        /**
         * Single flight: concurrent misses for one key share one load. The
         * loader runs OUTSIDE every segment lock, so a slow load never blocks
         * other keys in the same segment.
         */
        V getOrLoad(K key, Function<K, V> loader) {
            V cached = get(key);
            if (cached != null) {
                return cached;
            }
            CompletableFuture<V> mine = new CompletableFuture<>();
            CompletableFuture<V> theirs = inFlight.putIfAbsent(key, mine);
            if (theirs != null) {
                return theirs.join();
            }
            try {
                V again = get(key);                  // another thread may have finished first
                V value = again != null ? again : loadAndStore(key, loader);
                mine.complete(value);
                return value;
            } catch (RuntimeException e) {
                mine.completeExceptionally(e);
                throw e;
            } finally {
                inFlight.remove(key, mine);
            }
        }

        private V loadAndStore(K key, Function<K, V> loader) {
            loads.increment();
            V value = loader.apply(key);
            put(key, value);
            return value;
        }

        int size() {
            int n = 0;
            for (Segment<K, V> s : segments) {
                s.lock.lock();
                try {
                    n += s.map.size();
                } finally {
                    s.lock.unlock();
                }
            }
            return n;
        }

        int capacity() {
            return segments.length * segments[0].capacity;
        }

        /** Invariant: every segment's policy tracks exactly the keys in its map. */
        boolean consistent() {
            for (Segment<K, V> s : segments) {
                s.lock.lock();
                try {
                    if (s.policy.trackedKeys() != s.map.size() || s.map.size() > s.capacity) {
                        return false;
                    }
                } finally {
                    s.lock.unlock();
                }
            }
            return true;
        }

        /** Debug view: keys in the order the policy would evict them, per segment. */
        List<K> keysInEvictionOrder() {
            List<K> out = new ArrayList<>();
            for (Segment<K, V> s : segments) {
                s.lock.lock();
                try {
                    out.addAll(s.policy.evictionOrder());
                } finally {
                    s.lock.unlock();
                }
            }
            return out;
        }

        String stats() {
            long h = hits.sum();
            long m = misses.sum();
            double ratio = h + m == 0 ? 0 : 100.0 * h / (h + m);
            return String.format("hits=%d misses=%d hitRatio=%.1f%% evictions=%d expirations=%d loads=%d",
                    h, m, ratio, evictions.sum(), expirations.sum(), loads.sum());
        }

        private void notifyAll(List<Removal<K, V>> removed) {
            for (Removal<K, V> r : removed) {
                if (r != null) {
                    config.listener.onRemoval(name, r.key(), r.value(), r.cause());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    //  THE MANAGER - a registry of named caches plus one sweeper
    // -------------------------------------------------------------------------

    private final Map<String, ManagedCache<?, ?>> caches = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    <K, V> ManagedCache<K, V> getOrCreate(String name, CacheConfig<K, V> config) {
        return (ManagedCache<K, V>) caches.computeIfAbsent(name, n -> new ManagedCache<>(n, config));
    }

    /** Called by a scheduled executor in production; called by hand in the demo. */
    int sweepAll() {
        int total = 0;
        for (ManagedCache<?, ?> c : caches.values()) {
            total += c.sweepExpired();
        }
        return total;
    }

    // -------------------------------------------------------------------------
    //  DEMO
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   Cache Manager - LLD Demo");
        System.out.println("========================================");

        CacheManager manager = new CacheManager();
        RemovalListener<String, String> printer = (cache, k, v, cause) ->
                System.out.printf("    [removal] %-8s key=%s cause=%s%n", cache, k, cause);

        System.out.println("\n--- 1. One access sequence, three policies, same cache code ---");
        System.out.println("  sequence: put a,b,c | get b x3 | get a x2 | get c | put d");
        for (PolicyType type : PolicyType.values()) {
            ManagedCache<String, String> c = manager.getOrCreate("demo-" + type,
                    CacheConfig.<String, String>builder().maximumSize(3).policy(type).listener(printer).build());
            c.put("a", "A");
            c.put("b", "B");
            c.put("c", "C");
            c.get("b");
            c.get("b");
            c.get("b");
            c.get("a");
            c.get("a");
            c.get("c");
            c.put("d", "D");
            System.out.println("  " + type + " keeps, next victim first: " + c.keysInEvictionOrder());
        }

        System.out.println("\n--- 2. TTL: lazy expiry on read, active expiry by the sweeper ---");
        ManualTicker clock = new ManualTicker();
        ManagedCache<String, String> sessions = manager.getOrCreate("sessions",
                CacheConfig.<String, String>builder()
                        .maximumSize(100).defaultTtl(Duration.ofSeconds(30))
                        .listener(printer).ticker(clock).build());
        sessions.put("s1", "alice");
        sessions.put("s2", "bob", Duration.ofSeconds(5));
        sessions.put("s3", "carol", Duration.ofSeconds(5));
        sessions.put("s4", "dave", Duration.ofSeconds(5));
        clock.advance(Duration.ofSeconds(6));
        System.out.println("  t=6s  get(s2) -> " + sessions.get("s2") + ", size=" + sessions.size());
        System.out.println("  t=6s  sweepAll removed " + manager.sweepAll() + ", size=" + sessions.size());
        System.out.println("  t=6s  get(s1) -> " + sessions.get("s1"));
        sessions.put("s1", "alice-v2", Duration.ofSeconds(5));
        clock.advance(Duration.ofSeconds(30));
        System.out.println("  t=36s sweepAll removed " + manager.sweepAll()
                + " (two s1 deadlines were due, the 30s one belonged to the replaced value)");
        System.out.println("  stats: " + sessions.stats());

        System.out.println("\n--- 3. Stampede: 16 threads miss the same key ---");
        ManagedCache<String, String> profiles = manager.getOrCreate("profiles",
                CacheConfig.<String, String>builder().maximumSize(100).segments(4).build());
        AtomicInteger naiveLoads = new AtomicInteger();
        runConcurrently(16, () -> {
            if (profiles.get("user:7") == null) {            // BROKEN: check-then-act
                naiveLoads.incrementAndGet();
                sleep(50);
                profiles.put("user:7", "profile-7");
            }
        });
        System.out.println("  naive get-then-put: loader ran more than once = " + (naiveLoads.get() > 1));
        AtomicInteger flightLoads = new AtomicInteger();
        List<String> seen = java.util.Collections.synchronizedList(new ArrayList<>());
        runConcurrently(16, () -> seen.add(profiles.getOrLoad("user:42", k -> {
            flightLoads.incrementAndGet();
            sleep(50);
            return "profile-42";
        })));
        System.out.println("  getOrLoad single flight: loader ran " + flightLoads.get()
                + " time(s), " + seen.stream().filter("profile-42"::equals).count() + " of 16 callers got the value");

        System.out.println("\n--- 4. Stress: 8 threads x 20,000 mixed ops, 8 segments, cap 1,024 ---");
        ManagedCache<Integer, Integer> hot = manager.getOrCreate("hot",
                CacheConfig.<Integer, Integer>builder().maximumSize(1_024).segments(8)
                        .policy(PolicyType.LFU).build());
        AtomicInteger seed = new AtomicInteger();
        runConcurrently(8, () -> {
            Random rnd = new Random(seed.incrementAndGet());
            for (int i = 0; i < 20_000; i++) {
                int key = rnd.nextInt(4_096);
                if (rnd.nextBoolean()) {
                    hot.get(key);
                } else {
                    hot.put(key, key * 10);
                }
            }
        });
        System.out.println("  size <= capacity: " + (hot.size() <= hot.capacity())
                + ", every segment policy matches its map: " + hot.consistent());

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }

    private static void runConcurrently(int threads, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                task.run();
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
