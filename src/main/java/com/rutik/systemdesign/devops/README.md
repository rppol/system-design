# DevOps, Cloud & Platform Engineering — Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **DevOps, SRE, Cloud, and Platform Engineering** — from Linux/OS internals and container runtimes through the full Kubernetes stack, CI/CD and GitOps, Infrastructure as Code, cloud platforms (AWS-primary, GCP/Azure compared), the observability stack, SRE practice, and DevSecOps supply-chain security. Covers everything a senior DevOps / SRE / Platform engineer is expected to know in technical interviews.

> **No runtime application** — all content is Markdown with executable-shaped YAML / HCL / Dockerfile / Bash / PromQL / Rego / Go code blocks.

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
| [`backend/service_mesh_and_service_discovery`](../backend/service_mesh_and_service_discovery/) | Istio/Envoy internals, xDS, mTLS theory | Operating a mesh across clusters; mesh as a platform capability |
| [`backend/chaos_engineering`](../backend/chaos_engineering/) | Steady-state hypothesis, fault-injection taxonomy | DR drills, GameDays as an SRE operational practice |
| [`backend/observability_and_monitoring`](../backend/observability_and_monitoring/) | App-level instrumentation (Micrometer, MDC, SLO concepts in code) | Running the observability *infrastructure* — Prometheus/Loki/Tempo/Grafana/Alertmanager at scale |
| [`backend/backend_security_owasp`](../backend/backend_security_owasp/) | Application code vulnerabilities (SQLi/XSS/CSRF) | Supply-chain & infra security — image scanning, SBOM, signing, admission policy |
| [`backend/container_and_deployment_patterns`](../backend/container_and_deployment_patterns/) | App-developer view of Docker/K8s deploy basics | Cluster operation, runtime internals, multi-tenant platform concerns |
| [`database/backup_recovery_and_disaster_recovery`](../database/backup_recovery_and_disaster_recovery/) | DB-specific PITR/WAL backup mechanics | Infra-wide multi-region DR architecture and failover |
| [`database/replication_and_high_availability`](../database/replication_and_high_availability/) | DB replication/HA internals | Infra HA topology, multi-AZ/region failover |
| [`hld/`](../hld/) | Distributed-systems theory (CAP, consistent hashing, sharding) | Operating those systems in production |

**DevOps owns**: Linux/OS internals for ops, cloud networking, container *runtime internals*, the full Kubernetes stack, CI/CD & GitOps, IaC, cloud platforms, the observability *infrastructure*, SRE practice (SLO math, incident command, on-call), DevSecOps supply chain, policy-as-code, infra DR, platform engineering, and FinOps.

---

## 3. Module Table

| # | Module Directory | Phase | Difficulty | Key Topics |
|---|-----------------|-------|------------|------------|
| 1 | [linux_and_os_fundamentals](linux_and_os_fundamentals/) | 1 — Foundations | Intermediate | Processes, signals, file descriptors, cgroups v2, namespaces, systemd, /proc, ulimits, OOM killer |
| 2 | [shell_scripting_and_automation](shell_scripting_and_automation/) | 1 — Foundations | Beginner | Bash, `sed`/`awk`/`jq`, Python for ops, idempotent scripts, exit codes, `set -euo pipefail` |
| 3 | [networking_for_devops](networking_for_devops/) | 1 — Foundations | Intermediate | DNS, DHCP, CIDR/subnetting, NAT, firewalls, L4/L7 load balancing, TLS/mTLS/certs, Nginx/Envoy reverse proxy |
| 4 | [version_control_and_git_workflows](version_control_and_git_workflows/) | 1 — Foundations | Beginner | Git internals, trunk-based vs GitFlow, monorepo vs polyrepo, hooks, release tagging |
| 5 | [containers_and_docker](containers_and_docker/) | 2 — Containers & K8s | Intermediate | Namespaces/cgroups, image layers, multi-stage builds, distroless, BuildKit, registries, Dockerfile best practices |
| 6 | [container_runtimes_and_oci](container_runtimes_and_oci/) | 2 — Containers & K8s | Advanced | containerd, runc, CRI-O, OCI image/runtime spec, gVisor/Kata isolation |
| 7 | [kubernetes_architecture](kubernetes_architecture/) | 2 — Containers & K8s | Advanced | API server, etcd, scheduler, controller-manager, kubelet, kube-proxy, reconciliation loop |
| 8 | [kubernetes_workloads_and_objects](kubernetes_workloads_and_objects/) | 2 — Containers & K8s | Intermediate | Pods, Deployments, StatefulSets, DaemonSets, Jobs/CronJobs, Services, Ingress, ConfigMap/Secret |
| 9 | [kubernetes_networking](kubernetes_networking/) | 2 — Containers & K8s | Advanced | CNI (Calico/Cilium/eBPF), kube-proxy modes, Ingress controllers, Gateway API, NetworkPolicy, CoreDNS |
| 10 | [kubernetes_storage_and_state](kubernetes_storage_and_state/) | 2 — Containers & K8s | Intermediate | PV/PVC, StorageClass, CSI, StatefulSet storage, volume snapshots |
| 11 | [kubernetes_scheduling_and_autoscaling](kubernetes_scheduling_and_autoscaling/) | 2 — Containers & K8s | Advanced | Affinity/taints/tolerations, requests/limits, QoS, HPA/VPA/KEDA, Cluster Autoscaler/Karpenter, PDB |
| 12 | [kubernetes_security](kubernetes_security/) | 2 — Containers & K8s | Advanced | RBAC, ServiceAccounts, Pod Security Standards, admission control, secrets-at-rest, image policy |
| 13 | [helm_and_package_management](helm_and_package_management/) | 2 — Containers & K8s | Intermediate | Helm charts/templating/releases, Kustomize overlays, repositories |
| 14 | [kubernetes_operators_and_crds](kubernetes_operators_and_crds/) | 2 — Containers & K8s | Advanced | CRDs, custom controllers, operator pattern, Operator SDK, reconcile loop |
| 15 | [ci_cd_fundamentals](ci_cd_fundamentals/) | 3 — CI/CD & GitOps | Intermediate | Pipeline anatomy, stages, artifacts, caching, parallelism, ephemeral runners |
| 16 | [ci_cd_platforms](ci_cd_platforms/) | 3 — CI/CD & GitOps | Intermediate | GitHub Actions, GitLab CI, Jenkins, Argo Workflows/Tekton, CircleCI (comparison) |
| 17 | [deployment_strategies](deployment_strategies/) | 3 — CI/CD & GitOps | Advanced | Rolling/blue-green/canary, feature flags, progressive delivery (Argo Rollouts/Flagger) |
| 18 | [gitops_argocd_flux](gitops_argocd_flux/) | 3 — CI/CD & GitOps | Advanced | Declarative delivery, ArgoCD, Flux, drift detection, app-of-apps, sync waves |
| 19 | [artifact_and_registry_management](artifact_and_registry_management/) | 3 — CI/CD & GitOps | Intermediate | Container/artifact registries, Artifactory/Nexus, SemVer, promotion across envs |
| 20 | [infrastructure_as_code_terraform](infrastructure_as_code_terraform/) | 4 — IaC & Config | Advanced | Core, state, modules, providers, workspaces, drift, import, remote backends, locking |
| 21 | [terraform_advanced_and_alternatives](terraform_advanced_and_alternatives/) | 4 — IaC & Config | Advanced | Terragrunt, Pulumi, CloudFormation/CDK, OpenTofu, Terratest, policy (Sentinel/OPA) |
| 22 | [configuration_management](configuration_management/) | 4 — IaC & Config | Intermediate | Ansible/Chef/Puppet/Salt, idempotency, push vs pull, immutable infra, Packer |
| 23 | [secrets_management](secrets_management/) | 4 — IaC & Config | Advanced | HashiCorp Vault, dynamic secrets, AWS/GCP secret managers, External Secrets Operator, SOPS, sealed-secrets, rotation |
| 24 | [cloud_fundamentals_and_aws](cloud_fundamentals_and_aws/) | 5 — Cloud | Intermediate | IAM, VPC, EC2, S3/EBS, ELB/ALB, Route53, RDS, EKS, Well-Architected Framework |
| 25 | [gcp_and_azure_essentials](gcp_and_azure_essentials/) | 5 — Cloud | Intermediate | GKE/GCS/Cloud Run/IAM, AKS/Blob/Entra ID; AWS↔GCP↔Azure mapping |
| 26 | [serverless_and_faas](serverless_and_faas/) | 5 — Cloud | Intermediate | Lambda/Cloud Functions/Knative, cold starts, event-driven, API Gateway, Step Functions |
| 27 | [cloud_networking_and_cdn](cloud_networking_and_cdn/) | 5 — Cloud | Advanced | VPC peering, Transit Gateway, PrivateLink, CloudFront/Cloudflare CDN, global LB, DNS strategies |
| 28 | [cloud_cost_optimization_finops](cloud_cost_optimization_finops/) | 5 — Cloud | Intermediate | Tagging, rightsizing, spot/reserved/savings plans, FinOps practice, cost allocation/showback |
| 29 | [observability_metrics_prometheus](observability_metrics_prometheus/) | 6 — Observability & SRE | Advanced | Prometheus architecture, PromQL, exporters, recording/alerting rules, Thanos/Mimir/Cortex, cardinality |
| 30 | [observability_logging](observability_logging/) | 6 — Observability & SRE | Intermediate | Aggregation, EFK/ELK, Loki, structured logging, retention/sampling, parsing pipelines |
| 31 | [observability_tracing_and_otel](observability_tracing_and_otel/) | 6 — Observability & SRE | Advanced | OpenTelemetry collector pipelines, Jaeger/Tempo, sampling, span propagation |
| 32 | [visualization_and_alerting](visualization_and_alerting/) | 6 — Observability & SRE | Intermediate | Grafana dashboards, Alertmanager routing, PagerDuty/OpsGenie, alert fatigue, SLO burn-rate alerts |
| 33 | [sre_principles_and_slos](sre_principles_and_slos/) | 6 — Observability & SRE | Advanced | SLI/SLO/SLA, error budgets, toil, capacity planning, Google SRE practices |
| 34 | [incident_management_and_oncall](incident_management_and_oncall/) | 6 — Observability & SRE | Intermediate | Incident command, severity levels, on-call rotations, blameless postmortems, runbooks, MTTR/MTTD |
| 35 | [devsecops_and_supply_chain_security](devsecops_and_supply_chain_security/) | 7 — DevSecOps & Reliability | Advanced | SAST/DAST/SCA, image scanning (Trivy/Grype), SBOM, Sigstore/cosign, SLSA levels, secret scanning |
| 36 | [policy_as_code_and_compliance](policy_as_code_and_compliance/) | 7 — DevSecOps & Reliability | Advanced | OPA/Rego, Gatekeeper, Kyverno, CIS benchmarks, SOC2/PCI/HIPAA controls, admission control |
| 37 | [disaster_recovery_and_resilience](disaster_recovery_and_resilience/) | 7 — DevSecOps & Reliability | Advanced | RTO/RPO, multi-region DR (active-active/passive), failover, restore drills |
| 38 | [platform_engineering_and_idp](platform_engineering_and_idp/) | 7 — DevSecOps & Reliability | Intermediate | Internal developer platforms, Backstage, golden paths, self-service, Crossplane |
| 39 | [ml_platform_and_gpu_infrastructure](ml_platform_and_gpu_infrastructure/) | 8 — Specialized Platforms & Performance | Advanced | NVIDIA GPU Operator, device plugin, MIG/time-slicing, Karpenter GPU NodePools, Kubeflow/Ray on K8s, training vs serving infra |
| 40 | [event_streaming_operations](event_streaming_operations/) | 8 — Specialized Platforms & Performance | Advanced | Strimzi/Kafka operator, partition & disk sizing, consumer-lag monitoring, rebalancing, rack awareness, tiered storage |
| 41 | [performance_and_load_testing](performance_and_load_testing/) | 8 — Specialized Platforms & Performance | Intermediate | k6, Locust, distributed load generation, soak/spike/capacity tests, latency percentiles, CI performance gates |

---

## 4. 7-Phase Learning Path

```
Phase 1 — Foundations
+------------------------------------------------------------+
|  linux_and_os_fundamentals     shell_scripting_and_         |
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

## 5. Cloud Service Mapping (AWS ↔ GCP ↔ Azure)

Worked examples use AWS; this is the quick translation table referenced throughout the section.

| Capability | AWS | GCP | Azure |
|-----------|-----|-----|-------|
| Managed Kubernetes | EKS | GKE | AKS |
| Object storage | S3 | Cloud Storage (GCS) | Blob Storage |
| Block storage | EBS | Persistent Disk | Managed Disks |
| VM compute | EC2 | Compute Engine | Virtual Machines |
| Serverless functions | Lambda | Cloud Functions | Azure Functions |
| Serverless containers | Fargate / App Runner | Cloud Run | Container Apps |
| Identity & access | IAM | Cloud IAM | Entra ID + RBAC |
| Virtual network | VPC | VPC | VNet |
| L7 load balancer | ALB | Global HTTP(S) LB | Application Gateway |
| Managed DNS | Route 53 | Cloud DNS | Azure DNS |
| Secrets | Secrets Manager / SSM | Secret Manager | Key Vault |
| Managed relational DB | RDS / Aurora | Cloud SQL / AlloyDB | Azure SQL / Flexible Server |
| Message queue | SQS / SNS | Pub/Sub | Service Bus |
| CDN | CloudFront | Cloud CDN | Azure CDN / Front Door |
| Infra-as-code native | CloudFormation / CDK | Deployment Manager / Config Connector | ARM / Bicep |
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
1. **What is Terraform state and why does it matter?** State maps real resources to config; `plan` diffs desired config against state, not against the live cloud. Stale/lost state causes resource orphaning or destructive re-creation. Use a remote backend (S3 + DynamoDB lock) so the team shares one locked state.
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
| [networking_for_devops](networking_for_devops/) | [`backend/tcp_ip_deep_dive`](../backend/tcp_ip_deep_dive/), [`backend/osi_model_and_networking`](../backend/osi_model_and_networking/), [cloud_networking_and_cdn](cloud_networking_and_cdn/) |
| [containers_and_docker](containers_and_docker/) | [container_runtimes_and_oci](container_runtimes_and_oci/), [linux_and_os_fundamentals](linux_and_os_fundamentals/), [`backend/container_and_deployment_patterns`](../backend/container_and_deployment_patterns/) |
| [kubernetes_networking](kubernetes_networking/) | [`backend/service_mesh_and_service_discovery`](../backend/service_mesh_and_service_discovery/), [networking_for_devops](networking_for_devops/) |
| [kubernetes_scheduling_and_autoscaling](kubernetes_scheduling_and_autoscaling/) | [cloud_cost_optimization_finops](cloud_cost_optimization_finops/), [sre_principles_and_slos](sre_principles_and_slos/) |
| [gitops_argocd_flux](gitops_argocd_flux/) | [deployment_strategies](deployment_strategies/), [infrastructure_as_code_terraform](infrastructure_as_code_terraform/) |
| [infrastructure_as_code_terraform](infrastructure_as_code_terraform/) | [cloud_fundamentals_and_aws](cloud_fundamentals_and_aws/), [secrets_management](secrets_management/), [policy_as_code_and_compliance](policy_as_code_and_compliance/) |
| [secrets_management](secrets_management/) | [kubernetes_security](kubernetes_security/), [`database/database_security_and_compliance`](../database/database_security_and_compliance/) |
| [observability_tracing_and_otel](observability_tracing_and_otel/) | [`backend/observability_and_monitoring`](../backend/observability_and_monitoring/), [`llm/case_studies/cross_cutting/opentelemetry_for_llm_apps.md`](../llm/case_studies/cross_cutting/opentelemetry_for_llm_apps.md) |
| [sre_principles_and_slos](sre_principles_and_slos/) | [incident_management_and_oncall](incident_management_and_oncall/), [`backend/observability_and_monitoring`](../backend/observability_and_monitoring/) |
| [disaster_recovery_and_resilience](disaster_recovery_and_resilience/) | [`backend/chaos_engineering`](../backend/chaos_engineering/), [`database/backup_recovery_and_disaster_recovery`](../database/backup_recovery_and_disaster_recovery/), [`database/replication_and_high_availability`](../database/replication_and_high_availability/) |
| [devsecops_and_supply_chain_security](devsecops_and_supply_chain_security/) | [`backend/backend_security_owasp`](../backend/backend_security_owasp/), [policy_as_code_and_compliance](policy_as_code_and_compliance/) |
| [policy_as_code_and_compliance](policy_as_code_and_compliance/) | [kubernetes_security](kubernetes_security/), [devsecops_and_supply_chain_security](devsecops_and_supply_chain_security/) |

---

## 8. Build Status & Implementation Tracker

> **BUILD COMPLETE through Chunk 11.** Core (chunks 0–10): 38 modules + 6 cross-cutting primitives + 12 case studies + finalized `case_studies/README.md`. **Phase 8 expansion (chunk 11): 41 modules total + 7 cross-cutting primitives + 13 case studies.** No NEXT UP pointer remains.
>
> Chunk 11 added Phase 8 — Specialized Platforms & Performance: 3 modules (`ml_platform_and_gpu_infrastructure`, `event_streaming_operations`, `performance_and_load_testing`), 1 case study (`design_ml_platform_infrastructure`), 1 cross-cutting primitive (`gpu_node_lifecycle`). Future additions should append modules/case studies following the "Adding a DevOps module" instructions in CLAUDE.md and flip new rows below.

### Chunk Plan

| Chunk | Contents | Status |
|-------|----------|--------|
| **0 — Scaffold** | `devops/README.md`, `case_studies/README.md` skeleton, root `README.md` + `CLAUDE.md` + `MEMORY.md` references | done |
| **1** | Phase 1 modules 1–4 | done |
| **2** | Phase 2 modules 5–9 (containers + K8s arch/workloads/networking) | done |
| **3** | Phase 2 modules 10–14 (K8s storage/scheduling/security/helm/operators) | done |
| **4** | Phase 3 modules 15–19 (CI/CD & GitOps) | done |
| **5** | Phase 4 modules 20–23 (IaC & config) | done |
| **6** | Phase 5 modules 24–28 (Cloud) | done |
| **7** | Phase 6 modules 29–34 (Observability & SRE) | done |
| **8** | Phase 7 modules 35–38 (DevSecOps & reliability) | done |
| **9** | 6 cross-cutting primitives + case studies 1–6 | done |
| **10** | Case studies 7–12 + finalize `case_studies/README.md` | done |
| **11 — Phase 8 expansion** | Modules 39–41 (GPU/MLOps, streaming ops, perf/load testing) + case study `design_ml_platform_infrastructure` + cross-cutting `gpu_node_lifecycle` | done |

### Module File Status

| # | Module | Phase | Chunk | Status | Q&A Target |
|---|--------|-------|-------|--------|-----------|
| 1 | `linux_and_os_fundamentals/README.md` | 1 | 1 | done | 12 |
| 2 | `shell_scripting_and_automation/README.md` | 1 | 1 | done | 10 |
| 3 | `networking_for_devops/README.md` | 1 | 1 | done | 12 |
| 4 | `version_control_and_git_workflows/README.md` | 1 | 1 | done | 10 |
| 5 | `containers_and_docker/README.md` | 2 | 2 | done | 12 |
| 6 | `container_runtimes_and_oci/README.md` | 2 | 2 | done | 12 |
| 7 | `kubernetes_architecture/README.md` | 2 | 2 | done | 15 |
| 8 | `kubernetes_workloads_and_objects/README.md` | 2 | 2 | done | 12 |
| 9 | `kubernetes_networking/README.md` | 2 | 2 | done | 15 |
| 10 | `kubernetes_storage_and_state/README.md` | 2 | 3 | done | 12 |
| 11 | `kubernetes_scheduling_and_autoscaling/README.md` | 2 | 3 | done | 12 |
| 12 | `kubernetes_security/README.md` | 2 | 3 | done | 15 |
| 13 | `helm_and_package_management/README.md` | 2 | 3 | done | 10 |
| 14 | `kubernetes_operators_and_crds/README.md` | 2 | 3 | done | 12 |
| 15 | `ci_cd_fundamentals/README.md` | 3 | 4 | done | 12 |
| 16 | `ci_cd_platforms/README.md` | 3 | 4 | done | 10 |
| 17 | `deployment_strategies/README.md` | 3 | 4 | done | 12 |
| 18 | `gitops_argocd_flux/README.md` | 3 | 4 | done | 12 |
| 19 | `artifact_and_registry_management/README.md` | 3 | 4 | done | 10 |
| 20 | `infrastructure_as_code_terraform/README.md` | 4 | 5 | done | 15 |
| 21 | `terraform_advanced_and_alternatives/README.md` | 4 | 5 | done | 12 |
| 22 | `configuration_management/README.md` | 4 | 5 | done | 10 |
| 23 | `secrets_management/README.md` | 4 | 5 | done | 12 |
| 24 | `cloud_fundamentals_and_aws/README.md` | 5 | 6 | done | 12 |
| 25 | `gcp_and_azure_essentials/README.md` | 5 | 6 | done | 10 |
| 26 | `serverless_and_faas/README.md` | 5 | 6 | done | 12 |
| 27 | `cloud_networking_and_cdn/README.md` | 5 | 6 | done | 12 |
| 28 | `cloud_cost_optimization_finops/README.md` | 5 | 6 | done | 10 |
| 29 | `observability_metrics_prometheus/README.md` | 6 | 7 | done | 15 |
| 30 | `observability_logging/README.md` | 6 | 7 | done | 12 |
| 31 | `observability_tracing_and_otel/README.md` | 6 | 7 | done | 12 |
| 32 | `visualization_and_alerting/README.md` | 6 | 7 | done | 10 |
| 33 | `sre_principles_and_slos/README.md` | 6 | 7 | done | 15 |
| 34 | `incident_management_and_oncall/README.md` | 6 | 7 | done | 12 |
| 35 | `devsecops_and_supply_chain_security/README.md` | 7 | 8 | done | 12 |
| 36 | `policy_as_code_and_compliance/README.md` | 7 | 8 | done | 12 |
| 37 | `disaster_recovery_and_resilience/README.md` | 7 | 8 | done | 12 |
| 38 | `platform_engineering_and_idp/README.md` | 7 | 8 | done | 10 |
| 39 | `ml_platform_and_gpu_infrastructure/README.md` | 8 | 11 | done | 15 |
| 40 | `event_streaming_operations/README.md` | 8 | 11 | done | 12 |
| 41 | `performance_and_load_testing/README.md` | 8 | 11 | done | 12 |

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

### Conventions Reminder (for future chunk agents)

```
MODULE TEMPLATE — 14-section canonical scheme (matches the rest of the repo):
  ## 1. Concept Overview
  ## 2. Intuition     (> blockquote analogy + Mental model + Why it matters + Key insight)
  ## 3. Core Principles
  ## 4. Types / Architectures / Strategies
  ## 5. Architecture Diagrams            (ASCII art only — no Mermaid, no images)
  ## 6. How It Works — Detailed Mechanics   (real YAML/HCL/Bash/PromQL/Go, concrete numbers)
  ## 7. Real-World Examples
  ## 8. Tradeoffs                        (comparison tables)
  ## 9. When to Use / When NOT to Use
  ## 10. Common Pitfalls                 (# BROKEN -> # FIX pattern, at least 1 required)
  ## 11. Technologies & Tools            (comparison table)
  ## 12. Interview Questions with Answers  (bold Q, plain A; targets in tables above)
  ## 13. Best Practices
  ## 14. Case Study   (scenario + ASCII diagram + real code + BROKEN/FIX + metrics + Discussion Qs)

QUALITY BAR:
  - 700-1000 lines per module README
  - Q&A minimum per the tables above (15 for: kubernetes_architecture, kubernetes_networking,
    kubernetes_security, infrastructure_as_code_terraform, observability_metrics_prometheus,
    sre_principles_and_slos)
  - At least 1 BROKEN->FIX block in §10 and at least 1 in §14
  - AWS as the default cloud; GCP/Azure in comparison tables (see §5 mapping)
  - Concrete numbers everywhere (etcd 8GB default quota, HPA 15s sync, Prometheus 15s scrape /
    2h block, K8s 110 pods/node default, cgroup v2 memory.high, etc.) — no "a few"/"some"
  - ASCII diagrams in fenced code blocks; no emojis; --- between every top-level section
  - Em-dash in §6 heading: "## 6. How It Works — Detailed Mechanics"
  - Cross-link to other sections via relative paths: ../backend/..., ../database/..., ../hld/...

CASE STUDY TEMPLATE — 11-section principal template (matches llm/ml):
  Reference file: ../llm/case_studies/design_gpu_inference_platform.md
  Intuition -> 1. Requirements -> 2. Scale Estimation -> 3. High-Level Architecture
  -> 4. Component Deep Dives -> 5. Design Decisions & Tradeoffs -> 6. Real-World Implementations
  -> 7. Technologies & Tools -> 8. Operational Playbook -> 9. Common Pitfalls & War Stories
  -> 10. Capacity Planning -> 11. Interview Discussion Points (10+ Q&As)
  900-1100 lines; min 4 cross-refs to cross_cutting/; real code in §4; broken-then-fix once;
  §6 names actual companies; §9 has quantified impact ($/users/SLA).

MAINTENANCE RULE when completing a chunk:
  1. Flip Status "pending" -> "done" for each completed file in the tables above
  2. Advance the NEXT UP pointer at the top of §8
  3. Update case_studies/README.md if new case studies were added
  4. Update root README.md and CLAUDE.md counts if the total changed
  5. Update the devops-section.md memory file if structure changed
```

---

## Getting Started

Recommended order for interview preparation:

1. **Week 1 — Foundations + Containers**: Phase 1, then `containers_and_docker` → `kubernetes_architecture`. The Kubernetes control plane is the single highest-signal interview topic.
2. **Week 2 — Kubernetes depth + CI/CD**: finish Phase 2, then Phase 3 (pipelines, GitOps, deployment strategies).
3. **Week 3 — IaC + Cloud**: Phase 4 (Terraform first), then Phase 5 (AWS-primary).
4. **Week 4 — Observability, SRE, Security**: Phase 6 (Prometheus + SLOs are most-tested), then Phase 7.
5. **Review**: work the case studies end-to-end — see [case_studies/README.md](case_studies/README.md) for the guided path.

Each module follows the standard 14-section template. See [`../llm/foundations_and_architecture/README.md`](../llm/foundations_and_architecture/README.md) as the format reference, and [`../llm/case_studies/design_gpu_inference_platform.md`](../llm/case_studies/design_gpu_inference_platform.md) for the principal case-study format.
