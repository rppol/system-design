# Book Audit — 2026-08-25

> **Scope note, read first.** `book/` is permanently excluded from the repo's
> factual-currency audit and that exclusion does not expire (`book/CLAUDE.md` §Scope).
> Nothing in this report proposes modernizing a 2017 DDIA claim to 2026 reality. The three
> axes actually audited are **fidelity** (does the summary say what the book says),
> **coverage** (which books are missing), and the **`**Short:**` question**. Section 2 is
> retitled "Fidelity — verified misrepresentations" for that reason.

## 0. Snapshot

`book/` is 74,140 lines across 6 books / 69 chapter pages, and on the structural axes it is
the cleanest section in the repository. All 69 pages carry all 12 required template
headings — zero omissions, verified mechanically. Every chapter clears the 15-Q&A floor.
`STUDY_ORDER.book` matches disk exactly: 69 entries, no orphans, no duplicates, no
ordering drift, `BOOK_LABELS` complete for all six books. Chapter lists were verified
against primary sources for **all six books and all six match exactly**, including the
33-chapter Understanding Distributed Systems collapse — every one of Vitillo's 33 chapters
is present, correctly placed, with no invented headings. Claim-level sampling across
roughly half the chapters turned up **four** findings in total, one of which is a wording
hedge. On fidelity, which is the only axis that applies here, this section is in good
shape.

Its single biggest weakness is that **the section's most important book gets its least
effort, and it is now describing an edition that has been superseded.** DDIA averages 660
lines per chapter against SDI Vol 2's 1,215 — for chapters that are *longer and denser in
the source book*, not shorter. DDIA also carries the section's worst derived-MCQ quality
(39% of its Q&A options ship truncated, against 10% for SDI Vol 1). And **DDIA 2nd
edition shipped in March 2026** with a re-chaptered, substantially rewritten structure,
while these 14 files never state which edition they summarize — the one book in the
section whose files omit the edition, where UDS states "(2nd ed.)" in every Further
Reading block.

The second weakness is narrower and more dangerous: three verified cases in UDS where
technically-correct material the book does not contain is presented under a heading citing
that book chapter. The repo already invented the right fix — an explicit
`## Beyond the book — …` section — and uses it in exactly **one file out of 69**.

| Metric | Value |
|--------|-------|
| Books | 6 (across 6 folders) |
| Chapter pages | 69 (+ 6 book index pages + 1 section README + 1 CLAUDE.md) |
| Deep-dive sub-files | 0 |
| Case studies | 0 (by design — the chapters *are* the case studies) |
| Total lines | 74,140 |
| Total Q&As | 1,402 (all in the quiz bank; **0 authored `**Short:**`**) |
| Mermaid fences | 433 |
| Avg lines/chapter | 1,074 |
| Avg Q&As/chapter | 20.3 |
| Template compliance | **69/69 files carry all 12 required headings** |
| `STUDY_ORDER.book` | **69/69, exact, no drift** |

Per-book breakdown:

| Book | Ch pages | Lines | Avg lines/ch | Q&As | Avg Q&As/ch | Mermaid | Truncated MCQ options |
|---|---|---|---|---|---|---|---|
| DDIA (Kleppmann) | 13 | 8,289 | **660** | 208 | 16.0 | 64 | **81 / 208 = 39%** |
| SDI Vol 1 (Xu) | 16 | 16,704 | 1,044 | 331 | 20.7 | 106 | 33 / 331 = 10% |
| SDI Vol 2 (Xu & Lam) | 13 | 15,797 | 1,215 | 270 | 20.8 | 82 | 43 / 270 = 16% |
| ML SDI (Aminian & Xu) | 11 | 12,109 | 1,101 | 225 | 20.5 | 71 | 58 / 225 = 26% |
| DMLS (Huyen) | 11 | 11,050 | 1,004 | 231 | 21.0 | 61 | 55 / 231 = 24% |
| UDS (Vitillo) | 5 files / **33 chapters** | 9,720 | 1,944/file, **294/book-ch** | 137 | **4.2 per book chapter** | 49 | 32 / 137 = 23% |

---

## 1. Missing — proposed ADDITIONS

The coverage question here is *which books*, not which modules. Three signals drove the
list: (a) DDIA 2e's release, (b) the 2026 AI-engineering reading canon, and (c) the fact
that a **brand-new 26-module `soft_skills/` section** is being built in this same session,
which for the first time gives the career/leadership books a topic section to pair with.

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `book/designing_data_intensive_applications/` — **edition stamp**, then a 2e decision | rework of an existing book | **DDIA 2nd edition, Kleppmann & Riccomini, published March 2026** (early release Feb 2026, print 6 Mar 2026, 670pp, ISBN 9781098119065) — [martin.kleppmann.com/2026/03/24/…](https://martin.kleppmann.com/2026/03/24/designing-data-intensive-applications-2e.html), [O'Reilly](https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/). It is **re-chaptered to 14 chapters in 3 parts**: old Ch 1 splits into *Trade-offs in Data System Architecture* + *Defining Non-Functional Requirements*, everything from old Ch 2 shifts +1, and Part III gains *Stream Processing System Philosophy* with *Doing the Right Thing* becoming its own Ch 14 (per the [Vonng/ddia](https://github.com/Vonng/ddia) mirror). Ch 10 (Consistency and Consensus) was "almost completely rewritten"; MapReduce coverage was largely cut in favour of Spark/Flink; new material on sync engines / local-first, workflow engines and durable execution, formal methods and randomized testing, GraphQL, and AI/ML data systems (vector indexes, DataFrames, training-data batch systems). Anyone buying "DDIA" in 2026 gets 2e. **Right now not one of the 14 DDIA files states which edition it summarizes.** | **P0** (the stamp) / P1 (the 2e summary) | +150 / +9,000 |
| `book/ai_engineering/` — 10 chapters | new book | **AI Engineering: Building Applications with Foundation Models**, Chip Huyen, O'Reilly 2025 — the most-read book on the O'Reilly platform in 2025 and the single most-recommended AI-engineering title in every 2026 list surveyed ([dev.to AI Engineer's Reading List 2026](https://dev.to/somadevtoo/the-ai-engineers-reading-list-for-2026-10-books-that-matter-50pb), [reactjava.substack.com](https://reactjava.substack.com/p/i-have-read-20-books-on-ai-and-llm), [O'Reilly](https://www.oreilly.com/library/view/ai-engineering/9781098166298/)). The repo already has Huyen's *other* book. It has a 54-module `llm/` section with **nothing in `book/` that pairs with it** — the biggest book/topic mismatch in the repo. TOC verified: [chiphuyen/aie-book ToC.md](https://raw.githubusercontent.com/chiphuyen/aie-book/main/ToC.md). | **P0** | 10,000 |
| `book/generative_ai_system_design_interview/` — 11 chapters | new book | **Generative AI System Design Interview**, Ali Aminian & Hao Sheng, ByteByteGo 2024 ([Amazon](https://www.amazon.com/Generative-AI-System-Design-Interview/dp/1736049143), [PSU catalog](https://catalog.libraries.psu.edu/catalog/50177449)). This is the *third* volume of the exact ByteByteGo interview series the section already carries two-and-a-half volumes of, it is the only GenAI-specific design-interview book, and it is the direct 2026 successor to `machine_learning_system_design_interview/`. Chapters: Introduction and Overview; Gmail Smart Compose; Google Translate; ChatGPT Personal Assistant Chatbot; Image Captioning; Retrieval-Augmented Generation; Realistic Face Generation; High-Resolution Image Synthesis; Text-to-Image Generation; Personalized Headshot Generation; Text-to-Video Generation. | **P0** | 11,000 |
| `book/the_staff_engineers_path/` — 14 chapters | new book | **The Staff Engineer's Path**, Tanya Reilly, O'Reilly 2022 ([Amazon](https://www.amazon.com/Staff-Engineers-Path-Individual-Contributors/dp/1098118731)). The new `soft_skills/` section ships modules named `staff_plus_archetypes`, `engineering_career_ladders`, `influence_without_authority`, `scope_impact_and_ownership`, `technical_leadership_and_execution` — this book is the canonical source text for every one of them, and until this session there was nothing in the repo for it to pair with. Reilly's three-pillar frame (big picture / execution / levelling up) maps almost 1:1 onto the section's structure. | **P1** | 9,000 |
| `book/staff_engineer_larson/` — 4 parts + narratives | new book | **Staff Engineer: Leadership beyond the management track**, Will Larson, 2021 ([staffeng.com/book](https://staffeng.com/book/), [Amazon](https://www.amazon.com/Staff-Engineer-Leadership-beyond-management/dp/1736417916)). The archetype taxonomy (Tech Lead / Architect / Solver / Right Hand) that `soft_skills/staff_plus_archetypes` is built on *comes from this book*. Shorter and more quotable than Reilly; the two are complements, not duplicates. Full text is free at lethain.com, so fidelity is cheaply verifiable — a rare advantage in this section. | P1 | 5,500 |
| `book/database_internals/` — 13 chapters (2 parts) | new book | **Database Internals**, Alex Petrov, O'Reilly 2019 ([databass.dev](https://www.databass.dev/), [O'Reilly](https://www.oreilly.com/library/view/database-internals/9781492040330/)). The repo's own `book/README.md` already names it as a planned addition ("More may be added over time (e.g. *Database Internals*, *Streaming Systems*)"). It is the depth layer under DDIA Ch 3 and Ch 9 for the 31-module `database/` section: B-tree internals, page layout, LSM compaction strategies, failure detection, leader election, Paxos/Raft/Zab. Part II makes it the only book in the section that is a genuine distributed-*algorithms* text. | P1 | 11,000 |
| `book/site_reliability_engineering/` — selected chapters | new book | **Site Reliability Engineering** (Google, 2016) + **The Site Reliability Workbook** (2018), both free and complete online — [sre.google/books](https://sre.google/books/), [sre.google/sre-book/table-of-contents](https://sre.google/sre-book/table-of-contents/). Google publishes an ongoing chapter-mapped update compendium ([sre.google/resources/book-update](https://sre.google/resources/book-update/)) so a 2026 fidelity pass is unusually well-supported. Pairs with the 41-module `devops/` section, which today has **no** book at all. Recommend a curated ~14-chapter cut (SLOs, error budgets, eliminating toil, monitoring, release engineering, incident response, postmortems), not all 34. | P1 | 9,000 |
| `book/fundamentals_of_software_architecture/` — 24+ chapters | new book | **Fundamentals of Software Architecture, 2nd Edition**, Richards & Ford, O'Reilly March 2025 — [O'Reilly](https://www.oreilly.com/library/view/fundamentals-of-software/9781098175504/), [developertoarchitect.com lesson 205](https://developertoarchitect.com/lessons/lesson205.html). Five new chapters including generative AI and team topologies. Pairs with `hld/` and `lld/`; supplies the architecture-characteristics vocabulary that `soft_skills/design_docs_rfcs_and_adrs` assumes. Note the 2nd edition specifically — the 1st is 2020 and pre-dates the ADR/characteristics rework. | P2 | 12,000 |
| `book/building_microservices/` — 16 chapters | new book | **Building Microservices, 2nd Edition**, Sam Newman, O'Reilly 2021 ([samnewman.io](https://samnewman.io/books/building_microservices/)). Pairs with `backend/`'s microservices cluster. P2 rather than P1 because `backend/` already covers most of this at production depth — the marginal value is Newman's *cautionary* framing ("microservices are not the solution for every business need"), which the topic sections under-teach. | P2 | 10,000 |
| `book/crucial_conversations/` | new book | **Crucial Conversations, 3rd Edition**, Grenny et al. ([Amazon](https://www.amazon.com/Crucial-Conversations-Tools-Talking-Stakes/dp/1260474186)), consistently named for staff-level behavioural prep ([eng-leadership.com](https://newsletter.eng-leadership.com/p/how-to-nail-big-tech-behavioral-interviews)). Direct source for `soft_skills/difficult_conversations_and_conflict` and `giving_and_receiving_feedback`. | P2 | 5,000 |

### P0 detail

**1. DDIA edition.** This is not a currency finding and must not be executed as one — it is
an *attribution* finding, which is squarely in scope. It splits into two decisions of very
different size, and only the first is urgent.

*Step 1 — the edition stamp (P0, ~2 hours, do it regardless of what follows).* Add
`1st edition (2017)` to every DDIA file's `>` byline and to the book index, exactly as the
five UDS files already write "(2nd ed.)". The existing 13 files are a *correct* summary of
1e; they are only ambiguous, and the ambiguity became load-bearing in March 2026. This is
cheap, safe, and independently useful.

*Step 2 — how to cover 2e (P1, owner's call).*
- *Option A (~2,500 lines).* Add
  `book/designing_data_intensive_applications/13_second_edition_delta/` — a
  chapter-by-chapter 1e→2e map (the Ch 1 split, the +1 shift, the new Ch 13/14, the Ch 10
  rewrite, the MapReduce cut, and the new sync-engine / durable-execution / formal-methods
  / GraphQL / vector-index material) with its own 15+ Q&As. Cheapest, and it is the single
  most useful artefact for a reader who owns 1e and is being told to buy 2e.
- *Option B (~9,000 lines).* A separate sibling book folder
  `designing_data_intensive_applications_2e/` with its own 14 chapters, `BOOK_LABELS`
  entry, and `STUDY_ORDER.book` entries, keeping 1e alongside. This is what
  `book/CLAUDE.md`'s folder-per-book convention actually implies: 1e and 2e have different
  chapter boundaries and are, for summary purposes, different books. More faithful, but it
  doubles the section's DDIA surface and duplicates roughly 70% of the content.

Under either option, do **not** edit the existing 13 files' claims to match 2e. That is
precisely the fabrication-attributed-to-a-named-author failure `book/CLAUDE.md` exists to
prevent. A/B is a resourcing question, not a correctness one — A now and B later is a valid
path, since the delta page stays useful even after a full 2e folder exists.

**2. `book/ai_engineering/`.** Ten chapter folders following the section template, one
`## N.x` per verified book section: Ch1 Introduction to Building AI Applications with
Foundation Models (rise of AI engineering / use cases / planning / the AI engineering
stack); Ch2 Understanding Foundation Models (training data / modeling / post-training /
sampling); Ch3 Evaluation Methodology (challenges / language-modeling metrics / exact
evaluation / AI as a judge / comparative evaluation); Ch4 Evaluate AI Systems (criteria /
model selection / evaluation pipeline); Ch5 Prompt Engineering (intro / best practices /
defensive prompting); Ch6 RAG and Agents (RAG / agents / memory); Ch7 Finetuning (overview
/ when / memory bottlenecks / techniques); Ch8 Dataset Engineering (curation /
augmentation and synthesis / processing); Ch9 Inference Optimization; Ch10 AI Engineering
Architecture and User Feedback. Cross-links go to `llm/` throughout — this is the book
that makes the repo's largest section legible as one argument.

**3. `book/generative_ai_system_design_interview/`.** Eleven chapters, Ch1 being the
7-step GenAI design framework and Ch2–11 the ten worked designs. Structurally identical to
the existing `machine_learning_system_design_interview/` folder, so the template, the
`## N.x` mapping, and the Q&A shape are all already proven. Cross-links to `llm/` and
`ml/`. This is the lowest-risk large addition in the list.

---

## 2. Fidelity — verified misrepresentations

Chapter lists and section mappings were verified against primary sources for **all six
books**, and **all six match their real tables of contents exactly** — DDIA (13), SDI Vol 1
(16), SDI Vol 2 (13), MLSDI (11), DMLS (11), UDS (33). No missing chapter, no invented
chapter, no reordering, no invented `## N.x` heading, and no top-level book section omitted
from any chapter sampled. This is the section's real achievement and it should be recorded
so nobody re-derives it.

Three specific positives worth naming:

- **DDIA came back with zero hard fidelity violations across all 13 files.** Numeric
  spot-checks confirmed against the book: Twitter fan-out (Ch 1), the GitHub 2012
  async-failover incident (Ch 5), the 200ppm/17-second clock-drift figure and Spanner's
  6ms/30s NTP figure (Ch 8).
- **The two ML books came back essentially clean.** DMLS chapter sections verified against
  O'Reilly, Huyen's own blog, and `chiphuyen/dmls-book`; MLSDI's CTR-model lineage in Ch 8
  (LR → feature crossing → GBDT → GBDT+LR/Facebook 2014 → DNN → DCN → FM → DeepFM) and the
  Netflix take-rate example in DMLS Ch 2 both verified accurate and properly attributed.
- **MLSDI's framework is 7 steps, not 6** (Clarifying Requirements → Frame as ML Task →
  Data Preparation → Model Development → Evaluation → Deployment and Serving → Monitoring
  and Infrastructure), and `01_introduction_and_overview.md` correctly uses all seven as
  `## 1.1`–`## 1.7`. Recorded because a prior summary of this section understated it — the
  repo is right and the summary was wrong.

The findings below are almost all of one kind: **technically correct material the book does
not contain, presented under a heading that cites that book chapter.** None of it is
factually wrong. All of it currently reads as the named author's.

| File | Line/heading | What it says | What the book actually says | Source |
|---|---|---|---|---|
| `understanding_distributed_systems/01_communication/01_communication.md` | 56, `## 1.1 Introduction (Ch 1)` | "You accept the enormous complexity of a distributed system for **exactly three reasons**… Scale beyond a single node / Fault tolerance / Low latency by proximity", plus "if none of them apply, you shouldn't build one" | Vitillo Ch 1 gives **four** reasons, each with a named example: applications that are *inherently distributed* (the web), high availability (Dropbox), workload too big for one node (Google search), latency (Netflix). The summary drops "inherently distributed" entirely, asserts a count the book does not assert, and adds a normative closer that is the summarizer's. | [understandingdistributed.systems/sample.pdf](https://understandingdistributed.systems/sample.pdf) pp.18–19 (full Ch 1 text) |
| `understanding_distributed_systems/01_communication/01_communication.md` | 473–495, `### Custom protocols (UDP and QUIC)` under `## 1.2 Reliable Links (Ch 2)` | ~23 lines of QUIC mechanics (0/1-RTT handshake, per-stream sequence spaces and head-of-line blocking, connection-ID migration), plus 20+ further QUIC references through the file | Vitillo Ch 2 §2.5 "Custom protocols" is **one page and discusses only UDP** plus a multiplayer-game example. **QUIC appears nowhere in Chapter 2.** | same sample PDF — Ch 2 is reproduced in full, ending at "GET THE REST OF THE BOOK" |
| `understanding_distributed_systems/01_communication/01_communication.md` | 220–234, under `## 1.2 Reliable Links (Ch 2)` | The RFC 6298 retransmission-timeout formula `SRTT + 4×RTTVAR` (Jacobson/Karels) with a worked LAN-vs-satellite table | Ch 2 §2.1 says only that "a timer fires on the sending side and the segment is retransmitted" — no formula, no named estimator | same |
| all 14 `designing_data_intensive_applications/*` files | every `>` byline and the book index at `designing_data_intensive_applications.md:3` | "Martin Kleppmann · O'Reilly" — **no edition, no year, anywhere in the folder** | There are now two editions with different chapter numbering and substantially different content. Every one of the five UDS files states "(2nd ed.)" in Further Reading; DDIA is the only book in the section that identifies no edition. A 2026 reader following `## 7.3 Serializability` against a 2e copy lands in the wrong chapter. | [O'Reilly 2e](https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/) |
| `designing_data_intensive_applications/designing_data_intensive_applications.md` | 28, `## The Three Parts (ASCII Map)` | Heading says ASCII | The block beneath it is a Mermaid `flowchart TD`. Stale heading left over from the Mermaid conversion. | n/a — on-disk |

**Soft findings — imprecision, not misattribution.** Recorded for a cheap sweep, not urgent:

| File | Line | Issue |
|---|---|---|
| `designing_data_intensive_applications/07_transactions/07_transactions.md` | 274 | "The classic (~30 years) approach" for two-phase locking. 2PL traces to Eswaran et al. 1976, so ~41 years as of the book's 2017 publication. Low confidence that the book states an age at all — likely the summarizer's own approximation, in which case it is simply wrong arithmetic. |
| `designing_data_intensive_applications/10_batch_processing/10_batch_processing.md` | 345 | "build a **search index** (Lucene segments built by a batch job, as at early Google)" compresses two separate book points — Google's proprietary MapReduce-built index, and Lucene/Solr's unrelated indexing — into a clause readable as "Google's search engine used Lucene", which is false. The book makes both points but never conflates them. |
| `designing_data_intensive_applications/02_data_models_and_query_languages/02_data_models_and_query_languages.md` | — | "NoSQL (a 2010s hashtag-turned-movement)" — coined 2009. |
| `designing_data_intensive_applications/08_trouble_with_distributed_systems/08_trouble_with_distributed_systems.md` | 66 | "~12 network faults/month" could not be confirmed against the book text; the correct citation (Gill/Jain/Nagappan, SIGCOMM 2011) *is* in the chapter's real reference list. Unverified, not asserted wrong. |
| `designing_machine_learning_systems/04_training_data/04_training_data.md` | 298, inside `## 4.2 Labeling` | "…This is arguably the single biggest reason modern teams can ship NLP models with modest labeled datasets." The preceding sentence is correctly framed as Huyen's (transfer learning / BERT pretraining becoming default); this superlative is the summarizer's own assessment tacked onto it with no attribution. Hedge it or mark it as the repo's commentary. |

**The pattern, the two halves of it, and the fix that already exists in this repo.** The
important nuance is that this section *already knows how to do it right* and does it right
most of the time. DDIA's "Decoding…" arithmetic boxes — queueing-theory derivations,
Little's Law, Poisson collision math — run through nearly every chapter and are original
scaffolding the book does not contain, but they are consistently framed as the summarizer's
own ("the chapter describes X without quantifying it", "walk one example"), so they are
*not* fidelity violations. `06_partitioning` states Cassandra 4.0's 16-vnode default, which
the 2017 book cannot state, and frames it as an editorial aside. SDI Vol 1 Ch 7 goes
further with an explicit `## Beyond the book — where Snowflake lives today` heading at
`07_design_a_unique_id_generator.md:774` plus two *(beyond the book)* glossary tags; SDI
Vol 1 Ch 2 does it inline at line 305 ("including rows the book does not list (99.95%,
99.995%)").

So the defect is not that material is added — it is that the disclosure is a **habit of
some authors rather than a rule**, and where the habit lapsed (UDS Ch 1–2) the reader gets
QUIC attributed to a book that never mentions it. The explicit heading form is used in
**1 file out of 69**. The prescription for every finding in the table above is *disclosure,
not deletion*: the QUIC material is good and should stay, under a
`## Beyond the book — QUIC and HTTP/3` heading placed outside the `## 1.2 (Ch 2)` span.
Make it a mandated rule in `book/CLAUDE.md`.

**One near-miss that is NOT a finding.** `designing_machine_learning_systems/08_…` promotes
Huyen's *Detecting* and *Addressing* data-distribution shifts from subsections of "Handling
Data Distribution Shifts" to standalone `## 8.3` / `## 8.4` headings. Content is complete
and nothing is invented — it is a flattening forced by the template's single-level `## N.x`
numbering. Not a fidelity error; recorded so it is not re-flagged. Likewise the four
"see the Pitfalls section" forward-references in MLSDI chapters 01/04/08/10 are
self-references to the same file, not undisclosed material.

**Not verified.** UDS chapters 3–33 could not be checked against primary text (the free
sample stops at Ch 2), so the same lapse may recur across the other four part-files — and
both UDS findings above sit in exactly the two chapters that *could* be checked, which
makes this the section's largest open risk. See §7.

---

## 3. Reordering — SEQUENCE and TIER changes

**Tiers: nothing to do, and nothing may be done.** `book/` is Full-path only. Every one of
the 69 entries in the `<!-- study-paths -->` block is correctly tagged `-`, and a tier
marker here would *fail the build* (tier membership is validated as a 2-segment module id;
book ids are 3 segments). Confirmed correct as-is. Do not touch.

**Sequence: `STUDY_ORDER.book` is mechanically perfect but pedagogically debatable.** All
69 entries present, no orphans, no duplicates, chapters in book order within each book.
Current book order: DDIA → SDI Vol 1 → SDI Vol 2 → DMLS → MLSDI → UDS.

| Item | Current | Proposed | Why |
|---|---|---|---|
| UDS position | last (6th) | **1st**, or split Part I to 1st | UDS chapters are 3–8 pages and deliberately breadth-first; the section README itself calls Part I "the networking on-ramp". Today a learner's very first book node is DDIA Ch 0, the densest text in the section. Leading with UDS Communication + Coordination gives the vocabulary DDIA Part II assumes. |
| DMLS vs MLSDI | DMLS then MLSDI | unchanged | Correct as-is: Huyen teaches the system, Aminian & Xu drill the interview. Note the section README and root CLAUDE.md both list MLSDI *before* DMLS — the docs are wrong, not the code. |
| DDIA new delta chapter | n/a | append after `12_future_of_data_systems` | If Option A in §1 is taken. |

Proposed array (only the book-group order changes; chapter order inside each book is
untouched):

```js
book: [
  // UDS first — the on-ramp
  "book/understanding_distributed_systems/01_communication",
  "book/understanding_distributed_systems/02_coordination",
  "book/understanding_distributed_systems/03_scalability",
  "book/understanding_distributed_systems/04_resiliency",
  "book/understanding_distributed_systems/05_maintainability",
  // DDIA — 13 existing entries, unchanged order, + the 2e delta page if built
  "book/designing_data_intensive_applications/00_preface_and_book_map",
  … 01 … 12 unchanged …
  "book/designing_data_intensive_applications/13_second_edition_delta",
  // SDI Vol 1 (16), SDI Vol 2 (13) — unchanged
  // DMLS (11), MLSDI (11) — unchanged
  // new: ai_engineering (10), generative_ai_system_design_interview (11)
]
```

This is **P2**. It is a real improvement and it is safe (progress keys are per-module id,
so reordering loses no learner state), but it is the lowest-value change in this report and
should not block anything.

---

## 4. Depth — THIN chapters and BLOAT

No bloat. Nothing in this section should be merged or deleted. Two genuine depth problems.

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| **All 13 DDIA chapters** | 546–753 (avg 660) | 15–17 (avg 16.0) | The section's own quality bar is a ~700-line floor for technical chapters; **10 of the 12 numbered DDIA chapters are under it** (only Ch 3 at 753 and Ch 5 at 725 clear it), and they summarize the *longest* chapters in the section's whole reading list. Every other book in the section clusters at 790–1,412 lines per chapter. Ch 7 Transactions gets 616 lines and Ch 9 Consistency & Consensus 641 — against SDI Vol 2's *Nearby Friends* at 1,137 for a much shorter source chapter. The book-content span (excluding the template apparatus) is only ~345 lines for Ch 7. Concretely: `## 5.3 Multi-Leader Replication` is 63 lines with **no `###` sub-headings at all**, compressing use cases + conflict handling + topologies into bullets, while its siblings 5.2 and 5.4 get 138 and 150 lines with full sub-structure. `## 9.3 Ordering Guarantees` is ~36 lines for causality + Lamport timestamps + total order broadcast. `## 7.3 Serializability` gives serial execution 11 lines, 2PL 12, SSI 15. **What is missing is depth inside sections, not sections** — coverage is complete at heading level. Named examples: Ch 7 never reaches predicate locks or index-range locking at the book's depth; Ch 9 never reaches ZooKeeper's actual API surface (znodes, watch semantics) though the book's coordination-services section does. The "Decoding" math boxes add lines but substitute *original* quantitative content for *book* depth. | Expand the compressed `## N.x` spans to sibling density and raise Q&As to the section average of 20. Target +250–400 lines per chapter. This is the single largest quality gap in the section. Do it **after** the edition stamp in §1, not before. |
| **UDS — Q&A density per book chapter** | 1,413–2,774/file | 22–33/file → **4.2 per book chapter** | The 15-Q&A floor is measured per *file*, and all five files clear it easily. But each file carries 5–10 real book chapters, so *Replication* (Ch 10) and *Messaging* (Ch 23) get roughly four questions each. Any equivalent standalone module elsewhere in the repo carries 15–24. A second, quieter cost: distractors for a Ch 10 question are drawn from a pool contaminated by seven unrelated chapters' first sentences. | Raise to ~8 Q&As per *book chapter* — roughly +130 Q&As across the five files (137 → ~265). Keep the 5-file collapse. |
| `understanding_distributed_systems/01_communication` `## 1.1 Introduction (Ch 1)` | 111 lines | — | Ch 1 is the **longest single chapter in the book (~15 pages)** and gets the thinnest per-page treatment of any section in the file (~7 lines/page vs 20–30 elsewhere). It is also where fidelity finding #1 sits. | Expand while fixing the four-reasons error — one edit. |
| `designing_data_intensive_applications/00_preface_and_book_map` | 305 | 15 | Shortest file in the section. Acceptable for a preface, but it is the natural home for the edition statement and the 1e↔2e map. | Fold the edition disclosure here. |
| `system_design_interview_vol_1/16_the_learning_continues` | 455 | 20 | **Not a defect.** `book/CLAUDE.md` explicitly sanctions 400–600 lines for resource-list closers, and 20 Q&As clears the floor. Recorded so it is not re-flagged. | none |

**On the UDS 5-file collapse.** It is sanctioned and should stay. But `03_scalability.md`
at 2,774 lines carrying 10 book chapters is roughly double every sibling, and the Study nav
gives a learner no way to jump to "Partitioning (Ch 16)". *If* the owner ever reopens it,
the minimal move is to split only that one part along its natural seam — Ch 14–19
(delivery/storage) and Ch 20–23 (compute/messaging) — leaving the other four parts and the
per-book nav structure alone. Not proposed for action now.

---

### The `**Short:**` question — recommendation

**Recommendation: yes, author `**Short:**` lines — but a targeted ~310, not a blanket
1,402. And the line summarizes the BOOK'S CLAIM, never the truth.**

*The measurement, re-run today against `extract.py`'s live `make_short()`*, reproducing
the 2026-08-04 table almost exactly (so that table is still trustworthy):

| Derived option | Count | Share | Author a Short? |
|---|---|---|---|
| Reads fine as-is | 982 | 70.0% | no |
| **TRUNCATED** — the real first sentence exceeded 220 chars and ships as a mid-clause fragment | **302** | 21.5% | **yes** |
| Opens with a referring word ("It targets engineers who…") | 106 | 7.6% | marginal — skip |
| Under 40 chars ("Your application code is.") | 8 | 0.6% | yes |
| Code-fence / table debris | 4 | 0.3% | yes |

Where they are — the executable worklist:

| Book | Truncated | Of total | Worst files |
|---|---|---|---|
| DDIA | **81** | 39% | `12_future_of_data_systems` 10, `06_partitioning` 10, `09_consistency_and_consensus` 10, `11_stream_processing` 9, `04_encoding_and_evolution` 8, `10_batch_processing` 8 |
| MLSDI | 58 | 26% | `02_visual_search_system` 10, `06_video_recommendation_system` 10, `04_youtube_video_search` 8 |
| DMLS | 55 | 24% | `10_infrastructure_and_tooling_for_mlops` 12, `06_model_development_and_offline_evaluation` 9 |
| SDI Vol 2 | 43 | 16% | `13_stock_exchange` 8 |
| UDS | 32 | 23% | `02_coordination` 11, `03_scalability` 11, `04_resiliency` 8 |
| SDI Vol 1 | 33 | 10% | — |
| **Total truncated** | **302** | **21.5%** | + 8 under-40 and 4 debris = **314 to author** |

**The case against (steelmanned).** `book/`'s whole justification for exclusion is that a
summary is answerable only to its source, and the repo's authoring rules were written for
content answerable to reality. A `**Short:**` line is a *new sentence written by the
summarizer* and shipped as the CORRECT option in a quiz — which is exactly the shape of
thing `book/CLAUDE.md` warns about. Worse, the root CLAUDE.md's own audit-ordering rule
says a Short written before an audit "becomes a wrong answer that ships as the CORRECT
option", and `book/` has never been audited at all. And every `**Short:**` line elsewhere
in the repo answers "what is true"; here it would have to answer "what does Kleppmann
say", a different question the format has no way to signal.

**The case for, and why it wins.** The exclusion argument does not actually apply, because
**the derived option is already a summarizer-written sentence — it is just written by a
regex instead of a person.** 302 book Q&As ship today as options that literally stop
mid-clause ("Operability (making it easy for operations to keep the system running
smoothly — monitoring, good defaults, predictabili"). That is not neutrality, it is a
worse summary produced by an algorithm. Authoring the line does not add an interpretive
act; it replaces a bad one with a good one. And it is genuinely fidelity-neutral in a way
the factual audit is not: `answerFull` is untouched, so nothing the book is reported to
say changes by a single word.

The audit-ordering objection cuts the other way too. Elsewhere the rule is "audit first,
because the audit will rewrite the answer." Here §2 found that the fidelity defects are
*additions presented as the author's* — the fix for which is moving material to a
`## Beyond the book` section, **which does not touch the Q&As at all**. The two passes
barely intersect. The one genuine interaction: skip the UDS Ch 1 and Ch 2 Q&As until
finding #1 and #2 are resolved.

**So, three rules for the pass:**

1. **Scope: the 314, not the 1,402.** The 982 that read fine gain nothing and 982 hand-written
   sentences is 982 chances to introduce a fidelity error. The 106 referring-word cases
   are weak, not wrong — the quiz renders the question directly above the options, so "It"
   resolves. Skip them.
2. **The line states the BOOK'S CLAIM, in the book's voice.** Not "what is true in 2026".
   Where the book's position is contestable, the line carries the attribution: prefer
   *"Kleppmann argues the database should be unbundled — the replication log becomes the
   public interface"* over *"Databases should be unbundled."* This is the whole difference
   between a faithful summary and an editorial, and it is cheap to enforce because 39% of
   the work is DDIA, where "Kleppmann argues…" is already the file's prose register.
3. **Never edit `answerFull` while adding one** — the standing repo rule, and it is
   load-bearing here specifically. If the existing first sentence already says it, the
   Short may restate it more tightly; it may never *correct* it.

Verify with `extract.py`'s own counter: after the pass, "authored `**Short:**` summaries:
N of M" should rise by ~314, and `M − N` should equal 1,088.

---

## 5. Cross-section notes

- **`llm/` has no book.** The repo's largest section (54 modules, 29 case studies) is the
  only major section with nothing in `book/` behind it. `ai_engineering` and
  `generative_ai_system_design_interview` both fix this; the first is the better fit.
- **`devops/` has no book.** 41 modules, 13 case studies, zero book pairing. The Google SRE
  book is free, complete, and online, which makes it the cheapest book in this report to
  verify for fidelity.
- **`soft_skills/` (new, 26 modules, being built this session) has no book, and its
  vocabulary comes from two.** `staff_plus_archetypes` is built on Larson's Tech Lead /
  Architect / Solver / Right Hand taxonomy; `engineering_career_ladders`,
  `influence_without_authority`, and `scope_impact_and_ownership` are Reilly's territory.
  Pairing those two books with that section is the highest-coherence addition in the list
  after the AI books. Suggest the section's own README cross-link to them once built.
- **`database/`** — Petrov's *Database Internals* is the depth layer under DDIA Ch 3/9 and
  is already named as a planned addition in `book/README.md`.
- **`book/README.md` carries four stale statements** that are cheap to fix and should be
  done in wave 0:
  1. Lines 208–214: a **"PARKED — to be done later"** banner saying the section is out of
     scope "until the owner re-opens it". `book/CLAUDE.md` says the exclusion is permanent
     and does not expire, and the other three parked sections were unparked on 2026-08-04.
     The banner reads as a scheduling deferral; it is a reasoned permanent exclusion.
  2. Line 217: *"Open the book's `README.md` first"* — **there is no README.md in any book
     folder.** The 2026-07-30 rename made each index `<book_slug>/<book_slug>.md`. This
     instruction has been broken since.
  3. Line 243: *"ordered by the `STUDY_ORDER.book` array (DDIA → SDI Vol 1 → SDI Vol 2 →
     DMLS)"* — drops MLSDI and UDS. The real order has six books.
  4. Lines 257 and 265: the Cross-Reference Map links "Streaming / messaging / Kafka" to
     `../backend/CLAUDE.md`. `CLAUDE.md` is agent rules, not content, and is explicitly
     excluded from the reader — link `../backend/README.md`.
  Also: the README and root CLAUDE.md list MLSDI before DMLS while `STUDY_ORDER` has DMLS
  first (§3), and the README says "five books across six folders" while every other
  document says six books.
- **Untracked junk inside content folders.** Four `.impeccable/` directories containing a
  27-byte `hook.cache.json` sit at `book/`, `designing_machine_learning_systems/`,
  `system_design_interview_vol_2/`, and `understanding_distributed_systems/`. Untracked and
  harmless (`extract.py` skips dotdirs), but they are tooling residue inside study content.
  Delete, and add `.impeccable/` to `.gitignore`.

---

## 6. Execution plan

Waves 1 and 2 are independent of each other and of wave 3; everything inside a wave is
parallelizable across agents unless noted.

**Wave 0 — free corrections (1 session, 1 agent).**
1. Fix the five `book/README.md` defects in §5 and the MLSDI/DMLS ordering mismatch in root
   `CLAUDE.md`. *(~1h)*
2. Fix `## The Three Parts (ASCII Map)` → `(Part Map)` in the DDIA index. *(5 min)*
3. Delete the four `.impeccable/` dirs; add to `.gitignore`. *(5 min)*
4. Add a mandated **`## Beyond the book`** rule to `book/CLAUDE.md`: any material the
   chapter does not contain lives under that heading, outside every `## N.x` span, or
   carries an inline *(beyond the book)* tag. Cite `07_design_a_unique_id_generator.md:774`
   as the model. *(~1h)*
   → No `STUDY_ORDER`, `<!-- study-paths -->`, or root README change.

**Wave 1 — fidelity (2 sessions, up to 3 parallel agents).**
5. Fix the three verified UDS findings in `01_communication.md`: restore Vitillo's fourth
   reason and drop the invented count and the normative closer; move the QUIC block and the
   RFC 6298 formula under `## Beyond the book — QUIC, HTTP/3, and TCP retransmission
   timing`, placed after `## 1.5 APIs (Ch 5)`. *(~3h)*
6. **Sweep UDS parts 2–5 for the same pattern** — material under a `## N.x (Ch M)` heading
   that the cited chapter does not contain. This is the highest-value unverified risk in
   the section: both findings above are in Ch 1–2, which is the only stretch of the book
   available as free primary text, so the clean result elsewhere is a sampling artefact
   rather than evidence. One agent per part-file; needs a copy of the book or Vitillo's
   serialized posts at robertovitillo.com. *(~2 days, parallel)* — the two ML books were
   swept for this and came back clean, so they need no equivalent pass.
7. **Stamp `1st edition (2017)`** into all 14 DDIA bylines + the book index (§1 Step 1).
   Needs no owner decision and no 2e research. *(~2h)*
8. Fix the five soft findings in §2 — the DDIA 2PL "~30 years", the Lucene/Google
   conflation, the NoSQL date, verify-or-drop the "~12 network faults/month" figure, and
   hedge the unattributed superlative at `04_training_data.md:298`. *(~1h)*
   → No structural files change in this wave.

**Wave 2 — depth (3–4 sessions, 3 parallel agents).**
9. Expand the 13 DDIA chapters to sibling density: target 900–1,100 lines and 20 Q&As each,
   prioritising the compressed spans named in §4 (`5.3`, `7.3`, `9.3`, and every `## N.x`
   with no `###` sub-headings). ~+4,000 lines total. Three agents, ~4 chapters each.
   *(~4 days)*
10. Raise UDS Q&As from 137 to ~265 (≈8 per book chapter). *(~2 days)*
   → No `STUDY_ORDER` / `<!-- study-paths -->` change (no new files) unless the 2e delta
   page from wave 1 is built, which needs **all three**: a `STUDY_ORDER.book` entry, a
   `<!-- study-paths -->` line, and a row in the DDIA index Build Manifest.

**Wave 3 — the `**Short:**` targeted pass (2–3 sessions, 3 parallel agents).**
11. Author `**Short:**` for the 314 (302 truncated + 8 under-40 + 4 debris), book by book,
    in truncation-density order: DDIA 81 → MLSDI 58 → DMLS 55 → SDI Vol 2 43 → SDI Vol 1 33
    → UDS 32. Rules per §4. *(~3 days)*
12. Verify: `python3 game/extract.py` must report authored Shorts up by ~314 and `M − N` =
    1,088; then run the whole `scripts/build_banks.sh`, not just `extract.py --strict`.
    → **Must run after wave 2**, since expanding a DDIA chapter may rewrite a first sentence.

**Wave 4 — new books (large; sequential per book, parallel across books).**
13. `book/ai_engineering/` — 10 chapters. *(~6 days)* **P0**
14. `book/generative_ai_system_design_interview/` — 11 chapters. *(~6 days)* **P0**
15. `book/the_staff_engineers_path/` + `book/staff_engineer_larson/` — pair with
    `soft_skills/`; build after that section lands so cross-links resolve. *(~7 days)* **P1**
16. `book/database_internals/`, `book/site_reliability_engineering/`. *(~9 days)* **P1**
17. **If §1 Step 2 Option B is chosen:** `book/designing_data_intensive_applications_2e/` —
    14 chapters, its own `BOOK_LABELS` entry. Blocked on confirming the real 2e chapter list
    (§7 item 3). If Option A is chosen instead, the delta page is a wave-2 item, not here.
    *(~7 days)* **P1**
18. `book/fundamentals_of_software_architecture/`, `book/building_microservices/`,
    `book/crucial_conversations/`. **P2**
    → **Every new book needs all four:** a `BOOK_LABELS` entry + `STUDY_ORDER.book` entries
    in `game/app.js`, a `<!-- study-paths -->` block per chapter in `book/README.md`
    (every line tagged `-`), a Books-table row in `book/README.md` **and** `book/CLAUDE.md`,
    and a row in the root `README.md` Book Summaries table **and** root `CLAUDE.md`.
    Then `python3 game/extract.py --write-paths` followed by the full `build_banks.sh`.

**Total effort: ~7–9 weeks of agent time**, of which waves 0–3 (the fix-what-exists half)
are ~2 weeks and wave 4 (the new books) is the rest. Waves 0–3 are the higher value per
hour: they repair the section's most-used book and its worst quiz artefact. If only one
thing is done, do wave 1 item 7 plus wave 3 — the edition statement and the 314 Shorts,
about 4 days for the two.

---

## 7. Confidence and gaps

**High confidence.** All structural claims are mechanical and reproducible: 69/69 template
compliance, the `STUDY_ORDER`↔disk diff, per-file line/Q&A/Mermaid counts, and the
`**Short:**` measurement (reproduced against live `extract.py`, landing on 302 truncated —
the same number the 2026-08-04 table records, which independently validates both runs).
The DDIA 2e release, the AI Engineering TOC, and the Generative AI System Design Interview
chapter list are all cited to primary or publisher sources.

**Verified fidelity, partial.** SDI Vol 1 and Vol 2 chapter lists and section structures
were checked against ByteByteGo/Amazon TOCs and five chapters were spot-checked against
published chapter summaries — clean. UDS was checked against the publisher's own sample
PDF, which reproduces Chapters 1 and 2 in full; the three findings in §2 all come from that
primary text, and the 33-chapter mapping comes from the sample's complete contents page.

**What I could not verify, in priority order.**
1. **UDS chapters 3–33 against primary text.** The free sample stops at Ch 2 — and both
   substantive findings sit in Ch 1–2, which is the only place I could look. That is a
   sampling artefact, not evidence the rest is clean. Needs the full book, or a
   per-chapter check against Vitillo's blog posts (much of the book was serialized at
   robertovitillo.com).
2. **Claim-level sweeps, everywhere.** Every book's chapter list and section mapping was
   verified; individual claims inside every `## N.x` span were sampled, not swept. Coverage
   of the sampling: DDIA Ch 7–12 + index directly and Ch 0–6 by delegation; DMLS Ch 1, 2, 4,
   6, 7, 8, 10, 11; MLSDI Ch 1 and 8; SDI Vol 1 Ch 2, 4, 6, 7, 16; SDI Vol 2 Ch 12, 13; UDS
   Ch 1–2. Roughly half the section's chapters had a claim-level look. The 2e release makes
   DDIA verification *harder*, not easier — an online summary found in 2026 may describe
   either edition, so a 1e check needs a 1e copy.
3. **DDIA 2e's exact chapter list — two sources disagree, and neither is the book.** The
   [Vonng/ddia](https://github.com/Vonng/ddia) mirror gives 14 chapters in 3 parts with
   Ch 7 titled *Partitioning*; a separate search of O'Reilly's chapter files reported
   Ch 7 as *Sharding* and implied 13 chapters. They agree on Ch 5 *Encoding and Evolution*
   and Ch 10 *Consistency and Consensus*, so the +1 shift from old-Ch 2 onward is solid;
   the chapter count, the *Partitioning*→*Sharding* rename, and the Part III split are not.
   O'Reilly's own TOC page returns 403 to automated fetch, and at least one blog claiming a
   2e TOC actually reprints the 1e structure. **Confirm the full 2e chapter list from a real
   copy before executing §1 Step 2** — both Option A and Option B are only as good as that
   list. The Step 1 edition stamp needs none of this and can proceed today.
4. **Whether the "added material under a book heading" lapse recurs in UDS parts 2–5.**
   DDIA, both SDI volumes, and both ML books were checked for it and came back clean — they
   add material but disclose it. UDS is the outlier and only its first file could be
   checked. Wave 1 item 6 exists to answer this.
5. **The three-books-that-cannot-be-fully-checked problem, generally.** DDIA 1e, both SDI
   volumes, MLSDI, and UDS are all paywalled; DMLS and Larson's *Staff Engineer* are the
   only titles in scope with freely-readable full text. Every fidelity verdict in this
   report therefore rests on published section-level TOCs and third-party chapter notes,
   which reliably catch a *missing or invented section* and reliably miss a *subtly wrong
   sentence inside a correct section*. If the owner wants claim-level certainty rather than
   structural certainty, the blocker is access to the books, not agent time — and that is
   worth knowing before commissioning a deeper pass.
