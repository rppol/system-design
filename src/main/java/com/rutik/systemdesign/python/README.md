# Python — Senior Engineer & Interview Prep Guide

FastAPI now has its own section: [../fastapi/](../fastapi/README.md).

A comprehensive, one-stop reference for mastering **Python** — from CPython internals and the GIL through asyncio and the type system, to the pure-Python ecosystem (stdlib depth, testing, packaging, tooling). Covers everything a senior Python software engineer is expected to know in technical interviews.

> **No runtime application** — all content is Markdown with executable-shaped Python 3.11/3.12 code blocks.

---

## 1. Section Overview

This section covers:

- **Pure Python internals** — object model (everything is an object, dunder methods, `__slots__`, MRO/C3 linearization), CPython memory management (reference counting, generational GC, arenas/pools/blocks), the GIL and free-threading (PEP 703), metaclasses and descriptors, the type system (`Protocol`, `TypeVar`, `ParamSpec`, PEP 695 generics)
- **Concurrency and async** — `threading`, `multiprocessing`, `concurrent.futures`; `asyncio` event loop internals, coroutines, `TaskGroup` (3.11), structured concurrency; blocking-in-async pitfalls, backpressure, retries
- **Python ecosystem** — `itertools`, `collections`, `functools`, `contextlib`; `pytest` + `hypothesis`; `pyproject.toml`, `uv`, `ruff`, `mypy`; design patterns in Pythonic idioms

**Primary language:** Python 3.11/3.12. Version tags mark features introduced in earlier or later versions.

---

## 2. Module Table

| # | Module Directory | Phase | Difficulty | Key Topics |
|---|-----------------|-------|-----------|-----------|
| 1 | [data_model_and_objects](data_model_and_objects/) | 1 — Language Core | Intermediate | Dunder methods, `__slots__`, MRO/C3, operator overloading, hashing/equality contract |
| 2 | [core_language_idioms](core_language_idioms/) | 1 — Language Core | Intermediate | Mutability vs identity, EAFP vs LBYL, comprehensions, walrus `:=` (3.8), `match` (3.10) |
| 3 | [iterators_and_generators](iterators_and_generators/) | 1 — Language Core | Intermediate | Iterator protocol, `yield`/`yield from`, lazy pipelines, `itertools`, generator coroutines |
| 4 | [decorators_and_closures](decorators_and_closures/) | 1 — Language Core | Intermediate | Closures, free variables, function/class/parametrized decorators, `functools.wraps/lru_cache/cached_property` |
| 5 | [context_managers_and_exceptions](context_managers_and_exceptions/) | 1 — Language Core | Intermediate | `contextlib`, `ExitStack`, async CMs, `ExceptionGroup`/`except*` (3.11), traceback manipulation |
| 6 | [collections_and_data_structures](collections_and_data_structures/) | 1 — Language Core | Intermediate | `list`/`dict`/`set` internals + Big-O, `collections` (deque/Counter/defaultdict), `heapq`, `bisect` |
| 7 | [strings_bytes_encoding_and_regex](strings_bytes_encoding_and_regex/) | 1 — Language Core | Intermediate | `str` vs `bytes`, Unicode/codecs, `memoryview`, `re` engine, catastrophic backtracking |
| 8 | [file_io_and_serialization](file_io_and_serialization/) | 1 — Language Core | Beginner | `pathlib`, text/binary I/O, `json`/`csv`, `pickle` security, `struct` |
| 9 | [cpython_memory_model](cpython_memory_model/) | 2 — CPython Internals | Advanced | Refcounting, generational GC, cyclic GC, `PyObject` header, arenas/pools/blocks, `sys.getsizeof` |
| 10 | [the_gil_and_free_threading](the_gil_and_free_threading/) | 2 — CPython Internals | Advanced | GIL mechanics, GIL release points, contention profiling, PEP 703 (3.13), PEP 684 sub-interpreters |
| 11 | [metaclasses_and_metaprogramming](metaclasses_and_metaprogramming/) | 2 — CPython Internals | Advanced | `type()`, metaclasses, `__init_subclass__`, `__set_name__`, descriptors (non-data/data), `__getattr__` |
| 12 | [the_type_system_and_typing](the_type_system_and_typing/) | 2 — CPython Internals | Advanced | Type hints, generics, `Protocol` (structural), `TypeVar`/`ParamSpec`, variance, PEP 695 (3.12), mypy/pyright |
| 13 | [performance_and_profiling](performance_and_profiling/) | 2 — CPython Internals | Advanced | `cProfile`/`line_profiler`/`dis`, CPython 3.11+ speedups, Cython/mypyc/C extensions, slow patterns |
| 14 | [functional_programming](functional_programming/) | 2 — CPython Internals | Intermediate | `map`/`filter`/`reduce`, `functools`/`operator`, immutability, currying/partial, comprehension vs generator perf |
| 15 | [threading_and_multiprocessing](threading_and_multiprocessing/) | 3 — Concurrency & Quality | Advanced | `threading`, GIL impact, `multiprocessing`, `concurrent.futures`, shared memory, pickling cost |
| 16 | [asyncio_and_event_loop](asyncio_and_event_loop/) | 3 — Concurrency & Quality | Advanced | Coroutines, event-loop internals, tasks/futures, `gather`/`wait`, `TaskGroup` (3.11), `anyio` |
| 17 | [async_patterns_and_pitfalls](async_patterns_and_pitfalls/) | 3 — Concurrency & Quality | Advanced | Blocking-in-async detection, `run_in_executor`, async generators, `Semaphore`, backpressure, retries |
| 18 | [design_patterns_in_python](design_patterns_in_python/) | 3 — Concurrency & Quality | Intermediate | Pythonic GoF (singleton/strategy/observer/factory), anti-patterns — cross-links `lld/` |
| 19 | [stdlib_datetime_and_logging](stdlib_datetime_and_logging/) | 3 — Concurrency & Quality | Intermediate | `datetime`/`zoneinfo`/tz pitfalls, structured `logging`, `argparse`, `subprocess`, `os`/`sys` |
| 20 | [testing_with_pytest](testing_with_pytest/) | 3 — Concurrency & Quality | Intermediate | pytest, fixtures/scopes, `parametrize`, `monkeypatch`, `hypothesis`, coverage, `pytest-asyncio` |
| 21 | [packaging_and_project_tooling](packaging_and_project_tooling/) | 3 — Concurrency & Quality | Intermediate | `pyproject.toml`, `uv`/poetry/pip, venv, wheels/sdist, `ruff`/mypy, semantic versioning |

---

## 3. 3-Phase Learning Path

```
Phase 1 — Language Core & Data Model (8 modules)
+------------------------------------------------------------+
|  data_model_and_objects       core_language_idioms         |
|  iterators_and_generators     decorators_and_closures      |
|  context_managers_and_exceptions                           |
|  collections_and_data_structures                           |
|  strings_bytes_encoding_and_regex                          |
|  file_io_and_serialization                                 |
+------------------------------------------------------------+
                               |
                               v
Phase 2 — CPython Internals & Type System (6 modules)
+------------------------------------------------------------+
|  cpython_memory_model         the_gil_and_free_threading   |
|  metaclasses_and_metaprogramming                           |
|  the_type_system_and_typing   performance_and_profiling    |
|  functional_programming                                    |
+------------------------------------------------------------+
                               |
                               v
Phase 3 — Concurrency, Async & Quality (7 modules)
+------------------------------+  +--------------------------+
|  threading_and_              |  |  design_patterns_in      |
|  multiprocessing             |  |  python                  |
|  asyncio_and_event_loop      |  |  stdlib_datetime_and     |
|  async_patterns_and_pitfalls |  |  logging                 |
|                              |  |  testing_with_pytest     |
|                              |  |  packaging_and_project   |
|                              |  |  tooling                 |
+------------------------------+  +--------------------------+
```

**Dependencies to note:**
- Phase 3 `asyncio` modules require Phase 1 generators/iterators (coroutines are generators under the hood).

---

## Learning Paths

This section is exhaustive by design — 21 modules spanning pure Python internals, concurrency, and the broader Python ecosystem. That is the right depth for a reference and the wrong shape for someone two weeks from an interview. So there are **two ways through it**; the browser learning game's **Study** view surfaces both as a **Full / Interview** toggle (Full is the default).

### Full Path (21 modules)

The complete curriculum in the order above — see [3-Phase Learning Path](#3-3-phase-learning-path). Use it for genuine mastery: deep CPython internals (metaclasses, descriptors, packaging/tooling, `dis`/profiling), the full concurrency toolkit (`threading`/`multiprocessing` alongside `asyncio`), stdlib depth (strings/bytes/regex, file I/O, datetime/logging), and Pythonic design patterns. Nothing is dropped.

### Interview-Specific Path (13 modules)

A ruthless cut to what a **senior Python interview** actually probes, anchored on the modules this section already flags as highest-yield (language core, the GIL, and asyncio). Same learning order, ~38% fewer modules. Each group below says why it earns interview time.

| Group | Modules | Why it's tested |
|-------|---------|-----------------|
| Language Core & Data Model | [data_model_and_objects](data_model_and_objects/), [core_language_idioms](core_language_idioms/), [iterators_and_generators](iterators_and_generators/), [decorators_and_closures](decorators_and_closures/), [context_managers_and_exceptions](context_managers_and_exceptions/), [collections_and_data_structures](collections_and_data_structures/) | Dunder methods, `__slots__`, MRO/C3, EAFP vs LBYL, the iterator protocol, closures, `ExceptionGroup`/`except*`, and dict/list Big-O — the fundamentals nearly every Python interview opens with, screen or onsite |
| CPython Internals & Type System | [cpython_memory_model](cpython_memory_model/), [the_gil_and_free_threading](the_gil_and_free_threading/), [the_type_system_and_typing](the_type_system_and_typing/), [functional_programming](functional_programming/) | Reference counting vs generational GC, the GIL's release points and PEP 703 free-threading, `Protocol`/`TypeVar`/variance, and functional idioms — the internals that separate mid from senior |
| Concurrency, Async & Testing | [asyncio_and_event_loop](asyncio_and_event_loop/), [async_patterns_and_pitfalls](async_patterns_and_pitfalls/), [testing_with_pytest](testing_with_pytest/) | Event-loop internals, `TaskGroup` structured concurrency, blocking-in-async detection, and pytest fixtures/mocking — proof you can write and verify correct async code, not just describe it |

**Deliberately deferred to the Full Path** (valuable, lower interview yield): deep CPython/tooling internals (metaclasses & descriptors, packaging & project tooling, performance profiling), string/bytes/regex internals, file I/O & serialization, `threading`/`multiprocessing` (asyncio dominates the async story above), Pythonic design patterns, and stdlib datetime & logging. A niche flagged in an interview is a bonus, not a gate — reach for these once the 13 above are solid.

### Decoding the path arithmetic

**What it means.** "The interview path is not a different curriculum — it is the same ordered list with 8 of its 21 entries removed, which is why the subset must stay in the full path's order."

This section has no formulas to speak of; the only arithmetic worth stating explicitly is how the two paths relate, because that relationship is a maintenance constraint, not just a description.

| Symbol | What it is |
|--------|------------|
| Full Path | All **21** modules, in the 3-Phase order above |
| Interview Path | An ordered **subset** of 13 of those same modules — never a re-ordering |
| Deferred | `21 - 13 = 8` modules that stay in the Full Path only |
| `STUDY_ORDER["python"]` | The game's canonical module order; the Full Path must match it |
| `STUDY_PATHS.python.interview` | The game's 13-module subset; this README's table is its twin |

**Walk one example.** Reconcile every count stated on this page:

```
  Full Path, by phase
    Phase 1  Language Core & Data Model        8 modules
    Phase 2  CPython Internals & Type System   6 modules
    Phase 3  Concurrency, Async & Quality      7 modules
                                              -- total 21   matches the module table

  Interview Path, by group
    Language Core & Data Model                 6 modules
    CPython Internals & Type System            4 modules
    Concurrency, Async & Testing               3 modules
                                              -- total 13   matches the 3-week plan
                                                            (weeks of 6, 4, 3)

  reduction
    deferred     = 21 - 13 = 8 modules
    "~38% fewer" =  8 / 21 = 38.1 %           the page's stated figure checks out
    coverage     = 13 / 21 = 61.9 % of the section
```

Both stated totals and the `~38%` figure are internally consistent. The number that matters operationally is the `8`: those modules are dropped, never reordered, which is what keeps the interview list a valid *ordered subset*. If a future edit reorders the interview table to group topics differently, the game's Study toggle breaks its subset invariant even though all 13 modules are still present — the count would still read 13 while the ordering check fails.

---

## Knowledge-Question Map

The highest-frequency Python *knowledge* questions mapped to the file that answers them.

| Interview question | Where the answer lives |
|--------------------|------------------------|
| How does Python's attribute lookup work (MRO, descriptors, data vs non-data), and when do you actually need `__slots__`? | [data_model_and_objects](data_model_and_objects/) |
| EAFP vs LBYL; comprehensions vs `map`/`filter`/`reduce` — which is idiomatic, and why? | [core_language_idioms](core_language_idioms/), [functional_programming](functional_programming/) |
| How do `yield`/`yield from` work, and what distinguishes an iterator from an iterable? | [iterators_and_generators](iterators_and_generators/) |
| What is a closure, and how does late binding cause the classic loop-variable bug? | [decorators_and_closures](decorators_and_closures/) |
| What changed with `ExceptionGroup`/`except*` in Python 3.11? | [context_managers_and_exceptions](context_managers_and_exceptions/) |
| What's the time complexity of common `dict`/`list` operations, and how does a dict resize under the hood? | [collections_and_data_structures](collections_and_data_structures/) |
| Explain CPython reference counting — when does the cyclic garbage collector have to step in? | [cpython_memory_model](cpython_memory_model/) |
| When exactly is the GIL released, and how does PEP 703 free-threading change that model? | [the_gil_and_free_threading](the_gil_and_free_threading/) |
| What is a `Protocol`, and how does structural typing differ from nominal typing? | [the_type_system_and_typing](the_type_system_and_typing/) |
| Explain the async/await execution model, and what does `TaskGroup` (3.11) improve over `gather`? | [asyncio_and_event_loop](asyncio_and_event_loop/) |
| What happens if you call a blocking call like `requests.get()` inside `async def`? | [async_patterns_and_pitfalls](async_patterns_and_pitfalls/) |
| How do you mock an async dependency and assert on it with pytest? | [testing_with_pytest](testing_with_pytest/) |

---

## Study Plan

A 3-week plan over the Interview-Specific Path.

| Week | Focus | Modules |
|------|-------|---------|
| 1 | Language Core & Data Model | [data_model_and_objects](data_model_and_objects/), [core_language_idioms](core_language_idioms/), [iterators_and_generators](iterators_and_generators/), [decorators_and_closures](decorators_and_closures/), [context_managers_and_exceptions](context_managers_and_exceptions/), [collections_and_data_structures](collections_and_data_structures/) |
| 2 | CPython Internals & Type System | [cpython_memory_model](cpython_memory_model/), [the_gil_and_free_threading](the_gil_and_free_threading/), [the_type_system_and_typing](the_type_system_and_typing/), [functional_programming](functional_programming/) |
| 3 | Concurrency, Async & Testing + drills | [asyncio_and_event_loop](asyncio_and_event_loop/), [async_patterns_and_pitfalls](async_patterns_and_pitfalls/), [testing_with_pytest](testing_with_pytest/) |

---

## 4. Python Version Matrix

| Feature | Version | Notes |
|---------|---------|-------|
| f-strings | 3.6 | Widely deployed baseline |
| `dataclasses` | 3.7 | `@dataclass`, `field()` |
| `asyncio.run()` | 3.7 | Stable public entry point |
| walrus operator `:=` | 3.8 | PEP 572, assignment expressions |
| Positional-only params `/` | 3.8 | PEP 570 |
| `TypedDict` | 3.8 | `typing.TypedDict` |
| `Union[X, Y]` as `X \| Y` | 3.10 | PEP 604 |
| `ParamSpec`, `Concatenate` | 3.10 | PEP 612 |
| `match`/`case` structural pattern matching | 3.10 | PEP 634 |
| `typing.TypeAlias` | 3.10 | PEP 613 |
| `tomllib` (stdlib TOML reader) | 3.11 | PEP 680 |
| Exception Groups + `except*` | 3.11 | PEP 654 |
| `asyncio.TaskGroup` | 3.11 | PEP 654, structured concurrency |
| `typing.Self` | 3.11 | PEP 673 |
| `typing.LiteralString` | 3.11 | PEP 675, SQL injection safety |
| CPython 3.11 speedup | 3.11 | ~25% faster than 3.10 (specializing adaptive interpreter, faster frame eval) |
| `typing.TypeVarTuple` | 3.11 | PEP 646, variadic generics |
| PEP 695 generics `type X[T] = ...` | 3.12 | New type alias syntax, inline TypeVar |
| `@override` decorator | 3.12 | PEP 698 |
| `asyncio.eager_task_factory` | 3.12 | Reduce event-loop overhead for short coroutines |
| Sub-interpreters (stable C API) | 3.12 | PEP 684 |
| Free-threading GIL-optional (`python3.13t`) | 3.13 | PEP 703, experimental |
| JIT compiler (copy-and-patch) | 3.13 | Experimental, ~5% speedup in benchmarks |

---

## 5. Top Interview Topics by Category

### Python Object Model & Internals
1. **How does Python's attribute lookup work?** Python checks (in order): data descriptors on the class/MRO, then instance `__dict__`, then non-data descriptors and class attributes. `property` is a data descriptor (has `__set__`), so it always intercepts instance attribute access.
2. **Explain `__slots__` and when to use it.** `__slots__` replaces per-instance `__dict__` with a compact C-level array. Saves 50–200 bytes per instance, speeds attribute access, prevents accidental attribute creation. Use when creating millions of instances; trade-off is loss of dynamic attributes.
3. **What is C3 linearization?** The MRO algorithm ensuring consistency in multiple inheritance. It respects local precedence order and monotonicity. `ClassName.__mro__` shows the resolution order; `super()` follows it cooperatively.
4. **What is a descriptor?** An object implementing `__get__`/`__set__`/`__delete__`. Python's attribute machinery calls `__get__` on class attribute access. `property`, `classmethod`, `staticmethod` are all implemented as descriptors.
5. **How does Python's `__hash__` contract work?** Objects that compare equal (`__eq__`) must have the same hash. If you override `__eq__`, Python sets `__hash__ = None` (unhashable) unless you also define `__hash__`. Mutable objects should not be hashable.

### GIL, Concurrency & asyncio
1. **When is the GIL released?** CPython releases the GIL every `sys.getswitchinterval()` (default 5 ms) of bytecode execution, and on every blocking I/O system call (socket, file, `time.sleep`). Pure CPU-bound Python bytecode holds the GIL continuously.
2. **When does multi-threading help despite the GIL?** I/O-bound workloads. While one thread blocks on I/O, the GIL is released and other threads can run. For CPU-bound work, use `multiprocessing` (separate processes = separate GILs).
3. **Explain the async/await execution model.** `async def` defines a coroutine. `await expr` suspends the coroutine, yielding control to the event loop, which uses `select()`/`epoll`/`kqueue` to multiplex I/O readiness. No OS threads are involved; a single thread executes all coroutines interleaved.
4. **What does `asyncio.TaskGroup` improve over `gather`?** TaskGroup (3.11, PEP 654) provides structured concurrency: if any child task raises, remaining tasks are cancelled immediately, and ALL exceptions are collected into an `ExceptionGroup`. `gather` propagates only the first exception by default, leaving others silently swallowed.
5. **What happens if you call `requests.get()` inside `async def`?** It blocks the event loop thread for the entire network round-trip — all other coroutines stall. Fix: use `httpx.AsyncClient` or `await asyncio.get_event_loop().run_in_executor(None, requests.get, url)`.

---

## 6. Cross-Reference Map

| Module | Also See |
|--------|---------|
| `data_model_and_objects` | [`lld/behavioral/`](../lld/behavioral/) — observer/strategy as Python callables |
| `the_gil_and_free_threading` | [`java/concurrency/`](../java/concurrency/) — compare JVM threading vs CPython GIL |
| `asyncio_and_event_loop` | [`backend/async_and_concurrency_patterns/`](../backend/async_and_concurrency_patterns/); [`llm/case_studies/cross_cutting/streaming_at_scale.md`](../llm/case_studies/cross_cutting/streaming_at_scale.md) |
| `the_type_system_and_typing` | [`java/generics_and_type_system/`](../java/generics_and_type_system/) — erasure vs Python runtime generics |
| `design_patterns_in_python` | [`lld/creational/`](../lld/creational/); [`lld/structural/`](../lld/structural/); [`lld/behavioral/`](../lld/behavioral/) |

---

## 7. Build Status & Implementation Tracker

> **ALL BATCHES COMPLETE — Section fully built.** 21 modules + 3 sub-files. No pending work. (FastAPI's former modules, case studies, and sub-files were split into [../fastapi/](../fastapi/README.md) on 2026-07-07.)

### Batch Plan

| Batch | Files | Status |
|-------|-------|--------|
| **Batch 1 — Core** | `data_model_and_objects`, `the_gil_and_free_threading`, `asyncio_and_event_loop`, `decorators_and_closures`, `the_type_system_and_typing` | done |
| **Batch 2 — Phase 1 finish** | `core_language_idioms`, `iterators_and_generators`, `context_managers_and_exceptions`, `collections_and_data_structures`, `strings_bytes_encoding_and_regex`, `file_io_and_serialization` | done |
| **Batch 3 — Phase 2 finish** | `cpython_memory_model`, `metaclasses_and_metaprogramming`, `performance_and_profiling`, `functional_programming` | done |
| **Batch 4 — Phase 3 finish** | `threading_and_multiprocessing`, `async_patterns_and_pitfalls`, `design_patterns_in_python`, `stdlib_datetime_and_logging`, `testing_with_pytest`, `packaging_and_project_tooling` | done |
| **Batch 5 — Sub-files** | `asyncio_and_event_loop/event_loop_internals.md`, `asyncio_and_event_loop/structured_concurrency.md`, `the_type_system_and_typing/protocols_and_structural_typing.md` | done |

### All Files Status

| # | Module / File | Phase | Batch | Status | Q&A Target |
|---|--------------|-------|-------|--------|-----------|
| 1 | `data_model_and_objects/README.md` | 1 | 1 | done | 15+ |
| 2 | `core_language_idioms/README.md` | 1 | 2 | done | 12+ |
| 3 | `iterators_and_generators/README.md` | 1 | 2 | done | 12+ |
| 4 | `decorators_and_closures/README.md` | 1 | 1 | done | 15+ |
| 5 | `context_managers_and_exceptions/README.md` | 1 | 2 | done | 12+ |
| 6 | `collections_and_data_structures/README.md` | 1 | 2 | done | 12+ |
| 7 | `strings_bytes_encoding_and_regex/README.md` | 1 | 2 | done | 10+ |
| 8 | `file_io_and_serialization/README.md` | 1 | 2 | done | 10+ |
| 9 | `cpython_memory_model/README.md` | 2 | 3 | done | 15+ |
| 10 | `the_gil_and_free_threading/README.md` | 2 | 1 | done | 18+ |
| 11 | `metaclasses_and_metaprogramming/README.md` | 2 | 3 | done | 15+ |
| 12 | `the_type_system_and_typing/README.md` | 2 | 1 | done | 15+ |
| 13 | `performance_and_profiling/README.md` | 2 | 3 | done | 12+ |
| 14 | `functional_programming/README.md` | 2 | 3 | done | 10+ |
| 15 | `threading_and_multiprocessing/README.md` | 3 | 4 | done | 15+ |
| 16 | `asyncio_and_event_loop/README.md` | 3 | 1 | done | 18+ |
| 17 | `async_patterns_and_pitfalls/README.md` | 3 | 4 | done | 15+ |
| 18 | `design_patterns_in_python/README.md` | 3 | 4 | done | 12+ |
| 19 | `stdlib_datetime_and_logging/README.md` | 3 | 4 | done | 10+ |
| 20 | `testing_with_pytest/README.md` | 3 | 4 | done | 12+ |
| 21 | `packaging_and_project_tooling/README.md` | 3 | 4 | done | 10+ |
| SF1 | `asyncio_and_event_loop/event_loop_internals.md` | 3 | 5 | done | 15+ |
| SF2 | `asyncio_and_event_loop/structured_concurrency.md` | 3 | 5 | done | 15+ |
| SF3 | `the_type_system_and_typing/protocols_and_structural_typing.md` | 2 | 5 | done | 15+ |

### Conventions Reminder (for future batch agents)

```
MODULE TEMPLATE — 14-section canonical clean scheme:
  ## 1. Concept Overview
  ## 2. Intuition     ("> blockquote analogy" + **Mental model** + **Why it matters** + **Key insight**)
  ## 3. Core Principles
  ## 4. Types / Architectures / Strategies
  ## 5. Architecture Diagrams            (ASCII art only — no Mermaid, no image files)
  ## 6. How It Works — Detailed Mechanics   (real Python code, concrete numbers)
  ## 7. Real-World Examples
  ## 8. Tradeoffs                        (comparison tables)
  ## 9. When to Use / When NOT to Use
  ## 10. Common Pitfalls                 (# BROKEN / # FIX pattern, at least 1 required)
  ## 11. Technologies & Tools            (comparison table)
  ## 12. Interview Questions with Answers  (bold Q as "**Q1:**", plain text A; Q&A targets below)
  ## 13. Best Practices
  ## 14. Case Study   (scenario + ASCII diagram + real code + BROKEN/FIX + metrics + Discussion Qs)

QUALITY BAR:
  - 700-1000 lines per module README
  - Q&A minimum: 10+ standard; 15-18 for:
      the_gil_and_free_threading, asyncio_and_event_loop, the_type_system_and_typing,
      threading_and_multiprocessing
  - At least 1 BROKEN->FIX block in §10 and at least 1 in §14
  - Python 3.11/3.12 primary; mark new features with "[3.X]" version tags inline
  - Type hints on ALL function signatures (use X | None not Optional[X])
  - Concrete numbers everywhere (no "a few", "some", "significant")
  - ASCII diagrams in fenced code blocks
  - Horizontal rules (---) between every top-level section
  - Em-dash in §6 heading: "## 6. How It Works — Detailed Mechanics"
  - No emojis in any content
  - Cross-link to other sections via relative paths: ../backend/..., ../lld/..., etc.
    (python/README.md sits one level under the section root, so a cross-section
    link needs exactly one ../ — not ../../)

MAINTENANCE RULE when completing a batch:
  1. Flip Status "pending" -> "done" for each completed file in this tracker
  2. Update root README.md module counts if the total changed
  3. Update CLAUDE.md Python section module list if needed
```

---

## Getting Started

**Week 1 — Python Internals Foundation** (highest interview signal-to-noise)
- `data_model_and_objects` — covers ~30% of Python interview questions on its own
- `decorators_and_closures` — appears in almost every senior Python interview
- `the_gil_and_free_threading` — key differentiator for principal-level interviews

**Week 2 — Async and Type System** (the hard ones)
- `asyncio_and_event_loop` — deepest async module; study alongside `async_patterns_and_pitfalls`
- `the_type_system_and_typing` — `Protocol`, `TypeVar`, `ParamSpec` tested at FAANG+
- `cpython_memory_model` + `metaclasses_and_metaprogramming` for deep CPython questions

Once these are solid, move on to [../fastapi/](../fastapi/README.md) for the framework layer (ASGI, Pydantic v2, dependency injection) and its production concerns.

See individual module READMEs for per-module learning objectives and cross-references.
