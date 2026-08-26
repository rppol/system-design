# Wave 2, phase A — CONTENT CREATION (parallel)

You are a wave-2 CONTENT agent for exactly one section. Repo root:
`/Users/rutikp/Prism/rutik46/systemdesign`

**PATH TRAP:** `extract.py` is at `src/main/java/com/rutik/systemdesign/game/extract.py`, NOT
the repo root. Run it from the root and it fails with "No such file or directory" while the
shell still reports EXIT=0 — a check that silently never ran.

## Read first
1. Root `CLAUDE.md` — the authoring contract.
2. `<section>/CLAUDE.md` — your section's rules.
3. `.claude/audit-2026-08-25/<section>.md` — **your worklist is its §1 (Missing) and the
   wave-2 list at the end of the fixer's report.**

## THE SPLIT — this is the whole point of phase A

**You WRITE FILES. You do NOT WIRE THEM.** Wiring is one serialized agent in phase B, because
`game/app.js`, the `<!-- study-paths -->` blocks and the generated tier tables cannot take
concurrent edits without going red.

**FORBIDDEN — every one of these belongs to phase B:**
- `game/app.js` (`STUDY_ORDER`, `SECTION_LABELS`, `BOOK_LABELS`, the accent map)
- ANY `<!-- study-paths -->` block
- ANY `<!-- study-path-table -->` generated table
- `<section>/README.md`, `<section>/CLAUDE.md`, root `README.md`, root `CLAUDE.md`
- `case_studies/case_studies.md`
- `python3 game/extract.py --write-paths` — never
- **`python3 game/extract.py --strict` — do NOT run it and do NOT report it as a failure.**
  A file on disk that is absent from its study-paths block is FATAL under `--strict` BY DESIGN.
  Between phase A and phase B the tree is EXPECTED to be red. That is not a bug and it is not
  yours to fix. Running plain `python3 game/extract.py` to see your Q&A count is fine.

Stay inside your own section. Do not read-then-edit another section's files.

## What to write

Create every file on your worklist that is P0 or P1. Skip P2 unless it is trivial.
**If a proposed file's payload has already been folded into an existing module by the wave-1
fixer, DO NOT write it — say so in your report and move on.** (fastapi's
`asgi_runtimes_and_free_threading.md` is the known instance: it is largely inside
`production_deployment_and_scaling.md` now.) Check before writing; a redundant module is worse
than a missing one.

Where the audit says content was PARTLY PRE-EMPTED — cs_fundamentals has three — **MOVE the
material and leave a stub link in the origin**, do not re-derive it and do not duplicate it.

## The rules that bind every file

**Module page** (`<module>/<module>.md`) — the 14-section template, headings exact:
`## 1. Concept Overview` / `## 2. Intuition` / `## 3. Core Principles` /
`## 4. Types / Architectures / Strategies` / `## 5. Architecture Diagrams` /
`## 6. How It Works — Detailed Mechanics` / `## 7. Real-World Examples` / `## 8. Tradeoffs` /
`## 9. When to Use / When NOT to Use` / `## 10. Common Pitfalls` /
`## 11. Technologies & Tools` / `## 12. Interview Questions with Answers` /
`## 13. Best Practices` / `## 14. Case Study`

**Case study** — the 11-section principal template (`## Intuition` then `## 1.`-`## 11.`),
**900-1100 lines**, executable code in §4, a broken->fix example in §4, named real companies in
§6, quantified impact in §9, 10+ Q&As in §11.

**Q&As — the load-bearing part:**
- Heading must be `## 12. Interview Questions with Answers`. A heading without the word
  "questions" after "interview" contributes ZERO questions to the bank, silently.
- **15 Q&A minimum**, more if your section CLAUDE.md says so. Order by interview frequency:
  gotchas and traps first, then internals, then edge cases.
- Format is `**Q: <question>?**` starting at column 0 with `**`. **NEVER number them as a
  Markdown list** — `1. **"q?"**` does not start with `**`, so the whole section becomes
  invisible to the bank.
- **Every Q&A MUST carry an authored `**Short:**` line as the FIRST non-blank line of its
  answer.** 15-220 characters (outside that range FAILS THE BUILD), self-contained, no code
  fence, no list, does not restate the question. It ships as the correct MCQ option, so it must
  stand alone among three distractors.
- **A `##` heading placed after §12 TERMINATES the bank's read of the file.** Never demote one
  to `###`; that swallows it into the Q&A span and manufactures garbage MCQs.
- Answers must be CURRENT — REPLACE, DO NOT ANNOTATE. Never "`foo()` is deprecated, use
  `bar()`"; just say `bar()`.

**Diagrams** — Mermaid preferred (`flowchart LR` default, One-Dark `classDef` on EVERY node,
short labels wrapped with `<br/>`, rounded shapes). ASCII only for constraint grids,
alignment-critical maps, and vector geometry. **A fence that does not parse FAILS THE BUILD.**
Validate every file you write:
```
node scripts/check_render.mjs <absolute-file.md>      # from the repo root; npm ci once first
```
Exit 0 = every fence renders. **Exit 3 = a 120s timeout = NOT VALIDATED, not a parse failure.**
Traps: no `;` in a sequenceDiagram Note; no bare `call` as a node id; no padding spaces after
an edge label's closing pipe; no `\"` in a label (use `#quot;`); quadrant labels containing `:`
must be quoted; quadrant coords must be `< 1.0`.

**Everything else:** no emojis (`✓`/`✗`, never `✅`/`❌`); concrete numbers everywhere; show
broken code then the fix; production war stories in §10; link to module PAGES
(`../foo/foo.md`), never bare directories.

## Verify what you assert
Every version, API, price, and product claim must be checked at a **primary source** — release
notes, the spec, the vendor's own docs — and cited in your report. Do not write from memory.

**Delegate the finding, never the verdict.** Repo-wide, audit subagent flags measured wrong
5 of 7. Rescued subagents this session contradicted three further claims outright, all sharing
one shape: **deprecated vs removed**, one word apart, indistinguishable to a skimming author,
and it inverts the advice. Under the `**Short:**` rule a confident wrong claim ships as the
graded-CORRECT option.

Use `sonnet` subagents (pass `model: "sonnet"` explicitly) to parallelize writing and
verification. **Have each write anything durable into its report text — if you are stopped,
your in-flight children report to the orchestrator, not to you.**

Do NOT `git add` or `git commit`.

## Your report — phase B depends on it being exact
For EVERY file you created:
- full path, type (module / sub-file / case study), line count, Q&A count
- **the exact `STUDY_ORDER["<section>"]` index it must be inserted at, and the entry it goes
  after** — or "sub-file, NO `STUDY_ORDER` entry" (a sub-file entry is FATAL under `--strict`)
- **the exact `<!-- study-paths -->` lines to add**, module line at column 0 and each file
  line indented with its tiers (`senior`, `principal`, `senior, principal`, or `-`)
- **A sub-file CANNOT join a tier its parent module page skips.** If yours needs a tier the
  parent lacks, say so LOUDLY — phase B must promote the parent in the same commit or the file
  ships invisible to that tier with a green build.
- for a case study: the rows needed in all five parts of `case_studies/case_studies.md`
- the README module-table row and the section CLAUDE.md row
Also list anything you deliberately did NOT write, and why.
