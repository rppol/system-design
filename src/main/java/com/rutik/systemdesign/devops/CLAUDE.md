# DevOps Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/devops/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §8 — check NEXT UP pointer and per-file status before starting a new module.

---

## Module List — 41 Modules (8 Phases)

AWS is the default cloud in worked examples; GCP/Azure appear in comparison tables. This section deliberately **cross-references** `backend/`, `database/`, and `hld/` instead of duplicating them — see the non-overlap boundary in `README.md` §2.

| Phase | Modules |
|-------|---------|
| 1 — Foundations | linux_and_os_fundamentals, shell_scripting_and_automation, networking_for_devops, version_control_and_git_workflows |
| 2 — Containers & Kubernetes | containers_and_docker, container_runtimes_and_oci, kubernetes_architecture, kubernetes_workloads_and_objects, kubernetes_networking, kubernetes_storage_and_state, kubernetes_scheduling_and_autoscaling, kubernetes_security, helm_and_package_management, kubernetes_operators_and_crds |
| 3 — CI/CD & GitOps | ci_cd_fundamentals, ci_cd_platforms, deployment_strategies, gitops_argocd_flux, artifact_and_registry_management |
| 4 — IaC & Config | infrastructure_as_code_terraform, terraform_advanced_and_alternatives, configuration_management, secrets_management |
| 5 — Cloud (AWS-primary) | cloud_fundamentals_and_aws, gcp_and_azure_essentials, serverless_and_faas, cloud_networking_and_cdn, cloud_cost_optimization_finops |
| 6 — Observability & SRE | observability_metrics_prometheus, observability_logging, observability_tracing_and_otel, visualization_and_alerting, sre_principles_and_slos, incident_management_and_oncall |
| 7 — DevSecOps & Reliability | devsecops_and_supply_chain_security, policy_as_code_and_compliance, disaster_recovery_and_resilience, platform_engineering_and_idp |
| 8 — Specialized Platforms & Performance | ml_platform_and_gpu_infrastructure, event_streaming_operations, performance_and_load_testing |

**Q&A floor:** 15 per module (root CLAUDE.md hard floor). Deep modules aim higher (18+): kubernetes_architecture, kubernetes_networking, kubernetes_security, infrastructure_as_code_terraform, observability_metrics_prometheus, sre_principles_and_slos, ml_platform_and_gpu_infrastructure

Backfill status (2026-07-16): the floor backlog is fully cleared — every module repo-wide now meets the 15-question floor (all former 11-14 modules raised to 16 across backend, database, devops, fastapi, hld, and python).

---

## Learning Paths (Full + Interview-Specific)

`README.md` documents two routes: the **Full Path** (all 41 modules = "8-Phase Learning
Path") and a curated **Interview-Specific Path** (22 modules). The interview subset is a
**dual-source list** — it lives in both `README.md` ("## Learning Paths") and
`game/app.js` (`STUDY_PATHS.devops.interview`, which drives the game's Study
Full/Interview toggle). **Change one, change the other** — same modules, same order.
Non-Q&A narrative only; no `extract.py` re-run needed. The README also carries a
Knowledge-Question Map and a 6-week Study Plan (interview-readiness prose; no toggle impact).

---

## Case Studies — 13 Total

`case_studies/` — all use the 11-section principal template.
Reference: `../llm/case_studies/design_gpu_inference_platform.md`
Learning-path index: `case_studies/README.md` (mandatory; update with every new case study).

design_ci_cd_platform, design_kubernetes_platform, design_observability_platform, design_gitops_delivery_pipeline, design_secrets_management_platform, design_multi_region_dr_architecture, design_autoscaling_platform, design_log_aggregation_pipeline, design_internal_developer_platform, design_incident_response_system, design_container_registry, design_zero_downtime_infra_migration, design_ml_platform_infrastructure

---

## Cross-Cutting Shared Primitives — 7 Files

`case_studies/cross_cutting/` — all use the 14-section template:

| File | When Relevant |
|------|--------------|
| `kubernetes_production_hardening/` | Any case study deploying to Kubernetes |
| `terraform_state_at_scale/` | Any case study using infrastructure as code |
| `prometheus_cardinality_and_scale/` | Any observability case study |
| `slo_error_budget_math/` | Any case study with SRE/reliability requirements |
| `supply_chain_security_pipeline/` | Any CI/CD case study |
| `multi_cluster_networking/` | Any multi-region or multi-cluster case study |
| `gpu_node_lifecycle/` | Any ML platform or GPU infrastructure case study |

---

## Cross-Reference Map

| DevOps Module | See Also (other sections) |
|--------------|--------------------------|
| `kubernetes_workloads_and_objects/` | `../../backend/container_and_deployment_patterns/` — 12-factor, K8s strategies |
| `observability_tracing_and_otel/` | `../../backend/observability_and_monitoring/` — Micrometer, MDC; `../../spring/observability_and_tracing/` |
| `event_streaming_operations/` | `../../backend/kafka_deep_dive/` — Kafka internals; `../../backend/event_driven_fundamentals/` |
| `ml_platform_and_gpu_infrastructure/` | `../../ml/gpu_and_hardware_optimization/` — CUDA profiling; `../../ml/mlops_and_ci_cd/` |

---

## Content Rules (DevOps-specific)

- Code in realistic YAML / HCL / Dockerfile / Bash / PromQL / Rego / Go
- AWS-default with GCP/Azure in comparison tables
- Concrete numbers everywhere; at least 1 BROKEN→FIX block in §10 and §14
- No emojis; ASCII diagrams only

## Build Manifest

See `README.md` §8 for the authoritative per-file `pending`/`done` status table and NEXT UP pointer.

On finishing a module/chunk:
1. Flip status to `done` in `README.md` §8
2. Advance NEXT UP pointer
3. Update `case_studies/README.md` + root `README.md` + this CLAUDE.md if applicable

## Adding a New DevOps Module

1. Create `<module_name>/README.md` — 14-section template; 15 Q&As minimum (root CLAUDE.md hard floor; 18+ for deep modules listed above)
2. Follow DevOps-specific content rules above
3. Update `README.md` module table AND flip the file's status in the §8 build manifest
4. Update root `README.md` DevOps phase table
5. Update root `CLAUDE.md` DevOps module table

---

## Visual Intuition Diagrams

Section 5 (Architecture Diagrams) and any hard-to-picture concept should use an
**ASCII visual intuition diagram** that makes an abstract relationship visible
(constraint grid, before/after-with-delta, stacked flow, routing fan-out, bar
chart, or curve/sketch). Generate and validate them with the
`/visual-intuition-diagrams` skill. The full archetype catalog, conventions
(ASCII only, no tabs, no emojis, widest line <= 100 cols, caption every diagram),
and the `diagram_tools.py` validator live in root `CLAUDE.md` -> "Visual Intuition
Diagrams".
