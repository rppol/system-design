# CLAUDE.md — Game — LORA (Learn Often, Recall Always), by Rutik (browser app, template-exempt)

The `game/` directory is an application, not study content — see
[README.md](README.md) for architecture, features, and how to run it.

## Hard limits (user-set — do NOT change these, in any theme or redesign)

1. **The reader background is pitch black `#000000`, opaque, in EVERY theme.**
   Never translucent, never glass, never theme-tinted. The `--rd-bg` token in
   `style.css` must stay `#000000`, and no `[data-theme=...]` block may
   override the reader surface tokens.
2. **Reader prose text is pure white `#ffffff`** (`--rd-text`; changed from
   `#e6e6e6` on 2026-07-03 by owner request). Code blocks, ASCII diagrams, and
   Mermaid canvases keep the One Dark palette on their dark islands.

## Other invariants

- Vanilla JS + CSS, no build step, no frameworks, no external deps (the reader's
  mermaid CDN import is the only exception on Pages).
- **Exactly three APK seams, and no others may exist.** LORA also ships as an
  offline Android APK (see `android/README.md`). Two seams are gated on
  `IS_APK` (`app.js`, keyed on `location.hostname ===
  "appassets.androidplatform.net"`): the vendored-Mermaid-UMD loader in
  `_loadMermaidModule()` (jsDelivr ESM is unreachable offline) and the early
  return in `registerServiceWorker()` (every asset is already local; SW
  registration fails noisily against the WebView asset loader). The third — the
  `SDAndroid.saveBackup` native bridge in `exportProgress()` (no browser
  download chrome in a WebView) — is deliberately **feature-detected on
  `window.SDAndroid`, not on `IS_APK`**: the code needs the bridge, not the
  hostname, so it degrades correctly anywhere the bridge is absent. `grep -c
  IS_APK app.js` must therefore stay at 3 (the definition plus two uses).
  Every other line must render byte-identically on Pages and in the APK. The vendored `game/vendor/mermaid.min.js` (fetched
  once at CI build time by `scripts/build_android_assets.sh`, bundled into the
  APK, never fetched at runtime) is a second sanctioned exception to the
  no-external-deps rule, alongside the Pages Mermaid CDN import.
- **Pages-only (2026-07-03): `localStorage` `sd_progress` is the single source of
  truth and the single write path for all progress.** There is no server, no
  `/api`, no database, and no local scheduling stack. Every `localStorage` write —
  progress and UI prefs alike — goes through the quota-safe `safeSet()` wrapper
  (wraps `localStorage.setItem`, never throws, shows a one-time toast nudging
  export on first failure), except the one-shot backup-import path, which
  deliberately writes `BACKUP_KEYS` raw inside its own try/catch so a failed
  restore is loud; `saveSessionLocal()` remains the session-aggregate path
  (streak, XP, history, reviews) and persists via `safeSet()` underneath. The reader fetches
  content at `../<path>`; **export/import from the Progress screen is the only
  backup path** between browsers.
- **The mirror-seam rule is DEAD** — there is no server to sync to, so there is no
  round-trip and no `POST`. A finished session writes straight to `localStorage`.
- **All persisted fields are additive** — never rename, repurpose, or drop an
  existing `sd_progress` field (old exports must still import). New state = a new
  field or a new `sd_*` key. `sd_last_mastery` (Home-render accuracy snapshot for
  the mastery-delta shine) is the only key E2 added.
- **The moments engine (`queueMoments`/`moment`) is the single celebration
  system** — level-ups, tier promotions, streak milestones, capsules, cleared
  backlog, goal all queue through it; do not add a parallel overlay/toast for a
  milestone. **`bonusXp` is the only XP side-channel** (everything above
  `correct × 10` — combos, boss, recall, double-down, seal — lands in it).
- **Deterministic randomness only** for anything that must survive a reload
  (gauntlet recipe, flip selection): seed on `todayISO()` via `cyrb53`/
  `mulberry32`/`seededShuffle`, never `Math.random()`.
- All app colors flow from tokens in `:root` / `[data-theme=...]` blocks in
  `style.css`; themes are set via `data-theme` on `<html>` (`?theme=` URL
  param previews without persisting). Any new color uses a token or `color-mix`,
  and must read on both midnight AND daylight.
- Date/time math flows through `todayISO()` / `nowHM()` (honoring the `?qa_date=` /
  `?qa_time=` QA seams); animations are `prefers-reduced-motion`-gated.
- Question ids are content-stable hashes (`module#md5(module|question)[:12]`);
  never revert to position-based ids — they orphan spaced-repetition state.
- `extract.py` emits, per question: `id`, `section`, `module`, `moduleName`,
  `sourceFile`, `difficulty`, `question`/`questionMd`, `answerFull`/`answerFullMd`,
  `correct`/`correctMd`, `distractors`/`distractorsMd`, `distractorIds`, `concepts`.
  **Re-run `python3 extract.py` after editing any module's Q&A**, then reload.
- **`STUDY_PATHS` in `app.js` is the Study view's Full/Interview toggle.** Each
  section may have an `interview` array — an ordered SUBSET of that section's
  `STUDY_ORDER` — and it MUST stay in sync with that section's README "Learning
  Paths" list (same hand-maintained discipline as `STUDY_ORDER`). Sections absent
  from `STUDY_PATHS` show no toggle (Full only). Choice persists in `sd_study_path`
  (JSON map keyed by section). **All game sections have interview paths** — every
  section except `book` and `technologies`. Both ARE bank sections (their Q&As
  extract into the MCQ bank like any module) and both have a `STUDY_ORDER` entry
  pinning module/chapter order, but neither has an `interview` array — both are
  Full-only, and the toggle auto-hides when a section is absent from `STUDY_PATHS`.
  `book` is Full-only because a chapter-by-chapter summary has no interview subset to
  cut; `technologies` is Full-only because it is too small (2 modules) for a
  meaningful cut — it gains an interview array once it reaches >= 4 modules (see
  `technologies/CLAUDE.md`). Each interview path is added the same way (a subset
  array here + a README "Learning Paths" block).
- **The book section navigates one level deeper than every other section** (owner-set
  2026-07-16; module ids are `book/<book_slug>/<chapter>`, three segments). Three
  pieces in `app.js` implement it, all keyed on `BOOK_LABELS` + `bookOf()`:
  (1) `#/study/book` renders `renderBookPicker()` — one card per book in
  `STUDY_ORDER.book` order — and `#/study/book/<book_slug>` renders the standard
  serpentine chapter graph scoped to that book (`openStudySection` parses the scope
  from the route); (2) `buildModuleNav()` wraps the reader sidebar's chapter list in
  collapsible per-book groups (`readerBooksOpen` session Set; the current page's book
  auto-opens); (3) module-key resolution matches by prefix against the real module
  list — never assume 2-segment `path.split("/").slice(0, 2)` module keys, book ids
  are 3 segments. **Adding a book = a `BOOK_LABELS` entry (`{ name, author, short }`)
  + `STUDY_ORDER.book` entries**; a missing label degrades to a title-cased slug (no
  author/short), never a broken screen. No other section may nest — the picker and
  sidebar grouping activate only when module ids match `book/*/*`.
