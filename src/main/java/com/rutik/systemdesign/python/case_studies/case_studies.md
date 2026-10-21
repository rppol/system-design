# Python — Case Studies

End-to-end pure-Python design walkthroughs, all on the repo's 11-section principal
template. Each one takes a problem an interviewer actually poses — "make this batch job
faster", "this logger is dropping lines under load" — and works it from requirements through
capacity planning, in Python, with measured numbers rather than asserted ones.

These are **library and runtime design** studies. They deliberately stop at the process
boundary: anything that becomes a distributed-systems problem is the `hld/` and `backend/`
sections' territory, and each file cross-links rather than restates it.

---

## Quick Start

Read these three first, in this order:

1. **[Design a CPU-Bound Batch Pipeline](design_a_cpu_bound_batch_pipeline.md)** — the one that
   forces you to say out loud why threads do not help, and what free-threading changes about
   that answer. Every other study assumes you have internalised the GIL boundary.
2. **[Design a High-Throughput Logging Pipeline](design_a_high_throughput_logging_pipeline.md)**
   — backpressure, bounded queues, and the drop-vs-block decision, in the one subsystem every
   service has and nobody designs.
3. **[Design a Validation Library](design_a_validation_library.md)** — the descriptor protocol,
   `__set_name__`, and why the fast path has to be code you generate rather than code you
   interpret.

---

## Full Learning Path

### Performance and the GIL Boundary

| Case Study | Primary Concern | What It Teaches |
|---|---|---|
| [Design a CPU-Bound Batch Pipeline](design_a_cpu_bound_batch_pipeline.md) | Parallelism that actually parallelises | Why a thread pool leaves a CPU-bound job exactly as slow, process pools and their pickling tax, shared memory for large arrays, chunk sizing, and what the free-threaded build does and does not change |

### Memory and the Object Model

| Case Study | Primary Concern | What It Teaches |
|---|---|---|
| [Design a Memory-Efficient Cache](design_a_memory_efficient_cache.md) | A bounded cache that does not tax the hot path | Measuring real memory with `tracemalloc` instead of `sys.getsizeof()`, `__slots__` savings on 3.11+, `functools.lru_cache` internals and its lock, `WeakValueDictionary`, cache-made reference cycles, and what free-threaded CPython changes for an unsynchronized cache |

### Throughput and Backpressure

| Case Study | Primary Concern | What It Teaches |
|---|---|---|
| [Design a High-Throughput Logging Pipeline](design_a_high_throughput_logging_pipeline.md) | Never blocking the request path | `QueueHandler`/`QueueListener`, bounded queues, the drop-oldest vs block decision as a product choice, batched flushes, and why the handler must never do I/O on the caller's thread |
| [Design a Streaming ETL Pipeline](design_a_streaming_etl.md) | Constant memory over unbounded input | Generator pipelines, `itertools` composition, chunked reads, where laziness silently breaks, and checkpointing a stream you cannot replay |

### API and Extension Design

| Case Study | Primary Concern | What It Teaches |
|---|---|---|
| [Design a Plugin System](design_a_plugin_system.md) | Extension without a rewrite | Entry points vs directory scanning, import-time side effects, `importlib` loading, versioned plugin contracts, and failure isolation when third-party code runs in your process |
| [Design a Validation Library](design_a_validation_library.md) | A declarative API with a fast path | Descriptors and `__set_name__`, `__init_subclass__`, building the validator once at class-creation time, error aggregation, and where the interpreted approach loses to generated code |

---

## Cross-Cutting / Shared Primitives

| File | When it becomes relevant |
|---|---|
| [Choosing a Concurrency Model](cross_cutting/choosing_a_concurrency_model.md) | Before picking threads, processes, `asyncio`, sub-interpreters, or the free-threaded build for any of these workloads: what the workload waits on, measured, and what each model costs in memory and isolation |
| [Benchmarking Python Correctly](cross_cutting/benchmarking_python_correctly.md) | **Before you believe any number in any of these files.** `timeit` vs `perf_counter`, warm-up and the adaptive interpreter, why a single run is noise, how to state the machine, and the measurement mistakes that make an optimisation look like a win |

---

## Dependency Map

```
design_a_cpu_bound_batch_pipeline        (root — the GIL boundary)
  |
  +--> design_a_high_throughput_logging_pipeline   (bounded queues, backpressure)
  |       |
  |       +--> design_a_streaming_etl               (constant-memory pipelines)
  |
  +--> design_a_plugin_system                       (import machinery, isolation)
  |       |
  |       +--> design_a_validation_library          (class-creation-time construction)
  |
  +--> design_a_memory_efficient_cache              (object memory, free-threaded safety)

cross_cutting/choosing_a_concurrency_model   -- prerequisite for the pipeline studies
cross_cutting/benchmarking_python_correctly  -- prerequisite for ALL of the above
```

Read this as a concept-prerequisite order, not a citation graph: each arrow says what you
should already understand, not that one file references another.

---

## Interview Prep Shortcuts

| "Design X" prompt | Start here |
|---|---|
| Speed up a CPU-bound Python job / why doesn't threading help? | [design_a_cpu_bound_batch_pipeline](design_a_cpu_bound_batch_pipeline.md) |
| Design a logging system that never blocks the request path | [design_a_high_throughput_logging_pipeline](design_a_high_throughput_logging_pipeline.md) |
| Process a file larger than memory / design an ETL pipeline | [design_a_streaming_etl](design_a_streaming_etl.md) |
| Design a plugin or extension system | [design_a_plugin_system](design_a_plugin_system.md) |
| Design a schema-validation library (a mini-Pydantic) | [design_a_validation_library](design_a_validation_library.md) |
| Design an in-process cache that stays bounded / why is my cache leaking memory? | [design_a_memory_efficient_cache](design_a_memory_efficient_cache.md) |
| Threads, processes, or asyncio for this workload? | [choosing_a_concurrency_model](cross_cutting/choosing_a_concurrency_model.md) |
| How would you prove this optimisation actually worked? | [benchmarking_python_correctly](cross_cutting/benchmarking_python_correctly.md) |

---

## Back to the Python Section

[Python Master Index](../README.md)
