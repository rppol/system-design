# Soft Skills Case Studies — Learning Path

Eight worked scenarios. Each one is a single situation followed end to end, with the
actual documents, messages and transcripts the participants would have written — including
the versions that did not work.

These are **not** quiz material by design: case-study Q&As are excluded from the game's
MCQ bank repo-wide, because a discussion prompt tied to one scenario does not stand alone
as a multiple-choice item. Read them as walkthroughs, not as drills.

---

## 1. Quick Start — read these three first

| # | File | Why first |
|---|------|-----------|
| 1 | [the_amazon_loop.md](the_amazon_loop.md) | It is the single highest-leverage read in the section for anyone interviewing in the next month, and it makes the abstract competency language in the modules concrete: you watch the same story score well and score badly. |
| 2 | [the_sev1_and_its_postmortem.md](the_sev1_and_its_postmortem.md) | Incident communication is the one soft skill with a hard deadline. This is the only case study where the words are written under time pressure, and it shows the exact register shift between the internal channel and the status page. |
| 3 | [getting_a_migration_approved.md](getting_a_migration_approved.md) | The canonical staff-engineer problem — a technically correct plan that nobody has agreed to yet — and the one that pulls in the most other modules at once: the design doc, the pre-wire, the skeptical reviewer, and disagree-and-commit. |

---

## 2. Full Learning Path

### Phase A — Getting a decision made

| File | Concern | Summary |
|------|---------|---------|
| [getting_a_migration_approved.md](getting_a_migration_approved.md) | **Written influence across teams** | A multi-team migration that is technically obvious and organisationally stuck. Follows the RFC from a first draft that gets ignored, through the pre-wire conversation with the staff engineer who will object, to the version that gets approved — with both drafts shown in full and the reviewer's actual comments. |
| [the_disagreement_that_went_wrong.md](the_disagreement_that_went_wrong.md) | **Conflict in a technical review** | The same design disagreement run twice. The first run escalates into a two-week stalemate and a decision made by exhaustion; the second reaches the same technical conclusion in one meeting. The delta is entirely in the language, the order people were spoken to, and where the disagreement was allowed to happen. |

### Phase B — Talking to people who control the budget

| File | Concern | Summary |
|------|---------|---------|
| [saying_no_to_a_vp.md](saying_no_to_a_vp.md) | **Scope negotiation under a committed date** | A date has been announced to customers and the scope does not fit. Covers the counter-offer instead of the refusal, making opportunity cost concrete, and what to do when the answer is still no. |
| [translating_a_platform_bet_to_the_cfo.md](translating_a_platform_bet_to_the_cfo.md) | **The business register** | An eighteen-month platform investment presented to someone who thinks in TCO, NPV and payback period. Shows the engineering-language version, the finance-language version, and the arithmetic behind each number. |

### Phase C — Conversations about people

| File | Concern | Summary |
|------|---------|---------|
| [the_underperforming_teammate.md](the_underperforming_teammate.md) | **Feedback to escalation** | A colleague whose work is quietly costing the team, and you are not their manager. Follows the SBI conversation, the second conversation when nothing changed, and the escalation — including the version of the escalation that would have been unfair. |
| [the_sev1_and_its_postmortem.md](the_sev1_and_its_postmortem.md) | **Communication under time pressure, then blamelessly** | A customer-visible outage from the first page to the published postmortem. Internal channel, status-page cadence, executive escalation, and the write-up rewritten to remove counterfactual language. |

### Phase D — Your own career

| File | Concern | Summary |
|------|---------|---------|
| [the_amazon_loop.md](the_amazon_loop.md) | **The behavioural interview from both sides** | A full loop for a senior role: the questions asked, the answers given, the follow-up probes, and the written debrief each interviewer files — including what the Bar Raiser wrote and why it decided the outcome. |
| [writing_the_promotion_packet.md](writing_the_promotion_packet.md) | **Evidence, not activity** | Eighteen months of a brag doc compressed into a Staff promotion packet. Shows the activity-shaped draft, the scope-shaped rewrite, and what happens to each in calibration. |

---

## 3. Cross-Cutting Primitives

This section has no `cross_cutting/` directory (anything filed there is excluded from the
game's case-study index). The shared primitives are the **module pages** themselves — each
case study leans on several, and the table below says which phase of your reading each one
becomes relevant in.

| Module page | Becomes relevant when... | Used by |
|-------------|--------------------------|---------|
| [technical_writing_for_engineers](../technical_writing_for_engineers/technical_writing_for_engineers.md) | you draft anything anyone else has to read | every case study |
| [design_docs_rfcs_and_adrs](../design_docs_rfcs_and_adrs/design_docs_rfcs_and_adrs.md) | the decision needs a durable artifact | migration, disagreement, platform bet |
| [precise_technical_vocabulary](../precise_technical_vocabulary/precise_technical_vocabulary.md) | a claim about reliability or consistency has to survive scrutiny | SEV1, migration |
| [business_and_finance_vocabulary](../business_and_finance_vocabulary/business_and_finance_vocabulary.md) | the reader owns a budget | platform bet, saying no to a VP |
| [influence_without_authority](../influence_without_authority/influence_without_authority.md) | the people who must agree do not report to you | migration, disagreement |
| [giving_and_receiving_feedback](../giving_and_receiving_feedback/giving_and_receiving_feedback.md) | the problem is a person, not a system | underperforming teammate |
| [negotiation_for_engineers](../negotiation_for_engineers/negotiation_for_engineers.md) | there is a fixed pie and two parties | saying no to a VP, platform bet |
| [incident_communication_and_postmortems](../incident_communication_and_postmortems/incident_communication_and_postmortems.md) | something is on fire and customers can see it | SEV1 |
| [story_portfolio_and_star_frameworks](../story_portfolio_and_star_frameworks/story_portfolio_and_star_frameworks.md) | you have to compress a year into four minutes | Amazon loop, promotion packet |
| [scope_impact_and_ownership](../scope_impact_and_ownership/scope_impact_and_ownership.md) | someone asks what you actually changed | promotion packet, Amazon loop |
| [executive_communication](../executive_communication/executive_communication.md) | you get five minutes with someone two levels up | platform bet, saying no to a VP, SEV1 |

---

## 4. Dependency Map

```
technical_writing_for_engineers  (assumed by all eight)
│
├─ getting_a_migration_approved
│  │   needs: design_docs_rfcs_and_adrs, influence_without_authority
│  └─ the_disagreement_that_went_wrong
│         needs: difficult_conversations_and_conflict,
│                hedging_commitment_and_disagreement
│
├─ business_and_finance_vocabulary
│  ├─ translating_a_platform_bet_to_the_cfo
│  │      needs: executive_communication
│  └─ saying_no_to_a_vp
│         needs: negotiation_for_engineers,
│                scope_prioritization_and_saying_no
│
├─ the_sev1_and_its_postmortem
│         needs: incident_communication_and_postmortems,
│                precise_technical_vocabulary
│
├─ the_underperforming_teammate
│         needs: giving_and_receiving_feedback,
│                difficult_conversations_and_conflict
│
└─ scope_impact_and_ownership
   ├─ writing_the_promotion_packet
   │      needs: performance_reviews_and_promotions
   └─ the_amazon_loop
          needs: behavioral_interview_systems,
                 story_portfolio_and_star_frameworks
```

Read down a branch, not across. The two career case studies at the bottom are the ones
that assume the most, which is why they are last in the path even though they are the ones
people reach for first.

---

## 5. Interview Prep Shortcuts

| If you are asked... | Read |
|---------------------|------|
| "Tell me about a time you influenced a team you had no authority over" | [getting_a_migration_approved.md](getting_a_migration_approved.md) |
| "Tell me about a time you disagreed with a decision" | [the_disagreement_that_went_wrong.md](the_disagreement_that_went_wrong.md) |
| "Tell me about a time you had to say no" | [saying_no_to_a_vp.md](saying_no_to_a_vp.md) |
| "Walk me through your worst outage" | [the_sev1_and_its_postmortem.md](the_sev1_and_its_postmortem.md) |
| "Tell me about giving someone difficult feedback" | [the_underperforming_teammate.md](the_underperforming_teammate.md) |
| "Why do you think you are ready for Staff?" | [writing_the_promotion_packet.md](writing_the_promotion_packet.md) |
| "How would you justify this project to leadership?" | [translating_a_platform_bet_to_the_cfo.md](translating_a_platform_bet_to_the_cfo.md) |
| "Prepare me for an Amazon loop" | [the_amazon_loop.md](the_amazon_loop.md) |
| "Describe a project you owned end to end" | [getting_a_migration_approved.md](getting_a_migration_approved.md) plus [writing_the_promotion_packet.md](writing_the_promotion_packet.md) |
| "Tell me about a failure" | [the_sev1_and_its_postmortem.md](the_sev1_and_its_postmortem.md) plus [the_disagreement_that_went_wrong.md](the_disagreement_that_went_wrong.md) |

---

**Maintenance rule:** update this index in the same commit as any new case study — add it
to the correct phase in §2, to the dependency map in §4, and give it a row in §5. Add its
line to the `case_studies` entry in the section
[`README.md`](../README.md)'s `<!-- study-paths -->` block with its tier, or the build
fails under `--strict`.
