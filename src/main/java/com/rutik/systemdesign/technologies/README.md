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
- **CPU/Edge Inference & Model Optimization** — Intel OpenVINO: the Runtime and device
  plugins (CPU/GPU/NPU), the IR format and `ovc`/`convert_model`, AUTO/HETERO virtual
  devices and performance hints, async infer requests, NNCF INT8/INT4 quantization,
  model caching, `PrePostProcessor`, and OpenVINO Model Server.

**Primary stack:** Apache Airflow 3.0.x; NVIDIA Triton Inference Server (version tagged
per NGC container release inside the module); Intel OpenVINO 2025.2 (API 2.0 `ov::`
era). See each module's §1 for the exact version studied and inline tags for version-
specific features.

See [`CLAUDE.md`](CLAUDE.md) for the scope & non-overlap boundary (why Kafka and vLLM
are *not* here), the module template, and how to add a new technology.

---

## 2. Module Table

| # | Module | Category | Difficulty | Key Topics |
|---|--------|----------|-----------|-----------|
| 1 | [apache_airflow](apache_airflow/README.md) | Workflow Orchestration | Advanced | Scheduler loop, executors, deferrables, backfills, HA scheduler, Airflow 2→3 migration |
| 2 | [nvidia_triton_inference_server](nvidia_triton_inference_server/README.md) | GPU Model Serving | Advanced | Model repository + `config.pbtxt`, backends, dynamic batching, ensembles/BLS, `perf_analyzer` |
| 3 | [intel_openvino](intel_openvino/README.md) | CPU/Edge Inference & Optimization | Advanced | IR + `ovc`/`convert_model`, device plugins (CPU/GPU/NPU), AUTO/HETERO + performance hints, async infer requests, NNCF INT8/INT4, model caching, OVMS, `openvino-genai` |

---

## 3. Learning Path

The three modules are **independent** — none depends on the others, so study order is
a matter of which domain you need first, not a hard prerequisite chain. Suggested
order for a generalist: **apache_airflow → nvidia_triton_inference_server →
intel_openvino** (batch orchestration is the more common first infra-technology
interview probe; GPU serving follows once you're touching ML/LLM systems; OpenVINO
lands last as the CPU/edge counterpoint to Triton's GPU serving, so the two serving
modules read as a contrast pair).

```
apache_airflow  (workflow orchestration)
       |
       v  (independent — no hard dependency; suggested order only)
nvidia_triton_inference_server  (GPU model serving)
       |
       v  (independent — CPU/edge counterpoint to GPU serving)
intel_openvino  (CPU/edge inference & model optimization)
```

---

## Learning Paths

This section is small by design — 3 modules, each already scoped to what a senior
engineer needs to operate the technology in production. There is **no Full/Interview
toggle yet**: the browser learning game's Study view only shows that toggle for
sections with a `STUDY_PATHS` entry, and a meaningful interview-vs-full cut doesn't
exist at 3 modules. See [`CLAUDE.md`](CLAUDE.md) "Learning Paths (Full-only for now)"
for the threshold (≥4 modules) at which an Interview-Specific Path gets added.

### Full Path (3 modules)

The complete curriculum in the order above — see [Learning Path](#3-learning-path).
All three modules in full: every layer of Airflow's scheduler and executor internals,
every layer of Triton's model repository, backend, and batching internals, and every
layer of OpenVINO's Runtime, device plugins, hints/streams model, and NNCF
quantization.

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
| Why is OpenVINO's THROUGHPUT hint slower for a single request than LATENCY mode? | [intel_openvino](intel_openvino/README.md) |
| What changed between Model Optimizer (`mo`) and `ovc`/`convert_model`, and where did `--mean_values` go? | [intel_openvino](intel_openvino/README.md) |
| Why did INT8 quantization tank accuracy, and how does accuracy-aware quantization fix it? | [intel_openvino](intel_openvino/README.md) |
| When do you serve with OpenVINO Model Server versus embedding the runtime versus Triton? | [intel_openvino](intel_openvino/README.md) |
| Does OpenVINO need a GPU, and can it run on AMD CPUs or NVIDIA GPUs? | [intel_openvino](intel_openvino/README.md) |

---

## Study Plan

A 3-week plan. Each week pairs the module with related concept-section material to
rehearse how the technology fits into a broader system design answer.

| Week | Focus | Module | Rehearse with |
|------|-------|--------|---------------|
| 1 | Orchestration | [apache_airflow](apache_airflow/README.md) | [ml/data_pipelines_and_processing](../ml/data_pipelines_and_processing/README.md) (what Airflow is scheduling), [ml/mlops_and_ci_cd](../ml/mlops_and_ci_cd/README.md) (where Airflow sits in a training/retraining pipeline) |
| 2 | GPU Serving | [nvidia_triton_inference_server](nvidia_triton_inference_server/README.md) | [llm/inference_engines](../llm/inference_engines/README.md) (Triton vs vLLM/TGI-style serving stacks), [devops/ml_platform_and_gpu_infrastructure](../devops/ml_platform_and_gpu_infrastructure/README.md) (the GPU platform Triton runs on) |
| 3 | CPU/Edge Serving & Optimization | [intel_openvino](intel_openvino/README.md) | [llm/optimization_and_quantization](../llm/optimization_and_quantization/README.md) (the quantization concepts NNCF implements), [ml/gpu_and_hardware_optimization](../ml/gpu_and_hardware_optimization/README.md) (VNNI/AMX/XMX vs Tensor Cores), [llm/vllm_deep_dive](../llm/vllm_deep_dive/README.md) (the GPU LLM-serving contrast to OpenVINO's CPU/edge LLM path) |

---

## Version Notes

| Technology | Version Studied | Key Notes |
|-----------|-----------------|-----------|
| Apache Airflow | 3.0.x | React-based UI, DAG versioning, and the task-execution API server are new in 3.0; deferrable operators (Airflow 2.7+) and the Celery/Kubernetes executors are tagged inline where behavior differs from 2.x |
| NVIDIA Triton Inference Server | Tagged per NGC container release inline (e.g. `24.05-py3`) | Triton does not gate features by simple semver — the module tags each version-specific flag/default against the NGC release it shipped in |
| Intel OpenVINO | 2025.2 | API 2.0 (`ov::` / top-level `openvino` package) — the legacy `InferenceEngine::` API was removed in 2024.0 and the Model Optimizer (`mo`) CLI in 2025.0; NPU plugin is 2023.2+, `openvino-genai` 2024.2+. Features are tagged inline against the release they landed in |

---

## See also

- [`ml/data_pipelines_and_processing`](../ml/data_pipelines_and_processing/README.md) — the data-pipeline concepts Airflow orchestrates.
- [`ml/mlops_and_ci_cd`](../ml/mlops_and_ci_cd/README.md) — where a workflow orchestrator fits in the training/deployment lifecycle.
- [`llm/inference_engines`](../llm/inference_engines/README.md) and [`llm/vllm_deep_dive`](../llm/vllm_deep_dive/README.md) — LLM-specific serving engines, contrasted with Triton's general-purpose multi-framework serving and OpenVINO's CPU/edge LLM path.
- [`llm/optimization_and_quantization`](../llm/optimization_and_quantization/README.md) — the quantization concepts OpenVINO's NNCF implements (INT8 PTQ, INT4 weight compression).
- [`ml/gpu_and_hardware_optimization`](../ml/gpu_and_hardware_optimization/README.md) — the hardware-acceleration contrast (Intel VNNI/AMX/XMX vs NVIDIA Tensor Cores) behind OpenVINO's precision choices.
- [`devops/ml_platform_and_gpu_infrastructure`](../devops/ml_platform_and_gpu_infrastructure/README.md) — the GPU cluster and platform layer both Airflow (GPU-backed tasks) and Triton (GPU inference) run on.
- [`cuda/triton_and_kernel_dsls`](../cuda/triton_and_kernel_dsls/README.md) — OpenAI Triton, the GPU kernel DSL — an unrelated product that shares a name with NVIDIA Triton Inference Server; see the disambiguation note in [`CLAUDE.md`](CLAUDE.md).
- [`backend/kafka_deep_dive`](../backend/kafka_deep_dive/README.md) — an example of a technology already owned by a concept section (why it isn't duplicated here).
