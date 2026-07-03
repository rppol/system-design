# Performance Profiling

## 1. Concept Overview

Performance profiling is the process of measuring where a Java application spends its time and memory. Without profiling, performance optimization is guesswork — you risk optimizing hot code that is not the bottleneck, or missing a subtle memory leak that only manifests after days of operation. Production-safe profiling is the art of gathering performance data without significantly impacting the running system.

Java has a rich profiling ecosystem: async-profiler for CPU and allocation profiling with low overhead, Java Flight Recorder (JFR) for continuous, production-safe telemetry, heap dump analysis for memory leaks, and thread dump analysis for concurrency issues. Reading a flamegraph and a GC log is a core skill for any senior backend engineer.

---

## 2. Intuition

> **One-line analogy**: Profiling a Java application is like giving a GPS tracker and a fitness monitor to every line of your code — you learn not just where it goes but how long it stays there and how much energy it burns.

**Mental model**: A CPU profiler takes samples of the call stack at regular intervals (e.g., every 1ms). The frequency with which a method appears in samples correlates with the time spent in that method. A flamegraph visualizes this: width proportional to time. Heap profilers track allocation sites — where objects are created. GC logs show how often the garbage collector runs and how long it pauses.

**Why it matters**: "My service is slow" needs a precise answer. Is it CPU-bound (tight computation loop)? IO-bound (waiting for database, external API)? Memory pressure (frequent GC pauses)? Lock contention (threads waiting)? Each diagnosis has different solutions. Profiling gives you the precise answer.

**Key insight**: The biggest profiling mistake is running intrusive profilers in production — JVM TI-based profilers like VisualVM use safepoints, which can pause the JVM for profiling overhead. Use async-profiler (samples at AsyncGetCallTrace, not safepoints) for production profiling with < 5% overhead.

---

## 3. Core Principles

- **Sample-based vs instrumentation-based**: Sample profilers periodically snapshot the call stack (low overhead). Instrumentation profilers add code to every method entry/exit (accurate but high overhead, not for production).
- **Safepoint bias**: Traditional JVM profilers wait for safepoints to take stack samples. Code that runs frequently between safepoints (tight loops) appears to not consume CPU. async-profiler avoids safepoint bias via AsyncGetCallTrace.
- **Heap allocation tracking**: Tracking every allocation is expensive. Use allocation sampling (track a sample of allocations) or allocation profiling (track allocations of specific types).
- **Production safety**: JFR is the only profiler designed specifically for continuous production use (~1-2% overhead). async-profiler is also production-safe at intervals > 1ms.

---

## 4. Types / Architectures / Strategies

### 4.1 Profiling Tool Comparison

| Tool | Type | Overhead | Production Safe | Use Case |
|------|------|---------|----------------|---------|
| async-profiler | CPU/alloc/lock (native) | < 5% | Yes | CPU hotspots, allocation profiling, lock contention |
| Java Flight Recorder | Event-based | 1-2% | Yes (designed for it) | Continuous telemetry, GC, I/O, method profiling |
| Java Mission Control (JMC) | JFR UI | None (analysis) | Yes | Analyzing JFR recordings |
| VisualVM | CPU/heap/thread | 5-30% | Dev/test only | Interactive exploration |
| YourKit | CPU/memory | 2-10% | Limited | Deep allocation analysis |
| Arthas (Alibaba) | Runtime diagnostics | Low | Yes | Dynamic tracing, method tracing |
| async-profiler flamegraph | Visualization | None | N/A | Identify hotspots from samples |

### 4.2 GC Algorithm Selection

| GC | Default Since | Pause Target | Throughput | Use Case |
|----|-------------|-------------|------------|---------|
| Serial GC | - | N/A | High | Single-threaded, tiny heap |
| Parallel GC | JDK 8 default | N/A | Highest | Batch processing |
| G1 GC | JDK 9 default | 200ms (configurable) | High | Balanced latency/throughput |
| ZGC | JDK 15 stable | Sub-millisecond (<1ms) | Good | Low-latency services |
| Shenandoah | JDK 17+ | Sub-millisecond | Good | Low-latency |

### 4.3 Thread States for Diagnosis

| Thread State | Meaning | Likely Cause |
|-------------|---------|-------------|
| RUNNABLE | Running on CPU | CPU-bound work or OS I/O (in OS kernel) |
| BLOCKED | Waiting for monitor (synchronized) | Lock contention |
| WAITING | Waiting indefinitely (Object.wait, park) | Thread pool idle or deadlock |
| TIMED_WAITING | Waiting with timeout (sleep, wait(ms), join(ms)) | Sleeping, scheduled task idle |

---

## 5. Architecture Diagrams

### Flamegraph Anatomy

```
Width = time spent (or allocation count)
Y-axis = call stack depth (bottom = thread root, top = leaf)

|                    main                          |
|         run()                         wait()     |
|  processRequest()           ...                  |
|  queryDB()  serialize()                          |
|  execute()  toJson()                             |
|  parse()    format()                             |

Reading: the widest frames at the bottom are where time is spent.
If "queryDB" is wide, DB calls dominate.
If "serialize" is wide, serialization dominates.
Icicle chart (inverted): same but top-down.

Hotspot identification:
  Look for wide plateau frames near the top of stacks.
  These are "hot" methods — they appear in many samples.
```

### GC Log Analysis

```
G1GC Log (JDK 17+, -Xlog:gc*):
[1.234s][info][gc] GC(1) Pause Young (Normal) (G1 Evacuation Pause)
                   15M->8M(128M) 12.123ms

Parse:
  1.234s     = time since JVM start
  GC(1)      = GC event #1
  Pause Young = collected young generation
  Normal      = normal evacuation (not humongous objects)
  15M->8M    = heap before -> heap after
  (128M)     = total heap committed
  12.123ms   = pause duration

Warning signs:
  Pause > 200ms → G1 missing its pause target (increase heap or tune regions)
  Frequent GCs (< 1 second between) → heap too small or allocation rate too high
  "To-space exhausted" → insufficient survivor space, increase -Xmx
  Full GC → G1 falling back to stop-the-world (very bad for latency)

Key metrics:
  GC throughput = time not in GC / total time. Target: > 95%
  Allocation rate = MB/s of new object allocation
  Promotion rate = MB/s of objects surviving to old gen
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Starting async-profiler

```bash
# Attach to running JVM (PID 12345) for 30 seconds
./profiler.sh -d 30 -f /tmp/flamegraph.html 12345

# CPU profiling with 1ms interval (production-safe)
./profiler.sh -e cpu -i 1ms -d 60 -f /tmp/cpu.html 12345

# Allocation profiling (track allocations, every 512KB sample)
./profiler.sh -e alloc -i 512k -d 60 -f /tmp/alloc.html 12345

# Lock contention profiling
./profiler.sh -e lock -d 60 -f /tmp/lock.html 12345

# Wall clock profiling (includes threads waiting for I/O)
./profiler.sh -e wall -d 60 -f /tmp/wall.html 12345
# Useful for identifying latency from I/O, not just CPU hotspots
```

### 6.2 JFR Recording

```java
// Programmatic JFR recording
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

// Start a recording
Recording recording = new Recording();
recording.enable("jdk.CPUSample").withSamplePeriod(Duration.ofMillis(20));
recording.enable("jdk.GarbageCollection");
recording.enable("jdk.JavaMonitorWait");
recording.enable("jdk.SocketRead").withThreshold(Duration.ofMillis(10));
recording.enable("jdk.FileWrite").withThreshold(Duration.ofMillis(10));
recording.setDestination(Paths.get("/tmp/app.jfr"));
recording.setMaxSize(100 * 1024 * 1024);  // 100 MB ring buffer
recording.start();

// Command line equivalent:
// java -XX:StartFlightRecording=duration=60s,filename=app.jfr,
//          settings=profile MyApp

// Continuous recording (ring buffer, dump on demand):
// java -XX:StartFlightRecording=maxsize=200m,maxage=1d MyApp
// jcmd <pid> JFR.dump name=1 filename=/tmp/dump.jfr

// Custom JFR event
@Label("Database Query")
@Category("Application")
@StackTrace(true)
public class DatabaseQueryEvent extends Event {
    @Label("Query") String query;
    @Label("Duration MS") long durationMs;
    @Label("Rows Returned") int rows;
}

// Usage:
DatabaseQueryEvent event = new DatabaseQueryEvent();
event.begin();
ResultSet rs = statement.executeQuery(sql);
event.commit();  // records if duration > event threshold
```

### 6.3 Heap Dump Analysis

```bash
# Generate heap dump (add jmap to PATH)
jmap -dump:format=b,file=/tmp/heap.hprof <pid>

# Or trigger from application on OOM (add to JVM args):
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/

# Analyze with Eclipse MAT (Memory Analyzer Tool)
# Key views:
#   Dominator Tree: shows largest objects and what's preventing GC
#   Leak Suspects: automated analysis
#   OQL (Object Query Language): query heap like a database
#     SELECT * FROM java.util.HashMap WHERE size > 100000

# Common leak patterns:
#   1. ThreadLocal variables not removed: Map held by thread, thread in pool → leak
#      Fix: always call threadLocal.remove() in finally block
#
#   2. Static collections growing indefinitely
#      Fix: eviction policy, size limits, WeakHashMap for caches
#
#   3. Event listeners not deregistered
#      Fix: unregister on lifecycle events; use WeakReference for listeners
#
#   4. HttpSession storing large objects (web tier)
#      Fix: keep sessions lightweight; store IDs, not objects
```

### 6.4 Thread Dump Analysis

```bash
# Generate thread dump
jstack -l <pid> > /tmp/thread-dump.txt

# Or via kill signal on Unix:
kill -3 <pid>

# Or via JMX/Spring Boot Actuator:
GET /actuator/threaddump

# Deadlock detection (jstack highlights automatically):
Found 1 deadlock:
  Thread-1:
    waiting to lock <0x0000000787a5e440> (java.lang.Object)
    which is held by Thread-2
  Thread-2:
    waiting to lock <0x0000000787a5e430> (java.lang.Object)
    which is held by Thread-1

# High BLOCKED count = lock contention:
# 1. Many threads BLOCKED on same lock address = single hot lock
#    Fix: reduce synchronized scope, use striped locks, or lock-free structures

# All threads WAITING in pool queue = thread pool exhausted:
# Fix: increase pool size or reduce task duration

# Many threads in TIMED_WAITING with sleep()/Condition.await() = normal idle

# Thread dump patterns by problem:
# CPU spike: many threads RUNNABLE in the same method
# Deadlock: cyclic BLOCKED chains
# Thread pool exhaustion: WAITING in pool.take() + many tasks queued
# Slow dependency: many threads WAITING/BLOCKED in external call
```

---

## 7. Real-World Examples

**Netflix Hollow**: Netflix uses async-profiler in production to profile their Hollow data store service. They found a hot allocation in their delta application path using allocation profiling — a helper object created on every record update was causing 500 MB/s allocation rate, triggering frequent GCs. Eliminating the allocation reduced GC frequency by 80%.

**Disabling C2 JIT for profile consistency**: Some performance-sensitive code paths (trading systems, game servers) disable JIT compilation on specific methods to get predictable, non-JIT latency. JIT can introduce unexpected latency spikes during first-time compilation.

---

## 8. Tradeoffs

| Profiler | Accuracy | Overhead | Deployment |
|----------|---------|---------|-----------|
| async-profiler CPU | High (no safepoint bias) | Low (<5%) | Production |
| JFR | Very high (event-based) | Very low (1-2%) | Production |
| VisualVM | Medium (safepoint bias) | High (5-30%) | Dev/test |
| Allocation sampling | Approximation | Low | Production |
| Exact allocation | Perfect | Very high | Dev/test only |

---

## 9. When to Use / When NOT to Use

**async-profiler in production**: Use for targeted investigations (15-60 second profiling runs) when you have a known performance problem. Do not run continuously at high sampling rates — 1ms interval for 60 seconds is fine; 0.1ms interval continuously is risky.

**JFR continuous recording**: Enable on all production JVMs with a ring buffer (200 MB). Dump on demand when a problem occurs. The recording captures the last 1-24 hours of telemetry, invaluable for post-mortem analysis.

**Heap dump**: Use only when diagnosing a memory leak or OOM. Heap dumps of large heaps (>4 GB) can cause JVM pause during dump. Use `jmap -histo:live` for a quick object histogram without a full heap dump.

---

## 10. Common Pitfalls

**Safepoint bias in traditional profilers**: VisualVM and older profilers sample only at JVM safepoints. Tight loops without safepoints (character parsing, hash computation) appear to consume zero CPU in these profiles. async-profiler uses AsyncGetCallTrace to sample at any point — use it for accurate CPU profiling.

**Off-heap memory leaks not visible in heap dumps**: Direct ByteBuffers (NIO, Netty), JNI-allocated memory, and memory-mapped files are not in the heap. A service leaking off-heap memory shows normal heap metrics but grows in system RSS. Use JFR's `jdk.DirectBufferStatistics` event or `/proc/<pid>/smaps` to measure off-heap usage.

**GC pause time vs GC throughput confusion**: A GC with 50ms pause every 30 seconds (throughput = 99.7%) is very different from 50ms pause every 2 seconds (throughput = 97.5%). High throughput (low % of time in GC) is important for batch jobs. Low pause times are important for latency-sensitive services. G1's `-XX:MaxGCPauseMillis=50` targets pause time; Parallel GC maximizes throughput.

**Profiling the wrong thing**: A service is slow. The profiler shows 80% of CPU in `serialize()`. The developer optimizes serialization. But the actual slowness was 500ms DB query latency invisible to CPU profiling because the thread was WAITING (not RUNNABLE) during the query. CPU profilers only show CPU time — use wall-clock profiling (`-e wall`) to see where threads spend time including I/O waits.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| async-profiler | Low-overhead CPU/alloc/lock profiling |
| Java Flight Recorder | Continuous event recording |
| Java Mission Control | JFR recording analysis GUI |
| Eclipse MAT | Heap dump analysis |
| `jstack` | Thread dump (diagnose deadlocks/contention) |
| `jmap` | Heap dump, heap histogram |
| `jstat` | JVM statistics (GC, class loading) |
| `jcmd` | JVM diagnostics (JFR control, thread dump, GC) |
| Arthas | Dynamic tracing (watch method args/return values) |
| Grafana + Micrometer | Production GC metrics, heap usage |

---

## 12. Interview Questions with Answers

**Q: How do you find the cause of high CPU usage in a Java application?**
Take a 30-60 second CPU profile with async-profiler (`-e cpu`). Generate a flamegraph. The widest frames near the top of stacks are the CPU hotspots. Look for business logic (your code) vs framework code vs GC. If GC is wide, investigate allocation rate. If serialization or parsing is wide, optimize the data format. If a specific method is wide, inspect it for inefficiencies (unnecessary object creation, redundant computation, unoptimized algorithms).

**Q: What is safepoint bias and why does it matter?**
Traditional JVM profilers (VisualVM, JProfiler with some modes) take stack samples only at JVM safepoints — points where the JVM can pause all threads (method boundaries, loop back-edges, before allocations). Tight loops without safepoints appear to consume zero CPU in these profiles. async-profiler uses AsyncGetCallTrace which can sample at any point in execution, including inside tight loops. Safepoint bias matters when you have CPU-intensive tight loops — traditional profilers will incorrectly show them as not consuming CPU.

**Q: How do you diagnose a memory leak in a Java application?**
(1) Take a heap histogram: `jcmd <pid> GC.heap_info` or `jmap -histo:live <pid>`. Look for unexpected object counts growing over time. (2) Compare two heap histograms taken minutes apart — objects growing are suspects. (3) Take a full heap dump and analyze in Eclipse MAT: the Dominator Tree shows which objects hold the most memory; Leak Suspects report automates analysis. (4) Look for classic patterns: static collections growing, ThreadLocal not removed, listeners not unregistered, session objects accumulating.

**Q: How do you read a flamegraph?**
The x-axis represents time (or sample count) — width means how much time was spent. The y-axis is call stack depth — bottom is the thread entry point, top is the currently executing method. Wide frames near the top are hot methods. To find the bottleneck: identify the widest frame in the visible stack area. If many narrow top frames converge to a wide bottom frame, the bottleneck is that common ancestor. Colors (in async-profiler) represent categories: yellow = Java code, green = C++ JVM code, red = kernel/native.

**Q: What are the JVM flags for GC logging in production?**
JDK 17+: `-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=10,filesize=20m`. This logs all GC events with timestamps, rotates after 20MB, keeping 10 files. Key events to watch: Pause duration (> MaxGCPauseMillis is a warning), Full GC events (severe latency impact), heap occupancy after GC (approaching heap limit means memory pressure). For G1: also log `-Xlog:gc+phases*` for detailed phase breakdown.

**Q: How do you profile memory allocation rate?**
Use async-profiler with `-e alloc`: samples allocation sites, showing which code paths allocate the most. Alternatively, JFR with the ObjectAllocationInNewTLAB event records allocations. Key metrics: allocation rate (MB/s), top allocation sites, largest allocated types. A high allocation rate (>500 MB/s for typical workloads) causes frequent young GC pauses. Fix: reduce object creation in hot paths (use primitives, reuse objects, use object pools for expensive-to-create objects).

**Q: What is the difference between heap dump and heap histogram?**
A heap histogram (jmap -histo or jcmd GC.heap_info) lists object counts and sizes by class — fast (seconds), low impact. Good for initial diagnosis. A full heap dump (jmap -dump or -XX:+HeapDumpOnOutOfMemoryError) captures the entire heap with object references — can take seconds to minutes for large heaps, requires significant disk space (heap size bytes), and pauses the JVM during dump (for jmap; JFR heap dumps are asynchronous). Use histogram first; full dump for deep investigation.

**Q: How do you diagnose thread contention?**
Take a thread dump with jstack. Count threads in BLOCKED state — high BLOCKED count indicates lock contention. Multiple threads BLOCKED on the same object address indicate a hot lock. Use async-profiler with `-e lock` to identify which locks cause the most contention and which methods hold them. Fix: reduce synchronized scope, replace synchronized with ReentrantLock (tryLock with timeout), use concurrent data structures (ConcurrentHashMap), or striped locks (Guava Striped).

**Q: What JVM flags do you set for a production Spring Boot service?**
```bash
java \
  -Xms512m -Xmx2g \                          # heap sizing
  -XX:+UseG1GC \                             # G1 GC (default JDK 9+)
  -XX:MaxGCPauseMillis=200 \                 # G1 pause target
  -XX:+HeapDumpOnOutOfMemoryError \          # capture OOM heap dump
  -XX:HeapDumpPath=/var/log/app/heap.hprof \ # heap dump location
  -XX:+ExitOnOutOfMemoryError \              # restart on OOM (let k8s restart pod)
  -Xlog:gc*:file=/var/log/app/gc.log:time,uptime:filecount=5,filesize=20m \
  -XX:StartFlightRecording=maxsize=200m,maxage=1h \ # continuous JFR
  -Dfile.encoding=UTF-8 \
  -jar app.jar
```

**Q: What is async-profiler and how does it avoid safepoint bias?**
async-profiler is a low-overhead profiler for Java that uses AsyncGetCallTrace (a JVM internal API) and Linux perf_events to sample call stacks. AsyncGetCallTrace can be called at any signal (including from a signal handler triggered by a timer), not just at JVM safepoints. This eliminates safepoint bias. async-profiler also uses perf_events to sample native and kernel frames, giving a complete picture including time spent in system calls and native libraries — which traditional JVM profilers miss entirely.

**Q: How do you find the root cause of a garbage collection spike?**
(1) Check GC logs for the spike: what GC event type (Young, Mixed, Full)? What was the heap occupancy before and after? (2) Full GC: old generation full — memory pressure, possibly a leak. (3) Young GC spike: high allocation rate, possibly a hot code path creating many objects. Use async-profiler `-e alloc` to find the allocation site. (4) Check promotion rate — if young objects are surviving to old gen quickly, the survivor spaces are too small (increase -XX:SurvivorRatio) or objects live too long. (5) Check for humongous object allocations (objects > half a G1 region, default 1 MB/2 = 512 KB) — these bypass young gen and go directly to old gen.

**Q: What is the difference between wall-clock profiling and CPU profiling?**
CPU profiling (`-e cpu`) samples threads only when they are running on CPU (RUNNABLE state). It shows where CPU time is spent. Wall-clock profiling (`-e wall`) samples all threads regardless of state — including threads waiting for I/O, blocked on locks, or sleeping. For latency investigations where a request is slow but CPU usage is low (waiting for database, waiting for network, waiting for a lock), wall-clock profiling reveals where the wall time is spent. CPU profiling would show almost nothing for a primarily I/O-bound request.

**Q: How do you detect and fix high allocation pressure?**
Detection: async-profiler `-e alloc` shows top allocation sites. JFR's jdk.ObjectAllocationInNewTLAB shows allocations per code path. GC frequency > once every 5 seconds for a typical service indicates high allocation. Fix strategies: (1) Object pooling for frequently created/destroyed objects (e.g., StringBuilder, ByteBuffer, database result objects). (2) Replace boxed types with primitives in hot paths (Integer → int, HashMap<Long,Long> → use a primitive map library). (3) Reduce intermediate object creation in streams/lambdas (use traditional for-loops for allocation-sensitive code). (4) Escape analysis: the JVM may already eliminate allocations (stack allocation) for short-lived objects — check with JFR AllocationInOldGen event.

**Q: What production signals indicate a need for profiling?**
High CPU utilization without high throughput (CPU-intensive processing). Increasing heap usage over time (memory leak). GC throughput < 95% (too much time in GC). Latency p99 >> p50 (long-tail latency from GC pauses, lock contention, or occasional slow I/O). Thread pool rejection or queue depth growing (thread pool exhaustion or slow tasks). JVM OOM errors in logs. Service response time degrading after running for hours (heap pressure, leak, fragmentation).

---

## 13. Best Practices

- Enable continuous JFR recording on all production JVMs with a 200 MB ring buffer.
- Use async-profiler for production CPU investigations; never use VisualVM in production.
- Set -XX:+HeapDumpOnOutOfMemoryError and -XX:+ExitOnOutOfMemoryError on all services.
- Always enable GC logging with rotation in production.
- Use wall-clock profiling to diagnose latency issues, not just CPU profiling.
- Establish a GC throughput baseline (target >98% for latency-sensitive services).
- Profile allocation rate during load tests — high allocation rate is a warning sign before it becomes a production problem.
- Look for ThreadLocal leak patterns in code review — missed remove() calls are a common source of memory leaks in thread pool environments.

---

## 14. Case Study

**Problem**: A Spring Boot microservice handling product search was experiencing p99 latency of 800ms while p50 was 50ms. The service was not CPU-bound (40% CPU utilization). No errors in logs.

**Investigation**:
1. Thread dump during a slow period: 12 threads in WAITING state inside `GarbageFirstHeapRegionManager.allocateContiguous()`. These were threads waiting for GC.
2. GC log showed: Young GC happening every 1.2 seconds, Mixed GC every 8 seconds, each Mixed GC pausing for 400-600ms.
3. JFR recording showed allocation rate of 800 MB/s — extremely high.
4. async-profiler allocation profile: top allocator was `JsonNode.deepCopy()` — the service was deep-copying the entire product catalog JSON (2 MB) for every search request to avoid mutation.

**Root Cause**: Each of the 50 concurrent search requests was allocating 2 MB of JSON nodes per request = 100 MB/request-batch = 800 MB/s allocation rate. G1 could not keep up, causing Mixed GC pauses that blocked all threads.

**Fix**:
```java
// BEFORE: deep copy to avoid mutation — 2MB allocation per request
JsonNode productData = productCache.get(productId).deepCopy();
processSearchResult(productData);

// AFTER: design the processor to be read-only, no mutation needed
JsonNode productData = productCache.get(productId);  // no copy
processSearchResultImmutable(productData);
// OR: project only needed fields using a streaming approach
```

**Results**: Allocation rate dropped from 800 MB/s to 15 MB/s. Young GC from every 1.2s to every 3 minutes. Mixed GC eliminated. p99 dropped from 800ms to 45ms.
