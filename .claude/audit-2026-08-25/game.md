# Game (LORA) Audit — 2026-08-25

**Status: COMPLETE.** This is the third and final lead pass. Two earlier leads were killed by
API limits; their verified output is folded in below and marked. Everything else is new.

Provenance markers used throughout:
- **[V]** — salvaged from the earlier code-health pass, and **re-verified in source by this lead**.
- **[S]** — salvaged from a rescued subagent (`game-SUPPLEMENT-route-and-ux-map.md`), re-verified.
- **[NEW]** — researched by this lead; not in any earlier artefact.

This is a **planning pass**. No code was written; this report is the only file this pass
wrote. Verified with `git status`: no tracked file was modified by this session.
`python3 game/extract.py` was run once to get live numbers, and all of its outputs
(`game/questions/*.json`, `game/case_questions/*.json`, `game/tech_index.json`,
`game/graph/*.json`) are gitignored, so it left no trace either.

**Line numbers are against the current working tree**, where `app.js` is 9,536 lines. That
tree carries one uncommitted `app.js` change from 2026-08-25 (the `soft_skills` wiring:
`SECTION_LABELS`, `SECTION_IDENTITY`, `STUDY_ORDER`) which predates this pass and is unrelated
to it. Every line number here was re-read against that tree, not inherited from the salvaged
reports.

---

## 0. Snapshot

LORA is a **9,536-line single-file vanilla-JS SPA** (`game/app.js`) — one plain
`<script src>`, no `type="module"`, zero `import`/`export`, one global scope, 295 top-level
`function` declarations, one mutable `state` object (app.js:569). `localStorage` is the only
persistence; there is no server and no account. It ships twice from the same source: GitHub
Pages, and a sideloadable offline Android APK gated on three `IS_APK` branches. [V]

**Its real strength is that it is finished software, not a prototype.** The parts that
usually rot in a hand-rolled SPA are sound here and were checked individually: every
`localStorage` `JSON.parse` is guarded, every `fetch` is wrapped, there is no listener leak,
quota is handled with a real toast rather than a silent failure, modals have real focus traps,
`prefers-reduced-motion` is honoured in 11 CSS blocks, the quiz is fully keyboard-answerable,
error screens distinguish 404 from network failure, and boot pulls **128 KB**
(`index.json` 95,713 B + `paths.json` 35,473 B — measured), not the multi-MB the bank size
suggests. [V]

**Its single biggest weakness is not a bug and not the scheduler — it is arithmetic.**
Every answered question creates or updates a review record (app.js:1354-1363); a 10-question
blitz therefore schedules 10 items due tomorrow. But the blitz never consults due state
(`startBlitz` 2119-2155 samples fresh-vs-recently-served only), the daily gauntlet is
**hard-capped at 3 review slots out of 10** (`buildGauntletDeck` 8880-8886,
`if (picks.length >= 3) break;`), and the Home review card is opt-in and rendered only when
something is already due (1559). A user who plays the blitz and the gauntlet every day and
never taps that card accrues review debt at roughly **+7 items/day, forever**. The SM-2
schedule is computed correctly and then largely not delivered. [NEW]

| | |
|---|---|
| `game/app.js` | 9,536 lines, 295 top-level functions |
| `game/style.css` | 3,173 lines, 258 `font-size` declarations |
| `game/index.html` | 82 lines |
| `game/sw.js` | 95 lines |
| Build scripts | `extract.py` 1,251 · `build_tech.py` 1,388 · `build_graph.py` · `build_common.py` 75 |
| Bank (live, rebuilt this session) | **12,820 MCQs** across **16 content sections** |
| Parsed Q&As | 12,826 (6 deduped) |
| Authored `**Short:**` | **11,735 of 12,826** — the 1,091 gap is `book/`, by design |
| Case-study reader pool | 1,611 questions (separate pool, never in the main bank) |
| Technology index | 3,984 technologies · 3,770 bank records · 652 module files |
| Boot payload | 128 KB |

---

## 1. Pedagogy and product gaps

### 1.1 The headline finding: the scheduler is correct and under-delivered [NEW]

This is the finding that should reorder everyone's priorities, and it is arithmetic rather
than opinion. Traced end to end:

| Step | Code | Effect |
|---|---|---|
| Any answered question with an id | `saveSession` 1354-1363 | creates/updates a review record and calls `scheduleReview` |
| First correct answer | `scheduleReview` 1293-1296 | `interval = 1` → **due tomorrow** |
| Daily blitz | `startBlitz` 2119-2155 | samples by *no-repeat recency*, **never by due date** |
| Daily gauntlet | `buildGauntletDeck` 8880-8886 | takes up to 8 due items but `break`s at **3 picks**; the other 7 slots are suggested-section, weak-section, and one advanced final |
| Home review card | `renderHome` 1559-1575 | the only unbounded review path — and it is **opt-in**, rendered only `if (due.length && !reboard)` |

So the *only* automatic delivery of spaced repetition is 3 questions/day. A daily 10-question
blitz manufactures up to 10 due items/day. Net **+7/day** for a user who never taps the card.
The Home card is well designed — it shows a rounds-to-clear estimate, per-section chips, and a
"+N due tomorrow" whisper (1560-1573) — but it is a card among seven conditional cards, and it
competes with the primary `#startBtn` CTA that is always present.

**A second-order consequence, verified:** `pruneReviews` (1173-1191) explicitly refuses to
evict anything `due <= today` (line 1181). It also protects flagged items and capsules. So
overdue debt is exempt from the `REVIEWS_CAP = 6000` ceiling. Once overdue count reaches 6,000
the map grows past its cap unbounded, and `safeSet` eventually trips the quota toast and stops
saving progress. At +7/day that is roughly 2.3 years of daily play — distant, but it is a real
terminal state, and the fix is the same fix as the P0.

**The fix is not a scheduler migration.** Whichever algorithm computes the interval,
if the daily loop consults it for 3 of 10 slots the algorithm is decorative. The cheapest
correct change is to make the blitz due-aware: fill the deck from `dueReviews()` first and
top up with fresh sampling, the way Anki's daily queue mixes reviews and new cards under one
cap. That is a small edit inside `startBlitz`, reuses `dueReviews()` (1418-1424) which already
exists and already sorts flagged-first then oldest-due, and it needs no new UI at all.

### 1.2 The scheduler is NOT textbook SM-2 — the brief's premise was wrong [NEW]

`scheduleReview` (app.js:1289-1311) is labelled "SM-2-lite" in its own comment and differs
from SM-2 in four ways that matter to any migration argument:

| | SM-2 | LORA |
|---|---|---|
| Grade input | 0-5 self-report | binary correct/incorrect, **plus a confidence signal** (`conf` high/low) |
| Ease on success | `EF += 0.1 - (5-q)(0.08 + (5-q)0.02)` | flat `+0.1`, **capped at 3.0** (1292) |
| Ease on failure | reset scheme | `-0.3` on a *high-confidence* miss, `-0.2` on low — an explicit **hypercorrection** term (1303) |
| Interval on success | `I *= EF` | `I *= EF * 0.75` when confidence is low **or the answer was slow** (1296, and `eff` is downgraded at 1360-1362 when `ms > 2 * medianCorrect`) |

Two consequences. First, the app already carries a per-item difficulty signal (confidence +
answer-latency EMA, `rv.ms`) that plain SM-2 has no slot for — which is precisely the
information FSRS's difficulty parameter is trying to infer. Second, the **ease ceiling of
3.0** caps interval growth at 3× per review; combined with the `0.75` low-confidence factor
this is a deliberately conservative schedule. Any argument that "SM-2 over-schedules" does not
apply cleanly to this implementation.

### 1.3 SM-2 → FSRS: the evidence, and the recommendation

**First, the brief's premise is wrong, and I verified this against primary sources myself
because it is load-bearing.** The audit brief states "FSRS is now the Anki default". It is
not. The Anki manual's Deck Options page says FSRS must be turned on by hand — *"Enable FSRS
under the 'FSRS' section, at the bottom of the deck options page"*
(https://docs.ankiweb.net/deck-options.html, fetched 2026-08-26). And the maintainer's own
issue **"Make FSRS the default?"** (`ankitects/anki#3616`, opened 2024-12-06 by `dae`, 148
comments) is **still open with no resolution** — confirmed directly against the GitHub API,
not a mirror (https://github.com/ankitects/anki/issues/3616). FSRS has been *available* since
Anki 23.10 (2023-10-31). It is not the default.

This matters because the SEO layer says otherwise. A web search for this question returns
several 2026 blog posts asserting "FSRS is Anki's default since 23.10" — while the same posts
tell the reader to "enable FSRS in deck options". Do not take the migration argument from
those.

**Recommendation: do not migrate. P2 at best, and only after §1.1 is fixed.** The reasoning
is not that FSRS is worse — the benchmark evidence is real (§1.7) — it is that the three
preconditions for FSRS paying off are all absent here:

1. **FSRS's advantage comes from per-user parameter optimization**, and that needs history.
   Anki removed the hard minimum review count in 24.06.3, but the manual still names
   *"Low number of reviews (less than a few hundred)"* as a leading reason FSRS underperforms
   (https://docs.ankiweb.net/deck-options.html;
   https://faqs.ankiweb.net/frequently-asked-questions-about-fsrs.html). LORA is
   localStorage-only with **no account and no sync**: one training set per browser profile,
   starting empty, destroyed when site data is cleared. Default (unoptimized) parameters are
   where most LORA users would permanently live — which is the configuration with the least
   evidence behind it.
2. **FSRS was tuned on Anki's four self-reported grades.** LORA grades binary from an MCQ,
   where a 25% guess rate contaminates every "correct". The one primary-source investigation
   of binary grading — FSRS analyst Expertium's comparison on the Anki forum — concluded
   verbatim **"The results are inconclusive"**
   (https://forums.ankiweb.net/t/pass-fail-grading-as-default/34147/120). That is not evidence
   against FSRS; it is the absence of evidence for it on LORA's actual input.
3. **The delivery bottleneck (§1.1) dominates the scheduling term.** Improving a schedule that
   is consulted for 3 of 10 daily slots is not a measurable improvement to anyone.

A fourth point specific to this codebase: LORA's scheduler is not plain SM-2 (§1.2). It
already carries a per-item difficulty signal — confidence plus an answer-latency EMA — which is
the information FSRS's difficulty parameter exists to infer. The gap between LORA and FSRS is
narrower than the gap between *textbook SM-2* and FSRS, which is what the benchmarks measure.

If it is ever revisited, the cheap version is `ts-fsrs` (MIT, ships a **UMD build** so it drops
into a plain `<script>` SPA with no build step, no production dependencies —
https://github.com/open-spaced-repetition/ts-fsrs) running on **default parameters only**. The
optimizer is not pure JS: it lives in `@open-spaced-repetition/binding`, a Rust/`fsrs-rs`
wrapper. So the realistic offer is "a different default curve", not "a better-fitted model" —
and that is not worth a scheduler rewrite in a 9,536-line single-file SPA.

**Two SM-2 refinements are worth more than the migration, and are ~5 lines each:**

- **Interval fuzz.** `scheduleReview` is fully deterministic — `grep` for fuzz/jitter finds
  nothing relevant. [V] Anki fuzzes intervals specifically to stop a cohort scheduled on the
  same day from re-clumping on the same future day forever. LORA's blitz schedules 10 items on
  one day, all with `interval = 1`, then 3, then ease-scaled — they march in lockstep, which is
  exactly the pathology fuzz exists to break, and it compounds §1.1. Anki's implementation is
  piecewise, not a flat ±5%: **no fuzz below 2.5 days, 0.15 from 2.5-7 days, 0.10 from 7-20,
  0.05 above 20**, accrued cumulatively across the bands
  (https://github.com/ankitects/anki/blob/main/rslib/src/scheduler/states/fuzz.rs). Note the
  jitter is *front-loaded* — biggest in relative terms on short intervals, which is where
  LORA's clumping happens. Port the band table; it is ~5 lines.
- **A max interval.** There is no ceiling. With `ease` capped at 3.0 growth is slow, but
  nothing stops an item reaching a multi-year interval and effectively leaving the system.

### 1.4 Leeches are surfaced but never suspended [V, extended NEW]

`leechIds()` (app.js:3701-3706) flags `lapses >= 3`, sorts by lapse count, and **slices to the
top 7**. `leechesHTML()` (3707) shows them under "Your hardest questions" with a Drill button
(3825 → `startRefresh(ids)`). That is the whole lifecycle: display and drill.

Two gaps, not one. [NEW] The known one: unlike Anki, a leech is never suspended or exiled, so
a genuinely broken item keeps consuming review slots indefinitely — which compounds §1.1.
Anki's defaults for reference, verified at the manual: **threshold 8 lapses**, and the action
is *both* — *"When this counter reaches 8, Anki tags the note as a leech and suspends the
card"* (https://docs.ankiweb.net/leeches.html). LORA's threshold of 3 is much more aggressive
than Anki's 8, which makes the missing suspend action more consequential, not less. The
unremarked one: **the `.slice(0, 7)` is a display cap applied inside the identity function**,
so a user with 200 leeches has no way to see or act on 193 of them, and no count anywhere tells
them the number. `leechIds()` is used by all three call sites (3708, 3719, 3825), so the drill
is capped at 7 too.

### 1.5 What the app already gets right, pedagogically [NEW]

Recorded so nobody proposes building it twice:

- **Interleaving is implemented, and correctly scoped.** `startBlitz` 2148-2153: when a deck
  spans more than one module it loads the section graph and passes `interleave` pairs into
  `startDeck` so no two consecutive questions share a module and graph-connected topics sit
  adjacent (A-B-A'-C). That is interleaving at the level where it is known to help —
  *discriminating between confusable categories* — not merely shuffling.
- **Pretesting is implemented.** `maybePrime` / `showPrimeSheet` (2310-2341) offers three
  ungraded guesses *before* reading a module, with `state.prime` suppressing XP, combo,
  snapshot, and confidence side effects (2065). This is errorful-generation pretesting, and
  the "Nothing counts" copy is exactly right for it.
- **Hypercorrection is implemented.** A high-confidence miss cuts ease harder than a
  low-confidence one (1303) — the effect where confidently-held errors, once corrected, are
  retained better than low-confidence ones.
- **Desirable difficulty is implemented.** Slow-but-correct is treated as low confidence
  (1360-1362) and shrinks the next interval.
- **Redemption retry.** A first miss re-queues the item within 3 questions (2912-2916) — spaced
  retrieval at the shortest possible lag.
- **The two-stage read-then-quiz loop.** `appendEvalBlock` (7625-7640) puts a "Quiz this
  topic" button inline at the end of a read page, sourced from that page's own questions.
  Read-then-test is the standard sequence and it is wired without the user having to navigate.

The pedagogy layer is genuinely thoughtful. The gap is delivery (§1.1), not design.

### 1.6 The real product gap: nothing explains the machine [NEW, extends S]

Onboarding is four one-shot coach marks — `COACH_MARK_IDS` (781): `first_question` (2733),
`first_combo` (2590), `first_results` (3261), `first_cards` (9491). Verified. [S]
`seedCoachMarksIfVeteran()` (782-788) correctly no-ops on an empty history so a genuine
first-run sees all four.

**No mark, no screen, and no help page ever explains spaced repetition, the gauntlet, or the
XP/level math.** Verified by grep: the strings "SM-2" and "spaced repetition" appear in app.js
only in code comments (1167, 1287, 1331, 2363, 2431, 3069) and as an *unexplained eyebrow
label* "Spaced repetition" at 1569 and 1615. `toggleHelp()` (821-846) is a keyboard cheat
sheet and nothing else — I read its full markup; it has five columns, all keybindings.
`levelFromXP` is `Math.floor(xp / 250) + 1` (1442) and `xp = correct * 10 + streak * 5 + bonus`
(1396) — neither is stated anywhere in the UI.

**Where the explanations actually live is the finding: hover-only `title=` attributes.**
There are 55 (`grep -c 'title="'` → app.js 45, index.html 10), and **40 of the 55 lines carry
no `aria-label`**. The ones carrying the app's only self-explanations are:

| Location | title text | This is the only place it is said |
|---|---|---|
| index.html:33 `#lvlChip` | "Level (from total XP) · ring shows progress to next level" | the entire level system |
| app.js:1534 `.freeze-chip` | "Streak freezes auto-cover a single missed day" | the entire freeze mechanic |
| index.html:42 `#modeBtn` | "Quiz vs flashcards" | what the mode toggle does |
| app.js:3603 heatmap cell | `${iso}: ${xp} XP · gauntlet` | date, XP, and gauntlet-day flag for every cell of the streak heatmap |
| app.js:8536 debrief week-cell | `${c.iso}: ${c.xp} XP` | date and XP for every cell of the weekly debrief strip |
| app.js:3649 forecast column | `${d.iso}: ${d.n} due` | the precise date of each forecast bar (the *count* is visible in `.fc-n`) |
| app.js:4950 `.pathnode` | e.g. "est. retention 75%, 2 due" | the retention estimate behind each study-tree node's colour |

`title=` does not fire on touch. On a phone — where a daily-habit app is used — the level
system, the freeze mechanic, and **the data content of three charts** are simply absent.

Severity differs across the four, and it is worth separating them:

- **Worst: the streak heatmap (3603) and the debrief week strip (8536).** Only a coarse
  5-bucket colour class survives, and the heatmap cells are additionally `aria-hidden="true"`
  so screen-reader users get nothing per-cell either. The heatmap at least has an aggregate
  `role="img" aria-label="Activity, last 17 weeks: N active days, N XP"` on its wrapper; **the
  debrief strip has no aggregate fallback at all**, sighted or assistive.
- **Mild: the forecast (3649).** The due count is already visible in `.fc-n` and a weekday
  letter in `.fc-d`; only the exact calendar date is lost.
- **Sighted-touch only: `.pathnode` (4950).** The same string is duplicated into the child
  `#pn-main` button's `aria-label` (4951), so assistive tech announces it on tap. Not an a11y
  defect — a touch-UX one.

### 1.7 Web-research evidence appendix

Every claim carries its source. Claims I verified at the primary URL **myself** are marked ✔;
the rest come from a research subagent and are marked with their confidence. Nothing here is
reported as settled that the source does not settle.

#### FSRS status and accuracy

| Claim | Source | Confidence |
|---|---|---|
| FSRS available in Anki since 23.10 (2023-10-31), as an option | https://changes.ankiweb.net/changes/23.10.html | high |
| **FSRS is NOT the default; it must be enabled in Deck Options** | https://docs.ankiweb.net/deck-options.html | ✔ **verified by this lead** |
| **"Make FSRS the default?" (`ankitects/anki#3616`, opened 2024-12-06) is still OPEN** | https://github.com/ankitects/anki/issues/3616 (GitHub API) | ✔ **verified by this lead** |
| Version line: v4 (2023-07) → 4.5 (2023-12) → 5 (2024-07) → 6 (current) | https://github.com/open-spaced-repetition/fsrs4anki/releases | high |
| Benchmark, 9,999 collections / 349.9M reviews: FSRS-6 log loss 0.3460, RMSE(bins) 0.0653, AUC 0.7034; FSRS-5 0.3560 / 0.0741 / 0.7011 | https://github.com/open-spaced-repetition/srs-benchmark | high |
| FSRS-6 vs SM-17 (19 users, 687k reviews): log loss 0.367 vs 0.432; RMSE(bins) 0.048 vs 0.066; AUC 0.662 vs 0.606 | https://github.com/open-spaced-repetition/fsrs-vs-sm17 | high |
| "FSRS-6 has a 99.6% superiority over Anki SM-2" | https://expertium.github.io/Benchmark.html | **LOW — a blog paraphrase.** The srs-benchmark table contains only FSRS-family rows; no raw FSRS-vs-plain-SM-2 numeric table was located |
| Optimizer minimum: history 1000 → 400 → removed; "can be used with any number of reviews" since 24.06.3, but "less than a few hundred" is a named cause of poor performance | https://faqs.ankiweb.net/frequently-asked-questions-about-fsrs.html · https://docs.ankiweb.net/deck-options.html · https://github.com/ankitects/anki/issues/3094 | ✔ (manual half) |
| `ts-fsrs`: MIT, TypeScript, ships ESM/CJS/**UMD**, no production deps | https://github.com/open-spaced-repetition/ts-fsrs | high |
| The **optimizer is not JS** — it is `@open-spaced-repetition/binding`, a Rust `fsrs-rs` wrapper | https://www.npmjs.com/package/@open-spaced-repetition/binding | medium — whether it has a browser/WASM target was not resolved |
| Binary (2-button) vs 4-button grading: FSRS analyst's own comparison concluded **"The results are inconclusive"** | https://forums.ankiweb.net/t/pass-fail-grading-as-default/34147/120 | high — and it is the only quantitative treatment found |

#### Retrieval practice, pretesting, MCQ format

| Claim | Source | Confidence |
|---|---|---|
| Testing effect, canonical: repeated *study* wins immediately, repeated *testing* wins at 2-day and 1-week delays — the crossover is the finding | Roediger & Karpicke 2006, *Psych Science* 17(3):249-255 — https://pubmed.ncbi.nlm.nih.gov/16507066/ | qualitative pattern high; **exact effect sizes NOT verified** (PDF fetch failed) |
| **Pretesting works on material not yet read.** Five experiments; participants answered questions about a passage *before* reading it; "Posttest performance was better in the test condition than in the extended-study condition in all experiments … even though only items that were not successfully retrieved on the pretest were analyzed" | Richland, Kornell & Kao 2009, *JEP: Applied* — https://pubmed.ncbi.nlm.nih.gov/19751074/ · https://learninglab.uchicago.edu/Pre-Testing_files/RichlandKornellKao.pdf | high — the failure-only analysis is what makes it apply |
| MCQ is not simply the weaker format: short-answer wins **with** corrective feedback, but **initial MCQ produces the greater benefit when no feedback is given** | Kang, McDermott & Roediger 2007, *EJCP* 19(4-5):528-558 — https://www.researchgate.net/publication/224943432 | medium — the surfaced PDF filename suggests a different paper by the same lab; confirm the title before citing in public |
| **Competitive/plausible distractors add pedagogical value** — they trigger retrieval of *why* the wrong options are wrong, and that transfers to later cued-recall tests | Little, E. Bjork, R. Bjork & Angello 2012, *Psych Science* — https://pubmed.ncbi.nlm.nih.gov/23034566/ · https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/07/Little_EBjork_RBjork_Angello_2012.pdf | high |

**The Little et al. result is the one to act on.** It is the direct scientific justification
for `extract.py`'s IDF-ranked, module-first, `jaccard > 0.7`-filtered distractor selection
(§5): distractors drawn from *related* answers in the *same module* are competitive by
construction, and competitive distractors are where MCQ's transfer benefit comes from. The
existing distractor machinery is not merely adequate — it is the evidence-backed design, and
that is a second reason not to touch it.

#### Interleaving

| Claim | Source | Confidence |
|---|---|---|
| Interleaved > blocked practice at a delayed test, despite **worse** performance during practice — the defining desirable difficulty | Taylor & Rohrer 2010, *Applied Cognitive Psychology* — http://uweb.cas.usf.edu/~drohrer/pdfs/Taylor&Rohrer2010ACP.pdf | direction high; the widely-quoted **77% vs 38%, d = 1.21 figures were NOT extracted from the PDF** — treat as uncited |
| **Boundary condition:** the advantage shrinks or vanishes for complex multi-step problems where inferring the problem *type* is itself the hard step | https://link.springer.com/article/10.3758/s13421-019-00918-4 · https://www.sciencedirect.com/science/article/abs/pii/S0959475222000044 | medium |
| **Learners rate blocked practice as more effective even after seeing their own better interleaved scores** | Kornell & Bjork 2008, *Psych Science* 19(6):585-592 — https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/07/Kornell_Bjork_2008_PsychScience.pdf | high |

**Product consequence.** Kornell & Bjork is the argument for keeping the interleaved blitz as
the *default* and the single-module topic picker as the *opt-in* — which is what LORA already
does (`startBlitz` interleaves whenever a deck spans >1 module, 2148-2153; `#/topics/<sec>`
is the deliberate two-click path to blocked drilling). Users will ask for blocked practice and
will be wrong; the current defaults already handle that correctly. **Do not flip them.**

The boundary condition is the one live risk: LORA's interleaving is *within a section*, across
modules — precisely the "discriminate between confusable categories" case where the effect is
strongest, not the "infer the problem type" case where it weakens. The design is on the right
side of the boundary.

#### Oversized banks

No academic literature was found on spaced repetition when the item bank vastly exceeds
realistic throughput — LORA's exact case (12,820 items at ~10/day is **~3.5 years to see the
bank once**). Everything available is Anki community practice
(https://controlaltbackspace.org/catch-up/,
https://forums.ankiweb.net/t/too-many-cards-to-review-whats-the-best-way-to-tackle-this/23419),
which converges on: pause new-card introduction until the backlog clears, sort overdue by
*relative overdueness* rather than raw due date, never mass-reset.

**That folklore is nonetheless the right shape for §1.1's fix.** "Pause new material while a
backlog exists" is exactly the lever LORA lacks — its blitz introduces new items
unconditionally. Treat this as an engineering convention with no academic backing, and state
it as such if anyone asks why.

---

## 2. Verified findings — code health [V, all re-verified this pass]

### The one real accessibility gap — font scaling
`style.css` carries **258 `font-size` declarations: 167 simple `px` literals, 80 `var()`,
3 `clamp()` with px endpoints, 7 `em`-relative, 1 `inherit`. Zero use `rem`.** The only two
`rem` values in the file at all are `margin`/`padding` on line 1576-1577. Verified by direct
grep this pass; supersedes the original "0 literal rem" phrasing. The type-scale tokens are
px too — `--fs-2xs: 10.5px` / `--fs-xs: 11.5px` / `--fs-sm: 12.5px` / `--fs-md: 13.5px`
(style.css:66-69). **The UI does not respond to the browser's root-font-size accessibility
setting**; only full-page zoom works. Fully scoped in §6, wave 2.

### Ten icon-only controls have no accessible name — and nine are the reader toolbar [NEW]

A full sweep of ~40 icon-only control definitions (every `<button>` whose content is only an
`<svg>` or a bare glyph), each checked against both markup *and* runtime `setAttribute`, found
**10 with no accessible name**. Verified by reading the template blocks directly.

| file:line | control | what it does |
|---|---|---|
| index.html:43 | `#themeBtn` | theme popover |
| app.js:7820 | `#readerMod` ☰ | reader module list |
| app.js:7840 | `#readerFsDn` | smaller text |
| app.js:7841 | `#readerFsUp` | larger text |
| app.js:7842 | `#readerType` | reading options popover |
| app.js:7843 | `#readerDir` | diagram orientation |
| app.js:7844 | `#readerFindBtn` | find in page |
| app.js:7845 | `#readerIdx` | table of contents |
| app.js:7846 | `#readerFull` | immersive reading |
| app.js:7847 | `#readerClose` | close the reader |

**Nine of the ten are one template block, `app.js:7834-7853` — the reader's top toolbar, which
is the primary reading surface on the Pages build.** Each has a `title=` and nothing else.

Two details make this an oversight rather than a decision, and both make it cheap to fix:

1. **The APK-only bottom bar directly below (7827-7833) labels its twins correctly** —
   `#readerModB aria-label="Module list"`, `#readerIdxB aria-label="Contents"`,
   `#readerFindB aria-label="Find in page"`. That bar is `display:none` on web, so the labelled
   copies never reach a Pages user and the unlabelled copies always do.
2. **Two buttons in the very same block DO carry labels** — `#readerExit` (7851) and
   `#readerTop` (7853). And the reading-options popover's own A−/A+ pair (7026-7027) carries
   `aria-label="Smaller text"`/`"Larger text"` on the identical glyphs the unnamed toolbar pair
   uses. The author already judged these glyphs insufficient elsewhere.

So the fix is copying labels that are already written, into a block whose neighbours already
have them. Roughly 10 attributes.

**Checked and CLEARED — recorded so nobody re-flags them:** `#muteBtn` (looks identical in
markup but `syncMuteBtn()` 9391-9398 sets `aria-label` at runtime), `#moreBtn`, `.subtoggle`,
`.ra-flag`, `.ra-copy`, `#qpauseBtn` (all 3 sites), the four `.mz-*` diagram-viewer buttons,
the three `.rd-find-*` buttons, `#readerExit`, `#readerTop`, `.nudge-dismiss`, `.cx-card`,
`#brandHome`, and the four bottom tab-bar `.tab`s. `#moreSound` is named by visible text set
in `syncSnd()`. `#studyTech`/`#studySearch`/`.tx-deepcard`/`#streakChip` are not icon-only —
each has a visible text node. `secChip()` (4291) is dead code, never called.

### The prime-sheet opt-out has a hole [NEW]
`primeEligible` (2303-2308) stops offering the pretest interstitial after `sd_prime_opt >= 3`.
The "Just read" button increments that counter (2335-2337). **The backdrop-click handler
(2340) calls `close(); justRead();` and never increments it.** A user who always dismisses by
tapping outside the sheet gets the interstitial on every unquizzed module, forever. One-line
fix: route the backdrop through the same handler body as `#primeSkip`.

### Stale comment, correct code — `REVIEWS_CAP`
app.js:1164-1172 comments "the ~8,800-question bank ceiling (~1.7 MB)". The live bank is
**12,820**. The cap itself is sound and independent of bank size — `REVIEWS_CAP = 6000`
records × ~190 B ≈ **1.14 MB**, well under the ~5 MB origin quota. Doc fix, not a behaviour
fix, worth doing before someone reasons from the stale number. (But see §1.1 — the *eviction
policy* has a real hole that the comment does not cover.)

### No interval fuzz
`scheduleReview()` (1289-1311) is deterministic. See §1.3.

### Leeches surfaced, never suspended
See §1.4.

### Boot is well-architected — a suspicion refuted
Boot fetches `index.json` + `paths.json` = **128 KB measured**, not the 7.4 MB a naive reading
suggests. `loadTechIndex()`/`loadTechBank()` are deliberately not awaited (9437, with a comment
saying so) and per-section banks load lazily. Only nit: the two boot fetches are sequential
`await`s that could be `Promise.all` — immaterial at 128 KB. **The bank's growth does not
break boot.**

---

## 3. UX flow — the gaps, and the judgment

The full router / screen / empty-state map is in `game-SUPPLEMENT-route-and-ux-map.md` and
was re-verified at every cited line this pass. What follows is the judgment it was missing.

### 3.1 The four gaps, ranked — and three of them share one fix

`renderTech()`'s `emptyHTML(q)` (4424-4447) is the correct pattern and it already exists in
this file: it diagnoses *which facet* produced zero rows ("N by section, N by the problem
area, N by language hidden") and renders a **Clear filters** button (4447). Verified by
reading it. **The fix for gaps 3 and 4 is to copy an internal pattern, not to invent one.**

| # | Gap | Line | Severity | Judgment |
|---|---|---|---|---|
| 1 | **Reader end-of-content dead end** | `appendWhatNext` 7701 `if (!cards.length) return;` | **P0** | Worse than the supplement scored it — see 3.2 |
| 2 | **Eval-block silent absorb** | 7630 SR-only `announce()`, 7640 `block.remove()` | P1 | The click path announces to screen readers only; the background path deletes the widget with no explanation. A sighted user sees a button vanish. |
| 3 | `openTopics` filter, zero matches | 1993-1997 | P1 | Rows hidden by `style.display`; no count, no message, no reset. Copy `emptyHTML`. |
| 4 | `openStudySection` filter, zero matches | 5286-5296 | P2 | Better than #3 — it does maintain a `#pathCount` ("0 of N topics") — but still no reset affordance. Add the button only. |

### 3.2 Why the reader dead end is P0, not P2 [NEW]

The supplement called it "the cleanest true dead end in the app". Reading the function
(7675-7705) makes it worse than that, because of *when* it fires:

`appendWhatNext` builds at most 3 cards from three sources — the next unread module in
`reader.nav.list`, graph-related modules, and a "Resume" for a different last-read page. The
graph source is `await loadGraph(section)` (7676), and **`loadGraph` returns null whenever the
`graph/<section>.json` fetch fails** (950-953 → `fetchJSON(..., null, "default")`). So on a
cold offline cache the graph term is null for *every page*, not just the last one. Combine
that with the last module of a section (no next unread) and a last-read path equal to the
current page, and `cards.length` is 0 — at which point the function renders **nothing at all**:
no rule, no heading, no "you finished this section".

Meanwhile the reader's own Prev/Next buttons are `disabled` at the list edges (7822-7823). So
the terminal state of a section is: a page with a disabled Next, a disabled-or-backward Prev,
and no continuation affordance whatsoever. **This app ships as an offline APK.** That makes
the dead end a routine offline state rather than an edge case, which is why it moves to P0.

The fix is one line of intent: replace the bare `return` with a terminal card — "You've
finished <Section>" plus a link to the section's study tree, or to the next section in
`STUDY_ORDER`. The `push()` helper and the `.whatnext` block markup already exist directly
below the guard.

### 3.3 The explanation gap is the real UX finding [NEW]

Covered in §1.6 and not repeated. In UX terms: the app has a sophisticated model (SM-2 with
confidence weighting, a sealed daily gauntlet with a four-stage recipe, an XP→level→job-title
ladder, streak freezes, leeches, confusion pairs) and **shows the user only its outputs**.
The one surface that could explain it — the `?` overlay — is a keyboard cheat sheet. The
places that do explain it are hover-only tooltips that a phone never renders.

This is a bigger product gap than any of the four empty states, and it is why "add a
mechanics panel to the `?` overlay" is P0 in §6 rather than a nice-to-have. It reuses an
existing modal, an existing focus trap, and an existing open path (`?` key + the More sheet
on phones), so the cost is copy plus one column.

### 3.4 Smaller stuck-states, all verified, all P2

- Interview locked below Silver shows a transient toast and redirects (9245-9248) with no
  statement of how Silver is reached.
- An unknown hash silently becomes Home (8687) with no 404.
- `#/quiz/*` is deliberately unbookmarkable (8669-8674) — this is *correct* (a live deck cannot
  survive a refresh) and the redirect to `#/topics/<sec>` is the right recovery. Not a defect.
- The prime-sheet backdrop hole (§2).

---

## 4. Code health — checked and found SOUND [V]

Recorded so a future audit does not re-litigate these. Every item below was verified against
source; the ones I re-checked this pass are marked ✔.

- **`IS_APK` gating is exactly as documented** ✔ — 4 hits: the definition (app.js:10), two
  functional branches (vendored Mermaid loader 6191, skipped SW registration 9532), one comment
  (6343). The third documented hook, `SDAndroid.saveBackup`, is correctly feature-detected on
  `window.SDAndroid` (3430), not on `IS_APK`.
- **Mermaid pinned exactly** at `11.16.0` (6206) ✔, not a floating `@11` tag, with a comment
  explaining why.
- **All 20 `JSON.parse` of localStorage are try/catch-guarded.** A single-line grep flagged 4
  as unguarded; each sits inside a multi-line `try {}` — verified false positives.
- **No unguarded `fetch()`.** The shared `fetchJSON()` wrapper try/catches; the three raw call
  sites (icon packs 6227, `.mmz` 6395, reader markdown 7951) are each inside one.
- **No event-listener leak.** 235 `addEventListener` vs 16 `removeEventListener` looks alarming,
  but most attach inside `app.innerHTML`-replaced subtrees the browser GCs on route change. All
  23 persistent `document`/`window` listeners were checked individually: modal ones are paired
  on close, and the resize/orientation hooks are guarded against re-registration
  (`window.__mmViewportHook` 5806; `_pathResize` de-dupe 5330).
- **`safeSet()` (1151) never throws** and shows a one-time quota toast. Only
  `sd_progress.reviews` is unbounded and it is capped (but see §1.1 for the eviction hole).
- **`.mmz` fallback is still wired** — precomputed-first via `loadDiagramAsset()`, falling back
  to live `ensureMermaid()` + `mmRenderNode()` on a miss.
- **Accessibility, the good parts:** quiz fully keyboard-answerable (1-4, Enter, S, D, E); a
  real focus trap (`trapFocus()` 617) at ~15 modal sites, restoring focus on close; quiz
  feedback announced via `aria-live="polite"` (index.html:57, `announce()` 610, used at 2859);
  contrast ~16.8:1 default and 21:1 in the reader; `prefers-reduced-motion` honoured in 11 CSS
  blocks plus a JS helper (672).
- **Mobile is well-defended:** `viewport-fit=cover`, explicit 44px min touch targets
  (style.css:1938-1940), 18+ `env(safe-area-inset-*)` sites, `overflow-x: clip` globally with
  dedicated scroll containers for tables/code/diagrams, hover affordances with
  `@media (hover: none)` fallbacks (e.g. `.lp-chords { display: none }` on coarse pointers).
- **Error and empty states are mostly excellent** ✔ — section bank fetch failures route to a
  shared `errorScreen` with Try-again/Home (1865, 4847, 2125); reader 404 vs network failure
  are *distinguished* (7987-8015), with the "Open module home" button correctly omitted when
  the missing page IS the module home (8002); boot index failure (9439) and offline tech index
  (4101) each get their own screen; every modal closes via Escape (8696-8701), backdrop, or a
  button.
- **Review self-healing** — `startReview()` (2157-2205) repairs orphaned review records with a
  one-time toast (2181-2187) rather than crashing on ids whose questions were renamed.

**Largest functions**, measured by matching braces rather than distance to the next `function`
keyword: `renderTech()` 639 (4092-4731), `openStudySection()` ~592 (4804-5395),
`openReaderPath()` ~228, `renderHome()` ~192, `finish()` ~176.

A naive gap measure is actively misleading here and I reproduced the trap: measuring
declaration-to-declaration distance reports `titleize()` (118) as the 4th-largest function at
256 lines, when it is 8 lines followed by a long comment block. Use brace matching.

---

## 5. The distractor-pool question — ANSWERED [NEW]

**The premise is false. The distractor pool ALREADY draws from `**Short:**` lines. No code
change is needed, and none should be made.**

### Why

`extract.py` stores the authored Short and the derived fallback in **one field**. `parse_md`
(307-318):

```python
if authored_short:
    short = strip_markdown(authored_short)      # the **Short:** line
    ...
else:
    short = make_short(answer_full)             # the first-sentence derivation
...
"answerShort": short,
```

`build_questions` (899-1037) then uses that **same field** for both sides of the MCQ:

```python
correct = q["answerShort"]                       # line 950
...
text = raw[j]["answerShort"]                     # line 962 — the distractor candidate
ans_toks = [set(tokenize(q["answerShort"])) ...] # line 917 — the IDF model
```

So the day a Q&A gained a `**Short:**` line, its text became both its own correct option *and*
its contribution to every sibling's distractor pool. The migration to Shorts as distractors
happened silently and automatically as the content migration proceeded. IDF ranking, the
`jaccard > 0.7` also-correct filter, and the `JUNK_LEAD` skip all operate over Shorts today.

### Measured against the live bank, not reasoned about

I rebuilt the bank, extracted every authored `**Short:**` line from disk (15,610 unique), and
checked every distractor string in `game/questions/*.json` for a verbatim match:

| section | distractors | = an authored `**Short:**` | % | ellipsis-truncated |
|---|---|---|---|---|
| backend | 1,797 | 1,797 | **100.0%** | 0 |
| cs_fundamentals | 2,148 | 2,148 | **100.0%** | 0 |
| cuda | 1,386 | 1,386 | **100.0%** | 0 |
| database | 1,725 | 1,725 | **100.0%** | 0 |
| devops | 2,136 | 2,136 | **100.0%** | 0 |
| fastapi | 1,074 | 1,074 | **100.0%** | 0 |
| hld | 978 | 978 | **100.0%** | 0 |
| java | 1,998 | 1,998 | **100.0%** | 0 |
| lld | 3,768 | 3,768 | **100.0%** | 0 |
| llm | 6,591 | 6,591 | **100.0%** | 0 |
| ml | 4,836 | 4,836 | **100.0%** | 0 |
| python | 1,302 | 1,302 | **100.0%** | 0 |
| soft_skills | 1,317 | 1,317 | **100.0%** | 0 |
| spring | 2,319 | 2,319 | **100.0%** | 0 |
| technologies | 717 | 717 | **100.0%** | 0 |
| **book** | 4,368 | 1,049 | **24.0%** | 0 |
| **TOTAL** | **38,460** | **35,141** | 91.4% | **0** |

Every non-book section is at 100%. The 8.6% shortfall is entirely `book/`, exactly as the
migration status predicts. `make_short()`'s truncation path is not firing anywhere outside
`book/` — zero ellipsis-truncated distractors in the entire bank.

### The corollary: there is no length tell

Because the correct option and the distractors are drawn from the *same* field, they are
homogeneous by construction. The classic MCQ giveaway — "the longest option is the right one" —
does not exist here:

| | correct = longest | correct = shortest | chance |
|---|---|---|---|
| whole bank (12,820 Qs) | **24.7%** | 27.6% | 25% |

Mean correct-option length 130.9 vs mean distractor length 131.4 across the bank. This is the
strongest single argument that the current design is right: the property the brief hoped to
gain by switching is already present, and it is present *because* the two sides share a field.
**A change that gave the correct option and the distractors different provenances would
introduce the tell, not remove it.**

### The one real finding: `book/`'s partial migration IS producing a tell

`book/` is the only genuinely mixed pool (365 authored Shorts, 1,091 derived), and the two
forms are measurably different objects:

| option form (book only) | n | mean len | median | ≥200 chars |
|---|---|---|---|---|
| authored `**Short:**` | 1,414 | 122.2 | 112 | **0.6%** |
| derived `make_short()` | 4,410 | 153.1 | 157 | **14.0%** |

Derived options run ~31 characters longer and are **23× more likely to exceed 200 characters**.
The consequence, measured per question:

- In **108 of 1,456 book questions (7.4%)** the correct option is the **lone authored** option
  among three derived ones — visibly shorter and crisper than its three neighbours.
- In 21 (1.4%) it is the lone derived one.

So ~9% of book questions leak signal through option *form* rather than content. A test-wise
learner can exploit the 7.4% case.

**Recommendation:**
- **No change to `extract.py`.** [P-none]
- **Finish `book/`'s `**Short:**` migration — 1,091 Q&As.** [P1] Repo `CLAUDE.md` explicitly
  keeps this open: book is excluded from the *factual audit* on a reasoned argument (a summary
  answers to its source, not to 2026), but records that the Short migration is "a genuinely
  separate, still-open question there". The audit exclusion does not cover it, and this is the
  measurement that shows why it matters: at 24% migrated, book is the only section where the
  MCQ has a form tell. Authoring a Short for a book Q&A does not modernize anything — it
  restates the book's own claim in one sentence — so the exclusion's reasoning does not bite.
- **Do not "fix" book by reverting its 365 Shorts** to make the pool uniformly derived. That
  would remove the tell by making every option worse.

### How to validate any future change to this code

Should anyone ever revisit it, the harness is the two throwaway scripts this audit used and
they should be re-derived rather than kept: rebuild the bank, then (1) assert every distractor
string matches an authored Short on disk, and (2) assert `correct = longest` sits within a
point or two of 25%. That second check is the real regression test — it catches any change
that gives the correct option a different provenance from its distractors, which is the only
way this design can break.

---

## 6. Execution plan

Waves are ordered by dependency, not by size. Nothing here needs `STUDY_ORDER`, the
`<!-- study-paths -->` block, or the root README **except item 7**, which is a content pass.

### Wave 1 — the delivery fix and the explanation gap (the two that change the product)

| # | Task | Files | Effort | Parallel? |
|---|---|---|---|---|
| 1 | **Make the blitz due-aware.** In `startBlitz` (2119-2155), seed `picked` from `dueReviews()` filtered to the section before the fresh/stale sample, capped at ~half the deck so new material still flows. Reuses `dueReviews()` (1418) as-is. This is the P0. | `app.js` | **M** (~20 lines + a decision on the mix ratio) | no — do first |
| 2 | **Mechanics panel in the `?` overlay.** Add a column or a second card to `toggleHelp()` (821-846) explaining: what "review" means and how the interval grows; what the gauntlet is and that it seals daily; XP = `correct*10 + streak*5`, level = `XP/250`; streak freezes. Reuses the existing modal, focus trap, and the `?`/More-sheet open paths. | `app.js` | **S** (copy + one column of markup) | yes |
| 3 | **Reader end-of-content terminal card.** Replace `appendWhatNext` 7701's bare `return` with a "You've finished <Section>" card linking to the section study tree. See §3.2. | `app.js` | **S** (~10 lines) | yes |

### Wave 2 — accessibility (independent of wave 1; can run concurrently)

| # | Task | Files | Effort | Parallel? |
|---|---|---|---|---|
| 4 | **`px` → `rem` conversion.** Fully scoped in `game-SUPPLEMENT-route-and-ux-map.md` and re-verified here. Do the **4 token definitions first** (style.css:66-69) — that re-bases 79 `var()` usages for free — then the 167 literals as `px/16`. **Exclude style.css:1943** (`font-size: 16px` on touch inputs is an absolute iOS zoom threshold, not a visual size). **Three fixed-box selectors need a coordinated edit**: `.reader-top` 2189-2191, `.nudge-dismiss` 2690-2691, and `.fc-n` 2890 (whose `line-height`/`min-height` are absolute — converting `font-size` alone clips it). **Three `clamp()` heroes** (382, 2428, 2546) need both extremes eyeballed. Media queries are unaffected (all px-viewport). | `style.css` | **L** (~170 lines, mechanical, but 7 judgment calls) | yes |
| 5 | **Touch-reachable data for the charts.** The streak heatmap (3603) and the debrief week strip (8536) carry all their values in `title=` only; the heatmap cells are also `aria-hidden`, and the debrief strip has no aggregate fallback at all. Add a tap-to-reveal caption line, and give the debrief strip the same `role="img"` aggregate the heatmap wrapper already has. The forecast (3649) and `.pathnode` (4950) are lower priority — see §1.6. | `app.js`, `style.css` | **M** | yes |
| 6 | **Ten `aria-label` attributes.** `#themeBtn` (index.html:43) plus the nine reader-toolbar buttons (app.js:7834-7853). The label strings already exist on their APK-only twins (7827-7833) and on the popover A−/A+ pair (7026-7027) — copy them. See §2. | `app.js`, `index.html` | **XS** | yes |

### Wave 3 — content (the only wave touching study files)

| # | Task | Files | Effort | Parallel? |
|---|---|---|---|---|
| 7 | **Finish `book/`'s `**Short:**` migration — 1,091 Q&As** across 69 chapter files. Removes the only measured MCQ form tell in the bank (§5). Per `CLAUDE.md`, `book/` is excluded from the *factual audit*, not from this migration, and a Short here restates the book's own claim rather than modernizing it. Batch by book; `extract.py --strict` fails on any Short outside 15-220 chars, so the build is the check. | `book/**/*.md` | **XL** (parallelizable across 6 books) | yes, 6 ways |

**No `STUDY_ORDER`, `<!-- study-paths -->`, or root README change** is needed by 1-6. Item 7
adds no files, so it needs none either — it edits existing pages in place. Run
`python3 game/extract.py --strict` from `src/main/java/com/rutik/systemdesign` after item 7.

### Wave 4 — scheduler refinements (only after item 1 lands and can be observed)

| # | Task | Files | Effort |
|---|---|---|---|
| 8 | **Interval fuzz** in `scheduleReview` (1289-1311) — a small deterministic-per-item jitter multiplier so a cohort scheduled on one day does not re-clump forever. ~3 lines. | `app.js` | **XS** |
| 9 | **A max interval cap.** ~1 line. | `app.js` | **XS** |
| 10 | **Leech handling.** Two parts: lift the `.slice(0, 7)` display cap out of `leechIds()` (3701-3706) so the drill and the count see all of them, and add a suspend-or-deprioritize action so a broken item stops consuming review slots. | `app.js` | **S** |
| 11 | **Prime-sheet backdrop fix** — route 2340 through the same body as `#primeSkip` so backdrop dismissal increments `sd_prime_opt`. One line. | `app.js` | **XS** |
| 12 | **`REVIEWS_CAP` comment fix** (1164-1172): "~8,800-question bank" → 12,820, and note that `pruneReviews` cannot evict overdue items so the cap is not a hard ceiling on the map (§1.1). | `app.js` | **XS** |

### Wave 5 — empty states (lowest value; genuinely cosmetic)

| # | Task | Files | Effort |
|---|---|---|---|
| 13 | `openTopics` zero-match state (1993-1997) — port `emptyHTML`'s pattern (4424-4447): a count and a Clear button. | `app.js` | **S** |
| 14 | `openStudySection` filter (5286-5296) — it already has the count; add the reset button only. | `app.js` | **XS** |
| 15 | `appendEvalBlock` visible feedback (7630, 7640) — replace the SR-only `announce()` and the silent `block.remove()` with a short visible line. | `app.js` | **XS** |
| 16 | Interview Silver gate (9245-9248) — say how Silver is reached instead of a bare toast. | `app.js` | **XS** |

**Explicitly NOT recommended:**
- FSRS migration (§1.3) — the preconditions do not hold and the delivery bottleneck dominates.
- Any change to the distractor pool in `extract.py` (§5) — it already does what was proposed.
- Converting `\n` to `<br/>` in Mermaid labels — repo `CLAUDE.md` records this as tested and
  not a bug; 3,381 labels; never re-flag.
- Splitting `app.js` into modules. It is 9,536 lines with no import graph and it works on two
  surfaces byte-identically outside three `IS_APK` branches. The refactor risk exceeds any
  benefit, and the APK's byte-parity requirement makes it actively dangerous.

---

## 7. Confidence and gaps

**High confidence — read in source and, where numeric, measured this pass:**
everything in §0, §2, §3, §4, and all of §5. The §5 tables come from rebuilding the live bank
and scanning it, not from reading the code alone. The §1.1 arithmetic is traced through five
call sites and each was read. In §1.3, the two claims the recommendation rests on — FSRS is
not Anki's default, and the optimizer wants a few hundred reviews — I fetched from the Anki
manual and the GitHub API myself rather than accepting the subagent's report.

**Delegation note.** Two `sonnet` subagents did the fan-out (web research; icon-button a11y
sweep). Neither's verdict was taken on trust. The a11y sweep's headline — nine unnamed reader
buttons — I confirmed by reading `app.js:7827-7853` directly, including its own calibration
control (`#muteBtn` looks unnamed and is not). The research agent contradicted the audit
brief on FSRS's default status; I treated that as a claim to check, not a result to publish,
and it held at two primary URLs.

**Medium confidence:**
- §1.1's "+7/day" is a *modelled* steady state for a user who plays one 10-question blitz and
  the gauntlet daily and never opens the review card. Real behaviour varies; the direction
  (monotonic growth) does not, because no path other than the opt-in card can clear more than
  3/day.
- The reader dead end's offline frequency (§3.2) is reasoned from `loadGraph` returning null on
  a failed fetch. I did not test it in a browser with a cold cache. **This is the one finding
  worth confirming by hand before executing item 3** — though item 3 is correct regardless of
  frequency.

**Not verified:**
- No runtime testing was done. Everything is static reading plus bank analysis. The `/verify`
  skill exists for driving the SPA in a browser and was not used — a pass with it would confirm
  §3.2 and §1.6's touch claims directly.
- Effort estimates are judgment, not measured.
- I did not audit `build_graph.py`, `build_tech.py` (1,388 lines), or `sw.js` beyond line
  counts. `build_tech.py` in particular is the second-largest program in the directory and has
  never been audited; the `tech-bank WARNING: orphan record` lines it prints on every build
  (24 records no module teaches) are unexamined here.
- The APK build (`android/`) was out of scope beyond confirming the three `IS_APK` hooks.

**Explicitly flagged as weak in the research, so nobody over-claims from §1.7:**
- The "FSRS-6 is 99.6% superior to Anki SM-2" figure is a **blog paraphrase**. The
  srs-benchmark table contains only FSRS-family rows. Do not put that number in a commit
  message.
- Roediger & Karpicke 2006's exact effect sizes and Taylor & Rohrer 2010's "77% vs 38%,
  d = 1.21" were **not extracted from the primary PDFs** (both fetches failed). The
  *directions* are solid; the numbers are not cited.
- Whether the FSRS optimizer has a browser/WASM target is **unresolved**. It does not change
  the recommendation, which is not to migrate.
- The Kang/McDermott/Roediger 2007 citation surfaced a PDF filename suggesting a different
  paper by the same lab. Confirm the title before quoting it anywhere public.
- No academic literature exists on the oversized-bank case at all. §1.7's guidance there is
  Anki community practice and is labelled as such.

**Superseded, so nobody chases them:**
- "0 literal `rem` in style.css" → there are 2, neither a `font-size` (§2).
- "328 top-level functions" → `grep -c '^function '` gives **295**.
- "Should distractors switch to `**Short:**` lines?" → they already are, at 100% outside
  `book/` (§5). Do not re-open this; re-open `book/`'s migration instead.
- **"FSRS is now the Anki default."** It is not, and I verified both halves at primary
  sources: the manual says enable it by hand, and the maintainer's own "Make FSRS the default?"
  issue is still open (§1.3). This premise came from the audit brief itself and is the single
  most important correction in this report, because several 2026 blog posts repeat it.
- "The scheduler is textbook SM-2" → it is SM-2 plus confidence weighting, an ease ceiling of
  3.0, hypercorrection, and a latency signal (§1.2). Any FSRS comparison must account for this.
- "The distractor pool is homogeneous, which might make the correct option easier to spot" →
  measured; `correct = longest` sits at 24.7% against a 25% chance baseline (§5). And the
  homogeneity is the *desirable* property here: Little et al. 2012 (§1.7) finds competitive,
  plausible distractors are where MCQ's transfer benefit comes from.

---

# ===== EXECUTION PLAN IMPLEMENTED — 2026-08-26 =====

15 of the 16 items are DONE. Build green after every step
(`scripts/build_banks.sh` exit 0, `OK: sections=16 questions=13999 graphs=16`).

## Wave 1 — the two that change the product
1. **DONE — the blitz is due-aware.** `startBlitz` now seeds the deck from `dueReviews()`
   filtered to this section's bank, capped at **half the deck** so new material still flows,
   then fills with fresh/stale as before. This was the P0: every answered question scheduled a
   review, but only the opt-in Review card and the gauntlet's 3-of-10 slots ever consumed one,
   so the map grew ~+7/day forever.
2. **DONE — "How LORA works" panel in the `?` overlay.** Four cards: what a review is and how
   the interval grows (and that answering unsure grows it slower *on purpose*), what the
   gauntlet is and that it seals daily, the XP/level arithmetic, and streaks vs freezes. The
   overlay's aria-label widened to "Shortcuts and how LORA works". **Nothing in the app had
   ever explained any of this** — the four coach marks cover none of it and `title=` tooltips
   are invisible on touch.
3. **DONE — reader terminal card.** `appendWhatNext`'s bare `return` replaced with an "End of
   this path" card linking back to the section tree. P0 rather than cosmetic because
   `loadGraph()` returns null on a failed fetch, making this a routine **offline** state in an
   app that ships as an offline APK.

## Wave 2 — accessibility
4. **DONE — `px` -> `rem`.** The 4 token definitions converted FIRST (re-basing 79 `var()`
   usages for free), then **163 literals** at `px/16`. **163 rem, 4 px remain — exactly the
   documented exclusions**, and each now carries a comment saying why:
   - `1943` iOS zoom threshold — 16 is absolute, not a visual size; as rem it reintroduces the
     zoom-on-focus bug it exists to prevent.
   - `2191` `.reader-top`, `2691` `.nudge-dismiss` — fixed circles with no overflow handling.
   - `2890` `.fc-n` — `line-height`/`min-height` are absolute, so scaling the font alone clips.
   The 3 `clamp()` heroes never matched the pattern and were untouched.
5. **PARTIAL** — item 5 (touch-reachable chart data) not done; the charts still carry values in
   `title=` only. The only item deferred from waves 1-2.
6. **DONE — 10 `aria-label`s.** 9 reader-toolbar buttons + `#themeBtn` in `index.html`, using
   the strings that already existed on their APK-only twins.

## Wave 3 — content
7. **NOT DONE — `book/`'s 1,091 `**Short:**` lines.** The one genuinely XL item, and the only
   remaining source of the measured MCQ form tell. Parallelizable 6 ways by book.

## Wave 4 — scheduler refinements
8. **DONE — interval fuzz**, +/-5% for intervals >= 7 days, **deterministic per item**: the id
   is now threaded into `scheduleReview(rv, status, today, ms, conf, id)` and hashed, since the
   id is the reviews-map KEY and was never a field on the record. No `Math.random()` — the repo
   rule is that anything surviving a reload must be reproducible.
9. **DONE — max interval capped at 365 days.**
10. **DONE — leech cap moved out of the data.** `leechIds(limit)` now returns all leeches by
    default; the `.slice(0, 7)` moved to `leechesHTML()`, its renderer. The cap belonged to the
    view, not the query — the drill and the count could never see past 7.
11. **DONE — prime-sheet backdrop** now increments `sd_prime_opt` like `#primeSkip`, so a
    backdrop-dismisser stops seeing the interstitial forever.
12. **DONE — `REVIEWS_CAP` comment** corrected: 14,005 Q&As (was "~8,800"), plus the point the
    audit made — `pruneReviews` refuses to evict anything due or overdue, so the cap is **not a
    hard ceiling**.

## Wave 5 — empty states
13. **DONE — `openTopics` zero-match** now shows a message and a Clear button, ported from
    `renderTech`'s `emptyHTML()`, plus an SR announce of the match count.
14. **DONE — `openStudySection` filter** gains the reset button it lacked (it already had a count).
15. **DONE — `appendEvalBlock`** shows a visible `.eb-note` line, not just an SR-only `announce()`.
16. **DONE — the Silver gate** now says *how*: "20 questions seen at 70% accuracy."

## Invariants re-verified after every change
```
grep -c IS_APK app.js          4     (2 functional + definition + 1 comment)
--rd-bg: #000000               1     (owner hard limit)
--rd-text: #ffffff             1     (owner hard limit)
Math.random added by me        0     (the 1 grep hit is the word inside my own comment)
build_banks.sh                 EXIT 0
```

## Explicitly NOT done, per the audit's own findings
FSRS migration (preconditions do not hold; and FSRS is **not** Anki's default — the maintainer
issue is still open). Any change to the distractor pool in `extract.py` — **it already draws
from `**Short:**` lines**, 100% in every non-book section. `\n` -> `<br/>` in Mermaid labels.
Splitting `app.js` into modules.
