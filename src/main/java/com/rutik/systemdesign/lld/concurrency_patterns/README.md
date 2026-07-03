# Concurrency Patterns

Low-level design patterns that address coordination between threads sharing a JVM process. Each pattern targets a specific coordination failure mode.

---

## 1. Concept Overview

| Pattern | File | Problem Solved | Primary Mechanism |
|---------|------|---------------|-------------------|
| Thread-Safe Singleton | [ThreadSafeSingleton_README.md](ThreadSafeSingleton_README.md) | One shared instance across all threads | DCL + volatile, enum, Holder idiom |
| Producer-Consumer | [ProducerConsumer_README.md](ProducerConsumer_README.md) | Decouple production from consumption via bounded buffer | `BlockingQueue`, `wait()`/`notifyAll()` |
| Read-Write Lock | [ReadWriteLock_README.md](ReadWriteLock_README.md) | Allow concurrent reads, exclusive writes | `ReentrantReadWriteLock`, `StampedLock` |
| Thread Pool | [ThreadPool_README.md](ThreadPool_README.md) | Reuse worker threads across many tasks | `ExecutorService`, `ThreadPoolExecutor` |

---

## 2. Intuition

Concurrency patterns solve the coordination problem: multiple threads competing for shared resources without stepping on each other. Each pattern addresses a specific coordination failure mode: Singleton prevents duplicate shared resource initialization, Producer-Consumer prevents speed mismatch from causing unbounded queues or starvation, Read-Write Lock prevents readers from blocking each other unnecessarily, and Thread Pool prevents thread-per-request from exhausting OS resources.

---

## 3. When to Use Which Pattern

```
Multiple threads share ONE resource that must be initialized exactly once?
  -> Thread-Safe Singleton (prefer enum; else Holder idiom)
     Do not use DCL unless enum and Holder are impossible.

Threads PRODUCE work items; other threads CONSUME them at different rates?
  -> Producer-Consumer (use BlockingQueue for simplicity over raw wait/notify)
     Bound the queue to prevent OOM if producers outrun consumers.

Data is READ frequently, WRITTEN rarely (read-heavy workload)?
  -> Read-Write Lock (ReentrantReadWriteLock; StampedLock for optimistic reads)
     Fairness=true prevents writer starvation on highly-read data.

Many short-lived TASKS need execution without creating a thread per task?
  -> Thread Pool (ExecutorService; tune corePoolSize, maxPoolSize, queue type)
     For I/O-bound tasks on Java 21+, consider virtual threads instead.
```

---

## 4. Relationship to Java Concurrency Infrastructure

| Pattern | Java Classes | Key Guarantees |
|---------|-------------|----------------|
| Thread-Safe Singleton | `volatile`, class-loading guarantees | Visibility; no partial construction visible to other threads |
| Producer-Consumer | `BlockingQueue`, `ArrayBlockingQueue`, `LinkedBlockingQueue` | Bounded capacity; blocking on full/empty; thread-safe by contract |
| Read-Write Lock | `ReentrantReadWriteLock`, `StampedLock`, `ReadWriteLock` | Multiple concurrent readers; exclusive writers; optional fairness |
| Thread Pool | `Executors`, `ThreadPoolExecutor`, `ForkJoinPool`, `ExecutorCompletionService` | Thread reuse; task queue management; configurable rejection policies |

---

## 5. Common Concurrency Anti-Patterns

These are the most frequent mistakes found in production code reviews. Each is a specific failure mode of the patterns above.

**Locking too broadly** — `synchronized` on an entire method when only a three-line critical section needs protection. Throughput drops because threads queue behind the lock even for work that doesn't touch shared state.

**Locking too narrowly** — two separate `synchronized` blocks that together form one logical atomic operation. Another thread can interleave between the two blocks and observe an inconsistent intermediate state.

**DCL without volatile** — double-checked locking where the `instance` field is not `volatile`. Partial construction is visible: a second thread can observe a non-null reference to an incompletely-constructed object. See `ThreadSafeSingleton_README.md` for the broken and fixed versions.

**`notify()` instead of `notifyAll()`** — with multiple consumers, `notify()` may wake a producer instead of a consumer, deadlocking the system. Use `notifyAll()` and re-check the condition in a `while` loop, not an `if`. `BlockingQueue` handles this internally.

**Creating threads directly instead of using a pool** — one thread per incoming HTTP request or DB query. Under load, OS thread count explodes. Default OS limit: ~32k threads. At ~1MB stack each, 1,000 threads = 1 GB RAM before doing any work.

**Sharing mutable state without synchronization** — `ArrayList`, `HashMap`, `SimpleDateFormat` used as static fields read and written by multiple threads. `HashMap` concurrent modification can cause an infinite loop in JDK 7 (a real production incident pattern). Use `ConcurrentHashMap`.

---

## 6. Virtual Threads (Project Loom) Impact

Java 21 virtual threads (`Thread.ofVirtual().start(...)`) are cheap enough (~few KB stack vs ~1 MB for platform threads) that thread-per-task is viable again for I/O-bound workloads.

**What changes:**
- Thread Pool sizing: `Executors.newVirtualThreadPerTaskExecutor()` replaces fixed-size pools for I/O tasks. Thousands of virtual threads are sustainable.
- `ThreadPoolExecutor` tuning: corePoolSize / maxPoolSize / keepAliveTime matter less for I/O tasks.

**What does NOT change:**
- Thread-Safe Singleton: virtual threads have the same memory visibility concerns. DCL still needs `volatile`; enum is still the best choice.
- Read-Write Lock semantics: virtual threads can still race on shared mutable state. `ReentrantReadWriteLock` is still necessary.
- Producer-Consumer backpressure: if consumers are CPU-bound, having 10,000 virtual threads producing work they cannot process still causes memory pressure. Bounded `BlockingQueue` backpressure remains essential.

**Caution:** `synchronized` blocks pin virtual threads to their carrier platform thread, eliminating the benefit of virtual threads. Prefer `ReentrantLock` over `synchronized` in code paths expected to run on virtual threads.

---

## 7. Pattern Selection by Failure Mode

```
Failure: Two threads initialize the same expensive resource twice
  -> Thread-Safe Singleton (Holder idiom or enum)

Failure: Fast producer fills memory; slow consumer crashes
  -> Producer-Consumer with bounded BlockingQueue + backpressure

Failure: Write lock blocks all concurrent read traffic
  -> Read-Write Lock; writers get exclusive access; readers don't block each other

Failure: 10,000 concurrent requests; JVM OOM on thread creation
  -> Thread Pool (or virtual threads on Java 21+)

Failure: check-then-act race ("if empty, add")
  -> ConcurrentHashMap.putIfAbsent() or AtomicReference.compareAndSet()

Failure: Readers starve writers (continuous read traffic blocks writes)
  -> ReentrantReadWriteLock with fairness=true, or StampedLock optimistic reads
```

---

## 9. Code Examples — Broken Then Fixed

### DCL Without volatile — Broken and Fixed

```java
// BROKEN: instance is not volatile; partial construction visible to other threads
public class Singleton {
    private static Singleton instance; // missing volatile

    public static Singleton getInstance() {
        if (instance == null) {             // Thread A passes this check
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton(); // Thread B may see non-null but unconstructed
                }
            }
        }
        return instance; // Thread B may return a partially-constructed object
    }
}

// FIXED: volatile prevents reordering; JMM guarantees construction before publication
public class Singleton {
    private static volatile Singleton instance;

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}

// PREFERRED: Holder idiom — lazy, thread-safe, no volatile needed
public class Singleton {
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}
```

### notify() Lost Wakeup — Broken and Fixed

```java
// BROKEN: notify() with multiple consumers may wake the wrong thread
class BoundedBuffer<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;

    public synchronized void put(T item) throws InterruptedException {
        while (queue.size() == capacity) wait();
        queue.add(item);
        notify(); // may wake another producer, not a consumer
    }

    public synchronized T take() throws InterruptedException {
        while (queue.isEmpty()) wait();
        T item = queue.poll();
        notify(); // may wake another consumer, not a producer
        return item;
    }
}

// FIXED: notifyAll() wakes all waiting threads; while-loop re-checks condition
class BoundedBuffer<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;

    public synchronized void put(T item) throws InterruptedException {
        while (queue.size() == capacity) wait(); // re-check after wakeup
        queue.add(item);
        notifyAll(); // wake ALL waiting threads; each checks its own condition
    }

    public synchronized T take() throws InterruptedException {
        while (queue.isEmpty()) wait();
        T item = queue.poll();
        notifyAll();
        return item;
    }
}

// BEST: Use BlockingQueue — handles all of this internally
BlockingQueue<String> queue = new ArrayBlockingQueue<>(1000);
// producer:
queue.put(item);  // blocks when full; no notify() needed
// consumer:
String item = queue.take(); // blocks when empty; no notify() needed
```

### ThreadPoolExecutor with CallerRunsPolicy — Backpressure

```java
// Producer that naturally slows down when the pool is saturated
ExecutorService pool = new ThreadPoolExecutor(
    4,                             // corePoolSize
    8,                             // maxPoolSize
    60L, TimeUnit.SECONDS,         // keepAliveTime for non-core threads
    new ArrayBlockingQueue<>(100), // bounded queue; fills before spawning max threads
    new ThreadPoolExecutor.CallerRunsPolicy() // caller thread executes task when queue full
);

// When queue=100 full AND threads=8, the submitting thread runs the task itself.
// This slows the producer to consumer speed — natural backpressure.
```

---

## 10. Common Pitfalls — Production War Stories

**The ConcurrentModificationException in a multi-threaded cache** — A team used `HashMap` as a shared cache across request threads with no synchronization. Under load, `HashMap.get()` entered an infinite loop because another thread's `put()` caused a resize mid-traversal (JDK 7 behavior). Fix: `ConcurrentHashMap`. Lesson: `HashMap` is never safe for concurrent use, even for read-only access during writes.

**The ThreadPoolExecutor that never scaled** — Configuration: `corePoolSize=5`, `maxPoolSize=200`, `LinkedBlockingQueue` (unbounded). Load increased to 500 concurrent requests. The pool stayed at 5 threads. Reason: with an unbounded queue, the pool never grows past corePoolSize — the queue never fills. Fix: use a bounded queue (`new ArrayBlockingQueue<>(50)`) so the pool grows when the queue fills under load.

**Write starvation killed the configuration service** — A configuration service used `ReentrantReadWriteLock` (unfair). 200 web threads read config every 100ms. Configuration updates (writes) were attempted every 30 seconds. Writers waited for minutes because new readers arrived faster than the queue drained. Fix: `new ReentrantReadWriteLock(true)` (fair). Alternatively: read-through cache so config updates are infrequent; readers hit the cache, not the lock.

**Virtual thread pinning in Hibernate** — After upgrading to Java 21 and switching to `newVirtualThreadPerTaskExecutor()`, a team saw no throughput improvement. Profiling showed virtual threads pinned to carrier threads for the entire Hibernate session. Reason: Hibernate internally uses `synchronized` blocks. Fix: stay on platform threads for Hibernate-heavy paths until the library migrates to `ReentrantLock`, or use a separate thread pool for Hibernate tasks.

---

## 11. Technologies and Tools

| Tool / Library | Concurrency Pattern |
|----------------|-------------------|
| `java.util.concurrent.BlockingQueue` | Producer-Consumer |
| `java.util.concurrent.ThreadPoolExecutor` | Thread Pool |
| `java.util.concurrent.locks.ReentrantReadWriteLock` | Read-Write Lock |
| `java.util.concurrent.locks.StampedLock` | Optimistic Read-Write Lock |
| `java.util.concurrent.Executors` | Thread Pool factory |
| `java.lang.Thread.ofVirtual()` (Java 21) | Virtual thread per task |
| `jcstress` (OpenJDK) | Stress-testing concurrency correctness |
| SpotBugs + `@GuardedBy` | Static analysis for synchronization violations |
| `java.util.concurrent.atomic.*` | Lock-free atomic operations for Singleton init |

---

## 8. Cross-References

| Pattern | See Also |
|---------|---------|
| Thread-Safe Singleton | `../creational/singleton/` — GoF Singleton; this section covers the thread-safety mechanisms |
| Thread Pool | `../../java/concurrency/` — ExecutorService internals, ThreadPoolExecutor parameters |
| Read-Write Lock | `../../java/java_memory_model/` — happens-before, volatile semantics |
| Producer-Consumer | `../../backend/messaging_patterns/` — distributed Producer-Consumer via Kafka |
| All patterns | `../../java/structured_concurrency_and_loom/` — virtual threads impact on all patterns |

---

## 12. Interview Q&As

Q&As ordered by interview frequency: gotchas and traps first, internals second, edge cases last.

---

**Q: Why must the field in DCL Singleton be volatile — what breaks without it?**

Without `volatile`, the JVM may reorder the instructions: `instance = new Singleton()` can be seen by another thread as (1) allocate memory, (2) assign reference to `instance`, (3) invoke constructor — steps 2 and 3 can be reordered. A second thread checks `instance != null` after step 2 but before step 3, sees a non-null reference, and uses a partially-constructed object. `volatile` inserts a memory barrier that prevents this reordering. This is the pre-Java 5 DCL bug; `volatile` fixes it in Java 5+ via the JMM.

---

**Q: Enum Singleton vs Holder idiom vs DCL + volatile — which is "correct" and when do you use each?**

Enum (Effective Java Item 3) is the definitive answer: thread-safe by JVM guarantee, handles serialization and reflection attacks automatically, and is the simplest. Holder idiom (`private static class Holder`) is the preferred choice when enum is not possible (e.g., the class has to extend another class, or the singleton must implement a more complex initialization). DCL + volatile is correct but verbose — it exists for cases where the singleton must be lazily initialized AND the class cannot use enum or Holder for some reason. In practice: enum if possible; Holder if you need a class-based singleton.

---

**Q: Producer-Consumer with `wait()`/`notify()`: what is the spurious wakeup bug?**

`notify()` wakes exactly one thread, but a thread woken by `notify()` should re-check the condition — not assume the condition is true — because the condition may have changed between the `notify()` call and the awakened thread actually running. More critically, `notify()` with multiple consumers can wake the wrong thread (a producer waking a producer instead of a consumer, deadlocking the system). Fix: always use `notifyAll()` and re-check the condition in a `while` loop, not an `if` statement. `BlockingQueue` handles all of this internally — prefer it over raw `wait()`/`notifyAll()`.

---

**Q: ThreadPoolExecutor: when does it create new threads vs queue tasks vs reject?**

With `corePoolSize=5`, `maxPoolSize=10`, `queueCapacity=20`: (1) if active threads < corePoolSize, create a new core thread even if the queue has space; (2) if active threads >= corePoolSize, add to queue; (3) if queue is full AND active threads < maxPoolSize, create a new non-core thread; (4) if queue is full AND active threads >= maxPoolSize, apply rejection policy. Counter-intuitive: the pool doesn't grow beyond corePoolSize until the queue is FULL. If corePoolSize=5 and maxPoolSize=50 with an unbounded queue, max threads stay at 5 forever.

---

**Q: What is write starvation in Read-Write Lock and how does the fairness parameter fix it?**

In an unfair `ReentrantReadWriteLock`, new read lock requests can be granted even when a writer is waiting — because readers don't block other readers. If reads are continuous, writers wait indefinitely (starvation). Fix: `new ReentrantReadWriteLock(true)` (fair mode) uses a FIFO ordering: once a writer is waiting, new readers must queue behind it. Cost: throughput drops because reads that could be concurrent are now serialized behind the waiting writer. `StampedLock` provides optimistic reads as an alternative: read without acquiring the lock, then validate; if invalid, upgrade to a full read lock.

---

**Q: Thread pool rejection policies — when does each apply?**

`AbortPolicy` (default): throws `RejectedExecutionException`. Use when the caller must know the task was rejected. `CallerRunsPolicy`: the submitting thread executes the task directly, providing natural backpressure (the caller slows down). Use when you'd rather slow the producer than lose tasks. `DiscardPolicy`: silently drops the task. Use only when tasks are truly expendable (e.g., monitoring samples). `DiscardOldestPolicy`: drops the oldest queued task and retries. Use when newest tasks are more valuable than oldest (e.g., real-time position updates).

---

**Q: Virtual threads (Java 21): do these concurrency patterns still apply?**

Singleton and Read-Write Lock: still fully relevant — virtual threads have the same memory visibility concerns and can still race on shared state. Producer-Consumer backpressure: still needed for CPU-bound consumers (virtual threads don't help with CPU saturation). Thread Pool: partially replaced — for I/O-bound tasks, a virtual thread per task is now viable. `Executors.newVirtualThreadPerTaskExecutor()` replaces a fixed thread pool for I/O tasks. But for CPU-bound work with known parallelism needs, a fixed platform-thread pool (sized to CPU cores) is still preferred.

---

**Q: How does `BlockingQueue` implement both the Producer-Consumer and the backpressure patterns?**

`BlockingQueue.put()` blocks the producer when the queue is at capacity. `BlockingQueue.take()` blocks the consumer when the queue is empty. The bounded capacity (`new ArrayBlockingQueue<>(1000)`) acts as a buffer: it absorbs speed bursts. When the buffer is full, `put()` blocks — the producer slows to consumer speed automatically (backpressure). `offer(item, timeout)` gives producers a timeout to avoid indefinite blocking. `LinkedBlockingQueue` with no capacity limit is an unbounded queue — it provides buffering but no backpressure and can grow until OOM under sustained producer excess.

---

**Q: ForkJoinPool vs ThreadPoolExecutor — when do you use each?**

`ThreadPoolExecutor`: general-purpose; best for independent tasks of uniform size (HTTP requests, DB queries, event processing). `ForkJoinPool`: designed for divide-and-conquer tasks that recursively fork subtasks and join results. Uses work-stealing: idle threads steal tasks from busy threads' queues, keeping all CPUs busy even when subtask sizes vary. `ForkJoinPool.commonPool()` backs `CompletableFuture.supplyAsync()` and parallel streams. Use `ForkJoinPool` for recursive computation (merge sort, tree traversal, parallel streams); use `ThreadPoolExecutor` for independent task execution.

---

**Q: How do you detect thread safety issues in code review?**

Look for: (1) mutable shared fields accessed from multiple threads without synchronization or `volatile`; (2) check-then-act patterns that aren't atomic (`if (map.containsKey(k)) map.put(k, v)` — use `putIfAbsent`); (3) iterating over a shared collection while another thread modifies it; (4) `SimpleDateFormat` or `Calendar` shared as a static field (not thread-safe); (5) lazy initialization without `volatile` or synchronization. Tools: SpotBugs `@GuardedBy` annotations, ErrorProne checks, and `jcstress` for JMM correctness verification.

---

**Q: What is the `synchronized` pinning problem with virtual threads and how do you avoid it?**

When a virtual thread enters a `synchronized` block, it is pinned to its carrier platform thread for the duration of the block. If the virtual thread then blocks on I/O inside the `synchronized` block, the carrier platform thread is also blocked — eliminating the scalability benefit of virtual threads. On Java 21, this means a `synchronized`-heavy codebase does not benefit from `newVirtualThreadPerTaskExecutor()`. Fix: replace `synchronized` with `ReentrantLock`. `ReentrantLock.lock()`/`unlock()` allows the virtual thread to unmount from the carrier while waiting, freeing the carrier to run other virtual threads.

---
