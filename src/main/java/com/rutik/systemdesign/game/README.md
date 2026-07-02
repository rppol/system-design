# Game — System Design Daily

A browser-based daily learning game built **from this repo's own content**. It turns
the ~5,600 interview Q&As scattered across all 12 sections (every module README **and
its deep-dive sub-files**; case studies excluded) into a one-click, 5-minute
multiple-choice blitz with streaks, XP, and per-section mastery — paired with a Claude
Code scheduled task that nudges you, curates the day's topic, rescues your streak, and
reports weekly.

> **Why this exists:** consistency, not content, is the bottleneck. This section is a
> *discipline engine* targeting the four reasons daily learning fails: no nudge,
> boredom, friction, and invisible progress.

### Modes & controls

- **Suggested topic** — one big button starts the coach's pick for today (all sub-topics).
- **Pick a section, then sub-topics** — choose any section (LLM, LLD, HLD, …), then
  multi-select exactly which modules to drill (e.g. LLM → only `advanced_rag`,
  `rag_fundamentals`, `fine_tuning`). Select-all / clear; live question count.
- **Review (spaced repetition)** — a home-screen card surfaces questions due for review
  (driven by an SM-2 schedule over your past answers) and builds a cross-section deck.
- **Drill your weak spots** — a home-screen card (shown when a section is below 70%) builds
  a deck from your lowest-accuracy sections, prioritising questions you've lapsed on.
- **Quiz / Cards toggle** — a top-bar switch turns any deck into flashcard self-grade mode
  (reveal → "Got it / Missed it"), feeding the same spaced-repetition schedule.
- **Skip for now** — defer a question; it returns at the end in **teach mode** (the
  full concept is shown first, then you lock it in by answering). Skipped-then-learned
  questions count as "learned," not as a scored-correct, so accuracy stays honest.
- **Boss round** — `advanced`-difficulty questions come last and are worth 2x XP.
- **Engagement** — answer-combo XP multiplier, a daily-XP goal ring, streak freezes, an
  activity heatmap, mastery tiers + level, sound effects (toggle in the top bar), and
  confetti on a flawless blitz.
- **Keyboard** — `1`–`4` to answer, `Enter` for next, `S` to skip; in Cards mode `Space`
  reveals and `1`/`2` grade Missed/Got. In the reader: `F` toggles fullscreen, `Esc`
  exits fullscreen (then closes). In the diagram lightbox: `+`/`−`/`0` zoom, arrows pan.
  `?` opens a shortcuts overlay anywhere. **Mouse back/forward buttons** navigate the
  reader (back-stack → prev/next topic → close) and return to the previous screen.
- **Post-round review** — the results screen lists every miss (and teach-mode learn)
  with the correct answer and a dive-deeper link, under an animated score ring.
- **Find fast** — type-to-filter inputs on the sub-topic picker and Study topic lists;
  Study also shows a "Continue reading" card for the last-opened page.
- **Reader comfort** — reading-progress bar, `A−`/`A+` font-size controls (persisted),
  back-to-top button, and full-name hover tooltips on truncated sidebar/TOC entries.
- **Themes** — a top-bar picker with four glass themes (Midnight, Aurora, Ember,
  Daylight), each driving every color token plus the animated aurora-mesh backdrop.
  Persisted to `localStorage`; `?theme=<id>` in the URL previews one without saving.
  All surfaces are translucent glass (`backdrop-filter`) over the mesh + blueprint grid.
- **Study mode** — a top-bar `Study` button opens a pure-reading browser: pick a section,
  then any topic, and read it in the full reader with Previous/Next-topic navigation.
- **Dive deeper** — every revealed answer opens its source module in the in-app reader
  (rendered Markdown beside the quiz), no tab switch needed.
- **The reader** — a One Dark reading surface: code blocks get IntelliJ-style syntax
  highlighting; ASCII §5 diagrams get alignment-safe colouring (muted scaffolding, blue
  `[labels]`, cyan flow arrows); headings/questions/answers are colour-coded. It is
  **drag-resizable**, has a **fullscreen** mode, an **always-accessible collapsible index**
  (sidebar built from the headings), and **working cross-links** (relative `.md` links
  open in-reader with a Back button). **Diagrams are interactive**: Mermaid *and* ASCII
  diagrams open in a lightbox with drag-to-pan, zoom-toward-cursor (wheel, buttons,
  double-click, keyboard), and fit-to-viewport; Mermaid nodes glow on hover; every code
  fence gets a hover copy button. **Hard limit (see CLAUDE.md): the reader surface is
  pitch-black `#000000` with `#e6e6e6` prose in every theme** — only the app chrome
  re-themes; code, ASCII diagrams, and Mermaid stay One Dark.

> **Template note:** `game/` is an application, not study content, so it is **exempt
> from the repo's 14-section module template** (same way `book/` declares its own
> chapter template). The files below are code, not a README-per-module.

---

## Quick start

```bash
cd src/main/java/com/rutik/systemdesign/game
python3 server.py            # serves on http://127.0.0.1:8777
# open http://127.0.0.1:8777/ in a browser
```

No `pip install`, no `npm`, no build step — Python 3 standard library only.

Rebuild the question bank after editing any module's Q&A:

```bash
python3 extract.py           # re-reads all READMEs -> questions/<section>.json
```

---

## How it works

```
Browser (index.html + app.js + style.css)
   |  GET /                    static SPA
   |  GET /questions/index.json   tiny manifest (section -> count)
   |  GET /questions/<sec>.json    ~400-800KB, loaded only for today's topic
   |  GET /api/today          coach's daily pick + message ({} if not set)
   |  GET /api/progress        streak / XP / per-section accuracy
   |  POST /api/progress       record a finished session, recompute streak
   v
server.py   (stdlib http.server; persists state/, serves files)
        ^
        |  reads progress.json, writes today.json, opens browser, notifies
Claude scheduled task  ->  driven by claude_coach_prompt.md
```

**Separation of concerns:** `server.py` is deliberately dumb (files + JSON
persistence). All intelligence — which topic to study, the nudge wording — lives in
the Claude coach (`claude_coach_prompt.md`), which writes `state/today.json`. The game
has its own fallback picker, so it still works when the coach hasn't run (weekends,
missed days).

### Question extraction

`extract.py` walks every `.md` file in each section — **module READMEs and their
deep-dive sub-files** (e.g. `llm/advanced_rag/graph_rag.md`), grouped under the parent
directory's module, so deep dives count toward their topic. `case_studies/` and
`CLAUDE.md` are excluded. It scopes to each file's `## 12. Interview Questions` section
and treats every fully-bold line as a question and the paragraph beneath as the answer
(handling all four `Q`-label variants found in the repo). Each MCQ uses the answer's
**first sentence** as the correct option (CLAUDE.md guarantees the first sentence is
the direct answer). Distractors are chosen to be **topically related**, not random: an
IDF-weighted token-overlap model ranks other answers (same module first, widening to the
section) by how many distinctive words they share with the question + answer, preferring
the most-related while skipping any candidate too close to the correct answer (Jaccard
> 0.7). This yields ~2.5x more topic overlap than random distractors, so you can't pick
the answer by keyword-matching alone. Output is split per section for lazy loading.

Question ids are **content-stable** (`module#md5(module|question)[:12]`), so
re-running `extract.py` after content edits does *not* orphan spaced-repetition
state (ids only change when a question's own text changes; the review deck also
self-heals by dropping ids that left the bank). Extraction is fully deterministic
(sorted walk, seeded RNG, total sort keys) — re-runs on any machine produce
byte-identical output, and exact duplicate questions within a module are deduped.

---

## Files

| File | Role |
|------|------|
| `extract.py` | Builds `questions/<section>.json` + `index.json` from all module READMEs |
| `questions/` | Generated bank (committed so the game runs with no build) |
| `index.html`, `app.js`, `style.css` | The single-page game (vanilla JS) |
| `server.py` | Stdlib server: static files + `/api/today` + `/api/progress` |
| `state/` | Runtime only (gitignored): `progress.json`, `today.json`, `coach.log` |
| `pick_today.py` | Deterministic (no-LLM) topic picker; writes `today.json` |
| `claude_coach_prompt.md` | Instructions the scheduled task runs (`main` / `checkin` / `weekly`) |
| `scheduling/` | `launchd` plists + `coach.sh` for the durable daily trigger — see [`scheduling/README.md`](scheduling/README.md) |

---

## The daily Claude coach

Three local cron triggers, all driven by `claude_coach_prompt.md`:

| Trigger | Schedule | Job |
|--------|----------|-----|
| Main session | `0 11 * * 1-5` | Pick today's topic, write `today.json`, open the game, send a streak-aware nudge |
| Check-in | `30 16 * * 1-5` | If you haven't played, send a streak-rescue reminder |
| Weekly report | `0 17 * * 5` | Summarize streak, accuracy by section, and next week's focus |

These must run **locally** (they read local files, open your browser, and notify).
See `claude_coach_prompt.md` for the exact per-mode behavior.

**Important — make it durable:** In-session Claude jobs (`CronCreate`) only fire
while Claude Code is open and idle and expire after 7 days, so they can't reliably
nudge you at 11am. For a real daily habit, install the macOS `launchd` agents in
[`scheduling/`](scheduling/README.md) — they run independently of Claude, and the
driver (`coach.sh` + `pick_today.py`) picks the topic and notifies with plain Python
(no tokens, no dependency on Claude running).

---

## Already built (beyond the MVP)

The MVP was the multiple-choice blitz. Since then the following shipped:

- **Section + sub-topic picker** — choose a section, then multi-select exactly which
  modules to drill (part of the old "pick-your-mode" idea).
- **Skip → teach-back** — skipped questions return at the end with the concept shown first.
- **Spaced repetition + review deck** — per-question SM-2 scheduling in `progress.json`;
  a "due for review" card on the home screen builds a cross-section review deck.
- **Boss round** — `advanced`-difficulty questions are ordered last and worth 2x XP.
- **Engagement layer** — answer-combo XP multiplier, daily-XP goal ring, sound effects
  (mutable), XP count-up, and confetti on a flawless blitz.
- **Keyboard controls** — `1`–`4` to answer, `Enter` for next, `S` to skip.
- **Dive deeper** — each answer opens its source module in an in-app split-view reader
  (see below), with a fallback to the server's `/content/` route.
- **Streak freeze / grace** — banked freeze tokens auto-cover a single missed day so a
  lone slip doesn't reset a long streak; one is re-earned at each 7-day milestone (cap 3).
  Shown as a `❄` chip on the home hero and a badge on the progress screen.
- **Activity heatmap** — a GitHub-style contribution grid on the progress screen, built
  from the existing `history` array, so daily consistency is visible at a glance.
- **Drill your weak spots** — a one-tap deck that pulls from your lowest-accuracy sections
  (prioritising questions you've lapsed on); surfaces on the home screen when a section
  drops below 70%.
- **In-app reader (One Dark)** — "Dive deeper" slides the quiz aside and renders the full
  source module beside it (Markdown → HTML via a hand-written, dependency-free renderer).
  It ships:
  - **Code syntax highlighting** — a compact hand-rolled tokenizer (java, python, sql,
    yaml, bash, json, js, dockerfile, properties) styled with the One Dark palette.
  - **Alignment-safe diagram colouring** — ASCII §5 diagrams are colour-coded by wrapping
    characters only (never altering them, so columns stay aligned): muted box/connectors,
    cyan flow arrows, blue `[labels]`, orange numbers.
  - **Semantic Markdown colours** — module title, section headings, questions (yellow) and
    answers each get a distinct One Dark colour.
  - **Resizable** (drag the left edge; width persists), **fullscreen** (`F`), and an
    **always-accessible collapsible index** (sidebar TOC from the headings; auto-shown in
    fullscreen, toggled by the `☰` button otherwise).
  - **Working cross-links** — relative `.md` links resolve and open in the reader with a
    Back button; in-page anchors and the TOC scroll within the pane; external links open
    in a new tab.
- **Study mode** — a top-bar `Study` button: a pure-reading browser (section → topic →
  reader) with Previous/Next-topic navigation. No quiz, no clock.
- **Flashcard self-grade mode** — a top-bar `Quiz / Cards` toggle switches any deck to
  active recall: see the question, reveal the answer, self-rate "Got it / Missed it". It
  feeds the *same* spaced-repetition schedule as the blitz; XP is flat (no combo/boss) so
  self-grading can't inflate score versus the verifiable multiple-choice path.
- **Mastery tiers + level** — sections earn Bronze/Silver/Gold from combined volume and
  accuracy (shown on the progress screen); a global `Lv` chip in the top bar derives from
  total XP.
- **Mastery-decay nudge** — the home screen flags your most-invested section when it
  hasn't been practiced in a week ("X is getting rusty — N days"), one tap to refresh it.

- **Visual overhaul (2026-07-02)** — token-driven design system in `style.css`: 4 glass
  themes over an animated aurora-mesh + blueprint-grid backdrop, SF-style display type
  with a monospace "data voice", unified button/motion system, single-hue chart ramps,
  SVG icons replacing emoji, `prefers-reduced-motion` + `:focus-visible` + aria-live
  announcements + focus management, and the interactive diagram lightbox (pan/zoom for
  Mermaid + ASCII). Engine hardening from a full audit shipped alongside: stable
  question ids + review-state migration, offline-session replay queue, key-repeat and
  double-click guards, DST-safe heatmap, Mermaid CDN retry, server write-lock, and
  bank revalidation caching (`no-cache` + 304s instead of `no-store` re-downloads).
- **Round 2 (same day)** — graphics/usability layer: pointer-spotlight on glass cards,
  scroll-driven reveals (`.rise`), aurora-mesh pointer parallax, gradient shimmer,
  staggered option dealing, floating `+XP` particles, combo shockwaves, boss-banner
  pulse, animated results score ring, View-Transitions cross-fades, date eyebrow, tile
  mini mastery bars, difficulty chips, `n/N` counter, post-round miss review,
  type-to-filter, `?` shortcuts overlay, mouse back/forward navigation, reader
  progress bar + font controls + back-to-top + sidebar tooltips. Reader hard limits
  locked in `CLAUDE.md`: pitch-black `#000` surface, `#e6e6e6` prose, every theme.

## Planned / to be implemented

- _(nothing queued)_ — the deep-dive "learn, then test" flow is now covered by **Study
  mode** plus the in-app reader and teach-mode.

### Deliberately out of scope

- **Hearts / lives** — punishing wrong answers discourages the exploration a *learning*
  tool should reward.
- **Shareable weekly card** — there is no social surface to share into (accounts/multi-user
  are out of scope), so it has no pull for a solo local tool.
- **Per-distractor "why this is wrong" text** — distractors are auto-generated by IDF
  overlap in `extract.py`; there is no authored explanation to show, so it would risk
  fabricated content.

### Out of scope

Accounts/multi-user, cloud hosting, a database, frontend frameworks/build tooling, and
a mobile app. The whole point is a zero-friction local tool.
