# LORA — navigation & main UI redesign

Implementation spec. Written against the repo at `src/main/java/com/rutik/systemdesign/game/`
(`index.html`, `app.js`, `style.css`). Line references are to the state of the files as read;
search for the quoted identifier if they have drifted.

Companion visual: `LORA Redesign.dc.html` — every screen below is drawn there, with a live
switcher for all eight themes plus light mode.

---

## 0. Scope

**In scope:** the app shell (header, primary navigation), Home, the Study picker levels,
the Tech index, Progress, and the quiz runner's chrome.

**Explicitly out of scope — do not touch:**

- The reader pane (`#reader`) and everything inside it: prose rendering, the three panes,
  drag grips, serif/dropcap, find-in-page, the back-stack.
- Diagram rendering: mermaid, `.mmz` pre-rendered assets, auto-flip, the direction toggle,
  the zoom overlay.
- The reader/markdown colour tokens `--rd-*`, `--md-*`, `--od-*`.

Two exceptions, both in the reader's **return path**, not its mechanism — see §7.1.

**Hard constraint:** all eight colour themes (`midnight`, `ocean`, `forest`, `ember`, `fire`,
`rose`, `orchid`, `obsidian`) and both modes must keep working. No new hard-coded colour
anywhere. See §6.

---

## 1. The problem

The app has 12 routes and has outgrown its chrome.

1. **Two navigations that disagree.** The topbar's ghost buttons offer Study / Tech / Progress;
   `#tabbar` offers Home / Study / Tech / Progress. Different sets, different first item.
2. **Four live routes have no permanent entry point.** `#/gauntlet`, `#/codex`, `#/debrief`
   and `#/interview/<section>` are reachable only from conditional cards on Home, or the
   command palette. `#/debrief` only renders when `debriefReady()` is true (`app.js` ~8679);
   `#/interview/<section>` has no card at all.
3. **641–899px has no navigation.** `--tabbar-h` and `#tabbar` are gated below 640px
   (`style.css` ~77), so iPad portrait and every half-width desktop window get the
   ten-control topbar and no tabs.
4. **Ten controls in one header row, doing three unrelated jobs** — identity (level ring,
   streak, XP), settings (deck mode, theme, mute, more), navigation (Study, Tech, Progress).
5. **During a quiz you are nowhere.** `updateTabbar()` (`app.js` ~8809) computes `active`
   as `null` for `#/quiz*` and `#/reader*`.
6. **Home's card stack is unbounded.** `renderHome()` (`app.js` 1629–1831) can emit twelve
   blocks of similar weight; the primary CTA can end up below the fold precisely on the days
   the coach has most to say.

---

## 2. The navigation model

**Five destinations own all twelve routes.** Same five on every viewport: a left rail at
≥900px, a bottom bar below it.

| Destination | Owns | Sub-navigation |
|---|---|---|
| **Today** | `#/home`, `#/gauntlet`, `#/debrief`, resume, due-review | none |
| **Practice** | `#/topics/<section>`, `#/quiz/<section\|review\|weak>`, `#/interview/<section>` | Sections · Due `n` · Weak · Interview |
| **Study** | `#/study`, `#/study/<section>`, `#/study/book`, `#/study/book/<slug>`, `#/reader/*` | breadcrumb |
| **Tech** | `#/tech`, `#/tech?t=<facet>` | facet tabs + breadcrumb |
| **Progress** | `#/progress`, `#/codex`, `#/debrief` (archive), `#/insights` | Overview · Codex · Debrief · Awards |

Rules:

- **Routing stays on the hash.** `diagrams/<key>.mmz`, `paths.json` and the question banks
  are fetched by relative URL so one build serves GitHub Pages and the APK asset host. Do not
  promote any destination to a real path.
- **All existing route strings keep working.** This is an addition of structure, not a
  renaming. `#/codex` still resolves; it now also has a permanent door.
- **Quiz and Reader remain full-screen modes, not destinations.** They suppress the primary
  nav, but they name themselves and their exit, and exiting returns to the owning destination.

### 2.1 Sub-navigation is a real route, not local state

Each sub-nav selection must be addressable so back/forward and reload work:

- Practice: `#/practice`, `#/practice/due`, `#/practice/weak`, `#/practice/interview`.
  Keep `#/topics/<section>` as the drill-down and keep the old `#/quiz/*` guards intact.
- Progress: `#/progress`, `#/codex`, `#/debrief`, `#/awards`.
- Tech: the existing `#/tech?t=<facet>` already does this — surface it (§5.4).

Announce sub-nav changes through the existing `#live` polite region.

---

## 3. Shell

### 3.1 Breakpoints

| Width | Primary nav | Notes |
|---|---|---|
| `< 900px` | bottom bar, 5 tabs | move the `--tabbar-h` gate from 640px to 900px |
| `>= 900px` | left rail, 208px | `--rail-w: 208px` |

Replace the `@media (max-width: 640px)` gate on `--tabbar-h` (`style.css` ~77) with 900px.
Add `--rail-w`, applied as `padding-left` on the scroll container at ≥900px only.

### 3.2 Z-index contract

```
#reader              (position: fixed; inset: 0)   ← highest of these three
.tabbar / .rail                                    ← below the reader
.backdrop .mesh                                    ← lowest
```

`#reader` must keep `position: fixed; inset: 0`. See §7.2 — this is the one way the redesign
could break diagram rendering.

### 3.3 Rail (≥900px)

Vertical stack, top to bottom:

- Brand block: `LORA` 17px/700, `by Rutik` at `--fs-2xs` in `--faint`. Padding `4px 11px 18px`.
  No longer a navigation control — Today is the tab.
- Five items. Each: 17px inline SVG (lift the glyphs from `#tabbar` in `index.html`) + label
  at `--fs-sm`, `gap: 10px`, `padding: 9px 11px`, `border-radius: var(--radius-sm)`,
  `color: var(--muted)`.
- Selected item: `background: var(--glass-2)`, `color: var(--text)`,
  `box-shadow: inset 0 0 0 1px var(--glass-brd)`.
- Bottom group: **Appearance** (theme + sound + deck-mode defaults) and
  **Search & shortcuts** with a `⌘K` hint in `--mono`/`--fs-2xs`.
- Right edge: `border-right: 1px solid var(--glass-brd)`.

### 3.4 Bottom bar (<900px)

Five equal `flex: 1` tabs, `min-height: 46px` (the container keeps the existing 58px so the
target stays ≥44px). 20px icon over a `--fs-2xs` label, `gap: 3px`.
Inactive `--faint`; active `--accent-text` (**not** `--accent` — `--accent-text` is the
AA-corrected value in light mode, `style.css` ~212).

Keep `guardedNav()` (`app.js` ~9646) on every tab so a live deck is still protected.

### 3.5 Header

Two items only, right-aligned:

- **Day chip** — streak flame in `--flame` + day count, and `Lv n · Title`. One chip,
  `--fs-2xs`, `border: 1px solid var(--glass-brd)`, `background: var(--glass)`.
- **Overflow `⋯`** — 26px round, holds Appearance, Sound, Shortcuts, and the About/repo line
  currently in `.foot`. Keep its `aria-haspopup` / `aria-expanded`.

Everything else moves:

| Was in the header | Goes to |
|---|---|
| Study / Tech / Progress buttons | the rail and the tab bar |
| Level ring (`#lvlChip`, `#lvlRingFg`) | Progress, at 58px — see §5.5 |
| Streak (`#streakChip`), XP (`#xpVal`) | one day chip; detail popover stays, moves to Today |
| Deck mode (`#modeBtn`) | the Today start card, as a segmented control |
| Theme (`#themeBtn`), mute (`#muteBtn`) | Appearance, in the overflow menu |
| Shortcuts (`#helpBtn`) | `⌘K` palette, which already exists |

`updateLevelRing()` (`app.js` ~1562) keeps working — repoint it at the Progress ring element.
Do not delete `refreshStats()`; Today and Progress both consume it.

---

## 4. Today

Replaces `renderHome()`'s open-ended stack with a fixed contract. Desktop is a two-column
grid `1.35fr / 1fr`, 20px gap; below 900px it is one column, primary first.

### 4.1 The card contract — exactly three slots

1. **One primary action.** The coach card, promoted: eyebrow `Coach · <why-chip>` in
   `--accent-text`; `<h1>` 27px = section + deck length; one sentence of `--muted` body
   explaining the mix ("Nine are spaced-repetition reviews that came due today; three are new.
   About five minutes."); the gradient CTA; a `--btn2` secondary "Pick a different section".
   The deck-mode segmented control sits top-right **of this card**.
   Reuse the existing precedence in `renderHome()`: reboarding card > review-due > suggested.
2. **At most two waiting rows.** Glass rows, 30px leading mark + title at `--fs-md`/600 +
   one line of `--muted` + `Enter →` in `--accent-text`. Ranked: unfinished blitz > gauntlet >
   debrief > fading > confusion > weak > rusty. Take the top two.
3. **One disclosure for the remainder.** A single dashed-border row:
   "Four more nudges — weak spots, a confusion pair, 2 rusty sections, Friday debrief · Show".
   Expands in place. Never more than one.

Everything `renderHome()` computes today is kept — `dueReviews()`, `weakSections()`,
`rustiestSection()`, `resumeSummary()`, confusions, fading, quests. Only the *presentation
budget* changes. Nothing is deleted.

### 4.2 Right column

- **This week** — seven bars, 62px tall, `gap: 6px`. Today's bar is the accent gradient;
  the rest are `var(--track)` (§6). A zero-activity day is a 3px stub, never `height: 0` —
  the day must still read as a day.
- **Weakest sections** — top three from `weakSections()`, label + percentage + `.tbar`,
  then a `--btn2` "Drill the bottom three" wired to `startWeakSpots()`.

The existing `skylineSVG(p)` and quest chips move into the disclosure or Progress; keep the
functions.

---

## 5. Subsections

### 5.1 Practice

Sub-nav: `Sections` / `Due n` / `Weak` / `Interview`.

Sections view: two-column tile grid, **sorted by mastery ascending**, not alphabetically
(`renderHome()` currently does `Object.keys(secs).sort()`). Tile = name at `--fs-md`/600,
`n Qs · n% mastery` at `--fs-2xs`/`--muted`, `.tbar`. Keep the `Passed` chip from
`p.awards["interview_"+s] || p.awards["panel_"+s]` and keep `.mastery-delta` shine.

Interview view: one card per section with its panel state, wired to `#/interview/<section>`.
This is what gives `renderInterviewIntro` / `renderInterviewStage` (`app.js` 9395–9480) a
permanent door.

### 5.2 Study

Breadcrumb replaces the per-level Back buttons (`#studyBack`, `#studyHome`, `#techHome`):

```
Study › Books › Designing Data-Intensive Applications
```

Every segment is a target. `renderStudy()`, `renderBookPicker()` and `openStudySection()`
keep their current data work; they render a breadcrumb instead of a Back button.

Chapter rows: 22px leading state mark — filled `--good` check when read, `--accent` ring
when in progress, `--glass-brd-hi` outline number when unread — then title `--fs-md`/600 and
a `--fs-2xs` meta line ("read 4 days ago · 9 min" / "continue — 40% in · 11 min left" /
"18 min"). The in-progress row gets `background: var(--glass-2)` and
`border-color: var(--glass-brd-hi)`.

Pin one card at the bottom: "Quiz this book — n questions drawn from the chapters you have
read", wired to the existing deck builder. This is the read→recall bridge; the two halves of
the app currently never point at each other.

Tapping a chapter opens the reader unchanged.

### 5.3 Reader

No visual change. See §7.1 for the two lines of routing.

### 5.4 Tech

The facets currently live in `#/tech?t=<facet>` with no on-screen trace (`renderTech()`,
`app.js` 4245–4884; `renderFacets()` ~4502, `renderShelf()` ~4551).

- Promote the facet list to the sub-nav strip, with counts: `All 84` / `Data stores 19` / `GPU 7` / …
- Add a breadcrumb under it — `Tech › Data stores` — with a `Clear` in `--accent-text`.
- Keep the query-string round-trip exactly as it is, including the `startsWith("#/tech")`
  check and the `replaceState` normalisation (~4381).

Shelf card: name + type chip, one line of tradeoff prose in `--muted`, then meta chips
(`n modules`, `n questions`, mastery). Mastery chip is tinted from `--good` / `--warn` via
`color-mix`, never a literal.

**Do not put mermaid fences in these cards** — see §7.2.

### 5.5 Progress

Sub-nav: `Overview` / `Codex` / `Debrief` / `Awards`. This is what gives `renderCodex()`
(~9185) and `renderDebrief()` (~8682) permanent addresses.

Overview, in order:

1. **Level card** — the ring from `#lvlChip` at 58px, `Lv n · Title` at 20px/700,
   `n XP · n to <next>` at `--fs-xs`/`--muted`. Ring track is `var(--track)`, fill `var(--accent)`.
2. **Readiness sentence** — plain words, from data already computed:
   "Solid on Spring, Java and Backend. Thin on soft skills and CUDA. Five sections you have
   never opened."
3. **Coverage grid** — one cell per section, 7 columns. Touched cells are accent at
   `color-mix` opacity by mastery; **never-opened cells are `var(--track)`**. Absence being
   visible is the entire point of this grid.
4. **Slipping** — topics scored against last recall: "was 80% · 12d", value in `--warn`.
5. **CTA** — "Build a session from these", wired to `startRefresh(ids)`.

The four zero-tiles (XP / streak / level / answered) come out. Cumulative accuracy only ever
goes up, so it cannot show decay — which is the one thing a recall app exists to fight.

### 5.6 Quiz mode

Nav stays suppressed. Add a top bar: `Exit` label, centred
"Backend blitz · question 4 of 12", a 4px `.tbar` progress fill, and the combo chip.

- Exit is a **labelled control**, not a bare glyph.
- Exiting returns to `Practice › <section>`, not Home.
- Keep `guardedNav()`'s confirm-on-leave.
- Keep the `#/quiz/*` reload guard (`app.js` ~8858) — a live deck cannot survive refresh — but
  redirect to `#/topics/<section>` rather than `#/home` where the section is known. It already
  does this; keep it when the router changes.

---

## 6. Token rules

Everything is drawn from `style.css`'s existing variables. The eight themes are pure token
swaps (`style.css` 80–196) and light mode is a token override block (205–227), so any
hard-coded value breaks one of the ten combinations.

- Surfaces: `--glass`, `--glass-2`; borders `--glass-brd`, `--glass-brd-hi`.
- Text: `--text`, `--muted`, `--faint`. Accent **text** is `--accent-text`, not `--accent`.
- Accent fills: `linear-gradient(135deg, var(--accent), var(--accent-2))` on `--accent-ink`.
- Semantics: `--good`, `--warn`, `--bad`, `--flame`.
- Geometry: `--radius`, `--radius-lg`, `--radius-sm`; type `--fs-2xs` … `--fs-md`; `--font`, `--mono`.

### 6.1 Add one token: `--track`

`--glass-2` is a **surface** token — `rgba(255,255,255,0.09)` in dark but
`rgba(255,255,255,0.82)` in light. `style.css` ~2108 uses it as the `.tbar` track, so in light
mode the track is near-white on a near-white card (~1.04:1) and a 38% fill has no visible 100%
to be read against. The same bug would hit the week chart and the empty coverage cells.

```css
:root      { --track: var(--glass-brd-hi); }   /* rgba(255,255,255,0.22) */
[data-mode="light"] { --track: var(--glass-brd-hi); }  /* rgba(13,23,44,0.22) — flips to ink */
```

`--glass-brd-hi` already carries contrast in both modes and is already used this way by
`.lvl-ring-bg` (~2530). Use `var(--track)` for: the `.tbar` track, the week-chart bars, empty
coverage cells, and the level-ring track. Keep `--glass-2` for surfaces only (selected rail
item, selected sub-nav pill, icon wells).

### 6.2 Theme QA

Check `obsidian` and light mode first — they are the two that expose hard-coded work.
`obsidian`'s accent is a near-neutral `#cbd1dc`, so anything relying on accent *hue* for
meaning fails there. Light mode inverts every glass value.

Leave the pre-paint boot script in `index.html` alone, including the retired-`daylight`
migration and the theme-id regex. If a theme is ever added, that regex is the one place to
update.

---

## 7. Constraints that must not break

### 7.1 The reader — verified unaffected, except the return path

Unaffected, all pane-local:

- `setReaderFull()` / `sd_reader_full`, native FS on `#reader` via `enterNativeFS()` /
  `exitNativeFS()` with the webkit spelling, the `native-fs` body class, the `F` shortcut.
- `applyReaderModes()` and the three toggles `#readerFull`, `#readerIdx`, `#readerMod`
  with their `.on` states.
- Drag grips and `--reader-w` / `--modules-w` / `--toc-w`; `rd-serif`, `rd-dropcap`
  (`app.js` ~7165); find-in-page; `wireReaderBody()`'s back-stack.

Because `#reader` is `position: fixed; inset: 0`, the rail and the tab bar are siblings it
covers, and native fullscreen on `#reader` hides both for free.

**The two lines to change.** `closeReader()` (`app.js` ~8235) does
`replaceState(null, "", state.underHash || "#/home")`, and the reader route guard (~8853) sets
`state.underHash = "#/home"` outright on a cold load. So finishing chapter 3 of a
twelve-chapter book drops you on Home with the chapter list gone. Preserve `underHash`, and
default to the **owning destination** — for a `#/reader/<path>` under a book, that is
`#/study/book/<slug>`.

Optional, non-visual: the reader persists six independent preferences (three pane widths,
immersive, serif, dropcap) with no reset. One "Reset layout" item in its menu makes
experimenting with the grips safe.

### 7.2 Diagrams — verified unaffected, on one condition

Unaffected:

- `diagramDir()` / `sd_diagram_dir` and the three-state toggle (`auto` ⤢ / `lr` ⇄ / `td` ⇅,
  `app.js` ~6572 and ~8058).
- `MM_DIR_RE`, `MM_FLIP`, `mmChartDirective(type, src, avail)` and the `_mmRO`
  `ResizeObserver` (~5675, ~5964) — all keyed off the prose column's available width.
- `diagrams/<key>.mmz` (~6559), which already carries both orientations, built by the same
  runtime code (`__mmBuildVariants`) for byte-identity.
- `openMermaidZoom()`, wheel/pinch zoom, `dataset.minw` surviving re-clamps, the `--od-*`
  tokens that alias `--rd-*`.
- `looksLikeDiagram()`, `highlightDiagram()`, the ASCII overlay.

**The condition: the rail must not shrink the reader.** If the rail is built as a flex sibling
of a static `main`, `--reader-w: min(58%, 760px)` resolves against a container 208px narrower,
the ResizeObserver sees less room, and every borderline TD diagram flips to LR on desktop.
Keep `#reader` fixed and full-viewport so the percentage keeps resolving against the viewport.

**Corollary:** do not put mermaid fences in the Tech shelf cards. A ~360px card is narrower
than any diagram wants, so it would render permanently flipped and re-render on every facet
change.

---

## 8. Usability fixes, ranked

**Blocking**

1. Closing the reader loses your place — §7.1. The most expensive interaction in the app,
   paid every time you finish reading.
2. 641–899px has no navigation — §3.1.
3. Four live routes can become unreachable — §2.

**Serious**

4. Home can stack twelve competing blocks — §4.1.
5. Deck mode is a global switch far from the action — §3.5, §4.1.
6. During a quiz you are nowhere; `active` is `null` — §5.6.
7. Ten controls in one header row, three jobs; icon targets under 44px on a phone — §3.5.

**Worth fixing**

8. Sections sort alphabetically though mastery is already computed — §5.1.
9. Tech's filter leaves no on-screen trace — §5.4.
10. Study is four levels deep with Back-only — §5.2.
11. The reader has six persisted prefs and no reset — §7.1.

**Keep** — the pre-paint theme script and the `daylight` migration; `aria-expanded` on the
streak and theme chips; the `#live` polite region; `guardedNav()` protecting a live deck.
New sub-navs should announce through `#live`; the collapsed overflow menu keeps its
`aria-expanded`.

---

## 9. Acceptance checklist

- [ ] All 12 existing hash routes still resolve; none renamed.
- [ ] Five destinations reachable at every width; `#/gauntlet`, `#/codex`, `#/debrief`,
      `#/interview/<section>` each reachable in ≤2 taps with no conditional card involved.
- [ ] No viewport between 320px and 2560px lacks a primary nav. Check 700px and 880px.
- [ ] Rail and tab bar sit below `#reader`; `#reader` is still `position: fixed; inset: 0`.
- [ ] Reader: F toggles immersive and native FS; all three pane toggles and their `.on`
      states work; grips resize; serif and dropcap persist.
- [ ] Diagrams: auto-flip still fires on grip-drag and on entering fullscreen; the ⤢/⇄/⇅
      toggle still cycles and persists; a `.mmz` diagram renders identically to before at the
      same reader width.
- [ ] Closing the reader from a book chapter returns to that book's chapter list.
- [ ] All 8 themes × 2 modes: no hard-coded colour; the week chart's seven bars, the empty
      coverage cells and every `.tbar` track are distinguishable from their card background.
      Check `obsidian` and light mode explicitly.
- [ ] Tab targets ≥44px; header down to two controls.
- [ ] `#live` announces destination and sub-nav changes.
- [ ] A live deck still prompts before a tab tap navigates away.
