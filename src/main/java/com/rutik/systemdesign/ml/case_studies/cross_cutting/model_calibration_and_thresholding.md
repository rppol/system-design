# Model Calibration and Thresholding

## 1. Concept Overview

Calibration is the property of a probabilistic classifier that its predicted probabilities match empirically observed event rates. A model that outputs p(churn) = 0.70 is well-calibrated if, across all customers assigned that score, 70% actually churn. Thresholding is the downstream task of converting a continuous probability score into a binary decision, where the optimal threshold depends on the cost of false positives versus false negatives.

A model can rank instances perfectly (high AUC = 1.0) yet be completely miscalibrated — consistently predicting 0.55 for events that happen 90% of the time. In that case the model is useless for any system that makes decisions based on the magnitude of the score, not just its rank.

---

## 2. Intuition

> A weather forecaster who says "70% chance of rain" every day when it rains 70% of the time is perfectly calibrated, even if she never correctly predicts which day will be rainy. Calibration is about the accuracy of the probability statement, not the prediction.

Mental model: imagine bucketing all predictions into bins (0.0–0.1, 0.1–0.2, …, 0.9–1.0). For a calibrated model, within each bin the fraction of positive examples equals the bin center. Calibration error measures how far the empirical positive rate is from the predicted rate, averaged across bins.

**Key insight:** AUC measures ranking quality (does the model rank positives above negatives?). Calibration measures probability quality (does the predicted probability mean what it says?). Both matter, but they measure different things. A churn model with AUC = 0.90 and ECE = 0.25 is wrong in a way that AUC will never reveal.

Why it matters: every decision system that uses a probability threshold — intervention budgeting, cost-sensitive classification, risk-based pricing, fraud decisioning — requires calibrated probabilities. If p̂ ≠ p(event), the threshold optimized on calibrated probabilities will be systematically wrong.

---

## 3. Core Principles

**Calibration and discrimination are independent axes.** A model can be well-calibrated with low AUC (random-looking predictions with correct average rates) or poorly calibrated with high AUC (perfect ranking, wrong magnitudes). Both axes must be evaluated. A well-calibrated, high-AUC model is the target.

**Calibration should be measured post-hoc on a held-out set.** Never measure calibration on the training set — the model has already seen this data. Measure on a validation or test set that was not used during training or threshold tuning.

**Class imbalance causes systematic miscalibration.** Many classifiers (especially tree models trained with default settings) output scores that are not calibrated probabilities but rather monotone transformations of the decision value. The raw Platt score from an SVM or the leaf mean from a GBT is not automatically a calibrated probability.

**Threshold selection is a business decision, not a model decision.** The optimal threshold is not 0.5 unless false positives and false negatives have equal cost, which they almost never do. Threshold selection requires explicitly estimating the cost ratio C(FP)/C(FN) from business context.

**Calibration is not constant over time.** A model calibrated on a training set from Q1 may be miscalibrated on Q2 data if the event rate has shifted. Include calibration metrics in production monitoring.

---

## 4. Types / Architectures / Strategies

### 4.1 Calibration Methods

| Method | Mechanism | When to use | Limitation |
|---|---|---|---|
| Platt scaling | Logistic regression on raw scores | Small held-out calibration set (< 1k) | Only corrects monotone S-shaped miscalibration |
| Isotonic regression | Non-parametric monotone function fit | Larger calibration set (> 1k); complex miscalibration | Can overfit on small sets; step-function output |
| Beta calibration | Beta distribution fit to scores | Scores near 0 or 1 (extreme imbalance) | Less commonly supported |
| Temperature scaling | Single scaling parameter for DL logits | Neural network classifiers with overconfident softmax | Only scales, doesn't reshape |
| Histogram binning | Equal-frequency bins, calibrate each bin | Any model; simple and robust | Coarse calibration at bin boundaries |

### 4.2 Threshold Selection Strategies

| Strategy | Formula | Use case |
|---|---|---|
| Cost-minimizing threshold | t* = C(FP) / (C(FP) + C(FN)) | Asymmetric costs (fraud, medical diagnosis) |
| F-beta threshold | Maximize Fβ on validation set | When recall and precision have explicit weights |
| Fixed recall threshold | Min t where recall ≥ target | SLA on positive detection (e.g., 95% of fraud caught) |
| Fixed precision threshold | Min t where precision ≥ target | Intervention cost per action bounded (e.g., <5% false positive rate) |
| Expected value threshold | Maximize E[value] = TP×gain - FP×cost over thresholds | Revenue-aware decisioning |

### 4.3 Reliability Diagram (Calibration Curve) Interpretation

```
Perfect calibration: diagonal (y = x)
Overconfident:       curve bends toward x-axis (predicted > actual)
Underconfident:      curve bends above diagonal (predicted < actual)
S-shaped:            overconfident in middle, underconfident at extremes
```

---

## 5. Architecture Diagrams

### Calibration Pipeline

```
Training Data
     |
     v
Model Training (LightGBM / LogReg / DNN)
     |
     v
Raw Scores on Held-Out Calibration Set
     |
     v
[Platt Scaling | Isotonic Regression | Beta Cal]
     |                Calibrator fitted on calibration set
     v
Calibrated Probabilities
     |
     +----------+-----------+
     |                       |
Reliability Diagram      ECE Metric
(visual check)           (numeric SLA)
     |
     v
Threshold Selection (cost matrix or F-beta optimization)
     |
     v
Production Decision: score >= threshold → action
```

### Train/Calibrate/Test Data Split

```
Full Dataset
+-----------------------------------+
|  60% Train  | 20% Calibrate | 20% Test |
+-----------------------------------+
      |               |                |
   Fit model    Fit calibrator    Evaluate
                on model scores   calibrated
                (never seen by    model
                  model)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Measuring Calibration

```python
import numpy as np
import matplotlib.pyplot as plt
from sklearn.calibration import calibration_curve

def compute_ece(
    y_true: np.ndarray,
    y_prob: np.ndarray,
    n_bins: int = 10,
) -> float:
    """
    Expected Calibration Error (ECE): weighted average of |confidence - accuracy|
    across equal-width probability bins. Target: ECE < 0.05 for production.
    """
    bin_edges = np.linspace(0.0, 1.0, n_bins + 1)
    ece = 0.0
    n = len(y_true)

    for i in range(n_bins):
        mask = (y_prob >= bin_edges[i]) & (y_prob < bin_edges[i + 1])
        if mask.sum() == 0:
            continue
        bin_acc = float(y_true[mask].mean())           # empirical positive rate in bin
        bin_conf = float(y_prob[mask].mean())          # mean predicted probability in bin
        bin_weight = mask.sum() / n
        ece += bin_weight * abs(bin_conf - bin_acc)

    return ece

def plot_calibration(
    y_true: np.ndarray,
    y_prob_uncal: np.ndarray,
    y_prob_cal: np.ndarray,
) -> None:
    fig, ax = plt.subplots(figsize=(6, 6))
    frac_pos_uncal, mean_pred_uncal = calibration_curve(y_true, y_prob_uncal, n_bins=10)
    frac_pos_cal,   mean_pred_cal   = calibration_curve(y_true, y_prob_cal,   n_bins=10)

    ax.plot([0, 1], [0, 1], "k--", label="Perfect calibration")
    ax.plot(mean_pred_uncal, frac_pos_uncal, "r-o", label=f"Before cal (ECE={compute_ece(y_true, y_prob_uncal):.3f})")
    ax.plot(mean_pred_cal,   frac_pos_cal,   "b-o", label=f"After cal  (ECE={compute_ece(y_true, y_prob_cal):.3f})")
    ax.set_xlabel("Mean predicted probability")
    ax.set_ylabel("Fraction of positives")
    ax.set_title("Reliability Diagram")
    ax.legend()
```

### 6.2 Platt Scaling and Isotonic Regression

```python
from sklearn.linear_model import LogisticRegression
from sklearn.isotonic import IsotonicRegression
from sklearn.calibration import CalibratedClassifierCV
import lightgbm as lgb
import numpy as np
from typing import Literal

def calibrate_model(
    base_scores: np.ndarray,    # raw model output on calibration set
    y_cal: np.ndarray,          # true labels on calibration set
    method: Literal["platt", "isotonic"] = "isotonic",
) -> IsotonicRegression | LogisticRegression:
    """Fit a calibrator on held-out calibration data."""
    scores_2d = base_scores.reshape(-1, 1)
    if method == "platt":
        calibrator = LogisticRegression(C=1.0, solver="lbfgs")
        calibrator.fit(scores_2d, y_cal)
    else:
        calibrator = IsotonicRegression(out_of_bounds="clip")
        calibrator.fit(base_scores, y_cal)
    return calibrator

# Example: GBDT trained, then calibrated
lgbm = lgb.LGBMClassifier(n_estimators=300, random_state=42)
lgbm.fit(X_train, y_train)

raw_scores_cal = lgbm.predict_proba(X_cal)[:, 1]
calibrator = calibrate_model(raw_scores_cal, y_cal, method="isotonic")

# At serving time:
def predict_calibrated(model, calibrator, X: np.ndarray) -> np.ndarray:
    raw = model.predict_proba(X)[:, 1]
    return calibrator.predict(raw)   # isotonic: .predict(); Platt: .predict_proba()
```

### 6.3 Broken Pattern — Threshold at 0.5 on Imbalanced Problem

```python
# WRONG: using default threshold on a 1:50 imbalance (2% event rate, e.g. fraud)
from sklearn.metrics import precision_score, recall_score

y_pred_wrong = (y_prob >= 0.5).astype(int)
print(f"Default threshold=0.5: precision={precision_score(y_test, y_pred_wrong):.2f}, "
      f"recall={recall_score(y_test, y_pred_wrong):.2f}")
# typical result: precision=0.82, recall=0.11
# The model flags almost nothing — misses 89% of fraud cases.
# Business assumed "high accuracy model" but recall=0.11 is operationally useless.
```

```python
# CORRECT: optimize threshold using cost matrix or fixed recall target
from sklearn.metrics import precision_recall_curve

precision, recall, thresholds = precision_recall_curve(y_test, y_prob)

# Option A: fix recall at 0.80 (catch 80% of fraud), find min threshold that achieves it
target_recall = 0.80
idx = np.argmin(np.abs(recall - target_recall))
optimal_threshold = thresholds[idx]
print(f"Threshold for recall={target_recall:.2f}: {optimal_threshold:.3f}, "
      f"precision={precision[idx]:.2f}")

# Option B: expected value optimization given cost of FP=5, cost of FN=200
cost_fp, cost_fn = 5, 200
ev = precision * recall * (-cost_fp) + (1 - precision) * recall * (-cost_fn)
# simplified; full expected value includes base rate normalization
best_idx = np.argmax(ev)
best_threshold = thresholds[best_idx]
```

### 6.4 Threshold Monitoring in Production

```python
import numpy as np
from dataclasses import dataclass

@dataclass
class ThresholdMonitor:
    threshold: float
    alert_fpr_max: float = 0.10   # alert if FPR exceeds this
    alert_recall_min: float = 0.75  # alert if recall drops below this

    def evaluate(
        self,
        y_true: np.ndarray,
        y_prob: np.ndarray,
    ) -> dict[str, float]:
        y_pred = (y_prob >= self.threshold).astype(int)
        tp = int(((y_pred == 1) & (y_true == 1)).sum())
        fp = int(((y_pred == 1) & (y_true == 0)).sum())
        fn = int(((y_pred == 0) & (y_true == 1)).sum())
        tn = int(((y_pred == 0) & (y_true == 0)).sum())

        recall = tp / (tp + fn + 1e-9)
        fpr    = fp / (fp + tn + 1e-9)
        prec   = tp / (tp + fp + 1e-9)

        return {
            "recall": recall,
            "fpr": fpr,
            "precision": prec,
            "recall_alert": recall < self.alert_recall_min,
            "fpr_alert": fpr > self.alert_fpr_max,
        }
```

---

## 7. Real-World Examples

**Google Pay (fraud detection):** uses cascaded calibration. The primary fraud scorer (a deep network) outputs raw logits. Platt scaling is applied on a rolling 30-day calibration window to adjust for daily event-rate shifts. The decision threshold is recalibrated weekly using the expected-value framework with merchant-specific cost matrices (chargebacks for card-not-present transactions average $15–$35 plus penalty fees from card networks).

**Spotify (churn prediction):** discovered in 2019 that their GBDT churn model had ECE = 0.31 — severely miscalibrated. The model ranked users correctly (high AUC) but the absolute probabilities were wrong: users labeled 0.60 probability churned at 0.85 rate. The marketing team was setting intervention budgets using the raw probabilities, resulting in 40% under-investment in high-risk users. After isotonic calibration, ECE dropped to 0.04 and budget allocation improved. Intervention ROI increased by 18%.

**Lending Club / credit underwriting:** regulatory requirement (ECOA) demands that declined applicants receive the top 4 adverse factors. The score must also be calibrated so that the cutoff score corresponds to a meaningful default probability (e.g., "applicants below 620 score have >8% 24-month default probability"). Miscalibrated scores violate this requirement because the cutoff would correspond to a different default rate than documented.

**Facebook (content ranking):** uses a two-stage calibration for click-through-rate prediction. Stage 1: isotonic regression calibrates the raw model output to match observed CTR. Stage 2: a real-time prior update adjusts predictions based on the current impression context (time of day, user cohort CTR shift). Without stage 2, predictions made at 09:00 are calibrated for the average day, but CTR at 09:00 is 30% higher than average — leading to systematic under-delivery in the morning.

---

## 8. Tradeoffs

| Calibration Method | Pros | Cons | Recommended data size |
|---|---|---|---|
| No calibration | No overhead | Wrong probabilities for any threshold-based decision | — |
| Platt scaling | Simple, few parameters, stable | Only corrects monotone sigmoid miscalibration | 500+ samples |
| Isotonic regression | Flexible, non-parametric | Can overfit; step-function output may be weird | 2,000+ samples |
| Temperature scaling | Preserves rank, one parameter | Only scales (doesn't reshape); DL-specific | Any DL |
| Beta calibration | Handles 0/1 extremes well | Less tooling support | 1,000+ samples |

### Threshold Strategy Tradeoffs

| Strategy | Optimizes for | Risk |
|---|---|---|
| Cost-minimizing | Correct economic decision | Requires accurate cost estimates (rarely available) |
| Fixed recall | Never miss a positive above budget | May produce too many FPs in low-precision regime |
| Fixed precision | Intervention budget predictable | May miss too many positives |
| F-beta | Balanced (explicit beta weight) | Beta is a hyperparameter that must be chosen |

---

## 9. When to Use / When NOT to Use

**Always calibrate when:**
- Model output drives probability-based decisions (intervention thresholds, risk-based pricing).
- Class imbalance > 10:1 (tree models are systematically miscalibrated in this regime).
- The model is a neural network (logits are not probabilities; softmax outputs are typically overconfident).
- Multiple models are ensembled and their outputs must be comparable on the same probability scale.

**Calibration is less critical when:**
- The downstream system only uses rank ordering (e.g., sort by score and show top-10 results).
- The business metric is AUC or NDCG only.
- The model is a logistic regression trained with proper cross-entropy loss on balanced data (often well-calibrated by construction).

---

## 10. Common Pitfalls

**Calibrating on training data.** The model has already minimized cross-entropy on training data; calibration on the same data produces a trivially well-fitted calibrator that overfits. Always calibrate on a held-out calibration set that was not used for model training.

**Treating threshold selection as a one-time decision.** The optimal threshold changes when the event rate shifts or the cost matrix changes. A fraud threshold optimized for a 1.5% fraud rate becomes conservative if fraud rate drops to 0.8% (too many false positives) and liberal if it rises to 3% (too many missed frauds). Recalibrate thresholds regularly — at least monthly for consumer ML systems.

**Confusing AUC with calibration quality.** Teams that monitor only AUC miss calibration degradation entirely. A concept drift that changes absolute event rates (e.g., seasonal churn spike) can leave AUC unchanged while ECE triples. Both metrics must be in the production dashboard.

**Applying calibration to a model that already uses Platt scaling internally.** Some sklearn classifiers (e.g., `SVC(probability=True)`) apply Platt scaling internally. Applying a second Platt scaling layer on top produces double-calibration, which may be worse than no calibration. Check the model's documentation before adding an external calibrator.

**Using the wrong base rate for calibration.** If the calibration set has a different positive rate than production (common in undersampling strategies), calibrated probabilities will be biased relative to production. Apply a prior correction: p_adj = (p_cal × prevalence_prod) / ((p_cal × prevalence_prod) + ((1 - p_cal) × (1 - prevalence_prod))) for the production base rate.

---

## 11. Technologies & Tools

| Tool | Calibration Support | Notes |
|---|---|---|
| scikit-learn | `CalibratedClassifierCV`, `calibration_curve` | Platt + isotonic, ECE via `calibration_curve`, cross-val wrapper |
| LightGBM | Raw GBDT output; calibrate externally | Add isotonic/Platt as post-processing step |
| netcal (Python) | Full calibration toolkit | ECE, ACE, MCE, reliability diagrams, 10+ calibration methods |
| Torch + temperature | `torch.nn.Parameter` for temperature | Standard in DL classification; one-parameter optimization |
| Evidently AI | Production calibration drift monitoring | Calibration metrics in production data pipelines |

---

## 12. Interview Questions with Answers

**What is calibration and how does it differ from discrimination (AUC)?**
Calibration measures whether predicted probabilities match empirical event rates: a model that outputs p=0.7 for a set of examples is well-calibrated if 70% of those examples are positive. Discrimination (AUC) measures whether the model correctly ranks positives above negatives, regardless of the absolute probability values. The two are independent: a model can rank perfectly (AUC=1.0) but output 0.55 for all events that happen 90% of the time (poorly calibrated), or be well-calibrated but with low AUC (correct average rates but poor separation). Both matter: AUC measures predictive value; calibration measures decision value.

**When would you use Platt scaling versus isotonic regression?**
Platt scaling (logistic regression fit on raw scores) is appropriate when you have a small calibration set (<2k examples) or when miscalibration is monotone and S-shaped (overconfident in the middle of the score range, which is typical for SVMs and some neural networks). Isotonic regression is a non-parametric monotone function fitter that can correct any shape of miscalibration but requires a larger calibration set (2k+) to avoid overfitting. In practice, for LightGBM/XGBoost, isotonic regression almost always produces better calibration. For neural networks, temperature scaling is preferred because it preserves the model's learned ranking.

**How do you choose an optimal classification threshold?**
The optimal threshold is a function of the cost ratio C(FP)/C(FN). If false positives and false negatives have equal cost, the threshold is 0.5 (assuming well-calibrated probabilities). In fraud detection, missing a fraud (FN) costs 10-100x more than a false alarm (FP), so the threshold is set low (0.1-0.3) to maximize recall. In medical diagnosis, the threshold depends on the severity and treatability of the condition. Practically: plot the precision-recall curve on the validation set, explicitly estimate costs from business context, compute expected value or cost at each threshold, and select the threshold that optimizes the business objective. Re-evaluate the threshold when the cost structure changes or when the event rate shifts significantly.

**A churn model has AUC=0.88 but the marketing team says it's not useful. What do you investigate?**
First, check calibration. Plot a reliability diagram: if the model outputs p=0.6 for customers who churn 85% of the time, the team's intervention budget is calibrated to the wrong risk level. Second, check the threshold: the default 0.5 threshold may miss most churners if the churn rate is 5% and the model is conservative. Find the threshold that catches, say, 80% of churners and present precision at that threshold. Third, check segment performance: high AUC overall can mask poor performance on the specific customer segment that marketing cares about (e.g., high-LTV churners). Fourth, check the label definition: did the marketing team's definition of "churn" (no purchase in 30 days? account closure? both?) match the training label?

**Explain Expected Calibration Error (ECE) and what a good value looks like.**
ECE is computed by bucketing predictions into equal-width probability bins (typically 10 bins of 0.1 width), computing the absolute difference between the bin's average predicted probability and the fraction of positives in that bin, weighting each bin by its sample proportion, and summing. ECE < 0.05 is generally considered well-calibrated for production systems. ECE between 0.05 and 0.15 is acceptable for non-critical decisions. ECE > 0.15 indicates substantial miscalibration that should be corrected before using probabilities for threshold-based decisions. Note that ECE is sensitive to the number of bins and can be misleading when some bins have few samples; complementary metrics like Maximum Calibration Error (MCE) and reliability diagrams should always accompany ECE.

**How does class imbalance cause miscalibration?**
Many classifiers optimize cross-entropy loss, which (for a 100:1 imbalance) has 100x more gradient from negative examples than positive examples. The model pushes predictions toward the majority class, resulting in probabilities that are systematically too low for positives and too high for negatives — but the ranking is still correct. Specifically, GBDT leaf means reflect empirical rates in leaves, which are dominated by negatives. The model's raw output is not a probability but a monotone transformation of the likelihood ratio. Post-hoc calibration (isotonic regression) corrects this by learning the mapping from raw score to calibrated probability on a held-out set that includes the full imbalance.

**What is temperature scaling and when is it appropriate?**
Temperature scaling divides the neural network's logits (pre-softmax activations) by a learnable scalar temperature T, then applies softmax. For T > 1, the softmax outputs are flattened (lower confidence, better calibrated). For T < 1, they are sharpened (overconfident). Temperature is optimized by minimizing NLL on a held-out validation set, using a single scalar parameter. Temperature scaling preserves the model's ranking (it is a monotone transformation) and is computationally trivial. It is appropriate specifically for neural networks with softmax outputs that are overconfident (common in large neural networks trained with cross-entropy). It does not correct non-monotone miscalibration and is not appropriate for GBDT models.

**How would you monitor calibration in production?**
Collect ground truth labels with a delay (for churn: wait 30 days after prediction; for fraud: after dispute resolution). Join labels back to logged predictions. Compute ECE on a rolling 7-day or 30-day window. Plot a reliability diagram in a monitoring dashboard. Alert when ECE exceeds a threshold (e.g., ECE > 0.10 triggers investigation; ECE > 0.20 triggers model refresh). For high-stakes systems (credit, fraud), compute ECE at the population level and separately for protected groups (age bands, geography) to detect differential calibration (the model may be well-calibrated overall but poorly calibrated for minority groups).

**What is the base-rate correction problem in calibration?**
If the calibration set was collected under undersampling (e.g., 50% positive, 50% negative to balance training), but production has 2% positive rate, calibrated probabilities will overestimate the true probability. A calibrator trained on 50/50 data will output p=0.5 for "uncertain" cases when the true probability at 2% prevalence should be much lower. The correction is: given a calibrator fitted at sampling prevalence p_s and deployment prevalence p_d, the adjusted probability is p_adj = (p_cal × p_d / p_s) / (p_cal × p_d / p_s + (1 - p_cal) × (1 - p_d) / (1 - p_s)). Always match the calibration set prevalence to production prevalence, or apply this adjustment.

**A model is used for both ranking (show top-100 results) and probability estimation (set intervention budget). One model or two?**
Two models (or at minimum, two calibration layers) are the production-correct answer. The ranking use case is best served by a model optimized for AUC/NDCG, which may be miscalibrated. The probability estimation use case requires calibration. Forcing one model to serve both objectives well simultaneously is possible but requires explicit multi-objective optimization and careful calibration. In practice, many large companies maintain a ranking model (optimized for ranking metrics) and a separate probability model or calibration layer that converts ranks to probabilities for decisioning. This separation allows each to be improved independently.

**How does calibration drift and how do you trigger a recalibration?**
Calibration drifts when the event rate changes (seasonal churn spike, fraud rate shift after a new attack vector) or when the model's feature distribution shifts. The ECE will increase over time. Trigger recalibration when: (a) ECE on a recent 30-day window exceeds the acceptable threshold (e.g., 0.10); or (b) the observed event rate on the last 30 days differs from the calibration set event rate by more than 20% relative. Recalibration (fitting a new isotonic regression on recent labeled data) is cheap and can be done without retraining the base model. It should be part of the regular model maintenance schedule.

**How do you handle calibration for a multi-class classifier?**
Multi-class calibration requires calibrating each class independently (one-vs-rest approach) or using a multi-class extension. Temperature scaling extends directly to multi-class softmax — divide all logits by the same T. For isotonic calibration, apply isotonic regression independently to each class's probability vector and then re-normalize so probabilities sum to 1. An alternative is the Dirichlet calibration method (parametric, designed for multi-class). Evaluate calibration per-class (ECE_k for class k) in addition to aggregate ECE, since a model may be well-calibrated on the majority class but poorly calibrated on rare classes.

**What is the reliability diagram and how do you read it?**
A reliability diagram (calibration plot) plots the mean predicted probability (x-axis) against the fraction of positive examples (y-axis) for each probability bin. The diagonal y=x represents perfect calibration. Points above the diagonal mean the model is underconfident (predicted probability is lower than actual rate — model says 0.3, reality is 0.5). Points below the diagonal mean the model is overconfident (predicted probability is higher than actual rate — model says 0.8, reality is 0.6). A well-calibrated model's reliability diagram closely follows the diagonal across all bins. Look for: (a) systematic bias (all points consistently above or below); (b) S-shape (overconfident in middle, underconfident at extremes); (c) sparse bins at the extremes (uncertainty in calibration estimate).

**In a credit scoring system, the default rate shifts from 3% to 5% due to economic conditions. What happens to calibration and what do you do?**
The model's score distribution reflects the 3% base rate used in training. At 5% base rate, the model underestimates default probability for all applicants — its predicted probabilities are systematically too low. The threshold optimized for 3% base rate will now miss more defaults than intended. Actions: (1) apply base-rate correction formula to shift all predicted probabilities upward; (2) re-optimize the decision threshold on recent data at the 5% rate; (3) if the feature-default relationship has also changed (concept drift, not just prior shift), trigger a full model retrain. Monitor ECE segmented by cohort (application month) to detect whether the drift is rate-only or full concept drift. See [Drift Monitoring and Retraining](./drift_monitoring_and_retraining.md).

**Why is the AUC-ROC a poor metric for calibrated systems?**
AUC-ROC measures only rank order — it is invariant to any monotone transformation of the scores, including transformations that destroy calibration. A model with perfect calibration and a model with scores shifted by +0.3 have identical AUC. The AUC will not detect calibration degradation. For systems where the business decision depends on the absolute probability (threshold selection, cost-sensitive decisions, probability-weighted value calculations), AUC must be supplemented with ECE, Brier score, or log-loss — all of which are sensitive to both calibration and discrimination.

**What is the Brier score and how does it relate to calibration?**
Brier score = mean squared error between predicted probabilities and binary outcomes: BS = (1/n) * sum((p_i - y_i)^2). Lower is better; perfect calibration + perfect discrimination gives BS = 0; random prediction at base rate r gives BS = r(1-r). Brier score decomposes into calibration (reliability) and refinement (resolution) components: BS = Calibration - Resolution + Uncertainty. Low calibration component means the model's probabilities match empirical rates. Low resolution means the model's probability distribution is spread out (it discriminates well across examples). The Brier score rewards both good calibration and good discrimination, making it a more complete metric than AUC alone for probabilistic systems.

**How do you calibrate a model that predicts tomorrow's stock price (regression, not classification)?**
Calibration for regression is uncertainty quantification. A well-calibrated regression model's 90% prediction interval should contain the true value 90% of the time. Approaches: (a) quantile regression — train separate models for the 5th, 50th, and 95th percentiles; (b) conformal prediction — compute non-conformity scores on a calibration set and derive prediction intervals with coverage guarantees; (c) Bayesian regression — posterior predictive distribution gives calibrated uncertainty. Evaluate calibration with a coverage plot (for each stated coverage level α, what fraction of test points fall within the α-interval?) and mean interval width (narrower intervals for same coverage = better).

---

## 13. Best Practices

**Always hold out a dedicated calibration set.** Split data into train (60%), calibration (20%), and test (20%). Train the model on the training set, fit the calibrator on the calibration set (never re-use training or test data), and evaluate the calibrated model on the test set.

**Measure calibration before and after calibration correction.** Plot reliability diagrams for both the raw model and the calibrated model on the test set. Include ECE as a headline number in the model card alongside AUC.

**Include calibration in the production monitoring dashboard.** Add ECE to the weekly model health report alongside AUC/NDCG. Alert when ECE > 0.10. Treat calibration degradation as seriously as accuracy degradation — they affect different but equally critical aspects of model correctness.

**Document the threshold decision process.** Record in the model card: the cost ratio used, the target recall or precision, the validation set ECE, and the expected precision/recall at the chosen threshold. When the threshold is revisited (after a base-rate shift), this documentation prevents re-deriving the rationale from scratch.

**Test calibration separately for subgroups.** A model well-calibrated on the overall population may be poorly calibrated for small subgroups. Compute ECE for each important subgroup (high-value users, specific geographies, protected demographic groups) and alert when subgroup ECE diverges from overall ECE by more than 0.05.

---

## 14. Case Study

This cross-cutting file is referenced by the following case studies:

**[design_churn_prediction.md](../design_churn_prediction.md):** Churn probability drives intervention budget allocation — the marketing team assigns retention offers to customers above a probability threshold. ECE was measured at 0.22 on the initial LightGBM model (severely miscalibrated). After isotonic regression calibration on a 90-day holdout, ECE dropped to 0.04. The intervention budget was reallocated: 35% more budget went to the top-decile risk group, improving retention ROI by 22%.

**[design_credit_risk_scoring.md](../design_credit_risk_scoring.md):** Regulatory requirement: the decision threshold must correspond to a documented default probability (e.g., cutoff = "applicants with > 8% 24-month PD are declined"). Calibration is legally required, not optional. Platt scaling is applied monthly on the prior month's originations + outcomes cohort to adjust for shifts in credit environment. The scorecard model's ECE is reported in quarterly risk committee materials.

**[design_eta_prediction.md](../design_eta_prediction.md):** ETA uses quantile regression (p10 and p90 intervals) to communicate uncertainty to drivers and riders. The quantile model's calibration is measured as coverage: the p90 interval must contain the actual ETA at least 88-92% of the time (allowing 2pp margin). When coverage drops below 85%, the quantile model is retrained on recent trip data.

**[design_marketplace_matching.md](../design_marketplace_matching.md):** The demand forecasting sub-model outputs calibrated probability-of-surge estimates per zone. If miscalibrated (consistently over-predicting surge), the platform pre-positions too many drivers, increasing idle time and driver dissatisfaction. Weekly calibration checks against observed surge events ensure the forecast probabilities remain actionable.
