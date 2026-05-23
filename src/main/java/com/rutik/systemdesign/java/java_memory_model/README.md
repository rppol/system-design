# Java Memory Model

## 1. Concept Overview

The Java Memory Model (JMM) is the formal specification that defines how multithreaded Java programs behave with respect to memory visibility and instruction ordering. Without the JMM, the behavior of concurrent programs would be platform-dependent — a program could work on one processor architecture and silently produce wrong results on another.

The JMM defines one concept above all others: **happens-before**. If action A happens-before action B, then all writes by A (and all writes that happened-before A) are guaranteed to be visible to B. Without a happens-before edge between two actions in different threads, those threads can observe writes in any order — including no order at all.

This module covers the complete happens-before rule enumeration, memory barrier mappings, safe publication idioms, and the data race / race condition distinction. It is the theoretical foundation for everything in the Concurrency module.

---

## 2. Intuition

> **One-line analogy**: The JMM is a contract between developer and JVM — the developer agrees to establish happens-before edges; the JVM agrees to make all writes visible across those edges. Without the contract, the JVM is free to reorder and cache as it pleases.

**Mental model**: Every CPU core has private registers and L1/L2 caches. A write by Thread A on Core 1 may stay in Core 1's cache and never propagate to Core 2's cache without a memory flush. The JMM defines exactly which Java constructs cause those flushes and which enforce ordering constraints. The JMM does NOT describe specific hardware instructions — it defines a programmer-visible abstraction that all JVM implementations must satisfy.

**Why it matters**: JMM violations are the most insidious bugs in Java. They are non-deterministic, appear only under specific timing conditions, are invisible in testing on x86 (which has a strong memory model), and are catastrophic on ARM and SPARC (weaker memory models). The classic double-checked locking bug is a JMM violation. Understanding the JMM turns "it sometimes fails on the production server but never in tests" from mystifying to diagnosable.

**Key insight**: The JMM guarantees sequential consistency (all threads see a consistent global order of operations) ONLY for data-race-free programs. If your program has a data race, all bets are off — the program is in undefined behavior territory for that race. The guarantee: write DRF (data-race-free) code → JVM guarantees sequential consistency.

---

## 3. Core Principles

- **Happens-before (hb)**: The transitive ordering relation that guarantees memory visibility between actions.
- **Data race**: Two accesses to the same variable where at least one is a write, and no happens-before orders them. Programs with data races have undefined JMM behavior.
- **Race condition**: A correctness bug dependent on timing/ordering — broader than data race; can occur even with synchronization if logic is wrong.
- **DRF (Data-Race-Free)**: A program has no data races. DRF programs are guaranteed sequentially consistent by the JMM.
- **Memory visibility**: Whether a read by one thread sees a write by another.
- **Instruction reordering**: JIT compiler and CPU may reorder instructions for optimization — the JMM constrains which reorderings are observable to other threads.
- **Safe publication**: Making an object's reference visible to other threads only after its construction is complete.

---

## 4. Types / Architectures / Strategies

### 4.1 Complete Happens-Before Rule Enumeration

| Rule | Description |
|------|-------------|
| Program order | Each action in a thread happens-before every subsequent action in that thread |
| Monitor lock | An `unlock` of a monitor happens-before every subsequent `lock` of that same monitor |
| Volatile write | A write to a `volatile` field happens-before every subsequent read of that field |
| Thread start | `Thread.start()` happens-before any action in the started thread |
| Thread join | Any action in a thread happens-before `Thread.join()` returning in another thread |
| Final field freeze | The write of a `final` field in a constructor happens-before the constructor completing (freeze action) |
| Thread interrupt | `Thread.interrupt()` on a thread happens-before the interrupted thread detecting the interrupt |
| `LockSupport.unpark()` | A call to `unpark(t)` happens-before the park that returns in thread `t` |
| Object initialization | Default field initialization (to 0/null/false) happens-before the constructor `<init>` |
| Class initialization | Completion of class `<clinit>` happens-before any thread accesses static fields of that class |
| Transitivity | If A hb B and B hb C, then A hb C |

### 4.2 Memory Barrier Types

| Barrier | Effect | Java Construct |
|---------|--------|---------------|
| `LoadLoad` | Loads before the barrier complete before loads after | Volatile read (before subsequent loads) |
| `StoreStore` | Stores before the barrier complete before stores after | Volatile write (after preceding stores) |
| `LoadStore` | Loads before complete before stores after | Volatile read |
| `StoreLoad` | All stores before complete and flush before any subsequent load | Volatile write; `synchronized` exit |

**Critical mapping**:
- Volatile write = `StoreStore` + `StoreLoad` (the most expensive barrier)
- Volatile read = `LoadLoad` + `LoadStore`
- `synchronized` block entry = `LoadLoad` + `LoadStore` (acquire semantics)
- `synchronized` block exit = `StoreStore` + `StoreLoad` (release semantics)

### 4.3 Safe Publication Idioms (in order of preference)

| Idiom | Mechanism | When to Use |
|-------|-----------|-------------|
| Static initializer | Class `<clinit>` hb any thread access | Eager singleton, constant-like fields |
| `final` field in constructor | Freeze action after constructor | Immutable value objects |
| `volatile` field | Volatile write hb volatile read | Late-initialized mutable reference |
| Properly locked field | Monitor unlock hb monitor lock | Any synchronized state |
| `AtomicReference` | CAS + volatile semantics | Lock-free reference publishing |

---

## 5. Architecture Diagrams

### Happens-Before Graph — Volatile Flag Example
```
Thread A                          Thread B
---------                         ---------
x = 42                            (nothing yet)
  |
  | hb (program order)
  v
flag = true  [volatile write]
                |
                | hb (volatile write -> volatile read)
                v
              read flag  [volatile read, sees true]
                |
                | hb (program order)
                v
              read x  --> guaranteed to see 42

Without volatile on flag:
  No hb edge between volatile write and volatile read.
  Thread B may see flag == true but x == 0 (stale cache line).
```

### Final Field Freeze Action
```
Thread A (constructor)
---------
new SafePoint(3, 4):
  this.x = 3  [write final field x]
  this.y = 4  [write final field y]
     |
     | freeze action (happens when constructor exits normally)
     v
  ref = new SafePoint(3, 4)  [publish reference to safelyPublished field]

Thread B (any time after ref is visible)
---------
  read ref.x  --> guaranteed to see 3
  read ref.y  --> guaranteed to see 4

KEY: if ref is published unsafely (via non-final non-volatile field without sync),
     Thread B might see ref != null but x == 0 (partial construction).
```

### Memory Barrier Placement
```
Volatile Write:
  store(value)                   [StoreStore barrier: all prior stores committed]
  store(volatileField, value)
  [StoreLoad barrier: subsequent loads see committed stores from ALL threads]

Volatile Read:
  [LoadLoad barrier: subsequent loads won't see stale values]
  [LoadStore barrier: subsequent stores won't be reordered before this load]
  load(volatileField)

synchronized entry (monitorenter):
  [LoadLoad + LoadStore: acquire semantics — see all writes from thread that unlocked]

synchronized exit (monitorexit):
  [StoreStore + StoreLoad: release semantics — flush all writes before unlock]
```

### This-Escape — Construction Safety Risk
```java
// BROKEN: this escapes before construction is complete
class EventProcessor {
    private final int id;
    EventProcessor(EventBus bus) {
        bus.register(this);  // 'this' published to bus BEFORE id is set!
        this.id = 42;        // may not be visible to threads that got 'this' from bus
    }
}
// Thread B receives 'this' from the bus, reads id -> may see 0 (default value)
// Even though id is final, the freeze action hasn't completed when this escapes

// FIX: never publish 'this' (or pass to any method that might share it)
// inside a constructor before the constructor completes.
class EventProcessor {
    private final int id;
    EventProcessor(int id) { this.id = id; }
    public static EventProcessor create(EventBus bus, int id) {
        EventProcessor p = new EventProcessor(id);  // fully constructed
        bus.register(p);  // published after construction complete
        return p;
    }
}
```

---

## 6. How It Works — Detailed Mechanics

### Volatile Long on 32-bit JVM

```java
// On a 32-bit JVM, a 64-bit long/double write is NOT atomic by default.
// The JVM may split it into two 32-bit writes:
//   write high 32 bits, then write low 32 bits (two separate operations)
// Thread B reading the long may see the high bits of the new value
// and the low bits of the old value — a "word tear."

// BROKEN on 32-bit JVM:
long balance = 1_000_000_000L;   // Thread A writes new value
// Thread B reads: may see combination of old/new 32-bit halves

// FIXED: volatile long is guaranteed atomic even on 32-bit JVM
volatile long balance = 1_000_000_000L;
// The JMM requires volatile long/double reads and writes to be atomic.

// Note: on modern 64-bit x86, non-volatile long is naturally atomic
// (one 64-bit MOV instruction) — but this is NOT a JMM guarantee.
// Never rely on platform-specific behavior; use volatile.
```

### Broken and Fixed Safe Publication Patterns

```java
// BROKEN: unsafely published field (non-final, non-volatile, no sync)
class Unsafe {
    private int value;
    private List<String> items;

    void init() {
        this.items = new ArrayList<>();  // write
        this.items.add("hello");
        this.value = 42;                 // write
    }
}

Unsafe u = new Unsafe();
new Thread(() -> u.init()).start();
// Main thread later:
// u.value and u.items may be null/0 — no hb between the thread's write and main thread's read

// FIXED 1: Use final fields (safe publication via constructor freeze)
class Safe1 {
    private final int value;
    private final List<String> items;

    Safe1() {
        this.items = new ArrayList<>();
        this.items.add("hello");
        this.value = 42;
    }
}
// Any thread that gets the reference after constructor returns sees value=42 and items populated.

// FIXED 2: Use static initializer
class Safe2 {
    private static final Safe2 INSTANCE = new Safe2();  // initialized in <clinit>
    // Class <clinit> completion hb any thread accessing INSTANCE.
}

// FIXED 3: Use volatile
class Safe3 {
    private volatile Config config;

    void publish() {
        Config c = buildConfig();  // build locally
        this.config = c;           // volatile write — hb all subsequent reads
    }
}

// FIXED 4: Use synchronized
class Safe4 {
    private Config config;

    synchronized void publish() {
        config = buildConfig();
    }

    synchronized Config get() {
        return config;  // monitor unlock hb monitor lock — sees published config
    }
}
```

### Partially Constructed Singleton (DCL Without Volatile)

```java
// BROKEN: missing volatile allows the reference to be published
// before all fields are initialized
class Singleton {
    private static Singleton instance;  // NOT volatile
    private final int[] data;

    private Singleton() {
        this.data = new int[1000];  // heap allocation
        Arrays.fill(data, 99);      // initialization
        // JIT may reorder: assign instance ref BEFORE fill() completes
    }

    public static Singleton getInstance() {
        if (instance == null) {          // Thread B: sees non-null
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();  // JIT may reorder steps
                    // 1. allocate memory
                    // 2. instance = <ref>  ← JIT may move this BEFORE step 3
                    // 3. initialize data[] (fill 99s)
                }
            }
        }
        return instance;  // Thread B: gets ref but data may be partially filled
    }
}
// Thread B calls instance.data[500] -> sees 0 instead of 99 -> NPE or wrong result

// FIXED:
class Singleton {
    private static volatile Singleton instance;  // volatile prevents reordering
    // ... same code ...
    // volatile write on instance creates StoreStore + StoreLoad barriers,
    // ensuring all stores (data initialization) complete before instance is written.
}
```

### Data Race vs Race Condition

```java
// DATA RACE: two threads access same variable without hb, at least one writes
// No synchronization, no volatile, no atomic — plain shared variable
int balance = 100;  // shared field
// Thread A: balance += 50;  // read-modify-write (3 steps)
// Thread B: balance -= 30;  // read-modify-write (3 steps)
// JMM says: any result is valid — 120, 150, 70, even 100 (lost update)
// THIS IS UNDEFINED BEHAVIOR IN THE JMM.

// RACE CONDITION: timing-dependent logic bug — even with sync
AtomicInteger balance = new AtomicInteger(100);
// Thread A: if (balance.get() > 0) balance.decrementAndGet();
// Thread B: if (balance.get() > 0) balance.decrementAndGet();
// Both see balance=1, both decrement → balance = -1
// Each individual operation is atomic, but the CHECK-THEN-ACT is not.
// Fix: balance.updateAndGet(v -> v > 0 ? v - 1 : v);

// KEY DISTINCTION:
// Data race = JMM contract violation (can produce arbitrary results)
// Race condition = logical concurrency bug (produces wrong results, defined behavior)
```

### Sequentially Consistent Execution

```java
// SEQUENTIAL CONSISTENCY: a global total order of all operations
// such that each thread's operations appear in program order.

// JMM guarantees sequential consistency IF AND ONLY IF the program is DRF.
// (Data-Race-Free = no data races in any sequentially consistent execution)

// Example DRF program:
volatile int x = 0, y = 0;

// Thread A:  x = 1; read y;
// Thread B:  y = 1; read x;

// Sequentially consistent executions:
// Order 1: x=1, y=1, read y (1), read x (1)  -> both see 1
// Order 2: x=1, read y (1), y=1, read x (1)  -> Thread A sees 1, Thread B sees 1
// Order 3: y=1, x=1, read x (1), read y (1)  -> both see 1
// etc.
// NO execution can produce both reads seeing 0 — that would require
// x=1 to not be visible to Thread B's read of x, which violates hb via volatile.

// Without volatile (data race): JMM allows both reads to see 0.
// This is the SURPRISING result — proves why volatile is needed.
```

---

## 7. Real-World Examples

- **DCL without volatile (2001-2008 widespread bug)**: The JDK `EventQueue` in early Java was implemented with double-checked locking without `volatile`. On multiprocessor machines, callers received partially initialized `EventQueue` instances. The Java community FAQ at the time labeled it "correctly broken."
- **Tomcat `SessionManager`**: A `HashMap` used for session storage without synchronization caused infinite loops under concurrent modification (pre-Java 8 resize bug). Root cause: no happens-before between concurrent `put()` calls.
- **Android's ARM architecture exposure**: Many Android apps had bugs that only appeared on ARM devices (weaker memory model than x86). The same code ran perfectly on emulators (x86) but failed on phones. Root cause: data races that x86 tolerated (strong memory model) but ARM did not.

---

## 8. Tradeoffs

| Synchronization Tool | Establishes hb | Atomicity | Blocking | Overhead |
|---------------------|---------------|-----------|----------|----------|
| `volatile` | volatile write → subsequent read | No (for compound) | No | Low (memory barrier) |
| `synchronized` | unlock → subsequent lock | Yes (critical section) | Yes (blocks waiters) | Medium |
| `AtomicReference` | CAS (compare-and-swap) semantics | Yes (single var) | No (spin) | Low-medium |
| `final` field | freeze action in constructor | Yes (immutability) | No | Zero at runtime |
| `Thread.join()` | join return → any action after | N/A | Yes | N/A |

---

## 9. When to Use / When NOT to Use

**Use `volatile`** when: one thread writes, others read; no compound read-modify-write operations; you need a visible flag or reference.

**Use `synchronized`** when: compound operations (check-then-act, read-modify-write) on shared mutable state; you need a critical section where multiple related variables must be consistent together.

**Use `final` fields** for immutability: constructors that assign all fields as `final` get the freeze action guarantee — the safest publication with zero runtime overhead.

**Do NOT rely on** the x86 strong memory model. x86 naturally provides most ordering guarantees that other architectures don't. Code that works on x86 without `volatile` may fail on ARM. Always write to the JMM specification, not to observed behavior on one architecture.

**Do NOT mix** locked and non-locked access to the same variable. If a variable is sometimes accessed under a lock and sometimes without, the non-locked access is a data race even if the lock is acquired the other 99% of the time.

---

## 10. Common Pitfalls

### War Story 1: Visibility failure — loop never terminates
```java
// BROKEN: 'running' is not volatile
private boolean running = true;

// Thread A:
while (running) { /* work */ }

// Thread B:
running = false;  // Thread A may NEVER see this change
                  // JIT may cache 'running' in register — infinite loop
```
This is the most common JMM visibility bug. The JIT is allowed to hoist the `running` read out of the loop into a register. **Fix**: `private volatile boolean running = true;`

### War Story 2: `this`-escape from constructor breaks `final` guarantees
A service published `this` to a static `List<Service>` inside its constructor before initializing its `final` configuration fields. Worker threads reading from the list saw the service with `null` configuration even though the field was `final`. The freeze action on `final` fields happens when the constructor completes — publishing `this` before then means no freeze action has occurred yet. **Fix**: Factory method pattern: construct fully, then publish.

### War Story 3: Stale cache entry despite volatile flag
A caching layer used `volatile boolean valid = false` as a flag and a non-volatile `String cachedValue`. The flag was set last: `cachedValue = compute(); valid = true;`. A reader checked `if (valid) return cachedValue;`. On ARM, the reader saw `valid == true` but `cachedValue == null` — because the write to `cachedValue` was reordered after the write to `valid`. **Fix**: `cachedValue` must also be `volatile`, OR use `synchronized`, OR publish as an atomic unit via `AtomicReference<String>`. The `volatile` write to `valid` only creates a hb edge for its own write, not for all preceding non-volatile writes on weaker architectures.

### War Story 4: Partially visible `long` on 32-bit JVM
A financial system running on a 32-bit legacy JVM had a shared `long balance` field (not volatile). Under high concurrency, balance occasionally showed bizarrely wrong values (billions) that were in fact high/low word combinations from different writes by two threads. **Fix**: `volatile long balance` — the JMM requires volatile 64-bit operations to be atomic.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `-XX:+PrintSafepointStatistics` | See safepoint pauses (related to memory barrier costs) |
| `jcstress` (OpenJDK) | JMM litmus tests — verify correctness of concurrent algorithms |
| async-profiler | CPU profiling to detect unexpected barrier costs |
| `-XX:+TraceMemoryBarriers` (debug JVM) | Show barrier insertions — diagnostic only |
| ThreadSanitizer (via native agent) | Detect data races in native code |
| `VarHandle` (Java 9+) | Fine-grained memory ordering: plain, opaque, acquire, release, volatile modes |

---

## 12. Interview Questions with Answers

**Q1: List all the happens-before edges in the JMM.**
(1) Program order: each action in a thread hb every subsequent action in that thread. (2) Monitor unlock hb subsequent lock of same monitor. (3) Volatile write hb subsequent volatile read of same field. (4) `Thread.start()` hb any action in started thread. (5) Any action in thread hb `Thread.join()` returning. (6) Final field freeze: write to final field in constructor hb constructor completing (freeze action). (7) `Thread.interrupt()` hb interrupted thread detecting interrupt. (8) `LockSupport.unpark(t)` hb park returning in thread t. (9) Default initialization hb constructor. (10) Class `<clinit>` completion hb any thread accessing static fields. (11) Transitivity.

**Q2: Why are `final` fields safe without synchronization after the constructor completes?**
The JMM defines a "freeze action" for `final` fields: when a constructor completes (without this-escape), all writes to `final` fields in that constructor are frozen and published. Any thread that reads the reference of the fully constructed object is guaranteed to see all final fields at their initialized values — even without any synchronization. This is why immutable objects are thread-safe: all their state is in `final` fields, established before any thread can read them. The guarantee breaks if the constructor publishes `this` before completing — the freeze action hasn't happened yet.

**Q3: What is a data race vs a race condition?**
A data race is a specific JMM-level violation: two concurrent accesses to the same variable where at least one is a write, and there is no happens-before ordering between them. Data races put the program in undefined behavior territory — the JMM makes no guarantees. A race condition is a logical concurrency bug: the program produces incorrect results due to timing dependencies, even though operations may individually be atomic. Example: two threads both check `if (balance > 0)` and both decrement — each decrement is atomic, but the check-then-act as a whole is not. Race conditions can exist even in perfectly synchronized code (though they're harder to create).

**Q4: What does "sequentially consistent" mean and when does the JMM guarantee it?**
Sequential consistency means: there exists a total ordering of all operations across all threads such that (a) each thread's operations appear in their program order, and (b) each read sees the most recent write in that total order. It's the simplest model to reason about — the program behaves as if all operations happened one at a time in some global order. The JMM guarantees sequential consistency for all programs that are data-race-free (DRF). If a program has data races, the JMM does NOT guarantee sequential consistency — it may produce results that no sequential execution could produce.

**Q5: Why is `volatile long` needed on 32-bit JVMs?**
The JMM explicitly states that non-volatile reads and writes of `long` and `double` fields on 32-bit platforms can be treated as two separate 32-bit operations. This means a 64-bit write from Thread A could be split: Thread B reads the high 32 bits from the new value and the low 32 bits from the old value — producing a completely incorrect 64-bit result ("word tear"). `volatile long` is required to guarantee atomicity of 64-bit operations. On 64-bit x86, non-volatile long is atomically written by hardware (single 64-bit MOV), but this is not a JMM guarantee and should not be relied upon for portability.

**Q6: Can `final` fields be observed in inconsistent state? Explain the `this`-escape caveat.**
Yes — if the constructor publishes `this` before completing. The freeze action for `final` fields occurs when the constructor exits normally. If the constructor calls a method on another object (or registers `this` with an observer) before setting all `final` fields, that other object can see `final` fields at their default values (0 or null). This is called "this-escape from constructor." Example: `EventListener(EventBus bus) { bus.register(this); this.id = 42; }` — any listener receiving this reference before the constructor finishes sees `id == 0`. Fix: use a static factory method that constructs the object completely before publishing.

**Q7: What is the freeze action?**
The freeze action is the JMM mechanism for final field safety. It occurs at the end of the constructor (or just before it exits, assuming no this-escape). The freeze action creates a happens-before edge from all writes to `final` fields in the constructor to any subsequent read of those fields by any thread. This is what makes final fields safe without synchronization: after the freeze action, the initialized values of final fields are visible to all threads. Normal (non-final) fields in the same constructor are NOT covered by the freeze action.

**Q8: What is the difference between memory visibility and instruction ordering?**
Memory visibility: whether a write by one thread is visible to a read by another thread. Caused by CPU caches — a write may be buffered in the writer's cache and not flushed to shared memory. Instruction ordering: the order in which a thread executes its own instructions — the JIT compiler and CPU can reorder instructions for optimization. Both problems are addressed by happens-before. A happens-before edge provides both: it ensures (1) all writes up to the edge point are flushed to shared memory (visibility), and (2) all those writes appear in order relative to the synchronized read (ordering). `volatile` provides both; the x86 TSO memory model provides strong ordering but weak visibility guarantees without barriers.

**Q9: What does `synchronized` guarantee beyond mutual exclusion?**
`synchronized` guarantees three things: (1) Mutual exclusion — only one thread holds the monitor at a time. (2) Visibility — all writes performed before releasing the lock are visible to any thread that subsequently acquires the same lock. This is the monitor unlock → subsequent lock hb edge. (3) Ordering — actions before the monitor exit are not reordered to appear after it, and actions after monitor entry are not reordered to appear before it (acquire/release semantics). So `synchronized` also acts as a memory barrier: reading a synchronized field is guaranteed to see the most recent synchronized write, even on weakly-ordered architectures.

**Q10: What is `VarHandle` and how does it provide finer-grained memory ordering than `volatile`?**
`VarHandle` (Java 9, `java.lang.invoke.VarHandles`) provides field access with explicit memory ordering modes: (1) Plain — no ordering guarantees, same as non-volatile access. (2) Opaque — no cross-variable ordering but each individual access is atomic. (3) Acquire/Release — one-way barriers: acquire (read) sees all writes by the release (write), but without the full StoreLoad barrier of volatile. (4) Volatile — full volatile semantics (most expensive). This lets lock-free algorithms pay only for the barriers they actually need — acquire/release is sufficient for a producer-consumer handoff but much cheaper than full volatile StoreLoad on most architectures.

**Q11: What are the safe publication idioms in order of preference?**
(1) Static field initialized in declaration (`static final Foo f = new Foo()`) — class `<clinit>` provides hb for all threads, zero runtime overhead. (2) `final` fields in constructor — freeze action guarantees visibility; works for immutable objects. (3) `volatile` field — volatile write hb subsequent read; suitable for mutable references. (4) Properly locked field — monitor unlock hb subsequent lock; necessary when multiple related fields must be consistent. (5) `AtomicReference` — CAS-based, `volatile` semantics on the reference. Order reflects preference: most restrictive (static final) provides strongest guarantees and is hardest to misuse.

**Q12: What is the StoreLoad barrier and why is volatile write the most expensive operation?**
The `StoreLoad` barrier ensures that all stores before the barrier complete and become visible to all processors before any subsequent load executes. It is the only barrier that prevents store-load reordering — the most significant optimization a processor can do. On x86, `StoreLoad` requires an `MFENCE` or a locked instruction, which forces a full memory serialization point — all store buffers are flushed. This is why volatile write is expensive: it requires `StoreStore` (flush preceding stores) + `StoreLoad` (flush this store and prevent subsequent loads from reordering before it). Volatile read requires only `LoadLoad` + `LoadStore`, which are effectively free on x86.

---

## 13. Best Practices

1. **Write DRF (Data-Race-Free) programs** — it's the only way to get the JMM's sequential consistency guarantee.
2. **Never read a shared mutable variable without a happens-before edge** — even "just a read" of a non-volatile long is a potential torn read.
3. **Prefer immutable objects (all-final fields)** for shared state — zero synchronization overhead, strongest safety guarantee.
4. **Use `volatile` only for simple flag/reference visibility** — not for compound operations like `i++`.
5. **Use `synchronized` (or `ReentrantLock`) for compound operations** on shared mutable state.
6. **Never publish `this` inside a constructor** — freeze action hasn't occurred yet.
7. **Use `AtomicReference` for single-reference safe publication** in lock-free code.
8. **Be explicit about ordering modes in lock-free code** — document which hb edges you're relying on.
9. **Test on ARM or use `jcstress`** — x86's strong model can hide JMM violations that appear on ARM (most mobile/cloud hardware).
10. **Prefer `final` fields over `volatile` for immutable data** — `volatile` has runtime barrier cost; `final` has zero runtime cost after construction.

---

## 14. Case Study

### Safe Publication of a Configuration Cache Across Threads

**Scenario.** A trading service hot-reloads its configuration without restarting. A single background **Thread A** (the reloader) builds a fresh immutable `Config` snapshot every 30 seconds from a config server; **dozens of request-handling threads (B)** read the current config on every request — roughly **40k reads/sec**. The naive first version stored the reference in a plain field; under load on ARM hardware, request threads occasionally saw a `Config` object whose reference was visible but whose **fields were still default/zero** — a half-constructed object — causing intermittent "0% margin" mispricings that vanished on retry. The root cause and the fix are pure Java Memory Model.

```
   Thread A (reloader, every 30s)            Threads B (40k reads/sec)
   --------------------------------          --------------------------
   Config c = new Config(...)   // (1)        Config snap = configRef;  // (4)
   // fields written            // (2)        use snap.marginRate()      // (5)
   configRef = c;  // volatile  // (3)
        |                                          ^
        | volatile write (3) -- happens-before --> volatile read (4)
        +------------------------------------------+
   So (1)+(2) [before the write in program order] are visible at (5). SAFE.
```

### Broken: Plain Reference Lets a Half-Constructed Object Escape

```java
// BROKEN — non-volatile reference. The JMM permits the publishing write to
// configRef to become visible to other threads BEFORE the writes to the new
// Config's fields. Reader sees a non-null reference but zeroed fields.
class ConfigCache {
    private Config configRef = Config.defaults();   // plain field

    void reload(Map<String,String> raw) {
        configRef = new Config(raw);   // construction + publication NOT ordered for readers
    }
    Config current() { return configRef; }           // may return a partly-built object
}
```

The constructor writes (`this.marginRate = ...`) and the publishing write (`configRef = ...`) have no happens-before edge to the reader, so a reader thread may observe them out of order and read `marginRate == 0.0`.

### Fix: `volatile` Reference Gives Safe Publication

```java
// FIX — volatile reference. A volatile WRITE happens-before every subsequent
// volatile READ of the same field. Everything the writer did before the write
// (the whole constructor) is therefore visible to any reader that sees the new ref.
class ConfigCache {
    private volatile Config configRef = Config.defaults();   // volatile

    void reload(Map<String,String> raw) {
        Config c = new Config(raw);   // (1) fully constructed first
        configRef = c;                // (2) volatile publish: hb for all readers
    }
    Config current() { return configRef; }   // sees a fully-built Config, always
}
```

### Double-Checked Locking: Broken Without `volatile`, Then Fixed (Item 83)

The same hazard underlies the classic DCL singleton.

```java
// BROKEN — DCL without volatile. Another thread can see a non-null `instance`
// whose fields are not yet written (reordered construction). Java 5+ requires volatile.
class Lazy {
    private static Lazy instance;                 // missing volatile
    static Lazy get() {
        if (instance == null) {
            synchronized (Lazy.class) {
                if (instance == null) instance = new Lazy(); // publish may precede init
            }
        }
        return instance;
    }
}
```

```java
// FIX — volatile makes the second read safe; or just use the simpler idioms below.
class Lazy {
    private static volatile Lazy instance;        // Java 5+ DCL is correct WITH volatile
    static Lazy get() {
        Lazy r = instance;                        // single volatile read
        if (r == null) {
            synchronized (Lazy.class) {
                r = instance;
                if (r == null) instance = r = new Lazy();
            }
        }
        return r;
    }
}

// SIMPLER: enum singleton needs no volatile, no locking, and is the preferred form.
enum Cfg { INSTANCE; private final Config config = Config.defaults(); }
```

### Concrete Numbers (modern x86-64)

| Operation | Approx cost | Why |
|-----------|-------------|-----|
| non-volatile read | ~0.3 ns | L1 cache hit, no fence |
| `volatile` read/write | ~5-10 ns | memory fence / no reorder across it |
| uncontended `synchronized` block | ~20-50 ns | monitor enter/exit |

At 40k reads/sec the `volatile` read cost is ~0.4 ms/sec total — negligible against eliminating mispricing incidents.

### Common Pitfalls

**`volatile` on an array reference does not protect the elements.** `volatile Config[] arr` makes the *reference* visible, but a write to `arr[3].field` has no volatile semantics. Use `AtomicReferenceArray` or publish a fresh immutable array on each update.

**64-bit `long`/`double` reads/writes are non-atomic on 32-bit JVMs.** Without `volatile`, a 32-bit JVM may tear a `long` into two 32-bit halves, so a reader can see a mix of old high bits and new low bits. Declaring the field `volatile` guarantees atomicity for `long`/`double` even on 32-bit.

**Misunderstanding happens-before transitivity.** Developers assume "Thread A wrote X before Y, so a reader sees both" — but without a release/acquire edge (volatile write paired with volatile read, lock release/acquire, or `final` field publication) there is no ordering at all between threads. The edge must be explicit.

**Forgetting that `final` fields are safely published without `volatile` (Java 5+).** A correctly constructed object whose state is all `final` is visible to other threads after the constructor finishes, even if the reference itself is published non-volatilely — provided `this` did not escape during construction. This is why immutable objects are the cheapest safe-publication mechanism.

### Interview Discussion Points

**What exactly does a `volatile` write guarantee for the reader?** It establishes a happens-before edge: every action the writing thread performed *before* the volatile write (in program order) is visible to any thread that *subsequently* performs a volatile read of the same field — which is why publishing a fully-constructed object through a volatile reference is safe.

**Why does plain DCL break, and why does `volatile` fix it?** Object construction and the publishing assignment can be reordered so another thread sees a non-null reference before the constructor's field writes are visible; `volatile` forbids that reordering and supplies the happens-before edge, making the second null check observe a fully-initialized object.

**When can you publish an object safely without `volatile`?** When all its fields are `final` and `this` does not escape during construction, the JMM's final-field semantics (Java 5+) guarantee visibility after the constructor completes; immutable objects exploit exactly this, costing nothing at read time.

**Is `volatile` enough to make a counter increment thread-safe?** No. `count++` is read-modify-write, three operations; `volatile` makes each read and write visible but does not make the trio atomic, so two threads can lose an update. Use `AtomicInteger`/`AtomicLong` or a lock.

**Why is the enum singleton preferable to a volatile DCL singleton?** It is initialized once by the classloader with happens-before guarantees and no application-level synchronization, it cannot be duplicated by reflection or deserialization, and it removes the subtle volatile/reordering reasoning entirely — strictly less to get wrong.
