# ML System Design

## Deep Dive Files

| File | Topic | Q&As |
|------|-------|------|
| [design_framework.md](design_framework.md) | 6-Step ML Design Interview Framework | 15+ |
| [feature_store_design.md](feature_store_design.md) | Feature Store Design | 15+ |
| [training_pipeline_design.md](training_pipeline_design.md) | ML Training Pipeline Design | 15+ |
| [ab_testing_for_ml.md](ab_testing_for_ml.md) | A/B Testing for ML Models | 15+ |
| [latency_and_throughput_optimization.md](latency_and_throughput_optimization.md) | ML Inference Latency and Throughput Optimization | 15+ |

---

## 1. Concept Overview

ML system design is the discipline of architecting end-to-end machine learning systems that are reliable, scalable, and maintainable in production. Unlike academic ML, production ML systems must handle data pipelines, feature engineering, model training orchestration, low-latency serving, and continuous monitoring simultaneously.

A complete ML system is not just a trained model — it is a sociotechnical system composed of data infrastructure, training pipelines, serving infrastructure, and monitoring loops. The model itself is typically the smallest component by code volume; the surrounding infrastructure dwarfs it.

The 6-step ML design interview framework provides a structured approach to scoping, designing, and communicating ML systems:

1. Clarify requirements — scale, latency, accuracy targets, data availability
2. Problem formulation — ML task type, objective function, evaluation metrics
3. Data collection and feature engineering — sources, feature computation, storage
4. Model selection and training — architecture choice, training strategy, validation
5. Serving and inference architecture — online/offline inference, latency budget
6. Monitoring and iteration — data drift, model degradation, retraining triggers

---

## 2. Intuition

One-line analogy: an ML system is like a factory assembly line — raw materials (data) enter one end, pass through specialized stations (feature engineering, training, validation), and finished products (predictions) exit the other end, with quality control (monitoring) at every stage.

Mental model: think of the ML system as three coupled loops:
- The data loop: raw events -> feature computation -> feature store
- The training loop: features -> model training -> model registry -> serving
- The feedback loop: serving -> outcome labels -> retraining triggers

Why it matters: 85% of ML projects fail to reach production not because of model quality but because of infrastructure gaps — training-serving skew, missing monitoring, inability to retrain quickly when data distribution shifts.

Key insight: the most important design decision is often not the model architecture but the feedback loop latency — how quickly can the system incorporate new signal to update predictions?

---

## 3. Core Principles

**Reproducibility**: every training run must be reproducible given the same data snapshot and code version. This requires data versioning, code versioning, and deterministic random seeds.

**Isolation of training and serving**: training code and serving code should be validated to produce identical feature values for identical inputs. Training-serving skew is the most common production failure mode.

**Fail-safe defaults**: when a model or feature store is unavailable, the system should fall back to a simpler strategy (popularity-based ranking, rule-based filtering) rather than failing hard.

**Decoupled iteration velocity**: data engineers, ML engineers, and serving engineers should be able to iterate independently. Feature stores and model registries are the contracts that decouple these teams.

**Observability by default**: every prediction should be logged with its input features, model version, and serving latency. Without this, debugging production issues is impossible.

**Proportional accuracy investment**: a 2% improvement in AUC rarely justifies doubling serving cost. Design for the accuracy-cost-latency tradeoff explicitly.

---

## 4. Types / Architectures / Strategies

### ML Task Types

| Task Type | Examples | Primary Metric | Typical Model |
|-----------|----------|---------------|---------------|
| Binary classification | CTR prediction, fraud detection | AUC-ROC, F1 | LR, GBT, DNN |
| Multi-class classification | Content categorization, intent detection | Accuracy, macro-F1 | Transformer, GBT |
| Ranking / LTR | Search ranking, recommendation | NDCG, MAP | LambdaRank, LightGBM |
| Regression | Bid price prediction, ETA estimation | RMSE, MAPE | GBT, DNN |
| Retrieval (ANN) | Item recall, semantic search | Recall@K | Two-tower, DPR |
| Sequence modeling | NLP, user journey prediction | Perplexity, BLEU | Transformer, LSTM |
| Anomaly detection | Fraud, infrastructure anomalies | Precision@K, AUC | Isolation Forest, Autoencoder |

### System Patterns

**Two-Stage Retrieval + Ranking** (most recommendation / search systems):
- Stage 1 (Retrieval): cheap model retrieves top-K candidates (K=100-1000) from millions of items using ANN search. Optimize for recall.
- Stage 2 (Ranking): expensive model scores and re-orders the K candidates. Optimize for precision.

**Multi-Task Learning**:
- Single model predicts multiple objectives (CTR, CVR, dwell time) sharing a backbone.
- Prevents optimizing one metric at the expense of others.

**Online Learning**:
- Model weights updated continuously from streaming events (e.g., Vowpal Wabbit, FTRL).
- Useful when distribution shifts rapidly (financial markets, breaking news).

**Lambda Architecture**:
- Batch layer computes complete features from historical data.
- Speed layer computes real-time features from recent events.
- Serving layer merges both for predictions.

**Model Cascade**:
- Cheap model (LR) handles 80% of requests with high confidence.
- Expensive model (DNN) handles remaining 20% where cheap model is uncertain.
- Reduces cost by 4-5x while maintaining quality.

---

## 5. Architecture Diagrams

### Complete ML System Architecture

```
                          DATA SOURCES
           ┌──────────────────────────────────────┐
           │  Event Stream  │  DB Snapshots  │ Logs│
           └──────┬─────────────────┬─────────────┘
                  │                 │
         ┌────────▼────────┐ ┌──────▼──────────┐
         │  Stream Processor│ │  Batch Processor │
         │  (Flink/Kafka)  │ │  (Spark/Hive)   │
         └────────┬────────┘ └──────┬──────────┘
                  │                 │
           ┌──────▼─────────────────▼──────┐
           │         FEATURE STORE          │
           │  Online (Redis, <10ms read)    │
           │  Offline (S3/Hive, batch)      │
           └──────┬─────────────────┬───────┘
                  │                 │
         ┌────────▼────────┐ ┌──────▼──────────┐
         │ TRAINING PIPELINE│ │  SERVING LAYER  │
         │  - Data validation│ │  - Feature fetch │
         │  - Feature join  │ │  - Model inference│
         │  - Model train   │ │  - Post-process  │
         │  - Validation gate│ │  - Cache layer  │
         └────────┬────────┘ └──────┬──────────┘
                  │                 │
         ┌────────▼────────┐ ┌──────▼──────────┐
         │  MODEL REGISTRY │ │   MONITORING     │
         │  (MLflow)       │ │  - Data drift    │
         │  - Versioning   │ │  - Perf metrics  │
         │  - Lineage      │ │  - Alerting      │
         │  - Artifacts    │ │  - Retraining    │
         └─────────────────┘ └─────────────────┘
```

### Two-Stage Retrieval + Ranking

```
  USER REQUEST (item_id, user_id, context)
         │
         ▼
  ┌──────────────┐
  │  RETRIEVAL   │  Two-tower model + FAISS ANN
  │  Stage 1     │  Latency: <10ms
  │              │  Output: Top-1000 candidates
  └──────┬───────┘
         │ 1000 candidates
         ▼
  ┌──────────────┐
  │   RANKING    │  LightGBM / DNN with rich features
  │   Stage 2    │  Latency: <20ms
  │              │  Output: Scored + ranked top-50
  └──────┬───────┘
         │ top-50 items
         ▼
  ┌──────────────┐
  │  BUSINESS    │  Diversity, price floors, policy rules
  │   LOGIC      │  Latency: <5ms
  └──────┬───────┘
         │ final results
         ▼
      RESPONSE
```

### Model Cascade Pattern

```
  REQUEST
    │
    ▼
┌───────────────────────┐
│  CHEAP MODEL (LR/GBT) │  Cost: $0.001/req, 2ms latency
│  Confidence threshold │
└──────┬────────────────┘
       │
  ┌────▼────┐
  │High conf│──YES──> Serve result (80% of requests)
  │  >0.95? │
  └────┬────┘
       │ NO (20% of requests)
       ▼
┌───────────────────────┐
│  EXPENSIVE MODEL (DNN)│  Cost: $0.01/req, 50ms latency
│  Full feature set     │
└──────┬────────────────┘
       │
       ▼
  Serve result
```

---

## 6. How It Works — Detailed Mechanics

### The 6-Step Framework in Code

```python
from dataclasses import dataclass
from enum import Enum
from typing import Optional


class MLTaskType(Enum):
    BINARY_CLASSIFICATION = "binary_classification"
    RANKING = "ranking"
    REGRESSION = "regression"
    RETRIEVAL = "retrieval"
    MULTI_LABEL_CLASSIFICATION = "multi_label_classification"


@dataclass
class MLSystemRequirements:
    # Scale
    qps: int                          # queries per second, e.g. 100_000
    daily_active_users: int           # e.g. 50_000_000
    catalog_size: int                 # e.g. 1_000_000 items

    # Latency
    p99_latency_ms: int               # e.g. 100
    p50_latency_ms: int               # e.g. 30

    # Accuracy
    primary_metric: str               # e.g. "AUC-ROC", "NDCG@10", "CTR"
    minimum_acceptable_metric: float  # e.g. 0.75 AUC

    # Data
    training_data_size_gb: int        # e.g. 500
    label_availability: str           # "real-time", "delayed-24h", "batch-weekly"
    label_noise_level: str            # "low", "medium", "high"

    # Business
    online_offline_inference: str     # "online", "offline", "hybrid"
    retraining_frequency: str         # "daily", "weekly", "continuous"


@dataclass
class MLSystemDesign:
    requirements: MLSystemRequirements
    task_type: MLTaskType
    retrieval_model: Optional[str]    # e.g. "two-tower"
    ranking_model: Optional[str]      # e.g. "lightgbm"
    feature_store_type: str           # "online+offline", "offline-only"
    serving_pattern: str              # "two-stage", "single-model", "cascade"
    monitoring_strategy: str          # "drift-based", "scheduled", "performance-triggered"


def design_ml_system(problem: str, requirements: MLSystemRequirements) -> MLSystemDesign:
    """
    Walk through the 6-step framework to produce a system design.
    """
    # Step 1: requirements already captured in MLSystemRequirements

    # Step 2: Problem formulation
    task_type = _formulate_task(requirements)

    # Step 3: Feature strategy
    feature_store = _choose_feature_store(requirements)

    # Step 4: Model selection
    retrieval_model, ranking_model = _select_models(task_type, requirements)

    # Step 5: Serving pattern
    serving_pattern = _choose_serving_pattern(requirements)

    # Step 6: Monitoring strategy
    monitoring = _choose_monitoring(requirements)

    return MLSystemDesign(
        requirements=requirements,
        task_type=task_type,
        retrieval_model=retrieval_model,
        ranking_model=ranking_model,
        feature_store_type=feature_store,
        serving_pattern=serving_pattern,
        monitoring_strategy=monitoring,
    )


def _formulate_task(req: MLSystemRequirements) -> MLTaskType:
    if req.catalog_size > 10_000 and req.primary_metric.startswith("NDCG"):
        return MLTaskType.RANKING
    if req.catalog_size > 100_000:
        return MLTaskType.RETRIEVAL
    if req.primary_metric in ("AUC-ROC", "F1", "precision", "recall"):
        return MLTaskType.BINARY_CLASSIFICATION
    return MLTaskType.REGRESSION


def _choose_feature_store(req: MLSystemRequirements) -> str:
    if req.online_offline_inference == "online" and req.p99_latency_ms <= 100:
        return "online+offline"   # Redis online + S3 offline
    return "offline-only"


def _select_models(task: MLTaskType, req: MLSystemRequirements) -> tuple[Optional[str], Optional[str]]:
    if task == MLTaskType.RANKING and req.catalog_size > 100_000:
        return "two-tower-ann", "lightgbm-ltr"
    if task == MLTaskType.BINARY_CLASSIFICATION:
        return None, "lightgbm"
    return None, "logistic-regression"


def _choose_serving_pattern(req: MLSystemRequirements) -> str:
    if req.qps > 10_000 and req.catalog_size > 100_000:
        return "two-stage"
    if req.p99_latency_ms < 50:
        return "cascade"
    return "single-model"


def _choose_monitoring(req: MLSystemRequirements) -> str:
    if req.label_availability == "real-time":
        return "performance-triggered"
    if req.retraining_frequency == "continuous":
        return "drift-based"
    return "scheduled"
```

---

## 7. Real-World Examples

**YouTube Recommendations** uses a two-stage architecture: a deep candidate generation network (retrieval) followed by a deep ranking network. The retrieval model uses user embeddings and video embeddings in a two-tower setup, producing 100-500 candidates from a corpus of millions. The ranking model uses hundreds of features and is optimized for watch time rather than clicks.

**Uber ETA prediction** uses a hybrid online/offline system. Offline features (road network, historical trip data) are precomputed and stored in a feature store. Online features (real-time traffic, weather) are fetched at request time. The model serves predictions in <50ms P99.

**LinkedIn feed ranking** uses a multi-task learning approach that jointly optimizes for likes, comments, shares, and long-form engagement. A single model backbone predicts all objectives, and a utility function combines them into a final ranking score.

**Airbnb search ranking** uses a two-stage pipeline where retrieval uses approximate nearest neighbor search on listing embeddings, and ranking uses a neural network with listing features, user features, and query features. They famously wrote about position bias — listings shown higher get more clicks regardless of quality — and use inverse propensity weighting to correct for it.

**Stripe fraud detection** uses a model cascade: a fast gradient-boosted tree runs on every transaction in <5ms and blocks high-confidence fraud immediately. Uncertain cases are escalated to a slower, more expensive deep learning model that uses graph features (shared cards, devices, merchants). Less than 20% of transactions reach the expensive model.

---

## 8. Tradeoffs

### Accuracy vs Latency

| Approach | Accuracy | P99 Latency | Cost |
|----------|----------|-------------|------|
| Simple LR | Low | 2ms | $0.001/req |
| GBT (100 trees) | Medium | 10ms | $0.003/req |
| DNN (100M params) | High | 50ms | $0.01/req |
| Ensemble | Highest | 100ms | $0.03/req |

### Online vs Offline Inference

| Dimension | Online Inference | Offline Inference |
|-----------|-----------------|-------------------|
| Freshness | Real-time | Hours to days stale |
| Latency | Must be fast (<100ms P99) | No latency constraint |
| Personalization | Full (current context) | Snapshot of user state |
| Cost | High (GPU/CPU on hot path) | Low (batch, spot instances) |
| Use cases | Fraud detection, ads | Email recommendations, reporting |

### Feature Store: Online vs Offline

| Dimension | Online Store (Redis) | Offline Store (S3/Hive) |
|-----------|---------------------|------------------------|
| Read latency | <10ms P99 | Minutes |
| Write throughput | High (real-time) | High (batch) |
| Storage cost | High ($) | Low ($) |
| Data freshness | Seconds | Hours to days |
| Use case | Serving | Training |

---

## 9. When to Use / When NOT to Use

### When to use a full ML system (retrieval + ranking + feature store + monitoring):
- Catalog has more than 10,000 items and personalization is required
- QPS exceeds 1,000 and P99 latency budget is under 200ms
- Retraining is needed more frequently than manually feasible (weekly or more often)
- Business outcome is directly tied to model quality (ads revenue, marketplace GMV)

### When NOT to build a complex ML system:
- Problem can be solved with heuristics or rule-based logic (business rules, popularity ranking)
- Training data is less than 10,000 labeled examples — simple models will generalize better
- Team lacks ML infrastructure expertise — a simple scikit-learn model deployed via Flask beats a complex system that never ships
- Latency requirement is under 5ms P99 — rule-based systems are faster and more predictable

### Signs a simpler approach is correct:
- Label quality is poor (noise rate >30%) — model will memorize noise
- Distribution shift is so frequent that models go stale within hours — consider online learning or rules
- Feature computation cost exceeds model benefit

---

## 10. Common Pitfalls

**Training-serving skew**: the most common production failure. The training pipeline computes features using a Spark job over a historical table. The serving pipeline computes the same features using a Python function over live data. Six months later, a schema change in the upstream table silently changes how age is computed. Training AUC stays high; production CTR drops 15%. Fix: compute features using a single shared library in both training and serving, with integration tests comparing feature values.

**Label leakage**: the model is trained on features that were not available at prediction time. Example: a fraud model includes "account_closed_within_7_days" as a feature. At training time this is known; at serving time it is unknown. The model achieves 99% AUC in offline evaluation and 55% in production. Fix: enforce point-in-time correctness in the feature store — features must be fetched as of the label event timestamp, not as of training time.

**Missing monitoring**: a model is deployed with no drift detection. Three months later, a product change shifts user behavior. The model continues serving stale predictions. Revenue drops 8% before anyone notices. Fix: monitor feature distribution (KL divergence, PSI), output distribution, and business metrics with automated alerting.

**Premature optimization**: spending six weeks building a two-tower deep retrieval system when a matrix factorization model would achieve 95% of the quality. Start simple, establish a baseline, then justify complexity with measured improvements.

**Ignoring cold start**: a collaborative filtering model cannot recommend items with fewer than 10 interactions or users with fewer than 3 interactions. These are served random popular items, which produces a terrible experience for 30% of users. Fix: have an explicit cold-start strategy — content-based features for new items, demographic-based for new users.

---

## 11. Technologies & Tools

| Category | Options | Notes |
|----------|---------|-------|
| Feature Store | Feast, Tecton, Vertex AI Feature Store, Hopsworks | Feast is open-source; Tecton is managed |
| Training Orchestration | Kubeflow Pipelines, Airflow, Prefect, Metaflow | Kubeflow tightly integrated with K8s |
| Experiment Tracking | MLflow, Weights & Biases, Neptune | MLflow is open-source standard |
| Model Serving | TorchServe, TF Serving, Triton, BentoML, Ray Serve | Triton best for multi-framework GPU serving |
| ANN Search | FAISS, ScaNN, Milvus, Weaviate, Pinecone | FAISS for self-hosted; Pinecone managed |
| Stream Processing | Apache Flink, Kafka Streams, Spark Structured Streaming | Flink lowest latency |
| Batch Processing | Apache Spark, Dask, BigQuery | Spark most widely used |
| Data Validation | Great Expectations, deequ, TFDV | Great Expectations most mature |
| Model Registry | MLflow Model Registry, Vertex AI, SageMaker Model Registry | |
| Monitoring | Evidently AI, WhyLogs, Arize Phoenix, Fiddler | Evidently is open-source |

---

## 12. Interview Questions with Answers

**Q: Walk me through the 6-step ML design interview framework.**
A structured approach: (1) Clarify requirements — scale (QPS, users), latency (P99 budget), accuracy targets, data availability. (2) Problem formulation — choose ML task type, define objective function and evaluation metrics. (3) Data and features — identify data sources, feature types, feature computation strategy, feature store architecture. (4) Model selection — match model complexity to data size and latency budget, choose retrieval vs ranking vs single-stage. (5) Serving architecture — online vs offline inference, caching strategy, latency budget decomposition. (6) Monitoring — data drift detection, model performance tracking, retraining triggers. Always clarify requirements before jumping to model selection.

**Q: Why is a two-stage retrieval + ranking architecture commonly used for recommendation systems?**
A two-stage design is used because it allows different accuracy/cost tradeoffs at each stage. Retrieval must score millions of items quickly, so it uses a simple embedding similarity model with ANN search, achieving recall@100 in <10ms. Ranking scores only 100-1000 candidates with a rich, expensive model, achieving high precision in <20ms. A single expensive model scoring all items would require either seconds of latency or a simpler model that achieves neither good recall nor good precision.

**Q: What is training-serving skew and how do you prevent it?**
Training-serving skew is a mismatch between feature values computed during training and feature values computed during serving for the same logical input. It is the most common production failure in ML systems. Common causes: different code paths for feature computation, schema changes in upstream data, different join logic. Prevention: (1) compute features using a shared library used in both training and serving; (2) write integration tests that compare training-time and serving-time feature values for the same entity; (3) log serving-time features and compare distributions against training data.

**Q: How do you handle the cold start problem in recommendation systems?**
Cold start affects new users (no interaction history) and new items (no engagement data). For new users: fall back to demographic-based or context-based recommendations (location, time of day, device type); use exploration strategies (epsilon-greedy) to gather signal quickly. For new items: use content-based features (title, description, category, image embeddings) until sufficient interaction data accumulates (typically 10-50 interactions); promote new items with a freshness boost to accelerate data collection.

**Q: How do you decide between online and offline inference?**
Online inference is required when predictions must reflect the user's current context (real-time fraud detection, live search ranking) or when the item set changes rapidly. Offline inference is appropriate when predictions can be precomputed (email campaign targeting, weekly report dashboards) or when the cost of online inference is prohibitive. Hybrid: precompute retrieval candidates offline and run ranking online. The decision driver is label availability and freshness requirements — if a user's state changes frequently and stale predictions cause business harm, use online inference.

**Q: What metrics do you monitor after a model is deployed?**
Three layers: (1) Data quality — feature distribution drift (PSI, KL divergence), null rates, schema violations; alert if PSI > 0.2. (2) Model performance — if labels are available quickly, track AUC/CTR/RMSE daily; use proxy metrics (click-through, conversion) if direct labels are delayed. (3) Business metrics — revenue, engagement, user satisfaction (NPS) — these are the ultimate success signal. Also monitor serving infrastructure: P99 latency, error rate, feature fetch failure rate.

**Q: How do you design a retraining pipeline?**
Three trigger types: (1) Scheduled — retrain weekly or daily regardless of performance, appropriate for stable distributions. (2) Drift-triggered — monitor feature distributions; if Population Stability Index exceeds 0.2 for key features, trigger retraining. (3) Performance-triggered — if online business metric (CTR, conversion) drops more than 5% from baseline, trigger retraining. The pipeline itself: validate new data -> compute features -> train model -> validate offline metrics (must exceed threshold) -> validate latency SLA -> shadow deploy -> gradual rollout.

**Q: What is position bias in recommendation and ranking, and how do you address it?**
Position bias is the tendency for users to click on items shown in higher positions regardless of their actual quality. A model trained on click data will learn to recommend items that were shown prominently, creating a self-reinforcing loop. Corrections: (1) Inverse propensity weighting — downweight clicks at high positions by the probability of being shown there. (2) Position-aware features — include the position as a feature during training and set it to a fixed value (e.g., position=1) at serving time. (3) Randomization — occasionally shuffle results to collect unbiased click data. (4) Counterfactual evaluation — estimate what CTR would be if all items were shown at the same position.

**Q: How do you design a model serving system that handles 100,000 QPS with P99 < 100ms?**
Key components: (1) Load-balanced prediction servers behind an API gateway, auto-scaling based on CPU utilization; (2) Feature cache (Redis) to avoid recomputing static features on every request — cache user features with 60-second TTL; (3) Model server with batching enabled (max_batch=32, max_wait=5ms) to amortize GPU cost; (4) Two-stage architecture so only a cheap retrieval model handles full QPS and the expensive ranking model handles fewer requests; (5) Circuit breakers to fall back to a simpler model when latency spikes; (6) Canary deployments to catch latency regressions before they affect all traffic.

**Q: What is the difference between batch features and real-time features in a feature store?**
Batch features are computed over historical data using Spark or Hive jobs, written to the offline store (S3/Hive), and synced to the online store (Redis) on a schedule (hourly or daily). They include aggregates like "user's average purchase value over the last 30 days." Real-time features are computed from streaming events using Flink or Kafka Streams and written directly to the online store, providing sub-minute freshness. They include signals like "number of page views in the last 5 minutes." The feature store serves both at request time; the serving latency is dominated by the online store read (<10ms for Redis) not the feature type.

**Q: How do you evaluate a recommendation model before deploying it?**
Offline evaluation: compute ranking metrics (NDCG@10, MAP, Recall@K) on a held-out test set with temporal splitting (train on days 1-28, test on days 29-30). Validate that the model beats the baseline by a statistically significant margin. Check for feature leakage by examining feature importance and removing suspicious features. Latency evaluation: benchmark P50/P99 serving latency on production-representative hardware and traffic patterns. Shadow evaluation: deploy the model in shadow mode (receiving traffic but not affecting users) and compare its rankings against the production model without affecting users. A/B test: run a controlled experiment with 5% of traffic, measure primary business metric (CTR, revenue), and require statistical significance (p < 0.05) before full rollout.

---

## 13. Best Practices

Start with a simple baseline and measure before adding complexity. A logistic regression with well-engineered features often achieves 90% of the quality of a deep neural network at 1% of the serving cost.

Separate feature computation from model training. Features should be reusable across multiple models. Invest in a feature store early — the cost of feature recomputation across 10 models is enormous.

Enforce point-in-time correctness in training data joins. Every feature value in the training set must be the value that would have been available at prediction time, not the current value.

Version everything — data, features, models, serving code. Every deployed model should have a lineage record: which data version, feature version, and code version produced it.

Design for graceful degradation. When the primary model is unavailable, serve from a simpler fallback. When the feature store is slow, use cached or default feature values.

Log predictions with full context. Store input features, model version, prediction score, and eventual outcome for every production prediction. This data is invaluable for debugging and future training.

Use shadow deployments before A/B tests. Shadow mode lets you validate correctness and latency without user impact. Only promote to A/B test when shadow evaluation passes.

Automate model validation gates. No model should reach production without passing AUC threshold, latency SLA, and fairness checks automatically. Remove human approval from the critical path.

---

## 14. Case Study

**Scenario: Real-time ad click-through-rate (CTR) prediction.** An ad exchange serves 5M ad impressions/hour. For each impression it must predict p(click) to rank ads and set bids, with a hard 10ms model-latency budget inside the bidding pipeline. We apply the 6-step ML system design framework end to end.

```
Step 2 data flow:
  ad serving -> Kafka (impression + click events)
       |
   Flink streaming job (join impression<->click within attribution window)
       |
   Feature Store (Feast): online=Redis (2ms), offline=warehouse (training)
       |
   Step 3 model: LightGBM, AUC 0.78
       |
   Step 4 serving: ONNX Runtime, 2ms p99
       |
   Step 5 monitoring: PSI (feature drift), online AUC on hour-old labels
       |
   Step 6 retraining: daily full batch + hourly delta on fresh labels
```

LightGBM offline AUC = 0.78, log-loss = 0.39, calibration error < 2%. Serving p99 = 2ms (ONNX, batch of 1), throughput 5M/hr = ~1400 req/s sustained per replica. Online AUC tracked on labels that arrive ~1 hour after impression (click attribution window).

**Step 1 requirements as code (latency-aware feature budget):**

```python
from dataclasses import dataclass

@dataclass(frozen=True)
class SLA:
    qps: int = 1400               # 5M/hour
    p99_latency_ms: float = 10.0
    model_latency_ms: float = 2.0
    feature_fetch_ms: float = 5.0  # leaves 3ms headroom
    min_auc: float = 0.75
    max_calibration_error: float = 0.02
```

**Step 3-4: training and ONNX export for low-latency serving:**

```python
import lightgbm as lgb
import numpy as np
from onnxmltools import convert_lightgbm
from onnxconverter_common import FloatTensorType

def train_ctr(X: np.ndarray, y: np.ndarray, n_features: int):
    model = lgb.LGBMClassifier(
        objective="binary", num_leaves=63, n_estimators=500,
        learning_rate=0.05, feature_fraction=0.8, metric="auc",
    )
    model.fit(X, y)
    onnx = convert_lightgbm(
        model, initial_types=[("input", FloatTensorType([None, n_features]))],
        zipmap=False,
    )
    return model, onnx
```

```python
import onnxruntime as ort
import numpy as np

class CTRService:
    def __init__(self, onnx_path: str, feature_store) -> None:
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 1     # single request, minimize latency
        self.sess = ort.InferenceSession(onnx_path, opts)
        self.fs = feature_store

    def predict(self, impression: dict) -> float:
        feats = self.fs.get_online(impression["user_id"], impression["ad_id"])
        x = np.asarray(feats, dtype=np.float32).reshape(1, -1)
        proba = self.sess.run(None, {"input": x})[1]   # class-1 probability
        return float(proba[0][1])
```

**Pitfall 1 — Train/serve feature skew.** The training pipeline computes "user 7-day click rate" with a SQL window; the serving path computes it with a different aggregation. The model sees inconsistent inputs and online AUC collapses below offline AUC.

```python
# BROKEN: feature defined twice, in batch SQL and in online code
train_rate = sql("clicks_7d / impressions_7d")
serve_rate = redis.get("clicks") / redis.get("impressions")   # subtly different

# FIX: one feature definition in Feast, materialized to both stores from the
# same transformation, read identically offline and online.
rate = feature_store.get_online_features(["user.ctr_7d"], entity_rows=[ent])
```

**Pitfall 2 — Training on biased click logs.** Ads shown in the top slot get more clicks because of position, not relevance; a model trained on raw clicks learns position, not quality.

```python
# BROKEN: label = click, ignoring that position drove the click
y = clicked

# FIX: inverse-propensity weighting to debias by display position
w = 1.0 / position_propensity[position]    # P(examined | position)
model.fit(X, y, sample_weight=w)
```

**Pitfall 3 — Latency SLA breach under load.** A traffic spike makes synchronous prediction queue up and breaches the 10ms budget, stalling the auction.

```python
# BROKEN: synchronous predict blocks the bid; a GC pause spikes p99
score = service.predict(impression)   # no timeout, no fallback

# FIX: bounded-timeout async call with a fast fallback (historical CTR prior)
async def score_with_fallback(svc, imp, timeout_s: float = 0.008) -> float:
    try:
        return await asyncio.wait_for(svc.apredict(imp), timeout_s)
    except asyncio.TimeoutError:
        return historical_ctr_prior(imp["ad_id"])   # degrade gracefully
```

**Interview Q&A:**

**Why LightGBM rather than a deep model for CTR here?** Tabular features (categorical IDs, counts, ratios) with a hard 2ms latency budget favor gradient-boosted trees: they train fast, infer in microseconds via ONNX, and match deep models on tabular data without GPUs. Deep CTR models (DeepFM, DCN) win mainly when learning embeddings for very high-cardinality features or fusing raw multimodal inputs.

**Why is calibration as important as AUC for CTR?** The predicted probability feeds the bid (bid is proportional to predicted CTR times value). AUC only measures ranking; a model can rank perfectly yet output probabilities that are systematically too high, causing overbidding and budget loss. Calibration (reliability curve, isotonic/Platt scaling) ensures the predicted 0.02 actually clicks 2% of the time.

**How do you handle labels that arrive an hour late?** The click attribution window means a label is only known after a delay. Stream the impression immediately, then join the click within the window in Flink; for monitoring, compute online AUC on a sliding window of attributed labels, accepting that the freshest impressions are still "pending" and excluded from the metric.

**What is the retraining strategy and why both daily and hourly?** A daily full retrain captures broad distribution shifts and rebuilds feature stats; an hourly delta (incremental warm-start or fine-tune on fresh attributed labels) keeps the model responsive to fast-moving campaigns and trending ads. The daily run is the safety net; the hourly delta is the freshness lever.

**How do you detect that the model is degrading in production?** Track input PSI per feature (alert > 0.2), prediction-distribution drift (KS test on the score histogram), and the gold metric, online AUC/log-loss on attributed labels. Accuracy is not used; CTR is a low-positive-rate problem. A silent feature-pipeline bug usually shows up as PSI drift before AUC moves.

**Walk through the 6-step framework for this system.** (1) Requirements: 5M/hr, 10ms, AUC>0.75, calibrated. (2) Data: Kafka events, Flink impression-click join, Feast feature store. (3) Model: LightGBM, binary log-loss, calibrated. (4) Serving: ONNX Runtime single-thread, async with fallback. (5) Monitoring: PSI, online AUC on hour-old labels, latency p99. (6) Retraining: daily full + hourly delta, champion/challenger promotion.

**Pitfall — Training on all historical data without a time-aware split causes future data leakage.**

```python
# BROKEN: random train/test split on time-series data — test set includes
# events BEFORE some training events → model sees the "future" during training
from sklearn.model_selection import train_test_split
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2)
# X_test may include t=100; X_train may include t=110 → leakage!

# FIX: time-ordered split — training data must be strictly before test data
cutoff_date = pd.Timestamp("2024-09-01")
train_mask = df["timestamp"] < cutoff_date
X_train, y_train = X[train_mask], y[train_mask]
X_test,  y_test  = X[~train_mask], y[~train_mask]
# Also: use walk-forward validation (TimeSeriesSplit) for cross-validation
from sklearn.model_selection import TimeSeriesSplit
tscv = TimeSeriesSplit(n_splits=5)
```

**How do you apply the 6-step ML system design framework to a cold-start recommendation problem?** (1) Requirements: new user/item must receive useful recommendations within 1 request, no click history available. (2) Data: item metadata (category, price, description embeddings), demographics if available. (3) Model: content-based filtering using item embedding similarity for new users; collaborative filtering only activates after 10+ interactions. (4) Serving: two-stage — content-based retrieval for cold users, collaborative ranking for warm users; shared item embedding index. (5) Monitoring: track cold-start CTR separately from warm-user CTR; alert if cold-start CTR drops below 50% of warm-user CTR. (6) Retraining: content-based model retrained weekly (item catalog changes); collaborative model retrained daily.

**What is the difference between online learning and retraining, and when do you choose each?** Online learning updates model parameters incrementally as each new example arrives — no batch retraining required. Suitable for simple models (logistic regression, linear contextual bandits) where gradients are cheap to compute. Retraining replaces the model periodically with a fresh run on recent data — required for complex models (XGBoost, neural nets) where online updates are unstable or computationally prohibitive. Choose online learning when: label delay is short (< 1 hour), the model is simple, and distribution shifts are gradual. Choose retraining when: labels arrive in batches (daily chargebacks), the model is complex, or the distribution shift is large and non-stationary.
