# MLOps and CI/CD for Machine Learning

---

## 1. Concept Overview

MLOps (Machine Learning Operations) is the discipline of applying DevOps principles — automation, version control, continuous integration, continuous delivery, and monitoring — to the full lifecycle of machine learning systems. It bridges the gap between ML experimentation and reliable production systems.

A software pipeline produces a binary artifact that either works or fails. An ML pipeline produces a model that degrades silently: the code may be correct while the model accuracy collapses due to data drift, label shift, or feature skew. MLOps adds a third axis — data and model health — on top of the traditional code-and-infrastructure axes that DevOps manages.

Key components:
- **Data versioning** — DVC, git-lfs; track which data snapshot produced which model
- **Experiment tracking** — MLflow Tracking, Weights & Biases; log hyperparameters, metrics, artifacts per run
- **Model registry** — MLflow Model Registry, Vertex AI Model Registry; manage model lifecycle stages
- **Pipeline orchestration** — Kubeflow Pipelines, Vertex AI Pipelines, Airflow; reproducible, containerized ML workflows
- **CI/CD for ML** — automated code, data, and model quality gates before any model reaches production
- **Monitoring and feedback** — drift detection, performance degradation alerts, retraining triggers

---

## 2. Intuition

One-line analogy: MLOps is the assembly line for machine learning — it ensures that every model rolling off the line is inspected, stamped with a serial number, tested under load, and can be recalled and replaced without stopping the factory.

Mental model: think of a model as a firmware binary. Firmware engineers version every build, run hardware-in-the-loop tests, do staged rollouts to device cohorts, and maintain rollback capability. ML teams without MLOps are shipping firmware from a USB stick with a sticky note that says "v2 final FINAL".

Why it matters: Gartner estimated in 2022 that 85% of ML projects fail to reach production. The primary killers are reproducibility failures, silent data quality issues, and the inability to monitor model health post-deployment. MLOps directly addresses all three.

Key insight: the model is not the deliverable. The deliverable is the pipeline that continuously produces, validates, and serves high-quality models.

---

## 3. Core Principles

**Reproducibility** — given the same code commit, dataset version, and hyperparameters, anyone on any machine must be able to reproduce the same trained model within acceptable numerical tolerance.

**Automation** — every step from data ingestion to model serving must be automatable. Manual steps are toil that does not scale and introduces human error at 2 AM during an incident.

**Continuous delivery of models** — new model versions should flow to production through the same pull-request and review process as code, with automated quality gates replacing (or augmenting) human review.

**Monitoring as a first-class concern** — model performance monitoring, data drift detection, and system health metrics are designed in from day one, not bolted on after the first incident.

**Fail fast with explicit gates** — a model that does not pass the performance gate (AUC >= baseline), latency SLA (P99 <= 100 ms), or fairness check is automatically rejected; it never reaches the registry staging area.

**Artifact lineage** — every production model carries a manifest: dataset URI + git commit SHA + hyperparameters + evaluation metrics. Auditors and incident responders can reconstruct exactly what produced any model.

---

## 4. Types / Architectures / Strategies

### MLOps Maturity Levels

**Level 0 — Manual process**
- Notebooks, manual data prep, model trained once
- No versioning, no monitoring
- Typical of initial proof-of-concept

**Level 1 — ML pipeline automation**
- Training pipeline is automated and reproducible
- Experiment tracking in place (MLflow / W&B)
- Models deployed manually after training
- Continuous training triggered by new data

**Level 2 — CI/CD pipeline automation**
- Full CI/CD for both code and models
- Automated testing: unit, integration, data schema, model performance gates
- Model registry with staged promotions
- Canary deployments with automatic rollback
- Drift detection triggering retraining pipelines

### CI/CD Strategy Variants

**Shadow mode testing** — new model receives a copy of live traffic, predictions logged but not served; performance compared to production model offline before any traffic shift.

**Canary deployment** — gradual traffic shift: 5% → 25% → 50% → 100%; automatic rollback triggered when key metric (AUC, F1, error rate) degrades more than a defined threshold (e.g., >2% drop relative to production baseline).

**Blue/green deployment** — full parallel environment; instant cutover; higher infrastructure cost but zero-downtime switch and instant rollback.

**A/B testing** — traffic split between model variants for statistical significance; requires sufficient volume and a defined primary metric; typical duration 1–2 weeks.

### Retraining Triggers

- **Scheduled** — weekly or nightly, regardless of drift signals; simple to implement
- **Performance-based** — online metric (CTR, conversion, precision) drops below threshold
- **Data drift** — Population Stability Index (PSI) > 0.2 on a key feature, or Kolmogorov-Smirnov test p-value < 0.05
- **Label drift** — distribution of predicted classes shifts significantly from training distribution
- **Event-triggered** — upstream schema change or new data partition available

---

## 5. Architecture Diagrams

### Full MLOps Pipeline

```
Code Commit (git push / PR)
         |
         v
+-------------------+
|   CI: Code Tests  |  unit tests, integration tests, linting, type checks
+-------------------+
         |
         v
+-------------------+
|  CI: Data Tests   |  Great Expectations schema validation,
|                   |  distribution checks, null-rate checks,
|                   |  feature store offline-online consistency
+-------------------+
         |  PASS
         v
+-------------------+
|  Training Step    |  containerized (Docker), GPU/CPU job,
|  (Kubeflow/       |  logs hyperparams + metrics to MLflow
|   Vertex AI)      |
+-------------------+
         |
         v
+-------------------+
|  Model Validation |  AUC >= baseline (0.02 delta allowed),
|  Gate             |  P99 latency <= 100ms,
|                   |  fairness: demographic parity diff <= 0.05
+-------------------+
         |  PASS            | FAIL
         v                  v
+-------------------+   Reject, notify, pipeline fails
| Model Registry    |  MLflow stages: None -> Staging
| (Staging)         |  artifact stored in S3/GCS,
|                   |  model signature (schema) attached
+-------------------+
         |  Manual approval or auto-promote
         v
+-------------------+
|  Canary Deploy    |  5% traffic routed to new model (shadow or live)
|                   |  monitor AUC, latency, error rate for N hours
+-------------------+
         |  Metrics stable        | Regression detected
         v                        v
  25% -> 50% -> 100%          Automatic rollback to
                               previous Production model
         |
         v
+-------------------+
|  Production       |  MLflow stage: Production
|                   |  previous model -> Archived
+-------------------+
         |
         v
+-------------------+
|  Monitoring       |  Prometheus + Grafana dashboards,
|                   |  PSI drift alerts, performance alerts,
|                   |  cost/latency SLO tracking
+-------------------+
         |  Drift / degradation detected
         v
  Retraining Trigger -> back to Training Step
```

### Feature Store Consistency Check in CI

```
Offline Feature Store         Online Feature Store
(batch, S3 parquet)     CI    (low-latency, Redis/Bigtable)
        |               |             |
        +----> compare  <-------------+
               mean, stddev, null rate
               for each feature key
               |
               | diff > 5% relative
               v
           CI FAIL — training-serving skew detected
```

---

## 6. How It Works — Detailed Mechanics

### MLflow Model Logging with Signature

```python
from __future__ import annotations

import mlflow
import mlflow.sklearn
import numpy as np
import pandas as pd
from mlflow.models.signature import infer_signature
from sklearn.base import BaseEstimator


def log_model(
    model: BaseEstimator,
    X_train: pd.DataFrame,
    X_test: pd.DataFrame,
    y_test: np.ndarray,
    metrics: dict[str, float],
    experiment_name: str = "default",
    registered_model_name: str | None = None,
) -> str:
    """
    Log a trained sklearn model to MLflow with full lineage.

    Returns the MLflow run_id for downstream traceability.
    """
    mlflow.set_experiment(experiment_name)

    with mlflow.start_run() as run:
        # Log all evaluation metrics
        mlflow.log_metrics(metrics)

        # Infer input/output schema from actual data — this schema is
        # validated at serving time; mismatches raise a ModelSignatureException
        signature = infer_signature(
            model_input=X_train,
            model_output=model.predict(X_train),
        )

        # Log model artifact with signature and sample input for validation
        mlflow.sklearn.log_model(
            sk_model=model,
            artifact_path="model",
            signature=signature,
            input_example=X_test.head(5),
            registered_model_name=registered_model_name,
        )

        # Log dataset hash for lineage — store SHA256 of the parquet file
        mlflow.log_param("dataset_sha256", _sha256_of_dataframe(X_train))
        mlflow.set_tag("git_commit", _get_git_sha())

        return run.info.run_id


def _sha256_of_dataframe(df: pd.DataFrame) -> str:
    import hashlib
    return hashlib.sha256(
        pd.util.hash_pandas_object(df, index=True).values.tobytes()
    ).hexdigest()[:16]


def _get_git_sha() -> str:
    import subprocess
    return subprocess.check_output(
        ["git", "rev-parse", "--short", "HEAD"],
        text=True,
    ).strip()
```

### Model Performance Gate

```python
from dataclasses import dataclass

import mlflow
from mlflow.tracking import MlflowClient


@dataclass
class ValidationGate:
    min_auc: float = 0.82
    max_p99_latency_ms: float = 100.0
    max_demographic_parity_diff: float = 0.05
    max_auc_regression_vs_production: float = 0.02  # must not drop more than 2%


def promote_to_staging(
    run_id: str,
    model_name: str,
    gate: ValidationGate,
) -> bool:
    """
    Promote a model version to Staging in MLflow Model Registry
    only if all validation gates pass.

    Returns True if promoted, False if rejected.
    """
    client = MlflowClient()
    run = client.get_run(run_id)
    metrics = run.data.metrics

    # Gate 1: absolute performance floor
    candidate_auc = metrics.get("auc", 0.0)
    if candidate_auc < gate.min_auc:
        print(f"GATE FAIL: AUC {candidate_auc:.4f} < floor {gate.min_auc}")
        return False

    # Gate 2: regression vs current production model
    production_auc = _get_production_metric(client, model_name, "auc")
    if production_auc is not None:
        regression = production_auc - candidate_auc
        if regression > gate.max_auc_regression_vs_production:
            print(
                f"GATE FAIL: AUC regression {regression:.4f} "
                f"> allowed {gate.max_auc_regression_vs_production}"
            )
            return False

    # Gate 3: latency SLA
    p99_ms = metrics.get("p99_latency_ms", 0.0)
    if p99_ms > gate.max_p99_latency_ms:
        print(f"GATE FAIL: P99 latency {p99_ms:.1f}ms > SLA {gate.max_p99_latency_ms}ms")
        return False

    # Gate 4: fairness
    dem_parity = metrics.get("demographic_parity_diff", 0.0)
    if dem_parity > gate.max_demographic_parity_diff:
        print(f"GATE FAIL: demographic parity diff {dem_parity:.4f} too high")
        return False

    # All gates passed — register and move to Staging
    model_version = client.create_model_version(
        name=model_name,
        source=f"runs:/{run_id}/model",
        run_id=run_id,
    )
    client.transition_model_version_stage(
        name=model_name,
        version=model_version.version,
        stage="Staging",
        archive_existing_versions=False,
    )
    print(f"PROMOTED: {model_name} v{model_version.version} -> Staging")
    return True


def _get_production_metric(
    client: MlflowClient,
    model_name: str,
    metric_key: str,
) -> float | None:
    prod_versions = client.get_latest_versions(model_name, stages=["Production"])
    if not prod_versions:
        return None
    run = client.get_run(prod_versions[0].run_id)
    return run.data.metrics.get(metric_key)
```

### Kubeflow Pipeline Definition (KFP SDK v2)

```python
from kfp import dsl
from kfp.dsl import Input, Output, Dataset, Model, Metrics


@dsl.component(base_image="python:3.10", packages_to_install=["scikit-learn", "pandas", "mlflow"])
def preprocess_data(
    raw_data_uri: str,
    processed_dataset: Output[Dataset],
) -> None:
    import pandas as pd

    df = pd.read_parquet(raw_data_uri)
    df = df.dropna(subset=["label"])
    df.to_parquet(processed_dataset.path, index=False)


@dsl.component(base_image="python:3.10", packages_to_install=["scikit-learn", "pandas", "mlflow"])
def train_model(
    dataset: Input[Dataset],
    model_output: Output[Model],
    metrics_output: Output[Metrics],
    n_estimators: int = 200,
    max_depth: int = 6,
) -> None:
    import mlflow
    import pandas as pd
    from sklearn.ensemble import GradientBoostingClassifier
    from sklearn.metrics import roc_auc_score
    from sklearn.model_selection import train_test_split
    import pickle

    df = pd.read_parquet(dataset.path)
    X = df.drop(columns=["label"])
    y = df["label"]

    X_train, X_val, y_train, y_val = train_test_split(X, y, test_size=0.2, random_state=42)

    clf = GradientBoostingClassifier(n_estimators=n_estimators, max_depth=max_depth)
    clf.fit(X_train, y_train)

    auc = roc_auc_score(y_val, clf.predict_proba(X_val)[:, 1])
    metrics_output.log_metric("auc", auc)

    with open(model_output.path, "wb") as f:
        pickle.dump(clf, f)


@dsl.component(base_image="python:3.10", packages_to_install=["scikit-learn", "mlflow"])
def validate_and_register(
    model: Input[Model],
    metrics: Input[Metrics],
    model_name: str,
    min_auc: float = 0.82,
) -> str:
    """Returns 'pass' or 'fail' — downstream steps gate on this output."""
    import pickle
    import mlflow

    auc = metrics.metadata.get("auc", 0.0)
    if auc < min_auc:
        print(f"Validation FAILED: AUC {auc} < {min_auc}")
        return "fail"

    with open(model.path, "rb") as f:
        clf = pickle.load(f)

    mlflow.sklearn.log_model(clf, artifact_path="model", registered_model_name=model_name)
    return "pass"


@dsl.pipeline(name="ml-training-pipeline", description="End-to-end training with validation gate")
def ml_pipeline(
    raw_data_uri: str,
    model_name: str = "fraud_detector",
    min_auc: float = 0.82,
) -> None:
    preprocess_task = preprocess_data(raw_data_uri=raw_data_uri)

    train_task = train_model(
        dataset=preprocess_task.outputs["processed_dataset"],
    )

    validate_and_register(
        model=train_task.outputs["model_output"],
        metrics=train_task.outputs["metrics_output"],
        model_name=model_name,
        min_auc=min_auc,
    )
```

### GitHub Actions CI Workflow for ML

```yaml
# .github/workflows/ml-ci.yml
name: ML CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  PYTHON_VERSION: "3.10"
  MLFLOW_TRACKING_URI: ${{ secrets.MLFLOW_TRACKING_URI }}

jobs:
  code-quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: ${{ env.PYTHON_VERSION }}
      - name: Install dependencies
        run: pip install -r requirements-dev.txt
      - name: Lint
        run: ruff check src/
      - name: Type check
        run: mypy src/ --strict
      - name: Unit tests
        run: pytest tests/unit/ -v --cov=src --cov-report=xml

  data-validation:
    runs-on: ubuntu-latest
    needs: code-quality
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: ${{ env.PYTHON_VERSION }}
      - name: Install dependencies
        run: pip install great_expectations pandas pyarrow
      - name: Run Great Expectations schema + distribution checks
        run: python scripts/validate_data.py --datasource ${{ secrets.DATA_URI }}
      - name: Feature store consistency check
        run: python scripts/check_feature_store_skew.py --threshold 0.05

  model-validation:
    runs-on: ubuntu-latest
    needs: data-validation
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: ${{ env.PYTHON_VERSION }}
      - name: Install ML dependencies
        run: pip install -r requirements-ml.txt
      - name: Train model (dry run on CI dataset)
        run: python scripts/train.py --config configs/ci.yaml --output /tmp/model
      - name: Performance gate — AUC and latency
        run: |
          python scripts/validate_model.py \
            --model /tmp/model \
            --min-auc 0.82 \
            --max-p99-ms 100 \
            --fairness-threshold 0.05
      - name: Integration tests (model serving)
        run: pytest tests/integration/ -v -k "serving"
      - name: Push to registry if on main
        if: github.ref == 'refs/heads/main'
        run: python scripts/register_model.py --stage Staging
```

### Canary Traffic Split Logic

```python
from __future__ import annotations

import time
from dataclasses import dataclass, field

import requests


@dataclass
class CanaryController:
    """
    Gradually shifts traffic from the current production model to a canary.
    Automatically rolls back if metric regression exceeds the threshold.
    """
    canary_endpoint: str
    production_endpoint: str
    metric_url: str          # Prometheus query endpoint
    metric_query: str        # e.g. 'model_auc{version="canary"}'
    baseline_auc: float
    max_regression: float = 0.02
    stages: list[float] = field(default_factory=lambda: [0.05, 0.25, 0.50, 1.0])
    stage_soak_minutes: int = 30

    def run(self) -> bool:
        """Returns True if full rollout succeeded, False if rollback triggered."""
        for traffic_fraction in self.stages:
            self._set_traffic_split(traffic_fraction)
            print(f"Traffic to canary: {int(traffic_fraction * 100)}%")
            time.sleep(self.stage_soak_minutes * 60)

            canary_auc = self._fetch_metric()
            regression = self.baseline_auc - canary_auc
            print(f"  Baseline AUC: {self.baseline_auc:.4f}, Canary AUC: {canary_auc:.4f}, regression: {regression:.4f}")

            if regression > self.max_regression:
                print(f"ROLLBACK triggered: regression {regression:.4f} > {self.max_regression}")
                self._set_traffic_split(0.0)
                return False

        print("Canary rollout complete: 100% traffic on new model")
        return True

    def _set_traffic_split(self, fraction: float) -> None:
        # In practice this calls the serving infrastructure API
        # (Istio VirtualService, Nginx upstream weights, etc.)
        requests.post(
            "http://serving-control-plane/traffic",
            json={"canary_weight": fraction, "production_weight": 1.0 - fraction},
            timeout=5,
        )

    def _fetch_metric(self) -> float:
        resp = requests.get(
            f"{self.metric_url}/api/v1/query",
            params={"query": self.metric_query},
            timeout=10,
        )
        resp.raise_for_status()
        result = resp.json()["data"]["result"]
        return float(result[0]["value"][1]) if result else self.baseline_auc
```

---

## 7. Real-World Examples

**Netflix** uses a multi-stage ML platform where every model version is registered in their internal registry with a lineage manifest (dataset snapshot, code SHA, training job ID). Canary deployments on recommendation models use engagement rate as the primary rollback metric, with automatic rollback if engagement drops more than 1% relative within 24 hours.

**Uber Michelangelo** pioneered the feature store concept to guarantee offline-online consistency. Features computed in the batch pipeline for training are served from the same feature store at inference, eliminating an entire class of training-serving skew bugs.

**Airbnb** runs Great Expectations checks as a mandatory CI step for any dataset used in a production model. Schema changes to upstream tables break the CI pipeline before the model is ever retrained on corrupted data.

**Google Cloud Vertex AI Pipelines** is built on Kubeflow and integrates with Cloud Build for CI. Teams define pipelines as Python DAGs, store them in Artifact Registry, and trigger them from Cloud Build on any push to the training data bucket.

---

## 8. Tradeoffs

| Approach | Benefit | Cost |
|---|---|---|
| Kubeflow Pipelines (self-managed) | Full control, portable across clouds | High setup and ops overhead; requires Kubernetes expertise |
| Vertex AI Pipelines (managed) | Zero infrastructure management, GCP-native | Vendor lock-in; cost increases at scale |
| MLflow Model Registry | Open source, integrates with any cloud | No built-in canary orchestration; manual promotion workflow |
| Canary deployment | Gradual risk; automatic rollback | Requires traffic routing infra (Istio, Nginx); doubles serving cost during split |
| Blue/green deployment | Instant rollback; zero downtime | Doubles infra cost continuously; expensive for GPU serving |
| Scheduled retraining | Simple, predictable | May retrain unnecessarily; may miss sudden drift between schedules |
| Drift-triggered retraining | React faster to data shifts | Requires robust drift detection; risk of false-positive retraining storms |
| Shadow mode testing | Zero risk before canary | Doubles inference cost; adds latency to the shadow path |

---

## 9. When to Use / When NOT to Use

**Use MLOps Level 2 (full CI/CD) when:**
- Model powers a user-facing product where degradation directly impacts revenue or safety
- Retraining happens more than once a month
- Multiple data scientists are contributing models
- Regulatory compliance requires audit trails (financial services, healthcare)
- The team has been bitten by a production incident caused by model drift or a bad deployment

**Use MLOps Level 1 (automated training only) when:**
- Team is small (1–2 engineers), model is stable, retraining is rare
- Model is internal tooling with acceptable degradation risk
- Budget and engineering bandwidth do not justify full pipeline investment

**Do NOT invest in full MLOps when:**
- Model is a one-time batch analysis with no production serving requirement
- Proof-of-concept or research prototype (add MLOps when graduating to production)
- The underlying business problem changes faster than the pipeline can stabilize

---

## 10. Common Pitfalls

### War Story 1: No Model Versioning — Wrong Model Deployed for 6 Hours

A team maintained a shared `model.pkl` file in S3 at a fixed key `s3://bucket/model/current.pkl`. During a hotfix deployment, an engineer manually copied an older model version over the current file while intending to test a rollback. The serving fleet picked up the old model on the next health-check cycle (90 seconds). AUC dropped from 0.88 to 0.71. No alert fired because the monitoring dashboard tracked only system metrics (CPU, latency), not model-level prediction quality. The incident was detected 6 hours later by a downstream team noticing conversion rate drop.

Fix: model registry with immutable versioned artifacts. Each model version gets a unique S3 key (`s3://bucket/models/{model_name}/v{version}/model.pkl`). Serving config references a version number, not a mutable key. Any change to the serving config goes through the same PR process as code.

### War Story 2: No Data Tests — NaN Features Served for 3 Days

An upstream data team renamed a column in a Hive table from `user_age_bucket` to `age_bucket`. The feature pipeline had no schema validation. It silently produced a DataFrame with all-NaN values for that feature and logged no error — pandas `.merge()` on mismatched column names produces NaN fill rather than raising. The model received NaN inputs, its imputation was not designed for this pattern, and predictions became biased toward the negative class. Precision dropped 12%. The team discovered it during a quarterly model review, not a real-time alert.

Fix: Great Expectations suite runs in CI on every data pipeline change. Schema contract specifies required columns, types, null rate <= 1%, and value range. Any upstream schema change that breaks the contract fails the CI pipeline before the feature pipeline is deployed.

### War Story 3: Rollback Not Tested — 2-Hour Outage During Incident

A production model failed a canary: AUC regressed 4%. The runbook said "execute `scripts/rollback.py`". When the on-call engineer ran it during the incident, the script failed because it read the previous model version from an environment variable (`PREV_MODEL_VERSION`) that had been overwritten during the canary promotion step. The rollback script had never been tested end-to-end in the staging environment. The team spent 2 hours manually reconstructing the previous serving config from logs.

Fix: rollback drills are scheduled monthly. The CI pipeline includes a rollback dry-run step that promotes a new model version to Staging, then immediately runs the rollback script and verifies that the serving config reverts to the pre-promotion state. Rollback is automated via the model registry: transitioning the previous Production version back to Production is a single API call.

### War Story 4: Training-Serving Skew — 15% Precision Drop

A team trained a fraud detection model that applied StandardScaler to three numeric features. The scaler was fitted on training data and persisted separately in `scaler.pkl`. The serving code loaded the model but forgot to load and apply the scaler (the serving engineer assumed the scaler was baked into the model pipeline). The model received raw, unscaled features. It still produced predictions — just poor ones. Precision dropped from 0.74 to 0.63. The drift detection system flagged statistical shifts in input distributions after 4 days, by which time significant fraud had passed through.

Fix: the sklearn `Pipeline` object bundles the scaler and the classifier into a single artifact. `mlflow.sklearn.log_model(pipeline, ...)` logs the full pipeline, and `mlflow.sklearn.load_model(uri)` always returns the complete pipeline. A CI integration test posts raw (unscaled) feature vectors to the model server and asserts that predictions fall within the expected range, catching serving skew before deployment.

---

## 11. Technologies & Tools

**Experiment Tracking:**
- MLflow Tracking — open source, self-hosted or Databricks-managed; tracks runs, params, metrics, artifacts
- Weights & Biases (W&B) — SaaS; richer visualization, team collaboration, sweeps for hyperparameter search
- Neptune.ai — SaaS alternative; strong metadata management

**Model Registry:**
- MLflow Model Registry — open source; stages: None, Staging, Production, Archived; model signatures; REST API
- Vertex AI Model Registry — GCP-managed; integrates with Vertex AI Endpoints
- AWS SageMaker Model Registry — AWS-managed; integrates with SageMaker Endpoints

**Pipeline Orchestration:**
- Kubeflow Pipelines (KFP v2) — Kubernetes-native; KFP SDK for Python DAG definition; portable
- Vertex AI Pipelines — managed KFP on GCP; Cloud Build integration; no infra management
- Apache Airflow — general-purpose; widely used; less ML-native than KFP
- Prefect / Dagster — modern workflow orchestrators; good Python-native experience

**Data Versioning:**
- DVC (Data Version Control) — git-compatible; tracks large files in remote storage (S3, GCS); versioned datasets
- Delta Lake / Iceberg — ACID-compliant table formats; time-travel queries for dataset versioning

**Data Quality:**
- Great Expectations — schema + distribution expectations; CLI and Python API; integrates into Airflow and CI
- Deepchecks — ML-specific checks including train-test drift, model performance degradation
- Evidently AI — drift reports, data quality reports; integrates with MLflow

**CI/CD:**
- GitHub Actions — YAML-based workflows; free tier for public repos; GitHub-native
- GitLab CI/CD — strong for self-hosted enterprise deployments
- Jenkins — legacy; widely deployed; high flexibility, high maintenance overhead

**Serving and Traffic Management:**
- Istio / Envoy — service mesh; fine-grained traffic splitting for canary deployments
- Seldon Core — Kubernetes-native model serving with canary and shadow mode built in
- BentoML — model packaging and serving; cloud-agnostic
- Triton Inference Server (NVIDIA) — high-performance GPU serving; supports TensorRT, ONNX, PyTorch

**Monitoring:**
- Prometheus + Grafana — metrics collection and dashboards; pull-based
- Evidently AI — open source drift and model performance monitoring
- Arize AI — SaaS; model observability; embedding drift, prediction drift
- Langfuse — LLM-specific observability (see `llm/llm_observability_and_monitoring/`)

**Feature Stores:**
- Feast — open source; offline (Parquet/BigQuery) + online (Redis/DynamoDB) stores
- Tecton — SaaS feature platform; strong consistency guarantees
- Vertex AI Feature Store — GCP-managed; integrates with BigQuery and Vertex AI Pipelines

---

## 12. Interview Questions with Answers

**Q: What is the difference between a DevOps CI/CD pipeline and an MLOps CI/CD pipeline?**
A DevOps pipeline tests code correctness and deploys a deterministic binary artifact. An MLOps pipeline adds two additional dimensions: data quality (schema, distribution) and model quality (performance gates, fairness, latency SLAs). A software artifact either passes tests or fails; a model artifact can pass all code tests while silently degrading due to data distribution shift, which is why model-specific validation gates are mandatory in MLOps.

**Q: What is training-serving skew and how do you detect it in CI?**
Training-serving skew occurs when features presented to the model at serving time differ from what the model saw during training — typically because preprocessing steps (scaling, encoding, imputation) are applied during training but omitted or applied differently at serving. Detection in CI: write an integration test that sends known raw input vectors to the deployed model server and asserts that predictions match expected outputs computed offline with the full training pipeline. Also compare mean and standard deviation of each feature between the offline feature store and online serving queries; flag any feature with >5% relative difference.

**Q: Explain MLflow Model Registry stages and how you automate promotion.**
MLflow Model Registry has four stages: None (newly registered), Staging (validated, awaiting production), Production (serving live traffic), Archived (retired). Automation: the CI pipeline trains a model, logs it to MLflow, calls `create_model_version()` to register it at stage None, then runs validation gates (AUC >= baseline, latency SLA, fairness checks). If all gates pass, the pipeline calls `transition_model_version_stage(stage="Staging")`. A separate deployment job, triggered by a merge to main or a manual approval, transitions from Staging to Production and archives the previous Production version.

**Q: How do you implement automatic rollback in a canary deployment for an ML model?**
The canary controller polls a real-time metric (AUC from an online evaluation service, or a business proxy metric like conversion rate) every N minutes. If the metric regresses beyond a defined threshold (e.g., AUC drops > 2% from the production baseline), the controller calls the serving infrastructure API to set canary traffic weight to 0% and production weight to 100%. Simultaneously, it transitions the canary model version back to Staging in the model registry and sends an alert. The critical requirement is that rollback must be atomic from the user's perspective: Istio VirtualService or Nginx upstream weight changes propagate in under 5 seconds.

**Q: What is Population Stability Index (PSI) and when do you trigger retraining based on it?**
PSI measures how much the distribution of a feature has shifted between a reference period (training data) and a current period (recent production traffic). PSI = sum over bins of (actual_fraction - expected_fraction) * ln(actual_fraction / expected_fraction). PSI < 0.1: no significant shift; 0.1–0.2: moderate shift, monitor; > 0.2: significant shift, trigger retraining. A common production setup computes PSI daily on the top 20 features and triggers a retraining pipeline when PSI > 0.2 on any of the top 5 features by feature importance.

**Q: How does a feature store solve the offline-online consistency problem?**
A feature store maintains a single feature computation definition that writes to both an offline store (e.g., S3 Parquet or BigQuery for batch training) and an online store (e.g., Redis or Bigtable for low-latency inference). Training pipelines read from the offline store; the serving layer reads from the online store using the same feature keys. The computation logic is defined once and executed in both contexts, eliminating the divergence that occurs when data science teams write Pandas code for training and engineering teams independently write SQL or Java for serving.

**Q: What data tests should run in CI before a model is retrained?**
Schema validation: required columns present, correct dtypes, no unexpected columns. Null rate: null fraction per column <= defined threshold (e.g., 1% for label column, 10% for optional features). Distribution checks: mean and standard deviation of numeric features within 3 standard deviations of historical baseline. Referential integrity: foreign keys resolve to valid entity IDs. Volume check: row count within expected range (guards against partial data loads). Feature store consistency: online store feature statistics within 5% of offline store statistics for the same time window.

**Q: How do you handle a situation where a new model version passes all CI gates but degrades in production?**
First, trigger automatic rollback via the canary controller if the degradation is caught within the canary window. If the model reached 100% traffic before degradation was detected, manually transition the previous Production model version back to Production in the registry and set traffic to 0% on the degraded version. Then conduct a root cause analysis: compare input feature distributions between the period when the old model was healthy and the current period; check whether a data pipeline change coincided with the deployment; run the model validation suite against the current production feature distribution rather than the CI holdout set. The common cause is that the CI holdout dataset did not represent the current data distribution (covariate shift since the last training run).

**Q: What is the difference between shadow mode and canary deployment in ML?**
In shadow mode, the new model receives a copy of all live requests and produces predictions, but those predictions are never shown to users — they are logged for offline comparison against the production model. Shadow mode has zero user-facing risk but does not validate user behavior (e.g., click-through rate) on the new model's output. Canary deployment routes a small fraction of real traffic (5%) to the new model, whose predictions are actually served to users. Canary validates true user-facing metrics but carries a small risk that the fraction of users receiving canary predictions may have a degraded experience if the model underperforms.

**Q: How do you version datasets in an ML project and why is it insufficient to just track the S3 path?**
An S3 path is mutable — the same path can point to different data at different times (overwrite, append, schema evolution). Dataset versioning requires an immutable reference: a git commit SHA of a DVC `.dvc` file (which records the S3 URI + SHA256 of the data), or an Iceberg/Delta Lake table snapshot ID (a monotonically increasing integer that points to an immutable manifest). The MLflow run record stores this immutable reference, so any model can be traced back to the exact byte-for-byte dataset used to train it, enabling full reproducibility and regulatory audit trails.

**Q: What is a model signature in MLflow and why does it matter for CI?**
A model signature in MLflow specifies the expected schema (column names, dtypes, value ranges) for model inputs and outputs. It is inferred from actual training data using `infer_signature(X_train, model.predict(X_train))` and stored as JSON alongside the model artifact. At serving time, MLflow's pyfunc wrapper validates every request against the signature and raises a `ModelSignatureException` if the schema does not match — before the model ever runs inference. In CI, the integration test sends a malformed request to catch any serving code that bypasses signature validation. This provides the serving-layer equivalent of an API contract test.

---

## 13. Best Practices

**Treat the training pipeline as production code.** Every script that trains a model goes through the same code review, testing, and linting process as application code. Data scientists own their pipeline code in git, not in notebooks checked in as `.ipynb` files.

**Make the model artifact the single source of truth.** Bundle preprocessing (scaler, encoder, imputer) with the model in a single sklearn `Pipeline` or equivalent. Log this unified artifact to the model registry. Never log a raw model that requires separately managed preprocessing code.

**Version everything that affects the model.** Dataset (DVC SHA or table snapshot ID), code (git commit SHA), hyperparameters (logged to MLflow), environment (Docker image digest). All four must be stored on the MLflow run record before the model is registered.

**Automate rollback and test it monthly.** The rollback procedure must require no manual steps beyond triggering the rollback command. Run a monthly rollback drill in a staging environment: promote a new model version, verify it is serving, then trigger rollback and verify the previous version is serving within 60 seconds.

**Define performance gates as code, not documentation.** Gate thresholds live in a versioned config file (e.g., `configs/validation_gates.yaml`). Changes to thresholds require a PR review. This prevents silent gate relaxation during time pressure.

**Run evaluation on a time-ordered holdout set.** Never use random shuffled train-test splits for time-series or event data. The holdout set should represent the most recent time period, simulating real deployment conditions. A model that achieves 0.91 AUC on a shuffled split may achieve 0.83 AUC on a time-ordered split.

**Monitor the model, not just the system.** Infrastructure metrics (CPU, latency, error rate) are necessary but not sufficient. Deploy an online evaluation service that computes model-level metrics (prediction distribution, AUC on a labeled sample, feature drift) and feeds them to Prometheus. Alert on model metric degradation, not just system failures.

**Keep the CI training job fast by using a representative sample.** Full training runs can take hours. The CI training step should use a 10–20% stratified sample of the training data and complete in under 10 minutes. The performance gate on this sample uses a lower absolute threshold but still enforces the regression-vs-production gate.

---

## 14. Case Study

### Problem: Fraud Detection Model at a Payments Company

A payments company runs a fraud detection model that scores every transaction in real time (P99 latency requirement: 50 ms). The model is a Gradient Boosted Tree trained on 180 days of transaction history with 47 features. Before MLOps investment, the team retrained monthly by running a notebook manually, copying the output to S3, and restarting the serving fleet. Three incidents occurred in 18 months: one wrong model deployed, one NaN feature incident, one failed rollback.

**Architecture Overview**

```
GitHub PR (model code / config change)
        |
        v
GitHub Actions CI
  |-- code tests (pytest, mypy, ruff)
  |-- data tests (Great Expectations on last 7-day sample)
  |-- feature store skew check (offline vs online, threshold 5%)
  |-- fast training (20% data sample, <10 min)
  |-- performance gate (AUC >= 0.80 on sample, P99 <= 50ms)
        |
        v (merge to main)
Kubeflow Pipeline triggered (full training, 180-day data)
  |-- preprocess_data component (DVC-versioned dataset)
  |-- train_model component (GBT, 200 estimators, logged to MLflow)
  |-- validate_and_register component
       |-- AUC >= 0.855 (production baseline: 0.872, max regression: 0.02)
       |-- P99 latency <= 50ms (load test against model server)
       |-- demographic parity diff <= 0.04
       |-- model signature attached (47 features, float64)
       |
       | PASS
       v
MLflow Model Registry: Staging
       |
       | Automated promotion to canary (no manual approval for non-critical changes)
       v
Canary Deployment (Istio VirtualService)
  5% traffic -> 30-min soak -> check AUC on real-time labeled transactions
  25% traffic -> 30-min soak -> check AUC
  50% traffic -> 60-min soak -> check AUC + precision + conversion impact
  100% traffic -> promote to Production in registry
       |
       | AUC regression > 2% at any stage
       v
Automatic Rollback: traffic 0%, previous Production model restored
       |
Monitoring (always on)
  Prometheus: prediction score distribution (mean, P5, P95)
  Evidently: PSI on top 10 features, daily batch report
  Alert: PSI > 0.2 on any top-5 feature -> trigger retraining pipeline
  Alert: online AUC < 0.85 (sampled labeled transactions) -> PagerDuty
```

**Key Design Decisions**

The team chose an sklearn `Pipeline` (Scaler + GBT) as the model artifact to eliminate training-serving skew permanently. The MLflow model signature enforces 47 named float64 columns; any upstream schema change that drops or renames a column fails at the signature validation layer in the serving container before a single prediction is computed.

The Great Expectations suite includes a custom expectation: `expect_column_mean_to_be_between` with bounds derived from the last 30 days of production traffic, not from the training data. This detects concept drift in input features even before model performance metrics degrade.

The canary controller polls an online evaluation service that labels a 2% sample of transactions using delayed ground truth (chargebacks confirmed within 48 hours). The AUC computed on this sample is the rollback trigger metric — not a proxy metric like click rate, but the actual model objective. This required building a labeling pipeline that joins prediction logs with chargeback events, but eliminated two false-positive rollbacks that would have occurred using only system-level metrics.

**Outcome**

After 6 months of full MLOps Level 2 implementation: zero production incidents caused by model deployment; mean time to detect model degradation reduced from 4 days (quarterly review) to 3 hours (automated alert); retraining cycle reduced from monthly manual to weekly automated with zero engineer time; three silent drift events detected and retraining triggered automatically before any user-visible impact.
