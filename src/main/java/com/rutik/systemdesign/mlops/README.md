# MLOps & ML Infra - Senior Engineer & Interview Prep Guide

The lifecycle and platform layer for AI systems: every artifact an AI feature ships (weights, adapters, prompts, tool servers, vector indexes, eval sets, decision schemas) as a versioned, gated, rolled-out and rolled-back release input, plus the serving, compute and operations platform that runs them. The [ML](../ml/README.md), [LLM](../llm/README.md) and [DevOps](../devops/README.md) sections teach the models, the techniques and the substrate. This section links to them and covers what crosses between them.

> **No runnable application.** All content is Markdown. Version facts are pinned in [CLAUDE.md](CLAUDE.md) "Version pins", fetched 2026-10-18.

---

## 1. Section Overview

- **Lifecycle and artifacts**: the artifact taxonomy, the release bundle, digests and lineage, dataset and label versions, prompts and MCP servers as release artifacts, registries and OCI packaging, the vector index lifecycle
- **Pipelines, gates and release**: orchestration, eval sets and release gates, the statistics behind a gate, refresh policy per artifact type, rollout ladders, flags and rollback semantics
- **Serving infrastructure**: deployment targets, serving platforms and KServe, engine benchmarking and upgrade qualification, disaggregated inference, multi-LoRA serving, cold start, autoscaling, progressive delivery, edge and on-device delivery
- **Compute platform and training infrastructure**: GPU schedulers and quota policy, GPU networking and storage fabric, training job operations and checkpoints, GPU telemetry and fleet health
- **Running AI in production**: AI gateways, decision models, quality monitoring and drift, AI incident response
- **Governance, security, cost and platform strategy**: model risk operations, ML supply chain security, unit economics, platform build vs buy

---

## 2. Module Table

| # | Module | Phase | Tiers | Key Topics |
|---|---|---|---|---|
| 1 | [mlops_foundations_and_the_ai_lifecycle](mlops_foundations_and_the_ai_lifecycle/mlops_foundations_and_the_ai_lifecycle.md) | 1 Lifecycle and artifacts | senior | Classic MLOps vs LLMOps vs AgentOps, artifact taxonomy, release bundle, CI vs CD vs CT per artifact, what changes without a deploy |
| 2 | [artifact_versioning_and_lineage](artifact_versioning_and_lineage/artifact_versioning_and_lineage.md) | 1 | senior | Digests vs semver vs aliases, bundle manifest fields, OpenLineage, MLflow `LoggedModel`, reproducibility manifests, lineage queries for erasure |
| 3 | [dataset_and_label_versioning](dataset_and_label_versioning/dataset_and_label_versioning.md) | 1 | senior | lakeFS, DVC, table-format time travel, label provenance, contamination and dedup, consent lineage, feature definitions as release inputs |
| 4 | [prompt_lifecycle_and_versioning](prompt_lifecycle_and_versioning/prompt_lifecycle_and_versioning.md) | 1 | senior | The prompt release unit, registries and aliases, variable contracts, eval gates, alias rollback, re-qualification after a provider model change |
| 5 | [model_registry_and_packaging](model_registry_and_packaging/model_registry_and_packaging.md) | 1 | senior | Aliases replacing stages, safetensors, GGUF, ONNX, ModelPack, KitOps, ORAS, ImageVolume, adapters pinned to a base digest |
| 6 | [rag_and_vector_index_lifecycle](rag_and_vector_index_lifecycle/rag_and_vector_index_lifecycle.md) | 1 | senior, principal | Versioned RAG parts, tombstones and compaction, dual-index cutover, recall@k gates, ACL sync, erasure propagation |
| 7 | [agent_tool_and_mcp_server_lifecycle](agent_tool_and_mcp_server_lifecycle/agent_tool_and_mcp_server_lifecycle.md) | 1 | senior | Agent config as an artifact, tool schemas as contracts, MCP server versioning and the MCP Registry, trajectory evals as gates |
| 8 | [ml_pipelines_and_orchestration](ml_pipelines_and_orchestration/ml_pipelines_and_orchestration.md) | 2 Pipelines, gates and release | - | KFP, Metaflow, ZenML, Flyte, Dagster, Argo Workflows, Ray Data LLM, step caching on input digests, batch inference |
| 9 | [evaluation_gates_and_eval_sets](evaluation_gates_and_eval_sets/evaluation_gates_and_eval_sets.md) | 2 | senior, principal | Golden sets, absolute vs regression gates, per-slice gates, judge pinning and drift, gates per artifact type, eval cost budgets |
| 10 | [continuous_training_and_refresh_policy](continuous_training_and_refresh_policy/continuous_training_and_refresh_policy.md) | 2 | principal | Retraining under label delay, champion-challenger, adapter retrain on base upgrade, index refresh economics, auto vs human-approved promotion |
| 11 | [ai_release_engineering](ai_release_engineering/ai_release_engineering.md) | 2 | senior, principal | Bundle promotion, rollout ladder, model, prompt and tool flags, rollback semantics per artifact, provider pinning, cost delta as a gate |
| 12 | [deployment_targets_and_serving_patterns](deployment_targets_and_serving_patterns/deployment_targets_and_serving_patterns.md) | 3 Serving infrastructure | senior, principal | Serving decision matrix, `llama-server` and Ollama targets, batch scoring, streaming inference, decision endpoints |
| 13 | [model_serving_platforms](model_serving_platforms/model_serving_platforms.md) | 3 | senior | KServe, Ray Serve, BentoML, Seldon Core 2, Triton, AIBrix, engine backend composition |
| 14 | [inference_engine_operations_and_benchmarking](inference_engine_operations_and_benchmarking/inference_engine_operations_and_benchmarking.md) | 3 | senior | TTFT, ITL, goodput, open vs closed loop load, upgrade qualification, `llama-server` operations, TGI migration |
| 15 | [distributed_inference_and_disaggregation](distributed_inference_and_disaggregation/distributed_inference_and_disaggregation.md) | 3 | senior, principal | TP, PP, EP, DP for inference, LeaderWorkerSet, prefill/decode disaggregation, NIXL and Mooncake, prefix-aware routing, llm-d, Dynamo |
| 16 | [lora_adapter_serving_and_lifecycle](lora_adapter_serving_and_lifecycle/lora_adapter_serving_and_lifecycle.md) | 3 | senior | Multi-LoRA serving, adapter cache tiers, affinity routing, base-model upgrade campaigns, multiplexing small classic models |
| 17 | [model_storage_loading_and_cold_start](model_storage_loading_and_cold_start/model_storage_loading_and_cold_start.md) | 3 | senior | The cold-start budget, streaming loaders, P2P weight distribution, LocalModelCache, warm pools vs scale-from-zero |
| 18 | [inference_autoscaling_and_capacity_planning](inference_autoscaling_and_capacity_planning/inference_autoscaling_and_capacity_planning.md) | 3 | senior | Tokens/s per GPU under an SLO, queue depth and KV-cache signals, HPA, KEDA, Knative KPA, predictive scaling |
| 19 | [progressive_delivery_for_model_servers](progressive_delivery_for_model_servers/progressive_delivery_for_model_servers.md) | 3 | senior | Deployment strategy matrix, KServe canary, Gateway API weights, mirroring, Argo Rollouts and Flagger analysis, Recreate for large models |
| 20 | [edge_and_on_device_deployment](edge_and_on_device_deployment/edge_and_on_device_deployment.md) | 3 | - | llama.cpp, Ollama, MLX, ExecuTorch, LiteRT, Core ML, OTA delta updates, device matrix, cohort rollout |
| 21 | [gpu_cluster_scheduling_and_quotas](gpu_cluster_scheduling_and_quotas/gpu_cluster_scheduling_and_quotas.md) | 4 Compute platform and training infrastructure | principal | GPU operators by vendor, Kueue, Volcano, KAI Scheduler, Slurm and Slinky, MIG vs MPS vs time-slicing as policy, quota design |
| 22 | [ai_networking_and_storage_fabric](ai_networking_and_storage_fabric/ai_networking_and_storage_fabric.md) | 4 | - | NVLink domains, InfiniBand vs RoCEv2 vs Ultra Ethernet, GPUDirect, NCCL operations, checkpoint storage tiers |
| 23 | [training_infrastructure_operations](training_infrastructure_operations/training_infrastructure_operations.md) | 4 | senior | Kubeflow Trainer, JobSet, Ray Train, goodput and ETTR, failure taxonomy, checkpoint frequency, RL post-training infrastructure |
| 24 | [gpu_observability_and_fleet_health](gpu_observability_and_fleet_health/gpu_observability_and_fleet_health.md) | 4 | senior | DCGM and dcgm-exporter fields, XID and ECC, substrate triage, engine and GPU metric correlation, MFU reporting |
| 25 | [ai_gateway_operations](ai_gateway_operations/ai_gateway_operations.md) | 5 Running AI in production | senior | LiteLLM, Agent Router, vLLM Semantic Router, virtual keys and budgets, fallback chains, MCP gateway auth |
| 26 | [decision_models](decision_models/decision_models.md) | 5 | senior | `/v1/decisions` and `/v1/systemone`, the decision artifact, calibration on your own labels, thresholds, decision monitoring |
| 27 | [production_quality_monitoring_and_drift](production_quality_monitoring_and_drift/production_quality_monitoring_and_drift.md) | 5 | senior | Online eval, feedback, drift taxonomy per artifact, label delay and proxy metrics, quality SLOs |
| 28 | [ai_incident_response_and_reliability](ai_incident_response_and_reliability/ai_incident_response_and_reliability.md) | 5 | senior, principal | AI SLOs, incident classes, replaying non-deterministic failures, kill switches and degradation modes |
| 29 | [ai_governance_and_model_risk_operations](ai_governance_and_model_risk_operations/ai_governance_and_model_risk_operations.md) | 6 Governance, security, cost and platform strategy | principal | AI inventory, risk tiering, SR 11-7, ISO/IEC 42001, NIST AI RMF, EU AI Act evidence, deploy gates |
| 30 | [ml_supply_chain_security](ml_supply_chain_security/ml_supply_chain_security.md) | 6 | senior | Pickle risks, model scanners, OpenSSF Model Signing, SLSA provenance, CycloneDX ML-BOM, signature checks at admission |
| 31 | [ai_cost_engineering_and_unit_economics](ai_cost_engineering_and_unit_economics/ai_cost_engineering_and_unit_economics.md) | 6 | principal | Cost per successful task, self-host break-even, cost levers with their quality gates, budgets, chargeback, cost SLOs |
| 32 | [ml_platform_architecture_and_build_vs_buy](ml_platform_architecture_and_build_vs_buy/ml_platform_architecture_and_build_vs_buy.md) | 6 | principal | Reference platform stacks, CNCF Kubernetes AI Conformance, build vs buy vs assemble, what not to build |

### Sub-Files

Each exists because it needs a different study tier from its parent page.

| File | Parent | Tiers | Why separate |
|---|---|---|---|
| [statistics_of_release_gates](evaluation_gates_and_eval_sets/statistics_of_release_gates.md) | evaluation_gates_and_eval_sets | principal | Sample size for a pass-rate delta, McNemar, Holm and Benjamini-Hochberg across slices, judge noise, why ">= champion" gates ratchet quality down |
| [experimentation_for_generative_features](ai_release_engineering/experimentation_for_generative_features.md) | ai_release_engineering | senior | Randomization unit, generative metrics, variance from non-determinism, interleaving, switchback, bandits for prompt and model variants |
| [multi_region_and_multi_tenant_serving](deployment_targets_and_serving_patterns/multi_region_and_multi_tenant_serving.md) | deployment_targets_and_serving_patterns | principal | GPU-scarce multi-region topologies, failover onto cold KV caches, tenant isolation tiers, sovereign and air-gapped deployments |
| [kserve_deep_dive](model_serving_platforms/kserve_deep_dive.md) | model_serving_platforms | senior | CRDs and controller, ServingRuntime, storage initializer, LocalModelCache, LLMInferenceService, `rolloutStrategy`, Knative vs raw mode, failure modes |

---

## 3. Learning Path by Phase

```mermaid
flowchart LR
  p1(["Phase 1<br/>Lifecycle and<br/>artifacts"]) --> p2(["Phase 2<br/>Pipelines, gates<br/>and release"])
  p2 --> p3(["Phase 3<br/>Serving<br/>infrastructure"])
  p3 --> p4(["Phase 4<br/>Compute platform<br/>and training"])
  p4 --> p5(["Phase 5<br/>Running AI<br/>in production"])
  p5 --> p6(["Phase 6<br/>Governance, security,<br/>cost, platform"])
  p6 --> cs(["Case studies"])
  classDef life fill:#2c313a,stroke:#61afef,color:#abb2bf
  classDef gate fill:#2c313a,stroke:#c678dd,color:#abb2bf
  classDef serve fill:#2c313a,stroke:#e5c07b,color:#abb2bf
  classDef comp fill:#2c313a,stroke:#98c379,color:#abb2bf
  classDef prod fill:#2c313a,stroke:#e06c75,color:#abb2bf
  classDef gov fill:#2c313a,stroke:#d19a66,color:#abb2bf
  classDef cases fill:#2c313a,stroke:#56b6c2,color:#abb2bf
  class p1 life
  class p2 gate
  class p3 serve
  class p4 comp
  class p5 prod
  class p6 gov
  class cs cases
```

| Phase | Modules (table rows) | Question it answers |
|---|---|---|
| 1 Lifecycle and artifacts | 1-7 | What is being released, and how is each part identified and traced? |
| 2 Pipelines, gates and release | 8-11 | What blocks a release, and how does it reach users and come back? |
| 3 Serving infrastructure | 12-20 | Where does the model run, how fast does it start, and how does traffic move to a new version? |
| 4 Compute platform and training infrastructure | 21-24 | Who gets which GPUs, and how do jobs survive the fleet? |
| 5 Running AI in production | 25-28 | How is quality watched, and what happens when it drops? |
| 6 Governance, security, cost and platform strategy | 29-32 | Who approves it, can it be trusted, what does it cost, and what should the platform team build? |

---

## Learning Paths

The Full Path is every module in the order above. The game's **Study** view shows it with a Full / Senior / Principal toggle (Full is the default). The tier tables below are generated from the `<!-- study-paths -->` block by `python3 game/extract.py --write-paths`. Do not edit them by hand.

<!-- study-path-table senior -->
### Senior Path (24 modules)

| # | Module | Files |
|---|--------|-------|
| 1 | [mlops_foundations_and_the_ai_lifecycle](mlops_foundations_and_the_ai_lifecycle/mlops_foundations_and_the_ai_lifecycle.md) | module page only |
| 2 | [artifact_versioning_and_lineage](artifact_versioning_and_lineage/artifact_versioning_and_lineage.md) | module page only |
| 3 | [dataset_and_label_versioning](dataset_and_label_versioning/dataset_and_label_versioning.md) | module page only |
| 4 | [prompt_lifecycle_and_versioning](prompt_lifecycle_and_versioning/prompt_lifecycle_and_versioning.md) | module page only |
| 5 | [model_registry_and_packaging](model_registry_and_packaging/model_registry_and_packaging.md) | module page only |
| 6 | [rag_and_vector_index_lifecycle](rag_and_vector_index_lifecycle/rag_and_vector_index_lifecycle.md) | module page only |
| 7 | [agent_tool_and_mcp_server_lifecycle](agent_tool_and_mcp_server_lifecycle/agent_tool_and_mcp_server_lifecycle.md) | module page only |
| 9 | [evaluation_gates_and_eval_sets](evaluation_gates_and_eval_sets/evaluation_gates_and_eval_sets.md) | module page only |
| 11 | [ai_release_engineering](ai_release_engineering/ai_release_engineering.md) | 2 files |
| 12 | [deployment_targets_and_serving_patterns](deployment_targets_and_serving_patterns/deployment_targets_and_serving_patterns.md) | module page only |
| 13 | [model_serving_platforms](model_serving_platforms/model_serving_platforms.md) | 2 files |
| 14 | [inference_engine_operations_and_benchmarking](inference_engine_operations_and_benchmarking/inference_engine_operations_and_benchmarking.md) | module page only |
| 15 | [distributed_inference_and_disaggregation](distributed_inference_and_disaggregation/distributed_inference_and_disaggregation.md) | module page only |
| 16 | [lora_adapter_serving_and_lifecycle](lora_adapter_serving_and_lifecycle/lora_adapter_serving_and_lifecycle.md) | module page only |
| 17 | [model_storage_loading_and_cold_start](model_storage_loading_and_cold_start/model_storage_loading_and_cold_start.md) | module page only |
| 18 | [inference_autoscaling_and_capacity_planning](inference_autoscaling_and_capacity_planning/inference_autoscaling_and_capacity_planning.md) | module page only |
| 19 | [progressive_delivery_for_model_servers](progressive_delivery_for_model_servers/progressive_delivery_for_model_servers.md) | module page only |
| 23 | [training_infrastructure_operations](training_infrastructure_operations/training_infrastructure_operations.md) | module page only |
| 24 | [gpu_observability_and_fleet_health](gpu_observability_and_fleet_health/gpu_observability_and_fleet_health.md) | module page only |
| 25 | [ai_gateway_operations](ai_gateway_operations/ai_gateway_operations.md) | module page only |
| 26 | [decision_models](decision_models/decision_models.md) | module page only |
| 27 | [production_quality_monitoring_and_drift](production_quality_monitoring_and_drift/production_quality_monitoring_and_drift.md) | module page only |
| 28 | [ai_incident_response_and_reliability](ai_incident_response_and_reliability/ai_incident_response_and_reliability.md) | module page only |
| 30 | [ml_supply_chain_security](ml_supply_chain_security/ml_supply_chain_security.md) | module page only |

**Not in this path** (8 of 32, Full Path only): `ml_pipelines_and_orchestration`, `continuous_training_and_refresh_policy`, `edge_and_on_device_deployment`, `gpu_cluster_scheduling_and_quotas`, `ai_networking_and_storage_fabric`, `ai_governance_and_model_risk_operations`, `ai_cost_engineering_and_unit_economics`, `ml_platform_architecture_and_build_vs_buy`
<!-- /study-path-table -->

The Senior Path is the craft: build the release pipeline, debug the serving stack, and say why a rollout broke. It covers the artifact and release mechanics, the serving platform and its scaling signals, GPU triage, and the production quality loop.

<!-- study-path-table principal -->
### Principal Path (11 modules)

| # | Module | Files |
|---|--------|-------|
| 6 | [rag_and_vector_index_lifecycle](rag_and_vector_index_lifecycle/rag_and_vector_index_lifecycle.md) | module page only |
| 9 | [evaluation_gates_and_eval_sets](evaluation_gates_and_eval_sets/evaluation_gates_and_eval_sets.md) | 2 files |
| 10 | [continuous_training_and_refresh_policy](continuous_training_and_refresh_policy/continuous_training_and_refresh_policy.md) | module page only |
| 11 | [ai_release_engineering](ai_release_engineering/ai_release_engineering.md) | module page only |
| 12 | [deployment_targets_and_serving_patterns](deployment_targets_and_serving_patterns/deployment_targets_and_serving_patterns.md) | 2 files |
| 15 | [distributed_inference_and_disaggregation](distributed_inference_and_disaggregation/distributed_inference_and_disaggregation.md) | module page only |
| 21 | [gpu_cluster_scheduling_and_quotas](gpu_cluster_scheduling_and_quotas/gpu_cluster_scheduling_and_quotas.md) | module page only |
| 28 | [ai_incident_response_and_reliability](ai_incident_response_and_reliability/ai_incident_response_and_reliability.md) | module page only |
| 29 | [ai_governance_and_model_risk_operations](ai_governance_and_model_risk_operations/ai_governance_and_model_risk_operations.md) | module page only |
| 31 | [ai_cost_engineering_and_unit_economics](ai_cost_engineering_and_unit_economics/ai_cost_engineering_and_unit_economics.md) | module page only |
| 32 | [ml_platform_architecture_and_build_vs_buy](ml_platform_architecture_and_build_vs_buy/ml_platform_architecture_and_build_vs_buy.md) | module page only |

**Not in this path** (21 of 32, Full Path only): `mlops_foundations_and_the_ai_lifecycle`, `artifact_versioning_and_lineage`, `dataset_and_label_versioning`, `prompt_lifecycle_and_versioning`, `model_registry_and_packaging`, `agent_tool_and_mcp_server_lifecycle`, `ml_pipelines_and_orchestration`, `model_serving_platforms`, `inference_engine_operations_and_benchmarking`, `lora_adapter_serving_and_lifecycle`, `model_storage_loading_and_cold_start`, `inference_autoscaling_and_capacity_planning`, `progressive_delivery_for_model_servers`, `edge_and_on_device_deployment`, `ai_networking_and_storage_fabric`, `training_infrastructure_operations`, `gpu_observability_and_fleet_health`, `ai_gateway_operations`, `decision_models`, `production_quality_monitoring_and_drift`, `ml_supply_chain_security`
<!-- /study-path-table -->

The Principal Path is the judgment: which refresh policy, scheduler, quota model or platform stack at what cost, what migration, and what not to build. It is a different cut, not senior plus extras. More than half of it is material the Senior Path never covers.

---

## Knowledge-Question Map

| If you can't answer this | Read |
|--------------------------|------|
| "Answer quality dropped overnight and nobody deployed. What changed?" | [mlops_foundations_and_the_ai_lifecycle](mlops_foundations_and_the_ai_lifecycle/mlops_foundations_and_the_ai_lifecycle.md) |
| "Which model, prompt and index versions produced this answer?" | [artifact_versioning_and_lineage](artifact_versioning_and_lineage/artifact_versioning_and_lineage.md) |
| "Which data snapshot and label guideline trained this model?" | [dataset_and_label_versioning](dataset_and_label_versioning/dataset_and_label_versioning.md) |
| "Who changed the production prompt, and did it pass a gate?" | [prompt_lifecycle_and_versioning](prompt_lifecycle_and_versioning/prompt_lifecycle_and_versioning.md) |
| "Why did retrieval go near-random after a new index went live?" | [rag_and_vector_index_lifecycle](rag_and_vector_index_lifecycle/rag_and_vector_index_lifecycle.md) |
| "Can another team's MCP server change the tools my agent sees?" | [agent_tool_and_mcp_server_lifecycle](agent_tool_and_mcp_server_lifecycle/agent_tool_and_mcp_server_lifecycle.md) |
| "Is a 1-point drop on the gate set a regression or noise?" | [statistics_of_release_gates](evaluation_gates_and_eval_sets/statistics_of_release_gates.md) |
| "How do we roll back a model, prompt and index together?" | [ai_release_engineering](ai_release_engineering/ai_release_engineering.md) |
| "Why does round-robin load balancing cut the prefix-cache hit rate?" | [distributed_inference_and_disaggregation](distributed_inference_and_disaggregation/distributed_inference_and_disaggregation.md) |
| "Why does a new replica take minutes before it serves a request?" | [model_storage_loading_and_cold_start](model_storage_loading_and_cold_start/model_storage_loading_and_cold_start.md) |
| "What should the autoscaler watch on an LLM server, if not GPU utilization?" | [inference_autoscaling_and_capacity_planning](inference_autoscaling_and_capacity_planning/inference_autoscaling_and_capacity_planning.md) |
| "GPU utilization reads 100%, so why is throughput low?" | [gpu_observability_and_fleet_health](gpu_observability_and_fleet_health/gpu_observability_and_fleet_health.md) |
| "Can training borrow the serving GPUs at night without hurting serving?" | [gpu_cluster_scheduling_and_quotas](gpu_cluster_scheduling_and_quotas/gpu_cluster_scheduling_and_quotas.md) |
| "Is the probability a decision endpoint returns calibrated?" | [decision_models](decision_models/decision_models.md) |
| "What does one successful task cost, all-in?" | [ai_cost_engineering_and_unit_economics](ai_cost_engineering_and_unit_economics/ai_cost_engineering_and_unit_economics.md) |
| "Kubeflow plus KServe plus Kueue, or a managed platform?" | [ml_platform_architecture_and_build_vs_buy](ml_platform_architecture_and_build_vs_buy/ml_platform_architecture_and_build_vs_buy.md) |

---

## Study Plan

| Window | Cover |
|--------|-------|
| **Weekend crash (2 days)** | Phase 1 modules 1-5, then `evaluation_gates_and_eval_sets`, `ai_release_engineering` and `progressive_delivery_for_model_servers`. Enough to explain the release bundle, the gate and the rollout |
| **Two weeks** | The Senior Path in order, plus `design_an_ai_release_pipeline` and `design_a_model_weight_distribution_system` |
| **One month** | Full Path phases 1-6, all eight case studies, and `cross_cutting/ai_release_bundle_and_lineage` |
| **Staff prep** | The Principal Path, then `design_an_ai_governance_and_model_inventory_platform` and `design_a_continuous_training_and_champion_challenger_system` |

---

## 4. Version Notes

The pin table in [CLAUDE.md](CLAUDE.md) is the only source for versions in this section. Each module page carries a dated version-anchor blockquote that uses rows from it. A fact not in the table is fetched from its primary source before it is written, or cut.

Rows with frequent releases: vLLM (v0.30.0, v0.31.0 and v0.32.0 shipped between 2026-09-22 and 2026-10-18), llm-d, Gateway API Inference Extension, KServe, and the decision-model APIs. The OpenAI Decisions API is in public beta as of 2026-10-18. TGI is archived (2026-03-21). Re-fetch these rows before an audit pass.

---

## 5. Tool Map

Each tool has one home page. Other pages link there.

| Tool or mechanism | Home page |
|---|---|
| Deployment strategy matrix (Recreate, rolling, blue-green, canary, shadow, A/B, rollback) | [progressive_delivery_for_model_servers](progressive_delivery_for_model_servers/progressive_delivery_for_model_servers.md) §4 |
| Release bundle manifest | [ai_release_bundle_and_lineage](case_studies/cross_cutting/ai_release_bundle_and_lineage.md) |
| OpenLineage, MLflow `LoggedModel` lineage | [artifact_versioning_and_lineage](artifact_versioning_and_lineage/artifact_versioning_and_lineage.md) |
| lakeFS, DVC, Label Studio, Argilla, Snorkel | [dataset_and_label_versioning](dataset_and_label_versioning/dataset_and_label_versioning.md) |
| MLflow Prompt Registry, Langfuse prompt management, LaunchDarkly AI Configs | [prompt_lifecycle_and_versioning](prompt_lifecycle_and_versioning/prompt_lifecycle_and_versioning.md) |
| Kubeflow Hub, ModelPack, KitOps, ORAS, Harbor | [model_registry_and_packaging](model_registry_and_packaging/model_registry_and_packaging.md) |
| MCP Registry, `server.json` | [agent_tool_and_mcp_server_lifecycle](agent_tool_and_mcp_server_lifecycle/agent_tool_and_mcp_server_lifecycle.md) |
| Kubeflow Pipelines, Metaflow, ZenML, Flyte, Dagster, Prefect, Argo Workflows, Ray Data LLM | [ml_pipelines_and_orchestration](ml_pipelines_and_orchestration/ml_pipelines_and_orchestration.md) |
| DeepEval, Ragas, promptfoo, Inspect AI, lm-eval-harness, Braintrust | [evaluation_gates_and_eval_sets](evaluation_gates_and_eval_sets/evaluation_gates_and_eval_sets.md) |
| OpenFeature, Statsig, Unleash | [ai_release_engineering](ai_release_engineering/ai_release_engineering.md) |
| `llama-server`, Ollama as a serving target | [deployment_targets_and_serving_patterns](deployment_targets_and_serving_patterns/deployment_targets_and_serving_patterns.md) |
| Ray Serve, BentoML, Seldon Core 2, MLServer, AIBrix | [model_serving_platforms](model_serving_platforms/model_serving_platforms.md) |
| KServe (InferenceService, LLMInferenceService, InferenceGraph, LocalModelCache) | [kserve_deep_dive](model_serving_platforms/kserve_deep_dive.md) |
| GuideLLM, AIPerf, GenAI-Perf, `vllm bench`, LLMPerf, `llama-bench` | [inference_engine_operations_and_benchmarking](inference_engine_operations_and_benchmarking/inference_engine_operations_and_benchmarking.md) |
| llm-d, LeaderWorkerSet, Gateway API Inference Extension, NIXL, Mooncake, LMCache, NVIDIA Dynamo | [distributed_inference_and_disaggregation](distributed_inference_and_disaggregation/distributed_inference_and_disaggregation.md) |
| LoRAX, S-LoRA and Punica kernels, ModelMesh | [lora_adapter_serving_and_lifecycle](lora_adapter_serving_and_lifecycle/lora_adapter_serving_and_lifecycle.md) |
| Model Streamer, fastsafetensors, tensorizer, Dragonfly, Kraken | [model_storage_loading_and_cold_start](model_storage_loading_and_cold_start/model_storage_loading_and_cold_start.md) |
| KEDA, HPA custom metrics, Knative KPA | [inference_autoscaling_and_capacity_planning](inference_autoscaling_and_capacity_planning/inference_autoscaling_and_capacity_planning.md) |
| Argo Rollouts, Flagger for model servers | [progressive_delivery_for_model_servers](progressive_delivery_for_model_servers/progressive_delivery_for_model_servers.md) |
| ExecuTorch, LiteRT, Core ML, MLX, ONNX Runtime GenAI, MLC LLM, WebLLM | [edge_and_on_device_deployment](edge_and_on_device_deployment/edge_and_on_device_deployment.md) |
| Kueue, Volcano, KAI Scheduler, Slurm and Slinky, YuniKorn, JobSet | [gpu_cluster_scheduling_and_quotas](gpu_cluster_scheduling_and_quotas/gpu_cluster_scheduling_and_quotas.md) |
| NVIDIA GPU Operator, AMD GPU Operator, Intel Device Plugins Operator (operators by vendor table) | [gpu_cluster_scheduling_and_quotas](gpu_cluster_scheduling_and_quotas/gpu_cluster_scheduling_and_quotas.md) |
| MIG vs MPS vs time-slicing as a capacity policy | [gpu_cluster_scheduling_and_quotas](gpu_cluster_scheduling_and_quotas/gpu_cluster_scheduling_and_quotas.md) |
| Device plugin, DRA, MIG, MPS, time-slicing mechanism | [devops/device_scheduling_and_dra](../devops/kubernetes_scheduling_and_autoscaling/device_scheduling_and_dra.md), [devops/ml_platform_and_gpu_infrastructure](../devops/ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) |
| NVIDIA Network Operator, NCCL operations, GPUDirect | [ai_networking_and_storage_fabric](ai_networking_and_storage_fabric/ai_networking_and_storage_fabric.md) |
| Kubeflow Trainer, Ray Train, torchrun elastic, SkyPilot | [training_infrastructure_operations](training_infrastructure_operations/training_infrastructure_operations.md) |
| DCGM, dcgm-exporter | [gpu_observability_and_fleet_health](gpu_observability_and_fleet_health/gpu_observability_and_fleet_health.md) |
| LiteLLM, Agent Router, vLLM Semantic Router, Portkey, Kong AI Gateway | [ai_gateway_operations](ai_gateway_operations/ai_gateway_operations.md) |
| OpenAI Decisions API, TypeSafe System One API, decision models in SGLang, vLLM, Ollama and llama.cpp | [decision_models](decision_models/decision_models.md) |
| Evidently, NannyML, Arize Phoenix, Fiddler | [production_quality_monitoring_and_drift](production_quality_monitoring_and_drift/production_quality_monitoring_and_drift.md) |
| OPA and Kyverno deploy gates for models | [ai_governance_and_model_risk_operations](ai_governance_and_model_risk_operations/ai_governance_and_model_risk_operations.md) |
| model-transparency (OpenSSF Model Signing), ModelScan, picklescan, CycloneDX ML-BOM | [ml_supply_chain_security](ml_supply_chain_security/ml_supply_chain_security.md) |
| Triton Inference Server, OpenVINO | [technologies/nvidia_triton_inference_server](../technologies/nvidia_triton_inference_server/nvidia_triton_inference_server.md), [technologies/intel_openvino](../technologies/intel_openvino/intel_openvino.md) |

---

## 6. Case Studies

11-section principal template, 900-1100 lines each, 4+ `cross_cutting/` references each.

| Case Study | Tiers | Focus |
|---|---|---|
| [Design an AI Release Pipeline](case_studies/design_an_ai_release_pipeline.md) | senior, principal | 40 AI features, about 300 bundle releases a week through gates, shadow, canary and A/B; rollback under 5 minutes |
| [Design an Embedding Model Migration](case_studies/design_an_embedding_model_migration.md) | senior, principal | 2B chunks to a new embedding model with zero downtime: dual index, re-embed cost, shadow recall, cutover and rollback |
| [Design a Multi-Tenant LoRA Serving Platform](case_studies/design_a_multi_tenant_lora_serving_platform.md) | senior | 5,000 customer adapters over 3 base models: registry, hot-load, affinity routing, base-upgrade campaign |
| [Design a Model Weight Distribution System](case_studies/design_a_model_weight_distribution_system.md) | senior | 0 to 64 nodes of a frontier MoE in under 3 minutes: OCI artifacts, P2P, NVMe cache, streaming load, warm pools |
| [Design an On-Device Model Delivery System](case_studies/design_an_on_device_model_delivery_system.md) | - | A 3B model plus adapters to 50M phones: OTA deltas, cohort rollout, device matrix, hybrid fallback |
| [Design a Continuous Training and Champion-Challenger System](case_studies/design_a_continuous_training_and_champion_challenger_system.md) | principal | CT for a scoring model with 30-day label delay: challenger shadowing, promotion rules, decision-policy versioning |
| [Design an AI Quality Incident Response Program](case_studies/design_an_ai_quality_incident_response_program.md) | principal | A silent provider model update cuts task success by 8%: quality SLOs, detection, kill switch, re-qualification |
| [Design an AI Governance and Model Inventory Platform](case_studies/design_an_ai_governance_and_model_inventory_platform.md) | principal | A bank with 1,200 models and 300 GenAI use cases: inventory, risk tiers, approvals, EU AI Act and SR 11-7 evidence |

**Cross-cutting** (excluded from the tier paths by `CS_EXCLUDE_DIRS`):

| File | Focus |
|---|---|
| [ai_release_bundle_and_lineage](case_studies/cross_cutting/ai_release_bundle_and_lineage.md) | The release-bundle manifest as a worked example: fields, digests, schema, how each module writes and reads it, lineage queries |

See [case_studies/case_studies.md](case_studies/case_studies.md) for the learning path, dependency map and interview-prep shortcuts.

---

## 7. Cross-Section Links

| For | mlops module | See also |
|---|---|---|
| Classic MLOps maturity, KFP SDK, retraining triggers | `mlops_foundations_and_the_ai_lifecycle` | [ml/mlops_and_ci_cd](../ml/mlops_and_ci_cd/mlops_and_ci_cd.md) |
| The LLM serving stack | `mlops_foundations_and_the_ai_lifecycle` | [llm/deployment_and_mlops](../llm/deployment_and_mlops/deployment_and_mlops.md) |
| Prompt-writing technique and PromptOps tooling | `prompt_lifecycle_and_versioning` | [llm/prompt_engineering](../llm/prompt_engineering/prompt_engineering.md), [llm/prompt_management_and_promptops](../llm/prompt_management_and_promptops/prompt_management_and_promptops.md) |
| RAG and vector database fundamentals | `rag_and_vector_index_lifecycle` | [llm/rag_fundamentals](../llm/rag_fundamentals/rag_fundamentals.md), [database/vector_databases](../database/vector_databases/vector_databases.md) |
| Eval design and benchmarks | `evaluation_gates_and_eval_sets` | [llm/evaluation_and_benchmarks](../llm/evaluation_and_benchmarks/evaluation_and_benchmarks.md), [llm/error_analysis_and_eval_design](../llm/error_analysis_and_eval_design/error_analysis_and_eval_design.md) |
| Engine internals | `inference_engine_operations_and_benchmarking` | [llm/inference_engines](../llm/inference_engines/inference_engines.md), [llm/vllm_deep_dive](../llm/vllm_deep_dive/vllm_deep_dive.md) |
| Generic deployment strategies | `progressive_delivery_for_model_servers` | [devops/deployment_strategies](../devops/deployment_strategies/deployment_strategies.md) |
| GPU device scheduling and DRA | `gpu_cluster_scheduling_and_quotas` | [devops/device_scheduling_and_dra](../devops/kubernetes_scheduling_and_autoscaling/device_scheduling_and_dra.md) |
| NCCL programming | `ai_networking_and_storage_fabric` | [cuda/multi_gpu_programming_and_nccl](../cuda/multi_gpu_programming_and_nccl/multi_gpu_programming_and_nccl.md) |
| Drift statistics and LLM instrumentation | `production_quality_monitoring_and_drift` | [ml/monitoring_and_drift_detection](../ml/monitoring_and_drift_detection/monitoring_and_drift_detection.md), [llm/llm_observability_and_monitoring](../llm/llm_observability_and_monitoring/llm_observability_and_monitoring.md) |
| On-call and SLO basics | `ai_incident_response_and_reliability` | [devops/incident_management_and_oncall](../devops/incident_management_and_oncall/incident_management_and_oncall.md), [devops/sre_principles_and_slos](../devops/sre_principles_and_slos/sre_principles_and_slos.md) |
| The AI law and its timeline | `ai_governance_and_model_risk_operations` | [llm/ai_regulations_and_compliance](../llm/ai_regulations_and_compliance/ai_regulations_and_compliance.md) |
| Token pricing, TCO and GPU FinOps | `ai_cost_engineering_and_unit_economics` | [llm/token_economics_and_cost_optimization](../llm/token_economics_and_cost_optimization/token_economics_and_cost_optimization.md), [devops/gpu_and_ai_finops](../devops/cloud_cost_optimization_finops/gpu_and_ai_finops.md) |

---

## 8. Build Manifest

| # | File | Status |
|---|---|---|
| 1 | `mlops_foundations_and_the_ai_lifecycle/mlops_foundations_and_the_ai_lifecycle.md` | done |
| 2 | `artifact_versioning_and_lineage/artifact_versioning_and_lineage.md` | done |
| 3 | `dataset_and_label_versioning/dataset_and_label_versioning.md` | done |
| 4 | `prompt_lifecycle_and_versioning/prompt_lifecycle_and_versioning.md` | done |
| 5 | `model_registry_and_packaging/model_registry_and_packaging.md` | done |
| 6 | `rag_and_vector_index_lifecycle/rag_and_vector_index_lifecycle.md` | done |
| 7 | `agent_tool_and_mcp_server_lifecycle/agent_tool_and_mcp_server_lifecycle.md` | done |
| 8 | `ml_pipelines_and_orchestration/ml_pipelines_and_orchestration.md` | done |
| 9 | `evaluation_gates_and_eval_sets/evaluation_gates_and_eval_sets.md` | done |
| 10 | `evaluation_gates_and_eval_sets/statistics_of_release_gates.md` | done |
| 11 | `continuous_training_and_refresh_policy/continuous_training_and_refresh_policy.md` | done |
| 12 | `ai_release_engineering/ai_release_engineering.md` | done |
| 13 | `ai_release_engineering/experimentation_for_generative_features.md` | done |
| 14 | `deployment_targets_and_serving_patterns/deployment_targets_and_serving_patterns.md` | done |
| 15 | `deployment_targets_and_serving_patterns/multi_region_and_multi_tenant_serving.md` | done |
| 16 | `model_serving_platforms/model_serving_platforms.md` | done |
| 17 | `model_serving_platforms/kserve_deep_dive.md` | done |
| 18 | `inference_engine_operations_and_benchmarking/inference_engine_operations_and_benchmarking.md` | done |
| 19 | `distributed_inference_and_disaggregation/distributed_inference_and_disaggregation.md` | done |
| 20 | `lora_adapter_serving_and_lifecycle/lora_adapter_serving_and_lifecycle.md` | done |
| 21 | `model_storage_loading_and_cold_start/model_storage_loading_and_cold_start.md` | done |
| 22 | `inference_autoscaling_and_capacity_planning/inference_autoscaling_and_capacity_planning.md` | done |
| 23 | `progressive_delivery_for_model_servers/progressive_delivery_for_model_servers.md` | done |
| 24 | `edge_and_on_device_deployment/edge_and_on_device_deployment.md` | done |
| 25 | `gpu_cluster_scheduling_and_quotas/gpu_cluster_scheduling_and_quotas.md` | done |
| 26 | `ai_networking_and_storage_fabric/ai_networking_and_storage_fabric.md` | done |
| 27 | `training_infrastructure_operations/training_infrastructure_operations.md` | done |
| 28 | `gpu_observability_and_fleet_health/gpu_observability_and_fleet_health.md` | done |
| 29 | `ai_gateway_operations/ai_gateway_operations.md` | done |
| 30 | `decision_models/decision_models.md` | done |
| 31 | `production_quality_monitoring_and_drift/production_quality_monitoring_and_drift.md` | done |
| 32 | `ai_incident_response_and_reliability/ai_incident_response_and_reliability.md` | done |
| 33 | `ai_governance_and_model_risk_operations/ai_governance_and_model_risk_operations.md` | done |
| 34 | `ml_supply_chain_security/ml_supply_chain_security.md` | done |
| 35 | `ai_cost_engineering_and_unit_economics/ai_cost_engineering_and_unit_economics.md` | done |
| 36 | `ml_platform_architecture_and_build_vs_buy/ml_platform_architecture_and_build_vs_buy.md` | done |
| 37 | `case_studies/cross_cutting/ai_release_bundle_and_lineage.md` | done |
| 38 | `case_studies/design_an_ai_release_pipeline.md` | done |
| 39 | `case_studies/design_an_embedding_model_migration.md` | done |
| 40 | `case_studies/design_a_multi_tenant_lora_serving_platform.md` | done |
| 41 | `case_studies/design_a_model_weight_distribution_system.md` | done |
| 42 | `case_studies/design_an_on_device_model_delivery_system.md` | done |
| 43 | `case_studies/design_a_continuous_training_and_champion_challenger_system.md` | done |
| 44 | `case_studies/design_an_ai_quality_incident_response_program.md` | done |
| 45 | `case_studies/design_an_ai_governance_and_model_inventory_platform.md` | done |
| 46 | `case_studies/case_studies.md` | done |

---

<!-- study-paths
     THE source of truth for this section's file inventory and study-tier membership.
     Every module, every file it owns (module page AND deep-dive sub-files), and every
     case study is listed here with the tiers it belongs to; `-` means Full path only.
     Content files carry NO structural metadata. Order is never declared here: it comes
     from STUDY_ORDER in game/app.js. The tier TABLES higher in this file are generated
     from this block by `python3 game/extract.py --write-paths`. A file on disk that is
     missing from this block, or listed here and absent from disk, FAILS
     `extract.py --strict`. cross_cutting/ files are never listed.
mlops_foundations_and_the_ai_lifecycle
  mlops_foundations_and_the_ai_lifecycle.md  senior
artifact_versioning_and_lineage
  artifact_versioning_and_lineage.md  senior
dataset_and_label_versioning
  dataset_and_label_versioning.md  senior
prompt_lifecycle_and_versioning
  prompt_lifecycle_and_versioning.md  senior
model_registry_and_packaging
  model_registry_and_packaging.md  senior
rag_and_vector_index_lifecycle
  rag_and_vector_index_lifecycle.md  senior, principal
agent_tool_and_mcp_server_lifecycle
  agent_tool_and_mcp_server_lifecycle.md  senior
ml_pipelines_and_orchestration
  ml_pipelines_and_orchestration.md  -
evaluation_gates_and_eval_sets
  evaluation_gates_and_eval_sets.md  senior, principal
  statistics_of_release_gates.md     principal
continuous_training_and_refresh_policy
  continuous_training_and_refresh_policy.md  principal
ai_release_engineering
  ai_release_engineering.md                 senior, principal
  experimentation_for_generative_features.md  senior
deployment_targets_and_serving_patterns
  deployment_targets_and_serving_patterns.md  senior, principal
  multi_region_and_multi_tenant_serving.md    principal
model_serving_platforms
  model_serving_platforms.md  senior
  kserve_deep_dive.md         senior
inference_engine_operations_and_benchmarking
  inference_engine_operations_and_benchmarking.md  senior
distributed_inference_and_disaggregation
  distributed_inference_and_disaggregation.md  senior, principal
lora_adapter_serving_and_lifecycle
  lora_adapter_serving_and_lifecycle.md  senior
model_storage_loading_and_cold_start
  model_storage_loading_and_cold_start.md  senior
inference_autoscaling_and_capacity_planning
  inference_autoscaling_and_capacity_planning.md  senior
progressive_delivery_for_model_servers
  progressive_delivery_for_model_servers.md  senior
edge_and_on_device_deployment
  edge_and_on_device_deployment.md  -
gpu_cluster_scheduling_and_quotas
  gpu_cluster_scheduling_and_quotas.md  principal
ai_networking_and_storage_fabric
  ai_networking_and_storage_fabric.md  -
training_infrastructure_operations
  training_infrastructure_operations.md  senior
gpu_observability_and_fleet_health
  gpu_observability_and_fleet_health.md  senior
ai_gateway_operations
  ai_gateway_operations.md  senior
decision_models
  decision_models.md  senior
production_quality_monitoring_and_drift
  production_quality_monitoring_and_drift.md  senior
ai_incident_response_and_reliability
  ai_incident_response_and_reliability.md  senior, principal
ai_governance_and_model_risk_operations
  ai_governance_and_model_risk_operations.md  principal
ml_supply_chain_security
  ml_supply_chain_security.md  senior
ai_cost_engineering_and_unit_economics
  ai_cost_engineering_and_unit_economics.md  principal
ml_platform_architecture_and_build_vs_buy
  ml_platform_architecture_and_build_vs_buy.md  principal
case_studies
  design_an_ai_release_pipeline.md                                senior, principal
  design_an_embedding_model_migration.md                          senior, principal
  design_a_multi_tenant_lora_serving_platform.md                  senior
  design_a_model_weight_distribution_system.md                    senior
  design_an_on_device_model_delivery_system.md                    -
  design_a_continuous_training_and_champion_challenger_system.md  principal
  design_an_ai_quality_incident_response_program.md               principal
  design_an_ai_governance_and_model_inventory_platform.md          principal
-->
