# Design an Anomaly Detection System

## Problem Statement

Design an anomaly detection system for a cloud infrastructure monitoring platform (Datadog/Grafana scale).
The system ingests 100K+ time series metrics per second (CPU utilization, memory usage, request latency,
error rates, disk I/O) from hundreds of thousands of hosts. Anomalies must be surfaced within 60 seconds
of occurrence. The false positive rate must stay below 0.1% to prevent alert fatigue. The system must
catch both well-known anomaly patterns (sudden spikes, gradual drift) and novel patterns never seen before.

### Functional Requirements
- Ingest 100K metrics/second in real time
- Detect anomalies within 60 seconds of occurrence
- Support multi-variate correlation: if CPU and latency both spike, attribute to one root cause
- Provide anomaly scores (not just binary labels) for prioritization
- Alert suppression: deduplicate correlated alerts, 5-minute cooldown per metric

### Non-Functional Requirements
- False positive rate: < 0.1%
- False negative rate: < 5% for amplitude anomalies > 3 standard deviations
- Throughput: 100K metrics/sec sustained ingestion
- Detection latency: < 60 seconds end-to-end
- Model retraining: weekly, with rolling online updates for drift

### Out of Scope
- Root cause analysis (separate causal inference service)
- Log and trace anomaly detection (different pipeline)

---

## Architecture Overview

```
                        Metrics Sources
          (hosts, containers, services, databases)
                              |
                    100K metrics/sec
                              |
                              v
              +-------------------------------+
              |         Kafka Cluster         |
              |   8 partitions, 3 replicas    |
              |   retention: 7 days           |
              +-------------------------------+
                              |
              +---------------+---------------+
              |               |               |
              v               v               v
        [Flink Job 1]   [Flink Job 2]   [Flink Job 3]
        Normalization   Feature Ext.    Seasonal Adj.
        z-score         lag, rolling    STL decomp.
        rolling 1h      windows         (trend+seasonal
        window                           +residual)
              |               |               |
              +---------------+---------------+
                              |
                              v
              +-------------------------------+
              |      Anomaly Scoring Layer    |
              |                               |
              |  [3-Sigma Rule]  -> fast       |
              |  [CUSUM]         -> drift      |
              |  [Isolation Forest] -> general |
              |  [Autoencoder]   -> complex    |
              |  [Prophet Baseline] -> seasonal|
              +-------------------------------+
                              |
                              v
              +-------------------------------+
              |       Alert Correlation        |
              |  - graph-based grouping        |
              |  - 5-min dedup cooldown        |
              |  - severity scoring            |
              +-------------------------------+
                              |
                    +---------+---------+
                    |                   |
                    v                   v
             [PagerDuty]         [Internal UI]
             [Slack webhook]     [Grafana alerts]


Storage Layer:
  InfluxDB (raw metrics, 30-day hot retention)
  Parquet on S3 (cold storage, 2 years)
  Redis (model scores cache, 5-min TTL)
  PostgreSQL (alert history, suppression state)
```

---

## Key Design Decisions

### 1. Layered Detection Pipeline (Fast Path + Slow Path)

Fast path (< 5 seconds): 3-sigma rule on rolling z-score catches gross anomalies immediately.
Slow path (< 60 seconds): Isolation Forest and Autoencoder run on 15-minute feature windows.
Rationale: 3-sigma catches obvious spikes cheaply; ML models catch subtle patterns (gradual drift,
multivariate anomalies) that statistics miss.

### 2. STL Decomposition for Seasonal Adjustment

Cloud metrics are highly seasonal (daily/weekly cycles). Running anomaly detection on raw values
generates false positives every morning when CPU naturally rises. STL decomposes:
- Trend: long-term drift (retraining signal)
- Seasonal: expected periodic pattern (subtracted before detection)
- Residual: true anomaly signal

Anomaly detection runs on the residual, eliminating seasonal false positives.

### 3. Isolation Forest as Primary Unsupervised Detector

No labeled anomaly dataset exists at ingest time. Isolation Forest requires no labels, is fast at
inference (O(log n) per sample), handles high-dimensional feature vectors, and produces interpretable
anomaly scores. Contamination parameter = 0.01 (expect 1% anomalies). Retrained weekly on last 30
days of normal behavior.

### 4. Autoencoder for Novel Pattern Detection

Trained exclusively on normal traffic. High reconstruction error signals an unknown anomaly pattern.
Threshold set at 99th percentile of reconstruction error on validation normal data. Catches correlated
multi-metric anomalies (e.g., memory leak pattern: memory up + GC time up + latency up) that
univariate detectors miss.

### 5. Alert Correlation via Metric Graph

When 200 metrics spike simultaneously (e.g., host failure), naive detection fires 200 alerts.
Correlation layer builds a similarity graph on anomaly timestamps and scores. Connected components
with > 3 co-occurring anomalies are grouped into one incident. Reduces alert volume by 95% during
infrastructure incidents.

### 6. CUSUM for Slow Drift Detection

CUSUM (Cumulative Sum Control Chart) detects gradual drift that 3-sigma misses (each individual
point is within bounds, but the cumulative direction is clearly anomalous). Critical for detecting
memory leaks, slow disk degradation, and traffic migration patterns.

---

## Implementation

```python
from __future__ import annotations

import numpy as np
import pandas as pd
from dataclasses import dataclass, field
from typing import Optional
from sklearn.ensemble import IsolationForest
from statsmodels.tsa.seasonal import STL
import torch
import torch.nn as nn


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class MetricPoint:
    metric_name: str
    host: str
    timestamp: float          # Unix epoch seconds
    value: float
    tags: dict[str, str] = field(default_factory=dict)


@dataclass
class AnomalyResult:
    metric_name: str
    host: str
    timestamp: float
    anomaly_score: float      # 0.0 = normal, 1.0 = certain anomaly
    detector: str
    residual: Optional[float] = None
    reconstruction_error: Optional[float] = None


# ---------------------------------------------------------------------------
# STL Decomposition + 3-Sigma
# ---------------------------------------------------------------------------

class STLAnomalyDetector:
    """
    Decomposes time series into trend + seasonal + residual using STL.
    Applies 3-sigma rule on residuals to flag anomalies.
    Eliminates false positives from daily/weekly seasonality.
    """

    def __init__(
        self,
        period: int = 1440,          # 1440 minutes = 1 day for minute-resolution data
        sigma_threshold: float = 3.0,
        robust: bool = True,         # robust=True: resistant to outliers in decomposition
    ) -> None:
        self.period = period
        self.sigma_threshold = sigma_threshold
        self.robust = robust

    def detect(self, series: pd.Series) -> list[AnomalyResult]:
        """
        series: pd.Series with DatetimeIndex, at least 2*period points required.
        Returns list of AnomalyResult for detected anomalies.
        """
        if len(series) < 2 * self.period:
            # Fall back to simple z-score when not enough history
            return self._zscore_fallback(series)

        stl = STL(series, period=self.period, robust=self.robust)
        result = stl.fit()
        residual = result.resid

        mu = residual.mean()
        sigma = residual.std()
        if sigma == 0:
            return []

        z_scores = (residual - mu) / sigma
        anomalies = []
        for ts, z, res_val in zip(series.index, z_scores, residual):
            if abs(z) > self.sigma_threshold:
                score = min(1.0, abs(z) / (self.sigma_threshold * 2))
                anomalies.append(AnomalyResult(
                    metric_name="unknown",
                    host="unknown",
                    timestamp=ts.timestamp(),
                    anomaly_score=score,
                    detector="stl_3sigma",
                    residual=float(res_val),
                ))
        return anomalies

    def _zscore_fallback(self, series: pd.Series) -> list[AnomalyResult]:
        mu, sigma = series.mean(), series.std()
        if sigma == 0:
            return []
        anomalies = []
        for ts, val in series.items():
            z = abs((val - mu) / sigma)
            if z > self.sigma_threshold:
                anomalies.append(AnomalyResult(
                    metric_name="unknown",
                    host="unknown",
                    timestamp=ts.timestamp(),
                    anomaly_score=min(1.0, z / (self.sigma_threshold * 2)),
                    detector="zscore_fallback",
                ))
        return anomalies


# ---------------------------------------------------------------------------
# CUSUM Detector
# ---------------------------------------------------------------------------

class CUSUMDetector:
    """
    Cumulative Sum control chart for detecting gradual drift.
    Maintains two accumulators: one for upward drift, one for downward.
    Triggers when cumulative deviation exceeds threshold h.

    Parameters:
        k: slack value (allowance for natural variation), typically 0.5 * sigma
        h: decision threshold, typically 4-5 * sigma
    """

    def __init__(self, k: float = 0.5, h: float = 5.0) -> None:
        self.k = k
        self.h = h
        self._cusum_pos: float = 0.0
        self._cusum_neg: float = 0.0
        self._mu: float = 0.0
        self._sigma: float = 1.0

    def fit(self, baseline: np.ndarray) -> None:
        """Estimate mu and sigma from normal baseline data."""
        self._mu = float(np.mean(baseline))
        self._sigma = float(np.std(baseline)) or 1.0

    def update(self, value: float) -> Optional[AnomalyResult]:
        """
        Process one new value. Returns AnomalyResult if drift detected, else None.
        Resets accumulators after alarm to avoid sustained false alerts.
        """
        z = (value - self._mu) / self._sigma
        self._cusum_pos = max(0.0, self._cusum_pos + z - self.k)
        self._cusum_neg = max(0.0, self._cusum_neg - z - self.k)

        if self._cusum_pos > self.h or self._cusum_neg > self.h:
            score = min(1.0, max(self._cusum_pos, self._cusum_neg) / (self.h * 2))
            # Reset to prevent repeated alarms for same event
            self._cusum_pos = 0.0
            self._cusum_neg = 0.0
            return AnomalyResult(
                metric_name="unknown",
                host="unknown",
                timestamp=0.0,
                anomaly_score=score,
                detector="cusum",
            )
        return None


# ---------------------------------------------------------------------------
# Isolation Forest Detector
# ---------------------------------------------------------------------------

class IsolationForestDetector:
    """
    Unsupervised anomaly detection using Isolation Forest.
    Trained on feature vectors extracted from normal metric windows.
    Contamination=0.01: expects ~1% anomalies in unlabeled training data.
    No labels required — ideal for infrastructure monitoring.

    Feature vector per metric window (15-minute):
        [mean, std, min, max, p50, p95, p99, trend_slope, range, cv]
    """

    def __init__(
        self,
        n_estimators: int = 200,
        contamination: float = 0.01,
        max_samples: int = 256,      # subsample size per tree, default in sklearn
        random_state: int = 42,
    ) -> None:
        self.model = IsolationForest(
            n_estimators=n_estimators,
            contamination=contamination,
            max_samples=max_samples,
            random_state=random_state,
            n_jobs=-1,               # use all CPUs for training
        )
        self._fitted = False

    @staticmethod
    def extract_features(window: np.ndarray) -> np.ndarray:
        """
        Extract statistical features from a metric window.
        window: 1-D array of metric values (e.g., 15 minutes at 1-sec resolution = 900 points)
        """
        if len(window) == 0:
            return np.zeros(10)

        mean = np.mean(window)
        std = np.std(window)
        p50 = np.percentile(window, 50)
        p95 = np.percentile(window, 95)
        p99 = np.percentile(window, 99)
        minimum = np.min(window)
        maximum = np.max(window)
        value_range = maximum - minimum
        # Coefficient of variation (relative variability)
        cv = std / (mean + 1e-9)
        # Linear trend slope via least squares
        x = np.arange(len(window))
        slope = float(np.polyfit(x, window, 1)[0]) if len(window) > 1 else 0.0

        return np.array([mean, std, minimum, maximum, p50, p95, p99, slope, value_range, cv])

    def fit(self, windows: list[np.ndarray]) -> None:
        """
        Train on a list of normal metric windows.
        Expects ~30 days of data per metric at 15-min window resolution = ~2880 windows.
        """
        feature_matrix = np.vstack([self.extract_features(w) for w in windows])
        self.model.fit(feature_matrix)
        self._fitted = True

    def score(self, window: np.ndarray) -> float:
        """
        Returns anomaly score in [0, 1]. Higher = more anomalous.
        Isolation Forest raw score: -1 (anomaly) to 1 (normal).
        We map to [0, 1] where 1 = definite anomaly.
        """
        if not self._fitted:
            raise RuntimeError("Detector not fitted. Call fit() first.")
        features = self.extract_features(window).reshape(1, -1)
        raw_score = self.model.decision_function(features)[0]
        # decision_function: lower = more anomalous; typically in [-0.5, 0.5]
        # Map: score 0 = normal, 1 = anomaly
        anomaly_score = max(0.0, min(1.0, (-raw_score + 0.5)))
        return anomaly_score


# ---------------------------------------------------------------------------
# Autoencoder Anomaly Detector
# ---------------------------------------------------------------------------

class MetricAutoencoder(nn.Module):
    """
    Autoencoder trained exclusively on normal metric behavior.
    Input: flattened feature vector (10-dim from IsolationForestDetector.extract_features).
    High reconstruction error (MSE) signals novel anomaly.

    Architecture: 10 -> 64 -> 32 -> 8 -> 32 -> 64 -> 10
    Bottleneck dimension 8 forces learning of compressed normal representation.
    """

    def __init__(self, input_dim: int = 10) -> None:
        super().__init__()
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, 64),
            nn.ReLU(),
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Linear(32, 8),       # bottleneck
        )
        self.decoder = nn.Sequential(
            nn.Linear(8, 32),
            nn.ReLU(),
            nn.Linear(32, 64),
            nn.ReLU(),
            nn.Linear(64, input_dim),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.decoder(self.encoder(x))


class AutoencoderDetector:
    """
    Wrapper around MetricAutoencoder for anomaly scoring.
    Threshold: 99th percentile of reconstruction error on normal validation data.
    Trained only on normal data — anomalies have no gradient influence.
    """

    def __init__(
        self,
        input_dim: int = 10,
        lr: float = 1e-3,
        epochs: int = 50,
        batch_size: int = 256,
    ) -> None:
        self.model = MetricAutoencoder(input_dim=input_dim)
        self.optimizer = torch.optim.Adam(self.model.parameters(), lr=lr)
        self.criterion = nn.MSELoss()
        self.epochs = epochs
        self.batch_size = batch_size
        self.threshold: float = float("inf")
        self._fitted = False

    def fit(self, normal_windows: list[np.ndarray]) -> None:
        """
        Train on normal windows. Extract features, train AE, set threshold.
        normal_windows: list of numpy arrays, each a 15-minute metric window.
        """
        feature_matrix = np.vstack([
            IsolationForestDetector.extract_features(w) for w in normal_windows
        ]).astype(np.float32)

        # Normalize features to [0, 1] for stable training
        self._feature_min = feature_matrix.min(axis=0)
        self._feature_max = feature_matrix.max(axis=0)
        feature_range = self._feature_max - self._feature_min + 1e-9
        normalized = (feature_matrix - self._feature_min) / feature_range

        tensor_data = torch.from_numpy(normalized)
        dataset = torch.utils.data.TensorDataset(tensor_data)
        loader = torch.utils.data.DataLoader(dataset, batch_size=self.batch_size, shuffle=True)

        self.model.train()
        for epoch in range(self.epochs):
            for (batch,) in loader:
                self.optimizer.zero_grad()
                reconstructed = self.model(batch)
                loss = self.criterion(reconstructed, batch)
                loss.backward()
                self.optimizer.step()

        # Set threshold at 99th percentile of reconstruction errors on training data
        self.model.eval()
        with torch.no_grad():
            reconstructed = self.model(tensor_data)
            errors = ((reconstructed - tensor_data) ** 2).mean(dim=1).numpy()
        self.threshold = float(np.percentile(errors, 99))
        self._fitted = True

    def score(self, window: np.ndarray) -> float:
        """Returns anomaly score in [0, 1]."""
        if not self._fitted:
            raise RuntimeError("Detector not fitted.")
        features = IsolationForestDetector.extract_features(window).astype(np.float32)
        feature_range = self._feature_max - self._feature_min + 1e-9
        normalized = (features - self._feature_min) / feature_range
        tensor = torch.from_numpy(normalized).unsqueeze(0)

        self.model.eval()
        with torch.no_grad():
            reconstructed = self.model(tensor)
            error = float(((reconstructed - tensor) ** 2).mean().item())

        # Score: how many multiples of threshold
        return min(1.0, error / (self.threshold + 1e-9))


# ---------------------------------------------------------------------------
# Alert Correlation
# ---------------------------------------------------------------------------

class AlertCorrelator:
    """
    Groups co-occurring anomalies into incidents to reduce alert noise.
    Two anomalies are correlated if they occur within time_window seconds of each other.
    Uses Union-Find for efficient connected component grouping.
    Applies 5-minute deduplication cooldown per (metric, host) pair.
    """

    def __init__(
        self,
        time_window_seconds: float = 30.0,
        min_group_size: int = 3,
        cooldown_seconds: float = 300.0,   # 5-minute cooldown
    ) -> None:
        self.time_window = time_window_seconds
        self.min_group_size = min_group_size
        self.cooldown = cooldown_seconds
        self._last_alert: dict[str, float] = {}   # key: (metric, host), value: last alert ts

    def correlate(
        self, anomalies: list[AnomalyResult], current_time: float
    ) -> list[list[AnomalyResult]]:
        """
        Groups anomalies into correlated incidents.
        Filters out alerts still in cooldown.
        Returns list of groups; groups with >= min_group_size are collapsed to one incident.
        """
        # Apply cooldown filter
        active = []
        for a in anomalies:
            key = f"{a.metric_name}:{a.host}"
            last = self._last_alert.get(key, 0.0)
            if current_time - last >= self.cooldown:
                active.append(a)
                self._last_alert[key] = current_time

        if not active:
            return []

        # Build groups by timestamp proximity (simple O(n^2) for small batches)
        n = len(active)
        parent = list(range(n))

        def find(x: int) -> int:
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x

        def union(x: int, y: int) -> None:
            parent[find(x)] = find(y)

        for i in range(n):
            for j in range(i + 1, n):
                if abs(active[i].timestamp - active[j].timestamp) <= self.time_window:
                    union(i, j)

        groups: dict[int, list[AnomalyResult]] = {}
        for i, a in enumerate(active):
            root = find(i)
            groups.setdefault(root, []).append(a)

        return list(groups.values())


# ---------------------------------------------------------------------------
# Orchestrator
# ---------------------------------------------------------------------------

class AnomalyDetectionPipeline:
    """
    Orchestrates all detectors. In production, Flink calls score() for each
    15-minute window as a tumbling window operation.
    """

    def __init__(self) -> None:
        self.stl_detector = STLAnomalyDetector(period=1440, sigma_threshold=3.0)
        self.cusum_detectors: dict[str, CUSUMDetector] = {}
        self.if_detector = IsolationForestDetector(n_estimators=200, contamination=0.01)
        self.ae_detector = AutoencoderDetector(input_dim=10, epochs=50)
        self.correlator = AlertCorrelator(time_window_seconds=30.0, cooldown_seconds=300.0)

    def score_window(
        self, metric_name: str, host: str, window: np.ndarray, timestamp: float
    ) -> AnomalyResult:
        """
        Score a 15-minute window of metric data.
        Returns the highest-scoring anomaly result across all detectors.
        """
        if_score = self.if_detector.score(window) if self.if_detector._fitted else 0.0
        ae_score = self.ae_detector.score(window) if self.ae_detector._fitted else 0.0

        # Ensemble: max score with detector label
        if if_score >= ae_score:
            score, detector = if_score, "isolation_forest"
        else:
            score, detector = ae_score, "autoencoder"

        return AnomalyResult(
            metric_name=metric_name,
            host=host,
            timestamp=timestamp,
            anomaly_score=score,
            detector=detector,
        )
```

---

## ML Components Used

| Component | Purpose | Key Parameters |
|-----------|---------|----------------|
| Apache Kafka | Metric ingestion buffer | 8 partitions, 3 replicas, 7-day retention |
| Apache Flink | Streaming feature extraction | 15-min tumbling windows, parallelism=32 |
| STL Decomposition | Seasonal adjustment | period=1440 (daily), robust=True |
| 3-Sigma Rule | Fast path: gross spike detection | threshold=3.0 sigma |
| CUSUM | Slow drift detection | k=0.5, h=5.0 sigma |
| Isolation Forest | General unsupervised anomaly detection | n_estimators=200, contamination=0.01 |
| Autoencoder | Novel pattern / multivariate detection | 10->64->32->8 bottleneck, MSE threshold at p99 |
| Prophet | Seasonal baseline forecasting (optional) | weekly + daily seasonality |
| InfluxDB | Hot metric storage | 30-day retention, 100K writes/sec |
| Redis | Anomaly score cache | 5-min TTL, eliminates redundant scoring |
| PostgreSQL | Alert history and suppression state | cooldown tracking per metric/host |

---

## Tradeoffs and Alternatives

| Decision | Chosen Approach | Alternative | Reason for Choice |
|----------|----------------|-------------|------------------|
| Primary detector | Isolation Forest | One-class SVM | IF is O(log n) inference vs O(n) for OCSVM; scales to 100K metrics/sec |
| Seasonal adjustment | STL decomposition | Facebook Prophet | STL is 10x faster for streaming; Prophet better for long-horizon forecasting |
| Drift detection | CUSUM | ADWIN | CUSUM is simpler to tune; ADWIN better for concept drift in non-stationary series |
| Ensemble method | Max score | Weighted average | Max score is conservative (high recall); weighted average gives more false positives |
| Alert grouping | Timestamp proximity graph | ML-based clustering | Proximity is deterministic and debuggable; ML clustering adds latency |
| Storage (hot) | InfluxDB | TimescaleDB | InfluxDB purpose-built for time series; TimescaleDB better for SQL join requirements |
| Training cadence | Weekly retrain + online CUSUM | Continuous learning | Weekly retrain prevents concept drift without training instability |

### False Positive Control Strategy

The 0.1% FPR target is achieved through defense in depth:
1. Seasonal adjustment (STL) eliminates ~60% of naive false positives from daily cycles
2. Threshold calibration: Isolation Forest contamination=0.01 tuned on 30-day baseline
3. Alert deduplication: 5-minute cooldown prevents alarm storms
4. Correlated alert grouping: co-occurring anomalies grouped into one incident

---

## Interview Discussion Points

**Q: How do you handle a new metric with no historical baseline?**
A: Cold start problem. Apply a 24-hour observation window collecting data before enabling ML
detectors. During the window, use only conservative 5-sigma rule on rolling z-score. After 24 hours,
fit CUSUM and Isolation Forest on collected data. After 7 days, enable Autoencoder and STL.
New metrics are tagged as "bootstrapping" and alerts are suppressed except for extreme deviations.

**Q: How do you prevent the model from treating a new normal as anomalous?**
A: Concept drift. CUSUM resets its baseline when cumulative drift persists for > 6 hours (sustained
shift, not a spike). Isolation Forest is retrained weekly on last 30 days — new normal is included
after 1 week. Autoencoder threshold is recalculated on the validation set at each weekly retrain.
The key insight: drift changes the baseline gradually; the model must relearn the baseline without
forgetting extreme values are still anomalous.

**Q: How do you handle correlated metrics causing alert storms during incidents?**
A: Three layers. First, timestamp-proximity grouping collapses co-occurring anomalies within 30
seconds into one incident. Second, 5-minute cooldown suppresses repeat alerts per metric.
Third, graph analysis identifies common infrastructure ancestor (same host, same AZ) and attributes
all anomalies to one root incident. During a large incident (entire AZ down), this reduces 10,000
individual metric alerts to 1 incident with severity proportional to the count of affected metrics.

**Q: How would you evaluate detection quality without ground truth labels?**
A: Multi-pronged evaluation. Precision: manually label a sample of 500 alerts per week (15 minutes
of on-call engineer time) — track precision@500. Recall: inject synthetic anomalies (chaos
engineering: CPU spike, memory leak simulation) and measure detection rate and latency. Drift:
track alert volume over time — sudden increase suggests model drift or changed system behavior.
False positive rate: track alert-to-incident ratio; alerts that are acknowledged and marked "not
an issue" count as false positives.

**Q: Why Isolation Forest over supervised methods like XGBoost on labeled data?**
A: Label scarcity and distribution shift. In infrastructure monitoring, fewer than 0.1% of metric
windows are true anomalies. Collecting enough labels for supervised training requires months of
on-call annotation. Worse, infrastructure changes monthly — labeled anomalies from Q1 (memory
leak in old service) may not represent Q3 anomalies. Isolation Forest adapts to whatever "normal"
is in the last 30 days. Supervised methods are used as a second pass when labels accumulate
for specific known failure modes (e.g., "database connection pool exhaustion" pattern).

---

## Failure Scenarios and Recovery

### Failure 1: Autoencoder Reconstruction Threshold Set Too Low During Traffic Migration

**What failed:** A major infrastructure migration moved 40% of the fleet from bare-metal to containerized workloads. Containerization changed CPU utilization patterns significantly: containers showed higher micro-burst frequency (10-100ms bursts every few seconds) and lower sustained utilization. The Autoencoder had been trained on bare-metal telemetry only and had not seen this pattern. Its reconstruction error for container metrics was consistently above the p99 training threshold, triggering 47,000 alerts over 8 hours. On-call engineers were overwhelmed; true incidents were buried in false positives and went undetected for 3 hours.

**Detection:** Alert volume spike to 5,800/hour (baseline: 120/hour) triggered an alert-volume anomaly detector (meta-monitoring). Time-to-detect the threshold problem: 25 minutes after alert storm began.

**Recovery steps:**
1. Immediately disabled Autoencoder detection for containerized hosts, reverting to 3-sigma + CUSUM only.
2. Collected 7 days of container telemetry, trained a separate Autoencoder on container patterns.
3. Deployed infrastructure-type conditional: Autoencoder model routing based on host_type label (bare-metal vs container).
4. Added pre-deployment validation: when training data composition changes by > 20% in any infrastructure category, flag for human review before deploying the new model.

**Prevention:** Infrastructure migration events trigger automatic "observation mode" for anomaly detection: increase thresholds by 2x, disable all except 5-sigma detection, collect new-environment data for 7 days, then retrain and validate before returning to normal sensitivity.

---

### Failure 2: CUSUM Accumulated State Not Reset After Scheduled Maintenance Windows

**What failed:** A 4-hour maintenance window caused legitimate CPU spikes (deployments, restarts, health checks) across 2,000 hosts. CUSUM's cumulative sum accumulated a large positive value during the maintenance window. When maintenance ended and systems returned to normal, CUSUM's internal state was already far above the alarm threshold — it interpreted the end-of-maintenance return to normal as a continuation of an anomaly. CUSUM was stuck in "alarming" state for 6 hours after maintenance ended, suppressing legitimate alerts for new anomalies that occurred post-maintenance.

**Detection:** On-call engineers noticed CUSUM was still generating alerts for metrics that had clearly returned to normal (visual inspection on Grafana). Time-to-detect: 2 hours after maintenance window ended.

**Recovery steps:**
1. CUSUM state is now reset to zero at the start and end of scheduled maintenance windows (maintenance window metadata from the CMDB is streamed to the anomaly detection service via Kafka).
2. Added a "maintenance mode" where 3-sigma fast path still runs (for catching real failures during maintenance) but CUSUM and Autoencoder are paused.
3. Added automatic CUSUM state audit: if CUSUM state remains above 0.8× threshold for > 1 hour after an alert fires, and the raw metric has returned to baseline, force a CUSUM reset.

**Prevention:** Integrate with the deployment and maintenance scheduling system. Any event tagged as "planned maintenance" automatically triggers anomaly detector state reset at event start and end.

---

### Failure 3: Kafka Consumer Lag Causing 60-Second Detection Delay to Inflate to 8 Minutes

**What failed:** The Flink streaming job processing metric data from Kafka fell behind during a traffic spike. Kafka consumer lag reached 500,000 messages (approximately 5 seconds of data at 100K metrics/sec). Processing rate was 95K messages/sec (5% below ingestion rate) due to STL decomposition being CPU-bound. The lag compounded: after 30 minutes, the processing was 8 minutes behind real time. Anomalies were detected 8 minutes late — the 60-second SLA was violated for 2 hours. Two real infrastructure incidents were detected after their cascade had already caused user-visible impact.

**Detection:** Flink lag monitoring alerted at 100K message lag (30-second delay threshold). Time-to-detect the lag: 8 minutes. Time-to-detect that detection was delayed: after the fact, from post-mortem.

**Recovery steps:**
1. Profiled Flink job: STL decomposition consumed 70% of CPU. Moved STL to a daily precompute (deseasonalized baseline computed offline), replacing the streaming STL with a simple baseline subtraction using the precomputed seasonal component. STL streaming CPU dropped from 70% to 15%.
2. Added Kafka consumer lag alert at 50K messages (15-second delay) — earlier than the previous 100K threshold.
3. Enabled Flink autoscaling: task managers scale from 32 to 64 when lag > 50K messages.

**Prevention:** Processing throughput must be >= 1.3× peak ingestion rate (30% headroom) measured on the 99th percentile of historical ingestion spikes. This is validated quarterly with a load test.

---

## Capacity Planning

### Data Volume Projections

```
Year 0 (current):
  Ingestion: 100K metrics/sec × 30 days × 86400 sec = 259B metric data points/month
  InfluxDB (30-day hot): 259B × 16 bytes/point = 4.14TB hot storage
  Parquet cold (2yr): 259B × 12 months = 3.1T points/yr × 2yr × 16B = 99TB/yr
  Kafka: 100K × 100 bytes/msg = 10MB/sec sustained, 7-day retention = 6TB total

Year 1 (50% fleet growth):
  150K metrics/sec, 6.2TB InfluxDB hot, 9TB Kafka
  Flink task managers: scale from 32 to 48

Year 3 (3x growth — new data centers, more services):
  300K metrics/sec, 12.4TB InfluxDB hot
  Parquet cold: ~300TB/yr
  May require InfluxDB cluster expansion: from 6-node to 12-node cluster
```

### Training Compute Requirements

```
Isolation Forest Weekly Retrain:
  Dataset: 30-day rolling window × 100K metrics × 15-min windows
  = 100K metrics × 2,880 windows/day × 30 days = 8.64B samples (subsample 1% = 86.4M)
  Hardware: c5.4xlarge (16 vCPU, 32GB RAM)
  Duration: 3 hours (parallel sklearn with n_jobs=-1)
  Cost: $0.68/hr × 3hr = $2.04/week = $106/month

Autoencoder Weekly Retrain:
  Dataset: same 86.4M subsampled windows, each as (1024-dim feature vector)
  Hardware: 1× A10G GPU (g5.xlarge)
  Duration: 4 hours (early stopping typical at epoch 50)
  Cost: $1.006/hr × 4hr = $4.02/week = $209/month

CUSUM Parameters Re-optimization (monthly):
  Grid search over k ∈ [0.25, 0.5, 1.0] × h ∈ [3, 5, 7] on labeled anomaly set
  CPU-only: 1 hour on c5.2xlarge
  Cost: $0.34/hr × 1hr = $0.34/month ≈ negligible

Total monthly training cost: ~$315
```

### Serving Infrastructure

```
Flink Streaming (feature extraction):
  100K metrics/sec, 15-minute tumbling windows
  32 task managers (c5.xlarge, 4 vCPU each): 3,125 metrics/task manager
  Cost: 32 × $0.17/hr = $5.44/hr = ~$3,917/month

Anomaly Scoring Service:
  100K scores/sec across 5 detectors
  3-sigma and CUSUM: in-process, no additional infrastructure
  Isolation Forest: serialized model loaded in-memory (100MB), 10ms inference per batch of 1K
  Autoencoder: GPU inference, 1× g5.xlarge handles 50K samples/sec
  Cost: 1 × $1.006/hr = ~$724/month

InfluxDB Cluster:
  6-node cluster (r5.2xlarge, 64GB RAM): 100K writes/sec, 4.14TB hot storage
  Cost: 6 × $0.504/hr = $3.03/hr = ~$2,182/month

Redis (anomaly score cache, 5-min TTL):
  100K active metrics × 500B per score = 50MB working set
  1× r5.large: trivially small
  Cost: $0.126/hr = ~$91/month

PostgreSQL (alert history + suppression):
  Alert volume: 1,200/hr baseline, 50GB/year
  1× r5.large: adequate
  Cost: $0.126/hr = ~$91/month

Kafka Cluster:
  10MB/sec ingestion, 7-day retention = 6TB
  5-broker cluster (kafka.m5.2xlarge)
  Cost: ~$600/month

Total monthly serving infrastructure: ~$7,605
```

---

## Additional War Stories

**War Story 1 — Isolation Forest Contamination Parameter Causing Alert Volume Collapse:**

```python
# BROKEN: contamination=0.001 (0.1%) underestimates real anomaly rate
# If true anomaly rate is 0.5%, Isolation Forest misclassifies 80% of anomalies as normal
# Alert volume drops 80% — appears to be "improvement" but is actually missed detections

from sklearn.ensemble import IsolationForest
import numpy as np


def train_isolation_forest_broken(
    X_train: np.ndarray,
) -> IsolationForest:
    """BROKEN: contamination too low — misses most true anomalies."""
    model = IsolationForest(
        n_estimators=200,
        contamination=0.001,  # BUG: too low; assumes only 0.1% anomalies
        random_state=42,
        n_jobs=-1,
    )
    model.fit(X_train)
    # model.score_samples() returns lower values for anomalies
    # decision_function < 0 = anomaly; threshold calibrated for contamination=0.001
    # At true anomaly rate 0.5%, 80% of true anomalies have score above threshold
    return model


# FIX: Calibrate contamination using actual labeled anomaly rate from historical data
# Or use decision function raw scores and tune threshold independently on a labeled set

def train_isolation_forest_correct(
    X_train: np.ndarray,
    X_labeled: np.ndarray,
    y_labeled: np.ndarray,  # 1=anomaly, 0=normal
    target_fpr: float = 0.001,  # 0.1% FPR target
) -> tuple["IsolationForest", float]:
    """
    Train Isolation Forest with threshold tuned on labeled set.
    Do NOT rely on contamination parameter for threshold — tune explicitly.
    """
    model = IsolationForest(
        n_estimators=200,
        contamination="auto",  # auto: does not use contamination for threshold
        random_state=42,
        n_jobs=-1,
    )
    model.fit(X_train)

    # Score labeled set and find threshold that achieves target FPR
    scores = model.score_samples(X_labeled)  # lower = more anomalous

    normal_scores = scores[y_labeled == 0]
    # At target_fpr, the threshold is the (fpr * 100)th percentile of normal scores
    # i.e., only top-fpr fraction of normal instances score below threshold
    threshold = float(np.percentile(normal_scores, target_fpr * 100))

    tp = ((scores[y_labeled == 1] < threshold)).sum()
    fp = ((scores[y_labeled == 0] < threshold)).sum()
    recall = tp / max((y_labeled == 1).sum(), 1)
    actual_fpr = fp / max((y_labeled == 0).sum(), 1)

    print(f"Threshold: {threshold:.4f}")
    print(f"Recall: {recall:.4f}, Actual FPR: {actual_fpr:.4f} (target: {target_fpr:.4f})")
    return model, threshold
```

**War Story 2 — Autoencoder Training on Biased Normal Data Including Slow Leaks:**

```python
# BROKEN: Training autoencoder on "normal" data that includes slow memory leaks
# A memory leak growing 0.1% per hour looks "normal" over any single hour
# Autoencoder learns to reconstruct the slowly-growing pattern as normal
# When leak accelerates (0.5% per hour), score stays low — missed

import torch
import torch.nn as nn
import numpy as np
from typing import Optional


def train_autoencoder_broken(
    normal_data: np.ndarray,  # BUG: contains slow drift that is actually anomalous
    epochs: int = 100,
) -> nn.Module:
    """Broken: training data includes gradual drift patterns labeled as 'normal'."""
    model = Autoencoder(input_dim=normal_data.shape[1])
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    X = torch.FloatTensor(normal_data)

    for epoch in range(epochs):
        model.train()
        recon = model(X)
        loss = nn.MSELoss()(recon, X)
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()

    return model  # will not detect slow memory leaks it was trained on


# FIX: Detrend training data before autoencoder training
# CUSUM is better suited for drift; Autoencoder for sudden structural changes
# Autoencoder trains on STL residuals (seasonal + trend removed), not raw metrics

def train_autoencoder_on_residuals_correct(
    raw_data: np.ndarray,  # (T, num_metrics) raw metric time series
    seasonal_period: int = 1440,  # 1440 * 1-min intervals = 1 day
    epochs: int = 100,
) -> tuple[nn.Module, float]:
    """
    Train autoencoder on STL residuals to isolate structural anomalies
    from trend and seasonal components. Autoencoder learns 'normal' residual
    patterns, not 'normal' levels.
    """
    from statsmodels.tsa.seasonal import STL

    residuals = np.zeros_like(raw_data)
    for i in range(raw_data.shape[1]):
        try:
            stl = STL(raw_data[:, i], period=seasonal_period, robust=True)
            result = stl.fit()
            residuals[:, i] = result.resid
        except Exception:
            residuals[:, i] = raw_data[:, i] - raw_data[:, i].mean()

    # Normalize residuals per feature (z-score)
    residual_mean = residuals.mean(axis=0)
    residual_std = residuals.std(axis=0) + 1e-8
    normalized = (residuals - residual_mean) / residual_std

    model = Autoencoder(input_dim=normalized.shape[1])
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    X = torch.FloatTensor(normalized)

    for epoch in range(epochs):
        model.train()
        recon = model(X)
        loss = nn.MSELoss()(recon, X)
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()

    # Compute reconstruction threshold from training data (p99 of reconstruction error)
    model.eval()
    with torch.no_grad():
        train_recon = model(X)
        train_errors = ((train_recon - X) ** 2).mean(dim=1).numpy()
    threshold = float(np.percentile(train_errors, 99))

    return model, threshold
```

---

## Monitoring and Drift Detection Deep-Dive

### Metrics That Drift Fastest

```
Feature / Signal                  Drift rate   Reason
──────────────────────────────────────────────────────────────────────────
CPU utilization baseline          High         New deployments change service behavior
Request rate patterns             High         Product launches, viral events, seasonality
Memory leak rate (slow drift)     Moderate     Appears as gradual increase; often weeks
STL seasonal component            Moderate     Business hours shift with timezone, seasons
Disk I/O patterns                 Moderate     Database growth, new indexes, vacuum jobs
Error rate baseline               High         Code deployments change error behavior
Network traffic distribution      Moderate     CDN routing changes, new services launch
GC pause frequency (JVM)          Low          Changes with heap growth or GC tuning
```

### PSI Monitoring for Anomaly Detection Features

```python
# PSI monitoring for anomaly detection input feature distributions
FEATURE_PSI_THRESHOLDS = {
    "cpu_utilization_mean": 0.20,      # high drift expected from deployments
    "request_latency_p99": 0.15,       # medium: changes with traffic mix
    "memory_utilization_trend": 0.10,  # low: gradual change is the signal
    "error_rate": 0.20,                # high: deployment-driven changes
    "isolation_forest_score": 0.15,    # model output drift = model needs retraining
    "autoencoder_recon_error": 0.15,
}

# Meta-monitoring: monitor the anomaly detector outputs themselves
DETECTOR_HEALTH_METRICS = {
    "alert_volume_hourly": {
        "baseline": 120,        # alerts/hour baseline
        "upper_alert_multiplier": 5.0,   # 5x baseline = alert storm
        "lower_alert_multiplier": 0.2,   # 80% below baseline = missed detections?
    },
    "mean_score_per_metric": {
        "drift_threshold": 0.20,   # PSI on isolation forest scores
    },
    "false_positive_rate": {
        "target": 0.001,           # 0.1% FPR
        "alert_upper": 0.005,      # 5x target triggers retraining
    },
}
```

### Retraining Triggers and Cadence

```
Cadence        Trigger                                    Action
──────────────────────────────────────────────────────────────────────────────
Weekly         Scheduled                                   Isolation Forest + Autoencoder retrain
Monthly        Scheduled                                   CUSUM parameter grid search
Triggered      Alert volume > 5× baseline (1 hour)        Alert storm: raise thresholds + investigate
Triggered      Alert volume < 0.2× baseline (1 hour)      Missed detections: lower thresholds + audit
Triggered      PSI > 0.20 on any top-5 feature            Feature drift: trigger Isolation Forest retrain
Triggered      Major infrastructure change (deployment,   Put detection in observation mode (2x threshold)
               migration, new service)                     for 7 days; collect data; retrain
Triggered      Labeled FPR > 0.5% (weekly precision audit)Emergency threshold recalibration
Triggered      Synthetic anomaly injection recall < 95%   Recall regression: investigate + retrain
Quarterly      Scheduled                                   Full system evaluation with synthetic
                                                           anomalies across all scenario types
```

---

## Additional Interview Questions

**How do you detect anomalies in multivariate metric correlations, not just individual metrics?**
Single-metric detectors miss anomalies where each individual metric appears normal but their joint behavior is unusual. Example: CPU utilization at 70% and network I/O at 50% are both normal individually, but their simultaneous occurrence at 2 AM on a Sunday is unusual. Three approaches: (1) Multivariate Autoencoder: train on joint feature vectors of correlated metrics per host (e.g., [cpu, memory, network, disk] as a 4-dim vector). The autoencoder learns the joint normal distribution; unusual combinations produce high reconstruction error even if each dimension is individually normal. (2) Correlation drift detection: compute pairwise Pearson correlation between related metrics (e.g., request_count vs CPU) on a rolling basis. A sudden change in correlation (from 0.9 to 0.2) indicates a structural change worth investigating. (3) Graph-based anomaly: model services as nodes, metric correlations as edges. Anomaly if a node's correlation profile with its neighbors changes significantly.

**How does the CUSUM algorithm detect gradual drift versus the 3-sigma rule?**
The 3-sigma rule computes the z-score of the current value against the rolling window mean and std: z = (x - mu) / sigma. It detects point anomalies (sudden large deviations) but is insensitive to gradual drift — a sequence of small positive deviations each within 2 sigma is not flagged, but represents a cumulative drift of 10+ sigma. CUSUM (Cumulative Sum): S_t = max(0, S_{t-1} + (x_t - mu - k)), where k is the allowed slack (typically 0.5 sigma). When S_t exceeds threshold h (typically 5 sigma), an alarm fires. CUSUM accumulates the evidence of sustained drift: 20 consecutive deviations of +0.3 sigma = S_20 = 20 × (0.3 - 0.5) = -4 (reset to 0 each time negative)... actually: if the drift is consistent, S_t grows. For drift of +0.6 sigma above mu with k=0.5: each step adds (0.6 - 0.5) = 0.1 to S_t. After h/0.1 = 50 steps (50 minutes), CUSUM alarms. 3-sigma would never alarm because each individual point is only 0.6 sigma above mean.

**How do you build an anomaly detection system that supports multi-tenancy (different teams with different baselines)?**
Infrastructure monitoring at scale serves hundreds of teams with different baseline behaviors. Single global models fail: what is anomalous for the checkout service is normal for the batch processing service. Multi-tenancy design: (1) Per-service-type models: cluster services by behavior profile (web APIs, batch jobs, databases, streaming) using k-means on feature vector statistics. Train separate Isolation Forest and Autoencoder models per cluster (4-8 clusters). (2) Per-service thresholds: the Isolation Forest score distribution varies by service. Calibrate the decision threshold independently per service using its own 30-day historical data. (3) Shared seasonal decomposition: STL decomposition of the seasonal component is reusable across services if they share the same time zone and business hour patterns. (4) Team-configurable sensitivity: expose a sensitivity parameter (low/medium/high) that shifts the Isolation Forest decision threshold by ±1 sigma, letting teams tune their own alert sensitivity without affecting others.

**How would you design anomaly detection for a metric that has no normal state (e.g., error count that should always be near zero)?**
Metrics with near-zero baseline (error counts, exception rates, 5xx error rates) require a different approach than typical mean-reversion metrics. Problems with standard z-score: if the mean is 0 and std is 0, the z-score is undefined; if std is 0.001, any count above 0 is thousands of sigma. Solutions: (1) Threshold-based detection: any non-zero value triggers an alert, with a minimum threshold (e.g., > 5 errors in 5 minutes) to suppress noise. (2) Poisson distribution test: model error counts as Poisson(lambda) where lambda is estimated from the last 7 days. Use a Poisson exact test: P(count >= observed | lambda). Flag if p-value < 0.001. This accounts for natural variability in rare events without being confused by zero baseline. (3) Rate change detection: instead of absolute count, compute errors_per_1000_requests. This metric has a meaningful mean even when counts are low and is insensitive to traffic volume changes. Apply CUSUM on the rate.

**How do you measure the recall of an anomaly detection system in production where ground truth is rare and expensive?**
Ground truth labeling for anomaly detection is expensive: on-call engineers manually reviewing 500 alerts/week is the maximum sustainable budget. Three complementary recall measurement methods: (1) Injected synthetic anomalies (chaos engineering): schedule controlled experiments — inject a CPU spike to 95%, simulate a memory leak growing 2% per hour, kill and restart a service. Measure what fraction of these known anomalies are detected within 60 seconds. Run 10 synthetic anomalies per week; target recall > 95%. (2) Post-hoc incident coverage: for every production incident that caused user impact (tracked in PagerDuty), check whether the anomaly detection system fired an alert within 5 minutes of the incident start. Track "incident coverage rate" — what fraction of production incidents had a preceding anomaly alert. (3) Honeypot metrics: inject fake metrics that simulate known failure patterns (a metric that always increases 1% per hour, simulating a memory leak) permanently in the metric stream. These honeypot anomalies must always be detected; failure to detect = recall regression.
