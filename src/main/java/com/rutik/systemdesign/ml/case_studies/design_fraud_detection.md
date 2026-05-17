# Design a Real-Time Fraud Detection System

## Problem Statement

Design a fraud detection system for a payment platform processing 10,000 transactions per second. Every transaction must be scored in under 50ms P99. The business requires greater than 99.9% precision (false positives block legitimate transactions, destroying user trust) while catching greater than 80% of fraud (recall). The fraud base rate is 0.1% — 1 in 1,000 transactions is fraudulent, creating severe class imbalance. The system must support feedback loops: when analysts review flagged transactions, those labels flow back to retrain the model.

Constraints:
- 10K TPS peak, 864M transactions/day
- P99 latency < 50ms end-to-end
- Precision > 99.9%, Recall > 80% (F-beta with beta=0.5, precision-weighted)
- Fraud rate 0.1% — class imbalance ratio 1:1000
- Model must be explainable (regulatory requirement: reason codes for declined transactions)
- Online learning: model updated at minimum daily, preferably hourly

---

## Architecture Overview

```
  Transaction Event
        │
        v
  ┌─────────────────────────────────────────────┐
  │  RULE ENGINE (<1ms)                         │
  │  - Blocklist check (stolen cards/devices)   │
  │  - Allowlist (verified merchants, own ATMs) │
  │  - Hard rules: amount > $10K, new country   │
  │  BLOCK / ALLOW / PASS-THROUGH               │
  └────────────────┬────────────────────────────┘
                   │ PASS-THROUGH (~60% of TXs)
                   v
  ┌─────────────────────────────────────────────┐
  │  FEATURE COMPUTATION (5-10ms)               │
  │                                             │
  │  Real-time features (from event):           │
  │  - tx_amount, merchant_category, hour       │
  │  - device_fingerprint, ip_country           │
  │                                             │
  │  Aggregated features (from Redis):          │
  │  - spend_velocity_1h / 24h / 7d            │
  │  - tx_count_1h (new card: <3 = suspicious)  │
  │  - new_merchant_flag (first time at merch)  │
  │  - failed_attempts_15min                    │
  │                                             │
  │  Graph features (precomputed, cached):      │
  │  - shared_device_with_known_fraud           │
  │  - card_device_association_age_days         │
  └────────────────┬────────────────────────────┘
                   │
                   v
  ┌─────────────────────────────────────────────┐
  │  ML MODEL (XGBoost, 10-15ms)                │
  │  Output: fraud_probability [0.0, 1.0]       │
  │                                             │
  │  Threshold routing:                         │
  │  score > 0.85 → AUTO BLOCK                  │
  │  score 0.40-0.85 → HUMAN REVIEW QUEUE       │
  │  score < 0.40 → AUTO APPROVE                │
  └────────────────┬────────────────────────────┘
                   │
         ┌─────────┼──────────┐
         v         v          v
    AUTO        HUMAN      AUTO
    BLOCK       REVIEW     APPROVE
                QUEUE
                   │
                   v
     ┌─────────────────────────┐
     │  ANALYST DASHBOARD      │
     │  - Reason codes         │
     │  - SHAP explanations    │
     │  - Review → label       │
     └────────────┬────────────┘
                  │ Labeled feedback
                  v
     ┌─────────────────────────┐
     │  TRAINING PIPELINE      │  (Flink + Kafka + Spark)
     │  Daily/hourly retrain   │
     │  → MLflow registry      │
     │  → Shadow deploy → A/B  │
     └─────────────────────────┘

  Streaming Feature Computation (Flink):
  ┌──────────────────────────────────────────────────────────┐
  │  Kafka (tx events)                                       │
  │       │                                                  │
  │       v                                                  │
  │  Flink Job (sliding windows)                             │
  │  - [card_id, 1h]  → sum(amount), count(tx)              │
  │  - [card_id, 24h] → sum(amount), distinct merchants      │
  │  - [device_id, 1h] → count(cards_used)                  │
  │       │                                                  │
  │       v                                                  │
  │  Redis (atomic INCR + EXPIRE)                            │
  │  Key: feat:{card_id}:{window}:{metric}                   │
  │  TTL: window_size + 10min buffer                         │
  └──────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

**Rule engine first**: 20-30% of transactions are cleared by allowlist/blocklist in under 1ms, reducing ML model load and latency budget for the remaining transactions. Known fraud patterns (card number appears in breach database) are handled deterministically with zero false-negative risk.

**XGBoost over deep learning**: Regulatory requirements mandate explainability — each decline must include a human-readable reason code (e.g., "unusual transaction location," "high spend velocity"). XGBoost with SHAP values satisfies this. Deep models require post-hoc approximations (LIME) that can be inconsistent. XGBoost also trains in minutes, enabling hourly retraining on fresh fraud patterns.

**Threshold tuning for asymmetric costs**: Blocking a legitimate transaction costs far more than missing fraud in brand terms. Use F-beta with beta=0.5 (precision-weighted) for threshold selection. Optimal threshold is typically 0.85 for auto-block, not the naive 0.5. Maintain a borderline zone (0.40-0.85) for human review rather than binary auto-decisions.

**Class imbalance**: 0.1% fraud rate means naive training optimizes for always predicting legitimate. Three strategies applied together: (1) scale_pos_weight=999 in XGBoost (ratio of negative to positive examples), (2) SMOTE to generate synthetic minority samples in feature space, (3) undersample majority class 10:1 during training. Target training ratio: 10:1 negative to positive (still imbalanced but manageable).

**Streaming features with Flink**: Spend velocity over the last 1 hour is a top-3 feature for fraud. Computing it requires stateful aggregation. Flink maintains per-card windowed state in RocksDB with exactly-once semantics. Results are written to Redis for sub-millisecond read during inference.

---

## Implementation

### XGBoost Model with Threshold Optimization

```python
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.model_selection import StratifiedKFold
from sklearn.metrics import precision_recall_curve, fbeta_score, confusion_matrix
from imblearn.over_sampling import SMOTE
from imblearn.under_sampling import RandomUnderSampler
from imblearn.pipeline import Pipeline as ImbPipeline
import shap
from typing import Optional


def build_fraud_features(df: pd.DataFrame) -> pd.DataFrame:
    """Feature engineering for fraud detection.

    Expected raw columns:
      tx_amount, merchant_category_code (MCC), hour_of_day, day_of_week,
      ip_country, card_country, device_fingerprint_age_days,
      spend_velocity_1h, spend_velocity_24h, spend_velocity_7d,
      tx_count_1h, tx_count_24h,
      is_new_merchant (first tx at this merchant),
      failed_attempts_15min,
      shared_device_with_fraud (graph feature, 0/1),
      card_device_age_days (how long card-device pair has been seen together)
    """
    f = df.copy()

    # Derived features
    f["amount_vs_velocity_ratio"] = f["tx_amount"] / (f["spend_velocity_1h"] + 1.0)
    f["country_mismatch"] = (f["ip_country"] != f["card_country"]).astype(int)
    f["is_high_risk_mcc"] = f["merchant_category_code"].isin(
        [6011, 6051, 7995, 5912]  # ATM, crypto, gambling, pharmacies
    ).astype(int)
    f["tx_frequency_spike"] = (f["tx_count_1h"] > 5).astype(int)
    f["unusual_hour"] = f["hour_of_day"].between(0, 5).astype(int)
    f["new_device"] = (f["device_fingerprint_age_days"] < 1).astype(int)
    f["velocity_24h_vs_7d"] = f["spend_velocity_24h"] / (
        f["spend_velocity_7d"] / 7.0 + 1.0
    )  # daily spend vs weekly average

    feature_cols = [
        "tx_amount", "hour_of_day", "day_of_week",
        "spend_velocity_1h", "spend_velocity_24h", "spend_velocity_7d",
        "tx_count_1h", "tx_count_24h",
        "is_new_merchant", "failed_attempts_15min",
        "shared_device_with_fraud", "card_device_age_days",
        "amount_vs_velocity_ratio", "country_mismatch", "is_high_risk_mcc",
        "tx_frequency_spike", "unusual_hour", "new_device",
        "velocity_24h_vs_7d",
    ]
    return f[feature_cols]


def train_fraud_model(
    X: pd.DataFrame,
    y: np.ndarray,
    fraud_rate: float = 0.001,
) -> xgb.XGBClassifier:
    """Train XGBoost with class-imbalance handling."""
    # scale_pos_weight: ratio of negative to positive
    # For 0.1% fraud: (1 - 0.001) / 0.001 = 999
    scale_pos_weight = (1 - fraud_rate) / fraud_rate

    model = xgb.XGBClassifier(
        n_estimators=500,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        scale_pos_weight=scale_pos_weight,
        eval_metric=["logloss", "auc"],
        use_label_encoder=False,
        random_state=42,
        n_jobs=-1,
        tree_method="hist",  # faster for large datasets
    )

    # Combined resampling: SMOTE minority class up, then undersample majority
    smote = SMOTE(sampling_strategy=0.1, random_state=42)  # fraud → 10% of data
    under = RandomUnderSampler(sampling_strategy=0.5, random_state=42)  # reduce majority
    pipeline = ImbPipeline([("smote", smote), ("under", under)])

    X_res, y_res = pipeline.fit_resample(X, y)
    print(f"Resampled: {np.bincount(y_res.astype(int))} (legit, fraud)")

    # Cross-val to estimate generalization
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    oof_probs = np.zeros(len(X_res))
    for fold, (train_idx, val_idx) in enumerate(cv.split(X_res, y_res)):
        model.fit(
            X_res.iloc[train_idx], y_res.iloc[train_idx],
            eval_set=[(X_res.iloc[val_idx], y_res.iloc[val_idx])],
            verbose=False,
        )
        oof_probs[val_idx] = model.predict_proba(X_res.iloc[val_idx])[:, 1]

    # Final fit on all resampled data
    model.fit(X_res, y_res)
    return model


def find_optimal_thresholds(
    model: xgb.XGBClassifier,
    X_val: pd.DataFrame,
    y_val: np.ndarray,
    beta: float = 0.5,  # precision-weighted
) -> dict[str, float]:
    """Find auto-block and review thresholds using F-beta on validation set."""
    probs = model.predict_proba(X_val)[:, 1]
    precision, recall, thresholds = precision_recall_curve(y_val, probs)

    f_scores = []
    for p, r in zip(precision, recall):
        if p + r == 0:
            f_scores.append(0.0)
        else:
            f_beta = (1 + beta**2) * p * r / (beta**2 * p + r)
            f_scores.append(f_beta)

    best_idx = np.argmax(f_scores)
    auto_block_threshold = float(thresholds[best_idx])

    # Review zone lower bound: maximize recall above 80%
    recall_80_idx = np.where(recall >= 0.80)[0]
    review_lower = float(thresholds[recall_80_idx[-1]]) if len(recall_80_idx) else 0.3

    result = {
        "auto_block": auto_block_threshold,
        "review_lower": review_lower,
        "best_f_beta": float(f_scores[best_idx]),
        "precision_at_threshold": float(precision[best_idx]),
        "recall_at_threshold": float(recall[best_idx]),
    }
    print(f"Thresholds: auto_block={auto_block_threshold:.3f}, "
          f"review_lower={review_lower:.3f}")
    print(f"F{beta}={result['best_f_beta']:.4f}, "
          f"P={result['precision_at_threshold']:.4f}, "
          f"R={result['recall_at_threshold']:.4f}")
    return result


def explain_decision(
    model: xgb.XGBClassifier,
    X_instance: pd.DataFrame,
    top_n: int = 5,
) -> list[dict]:
    """Generate SHAP-based reason codes for a single transaction decision."""
    explainer = shap.TreeExplainer(model)
    shap_values = explainer.shap_values(X_instance)

    feature_names = X_instance.columns.tolist()
    shap_row = shap_values[0]  # single instance
    contributions = sorted(
        zip(feature_names, shap_row),
        key=lambda x: abs(x[1]),
        reverse=True,
    )

    reason_map = {
        "spend_velocity_1h": "Unusually high spend in last hour",
        "country_mismatch": "Transaction country differs from card country",
        "is_new_merchant": "First transaction at this merchant",
        "shared_device_with_fraud": "Device associated with known fraud",
        "tx_frequency_spike": "Abnormal transaction frequency",
        "unusual_hour": "Transaction at unusual hour",
        "amount_vs_velocity_ratio": "Amount unusually high relative to recent spending",
    }

    reasons = []
    for feature, shap_val in contributions[:top_n]:
        if shap_val > 0:  # contributing to fraud score
            reasons.append({
                "feature": feature,
                "contribution": float(shap_val),
                "reason_code": reason_map.get(feature, f"Feature: {feature}"),
            })
    return reasons
```

### Streaming Feature Aggregation (Flink concept in Python)

```python
from dataclasses import dataclass, field
from collections import deque
import time
import redis


@dataclass
class SlidingWindowAggregator:
    """
    Conceptual implementation of Flink sliding-window aggregation.
    In production: Flink with RocksDB state backend, exactly-once semantics.
    This shows the logic; actual Flink code uses Java/Scala DataStream API.
    """
    window_seconds: int
    events: deque = field(default_factory=deque)

    def add_event(self, amount: float, timestamp: float) -> None:
        self.events.append((timestamp, amount))
        self._evict_old(timestamp)

    def _evict_old(self, current_ts: float) -> None:
        cutoff = current_ts - self.window_seconds
        while self.events and self.events[0][0] < cutoff:
            self.events.popleft()

    def get_sum(self) -> float:
        return sum(amt for _, amt in self.events)

    def get_count(self) -> int:
        return len(self.events)


class FraudFeatureStore:
    """Redis-backed feature store for real-time fraud features."""

    def __init__(self, redis_client: redis.Redis) -> None:
        self.redis = redis_client

    def update_spend_velocity(
        self,
        card_id: str,
        amount: float,
        timestamp: float,
    ) -> None:
        """Increment rolling spend using sorted-set trick."""
        key_1h = f"spend:{card_id}:1h"
        key_24h = f"spend:{card_id}:24h"
        key_7d = f"spend:{card_id}:7d"

        pipe = self.redis.pipeline()
        # Store amount in sorted set with timestamp as score
        member = f"{timestamp}:{amount}"
        pipe.zadd(key_1h, {member: timestamp})
        pipe.zadd(key_24h, {member: timestamp})
        pipe.zadd(key_7d, {member: timestamp})

        # Remove entries outside window
        now = timestamp
        pipe.zremrangebyscore(key_1h, "-inf", now - 3600)
        pipe.zremrangebyscore(key_24h, "-inf", now - 86400)
        pipe.zremrangebyscore(key_7d, "-inf", now - 604800)

        # Set TTL
        pipe.expire(key_1h, 7200)
        pipe.expire(key_24h, 90000)
        pipe.expire(key_7d, 691200)
        pipe.execute()

    def get_spend_features(self, card_id: str) -> dict[str, float]:
        """Get spend velocity features for scoring."""
        now = time.time()
        pipe = self.redis.pipeline()

        for window_name, window_sec in [("1h", 3600), ("24h", 86400), ("7d", 604800)]:
            key = f"spend:{card_id}:{window_name}"
            pipe.zrangebyscore(key, now - window_sec, "+inf", withscores=False)
        results = pipe.execute()

        def sum_amounts(members: list[bytes]) -> float:
            total = 0.0
            for m in members:
                try:
                    total += float(m.decode().split(":")[1])
                except (IndexError, ValueError):
                    pass
            return total

        return {
            "spend_velocity_1h": sum_amounts(results[0]),
            "spend_velocity_24h": sum_amounts(results[1]),
            "spend_velocity_7d": sum_amounts(results[2]),
            "tx_count_1h": len(results[0]),
            "tx_count_24h": len(results[1]),
        }
```

### Online Scoring Service

```python
from dataclasses import dataclass
import numpy as np
import xgboost as xgb
import redis
import time


@dataclass
class FraudScore:
    transaction_id: str
    fraud_probability: float
    decision: str  # "approve", "review", "block"
    reason_codes: list[str]
    latency_ms: float


class FraudScoringService:
    """
    End-to-end fraud scoring pipeline.
    Target: <50ms P99 including feature fetch and model inference.
    """

    BLOCKLIST_KEY = "blocklist:cards"
    ALLOWLIST_KEY = "allowlist:merchants"

    def __init__(
        self,
        model: xgb.XGBClassifier,
        feature_store: "FraudFeatureStore",
        thresholds: dict[str, float],
    ) -> None:
        self.model = model
        self.feature_store = feature_store
        self.auto_block_threshold = thresholds["auto_block"]   # e.g., 0.85
        self.review_lower = thresholds["review_lower"]         # e.g., 0.40

    def score(self, transaction: dict) -> FraudScore:
        start = time.perf_counter()
        tx_id = transaction["transaction_id"]
        card_id = transaction["card_id"]

        # Stage 1: Rule engine (<1ms)
        if self._is_blocklisted(card_id):
            return FraudScore(tx_id, 1.0, "block", ["Card on blocklist"],
                              (time.perf_counter() - start) * 1000)
        if self._is_allowlisted(transaction.get("merchant_id", "")):
            return FraudScore(tx_id, 0.0, "approve", [],
                              (time.perf_counter() - start) * 1000)

        # Stage 2: Feature computation (5-10ms)
        velocity_features = self.feature_store.get_spend_features(card_id)
        all_features = {**transaction, **velocity_features}
        X = self._build_feature_vector(all_features)

        # Stage 3: ML scoring (10-15ms)
        fraud_prob = float(self.model.predict_proba(X)[:, 1][0])

        # Stage 4: Decision + reason codes
        if fraud_prob >= self.auto_block_threshold:
            decision = "block"
        elif fraud_prob >= self.review_lower:
            decision = "review"
        else:
            decision = "approve"

        latency = (time.perf_counter() - start) * 1000
        return FraudScore(tx_id, fraud_prob, decision, [], latency)

    def _is_blocklisted(self, card_id: str) -> bool:
        return bool(self.feature_store.redis.sismember(self.BLOCKLIST_KEY, card_id))

    def _is_allowlisted(self, merchant_id: str) -> bool:
        return bool(self.feature_store.redis.sismember(self.ALLOWLIST_KEY, merchant_id))

    def _build_feature_vector(self, features: dict) -> np.ndarray:
        # Build ordered feature array matching model's expected input
        feature_order = [
            "tx_amount", "hour_of_day", "day_of_week",
            "spend_velocity_1h", "spend_velocity_24h", "spend_velocity_7d",
            "tx_count_1h", "tx_count_24h", "is_new_merchant",
            "failed_attempts_15min", "shared_device_with_fraud",
            "card_device_age_days",
        ]
        return np.array([[features.get(f, 0.0) for f in feature_order]])
```

---

## ML Components Used

| Component | Technology | Role |
|-----------|-----------|------|
| Rule Engine | Redis SET (blocklist/allowlist) | Sub-1ms hard rules, deterministic |
| Feature Storage | Redis Cluster (sorted sets) | Rolling window spend aggregation |
| Stream Processing | Apache Flink | Real-time feature computation, exactly-once |
| Event Bus | Apache Kafka | Transaction event streaming |
| ML Model | XGBoost | Fraud probability scoring, 10-15ms |
| Imbalance Handling | SMOTE + RandomUnderSampler (imbalanced-learn) | Class imbalance 1:1000 |
| Explainability | SHAP TreeExplainer | Regulatory reason codes |
| Threshold Selection | F-beta optimization (precision-weighted) | Precision/recall tradeoff tuning |
| Experiment Tracking | MLflow | Model versioning, threshold tracking |

---

## Tradeoffs and Alternatives

| Decision | Chosen | Alternative | Reasoning |
|----------|--------|-------------|-----------|
| Model type | XGBoost | Neural network (MLP, Transformer) | XGBoost: interpretable SHAP values, trains in minutes, handles missing features |
| Imbalance strategy | SMOTE + scale_pos_weight | Pure scale_pos_weight | SMOTE creates synthetic minority samples in feature space; better calibration |
| Feature aggregation | Flink sliding windows | Lambda architecture (speed + batch) | Flink unified streaming eliminates dual-path complexity |
| Threshold | F-beta (beta=0.5) | Fixed 0.5 or ROC-optimal | F-beta explicitly encodes business cost of false positives vs false negatives |
| Review zone | [0.40, 0.85] | Binary approve/block | Borderline zone recovers precision without sacrificing recall |
| Graph features | Precomputed + cached | Real-time graph traversal | Real-time graph query >50ms; precompute nightly, cache in Redis |

---

## Interview Discussion Points

**How do you handle the class imbalance of 0.1% fraud rate?**
Three-layer approach: (1) scale_pos_weight=999 in XGBoost tells the model each fraud example counts as 999 legitimate ones during gradient computation. (2) SMOTE generates synthetic fraud examples by interpolating in feature space between existing fraud cases — avoids overfitting to exact training fraud examples. (3) Threshold tuning: the naive 0.5 threshold is wrong for imbalanced data; use F-beta with beta=0.5 on a time-held-out validation set (not random split — fraud patterns are temporal). Target threshold is typically 0.85 for auto-block.

**How do you prevent model degradation as fraud patterns evolve (concept drift)?**
Fraudsters adapt within days of a new model deploy. Mitigations: (1) Monitor PSI (Population Stability Index) on feature distributions — PSI > 0.2 triggers alert. (2) Monitor fraud rate on auto-approved transactions using delayed labels (chargebacks arrive 30-90 days later). (3) Hourly model retraining on a rolling 30-day window so fresh fraud patterns quickly dominate. (4) Maintain an "emergency rules" layer that analysts can update within minutes without model retraining.

**Why use F-beta with beta=0.5 for threshold selection rather than maximizing AUC?**
AUC measures overall ranking quality but does not account for the asymmetric cost of errors. Blocking a legitimate transaction (false positive) costs an estimated $50 in customer service, potential churn, and reputation. Missing fraud costs an average $200 in loss. But the false positive rate multiplier is 1000x the false negative rate (because 99.9% of transactions are legitimate). F-beta with beta=0.5 gives precision double the weight of recall, directly encoding this business asymmetry. AUC would be 0.98+ while the system produces unacceptable false positive rates.

**How do you ensure the fraud score is produced in <50ms given streaming feature computation?**
The critical insight is that streaming features must be precomputed, not computed on the critical path. Flink aggregates spend velocity continuously and writes results to Redis. At scoring time, the API does a Redis GET (sub-1ms), not a Flink computation. The 50ms budget is: rule engine 1ms + Redis feature fetch 5ms + model inference 15ms + network 10ms = 31ms, leaving 19ms buffer for tail latency. SHAP explanations (50-100ms) are computed asynchronously after the decision is returned.
