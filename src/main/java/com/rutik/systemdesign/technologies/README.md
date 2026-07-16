# Technologies — Per-Technology Deep Dives

A canonical, senior-engineer-level module per major infrastructure technology — the
architecture, the internals (with real configs, not placeholders), the operational
playbook, and when NOT to reach for it. This section complements the concept-first
sections (`backend/`, `llm/`, `ml/`, `devops/`, `cuda/`), which teach the *pattern*
(orchestration, model serving); each module here teaches the *product* itself, and is
cross-linked back into the concept sections that use it as a worked example.

> **No runtime application** — all content is Markdown with annotated real-world
> config blocks and command output.

---

## 1. Section Overview

This section covers:

- **Workflow Orchestration** — Apache Airflow: the scheduler loop, executor models
  (Local/Celery/Kubernetes), DAG authoring, deferrable operators, backfills, and
  scheduler high availability.
- **GPU Model Serving** — NVIDIA Triton Inference Server: the model repository layout,
  `config.pbtxt`, multi-framework backends, dynamic batching, ensembles/BLS, and
  `perf_analyzer`-driven capacity planning.

**Primary stack:** Apache Airflow 3.0.x; NVIDIA Triton Inference Server (version tagged
per NGC container release inside the module). See each module's §1 for the exact
version studied and inline tags for version-specific features.

See [`CLAUDE.md`](CLAUDE.md) for the scope & non-overlap boundary (why Kafka and vLLM
are *not* here), the module template, and how to add a new technology.

---

## 2. Module Table

| # | Module | Category | Difficulty | Key Topics |
|---|--------|----------|-----------|-----------|
| 1 | [apache_airflow](apache_airflow/README.md) | Workflow Orchestration | Advanced | Scheduler loop, executors, deferrables, backfills, HA scheduler, Airflow 2→3 migration |
| 2 | [nvidia_triton_inference_server](nvidia_triton_inference_server/README.md) | GPU Model Serving | Advanced | Model repository + `config.pbtxt`, backends, dynamic batching, ensembles/BLS, `perf_analyzer` |

---

## 3. Learning Path

The two modules are **independent** — neither depends on the other, so study order is
a matter of which domain you need first, not a hard prerequisite chain. Suggested
order for a generalist: **apache_airflow → nvidia_triton_inference_server** (batch
orchestration is the more common first infra-technology interview probe; GPU serving
follows once you're touching ML/LLM systems).

```
apache_airflow  (workflow orchestration)
       |
       v  (independent — no hard dependency; suggested order only)
nvidia_triton_inference_server  (GPU model serving)
```

---

## Learning Paths

This section is small by design — 2 modules, each already scoped to what a senior
engineer needs to operate the technology in production. There is **no Full/Interview
toggle yet**: the browser learning game's Study view only shows that toggle for
sections with a `STUDY_PATHS` entry, and a meaningful interview-vs-full cut doesn't
exist at 2 modules. See [`CLAUDE.md`](CLAUDE.md) "Learning Paths (Full-only for now)"
for the threshold (≥4 modules) at which an Interview-Specific Path gets added.

### Full Path (2 modules)

The complete curriculum in the order above — see [Learning Path](#3-learning-path).
Both modules in full: every layer of Airflow's scheduler and executor internals, and
every layer of Triton's model repository, backend, and batching internals.

---

## Knowledge-Question Map

The highest-frequency *technology-specific* interview questions mapped to the module
that answers them.

| Interview question | Where the answer lives |
|--------------------|------------------------|
| Why did un-pausing a DAG trigger hundreds of runs at once? | [apache_airflow](apache_airflow/README.md) |
| What's the difference between the Local, Celery, and Kubernetes executors, and when do you pick each? | [apache_airflow](apache_airflow/README.md) |
| How do deferrable operators free up a worker slot while waiting on an external event? | [apache_airflow](apache_airflow/README.md) |
| How does Airflow's scheduler achieve high availability, and what changed between Airflow 2 and 3? | [apache_airflow](apache_airflow/README.md) |
| Why autoscale GPU inference on queue time, not GPU utilization? | [nvidia_triton_inference_server](nvidia_triton_inference_server/README.md) |
| How does dynamic batching in Triton trade latency for throughput, and what does `max_queue_delay_microseconds` control? | [nvidia_triton_inference_server](nvidia_triton_inference_server/README.md) |
| What's the difference between an ensemble and a Business Logic Scripting (BLS) pipeline in Triton? | [nvidia_triton_inference_server](nvidia_triton_inference_server/README.md) |
| How do you use `perf_analyzer` to find the throughput/latency knee for a deployed model? | [nvidia_triton_inference_server](nvidia_triton_inference_server/README.md) |

---

## Study Plan

A 2-week plan. Each week pairs the module with related concept-section material to
rehearse how the technology fits into a broader system design answer.

| Week | Focus | Module | Rehearse with |
|------|-------|--------|---------------|
| 1 | Orchestration | [apache_airflow](apache_airflow/README.md) | [ml/data_pipelines_and_processing](../ml/data_pipelines_and_processing/README.md) (what Airflow is scheduling), [ml/mlops_and_ci_cd](../ml/mlops_and_ci_cd/README.md) (where Airflow sits in a training/retraining pipeline) |
| 2 | GPU Serving | [nvidia_triton_inference_server](nvidia_triton_inference_server/README.md) | [llm/inference_engines](../llm/inference_engines/README.md) (Triton vs vLLM/TGI-style serving stacks), [devops/ml_platform_and_gpu_infrastructure](../devops/ml_platform_and_gpu_infrastructure/README.md) (the GPU platform Triton runs on) |

---

## Version Notes

| Technology | Version Studied | Key Notes |
|-----------|-----------------|-----------|
| Apache Airflow | 3.0.x | React-based UI, DAG versioning, and the task-execution API server are new in 3.0; deferrable operators (Airflow 2.7+) and the Celery/Kubernetes executors are tagged inline where behavior differs from 2.x |
| NVIDIA Triton Inference Server | Tagged per NGC container release inline (e.g. `24.05-py3`) | Triton does not gate features by simple semver — the module tags each version-specific flag/default against the NGC release it shipped in |

---

## See also

- [`ml/data_pipelines_and_processing`](../ml/data_pipelines_and_processing/README.md) — the data-pipeline concepts Airflow orchestrates.
- [`ml/mlops_and_ci_cd`](../ml/mlops_and_ci_cd/README.md) — where a workflow orchestrator fits in the training/deployment lifecycle.
- [`llm/inference_engines`](../llm/inference_engines/README.md) and [`llm/vllm_deep_dive`](../llm/vllm_deep_dive/README.md) — LLM-specific serving engines, contrasted with Triton's general-purpose multi-framework serving.
- [`devops/ml_platform_and_gpu_infrastructure`](../devops/ml_platform_and_gpu_infrastructure/README.md) — the GPU cluster and platform layer both Airflow (GPU-backed tasks) and Triton (GPU inference) run on.
- [`cuda/triton_and_kernel_dsls`](../cuda/triton_and_kernel_dsls/README.md) — OpenAI Triton, the GPU kernel DSL — an unrelated product that shares a name with NVIDIA Triton Inference Server; see the disambiguation note in [`CLAUDE.md`](CLAUDE.md).
- [`backend/kafka_deep_dive`](../backend/kafka_deep_dive/README.md) — an example of a technology already owned by a concept section (why it isn't duplicated here).
