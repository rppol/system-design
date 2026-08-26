# Game — RESCUED subagent output: router + screen flow + empty states

Salvaged from a `sonnet` profiling subagent whose parent (the game audit lead) was stopped to
serialize the queue. **This IS most of the §3 "UX flow" deliverable.** The relaunched game
auditor should read this instead of re-deriving it, and spend its budget on §1 pedagogy
(SM-2 vs FSRS, web-researched), §6 execution plan, and the distractor-pool question.

All line numbers are `game/app.js` (9,536 lines) unless noted.

## Router — hash-based, one dispatcher

`hashchange` -> `onHashChange` (9506); first load normalizes to `#/home` (9509).
- `go(route)` (8603-8607) — sets the hash; if unchanged, calls `onHashChange()` directly
  (a same-hash write fires no native event).
- `redirect(route)` (8609-8613) — `history.replaceState` + manual dispatch. Guard redirects
  only; never adds a history entry.
- `onHashChange()` (8628-8654) — syncs the mobile tab bar (`updateTabbar` 8616), swallows the
  router's own silent hash restore (`_navLock`), closes the reader overlay when navigating to a
  non-reader route (8638-8641), and **guards leaving a live quiz** (8644-8652): with >=1 card
  answered it restores the quiz hash and raises `openPauseSheet` with the destination queued.

`dispatch(route)` (8656-8688):

| Pattern | Handler | Note |
|---|---|---|
| `#/reader/<path>[@frag]` | `openReaderPath` (8657-8668) | Overlay; synthesizes Home underneath if none mounted |
| `#/quiz/<sec>` | (8669-8674) | **Never renders a quiz directly** — a live deck cannot survive refresh. No-ops if `state.inQuiz`; else redirects to `#/topics/<sec>` or `#/home` |
| `#/gauntlet` | `startGauntlet()` (8678) | |
| `#/codex` | `renderCodex()` (8679) | |
| `#/interview/<sec>` | `startInterview(sec)` (8680) | |
| `#/topics/<sec>` | `openTopics(sec)` (8681) | Module picker before a blitz |
| `#/study/<sectionPath>` | `openStudySection()` (8682) | |
| `#/study` | `renderStudy()` (8683) | |
| `#/tech` / `#/tech?...` | `renderTech()` (8684) | `startsWith`, so facet query strings match |
| `#/progress` | `renderProgress()` (8685) | |
| `#/debrief` | `renderDebrief()` (8686) | |
| **anything else, incl. `#/home`** | `renderHome()` (8687) | **This is the 404** — unknown routes silently become Home |

Line 8677 strips `interview-mode`/`gauntlet` skin unless a quiz is live. `#/insights` is
reserved and unimplemented (comment, 8590).

## Screens and their next-action affordances

**Home** `renderHome()` (1526-1710) — hero streak, `skylineSVG`, gauntlet card, topic card,
quest chips, then CONDITIONAL cards each rendered only if its condition holds (Resume /
Debrief-ready / Review-due / Confusion / Fading / Weak-spot / Rusty-section), then the section
tile grid. Primary CTA `#startBtn` -> `startBlitz(section)` (1692) is always present.
`.tile` -> `#/topics/<sec>` with hover/focus bank prefetch (1703-1708).

**Study tree** `renderStudy()` (4027-4081) / `openStudySection()` (4804-5357) — serpentine
skill tree; `book` nests one level deeper (4805-4809). The tier `pathswitch` renders only when
>=2 options exist (4974-4978); the case-study Level filter only when >=2 tiers exist (4986-4990).
`.pn-main` -> reader, but via `maybePrime()` first. `.pathleaf` -> `openReaderPath` on that
sub-file (5234-5238). `.pn-fan` toggles the sub-file list (5239-5247).

**Reader** `openReaderPath()` (7791-8018) — overlay `<aside id="reader">`. Chrome (7817-7853):
Back, module list, Prev/Next (`disabled` at list edges, 7822-7823), font +/-, `Aa` options,
diagram orientation cycle Auto/Horizontal/Vertical (7869-7880), find-in-page, ToC, immersive,
close. A duplicate `reader-bottombar` exists for the APK phone build only (7827-7833).
`buildModuleNav()` (7319-7427) renders multi-file modules as collapsible `.mod-group`, and book
sections get an extra `.mod-book` layer (7362-7386).

**Quiz run** — `renderQuestion()` (2647-2734), `answer(i)` (2811), `gradeAnswer()` (2843-2945:
`✓`/`✗` marks 2854-2858, SR announcement on a miss 2859, one-time "redemption" retry queued
within 3 questions on a first miss 2912-2916), `buildReveal()` (2950-2985: full answer, a
"You picked the answer to: … from <module>" provenance line on a wrong pick, "Dive deeper" 2977,
explain-back textarea). `finish()` (3100-3266) ends with Redeemed / Still-shaky /
Learned-from-skip groups (3192-3215) and three exits: replay (3254), Home (3255), Progress (3256).

**Gauntlet** `startGauntlet()` (8920-8951), `buildGauntletDeck()` (8875-8918) — deterministic
daily recipe: oldest-due reviews -> suggested section -> weakest section -> one advanced final.

**Review** `startReview()` (2157-2205) — self-heals orphaned review records with a one-time
toast (2181-2187), applies ~30% deterministic answer->question flip rounds (2194-2202).

**Tech** `renderTech()` (4092-4742) — Tools + Tradeoffs tabs, facets in the hash query string
(4184-4232), author-curated pinned deep-dive grid (4173-4181).

**Debrief (the coach)** `renderDebrief()` (8493-8559) — reachable ONLY via the Home card, gated
by `debriefReady()`.

**Progress** `renderProgress()` (3979-4024) — export (3423-3448) / import (3449-3464).

## Empty and edge states — mostly GOOD, four gaps

Handled well: first-ever visit (`loadProgress()` 1194-1208 zeroed default;
`seedCoachMarksIfVeteran()` 782-788 deliberately no-ops on empty history so all 4 marks fire;
`coachPick()` 8123-8154 always resolves deterministically by day-of-year so the suggested-topic
card is never broken). Nothing due -> toast + Home (2188-2191), and the Home card is simply
omitted when `due.length` is falsy (1560) so no broken affordance is shown at all. Gauntlet
already sealed -> becomes "Practice ->", unscored, same recipe (8953-8965, 8943-8948).
Section bank fetch failures all route to a shared `errorScreen` with Try-again/Home
(1865-1868, 4847-4851, 2125-2128). Reader 404 vs other failure are DISTINGUISHED (7987-8015):
404 -> "This page isn't in the repo — it may have moved." + Open-module-home (omitted when the
missing page IS the module home, 8002); other -> "Check your connection" + Try again. Boot
index failure (9439-9442) and offline tech index (4101-4106) each get their own errorScreen.
Every modal (`helpOverlay`, `pauseSheet`, `moreSheet`, `primeSheet`) closes via Escape
(8696-8701), backdrop, or a button — none lack a close path.

**The gaps:**
1. **Reader end-of-content dead end.** `appendWhatNext()` line 7701: `if (!cards.length) return;`
   On the last module of a section with no graph-related pair and no different last-read page,
   **nothing renders** — no "Continue your path", no "you finished this section", just a bare
   page with possibly-disabled Prev/Next. The cleanest true dead end in the app.
2. **Eval-block silent absorb.** `appendEvalBlock()` 7630: clicking "Quiz this topic" on a
   0-question page produces a screen-reader-only `announce()` and no visible feedback; then
   7640 `block.remove()` deletes the whole widget with no explanation.
3. **`openTopics` filter with zero matches** (1993-1997) — rows hidden, no count, no message,
   no reset button.
4. **`openStudySection` filter** (5282-5291) — "0 of N topics" only, no reset affordance.

**`renderTech()` is the one screen that gets this right:** `emptyHTML(q)` (4424-4448) diagnoses
WHICH facet caused zero rows ("N by section, N by the problem area, N by language hidden") and
offers a Clear-filters button (4448). **That is the pattern the other three should copy.**

Also stuck-able: interview locked behind Silver shows a transient toast and redirects with no
"how do I reach Silver" (9246-9249); an unknown hash silently becomes Home with no 404 (8687);
`#/quiz/*` is unbookmarkable (8669-8674); the prime-sheet backdrop click is treated as
"Just read" rather than a true cancel (2340).

## Onboarding — there is no tutorial

Four one-shot `coachMark()` tooltips (789-818), gated on `localStorage["sd_cm_<id>"]`,
`COACH_MARK_IDS` at 781: first question (2733), first combo >=2 (2590), first results screen
(3261), first flashcard switch (9491).

**No mark ever explains SM-2, the gauntlet, XP/level math, or what "review" mechanically means.**
"SM-2" and "spaced repetition" appear only in code comments (e.g. 1287) and as the unexplained
eyebrow label "Spaced repetition" (1569, 1615). The `?` overlay (`toggleHelp()` 821-846) is a
keyboard cheat sheet only. The rest is native `title=` tooltips in `index.html` (33, 40, 42) —
hover-only, so **absent on touch entirely**.

## Time-to-first-question

- **1 click** — Home `#startBtn` (1692) -> `startBlitz()` (2119) -> sample with no-repeat/recency
  weighting (2140-2146) -> `startDeck()` -> `renderQuestion()` (2647). No confirmation screen.
- **2 clicks** — tile -> `#/topics/<sec>` -> checkbox picker -> `#startSel`.
- **Reading-first** — Study node -> `maybePrime()` (2310-2314) interstitial: "Prime me"
  (3-question ungraded pretest) or "Just read" (2315-2340).

---

# RESCUED #2: the `px` -> `rem` conversion, fully scoped

Second orphaned `sonnet` subagent from the same stopped parent. **This closes the "highest-value
a11y fix" item — it is scoped, counted, and has its exclusions identified. Do not re-derive it.**

## It corrects the salvaged report

`game.md` says "167 `font-size: <n>px` declarations and **0 literal `rem`**". The first half is
right; the second needs narrowing. `grep -nE "[0-9.]+rem" style.css` finds **2** rem values —
lines 1576 (`margin: 0 0 .45rem`) and 1577 (`padding-left: .7rem`), both on
`#reader.short-on .md-body p.qa-short`. Neither is a `font-size`.
**Correct phrasing: "0 of 258 `font-size` declarations use rem; 2 non-font-size rem elsewhere."**

## The accounting (258 `font-size` lines, fully partitioned)

| Form | Count |
|---|---|
| simple `px` literal | **167** (this is the audited number) |
| `var(...)` | 80 (79 x `--fs-*`, 1 x `--rd-fs`) |
| `clamp()` with px endpoints | 3 (382, 2428, 2546) |
| `em`-relative | 7 (628, 913, 1258, 1448, 1580, 3068, 3145) |
| `inherit` | 1 (2179) |

Only **4** inside any `@media` (1321, 1909, 1920, 1943 — all px literals). **Zero** inside a
`:root`/`[data-theme]`/`[data-mode]` token block: those define custom properties only.

## The leverage point — 4 lines re-base 79 usages

The whole type scale is four properties in `:root`:
`--fs-2xs: 10.5px` (66), `--fs-xs: 11.5px` (67), `--fs-sm: 12.5px` (68), `--fs-md: 13.5px` (69),
used 20/19/17/23 times respectively. **Converting those 4 lines fixes all 79 usages for free.**

**And the scale has drifted.** The 167 raw literals span 21 distinct values, the commonest being
33x`11px`, 31x`13px`, 25x`14px`, 16x`12px` — **not one of which matches any token**
(10.5/11.5/12.5/13.5). The token scale and the ad-hoc scale are two different scales.

## The conversion factor is clean

**No root font-size is set anywhere** — not in `style.css` (`html`/`body` both checked), not
inline in `index.html`. So `1rem` = the browser's real accessibility size with zero indirection,
and `px / 16` is uniformly correct. No per-component ratio math.

## MUST NOT convert — one line, and the reason is a browser threshold

```
1937: @media (pointer: coarse) {
1943:   .filter, .rd-find-input, .eb-input { font-size: 16px; }
```
Its own comment: *"iOS zooms on focus of any input < 16px — pin these to 16px on touch."*
That 16 is an **absolute browser threshold, not a visual size**. As `rem` it breaks on any root
below 16px and reintroduces the exact zoom-on-focus bug it exists to prevent. Keep it px, or
`max(16px, …)`.

## Needs a coordinated edit, not a find/replace — 3 selectors

| Selector | Line | Why |
|---|---|---|
| `.reader-top` | 2189-2191 | `width/height: 38px` + `border-radius: 50%` + `font-size: 16px`. Fixed circle, no overflow handling — a scaled glyph clips on the curve |
| `.nudge-dismiss` | 2690-2691 | Same shape at `26px` with a `16px` glyph — already ~62% fill before any scaling |
| `.fc-n` | 2890 | **Different failure mode**: `line-height: 12px` and `min-height: 12px` are ABSOLUTE. Convert `font-size` alone and the text outgrows its own pinned line box and clips vertically |

## Also judgment, not syntax

- **3 `clamp(Npx, Xvw, Mpx)` hero headings** (382, 2428, 2546) — easy to convert syntactically
  right and visually wrong with a `vw` term in the middle. Eyeball both extremes.
- **10 ellipsis-truncated labels** (1146, 1365, 1376, 1395, 1402, 1422, 1919, 2900, 2938, 2977)
  degrade gracefully — they truncate harder rather than breaking. A UX call, not a bug.
- **~15 sub-11px dense values** (9.5/10/10.5px — chip counters, tab-bar icons, forecast labels).
  Scaling all of them can crowd the bottom tab bar (`--tabbar-h: 58px`, line 77, untouched by
  this conversion) and the 14-bar forecast chart. **Owner call on what is decorative.**

## The reader's OWN font control is a separate product decision

`app.js:6986-6992`:
```js
// Reader font size: A− / A+ in the reader head, persisted, clamped 12–19px.
function applyReaderFont(delta = 0) {
  let fs = +(localStorage.getItem("sd_reader_fs") || 14.5) + delta;
  fs = Math.min(19, Math.max(12, fs));
  safeSet("sd_reader_fs", fs);
  document.documentElement.style.setProperty("--rd-fs", fs + "px");
}
```
The **only** JS path touching font sizing. `--rd-fs` is never defined in CSS — only referenced at
`style.css:1432` with a `14.5px` fallback; the real value is written at runtime.

**What it scales:** reader body prose, lists, blockquotes, and `em`-based children (drop cap
1258 `2.9em`, anchor icon 1448 `0.65em`, summary note 1580 `.93em`).
**What it does NOT:** headings h1-h6 (1434 `24px`, 1435 `20px`, 1457 `17px`, 1458 `15px`,
1459 `14px`), inline/`pre` code (1630, 1646 — both `--fs-sm`), tables (1671 `13px`), callout
icons (1494 `12px`), Mermaid edge labels (1778 `11px`).

So the app already has per-component font scaling — it just covers reader prose only, and it is
an app preference layered ON TOP of the root rem rather than derived from it. Whether the
12-19 clamp and 14.5 default should become rem-relative is a product decision.

## Media queries are NOT affected

All 4 breakpoints are px-based (`max-width: 639px`, `max-width: 640px`, `pointer: coarse`).
`max-width`/`min-width` measure viewport CSS px, independent of root font-size — converting the
font sizes inside them does not change when a breakpoint fires. The only indirect effect is that
rem-scaled text inside an already-narrow layout wraps sooner. A layout risk, not a breakpoint risk.

## Bottom line

**~167 lines are a safe mechanical `px/16` rewrite** (163-166 literals + the 4 token defs, with
79 `var()` usages riding along free). **1 line explicitly excluded** (1943). **3 need a fixed-box
decision** (2191, 2690, 2890). **3 need a clamp re-check** (382, 2428, 2546). **The `--rd-fs`
control needs a product decision before anyone touches it.**
