# ML Interview Patterns and Preparation

## 1. Concept Overview

ML interviews test three distinct skills: (1) theoretical understanding of algorithms, statistics, and optimization; (2) ML system design — architecting end-to-end production systems; and (3) coding — implementing ML algorithms from scratch. This module focuses on systematic frameworks and patterns for excelling at ML interviews at top-tier companies (FAANG, ML-first startups).

Most interview failures are structural, not knowledge failures. Candidates know XGBoost and neural networks but cannot articulate why they chose one over the other, skip the problem formulation step, or never discuss monitoring and data drift. This module provides frameworks to avoid these failure modes.

---

## 2. Intuition

One-line analogy: an ML interview is like a system design review — the interviewer wants to see how you think, not just what you know. They are evaluating your engineering judgment under ambiguity.

Mental model: treat each ML design question as a product specification. You are the senior ML engineer presenting to a skeptical engineering manager. Lead with constraints and tradeoffs, not with model architecture.

Why it matters: ML engineers who can communicate clearly and structure ambiguous problems are 10x more valuable than those who know the latest architecture. Most interview loops value communication and structure over obscure knowledge.

Key insight: interviewers test failure modes by design. They ask open-ended questions to see if you (a) clarify requirements, (b) propose a baseline before a complex solution, and (c) know when not to use ML. Candidates who jump to deep learning on question 1 fail the judgment test.

---

## 3. Core Principles

**Principle 1 — Baseline first:** every answer starts with the simplest model that could work. Logistic regression or heuristic rule. Justify every step up in complexity.

**Principle 2 — Metrics before models:** define success metrics before proposing any model. An interviewer who hears "I would train a neural network" before hearing "the business cares about precision over recall because false positives cost $50 each" knows you are not production-ready.

**Principle 3 — State assumptions explicitly:** "I am assuming the labeling budget is 10K samples," "I am assuming latency < 100ms," "I am assuming weekly retraining is acceptable." Unstated assumptions are interviewer red flags.

**Principle 4 — Discuss tradeoffs, not just solutions:** for every design choice, name the alternative you did not choose and explain why. "I chose a two-tower model over a cross-encoder because the cross-encoder's O(N^2) complexity is infeasible at 10M candidates, though it would give higher precision."

**Principle 5 — Production thinking:** mention monitoring, drift detection, retraining triggers, and failure modes. Candidates who stop at model training are viewed as junior.

---

## 4. Types / Architectures / Strategies

### The 6-Step ML Design Framework (45-minute interview)

```
Step 1 — Clarify Requirements (5 minutes)
  Questions to always ask:
  - What is the business objective? What does success look like?
  - What is the scale? (users, QPS, data volume)
  - What is the latency budget? (<10ms, <100ms, <1s, batch)
  - How much labeled data is available?
  - Are there fairness, privacy, or regulatory constraints?
  - What is the acceptable error rate? (precision vs recall tradeoff)

Step 2 — Problem Formulation (5 minutes)
  - Define the ML task: classification, regression, ranking, generation, RL
  - Define the label: what exactly are we predicting?
  - Define the evaluation metric: offline (AUC, NDCG, RMSE) and online (CTR, conversion)
  - Identify the train/val/test split strategy (time-based? user-based?)

Step 3 — Data and Features (10 minutes)
  - Data sources: logs, databases, 3rd party, user-generated
  - Label collection: historical, human annotation, implicit feedback
  - Feature categories: user, item, context, interaction
  - Feature engineering: embeddings, aggregations, temporal features
  - Handling missing data, cold start, class imbalance

Step 4 — Model Architecture (10 minutes)
  - Start with: logistic regression / linear model baseline
  - Gradient boosted trees: when features are tabular, structured
  - Neural network: when features are unstructured or interactions matter
  - Specific architectures: two-tower, transformer, GNN — justify each
  - Hyperparameters and training details

Step 5 — Serving and Infrastructure (10 minutes)
  - Online vs. offline scoring
  - Latency budget breakdown: feature retrieval + inference + postprocessing
  - Batching strategy
  - Caching: pre-computed embeddings, pre-scored candidates
  - A/B testing and rollout strategy (canary, shadow mode)
  - Fallback strategy if model is unavailable

Step 6 — Monitoring and Iteration (5 minutes)
  - Input data distribution monitoring (KL divergence, PSI)
  - Model performance monitoring (AUC on held-out labels)
  - Prediction distribution monitoring (output drift)
  - Retraining triggers: scheduled vs. drift-triggered
  - Feedback loop: how new labels are collected
```

### ML System Design Problem Templates

**Recommendation System Template:**
```
Problem formulation: ranking problem, maximize engagement (CTR, watch time)
Two-stage architecture:
  Stage 1 — Candidate Generation (retrieval):
    Two-tower model (user tower + item tower, dot product)
    ANN index (FAISS, ScaNN) — retrieve top-1000 candidates from 100M items
    Latency: <10ms, run online per request

  Stage 2 — Ranking:
    More complex model (DCN-v2, DIN, DLRM) on top-1000 candidates
    Rich cross-features, attention, explicit/implicit feedback
    Latency: <50ms, run online

  Post-ranking: business rules (diversity, freshness, safety filtering)
  Training: daily retraining on last 7 days of interaction logs
  Labels: click (positive), skip (negative), watch time (regression)
  Cold start: content-based features for new items, popular items for new users
```

**Fraud Detection Template:**
```
Problem formulation: binary classification, high precision + recall required
  False positive = customer friction; False negative = financial loss

Features:
  Transaction: amount, merchant, time, device
  User velocity: txn_count_1h, txn_count_24h, amount_sum_24h
  Graph features: account-device-IP relationship embeddings
  Historical: chargeback_rate_30d, dispute_count_90d

Model: gradient boosted trees (XGBoost/LightGBM) for speed + interpretability
  + GNN layer for graph-structural fraud ring detection
  + Calibrated probabilities (Platt scaling or isotonic regression)

Serving: real-time (<100ms), online features from feature store
Threshold: tuned for 85% precision at 65% recall (business requirement)
Monitoring: chargeback rate, false positive rate, concept drift (new fraud patterns)
Retraining: daily on rolling 90-day window + online learning for emerging patterns
```

**Search Ranking Template:**
```
Problem formulation: learning-to-rank (LTR), NDCG@10 metric
  Stages: query understanding -> retrieval -> ranking -> serving

Query understanding:
  Query classification (navigational/informational/transactional)
  Entity recognition, spell correction, query expansion

Retrieval:
  BM25 (sparse): fast, no training required, good for exact match
  Bi-encoder (dense): semantic search, FAISS ANN index
  Hybrid: RRF (Reciprocal Rank Fusion) or learned score combination

Ranking:
  Listwise LTR: LambdaMART or LambdaRank
  Or: cross-encoder for semantic re-ranking (top-100 only, latency budget)
  Features: query-document relevance, document quality, user context

Labels: click-through data (implicit) + human relevance judgments (explicit)
Evaluation: offline NDCG@10, online CTR, session success rate
```

**Classification Pipeline Template:**
```
Data: structured tabular features
Baseline: logistic regression (interpretable, fast, debuggable)
Step up: gradient boosted trees (handles missing, nonlinear, fast inference)
Step up: neural network with embeddings (when high-cardinality categoricals)

Training pipeline:
  Feature store -> feature retrieval -> preprocessing -> model -> evaluation
  Data validation: Great Expectations or custom schema checks
  Experiment tracking: MLflow or W&B

Serving:
  Batch: Spark + model serialize (Pickle/ONNX) -> predictions to database
  Online: FastAPI + model server (TorchServe, BentoML) -> REST API

Monitoring:
  Population Stability Index (PSI) on all features daily
  Model AUC on held-out labels weekly
  Prediction drift: KS test on output distribution
```

### Common Tradeoffs Framework

| Tradeoff | When to choose A | When to choose B |
|---|---|---|
| Precision vs Recall | A: Cost of FP > Cost of FN (spam, legal) | B: Cost of FN > Cost of FP (cancer, fraud) |
| Latency vs Accuracy | A: User-facing, real-time (<100ms) | B: Batch offline, accuracy-critical |
| Online vs Batch training | A: Concept drift is rapid (<24h shift) | B: Stable distribution, compute cost |
| Bias vs Variance | A: Small dataset, complex model -> regularize | B: Large dataset, underfitting -> bigger model |
| Exploration vs Exploitation | A: New users, cold start -> explore | B: Known users, revenue-critical -> exploit |
| Simple vs Complex model | A: Interpretability required (credit, legal) | B: Accuracy is primary metric, black box ok |

---

## 5. Architecture Diagrams

```
ML System — End-to-End Architecture
======================================

  [Data Sources]
  User logs | Item catalog | External APIs | Labels
        |
  [Data Pipeline]
  Ingestion (Kafka/Kinesis) -> ETL (Spark/DBT) -> Feature Store
        |                                               |
  [Offline Training]                           [Online Serving]
  Training data prep                           Feature retrieval (<5ms)
  -> Model training (GPU cluster)              -> Model inference (<20ms)
  -> Evaluation (holdout set)                  -> Post-processing
  -> Model registry (MLflow)                   -> Response (<50ms total)
        |
  [Deployment]
  Shadow mode -> Canary (5%) -> Full rollout
  A/B testing: control vs. treatment model
        |
  [Monitoring]
  Feature drift (PSI daily)
  Model AUC (weekly on labeled sample)
  Prediction drift (KS test daily)
  Business metrics (CTR, conversion) real-time
        |
  [Retraining Trigger]
  Schedule (weekly) OR drift alert (PSI > 0.2)


Bias-Variance Decomposition
============================

  Total Error = Bias^2 + Variance + Irreducible Noise

  High Bias (Underfitting):         High Variance (Overfitting):
  Training loss: high               Training loss: very low
  Val loss:      high               Val loss: high (gap from train)
  Fix: larger model, more features  Fix: regularization, more data
       less regularization               dropout, early stopping
       feature engineering               simpler model

  Bias-Variance Tradeoff:
  Model Complexity --->
  Error |
        |    Total Error
        |  \/
        |  /\ Variance
        |\/  \____
        |/Bias     ----
        |_____________ model complexity
             optimal


Precision-Recall Tradeoff
===========================

  Threshold 0.9:   High precision (0.95), Low recall (0.30)  <- conservative
  Threshold 0.5:   Medium precision (0.80), Medium recall (0.70) <- balanced
  Threshold 0.2:   Low precision (0.60), High recall (0.90) <- aggressive

  F_beta = (1 + beta^2) * P * R / (beta^2 * P + R)
  beta > 1: recall matters more (medical diagnosis)
  beta < 1: precision matters more (spam detection)
  beta = 1: F1, balanced


Cascade Model — Latency vs Accuracy
=====================================

  All Candidates (10M items)
        |
  Fast Model (LR, BM25): <1ms
  Top-1000 candidates
        |
  Medium Model (GBT, two-tower): <10ms
  Top-100 candidates
        |
  Slow Model (cross-encoder, LLM re-ranker): <50ms
  Top-10 results
        |
  Business Rules / Diversity Filter
  Final ranked list
```

---

## 6. How It Works — Detailed Mechanics

```python
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import StratifiedKFold, cross_val_score
from sklearn.metrics import (roc_auc_score, precision_recall_curve,
                              average_precision_score, f1_score,
                              confusion_matrix, classification_report)
from sklearn.calibration import CalibratedClassifierCV
from sklearn.preprocessing import StandardScaler
from typing import Any, Optional
import warnings
warnings.filterwarnings('ignore')


# ── Debugging checklist — code patterns ──────────────────────────────────────

def diagnose_model(
    model: Any,
    X_train: np.ndarray,
    y_train: np.ndarray,
    X_val: np.ndarray,
    y_val: np.ndarray,
) -> dict[str, float]:
    """
    Systematic model health check.
    Returns diagnostic metrics for bias-variance analysis.
    """
    train_preds = model.predict_proba(X_train)[:, 1]
    val_preds = model.predict_proba(X_val)[:, 1]

    train_auc = roc_auc_score(y_train, train_preds)
    val_auc = roc_auc_score(y_val, val_preds)
    gap = train_auc - val_auc

    # Class imbalance check
    pos_rate = y_train.mean()

    # Prediction distribution check (collapse detection)
    pred_std = val_preds.std()

    print("=== Model Diagnosis ===")
    print(f"Train AUC:  {train_auc:.4f}")
    print(f"Val AUC:    {val_auc:.4f}")
    print(f"Gap:        {gap:.4f}  {'HIGH VARIANCE' if gap > 0.05 else 'OK'}")
    print(f"Val AUC:    {val_auc:.4f}  {'HIGH BIAS' if val_auc < 0.70 else 'OK'}")
    print(f"Pos rate:   {pos_rate:.4f}  {'IMBALANCED (<1%)' if pos_rate < 0.01 else 'OK'}")
    print(f"Pred std:   {pred_std:.4f}  {'COLLAPSED (<0.01)' if pred_std < 0.01 else 'OK'}")

    return {
        'train_auc': train_auc,
        'val_auc': val_auc,
        'gap': gap,
        'pos_rate': pos_rate,
        'pred_std': pred_std,
    }


# ── Precision-Recall threshold tuning ────────────────────────────────────────

def tune_threshold(
    y_true: np.ndarray,
    y_prob: np.ndarray,
    beta: float = 1.0,
    target_precision: Optional[float] = None,
    target_recall: Optional[float] = None,
) -> dict[str, float]:
    """
    Find optimal threshold for F-beta or a precision/recall constraint.
    beta > 1: recall-weighted (medical)
    beta < 1: precision-weighted (spam)
    """
    precisions, recalls, thresholds = precision_recall_curve(y_true, y_prob)
    # thresholds has len(precisions) - 1; add sentinel
    thresholds = np.append(thresholds, 1.0)

    if target_precision is not None:
        # Maximize recall subject to precision >= target
        mask = precisions >= target_precision
        if mask.sum() == 0:
            print(f"Warning: target precision {target_precision} unreachable")
            return {}
        best_idx = np.argmax(recalls[mask])
        idx = np.where(mask)[0][best_idx]
    elif target_recall is not None:
        # Maximize precision subject to recall >= target
        mask = recalls >= target_recall
        if mask.sum() == 0:
            print(f"Warning: target recall {target_recall} unreachable")
            return {}
        best_idx = np.argmax(precisions[mask])
        idx = np.where(mask)[0][best_idx]
    else:
        # Maximize F-beta
        f_beta = (1 + beta**2) * (precisions * recalls) / \
                 (beta**2 * precisions + recalls + 1e-9)
        idx = np.argmax(f_beta)

    return {
        'threshold': float(thresholds[idx]),
        'precision': float(precisions[idx]),
        'recall': float(recalls[idx]),
        'f1': 2 * precisions[idx] * recalls[idx] / (precisions[idx] + recalls[idx] + 1e-9),
        'auprc': average_precision_score(y_true, y_prob),
    }


# ── Feature importance and selection ─────────────────────────────────────────

def feature_importance_analysis(
    model: GradientBoostingClassifier,
    feature_names: list[str],
    top_k: int = 20,
) -> pd.DataFrame:
    """
    Analyze feature importances with permutation importance for validation.
    GBT's built-in importances can overweight high-cardinality features.
    """
    importance_df = pd.DataFrame({
        'feature': feature_names,
        'importance': model.feature_importances_,
    }).sort_values('importance', ascending=False).head(top_k)

    print(importance_df.to_string(index=False))
    return importance_df


# ── Cross-validation — correct temporal split ────────────────────────────────

def temporal_cv(
    X: pd.DataFrame,
    y: pd.Series,
    date_col: str,
    n_splits: int = 5,
) -> list[float]:
    """
    Time-series aware cross-validation.
    Always train on past, evaluate on future.
    Never use StratifiedKFold for temporal data — causes data leakage.
    """
    from sklearn.model_selection import TimeSeriesSplit

    X_arr = X.drop(columns=[date_col]).values
    y_arr = y.values

    tscv = TimeSeriesSplit(n_splits=n_splits)
    aucs: list[float] = []

    for fold, (train_idx, val_idx) in enumerate(tscv.split(X_arr)):
        X_train, X_val = X_arr[train_idx], X_arr[val_idx]
        y_train, y_val = y_arr[train_idx], y_arr[val_idx]

        model = GradientBoostingClassifier(n_estimators=200, max_depth=4)
        model.fit(X_train, y_train)

        auc = roc_auc_score(y_val, model.predict_proba(X_val)[:, 1])
        aucs.append(auc)
        print(f"Fold {fold+1}: AUC = {auc:.4f}")

    print(f"Mean AUC: {np.mean(aucs):.4f} +/- {np.std(aucs):.4f}")
    return aucs


# ── Model calibration check ───────────────────────────────────────────────────

def calibration_check(
    y_true: np.ndarray,
    y_prob: np.ndarray,
    n_bins: int = 10,
) -> float:
    """
    Expected Calibration Error (ECE).
    A model predicting 0.7 probability should be correct 70% of the time.
    ECE > 0.05 typically indicates need for calibration (Platt or isotonic).
    """
    bin_edges = np.linspace(0, 1, n_bins + 1)
    ece = 0.0
    for i in range(n_bins):
        mask = (y_prob >= bin_edges[i]) & (y_prob < bin_edges[i + 1])
        if mask.sum() == 0:
            continue
        bin_acc = y_true[mask].mean()
        bin_conf = y_prob[mask].mean()
        bin_size = mask.sum() / len(y_true)
        ece += bin_size * abs(bin_acc - bin_conf)

    print(f"ECE: {ece:.4f} {'(needs calibration)' if ece > 0.05 else '(well calibrated)'}")
    return ece


# ── Drift detection — Population Stability Index ──────────────────────────────

def population_stability_index(
    reference: np.ndarray,
    current: np.ndarray,
    n_bins: int = 10,
) -> float:
    """
    PSI measures distribution shift between reference and current data.
    PSI < 0.1: no significant change
    0.1 <= PSI < 0.2: moderate change, monitor
    PSI >= 0.2: major shift, investigate and retrain
    """
    bins = np.percentile(reference, np.linspace(0, 100, n_bins + 1))
    bins[0] = -np.inf
    bins[-1] = np.inf

    ref_counts = np.histogram(reference, bins=bins)[0] + 1e-6   # avoid log(0)
    cur_counts = np.histogram(current, bins=bins)[0] + 1e-6

    ref_pct = ref_counts / ref_counts.sum()
    cur_pct = cur_counts / cur_counts.sum()

    psi = np.sum((cur_pct - ref_pct) * np.log(cur_pct / ref_pct))

    status = "OK" if psi < 0.1 else ("MONITOR" if psi < 0.2 else "ALERT — RETRAIN")
    print(f"PSI: {psi:.4f} [{status}]")
    return float(psi)


# ── A/B test — minimum detectable effect ─────────────────────────────────────

def minimum_sample_size(
    baseline_rate: float,
    minimum_detectable_effect: float,  # relative lift, e.g., 0.05 = 5%
    alpha: float = 0.05,
    power: float = 0.80,
) -> int:
    """
    Sample size per arm for a two-proportion z-test.
    baseline_rate: control conversion rate (e.g., 0.10 = 10%)
    minimum_detectable_effect: minimum lift to detect (relative)
    """
    from scipy import stats

    p1 = baseline_rate
    p2 = baseline_rate * (1 + minimum_detectable_effect)
    p_bar = (p1 + p2) / 2

    z_alpha = stats.norm.ppf(1 - alpha / 2)
    z_beta = stats.norm.ppf(power)

    n = (z_alpha * np.sqrt(2 * p_bar * (1 - p_bar)) +
         z_beta * np.sqrt(p1 * (1 - p1) + p2 * (1 - p2))) ** 2 / (p2 - p1) ** 2

    print(f"Required sample per arm: {int(np.ceil(n)):,}")
    print(f"Total sample: {2*int(np.ceil(n)):,}")
    return int(np.ceil(n))
```

---

## 7. Real-World Examples

**Google — feature store architecture:** Google's Vertex AI Feature Store provides online (low-latency key-value lookup for real-time serving) and offline (batch retrieval for training) access to features. Reusing features across models ensures training-serving consistency. Without a feature store, teams recreate features independently, causing training-serving skew — one of the most common production failure modes.

**Meta — ranking model evolution:** Feed ranking progressed from logistic regression (2006) -> gradient boosted trees (2012) -> deep neural networks (2015) -> DLRM (Deep Learning Recommendation Model, 2019). Each step was justified by incremental metric gain AND infrastructure readiness. LR is still used in the first retrieval stage because it can score 1M items in <5ms.

**Spotify — Discover Weekly evaluation:** Playlist recommendation measured by stream rate (>30s listens) and skip rate. Offline metrics: MRR (Mean Reciprocal Rank) of clicked tracks. Online metrics: stream count, save-to-library rate. The online/offline metric correlation was validated via holdout experiments: MRR improvement of 0.01 correlated with 0.8% increase in stream rate. Without this validation, offline improvements could not be trusted to translate online.

**Airbnb — price prediction:** Initial model: linear regression on location, size, amenities. Improved to gradient boosted trees adding temporal features (seasonality, events). Key learning: adding the neighborhood-level occupancy rate feature (aggregated from historical bookings) drove the largest single improvement (+12% RMSE reduction). Feature engineering > architecture choice at early stages.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key Factor |
|---|---|---|---|
| Model selection | Gradient boosted trees | Deep neural network | Data size: GBT wins <500K rows; DNN wins >5M rows |
| Evaluation metric | AUC-ROC | AUC-PR | Imbalanced classes: prefer PR-AUC |
| Training frequency | Daily batch | Real-time online | Concept drift speed; infrastructure cost |
| Feature engineering | Manual features | End-to-end learned | Domain expertise availability; data volume |
| Serving | Pre-computed scores | Real-time inference | Personalization depth; latency budget |
| Label strategy | Click = positive | Explicit rating | Click has higher volume; explicit is higher quality |
| Retraining trigger | Scheduled (weekly) | Drift-triggered | Stable distributions: schedule; volatile: drift |

---

## 9. When to Use / When NOT to Use

**When ML is appropriate:**
- Problem has clear input-output specification with measurable success metric
- Sufficient training data (>1K examples for classification, >100K for deep learning)
- Pattern is too complex for hand-coded rules
- Performance benefit justifies maintenance complexity

**When ML is NOT appropriate:**
- Simple heuristic suffices (rule-based systems for deterministic logic)
- Data is too scarce (<100 labeled examples for complex tasks)
- Decision must be fully explainable (some regulatory environments)
- Cost of maintaining ML system > business benefit
- Faster iteration possible with engineering (e.g., fixing a data pipeline bug)

**When to upgrade from simple to complex ML:**
- Logistic regression AUC is close to ceiling for the problem
- Nonlinear interactions in data that LR cannot capture
- Raw data inputs (images, text, audio) require representation learning
- Have infrastructure to support training, serving, and monitoring of complex models

---

## 10. Common Pitfalls

**Pitfall 1 — Training-serving skew (most common production failure):**
A team trained a CTR model with features computed at query time (e.g., user's last 10 clicked items). At serving, they used a different code path that computed the same feature differently — bucketing timestamps differently and missing the most recent click. The offline AUC was 0.84. Online CTR lift: 0.3% (expected 3%). Debug: log feature values at serving time, compare distributions to training. Fix: shared feature computation library for training and serving, or feature store with point-in-time correct retrieval.

**Pitfall 2 — Label leakage:**
A fraud detection team included `account_blocked` as a feature — a field set after fraud is confirmed. The model achieved AUC 0.99 in offline eval. In production: AUC 0.62. Fix: strict temporal feature construction — only use features with timestamp strictly before the label timestamp. Audit every feature for post-event information. Use a "feature creation timestamp" column in the feature store.

**Pitfall 3 — Metric-objective mismatch:**
A recommendation team optimized CTR (clicks per impression). They deployed a model that recommended clickbait titles. CTR improved 8%. Session length dropped 12%. User retention declined over 3 months. Fix: define the north star metric (long-term engagement, retention) before training. Use guard metrics in A/B tests — the experiment succeeds only if CTR improves AND session length does not regress more than X%.

**Pitfall 4 — Ignoring class imbalance:**
A team built a churn prediction model with 2% positive rate. Standard cross-entropy loss. Model predicted "no churn" for all users (98% accuracy). AUC: 0.52. Fix: use stratified sampling in train/val split, class_weight='balanced' or focal loss, oversample positives (SMOTE) or undersample negatives. Report AUC-PR (area under precision-recall curve) rather than AUC-ROC — AUC-PR is more informative for imbalanced problems.

**Pitfall 5 — Wrong cross-validation strategy:**
A time-series churn model used StratifiedKFold (random split). Offline AUC: 0.87. Online AUC: 0.71. Random splitting allowed the model to "look into the future" — validation data included users whose behavior was observed before some training data. Fix: TimeSeriesSplit — train on months 1-10, validate on month 11; train on months 1-11, validate on month 12. Simulate the exact temporal deployment scenario in offline evaluation.

**Pitfall 6 — Stopping at model training:**
A team built and trained an excellent model but did not set up monitoring. Three months later, a feature pipeline schema change dropped one high-importance feature silently (it became all-zeros). The model's AUC degraded from 0.88 to 0.73 over 6 weeks before a business stakeholder noticed conversion declining. Fix: monitor feature distribution (PSI) daily, model AUC on a held-out labeled sample weekly, output distribution (KS test) daily. Alert threshold: PSI > 0.2 for any top-10 feature.

---

## 11. Technologies & Tools

| Category | Tool | Use Case |
|---|---|---|
| Training framework | PyTorch, TensorFlow, JAX | Deep learning models |
| Tabular ML | XGBoost, LightGBM, CatBoost | Structured data |
| Feature store | Feast, Tecton, Vertex Feature Store | Training-serving consistency |
| Experiment tracking | MLflow, Weights & Biases, Neptune | Hyperparameter and metric logging |
| Model serving | TorchServe, BentoML, Triton | Production inference |
| Pipeline orchestration | Airflow, Prefect, Kubeflow | Training pipelines |
| Monitoring | Evidently AI, Arize Phoenix, Whylogs | Drift detection |
| A/B testing | Statsig, Optimizely, Eppo | Experiment platforms |
| Data validation | Great Expectations, Pandera | Schema and distribution checks |
| Hyperparameter tuning | Optuna, Ray Tune, Hyperopt | Automated HPO |
| Annotation | Label Studio, Scale AI, Prodigy | Data labeling |
| Vector search | FAISS, Pinecone, Weaviate | ANN for retrieval |

---

## 12. Interview Questions with Answers

**Q: Walk me through how you would design a recommendation system for a video streaming platform.**
Start by clarifying requirements: scale (100M users, 10M videos), latency (<200ms for ranked list), success metric (30-day retention, not just CTR). Problem formulation: ranking task, label = completed watch (>80% of video watched). Two-stage architecture: (1) Candidate generation — two-tower model, user tower on watch history, video tower on content features, ANN retrieval of top-1000 videos in <10ms; (2) Ranking — DCN-v2 or attention-based model on top-1000 with cross-features (user-video interaction history, content-user affinity), 50ms budget. Training: daily on last 30 days interaction logs with negative sampling (videos shown but not clicked). Cold start: for new videos, use content-based features; for new users, use popular items by region/time. Monitoring: AUC on held-out labels, watch time distribution, feature drift weekly.

**Q: What is the bias-variance tradeoff and how do you manage it in practice?**
Total generalization error = Bias^2 + Variance + Irreducible Noise. High bias: model too simple, underfits training data, both train and val loss are high. High variance: model too complex, overfits training data, large gap between train (low) and val (high) loss. In practice: start by checking the train-val gap. Gap > 5% AUC: overfitting — add regularization (L2, dropout), reduce model depth, increase training data. Val loss high regardless of gap: underfitting — increase model capacity, add features, reduce regularization. Manage via cross-validation (estimate true generalization), learning curves (plot val loss vs. training set size), and ensemble methods (reduce variance without increasing bias).

**Q: How do you handle class imbalance in a fraud detection problem?**
First, check whether imbalance is actually a problem — if the positive rate is 1% and the model scores all negatives correctly, business impact may be fine. If not, use a combination: (1) Algorithm level: class_weight='balanced' (reweights loss function proportional to inverse class frequency), or focal loss (down-weights easy negatives, focuses on hard examples). (2) Sampling level: oversample positives with SMOTE (synthetic minority oversampling), or undersample negatives. (3) Threshold tuning: don't use 0.5 as default — tune to the precision-recall operating point matching business constraints. (4) Evaluation: always use AUC-PR or F-beta, never accuracy, for imbalanced problems. For extreme imbalance (<0.01%), consider one-class classification (Isolation Forest, autoencoders for anomaly detection) instead of binary classification.

**Q: Explain data leakage and give two examples of how it manifests.**
Data leakage occurs when information unavailable at prediction time is used during training, creating artificially inflated offline metrics that do not hold in production. Example 1 — target leakage: a loan default model includes "number of missed payments" as a feature — this is determined after loan disbursement, not before. The model learns to predict a consequence of default rather than its cause. Example 2 — temporal leakage: a churn model uses random KFold split on users sorted by ID. Users with IDs close together were often created at similar times. The model trains on future data for some folds. Fix: always use temporal splits, audit all features for causal validity (can this feature be known at prediction time?), and use a temporal cutoff that simulates the deployment scenario.

**Q: What metrics would you use for an information retrieval/search system, and why?**
The core metric depends on whether rank matters and how many results are shown. Precision@K: what fraction of top-K results are relevant? Good when all K results are equally important. Recall@K: what fraction of all relevant documents appear in top-K? Good when coverage matters. MRR (Mean Reciprocal Rank): average of 1/rank_of_first_relevant_result. Good for navigational queries where users want the first good result. NDCG@K: normalized discounted cumulative gain, accounts for graded relevance and position discounting. Best overall metric for search — rewards relevant results at top positions more than bottom positions. In practice, also measure click-through rate and session success rate (user found what they needed without reformulating query) as online metrics.

**Q: How would you detect and handle concept drift in a production ML model?**
Concept drift occurs when the statistical relationship between features X and label Y changes over time. Detection approaches: (1) Input drift: monitor PSI (Population Stability Index) on each feature. PSI > 0.2 indicates major shift. (2) Prediction drift: KS test on score distribution (current week vs. reference). (3) Label drift: if labels arrive with delay (e.g., chargebacks take 30 days), monitor the labeled sample's rate vs. historical. (4) Performance drift: track AUC on a held-out labeled sample weekly. Handling: schedule periodic retraining (weekly or monthly), trigger retraining when PSI exceeds threshold, use online learning or model ensembles that weight recent data more. Do not wait for business stakeholders to report the problem — by then, 2-3 months of degradation has occurred.

**Q: Compare gradient boosted trees vs. neural networks. When do you choose each?**
Gradient boosted trees (XGBoost, LightGBM): prefer when data is tabular and structured, dataset size is <1M rows, features are well-engineered, training speed matters (<1 hour), interpretability is required (SHAP values are native), and missing values/categorical features need minimal preprocessing. Neural networks: prefer when inputs are unstructured (text, images, audio), high-dimensional embeddings are needed (user history, product catalog), dataset is large (>5M rows), cross-feature interactions are complex and numerous, and transfer learning from pretrained models is possible. In practice, many production systems use GBT for the ranking layer (fast, interpretable, handles tabular well) and neural networks for embedding generation (user/item towers in two-stage recommendation).

**Q: How do you approach feature engineering for a new ML problem?**
Start by understanding the data generating process — what causes the label? Build features from each causal factor. Categories: (1) Raw features: direct values (price, age, location). (2) Aggregation features: user historical behavior (purchase_count_30d, avg_order_value_90d, click_rate_7d). (3) Ratio/interaction features: cart_abandonment_rate = abandoned_carts / total_carts. (4) Temporal features: day of week, hour, time since last action, days since account creation. (5) Entity embeddings: high-cardinality categoricals (merchant_id, product_id) trained as embeddings. (6) Graph features: for network data, degree, PageRank, community membership. Feature validation: check for leakage (post-event information), check importance via permutation importance, monitor distribution drift. The single highest-ROI activity in early-stage ML is feature engineering, not model tuning.

**Q: What is the difference between online and batch machine learning? When do you use each?**
Batch (offline) learning: model is trained periodically on a fixed dataset, deployed as a static artifact. Simple to implement, easy to debug, evaluation is straightforward. Drawback: cannot adapt to rapid distribution shifts. Use when concept drift is slow (days-weeks), training data volume is large, and compute for real-time training is unavailable. Online learning: model updates incrementally with each new data point or mini-batch. Adapts to drift rapidly, lower training infrastructure requirements. Drawback: harder to debug, sensitive to noisy labels, can catastrophically forget. Use when concept drift is rapid (hours), labels arrive in real-time (ad click data), and the system must personalize for individual users continuously. In practice, most production systems use batch retraining (daily/weekly) with a simpler online component for recency signals (user's most recent 10 actions as contextual features).

**Q: How do you evaluate an uplift/recommendation model when true counterfactuals are unobservable?**
Use a combination: (1) Qini/AUUC curve: sort users by predicted CATE/uplift score, compute actual lift vs. random targeting across deciles. Higher area under Qini curve = better ranking of persuadables. Does not require counterfactuals — uses observed outcomes from a historical A/B test. (2) Held-out randomized experiment: deploy the uplift model, treat top quintile of predicted CATE, compare to random treatment group. Measure actual ATE in top quintile vs. random — should be higher. (3) Calibration: compare predicted CATE to observed lift in each decile. If predicted CATE of 10% in decile 1 matches observed 10% lift in that decile, the model is calibrated. (4) Placebo test: estimate treatment effect on a pre-treatment outcome (e.g., purchases before the campaign). Should be zero — any non-zero estimate indicates model confounding.

**Q: Describe the ML interview failure modes you have seen and how to avoid them.**
The most common failure modes: (1) Jumping to model architecture without clarifying requirements — fix: spend the first 5 minutes asking business-context questions. (2) No baseline — fix: always propose logistic regression or rule-based system before neural networks, and explain why you would upgrade. (3) No monitoring plan — fix: always end with "and here is how I would monitor this in production." (4) Optimizing a metric that does not match business value — fix: ask what the business cares about before defining the ML objective. (5) Ignoring the data problem — fix: spend 10 minutes on data sources, labeling strategy, and class imbalance before discussing models. (6) Stating solutions without tradeoffs — fix: for every design choice, name the alternative and explain the tradeoff. Interviewers explicitly probe these gaps. Structured preparation: practice 5 end-to-end ML design problems per week using the 6-step framework, out loud, in 45 minutes.

---

## 13. Best Practices

- Always start with a simple baseline (logistic regression or rule-based) — it establishes a lower bound and is often good enough.
- Define metrics before training — if you cannot measure success, you cannot improve.
- Use temporal splits for all time-series data. StratifiedKFold on temporal data causes leakage.
- Monitor PSI on top features daily in production. Set automated alerts at PSI > 0.2.
- Calibrate model probabilities before using them for threshold-based decisions. ECE > 0.05 needs calibration.
- Use AUC-PR (not AUC-ROC) for imbalanced classification problems. AUC-ROC is insensitive to class imbalance.
- Log feature values at serving time. Compare distributions to training data regularly (training-serving skew detection).
- A/B test every significant model change. Do not skip the experiment because offline metrics improved.
- Use guard metrics in A/B tests — primary metric improvement + zero statistically significant regressions in guard metrics.
- Document all feature creation logic with temporal semantics (what timestamp the feature is computed as-of).
- Never use accuracy as the sole metric for imbalanced datasets.
- Track both offline (AUC, NDCG) and online (CTR, conversion) metrics and calibrate the relationship between them.
- Compute sample size requirements before starting an A/B test to avoid underpowered experiments.

---

## 14. Case Study

**Problem: Design an ML system for e-commerce product ranking (45-minute interview response)**

Step 1 — Clarify Requirements (5 min):
- Scale: 5M users/day, 20M product catalog, 200ms latency budget for full ranking
- Business goal: maximize purchase conversion rate (not just clicks)
- Labels: purchase = strong positive; add-to-cart = weak positive; click = weak positive; impression without interaction = negative
- Data: 18 months of user interaction logs, product catalog (title, description, category, images, price)
- Constraints: model must not discriminate by protected attributes (ECOA compliance for credit-related categories)

Step 2 — Problem Formulation (5 min):
- ML task: learning-to-rank (listwise, optimize NDCG@10)
- Label: purchase within 24h of click = strong positive (label = 3); add-to-cart no purchase = medium (label = 2); click no add = weak (label = 1); no click = negative (label = 0)
- Evaluation: offline NDCG@10, online conversion rate (north star), click-through rate (guardrail)
- Split: time-based — train on months 1-16, validate on month 17, test on month 18

Step 3 — Data and Features (10 min):
```
User features:
  - Embedding: user_id (trained, 128d) for returning users
  - History: category_affinity_30d (top-10 categories, visit frequency)
  - Context: session_query, device, hour, day_of_week, location
  - LTV tier: high/medium/low based on 90-day spend

Item features:
  - Text: title/description embedding (Sentence-BERT, 384d, precomputed)
  - Image: ResNet-50 embedding (2048d -> PCA to 128d, precomputed)
  - Catalog: price, brand, category, avg_rating, review_count
  - Popularity: purchase_rate_7d, view_rate_7d, inventory_level

Interaction features (cross):
  - Query-item semantic similarity (dot product of query embedding and title embedding)
  - User-category affinity (user's 30d category affinity * item's category)
  - Price relative to user's historical purchase price distribution

Cold start:
  - New users: use session context only (device, location, query)
  - New items: use content features (text, image, category), no historical signals
```

Step 4 — Model Architecture (10 min):
```
Stage 1 — Retrieval (top-1000 from 20M):
  Two-tower model:
  - User tower: MLP on [user_embedding, history_features, context] -> 128d
  - Item tower: MLP on [title_embedding, image_embedding, catalog_features] -> 128d
  - Score: dot product; ANN index (ScaNN): <5ms for 1000 candidates
  - Train: sampled softmax on purchase logs; daily retraining

Stage 2 — Ranking (top-10 from 1000):
  Deep Cross Network v2 (DCN-v2):
  - Input: user, item, interaction features concatenated
  - Cross network: 6 layers (explicit feature interactions)
  - Deep network: [512, 256, 128] ReLU layers
  - Output: 4-class ordinal regression (purchase > cart > click > no action)
  - Loss: LambdaRank (listwise, optimizes NDCG@10 directly)
  - Train: weekly on rolling 60-day interaction logs
  - Inference: 150ms for 1000 candidates on CPU cluster

Post-ranking:
  Business rules: enforce inventory (no out-of-stock), diversity (no >3 items from same brand), safety filters
```

Step 5 — Serving and Infrastructure (10 min):
```
Feature store (Feast):
  Online: Redis — user features, <2ms lookup
  Offline: BigQuery — training data retrieval

Latency budget:
  Feature retrieval: 20ms (Redis)
  Stage 1 retrieval: 5ms (ScaNN ANN)
  Stage 2 ranking (DCN-v2): 150ms (100 products * batched inference)
  Post-processing: 5ms
  Total: ~180ms (under 200ms budget)

A/B testing:
  Shadow mode for 2 weeks: run new model, log scores, do not serve
  Canary: 5% traffic for 1 week; monitor conversion rate and P95 latency
  Gradual rollout: 5% -> 25% -> 50% -> 100% over 4 weeks
  Holdback: keep 5% on old model for 6 months to measure long-term impact
```

Step 6 — Monitoring and Iteration (5 min):
```
Daily:
  PSI on top-20 features; alert if PSI > 0.2
  Prediction distribution (score histogram); KS test vs. last week
  Latency P50, P95, P99

Weekly:
  NDCG@10 on labeled holdout set (500K interactions, human-labeled relevance)
  Conversion rate by user segment, device type
  Feature importance drift (SHAP value changes)

Retraining triggers:
  Scheduled: weekly full retraining (DCN-v2), daily delta for two-tower
  Drift-triggered: if NDCG degrades >5% on holdout -> immediate retraining alert

Iteration roadmap:
  Month 1: establish baselines, two-tower + LambdaMART
  Month 3: add image features, improve cold start
  Month 6: add user interest graph (GNN) for long-term preference modeling
  Month 12: multimodal ranking with session context attention
```

**Expected outcomes:**
- Offline NDCG@10: 0.72 (baseline heuristic: 0.58)
- Online conversion rate: +8% vs. popularity-based ranking
- P95 latency: 190ms (within 200ms budget)
- NDCG@10 stability over 6 months: standard deviation < 0.02 (monitoring effective)
