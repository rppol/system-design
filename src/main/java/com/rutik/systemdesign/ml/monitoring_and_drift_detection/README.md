# ML Model Monitoring and Drift Detection

## 1. Concept Overview

ML model monitoring is the practice of continuously observing a deployed model's inputs, outputs, and performance metrics to detect degradation, data distribution shifts, and operational failures before they cause significant business impact. Unlike traditional software where bugs are deterministic, ML models degrade silently — accuracy erodes gradually as the world changes in ways the training data no longer reflects.

Drift detection is the specific practice of statistically comparing current production data distributions against training or reference distributions. When distributions diverge significantly, the model's learned decision boundaries no longer apply to the data it is receiving, and predictions become unreliable.

Without monitoring, a model can silently underperform for weeks before a downstream business metric (revenue, churn, fraud loss) reveals the problem — at which point the damage is already done.

---

## 2. Intuition

Think of a weather forecasting model trained on historical data from the past decade. If climate patterns shift over the next five years (concept drift), the model's temperature predictions will increasingly miss. If the sensors start malfunctioning and reporting different ranges (data drift), the model may still look correct by its own internal metrics while being fed garbage inputs. Monitoring is the process of checking both the sensor readings (input data quality) and comparing forecast accuracy against observations (output/performance monitoring) on an ongoing basis.

One-line analogy: Monitoring is the canary in the coal mine — it detects invisible poisonous gases (distribution shift) before miners (users) are harmed.

Why it matters: A credit scoring model silently degrading by 5% AUC can cost millions in bad loans before the quarterly review catches it.

Key insight: In production, you cannot assume the test set distribution persists. The world changes; your model does not automatically follow.

---

## 3. Core Principles

**Separate infrastructure monitoring from model monitoring**: Infrastructure monitoring (CPU, memory, latency, error rates) is necessary but not sufficient. It tells you the model is running, not whether it is correct.

**Reference distribution is ground truth for drift**: Drift is always relative to a reference window (training data distribution or a healthy recent production window). Choose the reference carefully — it defines what "normal" means.

**Delayed labels require proxy metrics**: Ground truth labels often arrive hours or days after predictions (fraud confirmed after investigation, click-through measured after impression). Design proxy metrics (intermediate user actions, upstream feature statistics) that correlate with eventual label quality.

**Statistical significance matters**: A single anomalous day does not constitute drift. Statistical tests must account for sample size; small windows produce noisy estimates. Use rolling windows of at least 1,000–10,000 samples for reliable PSI/KS estimates.

**Alert on trend, not just threshold**: A PSI that rises from 0.05 to 0.19 over two weeks is more alarming than a single-day spike to 0.21 and recovery. Trend-based alerting catches gradual drift that threshold-based alerting misses.

---

## 4. Types / Architectures / Strategies

### Data Drift (Covariate Shift)
- Input distribution P(X) changes while P(Y|X) remains approximately constant
- Example: e-commerce model trained on desktop users; mobile users surge, feature distributions shift (smaller screen resolution, shorter session duration)
- Detection: statistical tests on individual features or multivariate tests on joint distribution
- Impact: model predictions are extrapolating outside its training manifold

### Concept Drift
- The relationship P(Y|X) changes — same inputs should now produce different outputs
- Example: fraud patterns change as fraudsters adapt to detection; "normal" transaction behavior changes during economic crisis
- Detection: requires labels (delayed or approximated via proxy); performance monitoring (AUC, precision, recall decline)
- Most dangerous type: cannot be detected from input statistics alone

### Label Drift
- The marginal output distribution P(Y) changes
- Example: seasonal effects shift fraud base rate from 0.1% to 0.5%; prediction score distribution looks stable but calibration breaks
- Detection: monitor prediction score distribution; compare to expected label rate when labels arrive

### Covariate Shift (subset of data drift)
- P(X) changes but P(Y|X) is stable — the model can still be accurate if it generalizes
- Example: training on users 18–35; deployment audience shifts to include 55+ users with similar purchase behavior but different feature values
- Response: importance weighting, domain adaptation, or targeted retraining on new demographic

### Feature Drift
- Individual features drift independently; root cause analysis needed to identify which features changed
- Example: a partner API changes encoding of a categorical feature; "US" → "United States"; model receives all-zero one-hot encoding for a critical feature

---

## 5. Architecture Diagrams

### Full Monitoring Pipeline

```
Production Traffic
        |
        v
+-------------------+
|  Serving Layer    |  (FastAPI / TorchServe)
+-------------------+
        |
        | Log every request: timestamp, input features,
        | prediction score, model version
        v
+-------------------+
|  Feature Store /  |  (Kafka topic, S3 event log)
|  Event Log        |
+-------------------+
        |
        v
+-------------------+     +--------------------+
|  Drift Detector   |<----|  Reference Dataset  |
|  (PSI, KS tests)  |     |  (training window  |
+-------------------+     |   or recent healthy|
        |                  |   production)      |
        |                  +--------------------+
        v
+-------------------+
|  Metrics Store    |  (Prometheus, InfluxDB)
+-------------------+
        |
        v
+-------------------+
|  Alerting         |  (PagerDuty, Slack)
|  & Dashboards     |  (Grafana)
+-------------------+
        |
        v (if drift confirmed)
+-------------------+
|  Retraining       |  (trigger pipeline, re-evaluate)
|  Pipeline         |
+-------------------+
```

### PSI Computation Flow

```
Reference Data (training)          Production Data (current window)
        |                                   |
        | Bin into k buckets                | Map to same buckets
        v                                   v
 expected_pct[i] = count_i / N_ref   actual_pct[i] = count_i / N_prod
        |                                   |
        +-----------------------------------+
                          |
                          v
          PSI = sum((actual_pct[i] - expected_pct[i])
                    * ln(actual_pct[i] / expected_pct[i]))
                          |
                          v
              PSI < 0.1: No change
         0.1 <= PSI < 0.2: Monitor closely
              PSI >= 0.2: Significant drift — alert
```

---

## 6. How It Works — Detailed Mechanics

### PSI Calculation

```python
from __future__ import annotations

import numpy as np
import pandas as pd
from scipy import stats

def compute_psi(
    reference: np.ndarray,
    production: np.ndarray,
    n_bins: int = 10,
    epsilon: float = 1e-6,
) -> float:
    """
    Population Stability Index.
    PSI < 0.1: no significant change
    0.1 <= PSI < 0.2: moderate change, monitor
    PSI >= 0.2: significant change, investigate
    """
    # Build bins from reference distribution
    breakpoints = np.percentile(reference, np.linspace(0, 100, n_bins + 1))
    breakpoints[0] = -np.inf
    breakpoints[-1] = np.inf

    ref_counts, _ = np.histogram(reference, bins=breakpoints)
    prod_counts, _ = np.histogram(production, bins=breakpoints)

    # Convert to percentages; add epsilon to avoid log(0)
    ref_pct = ref_counts / len(reference) + epsilon
    prod_pct = prod_counts / len(production) + epsilon

    psi = np.sum((prod_pct - ref_pct) * np.log(prod_pct / ref_pct))
    return float(psi)


def compute_ks_test(
    reference: np.ndarray,
    production: np.ndarray,
    significance_level: float = 0.05,
) -> dict[str, float | bool]:
    """
    Kolmogorov-Smirnov two-sample test for continuous features.
    Returns statistic, p_value, and drift flag.
    """
    ks_stat, p_value = stats.ks_2samp(reference, production)
    drift_detected = bool(p_value < significance_level)
    return {
        "ks_statistic": round(ks_stat, 4),
        "p_value": round(p_value, 6),
        "drift_detected": drift_detected,
    }


def compute_chi_squared_test(
    reference_counts: np.ndarray,
    production_counts: np.ndarray,
    significance_level: float = 0.05,
) -> dict[str, float | bool]:
    """
    Chi-squared test for categorical features.
    reference_counts and production_counts must have same length (one per category).
    """
    # Scale reference to same total as production
    scale = production_counts.sum() / reference_counts.sum()
    expected = reference_counts * scale

    chi2, p_value = stats.chisquare(f_obs=production_counts, f_exp=expected)
    drift_detected = bool(p_value < significance_level)
    return {
        "chi2_statistic": round(chi2, 4),
        "p_value": round(p_value, 6),
        "drift_detected": drift_detected,
    }
```

### Feature-Level Drift Monitor

```python
from dataclasses import dataclass
from typing import Callable
import logging

logger = logging.getLogger(__name__)

@dataclass
class FeatureDriftReport:
    feature_name: str
    test_type: str
    statistic: float
    p_value: float
    psi: float
    drift_detected: bool

class DriftMonitor:
    """Monitors a set of features for distribution drift."""

    def __init__(
        self,
        reference_df: pd.DataFrame,
        continuous_features: list[str],
        categorical_features: list[str],
        psi_threshold: float = 0.2,
        ks_significance: float = 0.05,
    ) -> None:
        self.reference_df = reference_df
        self.continuous_features = continuous_features
        self.categorical_features = categorical_features
        self.psi_threshold = psi_threshold
        self.ks_significance = ks_significance

    def run(self, production_df: pd.DataFrame) -> list[FeatureDriftReport]:
        reports: list[FeatureDriftReport] = []

        for feature in self.continuous_features:
            ref_vals = self.reference_df[feature].dropna().values
            prod_vals = production_df[feature].dropna().values

            psi = compute_psi(ref_vals, prod_vals)
            ks_result = compute_ks_test(ref_vals, prod_vals, self.ks_significance)

            drift = psi >= self.psi_threshold or ks_result["drift_detected"]
            reports.append(FeatureDriftReport(
                feature_name=feature,
                test_type="KS+PSI",
                statistic=float(ks_result["ks_statistic"]),
                p_value=float(ks_result["p_value"]),
                psi=psi,
                drift_detected=drift,
            ))
            if drift:
                logger.warning(
                    "Drift detected in feature '%s': PSI=%.3f, KS p-value=%.4f",
                    feature, psi, ks_result["p_value"],
                )

        for feature in self.categorical_features:
            ref_counts = self.reference_df[feature].value_counts().sort_index().values
            prod_counts = production_df[feature].value_counts().sort_index().values

            if len(ref_counts) != len(prod_counts):
                logger.error("Category set mismatch for feature '%s'", feature)
                continue

            chi2_result = compute_chi_squared_test(ref_counts, prod_counts)
            reports.append(FeatureDriftReport(
                feature_name=feature,
                test_type="Chi2",
                statistic=float(chi2_result["chi2_statistic"]),
                p_value=float(chi2_result["p_value"]),
                psi=0.0,  # PSI not computed for categorical here
                drift_detected=bool(chi2_result["drift_detected"]),
            ))

        return reports
```

### Prediction Score Distribution Monitor

```python
def monitor_score_distribution(
    reference_scores: np.ndarray,
    production_scores: np.ndarray,
    n_bins: int = 20,
) -> dict[str, float]:
    """
    Monitor model output distribution.
    Sudden shifts in prediction score distributions can indicate
    concept drift even before labels arrive.
    """
    psi = compute_psi(reference_scores, production_scores, n_bins=n_bins)
    ks_result = compute_ks_test(reference_scores, production_scores)

    mean_diff = abs(production_scores.mean() - reference_scores.mean())
    std_diff = abs(production_scores.std() - reference_scores.std())

    return {
        "score_psi": round(psi, 4),
        "score_ks_stat": ks_result["ks_statistic"],
        "score_ks_p_value": ks_result["p_value"],
        "mean_shift": round(mean_diff, 4),
        "std_shift": round(std_diff, 4),
        "drift_flag": psi >= 0.2 or bool(ks_result["drift_detected"]),
    }
```

### Proxy Metric Monitoring (Delayed Labels)

```python
def compute_click_through_proxy(
    predictions: np.ndarray,
    actions: np.ndarray,  # 1 if user clicked/converted, 0 otherwise
    threshold: float = 0.5,
    window_size: int = 1000,
) -> dict[str, float]:
    """
    When ground truth labels are delayed, use downstream user actions
    as a proxy for model quality. For a recommendation model, CTR
    on top-scored items is a proxy for recommendation quality.
    """
    high_confidence_mask = predictions >= threshold
    if high_confidence_mask.sum() == 0:
        return {"proxy_ctr": 0.0, "sample_size": 0}

    proxy_ctr = actions[high_confidence_mask].mean()
    return {
        "proxy_ctr": round(float(proxy_ctr), 4),
        "sample_size": int(high_confidence_mask.sum()),
        "high_confidence_fraction": round(float(high_confidence_mask.mean()), 4),
    }
```

---

## 7. Real-World Examples

**Spotify recommendation drift**: User listening behavior shifts seasonally (holiday music, summer genres). Spotify monitors input feature distributions (genre affinity vectors) with PSI daily. When PSI exceeds 0.15 for more than 3 consecutive days, an automatic retraining job triggers with the last 90 days of interaction data weighted toward the most recent 30 days.

**Stripe fraud detection**: Fraud patterns change as attackers adapt. Stripe monitors two proxy metrics: (1) chargeback rate (labels arrive 30–90 days late), (2) rule-based system agreement rate (fast proxy). When rule-based agreement drops 5%, an alert fires before chargebacks confirm the issue.

**Facebook ad ranking drift**: CTR (click-through rate) serves as a real-time proxy for model quality. A 10% relative drop in CTR triggers an automated investigation pipeline that compares feature distributions across 50+ input signals to identify the root cause. Shadow model predictions are logged for 72 hours before any model update, providing a controlled comparison.

**Amazon product search**: Query distribution shifts during Prime Day (unusual query volume, different product categories). Monitoring detects query length distribution shift (PSI = 0.31) 2 hours into Prime Day and routes traffic to a model fine-tuned on previous Prime Day data. Reversion to the standard model happens automatically 48 hours post-event.

---

## 8. Tradeoffs

| Monitoring Approach | Detection Speed | Label Required | False Positive Rate | Cost |
|---------------------|----------------|----------------|--------------------|----|
| Input feature drift (PSI/KS) | Fast (real-time) | No | Medium | Low |
| Prediction score drift | Fast (real-time) | No | Medium | Low |
| Performance monitoring | Slow (label delay) | Yes | Low | Medium |
| SHAP-based attribution drift | Medium | No | Low | High |
| Shadow model comparison | Slow (deploy new model) | Partial | Low | High |

| Statistical Test | Feature Type | Sensitivity | Sample Size Needed | Threshold |
|------------------|-------------|-------------|-------------------|-----------|
| KS test | Continuous | High | ~500+ | p < 0.05 |
| Chi-squared | Categorical | Moderate | ~200+ | p < 0.05 |
| PSI | Continuous/discrete | Configurable | ~1,000+ | > 0.2 |
| MMD (Maximum Mean Discrepancy) | Multivariate | High | ~1,000+ | Statistical |

---

## 9. When to Use / When NOT to Use

**Use PSI when:**
- Monitoring a single continuous or discretizable feature over time
- A simple, interpretable threshold (0.1/0.2) is needed for alerting
- Business stakeholders need to understand the drift metric easily

**Use KS test when:**
- Comparing two independent samples of continuous data
- Need a formal statistical test with a p-value (not just a heuristic threshold)
- Sample sizes are small enough that PSI binning is unreliable (< 500 samples)

**Use Chi-squared when:**
- Feature is categorical (country, device type, product category)
- Comparing observed frequencies to expected frequencies

**Use SHAP-based monitoring when:**
- Root cause analysis is needed (which features are driving the model's shifting behavior)
- Resources allow for SHAP computation on a sampled subset of production traffic (expensive)

**Do NOT rely solely on input drift monitoring when:**
- Concept drift is the primary risk (P(Y|X) changes, not P(X)) — input drift tests will not catch this
- Model is used for safety-critical decisions — always pair with downstream outcome monitoring

---

## 10. Common Pitfalls

**War story 1: PSI computed on too small a window gives noisy alerts.** A team set up hourly PSI monitoring with 100-sample windows. PSI values fluctuated between 0.05 and 0.35 for a stable feature, firing alerts multiple times per day. Root cause: PSI with 10 bins requires at least 1,000 samples for reliable estimates (each bin needs ~100 samples minimum). Fix: increased window to 24 hours (accumulating ~5,000 samples); alert noise dropped 90%.

**War story 2: Reference distribution from training set included data leakage.** A team used the full training dataset as the reference distribution for PSI. The training set contained rows from a historical data correction that was never applied in production. Every PSI computation showed artificial drift because the reference included corrected data that production never saw. Fix: use a "recent healthy production window" (last 7–14 days before a known good model deployment) as the reference, not the raw training set.

**Broken pattern: Not handling null rates separately from value distribution.**
```python
# BROKEN: dropna() before PSI hides null rate changes
psi = compute_psi(reference[feature].dropna(), production[feature].dropna())
# If null rate went from 2% to 25%, this PSI shows no change

# FIXED: monitor null rate separately
ref_null_rate = reference[feature].isna().mean()
prod_null_rate = production[feature].isna().mean()
null_rate_change = abs(prod_null_rate - ref_null_rate)
if null_rate_change > 0.05:
    alert(f"Null rate changed: {ref_null_rate:.2%} -> {prod_null_rate:.2%}")

# Then compute PSI on non-null values
psi = compute_psi(reference[feature].dropna(), production[feature].dropna())
```

**War story 3: Alerting on every feature independently causes alert fatigue.** A team with 150 features ran independent KS tests at p < 0.05. At any given time, 5% of tests fire by random chance (7–8 alerts per run). Operators stopped acknowledging alerts. Fix: applied Bonferroni correction (threshold = 0.05 / 150 = 0.00033); also prioritized high-importance features (top 20 by SHAP) for alerting, reducing alert volume by 80%.

**War story 4: No monitoring of prediction score distribution masks silent model failure.** A feature pipeline bug introduced a constant value (0.0) for an important feature that was previously normally distributed. Input feature PSI caught it on that feature, but the alert was suppressed due to a misconfigured rule. The prediction score distribution shifted measurably (PSI = 0.28) but no alert was set up for output distribution. Revenue impact ran for 6 days before a business analyst flagged the anomaly. Fix: always monitor prediction score distribution as a secondary check independent of feature drift.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| Evidently AI | Open-source monitoring | Feature drift, data quality, model performance reports |
| WhyLogs (whylabs) | Data logging | Statistical profiles, drift detection, lightweight |
| Arize AI | Commercial monitoring | Feature/prediction drift, explainability, retraining triggers |
| Fiddler AI | Commercial monitoring | Explainability, fairness monitoring, alert management |
| Grafana + Prometheus | Infrastructure + custom | Custom drift metrics pushed as Prometheus gauges |
| MLflow | Experiment tracking | Not drift-specific; use for performance metric tracking |
| Great Expectations | Data validation | Schema and distribution tests for batch data pipelines |
| Deepchecks | Open-source | Train/test/production comparison, drift, integrity checks |
| NannyML | Confidence-based monitoring | Estimates performance without labels (CBPE method) |
| SciPy | Statistical tests | KS test, Chi-squared; Python standard for custom drift |

---

## 12. Interview Questions with Answers

**Q: What is the difference between data drift and concept drift, and why does the distinction matter operationally?**
Data drift means the input distribution P(X) has changed — the model is receiving different kinds of inputs than it was trained on. Concept drift means the relationship P(Y|X) has changed — even with the same inputs, the correct output is different. The operational distinction matters because data drift is detectable from input statistics alone (PSI, KS tests) without labels, while concept drift requires observing actual outcomes (delayed labels or proxy metrics). A model suffering from data drift might still predict well if it generalizes; a model suffering from concept drift will predict incorrectly regardless of input distribution.

**Q: Explain PSI. What are the threshold interpretations, and what are its limitations?**
Population Stability Index measures the divergence between two distributions by comparing the percentage of samples in each bin: PSI = sum((actual_pct - expected_pct) * ln(actual_pct / expected_pct)). Thresholds: PSI < 0.1 indicates no meaningful change; 0.1–0.2 indicates moderate change worth monitoring; > 0.2 indicates significant drift requiring action. Limitations: PSI is sensitive to the number of bins and bin boundaries; small samples (< 500) produce noisy estimates; it aggregates across the distribution, potentially missing localized shifts in tails; it is not a formal statistical test with a p-value.

**Q: How do you monitor model performance when ground truth labels are delayed by days or weeks?**
Use proxy metrics that correlate with eventual outcomes and arrive faster. For fraud detection: chargeback rate (30–90 day delay) pairs with rule-based system agreement rate (instant) and declined transaction rate (1 day). For recommendation: eventual purchase rate (days) pairs with click-through rate (hours) and scroll depth (minutes). Formally, NannyML's Confidence-Based Performance Estimation (CBPE) estimates AUC from prediction score distributions without labels, using the empirical relationship between model confidence and accuracy. Always validate proxy metrics periodically against actual labels to confirm correlation has not itself drifted.

**Q: What is the Bonferroni correction and when should you apply it in drift monitoring?**
When running independent statistical tests on N features at significance level alpha, the probability of at least one false positive is 1 - (1 - alpha)^N, which approaches 1 for large N. For 100 features at alpha=0.05, ~5 will falsely trigger. The Bonferroni correction sets the individual test threshold to alpha/N, controlling the family-wise error rate at alpha. In monitoring with 150 features: use KS test threshold of 0.05/150 = 0.00033. Practically, also prioritize monitoring the top 20 features by SHAP importance, reducing both false positive volume and cognitive load.

**Q: How would you design a drift monitoring system for a real-time fraud detection model?**
The system has three layers. Layer 1 — input monitoring: compute PSI on top-20 SHAP features daily using 24-hour rolling windows vs a 30-day reference window; alert if any feature PSI > 0.2 or null rate changes > 5%. Layer 2 — output monitoring: monitor prediction score distribution PSI daily; monitor positive prediction rate (proportion of transactions flagged as fraud); sudden spikes or drops indicate issues. Layer 3 — performance monitoring: compute precision, recall, and AUC using labels from completed chargeback investigations (30-day lag); track week-over-week trend; alert on > 5% relative AUC decline. Retraining triggers: PSI > 0.25 on more than 3 features simultaneously, or AUC week-over-week decline > 5%, or chargeback rate increasing while model score distribution is stable (concept drift signal).

**Q: What statistical test would you use for a categorical feature with 50 categories, and why?**
Use the Chi-squared goodness-of-fit test with the reference distribution as expected frequencies (scaled to the production sample size). Chi-squared is appropriate for comparing observed categorical counts to expected counts and handles many categories well. PSI can also be adapted for categorical features (one bin per category) but loses its interpretability advantages. Important caveat: Chi-squared requires sufficient expected counts (typically >= 5) per category; for rare categories (< 5 expected occurrences), aggregate them into an "other" bucket or use Fisher's exact test for the sparse cells.

**Q: How do you handle the case where production data has new categories not seen during training?**
New categories not in training are a critical failure mode — the model will either error on encoding or silently map to a fallback (usually all-zero embedding), producing incorrect predictions. Detection: schema validation at the serving layer should check that all categorical values are in the known vocabulary and raise an alert on unknowns. Monitoring: track the rate of unknown category values as a special drift metric (out-of-vocabulary rate). Response: implement a fallback encoding (map to a generic "unknown" token trained during training); trigger model update to incorporate new categories in embedding vocabulary.

**Q: What is covariate shift and how is it different from concept drift?**
Covariate shift is a subtype of data drift where P(X) changes but P(Y|X) remains constant — the correct prediction rule has not changed, only the distribution of inputs has shifted. A model may still generalize correctly under covariate shift if it has learned the true causal relationship. Concept drift is fundamentally different: P(Y|X) changes, meaning the rule itself is wrong regardless of input distribution. Covariate shift can be addressed by importance weighting (up-weight production-like training samples in retraining). Concept drift requires fresh labeled data from the new environment.

**Q: How do you detect drift when you have only 200 new production samples per day?**
With small samples, PSI binning is unreliable (bins have too few observations). Strategies: (1) Use KS test instead of PSI — KS requires fewer samples and has a formal p-value. (2) Accumulate samples across multiple days (rolling 7-day or 14-day windows) to reach 1,000+ samples before computing PSI. (3) Monitor simpler summary statistics (mean, standard deviation, fraction above threshold) daily; these are reliable with smaller samples. (4) Use permutation tests or bootstrap confidence intervals for robust small-sample drift estimation.

**Q: What is SHAP-based drift monitoring and when does it add value over raw feature drift?**
SHAP-based drift monitoring computes SHAP values for a sample of production predictions and compares the feature attribution distributions to a reference. A feature can be individually stable in distribution but have a different relationship to the model output (e.g., a feature that was highly predictive now contributes near-zero SHAP value because its correlation with the target broke). This detects model behavioral drift — changes in which features the model is relying on — which raw PSI on inputs cannot capture. It is expensive (SHAP computation scales as O(features * samples)); apply to a 1–5% sample of traffic.

**Q: Describe a monitoring strategy for a model that does not return probabilities, only binary predictions.**
Without probability scores, standard prediction distribution monitoring is limited. Strategies: (1) Monitor positive prediction rate (proportion of 1s) as a proxy for prediction distribution stability — sudden changes indicate drift. (2) Monitor input feature distributions (PSI, KS) as leading indicators. (3) If any confidence proxy is available (distance to decision boundary in SVMs, leaf sample counts in tree models), use it as a surrogate score distribution. (4) Pair with downstream outcome monitoring (actual label rates) as a lagging indicator. (5) Consider adding probability calibration to the model pipeline (Platt scaling, isotonic regression) to enable richer monitoring.

---

## 13. Best Practices

- Always monitor both input features and prediction score distributions; neither alone is sufficient
- Use a recent healthy production window (7–14 days from last known-good deployment) as the reference distribution rather than the raw training dataset, which may contain historical anomalies
- Separate null rate monitoring from value distribution monitoring; a sudden spike in null rates is a data pipeline failure, not a distribution shift, and requires a different response
- Apply Bonferroni correction when testing many features independently; or limit alerting to the top 20 features by feature importance
- Set minimum window sizes before computing PSI: 1,000 samples for PSI with 10 bins; 500 samples for KS test
- Track drift metrics as time series in Prometheus/Grafana; trend-based detection catches gradual drift that threshold-based alerting misses
- Design proxy metrics at model design time, not after deployment; document the correlation between proxy and actual label quality
- Test monitoring infrastructure with synthetic drift (inject corrupted data in staging) before relying on it in production
- Store all monitoring metrics with model version tags; this enables retrospective analysis of which model version was affected when a drift event occurred

---

## 14. Case Study

### E-Commerce Price Prediction Model Drift Detection

**Problem**: An e-commerce platform ran a price optimization model (gradient boosted trees, XGBoost) trained on 18 months of historical listing data. The model predicted optimal listing prices for sellers. After a major competitor entered the market, prices across the platform began shifting, but the model continued suggesting pre-competitive prices, causing sellers to lose sales to cheaper competitor listings.

**Monitoring gaps identified**:
1. No input feature drift monitoring — competitor price feature was not being tracked
2. No prediction score monitoring — price predictions were point estimates with no distribution tracking
3. Performance monitoring relied solely on quarterly business reviews, with 90-day label delay (actual sales conversion rate)

**Monitoring system designed**:

```
Daily batch job runs on previous 24h production data:

Feature drift (PSI, n=10 bins):
  - listing_price_mean      PSI = 0.31  ALERT (>0.2)
  - competitor_price_delta  PSI = 0.44  ALERT (very significant)
  - category_avg_price      PSI = 0.19  MONITOR (0.1-0.2)
  - seller_rating           PSI = 0.02  OK

Prediction score distribution:
  - Predicted price PSI = 0.38           ALERT

Proxy metric (conversion rate proxy):
  - Add-to-cart rate on model-priced items: -22% week-over-week  ALERT
```

**Response procedure executed**:
1. PSI alert fired on Day 3 after competitor launch (competitor_price_delta PSI = 0.44)
2. Root cause identified: model had no competitor price feature; the drift was in correlated features (category_avg_price) that the model was using as proxies for competitive pricing
3. Emergency retraining triggered: added explicit competitor price features; retrained on last 60 days; validation AUC stable
4. New model deployed via canary (10% traffic) after 48 hours; conversion rate proxy recovered to within 5% of pre-drift baseline
5. Total recovery time: 5 days from first alert to full deployment

**Lessons learned**:
- Proxy metric (add-to-cart rate) was the fastest signal — fired 6 hours before PSI alert accumulated enough samples
- Monitoring only business metrics (sales volume) would have detected the issue 2–3 weeks later
- The competitor price feature was in the roadmap but not yet implemented; drift monitoring surfaced the priority gap
- Set up automatic retraining triggers for feature PSI > 0.3 sustained for 48+ hours; manual review required before deployment
