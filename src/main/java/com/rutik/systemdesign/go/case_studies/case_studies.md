# Go Case Studies — Learning Path

Six case studies, all **Go-runtime** studies rather than architecture studies. They stop
where [hld/](../../hld/README.md) starts: none of them asks "how do you shard this" or
"what is your consistency model". Each asks a question you can only answer if you know how
the Go runtime behaves under load — how much a goroutine costs, when the collector scans
your pointers, what the scheduler does when you block, and which allocation you failed to
avoid.

That is the boundary. If a design question is about topology, partitioning or consensus, it
belongs in `hld/`. If it is about what happens inside one Go process at 500k events per
second, it belongs here.

---

## 1. Quick Start — read these three first

| # | Case Study | Why this one first |
|---|-----------|--------------------|
| 1 | [Design a High-Throughput Ingest Service](design_a_high_throughput_ingest_service.md) | The purest expression of the section's thesis: one binary, 500k events/s, and every design decision falling out of allocation rate and backpressure arithmetic. Read it and the rest of the section makes sense. |
| 2 | [Design a Realtime WebSocket Fan-out](design_a_realtime_websocket_fanout.md) | Teaches goroutine cost as a *budget* — two goroutines per connection times a million connections is a number you either did or did not do. The slow-consumer eviction decision is the most transferable idea in the section. |
| 3 | [Design a Kubernetes Controller](design_a_kubernetes_controller.md) | The most likely thing you will actually be asked to build in Go, and the one place where "level-triggered, not edge-triggered" earns its keep. Also the clearest example of idempotence as a design requirement rather than a nicety. |

---

## 2. Full Learning Path

Grouped by engineering concern. Within a group, read in the order given.

### Throughput and allocation — making one process do more

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a High-Throughput Ingest Service](design_a_high_throughput_ingest_service.md) | Allocation rate and backpressure | 500,000 events/second into a single Go binary. Builds a zero-allocation hot path with pooled decode buffers, sizes a bounded worker pool from measurement, shards batching to kill mutex contention, and sets the `GOMEMLIMIT` budget that stops the collector death-spiralling. Ends with the profiling loop that found each win. |
| [Design an Embedded KV Store](design_an_embedded_kv_store.md) | The GC as a design constraint | An LSM store in the shape of Badger or Pebble. The thesis: millions of live pointers wreck GC scan time, so the index stores integer offsets into an off-heap value log rather than pointers. Covers mmap versus explicit `read` and what each costs you inside a goroutine, plus WAL fsync and group commit. |

### Connection scale — making one process hold more

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a Realtime WebSocket Fan-out](design_a_realtime_websocket_fanout.md) | Goroutine cost at a million connections | Two goroutines per connection at 2 KB of initial stack each, times 1M connections, is the arithmetic that decides the whole architecture. Weighs goroutine-per-connection against an epoll reactor, shards the connection registry, and treats slow-consumer eviction as the central design decision rather than an afterthought. |
| [Design a gRPC Edge Proxy](design_a_grpc_edge_proxy.md) | Latency under GC at the edge | An L7 Go proxy that forwards arbitrary methods without generated stubs, using a raw codec so the data path never marshals. Hot-reloads config through an atomic pointer swap so the data path takes no lock, restarts without dropping streams, and treats p99-under-GC as a first-class budget. |

### Tooling and control planes — the other half of what Go is used for

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a Kubernetes Controller](design_a_kubernetes_controller.md) | Level-triggered reconciliation | A production controller over a CRD. Reconciles desired against actual rather than processing events, so a missed event is survivable and every reconcile must be idempotent. Covers the informer cache, the rate-limited workqueue, finalizers and the wedge they cause, leader election, and the API-server QPS budget. |
| [Design a Cross-Platform CLI Tool](design_a_cross_platform_cli_tool.md) | Single-binary distribution | The `kubectl`/`terraform`/`gh` shape. Command trees, a resolved configuration-precedence order, output that is human-readable on a TTY and machine-readable in a pipe, cancellation that cleans up partial work, and a release matrix that stays free because `CGO_ENABLED=0` keeps cross-compilation free. |

---

## 3. Cross-Cutting / Shared Primitives

Two shared references the case studies cite rather than restate. Both live under
`cross_cutting/`, which is in `CS_EXCLUDE_DIRS` — they are **not** listed in the section
README's `<!-- study-paths -->` block and never enter the question bank.

| File | Becomes relevant when | Cited by |
|------|----------------------|----------|
| [benchmarking_go_correctly](cross_cutting/benchmarking_go_correctly.md) | The moment you claim an optimisation worked. `b.Loop`, `benchstat` with `-count`, and the Go-specific noise sources — GC timing between runs, size-class boundaries, `GOMAXPROCS` drift — that make a single run meaningless. | Ingest service, KV store, edge proxy |
| [gc_and_memory_budgets](cross_cutting/gc_and_memory_budgets.md) | The moment a service runs in a container with a memory limit. The `GOGC` × `GOMEMLIMIT` arithmetic, live-heap estimation under real load, and the death spiral when the live heap approaches the limit. | Ingest service, WebSocket fan-out, KV store, edge proxy |

---

## 4. Dependency Map

Every study stands alone, but the arrows show which one teaches a prerequisite idea best.

```
                      benchmarking_go_correctly
                      (measure before you claim)
                                 |
              +------------------+------------------+
              |                                     |
    gc_and_memory_budgets                           |
    (GOGC x GOMEMLIMIT)                             |
              |                                     |
              v                                     v
    design_a_high_throughput_ingest_service   design_a_cross_platform_cli_tool
    (allocation rate, backpressure)           (single binary, no runtime concerns)
              |
      +-------+-------+-------------------+
      |               |                   |
      v               v                   v
  design_a_realtime   design_a_grpc    design_an_embedded
  _websocket_fanout   _edge_proxy      _kv_store
  (goroutine budget)  (p99 under GC)   (GC scan time)
      |                   |
      +---------+---------+
                |
                v
      design_a_kubernetes_controller
      (idempotence, reconcile loops)
```

`design_a_cross_platform_cli_tool` is deliberately off the main chain — it is the one study
with no server-side runtime pressure, and it can be read at any point.

---

## 5. Interview Prep Shortcuts

| "Design X" question | Best case study | What it gives you |
|---------------------|-----------------|-------------------|
| "Design a high-throughput event pipeline in Go" | [High-Throughput Ingest Service](design_a_high_throughput_ingest_service.md) | Allocation-per-event arithmetic, bounded queues, backpressure vs shed |
| "How would you hold a million WebSocket connections?" | [Realtime WebSocket Fan-out](design_a_realtime_websocket_fanout.md) | Goroutine cost budget, sharded hub, slow-consumer eviction |
| "Write a Kubernetes operator" | [Kubernetes Controller](design_a_kubernetes_controller.md) | Level-triggered reconcile, workqueue, finalizers, leader election |
| "Design a CLI our whole company will install" | [Cross-Platform CLI Tool](design_a_cross_platform_cli_tool.md) | Config precedence, TTY-aware output, release matrix, self-update |
| "Build a proxy / sidecar / API gateway in Go" | [gRPC Edge Proxy](design_a_grpc_edge_proxy.md) | Raw-codec passthrough, atomic config swap, zero-downtime restart |
| "Design a storage engine" | [Embedded KV Store](design_an_embedded_kv_store.md) | Off-heap value log, mmap vs read, WAL and group commit |
| "Why is our Go service getting OOMKilled?" | [gc_and_memory_budgets](cross_cutting/gc_and_memory_budgets.md) | RSS is not the heap; the `GOMEMLIMIT` pattern and the death spiral |
| "Prove this optimisation actually worked" | [benchmarking_go_correctly](cross_cutting/benchmarking_go_correctly.md) | `b.Loop`, `benchstat`, and the Go-specific noise floor |

---

## Maintenance

Adding a case study means updating **all five sections above in the same commit** — Quick
Start if it displaces an entry, the Full Learning Path group it belongs to, the
Cross-Cutting table if it cites a new shared primitive, the dependency map, and an
Interview Prep row. Also add it to the `<!-- study-paths -->` block in
[the section README](../README.md) with its tiers, unless it lives under `cross_cutting/`,
which is excluded.
