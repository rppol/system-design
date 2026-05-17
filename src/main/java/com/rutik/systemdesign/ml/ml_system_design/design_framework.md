# ML Design Interview Framework — 6-Step Deep Dive

## 1. Concept Overview

The 6-step ML design interview framework is a structured methodology for scoping, designing, and communicating machine learning systems in a 45-60 minute interview. Each step corresponds to a distinct phase of real ML system development. The framework prevents the most common interview failure mode: jumping to model architecture before understanding requirements, data, or business context.

The six steps are:
1. Clarify requirements — business and technical constraints
2. Problem formulation — translate business problem to ML task
3. Data and features — sources, engineering, and storage
4. Model selection and training — architecture, training strategy, validation
5. Serving and inference — latency budget, serving pattern, caching
6. Monitoring and iteration — drift detection, retraining, experimentation

This document walks through the framework using a concrete running example: "Design a content recommendation system for a video platform with 100 million users."

---

## 2. Intuition

One-line analogy: the 6-step framework is a funnel — you start wide (business context) and progressively narrow to specific technical decisions, ensuring every decision is justified by an upstream constraint.

Mental model: think of each step as answering one key question:
1. What problem are we actually solving and how will we measure success?
2. How do we frame this as an ML task?
3. What raw material (data) do we have, and how do we shape it into features?
4. What model fits our data size, latency budget, and quality requirement?
5. How does the model get from training to serving predictions in real-time?
6. How do we know if the model is still working next week?

Key insight: interviewers are not just evaluating your knowledge of ML algorithms — they are evaluating whether you think like a senior engineer who ships systems. A candidate who asks "what is our latency requirement?" before drawing a neural network architecture signals production awareness.

---

## 3. Core Principles

**Requirements before architecture**: every architectural decision must be traceable to a stated requirement. Never choose a model or database technology without first identifying the constraint it addresses.

**Explicit metric hierarchy**: define the primary business metric (e.g., weekly active users), primary ML metric (e.g., NDCG@10), and guardrail metrics (e.g., P99 latency < 100ms, content policy violation rate < 0.01%). Optimization of the primary metric must not degrade guardrail metrics.

**Data availability drives model choice**: the best model for a dataset of 10,000 labeled examples is different from the best model for 10 billion examples. State your assumptions about data size before proposing a model.

**Serve the simplest model that meets requirements**: a logistic regression model that meets the latency and accuracy requirements is superior to a transformer model that barely fits the latency budget. Complexity has maintenance and debugging costs.

**Monitoring is not optional**: a model without monitoring is a time bomb. Plan monitoring from the design phase, not as an afterthought.

---

## 4. Types / Architectures / Strategies

### Step 1 — Requirements Taxonomy

| Category | Questions to Ask | Example Answers |
|----------|-----------------|-----------------|
| Business metrics | What KPI does this system optimize? | CTR, watch time, revenue, DAU retention |
| Scale | QPS, DAU, catalog size | 10K QPS, 100M users, 10M videos |
| Latency | P99, P50 budget | P99 < 100ms, P50 < 30ms |
| Accuracy | Minimum acceptable metric | AUC > 0.78, NDCG@10 > 0.65 |
| Data | Size, freshness, label quality | 1TB/day, labels delayed 1 hour |
| Infrastructure | Existing ML platform, budget | Kubernetes cluster, $50K/month GPU budget |

### Step 2 — Problem Formulation Taxonomy

| ML Task | Framing | Label | Metric |
|---------|---------|-------|--------|
| Ranking | Given user + context, rank N items | Clicks, watch time (continuous or binary) | NDCG@K, MAP |
| CTR prediction | Given (user, item, context), predict click probability | Binary click | AUC-ROC, log loss |
| Multi-task | Predict multiple objectives jointly | Multiple label types | Weighted average of per-task metrics |
| Retrieval | Given user, retrieve top-K relevant items from large corpus | Engagement | Recall@K |

### Step 3 — Feature Taxonomy

| Feature Type | Examples | Freshness Requirement | Storage |
|-------------|----------|----------------------|---------|
| User static | Demographics, account age | Daily | Redis / DynamoDB |
| User behavioral | 7d watch history, genre preferences | Hourly | Redis |
| User real-time | Views in last 5 minutes, current session | Seconds | Redis (Flink-computed) |
| Item static | Title, description, category, duration | At upload time | Redis |
| Item aggregate | Total views, average rating | Hourly | Redis |
| Interaction | Co-watch rates, collaborative signals | Daily | Offline only (training) |
| Context | Time of day, device type, location | Per-request | Computed at serving time |

### Step 4 — Model Selection Decision Tree

```
Catalog size > 1M items?
  YES -> Two-stage: retrieval (two-tower ANN) + ranking
  NO  -> Single-stage ranking model

Training data > 10M examples?
  YES -> Deep model (DNN, transformer)
  NO  -> Gradient-boosted tree (LightGBM, XGBoost)

Latency budget < 20ms?
  YES -> Logistic regression or shallow GBT
  NO  -> Deeper model acceptable

Multiple objectives?
  YES -> Multi-task learning (shared backbone, task-specific heads)
  NO  -> Single-objective model
```

---

## 5. Architecture Diagrams

### Complete Framework Applied to Video Recommendation

```
STEP 1: REQUIREMENTS
  ┌───────────────────────────────────────────────────┐
  │ Business: maximize weekly watch time               │
  │ Scale: 100M DAU, 10K QPS, 10M videos              │
  │ Latency: P99 < 100ms                              │
  │ Accuracy: NDCG@10 > 0.65 (vs baseline 0.52)       │
  │ Data: 5B clicks/day, labels available in 1 hour   │
  └───────────────────────────────────────────────────┘
                          │
STEP 2: PROBLEM FORMULATION
  ┌───────────────────────────────────────────────────┐
  │ Task: two-stage ranking                           │
  │ Label: watch_time > 30s = positive (binary)       │
  │ Objective: cross-entropy on positive/negative     │
  │ Metric: NDCG@10 (offline), watch time (online)   │
  │ Position bias: use IPW during training            │
  └───────────────────────────────────────────────────┘
                          │
STEP 3: DATA & FEATURES
  ┌───────────────────────────────────────────────────┐
  │ User features: embedding (512d), 7d genre history │
  │ Video features: content embedding (512d), stats   │
  │ Context: time of day, device type (computed live) │
  │ Real-time: session views (Flink, <30s latency)   │
  │ Feature store: Redis online, S3 offline           │
  └───────────────────────────────────────────────────┘
                          │
STEP 4: MODEL
  ┌───────────────────────────────────────────────────┐
  │ Retrieval: two-tower, 512d embeddings, FAISS IVF  │
  │   Output: top-500 candidates, recall@500 > 0.85  │
  │ Ranking: LightGBM, 200 features, 500 trees        │
  │   Output: watch_time probability score            │
  │ Training: daily, 30 days history, temporal split  │
  └───────────────────────────────────────────────────┘
                          │
STEP 5: SERVING
  ┌───────────────────────────────────────────────────┐
  │ Retrieval: FAISS server, <10ms P99                │
  │ Feature fetch: Redis batch GET, <5ms              │
  │ Ranking: LightGBM inference, <15ms                │
  │ Post-process: diversity + ads, <3ms               │
  │ Total: ~35ms P50, ~70ms P99 (budget: 100ms)      │
  └───────────────────────────────────────────────────┘
                          │
STEP 6: MONITORING
  ┌───────────────────────────────────────────────────┐
  │ Feature drift: PSI hourly on top-20 features     │
  │ Model output drift: score distribution daily      │
  │ Business metric: watch time per session, real-time│
  │ Retraining: daily scheduled + drift-triggered     │
  └───────────────────────────────────────────────────┘
```

### Latency Budget Decomposition

```
P99 LATENCY BUDGET: 100ms
├── Network (client to API gateway):       10ms
├── API gateway + auth:                     3ms
├── Feature fetch (Redis batch GET):        8ms
├── Retrieval (FAISS ANN search):          12ms
├── Ranking model inference:               18ms
├── Post-processing (diversity, policy):    4ms
├── Response serialization + network:       8ms
└── Total modeled:                         63ms
    Headroom:                              37ms
```

---

## 6. How It Works — Detailed Mechanics

### Step 1: Requirements Gathering — Code Pattern

```python
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class BusinessRequirements:
    """Step 1: Capture what the business cares about."""
    # Business outcome
    primary_business_metric: str        # "weekly_watch_time_hours"
    secondary_business_metrics: list[str] = field(default_factory=list)
    guardrail_metrics: list[str] = field(default_factory=list)

    # Scale
    daily_active_users: int = 0         # 100_000_000
    peak_qps: int = 0                   # 10_000
    catalog_size: int = 0               # 10_000_000

    # Latency
    p99_latency_ms: int = 200
    p50_latency_ms: int = 50

    # Data
    daily_events_volume: int = 0        # 5_000_000_000
    label_delay_hours: float = 1.0      # labels available 1h after event
    label_noise_estimate: float = 0.05  # 5% mislabeled

    # Infrastructure
    existing_platform: str = "kubernetes"
    monthly_gpu_budget_usd: int = 50_000


@dataclass
class ProblemFormulation:
    """Step 2: ML task definition."""
    task_type: str                      # "ranking", "classification", "retrieval"
    label_definition: str              # "watch_time > 30s = positive"
    objective_function: str            # "binary_cross_entropy"
    primary_offline_metric: str        # "ndcg@10"
    primary_online_metric: str         # "watch_time_per_session"
    known_biases: list[str] = field(default_factory=list)   # ["position_bias"]
    bias_corrections: list[str] = field(default_factory=list)  # ["ipw"]
```

### Step 2: Position Bias Correction (a concrete formulation detail)

```python
import numpy as np
from numpy.typing import NDArray


def compute_inverse_propensity_weights(
    positions: NDArray[np.int32],
    eta: float = 1.0,
) -> NDArray[np.float64]:
    """
    Compute position-based inverse propensity weights.

    Assumption: P(click | shown at position k) proportional to 1/k^eta
    Weight = 1 / propensity = k^eta

    Args:
        positions: array of positions (1-indexed) at which items were shown
        eta: position bias strength parameter (1.0 = strong, 0 = no bias)
    Returns:
        sample weights to pass to model training
    """
    propensity = 1.0 / (positions.astype(np.float64) ** eta)
    # Normalize so weights sum to n (to preserve gradient scale)
    weights = propensity / propensity.mean()
    return weights


# Usage during training data preparation
positions = np.array([1, 1, 2, 3, 5, 10, 1, 4])   # positions where items were shown
labels    = np.array([1, 0, 1, 0, 0,  0, 1, 1])    # click labels
weights   = compute_inverse_propensity_weights(positions, eta=0.8)

# Pass weights to model:
# lgb.train(..., weight=weights)
# XGBClassifier(...).fit(X, y, sample_weight=weights)
```

### Step 3: Point-in-Time Feature Join (the critical data correctness step)

```python
import pandas as pd
from datetime import timedelta


def point_in_time_join(
    label_events: pd.DataFrame,    # columns: user_id, item_id, event_time, label
    feature_snapshots: pd.DataFrame,  # columns: user_id, feature_time, feature_value
    feature_name: str,
    max_age_hours: int = 24,
) -> pd.DataFrame:
    """
    Join features to labels using the most recent feature snapshot
    that was available BEFORE the label event time.

    This prevents data leakage where future feature values are used to
    predict past labels.

    Args:
        label_events: training labels with timestamps
        feature_snapshots: historical feature values with computation timestamps
        feature_name: name of feature being joined
        max_age_hours: maximum age of feature snapshot to use; older snapshots
                       are treated as missing to avoid staleness

    Returns:
        label_events with feature column added
    """
    result_rows = []

    for _, event in label_events.iterrows():
        event_time = event["event_time"]
        user_id = event["user_id"]
        cutoff = event_time - timedelta(hours=max_age_hours)

        # Find the most recent feature snapshot for this user
        # that was computed BEFORE the event (point-in-time correct)
        user_features = feature_snapshots[
            (feature_snapshots["user_id"] == user_id)
            & (feature_snapshots["feature_time"] < event_time)
            & (feature_snapshots["feature_time"] >= cutoff)
        ]

        if user_features.empty:
            feature_value = None   # handle missing in model
        else:
            feature_value = user_features.sort_values("feature_time").iloc[-1]["feature_value"]

        row = event.to_dict()
        row[feature_name] = feature_value
        result_rows.append(row)

    return pd.DataFrame(result_rows)


# BROKEN pattern — future leakage:
# training_data = label_events.merge(features, on="user_id")
# This joins the CURRENT feature value, which may have been computed
# using information that post-dates the label event.

# CORRECT pattern:
# training_data = point_in_time_join(label_events, feature_snapshots, "7d_watch_count")
```

### Step 4: Model Selection and Training

```python
import lightgbm as lgb
from sklearn.model_selection import TimeSeriesSplit
import optuna


def train_ranking_model(
    X_train: pd.DataFrame,
    y_train: pd.Series,
    groups_train: pd.Series,     # number of items per query (for LTR)
    sample_weights: NDArray[np.float64],
) -> lgb.Booster:
    """
    Train a LambdaRank model for learning-to-rank.
    Uses temporal cross-validation to prevent leakage.
    """

    def objective(trial: optuna.Trial) -> float:
        params = {
            "objective": "lambdarank",
            "metric": "ndcg",
            "ndcg_eval_at": [10],
            "num_leaves": trial.suggest_int("num_leaves", 31, 255),
            "learning_rate": trial.suggest_float("learning_rate", 0.01, 0.3, log=True),
            "n_estimators": trial.suggest_int("n_estimators", 100, 1000),
            "min_child_samples": trial.suggest_int("min_child_samples", 5, 100),
            "subsample": trial.suggest_float("subsample", 0.6, 1.0),
            "colsample_bytree": trial.suggest_float("colsample_bytree", 0.6, 1.0),
            "verbose": -1,
        }

        # Temporal split: train on days 1-21, validate on days 22-28
        # Never use random split for time-series ML data
        split_idx = int(len(X_train) * 0.75)
        X_tr, X_val = X_train.iloc[:split_idx], X_train.iloc[split_idx:]
        y_tr, y_val = y_train.iloc[:split_idx], y_train.iloc[split_idx:]
        g_tr = groups_train.iloc[:split_idx]
        g_val = groups_train.iloc[split_idx:]
        w_tr = sample_weights[:split_idx]

        model = lgb.LGBMRanker(**params)
        model.fit(
            X_tr, y_tr,
            group=g_tr,
            sample_weight=w_tr,
            eval_set=[(X_val, y_val)],
            eval_group=[g_val],
            callbacks=[lgb.early_stopping(50, verbose=False)],
        )
        return model.best_score_["valid_0"]["ndcg@10"]

    study = optuna.create_study(direction="maximize")
    study.optimize(objective, n_trials=50, timeout=3600)

    # Final model with best params
    best_model = lgb.LGBMRanker(**study.best_params)
    best_model.fit(X_train, y_train, group=groups_train, sample_weight=sample_weights)
    return best_model.booster_
```

### Step 5: Serving Architecture

```python
import time
import redis
import faiss
import numpy as np
from typing import Any


class VideoRecommendationServer:
    """
    Serves video recommendations using two-stage retrieval + ranking.
    Latency budget: P99 < 100ms
    """

    def __init__(
        self,
        faiss_index: faiss.Index,
        ranking_model: lgb.Booster,
        redis_client: redis.Redis,
        retrieval_k: int = 500,
        final_k: int = 50,
    ) -> None:
        self.faiss_index = faiss_index
        self.ranking_model = ranking_model
        self.redis_client = redis_client
        self.retrieval_k = retrieval_k
        self.final_k = final_k

    def recommend(self, user_id: str, context: dict[str, Any]) -> list[str]:
        t_start = time.perf_counter()

        # Stage 0: Fetch user embedding from Redis (target: <5ms)
        user_embedding = self._fetch_user_embedding(user_id)
        t_after_embed = time.perf_counter()

        # Stage 1: ANN retrieval (target: <12ms)
        candidate_ids, candidate_embeddings = self._retrieve_candidates(user_embedding)
        t_after_retrieval = time.perf_counter()

        # Stage 2: Fetch item features from Redis (target: <8ms)
        item_features = self._fetch_item_features(candidate_ids)
        t_after_features = time.perf_counter()

        # Stage 3: Ranking model inference (target: <18ms)
        scores = self._rank_candidates(user_id, candidate_ids, item_features, context)
        t_after_ranking = time.perf_counter()

        # Stage 4: Post-processing (diversity, policy) (target: <3ms)
        final_ids = self._post_process(candidate_ids, scores)
        t_end = time.perf_counter()

        # Emit latency breakdown to metrics system
        self._emit_latency_metrics({
            "embed_ms": (t_after_embed - t_start) * 1000,
            "retrieval_ms": (t_after_retrieval - t_after_embed) * 1000,
            "feature_ms": (t_after_features - t_after_retrieval) * 1000,
            "ranking_ms": (t_after_ranking - t_after_features) * 1000,
            "postproc_ms": (t_end - t_after_ranking) * 1000,
            "total_ms": (t_end - t_start) * 1000,
        })

        return final_ids

    def _fetch_user_embedding(self, user_id: str) -> np.ndarray:
        raw = self.redis_client.get(f"user_emb:{user_id}")
        if raw is None:
            return np.zeros(512, dtype=np.float32)  # cold start fallback
        return np.frombuffer(raw, dtype=np.float32)

    def _retrieve_candidates(
        self, user_embedding: np.ndarray
    ) -> tuple[list[str], np.ndarray]:
        query = user_embedding.reshape(1, -1)
        _, indices = self.faiss_index.search(query, self.retrieval_k)
        candidate_ids = [str(i) for i in indices[0] if i >= 0]
        return candidate_ids, indices

    def _fetch_item_features(self, item_ids: list[str]) -> dict[str, dict]:
        # Batch fetch — single round trip to Redis
        pipeline = self.redis_client.pipeline()
        for item_id in item_ids:
            pipeline.hgetall(f"item_feat:{item_id}")
        results = pipeline.execute()
        return {item_id: result for item_id, result in zip(item_ids, results)}

    def _rank_candidates(
        self,
        user_id: str,
        candidate_ids: list[str],
        item_features: dict[str, dict],
        context: dict[str, Any],
    ) -> np.ndarray:
        feature_matrix = self._build_feature_matrix(user_id, candidate_ids, item_features, context)
        return self.ranking_model.predict(feature_matrix)

    def _build_feature_matrix(self, user_id, candidate_ids, item_features, context) -> np.ndarray:
        # Build feature matrix for LightGBM inference
        # (actual feature engineering omitted for brevity)
        return np.random.rand(len(candidate_ids), 200).astype(np.float32)

    def _post_process(self, candidate_ids: list[str], scores: np.ndarray) -> list[str]:
        ranked_indices = np.argsort(scores)[::-1]
        return [candidate_ids[i] for i in ranked_indices[:self.final_k]]

    def _emit_latency_metrics(self, metrics: dict[str, float]) -> None:
        pass  # emit to Prometheus / DataDog
```

### Step 6: Monitoring

```python
import numpy as np
from scipy.stats import ks_2samp


def compute_population_stability_index(
    baseline: np.ndarray,
    current: np.ndarray,
    n_bins: int = 10,
    epsilon: float = 1e-8,
) -> float:
    """
    Compute Population Stability Index (PSI) to detect feature drift.

    PSI < 0.1: no significant change
    PSI 0.1-0.2: moderate change, investigate
    PSI > 0.2: significant shift, trigger retraining

    Args:
        baseline: feature values from training data distribution
        current: feature values from recent serving data
        n_bins: number of bins for histogram
    Returns:
        PSI value
    """
    bins = np.percentile(baseline, np.linspace(0, 100, n_bins + 1))
    bins[0] = -np.inf
    bins[-1] = np.inf

    baseline_freq = np.histogram(baseline, bins=bins)[0] / len(baseline)
    current_freq = np.histogram(current, bins=bins)[0] / len(current)

    # Clip to avoid log(0)
    baseline_freq = np.clip(baseline_freq, epsilon, None)
    current_freq = np.clip(current_freq, epsilon, None)

    psi = np.sum((current_freq - baseline_freq) * np.log(current_freq / baseline_freq))
    return float(psi)


def should_retrain(
    feature_psi_scores: dict[str, float],
    business_metric_change: float,   # fractional change from baseline, e.g. -0.03
    psi_threshold: float = 0.2,
    metric_drop_threshold: float = -0.05,
) -> tuple[bool, str]:
    """
    Determine whether to trigger model retraining.
    Returns (should_retrain, reason).
    """
    # Check feature drift
    drifted_features = [f for f, psi in feature_psi_scores.items() if psi > psi_threshold]
    if drifted_features:
        return True, f"Feature drift detected: {drifted_features}"

    # Check business metric degradation
    if business_metric_change < metric_drop_threshold:
        pct = business_metric_change * 100
        return True, f"Business metric dropped {pct:.1f}% below baseline"

    return False, "No retraining needed"
```

---

## 7. Real-World Examples

**Netflix content ranking**: uses a three-stage pipeline — (1) candidate generation uses matrix factorization to produce 1,000 candidates per user; (2) ranking uses a neural network with user taste profile, item metadata, and contextual features; (3) row/shelf ordering uses a separate model that optimizes for "percentage of shows that are clicked from the first row." Each stage has its own training pipeline, serving infrastructure, and monitoring.

**Twitter (X) timeline ranking**: the For You feed uses a two-tower retrieval model to retrieve 1,500 candidates from 500 million tweets, then a 48-million-parameter neural ranking model that predicts 10 engagement probabilities (like, reply, retweet, click profile, etc.) simultaneously. A utility function combines these into a final ranking score. The system is described in their 2023 open-source release.

**Amazon product recommendations**: "Customers who bought X also bought Y" uses item-to-item collaborative filtering (offline precomputed) for the cold path. The hot path uses a real-time DNN that incorporates the user's current session clicks, which changes the recommendations within seconds of each interaction.

---

## 8. Tradeoffs

### Single-Stage vs Two-Stage

| Dimension | Single-Stage | Two-Stage (Retrieval + Ranking) |
|-----------|-------------|--------------------------------|
| Complexity | Low | High (two models, two serving stacks) |
| Catalog size limit | ~10,000 items (must score all) | Millions (ANN search for retrieval) |
| Quality | Potentially higher (no recall cap) | Bounded by retrieval recall |
| Latency | Predictable | Additive (retrieval + ranking) |
| Iteration speed | Faster | Slower (two systems to update) |

### Multi-Task vs Single-Task Models

| Dimension | Multi-Task | Single-Task |
|-----------|-----------|-------------|
| Training data efficiency | High (shared gradients) | Lower |
| Negative transfer risk | Yes (conflicting objectives) | No |
| Serving complexity | One model | One model per task |
| Metric optimization | Requires utility function | Directly optimizes target |
| Typical use | Recommendation, ads | Fraud detection, simple CTR |

---

## 9. When to Use / When NOT to Use

### Use the full 6-step framework when:
- The interview problem involves a large-scale user-facing ML system (search, recommendation, ads, fraud)
- The problem has multiple stakeholders with conflicting requirements
- The problem involves both retrieval at scale and personalization
- You need to justify architectural decisions with concrete constraints

### Skip or compress steps when:
- The problem is clearly scoped (e.g., "design a binary classifier for churn prediction" — no retrieval stage needed)
- The catalog is small (<10,000 items) — two-stage is over-engineering
- Offline inference is acceptable — serving complexity drops dramatically
- The interview emphasizes model design over system design

### Common interview time allocation (45 minutes):
- Step 1 (Requirements): 5 minutes
- Step 2 (Formulation): 5 minutes
- Step 3 (Features): 8 minutes
- Step 4 (Model): 10 minutes
- Step 5 (Serving): 10 minutes
- Step 6 (Monitoring): 5 minutes
- Buffer / deep-dive on interviewer interest: 2 minutes

---

## 10. Common Pitfalls

**Jumping to model architecture**: the most common interview mistake. Candidates immediately say "I'd use a transformer" without asking about data size, latency budget, or label availability. A transformer with 100M parameters cannot run in 5ms. Always ask requirements first.

**Ignoring position bias**: candidates design a ranking system trained on click logs and never mention that high-position items receive more clicks regardless of quality. A model trained naively on these labels learns to rank items high because they were shown high, creating a feedback loop. Mention IPW or position-as-feature during Step 2.

**Vague feature lists**: "I'd use user features and item features" without specifying which features, how they are computed, and what freshness they require. Interviewers probe feature specificity. Be concrete: "user's 7-day genre preference distribution, computed by a daily Spark job over the click log."

**Missing the cold start problem**: designing a collaborative filtering or embedding-based system without addressing what happens for new users (0 interactions) or new items (0 engagements). Cold start handling demonstrates production thinking.

**No monitoring plan**: designing a complete training and serving system and then stopping. Production ML systems degrade silently. A plan to detect and respond to degradation is expected in senior-level interviews.

**Training-serving skew in feature discussion**: proposing feature engineering in training (Spark join) without noting that serving must compute the same features in real-time (fast path). If you don't address this, experienced interviewers will ask "how do you ensure the features in training match what you compute at serving time?" — and if you haven't thought about it, the answer will show.

---

## 11. Technologies & Tools

| Step | Tools | Notes |
|------|-------|-------|
| Requirements | Whiteboard, interview notes | No tools needed |
| Problem formulation | LightGBM, PyTorch, sklearn | GBT for tabular; transformer for sequence |
| Feature engineering | Apache Spark, Flink, dbt | Spark for batch, Flink for streaming |
| Feature store | Feast, Tecton, Redis, S3 | Redis for online, S3/Hive for offline |
| Model training | PyTorch, LightGBM, Optuna, MLflow | MLflow for experiment tracking |
| ANN retrieval | FAISS, ScaNN, Pinecone | FAISS self-hosted, Pinecone managed |
| Model serving | Triton, BentoML, Ray Serve, TF Serving | Triton best for multi-framework GPU |
| Monitoring | Evidently AI, Arize, WhyLogs | Evidently open-source, PSI for drift |
| Orchestration | Kubeflow Pipelines, Airflow | Airflow most widely deployed |

---

## 12. Interview Questions with Answers

**Q: How would you start an ML system design interview?**
Start by asking requirements questions before drawing any diagram. Ask: (1) What business metric does this optimize? (2) What is the scale — QPS, users, catalog size? (3) What is the latency budget — P99? (4) What training data is available and how are labels generated? (5) What is the acceptable minimum offline metric? This signals production awareness and prevents designing a system that cannot meet the actual constraints.

**Q: How do you formulate a recommendation problem as an ML task?**
First decide the task type: if the catalog is large (>10K items), frame it as two-stage — retrieval (find candidates) + ranking (score candidates). Define the label: clicks are noisy but abundant; dwell time is higher quality but delayed. Define the objective: binary cross-entropy for CTR; lambdarank loss for learning-to-rank with NDCG optimization. Identify biases: position bias (items shown at the top get more clicks), popularity bias (popular items dominate). Plan corrections: inverse propensity weighting for position bias, popularity debiasing for collaborative signals.

**Q: What is the difference between offline and online evaluation of ML models?**
Offline evaluation measures model quality on a held-out test set without any user impact. Metrics include AUC-ROC, NDCG@K, precision@K, RMSE. Online evaluation runs a controlled experiment (A/B test) with real users, measuring business metrics (CTR, revenue, engagement). Offline metrics may not correlate with online metrics due to distribution shift between training data and current user behavior, position bias in click logs, or proxy metric misalignment. Always validate offline → online metric correlation before relying on offline metrics as proxies.

**Q: How do you handle a catalog of 100 million items for real-time recommendation?**
A two-stage architecture is required. Stage 1 (retrieval): use a two-tower model to learn user and item embeddings, then build a FAISS or ScaNN index over all item embeddings. At serving time, embed the user and perform ANN search to retrieve top-1000 candidates in <15ms. Stage 2 (ranking): use a rich feature model (LightGBM or DNN) to score the 1,000 candidates with user features, item features, and contextual features in <25ms. This architecture reduces the expensive ranking computation from 100M items to 1,000.

**Q: How do you choose between LightGBM and a deep neural network for ranking?**
LightGBM is preferred when: training data is <100M examples; features are mostly tabular (counts, ratios, categorical); serving must be extremely fast (<5ms on CPU); interpretability is needed (feature importance). Deep neural networks are preferred when: training data exceeds 100M examples; raw features are sparse or unstructured (embeddings, text); interactions between features are complex and hard to engineer manually; GPU serving infrastructure is available. In practice, LightGBM often matches DNN quality on tabular data while being 10-100x cheaper to serve.

**Q: How do you detect and handle training-serving skew?**
Prevention: implement feature computation using a shared library (Python package) imported by both the training pipeline and the serving pipeline. The same code path computes the same feature value. Detection: log serving-time features for a random sample (1%) of requests; run statistical tests comparing the serving feature distribution to the training feature distribution daily; alert if KS test p-value < 0.01 or if PSI > 0.1 for any key feature. Response: investigate the root cause (schema change, different preprocessing, missing data handling difference), fix the discrepancy, and retrain.

**Q: What is multi-task learning and when do you use it for recommendation?**
Multi-task learning (MTL) trains a single model to predict multiple objectives simultaneously, sharing a feature backbone. Use it when: (1) you need to optimize multiple business metrics (CTR, CVR, dwell time) without training separate models; (2) some tasks have limited training data and can borrow signal from related tasks (auxiliary tasks). The risk is negative transfer — if task gradients conflict, the shared representation may not optimize any task well. Mitigation: use task-specific learning rate scaling, gradient surgery, or uncertainty-based task weighting. MTL is used in YouTube, Twitter, and TikTok for recommendation.

**Q: How do you handle label delay in a recommendation system?**
Label delay means the outcome (e.g., purchase 24 hours after recommendation) is not available at the time of training. Strategies: (1) use a proxy label with lower delay (click available in minutes vs purchase in hours); (2) train on delayed labels with a temporal cutoff — exclude examples whose outcome window has not yet closed; (3) use a multi-task model where the fast proxy label (click) is task 1 and the slow business label (purchase) is task 2, sharing the backbone. The most dangerous error is including future labels in training — always enforce a temporal gap between training cutoff and label observation window.

**Q: How do you prioritize features for a ranking model?**
Start with the highest-signal features known from domain knowledge: for recommendation, user interest embeddings and item embeddings are typically the strongest. Use SHAP values or LightGBM feature importance on an initial model to identify the top 20 features by contribution. Measure the marginal gain of adding features: retrain with feature subsets and compare NDCG. Remove features that do not improve NDCG by > 0.1% — they add serving latency and fragility without benefit. Special attention to features that are expensive to compute at serving time: if a feature requires a database join and adds 5ms of latency for 0.05% NDCG gain, drop it.

**Q: What is shadow mode deployment and why is it important?**
Shadow mode (also called shadow deployment or dark launch) runs a new model on production traffic, receiving the same requests as the production model, but without serving its predictions to users. The shadow model's predictions are logged and compared to the production model's predictions. Benefits: (1) validates that the new model produces sensible scores for real traffic (not just test set); (2) measures serving latency under real traffic load; (3) catches training-serving skew before users are affected; (4) enables offline comparison of model outputs without running an A/B test. Run shadow mode for 2-4 hours before promoting to A/B test.

**Q: How do you design the monitoring strategy for a recommendation model?**
Three layers of monitoring: (1) Data quality — schema validation on every feature pipeline run; distribution drift (PSI) measured daily on top-20 features; alert threshold PSI > 0.2. (2) Model health — prediction score distribution monitored daily; alert if mean score drifts > 2 standard deviations from baseline; if online labels are available quickly, monitor AUC daily. (3) Business metrics — track primary metric (CTR, engagement) in real-time dashboards with automated rollback if the metric drops > 5% below baseline for > 30 minutes. Monitoring SLA: an alert must fire within 1 hour of a detectable degradation.

**Q: How do you structure the model validation gate before production deployment?**
A model validation gate is a set of automated checks that must pass before a model is promoted. Required checks: (1) offline metric must exceed threshold (e.g., NDCG@10 > 0.65); (2) improvement over production model must be statistically significant (paired t-test p < 0.05 on test set); (3) P99 inference latency must be within SLA (e.g., < 20ms for ranking model); (4) fairness check — performance gap across demographic groups must be < 10% relative; (5) shadow mode comparison — model output correlation with production model > 0.7 (sanity check). If any gate fails, the pipeline halts and sends an alert. No human approval required for routine retraining; human review required for architecture changes.

**Q: How do you handle cold start for new users in a recommendation system?**
Cold start for new users (0 historical interactions) is handled in stages. Day 0 (account creation): use demographic features (age, location, device) and context (time, onboarding survey responses) with a content-based model that does not require interaction history. Day 1-3 (sparse history): use a hybrid model that blends content-based signals (high weight) with collaborative filtering (low weight). Week 1+ (sufficient history): transition fully to collaborative / embedding-based model. The transition should be smooth (weight interpolation based on interaction count). A common threshold: collaborative filtering starts dominating after 10 interactions. Track cold-start users separately in monitoring — they often have different engagement patterns.

**Q: What questions should you ask to clarify the data availability for an ML problem?**
Key questions: (1) How many labeled training examples exist? (10K vs 100M changes model choice dramatically.) (2) How are labels generated — explicit feedback (ratings) or implicit feedback (clicks, dwell time)? (3) What is the label noise rate — are clicks reliable proxies for quality? (4) What is the label delay — how long after a recommendation is made do you know the outcome? (5) Are features available at training time and at serving time? Which features are unavailable at serving time? (6) What is the data retention policy — how far back does historical data go? (7) Is there a significant class imbalance — for fraud detection, 0.1% fraud rate requires careful sampling strategy.

**Q: How do you avoid data leakage in ML training pipelines?**
Data leakage occurs when information from the future (relative to the prediction time) contaminates training examples. Common sources: (1) temporal leakage — using features computed after the label event (fix: point-in-time correct feature joins); (2) feature leakage — including features that are derived from or highly correlated with the label (fix: audit features for logical impossibility at prediction time); (3) test set contamination — preprocessing statistics (mean, std, vocabulary) computed on the full dataset including the test set (fix: fit preprocessors only on training fold); (4) group leakage — users or items appear in both train and test sets, inflating apparent generalization (fix: group-based train-test split). Temporal splitting is the most important safeguard: always train on earlier data and test on later data.

---

## 13. Best Practices

Always state the latency budget before choosing a model architecture. A model that cannot meet the latency SLA is not a valid option, regardless of its offline AUC.

Use temporal splitting for all time-series ML problems. Random splitting inflates offline metrics by allowing the model to see future data during validation.

Establish a simple baseline before proposing complex architectures. For ranking, a popularity-based ranker (most watched in the user's country in the last 7 days) is a valid baseline. Measure NDCG@10 of the baseline before claiming a complex model is needed.

Separate concerns: feature computation, model training, and model serving should be independent, versioned, and testable systems. Tight coupling between these phases makes debugging production issues nearly impossible.

Document the label definition precisely. "Click" can mean first frame loaded, intentional click with > 1 second dwell, or any tap. The label definition determines what behavior the model learns to predict. An imprecise label definition is a production incident waiting to happen.

Plan for graceful degradation at every layer: if the feature store is slow (circuit break to cached features), if the retrieval model is unavailable (fall back to popularity-based retrieval), if the ranking model times out (fall back to retrieval model scores directly).

---

## 14. Case Study

### Design a Content Recommendation System (Full 6-Step Walkthrough)

**Problem**: Design the recommendation system for a video streaming platform with 100M users and 10M videos. Optimize for watch time. QPS: 10,000. P99 latency: 100ms.

**Step 1 — Requirements**:
- Business metric: total weekly watch time (primary), subscriber retention (guardrail)
- Scale: 100M DAU, 10K QPS, 10M videos, 2B interaction events/day
- Latency: P99 < 100ms total, P50 < 35ms
- Accuracy: NDCG@10 > 0.65 (current heuristic baseline: 0.48)
- Data: click logs (available immediately), watch time (available after video ends, median 4 minutes delay)
- Infrastructure: Kubernetes on GCP, $40K/month GPU budget

**Step 2 — Problem Formulation**:
- Task: two-stage (retrieval + ranking) — 10M videos requires ANN retrieval
- Label: watch_time_ratio = watched_seconds / video_duration; threshold 0.3 = positive
- Retrieval objective: recall@500 > 0.90 (top-500 must contain most relevant items)
- Ranking objective: lambdarank, optimize NDCG@10
- Known biases: position bias (correct with IPW), popularity bias (correct with popularity-debiased embeddings)

**Step 3 — Data and Features**:
- User embeddings (512d): trained by two-tower, updated daily, stored in Redis
- User behavioral: 7d genre history, 30d creator subscriptions — daily Spark job, Redis
- User real-time: videos watched in current session — Flink, <30 second latency, Redis
- Item embeddings (512d): trained at video upload time, stored in Redis + FAISS index
- Item aggregates: 7d average completion rate, creator subscriber count — daily, Redis
- Context: time of day, device type — computed at request time (no storage needed)

**Step 4 — Model**:
- Retrieval: two-tower (user encoder 512d, video encoder 512d), trained with in-batch negatives + hard negatives; FAISS IVF100 index for ANN search; retrain weekly
- Ranking: LightGBM LambdaRank, 300 features, 500 trees, depth 7; retrain daily
- Multi-task option (future): add "add to watchlist" as auxiliary task to improve embedding quality for long-tail videos

**Step 5 — Serving**:
- Retrieval server: 4 GPUs for embedding computation; FAISS index on 8 CPU servers; P99 < 12ms
- Feature server: Redis cluster, batch GET for 500 item features; P99 < 8ms
- Ranking server: LightGBM on CPU, 32 cores; P99 < 18ms
- Load balancer: round-robin with health checks
- Total: ~40ms P50, ~80ms P99 — within 100ms budget with 20ms headroom

**Step 6 — Monitoring**:
- Feature drift: hourly PSI on user embedding distribution, 7d genre distribution; alert PSI > 0.2
- Model output: daily score distribution comparison; alert if shift > 2 std
- Business: real-time watch time per session on 5-minute rolling window; rollback trigger: -3% for 30 minutes
- Retraining: ranking model daily (scheduled); retrieval model weekly (scheduled) + triggered on PSI > 0.25