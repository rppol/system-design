# MLOps and CI/CD for Machine Learning

---

## 1. Concept Overview

MLOps (Machine Learning Operations) is the discipline of applying DevOps principles — automation, version control, continuous integration, continuous delivery, and monitoring — to the full lifecycle of machine learning systems. It bridges the gap between ML experimentation and reliable production systems.

A software pipeline produces a binary artifact that either works or fails. An ML pipeline produces a model that degrades silently: the code may be correct while the model accuracy collapses due to data drift, label shift, or feature skew. MLOps adds a third axis — data and model health — on top of the traditional code-and-infrastructure axes that DevOps manages.

Key components:
- **Data versioning** — DVC, git-lfs; track which data snapshot produced which model
- **Experiment tracking** — MLflow Tracking, Weights & Biases; log hyperparameters, metrics, artifacts per run
- **Model registry** — MLflow Model Registry, Model Registry on Google's Gemini Enterprise Agent Platform (formerly Vertex AI); manage the model lifecycle
- **Pipeline orchestration** — Kubeflow Pipelines, Agent Platform Pipelines, Apache Airflow; reproducible, containerized ML workflows
- **CI/CD for ML** — automated code, data, and model quality gates before any model reaches production
- **Monitoring and feedback** — drift detection, performance degradation alerts, retraining triggers

---

## 2. Intuition

One-line analogy: MLOps is the assembly line for machine learning — it ensures that every model rolling off the line is inspected, stamped with a serial number, tested under load, and can be recalled and replaced without stopping the factory.

Mental model: think of a model as a firmware binary. Firmware engineers version every build, run hardware-in-the-loop tests, do staged rollouts to device cohorts, and maintain rollback capability. ML teams without MLOps are shipping firmware from a USB stick with a sticky note that says "v2 final FINAL".

Why it matters: the widely repeated "85% of ML projects never reach production" figure is a misquotation — Gartner's 2018 prediction was that through 2022, 85% of AI projects would deliver *erroneous outcomes* from biased data, algorithms, or teams, not that 85% would fail to ship — so treat any such headline number as unsourced. What is well documented is the failure mechanism: reproducibility gaps, silent data quality issues, and the inability to monitor model health post-deployment. MLOps directly addresses all three.

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

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    commit([Code commit / PR]) --> codeTests["CI: code tests\nunit · lint · type"]
    codeTests -->|"pass"| dataTests["CI: data tests\nGreat Expectations · skew"]
    dataTests -->|"pass"| trainStep["Training step\nKubeflow / Agent Platform"]
    trainStep --> gate{"Validation gate\nAUC · P99 · fairness"}
    gate -->|"fail"| reject["Reject + notify\npipeline fails"]
    gate -->|"pass"| registry["Model registry\nalias @challenger"]
    registry --> canary["Canary deploy\n5% traffic"]
    canary --> check{"Metrics stable?"}
    check -->|"regression"| rollback["Auto rollback\n@champion unchanged"]
    check -->|"stable"| promote["Ramp 25 → 50 → 100%"]
    promote --> prod["Production\n@champion reassigned"]
    prod --> monitor["Monitoring\nPSI · perf · SLO"]
    monitor -.->|"drift / degradation"| trainStep

    class commit io
    class codeTests,dataTests,gate,check,monitor mathOp
    class trainStep train
    class reject,rollback lossN
    class registry,prod base
    class canary,promote req
```

The pipeline gates twice: the validation gate rejects any model below the AUC / latency / fairness bar before it reaches the registry, and the canary check auto-rolls-back on live regression before full ramp. Monitoring closes the loop, feeding drift back to the training step (dotted retraining edge).

### Feature Store Consistency Check in CI

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    offline([Offline store\nS3 / BigQuery parquet]) --> compare["Compare mean · stddev · null rate\nper feature key"]
    online([Online store\nRedis / Bigtable]) --> compare
    compare --> diff{"diff > 5% relative?"}
    diff -->|"yes"| fail["CI FAIL\ntraining-serving skew"]
    diff -->|"no"| pass([CI pass])

    class offline,online io
    class compare,diff mathOp
    class fail lossN
    class pass base
```

CI reads the same feature keys from both stores and fails the build if any feature's mean, stddev, or null rate diverges by more than 5% relative — catching training-serving skew before the model is retrained on inconsistent data.

### Canary Rollout State Machine (with rollback)

```mermaid
stateDiagram-v2
    [*] --> Challenger
    Challenger --> Canary5: start rollout
    Canary5 --> Canary25: stable (30m soak)
    Canary25 --> Canary50: stable
    Canary50 --> Full100: stable
    Full100 --> [*]: champion reassigned
    Canary5 --> RolledBack: regression over 2%
    Canary25 --> RolledBack: regression over 2%
    Canary50 --> RolledBack: regression over 2%
    RolledBack --> Challenger: champion never moved
```

Traffic advances 5 → 25 → 50 → 100% only after each stage soaks cleanly; any stage that regresses more than 2% versus the incumbent baseline jumps straight to RolledBack. Note what rollback actually *is* here: the `@champion` alias never moved during the canary, so reverting means draining canary traffic to 0% — there is no registry state to undo, which is why this shape is safe to automate.

### Retraining Trigger Sources

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    sched["Scheduled\nnightly / weekly"] --> pipeline
    perf["Performance drop\nCTR / precision below SLO"] --> pipeline
    drift["Data drift\nPSI > 0.2"] --> pipeline
    label["Label drift\nclass balance shift"] --> pipeline
    event["Upstream event\nschema change / new partition"] --> pipeline
    pipeline["Retraining pipeline\nretrain + revalidate gate"] --> beats{"Beats incumbent?\nchampion / challenger"}
    beats -->|"yes"| deploy([Promote via registry])
    beats -->|"no"| keep([Keep incumbent])

    class sched,keep base
    class perf lossN
    class drift,label,beats mathOp
    class event io
    class pipeline train
    class deploy req
```

Five heterogeneous triggers fan into one retraining pipeline, but a fresh model is never promoted blindly — a champion/challenger gate requires it to beat the incumbent on recent data before the registry swaps it in, otherwise the incumbent stays.

---

## 6. How It Works — Detailed Mechanics

### MLflow Model Logging with Signature

This is the CI gate view of it. For signature depth — what `infer_signature` produces, exactly how enforcement handles reordered, extra, missing and mistyped columns, and what an unsigned model does instead — see [MLflow Deep Dive](../mlflow_deep_dive/mlflow_deep_dive.md).

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
        # enforced at serving time; mismatches raise an MlflowException
        signature = infer_signature(
            model_input=X_train,
            model_output=model.predict(X_train),
        )

        # Log model artifact with signature and sample input for validation.
        mlflow.sklearn.log_model(
            sk_model=model,
            name="model",
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
from mlflow.exceptions import MlflowException
from mlflow.tracking import MlflowClient


@dataclass
class ValidationGate:
    min_auc: float = 0.82
    max_p99_latency_ms: float = 100.0
    max_demographic_parity_diff: float = 0.05
    max_auc_regression_vs_production: float = 0.02  # must not drop more than 2%


def promote_to_challenger(
    run_id: str,
    model_name: str,
    gate: ValidationGate,
) -> bool:
    """
    Register a model version and tag it with the `@challenger` alias in the
    MLflow Model Registry only if all validation gates pass.

    Aliases are mutable named pointers to a version, so one model can carry
    several at once (per-region champions, a shadow candidate) and promotion
    or rollback is a single atomic alias move.

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

    # All gates passed — register and label the version @challenger
    model_version = client.create_model_version(
        name=model_name,
        source=f"runs:/{run_id}/model",
        run_id=run_id,
    )
    client.set_registered_model_alias(
        name=model_name,
        alias="challenger",
        version=model_version.version,
    )
    print(f"PROMOTED: {model_name} v{model_version.version} -> @challenger")
    return True


def _get_production_metric(
    client: MlflowClient,
    model_name: str,
    metric_key: str,
) -> float | None:
    """Metric of the live model, resolved via the `@champion` alias."""
    try:
        champion = client.get_model_version_by_alias(model_name, "champion")
    except MlflowException:
        return None  # no champion yet — first model for this name
    run = client.get_run(champion.run_id)
    return run.data.metrics.get(metric_key)
```

### Kubeflow Pipeline Definition (KFP SDK v2)

```python
from kfp import dsl
from kfp.dsl import Input, Output, Dataset, Model, Metrics


@dsl.component(base_image="python:3.12", packages_to_install=["scikit-learn", "pandas", "mlflow"])
def preprocess_data(
    raw_data_uri: str,
    processed_dataset: Output[Dataset],
) -> None:
    import pandas as pd

    df = pd.read_parquet(raw_data_uri)
    df = df.dropna(subset=["label"])
    df.to_parquet(processed_dataset.path, index=False)


@dsl.component(base_image="python:3.12", packages_to_install=["scikit-learn", "pandas", "mlflow"])
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


@dsl.component(base_image="python:3.12", packages_to_install=["scikit-learn", "mlflow"])
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

    mlflow.sklearn.log_model(clf, name="model", registered_model_name=model_name)
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
  PYTHON_VERSION: "3.12"   # matches the KFP component base images below
  MLFLOW_TRACKING_URI: ${{ secrets.MLFLOW_TRACKING_URI }}

jobs:
  code-quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-python@v7
        with:
          python-version: ${{ env.PYTHON_VERSION }}
      - name: Install dependencies
        # requirements-dev.txt must pin pytest-cov — `--cov` is a plugin flag,
        # not core pytest, and a missing plugin fails the step with "unrecognized arguments"
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
      - uses: actions/checkout@v7
      - uses: actions/setup-python@v7
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
      - uses: actions/checkout@v7
      - uses: actions/setup-python@v7
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
        run: python scripts/register_model.py --alias challenger
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

**In plain terms.** A canary schedule is two numbers multiplied together — `len(stages) x stage_soak_minutes` sets how long the rollout takes, and `sum(stage x soak)` sets how much user traffic a bad model gets to touch before you catch it.

Those two pull in opposite directions, which is the whole design tension: every soak minute you add buys confidence and costs rollout time.

| Symbol | What it is |
|--------|------------|
| `stages` | Traffic fractions stepped through in order. `[0.05, 0.25, 0.50, 1.0]` |
| `stage_soak_minutes` | How long each fraction runs before the next check. `30` |
| `baseline_auc` | The current Production model's AUC — the number the canary is measured against, not an absolute floor |
| `regression` | `baseline_auc - canary_auc`. Positive means the canary is worse |
| `max_regression` | Rollback trigger. `0.02` — 2 AUC points, matching the gate in `ValidationGate` |

**Walk one example.** The default schedule, in wall-clock time and in exposed traffic:

```
  stage    traffic    soak      traffic-minutes (traffic x soak)
  ------   -------    ------    --------------------------------
  1        5%         30 min    0.05 x 30 =  1.5
  2        25%        30 min    0.25 x 30 =  7.5
  3        50%        30 min    0.50 x 30 = 15.0
  4        100%       30 min    1.00 x 30 = 30.0
                      ------                -----
  total                120 min                54.0

  risk window (before 100%)  = 3 x 30      = 90 min of graduated exposure
  exposure before full ramp  = 1.5+7.5+15  = 24 traffic-minutes
  same 120 min at 100% from the start      = 120 traffic-minutes
  reduction  = 1 - 54/120 = 55% less exposed traffic for the same two hours
```

Three checks fire before any user population is fully committed, and the first one costs only `1.5` traffic-minutes — a broken model is caught having touched 5% of users for half an hour rather than all of them.

**Why the soak cannot be shortened to "just check once."** `_fetch_metric` reads an online AUC that needs labelled outcomes to accumulate; at 5% traffic a 5-minute soak may not produce enough labelled events for the AUC to be anything but noise, and the controller would roll back healthy models on sampling variance. The 30-minute soak is a sample-size decision disguised as a timer, which is also why the first stage is the riskiest one to shrink: it has both the smallest traffic share and the same window.

---

## 7. Real-World Examples

**Netflix** open-sourced its automated canary analysis service, Kayenta, with Google in 2018; it is the canary judge inside Spinnaker. The mechanism is a paired comparison, not a raw before/after: Spinnaker stands up a *baseline* cluster running the current version alongside the *canary* cluster running the new one, sends each an equal slice of traffic, and Kayenta compares their metric time series with a Mann-Whitney U test (non-parametric, so it assumes no particular distribution) inside a tolerance band of +/- 0.25 x the estimate. Each metric group scores `pass_count / total_count * 100`; the weighted summary score is compared to `passThreshold` and `marginalThreshold`, and a score below the marginal threshold — or any single metric marked `critical: true` degrading, which zeroes the score outright — aborts the rollout and routes all traffic back to the production cluster. Netflix has not published the specific rollback thresholds it uses for recommendation models.

**Uber Michelangelo** pioneered the feature store concept — productized as Michelangelo Palette — to guarantee offline-online consistency: Hive/Spark for the offline store, Cassandra for the online store, and a shared Transformer framework that executes the *same* transformation logic in both batch training and online serving. That shared execution path, not merely a shared table, is what eliminates the training-serving skew class of bugs.

**Airbnb** does not use Great Expectations; it built its own data quality stack. The Midas certification process requires that a Data Engineer build automated checks — basic sanity checks, definitional testing, and anomaly detection on new data — into the pipeline itself before data can be certified, and Airbnb's Wall framework expresses those checks as YAML configs run by an Airflow helper, with blocking and non-blocking modes so a minor violation warns while a critical one halts the pipeline. The transferable lesson is the same: schema and distribution checks must gate the pipeline, not just annotate it.

**Google Cloud Agent Platform Pipelines** (formerly Vertex AI Pipelines) runs KFP-authored pipelines as a managed service and integrates with Cloud Build for CI. Teams define pipelines as Python DAGs, store the compiled templates in Artifact Registry, and trigger them from Cloud Build.

---

## 8. Tradeoffs

| Approach | Benefit | Cost |
|---|---|---|
| Kubeflow Pipelines (self-managed) | Full control, portable across clouds | High setup and ops overhead; requires Kubernetes expertise |
| Agent Platform Pipelines (managed) | Zero infrastructure management, GCP-native | Vendor lock-in; cost increases at scale |
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

Fix: rollback drills are scheduled monthly. The CI pipeline includes a rollback dry-run step that labels a new model version `@challenger`, then immediately runs the rollback script and verifies that the serving config reverts to the pre-promotion state. Rollback is automated via the model registry: repointing the `@champion` alias at the previous version is a single API call, and because an alias is a pointer rather than a state machine, it needs no knowledge of which version was live before.

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
- MLflow Model Registry — open source; the lifecycle mechanism is mutable **aliases** (`@champion`, `@challenger`) plus key-value tags, so one model version can carry several labels at once and promotion is an atomic alias move. Also provides model signatures and a REST API
- Agent Platform Model Registry (formerly Vertex AI Model Registry) — GCP-managed; integrates with Agent Platform Endpoints
- Amazon SageMaker Model Registry — AWS-managed; Model Groups + Model Package versions with an approval status; integrates with SageMaker Endpoints

**Pipeline Orchestration:**
- Kubeflow Pipelines (KFP v2, SDK 2.x) — Kubernetes-native; KFP SDK for Python DAG definition; portable
- Agent Platform Pipelines — managed KFP on GCP; Cloud Build integration; no infra management
- Apache Airflow — general-purpose; widely used; less ML-native than KFP
- Prefect / Dagster — modern workflow orchestrators; good Python-native experience

**Data Versioning:**
- DVC (Data Version Control) — git-compatible; tracks large files in remote storage (S3, GCS); versioned datasets
- Delta Lake / Iceberg — ACID-compliant table formats; time-travel queries for dataset versioning

**Data Quality:**
- Great Expectations (GX Core 1.x) — schema + distribution expectations; Python-API only (no CLI); integrates into Airflow and CI
- Deepchecks — ML-specific checks including train-test drift, model performance degradation
- Evidently AI — drift reports, data quality reports; integrates with MLflow

**CI/CD:**
- GitHub Actions — YAML-based workflows; free tier for public repos; GitHub-native
- GitLab CI/CD — strong for self-hosted enterprise deployments
- Jenkins — widely deployed in existing enterprise estates; high flexibility, high maintenance overhead

**Serving and Traffic Management:**
- Istio / Envoy — service mesh; fine-grained traffic splitting for canary deployments
- Seldon Core — Kubernetes-native model serving; Core 2 has Experiments (HTTP traffic split for A/B and canary) and shadow deployment via a mirror model built in. Not open source any more: since 22 January 2024 all new releases of Core 1, Core 2, Alibi Explain and Alibi Detect are under the Business Source License 1.1 — free for non-production use, paid for production
- BentoML — model packaging and serving; cloud-agnostic
- Dynamo Triton (NVIDIA) — high-performance GPU serving; supports TensorRT, ONNX, PyTorch, Python backends

**Monitoring:**
- Prometheus + Grafana — metrics collection and dashboards; pull-based
- Evidently AI — open source drift and model performance monitoring
- Arize AI — SaaS; model observability; embedding drift, prediction drift
- Langfuse — LLM-specific observability (see `llm/llm_observability_and_monitoring/`)

**Feature Stores:**
- Feast — open source; offline (Parquet/BigQuery) + online (Redis/DynamoDB) stores
- Tecton — SaaS feature platform; strong consistency guarantees
- Agent Platform Feature Store (formerly Vertex AI Feature Store) — GCP-managed; feature data is registered from a BigQuery source and exposed through Bigtable online serving via feature view instances

---

## 12. Interview Questions with Answers

**Q: What is the difference between a DevOps CI/CD pipeline and an MLOps CI/CD pipeline?**
**Short:** MLOps adds data-quality and model-quality gates on top of the code correctness a DevOps pipeline already tests.
A DevOps pipeline tests code correctness and deploys a deterministic binary artifact. An MLOps pipeline adds two additional dimensions: data quality (schema, distribution) and model quality (performance gates, fairness, latency SLAs). A software artifact either passes tests or fails; a model artifact can pass all code tests while silently degrading due to data distribution shift, which is why model-specific validation gates are mandatory in MLOps.

**Q: What is training-serving skew and how do you detect it in CI?**
**Short:** Skew is features differing between training and serving, detected by asserting served predictions match offline-computed ones.
Training-serving skew occurs when features presented to the model at serving time differ from what the model saw during training. This typically happens because preprocessing steps (scaling, encoding, imputation) are applied during training but omitted or applied differently at serving. Detection in CI: write an integration test that sends known raw input vectors to the deployed model server and asserts that predictions match expected outputs computed offline with the full training pipeline. Also compare mean and standard deviation of each feature between the offline feature store and online serving queries; flag any feature with >5% relative difference.

**Q: Explain how MLflow Model Registry tracks a model's lifecycle and how you automate promotion.**
**Short:** MLflow tracks lifecycle with mutable aliases like champion and challenger, and CI automates promotion by moving the alias after gates pass.
MLflow tracks lifecycle with mutable **aliases** such as `@champion` and `@challenger` plus key-value tags. An alias is a named pointer to one version, so a single model can carry several at once — per-region champions, a shadow candidate — which a fixed promotion ladder cannot express. Automation: CI trains a model, logs it, calls `create_model_version()`, then runs validation gates (AUC >= baseline, latency SLA, fairness checks); if all pass it calls `set_registered_model_alias(name, "challenger", version)`. A separate deployment job, triggered by a merge to main or manual approval, repoints `@champion` to that version — a single atomic alias move, which is also what makes rollback one API call back to the prior version.

**Q: How do you implement automatic rollback in a canary deployment for an ML model?**
**Short:** A canary controller polls a live metric and atomically shifts all traffic weight back to the production model on regression.
The canary controller polls a real-time metric (AUC from an online evaluation service, or a business proxy metric like conversion rate) every N minutes. If the metric regresses beyond a defined threshold (e.g., AUC drops > 2% from the production baseline), the controller calls the serving infrastructure API to set canary traffic weight to 0% and production weight to 100% — in Istio that is a patch to `spec.http[].route[].weight`, where the weight lives on each route destination, not on the HTTPRoute itself. Simultaneously it drops the `@challenger` alias from the candidate version, leaving `@champion` untouched, and sends an alert. The critical requirement is that rollback be atomic from the user's perspective: the weight change must reach every proxy before the next request wave, and the controller must verify the new split took effect rather than assume the API call succeeded.

**Q: What is Population Stability Index (PSI) and when do you trigger retraining based on it?**
**Short:** PSI measures how much a feature's distribution shifted from training to production, with retraining typically triggered above 0.2.
PSI measures how much the distribution of a feature has shifted between a reference period (training data) and a current period (recent production traffic). PSI = sum over bins of (actual_fraction - expected_fraction) * ln(actual_fraction / expected_fraction). PSI < 0.1: no significant shift; 0.1–0.2: moderate shift, monitor; > 0.2: significant shift, trigger retraining. A common production setup computes PSI daily on the top 20 features and triggers a retraining pipeline when PSI > 0.2 on any of the top 5 features by feature importance.

**Q: How does a feature store solve the offline-online consistency problem?**
**Short:** A feature store defines feature computation once and writes it to both the offline training store and the online serving store.
A feature store maintains a single feature computation definition that writes to both an offline store for batch training and an online store for low-latency inference. The offline store is typically S3 Parquet or BigQuery; the online store is Redis or Bigtable. Training pipelines read from the offline store; the serving layer reads from the online store using the same feature keys. The computation logic is defined once and executed in both contexts, eliminating the divergence that occurs when data science teams write Pandas code for training and engineering teams independently write SQL or Java for serving.

**Q: What data tests should run in CI before a model is retrained?**
**Short:** Run schema, null-rate, distribution, referential integrity, volume, and feature-store consistency checks before retraining.
Schema validation: required columns present, correct dtypes, no unexpected columns. Null rate: null fraction per column <= defined threshold (e.g., 1% for label column, 10% for optional features). Distribution checks: mean and standard deviation of numeric features within 3 standard deviations of historical baseline. Referential integrity: foreign keys resolve to valid entity IDs. Volume check: row count within expected range (guards against partial data loads). Feature store consistency: online store feature statistics within 5% of offline store statistics for the same time window.

**Q: How do you handle a situation where a new model version passes all CI gates but degrades in production?**
**Short:** Roll back to the previous version immediately, then investigate whether the CI holdout no longer represents current production data.
First, trigger automatic rollback via the canary controller if the degradation is caught within the canary window. If the model reached 100% traffic before degradation was detected, manually transition the previous Production model version back to Production in the registry and set traffic to 0% on the degraded version. Then conduct a root cause analysis: compare input feature distributions between the period when the old model was healthy and the current period; check whether a data pipeline change coincided with the deployment; run the model validation suite against the current production feature distribution rather than the CI holdout set. The common cause is that the CI holdout dataset did not represent the current data distribution (covariate shift since the last training run).

**Q: What is the difference between shadow mode and canary deployment in ML?**
**Short:** Shadow mode logs predictions without serving them, while canary deployment actually serves a small real-traffic slice to users.
In shadow mode, the new model receives a copy of all live requests and produces predictions, but those predictions are never shown to users — they are logged for offline comparison against the production model. Shadow mode has zero user-facing risk but does not validate user behavior (e.g., click-through rate) on the new model's output. Canary deployment routes a small fraction of real traffic (5%) to the new model, whose predictions are actually served to users. Canary validates true user-facing metrics but carries a small risk that the fraction of users receiving canary predictions may have a degraded experience if the model underperforms.

**Q: How do you version datasets in an ML project and why is it insufficient to just track the S3 path?**
**Short:** An S3 path is mutable, so dataset versioning needs an immutable reference like a DVC hash or a table snapshot id.
An S3 path is mutable — the same path can point to different data at different times (overwrite, append, schema evolution). Dataset versioning requires an immutable reference: a git commit SHA of a DVC `.dvc` file (which records the S3 URI + SHA256 of the data), or an Iceberg/Delta Lake table snapshot ID (a monotonically increasing integer that points to an immutable manifest). The MLflow run record stores this immutable reference, so any model can be traced back to the exact byte-for-byte dataset used to train it, enabling full reproducibility and regulatory audit trails.

**Q: What is a model signature in MLflow and why does it matter for CI?**
**Short:** A model signature is a type contract for inputs and outputs that MLflow enforces at serving time to catch malformed requests.
A model signature in MLflow declares the expected column names and dtypes (and tensor shapes) for model inputs and outputs. It is a type contract, not a value-range constraint, so an in-range check still has to be a separate data test. It is inferred from actual training data using `infer_signature(X_train, model.predict(X_train))` and stored as JSON alongside the model artifact. At serving time MLflow's pyfunc wrapper enforces the signature on every request before inference: missing columns or uncastable types raise an `MlflowException` (MLflow has no `ModelSignatureException`), while extra columns are ignored and safe type conversions are performed silently. In CI, the integration test sends a malformed request to catch any serving code that bypasses signature enforcement. This provides the serving-layer equivalent of an API contract test.

**Q: Why must preprocessing artifacts like scalers and encoders be bundled with the model, not stored separately?**
**Short:** A separately stored scaler can be forgotten at serving time, silently feeding the model raw features and degrading predictions.
A separately stored scaler can be forgotten or applied differently at serving time, feeding the model raw features and silently degrading predictions with no error. Bundle preprocessing and the estimator into one artifact (an sklearn `Pipeline`) and log it as a single unit, so `load_model` always returns the complete transform-plus-predict path. This eliminates an entire class of training-serving skew that produces plausible-but-wrong outputs rather than crashes.

**Q: What is the CACE principle in ML systems?**
**Short:** CACE means changing anything changes everything, since every feature interacts through the learned model with no isolated parts.
CACE means "Changing Anything Changes Everything" — in ML there are no isolated features, because every input interacts through the learned model. Removing a feature, changing its encoding, or retraining on new data can shift the model's behavior on inputs that seem unrelated, so you cannot reason about changes locally the way you can with modular code. The practical consequence is that any change requires full retraining plus end-to-end evaluation, not a unit test on the changed part alone.

**Q: Should every data drift alert trigger an automatic retraining pipeline?**
**Short:** No, auto-retraining on every drift signal risks a retraining storm, so gate it on confirmed performance drops instead.
No — auto-retraining on every drift signal risks a retraining storm that burns compute and can promote a model fit to transient noise. Drift is a leading indicator; gate retraining on a confirmed performance drop, sustained multi-feature drift, and availability of fresh trustworthy labels, with a champion/challenger evaluation before promotion. Otherwise a single noisy feature or a one-day spike triggers needless retrains that may degrade production.

**Q: What is continuous training (CT) and how does it differ from CI and CD?**
**Short:** Continuous training automatically regenerates the model on new data, a pipeline axis CI and CD alone do not provide.
Continuous training is automatic retraining of the model on new data — a third pipeline axis that DevOps CI/CD does not have. CI validates code and data, CD ships the artifact, and CT regenerates the artifact itself when data drifts or on a schedule, then hands the new model back through the same CI/CD gates. MLOps Level 1 automates CT; Level 2 wraps full CI/CD around it.

**Q: Why is reproducibility harder for ML pipelines, and what four things must you version to achieve it?**
**Short:** ML results depend on data and randomness, so reproducing them requires versioning the dataset, code, hyperparameters, and environment.
An ML result depends on data and randomness, not just code, so the same script can produce a different model unless every input is pinned. To reproduce a model you must version all four of: the dataset (DVC SHA or table snapshot id), the code (git commit), the hyperparameters (logged to MLflow), and the environment (Docker image digest). Miss any one — most often the dataset or a random seed — and the "same" run diverges beyond tolerance.

**Q: What are the three MLOps maturity levels and how do you know which one you need?**
**Short:** The three levels are manual, automated training, and full CI/CD, chosen by a model's blast radius and retraining cadence.
Level 0 is manual notebooks, Level 1 automates the training pipeline, and Level 2 adds full CI/CD with gates, a registry, canary deploys, and drift-triggered retraining. Choose by blast radius and cadence: a one-off analysis stays at 0, a stable internal model at 1, and a revenue- or safety-critical model retrained more than monthly needs Level 2. Jumping straight to Level 2 for a prototype is over-engineering.

**Q: How do you keep a retraining pipeline from silently learning on corrupted or poisoned data?**
**Short:** Put automated data-validation gates before training so corrupted data fails the pipeline instead of reaching the model.
Put automated data-validation gates before training so bad data fails the pipeline instead of flowing into the model. Great Expectations (or equivalent) enforces a schema contract — required columns, dtypes, null-rate and range bounds, row-count volume checks — and distribution checks flag values outside historical norms; for adversarial risk, add anomaly detection on new partitions and require human approval for large shifts. The gate must block the run, not merely warn.

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

**Scenario (illustrative — a composite, not a published company case):** A ride-sharing company (12M daily rides, 4M active drivers) runs a surge pricing model that updates every 5 minutes. The current manual promotion process takes 3 days from "model passes offline eval" to "model in production", causing 4-6 stale model incidents per quarter where drift degrades pricing accuracy. The goal: implement a CI/CD pipeline with MLflow Registry + GitHub Actions that promotes models automatically when AUC-ROC >= 0.92 and MAPE <= 8% on a rolling 7-day holdout, with promotion-to-serving in under 45 minutes and automatic rollback if production error rate exceeds 2x baseline within 30 minutes.

**Architecture:**
```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    dataPipeline["Data Pipeline<br/>hourly Spark job<br/>Feature refresh: supply - demand -<br/>weather - events - elasticity"]
    mlflowTrack["MLflow Experiment Tracking<br/>Train XGBoost / LightGBM<br/>Params - metrics - schema logged<br/>Registry: alias @challenger"]
    ciCheckout["1. Checkout code + artifact<br/>GitHub Actions - on tag push"]
    ciGate{"2. Offline validation gate<br/>AUC-ROC >= 0.92 - MAPE <= 8%<br/>PSI <= 0.15 - schema check"}
    ciIntegration["3. Integration test<br/>shadow traffic, 15 min"]
    ciPromote["4. MLflow alias move<br/>@challenger to @champion"]
    k8sDeploy@{ icon: "logos:kubernetes", form: "square", label: "5. Deploy to K8s<br/>rolling update, 10% canary", pos: "b", h: 44 }
    modelServing["Model Serving<br/>FastAPI + TorchServe, 400 RPS<br/>Istio weighted split 10/90"]
    prometheus@{ icon: "logos:prometheus", form: "square", label: "Prometheus metrics<br/>rate - errors - p99", pos: "b", h: 44 }
    rollbackMon["Automated Rollback Monitor<br/>canary error_rate vs baseline<br/>rollback if ratio over 2.0 in 30min"]

    dataPipeline --> mlflowTrack --> ciCheckout --> ciGate --> ciIntegration --> ciPromote --> k8sDeploy --> modelServing --> prometheus --> rollbackMon

    class dataPipeline base
    class mlflowTrack train
    class ciCheckout,ciIntegration,ciPromote req
    class ciGate mathOp
    class modelServing req
    class rollbackMon lossN
```

**Step-by-step implementation:**

```python
from __future__ import annotations
import mlflow
import mlflow.lightgbm
from mlflow.tracking import MlflowClient
import lightgbm as lgb
import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score, mean_absolute_percentage_error

EXPERIMENT_NAME = "surge_pricing_model"
MODEL_NAME = "surge_pricing_lgbm"
PROMOTION_THRESHOLDS: dict[str, float] = {
    "auc_roc": 0.92,
    "mape": 0.08,
    "psi_score": 0.15,
}

SURGE_THRESHOLD = 1.2   # multiplier at or above which a ride counts as "surging"

def train_and_log_model(
    X_train: pd.DataFrame,
    y_train: pd.Series,               # continuous surge multiplier, >= 1.0
    X_val: pd.DataFrame,
    y_val: pd.Series,
    params: dict,                     # LightGBM regression objective
    feature_schema: dict[str, str],   # col -> dtype mapping
) -> str:
    mlflow.set_experiment(EXPERIMENT_NAME)

    with mlflow.start_run() as run:
        mlflow.log_params(params)
        mlflow.log_dict(feature_schema, "feature_schema.json")

        dtrain = lgb.Dataset(X_train, label=y_train)
        dval = lgb.Dataset(X_val, label=y_val, reference=dtrain)
        model = lgb.train(
            params,
            dtrain,
            valid_sets=[dval],
            num_boost_round=1000,
            callbacks=[lgb.early_stopping(50), lgb.log_evaluation(0)],
        )

        val_preds = model.predict(X_val)          # predicted surge multiplier
        # The two gates ask different questions of the same regression output.
        # MAPE = "is the magnitude right?" on the continuous target.
        # AUC-ROC = "does it rank surge events correctly?" against a binarised
        # label. Feeding the 0/1 label to MAPE instead would divide by ~0 and
        # return a meaningless ~1e15, so the gate could never pass.
        mape = mean_absolute_percentage_error(y_val, val_preds)
        auc_roc = roc_auc_score((y_val >= SURGE_THRESHOLD).astype(int), val_preds)

        mlflow.log_metric("val_auc_roc", auc_roc)
        mlflow.log_metric("val_mape", mape)
        mlflow.lightgbm.log_model(model, name="model")

        # Register the version and label it @challenger; the deploy job later
        # moves @champion onto the same version if the canary stays clean.
        model_uri = f"runs:/{run.info.run_id}/model"
        mv = mlflow.register_model(model_uri, MODEL_NAME)
        MlflowClient().set_registered_model_alias(MODEL_NAME, "challenger", mv.version)
        print(f"Run {run.info.run_id}: AUC-ROC={auc_roc:.4f}, MAPE={mape:.4f}")
        return run.info.run_id
```

```python
import json

import mlflow.artifacts


def compute_psi(
    baseline_scores: np.ndarray,
    candidate_scores: np.ndarray,
    n_bins: int = 10,
) -> float:
    bins = np.percentile(baseline_scores, np.linspace(0, 100, n_bins + 1))
    bins[0] = -np.inf
    bins[-1] = np.inf

    baseline_hist, _ = np.histogram(baseline_scores, bins=bins)
    candidate_hist, _ = np.histogram(candidate_scores, bins=bins)

    baseline_pct = baseline_hist / baseline_hist.sum() + 1e-6
    candidate_pct = candidate_hist / candidate_hist.sum() + 1e-6

    psi = float(np.sum((candidate_pct - baseline_pct) * np.log(candidate_pct / baseline_pct)))
    return psi

def validate_model_for_promotion(
    client: MlflowClient,
    run_id: str,
    X_holdout: pd.DataFrame,
    y_holdout: pd.Series,
    production_scores: np.ndarray,
    feature_schema_production: dict[str, str],
) -> dict[str, bool | float]:
    model_uri = f"runs:/{run_id}/model"
    model = mlflow.lightgbm.load_model(model_uri)

    # Schema compatibility check. download_artifacts returns a LOCAL PATH (str),
    # not the parsed artifact — calling .keys() on it raises AttributeError.
    schema_path = mlflow.artifacts.download_artifacts(
        run_id=run_id, artifact_path="feature_schema.json"
    )
    with open(schema_path) as f:
        candidate_schema: dict[str, str] = json.load(f)
    schema_ok = (candidate_schema == feature_schema_production)   # names AND dtypes

    candidate_preds = model.predict(X_holdout)
    auc_roc = roc_auc_score((y_holdout >= SURGE_THRESHOLD).astype(int), candidate_preds)
    mape = mean_absolute_percentage_error(y_holdout, candidate_preds)
    psi = compute_psi(production_scores, candidate_preds)

    results = {
        "auc_roc": auc_roc,
        "mape": mape,
        "psi_score": psi,
        "schema_compatible": schema_ok,
        "auc_roc_pass": auc_roc >= PROMOTION_THRESHOLDS["auc_roc"],
        "mape_pass": mape <= PROMOTION_THRESHOLDS["mape"],
        "psi_pass": psi <= PROMOTION_THRESHOLDS["psi_score"],
        "schema_pass": schema_ok,
    }
    results["all_pass"] = all(
        results[k] for k in ["auc_roc_pass", "mape_pass", "psi_pass", "schema_pass"]
    )
    return results
```

**Stated plainly.** `(candidate_pct - baseline_pct) * log(candidate_pct / baseline_pct)` asks, bin by bin, "how much mass moved, and how big a proportional move was that?" — then sums the answers into one number that is zero when nothing shifted and grows fast when mass piles up somewhere new.

Both factors carry the same sign, so every term is non-negative: PSI can only be zero or positive, and it cannot cancel a shift in one bin against a shift in another.

| Symbol | What it is |
|--------|------------|
| `baseline_scores` | Score distribution of the current Production model — the reference the candidate is compared to |
| `n_bins = 10` | Number of buckets. Cut on baseline *percentiles*, so `baseline_pct` is 10% in every bin by construction |
| `baseline_pct` | Expected fraction of scores in a bin. `0.10` here, because of the percentile binning |
| `candidate_pct` | Actual fraction of the candidate's scores landing in that same bin |
| difference term | `candidate_pct - baseline_pct` — absolute mass moved |
| log-ratio term | `log(candidate_pct / baseline_pct)` — relative size of that move; `0` when the bin is unchanged |
| `+ 1e-6` | Guard so an empty bin does not produce `log(0)` and blow the score up to infinity |
| `psi_score: 0.15` | This pipeline's promotion gate — stricter than the usual `0.2` retraining trigger |

**Walk one example.** Percentile binning makes the baseline column all `0.10`, so only the candidate row varies:

```
  bin  base   cand   diff    log(cand/base)   term
   1   0.10   0.06   -0.04      -0.5108      0.02043
   2   0.10   0.08   -0.02      -0.2231      0.00446
   3   0.10   0.09   -0.01      -0.1054      0.00105
   4   0.10   0.10    0.00       0.0000      0.00000
   5   0.10   0.10    0.00       0.0000      0.00000
   6   0.10   0.10    0.00       0.0000      0.00000
   7   0.10   0.11   +0.01      +0.0953      0.00095
   8   0.10   0.12   +0.02      +0.1823      0.00365
   9   0.10   0.12   +0.02      +0.1823      0.00365
  10   0.10   0.12   +0.02      +0.1823      0.00365
                                             -------
                                    PSI    = 0.0378   <- below 0.1: no meaningful shift
```

Push the same shape further and the gate starts biting:

```
  cand = [.03 .05 .07 .09 .10 .11 .13 .14 .14 .14]  ->  PSI = 0.1799
         blocked by this pipeline's 0.15 promotion gate, but under the 0.2 retrain trigger

  cand = [.02 .04 .06 .08 .10 .12 .14 .14 .15 .15]  ->  PSI = 0.2797
         over 0.2 -- significant shift, retraining territory
```

**Why the extreme bins dominate.** Bin 1 alone contributes `0.02043`, over half the total `0.0378`, even though bins 8-10 each moved by a larger absolute `+0.02`. The log-ratio is what does it: losing 40% of a bin's mass (`0.10 -> 0.06`) is a bigger proportional move than gaining 20% (`0.10 -> 0.12`), so PSI is most sensitive to bins that empty out. That is deliberate — a score band the model has stopped producing at all is exactly the failure that breaks downstream thresholds calibrated on the old distribution.

```python
import json
import subprocess
import time
import requests

def set_traffic_split(canary_pct: int, host: str = "surge-pricing") -> None:
    """
    Istio puts `weight` on each route DESTINATION, i.e. spec.http[].route[].weight —
    NOT on the HTTPRoute itself. A patch shaped {"http":[{"weight":10},...]} is
    silently invalid and leaves the split unchanged.
    """
    patch = {"spec": {"http": [{"route": [
        {"destination": {"host": host, "subset": "canary"}, "weight": canary_pct},
        {"destination": {"host": host, "subset": "stable"}, "weight": 100 - canary_pct},
    ]}]}}
    subprocess.run([
        "kubectl", "patch", "virtualservice", "surge-pricing-vs",
        "--type", "merge", "--patch", json.dumps(patch),
    ], check=True)

def promote_and_deploy(
    client: MlflowClient,
    run_id: str,
    model_name: str = MODEL_NAME,
    canary_weight: float = 0.1,
    canary_monitor_minutes: int = 30,
    error_rate_multiplier_threshold: float = 2.0,
) -> bool:
    # The candidate carries @challenger; @champion stays on the live model for the
    # whole canary, so rollback is "do nothing to the registry" rather than "undo".
    candidate = client.get_model_version_by_alias(model_name, "challenger")
    assert candidate.run_id == run_id, "challenger alias does not point at this run"

    # Deploy canary via kubectl
    subprocess.run([
        "kubectl", "set", "image",
        "deployment/surge-pricing-canary",
        f"model-server=registry/surge-model:{run_id}",
    ], check=True)
    set_traffic_split(int(canary_weight * 100))

    # Monitor canary error rate for 30 minutes
    baseline_error_rate = get_prometheus_metric("surge_pricing_error_rate{canary='false'}")
    poll_interval_s = 60
    for minute in range(canary_monitor_minutes):
        time.sleep(poll_interval_s)
        canary_error_rate = get_prometheus_metric("surge_pricing_error_rate{canary='true'}")
        if canary_error_rate > baseline_error_rate * error_rate_multiplier_threshold:
            print(f"Canary error rate {canary_error_rate:.4f} > {baseline_error_rate * 2:.4f}; rolling back")
            set_traffic_split(0)   # drain traffic FIRST, then touch the workload
            subprocess.run(["kubectl", "rollout", "undo", "deployment/surge-pricing-canary"], check=True)
            client.delete_registered_model_alias(model_name, "challenger")
            return False

    # Clean window: cut over to 100% and move @champion to the new version
    set_traffic_split(100)
    client.set_registered_model_alias(model_name, "champion", candidate.version)
    client.delete_registered_model_alias(model_name, "challenger")
    return True

def get_prometheus_metric(query: str) -> float:
    resp = requests.get(
        "http://prometheus:9090/api/v1/query",
        params={"query": query},
        timeout=5,
    )
    return float(resp.json()["data"]["result"][0]["value"][1])
```

**Key pitfalls (3 with BROKEN->FIX):**

**Pitfall 1 - Using validation set AUC as the promotion gate without holdout temporal split:**
```python
# BROKEN: validation set overlaps temporally with training window
# LightGBM optimised on val; AUC on val is inflated by hyperparameter tuning
train_df = df[df["date"] < "2025-01-01"]
val_df = df[(df["date"] >= "2024-12-01") & (df["date"] < "2025-01-01")]  # in training window
# AUC on this val = 0.944 -> passes gate; true 7-day forward holdout AUC = 0.906 -> fails

# FIX: strict temporal holdout; promotion gate uses ONLY future data never seen during training
train_df = df[df["date"] < "2025-01-01"]
holdout_df = df[(df["date"] >= "2025-01-01") & (df["date"] < "2025-01-08")]  # future window
# No overlap between training and promotion evaluation data
```

**Pitfall 2 - MLflow model registration without schema validation causes silent type mismatches:**
```python
# BROKEN: register model without logging feature schema;
# new model trained with "driver_supply" as float64, production serves int32 -> score drift
mlflow.lightgbm.log_model(model, name="model")   # no schema logged
# Production serving converts features to training dtype -> wrong predictions for int features

# FIX: log feature schema as artifact; validate compatibility before promotion
feature_schema = {col: str(dtype) for col, dtype in X_train.dtypes.items()}
mlflow.log_dict(feature_schema, "feature_schema.json")
# CI gate checks schema before promoting: all column names and dtypes must match
```

**Pitfall 3 - Promoting directly to 100% traffic without canary causes widespread incidents:**
```python
# BROKEN: immediate full traffic switch on promotion
# $ kubectl set image deployment/surge-pricing model-server=registry/surge-model:v2
# If model has latency regression (p99: 12ms -> 180ms), 100% of users affected immediately

# FIX: 10% canary for 30 minutes with automated rollback on error spike
set_traffic_split(10)   # weight sits on spec.http[].route[], see helper above
# Monitor p99 latency and error_rate; auto-rollback if degraded
# Only then ramp to 100% after 30-minute clean window
```

**What it means.** The canary window is a sample-size budget: `RPS x canary_weight x window_seconds` is the number of real requests you get to judge the model on, and it has to be large enough that a 2x error-rate spike is a signal rather than noise.

A 30-minute window is not a superstition — it is the smallest window that clears three separate bars at this traffic level, shown below.

| Symbol | What it is |
|--------|------------|
| `400 RPS` | Production request rate for the surge-pricing service |
| `canary_weight = 0.1` | Fraction of that traffic routed to the new model version |
| `canary_monitor_minutes = 30` | Observation window before the ramp to 100% |
| `error_rate_multiplier_threshold = 2.0` | Rollback fires when canary error rate exceeds 2x the baseline model's |
| scrape interval | Prometheus pull cadence, `15 s` — sets how many independent metric samples the window holds |
| model refresh | Surge model updates every `5 min` — the window must span several of these to be representative |

**Walk one example.** The 30-minute, 10% canary at 400 RPS:

```
  total requests in the window   = 400 x 30 x 60          = 720,000
  requests hitting the canary    = 720,000 x 0.10         =  72,000
  requests protected from a bad model                     = 648,000  (90%)

  Prometheus samples in window   = 30 x 60 / 15           =     120 data points
  surge model refresh cycles     = 30 / 5                 =       6 cycles observed
```

The alternative — the BROKEN branch above — exposes all `720,000` requests, and a latency regression from `12 ms` to `180 ms` (15x) hits every one of them from the first second.

Widen the ramp and the same arithmetic gives the exposure at each step of the 5/25/50/100 schedule, per 30-minute stage:

```
   5%  ->  720,000 x 0.05 =  36,000 requests exposed
  25%  ->  720,000 x 0.25 = 180,000
  50%  ->  720,000 x 0.50 = 360,000
 100%  ->  720,000 x 1.00 = 720,000
```

**Why shrinking the window is the wrong economy.** Cut the canary to 5 minutes and the sample drops to `400 x 5 x 60 x 0.1 = 12,000` requests, `20` Prometheus points, and a single model refresh cycle — enough to catch a model that is broken outright, not enough to distinguish a real 2x error-rate move from one unlucky scrape. The 30-minute window is what makes the *automatic* rollback trustworthy; without the sample size behind it, the controller either flaps on noise or is quietly ignored by the on-call.

**Metrics and results** (illustrative outcomes for the composite scenario above — no company published these):

| Metric | Before (manual) | After (MLflow + GH Actions CI) |
|---|---|---|
| Promotion cycle time | 3 days | 42 min |
| Stale model incidents/quarter | 4-6 | 0 |
| Promotion gate failure rate | N/A | 18% (correctly blocked) |
| Canary rollbacks triggered | N/A | 3 (prevented 3 incidents) |
| Model AUC-ROC (production) | 0.89 | 0.93 |
| MAPE (surge windows) | 11.2% | 6.8% |
| Pricing accuracy (revenue impact) | baseline | +$4.2M/month |
| Time-to-detect model drift | ~72 hr | 30 min |
| Engineering hours per promotion | 8 hr | 0 (fully automated) |

**What the formula is telling you.** Every row here is the same division — `before / after` — but only two of them are speedups that matter operationally: promotion cycle time and time-to-detect, because those two multiplied together are how long a bad model stays live.

The model-quality rows (AUC, MAPE) are consequences, not causes; they improved because a faster cycle means production is running a fresher model, not because the model architecture changed.

| Symbol | What it is |
|--------|------------|
| promotion cycle time | Wall clock from "passes offline eval" to "serving production traffic" |
| 45 min | The stated target in the scenario — the number the 42 min result is judged against |
| time-to-detect | Lag between drift starting and the pipeline noticing it |
| stale model incidents | Quarters in which drift degraded pricing before anyone promoted a fix. `4-6` before, `0` after |
| gate failure rate | `18%` of candidate models correctly rejected — evidence the gate is doing work, not a defect |

**Walk one example.** The two rows that compound, converted to a common unit:

```
  promotion cycle
    before : 3 days   = 3 x 24 x 60           = 4,320 min
    after  :                                       42 min
    ratio  : 4,320 / 42                       = 102.9x faster
    saved  : 4,320 - 42 = 4,278 min           = 71.3 engineer-free hours per promotion
    target : 42 min vs the 45 min goal        = 3 min of headroom -- it just cleared

  time-to-detect drift
    before : ~72 hr   = 72 x 60               = 4,320 min   (same figure, by coincidence)
    after  :                                       30 min   = the canary window
    ratio  : 4,320 / 30                       = 144x faster

  worst-case exposure to a stale model = detect + promote
    before : 4,320 + 4,320                    = 8,640 min = 6.0 days
    after  :    30 +    42                    =    72 min = 1.2 hours
```

That 6 days versus 72 minutes is the actual explanation for `4-6 stale model incidents per quarter -> 0`: the incident window shrank below the timescale on which surge-pricing drift does damage.

**Why the gate failure rate is a healthy number, not a problem.** An `18%` rejection rate means roughly one candidate in five was worse than the incumbent and got stopped — those are the 3 canary rollbacks plus the offline rejections. A gate that never fails is either mis-specified or measuring nothing; the number to watch is not the failure rate itself but whether it is drifting upward, which would say the training pipeline has started producing worse models.

**Interview discussion points:**

**What is the difference between the `@challenger` and `@champion` aliases in MLflow Model Registry?** Both are mutable named pointers to a model version — metadata labels, not deployment states. `@challenger` means "passed offline eval, candidate for deployment"; `@champion` means "the version serving live traffic." The CI pipeline enforces that only models passing the validation gate (AUC-ROC, MAPE, PSI, schema checks) get `@challenger`, and only a clean canary window moves `@champion`. Aliases exist precisely because a fixed promotion ladder cannot express workflows like champion/challenger or per-region champions — a version can hold any number of aliases at once, and moving one is atomic. The registry remains a single source of truth for which version should be served, decoupling the promotion decision from the mechanics of deployment.

**Why is PSI used as a promotion gate criterion alongside AUC-ROC?** PSI measures how much the new model's score distribution differs from the production model's score distribution. A new model can achieve high AUC-ROC on the validation holdout while generating completely different score distributions in production, causing downstream systems (fraud score thresholds, pricing bands) calibrated to the old distribution to behave incorrectly. This pipeline's PSI > 0.15 score-distribution gate (deliberately stricter than the 0.2 threshold conventionally used to trigger *retraining*) flags that structural mismatch before deployment, preventing silent system breakage that AUC-ROC alone would not detect.

**How does the 10% canary deployment protect against latency regressions that offline eval misses?** Offline eval computes metrics on a static dataset using single-process prediction; it does not reflect production conditions: concurrent requests, JVM warm-up, serialisation overhead, and network latency to feature stores. A model that scores in 4ms in batch evaluation may have p99 latency of 180ms under 400 RPS concurrent load due to memory pressure or I/O serialisation. The 10% canary exposes the new model to real production traffic and load patterns, with Prometheus scraping p99 latency and error rate every 15 seconds. If p99 exceeds 2x baseline during the 30-minute window, the rollback fires before the majority of users are affected.

**What is the risk of using early stopping patience of 50 rounds in LightGBM training and how does it interact with MLflow logging?** Early stopping halts training when the validation metric fails to improve for 50 consecutive rounds. The risk is that patience is a *noise* budget, not a convergence budget: on a small or noisy validation set the metric can plateau for 50 rounds and then resume improving, so a patience that is too short stops the model short of its real optimum (genuine underfit), while a patience that is too long on a set you also tune on inflates `best_iteration` toward that set. Two logging traps follow. First, `lgb.train` returns the *full* booster — all 130 trees if it stopped at 130 — and only `Booster.predict` defaults to `num_iteration = best_iteration`; anything that reloads the artifact and passes an explicit `num_iteration` silently gets a different, over-boosted model. Second, `mlflow.lightgbm.autolog()` records the `num_boost_round` you passed (1000, the ceiling), not the round actually used, so the run is not reproducible from its own params. The fix is to log the real stopping point explicitly: `mlflow.log_metric("best_iteration", model.best_iteration)`.

**How do you handle feature store schema drift between model training and serving?** At training time, each feature's name and dtype is serialised to a JSON artifact in MLflow alongside the model. At serving time, the prediction handler loads this schema and validates incoming feature vectors against it before prediction. If a feature has been renamed (e.g., "demand_index_v2" -> "demand_index_v3") or its dtype changed (float32 -> int32), the schema check fails at deployment time rather than silently producing wrong predictions. The schema artifact is versioned with each model version in the registry, ensuring schema and model are always co-located and auditable.

**What monitoring beyond error rate and latency should be applied to the surge pricing model post-deployment?** Three additional monitors are essential: (1) prediction distribution monitoring - track mean and standard deviation of model output scores using a rolling 1-hour window; sudden shift > 2 standard deviations from historical baseline triggers an alert; (2) feature drift monitoring - compute PSI for the top 10 most important features daily, with PSI > 0.2 triggering retraining; (3) business metric monitoring - track revenue per ride and cancellation rate at 5-minute granularity aligned with model update cycles; degradation > 5% from 7-day rolling average triggers an incident despite healthy technical metrics.
