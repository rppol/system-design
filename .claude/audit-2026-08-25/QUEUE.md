# Launch queue — 2026-08-25

## Audits: 16 of 17 complete
Done: backend, cs_fundamentals, cuda, database, devops, fastapi, hld, java, lld, llm
(+ llm-SUPPLEMENT), ml, python, spring, technologies
Running: game
**NEVER LAUNCHED (bounced 3x on rate limit): book** — must launch.

## Fixers
Running: database, java, backend, cs_fundamentals, ml, devops, llm, technologies, cuda
Queued: **hld, python, spring, lld, fastapi**

Methodology warning relayed to all 7 fixers running at the time (llm, devops, backend,
database, technologies, java, cs_fundamentals): the `hld` auditor measured its profiling
subagents' staleness flags as **wrong 5 of 7** — one flagged a correct OWASP Top 10:2025
mapping as a fabricated citation, having compared it to the 2021 list. Rule pushed to all:
**delegate the finding, never the verdict**; verify each flag at a primary source; "already
correct" is a valid result; count with `grep -w` (`grep TMA` matches *sof__tma__x*).
Any fixer launched later must be given the same warning in its brief.

### Launch-accounting correction (2026-08-25)
Two batched Agent calls were misread: in each, result #1 was a cap error and result #2 a
success, so `python` and `spring` were recorded as launched when they had not been, and
`technologies` was launched TWICE. Duplicate `a58f54741eaca9f08` was stopped via TaskStop
before it wrote anything (its last message: still doing bank-debris root cause).
`af5334ed7a5f07211` is the surviving technologies fixer.
**Lesson: when batching Agent calls, map results to calls positionally and verify each.**

## soft_skills
Building. 26/26 module dirs + case_studies + README.md + CLAUDE.md on disk.
Root CLAUDE.md already wired by that agent.

## Wave 2 (after wave 1 lands) — SERIALIZE, one agent, never parallel
Every fixer reports a wave-2 list of files it did NOT create. Wave 2 must:
- create those files
- add each to `STUDY_ORDER` in `game/app.js`
- add each to its section README's `<!-- study-paths -->` block with tiers
- update root README + root CLAUDE.md counts
- run `python3 game/extract.py --write-paths` then `--strict`
- run the WHOLE of `scripts/build_banks.sh` (strict passing does NOT mean the script passes)

Known wave-2 owner decisions to put to the owner, NOT to decide unilaterally:
- devops: create a Principal tier (CLAUDE.md currently says none needed)
- cs_fundamentals: create a Principal tier; move `computer_architecture_and_memory_hierarchy` 20 -> 2
- technologies: end the tiering deferral (senior 8 / principal 8) — blocked on the P0 sub-files
- ml: `model_selection_and_algorithm_choice` 47 -> 11
- python: full STUDY_ORDER reshape + 8 tier moves
- spring: `validation_and_error_handling` 19 -> 12; the larger "Phase 4b" restructure
- llm: `coding_agents` + `code_generation` into senior; `agentic_frameworks` add senior
- lld: swap `solid_principles` ahead of `design_principles`
- cuda: `triton_and_kernel_dsls` 21 -> 14 and into senior
- fastapi: `testing_fastapi` 12 -> 7; `api_design_and_versioning` tier fix
- backend: demote `rabbitmq_deep_dive` from senior; promote `kafka_deep_dive` + `rate_limiting_in_depth`
- database: `database_caching_patterns` 25 -> 8 and into senior

## RESOLVED: backend/load_and_performance_testing vs devops/performance_and_load_testing

Both fixers read both files end to end and reported independently. They AGREE on: do not
merge, add reciprocal cross-links, declare the boundary in §1/§2. They DISAGREE on whether
to move the concept layer (coordinated omission, percentiles, open-vs-closed arrival models)
from devops into backend.

**Decision: follow `backend` — cross-links and scope statements only, NO content move.**
Reason: backend's reading is the more granular of the two. It enumerated both §6 structures
and found the intended split already ~80% observed on disk — backend owns measurement
science (what to measure, why the number lies), devops owns the pipeline (running it at
scale, capacity per replica, the CI gate). The genuinely duplicated surface is two items: an
intro k6 script, and Little's Law / open-vs-closed. The same primitive taught from a
fundamentals angle and an application angle is this repo's intended shape elsewhere, not a
defect. devops's own evidence supports this reading — it noted its platform angle is barely
realized (Kubernetes mentioned 3 times), which is an argument for STRENGTHENING the devops
angle, not for stripping its concept layer out.
Wave 2 action: reciprocal cross-link + one-sentence scope statement in each §1. Nothing moves.

## Cross-section items already relayed
- cuda -> ml: `ml/gpu_and_hardware_optimization:34` multiples-of-8 rule false post-cuBLAS 11.0. SENT.
- backend <-> devops: `load_and_performance_testing` near-duplicates. Both told to report, not act.
- ml -> llm: ml may READ `llm/rag_fundamentals/embedding_models.md`, must not edit it.

---

# SESSION-LIMIT STOP — 2026-08-25 (~06:00 IST, resets 06:50)

Build verified GREEN at the stop: `extract.py --strict` EXIT=0, `wiring check: OK`,
12,821 Q&As, 11,730 Shorts (gap 1,091 = book's remaining unmigrated, invariant holds).
The interrupts left NO broken state — fixers make atomic in-place edits.

## Complete (12 of 14 fixers)
cs_fundamentals, devops, technologies, backend, java, ml, database, book, llm, cuda,
lld, hld, python  — plus the soft_skills BUILD (26 modules, 8 case studies, 542 Q&As,
`scripts/build_banks.sh` exit 0).

## INTERRUPTED — resume these
- **`fastapi` fixer** — reached 345 -> 357 Q&As. Was mid case-study rewrite; its last words:
  "1325 lines against the 1100 ceiling. Let me find the fat." Partial work is on disk and
  valid. **Re-launch with the same brief**; tell it 4 of 6 case studies may already be
  rewritten and to check before redoing them.
- **`spring` fixer** — reached 730 -> 769 Q&As. Last words: "Two `## 14` stubs remain.
  Writing real inline case studies for both." Same instruction.
- **`game` audit** — the app.js half is salvaged into `game.md`; the pedagogy/product half
  never ran. Re-launch the full brief; `game.md` records what is already verified so it need
  not be redone.

## The one open question worth prioritising
Distractors are drawn from other answers' FIRST SENTENCES, IDF-ranked. Every non-book Q&A
now has an authored `**Short:**` line (and book gained 365). A Short line is authored to
stand alone as an option among three distractors — exactly the property a distractor needs
and exactly what a truncated first sentence lacks. **Should the distractor pool switch to
Short lines?** Potentially the highest-value MCQ-quality change available. Unevaluated.

## Wave 2 is fully specified in WAVE2.md — DO NOT start it in parallel
It is a single serialized agent. The most dangerous item in it: a sub-file cannot join a
tier its parent module skips, so four of devops's five proposed sub-files would be invisible
to Senior with a green build unless the tier promotions land first.

---

# RESUMED — 2026-08-25, after the session limit cleared

Three agents relaunched in parallel:
- **`fastapi` fixer** — told to `git diff --stat` its section FIRST and check which of the 6
  case studies are already rewritten before touching them. Told the `fastapi/CLAUDE.md` +
  README §5 "generator defect" (they blessed the legacy skeleton as policy) is the
  highest-value edit and must land before any template work can regress.
- **`spring` fixer** — told to find the two thin `## 14` stubs by grepping for short §14
  sections, and to read every already-modified file before re-editing. Given the
  demote-a-`##`-after-§12 trap explicitly.
- **`game` audit (full)** — given `game.md` as already-verified input so the code-health half
  is not re-derived. Its scope is §1 pedagogy (SM-2 vs FSRS, web-researched), §3 UX flow, §6
  execution plan, and **the distractor-pool question as a first-class deliverable** (sample
  the live bank both ways, give the code change, the risks, and how to validate).

All three carry the "delegate the finding, never the verdict" warning and the wave-1
content-only boundaries.

## Wave-2 decisions: 8 taken here, 5 put to the owner

**Taken (single-section, mechanical, recommended with reasoning by the agent that read the
whole section — these are routine judgment calls, not policy):**
`ml` 47->11 · `spring` 19->12 · `llm` coding_agents + code_generation into senior,
agentic_frameworks add senior · `lld` solid_principles ahead of design_principles ·
`cuda` triton_and_kernel_dsls 21->14 + senior · `fastapi` testing_fastapi 12->7 +
api_design_and_versioning tier fix · `backend` demote rabbitmq_deep_dive, promote
kafka_deep_dive + rate_limiting_in_depth · `database` database_caching_patterns 25->8 + senior.

**Put to the owner** — each one either reverses a written statement in a CLAUDE.md, changes a
repo-wide bar, or commits to a large body of new work:
devops Principal tier · cs_fundamentals Principal tier · technologies tiering deferral ·
book DDIA 2e strategy · the root CLAUDE.md 900-1100 -> 500-800 case-study line bar.

## SERIALIZED on owner instruction — one agent at a time

Order: **fastapi fixer -> spring fixer -> game audit -> wave 2.**

Stopped mid-flight to enforce it (both had partial work; neither left broken state):
- **spring fixer** — had already located the two thin `## 14` stubs and finished one.
  Its last words: *"Now writing the `spring_events_and_scheduling` §14."* So on relaunch:
  the OTHER stub is done, `spring_events_and_scheduling` §14 is the one to write.
- **game audit** — writes its report only at the end, so nothing landed on disk. But it had
  already reached a verdict on the distractor-pool question and called it *"evidence-backed"*
  before starting the pedagogy half. Relaunch with the same brief; it will re-derive.

`fastapi` was left running as the first in the series.

## 2026-08-26 — weekly limit cleared, fastapi relaunched

Build verified green at the restart: `extract.py --strict` EXIT 0, `wiring check: OK`,
12,816 questions + 1,611 case-study questions, **Shorts 11,731 of 12,822 — gap exactly 1,091 =
book's remainder.** The invariant has now survived four separate agent kills.

**fastapi is nearly done.** Measured, not assumed: 22 files modified (+5492/-2098), section at
358 Q&As (from 345), and **all 6 case studies are already on the 11-section template** (12 `## `
headings each). Only the line bar is outstanding:

| Case study | Lines | vs 900-1100 |
|---|---|---|
| design_realtime_chat_fastapi | 982 | OK |
| design_async_web_scraper | 1065 | OK |
| design_ml_inference_api_fastapi | 1099 | OK |
| design_rate_limited_api_fastapi | 1100 | OK |
| design_async_task_queue | 1106 | 6 over, trivial |
| **design_multi_tenant_saas_api** | **1294** | **the one real item** (was 1325; mid-trim when killed) |

Relaunched with that measured state in the brief so it cannot re-rewrite five finished files.

### Four orphaned subagents were RESCUED this session — read them before relaunching spring/game
Stopping a parent does not stop its children; they report to the orchestrator instead, and that
output is invisible to any relaunch unless written down.
- `spring-SUPPLEMENT-framework7-verification.md` — Framework 7 / Boot 4.1 + Spring Batch 6.
- `game-SUPPLEMENT-route-and-ux-map.md` — the full router/screen/empty-state map (most of the
  game audit's §3), plus the px->rem conversion fully scoped.

**Combined scoreboard: 3 audit claims contradicted outright** — a Spring GraphQL "trusted
documents" feature that does not exist, JSR-352 removal misattributed to Batch 6.0 (it was 5.0),
and "Framework 7 made CGLIB the default" (core Framework still defaults to interface proxies).
The recurring shape is **deprecated-vs-removed**: one word apart, reads identically to a skimming
author, and inverts the advice.

### Tooling note (hit for real this session)
`game/extract.py` is at `src/main/java/com/rutik/systemdesign/game/extract.py`, NOT the repo
root. Running it from the root fails with `No such file or directory` **and the shell reports
EXIT=0**, so a careless check reads as a pass. Always `cd src/main/java/com/rutik/systemdesign`
first, and read the actual output rather than the exit code alone.

---

# WAVE 1 COMPLETE — all 14 fixers landed (2026-08-26)

`fastapi` 358 Q&As (6/6 case studies on template, all inside 900-1100).
`spring` 773 Q&As (2 new §14s, README's CGLIB claim corrected, 4 audit corrections DECLINED).
Build green throughout: `wiring check: OK`, 12,826 Q&As, 11,735 Shorts, gap 1,091 = book exactly.

# WAVE 2 RESTRUCTURED — phase A (parallel content) / phase B (serial wiring)

Owner asked for max parallelism. Wave 2 splits on the same content-vs-inventory boundary wave 1
used: **rewriting or creating a file's CONTENT is independent; changing the INVENTORY is not.**

**Phase A — 14 agents running concurrently**, one per section plus DDIA 2e. Contract in
`WAVE2-CONTENT-BRIEF.md`. They WRITE FILES and are forbidden to wire them: no `game/app.js`, no
`<!-- study-paths -->` block, no generated tier table, no README/CLAUDE.md, no
`case_studies/case_studies.md`, no `--write-paths`.

**THE TREE IS EXPECTED TO BE RED BETWEEN PHASES.** A file on disk absent from its study-paths
block is FATAL under `--strict` BY DESIGN — that is the inventory guard working. Phase A agents
are explicitly told not to run `--strict` and not to report it as a failure. Do not "fix" it
between phases; phase B closes it.

**Phase B — ONE serialized agent** consumes phase A's reports: creates the `STUDY_ORDER`
entries, the study-paths lines, the tier promotions, the README/CLAUDE.md rows, the
`case_studies.md` five-part updates, then `--write-paths`, `--strict`, and the WHOLE of
`scripts/build_banks.sh`.

## Cross-section boundary decided at launch, to stop two agents writing the same file
`lakehouse_and_table_formats` (database) and `apache_iceberg` (technologies) were BOTH proposed.
Split, following the `backend`/`devops` load-testing precedent: **database owns the vendor-neutral
CONCEPT** (table formats, ACID on object storage, Iceberg vs Delta vs Hudi); **technologies owns
the PRODUCT deep dive** (spec versions, catalogs, maintenance, engine integrations). Each agent
was told to cross-link and NOT to write the other's file.

## Per-agent hazards briefed (each is a silent, green-build failure)
- **devops**: 4 of 5 sub-files have Full-only parents. Ordered to report each parent's CURRENT
  tier, read off disk, and flag the mismatch loudly. Principal tier is phase B's job.
- **spring**: `spring_ai.md` is tier `-` but both its new sub-files need `senior` — same trap.
- **cs_fundamentals**: 3 of 4 files partly pre-empted by wave 1 — MOVE and stub, do not
  re-derive. `memory_models_and_concurrency_theory` has THREE inbound links to repoint.
- **ml**: 2 of 3 are extractions from existing §4.4 / §4.7, not fresh writes.
- **fastapi**: `asgi_runtimes_and_free_threading` is pre-empted — re-scope or drop, do not
  duplicate. Also told never to run a blank-line normalizing sweep (the 149-line revert).
- **technologies**: no tiering (deferred), never name a file `*_deep_dive.md`/`*_internals.md`,
  never author a bank record to silence an orphan warning.
- **lld/hld**: the 217-Q&A heading loss and the 45-Q&A numbered-list loss, verbatim.
- **python**: its README taught three wrong conventions; write from root CLAUDE.md. And do not
  invent pyperformance figures — CPython publishes none past 3.11.
- **book/DDIA 2e**: establish the real 2e ToC from a primary source FIRST and stop if it cannot
  be established. `book/` is answerable to its source, NOT to 2026 reality — the
  REPLACE-DO-NOT-ANNOTATE rule is INVERTED here.
