# Python Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/python/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §7 — check Batch Plan before starting a new module.

---

## Module List — 21 Modules

| Module Directory | Phase | Key Concepts |
|-----------------|-------|-------------|
| `data_model_and_objects/` | 1 | Dunder methods, `__slots__`, MRO/C3 linearization, operator overloading, hashing/equality contract |
| `core_language_idioms/` | 1 | Mutability vs identity, EAFP vs LBYL, comprehensions, walrus `:=` (3.8), `match`/`case` (3.10) |
| `iterators_and_generators/` | 1 | Iterator protocol, `yield`/`yield from`, lazy pipelines, `itertools`, generator coroutines |
| `decorators_and_closures/` | 1 | Closures, free variables, function/class/parametrized decorators, `functools.wraps/lru_cache/cached_property` |
| `context_managers_and_exceptions/` | 1 | `contextlib`, `ExitStack`, async CMs, `ExceptionGroup`/`except*` (3.11) |
| `collections_and_data_structures/` | 1 | `list`/`dict`/`set` internals + Big-O, `collections`, `heapq`, `bisect` |
| `strings_bytes_encoding_and_regex/` | 1 | `str` vs `bytes`, Unicode, `re` engine, catastrophic backtracking |
| `file_io_and_serialization/` | 1 | `pathlib`, text/binary I/O, `json`/`csv`, `pickle` security |
| `cpython_memory_model/` | 2 | Reference counting, generational GC, `PyObject` header, arenas/pools/blocks |
| `the_gil_and_free_threading/` | 2 | GIL mechanics, GIL release points, PEP 703 free-threading (3.13), PEP 684 sub-interpreters |
| `metaclasses_and_metaprogramming/` | 2 | `type()`, metaclasses, `__init_subclass__`, `__set_name__`, descriptors |
| `the_type_system_and_typing/` | 2 | Type hints, generics, `Protocol`, `TypeVar`/`ParamSpec`, variance, PEP 695 (3.12), mypy |
| `performance_and_profiling/` | 2 | `cProfile`/`dis`, CPython 3.11+ speedups, Cython/mypyc, common slow patterns |
| `functional_programming/` | 2 | `map`/`filter`/`reduce`, `functools`, immutability, currying, comprehension vs generator perf |
| `threading_and_multiprocessing/` | 3 | `threading`, GIL impact, `multiprocessing`, `concurrent.futures`, shared memory |
| `asyncio_and_event_loop/` | 3 | Coroutines, event-loop internals, `gather`/`wait`, `TaskGroup` (3.11), `anyio` |
| `async_patterns_and_pitfalls/` | 3 | Blocking-in-async, `run_in_executor`, async generators, `Semaphore`, backpressure |
| `design_patterns_in_python/` | 3 | Pythonic GoF patterns, anti-patterns — cross-links `lld/` |
| `stdlib_datetime_and_logging/` | 3 | `datetime`/`zoneinfo`, structured logging, `argparse`, `subprocess` |
| `testing_with_pytest/` | 3 | pytest, fixtures, `parametrize`, `monkeypatch`, `hypothesis`, `pytest-asyncio` |
| `packaging_and_project_tooling/` | 3 | `pyproject.toml`, `uv`/poetry/pip, `ruff`/mypy, wheels, dependency resolution |

---

## Sub-Files (3 total — 14-section template, 15+ Q&As each)

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
- Python 3.13: free-threading build introduced (PEP 703), experimental JIT
- Python 3.14: free-threading **officially supported** (PEP 779, single-thread penalty down to 5–10%), deferred annotation evaluation (PEP 649/749) + `annotationlib`, t-strings (PEP 750), `concurrent.interpreters` (PEP 734) + `InterpreterPoolExecutor`, `compression.zstd` (PEP 784), `sys.remote_exec` debugger attach (PEP 768)

Format: `[3.11]` inline or `Added in Python 3.11`.

## Q&A Minimums (Python-specific)

- Standard modules: 15+ Q&As (the hard floor in root `CLAUDE.md`)
- Deep modules (15–18 required): `the_gil_and_free_threading`, `asyncio_and_event_loop`, `the_type_system_and_typing`

## Adding a New Python Module

1. Create `<module_name>/<module_name>.md` — 14-section clean template
2. All code in Python 3.13/3.14 with type hints (3.10+ syntax: `X | None` not `Optional[X]`)
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

Section 5 (Architecture Diagrams) and any hard-to-picture concept should use an
**ASCII visual intuition diagram** that makes an abstract relationship visible
(constraint grid, before/after-with-delta, stacked flow, routing fan-out, bar
chart, or curve/sketch). Generate and validate them with the
`/visual-intuition-diagrams` skill. The full archetype catalog, conventions
(ASCII only, no tabs, no emojis, widest line <= 100 cols, caption every diagram),
and the `diagram_tools.py` validator live in root `CLAUDE.md` -> "Visual Intuition
Diagrams".

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
