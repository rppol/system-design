# Model Evaluation and Selection

## 1. Concept Overview

Model evaluation measures how well a trained model generalizes to unseen data. Model selection chooses among competing algorithms and hyperparameter configurations. Together they answer: "is this model good enough to deploy, and which version should we ship?"

Core topics:
- **Cross-validation**: estimate generalization error without using the test set; k-fold, stratified, time series, and group variants
- **Classification metrics**: accuracy, precision, recall, F1, AUC-ROC, AUC-PR — each optimizes for a different error cost
- **Regression metrics**: MAE, MSE, RMSE, MAPE, R^2 — sensitivity to outliers and interpretability vary
- **Calibration**: how well predicted probabilities reflect true likelihoods; critical for decision-making
- **Bias-variance tradeoff**: diagnosing underfitting vs overfitting
- **Hyperparameter tuning**: GridSearchCV, RandomizedSearchCV, Bayesian optimization (Optuna)
- **Statistical model comparison**: determining whether one model is significantly better than another

---

## 2. Intuition

One-line analogy: evaluating a model on training data is like grading yourself on a test you wrote — you already know the answers. Cross-validation is like taking five different exams from the same syllabus, written by others.

Mental model:
- CV score = estimate of what you will see in production (with uncertainty)
- AUC-ROC = "how well does the model rank positives above negatives?"
- Calibration = "when the model says 80% probability, does it happen 80% of the time?"
- Hyperparameter tuning = searching for the best regularization dial setting

Why it matters: a model evaluated carelessly produces inflated metrics that collapse in production. Metric choice shapes what behavior gets optimized — precision-focused model behavior differs dramatically from recall-focused, even with identical AUC.

Key insight: the test set must remain completely unseen until final evaluation. Any decision that uses the test set — including metric-based model selection — converts it into a validation set and requires a separate held-out set for unbiased final reporting.

---

## 3. Core Principles

1. **Train / validation / test split hierarchy**: train for learning, validation (CV) for model selection, test for final unbiased estimate. Never loop back from test to training decisions.
2. **Stratify for imbalanced classes**: random k-fold on an imbalanced dataset may produce folds with no positive examples. `StratifiedKFold` ensures each fold has the same class distribution as the original.
3. **Temporal data requires temporal splits**: random splits on time-series data allow future information to leak into training (a row from day 100 trains on features from day 200). Use `TimeSeriesSplit`.
4. **Metric must match the business objective**: a spam filter prioritizes recall (catch all spam) over precision (occasional false positives acceptable). A fraud block system may prioritize precision (blocking a legitimate transaction is costly).
5. **Probability calibration is separate from discrimination**: a model can rank examples perfectly (AUC-ROC = 0.95) but output poorly calibrated probabilities (model says 90% for events that actually happen 60% of the time). Both matter for decision thresholds.

---

## 4. Types / Architectures / Strategies

### Cross-Validation Variants

| Variant              | Use when                                        | Leakage risk |
|----------------------|-------------------------------------------------|--------------|
| K-Fold               | IID data, balanced classes                      | None         |
| Stratified K-Fold    | Imbalanced classes, classification              | None         |
| TimeSeriesSplit      | Temporal data (financial, sensor, clickstream)  | None         |
| Group K-Fold         | Groups must not span folds (patients, users)    | None         |
| Leave-One-Out (LOO)  | Very small n (< 100), expensive to compute      | None         |
| Purged K-Fold        | Finance: embargo period between train and val   | None         |
| Repeated K-Fold      | Reduce variance in CV estimate                  | None         |

### Classification Metrics

| Metric     | Formula                           | Best for                             |
|------------|-----------------------------------|--------------------------------------|
| Accuracy   | (TP+TN) / N                       | Balanced classes only                |
| Precision  | TP / (TP + FP)                    | Minimize false positives             |
| Recall     | TP / (TP + FN)                    | Minimize false negatives             |
| F1         | 2 * P * R / (P + R)               | Balance precision-recall             |
| F-beta     | (1+b^2) * P * R / (b^2*P + R)    | Weight recall b times over precision |
| AUC-ROC    | P(score(pos) > score(neg))        | Balanced to moderately imbalanced    |
| AUC-PR     | Area under precision-recall curve | Highly imbalanced (< 5% positives)   |
| MCC        | Matthews Correlation Coefficient  | Imbalanced, all 4 cells of CM matter |

### Regression Metrics

| Metric | Formula                    | Outlier sensitivity | Interpretable |
|--------|----------------------------|---------------------|---------------|
| MAE    | mean(|y - y_hat|)          | Low                 | Yes (same units as y) |
| MSE    | mean((y - y_hat)^2)        | High                | No (squared units) |
| RMSE   | sqrt(MSE)                  | High                | Yes (same units as y) |
| MAPE   | mean(|y - y_hat| / |y|)*100| Low                 | Yes (percentage) |
| R^2    | 1 - SS_res / SS_tot        | Moderate            | Relative (0–1 for well-fit) |

### Hyperparameter Tuning

| Method             | Strategy                            | When to use                        |
|--------------------|-------------------------------------|------------------------------------|
| GridSearchCV       | Exhaustive grid search              | Small param grid (< 100 combos)    |
| RandomizedSearchCV | Random sampling from distributions  | Larger grids; 60-80% of grid perf  |
| Optuna (TPE)       | Bayesian / Tree-structured Parzen   | Large grids, expensive models      |
| Hyperopt           | Bayesian (TPE, ATPE)                | Similar to Optuna                  |
| BOHB               | Bandit + Bayesian                   | Very expensive models (neural)     |

---

## 5. Architecture Diagrams

### Train / Validation / Test Hierarchy

```
Full labeled dataset
        |
        +-----------------------------+
        |                             |
  80% Training pool             20% Test set  <-- LOCKED. Touch once at the very end.
        |
  Cross-validation (5-fold):
  +------+------+------+------+------+
  |  T   |  T   |  T   |  T   |  V  |   fold 1: train on 1-4, validate on 5
  |  T   |  T   |  T   |  V   |  T  |   fold 2: train on 1-3, 5, validate on 4
  |  T   |  T   |  V   |  T   |  T  |   fold 3: ...
  |  T   |  V   |  T   |  T   |  T  |   fold 4: ...
  |  V   |  T   |  T   |  T   |  T  |   fold 5: ...
  +------+------+------+------+------+
        |
  mean CV score = model selection criterion
        |
  Retrain best model on full 80%
        |
  Evaluate once on 20% test set --> final reported metric
```

### TimeSeriesSplit (No Future Leakage)

```
Time:  t1 --- t2 --- t3 --- t4 --- t5 --- t6 --- t7 --- t8

Split 1:  [Train: t1-t4]           [Val: t5-t6]
Split 2:  [Train: t1-t6]           [Val: t7]
Split 3:  [Train: t1-t7]           [Val: t8]

NEVER: [Train: t1, t3, t5, t7]  [Val: t2, t4, t6, t8]  <-- future data in train
```

### Reliability Diagram (Calibration)

```
Predicted probability (x-axis)
1.0 |                              /  <- perfect calibration (diagonal)
    |                          /
0.8 |                      / *        * = well-calibrated model
    |                  /     *
0.6 |              /
    |          /  *  *                * = overconfident model (sigmoid shape)
0.4 |      /    *
    |  /  *
0.2 |/  *
    +--+--+--+--+--+--+--+--+--+---> Fraction of positives (y-axis)
   0.0 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0
```

---

## 6. How It Works — Detailed Mechanics

```python
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.calibration import CalibratedClassifierCV, calibration_curve
from sklearn.datasets import make_classification
from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    brier_score_loss,
    classification_report,
    f1_score,
    make_scorer,
    matthews_corrcoef,
    mean_absolute_error,
    mean_squared_error,
    precision_recall_curve,
    r2_score,
    roc_auc_score,
)
from sklearn.model_selection import (
    GridSearchCV,
    GroupKFold,
    RandomizedSearchCV,
    RepeatedStratifiedKFold,
    StratifiedKFold,
    TimeSeriesSplit,
    cross_val_score,
    cross_validate,
)
from scipy import stats
from typing import Any


# ── Cross-validation ──────────────────────────────────────────────────────────

def evaluate_model_cv(
    model: Any,
    X: np.ndarray,
    y: np.ndarray,
    cv_strategy: str = "stratified",
    n_splits: int = 5,
    scoring: str = "roc_auc",
) -> dict[str, float]:
    """
    Returns mean and std of CV scores.
    cv_strategy:
      "stratified" -- StratifiedKFold (default for classification)
      "timeseries" -- TimeSeriesSplit (temporal data)
      "repeated"   -- RepeatedStratifiedKFold (lower variance estimate)
    """
    if cv_strategy == "stratified":
        cv = StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=42)
    elif cv_strategy == "timeseries":
        cv = TimeSeriesSplit(n_splits=n_splits)
    elif cv_strategy == "repeated":
        cv = RepeatedStratifiedKFold(n_splits=n_splits, n_repeats=3, random_state=42)
    else:
        raise ValueError(f"Unknown cv_strategy: {cv_strategy}")

    scores = cross_val_score(model, X, y, cv=cv, scoring=scoring, n_jobs=-1)
    result = {"mean": float(scores.mean()), "std": float(scores.std())}
    print(f"CV {scoring}: {result['mean']:.4f} +/- {result['std']:.4f}")
    return result


# ── Classification metrics ────────────────────────────────────────────────────

def evaluate_classifier(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    y_prob: np.ndarray | None = None,
) -> dict[str, float]:
    """
    Comprehensive classification evaluation.
    y_prob: predicted probabilities for positive class (needed for AUC metrics).
    """
    metrics: dict[str, float] = {}

    # Threshold-dependent metrics
    metrics["f1"] = f1_score(y_true, y_pred, average="binary")
    metrics["mcc"] = matthews_corrcoef(y_true, y_pred)
    print(classification_report(y_true, y_pred))

    if y_prob is not None:
        # AUC-ROC: probability that model ranks a random positive above a random negative
        # Invariant to class threshold; baseline random = 0.5
        metrics["auc_roc"] = roc_auc_score(y_true, y_prob)

        # AUC-PR: better for imbalanced; area under precision-recall curve
        # Baseline = prevalence (e.g., 0.05 for 5% positive rate)
        metrics["auc_pr"] = average_precision_score(y_true, y_prob)

        # Brier score: mean squared error of probability predictions; 0 = perfect; 1 = worst
        metrics["brier_score"] = brier_score_loss(y_true, y_prob)

        print(f"AUC-ROC:     {metrics['auc_roc']:.4f}  (random baseline: 0.5)")
        print(f"AUC-PR:      {metrics['auc_pr']:.4f}  (random baseline: {y_true.mean():.3f})")
        print(f"Brier score: {metrics['brier_score']:.4f}  (perfect: 0.0)")

    return metrics


# ── Imbalanced class: prefer AUC-PR over AUC-ROC ─────────────────────────────

def demonstrate_auc_pr_importance(
    y_true: np.ndarray,
    y_prob_good: np.ndarray,
    y_prob_bad: np.ndarray,
) -> None:
    """
    On heavily imbalanced data (1% positives), AUC-ROC can be misleadingly high
    for a model that does poorly on the minority class.
    AUC-PR directly measures minority class retrieval quality.
    """
    print("Model A (good at minority):")
    print(f"  AUC-ROC: {roc_auc_score(y_true, y_prob_good):.4f}")
    print(f"  AUC-PR:  {average_precision_score(y_true, y_prob_good):.4f}")

    print("Model B (poor at minority):")
    print(f"  AUC-ROC: {roc_auc_score(y_true, y_prob_bad):.4f}")
    print(f"  AUC-PR:  {average_precision_score(y_true, y_prob_bad):.4f}")
    # Observation: AUC-ROC gap may be small (e.g., 0.89 vs 0.85)
    # AUC-PR gap will be large (e.g., 0.72 vs 0.31) — AUC-PR is more diagnostic


# ── Regression metrics ────────────────────────────────────────────────────────

def evaluate_regressor(
    y_true: np.ndarray,
    y_pred: np.ndarray,
) -> dict[str, float]:
    mae = mean_absolute_error(y_true, y_pred)
    mse = mean_squared_error(y_true, y_pred)
    rmse = float(np.sqrt(mse))
    r2 = r2_score(y_true, y_pred)
    # MAPE: undefined for y_true == 0; guard with mask
    nonzero_mask = y_true != 0
    mape = float(np.mean(np.abs((y_true[nonzero_mask] - y_pred[nonzero_mask]) /
                                 y_true[nonzero_mask])) * 100)
    print(f"MAE:  {mae:.4f}  (same units as y, outlier-robust)")
    print(f"RMSE: {rmse:.4f}  (penalizes large errors, same units as y)")
    print(f"MAPE: {mape:.2f}%  (percentage error, undefined when y=0)")
    print(f"R^2:  {r2:.4f}  (fraction of variance explained; 1.0 = perfect)")
    return {"mae": mae, "rmse": rmse, "mape": mape, "r2": r2}


# ── Probability calibration ───────────────────────────────────────────────────

def calibrate_model(
    model: Any,
    X_train: np.ndarray,
    y_train: np.ndarray,
    X_val: np.ndarray,
    y_val: np.ndarray,
    method: str = "isotonic",
) -> CalibratedClassifierCV:
    """
    Calibration corrects probability outputs so that predicted P(y=1) = 0.7
    corresponds to 70% actual positive rate.

    method="sigmoid"  (Platt scaling): few calibration samples (< 1,000), fast.
    method="isotonic": more samples (> 1,000), flexible non-monotonic correction.

    Brier score: mean squared error of probabilities. Lower = better calibrated.
    Expected calibration error (ECE): average abs difference between confidence
      and accuracy across probability bins.
    """
    calibrated = CalibratedClassifierCV(model, method=method, cv="prefit")
    calibrated.fit(X_val, y_val)  # fit calibrator on held-out validation set

    y_prob_raw = model.predict_proba(X_val)[:, 1]
    y_prob_cal = calibrated.predict_proba(X_val)[:, 1]

    print(f"Brier score before calibration: {brier_score_loss(y_val, y_prob_raw):.4f}")
    print(f"Brier score after  calibration: {brier_score_loss(y_val, y_prob_cal):.4f}")

    # Reliability curve: compare fraction of positives vs predicted probability
    frac_pos_raw, mean_pred_raw = calibration_curve(y_val, y_prob_raw, n_bins=10)
    frac_pos_cal, mean_pred_cal = calibration_curve(y_val, y_prob_cal, n_bins=10)
    print("\nCalibration curve (before | after):")
    for mp, fp, mp2, fp2 in zip(mean_pred_raw, frac_pos_raw, mean_pred_cal, frac_pos_cal):
        print(f"  pred={mp:.2f} actual={fp:.2f}  |  pred={mp2:.2f} actual={fp2:.2f}")

    return calibrated


# ── Hyperparameter tuning with Optuna ─────────────────────────────────────────

def tune_with_optuna(
    X_train: np.ndarray,
    y_train: np.ndarray,
    n_trials: int = 100,
) -> dict[str, Any]:
    """
    Optuna: Bayesian optimization using TPE (Tree-structured Parzen Estimator).
    TPE models p(x|y) separately for good and bad trials, sampling from the
    ratio to guide the next suggestion.

    MedianPruner: stops unpromising trials early if intermediate value
      is below the median of completed trials at that step (similar to early stopping).
    """
    try:
        import optuna
        optuna.logging.set_verbosity(optuna.logging.WARNING)

        def objective(trial: "optuna.Trial") -> float:
            params = {
                "n_estimators": trial.suggest_int("n_estimators", 100, 1000, step=100),
                "max_depth": trial.suggest_int("max_depth", 3, 10),
                "learning_rate": trial.suggest_float("learning_rate", 1e-3, 0.3, log=True),
                "min_samples_leaf": trial.suggest_int("min_samples_leaf", 5, 100),
                "subsample": trial.suggest_float("subsample", 0.5, 1.0),
            }
            model = GradientBoostingClassifier(**params, random_state=42)
            cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
            scores = cross_val_score(model, X_train, y_train, cv=cv, scoring="roc_auc", n_jobs=-1)
            return float(scores.mean())

        sampler = optuna.samplers.TPESampler(seed=42)
        pruner = optuna.pruners.MedianPruner(n_startup_trials=10, n_warmup_steps=5)
        study = optuna.create_study(
            direction="maximize", sampler=sampler, pruner=pruner
        )
        study.optimize(objective, n_trials=n_trials, n_jobs=1)

        print(f"Best AUC-ROC: {study.best_value:.4f}")
        print(f"Best params: {study.best_params}")
        return study.best_params

    except ImportError:
        raise ImportError("Install optuna: pip install optuna")


# ── Statistical model comparison ──────────────────────────────────────────────

def compare_models_ttest(
    model_a: Any,
    model_b: Any,
    X: np.ndarray,
    y: np.ndarray,
    n_splits: int = 10,
    scoring: str = "roc_auc",
    alpha: float = 0.05,
) -> None:
    """
    Paired t-test on CV scores.
    Each fold gives one score for model A and one for model B on the same data.
    Paired test accounts for fold-level correlation.
    Limitation: CV scores are not truly independent (overlapping training sets),
      so p-values are optimistic — treat as directional evidence, not definitive proof.
    """
    cv = StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=42)
    scores_a = cross_val_score(model_a, X, y, cv=cv, scoring=scoring, n_jobs=-1)
    scores_b = cross_val_score(model_b, X, y, cv=cv, scoring=scoring, n_jobs=-1)

    t_stat, p_value = stats.ttest_rel(scores_a, scores_b)
    print(f"Model A: {scores_a.mean():.4f} +/- {scores_a.std():.4f}")
    print(f"Model B: {scores_b.mean():.4f} +/- {scores_b.std():.4f}")
    print(f"Paired t-test: t={t_stat:.4f}, p={p_value:.4f}")
    if p_value < alpha:
        better = "A" if scores_a.mean() > scores_b.mean() else "B"
        print(f"Model {better} is significantly better at alpha={alpha}")
    else:
        print(f"No significant difference detected at alpha={alpha}")


def compare_models_mcnemar(
    y_true: np.ndarray,
    pred_a: np.ndarray,
    pred_b: np.ndarray,
) -> None:
    """
    McNemar's test: compares two classifiers on the same test set.
    Tests whether the off-diagonal counts (A correct + B wrong, vs A wrong + B correct)
    differ significantly.
    More appropriate than t-test when you have a single fixed test set.
    """
    from statsmodels.stats.contingency_tables import mcnemar
    # Contingency table
    correct_a = (pred_a == y_true)
    correct_b = (pred_b == y_true)
    table = np.array([
        [(correct_a & correct_b).sum(), (correct_a & ~correct_b).sum()],
        [(~correct_a & correct_b).sum(), (~correct_a & ~correct_b).sum()],
    ])
    result = mcnemar(table, exact=True)
    print(f"McNemar test: statistic={result.statistic:.4f}, p={result.pvalue:.4f}")
```

---

## 7. Real-World Examples

**Medical diagnosis (recall vs precision):** A cancer screening model must maximize recall (catch all true positives) because a missed cancer (FN) is far more costly than an unnecessary biopsy (FP). Team uses F2-score (beta=2, weighting recall twice over precision) as the CV objective. Final model achieves recall=0.94, precision=0.61, AUC-PR=0.82. A precision-tuned model at the same AUC-ROC (0.91) had recall=0.78 — 16% more missed cases.

**Fraud detection (AUC-PR over AUC-ROC):** Payment fraud rate = 0.3% (severely imbalanced). Model A had AUC-ROC=0.96, AUC-PR=0.72. Model B had AUC-ROC=0.94, AUC-PR=0.41. Team initially chose Model B (higher simplicity). After switching metric to AUC-PR, Model A was selected. At identical 0.1% false positive rate, Model A caught 2.1x more fraud.

**Insurance pricing (calibration):** Gradient boosted model scored AUC-ROC=0.87 but Brier score=0.18 (vs logistic regression Brier=0.11 at AUC-ROC=0.82). Insurance premiums are set as `P(claim) * E[claim_amount]` — poorly calibrated probabilities meant premiums were systematically mispriced. Platt scaling on a 10,000-row held-out set reduced Brier score from 0.18 to 0.12 without changing AUC.

**Financial time series (TimeSeriesSplit):** A team used random 5-fold CV on daily stock features. Validation AUC-ROC was 0.79. After switching to `TimeSeriesSplit(n_splits=5)` (always train on past, validate on future), CV AUC dropped to 0.61 — closer to actual live performance of 0.59. The gap was entirely caused by future data leaking into training folds.

**Optuna hyperparameter search:** GBM model with GridSearchCV over 4 hyperparameters (5 values each = 625 fits * 5 CV = 3,125 model fits, ~4 hours). Optuna TPE with 200 trials found the same best AUC-ROC (0.8312 vs grid 0.8309) in 200 * 5 = 1,000 fits (~80 minutes). Pruner stopped 43 of 200 trials early, saving additional ~25% time.

---

## 8. Tradeoffs

### Metric Selection for Imbalanced Classification

| Metric   | Insensitive to imbalance | Reflects minority recall | Use when |
|----------|--------------------------|--------------------------|----------|
| Accuracy | No (90% negative = 90% acc) | No                     | Balanced only |
| AUC-ROC  | Mostly yes               | Partially               | Moderately imbalanced (> 5% positive) |
| AUC-PR   | Yes                      | Yes                     | Highly imbalanced (< 5% positive) |
| F1       | No                       | Yes (at threshold)       | Single operating point matters |
| MCC      | Yes                      | Yes                     | Imbalanced, both FP and FN costly |

### CV Strategy Comparison

| Strategy          | Data assumption | Fold independence | Cost |
|-------------------|-----------------|-------------------|------|
| K-Fold            | IID             | Moderate          | n_splits * 1 fit |
| Stratified K-Fold | IID, imbalanced | Moderate          | n_splits * 1 fit |
| TimeSeriesSplit   | Temporal        | High              | n_splits * 1 fit, growing train size |
| Repeated Stratified| IID, low n     | Highest           | n_splits * n_repeats fits |
| LOO               | Small n         | High              | n fits |

### Tuning Strategy Comparison

| Method             | Trials needed to find good params | Handles conditional params | Parallelizable |
|--------------------|-----------------------------------|----------------------------|----------------|
| GridSearchCV       | Exponential in param count        | No                         | Yes            |
| RandomizedSearchCV | 60 trials ~ 95% of grid           | No                         | Yes            |
| Optuna TPE         | 50–200 for most problems          | Yes                        | Yes (async)    |

---

## 9. When to Use / When NOT to Use

**StratifiedKFold — always use for classification** unless class distribution is naturally uniform (very rare). Never use plain `KFold` on imbalanced data — folds may have no positive examples.

**TimeSeriesSplit — must use for temporal data** (financial, sensor, clickstream, demand forecasting). Random splits create future-to-past leakage that produces wildly optimistic metrics. Purge a gap between train and validation (e.g., skip 1 week) to prevent feature autocorrelation from leaking across the boundary.

**AUC-ROC — use when:** class imbalance is moderate (> 5% positives), you need a threshold-agnostic metric, or you are comparing models for ranking quality.
**AUC-PR — use when:** positives are rare (< 5%), false positives are cheap but false negatives are costly (fraud, disease detection).

**Calibration — must do when:** model output is used as a probability estimate to drive decisions (insurance pricing, medical risk, auction bid pricing). Not needed if you only care about ranking (recommendation systems, information retrieval).

**Optuna / Bayesian tuning — use when:** each model fit is expensive (> 1 minute), the parameter space is large (> 4 parameters), or you have conditional parameters (depth only matters if n_estimators > 500). Use GridSearchCV for small grids (< 30 combinations) — overhead is not justified.

**LOO-CV — avoid for large n**: O(n) fits; 100,000 rows = 100,000 model trains. Use only when n < 200 and you need the lowest-bias estimate possible (medical studies, rare event research).

---

## 10. Common Pitfalls

**Pitfall 1: Evaluating on training data.**
A junior engineer reported model accuracy of 99.7% on the training set. Actual test accuracy was 74%. The model had memorized training data (decision tree with no depth limit). Fix: always report metrics on a held-out set or via cross-validation.

**Pitfall 2: Using the test set for model selection.**
Team ran 20 experiments, evaluated each on the test set, chose the best, and reported that test score. The reported metric is now optimistically biased — you have effectively used the test set as a validation set 20 times. Fix: use cross-validation scores for model selection; evaluate on the test set exactly once for final reporting.

**Pitfall 3: Choosing accuracy on imbalanced data.**
Fraud detection dataset with 0.5% fraud rate. Classifier that always predicts "not fraud": accuracy = 99.5%, AUC-PR = 0.005, recall = 0. Team shipped the model because "99.5% accuracy" sounded good. Fix: define the right metric (AUC-PR or recall@fixed FPR) before training begins.

**Pitfall 4: Not calibrating probabilities for decision-making.**
A churn prediction model output P(churn) = 0.8 for many customers. Marketing set the retention offer threshold at 0.5 (above = offer). Post-launch: only 43% of flagged customers actually churned (actual rate should have been ~80% per the model). Brier score analysis showed severe overconfidence. Fix: fit Platt scaling or isotonic calibration on a held-out validation set.

**Pitfall 5: Random split on time-series data.**
A demand forecasting model for a retail chain used random 20% test split. Test AUC 0.86. After deployment, live MAE was 3x higher than test MAE. Random split selected test rows from all months; training data included December rows when the model predicted November. TimeSeriesSplit on the same data gave a test MAE matching production within 8%.

**Pitfall 6: Misinterpreting MAPE with near-zero actuals.**
MAPE = |actual - predicted| / |actual|. When actual sales = 1 unit (common for long-tail SKUs), a prediction of 2 gives MAPE = 100%. A prediction of 1.1 gives MAPE = 10%. MAPE becomes numerically unstable and misleading. Fix: use MASE (mean absolute scaled error) or RMSSE for intermittent demand data.

```python
# BROKEN — MAPE blows up for small actuals
mape = np.mean(np.abs((y_true - y_pred) / y_true)) * 100

# FIXED — guard against near-zero
epsilon = 1.0  # domain-specific floor
mape_safe = np.mean(np.abs((y_true - y_pred) / np.maximum(np.abs(y_true), epsilon))) * 100
```

---

## 11. Technologies & Tools

| Tool                  | Purpose                                      | Notes |
|-----------------------|----------------------------------------------|-------|
| scikit-learn          | CV, metrics, calibration, GridSearchCV       | Production standard |
| Optuna                | Bayesian hyperparameter tuning               | pip install optuna |
| Hyperopt              | Alternative Bayesian tuning (TPE, ATPE)      | Compatible with XGBoost |
| Ray Tune              | Distributed hyperparameter search            | Works with PyTorch, sklearn |
| MLflow                | Experiment tracking, metric logging          | Log CV scores per trial |
| Weights & Biases      | Experiment tracking + hyperparameter sweeps  | Sweep = distributed random/Bayesian |
| statsmodels           | McNemar's test, likelihood ratio tests       | pip install statsmodels |
| imbalanced-learn      | SMOTE, ADASYN, class-weighted CV             | Imbalanced dataset tools |
| scikit-plot           | AUC curves, confusion matrix visualization   | Quick EDA |
| SHAP                  | Model explanation alongside evaluation       | Feature importance at evaluation time |

---

## 12. Interview Questions with Answers

**Q: What is the difference between AUC-ROC and AUC-PR, and when do you prefer each?**
AUC-ROC measures the probability that the model scores a random positive higher than a random negative; it is threshold-agnostic and ranges from 0.5 (random) to 1.0. AUC-PR measures the area under the precision-recall curve; its baseline equals the positive class prevalence (e.g., 0.05 for 5% positives). For heavily imbalanced datasets (< 5% positives), AUC-ROC is misleadingly optimistic because the large number of true negatives inflates TPR and TNR. AUC-PR directly measures how well the model retrieves the minority class. Use AUC-PR for fraud detection, rare disease prediction, and any setting where the minority class is the primary concern.

**Q: Why must you use StratifiedKFold instead of KFold for classification with imbalanced classes?**
Plain KFold assigns rows to folds randomly; with 1% positive rate and 1,000 rows, some folds may contain zero positive examples, making AUC computation undefined and giving wildly variable estimates. StratifiedKFold ensures each fold has approximately the same positive rate as the full dataset (here ~10 positives per fold). This produces stable, unbiased cross-validation estimates. Always use StratifiedKFold as the default for classification.

**Q: Explain the bias-variance tradeoff and how you diagnose each.**
Bias is systematic error from a model too simple to capture the true relationship (underfitting); variance is error from a model too sensitive to training data fluctuations (overfitting). Diagnose: if training score is low and CV score is similar (both ~65%), the model underfits — add features, use more complex model. If training score is high (95%) but CV score is low (70%), the model overfits — regularize, reduce complexity, add more data. Learning curves (train/val score vs training set size) visualize this: high variance = large gap that closes as n increases; high bias = gap is small but both scores are low.

**Q: What is probability calibration and why does it matter?**
Calibration means that when a model predicts P(y=1) = 0.7, approximately 70% of such predictions are actually positive. Many models (GBMs, SVMs, random forests) produce probabilities that are not well-calibrated — GBMs tend to be overconfident (probabilities cluster near 0 or 1). Calibration matters when probabilities drive decisions: insurance pricing uses P(claim) * E[amount]; a medical decision tool uses P(disease) to trigger further tests; an auction system uses P(click) * bid value. Fix with Platt scaling (sigmoid fit, needs ~1,000 samples) or isotonic regression (non-parametric, needs > 1,000 samples); always fit the calibrator on a separate held-out set.

**Q: When should you use TimeSeriesSplit and what is the purge gap?**
Use TimeSeriesSplit whenever rows are temporally dependent — financial data, time-based user events, sensor readings, demand forecasting. The core rule: training data must always be earlier in time than validation data. A purge gap is a buffer period between the training end and validation start. For example, if you train up to day 100 and validate on day 107, you skip days 101–106. This prevents autocorrelated features (e.g., 7-day rolling average) from leaking information across the boundary. Without a purge gap, day-100 training features and day-101 validation features share the same underlying values.

**Q: How does Optuna's TPE sampler work and why is it better than random search?**
TPE (Tree-structured Parzen Estimator) maintains two models: `l(x)` (distribution of hyperparameter configurations associated with good results) and `g(x)` (associated with poor results). It samples the next candidate from the ratio `l(x)/g(x)`, meaning it prioritizes regions that historically produce good results while maintaining some exploration. After 50–100 trials, TPE focuses search in the most promising parameter regions, whereas random search samples uniformly throughout. In practice TPE finds equivalent or better results in 30–50% fewer trials than random search for standard ML hyperparameter spaces.

**Q: What is the Brier score and how does it relate to calibration?**
The Brier score is the mean squared error of probability predictions: `mean((y_true - y_prob)^2)`. Range: 0 (perfect) to 1 (worst). A perfectly calibrated model does not guarantee a low Brier score — discrimination (ranking ability) also contributes. The Brier score decomposes into calibration + resolution + uncertainty. It is the most common scalar summary of probability prediction quality. Reduce Brier score by: (1) improving model AUC (better discrimination), (2) calibrating probabilities (better calibration).

**Q: When is it appropriate to use the paired t-test for model comparison?**
The paired t-test on CV fold scores is appropriate when you have multiple evaluation folds on the same data (each fold gives one observation per model) and you want to test whether mean CV performance differs. "Paired" accounts for fold-level correlation — fold 3 tends to be hard for all models, so comparing models on the same folds is more efficient than unpaired. Limitation: CV fold scores are not truly independent (training sets overlap), so p-values are anti-conservative (reject H0 too easily). Treat results as directional evidence; require p < 0.01 rather than p < 0.05 for higher confidence.

**Q: What is McNemar's test and when do you use it instead of the t-test?**
McNemar's test compares two classifiers on a fixed held-out test set based on the discordant pairs: cases where model A is correct and B is wrong (b), versus B correct and A wrong (c). The test statistic is `(b - c)^2 / (b + c)` following chi-squared. Use McNemar's when you have a single test set (not CV) and binary classification predictions. It is more appropriate than the t-test in this setting because it directly measures disagreement between classifiers rather than continuous score differences.

**Q: Explain the problem with using test set for both model selection and reporting.**
If you use the test set to compare 20 models and report the best, you have introduced selection bias. The reported metric is the maximum of 20 noisy estimates rather than an unbiased estimate of the winner's true performance — it overestimates true performance by an amount proportional to the number of models compared and the noise in the estimate. Fix: use cross-validation on the training set for all model comparison decisions; reserve the test set for a single final evaluation after all decisions are made.

**Q: How do you choose the number of folds k in k-fold cross-validation?**
Standard choice is k=5 or k=10. Higher k (10) gives a lower-bias estimate (each validation fold is smaller, training set is larger and closer to full data size) but higher variance (more folds, each noisier). Lower k (5) is faster and still gives reasonable estimates for n > 1,000. For small n (< 500), use LOO-CV or k=n-1. For very large n (> 1M), k=3 is often sufficient — the estimate variance is dominated by the model's own noise, not fold size. Time-series splits: k = number of years or quarters depending on forecast horizon.

**Q: What is MAPE and what are its limitations for regression evaluation?**
MAPE (Mean Absolute Percentage Error) = `mean(|actual - predicted| / |actual|) * 100`. Limitations: (1) undefined when actual = 0 (division by zero), (2) asymmetric — over-prediction and under-prediction of the same magnitude give different errors, (3) heavy penalty for small actuals (predicting 2 when actual is 1 gives 100% error). Better alternatives: sMAPE (symmetric MAPE), MASE (mean absolute scaled error, relative to naïve forecast), RMSSE. Use MAPE only when actuals are reliably > 0 and interpretability in percentage terms is required.

---

## 13. Best Practices

1. Fix the metric before writing any code — let the business objective define whether you optimize for precision, recall, AUC-ROC, AUC-PR, MAE, or MAPE.
2. Use `StratifiedKFold` as the default CV for all classification tasks; switch to `TimeSeriesSplit` immediately for temporal data.
3. Keep the test set locked in a separate file or database partition; do not load it until the very end of the project.
4. On imbalanced datasets (< 10% positives), always report AUC-PR alongside AUC-ROC; report both precision and recall at the chosen operating threshold.
5. Calibrate any model whose probability outputs drive financial or medical decisions; use Platt scaling for small validation sets (< 1,000), isotonic regression for larger.
6. Use Optuna or Hyperopt for hyperparameter tuning when each model fit takes > 30 seconds; plain GridSearchCV for fast models with < 30 parameter combinations.
7. Log all experiment results (hyperparameters, CV scores, metric values, training time) with MLflow or Weights & Biases — reproducibility requires this.
8. Run learning curves (train/val score vs training set size) when CV score is disappointing — they diagnose whether more data or more complexity is needed.
9. When comparing two models, use a paired t-test on CV scores (set p < 0.01 threshold) or McNemar's test on a fixed test set — never eyeball mean differences.
10. Report uncertainty: always include standard deviation of CV scores alongside the mean. A model with mean AUC 0.84 ± 0.02 is not clearly better than 0.83 ± 0.04.

---

## 14. Case Study

**Problem:** An e-commerce platform wants to predict whether a user will make a purchase within 24 hours of a session. Dataset: 10M sessions over 12 months, 2.3% purchase rate (highly imbalanced). Features: 35 numeric + 8 categorical. Business requirement: maximize revenue per email sent — precision matters (do not spam non-buyers), but recall matters (do not miss buyers). Agreed metric: F1-score at 10% email budget constraint (top 10% of scores by predicted probability).

**Step 1 — Metric choice:**
- Accuracy: rejected (90% "no purchase" baseline = 97.7% accuracy)
- AUC-ROC: used for model selection (threshold-agnostic comparison)
- AUC-PR: primary report metric (imbalanced, minority class matters)
- F1 at top-10% cutoff: business metric for production threshold

**Step 2 — Cross-validation design:**
12 months of data — random split would allow December behavior in training and July in validation. Use `TimeSeriesSplit(n_splits=5)`: each fold trains on earlier months, validates on next month. This matches production deployment (model trained on past, scores future sessions).

**Step 3 — Baseline:**
LogisticRegression with StandardScaler: CV AUC-ROC=0.764, CV AUC-PR=0.213. F1@10%=0.31.

**Step 4 — Model comparison (3 candidates):**

```python
models = {
    "logreg": LogisticRegression(C=1.0, max_iter=1000),
    "rf": RandomForestClassifier(n_estimators=300, random_state=42),
    "gbm": GradientBoostingClassifier(n_estimators=500, random_state=42),
}
cv = TimeSeriesSplit(n_splits=5)
for name, model in models.items():
    scores = cross_val_score(model, X, y, cv=cv, scoring="average_precision", n_jobs=-1)
    print(f"{name}: AUC-PR = {scores.mean():.4f} +/- {scores.std():.4f}")
# logreg: 0.213 +/- 0.018
# rf:     0.287 +/- 0.021
# gbm:    0.341 +/- 0.019
```

Paired t-test (AUC-PR, gbm vs rf): t=4.2, p=0.008 — GBM significantly better.

**Step 5 — Hyperparameter tuning (Optuna, 150 trials, ~90 minutes):**
Best params: `n_estimators=700, max_depth=6, learning_rate=0.05, subsample=0.8`. CV AUC-PR=0.358.

**Step 6 — Calibration:**
GBM Brier score before calibration: 0.037. After isotonic regression on 200k held-out rows: 0.021. Reliability diagram shows calibrated model within 3% of diagonal at all probability bins.

**Step 7 — Final test set evaluation (touched once):**
12-month test period (last month, ~833k sessions): AUC-PR=0.349, F1@10%=0.44, recall=0.61, precision=0.34.

**Business outcome:** At 10% email budget, model identifies 61% of buyers. Prior rule-based system (cart abandonment + recency) captured 38% of buyers at the same 10% budget. Incremental revenue per 100k sessions: $142k vs $88k (+61% lift).

**Key learnings:** TimeSeriesSplit was critical — random CV gave AUC-PR 0.41 (inflated by ~0.06 vs time-based CV 0.358). Calibration improved probability-based threshold setting without changing AUC at all. Optuna found better params than GridSearchCV with 40% fewer model fits.
