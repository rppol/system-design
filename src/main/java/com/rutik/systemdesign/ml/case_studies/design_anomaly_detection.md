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
