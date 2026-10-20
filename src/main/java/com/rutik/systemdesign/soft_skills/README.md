# Soft Skills & Leadership — Senior to Principal Engineer Guide

Behavioural interviewing, leadership, influence, communication, **vocabulary**, and soft
skills generally — for engineers who already know how to build the thing and now have to
get it approved, staffed, adopted and maintained by people who do not report to them.

> **No runtime application** — all content is Markdown. Where other sections carry code,
> this one carries transcripts, scripts, templates and worked numbers.

---

## Intuition

> **One-line analogy**: The technical sections teach you to build a bridge. This one
> teaches you to get the bridge funded, get the other team to route traffic over it, get
> the person who wanted a tunnel to say yes, and write it down so the next engineer knows
> why it is a bridge.

**Mental model**: Every engineering decision above a certain size is made twice. Once
technically — which design is correct — and once socially — which design gets built. The
second decision is made in documents, one-on-ones, review comments, meetings you were not
in, and the exact words you chose. Engineers who are excellent at the first decision and
illiterate in the second stall at Senior and cannot work out why.

**Why it matters**: Read any published engineering ladder (module 1) and the technical
requirements barely move between Senior and Staff. What moves is scope, ambiguity and
influence — three words that all cash out as communication. The behavioural loop at every
large company exists to test exactly this, and it is the round strong technical candidates
most often fail.

**Key insight**: None of this is a personality trait. It is a craft with named
frameworks, published research, and a right and wrong way to phrase a sentence. That is
why this section is full of `Weak version -> Strong version` pairs rather than
encouragement: the difference between a sentence that gets ignored and one that gets a
decision is usually four concrete words.

---

## 1. Section Overview

This section covers:

- **What seniority actually means** — the published career ladders (Dropbox, Rent the
  Runway, CircleCI, Square, Monzo) and the big-tech grids (Google, Meta, Amazon,
  Microsoft, Apple, Netflix); the three axes every ladder measures whatever words it uses;
  scope, blast radius and ambiguity; counterfeit impact; the staff-plus archetypes from
  Will Larson and Tanya Reilly.
- **Vocabulary and precision** — four working lexicons. The precise-technical register
  (SLI/SLO/SLA, idempotent, the three unrelated meanings of "consistent", race condition
  vs data race, technical debt as Ward Cunningham actually defined it); the
  business/finance register you need with executives (COGS, margin, TCO, NPV, capex vs
  opex, the Rule of 40, software-capitalisation rules); the leadership/org register
  (sponsorship vs mentorship, RACI and RAPID, Team Topologies, Conway's Law, psychological
  safety, Cynefin, Radical Candor); and the language of hedging, commitment and
  disagreement.
- **Writing that travels** — sentence craft (BLUF, Minto's pyramid, SCQA, MECE, writing
  for skim); design docs, RFCs and ADRs (Google's design doc, Amazon's six-pager and
  PR/FAQ, RFC 2119 keywords, Nygard's ADR); executive communication; upward and async
  communication; incident communication and blameless postmortems.
- **Influence, conflict and negotiation** — influence without authority; giving and
  receiving feedback (SBI, Radical Candor, and the research showing feedback often makes
  performance worse); difficult conversations; negotiation (Fisher and Ury, Voss); scope
  prioritisation and saying no.
- **Leading people and teams** — mentorship and sponsorship; leading a project you do not
  manage; cross-functional work with PM, design, data, sales, support, legal and finance;
  team health, on-call load and sustainable pace.
- **Interviews and career operations** — how behavioural loops actually score you (Amazon
  Leadership Principles and the Bar Raiser, Google's hiring attributes and committee,
  Meta's leadership round); STAR/SBI/CARL and building a story portfolio; interviewing as
  the interviewer; performance reviews, brag docs and promotion packets.
- **2026** — what AI changed about engineering leadership, review load, and the
  behavioural interview itself.

---

## 2. Scope & Non-Overlap Boundary

This section owns the human and organisational side. Where a topic has a deep technical
treatment elsewhere in the repo, this section teaches the **communication and decision**
layer and links out rather than re-teaching the mechanism.

| Already covered in... | Soft Skills does NOT re-teach | Soft Skills DOES cover |
|-----------------------|-------------------------------|------------------------|
| [`devops/incident_management_and_oncall`](../devops/incident_management_and_oncall/incident_management_and_oncall.md) | paging systems, runbooks, error-budget policy mechanics | the incident channel's words, the status-page register, the blameless postmortem as a cultural artifact, escalating to an executive mid-incident |
| [`backend/observability_and_monitoring`](../backend/observability_and_monitoring/observability_and_monitoring.md) | instrumenting an SLI, computing an SLO | saying "SLO" and meaning it; the SLA-vs-SLO confusion in a customer conversation |
| [`hld/`](../hld/README.md) | the architecture itself | getting the architecture approved — the design doc, the RFC, the alternatives-considered section, the skeptical reviewer |
| [`cs_fundamentals/dsa_patterns/interview_execution_playbook.md`](../cs_fundamentals/dsa_patterns/interview_execution_playbook.md) | the coding round — UMPIRE, thinking aloud on an algorithm | the behavioural round — the story portfolio, the LP mapping, the follow-up probe |
| [`devops/cloud_cost_optimization_finops`](../devops/cloud_cost_optimization_finops/cloud_cost_optimization_finops.md) | tagging, rightsizing, committed-use discounts | the finance vocabulary itself, and presenting a cost case to someone who owns the budget |

**Soft Skills owns**: career ladders and level scope, the four professional vocabularies,
written-artifact craft, influence and negotiation, feedback and conflict, mentorship and
sponsorship, behavioural interviewing on both sides of the table, and promotion mechanics.

---

## 3. Module Table

| # | Module | Phase | Key Topics |
|---|--------|-------|-----------|
| 1 | [engineering_career_ladders](engineering_career_ladders/engineering_career_ladders.md) | 1 | Dropbox / Rent the Runway / CircleCI / Square / Monzo ladders; Google L3-L8, Meta E3-E8, Amazon SDE I-Principal, Microsoft bands, Apple ICT, Netflix's flat model; scope / ambiguity / autonomy as the three axes; level inflation; the dual ladder |
| 2 | [scope_impact_and_ownership](scope_impact_and_ownership/scope_impact_and_ownership.md) | 1 | blast radius, time horizon, ambiguity; ownership vs assignment vs accountability; counterfeit impact; leading vs lagging impact measures; the brag doc; additive vs multiplier impact and the glue-work trap |
| 3 | [staff_plus_archetypes](staff_plus_archetypes/staff_plus_archetypes.md) | 1 | Larson's Tech Lead / Architect / Solver / Right Hand; Reilly's three pillars and map metaphors; "Being Glue"; the IC-management fork; archetype anti-patterns |
| 4 | [precise_technical_vocabulary](precise_technical_vocabulary/precise_technical_vocabulary.md) | 2 | SLI/SLO/SLA/error budget; idempotent vs safe vs pure; the three "consistent"s; linearizability vs serializability; race condition vs data race; bug/fault/error/failure; risk vs issue; technical debt as coined; MTTR family; availability vs durability |
| 5 | [business_and_finance_vocabulary](business_and_finance_vocabulary/business_and_finance_vocabulary.md) | 2 | gross / contribution / net margin; COGS and cloud spend; runway and burn; TCO, ROI, NPV, payback; capex vs opex; unit economics, CAC, LTV, ARR, churn, Rule of 40; software capitalisation; showback vs chargeback |
| 6 | [leadership_and_org_vocabulary](leadership_and_org_vocabulary/leadership_and_org_vocabulary.md) | 2 | sponsorship vs mentorship vs coaching; delegation levels; escalation anatomy; commitment vs concurrence vs consensus; RACI / DACI / RAPID; Conway's Law and the inverse manoeuvre; Team Topologies; psychological safety; Cynefin; Radical Candor |
| 7 | [hedging_commitment_and_disagreement](hedging_commitment_and_disagreement/hedging_commitment_and_disagreement.md) | 2 | when a hedge is dishonest and when it is the only honest option; calibrated probability language; Sherman Kent's estimative bands; Paul Graham's DH0-DH6; Rapoport's Rules; steelmanning; "strong opinions weakly held" and its critics; the Chatham House Rule |
| 8 | [technical_writing_for_engineers](technical_writing_for_engineers/technical_writing_for_engineers.md) | 3 | BLUF; Minto's pyramid, SCQA, MECE; the F-pattern and writing for skim; passive voice, nominalisation, hedges, vague quantifiers, the buried ask; the revision pass |
| 9 | [design_docs_rfcs_and_adrs](design_docs_rfcs_and_adrs/design_docs_rfcs_and_adrs.md) | 3 | Google's design doc and its non-goals section; Amazon's six-pager, PR/FAQ and the silent-reading ritual; RFC processes; RFC 2119 / 8174 keywords; Nygard's ADR and MADR; getting a doc actually reviewed |
| 10 | [executive_communication](executive_communication/executive_communication.md) | 3 | the engineering-to-business translation table; the exec question set; the one-page brief; being interrupted at minute one; escalation; delivering bad news early; saying "I don't know" correctly |
| 11 | [upward_and_async_communication](upward_and_async_communication/upward_and_async_communication.md) | 3 | the weekly update that gets read; red/yellow/green and the watermelon; "no surprises"; managing up; handbook-first async (GitLab, P2, Doist); chat vs doc vs meeting |
| 12 | [incident_communication_and_postmortems](incident_communication_and_postmortems/incident_communication_and_postmortems.md) | 3 | Incident Commander / Scribe / Deputy / SME; severity definitions; status-page cadence and register; blameless postmortems, Just Culture and the second story; why 5 Whys is weak; named public postmortems |
| 13 | [influence_without_authority](influence_without_authority/influence_without_authority.md) | 4 | Cialdini's principles; pre-wiring a decision; coalition and talk order; making the other person's win visible; disagree-and-commit as a protocol with an exit |
| 14 | [giving_and_receiving_feedback](giving_and_receiving_feedback/giving_and_receiving_feedback.md) | 4 | SBI and SBI-I; Radical Candor's quadrants; why the feedback sandwich fails; the Kluger and DeNisi finding; the truth / relationship / identity triggers; soliciting feedback people will actually give |
| 15 | [difficult_conversations_and_conflict](difficult_conversations_and_conflict/difficult_conversations_and_conflict.md) | 4 | the three conversations (What Happened, Feelings, Identity); STATE and contrasting; conflict styles; the postponed conversation and what the delay costs; verbatim scripts |
| 16 | [negotiation_for_engineers](negotiation_for_engineers/negotiation_for_engineers.md) | 4 | Fisher and Ury's four principles, BATNA, ZOPA; Voss's labelling, mirroring, calibrated questions, accusation audit; negotiating headcount, deadlines, on-call and compensation |
| 17 | [scope_prioritization_and_saying_no](scope_prioritization_and_saying_no/scope_prioritization_and_saying_no.md) | 4 | the iron triangle; "yes, and here is what moves"; the counter-offer instead of the refusal; opportunity cost made concrete; protecting focus without becoming the department of no |
| 18 | [mentorship_and_sponsorship](mentorship_and_sponsorship/mentorship_and_sponsorship.md) | 5 | the distinction and why sponsorship is the scarce one; the IC's 1:1; growing a junior into an owner; the sponsorship ledger; what you owe someone you sponsored |
| 19 | [technical_leadership_and_execution](technical_leadership_and_execution/technical_leadership_and_execution.md) | 5 | leading a project you do not manage; breaking work down; sequencing to retire risk first; unblocking; delegating outcomes not tasks; handing a project off |
| 20 | [technical_strategy_and_vision](technical_strategy_and_vision/technical_strategy_and_vision.md) | 5 | Rumelt's kernel (diagnosis, guiding policy, coherent actions); Larson's write-five-design-docs-then-synthesise method; strategy vs vision vs design doc vs roadmap; getting a strategy adopted; refreshing and retiring one |
| 21 | [cross_functional_collaboration](cross_functional_collaboration/cross_functional_collaboration.md) | 5 | what PM, design, data science, sales engineering, support, legal and finance are each measured on, what they need from you, and the specific translation each requires |
| 22 | [team_health_and_sustainable_pace](team_health_and_sustainable_pace/team_health_and_sustainable_pace.md) | 5 | psychological safety in practice; on-call load, page budgets and toil; burnout as a systems failure; the retro that changes something; the quiet signals |
| 23 | [behavioral_interview_systems](behavioral_interview_systems/behavioral_interview_systems.md) | 6 | Amazon's Leadership Principles and the Bar Raiser loop; Google's hiring attributes and committee; Meta's leadership round; Netflix, Stripe, Airbnb; how a loop actually scores you |
| 24 | [story_portfolio_and_star_frameworks](story_portfolio_and_star_frameworks/story_portfolio_and_star_frameworks.md) | 6 | STAR, SBI, CARL, SOAR and which fits which question; the twelve-story portfolio against a competency grid; the numbers a story needs; full weak-to-strong story rewrites |
| 25 | [interviewing_as_the_interviewer](interviewing_as_the_interviewer/interviewing_as_the_interviewer.md) | 6 | writing a question that discriminates; scoring rubrics; structured-interviewing evidence; bias and calibration; the debrief write-up; the hiring bar and when to say no |
| 26 | [performance_reviews_and_promotions](performance_reviews_and_promotions/performance_reviews_and_promotions.md) | 6 | the weekly brag doc; the self-review; the promotion packet as scope evidence not activity; calibration and what happens in the room; being told no; the conversation to have twelve months early |
| 27 | [ai_era_engineering_leadership](ai_era_engineering_leadership/ai_era_engineering_leadership.md) | 7 | review load and the reviewer bottleneck; judgment as the scarce input; AI-assisted interview cheating and the countermeasures; rewritten ladders; the soft skills that got more valuable |

---

## 4. Full Learning Path — 7 Phases

The order is the order the material becomes **usable**, not the order it becomes
interesting.

```mermaid
flowchart LR
    classDef p1 fill:#e06c75,stroke:#c0392b,color:#fff
    classDef p2 fill:#e5c07b,stroke:#f39c12,color:#1a1a1a
    classDef p3 fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef p4 fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef p5 fill:#61afef,stroke:#2e86c1,color:#1a1a1a
    classDef p6 fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef p7 fill:#eb7099,stroke:#c0392b,color:#fff

    P1(["1 - What Seniority<br/>Actually Means"]) --> P2(["2 - Vocabulary<br/>and Precision"])
    P2 --> P3(["3 - Writing<br/>That Travels"])
    P3 --> P4(["4 - Influence, Conflict,<br/>Negotiation"])
    P4 --> P5(["5 - Leading People<br/>and Teams"])
    P5 --> P6(["6 - Interviews and<br/>Career Operations"])
    P6 --> P7(["7 - The 2026 Delta"])

    class P1 p1
    class P2 p2
    class P3 p3
    class P4 p4
    class P5 p5
    class P6 p6
    class P7 p7
```

Phases run left to right; each one consumes the vocabulary and artifacts of the one before it.

1. **Ladder first** because every later module implicitly answers "at what level". Read
   the feedback module without knowing whether senior craft or principal judgment is being
   asked of you and you get generic advice, which is exactly what this section exists to
   avoid.
2. **Vocabulary second** because it is the dependency of everything after it. The
   executive-communication module tells you to lead with the COGS impact; that sentence is
   noise until module 5 has defined COGS. The writing modules assume you can say `SLO`
   and mean it.
3. **Writing third** because written artifacts are how influence actually travels above
   Senior, and because the conflict modules keep referring back to "the doc you wrote".
4. **Influence and conflict fourth** — they consume the vocabulary and the writing.
5. **Leading people fifth** — the same skills pointed at a person rather than a decision.
6. **Interviews sixth, deliberately not first.** A behavioural answer is a compressed
   report of everything in phases 1-5. Practising the format before you have the content
   is precisely how candidates learn to produce polished, empty STAR answers.
7. **The AI-era module last** because it is the delta on all of it.

---

<!-- study-paths
     THE source of truth for this section's file inventory and study-tier membership.
     Every module, every file it owns, and every case study is listed here with the tiers
     it belongs to; `-` means Full path only. Content files carry NO structural metadata.
     Order is never declared here: it comes from STUDY_ORDER in game/app.js.
     The tier TABLES lower in this file are generated from this block by
     `python3 game/extract.py --write-paths`. A file on disk that is missing from this
     block, or listed here and absent from disk, FAILS `extract.py --strict`.
engineering_career_ladders
  engineering_career_ladders.md  senior, principal
scope_impact_and_ownership
  scope_impact_and_ownership.md  principal
staff_plus_archetypes
  staff_plus_archetypes.md  principal
precise_technical_vocabulary
  precise_technical_vocabulary.md  senior
business_and_finance_vocabulary
  business_and_finance_vocabulary.md  principal
leadership_and_org_vocabulary
  leadership_and_org_vocabulary.md  principal
hedging_commitment_and_disagreement
  hedging_commitment_and_disagreement.md  senior, principal
technical_writing_for_engineers
  technical_writing_for_engineers.md  senior
design_docs_rfcs_and_adrs
  design_docs_rfcs_and_adrs.md  senior, principal
executive_communication
  executive_communication.md  principal
upward_and_async_communication
  upward_and_async_communication.md  senior
incident_communication_and_postmortems
  incident_communication_and_postmortems.md  senior, principal
influence_without_authority
  influence_without_authority.md  principal
giving_and_receiving_feedback
  giving_and_receiving_feedback.md  senior
difficult_conversations_and_conflict
  difficult_conversations_and_conflict.md  senior
negotiation_for_engineers
  negotiation_for_engineers.md  principal
scope_prioritization_and_saying_no
  scope_prioritization_and_saying_no.md  senior, principal
mentorship_and_sponsorship
  mentorship_and_sponsorship.md  senior, principal
technical_leadership_and_execution
  technical_leadership_and_execution.md  senior
cross_functional_collaboration
  cross_functional_collaboration.md  senior
team_health_and_sustainable_pace
  team_health_and_sustainable_pace.md  principal
behavioral_interview_systems
  behavioral_interview_systems.md  senior
story_portfolio_and_star_frameworks
  story_portfolio_and_star_frameworks.md  senior
interviewing_as_the_interviewer
  interviewing_as_the_interviewer.md  principal
performance_reviews_and_promotions
  performance_reviews_and_promotions.md  senior, principal
ai_era_engineering_leadership
  ai_era_engineering_leadership.md  senior, principal
technical_strategy_and_vision
  technical_strategy_and_vision.md  senior, principal
case_studies
  getting_a_migration_approved.md          principal
  saying_no_to_a_vp.md                     principal
  the_amazon_loop.md                       senior
  the_disagreement_that_went_wrong.md      senior, principal
  the_sev1_and_its_postmortem.md           senior
  the_underperforming_teammate.md          senior
  translating_a_platform_bet_to_the_cfo.md  principal
  writing_the_promotion_packet.md          senior
-->
## Learning Paths

Two curated tiers sit over the Full Path above. They are **different cuts, not nested
depths**.

**Senior is the craft.** Can you write the design doc, give the feedback, run the incident
channel, answer the behavioural question, keep the brag doc. Seventeen modules.

**Principal is the judgment.** Which rung is this really, what does it cost the business,
who do you talk to first and in what order, what do you tell a team *not* to do. Also
seventeen modules — and **nine of them are material senior never sees**: the whole
business-and-finance register, the org vocabulary, scope and archetypes, influence,
negotiation, executive communication, team health, and interviewing from the other side of
the table.

Eight modules are in both, because they are genuinely two different readings of the same
topic: the ladder, the language of commitment, design docs, incident work, saying no,
sponsorship, promotions, and the 2026 delta.

<!-- study-path-table senior -->
### Senior Path (18 modules)

| # | Module | Files |
|---|--------|-------|
| 1 | [engineering_career_ladders](engineering_career_ladders/engineering_career_ladders.md) | module page only |
| 4 | [precise_technical_vocabulary](precise_technical_vocabulary/precise_technical_vocabulary.md) | module page only |
| 7 | [hedging_commitment_and_disagreement](hedging_commitment_and_disagreement/hedging_commitment_and_disagreement.md) | module page only |
| 8 | [technical_writing_for_engineers](technical_writing_for_engineers/technical_writing_for_engineers.md) | module page only |
| 9 | [design_docs_rfcs_and_adrs](design_docs_rfcs_and_adrs/design_docs_rfcs_and_adrs.md) | module page only |
| 11 | [upward_and_async_communication](upward_and_async_communication/upward_and_async_communication.md) | module page only |
| 12 | [incident_communication_and_postmortems](incident_communication_and_postmortems/incident_communication_and_postmortems.md) | module page only |
| 14 | [giving_and_receiving_feedback](giving_and_receiving_feedback/giving_and_receiving_feedback.md) | module page only |
| 15 | [difficult_conversations_and_conflict](difficult_conversations_and_conflict/difficult_conversations_and_conflict.md) | module page only |
| 17 | [scope_prioritization_and_saying_no](scope_prioritization_and_saying_no/scope_prioritization_and_saying_no.md) | module page only |
| 18 | [mentorship_and_sponsorship](mentorship_and_sponsorship/mentorship_and_sponsorship.md) | module page only |
| 19 | [technical_leadership_and_execution](technical_leadership_and_execution/technical_leadership_and_execution.md) | module page only |
| 20 | [technical_strategy_and_vision](technical_strategy_and_vision/technical_strategy_and_vision.md) | module page only |
| 21 | [cross_functional_collaboration](cross_functional_collaboration/cross_functional_collaboration.md) | module page only |
| 23 | [behavioral_interview_systems](behavioral_interview_systems/behavioral_interview_systems.md) | module page only |
| 24 | [story_portfolio_and_star_frameworks](story_portfolio_and_star_frameworks/story_portfolio_and_star_frameworks.md) | module page only |
| 26 | [performance_reviews_and_promotions](performance_reviews_and_promotions/performance_reviews_and_promotions.md) | module page only |
| 27 | [ai_era_engineering_leadership](ai_era_engineering_leadership/ai_era_engineering_leadership.md) | module page only |

**Not in this path** (9 of 27, Full Path only): `scope_impact_and_ownership`, `staff_plus_archetypes`, `business_and_finance_vocabulary`, `leadership_and_org_vocabulary`, `executive_communication`, `influence_without_authority`, `negotiation_for_engineers`, `team_health_and_sustainable_pace`, `interviewing_as_the_interviewer`
<!-- /study-path-table -->

<!-- study-path-table principal -->
### Principal Path (18 modules)

| # | Module | Files |
|---|--------|-------|
| 1 | [engineering_career_ladders](engineering_career_ladders/engineering_career_ladders.md) | module page only |
| 2 | [scope_impact_and_ownership](scope_impact_and_ownership/scope_impact_and_ownership.md) | module page only |
| 3 | [staff_plus_archetypes](staff_plus_archetypes/staff_plus_archetypes.md) | module page only |
| 5 | [business_and_finance_vocabulary](business_and_finance_vocabulary/business_and_finance_vocabulary.md) | module page only |
| 6 | [leadership_and_org_vocabulary](leadership_and_org_vocabulary/leadership_and_org_vocabulary.md) | module page only |
| 7 | [hedging_commitment_and_disagreement](hedging_commitment_and_disagreement/hedging_commitment_and_disagreement.md) | module page only |
| 9 | [design_docs_rfcs_and_adrs](design_docs_rfcs_and_adrs/design_docs_rfcs_and_adrs.md) | module page only |
| 10 | [executive_communication](executive_communication/executive_communication.md) | module page only |
| 12 | [incident_communication_and_postmortems](incident_communication_and_postmortems/incident_communication_and_postmortems.md) | module page only |
| 13 | [influence_without_authority](influence_without_authority/influence_without_authority.md) | module page only |
| 16 | [negotiation_for_engineers](negotiation_for_engineers/negotiation_for_engineers.md) | module page only |
| 17 | [scope_prioritization_and_saying_no](scope_prioritization_and_saying_no/scope_prioritization_and_saying_no.md) | module page only |
| 18 | [mentorship_and_sponsorship](mentorship_and_sponsorship/mentorship_and_sponsorship.md) | module page only |
| 20 | [technical_strategy_and_vision](technical_strategy_and_vision/technical_strategy_and_vision.md) | module page only |
| 22 | [team_health_and_sustainable_pace](team_health_and_sustainable_pace/team_health_and_sustainable_pace.md) | module page only |
| 25 | [interviewing_as_the_interviewer](interviewing_as_the_interviewer/interviewing_as_the_interviewer.md) | module page only |
| 26 | [performance_reviews_and_promotions](performance_reviews_and_promotions/performance_reviews_and_promotions.md) | module page only |
| 27 | [ai_era_engineering_leadership](ai_era_engineering_leadership/ai_era_engineering_leadership.md) | module page only |

**Not in this path** (9 of 27, Full Path only): `precise_technical_vocabulary`, `technical_writing_for_engineers`, `upward_and_async_communication`, `giving_and_receiving_feedback`, `difficult_conversations_and_conflict`, `technical_leadership_and_execution`, `cross_functional_collaboration`, `behavioral_interview_systems`, `story_portfolio_and_star_frameworks`
<!-- /study-path-table -->

---

## 5. Case Studies — 8

Full index with dependency map and interview shortcuts:
[case_studies/case_studies.md](case_studies/case_studies.md).

| Case Study | Concern |
|-----------|---------|
| [getting_a_migration_approved](case_studies/getting_a_migration_approved.md) | driving a multi-team migration through an RFC past a skeptical staff engineer |
| [the_amazon_loop](case_studies/the_amazon_loop.md) | a full behavioural loop end to end, LP mapping, bar-raiser debrief |
| [saying_no_to_a_vp](case_studies/saying_no_to_a_vp.md) | scope negotiation against an impossible externally-announced date |
| [the_sev1_and_its_postmortem](case_studies/the_sev1_and_its_postmortem.md) | incident comms live, then the blameless write-up |
| [the_underperforming_teammate](case_studies/the_underperforming_teammate.md) | feedback to difficult conversation to escalation |
| [writing_the_promotion_packet](case_studies/writing_the_promotion_packet.md) | brag doc to staff packet to calibration |
| [the_disagreement_that_went_wrong](case_studies/the_disagreement_that_went_wrong.md) | a design fight handled badly, then re-run correctly |
| [translating_a_platform_bet_to_the_cfo](case_studies/translating_a_platform_bet_to_the_cfo.md) | TCO and NPV, capex vs opex, the exec one-pager |

---

## 6. Knowledge-Question Map

| If you are asked... | Read |
|---------------------|------|
| "Tell me about a time you disagreed with your manager" | [story_portfolio_and_star_frameworks](story_portfolio_and_star_frameworks/story_portfolio_and_star_frameworks.md), [difficult_conversations_and_conflict](difficult_conversations_and_conflict/difficult_conversations_and_conflict.md) |
| "How do you decide what not to work on?" | [scope_prioritization_and_saying_no](scope_prioritization_and_saying_no/scope_prioritization_and_saying_no.md), [scope_impact_and_ownership](scope_impact_and_ownership/scope_impact_and_ownership.md) |
| "What is the difference between an SLO and an SLA?" | [precise_technical_vocabulary](precise_technical_vocabulary/precise_technical_vocabulary.md) |
| "Justify this migration to the CFO" | [business_and_finance_vocabulary](business_and_finance_vocabulary/business_and_finance_vocabulary.md), [executive_communication](executive_communication/executive_communication.md) |
| "How would you get another team to adopt your platform?" | [influence_without_authority](influence_without_authority/influence_without_authority.md), [design_docs_rfcs_and_adrs](design_docs_rfcs_and_adrs/design_docs_rfcs_and_adrs.md) |
| "Walk me through a postmortem you wrote" | [incident_communication_and_postmortems](incident_communication_and_postmortems/incident_communication_and_postmortems.md) |
| "What does Staff mean at your company?" | [engineering_career_ladders](engineering_career_ladders/engineering_career_ladders.md), [staff_plus_archetypes](staff_plus_archetypes/staff_plus_archetypes.md) |
| "Tell me about a time you gave someone hard feedback" | [giving_and_receiving_feedback](giving_and_receiving_feedback/giving_and_receiving_feedback.md) |
| "How do you work with product?" | [cross_functional_collaboration](cross_functional_collaboration/cross_functional_collaboration.md) |
| "You are the interviewer — write a behavioural question" | [interviewing_as_the_interviewer](interviewing_as_the_interviewer/interviewing_as_the_interviewer.md) |

---

## 7. Suggested Study Plan — 6 Weeks

| Week | Focus | Modules |
|------|-------|---------|
| 1 | Locate yourself | 1-3 |
| 2 | Vocabulary | 4-7 |
| 3 | Writing | 8-10 |
| 4 | Communication in flight | 11-12, plus the incident case study |
| 5 | Influence and conflict | 13-17 |
| 6 | People, interviews, 2026 | 18-27, plus the loop and promo-packet case studies |

Do the modules in path order; do one case study at the end of each week from week 3 on.
