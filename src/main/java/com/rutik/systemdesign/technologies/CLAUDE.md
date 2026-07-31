# Technologies Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/technologies/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

This section holds **per-technology deep dives** — a canonical senior-engineer module
for a single major infrastructure technology (an orchestrator, an inference server, a
message broker, a workflow engine), covering its architecture, internals with real
configs, operations, and when NOT to reach for it. It complements the concept-first
sections (`backend/`, `llm/`, `ml/`, `devops/`, `cuda/`) which teach the *pattern*
(orchestration, model serving, event streaming); this section teaches the *product*.

---

## Scope & Non-Overlap Boundary

A technology earns a module here **only when no existing section already owns it**.
Before adding a module, check whether the technology is already the worked example in
another section's concept module — if so, cross-link into that module instead of
duplicating it here. Examples: Kafka is owned by
[`backend/kafka_deep_dive`](../backend/kafka_deep_dive/kafka_deep_dive.md) — do not add
`technologies/apache_kafka`; vLLM is owned by
[`llm/vllm_deep_dive`](../llm/vllm_deep_dive/vllm_deep_dive.md) — do not add
`technologies/vllm`. This section is for technologies that don't already have a
canonical home — an orchestrator like Airflow or an inference server like Triton has
no single owning section, so it lives here instead.

**Naming convention:** `<vendor>_<product>`, lowercase snake_case. Disambiguate
collisions explicitly in the module's intro — e.g. `nvidia_triton_inference_server`
(NVIDIA's model-serving server) vs [`cuda/triton_and_kernel_dsls`](../cuda/triton_and_kernel_dsls/triton_and_kernel_dsls.md)
(OpenAI Triton, the GPU kernel DSL). Same word, unrelated products; both files must
say so on first mention.

---

## Module List — 3 Modules

| Dir | Category | Key Concepts | Version Studied |
|-----|----------|-------------|-----------------|
| [`apache_airflow/`](apache_airflow/apache_airflow.md) | Workflow orchestration | Scheduler loop, executors (Local/Celery/Kubernetes), DAGs, deferrable operators, backfills, HA scheduler | Airflow 3.3.0 |
| [`nvidia_triton_inference_server/`](nvidia_triton_inference_server/nvidia_triton_inference_server.md) | GPU model serving | Model repository, `config.pbtxt`, backends (TensorRT/ONNX/PyTorch/Python), dynamic batching, ensembles/BLS, `perf_analyzer` | NGC release studied inline per module |
| [`intel_openvino/`](intel_openvino/intel_openvino.md) | CPU/edge inference & model optimization | `ov::Core` + device plugins (CPU/GPU/NPU), IR (`.xml`/`.bin`), `ovc`/`convert_model`, AUTO/HETERO + performance hints, async infer requests, NNCF INT8/INT4, model caching, `PrePostProcessor`, OVMS, `openvino-genai` | OpenVINO 2026.2 |

---

## Module Template

Every module page (`<module>/<module>.md`) follows the standard 14-section template (see root `CLAUDE.md`):

```
## 1. Concept Overview
## 2. Intuition
## 3. Core Principles
## 4. Types / Architectures / Strategies
## 5. Architecture Diagrams
## 6. How It Works — Detailed Mechanics
## 7. Real-World Examples
## 8. Tradeoffs
## 9. When to Use / When NOT to Use
## 10. Common Pitfalls
## 11. Technologies & Tools
## 12. Interview Questions with Answers
## 13. Best Practices
## 14. Case Study
```

**Technology-flavor interpretation** of four sections that differ from a pure-concept
module:

| Section | Technology-flavor interpretation |
|---------|-----------------------------------|
| §4 Types / Architectures / Strategies | Process/component taxonomy and deployment topologies — e.g. Airflow's webserver/scheduler/triton/workers/metadata-DB split under each executor; Triton's server process + backend model instances + model repository layout |
| §6 How It Works | Internals **with concrete configs** — an annotated `airflow.cfg` (real default values, not placeholders) or an annotated `config.pbtxt` (real default `max_batch_size`, instance groups, dynamic-batching timeouts) |
| §11 Technologies & Tools | Ecosystem & integrations — what plugs into this technology (Airflow: Kubernetes executor, Great Expectations, dbt; Triton: TensorRT, ONNX Runtime, Kubernetes + KServe/Triton Operator) |
| §14 Case Study | An **inline mini case study** (no `case_studies/` sub-directory at section launch — see below) |

**No `case_studies/` directory yet.** Unlike concept sections, this section launches
without a `case_studies/` sub-directory; §14 of each module carries a self-contained
mini case study instead. Add a `case_studies/` directory (11-section principal
template, `case_studies/case_studies.md` learning-path index) only once a module family
needs a case study too large for §14 — follow the root `CLAUDE.md` case-study rules
when that happens.

---

## Q&A Minimums

**16 Q&As minimum per module** — the repo floor is 15 (root `CLAUDE.md`); this section
standardizes one above the floor, and flagship modules go well past it when the
technology's surface demands it (the launch modules carry 22 and 30). Format from the start: `**Q: <question>?**` bolded
question prefixed `Q: `, plain-text answer, first sentence a self-contained direct
answer 15–220 characters (see root `CLAUDE.md` Interview Q&A Rules and the Game/Reader
Q&A extraction contract — the length window and bold-question rule are load-bearing
for `extract.py`, not cosmetic).

---

## Version Pinning

Every module states the **version studied** up front (in its intro, §1 Concept
Overview) and tags version-specific features inline as they come up — e.g.
`[Airflow 3.0+]` for the React-based UI and DAG versioning, `[Airflow 2.7+]` for
deferrable operators. Triton modules tag by NGC container release (e.g.
`[24.05-py3]`) since Triton doesn't use simple semver for feature gating. Never
describe a feature as current without naming the version it landed in — this section
covers fast-moving infrastructure products where defaults and flags change release to
release.

---

## Learning Paths (Full-only for now)

No module here carries a `<!-- study-paths -->` block, so `questions/paths.json` has no
`technologies` key and the section is Full-path only — with 3 modules there is still no
meaningful cut to make. The Study view's tier tabs **auto-hide** for any section absent
from the derived paths (`book` is the other such section), so this is a deliberate
omission, not a gap. **Threshold to add one:** once this section reaches **4 or more
modules**, decide the tiers — Senior (the craft: operate it, debug it) and Principal (the
judgment: adopt it or not, at what cost) are different cuts, not nested depths — then
write a `<!-- study-paths -->` block at the top of each participating module's page (`<module>.md`) naming the
files it contributes, paste an empty `<!-- study-path-table senior -->` /
`<!-- /study-path-table -->` marker pair into `README.md` where the table should sit
(placement is editorial; `--write-paths` only fills blocks that already exist), and run
`python3 extract.py --write-paths` to generate it. Also update the toggle-exception
language in `game/CLAUDE.md` and `game/README.md` in the same commit. There is **no
array in `app.js`** to add.

---

## `tech_bank/` — the technology knowledge bank (DATA, not a module)

`technologies/tech_bank/` is the **source of truth for `game/tech_index.json`**: what each
of the 3,809 indexed tools *is* and what problems it solves, independent of which module
teaches it. It is authored markdown, committed; the JSON is **generated and gitignored**,
regenerated by CI on every push exactly like `questions/*.json`, `paths.json`, the
relatedness graphs and the pre-rendered Mermaid `.mmz` assets.

**It is not a module and must never become one.** `extract.py` excludes it from BOTH walks
by exact relative path (`TECH_BANK_DIR` / `in_tech_bank()`, in `game/build_tech.py`), so nothing here is in
`STUDY_ORDER`, contributes a question, appears in the reader's module tree, or carries a
`<!-- study-paths -->` block. Three measured hazards make the exclusion load-bearing:

| If it were… | What happens | Loud? |
|---|---|---|
| a plain module dir under `technologies/` | lands in `index.files` with `moduleCounts: 0` → a **phantom module** in the reader tree, prev/next chain, command palette and Study skill tree | no — `--strict` exits 0 |
| a shard carrying a `## 11. Technologies & Tools` table | `build_tech_index()` indexes the bank **into itself** (3809/613 → 3810/614) | no |
| a file at the SECTION ROOT with a `## 12. Interview Questions` heading | `module` collapses to the 1-segment `technologies` → `WIRING ERROR: STUDY_ORDER gap` | yes — deploy dies |

Because the exclusion is silent by design, `build_tech_bank()` **fails the build under
`--strict` on any `## NN.` section-template heading** anywhere under `tech_bank/` — that
heading is the signature of someone mistaking this data directory for study content.

### Layout

```
tech_bank/
├── tech_bank.md      ← the TAXONOMY: 6 kinds, 8 language tokens, 18 tiers, 95 roles
├── caching.md        ← one shard per tier, named for the tier id
├── data-stores.md
└── …                 ← 18 shards
```

Shard by **primary tier** — the tier of the tool's FIRST role. Roles are weight-ascending
in all 3,809 records, so "first" is "best"; only 26 tools have a tie at weight 1. A tool
lives in exactly one shard and declares all of its roles there, so Redis is filed under
Caching and still carries its key-value, rate-limiting, broker and semantic-cache roles.
A record whose primary tier disagrees with its shard is a **warning**, not an error.

### The record contract — the `**Short:**` Q&A contract, applied to a tool

```markdown
### Redis
**Short:** In-memory key-value server used as distributed cache, session store, rate-limit counter and pub/sub bus.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @1, traffic-edge/rate-limiting-and-resilience @2

Redis holds its entire dataset in RAM and executes commands on a single thread, so every
command — including a multi-key Lua script — is atomic without you taking a lock.

Reach for it when the working set fits in memory and you want sub-millisecond reads.
```

- **`**Short:**` is REQUIRED and bounded 15–220 chars** — the same `SHORT_MIN`/`SHORT_MAX`
  constants the Q&A contract uses, reused rather than re-declared, for the same reason: it
  is the always-visible row line, so a bad value ships to the user either way.
- `**Kind:**` one kind id; `**Lang:**` comma-separated language tokens (`*` = polyglot, the
  literal JSON token, never a friendly alias); `**Roles:**` comma-separated
  `tier/role @weight`, weight `1|2|3`, best first. Every id must exist in `tech_bank.md`.
- The **field block is contiguous and starts on the line after the `###`**, so a bolded
  sentence inside a description can never be mistaken for a field.
- The paragraphs after the blank line are the **description**, revealed when the row is
  expanded. **Per-record opt-in**, exactly like `**Short:**` in a Q&A: a record without one
  emits no `d` key and renders as it does today, so the description pass is resumable
  shard by shard forever. Hard-wrapped lines inside a paragraph are joined with a space.
- **A description with no short line is FATAL. A short line with no description is the
  normal state** (and the permanent terminal state for the 1,112 `api` symbols).
- Descriptions are **escaped, not rendered** — no fences, lists, tables or links; that is
  a `--strict` failure. Backtick code spans are the one inline form to use.
- **Nothing but records after a shard's preamble.** A footer would be read as the last
  record's description.

Also fatal: an unknown `**Kind:**`, language token or role id, a malformed `@weight`, a
record with zero roles, a duplicate `###` name across shards, an unknown field name, and
— since 2026-07-31 — a **markdown heading or an emoji** inside a description. Both survive
the parser and ship as literal escaped text in the reader; `###` opens a new record so it
can never reach the check, but `##` and below can. The emoji range deliberately carves out
`U+2713..U+2718`, because the repo style guide asks for `✓`/`✗` and banning the whole
dingbats block would fail the build on content it requests.

Warn-only: an **orphan** record no module teaches (it can never render) and the shard/tier
mismatch above. `extract.py` prints coverage both ways every build.

**Deliberately NOT checked: whether a description merely restates its `**Short:**` line.**
Word-overlap between the two was tried at several thresholds and does not separate — the
house style opens by recapping the tool and then expands, so well-written records score the
same as the one genuine offender (five of seven hits were false positives on inspection).
It shipped as a warning nobody could act on, which is worse than no check. Catch it in
review.

### The name is the join key, so two exception tables exist

`tech_key()` in `extract.py` decides which written forms are the same tool. The record's
`###` name must match the DISPLAY name the index settles on, or the record orphans and its
description never renders. Two hand-kept tables handle what no rule can derive:

| Table | Problem | Example |
|---|---|---|
| `TECH_ALIASES` | many names, ONE product | `envoy proxy` -> `envoy`, `grafana loki` -> `loki`, `huggingface transformers` -> `transformers` |
| `TECH_HOMONYMS` | one name, TWO unrelated products | `medusa` in `llm/` -> `Medusa (speculative decoding)`, leaving `Medusa` to the Cassandra backup tool |

They cannot be one mechanism: the alias key is derivable from the string alone, the homonym
key needs an outside signal (the section that teaches it).

**A homonym needs BOTH halves of the fix.** Splitting the key yields two rows, but the
display name is voted on from the surface forms modules actually wrote — and both halves of
a homonym were written the same way. `_HOMONYM_LABELS` overrides that vote. Without it you
get two identically-named rows AND, because the bank lookup is keyed on display name, both
receive the same record: a wrong answer wearing the costume of a duplicate.

**After touching either table, check the coverage line both ways.** `bank coverage: N of N
indexed tools have a record; 0 bank records no module teaches` is the only thing that
notices a merge which left an old record stranded.

### Adding or editing a tool

1. Edit the shard for its primary tier (add the `###` record in alphabetical position).
2. Run `python3 extract.py --strict` from `game/`. Confirm the tool count, the module-file
   count and every per-section question count are unchanged — a change to any of those
   means the bank leaked into a walk.
3. `bash scripts/build_banks.sh` re-runs the round-trip guard: every record must still
   carry `k`/`r`/`l`/`s` and no key beyond those plus `d`.

Never hand-edit `game/tech_index.json` — it is overwritten on every build.

---

## Deep-Dive Sub-Files (future)

Sub-files follow the repo-wide convention: `<module>/<topic>.md`, full 14-section
template, 15+ Q&As. `extract.py` auto-groups a sub-file under its parent module's
existing `STUDY_ORDER` position by directory — **no `STUDY_ORDER` change is needed**
when adding a sub-file, only when adding a new top-level module directory.

---

## How to Add a Technology

1. Create `<vendor>_<product>/<vendor>_<product>.md` — 14 canonical sections, 16 Q&As, version
   studied stated up front.
2. **Append the slug to `STUDY_ORDER.technologies` in `game/app.js` in the same
   commit.** `extract.py --strict` runs in Pages CI and **fails the deploy** if a
   module that produced Q&As is missing from `STUDY_ORDER` — this is not optional
   housekeeping.
3. Update this file's Module List table and `technologies/README.md`'s Module Table /
   Learning Path / Knowledge-Question Map / Study Plan.
4. Update root `README.md`'s Technologies section and root `CLAUDE.md`'s section
   table row (module count).
5. Run `python3 extract.py --strict` locally from `game/` before committing, to catch
   wiring gaps before CI does.
6. Author diagrams via the `/mermaid-diagrams` skill (Mermaid is the default form for
   flows/topologies/lifecycles) and `/visual-intuition-diagrams` for anything Mermaid
   can't draw (constraint grids, alignment-critical layout, vector geometry) — e.g. an
   annotated `config.pbtxt` field-by-field breakdown.
