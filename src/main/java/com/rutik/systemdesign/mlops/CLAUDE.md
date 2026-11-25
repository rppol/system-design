# MLOps & ML Infra Section - CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/mlops/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest and status tracker: `README.md` "Build Manifest". Check it before starting a module.

---

## Why this section exists

The repo teaches the parts of an AI system in four places and the lifecycle between them in
none. `ml/` teaches models and classic MLOps (maturity levels, KFP, retraining triggers).
`llm/` teaches LLM techniques and tools (RAG, prompt management, inference engines, eval
design). `devops/` teaches the substrate (Kubernetes, GPU device plugins, DRA, MIG, FinOps).
`technologies/` holds per-product deep dives (Triton, OpenVINO, Airflow).

`mlops/` owns what crosses those lines: every artifact an AI feature ships (weights,
adapters, prompts, tool servers, indexes, eval sets, decision schemas) as a versioned,
gated, rolled-out and rolled-back release input, plus the serving, compute and operations
platform that runs them. It links to the existing pages for technique and substrate. It
does not re-teach them.

Placement is link-only. No existing module moves into `mlops/`. KServe, llm-d, Kueue,
Volcano and KAI Scheduler are owned here, so there is no `technologies/kserve`. Triton and
OpenVINO stay in `technologies/` as a recorded pre-mlops exception.

**GPU scheduling ownership (owner decision, 2026-11-25).** `mlops/` owns GPU schedulers and
quota policy (Kueue, Volcano, KAI Scheduler, gang admission, cohorts and borrowing, MIG versus
MPS versus time-slicing as a capacity policy) in `gpu_cluster_scheduling_and_quotas`, and
inference autoscaling (the engine signal that drives the scaler, KEDA and HPA configs for model
servers, capacity planning) in `inference_autoscaling_and_capacity_planning`. `devops/` keeps
the mechanism and the generic layer: the device plugin, DRA, MIG, MPS and time-slicing
mechanics, node provisioning, and generic autoscaling (HPA, VPA, Cluster Autoscaler, Karpenter,
generic KEDA). Never hand off scheduler policy or inference autoscaling to a `devops/` page;
link `devops/` only for the mechanism or the generic layer.

---

## Non-overlap boundary

Each module's §1 ends with a "Not here" paragraph that names these pages. If a subtopic is
listed in the right column, link to it. Do not repeat it.

| mlops module | Owned elsewhere (link, do not repeat) |
|---|---|
| `mlops_foundations_and_the_ai_lifecycle` | maturity levels, CI/CD strategy variants, retraining triggers: `ml/mlops_and_ci_cd/mlops_and_ci_cd.md` §4; the LLM serving stack: `llm/deployment_and_mlops/deployment_and_mlops.md` |
| `artifact_versioning_and_lineage` | experiment-tracking mechanics: `ml/experiment_tracking_and_versioning/experiment_tracking_and_versioning.md` |
| `dataset_and_label_versioning` | `ml/data_pipelines_and_processing/data_pipelines_and_processing.md`, `ml/active_learning_and_weak_supervision/active_learning_and_weak_supervision.md`, `llm/synthetic_data_generation/synthetic_data_generation.md`; feature-store design and point-in-time joins: `ml/ml_system_design/feature_store_design.md`, `ml/case_studies/cross_cutting/feature_store_and_point_in_time_correctness.md` |
| `prompt_lifecycle_and_versioning` | writing techniques: `llm/prompt_engineering/prompt_engineering.md`; templates, eval-gated CI code, alias hot-swap code: `llm/prompt_management_and_promptops/prompt_management_and_promptops.md` §6 |
| `model_registry_and_packaging` | stage and approval basics: `ml/mlops_and_ci_cd/mlops_and_ci_cd.md` §4 "Model Registry Governance"; MLflow internals: `ml/mlflow_deep_dive/mlflow_deep_dive.md` |
| `rag_and_vector_index_lifecycle` | `llm/rag_fundamentals/rag_fundamentals.md`, `database/vector_databases/vector_databases.md`, `llm/faiss_deep_dive/faiss_deep_dive.md` |
| `agent_tool_and_mcp_server_lifecycle` | `llm/mcp_model_context_protocol/mcp_server_building.md`, `devops/platform_engineering_and_idp/ai_workloads_and_agent_operations.md` |
| `ml_pipelines_and_orchestration` | KFP SDK v2 definition: `ml/mlops_and_ci_cd/mlops_and_ci_cd.md` §6; `technologies/apache_airflow/apache_airflow.md`; `technologies/temporal_durable_execution/temporal_durable_execution.md`; `ml/ml_system_design/training_pipeline_design.md` |
| `evaluation_gates_and_eval_sets` | `llm/evaluation_and_benchmarks/evaluation_and_benchmarks.md`, `llm/error_analysis_and_eval_design/error_analysis_and_eval_design.md`, `llm/llm_testing_strategies/llm_testing_strategies.md`; classic gate code: `ml/mlops_and_ci_cd/mlops_and_ci_cd.md` §6 "Model Performance Gate" |
| `statistics_of_release_gates.md` | general hypothesis testing: `ml/probability_and_statistics/probability_and_statistics.md`; online A/B statistics: `ml/ml_system_design/ab_testing_for_ml.md` |
| `continuous_training_and_refresh_policy` | trigger lists: `ml/mlops_and_ci_cd/mlops_and_ci_cd.md` §4 and §5; drift statistics: `ml/monitoring_and_drift_detection/monitoring_and_drift_detection.md`; flywheel design: `llm/data_flywheels_and_continuous_learning/data_flywheels_and_continuous_learning.md` |
| `ai_release_engineering` | generic Kubernetes strategies: `devops/deployment_strategies/deployment_strategies.md`; online-experiment basics: `ml/ml_system_design/ab_testing_for_ml.md`; traffic-split mechanics: `progressive_delivery_for_model_servers` |
| `experimentation_for_generative_features.md` | `ml/ml_system_design/ab_testing_for_ml.md`, `ml/case_studies/cross_cutting/experimentation_and_online_evaluation.md` |
| `deployment_targets_and_serving_patterns` | `backend/serving_ai_workloads/serving_ai_workloads.md`, `fastapi/serving_llm_and_ai_workloads/serving_llm_and_ai_workloads.md`; engine tuning: `inference_engine_operations_and_benchmarking` |
| `multi_region_and_multi_tenant_serving.md` | `hld/multi_region_and_data_residency/multi_region_and_data_residency.md`, `llm/case_studies/cross_cutting/multi_region_llm_topology.md` |
| `model_serving_platforms` | engine internals: `llm/inference_engines/inference_engines.md`, `llm/vllm_deep_dive/vllm_deep_dive.md`; Triton internals: `technologies/nvidia_triton_inference_server/nvidia_triton_inference_server.md` |
| `kserve_deep_dive.md` | Knative and operator basics: `devops/kubernetes_operators_and_crds/kubernetes_operators_and_crds.md` |
| `inference_engine_operations_and_benchmarking` | `llm/inference_engines/inference_engines.md`, `llm/vllm_deep_dive/vllm_deep_dive.md` (flag semantics); generic method: `backend/benchmarking_methodology/benchmarking_methodology.md` |
| `distributed_inference_and_disaggregation` | `llm/inference_and_decoding/kv_cache_optimization.md`, `technologies/nvidia_triton_inference_server/dynamo_and_the_nvidia_serving_stack.md` |
| `lora_adapter_serving_and_lifecycle` | `llm/fine_tuning/lora.md` |
| `model_storage_loading_and_cold_start` | `devops/container_runtimes_and_oci/oci_image_formats_and_lazy_pulling.md` |
| `inference_autoscaling_and_capacity_planning` | generic HPA, VPA, Cluster Autoscaler, Karpenter and KEDA mechanics: `devops/kubernetes_scheduling_and_autoscaling/kubernetes_scheduling_and_autoscaling.md`; GPU node-pool provisioning and Capacity Blocks: `devops/ml_platform_and_gpu_infrastructure/gpu_scheduling_and_inference_ops.md`; cost of idle capacity: `ai_cost_engineering_and_unit_economics` |
| `progressive_delivery_for_model_servers` | generic Argo Rollouts and Flagger: `devops/deployment_strategies/deployment_strategies.md`; the release decision: `ai_release_engineering` |
| `edge_and_on_device_deployment` | `llm/small_language_models_and_edge_ai/small_language_models_and_edge_ai.md`; compression: `ml/model_compression_and_efficiency/model_compression_and_efficiency.md` |
| `gpu_cluster_scheduling_and_quotas` | device plugin, DRA, MIG, MPS, time-slicing mechanism: `devops/kubernetes_scheduling_and_autoscaling/device_scheduling_and_dra.md`, `devops/ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md` (scheduler and quota policy stays here) |
| `ai_networking_and_storage_fabric` | programming view: `cuda/multi_gpu_programming_and_nccl/multi_gpu_programming_and_nccl.md`; `llm/training_infrastructure/training_infrastructure.md` |
| `training_infrastructure_operations` | parallelism theory: `llm/training_infrastructure/training_infrastructure.md`; `ml/distributed_training/distributed_training.md`; `devops/case_studies/cross_cutting/gpu_node_lifecycle.md` |
| `gpu_observability_and_fleet_health` | `cuda/profiling_and_performance_analysis/profiling_and_performance_analysis.md`, `devops/cloud_cost_optimization_finops/gpu_and_ai_finops.md` |
| `ai_gateway_operations` | `llm/llm_routing_and_model_selection/llm_routing_and_model_selection.md`, `llm/llm_caching/llm_caching.md`, `fastapi/serving_llm_and_ai_workloads/serving_llm_and_ai_workloads.md` |
| `decision_models` | logit and grammar mechanics: `llm/inference_and_decoding/constrained_decoding_and_structured_outputs.md`; calibration theory: `ml/case_studies/cross_cutting/model_calibration_and_thresholding.md`, `ml/uncertainty_quantification_and_conformal_prediction/uncertainty_quantification_and_conformal_prediction.md`; judge design: `llm/error_analysis_and_eval_design/error_analysis_and_eval_design.md`; routing strategy: `llm/llm_routing_and_model_selection/llm_routing_and_model_selection.md` |
| `production_quality_monitoring_and_drift` | drift statistics: `ml/monitoring_and_drift_detection/monitoring_and_drift_detection.md`; instrumentation: `llm/llm_observability_and_monitoring/llm_observability_and_monitoring.md` |
| `ai_incident_response_and_reliability` | `devops/incident_management_and_oncall/incident_management_and_oncall.md`, `devops/sre_principles_and_slos/sre_principles_and_slos.md` |
| `ai_governance_and_model_risk_operations` | the law and its timeline: `llm/ai_regulations_and_compliance/ai_regulations_and_compliance.md`; `ml/fairness_and_responsible_ai/fairness_and_responsible_ai.md` |
| `ml_supply_chain_security` | `llm/llm_security/llm_security.md`, `devops/devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md` |
| `ai_cost_engineering_and_unit_economics` | API pricing, provider caching, Batch API, TCO model: `llm/token_economics_and_cost_optimization/token_economics_and_cost_optimization.md` §4-6; GPU purchase instruments, FOCUS, cost-per-1M-tokens derivation: `devops/cloud_cost_optimization_finops/gpu_and_ai_finops.md`; general FinOps: `devops/cloud_cost_optimization_finops/cloud_cost_optimization_finops.md` |
| `ml_platform_architecture_and_build_vs_buy` | `devops/platform_engineering_and_idp/platform_engineering_and_idp.md`, `ml/case_studies/design_ml_platform.md` |

---

## Version pins (fetched 2026-11-25)

Every module's version-anchor blockquote uses only rows from this table. A version or
product fact not in this table is fetched from its primary source before it is written. If
the fetch fails, cut the fact. No "verify" marker ships. Re-fetch the table before an audit
pass. llm-d, GIE, KServe, vLLM and the decision-model APIs change monthly: between the
2026-10-18 and 2026-11-25 fetches, llm-d, vLLM, dcgm-exporter and the OpenAI Decisions API
each changed taught behavior, so schedule the next re-fetch within 30 days. Every fact that module
pages and case studies dated "checked" or "read" 2026-10-18 was re-fetched and re-dated 2026-11-25
(one unconfirmable claim, the ML Kit unlocked-bootloader restriction, was cut). A 2026-10-18 date
that remains is a release date, sample data or deliberate history, not a check date.

| Tool | Pin | Date | Source |
|---|---|---|---|
| vLLM | v0.34.0 (v0.33.0 tagged 2026-11-01); the extensible KV cache is on by default and sizes the cache from memory free after CUDA graph capture and warmup, `gpu_memory_utilization` defaults to 1.0 and the notes say to stop setting it in most cases (opt out with `--gpu-memory-utilization 0.9` or `--no-enable-extensible-kv-cache`); default `--block-size` is 64 (was 16); Model Runner V1 is removed (`VLLM_USE_V2_MODEL_RUNNER=0` fails at startup); `POST /v1/systemone` added in v0.32.0 | 2026-11-16 | https://github.com/vllm-project/vllm/releases |
| SGLang | v0.5.23; Decisions API `/v1/decisions` and `/v1/systemone` since v0.5.21 (PR #41208) | 2026-11-02 | https://github.com/sgl-project/sglang/releases |
| SGLang decision-model docs | validated on Qwen3.8-27B and Qwen3.5-35B-A3B, one H200, BF16 | checked 2026-11-25 | https://docs.sglang.io/docs/supported-models/decision_models |
| TensorRT-LLM | stable v1.3.0 (2026-11-20); v1.4.0rc4 is the newest pre-release | 2026-11-20 | https://github.com/NVIDIA/TensorRT-LLM/releases |
| llama.cpp | stable v1.0.0 (2026-11-13; removes the deprecated public API, including `--defrag-thold`); rolling build b12722 (2026-11-25, pre-release); decision-model support noted in b11714 | 2026-11-25 | https://github.com/ggml-org/llama.cpp/releases |
| llama-server | `-np/--parallel` default -1 (auto); `-cb/--cont-batching` default enabled; `--kv-unified` default enabled when slots are auto; `/metrics` only with `--metrics`; `--port` default 9931 (was 8080; changed in PR #30159, listed in the v0.7.0 notes) | checked 2026-11-25 | https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md |
| Ollama | v0.40.18; decision models via `/v1/systemone` since v0.35.0 (2026-09-28); Clef models v0.35.1 (2026-09-29); CLI and MLX support v0.40.4 (2026-10-12) | 2026-11-24 | https://github.com/ollama/ollama/releases |
| TGI | archived; README points to vLLM, SGLang, llama.cpp, MLX | 2026-03-21 | https://github.com/huggingface/text-generation-inference |
| KServe | v0.21.0 (no later release as of 2026-11-25) | 2026-09-25 | https://github.com/kserve/kserve/releases |
| llm-d | v1.0.0 (CNCF Sandbox since 2026-03): router configuration API graduates to `v1`; priority- and fairness-aware flow control consolidated under `flowControl`; router HA with hot standby; `DisaggregatedSet` manages prefill and decode replicas as one unit; components renamed `llm-d-router-endpoint-picker`, `llm-d-router-disagg-sidecar`, `llm-d-router-coordinator`; `llm-d-kv-cache` moved into `llm-d-router`; llm-d is deprecating its own inference images (`llm-d-rocm` deprecated) in favor of upstream engine images; `ai-dynamo/modelexpress` integrated for P2P weight loading; all components version-aligned at 1.0.0 | 2026-10-20 | https://github.com/llm-d/llm-d/releases |
| Gateway API Inference Extension | v1.6.2 (EPP, BBR, InferenceObjective, InferenceModelRewrite moved to llm-d in v1.6.0) | 2026-09-17 | https://github.com/kubernetes-sigs/gateway-api-inference-extension/releases |
| LeaderWorkerSet | v1.0.0 (adds DisaggregatedSet) | 2026-10-16 | https://github.com/kubernetes-sigs/lws/releases |
| NVIDIA Dynamo | v1.7.0 stable (model-specific `-dev` and `-post` builds are published beside it) | 2026-11-24 | https://github.com/ai-dynamo/dynamo/releases |
| AIBrix | v0.8.0 (StormService, ModelAdapter, predictive autoscaling APIs, scheduled replica bounds) | 2026-10-13 | https://github.com/vllm-project/aibrix/releases |
| Ray | 2.60.1 (Serve LLM, Ray Data LLM) | 2026-11-23 | https://github.com/ray-project/ray/releases |
| KubeRay | v1.7.3 (v1.8.0-rc.0 is a pre-release) | 2026-11-23 | https://github.com/ray-project/kuberay/releases |
| Triton Inference Server | 2.75.0 / NGC 26.11 | 2026-11-17 | https://github.com/triton-inference-server/server/releases |
| BentoML | v1.4.40 | 2026-10-23 | https://github.com/bentoml/BentoML/releases |
| Seldon Core 2 | v2.10.2 (newest v2 tag; the repo's newer v1.19.0 of 2026-01-23 is the v1 line); `LICENSE` is a Business Source License naming "Seldon Core v2" as the Licensed Work | 2025-12-19 | https://github.com/SeldonIO/seldon-core/releases |
| Kueue | v0.20.7 (v1beta1 no longer served); v0.21.0-rc.2 (2026-11-24) is a pre-release | 2026-11-19 | https://github.com/kubernetes-sigs/kueue/releases |
| Volcano | v1.16.0 | 2026-10-14 | https://github.com/volcano-sh/volcano/releases |
| KAI Scheduler | v0.19.0; the repo moved from `NVIDIA/KAI-Scheduler` to `kai-scheduler/KAI-Scheduler` (the old path redirects); v0.19.0 consumes its CRDs from a standalone `kai-scheduler/api` module | 2026-11-11 | https://github.com/kai-scheduler/KAI-Scheduler/releases |
| Kubeflow Trainer | v2.4.0: BREAKING, ReplicatedJob names in runtimes limited to 20 characters, the TrainJob image is set on the MPI launcher and nodes, CRDs kept on `helm uninstall`, CertManagement fields renamed; suspended MPI-plugin TrainJobs created before the upgrade fail to resume after it; Workload-Aware Scheduling for TrainJob phase 1 (KEP-3015); `OptimizationJob` controller behind an alpha feature gate; DRA support for TrainJob (KEP-2782). v2.3.0 (2026-08-07) introduced the `OptimizationJob` CRD and requires clusters on v2.0, v2.1 or v2.2 to upgrade to v2.3 before any later version | 2026-11-06 | https://github.com/kubeflow/trainer/releases |
| Kubeflow Pipelines | 2.18.2 | 2026-11-23 | https://github.com/kubeflow/pipelines/releases |
| Kubeflow Hub | v0.3.18 (renamed from Model Registry at v0.3.9; MCP and Agents catalogs in v0.3.14 and v0.3.16) | 2026-10-22 | https://github.com/kubeflow/hub/releases |
| MLflow | 3.18.0; 3.17.1 (2026-10-13) added "Support Decision API in `make_judge()`"; 3.17.0 (2026-10-07) added TypeSafe judges | 2026-11-04 | https://github.com/mlflow/mlflow/releases |
| Langfuse | v4.86.0; OpenAI decision-model evaluators v4.55.0 (2026-10-08); decision-model evaluators graduated v4.57.0 (2026-10-12) | 2026-11-24 | https://github.com/langfuse/langfuse/releases |
| Arize Phoenix | v22.0.1 (server package `arize-phoenix`; the repo also publishes client releases) | 2026-11-21 | https://pypi.org/project/arize-phoenix/ |
| OpenAI Decisions API | generally available, `POST /v1/decisions`, model `gpt-6-luna` (the only supported model); uncached input $0.10 per 1M tokens, cache reads $0.01 per 1M, cache writes $0.125 per 1M, no output charge; prompt caching needs a reusable `input` prefix of at least 1,024 tokens; 1 to 400 questions per request (200 in the beta); `input_json` content parts and hosted image URLs | checked 2026-11-25 | https://developers.openai.com/api/docs/guides/decisions, https://developers.openai.com/api/docs/models/gpt-6-luna |
| TypeSafe System One API | model family Jev, `POST /v1/systemone`; current models `jev-1.14.0` (available 2026-10-14, adds Box questions) and `jev-1.13.0`, both $42 per billion input tokens with output free; aliases `jev-latest` and `jev-preview` both point to `jev-1.14.0`; pin a versioned id | checked 2026-11-25 | https://docs.typesafe.ai/models |
| vLLM Semantic Router | v0.4.0 (signal-driven decisions; Router Replay plugin since v0.2.0) | 2026-09-27 | https://github.com/vllm-project/semantic-router/releases |
| Agent Router | v1.3.0; the repo moved from `envoyproxy/ai-gateway` to `theagentrouter/agent-router` (the old path redirects); renamed from Envoy AI Gateway at v1.2.0, API group `aigateway.envoyproxy.io` unchanged; v1.3.0 makes `QuotaPolicy` a token-budget system (charged at response end, live policy changes, `fromMetadata` per-tenant limits, `1w`/`1mo`/`1y` windows, cross-namespace targets with a ReferenceGrant, quota-aware backend failover in preview), adds the OpenAI Files and Decisions APIs, moves to Envoy Gateway v1.10.0 | 2026-11-18 | https://github.com/theagentrouter/agent-router/releases |
| LiteLLM | v1.111.0; stdio MCP servers disabled by default since v1.106.0 (#44066) | 2026-11-25 | https://github.com/BerriAI/litellm/releases |
| NVIDIA GPU Operator | v26.7.3 (ships dcgm-exporter 4.9.0 and DCGM 4.7.0, so its metric renames are a breaking change of this Operator release); the v26.7.2 notes name DRA Driver v0.5.1 managed through the `GPUCluster` CR | 2026-11-17 | https://github.com/NVIDIA/gpu-operator/releases, https://docs.nvidia.com/datacenter/cloud-native/gpu-operator/latest/release-notes.html |
| AMD GPU Operator | v1.5.1 (chart `gpu-operator-charts-v1.5.1`); DRA driver v1.0.1 or newer required from amdgpu 6.19.14 (release 31.40) | 2026-07-21 | https://github.com/ROCm/gpu-operator/releases |
| Intel Device Plugins Operator | v0.37.1 (v0.37.0 on 2026-09-16 removed the DLB and FPGA plugins) | 2026-10-02 | https://github.com/intel/intel-device-plugins-for-kubernetes/releases |
| dcgm-exporter / DCGM | 4.9.0 / 4.7.0: renames the 23 default and profiling-preset fields to DCGM 4.7 names, 11 of them with a new unit (percent to a 0 to 1 ratio, MiB to bytes, MHz to hertz, mJ to joules, MB/s to bytes per second); driver label `DCGM_FI_DRIVER_VERSION` becomes `DCGM_FI_SYSTEM_DRIVER_VERSION`; per-pod utilization and memory are exported under both old and new names; GPU-wide fields under MIG are reported once per physical GPU; pod labels need a non-empty allowlist (`.*` for all); preview v2 YAML replaced by the final v2 format, v1 YAML deprecated (support ends in 4.10). Renames used in this section: `GPU_UTIL` to `GPU_UTIL_RATIO`, `MEM_COPY_UTIL` to `MEMORY_UTIL_RATIO`, `FB_USED`/`FB_FREE`/`FB_RESERVED` to `*_BYTES`, `SM_CLOCK` to `SM_CLOCK_HERTZ`, `MEM_CLOCK` to `MEMORY_CLOCK_HERTZ`, `GPU_TEMP` to `GPU_TEMP_CELSIUS`, `MEMORY_TEMP` to `MEMORY_TEMP_CELSIUS`, `POWER_USAGE` to `BOARD_POWER_WATTS`, `TOTAL_ENERGY_CONSUMPTION` to `GPU_ENERGY_JOULES_TOTAL`, `XID_ERRORS` to `XID_ERROR`, `PCIE_REPLAY_COUNTER` to `PCIE_REPLAY_TOTAL`, `NVLINK_BANDWIDTH_TOTAL` to `NVLINK_THROUGHPUT_BYTES_PER_SECOND`, `UNCORRECTABLE_REMAPPED_ROWS` to `ROW_REMAP_UNCORRECTABLE_TOTAL`, `ROW_REMAP_FAILURE` to `ROW_REMAP_FAILED`, `PROF_GR_ENGINE_ACTIVE` to `PROF_GR_ENGINE_UTIL_RATIO`, `PROF_SM_ACTIVE` to `PROF_SM_UTIL_RATIO`, `PROF_SM_OCCUPANCY` to `PROF_SM_OCCUPANCY_RATIO`, `PROF_PIPE_TENSOR_ACTIVE` to `PROF_TENSOR_UTIL_RATIO`, `PROF_DRAM_ACTIVE` to `PROF_DRAM_UTIL_RATIO` (all `DCGM_FI_DEV_` or `DCGM_FI_` prefixed); `DCGM_FI_PROF_PCIE_TX_BYTES` and `_RX_BYTES` unchanged | 2026-11-06 | https://github.com/NVIDIA/dcgm-exporter/releases, https://github.com/NVIDIA/dcgm-exporter/blob/4.9.0/etc/default-counters.csv |
| KEDA | v2.21.1 | 2026-11-15 | https://github.com/kedacore/keda/releases |
| ExecuTorch | v1.6.0 | 2026-11-12 | https://github.com/pytorch/executorch/releases |
| Kubernetes | 1.37 (1.37.0 2026-08-26, 1.37.2 2026-10-13); ImageVolume GA since 1.36 | 2026-10-13 | https://kubernetes.io/releases/ |
| Model Streamer | 0.17.3 (GPU streaming through the C API since 0.17.0); repo `dsx-ai-factory/model-streamer`, `run-ai/runai-model-streamer` redirects to it | 2026-11-11 | https://github.com/dsx-ai-factory/model-streamer/releases |
| ModelPack model-spec | v0.0.7 (tag; no GitHub release objects) | 2025-07-21 | https://github.com/modelpack/model-spec |
| KitOps | v1.17.0 (repo `kitops-ml/kitops`; adds ModelKit indexes) | 2026-11-06 | https://github.com/kitops-ml/kitops/releases |
| NVIDIA Network Operator | v26.10.0 | 2026-11-17 | https://github.com/Mellanox/network-operator/releases |
| Slinky slurm-operator | v1.3.0 (tag; date is the tag commit; v1.2.5 and v1.1.6 patch lines tagged the same day) | 2026-11-19 | https://github.com/SlinkyProject/slurm-operator |
| NIXL | v1.5.2 | 2026-10-29 | https://github.com/ai-dynamo/nixl/releases |
| Mooncake | v0.3.14 | 2026-11-03 | https://github.com/kvcache-ai/Mooncake/releases |
| lakeFS | v1.88.0 | 2026-10-01 | https://github.com/treeverse/lakeFS/releases |
| DVC | 3.67.1; repo is `treeverse/dvc` (`iterative/dvc` redirects to it) | 2026-03-31 | https://github.com/treeverse/dvc/releases |
| Label Studio | 1.23.2 | 2026-09-29 | https://github.com/HumanSignal/label-studio/releases |
| Argo Rollouts | v1.10.2 | 2026-11-04 | https://github.com/argoproj/argo-rollouts/releases |
| Flagger | v1.45.0 | 2026-09-01 | https://github.com/fluxcd/flagger/releases |
| Kyverno | v1.20.0; removes the legacy `kyverno.io` policy engine, so `ClusterPolicy` objects stop enforcing and new legacy policies are blocked; use `ValidatingPolicy` and `ImageValidatingPolicy` (`policies.kyverno.io/v1`) | 2026-11-12 | https://github.com/kyverno/kyverno/releases |
| model-transparency (OpenSSF Model Signing) | v1.1.1 (still the newest release on 2026-11-25) | 2025-10-10 | https://github.com/sigstore/model-transparency/releases |
| CycloneDX specification | 1.7 (repo tag 1.7.2) | 2026-09-17 | https://github.com/CycloneDX/specification/releases |
| Feast | v0.68.0 (link-only: feature stores are taught in `ml/`) | 2026-11-10 | https://github.com/feast-dev/feast/releases |
| CNCF Kubernetes AI Conformance | per-version directories v1.33 to v1.37 | checked 2026-11-25 | https://github.com/cncf/k8s-ai-conformance |
| MCP specification | current revision 2026-07-28 (per-request version in `_meta`, `server/discover`); handshake-based revisions are 2025-11-25 and earlier | checked 2026-11-25 | https://modelcontextprotocol.io/specification/versioning |
| MCP Registry | preview; API v0.1 frozen 2025-10-24 | checked 2026-11-25 | https://github.com/modelcontextprotocol/registry |
| OTel GenAI semantic conventions | moved to `open-telemetry/semantic-conventions-genai`; stability not stated there, so content claims no stability level | checked 2026-11-25 | https://opentelemetry.io/docs/specs/semconv/gen-ai/ |
| Ultra Ethernet | specification 1.1 (publication date not stated) | checked 2026-11-25 | https://ultraethernet.org/ |

Cut for lack of a confirmable source: NVIDIA NIM version, OS-provided on-device model
names, Neptune.ai status. A module that needs one fetches it first or leaves it out.
LMCache publishes only nightly pre-releases; name it without a version. Argilla, Snorkel,
LaunchDarkly AI Configs and the flag tools (OpenFeature, Statsig, Unleash) have no pin;
name them without a version or fetch first.

### Ownership and status facts

Content states only the "Confirmed" rows. A "Not confirmed" row is fetched from a primary
source before it is written, or left out.

| Fact | State | Source |
|---|---|---|
| KServe is a CNCF incubating project, accepted 2025-09-29 | Confirmed 2026-11-25 | https://www.cncf.io/projects/kserve/ |
| ModelPack is a CNCF Sandbox project, accepted 2025-05-13 | Confirmed 2026-11-25 | https://www.cncf.io/projects/modelpack/ |
| KitOps is a CNCF Sandbox project, accepted 2025-03-04 | Confirmed 2026-11-25 | https://www.cncf.io/projects/kitops/ |
| KAI Scheduler is a CNCF Sandbox project, accepted 2025-12-21; license Apache-2.0 | Confirmed 2026-11-25 | https://www.cncf.io/projects/kai-scheduler/, GitHub repo license |
| llm-d is a CNCF Sandbox project, accepted 2026-03-12 | Confirmed 2026-11-25 (still Sandbox after v1.0.0) | https://www.cncf.io/projects/llm-d/ |
| Kueue is not a CNCF project (Kubernetes SIG project) | Confirmed 2026-11-25: absent from both CNCF lists | https://www.cncf.io/projects/ |
| ClickHouse acquired Langfuse, announced 2026-01-16 | Confirmed 2026-11-25 | https://langfuse.com/blog/joining-clickhouse |
| OpenAI agreed to acquire promptfoo, announced 2026-03-09; repo license MIT | Confirmed 2026-11-25 | https://www.promptfoo.dev/blog/, GitHub repo license |
| DVC lives under the `treeverse` org | Confirmed 2026-11-25 | GitHub redirect `iterative/dvc` to `treeverse/dvc` |
| Model Streamer repo moved to `dsx-ai-factory/model-streamer` | Confirmed 2026-11-25 (repo redirect); the rename date is not confirmed, so content gives no date | GitHub redirect |
| TGI repo is archived | Confirmed 2026-11-25 (archived 2026-03-21) | GitHub repo page |
| KAI Scheduler repo moved to `kai-scheduler/KAI-Scheduler` | Confirmed 2026-11-25 (redirect from `NVIDIA/KAI-Scheduler`); the move date is not confirmed, so content gives no date | GitHub redirect |
| Agent Router repo moved to `theagentrouter/agent-router` | Confirmed 2026-11-25 (redirect from `envoyproxy/ai-gateway`); the API group stays `aigateway.envoyproxy.io`; the move date is not confirmed | GitHub redirect, v1.3.0 release notes |
| W&B is part of CoreWeave | Not confirmed | fetch before writing |
| Tecton is part of Databricks | Not confirmed | fetch before writing |
| Humanloop is sunsetting | Not confirmed | fetch before writing |

---

## Glossary

| Term | Meaning in this section |
|---|---|
| Release bundle | The unit of release for an AI feature: one manifest that pins every artifact the feature runs on, by digest or immutable version. Fields: model digest, adapter digests, prompt version, tool-server versions, index snapshot id, decoding params, guardrail policy version, eval-set version, decision schema version. A rollback redeploys a previous bundle digest. The worked manifest lives in `case_studies/cross_cutting/ai_release_bundle_and_lineage.md`; modules define and use it, they do not re-derive it |
| Artifact taxonomy | The artifact types a bundle can pin: code, data snapshot, labels, features, embedding model, vector index, base weights, adapters, prompt templates, tools and MCP servers, agent config, guardrail policy, eval set, decoding and runtime config, decision schema. Each module names which types it owns |
| Decision model | A model served behind a typed-decision endpoint (`/v1/decisions`, `/v1/systemone`) that returns labels, probabilities and scores instead of generated text. Also the classic form: a score, a threshold and an action mapping on top of a classifier. Owned by `decision_models` |
| Routing decision | A vLLM Semantic Router "decision": a boolean rule over request signals that selects candidate models, an algorithm and plugins. It is a routing rule, not a decision model. Owned by `ai_gateway_operations` |

Name each vendor's fields as the vendor names them (`predicate`, `yes_no`, `noul`). The
terminology is not settled across vendors.

---

## Module list (32 modules, 6 phases)

| Phase | Module directory | Q&A floor |
|---|---|---|
| 1 Lifecycle and artifacts | `mlops_foundations_and_the_ai_lifecycle/` | 15 |
| 1 | `artifact_versioning_and_lineage/` | 15 |
| 1 | `dataset_and_label_versioning/` | 15 |
| 1 | `prompt_lifecycle_and_versioning/` | 15 |
| 1 | `model_registry_and_packaging/` | 15 |
| 1 | `rag_and_vector_index_lifecycle/` | 18 |
| 1 | `agent_tool_and_mcp_server_lifecycle/` | 15 |
| 2 Pipelines, gates and release | `ml_pipelines_and_orchestration/` | 15 |
| 2 | `evaluation_gates_and_eval_sets/` | 18 |
| 2 | `continuous_training_and_refresh_policy/` | 15 |
| 2 | `ai_release_engineering/` | 18 |
| 3 Serving infrastructure | `deployment_targets_and_serving_patterns/` | 15 |
| 3 | `model_serving_platforms/` | 18 |
| 3 | `inference_engine_operations_and_benchmarking/` | 15 |
| 3 | `distributed_inference_and_disaggregation/` | 18 |
| 3 | `lora_adapter_serving_and_lifecycle/` | 15 |
| 3 | `model_storage_loading_and_cold_start/` | 15 |
| 3 | `inference_autoscaling_and_capacity_planning/` | 18 |
| 3 | `progressive_delivery_for_model_servers/` | 15 |
| 3 | `edge_and_on_device_deployment/` | 15 |
| 4 Compute platform and training infrastructure | `gpu_cluster_scheduling_and_quotas/` | 18 |
| 4 | `ai_networking_and_storage_fabric/` | 15 |
| 4 | `training_infrastructure_operations/` | 15 |
| 4 | `gpu_observability_and_fleet_health/` | 15 |
| 5 Running AI in production | `ai_gateway_operations/` | 15 |
| 5 | `decision_models/` | 15 |
| 5 | `production_quality_monitoring_and_drift/` | 15 |
| 5 | `ai_incident_response_and_reliability/` | 15 |
| 6 Governance, security, cost and platform strategy | `ai_governance_and_model_risk_operations/` | 15 |
| 6 | `ml_supply_chain_security/` | 15 |
| 6 | `ai_cost_engineering_and_unit_economics/` | 15 |
| 6 | `ml_platform_architecture_and_build_vs_buy/` | 15 |

`progressive_delivery_for_model_servers` §4 holds the only deployment-strategy matrix in the
section. Every other module links to it.

---

## Sub-files (4, 14-section template, 15+ Q&As each)

Each exists because it needs a different study tier from its parent page.

| File | Parent | Tier |
|---|---|---|
| `evaluation_gates_and_eval_sets/statistics_of_release_gates.md` | `evaluation_gates_and_eval_sets` | principal |
| `ai_release_engineering/experimentation_for_generative_features.md` | `ai_release_engineering` | senior |
| `deployment_targets_and_serving_patterns/multi_region_and_multi_tenant_serving.md` | `deployment_targets_and_serving_patterns` | principal |
| `model_serving_platforms/kserve_deep_dive.md` | `model_serving_platforms` | senior |

### Deep-dive naming rule

`model_serving_platforms/kserve_deep_dive.md` is the only file in this section allowed a
`*_deep_dive` or `*_internals` name. `DEEP_NAME_RE` in `game/build_tech.py` makes any such
page FATAL under `--strict` unless it is listed in `PRODUCT_DEEP_DIVES` or
`CONCEPT_DEEP_DIVES`. Add `"mlops/model_serving_platforms/kserve_deep_dive.md"` to
`PRODUCT_DEEP_DIVES` in the same commit as the file. There is no llm-d deep dive: its
components move between repos monthly.

---

## Case studies (8 plus 1 cross-cutting)

11-section principal template. Quality bar for every study, senior or principal: 900-1100
lines; 4+ `cross_cutting/` references; executable code in §4; a broken-then-fixed example
in §4; named companies in §6; quantified impact in §9; 10+ Q&As in §11 under
`## 11. Interview Discussion Points`, each with a `**Short:**` line.

| File | Tiers | Concern |
|---|---|---|
| `case_studies/design_an_ai_release_pipeline.md` | senior, principal | 40 AI features, about 300 bundle releases a week through gates, shadow, canary and A/B; rollback under 5 minutes |
| `case_studies/design_an_embedding_model_migration.md` | senior, principal | 2B chunks to a new embedding model with zero downtime: dual index, re-embed cost, shadow recall, cutover and rollback |
| `case_studies/design_a_multi_tenant_lora_serving_platform.md` | senior | 5,000 customer adapters over 3 base models: registry, hot-load, affinity routing, base-upgrade campaign |
| `case_studies/design_a_model_weight_distribution_system.md` | senior | 0 to 64 nodes of a frontier MoE in under 3 minutes: OCI artifacts, P2P, NVMe cache, streaming load |
| `case_studies/design_an_on_device_model_delivery_system.md` | - | a 3B model plus adapters to 50M phones: OTA deltas, cohort rollout, device matrix, hybrid fallback |
| `case_studies/design_a_continuous_training_and_champion_challenger_system.md` | principal | CT for a scoring model with 30-day label delay: challenger shadowing, promotion rules, decision-policy versioning |
| `case_studies/design_an_ai_quality_incident_response_program.md` | principal | a silent provider model update cuts task success by 8%: quality SLOs, detection, kill switch, re-qualification |
| `case_studies/design_an_ai_governance_and_model_inventory_platform.md` | principal | a bank with 1,200 models and 300 GenAI use cases: inventory, risk tiers, approvals, EU AI Act and SR 26-2 evidence |
| `case_studies/cross_cutting/ai_release_bundle_and_lineage.md` | not listed | the release-bundle manifest as a worked example (14-section reference note) |

Case-study Q&As never enter the main bank. `extract.py` puts them in the reader-only
`game/case_questions/mlops.json` pool. Do not move one under a module's §12 to "rescue" it.

`case_studies/cross_cutting/` is in `CS_EXCLUDE_DIRS`. Listing a file under it in the
`<!-- study-paths -->` block fails `--strict`. Neither the cross-cutting file nor
`case_studies.md` appears in the block.

Studies must not duplicate `llm/case_studies/design_gpu_inference_platform.md`,
`llm/case_studies/design_llm_gateway.md`, `llm/case_studies/design_llm_eval_platform.md`,
`llm/case_studies/design_llm_fine_tuning_platform.md`,
`llm/case_studies/design_agent_platform.md`, `ml/case_studies/design_ml_platform.md`,
`ml/case_studies/design_experimentation_platform.md`,
`devops/case_studies/design_ml_platform_infrastructure.md`,
`devops/case_studies/design_gpu_training_and_inference_cluster.md`,
`backend/case_studies/design_llm_serving_api/design_llm_serving_api.md`, or
`database/case_studies/design_vector_search_platform/design_vector_search_platform.md`.

---

## Learning paths

Senior is the craft: build it, debug it, say why it broke. Principal is the judgment: which
approach at what cost, what migration, what not to build. They are different cuts, not
nested depths. 6 of the 13 principal files (11 module pages plus 2 sub-files) are material
the senior path never sees: four module pages (`continuous_training_and_refresh_policy`,
`ai_governance_and_model_risk_operations`, `ai_cost_engineering_and_unit_economics`,
`ml_platform_architecture_and_build_vs_buy`) and two sub-files
(`statistics_of_release_gates.md`, `multi_region_and_multi_tenant_serving.md`).
Do not promote a module to principal because it is advanced.

Tier decision (2026-11-25): `ml_pipelines_and_orchestration`, `edge_and_on_device_deployment`
and `ai_networking_and_storage_fabric` were on no tier; all three are craft (step caching on input
digests and batch inference, OTA and device-matrix delivery, NCCL triage and fabric acceptance tests), so they
joined the senior tier. `gpu_cluster_scheduling_and_quotas` moved from principal to senior and
principal, because its §6 (MIG relayout, Kueue YAML, KAI sharing modes) is craft while its quota
design stays principal judgment. Senior is now 28 modules, principal 11 modules and 13 files.

Membership is declared once, in the `<!-- study-paths -->` block in `README.md`. Order comes
from `STUDY_ORDER.mlops` in `game/app.js`. There is no path array in `app.js` to edit.

---

## Q&A rules

- 15+ Q&As per module page and per sub-file. A floor, not a target.
- 18+ for `rag_and_vector_index_lifecycle`, `evaluation_gates_and_eval_sets`,
  `ai_release_engineering`, `model_serving_platforms`,
  `distributed_inference_and_disaggregation`, `inference_autoscaling_and_capacity_planning`
  and `gpu_cluster_scheduling_and_quotas`. Floor total for the section: 561.
- Every Q&A is `**Q: ...?**` followed by an authored `**Short:**` line (15-220 chars) as the
  first non-blank answer line. This section ships Short-migrated from the start.
- Order by interview frequency: traps first, then mechanics, then edge cases.
- Keep first sentences lexically distinct. MLOps answers cluster on "model", "rollout" and
  "version", and near-identical openers produce indistinguishable MCQ options.

---

## Authoring rules

- Module pages and sub-files: the 14-section template. No line-count target. Do not pad.
- A dated version-anchor blockquote under the H1, devops pattern, using only pin-table rows.
- §1 ends with a "Not here" paragraph naming the hand-off pages from the boundary table.
- At least 3 sibling `mlops/` links per module, written `../<slug>/<slug>.md`. Sibling links
  create `build_graph` edges; cross-section links do not.
- No em-dash (U+2014), no en-dash (U+2013), no emoji. The repo house style uses em-dashes
  heavily; do not copy it into `mlops/`. Check:
  `grep -rnP '[\x{2013}\x{2014}]' src/main/java/com/rutik/systemdesign/mlops` gives no output.
- Prices carry their check date. Any price not in the pin table is fetched and dated in the
  anchor, or given as a variable.
- §11 stays `## 11. Technologies & Tools`. This section feeds `tech_index.json`. Run the §11
  lint in `technologies/CLAUDE.md` ("The §11 lint") before committing a §11.

---

## Question ids: what re-keys them

`qid()` in `game/extract.py` hashes `module|question`, giving `mlops/<module>#<md5[:12]>`.
`sourceFile` is not hashed.

- Splitting a module into its own sub-files is id-safe. Questions keep their module.
- Moving, merging or renaming a module directory re-keys every question in it.
  `startReview()` in `game/app.js` then deletes the orphaned review ids, and learners lose
  their spaced-repetition state for that module. Settle a slug before its first deploy.

---

## Mermaid traps

A Mermaid fence that does not parse fails the build (`scripts/build_diagrams.mjs` exits 1).
Validate every file you touch: `node scripts/check_render.mjs <absolute-file.md>` from the
repo root (`npm ci` once first). Run `/mermaid-diagrams` before authoring.

| Trap | Where MLOps content hits it |
|---|---|
| `;` in a `sequenceDiagram` Note or message | YAML or flag lists pasted into a Note (`maxSurge: 0; maxUnavailable: 1`). Use `,` |
| `call`, `end`, `default`, `class`, `style`, `click` as a node id or `classDef` name | `default` route, `end` state, a classifier `class` node. Rename (`defRoute`, `done`, `clsNode`) |
| `<` or `>` in an unquoted label | gates and SLOs (`p95 > 500 ms`, `>= champion`). Quote the label or write it in words |
| `\"` in a label | JSON request bodies and struct fields. Use `#quot;` or keep payloads in a code fence |
| Spaces after an edge label's closing `\|` | aligned columns of canary weights (`-->\|"5%"\|   b`). One space only |
| `:` in a `quadrant-N` label, a point without brackets, a coordinate of exactly `1.0` | build-vs-buy and cost-vs-quality quadrants. Quote the label, write `[x, y]`, use `0.98` |

Product nodes (KServe, Kubernetes, Ray) may use icon nodes per root `CLAUDE.md`; every icon
node carries a text `label`.

---

## Adding a new mlops module

1. Create `<module>/<module>.md` with the 14-section template. The page is named for its
   folder.
2. Write the version anchor from the pin table. Fetch anything else from its primary source.
3. Meet the Q&A floor above, each answer opening with its `**Short:**` line.
4. Add `"mlops/<module>"` to `STUDY_ORDER.mlops` in `game/app.js` at its phase position.
5. Add the module and every file it owns to the `<!-- study-paths -->` block in `README.md`,
   with tiers (`-` for Full path only). Then run `python3 game/extract.py --write-paths`.
6. Run `python3 game/extract.py --strict`. It must exit 0 and show an mlops count.
7. Flip the file's status in the `README.md` Build Manifest.
8. Update the root `README.md` and root `CLAUDE.md` rows for this section.

To add a case study: write it, add its block line, update `case_studies/case_studies.md`
(Quick Start, Full Learning Path, Cross-Cutting / Shared Primitives, Dependency Map,
Interview Prep Shortcuts), the `README.md` case-study table and the case-study list in this
file, in the same commit.

---

## HARD RULE: structure lives in `README.md`, content files hold only content

The section `README.md` is the single source of truth for this section's file inventory and
study-tier membership. Its `<!-- study-paths -->` block lists every module, every file that
module owns (the module page and every sub-file), and every case study, each tagged with its
tiers. `-` means Full path only.

A content file carries no structural metadata: no `<!-- study-paths -->` block, no tier
declaration, no path membership.

Adding a file? Add its line to the README block in the same commit. A file on disk missing
from the block, or listed there and absent from disk, fails
`python3 game/extract.py --strict` and takes the Pages deploy red. The parser splits each
file line on its last double space, so put at least two spaces between a filename and its
tiers.

Order is never declared in the block. It comes from `STUDY_ORDER` in `game/app.js`. The tier
tables in the README are generated from the block by `python3 game/extract.py --write-paths`.
Never hand-edit them.
