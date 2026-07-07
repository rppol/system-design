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
| **ML** | Machine Learning guide — 45 modules, 22 case studies | [ml/CLAUDE.md](src/main/java/com/rutik/systemdesign/ml/CLAUDE.md) |
| **LLM** | LLM engineering guide — 52 modules, 29 case studies, 82 deep-dive sub-files | [llm/CLAUDE.md](src/main/java/com/rutik/systemdesign/llm/CLAUDE.md) |
| **DevOps** | DevOps / Cloud / Platform guide — 41 modules, 13 case studies | [devops/CLAUDE.md](src/main/java/com/rutik/systemdesign/devops/CLAUDE.md) |
| **CUDA** | GPGPU / CUDA programming guide (kernel-author viewpoint) — 24 modules, 6 case studies | [cuda/CLAUDE.md](src/main/java/com/rutik/systemdesign/cuda/CLAUDE.md) |
| **CS Fundamentals** | Language-agnostic CS spine — 24 modules, 6 case studies + DSA pattern playbooks sub-section (25-pattern recognition engine, interview execution playbook, Blind 75/NeetCode 150 study plans — complete) | [cs_fundamentals/CLAUDE.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/CLAUDE.md) |
| **Book** | Chapter-by-chapter book summaries (book-faithful chapter template, not the 14-section module template) — Designing Data-Intensive Applications (Kleppmann): 12 chapters + preface | [book/CLAUDE.md](src/main/java/com/rutik/systemdesign/book/CLAUDE.md) |
| **Game** | Pages-deployed daily learning game (a static SPA, NOT 14-section content — template-exempt). 5-min MCQ blitz auto-built from all sections' Q&As via `extract.py`; SM-2 spaced-repetition review, daily sealed Gauntlet, and an in-app coach; `localStorage` is the single source of truth (no server). | [game/CLAUDE.md](src/main/java/com/rutik/systemdesign/game/CLAUDE.md) |

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
├── book/             ← Book summaries (chapter-by-chapter) + CLAUDE.md
└── game/             ← Browser learning game (app, not study content) + README.md
```

---

## The 14-Section Module Template

Every module README must follow this exact structure:

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

## Case Study Learning Path (case_studies/README.md)

Every `case_studies/` directory MUST contain a `README.md` with these 5 sections:

1. **Quick Start** — 3 files to read first, with one-sentence justification each
2. **Full Learning Path** — all case studies grouped by engineering concern; file link + concern phrase + 1–2 sentence summary
3. **Cross-Cutting / Shared Primitives** — table mapping each cross_cutting/ sub-file to the phase where it becomes relevant
4. **Dependency Map** — ASCII tree showing which case studies build on others
5. **Interview Prep Shortcuts** — table mapping "design X" questions to best case study file

**Maintenance rule:** Update this README every time a new case study is added — same commit, no exceptions.

---

## Interview Q&A Rules

- **Bold the question**, plain text the answer
- **Prefix every question with `Q: `** inside the bold, i.e. `**Q: <question>?**` (the LLM-section convention standardized repo-wide in ae92f65). The leading `Q: ` is display-only — `extract.py` strips it when building the MCQ bank, so it never leaks into the game. New sections MUST adopt this from the start (CUDA missed it because it was authored after the sweep).
- First sentence = direct answer; following sentences = mechanism / example / gotcha; final sentence = practical guidance
- **Minimum 15 Q&As per module** — this is a hard floor; see section CLAUDE.md for modules that require 18+
- **Order by interview importance**: highest-frequency gotchas and traps first, then internal mechanics, then edge cases and advanced scenarios last

---

## Content Quality Standards

- **Show broken code, then the fix** — DCL without volatile, HashMap concurrency, self-invocation, N+1
- **Concrete numbers everywhere** — virtual thread ~few KB stack; platform thread ~1MB; HashMap capacity 16; load factor 0.75; ArrayList grows 1.5×; G1 pause 200ms; ZGC sub-1ms; HikariCP default pool 10; Tomcat default threads 200; BCrypt cost 10–12
- **Production war stories** in Common Pitfalls — real incident patterns, not toy examples
- **No emojis** in any file
- **Effective Java item references** where applicable (Java section only)

---

## How to Add a New Module

1. Create `<section>/<module_name>/README.md` — 14-section template
2. Meet the Q&A minimum (see section CLAUDE.md for specifics)
3. Update the section's master `README.md`
4. Update root `README.md` table
5. See the section's `CLAUDE.md` for section-specific steps

### Adding a case study
- Write the file following the section's template
- Update the section's master `README.md` case study table
- **Update `case_studies/README.md`** — add to correct phase, update dependency map, add interview prep row
- Update root `README.md` and the section's `CLAUDE.md` case study list

---

## Reference Files

| File | Purpose |
|------|---------|
| `llm/foundations_and_architecture/README.md` | Gold standard 14-section format |
| `llm/case_studies/design_gpu_inference_platform.md` | Gold standard 11-section principal case study |
| `java/concurrency/README.md` | Example of 15+ Q&A deep module |
| `spring/spring_transactions/README.md` | Example of 18+ Q&A deep Spring module |
| `llm/agentic_frameworks/langchain_and_lcel.md` | Example deep-dive sub-file (15+ Q&As) |

---

## Formatting Rules

- Diagrams are **appeal-first** (owner policy, 2026-07-02): use the most visually appealing renderable form that conveys the information accurately. In study section files that means the **Mermaid diagram family is preferred** (flowchart, sequenceDiagram, stateDiagram-v2, xychart-beta, pie, quadrantChart, timeline, sankey-beta — all render on GitHub and in the game reader). ASCII remains for shapes Mermaid cannot draw (constraint grids/masks, alignment-critical layout maps, vector geometry). No image files. Use `/mermaid-diagrams` skill to decide; see "Mermaid Diagrams" section below.
- Tables use standard Markdown pipe syntax
- Code blocks use triple backticks with language tag (` ```java `, ` ```sql `, ` ```yaml `, etc.)
- Section headers follow exact numbering: `## 1.`, `## 2.`, ... `## 14.`
- Use `---` horizontal rules to separate major sections
- Links between modules: use relative paths, e.g., `[Concurrency](../concurrency/README.md)`

---

## Visual Intuition Diagrams

Section 5 of every module (Architecture Diagrams) — and any place a concept is
hard to picture — should use a **visual intuition diagram**: ASCII art that makes
an abstract relationship *physically visible*. The gold standard is the causal-mask
grid and the sliding-window before/after pair in
`llm/foundations_and_architecture/README.md`.

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

---

## Game / Reader / Q&A Compatibility (authoring contract — all files MUST comply)

Every module README and deep-dive sub-file is consumed by the browser learning
game in two ways: (1) `game/extract.py` parses its interview Q&As into the MCQ
question bank, and (2) the game's reader renders its Markdown (including Mermaid)
for the "dive deeper" content view. Content that violates this contract is
silently dropped from the game or renders wrong. These rules are derived from
`game/extract.py`, `game/app.js` (`renderMermaid`), `game/server.py`, and
`game/CLAUDE.md` — do not contradict them.

### What gets scanned

- `extract.py` walks **every section** with no allowlist. A **new module dir +
  `README.md`** and any **new deep-dive sub-file** (`<module>/<name>.md`) are
  picked up automatically — sub-files are grouped under their parent directory's
  module (so `ml/foo/README.md` and `ml/foo/bar.md` share the `ml/foo` topic).
- **Excluded from Q&A extraction:** any path containing `case_studies/`, and all
  `CLAUDE.md` files. Case studies are still **reachable in the reader** via
  relative `.md` links (the `/content/` route serves any file) — so linking to a
  case study from a module is fine; its Q&As just never enter the quiz bank.
- The bank (`game/questions/*.json`) is **generated, not committed** — it is
  gitignored; the Pages CI regenerates it fresh on every push (see
  `.github/workflows/pages.yml`). For local play/testing, run
  `python3 game/extract.py` after editing ANY Q&A or adding content, then reload
  the reader (`readerCache` is per-session). **Every new module directory MUST be
  added to
  `STUDY_ORDER["<section>"]` in `game/app.js`** at its correct learning-path
  position — a module absent from the array falls through the `indexOf === -1 →
  9999` fallback and sorts to the very end, breaking the learning order. (New
  deep-dive **sub-files** need no `STUDY_ORDER` entry — they group under their
  parent module's existing position.)

### Q&A format required for extraction (Section 12)

- Q&As must sit under a heading matching `^##\s+.*interview\s+q` (case-insensitive)
  — the canonical `## 12. Interview Questions with Answers` works.
- Each **question line starts with `**`** and is one of: fully bold `**question?**`
  (optional trailing `:`/`.`), a `**Qn:` / `**Q:` label, or an opening `**` that
  wraps across lines. This is why the "bold the question" rule is load-bearing,
  not cosmetic.
- **The first sentence of the answer must be a self-contained direct answer of
  15–220 characters.** Shorter or longer → the Q&A is silently dropped (it becomes
  the MCQ's correct option). This is why the Q&A rule mandates "first sentence =
  direct answer."
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
