# DevOps, Cloud & Platform Engineering — Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **DevOps, SRE, Cloud, and Platform Engineering** — from Linux/OS internals and container runtimes through the full Kubernetes stack, CI/CD and GitOps, Infrastructure as Code, cloud platforms (AWS-primary, GCP/Azure compared), the observability stack, SRE practice, and DevSecOps supply-chain security. Covers everything a senior DevOps / SRE / Platform engineer is expected to know in technical interviews.

> **No runtime application** — all content is Markdown with executable-shaped YAML / HCL / Dockerfile / Bash / PromQL / Rego / Go code blocks.

> **Audited and migrated (2026-08-04).** The repo-wide factual audit is **complete for all 41
> modules** (~1,560 claims verified, ~215 corrections — commits `7f98bf4`, `9462731`,
> `ecd9414`), and every module Q&A in the section carries **an authored
> `**Short:**` MCQ summary** (first pass in commit `6ce35a6`). The 15 case studies and 8 cross-cutting
> primitives were audited too, and re-audited 2026-08-05. This section is the most
> version-exposed in the repo: see `CLAUDE.md` -> "What the audit found" before writing here,
> and audit a claim before writing a `**Short:**` line against it.

---

## Intuition

> **One-line analogy**: DevOps is the discipline of turning *"it works on my machine"* into *"it works, observably and reproducibly, on ten thousand machines that nobody logs into."*

**Mental model**: Every production system is a control loop. You declare a desired state (Git, Terraform, a Kubernetes manifest), a controller continuously reconciles reality toward it, and an observability pipeline tells you the gap between intent and reality. DevOps engineering is the practice of making that loop fast (CI/CD), safe (progressive delivery, policy gates), cheap (autoscaling, FinOps), and recoverable (DR, error budgets). Almost every tool in this section — Kubernetes controllers, ArgoCD, Terraform plan/apply, Prometheus alerting — is a variation on *declare → reconcile → observe → correct*.

**Why it matters**: Modern software organizations live and die on deployment velocity and reliability. The senior DevOps engineer is the person who can ship 100 times a day without taking the site down, prove the system is healthy with data, and restore service in minutes when it isn't. Interviews test whether you understand the *mechanics* underneath the tools — why a Pod gets OOMKilled, what etcd does during a control-plane outage, how a canary actually shifts traffic — not just which `kubectl` flag to type.

**Key insight**: The hardest problems in DevOps are not "how do I run this container" — they are **state, blast radius, and feedback latency**. Where does the source of truth live (Git vs cluster vs cloud API)? When something breaks, how many users/regions/tenants does it take down? And how long between a bad change shipping and someone (or something) noticing? Master those three axes and the rest is configuration.

---

## 1. Section Overview

This section covers:

- **Foundations** — Linux process/cgroup/namespace internals, shell automation, networking for ops (DNS, CIDR, TLS/mTLS, L4/L7 LB), Git workflows
- **Containers & Kubernetes** — Docker image internals, OCI runtimes (containerd/runc/CRI-O), the full K8s control plane and data plane, networking (CNI/Cilium/Gateway API), storage (CSI), scheduling/autoscaling (HPA/VPA/KEDA/Karpenter), security (RBAC/PSS/admission), Helm/Kustomize, Operators/CRDs
- **CI/CD & GitOps** — pipeline anatomy, GitHub Actions/GitLab CI/Jenkins/Tekton, deployment strategies (blue-green/canary/progressive delivery), ArgoCD/Flux, artifact/registry management
- **Infrastructure as Code & Config** — Terraform (state, modules, drift, remote backends), Terragrunt/Pulumi/CloudFormation/CDK/OpenTofu, Ansible/config management, secrets management (Vault, ESO, SOPS)
- **Cloud platforms** — AWS (IAM/VPC/EC2/S3/EKS/Well-Architected) as the worked default, with GCP/Azure equivalents in comparison tables; serverless/FaaS; cloud networking & CDN; cost optimization / FinOps
- **Observability & SRE** — Prometheus/PromQL/Thanos, logging (Loki/EFK), tracing (OpenTelemetry/Tempo/Jaeger), Grafana/Alertmanager, SLI/SLO/error budgets, incident management & on-call
- **DevSecOps & Reliability** — supply-chain security (SAST/DAST/SCA, image scanning, SBOM, Sigstore/SLSA), policy-as-code (OPA/Gatekeeper/Kyverno), disaster recovery, platform engineering / IDPs
- **Specialized Platforms & Performance** — ML platform / GPU infrastructure on Kubernetes (GPU Operator, MIG/time-slicing, Karpenter GPU pools, Kubeflow/Ray), event-streaming operations (Strimzi/Kafka, partition & disk sizing, consumer lag, rebalancing), performance & load testing (k6, Locust, capacity tests, CI perf gates)

**Primary cloud:** AWS. GCP and Azure equivalents are given in comparison tables where relevant.

---

## 2. Scope & Non-Overlap Boundary

This section is deliberately scoped to **not duplicate** adjacent sections. Where a topic is already covered elsewhere, this section **cross-references it and adds only the DevOps/infrastructure-specific angle**:

| Already covered in... | DevOps section does NOT re-teach | DevOps section DOES cover |
|-----------------------|----------------------------------|---------------------------|
| [`backend/service_mesh_and_service_discovery`](../backend/service_mesh_and_service_discovery/service_mesh_and_service_discovery.md) | Istio/Envoy internals, xDS, mTLS theory | Operating a mesh across clusters; mesh as a platform capability |
| [`backend/chaos_engineering`](../backend/chaos_engineering/chaos_engineering.md) | Steady-state hypothesis, fault-injection taxonomy | DR drills, GameDays as an SRE operational practice |
| [`backend/observability_and_monitoring`](../backend/observability_and_monitoring/observability_and_monitoring.md) | App-level instrumentation (Micrometer, MDC, SLO concepts in code) | Running the observability *infrastructure* — Prometheus/Loki/Tempo/Grafana/Alertmanager at scale |
| [`backend/backend_security_owasp`](../backend/backend_security_owasp/backend_security_owasp.md) | Application code vulnerabilities (SQLi/XSS/CSRF) | Supply-chain & infra security — image scanning, SBOM, signing, admission policy |
| [`backend/container_and_deployment_patterns`](../backend/container_and_deployment_patterns/container_and_deployment_patterns.md) | App-developer view of Docker/K8s deploy basics | Cluster operation, runtime internals, multi-tenant platform concerns |
| [`database/backup_recovery_and_disaster_recovery`](../database/backup_recovery_and_disaster_recovery/backup_recovery_and_disaster_recovery.md) | DB-specific PITR/WAL backup mechanics | Infra-wide multi-region DR architecture and failover |
| [`database/replication_and_high_availability`](../database/replication_and_high_availability/replication_and_high_availability.md) | DB replication/HA internals | Infra HA topology, multi-AZ/region failover |
| [`hld/`](../hld/README.md) | Distributed-systems theory (CAP, consistent hashing, sharding) | Operating those systems in production |

**DevOps owns**: Linux/OS internals for ops, cloud networking, container *runtime internals*, the full Kubernetes stack, CI/CD & GitOps, IaC, cloud platforms, the observability *infrastructure*, SRE practice (SLO math, incident command, on-call), DevSecOps supply chain, policy-as-code, infra DR, platform engineering, and FinOps.

---

## 3. Module Table

| # | Module Directory | Phase | Difficulty | Key Topics |
|---|-----------------|-------|------------|------------|
| 1 | [linux_and_os_fundamentals](linux_and_os_fundamentals/linux_and_os_fundamentals.md) | 1 — Foundations | Intermediate | Processes, signals, file descriptors, cgroups v2, namespaces, systemd, /proc, ulimits, OOM killer |
| 2 | [shell_scripting_and_automation](shell_scripting_and_automation/shell_scripting_and_automation.md) | 1 — Foundations | Beginner | Bash, `sed`/`awk`/`jq`, Python for ops, idempotent scripts, exit codes, `set -euo pipefail` |
| 3 | [networking_for_devops](networking_for_devops/networking_for_devops.md) | 1 — Foundations | Intermediate | DNS, DHCP, CIDR/subnetting, NAT, firewalls, L4/L7 load balancing, TLS/mTLS/certs, Nginx/Envoy reverse proxy |
| 4 | [version_control_and_git_workflows](version_control_and_git_workflows/version_control_and_git_workflows.md) | 1 — Foundations | Beginner | Git internals, trunk-based vs GitFlow, monorepo vs polyrepo, hooks, release tagging |
| 5 | [containers_and_docker](containers_and_docker/containers_and_docker.md) | 2 — Containers & K8s | Intermediate | Namespaces/cgroups, image layers, multi-stage builds, distroless, BuildKit, registries, Dockerfile best practices |
| 6 | [container_runtimes_and_oci](container_runtimes_and_oci/container_runtimes_and_oci.md) | 2 — Containers & K8s | Advanced | containerd, runc, CRI-O, OCI image/runtime spec, gVisor/Kata isolation, plus 1 deep-dive sub-file |
| 7 | [kubernetes_architecture](kubernetes_architecture/kubernetes_architecture.md) | 2 — Containers & K8s | Advanced | API server, etcd, scheduler, controller-manager, kubelet, kube-proxy, reconciliation loop |
| 8 | [kubernetes_workloads_and_objects](kubernetes_workloads_and_objects/kubernetes_workloads_and_objects.md) | 2 — Containers & K8s | Intermediate | Pods, Deployments, StatefulSets, DaemonSets, Jobs/CronJobs, Services, Ingress, ConfigMap/Secret |
| 9 | [kubernetes_networking](kubernetes_networking/kubernetes_networking.md) | 2 — Containers & K8s | Advanced | CNI (Calico/Cilium/eBPF), kube-proxy modes, Ingress controllers, Gateway API, NetworkPolicy, CoreDNS, plus 1 deep-dive sub-file |
| 10 | [kubernetes_storage_and_state](kubernetes_storage_and_state/kubernetes_storage_and_state.md) | 2 — Containers & K8s | Intermediate | PV/PVC, StorageClass, CSI, StatefulSet storage, volume snapshots |
| 11 | [kubernetes_scheduling_and_autoscaling](kubernetes_scheduling_and_autoscaling/kubernetes_scheduling_and_autoscaling.md) | 2 — Containers & K8s | Advanced | Affinity/taints/tolerations, requests/limits, QoS, HPA/VPA/KEDA, Cluster Autoscaler/Karpenter, PDB, plus 1 deep-dive sub-file |
| 12 | [kubernetes_security](kubernetes_security/kubernetes_security.md) | 2 — Containers & K8s | Advanced | RBAC, ServiceAccounts, Pod Security Standards, admission control, secrets-at-rest, image policy, plus 1 deep-dive sub-file |
| 13 | [helm_and_package_management](helm_and_package_management/helm_and_package_management.md) | 2 — Containers & K8s | Intermediate | Helm charts/templating/releases, Kustomize overlays, repositories |
| 14 | [kubernetes_operators_and_crds](kubernetes_operators_and_crds/kubernetes_operators_and_crds.md) | 2 — Containers & K8s | Advanced | CRDs, custom controllers, operator pattern, Operator SDK, reconcile loop |
| 15 | [ci_cd_fundamentals](ci_cd_fundamentals/ci_cd_fundamentals.md) | 3 — CI/CD & GitOps | Intermediate | Pipeline anatomy, stages, artifacts, caching, parallelism, ephemeral runners |
| 16 | [ci_cd_platforms](ci_cd_platforms/ci_cd_platforms.md) | 3 — CI/CD & GitOps | Intermediate | GitHub Actions, GitLab CI, Jenkins, Argo Workflows/Tekton, CircleCI (comparison) |
| 17 | [deployment_strategies](deployment_strategies/deployment_strategies.md) | 3 — CI/CD & GitOps | Advanced | Rolling/blue-green/canary, feature flags, progressive delivery (Argo Rollouts/Flagger) |
| 18 | [gitops_argocd_flux](gitops_argocd_flux/gitops_argocd_flux.md) | 3 — CI/CD & GitOps | Advanced | Declarative delivery, ArgoCD, Flux, drift detection, app-of-apps, sync waves, plus 1 deep-dive sub-file |
| 19 | [artifact_and_registry_management](artifact_and_registry_management/artifact_and_registry_management.md) | 3 — CI/CD & GitOps | Intermediate | Container/artifact registries, Artifactory/Nexus, SemVer, promotion across envs |
| 20 | [infrastructure_as_code_terraform](infrastructure_as_code_terraform/infrastructure_as_code_terraform.md) | 4 — IaC & Config | Advanced | Core, state, modules, providers, workspaces, drift, import, remote backends, locking |
| 21 | [terraform_advanced_and_alternatives](terraform_advanced_and_alternatives/terraform_advanced_and_alternatives.md) | 4 — IaC & Config | Advanced | Terragrunt, Pulumi, CloudFormation/CDK, OpenTofu, Terratest, policy (Sentinel/OPA) |
| 22 | [configuration_management](configuration_management/configuration_management.md) | 4 — IaC & Config | Intermediate | Ansible/Chef/Puppet/Salt, idempotency, push vs pull, immutable infra, Packer |
| 23 | [secrets_management](secrets_management/secrets_management.md) | 4 — IaC & Config | Advanced | HashiCorp Vault, dynamic secrets, AWS/GCP secret managers, External Secrets Operator, SOPS, sealed-secrets, rotation |
| 24 | [cloud_fundamentals_and_aws](cloud_fundamentals_and_aws/cloud_fundamentals_and_aws.md) | 5 — Cloud | Intermediate | IAM, VPC, EC2, S3/EBS, ELB/ALB, Route53, RDS, EKS, Well-Architected Framework |
| 25 | [gcp_and_azure_essentials](gcp_and_azure_essentials/gcp_and_azure_essentials.md) | 5 — Cloud | Intermediate | GKE/GCS/Cloud Run/IAM, AKS/Blob/Entra ID; AWS↔GCP↔Azure mapping |
| 26 | [serverless_and_faas](serverless_and_faas/serverless_and_faas.md) | 5 — Cloud | Intermediate | Lambda/Cloud Functions/Knative, cold starts, event-driven, API Gateway, Step Functions |
| 27 | [cloud_networking_and_cdn](cloud_networking_and_cdn/cloud_networking_and_cdn.md) | 5 — Cloud | Advanced | VPC peering, Transit Gateway, PrivateLink, CloudFront/Cloudflare CDN, global LB, DNS strategies |
| 28 | [cloud_cost_optimization_finops](cloud_cost_optimization_finops/cloud_cost_optimization_finops.md) | 5 — Cloud | Intermediate | Tagging, rightsizing, spot/reserved/savings plans, FinOps practice, cost allocation/showback, plus 1 deep-dive sub-file |
| 29 | [observability_metrics_prometheus](observability_metrics_prometheus/observability_metrics_prometheus.md) | 6 — Observability & SRE | Advanced | Prometheus architecture, PromQL, exporters, recording/alerting rules, Thanos/Mimir/Cortex, cardinality |
| 30 | [observability_logging](observability_logging/observability_logging.md) | 6 — Observability & SRE | Intermediate | Aggregation, EFK/ELK, Loki, structured logging, retention/sampling, parsing pipelines |
| 31 | [observability_tracing_and_otel](observability_tracing_and_otel/observability_tracing_and_otel.md) | 6 — Observability & SRE | Advanced | OpenTelemetry collector pipelines, Jaeger/Tempo, sampling, span propagation, plus 1 deep-dive sub-file |
| 32 | [visualization_and_alerting](visualization_and_alerting/visualization_and_alerting.md) | 6 — Observability & SRE | Intermediate | Grafana dashboards, Alertmanager routing, PagerDuty / Jira Service Management Operations, alert fatigue, SLO burn-rate alerts |
| 33 | [sre_principles_and_slos](sre_principles_and_slos/sre_principles_and_slos.md) | 6 — Observability & SRE | Advanced | SLI/SLO/SLA, error budgets, toil, capacity planning, Google SRE practices |
| 34 | [incident_management_and_oncall](incident_management_and_oncall/incident_management_and_oncall.md) | 6 — Observability & SRE | Intermediate | Incident command, severity levels, on-call rotations, blameless postmortems, runbooks, MTTR/MTTD |
| 35 | [devsecops_and_supply_chain_security](devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md) | 7 — DevSecOps & Reliability | Advanced | SAST/DAST/SCA, image scanning (Trivy/Grype), SBOM, Sigstore/cosign, SLSA levels, secret scanning, plus 1 deep-dive sub-file |
| 36 | [policy_as_code_and_compliance](policy_as_code_and_compliance/policy_as_code_and_compliance.md) | 7 — DevSecOps & Reliability | Advanced | OPA/Rego, Gatekeeper, Kyverno, CIS benchmarks, SOC2/PCI/HIPAA controls, admission control |
| 37 | [disaster_recovery_and_resilience](disaster_recovery_and_resilience/disaster_recovery_and_resilience.md) | 7 — DevSecOps & Reliability | Advanced | RTO/RPO, multi-region DR (active-active/passive), failover, restore drills |
| 38 | [platform_engineering_and_idp](platform_engineering_and_idp/platform_engineering_and_idp.md) | 7 — DevSecOps & Reliability | Intermediate | Internal developer platforms, Backstage, golden paths, self-service, Crossplane, plus 1 deep-dive sub-file |
| 39 | [ml_platform_and_gpu_infrastructure](ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) | 8 — Specialized Platforms & Performance | Advanced | NVIDIA GPU Operator, device plugin, MIG/time-slicing, Karpenter GPU NodePools, Kubeflow/Ray on K8s, training vs serving infra, plus 1 deep-dive sub-file |
| 40 | [event_streaming_operations](event_streaming_operations/event_streaming_operations.md) | 8 — Specialized Platforms & Performance | Advanced | Strimzi/Kafka operator, partition & disk sizing, consumer-lag monitoring, rebalancing, rack awareness, tiered storage |
| 41 | [performance_and_load_testing](performance_and_load_testing/performance_and_load_testing.md) | 8 — Specialized Platforms & Performance | Intermediate | k6, Locust, distributed load generation, soak/spike/capacity tests, latency percentiles, CI performance gates |

---

## 4. 8-Phase Learning Path

```
Phase 1 — Foundations
+------------------------------------------------------------+
|  linux_and_os_fundamentals     shell_scripting_and_        |
|  networking_for_devops         automation                  |
|  version_control_and_git_workflows                         |
+------------------------------------------------------------+
                               |
                               v
Phase 2 — Containers & Kubernetes (the interview core)
+------------------------------------------------------------+
|  containers_and_docker        container_runtimes_and_oci   |
|  kubernetes_architecture      kubernetes_workloads         |
|  kubernetes_networking        kubernetes_storage_and_state |
|  kubernetes_scheduling_and_autoscaling                     |
|  kubernetes_security          helm_and_package_management  |
|  kubernetes_operators_and_crds                             |
+------------------------------------------------------------+
                               |
            +------------------+------------------+
            v                                     v
Phase 3 — CI/CD & GitOps              Phase 4 — IaC & Config
+----------------------------+        +--------------------------+
|  ci_cd_fundamentals        |        |  infrastructure_as_code_ |
|  ci_cd_platforms           |        |  terraform               |
|  deployment_strategies     |        |  terraform_advanced_and_ |
|  gitops_argocd_flux        |        |  alternatives            |
|  artifact_and_registry_    |        |  configuration_management|
|  management                |        |  secrets_management      |
+-------------+--------------+        +------------+-------------+
              |                                    |
              +------------------+-----------------+
                                 v
Phase 5 — Cloud Platforms (AWS-primary)
+------------------------------------------------------------+
|  cloud_fundamentals_and_aws   gcp_and_azure_essentials     |
|  serverless_and_faas          cloud_networking_and_cdn     |
|  cloud_cost_optimization_finops                            |
+------------------------------------------------------------+
                               |
                               v
Phase 6 — Observability & SRE
+------------------------------------------------------------+
|  observability_metrics_prometheus   observability_logging  |
|  observability_tracing_and_otel     visualization_and_     |
|  sre_principles_and_slos            alerting               |
|  incident_management_and_oncall                            |
+------------------------------------------------------------+
                               |
                               v
Phase 7 — DevSecOps & Reliability
+------------------------------------------------------------+
|  devsecops_and_supply_chain_security                       |
|  policy_as_code_and_compliance                             |
|  disaster_recovery_and_resilience                          |
|  platform_engineering_and_idp                              |
+------------------------------------------------------------+
                               |
                               v
Phase 8 — Specialized Platforms & Performance (advanced electives)
+------------------------------------------------------------+
|  ml_platform_and_gpu_infrastructure                        |
|  event_streaming_operations                                |
|  performance_and_load_testing                              |
+------------------------------------------------------------+
```

**Dependencies to note:**
- Phase 2 (Kubernetes) depends on Phase 1 container/Linux primitives (namespaces/cgroups *are* containers).
- Phases 3 and 4 can be studied in parallel after Phase 2; they converge at deployment.
- Phase 5 (Cloud) is where IaC (Phase 4) is applied; study Terraform before/alongside the AWS module.
- Phase 6 (Observability/SRE) assumes you can deploy to Kubernetes (Phase 2) and run pipelines (Phase 3).
- Phase 7 (DevSecOps) layers gates onto the CI/CD (Phase 3) and admission control (Phase 2 security) you already understand.
- Phase 8 (Specialized Platforms) is advanced electives that build directly on Phase 2 (Kubernetes scheduling, operators, storage) and Phase 6 (SLOs, observability) — study them after the core path, not instead of it. GPU/MLOps infra and Kafka ops are both "operate a stateful, expensive, scheduling-sensitive workload on Kubernetes"; load testing closes the SRE loop by validating the capacity numbers Phase 6 asks you to commit to.

---

<!-- study-paths
     THE source of truth for this section's file inventory and study-tier membership.
     Every module, every file it owns (module page AND deep-dive sub-files), and every
     case study is listed here with the tiers it belongs to; `-` means Full path only.
     Content files carry NO structural metadata -- a deep dive holds its topic, nothing
     else. Order is never declared here: it comes from STUDY_ORDER in game/app.js.
     The tier TABLES lower in this file are generated from this block by
     `python3 game/extract.py --write-paths`. A file on disk that is missing from this
     block, or listed here and absent from disk, FAILS `extract.py --strict`.
linux_and_os_fundamentals
  linux_and_os_fundamentals.md  senior
shell_scripting_and_automation
  shell_scripting_and_automation.md  -
networking_for_devops
  networking_for_devops.md  senior
version_control_and_git_workflows
  version_control_and_git_workflows.md  -
containers_and_docker
  containers_and_docker.md  senior
container_runtimes_and_oci
  container_runtimes_and_oci.md  senior
  oci_image_formats_and_lazy_pulling.md  senior
kubernetes_architecture
  kubernetes_architecture.md  senior
kubernetes_workloads_and_objects
  kubernetes_workloads_and_objects.md  senior
kubernetes_networking
  kubernetes_networking.md  senior
  cilium_and_ebpf_dataplane.md  senior
kubernetes_storage_and_state
  kubernetes_storage_and_state.md  -
kubernetes_scheduling_and_autoscaling
  kubernetes_scheduling_and_autoscaling.md  senior
  device_scheduling_and_dra.md  senior
kubernetes_security
  kubernetes_security.md  senior
  workload_identity_and_federation.md  senior
helm_and_package_management
  helm_and_package_management.md  -
kubernetes_operators_and_crds
  kubernetes_operators_and_crds.md  -
ci_cd_fundamentals
  ci_cd_fundamentals.md  senior
ci_cd_platforms
  ci_cd_platforms.md  senior
deployment_strategies
  deployment_strategies.md  senior
gitops_argocd_flux
  gitops_argocd_flux.md  senior
  environment_promotion.md  senior
artifact_and_registry_management
  artifact_and_registry_management.md  -
infrastructure_as_code_terraform
  infrastructure_as_code_terraform.md  senior
terraform_advanced_and_alternatives
  terraform_advanced_and_alternatives.md  -
configuration_management
  configuration_management.md  -
secrets_management
  secrets_management.md  senior
cloud_fundamentals_and_aws
  cloud_fundamentals_and_aws.md  senior
gcp_and_azure_essentials
  gcp_and_azure_essentials.md  -
serverless_and_faas
  serverless_and_faas.md  senior
cloud_networking_and_cdn
  cloud_networking_and_cdn.md  senior
cloud_cost_optimization_finops
  cloud_cost_optimization_finops.md  senior, principal
  gpu_and_ai_finops.md  senior, principal
observability_metrics_prometheus
  observability_metrics_prometheus.md  senior
observability_logging
  observability_logging.md  senior
observability_tracing_and_otel
  observability_tracing_and_otel.md  senior
  opentelemetry_deep_dive.md         senior
visualization_and_alerting
  visualization_and_alerting.md  -
sre_principles_and_slos
  sre_principles_and_slos.md  senior
incident_management_and_oncall
  incident_management_and_oncall.md  senior
devsecops_and_supply_chain_security
  devsecops_and_supply_chain_security.md  senior
  regulated_supply_chain.md  senior
policy_as_code_and_compliance
  policy_as_code_and_compliance.md  -
disaster_recovery_and_resilience
  disaster_recovery_and_resilience.md  -
platform_engineering_and_idp
  platform_engineering_and_idp.md  senior, principal
  ai_workloads_and_agent_operations.md  senior, principal
ml_platform_and_gpu_infrastructure
  ml_platform_and_gpu_infrastructure.md  senior, principal
  gpu_scheduling_and_inference_ops.md  senior, principal
event_streaming_operations
  event_streaming_operations.md  -
performance_and_load_testing
  performance_and_load_testing.md  -
case_studies
  design_autoscaling_platform.md           -
  design_ci_cd_platform.md                 -
  design_container_registry.md             -
  design_gitops_delivery_pipeline.md       -
  design_incident_response_system.md       -
  design_internal_developer_platform.md    -
  design_kubernetes_platform.md            -
  design_log_aggregation_pipeline.md       -
  design_ml_platform_infrastructure.md     -
  design_multi_region_dr_architecture.md   -
  design_observability_platform.md         -
  design_secrets_management_platform.md    -
  design_zero_downtime_infra_migration.md  -
  design_gpu_training_and_inference_cluster.md  senior, principal
  design_multi_tenant_platform.md  -
-->
## Learning Paths

This section is exhaustive by design — 41 modules from Linux/OS internals and container runtimes through the full Kubernetes stack, CI/CD, IaC, cloud platforms, observability, SRE, DevSecOps, and specialized GPU/MLOps and event-streaming platforms. That is the right depth for a reference and the wrong shape for someone two weeks from an interview. So there are **two ways through it**; the browser learning game's **Study** view surfaces both as a **Full / Interview** toggle (Full is the default).

### Full Path (41 modules)

The complete curriculum in the order above — see [8-Phase Learning Path](#4-8-phase-learning-path). Use it for genuine mastery: Linux/OS and Git foundations, the full Kubernetes stack (storage, Helm, Operators/CRDs), every CI/CD and IaC alternative, GCP/Azure alongside AWS, FinOps, the complete DevSecOps & Reliability phase, and the Specialized Platforms electives (GPU/MLOps, event streaming, performance testing). Nothing is dropped.

<!-- study-path-table senior -->
### Senior Path (27 modules)

| # | Module | Files |
|---|--------|-------|
| 1 | [linux_and_os_fundamentals](linux_and_os_fundamentals/linux_and_os_fundamentals.md) | module page only |
| 3 | [networking_for_devops](networking_for_devops/networking_for_devops.md) | module page only |
| 5 | [containers_and_docker](containers_and_docker/containers_and_docker.md) | module page only |
| 6 | [container_runtimes_and_oci](container_runtimes_and_oci/container_runtimes_and_oci.md) | 2 files |
| 7 | [kubernetes_architecture](kubernetes_architecture/kubernetes_architecture.md) | module page only |
| 8 | [kubernetes_workloads_and_objects](kubernetes_workloads_and_objects/kubernetes_workloads_and_objects.md) | module page only |
| 9 | [kubernetes_networking](kubernetes_networking/kubernetes_networking.md) | 2 files |
| 11 | [kubernetes_scheduling_and_autoscaling](kubernetes_scheduling_and_autoscaling/kubernetes_scheduling_and_autoscaling.md) | 2 files |
| 12 | [kubernetes_security](kubernetes_security/kubernetes_security.md) | 2 files |
| 15 | [ci_cd_fundamentals](ci_cd_fundamentals/ci_cd_fundamentals.md) | module page only |
| 16 | [ci_cd_platforms](ci_cd_platforms/ci_cd_platforms.md) | module page only |
| 17 | [deployment_strategies](deployment_strategies/deployment_strategies.md) | module page only |
| 18 | [gitops_argocd_flux](gitops_argocd_flux/gitops_argocd_flux.md) | 2 files |
| 20 | [infrastructure_as_code_terraform](infrastructure_as_code_terraform/infrastructure_as_code_terraform.md) | module page only |
| 23 | [secrets_management](secrets_management/secrets_management.md) | module page only |
| 24 | [cloud_fundamentals_and_aws](cloud_fundamentals_and_aws/cloud_fundamentals_and_aws.md) | module page only |
| 26 | [serverless_and_faas](serverless_and_faas/serverless_and_faas.md) | module page only |
| 27 | [cloud_networking_and_cdn](cloud_networking_and_cdn/cloud_networking_and_cdn.md) | module page only |
| 28 | [cloud_cost_optimization_finops](cloud_cost_optimization_finops/cloud_cost_optimization_finops.md) | 2 files |
| 29 | [observability_metrics_prometheus](observability_metrics_prometheus/observability_metrics_prometheus.md) | module page only |
| 30 | [observability_logging](observability_logging/observability_logging.md) | module page only |
| 31 | [observability_tracing_and_otel](observability_tracing_and_otel/observability_tracing_and_otel.md) | 2 files |
| 33 | [sre_principles_and_slos](sre_principles_and_slos/sre_principles_and_slos.md) | module page only |
| 34 | [incident_management_and_oncall](incident_management_and_oncall/incident_management_and_oncall.md) | module page only |
| 35 | [devsecops_and_supply_chain_security](devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md) | 2 files |
| 38 | [platform_engineering_and_idp](platform_engineering_and_idp/platform_engineering_and_idp.md) | 2 files |
| 39 | [ml_platform_and_gpu_infrastructure](ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) | 2 files |

**Not in this path** (14 of 41, Full Path only): `shell_scripting_and_automation`, `version_control_and_git_workflows`, `kubernetes_storage_and_state`, `helm_and_package_management`, `kubernetes_operators_and_crds`, `artifact_and_registry_management`, `terraform_advanced_and_alternatives`, `configuration_management`, `gcp_and_azure_essentials`, `visualization_and_alerting`, `policy_as_code_and_compliance`, `disaster_recovery_and_resilience`, `event_streaming_operations`, `performance_and_load_testing`
<!-- /study-path-table -->

A ruthless cut to what a **senior DevOps / SRE / Platform engineering interview** actually probes, anchored on the phases this section already flags as highest-yield (the Kubernetes core, CI/CD & GitOps, and Observability & SRE). Same learning order, a strict subset of the Full Path. Each group below says why it earns senior time.

| Group | Why it's tested |
|-------|-----------------|
| Foundations | Process/cgroup/OOM-killer mechanics and DNS/CIDR/TLS/mTLS/L4-L7 load balancing are the substrate every "why did this break" question sits on |
| Kubernetes Core | The single highest-signal DevOps interview topic — control-plane reconciliation, workload objects, CNI/NetworkPolicy, HPA/VPA/Karpenter, and RBAC/Pod Security Standards come up in nearly every senior screen |
| CI/CD & GitOps | "How does a deploy actually happen" and "how does a canary shift traffic" are near-universal; push- vs pull-based GitOps is a classic tradeoff question |
| IaC & Secrets | Terraform state/plan/apply semantics and dynamic-secret rotation are the two most-probed "manage infrastructure safely" questions |
| Cloud Platforms | IAM/VPC/EC2/S3/EKS baseline literacy, cold-start/event-driven tradeoffs, and CDN/global routing — assumed knowledge in any cloud-facing interview |
| Observability | "How do you know it's broken, and where" — PromQL/cardinality control, structured logging at scale, and span propagation across services |
| SRE & Incident Response | Error-budget math and incident command/blameless postmortems separate senior candidates from mid — a near-guaranteed technical-behavioral hybrid question |

---


---

## Principal Path

The judgment cut: which approach at what cost, what fails and how you would know, what you would tell a team NOT to do. All three modules on this list are also on the Senior path; Principal reads them for the judgment questions, not for new material.

<!-- study-path-table principal -->
### Principal Path (3 modules)

| # | Module | Files |
|---|--------|-------|
| 28 | [cloud_cost_optimization_finops](cloud_cost_optimization_finops/cloud_cost_optimization_finops.md) | 2 files |
| 38 | [platform_engineering_and_idp](platform_engineering_and_idp/platform_engineering_and_idp.md) | 2 files |
| 39 | [ml_platform_and_gpu_infrastructure](ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) | 2 files |

**Not in this path** (38 of 41, Full Path only): `linux_and_os_fundamentals`, `shell_scripting_and_automation`, `networking_for_devops`, `version_control_and_git_workflows`, `containers_and_docker`, `container_runtimes_and_oci`, `kubernetes_architecture`, `kubernetes_workloads_and_objects`, `kubernetes_networking`, `kubernetes_storage_and_state`, `kubernetes_scheduling_and_autoscaling`, `kubernetes_security`, `helm_and_package_management`, `kubernetes_operators_and_crds`, `ci_cd_fundamentals`, `ci_cd_platforms`, `deployment_strategies`, `gitops_argocd_flux`, `artifact_and_registry_management`, `infrastructure_as_code_terraform`, `terraform_advanced_and_alternatives`, `configuration_management`, `secrets_management`, `cloud_fundamentals_and_aws`, `gcp_and_azure_essentials`, `serverless_and_faas`, `cloud_networking_and_cdn`, `observability_metrics_prometheus`, `observability_logging`, `observability_tracing_and_otel`, `visualization_and_alerting`, `sre_principles_and_slos`, `incident_management_and_oncall`, `devsecops_and_supply_chain_security`, `policy_as_code_and_compliance`, `disaster_recovery_and_resilience`, `event_streaming_operations`, `performance_and_load_testing`
<!-- /study-path-table -->

## Knowledge-Question Map

The highest-frequency DevOps *knowledge* questions mapped to the file that answers them. For *system design* ("design a CI/CD platform", "design an observability stack") questions, use the interview-prep shortcuts in [case_studies/case_studies.md](case_studies/case_studies.md).

| Interview question | Where the answer lives |
|--------------------|------------------------|
| Why was my Pod OOMKilled even though the node had free memory? | [linux_and_os_fundamentals](linux_and_os_fundamentals/linux_and_os_fundamentals.md) |
| DNS resolution, CIDR/subnetting, and TLS/mTLS — where does a request actually terminate and route? | [networking_for_devops](networking_for_devops/networking_for_devops.md) |
| What happens end-to-end when you `kubectl apply` a Deployment? | [kubernetes_architecture](kubernetes_architecture/kubernetes_architecture.md) |
| Deployment vs StatefulSet vs DaemonSet vs Job/CronJob — when does each apply? | [kubernetes_workloads_and_objects](kubernetes_workloads_and_objects/kubernetes_workloads_and_objects.md) |
| How does a rolling update stay zero-downtime (maxUnavailable/maxSurge, readiness probes)? | [kubernetes_workloads_and_objects](kubernetes_workloads_and_objects/kubernetes_workloads_and_objects.md), [deployment_strategies](deployment_strategies/deployment_strategies.md) |
| How does a CNI plugin wire Pod-to-Pod networking, and how does NetworkPolicy restrict it? | [kubernetes_networking](kubernetes_networking/kubernetes_networking.md) |
| Requests vs limits, QoS classes, and how HPA/VPA/Cluster Autoscaler/Karpenter interact? | [kubernetes_scheduling_and_autoscaling](kubernetes_scheduling_and_autoscaling/kubernetes_scheduling_and_autoscaling.md) |
| Design RBAC and Pod Security Standards for a multi-tenant cluster. | [kubernetes_security](kubernetes_security/kubernetes_security.md) |
| Design a CI pipeline with caching, parallel stages, and ephemeral runners — how do GitHub Actions/GitLab CI/Jenkins compare? | [ci_cd_fundamentals](ci_cd_fundamentals/ci_cd_fundamentals.md), [ci_cd_platforms](ci_cd_platforms/ci_cd_platforms.md) |
| How does a canary deployment shift traffic and decide to promote or roll back? | [deployment_strategies](deployment_strategies/deployment_strategies.md) |
| Push-based CI/CD vs pull-based GitOps (ArgoCD/Flux) — what's the real security/drift tradeoff? | [gitops_argocd_flux](gitops_argocd_flux/gitops_argocd_flux.md) |
| What is Terraform state, and why is a stale or lost state file dangerous? | [infrastructure_as_code_terraform](infrastructure_as_code_terraform/infrastructure_as_code_terraform.md) |
| Dynamic secrets (Vault) vs static cloud secrets — why does rotation matter? | [secrets_management](secrets_management/secrets_management.md) |
| Walk through IAM, VPC, and security groups for a 3-tier AWS application. | [cloud_fundamentals_and_aws](cloud_fundamentals_and_aws/cloud_fundamentals_and_aws.md) |
| Why do serverless functions have cold starts, and how do you mitigate them? | [serverless_and_faas](serverless_and_faas/serverless_and_faas.md) |
| How does a CDN plus global load balancer cut latency for users worldwide? | [cloud_networking_and_cdn](cloud_networking_and_cdn/cloud_networking_and_cdn.md) |
| Why is high-cardinality labeling dangerous in Prometheus? | [observability_metrics_prometheus](observability_metrics_prometheus/observability_metrics_prometheus.md) |
| Structured logging vs raw text at scale — why standardize on Loki/EFK? | [observability_logging](observability_logging/observability_logging.md) |
| How does a trace context propagate across service boundaries in OpenTelemetry? | [observability_tracing_and_otel](observability_tracing_and_otel/observability_tracing_and_otel.md) |
| What is an error budget, how does it gate release velocity, and how do you run a blameless postmortem? | [sre_principles_and_slos](sre_principles_and_slos/sre_principles_and_slos.md), [incident_management_and_oncall](incident_management_and_oncall/incident_management_and_oncall.md) |

---

## Study Plan

A 6-week plan over the Senior Path. Each week pairs modules with one or two case studies to rehearse the "design X" format.

| Week | Focus | Modules | Case study |
|------|-------|---------|------------|
| 1 | Foundations + Containers | [linux_and_os_fundamentals](linux_and_os_fundamentals/linux_and_os_fundamentals.md), [networking_for_devops](networking_for_devops/networking_for_devops.md), [containers_and_docker](containers_and_docker/containers_and_docker.md) | skim [Kubernetes Platform](case_studies/design_kubernetes_platform.md) |
| 2 | Kubernetes Core | [kubernetes_architecture](kubernetes_architecture/kubernetes_architecture.md), [kubernetes_workloads_and_objects](kubernetes_workloads_and_objects/kubernetes_workloads_and_objects.md), [kubernetes_networking](kubernetes_networking/kubernetes_networking.md), [kubernetes_scheduling_and_autoscaling](kubernetes_scheduling_and_autoscaling/kubernetes_scheduling_and_autoscaling.md), [kubernetes_security](kubernetes_security/kubernetes_security.md) | [Kubernetes Platform](case_studies/design_kubernetes_platform.md) (full), [Autoscaling Platform](case_studies/design_autoscaling_platform.md) |
| 3 | CI/CD & GitOps | [ci_cd_fundamentals](ci_cd_fundamentals/ci_cd_fundamentals.md), [ci_cd_platforms](ci_cd_platforms/ci_cd_platforms.md), [deployment_strategies](deployment_strategies/deployment_strategies.md), [gitops_argocd_flux](gitops_argocd_flux/gitops_argocd_flux.md) | [CI/CD Platform](case_studies/design_ci_cd_platform.md), [GitOps Delivery Pipeline](case_studies/design_gitops_delivery_pipeline.md) |
| 4 | IaC, Secrets & Cloud | [infrastructure_as_code_terraform](infrastructure_as_code_terraform/infrastructure_as_code_terraform.md), [secrets_management](secrets_management/secrets_management.md), [cloud_fundamentals_and_aws](cloud_fundamentals_and_aws/cloud_fundamentals_and_aws.md), [serverless_and_faas](serverless_and_faas/serverless_and_faas.md), [cloud_networking_and_cdn](cloud_networking_and_cdn/cloud_networking_and_cdn.md) | [Secrets Management Platform](case_studies/design_secrets_management_platform.md) |
| 5 | Observability | [observability_metrics_prometheus](observability_metrics_prometheus/observability_metrics_prometheus.md), [observability_logging](observability_logging/observability_logging.md), [observability_tracing_and_otel](observability_tracing_and_otel/observability_tracing_and_otel.md) | [Observability Platform](case_studies/design_observability_platform.md), [Log Aggregation Pipeline](case_studies/design_log_aggregation_pipeline.md) |
| 6 | SRE + Incident + drills | [sre_principles_and_slos](sre_principles_and_slos/sre_principles_and_slos.md), [incident_management_and_oncall](incident_management_and_oncall/incident_management_and_oncall.md) | [Incident Response System](case_studies/design_incident_response_system.md); 2-3 more via the [interview shortcuts](case_studies/case_studies.md) |

---

## 5. Cloud Service Mapping (AWS ↔ GCP ↔ Azure)

Worked examples use AWS; this is the quick translation table referenced throughout the section.

| Capability | AWS | GCP | Azure |
|-----------|-----|-----|-------|
| Managed Kubernetes | EKS | GKE | AKS |
| Object storage | S3 | Cloud Storage (GCS) | Blob Storage |
| Block storage | EBS | Persistent Disk | Managed Disks |
| VM compute | EC2 | Compute Engine | Virtual Machines |
| Serverless functions | Lambda | Cloud Functions | Azure Functions |
| Serverless containers | Fargate / ECS Express Mode | Cloud Run | Container Apps |
| Identity & access | IAM | Cloud IAM | Entra ID + RBAC |
| Virtual network | VPC | VPC | VNet |
| L7 load balancer | ALB | Global HTTP(S) LB | Application Gateway |
| Managed DNS | Route 53 | Cloud DNS | Azure DNS |
| Secrets | Secrets Manager / SSM | Secret Manager | Key Vault |
| Managed relational DB | RDS / Aurora | Cloud SQL / AlloyDB | Azure SQL / Flexible Server |
| Message queue | SQS / SNS | Pub/Sub | Service Bus |
| CDN | CloudFront | Cloud CDN | Azure CDN / Front Door |
| Infra-as-code native | CloudFormation / CDK | Infrastructure Manager / Config Connector | ARM / Bicep |
| Managed Prometheus | AMP | Managed Service for Prometheus | Azure Monitor managed Prometheus |

---

## 6. Top Interview Topics by Category

### Kubernetes
1. **What happens when you `kubectl apply` a Deployment?** kubectl POSTs to the API server → validated/admitted/persisted in etcd → Deployment controller creates a ReplicaSet → ReplicaSet controller creates Pods → scheduler binds Pods to nodes → kubelet pulls images and starts containers → kube-proxy/CNI wires networking. Every step is an independent control loop reading desired state from etcd.
2. **Why was my Pod OOMKilled but the node had free memory?** The container exceeded its `resources.limits.memory`; the kernel cgroup limit (not node pressure) triggered the OOM kill. Requests affect scheduling/QoS; limits enforce a hard ceiling.
3. **Requests vs limits and QoS classes.** Guaranteed (requests == limits for all containers), Burstable (requests < limits), BestEffort (none set). QoS drives eviction order under node pressure: BestEffort first, Guaranteed last.
4. **How does a rolling update avoid downtime?** `maxUnavailable`/`maxSurge` bound how many Pods are down/extra at once; readiness probes gate traffic so a new Pod receives requests only after it reports ready; the old ReplicaSet scales down only as new Pods become ready.

### CI/CD & GitOps
1. **Push-based CI/CD vs pull-based GitOps.** Push: the pipeline has cluster credentials and runs `kubectl apply`. Pull: an in-cluster agent (ArgoCD/Flux) watches Git and reconciles — no external credentials, automatic drift detection, Git as the single source of truth.
2. **How does a canary deployment shift traffic?** A controller (Argo Rollouts/Flagger) creates a canary ReplicaSet, points a weighted route (mesh/ingress) at it, queries metrics against a success threshold, and either promotes (100%) or aborts (0%) — all declaratively.

### Infrastructure as Code
1. **What is Terraform state and why does it matter?** State maps real resources to config; `plan` diffs desired config against state, not against the live cloud. Stale/lost state causes resource orphaning or destructive re-creation. Use a remote backend (S3 with `use_lockfile = true` for S3-native locking) so the team shares one locked state.
2. **How do you do a zero-downtime resource replacement?** `create_before_destroy` lifecycle, or refactor to avoid the replacing attribute; for things like an RDS instance, replacement is destructive — plan a migration, not an apply.

### Observability & SRE
1. **What is an error budget and how is it used?** `100% − SLO`. A 99.9% availability SLO permits ~43 minutes of downtime per 30-day window. Burn the budget → freeze risky launches and pay down reliability; budget remaining → ship faster. It turns reliability into a quantitative, shared decision.
2. **Why is Prometheus cardinality dangerous?** Each unique label-set is a separate time series stored in memory; a high-cardinality label (user ID, request ID) can explode series count, blow up RAM, and crash the server. Keep labels bounded; push high-cardinality data to logs/traces.

### DevSecOps
1. **How do you secure a software supply chain?** Pin and scan dependencies (SCA), scan images for CVEs (Trivy), generate an SBOM, sign artifacts (cosign/Sigstore), and enforce at admission (only signed+scanned images run). SLSA levels formalize the provenance guarantees.

---

## 7. Cross-Reference Map

| Module | Also See |
|--------|----------|
| [networking_for_devops](networking_for_devops/networking_for_devops.md) | [`backend/tcp_ip_deep_dive`](../backend/tcp_ip_deep_dive/tcp_ip_deep_dive.md), [`backend/osi_model_and_networking`](../backend/osi_model_and_networking/osi_model_and_networking.md), [cloud_networking_and_cdn](cloud_networking_and_cdn/cloud_networking_and_cdn.md) |
| [containers_and_docker](containers_and_docker/containers_and_docker.md) | [container_runtimes_and_oci](container_runtimes_and_oci/container_runtimes_and_oci.md), [linux_and_os_fundamentals](linux_and_os_fundamentals/linux_and_os_fundamentals.md), [`backend/container_and_deployment_patterns`](../backend/container_and_deployment_patterns/container_and_deployment_patterns.md) |
| [kubernetes_networking](kubernetes_networking/kubernetes_networking.md) | [`backend/service_mesh_and_service_discovery`](../backend/service_mesh_and_service_discovery/service_mesh_and_service_discovery.md), [networking_for_devops](networking_for_devops/networking_for_devops.md) |
| [kubernetes_scheduling_and_autoscaling](kubernetes_scheduling_and_autoscaling/kubernetes_scheduling_and_autoscaling.md) | [cloud_cost_optimization_finops](cloud_cost_optimization_finops/cloud_cost_optimization_finops.md), [sre_principles_and_slos](sre_principles_and_slos/sre_principles_and_slos.md) |
| [gitops_argocd_flux](gitops_argocd_flux/gitops_argocd_flux.md) | [deployment_strategies](deployment_strategies/deployment_strategies.md), [infrastructure_as_code_terraform](infrastructure_as_code_terraform/infrastructure_as_code_terraform.md) |
| [infrastructure_as_code_terraform](infrastructure_as_code_terraform/infrastructure_as_code_terraform.md) | [cloud_fundamentals_and_aws](cloud_fundamentals_and_aws/cloud_fundamentals_and_aws.md), [secrets_management](secrets_management/secrets_management.md), [policy_as_code_and_compliance](policy_as_code_and_compliance/policy_as_code_and_compliance.md) |
| [secrets_management](secrets_management/secrets_management.md) | [kubernetes_security](kubernetes_security/kubernetes_security.md), [`database/database_security_and_compliance`](../database/database_security_and_compliance/database_security_and_compliance.md) |
| [observability_tracing_and_otel](observability_tracing_and_otel/observability_tracing_and_otel.md) | [`backend/observability_and_monitoring`](../backend/observability_and_monitoring/observability_and_monitoring.md), [`llm/case_studies/cross_cutting/opentelemetry_for_llm_apps.md`](../llm/case_studies/cross_cutting/opentelemetry_for_llm_apps.md) |
| [sre_principles_and_slos](sre_principles_and_slos/sre_principles_and_slos.md) | [incident_management_and_oncall](incident_management_and_oncall/incident_management_and_oncall.md), [`backend/observability_and_monitoring`](../backend/observability_and_monitoring/observability_and_monitoring.md) |
| [disaster_recovery_and_resilience](disaster_recovery_and_resilience/disaster_recovery_and_resilience.md) | [`backend/chaos_engineering`](../backend/chaos_engineering/chaos_engineering.md), [`database/backup_recovery_and_disaster_recovery`](../database/backup_recovery_and_disaster_recovery/backup_recovery_and_disaster_recovery.md), [`database/replication_and_high_availability`](../database/replication_and_high_availability/replication_and_high_availability.md) |
| [devsecops_and_supply_chain_security](devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md) | [`backend/backend_security_owasp`](../backend/backend_security_owasp/backend_security_owasp.md), [policy_as_code_and_compliance](policy_as_code_and_compliance/policy_as_code_and_compliance.md) |
| [policy_as_code_and_compliance](policy_as_code_and_compliance/policy_as_code_and_compliance.md) | [kubernetes_security](kubernetes_security/kubernetes_security.md), [devsecops_and_supply_chain_security](devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md) |

---

## Known Gaps & Planned Work

Recorded 2026-09-04 from a verified gap review of all 51 files. Every entry below is
briefable on its own — pick one, read the entry, start. Nothing here is a blocker for the
section shipping; it is the ordered backlog of what would make it better.

### A. Version-anchor backfill — 11 of 51 module and sub-files lack the dated anchor `CLAUDE.md` mandates

`CLAUDE.md` requires a `> **Version anchor (YYYY-MM-DD).**` blockquote on every
version-exposed page. Checked 2026-10-22 over the module pages and sub-files (case studies
excluded): 40 carry one. The three below are the most version-exposed of the 11 that do
not, and should be done first, in this order.

| # | File | Why it is urgent |
|---|------|------------------|
| 1 | `configuration_management/configuration_management.md:21-31` | The densest licence/ownership block in the section — Ansible/Red Hat, Puppet/Perforce plus the OpenVox fork, Chef/Progress plus Cinc, Salt/Broadcom. Ownership claims rot faster than APIs and there is no check date. |
| 2 | `gcp_and_azure_essentials/gcp_and_azure_essentials.md` | Pins the Bicep resource version `Microsoft.ContainerService/managedClusters@2024-02-01` twice; near-certainly stale, and a stale ARM API version is a copy-paste failure, not a stylistic one. |
| 3 | `containers_and_docker/containers_and_docker.md` | Pins `node:24-slim` and `gcr.io/distroless/nodejs24-debian13` in examples readers copy verbatim. |

The other eight: `artifact_and_registry_management`, `cloud_cost_optimization_finops`,
`cloud_networking_and_cdn`, `deployment_strategies`, `disaster_recovery_and_resilience`,
`incident_management_and_oncall`, `kubernetes_operators_and_crds` (module pages), and
`devsecops_and_supply_chain_security/regulated_supply_chain.md`, which carries a dated
`Regulatory anchor` and `Tooling anchor` but not the `Version anchor` label.

### B. Thin modules — 32 of 41 sit under the section's own 700-line bar (median 567)

Ranked by interview frequency times the size of the gap, with the specific missing content
named so each is briefable without re-reading the file.

1. **`containers_and_docker` (446 lines — thinnest in the section, highest-frequency interview topic).** Missing Docker Compose entirely; `HEALTHCHECK`; Docker networking (bridge/host/none); an ENTRYPOINT-vs-CMD interaction table; rootless daemon beyond a tools row; BuildKit's LLB.
2. **`kubernetes_workloads_and_objects` (544).** Missing init containers entirely; multi-container pod patterns; native sidecars (`restartPolicy: Always` on an init container); the pod phase/lifecycle state machine; `PodDisruptionBudget` YAML; the Downward API; `externalTrafficPolicy`; Job `parallelism`/`completions`/Indexed mode; the `Recreate` strategy; `postStart`.
3. **`kubernetes_storage_and_state` (483).** CSI stays at concept level and never opens the sidecar architecture (external-provisioner/attacher/resizer/snapshotter/node-driver-registrar). Missing `fsGroup` volume ownership — the classic "mounted as root, app can't write" pitfall — plus `persistentVolumeClaimRetentionPolicy`, raw block volumes, PVC cloning, `CSIStorageCapacity` scheduling, and `subPath` gotchas.
4. **`platform_engineering_and_idp` (548).** The **only module in its cluster with no worked-math section 6 at all**, plus the SPACE/DevEx gap (see D). Its Crossplane v2 and DORA claims are correct and current; add one sentence noting v1-style XRDs still work via the `LegacyCluster` scope default, which *does* support claims — the migration escape hatch a reader will hit on their first upgrade.
5. **`devsecops_and_supply_chain_security` (466).** **Zero worked-arithmetic blocks anywhere.** Missing in-toto's layout/link model; `slsa-verifier` (never named, despite SLSA being the headline); reproducible-build mechanics. Dependency confusion sits in a Q&A rather than in section 10 where a pitfall belongs.
6. **`helm_and_package_management` (482).** Missing `configMapGenerator`/`secretGenerator` content-hash suffixing — the most-used Kustomize feature — plus Helm hook YAML, chart signing/provenance, `helm test`, and Kustomize `replacements`.
7. **`ci_cd_platforms` (512) and `ci_cd_fundamentals` (504).** Both missing concurrency groups / cancel-superseded-run, build-matrix strategy, test sharding, and monorepo path filtering. `ci_cd_platforms` additionally leans on "ephemeral runners" without ever covering runner sandboxing (gVisor/Kata/Firecracker).
8. **`artifact_and_registry_management` (442).** Section 6 has one comment-only bash block. Dependency confusion gets two words in the module whose subject *is* registry supply chain. Missing `imagePullSecrets` mechanics and air-gapped mirroring.
9. **`kubernetes_operators_and_crds` (506).** Missing garbage-collection propagation policies, conversion-webhook code, admission-webhook implementation, and server-side-apply field-manager conflicts.
10. **`observability_tracing_and_otel` (572).** A 572-line parent under a 2,037-line child.

Second tier, same treatment, lower priority:

| Module | Lines | Missing |
|--------|-------|---------|
| `kubernetes_architecture` | 673 | Watch semantics (`resourceVersion`, bookmarks, `410 Gone`); built-in admission controllers; the API aggregation layer; etcd encryption-at-rest / KMS v2; static pods; audit logging |
| `cloud_networking_and_cdn` | 738 | Origin shield; multi-CDN failover; CNAME flattening at the apex |
| `cloud_fundamentals_and_aws` | 689 | IMDSv2; SCPs and permission boundaries; VPC Flow Logs |
| `serverless_and_faas` | 617 | Function URLs; response streaming; Lambda extensions; Cloudflare Workers |
| `deployment_strategies` | 559 | Shadow/dark traffic; ring rollout; session affinity during a canary |
| `configuration_management` | 556 | Windows DSC / WinRM |
| `gcp_and_azure_essentials` | 537 | The only cloud module with no worked arithmetic; no VPC Service Controls, Shared VPC, or Azure Policy vs RBAC |
| `container_runtimes_and_oci` | 487 | containerd runtime-handler registration; CDI; user namespaces |
| `version_control_and_git_workflows` | 494 | Commit signing — absent from the Git module |

### C. Content gaps

- **IPv6 and dual-stack are absent section-wide.** `networking_for_devops/networking_for_devops.md` has exactly one IPv6 mention (a table row near line 150) and every CIDR and subnetting example is IPv4-only. **`dual-stack` has zero occurrences in any of the 51 files**, including `kubernetes_networking`, where dual-stack Services (`ipFamilyPolicy`, `ipFamilies`) have been GA since 1.23. Two-module fix: address plan and subnetting in `networking_for_devops`, Service/Pod dual-stack in `kubernetes_networking`.
- **VictoriaMetrics is missing from the module pages.** `observability_metrics_prometheus/observability_metrics_prometheus.md` around line 92 and its section 11 name Thanos, Cortex and Mimir and stop. VictoriaMetrics appears only inside two case studies. Add it to the section 4 comparison and to section 11.

### D. Case studies

- **Three sit below the 900-line principal floor:** `design_ml_platform_infrastructure` (720), `design_incident_response_system` (799), `design_log_aggregation_pipeline` (846).
- **`design_multi_tenant_platform.md` has 1 inline `cross_cutting/` link against a 4+ bar.** Its cross-links are parked in a `## Cross-References` appendix after section 11 — a workaround, not compliance. Inline them where the primitive is actually used.
- **Real, unacknowledged duplication: `design_ml_platform_infrastructure` vs `design_gpu_training_and_inference_cluster`.** Both cover self-service submission, GPU quota / fair-share / borrowing / preemption, MIG vs time-slicing, scale-to-zero serving, and Spot plus checkpointing — and **neither cross-references the other**. `gpu_training` is the deeper, more current treatment. **Re-scope rather than delete:** shrink `ml_platform` to pipelines, experiment tracking and model registry, and delegate GPU scheduling to `gpu_training` the same way it already delegates cluster tenancy to `design_kubernetes_platform`.
- **Numeric inconsistency across two case studies describing what reads as the same org:** `design_observability_platform` says 20 TB/day with 90-day cold retention; `design_log_aggregation_pipeline` says 50 TB/day with 1-year retention. Reconcile or explicitly separate the two scenarios.
- **`design_kubernetes_platform` Decision 1 re-derives** the namespace / vCluster / cluster-per-tenant comparison that `design_multi_tenant_platform` expands into a four-rung threat-modeled ladder. Make Decision 1 link forward instead of re-deriving, or the two will drift.

### E. Proposed modules (6)

Each is justified by a primitive the section already uses but no module owns.

| # | Module | Placement | Justification |
|---|--------|-----------|---------------|
| 1 | `multi_cluster_and_fleet_management` | after `kubernetes_operators_and_crds` | Cluster API, Karmada / Open Cluster Management, fleet upgrade orchestration, placement policy. **Cluster API today appears only inside a case study** — a case study carrying a primitive no module owns. |
| 2 | `runtime_security_and_ebpf_observability` | after `kubernetes_security` | Falco appears in one file only; Tetragon only inside the Cilium sub-file; **AppArmor is absent section-wide**. This section owns eBPF-for-networking and `backend/` owns eBPF-for-performance; **nobody owns eBPF-for-security**. |
| 3 | `certificate_and_pki_lifecycle` | after `secrets_management` | cert-manager peaks at 4 mentions and is never explained. ACME and DNS-01, private CA hierarchy, mTLS rotation at fleet scale, and the expiry outage as an incident class. |
| 4 | `continuous_profiling` | after `observability_tracing_and_otel` | **Zero hits section-wide** for Pyroscope, Parca or the phrase "continuous profiling". The OTel deep dive covers the Profiles *signal*; nothing covers the practice. |
| 5 | `developer_experience_metrics` (or a large section-expansion of `platform_engineering_and_idp`) | with or inside `platform_engineering_and_idp` | **SPACE, DevEx and "four keys" have zero occurrences section-wide**; DORA stands alone. The section's own note that DORA's benchmark bands are unsourceable strengthens the case for teaching the frameworks rather than the numbers. |
| 6 | `capacity_and_quota_management` | after `kubernetes_scheduling_and_autoscaling` | `ResourceQuota`, `LimitRange` and `PriorityClass` live almost entirely in two case studies. Add cloud service quotas, quota exhaustion as an incident class, headroom math, and preemption ordering. |

**Already covered — checked, do not re-commission:** eBPF/Cilium dataplane, progressive delivery and feature flags, supply-chain attestation, database operators, cost attribution.

### F. Open decision for the owner

The Principal tier currently has **3 modules** (`cloud_cost_optimization_finops`,
`platform_engineering_and_idp`, `ml_platform_and_gpu_infrastructure`). That cut omits
`sre_principles_and_slos`, `disaster_recovery_and_resilience`, `kubernetes_architecture`
and the multi-tenancy material — precisely the "which approach at what cost, what fails and
how you would know" judgment content the tier is defined as. Widening it is a deliberate
curation call, not a defect to fix automatically. **Needs an owner decision.**

---

## 8. Build Status & Implementation Tracker

> **BUILD COMPLETE through Chunk 11.** Core (chunks 0–10): 38 modules + 6 cross-cutting primitives + 12 case studies + finalized `case_studies/case_studies.md`. **Phase 8 expansion (chunk 11): 41 modules total + 7 cross-cutting primitives + 13 case studies.** Later additions bring the section to 41 modules + 10 deep-dive sub-files + 15 case studies + 8 cross-cutting primitives. No NEXT UP pointer remains.
>
> Chunk 11 added Phase 8 — Specialized Platforms & Performance: 3 modules (`ml_platform_and_gpu_infrastructure`, `event_streaming_operations`, `performance_and_load_testing`), 1 case study (`design_ml_platform_infrastructure`), 1 cross-cutting primitive (`gpu_node_lifecycle`). Future additions should append modules/case studies following the "Adding a DevOps module" instructions in CLAUDE.md and flip new rows below.

### Chunk Plan

| Chunk | Contents | Status |
|-------|----------|--------|
| **0 — Scaffold** | `devops/README.md`, `case_studies/case_studies.md` skeleton, root `README.md` + `CLAUDE.md` + `MEMORY.md` references | done |
| **1** | Phase 1 modules 1–4 | done |
| **2** | Phase 2 modules 5–9 (containers + K8s arch/workloads/networking) | done |
| **3** | Phase 2 modules 10–14 (K8s storage/scheduling/security/helm/operators) | done |
| **4** | Phase 3 modules 15–19 (CI/CD & GitOps) | done |
| **5** | Phase 4 modules 20–23 (IaC & config) | done |
| **6** | Phase 5 modules 24–28 (Cloud) | done |
| **7** | Phase 6 modules 29–34 (Observability & SRE) | done |
| **8** | Phase 7 modules 35–38 (DevSecOps & reliability) | done |
| **9** | 6 cross-cutting primitives + case studies 1–6 | done |
| **10** | Case studies 7–12 + finalize `case_studies/case_studies.md` | done |
| **11 — Phase 8 expansion** | Modules 39–41 (GPU/MLOps, streaming ops, perf/load testing) + case study `design_ml_platform_infrastructure` + cross-cutting `gpu_node_lifecycle` | done |

### Module File Status

| # | Module | Phase | Chunk | Status | Q&A Floor |
|---|--------|-------|-------|--------|-----------|
| 1 | `linux_and_os_fundamentals/linux_and_os_fundamentals.md` | 1 | 1 | done | 15 |
| 2 | `shell_scripting_and_automation/shell_scripting_and_automation.md` | 1 | 1 | done | 15 |
| 3 | `networking_for_devops/networking_for_devops.md` | 1 | 1 | done | 15 |
| 4 | `version_control_and_git_workflows/version_control_and_git_workflows.md` | 1 | 1 | done | 15 |
| 5 | `containers_and_docker/containers_and_docker.md` | 2 | 2 | done | 15 |
| 6 | `container_runtimes_and_oci/container_runtimes_and_oci.md` | 2 | 2 | done | 15 |
| 6a | `container_runtimes_and_oci/oci_image_formats_and_lazy_pulling.md` | 2 | - | done | 15 |
| 7 | `kubernetes_architecture/kubernetes_architecture.md` | 2 | 2 | done | 18 |
| 8 | `kubernetes_workloads_and_objects/kubernetes_workloads_and_objects.md` | 2 | 2 | done | 15 |
| 9 | `kubernetes_networking/kubernetes_networking.md` | 2 | 2 | done | 18 |
| 9a | `kubernetes_networking/cilium_and_ebpf_dataplane.md` | 2 | - | done | 15 |
| 10 | `kubernetes_storage_and_state/kubernetes_storage_and_state.md` | 2 | 3 | done | 15 |
| 11 | `kubernetes_scheduling_and_autoscaling/kubernetes_scheduling_and_autoscaling.md` | 2 | 3 | done | 15 |
| 11a | `kubernetes_scheduling_and_autoscaling/device_scheduling_and_dra.md` | 2 | - | done | 15 |
| 12 | `kubernetes_security/kubernetes_security.md` | 2 | 3 | done | 18 |
| 12a | `kubernetes_security/workload_identity_and_federation.md` | 2 | - | done | 15 |
| 13 | `helm_and_package_management/helm_and_package_management.md` | 2 | 3 | done | 15 |
| 14 | `kubernetes_operators_and_crds/kubernetes_operators_and_crds.md` | 2 | 3 | done | 15 |
| 15 | `ci_cd_fundamentals/ci_cd_fundamentals.md` | 3 | 4 | done | 15 |
| 16 | `ci_cd_platforms/ci_cd_platforms.md` | 3 | 4 | done | 15 |
| 17 | `deployment_strategies/deployment_strategies.md` | 3 | 4 | done | 15 |
| 18 | `gitops_argocd_flux/gitops_argocd_flux.md` | 3 | 4 | done | 15 |
| 18a | `gitops_argocd_flux/environment_promotion.md` | 3 | - | done | 15 |
| 19 | `artifact_and_registry_management/artifact_and_registry_management.md` | 3 | 4 | done | 15 |
| 20 | `infrastructure_as_code_terraform/infrastructure_as_code_terraform.md` | 4 | 5 | done | 18 |
| 21 | `terraform_advanced_and_alternatives/terraform_advanced_and_alternatives.md` | 4 | 5 | done | 15 |
| 22 | `configuration_management/configuration_management.md` | 4 | 5 | done | 15 |
| 23 | `secrets_management/secrets_management.md` | 4 | 5 | done | 15 |
| 24 | `cloud_fundamentals_and_aws/cloud_fundamentals_and_aws.md` | 5 | 6 | done | 15 |
| 25 | `gcp_and_azure_essentials/gcp_and_azure_essentials.md` | 5 | 6 | done | 15 |
| 26 | `serverless_and_faas/serverless_and_faas.md` | 5 | 6 | done | 15 |
| 27 | `cloud_networking_and_cdn/cloud_networking_and_cdn.md` | 5 | 6 | done | 15 |
| 28 | `cloud_cost_optimization_finops/cloud_cost_optimization_finops.md` | 5 | 6 | done | 15 |
| 28a | `cloud_cost_optimization_finops/gpu_and_ai_finops.md` | 5 | - | done | 15 |
| 29 | `observability_metrics_prometheus/observability_metrics_prometheus.md` | 6 | 7 | done | 18 |
| 30 | `observability_logging/observability_logging.md` | 6 | 7 | done | 15 |
| 31 | `observability_tracing_and_otel/observability_tracing_and_otel.md` | 6 | 7 | done | 15 |
| 31a | `observability_tracing_and_otel/opentelemetry_deep_dive.md` | 6 | 7 | done | 15 |
| 32 | `visualization_and_alerting/visualization_and_alerting.md` | 6 | 7 | done | 15 |
| 33 | `sre_principles_and_slos/sre_principles_and_slos.md` | 6 | 7 | done | 18 |
| 34 | `incident_management_and_oncall/incident_management_and_oncall.md` | 6 | 7 | done | 15 |
| 35 | `devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md` | 7 | 8 | done | 15 |
| 35a | `devsecops_and_supply_chain_security/regulated_supply_chain.md` | 7 | - | done | 15 |
| 36 | `policy_as_code_and_compliance/policy_as_code_and_compliance.md` | 7 | 8 | done | 15 |
| 37 | `disaster_recovery_and_resilience/disaster_recovery_and_resilience.md` | 7 | 8 | done | 15 |
| 38 | `platform_engineering_and_idp/platform_engineering_and_idp.md` | 7 | 8 | done | 15 |
| 38a | `platform_engineering_and_idp/ai_workloads_and_agent_operations.md` | 7 | - | done | 15 |
| 39 | `ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md` | 8 | 11 | done | 18 |
| 39a | `ml_platform_and_gpu_infrastructure/gpu_scheduling_and_inference_ops.md` | 8 | - | done | 15 |
| 40 | `event_streaming_operations/event_streaming_operations.md` | 8 | 11 | done | 15 |
| 41 | `performance_and_load_testing/performance_and_load_testing.md` | 8 | 11 | done | 15 |

### Case Study & Cross-Cutting File Status

| File | Chunk | Status |
|------|-------|--------|
| `case_studies/cross_cutting/kubernetes_production_hardening.md` | 9 | done |
| `case_studies/cross_cutting/terraform_state_at_scale.md` | 9 | done |
| `case_studies/cross_cutting/prometheus_cardinality_and_scale.md` | 9 | done |
| `case_studies/cross_cutting/slo_error_budget_math.md` | 9 | done |
| `case_studies/cross_cutting/supply_chain_security_pipeline.md` | 9 | done |
| `case_studies/cross_cutting/multi_cluster_networking.md` | 9 | done |
| `case_studies/design_ci_cd_platform.md` | 9 | done |
| `case_studies/design_kubernetes_platform.md` | 9 | done |
| `case_studies/design_observability_platform.md` | 9 | done |
| `case_studies/design_gitops_delivery_pipeline.md` | 9 | done |
| `case_studies/design_secrets_management_platform.md` | 9 | done |
| `case_studies/design_multi_region_dr_architecture.md` | 9 | done |
| `case_studies/design_autoscaling_platform.md` | 10 | done |
| `case_studies/design_log_aggregation_pipeline.md` | 10 | done |
| `case_studies/design_internal_developer_platform.md` | 10 | done |
| `case_studies/design_incident_response_system.md` | 10 | done |
| `case_studies/design_container_registry.md` | 10 | done |
| `case_studies/design_zero_downtime_infra_migration.md` | 10 | done |
| `case_studies/cross_cutting/gpu_node_lifecycle.md` | 11 | done |
| `case_studies/design_ml_platform_infrastructure.md` | 11 | done |
| `case_studies/cross_cutting/dra_and_device_plugins.md` | - | done |
| `case_studies/design_multi_tenant_platform.md` | - | done |
| `case_studies/design_gpu_training_and_inference_cluster.md` | - | done |

### Conventions Reminder (for future chunk agents)

```
MODULE TEMPLATE — 14-section canonical scheme (matches the rest of the repo):
  ## 1. Concept Overview
  ## 2. Intuition     (> blockquote analogy + Mental model + Why it matters + Key insight)
  ## 3. Core Principles
  ## 4. Types / Architectures / Strategies
  ## 5. Architecture Diagrams            (Mermaid preferred; ASCII only for grids/masks)
  ## 6. How It Works — Detailed Mechanics   (real YAML/HCL/Bash/PromQL/Go, concrete numbers)
  ## 7. Real-World Examples
  ## 8. Tradeoffs                        (comparison tables)
  ## 9. When to Use / When NOT to Use
  ## 10. Common Pitfalls                 (# BROKEN -> # FIX pattern, at least 1 required)
  ## 11. Technologies & Tools            (comparison table)
  ## 12. Interview Questions with Answers  (bold Q, plain A; targets in tables above)
  ## 13. Best Practices
  ## 14. Case Study   (scenario + diagram + real code + BROKEN/FIX + metrics + Discussion Qs)

  The page is named for its folder — `<module>/<module>.md`. A `README.md` below the section
  root is NOT a module page and is invisible to `extract.py`.

QUALITY BAR:
  - 700-1000 lines per module page. Measured 2026-08-25: 30 of 41 modules are under 700, so
    treat this as the target for a NEW deep module, not as a claim about the current section.
  - Q&A floor is 15 per module, repo-wide and hard (root CLAUDE.md). Seven deep modules aim
    for 18+: kubernetes_architecture, kubernetes_networking, kubernetes_security,
    infrastructure_as_code_terraform, observability_metrics_prometheus, sre_principles_and_slos,
    ml_platform_and_gpu_infrastructure. CLAUDE.md's list is the authority; the "Q&A Target"
    column in the status table above restates it and nothing else.
  - Every Q&A carries an authored **Short:** line as the FIRST non-blank line of its answer
    (15-220 chars, self-contained, no code fence, no list). If you change what an answer says,
    update its **Short:** in the same edit — a stale one ships as the CORRECT MCQ option.
  - Version-sensitive claims carry a dated version anchor blockquote under the H1. Pattern:
    observability_tracing_and_otel/opentelemetry_deep_dive.md line 3. This section is the
    repo's most version-exposed; the anchor is what makes the next audit cheap.
  - At least 1 BROKEN->FIX block in §10 and at least 1 in §14
  - AWS as the default cloud; GCP/Azure in comparison tables (see §5 mapping)
  - Concrete numbers everywhere (etcd 2 GiB default --quota-backend-bytes, HPA 15s sync,
    Prometheus 1m default scrape_interval / 2h block, K8s 110 pods/node default,
    cgroup v2 memory.high, etc.) — no "a few"/"some". VERIFY the number before writing it:
    the audit's largest class was wrong numbers in modules nobody suspected.
  - Mermaid diagrams in fenced mermaid blocks, One-Dark classDef on every node, flowchart LR
    default, rounded shapes, short labels wrapped with <br/>. Validate with
    `node scripts/check_render.mjs <file>` — a fence that fails to parse takes the deploy red.
    ASCII stays only for constraint grids, alignment-critical maps and vector geometry.
  - No emojis; --- between every top-level section
  - Em-dash in §6 heading: "## 6. How It Works — Detailed Mechanics"
  - Cross-link to other sections via relative paths, always to the PAGE not the directory:
    ../backend/x/x.md, ../database/x/x.md, ../hld/x/x.md

CASE STUDY TEMPLATE — 11-section principal template (matches llm/ml):
  Reference file: ../llm/case_studies/design_gpu_inference_platform.md
  Intuition -> 1. Requirements -> 2. Scale Estimation -> 3. High-Level Architecture
  -> 4. Component Deep Dives -> 5. Design Decisions & Tradeoffs -> 6. Real-World Implementations
  -> 7. Technologies & Tools -> 8. Operational Playbook -> 9. Common Pitfalls & War Stories
  -> 10. Capacity Planning -> 11. Interview Discussion Points (10+ Q&As)
  900-1100 lines; min 4 cross-refs to cross_cutting/; real code in §4; broken-then-fix once;
  §6 names actual companies; §9 has quantified impact ($/users/SLA).

MAINTENANCE RULE when adding a file:
  1. Add the file's line to the <!-- study-paths --> block in THIS README, tagging the tiers
     it belongs to ("-" = Full path only), IN THE SAME COMMIT. A file on disk missing from the
     block — or listed there and absent from disk — is FATAL under extract.py --strict.
     Content files carry NO structural metadata; never put a study-paths block in a module page.
  2. Add a new MODULE dir to STUDY_ORDER.devops in game/app.js at its phase position. A new
     deep-dive SUB-FILE needs no entry — it groups under its parent via splitModulePath().
  3. Add a row to the tables above
  4. Update case_studies/case_studies.md if new case studies were added
  5. Update root README.md and CLAUDE.md counts if the total changed
  6. Run `python3 game/extract.py --write-paths` (regenerates the tier tables between the
     <!-- study-path-table --> markers — never hand-edit those), then `--strict`, then the
     full scripts/build_banks.sh, whose own verifier runs AFTER --strict and can still fail
```

---

## Getting Started

Recommended order for interview preparation:

1. **Week 1 — Foundations + Containers**: Phase 1, then `containers_and_docker` → `kubernetes_architecture`. The Kubernetes control plane is the single highest-signal interview topic.
2. **Week 2 — Kubernetes depth + CI/CD**: finish Phase 2, then Phase 3 (pipelines, GitOps, deployment strategies).
3. **Week 3 — IaC + Cloud**: Phase 4 (Terraform first), then Phase 5 (AWS-primary).
4. **Week 4 — Observability, SRE, Security**: Phase 6 (Prometheus + SLOs are most-tested), then Phase 7.
5. **Review**: work the case studies end-to-end — see [case_studies/case_studies.md](case_studies/case_studies.md) for the guided path.

Each module follows the standard 14-section template. See [`../llm/foundations_and_architecture/foundations_and_architecture.md`](../llm/foundations_and_architecture/foundations_and_architecture.md) as the format reference, and [`../llm/case_studies/design_gpu_inference_platform.md`](../llm/case_studies/design_gpu_inference_platform.md) for the principal case-study format.
