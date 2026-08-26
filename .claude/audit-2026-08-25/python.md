# Python Audit — 2026-08-25

## 0. Snapshot

`python/` is a 21-module pure-Python senior guide, structurally the most *uniform* section in the
repo: all 24 files carry a complete 14-section template, every one clears the 15-Q&A floor, every one
has 4–9 Mermaid diagrams, an authored `**Short:**` on every Q&A, 5–7 broken→fixed pairs, and a real
(135–316 line) in-file §14 case study. Nothing here is a stub. Its genuine strength is *quantitative
rigour* — `collections_and_data_structures` derives dict resize arithmetic from the 2/3 load factor,
`cpython_memory_model` gives the 16-byte `PyObject` header and gen0/1/2 thresholds with pause budgets,
`async_patterns_and_pitfalls` derives semaphore sizing from Little's Law, and `the_gil_and_free_threading`
is genuinely current on PEP 779. The brief's hypothesis that the GIL treatment is now wrong is
**falsified** — that module is the best-maintained file in the section.

The single biggest weakness is **coverage, not quality**: `python/` has **zero case studies** (the only
in-scope section with none), and three whole topic clusters that a 2026 senior interview reaches for are
absent from all 24 files — **the import system** (`importlib` appears twice in the entire section;
`namespace package`, `MetaPathFinder`, `circular import` score zero), **the C/Rust extension boundary**
(`Limited API`, `Stable ABI`, `pybind11` all zero; `PyO3` four hits in one file), and **data modelling**
(157 `dataclass` mentions scattered across 16 files with no home module; `enum`/`StrEnum` essentially
uncovered). Secondary: `performance_and_profiling` and `packaging_and_project_tooling` — the two
fastest-moving areas — are the two smallest modules and are frozen roughly at a 3.13 / early-2025 view
of the world.

| Modules | Case studies | Sub-files | Total lines | Total Q&As | Avg lines/module page |
|---------|--------------|-----------|-------------|-----------|-----------------------|
| 21 | **0** | 3 | 31,381 | 385 (all with `**Short:**`) | 1,281 |

Comparison anchor: `java/` has 33 modules + 8 case studies + 4 `cross_cutting/` sub-files (9,467
case-study lines). `python/` at 21/0/3 is the outlier.

---

## 1. Missing — proposed ADDITIONS

### Modules and sub-files

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `python/the_import_system/the_import_system.md` | module | Zero coverage today (`importlib` = 2 hits section-wide; `namespace package`/`MetaPathFinder`/`circular import` = 0). Newly topical: **PEP 810 explicit lazy imports** was accepted unanimously by the SC on 2025-11-03 and ships in 3.15 (rc1 out 2026-08-04) — a new `lazy import x` **keyword**, the first import-statement syntax change in a decade. <https://peps.python.org/pep-0810/> · <https://docs.python.org/3.15/whatsnew/3.15.html> | **P0** | 1,300 |
| `python/c_extensions_and_native_interop/c_extensions_and_native_interop.md` | module | The free-threaded build made the C boundary a first-class topic: `Py_mod_gil = Py_MOD_GIL_NOT_USED`, and **PEP 803 "abi3t"** proposes a Stable ABI *for free-threaded builds*. Section has zero on Limited API / Stable ABI / pybind11 / nanobind, and PyO3 only incidentally. Rust-backed extensions (pydantic-core, ruff, uv, tiktoken) are now the default answer to "make this fast". <https://peps.python.org/pep-0803/> · <https://docs.python.org/3/howto/free-threading-extensions.html> | **P0** | 1,300 |
| `python/dataclasses_enums_and_data_modeling/dataclasses_enums_and_data_modeling.md` | module | `dataclass` is referenced 157 times across 16 files and owned by none; `enum`/`StrEnum`/`IntEnum` appear in exactly one file (2 hits). `copy.replace()` [3.13] and `field(kw_only=True)`/`slots=True` are absent everywhere. This is the boundary module against `fastapi/pydantic_v2_deep_dive`. <https://docs.python.org/3/whatsnew/3.13.html> | P1 | 1,250 |
| `python/performance_and_profiling/the_modern_cpython_runtime.md` | sub-file | The parent module's newest fact is "3.13 introduced an experimental JIT". Missing: **PEP 744** by name + copy-and-patch, the **3.14 tail-call interpreter** (`--with-tail-call-interp`, 3–5% geomean, Clang ≥19), the 3.15 JIT (8–9% x86-64 Linux / 12–13% aarch64 macOS), **PEP 669 `sys.monitoring`** (zero hits), **PEP 799** `profiling.sampling` ("tachyon") with `profile` deprecated for 3.17 removal. <https://docs.python.org/3/whatsnew/3.14.html> · <https://realpython.com/python315-jit-compiler/> · <https://peps.python.org/pep-0799/> | **P0** | 900 |
| `python/packaging_and_project_tooling/supply_chain_and_publishing.md` | sub-file | Parent has zero hits for **PEP 751 `pylock.toml`** (uv, pip 26.1 `install -r pylock.toml`, PDM all ship producers), **PEP 723** inline script metadata, **trusted publishing (OIDC)** — >1M files published via it, ~1 in 8 uploads — **PEP 740** attestations, `cibuildwheel`, and `cp314t` free-threaded wheel tags. <https://peps.python.org/pep-0751/> · <https://docs.pypi.org/trusted-publishers/> · <https://www.infoq.com/news/2026/05/pip-261-dependency-cooldowns/> | P1 | 900 |
| `python/the_type_system_and_typing/type_checkers_and_adoption.md` | sub-file | Both typing files name only mypy + pyright. **pyrefly (Meta) hit 1.0 in May 2026**, ships monthly, runs on Instagram/PyTorch/JAX, and checks pandas in 1.9s vs mypy's 144s; **ty (Astral)** is alpha but 10–100x faster; typing-spec conformance (Mar 2026): pyright 97.8%, pyrefly 87.8%, mypy 58.3%, ty 53.2%. Also missing PEP 705 `ReadOnly`, PEP 702 `@deprecated`, and a code example for PEP 696 TypeVar defaults. <https://www.danilchenko.dev/posts/pyrefly-vs-mypy-vs-ty/> · <https://pyrefly.org/> | P1 | 850 |
| `python/the_gil_and_free_threading/subinterpreters_and_interpreter_pools.md` | sub-file | PEP 684 + **PEP 734 `concurrent.interpreters` / `InterpreterPoolExecutor`** get one Q&A and a table row today. It is now the *third* executor next to Thread/Process pools and belongs beside them with a real benchmark. <https://peps.python.org/pep-0734/> · <https://docs.python.org/3/library/concurrent.interpreters.html> | P1 | 800 |

### Case studies — the whole track (currently zero)

Model: `java/case_studies/` — 8 "build the primitive" studies (663–815 lines) + 4 `cross_cutting/`
sub-files, indexed by a 5-section `case_studies.md`. Every proposal below is chosen because it can
**only** be written in Python — it exercises descriptors, the import system, the concurrency-model
choice, or CPython memory behaviour. None duplicates `fastapi/case_studies/` (which owns the
*web-service* studies) or `java/case_studies/`.

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `python/case_studies/case_studies.md` | index | Required by root `CLAUDE.md` the moment the first case study lands (5 sections: Quick Start, Full Learning Path, Cross-Cutting map, Dependency Map, Interview Prep Shortcuts) | **P0** | 170 |
| `python/case_studies/design_a_cpu_bound_batch_pipeline.md` | case study | The flagship. Benchmarks one workload four ways — `ThreadPoolExecutor` on 3.14, `ProcessPoolExecutor`, `python3.14t` free-threaded, `InterpreterPoolExecutor` — and shows the decision. This is the most consequential Python change in a decade and the section has no place where the four are compared end to end. <https://docs.python.org/3/howto/free-threading-python.html> · <https://peps.python.org/pep-0779/> | **P0** | 950 |
| `python/case_studies/design_a_plugin_system.md` | case study | Vehicle for the new import module: `importlib.metadata` entry points, PEP 420 namespace packages, a custom `MetaPathFinder`, a circular-import failure, `-X importtime`, and PEP 810 `lazy import` to cut CLI cold start. <https://peps.python.org/pep-0810/> · <https://hugovk.dev/blog/2025/lazy-imports/> | **P0** | 900 |
| `python/case_studies/design_a_validation_library.md` | case study | Build a mini-pydantic: `__init_subclass__` + `__set_name__` descriptors + annotation introspection under **PEP 649/749 `annotationlib`** + `exec`-based `__init__` codegen, then measure it against pydantic-core and explain why the Rust core wins. Explains the most-used library in the ecosystem from first principles. <https://peps.python.org/pep-0649/> | **P0** | 900 |
| `python/case_studies/cross_cutting/choosing_a_concurrency_model.md` | cross-cutting | The decision table (threads / processes / asyncio / free-threaded / subinterpreters) referenced by every concurrency module. Analogue of `java/case_studies/cross_cutting/concurrency_memory_visibility_primitives.md` | P1 | 750 |
| `python/case_studies/cross_cutting/benchmarking_python_correctly.md` | cross-cutting | Analogue of `java/.../benchmarking_with_jmh.md`. Specializing-interpreter warmup (~8 iterations), JIT warmup, GC disable, `pyperf` vs naive `timeit`, and why a 3.13 number is not a 3.14 number | P1 | 700 |
| `python/case_studies/design_a_streaming_etl.md` | case study | Constant-memory pipeline: generators, `itertools.batched` [3.12], `memoryview` zero-copy, pickle protocol 5 out-of-band buffers, `tracemalloc`-verified flat RSS over a 50 GB input | P1 | 850 |
| `python/case_studies/design_a_high_throughput_logging_pipeline.md` | case study | `QueueHandler`/`QueueListener` (absent section-wide), backpressure, sampling, structured JSON, OTel correlation. `stdlib_datetime_and_logging`'s own §14 computes 5.76M records/day and never reaches for the queue handlers | P1 | 800 |
| `python/case_studies/design_a_memory_efficient_cache.md` | case study | `__slots__`, `weakref.WeakValueDictionary`, `lru_cache` C internals, refcount vs cycle GC, and thread-safety under `python3.14t` where `lru_cache`'s implicit GIL protection no longer holds | P2 | 800 |

### What the P0s would contain (one line per template section)

**`the_import_system/the_import_system.md`** — 1: what `import x` actually executes. 2: the filing-cabinet
analogy (finders are the index, loaders fetch, `sys.modules` is the desk you already have it on).
3: `sys.modules` is authoritative; import is not idempotent, it is *memoised*; a module is an object.
4: regular vs namespace (PEP 420) vs frozen vs extension modules; absolute vs relative; `__init__.py`
vs implicit. 5: Mermaid `import` → `sys.meta_path` → `PathFinder` → `sys.path_hooks` → loader →
`exec_module` → `sys.modules`, plus the circular-import partially-initialised-module diagram.
6: writing a `MetaPathFinder`; `importlib.reload` and why it lies; `importlib.metadata` entry points;
PEP 810 `lazy import` and `-X importtime` with real ms. 7: pytest rootdir/conftest discovery, Django
app loading, setuptools entry points. 8: eager vs lazy import cost table; namespace vs regular package.
9: when a plugin registry beats an `if/elif`. 10: circular imports, `sys.path[0]` shadowing a stdlib
name, mutable module-level state, `reload` leaving stale class objects. 11: `importlib`,
`importlib.metadata`, `pkgutil`, `-X importtime`, `tuna`. 12: 16 Q&As. 13: absolute imports only, no
import-time side effects, lazy-import heavy optional deps. 14: a CLI whose cold start goes 420 ms → 90 ms.

**`c_extensions_and_native_interop/…`** — 1: why Python leaves Python. 2: the airlock analogy — the GIL
is the pressure seal and you must say when you leave it. 3: refcount ownership at the boundary; releasing
the GIL is *your* job; ABI vs API stability. 4: ctypes / cffi / Cython / mypyc / pybind11 / nanobind /
PyO3+maturin, and the buffer protocol as the zero-copy contract. 5: Mermaid of the CPython↔native boundary
with `Py_BEGIN_ALLOW_THREADS`; ASCII of a `memoryview` over a strided buffer. 6: a working PyO3 function;
the same in Cython; `Py_mod_gil = Py_MOD_GIL_NOT_USED` and what happens without it (RuntimeWarning, GIL
re-enabled at import); Limited API `abi3` and PEP 803 `abi3t`. 7: pydantic-core, tiktoken, ruff, numpy,
lxml. 8: build-complexity vs speed table with real multiples. 9: when a native extension is the wrong
answer (I/O-bound, or a `__slots__`/algorithm fix would do). 10: forgetting to release the GIL in a long
C call; returning a borrowed reference; an `abi3` wheel that is not `abi3`-clean; shipping only `cp314`
and breaking every `3.14t` user. 11: maturin, setuptools-rust, scikit-build-core, cibuildwheel,
`auditwheel`. 12: 16 Q&As. 13: prefer PyO3/Cython over the raw C API; build `abi3` where possible; always
ship both `cp3XX` and `cp3XXt`. 14: a 40x hot-path win where the honest conclusion is that 30x of it came
from the algorithm.

**`performance_and_profiling/the_modern_cpython_runtime.md`** — 1: what actually got faster since 3.11.
2: the interpreter as a self-rewriting machine. 3: specialise → warm up → measure warm, never cold.
4: specializing adaptive interpreter (3.11) / copy-and-patch JIT (PEP 744) / tail-call interpreter (3.14,
`--with-tail-call-interp`) / free-threaded build. 5: xychart of pyperformance 3.10→3.15 and a Mermaid of
the bytecode specialisation state machine. 6: reading specialised opcodes out of `dis`; `sys.monitoring`
(PEP 669) vs `sys.settrace` overhead; `profiling.sampling` (PEP 799) attaching to a live PID. 7: who
actually enables the JIT in production and why most do not. 8: JIT / tail-call / free-threading
gain-vs-cost table with the measured numbers. 9: when a build flag is the wrong lever. 10: benchmarking
cold; comparing a 3.13 number to a 3.14 number; leaving `cProfile` on in prod. 11: `pyperf`,
`sys.monitoring`, `profiling.sampling`, py-spy, memray, scalene. 12: 16 Q&As. 13: measure warm, pin the
interpreter build in the report. 14: a regression that turned out to be a benchmark bug.

**`case_studies/design_a_cpu_bound_batch_pipeline.md`** — the 11-section principal template. §2 sizes a
40M-record/day image-thumbnail workload. §4 implements the same pipeline four ways with measured
throughput and RSS on `3.14` and `3.14t`. §5 is the decision: pickling cost vs free-threading's 5–10%
single-thread tax vs subinterpreter isolation. §9 carries the war story where `fork` deadlocked a
multi-threaded parent — now the 3.14 `forkserver` default. §11 has 10+ Q&As.

---

## 2. Stale — verified CORRECTIONS

| File | Line / heading | What it says | What is true in Aug 2026 | Source URL |
|---|---|---|---|---|
| `threading_and_multiprocessing/threading_and_multiprocessing.md` | L13, §1 | "Python **3.12** introduced experimental free-threaded mode (no GIL) via `--disable-gil` build flag." | PEP 703 landed **experimentally in 3.13** (`python3.13t`); **PEP 779 made it officially supported in 3.14** (`python3.14t`), single-thread penalty 5–10%. Wrong version AND wrong status. It also **contradicts two sibling files** — `the_gil_and_free_threading.md` L14-19 and `event_loop_internals.md` Q15 both state the correct timeline. **The worst single defect in the section.** | <https://peps.python.org/pep-0779/> · <https://docs.python.org/3/howto/free-threading-python.html> |
| `threading_and_multiprocessing/threading_and_multiprocessing.md` | L42 §4; Q15 L974-977 | Only the "macOS/Windows default to `spawn` since 3.8" story; `forkserver` named generically as "a third option" | **In 3.14 the POSIX default start method changed from `fork` to `forkserver`.** `fork` is no longer the default on any platform; code needing it must call `get_context('fork')`. This broke real code (PyTorch issue #169252) and is a live interview question. | <https://docs.python.org/3/library/multiprocessing.html> |
| `performance_and_profiling/performance_and_profiling.md` | L9, §1 | "CPython **3.13** introduced an experimental JIT compiler (still opt-in at build time)" — the newest runtime fact in the file | 3.14 shipped the **tail-call interpreter** (3–5% geomean, Clang ≥19, PGO). The 3.15 JIT (rc1, 2026-08-04) is **8–9% x86-64 Linux / 12–13% aarch64 macOS**. PEP 744 is never named; copy-and-patch never explained. | <https://docs.python.org/3/whatsnew/3.14.html> · <https://realpython.com/python315-jit-compiler/> · <https://blog.python.org/2026/03/jit-on-track/> |
| `performance_and_profiling/performance_and_profiling.md` | L255-262, §6 | pyperformance table ends "Python 3.13 (JIT preview): ~133" | Two releases behind — no 3.14 or 3.15 rows. | <https://blog.python.org/2026/08/python-3150-rc1/> |
| `performance_and_profiling/performance_and_profiling.md` | §6.5 / §11 | `sys.settrace`/`setprofile` framing; tool table lists py-spy, memray, VizTracer, line_profiler | **PEP 669 `sys.monitoring` (3.12)** — zero hits section-wide — is the modern low-overhead hook that coverage.py and debuggers moved to. **PEP 799** adds a stdlib `profiling` package in 3.15 (`profiling.sampling`/"tachyon"; attaches to a live process, works on free-threaded builds); `profile` is deprecated for removal in 3.17. `scalene` absent. | <https://peps.python.org/pep-0669/> · <https://peps.python.org/pep-0799/> |
| `README.md` | §4 Version Matrix, last two rows | "Free-threading GIL-optional (`python3.13t`) \| 3.13 \| PEP 703, **experimental**" and "JIT compiler (copy-and-patch) \| 3.13 \| Experimental, ~5% speedup" | Matrix stops at 3.13 and freezes free-threading as experimental. Needs 3.14 rows (PEP 779 official, PEP 734 `concurrent.interpreters`, PEP 649/749, PEP 750 t-strings, PEP 765, `forkserver` default) and 3.15 rows (PEP 686 UTF-8 default, PEP 810 lazy imports, PEP 799 profiling). | <https://docs.python.org/3/whatsnew/3.14.html> · <https://docs.python.org/3.15/whatsnew/3.15.html> |
| `README.md` | L7, L19, L380 | "executable-shaped Python **3.11/3.12** code blocks"; "**Primary language:** Python 3.11/3.12"; "Python 3.11/3.12 primary" | 3.14 is stable, 3.15 rc1 (final 2026-10-01). **3.10 reaches EOL in Oct 2026** — `structured_concurrency.md` L643-645 already says so, so the README contradicts its own content files. | <https://peps.python.org/pep-0790/> · <https://devguide.python.org/versions/> |
| `README.md` | §7 "Conventions Reminder" block | "§5 Architecture Diagrams (**ASCII art only — no Mermaid**)"; "700-1000 lines per module README"; "Q&A minimum: **10+** standard"; "bold Q as `**Q1:**`"; "module README" | Every one contradicts root `CLAUDE.md` and the files on disk: Mermaid is the appeal-first standard (files carry 4–9 fences each), files run 1,045–1,712 lines, the floor is 15+ Q&As, the label is `**Q: `, and pages are `<module>.md` not `README.md`. **This block is a live mis-instruction to any future authoring agent.** | root `CLAUDE.md` |
| `packaging_and_project_tooling/packaging_and_project_tooling.md` | L1013 | `COPY --from=ghcr.io/astral-sh/uv:0.4.10 …` beside `FROM python:3.14-slim` | uv's latest stable is **0.11.8** (2026-04-27). A 0.4.x pin next to a 3.14 base image is internally inconsistent and predates `uv lock`/`uv add`/`uv python` being first-class. (Astral was acquired by OpenAI in March 2026.) | <https://astral.sh/blog/uv> · <https://github.com/astral-sh/uv/releases> |
| `packaging_and_project_tooling/packaging_and_project_tooling.md` | L507-508, §6 | Publishing shown only as `twine upload` / `uv publish --token $PYPI_TOKEN` | **Trusted publishing (OIDC) is the recommended path** — short-lived credentials expiring ≤15 min; >1M files published, ~1 in 8 PyPI uploads. Long-lived API tokens are the legacy fallback. PEP 740 attestations build on the same OIDC identity. | <https://docs.pypi.org/trusted-publishers/> · <https://peps.python.org/pep-0740/> |
| `packaging_and_project_tooling/packaging_and_project_tooling.md` | L308-314, §6 | Wheel-tag examples show only `cp313-cp313-manylinux…` | manylinux **removed CPython 3.13t on 2026-05-07** — 3.13t is superseded by the non-experimental 3.14t. A 2026 wheel-tag table that omits `cp314t` teaches the wrong build matrix. | <https://github.com/Quansight-Labs/free-threaded-compatibility> |
| `strings_bytes_encoding_and_regex/…` | §4.5 format spectrum, §6.5 f-string deep dive (newest tag PEP 701, 3.12) | Covers f-strings, `%`, `str.format`, `Template` | **PEP 750 t-strings (3.14)** — zero hits — are a string-*formatting* feature and this is the strings module; they are the sanctioned answer to "how do I interpolate safely into SQL/HTML". PEP 686 (UTF-8 default, 3.15) also absent despite §3's core principle being "UTF-8 is the universal default". | <https://peps.python.org/pep-0750/> · <https://peps.python.org/pep-0686/> |
| `context_managers_and_exceptions/…` | §2 mental model ("`__exit__` is a `finally`-equivalent"), §10 | Nothing on returning from `finally` | **PEP 765 (3.14)** now emits a `SyntaxWarning` for `return`/`break`/`continue` in a `finally` block that silently swallows an in-flight exception — exactly this module's central mental model. `Exception.add_note()` (PEP 678, same 3.11 wave as the `ExceptionGroup` content it covers deeply) also absent. | <https://peps.python.org/pep-0765/> |
| `file_io_and_serialization/…` | Q2, Q13, Pitfall 6 | Teaches `Path.rename()` vs `Path.replace()` vs the `shutil.move()` cross-device workaround | **`Path.copy()` and `Path.move()` were added in 3.14** and supersede the `shutil.move()` workaround this module teaches. `Path.full_match()` [3.13] also absent despite `glob`/`rglob` coverage. `tomllib` [3.11] absent despite §1 framing the module around "reading configuration". | <https://docs.python.org/3/whatsnew/3.14.html> |
| `the_type_system_and_typing/*.md` | L14 "Toolchain: mypy, pyright"; §11 tool tables | Only mypy and pyright | **pyrefly (Meta) reached 1.0 in May 2026**, ships monthly, runs Instagram/PyTorch/JAX, checks pandas in 1.9s vs mypy's 144s. **ty (Astral)** is alpha, 10–100x faster. Conformance Mar 2026: pyright 97.8 / pyrefly 87.8 / mypy 58.3 / ty 53.2. Also: PEP 705 `ReadOnly` and PEP 702 `@deprecated` absent; PEP 696 TypeVar defaults named in the header only, never demonstrated. | <https://www.danilchenko.dev/posts/pyrefly-vs-mypy-vs-ty/> · <https://pyrefly.org/> |
| `collections_and_data_structures/…` | L87 | `list` memory-layout diagram pinned "64-bit CPython **3.11**" | Every other benchmark in the same file cites 3.13 (L227, 671, 683, 1199, 1254). Internal inconsistency. Also PEP 584 dict union (`\|`/`\|=`, 3.9) is absent from a collections module. | internal |
| `data_model_and_objects/…`, `decorators_and_closures/…`, `iterators_and_generators/…` | `from __future__ import annotations` — data_model L267/601/672/1148 and elsewhere | Presented as the current idiom | **PEP 649/749 changed what that import means in 3.14** — annotations are now lazily evaluated to real objects by default, and `from __future__ import annotations` still forces *strings*, which is now usually the wrong choice. `the_type_system_and_typing.md` §6.11 gets this right; these three files do not. | <https://peps.python.org/pep-0649/> |

Two things checked and found **NOT stale** — do not "fix" them:

- **`the_gil_and_free_threading/the_gil_and_free_threading.md` is correct and current.** It states 3.13
  experimental → 3.14 PEP 779 official, quotes the 5–10% penalty (not the stale 3.13-era ~40%), covers
  `sys._is_gil_enabled()`, `PYTHON_GIL=0`/`-X gil=0`, `Py_mod_gil = Py_MOD_GIL_NOT_USED` with the
  RuntimeWarning re-enable path, PEP 684 and PEP 734. The brief's "the GIL treatment is now wrong"
  hypothesis is falsified here; the stale claim lives in `threading_and_multiprocessing.md` instead.
- **`cpython_memory_model/cpython_memory_model.md`** correctly says free-threading is "officially
  supported since Python 3.14 under PEP 779" (L118, L1259).

---

## 3. Reordering — SEQUENCE and TIER changes

### The single most important move

**`the_gil_and_free_threading` should leave Phase 2 (position 10) and open Phase 3 (position 15),
immediately before `threading_and_multiprocessing`.** It is no longer a CPython-internals curiosity;
since PEP 779 it is the *decision frame* for the entire concurrency phase. Today the two modules sit
five apart, and that distance is exactly how `threading_and_multiprocessing.md` L13 came to assert a
free-threading timeline that contradicts the module five positions earlier. Adjacency makes the
concurrency story one continuous argument: GIL/free-threading → threads vs processes vs interpreter
pools → asyncio → async pitfalls → the native boundary.

### All proposed moves

| Module | Current position / tier | Proposed | Why |
|---|---|---|---|
| `the_gil_and_free_threading` | 10, Phase 2 · senior+principal | **15, opens Phase 3** · senior+principal | See above. It is the premise of the concurrency phase, not an internals aside |
| `functional_programming` | 14, Phase 2 "CPython Internals" · Full only | **4, Phase 1** (after `iterators_and_generators`) · senior | It is not a CPython internal. It depends on generators and belongs with language-core idioms; its PEP 709 comprehension-inlining content is the natural sequel to `iterators_and_generators` |
| `packaging_and_project_tooling` | 21, last · **principal only** | 24, last · **senior + principal** | uv/`pyproject.toml`/lockfiles are asked at every senior screen now; excluding it from the Senior Path is the section's least defensible tier call. Keep it last in order — it is the least conceptual module — but it must be in the senior cut |
| `file_io_and_serialization` | 8 · Full only | 8 · **+ senior** | `pickle` RCE, streamed-upload memory math, and `fsync` vs `flush` are standard senior questions; the module already carries an 8000x memory-scaling derivation |
| `cpython_memory_model` | 9 · senior | 9 · **+ principal** | "Do we disable the cycle GC in this service, or shard into processes?" is a judgment call, and refcount vs biased refcount under free-threading is principal material |
| `threading_and_multiprocessing` | 15 · senior | 16 · **+ principal** | Choosing the concurrency model *is* the principal question in Python. Today the Principal Path covers async pitfalls but not the threads/processes/interpreters decision |
| `stdlib_datetime_and_logging` | 19 · Full only | 22 · **+ senior** | Timezone/`fold`/naive-datetime bugs and structured logging are among the most frequently asked production questions in the section |
| `design_patterns_in_python` | 18 · **principal only** | 21 · **senior + principal** | The "when NOT to abstract" material justifies principal, but excluding Pythonic GoF from the senior cut while `lld/` covers it in Java is an odd boundary |
| `dataclasses_enums_and_data_modeling` (NEW) | — | **8, Phase 1** · senior | Language-core data modelling; feeds `the_type_system_and_typing` and `fastapi/pydantic_v2_deep_dive` |
| `the_import_system` (NEW) | — | **12, Phase 2** · senior + principal | Depends on the object model; prerequisite for the plugin-system case study |
| `c_extensions_and_native_interop` (NEW) | — | **19, closes Phase 3** · principal | Depends on the GIL module and on performance; "do we drop to Rust" is a principal call |

### Proposed `STUDY_ORDER["python"]` (24 modules; 3 new entries marked)

```js
python: [
  // Phase 1 - Language Core & Data Model (10)
  "python/data_model_and_objects","python/core_language_idioms","python/iterators_and_generators",
  "python/functional_programming","python/decorators_and_closures","python/context_managers_and_exceptions",
  "python/collections_and_data_structures","python/dataclasses_enums_and_data_modeling",   // NEW
  "python/strings_bytes_encoding_and_regex","python/file_io_and_serialization",
  // Phase 2 - CPython Internals & Type System (5)
  "python/cpython_memory_model","python/the_import_system",                                 // NEW
  "python/metaclasses_and_metaprogramming","python/the_type_system_and_typing",
  "python/performance_and_profiling",
  // Phase 3 - Concurrency, Async & Native (5)
  "python/the_gil_and_free_threading","python/threading_and_multiprocessing",
  "python/asyncio_and_event_loop","python/async_patterns_and_pitfalls",
  "python/c_extensions_and_native_interop",                                                 // NEW
  // Phase 4 - Engineering Practice (4)
  "python/design_patterns_in_python","python/stdlib_datetime_and_logging",
  "python/testing_with_pytest","python/packaging_and_project_tooling",
],
```

Senior Path becomes 20 of 24; Principal Path becomes 11 of 24. Both stay ordered subsets by
construction, so only the `<!-- study-paths -->` block in `python/README.md` changes — never a
hand-edited tier table.

---

## 4. Depth — THIN modules and BLOAT

No module is thin by structure: all 24 files have 14 sections, 15+ Q&As, 4–9 Mermaid diagrams, 5–7
broken→fixed pairs, and a real §14. The thinness is **topical**, in the two fastest-moving modules —
which are, not coincidentally, the two smallest.

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `performance_and_profiling` | **1,045** (smallest) | 16 | PEP 744 by name, copy-and-patch, the 3.14 tail-call interpreter, 3.15 JIT numbers, `sys.monitoring` (PEP 669), PEP 799 `profiling` package, scalene, PyO3/Rust as a performance lever (only Cython/mypyc covered) | Add sub-file `the_modern_cpython_runtime.md` (P0, §1) + refresh the §6 pyperformance table and the §1 JIT sentence in place |
| `packaging_and_project_tooling` | **1,084** | 16 | PEP 751 `pylock.toml`, PEP 723 inline script metadata, trusted publishing / PEP 740, `cibuildwheel`, `cp314t` wheel tags, `pdm`; uv pinned at 0.4.10 and framed around `uv pip compile` rather than `uv lock`/`uv add`/`uv sync` | Add sub-file `supply_chain_and_publishing.md` (P1) + refresh the uv pin, the wheel-tag table, and the publish flow in place |
| `core_language_idioms` | 1,076 | 18 | **Positional-only `/` and keyword-only `*` parameters are absent** — a core idiom missing from the module named for core idioms. Also PEP 695 `type` statement, PEP 750 t-strings | In place: a §4 subsection + 2 Q&As, ~120 lines |
| `functional_programming` | 1,164 | 16 | `functools.singledispatch`/`singledispatchmethod` absent from both this module and `decorators_and_closures`; `functools.cache` [3.9] never named | In place: `singledispatch` deserves a §4 subsection here (dispatch *is* the functional answer to the Visitor pattern) + a Q&A, ~100 lines |
| `iterators_and_generators` | 1,372 | 16 | `itertools.batched` [3.12] and `pairwise` [3.10] absent while the module hand-rolls `islice` chunking in §6.6 and §14 | In place: use `batched`, keep the `islice` version as the pre-3.12 fallback, ~60 lines |
| `strings_bytes_encoding_and_regex` | 1,224 | 16 | PEP 750 t-strings; `str.removeprefix`/`removesuffix` [3.9]; Unicode normalization (`unicodedata`, NFC/NFD/NFKC — classic senior material on visually-identical-but-unequal strings) | In place: t-strings into §4.5/§6.5, a normalization subsection + 2 Q&As, ~180 lines |
| `testing_with_pytest` | 1,223 | 16 | `--cov-branch` (only line coverage discussed), `testcontainers` (never named despite a "pytest + real DB (Docker)" strategy row), `time-machine`, real mutation-testing content (`mutmut` is a one-line table stub), and the anyio-pytest-plugin tradeoff (which lives in `structured_concurrency.md`, the wrong file) | In place: ~150 lines across §4/§11/§12 |
| `stdlib_datetime_and_logging` | 1,307 | 16 | `logging.QueueHandler`/`QueueListener` absent section-wide — and this module's own §14 computes 5.76M records/day, exactly the scenario that needs them. `datetime.UTC` [3.11] never used (every example writes `timezone.utc`); the `utcnow()` deprecation never taught | In place: ~140 lines; the queue-handler material also seeds the P1 logging case study |
| `file_io_and_serialization` | 1,327 | 16 | `Path.copy`/`Path.move` [3.14], `Path.full_match` [3.13], `tomllib` [3.11], pickle protocol 5 out-of-band buffers (protocol 5 is named, its headline feature never explained), `zipfile`/`tarfile` and the `filter='data'` extraction default | In place: ~200 lines |

**Bloat / merge / delete: none recommended.** No two modules are redundant duplicates. The closest call
is `design_patterns_in_python` against `lld/` — but it is the *Pythonic* cut (module-as-singleton,
callables-as-strategy, why the GIL does not make check-then-set atomic) and its cross-links to `lld/`
are already in place. Keep it.

---

## 5. Cross-section notes

- **`fastapi/`** — the boundary is clean and should stay that way. `fastapi/case_studies/` owns the
  web-service studies (`design_async_task_queue`, `design_rate_limited_api_fastapi`, …); every case study
  proposed above is a *library/runtime* study with no HTTP surface. Note the leakage in the other
  direction: `packaging_and_project_tooling` §2 and §14 are written around "production FastAPI services"
  — fine as an example, but the module should not read as FastAPI-specific once it joins the Senior Path.
  `dataclasses_enums_and_data_modeling` must explicitly hand off to `fastapi/pydantic_v2_deep_dive`
  rather than re-teaching pydantic.
- **`ml/`** — has no numpy/pandas module and is ML-theory shaped, so the **buffer protocol / `memoryview`
  / zero-copy** story is homeless. Put it in the new `c_extensions_and_native_interop` module (it is the
  same contract PyO3 and Cython consume) rather than creating a scientific-Python module. Cross-link from
  `ml/gpu_and_hardware_optimization`.
- **`java/`** — `python/case_studies/` should mirror `java/case_studies/`'s shape (studies + a
  `cross_cutting/` directory + a 5-section `case_studies.md`), and `cross_cutting/benchmarking_python_correctly.md`
  is the deliberate analogue of `java/case_studies/cross_cutting/benchmarking_with_jmh.md`. The existing
  README cross-reference to `java/concurrency` for GIL-vs-JVM comparison gets much stronger once
  free-threading is in play — worth a reciprocal link from the Java side.
- **`cs_fundamentals/`** — `design_a_memory_efficient_cache.md` (P2) overlaps
  `cs_fundamentals/case_studies/design_lru_cache.md` and `java/case_studies/design_lru_cache_java.md` in
  *name* only; the Python angle is `__slots__`/weakref/refcount/free-threaded safety, not the algorithm.
  Say so explicitly in its §1 and cross-link both, or drop it — it is the weakest of the nine.
- **`technologies/`** — `uv`, `ruff`, `ty` and `pyrefly` are all Astral/Meta products with real depth. If
  the tech bank does not already carry them, the new `supply_chain_and_publishing.md` and
  `type_checkers_and_adoption.md` §11 tables are the natural feed.
- **`backend/`** — `async_patterns_and_pitfalls` already cross-links `backend/async_and_concurrency_patterns`.
  The new `choosing_a_concurrency_model.md` cross-cutting file should link there too, and vice versa.

---

## 6. Execution plan

Effort is in **agent-sessions** (one session ≈ one 1,000–1,300 line file authored to the quality bar,
web-verified, with 15+ Q&As each carrying a `**Short:**`).

Note the alignment with `FIX-BRIEF.md`: **Wave 0 below is entirely in-place editing** and is therefore
safe for the fix phase's content-only wave 1. Everything in Waves 1–2 below **creates files** and must
wait for the fix phase's wave 2, where `STUDY_ORDER` and the `<!-- study-paths -->` blocks are wired.

### Wave 0 — Corrections (0.5 sessions; in-place only; blocks nothing)

1. Fix `threading_and_multiprocessing.md` L13 (3.12/`--disable-gil` → 3.13 experimental / 3.14 PEP 779),
   add the 3.14 `forkserver` default to §4 and Q15, and name `InterpreterPoolExecutor` as the third
   executor. **Re-read the `**Short:**` line above every answer touched** — the audit owns the Short.
2. Refresh `performance_and_profiling.md` §1 JIT sentence and the §6 pyperformance table (add 3.14, 3.15).
3. Rewrite `README.md` §4 Version Matrix (add all 3.14 and 3.15 rows), fix "Primary language 3.11/3.12"
   in three places (L7, L19, L380), and **rewrite the §7 Conventions Reminder block** — it currently
   instructs future agents to write ASCII-only diagrams, 700-1000 lines, 10+ Q&As, `**Q1:**` labels and
   `README.md` pages, all of which contradict root `CLAUDE.md`.
4. Bump the uv pin in `packaging_and_project_tooling.md` L1013; fix the L87 "CPython 3.11" pin in
   `collections_and_data_structures.md`.

*Four independent files — run in parallel.*

### Wave 1 — P0 content (5.5 sessions, parallelisable 3-wide)

5. `the_import_system/the_import_system.md` — new module (1.0)
6. `c_extensions_and_native_interop/…` — new module (1.0)
7. `performance_and_profiling/the_modern_cpython_runtime.md` — new sub-file (0.8)
8. `case_studies/design_a_cpu_bound_batch_pipeline.md` (0.9) — **depends on Wave 0 item 1**
9. `case_studies/design_a_validation_library.md` (0.9)
10. `case_studies/design_a_plugin_system.md` (0.9) — **depends on item 5**
11. `case_studies/case_studies.md` index (0.2) — **must land in the same commit as the first case study**

Items 5, 6, 7, 9 are fully independent.

### Wave 2 — P1 content (5.5 sessions, parallelisable 3-wide)

12. `dataclasses_enums_and_data_modeling/…` — new module (1.0)
13. `packaging_and_project_tooling/supply_chain_and_publishing.md` (0.8)
14. `the_type_system_and_typing/type_checkers_and_adoption.md` (0.8)
15. `the_gil_and_free_threading/subinterpreters_and_interpreter_pools.md` (0.8)
16. `case_studies/cross_cutting/choosing_a_concurrency_model.md` (0.7)
17. `case_studies/cross_cutting/benchmarking_python_correctly.md` (0.7)
18. `case_studies/design_a_streaming_etl.md` (0.8)
19. `case_studies/design_a_high_throughput_logging_pipeline.md` (0.8)

All independent; 15 and 16 should be written by the same agent for consistency.

### Wave 3 — In-place depth patches (1.5 sessions, one agent, sequential)

20. The §4 table worked top to bottom: positional-only/keyword-only params, `singledispatch`,
    `itertools.batched`, t-strings + Unicode normalization, PEP 765 + `add_note`, `Path.copy`/`Path.move`
    + `tomllib` + pickle-5 buffers, `QueueHandler` + `datetime.UTC`, `--cov-branch` + testcontainers +
    `time-machine`, and the `from __future__ import annotations` / PEP 649 correction in three Phase-1
    files. ~1,100 lines of edits across 9 files.

### Wave 4 — Reordering + wiring (0.5 sessions, must be last)

21. Apply the tier changes and the new `STUDY_ORDER["python"]` (§3).
22. `case_studies/design_a_memory_efficient_cache.md` (P2, 0.8) — optional; drop it if §5's overlap
    argument is not resolved.

### Files that MUST be updated alongside content

- **`game/app.js` → `STUDY_ORDER["python"]`** — 3 new module dirs + the reordering. A module absent from
  the array falls to the 9999 sort and fails `extract.py --strict`.
- **`python/README.md` → the `<!-- study-paths -->` block** — every new module page, every new sub-file,
  and every case study needs a line **in the same commit**; a file on disk missing from the block is fatal
  both directions. Then `python3 game/extract.py --write-paths` to regenerate the tier tables (never
  hand-edit them).
- **`python/README.md`** — module table, the 3-phase Mermaid (now 4 phases), Knowledge-Question Map,
  Study Plan, Version Matrix, Cross-Reference Map, and the §7 tracker.
- **`python/CLAUDE.md`** — module list 21 → 24, sub-file list 3 → 7, and a "Relationship to case studies"
  note: the file currently states "This section has no case studies" in two places.
- **Root `README.md`** — the Python phase tables (3 new module rows + a new case-study table).
- **Root `CLAUDE.md`** — the Python row currently reads "21 modules, 0 case studies".
- Verify with `python3 game/extract.py --strict`, then the whole `scripts/build_banks.sh` — `--strict`
  passing does not mean CI passes.

**Total: ~13.5 agent-sessions** (Wave 0: 0.5 · Wave 1: 5.5 · Wave 2: 5.5 · Wave 3: 1.5 · Wave 4: 0.5),
producing 9 new content files plus ~4,700 lines of case studies and ~1,100 lines of in-place patches.
Stopping after Wave 1 still closes the two headline gaps (zero case studies, zero import-system coverage)
and every verified correction.

---

## 7. Confidence and gaps

**High confidence.** Every §2 correction was web-verified against a primary source (PEP text, CPython
whatsnew, or PyPI docs) *and* re-checked against the file on disk by me — I did not take the profiling
subagents' word for any of them. All three subagent reports arrived with a note that the safety classifier
was rate-limited during their review, so I independently re-grepped the load-bearing claims:
`threading_and_multiprocessing.md` L13, the `performance_and_profiling` JIT lines, and zero-hit
confirmations for t-strings, `Path.copy`/`Path.move`, PEP 765, `singledispatch`, `itertools.batched`,
`removeprefix`, `unicodedata.normalize`, positional-only params, `sys.monitoring`, `scalene`, `ty`/`pyrefly`,
PEP 751, PEP 723, `cibuildwheel`, and trusted publishing. All confirmed.

**One subagent claim I softened.** Batch 2 reported PEP 705 `ReadOnly` as "zero hits"; grep returns 5, but
all five are unrelated identifiers (`ReadOnlyContainer`, `ReadOnlyBox` in the variance examples). The
substantive claim — PEP 705 `ReadOnly` for `TypedDict` is not taught — holds.

**What I could not verify.**
- **Whether the Q&As actually reach the MCQ bank.** I did not run `python3 game/extract.py` (it writes
  generated artefacts and this is a planning pass). Heading discipline looks correct — all 24 files use
  `## 12. Interview Questions with Answers` — and every parsed Q&A already has a `**Short:**`, so I expect
  no losses, but the per-section count is unconfirmed.
- **Mermaid render health.** I counted fences (4–9 per file) but did not run `scripts/check_render.mjs`.
  Given `build_diagrams.mjs` now fails the build on a parse error and the section is on main, they
  presumably render — but any new diagram authored in Waves 1–3 must be validated before commit.
- **Python 3.15 specifics are moving.** 3.15 is at rc1 (2026-08-04), rc2 due 2026-09-01, final 2026-10-01.
  The PEP 810 syntax and the PEP 799 `profiling` namespace should be re-verified against the *final*
  whatsnew before any content asserts them; until then tag them `[3.15]` and say "ships in 3.15" rather
  than writing them as current.
- **The 3.14 tail-call interpreter number.** Official docs say 3–5% geomean; earlier third-party claims of
  9–15% were later attributed to a compiler bug. Use 3–5% and note the Clang ≥19 / PGO build requirement,
  or the module inherits a known-bad benchmark.
- **Case-study demand signal.** I justified the case-study track structurally (every other in-scope section
  has one; `java/` at 33 modules has 8) rather than from a job-post corpus. If the owner wants a
  market-demand justification per study rather than a symmetry argument, that research is not done.
- **uv's exact current version.** Sourced 0.11.8 (2026-04-27) from a secondary summary, not from GitHub
  releases directly. Check before pinning it in a Dockerfile example.
