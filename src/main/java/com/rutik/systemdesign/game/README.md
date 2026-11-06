# Game — LORA (Learn Often, Recall Always), by Rutik

A browser-based daily learning game built **from this repo's own content**. It turns
the ~19,000 interview Q&As across every section (every module page **and its deep-dive
sub-files**; case-study Q&As feed only a separate reader quiz pool) into a one-click, 5-minute
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
   GET  questions/index.json       manifest: { generatedAt, total, sections, files, moduleCounts, caseStudies }
   GET  questions/<section>.json   ~0.6-5.6 MB bank, fetched lazily only for the sections you touch
   GET  questions/paths.json       Senior/Principal study tiers per section
   GET  questions/tech.json        Technologies screen index (+ tech_index.json knowledge bank)
   GET  case_questions/<section>.json  case-study reader quiz pool
   GET  graph/<section>.json       prerequisite edges for the Learning Path (optional)
   GET  diagrams/<key>.mmz         pre-rendered Mermaid SVG (gzipped JSON)
   GET  vendor/icons-<pack>.json   iconify packs, only when the Mermaid engine loads
   GET  ../<section>/<module>/…md  raw repo Markdown, rendered in the in-app reader
   localStorage  sd_progress       ← streak · XP · mastery · SM-2 review schedule · history
```

- **`localStorage` `sd_progress` is the one place all progress lives.** Every write goes
  through the quota-safe `safeSet()` wrapper (`saveSessionLocal()` for session
  aggregates); only backup import writes raw.
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
  eight colour themes (Midnight, Ocean, Forest, Ember, Fire, Rose, Orchid, Obsidian) set
  `data-theme` on `<html>`, and an independent light/dark mode sets `data-mode`.
  `?theme=<id>` previews one without persisting.

### Run it locally

Serve the **repo root** (so the reader can fetch content at `../<section>/…`):

```bash
cd /path/to/systemdesign          # the repo ROOT, not the game dir
python3 -m http.server 8901
# open http://localhost:8901/src/main/java/com/rutik/systemdesign/game/index.html
```

Run `bash scripts/build_banks.sh` once first: the banks and graphs are gitignored. No
`pip install`. In production `.github/workflows/pages.yml` runs `scripts/build_banks.sh`
and `scripts/prerender_diagrams.sh`, then publishes the whole repo root to Pages.

---

## Feature inventory

### The daily loop

- **Blitz** — one big button starts the coach's pick for today, or resumes a paused blitz
  (starting any other deck while one is paused asks first); or pick a section, then
  multi-select exactly which sub-topics to drill (select-all/clear, live count, a
  5/10/20 session-length control). Questions interleave so no two consecutive share a
  module; a graph decides which topics sit adjacent for contrast.
- **Ghost rematch** — full-section results compare against your previous best run of
  that section ("vs you, <date>"); review/drill/gauntlet runs are excluded as not
  apples-to-apples.
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
- **Interviewer** (`#/interview/<section>`): unlocks at Silver (20 questions seen at 70%
  accuracy); 4 core, 4 intermediate, then 4 advanced questions. Gold opens a
  12-advanced panel. Passes land in the Ledger.

### Study and Technologies

- **Study paths**: Full, Senior, Principal, and Case Studies tabs per section from
  `questions/paths.json`, with a Level filter on case studies.
- **Technologies** (`#/tech`): By technology and Tradeoffs tabs, a section filter, and
  tier/role facets, from `questions/tech.json` and `tech_index.json`.
- **Light/dark mode**, independent of the colour theme.

### The learning engine

- **SM-2-lite spaced repetition** — every first-attempt answer schedules a per-question
  review (`ease`/`interval`/`reps`/`lapses`/`due`) in `sd_progress.reviews`. A
  much-slower-than-typical correct answer schedules like a low-confidence one.
- **Two-step confidence** — pick an option (another tap moves the pick), then rate **Sure /
  Not sure** from the action bar; the tallies drive the Insights calibration strip and a
  "hard for you" difficulty label.
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
- **Reading streak** — the Home streak line carries a chip with modules read today and
  the consecutive-day reading streak, tracked separately from the quiz streak.
- **Mastery tiers** — sections earn Bronze/Silver/Gold from combined volume + accuracy.
- **The Codex** — a collection view of every module, unlocked as you practise.
- **The Skyline**: a city on Progress > Awards that grows with your sections.
- **The Ledger**: earned award chips (and Interview/Panel passes) on Progress > Awards.
- **Time capsules** — a flawless full blitz buries a capsule; the question returns ~60
  days later and celebrates if it "kept".
- **Boss round, combos, double-down, comeback engine, daily-XP goal ring, streak freezes**
  (auto-cover one missed day, re-earned every 7-day milestone, cap 3), sound effects,
  confetti — all celebration flows through the single **moments engine** (`queueMoments`),
  which plays at most two moments after a deck and folds the rest into one "Also" moment;
  `bonusXp` is the only XP side-channel.

### The coach (in-app, no scheduler)

- **Daily pick + voice** — `coachPick()` chooses today's topic (new territory / weak spot /
  least practised / rotation) and `coachMessage()` speaks it in a terse, dry voice; both
  computed once per boot and shown on the home card.
- **Reboarding** — a >2-day gap replaces the suggested card with a calm "the reviews kept
  your place" card.
- **Weekly debrief**: `#/debrief`, XP per day, the week's deltas, a held-memory highlight,
  and three generated quests. Each quest chip (also on Home) starts the deck that moves it.
- **Streak nudge** — a same-day "you haven't played" toast, re-checked whenever the tab
  regains visibility (there is no OS scheduler on Pages).

### The reader (One Dark)

- **Dive deeper** — every revealed answer opens its exact source module beside the quiz.
- **One Dark surface** — code gets IntelliJ-style highlighting; ASCII §5 diagrams get
  alignment-safe colouring; **Mermaid** fences render from build-time SVGs
  (`diagrams/<key>.mmz`), and the pinned `mermaid@11.16.0` engine loads only for a fence
  with no pre-rendered asset (flowchart, sequence, state, xychart, pie, quadrant,
  timeline, sankey), column-aware and themed.
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
- **30-day trend** — XP bars + a 7-day rolling-accuracy line (inline SVG).
- **Calibration** — per-section "When sure: X% · When unsure: Y%", with an *overconfident*
  warn chip when Sure underperforms Not-sure.
- **Session log** — the last 10 runs (date · section · score · XP · duration).

### Command palette (Cmd/Ctrl+K, or `/`)

A centred glass modal with fuzzy (subsequence) search over: every section blitz, every
module's Read/Quiz command (book chapters included), and commands (resume, start review +
due count, every main screen, weak spots, quiz/flashcards toggle, export, theme and mode).
With no query it lists three groups: Continue (resume, and start review when something is
due), Go to, and Actions; themes and modes appear once you type. Arrow keys + Enter, Esc closes. When a section's bank is already cached, a final
row runs full-text question search (top 8, each opens its reader source).

### Mobile

Below 47rem: a fixed glass **bottom tab bar** (Today / Practice / Study / Tech /
Progress; the topbar keeps brand + stats); at 47rem and up the same five sit in a left
rail that collapses to icons (`[`). Coarse pointers get 44px targets. Also **sticky quiz
actions**, a horizontally-scrollable heatmap
(scrolled to today), and 44px reader controls. On coarse pointers the Learning Path's
hover-only prerequisite chords are hidden and the side gutters reclaimed.

### Accessibility & motion

`prefers-reduced-motion` gates every animation (view transitions gate in JS); the beauty
pass (reveal glow sweep, results stagger, mastery-delta shine, screen slide-fade between
routes) is all reduced-motion-safe. A screen plays one entrance per navigation, and the next
question swaps in instantly so a fast tap is never lost to a transition. `:focus-visible`, an aria-live region,
focus management, no emojis, tabular numerals throughout.

---

## `localStorage` keys

`sd_progress` is the single source of truth; everything else is UI state or a cache.
**All persisted fields are additive** — never rename or repurpose an existing field.

| Key | Schema / purpose |
|-----|------------------|
| `sd_progress` | The one truth. `{ streak, longestStreak, lastPlayed, totalXP, sections:{sec:{seen,correct,lastPlayed,sureSeen,sureCorrect,unsureSeen,unsureCorrect}}, history:[{date,answered,correct,xp,section,durationSec,comeback?}] (cap 365), reviews:{qid:{ease,interval,reps,lapses,section,module,due,ms,flagged?,capsule?}}, freezes, freezeUsedOn:[], awards:{id:date}, deepReads, confusions:{"a\|b":n}, readModules:{path:date}, reading:{day,count,streak,longest,todayKeys}, questClears:{week,count} }` |
| `sd_active_deck` | Same-day resume snapshot of an interrupted blitz (dropped on a new day). |
| `sd_gauntlet` | Today's sealed run: `{ date, qids:[], sealed, score, attempt:[] }` (ignored if `date` ≠ today). |
| `sd_coach` | Coach memory: recent picks/observations so it doesn't repeat itself. |
| `sd_recent_<section>` | Ring buffer of recently-served qids for no-repeat sampling. |
| `sd_last_mastery` | `{sec: acc%}` snapshot from the last Home render — drives the mastery-delta shine. |
| `sd_last_read` | `{ path, title }` for Study's "Continue reading" card. |
| `sd_theme` | `midnight` \| `ocean` \| `forest` \| `ember` \| `fire` \| `rose` \| `orchid` \| `obsidian` (a saved `daylight` migrates to midnight + light). |
| `sd_theme_mode` | `dark` \| `light`. |
| `sd_mode` | `quiz` \| `flash` (flashcard self-grade). |
| `sd_deck_len` | Session length `5` \| `10` \| `20`. |
| `sd_mute` | Sound on/off. |
| `sd_prime_opt` | Count of "just read" opt-outs; 3 stops the prime offer. |
| `sd_cm_<id>` | First-run coach marks seen (`first_question`/`first_combo`/`first_results`/`first_cards`). |
| `sd_reader_fs` / `sd_reader_full` / `sd_reader_toc` / `sd_reader_modules` / `sd_reader_w` / `sd_modules_w` / `sd_toc_w` | Reader font size, fullscreen, TOC/module-tree open state, pane/sidebar widths. |
| `sd_reader_font` / `sd_reader_measure` / `sd_reader_dropcap` / `sd_reader_recall` / `sd_reader_scroll` | Reader typography (serif, measure, drop-cap), think-first answers pref (`"1"` hidden = default), per-path scroll-resume map. |
| `sd_last_export` | Date of the last backup export (for the backup nudge). |
| `sd_study_path` | `{section: "full"\|"senior"\|"principal"\|"cases"}` Study path per section (legacy `interview` reads as `senior`). |
| `sd_case_tier` | `{section: "all"\|"senior"\|"principal"}` Case Studies level filter. |
| `sd_rail_collapsed` | `"1"` collapses the left rail (applied pre-paint). |
| `sd_reader_short` | `"1"` shows authored `**Short:**` summaries in the reader. |
| `sd_diagram_dir` | `lr` \| `td`; absent = auto orientation. |
| `sd_tech_tab` / `sd_tech_sec` / `sd_tech_facets` | Technologies screen tab, section filter, facet state. |
| `sd_progress_corrupt` | Raw copy of an unparseable `sd_progress`; never exported. |

---

## Question extraction & the authoring contract

`extract.py` walks every `.md` file in each section — **module READMEs and their
deep-dive sub-files** (e.g. `llm/advanced_rag/graph_rag.md`), grouped under the parent
module — and builds `questions/<section>.json` + `index.json`. `case_studies/` and
`CLAUDE.md` are excluded. It scopes to each file's `## 12. Interview Questions` section,
treats every fully-bold line as a question and the paragraph beneath as the answer, uses
the authored `**Short:**` line under the question as the correct option (or the answer's
**first sentence**, trimmed to 220 chars, when there is none), and picks **topically related**
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
  with `**`; an authored `**Short:**` line under the question (15-220 chars; `--strict`
  fails outside that range) becomes the option; without one, the answer's first sentence
  is used and an over-long one is trimmed, never dropped. The **15-Q&A floor** guarantees
  enough distractors.
- Every new module directory must be added to `STUDY_ORDER["<section>"]` in `app.js` at
  its learning-path position, and every new file (module page, sub-file, case study) must
  be listed in the section README's `<!-- study-paths -->` block; `--strict` fails on a
  gap either way.
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
| `build_tech.py` | Builds `tech_index.json` and `questions/tech.json`. |
| `build_common.py` | Constants shared by the builders. |
| `questions/`, `graph/` | Generated data, gitignored; Pages CI rebuilds both on every deploy (locally: `bash scripts/build_banks.sh`). |
| `case_questions/` | Case-study reader quiz pools (generated). |
| `tech_index.json` | Technology knowledge bank (generated). |
| `diagrams/` | Pre-rendered Mermaid `.mmz` (generated by `scripts/build_diagrams.mjs`). |
| `vendor/` | Iconify icon packs (generated by `scripts/build_icons.mjs`); the APK build also adds `mermaid.min.js` to its asset copy. |

---

## Android APK

LORA also ships as a fully-offline, sideloadable Android APK — a raw-WebView
wrapper (no Capacitor/frameworks) built by `scripts/build_android_assets.sh`,
which mirrors the whole repo (every section's Markdown + freshly regenerated
question banks/graphs) plus a vendored `mermaid@11.16.0` UMD build into the
app's assets, plus the pre-rendered diagram assets (`game/diagrams/*.mmz`) and icon
packs. The CI smoke test accepts an APK of 15-200 MiB.
`.github/workflows/android-apk.yml` builds and publishes a signed GitHub
Release on **every push to `main`, newest push wins** (builds are
cancel-in-progress, so a burst of commits yields one release for the last of
them and the release numbering skips; green-skip if any signing secret is
absent). It also `node --check`s the SPA before building and smoke-tests the
assembled APK: required entries (including `paths.json` and `tech.json`), a
parseable shipped `app.js`, graph count equal to the section count, bank count
equal to sections + 3, at least 3,000 pre-rendered `.mmz` diagrams, an unstamped
`sw.js`, and a size in the 15-200 MiB window, so a white-screen build cannot reach the stable download at
`releases/latest/download/systemdesign-daily.apk`.

Three seams in `app.js` are the only places behavior forks from the Pages
build. Two are gated on `IS_APK` (`location.hostname ===
"appassets.androidplatform.net"`); the third is feature-detected on the
injected bridge object, which is the more honest test since it is the bridge,
not the hostname, that the code needs:

- **Vendored Mermaid loader** — `_loadMermaidModule()` injects the bundled
  `vendor/mermaid.min.js` UMD script instead of the jsDelivr ESM import, since
  the CDN is unreachable offline.
- **Service worker skipped** — `registerServiceWorker()` returns early in the
  APK; every asset is already local and SW registration would just fail noisily
  against the WebView's asset loader.
- **`SDAndroid.saveBackup` export bridge** (feature-detected on
  `window.SDAndroid`, not on `IS_APK`) — `exportProgress()` hands the backup
  JSON to the native bridge (no browser download chrome in a WebView) when
  `window.SDAndroid.saveBackup` exists, instead of the `<a download>` path.
  The bridge returns success synchronously, and the export is only recorded on
  `true` so a failed write cannot suppress the backup nudge.

The `html.is-apk` class (stamped on `<html>` by `MainActivity.onPageFinished`) is
a wrapper-injected, presentation-only CSS hook — not a fourth `app.js` seam:
app.js logic never branches on it; it only lets `style.css` adapt the reader
chrome for the phone WebView (slim head, thumb-reach bottom bar, no redundant
close pill), and every such rule collapses to `display:none` on web.

Progress does not carry over automatically between the Pages site and the APK
(separate `localStorage`); a one-time Export (Pages) / Import (APK) carries it
across. No Play Store — sideload the APK directly, or point **Obtainium** at
the GitHub repo for tap-to-update. Full detail: `android/README.md`.

---

## QA harness pattern

Features are smoke-tested with headless Chrome over the Chrome DevTools Protocol (raw Node
`WebSocket`, no Puppeteer). The pattern:

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
tooling, and Play Store publishing. The Android APK (see above) is an offline WebView
wrapper of the same static tool, not a departure from it — the only state anywhere is
still `localStorage`.
