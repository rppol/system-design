# Java — Complete Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **pure Java** — from language fundamentals and JVM internals to concurrency, performance tuning, and interview patterns. Designed for senior engineers and anyone preparing for Java system design + deep-dive interviews.

> **No frameworks. No Spring. Pure Java only.**
> Spring Boot / Spring Framework will be a separate section.

---

## Intuition

> **One-line analogy**: Java is a carefully designed contract between developer and JVM — write once, run anywhere — where the JVM's job is to make that contract fast, safe, and manageable at scale.

**Mental model**: Every Java program is a tree of objects living in heap memory, orchestrated by threads scheduled on OS cores, with the JVM acting as the invisible runtime layer — compiling hot code on-the-fly (JIT), managing memory (GC), enforcing type safety, and providing platform abstraction. The senior engineer's job is to understand that invisible layer deeply enough to work *with* it, not against it.

**Why it matters**: Java powers the backends of Google, Amazon, Netflix, LinkedIn, and thousands of financial systems. Most system design interviews at top-tier companies assume deep Java knowledge. Understanding HashMap internals, GC mechanics, and the Java Memory Model is the difference between a junior and senior engineer at interview time.

**Key insight**: The three pillars of Java mastery are: (1) **Language semantics** — knowing exactly what your code does at the bytecode/JVM level; (2) **Concurrency** — the single hardest part, where most production bugs live; (3) **JVM internals** — GC, JIT, memory model — the foundation of all performance work.

---

## All Modules

| # | Module | Key Concepts | Difficulty |
|---|--------|-------------|------------|
| 1 | [Core Language](core_language/README.md) | OOP, equals/hashCode, inner classes, polymorphism, Object methods | Intermediate |
| 2 | [Java 8 Features](java8_features/README.md) | Lambdas, Streams overview, Optional, Collectors, Date/Time API | Intermediate |
| 3 | [Java Streams — Deep Dive](java_streams/README.md) | All ops, lazy eval, flatMap, reduce, collect, Spliterator, parallel rules | Intermediate |
| 4 | [Java 9-21 Features](java9_to_21_features/README.md) | Records, Sealed classes, Virtual threads, Pattern matching, JPMS | Intermediate |
| 4 | [JVM Internals](jvm_internals/README.md) | GC algorithms, JIT, Memory model, Class loading, Object layout | Advanced |
| 5 | [Concurrency](concurrency/README.md) | synchronized, volatile, locks, ThreadPool, CompletableFuture, CAS | Advanced |
| 6 | [Collections Internals](collections_internals/README.md) | HashMap/TreeMap/ArrayList internals, fail-fast, treeification | Advanced |
| 7 | [Exceptions & I/O](exceptions_and_io/README.md) | Checked/unchecked, try-with-resources, NIO.2, serialization | Intermediate |
| 8 | [Functional Programming](functional_programming/README.md) | Function composition, custom Collectors, parallel streams, immutability | Intermediate |
| 9 | [Generics & Type System](generics_and_type_system/README.md) | PECS, type erasure, bridge methods, wildcards, dynamic proxies | Advanced |
| 10 | [Performance & Tuning](performance_and_tuning/README.md) | GC tuning, JMH, heap/thread dumps, false sharing, async-profiler | Advanced |
| 11 | [Java Interview Patterns](java_interview_patterns/README.md) | Immutable class, builder, equals contract, Integer cache, enum singleton | Intermediate |
| 12 | [Design Patterns in Java](design_patterns_in_java/README.md) | GoF patterns in JDK, JDK Proxy vs CGLIB, Decorator chain, Strategy vs Template Method, concurrency patterns | Intermediate |
| 13 | [Testing — JUnit 5 & Mockito](testing_junit_mockito/README.md) | JUnit 5 lifecycle, assertions, @ParameterizedTest, Mockito mocks/spies/captors, test doubles | Intermediate |
| 14 | [Java Memory Model](java_memory_model/README.md) | Happens-before rules, memory barriers, safe publication, final fields, data race vs race condition | Advanced |
| 15 | [Networking & HTTP Client](networking_and_http_client/README.md) | HttpClient (Java 11), NIO Selector/Reactor, HTTP/2 multiplexing, NIO2 async channels | Intermediate |
| 16 | [JDBC & Database Access](jdbc_and_database/README.md) | PreparedStatement, HikariCP, transaction isolation levels, batch inserts, ResultSet streaming | Intermediate |

> **Note**: `java8_features` covers Streams as part of the full Java 8 overview. `java_streams` is the dedicated deep dive — all 20+ operations, `Spliterator` internals, parallel rules, `reduce` vs `collect`, and the full `Collectors` catalogue.

---

## 6-Phase Learning Path

### Phase 1 — Language Core
```
core_language  -->  generics_and_type_system  -->  exceptions_and_io
```
Understand what Java code actually means at the semantic level — object contracts, type system rules, and resource management.

### Phase 2 — Modern Java
```
java8_features  -->  java_streams  -->  functional_programming  -->  java9_to_21_features
```
Master the functional style, stream pipelines (full deep dive), and the new language features from Java 8 through 21.

### Phase 3 — JVM Internals (Show Depth)
```
jvm_internals
```
Understand what the JVM does under the hood — GC algorithms, JIT compilation, memory model, and class loading. This module separates senior from junior engineers at interviews.

### Phase 4 — Concurrency + Collections + Patterns (Most Tested)
```
concurrency  -->  collections_internals  -->  design_patterns_in_java
```
The most heavily tested areas. HashMap/ConcurrentHashMap internals + ThreadPoolExecutor + volatile + the Java Memory Model appear in nearly every senior Java interview. Design patterns round out the architecture vocabulary.

### Phase 5 — Performance + Memory Model
```
performance_and_tuning  -->  java_memory_model
```
Diagnose and fix real production performance problems. The Java Memory Model module provides the formal foundation for understanding all concurrency guarantees.

### Phase 6 — Interview Consolidation + Testing
```
java_interview_patterns  -->  testing_junit_mockito  -->  case_studies/
```
Lock in the patterns and recipes that appear on whiteboard interviews. Testing module teaches how to write verifiable code — also an interview topic.

### Phase 7 — Networking & Database (Optional Deep-Dives)
```
networking_and_http_client  -->  jdbc_and_database
```
Advanced topics for senior engineers building services that talk to other services and databases directly.

---

## Key Tradeoffs at a Glance

| Decision | Option A | Option B | Key Factor |
|----------|----------|----------|------------|
| synchronized vs ReentrantLock | synchronized (simpler) | ReentrantLock (tryLock, fairness) | Need advanced features? |
| volatile vs AtomicInteger | volatile (visibility only) | AtomicInteger (CAS atomicity) | Need atomic read-modify-write? |
| ArrayList vs LinkedList | ArrayList (O(1) random access, cache-friendly) | LinkedList (O(1) head/tail insert) | Almost always ArrayList |
| HashMap vs ConcurrentHashMap | HashMap (single-threaded) | ConcurrentHashMap (multi-threaded) | Thread safety needed? |
| ThreadPoolExecutor queue | LinkedBlockingQueue (unbounded) | ArrayBlockingQueue (bounded) | Backpressure needed? |
| for-loop vs Stream | for-loop (simple, fast) | Stream (expressive, composable) | Hot path vs readability? |
| checked vs unchecked exception | checked (API contracts) | unchecked (internal errors) | Can caller recover? |
| platform thread vs virtual thread | platform (~1MB stack) | virtual (~few KB stack, Java 21) | IO-bound blocking work? |
| G1 GC vs ZGC | G1 (default, balanced) | ZGC (sub-1ms pauses) | Latency-critical service? |
| strong vs WeakReference | strong (normal) | WeakReference (caches, listeners) | GC-eligible when memory needed? |

---

## Cross-Reference Map

| If you're reading... | Also see... |
|---------------------|-------------|
| core_language | generics_and_type_system (bridge methods, erasure), java_interview_patterns (equals/hashCode recipe), design_patterns_in_java (Builder, value objects) |
| java8_features | java_streams (full op reference), functional_programming (Collector internals), concurrency (CompletableFuture) |
| java_streams | functional_programming (custom Collectors, Spliterator), java8_features (lambdas, functional interfaces), collections_internals (stream sources, Spliterator characteristics) |
| jvm_internals | concurrency (Java Memory Model, happens-before), java_memory_model (full JMM spec), performance_and_tuning (GC tuning flags) |
| concurrency | collections_internals (ConcurrentHashMap), jvm_internals (memory model), java_memory_model (happens-before), java_interview_patterns (DCL) |
| java_memory_model | concurrency (volatile, synchronized, LockSupport, AQS), jvm_internals (safepoints, memory barriers) |
| collections_internals | concurrency (ConcurrentHashMap, CopyOnWriteArrayList), performance_and_tuning (cache effects, Spliterator) |
| performance_and_tuning | jvm_internals (GC algorithms, JIT), concurrency (ThreadLocal leaks, false sharing) |
| java_interview_patterns | core_language (immutability), java8_features (Optional), generics_and_type_system (erasure gotchas) |
| design_patterns_in_java | core_language (inner classes, polymorphism), generics_and_type_system (dynamic proxy, reflection), concurrency (Producer-Consumer, Immutable Object) |
| testing_junit_mockito | design_patterns_in_java (test doubles taxonomy), concurrency (testing concurrent code) |
| networking_and_http_client | concurrency (CompletableFuture, virtual threads), java9_to_21_features (virtual threads) |
| jdbc_and_database | concurrency (connection pool, thread safety), performance_and_tuning (pool sizing) |

---

## Java Version Matrix

| Feature | Introduced | LTS Version | Common Usage |
|---------|-----------|-------------|-------------|
| Lambdas & Streams | Java 8 | Java 8 (LTS) | Universal |
| Optional | Java 8 | Java 8 (LTS) | Universal |
| var (local type inference) | Java 10 | Java 11 (LTS) | High |
| Text Blocks | Java 15 | Java 17 (LTS) | High |
| Records | Java 16 | Java 17 (LTS) | High |
| Sealed Classes | Java 17 | Java 17 (LTS) | Growing |
| Pattern Matching instanceof | Java 16 | Java 17 (LTS) | High |
| Switch Expressions | Java 14 | Java 17 (LTS) | High |
| Pattern Matching switch | Java 21 | Java 21 (LTS) | Growing |
| Virtual Threads | Java 21 | Java 21 (LTS) | Growing fast |
| StructuredTaskScope | Java 21 (preview→21) | Java 21 (LTS) | Early adoption |
| Sequenced Collections | Java 21 | Java 21 (LTS) | Growing |

---

## Case Studies

| Case Study | Core Java Concepts | Difficulty |
|------------|-------------------|------------|
| [Design Connection Pool](case_studies/design_connection_pool.md) | ThreadPoolExecutor, BlockingQueue, AtomicInteger, timeouts | Advanced |
| [Design Rate Limiter (Java)](case_studies/design_rate_limiter_java.md) | AtomicLong, ScheduledExecutor, token bucket, CAS | Intermediate |
| [Design Event Bus](case_studies/design_event_bus.md) | Observer, CompletableFuture, WeakReference, ConcurrentHashMap | Intermediate |
| [Design LRU Cache (Java)](case_studies/design_lru_cache_java.md) | LinkedHashMap, ConcurrentHashMap, WeakReference, ReentrantReadWriteLock | Advanced |

---

## Java Interview Cheat Sheet

### The "Always Know" List
1. **equals/hashCode contract**: reflexive, symmetric, transitive, consistent, non-null. Violating it breaks HashMap.
2. **happens-before edges**: program order, monitor unlock→lock, volatile write→read, thread start/join.
3. **Double-checked locking bug**: field must be `volatile` or object is partially initialized.
4. **ThreadPoolExecutor behavior**: core → queue → max → reject. Queue fills BEFORE more threads are created.
5. **HashMap treeification**: linked list converts to red-black tree at bucket size 8; reverts at 6.
6. **G1 GC default**: default since Java 9; uses region-based heap; 200ms default pause target.
7. **Integer cache**: [-128, 127] cached; `==` comparison on boxed Integer gives wrong result outside range.
8. **Virtual threads (Java 21)**: ~few KB stack vs ~1MB platform thread; pinned by `synchronized` on monitor.
9. **Stream laziness**: intermediate ops are lazy; terminal op triggers the pipeline.
10. **PECS**: Producer Extends, Consumer Super — for wildcard generics.

---

See individual module READMEs for full 14-section deep-dives and 10+ interview Q&As each.
