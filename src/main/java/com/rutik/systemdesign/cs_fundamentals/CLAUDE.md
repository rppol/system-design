# CS Fundamentals Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/cs_fundamentals/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §7 — check NEXT UP pointer and per-file status before starting a new module.

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

Learning-path index: `case_studies/README.md` (mandatory; update with every new case study).
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
- **ASCII diagrams only** — no Mermaid, no image files
- **No emojis** anywhere
- **At least 1 BROKEN→FIX block** in §10 (Common Pitfalls) and §14 (Case Study)
- **`---` horizontal rules** between every top-level section
- **Cross-link rather than duplicate**: if a topic has a deep applied treatment in another section, give a 2–4 paragraph conceptual explanation here and link out — do not re-teach the full depth

---

## DSA Pattern Playbooks Sub-Section (`dsa_patterns/`)

Path: `dsa_patterns/` (inside this section root).

This is the **pattern-recognition and strategy-selection layer** — the "fairly certain guess" engine. It sits *on top of* the 12 DSA concept modules (Phases 1–3) and does NOT re-teach data structures. It answers: given an unseen problem, what pattern do I apply?

Files:
- `dsa_patterns/README.md` — master recognition engine (decision tree, cue→pattern table, constraints→complexity table, complexity cheat sheet, pattern index)
- `dsa_patterns/interview_execution_playbook.md` — UMPIRE method, L5 rubric, communication scripts, mock dialogue, "what to say when stuck"
- `dsa_patterns/study_plans.md` — Blind 75 + NeetCode 150, pattern-mapped, LeetCode links, difficulty, suggested order
- 25 pattern playbook files (one per pattern — see master README §6 Pattern Index for the full list)

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
- ASCII diagrams only — no Mermaid, no images
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
3. Add a row to the Pattern Index table in `dsa_patterns/README.md` §6
4. Add a row to the DSA Pattern Playbooks manifest in `README.md` §7
5. Add a bidirectional "See Also" entry in the relevant concept module(s) pointing to the new pattern file

---

## Learning Paths (Full + Interview-Specific)

`README.md` documents two routes: the **Full Path** (all 21 modules = "5-Phase Learning Path", README §4) and a curated **Interview-Specific Path** (16 modules). The interview subset is a **dual-source list** — it lives in both `README.md` ("## Learning Paths") and `game/app.js` (`STUDY_PATHS.cs_fundamentals.interview`, which drives the game's Study Full/Interview toggle). **Change one, change the other** — same modules, same order. Non-Q&A narrative only; no `extract.py` re-run needed. The README also carries a Knowledge-Question Map and a 6-week Study Plan (interview-readiness prose; no toggle impact).

---

## Planned Topics (NOT built this pass)

Record these here so future sessions pick them up. Do not link to them as if they exist:

**DONE (2026-07-07):** `theory_of_computation/`, `how_code_runs_compilers_and_interpreters/`, and `discrete_math_for_engineers/` — all built as full standalone 14-section modules (see `README.md` §3 module table, modules 22-24; added to `STUDY_ORDER.cs_fundamentals`).

**DONE (2026-07-07):** `character_encoding_deep_dive/` — built as a full standalone 14-section module (not the crosslink-only stub originally envisioned here); see `README.md` §3 module table (module 21) and the Cross-Reference Map above.

---

## Build Manifest

See `README.md` §7 for the authoritative per-file `pending`/`done` status table and NEXT UP pointer.

**DSA Pattern Playbooks — STATUS: COMPLETE (Chunks P0-P6 all done)**
- Chunk P0: DONE — `dsa_patterns/README.md` (master recognition engine)
- Chunk P1: DONE — `two_pointers.md`, `sliding_window.md`, `fast_and_slow_pointers.md`, `prefix_sum.md`, `cyclic_sort.md`, `monotonic_stack.md`, `in_place_linked_list_reversal.md`, `merge_intervals.md`, `hashing_patterns.md`
- Chunk P2: DONE — `modified_binary_search.md`, `top_k_elements.md`, `k_way_merge.md`, `two_heaps.md`
- Chunk P3: DONE — `tree_bfs.md`, `tree_dfs.md`, `graph_traversal.md`, `topological_sort.md`, `union_find.md`, `trie_patterns.md`, `shortest_path.md`
- Chunk P4: DONE — `backtracking.md`, `dynamic_programming.md`, `greedy.md`, `bit_manipulation.md`
- Chunk P5: DONE — `interview_execution_playbook.md`, `study_plans.md` + 100 bidirectional See Also links across the 12 Phase 1-3 concept modules
- Chunk P6: DONE (2026-06-10) — comprehensive audit: all 24 problem banks expanded to ≥15 problems with full variation coverage; `matrix_traversal.md` added as pattern #25 and wired into README §3/§4/§6, study plans, and the `arrays_strings_and_hashing` See Also

All 25 pattern files + master README + 2 guides are complete and verified (structure, Q&A counts, code sanity, link integrity, conventions). No further dsa_patterns/ chunks are queued. If this sub-section is revisited, see "Planned Topics" above for candidate additions to the broader cs_fundamentals section instead.

On finishing a module/chunk:
1. Flip status to `done` in `README.md` §7
2. Advance NEXT UP pointer
3. Update `case_studies/README.md` + root `README.md` + this CLAUDE.md if applicable
4. For pattern files: add bidirectional "See Also" in the relevant concept module(s)

---

## Adding a New CS Fundamentals Module

1. Create `<module_name>/README.md` — 14-section template (root CLAUDE.md); 15 Q&As minimum (18 for DSA/algorithm modules listed above)
2. Follow CS Fundamentals-specific content rules above (Python-first, concrete numbers, BROKEN→FIX)
3. Update `README.md` module table AND flip the file's status in the §7 build manifest
4. Update root `README.md` CS Fundamentals phase table
5. Update root `CLAUDE.md` CS Fundamentals module count

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
