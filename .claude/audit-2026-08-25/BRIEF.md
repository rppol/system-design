# Section Audit Brief — 2026-08-25

You are the **lead auditor for exactly one section** of this study repository.
Repo root: `/Users/rutikp/Prism/rutik46/systemdesign`
Section root: `src/main/java/com/rutik/systemdesign/<section>/`

## Read these FIRST, in this order
1. `CLAUDE.md` (repo root) — the authoring contract. Non-negotiable.
2. `src/main/java/com/rutik/systemdesign/<section>/CLAUDE.md` — your section's rules.
3. `src/main/java/com/rutik/systemdesign/<section>/README.md` — the module table, learning paths,
   and the `<!-- study-paths -->` block (the single source of truth for file inventory + tiers).

## THIS IS A PLANNING PASS. WRITE NO CONTENT FILES.
Do NOT edit, create, or delete any file under `src/main/java/com/rutik/systemdesign/`.
Your ONLY write is your own report (path given in your task prompt).
No `git add`, no `git commit`, no `extract.py --write-paths`.

## What has ALREADY been done — do not re-report it as a finding
- A repo-wide **factual audit + modernization pass is COMPLETE** for all 15 non-book sections
  (finished 2026-08-04). "This claim is stale" is only a finding if you *verify it is still wrong today*
  with a web search and cite the source.
- The **`**Short:**` MCQ-summary migration is COMPLETE** for all non-book sections. Only `book/` lacks it.
- Mermaid conversion, formula decoder blocks, the `study-paths` restructure, and the
  case-study audit are all done. Do not propose them again.
- A literal `\n` inside a Mermaid node label is **NOT a bug** (tested, renders identically). Never flag it.
- Case-study Q&As are **deliberately excluded** from the quiz bank. Never flag that as a loss.

So: your value is in **coverage, ordering, depth, and 2026 currency** — NOT in re-running finished passes.

## Your four questions
1. **What is MISSING?** Modules, deep-dive sub-files, case studies, or whole topic clusters a
   senior/principal engineer would expect in 2026 and this section does not have. Justify each with a
   web-searched signal (job posts, conference tracks, a released spec, an adopted tool, an interview
   guide) — not vibes.
2. **What is STALE or WRONG?** Content that a 2026 reader would find outdated. Web-verify before claiming.
   Cite the URL. Distinguish "the world moved" from "this was always wrong".
3. **What should be REORDERED?** Learning-path sequencing in the section README + `STUDY_ORDER`, and
   the senior/principal tier assignment of each module (senior = craft, principal = judgment; they are
   different cuts, not nested depths). Name specific moves, not principles.
4. **What is THIN or BLOATED?** Modules well under the quality bar (14 sections, 15+ Q&As, diagrams,
   concrete numbers, broken->fix examples) — and modules that are redundant duplicates of a sibling
   and should be merged or deleted.

## How to work
- **Fan out with `sonnet` subagents.** Split your section's modules into batches of ~6-8 and give each
  subagent a read-only batch to profile (line count, section headings present/missing, Q&A count,
  diagram count, concrete-number density, obvious staleness candidates). You are Opus; they are Sonnet.
  Pass `model: "sonnet"` explicitly on every subagent you spawn.
- **Use web search aggressively.** There is no budget limit. Search for: the current state of every major
  tool/framework/spec your section names; 2026 interview guides and question banks for this domain;
  what changed in the last 12 months; what the big engineering blogs are writing about.
- Read the actual files. A module's line count alone does not tell you whether it is thin.
- Be specific enough that someone could execute your plan without re-deriving it.

## Useful commands
```
python3 game/extract.py            # per-section Q&A counts + Short coverage; prints a summary
wc -l src/main/java/com/rutik/systemdesign/<section>/*/*.md
grep -c '^\*\*Q' <file>            # rough Q&A count
grep -c '^```mermaid' <file>       # diagram count
```

## Required report format — write EXACTLY this shape
```markdown
# <Section> Audit — 2026-08-25

## 0. Snapshot
One paragraph: what this section is today, its real strengths, its single biggest weakness.
Then a table: modules | case studies | sub-files | total lines | total Q&As | avg lines/module.

## 1. Missing — proposed ADDITIONS
Table: Proposed file path | Type (module/sub-file/case study) | Why now (with a cited URL) | Priority P0/P1/P2 | Est. lines
Then, for each P0, a short paragraph on what it would actually contain (its 14 sections in one line each).

## 2. Stale — verified CORRECTIONS
Table: File | Line/heading | What it says | What is true in Aug 2026 | Source URL
Only entries you web-verified. If you found none, say "none found" — that is a valid result.

## 3. Reordering — SEQUENCE and TIER changes
Table: Module | Current position/tier | Proposed | Why
Plus the full proposed `STUDY_ORDER["<section>"]` array if you are proposing changes to it.

## 4. Depth — THIN modules and BLOAT
Table: Module | Lines | Q&As | What is missing | Fix
Plus any merge/delete recommendations with the reasoning.

## 5. Cross-section notes
Anything that belongs to another section, duplicates one, or needs a cross-link. Name the other section.

## 6. Execution plan
An ordered, numbered work plan with effort estimates. Group into waves. State what can run in parallel.
Call out anything that would need `STUDY_ORDER`, the `<!-- study-paths -->` block, or the root README updated.

## 7. Confidence and gaps
What you could not verify, and what you would need to check.
```

Keep the report dense and specific. It is read by someone who will execute it.
