# Go Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/go/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §7 — check the Batch Plan before starting a new module.

---

## Why this section exists

Go is the implementation language of nearly every system the `devops/` and
`technologies/` sections already teach — Kubernetes, Docker, Terraform, Prometheus,
Envoy's control plane, Cilium's userspace, etcd, Consul, Vault. The repo taught how to
*operate* those systems and never how to *write* one. This section closes that gap from
the language side: the runtime, the scheduler, the memory model, and the standard
library's production-grade `net/http` server.

It is a peer of `java/` and `python/`, not a subset of `backend/`. Backend engineering
concepts (rate limiting, API design, event-driven patterns) stay in `backend/`; this
section is Go the language and Go the runtime.

---

## PRIMARY TARGET VERSION — Go 1.27

**Go 1.27** (released 2026-08-19; 1.27.1 on 2026-09-01, 1.27.2 on 2026-10-08) is the version every module
targets. Go 1.26 shipped 2026-02-10. Supported releases are 1.26 and 1.27 only — Go
supports a release until two newer majors exist.

Go ships every **February and August**, so this pin goes stale twice a year. Before
writing or auditing a module, check `go.dev/doc/devel/release` and the release notes for
the current version rather than writing from memory — the single worst defect this
section can ship is a plausible-sounding feature that does not exist.

**Version tags are sparse here by design.** Go's compatibility promise means most of what
you write is true across every supported release, so a Python-density tag sweep would be
noise. Tag `[1.N]` only what is genuinely version-gated:

| Tag | What it gates |
|-----|---------------|
| `[1.27]` | Generic methods; `encoding/json/v2` + `encoding/json/jsontext` as real packages; `goroutineleak` profile GA; size-specialized allocation (up to 30% faster for allocations under 80 bytes); time channels always unbuffered; `bytes.CutLast`/`strings.CutLast`; `testing/synctest.Sleep`; `database/sql.ConvertAssign`; HTTP/1 response-body auto-drain |
| `[1.26]` | Green Tea GC on by default (10–40% less GC overhead); `new(expr)`; self-referential generic types; `errors.AsType`; `log/slog.NewMultiHandler`; `os/signal.NotifyContext` cancels with a cause; `reflect` iterator methods; `ServeMux` trailing-slash redirect is now 307; `go fix` as the modernizer home; `go tool doc` deleted; cgo call overhead down ~30% |
| `[1.25]` | `testing/synctest`; execution-trace flight recorder; container-aware `GOMAXPROCS`; `WaitGroup.Go` |
| `[1.24]` | Swiss-table maps; `b.Loop`; `omitzero`; `go tool` for module tools; generic type aliases |
| `[1.23]` | Iterators / range-over-func; `unique`; `Timer`/`Ticker` GC changes |
| `[1.22]` | Per-iteration loop variables; `ServeMux` method + wildcard patterns |
| `[1.21]` | `log/slog`; PGO generally available; `min`/`max`/`clear` |
| `[1.19]` | `GOMEMLIMIT` |
| `[1.18]` | Generics; native fuzzing |

**REPLACE, DO NOT ANNOTATE.** Never write "X is deprecated, use Y" — write Y. The three
exceptions in root `CLAUDE.md` (a broken→fixed pair where the old form IS the lesson, an
answer whose subject is a migration, and deprecation-as-discipline) apply here too.

---

## Module List — 27 Modules (5 Phases)

| # | Module Directory | Phase | Key Concepts |
|---|-----------------|-------|-------------|
| 1 | `go_fundamentals_and_idioms/` | 1 — Language core | Packages, zero values, `:=` shadowing, `defer` LIFO and argument evaluation at defer time, value semantics, named returns, `iota` |
| 2 | `data_structures_and_memory_layout/` | 1 | Slice header (ptr/len/cap), `append` reallocation and aliasing, `copy`, Swiss-table maps `[1.24]`, map non-addressability, string vs `[]byte` vs `rune`, struct alignment and padding |
| 3 | `interfaces_methods_and_embedding/` | 1 | Method sets and pointer vs value receivers, the `(type, value)` pair and the typed-nil trap, `itab`, embedding vs inheritance, promotion, type switches, `io.Reader`/`io.Writer` composition |
| 4 | `errors_and_panics/` | 1 | Errors as values, sentinel vs typed errors, `%w` wrapping, `errors.Is`/`As`/`Join`/`AsType` `[1.26]`, panic/recover boundary discipline |
| 5 | `generics_and_type_parameters/` | 1 | Type parameters, constraints and `~` approximation, inference limits, `comparable`, `slices`/`maps`, generic methods `[1.27]`, and when NOT to reach for generics |
| 6 | `packages_modules_and_tooling/` | 1 | `go.mod`, minimal version selection, `go.sum` and the checksum DB, workspaces, vendoring, `toolchain`/`GOTOOLCHAIN`, build tags, `go generate`, `go fix` modernizers `[1.26]`, `golangci-lint` |
| 7 | `goroutines_and_the_scheduler/` | 2 — Concurrency | GMP model, run queues and work stealing, `sysmon`, asynchronous preemption `[1.14]`, 2 KB stacks that grow by copying, container-aware `GOMAXPROCS` `[1.25]` |
| 8 | `channels_and_select/` | 2 | `hchan` internals (ring buffer, `sendq`/`recvq`), buffered vs unbuffered handoff, close semantics and the who-closes rule, `select` with `default`, nil-channel disabling, directional types |
| 9 | `sync_primitives_and_memory_model/` | 2 | `Mutex` normal vs starvation mode (1 ms threshold), `RWMutex` writer starvation, `WaitGroup.Go` `[1.25]`, `Once`, typed atomics, `sync.Map`'s two narrow cases, `sync.Pool` and GC, `-race` |
| 10 | `context_and_cancellation/` | 2 | The context tree, `WithTimeout`/`WithCancelCause`/`AfterFunc`, deadline propagation across RPC boundaries, ctx as the first parameter, the `context.Value` abuse rule |
| 11 | `concurrency_patterns_and_pitfalls/` | 2 | Pipelines, bounded worker pools, `errgroup` + `SetLimit`, `semaphore`, goroutine leaks and the `goroutineleak` profile `[1.27]`, loop-variable semantics `[1.22]`, graceful shutdown |
| 12 | `the_runtime_and_garbage_collector/` | 3 — Runtime and performance | Tricolor concurrent mark-sweep, hybrid write barrier, the pacer, sub-millisecond STW, `GOGC` vs `GOMEMLIMIT` `[1.19]` and the soft-limit arithmetic that prevents `OOMKilled`, Green Tea GC `[1.26]` |
| 13 | `escape_analysis_and_performance/` | 3 | `-gcflags=-m`, stack vs heap, interface boxing, `[]byte`↔`string` conversions, PGO `[1.21]`, inlining budget, bounds-check elimination, size-specialized allocation `[1.27]` |
| 14 | `profiling_and_tracing/` | 3 | `net/http/pprof`, CPU/heap/block/mutex/goroutine profiles, pprof labels, `benchstat`, the flame-graph-by-default web UI `[1.26]`, continuous profiling |
| 15 | `testing_benchmarking_and_fuzzing/` | 3 | Table-driven tests and subtests, `t.Cleanup`, `httptest`, `testing/synctest` `[1.25]`, native fuzzing `[1.18]`, `b.Loop` `[1.24]`, golden files, stdlib vs testify |
| 16 | `net_http_server/` | 4 — Stdlib and services | `ServeMux` method and wildcard patterns `[1.22]`, handlers and middleware as closures, all four `Server` timeouts, `Shutdown`, `ResponseController`, HTTP/2, request-context cancellation |
| 17 | `http_clients_and_resilience/` | 4 | `Transport` pooling and the `MaxIdleConnsPerHost = 2` default, the unclosed-`resp.Body` fd leak, per-attempt vs total timeouts, retries and idempotency, `httptrace` |
| 18 | `json_and_serialization/` | 4 | `encoding/json` reflection cost, struct tags, `omitempty` vs `omitzero` `[1.24]`, streaming `Decoder`, custom marshalers, `encoding/json/v2` and `jsontext` `[1.27]`, when to leave for protobuf |
| 19 | `database_sql_and_data_access/` | 4 | `database/sql` is a pool not a driver: `SetMaxOpenConns`/`SetMaxIdleConns`/`SetConnMaxLifetime`, the `rows.Close()` leak, context variants, `sql.Null*`, pgbouncer interaction, `pgx` vs `sqlc` vs GORM |
| 20 | `grpc_and_protobuf_in_go/` | 4 | `protoc-gen-go`, the four RPC modes, unary and stream interceptors, `status`/`codes`, deadlines, `connect-go`, buf |
| 21 | `observability_and_opentelemetry/` | 4 | `log/slog` `[1.21]` handlers and context attrs, `NewMultiHandler` `[1.26]`, OTel Go SDK wiring, propagation through `context`, `runtime/metrics`, cardinality control |
| 22 | `time_io_and_stdlib_essentials/` | 5 — Interop, judgment, production | `time` monotonic vs wall clock, `Timer`/`Ticker` leaks and `[1.23]` GC changes, unbuffered time channels `[1.27]`, the `io` interface algebra, `bufio`, `signal.NotifyContext`, `embed` |
| 23 | `reflection_and_unsafe/` | 5 | `reflect.Type`/`Value`, settability, struct-tag parsing, the three laws of reflect, iterator methods `[1.26]`, `unsafe.Pointer`'s six valid patterns, `unsafe.Slice`/`String`, `go:linkname` restrictions |
| 24 | `cgo_and_ffi/` | 5 | The cgo calling convention and its per-call cost (down ~30% in `[1.26]`), pointer-passing rules, why cgo forfeits easy cross-compilation and static linking, `CGO_ENABLED=0`, `purego`, WASM/`wasip1` |
| 25 | `idiomatic_api_and_package_design/` | 5 | Accept interfaces and return structs, consumer-defined interfaces, functional options, `internal/`, package naming, DI without a framework, error contracts across module boundaries |
| 26 | `build_deployment_and_supply_chain/` | 5 | Static `CGO_ENABLED=0` builds, distroless/scratch multi-stage images (~10–20 MB), `-ldflags "-s -w"` and `-trimpath`, the `GOOS`/`GOARCH` matrix, reproducible builds and `buildinfo`, `govulncheck`, `GOPROXY`, SBOM |
| 27 | `go_vs_java_and_python/` | 5 | CSP and goroutines vs JVM virtual threads vs `asyncio`; concurrent low-pause GC vs G1/ZGC vs refcounting; error values vs exceptions; gcshape stenciling vs erasure vs duck typing; startup time and RSS; which to pick for what |

---

## Sub-Files (3 total — 14-section template, 15+ Q&As each)

Each exists because it needs a **different study tier from its parent page**, which is
the only thing a sub-file expresses that a longer module page cannot.

| File | Parent Module | Why separate |
|------|--------------|--------------|
| `generics_and_type_parameters/generics_implementation_and_gcshape.md` | 5 | GC-shape stenciling and runtime dictionaries, why `[T any]` can be slower than an interface. Using generics is senior craft; knowing what the compiler emits is a principal-only cost argument |
| `sync_primitives_and_memory_model/the_go_memory_model.md` | 9 | The formal happens-before specification, atomic ordering guarantees, why `-race` proves nothing about untested interleavings. Principal-only; the API surface stays on the parent page |
| `profiling_and_tracing/execution_tracer_and_flight_recorder.md` | 14 | `runtime/trace` answers a different question than pprof — "why did this goroutine not run for 40 ms" rather than "where is CPU spent" — plus the flight recorder `[1.25]`. The 3am tool, senior-only |

**Never name a file here `*_deep_dive.md` or `*_internals.md`.** `DEEP_NAME_RE` in
`game/build_tech.py` makes any such page a FATAL error under `--strict` unless it is
hand-listed in `PRODUCT_DEEP_DIVES`/`CONCEPT_DEEP_DIVES`. That is why the memory-model
page is `the_go_memory_model.md` and not `runtime_internals.md`.

---

## Case Studies — 6 + 2 cross-cutting

All six are **Go-runtime** studies, not architecture studies — they stop where `hld/`
starts. 11-section principal template, 900–1100 lines each.

| File | Concern |
|------|---------|
| `case_studies/design_a_high_throughput_ingest_service.md` | 500k events/s in one binary: zero-allocation hot path, bounded worker pool, sharded batching, `GOMEMLIMIT` budget, backpressure vs shed |
| `case_studies/design_a_kubernetes_controller.md` | `controller-runtime` reconcile loop, informers, workqueue and rate limiter, CRDs, leader election, idempotent reconcile, finalizers, status conditions |
| `case_studies/design_a_realtime_websocket_fanout.md` | 1M concurrent connections in one process: goroutine-per-connection cost arithmetic, sharded hub, write-buffer pooling, slow-consumer eviction |
| `case_studies/design_a_cross_platform_cli_tool.md` | Single-binary CLI: command tree, config precedence, streaming output and TTY detection, `signal.NotifyContext`, release matrix, testable command boundaries |
| `case_studies/design_a_grpc_edge_proxy.md` | An L7 Go proxy: dynamic config, per-RPC interceptors, hot reload, `SO_REUSEPORT` zero-downtime restart, p99 budget under GC |
| `case_studies/design_an_embedded_kv_store.md` | LSM/WAL in Go: mmap vs `read` and how each interacts with the runtime, why millions of live pointers wreck GC scan time, off-heap value logs, crash recovery |
| `case_studies/cross_cutting/benchmarking_go_correctly.md` | `b.Loop`, `benchstat` p-values, dead-code elimination sinking a benchmark, PGO measurement, stating the machine |
| `case_studies/cross_cutting/gc_and_memory_budgets.md` | `GOGC` × `GOMEMLIMIT` arithmetic against a container limit, live-heap estimation, when to set `GOGC=off` with a hard `GOMEMLIMIT` |

**Case-study Q&As never enter the quiz bank** — `extract.py` skips every `case_studies/`
path outright. That is the rule working, not a bug. Do not move a case-study Q&A under an
interview heading to "rescue" it.

`case_studies/cross_cutting/` is in `CS_EXCLUDE_DIRS`. Listing a file under it in the
`<!-- study-paths -->` block is an explicit `--strict` error. Neither the two
cross-cutting files nor `case_studies.md` itself appear in that block.

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 27 modules = the 5-Phase Learning Path) plus
two curated tiers: **Senior** (the craft — can you write it, debug it at 3am, say why it
broke) and **Principal** (the judgment — which runtime model at what cost, what failure
modes, what you tell a team *not* to do). They are **different cuts, not nested depths**:
6 of the 12 principal modules are material senior never sees.

Membership is declared ONCE per section, in the `<!-- study-paths -->` block in this
section's `README.md`, naming every module, every file it owns, and the tiers each file
takes. Order is never declared there — it comes from `STUDY_ORDER.go` in `game/app.js`,
so a tier is an ordered subset by construction. **There is no path array in `app.js` to
edit**: `extract.py` walks the block and emits the gitignored `questions/paths.json`.

---

## Q&A Minimums

- **15+ Q&As** per module page and per sub-file — a hard floor, not a target.
- **18+** for the five highest-frequency interview modules: `goroutines_and_the_scheduler`,
  `channels_and_select`, `sync_primitives_and_memory_model`,
  `interfaces_methods_and_embedding`, `the_runtime_and_garbage_collector`.
- Order by interview frequency: gotchas and traps first, then internals, then edge cases.
- **Distractor quality is a section-level property.** Distractors are drawn from other
  answers' first sentences in the same module first. Go Q&As cluster hard around
  "goroutine", "channel" and "interface" — near-identical opening sentences across a
  module produce four indistinguishable options. Keep first sentences lexically distinct.

---

## Code Style in This Section

- ` ```go ` fences use **4 spaces, not gofmt tabs** — the house style already set by the
  53 existing Go fences elsewhere in this repo. The no-tabs rule in root `CLAUDE.md`
  governs prose and diagrams; a Go fence is not one of the tab-requiring data formats.
- Every non-trivial example compiles as written or is clearly marked a fragment.
- At least one **`// BROKEN` → `// FIX`** pair in §10 and one in §14 of every module.
- Concrete numbers everywhere: 2 KB initial goroutine stack; `GOGC` default 100;
  `MaxIdleConnsPerHost` default 2; sub-millisecond GC STW; ~10–20 MB distroless image;
  1 ms mutex starvation threshold. Never "a few", "some", "significant".

---

## Adding a New Go Module

1. Create `<module_name>/<module_name>.md` — the 14-section template. The page is named
   for its folder; a `README.md` here is invisible to `extract.py`
2. Target Go 1.27; verify anything version-gated against `go.dev` before writing it
3. Meet the Q&A minimum above, every answer opening with its `**Short:**` line
4. Add the module dir to `STUDY_ORDER.go` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
5. Add the module and EVERY file it owns to the `<!-- study-paths -->` block in this
   section's `README.md`, tagging each file with its tiers (`-` for Full path only; the
   module page must carry every tier the module is in). **Do NOT put a block in the module
   page — content files hold only content.** Then run
   `python3 game/extract.py --write-paths` to regenerate the README's tier tables
6. Flip the module's Status from `pending` → `done` in `README.md` §7
7. Update root `README.md` phase table under the Go section
8. Update root `CLAUDE.md` Go section row

---

## Cross-Reference Map

| From a Go module | Link to |
|------------------|---------|
| `goroutines_and_the_scheduler` | `../java/structured_concurrency_and_loom/structured_concurrency_and_loom.md`, `../python/asyncio_and_event_loop/asyncio_and_event_loop.md`, `../cs_fundamentals/processes_threads_and_context_switching/processes_threads_and_context_switching.md` |
| `sync_primitives_and_memory_model` | `../java/java_memory_model/java_memory_model.md`, `../cs_fundamentals/memory_models_and_concurrency_theory/memory_models_and_concurrency_theory.md` |
| `the_runtime_and_garbage_collector` | `../java/jvm_internals/jvm_internals.md`, `../python/cpython_memory_model/cpython_memory_model.md` |
| `escape_analysis_and_performance` | `../cs_fundamentals/computer_architecture_and_memory_hierarchy/computer_architecture_and_memory_hierarchy.md`, `../backend/performance_profiling/performance_profiling.md` |
| `profiling_and_tracing` | `../hld/observability/continuous_profiling.md`, `../backend/off_cpu_and_ebpf_analysis/off_cpu_and_ebpf_analysis.md` |
| `net_http_server` | `../backend/http_protocols/http_protocols.md`, `../backend/rest_api_design/rest_api_design.md` |
| `http_clients_and_resilience` | `../backend/fault_tolerance_patterns/fault_tolerance_patterns.md`, `../backend/connection_pooling_deep_dive/connection_pooling_deep_dive.md` |
| `database_sql_and_data_access` | `../database/connection_pool_management/connection_pool_management.md`, `../backend/database_internals_and_indexing/database_internals_and_indexing.md` |
| `grpc_and_protobuf_in_go` | `../backend/grpc_and_protobuf/grpc_and_protobuf.md`, `../java/grpc_protobuf/grpc_protobuf.md` |
| `observability_and_opentelemetry` | `../devops/observability_tracing_and_otel/observability_tracing_and_otel.md`, `../backend/observability_and_monitoring/observability_and_monitoring.md` |
| `build_deployment_and_supply_chain` | `../devops/containers_and_docker/containers_and_docker.md`, `../devops/devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md` |
| `idiomatic_api_and_package_design` | `../lld/design_principles/design_principles.md`, `../lld/solid_principles/solid_principles.md` |
| `go_vs_java_and_python` | `../java/concurrency/concurrency.md`, `../python/the_gil_and_free_threading/the_gil_and_free_threading.md` |
| `case_studies/design_a_kubernetes_controller.md` | `../devops/kubernetes_operators_and_crds/kubernetes_operators_and_crds.md` |

---

## Section §11 stays `## 11. Technologies & Tools`

Unlike `soft_skills/`, this section **wants** to feed the technology index. Go's tooling
— delve, pprof, golangci-lint, buf, goreleaser, air, govulncheck — is genuine product
content and belongs in `tech_index.json`. Do not retitle §11 here.

---

## Diagram Traps Specific to Go

A Mermaid fence that does not parse **FAILS THE BUILD** (`scripts/build_diagrams.mjs`
exits 1). Validate anything you touch:
`node scripts/check_render.mjs <absolute-file.md>` — exit 0 means every fence renders.

| Trap | Why Go content hits it constantly |
|------|-----------------------------------|
| `;` in a `sequenceDiagram` Note or message | `for i := 0; i < n; i++` and `if err := f(); err != nil` are exactly the strings a writer pastes into a Note. `;` is a statement separator — the fence breaks. Paraphrase, or put the code in a ` ```go ` fence below the diagram |
| `call` as a node id **or as a `classDef` class name** | Go diagrams are wall-to-wall call/defer/panic flows. `call` is reserved (`click X call fn()`), and it fails in BOTH positions — `classDef call fill:...` does not parse either, which is easy to miss because the node ids all look fine. Use `doCall`, `rpcCall`, `invoke`, `entry`. Also avoid `end`, `class`, `style`, `click`, `graph`, `subgraph`, `state`, `direction`, and especially `default` — every `select` diagram wants that one |
| `<-` in an unquoted label | `ch <- v` and `<-ch` collide with edge syntax. Quote any label containing `<-`, `->`, `<` or `>`, or write it in prose ("send on ch") |
| `\"` in a label | Struct tags (`json:"id"`) are the temptation. Use `#quot;`, or keep tags out of diagrams |
| Padding spaces after an edge label's closing `\|` | `a -->\|"send"\|   b` fails. The habit of aligning a column of edges — natural for anyone who writes good ASCII — is what broke the RSocket diagram. Mermaid is not ASCII art |

Diagrams are **appeal-first**: Mermaid is the default (`flowchart LR` for directed flows,
`sequenceDiagram` for actor chains, `stateDiagram-v2` for lifecycles, `xychart-beta` for
magnitudes). Colour every node with the One-Dark `classDef` palette semantically by role,
keep labels short (wrap with `<br/>`), use rounded shapes. Run `/mermaid-diagrams` first.
ASCII remains only for constraint grids, alignment-critical layout maps, and vector
geometry — the slice header (ptr/len/cap) and struct padding layouts are the two places
in this section where ASCII genuinely beats Mermaid.

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module, EVERY
file that module owns (the module page AND every deep-dive sub-file), and EVERY case
study, each tagged with the tiers it belongs to — `-` means Full path only. Reading that
one block tells you every file in the section and which paths it is on.

**A content file carries NO structural metadata.** A module page or a deep-dive sub-file
holds the content of its topic and nothing else — no `<!-- study-paths -->` block, no tier
declaration, no path membership.

**Adding a file? Add its line to the section README's block in the same commit.** A file on
disk that is missing from the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red.

Order is never declared in the block: it comes from `STUDY_ORDER` in `game/app.js`. The
tier TABLES further down the README are GENERATED from the block by
`python3 game/extract.py --write-paths` — never hand-edit them.
