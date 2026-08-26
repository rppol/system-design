# `soft_skills` — Section Plan (2026-08-25)

One section. Behavioural interviewing, leadership, influence, communication, **vocabulary**,
and soft skills generally. Not split.

Root: `src/main/java/com/rutik/systemdesign/soft_skills/`

---

## 1. The §11 decision — INVESTIGATED, decided

`build_tech.py` builds `tech_index.json` by matching:

```
TECH_HDR = re.compile(r"^##\s+(?:\d+[.)]\s*)?.*\btechnolog\w*\b.*\btools?\b.*$", re.I)
```

It keys on the heading **TEXT**, not the number: the `\d+[.)]` group is optional and
non-capturing, and the match requires BOTH a `technolog…` token AND a `tool`/`tools`
token. `## 11.` on its own is invisible to it.

**Decision: rename the §11 title to `## 11. Frameworks, Books & Further Reading`.**

- Zero code change. No new silent, load-bearing path exclusion for a future author to
  forget — the `TECH_BANK_DIR` one already carries that cost, and its own comment calls
  it a trap.
- Self-documenting: the heading itself says the contents are not tools, so an author
  cannot accidentally re-arm the trap without also renaming the heading back.
- A path exclusion would also have dropped §8 from the tradeoff digest, which is not
  wanted (see below).

**§8 `Tradeoffs` stays as-is, deliberately.** `TRADE_HDR` will index it into the
Technologies screen's per-module tradeoff digest. That digest answers "what decision does
this page make me weigh", it is rendered grouped and labelled by section, and a
soft-skills tradeoff table (sponsorship vs mentorship; sync vs async decision) is a real
answer to that question. Nothing enters the technology index itself from §8 — only §11
feeds `tech`.

**Guard rails for future authors** (recorded in `soft_skills/CLAUDE.md`):
- Never title a heading in this section with both a `technolog*` word and `tool`/`tools`.
- Never name a file `*_deep_dive.md` or `*_internals.md` — `DEEP_NAME_RE` in
  `build_tech.py` makes any such page FATAL under `--strict` unless it is hand-listed in
  `PRODUCT_DEEP_DIVES` or `CONCEPT_DEEP_DIVES`.

---

## 2. Template adaptation (3 headings retitled, numbers untouched)

| # | Repo template title | `soft_skills` title | Why |
|---|--------------------|---------------------|-----|
| 1 | Concept Overview | Concept Overview | — |
| 2 | Intuition | Intuition | — |
| 3 | Core Principles | Core Principles | — |
| 4 | Types / Architectures / Strategies | **Types / Levels / Modes** | there is no architecture; the axis is level, register or mode |
| 5 | Architecture Diagrams | **Models & Frameworks Diagrammed** | the diagrams draw frameworks (Radical Candor axes, Cynefin domains, escalation paths), not architecture |
| 6 | How It Works — Detailed Mechanics | How It Works — Detailed Mechanics | code becomes **transcripts, scripts, templates, worked numbers** |
| 7 | Real-World Examples | Real-World Examples | — |
| 8 | Tradeoffs | Tradeoffs | — |
| 9 | When to Use / When NOT to Use | When to Use / When NOT to Use | — |
| 10 | Common Pitfalls | Common Pitfalls | — |
| 11 | Technologies & Tools | **Frameworks, Books & Further Reading** | the §11 trap — see above |
| 12 | Interview Questions with Answers | Interview Questions with Answers | **UNCHANGED — load-bearing** |
| 13 | Best Practices | Best Practices | — |
| 14 | Case Study | Case Study | — |

**The broken-code-to-fix analogue.** Every module MUST carry at least one
`Weak version -> Strong version` pair: the actual sentence, message or story as a weak
engineer writes it, then the rewrite, then one line naming what changed. This is the
single quality lever that separates this section from a LinkedIn post.

---

## 3. Modules — 26, in 7 phases

Twenty-six rather than twenty because the owner named four distinct bodies of material
(behavioural interviewing, leadership, vocabulary, soft skills generally) and vocabulary
alone needs four modules to be a usable lexicon rather than a word list.

### Phase 1 — What Seniority Actually Means (3)

1. **`engineering_career_ladders`** — the published ladders (Dropbox, Rent the Runway,
   CircleCI, Square, Monzo, progression.fyi) and the big-tech grids (Google L4-L7, Meta
   E4-E7, Amazon SDE II/III/Principal, Microsoft 62-67, Apple ICT3-6, Netflix's flat
   Senior). What each level's scope statement actually says, verbatim where public. How
   to read a ladder you have been handed and locate yourself in it honestly.
2. **`scope_impact_and_ownership`** — the three axes seniority actually moves on (blast
   radius, ambiguity, time horizon), not "harder code". Ownership vs assignment. How
   impact is measured and, more importantly, written down so it survives a reorg.
   Counterfeit impact: busy work, hero work, the rewrite nobody asked for.
3. **`staff_plus_archetypes`** — Larson's four (Tech Lead, Architect, Solver, Right
   Hand) with his definitions; Reilly's three pillars and the map metaphors; "being
   glue"; the IC/management fork and why it is not a promotion decision.

### Phase 2 — Vocabulary & Precision (4) — the explicit owner ask

Every entry in every glossary is a four-part record: **term | precise definition with its
authority | a real misuse | a correct-use sentence.** No bare word lists.

4. **`precise_technical_vocabulary`** — SLI/SLO/SLA/error budget (Google SRE book, with
   the minutes-per-month arithmetic); idempotent vs safe vs pure vs deterministic (RFC
   9110); the three unrelated meanings of "consistent" (ACID C, CAP C, eventual);
   linearizability vs serializability; latency vs response time, p50/p95/p99;
   scalable/robust/resilient/reliable/available/fault-tolerant; race condition vs data
   race; concurrency vs parallelism; bug/fault/error/failure (ISTQB); risk vs issue vs
   assumption vs dependency; technical debt (Cunningham's original vs the popular
   misuse); MTTR/MTBF/MTTD; availability vs durability (S3's eleven nines is durability).
5. **`business_and_finance_vocabulary`** — gross vs contribution vs net margin; COGS and
   where cloud spend lands; runway and gross vs net burn; TCO, ROI, NPV, payback;
   opportunity cost; capex vs opex and the FinOps counter-argument; unit economics, CAC,
   LTV, ARR/MRR, churn, the Rule of 40; amortization vs depreciation; capitalising
   software development cost (ASC 350-40 / IAS 38) and why it changes how your quarter is
   booked; showback vs chargeback.
6. **`leadership_and_org_vocabulary`** — sponsorship vs mentorship vs coaching vs
   allyship; the seven levels of delegation; what a good escalation contains; alignment;
   commitment vs concurrence vs consensus vs consent, and "disagree and commit" from
   Grove to the Amazon LP; RACI / RASCI / DACI / RAPID; accountability vs responsibility;
   span of control; Conway's Law and the Inverse Conway Maneuver; Team Topologies' four
   team types and three interaction modes; psychological safety (Edmondson's definition,
   not the folk one); Cynefin's five domains under their current names; Radical Candor's
   two axes and four quadrants.
7. **`hedging_commitment_and_disagreement`** — when a hedge is dishonest and when it is
   the only honest thing available; calibrated probability language and Sherman Kent's
   estimative-probability bands; epistemic status; Paul Graham's DH0-DH6 hierarchy of
   disagreement; Rapoport's Rules; steelmanning; "strong opinions weakly held" and the
   case against it; the Chatham House Rule (singular, and usually misquoted); NVC's four
   components. Heavy on weak-to-strong sentence pairs.

### Phase 3 — Writing That Travels (5)

8. **`technical_writing_for_engineers`** — the sentence-level craft: BLUF, Minto's
   pyramid and SCQA, MECE, writing for skim (the F-pattern research), active voice,
   nominalisation, the paragraph as a claim plus its support. Mostly weak-to-strong pairs.
9. **`design_docs_rfcs_and_adrs`** — Google's design doc; Amazon's six-pager and PR/FAQ
   with the silent-reading ritual; the RFC process at Squarespace/Uber/Stripe; RFC 2119
   and 8174 keyword semantics; Nygard's ADR format, MADR, status lifecycle and
   superseding; how to get a doc reviewed rather than ignored.
10. **`executive_communication`** — the register change: engineering nouns become money,
    risk and time. The one-page brief, the exec question set ("what does it cost, what
    breaks if we don't, what do you need from me"), what to do when you are given five
    minutes and interrupted at minute one.
11. **`upward_and_async_communication`** — the weekly update that gets read; red/yellow/
    green and the watermelon failure; "no surprises"; managing up; the handbook-first
    async model (GitLab, Automattic P2, Doist); choosing chat vs doc vs meeting; writing
    a decision so a reader three timezones away can act without you.
12. **`incident_communication_and_postmortems`** — the roles (Incident Commander, Scribe,
    Deputy, SME), severity definitions, the status-page cadence and the customer-facing
    register; blameless postmortems (Allspaw/Etsy, Dekker's Just Culture, the "second
    story"); why 5 Whys is a weak instrument; named public postmortems worth imitating.

### Phase 4 — Influence, Conflict, Negotiation (5)

13. **`influence_without_authority`** — Cialdini's principles named exactly; pre-wiring a
    decision before the meeting; coalition and the order you talk to people in; making
    the other person's win visible; disagree-and-commit as a real protocol with an exit.
14. **`giving_and_receiving_feedback`** — SBI and SBI-I; Radical Candor's quadrants; why
    the feedback sandwich fails; Kluger and DeNisi's finding that feedback made
    performance *worse* in a large minority of studies; receiving feedback (the truth,
    relationship and identity triggers); soliciting it so people actually tell you.
15. **`difficult_conversations_and_conflict`** — the three conversations (What Happened,
    Feelings, Identity); Crucial Conversations' STATE and contrasting; conflict styles;
    the conversation you keep postponing and what the delay costs; scripts, verbatim.
16. **`negotiation_for_engineers`** — Fisher and Ury's four principles, BATNA, ZOPA;
    Voss's labelling, mirroring, calibrated questions, accusation audit, and "that's
    right" vs "you're right"; negotiating headcount, deadlines, on-call and compensation.
17. **`scope_prioritization_and_saying_no`** — the iron triangle; "yes, and here is what
    moves"; the counter-offer instead of the refusal; opportunity cost made concrete;
    protecting a team's focus without becoming the department of no.

### Phase 5 — Leading People and Teams (4)

18. **`mentorship_and_sponsorship`** — the difference and why sponsorship is the scarce
    one; running a useful 1:1 as an IC; growing a junior into an owner; the sponsorship
    ledger; what you owe someone you sponsored.
19. **`technical_leadership_and_execution`** — leading a project you do not manage:
    breaking work down, sequencing to reduce risk first, the status you keep, unblocking,
    delegating outcomes not tasks, and how to hand a project off.
20. **`cross_functional_collaboration`** — PM, design, data science, sales engineering,
    support, legal/compliance, finance. What each function is measured on, what they
    actually need from you, and the specific translation each one requires.
21. **`team_health_and_sustainable_pace`** — psychological safety in practice; on-call
    load, page budgets and toil; burnout as a systems failure; the retro that changes
    something; the quiet team-health signals a staff engineer should be reading.

### Phase 6 — Interviews & Career Operations (4)

22. **`behavioral_interview_systems`** — Amazon's Leadership Principles and the Bar
    Raiser loop (veto, written debrief, LP evidence); Google's hiring attributes and
    committee; Meta's leadership round; Netflix, Stripe, Airbnb. How a loop actually
    scores you, and what a bad note in a debrief looks like.
23. **`story_portfolio_and_star_frameworks`** — STAR, SBI, CARL, SOAR and which fits
    which question; building a twelve-story portfolio that covers the competency grid;
    the numbers a story needs; weak-to-strong story rewrites in full.
24. **`interviewing_as_the_interviewer`** — writing a question that discriminates;
    scoring rubrics; structured interviewing evidence; bias and calibration; the debrief
    write-up; the hiring bar and when to say no; the candidate experience you owe.
25. **`performance_reviews_and_promotions`** — the brag doc kept weekly; the self-review;
    the promotion packet (scope evidence, not activity); calibration and what happens in
    the room; being told no; the manager conversation to have twelve months early.

### Phase 7 — 2026 (1)

26. **`ai_era_engineering_leadership`** — what changed by 2026: review load and the
    reviewer bottleneck, judgment as the scarce input, AI-assisted interview cheating and
    the countermeasures, the ladders that were rewritten, and the soft skills that got
    *more* valuable rather than less.

---

## 4. Case studies — 8

Flat files under `soft_skills/case_studies/`, using the repo's 11-section principal
template with adapted §4/§7/§10 titles (numbering untouched).

| File | Concern |
|------|---------|
| `getting_a_migration_approved.md` | driving a multi-team migration through an RFC past a skeptical staff engineer |
| `the_amazon_loop.md` | a full behavioural loop end to end, LP mapping, bar-raiser debrief |
| `saying_no_to_a_vp.md` | scope negotiation against an impossible externally-announced date |
| `the_sev1_and_its_postmortem.md` | incident comms live, then the blameless write-up |
| `the_underperforming_teammate.md` | feedback to difficult conversation to escalation |
| `writing_the_promotion_packet.md` | brag doc to staff packet to calibration |
| `the_disagreement_that_went_wrong.md` | a design fight handled badly, then re-run correctly |
| `translating_a_platform_bet_to_the_cfo.md` | TCO/NPV, capex vs opex, the exec one-pager |

Plus the mandatory `case_studies/case_studies.md` 5-section index.

**No `cross_cutting/`** — it is in `CS_EXCLUDE_DIRS` and would never reach
`index.caseStudies`.

---

## 5. Tier assignment

Senior is the craft: can you write the doc, give the feedback, run the incident, answer
the question. Principal is the judgment: which ladder rung is this really, what does this
cost the business, who do I talk to first, what do I tell a team not to do.

| # | Module | senior | principal |
|---|--------|:------:|:---------:|
| 1 | engineering_career_ladders | y | y |
| 2 | scope_impact_and_ownership | | y |
| 3 | staff_plus_archetypes | | y |
| 4 | precise_technical_vocabulary | y | |
| 5 | business_and_finance_vocabulary | | y |
| 6 | leadership_and_org_vocabulary | | y |
| 7 | hedging_commitment_and_disagreement | y | y |
| 8 | technical_writing_for_engineers | y | |
| 9 | design_docs_rfcs_and_adrs | y | y |
| 10 | executive_communication | | y |
| 11 | upward_and_async_communication | y | |
| 12 | incident_communication_and_postmortems | y | y |
| 13 | influence_without_authority | | y |
| 14 | giving_and_receiving_feedback | y | |
| 15 | difficult_conversations_and_conflict | y | |
| 16 | negotiation_for_engineers | | y |
| 17 | scope_prioritization_and_saying_no | y | y |
| 18 | mentorship_and_sponsorship | y | y |
| 19 | technical_leadership_and_execution | y | |
| 20 | cross_functional_collaboration | y | |
| 21 | team_health_and_sustainable_pace | | y |
| 22 | behavioral_interview_systems | y | |
| 23 | story_portfolio_and_star_frameworks | y | |
| 24 | interviewing_as_the_interviewer | | y |
| 25 | performance_reviews_and_promotions | y | y |
| 26 | ai_era_engineering_leadership | y | y |

Senior 17, Principal 17, overlap 8. **Nine of the seventeen principal modules (53%) are
material senior never sees** — the repo's stated property holds, and principal is not
"senior plus extras".

Case-study tiers: `the_amazon_loop`, `the_sev1_and_its_postmortem`,
`the_underperforming_teammate`, `writing_the_promotion_packet` are senior;
`getting_a_migration_approved`, `saying_no_to_a_vp`,
`translating_a_platform_bet_to_the_cfo` are principal;
`the_disagreement_that_went_wrong` is both.

---

## 6. Learning-path order and rationale

The order is the order the material becomes usable, not the order it becomes interesting.

1. **Ladder first** because every later module answers "at what level". Reading the
   feedback module without knowing whether you are being asked for senior craft or
   principal judgment produces the generic advice this section exists to avoid.
2. **Vocabulary second** because it is the dependency of everything after it. The
   executive-communication module says "lead with the COGS impact"; that sentence is
   noise until §5 has defined COGS. The writing modules assume you can say `SLO` and
   mean it.
3. **Writing third** because written artifacts are how influence actually travels at
   staff+, and because the conflict modules keep referring to "the doc you wrote".
4. **Influence and conflict fourth** — they consume the vocabulary and the writing.
5. **Leading people fifth** — the same skills applied to a person rather than a decision.
6. **Interviews sixth** deliberately, not first. A behavioural answer is a compressed
   report of everything in phases 1-5; practising the format before you have the content
   is exactly how candidates learn to produce polished, empty STAR answers.
7. **The AI-era module last** because it is the delta on all of it.

---

## 7. Quality bar per module

- 700-1100 lines
- 15+ Q&As under `## 12. Interview Questions with Answers`, every one `**Q: …?**` with an
  authored `**Short:**` line (15-220 chars) as the first non-blank line of the answer
- 2+ Mermaid diagrams, every node coloured with the One-Dark `classDef` palette,
  `flowchart LR` by default, rounded shapes, `<br/>` wraps
- at least one `Weak version -> Strong version` pair
- named companies, named books with authors, real numbers, verbatim scripts
- no emojis; the plain check and cross marks only

## 8. Build sequence

1. Write 26 module pages + 8 case studies + `case_studies/case_studies.md`
2. `soft_skills/README.md` with the `<!-- study-paths -->` block and empty
   `<!-- study-path-table senior -->` / `principal` marker pairs
3. `soft_skills/CLAUDE.md`
4. `game/app.js`: `SECTION_LABELS.soft_skills = "Soft Skills & Leadership"`;
   `SECTION_IDENTITY.soft_skills = { accent: "#eb7099", glyph: "“" }` at hue 340,
   appended after `technologies` (320) since the map runs 355 down to 320;
   `STUDY_ORDER.soft_skills` with all 26 in the order above
5. root `README.md` + root `CLAUDE.md`
6. `python3 game/extract.py --write-paths`
7. `python3 game/extract.py --strict`
8. `node scripts/check_render.mjs` on every new file
9. `bash scripts/build_banks.sh` — must exit 0
