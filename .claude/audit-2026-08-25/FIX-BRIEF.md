# Fix Brief — 2026-08-25

You are a FIX agent. The audit phase is over; you are implementing. Repo root:
`/Users/rutikp/Prism/rutik46/systemdesign`.

## Wave 1 is CONTENT-ONLY. This is the most important rule here.

**Do NOT touch any of these files:**
- `game/app.js` — `SECTION_LABELS`, `STUDY_ORDER`, the accent map. Another agent owns it right now.
- Any `<!-- study-paths -->` block in any section `README.md`.
- Any `<!-- study-path-table -->` generated table.
- Any other section's files. Stay inside the section you were assigned.

**Do NOT create or delete content files in wave 1.** Rewriting an existing file in place
changes no inventory, so it needs no wiring. Creating one does, and concurrent wiring edits
take the Pages deploy red. If your audit calls for a NEW module or case study, **do not
create it** — list it in your final report for wave 2 and move on.

`python3 game/extract.py --write-paths` is FORBIDDEN in wave 1.

## The editing rules that bind every change

1. **REPLACE, DO NOT ANNOTATE.** An answer never says "`foo()` is deprecated, use `bar()`";
   it says `bar()`. Do not leave "(as of 2024)" or "formerly X" scaffolding behind. Three
   narrow exceptions: a broken→fixed pair where the old form IS the lesson, an answer whose
   explicit subject is a migration, and deprecation-as-subject (teaching the discipline of
   handling deprecation).
2. **THE SHORT LINE IS YOURS NOW.** Every Q&A carries an authored `**Short:**` line as the
   first non-blank line of its answer. **If you change an answer, you MUST re-read the
   `**Short:**` above it and update it in the same edit.** A stale Short contradicting a
   corrected answer still parses, still passes `--strict`, and ships as the CORRECT MCQ
   option. This is the one place a correct fix introduces a new defect. 15–220 chars, no
   code fence, no list, self-contained.
3. **Never lose a word of `answerFull`.** The Short line is added above the answer, never
   carved out of it.
4. **Q&A format is load-bearing.** Questions are `**Q: <question>?**` starting at column 0
   with `**`. Never number Q&As as a Markdown list. The heading stays
   `## 12. Interview Questions with Answers`.
5. **No emojis.** `✓`/`✗`, never `✅`/`❌`.
6. **Mermaid must parse.** One-Dark `classDef` on every node, `flowchart LR` default, short
   labels with `<br/>`, rounded shapes. A fence that fails to parse exits `build_diagrams.mjs`
   with 1 and takes the deploy red. Validate anything you touch:
   `node scripts/check_render.mjs <absolute-file.md>` (run `npm ci` once first).
   Traps: no `;` in a sequenceDiagram Note; no bare `call` as a node id; no padding spaces
   after an edge label's closing pipe; no `\"` in a label; quadrant labels with `:` must be
   quoted; quadrant coords must be `< 1.0`. A literal `\n` in a label is NOT a bug — leave it.
7. **Cite what you verified.** Every factual correction must be web-verified against a
   primary source (release notes, the spec, the vendor's docs). Do not "correct" from memory.

## The 11-section principal case-study template

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

Quality bar: **900–1100 lines**; executable code in §4; a broken→fix example in §4; concrete
numbers everywhere; **named real companies** in §6; quantified impact in §9; **10+ Q&As in §11**.

**`cs_fundamentals/case_studies/` is EXEMPT** — its six files use a documented adapted
interview-walkthrough template (`cs_fundamentals/CLAUDE.md` §Case Studies). Never rewrite
them to the template above. If you are not the cs_fundamentals agent this does not apply
to you anyway; it is recorded so nobody "fixes" it later.

## Verification before you report done

```
python3 game/extract.py            # per-section counts + Short coverage
python3 game/extract.py --strict   # must exit 0
```
Report the actual output. `--strict` passing is necessary, not sufficient — but the full
`scripts/build_banks.sh` is run centrally at the end of wave 1, not by you (it is not safe
to run concurrently from many agents).

Do NOT `git add` or `git commit`. Leave changes in the working tree.

## Your final report

- Every file you changed, and what changed in it, one line each.
- Every `**Short:**` line you updated as a consequence of an answer edit.
- Every correction with its source URL.
- The `extract.py --strict` output.
- **A wave-2 list:** new modules/case studies you did NOT create, and the exact wiring each
  would need (`STUDY_ORDER` position, study-paths lines, README rows).
