# DevOps Case Studies — Learning Path

13 principal-grade case studies + 7 cross-cutting infrastructure deep-dives.

Each case study follows the 11-section principal template (reference: [`../../llm/case_studies/design_gpu_inference_platform.md`](../../llm/case_studies/design_gpu_inference_platform.md)): requirements clarification, scale estimation, ASCII architecture diagrams, executable-shaped YAML/HCL/Bash/Go, real-world implementations at named companies, an operational playbook (eval/observability/runbooks), quantified war stories, capacity planning, and 10+ design-rationale interview Q&As. Each cross-cutting file is 600–800 lines covering one infrastructure primitive that recurs across many platforms.

> **Build status**: complete. All 13 case studies and 7 cross-cutting files are written. See the [Build Status & Implementation Tracker](../README.md#8-build-status--implementation-tracker) in the master index for the per-file record.

---

## Quick Start (if you only have time for three)

| Order | File | Why start here |
|-------|------|----------------|
| 1 | [design_kubernetes_platform.md](./design_kubernetes_platform.md) | The substrate everything else runs on — multi-cluster, multi-tenant K8s platform. Covers control-plane scaling, tenant isolation, autoscaling, and the operator pattern end-to-end. |
| 2 | [design_ci_cd_platform.md](./design_ci_cd_platform.md) | How change reaches production safely at scale — ephemeral runners, artifact caching, pipeline isolation. The delivery backbone every org needs. |
| 3 | [design_observability_platform.md](./design_observability_platform.md) | You cannot operate what you cannot see — metrics + logs + traces at scale (Prometheus/Thanos + Loki + Tempo), with cardinality control and retention tiers. |

---

## Full Learning Path

Case studies are grouped by the primary engineering concern they teach, not by product category.

### Phase A — Platform Substrate

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_kubernetes_platform.md](./design_kubernetes_platform.md) | Multi-tenant cluster operation | Control-plane scaling, namespace/tenant isolation, admission policy, Karpenter node autoscaling, operator-based self-service |
| [design_autoscaling_platform.md](./design_autoscaling_platform.md) | Cost-aware elasticity | HPA/VPA/KEDA event-driven scaling, Karpenter consolidation, scale-to-zero, the requests/limits/QoS tradeoff at scale |
| [design_internal_developer_platform.md](./design_internal_developer_platform.md) | Developer self-service | Backstage golden paths, Crossplane infra abstraction, paved-road vs flexibility tradeoff |

### Phase B — Delivery & Change Management

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_ci_cd_platform.md](./design_ci_cd_platform.md) | Build/test/deploy at scale | Ephemeral runner pools, distributed artifact cache, pipeline isolation, secrets injection |
| [design_gitops_delivery_pipeline.md](./design_gitops_delivery_pipeline.md) | Declarative progressive delivery | ArgoCD app-of-apps, canary via Argo Rollouts, metric-gated promotion, automated rollback |
| [design_container_registry.md](./design_container_registry.md) | Trusted artifact distribution | Image scanning, cosign signing, promotion across environments, admission enforcement |
| [design_zero_downtime_infra_migration.md](./design_zero_downtime_infra_migration.md) | Large-scale cutover | Strangler-fig infra migration, dual-run, traffic shifting, rollback safety |

### Phase C — Operability & Reliability

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_observability_platform.md](./design_observability_platform.md) | Telemetry at scale | Prometheus/Thanos federation, Loki log tiers, Tempo tracing, cardinality budgets, retention economics |
| [design_log_aggregation_pipeline.md](./design_log_aggregation_pipeline.md) | High-volume ingestion | Backpressure, buffering, parsing pipelines, hot/warm/cold retention, cost per GB |
| [design_incident_response_system.md](./design_incident_response_system.md) | Operational response | Alert routing, on-call escalation, SLO burn-rate alerting, postmortem workflow, MTTR reduction |
| [design_multi_region_dr_architecture.md](./design_multi_region_dr_architecture.md) | Survive a region loss | RTO/RPO targets, active-active vs active-passive, failover automation, restore drills |

### Phase D — Security & Secrets

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_secrets_management_platform.md](./design_secrets_management_platform.md) | Org-scale secret distribution | Vault dynamic secrets, External Secrets Operator, rotation, lease/revocation, audit |

### Phase E — Specialized Platforms

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_ml_platform_infrastructure.md](./design_ml_platform_infrastructure.md) | GPU economics & ML self-service | Multi-tenant GPU scheduling (Kueue gang/quota/borrowing), MIG vs time-slicing, Karpenter Spot/On-Demand pools, KServe scale-to-zero, raising fleet utilization from 22% to 65% |

---

## Cross-Cutting Infrastructure Files

These seven files live in `cross_cutting/` and are referenced by multiple case studies. Read each alongside the phase where it becomes relevant.

| When to Read | File | What It Covers |
|--------------|------|----------------|
| Phase A | [kubernetes_production_hardening.md](./cross_cutting/kubernetes_production_hardening.md) | Resource limits/QoS, PodDisruptionBudgets, probe design, security context, multi-tenant isolation |
| Phase E | [gpu_node_lifecycle.md](./cross_cutting/gpu_node_lifecycle.md) | GPU node bring-up (driver/toolkit/device-plugin), readiness gating via startup taints, DCGM health fencing, Spot interruption + checkpoint, graceful drain, decommission |
| Phase A | [multi_cluster_networking.md](./cross_cutting/multi_cluster_networking.md) | Cluster mesh, cross-cluster service discovery, global load balancing, failover routing |
| Phase B | [terraform_state_at_scale.md](./cross_cutting/terraform_state_at_scale.md) | Remote state, locking, workspace strategy, state splitting, blast-radius containment |
| Phase B | [supply_chain_security_pipeline.md](./cross_cutting/supply_chain_security_pipeline.md) | Sign → scan → SBOM → admission gate end-to-end; SLSA provenance |
| Phase C | [prometheus_cardinality_and_scale.md](./cross_cutting/prometheus_cardinality_and_scale.md) | Label hygiene, recording rules, series-count budgeting, long-term storage sizing |
| Phase C | [slo_error_budget_math.md](./cross_cutting/slo_error_budget_math.md) | Burn-rate alerts, multi-window multi-burn-rate, budget accounting, alert thresholds |

---

## Dependency Map

Some case studies build on patterns established by others.

```
design_kubernetes_platform
    +-> design_autoscaling_platform        (adds cost-aware elasticity)
    +-> design_internal_developer_platform (adds self-service abstraction)
    +-> design_gitops_delivery_pipeline    (delivery onto the platform)

design_ci_cd_platform
    +-> design_gitops_delivery_pipeline    (pull-based delivery layer)
    +-> design_container_registry          (artifact source of truth)

design_observability_platform
    +-> design_log_aggregation_pipeline    (logging sub-system at scale)
    +-> design_incident_response_system    (alerting/SLO consumption)

design_multi_region_dr_architecture
    +-> design_zero_downtime_infra_migration (shares traffic-shifting + cutover)

design_kubernetes_platform
    +-> design_autoscaling_platform
            +-> design_ml_platform_infrastructure (GPU scheduling + Spot autoscaling
                                                   on top of the autoscaling platform)
```

---

## Interview Prep Shortcuts

| Interview Topic | Best Case Study |
|----------------|-----------------|
| "Design a Kubernetes platform for many teams" | [design_kubernetes_platform.md](./design_kubernetes_platform.md) |
| "Design a CI/CD system" | [design_ci_cd_platform.md](./design_ci_cd_platform.md) |
| "How would you do progressive delivery / canaries?" | [design_gitops_delivery_pipeline.md](./design_gitops_delivery_pipeline.md) |
| "Design a metrics/monitoring system at scale" | [design_observability_platform.md](./design_observability_platform.md) + [cross_cutting/prometheus_cardinality_and_scale.md](./cross_cutting/prometheus_cardinality_and_scale.md) |
| "Design a logging pipeline" | [design_log_aggregation_pipeline.md](./design_log_aggregation_pipeline.md) |
| "How do you handle secrets at scale?" | [design_secrets_management_platform.md](./design_secrets_management_platform.md) |
| "Design for multi-region disaster recovery" | [design_multi_region_dr_architecture.md](./design_multi_region_dr_architecture.md) |
| "How do you autoscale cost-effectively?" | [design_autoscaling_platform.md](./design_autoscaling_platform.md) |
| "Design an internal developer platform" | [design_internal_developer_platform.md](./design_internal_developer_platform.md) |
| "Secure the software supply chain" | [cross_cutting/supply_chain_security_pipeline.md](./cross_cutting/supply_chain_security_pipeline.md) |
| "Design incident response / on-call" | [design_incident_response_system.md](./design_incident_response_system.md) |
| "Design an ML platform / GPU infrastructure" | [design_ml_platform_infrastructure.md](./design_ml_platform_infrastructure.md) + [cross_cutting/gpu_node_lifecycle.md](./cross_cutting/gpu_node_lifecycle.md) |
| "How do you schedule/share GPUs on Kubernetes?" | [design_ml_platform_infrastructure.md](./design_ml_platform_infrastructure.md) |
