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
- **Durable Execution** — Temporal: the two-plane split where the Service never runs
  your code, the event history and replay determinism, activities and their four
  timeouts, signals/queries/updates, Continue-As-New against the 51,200-event limit,
  versioning by patching or Pinned Worker Deployments, and the immutable shard count.
- **GPU Model Serving** — NVIDIA Triton Inference Server: the model repository layout,
  `config.pbtxt`, multi-framework backends, dynamic batching, ensembles/BLS, and
  `perf_analyzer`-driven capacity planning.
- **CPU/Edge Inference & Model Optimization** — Intel OpenVINO: the Runtime and device
  plugins (CPU/GPU/NPU), the IR format and `ovc`/`convert_model`, AUTO/HETERO virtual
  devices and performance hints, async infer requests, NNCF INT8/INT4 quantization,
  model caching, `PrePostProcessor`, and OpenVINO Model Server.

**Primary stack:** Apache Airflow 3.3.0; Temporal Server 1.31.2 (with Go 1.47.0,
Java 1.37.0, Python 1.31.0 and TypeScript 1.21.1 SDKs, and `temporal` CLI 1.8.2);
NVIDIA Triton Inference Server (version tagged per NGC container release inside the
module); Intel OpenVINO 2026.2 (API 2.0 `ov::` era). See each module's §1 for the exact
version studied and inline tags for version-specific features.

See [`CLAUDE.md`](CLAUDE.md) for the scope & non-overlap boundary (why Kafka and vLLM
are *not* here), the module template, and how to add a new technology.

---

## 2. Module Table

| # | Module | Category | Difficulty | Key Topics |
|---|--------|----------|-----------|-----------|
| 1 | [apache_airflow](apache_airflow/apache_airflow.md) | Workflow Orchestration | Advanced | Scheduler loop, executors, deferrables, backfills, HA scheduler, Airflow 2→3 migration |
| 2 | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) | Durable Execution | Advanced | Event history + replay determinism, the four activity timeouts, signals/queries/updates, Continue-As-New, patching vs Pinned Worker Deployments, immutable `numHistoryShards` |
| 3 | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) | GPU Model Serving | Advanced | Model repository + `config.pbtxt`, backends, dynamic batching, ensembles/BLS, `perf_analyzer` |
| 4 | [intel_openvino](intel_openvino/intel_openvino.md) | CPU/Edge Inference & Optimization | Advanced | IR + `ovc`/`convert_model`, device plugins (CPU/GPU/NPU), AUTO/HETERO + performance hints, async infer requests, NNCF INT8/INT4, model caching, OVMS, `openvino-genai` |

---

## 3. Learning Path

The four modules are **independent** — none depends on the others, so study order is a
matter of which domain you need first, not a hard prerequisite chain. They do, however,
read best as **two contrast pairs**, and `STUDY_ORDER.technologies` is arranged that
way:

- **Orchestration pair — apache_airflow ↔ temporal_durable_execution.** Both are called
  "orchestrators" and are constantly confused in interviews. Airflow schedules
  *pipelines over calendar time* with data intervals and backfills; Temporal runs *one
  durable execution per business entity* for minutes to months. Temporal's §8 and §9 are
  largely "why this is not Airflow", so reading them back to back is what makes the
  distinction stick.
- **Serving pair — nvidia_triton_inference_server ↔ intel_openvino.** GPU model serving
  against CPU/edge inference and model optimization, sharing a vocabulary (batching,
  instances, throughput/latency knees) with opposite hardware assumptions.

```
apache_airflow  (batch orchestration: DAG runs per data interval)
       |
       v  (contrast pair — same word "orchestrator", opposite unit of work)
temporal_durable_execution  (durable execution: one execution per entity)
       |
       v  (independent — different domain entirely)
nvidia_triton_inference_server  (GPU model serving)
       |
       v  (contrast pair — CPU/edge counterpoint to GPU serving)
intel_openvino  (CPU/edge inference & model optimization)
```

---

## Learning Paths

This section is small by design — 4 modules, each already scoped to what a senior
engineer needs to operate the technology in production. There is **no Senior/Principal
tier toggle yet**: the browser learning game's Study view only shows tier tabs for
sections present in the derived `paths.json`, and while this section has now crossed
the documented 4-module threshold, the tier decision is **deliberately deferred** until
the remaining planned technology pages land. See
[`CLAUDE.md`](CLAUDE.md) "Learning Paths (Full-only — tiers deliberately deferred past
the threshold)" for the reasoning and the mechanics.

### Full Path (4 modules)

The complete curriculum in the order above — see [Learning Path](#3-learning-path).
All four modules in full: every layer of Airflow's scheduler and executor internals,
every layer of Temporal's event history, determinism constraint, timeout and failure
taxonomy, versioning strategies and shard sizing, every layer of Triton's model
repository, backend, and batching internals, and every layer of OpenVINO's Runtime,
device plugins, hints/streams model, and NNCF quantization.

---

## Knowledge-Question Map

The highest-frequency *technology-specific* interview questions mapped to the module
that answers them.

| Interview question | Where the answer lives |
|--------------------|------------------------|
| Why did un-pausing a DAG trigger hundreds of runs at once? | [apache_airflow](apache_airflow/apache_airflow.md) |
| What's the difference between the Local, Celery, and Kubernetes executors, and when do you pick each? | [apache_airflow](apache_airflow/apache_airflow.md) |
| How do deferrable operators free up a worker slot while waiting on an external event? | [apache_airflow](apache_airflow/apache_airflow.md) |
| How does Airflow's scheduler achieve high availability, and what changed between Airflow 2 and 3? | [apache_airflow](apache_airflow/apache_airflow.md) |
| You deployed a change and every in-flight workflow stopped progressing, but nothing is marked failed — what happened? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| Why can't workflow code call `time.Now()` or iterate a Go map, and what replaces each? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| Which of the four activity timeouts do engineers get wrong most often, and which one must you always set? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| Why can't you change `numHistoryShards` after a Temporal cluster is built, and what do you do at the ceiling? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| When would you choose Airflow over Temporal, and when the reverse? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| Why autoscale GPU inference on queue time, not GPU utilization? | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) |
| How does dynamic batching in Triton trade latency for throughput, and what does `max_queue_delay_microseconds` control? | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) |
| What's the difference between an ensemble and a Business Logic Scripting (BLS) pipeline in Triton? | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) |
| How do you use `perf_analyzer` to find the throughput/latency knee for a deployed model? | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) |
| Why is OpenVINO's THROUGHPUT hint slower for a single request than LATENCY mode? | [intel_openvino](intel_openvino/intel_openvino.md) |
| What changed between Model Optimizer (`mo`) and `ovc`/`convert_model`, and where did `--mean_values` go? | [intel_openvino](intel_openvino/intel_openvino.md) |
| Why did INT8 quantization tank accuracy, and how does accuracy-aware quantization fix it? | [intel_openvino](intel_openvino/intel_openvino.md) |
| When do you serve with OpenVINO Model Server versus embedding the runtime versus Triton? | [intel_openvino](intel_openvino/intel_openvino.md) |
| Does OpenVINO need a GPU, and can it run on AMD CPUs or NVIDIA GPUs? | [intel_openvino](intel_openvino/intel_openvino.md) |

---

## Study Plan

A 4-week plan. Each week pairs the module with related concept-section material to
rehearse how the technology fits into a broader system design answer.

| Week | Focus | Module | Rehearse with |
|------|-------|--------|---------------|
| 1 | Orchestration | [apache_airflow](apache_airflow/apache_airflow.md) | [ml/data_pipelines_and_processing](../ml/data_pipelines_and_processing/data_pipelines_and_processing.md) (what Airflow is scheduling), [ml/mlops_and_ci_cd](../ml/mlops_and_ci_cd/mlops_and_ci_cd.md) (where Airflow sits in a training/retraining pipeline) |
| 2 | Durable Execution | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) | [backend/distributed_transactions_and_consistency](../backend/distributed_transactions_and_consistency/distributed_transactions_and_consistency.md) and [hld/distributed_transactions](../hld/distributed_transactions/distributed_transactions.md) (the saga and compensation theory Temporal's §6.15 applies rather than re-teaches), [backend/event_sourcing_and_cqrs](../backend/event_sourcing_and_cqrs/event_sourcing_and_cqrs.md) (the log-as-source-of-truth idea behind the event history), [llm/agents_and_tool_use/durable_long_running_agents.md](../llm/agents_and_tool_use/durable_long_running_agents.md) (the durable-agent pattern Temporal now hosts) |
| 3 | GPU Serving | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) | [llm/inference_engines](../llm/inference_engines/inference_engines.md) (Triton vs vLLM/TGI-style serving stacks), [devops/ml_platform_and_gpu_infrastructure](../devops/ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) (the GPU platform Triton runs on) |
| 4 | CPU/Edge Serving & Optimization | [intel_openvino](intel_openvino/intel_openvino.md) | [llm/optimization_and_quantization](../llm/optimization_and_quantization/optimization_and_quantization.md) (the quantization concepts NNCF implements), [ml/gpu_and_hardware_optimization](../ml/gpu_and_hardware_optimization/gpu_and_hardware_optimization.md) (VNNI/AMX/XMX vs Tensor Cores), [llm/vllm_deep_dive](../llm/vllm_deep_dive/vllm_deep_dive.md) (the GPU LLM-serving contrast to OpenVINO's CPU/edge LLM path) |

---

## Version Notes

| Technology | Version Studied | Key Notes |
|-----------|-----------------|-----------|
| Apache Airflow | 3.3.0 | React-based UI, DAG versioning, and the task-execution API server are new in 3.0; deferrable operators (Airflow 2.7+) and the Celery/Kubernetes executors are tagged inline where behavior differs from 2.x |
| Temporal | Server 1.31.2 (SDKs: Go 1.47.0, Java 1.37.0, Python 1.31.0, TypeScript 1.21.1, .NET 1.17.0; CLI 1.8.2) | MIT throughout. `[1.31]` brought Worker Deployment APIs to GA, Task Queue Priority and Fairness to GA, Nexus on by default with token-based routing, and `Principal` attribution on history events; the legacy Worker Versioning v1/v2 APIs are deprecated in 1.31 and removed in 1.32. Update-with-Start went GA in `[1.28]`. Core schema floor is MySQL/PostgreSQL v1.19 with Cassandra 5.0.4+; visibility schema is Elasticsearch v14 |
| NVIDIA Triton Inference Server | Tagged per NGC container release inline (e.g. `24.05-py3`) | Triton does not gate features by simple semver — the module tags each version-specific flag/default against the NGC release it shipped in |
| Intel OpenVINO | 2026.2 | API 2.0 (`ov::` / top-level `openvino` package) — the legacy `InferenceEngine::` API was removed in 2024.0 and the Model Optimizer (`mo`) CLI in 2025.0; NPU plugin is 2023.2+, `openvino-genai` 2024.2+. Features are tagged inline against the release they landed in |

---

## Technology Knowledge Bank (`tech_bank/`)

`tech_bank/` is **data, not a module** — the authored source of the game's Technologies
screen (`game/tech_index.json`, generated and gitignored). One record per tool: a one-line
summary, its kind, its language binding, and every problem-role it plays with a weight.
It is excluded from the question bank and from the reader's module tree by exact path;
see [`CLAUDE.md`](CLAUDE.md) for the record contract.

| File | What it holds |
|------|---------------|
| [`tech_bank/tech_bank.md`](tech_bank/tech_bank.md) | The taxonomy — 6 kinds, 8 language tokens, 18 tiers, 95 roles |
| [`tech_bank/caching.md`](tech_bank/caching.md) · [`data-stores.md`](tech_bank/data-stores.md) · [`data-access.md`](tech_bank/data-access.md) · [`data-movement.md`](tech_bank/data-movement.md) | Storage and data movement |
| [`tech_bank/runtime-systems.md`](tech_bank/runtime-systems.md) · [`apis-frameworks.md`](tech_bank/apis-frameworks.md) · [`devtools.md`](tech_bank/devtools.md) · [`platform-delivery.md`](tech_bank/platform-delivery.md) | Language runtimes, frameworks, build and delivery |
| [`tech_bank/traffic-edge.md`](tech_bank/traffic-edge.md) · [`observability.md`](tech_bank/observability.md) · [`security.md`](tech_bank/security.md) · [`search-retrieval.md`](tech_bank/search-retrieval.md) | Edge, operations, security, retrieval |
| [`tech_bank/gpu.md`](tech_bank/gpu.md) · [`model-training.md`](tech_bank/model-training.md) · [`inference.md`](tech_bank/inference.md) · [`ml-lifecycle.md`](tech_bank/ml-lifecycle.md) · [`applied-ml.md`](tech_bank/applied-ml.md) · [`llm-apps.md`](tech_bank/llm-apps.md) | GPU, ML and LLM stack |

---

## See also

- [`ml/data_pipelines_and_processing`](../ml/data_pipelines_and_processing/data_pipelines_and_processing.md) — the data-pipeline concepts Airflow orchestrates.
- [`backend/distributed_transactions_and_consistency`](../backend/distributed_transactions_and_consistency/distributed_transactions_and_consistency.md) and [`hld/distributed_transactions`](../hld/distributed_transactions/distributed_transactions.md) — the saga and compensation theory Temporal implements as ordinary `try`/`catch` plus a compensation stack.
- [`backend/event_sourcing_and_cqrs`](../backend/event_sourcing_and_cqrs/event_sourcing_and_cqrs.md) — the append-only-log-as-truth idea behind Temporal's event history, and the source of the *temporal queries* name collision.
- [`llm/agents_and_tool_use/durable_long_running_agents.md`](../llm/agents_and_tool_use/durable_long_running_agents.md) — the durable-agent pattern; that sub-file owns the agent shape, `temporal_durable_execution` owns the product.
- [`ml/mlops_and_ci_cd`](../ml/mlops_and_ci_cd/mlops_and_ci_cd.md) — where a workflow orchestrator fits in the training/deployment lifecycle.
- [`llm/inference_engines`](../llm/inference_engines/inference_engines.md) and [`llm/vllm_deep_dive`](../llm/vllm_deep_dive/vllm_deep_dive.md) — LLM-specific serving engines, contrasted with Triton's general-purpose multi-framework serving and OpenVINO's CPU/edge LLM path.
- [`llm/optimization_and_quantization`](../llm/optimization_and_quantization/optimization_and_quantization.md) — the quantization concepts OpenVINO's NNCF implements (INT8 PTQ, INT4 weight compression).
- [`ml/gpu_and_hardware_optimization`](../ml/gpu_and_hardware_optimization/gpu_and_hardware_optimization.md) — the hardware-acceleration contrast (Intel VNNI/AMX/XMX vs NVIDIA Tensor Cores) behind OpenVINO's precision choices.
- [`devops/ml_platform_and_gpu_infrastructure`](../devops/ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) — the GPU cluster and platform layer both Airflow (GPU-backed tasks) and Triton (GPU inference) run on.
- [`cuda/triton_and_kernel_dsls`](../cuda/triton_and_kernel_dsls/triton_and_kernel_dsls.md) — OpenAI Triton, the GPU kernel DSL — an unrelated product that shares a name with NVIDIA Triton Inference Server; see the disambiguation note in [`CLAUDE.md`](CLAUDE.md).
- [`backend/kafka_deep_dive`](../backend/kafka_deep_dive/kafka_deep_dive.md) — an example of a technology already owned by a concept section (why it isn't duplicated here).
