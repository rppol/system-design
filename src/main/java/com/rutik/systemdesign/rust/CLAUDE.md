# Rust Section - CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/rust/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest and status tracker: `README.md` §7 "Build Manifest". Check it before you start a file.

---

## Why this section exists

106 content files in this repo mention Rust, and no module owns it. The repo teaches
systems that are written in Rust (Pingora, linkerd2-proxy, ztunnel, Qdrant, uv, ruff) and
the Python side of PyO3. It never teaches the language.

`rust/` is a peer of `go/`, `java/` and `python/`. It owns:

- Rust the language: ownership, lifetimes, traits, macros, unsafe.
- The runtime model: `Send`/`Sync`, atomics, futures, Tokio.
- The production crates a Rust service is built from: axum, tower, hyper, serde, sqlx,
  diesel, tonic, tracing.
- The Rust toolchain and supply chain: cargo, editions, MSRV, cargo-deny, cargo-vet.

Theory stays in `cs_fundamentals/`. The Python side of PyO3 stays in `python/`. Protocols
and architecture stay in `backend/` and `hld/`. Placement is link-only: no existing module
moves into `rust/`, so no existing question id changes.

---

## Build process for this section

- Work happens in the main checkout on branch `feat/rust-section`. The owner pushes one
  commit to `main` at the end. Builders never run git commands that change state.
- Everything lands in one commit: the files, the README `<!-- study-paths -->` block,
  `STUDY_ORDER.rust` and the labels in `game/app.js`. `extract.py --strict` is red between
  phases on the unpushed branch. That is expected. CI runs only on `main`.
- One integrator adds the README block, `STUDY_ORDER`, the inbound links and the tech-bank
  records after all content exists. Parallel builders edit only their own files.
- Prose: apply the `humanizer`, `writing-voice`, `use-conversational-language` and
  `asd-ste100` skills. Plain, direct technical English. No flourish. Then run Vale once
  per file and fix every House hit:
  `vale --config ~/.claude/vale/.vale.ini --output=line <file> | grep ':House\.'`
- `npm ci` is done in the repo root. `node_modules/` exists.

---

## Non-overlap boundary

If a subtopic appears in the right-hand column, link to that page. Do not teach it again.
Where a real overlap exists, the module's §1 ends with one boundary sentence and the link.
A module with no overlap in this table does not need a "Not here" paragraph.

| Topic | Owner page (link, do not repeat) | What `rust/` owns |
|---|---|---|
| Memory-model theory: happens-before, acquire/release, SC-DRF, litmus tests, `loom` intro | `cs_fundamentals/memory_models_and_concurrency_theory/memory_models_and_concurrency_theory.md` | One paragraph that maps `Ordering` onto that model. The Rust atomics API |
| Threads, context switches, scheduling | `cs_fundamentals/processes_threads_and_context_switching/processes_threads_and_context_switching.md` | Link only |
| epoll, kqueue, io_uring, readiness vs completion | `cs_fundamentals/processes_threads_and_context_switching/async_io_and_event_loops.md` | One paragraph: io_uring needs owned buffers in Rust because a dropped future cancels the operation |
| Virtual memory, allocators, page faults | `cs_fundamentals/memory_management_and_virtual_memory/memory_management_and_virtual_memory.md` | Link only |
| Cache lines and false sharing | `cs_fundamentals/computer_architecture_and_memory_hierarchy/computer_architecture_and_memory_hierarchy.md` | One line on `CachePadded` and `#[repr(align(64))]` |
| Deadlock theory and lock ordering | `cs_fundamentals/deadlocks_and_synchronization/deadlocks_and_synchronization.md` | Safe Rust prevents data races, not deadlocks |
| Compiler pipeline, LLVM, monomorphization vs erasure in general | `cs_fundamentals/how_code_runs_compilers_and_interpreters/how_code_runs_compilers_and_interpreters.md` | rustc specifics: codegen units, compile-time cost |
| ABIs, linking, `extern "C"`, no stable Rust ABI | `cs_fundamentals/how_code_runs_compilers_and_interpreters/abis_linking_and_wasm.md` | The Rust FFI API surface |
| Wasm format, WASI, component model, runtimes, the `wasm32-wasi` to `wasm32-wasip1` rename, the native-plugin to Wasm case study | `cs_fundamentals/how_code_runs_compilers_and_interpreters/wasm_and_portable_bytecode.md` | The Rust toolchain side: targets, wasm-bindgen, cargo component, size tuning, embedding wasmtime |
| `driftsort`/`ipnsort`, the 1.81 sort panic on a comparator that is not a total order | `cs_fundamentals/sorting_and_searching/sorting_and_searching.md` | Link only |
| PyO3 extension end to end: Rust code, `py.detach`, free threading, maturin, wheels, when native is the wrong answer, the §14 matcher case | `python/c_extensions_and_native_interop/c_extensions_and_native_interop.md` (§6.1 and §14) | One paragraph and a link. `ffi_and_language_interop` owns only what that page lacks: `#[pyclass]` `Send`/`Sync` rules, error mapping, napi-rs, jni, UniFFI, cxx |
| pydantic-core as the PyO3 example | `fastapi/pydantic_v2_deep_dive/pydantic_core_and_performance.md` | Link only |
| Rust-built Python tools (uv, ruff, pyrefly, ty) | `python/packaging_and_project_tooling/`, `python/the_type_system_and_typing/` | Link only, in §7 Real-World Examples |
| Rust-built JS toolchain (Rolldown, Oxc, swc, Turbopack, Rspack, Biome, oxlint, Lightning CSS) | `frontend_platform/modules_bundling_and_the_build_pipeline/`, `frontend_platform/linting_formatting_and_code_quality/` | Link only |
| Go GC tails and the Discord Go-to-Rust story | `go/the_runtime_and_garbage_collector/`, `go/case_studies/design_a_realtime_websocket_fanout.md` | One sentence and a link. Do not retell Discord |
| Go vs Java vs Python | `go/go_vs_java_and_python/go_vs_java_and_python.md` | The Rust axis only |
| GC-shape stenciling vs monomorphization | `go/generics_and_type_parameters/generics_implementation_and_gcshape.md` | Code bloat and `dyn` cost |
| cgo cost; Java FFM | `go/cgo_and_ffi/`, `java/foreign_function_and_memory_api/` | Link only |
| JMM; virtual threads and structured concurrency; asyncio | `java/java_memory_model/`, `java/structured_concurrency_and_loom/`, `python/asyncio_and_event_loop/` (and `structured_concurrency.md`) | Link as the contrast |
| Benchmark statistics, the A/A noise floor, confidence intervals, `perf.rust-lang.org` | `backend/benchmarking_methodology/`, `backend/performance_regression_detection/` | `black_box`, criterion, divan, instruction-count benches |
| Profiling method, flame graphs, off-CPU, eBPF | `backend/performance_profiling/`, `backend/off_cpu_and_ebpf_analysis/`, `backend/performance_engineering_method/` | Debuginfo, frame pointers, symbol mangling, samply |
| HTTP/1, HTTP/2, HTTP/3, REST, retries and breakers, QUIC | `backend/http_protocols/`, `backend/rest_api_design/`, `backend/fault_tolerance_patterns/`, `backend/udp_and_quic/` | The tower layers that implement them |
| gRPC and protobuf concepts, deadlines, status codes | `backend/grpc_and_protobuf/` | tonic and prost |
| Pool theory and sizing (PgDog and PgCat are listed there as Rust) | `database/connection_pool_management/`, `backend/connection_pooling_deep_dive/` | The sqlx pool |
| OTel concepts, OTLP, collector, sampling, per-language status | `devops/observability_tracing_and_otel/opentelemetry_deep_dive.md` | The `tracing` crate and its OTel bridge |
| SLSA, Sigstore, SBOM formats, SCA; containers and distroless | `devops/devsecops_and_supply_chain_security/`, `devops/containers_and_docker/` | cargo-deny, cargo-audit, cargo-vet, cargo-auditable, cargo-chef, musl |
| Storage-engine theory; redb, sled, Turso | `database/storage_engines_internals/`, `database/embedded_and_edge_databases/` | The Rust-specific parts, in the case study |
| linkerd2-proxy, ztunnel, Envoy Rust dynamic modules, Proxy-Wasm | `backend/service_mesh_and_service_discovery/ambient_mesh_and_workload_identity.md`, `technologies/envoy_proxy/`, `go/case_studies/design_a_grpc_edge_proxy.md` | Link as real-world examples |
| Deterministic simulation (`madsim`, `turmoil`) | `hld/consensus_algorithms/` | One paragraph in testing, and a link |
| wgpu, GPU from Rust | `cuda/gpu_portability_hip_sycl_and_beyond/` | Out of scope. One line in the comparison module |
| Rust-built systems elsewhere: Qdrant, InfluxDB 3/DataFusion, Vector, HF tokenizers, tiktoken, SGLang's Rust frontend | `database/`, `devops/` and `llm/` modules | Link only, in the comparison module and case studies |

---

## Primary target version: Rust 1.99.1, edition 2024

- **Rust 1.99.1** (2026-10-15) on **edition 2024**. Rust 1.99.0 shipped 2026-10-01.
- **Rust 1.100.0 is due 2026-11-12** on the six-week train. On 2026-11-06 the release index
  ended at 1.99.1. On or after 2026-11-12, fetch `https://blog.rust-lang.org/releases/`
  again. If 1.100 is out before a file ships, add its row to the pin table and decide the
  target once for the whole section. Do not mix 1.99 and 1.100 targets across modules.
- Tags: `[1.NN]` marks a release-gated feature and `[ed2024]` marks an edition-gated one.
  Use them only on a feature that is really gated. Most of this section is true on every
  supported release.
- **REPLACE, DO NOT ANNOTATE.** Never write "X is deprecated, use Y". Write Y. The three
  exceptions in root `CLAUDE.md` apply: a broken-then-fixed pair where the old form is the
  lesson, an answer whose subject is a migration, and deprecation as the subject.

---

## Version pins (fetched 2026-11-06)

Every module's version-anchor blockquote under the H1 uses only rows from these tables. A
version, date or status fact that is not here is fetched from its primary source before
it is written. If the fetch fails, cut the fact. No "verify" marker ships.

### Rust releases

Source for every row: the release post at `https://blog.rust-lang.org/<date path>/Rust-<version>/`
(index: `https://blog.rust-lang.org/releases/`) and `RELEASES.md` on the `stable` branch of
`rust-lang/rust`.

| Version | Date | Features you may cite |
|---|---|---|
| 1.63 | 2022-08-11 | `thread::scope` (scoped threads) |
| 1.65 | 2022-11-03 | `let else`; generic associated types (GATs) |
| 1.66 | 2022-12-15 | `hint::black_box` |
| 1.67 | 2023-01-26 | `std::sync::mpsc` reimplemented on crossbeam-channel |
| 1.68 | 2023-03-09 | `pin!` |
| 1.71 | 2023-07-13 | `extern "C-unwind"` and the other `-unwind` ABIs |
| 1.75 | 2023-12-28 | `async fn` and return-position `impl Trait` in traits (AFIT, RPITIT) |
| 1.77 | 2024-03-21 | C-string literals `c"..."` |
| 1.79 | 2024-06-13 | associated type bounds |
| 1.80 | 2024-07-25 | `LazyCell` and `LazyLock`; checked `cfg` names; exclusive ranges in patterns |
| 1.81 | 2024-09-05 | `core::error::Error`; new sort implementations; `#[expect(lint)]`; an uncaught panic in an `extern "C"` function aborts |
| 1.82 | 2024-10-17 | precise capturing `use<..>`; `&raw const`/`&raw mut`; `unsafe extern` blocks; unsafe attributes (`#[unsafe(no_mangle)]`); `cargo info` |
| 1.84 | 2025-01-09 | MSRV-aware dependency resolution in Cargo; strict provenance APIs; migration to the new trait solver begins |
| 1.85 | 2025-02-20 | edition 2024; async closures and `AsyncFn*` |
| 1.86 | 2025-04-03 | trait upcasting; `get_disjoint_mut` on `HashMap` and slices; safe `#[target_feature]` functions |
| 1.87 | 2025-05-15 | `use<..>` in `impl Trait` in trait definitions; anonymous pipes; safe architecture intrinsics |
| 1.88 | 2025-06-26 | let chains (edition 2024 only); naked functions; boolean `cfg` literals; Cargo cache cleaning |
| 1.89 | 2025-08-07 | `_` as an inferred const generic argument; `mismatched_lifetime_syntaxes` lint; cross-compiled doctests |
| 1.90 | 2025-09-18 | LLD is the default linker on `x86_64-unknown-linux-gnu`; `cargo publish --workspace`; `x86_64-apple-darwin` demoted to tier 2 with host tools |
| 1.91 | 2025-10-30 | `aarch64-pc-windows-msvc` is tier 1; lint against dangling raw pointers from locals |
| 1.92 | 2025-12-11 | never-type lints deny by default; `RwLockWriteGuard::downgrade`; `Box`/`Rc`/`Arc::new_zeroed`; unwind tables emitted on Linux even with `panic=abort`; `#[macro_export]` input validated |
| 1.93 | 2026-01-22 | bundled musl 1.2.5; the global allocator may use thread-local storage; `cfg` on individual `asm!` lines; `Vec`/`String::into_raw_parts`; `fmt::from_fn` |
| 1.94 | 2026-03-05 | `<[T]>::array_windows`; Cargo config `include`; TOML 1.1 in Cargo; `LazyLock::get`/`force_mut`; `Peekable::next_if_map` |
| 1.95 | 2026-04-16 | `cfg_select!`; `if let` guards in match arms; atomic `update`/`try_update`; `Vec::push_mut`/`insert_mut`; `hint::cold_path`; `core::range` module; custom JSON target specs removed from stable |
| 1.96 | 2026-05-28 | `core::range` types; `assert_matches!`/`debug_assert_matches!`; Wasm targets stop passing `--allow-undefined`; Cargo CVE-2026-5223 and CVE-2026-5222 fixed (third-party registries only, crates.io users not affected) |
| 1.97 | 2026-07-09 | v0 symbol mangling by default; `CARGO_BUILD_WARNINGS=deny`; `linker_messages` lint; `bit_width`, `isolate_highest_one`/`isolate_lowest_one` |
| 1.98 | 2026-08-20 | `algebraic_*` float methods; `<int>::format_into` and `fmt::NumBuffer`; `str::substr_range`; `String::from_utf16le`; `from_mut`/`get_mut_slice`/`from_mut_slice` on the concrete atomic types; a fix for `ManuallyDrop` with `Box` |
| 1.99 | 2026-10-01 | C-variadic function definitions and `core::ffi::VaList`; `Layout::for_value_raw`, `size_of_val_raw`; `Vec::into_parts`/`from_parts`; `Box::into_non_null`/`from_non_null`; `fs::set_times`; guidance against round-trip unleaking after `Box::leak` |
| 1.99.1 | 2026-10-15 | point release: `wcslen` fallback, FFI `bool` return zero-extension on x86_64, `rust-lld` rpath on macOS |

### Edition 2024 change list

Source: `https://doc.rust-lang.org/edition-guide/rust-2024/index.html`. These are the only
`[ed2024]` items. Stabilized in 1.85.

- Language: RPIT lifetime capture rules; `if let` temporary scope; let chains in `if` and
  `while`; tail-expression temporary scope; match ergonomics reservations; `unsafe extern`
  blocks required; unsafe attributes required; `unsafe_op_in_unsafe_fn` warns; references
  to `static mut` denied; never-type fallback change; macro fragment specifiers (`expr`
  matches `const` and `_`, `expr_2021` keeps the old behavior); missing fragment specifiers
  are an error; `gen` is a reserved keyword; reserved syntax (`#"..."#`, guarded strings).
- Standard library: `Future` and `IntoFuture` in the prelude; `IntoIterator` for `Box<[T]>`;
  `std::env::set_var`, `std::env::remove_var` and `CommandExt::before_exec` are `unsafe`.
- Cargo: Rust-version-aware resolver (resolver 3) is the default; table and key name
  consistency; unused inherited `default-features` rejected.
- Rustdoc: combined doctests; nested `include!` change.
- Rustfmt: style edition; formatting fixes; raw identifier sorting; version sorting.

### Crates

Source for every row: `https://crates.io/api/v1/crates/<name>`, fetched 2026-11-06. Version
is the highest stable non-yanked version. MSRV is the published `rust-version`; an empty
cell means the crate publishes none, so do not state one.

| Crate | Version | Published | MSRV |
|---|---|---|---|
| tokio | 1.54.1 | 2026-10-21 | 1.85 |
| tokio-util | 0.7.20 | 2026-10-09 | 1.85 |
| hyper | 1.12.0 | 2026-10-06 | 1.63 |
| hyper-util | 0.1.21 | 2026-09-24 | 1.85 |
| axum | 0.8.9 | 2026-04-14 | 1.80 |
| tower | 0.5.3 | 2026-01-12 | 1.64 |
| tower-http | 0.7.1 | 2026-08-31 | 1.65 |
| actix-web | 4.18.2 | 2026-11-06 | 1.89 |
| reqwest | 0.13.5 | 2026-09-08 | 1.85 |
| rustls | 0.23.46 | 2026-10-14 | 1.71 |
| aws-lc-rs | 1.19.0 | 2026-10-28 | 1.71 |
| ring | 0.17.14 | 2025-03-11 | 1.66 |
| tonic, tonic-build, tonic-prost-build, tonic-prost, tonic-health, tonic-reflection, tonic-web, tonic-types | 0.14.9 | 2026-10-29 | 1.88 |
| prost, prost-build | 0.14.4 | 2026-06-07 | 1.85 |
| serde | 1.0.229 | 2026-07-18 | 1.56 |
| serde_json | 1.0.154 | 2026-10-11 | 1.71 |
| serde_with | 3.24.0 | 2026-09-26 | 1.88 |
| simd-json | 0.19.0 | 2026-10-27 | 1.88 |
| sonic-rs | 0.5.10 | 2026-09-11 | |
| postcard | 1.1.3 | 2025-07-24 | |
| rmp-serde | 1.3.1 | 2025-12-23 | 1.85 |
| bincode | 3.0.0 | 2025-12-16 | see status facts |
| sqlx, sqlx-cli | 0.9.0 | 2026-05-21 | 1.94 |
| diesel | 2.3.14 | 2026-10-07 | 1.86 |
| diesel-async | 0.9.2 | 2026-06-19 | 1.84 |
| sea-orm | 2.0.4 | 2026-09-27 | 1.94 |
| tokio-postgres | 0.7.18 | 2026-06-12 | 1.85 |
| deadpool-postgres | 0.14.2 | 2026-08-26 | 1.85 |
| tracing | 0.1.44 | 2025-12-18 | 1.65 |
| tracing-subscriber | 0.3.23 | 2026-03-13 | 1.65 |
| tracing-appender | 0.2.5 | 2026-04-17 | 1.63 |
| tracing-opentelemetry | 0.34.0 | 2026-09-23 | 1.75 |
| opentelemetry, opentelemetry_sdk, opentelemetry-otlp, opentelemetry-appender-tracing | 0.33.1 | 2026-10-09 | 1.75 |
| metrics | 0.24.6 | 2026-05-13 | 1.71.1 |
| metrics-exporter-prometheus | 0.18.3 | 2026-04-30 | 1.71.1 |
| console-subscriber | 0.5.0 | 2025-10-30 | 1.74 |
| tokio-console | 0.1.14 | 2025-10-30 | 1.74 |
| tokio-metrics | 0.5.2 | 2026-08-28 | 1.70 |
| thiserror | 2.0.21 | 2026-09-23 | 1.77 |
| anyhow | 1.0.104 | 2026-07-18 | 1.68 |
| snafu | 0.9.2 | 2026-07-21 | 1.65 |
| eyre | 0.6.14 | 2026-08-11 | 1.65 |
| color-eyre | 0.6.5 | 2025-05-30 | 1.65 |
| miette | 7.6.0 | 2025-04-27 | 1.70 |
| error-stack | 0.8.0 | 2026-07-03 | 1.83 |
| syn | 3.0.9 | 2026-10-12 | 1.71 |
| quote | 1.0.47 | 2026-07-19 | 1.71 |
| proc-macro2 | 1.0.107 | 2026-07-19 | 1.71 |
| trybuild | 1.0.122 | 2026-10-07 | 1.88 |
| pin-project-lite | 0.2.17 | 2026-02-27 | 1.37 |
| trait-variant | 0.1.3 | 2026-07-22 | 1.75 |
| bytes | 1.12.1 | 2026-07-08 | 1.57 |
| rayon | 1.12.0 | 2026-04-14 | 1.80 |
| crossbeam | 0.8.5 | 2026-09-05 | 1.61 |
| parking_lot | 0.13.0 | 2026-10-21 | 1.95 |
| dashmap | 6.2.1 | 2026-05-17 | 1.65 |
| arc-swap | 1.9.3 | 2026-10-18 | |
| flume | 0.12.0 | 2025-12-08 | |
| ractor | 0.16.5 | 2026-08-07 | 1.85 |
| kameo | 0.22.2 | 2026-07-18 | 1.88 |
| smol | 2.0.2 | 2024-09-07 | 1.63 |
| monoio | 0.2.4 | 2024-08-20 | |
| glommio | 0.9.0 | 2024-03-25 | 1.65 |
| hashbrown | 0.17.1 | 2026-05-09 | 1.85 |
| indexmap | 2.14.2 | 2026-09-05 | 1.85 |
| smallvec | 1.16.4 | 2026-10-23 | (2.0.0 is in beta) |
| ahash | 0.8.12 | 2025-05-08 | 1.60 |
| foldhash | 0.2.0 | 2025-08-23 | 1.60 |
| rustc-hash | 2.1.3 | 2026-07-02 | 1.77 |
| bumpalo | 3.20.3 | 2026-05-22 | 1.71.1 |
| slotmap | 1.1.1 | 2025-12-06 | 1.58 |
| self_cell | 1.3.0 | 2026-07-16 | |
| ouroboros | 0.18.5 | 2025-01-11 | |
| enum_dispatch | 0.3.13 | 2024-03-28 | |
| mimalloc | 0.1.52 | 2026-05-22 | |
| tikv-jemallocator | 0.7.0 | 2026-05-25 | |
| criterion | 0.8.2 | 2026-02-04 | 1.86 |
| divan | 0.1.21 | 2025-04-10 | 1.80 |
| gungraun (renamed from iai-callgrind; last iai-callgrind is 0.16.1, 2025-07-30) | 0.20.0 | 2026-09-26 | 1.88 |
| dhat | 0.3.3 | 2024-02-04 | |
| samply | 0.13.1 | 2025-02-01 | 1.77 |
| flamegraph (cargo-flamegraph) | 0.6.15 | 2026-11-02 | 1.88 |
| inferno | 0.12.8 | 2026-07-18 | 1.71 |
| cargo-bloat | 0.12.1 | 2024-05-10 | |
| cargo-llvm-lines | 0.4.48 | 2026-08-19 | 1.86 |
| proptest | 1.12.0 | 2026-10-10 | 1.88 |
| insta | 1.49.0 | 2026-10-03 | 1.66 |
| mockall | 0.15.0 | 2026-06-28 | 1.77 |
| loom | 0.7.2 | 2024-04-23 | 1.65 |
| turmoil | 0.7.2 | 2026-04-24 | |
| turmoil-fs, turmoil-io-uring | 0.1.0 | 2026-10-19 | |
| madsim | 0.2.34 | 2025-10-11 | |
| kani-verifier | 0.68.0 | 2026-09-16 | |
| cargo-fuzz | 0.13.2 | 2026-06-09 | |
| cargo-nextest | 0.9.151 | 2026-11-03 | 1.91 |
| cargo-llvm-cov | 0.9.1 | 2026-09-06 | 1.87 |
| cargo-mutants | 27.1.0 | 2026-06-02 | 1.88 |
| cargo-expand | 1.0.127 | 2026-10-09 | 1.90 |
| cargo-msrv | 0.19.6 | 2026-10-21 | 1.94.1 |
| cargo-hack | 0.6.46 | 2026-11-01 | 1.85 |
| cargo-semver-checks | 0.51.0 | 2026-10-03 | 1.93 |
| cargo-deny | 0.20.2 | 2026-07-09 | 1.88 |
| cargo-audit | 0.22.2 | 2026-06-05 | 1.88 |
| cargo-vet | 0.10.3 | 2026-10-22 | 1.85 |
| cargo-auditable | 0.7.7 | 2026-10-02 | |
| cargo-cyclonedx | 0.5.9 | 2026-03-19 | 1.85 |
| cargo-geiger | 0.14.0 | 2026-10-12 | 1.97 |
| cargo-chef | 0.1.78 | 2026-08-12 | |
| cargo-zigbuild | 0.24.1 | 2026-11-04 | 1.88 |
| cross | 0.2.5 | 2023-02-04 | |
| cargo-dist | 0.34.0 | 2026-11-02 | 1.74 |
| cargo-binstall | 1.26.2 | 2026-10-31 | 1.96.1 |
| bindgen | 0.73.2 | 2026-09-08 | 1.71 |
| cbindgen | 0.29.4 | 2026-06-09 | 1.74 |
| cxx | 1.0.205 | 2026-10-11 | 1.88 |
| pyo3 | 0.30.1 | 2026-10-14 | 1.85 |
| maturin | 1.16.0 | 2026-10-29 | 1.89 |
| napi | 3.16.1 | 2026-10-31 | 1.88 |
| jni | 0.22.4 | 2026-03-16 | 1.85 |
| uniffi | 0.32.2 | 2026-09-23 | |
| wasm-bindgen | 0.2.130 | 2026-10-30 | 1.81 |
| wasm-pack | 0.15.1 | 2026-10-30 | |
| wasmtime | 50.0.1 | 2026-10-29 | 1.97 (51.0.0 is at rc.1) |
| cargo-component | 0.21.1 | 2025-03-18 | |
| wit-bindgen | 0.63.0 | 2026-10-14 | |
| leptos | 0.8.23 | 2026-10-31 | 1.88 |
| dioxus | 0.7.10 | 2026-07-30 | 1.83 (0.8.0 is in alpha) |
| embassy-executor | 0.10.0 | 2026-03-20 | |
| embassy-time | 0.5.1 | 2026-03-20 | |
| embassy-sync | 0.8.0 | 2026-03-20 | |
| embassy-stm32 | 0.6.0 | 2026-03-20 | |
| embassy-rp | 0.10.0 | 2026-03-20 | |
| embassy-nrf | 0.11.0 | 2026-06-16 | |
| embassy-boot | 0.7.0 | 2026-03-20 | |
| embassy-boot-nrf | 0.12.0 | 2026-06-16 | |
| embedded-hal | 1.0.0 | 2024-01-09 | 1.60 |
| embedded-hal-async | 1.0.0 | 2024-01-09 | 1.75 |
| embedded-hal-mock | 0.11.1 | 2024-06-02 | 1.63 |
| cortex-m | 0.7.10 | 2026-10-22 | 1.85 |
| cortex-m-rt | 0.7.7 | 2026-09-24 | 1.85 |
| panic-probe | 1.0.0 | 2025-04-01 | |
| static_cell | 2.1.1 | 2025-06-22 | |
| portable-atomic | 1.15.0 | 2026-08-09 | 1.34 |
| esp-hal | 1.2.2 | 2026-09-18 | 1.95 |
| trouble-host | 0.8.0 | 2026-08-25 | |
| sequential-storage | 8.0.2 | 2026-10-01 | 1.87 |
| critical-section | 1.2.0 | 2024-10-16 | (1.3.0 is yanked) |
| heapless | 0.9.3 | 2026-04-30 | 1.87 |
| rtic | 2.4.0 | 2026-10-21 | |
| rtic-monotonics | 3.0.0 | 2026-10-21 | |
| probe-rs, probe-rs-tools | 0.32.0 | 2026-07-22 | 1.89 |
| defmt | 1.2.0 | 2026-10-15 | 1.81 |
| defmt-rtt | 1.3.1 | 2026-10-15 | |
| defmt-test | 0.5.1 | 2026-10-15 | |
| flip-link | 0.1.12 | 2025-11-10 | |
| ignore | 0.4.34 | 2026-10-27 | 1.88 |
| memmap2 | 0.9.12 | 2026-10-21 | 1.65 |
| zerocopy | 0.8.63 | 2026-10-13 | 1.56 |
| io-uring | 0.7.15 | 2026-09-07 | 1.63 |
| tokio-uring | 0.5.0 | 2024-05-27 | |
| zstd | 0.14.3 | 2026-10-16 | 1.64 |
| rdkafka | 0.39.0 | 2026-01-25 | 1.74 |
| object_store | 0.14.2 | 2026-09-15 | 1.85 |
| salsa | 0.28.5 | 2026-09-24 | 1.88 |
| redb | 4.5.0 | 2026-11-05 | 1.90 |
| fjall | 3.1.13 | 2026-10-25 | 1.90 |
| pingora | 0.9.1 | 2026-11-03 | |

`maturin`: `python/` pins 1.15.0. The current release is 1.16.0. Use 1.16.0 in `rust/`;
the integrator flags the stale `python/` pin to the owner and does not edit it.

`tokio` raised its MSRV from 1.71 (1.53.x) to 1.85 (1.54.0). That is the section's
standard example of a minor release that bumps MSRV. `sqlx` 0.9.0 (MSRV 1.94) is the second.

### Runtime defaults you may cite

| Fact | Value | Source |
|---|---|---|
| std thread default stack | 2 MiB on all tier 1 platforms; "platform-dependent and subject to change"; override with `Builder::stack_size` or `RUST_MIN_STACK` | `https://doc.rust-lang.org/stable/std/thread/index.html` "Stack size" |
| tokio blocking pool | `max_blocking_threads` default 512 | `tokio/src/runtime/builder.rs` at tag `tokio-1.54.1` |
| tokio coop budget | 128 per task poll | `tokio/src/task/coop/mod.rs` at tag `tokio-1.54.1` (`Budget(Some(128))`) |
| tokio worker local queue | 256 slots (`LOCAL_QUEUE_CAPACITY`) | `tokio/src/runtime/scheduler/multi_thread/queue.rs` at tag `tokio-1.54.1` |
| axum default body limit | 2 MB for `Bytes` and the extractors built on it (`String`, `Json`, `Form`); not applied to a body read directly with `Body::poll_frame` | `https://docs.rs/axum/0.8.9/axum/extract/struct.DefaultBodyLimit.html` |
| rustls default provider (0.23.x) | aws-lc-rs; `ring` through the `ring` feature; `default-features = false` drops aws-lc-rs | `https://docs.rs/rustls/0.23.46/rustls/` |

Layout numbers (`String` 24 bytes, a fat pointer 16 bytes, `Option<&T>` 8 bytes, the `Arc`
header 16 bytes) are true on 64-bit targets and are implementation facts, except the
documented null-pointer niche. See "Rust fact corrections" below.

### Status facts

Content states only what the Verdict column says, with the source. A row marked
"unconfirmed" is not stated at all.

| Fact | Verdict | Source |
|---|---|---|
| async-std | Discontinued. README title: "`async-std` has been discontinued; use `smol` instead". crates.io description: "Deprecated in favor of `smol`". Last release 1.13.2 (2025-08-15) | `https://github.com/async-rs/async-std`, crates.io API |
| `async fn` in `dyn Trait` | Not supported on stable 1.99.1. The Reference's dyn-compatibility rules exclude `async fn` and return-position `impl Trait`; `AsyncFn*` traits are not dyn compatible. Teach boxing or the `async-trait`/`trait-variant` patterns | `https://doc.rust-lang.org/reference/items/traits.html#dyn-compatibility` |
| Return type notation | Not stable as of 1.99.1 (absent from every release post 1.80 to 1.99 and from `RELEASES.md`). State nothing about its timeline | `RELEASES.md`, release posts |
| Polonius | Not stable as of 1.99.1. NLL is the stable borrow checker. State nothing about its timeline | `RELEASES.md`, release posts |
| `gen` blocks | `gen` is a reserved keyword in edition 2024. `gen` blocks are not stable as of 1.99.1 | edition guide `rust-2024/gen-keyword.html`, `RELEASES.md` |
| Edition 2027 | Unconfirmed: do not state. The edition guide has no 2027 chapter; the only hit is an internals forum discussion, not a decision | `https://doc.rust-lang.org/edition-guide/` |
| Rust for Linux | The kernel's "Rust experiment" is concluded. Miguel Ojeda posted "rust: conclude the Rust experiment" on 2025-12-13 after the 2025 Maintainers Summit. The same patch says not every configuration, architecture and toolchain works, and mixed GCC and LLVM builds stay experimental | `https://patchew.org/linux/20251213000042.23072-1-ojeda@kernel.org`, `https://lwn.net/Articles/1050308/` |
| Ferrocene and the FLS | The Rust Project adopted the FLS (formerly "Ferrocene Language Specification"), announced 2025-03-26. Ferrocene is Ferrous Systems' qualified Rust toolchain. Do not state certification levels or versions without a fetch | `https://blog.rust-lang.org/2025/03/26/adopting-the-fls/` |
| crates.io trusted publishing | Available for GitHub Actions (RFC 3691, announced 2025-07-11). GitLab CI/CD support added (announced 2026-01-21) | `https://blog.rust-lang.org/2025/07/11/crates-io-development-update-2025-07/`, `https://blog.rust-lang.org/2026/01/21/crates-io-development-update/` |
| OpenTelemetry Rust signal status | Stable: Logs API, Logs SDK, Logs OTLP exporter, Logs appender for tracing, Metrics API, Metrics SDK, Metrics OTLP exporter. Beta: Traces API, Traces SDK, Traces OTLP exporter, Context, Propagators, Metrics Prometheus exporter | `https://github.com/open-telemetry/opentelemetry-rust` README "Project Status" |
| rustls default provider | 0.23.46: aws-lc-rs by default. The `main` README describes 0.24, where the caller must pass a provider explicitly; 0.24 is not on crates.io, so do not teach it | `https://docs.rs/rustls/0.23.46/rustls/`, `https://github.com/rustls/rustls` |
| wasm-pack | Maintained in the `wasm-bindgen` GitHub org (`wasm-bindgen/wasm-pack`; the old `rustwasm` and `drager` repos point there). 0.15.1 released 2026-10-30 | `https://github.com/wasm-bindgen/wasm-pack`, crates.io API |
| bincode | Unmaintained. 3.0.0 (2025-12-16) contains only a README and a `compile_error!`; "No further releases will be published on crates.io". The README names wincode, postcard and rkyv as alternatives. Do not recommend bincode for new code | crates.io API `bincode/3.0.0/readme` |
| tokio LTS lines | `1.51.x` until March 2027 and `1.53.x` until September 2027, both MSRV 1.71. Pin an LTS with `tokio = { version = "~1.53" }` | `https://github.com/tokio-rs/tokio` README "LTS" |
| axum default body limit | 2 MB (see Runtime defaults) | docs.rs `DefaultBodyLimit` |
| `Atomic<T>` | The generic `std::sync::atomic::Atomic<T>` is nightly-only (`generic_atomic`, issue 130539) in std 1.99.1. 1.98 stabilized only `from_mut`, `get_mut_slice` and `from_mut_slice` on the concrete types. Teach `AtomicUsize`, `AtomicBool` and the other named types | `https://doc.rust-lang.org/stable/std/sync/atomic/struct.Atomic.html`, 1.98 release post |
| LLD default linker | 1.90 on `x86_64-unknown-linux-gnu`; opt out with `-C linker-features=-lld` | 1.90 release post |
| Portable SIMD (`std::simd`) | Not stable as of 1.99.1. Use `std::arch` intrinsics on stable | `RELEASES.md` |
| Cargo `trim-paths` | Not stable as of 1.99.1. Use `--remap-path-prefix` on stable | `RELEASES.md` |
| Android and Chromium memory-safety figures | Unconfirmed: fetch the primary source (Google security blog) before writing any number | none fetched |
| Company adoption claims (Discord, Dropbox, Cloudflare, Figma, Pingora figures) | Unconfirmed: fetch each primary source before writing any number | none fetched |

---

## Module list (30 modules, 6 phases)

The slugs are frozen. Renaming a module directory re-keys its questions (see "Question ids").

| # | Phase | Module directory | Q&A floor |
|---|---|---|---|
| 1 | 1 Ownership and the Type System | `ownership_borrowing_and_raii/` | 18 |
| 2 | 1 | `lifetimes_and_the_borrow_checker/` | 18 |
| 3 | 1 | `traits_generics_and_trait_objects/` | 18 |
| 4 | 1 | `enums_pattern_matching_and_type_driven_design/` | 15 |
| 5 | 1 | `error_handling_and_panics/` | 15 |
| 6 | 1 | `collections_and_strings/` | 15 |
| 7 | 1 | `closures_and_iterators/` | 15 |
| 8 | 1 | `smart_pointers_and_interior_mutability/` | 18 |
| 9 | 1 | `modules_crates_and_cargo/` | 15 |
| 10 | 2 Metaprogramming, Unsafe and Interop | `macros_declarative_and_procedural/` | 15 |
| 11 | 2 | `unsafe_rust_and_undefined_behavior/` | 15 |
| 12 | 2 | `ffi_and_language_interop/` | 15 |
| 13 | 3 Concurrency and Async | `threads_send_sync_and_locks/` | 18 |
| 14 | 3 | `channels_atomics_and_shared_state/` | 15 |
| 15 | 3 | `async_futures_and_pinning/` | 18 |
| 16 | 3 | `async_runtimes_and_executors/` | 15 |
| 17 | 3 | `async_cancellation_and_pitfalls/` | 18 |
| 18 | 4 Performance and Verification | `performance_and_zero_cost_abstractions/` | 15 |
| 19 | 4 | `profiling_and_benchmarking/` | 15 |
| 20 | 4 | `testing_fuzzing_and_miri/` | 15 |
| 21 | 5 Services and Ecosystem | `web_services_axum_tower_and_hyper/` | 15 |
| 22 | 5 | `serde_and_serialization/` | 15 |
| 23 | 5 | `databases_sqlx_and_diesel/` | 15 |
| 24 | 5 | `grpc_with_tonic/` | 15 |
| 25 | 5 | `observability_tracing_and_opentelemetry/` | 15 |
| 26 | 6 Platforms, Judgment and Production | `embedded_rust_and_bare_metal/` | 15 |
| 27 | 6 | `webassembly_and_wasi/` | 15 |
| 28 | 6 | `library_api_design_and_semver/` | 15 |
| 29 | 6 | `build_deployment_and_supply_chain/` | 15 |
| 30 | 6 | `rust_vs_cpp_go_java_and_python/` | 15 |

The floor is a floor. The target is about 20 Q&As per module page (22 for the 18+
modules). Floor total for the section, sub-files included: 531.

`build_deployment_and_supply_chain` also exists in `go/`. The ids differ (`rust/` vs `go/`),
but search shows two pages with the same title. Keep the H1 specific: "Rust Build,
Deployment and Supply Chain".

### Decision models

Each row below holds the section's only copy of that decision. Other pages link to it.

| Page | Decision | Form |
|---|---|---|
| `channels_atomics_and_shared_state` | `Arc<Mutex<T>>` vs `RwLock` vs sharded locks vs channels vs actor vs atomics/arc-swap | table by contention and read:write ratio, plus a flowchart |
| `async_runtimes_and_executors` | Async at all? Tokio multi-thread vs `current_thread` vs thread-per-core vs smol vs embassy | flowchart plus a numbers table |
| `error_handling_and_panics/error_design_for_libraries_and_applications.md` | Error crate and error shape, library vs application | quadrant plus table |
| `rust_vs_cpp_go_java_and_python` | When to adopt Rust; migration seam | scorecard table plus flowchart |
| `traits_generics_and_trait_objects/monomorphization_vtables_and_dispatch_cost.md` | Generics vs `dyn` vs enum dispatch | table with code size and ns per call |
| `smart_pointers_and_interior_mutability` | `Box`/`Rc`/`Arc`; `RefCell` vs `Mutex`; indices or arenas instead | flowchart |
| `ffi_and_language_interop` | bindgen vs cxx vs hand-written; in-process FFI vs subprocess vs service | table |
| `unsafe_rust_and_undefined_behavior` | Unsafe-code policy and review gates | checklist plus table |
| `testing_fuzzing_and_miri` | Verification ladder: unit, property, fuzz, Miri, loom, Kani or deterministic simulation | cost vs bug-class table |
| `modules_crates_and_cargo/editions_toolchains_and_msrv.md` | MSRV policy | table |
| `web_services_axum_tower_and_hyper`, `databases_sqlx_and_diesel`, `serde_and_serialization` | Framework, data access and format | tables |
| `embedded_rust_and_bare_metal`, `webassembly_and_wasi`, `build_deployment_and_supply_chain` | RTIC vs Embassy vs RTOS; Wasm deployment shape; dependency admission (deny-only vs cargo-vet audits) | tables |

---

## Sub-files (4, 14-section template, 15+ Q&As each)

Each sub-file exists because it needs a different study tier from its parent page.

| File | Parent | Tiers | Why separate |
|---|---|---|---|
| `traits_generics_and_trait_objects/monomorphization_vtables_and_dispatch_cost.md` | 3 | principal | What rustc emits per instantiation, code bloat and `cargo llvm-lines`, vtable layout, the limits of devirtualization, `enum_dispatch`. Writing generics is senior craft. Their cost is a principal argument |
| `error_handling_and_panics/error_design_for_libraries_and_applications.md` | 5 | senior, principal | thiserror vs anyhow vs snafu vs eyre vs miette vs error-stack; enum vs opaque error; context without losing `source`; mapping to HTTP or gRPC status at the edge; semver impact of public error types. Holds the error-crate decision model |
| `modules_crates_and_cargo/editions_toolchains_and_msrv.md` | 9 | principal | The six-week train, `rust-toolchain.toml`, editions per crate, the full edition 2024 change list, `cargo fix --edition` at workspace scale, MSRV policies, `cargo-msrv` and `cargo hack`, `cfg_select!`. Holds the MSRV decision model. No edition 2027 content |
| `async_runtimes_and_executors/tokio_deep_dive.md` | 16 | senior | The inject queue, the 256-slot local queue, the LIFO slot, work stealing, the 128 coop budget, the 512-thread blocking pool, `block_in_place`, the mio driver, the timer wheel, `JoinSet`, runtime metrics, tokio-console, the LTS lines |

### Deep-dive naming rule

`async_runtimes_and_executors/tokio_deep_dive.md` is the only file in this section allowed a
`*_deep_dive` or `*_internals` name. `DEEP_NAME_RE` in `game/build_tech.py` makes any such
page FATAL under `--strict` unless it is listed in `PRODUCT_DEEP_DIVES` or
`CONCEPT_DEEP_DIVES`. Add `"rust/async_runtimes_and_executors/tokio_deep_dive.md"` to
`PRODUCT_DEEP_DIVES` in the same change as the file. Tokio is owned here, so there is no
`technologies/tokio`.

---

## Case studies (7 plus 2 cross-cutting)

11-section principal template: `## Intuition`, then `## 1. Requirements Clarification`
through `## 11. Interview Discussion Points`. Quality bar for every study: 900-1100 lines;
4 or more `cross_cutting/` references; executable Rust in §4 with a `// BROKEN` then
`// FIX` pair; named companies in §6; quantified impact in §9; 10 or more Q&As in §11, each
with a `**Short:**` line.

These are Rust-runtime studies. They stop where `hld/` starts. Each `## Intuition` states
the Rust-specific thesis and links the sibling study it must not duplicate.

| File | Tiers | Concern | Must not duplicate |
|---|---|---|---|
| `case_studies/design_a_high_performance_l7_proxy.md` | senior, principal | About 1M req/s on 32 cores, 200k connections, under 2 ms added p99. Work stealing vs thread-per-core, a hyper/tower stack, `Bytes` zero-copy, per-worker pools, arc-swap config reload, cancel-safe forwarding, memory per connection, allocator fragmentation. Real examples: Pingora, linkerd2-proxy, ztunnel (fetch every figure) | `go/case_studies/design_a_grpc_edge_proxy.md` |
| `case_studies/design_an_async_event_ingest_pipeline.md` | senior | About 500k events/s into object storage. Bounded mpsc backpressure, size-or-time batching, `spawn_blocking` for zstd, `TaskTracker` shutdown, cancel-safe offset commit, at-least-once delivery, a memory bound | `go/case_studies/design_a_high_throughput_ingest_service.md` |
| `case_studies/design_a_pyo3_python_extension.md` | senior, principal | A tokenizer or validator with about 10M monthly installs. Builds on `python/c_extensions_and_native_interop` §6.1 and §14 and never restates them: the wheel matrix (manylinux, musllinux, macOS, Windows), abi3 vs per-version wheels, free-threaded wheels, release engineering, honest benchmarking | `python/c_extensions_and_native_interop` §6.1 and §14, `python/case_studies/design_a_validation_library.md` |
| `case_studies/design_a_fast_developer_tool.md` | senior | A ruff/uv/ripgrep-class linter over 5M lines in under 1 s. Parallel walk with `ignore`, mmap vs read, arena ASTs, interning, rayon, a content-hash cache, salsa-style incremental recompute, cargo-dist releases | `go/case_studies/design_a_cross_platform_cli_tool.md` |
| `case_studies/design_a_zero_copy_storage_engine.md` | principal | A redb/fjall-shaped KV store. Lifetimes tie borrowed reads to read transactions; mmap soundness; io_uring with owned buffers; copy-on-write B-tree MVCC; crash consistency; `Send`/`Sync` of transaction types; fuzz, Miri, loom and deterministic simulation | `go/case_studies/design_an_embedded_kv_store.md`, `database/storage_engines_internals` |
| `case_studies/design_an_incremental_rust_migration.md` | principal | Moving a hot path to Rust in a 40-engineer org. Candidate scorecard, FFI seam vs service seam, ramp and review capacity, CI compile cost, unsafe policy, success metrics, rollback. Real examples: Discord, Dropbox, Cloudflare, Android, Figma (fetch every claim) | `go/go_vs_java_and_python` |
| `case_studies/design_a_sensor_node_firmware.md` | - (Full only) | `no_std` and Embassy on an MCU with 256 KB RAM. A microamp sleep budget, OTA with embassy-boot A/B slots, defmt and probe-rs, host-side tests through embedded-hal traits, hardware-in-the-loop CI, flip-link | none |
| `case_studies/cross_cutting/memory_budgets_without_a_gc.md` | not listed | RSS vs live allocations, glibc arena fragmentation (`MALLOC_ARENA_MAX`), jemalloc vs mimalloc, container limits and allocation-failure aborts, dhat and heaptrack, bounded queues as memory caps, `Arc` cycle leaks. Contrasts with `go/case_studies/cross_cutting/gc_and_memory_budgets.md` | - |
| `case_studies/cross_cutting/unsafe_review_and_soundness_checklist.md` | not listed | SAFETY comments, `clippy::undocumented_unsafe_blocks`, the module-privacy boundary, Miri in CI and its limits, sanitizers, cargo-geiger, a review sign-off policy, real soundness-bug examples | - |

There is no `benchmarking_rust_correctly.md`. `profiling_and_benchmarking` owns that
material. The cross-cutting files use the 14-section template.

`case_studies/case_studies.md` has 5 sections, as `go/` has:
1. Quick Start: proxy, ingest, pyo3.
2. Full Learning Path, grouped as "Throughput and latency" (proxy, ingest), "Interop and
   tooling" (pyo3, developer tool), "Storage and constrained targets" (storage engine,
   firmware) and "Adoption" (migration).
3. Cross-Cutting / Shared Primitives table.
4. ASCII dependency map.
5. Interview Prep Shortcuts.

Case-study Q&As never enter the main bank. `extract.py` puts them in the reader-only
`game/case_questions/rust.json` pool. Do not move one under a module's §12 to "rescue" it.

`case_studies/cross_cutting/` is in `CS_EXCLUDE_DIRS`. Listing a file under it in the
`<!-- study-paths -->` block fails `--strict`. Neither cross-cutting file nor
`case_studies.md` appears in the block.

---

## Learning paths

Senior is the craft: build it, debug it, say why it broke. Principal is the judgment: which
approach at what cost, what migration, what not to build. They are different cuts, not
nested depths. Do not promote a module to principal because it is advanced.

- Senior: 21 modules. Ownership, lifetimes and `Send`/`Sync` errors, the async cancel-safety
  and blocking traps, and the service crates that silently cap a service.
- Principal: 13 modules. Monomorphization and `dyn` cost, error-type contracts, MSRV and
  edition policy, unsafe and FFI policy, the runtime choice, the dependency-admission
  policy, and when Rust is the wrong answer. Six principal modules are principal-only
  (`unsafe_rust_and_undefined_behavior`, `ffi_and_language_interop`,
  `webassembly_and_wasi`, `library_api_design_and_semver`,
  `build_deployment_and_supply_chain`, `rust_vs_cpp_go_java_and_python`), plus two
  principal-only sub-files.
- Full only: `macros_declarative_and_procedural`, `grpc_with_tonic`,
  `embedded_rust_and_bare_metal`.

Membership is declared once, in the `<!-- study-paths -->` block in `README.md`. The
integrator writes that block in the wiring phase from `.rust-work/PLAN.md` §5. Order comes
from `STUDY_ORDER.rust` in `game/app.js`. There is no path array in `app.js` to edit.

---

## Q&A rules

- Every Q&A is `**Q: ...?**` followed directly by an authored `**Short:**` line of 15-220
  characters as the first non-blank answer line. This section ships Short-migrated from the
  start.
- Floors as in the module table: 15, or 18 for the seven 18+ modules. 15 per sub-file.
- Order by interview frequency: traps first, then mechanics, then edge cases.
- Keep first sentences lexically distinct. Rust answers cluster on "borrow", "lifetime",
  "trait", "Send" and "future", and near-identical openers make indistinguishable MCQ options.
- Never number Q&As as a Markdown list.

---

## Authoring rules

- Module pages and sub-files: the 14-section template. No line-count target. Do not pad.
- A dated version-anchor blockquote under the H1 that uses only rows from the pin tables.
- §6 heading is `## 6. How It Works - Detailed Mechanics` with a plain hyphen, as `mlops/` has.
- §11 stays `## 11. Technologies & Tools`. This section feeds `tech_index.json`. Run the §11
  lint in `technologies/CLAUDE.md` ("The §11 lint") before you finish a §11.
- Boundary sentence: only where the boundary table names a real overlap (see above).
- Sibling links: link a sibling `rust/` page where it helps the reader, written
  `../<slug>/<slug>.md`. There is no fixed minimum; `build_graph.py` also uses lexical overlap.
- No em dash (U+2014), no en dash (U+2013), no emoji. Check:
  `grep -rnP '[\x{2013}\x{2014}]' src/main/java/com/rutik/systemdesign/rust` prints nothing.
- No `README.md` inside a module directory.

### Code style and the compile check

- ` ```rust ` fences, rustfmt style with 4-space indents, edition 2024.
- **Compile check, mandatory for every file.** Docker has `rust:1.99.1-slim`. Run
  `python3 /Users/rutik/rutik/code/system-design/.rust-work/rscheck.py <file.md>`. It must
  exit 0. Its contract:
  - a fence that contains `// fragment` is skipped;
  - a fence that uses a crate outside `std`/`core`/`alloc` is skipped;
  - a fence that contains `// BROKEN` must FAIL to compile;
  - every other ` ```rust ` fence must compile on 1.99.1, edition 2024 (as a bin if it has
    `fn main`, else as a lib).
- So std-only examples compile as written. Crate-based examples (tokio, axum, serde) are
  written with care and are not checked. Mark a partial snippet `// fragment` only because it
  is partial, never to hide a compile failure.
- `// BROKEN` then `// FIX` pairs in §10 and §14 of every module, with the compiler error
  code in a comment (for example `// error[E0502]`).
- Every `unsafe` block has a `// SAFETY:` comment.
- No bare `unwrap()` in production-shaped code.
- Concrete numbers, each one from the pin tables or fetched first.

### Rust fact corrections

Do not repeat these errors from the first plan draft.

- Variance: `&'a T` is covariant in `'a` and `T`. `&'a mut T` is covariant in `'a` and
  invariant in `T`. `Cell<T>` and `UnsafeCell<T>` are invariant in `T`.
- `RefCell::borrow_mut` panics on a conflict. `try_borrow_mut` returns `BorrowMutError`.
- Layout numbers (`String` 24 bytes, a fat pointer 16 bytes, `Option<&T>` 8 bytes, the `Arc`
  header 16 bytes) hold on 64-bit targets. They are implementation facts that the docs do not promise.
  The exception is the documented null-pointer niche for `Option<&T>`, `Option<Box<T>>` and
  `Option<NonNull<T>>`. Write "on 64-bit targets" and show `size_of` asserts.
- `Vec` growth: amortized O(1) `push` is documented. The docs do not promise a growth
  factor. The current implementation doubles. Say both.
- Unwinding: since 1.81 a Rust panic that reaches an `extern "C"` (non-unwind) boundary
  aborts the process. A foreign exception that unwinds into Rust through a non-`-unwind` ABI
  is undefined behavior. Use `extern "C-unwind"` when unwinding must cross.
- The generic `Atomic<T>` is not stable in 1.99 (nightly `generic_atomic`). 1.98 stabilized
  only some conversion APIs on the concrete types. Teach `AtomicUsize` and the other named
  types.
- `Pin` does not make a self-referential struct safe by itself. Construction and projection
  need a sound abstraction (`pin-project-lite`, or the async state machine the compiler
  generates). The safe-Rust answer: indices, owned data plus offsets, or `self_cell` or
  `ouroboros`.
- Cargo features unify per build over the packages selected in that build. Resolver 2 and 3
  keep build dependencies, proc macros and dev dependencies separate. "Unified across the
  whole workspace" is not the default behavior.
- `use<..>` precise capturing is `[1.82]`. Edition 2024 changes the default RPIT capture
  rules (`[ed2024]`). 1.87 allows `use<..>` in trait definitions.
- Let chains need both `[1.88]` and `[ed2024]`.

---

## Question ids: what re-keys them

`qid()` in `game/extract.py` hashes `module|question`, giving `rust/<module>#<md5[:12]>`.
`sourceFile` is not hashed.

- Renaming a sub-file does not re-key its questions. They keep their module.
- Moving, merging or renaming a module directory re-keys every question in it.
  `startReview()` in `game/app.js` then deletes the orphaned review ids, and learners lose
  their spaced-repetition state for that module. The slugs in this file are frozen before
  the first deploy.

---

## Mermaid traps

A Mermaid fence that does not parse fails the build (`scripts/build_diagrams.mjs` exits 1).
Validate every file you touch: `node scripts/check_render.mjs <absolute-file.md>` from the
repo root. Run `/mermaid-diagrams` before you author a diagram. Colour every node with the
One-Dark `classDef` block from that skill.

| Trap | Where Rust content hits it | Fix |
|---|---|---|
| `<` or `>` in a label | `Vec<T>`, `Arc<Mutex<T>>`, `Pin<&mut Self>` | quote the label and write `#lt;`/`#gt;`, or write it in words ("Arc of Mutex") |
| `&` in a label | `&T`, `&mut T` | write "ref", "ref mut", or `#amp;` |
| `'` in a label | `'a`, `'static` | keep it inside a quoted label, or write "lifetime a" |
| Closure pipes in a flowchart | `\|x\| x + 1` collides with edge-label syntax | never put closure syntax in a flowchart; describe it |
| `#` in a label | `#[derive]`, `#[instrument]`: `#` starts an entity code | keep attributes out of labels |
| `;` in a `sequenceDiagram` Note or message | `let x = 5;`, `tokio::spawn(fut);` | use `,` or drop the semicolon |
| `::` in a `stateDiagram-v2` description | `Poll::Pending`, `Poll::Ready` | write "Pending", "Ready" |
| `{` or `}` in an unquoted node label | struct literals, `format!("{x}")` | quote the label or keep code in a ` ```rust ` fence |
| Reserved word as a node id or `classDef` name | `default`, `end`, `call`, `class`, `style`, `click` | rename (`defArm`, `done`, `doCall`) |
| Spaces after an edge label's closing `\|` | an aligned column of edges | one space only |
| `\"` in a label | string literals, serde attributes | `#quot;`, or keep it in a code fence |

Mermaid fences are not allowed in any `CLAUDE.md` file, this one included.

---

## Adding a new rust module

1. Create `<module>/<module>.md` with the 14-section template. The page is named for its
   folder.
2. Write the version anchor from the pin tables. Fetch anything else from its primary source.
3. Meet the Q&A floor, each answer opening with its `**Short:**` line.
4. Run the per-file checks: `check_render.mjs`, `diagram_tools.py check`, the dash grep,
   `rscheck.py`, Vale, and the per-file Python checker in `.rust-work/PLAN.md` §9.
5. Add `"rust/<module>"` to `STUDY_ORDER.rust` in `game/app.js` at its phase position.
6. Add the module and every file it owns to the `<!-- study-paths -->` block in `README.md`,
   with tiers (`-` for Full path only). Then run `python3 game/extract.py --write-paths`.
7. Run `python3 game/extract.py --strict`. It must exit 0 and show a rust count.
8. Flip the file's status in the `README.md` Build Manifest.
9. Update the root `README.md` and root `CLAUDE.md` rows for this section.

To add a case study: write it, add its block line, and update `case_studies/case_studies.md`
(all 5 sections), the `README.md` case-study table and the case-study list in this file, in
the same change.

---

## HARD RULE: structure lives in `README.md`, content files hold only content

The section `README.md` is the single source of truth for this section's file inventory and
study-tier membership. Its `<!-- study-paths -->` block lists every module, every file that
module owns (the module page and every sub-file), and every case study, each tagged with its
tiers. `-` means Full path only.

A content file carries no structural metadata: no `<!-- study-paths -->` block, no tier
declaration, no path membership.

A file on disk that is missing from the block, or listed there and absent from disk, fails
`python3 game/extract.py --strict` and takes the Pages deploy red. The parser splits each
file line on its last double space, so put at least two spaces between a filename and its
tiers.

Order is never declared in the block. It comes from `STUDY_ORDER` in `game/app.js`. The tier
tables in the README are generated from the block by `python3 game/extract.py --write-paths`.
Never hand-edit them.
