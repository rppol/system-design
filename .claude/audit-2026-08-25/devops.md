# DevOps Audit — 2026-08-25

## 0. Snapshot

`devops/` is the repo's most operationally complete section and its most version-exposed one. Forty-one modules across eight phases carry the full Linux → containers → Kubernetes → CI/CD → IaC → cloud → observability → SRE → DevSecOps arc, every one of them structurally perfect: **all 41 modules have all 14 template headings, all clear the 15-Q&A floor, all carry 3–7 Mermaid diagrams, and all have at least one BROKEN→FIX pair in §10 and §14**. The 2026-08-04 factual audit did real work and it shows — `use_lockfile`, Helm 4's `--rollback-on-failure`, nftables as the recommended kube-proxy mode, OpenBao, Jira Service Management Operations instead of Opsgenie, Grafana IRM instead of Grafana OnCall, the Elastic/OpenSearch tri-licence, Cortex-vs-Mimir licensing — all correct today. `opentelemetry_deep_dive.md` is the best file in the section and arguably in the repo: 2,037 lines opening with a dated version anchor pinned to six upstream releases.

The single biggest weakness is not staleness. It is **uniformity**. Every module lands between 442 and 866 lines with 15 or 16 Q&As, regardless of whether the topic is `artifact_and_registry_management` or `kubernetes_architecture`. The section was built to a manifest, and the manifest's floor became its ceiling: the section's own `CLAUDE.md` names seven modules that should "aim higher (18+)" and **all seven sit at 15 or 16**. One deep-dive sub-file exists across 41 modules (`llm/` has 85). The consequence is that the interview core — `kubernetes_architecture` at 574 lines, `kubernetes_security` at 543 — is the same depth as the periphery, and the topics that grew in 2026 (device scheduling, AI-workload operations, supply-chain regulation) had nowhere to land, so they were simply not written.

The second weakness is narrower and cleaner: **the audit fixed the content files and never opened `README.md`.** Section 8's "Conventions Reminder" — the block explicitly addressed to "future chunk agents" — still instructs authors to write "etcd 8GB default quota" and "Prometheus 15s scrape", which are the exact two wrong numbers `CLAUDE.md` records the audit removing from eleven places. The README is a wrong-number generator that survived the pass that fixed its output.

| | Count |
|---|---|
| Modules | 41 |
| Deep-dive sub-files | 1 (`opentelemetry_deep_dive.md`, 2,037 lines) |
| Case studies | 13 (+ 7 `cross_cutting/` primitives) |
| Total lines (modules + sub-file) | 26,602 |
| Total lines (case studies + cross-cutting) | 15,935 |
| Total Q&As in the bank | 686 (650 modules + 36 sub-file) — reconciles exactly with `extract.py` |
| Avg lines / module | 599 |
| Avg Q&As / module | 15.9 |
| Avg lines / case study | 930 (inside the 900–1100 bar) |
| Modules ≥ 18 Q&As | **0** |
| Modules with a dated version anchor | **1 of 42** |

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Pri | Est. lines |
|---|---|---|---|---|
| `kubernetes_scheduling_and_autoscaling/device_scheduling_and_dra.md` | sub-file | DRA went **GA in Kubernetes 1.34** and its feature gate is **locked on in 1.35**; 1.35 added partitionable devices and 1.36 is "DRA GA" headline. The string `DRA`/`ResourceClaim`/`DeviceClass` appears **zero times** in the entire section. https://kubernetes.io/blog/2025/09/01/kubernetes-v1-34-dra-updates/ | **P0** | 550–700 |
| `ml_platform_and_gpu_infrastructure/gpu_scheduling_and_inference_ops.md` | sub-file | The parent module's framing — GPUs as "opaque, integer-countable extended resources" (L11) — is the pre-DRA model. It also has zero on vLLM, Gateway API Inference Extension, EC2 Capacity Blocks, or LLM-aware autoscaling. GPU/AI infra is the dominant 2026 platform-hiring driver. https://cloud.google.com/blog/products/containers-kubernetes/kubernetes-device-management-with-dra-dynamic-resource-allocation | **P0** | 600–750 |
| `devsecops_and_supply_chain_security/regulated_supply_chain.md` | sub-file | **EU CRA vulnerability + incident reporting obligations start 11 Sept 2026** — 17 days after this audit — with a 24h early warning / 72h full notification via ENISA's Single Reporting Platform, and SBOM in technical documentation from Dec 2027. Fines to €15M or 2.5% of turnover. `CRA`/`Cyber Resilience` appears **zero times** in the section. https://www.herodevs.com/blog-posts/cra-reporting-obligations-start-september-2026-what-eol-dependencies-mean-for-your-compliance | **P0** | 450–600 |
| `platform_engineering_and_idp/ai_workloads_and_agent_operations.md` | sub-file | 2026 platform reality: "AI agents graduate from experimental tools to first-class platform citizens — mature platforms treat agents like any other user persona, complete with RBAC permissions, resource quotas, and governance policies"; 73% of platform teams ship AI assistants. `MCP`, `agentic`, `AI agent` all appear **zero times**. https://platformengineering.org/blog/10-platform-engineering-predictions-for-2026 | **P0** | 500–650 |
| `cloud_cost_optimization_finops/gpu_and_ai_finops.md` | sub-file | `cloud_cost_optimization_finops.md` (716 lines) has **zero occurrences of "GPU"** and zero of "carbon"/"sustainability", while FOCUS gets two sentences and no version. FOCUS 1.3 ratified 2025-12-05 adds shared cost allocation, contract commitments and recency; the 2026 spec expansion explicitly targets AI/model-inference workloads. https://www.linuxfoundation.org/press/finops-foundation-launches-focus-1.3-to-deepen-cloud-and-saas-billing-transparency-announces-expanded-vendor-support-for-focus-1.2 | **P1** | 450–550 |
| `kubernetes_networking/cilium_and_ebpf_dataplane.md` | sub-file | Cilium has 24 mentions in `kubernetes_networking` but no mechanism page; eBPF is the 2026 dataplane *and* observability substrate (OTel's own eBPF instrumentation, OBI, is now an OTel project at v0.10.0). Cilium graduated CNCF 2023-10-11 and is the default CNI on GKE Dataplane V2, EKS-A and AKS. https://www.cncf.io/projects/cilium/ | **P1** | 500–600 |
| `container_runtimes_and_oci/oci_image_formats_and_lazy_pulling.md` | sub-file | The module owning the OCI spec never cites an OCI version, never names zstd, eStargz or SOCI, and gives stargz one-line mentions only. Image-format/lazy-pull is where container cold-start work lives in 2026. | P1 | 400–500 |
| `kubernetes_security/workload_identity_and_federation.md` | sub-file | `SPIFFE`/`SPIRE` appear **zero times** section-wide. Workload identity is the 2026 replacement for both static secrets and per-cloud IAM glue, and the section already covers its consumers (ESO, IRSA, Pod Identity, OIDC-to-cloud) without the primitive underneath them. | P1 | 400–500 |
| `case_studies/design_gpu_training_and_inference_cluster.md` | case study | The one GPU case study (`design_ml_platform_infrastructure`, 720 lines — the shortest of the 13 and below the 900-line bar) covers platform plumbing. A 2026 interview asks for multi-node training topology, gang admission, spot/capacity-block strategy, and inference autoscaling economics. | P1 | 950–1050 |
| `case_studies/design_multi_tenant_platform.md` | case study | `vCluster`/virtual cluster appears in only 2 files and never as a design axis. Namespace-vs-cluster-vs-vCluster tenancy is the most common platform-design interview prompt after "design a K8s platform". | P2 | 950–1050 |
| `gitops_argocd_flux/environment_promotion.md` | sub-file | `Kargo` appears **zero times**. Promotion across dev→stage→prod is the thing GitOps is worst at and the thing every GitOps interview probes; the section teaches sync but not promotion. | P2 | 350–450 |
| `case_studies/cross_cutting/dra_and_device_plugins.md` | cross-cutting | If the two P0 GPU/DRA sub-files land, the device-allocation model is shared by `design_ml_platform_infrastructure`, `design_autoscaling_platform` and `design_kubernetes_platform` — that is the definition of a cross-cutting primitive here. | P2 | 450–550 |

### What each P0 would contain

**`device_scheduling_and_dra.md`** — 1: extended resources vs DRA, and why counting integers broke. 2: a GPU is a *claim*, not a number. 3: DeviceClass / ResourceClaim / ResourceClaimTemplate / ResourceSlice, and the allocation-vs-binding split. 4: structured parameters, partitionable devices, consumable capacity, admin access. 5: allocation flow from Pod → claim → slice → node. 6: full YAML for a DeviceClass + template + Pod, plus `kubectl get resourceslices`, with the 1.34-GA / 1.35-gate-locked / 1.36 state named. 7: NVIDIA and Intel DRA drivers in production. 8: DRA vs device plugin vs MIG-via-labels. 9: use DRA when devices are heterogeneous or shareable; skip it when one whole card per pod is the whole story. 10: the ResourceClaim leak — a template-less claim outliving its pod and pinning a GPU. 11: DRA drivers, Kueue, Volcano, the 1.35 Workload API. 12: 18 Q&As. 13: always use ResourceClaimTemplate for pod-scoped claims. 14: migrating a `nvidia.com/gpu: 1` fleet to DRA without a maintenance window.

**`gpu_scheduling_and_inference_ops.md`** — 1: training vs inference are different scheduling problems. 2: training is a gang, inference is a queue. 3: gang admission, topology, health, preemption. 4: Kueue/Volcano vs the 1.35 native Workload API vs Run:ai. 5: NVLink-domain packing and a multi-node NCCL/EFA topology map. 6: worked LLM autoscaling on queue depth and KV-cache utilisation rather than CPU, with vLLM + KServe YAML and Gateway API Inference Extension routing. 7: named platforms running it. 8: MIG vs MPS vs time-slicing vs DRA partitions. 9: when scale-to-zero on GPUs is right and when the cold start makes it a lie. 10: the XID-error node that stays Ready and silently fails every job; spot GPU reclaim mid-epoch. 11: DCGM, NPD, Capacity Blocks, Karpenter GPU NodePools, NIM/Dynamo. 12: 18 Q&As. 13: never autoscale an LLM endpoint on CPU. 14: cutting cost-per-token in half without touching the model.

**`regulated_supply_chain.md`** — 1: the CRA turns supply-chain hygiene into a legal obligation with a date. 2: the SBOM is now evidence, not documentation. 3: manufacturer duties, the open-source-steward category, actively-exploited-only reporting. 4: CRA vs NIST SSDF vs SLSA vs PCI DSS 4.0 — four frameworks, one pipeline. 5: the 24h/72h reporting clock as a timeline diagram. 6: wiring SBOM generation, VEX, and the ENISA Single Reporting Platform into an existing cosign/Rekor pipeline. 7: who has shipped this. 8: SPDX 3.x vs CycloneDX 1.6 for CRA evidence. 9: when you are in scope and when you are a steward. 10: EOL transitive dependencies you cannot patch inside 72 hours. 11: Trivy/Syft/Grype/Dependency-Track/GUAC. 12: 15 Q&As. 13: generate the SBOM at build, not at scan. 14: a vendor's first CRA-reportable exploit.

**`ai_workloads_and_agent_operations.md`** — 1: an agent is a workload class with a blast radius. 2: give the agent a service account, not a human's token. 3: identity, least privilege, egress control, quota, audit, kill switch. 4: sidecar agents vs an agent gateway vs MCP-server-as-a-platform-service. 5: request path from agent → MCP server → cloud API, with the policy chokepoints marked. 6: RBAC + NetworkPolicy + admission policy for an agent namespace; per-agent token budget as a resource quota; OTel GenAI-convention tracing on the agent loop. 7: who runs this today. 8: agent autonomy vs auditability. 9: when to let an agent hold write credentials — and the answer is usually no. 10: the agent that retried a destructive API 400 times inside its budget. 11: MCP, OpenTelemetry GenAI conventions, policy engines, cost attribution. 12: 15 Q&As. 13: every agent action must be attributable to a principal. 14: an AIOps remediation agent that made an incident worse.

---

## 2. Stale — verified CORRECTIONS

Everything below was web-verified on 2026-08-25. Note the pattern: **six of the eight are in `README.md`, which the 2026-08-04 audit did not open.**

| File | Line / heading | What it says | What is true in Aug 2026 | Source |
|---|---|---|---|---|
| `README.md` | 585, §8 Conventions Reminder | "Concrete numbers everywhere (**etcd 8GB default quota**, HPA 15s sync, **Prometheus 15s scrape** / 2h block, K8s 110 pods/node default…)" | etcd's `--quota-backend-bytes` default is **2 GiB**; Prometheus `scrape_interval` defaults to **1m**. Both are the exact errors the section's own `CLAUDE.md` records the audit removing from eleven places. The README instructs future authors to reintroduce them. | `devops/CLAUDE.md` §"What the audit found", class 2; `kubernetes_architecture.md` L275-276 already carries the corrected value |
| `README.md` | 8–14 | "**PARKED — to be done later (owner-set 2026-07-29).** … What is paused is **27 audit units (none started)** and **650 Q&As awaiting one-line MCQ summaries**." | Unparked 2026-08-04; audit DONE (41/41), Shorts DONE (650/650, commit `6ce35a6`). The banner is 21 days stale and contradicts `CLAUDE.md` line 8 of the same directory. | `devops/CLAUDE.md` scope table; root `CLAUDE.md` "Scope — 15 sections in scope" |
| `README.md` | 415, §5 Cloud Service Mapping | GCP infra-as-code native = "**Deployment Manager** / Config Connector" | Cloud Deployment Manager reached **end of support 31 Mar 2026**, and **new users have been blocked from enabling the V2 API since 30 Jun 2026**. Replacement is **Infrastructure Manager**. The section's own `CLAUDE.md` already lists "Deployment Manager" among the dead products the audit removed — from the modules, not from this table. | https://docs.cloud.google.com/deployment-manager/docs/deprecations |
| `README.md` | 433, §6 Top Interview Topics → IaC | "Use a remote backend (**S3 + DynamoDB lock**) so the team shares one locked state." | DynamoDB-based locking is **deprecated**. S3-native locking (`use_lockfile = true`) was experimental in Terraform 1.10 and **GA in 1.11**, which is when `dynamodb_table`/`dynamodb_endpoint` were marked deprecated. The module files already teach `use_lockfile` correctly — only the README's interview answer is stale. | https://developer.hashicorp.com/terraform/language/backend/s3 |
| `README.md` | 579–582, §8 quality bar | "**700-1000 lines per module README**" and "Q&A minimum … (15 for: …)" listing **six** modules | (a) Module pages have not been READMEs since the 2026-07-30 rename — a `README.md` below a section root is invisible to `extract.py`. (b) **30 of 41 modules are under 700 lines**, so the stated bar is met by 11. (c) `CLAUDE.md` names **seven** modules at an **18+** target; the README says six at 15. Two sources, three disagreements. | root `CLAUDE.md` file-naming rule; measured line counts; `devops/CLAUDE.md` "Q&A floor" |
| `README.md` | 489–532, §8 Module File Status | "Q&A Target" column gives **10 or 12** for 30 of the 41 modules | The repo-wide hard floor is **15** and every module already exceeds the printed target. The column is a live invitation to write an under-floor module. | root `CLAUDE.md` "Minimum 15 Q&As per module — this is a hard floor" |
| `container_runtimes_and_oci/container_runtimes_and_oci.md` | whole file | Never states a containerd version; L29/L206 stop at "dockershim, removed in 1.24" | **Kubernetes 1.35 is the last release supporting containerd 1.x** — containerd 2.0+ is required before upgrading past it, and "containerd 2.0 is the expected runtime in v1.36+". The module whose entire subject is the containerd runtime stack carries no version anchor at all. | https://kubernetes.io/blog/2025/12/17/kubernetes-v1-35-release/ ; https://kubernetes.recipes/recipes/configuration/kubernetes-1-35-1-36-upgrade-checklist/ |
| `linux_and_os_fundamentals/linux_and_os_fundamentals.md` | 69 | cgroups table marks v1 "Legacy"; row reads "Default on modern distros \| Legacy \| Default (**RHEL 9, Ubuntu 22.04+**)" | **Kubernetes 1.35 deprecates cgroup v1 outright — the kubelet refuses to start by default on a cgroup v1 node.** "Legacy" understates a hard boot failure. The distro examples are also two majors behind (RHEL 10, Ubuntu 24.04 LTS). | https://kubernetes.io/blog/2025/12/17/kubernetes-v1-35-release/ |
| `kubernetes_networking/kubernetes_networking.md` | 276 vs 344/441 | Gateway API implementations listed as "Envoy Gateway, Istio, Traefik, **kgateway**, and NGINX Gateway Fabric" at L276, but "(Envoy Gateway, Istio, Traefik, NGINX Gateway Fabric)" at L344 and L441 | Internal contradiction, not a currency error — the same file gives two different lists three times. Pick one. | measured in-file |
| `cloud_fundamentals_and_aws/cloud_fundamentals_and_aws.md` | 119 (and L611 Q14) | Accelerated instance family exemplar = "**p5.48xlarge (H100)**, inf2" | Two generations behind. **P6-B200** (NVIDIA B200, GA May 2025), **P6e-GB200 UltraServers** (72 GPUs in one NVLink domain), and **P6e-GB300** (GA Dec 2025) are current. The section's *own* `ml_platform_and_gpu_infrastructure.md` L554 already lists "p6-b200 (B200)" — the AWS-fundamentals module is behind its sibling. | https://aws.amazon.com/about-aws/whats-new/2025/12/amazon-ec2-p6e-gb300-ultraservers-nvidia-gb300-nvl72-generally-available |
| `gcp_and_azure_essentials/gcp_and_azure_essentials.md` | 252, 512 | Bicep AKS resource pinned twice at `managedClusters@**2024-02-01**` | An AKS API version from Feb 2024, in a file whose whole purpose is a current cross-cloud mapping. AKS revs its resource API every few months. Not confirmed removed, but two-and-a-half years stale as a worked example. | `Microsoft.ContainerService/managedClusters` API version history |
| `infrastructure_as_code_terraform/infrastructure_as_code_terraform.md` | 185 | `required_version = ">= 1.11"` | Not wrong as a *floor*, but 1.11 has aged out of the active support window — **1.15.8 (2026-07-08) is latest stable**, with 1.14.x and 1.15.x the supported lines. A module teaching Terraform should name the current line somewhere. The same file pins `hashicorp/aws ~> 6.57` with no date attached. | https://releases.sh/hashicorp/terraform |

### Verified NEGATIVE results — do not "fix" these

These look stale and are correct. Recording them so the next pass does not burn time or, worse, "corrects" them wrongly.

| Claim | Where | Verified |
|---|---|---|
| Backstage is "CNCF **incubating**" | `platform_engineering_and_idp.md` L278/L397, `design_internal_developer_platform.md` L723/L784 | Still Incubating (since 2022-03-15). It has **not** graduated. https://www.cncf.io/projects/backstage/ |
| CloudFront invalidation: 1,000 free paths/month, then $0.005/path | `cloud_networking_and_cdn.md` L381/L394/L400-407/L628 | Unchanged through 2026. https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/PayingForInvalidation.html |
| In-place pod vertical scaling is "a stable, on-by-default Kubernetes feature" | `kubernetes_scheduling_and_autoscaling.md` L566 | **GA in 1.35.** Correct. (Worth *adding*: pod-**level** resources are only Beta, in 1.36.) https://kubernetes.io/blog/2025/12/19/kubernetes-v1-35-in-place-pod-resize-ga |
| OpenTelemetry is "a graduated CNCF project" | `observability_tracing_and_otel.md` L345; deep-dive L3 | Graduated **2026-05-11**, announced 2026-05-21. The deep-dive has the exact date. https://www.cncf.io/announcements/2026/05/21/cloud-native-computing-foundation-announces-opentelemetrys-graduation-solidifying-status-as-the-de-facto-observability-standard/ |
| `MutatingAdmissionPolicy` "mutating in 1.36" | `cross_cutting/kubernetes_production_hardening.md` L426 | GA and on by default in v1.36 (shipped 2026-04-22); ValidatingAdmissionPolicy stable since 1.30. Correct. https://kubernetes.io/docs/reference/access-authn-authz/mutating-admission-policy |
| `use_lockfile` taught instead of DynamoDB | `infrastructure_as_code_terraform.md`, `terraform_advanced_and_alternatives.md`, `cross_cutting/terraform_state_at_scale.md` | Correct — the audit caught this in the modules. Only the README missed it. |

**One currency addition rather than correction:** Kubernetes **1.35 introduced native gang scheduling via a new core `Workload` API** (alpha, `scheduling.k8s.io/v1alpha1`, `GenericWorkload` gate, `minCount` semantics). `ml_platform_and_gpu_infrastructure.md` L41 and `kubernetes_scheduling_and_autoscaling.md` still present Volcano/Kueue as the only source of gang semantics. That statement is not yet *wrong* — the native implementation is alpha and thinner than Kueue — but a 2026 candidate is expected to know the API exists. https://www.kubernetes.io/blog/2025/12/29/kubernetes-v1-35-introducing-workload-aware-scheduling/

---

## 3. Reordering — SEQUENCE and TIER changes

### 3a. The 8-phase sequence is still right; two placements are not

The Foundations → Containers → CI/CD ∥ IaC → Cloud → Observability/SRE → DevSecOps ladder is sound and I would not rebuild it. Two modules are filed by accident of build order rather than by topic:

| Module | Current | Proposed | Why |
|---|---|---|---|
| `platform_engineering_and_idp` | Phase 7 "DevSecOps & Reliability", position 38 | **Phase 3 "CI/CD & GitOps", after `gitops_argocd_flux`** (position 19) | It is neither DevSecOps nor reliability. It is the *product* framing of the delivery layer the learner has just built — golden paths sit directly on top of GitOps, and every concept in it (scaffolder, catalog, self-service provisioning) is a CI/CD abstraction. Filed at 38 it reads as an afterthought; at 19 it is the "so what" of Phase 3. This also puts the section's own organising discipline before the four phases that implement it. |
| `ml_platform_and_gpu_infrastructure` | Phase 8 "advanced electives", position 39 | **Phase 8 promoted from "electives" to "Specialized Platforms" proper, and this module moved to the head of it** (position 39 → unchanged index, but the phase framing changes) | Calling GPU/ML infrastructure an *elective* in 2026 is the section's one genuinely dated editorial judgement. It is the single fastest-growing DevOps hiring surface. Keep it after Phase 6 (it needs SLOs and scheduling first) but delete the word "electives" from the phase title and from README §4's closing note. |

Everything else stays. In particular, do **not** move `cloud_cost_optimization_finops` earlier — it correctly depends on Phase 5 having introduced the resources whose cost it optimises.

**No `STUDY_ORDER["devops"]` array change is required for the Phase 8 re-framing** (it is prose only). The `platform_engineering_and_idp` move does require one, given below.

### 3b. Senior tier — six changes

The Senior tier (22 of 41) is a defensible cut but predates the 2026 interview. Four modules that are asked in every senior loop are absent, and two that are present are lower-yield.

| Module | Current tier | Proposed | Why |
|---|---|---|---|
| `helm_and_package_management` | Full only | **Senior** | You cannot hold the job without it, and "Helm vs Kustomize, and what does `--rollback-on-failure` actually do" is a standard question. The most conspicuous absence in the tier. |
| `devsecops_and_supply_chain_security` | Full only | **Senior** | Sigstore/SLSA/SBOM moved from specialist to baseline; the EU CRA gives it a compliance deadline 17 days out. A senior DevOps engineer in 2026 is expected to explain keyless signing. |
| `platform_engineering_and_idp` | Full only | **Senior** | "Platform engineer" is the job title. Gartner projects 80% of large engineering orgs with platform teams by 2027, up from 45% in 2024. |
| `cloud_cost_optimization_finops` | Full only | **Senior** | FinOps is a standing senior expectation, not a specialism, and the module already carries the strongest worked arithmetic in the section (the Savings Plan coverage table at L400-419). |
| `kubernetes_storage_and_state` | Full only | **Senior** | StatefulSets, PVC binding modes and the AZ-topology trap are core K8s interview material; the tier currently teaches networking and scheduling but not state. |
| `serverless_and_faas` | Senior | **Full only** | Lowest interview yield of the current 22 for a Kubernetes-centric platform role. Drop it to make room without inflating the tier past ~26. |

Net: Senior goes 22 → 26 modules. That is still under two-thirds of the section, which is the right shape for a curated tier.

### 3c. Principal tier — recommend creating one (owner decision)

`devops/CLAUDE.md` states: *"This section has no Principal tier and needs none."* I think that was right when written and is now the weakest structural claim in the section, and I flag it as needing owner sign-off rather than asserting it.

The argument for: every other large section has one, the repo defines principal as *judgment* (which approach, at what cost, what failure modes, what migration), and `devops/` is the section where that cut is most obvious — half its content is literally "here are four ways and here is the one that will hurt you in year three". The 13 case studies are all built to the 11-section **principal** template, so the section already produces principal-grade material and simply does not label any module as such.

Proposed Principal tier (11 modules — deliberately a *different cut*, not a superset of Senior):

```
kubernetes_architecture              (blast radius of a control-plane decision)
container_runtimes_and_oci           (isolation-model choice: runc vs gVisor vs Kata vs microVM)
deployment_strategies                (risk budget per release)
terraform_advanced_and_alternatives  (Terraform vs OpenTofu vs Pulumi vs Crossplane, and migrating)
cloud_cost_optimization_finops       (commitment strategy under uncertainty)
sre_principles_and_slos              (what reliability to promise and what to refuse)
incident_management_and_oncall       (organisational design, not tooling)
policy_as_code_and_compliance        (where to put the gate, and what it costs to pass it)
disaster_recovery_and_resilience     (RTO/RPO as a business negotiation)
platform_engineering_and_idp         (platform-as-product, adoption, deprecation)
ml_platform_and_gpu_infrastructure   (capital allocation on scarce hardware)
```

Note that `kubernetes_networking`, `kubernetes_security` and `observability_metrics_prometheus` are Senior-only under this proposal and `container_runtimes_and_oci`, `policy_as_code_and_compliance`, `disaster_recovery_and_resilience` and `incident_management_and_oncall` are Principal-only. Roughly half of each list is material the other never sees — which is the test the root `CLAUDE.md` sets for a real tier rather than a difficulty label.

Adding this requires only tier tags in the README's `<!-- study-paths -->` block plus `extract.py --write-paths`; `check_wiring()` already handles a section declaring both tiers.

### 3d. Full proposed `STUDY_ORDER["devops"]`

Only one element moves (`platform_engineering_and_idp`, 38 → 19). New sub-files need no entry.

```js
  devops: [
    "devops/linux_and_os_fundamentals","devops/shell_scripting_and_automation","devops/networking_for_devops","devops/version_control_and_git_workflows",
    "devops/containers_and_docker","devops/container_runtimes_and_oci","devops/kubernetes_architecture","devops/kubernetes_workloads_and_objects","devops/kubernetes_networking","devops/kubernetes_storage_and_state","devops/kubernetes_scheduling_and_autoscaling","devops/kubernetes_security","devops/helm_and_package_management","devops/kubernetes_operators_and_crds",
    "devops/ci_cd_fundamentals","devops/ci_cd_platforms","devops/deployment_strategies","devops/gitops_argocd_flux","devops/platform_engineering_and_idp","devops/artifact_and_registry_management",
    "devops/infrastructure_as_code_terraform","devops/terraform_advanced_and_alternatives","devops/configuration_management","devops/secrets_management",
    "devops/cloud_fundamentals_and_aws","devops/gcp_and_azure_essentials","devops/serverless_and_faas","devops/cloud_networking_and_cdn","devops/cloud_cost_optimization_finops",
    "devops/observability_metrics_prometheus","devops/observability_logging","devops/observability_tracing_and_otel","devops/visualization_and_alerting","devops/sre_principles_and_slos","devops/incident_management_and_oncall",
    "devops/devsecops_and_supply_chain_security","devops/policy_as_code_and_compliance","devops/disaster_recovery_and_resilience",
    "devops/ml_platform_and_gpu_infrastructure","devops/event_streaming_operations","devops/performance_and_load_testing",
  ],
```

---

## 4. Depth — THIN modules and BLOAT

No module is *structurally* thin — the template is complete everywhere. What is thin is depth relative to interview weight. The table below is ordered by how badly the gap hurts.

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `kubernetes_architecture` | 574 | **15** | `CLAUDE.md` targets 18+. No version anchor at all — the most version-sensitive module in Kubernetes has zero version numbers. No etcd defrag/compaction runbook, no API Priority and Fairness (whose `v1beta3` was *removed* in 1.35), no leader-election mechanics, no control-plane upgrade path. | +4 Q&As; add a dated version anchor; add APF and the 1.35 `flowcontrol.apiserver.k8s.io/v1beta3` removal to §6. **+150 lines.** |
| `kubernetes_security` | 543 | **15** | Targets 18+. No user namespaces (`hostUsers: false`) despite an extensive `securityContext` section. No image-volume or OCI-artifact-mount surface. Zero concrete numbers in the whole file — no timeouts, no sizes, no thresholds, which violates the section's own content rule. | +4 Q&As; add user namespaces; put real numbers into §6 (webhook timeout budgets, KMS latency, RBAC evaluation cost). **+150 lines.** |
| `ml_platform_and_gpu_infrastructure` | 752 | **16** | Targets 18+. Pre-DRA framing (L11). A100-centric in 2026 — `p4de.24xlarge` is the canonical example throughout while B200/GB200 appear only in a MIG-support list. No vLLM, no inference autoscaling on queue depth, no Capacity Blocks, no NPD/XID remediation. | The P0 sub-file carries most of it; the parent needs its §1 reframed around claims-not-counts and its worked example re-anchored on current hardware. **+120 lines + sub-file.** |
| `container_runtimes_and_oci` | 460 | 16 | No containerd version anywhere (see §2). No OCI 1.1 / zstd / eStargz / SOCI. Rootless is in `containers_and_docker` instead of here, where the runtime layer belongs. Second-shortest module in the section and the one with the fastest-moving subject. | Version anchor + the P1 image-format sub-file. **+80 lines + sub-file.** |
| `infrastructure_as_code_terraform` | 726 | **15** | Targets 18+. No Terraform Stacks (`Terraform Stacks` = zero hits section-wide). | +3 Q&As, add Stacks to §4/§11. **+70 lines.** |
| `observability_metrics_prometheus` | 782 | **15** | Targets 18+. No Prometheus major-version claim anywhere. Exemplars appear only inside one Remote-Write-2.0 sentence despite the tracing module cross-referencing this file as their owner. No OTLP-ingest path — the single most-asked Prometheus/OTel interop question in 2026. Remote-Write 2.0 given with no stability tag. | +3 Q&As; add an exemplars sub-section and an OTLP-ingest sub-section; add a dated version anchor. **+140 lines.** |
| `sre_principles_and_slos` | 693 | **15** | Targets 18+. | +3 Q&As. **+50 lines.** |
| `kubernetes_networking` | 607 | **15** | Targets 18+. kgateway listed inconsistently three times (§2). | +3 Q&As; reconcile the implementation list. **+50 lines.** |
| `containers_and_docker` | 446 | 16 | Three near-duplicate multi-stage Dockerfiles across §5/§6/§14 — the only genuine *bloat* found. No image-format internals despite L7 claiming that scope. Docker Scout absent. | Collapse two of the three Dockerfiles into one referenced example; the recovered space pays for the format content. **net ~0 lines.** |
| `artifact_and_registry_management` | 442 | 16 | Shortest module. Zot and Docker Scout absent from its own §11 table despite being exactly its subject. | +2 §11 rows and a paragraph each. **+40 lines.** |
| `serverless_and_faas` | 617 | 16 | **ARM/Graviton for Lambda absent** and **Lambda response streaming absent** — two live cost/latency levers in a module whose §6 is built entirely around cost arithmetic and cold-start mitigation. SnapStart is covered well (L77, L332, Q15); its two obvious siblings are not. | +2 Q&As, an arm64 row in the §6 cost table, response streaming in §4. **+70 lines.** |
| `gcp_and_azure_essentials` | 537 | 16 | The master cross-cloud service-mapping table (L49-82) has **no AI/ML row** — no Bedrock ↔ Vertex AI ↔ Azure AI Foundry, no GPU compute. In 2026 that is the row a reader opens the table for. The same omission exists in `README.md` §5's mapping table. | Add an AI/ML services row and a GPU-compute row to both tables. **+40 lines.** |
| `version_control_and_git_workflows` | 494 | 16 | Numerically thinnest file in the section — almost no figures. `git maintenance` absent; jj/Jujutsu absent. SHA-1/SHA-256 stated as if co-equal when SHA-256 repos remain opt-in and interop-limited. | +1 Q&A on `git maintenance` at monorepo scale with real timings. **+50 lines.** |
| `cloud_cost_optimization_finops` | 716 | 16 | Zero GPU. Zero carbon/sustainability. FOCUS gets two sentences and no version. Discount percentage bands (Spot "up to 90%", RI "40-72%", SP "30-66%") carry no dated source anchor. | P1 sub-file + a dated pricing anchor line. **+30 lines + sub-file.** |
| `case_studies/design_ml_platform_infrastructure.md` | 720 | — | The only case study below the 900-line bar, by 180 lines, and it is the one covering the highest-growth topic. | Bring to 950 alongside the GPU sub-files. **+230 lines.** |

**Bloat / merge / delete: none recommended.** I looked specifically for redundant siblings and did not find a pair worth merging. The three that look like candidates are each doing distinct work:

- `containers_and_docker` vs `container_runtimes_and_oci` — image/build view vs runtime/spec view. Keep both; the fix is moving rootless from the former to the latter.
- `infrastructure_as_code_terraform` vs `terraform_advanced_and_alternatives` — core+state vs comparison+ecosystem. Keep both.
- `observability_tracing_and_otel` (572) vs `opentelemetry_deep_dive.md` (2,037) — the parent is the concept layer and correctly delegates every version-pinned mechanic to the sub-file. This is the model the rest of the section should copy, not a duplication to collapse.

The one file that *is* disproportionate — `opentelemetry_deep_dive.md` at 3.4× the average module — earns it. It is also, by its own construction, the file that goes stale fastest: re-audit it first, every time.

---

## 5. Cross-section notes

- **`llm/` — GPU inference.** The proposed `gpu_scheduling_and_inference_ops.md` must stop at the *infrastructure* boundary: scheduling, placement, autoscaling signals, node health, cost. Model-side concerns (vLLM's PagedAttention internals, continuous batching algorithms, quantisation) belong to `llm/` and should be cross-linked, not restated. `llm/case_studies/design_gpu_inference_platform.md` is the repo's gold-standard principal case study and is the right target for that link. The DevOps angle is "the GPU is a claim on a scheduler"; the LLM angle is "the GPU is a throughput budget".
- **`cuda/` — hardware model.** MIG profiles, NVLink bandwidth and SM partitioning appear in `ml_platform_and_gpu_infrastructure` §4/§6. `cuda/` owns why those numbers are what they are. Add a cross-link both ways; neither section should grow the other's material.
- **`ml/` — already cross-linked.** `devops/CLAUDE.md` maps `ml_platform_and_gpu_infrastructure/` → `ml/gpu_and_hardware_optimization/` and `ml/mlops_and_ci_cd/`. Verify the reverse link exists after the sub-files land.
- **`backend/` — the non-overlap boundary in README §2 is holding.** Service mesh, chaos engineering, app instrumentation and OWASP are all correctly deferred. One gap: SPIFFE/SPIRE (proposed P1) sits on the boundary with `backend/service_mesh_and_service_discovery`, which owns mTLS theory. DevOps should own the *issuance and federation* half; say so explicitly in README §2 when the sub-file lands.
- **`technologies/` — `HashiCorp Vault` already has a deep dive there.** `secrets_management` correctly covers OpenBao and ESO without duplicating it. If the workload-identity sub-file lands, check whether SPIRE deserves a `technologies/` page instead — that is a placement question for the Technologies owner, not for this section.
- **`hld/` — no action.** Distributed-systems theory is correctly deferred throughout.

---

## 6. Execution plan

Nothing below writes content in this pass. Waves 1 and 2 are independent of each other and can run concurrently; wave 3 depends on wave 1's decisions only for tier tags.

### Wave 0 — README repair (do this first, alone, one commit)
Everything in §2's README rows. It is small, it is fully specified, and until it is done every new module written from the manifest inherits two wrong numbers.

1. Delete the PARKED banner from `README.md` L8-14. **10 min.**
2. §8 Conventions Reminder L585: replace "etcd 8GB default quota" → "etcd 2 GiB default `--quota-backend-bytes`" and "Prometheus 15s scrape" → "Prometheus 1m default `scrape_interval`". **10 min.**
3. §8 L579: "700-1000 lines per module README" → "700-1000 lines per module page (`<module>/<module>.md`)"; reconcile the Q&A-target sentence at L580-582 with `CLAUDE.md`'s seven-module 18+ list. **15 min.**
4. §8 Module File Status: replace the "Q&A Target" column's 10/12 values with the real floor (15, or 18 for the seven deep modules). **20 min.**
5. §5 L415: "Deployment Manager / Config Connector" → "Infrastructure Manager / Config Connector". **5 min.**
6. §6 L433: "S3 + DynamoDB lock" → "S3 with `use_lockfile = true` (native locking; DynamoDB locking is deprecated since Terraform 1.11)". **10 min.**
7. `CLAUDE.md` §"Cross-Cutting Shared Primitives": the seven entries are written as directories (`kubernetes_production_hardening/`) but are flat `.md` files on disk. Fix the notation. Also reconcile the §"Learning Paths" paragraph, which still says membership is declared "in a `<!-- study-paths -->` block in that module's own page" — contradicting the HARD RULE section at the bottom of the same file and the 2026-08-05 repo restructure. **20 min.**

**Wave 0 total: ~1.5 hours. One agent. No `extract.py` run needed (no file inventory change).**

### Wave 1 — verified corrections in content files (parallel, 3 agents)
8. `container_runtimes_and_oci`: add a dated version anchor and the containerd 1.x→2.0 / Kubernetes 1.36 requirement to §1, §6 and §11. **2h.**
9. `linux_and_os_fundamentals` L69-70: cgroup v1 from "Legacy" to the 1.35 kubelet-refuses-to-start deprecation; refresh distro examples to RHEL 10 / Ubuntu 24.04. **1h.**
10. `kubernetes_networking`: reconcile the three Gateway API implementation lists. **30 min.**
11. `kubernetes_scheduling_and_autoscaling` + `ml_platform_and_gpu_infrastructure`: add the 1.35 native `Workload` API / gang scheduling alongside Kueue/Volcano, correctly labelled alpha. **2h.**
12. `kubernetes_scheduling_and_autoscaling` L566: keep the in-place-resize GA claim, add the pod-**level** resources Beta-in-1.36 distinction. **30 min.**
12a. `cloud_fundamentals_and_aws` L119/L611: p5.48xlarge (H100) → the P6-B200 / P6e-GB200 / P6e-GB300 generation, matching what `ml_platform_and_gpu_infrastructure` L554 already says. **30 min.**
12b. `gcp_and_azure_essentials` L252/L512: bump `managedClusters@2024-02-01` to a current AKS API version; add the missing AI/ML and GPU-compute rows to the mapping table at L49-82 **and** to `README.md` §5's table. **1.5h.**
12c. `infrastructure_as_code_terraform` L185/L187: name the current Terraform line (1.15.x) alongside the `>= 1.11` floor and date the `hashicorp/aws ~> 6.57` pin — folds into the version-anchor work in item 13. **30 min.**
13. Add a dated version anchor line (the `opentelemetry_deep_dive.md` L3 pattern) to the eight most version-sensitive modules: `kubernetes_architecture`, `kubernetes_networking`, `kubernetes_security`, `kubernetes_scheduling_and_autoscaling`, `container_runtimes_and_oci`, `observability_metrics_prometheus`, `ml_platform_and_gpu_infrastructure`, `gitops_argocd_flux`. **This is the highest-leverage item in the whole plan** — it is what makes the next audit cheap and what stopped the OTel deep-dive from rotting. **4h.**

**Wave 1 total: ~13 hours across 3 parallel agents.**

### Wave 2 — Q&A backfill to the section's own stated bar (parallel, 2 agents)
14. Raise the seven `CLAUDE.md`-designated deep modules from 15/16 to 18: `kubernetes_architecture`, `kubernetes_networking`, `kubernetes_security`, `infrastructure_as_code_terraform`, `observability_metrics_prometheus`, `sre_principles_and_slos`, `ml_platform_and_gpu_infrastructure`. ~20 new Q&As total, each with an authored `**Short:**` line. **6h.**

*Ordering constraint from root `CLAUDE.md`: this section is the most version-sensitive in the repo, so any Q&A whose answer touches wave 1's corrections must be written after wave 1, not before. In practice: run wave 2 after wave 1 for the four Kubernetes modules, concurrently for the other three.*

### Wave 3 — P0 additions (sequential-ish, 2 agents)
15. `device_scheduling_and_dra.md` (550-700 lines, 18 Q&As). Blocks 16. **8h.**
16. `gpu_scheduling_and_inference_ops.md` (600-750 lines, 18 Q&As) + reframe the parent module's §1 + bring `design_ml_platform_infrastructure.md` to 950 lines. **12h.**
17. `regulated_supply_chain.md` (450-600 lines, 15 Q&As). Independent — can start immediately. **7h.**
18. `ai_workloads_and_agent_operations.md` (500-650 lines, 15 Q&As). Independent. **8h.**

**Wave 3 total: ~35 hours; 16 depends on 15, the rest parallel.**

### Wave 4 — P1 additions (parallel, 3 agents)
19. `gpu_and_ai_finops.md` + FOCUS 1.3 expansion + a dated pricing anchor in `cloud_cost_optimization_finops`. **7h.**
20. `cilium_and_ebpf_dataplane.md`. **7h.**
21. `oci_image_formats_and_lazy_pulling.md` + move rootless from `containers_and_docker` to `container_runtimes_and_oci` + collapse the duplicate Dockerfiles. **6h.**
22. `workload_identity_and_federation.md` + README §2 boundary note. **6h.**
23. `design_gpu_training_and_inference_cluster.md` (950-1050 lines). **10h.**
23a. `serverless_and_faas`: add arm64/Graviton as a cost lever in §6's cost arithmetic and Lambda response streaming in §4; +2 Q&As. **2h.**

**Wave 4 total: ~38 hours.**

### Wave 5 — structure, and the owner decision
24. **Owner decision required: create a Principal tier?** (§3c). If yes, tag 11 modules in the `<!-- study-paths -->` block. **1h once decided.**
25. Senior-tier changes (§3b): six tag edits in the block. **30 min.**
26. `platform_engineering_and_idp` 38 → 19 in `STUDY_ORDER["devops"]` (`game/app.js`); re-title Phase 8 to drop "electives" in README §4 and `CLAUDE.md`'s phase table. **1h.**
27. P2 additions: `environment_promotion.md` (Kargo), `design_multi_tenant_platform.md`, `cross_cutting/dra_and_device_plugins.md`. **20h.**

### Files that must be updated whenever a file is added or a tier changes
Every wave-3/4/5 item touches these; batch them per commit:
- `devops/README.md` → the `<!-- study-paths -->` block (**same commit as the new file — a missing line is fatal under `--strict`**), the §3 Module Table, and the §8 status tables.
- `devops/CLAUDE.md` → module list, deep-dive sub-file list (currently says "Deep-dive sub-files — 1"), case-study list, cross-cutting list.
- Root `README.md` → the DevOps phase table; root `CLAUDE.md` → the DevOps row's module/case-study/sub-file counts.
- `game/app.js` → `STUDY_ORDER["devops"]` **only** for the one module move (item 26). New sub-files need no entry — they group under their parent via `splitModulePath()`.
- Then `python3 game/extract.py --write-paths`, then `python3 game/extract.py --strict`, then the full `scripts/build_banks.sh` (the script's own verifier runs *after* `--strict` and can still fail).

### Total effort
**~118 hours of agent work**, or roughly 32 hours of wall-clock with 3-4 agents running in parallel. Waves 0-2 (~21h) deliver most of the correctness value and should not wait on the rest.

---

## 7. Confidence and gaps

**High confidence.** Every structural number in §0 was measured directly (heading presence, Q&A counts, line counts, Mermaid counts across all 42 files), and the 686-question total reconciles exactly with `extract.py`'s own per-section output. Every entry in §2 was web-verified today against a primary or near-primary source and is cited. Every "absent" claim in §1 was verified by a section-wide grep, not inferred: `DRA`/`ResourceClaim`, `SPIFFE`/`SPIRE`, `Kargo`, `MCP`, `agentic`, `Terraform Stacks`, `CRA`/`Cyber Resilience`, `vLLM`, and `GPU`-in-the-FinOps-module all return zero.

**What I could not verify, and what I would check.**

1. **Cloud price bands.** I verified the spot-checkable list prices (CloudFront invalidation, and by inspection the stable S3/NAT/Lambda figures). I did **not** verify the discount *percentage bands* in `cloud_cost_optimization_finops` L22-24/L61-64 — Spot "up to 90%", RI "40-72%", Savings Plans "30-66%". These are the classic rot point, they carry no dated source anchor, and AWS restates them periodically. Check against the current AWS pricing pages and add a "checked YYYY-MM-DD" line.
2. **The 8 GPU price figures** in `ml_platform_and_gpu_infrastructure` (the `p4de.24xlarge` ~$27.45/hr anchor and the five tables that derive from it). The section's own `CLAUDE.md` records that a previous audit found this module "4x wrong on top of a per-GPU decimal slip that fed five downstream tables" — the same arithmetic is still load-bearing and I re-verified none of it. Recompute rather than re-read.
3. **`kubernetes_networking`'s AWS VPC CNI figures** — the 55,000-connections-per-destination NAT ceiling and the "~250 pods per /24" prefix-delegation math. Both are specific AWS product ceilings and neither was checked.
4. **Three product-naming claims that are *currently right and could flip*:** "Jira Service Management Operations" (post-Opsgenie), "Grafana IRM" (post-Grafana OnCall), and the Elastic/OpenSearch tri-licence. All three have already been renamed once; each rename is exactly the kind the audit caught last time.
5. **`Node 24` / `gcr.io/distroless/nodejs24-debian13`** pinned four times in `containers_and_docker`. Plausible but unverified.
6. **AWS Secrets Manager's `$0.40/secret/month + $0.05 per 10k API calls`** (`secrets_management.md` L59) is the base rate for a derived cost table at L327-336 producing "$240.00 / $2,400.00 / $24,000.00 per year". If the base rate moved, six downstream figures moved with it silently. Unverified.
7. **Two subagent batches did not return before this report closed** — the CI/CD group (`helm`, `ci_cd_fundamentals`, `ci_cd_platforms`, `deployment_strategies`, `gitops_argocd_flux`, `platform_engineering_and_idp`) and the case-study group. I covered both by direct targeted grep rather than full profiling, which is how I know Helm 4 (`--rollback-on-failure`, WASM plugins, server-side apply), Argo Rollouts/Flagger driving Gateway API `backendRefs[].weight`, OIDC federation and `permissions:` least-privilege are all correct and current, and how I know Kargo is absent. What I have **not** done is a per-file quality-bar pass on the 13 case studies against the 900-1100 / 4+ cross-refs / named-companies / quantified-impact criteria. I measured their line counts (avg 930, only `design_ml_platform_infrastructure` at 720 below bar) and spot-read four; a full case-study depth audit is the one piece of this brief left genuinely incomplete.
9. **One structural oddity I chose not to call a defect.** The seven `case_studies/cross_cutting/*.md` files are **not** listed in the README's `<!-- study-paths -->` block while the 13 `design_*.md` files are. `extract.py` reports `wiring check: OK`, so this passes — `case_studies/` paths are excluded from the inventory guard. It is an inconsistency inside a passing build, not a break. Worth deciding deliberately rather than leaving as an accident.
