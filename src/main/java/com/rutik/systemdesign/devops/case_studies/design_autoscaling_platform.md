# Design a Cost-Aware Autoscaling Platform

> A cost-aware autoscaling platform is a thermostat with a budget: it heats the room fast when guests arrive, but it never burns money keeping empty rooms warm.

**Key insight**: Autoscaling is two coupled control loops at different time constants — pods react in ~15s, nodes react in ~40s–5min — and 90% of cloud waste lives in the gap where you provisioned capacity faster than you reclaimed it.

---

## Intuition

> Think of a ride-share fleet: drivers (pods) come online in seconds, but new cars (nodes) take minutes to arrive at the depot. You over-stock cars at rush hour and pay to park them empty at 3am unless something actively sends them home.

**Key insight**: The expensive failure mode is not under-provisioning (users see latency for 90 seconds) — it is the *silent* over-provisioning that runs at 35% node utilization for weeks and shows up only as a 7-figure annual cloud bill.

**Mental model.** There are two nested loops:

```
Inner loop (fast, 15s):   metric -> HPA/KEDA -> desired replicas -> pending pods
Outer loop (slow, 40s+):  pending pods -> Karpenter/CAS -> new nodes -> pods scheduled
Reclaim loop (slow, mins): low util -> consolidation -> drain (PDB-aware) -> node deleted
```

The inner loop decides *how many pods*. The outer loop decides *how much hardware*. The reclaim loop is the one everyone forgets, and it is where the cost savings actually come from. A platform that only scales up is a cost generator; a platform that scales down aggressively but ignores PodDisruptionBudgets is an outage generator. The job is to do both safely.

**Why this system exists.** We run 4000 microservices on Kubernetes. Traffic is bursty — a marketing push or a regional morning peak can drive 10x in 60 seconds. If every team statically provisions for peak, the fleet sits at ~30% average utilization and we pay for 3x the hardware we use. A central autoscaling platform lets teams declare *intent* (latency target, queue depth target, min/max replicas) and lets the platform translate that into the cheapest correct capacity — spot where safe, on-demand where not, scaled to zero when idle.

---

## 1. Requirements Clarification

### Functional Requirements

- **FR1 — Pod-level horizontal scaling.** Scale Deployment/StatefulSet replicas on CPU, memory, and custom/external metrics (RPS, p99 latency, queue depth) across all 4000 services.
- **FR2 — Event-driven scaling.** Scale on external event sources (Kafka lag, SQS depth, RabbitMQ ready messages, cron) including **scale-to-zero** for idle async workers.
- **FR3 — Node-level scaling.** Automatically provision and de-provision nodes to fit pending pods; bin-pack tightly and consolidate underutilized nodes.
- **FR4 — Spot/on-demand mix.** Place interruption-tolerant workloads on spot, stateful/critical workloads on on-demand, with automatic fallback when spot is unavailable.
- **FR5 — Safe scale-down.** Respect PodDisruptionBudgets (PDBs), node `terminationGracePeriod`, and drain ordering during consolidation and spot reclaim.
- **FR6 — Vertical right-sizing.** Recommend/apply CPU and memory requests (VPA) so HPA targets and bin-packing are based on accurate requests.
- **FR7 — Predictive pre-warming.** For predictable daily peaks, scale ahead of demand to hide node-provision latency.
- **FR8 — Policy + cost guardrails.** Enforce per-tenant max replicas, max node count, and a hard cost ceiling that blocks runaway scale-out.

### Non-Functional Requirements

| NFR | Target |
|-----|--------|
| Reaction to 10x traffic spike | New capacity serving within **90 seconds** (p95) |
| HPA decision latency | Default sync loop **15s**; custom-metric path **<30s** end-to-end |
| Node provision latency | **<60s** p95 (Karpenter), vs 3–5 min legacy Cluster Autoscaler |
| Average node utilization | **>65%** CPU requests committed (vs ~30% static) |
| Cloud spend reduction | **>=40%** vs static peak-provisioning baseline |
| Spot interruption handling | Drain + reschedule within the **120s** AWS spot warning window |
| Scale-down safety | **Zero** PDB violations; no `minAvailable` breach |
| Availability of platform control plane | **99.95%** (autoscaler down must not take down workloads) |
| Scale-to-zero cold start | Async workers back to serving within **<10s** of first event |

The two NFRs that most shape the architecture: the **90s spike-reaction SLO** forces the node provisioner choice (Karpenter, not Cluster Autoscaler) and the warm-pool buffer, while the **>=40% spend-reduction** target forces the spot mix and aggressive consolidation. The **zero-PDB-violation** constraint is what makes scale-*down* the hard part — anyone can add capacity safely; reclaiming it without dropping requests is where the engineering lives. The **99.95% control-plane availability** target is deliberately decoupled from workload availability: the platform is designed to fail static, so an autoscaler outage freezes scaling rather than cascading into the 4000 services it manages.

### Out of Scope

- The microservices' own application logic, request routing, and service mesh (cross-reference [`cross_cutting/multi_cluster_networking.md`](cross_cutting/multi_cluster_networking.md)).
- Database autoscaling (read replicas, Aurora ACU) — handled by the data platform; autoscaling stateful databases has fundamentally different constraints (durability, replication lag) than stateless pods.
- Multi-region failover orchestration (separate DR design); this platform autoscales *within* each cluster independently.
- GPU fractional sharing / MIG partitioning (only whole-GPU node scaling is in scope here).
- Cost *attribution* and chargeback tooling (Kubecost/OpenCost) — consumed by this platform as a signal but owned by the FinOps function, see [`../cloud_cost_optimization_finops/README.md`](../cloud_cost_optimization_finops/README.md).

---

## 2. Scale Estimation

### Workload shape

- **4000 microservices**, average 6 replicas at baseline, peak 18 -> **24,000 pods baseline, ~72,000 peak**.
- Average pod request: **250m CPU / 512Mi**. Peak fleet demand: 72,000 × 0.25 = **18,000 vCPU**, 72,000 × 0.5Gi = **36,000 GiB**.
- Node type: **m6i.2xlarge** = 8 vCPU / 32 GiB, allocatable ~**7.3 vCPU / 29 GiB** after kubelet/system reservation.

### Node count math

```
Pods per node (CPU-bound):  7.3 vCPU / 0.25 = 29.2  -> 29 pods/node
Pods per node (mem-bound):  29 GiB / 0.5    = 58 pods/node
Binding constraint = CPU = 29 pods/node

Baseline nodes = 24,000 / 29 = 828 nodes
Peak nodes     = 72,000 / 29 = 2483 nodes
```

At **65% utilization target**, add headroom: peak ≈ 2483 / 0.65 ≈ **3820 nodes** if we hold 35% slack, but consolidation lets us run hot during steady state and burst slack only during spikes, so steady-state peak settles near **2600 nodes**.

These numbers assume average requests are *accurate* — the single biggest sensitivity. If the fleet over-requests CPU by 28% (a typical pre-VPA state), every node count above inflates by ~24% and the cost math gets that much worse. This is why VPA right-sizing (§4.5) is a prerequisite for the savings, not an optional extra: the autoscalers are only as efficient as the requests they reason about.

### Scaling throughput

- HPA sync period: **15s default**. Each loop can change replicas for all HPAs it watches. With `--horizontal-pod-autoscaler-sync-period=15s` and ~4000 HPAs, the controller comfortably evaluates all within one loop on a tuned control plane.
- 10x spike: 24,000 -> needs ~72,000 pods. HPA computes desired in one sync (15s). Scheduler emits pending pods immediately. **Karpenter provisions a 2xlarge in ~40s**; CAS would take **3–5 min** (ASG warm-up + scale-up + node-not-ready). Karpenter is the difference between hitting and missing the 90s SLO.
- Pods scheduled/min during burst: a single Karpenter `NodePool` can launch hundreds of nodes in parallel; binding ~29 pods/node × 200 nodes/min = **~5,800 pods/min** of new capacity.

### Cost math

| Item | On-demand | Spot (~) |
|------|-----------|----------|
| m6i.2xlarge / node-hr | **$0.384** | **$0.1152** (~70% off) |
| 2600 nodes, all on-demand, 730 hr/mo | 2600 × 0.384 × 730 = **$728,832/mo** | — |
| Static peak-provision (3× = 7800 nodes on-demand) | 7800 × 0.384 × 730 = **$2,186,496/mo** | — |

**Autoscaling savings vs static:** $2,186,496 -> $728,832 = **$1,457,664/mo saved (~67%)** just from elastic sizing.

**Spot mix savings:** put 70% of capacity (interruption-tolerant) on spot:

```
On-demand portion:  780 nodes × 0.384 × 730 = $218,649
Spot portion:      1820 nodes × 0.1152 × 730 = $153,073
Total mixed:                                   $371,722/mo
```

vs $728,832 all-on-demand -> **additional $357,110/mo (49%) saved** from spot.

**Spot interruption rate** ~**5%/hr** observed on m6i family in us-east-1 -> expect ~91 spot nodes reclaimed/hr at 1820 spot nodes; each must drain within the **120s** warning window. Combined elastic + spot beats the static baseline by **>83%**, comfortably clearing the 40% NFR.

### Scale-to-zero contribution

Of 4000 services, ~**1,100 are async workers** (event consumers, batch processors, cron jobs) idle 60–80% of the day. At 6 replicas each, that is **6,600 pods** that HPA would pin at a floor of 1 replica = 1,100 pods always-on. KEDA scale-to-zero eliminates them during idle windows:

```
Idle reclaim: 1,100 pods × 0.25 vCPU = 275 vCPU freed
            = ceil(275 / 7.3) = 38 nodes not provisioned during idle
            = 38 × $0.1152 (spot) × 730 × 0.7 (avg idle fraction) = $2,236/mo
```

Modest in raw dollars but it also removes 1,100 idle pods from the scheduler's accounting, improving bin-packing density on the remaining nodes.

### Control-plane load

4000 HPAs polling every 15s = **267 metric reads/sec** from the metrics API. prometheus-adapter must serve these without becoming the bottleneck — at p99 < 200ms per query, a single adapter replica handles ~5 concurrent queries comfortably, so we run **4 adapter replicas** behind the aggregation layer. Karpenter watches all Pending pods via a single informer; at peak burst the pending queue can reach **~48,000 pods** (the 24k->72k jump), which Karpenter batches into provisioning decisions in **<5s** of solve time per batch.

---

## 3. High-Level Architecture

```
                         ┌─────────────────────────────────────────────┐
                         │              METRICS PLANE                   │
                         │  Prometheus  ── Kafka/SQS lag exporters      │
                         │     │              │                         │
                         │  prometheus-adapter (custom.metrics.k8s.io)  │
                         │  KEDA metrics-adapter (external.metrics...)  │
                         └───────┬──────────────────────┬──────────────┘
                                 │ custom/external metrics
                  ┌──────────────▼───────────┐   ┌───────▼────────────┐
                  │   HPA controller (15s)    │   │   KEDA Operator    │
                  │  desiredReplicas =        │   │  scalers + ScaledObj│
                  │  ceil(cur × m / target)   │   │  scale 0..N, cron   │
                  └──────────────┬───────────┘   └───────┬────────────┘
                                 │ patch .spec.replicas   │ (manages an HPA)
                  ┌──────────────▼────────────────────────▼─────────────┐
                  │           Deployments / StatefulSets                  │
                  │           (replica count changes)                     │
                  └──────────────────────┬───────────────────────────────┘
                                         │ creates Pods
                  ┌──────────────────────▼───────────────────────────────┐
                  │   kube-scheduler  -> schedulable? -> Pending pods     │
                  └──────────────────────┬───────────────────────────────┘
                          pending pods    │   (Unschedulable: Insufficient cpu)
                  ┌──────────────────────▼───────────────────────────────┐
                  │   NODE AUTOSCALER:  Karpenter  (or Cluster Autoscaler)│
                  │   - watches Pending pods                              │
                  │   - solves bin-pack -> picks cheapest instance types  │
                  │   - launches EC2 (spot first, on-demand fallback)     │
                  │   - consolidation loop: pack & delete underused nodes │
                  │   - PDB-aware drain on scale-down / spot reclaim       │
                  └──────────────────────┬───────────────────────────────┘
                                         │ provisions / terminates
                  ┌──────────────────────▼───────────────────────────────┐
                  │   EC2 NODES  ┌──────────────┐   ┌──────────────────┐  │
                  │              │ Spot pool 70%│   │ On-demand 30%    │  │
                  │              │ (stateless)  │   │ (stateful/crit)  │  │
                  │              └──────────────┘   └──────────────────┘  │
                  └───────────────────────────────────────────────────────┘
                            ▲                              │
                            │ spot interruption (120s)     │ node events
                  ┌─────────┴──────────────────────────────▼─────────────┐
                  │   Node Termination Handler / Karpenter interruption   │
                  │   queue (SQS) -> cordon+drain -> reschedule           │
                  └───────────────────────────────────────────────────────┘
```

### Component inventory

| Component | Role |
|-----------|------|
| Prometheus + prometheus-adapter | Expose CPU/mem and custom app metrics via `custom.metrics.k8s.io` |
| KEDA operator + metrics adapter | Event-driven scaling, scale-to-zero, external metrics |
| HPA controller (kube-controller-manager) | Reactive replica math on metrics, 15s loop |
| VPA recommender | Right-size pod requests so HPA + bin-pack are accurate |
| Karpenter (or Cluster Autoscaler) | Node provisioning, instance selection, consolidation |
| AWS Node Termination Handler / Karpenter interruption queue | Spot reclaim drain within 120s |
| Policy controller (Kyverno/OPA Gatekeeper) | Enforce min/max replicas, cost ceilings, PDB presence |
| Thanos / Mimir (federated metrics) | Global cross-cluster utilization and cost rollup |

The boundary between the planes matters: the **metrics plane** is the source of truth for scaling decisions and is the most common failure point (stale or missing metrics cause both under- and over-scaling), so it runs with redundancy and staleness alerting. The **scaling controllers** are stateless and fail-static. The **node layer** is where money is spent and where drains can cause outages, so it carries the disruption budgets and PDB enforcement.

### Data flow narrative

1. App pushes RPS/latency to Prometheus; Kafka lag exporter publishes consumer lag.
2. prometheus-adapter and KEDA expose these as Kubernetes metrics APIs.
3. HPA reads the metric every 15s, computes `desiredReplicas`, patches the Deployment.
4. New pods are created; kube-scheduler tries to bind them. Unschedulable pods become **Pending**.
5. Karpenter watches Pending pods, runs a bin-packing solve, picks the cheapest instance type satisfying the pods' requests/affinities, and launches EC2 (spot first).
6. Node joins (~40s), scheduler binds pods.
7. On low utilization, Karpenter's **consolidation** loop replaces/empties nodes — but only after a PDB-safe drain.
8. On spot interruption, the interruption queue triggers a cordon+drain inside the 120s window, and the pods reschedule onto remaining/new capacity.

### Worked scale-up timeline (10x spike)

```
t=0s    traffic 10x; per-pod RPS jumps 50 -> 500
t=2s    Prometheus scrape captures the rate increase
t=15s   HPA sync fires: desired = ceil(24000 × 500/50) ... capped per-HPA
        Deployments patched; ~48,000 new pods created
t=16s   scheduler binds what fits; ~46,000 pods go Pending (no capacity)
t=18s   Karpenter batch-solves: needs ~1,600 nodes, picks spot-first c6i/m6i
t=20s   EC2 RunInstances issued (parallel, hundreds at once)
t=58s   first wave of nodes Ready (~40s provision)
t=62s   pods bind; readiness probes start
t=78s   first new pods Ready and serving
t=90s   p95 of new capacity serving  <-- SLO met
```

The tall pole is unambiguous: **steps t=20->58s (node provision) consume ~40 of the 90 seconds.** This is exactly why Karpenter (40s) beats Cluster Autoscaler (3–5 min) for the SLO, and why a warm-pool of pause-pods can shave the tail to near-zero for the most spike-prone services.

### Multi-cluster topology

```
   Region us-east-1                      Region eu-west-1
   ┌───────────────────────┐             ┌───────────────────────┐
   │ cluster-use1 (prod)   │             │ cluster-euw1 (prod)   │
   │  HPA/KEDA + Karpenter │             │  HPA/KEDA + Karpenter │
   │  own NodePools, quota │             │  own NodePools, quota │
   └──────────┬────────────┘             └──────────┬────────────┘
              │  federated metrics (Thanos/Mimir)   │
              └──────────────┬──────────────────────┘
                  global capacity dashboard + cost rollup
```

Each cluster autoscales independently (no cross-cluster pod scheduling), but a federated metrics layer gives one global view of utilization and spend. Cross-region traffic shifting and cluster failover are out of scope here — see [`cross_cutting/multi_cluster_networking.md`](cross_cutting/multi_cluster_networking.md).

---

## 4. Component Deep Dives

The platform has six load-bearing components. Four are scaling controllers (HPA, KEDA, VPA, Karpenter) and two are the safety layers (PDB-aware drain, predictive pre-warm) that keep aggressive scaling from becoming an outage. The deep dives below pair each with real config and the failure mode it must avoid — the consistent theme is that *adding* capacity is the easy half and *reclaiming* it safely is where every production incident originates.

### 4.1 HPA with Custom & External Metrics

```
   app /metrics ──> Prometheus ──> prometheus-adapter
                                        │ custom.metrics.k8s.io/v1beta1
                                        ▼
   HPA: target http_requests_per_second = 50  ── reads cur=80
        desired = ceil( replicas × 80/50 ) = ceil( 10 × 1.6 ) = 16
```

The default CPU HPA is too coarse for latency-sensitive HTTP services. We scale on **requests-per-second per pod**, exposed through prometheus-adapter.

```yaml
# prometheus-adapter rule: expose per-pod RPS
rules:
  - seriesQuery: 'http_requests_total{namespace!="",pod!=""}'
    resources: { overrides: { namespace: {resource: namespace}, pod: {resource: pod} } }
    name: { matches: "http_requests_total", as: "http_requests_per_second" }
    metricsQuery: 'sum(rate(http_requests_total{<<.LabelMatchers>>}[2m])) by (<<.GroupBy>>)'
```

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: checkout-api
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: checkout-api }
  minReplicas: 6
  maxReplicas: 200
  metrics:
    - type: Pods
      pods:
        metric: { name: http_requests_per_second }
        target: { type: AverageValue, averageValue: "50" }   # 50 RPS/pod
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0            # react instantly on spikes
      policies:
        - { type: Percent, value: 100, periodSeconds: 15 }   # at most 2x/15s
        - { type: Pods,    value: 20,  periodSeconds: 15 }
      selectPolicy: Max
    scaleDown:
      stabilizationWindowSeconds: 300          # wait 5 min before shrinking
      policies:
        - { type: Percent, value: 10, periodSeconds: 60 }    # shrink slowly
```

#### BROKEN -> FIX: HPA thrashing

**Broken.** A team set a tight target and zero stabilization on *both* directions:

```yaml
metrics:
  - type: Resource
    resource: { name: cpu, target: { type: Utilization, averageUtilization: 50 } }
behavior:
  scaleUp:   { stabilizationWindowSeconds: 0 }
  scaleDown: { stabilizationWindowSeconds: 0 }   # <-- BUG: shrinks the instant CPU dips
```

CPU oscillated around 50% as each scale-up immediately dropped per-pod load below target, triggering an instant scale-down, which spiked load again. The Deployment flapped **8–24 replicas every ~30s for 6 hours**, churning pods, evicting from nodes, and triggering Karpenter to launch and kill nodes repeatedly — **$4,100 of wasted node-hours in one night** plus elevated p99 from constant cold starts.

**Fix.** Add a scale-down stabilization window (HPA keeps the *max* recommendation over the window) and rate-limit shrink:

```yaml
behavior:
  scaleUp:
    stabilizationWindowSeconds: 0
    policies: [{ type: Percent, value: 100, periodSeconds: 15 }]
  scaleDown:
    stabilizationWindowSeconds: 300        # FIX: 5-min cool-down, react up fast / down slow
    policies: [{ type: Percent, value: 10, periodSeconds: 60 }]
```

Asymmetric behavior — **fast up, slow down** — is the single most important HPA tuning rule. Flapping stopped; replicas settled and node churn went to zero.

---

### 4.2 KEDA Scalers + Scale-to-Zero

```
   Kafka topic lag = 12,000 msgs
          │ KEDA Kafka scaler: lagThreshold=500
          ▼
   desiredReplicas = ceil(12000 / 500) = 24   (capped at maxReplicaCount)
   ... lag drains to 0 ...
   idle > cooldownPeriod(300s) ──> scale to 0 pods  (no cost)
```

KEDA scales on the *event backlog* directly, and uniquely can scale to **zero** — HPA's floor is 1. For 4000 services, hundreds are async workers idle most of the day; scale-to-zero alone reclaims significant cost.

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: order-events-consumer
spec:
  scaleTargetRef: { name: order-events-consumer }
  minReplicaCount: 0           # scale to zero when idle
  maxReplicaCount: 50
  cooldownPeriod: 300          # wait 5 min of no events before scaling to 0
  pollingInterval: 15
  advanced:
    horizontalPodAutoscalerConfig:
      behavior:
        scaleDown: { stabilizationWindowSeconds: 120 }
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: kafka:9092
        consumerGroup: order-processors
        topic: orders
        lagThreshold: "500"
        activationLagThreshold: "10"   # wake from 0 only when lag > 10
```

`activationLagThreshold` separates the *0->1* activation decision from the *1->N* scaling decision, preventing flapping at the zero boundary. KEDA creates a managed HPA for the 1..N range and handles 0..1 itself. The cold-start cost is the pod start + first poll: with a pre-pulled image and `pollingInterval: 15`, first message is processed in **<10s**, meeting the NFR. For latency-critical zero-to-one, KEDA's HTTP add-on holds the request at a proxy while the first pod starts.

#### BROKEN -> FIX: scale-to-zero flapping at the boundary

**Broken.** A worker set `minReplicaCount: 0` but omitted `activationLagThreshold` and used a short cooldown:

```yaml
minReplicaCount: 0
cooldownPeriod: 30           # BUG: 30s, and no activationLagThreshold
triggers:
  - type: kafka
    metadata: { lagThreshold: "500" }   # activation defaults to same as lagThreshold
```

A trickle of 5–15 messages/min arrived all day. Each message spiked lag above the activation default, KEDA scaled 0->1, the pod drained the handful of messages in 4s, lag hit 0, and 30s later it scaled back to 0 — then the next message arrived and repeated. The worker **cold-started ~340 times/hour**, each pull-and-start costing ~6s of latency on those messages and churning the scheduler. Downstream consumers saw p99 message-processing latency of **8.2s** instead of the expected sub-second.

**Fix.** Set `activationLagThreshold` low (wake on real backlog, not a single message) and lengthen the cooldown so a steady trickle keeps one warm replica:

```yaml
minReplicaCount: 0
cooldownPeriod: 300            # FIX: stay warm 5 min after last work
triggers:
  - type: kafka
    metadata:
      lagThreshold: "500"
      activationLagThreshold: "50"   # FIX: only wake from 0 when lag > 50
```

Now a low-but-steady trickle keeps a single replica warm (cooldown never elapses), and the service only returns to zero during genuinely idle stretches. Cold starts dropped from ~340/hr to ~3/hr and p99 returned to sub-second.

---

### 4.3 Karpenter Provisioner & Consolidation

```
   Pending pods (need 4 vCPU, on spot, zone us-east-1a)
          │
          ▼  Karpenter solves: cheapest instance covering pods + constraints
   candidates: c6i.xlarge $0.17 | m6i.xlarge $0.192 | r6i.xlarge $0.252
          ▼  picks c6i.xlarge spot ($0.051)  -> launches in ~40s
   later: node at 28% util  -> consolidation: replace with smaller / empty & delete
```

Karpenter does not use node groups — it looks at pending pods and provisions *right-sized* instances directly, choosing across a wide instance family for the cheapest fit.

```yaml
apiVersion: karpenter.sh/v1
kind: NodePool
metadata: { name: general-spot }
spec:
  template:
    spec:
      requirements:
        - { key: karpenter.sh/capacity-type, operator: In, values: ["spot","on-demand"] }
        - { key: node.kubernetes.io/instance-type, operator: In,
            values: ["c6i.xlarge","c6i.2xlarge","m6i.xlarge","m6i.2xlarge"] }
        - { key: kubernetes.io/arch, operator: In, values: ["amd64"] }
      nodeClassRef: { name: default, group: karpenter.k8s.aws, kind: EC2NodeClass }
      expireAfter: 168h          # recycle nodes weekly for patching
  limits: { cpu: "20000" }       # hard cap = cost guardrail
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 60s        # wait 60s before consolidating
    budgets:
      - nodes: "10%"             # disrupt at most 10% of nodes at once
```

Consolidation is where cost savings land: Karpenter continuously asks "can these pods fit on fewer/cheaper nodes?" and replaces nodes when yes. `budgets: 10%` caps blast radius so consolidation can't drain the fleet at once.

#### BROKEN -> FIX: consolidation into an outage

**Broken.** A `disruption` config with no budget and a workload whose PDB was *missing*:

```yaml
disruption:
  consolidationPolicy: WhenEmptyOrUnderutilized
  consolidateAfter: 0s          # BUG: immediate, no budget
# ...and the payments Deployment had NO PodDisruptionBudget
```

Karpenter saw 40 underutilized nodes after a traffic dip and consolidated **all of them in ~2 minutes**. With no PDB, all 6 payments replicas were evicted simultaneously while their replacements were still pulling images — **payments hard-down for 3 minutes 40 seconds, ~31,000 failed checkout requests, ~$58,000 in lost GMV**.

**Fix.** (a) require a PDB on every workload via policy, (b) set a disruption budget:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata: { name: payments-pdb }
spec:
  minAvailable: 80%             # never evict below 80% of replicas
  selector: { matchLabels: { app: payments } }
---
# Karpenter NodePool
disruption:
  consolidationPolicy: WhenEmptyOrUnderutilized
  consolidateAfter: 60s
  budgets:
    - nodes: "10%"
    - { nodes: "0", schedule: "0 13 * * *", duration: 2h }  # freeze during daily peak
```

Karpenter now honors the PDB during drain (it will *not* evict if doing so breaches `minAvailable`) and disrupts at most 10% of nodes at a time, with a hard freeze during the daily peak window.

---

### 4.4 PDB-Aware Scale-Down & Spot Reclaim

```
   spot interruption notice (SQS) ── t=0
        │ Karpenter cordons node, taints it
        ▼ t=0..115s  evict pods respecting PDB (minAvailable)
        │   - pod re-scheduled, new pod Ready BEFORE old terminates
        ▼ t=120s     EC2 reclaims instance
```

Spot nodes get a **120s** warning. The drain must respect PDBs *and* finish inside 120s. The Go control logic that ties it together:

```go
// Simplified PDB-aware eviction loop run on node drain / spot reclaim.
func drainNode(ctx context.Context, node *v1.Node, deadline time.Time) error {
    pods := podsOnNode(node)
    // Order: lowest-priority + best-effort first; system/critical last.
    sort.Slice(pods, func(i, j int) bool { return priority(pods[i]) < priority(pods[j]) })

    for _, p := range pods {
        if isMirrorOrDaemonSet(p) {
            continue // DaemonSet pods die with the node; don't evict
        }
        // Eviction API respects PodDisruptionBudgets server-side.
        err := evictWithRetry(ctx, p, deadline)
        if errors.Is(err, ErrPDBBlocked) {
            // PDB would be violated: wait for a replacement elsewhere to go Ready,
            // then retry. If deadline approaches, surge a new node first.
            if time.Until(deadline) < 30*time.Second {
                provisionReplacementNode(ctx, p) // pre-warm capacity for the blocked pod
            }
            time.Sleep(2 * time.Second)
            err = evictWithRetry(ctx, p, deadline)
        }
        if err != nil {
            return fmt.Errorf("evict %s: %w", p.Name, err)
        }
    }
    return nil
}
```

The key correctness property: the **Eviction API** (`POST .../pods/{name}/eviction`), not raw `DELETE`, enforces the PDB server-side. Calling delete bypasses the PDB entirely — a common bug. For spot, set the workload's `terminationGracePeriodSeconds` to **<= 90s** so the graceful shutdown plus reschedule fits inside the 120s window with margin. See [`cross_cutting/kubernetes_production_hardening.md`](cross_cutting/kubernetes_production_hardening.md) for PDB + graceful-shutdown hardening.

---

### 4.5 VPA Right-Sizing for Accurate Bin-Packing

```
   90 days of usage ── VPA recommender ── p95 CPU=180m, p95 mem=420Mi
        │  current request 250m/512Mi  -> over-requested by 28% CPU
        ▼  apply recommendation -> request 200m/450Mi
   bin-pack density: 7.3/0.25 = 29 pods  ->  7.3/0.20 = 36 pods/node (+24%)
```

HPA and Karpenter both reason about *requests*, not actual usage. If requests are inflated, every node is under-packed and the whole fleet is over-provisioned regardless of how good the autoscalers are. VPA closes this gap by recommending requests from observed usage.

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata: { name: checkout-api-vpa }
spec:
  targetRef: { apiVersion: apps/v1, kind: Deployment, name: checkout-api }
  updatePolicy:
    updateMode: "Off"          # recommendation-only; never auto-evict (HPA owns live scaling)
  resourcePolicy:
    containerPolicies:
      - containerName: "*"
        controlledResources: ["cpu", "memory"]
        minAllowed: { cpu: 100m, memory: 128Mi }
        maxAllowed: { cpu: 2,    memory: 4Gi }
```

We run VPA in `updateMode: "Off"` so it *recommends* but never evicts — eviction would collide with HPA. A controller reads `status.recommendation` and applies it at the next deploy. Across 4000 services, correcting a fleet-wide 28% CPU over-request directly translates to **~24% fewer nodes** for the same workload — the single largest packing lever, larger than any consolidation tuning. The discipline: requests must reflect reality, or both HPA's per-pod target math and Karpenter's bin-pack solve operate on fiction.

---

### 4.6 Predictive Pre-Warming for Seasonal Peaks

```
   forecast: daily peak at 09:00, 4.2x baseline, ramp over 6 min
        │  reactive alone: node-provision tail adds 40s to first-pod latency
        ▼  KEDA cron trigger pre-scales at 08:54 -> nodes warm before traffic
   cold-start cliff eliminated for the predictable peak
```

For the ~600 services with strong daily seasonality, the 40s node-provision tail is a recurring, *predictable* latency hit. A KEDA `cron` trigger pre-scales ahead of the known ramp:

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata: { name: storefront-prewarm }
spec:
  scaleTargetRef: { name: storefront-api }
  minReplicaCount: 6
  maxReplicaCount: 200
  triggers:
    - type: cron                  # predictive: scale up BEFORE the 09:00 peak
      metadata:
        timezone: America/New_York
        start: "54 8 * * 1-5"     # 08:54 weekdays
        end:   "30 10 * * 1-5"    # ramp down after peak
        desiredReplicas: "120"
    - type: prometheus            # reactive floor: still scale on real RPS
      metadata:
        serverAddress: http://prometheus:9090
        query: sum(rate(http_requests_total{app="storefront-api"}[2m]))
        threshold: "50"
```

KEDA takes the **max** of all triggers, so the cron sets a pre-warm floor while the Prometheus trigger still handles unexpected magnitude. The cost of pre-warming wrong is bounded: ~114 extra pods for ~6 minutes before the peak ramps = **~$0.40/day/service** of idle compute — a rounding error against the latency and node-provision savings. The rule: predictive *raises the floor*, reactive *owns the ceiling*; never let prediction be the only signal, or a novel Black-Friday-scale spike that the forecast didn't see will under-provision.

---

## 5. Design Decisions & Tradeoffs

Every decision below trades cost against safety or simplicity. The unifying principle: prefer the cheaper option *until* it threatens the 90s reaction SLO or the zero-PDB-violation constraint, then fall back to the safer one for the specific workloads that need it. This is why the platform is never "all spot" or "all on-demand," never "pure reactive" or "pure predictive" — it is a per-workload policy mix governed by the CI gate in §8.

### 5.1 Karpenter vs Cluster Autoscaler

**Decision:** Karpenter as the node autoscaler. **Alternatives:** Cluster Autoscaler (CAS) with managed node groups. **Rationale:** CAS scales pre-defined ASGs and binds you to fixed instance types per group; node provision is **3–5 min** (ASG warm + node-ready). Karpenter provisions *just-in-time, right-sized* instances across the whole family in **~40s**, and its consolidation reclaims fragmentation CAS leaves behind. **Consequences:** Karpenter is AWS/Azure-specific (no node-group portability), and its aggressive consolidation requires disciplined PDBs — the tradeoff for speed and packing efficiency.

### 5.2 HPA vs KEDA

**Decision:** HPA for sync request/response services; KEDA for async/event-driven and anything needing scale-to-zero. **Alternatives:** KEDA for everything (it wraps HPA anyway). **Rationale:** HPA's CPU/RPS model fits HTTP services and avoids the extra operator dependency; KEDA adds 50+ scalers and the only path to **zero replicas**. **Consequences:** two scaling systems to operate, but each is best-fit; KEDA-managed HPAs must not be hand-edited (KEDA owns them).

### 5.3 Spot vs On-Demand Mix

**Decision:** 70% spot for stateless/interruption-tolerant, 30% on-demand for stateful/critical, with on-demand fallback when spot capacity is unavailable. **Alternatives:** 100% on-demand (safe, +$357k/mo); 100% spot (cheap, risky). **Rationale:** spot is ~70% cheaper but interrupts at ~5%/hr; pinning critical workloads to on-demand bounds blast radius. **Consequences:** must run a robust interruption handler and over-provision spot diversity (many instance types) to avoid correlated reclaim.

### 5.4 Reactive vs Predictive Scaling

**Decision:** Reactive (HPA/KEDA) as the baseline; predictive pre-warm only for services with strong daily seasonality. **Alternatives:** pure predictive (ML-forecast capacity). **Rationale:** reactive is simple and self-correcting; predictive hides the 40s node-provision tail for known peaks but is wrong on novel spikes. **Consequences:** a cron-based or KEDA-cron pre-scale for the 9am peak avoids the cold-start cliff; mispredictions cost a little idle capacity, not an outage.

### 5.5 VPA + HPA Conflict

**Decision:** VPA in **recommendation mode** (or VPA on memory only) while HPA scales on a custom RPS metric, never both on CPU. **Alternatives:** VPA `Auto` + HPA on CPU (they fight). **Rationale:** if both act on CPU, VPA raises requests as HPA adds replicas, causing oscillation and double-counting. **Consequences:** VPA right-sizes requests offline (better bin-packing), HPA owns the live scaling dimension; humans apply VPA recommendations during deploys.

### 5.6 Consolidation Aggressiveness

**Decision:** `WhenEmptyOrUnderutilized` with `consolidateAfter: 60s` and a 10% disruption budget, frozen during peak. **Alternatives:** `WhenEmpty` only (less churn, worse packing) or immediate consolidation (max savings, max risk). **Rationale:** balances packing efficiency against pod churn and PDB pressure. **Consequences:** ~10–15% more node-hours than maximally aggressive packing, in exchange for stability.

### 5.7 Scale-to-Zero vs Warm Floor

**Decision:** Scale-to-zero for async workers and rarely-hit internal endpoints; a warm floor of >=1 replica for any synchronous user-facing path. **Alternatives:** scale everything to zero (max savings) or never scale below the steady-state min (max readiness). **Rationale:** scale-to-zero's cold start (pod start + first poll, ~10s for workers) is invisible to async consumers draining a queue, but unacceptable for a user waiting on an HTTP response unless a buffering proxy (KEDA HTTP add-on) absorbs it. **Consequences:** the ~1,100 async services reclaim idle cost fully; user-facing services keep a minimum warm replica and rely on burst-buffer pause-pods instead of zero.

### Comparison table

| Dimension | Karpenter | Cluster Autoscaler | KEDA | HPA | VPA |
|-----------|-----------|--------------------|------|-----|-----|
| Scales | Nodes | Nodes | Pods (0..N) | Pods (1..N) | Pod size |
| Provision latency | ~40s | 3–5 min | n/a | 15s | n/a |
| Scale-to-zero | n/a | n/a | Yes | No | No |
| Bin-packing | Excellent | Poor (fixed groups) | n/a | n/a | n/a |
| Cost focus | Instance choice + consolidation | Node count | Idle elimination | Replica fit | Request accuracy |
| Cloud lock-in | High (AWS/Azure) | Low | Low | None | None |

---

## 6. Real-World Implementations

The companies below converge on the same conclusion from different scales: the node layer dominates cost and risk, spot diversification is non-negotiable, and PDB discipline is the precondition for any aggressive consolidation. Where they differ is the *signal* they scale on — RPS, queue depth, in-flight queries, or tokens — which is dictated by what actually saturates their workload.

**Airbnb** migrated from Cluster Autoscaler to **Karpenter** across its Kubernetes fleet, citing faster node provisioning and better bin-packing. They reported substantially reduced compute cost from consolidation and a heavy spot mix, and used Karpenter's instance flexibility to ride out spot capacity shortages by diversifying across many instance types rather than a fixed node group. Their public engineering writeups emphasize PDB discipline as the precondition for safe consolidation.

**Pinterest** runs large-scale autoscaling on EKS and built tooling around **KEDA** for event-driven workloads (Kafka-lag-based consumers) plus HPA for serving, scaling tens of thousands of pods. They highlighted scale-to-zero for batch/async workers as a major idle-cost reduction and custom Prometheus metrics for serving-tier scaling.

**Grafana Labs** runs Mimir/Loki/Tempo on Kubernetes and uses **KEDA with Prometheus-based external metrics** to scale ingesters and queriers on query/ingest load rather than CPU, because CPU is a poor proxy for their queue-driven work. They documented scaling read paths on in-flight query metrics to keep p99 stable under bursty query load.

**Zalando** authored and open-sourced significant Kubernetes autoscaling tooling (the **kube-metrics-adapter**) to drive HPA from external metrics (SQS, Prometheus, ingress RPS) across hundreds of clusters, and pioneered patterns for scaling on application-level signals years before KEDA matured.

**Datadog** runs one of the largest Kubernetes footprints and has published on autoscaling its own intake pipeline with custom metrics and careful PDB/consolidation tuning, noting that the dominant cost lever at their scale is node consolidation and spot diversification, not pod-level scaling.

**Anthropic and OpenAI** operate GPU-heavy inference fleets where the autoscaling problem inverts: GPU nodes (p4d/p5, H100/A100) cost **$30–$98/hr** versus a CPU node's $0.38, so the cost of *over*-provisioning is two orders of magnitude higher and the cost of a cold start (model weights are tens of GB to load) is minutes, not seconds. Public discussion of large-scale LLM serving emphasizes keeping a warm pool of GPU nodes sized to the p99 of forecast demand, scaling on queue depth / tokens-in-flight rather than CPU, and treating spot GPU capacity as best-effort burst only — because a correlated GPU spot reclaim is far more disruptive than a CPU one. The same HPA-on-custom-metric + node-autoscaler pattern applies, but the warm-pool and predictive components dominate over pure reactive scaling.

The throughline across all five: at scale, **pod-level scaling is solved and cheap; the money and the incidents are at the node layer** — instance selection, consolidation, spot diversification, and PDB-safe drain.

---

## 7. Technologies & Tools

| Tool | Scope | Scale-to-zero | Provision speed | Cost lever | Best for |
|------|-------|---------------|-----------------|-----------|----------|
| **HPA** | Pods 1..N | No | 15s loop | Replica right-fit | Sync HTTP/gRPC on CPU/RPS |
| **KEDA** | Pods 0..N | **Yes** | 15s poll | Idle elimination | Kafka/SQS/cron, async workers |
| **VPA** | Pod requests | No | Offline | Accurate requests -> packing | Right-sizing, batch jobs |
| **Karpenter** | Nodes | n/a | **~40s** | Instance choice + consolidation | AWS/Azure cost-optimal nodes |
| **Cluster Autoscaler** | Nodes | n/a | 3–5 min | Node count | Multi-cloud / node-group shops |
| **Fargate** | Pods as nodes | per-pod | ~60–90s | No idle node cost | Spiky low-volume, no node mgmt |

Karpenter and CAS are mutually exclusive per node pool; HPA + KEDA + VPA compose (with the VPA/HPA caveat in §5.5). For node-level metrics scale and cardinality of the Prometheus metrics driving all of this, see [`cross_cutting/prometheus_cardinality_and_scale.md`](cross_cutting/prometheus_cardinality_and_scale.md).

**Why not Fargate for everything?** Fargate removes node management entirely — each pod gets its own micro-VM, billed per vCPU-second with no idle node cost — which is ideal for spiky, low-volume, or untrusted workloads. But at our scale it loses on three axes: (1) **cost** — Fargate's per-pod pricing is ~20–30% more than equivalent EC2 for sustained load, and you can't ride spot's 70% discount; (2) **bin-packing** — there is none, so 24,000 pods means 24,000 micro-VMs with per-pod overhead instead of 29 densely-packed pods per node; (3) **provision latency** — ~60–90s per pod vs Karpenter binding to an already-warm node in milliseconds once capacity exists. We use Fargate selectively for bursty batch jobs and security-isolated tenants, and EC2 + Karpenter for the sustained 4000-service fleet. The decision rule: Fargate when per-pod isolation or zero-ops matters more than density; EC2 + Karpenter when density and spot economics dominate.

---

## 8. Operational Playbook

Operating autoscaling at 4000-service scale is mostly about *preventing* bad configs from shipping (the gate), *seeing* scaling decisions when they go wrong (observability), and having muscle-memory responses for the four recurring failure shapes (runbooks). The gate stops most incidents at PR time; the alerts catch what slips through; the runbooks bound time-to-mitigate when an incident is live.

### (a) Scaling-policy evaluation gate

Every HPA/ScaledObject/NodePool change passes a CI gate before merge:

```yaml
# OPA/Conftest policy snippet (Rego-style intent)
deny[msg] {
  input.kind == "HorizontalPodAutoscaler"
  input.spec.behavior.scaleDown.stabilizationWindowSeconds < 60
  msg := "scaleDown stabilization must be >= 60s to prevent thrash"
}
deny[msg] {
  input.kind == "HorizontalPodAutoscaler"
  input.spec.maxReplicas > 500
  not input.metadata.annotations["cost-approved"]
  msg := "maxReplicas > 500 requires cost approval"
}
deny[msg] {
  input.kind == "Deployment"
  not has_pdb(input)
  msg := "workload must declare a PodDisruptionBudget"
}
```

The gate enforces: scale-down stabilization >= 60s, a cost-ceiling annotation for high `maxReplicas`, and a mandatory PDB for any consolidatable workload. This is the same class of guardrail described in [`cross_cutting/kubernetes_production_hardening.md`](cross_cutting/kubernetes_production_hardening.md).

### (b) Observability for scaling decisions

Instrument the *decisions*, not just the resources. Key signals:

```
# Prometheus signals
kube_horizontalpodautoscaler_status_desired_replicas
kube_horizontalpodautoscaler_status_current_replicas
keda_scaler_metrics_value{scaledObject=...}
karpenter_pods_state{phase="pending"}             # pending-pod backlog
karpenter_nodes_total{capacity_type="spot"}
karpenter_voluntary_disruption_decisions_total    # consolidation actions
node_cpu_utilization:ratio                          # vs 65% target
```

OTel spans wrap the full scale path so a slow scale-up can be attributed to the exact stage:

```
span: scale.event (root)
 ├─ hpa.evaluate         (metric read + desired calc, ~50ms)
 ├─ deployment.patch     (replica update, ~20ms)
 ├─ scheduler.bind       (or -> Pending if no capacity)
 ├─ karpenter.provision  (EC2 RunInstances -> node Ready, ~40s)  <-- usual tall pole
 └─ pod.ready            (image pull + readiness probe pass)
```

When the 90s SLO is missed, the trace immediately shows whether the delay was metric latency, the node-provision tail, or a slow image pull / readiness probe — three completely different fixes. The key derived alerts:

| Alert | Expression (intent) | Meaning |
|-------|---------------------|---------|
| Scale-up SLO miss | `karpenter_pods_state{phase="pending"} > 0` for > 90s | users waiting on capacity |
| Waste | `node_cpu_utilization:ratio < 0.50` for > 30 min | over-provisioned, money burning |
| Runaway | `hpa_desired == hpa_max` for > 10 min | bad metric or real capacity wall |
| PDB risk | service ready replicas `< pdb_min_available` | drain breached a budget |
| Spot storm | `rate(karpenter_interruptions[5m]) > 20/min` | correlated reclaim in progress |

Cardinality matters: emitting per-pod, per-HPA scaling metrics across 4000 services × 24k pods can explode Prometheus series count — keep scaling metrics at the *workload* (Deployment) granularity, not per-pod, per the guidance in [`cross_cutting/prometheus_cardinality_and_scale.md`](cross_cutting/prometheus_cardinality_and_scale.md). Error-budget burn from autoscaling lag is tracked per [`cross_cutting/slo_error_budget_math.md`](cross_cutting/slo_error_budget_math.md); cross-cluster scaling topology in [`cross_cutting/multi_cluster_networking.md`](cross_cutting/multi_cluster_networking.md).

### (c) Incident runbooks

**Runbook 1 — HPA thrashing.**
Symptom: a Deployment's replica count oscillates (e.g., 8<->24) every ~30s and pods churn. Diagnosis: `kubectl describe hpa <name>` shows alternating `SuccessfulRescale` events in both directions; inspect `scaleDown.stabilizationWindowSeconds` (likely 0) and whether the metric is raw CPU. Mitigation: `kubectl patch` the HPA to set `scaleDown.stabilizationWindowSeconds: 300` and a `Percent: 10 / 60s` shrink policy immediately. Resolution: re-evaluate the metric — move to per-pod RPS rather than raw CPU — and confirm the target value is actually achievable at steady state.

**Runbook 2 — Spot mass-eviction.**
Symptom: many spot nodes interrupted in the same AZ/instance type within minutes; pods Pending; p99 latency rising. Diagnosis: `kubectl get nodes -l karpenter.sh/capacity-type=spot` shows a sudden drop; Karpenter interruption-queue metrics spike; AWS spot capacity shortage for that instance type. Mitigation: Karpenter falls back to on-demand automatically if the NodePool's `capacity-type` requirement includes `on-demand`; temporarily bias the NodePool to on-demand and widen the instance-type list. Resolution: permanently diversify the NodePool across >=8 instance types and 3 AZs; pin critical workloads to on-demand via a separate NodePool.

**Runbook 3 — Scale-up stuck on quota.**
Symptom: pods Pending > 90s, no new nodes appearing. Diagnosis: `kubectl logs -n karpenter deploy/karpenter` shows `InsufficientInstanceCapacity` or `VcpuLimitExceeded`; check Service Quotas console for the EC2 family's vCPU limit. Mitigation: `kubectl edit nodepool` to shift `node.kubernetes.io/instance-type` to an alternate family/AZ with headroom; file an urgent Service Quotas increase. Resolution: pre-raise vCPU quotas to 1.5x projected peak per family; ensure every NodePool spans multiple families and all 3 AZs so a single exhausted pool never blocks scale-up.

**Runbook 4 — Runaway scale-out cost.**
Symptom: node count and bill climbing without traffic justification; the `hpa_desired == hpa_max` alert firing. Diagnosis: identify the offending ScaledObject/HPA pinned at max via `kubectl get hpa --all-namespaces | awk '$6==$7'`; inspect the driving metric (often a stuck exporter reporting a stale max value). Mitigation: pause the ScaledObject (`kubectl annotate scaledobject <n> autoscaling.keda.sh/paused-replicas="<safe>"`) and rely on the NodePool `limits.cpu` cost ceiling to cap node growth. Resolution: add metric sanity clamps, right-size `maxReplicaCount` to 2x p99 demand, and keep the `desired == max` sustained-10-min alert.

---

## 9. Common Pitfalls & War Stories

A pattern runs through all seven incidents below: the autoscaler did exactly what it was configured to do, and the configuration was subtly wrong. None were "the autoscaler crashed" — they were missing guardrails (PDB, sanity bound, disruption budget) or a metric that lied. The defenses are the policy gate (§8a), the derived alerts (§8b), and the hardening patterns in [`cross_cutting/kubernetes_production_hardening.md`](cross_cutting/kubernetes_production_hardening.md).

**1. The missing PDB outage ($58k).** As in §4.3 — Karpenter consolidated 40 nodes with no PDB on payments, evicting all replicas at once. Root cause: a Deployment templated by an older Helm chart that predated the PDB-required policy, so it slipped through. Detection was a synthetic checkout probe failing, not the autoscaler — there was no alert on "node-disruption-evicting-all-replicas-of-a-service." **3m40s downtime, ~31,000 failed checkouts, ~$58,000 lost GMV.** Fix: mandatory PDB policy gate on every Deployment + Karpenter disruption budget, plus an alert when a service drops below its PDB `minAvailable`. See [`cross_cutting/kubernetes_production_hardening.md`](cross_cutting/kubernetes_production_hardening.md).

**2. HPA thrash burning node-hours ($4.1k/night).** Zero scale-down stabilization caused 8<->24 replica flapping every ~30s for 6 hours, churning nodes up and down. Root cause: the metric was raw CPU utilization at a 50% target with no cool-down, so each scale-up dropped per-pod CPU below target and triggered an immediate scale-down. The pod churn also blew the image-pull cache, adding ~8s of cold start per cycle. **$4,100 wasted in one night** plus elevated p99 from cold starts. Fix: asymmetric behavior (instant up, 300s scale-down window, 10%/min shrink cap) and switch the metric to per-pod RPS.

**3. Stuck Kafka exporter -> runaway KEDA ($23k).** A consumer-lag exporter crashed mid-poll and reported a stale max-int lag; KEDA scaled the consumer to `maxReplicaCount: 200` and held there overnight, dragging Karpenter to add ~70 nodes. Root cause: no upper sanity bound on the lag metric and a `maxReplicaCount` set far above any real need "to be safe." The alert that should have caught it — `desired == max` sustained — did not exist. **~$23,000 of idle compute over a weekend** before a Monday cost review caught it. Fix: clamp the lag metric to a plausible ceiling, right-size `maxReplicaCount` to 2x p99 demand, a hard NodePool `limits.cpu`, and an alert on `desired_replicas == maxReplicas` for > 10 min.

**4. Spot AZ correlation (11 min degraded).** A NodePool pinned to a single instance type (`m6i.2xlarge`) in two AZs lost ~180 nodes in 4 minutes during an AWS spot reclaim wave. Root cause: insufficient instance-type diversity meant the reclaim hit a large correlated slice, and on-demand fallback could not place the displaced pods fast enough during the surge. **11 minutes of degraded latency (p99 +340ms), ~9,000 requests slow.** Fix: diversify each NodePool to >=8 instance types across 3 AZs and keep a 5% on-demand warm pool so fallback placement is instant.

**5. VPA+HPA double-scaling ($9k/mo).** A team enabled VPA `Auto` and HPA on CPU simultaneously. VPA kept raising CPU requests as HPA added pods, so per-pod utilization never reached the 60% target — the Deployment scaled to its 150-replica cap and *stayed there*. Root cause: the two controllers both acting on CPU created a feedback loop with no stable fixed point. **~$9,000/mo overspend** until caught in a quarterly cost review (it never triggered an availability alert because the service was, if anything, over-provisioned). Fix: VPA recommendation-mode only when HPA owns CPU; or split dimensions (VPA on memory, HPA on RPS).

**6. Consolidation during deploy (~1,200 dropped requests).** Consolidation kicked in mid-rollout while new pods were `ContainerCreating`; Karpenter counted them as schedulable elsewhere and drained a node that was the only home for an old replica, briefly dropping ready capacity below the desired count. Root cause: consolidation's bin-pack solve treats `Pending`/`ContainerCreating` pods as already-placeable, optimistically reclaiming the node before the new pods were `Ready`. **~1,200 dropped requests over 90 seconds.** Fix: `karpenter.sh/do-not-disrupt: "true"` annotation applied during rollouts (removed on completion) and a peak-hours consolidation freeze.

**7. Quota wall during a launch spike (4 min Pending).** A product launch drove a 14x spike; HPA computed 900 replicas, Karpenter tried to launch ~30 nodes and hit the account's **vCPU service quota** for the c6i family. Root cause: the quota had been sized to a previous peak and never raised; the NodePool was not diversified across families to route around the exhausted one. Pods sat `Pending` for ~4 minutes until on-call manually shifted the NodePool to m6i. **~4 minutes of capacity starvation, ~6,400 requests queued/slow.** Fix: pre-raise vCPU quotas to 1.5x projected peak per family, diversify NodePools across families, and alert on `karpenter` `InsufficientInstanceCapacity`/`VcpuLimitExceeded` log events.

---

## 10. Capacity Planning

Capacity planning here answers two questions: *how many pods* a service needs at a given load, and *how many nodes* the fleet needs to host all pods at the target utilization and spot mix. The formulas chain — pod demand feeds node demand feeds cost — and the burst buffer and disruption-budget math below bound the two operational extremes (instant spike absorption and safe fleet-wide recycling).

### Scaling formulas

**Pod replicas (HPA):**
```
desiredReplicas = ceil( currentReplicas × currentMetricValue / targetMetricValue )
```

**Node count from pending resource demand:**
```
nodesNeeded = ceil( sum(pod CPU requests) / nodeAllocatableCPU )      # CPU-bound
            = max( CPU-bound, memory-bound )                          # take binding dim
effectiveNodes = ceil( nodesNeeded / targetUtilization )             # add headroom
```

**Spot/on-demand split given an interruption tolerance:**
```
onDemandFloor = ceil( criticalReplicas / minAvailableFraction )      # never on spot
spotNodes     = totalNodes − onDemandNodes
expectedReclaims/hr = spotNodes × spotInterruptionRate(0.05)
```

### Worked example

Service tier at peak: **2,000 services × 12 replicas × 250m = 6,000 vCPU**, 12,000 GiB.

```
Node: m6i.2xlarge, allocatable 7.3 vCPU / 29 GiB
CPU-bound nodes  = ceil(6000 / 7.3)   = 822
Mem-bound nodes  = ceil(12000 / 29)   = 414
Binding = CPU = 822 nodes (raw, at 100% packing)

At 65% target util:  effectiveNodes = ceil(822 / 0.65) = 1265 nodes
```

Cost at the 70/30 spot mix:

```
On-demand 30% = 380 nodes × $0.384 × 730 = $106,521/mo
Spot      70% = 885 nodes × $0.1152 × 730 = $74,438/mo
Total                                       $180,959/mo
```

vs all-on-demand at this tier (1265 × $0.384 × 730 = **$354,604/mo**) -> **$173,645/mo (49%) saved** from the spot mix, on top of the elasticity savings from not static-provisioning for peak. Expected spot reclaims: 885 × 0.05 = **~44 nodes/hr**, each drained PDB-safely inside 120s.

### Burst buffer (overprovisioning) sizing

The 40s node-provision tail means a 10x spike that needs ~1,400 new pods would wait 40s for hardware even with Karpenter. To hide it, run a pool of **low-priority pause pods** (Kubernetes `priorityClass` = -1) that reserve capacity and are evicted instantly when real pods need the room:

```
Spike magnitude to absorb instantly = 10x baseline burst = ~600 pods
Buffer nodes = ceil(600 / 29 pods-per-node) = 21 nodes
Buffer cost  = 21 × $0.384 (on-demand, for instant availability) × 730 = $5,886/mo
```

For the full 2,600-node fleet a **5% warm-pool = 130 nodes** of pre-provisioned on-demand capacity costs ~$36.4k/mo but eliminates the cold-start cliff for the 10x spike. The tradeoff is explicit: $36.4k/mo of always-warm capacity buys you a sub-second response to bursts instead of a 40s tail. Tune the buffer to the burst magnitude you actually see, not the worst case imaginable.

### Disruption-budget math during node recycling

Weekly node recycling (`expireAfter: 168h`) for patching must not breach any PDB. With a 10% node disruption budget on a 2,600-node fleet:

```
Concurrent disruptable nodes = 10% × 2600 = 260 nodes
Time to recycle whole fleet  = (2600 / 260) × (drain+provision ~5 min) = 50 min
```

So a full fleet recycle for a CVE patch completes in **~50 minutes** without ever violating a PDB, provided every workload's PDB `minAvailable` leaves at least one replica evictable. A common trap: a 2-replica Deployment with `minAvailable: 2` is **un-disruptable** and will stall recycling forever — the policy gate rejects `minAvailable >= replicas`.

See [`cross_cutting/kubernetes_production_hardening.md`](cross_cutting/kubernetes_production_hardening.md) for warm-pool and overprovisioning (pause-pod) patterns, and [`../cloud_cost_optimization_finops/README.md`](../cloud_cost_optimization_finops/README.md) for spot strategy and RI/Savings-Plan layering.

---

## 11. Interview Discussion Points

**Why is "fast up, slow down" the cardinal rule of HPA tuning?**
Because scaling up protects users (latency) while scaling down only saves cost, and cost can wait. Reacting up instantly with zero stabilization absorbs spikes; shrinking slowly with a 300s window prevents the oscillation where each scale-down re-spikes load. Asymmetric `behavior` blocks are the mechanism — set `scaleUp.stabilizationWindowSeconds: 0` and `scaleDown` to 300s.

**How do you hit a 90-second SLO for a 10x spike when nodes take time to provision?**
The pod loop reacts in 15s (HPA) but pods go Pending until hardware exists, so the node provisioner's latency dominates. Karpenter's ~40s provision (vs CAS 3–5 min) plus a small warm-pool/overprovisioning buffer of pause-pods (low-priority placeholder pods that get evicted instantly to make room) hides the tail and keeps you under 90s. Pre-warming for predictable peaks removes the cold-start entirely.

**Why Karpenter over Cluster Autoscaler?**
Karpenter provisions right-sized instances just-in-time across the whole instance family and consolidates fragmentation, while CAS scales fixed node groups slowly. The cost win is from instance flexibility (pick the cheapest fit, spot-first) and consolidation, not just node count. The tradeoff is cloud lock-in and a hard dependency on disciplined PDBs.

**When do you reach for KEDA instead of HPA?**
When the scaling signal is an external event backlog (Kafka lag, SQS depth, cron) or when you need scale-to-zero, which HPA cannot do (its floor is 1 replica). KEDA wraps HPA for the 1..N range and handles 0..1 itself via `activationLagThreshold`. For plain CPU/RPS sync services, HPA alone is simpler.

**How do you prevent consolidation from causing an outage?**
Three controls: a mandatory PodDisruptionBudget on every workload (the Eviction API enforces it server-side so a drain can't breach `minAvailable`), a Karpenter disruption budget capping concurrent disruptions (e.g., 10% of nodes), and a consolidation freeze during peak windows and active rollouts (`do-not-disrupt`). Missing the PDB is the classic cause of consolidation-induced downtime.

**What's the right spot/on-demand mix and how do you keep spot safe?**
Roughly 70% spot for interruption-tolerant stateless workloads, 30% on-demand for stateful/critical, with on-demand fallback. Safety comes from diversification — at least 6 instance types across 3 AZs — so a capacity reclaim in one type doesn't take a correlated chunk of the fleet. Each spot node must drain PDB-safely inside the 120s warning, which means `terminationGracePeriodSeconds <= 90s`.

**Why do VPA and HPA conflict, and how do you run them together?**
If both act on CPU they fight: VPA raises per-pod requests while HPA adds replicas, so per-pod utilization never reaches target and the Deployment over-scales. The fix is to separate dimensions — HPA on a custom RPS metric, VPA on memory or in recommendation-only mode — so each owns a distinct lever. Never let both auto-act on the same resource.

**How do you measure whether the platform is actually saving money?**
Track average node utilization against the 65% target, the pending-pod backlog (should be near zero), and consolidation-decision counts, then attribute cost per namespace via Kubecost-style allocation. The headline KPI is spend-vs-static-baseline; the leading indicators are utilization and the spot fraction. A platform "saving money" while running at 35% utilization is lying.

**What is the failure mode of scaling on a bad metric?**
A stuck or stale metric drives scaling to its ceiling and parks there — a crashed Kafka lag exporter reporting max-int lag makes KEDA scale to `maxReplicaCount` and stay, dragging the node autoscaler with it. Defenses are metric sanity bounds, a hard `maxReplicas`/NodePool `limits.cpu` cost ceiling, and an alert on `desired == max` sustained beyond 10 minutes.

**How does predictive scaling complement reactive scaling?**
Reactive (HPA/KEDA) is self-correcting and handles novel spikes, but it always pays the node-provision latency on the way up. Predictive pre-warm — a scheduled scale-up before a known 9am peak — hides that latency for seasonal traffic, at the cost of some idle capacity if the forecast is wrong. Use predictive only where seasonality is strong and the cold-start cliff is expensive; never rely on it alone for unpredictable bursts.

**How do you bound the blast radius of an autoscaler bug across 4000 services?**
Per-tenant and per-NodePool hard limits: `maxReplicas` on every HPA, `limits.cpu` on every NodePool, disruption budgets, and a policy gate in CI that rejects configs violating these. The autoscaler control plane itself is run at 99.95% but is designed so that if it dies, workloads keep running at their current scale — autoscaling failure must degrade to "no scaling," never to "mass eviction."

**What's the difference between cluster utilization and cost efficiency?**
Utilization measures how packed your nodes are; cost efficiency measures whether you're using the cheapest correct capacity for that packing. You can be 90% utilized entirely on on-demand and still overspend by 60% versus a spot mix. True cost-awareness optimizes both axes — pack tightly (consolidation, VPA-accurate requests) *and* buy cheaply (spot-first, right instance family) — which is why this platform couples node packing with instance selection rather than treating them separately. See [`../kubernetes_scheduling_and_autoscaling/README.md`](../kubernetes_scheduling_and_autoscaling/README.md).

**Why is the Eviction API critical, and what breaks if you use raw delete?**
The Eviction API (`POST .../pods/{name}/eviction`) checks PodDisruptionBudgets server-side and refuses to evict if it would breach `minAvailable`, whereas a raw `DELETE` bypasses the PDB entirely and removes the pod unconditionally. Any drain path — Karpenter consolidation, spot reclaim, node recycling, `kubectl drain` — must use eviction, or a "safe" scale-down silently becomes an outage. This is the single most common root cause of consolidation-induced downtime: tooling that deletes instead of evicts.

**How do you scale a service whose bottleneck is not CPU?**
Scale on the metric that actually reflects saturation — queue depth for workers, in-flight requests or p99 latency for I/O-bound services, GPU utilization or tokens-in-flight for inference. CPU is a poor proxy for queue-driven or I/O-bound work, so a CPU-based HPA will under-scale a service that is latency-bound while sitting at 30% CPU. Expose the real saturation signal via prometheus-adapter (custom metric) or KEDA (external metric) and target that; Grafana Labs scaling queriers on in-flight queries rather than CPU is the canonical example.

**What happens to autoscaling if Prometheus or the metrics adapter goes down?**
HPA stops getting fresh custom metrics and, per its design, holds the last known replica count rather than scaling to zero — but it cannot react to new spikes, so a metrics outage during a traffic surge means under-provisioning. The mitigations are: run the metrics adapter with >=3 replicas behind the aggregation layer, fall back to a CPU-based HPA as a safety floor if the custom metric is stale, and alert on metric staleness. The principle is fail-static: a monitoring outage must freeze scaling, never trigger a runaway scale-down or scale-up.

**How do you handle stateful workloads that can't tolerate spot interruption?**
Pin them to a dedicated on-demand NodePool via node affinity / a `capacity-type: on-demand` requirement, give them a PDB with `minAvailable` that preserves quorum (e.g., 2 of 3 for a Raft cluster), and set a generous `terminationGracePeriodSeconds` so graceful handoff completes. Stateful sets also need PodDisruptionBudgets that account for ordered rollout — disrupting the leader and a follower simultaneously can break quorum even if the raw replica math looks fine. The rule: spot is for stateless burst; anything with durable local state or quorum semantics stays on-demand.

**Would you ever scale to zero a synchronous HTTP service?**
Only with a request-buffering proxy in front, because a cold HTTP service can't hold the inbound request while the first pod starts. KEDA's HTTP add-on does exactly this — it holds the connection at the proxy, triggers the 0->1 scale, and forwards once a pod is Ready, trading ~1–3s of added first-request latency for zero idle cost. For latency-critical user-facing paths the cold-start penalty usually isn't worth it; scale-to-zero is best reserved for async workers and rarely-hit internal endpoints.
