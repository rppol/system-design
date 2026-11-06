# Rust Case Studies - Learning Path

Seven **Rust-runtime** case studies. They stop where [hld/](../../hld/README.md) starts: none of them asks "how do you shard this" or "what is your consistency model". Each asks a question you can answer only if you know what Rust does inside one process: what a future costs per connection, which type owns a buffer and on which thread it drops, where `unsafe` sits and who reviewed it, and what the compiler refuses to let you get wrong.

If a design question is about topology, partitioning or consensus, it belongs in `hld/`. If it is about memory per connection, a lifetime on a borrowed read, a wheel matrix, or the seam where Rust meets a host process, it belongs here.

Each study names its Go or Python sibling in its `## Intuition` and does not repeat it. A Rust design has no garbage collector, so each cost shows up in a type or in code you can read.

---

## 1. Quick Start - read these three first

| Case Study | Why this one first |
|-----------|--------------------|
| [Design a High-Performance L7 Proxy](design_a_high_performance_l7_proxy.md) | States the section's thesis most directly: with no collector, memory per connection is the size of a future plus the buffers it owns, and the compiler knows that number. At 200,000 connections and 1M req/s on 32 cores, each byte per connection and each shared lock shows up in the budget. |
| [Design an Async Event Ingest Pipeline](design_an_async_event_ingest_pipeline.md) | Teaches two promises a pipeline keeps with no collector to help: a fixed memory bound whatever the downstream does, and no offset committed before its batch is durable. Bounded channels, byte permits and cancel safety in one design. |
| [Design a PyO3 Python Extension](design_a_pyo3_python_extension.md) | A common way for Rust to reach production in a company that does not write Rust elsewhere. Builds on the PyO3 material in `python/` and covers what that page does not: the crate under the binding and the release matrix of 24 wheels. |

---

## 2. Full Learning Path

Grouped by engineering concern. Within a group, read in the order given.

### Throughput and latency - making one process do more

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a High-Performance L7 Proxy](design_a_high_performance_l7_proxy.md) | Memory and latency per connection | About 1M req/s on 32 cores, 200,000 connections, under 2 ms of added p99. Work stealing versus thread-per-core, a hyper and tower stack, `Bytes` zero-copy forwarding, per-worker pools, config reload through arc-swap, and cancel-safe forwarding. Sibling: [Go gRPC edge proxy](../../go/case_studies/design_a_grpc_edge_proxy.md). |
| [Design an Async Event Ingest Pipeline](design_an_async_event_ingest_pipeline.md) | Backpressure and a hard memory bound | About 500,000 events/s from Kafka into object storage. Bounded mpsc backpressure, size-or-time batching, zstd on `spawn_blocking`, `TaskTracker` shutdown, cancel-safe offset commit and at-least-once delivery. A batch owns its bytes, its byte-budget permit and its offset range. Sibling: [Go high-throughput ingest service](../../go/case_studies/design_a_high_throughput_ingest_service.md). |

### Interop and tooling - Rust inside and beside other ecosystems

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a PyO3 Python Extension](design_a_pyo3_python_extension.md) | Shipping native code to 10M installs a month | A Rust BPE tokenizer on PyPI with about 10 million monthly installs. The Rust crate under the binding, abi3 versus per-version wheels, free-threaded wheels, the manylinux, musllinux, macOS and Windows matrix, release engineering and honest benchmarking. Builds on [C extensions and native interop](../../python/c_extensions_and_native_interop/c_extensions_and_native_interop.md) and never restates it. |
| [Design a Fast Developer Tool](design_a_fast_developer_tool.md) | Wall-clock time over 5M lines | A ruff, uv or ripgrep-class linter that checks 5 million lines in under a second. A parallel walk with `ignore`, mmap versus read, arena trees with borrowed tokens, interning, rayon, a content-hash cache, salsa-style incremental recompute and cargo-dist releases. Sibling: [Go cross-platform CLI tool](../../go/case_studies/design_a_cross_platform_cli_tool.md). |

### Storage and constrained targets - where the type system carries the contract

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a Zero-Copy Storage Engine](design_a_zero_copy_storage_engine.md) | Lifetimes as an API contract | A redb or fjall-shaped key-value store. A borrowed read is tied to its read transaction by a lifetime, so the borrow checker rejects a slice that outlives the transaction. mmap soundness, io_uring with owned buffers, copy-on-write B-tree MVCC, crash consistency, `Send` and `Sync` on transaction types, and fuzz, Miri, loom and deterministic simulation in CI. Sibling: [Go embedded KV store](../../go/case_studies/design_an_embedded_kv_store.md). |
| [Design a Sensor Node Firmware](design_a_sensor_node_firmware.md) | RAM, microamps and safe updates on 256 KB | `no_std` and Embassy on a microcontroller with 256 KB of RAM, running for years on a coin cell. A microamp sleep budget, over-the-air updates with embassy-boot A/B slots, defmt and probe-rs, host-side tests through embedded-hal traits, hardware-in-the-loop CI and flip-link. No Go or Python sibling exists. |

### Adoption - bringing Rust into an existing organization

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design an Incremental Rust Migration](design_an_incremental_rust_migration.md) | Seam choice and rollback | Moving the PDF-decoder slice of a 70,000-line C++ library, called from Python workers, to Rust in a 40-engineer organization. A candidate scorecard, an FFI seam versus a service seam, ramp-up and review capacity, CI compile cost, the unsafe policy at the boundary, success metrics and rollback. The language comparison it builds on is [Go vs Java and Python](../../go/go_vs_java_and_python/go_vs_java_and_python.md). |

---

## 3. Cross-Cutting / Shared Primitives

Two shared references that the case studies cite and do not restate. Both live under `cross_cutting/`, which is in `CS_EXCLUDE_DIRS`. They are not listed in the section README's `<!-- study-paths -->` block and never enter the question bank. Benchmarking has no cross-cutting file here: [profiling and benchmarking](../profiling_and_benchmarking/profiling_and_benchmarking.md) owns it.

| File | Becomes relevant when | Cited by |
|------|----------------------|----------|
| [memory_budgets_without_a_gc](cross_cutting/memory_budgets_without_a_gc.md) | A service runs in a container with a memory limit. RSS versus live allocations, glibc arena fragmentation and `glibc.malloc.arena_max`, jemalloc versus mimalloc, why the OOM kill comes from the kernel and not from `handle_alloc_error`, bounded queues and byte semaphores as memory caps, and `Arc` cycle leaks. The contrast with Go's `GOMEMLIMIT` is in [gc_and_memory_budgets](../../go/case_studies/cross_cutting/gc_and_memory_budgets.md). | All seven case studies |
| [unsafe_review_and_soundness_checklist](cross_cutting/unsafe_review_and_soundness_checklist.md) | A change adds or edits `unsafe`, an `unsafe impl Send`, or an FFI binding. SAFETY comments and the clippy lints that enforce them, the module-privacy boundary, Miri in CI and what it cannot run, sanitizers, cargo-geiger and cargo-vet, a sign-off policy, and four RustSec and std advisories mapped to checklist rows. | All seven case studies |

---

## 4. Dependency Map

Every study stands alone. The arrows show which one teaches a prerequisite idea best.

```
   memory_budgets_without_a_gc                    unsafe_review_and_soundness_checklist
   (RSS vs live, bounded queues)                  (SAFETY, Miri, sign-off)
              |                                              |
      +-------+------------------+               +-----------+------------+
      |                          |               |                        |
      v                          v               v                        v
 design_an_async_event_    design_a_high_    design_a_pyo3_        design_a_zero_copy_
 ingest_pipeline           performance_      python_extension      storage_engine
 (backpressure,            l7_proxy          (wheels, abi3,        (lifetimes on reads,
  memory bound)            (per-connection    the seam)             mmap, io_uring)
      |                     memory)              |                        |
      +------------+------------+                |                        v
                   |                             |              design_a_fast_developer_tool
                   v                             v              (arenas, borrowed tokens)
          design_an_incremental_rust_migration <-+
          (seam choice, review capacity, rollback)

   design_a_sensor_node_firmware  (no allocator, no runtime: read at any point)
```

`design_a_sensor_node_firmware` sits off the main chain. It has no heap budget to manage because every buffer size is a compile-time constant, which makes it the far end of the memory argument in `memory_budgets_without_a_gc`.

---

## 5. Interview Prep Shortcuts

| "Design X" question | Best case study | What it gives you |
|---------------------|-----------------|-------------------|
| "Build a proxy, sidecar or API gateway in Rust" | [High-Performance L7 Proxy](design_a_high_performance_l7_proxy.md) | Memory per connection from future sizes, work stealing vs thread-per-core, arc-swap reload, cancel-safe forwarding |
| "Design a pipeline from Kafka to S3 that never loses data" | [Async Event Ingest Pipeline](design_an_async_event_ingest_pipeline.md) | Bounded channels, byte permits, size-or-time batching, cancel-safe offset commit |
| "Speed up our Python library with Rust" | [PyO3 Python Extension](design_a_pyo3_python_extension.md) | abi3 vs per-version wheels, the wheel matrix, free-threaded builds, honest benchmarks |
| "Build a linter or formatter that runs in under a second" | [Fast Developer Tool](design_a_fast_developer_tool.md) | Parallel walk, arena trees, interning, rayon, content-hash cache |
| "Design an embedded key-value store" | [Zero-Copy Storage Engine](design_a_zero_copy_storage_engine.md) | Read lifetimes tied to transactions, mmap soundness, CoW B-tree MVCC, Miri and loom in CI |
| "Should we rewrite this service in Rust?" | [Incremental Rust Migration](design_an_incremental_rust_migration.md) | Candidate scorecard, FFI seam vs service seam, review capacity, rollback |
| "Design firmware for a battery-powered sensor" | [Sensor Node Firmware](design_a_sensor_node_firmware.md) | Static RAM budget, microamp sleep budget, A/B OTA, hardware-in-the-loop CI |
| "Why is our Rust service getting OOM-killed with no leak?" | [memory_budgets_without_a_gc](cross_cutting/memory_budgets_without_a_gc.md) | RSS vs live bytes, glibc arenas, allocator swap, admission control as the memory cap |
| "How do you review unsafe code?" | [unsafe_review_and_soundness_checklist](cross_cutting/unsafe_review_and_soundness_checklist.md) | The module as the unsafe boundary, SAFETY arguments, Miri limits, sign-off tiers |

---

## Maintenance

Adding a case study means updating all five sections above in the same commit: Quick Start if it displaces an entry, the Full Learning Path group it belongs to, the Cross-Cutting table if it cites a new shared primitive, the dependency map, and an Interview Prep row. Also add it to the `<!-- study-paths -->` block in [the section README](../README.md) with its tiers, unless it lives under `cross_cutting/`, which is excluded.
