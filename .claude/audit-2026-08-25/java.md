# Java Audit — 2026-08-25

## 0. Snapshot

The `java/` section is the most *disciplined* section I have profiled: 33 modules, every one
carrying all 14 template headings, every one at or above the 15-Q&A floor, and every one of the
636 module Q&As carrying an authored `**Short:**` line (100% — the one apparent gap in
`concurrency.md:102` is a bold table lead-in, not a question). The 2026-08-04 modernization pass
landed hard and visibly: string templates are correctly recorded as *withdrawn*, `HttpClient.close()`
is correctly attributed to Java 21, non-generational ZGC is correctly recorded as removed by JEP 490
in Java 24, JUnit 6 / Mockito 5 inline-mock-maker is the taught baseline, GraalVM is on the 25.0 LTS
train with `reachability-metadata.json`, and `structured_concurrency_and_loom` already teaches the
JDK 25 `StructuredTaskScope.open()` + `Joiner` shape with JEP 525 as the current form. Do not
re-litigate any of that.

The single biggest weakness is a **release-window blind spot**: the section's mental model of "modern
Java" ends at Java 21, and everything Java 22-26 delivered that is *not* Loom or Panama is simply
absent. Across 40,199 lines there are **zero** mentions of CDS/AppCDS/AOT cache (JEP 483/514/515/516),
**zero** of compact object headers (JEP 519, product in 25; default in 27), **zero** of post-quantum
crypto (ML-KEM/ML-DSA, final in 24) inside a 1,107-line security module, **zero** of HTTP/3 in the
HttpClient (JEP 517, final in 26) inside an 817-line networking module, **zero** of
`--enable-native-access` inside an 876-line Panama module, and **zero** of module import declarations,
compact source files, or flexible constructor bodies — the three *language* features finalized in the
LTS this section names as its own baseline. The structural cause is that
`java9_to_21_features/` is the only "what's new" module and its name freezes it at 21.

A second, quieter weakness: the section's own `CLAUDE.md` mandates Effective Java item references,
and **28 of the 35 module files cite zero of them**, including `concurrency`, `collections_internals`,
`java_streams`, `functional_programming`, and `reference_types_and_cleaners` — the five modules where
Bloch is most directly on point.

| Metric | Value |
|--------|-------|
| Modules | 33 |
| Deep-dive sub-files | 2 (`regex_engine_and_redos`, `jmx_and_management`) |
| Case studies | 8 + 4 `cross_cutting/` + 1 index |
| Total lines (all 49 content files) | 40,199 |
| Total lines (35 module files only) | 29,300 |
| Total Q&As | 786 (636 module / 88 case study / 62 cross-cutting) |
| `**Short:**` coverage on module Q&As | 636 / 636 (100%) |
| Avg lines/module file | 837 |
| Mermaid fences | 122 |
| Module files citing Effective Java | 7 / 35 |

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Pri | Est. lines |
|---|---|---|---|---|
| `java22_to_26_features/java22_to_26_features.md` | module | 13 releases of finalized language/API work are uncovered. JDK 25 finalized Module Import Declarations (JEP 511), Compact Source Files & Instance Main Methods (JEP 512), Flexible Constructor Bodies (JEP 513); JDK 24 finalized Stream Gatherers (JEP 485) and the Class-File API (JEP 484). Section has **0** mentions of the first three. <https://openjdk.org/projects/jdk/25/> · <https://www.happycoders.eu/java/java-26-features/> | **P0** | 850 |
| `aot_startup_and_class_data_sharing/aot_startup_and_class_data_sharing.md` | module | **Zero** mentions of CDS, AppCDS, `-Xshare`, `AOTCache`, or Leyden anywhere in 40k lines — while the JDK shipped JEP 483 (AOT Class Loading & Linking, 24), JEP 514 (AOT Command-Line Ergonomics, 25), JEP 515 (AOT Method Profiling, 25), JEP 516 (AOT Object Caching with Any GC, 26). This is now the *default* answer to "our JVM starts too slow", and the section only offers the nuclear option (`graalvm_native_image/`). <https://openjdk.org/jeps/483> · <https://openjdk.org/jeps/515> · <https://openjdk.org/jeps/516> | **P0** | 800 |
| `security_and_cryptography/post_quantum_and_modern_jca.md` | sub-file | A 1,107-line JCA module with **0** mentions of ML-KEM, ML-DSA, KEM, or post-quantum. JDK 24 shipped ML-KEM (JEP 496) and ML-DSA (JEP 497) as final; JDK 25 shipped the KDF API (JEP 510); JDK 27 (GA 2026-09-15) ships Post-Quantum Hybrid Key Exchange for TLS 1.3 (JEP 527). Also: `SecurityManager` was permanently disabled by JEP 486 (JDK 24) and the module never says so. <https://www.happycoders.eu/java/java-27-features/> · <https://openjdk.org/jeps/496> | **P0** | 700 |
| `performance_and_tuning/jvm_in_containers.md` | sub-file | Only 6 scattered hits for `cgroup|MaxRAMPercentage|ActiveProcessorCount` across the whole section, 4 of them inside one cross-cutting file. "Why did Kubernetes OOMKill a JVM whose heap was fine" is a standard 2026 senior question and the section cannot answer it. | P1 | 700 |
| `performance_and_tuning/jfr_and_production_diagnostics.md` | sub-file | JFR is mentioned 60+ times but `RecordingStream` / `JFR.start` only 7. JDK 25 alone shipped three JFR JEPs — Cooperative Sampling (518), Method Timing & Tracing (520), CPU-Time Profiling (509, experimental) — and JDK 27 ships In-Process Redaction (536). None appear. <https://www.happycoders.eu/java/java-25-features/> | P1 | 750 |
| `networking_and_http_client/` — HTTP/3 section (in place) | in-place | JEP 517 (final, JDK 26) added `HttpClient.Version.HTTP_3` with transparent HTTP/2 fallback. The module's protocol table at `:56` lists HTTP/3 but every code path pins `Version.HTTP_2` (`:228`, `:705`) and `:541` implies HTTP/2 is the ceiling. <https://openjdk.org/jeps/517> | P1 | 120 + 3 Q&As |
| `foreign_function_and_memory_api/` — native-access policy (in place) | in-place | **0** mentions of `--enable-native-access` / `--illegal-native-access` / JEP 472. Every FFM snippet in the module warns at runtime on JDK 24+; on `deny` it throws `IllegalCallerException`. This is the first thing a reader hits when they run the module's own code. <https://openjdk.org/jeps/472> · <https://docs.oracle.com/en/java/javase/25/core/restricted-methods.html> | P1 | 80 + 2 Q&As |
| `jdbc_and_database/` — virtual threads × pool (in place) | in-place | **0** mentions of virtual threads in a 933-line JDBC module. The module already gets the hard part right (`:440-461`: size the pool to the DB's parallelism, not your thread count) — it just never names the case that makes that lesson urgent. | P1 | 90 + 2 Q&As |
| `collections_internals/` + `java_interview_patterns/` — compact object headers (in place) | in-place | JEP 519 made compact object headers a product feature in JDK 25 (12→8 byte headers); JEP 534 makes them the **default** in JDK 27. Every object-footprint number these two modules teach is about to be wrong by 4 bytes/object. **0** mentions. <https://openjdk.org/jeps/519> | P1 | 80 + 3 Q&As |
| `build_tools_maven_gradle/` — 2026 build surface (in place) | in-place | 1,281 lines with **0** mentions of Maven 4 / `mvnd`, **0** of the Gradle configuration cache, **0** of SBOM/CycloneDX, **0** of the Sonatype Central Portal migration. Reproducible builds and version catalogs are covered well; the rest of the 2026 build conversation is missing. | P1 | 160 + 4 Q&As |
| `case_studies/design_structured_concurrency_aggregator.md` | case study | No case study exercises `StructuredTaskScope` + `ScopedValue` as the primary design, despite them being the section's flagship modules. Fan-out-with-deadline / hedged-read is the single most-asked Loom-era design question. | P2 | 950 |
| `case_studies/design_low_latency_market_data_handler.md` | case study | Nothing ties FFM + JMM + false sharing + GC-free steady state into one design. The section teaches all four separately and never composes them. | P2 | 950 |
| `jvm_internals/` — Valhalla & Vector API status (in place) | in-place | **0** mentions of the Vector API (12th incubator in JDK 27, JEP 537) and one *incorrect* Valhalla mention (see §2). JEP 401 Value Objects is now a real preview targeted at JDK 28. <https://www.infoq.com/news/2026/08/jep401-value-objects-preview/> | P2 | 60 |

### P0 detail

**`java22_to_26_features/`** — §1 what changed between the 21 and 25 LTS and why the section's
baseline moved; §2 "the language got smaller at the edges" (declaration-free `main`, module imports,
`super()` no longer first); §3 finalization discipline — preview→final graduation, `--enable-preview`
pinning, what "preview" costs you in a shipped product; §4 taxonomy split into *language* (511/512/513,
Markdown javadoc 467), *API* (Gatherers 485, Class-File 484, HTTP/3 517), *runtime* (compact object
headers 519, AOT 483/514/515/516, Generational Shenandoah 521), *still-preview* (Structured Concurrency
505/525/533, Stable Values 502/526/531, Primitive Patterns 507/530/532); §5 a timeline diagram
(`timeline` mermaid) of 22→27 with final vs preview lanes; §6 mechanics — a `Gatherers.windowSliding`
worked example, a `ClassFile.of().parse()` example replacing an ASM snippet, `import module java.base`
resolution and its ambiguity rules, flexible-constructor-body validation before `super()`; §7 what real
codebases adopted first (Gatherers, module imports in scripts); §8 tradeoff table — adopt-on-LTS vs
adopt-on-feature-release, cost of `--enable-preview` in CI; §9 when a preview feature is and is not
worth it; §10 pitfalls — preview API renames between releases (`anySuccessfulResultOrThrow` →
`anySuccessfulOrThrow` in 26), Gatherers' `mapConcurrent` and virtual threads, module imports colliding
with a same-named class; §11 tooling — `--release`, `--enable-preview`, jdeprscan, the JDK EA build
train; §12 18 Q&As; §13 an upgrade checklist; §14 migrating a 21-baseline service to 25.

**`aot_startup_and_class_data_sharing/`** — §1 the three costs of JVM startup (class load, verify+link,
interpret-then-JIT) and which of them each technique removes; §2 "the JVM re-does the same homework
every launch — AOT hands it the marked copy"; §3 principles: a training run must be representative, the
cache is JDK-version-and-classpath-bound, correctness never depends on the cache; §4 the ladder —
default CDS → AppCDS (`-XX:SharedArchiveFile`) → dynamic archives (`-XX:ArchiveClassesAtExit`) → AOT
cache (`-XX:AOTCacheOutput`/`-XX:AOTCache`, JEP 483/514) → AOT method profiling (JEP 515) → AOT object
caching with any GC (JEP 516) → GraalVM native image; §5 a `flowchart LR` of the training-run →
cache-creation → production-run pipeline; §6 mechanics with real commands and real numbers (a Spring-shaped
app's time-to-first-request with and without a cache), plus a broken→fix: a cache built from a
classpath that drifted, silently ignored at runtime; §7 who ships this (Leyden's published benchmarks,
cloud function cold starts); §8 tradeoff table AOT cache vs native image vs plain JVM across startup,
peak throughput, build time, dynamic-feature support, debuggability; §9 when a 15ms startup is worth a
closed world and when it is not; §10 pitfalls — stale caches, unrepresentative training runs, cache
invalidation you never noticed; §11 `jcmd`, `-Xlog:cds`, `-Xlog:aot`, Leyden EA builds; §12 16 Q&As;
§13 CI recipe for producing and verifying a cache; §14 cutting a service's p99 cold start.

**`security_and_cryptography/post_quantum_and_modern_jca.md`** — §1 why "harvest now, decrypt later"
makes this a 2026 problem not a 2030 one; §2 the lattice mental model at one level of abstraction above
the math; §3 crypto-agility as the actual deliverable — you are not picking an algorithm, you are
building the ability to change one; §4 the JDK surface: `KEM` (JEP 452, JDK 21), ML-KEM (JEP 496),
ML-DSA (JEP 497), `KDF` (JEP 510), PEM encodings (JEP 470/524/538, still preview), hybrid TLS 1.3
(JEP 527, JDK 27); §5 a `sequenceDiagram` of a hybrid X25519+ML-KEM handshake showing which secret
feeds the KDF; §6 executable `KEM.getInstance("ML-KEM")` encapsulate/decapsulate and
`Signature.getInstance("ML-DSA")` sign/verify, plus key-size and handshake-byte numbers vs ECDHE;
§7 who has deployed hybrid TLS; §8 tradeoffs — key/signature size, handshake bytes, CPU, MTU effects;
§9 when to move now (long-lived confidentiality) vs when ECDHE is still fine; §10 pitfalls — treating PQ
as a drop-in, breaking on larger certificates, hardcoded algorithm strings, `SecurityManager`-era code
that no longer runs after JEP 486; §11 provider landscape (SunJCE vs BouncyCastle); §12 16 Q&As;
§13 a crypto-agility checklist; §14 planning a PQ migration for an existing TLS fleet.

---

## 2. Stale — verified CORRECTIONS

| File | Line / heading | What it says | What is true in Aug 2026 | Source |
|---|---|---|---|---|
| `foreign_function_and_memory_api/foreign_function_and_memory_api.md` | 652 (§12 Q&A on Panama vs Valhalla) | "Valhalla (value types / primitive classes, **still in preview as of Java 24**)" | Valhalla has **never** been in preview in any released JDK. JEP 401 *Value Objects (Preview)* is the first, integrated to mainline ~July 2026 and **targeted at JDK 28 (March 2027)**, alongside JEP 539 Strict Field Initialization. Today it exists only in EA builds behind `--enable-preview`. | <https://openjdk.org/jeps/401> · <https://www.infoq.com/news/2026/08/jep401-value-objects-preview/> |
| `bytecode_and_classfile/bytecode_and_classfile.md` | 655 (§12 javap Q&A) | "(Java 22+ **preview**) use the `java.lang.classfile` API (`ClassFile.parse`)" | The Class-File API was **finalized by JEP 484 in JDK 24**. It was preview in 22 (JEP 457) and 23 (JEP 466). On the section's own Java 25 baseline it is a standard API, no `--enable-preview`. The API entry point is `ClassFile.of().parse(...)`, not `ClassFile.parse`. | <https://openjdk.org/jeps/484> |
| `structured_concurrency_and_loom/structured_concurrency_and_loom.md` | 754 (§13 Best Practices item 10) | "**Java 21 is the target**: virtual threads are GA; `StructuredTaskScope` **and `ScopedValue` are in preview** and API may change through Java 24/25." | `ScopedValue` was **finalized in Java 25 by JEP 506** — which this same file states correctly at `:15` and `:639`. The Best Practices line contradicts the module's own timeline and names a target two LTS behind the section baseline. | <https://openjdk.org/jeps/506> |
| `reactive_programming/reactive_programming.md` | 24 | "Reactor Core **3.6+** (the version under **Spring Boot 3.x**) targets Java 8+" | Reactor Core is at **3.8.x** (2025.0.x release train), managed by **Spring Boot 4.x / Spring Framework 7** (Boot 4.0 GA Nov 2025; Boot 4.1 current as of mid-2026). Boot 3.x is no longer the current line. | <https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes> · <https://www.baeldung.com/spring-boot-4-spring-framework-7> |
| `case_studies/case_studies.md` | 15 | "`sun.misc.Unsafe`, which is **no longer reachable from application code**" | `sun.misc.Unsafe` is still reachable via the `jdk.unsupported` module. What changed is narrower: its **memory-access methods** were deprecated for removal (JEP 471, JDK 23) and `--sun-misc-unsafe-memory-access` **defaults to `deny` in JDK 26**, so those specific calls now throw. Field/object access via the class is a separate, still-present surface. | <https://openjdk.org/jeps/471> · <https://inside.java/2025/01/03/evolving-default-integrity/> |
| `jvm_internals/jvm_internals.md` | 55 (§4 GC table) | "G1 GC \| `-XX:+UseG1GC` — **the default** on any server-class machine" | **JEP 523 makes G1 the default GC in ALL environments** in JDK 27 (GA 2026-09-15) — the server-class-machine heuristic that could select Serial GC on small/containerized machines is removed. Worth updating now rather than in three weeks. | <https://www.happycoders.eu/java/java-27-features/> |
| `java9_to_21_features/java9_to_21_features.md` | 52, 471 | "still preview in Java 25 (JEP 505) and 26 (JEP 525)"; race joiner "is spelled `anySuccessfulResultOrThrow()` on the Java 25 LTS" | Both correct *as far as they go*, but incomplete: **JEP 533 is the seventh preview in JDK 27**, and JDK 26 renamed the joiner to `anySuccessfulOrThrow()` and added `Joiner.onTimeout()`. The file gives both spellings without consistently tying each to its release, which will read as a contradiction. | <https://www.happycoders.eu/java/java-26-features/> · <https://www.happycoders.eu/java/java-27-features/> |

### Two cross-file inconsistencies (not staleness — the section disagreeing with itself)

| Files | The disagreement | Fix |
|---|---|---|
| `performance_and_tuning/performance_and_tuning.md` (`:148`, `:588-595`, `:614`) vs its own sub-file `performance_and_tuning/jmx_and_management.md:367-371` | The module page never mentions `jcmd` at all and teaches `jmap -dump:live` / `jstack` / `jstat` as the primary local tools. The sub-file it owns states plainly that "`jcmd` is the modern umbrella that subsumes much of `jstack`/`jmap`". A reader gets opposite advice from a parent and its own child. | Lead with `jcmd` (`VM.heap_dump`, `Thread.print`, `GC.heap_info`, `JFR.start`) in the module page's §11 and §14; keep the legacy tools as "what you will still see in old runbooks". |
| `performance_and_tuning/performance_and_tuning.md:722` (case-study header) vs `java/CLAUDE.md` ("Java 25 LTS — the current baseline") and `testing_junit_mockito.md:666` (runs on Java 25) | The performance case study pins itself to "Java 17 LTS". Not wrong in isolation — a case study may target older infrastructure — but three files in one section name three different baselines. | Normalize to Java 25 unless the case study's point depends on 17, in which case say so in one line. |

Everything else I spot-checked was **already correct** and should not be touched: ZGC generational-only
since JEP 490/Java 24 (`jvm_internals:56`), string templates withdrawn with no replacement
(`strings_and_text:5,483`), `HttpClient` `AutoCloseable`/`close()`/`shutdown()` as Java 21
(`networking:559-561`), JUnit 6 with a Java 17 runtime baseline and deprecated Vintage
(`testing:7,533`), Mockito 5 inline mock maker as default plus the JEP 451 dynamic-attach warning
(`testing:444,474`), GraalVM 25.0 LTS train vs Innovation releases (`graalvm:66`), and
`reachability-metadata.json` as the current metadata format (`graalvm:47`).

---

## 3. Reordering — SEQUENCE and TIER changes

| Module | Current position / tier | Proposed | Why |
|---|---|---|---|
| `structured_concurrency_and_loom` | pos 26 / senior+principal | **pos 17**, immediately after `concurrency` | The section teaches nine modules of AQS/CompletableFuture/thread-pool craft before naming the model that has replaced most of it. In 2026 the virtual-thread answer comes *first* and the platform-thread machinery is the "why it used to be hard" backstory. Keeping it at 26 teaches the historical idiom as the current one — the exact failure the brief asks about. |
| `java22_to_26_features` (new) | — | **pos 11**, directly after `java9_to_21_features` | Continues the version narrative without breaking it. |
| `aot_startup_and_class_data_sharing` (new) | — | **pos 16**, directly *before* `graalvm_native_image` | A reader must see the cheap, no-closed-world startup fix before the expensive one. Today `graalvm_native_image` is presented as the only answer to slow startup. |
| `java_time_datetime` | tier `-` (Full only) | **`senior`** | Date/time is craft, not trivia, and it is the most common source of silent production correctness bugs (DST gaps, `Instant` vs `LocalDateTime`, `Clock` injection). An 826-line, 18-Q&A, 5-diagram module sitting off the senior path is the section's clearest mis-tier. |
| `networking_and_http_client` | tier `-` | **`senior`** | `HttpClient` + timeouts + connection pooling + retry is bread-and-butter senior craft, and this module is where HTTP/3 will land. |
| `foreign_function_and_memory_api` | tier `-` | **`principal`** | "Do we call native code, and at what integrity cost" is a judgment call, not a craft skill — and the `--enable-native-access` policy question is squarely principal. |
| `bytecode_and_classfile` | tier `-` | **`principal`** | Agents, instrumentation, and reachability are the substrate of every observability and native-image decision a principal makes. |
| `security_and_cryptography` | tier `principal` | **`senior, principal`** | AES-GCM vs CBC, password hashing, `SecureRandom` are asked at senior level; the PQ-migration half is principal. Both cuts apply. |
| `design_patterns_in_java` | tier `senior, principal` | **`senior`** only | `lld/` owns the full 23-pattern GoF catalogue with UML. The principal-tier *judgment* about pattern choice belongs there, not duplicated here. |
| `microservices_patterns` | tier `principal` | **`-`** (Full only) | Saga / outbox / idempotency are owned by `backend/microservices_fundamentals`, `backend/event_driven_fundamentals`, and `backend/distributed_transactions_and_consistency`. A pure-Java section should not carry a distributed-systems module on its principal path. |
| `performance_and_tuning/jvm_in_containers.md` (new sub-file) | — | `senior, principal` | |
| `performance_and_tuning/jfr_and_production_diagnostics.md` (new sub-file) | — | `senior` | |
| `security_and_cryptography/post_quantum_and_modern_jca.md` (new sub-file) | — | `principal` | |

### Proposed `STUDY_ORDER["java"]`

```js
  java: [
    "java/core_language","java/strings_and_text","java/generics_and_type_system","java/exceptions_and_io","java/json_processing_jackson",
    "java/java8_features","java/java_time_datetime","java/java_streams","java/functional_programming",
    "java/java9_to_21_features","java/java22_to_26_features","java/java_platform_module_system",
    "java/jvm_internals","java/bytecode_and_classfile","java/reference_types_and_cleaners",
    "java/aot_startup_and_class_data_sharing","java/graalvm_native_image",
    "java/concurrency","java/structured_concurrency_and_loom",
    "java/collections_internals","java/design_patterns_in_java",
    "java/performance_and_tuning","java/java_memory_model",
    "java/java_interview_patterns","java/testing_junit_mockito","java/logging","java/annotation_processing","java/build_tools_maven_gradle",
    "java/foreign_function_and_memory_api","java/reactive_programming",
    "java/networking_and_http_client","java/jdbc_and_database","java/security_and_cryptography","java/grpc_protobuf","java/microservices_patterns",
  ],
```

Two moves only, plus two insertions: `structured_concurrency_and_loom` jumps to sit beside
`concurrency`; `foreign_function_and_memory_api` slides into the tooling block it belongs to (it is a
platform-integration topic, not a concurrency one). Everything else keeps its relative order, so the
generated tier subsets stay ordered by construction.

---

## 4. Depth — THIN modules and BLOAT

| Module / file | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `case_studies/cross_cutting/jvm_tuning_and_gc_for_services.md` | 465 | 15 | Half the length of its three siblings (838 / 953 / 1053). Missing `## 4. Types` and `## 6. How It Works`. No AOT/CDS, no compact object headers, no container/cgroup heap sizing — the three things that dominate 2026 JVM tuning. | Expand to ~850; add §4 (collector selection matrix) and §6 (a real tuning session with `-Xlog:gc` output → diagnosis → flag change → measured delta). ~5h |
| `strings_and_text/strings_and_text.md` | 659 | 15 (floor) | Thinnest module page in the section. Correctly closes out string templates but stops there. No `String` intrinsics / `indexOf` vectorization, no `Normalizer` / NFC-vs-NFD equality trap, no `CharSequence`-based zero-copy parsing. | +200 lines, +3 Q&As. ~3h |
| `functional_programming/functional_programming.md` | 691 | 15 (floor) | Substantially overlaps `java8_features` (819) and `java_streams` (1038). Its genuinely unique content is custom `Collector` authoring, function composition, and immutability discipline — perhaps 250 lines. | **Merge**: fold custom Collectors + composition into `java_streams`, fold immutability into `core_language`, delete the module. Removes a Full-path-only module that pays for itself nowhere. ~5h including `STUDY_ORDER` / study-paths / README edits |
| `java9_to_21_features/java9_to_21_features.md` | 780 | 15 (floor) | Lines 517-772 are an embedded 255-line case study ("Migrating a REST API to Virtual Threads") — so the module has ~520 lines of actual module content covering 13 Java releases. Lowest topic density in the section. | Once `java22_to_26_features` exists, either promote the embedded case study to `case_studies/design_virtual_thread_migration.md` (it is already 11-section-shaped) or leave it and accept the module as a survey. Prefer promotion. ~4h |
| `performance_and_tuning/jmx_and_management.md` | 616 | 17 | Uses its own 12-heading structure rather than the canonical 14 — **legitimate**, sub-files are free-named. Content quality is high and its §9 already recommends bridging to Micrometer/Prometheus. The real gap is that it still opens by framing JMX as free-in-every-JVM rather than as a legacy bridge most 2026 services skip entirely. | Sharpen §9's opening, not the template. Also make it the canonical home for `jcmd`, which it already documents better than its parent. ~2h |
| `java_memory_model/java_memory_model.md` | 794 | 18 (at its 18+ floor, no headroom) | `Thread.onSpinWait()` appears **nowhere**, despite being the natural landing point of the barrier/busy-wait discussion. `sun.misc.Unsafe`'s `loadFence`/`storeFence`/`fullFence` are skipped entirely — the module jumps JSR-133 barriers → `VarHandle`, leaving a reader unable to read the legacy code they will actually meet. No forward pointer to value-object memory semantics under Valhalla. | +3 Q&As and ~120 lines in §6. ~2h |
| `java_interview_patterns/java_interview_patterns.md` | 751 | 16 | A module whose entire subject is immutable classes and Builders and it **never mentions `record`** — the language feature that replaced the hand-written immutable class for simple value types in Java 16. Also no sealed-interface factory dispatch as the modern alternative to enum factories. Its Effective Java table (`:38-51`) lists 12 items but Item 14 is cited only inline at `:571`. | Add a "when a record replaces this recipe, and when it does not" section; complete the item table. ~2.5h |
| `testing_junit_mockito/testing_junit_mockito.md` | 849 | 17 | Version currency is excellent (JUnit 6.x / Java 17 baseline / Vintage deprecated, Mockito 5.x inline maker, Testcontainers 2.x, AssertJ 3.27.x — all verified accurate). Missing entirely: **ArchUnit** (architecture-fitness testing is routine in 2026), `@TempDir`, JUnit's `@Suite`, and contract testing even as a pointer. PIT mutation testing is name-dropped once at `:658` with no coverage. | +180 lines, +3 Q&As. ~3h |
| `logging/logging.md` | 943 | 21 | The single most factually current file in the section — every version claim (Log4j 2.26.x, SLF4J 2.x ServiceLoader binding, ScopedValue/JEP 506 for MDC) verified accurate. One gap: OpenTelemetry appears only as a trace_id/span_id concept; no OTel Logback/Log4j2 appender artifact names and no OTel Java agent auto-instrumentation. | +80 lines in §11, +1 Q&A. ~1.5h |
| `strings_and_text/regex_engine_and_redos.md` | 856 | 18 | Missing `## 4. Types`, `## 7. Real-World Examples`, `## 14. Case Study`. Content quality is high; the template is just incomplete. | Add the 3 sections (§7 is easy — Cloudflare 2019, Stack Overflow 2016 are the canonical ReDoS outages). ~2h |
| All 8 case studies | 664-816 | 10-12 | **Every one is below the 900-1100 principal bar.** Four lack the required `## Intuition` opener: `design_circuit_breaker_java`, `design_di_container_java`, `design_snowflake_id_generator_java`, `design_thread_pool_java`. Only 3 of 8 mention virtual threads. `design_thread_pool_java` and `design_connection_pool` are the two where a 2026 reader will most notice the absence. | Add `## Intuition` to the four (~1h total). Raise each to 900+ by adding the Loom-era angle to §5 Design Decisions and §9 War Stories. ~2h per case study. |
| `design_patterns_in_java/design_patterns_in_java.md` | 894 | 16 | **Bloat.** `lld/` carries all 23 GoF patterns with UML and Java implementations; this module restates the catalogue. Meanwhile the *Java-idiom* layer that would justify its existence is barely there — `record` as the alternative to a hand-rolled Builder is absent, sealed-interface + pattern-matching-switch as the modern Visitor gets one line in Q11, and there is no acknowledgement that DI frameworks have superseded manual Factory/Singleton in practice. | Cut the catalogue restatement, build out the Java-idiom layer to ~600 lines. Drop from the principal tier (see §3). Cross-link `lld/` for the catalogue. ~5h |
| `microservices_patterns/microservices_patterns.md` | 876 | 17 | **Bloat by domain.** Saga, outbox, idempotency keys, strangler fig, bulkhead are all owned by `backend/`. The only genuinely Java-specific content is `ScopedValue`-based context propagation (9 hits) and Resilience4j wiring. | Either cut to ~400 lines of Java-implementation-only material, or delete and move the `ScopedValue` context-propagation content into `structured_concurrency_and_loom`. I recommend the latter. Drop from principal tier either way. ~4h |
| 28 of 35 module files | — | — | **Zero Effective Java citations**, against a `CLAUDE.md` rule that requires them. Only `core_language`, `java_interview_patterns`, `generics_and_type_system`, `exceptions_and_io`, `java8_features`, `design_patterns_in_java`, and `java_streams` (partially) comply. `concurrency` (Items 78-84), `collections_internals` (28-33), `java_streams` (45-48), `functional_programming` (42-44), `reference_types_and_cleaners` (Item 8 — literally "Avoid finalizers and cleaners"), and `json_processing_jackson` (85-90) are the most glaring. | A single **citation sweep**: add 3-6 inline `[Effective Java Item N]` references per file, at the exact claim each item supports. Purely additive, no restructuring, highly parallelizable. ~6h total across all 28 |

No module falls below 15 Q&As. All five modules with an 18+ floor (`concurrency` 21, `jvm_internals` 19,
`java_memory_model` 18, `collections_internals` 21, `generics_and_type_system` 19) meet it.

---

## 5. Cross-section notes

- **`lld/`** — owns the GoF catalogue (23 patterns, UML, Java). `java/design_patterns_in_java` duplicates
  it. Resolve by scope: `lld/` = the catalogue and pattern selection; `java/` = how modern Java idioms
  (records, sealed, lambdas, `ServiceLoader`) *replace* specific patterns. Cross-link both ways.
- **`backend/`** — owns `microservices_fundamentals`, `event_driven_fundamentals`,
  `distributed_transactions_and_consistency`, `fault_tolerance_patterns`, `connection_pooling_deep_dive`,
  `grpc_and_protobuf`, `http_protocols`, `performance_profiling`. `java/microservices_patterns` is a
  near-total duplicate of the first four and should shrink or go (see §4).
  `java/grpc_protobuf` is a *third* copy of gRPC alongside `backend/grpc_and_protobuf` and
  `spring/spring_grpc` — keep it, but confine it to the wire format, stub generation, and the
  threading/deadline model, and say so in its opening note the way `reactive_programming` does.
- **`spring/`** (39 modules) — the pure-Java boundary is being held well; `reactive_programming` opens
  with an explicit "Spring integration lives in `spring/spring_webflux`" note, which is the right
  pattern. Two things to hand over rather than duplicate: `spring/spring_native_graalvm` should
  cross-link the **new** `aot_startup_and_class_data_sharing` module (Spring Boot 4 ships CDS/AOT-cache
  support and that decision now has two branches, not one); and the Reactor version claim in §2 needs
  to agree with whatever `spring/` states for Spring Boot 4.x.
- **`cs_fundamentals/`** — owns `cryptography_fundamentals`, `memory_management_and_virtual_memory`,
  `processes_threads_and_context_switching`, `computer_architecture_and_memory_hierarchy`. The overlap
  with `java/security_and_cryptography` and `java/java_memory_model` is healthy (primitives vs JCA
  surface; hardware memory model vs JMM) and already cross-linked in `java/CLAUDE.md`. No action.
  The proposed post-quantum sub-file should cross-link `cs_fundamentals/cryptography_fundamentals`
  for the lattice primitives rather than re-teaching them.
- **New cross-link needed both ways**: `java/aot_startup_and_class_data_sharing` ↔
  `java/graalvm_native_image` ↔ `spring/spring_native_graalvm` ↔ `devops/` container startup content.
  Right now the section presents native image as the only startup answer.

---

## 6. Execution plan

**Wave 1 — corrections (fully parallel, no coordination, ~4h total).** Seven verified edits from §2.
Each is a 1-3 line change plus the `**Short:**` line above it where the answer moves — the audit owns
the Short. No `STUDY_ORDER`, no study-paths, no README impact.
1. `foreign_function_and_memory_api.md:652` — Valhalla status. *(0.5h)*
2. `bytecode_and_classfile.md:655` — Class-File API final in 24, `ClassFile.of().parse()`. *(0.5h)*
3. `structured_concurrency_and_loom.md:754` — align Best Practices with the file's own `:15` timeline. *(0.5h)*
4. `reactive_programming.md:24` — Reactor 3.8 / Spring Boot 4. *(0.5h)*
5. `case_studies/case_studies.md:15` — narrow the Unsafe claim to memory-access methods. *(0.5h)*
6. `jvm_internals.md:55` — G1 default in all environments (JEP 523, JDK 27). *(0.5h)*
7. `java9_to_21_features.md:52,471` — add the 7th preview and pin each joiner spelling to its release. *(1h)*

**Wave 2 — in-place additions (parallel across files, ~10h).** Each touches one existing module; all
add Q&As so all need `**Short:**` lines authored at the same time.
8. HTTP/3 in `networking_and_http_client`. *(2h)*
9. `--enable-native-access` policy in `foreign_function_and_memory_api`. *(1.5h)*
10. Virtual threads × pool in `jdbc_and_database`. *(1.5h)*
11. Compact object headers in `collections_internals` + `java_interview_patterns`. *(1.5h)*
12. Maven 4 / `mvnd` / Gradle configuration cache / SBOM / Central Portal in `build_tools_maven_gradle`. *(2.5h)*
13. Vector API + Valhalla status paragraph in `jvm_internals`. *(1h)*

**Wave 3 — new content (parallel, ~28h).** Each new file needs a line in the section README's
`<!-- study-paths -->` block **in the same commit**; the two new *modules* also need a
`STUDY_ORDER["java"]` entry (sub-files do not). Then `python3 game/extract.py --write-paths`
followed by `scripts/build_banks.sh`.
14. `java22_to_26_features/` module. **needs `STUDY_ORDER` + study-paths + section README table + root README** *(7h)*
15. `aot_startup_and_class_data_sharing/` module. **needs `STUDY_ORDER` + study-paths + section README table + root README** *(7h)*
16. `security_and_cryptography/post_quantum_and_modern_jca.md`. **needs study-paths line only** *(6h)*
17. `performance_and_tuning/jvm_in_containers.md`. **needs study-paths line only** *(5h)*
18. `performance_and_tuning/jfr_and_production_diagnostics.md`. **needs study-paths line only** *(6h)*

**Wave 4 — reordering and tiers (single serialized commit, ~2h).** Must be one commit because
`--strict` cross-checks `STUDY_ORDER`, the study-paths block, and the generated tier tables together.
19. Apply the `STUDY_ORDER["java"]` array from §3. **`game/app.js`**
20. Apply the 9 tier changes in the README `<!-- study-paths -->` block. **section README**
21. Run `python3 game/extract.py --write-paths`, then the full `scripts/build_banks.sh`.

**Wave 5 — depth and consolidation (parallel except where noted, ~40h).**
22. Effective Java citation sweep across 28 files — highly parallelizable, 6-8 files per agent. *(6h)*
23. Add `## Intuition` to the 4 case studies missing it. *(1h)*
24. Complete the template on `regex_engine_and_redos.md` (§4, §7, §14). *(2h)*
25. Expand `cross_cutting/jvm_tuning_and_gc_for_services.md` 465 → 850. *(5h)*
26. Expand `strings_and_text.md` 659 → 860. *(3h)*
27. Raise the 8 case studies to the 900-line bar, adding the Loom-era angle. *(14h — parallel, 2h each)*
28. `java_memory_model`: add `Thread.onSpinWait()` and the legacy `Unsafe` fence API. *(2h)*
29. `java_interview_patterns`: add "when a `record` replaces this recipe"; complete the EJ item table. *(2.5h)*
30. `testing_junit_mockito`: add ArchUnit, `@TempDir`, `@Suite`, real PIT coverage. *(3h)*
31. `logging`: add OTel appender artifacts and the OTel Java agent. *(1.5h)*
32. Reconcile the two cross-file inconsistencies from §2 — `jcmd` as primary in
    `performance_and_tuning`, and the Java 17 vs 25 baseline in its case study. *(1.5h)*

**Wave 6 — merges and deletes (serialized, ~14h).** These change the file inventory, so each is its own
commit with study-paths + `STUDY_ORDER` + both READMEs updated.
33. Merge `functional_programming` into `java_streams` + `core_language`, delete the module.
    **`STUDY_ORDER` + study-paths + section README + root README + `java/CLAUDE.md` module table** *(5h)*
34. Cut `design_patterns_in_java` to the Java-idiom layer; drop from principal tier. *(5h)*
35. Cut or dissolve `microservices_patterns`; move `ScopedValue` context propagation into
    `structured_concurrency_and_loom`. **same full update set if deleted** *(4h)*

**Wave 7 — optional new case studies (parallel, ~16h).**
36. `case_studies/design_structured_concurrency_aggregator.md`. *(6h)*
37. `case_studies/design_low_latency_market_data_handler.md`. *(6h)*
38. Promote the embedded virtual-thread migration case study out of `java9_to_21_features`. *(4h)*

**Total: ~115h if Waves 1-6 are executed in full; ~50h for Waves 1-4, which capture every verified
correction and every P0/P1 gap.** Waves 1, 2, 3, and 5 are internally parallel. Wave 4 must be a single
commit. Wave 6 must follow Wave 4 (it changes the same arrays). Every wave that adds or removes a file
requires the section README's `<!-- study-paths -->` block updated in the same commit or the Pages
deploy goes red.

---

## 7. Confidence and gaps

**High confidence.** The zero-coverage findings are mechanical and reproducible — I ran case-insensitive
regex sweeps over all 49 content files for each topic. CDS/AOT/Leyden, compact object headers,
post-quantum crypto, `--enable-native-access`, module import declarations, compact source files,
flexible constructor bodies, the Vector API, Maven 4, `mvnd`, and the Gradle configuration cache each
return **zero** hits section-wide. Line counts, heading inventories, Q&A counts, `**Short:**` counts,
and Mermaid counts are all script-derived. All seven §2 corrections were web-verified against a primary
or near-primary source.

**Medium confidence.** The tier reassignments in §3 are editorial judgment against the repo's own
senior-is-craft / principal-is-judgment definition; the `java_time_datetime` → senior and
`microservices_patterns` → Full-only calls are the ones most likely to be argued. Line estimates for new
files are calibrated against the section's 837-line average, not measured.

**What I could not verify.**
1. **Maven 4 GA.** Search returned 4.0.0-rc-4 (June 2025) as the latest with no announced GA date. If
   Maven 4 has since gone GA, item 12 becomes more urgent and its framing changes from "know it is
   coming" to "know it shipped". Re-check <https://maven.apache.org/docs/history.html> before writing.
2. **Current Gradle major.** The build module names no Gradle version at all, so I could not detect
   drift — only absence. Confirm the current Gradle line and whether the configuration cache is now
   on by default before writing item 12.
3. **JDK 27 as shipped.** GA is 2026-09-15, three weeks out. I worked from the frozen 9-JEP feature
   set. JEP 523 (G1 default) and JEP 534 (compact object headers by default) are listed as finalized
   and are very unlikely to slip, but the §2 row for `jvm_internals:55` should be phrased as
   "in JDK 27" rather than "today" until GA.
4. **Jackson 3 depth.** `json_processing_jackson.md` has 13 Jackson-3 hits so it is clearly migrated,
   but I did not read closely enough to confirm the `tools.jackson` package rename, the
   `JsonMapper.builder()` default changes, or whether `FAIL_ON_UNKNOWN_PROPERTIES`'s new default is
   stated correctly. Worth one focused read.
5. **Case-study §4 executable-code and §6 named-company checks.** I verified heading structure, line
   counts, Q&A counts, and virtual-thread coverage across all 8 mechanically, but did not read each in
   full to score the "executable code in §4 / named companies in §6 / quantified impact in §9" bar. My
   Wave-5 estimate of 2h per case study assumes those are mostly present; if they are not, that wave
   roughly doubles. The 8 case studies and the 4 `cross_cutting/` files are the only part of this
   section I did not get a full read-through on — every module file was either read directly or
   profiled in depth by a subagent.

**A note on method.** Rate limits meant only one of seven planned profiling subagents launched, so the
per-file inventory (headings, Q&A counts, `**Short:**` coverage, diagram counts, Effective Java
citations) is script-derived across all 49 files rather than read by hand, with one subagent's deep read
of 7 files used to calibrate it. Where the two disagreed, the subagent was right and the script was
wrong in the safe direction: it flagged `jmx_and_management.md` as missing four template sections when
that sub-file legitimately uses its own 12-heading structure. Treat every "missing heading" claim about
a **sub-file** as needing that same check; the claims about **module pages** are sound, because all 33
module pages carry all 14 headings.
