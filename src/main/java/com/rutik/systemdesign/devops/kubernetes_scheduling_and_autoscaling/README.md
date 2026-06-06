# Kubernetes Scheduling & Autoscaling

> Phase 2 — Containers & Kubernetes · Difficulty: Advanced

Scheduling decides *where* a Pod runs; autoscaling decides *how many* Pods and *how many* nodes exist. Together they determine whether your cluster is reliable (Pods land where they should, survive disruptions) and cost-efficient (you run the minimum capacity for the load). This module covers requests/limits/QoS, affinity/taints/topology, the three autoscalers (HPA, VPA, Cluster Autoscaler/Karpenter), KEDA event-driven scaling, and PodDisruptionBudgets.

---

## 1. Concept Overview

**Scheduling** (placement):
- **Requests/limits** — requests reserve capacity and set QoS; limits cap usage. The scheduler places Pods based on *requests* vs node allocatable.
- **Node affinity / nodeSelector** — constrain Pods to node types (GPU, spot, AZ).
- **Pod affinity/anti-affinity** — co-locate or spread Pods relative to each other.
- **Taints/tolerations** — nodes repel Pods unless the Pod tolerates the taint (dedicate nodes).
- **Topology spread constraints** — spread Pods evenly across zones/nodes for HA.

**Autoscaling** (capacity):
- **HPA (Horizontal Pod Autoscaler)** — scales Pod *replicas* on CPU/memory/custom metrics.
- **VPA (Vertical Pod Autoscaler)** — adjusts Pod *requests/limits* (right-sizing).
- **Cluster Autoscaler / Karpenter** — adds/removes *nodes* when Pods can't schedule / nodes are underused.
- **KEDA** — event-driven scaling (queue depth, Kafka lag, cron) including scale-to-zero.

---

## 2. Intuition

> **One-line analogy**: Scheduling is seating guests at a wedding — you honor dietary needs (resource requests), keep feuding families apart (anti-affinity), reserve the head table (taints), and spread the wedding party across tables (topology spread). Autoscaling is calling the venue to add or remove tables as more or fewer guests RSVP.

**Mental model**: The scheduler is a bin-packer constrained by rules. It filters nodes that *can* hold the Pod (enough requested resources, tolerations match taints, affinity satisfied), then scores the survivors and picks the best. Autoscaling closes the loop: HPA changes how many Pods you ask for, and the node autoscaler changes how many bins exist to hold them. Pending Pods (nothing fits) trigger node scale-up; empty/underused nodes trigger scale-down.

**Why it matters**: Misconfigured requests waste money (over-request) or cause OOMKills/throttling and noisy-neighbor problems (under-request). No anti-affinity/topology spread means all replicas can land on one node — which then dies and takes the whole service down. No PodDisruptionBudget means a node drain (upgrade) can evict every replica at once. Autoscaling tuned wrong either can't keep up with spikes or flaps and burns money.

**Key insight**: The scheduler bin-packs on **requests**, not actual usage. If you under-request, the node looks empty to the scheduler, it over-packs, and real usage causes contention. If you over-request, nodes look full while sitting idle, and you pay for capacity you never use. Requests are the single most consequential number you set.

---

## 3. Core Principles

1. **The scheduler packs by requests.** Set requests close to real usage; they drive placement and QoS.
2. **Spread for availability.** Topology spread + anti-affinity prevent single-node/zone wipeouts.
3. **Taints reserve, tolerations grant access.** Dedicate nodes (GPU, spot) deliberately.
4. **Scale Pods, then nodes.** HPA reacts to load; the node autoscaler reacts to unschedulable Pods.
5. **Right-size continuously.** VPA recommendations; avoid the "everyone requests 1 CPU" anti-pattern.
6. **Protect availability during disruption.** PodDisruptionBudgets bound voluntary evictions.

---

## 4. Types / Architectures / Strategies

### QoS classes (from requests/limits)

| QoS | Condition | Eviction order under node pressure |
|-----|-----------|-----------------------------------|
| Guaranteed | requests == limits, all containers | Evicted last |
| Burstable | requests < limits (or partial) | Middle |
| BestEffort | no requests/limits | Evicted first |

### Autoscaler comparison

| Autoscaler | Scales | Signal | Notes |
|-----------|--------|--------|-------|
| HPA | Pod replicas | CPU/mem/custom/external metrics | Default sync ~15s; needs metrics-server |
| VPA | Pod requests/limits | Historical usage | Recreates Pods to apply (disruptive); don't combine with HPA on same metric |
| Cluster Autoscaler | Nodes (node groups) | Unschedulable Pods / underused nodes | Works within predefined node groups/ASGs |
| Karpenter | Nodes (just-in-time) | Unschedulable Pods | Provisions right-sized nodes directly, fast, consolidation |
| KEDA | Pod replicas (incl. 0) | Queue/Kafka/cron/etc. | Event-driven, scale-to-zero |

### Placement controls

| Control | Purpose |
|---------|---------|
| nodeSelector / node affinity | Pin to node types (GPU, arch, AZ) |
| Pod anti-affinity | Spread replicas off the same node |
| topologySpreadConstraints | Even spread across zones/nodes |
| Taints + tolerations | Reserve nodes for specific workloads |
| PodDisruptionBudget | Limit simultaneous voluntary evictions |

---

## 5. Architecture Diagrams

```
Scheduling: filter then score

  Pending Pod (requests: cpu 500m, mem 1Gi; tolerates spot; anti-affinity app=web)
        |
        v
  FILTER nodes: enough allocatable? taints tolerated? affinity ok? topology ok?
        |  feasible: [node2, node5]
        v
  SCORE: resource balance, spread, affinity preference -> pick node5 -> bind
        |  if NO feasible node -> Pod stays Pending -> triggers node autoscaler

Autoscaling control loops

  load up -> HPA: replicas 3 -> 8 -> some Pods Pending (no room)
                                       |
                                       v
                          Karpenter/Cluster Autoscaler: launch node(s) -> Pods schedule
  load down -> HPA: 8 -> 3 -> nodes underused -> autoscaler consolidates/removes nodes
```

---

## 6. How It Works — Detailed Mechanics

### Requests/limits and HPA

```yaml
# Deployment: requests drive scheduling + QoS; HPA scales replicas on CPU utilization.
resources:
  requests: {cpu: 250m, memory: 256Mi}
  limits:   {memory: 512Mi}            # memory cap; CPU limit omitted to avoid throttling
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: {name: web}
spec:
  scaleTargetRef: {apiVersion: apps/v1, kind: Deployment, name: web}
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: {type: Utilization, averageUtilization: 70}   # % of REQUESTED cpu
  behavior:                              # tame flapping
    scaleDown:
      stabilizationWindowSeconds: 300    # wait 5min before scaling down
```

HPA's CPU utilization is a percentage of the **request**, so if you don't set CPU requests, HPA can't compute utilization and won't scale.

### Topology spread (HA across zones)

```yaml
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: DoNotSchedule      # hard: never let one zone hold too many
    labelSelector: {matchLabels: {app: web}}
  - maxSkew: 1
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: ScheduleAnyway     # soft: prefer node spread
    labelSelector: {matchLabels: {app: web}}
```

### Taints/tolerations to dedicate nodes

```bash
kubectl taint nodes gpu-node-1 dedicated=gpu:NoSchedule   # repel everything by default
```

```yaml
# Only Pods that tolerate it (and request a GPU) land on the GPU node:
tolerations: [{key: dedicated, operator: Equal, value: gpu, effect: NoSchedule}]
nodeSelector: {nvidia.com/gpu.present: "true"}
```

### Karpenter (just-in-time, right-sized nodes)

```yaml
# Karpenter provisions nodes that fit pending Pods directly (no fixed node groups),
# blends spot+on-demand, and consolidates underused nodes.
apiVersion: karpenter.sh/v1
kind: NodePool
spec:
  template:
    spec:
      requirements:
        - {key: karpenter.sh/capacity-type, operator: In, values: ["spot","on-demand"]}
        - {key: kubernetes.io/arch, operator: In, values: ["amd64","arm64"]}
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized   # pack workloads, remove waste
    consolidateAfter: 30s
```

### KEDA event-driven scale-to-zero

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
spec:
  scaleTargetRef: {name: worker}
  minReplicaCount: 0                # scale to ZERO when the queue is empty (cost!)
  maxReplicaCount: 50
  triggers:
    - type: aws-sqs-queue
      metadata: {queueURL: ..., queueLength: "20"}   # 1 replica per 20 messages
```

### PodDisruptionBudget (survive drains)

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
spec:
  minAvailable: 2                   # during node drains/upgrades, keep >=2 web Pods up
  selector: {matchLabels: {app: web}}
# Without this, draining a node can evict ALL replicas at once -> outage during a routine upgrade.
```

---

## 7. Real-World Examples

- **Karpenter at scale (AWS)**: teams replace Cluster Autoscaler + fixed ASGs with Karpenter to get right-sized, spot-heavy nodes provisioned in seconds and aggressive consolidation — often cutting compute cost 30–50%.
- **KEDA for queue workers**: scale-to-zero workers wake on SQS/Kafka backlog, so idle queues cost nothing and spikes scale to dozens of consumers (see [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/)).
- **Topology spread for HA**: spreading replicas one-per-AZ means a single AZ outage degrades capacity by ~1/3 rather than taking the service down.
- **GPU node pools** tainted `nvidia.com/gpu` so only ML workloads (which tolerate the taint and request GPUs) schedule on expensive accelerators.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| CPU limit | Set (predictable cap) | Omit (no throttling) | Latency-sensitive services often omit |
| Node autoscaler | Cluster Autoscaler (node groups) | Karpenter (just-in-time) | Flexibility/cost vs maturity/simplicity |
| Scaling signal | CPU (simple) | Custom/external (accurate) | Does CPU reflect real load? |
| Spot usage | Heavy spot (cheap) | On-demand (reliable) | Cost vs interruption tolerance |
| VPA + HPA | Separate metrics | Conflict on same metric | Don't scale replicas and requests on the same signal |
| Spread | Hard (DoNotSchedule) | Soft (ScheduleAnyway) | Strict HA vs schedulability |

---

## 9. When to Use / When NOT to Use

**Tune scheduling/autoscaling when:** running cost-sensitive or bursty workloads, multi-AZ HA requirements, mixed node types (GPU/spot/ARM), or large clusters where bin-packing efficiency matters.

**Keep defaults when:** small steady workloads — set sane requests/limits, an HPA with CPU, and topology spread, and don't over-engineer. Avoid combining VPA and HPA on the same metric; avoid CPU limits on latency-critical paths; don't run everything on spot without interruption handling.

---

## 10. Common Pitfalls

**Pitfall 1 — No requests, or copy-pasted requests, breaking the scheduler and HPA.**

```yaml
# BROKEN: no resource requests. Scheduler treats the Pod as ~free -> over-packs nodes ->
# real usage causes contention/OOM; and HPA can't compute CPU% -> never scales.
containers:
  - name: web
    image: web:1.0
    # (no resources)
```

```yaml
# FIX: set requests near real usage (measure with VPA recommendations / metrics).
    resources:
      requests: {cpu: 250m, memory: 256Mi}     # enables correct packing + HPA utilization
      limits:   {memory: 512Mi}
```

**Pitfall 2 — All replicas on one node; node dies; full outage.** Without anti-affinity/topology spread, the scheduler may pack all 3 replicas onto one roomy node. FIX: `topologySpreadConstraints` across zones and hostnames so a single node/AZ loss can't take the whole service down.

**Pitfall 3 — No PodDisruptionBudget during cluster upgrades.** A routine node drain evicts every replica simultaneously, causing an outage mid-upgrade.

```yaml
# FIX: a PDB bounds voluntary disruptions.
kind: PodDisruptionBudget
spec: {minAvailable: 2, selector: {matchLabels: {app: web}}}
# Now `kubectl drain` evicts only down to the budget, never below 2 ready Pods.
```

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| metrics-server | CPU/memory metrics for HPA/`kubectl top` |
| HPA (autoscaling/v2) | Replica autoscaling on metrics |
| VPA | Request/limit right-sizing recommendations |
| Cluster Autoscaler | Node scaling within node groups |
| Karpenter | Just-in-time, right-sized node provisioning |
| KEDA | Event-driven + scale-to-zero |
| Prometheus Adapter | Custom/external metrics for HPA |
| Goldilocks | Surfaces VPA recommendations as dashboards |
| `kubectl describe node` | allocatable vs requested, taints |

---

## 12. Interview Questions with Answers

**Q1: How does the scheduler decide where a Pod goes?**
Two phases: filtering (which nodes are feasible — enough allocatable resources for the Pod's *requests*, taints tolerated, node affinity/selectors and topology constraints satisfied) and scoring (rank feasible nodes by resource balance, spread, and affinity preferences). It binds the Pod to the highest-scoring node by setting `nodeName`; the kubelet then starts it. If no node is feasible, the Pod stays Pending and may trigger node autoscaling.

**Q2: Why are requests the most important resource setting?**
The scheduler bin-packs based on requests, not actual usage. Under-request and nodes look empty, so it over-packs and real usage causes contention/OOM/throttling; over-request and nodes look full while idle, wasting money. Requests also determine QoS class and HPA's CPU-utilization denominator. Setting them close to measured real usage is the single highest-leverage tuning decision.

**Q3: Explain QoS classes and their effect.**
Guaranteed (requests==limits for all containers) Pods are evicted last under node memory pressure; Burstable (requests<limits) are in the middle; BestEffort (no requests/limits) are evicted first. QoS controls the kubelet's eviction order and `oom_score_adj`, so latency-critical Pods should be Guaranteed to be the least likely to be killed when a node is squeezed.

**Q4: HPA vs VPA vs Cluster Autoscaler — what does each scale?**
HPA scales the number of Pod replicas based on CPU/memory/custom metrics. VPA adjusts a Pod's requests/limits (right-sizing), recreating Pods to apply changes. Cluster Autoscaler (and Karpenter) add/remove nodes when Pods can't schedule or nodes are underused. They compose: HPA adds Pods, the node autoscaler adds nodes to hold them. Don't run HPA and VPA on the same metric — they fight.

**Q5: How does HPA actually compute the desired replica count?**
`desiredReplicas = ceil(currentReplicas * (currentMetricValue / targetMetricValue))`. For CPU utilization, the metric is a percentage of the *requested* CPU averaged across Pods — which is why CPU requests must be set or HPA can't compute it. The controller syncs ~every 15s and applies a stabilization window (default 5min on scale-down) to avoid flapping.

**Q6: What are taints and tolerations, and a use case?**
A taint on a node repels Pods that don't explicitly tolerate it; a matching toleration on a Pod lets it schedule there. Use them to dedicate nodes: taint GPU nodes `nvidia.com/gpu:NoSchedule` so only GPU workloads (which add the toleration) land there, keeping expensive accelerators free of general Pods. Effects: `NoSchedule`, `PreferNoSchedule`, `NoExecute` (also evicts existing Pods).

**Q7: How do you ensure replicas survive a node or AZ failure?**
Use `topologySpreadConstraints` (and/or Pod anti-affinity) to spread replicas across `topology.kubernetes.io/zone` and `kubernetes.io/hostname`, so no single node or AZ holds too many. With `maxSkew: 1` across zones, an AZ loss degrades capacity by roughly its share rather than taking the service down. Pair with a PodDisruptionBudget to protect against voluntary disruptions.

**Q8: What is a PodDisruptionBudget and when is it essential?**
A PDB limits how many Pods of a set can be *voluntarily* disrupted at once (`minAvailable`/`maxUnavailable`). It's essential during node drains (cluster upgrades, autoscaler scale-down): without it, draining a node can evict all replicas simultaneously and cause an outage during a routine operation. With `minAvailable: 2`, drains block until they can proceed without dropping below 2 ready Pods.

**Q9: Cluster Autoscaler vs Karpenter?**
Cluster Autoscaler scales predefined node groups (ASGs) up/down based on unschedulable Pods and underutilization — simple but constrained to the instance types you pre-defined. Karpenter provisions nodes just-in-time, choosing instance types/sizes that best fit pending Pods directly (no node groups), blends spot/on-demand, and aggressively consolidates — typically faster scale-up and lower cost, at the price of being newer/more complex.

**Q10: What does KEDA add over HPA?**
KEDA enables event-driven scaling on external sources HPA can't natively use — queue depth (SQS/RabbitMQ), Kafka consumer lag, cron schedules, Prometheus queries — and crucially supports scale-to-zero. Idle workers cost nothing and wake on backlog. Under the hood KEDA drives an HPA, translating external triggers into scaling decisions.

**Q11: Why might you omit CPU limits but keep memory limits?**
A CPU limit causes CFS throttling: once a Pod uses its quota within a 100ms period, it's paused, spiking tail latency even when the node has spare CPU. For latency-sensitive services, omitting the CPU limit (keeping the request for scheduling) lets the Pod burst into idle CPU. Memory limits are kept because exceeding memory triggers an OOM kill, and unbounded memory growth can take down a node.

**Q12: A Pod is stuck Pending. How do you diagnose it?**
`kubectl describe pod` shows the scheduler's reason: "Insufficient cpu/memory" (no node has enough requested capacity → node autoscaler should add one, or requests are too high), "node(s) had taint … that the pod didn't tolerate", "didn't match node affinity/selector", "had volume node affinity conflict" (zonal volume mismatch), or "didn't match pod topology spread constraints". The event message points directly at which constraint can't be satisfied.

---

## 13. Best Practices

- Set **requests near measured usage** (use VPA recommendations/Goldilocks); they drive packing, QoS, and HPA.
- Keep **memory limits**; be cautious with **CPU limits** on latency-sensitive Pods.
- **Spread replicas** across zones and nodes with topology spread constraints; add **PodDisruptionBudgets**.
- Use **HPA** with a stabilization window to avoid flapping; pick a metric that reflects real load.
- Adopt **Karpenter** for cost-efficient, right-sized nodes; blend spot with interruption handling.
- Use **KEDA scale-to-zero** for bursty/queue workloads to eliminate idle cost.
- **Dedicate special nodes** (GPU/spot) with taints; don't let general Pods squat on them.
- Never run **VPA and HPA on the same metric**.

---

## 14. Case Study

### Scenario: Cloud bill doubled and a routine upgrade caused an outage

A platform team's EKS bill jumps 2x over a quarter while traffic is flat, and a routine Kubernetes version upgrade (which drains nodes) caused a 4-minute outage of the checkout service.

```
Two problems, one root cause: bad resource hygiene + no disruption protection.

Cost: every team copy-pasted requests: cpu 1, memory 2Gi -- regardless of real use
   -> nodes "full" at ~30% real utilization -> Cluster Autoscaler keeps adding nodes
   -> 2x the nodes needed.

Outage: checkout had 3 replicas, no PDB, no topology spread
   -> upgrade drained a node holding 2 of 3 replicas, then the 3rd's node next
   -> momentarily 0 ready replicas -> checkout down 4 minutes mid-upgrade.
```

```yaml
# BROKEN: over-requested, no spread, no PDB.
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: checkout
          resources: {requests: {cpu: "1", memory: 2Gi}, limits: {cpu: "1", memory: 2Gi}}  # uses ~250m/600Mi
      # no topologySpreadConstraints, no PDB
```

```yaml
# FIX: right-size requests (VPA-informed), spread across AZs, protect with a PDB,
# and switch node scaling to Karpenter for right-sized, consolidating nodes.
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: checkout
          resources:
            requests: {cpu: 300m, memory: 768Mi}   # measured headroom over real usage
            limits:   {memory: 1Gi}                 # memory cap; no CPU limit (latency)
      topologySpreadConstraints:
        - {maxSkew: 1, topologyKey: topology.kubernetes.io/zone, whenUnsatisfiable: DoNotSchedule, labelSelector: {matchLabels: {app: checkout}}}
---
apiVersion: policy/v1
kind: PodDisruptionBudget
spec: {minAvailable: 2, selector: {matchLabels: {app: checkout}}}
```

**Outcome metrics:**

| Metric | Before | After |
|--------|--------|-------|
| Node count (flat traffic) | ~80 | ~40 |
| Avg node CPU utilization | ~30% | ~60% |
| Monthly compute cost | 2x baseline | ~1.05x baseline |
| Upgrade-time outage | 4 min | 0 (PDB held 2 replicas; drains waited) |

Karpenter consolidation plus honest requests halved the fleet; the PDB + topology spread made node drains non-disruptive.

**Discussion questions:**
1. Why did over-requesting double the node count even though real utilization was 30%? (Scheduler packs on requests, not usage.)
2. How does a PDB interact with `kubectl drain` to prevent the outage?
3. What's the risk of setting requests *too* tight after right-sizing, and how does VPA help calibrate? (Throttling/OOM under spikes; VPA recommends from observed peaks.)

---

**Cross-references:** [kubernetes_workloads_and_objects](../kubernetes_workloads_and_objects/) (requests/limits/QoS, PDB), [linux_and_os_fundamentals](../linux_and_os_fundamentals/) (cgroup throttling/OOM behind limits), [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/) (spot, rightsizing, consolidation), [kubernetes_storage_and_state](../kubernetes_storage_and_state/) (volume topology constraints), [`case_studies/cross_cutting/kubernetes_production_hardening.md`](../case_studies/cross_cutting/kubernetes_production_hardening.md).
