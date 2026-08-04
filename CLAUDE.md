# CLAUDE.md — System Design Repository

## What This Repo Is

A comprehensive system design study repository. All content is Markdown — no runnable application.

| Section | Coverage | Sub-CLAUDE |
|---------|---------|-----------|
| **LLD** | Design patterns (GoF), SOLID, anti-patterns | [lld/CLAUDE.md](src/main/java/com/rutik/systemdesign/lld/CLAUDE.md) |
| **HLD** | Distributed system concepts (CAP, caching, queues, sharding) | [hld/CLAUDE.md](src/main/java/com/rutik/systemdesign/hld/CLAUDE.md) |
| **Backend** | Networking, API design, performance, resilience, security, microservices — 34 modules, 5 case studies | [backend/CLAUDE.md](src/main/java/com/rutik/systemdesign/backend/CLAUDE.md) |
| **Database** | Relational, NoSQL, distributed DB, production ops — 29 modules, 6 case studies | [database/CLAUDE.md](src/main/java/com/rutik/systemdesign/database/CLAUDE.md) |
| **Java** | Pure Java senior-engineer guide — 33 modules, 8 case studies | [java/CLAUDE.md](src/main/java/com/rutik/systemdesign/java/CLAUDE.md) |
| **Spring** | Spring Framework guide — 39 modules, 9 case studies | [spring/CLAUDE.md](src/main/java/com/rutik/systemdesign/spring/CLAUDE.md) |
| **Python** | Pure Python senior-engineer guide — 21 modules, 0 case studies | [python/CLAUDE.md](src/main/java/com/rutik/systemdesign/python/CLAUDE.md) |
| **FastAPI** | FastAPI + ASGI production guide — 19 modules, 6 case studies | [fastapi/CLAUDE.md](src/main/java/com/rutik/systemdesign/fastapi/CLAUDE.md) |
| **ML** | Machine Learning guide — 45 modules, 24 case studies | [ml/CLAUDE.md](src/main/java/com/rutik/systemdesign/ml/CLAUDE.md) |
| **LLM** | LLM engineering guide — 53 modules, 29 case studies, 85 deep-dive sub-files (75 module + 10 cross-cutting) | [llm/CLAUDE.md](src/main/java/com/rutik/systemdesign/llm/CLAUDE.md) |
| **DevOps** | DevOps / Cloud / Platform guide — 41 modules, 13 case studies | [devops/CLAUDE.md](src/main/java/com/rutik/systemdesign/devops/CLAUDE.md) |
| **CUDA** | GPGPU / CUDA programming guide (kernel-author viewpoint) — 24 modules, 6 case studies | [cuda/CLAUDE.md](src/main/java/com/rutik/systemdesign/cuda/CLAUDE.md) |
| **CS Fundamentals** | Language-agnostic CS spine — 24 modules, 6 case studies + DSA pattern playbooks sub-section (25-pattern recognition engine, interview execution playbook, Blind 75/NeetCode 150 study plans — complete; wired as its own Study topic (`cs_fundamentals/dsa_patterns`) in the game) | [cs_fundamentals/CLAUDE.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/CLAUDE.md) |
| **Technologies** | Per-technology deep dives (Apache Airflow, Temporal, NVIDIA Triton Inference Server, Intel OpenVINO) — 4 modules | [technologies/CLAUDE.md](src/main/java/com/rutik/systemdesign/technologies/CLAUDE.md) |
| **Book** | Chapter-by-chapter book summaries (book-faithful chapter template, not the 14-section module template; no line ceiling) — DDIA (Kleppmann) 12+preface; SDI Vol 1 (Xu) 16; SDI Vol 2 (Xu & Lam) 13; ML System Design Interview (Aminian & Xu) 11; Designing ML Systems (Huyen) 11; Understanding Distributed Systems (Vitillo) 33 chapters as 5 part-folders. Game shows one Study node per book (`BOOK_LABELS` + `STUDY_ORDER.book` in game/app.js) | [book/CLAUDE.md](src/main/java/com/rutik/systemdesign/book/CLAUDE.md) |
| **Game** | **LORA — Learn Often, Recall Always**, by Rutik (Pages-deployed daily learning game; a static SPA, NOT 14-section content — template-exempt). 5-min MCQ blitz auto-built from all sections' Q&As via `extract.py`; SM-2 spaced-repetition review, daily sealed Gauntlet, and an in-app coach; `localStorage` is the single source of truth (no server). Also ships as a fully-offline sideloadable Android APK — built and released by CI on every push; see `android/`. | [game/CLAUDE.md](src/main/java/com/rutik/systemdesign/game/CLAUDE.md) |

---

## Repository Structure

```
src/main/java/com/rutik/systemdesign/
├── lld/          ← Design patterns + CLAUDE.md
├── hld/          ← System design concepts + CLAUDE.md
├── backend/      ← Backend engineering + CLAUDE.md
├── database/     ← Database engineering + CLAUDE.md
├── java/         ← Pure Java guide + CLAUDE.md
├── spring/       ← Spring Framework guide + CLAUDE.md
├── python/       ← Pure Python guide + CLAUDE.md
├── fastapi/      ← FastAPI + ASGI guide + CLAUDE.md
├── ml/               ← Machine Learning guide + CLAUDE.md
├── llm/              ← LLM engineering guide + CLAUDE.md
├── devops/           ← DevOps / Cloud / Platform guide + CLAUDE.md
├── cuda/             ← GPGPU / CUDA programming guide + CLAUDE.md
├── cs_fundamentals/  ← CS Fundamentals guide + CLAUDE.md
├── technologies/     ← Per-technology deep dives + CLAUDE.md
├── book/             ← Book summaries (chapter-by-chapter) + CLAUDE.md
└── game/             ← Browser learning game (app, not study content) + README.md
```

### File-naming rule (owner-set 2026-07-30, commit 0af2022 — 516 files renamed)

**Every content page is named for the folder that contains it.** There is no `README.md`
anywhere below a section root.

| File | How many | What it is |
|------|----------|-----------|
| `<section>/README.md` | exactly 16, one per section | The section INDEX — module table, learning paths, study plan, build manifest. Orchestration only; never a module page, never shown as content in the game UI. |
| `<section>/CLAUDE.md` | one per section | Agent rules for that section. Not study content; not parsed by `extract.py`. |
| `<section>/<module>/<module>.md` | every module | The MODULE PAGE. Carries the `<!-- study-paths -->` block. |
| `<section>/<module>/<subfile>.md` | optional | Deep-dive sub-file, free-named, groups under its parent module. |
| `<cat>/<pattern>/<pattern>.md` | nested pattern | Same rule one level deeper (`lld/behavioral/observer/observer.md`). |
| `<section>/case_studies/case_studies.md` | one per section with case studies | The case-study INDEX (5-section learning path + tier markers). |
| `<section>/case_studies/<name>/<name>.md` | directory-shaped case study | Flat case studies stay `<name>.md`. |
| `book/<book>/<NN_chapter>/<NN_chapter>.md` | book chapter | Same rule; the per-book index is `book/<book>/<book>.md` and only `book/README.md` is a README. |
| `technologies/tech_bank/*.md` | one taxonomy + 18 tier shards | The technology KNOWLEDGE BANK — authored **data**, not a module. Source of the generated `game/tech_index.json`. Excluded from both `extract.py` walks by exact path; carries records, never the 14-section template. See `technologies/CLAUDE.md`. |

So: **one `README.md` per section (the index), one `CLAUDE.md` per section (agent rules),
and every content page named for its folder.** `extract.py` resolves a module page with
`_module_page(module)` = `basename(module) + ".md"` — it never looks for a README below a
section root, and a module page under any other name is invisible to the bank and fails
`--strict`. When linking a module from prose, link the PAGE
(`../advanced_rag/advanced_rag.md`), not the directory — a bare directory link used to work
only because GitHub falls back to a folder's README, and there is none any more.

---

## Scope — 15 sections in scope; only `book/` is still out (owner-set 2026-08-04)

`devops/`, `cs_fundamentals/` and `cuda/` were parked on 2026-07-29 and **re-opened on
2026-08-04**. Every improvement pass now applies to them: the factual audit, the
`**Short:**` MCQ-summary migration, modernization, and new prose or Q&As.

**`book/` stays out, and its exclusion does not expire.** The owner's reasoning: the other
three are *educational modules*, answerable to reality; `book/` is a chapter-by-chapter
summary of six specific books, answerable only to its sources. A currency audit is not
deferred there, it is the wrong test — "correcting" a 2017 DDIA claim to 2026 reality makes
the summary wrong about the book, and REPLACE-DO-NOT-ANNOTATE inverts into fabrication
attributed to a named author. See `book/CLAUDE.md` for what a fidelity pass would ask
instead, and for why the `**Short:**` migration is a genuinely separate, still-open
question there.

| Section | Was parked | State at unparking | Now |
|---------|-----------|--------------------|-----|
| `devops/` | 2026-07-29 | 27 audit units none started; 650 Q&As, 0 Short | **in scope** |
| `cs_fundamentals/` | 2026-07-29 | 24 audit units none started; 701 Q&As, 0 Short | **in scope** |
| `cuda/` | 2026-07-29 | 5 of 19 units audited; 427 Q&As, 0 Short | **in scope** |
| `book/` | 2026-07-28 | never in the audit manifest; 1,402 Q&As, 0 Short | **still out** |

**Report against these numbers.** The audit denominator is **295 units** (`book/` was never
in it). The Short-migration denominator is **9,503 Q&As** — every parsed Q&A except
`book/`'s 1,402. The old parked-era figures (230 units, 8,489 Q&As) are dead; do not quote
them, and do not carry forward a worklist built against them.

**The `cuda` trap, for whoever audits it.** Units 48-52 are genuinely audited and committed
(`b223d21`). Units 53-57 have an **interrupted partial batch** committed as `2fca64f`: five
files carry real corrections, but the agent was stopped mid-§14 and never produced a verdict
record, so those units are deliberately still `status != done`. **Re-audit those five files
from the top** — treat that commit as a partial improvement, never as an audited result.

Working state (`audit_manifest.json`, per-unit `status`) lives in the session scratch
directory, which is ephemeral. This table is the durable record; if the manifest is gone,
rebuild it from here and from the `audit(...)` commit messages, which each name their units.

---

## The 14-Section Module Template

Every module page (`<module>/<module>.md`) must follow this exact structure:

```
## 1. Concept Overview
## 2. Intuition           (one-line analogy, mental model, why it matters, key insight)
## 3. Core Principles
## 4. Types / Architectures / Strategies
## 5. Architecture Diagrams    (ASCII art — no image files)
## 6. How It Works — Detailed Mechanics   (code, pseudocode, concrete numbers)
## 7. Real-World Examples
## 8. Tradeoffs            (comparison tables)
## 9. When to Use / When NOT to Use
## 10. Common Pitfalls     (production war stories)
## 11. Technologies & Tools
## 12. Interview Questions with Answers   (15+ Q&As, ordered by interview frequency — gotchas and traps first, then internals, then edge cases; bold Q, plain A)
## 13. Best Practices
## 14. Case Study
```

---

## Case Study Templates

### 11-Section Principal Template — Standard for ALL case studies
```
## Intuition
## 1. Requirements Clarification
## 2. Scale Estimation
## 3. High-Level Architecture
## 4. Component Deep Dives
## 5. Design Decisions & Tradeoffs
## 6. Real-World Implementations
## 7. Technologies & Tools
## 8. Operational Playbook
## 9. Common Pitfalls & War Stories
## 10. Capacity Planning
## 11. Interview Discussion Points
```

**Principal case study quality bar:** 900–1100 lines; 4+ cross_cutting/ references; executable code in §4; concrete numbers everywhere; broken→fix example in §4; named companies in §6; quantified impact in §9; 10+ Q&As in §11.

---

## Case Study Learning Path (case_studies/case_studies.md)

Every `case_studies/` directory MUST contain a `case_studies.md` index with these 5 sections:

1. **Quick Start** — 3 files to read first, with one-sentence justification each
2. **Full Learning Path** — all case studies grouped by engineering concern; file link + concern phrase + 1–2 sentence summary
3. **Cross-Cutting / Shared Primitives** — table mapping each cross_cutting/ sub-file to the phase where it becomes relevant
4. **Dependency Map** — ASCII tree showing which case studies build on others
5. **Interview Prep Shortcuts** — table mapping "design X" questions to best case study file

**Maintenance rule:** Update this index every time a new case study is added — same commit, no exceptions.

---

## Interview Q&A Rules

- **Bold the question**, plain text the answer
- **Prefix every question with `Q: `** inside the bold, i.e. `**Q: <question>?**` (the LLM-section convention standardized repo-wide in ae92f65). The leading `Q: ` is display-only — `extract.py` strips it when building the MCQ bank, so it never leaks into the game. New sections MUST adopt this from the start (CUDA missed it because it was authored after the sweep).
- First sentence = direct answer; following sentences = mechanism / example / gotcha; final sentence = practical guidance
- **Minimum 15 Q&As per module** — this is a hard floor; see section CLAUDE.md for modules that require 18+
- **Order by interview importance**: highest-frequency gotchas and traps first, then internal mechanics, then edge cases and advanced scenarios last
- **The answer must be current, not historical.** Every API, flag, version, model, product
  and spec named in an answer is the one that is live today — the same REPLACE, DO NOT
  ANNOTATE rule that governs module prose (see "Modernization" below). An answer never
  says "`foo()` is deprecated, use `bar()`"; it says `bar()`. Three narrow exceptions:
  a broken→fixed pair where the old form IS the lesson, an answer whose explicit subject
  is a migration, and **deprecation-as-subject** — an answer teaching the *discipline*
  of handling deprecation (surviving a vendor retirement, versioning your own API, a
  published lifecycle policy, a `deprecated` enum value) is content and stays.

### Case-study Q&As are NEVER part of the quiz (owner-set 2026-07-28)

**Both forms are excluded, repo-wide:** a dedicated file under `case_studies/`, and a
`## N. Case Study` section inside a module page or sub-file. A case study's Q&As are
discussion prompts tied to one scenario — they do not stand alone as MCQ items, and the
distractor pool would be drawn from an unrelated scenario.

`extract.py` already satisfies this: `case_studies/` paths are skipped outright, and an
in-file Q&A span opens only on a heading matching `interview q` and closes at the next `##`,
so anything parked under `## N. Case Study` falls outside it. **182 in-file case-study Q&As
(llm 99, hld 83) are correctly excluded today — that is the rule working, not a bug.**

The consequence for authors: **do not "rescue" case-study Q&As by moving them under the
interview heading**, and do not file a bug when a `## N. Case Study` Q&A is missing from the
bank. If a question is genuinely a general interview question, it belongs in
`## 12. Interview Questions with Answers` on its merits, not because it was hidden.

**What IS a real loss** is a Q&A under a mislabeled *interview* heading — see the game
compatibility section below. Measured 2026-07-28 at **229, 223 of them in `lld`**;
**fixed 2026-07-29 (commit 18e2328), recovering 217 and taking `lld` from 198 to 415.**
Measure these directly (bold `**Q:` lines outside any `interview q` span, excluding
`case_studies/` and `## N. Case Study` sections); a scan for modules with a ZERO question
count cannot see a partial loss inside an otherwise-healthy module.

Not every mislabeled-looking heading is a loss. `## Interview Relevance` in the five
`lld/anti_patterns/` files was deliberately left alone: it sits over bullet lists of
question *prompts* with no answers, so renaming it would inject answerless fragments into
the bank. **Read the content before renaming a heading** — the heading is the trigger, the
content is the test.

### Authored one-line summaries — ACTIVE since 2026-07-29

`game/extract.py` and the reader now understand an authored `**Short:**` line, so these
may be written. The migration runs section by section, llm first.

**Why.** Without one, the MCQ option is derived by taking the answer's first sentence and
trimming it at a clause boundary past 220 chars (`make_short()`). That produces a weak or
mangled option whenever the first sentence is long, opens with a code fence, or is not
self-contained. An authored line replaces the derivation; the full answer is untouched and
is still what the quiz reveals after answering.

**The format** — the `**Short:**` line goes directly under the question, no blank line
needed (the reader treats it as its own paragraph either way):

```
**Q: <question>?**
**Short:** <one self-contained sentence, 15–220 chars, no code fence, no list>

<the full answer — mechanism, example, gotcha, guidance — unchanged>
```

**Rules for the line itself.** It must stand alone as an answer to the question with no
surrounding context, since it is read as a bare option among three distractors. 15–220
characters — `extract.py --strict` FAILS THE BUILD outside that range, because an
unbounded authored line ships as the option either way. No code fence, no list, no
trailing "see below". Do not restate the question. Prefer the claim over the hedge.

**Rules for everything around it.** The line must be the FIRST non-blank line of the
answer — anywhere else it is treated as ordinary prose and left alone, which is what stops
the parser silently deleting a sentence that happens to start that way. Never edit the
full answer while adding one; if the answer's first sentence was doing the job, the
`**Short:**` line may simply restate it more tightly. `answerFull` must never lose a word.

**It is per-Q&A opt-in.** A file with no `**Short:**` lines keeps working exactly as
before, so a section can be migrated in any order. `extract.py` reports migration progress
("authored **Short:** summaries: N of M parsed Q&As"). Until a Q&A has one, the existing
rule still binds for it: the first sentence must be a self-contained direct answer of
15–220 characters — not because a longer one is dropped (it is not; see the extraction
rules below) but because it is trimmed at a clause boundary and ships as the option.

**Measure migration coverage per parsed Q&A, never per file.** A file-level count read
llm as complete at 125 of 127 files while `vllm_deep_dive/vllm_deep_dive.md`'s 18 Q&As had no
summaries at all — the per-Q&A count (2054 of 2072) was the one that saw it.

### ORDER: audit a version-sensitive section BEFORE migrating its Shorts (owner-set 2026-08-04)

**A `**Short:**` line written against an answer the audit later corrects becomes a wrong
answer that ships as the CORRECT option.** This is not merely rework. The audit agent edits
the answer body; the stale summary sitting above it now contradicts it; and nothing catches
that — the line is still 15–220 chars, still parses, still passes `--strict`. It is the
same shape as every other silent-loss bug in this repo: a wrong value indistinguishable
from a right one.

So sequence by **how version-sensitive the content is**, not by which pass is cheaper:

| Content | Order | Why |
|---------|-------|-----|
| Named products, APIs, versions, cloud services, CLI flags (`devops`, `technologies`, much of `llm`/`ml`) | **audit, then Short** | the audit will rewrite answers; a Short written first is dead on arrival |
| Timeless theory — complexity, automata, number representation, OS primitives (`cs_fundamentals`) | Short first is safe | modernization has almost nothing to change |
| Mixed (`cuda`: stable warp/memory semantics, volatile toolkit and Nsight specifics) | audit the un-audited units first | judge per unit, not per section |

**And regardless of order, the audit owns the Short line.** Any pass that changes an answer
MUST re-read the `**Short:**` line above it and update it in the same edit. Add this to
every audit prompt — it is the one place a correct fix can introduce a new defect.

**In the reader** the line is hidden by default and revealed by the Summaries row in the
typography popover (`sd_reader_short`). It is never part of `answerFull` and never
appears in the quiz as anything but the option.

---

## Content Quality Standards

- **Show broken code, then the fix** — DCL without volatile, HashMap concurrency, self-invocation, N+1
- **Concrete numbers everywhere** — virtual thread ~few KB stack; platform thread ~1MB; HashMap capacity 16; load factor 0.75; ArrayList grows 1.5×; G1 pause 200ms; ZGC sub-1ms; HikariCP default pool 10; Tomcat default threads 200; BCrypt cost 10–12
- **Production war stories** in Common Pitfalls — real incident patterns, not toy examples
- **No emojis** in any file
- **Effective Java item references** where applicable (Java section only)

---

## How to Add a New Module

1. Create `<section>/<module_name>/<module_name>.md` — 14-section template. The page is
   named for its folder; a `README.md` here is invisible to `extract.py`
2. Meet the Q&A minimum (see section CLAUDE.md for specifics)
3. Write the `<!-- study-paths -->` block at the top of the new page (it must list
   `<module_name>.md` on every tier line it declares)
4. Add the module dir to `STUDY_ORDER["<section>"]` in `game/app.js` at its learning-path position
5. Update the section's master `README.md` module table
6. Update root `README.md` table
7. Run `python3 game/extract.py --write-paths` (regenerates the section README's tier
   tables), then `python3 game/extract.py --strict` to confirm the wiring
8. See the section's `CLAUDE.md` for section-specific steps

### Adding a case study
- Write the file following the section's template — flat as `case_studies/<name>.md`, or
  directory-shaped as `case_studies/<name>/<name>.md`
- Update the section's master `README.md` case study table
- **Update `case_studies/case_studies.md`** — add to correct phase, update dependency map, add interview prep row
- Update root `README.md` and the section's `CLAUDE.md` case study list

---

## Reference Files

| File | Purpose |
|------|---------|
| `llm/foundations_and_architecture/foundations_and_architecture.md` | Gold standard 14-section format |
| `llm/case_studies/design_gpu_inference_platform.md` | Gold standard 11-section principal case study |
| `java/concurrency/concurrency.md` | Example of 15+ Q&A deep module |
| `spring/spring_transactions/spring_transactions.md` | Example of 18+ Q&A deep Spring module |
| `llm/agentic_frameworks/langchain_and_lcel.md` | Example deep-dive sub-file (15+ Q&As) |

---

## Formatting Rules

- Diagrams are **appeal-first** (owner policy, 2026-07-02): use the most visually appealing renderable form that conveys the information accurately. In study section files that means the **Mermaid diagram family is preferred** (flowchart, sequenceDiagram, stateDiagram-v2, xychart-beta, pie, quadrantChart, timeline, sankey-beta — all render on GitHub and in the game reader). ASCII remains for shapes Mermaid cannot draw (constraint grids/masks, alignment-critical layout maps, vector geometry). No image files. Use `/mermaid-diagrams` skill to decide; see "Mermaid Diagrams" section below.
- Tables use standard Markdown pipe syntax
- Code blocks use triple backticks with language tag (` ```java `, ` ```sql `, ` ```yaml `, etc.)
- Section headers follow exact numbering: `## 1.`, `## 2.`, ... `## 14.`
- Use `---` horizontal rules to separate major sections
- Links between modules: use relative paths to the module PAGE, e.g., `[Concurrency](../concurrency/concurrency.md)` — never to the bare directory (there is no folder README to fall back to)

---

## Visual Intuition Diagrams

Section 5 of every module (Architecture Diagrams) — and any place a concept is
hard to picture — should use a **visual intuition diagram**: ASCII art that makes
an abstract relationship *physically visible*. The gold standard is the causal-mask
grid and the sliding-window before/after pair in
`llm/foundations_and_architecture/foundations_and_architecture.md`.

**Skill:** run `/visual-intuition-diagrams` (at
`.claude/skills/visual-intuition-diagrams/`) to generate or validate these. It
ships a validator/previewer — author a diagram, then run it through the driver
before committing:

```bash
# Lint diagram blocks (tabs, trailing whitespace, emoji, >100-col width); accepts files or dirs
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py check <path-or-dir>
# Print one block under a column ruler to eyeball alignment
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py preview <file.md> <index>
```

**Pick the archetype that matches the concept's shape:**

| Archetype | Use when the concept is… | Examples |
|-----------|--------------------------|----------|
| Constraint / value grid | a relationship across two axes (X×Y) | causal mask, ALiBi bias, sliding window; numeric grid + max column (ColBERT MaxSim) |
| Before/after + delta | a quantified win | KV-cache reduction, MLA compression; score-scale mismatch (cosine vs BM25) |
| Side-by-side / stacked flow | a placement or phase difference | Pre-LN vs Post-LN, prefill vs decode; image-index strategy (text-space vs CLIP joint space) |
| Routing / fan-out / tree | one input selecting among paths, or a hierarchy/DAG | MoE experts, router/cascade; Leiden community levels, query-decomposition DAG (use indented `├─ └─` trees) |
| Bar chart | comparing magnitudes (a *ratio*, not two stated numbers) | softmax temperature, attention-sink weights |
| Curve / vector / number-line | a trend, geometry, or partitioned axis | "lost in the middle", embedding/cosine-angle sketch; threshold bands (CRAG 0.3/0.7) |

**Appeal-first note (2026-07-02):** the *Bar chart* and *Curve / number-line*
archetypes should now normally be authored as Mermaid `xychart-beta` (see the
Mermaid section); the ASCII forms here remain for the grid, before/after,
side-by-side, and tree archetypes where character alignment carries the meaning.

**A diagram must earn its place — audit before adding.** When a module is *already*
dense with diagrams, almost all are pipeline/data-flow pictures; do not add another.
The real gaps are the **math and decision mechanics still trapped in formulas, prose,
or code** — an arithmetic rule (RRF `1/(k+rank)`), a threshold (CRAG buckets), a scale
mismatch, a hierarchy/dependency structure. Skip any diagram that merely restates a
two-number table the sentence already gives.

**Conventions (enforced by the validator):** ASCII only, fenced block with **no
language tag**; spaces not tabs; no trailing whitespace; **no emojis** (use `✓`/`✗`,
not `✅`/`❌`); widest line ≤ 100 cols (prefer vertical stacking over wide
side-by-side); caption every diagram with 1–2 sentences tying it to the insight and
reuse numbers already in the surrounding text.

**The no-tabs rule governs DIAGRAMS and prose, not data whose format requires a tab.**
`database/sql_query_optimization/sql_query_optimization.md` carries tab-delimited rows inside a
`COPY ... FROM stdin` example because that is COPY's default text format — converting
them to spaces would make the example wrong. A tab inside a code fence whose language
genuinely uses tabs is content; the fence carries a note saying so. Check before
"fixing" a tab; everywhere else it is still a defect.

---

## Mermaid Diagrams

**Appeal-first policy (owner-set 2026-07-02, supersedes the old ASCII-first rule):**
pick the diagram type whose *topology* matches the concept — flowchart for directed
flows, sequenceDiagram for actor chains, stateDiagram-v2 for lifecycles,
xychart-beta for magnitude comparisons and trends, pie for proportions,
quadrantChart for two-axis tradeoffs, timeline for evolution, sankey-beta for flow
volumes. Keep ASCII only for constraint grids/masks, alignment-critical layout
maps, and vector geometry, which Mermaid cannot draw.

**Four style rules (owner-set 2026-07-07) — every Mermaid diagram must satisfy all four:**
(1) **Colour every node** with the One-Dark `classDef` palette, semantic-by-role — the
reader's grey auto-tint is a fallback for legacy diagrams, not the target.
(2) **Horizontal-first** — default to `flowchart LR`; use `TD`/`TB` only for genuinely
vertical hierarchies/lifecycles or when an `LR` row would overflow (then use `subgraph`s).
(3) **No spillover text** — short labels, wrap with `<br/>`; don't rely on the reader's
auto-widen of under-measured boxes.
(4) **Rounded corners** — the reader rounds every box; for GitHub parity prefer rounded node
shapes `(label)`/`([label])`, and never hand-set a light fill or a square-corner override.

**`\n` in a node label is NOT a bug — do not sweep it (tested 2026-08-04).** `<br/>` is the
house style under rule 3, but **3,381 existing labels use a literal `\n`** instead (ml 1305,
llm 1068, spring 417, java 327, cuda 264). An audit agent flagged these as possibly
rendering the literal escape. They do not: rendered through the pinned `mermaid@11.16.0`
with the repo's exact config (`htmlLabels: false`), `\n` and `<br/>` produce the same label
text and **neither leaves a `\n` anywhere in the SVG**. Use `<br/>` in new diagrams for
consistency; never open a mass rewrite of the existing ones, and do not re-flag this.

**Product logos (icon nodes) — owner-set 2026-07-24.** A node standing for a **concrete
product or service** (S3, Lambda, Kafka, Redis, PostgreSQL, Kubernetes, PyTorch, …)
should render its **real logo** via a Mermaid icon node:
`n@{ icon: "logos:aws-s3", form: "square", label: "S3", pos: "b", h: 44 }` (or an
`architecture-beta` diagram for a pure deployment topology). Icons come from the
**bundled** iconify `logos` pack (`game/vendor/icons-<name>.json`, built by
`scripts/build_icons.mjs`, registered offline — never the iconify CDN) and bake into the
pre-rendered `.mmz`. Rules: (a) **every icon node MUST carry a text `label`** — GitHub
does not register icon packs, so on GitHub the icon degrades to a placeholder but the
label still reads; (b) icons are for **concrete products only** — conceptual boxes
("Rate limiter", "Read path") keep the One-Dark `classDef` palette; (c) find the id in
the pack (2091 `logos:` ids, all 64 `aws-*` services) — an unknown id degrades to a
placeholder. This is additive to the four rules above, not a replacement.

**Skill:** run `/mermaid-diagrams` (at `.claude/skills/mermaid-diagrams/`) before
authoring or converting any diagram. The skill contains the full decision table
(which form for which shape), the One-Dark color palette and `classDef` block, the
ASCII→Mermaid conversion guide, and gotchas (stale `readerCache`, `classDef`
ordering, `data-processed`, square brackets inside labels).

**When to invoke `/mermaid-diagrams` automatically:**
- Asked to "convert this ASCII to Mermaid" or "make this diagram colorful"
- About to write a ```` ```mermaid ```` fence in any study file
- Unsure whether a concept's diagram should be Mermaid or ASCII

**Scope:** Mermaid fences are valid only in study section files (under
`src/main/java/com/rutik/systemdesign/<section>/`). Do not add mermaid fences to
CLAUDE.md files, skill files, or the `game/` tooling directory.

**The game reader renders Mermaid** (`game/app.js` → `renderMermaid()` → CDN lazy
import). GitHub renders mermaid fences natively. Both surfaces work without a build step.

**Reader post-processing is already wired** — `renderMermaid()`/`mmRenderNode()`
round ALL diagram boxes (flowchart nodes, sequence actors/notes, frames, timeline
periods — chart data marks exempt; Mermaid has no border-radius themeVariable),
match the sequence renderer's measurement fonts to the display font AND widen
under-measured note/actor rects so text never spills onto the canvas, color
`<marker>` arrowheads blue (they ignore `lineColor` themeVariable — SVG `<defs>`
elements must be patched via `setAttribute`), make edge label backgrounds
transparent, and add a fit-to-width button on any diagram that overflows its
column (auto re-fits when sidebars collapse/expand). Do not add per-diagram
workarounds; these are handled globally.

### A fence that does not parse FAILS THE BUILD (owner-set 2026-07-30)

`scripts/build_diagrams.mjs` pre-renders every fence to `game/diagrams/<hash>.mmz`
and now **exits 1 on any parse failure**, taking both Pages and the APK red. It used
to exit 0 on the theory that the reader live-renders a failed fence anyway — which
hid **20 broken diagrams across 8 sections**, each shipping as a raw-source blob to
anyone offline or on the APK. Do not restore the non-blocking behaviour.

**Validate before committing** — render the fences you touched rather than eyeballing
them. GitHub silently shows a broken fence as an error box, so "it looked fine in the
diff" proves nothing:

```bash
npm ci                                             # once — Puppeteer + pinned mermaid
node scripts/check_render.mjs <absolute-file.md>   # exit 0 = every fence renders
```

**The traps that caused all 20.** Every one of these is a *silent* parse failure — the
diagram reads perfectly as source:

| Trap | Breaks | Fix |
|------|--------|-----|
| `;` anywhere in a `Note`/message body | sequenceDiagram — `;` is a statement separator, so the text is cut and the remainder parses as a new statement | use `,` or `.` |
| Bare `call` as a node id | flowchart — `call` is reserved (`click X call fn()`). Legal in `call([...])`, explodes at statement start (`call --> b`) or in a `class` list | rename (`doCall`, `entry`) |
| Extra spaces AFTER an edge label's closing `\|` | flowchart — `a -->\|"x"\|   b` fails; spaces *before* the arrow are fine | single space after `\|` |
| `\"` inside a node label | flowchart — Mermaid has no backslash escape | `#quot;` |
| `:` or `()` in a `quadrant-N` label | quadrantChart — `:` is the data-point separator | quote it: `quadrant-1 "Ideal: fast"` |
| A quadrant point without brackets | quadrantChart — `"P": 0.2, 0.8` | `"P": [0.2, 0.8]` |
| A quadrant coordinate of exactly `1.0` | quadrantChart — must be `< 1.0` | use `0.98` |

The alignment trap deserves naming: padding spaces to line up a column of edges is
exactly the habit that makes ASCII diagrams readable, and it is the one that broke
the RSocket diagram. Mermaid is not ASCII art — do not pad.

---

## Game / Reader / Q&A Compatibility (authoring contract — all files MUST comply)

Every module page (`<module>/<module>.md`) and deep-dive sub-file is consumed by the browser learning
game in two ways: (1) `game/extract.py` parses its interview Q&As into the MCQ
question bank, and (2) the game's reader renders its Markdown (including Mermaid)
for the "dive deeper" content view. Content that violates this contract is
silently dropped from the game or renders wrong. These rules are derived from
`game/extract.py`, `game/app.js` (`renderMermaid`), and
`game/CLAUDE.md` — do not contradict them.

- **Android APK exception:** the game also ships as an offline-sideloadable APK
  (see `android/README.md`). Three `IS_APK`-gated hooks in `game/app.js`
  (vendored Mermaid UMD loader, skipped service-worker registration,
  `SDAndroid.saveBackup` export bridge) are the ONLY APK-conditional branches,
  keyed on the `appassets.androidplatform.net` hostname — the Pages build must
  stay byte-identical outside them.

### What gets scanned

- `extract.py` walks **every section** with no allowlist. A **new module dir +
  its `<module>.md` page** and any **new deep-dive sub-file** (`<module>/<name>.md`)
  are picked up automatically — sub-files are grouped under their parent directory's
  module (so `ml/foo/foo.md` and `ml/foo/bar.md` share the `ml/foo` topic). A page
  named `README.md` below a section root is NOT a module page and will not be found
  where one is expected.
- **Excluded from Q&A extraction:** any path containing `case_studies/`, and all
  `CLAUDE.md` files. Case studies are still **reachable in the reader** via
  relative `.md` links (the `/content/` route serves any file) — so linking to a
  case study from a module is fine; its Q&As just never enter the quiz bank.
- **`technologies/tech_bank/` is excluded from BOTH walks by exact path** — the Q&A/file-
  tree walk in `extract.py`'s `main()` and the §11/§8 walk in `build_tech_index()`
  (`TECH_BANK_DIR` / `in_tech_bank()` — both live in `game/build_tech.py`, which
  `extract.py` imports; the technology code was split out of `extract.py` on 2026-07-31
  so the question bank and the technology bank are separate programs sharing only
  `game/build_common.py`). It is authored DATA, the source of
  `game/tech_index.json`, not study content: no `STUDY_ORDER` entry, no
  `<!-- study-paths -->` block, no questions, not in the reader's module tree. The
  exclusion is load-bearing and silent — as a plain module dir it becomes a phantom
  module that `--strict` waves through, and one `## 11. Technologies & Tools` table
  inside it makes the index index the bank into itself (3809/613 → 3810/614, no error).
  So `build_tech_bank()` fails the build on any `## NN.` template heading found there.
  Full authoring contract: `technologies/CLAUDE.md`.
- The bank (`game/questions/*.json`) and the module-relatedness graphs
  (`game/graph/*.json`) are **generated, not committed** — they are
  gitignored; the Pages CI regenerates them fresh on every push (see
  `.github/workflows/pages.yml`). For local play/testing, run
  `python3 game/extract.py` after editing ANY Q&A or adding content, then reload
  the reader (`readerCache` is per-session). **Every new module directory MUST be
  added to
  `STUDY_ORDER["<section>"]` in `game/app.js`** at its correct learning-path
  position — a module absent from the array falls through the `indexOf === -1 →
  9999` fallback and sorts to the very end, breaking the learning order.
  `extract.py --strict` (run by Pages CI) fails the deploy when a bank module is
  missing from `STUDY_ORDER`, when a `STUDY_PATHS` array stops being an ordered
  subset, or when a section's **Interview-Specific Path drifts between its two
  sources**. (New deep-dive **sub-files** need no `STUDY_ORDER` entry — they group
  under their parent module's existing position.)
- **Curated study paths are declared ONCE per module, in a `<!-- study-paths -->` block in
  that module's own page (`<module>/<module>.md`) — never scattered through the content.**
  Study files carry no metadata of their own; a deep-dive sub-file is study content and
  stays study content.

      # Creational Patterns — Master Index

      <!-- study-paths
      senior: creational.md, singleton/singleton.md, factory_method/factory_method.md, builder/builder.md
      principal: creational.md
      files this module contributes to each curated path; omit a tier to leave it out
      -->

  One line per tier, naming every file that tier takes — the module page plus whichever
  sub-files that level actually needs. **Listing a tier puts the module in it; omitting the
  tier leaves the module out.** The module page (`<module>.md`, resolved by
  `_module_page()`) must always be listed — it is never optional — and every named file
  must exist; both are FATAL under `--strict`. A nested pattern page is named for its own
  folder (`singleton/singleton.md`); module ids stay 2 segments.

  Case studies work the same way, declared once in the section's case-study index
  `<section>/case_studies/case_studies.md`:

      <!-- study-paths
      senior: design_banking_ledger/design_banking_ledger.md, design_ecommerce_catalog/design_ecommerce_catalog.md
      principal: design_monolith_to_polyglot_migration/design_monolith_to_polyglot_migration.md
      -->

  That block drives the **Level filter** on the Case Studies tab (All / Senior N / Principal
  N), which persists in `sd_case_tier` independently of the module path.

  **The block says WHETHER, never WHERE.** Order still comes from `STUDY_ORDER` in
  `game/app.js`, so a derived path is an ordered subset by construction and cannot drift
  out of order. `extract.py` walks the tree, reads every marker, and emits
  `game/questions/paths.json` — generated and gitignored exactly like the question banks,
  regenerated by CI on every push. `app.js` fetches it at boot into `STUDY_PATHS`; there is
  **no hand-maintained path array in `app.js` any more** (the old 31 KB literal is gone).

  **Why this shape.** The sub-file layer had never been curated, because nothing could
  express it: a sub-file has no `STUDY_ORDER` entry (it groups under its parent via
  `splitModulePath()`), so adding a module silently pulled in every deep-dive beneath it.
  Measured before the change: 171 modules dragged in 163 sub-files — `lld` advertised "2
  modules fewer" while shipping 69 files at 90% of the section. Putting membership in the
  file makes the sub-file addressable and puts the decision next to the content.

  **The two tiers are DIFFERENT CUTS, not nested depths.** Senior is the craft — can you
  build it, debug it at 3am, say why it broke. Principal is the judgment — which approach
  at what cost, what failure modes, what migration, what you tell a team *not* to do. A
  module may be in one, both, or neither; principal is usually SMALLER; and roughly half of
  each principal list is material senior never sees. Do not "promote" a module to principal
  because it is advanced.

  **Adding a module:** write the block at the top of its `<module>.md` page. **Adding a
  deep-dive sub-file:** add
  its filename to whichever tier lines in the PARENT module's block should carry it — and to
  neither, if it is Full-path depth. That is the whole wiring step; there is no array to edit
  and nothing to keep in sync. A sub-file that exists but appears in no tier line is simply
  Full-path only, which is a legitimate and common choice.

- **HISTORY, and the trap it leaves behind.** The curated subset used to be a
  DUAL-SOURCE list: an `interview` array in `game/app.js` plus a hand-written
  `### Interview-Specific Path (N modules)` table in `<section>/README.md`, kept in step by
  hand. **Both are gone.** There is no `STUDY_PATHS.<section>.interview` — `app.js` has
  `let STUDY_PATHS = {}` filled at boot from the generated `questions/paths.json` — and the
  README tier tables are GENERATED between `<!-- study-path-table <tier> -->` markers by
  `python3 game/extract.py --write-paths`. Membership is declared once per module, in the
  `<!-- study-paths -->` block on that module's page.

  This is recorded because the old instruction ("add it in both places, update the
  `(N modules)` count") is still the intuitive move and is now actively harmful: a
  hand-edited tier table fails `--strict` with a STALE error, and the obvious remedy —
  re-running `--write-paths` — makes the build pass again while SILENTLY DROPPING the
  module from every tier, because `_declared_paths()` looks for the marker on
  `_module_page(mod)` and a page named otherwise is never read. Green build, module gone
  from Senior and Principal, questions still in the bank so nothing looks wrong.
- **A module id is always `<section>/<module>` — exactly two segments.** `book` is the
  single exception (`book/<book>/<chapter>`). A file living in a sub-directory of a module
  — `lld/creational/prototype/prototype.md` — folds into its parent module the same way a
  deep-dive sub-file does, carrying the extra path inside `sourceFile`
  (`prototype/prototype.md`). So a nested folder needs **no** `STUDY_ORDER` entry either. Do
  not "fix" this by adding 3-segment keys: they are absent from `STUDY_ORDER`,
  `check_wiring()` treats that as fatal, and `--strict` fails the Pages deploy.

  The reader resolves a page back to its module with `splitModulePath()` in `app.js`
  (longest-prefix match against the real module list), which is what keeps the
  "Evaluate yourself" quiz, "What next", and prev/next nav working on a nested page.
  Anything new that needs a module id from a reader path must use it rather than
  assuming `dirname(path)`.

### Q&A format required for extraction (Section 12)

- Q&As must sit under a heading matching `^##\s+.*interview\s+q` (case-insensitive)
  — the canonical `## 12. Interview Questions with Answers` works.
  **`## NN. Interview Tips` does NOT match**, and neither does any heading without the
  word "questions" after "interview". A file using one contributes **zero** questions to
  the bank, silently — no error, no warning, it simply never appears in the quiz. This is
  not hypothetical: 23 `lld` pattern modules used `Interview Tips` and 5
  `lld/pattern_comparisons/` files used `Interview Answer Template(s)`, losing 217 Q&As
  between them — `lld` sat at 198 questions against `llm`'s 2,052 until they were renamed
  on 2026-07-29. When adding a module, confirm it reached the bank
  (`python3 game/extract.py` prints a per-section count) rather than assuming the heading
  was close enough.
- Each **question line starts with `**`** and is one of: fully bold `**question?**`
  (optional trailing `:`/`.`), a `**Qn:` / `**Q:` label, or an opening `**` that
  wraps across lines. This is why the "bold the question" rule is load-bearing,
  not cosmetic.
- **NEVER number your Q&As as a Markdown list.** `is_question_line()` requires the
  line to *start* with `**`, so `1. **"question?"**` never registers as a question
  boundary and the whole section is silently unreachable. Measured 2026-07-29: six
  `lld` files used it and lost **45 Q&As** between them (fixed in bc06e22). Two
  distinct symptoms, and the first is nastier than plain loss:
  - the three `lld/creational/` files yielded **two GARBAGE pseudo-questions each**,
    whose question text was a bold section header (`Q: "Common Interview Questions:"`)
    with the entire list swallowed as the answer — junk that then fed the distractor
    pool for every sibling question;
  - three of the `lld/concurrency_patterns/` sub-files yielded **exactly zero**,
    (they were `*_README.md` at the time; renamed to `ProducerConsumer.md`,
    `ReadWriteLock.md`, `ThreadPool.md`, `ThreadSafeSingleton.md` in a46b3e0)
    which is how they escaped the `**Short:**` migration entirely: a file with no
    parsed questions looks like a file with nothing to migrate.

  If you inherit the format, convert it — do not hand-edit. The converter is at
  `memory/fix_numbered_qa.py` (checked into the assistant memory directory, since
  session scratch is ephemeral). It rewrites `N. **"q?"**` to `**Q: q?**`, dedents
  the body, maps `Answer:` to `A:`, and turns the bold section headers into `###`
  so they stop parsing as questions. Verify with a word-frequency diff: the only
  tokens that may disappear are the list numbers and the renamed header words.
- **The first sentence of the answer should be a self-contained direct answer of
  15–220 characters**, because for a Q&A with no authored `**Short:**` line it
  becomes the MCQ's correct option. **It is NOT a drop rule** — `make_short()`
  returns a short first sentence as-is and trims an over-long one at a clause
  boundary, returning `""` only for empty input, so **no question is ever excluded
  from the bank on length alone** (this has been true since 18e2328). What a bad
  first sentence costs is a mangled *option*: a 442-char opener ships as a truncated
  fragment, and one opening with a code fence ships as gibberish. Worth fixing on
  its own merits — but do not report it as lost questions, and do not prioritise it
  over a heading that really does delete Q&As from the bank (see above). The
  permanent fix for any such Q&A is to author a `**Short:**` line.
- A topic needs enough sibling Q&As to build 3 distractors; the **15-Q&A floor**
  guarantees this. Distractors are drawn from other answers' first sentences
  (same module first, widening to the section), IDF-ranked — so keep first
  sentences crisp and distinct.

### Mermaid render rules (so diagrams draw in the reader)

- The reader renders every ```` ```mermaid ```` fence via CDN `mermaid@11`
  (flowchart, sequenceDiagram, stateDiagram-v2, xychart-beta, pie, quadrantChart,
  timeline, sankey-beta). Offline → raw source shown, retried next open.
- **Flowcharts: colour EVERY node** with the One-Dark `classDef` block (from
  `/mermaid-diagrams`), semantic-by-role — that is the standard (owner-set 2026-07-07).
  The reader's grey auto-tint fills **only** the nodes you leave unstyled, assigning
  One-Dark hues in node order (`mmTintPlain`, `app.js`) — authored colours are always
  respected, so it degrades **per node**, never all-or-nothing and never a flat-grey
  bail. Colour every node anyway: the auto-tint's order-based hues are semantically
  arbitrary and can collide with your authored ones (an auto-blue box beside your
  authored-blue box), so hand-colouring every node is the only way each colour *means*
  something. Also **horizontal-first**
  (`flowchart LR` default), **short labels** (no spillover — wrap with `<br/>`), and
  **rounded shapes** (the reader rounds every box; use `(label)`/`([label])` for GitHub parity).
- The reader surface is **pitch-black One-Dark in every theme** — never hand-set a
  light background or theme-tinted colors inside a diagram.
- Mermaid fences are valid **only** in study section files (`<section>/…`) — never
  in CLAUDE.md, skills, or `game/` (see "Mermaid Diagrams" scope rule above).
