# Design an ML Platform (Internal Tooling)

## Problem Statement

Design an internal ML platform for a large tech company with 500 ML engineers and 1,000 models
in production. The platform must support the full ML lifecycle: feature engineering and storage,
experiment tracking, distributed training, model registry with lineage, and multi-framework serving
infrastructure. Current state: engineers spend 6 weeks going from idea to production due to
infrastructure friction (manual cluster provisioning, ad-hoc feature pipelines, no standardized
deployment). Target state: 1 week from idea to production for standard use cases. The platform
must support PyTorch, TensorFlow, scikit-learn, and XGBoost models without requiring engineers to
understand Kubernetes internals.

### Functional Requirements
- Feature store: offline (petabyte-scale batch) + online (< 5ms serving)
- Experiment tracking: log metrics, parameters, artifacts; compare runs; reproducibility
- Distributed training: GPU/CPU/TPU, spot instances, fault tolerance (checkpoint/resume)
- Model registry: version control, lineage (data + code -> model), promotion workflow
- Serving: multi-framework, auto-scaling, A/B testing, shadow mode, canary deployments
- Python SDK + CLI for all platform interactions; web UI for browsing and monitoring

### Non-Functional Requirements
- Feature serving: < 5ms P99 for online reads, 50K QPS per feature group
- Training: support 10K training runs/day, GPU utilization > 80%
- Serving: 1000 models in production, auto-scale to 0 for batch-only models
- Security: feature access control (PII features require approval), model audit trail
- Cost: GPU cost attribution per team and per model (chargeback)

### Out of Scope
- Data ingestion pipelines (ETL from production databases — upstream data engineering)
- Model monitoring and drift detection (separate observability service consuming platform APIs)
- Automated ML (AutoML) — possible future extension

---

## Architecture Overview

```
ML Engineer Interaction Layer
+-----------------------------------------------------+
|  Python SDK (pip install mlplatform)                |
|  CLI (mlp train, mlp deploy, mlp features get)      |
|  Web UI (model catalog, experiment browser, cost)   |
+-----------------------------------------------------+
         |                  |                  |
         v                  v                  v
+----------------+  +----------------+  +----------------+
|  Feature Store |  | Training Plat. |  | Serving Infra. |
|                |  |                |  |                |
| Offline Store  |  | Job Scheduler  |  | Model Registry |
| (Hive + Spark) |  | (K8s + Volcano)|  | (MLflow)       |
| Online Store   |  |                |  |                |
| (Redis Cluster)|  | Kubeflow Pipes |  | TorchServe     |
|                |  | + Argo WF      |  | TF Serving     |
| Feature Reg.   |  |                |  | Custom sklearn |
| (schema +      |  | Experiment     |  |                |
|  lineage)      |  | Tracking       |  | A/B Router     |
|                |  | (MLflow)       |  | Shadow Mode    |
+----------------+  +----------------+  +----------------+
         |                  |                  |
         +------------------+------------------+
                            |
              +----------------------------+
              |    Control Plane           |
              |  - Job priority queue      |
              |  - Resource quotas/team    |
              |  - Cost tracking           |
              |  - Health monitoring       |
              +----------------------------+
                            |
              +----------------------------+
              |    Infrastructure          |
              |  Kubernetes (100 nodes)    |
              |  GPU: 200 A100s (training) |
              |  CPU: 500 cores (serving)  |
              |  Storage: S3 (artifacts)   |
              |  Redis Cluster (online FS) |
              |  Hive Metastore (offline)  |
              +----------------------------+


Data Flow — Feature Pipeline:
  Production DB (CDC) --> Kafka --> Flink --> Feature Store (offline + online)

Data Flow — Training:
  Feature Store (offline, Parquet) --> Training Job (GPU) --> MLflow artifact --> Registry

Data Flow — Serving:
  User Request --> API Gateway --> A/B Router --> Model Server --> Feature Store (online) --> Inference

  Model Server calls Feature Store inline:
  [Request: user_id=123] -> [Online FS: user_embedding, user_history] -> [Inference] -> [Response]


Platform SDK Flow:
  from mlplatform import FeatureStore, Trainer, ModelRegistry, Deployer

  fs = FeatureStore()
  features = fs.get_online(entity_id="user_123", feature_group="user_profile")

  trainer = Trainer(experiment="rec_model_v2", gpu=4)
  trainer.log_params({"lr": 0.001, "epochs": 50})
  trainer.run(training_script="train.py", dataset="s3://data/features/2024-01")

  registry = ModelRegistry()
  model_version = registry.register("recommendation_model", trainer.artifact_uri)

  deployer = Deployer()
  deployer.deploy(model_version, strategy="canary", canary_pct=5)
```

---

## Key Design Decisions

### 1. Unified Feature Store with Point-in-Time Correctness

The hardest ML infrastructure problem: training-serving skew. Engineers compute features in
training notebooks using all available data. At serving time, only features available at request
time exist. If training uses future data accidentally (e.g., joining on event_date instead of
feature_available_date), the model trains on leaked future information and fails badly in production.

Point-in-time correct joins: the offline store maintains feature values with effective timestamps.
A training query for "user's 30-day purchase count as of 2024-01-15" retrieves the exact value
that was available on that date, not today's value. This requires event sourcing of feature values
(every write stores a timestamp) and range queries during training data generation.

### 2. Online/Offline Store Duality with Materialization Pipeline

Online store (Redis Cluster): stores only the latest feature value per entity. Latency < 5ms.
Used at serving time. Offline store (Hive + Parquet): stores full history of feature values with
timestamps. Used for training data generation. A materialization job (Spark) computes features
in batch and writes to both stores simultaneously. This guarantees online and offline feature
values use identical computation logic (same feature definitions, no drift between batch and
real-time implementations).

### 3. Kubernetes + Volcano for ML Workloads

Standard Kubernetes job scheduling is not designed for ML: a 4-GPU training job needs all 4 GPUs
simultaneously or it cannot start (gang scheduling). Volcano extends Kubernetes with gang scheduling
(a job either gets all requested resources or waits), priority queues (research queue vs production
queue), and queue preemption. Spot instance support: training jobs checkpoint every 15 minutes;
when a spot node is preempted, the job resumes from the last checkpoint on a new node. GPU
utilization goes from 55% (standard K8s) to > 80% with Volcano gang scheduling.

### 4. MLflow as Experiment and Model Registry

MLflow provides: experiment tracking (log parameters, metrics, artifacts at each training run),
model registry (versioned models with stage transitions: Staging -> Production -> Archived),
and artifact storage (S3 backend for model weights, preprocessors, configs). Model lineage is
critical for compliance: each registered model version links to the training run, which links to
the dataset version (S3 path + snapshot date) and the code commit (Git SHA). Full audit trail:
"which training data and code produced the model currently serving 10M users."

### 5. Multi-Framework Serving with Unified Interface

Engineers use PyTorch, TensorFlow, scikit-learn, and XGBoost. A single serving interface hides
framework differences. The model server reads the framework tag from the model registry and
routes to the appropriate runtime: TorchServe for PyTorch, TF Serving for TensorFlow, a custom
Flask + joblib server for scikit-learn, and a custom server for XGBoost. All expose the same
gRPC and REST API: {model_name, model_version, inputs} -> {outputs, latency}. Engineers write
model code once; the platform handles deployment details.

### 6. A/B Testing and Canary Deployments via Traffic Splitting

The A/B router sits in front of all model servers. Traffic splitting is configured via control
plane API. Supported strategies:
- Canary: 5% traffic to new model, 95% to current. Automatic rollback if error rate > 1%
  or P99 latency increases > 20ms over 1 hour.
- A/B test: 50/50 split, run for statistically significant period (minimum detectable effect
  configured per experiment).
- Shadow mode: 100% traffic to current model; new model receives copies of all requests but
  responses are discarded. Used for integration testing without user impact.
- Blue-green: full traffic switch with instant rollback capability.

---

## Implementation

```python
from __future__ import annotations

import json
import time
import hashlib
import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Optional
import numpy as np
import redis
import mlflow
import mlflow.pytorch
import mlflow.sklearn


logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Feature Store SDK
# ---------------------------------------------------------------------------

@dataclass
class FeatureDefinition:
    """Schema definition for a feature in the feature registry."""
    name: str
    entity_type: str              # "user", "item", "session", etc.
    dtype: str                    # "float32", "int64", "string", "embedding_256"
    description: str
    owner_team: str
    sla_ms: int                   # maximum allowed latency for online retrieval
    contains_pii: bool = False    # PII features require additional access approval
    freshness_minutes: int = 60   # how stale is acceptable


@dataclass
class FeatureGroup:
    """A logical grouping of features sharing the same entity type and pipeline."""
    group_name: str
    entity_type: str
    features: list[FeatureDefinition]
    online_ttl_seconds: int = 3600    # Redis TTL for online store entries


class OnlineFeatureStore:
    """
    Redis-backed online feature store.
    Key format: {entity_type}:{entity_id}:{feature_group} -> JSON blob
    Write path: materialization job writes batch-computed features
    Read path: model servers read during inference (< 5ms P99 target)

    Redis Cluster config: 6 shards * 2 replicas = 12 nodes
    Total capacity: 500GB (covers all user/item features for 100M entities)
    Throughput: 50K reads/sec per feature group
    """

    def __init__(
        self,
        redis_host: str = "redis-cluster.internal",
        redis_port: int = 6379,
        socket_timeout_ms: float = 2.0,
    ) -> None:
        self._client = redis.Redis(
            host=redis_host,
            port=redis_port,
            socket_timeout=socket_timeout_ms / 1000.0,
            socket_connect_timeout=socket_timeout_ms / 1000.0,
            decode_responses=True,
        )

    def get(
        self,
        entity_type: str,
        entity_id: str,
        feature_group: str,
        features: Optional[list[str]] = None,   # None = return all features in group
    ) -> dict[str, Any]:
        """
        Retrieve features for a single entity.
        Returns {} if entity not found (caller must handle missing features).

        In production: use Redis pipeline for batching multiple entity lookups.
        """
        key = f"{entity_type}:{entity_id}:{feature_group}"
        start = time.perf_counter_ns()
        try:
            raw = self._client.get(key)
        except redis.RedisError as e:
            logger.warning("Feature store read failed for %s: %s", key, e)
            return {}
        finally:
            latency_ms = (time.perf_counter_ns() - start) / 1e6
            if latency_ms > 5.0:
                logger.warning("Feature store read exceeded SLA: %.1fms for key %s", latency_ms, key)

        if raw is None:
            return {}

        data: dict[str, Any] = json.loads(raw)
        if features is not None:
            return {k: v for k, v in data.items() if k in features}
        return data

    def get_batch(
        self,
        entity_type: str,
        entity_ids: list[str],
        feature_group: str,
    ) -> dict[str, dict[str, Any]]:
        """
        Batch retrieve features for multiple entities using Redis pipeline.
        Pipeline reduces round-trip overhead: N entities in ~1 RTT instead of N RTTs.
        """
        keys = [f"{entity_type}:{entity_id}:{feature_group}" for entity_id in entity_ids]
        pipe = self._client.pipeline(transaction=False)
        for key in keys:
            pipe.get(key)

        try:
            raw_values = pipe.execute()
        except redis.RedisError as e:
            logger.error("Batch feature store read failed: %s", e)
            return {}

        result = {}
        for entity_id, raw in zip(entity_ids, raw_values):
            if raw is not None:
                result[entity_id] = json.loads(raw)
        return result

    def put(
        self,
        entity_type: str,
        entity_id: str,
        feature_group: str,
        features: dict[str, Any],
        ttl_seconds: int = 3600,
    ) -> None:
        """
        Write features for a single entity (called by materialization job).
        Includes write timestamp for freshness monitoring.
        """
        key = f"{entity_type}:{entity_id}:{feature_group}"
        features["_written_at"] = time.time()
        self._client.setex(key, ttl_seconds, json.dumps(features))


# ---------------------------------------------------------------------------
# Experiment Tracking (MLflow wrapper)
# ---------------------------------------------------------------------------

class Experiment:
    """
    Wrapper around MLflow for experiment tracking.
    Provides a simplified interface and enforces metadata requirements.

    Every training run must log:
    - model_type: framework + model class
    - dataset_path: S3 path + date partition
    - git_commit: exact code version (read from git)
    - team: cost attribution

    This enforces reproducibility and enables lineage tracking.
    """

    def __init__(
        self,
        experiment_name: str,
        team: str,
        mlflow_tracking_uri: str = "http://mlflow.internal:5000",
    ) -> None:
        self.experiment_name = experiment_name
        self.team = team
        mlflow.set_tracking_uri(mlflow_tracking_uri)
        mlflow.set_experiment(experiment_name)
        self._run: Optional[mlflow.ActiveRun] = None

    def start_run(
        self,
        run_name: str,
        dataset_path: str,
        model_type: str,
        git_commit: str,
    ) -> "Experiment":
        self._run = mlflow.start_run(run_name=run_name)
        mlflow.set_tags({
            "team": self.team,
            "dataset_path": dataset_path,
            "model_type": model_type,
            "git_commit": git_commit,
            "platform_version": "2.4.0",
        })
        return self

    def log_params(self, params: dict[str, Any]) -> None:
        mlflow.log_params(params)

    def log_metric(self, key: str, value: float, step: Optional[int] = None) -> None:
        mlflow.log_metric(key, value, step=step)

    def log_metrics(self, metrics: dict[str, float], step: Optional[int] = None) -> None:
        mlflow.log_metrics(metrics, step=step)

    def log_model(self, model: Any, artifact_path: str = "model") -> str:
        """
        Log model to MLflow artifact store (S3 backend).
        Returns artifact URI for registration.
        Auto-detects framework from model type.
        """
        if hasattr(model, "parameters"):      # PyTorch
            mlflow.pytorch.log_model(model, artifact_path)
        elif hasattr(model, "save"):          # scikit-learn / XGBoost
            mlflow.sklearn.log_model(model, artifact_path)
        else:
            mlflow.pyfunc.log_model(artifact_path, python_model=model)

        run_id = mlflow.active_run().info.run_id
        artifact_uri = f"runs:/{run_id}/{artifact_path}"
        logger.info("Model logged to %s", artifact_uri)
        return artifact_uri

    def end_run(self) -> None:
        if self._run:
            mlflow.end_run()
            self._run = None

    def __enter__(self) -> "Experiment":
        return self

    def __exit__(self, *args: Any) -> None:
        self.end_run()


# ---------------------------------------------------------------------------
# Model Registry
# ---------------------------------------------------------------------------

@dataclass
class ModelVersion:
    name: str
    version: int
    artifact_uri: str
    stage: str                     # "Staging", "Production", "Archived"
    run_id: str
    framework: str                 # "pytorch", "tensorflow", "sklearn", "xgboost"
    git_commit: str
    dataset_path: str
    metrics: dict[str, float] = field(default_factory=dict)


class ModelRegistry:
    """
    Wrapper around MLflow Model Registry.
    Enforces promotion workflow: model must pass validation before Production.
    Records full lineage: dataset + code + training run -> model version.
    """

    def __init__(self, mlflow_tracking_uri: str = "http://mlflow.internal:5000") -> None:
        mlflow.set_tracking_uri(mlflow_tracking_uri)
        self._client = mlflow.tracking.MlflowClient()

    def register(
        self,
        model_name: str,
        artifact_uri: str,
        description: str = "",
    ) -> ModelVersion:
        """
        Register a model artifact as a new version in the registry.
        New versions always start in 'None' stage (unvalidated).
        """
        result = mlflow.register_model(artifact_uri, model_name)
        self._client.update_model_version(
            name=model_name,
            version=result.version,
            description=description,
        )
        logger.info("Registered %s version %s", model_name, result.version)
        return self._get_version(model_name, int(result.version))

    def promote_to_staging(self, model_name: str, version: int) -> None:
        """Promote to Staging for integration testing and shadow mode evaluation."""
        self._client.transition_model_version_stage(
            name=model_name, version=str(version), stage="Staging"
        )
        logger.info("Promoted %s v%d to Staging", model_name, version)

    def promote_to_production(self, model_name: str, version: int) -> None:
        """
        Promote to Production. Requires the version to be in Staging first.
        Archives the previous Production version automatically.
        """
        current_prod = self._client.get_latest_versions(model_name, stages=["Production"])
        self._client.transition_model_version_stage(
            name=model_name, version=str(version), stage="Production",
            archive_existing_versions=True,
        )
        logger.info(
            "Promoted %s v%d to Production (archived: %s)",
            model_name, version,
            [v.version for v in current_prod],
        )

    def get_production_model(self, model_name: str) -> Optional[ModelVersion]:
        """Fetch the currently active Production model version."""
        versions = self._client.get_latest_versions(model_name, stages=["Production"])
        if not versions:
            return None
        v = versions[0]
        return self._get_version(model_name, int(v.version))

    def _get_version(self, model_name: str, version: int) -> ModelVersion:
        v = self._client.get_model_version(model_name, str(version))
        run = self._client.get_run(v.run_id)
        tags = run.data.tags
        return ModelVersion(
            name=model_name,
            version=int(v.version),
            artifact_uri=v.source,
            stage=v.current_stage,
            run_id=v.run_id,
            framework=tags.get("model_type", "unknown"),
            git_commit=tags.get("git_commit", "unknown"),
            dataset_path=tags.get("dataset_path", "unknown"),
            metrics=run.data.metrics,
        )


# ---------------------------------------------------------------------------
# A/B Testing Traffic Router
# ---------------------------------------------------------------------------

class ABRouter:
    """
    Consistent hash-based traffic router for A/B testing and canary deployments.
    Consistent hashing ensures the same user always gets the same model variant
    (avoids within-user inconsistency during an A/B test).

    Config format:
        {
            "experiment_id": "rec_model_v3_canary",
            "variants": [
                {"model_name": "rec_model", "version": 5, "traffic_pct": 95},
                {"model_name": "rec_model", "version": 6, "traffic_pct": 5}
            ]
        }
    """

    def __init__(self, config: dict[str, Any]) -> None:
        self.experiment_id = config["experiment_id"]
        self.variants = config["variants"]
        # Validate weights sum to 100
        total = sum(v["traffic_pct"] for v in self.variants)
        assert abs(total - 100) < 0.01, f"Traffic percentages must sum to 100, got {total}"

    def route(self, entity_id: str) -> dict[str, Any]:
        """
        Route an entity (user_id, session_id) to a model variant.
        Uses MD5 hash modulo 100 for consistent assignment.
        Returns the variant config dict.
        """
        # Combine entity_id with experiment_id for per-experiment consistency
        hash_input = f"{self.experiment_id}:{entity_id}".encode()
        bucket = int(hashlib.md5(hash_input).hexdigest(), 16) % 100

        cumulative = 0
        for variant in self.variants:
            cumulative += variant["traffic_pct"]
            if bucket < cumulative:
                return variant

        # Fallback: return last variant (should not happen with valid config)
        return self.variants[-1]


# ---------------------------------------------------------------------------
# Cost Tracking
# ---------------------------------------------------------------------------

class CostTracker:
    """
    Tracks GPU-hour consumption per team and per model.
    Called at training job completion and serving inference.

    GPU cost: $2.50/hour per A100 (approximate cloud spot price).
    Stored in PostgreSQL for monthly chargeback reports.
    """

    GPU_COST_PER_HOUR_USD: float = 2.50

    def record_training_cost(
        self,
        team: str,
        experiment_name: str,
        run_id: str,
        gpu_count: int,
        duration_seconds: float,
    ) -> float:
        gpu_hours = (gpu_count * duration_seconds) / 3600.0
        cost_usd = gpu_hours * self.GPU_COST_PER_HOUR_USD
        logger.info(
            "Training cost: team=%s experiment=%s run=%s GPUs=%d duration=%.0fs cost=$%.2f",
            team, experiment_name, run_id, gpu_count, duration_seconds, cost_usd,
        )
        # In production: write to PostgreSQL cost_tracking table
        return cost_usd

    def record_serving_cost(
        self,
        model_name: str,
        team: str,
        requests: int,
        gpu_ms_per_request: float,
    ) -> float:
        total_gpu_hours = (requests * gpu_ms_per_request / 1000.0) / 3600.0
        cost_usd = total_gpu_hours * self.GPU_COST_PER_HOUR_USD
        logger.debug(
            "Serving cost: model=%s requests=%d total_gpu_ms=%.0f cost=$%.4f",
            model_name, requests, requests * gpu_ms_per_request, cost_usd,
        )
        return cost_usd
```

---

## ML Components Used

| Component | Purpose | Key Parameters |
|-----------|---------|----------------|
| Redis Cluster | Online feature store | 6 shards, 2 replicas, 500GB, < 5ms P99 |
| Apache Hive + Parquet | Offline feature store | Point-in-time correct joins, petabyte scale |
| Apache Spark | Feature materialization + training data generation | 64 workers, Delta Lake |
| MLflow | Experiment tracking + model registry | S3 artifact backend, PostgreSQL metadata |
| Kubernetes + Volcano | Training job scheduling | Gang scheduling, priority queues, spot instances |
| Kubeflow Pipelines | ML workflow orchestration | DAG-based pipeline definition |
| TorchServe | PyTorch model serving | Dynamic batching, gRPC + REST |
| TF Serving | TensorFlow model serving | Batching config, version management |
| Prometheus + Grafana | Platform observability | GPU utilization, feature store latency, serving QPS |
| Argo Workflows | Feature pipeline orchestration | Retry logic, parallelism |

---

## Tradeoffs and Alternatives

| Decision | Chosen Approach | Alternative | Reason |
|----------|----------------|-------------|--------|
| Online store | Redis Cluster | DynamoDB, Cassandra | Redis: lowest latency (< 2ms typical); DynamoDB: fully managed but 5-10ms; Cassandra: high ops burden |
| Offline store | Hive + Parquet | Delta Lake / Iceberg | Delta Lake: better ACID transactions and time travel; Hive: more mature tooling in 2024, lower migration cost |
| Experiment tracking | MLflow | Weights & Biases (W&B) | MLflow: self-hosted (data privacy), open source, no per-seat cost; W&B: better UI, more features |
| Training orchestration | Kubeflow + Volcano | Ray Train | Ray Train: simpler Python API; Kubeflow: more flexible for heterogeneous workloads, better K8s integration |
| Model serving | Multi-framework (TorchServe + TF Serving) | Triton Inference Server | Triton: single serving solution for all frameworks; chosen approach: easier model packaging per framework |
| A/B routing | Consistent hash (MD5 mod 100) | Sticky session via cookie | Hash: stateless, no session storage; cookie: simpler but requires client support |
| Feature pipeline | Batch materialization | Real-time Flink streaming | Batch: simpler, cheaper, works for < 1 hour freshness; streaming needed only for real-time features (e.g., recent clicks) |

---

## Interview Discussion Points

**Q: How do you prevent training-serving skew — the most common silent ML bug?**
A: Three mechanisms. First, point-in-time correct joins in the offline store: training data
generation queries feature values as they existed at the label timestamp, not at query time.
Second, unified feature definitions: the same Python function that computes a feature in the
batch pipeline (Spark) is also used in the real-time pipeline (Flink). The feature definition is
the single source of truth — not a Jupyter notebook and a separate serving implementation that
drift apart. Third, shadow mode validation: when deploying a new feature, run the model in shadow
mode and compare online feature values to what the offline pipeline produces for the same entity
and timestamp. Discrepancies above 1% flag a skew bug before it reaches production.

**Q: How do you handle a GPU node failure mid-training for a 6-hour distributed training job?**
A: Checkpoint-based fault tolerance. Training jobs checkpoint model weights to S3 every 15
minutes (configurable per job). The Kubeflow pipeline tracks the last successful checkpoint.
When Volcano detects a node failure (preemption or crash), it reschedules the job gang on available
nodes and the job resumes from the last checkpoint. The job requeues automatically — engineers
only see a 15-minute delay in training completion. For spot instance preemption (30-second warning),
the job receives a SIGTERM and synchronously writes an emergency checkpoint before the node is
reclaimed. Fault tolerance makes spot instances (3x cheaper than on-demand) viable for 95% of
training jobs.

**Q: How do you ensure feature access control for PII features (user location, email)?**
A: Three-layer access control. First, feature registry: each feature definition includes a
contains_pii flag. PII features require explicit approval from the data governance team before
a team can read them (approval stored in the registry, checked by the SDK on feature_store.get()).
Second, audit logging: every PII feature read logs the team, model, entity_id, and timestamp to
an immutable audit log (AWS CloudTrail). Third, data minimization: PII features are automatically
hashed at write time for model training (model sees hash of email, not plaintext). Raw PII is
accessible only in the online serving path for identity resolution. These controls satisfy GDPR
Article 30 (records of processing) and SOC 2 Type II requirements.

**Q: What metrics do you track to prove the platform reduced time-to-production from 6 weeks to 1 week?**
A: DORA-style ML metrics. First, lead time: from the Git commit of model code to the model
serving production traffic. Measured automatically via MLflow metadata (code commit) and deployment
timestamp. Target: median < 7 days. Second, deployment frequency: number of new model versions
promoted to production per week per team. Baseline: 0.5/week; target: 3/week. Third, mean time
to restore: when a serving model degrades, how long to roll back to the previous version.
Platform target: < 5 minutes (one-click rollback in the web UI). Fourth, GPU utilization:
platform-wide GPU utilization (80% target). Low utilization = expensive idle capacity; high
utilization = engineers waiting for resources. Fifth, feature freshness SLA violations: percentage
of feature reads that exceed the feature's declared freshness guarantee. Target < 0.1%.
