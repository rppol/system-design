# Java Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/java/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 33 Modules

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
| `generics_and_type_system/` | PECS, erasure, bridge methods, wildcards, MethodHandle, dynamic proxies — sub-file: `type_inference_and_bounds` |
| `performance_and_tuning/` | GC tuning, JMH, CPU cache, JIT inlining, tiered compilation |
| `java_interview_patterns/` | Immutable class, Builder, enum singleton, Integer cache |
| `design_patterns_in_java/` | GoF patterns (Creational, Structural, Behavioral), concurrency patterns |
| `testing_junit_mockito/` | JUnit 5, Mockito, test doubles, AAA pattern, parameterized tests |
| `java_memory_model/` | Happens-before rules, memory barriers, safe publication, DRF |
| `networking_and_http_client/` | HttpClient (Java 11), NIO Selector, Reactor pattern, HTTP/2 |
| `jdbc_and_database/` | PreparedStatement, HikariCP, transaction isolation, batch inserts |
| `strings_and_text/` | String immutability, constant pool, Compact Strings (JEP 254), invokedynamic concat (JEP 280), StringBuilder, text blocks (JEP 378), Unicode correctness |
| `structured_concurrency_and_loom/` | Virtual threads (JEP 444, Java 21 GA), carrier threads, `synchronized` no longer pins (JEP 491, Java 24), remaining native-frame pinning, StructuredTaskScope (JEP 525, preview in Java 26), ScopedValue (JEP 506, final in Java 25), Continuation internals |
| `foreign_function_and_memory_api/` | Panama: Arena, MemorySegment, MemoryLayout, VarHandle, Linker (JEP 454), downcall/upcall handles, jextract, replacing Unsafe/JNI, Java 22 GA |
| `reactive_programming/` | Reactor Flux/Mono, Reactive Streams/Flow, cold vs hot, flatMap/concatMap/switchMap, backpressure strategies, Schedulers (subscribeOn vs publishOn), Reactor Context, RxJava 3, StepVerifier |
| `microservices_patterns/` | Saga (choreography + orchestration), transactional outbox, idempotency keys, distributed tracing context propagation, strangler fig, bulkhead (thread-pool vs semaphore) |
| `grpc_protobuf/` | Protobuf wire format (varint/tag) + schema evolution + reserved, 4 RPC modes, blocking/async/future stubs, interceptors, deadlines/cancellation, Status error model, HTTP/2 transport |
| `annotation_processing/` | JSR 269 rounds, AbstractProcessor, Filer/Messager, javax.lang.model element model, JavaPoet codegen, Lombok AST mutation, MapStruct, AutoService, compile-time vs runtime tradeoff |
| `java_time_datetime/` | Instant vs LocalDateTime, ZoneId/ZoneOffset, Duration vs Period, ChronoUnit, TemporalAdjuster, DateTimeFormatter, Clock (testable time), DST gaps/overlaps, legacy Date interop |
| `bytecode_and_classfile/` | .class structure + constant pool, opcode families, all 5 invoke*, invokedynamic (LambdaMetafactory/StringConcatFactory), javap, verification, ASM/Byte Buddy, java agents + Instrumentation |
| `security_and_cryptography/` | JCA/JCE providers, MessageDigest/Mac, AES-GCM vs CBC, RSA-OAEP/ECDH, KeyStore, SecureRandom, PBKDF2/bcrypt/Argon2, Signature, TLS/SSLEngine handshake + mTLS, JAAS |
| `json_processing_jackson/` | ObjectMapper thread-safety + reuse, streaming/tree/databind, records/@JsonCreator, TypeReference generics, @JsonTypeInfo + polymorphic-typing CVEs, PolymorphicTypeValidator, JavaTimeModule, FAIL_ON_UNKNOWN_PROPERTIES |
| `logging/` | SLF4J facade + bindings, Logback/Log4j2, parameterized logging, MDC across threads/pools/virtual-threads, async appenders (Disruptor), structured JSON, Log4Shell (CVE-2021-44228) |
| `java_platform_module_system/` | JPMS: module-info, requires/exports/opens, requires transitive, automatic modules, split packages, services (ServiceLoader), jlink |
| `reference_types_and_cleaners/` | Strong/Soft/Weak/Phantom references, ReferenceQueue, WeakHashMap, Cleaner vs finalize, ThreadLocal/ClassLoader leaks |
| `graalvm_native_image/` | GraalVM native-image AOT, closed-world reachability, reflection/resource/proxy metadata, build-time vs runtime init |
| `build_tools_maven_gradle/` | Maven (lifecycle, dependency mediation, BOM, shade) + Gradle (task graph, build cache, version catalogs), dependency hell |

**Deep-dive sub-files** (group under their parent module's game topic; no separate `STUDY_ORDER` entry):
- `strings_and_text/regex_engine_and_redos.md` — backtracking NFA engine, catastrophic backtracking / ReDoS, possessive/atomic groups, mitigations.
- `performance_and_tuning/jmx_and_management.md` — MBeanServer/MXBeans, custom MBeans, `ThreadMXBean` deadlock detection, remote-JMX security, JMX vs Prometheus.

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 33 modules = "8-Phase Learning Path") plus
two curated tiers: **Senior** (19 modules) and **Principal** (15). They are different
cuts, not nested depths — senior is the craft (write it, profile it, debug the 3am
deadlock), principal is the judgment (which runtime at what cost, migration, what you
tell a team *not* to do), so principal is usually the smaller list and much of it is
material senior never sees. Membership is declared ONCE per module, in a
`<!-- study-paths -->` block in that module's own README naming the files each tier takes
— which is also the only way to curate **deep-dive sub-files**, since they have no
`STUDY_ORDER` entry of their own. Listing a tier joins it, omitting the tier opts out,
and `README.md` must always be listed. Order is never declared — it comes from
`STUDY_ORDER.java` in `game/app.js`, so a tier is an ordered subset by construction.
**There is no path array in `app.js` to edit**: `extract.py` walks the markers and emits
the gitignored `questions/paths.json`, which the game fetches at boot. The tier tables in
`README.md` sit between `<!-- study-path-table <tier> -->` markers and are **generated** —
regenerate with `python3 game/extract.py --write-paths`; a hand-edited or stale block
fails `extract.py --strict` and the Pages deploy. Case studies are tiered the same way
from a block in `case_studies/README.md` (5 senior / 3 principal), driving the Level
filter on the game's Case Studies tab. The README also carries a Knowledge-Question Map
and a 5-week Study Plan (prose; no path impact).

---

## Planned / Missing Topics (not yet created)

The 2026-07-07 interview-path audit added `json_processing_jackson/` and `logging/` (both
built, in the Module List above). A same-day follow-up build then closed every remaining
gap below — the table has no pending items left; retained as a historical record.

| Topic | Priority | Note |
|-------|----------|------|
| Build tools & dependency management (Maven/Gradle, shading, classpath vs module-path) | DONE (2026-07-07) | Built as `build_tools_maven_gradle/` |
| JPMS deep-dive (module resolution, split packages, `requires transitive`) | DONE (2026-07-07) | Built as `java_platform_module_system/` |
| Reference types & leak hunting (Weak/Soft/Phantom, ReferenceQueue, Cleaner vs finalize) | DONE (2026-07-07) | Built as `reference_types_and_cleaners/` |
| GraalVM native image / AOT (pure-Java) | DONE (2026-07-07) | Built as `graalvm_native_image/` |

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
| `structured_concurrency_and_loom/` | `../../spring/spring_webflux/` — reactive alternative; `../../backend/async_and_concurrency_patterns/`; `../../lld/concurrency_patterns/` — pattern adaptation for virtual threads; `reactive_programming/` — reactive vs virtual-thread decision |
| `reactive_programming/` | `../../spring/spring_webflux/` — Reactor applied in Spring (Netty, R2DBC, WebClient); `../../backend/async_and_concurrency_patterns/` — backpressure in the broader concurrency landscape |
| `microservices_patterns/` | `../../backend/microservices_fundamentals/` — decomposition, deployment; `../../backend/event_driven_fundamentals/` — choreography vs orchestration; `../../backend/event_sourcing_and_cqrs/` — durable event log behind sagas/outbox |
| `grpc_protobuf/` | `../../backend/grpc_and_protobuf/` — gRPC design patterns, load balancing, mesh; `networking_and_http_client/` — HTTP/2 multiplexing |
| `annotation_processing/` | `generics_and_type_system/` — reflection/dynamic proxies (runtime metaprogramming); `design_patterns_in_java/` — Builder/Factory codegen; `../../spring/spring_native_graalvm/` — AOT, same build-time philosophy |
| `design_patterns_in_java/` | `../../lld/` — full GoF pattern catalogue (all 23 patterns with UML + Java implementations) |
| `functional_programming/` | `../../lld/behavioral/strategy/` — Strategy pattern via lambdas |
| `java_time_datetime/` | `java8_features/` — where java.time landed; `strings_and_text/` — parsing/formatting; `jdbc_and_database/` — TIMESTAMP vs TIMESTAMPTZ; `../../spring/request_handling/i18n_and_localization.md` — locale-aware formatting |
| `bytecode_and_classfile/` | `jvm_internals/` — class loading, JIT; `annotation_processing/` — compile-time vs bytecode-time codegen; `../../spring/spring_native_graalvm/` — reachability/reflection hints |
| `security_and_cryptography/` | `../../cs_fundamentals/cryptography_fundamentals/` — primitives; `../../backend/backend_security_owasp/` — applied security; `../../backend/auth_and_authorization_systems/` — OAuth/OIDC; `networking_and_http_client/` — TLS transport |

---

## Java Version Tags

When covering a feature, always include the version it was introduced and LTS status:
- Java 8 (LTS), Java 11 (LTS), Java 17 (LTS), Java 21 (LTS), Java 25 (LTS — the current baseline)
- Non-LTS: 9, 10, 12–16, 18–20, 22–24, 26
- Format: `[Java 25]` or `[JEP 444, Java 21 GA]`

## Content Rules (Java-specific)

- **No Spring/framework content** — this section is pure Java only. Spring integration lives in `spring/`.
- **Effective Java references** where applicable: Item 1, Item 3, Item 17, etc.
- Q&A minimums: **15 per module** (absolute floor); **18+** for `concurrency/`, `jvm_internals/`, `java_memory_model/`, `collections_internals/`, `generics_and_type_system/`
- Order Q&As by interview frequency: self-invocation traps, volatile vs synchronized, HashMap resize, class loading — gotchas first; internals second; edge cases last
- Code must compile against Java 21+ (Java 25 LTS is the section baseline; use an older LTS only when the feature under discussion requires it)

## Adding a New Java Module

1. Create `<module_name>/README.md` — 14-section template
2. Minimum 15 Q&As; ordered by interview frequency (gotchas first). Author a
   `**Short:**` line under each question (15–220 chars, no fence) — that line IS the
   MCQ option. Without one `extract.py` derives the option from the answer's first
   sentence and trims it at a clause boundary past 220 chars; nothing is dropped, but
   a long or fence-opening first sentence ships as a mangled option.
3. Add a row to the module table in `README.md` (the Java master index)
4. Place it in the correct learning phase in the phase diagram in `README.md`
5. Add cross-references in the Cross-Reference Map in `README.md` if applicable
6. Update root `README.md` Phase table under the Java section
7. Update `java/CLAUDE.md` module table (this file)
8. **REQUIRED for the game:** add the new module id (`java/<module_name>`) to
   `STUDY_ORDER.java` in `game/app.js` at its learning-path position — a module
   missing from that array sorts to the end and fails `--strict`. (A new **sub-file**
   `<module>/<name>.md` needs no `STUDY_ORDER` entry; it groups under its parent
   module's topic and its Q&As merge into that module's bank — but if a tier should
   carry it, add its filename to that tier's line in the parent's `<!-- study-paths -->`
   block, which is the only place a sub-file is addressable.)
9. Write a `<!-- study-paths -->` block in the new module's README naming the tiers it
   belongs to (or none, for Full-path-only), then run
   `python3 game/extract.py --write-paths` to regenerate `README.md`'s tier tables.
10. **Re-run `python3 game/extract.py`** to regenerate the question bank.

---

## Diagrams — Appeal-First (Mermaid preferred)

**Owner policy (2026-07-02), supersedes the old ASCII-only rule.** Section 5
(Architecture Diagrams) and any hard-to-picture concept should use the most
visually appealing renderable form that conveys the info accurately. In practice
the **Mermaid family is preferred** — `flowchart` for directed flows (GC roots,
NIO Reactor, Panama Java→Native, HTTP/2 multiplexing), `sequenceDiagram` for
actor chains (thread interaction, JDBC round-trips), `stateDiagram-v2` for
lifecycles (circuit-breaker CLOSED→OPEN→HALF_OPEN, connection-pool states),
`xychart-beta` for magnitude comparisons. Run `/mermaid-diagrams` before
authoring or converting any diagram — it has the One-Dark `classDef` palette,
supported types, and gotchas.

**Reader contract:** Mermaid renders in the game reader (v11, pitch-black
surface). Colour **every** flowchart node with the One-Dark `classDef` — the reader
auto-tints only the nodes you leave unstyled (authored colours are always respected,
so it degrades per node, never a flat-grey bail), but its order-based hues are
arbitrary, so hand-colour all nodes for semantic consistency. Never set a light
background inside a diagram.

**Keep ASCII only** for shapes Mermaid cannot draw — byte/memory-layout maps
(Compact Strings `byte[]`, MemoryLayout field offsets), the JVM heap-region grid,
the HashMap bucket-array grid, constraint grids, and alignment-critical layout
maps. Validate those with the `/visual-intuition-diagrams` skill's
`diagram_tools.py check` (ASCII only, no tabs/emojis, widest line ≤ 100 cols,
caption every diagram). Full policy in root `CLAUDE.md` → "Mermaid Diagrams" and
"Visual Intuition Diagrams".
