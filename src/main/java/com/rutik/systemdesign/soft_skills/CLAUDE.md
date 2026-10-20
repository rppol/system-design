# Soft Skills & Leadership Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/soft_skills/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Section index / learning paths / build manifest: `README.md`.

Created 2026-08-25 in one pass, at owner request: *"a section on behavior leadership,
vocabulary, and soft skills in general — put everything in ONE soft skills section."*
It is one section by instruction. Do not split it.

---

## Why This Section Exists

Every other section in this repo answers "can you build it". Nothing answered "can you
get it approved, get it staffed, get it adopted, and still have a team afterwards" — and
that is the whole difference between the senior and staff-plus rungs of the ladders in
module 1. The section covers behavioural interviewing, leadership, influence,
communication, **vocabulary**, and soft skills generally.

It is written to the same evidence bar as the rest of the repo. Named companies, named
books with authors and years, real numbers, verbatim scripts. **A soft-skills section
fails the moment it reads like a LinkedIn post**, and that is the failure mode any future
pass here must be audited against first.

---

## Template Adaptation — 3 headings retitled, numbers untouched

Module pages use the repo's 14-section template with the numbers exactly as the root
`CLAUDE.md` specifies. Three TITLES are adapted because the technical wording does not
describe anything in this domain:

| # | Repo template title | This section's title | Why |
|---|--------------------|----------------------|-----|
| 4 | Types / Architectures / Strategies | **Types / Levels / Modes** | there is no architecture; the axis is level, register or mode |
| 5 | Architecture Diagrams | **Models & Frameworks Diagrammed** | the diagrams draw frameworks (Radical Candor's axes, Cynefin's domains, an escalation path), not architecture |
| 11 | Technologies & Tools | **Frameworks, Books & Further Reading** | the §11 trap — see below |

Everything else is verbatim, including — non-negotiably — `## 12. Interview Questions
with Answers`.

**§6 "How It Works — Detailed Mechanics" carries transcripts, scripts, templates and
worked numbers where other sections carry code.** That is the adaptation of the content,
not of the heading.

### The section's version of "show broken code, then the fix"

**Every module page carries at least two `Weak version -> Strong version` pairs**: the
sentence, message, story or update as a weak engineer actually writes it, then the
rewrite, then one line naming what changed. `technical_writing_for_engineers` carries
eight or more; it is the module the rest of the section leans on.

This is the single highest-value content in the section and the thing that keeps it
concrete. A module without one has not met the bar.

---

## THE §11 DECISION — read this before renaming any heading here

`game/build_tech.py` walks EVERY section's `## 11. Technologies & Tools` table to build
`game/tech_index.json`, the game's technology index. A §11 in this section listing RACI,
SBI, Radical Candor and Cynefin would silently inject behavioural frameworks into a list
of software products, on a green build.

**Investigated:** the matcher is

```
TECH_HDR = re.compile(r"^##\s+(?:\d+[.)]\s*)?.*\btechnolog\w*\b.*\btools?\b.*$", re.I)
```

It keys on the heading **TEXT**, not the number. The `\d+[.)]` group is optional and
non-capturing; the match requires BOTH a `technolog…` token AND a `tool`/`tools` token.
`## 11.` on its own is invisible to it.

**Decision: retitle §11 to `## 11. Frameworks, Books & Further Reading`.** Chosen over an
explicit path exclusion alongside `TECH_BANK_DIR` because:

- it is a **zero-line code change**, so nothing new has to be remembered by anyone
  maintaining `build_tech.py`;
- it is **self-documenting** — the heading itself says the contents are not tools, so the
  trap cannot be re-armed without also renaming the heading back, which is a visible edit
  in a content file rather than an invisible omission in a build script;
- a path exclusion would be a **second silent, load-bearing rule** of exactly the kind
  `TECH_BANK_DIR`'s own comment describes as a trap;
- a path exclusion would also have dropped §8 from the tradeoff digest, which is not
  wanted (below).

**Consequently, two naming rules bind every file in this section:**

1. **Never put both a `technolog*` word and `tool`/`tools` in any `##` heading here.**
2. **Never name a file `*_deep_dive.md` or `*_internals.md`.** `DEEP_NAME_RE` in
   `build_tech.py` makes any such page FATAL under `--strict` unless it is hand-listed in
   `PRODUCT_DEEP_DIVES` or `CONCEPT_DEEP_DIVES`. Nothing here is a product deep dive.

### §8 "Tradeoffs" is deliberately left alone

`TRADE_HDR` indexes §8 into the Technologies screen's per-module tradeoff digest. That is
correct and wanted: the digest answers "what decision does this page make me weigh", it
renders grouped and labelled by section, and "sponsorship vs mentorship" or "sync vs
async decision" is a real answer to that question. Only §11 feeds the technology index
itself, so nothing non-software reaches `tech_index.json` from §8.

---

## Module List — 27 Modules (7 Phases)

Order is the learning-path order; `STUDY_ORDER.soft_skills` in `game/app.js` is the
authority for it.

| Phase | Modules |
|-------|---------|
| 1 — What Seniority Actually Means | engineering_career_ladders, scope_impact_and_ownership, staff_plus_archetypes |
| 2 — Vocabulary & Precision | precise_technical_vocabulary, business_and_finance_vocabulary, leadership_and_org_vocabulary, hedging_commitment_and_disagreement |
| 3 — Writing That Travels | technical_writing_for_engineers, design_docs_rfcs_and_adrs, executive_communication, upward_and_async_communication, incident_communication_and_postmortems |
| 4 — Influence, Conflict, Negotiation | influence_without_authority, giving_and_receiving_feedback, difficult_conversations_and_conflict, negotiation_for_engineers, scope_prioritization_and_saying_no |
| 5 — Leading People and Teams | mentorship_and_sponsorship, technical_leadership_and_execution, technical_strategy_and_vision, cross_functional_collaboration, team_health_and_sustainable_pace |
| 6 — Interviews & Career Operations | behavioral_interview_systems, story_portfolio_and_star_frameworks, interviewing_as_the_interviewer, performance_reviews_and_promotions |
| 7 — 2026 | ai_era_engineering_leadership |

**Why vocabulary is four modules.** It was an explicit owner ask and is the easiest thing
in this section to under-deliver. A single "vocabulary" module becomes a word list.
Four registers — precise-technical, business/finance, leadership/org, and the language of
hedging and disagreement — each need enough room for a real lexicon.

### The glossary record format (Phase 2 modules)

Every glossary entry in `precise_technical_vocabulary`,
`business_and_finance_vocabulary` and `leadership_and_org_vocabulary` is a **four-part
record**, never a bare definition:

| Part | What it is |
|------|-----------|
| Term | the word, in the form people actually write it |
| Definition | precise, with its **authority** named (RFC number, the SRE book, an accounting standard, the author who coined it) |
| Misuse | a real sentence in which engineers get it wrong |
| Correct use | a real sentence in which it is right |

A term with no misuse example is a term nobody needed defining.

### Modules requiring more than the 15-Q&A floor

`behavioral_interview_systems` and `story_portfolio_and_star_frameworks` carry 18+ —
they are the section's direct interview-prep surface.

---

## Case Studies — 8 Total

`case_studies/` — flat files, 11-section principal template with §4/§7/§10 titles adapted
the same way the module template is (numbering untouched). Quality bar: 700-1100 lines,
verbatim transcripts in §4, named companies in §6, quantified consequence in §9, 10+ Q&As
in §11.

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

Learning-path index: `case_studies/case_studies.md` (mandatory; update with every new case
study, same commit).

**No `cross_cutting/` sub-directory here.** It is in `CS_EXCLUDE_DIRS` in `extract.py`, so
anything filed there never reaches `index.caseStudies` and can never be tiered.

**Case-study Q&As never enter the quiz bank.** That is the repo-wide rule and it is
working, not a bug. Do not "rescue" one by moving it under an interview heading.

---

## Content Rules (Soft Skills-specific)

- **Concrete or delete it.** If a sentence would survive being pasted into any article on
  the internet, it does not belong here. Named companies, named books with authors and
  years, real numbers, verbatim scripts with speaker labels.
- **Do not fabricate a framework's internals.** Quadrant names, level names, acronym
  expansions and study figures are either right or absent. A module with six real
  citations beats one with twenty half-invented ones. This domain is unusually easy to
  hallucinate confidently in, because plausible-sounding leadership frameworks are cheap
  to invent — that is precisely why the bar is higher here, not lower.
- **Transcripts over descriptions.** When the subject is a conversation, write the words.
- **§10 Common Pitfalls carries consequences**, not warnings: what it cost, who it cost.
- **No emojis** anywhere; the plain check and cross marks only.
- **Diagrams appeal-first**: Mermaid preferred (`flowchart LR` by default,
  `quadrantChart` for the many two-axis models in this domain, `stateDiagram-v2` for
  lifecycles, `sequenceDiagram` for conversations). Every node coloured with the One-Dark
  `classDef` palette. Validate with `node scripts/check_render.mjs <file>` before
  committing — a fence that does not parse exits 1 in `scripts/build_diagrams.mjs` and
  takes the Pages deploy and the APK red.
- `---` horizontal rules between every top-level section.
- Cross-link to the module PAGE (`../executive_communication/executive_communication.md`),
  never to a bare directory.

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the Full Path (all 27 modules) plus both curated tiers, **Senior
(18)** and **Principal (18)**, with an overlap of 9. Nine of the eighteen principal
modules are material senior never sees, which is the repo's stated property: the tiers are
different CUTS, not nested depths.

Here specifically: **senior is the craft** — can you write the design doc, give the
feedback, run the incident channel, answer the behavioural question. **Principal is the
judgment** — which rung is this really, what does it cost the business, who do you talk to
first and in what order, what do you tell a team *not* to do. So the entire
business-and-finance register, the influence and negotiation modules, and
`interviewing_as_the_interviewer` are principal-only, while the writing craft and the
answer-the-question modules are senior-only.

Membership is declared ONCE, in the `<!-- study-paths -->` block in `README.md`. Order is
never declared there — it comes from `STUDY_ORDER.soft_skills`. The tier TABLES in
`README.md` sit between `<!-- study-path-table <tier> -->` markers and are **generated**;
regenerate with `python3 game/extract.py --write-paths` and never hand-edit them.

---

## Adding a New Soft Skills Module

1. Create `<module_name>/<module_name>.md` — the 14-section template with the three
   adapted titles above; 15 Q&As minimum, every one `**Q: …?**` with an authored
   `**Short:**` line of 15-220 chars as the first non-blank line of its answer
2. At least two `Weak version -> Strong version` pairs, and at least two Mermaid diagrams
   with every node coloured
3. Confirm §11 is titled `Frameworks, Books & Further Reading` and the filename is not
   `*_deep_dive.md` / `*_internals.md`
4. Add the module dir to `STUDY_ORDER.soft_skills` in `game/app.js` at its phase position
5. Add the module and every file it owns to `README.md`'s `<!-- study-paths -->` block,
   tagging each `senior`, `principal`, both, or `-`. **Never put a block in the module
   page — content files hold only content.** Then run
   `python3 game/extract.py --write-paths`
6. Update `README.md`'s module table and the phase table above
7. Update root `README.md` and root `CLAUDE.md`
8. `node scripts/check_render.mjs <abs path>`, then `python3 game/extract.py --strict`,
   then the whole of `bash scripts/build_banks.sh` — `--strict` passing does not mean the
   script passes

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module, EVERY
file that module owns, and EVERY case study, each tagged with the tiers it belongs to —
`-` means Full path only.

**A content file carries NO structural metadata.** A module page holds the content of its
topic and nothing else.

**Adding a file? Add its line to the block in the same commit.** A file on disk missing
from the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red, in both directions.
