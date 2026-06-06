# Design a Multi-Tenant Kubernetes Platform

> Running Kubernetes as a product is like running an apartment building, not handing every team a plot of land and a shovel. The platform team owns the foundation, plumbing, elevators, and fire code; tenants get a furnished unit with a key, a meter, and rules they cannot break.

**Key insight:** The hard problem is not running one Kubernetes cluster — `kubeadm` does that in 20 minutes. The hard problem is running 50 clusters and 15,000 nodes for 200 product teams who must not see, starve, or page each other, while one platform team of 12 engineers keeps the control-plane SLO at 99.95% and ships a `kubectl`-free golden path. Everything below is about turning a pile of YAML into a paved road.

---

## Intuition

Imagine a 2000-engineer company where every team wants "their own Kubernetes." If each team runs its own cluster, you get 200 snowflake clusters on 200 different versions, 200 different ingress configs, and a security team that cannot sleep. If you put all 2000 engineers into one giant cluster, a single bad `apply` — a Job that spawns 50,000 pods — takes down everyone, and your etcd database hits its 8 GB ceiling and the whole company's control plane goes read-only.

The platform answer is a **fleet**: a small number of well-run, homogeneous clusters, sharded by blast-radius and region, fronted by a **management plane** that provisions clusters, onboards tenants, enforces policy, and bills usage. Tenants never touch cluster lifecycle. They push a Git commit describing an app; the platform reconciles it onto the right cluster with quotas, network policy, and observability already wired in.

**Mental model:** three layers.
1. **Management plane** — one (HA) cluster that runs Cluster API / Crossplane controllers, Argo CD, the policy engine, and the tenant catalog. It is the "factory."
2. **Workload fleet** — the 50 clusters that actually run product pods. They are cattle: created, upgraded, and deleted by the management plane.
3. **Tenant abstraction** — namespaces (cheap, soft isolation) or virtual clusters (stronger, per-tenant API server) carved into the fleet, each capped by `ResourceQuota`, fenced by `NetworkPolicy`, and gated by admission policy.

**Why the system exists:** to convert "I need to run a service" from a 3-week infra ticket into a 5-minute self-service merge, while keeping 200 tenants isolated, the bill attributable, and the upgrade treadmill (Kubernetes ships a minor every ~4 months, supports each for ~14 months) survivable by a 12-person team.

This case study assumes familiarity with the control-plane internals in [../kubernetes_architecture/README.md](../kubernetes_architecture/README.md), the RBAC/PSA model in [../kubernetes_security/README.md](../kubernetes_security/README.md), and the GitOps reconciliation loop in [../gitops_argocd_flux/README.md](../gitops_argocd_flux/README.md).

---

## 1. Requirements Clarification

### Functional requirements

- **Cluster lifecycle as an API.** Platform engineers (and automation) create, scale, upgrade, and delete clusters declaratively. A new cluster is provisioned end-to-end (control plane + node pools + CNI + base addons) in under 30 minutes with zero manual `kubectl`.
- **Self-service tenant onboarding.** A product team opens a PR against a `tenants/` repo describing their team, environment (dev/staging/prod), and resource ask. Merge provisions a namespace (or vcluster) with quota, network policy, RBAC, and dashboards in under 5 minutes — no platform-team ticket.
- **Golden paths.** A scaffolded service (via Backstage software template) ships to production through a paved CI/CD + GitOps pipeline without the developer writing raw Kubernetes manifests.
- **Tenant isolation.** A tenant cannot view, mutate, network-reach, or resource-starve another tenant. Hard multi-tenancy (untrusted code) uses vclusters or dedicated node pools; soft multi-tenancy (trusted internal teams) uses namespaces.
- **Policy enforcement.** Org-wide guardrails (no `:latest` images, no privileged pods, required `team` label, image-registry allowlist, mandatory resource requests) are enforced at admission, not in code review.
- **Multi-cluster placement & networking.** Workloads are scheduled to clusters by region/compliance/capacity. Services discover each other across clusters; failover moves a tenant's traffic to a healthy region.
- **Cost attribution.** Every namespace's CPU/memory/storage/egress is measured and charged back to a cost center monthly.
- **Fleet observability.** One pane of glass for control-plane health, node saturation, and per-tenant SLOs across all 50 clusters.

### Non-functional requirements

| NFR | Target |
|-----|--------|
| Scale — tenants | 200 tenants (teams), ~2,000 workloads |
| Scale — clusters | 50 workload clusters across 4 regions |
| Scale — nodes | 15,000 nodes (avg 300/cluster, max 1,000/cluster) |
| Control-plane availability SLO | 99.95% (≈21.9 min/month error budget) per cluster |
| Namespace provisioning latency | p95 < 5 min from PR merge to ready |
| New cluster provisioning | < 30 min end-to-end |
| Upgrade cadence | N-1 within 60 days of upstream GA; never run an unsupported minor |
| Policy admission latency | p99 < 100 ms added to API request |
| Cost attribution accuracy | ≥ 95% of spend mapped to a cost center |
| Blast radius | One cluster failure affects ≤ 1/50 = 2% of workloads |
| Platform team size | 12 engineers (the binding constraint) |

### Out of scope

- The application code, business logic, and per-app data models tenants run.
- The CI build pipeline internals (covered in [design_ci_cd_platform.md](design_ci_cd_platform.md)).
- Bare-metal / on-prem provisioning — assume AWS EKS as the primary substrate, GCP GKE as secondary.
- Database-as-a-service and stateful data platforms — those are sibling platform products, cross-referenced not duplicated.
- Edge / IoT clusters.

---

## 2. Scale Estimation

### Fleet and pod math

```
Clusters:                 50
Avg nodes per cluster:    300   (range 100–1,000)
Total nodes:              50 × 300 = 15,000

Pods per node (k8s max):  110   (default kubelet --max-pods)
Realistic pods/node:      ~40   (m5.2xlarge: 8 vCPU, 32 GiB; avg pod 200m / 512Mi)
Total scheduled pods:     15,000 × 40 = 600,000 pods across the fleet
Per cluster:              300 × 40 = 12,000 pods
```

A single cluster at 12,000 pods is comfortably inside the upstream-tested envelope (5,000 nodes / 150,000 pods), but the *practical* ceiling for a shared control plane is much lower because of etcd and watch fan-out (see §10).

### etcd object budget

etcd has a default backend quota of **8 GiB** (`--quota-backend-bytes`, max recommended 8 GiB). Every object — pods, endpoints, configmaps, secrets, events, leases — consumes space.

```
Objects per cluster (rough):
  Pods:                     12,000 × ~6 KiB  = 72 MiB
  ReplicaSets/Deployments:   2,000 × ~3 KiB  =  6 MiB
  ConfigMaps + Secrets:      8,000 × ~4 KiB  = 32 MiB
  EndpointSlices:            6,000 × ~3 KiB  = 18 MiB
  Events (1h TTL):          50,000 × ~1 KiB  = 50 MiB
  Leases / nodes / misc:                       ~40 MiB
  -------------------------------------------------
  Steady state:             ~220 MiB live data

Headroom vs 8 GiB:  generous on raw bytes.
```

The real etcd killer is **write throughput and history**, not steady-state bytes: a tenant looping `kubectl apply` or a leaking controller can push 10,000 writes/sec, blow up the MVCC history before compaction, and spike etcd p99 from 10 ms to 2 s. That is why we cap **events**, set `--max-mutating-requests-inflight`, and shard tenants across clusters.

### Tenant density

```
200 tenants / 50 clusters = 4 tenants per cluster (avg)
Each tenant: ~10 workloads × ~4 pods = 40 pods, 3 namespaces (dev/staging/prod span clusters)
```

Prod tenants land on prod-tier clusters; dev/staging share denser, cheaper clusters.

### Cross-AZ / cross-cluster traffic

```
Assume 30% of service-to-service calls cross an AZ boundary.
Fleet RPS:                 ~2,000,000 internal RPS
Cross-AZ share:            600,000 RPS
Avg payload:               4 KiB
Cross-AZ bytes:            600,000 × 4 KiB = 2.3 GiB/s = ~5,900 TiB/month
AWS cross-AZ data:         $0.01/GB each direction → ~$120k/month if unmanaged
```

This single line is why CNI and topology-aware routing (§4.3, §5) are a *cost* decision, not just a latency one. Topology-aware hints that keep 70% of traffic same-AZ cut this to ~$36k/month.

### Cost per cluster per month (EKS, us-east-1)

```
Control plane:   $0.10/hr × 730 hr            = $73 / cluster / month
Nodes (300 × m5.2xlarge on-demand $0.384/hr): 300 × $0.384 × 730 = $84,096
  -- with 60% Savings Plan / Spot blend:      ~$42,000
EBS (300 × 100 GiB gp3 $0.08/GB):             $2,400
Data transfer (per above, managed):            ~$36,000 / fleet ÷ 50 ≈ $720
  -------------------------------------------------
Per cluster:  ~$45,000 / month
Fleet (50):   ~$2.25M / month  = ~$27M / year
```

The control plane ($73) is a rounding error; **nodes are 93% of spend**, so the platform's biggest cost lever is bin-packing and autoscaling (§4, §10), not control-plane efficiency.

---

## 3. High-Level Architecture

```
                         ┌──────────────────────────────────────────────────────┐
                         │          PLATFORM CONTROL (Management Plane)           │
                         │   HA EKS cluster, 3 AZ, no tenant workloads ever       │
                         │                                                        │
   Platform/Tenant       │  ┌───────────────┐  ┌────────────────┐  ┌──────────┐  │
   engineers ──PR──────► │  │ Cluster API   │  │  Crossplane    │  │ Argo CD  │  │
   (GitOps repos)        │  │ (CAPI/CAPA)   │  │ (cloud infra)  │  │ (App-of- │  │
                         │  │ controllers   │  │ RDS/S3/IAM/VPC │  │  Apps,   │  │
                         │  └──────┬────────┘  └───────┬────────┘  │ AppSets) │  │
                         │         │ provisions        │           └────┬─────┘  │
                         │  ┌──────┴─────────┐  ┌───────┴────────┐       │        │
                         │  │ Tenant Catalog │  │ Policy: Kyverno │      │ syncs  │
                         │  │ (CRD: Tenant)  │  │ /OPA Gatekeeper │      │        │
                         │  └────────────────┘  └────────────────┘       │        │
                         │  ┌────────────────┐  ┌────────────────────────┴─────┐  │
                         │  │ Backstage IDP  │  │ Fleet Observability:         │  │
                         │  │ (golden paths) │  │ Thanos/Mimir + Grafana +     │  │
                         │  └────────────────┘  │ OTel Collector + Loki        │  │
                         │                      └──────────────────────────────┘  │
                         └───────────────┬────────────────────────────────────────┘
                                         │ Cluster API + Argo CD push/pull
        ┌────────────────────────────────┼─────────────────────────────────┬─────────────┐
        ▼                                 ▼                                 ▼             ▼
 ┌──────────────┐                 ┌──────────────┐                  ┌──────────────┐  ┌────────┐
 │ us-east-1    │                 │ us-west-2    │                  │ eu-west-1    │  │ ...50  │
 │ PROD cluster │                 │ PROD cluster │                  │ PROD cluster │  │ total  │
 │ ┌──────────┐ │                 │              │                  │              │  │        │
 │ │tenant-A  │ │  Cilium Cluster │              │  Cilium ClusterMesh / Submariner │  │        │
 │ │ ns+quota │◄┼─────────────────┼──────────────┼──────────────────┼──────────────┼──┘        │
 │ │ +netpol  │ │                 │              │                  │              │            │
 │ ├──────────┤ │                 │ ┌──────────┐ │                  │ ┌──────────┐ │            │
 │ │tenant-B  │ │                 │ │vcluster-C│ │                  │ │tenant-D  │ │            │
 │ │ (vcluster)│ │                 │ │(hard iso)│ │                  │ └──────────┘ │            │
 │ └──────────┘ │                 │ └──────────┘ │                  │              │            │
 │ Karpenter    │                 │ Karpenter    │                  │ Karpenter    │            │
 │ + per-cluster│                 │              │                  │              │            │
 │ Prometheus   │── remote_write ─┼──────────────┼─► Thanos/Mimir ◄─┼──────────────┘            │
 └──────────────┘ (agent mode)    └──────────────┘  (global query)  └──────────────┘            │
```

### Component inventory

| Component | Role |
|-----------|------|
| **Management cluster** | HA EKS, 3 AZs, runs only platform controllers — never tenant pods. The factory. |
| **Cluster API (CAPI + CAPA provider)** | Declarative cluster lifecycle: `Cluster`, `MachineDeployment`, `KubeadmControlPlane` (or `AWSManagedControlPlane` for EKS). |
| **Crossplane** | Reconciles non-K8s cloud infra (VPC, IAM roles, RDS, S3 buckets) as CRDs in the same GitOps flow. |
| **Argo CD (App-of-Apps + ApplicationSets)** | Pulls desired state from Git, fans out base addons and tenant manifests to all fleet clusters. |
| **Tenant Catalog (`Tenant` CRD + controller)** | The onboarding API. Expands a `Tenant` CR into namespace + quota + netpol + RBAC + dashboards. |
| **Policy engine (Kyverno / OPA Gatekeeper)** | Validating + mutating admission webhooks enforcing org guardrails on every cluster. |
| **CNI (Cilium)** | eBPF dataplane, `NetworkPolicy`, ClusterMesh for cross-cluster service discovery, Hubble for flow visibility. |
| **Karpenter** | Per-cluster node autoscaling and bin-packing; consolidates underused nodes. |
| **Backstage IDP** | Developer portal: software templates (golden paths), service catalog, TechDocs. |
| **Fleet observability** | Per-cluster Prometheus (agent mode) → Thanos/Mimir global query; OTel Collector; Loki logs; Grafana. |

### Data flow — tenant onboarding (the critical path)

1. Team opens PR adding `tenants/team-payments.yaml` (a `Tenant` CR) to the platform GitOps repo.
2. CI validates schema + runs `conftest`/Kyverno CLI against org policy; PR merges.
3. Argo CD on the management cluster detects the commit, applies the `Tenant` CR.
4. The **Tenant controller** reconciles: picks a target cluster (placement logic), creates a child Argo CD `ApplicationSet` that templates `Namespace`, `ResourceQuota`, `LimitRange`, `NetworkPolicy`, `RoleBinding`, and Grafana dashboard onto the chosen workload cluster.
5. Kyverno admits the namespace (label/quota present → pass).
6. Within p95 < 5 min the namespace is ready; Backstage shows the tenant a "ready" tile with a kubeconfig-less link.

### Data flow — cluster provisioning

1. Platform engineer merges a `Cluster` + `AWSManagedControlPlane` + `MachineDeployment` manifest.
2. Argo CD applies it to the management cluster; CAPA calls the EKS API.
3. Control plane comes up (~10 min for EKS); CAPI bootstraps node pools.
4. Argo CD `ApplicationSet` (cluster generator) auto-discovers the new cluster and installs the **base addon bundle** (Cilium, Karpenter, Kyverno, Prometheus agent, OTel) — GitOps, no manual `kubectl`.
5. Cluster registers into the fleet inventory; Thanos starts receiving its metrics.

---

## 4. Component Deep Dives

### 4.1 Cluster provisioning via Cluster API + Crossplane

Cluster API (CAPI) turns "a cluster" into a Kubernetes object the management cluster reconciles. For EKS we use the AWS provider's managed control plane so we are not babysitting etcd ourselves.

```
   Git (Cluster manifests)
        │  Argo CD apply
        ▼
 ┌─────────────────────────────────────────────┐
 │           Management cluster                 │
 │  ┌───────────┐    ┌──────────────────────┐   │
 │  │  CAPI core │──►│ CAPA (AWS provider)   │──┼──► EKS API: CreateCluster
 │  └───────────┘    │ AWSManagedControlPlane │  │    CreateNodegroup
 │  ┌───────────┐    │ AWSManagedMachinePool  │  │
 │  │ Crossplane │──►│ (Karpenter NodePool)   │  │
 │  └───────────┘    └──────────────────────┘   │──► IAM, VPC, SGs via Crossplane
 └─────────────────────────────────────────────┘
```

```yaml
# cluster-prod-use1.yaml  — one cluster, fully declarative
apiVersion: cluster.x-k8s.io/v1beta1
kind: Cluster
metadata:
  name: prod-use1
  namespace: fleet
  labels:
    tier: prod
    region: us-east-1
spec:
  infrastructureRef:
    apiVersion: infrastructure.cluster.x-k8s.io/v1beta2
    kind: AWSManagedControlPlane
    name: prod-use1-cp
  controlPlaneRef:
    apiVersion: infrastructure.cluster.x-k8s.io/v1beta2
    kind: AWSManagedControlPlane
    name: prod-use1-cp
---
apiVersion: infrastructure.cluster.x-k8s.io/v1beta2
kind: AWSManagedControlPlane
metadata:
  name: prod-use1-cp
  namespace: fleet
spec:
  region: us-east-1
  version: "1.30"                 # pinned; bumped via PR for upgrades
  endpointAccess:
    public: false                 # private API endpoint only
    private: true
  vpcCni:
    disable: true                 # we run Cilium instead of aws-vpc-cni
  logging:
    apiServer: true
    audit: true                   # audit logs are non-negotiable for prod
---
apiVersion: infrastructure.cluster.x-k8s.io/v1beta2
kind: AWSManagedMachinePool
metadata:
  name: prod-use1-system
  namespace: fleet
spec:
  instanceType: m5.2xlarge
  scaling:
    minSize: 3
    maxSize: 6                    # only system/addon pods; tenants use Karpenter
  taints:
    - key: dedicated
      value: system
      effect: NoSchedule
```

The version field is the upgrade knob: bump `1.30` → `1.31` in a PR, Argo CD rolls the control plane, then CAPA cycles node pools surge-style. The whole fleet upgrade is "merge 50 PRs" (automated via a renovate-style bot), gated by the eval pipeline in §8.

Cluster lifecycle internals reference: [../kubernetes_architecture/README.md](../kubernetes_architecture/README.md).

### 4.2 Tenant isolation — the BROKEN → FIX that defines the platform

Soft multi-tenancy means many trusted teams share a cluster's node pool. The cardinal sin is a namespace with no resource ceiling and no network fence. Here is the naive onboarding manifest a junior platform engineer might ship:

```yaml
# BROKEN: a bare namespace. This is the #1 cause of "noisy neighbor" incidents.
apiVersion: v1
kind: Namespace
metadata:
  name: team-payments
# That's it. No ResourceQuota, no LimitRange, no NetworkPolicy, no RBAC.
```

What goes wrong, concretely:

- A `team-payments` Deployment with no resource requests (because nothing forces them) gets scheduled with `BestEffort` QoS. Under node pressure the kubelet evicts *other tenants'* `Burstable` pods first if requests are mis-set, or the payments pods themselves balloon and OOM the node.
- A buggy batch Job spawns 5,000 pods. With no quota, the scheduler happily places them, Karpenter scales the cluster from 300 to 480 nodes, and `team-search` — sharing the cluster — watches its pods go `Pending` while the autoscaler catches up. A $0 mistake just cost $25k of surprise compute and an SLO breach for an innocent tenant.
- With no `NetworkPolicy`, `team-payments` pods can `curl` directly into `team-search`'s Redis. A single compromised dependency now has lateral reach across the whole cluster.

The fix is a tenant *template* the controller renders for every namespace — quota, limit defaults, default-deny networking, and least-privilege RBAC:

```yaml
# FIXED: rendered by the Tenant controller for every namespace.
apiVersion: v1
kind: Namespace
metadata:
  name: team-payments
  labels:
    team: payments
    cost-center: cc-4471
    pod-security.kubernetes.io/enforce: restricted   # PSA: no privileged pods
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: team-payments-quota
  namespace: team-payments
spec:
  hard:
    requests.cpu: "64"
    requests.memory: 256Gi
    limits.cpu: "128"
    limits.memory: 512Gi
    pods: "400"
    count/jobs.batch: "50"        # the 5,000-pod Job is now rejected at 50
    persistentvolumeclaims: "30"
---
apiVersion: v1
kind: LimitRange                  # forces requests so QoS is never BestEffort by accident
metadata:
  name: team-payments-defaults
  namespace: team-payments
spec:
  limits:
    - type: Container
      default:        { cpu: 500m, memory: 512Mi }
      defaultRequest: { cpu: 100m, memory: 128Mi }
      max:            { cpu: "8",  memory: 16Gi }
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy               # default-deny ingress; tenants opt-in to specific peers
metadata:
  name: default-deny-ingress
  namespace: team-payments
spec:
  podSelector: {}
  policyTypes: [Ingress]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy               # allow same-tenant + ingress-gateway only
metadata:
  name: allow-same-tenant
  namespace: team-payments
spec:
  podSelector: {}
  policyTypes: [Ingress]
  ingress:
    - from:
        - namespaceSelector:
            matchLabels: { team: payments }
        - namespaceSelector:
            matchLabels: { kubernetes.io/metadata.name: ingress-system }
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding                 # team gets edit in their ns only — never cluster-admin
metadata:
  name: team-payments-edit
  namespace: team-payments
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: edit
subjects:
  - apiGroup: rbac.authorization.k8s.io
    kind: Group
    name: oidc:team-payments       # mapped from corporate IdP group
```

For **hard** isolation (untrusted code, regulated workloads, or a tenant that genuinely needs cluster-scoped resources like CRDs), namespaces are not enough — `RoleBinding` cannot stop a tenant from listing all namespaces' metadata, and CRDs are cluster-global. We give those tenants a **vcluster**: a syncer + a virtual API server (k3s/k8s) running as pods inside a host namespace. The tenant gets what looks like cluster-admin on their own API server; the syncer translates their pods down to the host cluster, prefixed and quota-bounded.

```yaml
# vcluster for a tenant needing cluster-admin semantics, isolated from the host.
apiVersion: v1
kind: HelmRelease            # via Argo CD / Flux
metadata: { name: vcluster-fraud-ml, namespace: vc-fraud-ml }
spec:
  chart: vcluster
  values:
    sync:
      toHost:
        ingresses: { enabled: true }
      fromHost:
        nodes: { enabled: true, selector: { labels: { tier: gpu } } }
    isolation:
      enabled: true          # injects ResourceQuota + LimitRange + NetworkPolicy on host ns
      resourceQuota:
        quota: { requests.cpu: "32", requests.memory: 128Gi, pods: "200" }
```

Tradeoff: a vcluster adds ~150–300 MiB overhead and ~5–15 ms API latency per tenant, so we reserve it for the ~10% of tenants that need it. The rest stay on namespaces. RBAC and PSA details: [../kubernetes_security/README.md](../kubernetes_security/README.md).

### 4.3 Multi-cluster networking

Three problems: pods in cluster A must reach a service in cluster B; the same service name should resolve to the nearest healthy cluster; and a cluster failure should fail traffic over. We use **Cilium ClusterMesh** (eBPF, global services) as primary; Submariner is the portable fallback when CNIs differ across regions.

```
 cluster prod-use1 (Cilium)            cluster prod-euw1 (Cilium)
 ┌─────────────────────┐               ┌─────────────────────┐
 │ pod ──► svc:payments │               │ svc:payments         │
 │   (global, annotated │◄──ClusterMesh─┤  (global endpoint)   │
 │    service.cilium.io/│   (mTLS VXLAN │                      │
 │    global: "true")   │    over TGW)  │  failover target     │
 └─────────────────────┘               └─────────────────────┘
        │  topology-aware: prefer local endpoints, spill to remote on failure
```

```yaml
# A global service: same name in both clusters, Cilium load-balances with locality bias.
apiVersion: v1
kind: Service
metadata:
  name: payments
  namespace: team-payments
  annotations:
    service.cilium.io/global: "true"
    service.cilium.io/affinity: "local"      # prefer same-cluster endpoints
spec:
  selector: { app: payments }
  ports: [{ port: 8080 }]
```

`affinity: local` is the cost lever from §2: it keeps the 70% of traffic that *can* stay in-cluster local, spilling cross-region only on local-endpoint failure. Deep dive: [cross_cutting/multi_cluster_networking.md](cross_cutting/multi_cluster_networking.md).

### 4.4 Policy admission (Kyverno)

Guardrails must be enforced *before* an object is persisted, on every cluster, identically. We run Kyverno as a validating + mutating webhook, with policies shipped by GitOps to the whole fleet.

```yaml
# Reject :latest tags and non-allowlisted registries; mutate-in the cost-center label.
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: image-and-label-guardrails
spec:
  validationFailureAction: Enforce      # block, don't just audit
  background: true
  rules:
    - name: disallow-latest-tag
      match: { any: [{ resources: { kinds: [Pod] } }] }
      validate:
        message: "Image tag ':latest' is not allowed; pin a digest or semver."
        pattern:
          spec:
            containers:
              - image: "!*:latest"
    - name: registry-allowlist
      match: { any: [{ resources: { kinds: [Pod] } }] }
      validate:
        message: "Images must come from 123456789.dkr.ecr.us-east-1.amazonaws.com"
        pattern:
          spec:
            containers:
              - image: "123456789.dkr.ecr.us-east-1.amazonaws.com/*"
    - name: require-team-label
      match: { any: [{ resources: { kinds: [Namespace] } }] }
      validate:
        message: "Namespaces must carry a 'team' and 'cost-center' label."
        pattern:
          metadata:
            labels:
              team: "?*"
              cost-center: "?*"
```

Equivalent guardrails can be expressed in OPA Gatekeeper's Rego if the org standardizes on OPA elsewhere:

```rego
# gatekeeper constraint template logic: deny privileged containers
package k8srequiredsecurity

violation[{"msg": msg}] {
  c := input.review.object.spec.containers[_]
  c.securityContext.privileged == true
  msg := sprintf("container %q runs privileged; forbidden by org policy", [c.name])
}
```

The p99 admission budget is 100 ms (§1). Kyverno background scans catch drift on already-admitted objects. We measure webhook latency as a first-class SLI because a slow or failed webhook with `failurePolicy: Fail` can make the entire API server unavailable — a war story in §9.

---

## 5. Design Decisions & Tradeoffs

### Decision 1 — Tenant isolation model: namespace vs cluster-per-tenant vs vcluster

- **Decision:** Namespace-per-tenant by default (soft multi-tenancy); vcluster for the ~10% needing cluster-scoped resources or stronger isolation; dedicated cluster only for regulated/compliance-bound tenants.
- **Alternatives:** Cluster-per-tenant (200 clusters) — 200× the control-plane cost and upgrade toil for a 12-person team. One giant cluster — single blast radius, etcd ceiling.
- **Rationale:** A 12-engineer team cannot operate 200 clusters; namespaces give 90% of isolation at ~1% of the operational cost. vcluster fills the gap where namespaces leak (CRDs, cluster-admin).
- **Consequences:** Soft isolation requires *disciplined* quotas, network policy, and PSA — exactly the §4.2 template. A bug in the tenant template is a fleet-wide risk.

### Decision 2 — Many medium clusters vs one huge cluster

- **Decision:** ~50 clusters of ~300 nodes, sharded by region + tier + blast-radius, not one 15,000-node cluster.
- **Alternatives:** A few 5,000-node clusters (fewer to operate) — but etcd, watch fan-out, and a single bad webhook take down 33% of the fleet.
- **Rationale:** Blast radius. One failed cluster = 2% of workloads. Upgrades and CNI changes roll cluster-by-cluster as canaries.
- **Consequences:** More control planes to pay for ($73 × 50 — trivial) and more inventory to manage — solved by treating clusters as cattle via CAPI.

### Decision 3 — CNI: Cilium vs Calico vs aws-vpc-cni

- **Decision:** Cilium (eBPF) fleet-wide.
- **Alternatives:** Calico (mature `NetworkPolicy`, iptables/eBPF) — good, but ClusterMesh + Hubble + bandwidth manager swing it. aws-vpc-cni — exhausts ENI IP limits at high pod density and lacks L7 policy.
- **Rationale:** eBPF dataplane scales past iptables' O(n) rule blowup at 12,000 pods; ClusterMesh solves §4.3; Hubble gives per-flow visibility for the security team.
- **Consequences:** eBPF requires newer kernels (≥ 5.10) and a steeper operational learning curve; a Cilium upgrade is a dataplane event handled as a canary.

### Decision 4 — Provisioning: Cluster API vs raw Terraform vs managed-only

- **Decision:** Cluster API (CAPA) for cluster lifecycle + Crossplane for surrounding cloud infra, both via GitOps.
- **Alternatives:** Terraform-per-cluster (drift, no reconciliation loop, manual `apply`). Click-ops in the EKS console (un-auditable).
- **Rationale:** A reconciliation loop self-heals drift and makes "the fleet" a queryable set of objects. Upgrades become declarative field bumps.
- **Consequences:** CAPI/CAPA is operationally young; the management cluster becomes tier-0 — if it dies, you cannot provision (but running clusters keep serving).

### Decision 5 — Autoscaling: Karpenter vs Cluster Autoscaler

- **Decision:** Karpenter per cluster.
- **Alternatives:** Cluster Autoscaler with fixed ASGs — slower, node-group sprawl, worse bin-packing.
- **Rationale:** Karpenter provisions right-sized nodes in ~30–60 s from raw EC2 fleet, consolidates underused nodes (the §2 cost lever), and mixes Spot/on-demand by policy.
- **Consequences:** Aggressive consolidation can churn nodes and disrupt pods lacking PDBs — mitigated with `do-not-disrupt` annotations and PodDisruptionBudgets.

### Decision 6 — Observability: per-cluster Prometheus + global Thanos vs central scrape

- **Decision:** Prometheus in agent mode per cluster, `remote_write` to a central Thanos/Mimir.
- **Alternatives:** One central Prometheus scraping all clusters — cross-region scrape latency, single point of failure, cardinality explosion.
- **Rationale:** Local scrape survives network partitions; global query gives one pane of glass. Cardinality is managed at the agent (relabeling). See [cross_cutting/prometheus_cardinality_and_scale.md](cross_cutting/prometheus_cardinality_and_scale.md).
- **Consequences:** `remote_write` adds egress cost and needs WAL tuning; Mimir/Thanos becomes its own scaling problem.

### Decision 7 — Policy engine: Kyverno vs OPA Gatekeeper

- **Decision:** Kyverno primary (YAML-native, mutating + validating + generate).
- **Alternatives:** OPA Gatekeeper (Rego) — more expressive for complex cross-object logic, steeper to author.
- **Rationale:** Platform tenants read YAML, not Rego; Kyverno's `generate` rules can auto-create the §4.2 default-deny `NetworkPolicy` in every new namespace.
- **Consequences:** Very complex policies (graph-of-objects checks) are awkward in Kyverno; we keep a small OPA escape hatch for those.

### Comparison table

| Dimension | Namespace | vcluster | Cluster-per-tenant |
|-----------|-----------|----------|--------------------|
| Isolation strength | Soft (RBAC+netpol+quota) | Strong (own API server) | Strongest (own etcd) |
| Cluster-scoped resources (CRDs) | Shared, conflict-prone | Per-tenant, isolated | Per-tenant |
| Overhead per tenant | ~0 | ~150–300 MiB + 5–15 ms | Full control plane ($73+nodes) |
| Upgrade blast radius | Whole cluster | Host cluster | Independent |
| Operability at 200 tenants | Excellent | Good (~20 vclusters) | Untenable (200 control planes) |
| Use when | Trusted internal team | Needs cluster-admin / CRDs | Regulated / compliance boundary |

---

## 6. Real-World Implementations

- **Datadog** runs one of the largest known multi-cluster fleets — tens of clusters and, per their public talks, individual clusters that have been pushed toward and past the upstream-tested envelope. They publicized hitting etcd and Cilium scaling limits at ~thousands of nodes per cluster, which is exactly why they shard. Their compute platform exposes a self-service abstraction so product teams deploy without touching cluster internals; they invested heavily in Cilium/eBPF and contributed scaling fixes upstream.

- **Airbnb** built "Kubernetes as a product" with a config-generation layer (`kube-gen`) that turns a small declarative input into the full set of K8s objects, and a homegrown CI/CD ("Spinnaker"-era then internal) so engineers never hand-write manifests — the golden-path principle in §1. They run many clusters and standardized on a paved road precisely to avoid 1,000 snowflake configs.

- **Spotify** pairs **Backstage** (which they created and open-sourced) as the developer portal/IDP with GKE clusters. Backstage software templates are their golden paths — scaffold a service, get a repo + CI + K8s manifests + ownership metadata, all wired in. Backstage is now the reference IDP for §4's onboarding UX.

- **Mercari** runs a multi-tenant platform on GKE with a strong "platform team enables, doesn't gatekeep" philosophy, heavy use of namespaces + network policy + per-team quotas, and PR-based self-service onboarding. They have written publicly about microservices platform isolation and tenant guardrails.

- **Cloudflare** operates Kubernetes at the edge across many locations and has published on the limits of large clusters and their move toward more, smaller clusters with strong policy enforcement — reinforcing Decision 2 (blast radius over consolidation). Their networking work informs the eBPF-first stance in Decision 3.

- **Adobe** has spoken about multi-cluster management across cloud providers using a fleet-management approach (Cluster API-style lifecycle + centralized policy + GitOps), a close analog to the management-plane design in §3.

---

## 7. Technologies & Tools

| Tool | Category | Strengths | Weaknesses | Fit here |
|------|----------|-----------|------------|----------|
| **Cluster API (CAPI/CAPA)** | Cluster lifecycle | Declarative, provider-agnostic, GitOps-native, self-healing | Operationally young; management cluster is tier-0 | Primary lifecycle engine |
| **EKS/GKE managed** | Managed control plane | No etcd babysitting; cloud SLA; integrates with CAPI | Vendor lock-in; less control-plane tuning | The substrate CAPI drives |
| **Rancher (RKE2/Fleet)** | Fleet manager | Turnkey multi-cluster UI + Fleet GitOps; good for mixed/on-prem | Heavier; opinionated; another control plane to run | Alt for on-prem-heavy orgs |
| **Crossplane** | Cloud infra as CRDs | Unifies K8s + non-K8s infra in one GitOps flow; compositions | Steep composition model; reconcile lag on big infra | Surrounding infra (VPC/IAM/RDS) |
| **vcluster** | Tenant isolation | Strong isolation, cluster-admin per tenant, cheap vs real cluster | Per-tenant overhead; syncer edge cases | Hard-multitenant tenants |
| **Karmada** | Multi-cluster scheduling | Propagates workloads across clusters with override policies | Adds a scheduling layer; smaller ecosystem | Alt to Argo ApplicationSets for placement |
| **Argo CD + ApplicationSets** | GitOps delivery | Mature, cluster-generator fan-out, app-of-apps | Scale tuning needed at 50 clusters | Primary delivery to fleet |
| **Kyverno** | Policy | YAML-native, mutate/validate/generate | Weak on complex cross-object logic | Primary admission policy |
| **Cilium** | CNI / dataplane | eBPF scale, ClusterMesh, Hubble, L7 policy | Newer-kernel + ops learning curve | Fleet CNI |

---

## 8. Operational Playbook

### (a) Cluster-upgrade pipeline + eval gate

Upgrades are the platform's heaviest recurring tax. The pipeline:

```
1. Renovate bot opens PR bumping AWSManagedControlPlane.spec.version 1.30 → 1.31
2. CI gate runs:
     - kube-no-trouble (kubent): scan fleet for removed/deprecated APIs in the target version
     - Kyverno CLI: re-validate all org policies against the new API surface
     - conformance smoke: spin an ephemeral CAPI cluster on 1.31, run a golden-path deploy,
       assert tenant onboarding completes < 5 min and a sample cross-cluster call succeeds
3. Canary: merge for ONE dev cluster first; bake 48h; watch SLOs (control-plane error rate,
     scheduler latency, webhook p99). Error-budget burn gate halts further merges.
4. Staged rollout: dev → staging → prod tiers, one region at a time, 1 cluster per region per day.
5. Node pools surge-upgraded (Karpenter drains + replaces with PDBs respected).
```

The error-budget burn gate uses the math in [cross_cutting/slo_error_budget_math.md](cross_cutting/slo_error_budget_math.md): if a canary upgrade burns more than 2% of the monthly control-plane error budget in 1 hour (fast-burn), the rollout auto-pauses and pages.

### (b) Fleet observability

We treat the fleet as one system with per-cluster dimensions. Key SLIs:

- **Control-plane availability** per cluster: `apiserver_request_total{code!~"5.."}` ratio — drives the 99.95% SLO.
- **API request latency**: `apiserver_request_duration_seconds` p99 (watch for etcd backpressure).
- **Webhook latency**: admission webhook p99 (a Kyverno regression here is a fleet incident).
- **Scheduler pending**: `scheduler_pending_pods` (tenant starvation signal).
- **Per-tenant saturation**: quota usage vs limit, exported with `team` / `cost-center` labels.

Per-cluster Prometheus agents `remote_write` to Mimir; **cardinality is the enemy** — 50 clusters × 12,000 pods × dozens of series each is easily 100M+ active series. We aggressively drop high-cardinality labels (pod UID, ephemeral container IDs) at the agent via relabeling, per [cross_cutting/prometheus_cardinality_and_scale.md](cross_cutting/prometheus_cardinality_and_scale.md). OTel Collector (gateway mode) per cluster forwards traces to a central backend with tail-sampling.

```yaml
# prometheus agent: drop the worst cardinality offenders before remote_write
metric_relabel_configs:
  - source_labels: [__name__]
    regex: 'apiserver_request_duration_seconds_bucket'
    action: keep          # keep the SLI buckets...
  - source_labels: [pod]
    regex: '.*-[a-z0-9]{5}$'
    action: labeldrop     # ...drop the random replicaset suffix churn where safe
```

### (c) Named runbooks

**Runbook 1 — etcd database approaching quota.**
- *Symptom:* `etcd_mvcc_db_total_size_in_bytes` > 6 GiB (75% of 8 GiB); API writes slowing, p99 > 500 ms.
- *Diagnosis:* `etcdctl endpoint status`; check for un-compacted history (`etcd_debugging_mvcc_keys_total`), an event flood, or a leaking controller spamming CRs.
- *Mitigation:* Force compaction + defrag (`etcdctl defrag`, one member at a time to avoid quorum loss); cut event TTL; rate-limit the offending namespace via reduced quota.
- *Resolution:* If steady-state is genuinely large, shard the cluster (move a tenant to a new cluster per §10); raise quota only as a stopgap.

**Runbook 2 — admission webhook outage (failurePolicy: Fail).**
- *Symptom:* All writes to a cluster fail with `Internal error: failed calling webhook`. New deploys and even pod restarts blocked.
- *Diagnosis:* `kubectl get validatingwebhookconfiguration`; check Kyverno pod health, webhook TLS cert expiry, and webhook latency dashboards.
- *Mitigation:* If Kyverno is down and blocking the cluster, patch the webhook `failurePolicy` to `Ignore` (break-glass) to restore writes; or scale Kyverno back up.
- *Resolution:* Root-cause the Kyverno crash (OOM? cert rotation?); add a `namespaceSelector` exclusion for `kube-system`; ensure Kyverno has a PDB and runs 3 replicas across AZs.

**Runbook 3 — noisy-neighbor tenant starving a cluster.**
- *Symptom:* `scheduler_pending_pods` spikes for multiple tenants; Karpenter scaling aggressively; one namespace at 100% quota.
- *Diagnosis:* Sort namespaces by `kube_resourcequota{type="used"}`; find the tenant whose Job/Deployment exploded.
- *Mitigation:* The quota already caps them (post-§4.2 fix); if quota was mis-sized, temporarily lower it and notify the team. Cordon Karpenter consolidation if it's thrashing.
- *Resolution:* Right-size the tenant's quota; if they legitimately need more, place them on a dedicated node pool or their own cluster.

**Runbook 4 — cross-cluster service unreachable.**
- *Symptom:* Calls to a `global` service fail when local endpoints are down; failover not happening.
- *Diagnosis:* `cilium clustermesh status`; check Hubble flows; verify the remote cluster's endpoints are healthy and ClusterMesh tunnels (over Transit Gateway) are up.
- *Mitigation:* Restart Cilium operator on the affected cluster; verify CA trust between clusters; check Transit Gateway route tables.
- *Resolution:* Add ClusterMesh tunnel health to alerting; details in [cross_cutting/multi_cluster_networking.md](cross_cutting/multi_cluster_networking.md).

---

## 9. Common Pitfalls & War Stories

**1. The fail-closed webhook that bricked a cluster.** A platform team shipped a Kyverno upgrade that crash-looped on a new CRD. Because every policy had `failurePolicy: Fail`, the dead webhook rejected *all* writes — including the kubelet's pod status updates. The cluster went read-only for **47 minutes**, blocking deploys for **31 tenants** and one customer-facing rollback. Impact: an estimated **$180k** in delayed-revenue from a stuck payments fix. Lesson: exclude `kube-system`, run the webhook HA with a PDB, and rehearse the `failurePolicy: Ignore` break-glass. See [cross_cutting/kubernetes_production_hardening.md](cross_cutting/kubernetes_production_hardening.md).

**2. The missing ResourceQuota (the §4.2 incident, from the field).** Before quotas were enforced, a data team shipped a CronJob with a bug that spawned a new Job every minute without cleanup. Over a weekend it accumulated **18,000 pods**, drove a 300-node cluster to **640 nodes** via the autoscaler, and ran up **~$41,000** in surprise EC2 before Monday. Two co-tenant teams saw pods `Pending` and breached SLO. Lesson: `count/jobs.batch` and `ResourceQuota` are not optional; ship them in the tenant template.

**3. aws-vpc-cni IP exhaustion.** An early cluster used the AWS VPC CNI; at high pod density the ENI/IP limit per node (e.g., ~58 IPs on m5.2xlarge) was exhausted, leaving nodes with free CPU/memory but **no IPs** — pods stuck `ContainerCreating`. ~**1,100 pods** across **40 nodes** were unschedulable for **2 hours** until prefix-delegation was enabled. This drove the migration to Cilium (Decision 3).

**4. etcd defrag without leader awareness.** An engineer ran `etcdctl defrag` against all three etcd members in parallel during a maintenance window. Defrag blocks the member; doing all three at once **lost quorum** and the control plane was down **9 minutes** across one prod cluster. Lesson: defrag one member at a time, never the leader last. Now automated and serialized.

**5. Argo CD self-DDoS at fleet scale.** With 50 clusters and app-of-apps, a single root-app change triggered a simultaneous reconcile across all clusters and **~2,000 Applications**, saturating the Argo CD repo-server and Git API rate limits; syncs queued for **35 minutes**, delaying an urgent security patch. Lesson: shard Argo CD by region, tune `--repo-server-timeout-seconds` and sharding, and stagger ApplicationSet rollouts.

**6. Cross-AZ data transfer bill surprise.** A service mesh was deployed without topology-aware routing; **100% of a 600k-RPS** internal call pattern bounced across AZs, generating a **~$120k/month** data-transfer line nobody attributed until FinOps flagged it. Enabling Cilium `affinity: local` + topology hints cut it ~70% to ~$36k/month. Lesson: cross-AZ/cross-cluster traffic is a first-class cost SLI; see [cross_cutting/multi_cluster_networking.md](cross_cutting/multi_cluster_networking.md).

---

## 10. Capacity Planning

### When to shard a cluster into a new one

A cluster's practical ceiling is set by the *first* of these to strain, not the upstream 5,000-node number:

```
Constraint signals (any breach → plan a new cluster / move tenants):
  1. etcd db size:            > 6 GiB live (75% of 8 GiB quota)
  2. etcd write latency:      p99 fsync > 25 ms (disk-bound) or apply p99 > 100 ms
  3. apiserver request p99:   > 1 s sustained (watch fan-out / list pressure)
  4. node count:              > 1,000 nodes (Cilium/kube-proxy + endpoint churn)
  5. total pods:              > 30,000 (EndpointSlice + watch cache pressure)
  6. blast-radius policy:     no single cluster runs > 1/50 of fleet workloads
```

**Scaling formula (control-plane-bound shard point):**

```
max_nodes_per_cluster = min(
    node_hard_limit,                                  # 1,000 (our policy)
    etcd_quota_bytes / avg_bytes_per_node_footprint,  # 8 GiB / ~2 MiB ≈ 4,000
    apiserver_qps_capacity / writes_per_node_per_sec  # control-plane sizing
)
```

In practice node_hard_limit (1,000) binds first, so we target **300 nodes/cluster steady, 1,000 hard**, and shard at ~700 to leave headroom for upgrades (surge adds 10–20%).

### Worked example

A new region (eu-central-1) needs capacity for 40 tenants, ~1,600 workloads, ~6,400 pods.

```
Pods needed:            6,400
Pods per m5.2xlarge:    ~40  (8 vCPU / 32 GiB, avg pod 200m / 512Mi, with system overhead)
Worker nodes:           6,400 / 40 = 160 nodes

Add headroom (surge + burst): ×1.25 → 200 nodes
Blast-radius check:     200 < 700 shard threshold → ONE cluster suffices

System node pool (tainted): 3 × m5.2xlarge (addons: Cilium, Kyverno, Karpenter, Prom agent)

Cost:
  Control plane:        $73 / month
  Workers (200 × m5.2xlarge):
      on-demand:        200 × $0.384 × 730 = $56,064
      60% SP/Spot blend:                    ~$28,000
  System (3 × m5.2xlarge on-demand):        $841
  EBS (203 × 100 GiB gp3):                  $1,624
  ----------------------------------------------------
  Cluster total:        ~$30,600 / month

If tenant count doubles to 80 → ~12,800 pods → ~400 nodes (still < 700) → still one cluster,
~$58k/month. At ~28 tenants beyond that (≈700 nodes) → provision a second eu-central cluster
and split tenants by tier (prod vs dev/staging) for blast-radius separation.
```

The decisive lever remains node bin-packing: improving average pods/node from 40 to 50 (better requests, Karpenter consolidation) drops the 200-node cluster to 160 nodes — a ~$5.6k/month/cluster saving, ~$280k/year across 50 clusters.

---

## 11. Interview Discussion Points

**Why not give every team its own cluster — isn't that the cleanest isolation?**
It is the cleanest isolation and the worst operability. 200 clusters means 200 control planes to patch, 200 CNI upgrades, 200 etcd databases, and 200× the surface for a 12-person team — the upgrade treadmill alone (a minor every ~4 months) becomes a full-time job per handful of clusters. Namespaces give ~90% of the isolation (RBAC + NetworkPolicy + ResourceQuota + PSA) at ~1% of the operational cost, and you reserve real clusters for the few regulated tenants where a compliance boundary actually requires separate etcd. The deciding factor is team size, not isolation theory.

**A tenant complains a neighbor is starving them. What design elements prevent and diagnose this?**
Prevention is `ResourceQuota` (caps total CPU/memory/pods/Jobs per namespace) plus `LimitRange` (forces requests so QoS is never accidentally BestEffort) plus PriorityClasses so platform-critical pods preempt correctly. Diagnosis is per-tenant saturation metrics labeled by `team`/`cost-center` and `scheduler_pending_pods`. The classic failure (§9 #2) is a missing quota letting one Job balloon the cluster; the fix is shipping quota + `count/jobs.batch` in the tenant template so it is impossible to onboard without it. Quota is a guardrail you render, never a thing tenants are trusted to add.

**How do you upgrade 50 clusters without a fleet-wide outage?**
Treat clusters as canaries and gate on error budget. A bot bumps the version field in one dev cluster's manifest; CI runs `kubent` (deprecated-API scan), re-validates all policies against the new API, and a conformance smoke deploy; the canary bakes 48h while you watch control-plane error rate, scheduler latency, and webhook p99; an error-budget fast-burn gate auto-pauses the rollout. Then you stage dev → staging → prod, one region/cluster per day, with node pools surge-upgraded respecting PDBs. The key is that no upgrade is a fleet-wide simultaneous event — blast radius stays at one cluster.

**Namespace vs vcluster — when do you actually reach for vcluster?**
When namespaces leak the abstraction. Namespaces share cluster-scoped objects: CRDs, ClusterRoles, and a tenant can `list` namespace metadata they shouldn't. If a tenant genuinely needs cluster-admin semantics (installs their own operators/CRDs), or runs untrusted code, or must not even *see* other tenants' API objects, you give them a vcluster — their own API server as pods, with a syncer translating workloads to the host. The cost is ~150–300 MiB and a few ms of latency per tenant, so you keep it to the ~10% that need it; everyone else stays on namespaces.

**Why Cilium over Calico or the AWS VPC CNI?**
At 12,000 pods/cluster, iptables-based dataplanes degrade because rule evaluation is roughly O(n) in services/endpoints; Cilium's eBPF dataplane stays flat. Cilium also gives ClusterMesh for cross-cluster global services (the multi-region requirement), Hubble for per-flow visibility the security team needs, and L7-aware policy. The AWS VPC CNI specifically hits ENI/IP-per-node limits at high pod density (§9 #3), leaving nodes with spare CPU but no IPs. The tradeoff is Cilium needs newer kernels and more operational expertise.

**The management cluster is a single point of failure for provisioning — how do you mitigate?**
Make it tier-0: HA across 3 AZs, its own SLO, backed up, and recoverable from Git (it is itself defined as CAPI/Crossplane manifests, so you can rebuild it). Critically, decouple the *data plane* from the *control of the platform*: if the management cluster dies, already-running workload clusters keep serving traffic and Argo CD on each cluster keeps reconciling its last-known state — you only lose the ability to *provision new* clusters/tenants until it is restored. You never put tenant workloads on it, so its blast radius is bounded to platform operations.

**How does cost attribution actually work, and why is 95% accuracy the bar?**
Every namespace carries mandatory `team` and `cost-center` labels (enforced by Kyverno admission — you cannot create a namespace without them), and a cost tool (Kubecost/OpenCost or cloud cost allocation tags) joins namespace resource usage to those labels, allocating node cost by requests/usage and adding storage + a share of cross-AZ egress. You never hit 100% because shared system overhead (the management plane, monitoring, idle headroom) isn't owned by one tenant — that goes to a platform cost center. 95% means almost all *variable* spend is attributable, which is enough to drive team behavior (right-sizing requests) without litigating pennies.

**A Kyverno webhook with failurePolicy: Fail can take down a whole cluster. So why use Fail at all?**
Because `failurePolicy: Ignore` means a policy bypass is one webhook crash away — an attacker (or a bug) can ship a privileged pod the moment the webhook blinks, which defeats the point of enforcement. So security-critical policies run `Fail` but with compensating controls: the webhook is HA (3 replicas, PDB, spread across AZs), excludes `kube-system` via `namespaceSelector` so it can never block core control-plane writes, has a tested break-glass to flip to `Ignore`, and is monitored on p99 latency as a first-class SLI. It's a deliberate availability-vs-enforcement tradeoff, made safe by hardening rather than by weakening the policy. See [cross_cutting/kubernetes_production_hardening.md](cross_cutting/kubernetes_production_hardening.md).

**What's the etcd ceiling and how does it shape cluster sizing?**
etcd's default backend quota is 8 GiB, and beyond ~75% it slows and eventually goes read-only (`NOSPACE` alarm). But raw bytes rarely bind first — steady-state for a 12,000-pod cluster is only ~220 MiB. What actually strains etcd is write throughput and un-compacted MVCC history: event floods, leaking controllers, or `kubectl apply` loops spike fsync latency and apply p99. So you size clusters by control-plane signals (apiserver p99 < 1 s, etcd fsync p99 < 25 ms, node count < 1,000) and shard before any of those breach, rather than chasing the theoretical 5,000-node limit.

**How do you do self-service onboarding in under 5 minutes without a platform engineer in the loop?**
The onboarding *is* a Git merge. A tenant opens a PR adding a `Tenant` custom resource; CI validates it against schema and org policy (Kyverno CLI/conftest); on merge, Argo CD applies the CR and a Tenant controller expands it into namespace + quota + limits + default-deny NetworkPolicy + RBAC + dashboards via an ApplicationSet onto the placement-selected cluster. No human approves the K8s plumbing — the policy engine *is* the reviewer. Backstage fronts this with a software template so the developer fills a form rather than writing YAML, and gets a "ready" tile in p95 < 5 min.

**How do you handle cross-cluster service discovery and regional failover?**
We use Cilium ClusterMesh to expose `global` services: the same service name exists in multiple clusters, annotated `service.cilium.io/global: "true"` with `affinity: local` so traffic prefers same-cluster endpoints and only spills cross-region when local endpoints are unhealthy. That gives automatic failover (a dead local backend routes to the remote cluster) and keeps ~70% of traffic local, which is also the cross-AZ cost lever. The alternative for heterogeneous CNIs is Submariner. The operational risk is silent tunnel failure, so ClusterMesh tunnel health is an alert, per [cross_cutting/multi_cluster_networking.md](cross_cutting/multi_cluster_networking.md).

**Cluster API vs Terraform for cluster lifecycle — what's the real difference?**
Terraform is apply-once imperative-ish state; Cluster API is a continuous reconciliation loop where "a cluster" is a Kubernetes object the management cluster actively keeps converged. CAPI self-heals drift (a manually deleted node pool comes back), makes upgrades a declarative field bump, and makes the fleet a queryable set of CRDs you can policy-check and GitOps. Terraform tends toward drift, manual `apply` runs, and per-cluster state-file sprawl. The tradeoff is CAPI/CAPA is younger and the management cluster becomes tier-0; many teams use Crossplane alongside it precisely to also pull non-K8s infra (VPC, IAM, RDS) into the same reconciliation model.

**How do you keep fleet observability from melting under cardinality?**
Each cluster runs Prometheus in agent mode and `remote_write`s to a central Mimir/Thanos, but the cardinality math is brutal — 50 clusters × ~12,000 pods × dozens of series each is easily 100M+ active series, which blows up memory and query cost. So you drop high-cardinality labels at the agent (pod UID, ephemeral suffixes, container IDs) via `metric_relabel_configs`, keep only the SLI histograms you actually alert on, and use recording rules for dashboards. Local scrape also survives network partitions, and global query gives one pane of glass. The discipline is covered in [cross_cutting/prometheus_cardinality_and_scale.md](cross_cutting/prometheus_cardinality_and_scale.md), and the SLO/error-budget gating in [cross_cutting/slo_error_budget_math.md](cross_cutting/slo_error_budget_math.md).
