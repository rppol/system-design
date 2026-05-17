# Ensemble Methods

## Deep Dive Sub-Files

| File | Topic |
|------|-------|
| [random_forests.md](random_forests.md) | Bootstrap aggregation, OOB error, feature importance, proximity matrix |
| [gradient_boosting.md](gradient_boosting.md) | Additive models, residual fitting, shrinkage, stochastic GB |
| [xgboost_lightgbm.md](xgboost_lightgbm.md) | XGBoost regularization, LightGBM GOSS/EFB, CatBoost ordered boosting |
| [stacking_and_blending.md](stacking_and_blending.md) | Out-of-fold stacking, blending, meta-learners, Kaggle patterns |

---

## 1. Concept Overview

Ensemble methods combine multiple machine learning models to produce a single, more accurate predictor than any individual model. The core motivation comes from the bias-variance decomposition: individual models make errors from bias (wrong assumptions), variance (sensitivity to training data), and irreducible noise. Ensembles exploit the fact that combining diverse models reduces the overall expected error.

Three fundamental strategies dominate:

- **Bagging** (Bootstrap Aggregating): trains many models in parallel on random subsets of data, reduces variance
- **Boosting**: trains models sequentially, each correcting the errors of the previous, reduces bias
- **Stacking**: trains a meta-learner on the predictions of base models, captures complementary strengths

---

## 2. Intuition

One-line analogy: an ensemble is a panel of doctors — each with different training and specialties — who vote on a diagnosis; their collective judgment outperforms any single doctor.

Mental model: suppose you have 100 classifiers, each with 70% accuracy, but whose errors are independent. The probability that a majority vote is wrong is the probability that more than 50 of them err — by the binomial distribution this is roughly 0.006%. Independence of errors is the key assumption; diversity is how you achieve it in practice.

Why it matters: on structured/tabular data, gradient boosting ensembles (XGBoost, LightGBM) are the dominant approach in industry and competitions. Proper ensembling of 3-5 diverse models typically yields 0.5-2% AUC improvement over the best single model at the cost of training and serving complexity.

Key insight: **bias-variance tradeoff governs which ensemble strategy to choose**. High-variance models (deep trees, neural nets) benefit from bagging. High-bias models (stumps, linear models) benefit from boosting. Stacking is algorithm-agnostic and exploits model diversity regardless of bias/variance profile.

---

## 3. Core Principles

### Bias-Variance Decomposition

For a regression model, expected MSE decomposes as:

```
E[(y - f(x))^2] = Bias[f(x)]^2 + Var[f(x)] + σ^2(irreducible noise)
```

- **Bias**: error from wrong assumptions (underfitting)
- **Variance**: error from sensitivity to training set fluctuations (overfitting)
- Ensembles do not reduce irreducible noise

### How Each Method Addresses the Decomposition

```
Bagging:    Reduces Variance        | Does NOT reduce Bias
Boosting:   Reduces Bias            | Can increase Variance (overfitting)
Stacking:   Reduces Both (indirectly) via diversity
```

### Diversity Requirement

For ensemble improvements to materialise, base models must make **uncorrelated errors**. Diversity sources:

1. Different training subsets (bagging, boosting uses all data)
2. Different feature subsets (random forests)
3. Different algorithms (stacking: RF + GBT + SVM + neural net)
4. Different hyperparameter settings
5. Different random seeds

### Law of Condorcet (theoretical grounding)

If each voter (model) has accuracy > 0.5 and votes are independent, majority vote accuracy approaches 1.0 as the number of voters grows. Real models are not independent — diversity management is the engineering problem.

---

## 4. Types / Architectures / Strategies

### 4.1 Bagging Family

| Method | Key Mechanism | Typical Model |
|--------|--------------|---------------|
| Bagging | Bootstrap samples, average | Any model |
| Random Forest | Bootstrap + random feature subsets | Decision trees |
| Extra Trees | Random splits (extremely randomised) | Decision trees |
| Pasting | Subsampling without replacement | Any model |

### 4.2 Boosting Family

| Method | Update Mechanism | Loss Functions |
|--------|-----------------|----------------|
| AdaBoost | Re-weight misclassified samples | Exponential |
| Gradient Boosting (GBDT) | Fit negative gradient (pseudo-residuals) | Any differentiable |
| XGBoost | 2nd-order Taylor expansion, L1+L2 regularization | Any differentiable |
| LightGBM | GOSS sampling + EFB + leaf-wise growth | Any differentiable |
| CatBoost | Ordered boosting, native categoricals | Any differentiable |
| HistGradientBoosting | Histogram-based binning (sklearn) | Any differentiable |

### 4.3 Stacking / Blending

| Method | Data Split | Speed | Data Efficiency |
|--------|-----------|-------|-----------------|
| K-Fold Stacking | K-fold OOF | Slow (K×base) | High |
| Holdout Blending | Single holdout (20-30%) | Fast | Low |
| Multi-level Stacking | Nested folds | Very slow | Medium |

### 4.4 Voting

| Type | Mechanism | Use Case |
|------|-----------|----------|
| Hard Voting | Majority class label | When models are well-calibrated |
| Soft Voting | Average predicted probabilities | Better calibration, usually preferred |
| Weighted Voting | Weighted average of probabilities | When model quality varies significantly |

---

## 5. Architecture Diagrams

### Ensemble Taxonomy

```
                        ENSEMBLE METHODS
                               |
            +------------------+------------------+
            |                  |                  |
         BAGGING            BOOSTING           STACKING
            |                  |                  |
     +------+------+    +------+------+    +------+------+
     |             |    |             |    |             |
 Random        Extra   AdaBoost    GBDT   K-Fold     Blending
 Forest        Trees               |     Stacking
                             +-----+-----+
                             |           |
                          XGBoost    LightGBM
                                     CatBoost
```

### Bagging Architecture

```
Training Data D
      |
      +----------+----------+----------+
      |          |          |          |
  Bootstrap1  Bootstrap2  Bootstrap3  ...Bootstrap_B
      |          |          |          |
  Model_1     Model_2     Model_3    Model_B
      |          |          |          |
  Pred_1      Pred_2      Pred_3     Pred_B
      |          |          |          |
      +----------+----------+----------+
                        |
              Average (regression) /
              Majority vote (classification)
                        |
                 Final Prediction
```

### Boosting Architecture (Sequential)

```
Training Data D
      |
   Model_1  ---> Residuals_1
                     |
                  Model_2  ---> Residuals_2
                                   |
                                Model_3  ---> ...
                                               |
                                            Model_M

Final: F(x) = lr*h_1(x) + lr*h_2(x) + ... + lr*h_M(x)
       where lr = learning rate (shrinkage)
```

### K-Fold Stacking Architecture

```
Training Data (N rows)
      |
  Split into K folds
      |
  For each fold k:
    Train base models on K-1 folds
    Predict on fold k --> OOF predictions
      |
  OOF predictions (N rows) = new features
      |
  Train meta-learner on OOF predictions + original labels
      |
  Test Data:
    Base models (trained on all train) --> Test predictions (averaged)
    Meta-learner(test predictions) --> Final predictions
```

### Training Time Comparison (100K rows, 100 features, binary classification)

```
Algorithm              CPU Time    GPU Time    Memory
----------------------------------------------------
Random Forest (500T)   ~15s        ~5s         ~2GB
XGBoost (500 rounds)   ~10s        ~0.3s       ~0.5GB
LightGBM (500 rounds)  ~3s         ~0.3s       ~0.3GB
CatBoost (500 iter)    ~8s         ~0.5s       ~0.5GB
sklearn GBDT           ~120s       N/A         ~0.3GB
```

---

## 6. How It Works — Detailed Mechanics

### Bias-Variance of Averaging (Bagging)

If base models each have variance σ^2 and pairwise correlation ρ:

```
Variance(average) = ρ*σ^2 + (1-ρ)*σ^2/B
```

As B → ∞, variance approaches ρ*σ^2. Reducing correlation (diversity) reduces the floor; more trees reduce variance toward that floor.

### Quick comparison with sklearn

```python
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.datasets import make_classification
from sklearn.ensemble import (
    RandomForestClassifier,
    GradientBoostingClassifier,
    VotingClassifier,
    StackingClassifier,
)
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import cross_val_score, StratifiedKFold
from sklearn.metrics import roc_auc_score
import xgboost as xgb
import lightgbm as lgb

X, y = make_classification(
    n_samples=50_000,
    n_features=30,
    n_informative=20,
    n_redundant=5,
    random_state=42,
)

cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)

models: dict[str, object] = {
    "Random Forest": RandomForestClassifier(
        n_estimators=300,     # default 100 is too low for production
        max_features="sqrt",
        n_jobs=-1,
        random_state=42,
    ),
    "XGBoost": xgb.XGBClassifier(
        n_estimators=500,
        learning_rate=0.05,
        max_depth=6,
        subsample=0.8,
        colsample_bytree=0.8,
        use_label_encoder=False,
        eval_metric="logloss",
        random_state=42,
        n_jobs=-1,
    ),
    "LightGBM": lgb.LGBMClassifier(
        n_estimators=500,
        learning_rate=0.05,
        num_leaves=63,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42,
        n_jobs=-1,
        verbose=-1,
    ),
}

for name, model in models.items():
    scores = cross_val_score(model, X, y, cv=cv, scoring="roc_auc", n_jobs=-1)
    print(f"{name:20s}  AUC={scores.mean():.4f} ± {scores.std():.4f}")
```

### Soft Voting Ensemble

```python
from sklearn.calibration import CalibratedClassifierCV

# Voting combines diverse models via probability averaging
voting_clf = VotingClassifier(
    estimators=[
        ("rf", RandomForestClassifier(n_estimators=300, n_jobs=-1, random_state=42)),
        ("xgb", xgb.XGBClassifier(n_estimators=300, learning_rate=0.05, random_state=42)),
        ("lgb", lgb.LGBMClassifier(n_estimators=300, learning_rate=0.05, random_state=42, verbose=-1)),
    ],
    voting="soft",       # use predicted probabilities, not hard votes
    n_jobs=-1,
)

scores = cross_val_score(voting_clf, X, y, cv=cv, scoring="roc_auc")
print(f"Soft Voting  AUC={scores.mean():.4f}")
# Typical: 0.5-1.5% AUC improvement over best single model
```

---

## 7. Real-World Examples

### Kaggle Competitions

The winning pattern for structured/tabular competitions since 2014:

1. Feature engineering (domain-specific transforms, aggregations)
2. Train 3-5 diverse base models: RF, XGBoost, LightGBM, CatBoost, neural network
3. Stack with 5-fold OOF predictions as meta-features
4. Meta-learner: LogisticRegression or simple LightGBM with low n_estimators

The 2015 Higgs Boson competition winner used an ensemble of 70+ models with multi-level stacking, gaining 3%+ AUC over their best single model.

### Credit Scoring (Production)

- Base models: LightGBM (primary), Logistic Regression (interpretable), small neural net (embeddings for categoricals)
- Ensemble type: soft voting with learned weights optimized on a holdout
- Monitoring: each base model's AUC tracked separately; model is alerted if any base model degrades >0.5% AUC
- Serving: predictions from all models run in parallel; ~20ms p99 latency on CPU

### Fraud Detection

- Random Forest for speed and OOB-based anomaly scores (proximity matrix)
- XGBoost for high-precision classification
- Hard rule engine (velocity checks) as a fourth "model"
- Ensemble vote: fraud if RF > 0.6 AND XGBoost > 0.7, OR rule fires

---

## 8. Tradeoffs

### AUC Improvement from Ensembling (typical tabular data)

| Strategy | AUC Gain over Best Single Model | Training Cost |
|----------|--------------------------------|---------------|
| Bagging (RF 300 trees) | Baseline | 1x |
| XGBoost/LightGBM | +0.5-1.5% | 0.5-1x |
| Soft Voting (RF+XGB+LGB) | +0.5-1.0% | 3x |
| 5-Fold Stacking (3 base models) | +0.5-2.0% | 15x |
| 3-Level Stacking | +0.1-0.5% marginal | 50x+ |

Diminishing returns are steep beyond 3-5 base models and 2 stacking levels.

### Serving Complexity

| Ensemble Type | Serving Latency | Model Count | Maintenance |
|---------------|----------------|-------------|-------------|
| Single model | 1x | 1 | Low |
| Voting (3 models) | ~3x | 3 | Medium |
| Stacking | ~3x + meta | 4+ | High |
| Multi-level stacking | ~5x+ | 7+ | Very High |

### Interpretability

```
Most interpretable         Least interpretable
|                                             |
Logistic  Decision  Random  GBM   Stacking   |
Regression  Tree    Forest                   |
```

SHAP values work on tree ensembles (RF, XGBoost, LightGBM) efficiently via TreeSHAP (O(TLD^2) where T=trees, L=leaves, D=depth). Stacking adds another layer of opacity.

---

## 9. When to Use / When NOT to Use

### When to Use

- **Random Forest**: tabular data, fast baseline, need OOB error estimate, outlier detection via proximity matrix, features have high cardinality
- **XGBoost/LightGBM**: tabular competitions and production, imbalanced classes (scale_pos_weight), missing values natively handled
- **Stacking**: you have compute budget, diverse base models, competition setting, AUC gain of 0.5%+ justified by business value
- **Voting**: simple production setup, interpretability matters per-model, serving latency budget is tight (parallel scoring)

### When NOT to Use

- **Small data (< 1K samples)**: ensembles overfit; use cross-validated single model with regularisation
- **Image/text with deep learning**: a single large neural net typically beats tree ensembles on these modalities
- **Real-time latency < 5ms**: serving 3-5 large tree models is expensive; single quantised model preferred
- **Regulatory interpretability required**: single LogisticRegression or single decision tree with depth limit
- **Concept drift environment**: more models = more monitoring surface; a single well-monitored model is easier to retrain

---

## 10. Common Pitfalls

### Pitfall 1: n_estimators Too Low

Default `n_estimators=100` for Random Forest is frequently insufficient. At 100 trees the OOB error has not stabilised; typical production uses 300-500.

```python
# BROKEN: underfit, OOB unstable
rf = RandomForestClassifier(n_estimators=100)

# FIXED
rf = RandomForestClassifier(n_estimators=500, n_jobs=-1, oob_score=True)
print(f"OOB AUC: {rf.oob_score_:.4f}")
# OOB error is approximately equal to 3-fold CV error
```

### Pitfall 2: Data Leakage in Stacking

The most common and damaging mistake: training base models on all training data, then using their predictions as features to train the meta-learner on the same data. The meta-learner sees "perfect" predictions from overfit base models and learns garbage.

```python
# BROKEN: data leakage
rf.fit(X_train, y_train)
xgb_model.fit(X_train, y_train)
# Using train predictions to train meta-learner -- LEAKAGE
meta_features_train = np.column_stack([
    rf.predict_proba(X_train)[:, 1],
    xgb_model.predict_proba(X_train)[:, 1],
])
meta_learner.fit(meta_features_train, y_train)

# FIXED: out-of-fold predictions
from sklearn.model_selection import cross_val_predict
oof_rf = cross_val_predict(rf, X_train, y_train, cv=5, method="predict_proba")[:, 1]
oof_xgb = cross_val_predict(xgb_model, X_train, y_train, cv=5, method="predict_proba")[:, 1]
meta_features_train = np.column_stack([oof_rf, oof_xgb])
meta_learner.fit(meta_features_train, y_train)
```

### Pitfall 3: Correlated Base Models

Using five XGBoost models with slightly different hyperparameters produces highly correlated errors — you get the cost of ensembling with almost none of the variance reduction benefit.

### Pitfall 4: Ensemble Without Early Stopping

XGBoost/LightGBM with 1000 rounds and no early stopping will overfit, then the ensemble amplifies the overfit pattern.

### Pitfall 5: Not Monitoring Individual Base Models

In production, base model drift can be masked by the ensemble's average. Track each base model's AUC and alert on per-model degradation, not just ensemble AUC.

---

## 11. Technologies & Tools

| Tool | Version | Role |
|------|---------|------|
| scikit-learn | 1.4+ | RandomForest, ExtraTrees, VotingClassifier, StackingClassifier, GradientBoostingClassifier |
| XGBoost | 2.0+ | Gradient boosting, GPU support, DART, monotone constraints |
| LightGBM | 4.0+ | Fast GBDT, GOSS, EFB, categorical support |
| CatBoost | 1.2+ | Ordered boosting, native categoricals, symmetric trees |
| SHAP | 0.44+ | TreeSHAP for feature importance explanation across all tree ensembles |
| Optuna | 3.0+ | Hyperparameter optimisation for ensemble members |
| MLflow | 2.0+ | Tracking ensemble experiments, registering models |
| BentoML / Seldon | latest | Serving multiple models with ensemble logic |

---

## 12. Interview Questions with Answers

**Q: What is the bias-variance tradeoff and how do bagging and boosting each address it?**
Expected MSE decomposes into bias^2 + variance + irreducible noise. Bagging averages many high-variance, low-bias models (deep trees), which reduces variance proportional to (1-ρ)/B where ρ is pairwise correlation and B is the number of models; bias is unchanged. Boosting trains shallow (high-bias, low-variance) models sequentially, each correcting the previous model's errors, which reduces bias; it can increase variance if run too long (overfitting). Practical guidance: when your model underfits, try boosting; when it overfits, try bagging.

**Q: Why does a random forest use sqrt(n_features) for classification but n_features/3 for regression?**
Both choices de-correlate the trees by limiting which features each tree can split on at any node, which is the core mechanism for variance reduction. Classification uses sqrt because labels are categorical — each tree's optimal split set is smaller relative to the feature space. Regression uses n_features/3 empirically because continuous targets benefit from slightly more features to find good numeric splits. These are defaults; both should be tuned via OOB error or cross-validation for your specific dataset.

**Q: What is OOB (Out-Of-Bag) error and why is it approximately equivalent to 3-fold CV?**
Each bootstrap sample leaves out approximately 36.8% of training examples (probability of not being selected in N draws with replacement = (1-1/N)^N → 1/e ≈ 0.368). OOB error is computed by predicting each training sample only with trees that did not see it. This naturally provides an unbiased estimate of generalisation error. It approximates 3-fold CV because ~37% holdout is close to the 33% holdout of 3-fold; the estimate is slightly noisier because OOB sets overlap, but it is computed for free with no additional training.

**Q: What is the difference between feature importance via Mean Decrease in Impurity (MDI) and permutation importance?**
MDI (sklearn's default) sums the weighted impurity decrease from splits on each feature across all trees. It is computed during training and is fast, but it is biased toward high-cardinality features (many unique values create many split opportunities) and continuous features. Permutation importance shuffles each feature's values in the test set and measures the resulting AUC drop; it is unbiased, works on any metric, but requires a separate evaluation pass per feature and is sensitive to correlated features. For production feature selection, always use permutation importance on a held-out set.

**Q: How does gradient boosting fit residuals, and why does it fit the negative gradient rather than raw residuals?**
Gradient boosting is a functional gradient descent algorithm in function space. At each step we want to move F(x) in the direction that reduces the loss most: this direction is the negative gradient of the loss with respect to the current predictions. For squared error loss, the negative gradient equals the raw residuals (y - F(x)), so the two are identical. For other losses (log loss, MAE, Huber), the negative gradient differs from raw residuals and is the correct quantity to fit. Framing boosting as gradient descent lets you plug in any differentiable loss function, which is why XGBoost and LightGBM support ranking losses, custom losses, etc.

**Q: What is shrinkage (learning rate) in gradient boosting and what tradeoff does it introduce?**
Shrinkage scales each tree's contribution by a factor η (typically 0.01–0.3): F_m(x) = F_{m-1}(x) + η * h_m(x). A small learning rate means each tree makes a tiny correction, requiring more trees for the same training loss but achieving better generalisation because no single tree dominates. The tradeoff is training time: η=0.01 with 5000 trees trains 50× slower than η=0.5 with 100 trees. In production use η=0.05 with early stopping rather than fixing both η and n_estimators; let early stopping find the right depth.

**Q: What innovations does XGBoost introduce over vanilla gradient boosting?**
Four key innovations: (1) Regularised objective — adds L1 (alpha) and L2 (lambda) penalties on leaf weights in the objective, reducing overfitting; (2) Second-order Taylor approximation — uses both first and second derivatives of the loss, enabling more accurate split scoring for arbitrary loss functions; (3) Approximate split finding with column blocks — pre-sorts features into compressed column blocks for cache-efficient parallel split evaluation; (4) Sparsity-aware split — handles missing values natively by learning a default direction for missing values at each split. Together these make XGBoost regularised by construction, faster to train, and robust to sparse/missing data.

**Q: What is GOSS in LightGBM and why does it make LightGBM 3-5x faster than XGBoost?**
GOSS (Gradient-based One-Side Sampling) observes that samples with large gradients contribute more to information gain and should always be kept; samples with small gradients contribute little and can be subsampled. LightGBM keeps all large-gradient samples and randomly samples a fraction (default 10%) of small-gradient samples, introducing a correction factor to maintain gradient statistics. Combined with Exclusive Feature Bundling (EFB, which bundles mutually exclusive sparse features into fewer dense features) and histogram-based binning (discretising continuous features into 255 bins), LightGBM reduces both the number of samples considered per split and the number of feature evaluations. On 100K row datasets LightGBM typically trains in ~3s vs XGBoost's ~10s on CPU.

**Q: Why is data leakage the most critical pitfall in stacking, and how do out-of-fold predictions fix it?**
If base models are trained on all training data and then used to predict on that same training data to create meta-features, the meta-learner sees "memorised" predictions — base models have zero bias on their own training data for high-capacity models. The meta-learner learns from signals that do not exist at test time, leading to severe overfit. Out-of-fold (OOF) prediction fixes this by ensuring that for every training sample, the prediction used as a meta-feature comes from a base model that never saw that sample during training, giving honest, generalisation-quality predictions for the meta-learner to learn from.

**Q: When does soft voting outperform hard voting?**
Soft voting averages predicted class probabilities then takes the argmax; hard voting takes the majority class. Soft voting outperforms hard voting when models produce well-calibrated probabilities that convey confidence. If model A predicts class 1 with 0.99 probability and models B and C predict class 0 with 0.51 probability each, hard voting picks class 0 (2 vs 1) but soft voting correctly picks class 1 (0.99 > 0.51+0.49/2). Soft voting fails when models are poorly calibrated (e.g., naive Bayes with extreme probability estimates); in that case calibration (Platt scaling, isotonic regression) should precede soft voting.

**Q: What are the diminishing returns of adding more models to an ensemble?**
The variance reduction from averaging B models is σ^2*(ρ + (1-ρ)/B). Adding models reduces the second term but the floor ρ*σ^2 (from correlated errors) is not reducible. In practice: going from 1 to 3 diverse models yields most of the gain; 3 to 5 models gives modest additional gain; beyond 5 models with similar algorithms, improvement is marginal (typically < 0.1% AUC) while serving cost and maintenance complexity scale linearly. For stacking, the meta-learner's gains also plateau — a second stacking level typically adds 0.1-0.5% over the first; a third level is almost never worth it in production.

---

## 13. Best Practices

1. Always start with a strong single model (LightGBM with tuned hyperparameters) before building an ensemble — ensembling a weak baseline is wasteful.
2. Use OOB error for Random Forest validation instead of a separate hold-out when data is small.
3. Always use early stopping with XGBoost and LightGBM; never fix n_estimators without it.
4. For stacking, use LogisticRegression or Ridge as the meta-learner first — complex meta-learners overfit the meta-feature space.
5. Ensure base models use different algorithms, not just different hyperparameters, to maximise error diversity.
6. Track each base model's performance independently in production alongside the ensemble.
7. Use SHAP TreeSHAP for feature importance — it is model-agnostic across tree ensembles and consistent.
8. Calibrate probabilities before soft voting if models come from different families (XGBoost + SVM + naive Bayes).
9. Set n_estimators >= 300 for Random Forest in production; 100 is a documentation default, not a production recommendation.
10. In competitions, generate OOF predictions early (after your first solid base model) so you accumulate meta-features throughout the competition.

---

## 14. Case Study

### Problem: E-Commerce Churn Prediction at Scale

**Context**: 10 million customers, monthly churn prediction, model updated weekly, engineering team has 2 ML engineers, SLA: predictions in under 100ms per batch (100K rows).

**Baseline**: Single LightGBM with default hyperparameters, AUC 0.831.

**Ensemble strategy**:

1. Base models trained on 80% of data (stratified):
   - LightGBM (num_leaves=127, learning_rate=0.03, 800 rounds with early stopping): AUC 0.849
   - XGBoost (max_depth=7, learning_rate=0.03, 600 rounds with early stopping): AUC 0.844
   - Random Forest (n_estimators=500, max_features=0.4): AUC 0.832
   - CatBoost (depth=8, learning_rate=0.05, 600 iter): AUC 0.841

2. 5-fold OOF predictions generated for meta-features.

3. Meta-learner: LogisticRegression (C=0.1, l2 penalty) on OOF features + 5 domain features (tenure bucket, product tier, support_tickets_90d).

4. Final ensemble AUC on holdout: **0.861** — 1.2% gain over best single model.

**Serving architecture**:
- All four base models serialised with pickle/joblib, loaded once at startup
- Batch scoring: 100K rows in ~4s on 8-core CPU (LightGBM: 1s, XGBoost: 1.5s, RF: 1.2s, CatBoost: 0.8s, meta: 0.1s)
- Per-batch p99 latency well within 100ms SLA when batching 1K rows
- Monitoring: per-model PSI (Population Stability Index) tracked weekly; alert if any base model PSI > 0.2

**Business outcome**: 1.2% AUC improvement translated to ~8% better targeting in the top decile, resulting in €2.1M additional annual retention campaign ROI at an implementation cost of 3 engineer-weeks.
