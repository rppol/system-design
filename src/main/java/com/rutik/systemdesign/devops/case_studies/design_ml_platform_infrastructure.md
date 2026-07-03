# Design an ML Platform Infrastructure

> Principal-grade case study · Phase 8 (Specialized Platforms & Performance) · 11-section template
> Reference template: [`../../llm/case_studies/design_gpu_inference_platform.md`](../../llm/case_studies/design_gpu_inference_platform.md)

## Intuition

> Building an ML platform is **building an internal airline, not flying one plane**. Any team can charter a single jet (one data scientist SSH-ing into one GPU box); an airline is the scheduling, the gate assignments, the maintenance crews, the fuel contracts, and the safety board that let *hundreds* of flights share a fleet efficiently and never crash into each other. The hard part was never the engines (the models) — it's the operations that keep $50M of aircraft (GPUs) busy, isolated, and on time.

**Key insight:** An ML platform's product is **GPU utilization and self-service**, not models. The platform team owns scheduling, isolation, cost, and observability so that dozens of ML teams ship without each reinventing GPU provisioning — and so the company runs its accelerators at 65%+ instead of 20%. Every architectural decision is judged on two axes: does it raise utilization, and does it reduce the friction for an ML engineer to get from "I have code" to "it's training/serving in prod"?

**Mental model:** Three planes stacked. The **substrate** (Kubernetes + GPU Operator + Karpenter) makes GPUs schedulable, isolated, and elastic. The **orchestration** layer (Kueue/Volcano for batch gang-scheduling, Kubeflow/Ray for training, KServe for serving) turns "train this on 16 GPUs" or "serve this model" into Pods. The **product** layer (self-service APIs, quotas, observability, chargeback) makes it usable and accountable. This case study designs all three.

**Why this system exists:** Without a platform, every ML team copies a Terraform module, hand-builds GPU AMIs, fights driver mismatches, over-allocates whole GPUs to tiny models, and runs at ~20% utilization — multiplying the single largest line item in the cloud bill across N teams. The platform centralizes the hard 20% so the other 80% (modeling) can move fast.

---

## 1. Requirements Clarification

**Context:** A company with ~120 ML engineers across 14 teams (recommendations, fraud, search ranking, forecasting, several LLM/GenAI teams). Today each team self-manages GPUs on EC2; utilization is ~22%, GPU spend is ~$1.8M/month, and onboarding a new model to production takes ~3 weeks of infra work.

### Functional requirements

- **Self-service training**: submit a distributed training job (single- or multi-GPU, multi-node) via a CRD/CLI/SDK without provisioning infrastructure.
- **Self-service serving**: deploy a model as an autoscaling endpoint (including scale-to-zero for rarely-used models) with canary rollout.
- **Multi-tenancy**: 14 teams share GPU pools with quotas, fair-share queuing, and isolation so one team can't starve or crash another.
- **Pipelines**: orchestrate preprocessing → train → evaluate → register → deploy as a DAG.
- **Experiment tracking & model registry**: every run's params/metrics/artifacts tracked; promote models through stages.
- **GPU sharing**: fractional GPUs (MIG/time-slicing) for small inference; whole/many GPUs for training.
- **Cost attribution**: per-team GPU-hour chargeback with utilization weighting.
- **Observability**: GPU utilization, job/endpoint health, lag-to-ready, and per-team dashboards.

### Non-functional requirements (with concrete targets)

| Dimension | Target |
|-----------|--------|
| GPU fleet utilization | ≥ 65% (from ~22% today) — the headline goal |
| Training job queue-to-start (p95) | < 5 min when quota available; gang jobs admitted atomically |
| Serving p99 latency | < 150 ms for online models (excluding cold start) |
| Serving availability | 99.9% (≈43 min/month error budget) for tier-1 endpoints |
| Cold-start (scale-to-zero models) | < 30 s with warm pool; < 3 min worst case |
| Spot training cost saving | ≥ 60% vs On-Demand, with < 6 min work lost per interruption |
| Model deploy time (code → prod endpoint) | < 30 min (from ~3 weeks) |
| Platform control-plane availability | 99.95% |

### Out of scope (cross-referenced, not rebuilt)

- **Model architecture, training-loop code, hyperparameter strategy** → [`../../ml/`](../../ml/), [`../../llm/`](../../llm/).
- **The GPU inference serving *application* design** (KV cache, batching, routing) → [`../../llm/case_studies/design_gpu_inference_platform.md`](../../llm/case_studies/design_gpu_inference_platform.md).
- **Feature store internals / point-in-time correctness** → [`../../ml/case_studies/cross_cutting/feature_store_and_point_in_time_correctness.md`](../../ml/case_studies/cross_cutting/feature_store_and_point_in_time_correctness.md).
- **Generic Kubernetes platform multi-tenancy** → [`./design_kubernetes_platform.md`](./design_kubernetes_platform.md) (we build *on* it).

---

## 2. Scale Estimation

### Workload mix

```
14 teams. Steady-state demand (measured from current usage):
  Online serving:   ~40 models, ~22k inference RPS aggregate at peak
  Batch training:   ~30 concurrent training jobs at peak, avg 8 GPUs each = 240 training GPUs
  Batch inference / ETL:  bursty, ~80 GPUs at nightly peak
  Notebooks / dev:  ~120 engineers, bursty, mostly fractional GPUs
```

### GPU fleet sizing

```
Serving (MIG-sliced A100s, 1g.10gb each = 7 slices/GPU):
  40 models, avg 3 replicas, each fits a 10GB slice = 120 slices
  120 slices / 7 = ~18 physical A100s for serving (call it 24 with headroom + warm pool)

Training:
  240 training GPUs at peak (mostly Spot), elastic via Karpenter

Batch/ETL: 80 GPUs nightly (Spot, scale to ~0 daytime)

Notebooks/dev: time-sliced, ~16 A100s shared across 120 engineers

Peak fleet ≈ 24 (serving, On-Demand) + 240 (training, Spot) + 80 (batch, Spot) + 16 (dev) ≈ 360 GPUs peak
Average fleet (training/batch are bursty) ≈ 180 GPUs
```

### Cost math (the business case)

```
Today: ~$1.8M/month at ~22% utilization. Effectively paying ~$1.8M for ~$400k of useful work.

Platform targets:
  - Serving: MIG slicing collapses 40 whole-GPU endpoints (~40 A100s) to ~24 -> save ~16 A100s
  - Training/batch on Spot (~70% off): 320 bursty GPUs at Spot vs On-Demand
      On-Demand p4d/8 ≈ $32.77/hr -> ~$4.10/GPU-hr; Spot ≈ $1.25/GPU-hr
  - Scale-to-zero + Karpenter consolidation removes idle-node waste

Modeled new spend: ~$760k/month for MORE useful throughput, at ~66% utilization.
  => ~$1.04M/month saved (~58%), AND more work done. The platform team (say 6 engineers,
     ~$1.5M/yr loaded) pays for itself in ~6 weeks of savings.
```

### Control-plane scale

```
~360 GPU nodes peak, ~3,000 Pods (serving replicas + training workers + sidecars + dev).
etcd, API server, scheduler must handle Pod churn from bursty training + scale-to-zero.
Cardinality risk: DCGM exports ~30 series/GPU x 360 GPUs x (MIG multiplies) -> watch it.
  (See ./cross_cutting/prometheus_cardinality_and_scale.md)
```

---

## 3. High-Level Architecture

```
                     ┌──────────────────────── PRODUCT LAYER ────────────────────────┐
                     │  Self-service CLI / SDK / Backstage portal                      │
                     │  - submit TrainingJob   - deploy InferenceService               │
                     │  - view quotas, costs, dashboards                               │
                     └───────────────┬──────────────────────────────┬─────────────────┘
                                     │ TrainingJob CRD                │ InferenceService CRD
                                     v                                v
   ┌──────────────────────── ORCHESTRATION LAYER ──────────────────────────────────────┐
   │  Kueue (quotas + fair-share + gang admission)                                       │
   │  Kubeflow Training Operator (PyTorchJob) / KubeRay (RayJob)   KServe (serving)       │
   │  Kubeflow Pipelines / Argo Workflows (DAGs)   Katib/Ray Tune (HPO)                   │
   │  MLflow (experiment tracking + model registry)                                       │
   └───────────────┬───────────────────────────────────────────────┬─────────────────────┘
                   │ Pods request nvidia.com/gpu (or mig-1g.10gb)    │ InferenceService -> Pods
                   v                                                 v
   ┌──────────────────────────── SUBSTRATE LAYER (Kubernetes / EKS) ───────────────────────┐
   │  kube-scheduler + Volcano (topology/gang)                                              │
   │  ┌───────────────────────┐   ┌───────────────────────┐   ┌──────────────────────────┐ │
   │  │ Karpenter NodePool:    │   │ Karpenter NodePool:    │   │ Karpenter NodePool:       │ │
   │  │ serving (On-Demand,MIG)│   │ training (Spot, whole) │   │ dev (time-sliced)         │ │
   │  └──────────┬────────────┘   └──────────┬────────────┘   └────────────┬─────────────┘ │
   │             v                            v                             v               │
   │   GPU nodes + NVIDIA GPU Operator (driver/toolkit/device-plugin/DCGM/MIG)              │
   │   (lifecycle per ./cross_cutting/gpu_node_lifecycle.md)                                │
   └───────────────┬───────────────────────────────────────────────┬─────────────────────────┘
                   │ artifacts/checkpoints                           │ DCGM + app metrics
                   v                                                 v
        S3 (datasets, checkpoints, models)  /  FSx Lustre (hot data)   Prometheus/Thanos -> Grafana
```

**Component inventory:**
- **Product layer**: CLI/SDK + Backstage golden paths; quota & cost views.
- **Kueue**: cluster-wide quotas, fair-share across teams, gang admission for distributed jobs.
- **Training**: Kubeflow Training Operator (PyTorchJob/TFJob) and KubeRay (RayJob) — pick per team.
- **Serving**: KServe `InferenceService` (autoscaling, scale-to-zero, canary), often wrapping Triton.
- **Pipelines**: Kubeflow Pipelines / Argo Workflows for DAGs; MLflow registry.
- **Substrate**: EKS, NVIDIA GPU Operator, Karpenter (3 NodePools), Volcano for topology/gang.
- **Storage**: S3 (durable), FSx for Lustre (hot training data + model weight cache).
- **Observability**: DCGM exporter + Prometheus/Thanos + Grafana; per-team dashboards.

**Data-flow narrative (training):** ML engineer runs `mlctl train submit job.yaml` → a `PyTorchJob` (labeled with the team's Kueue queue) is created → Kueue checks the team's quota and admits the job only when all N GPUs are simultaneously available (gang) → Volcano places workers topology-aware (NVLink-packed) → Karpenter provisions Spot GPU nodes if needed (lifecycle bring-up, ~110s) → workers run, checkpointing to S3 every ~6 min → on completion the model is logged to MLflow.

**Data-flow narrative (serving):** engineer runs `mlctl serve deploy model.yaml` → a KServe `InferenceService` requesting a `mig-1g.10gb` slice is created → KServe (via Knative) autoscales replicas on RPS, scaling to zero when idle → a canary rollout shifts 5% traffic, checks p99/error metrics, then promotes or rolls back → traffic served behind an ALB.

**Multi-region note:** Tier-1 serving runs active-active across two regions for the 99.9% SLA; training is single-region (data gravity — datasets live in one region's S3/FSx). Cross-region serving topology and failover follow [`./cross_cutting/multi_cluster_networking.md`](./cross_cutting/multi_cluster_networking.md).

```
                         Route 53 latency / weighted routing  (health-checked)
                          ┌───────────────────────────┬───────────────────────────┐
                          v                            v
              ┌──────────────────────┐      ┌──────────────────────┐
              │  us-east-1 (primary)  │      │  us-west-2 (active)   │
              │  EKS + serving NodePool│      │  EKS + serving NodePool│
              │  KServe endpoints      │◄────►│  KServe endpoints      │
              │  MIG-sliced A100s      │ async│  MIG-sliced A100s      │
              └──────────┬────────────┘ model └──────────┬────────────┘
                         │ model artifacts   replication  │
                         v   (S3 CRR)                      v
                   S3 models bucket  ──────────────► S3 models bucket (replica)
              ┌──────────────────────────────────────────────────────────────┐
              │ TRAINING is single-region (us-east-1 only): datasets/checkpoints│
              │ live in one region's S3/FSx — replicating PBs cross-region is   │
              │ cost-prohibitive. Only the trained MODEL artifact replicates.   │
              └──────────────────────────────────────────────────────────────┘
  Failover: if us-east-1 serving degrades, Route 53 health checks shift 100% to
  us-west-2 (each region sized to absorb full load); training pauses, resumes when
  the region recovers (checkpoints are durable in S3). RTO for serving < 2 min.
```

The asymmetry is deliberate: **serving is replicated** (small model artifacts, latency-SLA-bound, needs regional redundancy) while **training is pinned** (petabyte datasets have gravity; you move the model to the data's region, not the data to the model). This is the standard ML-platform multi-region pattern.

---

## 4. Component Deep Dives

### 4.1 Multi-tenant quotas and gang scheduling (Kueue)

The core fairness and correctness engine. Each team gets a `ClusterQueue` with a GPU quota; jobs queue when over quota and are admitted **atomically** (all GPUs or none) to prevent the distributed-training deadlock.

```yaml
# A team's queue with a hard GPU quota and borrowing across a shared cohort
apiVersion: kueue.x-k8s.io/v1beta1
kind: ClusterQueue
metadata: { name: fraud-team }
spec:
  cohort: shared-gpu                  # teams in a cohort can borrow each other's idle quota
  resourceGroups:
    - coveredResources: ["nvidia.com/gpu"]
      flavors:
        - name: a100-spot
          resources:
            - name: nvidia.com/gpu
              nominalQuota: 64          # fraud team's baseline
              borrowingLimit: 32        # may borrow up to 32 more if idle in the cohort
  preemption:
    reclaimWithinCohort: Any            # reclaim borrowed GPUs when the owner needs them
```

```yaml
# The training job points at the team's LocalQueue; Kueue does gang admission.
apiVersion: kubeflow.org/v1
kind: PyTorchJob
metadata:
  name: fraud-xgb-pretrain
  labels: { kueue.x-k8s.io/queue-name: fraud-team-lq }
spec:
  pytorchReplicaSpecs:
    Master: { replicas: 1, template: {spec: {containers: [{name: pt, image: fraud:v3,
      resources: {limits: {nvidia.com/gpu: 8}}}]}} }
    Worker: { replicas: 3, template: {spec: {containers: [{name: pt, image: fraud:v3,
      resources: {limits: {nvidia.com/gpu: 8}}}]}} }
# 32 GPUs; admitted only when 32 are available -> released together. No partial placement.
```

**Concrete behavior:** When the experimentation team is idle, fraud can borrow up to 32 of its GPUs; when experimentation submits, Kueue preempts the borrowed GPUs (its jobs checkpoint and requeue). This is how you hit 65% utilization without starving anyone — idle quota is lent, not wasted.

### 4.2 Serving with KServe + MIG + scale-to-zero

```yaml
apiVersion: serving.kserve.io/v1beta1
kind: InferenceService
metadata: { name: fraud-scorer }
spec:
  predictor:
    minReplicas: 0                    # scale to zero when idle (rare-model cost saving)
    maxReplicas: 20
    scaleTarget: 50                   # target 50 RPS/replica (from a capacity test)
    scaleMetric: rps
    model:
      modelFormat: { name: triton }
      storageUri: s3://models/fraud-scorer/v7
      resources:
        limits: { nvidia.com/mig-1g.10gb: 1 }   # a 10GB slice, not a whole A100
    nodeSelector: { workload: serving }
    tolerations: [{ key: nvidia.com/gpu, operator: Exists, effect: NoSchedule }]
```

For tier-1 models that can't tolerate cold start, `minReplicas: 1` plus a warm GPU pool (see [`./cross_cutting/gpu_node_lifecycle.md`](./cross_cutting/gpu_node_lifecycle.md) §"warm pool") keeps p99 within 150ms.

### 4.3 Karpenter NodePools — the three workload classes

Separate pools so training Spot churn never touches serving, and each gets the right pricing/disruption policy:

```yaml
# Training: Spot, whole GPUs, protected from consolidation mid-run
apiVersion: karpenter.sh/v1
kind: NodePool
metadata: { name: gpu-training }
spec:
  template:
    metadata: { labels: { workload: training } }
    spec:
      startupTaints: [{ key: nvidia.com/gpu-not-ready, effect: NoSchedule }]
      taints: [{ key: nvidia.com/gpu, effect: NoSchedule }]
      requirements:
        - { key: karpenter.sh/capacity-type, operator: In, values: ["spot"] }
        - { key: node.kubernetes.io/instance-type, operator: In,
            values: ["p4d.24xlarge","p5.48xlarge"] }
  disruption: { consolidationPolicy: WhenEmpty, consolidateAfter: 2m }
  limits: { nvidia.com/gpu: 320 }      # spending circuit breaker
---
# Serving: On-Demand, MIG, stable
apiVersion: karpenter.sh/v1
kind: NodePool
metadata: { name: gpu-serving }
spec:
  template:
    metadata: { labels: { workload: serving } }
    spec:
      requirements:
        - { key: karpenter.sh/capacity-type, operator: In, values: ["on-demand"] }
  disruption: { consolidationPolicy: WhenEmpty, consolidateAfter: 10m }
  limits: { nvidia.com/gpu: 32 }
```

### 4.4 The data path — feeding the GPUs

A GPU starved for data is wasted money. Hot datasets live on **FSx for Lustre** (linked to S3), giving ~hundreds of GB/s aggregate read throughput so dataloaders don't starve the GPUs:

```yaml
# PVC backed by FSx for Lustre, mounted read-only by training Pods
apiVersion: v1
kind: PersistentVolumeClaim
metadata: { name: training-data }
spec:
  accessModes: ["ReadOnlyMany"]
  storageClassName: fsx-lustre
  resources: { requests: { storage: 4800Gi } }
```

Model weights for serving are pre-staged on the node (or FSx) so scale-up doesn't pay an S3 download per cold start — a key cold-start lever from [`./cross_cutting/gpu_node_lifecycle.md`](./cross_cutting/gpu_node_lifecycle.md).

### 4.5 Pipelines + experiment tracking + model registry (the reproducibility spine)

Self-service training is only trustworthy if every run is reproducible and every promoted model is traceable. The platform wires Kubeflow Pipelines (or Argo Workflows) for the DAG and MLflow for tracking/registry:

```python
# A pipeline step logs params, metrics, and the model artifact to MLflow.
# The registry stage-gates promotion (None -> Staging -> Production).
import mlflow

mlflow.set_tracking_uri("http://mlflow.platform.svc:5000")
with mlflow.start_run(run_name="fraud-scorer-v8") as run:
    mlflow.log_params({"lr": 3e-4, "batch": 512, "gpus": 32})
    # ... training loop (checkpointing to S3 every ~6 min) ...
    mlflow.log_metric("val_auc", 0.948)
    mlflow.pytorch.log_model(model, artifact_path="model",
                             registered_model_name="fraud-scorer")
# Promotion is gated by eval (see §8a); only a model that passes is moved to Production.
```

```yaml
# The pipeline DAG (Argo Workflows): preprocess -> train -> eval -> register -> deploy.
# Each node is a Pod; train requests GPUs via the team's Kueue queue.
apiVersion: argoproj.io/v1alpha1
kind: Workflow
metadata: { generateName: fraud-pipeline- }
spec:
  entrypoint: ml-dag
  templates:
    - name: ml-dag
      dag:
        tasks:
          - { name: preprocess, template: spark-prep }
          - { name: train, template: pytorchjob, dependencies: [preprocess] }
          - { name: eval, template: eval-gate, dependencies: [train] }
          - { name: register, template: mlflow-register, dependencies: [eval] }
          - { name: deploy, template: kserve-canary, dependencies: [register] }
```

**Why it matters:** when a production model misbehaves, you can trace the exact run, data snapshot, hyperparameters, and code commit that produced it, and roll back to the previous registered version in seconds. Without this spine, "which model is in prod and how was it built?" becomes an archaeology project during an incident.

### 4.6 A broken config that wasted GPUs — and the fix

Early in the rollout, serving endpoints requested whole GPUs and training had no gang admission. Result: 40 whole A100s for serving at 9% utilization, and distributed jobs deadlocking.

```yaml
# BROKEN: serving endpoint takes a whole A100 for a 4GB model;
# AND the training job has no Kueue queue, so the default scheduler
# places workers one-by-one and deadlocks under contention.
spec:
  predictor:
    minReplicas: 3
    model:
      resources: { limits: { nvidia.com/gpu: 1 } }   # whole A100 for 4GB
# (training PyTorchJob with no kueue.x-k8s.io/queue-name label -> no gang admission)
```

```yaml
# FIX: MIG slice for serving + Kueue gang admission for training.
spec:
  predictor:
    minReplicas: 1                                    # scale-to-zero-aware
    model:
      resources: { limits: { nvidia.com/mig-1g.10gb: 1 } }   # 10GB slice
# training job now labeled kueue.x-k8s.io/queue-name -> atomic all-or-nothing placement
```
Impact of the fix: serving collapsed from ~40 A100s to ~24 (slices), and the deadlocks vanished — together a ~$420k/month line-item reduction.

---

## 5. Design Decisions & Tradeoffs

**Decision 1: Kubernetes substrate (EKS) over a bespoke scheduler or pure SageMaker.**
- *Alternatives:* Slurm (HPC-style), fully-managed SageMaker for everything, a homegrown scheduler.
- *Rationale:* The company already runs EKS; Kubernetes gives one substrate for training *and* serving, a rich ecosystem (GPU Operator, Karpenter, Kueue, KServe), and avoids per-job SageMaker pricing at this volume. Slurm is excellent for pure HPC training but weak for autoscaling online serving and self-service.
- *Consequences:* Inherits Kubernetes control-plane scaling concerns (etcd, scheduler) and the GPU-on-K8s complexity this whole section covers.

**Decision 2: Kueue for quotas/gang, not raw scheduler.**
- *Alternatives:* Volcano alone, default scheduler + namespace ResourceQuotas, YuniKorn.
- *Rationale:* Kueue gives team quotas, fair-share, **borrowing within a cohort** (the utilization lever), and gang admission, integrating cleanly with Kubeflow/Ray. We still use Volcano *underneath* for topology-aware (NVLink) placement.
- *Consequences:* Two scheduling layers to understand; preemption semantics must be carefully tuned so borrowing doesn't thrash.

**Decision 3: MIG for serving, Spot for training, time-slicing only for dev.**
- *Alternatives:* time-slice everything (max packing), exclusive GPUs everywhere (max isolation).
- *Rationale:* Matches isolation to risk — serving needs hardware isolation (MIG) for predictable p99; training is fault-tolerant so Spot's 70% discount wins; dev is trusted/bursty so time-slicing maximizes packing.
- *Consequences:* MIG fragments (slices wasted if model sizes mismatch slice sizes); Spot needs checkpointing discipline.

**Decision 4: KServe for serving over a homegrown deployment.**
- *Alternatives:* plain Deployments + HPA, Ray Serve, SageMaker endpoints.
- *Rationale:* KServe gives a standard `InferenceService` CRD with scale-to-zero, canary, and multi-framework support — the self-service "golden path." Triton inside it maximizes GPU throughput.
- *Consequences:* Knative dependency; cold-start engineering needed for scale-to-zero.

**Decision 5: Separate training/serving NodePools with different capacity types.**
- *Alternatives:* one shared GPU pool.
- *Rationale:* Opposite profiles (Spot/bursty vs On-Demand/steady), opposite disruption policies; a shared pool causes Spot reclaims to kill serving and consolidation churn to destabilize endpoints.
- *Consequences:* Slightly lower bin-packing efficiency across pools; mitigated by Kueue borrowing.

**Decision 6: Build the platform vs buy (Anyscale/Databricks/SageMaker).**
- *Rationale:* At ~$1.8M/month GPU spend and 14 teams, the savings and control justify a 6-engineer platform team; managed offerings' per-unit pricing would exceed self-run at this scale, and data-residency/control needs argue for in-house.
- *Consequences:* Ongoing platform-team headcount; on-call for a stateful, expensive system.

| Decision | Chosen | Key alternative | Why chosen |
|----------|--------|-----------------|-----------|
| Substrate | EKS / Kubernetes | Slurm, SageMaker | One substrate for train+serve, rich ecosystem |
| Quota/gang | Kueue (+Volcano) | Default sched, YuniKorn | Cohort borrowing + gang admission |
| Serving share | MIG | Time-slicing, exclusive | Isolation for predictable p99 |
| Training cost | Spot + checkpoint | On-Demand | ~70% cheaper, training is restartable |
| Serving stack | KServe + Triton | Plain Deployment, Ray Serve | Golden-path CRD, scale-to-zero, throughput |
| Build vs buy | Build | SageMaker/Anyscale | Savings + control at this scale |

---

## 6. Real-World Implementations

- **Uber — Michelangelo.** Uber's ML platform standardized feature engineering, training, and serving company-wide so modelers don't operate infrastructure. Their public posts describe a unified platform team owning the substrate and the feature store (Palette), with self-service training/serving — the canonical "platform as product" model this design follows.
- **Spotify — Hendrix / Ray on GKE.** Spotify runs ML on GKE and has publicly discussed adopting Ray for distributed training and Kubeflow Pipelines for orchestration, exactly the orchestration-layer split (Ray for compute, Kubeflow for DAGs) used here.
- **OpenAI — Kubernetes past 7,500 nodes.** OpenAI's engineering blog documents scaling Kubernetes for training to thousands of nodes and the control-plane bottlenecks (etcd, API server, DNS, CNI) that emerge — the scaling lessons baked into §2 and §10 of this design.
- **Anyscale / Ray users (Cohere, OpenAI, Instacart, Shopify).** KubeRay on Kubernetes elastically scales Ray clusters across hundreds of GPUs for both training (Ray Train) and batch inference (Ray Data); Instacart and Shopify have described Ray-based ML platforms.
- **Meta.** Meta runs enormous GPU fleets with DCGM-based health checking and automated draining of degraded nodes — at their scale GPU hardware failures are routine, validating the auto-fencing lifecycle in [`./cross_cutting/gpu_node_lifecycle.md`](./cross_cutting/gpu_node_lifecycle.md).
- **NVIDIA — GPU Operator + DGX Cloud.** The reference for the substrate layer: GPU Operator + MIG + DCGM is exactly how NVIDIA's own managed GPU clusters bring up and monitor nodes.

---

## 7. Technologies & Tools

| Tool | Layer | Strength | Watch out for |
|------|-------|----------|---------------|
| EKS + NVIDIA GPU Operator | Substrate | Standard GPU bring-up, health, MIG | ~110s cold bring-up; driver/CUDA pinning |
| Karpenter | Substrate | Fast, Spot-aware, instance-flexible | AWS-primary; consolidation can disrupt if mis-set |
| Kueue | Orchestration | Quotas, fair-share, cohort borrowing, gang | Preemption tuning; pairs with Volcano for topology |
| Volcano | Orchestration | Topology-aware (NVLink) gang scheduling | Overlaps Kueue; clarify which owns what |
| Kubeflow Training Operator | Orchestration | PyTorchJob/TFJob distributed training | Heavyweight install footprint |
| KubeRay / Ray | Orchestration | Python-native train+serve+tune+data | Another cluster abstraction to operate |
| KServe + Triton | Serving | Scale-to-zero, canary, max GPU throughput | Knative dependency; cold-start work |
| MLflow | Tracking/registry | Experiment tracking + model stages | Scaling the tracking DB at high run volume |
| DCGM + Prometheus/Thanos | Observability | GPU health + utilization at scale | Cardinality (see cross_cutting/prometheus_cardinality) |
| FSx for Lustre | Data | High-throughput hot data for dataloaders | Cost; S3 linkage management |

**AWS ↔ GCP ↔ Azure platform mapping:**

| Capability | AWS | GCP | Azure |
|-----------|-----|-----|-------|
| Managed K8s + GPU | EKS + GPU Operator | GKE (GPU pools, time-sharing, MIG) | AKS GPU pools |
| Node autoscaler | Karpenter | GKE node auto-provisioning | Cluster Autoscaler |
| Managed ML platform | SageMaker | Vertex AI | Azure ML |
| Hot data | FSx for Lustre | Filestore / GCS FUSE | Azure NetApp Files |
| Managed Prometheus | AMP | Managed Service for Prometheus | Azure Monitor managed Prometheus |

---

## 8. Operational Playbook

### (a) Eval pipeline — gating model promotion

Every model promotion runs through an automated gate before it can serve tier-1 traffic — mirroring the LLM eval-harness pattern ([`../../llm/case_studies/cross_cutting/llm_eval_harness_in_production.md`](../../llm/case_studies/cross_cutting/llm_eval_harness_in_production.md)) but for classical/online models:

```
Promote candidate model vN+1:
  1. Offline eval on a golden holdout set -> compare AUC/precision/recall vs current prod vN.
     Gate: must not regress key metric beyond -0.5% (configurable per model).
  2. Shadow deploy: mirror live traffic to vN+1 (no user impact); compare predictions + latency.
  3. Canary (KServe): 5% live traffic, monitor p99 < 150ms and error rate; 30-min bake.
  4. Auto-promote to 100% if metric gates hold; auto-rollback on breach.
  5. Register the promoted version in MLflow with the eval report attached.
```

### (b) Observability — the span/metric hierarchy

```
Platform SLO dashboards (per team + global):
  GPU layer:   DCGM_FI_DEV_GPU_UTIL (the headline), FB_USED, ECC, throttle, temp
  Scheduling:  Kueue admitted vs pending jobs, queue wait p95, gang-admission failures
  Serving:     per-InferenceService p50/p99, RPS, error rate, replica count, scale-to-zero hits
  Training:    job success rate, Spot interruptions, checkpoint age, GPU-hours/team
  Cost:        GPU-hours allocated vs utilized per team (the chargeback view)
  Control plane: etcd latency, scheduler decision time, API server p99
```
Utilization (not allocation) is the headline metric; the cost dashboard juxtaposes "GPU-hours you reserved" vs "GPU-hours actually busy" per team to drive the chargeback conversation. SLO/error-budget accounting follows [`./cross_cutting/slo_error_budget_math.md`](./cross_cutting/slo_error_budget_math.md).

### (c) Incident runbooks

**Runbook 1 — Serving p99 SLO breach (tier-1 endpoint).**
Symptom: p99 > 150ms, burn-rate alert firing. Diagnosis: check replica count (did autoscale lag a traffic spike?), MIG co-tenant contention, GPU util saturation, or a bad model version. Mitigation: bump `minReplicas`, roll back the last model version if it correlates, or move the endpoint to exclusive GPU if a noisy co-tenant. Resolution: re-run the capacity test; adjust `scaleTarget`; if MIG contention, isolate.

**Runbook 2 — Training jobs stuck Pending.**
Symptom: a team's jobs queue forever. Diagnosis: `kubectl get workloads` (Kueue) — over quota? cohort borrowing exhausted? GPU NodePool at its `limits` cap? Spot capacity unavailable in the region? Mitigation: raise quota/borrowing, increase NodePool limit, or fall back to On-Demand for the pool. Resolution: review fair-share weights; add capacity reservation for predictable peaks.

**Runbook 3 — GPU node health failure.**
Symptom: DCGM ECC/XID alert on a node. Diagnosis: confirm via `nvidia-smi`/DCGM which GPU. Mitigation: cordon + drain (PDB-respecting; training checkpoints) per [`./cross_cutting/gpu_node_lifecycle.md`](./cross_cutting/gpu_node_lifecycle.md); Karpenter replaces it. Resolution: track failure rates per instance/AZ; open a support case if a host repeatedly fails.

**Runbook 4 — Cost spike / runaway provisioning.**
Symptom: GPU-hours or spend alert spikes. Diagnosis: a buggy job or HPA creating Pods endlessly; which team/queue? Mitigation: the NodePool `limits` cap should have bounded it — if not, set/lower it; pause the offending queue. Resolution: per-team spend alerts; require quota for new workloads; post-incident review of the cap.

---

## 9. Common Pitfalls & War Stories

**War story 1 — The 9%-utilization serving fleet ($420k/month).** A company served 40 models, each on a whole A100, at ~9% utilization — every model used <10GB of an 80GB card. Allocation dashboards showed "100% of GPUs in use," so no one questioned it until finance flagged the bill. MIG-slicing serving collapsed 40 cards to ~24 and added scale-to-zero for rare models; ~$420k/month recovered. *Lesson: alert on DCGM utilization, never allocation.*

**War story 2 — Karpenter killed a 5-hour training run nightly.** A training NodePool ran with default `WhenEmptyOrUnderutilized` consolidation; Karpenter judged a node "underutilized" mid-run and disrupted it. Checkpoints were hourly, so each interruption lost ~50 minutes; it happened most nights, quietly burning ~$30k/month in re-computed GPU-hours. Fix: `consolidationPolicy: WhenEmpty` + `do-not-disrupt` annotation + 6-minute checkpoints. *Lesson: protect running training from consolidation; checkpoint frequently.*

**War story 3 — Distributed training deadlock at quarter-end.** Under contention, the default scheduler placed 14 of 16 workers for several jobs; each held its GPUs waiting for the missing 2, which could never schedule. Throughput collapsed across the cluster for hours; a model release slipped a day. Fix: Kueue gang admission (all-or-nothing). *Lesson: never let the default scheduler place distributed training.*

**War story 4 — Cold start paged on-call during an audit.** A compliance model was scaled to zero (rarely used). An auditor hit it; the first request waited ~95s (Karpenter provisioning + ~110s bring-up + 6GB model download), the request timed out, and a synthetic monitor paged. Fix: `minReplicas: 1` for compliance-critical-but-rare models, weights pre-staged on a warm node. *Lesson: scale-to-zero trades cost for cold start; choose per model.*

**War story 5 — DCGM cardinality crashed Prometheus.** Enabling per-MIG-slice DCGM metrics across 360 GPUs multiplied series count past the Prometheus memory ceiling; the server OOM-crashed, taking down all platform dashboards during an unrelated incident — so on-call was flying blind. Fix: recording rules + dropping high-cardinality labels + Thanos for long-term, per [`./cross_cutting/prometheus_cardinality_and_scale.md`](./cross_cutting/prometheus_cardinality_and_scale.md). *Lesson: GPU metrics are a cardinality risk; budget series.*

**War story 6 — Spot drought stranded a deadline.** A regional A100 Spot shortage left a team's training jobs Pending for hours before a launch. The pool was Spot-only with no fallback. Fix: NodePool with Spot-preferred + On-Demand fallback (`capacity-type In [spot, on-demand]` with Spot weighted), and capacity reservations for known peak windows. *Lesson: Spot-only has no floor; design On-Demand fallback for deadline-bound work.*

---

## 10. Capacity Planning

**Primary bottleneck resource: GPU-hours, sized per workload class.**

Serving capacity (MIG slices):
```
slices_needed = Σ_models (replicas × 1)              # each replica = 1 MIG slice
physical_gpus_serving = ceil(slices_needed / 7)      # 7× 1g.10gb per A100
  worked: 120 slices / 7 = 18 -> provision 24 (headroom + warm pool + canary capacity)
```

Training capacity (whole GPUs, elastic):
```
peak_training_gpus = Σ_jobs (workers × gpus_per_worker) at peak concurrency
  worked: 30 jobs × 8 GPUs = 240 GPUs peak; average ~120 (bursty)
Spot cost = avg_training_gpus × hours × $1.25/GPU-hr
  worked: 120 × 730 hr × $1.25 ≈ $109.5k/month (vs ~$359k On-Demand)
```

Per-replica serving sizing (from a capacity test, see [`../performance_and_load_testing/`](../performance_and_load_testing/)):
```
measured: fraud-scorer sustains ~50 RPS/replica (MIG slice) at p99 < 150ms (knee at ~70 RPS)
peak aggregate: 22,000 RPS across all models
for a 4,000-RPS model: 4000 / 50 = 80 replicas... -> but that's 80 slices = ~12 A100s for ONE model
  => such a hot model should get exclusive or larger MIG slices, not 1g.10gb. Re-tier it.
HPA/KServe scaleTarget = 70% of knee = ~50 RPS/replica (leaves headroom for the ~15s scale lag)
```

**Worked fleet example (steady state):**
```
Serving:  24 × p4d (On-Demand) ≈ 24 × $32.77/hr... (note: A100s come 8/instance,
          so 24 A100s ≈ 3 instances) ≈ 3 × $32.77 × 730 ≈ $71.8k/month
Training: ~$109.5k/month (Spot, above)
Batch/dev: ~$60k/month (Spot + time-sliced)
Storage/control/observability: ~$40k/month
Total ≈ $280k–$760k/month depending on training burst (vs $1.8M before) at ~66% utilization.
```

**Scaling triggers:**
- GPU fleet utilization > 80% sustained → expand NodePool `limits` and/or capacity reservations.
- Kueue queue-wait p95 > 5 min → raise quotas or add capacity.
- Serving p99 approaching 150ms → re-run capacity test, lower `scaleTarget`, or re-tier to bigger slices.
- etcd/API-server latency rising with Pod count → scale control plane, reduce churn (see OpenAI lessons §6).

---

## 11. Interview Discussion Points

**Q: Why is GPU utilization the headline metric for an ML platform, and how do you actually raise it from 22% to 65%?**
Because GPUs are the dominant cost (often 60–80% of an ML org's cloud bill), so utilization *is* the dollar efficiency of the platform — 22% means you're paying ~3× for the work you get. You raise it with several compounding levers: MIG-slice serving so small models share cards instead of monopolizing them; gang-schedule and bin-pack training; let teams **borrow idle quota** within a cohort (Kueue) so reserved-but-unused GPUs do work; scale rarely-used models to zero; and use Karpenter consolidation to kill idle nodes fast. Crucially you measure DCGM *utilization*, not allocation — many fleets look "100% allocated" while SMs sit at 9%. The combination, not any single lever, gets you from 22% to 65%+.

**Q: Walk me through what happens when a data scientist submits a 32-GPU distributed training job.**
They run `mlctl train submit`, creating a PyTorchJob labeled with their team's Kueue LocalQueue. Kueue checks the team's ClusterQueue quota; if 32 GPUs are available (own quota or borrowable from the cohort), it admits the job **atomically** — all 32 or none — avoiding the partial-placement deadlock. Volcano then places the 4 workers topology-aware, packing GPUs onto NVLink-connected devices for fast all-reduce. If nodes are needed, Karpenter provisions Spot GPU instances, which go through the ~110s bring-up (driver→toolkit→device-plugin) gated by a startup taint. Workers start, checkpoint to S3 every ~6 minutes (so a Spot reclaim loses little), and on completion the model is logged to MLflow. The whole flow is self-service — the scientist never touched infrastructure.

**Q: How do you provide multi-tenancy so 14 teams share GPUs without starving or crashing each other?**
Isolation and fairness at three levels. **Quotas/fairness:** Kueue ClusterQueues give each team a nominal GPU quota with fair-share and cohort borrowing — idle quota is lent and reclaimed via preemption when the owner needs it, which is how you get high utilization without starvation. **Compute isolation:** MIG gives hardware-enforced memory/fault isolation for serving so one tenant can't crash or read another's; training gets whole GPUs. **Blast-radius:** separate NodePools and namespaces, ResourceQuotas, and NodePool `limits` caps so no team's runaway job can provision unbounded GPUs. **Accountability:** per-team GPU-hour chargeback weighted by utilization discourages hoarding. The subtle part is preemption tuning so borrowing doesn't thrash.

**Q: Why separate training and serving onto different node pools and pricing models?**
They have opposite profiles. Training is long, bursty, fault-tolerant, wants whole/many GPUs, and is happy on Spot (70% cheaper) because it's restartable via checkpointing. Serving is steady, latency-sensitive, wants fractional GPUs (MIG), and needs stable On-Demand capacity plus a warm pool. On a shared pool, Spot reclaims meant for training would kill serving Pods, consolidation churn would destabilize latency-sensitive endpoints, and a single disruption policy can't satisfy both gang-training and PDB-protected serving. Separate Karpenter NodePools let each have the right capacity type, disruption policy, and taints. Kueue borrowing recovers most of the bin-packing efficiency you'd otherwise lose by splitting.

**Q: How do you decide MIG vs time-slicing vs exclusive GPUs for a given workload?**
By the isolation-vs-utilization tradeoff for that workload's risk. **Exclusive** (whole GPU) for training and the largest models — they need all of it. **MIG** for multi-tenant production inference — hardware-enforced isolation gives predictable p99 and prevents one tenant crashing another, at the cost of fixed slice sizes and some fragmentation. **Time-slicing** only for dev/notebooks and trusted bursty workloads — maximum packing with zero isolation, so a co-tenant's OOM can crash you and p99 is unpredictable. The rule of thumb: if a latency SLA or tenant isolation matters, never time-slice; if it's trusted/bursty and you want max packing, time-slice; if it needs a full card, give it one.

**Q: What's your approach to cost on this platform, and how do you do chargeback?**
Multiple layers. **Spot** for all fault-tolerant compute (training/batch) with checkpointing — the single biggest lever (~70% off). **MIG + scale-to-zero** to stop over-allocating cards to small/rare models. **Karpenter consolidation** to kill idle nodes within minutes. **NodePool `limits`** as spending circuit breakers. For chargeback: tag Pods → nodes with team labels, attribute GPU-hours per team (via Kubecost or cloud cost-allocation tags), and — critically — show **GPU-hours allocated vs utilized** per team so a team that reserves 100 GPU-hours but uses 8% gets an actionable bill. Billing on utilization-weighted GPU-hours discourages hoarding, which raises fleet utilization for everyone.

**Q: How does scale-to-zero work and when should you NOT use it?**
KServe (via Knative) removes all replicas of an idle endpoint, so you pay nothing for GPUs when there's no traffic — essential for the long tail of rarely-used models. The catch is cold start: the first request after scale-down waits for a Pod to schedule, possibly a node to be provisioned (Karpenter, minutes) and brought up (~110s), and multi-GB weights to load — tens of seconds to minutes. Don't use it for latency-critical-but-bursty endpoints, or compliance/SLA-bound models where a cold-start timeout is unacceptable (we got paged when an auditor hit a scaled-to-zero compliance model). For those, `minReplicas: 1` with a warm pool. It's a per-model cost-vs-cold-start decision, not a global setting.

**Q: At what scale does the Kubernetes control plane become the bottleneck, and what do you do?**
Past roughly a few thousand nodes (OpenAI documented ~7,500), the control plane — etcd write throughput, API-server request latency, scheduler decision time, CoreDNS QPS, CNI IP allocation — hits limits before the GPUs do, especially with the Pod churn from bursty training and scale-to-zero. Mitigations: give etcd dedicated fast disks and raise its quota; scale/shard the API server; reduce scheduler load with gang scheduling (fewer, batched decisions); cap metrics cardinality so monitoring doesn't add load; deploy NodeLocal DNSCache; and tune Karpenter/Kueue to avoid thundering-herd Pod creation. The mental shift is that a large GPU platform is fundamentally a Kubernetes-control-plane scaling problem with GPUs attached.

**Q: How do you handle a Spot interruption mid-training without losing the run?**
AWS gives ~120s notice. Karpenter's interruption queue (or the Node Termination Handler) cordons the node and sends SIGTERM; the training Pod traps it (and/or uses a `preStop` hook) to write a checkpoint — model, optimizer, step — to S3 within a `terminationGracePeriodSeconds` set under 120s. Checkpoints also fire every ~6 minutes regardless, so worst-case loss is small. After the node dies, Karpenter sees the pending Pod and relaunches (Spot, with On-Demand fallback to avoid Spot-drought stalls), and training resumes from the last checkpoint. For very large jobs, PyTorch Elastic lets the job rescale around a lost worker instead of restarting. This discipline is what makes Spot's discount safe for training.

**Q: Build vs buy — when would you NOT build this platform?**
You shouldn't build when the scale doesn't justify a platform team. With one or two ML teams and modest GPU spend, a managed offering (SageMaker, Vertex AI, Databricks, Anyscale) gets you training and serving without operating stateful, expensive infrastructure or staffing on-call — the per-unit premium is cheaper than a 6-engineer platform team. Build when GPU spend is large (here ~$1.8M/month), many teams share the fleet (so centralizing the hard parts has leverage), you need fine-grained control or data residency, and the modeled savings (~$1M/month here) dwarf the platform team's cost. The breakpoint is roughly: does centralizing scheduling/isolation/cost across N teams save more than it costs to run? At 14 teams and this spend, clearly yes.

**Q: How do you keep GPUs from being starved by the data pipeline?**
A GPU waiting on data is as wasted as an idle one, and it's a common hidden cause of low utilization (DCGM shows the card allocated but at 12% SM busy). The fixes: put hot training data on a high-throughput store like FSx for Lustre (hundreds of GB/s aggregate) linked to S3, not on slow per-Pod EBS; tune dataloader worker counts and prefetching so CPU preprocessing keeps ahead of the GPU; cache decoded/preprocessed data; and for serving, pre-stage model weights on the node to avoid per-cold-start S3 downloads. You diagnose it by correlating GPU utilization with I/O metrics — if the card is allocated but idle and disk/network reads are saturated, the bottleneck is upstream and adding GPUs won't help.

**Q: How do you safely roll out a new model version to a tier-1 endpoint?**
Through the eval gate, not a direct swap. First an offline eval on a golden holdout set compares the candidate's key metric (AUC/precision) against current prod, gating on no regression beyond a small threshold. Then a shadow deploy mirrors live traffic to the candidate with no user impact, comparing predictions and latency. Then a KServe canary shifts ~5% of live traffic and monitors p99 (<150ms) and error rate for a bake period, auto-promoting to 100% if gates hold and auto-rolling-back on breach. The promoted version and its eval report are registered in MLflow for auditability. This staged gate — offline → shadow → canary → promote — is how you change a revenue-critical model without betting the SLA on it.

---

**Cross-cutting references used:** [gpu_node_lifecycle](./cross_cutting/gpu_node_lifecycle.md) · [kubernetes_production_hardening](./cross_cutting/kubernetes_production_hardening.md) · [slo_error_budget_math](./cross_cutting/slo_error_budget_math.md) · [prometheus_cardinality_and_scale](./cross_cutting/prometheus_cardinality_and_scale.md) · [multi_cluster_networking](./cross_cutting/multi_cluster_networking.md)

**Related modules:** [ml_platform_and_gpu_infrastructure](../ml_platform_and_gpu_infrastructure/) · [kubernetes_scheduling_and_autoscaling](../kubernetes_scheduling_and_autoscaling/) · [kubernetes_operators_and_crds](../kubernetes_operators_and_crds/) · [performance_and_load_testing](../performance_and_load_testing/) · [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/)

**Related case studies:** [design_kubernetes_platform](./design_kubernetes_platform.md) · [design_autoscaling_platform](./design_autoscaling_platform.md) · [`../../llm/case_studies/design_gpu_inference_platform.md`](../../llm/case_studies/design_gpu_inference_platform.md)
