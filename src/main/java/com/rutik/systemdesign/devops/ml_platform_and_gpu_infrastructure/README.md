# ML Platform & GPU Infrastructure

> Phase 8 — Specialized Platforms & Performance · Difficulty: Advanced · Q&A target: 15

Running GPUs on Kubernetes for training and inference: the NVIDIA GPU Operator, the device plugin, MIG and time-slicing for fractional sharing, Karpenter GPU NodePools, and orchestrating Kubeflow / Ray workloads on a multi-tenant cluster. This module owns the **operate-it** angle — how the platform team keeps expensive accelerators busy, isolated, and observable. For the *modeling* side (training loops, serving frameworks, model design) cross-reference [`../../ml/`](../../ml/) and [`../../llm/inference_engines/`](../../llm/inference_engines/); for the *application architecture* of an inference service see [`../../llm/case_studies/design_gpu_inference_platform.md`](../../llm/case_studies/design_gpu_inference_platform.md).

---

## 1. Concept Overview

An **ML platform** is the internal product that lets data scientists and ML engineers train, tune, and serve models without each team reinventing GPU provisioning, scheduling, and observability. **GPU infrastructure** is its hardest substrate: GPUs are expensive (an 8×A100 `p4d.24xlarge` lists at ~$32.77/hour on-demand, ~$98k/month if left running), scarce (regional capacity is frequently exhausted), and awkward for Kubernetes because the scheduler models them as opaque, integer-countable **extended resources** rather than divisible CPU/memory.

The platform must solve four problems the default Kubernetes install does not:

1. **Exposing GPUs to the scheduler** — Linux + the kernel see a GPU as a `/dev/nvidia*` device, not a schedulable resource. The **NVIDIA device plugin** advertises `nvidia.com/gpu` so Pods can request it.
2. **Driver and toolkit lifecycle** — every GPU node needs a matching NVIDIA kernel driver, the container toolkit, NVML, DCGM, and (optionally) MIG configuration. The **GPU Operator** installs and reconciles all of this so nodes are not hand-built.
3. **Sharing one GPU across workloads** — a single inference request may use 5% of an A100. Native K8s gives a Pod the *whole* card. **MIG** (hardware partitioning) and **time-slicing** (oversubscription) reclaim that waste.
4. **Elastic, cost-aware capacity** — GPU nodes should appear when a training job is queued and disappear when idle. **Karpenter** (or Cluster Autoscaler) provisions GPU instance types on demand, preferring Spot for fault-tolerant training.

On top of that substrate sit the workload orchestrators — **Kubeflow** (pipelines, training operators, KServe for serving), **Ray** (distributed Python: Ray Train, Ray Serve, Ray Tune), and **Volcano** / **Kueue** (batch gang-scheduling) — which translate "train this model on 16 GPUs" into Pods the scheduler can place.

---

## 2. Intuition

> A GPU cluster is a **valet parking garage for supercars**. Each car (GPU) is worth a fortune and must never sit idle in a spot it doesn't need. MIG is *painting smaller parking lines inside one bay* so seven compact cars fit where one SUV used to park. Time-slicing is *valets double-parking* — more cars than bays, shuffled in and out fast, betting they're not all moving at once. Karpenter is *opening and closing whole floors of the garage* as demand rises and falls.

**Mental model:** Kubernetes treats a GPU like a forklift, not like RAM — you check out the whole machine, integer count only, no "give me 0.3 of a forklift." Every advanced technique here is a workaround for that integer-counting limitation, trading isolation strength for utilization.

**Why it matters:** GPU spend is often the single largest line item in an ML org's cloud bill — frequently 60–80% of it. A platform that runs GPUs at 35% utilization instead of 70% is, in dollar terms, paying double for the same throughput. Utilization is the product.

**Key insight:** The two levers — **isolation** and **utilization** — pull against each other. MIG gives hardware-enforced isolation but fixed-size partitions and wasted fragments; time-slicing gives flexible packing but zero memory isolation (one Pod's OOM can crash a co-tenant). Choosing between them per workload class *is* the core platform design decision.

---

## 3. Core Principles

1. **GPUs are extended resources, not divisible like CPU.** You cannot request `nvidia.com/gpu: 0.5` natively. Fractional sharing requires MIG (the device plugin advertises `nvidia.com/mig-1g.10gb`) or time-slicing (it advertises N replicas of `nvidia.com/gpu`).
2. **The node must be fully provisioned before a GPU Pod schedules.** Driver → container toolkit → device plugin → (DCGM exporter, MIG config). The GPU Operator sequences this; until the device plugin reports capacity, the node has `nvidia.com/gpu: 0` and the scheduler skips it.
3. **Training and serving have opposite resource profiles.** Training: long-running, bursty, fault-tolerant, wants whole GPUs (or many of them gang-scheduled) and tolerates Spot interruption. Serving: latency-sensitive, steady, wants fractional GPUs, must run on stable On-Demand capacity. Mixing them on one node pool causes one to starve the other.
4. **Gang scheduling is mandatory for distributed training.** A 16-GPU job needs all 16 Pods running *simultaneously* or none make progress. The default scheduler places Pods one at a time and will deadlock (partial placement holds GPUs hostage). Volcano/Kueue add all-or-nothing gang semantics.
5. **Utilization is measured, not assumed.** `nvidia.com/gpu` *allocation* (how many cards are checked out) is not *utilization* (how busy the SMs are). DCGM exports `DCGM_FI_DEV_GPU_UTIL` and `DCGM_FI_DEV_FB_USED`; a card can be 100% allocated and 4% utilized — that gap is wasted money.
6. **Topology matters for multi-GPU jobs.** GPUs on the same node connected by NVLink (600 GB/s on A100) move data ~10× faster than across PCIe or across nodes over the network. The scheduler should pack a multi-GPU Pod onto NVLink-connected devices, not scatter them.

---

## 4. Types / Architectures / Strategies

**GPU sharing strategies (the central design choice):**

| Strategy | Isolation | Granularity | Hardware needed | Best for |
|----------|-----------|-------------|-----------------|----------|
| **Exclusive (1 Pod = 1 GPU)** | Full | 1 whole GPU | Any | Training, heavy single-tenant inference |
| **MIG (Multi-Instance GPU)** | Hardware (memory + compute fault isolation) | Fixed slices (e.g., 1g.10gb, 2g.20gb, 3g.40gb on A100) | A100/H100/A30 only | Multi-tenant inference needing isolation |
| **Time-slicing** | None (shared context, no memory isolation) | N logical replicas per GPU | Any | Dev/test, bursty low-utilization inference, notebooks |
| **MPS (Multi-Process Service)** | Weak (shared memory, spatial SM partitioning) | Process-level | Any (Volta+) | Trusted co-located inference processes |

**Workload orchestration layers:**

| Layer | Tool | Role |
|-------|------|------|
| Batch / gang scheduling | Volcano, Kueue | All-or-nothing placement, queues, fair-share quotas |
| Training operators | Kubeflow Training Operator (PyTorchJob, TFJob), Ray Train | Launch + coordinate distributed worker Pods |
| Pipelines | Kubeflow Pipelines, Argo Workflows | DAG of preprocessing → train → eval → register |
| Serving | KServe, Ray Serve, NVIDIA Triton | Autoscaling model endpoints, scale-to-zero |
| Hyperparameter tuning | Katib, Ray Tune | Parallel trial orchestration |

**Capacity strategies:**

| Strategy | Mechanism | Tradeoff |
|----------|-----------|----------|
| Static GPU node pool | Fixed N GPU nodes always on | Simple, predictable, but pays for idle |
| Cluster Autoscaler GPU pool | Scale node group within ASG bounds | Slower scale-up (~minutes), per-instance-type pools |
| Karpenter GPU NodePools | Just-in-time, picks cheapest fitting instance | Fast, flexible instance selection, Spot-aware |
| Spot for training | Karpenter Spot capacity + checkpointing | ~70% cheaper, but interruptions need checkpoint/resume |

---

## 5. Architecture Diagrams

GPU platform on EKS, end to end:

```
                            ┌──────────────────────────────────────┐
                            │         ML Platform Control            │
                            │  Kubeflow Pipelines / Ray / Argo       │
                            │  Kueue (quotas) + Volcano (gang sched) │
                            └───────────────┬────────────────────────┘
                                            │ creates PyTorchJob / RayJob / InferenceService
                                            v
   ┌───────────────────────────── Kubernetes API + Scheduler ─────────────────────────────┐
   │  Pod requests:  resources.limits["nvidia.com/gpu"]: 1   (or mig-1g.10gb, etc.)        │
   └───────────────┬───────────────────────────────────────────────┬──────────────────────┘
                   │ scale-up: no GPU node fits                      │ Pod bound to node
                   v                                                 v
        ┌────────────────────┐                          ┌───────────────────────────────┐
        │     Karpenter      │  provisions              │   GPU Node (p4d.24xlarge)      │
        │  NodePool: gpu     │─────────────────────────>│  ┌──────────────────────────┐  │
        │  - g5/p4d/p5       │   EC2 (Spot or On-Demand)│  │ NVIDIA GPU Operator        │  │
        │  - taint: nvidia   │                          │  │  driver + container-toolkit│  │
        └────────────────────┘                          │  │  device-plugin (advertises │  │
                                                         │  │   nvidia.com/gpu)          │  │
                                                         │  │  DCGM exporter (metrics)   │  │
                                                         │  │  MIG manager (partitions)  │  │
                                                         │  └──────────────────────────┘  │
                                                         │   8 × A100 80GB, NVLink mesh    │
                                                         └───────────────┬─────────────────┘
                                                                         │ DCGM_FI_DEV_GPU_UTIL
                                                                         v
                                                              Prometheus ──> Grafana / alerts
```

MIG vs time-slicing on a single A100:

```
  MIG (hardware partition, isolated)            Time-slicing (oversubscription, shared)
  ┌───────────── A100 80GB ─────────────┐       ┌───────────── A100 80GB ─────────────┐
  │ 1g.10gb │ 1g.10gb │ 1g.10gb │ ...   │       │   one context, time-multiplexed      │
  │  10 GB  │  10 GB  │  10 GB  │ (7 max)│       │  advertised as nvidia.com/gpu: 4     │
  │ SMs walled off, separate mem buses  │       │  4 Pods share ALL 80GB + ALL SMs     │
  │ Pod A cannot touch Pod B's memory   │       │  Pod A OOM can evict/crash Pod B      │
  └─────────────────────────────────────┘       └─────────────────────────────────────┘
  Isolation: STRONG   Utilization: medium        Isolation: NONE   Utilization: HIGH
  (fragments wasted if slice sizes mismatch)      (best for bursty, trusted workloads)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 The device plugin and resource advertisement

A GPU is invisible to the scheduler until the **NVIDIA device plugin** (a DaemonSet) runs on the node, calls NVML to enumerate GPUs, and reports them to the kubelet via the device-plugin gRPC API. The kubelet then sets node capacity:

```bash
$ kubectl describe node ip-10-0-1-42 | grep -A3 Capacity
Capacity:
  cpu:                96
  memory:             1179304Mi
  nvidia.com/gpu:     8        # <- advertised by the device plugin, not the kernel
```

A Pod requests a GPU as a `limit` (GPUs cannot be overcommitted, so request must equal limit — Kubernetes enforces this for extended resources):

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: trainer
spec:
  nodeSelector:
    nvidia.com/gpu.product: NVIDIA-A100-SXM4-80GB
  tolerations:
    - key: nvidia.com/gpu        # GPU nodes are tainted so non-GPU Pods stay off them
      operator: Exists
      effect: NoSchedule
  containers:
    - name: trainer
      image: nvcr.io/nvidia/pytorch:24.05-py3
      resources:
        limits:
          nvidia.com/gpu: 2      # whole cards; no fractions natively
```

### 6.2 The GPU Operator — what it actually installs

Hand-building a GPU node means: install the matching kernel driver, the NVIDIA container toolkit (so containerd injects `/dev/nvidia*` and libraries), the device plugin, DCGM + the DCGM exporter, the MIG manager, and the node-feature-discovery labels. The **GPU Operator** does all of this declaratively as a set of DaemonSets driven by a `ClusterPolicy` CRD:

```bash
helm install gpu-operator nvidia/gpu-operator \
  -n gpu-operator --create-namespace \
  --set driver.version=550.54.15 \
  --set mig.strategy=mixed \
  --set toolkit.enabled=true \
  --set dcgmExporter.enabled=true
```

Sequencing matters: the operator uses init containers and node labels (`nvidia.com/gpu.deploy.driver=true`) to gate each step. The device plugin will not start until the driver validation pod passes, so a freshly provisioned node shows `nvidia.com/gpu: 0` for the first 60–120 seconds while drivers load.

### 6.3 Enabling MIG (hardware partitioning)

MIG is configured per node via a label the GPU Operator's MIG manager watches. On an A100, `all-1g.10gb` slices each of 8 GPUs into 7 instances → 56 schedulable MIG devices per node:

```bash
kubectl label node ip-10-0-1-42 \
  nvidia.com/mig.config=all-1g.10gb --overwrite
# MIG manager drains GPU pods, reconfigures, then re-advertises:
#   nvidia.com/mig-1g.10gb: 56
```

A Pod then requests a slice:

```yaml
resources:
  limits:
    nvidia.com/mig-1g.10gb: 1   # 10GB, ~1/7 of the SMs, hardware-isolated
```

### 6.4 Time-slicing (oversubscription)

Time-slicing is pure software: a ConfigMap tells the device plugin to advertise each physical GPU as N replicas. There is **no memory isolation** — all replicas share the full 80 GB and all SMs, round-robin time-multiplexed by the GPU's hardware scheduler:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: time-slicing-config
  namespace: gpu-operator
data:
  any: |-
    version: v1
    sharing:
      timeSlicing:
        replicas: 4    # 1 physical GPU -> advertised as nvidia.com/gpu: 4
```

After applying, an 8-GPU node advertises `nvidia.com/gpu: 32`. Four Pods land per card; if one allocates 70 GB the others will OOM.

### 6.5 Karpenter GPU NodePool with Spot + checkpointing

Karpenter provisions GPU nodes just-in-time, picking the cheapest instance that fits the pending Pod's `nvidia.com/gpu` request. A training NodePool prefers Spot:

```yaml
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: gpu-training
spec:
  template:
    spec:
      taints:
        - key: nvidia.com/gpu
          effect: NoSchedule
      requirements:
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["spot"]            # ~70% cheaper; needs checkpointing
        - key: node.kubernetes.io/instance-type
          operator: In
          values: ["p4d.24xlarge", "p5.48xlarge", "g5.48xlarge"]
      nodeClassRef:
        name: gpu-nodeclass
  disruption:
    consolidationPolicy: WhenEmpty    # never consolidate a node with a running training Pod
    consolidateAfter: 5m
  limits:
    nvidia.com/gpu: 64                 # hard cap to bound spend
```

### 6.6 Gang scheduling a distributed PyTorch job

A 4-worker `PyTorchJob` with NCCL all-reduce must start all workers together. Kueue + the Kubeflow training operator give all-or-nothing admission:

```yaml
apiVersion: kubeflow.org/v1
kind: PyTorchJob
metadata:
  name: bert-pretrain
  labels:
    kueue.x-k8s.io/queue-name: gpu-team-a
spec:
  pytorchReplicaSpecs:
    Master:
      replicas: 1
      template: { spec: { containers: [{ name: pytorch, image: bert:latest,
        resources: { limits: { nvidia.com/gpu: 8 } } }] } }
    Worker:
      replicas: 3
      template: { spec: { containers: [{ name: pytorch, image: bert:latest,
        resources: { limits: { nvidia.com/gpu: 8 } } }] } }
# 32 GPUs total; Kueue admits the job only when all 32 are available, then releases together.
```

---

## 7. Real-World Examples

- **OpenAI** runs training on Kubernetes clusters scaled past 7,500 nodes (their public engineering post describes pushing the API server, etcd, and the Flannel/CNI to their limits, and switching DNS and metrics to handle the scale). Their lesson: at GPU scale the *control plane* (etcd write throughput, DNS QPS, kube-scheduler latency) becomes the bottleneck long before the GPUs do.
- **Spotify** runs ML workloads on GKE with their internal "Hendrix" / Ray-based platform, using Kubeflow Pipelines for orchestration so ML engineers ship pipelines without touching node provisioning.
- **Uber's Michelangelo** standardized training and serving across the company; the platform team owns GPU scheduling and feature pipelines so modelers don't.
- **Anyscale / Ray** users (e.g., Cohere, many LLM shops) run Ray on Kubernetes via KubeRay to elastically scale a Ray cluster across hundreds of GPUs for both training (Ray Train) and batch inference (Ray Data).
- **NVIDIA's own DGX Cloud** and reference architectures use the GPU Operator + MIG + DCGM exactly as described here; the operator is the de-facto standard for any production GPU cluster.

---

## 8. Tradeoffs

| Decision | Option A | Option B | When to pick which |
|----------|----------|----------|--------------------|
| Sharing model | MIG (isolated) | Time-slicing (packed) | MIG for multi-tenant prod inference needing isolation; time-slicing for trusted/dev/bursty |
| Capacity | Static GPU pool | Karpenter just-in-time | Static for steady 24/7 serving; Karpenter for spiky training queues |
| Pricing | On-Demand | Spot + checkpointing | On-Demand for latency-SLA serving; Spot for fault-tolerant training (~70% savings) |
| Orchestrator | Kubeflow | Ray | Kubeflow for pipeline/DAG-centric MLOps; Ray for Python-native distributed compute |
| Scheduler | Default kube-scheduler | Volcano / Kueue | Default for single-GPU serving; Volcano/Kueue for multi-GPU gang jobs and quotas |
| Serving runtime | KServe | Triton / Ray Serve | KServe for standardized CRD + scale-to-zero; Triton for max GPU throughput/multi-model |

| Sharing approach | GPU utilization | Isolation | Blast radius of a bad tenant |
|------------------|-----------------|-----------|------------------------------|
| Exclusive | ~30–50% (typical) | Total | None (1 tenant per card) |
| MIG | ~50–70% | Hardware | Contained to the slice |
| Time-slicing | ~70–90% | None | Co-tenants crash/OOM together |

---

## 9. When to Use / When NOT to Use

**Use a dedicated GPU platform layer when:**
- More than a handful of teams share GPUs and you need quotas, fair-share, and chargeback.
- You run both training and serving and need to keep them on separate, differently-priced node pools.
- GPU spend is large enough that a 2× utilization improvement pays for the platform team.
- Workloads are bursty and Karpenter-style elasticity meaningfully cuts idle cost.

**Do NOT reach for this complexity when:**
- You have one or two GPUs and one team — a single tainted node and `nvidia.com/gpu: 1` requests is enough; MIG/Kueue/Volcano are overkill.
- Latency-critical serving with strict SLAs — do **not** time-slice; the lack of isolation makes p99 unpredictable. Use exclusive or MIG.
- Tiny intermittent inference — a serverless GPU offering (AWS SageMaker Serverless, Modal, Replicate) may be cheaper than running a cluster.
- You can't checkpoint — don't put training on Spot; an interruption wastes the whole run.

---

## 10. Common Pitfalls

**Pitfall 1: Allocating whole GPUs to tiny inference Pods (the utilization killer).**

```yaml
# BROKEN: a 350M-param model that needs ~3GB and 8% of an A100,
# but checks out the entire 80GB card. 12 replicas pin 12 full GPUs at ~8% util.
resources:
  limits:
    nvidia.com/gpu: 1     # whole A100 80GB for a model that uses 3GB
```

```yaml
# FIX: MIG-slice the cards. 1g.10gb gives 10GB + isolation; 7 slices/GPU.
# 12 replicas now fit on 2 physical GPUs instead of 12. ~6x fewer cards.
resources:
  limits:
    nvidia.com/mig-1g.10gb: 1
```
The broken version cost 12 × $3.27/hr ≈ $39/hr; the fix runs the same 12 replicas on 2 cards ≈ $6.50/hr — a $32/hr (~$23k/month) waste eliminated by matching slice size to model size.

**Pitfall 2: Karpenter consolidates a node mid-training.** Default consolidation can disrupt a node it deems underutilized — killing a 6-hour training job at hour 5. Fix: `consolidationPolicy: WhenEmpty` on the training NodePool, plus a `karpenter.sh/do-not-disrupt: "true"` annotation on long jobs.

**Pitfall 3: No gang scheduling → distributed-training deadlock.** The default scheduler places worker Pods one at a time; under contention it places 3 of 4 workers, which hold their GPUs waiting for the 4th that can never schedule. Classic resource deadlock. Fix: Kueue/Volcano all-or-nothing admission.

**Pitfall 4: Confusing allocation with utilization.** Dashboards show `nvidia.com/gpu` 100% allocated and leadership assumes GPUs are busy. DCGM reveals `DCGM_FI_DEV_GPU_UTIL` at 12%. Always alert on *DCGM utilization*, not allocation.

**Pitfall 5: Driver/CUDA mismatch after a node image bump.** The container's CUDA version exceeds what the node driver supports → `CUDA error: forward compatibility was attempted on non supported HW`. Pin driver version in the GPU Operator and gate node-image upgrades on a GPU smoke test.

**Pitfall 6: Time-slicing a latency-SLA endpoint.** Co-tenants contend for SMs; p99 latency triples unpredictably. Time-slicing is for throughput-tolerant or dev workloads only.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| NVIDIA GPU Operator | Node provisioning | Installs driver, toolkit, device plugin, DCGM, MIG manager via `ClusterPolicy` |
| NVIDIA device plugin | Resource advertisement | Exposes `nvidia.com/gpu`; supports time-slicing replicas and MIG strategies |
| DCGM + DCGM exporter | GPU metrics | `DCGM_FI_DEV_GPU_UTIL`, `_FB_USED`, `_GPU_TEMP`, ECC errors → Prometheus |
| Karpenter | Node autoscaling | Just-in-time GPU instances, Spot-aware, instance-type flexible (AWS-native; Azure beta) |
| Cluster Autoscaler | Node autoscaling | Per-ASG GPU node groups; the cross-cloud alternative to Karpenter |
| Kueue | Job queueing | Quotas, fair-share, gang admission; CNCF, integrates with training operators |
| Volcano | Batch scheduler | Gang scheduling, fair-share, GPU topology awareness |
| Kubeflow | ML platform | Pipelines, Training Operator (PyTorchJob/TFJob), Katib, KServe |
| Ray / KubeRay | Distributed compute | Ray Train/Serve/Tune/Data; elastic Ray clusters on K8s |
| NVIDIA Triton | Inference server | Multi-model, dynamic batching, max GPU throughput |
| KServe | Model serving | CRD-based, scale-to-zero, canary; standard on Kubeflow |

**AWS ↔ GCP ↔ Azure GPU mapping:**

| Capability | AWS | GCP | Azure |
|-----------|-----|-----|-------|
| GPU instances | p4d/p5 (A100/H100), g5 (A10G) | a2/a3 (A100/H100), g2 (L4) | NDv5 (H100), NC A100 v4 |
| Managed K8s GPU | EKS + GPU Operator | GKE (GPU node pools, time-sharing built-in) | AKS GPU node pools |
| Managed training | SageMaker Training | Vertex AI Training | Azure ML |
| Node autoscaler | Karpenter / CAS | GKE Autopilot / node auto-provisioning | Cluster Autoscaler |
| Serverless GPU | SageMaker Serverless | Cloud Run GPU | Azure Container Apps GPU |

---

## 12. Interview Questions with Answers

**Q: Why can't you request a fraction of a GPU like you request 0.5 CPU in Kubernetes?**
Because GPUs are exposed as **extended resources**, which Kubernetes only supports as non-overcommittable integers — request must equal limit and must be a whole number. CPU and memory are first-class divisible resources the kubelet can throttle via cgroups; a GPU has no equivalent kernel-level fractional throttling that K8s understands. Fractional sharing is therefore faked at the device-plugin layer: MIG advertises smaller named resources (`nvidia.com/mig-1g.10gb`), and time-slicing advertises N integer replicas of one physical card. In practice you pick MIG when you need isolation and time-slicing when you need packing.

**Q: Walk me through what the NVIDIA GPU Operator installs and why the order matters.**
It installs, as DaemonSets gated by node labels: the kernel **driver**, the **container toolkit** (so containerd injects `/dev/nvidia*` and CUDA libs into Pods), the **device plugin** (advertises `nvidia.com/gpu`), **DCGM + exporter** (metrics), and the **MIG manager** (partitioning). Order matters because each layer depends on the previous: the device plugin can't enumerate GPUs until the driver loads, and the toolkit must be present before any GPU container can run. The operator uses init-container validation and labels like `nvidia.com/gpu.deploy.driver` to sequence this, which is why a fresh node reports `nvidia.com/gpu: 0` for the first 1–2 minutes. The practical payoff is that GPU nodes become cattle, not hand-built pets.

**Q: MIG vs time-slicing — when do you choose each?**
MIG gives **hardware-enforced** memory and fault isolation by physically partitioning the GPU into fixed-size instances (e.g., seven 1g.10gb slices on an A100). Time-slicing gives **no isolation** — it oversubscribes one GPU as N logical replicas that share all memory and SMs, round-robin time-multiplexed. Choose MIG for multi-tenant production inference where one tenant must never crash or read another's memory, accepting fixed slice sizes and some fragmentation. Choose time-slicing for trusted, bursty, or dev/notebook workloads where you want maximum packing and can tolerate noisy-neighbor latency variance. Never time-slice a strict-latency-SLA endpoint — p99 becomes unpredictable.

**Q: A 4-worker distributed training job is stuck with 3 Pods Running and 1 Pending. What's happening and how do you fix it?**
This is a classic **gang-scheduling deadlock**: the default scheduler placed Pods one at a time, the first three grabbed their GPUs, and there's no capacity left for the fourth — but NCCL all-reduce needs all four simultaneously, so the three running Pods make zero progress while holding GPUs hostage. The fix is a gang scheduler (Volcano or Kueue) that does **all-or-nothing admission**: the job is only placed when all four Pods' GPUs are simultaneously available, and they're released together. As a stopgap you can also reduce contention with dedicated quota per team so jobs don't interleave.

**Q: Your GPU dashboard shows 100% allocation but the ML team complains training is slow. How do you diagnose?**
Allocation (`nvidia.com/gpu` checked out) is not utilization. I'd look at DCGM: `DCGM_FI_DEV_GPU_UTIL` (SM busy %) and `DCGM_FI_DEV_FB_USED` (memory). If GPU util is low (say 15%) while allocated, the bottleneck is upstream — usually the **data pipeline** (CPU-bound preprocessing, slow storage reads starving the GPU) or small batch sizes. I'd check the input pipeline throughput, dataloader worker count, and whether data sits on slow EBS vs a local NVMe/FSx cache. The card is reserved but starved; the fix is feeding it faster, not adding more GPUs.

**Q: How does Karpenter decide which GPU instance to launch, and how do you stop it from killing training jobs?**
Karpenter watches for unschedulable Pods, reads their `nvidia.com/gpu` requirements and any node selectors/affinities, then provisions the **cheapest instance type** from the NodePool's allowed set that satisfies them — preferring Spot if configured. To protect training: set `consolidationPolicy: WhenEmpty` (so it never disrupts a node that still has running Pods) on the training NodePool, annotate long jobs with `karpenter.sh/do-not-disrupt: "true"`, and use a separate On-Demand NodePool for serving so consolidation churn doesn't touch latency-sensitive endpoints.

**Q: Why separate training and serving onto different node pools?**
They have opposite profiles. Training is long, bursty, fault-tolerant, wants whole/many GPUs, and is happy on cheap Spot. Serving is steady, latency-sensitive, wants fractional GPUs (MIG), and needs stable On-Demand capacity. On a shared pool, a big training job either starves serving of GPUs or forces you to over-provision; Spot interruptions meant for training would kill serving Pods; and consolidation churn destabilizes endpoints. Separate pools let you price each correctly (Spot for training, On-Demand/Savings Plans for serving) and isolate their failure modes.

**Q: What's the risk of running GPUs on Spot for training, and how do you mitigate it?**
Spot instances can be reclaimed with a 2-minute warning, which would otherwise throw away hours of training. Mitigation is **checkpointing**: save model + optimizer state to durable storage (S3/FSx) every N steps or minutes, and on restart resume from the latest checkpoint. Combine with Karpenter's Spot handling and a node-termination handler that catches the interruption signal and triggers a final checkpoint. For very large jobs use elastic training (PyTorch Elastic / torchrun) so the job survives losing a worker by rescaling rather than dying. The ~70% Spot discount is worth it precisely because training is restartable; serving usually is not.

**Q: How do you do multi-tenancy and chargeback on a shared GPU cluster?**
Namespaces per team with **ResourceQuotas** capping `nvidia.com/gpu`, **Kueue** ClusterQueues for fair-share and queuing across teams, and **labels/cost-allocation tags** propagated from Pods to nodes so a cost tool (Kubecost, or cloud cost allocation by tag) can attribute GPU-hours per team. The key subtlety: bill on **GPU-hours allocated** (and ideally weight by utilization to discourage hoarding), and enforce quotas at admission so one team can't starve others. DCGM utilization per namespace turns "you reserved 40 GPU-hours but used 8%" into an actionable chargeback conversation.

**Q: Why does the GPU control plane become the bottleneck before the GPUs at very large scale?**
At thousands of nodes, etcd write throughput, API-server request latency, kube-scheduler decision time, CoreDNS QPS, and CNI IP allocation all hit limits — OpenAI documented exactly this past ~7,500 nodes. GPU scheduling is low-volume but the surrounding churn (Pod creates/deletes, status updates, DNS lookups, metrics scrapes) scales with node and Pod count. Mitigations: tune etcd (separate disks, raise quota), shard or scale the API server, reduce scheduler load with gang scheduling, cap metrics cardinality, and move heavy DNS to NodeLocal DNSCache. The lesson: a GPU cluster is a Kubernetes-control-plane scaling problem wearing a GPU costume.

**Q: What is NVLink and why should the scheduler care about GPU topology?**
NVLink is NVIDIA's high-bandwidth GPU-to-GPU interconnect — ~600 GB/s on A100 vs ~64 GB/s over PCIe Gen4. For multi-GPU jobs doing frequent all-reduce (data-parallel training), GPUs connected by NVLink exchange gradients ~10× faster than over PCIe or across nodes via the network. So a topology-unaware scheduler that scatters a 4-GPU Pod across PCIe-only or cross-node placements can bottleneck on interconnect. Topology-aware scheduling (Volcano, or the device plugin's topology hints) packs co-dependent GPUs onto the same NVLink domain. For single-GPU inference, topology is irrelevant.

**Q: How do KServe and Triton differ for serving?**
KServe is a Kubernetes-native serving layer: an `InferenceService` CRD that gives you autoscaling (including scale-to-zero via Knative), canary rollout, and a standard predict/explain interface across frameworks — it's the platform-standardization choice. Triton is NVIDIA's inference *server*: it runs inside a serving Pod and maximizes GPU throughput via dynamic batching, concurrent model execution, and multi-framework backends (TensorRT, ONNX, PyTorch). They compose — you often run Triton *as* the model server inside a KServe InferenceService, getting KServe's K8s lifecycle plus Triton's raw GPU efficiency. Choose KServe alone for simple Python models, add Triton when you need to saturate the GPU with batching and multi-model packing.

**Q: What does scale-to-zero mean for GPU serving and what's the catch?**
Scale-to-zero (via KServe/Knative) removes all replicas of an idle model endpoint so you stop paying for the GPU when there's no traffic — essential when you serve hundreds of rarely-used models. The catch is **cold start**: the first request after scale-down must wait for a Pod to schedule, a GPU node to possibly be provisioned by Karpenter (minutes), the driver to load, and the multi-GB model weights to download and load into VRAM — easily tens of seconds to minutes. Mitigations: keep a warm pool of GPU nodes, pre-stage images and weights on the node (or a fast FSx/EFS cache), and use a minReplicas=1 for latency-critical models while letting the long tail scale to zero. It's a cost-vs-cold-start tradeoff decided per model.

**Q: How do you detect and respond to a failing GPU in production?**
DCGM exports health signals: ECC double-bit errors (`DCGM_FI_DEV_ECC_DBE_VOL_TOTAL`), XID errors, throttling (`DCGM_FI_DEV_CLOCK_THROTTLE_REASONS`), and temperature. A rising uncorrectable ECC count or repeated XID 79 (GPU has fallen off the bus) means the card is degrading. The response: cordon the node, drain GPU Pods (rescheduled elsewhere by the operator/controllers), label it for the GPU Operator's health checks to keep it out of rotation, and replace the instance. NVIDIA's GPU feature discovery + the operator's validation can auto-taint unhealthy GPUs. The principle is the same as any SRE practice — detect via metrics, fence the bad resource, reschedule, replace — but the blast radius is bigger because each card is so expensive.

**Q: MPS vs MIG — when would you use Multi-Process Service instead of MIG?**
MPS (Multi-Process Service) lets multiple processes share a single GPU context with spatial SM partitioning, so co-located processes run concurrently rather than time-multiplexed — useful when you control all the tenants (e.g., several inference workers from the *same* trusted service) and want better throughput than time-slicing without MIG's fixed slice sizes. The catch is weak isolation: MPS processes share memory and a fault in one can affect others, so it's only safe for trusted, co-operative workloads. MIG, by contrast, gives hardware-enforced memory and fault isolation suitable for untrusted multi-tenancy, but only on A100/H100/A30 and only in fixed partition sizes. Rule of thumb: MIG for cross-tenant isolation on supported hardware; MPS for trusted same-service concurrency on any Volta+ GPU; time-slicing for the loosest dev/bursty case.

**Q: How do you observe an ML platform's GPU fleet, and which metric do you put on the exec dashboard?**
The headline metric is **DCGM GPU utilization** (`DCGM_FI_DEV_GPU_UTIL`) — SM-busy percentage averaged across the fleet — because it's the direct measure of dollar efficiency, and it's the number that exposes the allocation-vs-utilization gap finance cares about. Below it: framebuffer memory used (`DCGM_FI_DEV_FB_USED`) to spot over- or under-sized slices, ECC/XID error counts and throttle reasons for health, power draw and temperature, and per-team GPU-hours allocated vs utilized for chargeback. You feed DCGM into Prometheus/Thanos and build per-team Grafana dashboards, but watch cardinality: per-MIG-slice metrics across hundreds of GPUs can explode series counts and OOM Prometheus (use recording rules and drop high-cardinality labels). Allocation goes on the capacity dashboard; *utilization* goes on the exec one, because that's the metric a platform's success is judged by.

---

## 13. Best Practices

- **Taint every GPU node** (`nvidia.com/gpu:NoSchedule`) so only GPU Pods (with tolerations) land on them — never waste a GPU node on a CPU sidecar.
- **Separate training and serving NodePools**, priced differently (Spot vs On-Demand), with different consolidation policies.
- **Match the sharing model to the workload**: exclusive for training, MIG for multi-tenant prod inference, time-slicing only for dev/bursty/trusted.
- **Always run DCGM** and alert on *utilization*, ECC errors, and throttling — not just allocation.
- **Gang-schedule all multi-GPU jobs** with Kueue/Volcano; never let the default scheduler place distributed training.
- **Checkpoint anything on Spot**; never put a latency-SLA endpoint on Spot or on time-slicing.
- **Pin driver/CUDA versions** in the GPU Operator and gate node-image upgrades on a GPU smoke test.
- **Cap spend with NodePool `limits`** on `nvidia.com/gpu` and per-team ResourceQuotas; bill on GPU-hours.
- **Pre-stage large model weights** on a fast shared cache (FSx for Lustre / EFS / local NVMe) to cut cold starts.
- **Cross-reference, don't duplicate**: model/training internals live in [`../../ml/`](../../ml/) and [`../../llm/`](../../llm/); this layer owns scheduling, isolation, and cost.

---

## 14. Case Study

**Scenario:** A fintech ML org runs 14 fraud/risk models for online inference plus nightly retraining. They're on EKS with a single static pool of 24 A100 GPUs (3× p4d.24xlarge). GPU bill is ~$70k/month, finance is alarmed, and DCGM shows fleet utilization at 19%. Serving and training share the pool; a nightly retraining job sometimes starves daytime inference, causing latency spikes.

**Diagnosis:** Two problems. (1) Each of the 14 inference models checks out a whole A100 while using 4–9 GB and <12% SMs — massive over-allocation. (2) Training and serving on one pool means contention and no ability to price them separately.

```
BEFORE (broken):                          AFTER (fixed):
24 A100, one pool, 19% util               serving pool: MIG-sliced + Karpenter On-Demand
14 models -> 14 whole GPUs                training pool: Karpenter Spot + checkpointing
training contends with serving            Kueue quotas; scale-to-zero for cold models
$70k/mo                                    ~$26k/mo
```

**Fix step 1 — MIG-slice the serving GPUs:**
```bash
# Each A100 -> 7x 1g.10gb. 14 models that each need <10GB now fit on 2 physical GPUs (14 slices).
kubectl label node <serving-nodes> nvidia.com/mig.config=all-1g.10gb --overwrite
```
```yaml
# Each InferenceService now requests a slice, not a card:
resources:
  limits:
    nvidia.com/mig-1g.10gb: 1
```

**Fix step 2 — split pools and price them:**
```yaml
# Serving NodePool: On-Demand, MIG, no aggressive consolidation, stable.
# Training NodePool: Spot, whole GPUs, checkpoint to S3 every 500 steps.
spec:
  template:
    spec:
      requirements:
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["spot"]        # training only
  disruption:
    consolidationPolicy: WhenEmpty
```

**Fix step 3 — quotas + scale-to-zero:**
```yaml
# Kueue ClusterQueue caps each team; KServe scales rarely-used models to zero,
# keeping a warm pool of 1 MIG-node so cold starts stay under ~20s.
```

**Broken config that caused a 2 a.m. page (and the fix):**
```yaml
# BROKEN: training NodePool had default consolidation; Karpenter killed a
# node at training-hour 5, and because checkpoints were every 2 HOURS,
# 1h58m of an 8-GPU run was lost. Repeated nightly.
disruption:
  consolidationPolicy: WhenEmptyOrUnderutilized
# checkpoint interval: 2h
```
```yaml
# FIX: protect running training + checkpoint frequently.
disruption:
  consolidationPolicy: WhenEmpty
# pod annotation: karpenter.sh/do-not-disrupt: "true"
# checkpoint interval: every 500 steps (~6 min) -> max loss now ~6 min, not 2h
```

**Result:** Serving collapsed from 14 whole GPUs to ~2 GPUs' worth of MIG slices; training moved to Spot with frequent checkpoints; rarely-used models scale to zero overnight. GPU bill dropped from ~$70k to ~$26k/month (a 63% cut), DCGM serving utilization rose to ~64%, and daytime inference latency stabilized because training can no longer steal its GPUs.

**Discussion questions:**
1. They considered time-slicing instead of MIG for serving. Why is MIG the right call for fraud models, and when would time-slicing have been acceptable?
2. The training pool is Spot. What happens during a regional A100 Spot shortage, and how would you design graceful fallback to On-Demand?
3. Scale-to-zero saved money but a quarterly-audit model had a 90-second cold start when an auditor hit it. How would you balance cost vs cold-start for the long tail of rare models?
4. How would you set the per-team Kueue quotas so the fraud team (latency-critical) is never starved by the experimentation team (batch)?

---

**See also:** [kubernetes_scheduling_and_autoscaling](../kubernetes_scheduling_and_autoscaling/) · [kubernetes_operators_and_crds](../kubernetes_operators_and_crds/) · [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/) · [case_studies/cross_cutting/gpu_node_lifecycle.md](../case_studies/cross_cutting/gpu_node_lifecycle.md) · [case_studies/design_ml_platform_infrastructure.md](../case_studies/design_ml_platform_infrastructure.md) · [`../../llm/case_studies/design_gpu_inference_platform.md`](../../llm/case_studies/design_gpu_inference_platform.md) · [`../../ml/distributed_training/`](../../ml/distributed_training/)
