# Case Study: Design an LRU Cache in Pure Java

## Problem Statement

Design a thread-safe LRU (Least Recently Used) cache with:
- O(1) `get(key)` — return value, move to most-recently-used position
- O(1) `put(key, value)` — add entry; evict LRU entry if at capacity
- `maxSize` — configurable capacity
- Thread-safe for concurrent reads and writes
- Optional: **soft/weak references** for automatic eviction under memory pressure
- Optional: **TTL (time-to-live)** per entry
- Metrics: hit rate, eviction count

**Constraints**: Pure Java, production-ready.

---

## Key Java Concepts Used

| Concept | Module | Why Used |
|---------|--------|---------|
| `LinkedHashMap` (accessOrder=true) | [Collections Internals](../collections_internals/README.md) | O(1) LRU ordering built-in |
| `ReentrantReadWriteLock` | [Concurrency](../concurrency/README.md) | Multiple concurrent reads; exclusive writes |
| `ConcurrentHashMap` | [Collections Internals](../collections_internals/README.md) | Alternative: lock-free reads |
| `SoftReference` | [JVM Internals](../jvm_internals/README.md) | Auto-eviction under GC memory pressure |
| `WeakReference` | [JVM Internals](../jvm_internals/README.md) | Alternative: evicted at next GC |
| `AtomicLong` | [Concurrency](../concurrency/README.md) | Lock-free hit/miss counters |
| Immutable value objects | [Java Interview Patterns](../java_interview_patterns/README.md) | CacheEntry with TTL, value, and metadata |

---

## Architecture

```
Three implementation levels (each module presents one):

Level 1: Simple (single-threaded)
  LinkedHashMap(capacity, 0.75f, true) with removeEldestEntry override

Level 2: Thread-safe LRU
  LinkedHashMap + ReentrantReadWriteLock (multiple readers, exclusive writer)

Level 3: High-performance (ConcurrentHashMap + doubly-linked list)
  ConcurrentHashMap for O(1) lookup
  + doubly-linked list for LRU ordering (requires fine-grained locking)

Level 4: Production (TTL + SoftReference + metrics)
  Level 2 or 3 + TTL expiry + SoftReference wrapping values + LongAdder metrics
```

---

## Step-by-Step Design Decisions

### Decision 1: LinkedHashMap vs custom doubly-linked list

**LinkedHashMap(accessOrder=true)**: O(1) LRU ordering built in. `get()` moves entry to tail. `removeEldestEntry()` evicts head. 20-line implementation. Not thread-safe.

**Custom DoublyLinkedList + HashMap**: more control, can be made thread-safe at fine granularity (e.g., lock only the moved node and its neighbors). Standard LeetCode solution. More code.

**Production choice**: `LinkedHashMap` for non-concurrent or low-contention caches (most cases). Custom for very high concurrent write throughput.

### Decision 2: Thread-safety strategy

**Option A**: `Collections.synchronizedMap(linkedHashMap)` — every operation serialized, including reads. Simple but read throughput limited.

**Option B**: `ReentrantReadWriteLock` — multiple concurrent readers, exclusive writer. `get()` acquires read lock (if no LRU reordering) or write lock (if LRU reordering needed, because `get()` modifies the doubly-linked list).

**Critical insight**: With `accessOrder=true`, `LinkedHashMap.get()` *modifies* the list (moves to tail). So `get()` requires a **write lock** even though it's semantically a read. This means ReentrantReadWriteLock gives no advantage over a plain lock for standard LRU — you lose read concurrency.

**Option C**: `ConcurrentHashMap` for lookup + separate LRU list with fine-grained locks. More complex but allows concurrent reads.

**Production choice**: For most use cases, `synchronized` on a `LinkedHashMap` is correct and sufficient. For high read throughput, use a separate `ConcurrentHashMap` + LRU list.

### Decision 3: SoftReference for memory-sensitive caches

`SoftReference<V>`: GC will NOT collect it unless the JVM needs memory (approaching OOM). Ideal for caches — entries are evicted automatically when memory is tight, preventing `OutOfMemoryError`.

`WeakReference<V>`: GC collects at the next GC cycle regardless of memory pressure. Too aggressive for caches — entries evicted even when memory is plentiful.

**Choice**: `SoftReference` for the value in cache entries — allows auto-eviction under pressure.

---

## Level 1: Simple LRU Cache (Non-Thread-Safe)

```java
public class SimpleLruCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public SimpleLruCache(int maxSize) {
        super(maxSize, 0.75f, true);  // true = access-order (LRU)
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;  // evict LRU when over capacity
    }
}

// Usage:
SimpleLruCache<String, String> cache = new SimpleLruCache<>(100);
cache.put("key1", "val1");
cache.get("key1");  // moves to most-recently-used position
// After 100 entries: oldest-accessed entry is evicted on next put()
```

---

## Level 2: Thread-Safe LRU Cache

```java
public class ThreadSafeLruCache<K, V> {
    private final int maxSize;
    private final LinkedHashMap<K, V> cache;
    private final Object lock = new Object();  // simple intrinsic lock

    // Metrics
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    public ThreadSafeLruCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean evict = size() > maxSize;
                if (evict) evictions.incrementAndGet();
                return evict;
            }
        };
    }

    public V get(K key) {
        synchronized (lock) {
            V value = cache.get(key);  // moves to tail (LRU update)
            if (value != null) hits.incrementAndGet();
            else misses.incrementAndGet();
            return value;
        }
    }

    public void put(K key, V value) {
        synchronized (lock) {
            cache.put(key, value);
        }
    }

    public V getOrLoad(K key, Supplier<V> loader) {
        // Double-checked pattern: check without lock, then with lock, then load
        V value;
        synchronized (lock) {
            value = cache.get(key);
            if (value != null) {
                hits.incrementAndGet();
                return value;
            }
        }
        // Load without holding lock (could be slow: DB call, network)
        V loaded = loader.get();

        synchronized (lock) {
            // Check again in case another thread loaded the same key
            value = cache.get(key);
            if (value != null) {
                hits.incrementAndGet();
                return value;  // use the already-loaded value
            }
            cache.put(key, loaded);
            misses.incrementAndGet();
            return loaded;
        }
    }

    public void invalidate(K key) {
        synchronized (lock) { cache.remove(key); }
    }

    public void invalidateAll() {
        synchronized (lock) { cache.clear(); }
    }

    public double hitRate() {
        long h = hits.get(), m = misses.get();
        long total = h + m;
        return total == 0 ? 0.0 : (double) h / total;
    }

    public int size() {
        synchronized (lock) { return cache.size(); }
    }

    public CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), evictions.get());
    }

    public record CacheStats(long hits, long misses, long evictions) {
        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }
    }
}
```

---

## Level 3: Production Cache with TTL and SoftReference

```java
public class TtlLruCache<K, V> {
    private final int maxSize;
    private final long ttlNanos;
    private final Object lock = new Object();

    private final LinkedHashMap<K, CacheEntry<V>> cache;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    public TtlLruCache(int maxSize, Duration ttl) {
        this.maxSize = maxSize;
        this.ttlNanos = ttl.toNanos();
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    public Optional<V> get(K key) {
        synchronized (lock) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) { misses.incrementAndGet(); return Optional.empty(); }

            // Check TTL expiry
            if (entry.isExpired()) {
                cache.remove(key);
                misses.incrementAndGet();
                return Optional.empty();
            }

            // Check if soft-referenced value was GC'd
            V value = entry.getValue();
            if (value == null) {
                cache.remove(key);
                misses.incrementAndGet();
                return Optional.empty();
            }

            hits.incrementAndGet();
            return Optional.of(value);
        }
    }

    public void put(K key, V value) {
        synchronized (lock) {
            cache.put(key, new CacheEntry<>(value, System.nanoTime() + ttlNanos));
        }
    }

    // Purge expired entries (call periodically or on access)
    public void purgeExpired() {
        synchronized (lock) {
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
        }
    }

    private static class CacheEntry<V> {
        // SoftReference: GC may collect value when under memory pressure
        private final SoftReference<V> valueRef;
        private final long expiresAt; // System.nanoTime()

        CacheEntry(V value, long expiresAt) {
            this.valueRef = new SoftReference<>(value);
            this.expiresAt = expiresAt;
        }

        V getValue() { return valueRef.get(); }  // null if GC'd

        boolean isExpired() { return System.nanoTime() > expiresAt; }
    }
}
```

---

## Alternative: ConcurrentHashMap + LRU Doubly-Linked List (High Concurrency)

```java
// For very high read throughput: separate ConcurrentHashMap (lock-free reads)
// + doubly-linked list with per-node locking for LRU ordering

public class ConcurrentLruCache<K, V> {
    private final int maxSize;
    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final Node<K, V> head;  // MRU sentinel
    private final Node<K, V> tail;  // LRU sentinel
    private final ReentrantLock listLock = new ReentrantLock();

    public ConcurrentLruCache(int maxSize) {
        this.maxSize = maxSize;
        this.map = new ConcurrentHashMap<>(maxSize * 2);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) return null;

        // Move to front (MRU position) — requires list lock
        listLock.lock();
        try {
            moveToFront(node);
        } finally {
            listLock.unlock();
        }
        return node.value;
    }

    public void put(K key, V value) {
        listLock.lock();
        try {
            Node<K, V> existing = map.get(key);
            if (existing != null) {
                existing.value = value;
                moveToFront(existing);
            } else {
                Node<K, V> node = new Node<>(key, value);
                map.put(key, node);
                addToFront(node);
                if (map.size() > maxSize) {
                    Node<K, V> lru = removeLru();
                    if (lru != null) map.remove(lru.key);
                }
            }
        } finally {
            listLock.unlock();
        }
    }

    private void moveToFront(Node<K, V> node) {
        if (node.prev == head) return; // already at front
        // Remove from current position
        node.prev.next = node.next;
        node.next.prev = node.prev;
        // Add to front
        addToFront(node);
    }

    private void addToFront(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private Node<K, V> removeLru() {
        Node<K, V> lru = tail.prev;
        if (lru == head) return null;
        lru.prev.next = tail;
        tail.prev = lru.prev;
        return lru;
    }

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev, next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }
}
```

---

## Tradeoffs Compared

| Implementation | Read Throughput | Write Throughput | Complexity | Memory |
|----------------|----------------|-----------------|-----------|--------|
| LinkedHashMap + synchronized | Low (serialized) | Low (serialized) | Low | Low |
| LinkedHashMap + RWLock | Low (get needs write lock) | Low | Medium | Low |
| ConcurrentHashMap + doubly-linked | High (CAS on map) | Medium (list lock) | High | Higher |
| Guava LoadingCache | High | High | None (library) | Medium |

**Key insight**: For LRU caches, `get()` modifies the LRU order, so it's effectively a write operation — this negates the benefit of read-write locks unless you use a different synchronization strategy (e.g., probabilistic LRU that only reorders with some probability).

---

## Interview Questions for This Case Study

**Q: How does LinkedHashMap implement LRU in O(1)?**
`LinkedHashMap` maintains a doubly-linked list overlay on top of its hash table. In `accessOrder=true` mode, every `get()` moves the accessed entry to the tail of the list. The head of the list is always the least-recently-used (LRU) entry. Eviction is O(1): `removeEldestEntry()` is called after each `put()` — it returns true when `size() > maxSize`, and `LinkedHashMap` removes the head entry.

**Q: Why can't you use ReentrantReadWriteLock to improve LRU cache read concurrency?**
With `accessOrder=true`, `LinkedHashMap.get()` modifies the doubly-linked list (moves the node to tail). This is a structural modification — it requires exclusive access. Any concurrent `get()` on the same node would cause a data race on the list pointers. Therefore, every `get()` must hold the write lock, giving no benefit over a plain `synchronized` lock. Solutions: (1) probabilistic LRU (skip reordering sometimes); (2) segment the cache; (3) use a different data structure for the LRU order with per-node locking.

**Q: What is the difference between SoftReference and WeakReference for cache values?**
`SoftReference`: GC tries to collect it only when the JVM is under memory pressure (approaching heap limit). Ideal for caches — entries survive while memory is available. `WeakReference`: GC collects it at the *next* GC cycle regardless of memory pressure. Too aggressive for caches — a GC immediately after a put would evict all entries. For cache values: use `SoftReference`. For event bus listeners (you want them GC'd when subscriber is gone): use `WeakReference`.

**Q: What is the double-check pattern in `getOrLoad()` and why is it needed?**
Without the pattern: `getOrLoad()` holds the lock while loading (DB call), serializing all callers waiting for the same key. With double-check: (1) check without lock (fast path for already-cached values); (2) acquire lock; (3) check again (someone else may have loaded while we waited for the lock); (4) load if still absent; (5) release lock. This ensures loading only happens once per key even under concurrent access, while allowing fast lock-free reads for already-cached values.

**Q: How would you implement TTL without a background eviction thread?**
Lazy eviction: check TTL in `get()` — if expired, remove and return `null`. Optionally, add a `purgeExpired()` that iterates and removes all expired entries, called on every N operations or periodically from a background thread. This is a common pattern in embedded caches. The trade-off: expired entries consume memory until next access or purge, but avoids background thread complexity and scheduling jitter.

**Q: How would you make this cache distributed (across multiple JVM nodes)?**
Replace the in-memory `LinkedHashMap`/`ConcurrentHashMap` with calls to Redis (using LPOS + ZADD for LRU) or Memcached. Use Redis' `EXPIRE` for TTL. Consistency challenge: LRU ordering across nodes requires a distributed coordination mechanism — typically you accept that each node has independent LRU order (good enough). For truly distributed LRU: use a distributed cache like Hazelcast with near-cache, or Redis sorted set with score = access timestamp. This is the transition from in-process cache to distributed cache architecture.

---

## Failure Scenarios

| Component | Failure | Symptom | Recovery | Time-to-Recovery |
|-----------|---------|---------|----------|------------------|
| Cache process | Restart -> cold start | DB overwhelmed by reload traffic | Write-through persistence + warm-up | minutes |
| Loader (DB call) | Slow/failing | Threads pile up in `getOrLoad` | Per-key lock + load timeout + negative cache | seconds |
| Eviction | Working set > capacity | Thrashing, hit-rate collapse | Resize / frequency-based eviction | immediate after resize |
| Heap | Cache too big | GC pauses / OOM | Cap entries; off-heap or distributed | depends |

### Cache process restart -> cold start stampede

BROKEN — an empty cache lets every concurrent miss hit the DB for the same key:

```java
// BROKEN: cold cache + no per-key coordination => thundering herd
public Value get(Key k) {
    Value v = map.get(k);
    if (v == null) {
        v = db.load(k);     // 10,000 concurrent requests for hot key k all hit the DB
        map.put(k, v);
    }
    return v;
}
// After restart: 10M entries gone; full production traffic stampedes the DB.
```

FIX — write-through persistence plus a startup warm-up of the hottest keys, and single-flight loading:

```java
// FIX: warm the top-N keys on startup; single-flight per key avoids herd
void warmUp() {
    // load the 1,000 most-accessed keys (tracked in Redis sorted set by hit count)
    redis.zrevrange("cache:hot", 0, 999).forEach(k -> map.put(k, db.load(k)));
}

public Value getOrLoad(Key k) {
    Value v = map.get(k);
    if (v != null) return v;
    // single-flight: only ONE thread per key calls the DB; others wait on the future
    CompletableFuture<Value> f = inflight.computeIfAbsent(k, key ->
        CompletableFuture.supplyAsync(() -> db.load(key)));
    try {
        v = f.get();
        map.put(k, v);
        redisWriteThrough(k, v);   // persistent L2 survives restarts
        return v;
    } finally {
        inflight.remove(k, f);
    }
}
```

Recovery procedure: on restart, warm-up seeds the top-1000 keys before accepting full traffic (or behind a slow-ramp load balancer); the persistent L2 (Redis) absorbs misses so the primary DB is never hit by a cold-start stampede. Time-to-recovery is the warm-up duration (seconds to a couple of minutes), not the time to organically refill 10M entries under load.

---

## Capacity Planning Math

### Heap budget for 10M entries

```
Payload:        10,000,000 entries x 500 bytes avg value = 5,000,000,000 = ~5.0 GB
Per-entry overhead (LinkedHashMap.Entry on a 64-bit JVM, compressed oops):
   object header        16 bytes
   key/value/hash/next  references
   before/after ptrs    (LinkedHashMap adds two extra refs for the access-order list)
   ~24 bytes effective overhead per entry (conservative)
Overhead:       10,000,000 x 24 bytes = 240,000,000 = ~240 MB
Total live:     ~5.0 GB + ~0.24 GB = ~5.25 GB
```

### Heap sizing for G1GC

```
Rule of thumb: heap = cache_size x 1.5  (room for allocation, survivor space, headroom)
heap = 5.25 GB x 1.5 = ~8 GB  ->  -Xmx8g

G1GC at 8 GB:
   target pause ~200ms default; a 5GB+ long-lived cache is mostly "old gen" survivors,
   so keep allocation rate low (avoid per-get allocations) to limit mixed-GC pressure.
   Consider -XX:MaxGCPauseMillis tuning and ensuring the cache lives in old gen quickly.
```

If 8 GB heap is unacceptable, move the cache off-heap (e.g., a byte-buffer backed store) or distribute it — see Evolution.

---

## Benchmark Comparisons — Concurrency Strategy

JMH-style, mixed 80/20 get/put, Zipfian key distribution, 32 threads, Java 17:

| Implementation | Throughput | Notes |
|----------------|-----------|-------|
| Single-lock `LinkedHashMap` (baseline) | ~45k ops/sec | Every `get` needs the write lock (access-order reorders the list) |
| Segmented (16 segments) | ~320k ops/sec | Lock striping; ~7x baseline; per-segment LRU is approximate |
| Caffeine (W-TinyLFU) | ~650k ops/sec | Lock-free reads via ring buffers; frequency+recency eviction |

Why Caffeine's W-TinyLFU beats pure LRU on real workloads: production access follows a Zipf distribution — a few keys are extremely hot, a long tail is rarely touched. Pure LRU admits any recently-touched key and can evict a frequently-used key during a burst of one-time scans (cache pollution from a sequential scan). W-TinyLFU keeps a compact frequency sketch (Count-Min) and only admits a new key if it is estimated to be accessed more than the victim it would evict, giving scan resistance and a higher hit rate for the same capacity. Caffeine also records reads in per-thread ring buffers and replays them asynchronously, so `get` is effectively lock-free — eliminating the LRU reorder-on-read bottleneck that caps the single-lock design.

---

## Production War Stories

### War story 1 — LRU eviction thrashing (working set larger than cache)

Symptom: hit rate below 5% despite a "large" cache; DB read load near 100%; cache CPU high from constant eviction churn.

BROKEN — cache sized far below the working set, so every access evicts something still needed:

```java
// BROKEN: maxSize 10,000 but working set is ~200,000 distinct hot keys
LruCache<Key, Value> cache = new LruCache<>(10_000);
// Each access misses, loads, and evicts a key that will be needed momentarily.
// Pure LRU + a scan over 200k keys => 100% miss after one pass (thrash).
```

FIX — measure the working set, size to it, and switch to frequency-aware eviction:

```java
// FIX: size to working set; use Caffeine's W-TinyLFU for scan resistance
Cache<Key, Value> cache = Caffeine.newBuilder()
    .maximumSize(250_000)              // >= measured working set
    .recordStats()                     // monitor hitRate() continuously
    .build();
// W-TinyLFU refuses to evict a hot key just because a one-off scan touched a cold key.
```

Lesson: a sub-5% hit rate is almost never "the cache is broken" — it is the working set exceeding capacity, or pure LRU being polluted by scans. Profile access patterns first; resize and choose frequency-based eviction second.

### War story 2 — equals()/hashCode() bug in the cache key

Symptom: cache hit rate ~0% for a specific key type; DB hammered; two "identical" lookups never coalesced.

BROKEN — a value-object key overrides `equals` but not `hashCode` (or neither), so logically equal keys land in different buckets:

```java
// BROKEN: equals overridden, hashCode NOT -> violates the equals/hashCode contract
final class CacheKey {
    final String tenant; final long id;
    CacheKey(String t, long id) { this.tenant = t; this.id = id; }
    @Override public boolean equals(Object o) {
        return o instanceof CacheKey k && k.tenant.equals(tenant) && k.id == id;
    }
    // hashCode() inherited from Object -> identity-based -> equal keys hash differently
}
// map.put(new CacheKey("acme", 7), v); map.get(new CacheKey("acme", 7)) -> MISS
```

FIX — always override both, consistently:

```java
// FIX (Java 16+ record gives correct equals/hashCode for free)
record CacheKey(String tenant, long id) {}
// records generate equals() AND hashCode() from all components -> equal keys hash equally.
// Pre-16: override hashCode() = Objects.hash(tenant, id) alongside equals().
```

Lesson: the `equals`/`hashCode` contract is load-bearing for every hash-based cache. A missing or inconsistent `hashCode` silently disables the cache. Prefer records or `Objects.hash`/`Objects.equals` and never override one without the other.

---

## Evolution / Scalability at 10x Load

At 100M entries / 50GB working set, a single-JVM heap cache is no longer viable (GC and memory limits). The architecture tiers:

```
   App instance
   +-----------------------------+
   |  L1: local Caffeine cache   |  <- near-cache, microsecond hits, small (hot subset)
   +-----------------------------+
            | miss
            v
   +-----------------------------+
   |  L2: Redis Cluster          |  <- sharded by key, ~0.5ms, large capacity
   |  (slots 0-16383, N shards)  |     invalidation via pub/sub to L1
   +-----------------------------+
            | miss
            v
   +-----------------------------+
   |  System of record (DB)      |
   +-----------------------------+
```

1. Distributed cache (Redis Cluster) — shard the keyspace across nodes (16384 hash slots) so capacity scales horizontally; per-node LRU/LFU eviction. Accept independent eviction order per shard.
2. L1 local + L2 remote (near-cache) — keep a small Caffeine L1 in each app for microsecond hits on the hottest keys, backed by Redis L2. Invalidate L1 via Redis pub/sub or short L1 TTLs to bound staleness.
3. Near-cache consistency — the hard problem is L1 invalidation. Options: short L1 TTL (bounded staleness, simple), or pub/sub invalidation messages (fresher, more moving parts). Choose based on tolerance for stale reads.

Technical debt to track: the in-process cache has no cross-node invalidation and its single-lock LRU caps concurrency. Before scaling out, instrument `hitRate()`/`evictionCount()` and migrate to Caffeine for L1; the hand-rolled `LinkedHashMap` cache is a teaching/embedded artifact, not a fleet-scale tier.

---

## Additional Interview Questions

**Q: Estimate the heap for a 10M-entry LRU cache with 500-byte values.**
Payload is `10M x 500 bytes = 5.0 GB`; per-entry JVM overhead (object header plus the extra before/after references LinkedHashMap adds for access order) is roughly 24 bytes, so `10M x 24 = ~240 MB` more, totaling about 5.25 GB live. Apply a 1.5x factor for allocation and GC headroom, giving `-Xmx8g` on G1GC. If 8 GB is unacceptable, go off-heap or distributed.

**Q: Why does W-TinyLFU outperform pure LRU on production traffic?**
Real access is Zipfian — a few hot keys, a long cold tail — and pure LRU is polluted by sequential scans that evict hot keys in favor of one-time touches. W-TinyLFU keeps a compact Count-Min frequency sketch and only admits a new key if it is likely hotter than the victim, giving scan resistance and a higher hit rate for the same capacity. It also makes reads effectively lock-free via per-thread ring buffers, removing the LRU reorder-on-read bottleneck.

**Q: Your cache hit rate is under 5%. What's wrong and how do you fix it?**
Almost always the working set exceeds the cache capacity (so every access evicts a key you still need) or pure LRU is being thrashed by scans. Measure the distinct-hot-key count, size the cache to at least the working set, and switch to frequency-aware eviction (Caffeine) for scan resistance. It is rarely a code bug in the cache itself — though a broken key `hashCode` can also produce near-zero hits.

**Q: How do you prevent a cold-start stampede after a cache restart?**
Combine a persistent L2 (write-through to Redis) so misses don't reach the primary DB, single-flight loading (one DB call per key while others await the same future), and a startup warm-up that seeds the top-N most-accessed keys before taking full traffic. Optionally ramp traffic via the load balancer while the cache fills. This caps DB load to one load per key rather than one per concurrent request.

**Q: What's the consistency challenge with an L1/L2 near-cache, and how do you bound it?**
The L1 in each app can serve stale data after the underlying value changes, because writes go to L2/DB but L1 copies linger. Bound staleness with a short L1 TTL (simple, accepts a fixed staleness window) or invalidate L1 entries via Redis pub/sub on writes (fresher, but adds messaging and ordering concerns). Pick based on how much stale-read tolerance the use case allows.
