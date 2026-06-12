package com.rutik.systemdesign.lld.system_design_problems;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

// =============================================================================
//  LRU CACHE — Low-Level Design
//  Core idea:
//    - HashMap<K, Node<K,V>>     : O(1) lookup of a key's node
//    - Doubly-linked list        : O(1) removal / re-insertion (recency order)
//    - Sentinel head/tail nodes  : eliminate null-checks at list boundaries
//
//  Patterns used:
//    - Decorator : ThreadSafeLRUCache wraps LRUCacheImpl, adds ReentrantLock
//    - Observer  : CacheEventListener notified on eviction
//
//  Also included: LFUCache — a separate, complete O(1) implementation using
//  frequency buckets, for direct LRU vs LFU comparison.
// =============================================================================

// ─────────────────────────────────────────────
//  NODE — doubly-linked list node
// ─────────────────────────────────────────────

class Node<K, V> {
    K key;
    V value;
    Node<K, V> prev;
    Node<K, V> next;

    Node(K key, V value) {
        this.key = key;
        this.value = value;
    }
}

// ─────────────────────────────────────────────
//  OBSERVER PATTERN — eviction notifications
// ─────────────────────────────────────────────

interface CacheEventListener<K, V> {
    /** Called immediately before an entry is removed due to capacity eviction. */
    void onEviction(K key, V value);
}

// ─────────────────────────────────────────────
//  LRU CACHE — core implementation
//  get/put/evict are all O(1).
//  Named LRUCacheImpl because the top-level demo class below is LRUCache.
// ─────────────────────────────────────────────

class LRUCacheImpl<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;  // sentinel: head.next is the MOST recently used
    private final Node<K, V> tail;  // sentinel: tail.prev is the LEAST recently used
    private int size;

    private CacheEventListener<K, V> listener; // optional Observer

    public LRUCacheImpl(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive.");
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    /** Registers a listener to be notified on eviction (Observer pattern). */
    public void setEventListener(CacheEventListener<K, V> listener) {
        this.listener = listener;
    }

    /**
     * Returns the value for {@code key}, or {@code null} if absent.
     * On a hit, the entry is promoted to most-recently-used. O(1).
     */
    public V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) return null;
        moveToFront(node);
        return node.value;
    }

    /**
     * Inserts or updates {@code key} with {@code value}, promoting it to
     * most-recently-used. If the cache is at capacity and {@code key} is new,
     * evicts the least-recently-used entry first. O(1).
     */
    public void put(K key, V value) {
        Node<K, V> existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }

        if (size == capacity) {
            evictLRU();
        }

        Node<K, V> node = new Node<>(key, value);
        map.put(key, node);
        addToFront(node);
        size++;
    }

    public int size() { return size; }

    public int capacity() { return capacity; }

    /** Returns entries from most-recently-used to least-recently-used. */
    public String snapshot() {
        StringBuilder sb = new StringBuilder("[");
        Node<K, V> cur = head.next;
        while (cur != tail) {
            sb.append(cur.key).append('=').append(cur.value);
            if (cur.next != tail) sb.append(", ");
            cur = cur.next;
        }
        return sb.append(']').toString();
    }

    // ── Internal pointer surgery — all O(1), no traversal ─────────────────

    /** Inserts {@code node} immediately after the head sentinel (MRU position). */
    private void addToFront(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    /** Unlinks {@code node} from the list (does not touch the map). */
    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    /** Removes then re-inserts {@code node} at the front (MRU position). */
    private void moveToFront(Node<K, V> node) {
        removeNode(node);
        addToFront(node);
    }

    /** Evicts the node just before the tail sentinel (LRU position). */
    private void evictLRU() {
        Node<K, V> lru = tail.prev;
        if (lru == head) return; // empty list, nothing to evict
        if (listener != null) listener.onEviction(lru.key, lru.value);
        removeNode(lru);
        map.remove(lru.key);
        size--;
    }
}

// ─────────────────────────────────────────────
//  DECORATOR PATTERN — ThreadSafeLRUCache
//  Wraps a plain LRUCacheImpl without modifying it; adds locking.
// ─────────────────────────────────────────────

class ThreadSafeLRUCache<K, V> {
    private final LRUCacheImpl<K, V> delegate;
    private final ReentrantLock lock = new ReentrantLock();

    public ThreadSafeLRUCache(int capacity) {
        this.delegate = new LRUCacheImpl<>(capacity);
    }

    public V get(K key) {
        lock.lock();
        try {
            return delegate.get(key);
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            delegate.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public void setEventListener(CacheEventListener<K, V> listener) {
        lock.lock();
        try {
            delegate.setEventListener(listener);
        } finally {
            lock.unlock();
        }
    }

    public String snapshot() {
        lock.lock();
        try {
            return delegate.snapshot();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return delegate.size();
        } finally {
            lock.unlock();
        }
    }
}

// ─────────────────────────────────────────────
//  LFU CACHE — separate, complete O(1) implementation
//  (classic LeetCode 460 approach: frequency buckets)
//
//  - keyToNode  : key -> FreqNode (holds value + current frequency)
//  - freqToKeys : frequency -> insertion-ordered set of keys at that frequency
//  - minFreq    : the smallest frequency currently present (eviction candidate set)
// =============================================================================

class LFUCache<K, V> {

    private static final class FreqNode<K, V> {
        K key;
        V value;
        int freq;

        FreqNode(K key, V value) {
            this.key = key;
            this.value = value;
            this.freq = 1;
        }
    }

    private final int capacity;
    private int minFreq;
    private final Map<K, FreqNode<K, V>> keyToNode;
    private final Map<Integer, LinkedHashSet<K>> freqToKeys;
    private CacheEventListener<K, V> listener;

    public LFUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive.");
        this.capacity = capacity;
        this.keyToNode = new HashMap<>();
        this.freqToKeys = new HashMap<>();
        this.minFreq = 0;
    }

    public void setEventListener(CacheEventListener<K, V> listener) {
        this.listener = listener;
    }

    /** Returns the value for {@code key}, or {@code null} if absent. Bumps frequency by 1. O(1). */
    public V get(K key) {
        FreqNode<K, V> node = keyToNode.get(key);
        if (node == null) return null;
        incrementFrequency(node);
        return node.value;
    }

    /**
     * Inserts or updates {@code key}. On insert at full capacity, evicts the
     * least-frequently-used key; ties broken by least-recently-used (oldest
     * insertion within the lowest frequency bucket). O(1).
     */
    public void put(K key, V value) {
        FreqNode<K, V> existing = keyToNode.get(key);
        if (existing != null) {
            existing.value = value;
            incrementFrequency(existing);
            return;
        }

        if (keyToNode.size() == capacity) {
            evictLFU();
        }

        FreqNode<K, V> node = new FreqNode<>(key, value);
        keyToNode.put(key, node);
        freqToKeys.computeIfAbsent(1, f -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    /** Returns a frequency-bucket snapshot, e.g. "{1=[3,4], 2=[1]}", for demo purposes. */
    public String frequencySnapshot() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Integer, LinkedHashSet<K>> entry : freqToKeys.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.append('}').toString();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private void incrementFrequency(FreqNode<K, V> node) {
        int oldFreq = node.freq;
        LinkedHashSet<K> oldBucket = freqToKeys.get(oldFreq);
        oldBucket.remove(node.key);

        if (oldBucket.isEmpty()) {
            freqToKeys.remove(oldFreq);
            if (minFreq == oldFreq) minFreq = oldFreq + 1;
        }

        node.freq++;
        freqToKeys.computeIfAbsent(node.freq, f -> new LinkedHashSet<>()).add(node.key);
    }

    private void evictLFU() {
        LinkedHashSet<K> bucket = freqToKeys.get(minFreq);
        if (bucket == null || bucket.isEmpty()) return;

        K evictKey = bucket.iterator().next(); // oldest entry in the lowest-frequency bucket
        bucket.remove(evictKey);
        if (bucket.isEmpty()) freqToKeys.remove(minFreq);

        FreqNode<K, V> evicted = keyToNode.remove(evictKey);
        if (listener != null && evicted != null) {
            listener.onEviction(evicted.key, evicted.value);
        }
    }
}

// ─────────────────────────────────────────────
//  DEMO / MAIN
// ─────────────────────────────────────────────

public class LRUCache {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("   LRU Cache — LLD Demo");
        System.out.println("========================================\n");

        // ── 1. Basic LRUCacheImpl: put/get/eviction sequence ──────────────
        System.out.println("--- LRUCache (capacity = 3) ---");
        LRUCacheImpl<Integer, String> lru = new LRUCacheImpl<>(3);
        lru.setEventListener((k, v) -> System.out.printf("             -> EVICTED key=%s (value=%s)%n", k, v));

        lru.put(1, "A");
        System.out.println("put(1, \"A\")  -> cache: " + lru.snapshot());

        lru.put(2, "B");
        System.out.println("put(2, \"B\")  -> cache: " + lru.snapshot());

        lru.put(3, "C");
        System.out.println("put(3, \"C\")  -> cache: " + lru.snapshot());

        String g1 = lru.get(1); // promotes key=1 to MRU
        System.out.println("get(1)       -> \"" + g1 + "\"   | cache: " + lru.snapshot());

        lru.put(4, "D"); // capacity reached; key=2 is now LRU -> evicted
        System.out.println("put(4, \"D\")  -> cache: " + lru.snapshot());

        String g2 = lru.get(2); // miss, key=2 was evicted
        System.out.println("get(2)       -> " + g2 + " (miss, was evicted)");

        String g3 = lru.get(3);
        System.out.println("get(3)       -> \"" + g3 + "\"   | cache: " + lru.snapshot());

        // ── 2. ThreadSafeLRUCache: concurrent access via ExecutorService ──
        System.out.println("\n--- ThreadSafeLRUCache (capacity = 2, 4 threads) ---");
        ThreadSafeLRUCache<Integer, String> safeCache = new ThreadSafeLRUCache<>(2);
        safeCache.setEventListener((k, v) ->
                System.out.printf("[listener] EVICTED key=%s (value=%s)%n", k, v));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        pool.submit(() -> {
            safeCache.put(10, "X");
            System.out.println("[thread] put(10, \"X\")");
        });
        pool.submit(() -> {
            safeCache.put(20, "Y");
            System.out.println("[thread] put(20, \"Y\")");
        });
        pool.submit(() -> {
            String v = safeCache.get(10);
            System.out.println("[thread] get(10) -> \"" + v + "\"");
        });
        pool.submit(() -> {
            safeCache.put(30, "Z");
            System.out.println("[thread] put(30, \"Z\")");
        });

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Final state: " + safeCache.snapshot() + "   (no corruption under concurrent access)");

        // ── 3. LFUCache: access pattern that diverges from LRU eviction ──
        System.out.println("\n--- LFUCache (capacity = 3) ---");
        LFUCache<Integer, String> lfu = new LFUCache<>(3);
        lfu.setEventListener((k, v) ->
                System.out.printf("             -> EVICTED key=%s (value=%s, was least frequently used)%n", k, v));

        lfu.put(1, "A");
        System.out.println("put(1, \"A\")  -> freq" + lfu.frequencySnapshot());

        lfu.put(2, "B");
        System.out.println("put(2, \"B\")  -> freq" + lfu.frequencySnapshot());

        lfu.put(3, "C");
        System.out.println("put(3, \"C\")  -> freq" + lfu.frequencySnapshot());

        lfu.get(1); // freq(1) = 2
        System.out.println("get(1)       -> \"A\"  | freq" + lfu.frequencySnapshot());

        lfu.get(1); // freq(1) = 3
        System.out.println("get(1)       -> \"A\"  | freq" + lfu.frequencySnapshot());

        // capacity reached: key=2 and key=3 both have freq=1; key=2 is older -> evicted
        lfu.put(4, "D");
        System.out.println("put(4, \"D\")  -> freq" + lfu.frequencySnapshot());

        // ── 4. LRU vs LFU contrast on the same access pattern ─────────────
        System.out.println("\n--- LRU vs LFU contrast ---");
        System.out.println("Same access pattern, different eviction:");
        System.out.println("  LRU  would evict key=3 (least *recently* used — key=1 was accessed twice after it)");
        System.out.println("  LFU  evicted  key=2 (least *frequently* used — both 2 and 3 had freq=1, but 2 is older)");

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
