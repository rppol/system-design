# Book Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/book/`
Global conventions (formatting, Q&A rules, diagram rules): see root `CLAUDE.md`.

## Scope — STILL OUT (owner-reaffirmed 2026-08-04, when the other three were re-opened)

`devops/`, `cs_fundamentals/` and `cuda/` were unparked on 2026-08-04. **`book/` was
deliberately left out**, on the owner's reasoning that the other three are *educational
modules* — they teach a subject and are answerable to reality — whereas this section is
**a chapter-by-chapter summary of specific books**, answerable only to its sources.
That is a difference in kind, not a difference in scheduling, so it does not expire.

**Why the factual audit is not merely deferred here but wrong here.** A book summary is
faithful to its source. "Is this claim true and current as of today" is the wrong test: a
2017 claim in DDIA is *correctly* reported as what DDIA says, and "correcting" it to 2026
reality would make the summary wrong about the book. The repo-wide modernization rule
(REPLACE, DO NOT ANNOTATE) inverts here for the same reason — swapping Kleppmann's example
for a newer one is a fabrication attributed to a named author.

If a pass is ever scheduled for this section it must ask a different question:

| Elsewhere the audit asks | Here it would have to ask |
|--------------------------|---------------------------|
| Is this claim true today? | Does the book actually say this? |
| Is this API/version/product current? | Is the chapter/section attribution right? |
| Replace anything stale | Change nothing the book states, however dated |

A **fidelity** finding is real: the summary attributes a claim to the wrong chapter, states
a number the book does not state, inverts an argument, or presents the summarizer's opinion
as the author's. A **currency** finding is not a finding here. Where the gap between the
book and today is genuinely instructive it belongs in an explicitly-marked editorial aside,
never as a silent correction.

---

## HARD RULE — `## Beyond the book` (owner-set 2026-08-25)

**Material the cited chapter does not contain may not sit under a `## N.x (Ch M)` heading.**
This is the section's dominant defect class and every instance found in the 2026-08-25
fidelity pass was of exactly this shape: *technically correct material the book does not
contain, presented as the named author's*. Nothing was factually wrong. All of it read as
Vitillo's, or Kleppmann's, or Huyen's.

Adding material is not the problem — this section adds a great deal and is better for it.
**Undisclosed** addition is the problem. The remedy is disclosure, never deletion.

Two sanctioned forms, in order of preference:

1. **A `## Beyond the book — <topic>` section**, placed after the last `## N.x` span and
   before `## Visual Intuition`, opening with a `>` blockquote that states plainly what the
   chapter does and does not say. Models:
   `understanding_distributed_systems/01_communication/01_communication.md`
   (`## Beyond the book — TCP retransmission timing and QUIC`) and
   `system_design_interview_vol_1/07_design_a_unique_id_generator/07_design_a_unique_id_generator.md`
   (`## Beyond the book — where Snowflake lives today`).
2. **An inline *(beyond the book)* tag** on a single sentence or glossary line, where hoisting
   a whole section would be heavier than the point deserves. Model:
   `06_partitioning`'s Cassandra-4.0 vnode aside, and SDI Vol 1 Ch 2's inline
   "including rows the book does not list (99.95%, 99.995%)".

**What does NOT need disclosing.** Original scaffolding that is already framed as the
summarizer's own — DDIA's "Decoding…" arithmetic boxes, worked derivations opening with
"the chapter describes X without quantifying it" — is fine as-is, because the frame already
tells the reader whose voice it is. The test is not "did the book say it", it is **"would a
reader think the author said it"**.

**Cross-links, glossary entries, and Q&As** are understood to be the repo's voice, not the
author's, and do not need the tag. But a Q&A whose subject is beyond-the-book material
should have that material disclosed *somewhere in the same file* — otherwise the quiz teaches
as the author's a thing the author never wrote.

**The `**Short:**` migration is a separate question — measured 2026-08-04, re-measured and
EXECUTED 2026-08-25.** Unlike the audit it is fidelity-neutral: an added one-line display
summary of an answer the section already wrote, changing nothing about what the book is
reported to say. book's Q&As all ship in the MCQ bank, so a Q&A without one quizzes on a
*derived* option. The measurement (reproduce `make_short()` per Q&A and classify the result),
re-run against live `extract.py` on 2026-08-25 over 1,402 Q&As:

| Derived option | Count | Share | Worth authoring? |
|----------------|-------|-------|------------------|
| Reads fine as-is | 1,092 | 77.9% | no |
| **TRUNCATED** — `make_short()` cut the real first sentence, ships as a mid-clause fragment | **302** | 21.5% | **yes — done** |
| Under 40 chars ("Your application code is.") | **8** | 0.6% | **yes — done** |
| Contains code-fence / table debris | 0 | 0.0% | n/a |

(The 2026-08-04 table split ~125 of the "reads fine" cases out as "opens with a referring
word". Those were deliberately skipped and are folded back into "reads fine" here: the quiz
renders the question directly above the options, so "It" resolves for a reader. They are
weak, not wrong.)

**The targeted pass over the 310 ran on 2026-08-25, and every book Q&A now carries an
authored `**Short:**` line.** The truncated ones were broken regardless of context — the option
literally stopped mid-thought. Rerun the measurement rather than trusting this table if the
section has changed.

**Two rules bind any `**Short:**` line written in this section, and they are not the
repo-wide rules:**

1. **The line states THE BOOK'S CLAIM, never the truth.** If the book is wrong by today's
   standards, the Short line is still the book's position. That is what makes the pass safe
   in a section excluded from the currency audit.
2. **Where the claim is contestable, carry the attribution.** Prefer *"Kleppmann argues the
   database should be unbundled — the replication log becomes the public interface"* over
   *"Databases should be unbundled."* This is the whole difference between a faithful summary
   and an editorial.

The standing repo rule also binds: **never edit `answerFull` while adding one.** The line is
added above the answer, never carved out of it.

`book/` has never been entered into the audit manifest and still is not — the 295-unit
denominator in root `CLAUDE.md` excludes it.

This section is **organized by book**, not by topic. Each book is a folder; each chapter is
a sub-folder holding a single page named for that folder
(`book/<book_slug>/NN_<slug>/NN_<slug>.md`) that follows the book's own section order.
Only `book/README.md` is a README — each book's own index is `book/<book_slug>/<book_slug>.md`.

---

## Books & Chapters

| Book | Folder | Chapters |
|------|--------|----------|
| Designing Data-Intensive Applications (Kleppmann) — **1st ed., O'Reilly 2017** | `designing_data_intensive_applications/` | preface + Ch 1–12 |
| System Design Interview Vol 1 (Xu) | `system_design_interview_vol_1/` | Ch 1–16 |
| System Design Interview Vol 2 (Xu & Lam) | `system_design_interview_vol_2/` | Ch 1–13 |
| Machine Learning System Design Interview (Aminian & Xu) | `machine_learning_system_design_interview/` | Ch 1–11 |
| Designing Machine Learning Systems (Huyen) | `designing_machine_learning_systems/` | Ch 1–11 |
| Understanding Distributed Systems (Vitillo) | `understanding_distributed_systems/` | Ch 1–33 as **5 part-folders** (sanctioned exception: UDS chapters are 3–8 pages, so one folder per PART — one `## N.x` per book chapter inside; do NOT "fix" it back to 33 folders) |
| Designing Data-Intensive Applications, 2nd ed. (Kleppmann & Riccomini) | `ddia_2e/` | Ch 1-14 |
| The Staff Engineer's Path (Reilly) | `the_staff_engineers_path/` | Ch 1-9 |
| Staff Engineer: Leadership Beyond the Management Track (Larson) | `staff_engineer_larson/` | 5 part-folders |
| Database Internals (Petrov) | `database_internals/` | Ch 1-14 |
| Site Reliability Engineering (Beyer, Jones, Petoff & Murphy, eds.) | `site_reliability_engineering/` | 11 selected chapters of 34 |
| Building Microservices, 2nd ed. (Newman) | `building_microservices/` | Ch 1-16 |
| AI Engineering (Huyen) | `ai_engineering/` | Ch 1-10 |
| Generative AI System Design Interview (Aminian & Sheng) | `generative_ai_system_design_interview/` | Ch 1-11 |

**Game navigation (wired in `game/app.js`):** the book section nests one level deeper than
every other section (module ids are `book/<book_slug>/<chapter>`), so the Study view shows
**one node per book** (`#/study/book` → per-book chapter graph at `#/study/book/<slug>`)
and the reader sidebar groups chapters under collapsible book headers. **Adding a book
therefore requires a `BOOK_LABELS` entry in `game/app.js`** (name, author, short label)
alongside its `STUDY_ORDER.book` entries — an unlisted book still renders (slug is
title-cased as a fallback) but loses its author/short labels.

**No curated tiers here — and do not add one.** Every other section declares Senior /
Principal membership with a `<!-- study-paths -->` block in each module page; `book` is
Full-path only, and `questions/paths.json` has no `book` key. A marker in a chapter page
would not merely be ignored, it would **fail the build**: tier membership is validated as
a 2-segment module id, and book ids are 3 segments (`book/<book_slug>/<chapter>`), so
`extract.py --strict` rejects it with "module id must be 2 segments". Chapters are ordered
by `STUDY_ORDER.book` alone.

Per-chapter build status lives in each book's index page (`<book_slug>/<book_slug>.md`)
**Build Manifest** table.

---

## The Book-Faithful Chapter Template

This section does NOT use the standard 14-section module template. A book chapter is a
multi-concept narrative, so each chapter page uses this structure instead. The
middle block — **one `##` per the chapter's own book sections, in book order** — is the
completeness guarantee: a missing topic is structurally visible.

```
# Chapter N: <Title>
> Part <I/II/III> · DDIA (Kleppmann, 1st ed., O'Reilly 2017) · builds on Ch <x>, leads to Ch <y>

## Chapter Map            — 1-paragraph placement + 3-bullet TL;DR of the chapter's argument
## The Big Question       — the driving problem the chapter answers (analogy / intuition)

## N.1 <book section title>   one ## per real book section, IN ORDER.
## N.2 <book section title>   ### sub-headings for sub-concepts. Each explained in depth:
## N.3 <book section title>   mechanism, concrete numbers, broken->fix, inline ASCII.
   ...

## Visual Intuition       — grouped diagrams (Mermaid preferred; ASCII for grids/geometry) for the chapter's hardest mechanics
## Key Concepts Glossary  — EVERY term the chapter defines, one line each (completeness net)
## Tradeoffs & Decision Tables — comparison tables
## Common Pitfalls / War Stories — production failure patterns tied to the chapter
## Real-World Systems Referenced — the systems the author names
## Summary                — the author's own end-of-chapter summary, expanded
## Interview Questions    — 15+ Q&As (bold Q, plain A; gotchas first)
## Cross-links in this repo — pointers to database/, hld/, backend/, devops/ deep dives
## Further Reading        — the key references the chapter cites
```

**Quality bars** (inherited from root `CLAUDE.md`):
- Diagrams appeal-first (see root `CLAUDE.md`): Mermaid preferred for topological diagrams (flow/sequence/state); ASCII kept only for grids, byte-layout maps, and geometry Mermaid can't draw; no image files
- Concrete numbers everywhere (latencies, sizes, amplification factors)
- Show broken design/code, then the fix
- No emojis (use `✓`/`✗`, not emoji check marks)
- **≥15 Q&As per chapter**, ordered by interview frequency (gotchas/traps first)
- Tables in Markdown pipe syntax; `---` rules between major sections
- Depth: **no line ceiling in `book/`** (owner-set 2026-07-16) — a chapter runs as long as
  full coverage requires ("comprehensive — don't miss anything"). Practical floor ~700
  lines for design/technical chapters; short-form chapters (e.g. resource-list closers
  like SDI-1 Ch 16) may be 400–600 lines but must still carry ≥15 Q&As

---

## Adding a New Chapter

1. Create `<book_folder>/NN_<slug>/NN_<slug>.md` using the chapter template above — the page
   is named for its folder, and the folder IS the 3-segment module id, so keep both. **The
   `>` byline MUST name the edition and year** (`(Kleppmann, 1st ed., O'Reilly 2017)`,
   `(Vitillo, 2nd ed.)`). A summary that does not identify its source edition is
   unfalsifiable, and once a second edition exists with different chapter numbering the
   ambiguity sends readers to the wrong chapter.
2. Map **every** book section to a `## NN.x` heading, in order — this is non-negotiable; it
   is how completeness is guaranteed.
3. Add a glossary entry for every term the chapter defines.
4. Add ≥15 Q&As.
5. Run the diagram validator (see below) on the file.
6. Flip the chapter's row in the book's index page (`<book_folder>/<book_folder>.md`)
   **Build Manifest** to done.
7. Update the chapter table + learning path in that same book index page if needed.

## Adding a New Book

1. Create `book/<book_slug>/<book_slug>.md` (thesis, part map, chapter table, learning path,
   build manifest) — the book index is named for its folder, not `README.md`.
2. Add a row to the **Books** table in `book/README.md` and in this file.
3. Add a row to the root `README.md` Book Summaries section and the root `CLAUDE.md` table.
4. Wire the game (`game/app.js`): append the chapter folders to `STUDY_ORDER.book` in
   learning order AND add a `BOOK_LABELS` entry (`{ name, author, short }`) — the labels
   drive the per-book Study picker and the reader sidebar's book groups. Re-run
   `python3 game/extract.py` locally to verify.
5. Build chapters per "Adding a New Chapter".

---

## Visual Intuition Diagrams

Follow the repo skill at `.claude/skills/visual-intuition-diagrams/`. Author a diagram,
then lint it before considering a file done:

```bash
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py check \
  src/main/java/com/rutik/systemdesign/book/
```

ASCII only; fenced block with no language tag; spaces not tabs; no trailing whitespace; no
emojis; widest line ≤ 100 cols; caption each diagram with 1–2 sentences tying it to the
insight.

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
