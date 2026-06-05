# Java Case Studies — Learning Path Index

Eight principal-grade case studies covering the core Java engineering patterns that appear most
frequently in senior-engineer interviews and production system design discussions. Each study uses
the 11-section principal template with executable Java code, concrete numbers, and production
war stories.

The studies are grouped into four engineering concerns: resource management, concurrency
primitives, design patterns and wiring, and distributed systems patterns. Reading them in the
order shown in the Dependency Map section below will give you the most transfer of knowledge
between studies.

All code in the studies targets Java 17+ (LTS). Concurrency examples use the java.util.concurrent
package exclusively — no sun.misc.Unsafe and no raw synchronized blocks except where demonstrating
a broken pattern before the fix.

---

## Quick Start

Read these three first if you only have time for a focused session:

| File | Why Read It First |
|------|------------------|
| [Connection Pool](design_connection_pool.md) | Demonstrates bounded-resource management, object lifecycle, and thread-safe acquisition — patterns that underpin nearly every other study here. |
| [Thread Pool](design_thread_pool_java.md) | Explains how the JDK ThreadPoolExecutor actually works, including queue saturation, rejection policies, and sizing heuristics that interviewers test directly. |
| [Circuit Breaker](design_circuit_breaker_java.md) | Shows state-machine design in pure Java and teaches failure-isolation patterns used in every distributed service. |

---

## Full Learning Path

### Group 1 — Resource Management

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [Connection Pool](design_connection_pool.md) | Bounded resource lifecycle | How to implement safe acquisition, release, and eviction of expensive resources; covers ReentrantLock, Condition variables, and health-check background threads. |
| [Thread Pool](design_thread_pool_java.md) | Worker-thread lifecycle and saturation | Internals of ThreadPoolExecutor including corePoolSize, maximumPoolSize, keepAliveTime, and the interaction between the queue and the thread-count control loop. |
| [Backpressure and Bounded Resources](cross_cutting/backpressure_and_bounded_resources.md) | Queue saturation and producer throttling | Cross-cutting primitive: how to propagate backpressure from bounded queues back to producers without deadlock. |

### Group 2 — Concurrency Primitives

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [Rate Limiter](design_rate_limiter_java.md) | Token-bucket and sliding-window concurrency | Thread-safe counter management, compare-and-swap, and clock-skew-resistant sliding-window implementation backed by AtomicLong and LongAdder. |
| [Snowflake ID Generator](design_snowflake_id_generator_java.md) | Lock-free monotonic ID generation | Bit-packing, clock rollback detection, and sequence exhaustion handling under high concurrency using AtomicLong and strict happens-before reasoning. |
| [LRU Cache](design_lru_cache_java.md) | Concurrent cache eviction | How to implement a thread-safe LRU eviction policy using LinkedHashMap, then upgrade it to a segmented ConcurrentHashMap variant for high-throughput scenarios. |

### Group 3 — Design Patterns and Wiring

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [DI Container](design_di_container_java.md) | Reflection-based dependency wiring | How Spring-style IoC containers discover, instantiate, and inject beans using reflection, annotation processing, and topological sort for cycle detection. |
| [Event Bus](design_event_bus.md) | Publish-subscribe decoupling | Synchronous and asynchronous event dispatch, listener registration via generics and reflection, and safe removal during iteration. |

### Group 4 — Distributed Systems Patterns

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [Circuit Breaker](design_circuit_breaker_java.md) | Failure isolation via state machine | CLOSED → OPEN → HALF_OPEN transitions, per-caller failure-rate windows, and thread-safe state updates using AtomicReference and scheduled probes. Covers half-open probe sizing, bulkhead integration, and how to avoid thundering-herd on recovery. |

---

## Cross-Cutting / Shared Primitives

These files live in `cross_cutting/` and should be read just-in-time alongside the case studies
listed in the "Best Read With" column.

| File | What It Covers | Best Read With |
|------|---------------|---------------|
| [JVM Tuning and GC for Services](cross_cutting/jvm_tuning_and_gc_for_services.md) | G1/ZGC configuration, heap sizing, pause-time budgets, TLAB tuning | Connection Pool, Thread Pool — both create objects at high frequency |
| [Benchmarking with JMH](cross_cutting/benchmarking_with_jmh.md) | JMH harness setup, avoiding dead-code elimination, measuring throughput and latency percentiles | Rate Limiter, LRU Cache, Snowflake ID — all have tight latency requirements |
| [Concurrency Memory Visibility Primitives](cross_cutting/concurrency_memory_visibility_primitives.md) | volatile, VarHandle, Unsafe, happens-before proofs, safe publication idioms | Rate Limiter, Snowflake ID, Circuit Breaker — all rely on lock-free or partially-locked designs |
| [Backpressure and Bounded Resources](cross_cutting/backpressure_and_bounded_resources.md) | Bounded queues, producer throttling, rejection strategies, load-shedding | Thread Pool, Connection Pool — both must handle queue saturation gracefully |

---

## Dependency Map

Reading order matters when case studies build conceptually on one another.

```
design_connection_pool
  └── design_thread_pool_java        (same bounded-resource pattern; thread sizing math)
        └── design_snowflake_id_generator_java  (concurrent access to shared state under load)

design_rate_limiter_java
  └── design_circuit_breaker_java    (state machine extension of per-call failure tracking)

design_di_container_java
  └── design_event_bus               (listener registration mirrors bean wiring; reflection reuse)

design_lru_cache_java                (self-contained; read after connection_pool for contrast)
```

The dependency arrows show the minimum prerequisite; you do not need to read every ancestor before
starting a study, but the concepts will land better if you do.

Recommended linear order for a full pass:

```
design_connection_pool
  → design_thread_pool_java
    → design_rate_limiter_java
      → design_circuit_breaker_java
        → design_lru_cache_java
          → design_snowflake_id_generator_java
            → design_di_container_java
              → design_event_bus
```

---

---

## Interview Prep Shortcuts

For each question below, open the linked file and focus on Sections 3 (High-Level Architecture),
5 (Design Decisions and Tradeoffs), and 11 (Interview Discussion Points).

| "Design X" Interview Question | Best Case Study File |
|-------------------------------|----------------------|
| Design a database connection pool | [design_connection_pool.md](design_connection_pool.md) |
| Design a rate limiter (token bucket / sliding window) | [design_rate_limiter_java.md](design_rate_limiter_java.md) |
| Design a thread pool executor from scratch | [design_thread_pool_java.md](design_thread_pool_java.md) |
| Design a circuit breaker | [design_circuit_breaker_java.md](design_circuit_breaker_java.md) |
| Design an LRU cache | [design_lru_cache_java.md](design_lru_cache_java.md) |
| Design a distributed unique ID generator (Snowflake) | [design_snowflake_id_generator_java.md](design_snowflake_id_generator_java.md) |
| Design a lightweight IoC / dependency injection container | [design_di_container_java.md](design_di_container_java.md) |
| Design an in-process event bus / pub-sub system | [design_event_bus.md](design_event_bus.md) |

---

## Build Manifest

All eight case studies are complete. Each uses the 11-section principal template: Requirements
Clarification, Scale Estimation, High-Level Architecture, Component Deep Dives, Design Decisions
and Tradeoffs, Real-World Implementations, Technologies and Tools, Operational Playbook, Common
Pitfalls and War Stories, Capacity Planning, and Interview Discussion Points.

| File | Template | Q&As | Status |
|------|----------|------|--------|
| [design_connection_pool.md](design_connection_pool.md) | 11-section principal | ~11 | DONE |
| [design_rate_limiter_java.md](design_rate_limiter_java.md) | 11-section principal | ~10 | DONE |
| [design_event_bus.md](design_event_bus.md) | 11-section principal | ~10 | DONE |
| [design_lru_cache_java.md](design_lru_cache_java.md) | 11-section principal | ~10 | DONE |
| [design_thread_pool_java.md](design_thread_pool_java.md) | 11-section principal | ~11 | DONE |
| [design_di_container_java.md](design_di_container_java.md) | 11-section principal | ~10 | DONE |
| [design_circuit_breaker_java.md](design_circuit_breaker_java.md) | 11-section principal | ~10 | DONE |
| [design_snowflake_id_generator_java.md](design_snowflake_id_generator_java.md) | 11-section principal | ~10 | DONE |
