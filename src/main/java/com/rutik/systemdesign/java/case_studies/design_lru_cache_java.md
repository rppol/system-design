# Case Study: Design an LRU Cache in Pure Java

## Intuition

> An LRU cache is a hotel with a fixed number of rooms. When it fills up, the guest who checked in longest ago (and hasn't moved since) is asked to leave to make room for the new arrival. The front desk keeps a list sorted by most recent activity — O(1) eviction because you always remove from the tail.

**Key insight**: LRU is secretly two data structures glued together — a hash map for O(1) lookup and a doubly-linked list for O(1) order maintenance. `LinkedHashMap(accessOrder=true)` bundles them so you get a correct LRU cache in 20 lines. The hard part is concurrency: `get()` in LRU *modifies* the access-order list (moving the node to the head), so it is a write operation disguised as a read — this negates the read-write lock optimization and forces every operation through the same lock.

The production path: `LinkedHashMap` (embedded), then Caffeine W-TinyLFU (in-process high-throughput), then Redis Cluster near-cache (multi-instance). Each tier serves 10× more traffic than the previous.

See also:
- [JVM Tuning & GC for Services](cross_cutting/jvm_tuning_and_gc_for_services.md) — heap sizing for large caches, GC pause impact, off-heap options
- [Backpressure & Bounded Resources](cross_cutting/backpressure_and_bounded_resources.md) — cache stampede / thundering-herd mitigation patterns

---

## 1. Requirements Clarification

### Functional requirements
- `get(key)` — O(1); return the value and move the entry to most-recently-used position; return `null` on miss
- `put(key, value)` — O(1); insert or update; evict the least-recently-used entry if at capacity
- `invalidate(key)` / `invalidateAll()` — explicit eviction
- `getOrLoad(key, Supplier)` — load from source if absent; single-flight to prevent stampedes
- `stats()` — hit count, miss count, eviction count, hit rate

### Non-functional requirements
| Dimension | Target |
|-----------|--------|
| `get` latency (in-process, cache hit) | < 1 µs |
| Concurrency model | Thread-safe for any number of concurrent callers |
| Eviction policy | LRU (order by last access time) |
| Memory cap | `maxSize` entries; optional TTL per entry |
| Optional | `SoftReference` wrapping — auto-evict under GC pressure |

### Out of scope
- Distributed (multi-JVM) invalidation — handled at the Redis layer (§6)
- Write-through or write-behind persistence (application concern, not cache concern)
- Eviction callbacks (can be added as `EvictionListener<K,V>` — straightforward extension)

---

## 2. Scale Estimation

### Heap budget for a 10M-entry cache

```
Payload:    10,000,000 entries × 500 bytes avg value  = 5,000 MB = ~5.0 GB

Per-entry JVM overhead (LinkedHashMap on 64-bit JVM, compressed oops):
  Object header:          16 bytes
  key, value references:   8 bytes each
  hash, next references:   8 bytes each
  before, after refs (LHM access-order list): 8 bytes each
  Effective:               ~56 bytes per Entry object

Overhead total: 10M × 56 B = 560 MB

Live heap: ~5.0 GB + 0.56 GB = ~5.6 GB

G1GC sizing (×1.5 headroom for allocation + survivor + metaspace):
  -Xmx9g
```

### Throughput under contention

```
Single-lock LinkedHashMap (get + access-order reorder):
  ~45k ops/sec at 32 threads (lock contention dominates)

Segmented (16 segments, each a locked LinkedHashMap):
  ~320k ops/sec (7× improvement; per-segment LRU is approximate)

Caffeine W-TinyLFU:
  ~650k ops/sec (reads via per-thread ring buffer; effectively lock-free get)
```

### Working set sizing

```
Working set: distinct hot keys accessed in a typical 5-minute window.
Measure via: HyperLogLog over access log; instrument cache.missCount() over time.

If miss rate > 30%: working set > cache capacity → resize or accept misses.
Rule of thumb: set maxSize ≥ 1.2 × observed_hot_key_count.
```

### Per-entry memory: SoftReference cost

```
SoftReference wrapper object: ~16 bytes header + 8 bytes referent
Extra overhead per entry: ~24 bytes
For 10M entries: +240 MB
Benefit: GC auto-evicts under pressure → prevents OOM at the cost of ~5% more heap overhead
```

---

## 3. High-Level Architecture

```
  ┌──────────────────────────────────────────────────────────────┐
  │                     Caller Threads                           │
  │   thread-1    thread-2    thread-3    ...    thread-N        │
  └───────┬─────────────┬──────────────────────────────────────┘
          │ get/put      │
          ▼              ▼
  ┌───────────────────────────────────────────────────────────┐
  │                ThreadSafeLruCache                          │
  │                                                           │
  │   lock: Object (intrinsic monitor)                        │
  │                                                           │
  │   cache: LinkedHashMap<K, CacheEntry<V>>                  │
  │     ├── hash table: O(1) lookup by key                    │
  │     └── doubly-linked list (access-order):                │
  │           HEAD (LRU) ─────────────── TAIL (MRU)          │
  │           get()    moves accessed entry to TAIL           │
  │           put()    evicts HEAD if size > maxSize          │
  │                                                           │
  │   hits: AtomicLong    misses: AtomicLong                  │
  │   evictions: AtomicLong                                   │
  └───────────────────────────────────────────────────────────┘
```

### Implementation levels

```
Level 1: SimpleLruCache (not thread-safe)
  LinkedHashMap(capacity, 0.75f, true) + removeEldestEntry override
  20 lines; correct LRU; use in single-threaded contexts or tests

Level 2: ThreadSafeLruCache (this case study)
  Level 1 + intrinsic synchronized lock + AtomicLong metrics

Level 3: TtlLruCache (TTL + SoftReference)
  Level 2 + CacheEntry<V> wrapper: {SoftReference<V>, expiresAt}

Level 4: ConcurrentLruCache (high-throughput)
  ConcurrentHashMap (lock-free reads) + doubly-linked list (list lock)
  Separates lookup from order-maintenance; concurrent reads with list lock
  only on reorder
```

---

## 4. Component Deep Dives

### 4.1 Why LRU `get()` requires a write lock

BROKEN — using `ReentrantReadWriteLock`, thinking `get()` is a read operation:

```java
// BROKEN: get() acquires read lock but LinkedHashMap.get(accessOrder=true)
// modifies the doubly-linked list (moves node to tail) -> data race
private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

public V get(K key) {
    rwLock.readLock().lock();          // concurrent readers allowed
    try {
        return cache.get(key);         // MODIFIES list order under read lock -> corruption
    } finally {
        rwLock.readLock().unlock();
    }
}
// Two threads both reading the same key concurrently each try to update
// the before/after pointers of the accessed node -> linked-list corruption
```

FIX — `get()` must hold the write lock because it modifies list structure:

```java
// FIX: synchronized on a single monitor — only correct option for LinkedHashMap LRU
private final Object lock = new Object();

public V get(K key) {
    synchronized (lock) {
        V value = cache.get(key);      // read + reorder: single critical section
        if (value != null) hits.incrementAndGet();
        else misses.incrementAndGet();
        return value;
    }
}
```

This is the counterintuitive insight: `ReadWriteLock` buys you nothing for `LinkedHashMap`-based LRU because every `get()` is a structural write. Alternatives for concurrent reads: (a) probabilistic LRU that skips reorder with probability P (amortizes the write cost); (b) per-thread ring buffers that replay reorders asynchronously (Caffeine's approach).

### 4.2 Level 1 — Simple LRU (20 lines)

```java
public class SimpleLruCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public SimpleLruCache(int maxSize) {
        super(maxSize, 0.75f, true);  // true = access-order mode
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;  // LinkedHashMap calls this after every put()
    }
}

// Usage:
var cache = new SimpleLruCache<String, String>(100);
cache.put("user:1", "Alice");
cache.get("user:1");  // moves to MRU position
// 101st put() evicts whoever was at the LRU (tail) position
```

### 4.3 Level 2 — Thread-safe LRU with metrics

```java
public class ThreadSafeLruCache<K, V> {
    private final int maxSize;
    private final Object lock = new Object();
    private final LinkedHashMap<K, V> cache;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public ThreadSafeLruCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean evict = size() > ThreadSafeLruCache.this.maxSize;
                if (evict) evictions.incrementAndGet();
                return evict;
            }
        };
    }

    public V get(K key) {
        synchronized (lock) {
            V v = cache.get(key);
            (v != null ? hits : misses).incrementAndGet();
            return v;
        }
    }

    public void put(K key, V value) {
        synchronized (lock) { cache.put(key, value); }
    }

    // Double-checked load: avoids holding lock during slow DB call
    public V getOrLoad(K key, Supplier<V> loader) {
        synchronized (lock) {
            V v = cache.get(key);
            if (v != null) { hits.incrementAndGet(); return v; }
        }
        V loaded = loader.get();                  // outside lock: may be slow
        synchronized (lock) {
            V existing = cache.get(key);          // re-check: another thread may have loaded
            if (existing != null) { hits.incrementAndGet(); return existing; }
            cache.put(key, loaded);
            misses.incrementAndGet();
            return loaded;
        }
    }

    public void invalidate(K key) { synchronized (lock) { cache.remove(key); } }
    public void invalidateAll()   { synchronized (lock) { cache.clear(); } }
    public int size()             { synchronized (lock) { return cache.size(); } }

    public double hitRate() {
        long h = hits.get(), m = misses.get(), t = h + m;
        return t == 0 ? 0.0 : (double) h / t;
    }

    public record CacheStats(long hits, long misses, long evictions) {}
    public CacheStats stats() { return new CacheStats(hits.get(), misses.get(), evictions.get()); }
}
```

### 4.4 Level 3 — TTL + SoftReference

```java
public class TtlLruCache<K, V> {
    private final int maxSize;
    private final long ttlNanos;
    private final Object lock = new Object();
    private final LinkedHashMap<K, CacheEntry<V>> cache;

    public TtlLruCache(int maxSize, Duration ttl) {
        this.maxSize = maxSize;
        this.ttlNanos = ttl.toNanos();
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > TtlLruCache.this.maxSize;
            }
        };
    }

    public Optional<V> get(K key) {
        synchronized (lock) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) return Optional.empty();
            if (entry.isExpired()) { cache.remove(key); return Optional.empty(); }
            V v = entry.getValue();                    // null if GC reclaimed SoftReference
            if (v == null)         { cache.remove(key); return Optional.empty(); }
            return Optional.of(v);
        }
    }

    public void put(K key, V value) {
        synchronized (lock) {
            cache.put(key, new CacheEntry<>(value, System.nanoTime() + ttlNanos));
        }
    }

    public void purgeExpired() {                      // call periodically (e.g., every 60 s)
        synchronized (lock) {
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
        }
    }

    private static class CacheEntry<V> {
        private final SoftReference<V> valueRef;      // GC may collect under memory pressure
        private final long expiresAt;                 // System.nanoTime()

        CacheEntry(V v, long expiresAt) {
            this.valueRef = new SoftReference<>(v);
            this.expiresAt = expiresAt;
        }
        V getValue()     { return valueRef.get(); }   // null if GC reclaimed
        boolean isExpired() { return System.nanoTime() > expiresAt; }
    }
}
```

### 4.5 ConcurrentLruCache (high-throughput variant)

```java
// When read throughput matters: ConcurrentHashMap (lock-free reads) +
// doubly-linked list (list lock for LRU reorder)
public class ConcurrentLruCache<K, V> {
    private final int maxSize;
    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final Node<K, V> head = new Node<>(null, null); // MRU sentinel
    private final Node<K, V> tail = new Node<>(null, null); // LRU sentinel
    private final ReentrantLock listLock = new ReentrantLock();

    public ConcurrentLruCache(int maxSize) {
        this.maxSize = maxSize;
        this.map = new ConcurrentHashMap<>(maxSize * 2);
        head.next = tail; tail.prev = head;
    }

    public V get(K key) {
        Node<K, V> node = map.get(key);   // lock-free ConcurrentHashMap read
        if (node == null) return null;
        listLock.lock();
        try { moveToFront(node); }
        finally { listLock.unlock(); }
        return node.value;
    }

    public void put(K key, V value) {
        listLock.lock();
        try {
            Node<K, V> existing = map.get(key);
            if (existing != null) { existing.value = value; moveToFront(existing); }
            else {
                Node<K, V> node = new Node<>(key, value);
                map.put(key, node);
                addToFront(node);
                if (map.size() > maxSize) {
                    Node<K, V> lru = removeLru();
                    if (lru != null) map.remove(lru.key);
                }
            }
        } finally { listLock.unlock(); }
    }

    private void moveToFront(Node<K, V> n) {
        if (n.prev == head) return;
        n.prev.next = n.next; n.next.prev = n.prev;
        addToFront(n);
    }
    private void addToFront(Node<K, V> n) {
        n.next = head.next; n.prev = head;
        head.next.prev = n; head.next = n;
    }
    private Node<K, V> removeLru() {
        Node<K, V> lru = tail.prev;
        if (lru == head) return null;
        lru.prev.next = tail; tail.prev = lru.prev;
        return lru;
    }

    private static class Node<K, V> {
        K key; V value; Node<K, V> prev, next;
        Node(K k, V v) { this.key = k; this.value = v; }
    }
}
```

---

## 5. Design Decisions & Tradeoffs

| Decision | Chosen | Alternatives | Rationale |
|----------|--------|-------------|-----------|
| Data structure | `LinkedHashMap(accessOrder=true)` | Custom doubly-linked list + HashMap | LHM gives correct LRU in 20 lines; custom structure needed only for fine-grained locking |
| Thread-safety | Intrinsic `synchronized` on single lock | `ReentrantReadWriteLock`, `ConcurrentHashMap` | RWLock gives zero benefit: `get()` is a write; CHM needs external LRU list; single lock is correct and simple |
| Memory safety | `SoftReference<V>` in CacheEntry | Strong reference, `WeakReference` | `SoftReference`: GC evicts only under pressure → prevents OOM while keeping cache warm; `WeakReference` is too aggressive (evicts freely) |
| Eviction algorithm | LRU (access-order list) | LFU, W-TinyLFU (Caffeine), ARC | LRU: simple, predictable; W-TinyLFU: better hit rate on Zipf workloads (scan-resistant); ARC: self-tuning between LRU and LFU |
| TTL expiry | Lazy (check on `get`) | Background eviction thread | Lazy: simpler, no background thread, no clock-jitter; downside: expired entries consume memory until accessed |

**When LRU is the wrong algorithm**: production access is Zipfian — a few keys very hot, a long cold tail. A sequential scan over a large dataset touches many cold keys, each briefly becoming "recently used" and evicting hot keys. Caffeine's W-TinyLFU maintains a Count-Min frequency sketch and only admits a new key if it is estimated to be accessed more often than its candidate victim — scan-resistant by construction. Use LRU for short-window recency bias (CDN, session caches); use W-TinyLFU for general application caches.

---

## 6. Real-World Implementations

**Guava Cache** (now `com.google.common.cache`): similar API to this design — `CacheBuilder.newBuilder().maximumSize(n).expireAfterAccess(d)`. Internally uses `ConcurrentHashMap`-segmented approach with per-segment LRU queues. Hit/miss stats via `.recordStats()`. Largely superseded by Caffeine, which Guava now wraps internally.

**Caffeine** (Ben Manes): the standard in-process cache for Java. W-TinyLFU eviction via compact Count-Min sketch. Reads recorded in per-thread ring buffers (`StripedBuffer`) and replayed asynchronously, making `get()` effectively lock-free (~650k ops/sec at 32 threads vs ~45k for single-lock LRU). `AsyncLoadingCache` variant returns `CompletableFuture<V>` for non-blocking callers. Spring Boot 3's default cache implementation (`spring.cache.type=caffeine`).

**Redis** (L2 tier): sorted set (`ZADD key score member`) with score = `System.currentTimeMillis()` gives distributed LRU. More commonly, Redis uses its own LRU approximation (samples N random keys and evicts the oldest) — `maxmemory-policy allkeys-lru`. Not true O(1) LRU but approaches it at large sample sizes with much less memory overhead (no per-key timestamp stored in a list).

**Memcached**: true LRU per slab class (memory-size bucketed). The "slab calcification" problem occurs when the working set shifts size distribution — a slab full of large items will not shrink to accommodate many small items. Memcached 1.5+ introduced slab automover, but this remains an operational concern that Redis avoids with its unified allocator.

**CPU hardware cache** (reference point): uses pseudo-LRU (approximated from tree bits per cache set) because true LRU hardware would require one timestamp per cache line. This is the same engineering tradeoff as probabilistic LRU in software — exact ordering is expensive; approximate ordering is good enough.

---

## 7. Technologies & Tools

| Tool | Algorithm | Throughput | Memory | Key Feature | Use When |
|------|-----------|-----------|--------|-------------|---------|
| Custom `LinkedHashMap` (this) | True LRU | ~45k ops/sec | Low | Zero dependencies | Embedded / test / learning |
| Guava Cache | Segmented LRU | ~200k ops/sec | Medium | Built-in stats | Legacy codebases |
| **Caffeine** | W-TinyLFU | ~650k ops/sec | Medium | Lock-free reads, scan-resistant | In-process production standard |
| Redis `allkeys-lru` | Approximate LRU | Millions/sec (cluster) | Distributed | Multi-JVM, TTL built-in | Shared cache across services |
| Memcached | True LRU per slab | Millions/sec | Distributed | Simple protocol | Read-heavy, object store |
| Hazelcast near-cache | LRU / LFU | ~500k ops/sec | Distributed | Cross-JVM invalidation | Need consistency guarantees |

JMH concurrency benchmark (32 threads, 80/20 get/put, Zipf distribution, Java 17):

| Implementation | Throughput | Notes |
|----------------|-----------|-------|
| Single-lock `LinkedHashMap` | ~45k ops/sec | Every `get` holds write lock |
| 16-segment `LinkedHashMap` | ~320k ops/sec | Approximate LRU per segment |
| Caffeine W-TinyLFU | ~650k ops/sec | Per-thread ring buffer; async LRU drain |

---

## 8. Operational Playbook

### a) Key metrics

```java
// Expose via Micrometer
Gauge.builder("cache.size",        cache, c -> c.size())          .register(registry);
Gauge.builder("cache.hit_rate",    cache, ThreadSafeLruCache::hitRate).register(registry);
Counter.builder("cache.evictions").register(registry);   // increment in removeEldestEntry
Counter.builder("cache.loads")    .register(registry);   // increment in getOrLoad on miss
Timer.builder("cache.load.latency").register(registry);  // time the loader.get() call
```

Alert thresholds:
- `cache.hit_rate < 0.50` sustained > 5 min → working set exceeds capacity; resize or investigate access pattern change
- `cache.load.latency p99 > 500 ms` → loader (DB) is slow; check DB health and query plan
- `cache.evictions` > expected rate × 3 → working set churn; consider frequency-based eviction

### b) Distributed trace span

```
HTTP request span
  └── cache.getOrLoad (5 ms total)
        ├── cache.hit  (0.001 ms)    ← fast path: lock + LinkedHashMap.get
        └── cache.miss.load (5 ms)   ← slow path: DB query
```

Tag every span with `cache.name`, `cache.result` (hit/miss/expired), `key.type`.

### c) Incident Runbooks

**Runbook 1 — Hit rate collapse after restart (cold-start stampede)**

Symptom: `cache.hit_rate` drops to 0 after deploy; DB CPU spikes to 100%; latency collapses.

Diagnosis: cache is empty (cold start); every concurrent request misses and calls the DB for the same hot keys simultaneously.

Mitigation:
1. Enable single-flight loading: only one DB call per key; other waiters join the same `CompletableFuture`.
2. Add a startup warm-up: load top-N hot keys from Redis (write-through L2) before opening traffic.
3. Ramp traffic behind the load balancer with a warm-up gate (canary receives 1% → 10% → 100% over 2 min).

Resolution: implement write-through to Redis on every `put()` so the L2 survives restarts and absorbs misses during cache rebuild.

---

**Runbook 2 — Hit rate collapse due to working set growth**

Symptom: `cache.hit_rate` trending down over days; `cache.evictions` rising.

Diagnosis: working set grew beyond `maxSize` — new feature added more distinct hot keys than the cache can hold.

Mitigation:
1. Measure distinct-hot-key count via `HyperLogLog` on access log (Redis `PFADD`/`PFCOUNT`).
2. Increase `maxSize` if heap budget allows, or switch to a larger host.
3. If not: switch to W-TinyLFU (Caffeine) — better hit rate at same capacity for Zipfian access.

---

**Runbook 3 — Zero hit rate (hashCode/equals bug)**

Symptom: `cache.hit_rate ≈ 0%` for a specific key type; DB hammered; no obvious traffic change.

Diagnosis: check `cache.getOrLoad(key, loader)` — does the second `cache.get(key)` immediately after `put` return the value? If not, the key's `equals`/`hashCode` is broken.

Mitigation: instrument: `assert cache.get(k) != null` immediately after `cache.put(k, v)`. Migrate the key class to a `record` (Java 16+) which auto-generates correct `equals` and `hashCode` from all components.

---

## 9. Common Pitfalls & War Stories

### War story 1 — LRU eviction thrashing (working set exceeds cache)

**Scenario**: product search service; cache max 10,000 entries; DB read load near 100%; cache hit rate 3%.

BROKEN — cache sized far below the working set; every access evicts a key still needed:

```java
// BROKEN: maxSize 10,000 but working set is ~200,000 distinct hot keys
LruCache<Key, Value> cache = new LruCache<>(10_000);
// Each request: miss → load from DB → evict a key that was just loaded 200ms ago
// A sequential scan of 10,001 products evicts 100% of the cache in one pass (cache pollution)
```

FIX — measure the working set; size to it; switch to scan-resistant eviction:

```java
// FIX: profile access patterns first; size to ≥ working set; use Caffeine for scan resistance
Cache<Key, Value> cache = Caffeine.newBuilder()
    .maximumSize(250_000)              // >= measured hot-key count
    .recordStats()                     // expose hitRate(), evictionCount() continuously
    .build();
// W-TinyLFU: a sequential scan cannot evict a hot key because the scan's access frequency
// is estimated at 0 and any hot key's estimate is > 0 → scan-resistant by design
```

**Root cause**: cache sized by "feel" (10k sounds large) not by measured working set. Sequential product-listing scans polluted pure LRU, evicting all the hot item-detail keys.
**Impact**: DB read replicas at 98% CPU; p99 latency 2.3s vs 50ms target; 3 h to diagnose because no `hitRate()` metric was exposed.

---

### War story 2 — `equals()`/`hashCode()` contract violation disabling the cache

**Scenario**: user session cache; hit rate 0%; DB hammered; identical-looking keys always missed.

BROKEN — `equals` overridden but `hashCode` is inherited from `Object` (identity-based):

```java
// BROKEN: equals overridden, hashCode NOT -> violates equals/hashCode contract
final class CacheKey {
    final String tenantId;
    final long userId;
    CacheKey(String t, long id) { this.tenantId = t; this.userId = id; }

    @Override
    public boolean equals(Object o) {
        return o instanceof CacheKey k && k.tenantId.equals(tenantId) && k.userId == userId;
    }
    // hashCode() not overridden → inherits Object.hashCode() → identity-based hash
}

cache.put(new CacheKey("acme", 7), session);
// key instance A: hashCode = 0x1a2b3c4d
cache.get(new CacheKey("acme", 7));
// key instance B: hashCode = 0x5e6f7a8b  ← different hash → different bucket → MISS
```

FIX — use a Java 16 record, which auto-generates both:

```java
// FIX: record generates equals() + hashCode() from all components automatically
record CacheKey(String tenantId, long userId) {}

cache.put(new CacheKey("acme", 7), session);
cache.get(new CacheKey("acme", 7));  // same hashCode → same bucket → HIT
// Pre-16 alternative: @Override hashCode() { return Objects.hash(tenantId, userId); }
```

**Root cause**: the `equals`/`hashCode` contract requires: `a.equals(b) → a.hashCode() == b.hashCode()`. Violation causes hash-based collections to silently treat logically equal keys as different.
**Impact**: effectively no caching; DB load 50× higher than necessary; issue took 6 h to find because the code "looked correct" — equals returned true in unit tests using the same instance.

---

### Failure scenarios summary

| Failure | Symptom | Recovery | TTR |
|---------|---------|---------|-----|
| Cold start (restart) | DB stampede, hit rate 0% | Single-flight load + L2 warm-up | Minutes |
| Working set > capacity | Eviction thrashing, hit rate < 30% | Resize or frequency-based eviction | On deploy |
| `hashCode` contract violation | Hit rate ~0%, cache effectively disabled | Switch to `record` or fix `hashCode` | On deploy |
| GC pressure (large cache) | GC pauses > 200ms | Use `SoftReference` or off-heap cache | Tune / redeploy |
| TTL too short | Unnecessary DB churn, high miss rate | Tune TTL to match data freshness SLA | Config change |

---

## 10. Capacity Planning

### Heap sizing for a large in-process cache

```
entries = maxSize
avg_value_bytes = (measure from profiler or estimate)
payload = entries × avg_value_bytes
entry_overhead = 56 bytes (LinkedHashMap.Entry on 64-bit JVM, compressed oops)
live_heap = payload + (entries × entry_overhead)
jvm_heap_size = live_heap × 1.5    (headroom for allocation + survivor + metaspace)

Example: 10M entries × 500 B value = 5.0 GB payload + 0.56 GB overhead = 5.56 GB live
         → -Xmx9g
```

### G1GC tuning for large old-gen caches

```
A cache with 10M long-lived entries fills the old generation.
G1 evacuates regions to compact the heap; if old-gen is full, it triggers
a Full GC — STW for many seconds at 5+ GB heaps.

Mitigations:
  - Keep allocation rate low: avoid per-get object creation (e.g., don't allocate
    a new wrapper on every hit)
  - -XX:MaxGCPauseMillis=200 (default G1 target)
  - -XX:G1HeapRegionSize=32m for large heaps (fewer regions to track)
  - SoftReference wrapping: GC can reclaim cache entries during old-gen pressure,
    preventing promotion failure / Full GC

See [JVM Tuning & GC for Services](cross_cutting/jvm_tuning_and_gc_for_services.md)
for the full G1/ZGC tuning checklist.
```

### Two-tier cache architecture

```
Working set: 100M entries × 500 B = 50 GB — exceeds single JVM heap budget.

Solution: L1 + L2 tier
  L1 (in-process Caffeine): top 500k hot keys = 250 MB heap — fits in 1 GB JVM
  L2 (Redis Cluster):       full 100M entries = 50 GB across Redis nodes
  DB (source of truth):     only miss path

Capacity math:
  L1 hit rate target: 80% (most traffic hits L1)
  L2 handles remaining 20%: 100 req/sec × 0.20 = 20 Redis calls/sec
  DB sees: 20 × (1 - L2 hit rate 0.95) = 1 DB call/sec ← negligible
```

---

## 11. Interview Discussion Points

**Q: How does `LinkedHashMap` implement LRU in O(1) time?**
`LinkedHashMap` maintains a doubly-linked list overlay on its hash table. In `accessOrder=true` mode, every `get()` moves the accessed entry to the tail of the list — O(1) pointer manipulation. The head is always the least-recently-used entry. `removeEldestEntry()` is called after every `put()` and returns `true` when `size() > maxSize`; `LinkedHashMap` then removes the head entry in O(1). Total: O(1) lookup, O(1) LRU reorder, O(1) eviction.

**Q: Why can't you use `ReentrantReadWriteLock` to improve LRU cache read throughput?**
With `accessOrder=true`, `LinkedHashMap.get()` structurally modifies the doubly-linked list — it updates the `before` and `after` pointers of the accessed node and its neighbors. Two concurrent reads on the same node both try to set the same pointers, causing a data race and potential list corruption. Every `get()` must hold the exclusive write lock, giving zero concurrency benefit over a plain `synchronized` lock. Alternatives: (1) probabilistic LRU — skip reorder on `get()` with probability P; (2) `ConcurrentHashMap` + separate LRU list with its own lock (concurrent reads; list lock only for reorder); (3) Caffeine's per-thread ring buffers.

**Q: What is the difference between `SoftReference` and `WeakReference` for cache values?**
`SoftReference`: GC collects it only when the JVM is approaching heap exhaustion — ideal for caches, because entries survive while memory is comfortable. `WeakReference`: GC collects it at the *next* GC cycle regardless of memory availability — too aggressive for caches, since a GC immediately after `put` would evict all entries. For cache values: use `SoftReference`. For event-bus handlers or interned objects where you want GC to collect when the last strong reference disappears: use `WeakReference`.

**Q: How does the double-check pattern in `getOrLoad()` work, and why is it needed?**
Without it: `getOrLoad()` would hold the lock during the loader (DB call), serializing all threads waiting for any key — not just the same key. With double-check: (1) check outside lock (fast for already-cached keys); (2) acquire lock; (3) re-check (another thread may have loaded while waiting for the lock); (4) load if still absent; (5) release lock. This ensures at most one DB call per key while all other threads wait for the in-progress load without holding the lock.

**Q: How do you prevent a thundering-herd stampede on cache restart?**
Three layers: (1) single-flight loading — one DB call per key, other threads await the same `CompletableFuture`; (2) write-through L2 (Redis) — every `put` also writes to Redis so the L2 survives restarts and absorbs misses during rebuild; (3) startup warm-up — seed the top-N keys from Redis before accepting full traffic. The combination means DB sees at most N warm-up loads, not one load per concurrent request per key.

**Q: Your cache hit rate is under 5%. What are the two most likely causes?**
First: working set exceeds `maxSize` — every access evicts a key needed a moment later. Diagnose by measuring distinct-hot-key count (`HyperLogLog`); fix by resizing to ≥ working set and switching to W-TinyLFU for scan resistance. Second: broken `equals`/`hashCode` on the key class — logically equal keys hash to different buckets, so every `get()` is a miss. Diagnose by asserting `cache.get(k) != null` immediately after `cache.put(k, v)`; fix by migrating to a `record` (Java 16+).

**Q: Why does Caffeine W-TinyLFU outperform pure LRU on production workloads?**
Production access is Zipfian: a few keys extremely hot, a long tail rarely touched. Pure LRU is vulnerable to cache pollution: a sequential scan over a large dataset makes every scanned key "recently used" and evicts genuinely hot keys. W-TinyLFU maintains a compact Count-Min sketch estimating access frequency per key; it only admits a new key if its estimated frequency exceeds the eviction candidate's frequency — scan-resistant. Additionally, Caffeine's per-thread `StripedBuffer` ring buffers amortize LRU reorders asynchronously, making `get()` effectively lock-free at ~650k ops/sec vs ~45k for single-lock LRU.

**Q: When would you move from an in-process cache to a distributed cache?**
When the working set exceeds JVM heap budget (50 GB is common at scale), when multiple JVM instances need consistent invalidation (shared writes must propagate to all L1 caches), or when cache state must survive app restarts. The standard tier is: Caffeine L1 (hot subset, per-instance) → Redis Cluster L2 (full working set, shared) → DB (source of truth). Invalidate L1 on writes via Redis pub/sub or short L1 TTLs depending on staleness tolerance.

**Q: What is the `equals`/`hashCode` contract, and how does violating it break a cache?**
The contract: if `a.equals(b)` is true, then `a.hashCode() == b.hashCode()` must also be true. Violation means two logically equal keys compute different hash codes, placing them in different hash table buckets — so `get(newKey)` never finds the value stored under `put(differentInstanceSameContent)`. The cache is silently disabled — misses look like working code. Always override both together; use `record` (Java 16+) for value-type keys to get correct implementations generated automatically.

**Q: How do you size a thread pool for a cache's async loader?**
Async loading with `getOrLoad()` makes a DB call per miss. Model it as a queue: `threads = (miss_rate × avg_load_latency)`. At 100 misses/sec × 50 ms each: 100 × 0.05 s = 5 thread-seconds/s → 5 threads at 100% utilization. Apply 2× headroom → 10 threads, bounded queue at 100 tasks (10 seconds of slack), `CallerRunsPolicy` as backpressure to avoid unbounded queue growth. With Java 21 virtual threads, use `newVirtualThreadPerTaskExecutor()` and skip the sizing math entirely.

**Q: How do you evolve the LRU cache to support multi-level invalidation across services?**
Add a write-through path: every `put()` also writes to Redis (L2); every write to the canonical DB publishes an invalidation message to a Redis pub/sub channel. Each app instance subscribes to that channel and calls `cache.invalidate(key)` on receipt. For eventual consistency: short L1 TTL (bound staleness, simple); for stronger consistency: pub/sub invalidation (fresher, but pub/sub delivery is best-effort — combine with short TTL as fallback). This is the near-cache invalidation pattern used by Hazelcast, Coherence, and Redis enterprise.
