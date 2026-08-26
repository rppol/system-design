# WAVE 3 — RESUME CHECKPOINT (written 2026-08-27 00:21 IST)

**If you are a fresh session, start here.** Everything below is measured from disk, not recalled.

## The goal (owner-set)
Build the deferred audit backlog: P0 + P1 + P2 files, 7 new books, ~15 in-place edits.
**EXPLICITLY EXCLUDED by the owner: `book/`'s `**Short:**` migration — "never gonna use that".**
Max 3 parallel agents, sequential waves.

## HOW TO RESUME IN ONE COMMAND
```bash
cd /Users/rutikp/Prism/rutik46/systemdesign/src/main/java/com/rutik/systemdesign
python3 game/extract.py 2>&1 | grep -E 'WIRING ERROR|Per-section|^  [a-z]' | head -40
```
Every `WIRING ERROR` naming a file that EXISTS on disk is wave-3 output awaiting the wiring pass.
That listing IS the worklist. **Do not rebuild a worklist from the audit reports — they predate
waves 1-2 and over-count by roughly half.**

## STATE: content waves

| Wave | Agents | Status |
|---|---|---|
| 1 | backend · technologies · python+database | **DONE** — 24 + 8 files |
| 2 | devops+cs_fundamentals · ml+llm | **IN FLIGHT at checkpoint** |
| 3 | lld+hld+P2 | **IN FLIGHT at checkpoint** |
| 4 | 7 new books | **NOT STARTED** |
| 5 | wiring (orchestrator, serial) | **NOT STARTED** |

### Wave 4 — the 7 books, not started
`the_staff_engineers_path` (14 ch) · `staff_engineer_larson` (4 parts) · `database_internals`
(13 ch) · `site_reliability_engineering` (selected) · `building_microservices` (16 ch) ·
`ai_engineering` (10 ch) · `generative_ai_system_design_interview` (11 ch).
Follow `book/CLAUDE.md`: chapter template, NOT the 14-section module template. **`book/` is
answerable to its SOURCE, not to 2026 reality — REPLACE-DO-NOT-ANNOTATE is INVERTED there.**
Establish each real ToC from a primary source FIRST and stop if it cannot be established.
New books need `BOOK_LABELS` + `STUDY_ORDER.book` entries (book ids are 3 segments).

### Wave 5 — the wiring pass (SERIAL, one agent or the orchestrator)
Full spec in `WAVE2-WIRING.md`. Order: tier promotions FIRST, then `STUDY_ORDER`, then
study-paths, then README/CLAUDE.md rows and `case_studies.md`, then `--write-paths`, `--strict`,
then the WHOLE of `scripts/build_banks.sh`.
**NEVER paste a STUDY_ORDER index from a report — grep the named predecessor and insert after it.**

## HARD-WON RULES (violating these has cost real damage)
1. `extract.py` is at `src/main/java/com/rutik/systemdesign/game/extract.py`. From the repo root
   it fails with "No such file" **while the shell reports EXIT=0**.
2. **A sub-file cannot join a tier its parent module page skips** — it ships invisible on a
   GREEN build. Known ceilings: backend's 4 sub-files, python `packaging_and_project_tooling`
   (principal-only), database `graph_databases` (`-`), `postgresql_internals` (senior-only).
3. **Never run a bulk edit keyed on a numeric heuristic.** A Q&A-count corrector guarded on
   `abs(diff) <= 25` overwrote 130 cells including single-digit PHASE columns. Enumerate targets.
4. Count Q&As with `grep -c '^\*\*Q:'` — the bare `^\*\*Q` matches prose starting "Qu…".
5. `node_modules` is SHARED. Do NOT let agents run `npm ci`; ~11 concurrent installs corrupted it
   once. Validate diagrams centrally with `bash scripts/prerender_diagrams.sh` (NOT
   `build_diagrams.mjs`, which needs a server on :8901 it does not start).
6. Pushing needs the **rppol** owner PAT via a GIT_ASKPASS shim plus
   `git -c credential.helper= push` (osxkeychain answers first otherwise). A rutik46 token
   cannot push. **The PAT the owner pasted should be treated as compromised — ask before reuse.**
7. A stale `.git/index.lock` in this repo is usually genuinely stale — **check its age** before
   blaming a concurrent process. One was 11.2 hours old with no live git.

## LAST GREEN STATE (before wave 3 began)
Commit `537704c6`, pushed, Pages + APK both green. 13,999 questions, 16 sections.
Live: https://rppol.github.io/system-design/src/main/java/com/rutik/systemdesign/game/index.html
