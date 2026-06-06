# Kubernetes Production Hardening

> Cross-Cutting Primitive — DevOps Case Studies · Difficulty: Advanced

---

## 1. Concept Overview

Kubernetes production hardening is the discipline of configuring workloads so that a single misbehaving pod, a node failure, a noisy tenant, or a compromised container cannot take down the cluster, starve neighbors, or escalate privileges. The Kubernetes defaults are deliberately permissive — a pod with no resource limits, no probes, no securityContext, and no PodDisruptionBudget will schedule and run perfectly in a demo, then page you at 3 a.m. in production.

Hardening spans four layers that map directly to the failure modes seen in incidents:

- **Resource governance** — requests/limits, QoS classes, and the OOMKill mechanics that decide which pod the kernel kills under memory pressure.
- **Health signaling** — liveness, readiness, and startup probes that tell the kubelet and the Service endpoints controller whether a pod should receive traffic or be restarted.
- **Availability under disruption** — PodDisruptionBudgets, topologySpreadConstraints, and graceful shutdown so that node drains, rolling updates, and autoscaler scale-downs don't drop requests.
- **Security posture** — securityContext, Pod Security Standards (baseline/restricted), seccomp, dropped capabilities, and multi-tenant isolation via ResourceQuota, LimitRange, and default-deny NetworkPolicy.

This file is the shared reference that the DevOps case studies — `design_kubernetes_platform`, `design_autoscaling_platform`, `design_internal_developer_platform` — link to instead of re-explaining QoS, probes, and PDBs in each. For the broader RBAC, admission, and runtime threat model, cross-reference [`../../kubernetes_security/README.md`](../../kubernetes_security/README.md); for HPA/VPA/Cluster Autoscaler interactions, cross-reference [`../../kubernetes_scheduling_and_autoscaling/README.md`](../../kubernetes_scheduling_and_autoscaling/README.md).

---

## 2. Intuition

> **One-line analogy**: A hardened pod is a tenant in a well-run apartment building — it has a metered utility cap (limits), a doorbell the landlord can ring to check it's alive (probes), a lease clause limiting how many units the landlord can renovate at once (PDB), and locks on its own door so neighbors can't wander in (NetworkPolicy + securityContext).

**Mental model**: Every hardening knob exists because some component — the kernel OOM killer, the kubelet, the scheduler, the endpoints controller, or the CNI — makes an automatic decision about your pod. If you don't supply the inputs (requests, probes, budgets, policies), those components fall back to defaults that optimize for "schedule anything, anywhere" rather than "protect the rest of the cluster."

**Why it matters**: An unbounded pod can consume a node's entire memory and trigger an OOM cascade that evicts Guaranteed neighbors. A missing readiness probe sends traffic to a pod that's still loading caches, producing 503s during every deploy. A missing PDB lets a routine `kubectl drain` take all 3 replicas of a service offline at once. These are not edge cases — they are the most common Kubernetes incidents in production.

**Key insight**: **QoS class is not something you set — it is derived from how you set requests and limits, and it silently determines who dies first under pressure.** A pod where every container has `requests == limits` for both CPU and memory becomes Guaranteed and is the last to be OOM-evicted; a pod with no requests at all becomes BestEffort and is the first to go. Most teams discover their critical service was BestEffort only after the kernel kills it.

---

## 3. Core Principles

1. **Every container declares memory requests and limits.** Memory is incompressible — a container that exceeds its limit is OOMKilled, not throttled. Without a request, the scheduler can overcommit a node and the kernel decides the casualties.

2. **CPU limits are usually a trap; CPU requests are mandatory.** CPU is compressible (throttled, not killed). A CPU limit causes CFS throttling that can add tail latency even when the node is idle. Set requests for scheduling fairness; set limits only when you have a hard multi-tenant SLA reason.

3. **Probes signal three distinct questions.** `startup` = "is initialization done?", `readiness` = "should I get traffic right now?", `liveness` = "am I wedged and need a restart?". Conflating them — e.g., using a heavy DB check as the liveness probe — causes restart storms.

4. **Availability is a budget, not an absolute.** PodDisruptionBudgets cap *voluntary* disruptions (drains, rollouts). They do not protect against node crashes (involuntary). Spread (topologySpreadConstraints) protects against involuntary loss.

5. **Default-deny everything, then allow explicitly.** Networking, capabilities, and filesystem access should start closed. `NetworkPolicy` default-deny, `drop: ["ALL"]` capabilities, `readOnlyRootFilesystem: true`.

6. **Multi-tenancy needs three walls.** ResourceQuota caps a namespace's total consumption, LimitRange supplies defaults and per-pod caps, NetworkPolicy isolates traffic. Missing any one lets a tenant escape its blast radius.

7. **Shutdown is a protocol, not an event.** SIGTERM → drain in-flight → close listeners → exit before `terminationGracePeriodSeconds`. Pods that ignore SIGTERM get SIGKILLed and drop connections.

---

## 4. Types / Architectures / Strategies

**QoS classes** (derived, not declared):

| QoS Class | Condition | Eviction order under node memory pressure |
|-----------|-----------|-------------------------------------------|
| Guaranteed | Every container: `requests == limits` for both CPU and memory | Evicted last |
| Burstable | At least one container has a request or limit, but not Guaranteed | Evicted second |
| BestEffort | No container sets any request or limit | Evicted first |

**Probe types**:

- **startupProbe** — gates liveness/readiness until the app is up. Use for slow JVM/cache warmups. While it runs, liveness is suppressed (no premature restarts).
- **readinessProbe** — adds/removes the pod from Service endpoints. Failing it removes traffic without restarting.
- **livenessProbe** — restarts the container on failure. Reserve for deadlock/wedge detection.

**Disruption controls**:

- **PodDisruptionBudget** — `minAvailable` or `maxUnavailable` caps voluntary evictions.
- **topologySpreadConstraints** — spreads replicas across zones/nodes by `maxSkew`.
- **terminationGracePeriodSeconds + preStop** — graceful drain.

**Security tiers** (Pod Security Standards):

- **privileged** — unrestricted (e.g., system pods).
- **baseline** — blocks known privilege escalations (no hostNetwork, no privileged, no hostPath).
- **restricted** — hardened: runAsNonRoot, drop ALL capabilities, seccomp RuntimeDefault, no privilege escalation.

**Multi-tenant isolation strategies**: soft (namespace + quota + NetworkPolicy on shared nodes) vs hard (separate node pools / virtual clusters / separate clusters).

---

## 5. Architecture Diagrams

```
                    NODE MEMORY PRESSURE -> kubelet eviction + kernel OOM
                    ===================================================

  Node: 16 GiB allocatable                      Eviction / OOM kill order
  +------------------------------------------+   (worst victim first)
  |  BestEffort pod  (no requests/limits)    |  <-- 1st killed
  |  Burstable pod   (req 512Mi, lim 2Gi)    |  <-- 2nd, if over request
  |  Burstable pod   (req 1Gi,  no limit)    |
  |  Guaranteed pod  (req==lim 4Gi)          |  <-- last killed
  +------------------------------------------+
       |                         |
       | kubelet soft eviction   | kernel cgroup OOM (container > mem limit)
       | (node-level pressure)   | -> only that container's process killed
       v                         v
   evict whole pod          OOMKilled, restart per restartPolicy

  ----------------------------------------------------------------------

       PROBE STATE MACHINE (one container)
       ====================================

   container start
        |
        v
   [ startupProbe ]  --fail x failureThreshold--> RESTART
        | success
        v
   liveness + readiness now active
        |
        +--> [ readinessProbe ] --fail--> remove from Service endpoints
        |                          (no traffic, NO restart)
        |
        +--> [ livenessProbe ]  --fail x failureThreshold--> RESTART container

  ----------------------------------------------------------------------

       GRACEFUL SHUTDOWN TIMELINE (terminationGracePeriodSeconds: 30)
       ==============================================================

   t=0   Pod marked Terminating
         |-- endpoints controller removes pod from Service (async!)
         |-- preStop hook runs (e.g., sleep 5)  ----+
         |                                          | overlap absorbs
   t=5   SIGTERM sent to PID 1 <--------------------+ endpoint propagation
         |-- app stops accepting new conns, drains in-flight
   t<30  app exits cleanly  -> done
   t=30  if still running -> SIGKILL (connections dropped)
```

---

## 6. How It Works — Detailed Mechanics

**OOMKill mechanics.** Each container's memory limit becomes a cgroup `memory.max` (cgroup v2) / `memory.limit_in_bytes` (v1). When the container's resident memory exceeds the limit, the kernel's OOM killer fires *inside that cgroup* and kills the largest process — usually PID 1 — producing exit code 137 (128 + SIGKILL 9). This is per-container and unrelated to node pressure. Separately, when the *node* runs low on allocatable memory, the kubelet evicts whole pods in QoS+priority order (BestEffort → Burstable over request → Guaranteed). A Guaranteed pod can still be OOMKilled if its own process exceeds its own limit.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders-api
spec:
  replicas: 3
  template:
    spec:
      terminationGracePeriodSeconds: 30   # default is 30
      containers:
        - name: app
          image: registry.example.com/orders-api:1.8.2
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              memory: "1Gi"        # memory req==lim ...
              # NOTE: no CPU limit on purpose (avoid CFS throttling)
          startupProbe:
            httpGet: { path: /healthz/startup, port: 8080 }
            periodSeconds: 5
            failureThreshold: 30   # allows 150s for warmup before liveness kicks in
          readinessProbe:
            httpGet: { path: /healthz/ready, port: 8080 }
            periodSeconds: 10
            failureThreshold: 3    # default 3
            timeoutSeconds: 2
          livenessProbe:
            httpGet: { path: /healthz/live, port: 8080 }
            periodSeconds: 10
            failureThreshold: 3
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sh", "-c", "sleep 5"]  # absorb endpoint removal lag
```

Because CPU is set on requests only and memory request equals limit, this pod is **Burstable** (CPU has request but no limit). To make it Guaranteed you would also set `cpu` under `limits` equal to its request — accepting potential CFS throttling.

**Probe timing math.** Time-to-restart on liveness failure = `initialDelaySeconds + (failureThreshold × periodSeconds)`. With the defaults above (no initialDelay, failureThreshold 3, periodSeconds 10), a wedged container is restarted ~30 seconds after it stops responding. Readiness removal from endpoints uses the same formula; with periodSeconds 10 / failureThreshold 3 a pod stops receiving new traffic ~30s after it goes unready.

**Graceful shutdown in Go** — the app must trap SIGTERM:

```go
func main() {
    srv := &http.Server{Addr: ":8080", Handler: router()}
    go func() { _ = srv.ListenAndServe() }()

    stop := make(chan os.Signal, 1)
    signal.Notify(stop, syscall.SIGTERM)
    <-stop // SIGTERM from kubelet

    // Stop accepting new requests, drain in-flight up to 25s
    ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
    defer cancel()
    _ = srv.Shutdown(ctx) // returns when in-flight requests finish or ctx expires
}
```

The 25s drain budget sits inside the 30s `terminationGracePeriodSeconds` minus the 5s `preStop sleep`, leaving margin before SIGKILL.

**PodDisruptionBudget** caps voluntary evictions:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: orders-api-pdb
spec:
  maxUnavailable: 1          # at most 1 of 3 replicas down during drains/rollouts
  selector:
    matchLabels: { app: orders-api }
```

A `kubectl drain` on a node hosting an `orders-api` pod blocks (the eviction API returns 429) if evicting it would push availability below the budget, until another replica reschedules elsewhere.

**Spread across zones**:

```yaml
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels: { app: orders-api }
```

With 3 replicas across 3 zones and `maxSkew: 1`, the scheduler refuses to place 2 replicas in one zone while another zone has 0 — so a single-AZ outage loses at most 1 of 3.

**The 110 pods/node default.** The kubelet `--max-pods` defaults to **110**. On AWS VPC-CNI the practical ceiling is also bounded by ENI IP capacity (e.g., an `m5.large` supports ~29 IPs without prefix delegation). Scheduling more pods than IPs available leaves pods `ContainerCreating` with `failed to assign an IP address`.

---

## 7. Real-World Examples

- **Netflix** runs CPU-request-only (no CPU limits) on most services to avoid CFS throttling tail latency, relying on requests for scheduling fairness — a widely-cited stance after the "CPU throttling even when under-utilized" cgroup v1 bug (kernel quota accounting) caused p99 spikes.

- **Datadog's** public Kubernetes incidents include a 2023 outage where a systemd/cgroup upgrade interacted badly with node-level networking; their postmortem reinforced node-pool isolation and aggressive PDBs to bound blast radius during fleet-wide changes.

- **A typical SaaS multi-tenant cluster** assigns each customer a namespace with a `ResourceQuota` (e.g., 50 CPU, 100Gi memory, 20 pods), a `LimitRange` supplying default 100m/256Mi requests, and a default-deny `NetworkPolicy` — preventing one tenant's runaway batch job from starving the API namespace.

- **Spotify's Backstage-driven platform** enforces the `restricted` Pod Security Standard at the namespace level via the built-in Pod Security Admission, blocking pods that run as root or request privileged at admission time rather than after the fact.

- **EKS at scale**: teams hitting the VPC-CNI IP ceiling enable prefix delegation (`ENABLE_PREFIX_DELEGATION=true`) to raise per-node pod density toward the 110 default instead of being capped at ~29-58 IPs per ENI.

---

## 8. Tradeoffs

| Decision | Option A | Option B | When A wins | When B wins |
|----------|----------|----------|-------------|-------------|
| CPU limits | Set CPU limit (Guaranteed-capable) | CPU request only | Hard multi-tenant SLA, billing isolation | Latency-sensitive service; avoid CFS throttling |
| QoS target | Guaranteed (req==lim) | Burstable | Critical, predictable footprint | Bursty workloads, better bin-packing |
| Liveness probe | Aggressive (low failureThreshold) | Conservative / none | Detect true deadlocks fast | App that pauses under GC; avoid restart storms |
| PDB | maxUnavailable: 1 | minAvailable: 51% | Small replica sets | Large fleets where % scales |
| Tenant isolation | Soft (namespace + quota) | Hard (node pool / cluster) | Cost-sensitive, trusted tenants | Compliance, untrusted/hostile tenants |
| Pod density | Raise --max-pods toward 110 | Keep conservative | Cost optimization, small pods | Large pods, IP/ENI constraints |
| Spread | DoNotSchedule (strict) | ScheduleAnyway (soft) | HA is non-negotiable | Capacity scarce, availability > spread |

---

## 9. When to Use / When NOT to Use

**Use full hardening (all four layers) when:**
- The workload is customer-facing or revenue-impacting.
- The cluster is multi-tenant or shared across teams.
- You run on spot/preemptible nodes where involuntary disruption is frequent.
- Compliance (SOC 2, PCI, FedRAMP) requires PSS `restricted` and NetworkPolicy.

**Relax specific knobs when:**
- **Skip CPU limits** for latency-sensitive single-tenant services to avoid CFS throttling.
- **Skip liveness probes** (keep readiness) for apps prone to long GC pauses — a liveness restart storm is worse than a temporarily slow pod.
- **Skip PDBs** for stateless single-replica dev/test workloads where downtime is acceptable.

**Do NOT over-harden when:**
- A short-lived Job/CronJob doesn't need a PDB or readiness probe.
- A genuinely trusted internal batch cluster doesn't need per-namespace NetworkPolicy if a cluster-wide default-deny would block legitimate cross-namespace pipelines without security benefit.
- Setting `readOnlyRootFilesystem: true` on a legacy app that writes to `/tmp` without an emptyDir mount — fix the mount first, don't disable the control.

---

## 10. Common Pitfalls

1. **No memory limit → node OOM cascade.** A leaking pod consumes node memory; kubelet evicts BestEffort/Burstable neighbors including innocent ones.

2. **Liveness probe doing a deep dependency check.** When the DB is slow, every pod fails liveness and restarts simultaneously, removing all capacity right when you need it.

3. **Readiness probe missing → 503s on every deploy.** New pods join Service endpoints before caches warm.

4. **PDB with `maxUnavailable: 0` deadlocks drains.** No pod can ever be evicted; node maintenance hangs forever.

5. **Forgetting the endpoint-removal race.** SIGTERM arrives before the endpoints controller finishes removing the pod, so in-flight requests hit a closing server. The `preStop sleep 5` absorbs this.

**BROKEN → FIX**: a "Guaranteed critical service" that is actually BestEffort and OOM-evicted first.

```yaml
# BROKEN: ops believes this is protected, but it sets NO requests/limits.
# QoS = BestEffort -> kubelet evicts it FIRST under node memory pressure,
# and it has no readiness probe -> serves 503s during rollout.
apiVersion: apps/v1
kind: Deployment
metadata: { name: payments-api }
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: app
          image: payments-api:2.1
          livenessProbe:
            httpGet: { path: /db-check, port: 8080 }  # deep DB check as LIVENESS
            periodSeconds: 5
            failureThreshold: 2
          # no requests, no limits, no readiness probe
```

```yaml
# FIX: explicit memory req==lim (Guaranteed-capable), CPU request only,
# readiness gates traffic, liveness is a cheap self-check (no DB),
# startup probe covers warmup so liveness never fires during boot.
apiVersion: apps/v1
kind: Deployment
metadata: { name: payments-api }
spec:
  replicas: 3
  template:
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: app
          image: payments-api:2.1
          resources:
            requests: { cpu: "500m", memory: "1Gi" }
            limits:   { cpu: "500m", memory: "1Gi" }   # -> Guaranteed QoS
          startupProbe:
            httpGet: { path: /healthz/startup, port: 8080 }
            periodSeconds: 5
            failureThreshold: 24      # 120s warmup budget
          readinessProbe:
            httpGet: { path: /healthz/ready, port: 8080 } # checks DB pool here
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet: { path: /healthz/live, port: 8080 }  # cheap, no deps
            periodSeconds: 10
            failureThreshold: 3
```

---

## 11. Technologies & Tools

| Tool | Layer | What it enforces / detects | Notes |
|------|-------|----------------------------|-------|
| Pod Security Admission (built-in) | Security | baseline/restricted PSS at namespace label | Replaced PodSecurityPolicy (removed 1.25) |
| Kyverno | Policy | Mutating/validating policies as CRDs (require limits, drop caps) | YAML-native, no Rego |
| OPA Gatekeeper | Policy | ConstraintTemplates in Rego | More expressive, steeper curve |
| Goldilocks (VPA-backed) | Resources | Recommends right-sized requests/limits | Surfaces over/under-provisioning |
| kube-bench | Security | CIS Kubernetes Benchmark checks | Node/control-plane hardening audit |
| Polaris | Audit | Scores workloads on probes, limits, securityContext | CI gate or dashboard |

`kubectl describe pod` shows the derived `QoS Class:` field; `kubectl get pod -o jsonpath='{.status.qosClass}'` reads it programmatically.

---

## 12. Interview Questions with Answers

**Q: How is a pod's QoS class determined, and why does it matter?**
QoS is derived, not declared: Guaranteed requires every container to set `requests == limits` for both CPU and memory; BestEffort sets none; everything else is Burstable. It matters because the kubelet evicts pods in BestEffort → Burstable (over request) → Guaranteed order under node memory pressure, so QoS silently decides who dies first. Make your critical services Guaranteed (or at least Burstable with a generous memory request) so they survive node pressure.

**Q: What is the difference between an OOMKill and a kubelet eviction?**
An OOMKill is per-container: the kernel's cgroup OOM killer fires when that container's memory exceeds its own limit, producing exit code 137, independent of node state. A kubelet eviction is node-level: when allocatable memory runs low, the kubelet evicts whole pods in QoS order to reclaim memory. A Guaranteed pod is immune to eviction-due-to-others but can still OOMKill itself if its process exceeds its own limit, so right-sizing the limit still matters.

**Q: Why might you deliberately omit CPU limits?**
Because CPU is compressible and a CPU limit triggers CFS (Completely Fair Scheduler) throttling that can add tail latency even when the node has idle cores, due to how the kernel accounts quota in 100ms periods. Netflix and others found p99 spikes from this on cgroup v1. Set CPU requests for scheduling fairness and omit limits for latency-sensitive services unless a hard multi-tenant SLA requires capping.

**Q: When should a liveness probe be used versus a readiness probe?**
Use readiness to control traffic (failing it removes the pod from Service endpoints without restarting) and liveness only to recover from unrecoverable wedges like deadlocks (failing it restarts the container). The classic mistake is putting a dependency check in liveness — when the DB is slow, every pod fails liveness and restarts at once, deleting all capacity. Keep liveness cheap and dependency-free; put dependency checks in readiness.

**Q: What is a startup probe for, and what problem does it solve?**
A startup probe gates liveness and readiness until the app finishes initializing, with its own generous `failureThreshold × periodSeconds` budget. It solves the conflict where slow-starting apps (JVM warmup, large cache loads) would otherwise be killed by an aggressive liveness probe during boot. Use it to give, say, 120s of warmup (`failureThreshold: 24`, `periodSeconds: 5`) without slackening liveness for the steady state.

**Q: What does a PodDisruptionBudget protect against, and what does it NOT protect against?**
A PDB caps *voluntary* disruptions — node drains, rolling updates, autoscaler scale-downs — by making the eviction API refuse to drop below `minAvailable` (or above `maxUnavailable`). It does NOT protect against *involuntary* disruptions like node crashes, kernel panics, or spot reclamation, which bypass the eviction API. Pair PDBs (voluntary) with topologySpreadConstraints across zones (involuntary) for full coverage.

**Q: A `kubectl drain` is hanging forever. What PDB misconfiguration causes this?**
A PDB with `maxUnavailable: 0` or `minAvailable` equal to the current replica count makes every eviction illegal, so the drain blocks indefinitely returning 429 on the eviction API. The fix is to allow at least one disruption (`maxUnavailable: 1`) and ensure enough replicas/capacity exist for a pod to reschedule before the next is evicted. Also check that the PDB selector actually matches running, schedulable replicas.

**Q: Walk through what happens between SIGTERM and SIGKILL during pod termination.**
At t=0 the pod is marked Terminating; the endpoints controller asynchronously removes it from Service endpoints while the preStop hook runs. After preStop, the kubelet sends SIGTERM to PID 1; the app should stop accepting new connections and drain in-flight work. If the process hasn't exited by `terminationGracePeriodSeconds` (default 30), the kubelet sends SIGKILL and connections are dropped — so add a short `preStop sleep` to absorb the endpoint-removal race and keep your drain budget under the grace period.

**Q: Why add a `preStop: sleep 5` if the app already handles SIGTERM?**
Because endpoint removal is asynchronous and races with SIGTERM — load balancers and kube-proxy may still send new requests for a second or two after termination begins. The `sleep` delays SIGTERM so the pod keeps serving while endpoint removal propagates, eliminating the burst of connection-refused errors at the start of termination. It's a cheap, app-agnostic fix for the most common cause of deploy-time 5xx spikes.

**Q: What are the three Pod Security Standards levels and what does `restricted` enforce?**
The levels are privileged (unrestricted), baseline (blocks known escalations like hostNetwork, privileged, hostPath), and restricted (hardened). Restricted additionally requires `runAsNonRoot: true`, `allowPrivilegeEscalation: false`, dropping ALL capabilities, seccomp `RuntimeDefault`, and a non-writable root filesystem posture. Enforce it via the namespace label `pod-security.kubernetes.io/enforce: restricted` so violating pods are rejected at admission.

**Q: How do you isolate tenants on a shared cluster?**
Use three walls per namespace: a ResourceQuota caps total CPU/memory/object counts so one tenant can't exhaust the cluster, a LimitRange supplies default requests/limits and per-pod maximums so unbounded pods can't be created, and a default-deny NetworkPolicy blocks cross-namespace traffic until explicitly allowed. For untrusted or compliance-bound tenants, escalate to hard isolation with separate node pools or clusters because shared kernels are a residual risk.

**Q: What does a default-deny NetworkPolicy look like and why start there?**
It's a NetworkPolicy selecting all pods (`podSelector: {}`) with empty `ingress`/`egress` rules, which drops all traffic not explicitly allowed by another policy. You start there because Kubernetes networking is allow-all by default — any pod can reach any pod cluster-wide — so without default-deny a compromised pod can scan and pivot freely. Layer specific allow policies on top for the connections each service legitimately needs.

**Q: Why does `readOnlyRootFilesystem: true` break some apps and how do you fix it correctly?**
It breaks apps that write to the container filesystem (logs, temp files, PID files) because the root filesystem becomes immutable. The correct fix is to mount writable `emptyDir` volumes at the specific paths the app needs (`/tmp`, cache dirs) rather than disabling the control, which preserves immutability everywhere else. This narrows the attacker's ability to drop binaries or tamper with the image at runtime.

**Q: What is the 110 pods-per-node default and when does it actually bite you?**
The kubelet's `--max-pods` defaults to 110, but the real ceiling is often lower due to networking: AWS VPC-CNI binds pod IPs to ENI capacity, so an instance might cap at ~29-58 IPs before prefix delegation. It bites when pods get stuck in ContainerCreating with "failed to assign an IP address" despite available CPU/memory. Enable VPC-CNI prefix delegation (`ENABLE_PREFIX_DELEGATION=true`) or choose larger instances to reach the 110 default.

**Q: How do topologySpreadConstraints differ from pod anti-affinity for HA?**
topologySpreadConstraints declaratively bound skew across a topology domain (e.g., `maxSkew: 1` across zones), which scales cleanly as replicas grow and the scheduler optimizes placement. Pod anti-affinity is a harder constraint expressed pairwise that can become unsatisfiable and leave pods Pending as replica count rises. Prefer topologySpreadConstraints with `whenUnsatisfiable: DoNotSchedule` for zone HA, falling back to `ScheduleAnyway` only when capacity is scarce.

---

## 13. Best Practices

1. **Always set memory requests AND limits; set CPU requests but be deliberate about CPU limits.** Memory unbounded is the top cause of node OOM cascades.
2. **Make the QoS class explicit and intentional** — verify with `kubectl get pod -o jsonpath='{.status.qosClass}'` rather than assuming.
3. **Three probes, three jobs.** Startup for warmup, readiness for traffic, liveness for wedges. Keep liveness dependency-free.
4. **Every multi-replica workload gets a PDB** with `maxUnavailable: 1` (small sets) or a percentage (large fleets).
5. **Spread across zones** with topologySpreadConstraints `maxSkew: 1`, `DoNotSchedule` for critical services.
6. **Handle SIGTERM in code + add `preStop: sleep 5`**, keep drain budget under `terminationGracePeriodSeconds`.
7. **Default-deny NetworkPolicy per namespace**, then allow explicitly.
8. **Enforce PSS `restricted`** via namespace labels; back it with Kyverno/Gatekeeper for the rules PSS doesn't cover (e.g., "must set resource limits").
9. **Per-tenant ResourceQuota + LimitRange + NetworkPolicy** — all three, not one.
10. **Audit continuously** with Polaris/kube-bench in CI; gate merges on a minimum hardening score.

---

## 14. Case Study

**Scenario**: A fintech runs a `ledger-api` (3 replicas) on a shared EKS cluster with mixed-tenant batch jobs. During a nightly batch run, a memory-leaking analytics pod (BestEffort) climbed to 12 GiB on an `m5.xlarge` (16 GiB allocatable). The kubelet hit memory pressure and evicted pods. Because `ledger-api` had requests but no limits and no PDB, two of its three replicas were evicted simultaneously during the same eviction sweep, and a concurrent `kubectl drain` for a node upgrade took the third — a full outage of a regulated service for 90 seconds, violating a 99.95% SLA and triggering a reconciliation backlog.

**Root cause**: `ledger-api` was Burstable-without-limit (evictable), had no PDB (drain took the last replica), and the analytics namespace had no ResourceQuota (the leak was unbounded).

```yaml
# BROKEN: ledger-api as-deployed during the incident.
apiVersion: apps/v1
kind: Deployment
metadata: { name: ledger-api, namespace: ledger }
spec:
  replicas: 3
  template:
    metadata: { labels: { app: ledger-api } }
    spec:
      containers:
        - name: app
          image: ledger-api:4.2
          resources:
            requests: { cpu: "500m", memory: "2Gi" }
            # no memory limit -> Burstable, evictable under node pressure
          # no readiness probe, no PDB elsewhere, neighbor namespace uncapped
```

```yaml
# FIX 1: ledger-api -> Guaranteed QoS + readiness + spread.
apiVersion: apps/v1
kind: Deployment
metadata: { name: ledger-api, namespace: ledger }
spec:
  replicas: 3
  template:
    metadata: { labels: { app: ledger-api } }
    spec:
      terminationGracePeriodSeconds: 30
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: DoNotSchedule
          labelSelector: { matchLabels: { app: ledger-api } }
      containers:
        - name: app
          image: ledger-api:4.2
          resources:
            requests: { cpu: "1", memory: "2Gi" }
            limits:   { cpu: "1", memory: "2Gi" }   # Guaranteed -> evicted last
          readinessProbe:
            httpGet: { path: /healthz/ready, port: 8080 }
            periodSeconds: 10
            failureThreshold: 3
          lifecycle:
            preStop: { exec: { command: ["/bin/sh","-c","sleep 5"] } }
---
# FIX 2: PDB protects against the concurrent drain.
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata: { name: ledger-api-pdb, namespace: ledger }
spec:
  maxUnavailable: 1
  selector: { matchLabels: { app: ledger-api } }
---
# FIX 3: cap the noisy neighbor so a leak can't take a node down.
apiVersion: v1
kind: ResourceQuota
metadata: { name: analytics-quota, namespace: analytics }
spec:
  hard:
    requests.memory: "8Gi"
    limits.memory: "8Gi"
    pods: "20"
---
apiVersion: v1
kind: LimitRange
metadata: { name: analytics-limits, namespace: analytics }
spec:
  limits:
    - type: Container
      default:        { memory: "512Mi", cpu: "500m" }   # cap unbounded pods
      defaultRequest: { memory: "256Mi", cpu: "100m" }
      max:            { memory: "2Gi" }
```

**Outcome**: ledger-api became Guaranteed (survives node pressure), the PDB blocked the drain from taking the second replica until a replacement scheduled in another zone, and the analytics namespace was capped at 8Gi total so the leak self-OOMKilled inside its own quota instead of starving the node. The next quarter saw zero eviction-driven outages of regulated services.

For the admission-control and RBAC layers that prevented future un-hardened deployments, see [`../../kubernetes_security/README.md`](../../kubernetes_security/README.md); for how the cluster autoscaler interacts with PDBs during scale-down, see [`../../kubernetes_scheduling_and_autoscaling/README.md`](../../kubernetes_scheduling_and_autoscaling/README.md).
