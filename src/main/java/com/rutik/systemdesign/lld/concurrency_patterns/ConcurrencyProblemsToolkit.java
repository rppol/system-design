package com.rutik.systemdesign.lld.concurrency_patterns;

/*
 * CONCURRENCY PROBLEMS TOOLKIT
 *
 * Runnable companion to ConcurrencyProblemsToolkit.md. One public class with a static
 * nested class per interview problem, so no name collides with other classes in this
 * package. Run main() with assertions on (java -ea) to exercise every solution.
 *
 *   1. BoundedBlockingQueue    ReentrantLock + two Conditions (notFull, notEmpty)
 *   2. TtlCache                ConcurrentHashMap + lazy expiry + scheduled sweeper
 *   3. StripedHashMap          lock striping: N locks guard N bucket groups
 *   4. ConcurrentBloomFilter   AtomicLongArray, CAS-set bits, no lock at all
 *   5. PrintInOrder            CountDownLatch as a one-shot signal
 *   6. FooBar                  two Semaphores in ping-pong
 *   7. ZeroEvenOdd             three Semaphores as a token ring
 *   8. H2O                     Semaphore(2) + Semaphore(1) + CyclicBarrier(3)
 *   9. FizzBuzz                one lock, one Condition, shared counter, signalAll
 *  10. ParallelMergeSort       ForkJoinPool + RecursiveAction with a sequential cutoff
 *  11. LivelockDemo            tryLock + randomized back-off breaks the lockstep
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

public final class ConcurrencyProblemsToolkit {

    private ConcurrencyProblemsToolkit() {
    }

    // =====================================================================================
    // 1. Bounded blocking queue: Monitor + Guarded Suspension with two condition queues
    // =====================================================================================

    static final class BoundedBlockingQueue<T> {
        private final Object[] items;
        private int head;
        private int tail;
        private int count;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        BoundedBlockingQueue(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive: " + capacity);
            }
            this.items = new Object[capacity];
        }

        void put(T item) throws InterruptedException {
            Objects.requireNonNull(item, "item");
            lock.lockInterruptibly();
            try {
                while (count == items.length) {      // while, never if: spurious wakeups
                    notFull.await();
                }
                items[tail] = item;
                tail = (tail + 1) % items.length;
                count++;
                notEmpty.signal();                   // wake exactly one consumer
            } finally {
                lock.unlock();
            }
        }

        boolean offer(T item, long timeout, TimeUnit unit) throws InterruptedException {
            Objects.requireNonNull(item, "item");
            long nanos = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (count == items.length) {
                    if (nanos <= 0L) {
                        return false;                // bounded wait: caller decides what to shed
                    }
                    nanos = notFull.awaitNanos(nanos);
                }
                items[tail] = item;
                tail = (tail + 1) % items.length;
                count++;
                notEmpty.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        T take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (count == 0) {
                    notEmpty.await();
                }
                T item = (T) items[head];
                items[head] = null;                  // let the GC reclaim the slot
                head = (head + 1) % items.length;
                count--;
                notFull.signal();                    // wake exactly one producer
                return item;
            } finally {
                lock.unlock();
            }
        }

        int size() {
            lock.lock();
            try {
                return count;
            } finally {
                lock.unlock();
            }
        }
    }

    // =====================================================================================
    // 2. Thread-safe cache with TTL: lazy expiry on read + periodic sweep for unread keys
    // =====================================================================================

    static final class TtlCache<K, V> implements AutoCloseable {
        private record Entry<V>(V value, long expiresAtNanos) {
        }

        private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
        private final long ttlNanos;
        private final LongSupplier clock;             // injectable, so tests need no sleep
        private final ScheduledExecutorService sweeper;

        TtlCache(long ttl, TimeUnit unit, LongSupplier clock, long sweepEveryMillis) {
            this.ttlNanos = unit.toNanos(ttl);
            this.clock = clock;
            this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ttl-cache-sweeper");
                t.setDaemon(true);
                return t;
            });
            sweeper.scheduleAtFixedRate(this::sweep, sweepEveryMillis, sweepEveryMillis,
                    TimeUnit.MILLISECONDS);
        }

        void put(K key, V value) {
            map.put(key, new Entry<>(value, clock.getAsLong() + ttlNanos));
        }

        V get(K key) {
            Entry<V> e = map.get(key);
            if (e == null) {
                return null;
            }
            if (clock.getAsLong() - e.expiresAtNanos() >= 0) {   // overflow-safe comparison
                map.remove(key, e);   // remove ONLY this entry; a fresh put must survive
                return null;
            }
            return e.value();
        }

        /** Atomic load-through: at most one loader runs per key, other callers wait for it. */
        V getOrLoad(K key, java.util.function.Function<K, V> loader) {
            Entry<V> e = map.compute(key, (k, old) -> {
                if (old != null && clock.getAsLong() - old.expiresAtNanos() < 0) {
                    return old;
                }
                return new Entry<>(loader.apply(k), clock.getAsLong() + ttlNanos);
            });
            return e.value();
        }

        int sweep() {
            long now = clock.getAsLong();
            int before = map.size();
            map.entrySet().removeIf(en -> now - en.getValue().expiresAtNanos() >= 0);
            return before - map.size();
        }

        int size() {
            return map.size();
        }

        @Override
        public void close() {
            sweeper.shutdownNow();
        }
    }

    // =====================================================================================
    // 3. Concurrent hash map by lock striping (the interview answer, not the JDK design)
    // =====================================================================================

    static final class StripedHashMap<K, V> {
        private static final int STRIPES = 16;

        private static final class Node<K, V> {
            final K key;
            volatile V value;
            Node<K, V> next;

            Node(K key, V value, Node<K, V> next) {
                this.key = key;
                this.value = value;
                this.next = next;
            }
        }

        private final ReentrantLock[] locks = new ReentrantLock[STRIPES];
        private final Node<K, V>[] buckets;
        private final AtomicInteger size = new AtomicInteger();

        @SuppressWarnings("unchecked")
        StripedHashMap(int bucketCount) {
            if (bucketCount % STRIPES != 0) {
                throw new IllegalArgumentException("bucketCount must be a multiple of " + STRIPES);
            }
            buckets = (Node<K, V>[]) new Node[bucketCount];
            for (int i = 0; i < STRIPES; i++) {
                locks[i] = new ReentrantLock();
            }
        }

        private int bucketOf(Object key) {
            int h = key.hashCode();
            h ^= (h >>> 16);                          // spread high bits, as HashMap does
            return (h & 0x7fffffff) % buckets.length;
        }

        private ReentrantLock lockFor(int bucket) {
            return locks[bucket % STRIPES];
        }

        V put(K key, V value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");   // null would be ambiguous with "absent"
            int b = bucketOf(key);
            ReentrantLock lock = lockFor(b);
            lock.lock();
            try {
                for (Node<K, V> n = buckets[b]; n != null; n = n.next) {
                    if (n.key.equals(key)) {
                        V old = n.value;
                        n.value = value;
                        return old;
                    }
                }
                buckets[b] = new Node<>(key, value, buckets[b]);
                size.incrementAndGet();
                return null;
            } finally {
                lock.unlock();
            }
        }

        V get(Object key) {
            int b = bucketOf(key);
            ReentrantLock lock = lockFor(b);
            lock.lock();          // simple and correct; the JDK reads without a lock
            try {
                for (Node<K, V> n = buckets[b]; n != null; n = n.next) {
                    if (n.key.equals(key)) {
                        return n.value;
                    }
                }
                return null;
            } finally {
                lock.unlock();
            }
        }

        V putIfAbsent(K key, V value) {
            int b = bucketOf(key);
            ReentrantLock lock = lockFor(b);
            lock.lock();          // check and act under ONE lock: that is the whole point
            try {
                for (Node<K, V> n = buckets[b]; n != null; n = n.next) {
                    if (n.key.equals(key)) {
                        return n.value;
                    }
                }
                buckets[b] = new Node<>(key, value, buckets[b]);
                size.incrementAndGet();
                return null;
            } finally {
                lock.unlock();
            }
        }

        int size() {
            return size.get();
        }
    }

    // =====================================================================================
    // 4. Concurrent Bloom filter: CAS on 64-bit words, no false negatives under contention
    // =====================================================================================

    static final class ConcurrentBloomFilter {
        private final AtomicLongArray words;
        private final long bitCount;
        private final int hashes;

        ConcurrentBloomFilter(long expectedInsertions, double falsePositiveRate) {
            double ln2 = Math.log(2);
            long m = (long) Math.ceil(-expectedInsertions * Math.log(falsePositiveRate) / (ln2 * ln2));
            int k = Math.max(1, (int) Math.round((double) m / expectedInsertions * ln2));
            this.bitCount = m;
            this.hashes = k;
            this.words = new AtomicLongArray((int) ((m + 63) / 64));
        }

        long bitCount() {
            return bitCount;
        }

        int hashCount() {
            return hashes;
        }

        void put(Object item) {
            long h64 = mix64(item.hashCode());
            int h1 = (int) h64;
            int h2 = (int) (h64 >>> 32);
            for (int i = 1; i <= hashes; i++) {
                long bit = Math.floorMod(h1 + (long) i * h2, bitCount);  // Kirsch-Mitzenmacher
                setBit(bit);
            }
        }

        boolean mightContain(Object item) {
            long h64 = mix64(item.hashCode());
            int h1 = (int) h64;
            int h2 = (int) (h64 >>> 32);
            for (int i = 1; i <= hashes; i++) {
                long bit = Math.floorMod(h1 + (long) i * h2, bitCount);
                if ((words.get((int) (bit >>> 6)) & (1L << bit)) == 0) {
                    return false;
                }
            }
            return true;
        }

        private void setBit(long bit) {
            int idx = (int) (bit >>> 6);
            long mask = 1L << bit;                    // Java masks the shift to bit & 63
            while (true) {
                long old = words.get(idx);
                if ((old & mask) != 0) {
                    return;                           // already set: no write, no contention
                }
                if (words.compareAndSet(idx, old, old | mask)) {
                    return;
                }
                // CAS lost to another thread setting a DIFFERENT bit in the same word: retry
            }
        }

        private static long mix64(long z) {           // SplitMix64 finalizer
            z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
            z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
            return z ^ (z >>> 33);
        }
    }

    // =====================================================================================
    // 5. Print in Order (LeetCode 1114): first, second, third in any thread order
    // =====================================================================================

    static final class PrintInOrder {
        private final CountDownLatch firstDone = new CountDownLatch(1);
        private final CountDownLatch secondDone = new CountDownLatch(1);

        void first(Runnable printFirst) {
            printFirst.run();
            firstDone.countDown();
        }

        void second(Runnable printSecond) throws InterruptedException {
            firstDone.await();
            printSecond.run();
            secondDone.countDown();
        }

        void third(Runnable printThird) throws InterruptedException {
            secondDone.await();
            printThird.run();
        }
    }

    // =====================================================================================
    // 6. FooBar alternately (LeetCode 1115): two semaphores pass a single token
    // =====================================================================================

    static final class FooBar {
        private final int n;
        private final Semaphore fooTurn = new Semaphore(1);
        private final Semaphore barTurn = new Semaphore(0);

        FooBar(int n) {
            this.n = n;
        }

        void foo(Runnable printFoo) throws InterruptedException {
            for (int i = 0; i < n; i++) {
                fooTurn.acquire();
                printFoo.run();
                barTurn.release();
            }
        }

        void bar(Runnable printBar) throws InterruptedException {
            for (int i = 0; i < n; i++) {
                barTurn.acquire();
                printBar.run();
                fooTurn.release();
            }
        }
    }

    // =====================================================================================
    // 7. Zero Even Odd (LeetCode 1116): 0 1 0 2 0 3 ... with three threads
    // =====================================================================================

    static final class ZeroEvenOdd {
        private final int n;
        private final Semaphore zero = new Semaphore(1);
        private final Semaphore odd = new Semaphore(0);
        private final Semaphore even = new Semaphore(0);

        ZeroEvenOdd(int n) {
            this.n = n;
        }

        void zero(java.util.function.IntConsumer print) throws InterruptedException {
            for (int i = 1; i <= n; i++) {
                zero.acquire();
                print.accept(0);
                (i % 2 == 1 ? odd : even).release();  // hand the token to the right thread
            }
        }

        void odd(java.util.function.IntConsumer print) throws InterruptedException {
            for (int i = 1; i <= n; i += 2) {
                odd.acquire();
                print.accept(i);
                zero.release();
            }
        }

        void even(java.util.function.IntConsumer print) throws InterruptedException {
            for (int i = 2; i <= n; i += 2) {
                even.acquire();
                print.accept(i);
                zero.release();
            }
        }
    }

    // =====================================================================================
    // 8. Building H2O (LeetCode 1117): admit 2 H + 1 O, release them as one molecule
    // =====================================================================================

    static final class H2O {
        private final Semaphore hSlots = new Semaphore(2);
        private final Semaphore oSlots = new Semaphore(1);
        private final CyclicBarrier molecule;

        H2O() {
            // The barrier action runs once per molecule, after all 3 arrived, before any leaves.
            this.molecule = new CyclicBarrier(3, () -> {
                hSlots.release(2);
                oSlots.release(1);
            });
        }

        void hydrogen(Runnable releaseHydrogen) throws InterruptedException {
            hSlots.acquire();
            releaseHydrogen.run();
            awaitMolecule();
        }

        void oxygen(Runnable releaseOxygen) throws InterruptedException {
            oSlots.acquire();
            releaseOxygen.run();
            awaitMolecule();
        }

        private void awaitMolecule() throws InterruptedException {
            try {
                molecule.await();
            } catch (BrokenBarrierException e) {
                throw new IllegalStateException("molecule barrier broken", e);
            }
        }
    }

    // =====================================================================================
    // 9. Fizz Buzz Multithreaded (LeetCode 1195): four threads, one shared counter
    // =====================================================================================

    static final class FizzBuzz {
        private final int n;
        private int current = 1;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition changed = lock.newCondition();

        FizzBuzz(int n) {
            this.n = n;
        }

        private interface Rule {
            boolean mine(int i);
        }

        private void loop(Rule rule, java.util.function.IntConsumer action) throws InterruptedException {
            lock.lock();
            try {
                while (true) {
                    while (current <= n && !rule.mine(current)) {
                        changed.await();
                    }
                    if (current > n) {
                        changed.signalAll();          // let the other three exit too
                        return;
                    }
                    action.accept(current);
                    current++;
                    changed.signalAll();              // the next owner is unknown: wake all 3
                }
            } finally {
                lock.unlock();
            }
        }

        void fizz(Runnable printFizz) throws InterruptedException {
            loop(i -> i % 3 == 0 && i % 5 != 0, i -> printFizz.run());
        }

        void buzz(Runnable printBuzz) throws InterruptedException {
            loop(i -> i % 5 == 0 && i % 3 != 0, i -> printBuzz.run());
        }

        void fizzbuzz(Runnable printFizzBuzz) throws InterruptedException {
            loop(i -> i % 15 == 0, i -> printFizzBuzz.run());
        }

        void number(java.util.function.IntConsumer printNumber) throws InterruptedException {
            loop(i -> i % 3 != 0 && i % 5 != 0, printNumber);
        }
    }

    // =====================================================================================
    // 10. Multithreaded merge sort: fork/join with a sequential cutoff
    // =====================================================================================

    static final class ParallelMergeSort extends RecursiveAction {
        static final int CUTOFF = 8_192;              // same order as Arrays.parallelSort's 1 << 13

        private final int[] a;
        private final int[] buf;
        private final int lo;
        private final int hi;                          // exclusive

        ParallelMergeSort(int[] a, int[] buf, int lo, int hi) {
            this.a = a;
            this.buf = buf;
            this.lo = lo;
            this.hi = hi;
        }

        static void sort(int[] a, ForkJoinPool pool) {
            pool.invoke(new ParallelMergeSort(a, new int[a.length], 0, a.length));
        }

        @Override
        protected void compute() {
            if (hi - lo <= CUTOFF) {
                Arrays.sort(a, lo, hi);               // below the cutoff, forking costs more
                return;
            }
            int mid = (lo + hi) >>> 1;
            invokeAll(new ParallelMergeSort(a, buf, lo, mid),
                      new ParallelMergeSort(a, buf, mid, hi));
            merge(mid);
        }

        private void merge(int mid) {
            System.arraycopy(a, lo, buf, lo, hi - lo);   // each task owns [lo, hi) of buf
            int i = lo;
            int j = mid;
            int k = lo;
            while (i < mid && j < hi) {
                a[k++] = buf[i] <= buf[j] ? buf[i++] : buf[j++];  // <= keeps it stable
            }
            while (i < mid) {
                a[k++] = buf[i++];
            }
            while (j < hi) {
                a[k++] = buf[j++];
            }
        }
    }

    // =====================================================================================
    // 11. Livelock and its fix: tryLock both, back off with random jitter, retry
    // =====================================================================================

    static final class LivelockDemo {
        static boolean acquireBoth(ReentrantLock first, ReentrantLock second, int maxAttempts)
                throws InterruptedException {
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                if (first.tryLock(1, TimeUnit.MILLISECONDS)) {
                    try {
                        if (second.tryLock(1, TimeUnit.MILLISECONDS)) {
                            return true;              // caller must unlock both
                        }
                    } finally {
                        if (!second.isHeldByCurrentThread()) {
                            first.unlock();
                        }
                    }
                }
                // Without jitter, two threads retry in lockstep forever: that is livelock.
                Thread.sleep(ThreadLocalRandom.current().nextLong(1, 5));
            }
            return false;
        }
    }

    // =====================================================================================
    // Driver: run every solution and check its invariant. Use java -ea.
    // =====================================================================================

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            demoBlockingQueue(pool);
            demoTtlCache();
            demoStripedMap(pool);
            demoBloomFilter(pool);
            demoPrintInOrder();
            demoFooBar();
            demoZeroEvenOdd();
            demoH2O();
            demoFizzBuzz();
            demoMergeSort();
            demoLivelockFix();
            System.out.println("All concurrency problems passed.");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void demoBlockingQueue(ExecutorService pool) throws Exception {
        BoundedBlockingQueue<Integer> q = new BoundedBlockingQueue<>(4);
        int items = 10_000;
        AtomicInteger sum = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(2);
        pool.submit(() -> {
            try {
                for (int i = 1; i <= items; i++) {
                    q.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            done.countDown();
        });
        pool.submit(() -> {
            try {
                for (int i = 1; i <= items; i++) {
                    sum.addAndGet(q.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            done.countDown();
        });
        assert done.await(10, TimeUnit.SECONDS) : "queue demo timed out";
        assert sum.get() == items * (items + 1) / 2 : "lost or duplicated items";
        System.out.println("1  BoundedBlockingQueue: 10,000 items, capacity 4, sum ok");
    }

    private static void demoTtlCache() {
        long[] now = {0L};
        try (TtlCache<String, String> cache =
                     new TtlCache<>(100, TimeUnit.MILLISECONDS, () -> now[0], 60_000)) {
            cache.put("k", "v1");
            now[0] += TimeUnit.MILLISECONDS.toNanos(99);
            assert "v1".equals(cache.get("k")) : "entry expired early";
            now[0] += TimeUnit.MILLISECONDS.toNanos(1);
            assert cache.get("k") == null : "entry outlived its TTL";
            cache.put("a", "x");
            cache.put("b", "y");
            now[0] += TimeUnit.MILLISECONDS.toNanos(200);
            assert cache.sweep() == 2 : "sweeper missed unread entries";
            AtomicInteger loads = new AtomicInteger();
            cache.getOrLoad("z", k -> "loaded-" + loads.incrementAndGet());
            cache.getOrLoad("z", k -> "loaded-" + loads.incrementAndGet());
            assert loads.get() == 1 : "load-through ran twice";
        }
        System.out.println("2  TtlCache: expiry at exactly 100 ms, sweep, single load");
    }

    private static void demoStripedMap(ExecutorService pool) throws Exception {
        StripedHashMap<Integer, Integer> map = new StripedHashMap<>(1_024);
        int threads = 8;
        int perThread = 5_000;
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int base = t * perThread;
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    map.put(base + i, i);
                    map.putIfAbsent(i, -1);           // contended keys across all threads
                }
                done.countDown();
            });
        }
        assert done.await(10, TimeUnit.SECONDS) : "map demo timed out";
        assert map.size() == threads * perThread : "size drifted: " + map.size();
        System.out.println("3  StripedHashMap: 8 threads x 5,000 keys, size exact");
    }

    private static void demoBloomFilter(ExecutorService pool) throws Exception {
        int n = 100_000;
        ConcurrentBloomFilter bf = new ConcurrentBloomFilter(n, 0.01);
        int threads = 4;
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int start = t;
            pool.submit(() -> {
                for (int i = start; i < n; i += threads) {
                    bf.put("user-" + i);
                }
                done.countDown();
            });
        }
        assert done.await(10, TimeUnit.SECONDS) : "bloom demo timed out";
        for (int i = 0; i < n; i++) {
            assert bf.mightContain("user-" + i) : "false negative at " + i;
        }
        int falsePositives = 0;
        for (int i = n; i < 2 * n; i++) {
            if (bf.mightContain("user-" + i)) {
                falsePositives++;
            }
        }
        double rate = falsePositives / (double) n;
        assert rate < 0.02 : "false-positive rate too high: " + rate;
        System.out.printf("4  ConcurrentBloomFilter: m=%d bits, k=%d, FP rate %.4f%n",
                bf.bitCount(), bf.hashCount(), rate);
    }

    private static void demoPrintInOrder() throws Exception {
        PrintInOrder p = new PrintInOrder();
        StringBuffer out = new StringBuffer();
        List<Thread> ts = List.of(
                new Thread(() -> runChecked(() -> p.third(() -> out.append("third")))),
                new Thread(() -> runChecked(() -> p.second(() -> out.append("second")))),
                new Thread(() -> p.first(() -> out.append("first"))));
        startAndJoin(ts);
        assert out.toString().equals("firstsecondthird") : out;
        System.out.println("5  PrintInOrder: " + out);
    }

    private static void demoFooBar() throws Exception {
        FooBar fb = new FooBar(5);
        StringBuffer out = new StringBuffer();
        startAndJoin(List.of(
                new Thread(() -> runChecked(() -> fb.bar(() -> out.append("bar")))),
                new Thread(() -> runChecked(() -> fb.foo(() -> out.append("foo"))))));
        assert out.toString().equals("foobar".repeat(5)) : out;
        System.out.println("6  FooBar: " + out);
    }

    private static void demoZeroEvenOdd() throws Exception {
        ZeroEvenOdd z = new ZeroEvenOdd(5);
        StringBuffer out = new StringBuffer();
        startAndJoin(List.of(
                new Thread(() -> runChecked(() -> z.even(out::append))),
                new Thread(() -> runChecked(() -> z.odd(out::append))),
                new Thread(() -> runChecked(() -> z.zero(out::append)))));
        assert out.toString().equals("0102030405") : out;
        System.out.println("7  ZeroEvenOdd: " + out);
    }

    private static void demoH2O() throws Exception {
        H2O h2o = new H2O();
        StringBuffer out = new StringBuffer();
        List<Thread> ts = new ArrayList<>();
        int molecules = 20;
        for (int i = 0; i < 2 * molecules; i++) {
            ts.add(new Thread(() -> runChecked(() -> h2o.hydrogen(() -> out.append('H')))));
        }
        for (int i = 0; i < molecules; i++) {
            ts.add(new Thread(() -> runChecked(() -> h2o.oxygen(() -> out.append('O')))));
        }
        Collections.shuffle(ts, new Random(42));
        startAndJoin(ts);
        String s = out.toString();
        for (int i = 0; i < s.length(); i += 3) {
            String group = s.substring(i, i + 3);
            long hs = group.chars().filter(c -> c == 'H').count();
            assert hs == 2 : "bad molecule " + group + " in " + s;
        }
        System.out.println("8  H2O: " + molecules + " molecules, every group of 3 is H2O");
    }

    private static void demoFizzBuzz() throws Exception {
        FizzBuzz fz = new FizzBuzz(15);
        List<String> out = Collections.synchronizedList(new ArrayList<>());
        startAndJoin(List.of(
                new Thread(() -> runChecked(() -> fz.fizz(() -> out.add("fizz")))),
                new Thread(() -> runChecked(() -> fz.buzz(() -> out.add("buzz")))),
                new Thread(() -> runChecked(() -> fz.fizzbuzz(() -> out.add("fizzbuzz")))),
                new Thread(() -> runChecked(() -> fz.number(i -> out.add(Integer.toString(i)))))));
        List<String> expected = List.of("1", "2", "fizz", "4", "buzz", "fizz", "7", "8", "fizz",
                "buzz", "11", "fizz", "13", "14", "fizzbuzz");
        assert out.equals(expected) : out;
        System.out.println("9  FizzBuzz: " + String.join(",", out));
    }

    private static void demoMergeSort() {
        int[] a = new Random(7).ints(1_000_000, 0, 1_000_000).toArray();
        int[] expected = a.clone();
        Arrays.sort(expected);
        try (ForkJoinPool fj = new ForkJoinPool(4)) {
            ParallelMergeSort.sort(a, fj);
        }
        assert Arrays.equals(a, expected) : "parallel merge sort produced a different order";
        System.out.println("10 ParallelMergeSort: 1,000,000 ints sorted on 4 workers");
    }

    private static void demoLivelockFix() throws Exception {
        ReentrantLock a = new ReentrantLock();
        ReentrantLock b = new ReentrantLock();
        AtomicInteger successes = new AtomicInteger();
        Runnable ab = () -> runChecked(() -> {
            if (LivelockDemo.acquireBoth(a, b, 1_000)) {
                try {
                    successes.incrementAndGet();
                } finally {
                    b.unlock();
                    a.unlock();
                }
            }
        });
        Runnable ba = () -> runChecked(() -> {
            if (LivelockDemo.acquireBoth(b, a, 1_000)) {
                try {
                    successes.incrementAndGet();
                } finally {
                    a.unlock();
                    b.unlock();
                }
            }
        });
        startAndJoin(List.of(new Thread(ab), new Thread(ba)));
        assert successes.get() == 2 : "a thread gave up: " + successes.get();
        System.out.println("11 LivelockDemo: opposite lock order, both threads finished");
    }

    // ---- helpers ------------------------------------------------------------------------

    @FunctionalInterface
    private interface InterruptibleAction {
        void run() throws InterruptedException;
    }

    private static void runChecked(InterruptibleAction action) {
        try {
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void startAndJoin(List<Thread> threads) throws InterruptedException {
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join(TimeUnit.SECONDS.toMillis(10));
            assert !t.isAlive() : "thread did not finish: " + t.getName();
        }
    }
}
