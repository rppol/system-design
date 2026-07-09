# Game — System Design Daily

A browser-based daily learning game built **from this repo's own content**. It turns
the ~5,600 interview Q&As scattered across all 12 sections (every module README **and
its deep-dive sub-files**; case studies excluded) into a one-click, 5-minute
multiple-choice blitz with streaks, XP, spaced repetition, per-section mastery, and an
in-app coach that curates the day's topic and reports weekly.

> **Why this exists:** consistency, not content, is the bottleneck. This section is a
> *discipline engine* targeting the four reasons daily learning fails: no nudge,
> boredom, friction, and invisible progress.

> **Template note:** `game/` is an application, not study content, so it is **exempt
> from the repo's 14-section module template** (same way `book/` declares its own
> chapter template). The files here are code, not a README-per-module.

---

## Architecture — Pages-only, `localStorage` is the single source of truth

There is **no server, no `/api`, no database, and no local scheduling stack.** The game
is a static single-page app deployed to GitHub Pages.

```
Browser (index.html + app.js + style.css + sw.js)
   GET  /                          static SPA shell (also precached by the service worker)
   GET  questions/index.json       tiny manifest: { total, sections{sec:count}, files{mod:[md]} }
   GET  questions/<section>.json   ~0.4–2 MB bank, fetched lazily only for the sections you touch
   GET  graph/<section>.json       prerequisite edges for the Learning Path (optional)
   GET  ../<section>/<module>/…md  raw repo Markdown, rendered in the in-app reader
   localStorage  sd_progress       ← streak · XP · mastery · SM-2 review schedule · history
```

- **`localStorage` `sd_progress` is the one place all progress lives.** Every write goes
  through `saveSessionLocal()` / `loadProgress()`; nothing else persists progress.
- **No mirror seam, no round-trip.** Because there is no server, there is nothing to sync
  and no `POST` — a finished session is written straight to `localStorage`.
- **Backup = export/import.** The Progress screen has **Export backup** (downloads
  `sd_progress` as JSON) and **Import backup** — that is the only way to move progress
  between browsers or devices.
- **PWA / offline.** `sw.js` precaches the app shell and lazily caches banks
  (stale-while-revalidate) + content pages (network-first), so a revisit works offline.
  The cache name embeds a per-deploy build stamp (`pages.yml` rewrites `__BUILD__`), so
  every deploy busts the shell cache; content edits arrive on the next load without a
  version bump.
- **All app colours flow from tokens** in `:root` / `[data-theme=…]` in `style.css`;
  four glass themes (Midnight, Orchid, Ember, Daylight) set `data-theme` on `<html>`.
  `?theme=<id>` previews one without persisting.

### Run it locally

Serve the **repo root** (so the reader can fetch content at `../<section>/…`):

```bash
cd /path/to/systemdesign          # the repo ROOT, not the game dir
python3 -m http.server 8901
# open http://localhost:8901/src/main/java/com/rutik/systemdesign/game/index.html
```

No `pip install`, no `npm`, no build step. In production
`.github/workflows/pages.yml` runs `extract.py` + `build_graph.py` and publishes the
whole repo root to Pages.

---

## Feature inventory

### The daily loop

- **Blitz** — one big button starts the coach's pick for today; or pick a section, then
  multi-select exactly which sub-topics to drill (select-all/clear, live count, a
  5/10/20 session-length control). Questions interleave so no two consecutive share a
  module; a graph decides which topics sit adjacent for contrast.
- **Gauntlet** — a daily **sealed** run: a deterministic 10-question recipe (oldest due →
  suggested section → weakest section → one advanced "final question"), one scored
  attempt per day, then unscored practice. Sealed days show a gold dot on the heatmap.
- **Review (spaced repetition)** — a home card surfaces questions **due** today and builds
  a cross-section deck, recall-first (options hidden until you commit). It reads as a
  plan: rounds-to-clear estimate, up to 3 per-section chips, a "+N due tomorrow" whisper.
- **Weak spots** — a card (shown when a section drops below 70%) builds a deck from your
  lowest-accuracy sections, lapsed questions first.
- **Drills** — a **confusion drill** (the two modules you most mix up, alternated) and a
  **fading refresh** (questions whose estimated retention is slipping this week).

### The learning engine

- **SM-2-lite spaced repetition** — every first-attempt answer schedules a per-question
  review (`ease`/`interval`/`reps`/`lapses`/`due`) in `sd_progress.reviews`. A
  much-slower-than-typical correct answer schedules like a low-confidence one.
- **Two-step confidence** — lock a pick, then rate **Sure / Not sure**; the tallies drive
  the Insights calibration strip and a "hard for you" difficulty label.
- **Miss loop (Redemption)** — a wrong answer re-queues the same question a few cards
  later for a flat-bonus second shot; the first miss still counts.
- **Skip → Teach 2.0** — skip a question and it returns in teach mode: the concept is
  shown first with **cloze** (tap-to-reveal) key terms, then you lock it in. Learned-from-
  a-skip counts as "learned", not scored-correct, so accuracy stays honest.
- **Interleaving + confusion tracking** — a wrong cross-module pick is recorded as a
  confusion pair (from honest distractor provenance) and feeds the confusion drill.
- **Prime (pretest)** — before reading an unquizzed module, a 3-question pretest primes
  recall (no XP, no combo, records reviews quietly), then opens the reader.
- **Explain-back** — on a wrong reveal, type the concept back; matched key terms glow,
  missed ones are highlighted, small XP for a real attempt.
- **Flip rounds** — ~30% of review items (deterministic per day+qid) flip: the answer is
  the prompt and the options are candidate questions.

### Progression

- **XP + career ladder** — correct = 10 XP × combo × boss × recall multipliers; a top-bar
  `Lv` ring derives level and title from total XP.
- **Mastery tiers** — sections earn Bronze/Silver/Gold from combined volume + accuracy.
- **The Codex** — a collection view of every module, unlocked as you practise.
- **The Skyline** — a home-screen city that grows with your sections.
- **The Ledger** — earned award chips (and Interview/Panel passes) on the Progress screen.
- **Time capsules** — a flawless full blitz buries a capsule; the question returns ~60
  days later and celebrates if it "kept".
- **Boss round, combos, double-down, comeback engine, daily-XP goal ring, streak freezes**
  (auto-cover one missed day, re-earned every 7-day milestone, cap 3), sound effects,
  confetti — all celebration flows through the single **moments engine** (`queueMoments`);
  `bonusXp` is the only XP side-channel.

### The coach (in-app, no scheduler)

- **Daily pick + voice** — `coachPick()` chooses today's topic (new territory / weak spot /
  least practised / rotation) and `coachMessage()` speaks it in a terse, dry voice; both
  computed once per boot and shown on the home card.
- **Reboarding** — a >2-day gap replaces the suggested card with a calm "the reviews kept
  your place" card.
- **Friday Debrief** — `#/debrief`: the week's deltas, a held-memory highlight, and three
  generated quests for next week (live quest chips on Home).
- **Streak nudge** — a same-day "you haven't played" toast, re-checked whenever the tab
  regains visibility (there is no OS scheduler on Pages).

### The reader (One Dark)

- **Dive deeper** — every revealed answer opens its exact source module beside the quiz.
- **One Dark surface** — code gets IntelliJ-style highlighting; ASCII §5 diagrams get
  alignment-safe colouring; **Mermaid** fences render via a lazy CDN import (flowchart,
  sequence, state, xychart, pie, quadrant, timeline, sankey), column-aware and themed.
  Authored diagrams follow four style rules — **colour every node**, **horizontal-first**
  (`LR`), **no spillover text**, **rounded corners** (see the authoring contract below).
- **Evaluate me** — every module page ends with a "Quiz this topic" launcher.
- **Section identity + masthead** — each section has an accent hue + glyph
  (`SECTION_IDENTITY`, applied as `--sec-accent` on the panel); every page opens with a
  designed masthead (badge, title, ornament rule, "~N min read · M sections") and the
  hue tints the progress bar, TOC, drop-cap, `§ n` heading ornaments, and selection.
- **Think-first recall** — §12 interview answers collapse behind "Show answer" buttons
  (active recall while reading); "Reveal all" on the heading, an `Answers` toggle in the
  Aa popover (`sd_reader_recall`), and in-page find auto-reveals answers containing a match.
- **Continue your path** — module pages end with up to three cards (next unread in the
  study order, graph-related modules, resume last read), each in its target section's hue;
  they animate in quietly the first time the page is read to the end.
- **Comfort + deep links** — drag-resize, fullscreen, collapsible index + module tree,
  reading-progress bar, `A−`/`A+` font, Aa typography popover, back-to-top, working
  cross-links, hover-to-copy heading anchors, an interactive pan/zoom **diagram
  lightbox**, and `#/reader/<path>` deep links.
- **Hard limit (see `CLAUDE.md`):** the reader surface is pitch-black `#000000` with
  `#ffffff` prose in **every** theme — only the app chrome re-themes.

### Insights (Progress screen)

Analytics derived entirely from `sd_progress`, ordered what's-due → what's-weak →
how-am-I-doing:

- **Memory forecast** — a 14-day bar chart of review-due counts (overdue rolls into
  today, which is highlighted).
- **Strongest / shakiest modules** — two 5-row lists (accuracy proxy from reps/lapses),
  each row a one-tap **Drill** into that module.
- **Your hardest questions (leeches)** — questions missed 3+ times; the list lazily fetches
  banks only when expanded, links each to its source, and has a "Drill these" button.
- **Calibration** — per-section "When sure: X% · When unsure: Y%", with an *overconfident*
  warn chip when Sure underperforms Not-sure.
- **30-day trend** — XP bars + a 7-day rolling-accuracy line (inline SVG).
- **Session log** — the last 10 runs (date · section · score · XP · duration).

### Command palette (Cmd/Ctrl+K, or `/`)

A centred glass modal with fuzzy (subsequence) search over: every section blitz, every
module's Read/Quiz command (~340 modules), and verbs (resume, start review + due count,
weak spots, gauntlet, codex, insights, debrief, quiz/flashcards toggle, theme switch,
export). Arrow keys + Enter, Esc closes. When a section's bank is already cached, a final
row runs full-text question search (top 8, each opens its reader source).

### Mobile

Under 640px: a fixed glass **bottom tab bar** (Home / Study / Progress, 44px targets; the
topbar keeps brand + stats), **sticky quiz actions**, a horizontally-scrollable heatmap
(scrolled to today), and 44px reader controls. On coarse pointers the Learning Path's
hover-only prerequisite chords are hidden and the side gutters reclaimed.

### Accessibility & motion

`prefers-reduced-motion` gates every animation (view transitions gate in JS); the beauty
pass (reveal glow sweep, directional question slide, results stagger, mastery-delta shine,
screen slide-fade) is all reduced-motion-safe. `:focus-visible`, an aria-live region,
focus management, no emojis, tabular numerals throughout.

---

## `localStorage` keys

`sd_progress` is the single source of truth; everything else is UI state or a cache.
**All persisted fields are additive** — never rename or repurpose an existing field.

| Key | Schema / purpose |
|-----|------------------|
| `sd_progress` | The one truth. `{ streak, longestStreak, lastPlayed, totalXP, sections:{sec:{seen,correct,lastPlayed,sureSeen,sureCorrect,unsureSeen,unsureCorrect}}, history:[{date,answered,correct,xp,section,durationSec,comeback?}] (cap 365), reviews:{qid:{ease,interval,reps,lapses,section,module,due,ms,flagged?,capsule?}}, freezes, freezeUsedOn:[], awards:{id:date}, deepReads, confusions:{"a\|b":n} }` |
| `sd_active_deck` | Same-day resume snapshot of an interrupted blitz (dropped on a new day). |
| `sd_gauntlet` | Today's sealed run: `{ date, qids:[], sealed, score, attempt:[] }` (ignored if `date` ≠ today). |
| `sd_coach` | Coach memory: recent picks/observations so it doesn't repeat itself. |
| `sd_recent_<section>` | Ring buffer of recently-served qids for no-repeat sampling. |
| `sd_last_mastery` | `{sec: acc%}` snapshot from the last Home render — drives the mastery-delta shine. |
| `sd_last_read` | `{ path, title }` for Study's "Continue reading" card. |
| `sd_theme` | `midnight` \| `orchid` \| `ember` \| `daylight`. |
| `sd_mode` | `quiz` \| `flash` (flashcard self-grade). |
| `sd_deck_len` | Session length `5` \| `10` \| `20`. |
| `sd_mute` | Sound on/off. |
| `sd_prime_opt` | Count of "just read" opt-outs; 3 stops the prime offer. |
| `sd_cm_<id>` | First-run coach marks seen (`first_question`/`first_combo`/`first_results`/`first_cards`). |
| `sd_reader_fs` / `sd_reader_full` / `sd_reader_toc` / `sd_reader_modules` / `sd_reader_w` / `sd_modules_w` / `sd_toc_w` | Reader font size, fullscreen, TOC/module-tree open state, pane/sidebar widths. |
| `sd_reader_font` / `sd_reader_measure` / `sd_reader_dropcap` / `sd_reader_recall` / `sd_reader_scroll` | Reader typography (serif, measure, drop-cap), think-first answers pref (`"1"` hidden = default), per-path scroll-resume map. |
| `sd_last_export` | Date of the last backup export (for the backup nudge). |

---

## Question extraction & the authoring contract

`extract.py` walks every `.md` file in each section — **module READMEs and their
deep-dive sub-files** (e.g. `llm/advanced_rag/graph_rag.md`), grouped under the parent
module — and builds `questions/<section>.json` + `index.json`. `case_studies/` and
`CLAUDE.md` are excluded. It scopes to each file's `## 12. Interview Questions` section,
treats every fully-bold line as a question and the paragraph beneath as the answer, uses
the answer's **first sentence** as the correct option, and picks **topically related**
distractors (IDF-weighted token overlap, same module first, widening to the section).
Each question record carries `id`, `section`, `module`, `moduleName`, `sourceFile`,
`difficulty`, `question`/`questionMd`, `answerFull`/`answerFullMd`, `correct`/`correctMd`,
`distractors`/`distractorsMd`, `distractorIds`, and `concepts`.

Question ids are **content-stable** (`module#md5(module|question)[:12]`), so re-running
`extract.py` after content edits does *not* orphan spaced-repetition state (the review
deck also self-heals by dropping ids that left the bank). Extraction is fully
deterministic (sorted walk, seeded RNG) — re-runs produce byte-identical output.

Authoring rules (unchanged, enforced by the game contract in the root `CLAUDE.md`):

```bash
cd src/main/java/com/rutik/systemdesign/game
python3 extract.py     # re-run after editing ANY module's Q&A, then reload the reader
```

- Q&As live under `## 12. Interview Questions with Answers`; each question line starts
  with `**`; the answer's **first sentence must be a self-contained 15–220 char answer**
  (shorter/longer → silently dropped). The **15-Q&A floor** guarantees enough distractors.
- Every new module directory must be added to `STUDY_ORDER["<section>"]` in `app.js` at
  its learning-path position (sub-files need no entry — they group under their parent).
- Mermaid fences are valid only in study section files, never in `game/` or `CLAUDE.md`.
- **Mermaid diagram style rules (owner-set 2026-07-07) — all four, every diagram:**
  (1) **Colour every node** with the One-Dark `classDef` palette, semantic-by-role — the
  reader's grey auto-tint is only a fallback for legacy diagrams, not the target.
  (2) **Horizontal-first** — default `flowchart LR`; `TD`/`TB` only for genuinely vertical
  hierarchies/lifecycles or when `LR` would overflow (then `subgraph`s).
  (3) **No spillover text** — short labels, wrap long ones with `<br/>`.
  (4) **Rounded corners** — the reader rounds every box; use rounded shapes
  `(label)`/`([label])` for GitHub parity. Full detail: root `CLAUDE.md` → "Mermaid
  Diagrams" and the `/mermaid-diagrams` skill.

---

## Files

| File | Role |
|------|------|
| `index.html`, `app.js`, `style.css` | The single-page game (vanilla JS/CSS, no build). |
| `sw.js` | Service worker: precache the shell, lazily cache banks + content (PWA/offline). |
| `manifest.webmanifest` | PWA manifest. |
| `extract.py` | Builds `questions/<section>.json` + `index.json` from all module READMEs + sub-files. |
| `build_graph.py` | Builds `graph/<section>.json` (prerequisite edges) for the Learning Path. |
| `questions/`, `graph/` | Generated data (committed so the game runs with no build). |

---

## QA harness pattern

Features are smoke-tested with headless Chrome over the Chrome DevTools Protocol (raw Node
`WebSocket`, no Puppeteer). The pattern (see `/tmp/qa_phase*.mjs`):

- Serve the repo root (`python3 -m http.server <port>`), launch
  `--headless=new --remote-debugging-port=<cdp>` against
  `…/game/index.html?qa=1`, and **wipe the Chrome profile each run** (`--user-data-dir` →
  `rm -rf`) so a stale service worker can't serve an old `app.js`.
- `?qa=1` exposes a `window.__qa` debug handle (`state`, `correctIdx()`, `loadBank`,
  `moduleStats`, `openPalette`, `forecastData`, `leechIds`, … — additive per phase) so a
  driver can read internals and call helpers without guessing from the shuffled DOM.
- **Answering is two-step:** click an option → confidence **Sure/Not sure** → reveal
  (prime/redemption/test grade instantly and skip the confidence bar).
- Deterministic seams: `?qa_date=YYYY-MM-DD` and `?qa_time=HH:MM` override "today"/"now"
  everywhere date/time math flows through `todayISO()` / `nowHM()`.
- Seed a scenario by writing a `sd_progress` object to `localStorage`, then navigate and
  assert on the rendered DOM + `__qa.state`.

---

## Deliberately out of scope

- **Per-distractor "why this is wrong" text** — **done, honestly.** A wrong pick whose
  distractor resolves to another question shows real *provenance* ("You picked the answer
  to: <that question> — from <that module>") plus a "Read that instead" link. No
  fabricated explanations: it only ever cites the actual source Q&A the distractor came
  from.
- **Hearts / lives** — still rejected. Punishing wrong answers discourages the exploration
  a *learning* tool should reward.
- **Shareable weekly card / social** — still rejected. There is no social surface and no
  accounts/multi-user, so it has no pull for a solo local tool.

### Out of scope

Accounts/multi-user, a server or database, cloud hosting, frontend frameworks/build
tooling, and a native mobile app. The whole point is a zero-friction static tool whose
only state is `localStorage`.
