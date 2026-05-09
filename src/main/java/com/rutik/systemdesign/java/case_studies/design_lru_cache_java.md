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
