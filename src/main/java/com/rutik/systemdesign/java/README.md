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
| 2 | [Strings and Text](strings_and_text/README.md) | String immutability, constant pool, Compact Strings (JEP 254), invokedynamic concatenation, StringBuilder, text blocks, Unicode correctness | Intermediate |
| 3 | [Generics & Type System](generics_and_type_system/README.md) | PECS, type erasure, bridge methods, wildcards, dynamic proxies | Advanced |
| 4 | [Exceptions & I/O](exceptions_and_io/README.md) | Checked/unchecked, try-with-resources, NIO.2, serialization | Intermediate |
| 5 | [Java 8 Features](java8_features/README.md) | Lambdas, Streams overview, Optional, Collectors, Date/Time API | Intermediate |
| 6 | [Java Streams — Deep Dive](java_streams/README.md) | All ops, lazy eval, flatMap, reduce, collect, Spliterator, parallel rules | Intermediate |
| 7 | [Functional Programming](functional_programming/README.md) | Function composition, custom Collectors, parallel streams, immutability | Intermediate |
| 8 | [Java 9-21 Features](java9_to_21_features/README.md) | Records, Sealed classes, Virtual threads, Pattern matching, JPMS | Intermediate |
| 9 | [JVM Internals](jvm_internals/README.md) | GC algorithms, JIT, Memory model, Class loading, Object layout | Advanced |
| 10 | [Concurrency](concurrency/README.md) | synchronized, volatile, locks, ThreadPool, CompletableFuture, CAS | Advanced |
| 11 | [Collections Internals](collections_internals/README.md) | HashMap/TreeMap/ArrayList internals, fail-fast, treeification | Advanced |
| 12 | [Design Patterns in Java](design_patterns_in_java/README.md) | GoF patterns in JDK, JDK Proxy vs CGLIB, Decorator chain, Strategy vs Template Method, concurrency patterns | Intermediate |
| 13 | [Performance & Tuning](performance_and_tuning/README.md) | GC tuning, JMH, heap/thread dumps, false sharing, async-profiler | Advanced |
| 14 | [Java Memory Model](java_memory_model/README.md) | Happens-before rules, memory barriers, safe publication, final fields, data race vs race condition | Advanced |
| 15 | [Java Interview Patterns](java_interview_patterns/README.md) | Immutable class, builder, equals contract, Integer cache, enum singleton | Intermediate |
| 16 | [Testing — JUnit 5 & Mockito](testing_junit_mockito/README.md) | JUnit 5 lifecycle, assertions, @ParameterizedTest, Mockito mocks/spies/captors, test doubles | Intermediate |
| 17 | [Annotation Processing](annotation_processing/README.md) | JSR 269 rounds, AbstractProcessor, Filer/Messager, element model, JavaPoet codegen, Lombok AST mutation, MapStruct, compile-time vs runtime | Advanced |
| 18 | [Structured Concurrency & Loom](structured_concurrency_and_loom/README.md) | Virtual threads, carrier threads, pinning, StructuredTaskScope, ScopedValue, Continuation internals, Java 21 GA | Advanced |
| 19 | [Foreign Function & Memory API (Panama)](foreign_function_and_memory_api/README.md) | Arena, MemorySegment, MemoryLayout, VarHandle, Linker downcall/upcall, jextract, replacing Unsafe/JNI, Java 22 GA | Advanced |
| 20 | [Reactive Programming](reactive_programming/README.md) | Reactor Flux/Mono, cold vs hot, flatMap/concatMap/switchMap, backpressure, Schedulers, Reactor Context, RxJava 3, StepVerifier | Advanced |
| 21 | [Networking & HTTP Client](networking_and_http_client/README.md) | HttpClient (Java 11), NIO Selector/Reactor, HTTP/2 multiplexing, NIO2 async channels | Intermediate |
| 22 | [JDBC & Database Access](jdbc_and_database/README.md) | PreparedStatement, HikariCP, transaction isolation levels, batch inserts, ResultSet streaming | Intermediate |
| 23 | [gRPC & Protocol Buffers](grpc_protobuf/README.md) | Protobuf wire format + schema evolution, 4 RPC modes, generated stubs, interceptors, deadlines/cancellation, Status error model, HTTP/2 transport | Advanced |
| 24 | [Microservices Patterns](microservices_patterns/README.md) | Saga (choreography + orchestration), transactional outbox, idempotency keys, distributed tracing context propagation, strangler fig, bulkhead | Advanced |
| 25 | [Java Date/Time (java.time)](java_time_datetime/README.md) | Instant vs LocalDateTime, ZoneId/offsets, Duration vs Period, TemporalAdjuster, Clock (testable time), DST gaps/overlaps | Intermediate |
| 26 | [Bytecode & Class-File Format](bytecode_and_classfile/README.md) | .class structure, constant pool, opcode families, invokedynamic, javap, ASM/Byte Buddy, java agents & Instrumentation | Advanced |
| 27 | [Security & Cryptography](security_and_cryptography/README.md) | JCA/JCE, MessageDigest/Cipher/KeyStore/SecureRandom, AES-GCM, TLS/SSLEngine handshake, password hashing, JAAS | Advanced |
| 28 | [JSON Processing with Jackson](json_processing_jackson/README.md) | ObjectMapper thread-safety + reuse, streaming vs tree vs databind, records/@JsonCreator, TypeReference generics, polymorphic deserialization CVEs, java.time module | Intermediate |
| 29 | [Logging](logging/README.md) | SLF4J facade, Logback/Log4j2, parameterized logging, MDC propagation across threads/virtual-threads, async appenders, structured JSON, Log4Shell | Intermediate |
| 30 | [JPMS — Java Platform Module System](java_platform_module_system/README.md) | module-info, requires/exports/opens, requires transitive, automatic modules, split packages, services (ServiceLoader), jlink | Advanced |
| 31 | [Reference Types & Cleaners](reference_types_and_cleaners/README.md) | Strong/Soft/Weak/Phantom, ReferenceQueue, WeakHashMap, Cleaner vs finalize, ThreadLocal/ClassLoader leaks | Advanced |
| 32 | [GraalVM Native Image](graalvm_native_image/README.md) | AOT native-image, closed-world reachability, reflection/resource metadata, build-time vs runtime init, startup vs throughput | Advanced |
| 33 | [Build Tools — Maven & Gradle](build_tools_maven_gradle/README.md) | Maven lifecycle/dependency mediation/BOM/shade, Gradle task graph/build cache/version catalogs, dependency hell | Intermediate |

> **Note**: `java8_features` covers Streams as part of the full Java 8 overview. `java_streams` is the dedicated deep dive — all 20+ operations, `Spliterator` internals, parallel rules, `reduce` vs `collect`, and the full `Collectors` catalogue.

---

## 8-Phase Learning Path

### Phase 1 — Language Core
```
core_language  -->  strings_and_text  -->  generics_and_type_system  -->  exceptions_and_io  -->  json_processing_jackson
```
Understand what Java code actually means at the semantic level — object contracts, string internals, type system rules, resource management, and JSON serialization/deserialization with Jackson (the de-facto data-binding library).

### Phase 2 — Modern Java
```
java8_features  -->  java_time_datetime  -->  java_streams  -->  functional_programming  -->  java9_to_21_features  -->  java_platform_module_system
```
Master the functional style, stream pipelines (full deep dive), the modern `java.time` date/time model, and the new language features from Java 8 through 21. `java_platform_module_system` closes the phase with the full JPMS deep-dive — `module-info`, `requires`/`exports`/`opens`, `requires transitive`, automatic modules, split packages, `ServiceLoader`, and `jlink` — beyond the single bullet inside `java9_to_21_features`.

### Phase 3 — JVM Internals (Show Depth)
```
jvm_internals  -->  reference_types_and_cleaners  -->  bytecode_and_classfile  -->  graalvm_native_image
```
Understand what the JVM does under the hood — GC algorithms, JIT compilation, memory model, and class loading. `reference_types_and_cleaners` follows immediately: Strong/Soft/Weak/Phantom references, `ReferenceQueue`, `WeakHashMap`, and `Cleaner` vs `finalize` — the GC-adjacent mechanics behind caches and leak hunting (`ThreadLocal`/`ClassLoader` leaks). `bytecode_and_classfile` goes one level deeper: the `.class` format, opcodes, `invokedynamic`, and runtime bytecode manipulation (ASM/Byte Buddy, java agents) that powers proxies, mocks, and profilers. `graalvm_native_image` closes the phase: AOT `native-image` compilation, closed-world reachability, and reflection/resource metadata — the build-time counterpart to everything else in this phase. This phase separates senior from junior engineers at interviews.

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
java_interview_patterns  -->  testing_junit_mockito  -->  logging  -->  annotation_processing  -->  build_tools_maven_gradle  -->  case_studies/
```
Lock in the patterns and recipes that appear on whiteboard interviews. Testing module teaches how to write verifiable code — also an interview topic. `logging` covers the SLF4J facade, Logback/Log4j2, MDC correlation across threads and virtual threads, and the Log4Shell incident — production-critical and a frequent senior probe. `annotation_processing` covers JSR 269 compile-time code generation (MapStruct/Dagger/Lombok) — the metaprogramming complement to runtime reflection from `generics_and_type_system`, and the build-time philosophy behind Spring AOT. `build_tools_maven_gradle` closes the phase: Maven lifecycle/dependency mediation/BOM/shade and Gradle task graph/build cache/version catalogs — the dependency-hell mechanics behind every build in this guide.

### Phase 7 — New Java (Java 21+) Deep-Dives
```
structured_concurrency_and_loom  -->  foreign_function_and_memory_api  -->  reactive_programming
```
Project Loom virtual threads and Panama FFM/Memory API — Java 21/22 GA features that change how the JVM interacts with concurrency and native memory. Reactive programming (Reactor/RxJava) closes the phase: the non-blocking, backpressure-driven model that virtual threads now compete with, and the foundation under Spring WebFlux.

### Phase 8 — Networking, Database & Distributed Systems (Optional Deep-Dives)
```
networking_and_http_client  -->  jdbc_and_database  -->  security_and_cryptography
grpc_protobuf  -->  microservices_patterns
```
Advanced topics for senior engineers building services that talk to other services and databases directly. `security_and_cryptography` covers the JCA/JCE toolkit — symmetric/asymmetric ciphers, AES-GCM, KeyStore, SecureRandom, password hashing, and the TLS handshake — that secures every one of those connections. `grpc_protobuf` covers the typed RPC transport between services; `microservices_patterns` covers correctness across them (Saga, outbox, idempotency, tracing, bulkhead) — both pure-Java foundations under the `backend/` and `spring/` framework treatments.

---

## Learning Paths

33 modules is the right depth for a reference and the wrong shape for someone two weeks from an interview. So there are **two ways through it**; the browser learning game's **Study** view surfaces both as a **Full / Interview** toggle (Full is the default).

### Full Path (33 modules)

The complete curriculum in the order above — see [8-Phase Learning Path](#8-phase-learning-path). Use it for genuine depth: bytecode and the class-file format, Panama FFM, reactive programming, gRPC, networking internals, security/crypto, and the production/tooling modules. Nothing is dropped.

### Interview-Specific Path (17 modules)

A ruthless cut to what a **senior Java interview** actually probes — the language semantics, the JVM "show depth" round, and the concurrency/collections cluster that appears in nearly every screen. Same learning order, ~40% fewer modules. Each group says why it earns interview time.

| Group | Modules | Why it's tested |
|-------|---------|-----------------|
| Language Core | [Core Language](core_language/README.md), [Strings & Text](strings_and_text/README.md), [Generics & Type System](generics_and_type_system/README.md), [Exceptions & I/O](exceptions_and_io/README.md), [JSON Processing (Jackson)](json_processing_jackson/README.md) | equals/hashCode contract, string immutability/interning, PECS/erasure, try-with-resources, and Jackson data-binding pitfalls — the guaranteed openers |
| Modern Java | [Java 8 Features](java8_features/README.md), [Java Streams](java_streams/README.md), [Java 9-21 Features](java9_to_21_features/README.md) | lambdas/Optional, stream laziness + parallel rules, records/sealed/pattern-matching/virtual-threads |
| JVM & Memory | [JVM Internals](jvm_internals/README.md), [Java Memory Model](java_memory_model/README.md) | GC algorithms (G1 ~200ms vs ZGC sub-ms), JIT, class loading, happens-before — the "show depth" separator |
| Concurrency & Collections | [Concurrency](concurrency/README.md), [Collections Internals](collections_internals/README.md) | HashMap/ConcurrentHashMap internals, volatile vs synchronized, ThreadPoolExecutor, CAS/AQS — the most-tested cluster |
| Patterns & Testing | [Design Patterns in Java](design_patterns_in_java/README.md), [Java Interview Patterns](java_interview_patterns/README.md), [Testing (JUnit 5 & Mockito)](testing_junit_mockito/README.md) | GoF-in-the-JDK, immutable/builder/singleton recipes, mocks vs spies vs captors |
| Modern Concurrency | [Structured Concurrency & Loom](structured_concurrency_and_loom/README.md) | virtual threads vs reactive, carrier-thread pinning, StructuredTaskScope — the current hot topic |
| Data Access | [JDBC & Database Access](jdbc_and_database/README.md) | transaction isolation levels, HikariCP pool sizing, PreparedStatement, batch inserts |

**Deliberately deferred to the Full Path** (valuable, lower whiteboard yield): functional programming (folded into Java 8 + Streams for interviews), java.time, performance & tuning (staff-level depth), logging, annotation processing, bytecode & class-file format, Foreign Function & Memory API, reactive programming, networking & HTTP client, security & cryptography, gRPC & Protocol Buffers, and microservices patterns. A niche flagged in an interview (e.g. "have you used Panama?") is a bonus, not a gate — reach for these once the 17 above are solid.

---

## Knowledge-Question Map

The highest-frequency Java *knowledge* questions mapped to the file that answers them. See also the [Java Interview Cheat Sheet](#java-interview-cheat-sheet) below for the rapid-fire "always know" list.

| Interview question | Where the answer lives |
|--------------------|------------------------|
| The equals/hashCode contract — what breaks if you override one but not the other? | [Core Language](core_language/README.md), [Java Interview Patterns](java_interview_patterns/README.md) |
| Why are Strings immutable? Explain the string pool and interning. | [Strings & Text](strings_and_text/README.md) |
| HashMap internals — resize, load factor 0.75, treeification at 8. | [Collections Internals](collections_internals/README.md) |
| ConcurrentHashMap vs synchronizedMap — how does CHM scale? | [Concurrency](concurrency/README.md), [Collections Internals](collections_internals/README.md) |
| volatile vs synchronized — what does volatile NOT give you? | [Concurrency](concurrency/README.md), [Java Memory Model](java_memory_model/README.md) |
| Explain the Java Memory Model and happens-before. | [Java Memory Model](java_memory_model/README.md) |
| How does ThreadPoolExecutor work (core/max/queue/rejection)? | [Concurrency](concurrency/README.md) |
| CompletableFuture — thenApply vs thenCompose, exception handling. | [Concurrency](concurrency/README.md) |
| Virtual threads — how they work, carrier-thread pinning, vs platform threads. | [Structured Concurrency & Loom](structured_concurrency_and_loom/README.md) |
| Generics — type erasure, PECS, why you can't do `new T[]`. | [Generics & Type System](generics_and_type_system/README.md) |
| Stream laziness — when does a pipeline run, and when is parallel a trap? | [Java Streams](java_streams/README.md) |
| Records, sealed classes, and pattern matching — what and why. | [Java 9-21 Features](java9_to_21_features/README.md) |
| G1 vs ZGC — pause targets and when to choose which. | [JVM Internals](jvm_internals/README.md) |
| JIT compilation — C1/C2, tiered compilation, inlining. | [JVM Internals](jvm_internals/README.md) |
| try-with-resources, suppressed exceptions, checked vs unchecked. | [Exceptions & I/O](exceptions_and_io/README.md) |
| Is ObjectMapper thread-safe? Streaming vs tree vs databind. | [JSON Processing (Jackson)](json_processing_jackson/README.md) |
| How did polymorphic deserialization (default typing) cause RCEs? | [JSON Processing (Jackson)](json_processing_jackson/README.md) |
| Design an immutable class — defensive copies, final fields. | [Java Interview Patterns](java_interview_patterns/README.md) |
| GoF patterns in the JDK — Decorator in I/O, Strategy in Comparator. | [Design Patterns in Java](design_patterns_in_java/README.md) |
| Mockito — mocks vs spies, verify, argument captors. | [Testing (JUnit 5 & Mockito)](testing_junit_mockito/README.md) |
| Transaction isolation levels and HikariCP pool sizing. | [JDBC & Database Access](jdbc_and_database/README.md) |

---

## Study Plan

A 5-week plan over the Interview-Specific Path. Case studies live in [case_studies/](case_studies/README.md) and rehearse the "implement X" format.

| Week | Focus | Modules | Case study |
|------|-------|---------|------------|
| 1 | Language Core | Core Language, Strings & Text, Generics & Type System, Exceptions & I/O, JSON Processing (Jackson) | skim [LRU Cache](case_studies/design_lru_cache_java.md) |
| 2 | Modern Java | Java 8 Features, Java Streams, Java 9-21 Features | — |
| 3 | JVM + Concurrency (most tested) | JVM Internals, Concurrency, Collections Internals, Java Memory Model | [Thread Pool](case_studies/design_thread_pool_java.md), [Connection Pool](case_studies/design_connection_pool.md) |
| 4 | Patterns, Testing, Modern Concurrency | Design Patterns in Java, Java Interview Patterns, Testing (JUnit 5 & Mockito), Structured Concurrency & Loom | [Circuit Breaker](case_studies/design_circuit_breaker_java.md) |
| 5 | Data Access + drills | JDBC & Database Access | [Rate Limiter](case_studies/design_rate_limiter_java.md) + mock "implement X" from the case studies |

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
| core_language | generics_and_type_system (bridge methods, erasure), java_interview_patterns (equals/hashCode recipe), design_patterns_in_java (Builder, value objects), strings_and_text (immutability, hashCode benign race) |
| strings_and_text | core_language (immutability contract), java_memory_model (hashCode benign data race), jvm_internals (object layout, byte[] vs char[]), performance_and_tuning (StringBuilder vs format, GC allocation rate) |
| structured_concurrency_and_loom | concurrency (ReentrantLock, ThreadPoolExecutor), java9_to_21_features (virtual threads overview), jvm_internals (ForkJoinPool, continuation internals), performance_and_tuning (carrier pool sizing, async-profiler), reactive_programming (reactive vs virtual-thread tradeoff) |
| reactive_programming | structured_concurrency_and_loom (virtual threads as the simpler alternative), concurrency (CompletableFuture, executors), java9_to_21_features (java.util.concurrent.Flow), functional_programming (composition, laziness) |
| foreign_function_and_memory_api | jvm_internals (native memory, GC heap boundaries), java_memory_model (VarHandle memory ordering), performance_and_tuning (JMH benchmarks, off-heap profiling) |
| microservices_patterns | structured_concurrency_and_loom (ScopedValue for context propagation), concurrency (CompletableFuture, ThreadPoolExecutor bulkheads), `../../backend/microservices_fundamentals/`, `../../backend/event_driven_fundamentals/`, `../../backend/event_sourcing_and_cqrs/` |
| grpc_protobuf | networking_and_http_client (HTTP/2 multiplexing), generics_and_type_system (generated stub generics), `../../backend/grpc_and_protobuf/` (architecture-level design) |
| annotation_processing | generics_and_type_system (reflection, dynamic proxies — the runtime metaprogramming codegen replaces), design_patterns_in_java (Builder/Factory are commonly generated), `../../spring/spring_native_graalvm/` (AOT = same build-time philosophy) |
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
| jdbc_and_database | concurrency (connection pool, thread safety), performance_and_tuning (pool sizing), java_time_datetime (TIMESTAMP vs TIMESTAMPTZ mapping) |
| java_time_datetime | java8_features (where java.time was introduced), strings_and_text (parsing/formatting), jdbc_and_database (temporal column mapping) |
| bytecode_and_classfile | jvm_internals (class loading, JIT), annotation_processing (compile-time vs bytecode-time codegen), generics_and_type_system (bridge methods, erasure in bytecode) |
| security_and_cryptography | networking_and_http_client (TLS transport), `../../cs_fundamentals/cryptography_fundamentals/`, `../../backend/backend_security_owasp/`, `../../backend/auth_and_authorization_systems/` |
| strings_and_text → regex_engine_and_redos.md (sub-file) | catastrophic backtracking / ReDoS, possessive/atomic groups, `../../backend/backend_security_owasp/` |
| performance_and_tuning → jmx_and_management.md (sub-file) | platform MXBeans, custom MBeans, remote JMX security, `../../spring/spring_boot_actuator/` |

---

## Java Version Matrix

| Feature | Introduced | LTS Version | Common Usage |
|---------|-----------|-------------|-------------|
| Lambdas & Streams | Java 8 | Java 8 (LTS) | Universal |
| Optional | Java 8 | Java 8 (LTS) | Universal |
| var (local type inference) | Java 10 | Java 11 (LTS) | High |
| Text Blocks | Java 15 | Java 17 (LTS) | High |
| Compact Strings (JEP 254) | Java 9 | Java 11 (LTS) | Transparent (no API change) |
| invokedynamic String concat | Java 9 (JEP 280) | Java 11 (LTS) | Transparent (compiler change) |
| String.strip() / isBlank() | Java 11 | Java 11 (LTS) | High |
| String.indent() | Java 12 | Java 17 (LTS) | Moderate |
| String.formatted() | Java 15 | Java 17 (LTS) | Growing |
| Records | Java 16 | Java 17 (LTS) | High |
| Sealed Classes | Java 17 | Java 17 (LTS) | Growing |
| Pattern Matching instanceof | Java 16 | Java 17 (LTS) | High |
| Switch Expressions | Java 14 | Java 17 (LTS) | High |
| Pattern Matching switch | Java 21 | Java 21 (LTS) | Growing |
| Virtual Threads (`Thread.ofVirtual()`) | Java 21 GA (JEP 444) | Java 21 (LTS) | Growing fast |
| StructuredTaskScope | Java 21 preview (JEP 453); re-preview 22–24 | Java 21 (LTS) | Early adoption |
| ScopedValue | Java 21 preview (JEP 446); JEP 487 in 24 | Java 21 (LTS) | Early adoption |
| Sequenced Collections | Java 21 | Java 21 (LTS) | Growing |

---

## Case Studies

For a guided learning path through all 8 case studies, see [case_studies/README.md](case_studies/README.md).

| Case Study | Core Java Concepts | Difficulty |
|------------|-------------------|------------|
| [Design Connection Pool](case_studies/design_connection_pool.md) | ThreadPoolExecutor, BlockingQueue, AtomicInteger, timeouts | Advanced |
| [Design Rate Limiter (Java)](case_studies/design_rate_limiter_java.md) | AtomicLong, ScheduledExecutor, token bucket, CAS | Intermediate |
| [Design Event Bus](case_studies/design_event_bus.md) | Observer, CompletableFuture, WeakReference, ConcurrentHashMap | Intermediate |
| [Design LRU Cache (Java)](case_studies/design_lru_cache_java.md) | LinkedHashMap, ConcurrentHashMap, WeakReference, ReentrantReadWriteLock | Advanced |
| [Design Thread Pool (Java)](case_studies/design_thread_pool_java.md) | ThreadPoolExecutor internals, ctl AtomicInteger, Worker extends AQS, rejection policies | Advanced |
| [Design DI Container (Java)](case_studies/design_di_container_java.md) | Reflection, @Inject/@Named/@PostConstruct, Binding record, cycle detection | Advanced |
| [Design Circuit Breaker (Java)](case_studies/design_circuit_breaker_java.md) | Sliding window ring buffer, CAS state transitions, HALF_OPEN probe counting | Advanced |
| [Design Snowflake ID Generator (Java)](case_studies/design_snowflake_id_generator_java.md) | 41+10+12 bit layout, CAS, clock skew, virtual thread pinning pitfall | Advanced |

**Cross-cutting shared primitives** (consumed by the case studies above):

| Primitive | What it covers |
|-----------|---------------|
| [JVM Tuning & GC for Services](case_studies/cross_cutting/jvm_tuning_and_gc_for_services.md) | G1/ZGC/Shenandoah, heap sizing, JFR, GC log analysis |
| [Benchmarking with JMH](case_studies/cross_cutting/benchmarking_with_jmh.md) | Dead-code elimination, constant folding, false sharing, CI regression gating |
| [Concurrency & Memory Visibility Primitives](case_studies/cross_cutting/concurrency_memory_visibility_primitives.md) | Happens-before, volatile, AtomicXxx, LongAdder, VarHandle acquire/release |
| [Backpressure & Bounded Resources](case_studies/cross_cutting/backpressure_and_bounded_resources.md) | Little's Law, ArrayBlockingQueue, CallerRunsPolicy, Semaphore, HikariCP sizing |

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
