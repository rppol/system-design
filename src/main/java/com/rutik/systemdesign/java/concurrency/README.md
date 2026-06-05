# Concurrency

## 1. Concept Overview

Java concurrency is the single hardest part of the language and the most common source of production bugs. Race conditions, deadlocks, and memory visibility issues are notoriously difficult to reproduce and diagnose. Java provides multiple layers of concurrency primitives: the low-level `synchronized`/`volatile` keywords tied to the Java Memory Model; the `java.util.concurrent` (JUC) package introduced in Java 5 with locks, queues, executors, and atomic variables; and Java 21's virtual threads and structured concurrency.

This module is the most critical for senior Java interviews. Expect questions on volatile vs synchronized, ThreadPoolExecutor internals, CAS and ABA, the Java Memory Model, and how to diagnose deadlocks.

---

## 2. Intuition

> **One-line analogy**: Threads sharing mutable state are like multiple cooks in a kitchen — without coordination protocols, they'll collide on every pot and pan. Java's concurrency primitives are those protocols: locks say "only one cook at this station," volatile says "post your changes on the shared whiteboard immediately," and atomic operations say "swap the ingredient in one indivisible motion."

**Mental model**: Every CPU core has its own cache. Without explicit synchronization, a write on Core 1 may not be visible to Core 2 until a cache flush. The Java Memory Model defines *happens-before* relationships that guarantee when writes by one thread become visible to reads by another. `synchronized` and `volatile` are the main tools to establish these relationships.

**Why it matters**: A race condition that appears 1 in 10,000 runs in testing will appear 1 in 100 runs in production under load. Double-checked locking without `volatile` worked fine in testing (single-threaded) and caused mysterious NPEs on production servers for years. ThreadLocal memory leaks slowly killed servlet containers at scale.

**Key insight**: The majority of concurrency bugs fall into three categories: (1) visibility failure (stale reads — fix with `volatile` or `synchronized`); (2) atomicity failure (check-then-act race — fix with locks or CAS); (3) ordering failure (JIT reorders operations — fix with `volatile`/`synchronized`/JMM happens-before).

---

## 3. Core Principles

- **Thread lifecycle**: NEW → RUNNABLE → BLOCKED/WAITING/TIMED_WAITING → TERMINATED.
- **Monitor/intrinsic lock**: Every object has a monitor; `synchronized` acquires it; only one thread at a time.
- **Volatile**: Guarantees visibility (flush to main memory) and ordering (no reordering across volatile access). Does NOT guarantee atomicity for compound operations.
- **Happens-before (JMM)**: The formal definition of inter-thread visibility guarantees.
- **CAS (Compare-And-Swap)**: Hardware instruction; atomically: if value == expected, set to new; return success/failure. Foundation of all lock-free algorithms.
- **Amdahl's Law**: Maximum speedup from parallelism = 1 / (S + (1-S)/N) where S = serial fraction. Even 10% serial code limits speedup to 10× regardless of cores.

---

## 4. Types / Architectures / Strategies

### 4.1 Synchronization Primitives

| Primitive | Guarantee | Use Case |
|-----------|-----------|---------|
| `synchronized` | Mutual exclusion + visibility | Simple critical sections |
| `volatile` | Visibility + ordering (no atomicity) | Flags, single-writer fields |
| `ReentrantLock` | Mutex + tryLock/timeout/fairness | Advanced locking |
| `ReentrantReadWriteLock` | Multiple readers OR exclusive writer | Read-heavy shared data |
| `StampedLock` | Optimistic read + pessimistic read/write | Low-contention reads |
| `AtomicInteger/Long/Reference` | CAS-based atomic operations | Counters, compare-and-swap |
| `LongAdder` | Striped counter, low-contention | High-throughput counters |

### 4.2 ThreadPoolExecutor — The 7 Parameters

```java
new ThreadPoolExecutor(
    int corePoolSize,        // threads kept alive even when idle
    int maximumPoolSize,     // max threads (above core when queue full)
    long keepAliveTime,      // idle time before extra thread terminates
    TimeUnit unit,
    BlockingQueue<Runnable> workQueue,  // DETERMINES GROWTH BEHAVIOR
    ThreadFactory threadFactory,        // how threads are created
    RejectedExecutionHandler handler    // what to do when queue+max full
);
```

**Queue type determines growth behavior**:
| Queue | Behavior |
|-------|---------|
| `LinkedBlockingQueue` (unbounded) | Queue grows forever; max threads NEVER reached; can OOM |
| `ArrayBlockingQueue(N)` (bounded) | Queue fills → more threads up to max → saturates → reject |
| `SynchronousQueue` | No buffer; each task needs a thread; grows to max immediately |
| `PriorityBlockingQueue` | Priority ordering; unbounded |

**Growth order**: Core threads first → queue up → extra threads up to max → saturate → reject

### 4.3 Synchronizers

| Synchronizer | Use Case |
|-------------|---------|
| `CountDownLatch` | Wait for N events (one-shot, cannot reset) |
| `CyclicBarrier` | N threads wait for each other at a barrier (reusable) |
| `Semaphore` | Limit concurrent access to N permits |
| `Phaser` | Flexible multi-phase barrier |
| `Exchanger` | Two threads swap data at a synchronization point |

### 4.4 Saturation Policies

| Policy | Behavior |
|--------|---------|
| `AbortPolicy` (default) | Throw `RejectedExecutionException` |
| `CallerRunsPolicy` | Caller thread runs the task (backpressure) |
| `DiscardPolicy` | Silently drop |
| `DiscardOldestPolicy` | Drop oldest queued task, retry |

---

## 5. Architecture Diagrams

### ThreadPoolExecutor Task Flow
```
submit(task)
  |
  v
corePoolSize reached? -- No --> create new core thread -> run task
  |
  Yes
  v
workQueue full? -- No --> enqueue task -> wait for core thread
  |
  Yes
  v
maximumPoolSize reached? -- No --> create extra thread -> run task
  |
  Yes
  v
RejectedExecutionHandler (AbortPolicy throws RejectedExecutionException)
```

### ReentrantReadWriteLock
```
Read lock: multiple readers can hold simultaneously
Write lock: exclusive; blocks all readers and other writers

Thread A: readLock.lock()  -> granted (no writers)
Thread B: readLock.lock()  -> granted (read sharing)
Thread C: writeLock.lock() -> BLOCKED (waiting for A, B to release)
Thread D: readLock.lock()  -> BLOCKED (writer waiting — prevents reader starvation)

Downgrading: hold write lock, acquire read lock, release write lock
Upgrading: NOT supported (would cause deadlock)
```

### CAS — Compare-And-Swap
```java
AtomicInteger counter = new AtomicInteger(5);

// Hardware instruction (LOCK CMPXCHG on x86):
// if (current == expected) { current = newValue; return true; }
// else { return false; }

// CAS loop (retry on failure):
int oldVal, newVal;
do {
    oldVal = counter.get();
    newVal = oldVal + 1;
} while (!counter.compareAndSet(oldVal, newVal));
// No locks; but can spin-wait under high contention
// LongAdder beats AtomicLong under contention by striping (multiple cells)
```

### ABA Problem
```
Thread A: reads value=A
Thread B: changes A -> B -> A (back to A)
Thread A: CAS(expected=A, new=C) SUCCEEDS — but state has changed!

Example: stack [A -> B]
Thread A: reads top=A, will CAS(A, C)
Thread B: pops A, pops B, pushes A back
Thread A: CAS succeeds, but B is now lost (dangling pointer)

Fix: AtomicStampedReference (value + version counter)
     CAS checks both value AND version — even if value cycles back to A
```

---

## 6. How It Works — Detailed Mechanics

### synchronized — Intrinsic Lock

```java
// Method-level: lock is `this`
public synchronized void increment() { count++; }
// Equivalent to:
public void increment() {
    synchronized (this) { count++; }
}

// Class-level: lock is MyClass.class
public static synchronized void staticIncrement() { ... }

// Reentrancy: same thread can re-acquire the lock it already holds
synchronized (lock) {
    synchronized (lock) {  // OK — intrinsic lock is reentrant
        // ...
    }
}

// Bytecode: monitorenter / monitorexit instructions
// JVM guarantees: monitorexit in finally block even if exception thrown
```

### volatile — Full Semantics

```java
// Guarantees: (1) visibility: write immediately flushed to main memory
//             (2) ordering: no reordering across volatile access
//             NOT: atomicity for compound operations

volatile boolean stopped = false;

// Thread A:
stopped = true;  // visible to all threads immediately

// Thread B (polling loop):
while (!stopped) { doWork(); }  // sees the write from Thread A

// volatile long/double on 32-bit JVM:
// Normal long/double: 64-bit write may be split into 2x 32-bit (torn write)
// volatile long/double: guaranteed atomic 64-bit read/write

// NOT enough for increment:
volatile int count = 0;
count++;  // read-modify-write: NOT atomic; use AtomicInteger
```

### wait() / notify() — Spurious Wakeup

```java
// BROKEN: if() check
synchronized (lock) {
    if (queue.isEmpty()) {
        lock.wait();  // Thread may wake spuriously (OS/JVM may wake without notify)
    }
    process(queue.take());
}

// CORRECT: while() loop — handles spurious wakeup AND multiple notifiers
synchronized (lock) {
    while (queue.isEmpty()) {   // Re-check condition after wakeup
        lock.wait();
    }
    process(queue.take());
}

// RULES for wait/notify:
// - Must hold the lock (synchronized) before calling wait()/notify()
// - wait() atomically releases lock and suspends thread
// - notify() wakes one waiting thread (arbitrary); notifyAll() wakes all
// - Prefer java.util.concurrent over wait/notify for new code
```

### ReentrantLock Advanced Features

```java
ReentrantLock lock = new ReentrantLock(true);  // fair: FIFO order

// tryLock: non-blocking acquisition attempt
if (lock.tryLock()) {
    try { /* ... */ } finally { lock.unlock(); }
} else {
    // couldn't acquire, do something else
}

// tryLock with timeout
if (lock.tryLock(100, TimeUnit.MILLISECONDS)) { ... }

// lockInterruptibly: can be interrupted while waiting
try {
    lock.lockInterruptibly();
    // ...
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // restore interrupt flag
}

// Condition variables (replacement for wait/notify)
Condition notEmpty = lock.newCondition();
lock.lock();
try {
    while (queue.isEmpty()) notEmpty.await();  // releases lock + waits
    process(queue.poll());
    notEmpty.signal();
} finally { lock.unlock(); }
```

### LongAdder vs AtomicLong Under Contention

```java
// AtomicLong: single cell, all threads CAS on same memory location
// Under high contention: many CAS failures -> spin -> contention O(N)

// LongAdder: striped cells (default 1, grows on contention)
// Each thread updates a different cell -> minimal contention
// sum() aggregates all cells -> correct total

LongAdder counter = new LongAdder();
counter.increment();     // updates one cell
long total = counter.sum();  // adds all cells

// Performance: LongAdder 10-20x faster under high contention
// AtomicLong wins for single-reader; LongAdder for write-heavy
```

### CompletableFuture Composition

```java
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> fetchUser(id), executor)          // async
    .thenApply(user -> user.toDTO())                     // transform (sync)
    .thenCompose(dto -> fetchOrders(dto.id()))           // flatMap (returns CF)
    .exceptionally(ex -> new UserDTO("error"))           // handle exception

// Combining two futures
CompletableFuture.thenCombine(cf1, cf2, (r1, r2) -> combine(r1, r2));

// Wait for all
CompletableFuture.allOf(cf1, cf2, cf3)
    .thenRun(() -> System.out.println("All done"));

// Race: first to complete wins
CompletableFuture.anyOf(cf1, cf2, cf3)
    .thenAccept(result -> System.out.println("First: " + result));

// Exception handling:
cf.handle((result, ex) -> ex != null ? fallback : result);  // always runs
cf.whenComplete((r, ex) -> log(r, ex));  // side-effect, doesn't change value
cf.exceptionally(ex -> fallback);        // only on exception
```

### LockSupport.park() / unpark()

```java
// LockSupport is the foundation of all JUC blocking primitives (AQS, ReentrantLock, etc.)
// park(): blocks the current thread until a permit is available (or interrupt/spurious wakeup)
// unpark(thread): grants one permit to the specified thread

// Key difference from Object.wait():
// - No lock required: park()/unpark() work without any monitor
// - Pre-posting: unpark() called BEFORE park() → park() returns immediately (permit consumed)
//   (Object.notify() before wait() is LOST — the thread misses it)
// - Not associated with a monitor: no IllegalMonitorStateException risk

Thread worker = new Thread(() -> {
    System.out.println("Worker: about to park");
    LockSupport.park();              // blocks here (unless permit already available)
    System.out.println("Worker: unparked, continuing");
});
worker.start();

Thread.sleep(100);  // let worker reach park()
LockSupport.unpark(worker);  // grant permit; worker resumes

// Pre-posting example (unpark before park):
LockSupport.unpark(worker);  // grant permit NOW
// Later, when worker calls park() → returns immediately (permit consumed)
LockSupport.park();          // returns without blocking!

// park with timeout:
LockSupport.parkNanos(1_000_000L);        // park for up to 1ms
LockSupport.parkUntil(System.currentTimeMillis() + 1000);  // absolute deadline

// park with blocker (shows up in thread dumps for diagnosis):
Object blocker = this;
LockSupport.park(blocker);  // jstack shows: "waiting on" the blocker object
```

### Priority Inversion

```
Priority inversion: a high-priority thread is effectively blocked by a low-priority thread
that holds a lock the high-priority thread needs.

Classic scenario:
  Thread H (high priority):  needs Lock L
  Thread M (medium priority): CPU-bound, preempts L
  Thread L (low priority):    holds Lock L

  L can't finish (M preempts it), H can't proceed (L holds the lock).
  H is starved by M — even though H has higher priority than M.

Java manifestation:
  - OS thread scheduler prioritizes M over L
  - L never releases its lock
  - H blocks waiting for L forever

Mitigation strategies:
  1. Priority inheritance: OS raises L's priority to H's level while L holds the lock
     (POSIX real-time: PTHREAD_PRIO_INHERIT mutex attribute; not available in Java)
  2. Priority ceiling: lock has a predefined "ceiling" priority; any thread holding it
     gets that ceiling priority temporarily
  3. Avoid priority dependency: don't use locks held by low-priority threads
     from high-priority code; use lock-free algorithms instead
  4. Use equal priorities: avoid priority differences in threads sharing locks

Java's ReentrantLock(fair=true) with FIFO ordering doesn't solve priority inversion —
it can make it worse by allowing low-priority threads ahead of high-priority ones
if they arrived first. Use lock-free data structures for latency-critical high-priority code.
```

### AbstractQueuedSynchronizer (AQS) Internals

```java
// AQS is the framework underlying: ReentrantLock, CountDownLatch,
// Semaphore, ReentrantReadWriteLock, SynchronousQueue, FutureTask

// Core: volatile int state + CLH (Craig-Landin-Hagersten) queue of waiting threads

// Internal structure:
// state: volatile int
//   - ReentrantLock: 0 = unlocked, N = locked (N = reentry count)
//   - Semaphore: number of available permits
//   - CountDownLatch: initial count; 0 = released

// CLH Queue: doubly-linked list of Node objects
// Each Node: {thread, waitStatus, prev, next}
// Nodes are added to tail when thread can't acquire; removed from head when granted

// Acquire (exclusive) algorithm:
// 1. tryAcquire(arg): CAS on state — if succeeds, current thread owns lock
// 2. If fails: add current thread to CLH queue tail (CAS on tail pointer)
// 3. Loop: park(), check if predecessor is head, try tryAcquire again
// 4. Once acquire succeeds: set node as new head, remove from queue

// Release algorithm:
// 1. tryRelease(arg): update state (set to 0 for mutex)
// 2. If head.waitStatus indicates waiters: unpark(head.next.thread)

// How ReentrantLock uses AQS:
class NonFairSync extends AbstractQueuedSynchronizer {
    boolean tryAcquire(int acquires) {
        if (compareAndSetState(0, acquires)) {  // CAS: 0 → 1
            setExclusiveOwnerThread(Thread.currentThread());
            return true;
        }
        if (getExclusiveOwnerThread() == Thread.currentThread()) {  // reentrant
            setState(getState() + acquires);
            return true;
        }
        return false;
    }
}

// CountDownLatch uses shared mode AQS:
//   countDown() calls releaseShared(1): state--
//   await() calls acquireSharedInterruptibly: blocks until state == 0
// When state reaches 0: ALL waiting threads are woken simultaneously (shared release)

// Semaphore: state = permits; acquire = tryAcquireShared (state--); release = state++
```

---

## 7. Real-World Examples

- **Thread pool queue saturation → RejectedExecutionException**: A service used `Executors.newCachedThreadPool()` (unbounded threads). Under load spike, it created 50,000 threads, exhausted OS thread limit, crashed the JVM. Fix: bounded `ThreadPoolExecutor` with `ArrayBlockingQueue`.
- **HashMap infinite loop (Java 6 concurrent resize race)**: Two threads concurrently inserting into a `HashMap` triggered simultaneous resize. The linked list in a bucket formed a cycle during the resize, causing infinite loop in `get()`. Fixed in Java 8 (resize algorithm reworked), but ConcurrentHashMap should always be used for multi-threaded access.
- **ThreadLocal memory leak in Tomcat**: A web app stored request-scoped data in `ThreadLocal`. After undeploy, Tomcat reused threads but the `ThreadLocal` values (with references to app classloader) were never removed. This prevented the app's classloader (and all its loaded classes) from being GC'd — metaspace leak.

---

## 8. Tradeoffs

| Choice | When | Why |
|--------|------|-----|
| synchronized | Simple critical sections | Low overhead, simple, built-in |
| ReentrantLock | Need tryLock, fairness, Condition | More control |
| volatile | Single-writer flags | Cheaper than synchronized |
| AtomicInteger | Simple counters | Lock-free, better throughput |
| LongAdder | High-contention counters | 10-20x faster than AtomicLong |
| ThreadPoolExecutor | CPU-bound tasks | Full control, bounded |
| Virtual threads | I/O-bound blocking code | Millions of concurrent tasks |

---

## 9. When to Use / When NOT to Use

**Use `volatile` when**:
- One thread writes, others read (no compound read-modify-write)
- Need a simple "stop flag" for a running thread

**Use `synchronized` when**:
- Multiple threads read AND write
- Need atomicity for compound operations (check-then-act)

**Use `ReentrantLock` when**:
- Need `tryLock()` to avoid deadlock
- Need timed lock acquisition
- Need multiple `Condition` objects on one lock
- Need fair locking

**Do NOT use `synchronized` when**:
- Fine-grained locking needed (ConcurrentHashMap is faster than synchronized map)
- Virtual threads and I/O (use `ReentrantLock` to avoid pinning)

---

## 10. Common Pitfalls

### War Story 1: Double-checked locking without volatile (production NPE)
```java
// BROKEN — seen in Java codebases as late as 2018
private static Service instance;
public static Service getInstance() {
    if (instance == null) {
        synchronized (Service.class) {
            if (instance == null) {
                instance = new Service();  // JIT may reorder: assign before init
            }
        }
    }
    return instance;  // Thread B may see non-null but uninitialized object
}
```
Caused intermittent NPEs in production on multi-core servers. Fix: `volatile` on `instance`.

### War Story 2: ThreadPoolExecutor — tasks never run (wrong queue)
A developer used `Executors.newFixedThreadPool(10)` which uses `LinkedBlockingQueue` (unbounded). Under load, the queue grew to millions of tasks. The pool had only 10 threads, maximum was also 10 — no new threads were created because the queue was never full. Tasks waited for hours. Fix: `ArrayBlockingQueue` with bounded size + `CallerRunsPolicy` for backpressure.

### War Story 3: CountDownLatch vs CyclicBarrier wrong choice
A developer used `CountDownLatch` for a "wait at barrier then continue" pattern across 5 phases of processing. After phase 1, the latch was fully counted down — useless for phases 2-5. Fix: `CyclicBarrier` resets after each phase; `CountDownLatch` is one-shot.

### War Story 4: Livelock in retry logic
Two threads, each trying to acquire locks A then B with `tryLock()`. Thread 1 acquires A, Thread 2 acquires B. Both fail to get the second lock, release their first, immediately retry — and keep repeating. Neither makes progress. Fix: add randomized backoff (`Thread.sleep(random.nextInt(100))`).

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `java.util.concurrent.*` | All JUC classes |
| `jstack <pid>` | Thread dump for deadlock detection |
| `-Djdk.tracePinnedThreads=full` | Detect virtual thread pinning |
| `ThreadMXBean` | Deadlock detection via JMX |
| async-profiler | Wall-clock profiling to find blocking threads |
| `ThreadPoolExecutor.getQueue().size()` | Monitor queue depth |

---

## 12. Interview Questions with Answers

**Q1: What is the difference between `volatile`, `synchronized`, and `AtomicInteger` — which do you choose?**
`volatile` guarantees visibility (writes flushed immediately) and ordering (no reordering around volatile access) but NOT atomicity for compound operations like `i++`. Use `volatile` for single-writer flags. `synchronized` gives both mutual exclusion and visibility — safe for any critical section but coarser. `AtomicInteger` uses CAS (hardware compare-and-swap) — lock-free atomicity for single-variable operations, better throughput than synchronized for counters. Choose: simple flag → volatile; compound operation → synchronized or Atomic; high-contention counter → `LongAdder`.

**Q2: Explain the happens-before relationship and give an example.**
Happens-before is a formal guarantee from the JVM: if action A happens-before action B, B is guaranteed to see all writes by A. Key edges: program order (each statement hb next in same thread), monitor unlock hb subsequent lock, volatile write hb subsequent volatile read, Thread.start() hb any action in child thread, Thread.join() hb any action after join returns. Example: Thread A writes `x=42; flag=true` (flag is volatile). Thread B reads `if(flag) read x`. When B reads `flag==true`, the volatile read establishes hb after A's volatile write, guaranteeing B sees `x=42`.

**Q3: What is the bug in double-checked locking and how do you fix it?**
The bug: `instance = new Service()` compiles to 3 operations: (1) allocate memory, (2) initialize fields, (3) assign reference. JIT may reorder to (1)→(3)→(2): the reference is assigned before initialization completes. Thread B sees non-null reference, skips the synchronized block, reads partially initialized object, gets NPE. Fix: declare field as `volatile` — volatile write establishes happens-before, preventing reordering of the initialization steps across the assignment.

**Q4: What happens when ThreadPoolExecutor's queue is full?**
The executor tries to create a new thread up to `maximumPoolSize`. If max is also reached, the `RejectedExecutionHandler` is invoked. Default is `AbortPolicy` — throws `RejectedExecutionException`. Production-safe alternatives: `CallerRunsPolicy` (caller thread runs the task — natural backpressure), `DiscardOldestPolicy` (drops oldest queued task and retries). With `LinkedBlockingQueue` (unbounded), the queue never fills — max threads are never created and rejection never happens, but the queue can grow to OOM.

**Q5: What is the difference between `Callable` and `Runnable`?**
`Runnable`: `void run()` — no return value, cannot throw checked exceptions. `Callable<V>`: `V call() throws Exception` — returns a value, can throw checked exceptions. `Callable` is used with `ExecutorService.submit()` which returns a `Future<V>`. `Runnable` with `submit()` returns `Future<?>` (always `null` result). Use `Callable` whenever you need to retrieve a result or propagate a checked exception from an async task.

**Q6: What is the ABA problem in CAS and how is it addressed?**
ABA: Thread A reads value=A, is preempted. Thread B changes A→B→A. Thread A resumes, CAS(A,C) succeeds because the value is A again — but the state has changed in between. Example: lock-free stack where a node is popped and re-pushed; a CAS on the stack top pointer sees the same address but the stack structure changed. Fix: `AtomicStampedReference<V>` — stores both value and a version/stamp integer. CAS checks both value AND stamp, so even if value cycles back to A, the stamp increment prevents the ABA scenario.

**Q7: Why is ConcurrentHashMap 10-20× faster in Java 8 than Java 7?**
Java 7 used a striped locking design with 16 segments (each a mini-HashMap), requiring lock acquisition for every write. Java 8 redesigned: for empty buckets, insertion uses CAS (no lock); for non-empty buckets, only the bucket HEAD is `synchronized` (lock-striping at bucket level, not segment level). Also added `LongAdder` for the size counter (no global lock for `size()`). With 16 buckets in Java 7, 16 concurrent threads could run; with Java 8, hundreds of threads can write to different buckets simultaneously.

**Q8: When would you use `CountDownLatch` vs `CyclicBarrier`?**
`CountDownLatch`: one-shot, counts down to zero, waiting threads released once. Use when waiting for N independent events to complete (e.g., N initialization tasks; N is known upfront). Cannot be reset. `CyclicBarrier`: reusable, all N threads wait at the barrier, then all continue together; a barrier action runs when full. Use for iterative algorithms where N threads synchronize at each phase boundary (e.g., parallel matrix computation phases). Rule: CountDownLatch for "wait for N events"; CyclicBarrier for "all N threads rendezvous at a point and continue together repeatedly."

**Q9: How does `ForkJoinPool`'s work-stealing algorithm work?**
Each worker thread has a deque (double-ended queue) of tasks. Tasks are split by recursion and pushed to the thread's own deque. The thread pops from the head (LIFO — good for cache). When a thread's deque is empty, it *steals* from the tail (FIFO) of another thread's deque. Stealing from the tail minimizes contention between the owner (working at head) and the thief. Work-stealing efficiently utilizes all CPUs and handles imbalanced workloads where some sub-tasks take longer than others.

**Q10: What is `ThreadLocal` and when can it cause memory leaks?**
`ThreadLocal` provides a per-thread variable — each thread has its own copy, no synchronization needed. Common uses: request context (userId per request), database connections, SimpleDateFormat (not thread-safe). Memory leak: when used with thread pools, threads live for the JVM lifetime. If `ThreadLocal.remove()` is not called after use, the value persists in the thread's `ThreadLocalMap` forever. If the value holds a reference to application objects (e.g., request context holding HTTP response), those objects are never GC'd. **Fix**: always call `threadLocal.remove()` in a `finally` block.

**Q11: What is the difference between `sleep()` and `wait()`?**
`Thread.sleep(ms)` suspends the current thread for the specified duration — does NOT release any held locks, is a static method on Thread, throws `InterruptedException`. `Object.wait()` suspends the current thread AND atomically releases the intrinsic lock on the object — the thread re-acquires the lock when awakened. `wait()` must be called from inside `synchronized`; `sleep()` can be called anywhere. Use `wait()`/`notify()` for producer-consumer coordination; use `sleep()` for simple time delays.

**Q12: What is livelock? How does it differ from deadlock?**
Deadlock: threads are BLOCKED, waiting for each other indefinitely — no progress, no CPU usage. Livelock: threads are ACTIVE (not blocked), repeatedly changing state in response to each other but making no progress. Example: two threads each back off when they detect a conflict, but they back off simultaneously and try again simultaneously, forever. Both are starvation scenarios; livelock is harder to detect because threads aren't blocked. Fix for livelock: randomized backoff, prioritization, or resource ordering.

**Q13: What is `StampedLock`'s optimistic read and how does it work?**
`StampedLock.tryOptimisticRead()` returns a stamp (version number) without acquiring any lock. The thread reads data optimistically. Then `validate(stamp)` checks if a write occurred during the read — if yes (stamp invalid), fall back to a full read lock. This allows completely lock-free reads when there are no writers, making it significantly faster than `ReentrantReadWriteLock` for read-heavy, low-write workloads. Caveat: `StampedLock` is not reentrant; attempting to re-acquire can deadlock.

**Q14: How does `CompletableFuture.allOf()` work?**
`CompletableFuture.allOf(cf1, cf2, cf3)` returns a new `CompletableFuture<Void>` that completes when ALL input futures complete. It doesn't return the individual results (use `cf1.get()` etc. after `allOf` completes). If any future completes exceptionally, the `allOf` future also completes exceptionally with that exception. The implementation installs a completion handler on each input future; when all complete, the result future is completed. Use `.thenRun()` or `.thenApply()` to collect results after `allOf`.

**Q15: Explain virtual thread pinning and how to avoid it.**
A virtual thread is "pinned" to its carrier thread when it cannot be unmounted while blocking — the OS thread is blocked too, wasting resources. Pinning occurs when: (1) the virtual thread is inside a `synchronized` block/method while waiting; (2) it's inside a native (JNI) method. To avoid: replace `synchronized` with `ReentrantLock` in I/O-bound paths. Detect with JVM flag `-Djdk.tracePinnedThreads=full`. Java 24 removes pinning for `synchronized` on non-primitive monitor objects.

**Q16: What is a spurious wakeup and how do you handle it?**
A spurious wakeup is when `Object.wait()` or `Condition.await()` returns without being `notify()`d or interrupted — a rare but legal JVM behavior (POSIX condition variable allows it). If you use `if (condition) wait()`, a spurious wakeup will proceed past the check even when the condition is still false, causing incorrect behavior. **Fix**: always use `while (condition) wait()` — re-check the condition after every wakeup. This is why all Java concurrency examples show a `while` loop.

**Q17: What is `LockSupport.park()` and how does it differ from `Object.wait()`?**
`LockSupport.park()` suspends the current thread until a permit is available. `LockSupport.unpark(thread)` grants a permit to the specified thread. Key differences from `Object.wait()`: (1) No monitor/lock required — `park()`/`unpark()` work without any `synchronized` block; `wait()` must be called inside `synchronized`. (2) Permit pre-posting — if `unpark()` is called BEFORE `park()`, the park returns immediately (permit consumed); `notify()` before `wait()` is lost. (3) No `IllegalMonitorStateException` risk. `LockSupport` is the primitive used by AQS (`AbstractQueuedSynchronizer`) to park/unpark threads in waiting queues — it underlies all JUC locks. It also supports a blocker object for thread dump diagnostics: `LockSupport.park(blockingObject)`.

**Q18: What is priority inversion and how does it manifest in Java?**
Priority inversion: a high-priority thread (H) is blocked waiting for a lock held by a low-priority thread (L), while a medium-priority thread (M) preempts L and prevents it from finishing. H is effectively blocked by M, even though H has higher priority than M. In Java: if L holds `ReentrantLock`, M's CPU-bound work starves L, which never releases the lock, which starves H indefinitely. Java has no built-in priority inheritance (OS mechanism to temporarily raise L's priority to H's level). Mitigation: avoid priority differences among threads sharing locks; use lock-free data structures for high-priority code; or use equal-priority threads for all critical sections.

**Q19: What is `AbstractQueuedSynchronizer` and how does `ReentrantLock` use it?**
AQS is the framework underlying all major JUC synchronizers. Core structure: `volatile int state` + a CLH (doubly-linked) queue of waiting `Node` objects. Acquire: `tryAcquire(arg)` tries a CAS on state; if it fails, the thread is added to the CLH queue tail and parked. Release: `tryRelease(arg)` updates state; if successful, unpacks the next waiting thread. `ReentrantLock` uses AQS with `state` = 0 (unlocked) or N (lock count for reentrant holds). `tryAcquire` CAS's from 0 to 1; re-entry increments state. `tryRelease` decrements state; releases when it reaches 0. `CountDownLatch` uses shared-mode AQS where all waiters are released when state reaches 0. `Semaphore` uses state as permit count.

---

## 13. Best Practices

1. **Prefer JUC over `synchronized`/`wait`/`notify`** — `ReentrantLock`, `BlockingQueue`, `CountDownLatch` are higher-level and safer.
2. **Size thread pools explicitly**: CPU-bound = `# CPU cores + 1`; IO-bound = `# CPU cores × (1 + wait_time/compute_time)`.
3. **Always use bounded queues** in `ThreadPoolExecutor` with a sensible saturation policy (`CallerRunsPolicy` for backpressure).
4. **Always call `threadLocal.remove()`** in a `finally` block in pooled thread environments.
5. **Use `volatile` for `instance` in double-checked locking** — no exceptions.
6. **Use `LongAdder` instead of `AtomicLong`** for high-contention counters.
7. **Prefer `ReadWriteLock` or `StampedLock`** for read-heavy shared data structures.
8. **Use `CompletableFuture` with explicit executor** — never use the default `ForkJoinPool.commonPool()` for I/O.
9. **Detect deadlocks** in production with `ThreadMXBean.findDeadlockedThreads()` in a scheduled health check.
10. **Use virtual threads + `ReentrantLock`** for new I/O-bound code on Java 21+.

---

## 14. Case Study

### A Request-Coalescing Cache at 100k RPS

**Scenario.** A read-heavy product API serves **100,000 requests/sec** across the fleet, ~95% served from an in-process cache. The danger is the **cache stampede** (a.k.a. thundering herd): when a hot key expires, every concurrent request for it misses simultaneously. Without coalescing, 1,000 concurrent misses on one key fire **1,000 identical DB queries** in the same millisecond, the DB connection pool (HikariCP default 10) saturates, latency spikes, and the outage cascades. With coalescing, the first miss starts exactly **one** DB lookup; the other 999 callers attach to the same in-flight `CompletableFuture` and wake when it completes.

```
  1000 concurrent requests for key "P-42" (just expired)
        |
   ConcurrentHashMap.computeIfAbsent("P-42", k -> startAsyncLoad(k))
        |                         |
   first caller wins         999 callers receive the SAME CompletableFuture
   starts 1 DB query              |
        v                         v
   DB: 1 query  <-------- all 1000 complete from one result
```

#### Coalescing with `computeIfAbsent` returning a shared future

```java
public final class CoalescingCache<K, V> {
    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
    private final Function<K, V> loader;
    private final Executor pool;

    public CoalescingCache(Function<K, V> loader, Executor pool) {
        this.loader = loader; this.pool = pool;
    }

    public CompletableFuture<V> get(K key) {
        // computeIfAbsent's mapping function runs atomically per key:
        // exactly one thread builds the future; the rest receive the same instance.
        return inFlight.computeIfAbsent(key, k ->
            CompletableFuture
                .supplyAsync(() -> loader.apply(k), pool)
                .whenComplete((v, ex) -> inFlight.remove(k)));  // allow re-load next miss
    }
}
```

The atomicity of `computeIfAbsent` per key is what makes this safe: even with 1,000 threads hitting the same key, the mapping function executes once, so only one `supplyAsync` (one DB query) is created. The `whenComplete` removes the entry so a *future* miss can reload — without it, the cache would serve one permanently stale value.

#### Measured impact

```
            DB queries on 1000-way concurrent miss   p99 latency under stampede
no coalesce            1000                            900ms (pool exhausted)
coalesce                  1                            70ms  (1 query + future wake)
```

#### Why the entry must be removed on completion (negative cache hazard)

```java
// If a load FAILS and you leave the failed future in the map, every future caller
// receives the SAME failed future forever -- a "negative cache" that never recovers.
.whenComplete((v, ex) -> inFlight.remove(key));   // remove on BOTH success and failure
```

Removing on completion turns the map into a *coalescing* structure (dedupe concurrent in-flight loads) rather than a *result* cache. The actual value cache (with TTL) sits behind the loader; the coalescing map only prevents duplicate concurrent work. This separation is what lets a transient DB error self-heal on the next request instead of being pinned.

#### Bounding blast radius with a timeout

```java
public V getOrThrow(K key, Duration timeout) {
    try {
        return get(key).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        inFlight.remove(key);          // don't let a hung load poison followers
        throw new CacheLoadTimeout(key, e);
    } catch (ExecutionException | InterruptedException e) {
        throw new CacheLoadFailed(key, e);
    }
}
```

Without the timeout, a single hung downstream call would block all 1,000 coalesced callers indefinitely — coalescing concentrates the risk into one future, so that future must have a deadline.

### Common Pitfalls (production war stories)

**1. Plain `HashMap` shared across threads.** An early cache used a `HashMap`. Under concurrent `put`, a JDK 7 resize formed a circular linked list in a bucket and a reader spun at 100% CPU forever (infinite loop). Even on JDK 8 (which fixed the cycle) concurrent writes still lose entries and throw `ConcurrentModificationException`.

```java
private final Map<K, V> cache = new HashMap<>();          // BROKEN: corrupts under writes
private final Map<K, V> cache = new ConcurrentHashMap<>();// FIX
```

**2. `volatile` used for a compound check-then-act.** A "load once" guard used `if (!loaded) { load(); loaded = true; }` with `volatile boolean loaded`. `volatile` gives visibility but not atomicity, so two threads both saw `false` and both loaded. Fix: `computeIfAbsent` or a lock around the compound action.

**3. `ThreadLocal` not removed in a pooled thread.** A per-request `ThreadLocal<UserContext>` was set but never cleared. Because the request ran on a reused pool thread, the context leaked into the next request (data exposure) and the objects were never GC'd (memory leak). Always `remove()` in a `finally`.

```java
try { CTX.set(user); handle(); } finally { CTX.remove(); }   // FIX
```

**4. Double-checked locking without `volatile`.** A lazy singleton used DCL on a non-`volatile` field. A second thread could observe the field as non-null while the constructor's writes were not yet visible, handing out a **half-constructed** object.

```java
private static Service inst;                       // BROKEN: missing volatile
// FIX: volatile establishes happens-before so writes in the constructor are visible.
private static volatile Service inst;
static Service get() {
    if (inst == null) synchronized (Service.class) {
        if (inst == null) inst = new Service();
    }
    return inst;
}
```

### Interview Discussion Points

**Why does `computeIfAbsent` solve the stampede where a plain `get`-then-`put` does not?** `computeIfAbsent` runs the mapping function atomically with respect to the key's bin, so concurrent callers for the same key see a single computation. A `get` followed by `put` is a check-then-act race: many threads see the miss and each starts its own load.

**Why store a `CompletableFuture` instead of the value itself?** The future is created instantly and shared; late callers attach to the in-flight computation rather than starting their own. If you cached the value, the slow load would still happen N times before the first value is stored.

**What guarantees does `volatile` give and not give?** It guarantees visibility (a write is seen by subsequent reads) and ordering (happens-before across the volatile access), but not atomicity of compound operations like increment or check-then-act. Use atomics or locks for those.

**Why must the DCL field be `volatile`?** Without it, the publishing write of the reference can be reordered before the object's fields are initialized, so another thread may read a non-null reference to a partially constructed object. `volatile` forbids that reordering and publishes the fully built object.

**How do you prevent ThreadLocal leaks in a thread pool?** Always pair `set` with `remove` in a `finally` block, because pool threads outlive the request and would otherwise carry stale values into the next task and prevent GC of the held objects.

**For high-precision rate limiting, where does this differ?** The token-bucket limiter (see `case_studies/design_rate_limiter_java.md`) uses an `AtomicLong` CAS loop for lock-free decrement and `volatile` for refill-time visibility; it tolerates slight refill imprecision in exchange for throughput, a different tradeoff than the exactness the coalescing cache needs.

**Why must a coalescing future have a timeout?** Coalescing concentrates 1,000 callers onto one in-flight future. If the underlying load hangs, all 1,000 hang with it — the very concentration that helps under load becomes a single point of failure without a deadline. A timeout caps the worst-case wait and removes the poisoned entry.

**Why remove the entry on failure, not just success?** Leaving a completed-exceptionally future in the map creates a negative cache: every subsequent caller receives the same failed future and the key can never reload, so a transient error becomes permanent. Removing on any completion lets the next request retry fresh.

---

## Related / See Also

- [Java Memory Model](../java_memory_model/README.md) — happens-before rules, volatile semantics, safe publication
- [Structured Concurrency & Loom](../structured_concurrency_and_loom/README.md) — virtual threads, StructuredTaskScope as a higher-level alternative
- [Case Study: Rate Limiter](../case_studies/design_rate_limiter_java.md) — AtomicLong CAS loop for lock-free token-bucket rate limiting
- [Case Study: Circuit Breaker](../case_studies/design_circuit_breaker_java.md) — CAS-based state machine for fault tolerance

**Is `computeIfAbsent` safe to call recursively or with a slow mapping function?** No — the mapping function runs while holding the bin lock, so a long-running or re-entrant `computeIfAbsent` on the same map can block other writers to that bin or deadlock. Keep the mapping function fast; here it only *creates* the future (`supplyAsync` returns immediately) rather than performing the load inline.
