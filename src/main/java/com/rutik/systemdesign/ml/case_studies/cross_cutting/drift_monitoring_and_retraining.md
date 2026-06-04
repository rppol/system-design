# Drift Monitoring and Retraining

## 1. Concept Overview

ML models are static artifacts trained on historical data. The world is not static. When the statistical relationship between inputs and outputs changes after a model is deployed, the model degrades silently unless actively monitored. Drift monitoring is the discipline of detecting when this degradation is occurring; retraining is the response. Together they form the closed loop that keeps production ML systems accurate over time.

Drift takes two forms: data drift (the input distribution shifts) and concept drift (the relationship between inputs and outputs shifts). Both cause model degradation but require different remedies. Data drift may be resolved by retraining on recent data; concept drift may require new features, a different model architecture, or a fundamental problem re-framing.

---

## 2. Intuition

> A model is like a map drawn in 2022. The roads it shows are correct as of that year. By 2025, new roads have been built, some roads have been closed, and neighborhoods have changed names. The map still looks accurate but gives wrong directions.

Mental model: model quality is a function of the gap between the training distribution and the serving distribution. When the gap is small, the model is accurate. As distributions diverge over time, the gap grows and performance degrades. Monitoring measures the gap; retraining closes it.

**Key insight:** the most dangerous drift is silent drift — the model's input distribution shifts enough to degrade performance, but the degradation is not visible in any metric the team monitors. Silent drift is caught only by monitoring input distributions, not just output metrics.

Why it matters: a model that was 0.88 AUC at deployment and is now 0.71 AUC in production with no monitoring alert has been silently harming the business for months. Every day of silent drift is a day of suboptimal decisions. Monitoring converts silent drift into actionable alerts within hours.

---

## 3. Core Principles

**Monitor inputs, not just outputs.** Waiting for AUC to degrade in production means waiting for the model to make wrong decisions at scale for long enough that the errors accumulate into measurable outcome degradation. Input distribution monitoring catches drift earlier — before it causes outcome degradation.

**Monitor at multiple granularities.** Population-level PSI catches large shifts. Segment-level PSI catches localized shifts that are masked at the population level. Both are needed.

**Separate data drift from concept drift diagnostically.** Data drift: the distribution of X changes but the conditional distribution of Y|X does not. Concept drift: the relationship between X and Y changes (same features, different outcomes). Diagnostic: if the model's score distribution matches the training distribution but calibration is degraded, suspect concept drift. If the model's score distribution has shifted, suspect data drift.

**Retraining is not always the answer.** Retraining on drifted data may make things worse if the drift is transient (e.g., a holiday season anomaly). Always validate on a holdout from the target distribution before retraining into production.

**Label freshness determines retraining latency.** The minimum time between data cutoff and production deployment equals the minimum label latency plus training time. A fraud model labels outcomes in 72 hours (dispute resolution time) → minimum retraining cadence is 3 days. A churn model labels outcomes in 30 days → minimum retraining cadence is 30 days.

---

## 4. Types / Architectures / Strategies

### 4.1 Drift Taxonomy

| Drift Type | Definition | Symptom | Detection Method |
|---|---|---|---|
| Covariate / data drift | P(X) changes; P(Y\|X) unchanged | Feature distributions shift; model score shifts | PSI, KS test per feature |
| Concept drift | P(Y\|X) changes; P(X) stable | Calibration degrades; accuracy drops; feature SHAP values shift | Model output distribution + label comparison |
| Label shift / prior shift | P(Y) changes; P(X\|Y) unchanged | Event rate shifts; model underestimates new base rate | Compare predicted vs observed positive rate |
| Virtual drift | P(X) changes in irrelevant region | No accuracy degradation | Feature-level PSI; confirmed benign |
| Sudden drift | Abrupt change (new data source, fraud attack) | Immediate AUC drop | Alert within hours |
| Gradual drift | Slow shift over weeks/months | Gradual AUC decay | Weekly PSI trending |
| Recurring drift | Seasonal patterns that repeat | Predictable seasonal performance drop | Calendar-based monitoring |

### 4.2 Retraining Strategies

| Strategy | Trigger | Data used for retrain | Pros | Cons |
|---|---|---|---|---|
| Scheduled retraining | Time-based (daily/weekly/monthly) | Rolling window of recent data | Simple; predictable | May retrain when unnecessary |
| Drift-triggered retraining | PSI > threshold or AUC < threshold | Recent window + drift analysis | Retrains when needed | Requires monitoring infra |
| Continuous/online learning | Per-batch or per-example | Stream of recent examples | Freshest possible model | Harder to validate; training instability |
| Champion/challenger | Scheduled or drift-triggered | Recent window for challenger | Safe; challenger validated before ship | Slower to respond |

---

## 5. Architecture Diagrams

### Drift Monitoring Pipeline

```
Production Serving
        |
        | log feature values + predictions (sampled 5-10%)
        v
Feature Log (Kafka / S3)
        |
        v
Drift Computation Engine (Spark batch / Flink streaming)
        |
        +----PSI per feature (vs training baseline)
        +----KS test per numeric feature
        +----Chi-squared per categorical feature
        +----Score distribution shift (KS on model output)
        |
        v
Monitoring Dashboard (Grafana / custom)
        |
        +----Alert: PSI > 0.2 for any feature (P1)
        +----Alert: AUC < deployment AUC - 0.05 (P1, requires labels)
        +----Alert: Score distribution KS p < 0.001 (P2)
        +----Info: weekly calibration report (ECE)
        |
        v
Retraining Pipeline (triggered by alert or schedule)
        |
        +---- Data validation (Great Expectations checks)
        +---- Train on recent N-month window
        +---- Evaluate on time-based holdout
        +---- Champion/challenger shadow period (7 days)
        +---- Promote if challenger AUC >= champion AUC - 0.005
```

### Label Latency and Retraining Cadence

```
Event occurs      Label available    Train + validate    Deploy
     |                  |                  |                |
     +------ lag -------+--- training time -+-- shadow -----+
                                                            |
        Minimum retraining cadence = label lag + train time + shadow period
        Example (fraud): 72h + 4h + 7d = ~10 days minimum between data cutoff and production
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 PSI Computation (Primary Drift Metric)

```python
import numpy as np
import pandas as pd

def compute_psi(
    reference: np.ndarray,
    current: np.ndarray,
    n_bins: int = 10,
    eps: float = 1e-9,
) -> float:
    """
    Population Stability Index (PSI):
    PSI = sum((P_curr - P_ref) * log(P_curr / P_ref))
    < 0.1: stable. 0.1-0.2: moderate shift. > 0.2: significant shift.
    """
    # Use reference distribution to define bins
    bin_edges = np.percentile(reference, np.linspace(0, 100, n_bins + 1))
    bin_edges[0] = -np.inf
    bin_edges[-1] = np.inf

    ref_counts  = np.histogram(reference, bins=bin_edges)[0]
    curr_counts = np.histogram(current,   bins=bin_edges)[0]

    ref_pct  = (ref_counts  + eps) / len(reference)
    curr_pct = (curr_counts + eps) / len(current)

    psi = float(np.sum((curr_pct - ref_pct) * np.log(curr_pct / ref_pct)))
    return psi

def compute_feature_psi_report(
    df_reference: pd.DataFrame,
    df_current: pd.DataFrame,
    numeric_features: list[str],
    psi_alert_threshold: float = 0.2,
) -> pd.DataFrame:
    """Compute PSI for all numeric features and flag those exceeding threshold."""
    rows = []
    for feat in numeric_features:
        psi_val = compute_psi(
            df_reference[feat].dropna().values,
            df_current[feat].dropna().values,
        )
        rows.append({
            "feature": feat,
            "psi": psi_val,
            "severity": "high" if psi_val > 0.2 else ("medium" if psi_val > 0.1 else "low"),
            "alert": psi_val > psi_alert_threshold,
        })
    return pd.DataFrame(rows).sort_values("psi", ascending=False)
```

### 6.2 Score Distribution Monitoring

```python
from scipy import stats as scipy_stats
import numpy as np

def monitor_score_distribution(
    training_scores: np.ndarray,
    serving_scores: np.ndarray,  # recent window (last 7 days)
    ks_pvalue_threshold: float = 0.001,
) -> dict[str, float | bool]:
    """
    KS test on model score distribution vs training distribution.
    A shift in score distribution signals data drift even before labels are available.
    """
    ks_stat, ks_pval = scipy_stats.ks_2samp(training_scores, serving_scores)
    mean_shift = float(serving_scores.mean() - training_scores.mean())
    std_ratio  = float(serving_scores.std() / (training_scores.std() + 1e-9))

    return {
        "ks_statistic": float(ks_stat),
        "ks_pvalue": float(ks_pval),
        "mean_shift": mean_shift,
        "std_ratio": std_ratio,
        "alert": ks_pval < ks_pvalue_threshold or abs(mean_shift) > 0.05,
    }
```

### 6.3 Broken Pattern — Retraining Without Validation

```python
import lightgbm as lgb
import pandas as pd

# WRONG: automated retraining that overwrites production without validation
def retrain_and_deploy_wrong(new_data: pd.DataFrame, model_path: str) -> None:
    model = lgb.LGBMClassifier(n_estimators=300, random_state=42)
    model.fit(new_data.drop("label", axis=1), new_data["label"])
    model.booster_.save_model(model_path)  # overwrites production model directly
    # No holdout evaluation. No comparison to current model.
    # If new_data has schema issues, label errors, or is a transient anomaly,
    # the new model may be WORSE and is now in production with no rollback mechanism.
```

```python
# CORRECT: champion/challenger pattern with validation gate
from sklearn.metrics import roc_auc_score
import lightgbm as lgb
import numpy as np

def train_and_validate_challenger(
    train_df: pd.DataFrame,
    holdout_df: pd.DataFrame,  # time-based holdout, not random split
    champion_model: lgb.LGBMClassifier,
    feature_cols: list[str],
    label_col: str = "label",
    min_auc_diff: float = -0.005,  # challenger must be within 0.5pp of champion
) -> tuple[lgb.LGBMClassifier | None, dict[str, float]]:
    """Train challenger and only return it if it passes the quality gate."""
    challenger = lgb.LGBMClassifier(n_estimators=300, learning_rate=0.05, random_state=42)
    challenger.fit(train_df[feature_cols], train_df[label_col])

    X_holdout = holdout_df[feature_cols]
    y_holdout = holdout_df[label_col]

    champion_auc = roc_auc_score(y_holdout, champion_model.predict_proba(X_holdout)[:, 1])
    challenger_auc = roc_auc_score(y_holdout, challenger.predict_proba(X_holdout)[:, 1])

    diff = challenger_auc - champion_auc
    promote = diff >= min_auc_diff   # challenger is not significantly worse

    return (challenger if promote else None), {
        "champion_auc": champion_auc,
        "challenger_auc": challenger_auc,
        "diff": diff,
        "promoted": promote,
    }
```

### 6.4 Concept Drift Detection via Label Monitoring

```python
import numpy as np
from scipy import stats as scipy_stats

def detect_concept_drift(
    scores: np.ndarray,          # model scores for recent cohort
    labels: np.ndarray,          # ground truth for same cohort (available with lag)
    training_positive_rate: float,
    psi_threshold: float = 0.1,
) -> dict[str, float | bool]:
    """
    Compare observed positive rate to training positive rate.
    A shift indicates label shift (prior drift) or concept drift.
    Also check calibration ECE to distinguish the two.
    """
    observed_positive_rate = float(labels.mean())
    rate_shift = observed_positive_rate - training_positive_rate

    # Calibration check: are scores aligned with observed positive rate?
    n_bins = 10
    bin_edges = np.linspace(0, 1, n_bins + 1)
    ece = 0.0
    for i in range(n_bins):
        mask = (scores >= bin_edges[i]) & (scores < bin_edges[i + 1])
        if mask.sum() == 0:
            continue
        ece += mask.sum() / len(scores) * abs(scores[mask].mean() - labels[mask].mean())

    return {
        "observed_positive_rate": observed_positive_rate,
        "training_positive_rate": training_positive_rate,
        "rate_shift": rate_shift,
        "ece": ece,
        "label_shift_alert": abs(rate_shift) > 0.2 * training_positive_rate,  # >20% relative shift
        "calibration_alert": ece > 0.10,
    }
```

---

## 7. Real-World Examples

**Instacart (COVID-19, March 2020):** demand for grocery delivery spiked 300% in two weeks. All demand forecasting models trained on pre-COVID data became severely miscalibrated — they predicted 10-20% of actual demand. The models showed no data drift alerts (input features were the same: time of day, day of week, category) but concept drift was extreme (the P(Y|X) relationship between day-of-week and order volume changed fundamentally). Instacart's response: manual override weights applied to all model outputs; emergency retraining on the most recent 2 weeks of data; transition to shorter-horizon forecasts until the new distribution stabilized.

**JPMorgan Chase (fraud):** runs hourly retraining of fraud models. Label lag for fraud is 48-72 hours (dispute resolution time). The training pipeline uses a rolling 30-day window updated hourly with the most recent labeled transactions. PSI monitoring on transaction features fires alerts within 2 hours of a new fraud pattern (new merchant category, new device type) appearing. Champion/challenger with a 24-hour shadow period is the promotion gate.

**Netflix (recommendation):** monitors score distribution for each user cohort weekly. When a major content release (e.g., new season of a hit show) causes a large shift in viewing patterns, the recommendation model's score distribution shifts because the feature space changes (new show IDs appearing as viewed content). This is detected as data drift but is benign — it is expected behavior during a content event. The monitoring system includes context annotations (scheduled events, marketing campaigns) that suppress false alerts during known perturbation periods.

**LinkedIn (people you may know):** uses a scheduled weekly retrain for the graph-based recommendation model. The trigger for emergency (off-schedule) retraining is: model output AUC on a daily labeled validation set drops below (deployment_AUC - 0.03). The validation set is constructed daily from the previous day's connection requests and their outcomes (accepted vs not). This gives a daily model quality signal without waiting for offline batch evaluation.

**DoorDash (delivery time estimation):** ETA models have strong seasonal drift patterns (weather, holidays, restaurant busy periods). Monitoring includes a weather-conditioned model quality report: on rainy days, the model's MAE is expected to increase; an alert fires only when the increase exceeds the historical rainy-day baseline by more than 2 standard deviations. This reduces alert fatigue from predictable seasonal patterns while catching genuine degradation.

---

## 8. Tradeoffs

### Retraining Frequency vs Stability

| Cadence | Freshness | Stability | Risk |
|---|---|---|---|
| Daily | Very high | Low (noisy data) | Transient drift causes unnecessary retraining |
| Weekly | High | Medium | Moderate lag for sudden drift |
| Monthly | Low | High | Slow to respond; significant degradation possible |
| Event-triggered | Optimal | Variable | Requires robust monitoring and automation |

### Monitoring Coverage vs Alert Fatigue

Too many metrics → too many alerts → alert fatigue → alerts are ignored → drift goes undetected.
Too few metrics → silent drift → no alerts → degradation accumulates undetected.

Target: 3-5 primary metrics monitored at high frequency (score distribution, PSI on top-10 features, positive rate). 10-20 secondary metrics monitored weekly with a weekly summary digest rather than individual alerts.

---

## 9. When to Use / When NOT to Use

**Always monitor when:**
- The model makes decisions that affect revenue, safety, or user experience.
- The model was trained on historical data and deployed in a changing environment.
- Label latency > 0 (cannot immediately verify all predictions).

**Monitor more aggressively when:**
- Data distribution is known to be unstable (fraud, financial markets, news-driven demand).
- The model affects a system with feedback loops (recommendations influencing future behavior).
- The model operates in a high-stakes domain (credit, healthcare, fraud).

**Monitoring can be lighter when:**
- The model is retrained frequently (daily) and monitored via training data freshness.
- The problem domain is stable (e.g., image classification of fixed categories in a controlled environment).
- The model is a simple rule-based system with no learned parameters.

---

## 10. Common Pitfalls

**Monitoring only output metrics (AUC, accuracy).** Output metrics require labels, which arrive with a lag (hours to months depending on the outcome). By the time AUC degradation is visible, the model may have been making wrong decisions for weeks. Monitor input distributions (PSI) in near-real-time — no label lag required.

**PSI computed on a single global bucket.** A feature may be stable overall (global PSI < 0.1) but severely drifted for a specific segment (high-value users: PSI = 0.35). Global PSI masks segment-level drift. Compute PSI separately for major segments (user tier, geography, product category) and report the max as the headline metric.

**Retraining on the wrong data window.** If a sudden fraud attack occurred in the last 7 days, retraining on a 7-day rolling window includes the attack data, which may be unrepresentative of the post-attack steady state. Or if the last 30 days include a holiday spike, the retrained model will underperform in non-holiday periods. Always inspect the training window visually before automated retraining; include data validation checks (Great Expectations) that fail the pipeline if unusual data is detected.

**Deploying a challenger without shadow validation.** Training a challenger on recent data and immediately promoting it to production without a shadow period is dangerous. The challenger may have regressed on a specific segment that the global AUC metric misses. Run the challenger in shadow mode (score production traffic but do not use the score for decisions) for at least 7 days before promotion.

**Ignoring the feedback loop.** When a model's predictions drive actions (fraud alerts, churn interventions), those actions change the labels in future training data. A model that flags more fraud → more fraud is disputed → more negative feedback → model sees more "not-fraud" labels for the cases it was most confident were fraud. This is the label feedback loop: it systematically biases retraining toward lower recall over time. Detection: track the fraction of model-alerted cases that received an intervention vs those that did not; compare outcomes across both groups using causal inference methods.

---

## 11. Technologies & Tools

| Tool | Purpose | Strengths | Notes |
|---|---|---|---|
| Evidently AI | Drift monitoring + reports | PSI, KS, data quality checks; HTML reports; Grafana plugin | Open source; production-ready |
| WhyLabs (whylogs) | Logging + drift profiling | Lightweight logging library; cloud aggregation | Sends to WhyLabs cloud; open core |
| Arize AI | Drift + performance monitoring | Schema inference, embedding drift, SHAP-over-time | Managed SaaS |
| Great Expectations | Data quality validation | Schema checks, value range, column statistics | Training pipeline validation gate |
| Prometheus + Grafana | Metrics collection + visualization | Time series alerts, custom metrics | Infrastructure monitoring; add ML metrics |
| MLflow | Experiment tracking + model registry | Champion/challenger versioning, deployment history | Self-hosted or Databricks managed |
| Kubeflow Pipelines | Retraining automation | DAG orchestration for train/validate/deploy pipelines | Kubernetes-native; complex setup |

---

## 12. Interview Questions with Answers

**What is the difference between data drift and concept drift?**
Data drift (covariate shift) means the distribution of input features X changes: P(X) at serving time differs from P(X) at training time. The conditional distribution P(Y|X) is unchanged — the model's learned relationship is still correct for the features it receives, but the features themselves have shifted. Concept drift means P(Y|X) changes — the same feature values now predict a different outcome. Examples: data drift — a new user demographic starts using the app and their feature values differ from historical users. Concept drift — customer price sensitivity changed after a recession; the same income level now predicts lower purchase probability than it did during training. Diagnosis: data drift shows up as input PSI alerts; concept drift shows up as calibration degradation or AUC drop on a feature-stable cohort.

**How do you choose between PSI and KS test for drift detection?**
Both are valid but measure different aspects. PSI (Population Stability Index) is a symmetric divergence measure designed for monitoring: it uses fixed bins derived from the reference distribution and produces a single number that is intuitive (< 0.1 stable, > 0.2 significant). It is preferred for production dashboards because it is interpretable, directional, and aggregates well across bins. KS test is a statistical hypothesis test that measures the maximum difference between empirical CDFs. It gives a p-value (is the shift statistically significant?) and a test statistic (how large is the shift?). KS test is preferred when you need a formal hypothesis test with a known Type I error rate. In practice: use PSI for continuous monitoring and alerting (threshold-based); use KS test for formal drift analysis when investigating a flagged alert.

**What is the champion/challenger pattern in model deployment?**
Champion/challenger is a deployment pattern where a new model (challenger) is deployed alongside the existing model (champion) in shadow mode for a validation period before promotion. The challenger receives the same inputs as the champion and generates predictions, but its predictions are not used to make decisions — they are compared against the champion's predictions and evaluated against ground truth as labels arrive. After the shadow period (7-14 days typically), if the challenger's metric (AUC, RMSE, calibration) meets or exceeds the champion's metric by more than the minimum threshold, the challenger is promoted to champion. Benefits: validates the challenger on live production data (not just historical); catches regressions on specific segments that may not appear in offline evaluation; provides a rollback path (champion is still serving).

**How do you handle label latency in a fraud model?**
Fraud label latency is 48-72 hours (dispute resolution time). This means: (1) training data is only available for transactions older than 72 hours — very recent predictions cannot be evaluated immediately; (2) retraining cadence is limited by the label lag (retraining daily makes no sense if labels arrive every 3 days — you would retrain on the same labeled data); (3) monitoring must rely on unlabeled drift signals (score distribution, input PSI) for recent data, and labeled performance metrics (AUC, precision/recall) for data older than 72 hours. In practice: PSI and score distribution monitored hourly (no label required); AUC/calibration computed daily on the rolling 3-7 day labeled cohort; retraining triggered by either PSI alert or AUC degradation, with a minimum cadence of weekly.

**Describe how you would detect a label feedback loop in a recommendation system.**
A label feedback loop occurs when the model's predictions drive which items users see (and thus can label), making future training labels non-representative of the true item quality distribution. Detection: (1) track the relationship between model score rank and label rate — in a fair world, labels should be available for items across the full score distribution; if only high-scoring items receive labels (because only high-scoring items are shown), labels are selection-biased. (2) Compute the correlation between item position in the recommendation list and label rate — a strong position bias (top-ranked items get more clicks regardless of quality) indicates feedback loop. (3) Introduce a random exploration set (1-5% of recommendations are random) and compare label rates between the model-selected and random sets. If the model-selected set has much higher label rates, selection bias is present. Mitigation: inverse propensity scoring (IPS) to downweight common items and upweight rare items in the training signal.

**What is PSI and how do you compute it?**
Population Stability Index (PSI) measures distributional shift between a reference distribution (training data) and a current distribution (serving data): PSI = sum[(P_curr_i - P_ref_i) × log(P_curr_i / P_ref_i)] over bins i. Computation steps: (1) bin the reference distribution into 10 equal-frequency bins using reference quantiles as bin edges; (2) compute the proportion of reference and current observations in each bin; (3) for each bin, compute (P_curr - P_ref) × log(P_curr / P_ref); (4) sum across bins. Interpretation: PSI < 0.1 = no significant shift; 0.1-0.2 = moderate shift, monitor closely; > 0.2 = significant shift, investigate and consider retraining. Add a small epsilon (1e-9) to prevent division by zero for empty bins. The symmetric log-ratio form makes PSI slightly different from KL divergence (asymmetric) — PSI captures both directions of shift equally.

**How do you set up automated retraining that is safe to run without human review?**
Four gates must pass before any automated promotion: (1) data quality gate — Great Expectations checks confirm that training data schema, value ranges, and cardinality are within expected bounds (no new unknown categories, no NaN rate explosion, no date gaps); fail the pipeline if any check fails. (2) Training success gate — loss curve is stable (no explosion or NaN), final training loss is within expected range. (3) Holdout validation gate — challenger AUC on a time-based holdout is no more than 0.5pp below the current champion's AUC; if the challenger is significantly worse, do not promote and alert for human review. (4) Shadow validation gate — during a 7-day shadow period, challenger's live score distribution matches expectations (no extreme outliers, similar PSI to champion's scores). If all four gates pass, automatically promote; otherwise, page the on-call ML engineer.

**How do you monitor a model that serves predictions in real-time with no label available for hours?**
Three strategies without immediate labels: (1) input monitoring — compute feature PSI daily vs training baseline; score distribution KS test hourly; alert on significant shifts. (2) Consistency monitoring — compare current predictions to the same model's predictions on a held-out set of "canary" inputs (fixed, known examples with stable true labels). If the model suddenly assigns a different score to a canary input, something has changed (model version, feature computation, or upstream data). (3) Proxy output monitoring — for classification, monitor prediction score distribution, mean score, and fraction of high-confidence predictions; any large shift is an alert. For regression, monitor predicted value distribution. These provide early warning; label-based metrics (AUC, calibration) validate the alert once labels arrive.

**What is virtual drift and how do you avoid false alerts from it?**
Virtual drift occurs when the input distribution shifts (triggering PSI alerts) but the shift is in a region of feature space where the model makes very few predictions — so the model's actual performance is unaffected. Example: a new user cohort with unusual age distribution joins the platform, triggering PSI alert on the "age" feature. But the model's predictions for this cohort are actually accurate because the new cohort's behavior (purchase patterns, session length) closely matches an existing cohort. Virtual drift causes alert fatigue if not handled. Mitigation: compute PSI only on the model-relevant feature distribution (weight each observation by its influence on the model score, e.g., by SHAP value magnitude); alert only if score distribution PSI also exceeds threshold (virtual drift does not shift the score distribution if the model treats the drifted region similarly to the training region).

**When would you choose online learning over periodic retraining?**
Online learning (updating the model on each new batch or example, e.g., FTRL for logistic regression, per-batch gradient updates for neural networks) is appropriate when: (a) the data distribution changes continuously and rapidly (click prediction, ad ranking — user behavior changes hour by hour); (b) the model must respond to new patterns within minutes, not days; (c) the feature space and label are available at near-real-time. Online learning is not appropriate when: (a) the model architecture requires batch training (tree-based models do not support online updates natively); (b) label latency is > 1 hour (online learning on stale labels causes instability); (c) the team lacks infrastructure to validate continuous model updates safely (online learning failures are hard to rollback). Most production systems use periodic retraining (daily/weekly) rather than online learning because it is safer and the accuracy improvement from online learning rarely justifies the operational complexity.

**How do you distinguish between a bug in the data pipeline and genuine concept drift?**
Sudden, large drift (PSI > 0.5 for many features simultaneously, or a score distribution mean shift of > 20% within a day) is more likely a pipeline bug than genuine drift. Genuine drift is gradual (weeks) unless caused by a major external event (COVID-19, a new competitor, regulatory change). Diagnostic steps: (1) check upstream data sources — are raw event counts normal? Is the Kafka consumer lag growing? Did a data schema change occur? (2) Check feature computation — did the Spark/Flink feature pipeline run successfully and on schedule? (3) Check for time gaps — is there missing data for any period that would cause rolling aggregates to be incorrect? If the drift appeared suddenly on a specific date, look for engineering changes (code deploy, schema migration, pipeline restart) on that date. Only after ruling out pipeline issues should you treat the shift as genuine concept drift.

**What monitoring would you add to a churn prediction model and what are the SLOs?**
Feature monitoring: PSI on top-10 features daily. SLO: PSI < 0.2 for all features; alert if any feature > 0.2 for 3 consecutive days. Score monitoring: KS test on daily score distribution vs training distribution. SLO: KS p-value > 0.001; alert immediately if p < 0.001. Label monitoring (30-day lag): ECE on the prior 30-day cohort's labeled outcomes monthly. SLO: ECE < 0.10. Positive rate monitoring: compare predicted positive rate to observed positive rate in the prior month's cohort. SLO: abs(predicted_rate - observed_rate) / predicted_rate < 0.20 (within 20% relative). AUC monitoring: quarterly backtesting on historical data with temporal validation. SLO: AUC on recent 90-day cohort ≥ deployment_AUC - 0.03. Retraining trigger: any P1 alert (PSI > 0.2, ECE > 0.15, AUC degradation > 0.03) triggers a challenger retrain within 24 hours.

---

## 13. Best Practices

**Establish baseline distributions at training time.** At the moment of model training, snapshot the distribution of every feature (mean, std, min, max, percentiles, unique count for categoricals) and save it with the model artifact. These snapshots are the reference distributions for future PSI computation. Without a saved reference, PSI must be computed against a recent baseline window, which conflates drift detection with moving-window comparison.

**Alert on score distribution before labels arrive.** The model's output score distribution is the earliest available signal of drift — it requires no label, no latency. A shift in the score distribution (mean, variance, or shape) indicates either data drift or a model issue. Monitor score distribution daily and alert on KS test significance as the first early-warning layer.

**Never retrain on a single anomalous period.** If drift is triggered by a one-week holiday anomaly, retraining on the most recent 30 days (which heavily weights the holiday) will produce a model that is miscalibrated for non-holiday periods. Always inspect the training data window visually and use data validation to exclude anomalous periods, or include a longer window (90 days) that dilutes the anomaly.

**Version every model artifact.** The model binary, the training data snapshot, the feature preprocessing pipeline, and the calibration layer must all be versioned together. A rollback after a bad retrain must revert all four simultaneously. Use a model registry (MLflow, Sagemaker Model Registry) that enforces this artifact bundle.

**Test your monitoring before you need it.** Simulate drift in a staging environment by feeding artificially shifted feature distributions and verify that alerts fire within the expected time. Also test that automated retraining pipelines complete successfully end-to-end in the staging environment monthly. Monitoring that has never been verified in a realistic drill is likely to fail when genuinely needed.

---

## 14. Case Study

This cross-cutting file is referenced by the following case studies:

**[design_churn_prediction.md](../design_churn_prediction.md):** Churn model drift monitoring operates on a 30-day label lag. Input feature PSI is computed daily on sampled serving logs. Retraining is triggered when PSI > 0.2 for any of the top-5 SHAP features, or when the monthly calibration check shows ECE > 0.10 on the labeled cohort. Retraining uses a rolling 12-month window to capture seasonal patterns without being dominated by short-term anomalies.

**[design_credit_risk_scoring.md](../design_credit_risk_scoring.md):** Regulatory requirement: any model parameter change (retraining) must go through model risk management review before deployment. Automated retraining is not permitted. Instead, the monitoring pipeline generates a monthly "model health report" that triggers a formal review if AUC on the current cohort drops below the deployment baseline by more than 2pp. Retraining requires sign-off from model risk management, compliance, and the model owner.

**[design_eta_prediction.md](../design_eta_prediction.md):** ETA model drift has strong seasonal components (weather, time-of-day, day-of-week). Monitoring accounts for this: PSI is computed conditional on day-of-week and weather condition to separate seasonal patterns from genuine drift. The alert threshold is calibrated using 2 years of historical drift patterns per (day-of-week, weather) combination.

**[design_marketplace_matching.md](../design_marketplace_matching.md):** Matching model drift is monitored via supply/demand ratio monitoring — if the predicted supply availability in a zone deviates from observed supply by > 15%, an alert fires. This is faster than waiting for label-based model metrics (trip completion rates available within the same hour) and provides sub-hour response to supply shocks.
