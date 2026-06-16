# Java Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/java/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 20 Modules

| Module Directory | Topic |
|-----------------|-------|
| `core_language/` | OOP, equals/hashCode, inner classes, polymorphism, init order |
| `java8_features/` | Lambdas, Streams overview, Optional, Collectors, Date/Time, primitive streams |
| `java_streams/` | Stream API deep dive — all ops, internals, parallel, Spliterator |
| `java9_to_21_features/` | Records, Sealed classes, Virtual threads, Pattern matching, JPMS |
| `jvm_internals/` | GC algorithms, JIT, memory barriers, safepoints, class loading |
| `concurrency/` | synchronized, volatile, ThreadPool, CAS, AQS, LockSupport, CompletableFuture |
| `collections_internals/` | HashMap, ArrayList, LinkedHashMap, NavigableMap, Spliterator internals |
| `exceptions_and_io/` | Checked/unchecked, try-with-resources, NIO.2, FileChannel, serialization |
| `functional_programming/` | Composition, custom Collectors, parallel streams, immutability |
| `generics_and_type_system/` | PECS, erasure, bridge methods, wildcards, MethodHandle, dynamic proxies |
| `performance_and_tuning/` | GC tuning, JMH, CPU cache, JIT inlining, tiered compilation |
| `java_interview_patterns/` | Immutable class, Builder, enum singleton, Integer cache |
| `design_patterns_in_java/` | GoF patterns (Creational, Structural, Behavioral), concurrency patterns |
| `testing_junit_mockito/` | JUnit 5, Mockito, test doubles, AAA pattern, parameterized tests |
| `java_memory_model/` | Happens-before rules, memory barriers, safe publication, DRF |
| `networking_and_http_client/` | HttpClient (Java 11), NIO Selector, Reactor pattern, HTTP/2 |
| `jdbc_and_database/` | PreparedStatement, HikariCP, transaction isolation, batch inserts |
| `strings_and_text/` | String immutability, constant pool, Compact Strings (JEP 254), invokedynamic concat (JEP 280), StringBuilder, text blocks (JEP 378), Unicode correctness |
| `structured_concurrency_and_loom/` | Virtual threads (JEP 444), carrier threads, pinning, StructuredTaskScope (JEP 453), ScopedValue (JEP 446), Continuation internals, Java 21 GA |
| `foreign_function_and_memory_api/` | Panama: Arena, MemorySegment, MemoryLayout, VarHandle, Linker (JEP 454), downcall/upcall handles, jextract, replacing Unsafe/JNI, Java 22 GA |

---

## Planned / Missing Topics (not yet created)

The following topics are identified as senior Java engineer coverage gaps. Add them as new modules following the adding guide below:

| Module Directory | Topic | Priority |
|-----------------|-------|---------|
| `reactive_programming/` | Project Reactor, RxJava 3, backpressure, Scheduler internals, Flux/Mono operators, testing with StepVerifier | Critical — bridges to Spring WebFlux |
| `microservices_patterns/` | Saga (choreography + orchestration), event sourcing in Java, idempotency keys, distributed tracing context propagation, strangler fig | High — bridges to backend/ |
| `grpc_protobuf/` | Protocol buffer message format, gRPC client/server stubs, 4 RPC modes, interceptors, deadlines, error handling | High — bridges to backend/grpc_and_protobuf |
| `annotation_processing/` | APT, AbstractProcessor, Lombok internals, MapStruct, Spring meta-annotations, compile-time code generation | Medium |

---

## Case Studies — 8 Total

`case_studies/` directory — all use the 11-section principal template.

| File | Primary Pattern |
|------|----------------|
| `design_connection_pool.md` | HikariCP mechanics, pool sizing, leak detection |
| `design_rate_limiter_java.md` | Token bucket, sliding window counter, Redis Lua |
| `design_event_bus.md` | Guava EventBus vs custom, weak references, async dispatch |
| `design_lru_cache_java.md` | LinkedHashMap internals, ConcurrentHashMap variant |
| `design_thread_pool_java.md` | ThreadPoolExecutor internals, queue types, rejection policies |
| `design_di_container_java.md` | Reflection-based injection, scope management, circular deps |
| `design_circuit_breaker_java.md` | State machine, half-open probing, metrics integration |
| `design_snowflake_id_generator_java.md` | Twitter Snowflake, clock skew handling, worker ID assignment |

Learning-path index: `case_studies/README.md` (mandatory; update with every new case study).

---

## Cross-Cutting Shared Primitives — 4 Files

`case_studies/cross_cutting/` — all use the 14-section template:

| File | When Relevant |
|------|--------------|
| `jvm_tuning_and_gc_for_services/` | Any case study involving throughput or latency tuning |
| `benchmarking_with_jmh/` | Any case study comparing implementation choices |
| `concurrency_memory_visibility_primitives/` | Any case study with shared state or thread pools |
| `backpressure_and_bounded_resources/` | Any case study with queues or resource limits |

---

## Cross-Reference Map

| Java Module | See Also (other sections) |
|-------------|--------------------------|
| `networking_and_http_client/` | `../../backend/http_protocols/` — HTTP/1.1 vs /2 vs /3 protocol internals; `../../backend/grpc_and_protobuf/` — gRPC design patterns |
| `concurrency/` | `../../backend/async_and_concurrency_patterns/` — production concurrency patterns; `../../hld/` — distributed consensus theory; `../../lld/concurrency_patterns/` — Thread-Safe Singleton, Producer-Consumer, Read-Write Lock, Thread Pool patterns |
| `jdbc_and_database/` | `../../database/connection_pool_management/` — PgBouncer, pool sizing math; `../../database/sql_query_optimization/` — EXPLAIN ANALYZE |
| `jvm_internals/` | `../../backend/performance_profiling/` — async-profiler, JFR, flamegraphs |
| `structured_concurrency_and_loom/` | `../../spring/spring_webflux/` — reactive alternative; `../../backend/async_and_concurrency_patterns/`; `../../lld/concurrency_patterns/` — pattern adaptation for virtual threads |
| `design_patterns_in_java/` | `../../lld/` — full GoF pattern catalogue (all 23 patterns with UML + Java implementations) |
| `functional_programming/` | `../../lld/behavioral/strategy/` — Strategy pattern via lambdas |

---

## Java Version Tags

When covering a feature, always include the version it was introduced and LTS status:
- Java 8 (LTS), Java 11 (LTS), Java 17 (LTS), Java 21 (LTS)
- Non-LTS: 9, 10, 12–16, 18–20
- Format: `[Java 21]` or `[JEP 444, Java 21 GA]`

## Content Rules (Java-specific)

- **No Spring/framework content** — this section is pure Java only. Spring integration lives in `spring/`.
- **Effective Java references** where applicable: Item 1, Item 3, Item 17, etc.
- Q&A minimums: **15 per module** (absolute floor); **18+** for `concurrency/`, `jvm_internals/`, `java_memory_model/`, `collections_internals/`, `generics_and_type_system/`
- Order Q&As by interview frequency: self-invocation traps, volatile vs synchronized, HashMap resize, class loading — gotchas first; internals second; edge cases last
- Code must compile against Java 17+ (or the specific LTS relevant to the feature)

## Adding a New Java Module

1. Create `<module_name>/README.md` — 14-section template
2. Minimum 15 Q&As; ordered by interview frequency (gotchas first)
3. Add a row to the module table in `README.md` (the Java master index)
4. Place it in the correct learning phase in the phase diagram in `README.md`
5. Add cross-references in the Cross-Reference Map in `README.md` if applicable
6. Update root `README.md` Phase table under the Java section
7. Update `java/CLAUDE.md` module table (this file)

---

## Visual Intuition Diagrams

Section 5 (Architecture Diagrams) and any hard-to-picture concept should use an
**ASCII visual intuition diagram** that makes an abstract relationship visible
(constraint grid, before/after-with-delta, stacked flow, routing fan-out, bar
chart, or curve/sketch). Generate and validate them with the
`/visual-intuition-diagrams` skill. The full archetype catalog, conventions
(ASCII only, no tabs, no emojis, widest line <= 100 cols, caption every diagram),
and the `diagram_tools.py` validator live in root `CLAUDE.md` -> "Visual Intuition
Diagrams".
