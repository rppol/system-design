# CS Fundamentals Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/cs_fundamentals/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §7 — check NEXT UP pointer and per-file status before starting a new module.

## Scope — unparked 2026-08-04; the factual audit is COMPLETE

Parked 2026-07-29, re-opened 2026-08-04, and audited the same day.

| Work | State |
|------|-------|
| Factual audit | **DONE — all 24 modules + all 26 `dsa_patterns` files**, ~2,500 claims verified, ~170 code blocks hand-executed, ~100 corrections. Commits `640ce22`, `b31a6b0`, `faf44a1`, `1923ffe` |
| `**Short:**` MCQ summaries | **DONE — 704 of 704**, run after the audit. The count rose from 701: the Case-B reflow split apart 3 questions that had been written on the same line as the END of the previous answer, where `is_question_line()` could not see them. Commit `6ce35a6` |
| Case studies | **not audited** (6 walkthrough case studies) |

### How this section audits — read before dispatching anything here

Agents were told to expect FEW corrections and to report zero honestly, because this is the
language-agnostic CS spine: Big-O is Big-O, the halting problem is still unsolved,
modernization is nearly irrelevant. **Ten of the fifty files came back with zero
corrections, and that is a result, not a gap** — do not re-audit them hunting for something.

What the pass actually found is that **almost every defect was in a DEMONSTRATION, not in a
claim.** The prose was overwhelmingly right; the worked examples, traces and "broken" code
frequently were not. That is the single most useful thing to know about this section:

- **Five "broken -> fixed" pairs whose BROKEN half was correct code**, three of them
  admitting it in their own comments ("Actually this IS a valid BST"). A reader learns a bug
  that is not one and misses the bug that is. `graph_and_string_algorithms`' KMP pitfall was
  byte-for-byte the correct algorithm, verified identical on 2,000 random patterns.
- **Two Dijkstra "negative weights fail" counterexamples that return the correct answer**
  when executed — the improved vertex had not been popped yet. A real one had to be
  constructed. Likewise a greedy-fails-knapsack counterexample where greedy finds the
  optimum, an exchange-argument walk whose "OPT" was not optimal, and a BROKEN float example
  that is not broken (`0.1 + 0.4 == 0.5` is `True`).
- **A teaching HashMap that duplicates keys** (probe stopped at a tombstone), a
  `critical_path` raising `NameError`, a lock-ordering "FIX" that self-deadlocks on
  non-reentrant locks, and a quicksort whose `hi=-1` sentinel restarts the whole sort.
- **A `4n` segment-tree illustration using n=5, which fits in 2n fine.** The smallest
  failing input is n=6, and the failure is NON-MONOTONIC — it breaks at 6 and 10 but not 5,
  7 or 9. You cannot find that by testing a few sizes.
- Statements the wrong way round: shift-by->=width called "Java: undefined" when it is C/C++
  that is undefined; knapsack called strongly NP-hard; the Bloom filter guarantee inverted;
  Clock listed as a stack algorithm; Master Theorem Case 3 without its regularity condition;
  arbitrage on `log(rate)` instead of `-log(rate)`.
- One Q&A shipped published thinking-out-loud debris: "— wait, let me recalculate: ...".

**The operational lesson: EXECUTE the code and RECOMPUTE the arithmetic.** Reading for
correctness passes almost everything here. The agents that found the most ran the snippets,
differential-tested against reference implementations, and checked 242 LeetCode links
number -> slug -> title -> difficulty against the live API (which is how two stale
difficulties surfaced).

Where currency DOES apply here, narrowly: named language/library behaviour and OS
internals. CFS -> EEVDF was corrected across four files; also cgroup v2 names,
free-threaded Python 3.14, 5-level paging, MGLRU, Apple Silicon's 128 B cache line and
16 KB pages, and `sys.setrecursionlimit` no longer segfaulting since CPython 3.11/3.12.

### Left open

`heaps_and_priority_queues` has an unnumbered `## Intuition` and duplicate `## 8`/`## 9`
titles; `graphs_tries_and_advanced_structures` has `## 9. Prerequisite Knowledge` where the
template's "When to Use" sits, shifting every later heading by one. Both were left untouched
under the no-restructure rule and want a deliberate decision. Also unverified rather than
guessed: a 250K-word trie at "~10 MB" (implies ~20 bytes/node) and a hash-set memory model
sizing a slot at 8 B where CPython's `setentry` is 16 B.

---

## Why This Section Exists

Every other section in this repo teaches CS concepts from a *language-specific* or *system-specific* angle: Java concurrency, Postgres B+Trees, JVM garbage collection, backend TCP deep-dives. Interviews assume the underlying computer-science *foundations* — asymptotic complexity, abstract data structures, operating-system primitives, computer architecture, cryptography theory — are already internalized.

This section is the **language-agnostic spine**: it teaches the concept at the CS-theory level, then crosslinks to the deep applied treatments elsewhere. The scope boundary (README §2) makes explicit what this section teaches versus what it delegates.

---

## Module List — 24 Modules (5 Phases)

Code examples use Python (type-hinted, runnable-shaped) as the default language for algorithms and data structures. Java is noted where JVM-specific behaviour matters. This section deliberately **cross-references** `java/`, `python/`, `backend/`, `database/`, `devops/`, `hld/`, and `lld/` instead of duplicating applied depth — see the non-overlap boundary in `README.md` §2.

| Phase | Modules |
|-------|---------|
| 1 — Complexity & Computation | complexity_analysis_and_big_o, discrete_math_for_engineers, number_systems_and_bit_manipulation, recursion_and_problem_solving_patterns |
| 2 — Data Structures | arrays_strings_and_hashing, linked_lists_stacks_and_queues, trees_and_binary_search_trees, heaps_and_priority_queues, graphs_tries_and_advanced_structures |
| 3 — Algorithms | sorting_and_searching, dynamic_programming, greedy_and_divide_and_conquer, graph_and_string_algorithms |
| 4 — Operating Systems | processes_threads_and_context_switching, cpu_scheduling_algorithms, memory_management_and_virtual_memory, deadlocks_and_synchronization |
| 5 — Systems & Security Foundations | computer_architecture_and_memory_hierarchy, networking_fundamentals, database_and_storage_fundamentals, cryptography_fundamentals, character_encoding_deep_dive, theory_of_computation, how_code_runs_compilers_and_interpreters |

**Deep modules requiring 18+ Q&As:** complexity_analysis_and_big_o, arrays_strings_and_hashing, trees_and_binary_search_trees, heaps_and_priority_queues, graphs_tries_and_advanced_structures, sorting_and_searching, dynamic_programming, graph_and_string_algorithms

**Modules requiring 15+ Q&As (standard deep bar):** all remaining modules

---

## Case Studies — 6 Total

`case_studies/` — all use the **adapted interview-problem walkthrough template** (11 sections):

```
## Intuition
## 1. Problem Statement & Clarifying Questions
## 2. Brute Force & Complexity Baseline
## 3. Optimal Approach & Key Insight
## 4. Implementation              (executable Python + one BROKEN -> FIX block)
## 5. Complexity Analysis & Tradeoffs
## 6. Variations & Follow-up Questions
## 7. Real-World Usage            (named systems/companies)
## 8. Edge Cases & Testing
## 9. Common Mistakes             (quantified war stories)
## 10. Related Problems
## 11. Interview Discussion Points  (10+ Q&As)
```

Quality bar: 900–1100 lines; executable Python in §4; BROKEN→FIX example in §4; named real systems in §7; quantified impact in §9; 10+ Q&As in §11.

Case studies:
- `case_studies/design_lru_cache.md`
- `case_studies/top_k_and_streaming_problems.md`
- `case_studies/dynamic_programming_patterns.md`
- `case_studies/graph_traversal_and_shortest_path.md`
- `case_studies/autocomplete_and_string_search.md`
- `case_studies/interval_and_scheduling_problems.md`

Learning-path index: `case_studies/case_studies.md` (mandatory; update with every new case study).
Reference for adapted template: see `../llm/case_studies/design_gpu_inference_platform.md` for structural quality bar (use as style guide, not exact template — the section headings above differ).

---

## Cross-Reference Map

| CS Fundamentals Module | See Also (other sections) |
|------------------------|--------------------------|
| `complexity_analysis_and_big_o/` | `../../java/collections_internals/` — per-collection Big-O tables; `../../database/indexing_deep_dive/` — B+Tree O(log n) guarantees |
| `number_systems_and_bit_manipulation/` | `../../python/strings_bytes_encoding_and_regex/` — UTF-8, bytes/memoryview; `../../java/strings_and_text/` — compact strings, surrogate pairs |
| `recursion_and_problem_solving_patterns/` | `../../java/concurrency/` — recursive algorithms with thread pools; `graph_and_string_algorithms/` (module 12) |
| `arrays_strings_and_hashing/` | `../../java/collections_internals/` — HashMap/ConcurrentHashMap internals; `../../python/collections_and_data_structures/` — dict/set internals |
| `linked_lists_stacks_and_queues/` | `../../java/collections_internals/` — LinkedHashMap-for-LRU, ArrayDeque |
| `trees_and_binary_search_trees/` | `../../database/indexing_deep_dive/` — B+Tree storage engine role; `../../java/collections_internals/` — TreeMap as red-black tree |
| `heaps_and_priority_queues/` | `../../java/collections_internals/` — PriorityQueue as binary min-heap; `sorting_and_searching/` (module 9) |
| `graphs_tries_and_advanced_structures/` | `../../hld/` — Bloom filters in caching; `graph_and_string_algorithms/` (module 12) |
| `sorting_and_searching/` | `../../database/` — sort-merge join; `complexity_analysis_and_big_o/` (module 1) |
| `dynamic_programming/` | `recursion_and_problem_solving_patterns/` (module 3) |
| `greedy_and_divide_and_conquer/` | `complexity_analysis_and_big_o/` (module 1) |
| `graph_and_string_algorithms/` | `graphs_tries_and_advanced_structures/` (module 8) |
| `processes_threads_and_context_switching/` | `../../devops/linux_and_os_fundamentals/` — cgroups, namespaces, OOM killer; `../../java/concurrency/` — JVM thread model; `../../python/threading_and_multiprocessing/` — GIL |
| `cpu_scheduling_algorithms/` | `../../devops/linux_and_os_fundamentals/` — CFS in practice |
| `memory_management_and_virtual_memory/` | `../../java/jvm_internals/` — JVM heap regions, GC; `../../python/cpython_memory_model/` — refcounting, pymalloc |
| `deadlocks_and_synchronization/` | `../../java/concurrency/` — AQS, ReentrantLock, semaphores deep dive; `../../database/concurrency_control_and_locking/` — deadlock in DB transactions; `../../lld/concurrency_patterns/` — ReadWriteLock, ThreadPool |
| `computer_architecture_and_memory_hierarchy/` | `../../java/concurrency/` — `@Contended`, false sharing, memory barriers |
| `networking_fundamentals/` | `../../backend/osi_model_and_networking/` — OSI 7-layer deep dive; `../../backend/tcp_ip_deep_dive/` — TCP internals; `../../backend/udp_and_quic/`; `../../backend/http_protocols/` |
| `database_and_storage_fundamentals/` | `../../database/database_fundamentals/` — ACID/BASE/MVCC deep dive; `../../database/indexing_deep_dive/`; `../../database/schema_design_and_normalization/` |
| `cryptography_fundamentals/` | `../../backend/backend_security_owasp/` — applied crypto (BCrypt, A02); `../../backend/auth_and_authorization_systems/` — JWT/TLS applied; `../../devops/secrets_management/` — Vault, key rotation |
| `character_encoding_deep_dive/` | `../../python/strings_bytes_encoding_and_regex/` — codec API, `str`/`bytes` split; `../../java/strings_and_text/` — Compact Strings, surrogate pairs in practice |

---

## Content Rules (CS Fundamentals-specific)

- **Python-first** for algorithm/DS implementations: type-hinted, runnable-shaped, `from __future__ import annotations`, dataclasses where appropriate; Java noted only for JVM-specific points
- **Concrete numbers**: cache line 64 B, L1 ~1–4 ns, L2 ~10 ns, L3 ~40 ns, RAM ~100 ns, SSD ~100 µs, HDD ~10 ms; hash table default load factor 0.75, resize threshold; page size 4 KB; context switch ~1–10 µs; virtual memory max 128 TiB (x86-64 user space)
- **Diagrams appeal-first** (see root `CLAUDE.md`): Mermaid preferred for topological diagrams (flow/sequence/state); ASCII kept only for grids, bit/byte layouts, and geometry Mermaid can't draw; no image files
- **No emojis** anywhere
- **At least 1 BROKEN→FIX block** in §10 (Common Pitfalls) and §14 (Case Study)
- **`---` horizontal rules** between every top-level section
- **Cross-link rather than duplicate**: if a topic has a deep applied treatment in another section, give a 2–4 paragraph conceptual explanation here and link out — do not re-teach the full depth

---

## DSA Pattern Playbooks Sub-Section (`dsa_patterns/`)

Path: `dsa_patterns/` (inside this section root).

This is the **pattern-recognition and strategy-selection layer** — the "fairly certain guess" engine. It sits *on top of* the 12 DSA concept modules (Phases 1–3) and does NOT re-teach data structures. It answers: given an unseen problem, what pattern do I apply? In the game it is wired as its own Study topic — a `STUDY_ORDER.cs_fundamentals` entry `cs_fundamentals/dsa_patterns`, placed after Phase 3, with its Senior-tier file list declared under `dsa_patterns` in this section's `README.md` `<!-- study-paths -->` block.

Files:
- `dsa_patterns/dsa_patterns.md` — master recognition engine (decision tree, cue→pattern table, constraints→complexity table, complexity cheat sheet, pattern index)
- `dsa_patterns/interview_execution_playbook.md` — UMPIRE method, L5 rubric, communication scripts, mock dialogue, "what to say when stuck"
- `dsa_patterns/study_plans.md` — Blind 75 + NeetCode 150, pattern-mapped, LeetCode links, difficulty, suggested order
- 25 pattern playbook files (one per pattern — see the master page `dsa_patterns/dsa_patterns.md` §6 Pattern Index for the full list)

### Pattern-File Template (NEW — third template alongside module and case-study templates)

```
# <Pattern Name>

## Pattern Snapshot          (what it is + the one-line cue + typical complexity)
## 1. Recognition Signals    (cues that match; PLUS anti-signals — looks-like-but-isn't)
## 2. Mental Model & Intuition  (ASCII diagram showing the core invariant)
## 3. The Template           (Python-first, type-hinted, canonical form to memorize)
## 4. Annotated Walkthrough  (trace the template on ONE signature problem, ASCII state)
## 5. Complexity             (time/space of the template + why)
## 6. Variations & Sub-patterns
## 7. Problem Bank           (LeetCode table: Problem [LC link] | Difficulty | Variation | Recognition cue/twist — easy→hard)
## 8. Common Mistakes (BROKEN -> FIX)   (at least 1 broken→fix block)
## 9. Related Patterns & When to Switch
## 10. Cross-links           (concept module + applied sections)
## 11. Interview Q&A         (10+ pattern-specific Q&As; bold question / plain answer)
```

**Quality bar per pattern file:**
- ~350–600 lines (focused playbook, not a full single-problem walkthrough)
- Python-first, type-hinted, runnable-shaped code
- Diagrams appeal-first: Mermaid preferred for topological diagrams; ASCII kept only for grids/geometry; no image files
- At least 1 BROKEN→FIX block in §8
- 10+ Q&As in §11
- Real LeetCode hyperlinks in §7 (see link-format exception below)
- `---` horizontal rules between every top-level section
- No emojis

### LeetCode Link Format Exception

**Scoped to `dsa_patterns/` only.** All files inside `dsa_patterns/` use real clickable hyperlinks:

```
[Two Sum (LC 1)](https://leetcode.com/problems/two-sum/)
```

This is an intentional exception to the rest of the repo's plain-text `LeetCode N` convention. The exception is scoped — all files outside `dsa_patterns/` must continue using plain-text references.

### Adding a New Pattern File

1. Create `dsa_patterns/<pattern_name>.md` — use the pattern-file template above
2. Meet the quality bar: 350–600 lines, 10+ Q&As, ≥1 BROKEN→FIX, real LeetCode links in §7
3. Add a row to the Pattern Index table in `dsa_patterns/dsa_patterns.md` §6
4. Add a row to the DSA Pattern Playbooks manifest in `README.md` §7
5. Add a bidirectional "See Also" entry in the relevant concept module(s) pointing to the new pattern file
6. Add the filename under `dsa_patterns` in this section's `README.md` `<!-- study-paths -->` block, tagged `senior` — that block is the only place a sub-file is addressable; leaving it out keeps the file Full-path only. Then run `python3 game/extract.py --write-paths` (the generated table counts files per module).

---

## Learning Paths (Full + Senior)

`README.md` documents the **Full Path** (all 24 modules + the dsa_patterns topic = "5-Phase Learning Path", README §4) plus one curated tier: **Senior** (17 modules). **This section has no Principal tier and needs none** — no module declares one, `check_wiring()` skips a tier whose markers declare zero modules, and adding a Principal heading with no members is a false alarm, not a gap. Membership is ONCE per SECTION, in the `<!-- study-paths -->` block in `README.md`, naming every module, every file it owns, and the tiers each tier takes — which is how `dsa_patterns/` names its 25 playbook files plus the two guides individually, since sub-files have no `STUDY_ORDER` entry of their own. Listing a tier joins it, omitting the tier opts out, and the module page (`<module>.md`) must always be listed. Order is never declared — it comes from `STUDY_ORDER.cs_fundamentals` in `game/app.js`, so a tier is an ordered subset by construction. **There is no path array in `app.js` to edit**: `extract.py` walks the markers and emits the gitignored `questions/paths.json`, which the game fetches at boot. The Senior table in `README.md` sits between `<!-- study-path-table senior -->` / `<!-- /study-path-table -->` and is **generated** — regenerate with `python3 game/extract.py --write-paths`; a hand-edited or stale block fails `extract.py --strict` and the Pages deploy. The 6 case-study walkthroughs carry no tier markers, so the Case Studies tab shows all of them with no Level filter. The README also carries a Knowledge-Question Map and a 6-week Study Plan (prose; no path impact).

---

## Planned Topics (NOT built this pass)

Record these here so future sessions pick them up. Do not link to them as if they exist:

**DONE (2026-07-07):** `theory_of_computation/`, `how_code_runs_compilers_and_interpreters/`, and `discrete_math_for_engineers/` — all built as full standalone 14-section modules (see `README.md` §3 module table, modules 22-24; added to `STUDY_ORDER.cs_fundamentals`).

**DONE (2026-07-07):** `character_encoding_deep_dive/` — built as a full standalone 14-section module (not the crosslink-only stub originally envisioned here); see `README.md` §3 module table (module 21) and the Cross-Reference Map above.

---

## Build Manifest

See `README.md` §7 for the authoritative per-file `pending`/`done` status table and NEXT UP pointer.

**DSA Pattern Playbooks — STATUS: COMPLETE (Chunks P0-P6 all done)**
- Chunk P0: DONE — `dsa_patterns/dsa_patterns.md` (master recognition engine)
- Chunk P1: DONE — `two_pointers.md`, `sliding_window.md`, `fast_and_slow_pointers.md`, `prefix_sum.md`, `cyclic_sort.md`, `monotonic_stack.md`, `in_place_linked_list_reversal.md`, `merge_intervals.md`, `hashing_patterns.md`
- Chunk P2: DONE — `modified_binary_search.md`, `top_k_elements.md`, `k_way_merge.md`, `two_heaps.md`
- Chunk P3: DONE — `tree_bfs.md`, `tree_dfs.md`, `graph_traversal.md`, `topological_sort.md`, `union_find.md`, `trie_patterns.md`, `shortest_path.md`
- Chunk P4: DONE — `backtracking.md`, `dynamic_programming.md`, `greedy.md`, `bit_manipulation.md`
- Chunk P5: DONE — `interview_execution_playbook.md`, `study_plans.md` + 100 bidirectional See Also links across the 12 Phase 1-3 concept modules
- Chunk P6: DONE (2026-06-10) — comprehensive audit: all 24 problem banks expanded to ≥15 problems with full variation coverage; `matrix_traversal.md` added as pattern #25 and wired into the `dsa_patterns` master page §3/§4/§6, study plans, and the `arrays_strings_and_hashing` See Also

All 25 pattern files + the `dsa_patterns/dsa_patterns.md` master page + 2 guides are complete and verified (structure, Q&A counts, code sanity, link integrity, conventions). No further dsa_patterns/ chunks are queued. If this sub-section is revisited, see "Planned Topics" above for candidate additions to the broader cs_fundamentals section instead.

On finishing a module/chunk:
1. Flip status to `done` in `README.md` §7
2. Advance NEXT UP pointer
3. Update `case_studies/case_studies.md` + root `README.md` + this CLAUDE.md if applicable
4. For pattern files: add bidirectional "See Also" in the relevant concept module(s)

---

## Adding a New CS Fundamentals Module

1. Create `<module_name>/<module_name>.md` — 14-section template (root CLAUDE.md); 15 Q&As minimum (18 for DSA/algorithm modules listed above)
2. Follow CS Fundamentals-specific content rules above (Python-first, concrete numbers, BROKEN→FIX)
3. Update `README.md` module table AND flip the file's status in the §7 build manifest
4. Add the module dir to `STUDY_ORDER.cs_fundamentals` in `game/app.js` at its phase position — a module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
5. Add the module and EVERY file it owns to this section's `README.md` `<!-- study-paths -->` block, tagging each `senior` or `-` (the module page must carry every tier the module is in; this section has no Principal tier). **Never put a block in the module page — content files hold only content**; then run `python3 game/extract.py --write-paths` to regenerate `README.md`'s Senior table. Never hand-edit that table.
6. Update root `README.md` CS Fundamentals phase table
7. Update root `CLAUDE.md` CS Fundamentals module count

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
