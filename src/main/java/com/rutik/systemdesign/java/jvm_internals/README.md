# JVM Internals

## 1. Concept Overview

The Java Virtual Machine is the invisible engine under every Java program. It manages memory through garbage collection, compiles hot bytecode to native machine code (JIT), enforces the Java Memory Model to give multithreaded programs well-defined behavior, and loads/links/initializes classes dynamically.

Understanding JVM internals is what separates engineers who can explain *why* a production system is slow or leaking memory from those who can only guess. It's also a major differentiator in senior Java interviews: GC pauses, memory leaks, JIT deoptimization, volatile semantics — all are JVM-level phenomena.

---

## 2. Intuition

> **One-line analogy**: The JVM is a city infrastructure — the heap is real estate, GC is the demolition crew that clears unused buildings, the JIT compiler is the contractor who paves gravel roads into highways for the most-traveled routes, and the memory model is the zoning law that governs who can build what where.

**Mental model**: Every Java object lives in the heap. The JVM tracks which objects are reachable from live references (GC roots: stacks, static fields, JNI). Objects not reachable are garbage. GC algorithms differ in how they identify, move, and reclaim garbage — with tradeoffs between throughput (doing this rarely), latency (doing this fast), and memory overhead.

**Why it matters**: A 10-second GC pause in a financial system is a $100K+ incident. A memory leak that only appears after 12 hours in production is debuggable only with heap dumps. JIT deoptimization causing throughput regression after a code deploy is traced with async-profiler flame graphs. These are real scenarios where JVM knowledge is business-critical.

**Key insight**: The Java Memory Model (JMM) is a formal specification of how thread interactions work — it defines *happens-before* relationships that guarantee visibility and ordering. Violating it (e.g., double-checked locking without `volatile`) produces bugs that appear only in production under heavy load on multi-core systems.

---

## 3. Core Principles

- **Memory areas**: Heap (objects), stack (frames), metaspace (class metadata), PC register, native method stacks.
- **Object layout**: Every object has a mark word (8B) + class pointer (4 or 8B) + fields + padding to 8-byte alignment.
- **GC roots**: Thread stacks, static fields, JNI references — the roots of the reachability graph.
- **Generational hypothesis**: Most objects die young. Young generation collected frequently (minor GC), old generation collected rarely (major GC).
- **JIT compilation**: Hotspot interprets bytecode initially; hot methods are compiled to native code (C1, C2 tiers).
- **Happens-before (JMM)**: The only formal guarantee of inter-thread visibility. No happens-before = data race = undefined behavior.
- **Safe publication**: An object is safely published if its construction is complete before other threads see the reference.

---

## 4. Types / Architectures / Strategies

### 4.1 JVM Memory Areas

| Area | Contents | GC'd | Per-thread? |
|------|----------|------|-------------|
| Eden space | New objects (TLAB allocation) | Yes (minor GC) | No |
| Survivor S0/S1 | Objects surviving minor GC | Yes (minor GC) | No |
| Old generation | Long-lived objects (tenure threshold) | Yes (major/full GC) | No |
| Metaspace | Class metadata, interned strings (Java 8+) | On class unload | No |
| Stack | Method frames, local vars, operand stack | No (frame-based) | Yes |
| PC register | Current bytecode position | No | Yes |
| TLAB | Thread-local allocation buffer (sub-region of Eden) | Indirectly | Yes |

### 4.2 GC Algorithm Comparison

| GC | Default | Pause Type | Best For |
|----|---------|-----------|---------|
| Serial GC | -XX:+UseSerialGC | Stop-the-world (single thread) | Single-core, small heap |
| Parallel GC | Java 8 default | Stop-the-world (multi-thread) | High throughput batch |
| G1 GC | Java 9+ default | Concurrent marking + STW evacuation | Balanced latency/throughput |
| ZGC | -XX:+UseZGC (Java 15 GA) | Sub-millisecond STW | Ultra-low latency |
| Shenandoah | -XX:+UseShenandoahGC | Concurrent compaction | Low latency (alternative to ZGC) |

### 4.3 JIT Compilation Tiers

| Tier | Compiler | Trigger | Notes |
|------|---------|---------|-------|
| 0 | Interpreter | All code | Initial execution |
| 1 | C1 (client) | ~1K invocations | Fast compile, basic opts |
| 2 | C1 + profiling | ~2K invocations | Adds type profile collection |
| 3 | C1 + full profiling | ~15K invocations | Full profiling for C2 |
| 4 | C2 (server) | ~15K invocations | Aggressive optimization (inlining, escape analysis) |

---

## 5. Architecture Diagrams

### Heap Layout (G1 GC)
```
Traditional Heap:        G1 Heap:
+----------+            +--+--+--+--+--+--+--+
| Young:   |            |E |S |O |E |H |O |E |  Each region = 1-32MB
|  Eden    |            +--+--+--+--+--+--+--+
|  S0  S1  |            |O |E |S |E |O |H |S |  E=Eden, S=Survivor
+----------+            +--+--+--+--+--+--+--+  O=Old, H=Humongous
| Old Gen  |            Humongous = object > region/2
+----------+

G1 Mixed GC = Young collection + subset of old regions with most garbage
Target pause: -XX:MaxGCPauseMillis=200 (default)
```

### Object Memory Layout
```
+----------------+
| Mark Word (8B) |  GC age (4 bits), lock state, hash code, GC forwarding ptr
+----------------+
| Class Ptr (4B) |  Compressed oops (default): pointer to Klass in metaspace
+----------------+
| Field 1 (4B)   |  int field
+----------------+
| Field 2 (8B)   |  long field (must align to 8B boundary)
+----------------+
| Padding (4B)   |  pad to make total size multiple of 8
+----------------+
Total: 8+4+4+8+4 = 28B -> padded to 32B
```

### GC Tri-Color Marking (Concurrent GC)
```
WHITE = not yet visited (candidate for collection)
GRAY  = visited, but references not yet scanned
BLACK = visited, all references scanned (definitely live)

Initial: all objects WHITE
Phase 1 (STW): mark GC roots GRAY
Phase 2 (concurrent): scan GRAY objects:
  - mark referenced objects GRAY
  - mark self BLACK
Phase 3: any remaining WHITE objects are garbage

Problem: mutator thread assigns black -> white reference during concurrent phase
Solution: write barrier (incremental update OR snapshot-at-the-beginning)
G1 uses post-write barrier (incremental update)
ZGC uses load barrier (colored pointers)
```

### Java Memory Model — Happens-Before Edges
```
Thread A                    Thread B
write x = 42
volatile write flag = true
                            volatile read flag (sees true) [hb edge here]
                            read x  (guaranteed to see 42)

Happens-before edges:
1. Program order: each action hb the next in same thread
2. Monitor: unlock hb subsequent lock of same monitor
3. Volatile: volatile write hb subsequent volatile read
4. Thread start: Thread.start() hb any action in started thread
5. Thread join: any action in thread hb Thread.join() return
6. Constructor: object construction hb finalize() of same object
```

---

## 6. How It Works — Detailed Mechanics

### TLAB (Thread-Local Allocation Buffer)

Each thread has a private TLAB — a chunk of Eden pre-allocated for that thread's exclusive use. Object allocation inside a TLAB is a simple pointer bump (≈ 2 instructions, nearly free). When TLAB is full, a new one is requested. This eliminates synchronization for the common case. TLAB size is adaptive; tunable with `-XX:TLABSize`.

```
Without TLAB:
  Allocation = synchronized access to Eden top pointer
  High contention under many threads

With TLAB:
  Thread-local Eden region
  Allocation = ptr++  (no synchronization)
  Full TLAB -> get new one from shared Eden
```

### G1 GC Mechanics

```
1. Young-only phase:
   - Evacuate Eden + Survivors to new Survivor/Old regions
   - Stop-the-world pause (evacuation pause) — typically 10-200ms

2. Concurrent marking cycle (when heap % > threshold):
   - Initial mark (STW, piggybacked on evacuation)
   - Root region scanning (concurrent)
   - Concurrent marking (concurrent — tri-color)
   - Remark (STW — drain SATB buffers)
   - Cleanup (concurrent + STW — reclaim empty regions)

3. Mixed GC phase:
   - Evacuate Eden + Survivors + selected old regions
   - Old regions selected by "liveness" — most garbage first
   - Continues until heap stabilized

Humongous objects: objects > region_size/2 go directly to contiguous old regions
Remembered sets: each region tracks references from OTHER regions (for evacuation)
Card tables: 512-byte cards; dirty card = potential cross-region reference
```

### ZGC — Sub-Millisecond Pauses

```
Key innovation: colored pointers + load barriers
- 42-bit address + 22 bits for metadata (remapped/finalizable/marked flags)
- Load barrier: every object reference READ checks the color bits
- If pointer stale (pre-relocation), update to new address on-the-fly
- Relocation is concurrent — mutator sees consistent view via load barrier

STW pauses: only initial mark and remark (a few ms for large heaps)
Concurrent: mark, relocate, remap — all happen while app runs
Practical result: sub-1ms pauses on 1TB heaps
```

### Class Loading: Load → Link → Initialize

```
1. Load: find bytecode (filesystem/network/JAR), create Class object in metaspace
   Bootstrap ClassLoader: rt.jar / JDK modules
   Platform ClassLoader: Java SE APIs not in bootstrap
   App ClassLoader: classpath JARs

   Parent delegation: check parent first; if not found, load self

2. Link:
   a. Verify: bytecode safety (type safety, stack overflow checks)
   b. Prepare: allocate memory for static fields, set to defaults (0/null/false)
   c. Resolve: resolve symbolic references to direct references (optional)

3. Initialize:
   - Execute <clinit> (static initializer blocks + static field assignments)
   - Thread-safe: JVM guarantees at-most-once execution
   - Lazy: only when class first actively used (first instance, static call, etc.)

OutOfMemoryError: Metaspace -> too many class definitions (common in dynamic proxies, CGLIB)
```

### Double-Checked Locking — Broken vs Fixed

```java
// BROKEN: no volatile — object can be partially initialized
private static Singleton instance;
public static Singleton getInstance() {
    if (instance == null) {           // Thread A: sees non-null but partially init'd
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Singleton();  // Thread B: 3 steps:
                // 1. allocate memory
                // 2. initialize fields
                // 3. assign to instance
                // JIT can reorder: 1 -> 3 -> 2 (assign before init complete)
            }
        }
    }
    return instance;
}

// FIXED: volatile ensures write happens-before read
private static volatile Singleton instance;
// volatile forbids reordering of write to instance across field initialization
```

### Escape Analysis and Stack Allocation

```java
// JIT can allocate on STACK (not heap) if object doesn't escape method
void process() {
    Point p = new Point(1, 2);  // JIT may stack-allocate
    int result = p.x + p.y;     // p never escapes; no reference stored outside
}                                // p freed with stack frame — zero GC overhead

// Scalar replacement: JIT may further eliminate the object entirely
void process() {
    int px = 1, py = 2;  // fields extracted to local variables
    int result = px + py;
}
```

### Safepoints — Definition and TTSP Latency

```
A safepoint is a point in program execution where the JVM can safely pause ALL Java threads
for operations that require a consistent heap state:
  - Stop-the-world GC pauses
  - Deoptimization
  - Class redefinition (via JVMTI)
  - Heap dump (jmap)
  - Thread dump (jstack)
  - Biased lock revocation

How it works:
  JVM requests a safepoint -> sets a "safepoint flag" in a polling page
  Each thread polls the page at safepoint poll points (method calls, loop back edges, etc.)
  When thread sees the flag: saves state, blocks at safepoint
  JVM waits until ALL threads are at a safepoint ("TTSP" = Time To Safepoint)
  Performs STW operation -> releases all threads

TTSP latency problem:
  A thread in a tight long loop (e.g., counting to 1 billion with no method calls)
  may not reach a safepoint poll for millions of instructions
  -> GC pause = TTSP + actual pause time
  -> On older JVMs: a single spinning thread could add 50-100ms to "pauses"

Mitigation:
  Java 10+: JEP 312 "Thread-Local Handshakes" — safepoint can target individual threads
  Java 14+: improved polling in counted loops
  Diagnostic: -XX:+PrintSafepointStatistics (Java 8-11) or -Xlog:safepoint (Java 9+)
```

### Biased Locking (Removed in Java 21)

```
Biased locking was an optimization where the JVM stored the locking thread's ID
in the object's mark word. A subsequent lock() by the SAME thread required only
a mark word check — no CAS, no memory barrier, essentially free.

Object header mark word in biased state:
+--[thread ID : 54 bits]--[epoch : 2]--[bias flag : 1]--[lock state : 2]--+
                                                           ^1 = biased

When a DIFFERENT thread tries to lock:
  JVM must "revoke" biased lock — requires stopping the owning thread at a safepoint
  Revocation is expensive (STW); biased locking saved time only when one thread held long

Why removed in Java 21 (JEP 374):
  Modern multi-threaded apps have many short-lived locks with multiple threads
  Contention is now common -> frequent revocations -> safepoint storms
  CAS (compare-and-swap) for thin locks is fast enough on modern hardware
  Benefit no longer exceeded the complexity and safepoint overhead cost

Effect of removal:
  Benchmarks show ~0.3% performance change (negligible)
  Safepoint pauses become more predictable (fewer revocation safepoints)
  Startup slightly faster (no biased locking initialization)
```

### Memory Barrier Types Mapped to Java Constructs

```
Four barrier types (prevent CPU reordering across the barrier):

  LoadLoad  barrier: loads before barrier cannot be reordered with loads after
  StoreStore barrier: stores before cannot be reordered with stores after
  LoadStore  barrier: loads before cannot be reordered with stores after
  StoreLoad  barrier: stores before must complete AND be visible to all CPUs
                      before any subsequent load executes
                      (most expensive: requires memory serialization, MFENCE on x86)

Java construct → barrier mappings:

  volatile write:
    [StoreStore]  // all prior stores committed before this write
    store(volatileField)
    [StoreLoad]   // this write visible to all, no subsequent load can float above

  volatile read:
    [LoadLoad]    // no subsequent load can float above this read
    load(volatileField)
    [LoadStore]   // no subsequent store can float above this read

  synchronized exit (monitorexit):
    [StoreStore]  // all writes in critical section flushed
    monitorexit
    [StoreLoad]   // release: subsequent locks in other threads see all writes

  synchronized entry (monitorenter):
    [LoadLoad]    // acquire: see all writes from thread that did monitorexit
    [LoadStore]   // subsequent stores not reordered before the acquire

  final field (in constructor):
    [StoreStore before freeze action]  // ensures all final field writes visible
    // freeze action after constructor completes
    [StoreLoad]  // reference write visible only after freeze
```

---

## 7. Real-World Examples

- **G1 GC pause spike**: A financial trading system saw 3-second GC pauses during market open. Investigation: large humongous objects (>32MB) being allocated repeatedly, causing fragmentation and full GC. Fix: `-XX:G1HeapRegionSize=32m` to prevent humongous classification.
- **Metaspace OOM**: A hot-deploy application kept redeploying wars without restarting the JVM. Each deploy loaded new class versions but old classloaders weren't GC'd (ClassLoader leak via thread-local or static reference). Fix: `jmap -clstats` to count classloaders; hunt the reference chain.
- **JIT deoptimization**: After a new version deployed, latency spiked for 2 minutes then recovered. Cause: JIT had compiled a method based on a type profile (only `ConcreteA` seen); new version introduced `ConcreteB` causing deoptimization and recompilation. Profiling with async-profiler confirmed.

---

## 8. Tradeoffs

| GC | Throughput | Latency | Overhead | When to Use |
|----|-----------|---------|----------|-------------|
| Serial | Low | High | Low | Tiny JVMs |
| Parallel | High | Medium (100ms-2s pauses) | Low | Batch processing |
| G1 | High | Low (10-200ms) | Medium | General use (default) |
| ZGC | High | Ultra-low (<1ms) | Higher (colored pointers overhead) | Latency-critical |
| Shenandoah | High | Ultra-low | Higher | Alternative to ZGC |

---

## 9. When to Use / When NOT to Use

**Use ZGC when**:
- Service has strict latency SLAs (sub-10ms P99)
- Large heaps (>32GB)
- Java 15+ available

**Use G1 (default) when**:
- General-purpose server application
- Heap 4GB–64GB
- Can tolerate 10-200ms pauses

**Use Parallel GC when**:
- Batch processing where throughput matters, not latency
- Can tolerate long STW pauses

**Do NOT tune GC blindly**: Always measure with GC logs before and after. `-Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=20m`

---

## 10. Common Pitfalls

### War Story 1: ThreadLocal leak in thread pools
A servlet used `ThreadLocal` to store a `Connection`. When the thread was returned to the pool, `ThreadLocal.remove()` was not called. Over days, each thread held an open database connection permanently. The pool grew to 50 threads, each with a leaked connection, exhausting the DB connection pool. **Fix**: Always call `threadLocal.remove()` in a `finally` block when using `ThreadLocal` with pooled threads.

### War Story 2: `finalize()` causing long GC pauses
A legacy class implemented `finalize()` for cleanup. The JVM processes finalizable objects in a special finalizer thread — they survive one extra GC cycle (put in finalizer queue), delaying their collection. Under load, the queue backed up, old gen filled, and full GC was triggered repeatedly. **Fix**: Remove `finalize()`; use `try-with-resources` and `Cleaner` instead.

### War Story 3: OutOfMemoryError: Metaspace in dynamic proxy heavy code
A service using heavy reflection-based frameworks (Hibernate, Spring proxies) ran for 2 weeks then crashed with `OutOfMemoryError: Metaspace`. Each CGLIB proxy class was loaded into a classloader that was never GC'd. **Fix**: Set `-XX:MaxMetaspaceSize=512m` to cap it and trigger OOME earlier for diagnostics; investigate classloader leaks with `jmap -clstats`.

### War Story 4: Volatile not preventing reordering in DCL
A developer "fixed" DCL by adding `synchronized` on the inner check but not making the field `volatile`. The outer check without synchronization could see a non-null but partially constructed object due to JIT reordering. This only manifested on multi-core x86 under specific compiler optimization passes. **Fix**: The field must be `volatile` — volatile write establishes a happens-before with the volatile read.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `jmap -dump:format=b,file=heap.dmp` | Take heap dump |
| Eclipse MAT | Heap dump analysis (dominator tree, leak suspects) |
| `jstack <pid>` | Thread dump — identify deadlocks, BLOCKED threads |
| `jstat -gcutil <pid> 1s` | Live GC stats |
| `-Xlog:gc*:file=gc.log` | Structured GC logging (Java 9+) |
| async-profiler | CPU/allocation flame graphs, wall-clock profiling |
| VisualVM / JConsole | Live JVM monitoring |
| `-XX:+PrintCompilation` | See JIT compilation events |
| `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` | See JIT inlining decisions |

---

## 12. Interview Questions with Answers

**Q1: What is a TLAB and why does it exist?**
Thread-Local Allocation Buffer: a private sub-region of Eden space allocated to each thread. Object allocation inside the TLAB is a simple pointer bump — O(1) with no synchronization. Without TLAB, every allocation would require synchronized access to the shared Eden pointer, creating contention under many threads. When a TLAB is exhausted, the thread requests a new TLAB from shared Eden (synchronized, but rare). TLAB size adapts based on allocation rate.

**Q2: Describe G1 GC regions and how mixed GC works.**
G1 divides the heap into equal-sized regions (1-32MB each). Each region is dynamically assigned as Eden, Survivor, Old, or Humongous. A *young GC* evacuates Eden and Survivor regions to new Survivor/Old regions (STW pause). When the heap occupancy exceeds a threshold (default 45%), G1 starts a *concurrent marking cycle* to identify live objects in old regions. After marking, a *mixed GC* evacuates both young regions AND a subset of old regions with the lowest liveness (most garbage) — hence "mixed." This continues until old region occupancy drops below a threshold.

**Q3: What is tri-color marking and why does it need write barriers?**
Tri-color marking classifies objects: white (unvisited), gray (visited but references unscanned), black (fully scanned). GC scans gray objects, marks their references gray, marks them black. When marking is concurrent, the mutator can create a reference from a black object to a white object — breaking the invariant (white objects would be collected even though reachable from black). Write barriers detect this: G1 uses SATB (snapshot-at-the-beginning) barriers to record pre-write values; ZGC uses load barriers on every reference read.

**Q4: Why does ZGC achieve sub-millisecond pauses?**
ZGC uses *colored pointers* (metadata bits in the 64-bit address) and *load barriers* (code executed on every object reference read). When ZGC relocates an object, it updates the color bits. When a thread reads a reference, the load barrier checks the color — if stale (pre-relocation), it transparently loads the new address. This means relocation can happen concurrently with the mutator. The only STW phases are initial mark and remark — both bounded by GC root count, not heap size, giving sub-1ms pauses even on TB heaps.

**Q5: What happens during class initialization (`<clinit>`)?**
The JVM executes static initializer blocks and static field assignments in textual order. It's guaranteed to run at most once (JVM serializes it). Triggered by: first instance creation, first static method call, first static field access (except constants). A class initialization cycle (A initializes B which initializes A) results in A seeing B partially initialized — a subtle ordering bug. Always prefer lazy-initialized holders or enum-based singletons to avoid class initialization ordering issues.

**Q6: How does escape analysis enable stack allocation?**
Escape analysis determines whether an object's reference escapes the current method (assigned to a static field, passed to a method, returned, etc.). If an object doesn't escape, the JIT can: (1) allocate it on the stack instead of the heap (no GC overhead); (2) apply scalar replacement — decompose the object into its constituent fields, which may be kept in registers. Enabled by default in HotSpot. To verify: use `-XX:+PrintEscapeAnalysis` or async-profiler allocation profiling.

**Q7: What is deoptimization and when does it happen?**
Deoptimization is the JIT discarding compiled native code and falling back to interpretation (or lower-tier compilation) when assumptions embedded in the compiled code are violated. Common triggers: a method was compiled assuming only one concrete type was seen (monomorphic dispatch), then a second type appears (bimorphic/megamorphic); a speculated null check is violated; a final field was changed via reflection. Deoptimization causes a latency spike followed by recompilation. Detected via `-XX:+TraceDeoptimization` or async-profiler.

**Q8: Explain happens-before with a volatile example.**
The JMM guarantees: a *volatile write* to a field happens-before any subsequent *volatile read* of the same field. "Subsequent" means observed to have read the write's value. So: Thread A writes `x = 42; volatile flag = true;` and Thread B reads `volatile flag; read x;` — if Thread B sees `flag == true`, it is guaranteed to see `x == 42`. The volatile write on `flag` establishes the happens-before edge across all preceding writes by Thread A. Without `volatile` on `flag`, Thread B might see `flag == true` but `x == 0` (stale cache line).

**Q9: What is safe publication and what are the 4 ways to achieve it in Java?**
Safe publication ensures all threads see the fully constructed state of an object. An object is *unsafely published* if a reference is made visible before construction is complete (partial initialization). The 4 safe publication mechanisms: (1) Static initializer: `static final Foo f = new Foo();` — JVM class initialization guarantees. (2) `final` field in constructor: constructors establish happens-before between writing final fields and any thread that observes the reference. (3) `volatile` field: storing reference to `volatile` field. (4) Thread-safe collection (AtomicReference, ConcurrentHashMap, etc.).

**Q10: What is the difference between minor GC, major GC, and full GC?**
Minor GC collects only the young generation (Eden + Survivors) — typically fast (< 100ms), STW. Major GC collects the old generation — can be concurrent (G1, ZGC) or STW (Parallel GC). Full GC collects everything (young + old + metaspace) — always STW, always slow. Full GC is triggered when: concurrent collection fails to keep up (G1 "concurrent mode failure"), explicit `System.gc()`, metaspace exhausted, or JVM cannot allocate in any region.

**Q11: What causes OutOfMemoryError: Metaspace?**
Metaspace stores class metadata. It grows unbounded by default (unlike PermGen which was fixed size). OOM:Metaspace occurs when: (1) a class generator (CGLIB, ASM, dynamic proxies) creates classes faster than old classloaders are GC'd; (2) ClassLoader leak — a classloader is referenced from a static field, preventing GC of all its loaded classes. Every class loaded by a leaked classloader occupies metaspace permanently. Fix: set `-XX:MaxMetaspaceSize` to cap it; hunt classloader leaks with `jmap -clstats`.

**Q12: What GC flags would you set for a low-latency Java service?**
```
-XX:+UseZGC                    # Sub-millisecond pauses
-Xms4g -Xmx4g                  # Pre-allocate heap (avoid resize pause)
-XX:+AlwaysPreTouch             # Touch all pages at startup (avoid fault latency)
-XX:+UseTransparentHugePages    # Reduce TLB misses (Linux)
-Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=20m
-XX:MaxGCPauseMillis=10         # Target pause goal (G1 only; ZGC ignores this)
```
For G1 (if ZGC not available):
```
-XX:+UseG1GC -XX:MaxGCPauseMillis=50
-XX:G1HeapRegionSize=16m        # For large heaps
-XX:InitiatingHeapOccupancyPercent=35  # Start marking earlier
```

**Q13: How does the JVM class loader hierarchy work?**
Bootstrap ClassLoader (JDK built-in, C++) loads core JDK classes. Platform ClassLoader loads Java SE APIs not in bootstrap. App ClassLoader loads application classpath. Parent delegation model: when asked to load a class, each classloader first delegates to its parent; only loads itself if parent returns null. This prevents user code from replacing `java.lang.String`. Custom classloaders for hot-deploy or isolation can override `loadClass()` to invert delegation (load own classes first).

**Q14: What is a safepoint and why can it cause latency spikes?**
A safepoint is a program execution point where all JVM threads are paused for operations requiring a consistent heap state: GC, deoptimization, heap dumps, thread dumps. The JVM sets a safepoint request flag; threads poll this flag at safepoint poll points (method returns, loop back edges). Time-to-safepoint (TTSP) is the latency from request to all threads reaching safepoints. TTSP causes latency spikes because: a thread in a tight loop without poll points may not reach a safepoint for millions of instructions. On Java 8 with counted loops, a loop like `for (int i = 0; i < 1_000_000_000; i++)` might not have a poll point — the GC request waits until the loop completes. Diagnosis: `-Xlog:safepoint` (Java 9+). Mitigation: Java 10+ Thread-Local Handshakes allow targeting individual threads; Java 14+ improves polling in loops.

**Q15: What is the `StoreLoad` memory barrier and what Java operation requires it?**
`StoreLoad` is the most expensive memory barrier: it ensures all stores before the barrier complete and are visible to all CPUs before any subsequent load executes. It requires flushing the store buffer and preventing speculative loads from executing before preceding stores are committed — requiring a full memory fence (`MFENCE` or locked instruction on x86). In Java, a volatile write requires `StoreLoad` (plus `StoreStore`): the volatile write must be visible to all threads before any subsequent load by the same thread can execute. `synchronized` exit also requires `StoreLoad`. This is why volatile writes are more expensive than volatile reads — reads only need `LoadLoad + LoadStore`, while writes need the full `StoreStore + StoreLoad` pair.

---

## 13. Best Practices

1. **Set `-Xms` equal to `-Xmx`** to avoid heap resize pauses in production.
2. **Enable GC logging** in production: `-Xlog:gc*:file=gc.log:time,uptime`.
3. **Prefer ZGC or Shenandoah** for latency-sensitive services on Java 15+.
4. **Always call `threadLocal.remove()`** in `finally` blocks when using thread pools.
5. **Make DCL fields `volatile`** — no exceptions.
6. **Avoid `finalize()`** — use `Cleaner` or `try-with-resources` instead.
7. **Avoid `System.gc()`** in production code — triggers full GC.
8. **Use `jmap`/MAT** for memory leak diagnosis; `jstack` for thread/deadlock analysis.
9. **Set `-XX:MaxMetaspaceSize`** to prevent unbounded metaspace growth in classloader-heavy apps.
10. **Use async-profiler** (not VisualVM) for low-overhead production profiling.

---

## 14. Case Study

### Diagnosing a G1GC Mixed-GC Pause Storm in a 32GB-Heap Service (Java 17 LTS)

**Scenario.** A config-aggregation service on Java 17 (LTS) runs with `-Xmx32g` using G1GC. After ~6 hours of uptime, it enters a **mixed-GC pause storm**: 2-second stop-the-world pauses every ~5 minutes, blowing the 200ms pause target and tripping the load balancer's health timeout. Restarts clear it for another 6 hours — the signature of a slow leak filling the old generation until G1 thrashes trying to reclaim it. The service handles ~3,000 config lookups/sec; each lookup parses and *caches* a config object in a `static Map` that is never evicted.

```
  heap (32GB)
  old gen fill -->  [#########################........]  -> mixed GC churns, 2s pauses
                            ^ static Map<String,Config> grows unbounded
  Fix: WeakReference map -> old gen plateaus -> pauses < 200ms
```

#### Investigation commands

```bash
# 1. Confirm GC behavior live (Java 17): unified logging.
java -Xlog:gc*:file=gc.log:time,uptime,level,tags -Xmx32g ... 
#    grep for "Pause Young (Mixed)" durations > 1000ms

# 2. Low-overhead flight recording in production (continuous, ~1% overhead).
jcmd <pid> JFR.start name=leak settings=profile maxsize=512m
jcmd <pid> JFR.dump  name=leak filename=leak.jfr
#    Open in JDK Mission Control -> Memory -> "Old Object Sample" shows leaking type

# 3. Live heap histogram - which class is growing?
jcmd <pid> GC.class_histogram | head -20
#    -> [C (char[]) and Config dominate; Config count climbs every snapshot

# 4. Full heap dump for MAT dominator-tree confirmation.
jmap -dump:live,format=b,file=heap.hprof <pid>
```

MAT's dominator tree showed a single `static final Map<String,Config> CACHE` retaining 24GB — every distinct config key ever seen, held by a strong reference, pinned in old gen forever.

#### Broken cache, then fix

```java
// BROKEN: unbounded static strong-reference cache. Entries never leave old gen.
public final class ConfigCache {
    private static final Map<String, Config> CACHE = new ConcurrentHashMap<>();
    public static Config get(String key) {
        return CACHE.computeIfAbsent(key, ConfigCache::parse);   // grows forever
    }
}
```

```java
// FIX: bound the cache so the GC can reclaim cold entries.
// Option A - size+TTL bounded cache (preferred; predictable footprint):
private static final Cache<String, Config> CACHE = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();

// Option B - WeakReference values: GC may evict when memory is tight.
private static final Map<String, WeakReference<Config>> CACHE = new ConcurrentHashMap<>();
```

After deploying the bounded cache, old-gen occupancy plateaued, mixed GCs returned to **sub-200ms** (G1's default `-XX:MaxGCPauseMillis=200`), and the 6-hour restart cycle disappeared.

**Sizing reference numbers:** G1 default pause target 200ms; ZGC delivers sub-1ms pauses at the cost of throughput and is the alternative when 200ms is still too high on a 32GB+ heap. For comparison, a virtual thread stack is ~few KB versus a platform thread's ~1MB; an unrelated HikariCP pool defaults to 10 connections — none of these were the leak, which is why the histogram, not intuition, found it.

### Common Pitfalls (production war stories)

**1. `System.gc()` in production triggered full STW pauses.** A "cleanup" endpoint called `System.gc()`, forcing a full stop-the-world collection on the 32GB heap (multi-second). Removed it and added `-XX:+DisableExplicitGC` so library calls cannot trigger it either.

**2. `-Xmx` set larger than physical RAM minus OS headroom.** A node with 32GB RAM ran `-Xmx30g`; with off-heap (Metaspace, thread stacks, direct buffers) the process exceeded RAM and the OS began swapping. GC pauses ballooned to 10s+ because the collector touched swapped-out pages. Fix: leave ~25% headroom (`-Xmx24g` on 32GB).

**3. `finalize()` resurrected objects and delayed reclamation.** A legacy class overrode `finalize()`, which moves objects onto a single finalizer queue and defers their collection by at least one GC cycle, bloating old gen. Replaced with `Cleaner` / try-with-resources (`AutoCloseable`).

**4. ClassLoader leak filling Metaspace.** A plugin system loaded classes with new ClassLoaders but a static listener held a reference to each, so the loaders (and their classes) never unloaded. Metaspace grew until `OutOfMemoryError: Metaspace`. Found via `jcmd <pid> GC.class_stats`; fixed by clearing the listener on undeploy.

#### Tuning flags applied and what they did

```bash
# G1 baseline for the 32GB service (Java 17 LTS):
-Xms32g -Xmx32g                 # fixed heap: avoid resize pauses + OS commit churn
-XX:MaxGCPauseMillis=200        # G1's soft pause goal (default 200ms)
-XX:+UseG1GC                    # default since Java 9, explicit for clarity
-XX:+DisableExplicitGC          # neutralize stray System.gc() calls
-XX:InitiatingHeapOccupancyPercent=45  # start concurrent mark earlier to avoid evacuation failure
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=20m

# Heap and GC headroom math for this node:
#   32GB physical RAM
#   - ~24GB -Xmx (75% leaves room for off-heap)
#   - Metaspace (~256MB), thread stacks (platform ~1MB each), direct buffers
#   Setting -Xmx30g caused swapping; -Xmx24g eliminated it.
```

Before the cache fix, GC logs showed `Pause Young (Mixed)` events of 1,800-2,100ms every ~5 min. After the bounded cache, the same log line read 60-150ms and mixed collections became infrequent because old-gen occupancy stopped climbing.

#### Verifying the fix

```bash
# Re-run the soak test for 12 hours and confirm old-gen plateaus.
jstat -gcutil <pid> 5s            # watch O (old %) stop trending upward
jcmd <pid> GC.class_histogram | grep Config   # Config count should stabilize, not grow
```

The success criterion is not "no GC" but "old-gen occupancy is flat over the soak window" — a leak is a *trend*, and only a sustained run proves it is gone.

### Interview Discussion Points

**What is a G1 mixed GC and why does it pause longer over time?** Mixed GC collects young regions plus a selection of old regions in one pause. As the old gen fills with live (un-reclaimable) data, each cycle must scan and evacuate more, so pauses lengthen and frequency rises — the classic leak signature.

**Heap dump vs JFR — when do you use each?** JFR (Old Object Sample) is continuous and low-overhead (~1%), good for catching the leaking allocation site in production without a pause. A heap dump is a full snapshot you analyze in MAT for the dominator tree and retained sizes — heavier, taken when you already suspect a leak.

**What is the difference between retained heap and shallow heap?** Shallow heap is the memory of the object itself; retained heap is everything that would be freed if that object were collected (the object plus all it exclusively dominates). The leak culprit is usually whatever has the largest *retained* heap.

**When would you switch from G1 to ZGC?** When even a well-tuned 200ms G1 pause violates your latency SLA on a large heap. ZGC keeps pauses sub-1ms regardless of heap size, trading some throughput and more CPU/memory overhead for concurrent collection.

**Why is `WeakReference` not a complete fix on its own?** Weak entries are reclaimed only at the GC's discretion and only under memory pressure, giving unpredictable hit rates and no upper bound on entry count between collections. A size+TTL bounded cache gives a deterministic footprint, which is usually what production wants.

**Why fix `-Xms` equal to `-Xmx`?** A growing heap incurs resize pauses and lazily commits OS pages, causing latency jitter as the heap expands under load. Fixing both to the same value commits the full heap at startup, trading a slower boot for predictable steady-state behavior — standard for latency-sensitive services.

**What is the difference between `OutOfMemoryError: Java heap space` and `: Metaspace`?** Heap-space OOM means live objects exceed `-Xmx` (a data leak like our cache). Metaspace OOM means class metadata exceeds the Metaspace limit, almost always a ClassLoader leak where loaded classes can never unload. They have different root causes and different diagnostics (`class_histogram` vs `GC.class_stats`).

---

## Related / See Also

- [Java Memory Model](../java_memory_model/README.md) — memory barriers, happens-before rules that underpin JVM memory guarantees
- [Performance & Tuning](../performance_and_tuning/README.md) — GC tuning flags, JIT profiling, JMH methodology
- [Foreign Function & Memory API](../foreign_function_and_memory_api/README.md) — MemorySegment, off-heap allocation, replacing Unsafe

**Why is `InitiatingHeapOccupancyPercent` relevant to a pause storm?** IHOP controls when G1 starts the concurrent marking cycle. If it starts too late, the old gen fills before marking completes and G1 falls back to a costly full GC (evacuation failure). Lowering it starts reclamation earlier, smoothing pauses while the leak is being fixed.
