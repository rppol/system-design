# CS Fundamentals Audit — 2026-08-25

## 0. Snapshot

`cs_fundamentals` is the repo's language-agnostic CS spine: 24 module pages across 5 phases
(complexity/discrete math → data structures → algorithms → OS → systems & security), a
26-file `dsa_patterns/` sub-section wired as its own 25th Study topic, and 6 interview-problem
walkthrough case studies. It is the **most structurally disciplined section in the repo** —
every module clears its Q&A floor, every Q&A carries an authored `**Short:**` (704 of 704),
every module has diagrams, and the 2026-08-04 audit that executed the code found and fixed the
class of defect that reading misses. Its **real strength is the `dsa_patterns/` engine**: 25
playbooks with 560 live LeetCode links, an anti-signal table, a pattern-interaction map, and a
7-phase plan — that is a better artifact than most paid interview courses, and it is the part a
reader will actually use.

Its **single biggest weakness is not staleness, it is a coverage hole with a shape**: the
section systematically stops at the boundary where a concept becomes *theory about
concurrency, randomness, or information*, and punts to a language section that teaches the
same thing applied. Grep the whole section and you get **zero hits for `linearizability`,
`sequential consistency`, `acquire/release`, `memory model`, `seqlock`, `transactional
memory`, `HyperLogLog`, `cuckoo filter`, `MinHash`, `rendezvous hashing`, `information
theory`, `Kolmogorov`, `io_uring`, `futex`, `BQP`, `Grover`, `Shor`** — and `count-min sketch`
appears only inside a case study. A section whose stated charter is "the CS theory the
applied sections assume" has delegated its own core theory: `deadlocks_and_synchronization`
covers Coffman, Banker's, and dining philosophers thoroughly, then hands `happens-before` off
to `java/concurrency`. That is the gap worth closing, and it is worth more than any
modernization pass here.

| Metric | Value |
|---|---|
| Modules | 24 (+ `dsa_patterns` as a 25th `STUDY_ORDER` topic) |
| Case studies | 6 + index |
| Sub-files | 26 (all in `dsa_patterns/`: 25 playbooks + 2 guides; `dsa_patterns.md` is the module page) |
| Total content lines | 49,037 (modules 25,314 · dsa_patterns 17,515 · case studies 6,208) |
| Q&As in the quiz bank | 704 (100% `**Short:**` coverage) |
| Case-study Q&As (separate pool) | 108 |
| Avg lines/module page | 1,055 (range 785 – 1,591) |
| Mermaid fences | 205 |
| Modules on **no** curated tier | 8 of 24 |
| Principal-tier modules | **0** |

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `cs_fundamentals/probabilistic_and_streaming_structures/probabilistic_and_streaming_structures.md` | module | Zero section-wide hits for HyperLogLog, cuckoo filter, MinHash/SimHash, rendezvous hashing; Count-Min only inside a case study; consistent hashing is 3 passing mentions. These are now table-stakes: the decision framework "Bloom = membership, CMS = frequency, HLL = cardinality" is taught as one unit in every 2026 interview guide — [techinterview.org](https://www.techinterview.org/post/3233474148/system-design-bloom-filter-count-min-sketch-hyperloglog-probabilistic-data-structures-space-efficient-membership-testing/), [Towards Data Science](https://towardsdatascience.com/probabilistic-data-structures-decoded-enhancing-performance-in-modern-computing-17f700e6ea47/) | **P0** | 1,000 |
| `cs_fundamentals/memory_models_and_concurrency_theory/memory_models_and_concurrency_theory.md` | module | `linearizability`, `sequential consistency`, `acquire/release`, `memory model`, `seqlock`, `transactional memory` = **0 hits section-wide**. `deadlocks_and_synchronization` gives lock-free one §6 paragraph + 2 Q&As and delegates `happens-before` to `java/concurrency`. "Interviewers use concurrency to separate senior engineers from developers who merely have senior-level experience"; linearizability is named explicitly as expected senior knowledge — [Algoroq, Concurrency Interview Questions for Senior Engineers (2026)](https://www.algoroq.io/interview-questions/concurrency/) | **P0** | 1,000 |
| `cs_fundamentals/dsa_patterns/ai_assisted_round_playbook.md` | sub-file | `interview_execution_playbook.md` has **zero** mentions of AI. Meta has formally added an AI-Enabled Coding round to E4/E5/E6 loops and at E7+ it is the *only* coding round; Netflix pairs CodeSignal GCA with an AI-Assisted follow-on; Google is piloting a Gemini-assisted comprehension round. The rubric moved to *verification of AI output* — [Formation](https://formation.dev/blog/3-ways-ai-has-changed-faang-interviews-in-2026), [ClavePrep Meta 2026](https://claveprep.com/blog/meta-interview-process-2026-guide) | **P0** | 450 |
| `cs_fundamentals/information_theory_and_compression/information_theory_and_compression.md` | module | `information theory`, `Kolmogorov`, `channel coding` = 0 hits; Shannon entropy appears once (sorting lower bound) with no definition behind it. Entropy is the shared root of the comparison-sort bound (`sorting_and_searching`), hashing uniformity (`arrays_strings_and_hashing`), HLL's estimator, compression, erasure coding (`database_and_storage_fundamentals`), and cross-entropy loss in `ml/` — five modules each borrowing a concept the section never teaches | P1 | 900 |
| `cs_fundamentals/processes_threads_and_context_switching/async_io_and_event_loops.md` | sub-file | `io_uring`, `futex`, `vDSO`, `seccomp`, `posix_spawn` = 0 hits; `epoll` is a passing name-drop. Readiness (epoll/kqueue) vs completion (io_uring/IOCP) is the model behind every async runtime the repo teaches applied (`fastapi/`, `java/` virtual threads, `python/` asyncio) and has no conceptual home | P1 | 600 |
| `cs_fundamentals/dsa_patterns/sweep_line.md` | sub-file | Sweep line is named in 2026 pattern cheat-sheets as a first-class pattern ("Interval Merge/Sweep" is listed among the essential patterns from recent Google/Meta/Amazon loops) — [LastRound AI patterns cheat sheet](https://lastroundai.com/blog/leetcode-patterns-cheat-sheet). Today it is folded into `merge_intervals.md`, which teaches the sort-and-merge template but not the event-queue/delta form (meeting rooms II, skyline, car pooling, difference array) | P1 | 450 |
| `cs_fundamentals/theory_of_computation/quantum_and_randomized_complexity.md` | sub-file | `BQP`, `Grover`, `Shor` = 0 hits in `theory_of_computation`, yet `cryptography_fundamentals` §4.8 builds its whole post-quantum argument on them. The section asserts the consequence and never teaches the cause. Also closes BPP/derandomization and PCP/approximation-hardness | P2 | 500 |
| `cs_fundamentals/how_code_runs_compilers_and_interpreters/abis_linking_and_wasm.md` | sub-file | `WASM`, `MLIR`, `calling convention`, `ABI`, `computed goto`, `sanitizer` = 0 hits. "How code runs" that never explains how a call frame is laid out, how a dynamic linker resolves a symbol, or what a portable bytecode target looks like in 2026 is missing its bottom layer | P2 | 550 |
| `cs_fundamentals/computer_architecture_and_memory_hierarchy/` — extend in place | edit | `roofline`, `DDR5`, `HBM`, `CXL`, `Gustafson` = 0 hits. Roofline is the standard way to say "am I compute- or memory-bound"; CXL memory tiering is the live change in the hierarchy this module draws | P2 | +200 |
| `cs_fundamentals/cryptography_fundamentals/` §4.8 — extend in place | edit | HQC selected by NIST as the fifth PQC algorithm on 2025-03-11, a code-based (non-lattice) KEM hedging ML-KEM; draft standard expected ~2026, final 2027 — [NIST/Utimaco](https://utimaco.com/news/blog-posts/pqc-news-nist-announces-hqc-fifth-algorithm-be-standardized), [HPCwire](https://www.hpcwire.com/2025/03/12/nist-selects-hqc-as-fifth-algorithm-for-post-quantum-encryption/). One table row + one sentence | P2 | +15 |

### P0 detail

**`probabilistic_and_streaming_structures/`** — §1 the accuracy-for-space trade as a design
axis; §2 "a hash is a random sample of an element's identity, and you can pay for less of it";
§3 one-sided error, the false-positive/false-negative asymmetry, mergeability, independence
assumptions; §4 Bloom / counting Bloom / cuckoo / quotient filters, HyperLogLog(++), Count-Min,
MinHash + SimHash LSH, t-digest, reservoir sampling, consistent + rendezvous hashing; §5 the
bit-array constraint grid (which of k hashes fired) and a before/after showing 1 GB exact set →
12 KB HLL; §6 executable Python for each with the real formulas (`m = -n·ln p / (ln 2)²`, HLL's
harmonic-mean estimator with the 1.04/√m error, CMS `w = ⌈e/ε⌉, d = ⌈ln 1/δ⌉`); §7 Cassandra/
RocksDB SSTable Bloom filters, Redis `PFCOUNT`, Chrome Safe Browsing, Akamai/DynamoDB rings;
§8 the space/error/query-type comparison table; §9 when the exact structure fits in RAM, do not
reach for these; §10 **BROKEN→FIX** sizing a Bloom filter for the wrong `n` and shipping a 40%
FPR, plus the "delete from a Bloom filter" trap; §11 RedisBloom, `datasketches`, `probables`,
`hyperloglog`; §12 18 Q&As; §13 measure the real FPR in prod, never trust the design-time one;
§14 dedup pipeline sized end-to-end. **Do not duplicate `hld/`** — this teaches the math and the
error bound, `hld/` teaches the deployment.

**`memory_models_and_concurrency_theory/`** — §1 why a correct-looking multithreaded program is
undefined without an ordering contract; §2 "the compiler and the CPU each reorder your code, and
the memory model is the only promise either one made you"; §3 program order vs happens-before as
a partial order, data-race-freedom ⇒ SC, publication/safe initialization; §4 SC / TSO / weak
(ARM, POWER) hardware models; relaxed / acquire-release / seq_cst language models; the Java JMM
and C++11 model as two instances of one idea; §5 a store-buffer litmus-test grid (the classic
`r1==r2==0` outcome) — this is exactly the ASCII constraint-grid archetype; §6 executable
demonstrations of a broken double-checked lock, a Dekker litmus test, and a seqlock; the
linearizability-vs-serializability distinction worked on a concrete history; the wait-free /
lock-free / obstruction-free hierarchy and Herlihy's consensus number; §7 Java `volatile`, Go's
race detector, Rust's `Ordering`, Linux RCU, `folly` hazard pointers; §8 progress-guarantee vs
throughput table; §9 when a mutex is simply the right answer; §10 **BROKEN→FIX** a lock-free
stack with ABA and the version-tag fix, plus a false-sharing pair with the padding fix; §11 TSan,
Loom (Java), `litmus7`, TLA+/PlusCal, Jepsen; §12 18 Q&As; §14 verifying a lock-free ring buffer.
Boundary: this owns the **model**, `java/concurrency` keeps AQS/`ReentrantLock`/virtual threads,
`database/consistency_models_and_consensus` keeps the distributed side — cross-link all three.

**`dsa_patterns/ai_assisted_round_playbook.md`** — the CoderPad three-panel format (files ·
editor · AI chat), what the AI can and cannot do (it reads every file, it cannot touch the
editor — you type every line), the scoring shift from "did you solve it" to "did you verify it",
a prompt-quality ladder, a verification checklist (run it, construct the adversarial input,
explain the invariant), the failure modes graders punish (pasting unread, accepting a plausible
wrong complexity, not noticing a dropped edge case), a full mock transcript, and a "when to
ignore the AI" rule. Pattern-file template applies; 10+ Q&As. This is the single highest-signal
addition in the whole section because it is the one thing here that a 2026 reader will hit in a
loop and find nothing about.

---

## 2. Stale — verified CORRECTIONS

| File | Line/heading | What it says | What is true in Aug 2026 | Source |
|---|---|---|---|---|
| `cs_fundamentals/README.md` | lines 7–12, the block quote above `## Intuition` | "**PARKED — to be done later (owner-set 2026-07-29)** … out of scope for the repo-wide factual audit and the `**Short:**` migration … **24 audit units (none started)** and **701 Q&As awaiting one-line MCQ summaries**. Do not dispatch audit or migration work here." | Parking was **lifted 2026-08-04**; the audit is DONE for all 24 modules + all 26 `dsa_patterns` files (commits `640ce22`, `b31a6b0`, `faf44a1`, `1923ffe`) and Shorts are **704 of 704** (`6ce35a6`). Verified live: `python3 extract.py` prints `cs_fundamentals 704`, and the section's own `CLAUDE.md` §Scope says COMPLETE. The banner also points at a root-`CLAUDE.md` heading ("Deferred / To Be Planned") that no longer exists | internal — repo state + `extract.py` output; contradicted by `cs_fundamentals/CLAUDE.md` line 7 |
| `cs_fundamentals/dsa_patterns/study_plans.md` | line 1 title, §1 (lines 57–72) | Frames the canon as exactly two lists — "Blind 75 (2020)" and "NeetCode 150 (2022) … 18 categories" — and stops there | Both remain valid, but the 2026 canon is wider: **NeetCode 250** exists as the depth tier above NC150, **LeetCode's own "Top Interview 150"** is an official badge-bearing study plan, and company-tagged lists are the standard last-mile. Guidance also converged on "100–150 deeply understood problems", which the file's own 8.5-week plan agrees with but never states as the target | [intervu.dev 2026 roadmap](https://intervu.dev/blog/how-to-prepare-for-coding-interview/), [LeetCode Top Interview 150](https://leetcode.com/studyplan/top-interview-150/), [Educative comparison](https://www.educative.io/blog/blind75-vs-neetcode-vs-educative) |
| `cs_fundamentals/cryptography_fundamentals/cryptography_fundamentals.md` | §4.8, line 295 table | Lists the PQC standards as exactly three: FIPS 203 / 204 / 205, "NIST published the first post-quantum standards in **August 2024**" | Correct but now incomplete. NIST selected **HQC** on 2025-03-11 as a fifth algorithm — a code-based KEM deliberately chosen as a non-lattice hedge against ML-KEM; draft standard ~2026, final expected 2027 | [Utimaco](https://utimaco.com/news/blog-posts/pqc-news-nist-announces-hqc-fifth-algorithm-be-standardized), [HPCwire 2025-03-12](https://www.hpcwire.com/2025/03/12/nist-selects-hqc-as-fifth-algorithm-for-post-quantum-encryption/) |
| `cs_fundamentals/README.md` | §7, line ~423 | "plus the **Interview-Specific** learning path (see 'Learning Paths')" | The tier was renamed **Senior** repo-wide; there is no Interview-Specific path any more. Cosmetic, but it is the second stale pointer in the same README | internal — `<!-- study-path-table senior -->` markers |

**The rest: none found.** I web-checked the section's most version-sensitive claims and they
hold. EEVDF is correctly named as the Linux default scheduler across `cpu_scheduling_algorithms`
and four other files — no file still calls CFS current. The post-quantum treatment names
ML-KEM/ML-DSA/SLH-DSA with the right FIPS numbers, names `X25519MLKEM768` as the shipped hybrid
group, and gets the Grover-halves/Shor-breaks asymmetry right. MGLRU, 5-level paging, cgroup v2,
free-threaded Python 3.14, and Apple Silicon's 128 B line / 16 KB pages are all current. **This
is what "the audit worked" looks like** — the 2026-08-04 pass did its job, and I am not going to
manufacture findings to fill this table. The one systemic risk it left is documented in the
section's own CLAUDE.md and I confirmed it: the defects here live in *demonstrations*, not
claims, so any future pass must execute the code rather than read it.

---

## 3. Reordering — SEQUENCE and TIER changes

| Module | Current position/tier | Proposed | Why |
|---|---|---|---|
| `computer_architecture_and_memory_hierarchy` | position **20**, tier `-` (Full only) | position **2**, tier **senior** | This is the single most important move. Every data-structure module argues from cache locality — "array beats linked list because of the cache line" is the load-bearing claim in `arrays_strings_and_hashing`, `linked_lists_stacks_and_queues`, and `complexity_analysis_and_big_o` — and the module that establishes the 64 B line and the L1 ~1 ns → RAM ~100 ns ladder sits 18 positions *after* them, on no curated tier at all. A Senior-tier reader never sees it. Move it to right after `complexity_analysis_and_big_o`: complexity gives the asymptote, architecture gives the constant, and the rest of the section spends its time trading one against the other. |
| `memory_models_and_concurrency_theory` (new) | — | position **20**, tier **senior + principal** | Directly after `deadlocks_and_synchronization`, which currently ends by handing the topic to another section. |
| `probabilistic_and_streaming_structures` (new) | — | position **11**, tier **senior + principal** | After `graphs_tries_and_advanced_structures` (which owns the Bloom filter today) and before `sorting_and_searching`. |
| `information_theory_and_compression` (new) | — | position **25**, tier `-` | Beside `theory_of_computation`; both are the "why" layer. |
| `character_encoding_deep_dive` | position 4 (Phase 1 in `STUDY_ORDER`), Phase 5 in `CLAUDE.md`'s table | keep position 4, **fix `CLAUDE.md`'s phase table** | Position 4 is right — bits → bytes → code points is the natural chain off `number_systems_and_bit_manipulation`. The section `CLAUDE.md` module-list table lists it under Phase 5, contradicting the shipped order. Fix the table, not the order. |
| `networking_fundamentals` | tier `-` | tier **senior** | 1,438 lines, 15 Q&As, QUIC/HTTP-3/TLS-1.3/BBR/PMTUD all present. Excluding it from Senior while including `cpu_scheduling_algorithms` is not a defensible cut. |
| `theory_of_computation` | tier `-` | tier **principal** | "This is NP-hard, stop trying to solve it exactly" is a judgment call, not a craft skill — the textbook principal-tier shape. |
| `database_and_storage_fundamentals`, `cryptography_fundamentals`, `computer_architecture_and_memory_hierarchy` | tier `-` | tier **principal** (architecture also senior) | Storage-engine choice, primitive choice, and hardware-cost reasoning are all "which approach at what cost", which is the definition of the principal cut. |

### The Principal-tier question — needs an owner decision

`cs_fundamentals/CLAUDE.md` states flatly: *"This section has no Principal tier and needs
none."* I am proposing to reverse that, and it is the one recommendation here that is a policy
change rather than a fix, so it should be decided deliberately.

The argument for reversing it: **8 of 24 modules are on no curated tier at all**, and they are
not the shallow ones — they are architecture (1,287 lines), networking (1,438), storage (1,499),
crypto (1,591), theory of computation, compilers, discrete math, and encoding. A reader on the
Senior path never sees any of them, and there is no other path that shows them. The section's
most judgment-flavoured material is currently reachable only by someone who selects "Full" and
walks all 25 topics. Per root `CLAUDE.md`, principal is *a different cut, not a deeper one* —
"which approach at what cost, what failure modes, what you tell a team not to do" — and those
eight modules are exactly that cut. A Principal tier of roughly 10 (complexity, architecture,
probabilistic structures, memory models, theory of computation, storage, crypto, networking,
compilers, dsa_patterns) would make them reachable and would overlap Senior by about half,
which is the normal shape elsewhere in the repo.

The argument against: it is 8 lines of `<!-- study-paths -->` edits for a tier nobody asked
for, and the current state is *consistent* (`check_wiring()` skips a zero-member tier, so
nothing is broken today). If the owner declines, the fallback is cheap and strictly better than
nothing: **promote `networking_fundamentals` and `computer_architecture_and_memory_hierarchy`
to Senior** and leave the tier count at one.

### Proposed `STUDY_ORDER["cs_fundamentals"]` (28 entries)

```js
cs_fundamentals: [
  "cs_fundamentals/complexity_analysis_and_big_o",
  "cs_fundamentals/computer_architecture_and_memory_hierarchy",   // MOVED UP from 20
  "cs_fundamentals/discrete_math_for_engineers",
  "cs_fundamentals/number_systems_and_bit_manipulation",
  "cs_fundamentals/character_encoding_deep_dive",
  "cs_fundamentals/recursion_and_problem_solving_patterns",
  "cs_fundamentals/arrays_strings_and_hashing",
  "cs_fundamentals/linked_lists_stacks_and_queues",
  "cs_fundamentals/trees_and_binary_search_trees",
  "cs_fundamentals/heaps_and_priority_queues",
  "cs_fundamentals/graphs_tries_and_advanced_structures",
  "cs_fundamentals/probabilistic_and_streaming_structures",       // NEW
  "cs_fundamentals/sorting_and_searching",
  "cs_fundamentals/dynamic_programming",
  "cs_fundamentals/greedy_and_divide_and_conquer",
  "cs_fundamentals/graph_and_string_algorithms",
  "cs_fundamentals/dsa_patterns",
  "cs_fundamentals/processes_threads_and_context_switching",
  "cs_fundamentals/cpu_scheduling_algorithms",
  "cs_fundamentals/memory_management_and_virtual_memory",
  "cs_fundamentals/deadlocks_and_synchronization",
  "cs_fundamentals/memory_models_and_concurrency_theory",         // NEW
  "cs_fundamentals/networking_fundamentals",
  "cs_fundamentals/database_and_storage_fundamentals",
  "cs_fundamentals/cryptography_fundamentals",
  "cs_fundamentals/theory_of_computation",
  "cs_fundamentals/information_theory_and_compression",           // NEW
  "cs_fundamentals/how_code_runs_compilers_and_interpreters",
],
```

---

## 4. Depth — THIN modules and BLOAT

**No module is thin.** The shortest module page is 785 lines (`recursion_and_problem_solving_patterns`)
and every one of the 24 clears its Q&A floor with the correct 18-vs-15 split. The "primer +
crosslink philosophy is producing stubs" hypothesis is **false here** — I checked, and if
anything the failure runs the other way (see BLOAT below). The real depth defects are
structural, and both were knowingly left open by the last pass.

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `heaps_and_priority_queues` | 920 | 18 | Heading numbering is broken: `## 1. Concept Overview` → **`## Intuition`** (unnumbered) → `## 2. Core Principles`. Every heading from 2 to 8 is off by one, so §5 "Architecture Diagrams" actually ships as `## 4`. On top of that there are **two "When to Use" sections**: `## 8. When to Use / When NOT to Use` and `## 9. When to Use / When NOT (Interview Cheatsheet)` | Renumber to the 14-section template: `## 2. Intuition`, shift 2–8 up by one, merge the duplicate §9 cheatsheet into §9. No Q&A is affected (`## 12` is correct, so the bank is intact) |
| `graphs_tries_and_advanced_structures` | 1,227 | 18 | Same off-by-one: `## 1.` → `## Intuition` → `## 2. Core Principles`. Then **`## 9. Prerequisite Knowledge`** occupies the slot the template gives "When to Use", which has been pushed up to `## 8` | Renumber identically; move the 9-line `Prerequisite Knowledge` block to the top of §1 where it belongs, or fold it into `## 2. Intuition` |
| `dsa_patterns/bit_manipulation.md` | 811 | 9 parsed as `**Q` + 2 unprefixed | Two Q&A lines omit the repo-standard `Q: ` prefix — line 718 `**What does \`n & (n-1)\` do…**` and line 788 `**How does Maximum XOR of Two Numbers (LC 421) use a trie?**`. They still extract (a fully-bold line matches `is_question_line()`), so **no bank loss** — it is a convention violation, not a defect | Add `Q: `. Same on the one instance in `interview_execution_playbook.md`. Three edits total, section-wide |
| `dsa_patterns/` quality bar | — | — | The stated bar is "~350–600 lines" and **14 of 25 playbooks exceed 600** (bit_manipulation 811, trie_patterns 796, shortest_path 782, dynamic_programming 778, greedy 766, graph_traversal 762, union_find 723, topological_sort 720, modified_binary_search 718, backtracking 717, tree_dfs 702, tree_bfs 701, two_heaps 670, k_way_merge 644) | The **bar is stale, not the files** — the longer ones are the harder patterns and earn it. Update the ceiling in `cs_fundamentals/CLAUDE.md` to "~350–800" rather than trimming 14 good files |
| `dsa_patterns/study_plans.md` §4 | 652 | 0 | §4 has 24 pattern tables (4.1–4.24); pattern #25 `matrix_traversal` has no §4.25 — it lives in the "Appendix: Math & Simulation" instead. Defensible (its problems came from NeetCode's Math & Geometry bucket) but inconsistent with §4's own promise of one table per pattern | Promote to `### 4.25 Matrix Traversal`, keeping the appendix for the genuinely pattern-less simulation problems (Plus One, Pow, Multiply Strings) |

### BLOAT — the scope boundary is inverted for three modules

`README.md` §2 promises that where a topic has a deep applied treatment elsewhere, this section
gives "a **2–4 paragraph** conceptual foundation and links out". Three modules overshoot that by
roughly an order of magnitude against sections that already own the material:

| Module | Lines | Overlaps |
|---|---|---|
| `cryptography_fundamentals` | 1,591 | `backend/backend_security_owasp`, `backend/auth_and_authorization_systems`, `devops/secrets_management` |
| `database_and_storage_fundamentals` | 1,499 | `database/database_fundamentals`, `indexing_deep_dive`, `storage_engines_internals`, `concurrency_control_and_locking` |
| `networking_fundamentals` | 1,438 | `backend/osi_model_and_networking`, `tcp_ip_deep_dive`, `udp_and_quic`, `http_protocols`, `devops/networking_for_devops` |

**Recommendation: do not cut them.** They are the three highest-quality modules in the section
by every measure I have (diagram count, concrete-number density, Q&A depth), they were just
audited, and deleting 4,500 audited lines to satisfy a paragraph-count in a README is the
expensive kind of tidiness. The cheap fix is to make the README honest: change "2–4 paragraphs"
to "a self-contained conceptual treatment", and add a one-line boundary callout at the top of
each of the three naming what it owns versus what the sibling section owns. Roughly 30 lines of
edit against 4,500 of rewrite. **No merges and no deletions are recommended anywhere in this
section.**

---

## 5. Cross-section notes

- **`java/concurrency` currently owns theory that belongs here.** `deadlocks_and_synchronization`'s
  See-Also punts `happens-before` and `volatile` to it. Once `memory_models_and_concurrency_theory`
  exists, invert the link: cs_fundamentals owns the model (SC/TSO/acquire-release/DRF ⇒ SC,
  linearizability), `java/concurrency` keeps the JMM's *Java* spelling plus AQS, `ReentrantLock`,
  and virtual threads. Also add a cross-link from `python/threading_and_multiprocessing` (the
  free-threaded 3.14 build makes the memory model a live Python question for the first time).
- **`hld/` owns Bloom filters in caching and consistent hashing in sharding.** The proposed
  `probabilistic_and_streaming_structures` must own the *math* — sizing formulas, error bounds,
  the independence assumption, why HLL's error is 1.04/√m — and explicitly hand deployment to
  `hld/`. Add a bidirectional link in the same commit or the two will drift into duplicates.
- **`database/consistency_models_and_consensus`** is the distributed sibling of the proposed
  memory-models module: linearizability is the same word in both, and a reader who meets it in
  one place and then the other with no link will think they are different concepts. Cross-link.
- **`ml/`** is the natural consumer of `information_theory_and_compression` — cross-entropy,
  KL divergence, and mutual information all have their root there and no conceptual home today.
- **`devops/linux_and_os_fundamentals`** overlaps the proposed `async_io_and_event_loops.md` on
  io_uring. Boundary: cs_fundamentals owns readiness-vs-completion as a model, devops owns
  tuning and the kernel-version matrix.
- **`backend/udp_and_quic`** already covers QUIC in depth; `networking_fundamentals` correctly
  stays conceptual there. No action.

---

## 6. Execution plan

**Wave 1 — corrections and structure (about 3 hours, all parallelizable, zero research).**

1. Delete the PARKED banner from `cs_fundamentals/README.md` lines 7–12; fix the
   "Interview-Specific" → "Senior" reference in §7. *(15 min)*
2. Renumber `heaps_and_priority_queues` and `graphs_tries_and_advanced_structures` to the
   14-section template and resolve the duplicate §9 / misplaced `Prerequisite Knowledge`.
   The last pass left these deliberately under a no-restructure rule; this is the deliberate
   decision it asked for. **Verify `extract.py` still reports 704** afterwards. *(45 min)*
3. Add the missing `Q: ` prefix to 3 Q&A lines (`bit_manipulation.md` ×2,
   `interview_execution_playbook.md` ×1). *(10 min)*
4. Fix the `character_encoding_deep_dive` phase mismatch in `cs_fundamentals/CLAUDE.md`'s
   module table (Phase 5 → Phase 1). Raise the `dsa_patterns` line ceiling to ~800. *(15 min)*
5. Add the HQC row to `cryptography_fundamentals` §4.8 + one Q&A sentence. *(20 min)*
6. Rewrite `study_plans.md` §1 to name NeetCode 250, LeetCode Top Interview 150, and
   company-tagged lists alongside B75/NC150, and state the "100–150 deeply understood" target.
   Promote the matrix_traversal appendix entry to §4.25. *(1 h)*
7. Soften README §2's "2–4 paragraphs" and add boundary callouts to the three large modules. *(30 min)*

**Wave 2 — the three P0 additions (about 3 days; all three run in parallel, no shared files
until the wiring step).**

8. `dsa_patterns/ai_assisted_round_playbook.md` — 450 lines. **Do this first**; it is the
   cheapest P0 and the one with a shelf life. Needs fresh web research on the Meta/Netflix/
   Google formats at write time, since this is the one topic in the section that genuinely
   moves month to month. *(0.5 day)*
9. `probabilistic_and_streaming_structures/` — 1,000 lines, 18 Q&As, 5–6 Mermaid + 2 ASCII
   (bit-array grid, before/after space delta). Every formula must be executed, not quoted —
   this section's audit history says the demonstrations are where defects live. *(1 day)*
10. `memory_models_and_concurrency_theory/` — 1,000 lines, 18 Q&As. Highest care required:
    the litmus tests must be real (a store-buffer example that cannot actually reorder is
    exactly the "broken example that isn't broken" defect the last audit found five times).
    Differential-check against the C++ and Java memory-model specs. *(1.5 days)*

**Wave 3 — wiring (30 min, must be sequential and must follow Wave 2).**

11. `STUDY_ORDER["cs_fundamentals"]` in `game/app.js` — apply the 28-entry array from §3.
    This is the only file where the reordering lives.
12. `cs_fundamentals/README.md` `<!-- study-paths -->` — add the new modules and sub-files with
    their tiers; apply the tier promotions. **Then run
    `python3 game/extract.py --write-paths` and `--strict`.** Never hand-edit the generated
    tier tables.
13. `cs_fundamentals/README.md` §3 module table, §4 phase list, §7 manifest; section
    `CLAUDE.md` module list + phase table + count; **root `README.md`** CS-Fundamentals phase
    table; **root `CLAUDE.md`** module count (24 → 27). Add the Principal-tier line to section
    `CLAUDE.md` if the owner approves §3's proposal.
14. Bidirectional See-Also links: `hld/` ↔ probabilistic structures; `java/concurrency`,
    `python/threading_and_multiprocessing`, `database/consistency_models_and_consensus` ↔
    memory models.

**Wave 4 — P1/P2 backlog (about 3 days, fully parallel, no ordering constraints).**

15. `information_theory_and_compression/` (900 lines) — P1.
16. `processes_threads_and_context_switching/async_io_and_event_loops.md` (600) — P1.
17. `dsa_patterns/sweep_line.md` (450) — P1; needs a §4.26 row in `study_plans.md`, a Pattern
    Index row in `dsa_patterns.md` §6, and a See-Also in `merge_intervals.md`.
18. `theory_of_computation/quantum_and_randomized_complexity.md` (500) — P2.
19. `how_code_runs_compilers_and_interpreters/abis_linking_and_wasm.md` (550) — P2.
20. Roofline / DDR5 / HBM / CXL additions to `computer_architecture_and_memory_hierarchy` — P2.

**Total effort: ~7 developer-days.** Wave 1 is 3 hours and is worth doing on its own — the
PARKED banner is currently the first thing a reader sees on GitHub and in the game reader, and
it tells them the section is unfinished when it is the most finished section in the repo.

**Files needing coordinated updates:** `game/app.js` (`STUDY_ORDER`), `cs_fundamentals/README.md`
(`<!-- study-paths -->` block — fatal under `--strict` in both directions), root `README.md`,
root `CLAUDE.md`, `cs_fundamentals/CLAUDE.md`. Every new file must land in the `study-paths`
block in the same commit.

---

## 7. Confidence and gaps

**High confidence.** The coverage gaps in §1 are grep-verified across all 57 content files, not
inferred — HyperLogLog, linearizability, io_uring, Kolmogorov, BQP, seqlock, and transactional
memory each return literally zero hits section-wide. The structural defects in §4 are read
directly off the heading dumps. The 704-question / 100%-Short figures are live `extract.py`
output, not quoted from a README.

**Medium confidence.** The tier proposals in §3 are judgment against root `CLAUDE.md`'s
senior-vs-principal definition, and the Principal-tier reversal contradicts an explicit
owner-set statement in `cs_fundamentals/CLAUDE.md`. I flagged it as needing a decision rather
than presenting it as a finding, and gave a cheap fallback.

**What I could not verify.**

- **NeetCode 250's exact composition.** Searches for it were rate-limited before I could pull
  the category list, and the one result that touched it did so only as an overlap estimate with
  Striver's sheet. Before rewriting `study_plans.md` §1, fetch `neetcode.io/practice` directly
  and count the current categories — the file's "18 categories" claim is from 2022 and I could
  not confirm it either way.
- **Whether the AI-assisted round has reached companies beyond Meta/Netflix/Google.** My sources
  are interview-prep blogs, not company engineering posts. The Meta detail (E4–E6 as one of two
  coding rounds, E7+ as the only one) is consistent across two independent sources, which is why
  I rated it P0, but re-verify at write time — this is the fastest-moving claim in the report.
- **Per-module depth for the modules I profiled by grep rather than full read.** Every subagent
  I dispatched was refused (global concurrency cap, then a model rate-limit), so §1's per-module
  gap list rests on keyword absence plus targeted reads of `deadlocks_and_synchronization`,
  `study_plans.md`, `interview_execution_playbook.md`, `cryptography_fundamentals` §4.8, and all
  57 heading dumps. Keyword absence is strong evidence a topic is missing and weak evidence about
  how *well* the present topics are covered. If any P0 gets contested, read the module in full
  before arguing.
- **I did not execute any code.** The section's own audit history says that is where its defects
  live. Nothing in this report claims a code block is correct or incorrect.
