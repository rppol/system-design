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
[`backend/kafka_deep_dive`](../backend/kafka_deep_dive/README.md) — do not add
`technologies/apache_kafka`; vLLM is owned by
[`llm/vllm_deep_dive`](../llm/vllm_deep_dive/README.md) — do not add
`technologies/vllm`. This section is for technologies that don't already have a
canonical home — an orchestrator like Airflow or an inference server like Triton has
no single owning section, so it lives here instead.

**Naming convention:** `<vendor>_<product>`, lowercase snake_case. Disambiguate
collisions explicitly in the module's intro — e.g. `nvidia_triton_inference_server`
(NVIDIA's model-serving server) vs [`cuda/triton_and_kernel_dsls`](../cuda/triton_and_kernel_dsls/README.md)
(OpenAI Triton, the GPU kernel DSL). Same word, unrelated products; both files must
say so on first mention.

---

## Module List — 3 Modules

| Dir | Category | Key Concepts | Version Studied |
|-----|----------|-------------|-----------------|
| [`apache_airflow/`](apache_airflow/README.md) | Workflow orchestration | Scheduler loop, executors (Local/Celery/Kubernetes), DAGs, deferrable operators, backfills, HA scheduler | Airflow 3.0.x |
| [`nvidia_triton_inference_server/`](nvidia_triton_inference_server/README.md) | GPU model serving | Model repository, `config.pbtxt`, backends (TensorRT/ONNX/PyTorch/Python), dynamic batching, ensembles/BLS, `perf_analyzer` | NGC release studied inline per module |
| [`intel_openvino/`](intel_openvino/README.md) | CPU/edge inference & model optimization | `ov::Core` + device plugins (CPU/GPU/NPU), IR (`.xml`/`.bin`), `ovc`/`convert_model`, AUTO/HETERO + performance hints, async infer requests, NNCF INT8/INT4, model caching, `PrePostProcessor`, OVMS, `openvino-genai` | OpenVINO 2025.2 |

---

## Module Template

Every module README follows the standard 14-section template (see root `CLAUDE.md`):

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
template, `case_studies/README.md` learning-path index) only once a module family
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

There is **no `STUDY_PATHS.technologies` entry** in `game/app.js` — with only 3
modules there's still no meaningful interview-vs-full cut to make. The Study view's
Full/Interview toggle **auto-hides** for any section absent from `STUDY_PATHS` (the
`book` section is the existing precedent for this — see `game/app.js` and
`game/CLAUDE.md`), so this is a correct, deliberate omission, not a gap. **Threshold
to add one:** once this section reaches **4 or more modules**, add an `interview:`
array to `STUDY_PATHS.technologies` in `game/app.js`, a matching "## Learning Paths"
cut in `README.md`, and update the toggle-exception language in `game/CLAUDE.md` and
`game/README.md` — all three in the same commit, per the dual-source rule other
sections follow.

---

## Deep-Dive Sub-Files (future)

Sub-files follow the repo-wide convention: `<module>/<topic>.md`, full 14-section
template, 15+ Q&As. `extract.py` auto-groups a sub-file under its parent module's
existing `STUDY_ORDER` position by directory — **no `STUDY_ORDER` change is needed**
when adding a sub-file, only when adding a new top-level module directory.

---

## How to Add a Technology

1. Create `<vendor>_<product>/README.md` — 14 canonical sections, 16 Q&As, version
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
