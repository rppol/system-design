# Python Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/python/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §7 — check Batch Plan before starting a new module.

---

## Module List — 24 Modules

| Module Directory | Phase | Key Concepts |
|-----------------|-------|-------------|
| `data_model_and_objects/` | 1 | Dunder methods, `__slots__`, MRO/C3 linearization, operator overloading, hashing/equality contract |
| `core_language_idioms/` | 1 | Mutability vs identity, EAFP vs LBYL, comprehensions, walrus `:=` (3.8), `match`/`case` (3.10) |
| `the_import_system/` | Finders, loaders, `sys.path`, namespace packages, circular imports, import-time side effects | Mutability vs identity, EAFP vs LBYL, comprehensions, walrus `:=` (3.8), `match`/`case` (3.10) |
| `iterators_and_generators/` | 1 | Iterator protocol, `yield`/`yield from`, lazy pipelines, `itertools`, generator coroutines |
| `decorators_and_closures/` | 1 | Closures, free variables, function/class/parametrized decorators, `functools.wraps/lru_cache/cached_property` |
| `context_managers_and_exceptions/` | 1 | `contextlib`, `ExitStack`, async CMs, `ExceptionGroup`/`except*` (3.11) |
| `collections_and_data_structures/` | 1 | `list`/`dict`/`set` internals + Big-O, `collections`, `heapq`, `bisect` |
| `dataclasses_enums_and_data_modeling/` | `dataclass` fields, `slots`, frozen instances, `Enum`/`StrEnum`, and when to reach for Pydantic | `list`/`dict`/`set` internals + Big-O, `collections`, `heapq`, `bisect` |
| `strings_bytes_encoding_and_regex/` | 1 | `str` vs `bytes`, Unicode, `re` engine, catastrophic backtracking |
| `file_io_and_serialization/` | 1 | `pathlib`, text/binary I/O, `json`/`csv`, `pickle` security |
| `cpython_memory_model/` | 2 | Reference counting, generational GC, `PyObject` header, arenas/pools/blocks |
| `the_gil_and_free_threading/` | 2 | GIL mechanics, GIL release points, PEP 703/779 free-threading, PEP 684/734 sub-interpreters |
| `metaclasses_and_metaprogramming/` | 2 | `type()`, metaclasses, `__init_subclass__`, `__set_name__`, descriptors, the import system and `MetaPathFinder` |
| `the_type_system_and_typing/` | 2 | Type hints, generics, `Protocol`, `TypeVar`/`ParamSpec`, variance, PEP 695, mypy/pyright/pyrefly/ty |
| `performance_and_profiling/` | 2 | `cProfile`/`dis`/`sys.monitoring`, CPython 3.11+ speedups, the JIT and tail-call interpreter, Cython/mypyc/PyO3 |
| `c_extensions_and_native_interop/` | CPython C API, `ctypes`/`cffi`, Cython, PyO3, the GIL boundary, packaging native wheels | `cProfile`/`dis`/`sys.monitoring`, CPython 3.11+ speedups, the JIT and tail-call interpreter, Cython/mypyc/PyO3 |
| `functional_programming/` | 2 | `map`/`filter`/`reduce`, `functools`, immutability, currying, comprehension vs generator perf |
| `threading_and_multiprocessing/` | 3 | `threading`, GIL impact, `multiprocessing`, `concurrent.futures`, shared memory |
| `asyncio_and_event_loop/` | 3 | Coroutines, event-loop internals, `gather`/`wait`, `TaskGroup` (3.11), `anyio` |
| `async_patterns_and_pitfalls/` | 3 | Blocking-in-async, `run_in_executor`, async generators, `Semaphore`, backpressure |
| `design_patterns_in_python/` | 3 | Pythonic GoF patterns, anti-patterns — cross-links `lld/` |
| `stdlib_datetime_and_logging/` | 3 | `datetime`/`zoneinfo`, structured logging, `argparse`, `subprocess` |
| `testing_with_pytest/` | 3 | pytest, fixtures, `parametrize`, `monkeypatch`, `hypothesis`, `pytest-asyncio` |
| `packaging_and_project_tooling/` | 3 | `pyproject.toml`, `uv`/poetry/pip, `ruff`/mypy, wheels, dependency resolution |

---

## Sub-Files (4 total — 14-section template, 15+ Q&As each)

| File | Parent Module |
|------|--------------|
| `asyncio_and_event_loop/event_loop_internals.md` | asyncio_and_event_loop/ |
| `asyncio_and_event_loop/structured_concurrency.md` | asyncio_and_event_loop/ |
| `the_type_system_and_typing/protocols_and_structural_typing.md` | the_type_system_and_typing/ |

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 21 modules = "3-Phase Learning Path") plus
two curated tiers: **Senior** (16 modules) and **Principal** (7). They are different
cuts, not nested depths — senior is the craft, principal is the judgment (which runtime
model at what cost, what you tell a team *not* to do), so principal is the far smaller
list here and much of it is material senior never sees. Membership is declared ONCE per section, the `<!-- study-paths -->` block in this section's `README.md`, naming every module, every file it owns, and the tiers each file
each tier takes; listing a tier joins it, omitting the tier opts out, and the module page
(`<module>.md`) must always be listed. Order is never declared — it comes from `STUDY_ORDER.python` in
`game/app.js`, so a tier is an ordered subset by construction. **There is no path array
in `app.js` to edit**: `extract.py` walks the markers and emits the gitignored
`questions/paths.json`, which the game fetches at boot. The tier tables in `README.md`
sit between `<!-- study-path-table <tier> -->` markers and are **generated** — regenerate
with `python3 game/extract.py --write-paths`; a hand-edited or stale block fails
`extract.py --strict` and the Pages deploy. This section has no case studies, so there is
no Case Studies track. The README also carries a Knowledge-Question Map and a 3-week
Study Plan (prose; no path impact).

---

## Relationship to fastapi/

FastAPI (previously modules 22-40 of this section, back when it was "Python + FastAPI")
is now its own section — see [`../fastapi/CLAUDE.md`](../fastapi/CLAUDE.md) and
[`../fastapi/README.md`](../fastapi/README.md). This section has 0 case studies of its
own; all 6 former case studies moved to `fastapi/case_studies/`.

---

## Python Version Tags

When covering a feature, include the version it was introduced:
- Python 3.8: walrus operator `:=`, `TypedDict`, `Literal`
- Python 3.9: `list[int]` built-in generics, `dict | dict` merge operators
- Python 3.10: `match`/`case`, `X | Y` union type syntax, `ParamSpec`
- Python 3.11: `TaskGroup`, `ExceptionGroup`/`except*`, 10–60% performance gains, `tomllib`
- Python 3.12: `type` statement (PEP 695), `@override`, `f-string` improvements
- Python 3.13: free-threading build introduced (PEP 703, experimental), experimental copy-and-patch JIT (PEP 744), `ReadOnly` for TypedDict (PEP 705), `@deprecated` (PEP 702), TypeVar defaults (PEP 696), `Path.full_match()`
- Python 3.14: free-threading **officially supported** (PEP 779; single-thread overhead ~1% on macOS aarch64 to ~8% on x86-64 Linux), deferred annotation evaluation (PEP 649/749) + `annotationlib`, t-strings (PEP 750), `concurrent.interpreters` (PEP 734) + `InterpreterPoolExecutor`, `multiprocessing` default start method → `forkserver` on Unix other than macOS, `SyntaxWarning` for `return` in `finally` (PEP 765), tail-call interpreter (`--with-tail-call-interp`, Clang 19+, 3-5%), `Path.copy()`/`Path.move()`, `tarfile` extraction defaults to `filter='data'` (PEP 706), pickle protocol 5 becomes the default, `compression.zstd` (PEP 784), `sys.remote_exec` debugger attach (PEP 768)
- Python 3.15 (rc1 2026-08-04, final due 2026-10-01 — tag `[3.15]` and write it as "ships in 3.15", never as current): UTF-8 mode on by default (PEP 686), explicit lazy imports (PEP 810), stdlib `profiling` package (PEP 799)

Format: `[3.11]` inline or `Added in Python 3.11`.

**Primary target is Python 3.14.** Python 3.10 reaches end-of-life on 2026-10-31, so 3.11 is
the oldest version an example should target.

**On speedup claims:** CPython publishes an official cumulative pyperformance figure only for
**3.11 (25% faster than 3.10)**. There is no official cumulative geomean for 3.12 through
3.15 — every "3.13 is N% faster" index number in circulation is a third-party aggregation.
Attribute a speedup to what it was measured against, or do not state it.

## Q&A Minimums (Python-specific)

- Standard modules: **15+** Q&As — the hard floor in root `CLAUDE.md`
- Deep modules, **18+**: `the_gil_and_free_threading`, `asyncio_and_event_loop`,
  `the_type_system_and_typing`, `threading_and_multiprocessing`

**These are floors, not targets.** The section sat at 15-16 Q&As in 20 of 24 files because
`README.md` §7 advertised a "Q&A Target" of 10+/12+ — a number below the actual floor, which
then became the ceiling. That column now records measured counts instead. When you touch a
module, adding a Q&A for material you just introduced is expected.

## Adding a New Python Module

1. Create `<module_name>/<module_name>.md` — 14-section clean template
2. All code in Python 3.14 with type hints (`X | None`, never `Optional[X]`)
3. Add version tags for features introduced in specific Python versions
4. Meet the Q&A minimum for the module's depth level
5. Flip the module's Status from `pending` → `done` in `README.md` §7 tracker
6. Add the module dir to `STUDY_ORDER.python` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
7. Add the module and EVERY file it owns to the `<!-- study-paths -->` block in
   this section's `README.md`, tagging each file with the tiers it belongs to (`-` for
   Full path only; the module page must carry every tier the module is in). **Do NOT put
   a block in the module page — content files hold only content.** Then run
   `python3 game/extract.py --write-paths` to regenerate the README's tier tables
8. Update root `README.md` Phase table under the Python section
9. Update root `CLAUDE.md` Python module table

---

## Visual Intuition Diagrams

Diagrams are **appeal-first** (root `CLAUDE.md`): pick the form whose topology matches the
concept. In practice that means **Mermaid is the default here** — `flowchart LR` for directed
flows, `sequenceDiagram` for actor chains, `stateDiagram-v2` for lifecycles, `xychart-beta`
for magnitudes and trends. Every file in this section already carries 4-9 Mermaid fences.
Run `/mermaid-diagrams` before authoring one, colour every node with the One-Dark `classDef`
palette, keep labels short (wrap with `<br/>`), and use rounded shapes.

**A fence that does not parse FAILS THE BUILD.** Validate anything you touch:
`node scripts/check_render.mjs <absolute-file.md>` (exit 0 = every fence renders).

**ASCII remains** for the shapes Mermaid cannot draw — constraint grids and masks,
alignment-critical layout maps, and vector geometry. For those, use the
`/visual-intuition-diagrams` skill; its archetype catalog, conventions (no tabs, no emojis,
widest line <= 100 cols, caption every diagram) and the `diagram_tools.py` validator live in
root `CLAUDE.md` -> "Visual Intuition Diagrams".

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module, EVERY
file that module owns (the module page AND every deep-dive sub-file), and EVERY case
study, each tagged with the tiers it belongs to — `-` means Full path only. Reading that
one block tells you every file in the section and which paths it is on.

**A content file carries NO structural metadata.** A module page or a deep-dive sub-file
holds the content of its topic and nothing else — no `<!-- study-paths -->` block, no tier
declaration, no path membership. That metadata used to live in each module page; it was
moved here so there is one place to look and one place to change.

**Adding a file? Add its line to the section README's block in the same commit.** A file on
disk that is missing from the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red. That check exists
because the old failure was silent: a new sub-file was invisible to the curated paths, or
silently dragged into every tier its parent was in, with a green build either way.

Order is never declared in the block: it comes from `STUDY_ORDER` in `game/app.js`. The
tier TABLES further down the README are GENERATED from the block by
`python3 game/extract.py --write-paths` — never hand-edit them.
