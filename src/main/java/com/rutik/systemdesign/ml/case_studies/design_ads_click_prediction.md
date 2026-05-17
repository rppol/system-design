# Design an Ads Click-Through Rate (CTR) Prediction System

## Problem Statement

Design a CTR prediction system for online advertising. For every ad auction (1 million per second), predict P(click | user, ad, context) within 10ms. Training data consists of 1 billion impressions per day with binary click labels. The model must be well-calibrated — predicted CTR should closely match actual observed CTR at the same predicted score level (a predicted CTR of 5% should correspond to a 5% actual click rate in aggregate). Miscalibration directly distorts auction mechanics: advertiser bids are multiplied by predicted CTR to compute effective CPC, so a 2x calibration error doubles or halves the auction clearing price.

Constraints:
- 1M QPS, <10ms P99 end-to-end latency
- 1B training examples per day (sparse: 0.1-2% click rate)
- Billions of unique user/ad/publisher IDs — sparse ID features dominate
- Model must be calibrated (Brier score < 0.08, reliability diagram near diagonal)
- Daily retraining minimum; hourly preferred (fresh data = higher accuracy on new campaigns)
- Model size: embedding tables can be hundreds of GB — too large for GPU serving

---

## Architecture Overview

```
  FEATURE ENGINEERING & TRAINING DATA (offline, Spark)
  ┌──────────────────────────────────────────────────────────────────┐
  │  Impression logs (Kafka → Parquet on S3)                        │
  │  + Click join (30-min delay for click attribution)              │
  │       │                                                          │
  │       v                                                          │
  │  Spark ETL:                                                      │
  │  - Feature hashing: user_id → 2^24 buckets (16M)               │
  │  - Feature hashing: ad_id  → 2^24 buckets (16M)                │
  │  - Feature hashing: publisher_id → 2^20 (1M)                   │
  │  - Cross features: user_interest × ad_category → 2^20          │
  │  - Dense: user_age_bucket, ad_price, hour_of_day               │
  │       │                                                          │
  │       v                                                          │
  │  Training data: 1B examples/day (libSVM or Parquet format)      │
  │  Sample weights: decay older examples                            │
  │  Negative downsampling: subsample 99% of non-clicks             │
  │  (with calibration correction factor q = 1/subsample_rate)      │
  └──────────────────────────────────────────────────────────────────┘

  MODEL TRAINING (PyTorch, daily/hourly)
  ┌──────────────────────────────────────────────────────────────────┐
  │  DeepFM Architecture:                                            │
  │                                                                  │
  │  Inputs: sparse IDs → Embedding Lookup (dim=16)                 │
  │          dense features (normalized)                             │
  │                                                                  │
  │  FM Component:                                                   │
  │  - 2nd-order interactions: <v_i, v_j> for all pairs i,j         │
  │  - Computed efficiently: 0.5*(||sum(v)||^2 - sum(||v||^2))       │
  │  - O(k*d) not O(d^2) — critical for 100+ embedding fields       │
  │                                                                  │
  │  Deep Component:                                                 │
  │  - Concat all embeddings + dense: ~1600-dim input               │
  │  - MLP: 400 → 400 → 400 → 1                                     │
  │  - BatchNorm + ReLU + Dropout(0.2)                               │
  │                                                                  │
  │  Output: sigmoid(fm_output + deep_output) → CTR probability      │
  │                                                                  │
  │  Optimizer: Adagrad (lr=0.01) for sparse features               │
  │             Adam (lr=0.001) for dense/MLP parameters            │
  │  Mixed optimizer via parameter groups                             │
  └──────────────────────────────────────────────────────────────────┘

  CALIBRATION
  ┌──────────────────────────────────────────────────────────────────┐
  │  Raw model output → Platt Scaling (logistic regression)         │
  │  Calibration set: 5M held-out impressions                       │
  │  Platt parameters (a, b): sigmoid(a * raw_score + b)            │
  │                                                                  │
  │  Evaluation: reliability diagram, Brier score, ECE              │
  │  Recalibrate if ECE > 2% or any bucket deviates >3%             │
  └──────────────────────────────────────────────────────────────────┘

  SERVING (<10ms P99)
  ┌──────────────────────────────────────────────────────────────────┐
  │  Auction Request: user_id, ad_ids (10-100), context             │
  │       │                                                          │
  │       v                                                          │
  │  Feature Server (Go/C++)                                        │
  │  - Compute feature hashes in microseconds                       │
  │  - Batch embedding lookup: Redis Cluster                        │
  │    (embedding table: 200GB+, sharded across 20 nodes)           │
  │  - Dense features computed inline                               │
  │  (feature computation: 1-3ms)                                   │
  │       │                                                          │
  │       v                                                          │
  │  ONNX Inference Server (C++, TensorRT on GPU)                   │
  │  - Batch all ads in auction together: batch_size up to 100      │
  │  - MLP inference only (embeddings already fetched)              │
  │  - FM computation on GPU                                        │
  │  (inference: 2-5ms for batch of 100)                            │
  │       │                                                          │
  │       v                                                          │
  │  Calibration: apply Platt scaling (microseconds)                │
  │       │                                                          │
  │       v                                                          │
  │  Response: CTR scores for each ad in auction                    │
  │  Auction: effective_cpc = bid * CTR → rank ads                  │
  └──────────────────────────────────────────────────────────────────┘

  MONITORING
  ┌──────────────────────────────────────────────────────────────────┐
  │  Per-hour: predicted CTR vs actual CTR                          │
  │  Calibration alert: ratio > 1.05 or < 0.95                      │
  │  NE (Normalized Entropy) vs baseline logistic regression         │
  │  Logloss improvement: >1% NE improvement over baseline = good   │
  └──────────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

**Feature hashing over explicit vocabulary**: With billions of unique user IDs and ad IDs, maintaining a lookup vocabulary table is costly (memory, update latency). Feature hashing maps any ID to a fixed-size bucket via MurmurHash(id) % bucket_count. Hash collisions exist but are rare at 2^24 buckets. No vocabulary management needed — new users and ads are handled automatically on day 1.

**DeepFM over Wide & Deep**: Both are standard architectures for CTR. DeepFM replaces the "wide" linear component of Wide & Deep with a Factorization Machine (FM) component that captures all pairwise embedding interactions. The FM is parameter-efficient: it does not learn a separate weight per pair (which would be O(d^2) parameters) but instead uses the dot product of embedding vectors, sharing parameters across pairs. This is crucial when there are 100+ feature fields.

**Adagrad for sparse features**: Sparse ID features (user_id, ad_id) get gradient updates only when they appear in a batch. SGD with uniform LR over-adjusts frequent features (appearing in every batch) and under-adjusts rare features. Adagrad maintains per-parameter accumulated squared gradient, effectively giving rare features a higher learning rate — exactly the right behavior. The dense MLP layers use Adam (adaptive but without sparsity concern).

**Negative downsampling with calibration correction**: At 1% CTR, 99% of training examples are negatives. Training on all negatives is wasteful (100B non-click examples/day). Subsample negatives at rate q = 1% (keep 1 in 100 non-clicks). This changes the training distribution. To restore calibration, apply correction at inference: calibrated_ctr = raw_ctr / (raw_ctr + (1 - raw_ctr) / q). Platt scaling absorbs this correction automatically if calibrated on the original (unsubsampled) distribution.

**Embedding table in Redis, not GPU**: At embedding dimension 16 and 2^24 buckets per feature, each feature table is 16 * 16M * 4 bytes = 1GB. With 10 feature fields, total is 10GB per table set. Multiple versions during A/B testing mean 20-40GB of embedding tables — far exceeding GPU memory (80GB A100 is consumed by the model and batch computation, not a 40GB static table). Redis Cluster with 20 nodes serves embedding lookups in 0.5-1ms using batch GET commands.

**Per-advertiser calibration**: Global calibration may hide per-advertiser miscalibration. A new advertiser with no historical data gets the global calibration factor, which may be wrong for their ad creative. Monitor calibration ratio (predicted/actual) per advertiser daily; apply a per-advertiser scalar correction factor if ratio deviates >10%.

---

## Implementation

### Feature Hashing

```python
import hashlib
import struct
import numpy as np
from typing import Any


def murmurhash3_32(key: str, seed: int = 42) -> int:
    """32-bit MurmurHash3 for feature hashing."""
    # Python's built-in hash is not stable across processes; use hashlib
    h = hashlib.md5(f"{seed}:{key}".encode()).digest()
    return struct.unpack("<I", h[:4])[0]


def feature_hash(value: Any, num_buckets: int = 2**24) -> int:
    """Hash any feature value to a bucket index."""
    key = str(value)
    return murmurhash3_32(key) % num_buckets


def hash_cross_feature(
    feature_a: Any,
    feature_b: Any,
    num_buckets: int = 2**20,
) -> int:
    """Hash the cross product of two features."""
    combined = f"{feature_a}|{feature_b}"
    return feature_hash(combined, num_buckets)


class FeatureHasher:
    """
    Convert raw auction features to hashed sparse indices.
    Mirrors the training-time feature engineering exactly to prevent skew.
    """

    FEATURE_CONFIGS = {
        "user_id":          {"buckets": 2**24, "field_id": 0},
        "ad_id":            {"buckets": 2**24, "field_id": 1},
        "publisher_id":     {"buckets": 2**20, "field_id": 2},
        "user_age_bucket":  {"buckets": 10,    "field_id": 3},   # 0-9
        "ad_category":      {"buckets": 500,   "field_id": 4},
        "hour_of_day":      {"buckets": 24,    "field_id": 5},
        "device_type":      {"buckets": 4,     "field_id": 6},   # mobile/tablet/desktop/other
    }

    CROSS_FEATURES = [
        ("user_age_bucket", "ad_category", 2**20, 10),
        ("device_type", "ad_category",     2**18, 11),
    ]

    def hash_example(self, raw_features: dict[str, Any]) -> dict[str, list[int]]:
        """Return field_ids and hashed indices for one example."""
        field_ids = []
        indices = []

        for feature_name, config in self.FEATURE_CONFIGS.items():
            value = raw_features.get(feature_name, "MISSING")
            hashed = feature_hash(value, config["buckets"])
            field_ids.append(config["field_id"])
            indices.append(hashed)

        # Cross features
        for feat_a, feat_b, buckets, field_id in self.CROSS_FEATURES:
            hashed = hash_cross_feature(
                raw_features.get(feat_a, "MISSING"),
                raw_features.get(feat_b, "MISSING"),
                buckets,
            )
            field_ids.append(field_id)
            indices.append(hashed)

        return {"field_ids": field_ids, "indices": indices}
```

### DeepFM Architecture (PyTorch)

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader


class FMLayer(nn.Module):
    """Factorization Machine: 2nd-order feature interaction in O(k*d) time."""

    def forward(self, embeddings: torch.Tensor) -> torch.Tensor:
        """
        embeddings: (batch, num_fields, embed_dim)
        Returns FM output: (batch, 1)

        Formula: 0.5 * (||sum(v_i)||^2 - sum(||v_i||^2))
        """
        # Sum of embeddings, then square
        sum_of_embeds = embeddings.sum(dim=1)               # (B, D)
        square_of_sum = sum_of_embeds.pow(2)                # (B, D)

        # Square of each embedding, then sum
        sum_of_squares = embeddings.pow(2).sum(dim=1)       # (B, D)

        # FM interaction term
        fm_term = 0.5 * (square_of_sum - sum_of_squares)   # (B, D)
        return fm_term.sum(dim=1, keepdim=True)             # (B, 1)


class DeepFM(nn.Module):
    """
    DeepFM for CTR prediction.

    Architecture:
    - Sparse fields: embedding lookup → (num_fields, embed_dim)
    - FM component: 2nd-order interactions of embeddings → scalar
    - Deep component: flatten embeddings + dense → MLP → scalar
    - Output: sigmoid(fm_out + deep_out)
    """

    def __init__(
        self,
        field_dims: list[int],   # number of buckets per field
        embed_dim: int = 16,
        mlp_dims: list[int] = [400, 400, 400],
        num_dense_features: int = 8,
        dropout: float = 0.2,
    ) -> None:
        super().__init__()
        self.num_fields = len(field_dims)
        self.embed_dim = embed_dim

        # One embedding table per field (different bucket sizes)
        self.embeddings = nn.ModuleList([
            nn.EmbeddingBag(dim, embed_dim, mode="sum", sparse=True)
            for dim in field_dims
        ])

        self.fm = FMLayer()

        # Deep component: concat(all embeddings, dense) → MLP
        deep_input_dim = self.num_fields * embed_dim + num_dense_features
        layers: list[nn.Module] = []
        for out_dim in mlp_dims:
            layers += [
                nn.Linear(deep_input_dim, out_dim),
                nn.BatchNorm1d(out_dim),
                nn.ReLU(),
                nn.Dropout(dropout),
            ]
            deep_input_dim = out_dim
        layers.append(nn.Linear(deep_input_dim, 1))
        self.deep = nn.Sequential(*layers)

        self._init_weights()

    def _init_weights(self) -> None:
        for emb in self.embeddings:
            nn.init.normal_(emb.weight, std=0.01)
        for module in self.deep.modules():
            if isinstance(module, nn.Linear):
                nn.init.xavier_uniform_(module.weight)
                nn.init.zeros_(module.bias)

    def forward(
        self,
        sparse_indices: list[torch.Tensor],  # list of (B,) per field
        dense_features: torch.Tensor,         # (B, num_dense_features)
    ) -> torch.Tensor:                         # (B,) CTR probabilities
        # Embed each field
        field_embeddings = []
        for i, (emb_layer, indices) in enumerate(zip(self.embeddings, sparse_indices)):
            # EmbeddingBag expects (B, 1) for single-index-per-example
            emb = emb_layer(indices.unsqueeze(1))  # (B, embed_dim)
            field_embeddings.append(emb)

        stacked = torch.stack(field_embeddings, dim=1)  # (B, num_fields, embed_dim)

        # FM term
        fm_out = self.fm(stacked)  # (B, 1)

        # Deep term
        flat = stacked.view(stacked.size(0), -1)           # (B, num_fields * embed_dim)
        deep_input = torch.cat([flat, dense_features], dim=1)
        deep_out = self.deep(deep_input)                    # (B, 1)

        logit = fm_out + deep_out                          # (B, 1)
        return torch.sigmoid(logit).squeeze(1)             # (B,)


def train_deepfm(
    model: DeepFM,
    train_loader: DataLoader,
    val_loader: DataLoader,
    epochs: int = 5,
) -> None:
    """Training loop with separate Adagrad (sparse) and Adam (dense) optimizers."""
    # Sparse embedding parameters benefit from Adagrad
    embedding_params = list(model.embeddings.parameters())
    dense_params = [p for n, p in model.named_parameters()
                    if "embeddings" not in n]

    optimizer = torch.optim.AdamW([
        {"params": embedding_params, "lr": 0.01,  "eps": 1e-6},
        {"params": dense_params,     "lr": 0.001, "eps": 1e-8},
    ])

    # Binary cross-entropy; reduction='none' to apply sample weights
    criterion = nn.BCELoss(reduction="none")

    device = next(model.parameters()).device
    model.train()

    for epoch in range(epochs):
        total_loss = 0.0
        for batch in train_loader:
            sparse = [t.to(device) for t in batch["sparse_indices"]]
            dense = batch["dense"].to(device)
            labels = batch["label"].float().to(device)
            weights = batch.get("weight", torch.ones_like(labels)).to(device)

            optimizer.zero_grad()
            preds = model(sparse, dense)
            loss = (criterion(preds, labels) * weights).mean()
            loss.backward()
            # Clip only dense gradients; sparse gradients are naturally bounded
            torch.nn.utils.clip_grad_norm_(dense_params, 5.0)
            optimizer.step()
            total_loss += loss.item()

        print(f"Epoch {epoch+1}: loss={total_loss/len(train_loader):.5f}")
```

### Calibration with Platt Scaling

```python
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.calibration import calibration_curve
from sklearn.metrics import brier_score_loss
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt


class PlattScaler:
    """
    Calibrate model outputs using Platt scaling.
    Fits sigmoid(a * raw_score + b) to minimize log-loss on calibration set.

    Accounts for negative downsampling during training:
    Before calibration, correct for subsampling rate q:
      corrected = raw / (raw + (1 - raw) / q)
    Then apply Platt scaling on corrected scores.
    """

    def __init__(self, negative_subsample_rate: float = 1.0) -> None:
        self.q = negative_subsample_rate  # 1.0 = no subsampling
        self.lr = LogisticRegression(C=1.0, max_iter=1000, solver="lbfgs")
        self.fitted = False

    def _correct_for_subsampling(self, raw_scores: np.ndarray) -> np.ndarray:
        """Correct probability estimates for negative downsampling."""
        if self.q >= 1.0:
            return raw_scores
        # P(click | subsampled) = raw / (raw + (1-raw)/q)
        return raw_scores / (raw_scores + (1 - raw_scores) / self.q)

    def fit(self, raw_scores: np.ndarray, labels: np.ndarray) -> "PlattScaler":
        corrected = self._correct_for_subsampling(raw_scores)
        # Logistic regression on corrected scores as single feature
        self.lr.fit(corrected.reshape(-1, 1), labels)
        self.fitted = True

        calibrated = self.predict(raw_scores)
        brier = brier_score_loss(labels, calibrated)
        print(f"Platt scaling: a={self.lr.coef_[0][0]:.4f}, "
              f"b={self.lr.intercept_[0]:.4f}, Brier={brier:.5f}")
        return self

    def predict(self, raw_scores: np.ndarray) -> np.ndarray:
        if not self.fitted:
            raise RuntimeError("PlattScaler not fitted")
        corrected = self._correct_for_subsampling(raw_scores)
        return self.lr.predict_proba(corrected.reshape(-1, 1))[:, 1]

    def evaluate_calibration(
        self,
        raw_scores: np.ndarray,
        labels: np.ndarray,
        n_bins: int = 20,
    ) -> dict[str, float]:
        calibrated = self.predict(raw_scores)

        # Expected Calibration Error (ECE)
        fraction_of_positives, mean_predicted = calibration_curve(
            labels, calibrated, n_bins=n_bins
        )
        ece = float(np.abs(fraction_of_positives - mean_predicted).mean())

        # Brier score
        brier = float(brier_score_loss(labels, calibrated))

        # Normalized Entropy (NE) vs base logistic regression
        base_ctr = labels.mean()
        base_logloss = -base_ctr * np.log(base_ctr) - (1-base_ctr) * np.log(1-base_ctr)
        model_logloss = float(-np.mean(
            labels * np.log(calibrated + 1e-10) +
            (1 - labels) * np.log(1 - calibrated + 1e-10)
        ))
        ne = model_logloss / base_logloss  # <1 is good; 0.85 means 15% improvement

        return {"ece": ece, "brier": brier, "normalized_entropy": ne}


def evaluate_calibration_by_score_bucket(
    predicted_ctrs: np.ndarray,
    actual_labels: np.ndarray,
    n_buckets: int = 20,
) -> None:
    """Print calibration ratio (predicted / actual) per score bucket."""
    thresholds = np.percentile(predicted_ctrs, np.linspace(0, 100, n_buckets + 1))
    print(f"{'Bucket':>10} {'Pred CTR':>10} {'Actual CTR':>12} {'Ratio':>8}")
    print("-" * 45)
    for i in range(n_buckets):
        mask = (predicted_ctrs >= thresholds[i]) & (predicted_ctrs < thresholds[i+1])
        if mask.sum() == 0:
            continue
        pred_mean = predicted_ctrs[mask].mean()
        actual_mean = actual_labels[mask].mean()
        ratio = pred_mean / (actual_mean + 1e-10)
        status = " OK" if 0.95 <= ratio <= 1.05 else " ALERT"
        print(f"{i+1:>10} {pred_mean:>10.4f} {actual_mean:>12.4f} {ratio:>8.3f}{status}")
```

### Online Serving (Embedding Lookup from Redis)

```python
import redis
import numpy as np
import struct
from typing import NamedTuple


class AuctionFeatures(NamedTuple):
    user_id: str
    ad_ids: list[str]
    publisher_id: str
    hour_of_day: int
    device_type: str


class EmbeddingStore:
    """Redis-backed embedding store for CTR model serving."""

    EMBED_DIM = 16
    DTYPE = np.float32
    BYTES_PER_EMBED = 16 * 4  # 64 bytes per embedding

    def __init__(self, redis_cluster: redis.RedisCluster) -> None:
        self.redis = redis_cluster

    def _embed_key(self, field: str, index: int) -> str:
        return f"emb:{field}:{index}"

    def batch_get_embeddings(
        self, field: str, indices: list[int]
    ) -> np.ndarray:
        """Batch fetch embeddings. Returns (len(indices), EMBED_DIM) array."""
        keys = [self._embed_key(field, idx) for idx in indices]
        pipe = self.redis.pipeline(transaction=False)
        for key in keys:
            pipe.get(key)
        results = pipe.execute()

        embeddings = np.zeros((len(indices), self.EMBED_DIM), dtype=self.DTYPE)
        for i, raw in enumerate(results):
            if raw is not None:
                embeddings[i] = np.frombuffer(raw, dtype=self.DTYPE)
            # Missing embedding (new user/ad) stays as zeros — handled gracefully
        return embeddings

    def score_auction(
        self,
        features: AuctionFeatures,
        model_session: "ort.InferenceSession",
        platt_scaler: PlattScaler,
    ) -> list[float]:
        """Score all ads in an auction. Returns calibrated CTR per ad."""
        n_ads = len(features.ad_ids)

        # Batch embedding lookup for all ads at once (single Redis round trip)
        from hasher import FeatureHasher
        hasher = FeatureHasher()

        user_idx = feature_hash(features.user_id, 2**24)
        pub_idx = feature_hash(features.publisher_id, 2**20)
        ad_indices = [feature_hash(ad_id, 2**24) for ad_id in features.ad_ids]

        # Fetch user embedding once
        user_emb = self.batch_get_embeddings("user_id", [user_idx])[0]  # (D,)
        pub_emb = self.batch_get_embeddings("publisher_id", [pub_idx])[0]
        ad_embs = self.batch_get_embeddings("ad_id", ad_indices)         # (n_ads, D)

        # Build dense features (same for all ads in auction)
        hour_norm = features.hour_of_day / 23.0
        device_enc = {"mobile": 0, "tablet": 1, "desktop": 2}.get(features.device_type, 3)
        dense_base = np.array([hour_norm, device_enc / 3.0], dtype=np.float32)

        # Build input batch: one row per ad
        all_embeddings = np.zeros((n_ads, 3 * self.EMBED_DIM), dtype=np.float32)
        all_embeddings[:, :self.EMBED_DIM] = user_emb    # broadcast
        all_embeddings[:, self.EMBED_DIM:2*self.EMBED_DIM] = pub_emb
        all_embeddings[:, 2*self.EMBED_DIM:] = ad_embs

        dense_batch = np.tile(dense_base, (n_ads, 1))

        # ONNX inference: MLP + FM on GPU
        model_inputs = {
            "embeddings": all_embeddings,
            "dense": dense_batch,
        }
        raw_scores = model_session.run(["ctr_logit"], model_inputs)[0].squeeze()

        # Calibrate
        calibrated = platt_scaler.predict(raw_scores)
        return calibrated.tolist()
```

---

## ML Components Used

| Component | Technology | Role |
|-----------|-----------|------|
| Feature Hashing | MurmurHash3 mod bucket_count | Sparse ID → fixed embedding index |
| Model Architecture | DeepFM (FM + MLP) | 2nd-order + higher-order feature interactions |
| Optimizer (sparse) | Adagrad (lr=0.01) | Adaptive LR for sparse embedding updates |
| Optimizer (dense) | Adam (lr=0.001) | MLP and BatchNorm parameters |
| Calibration | Platt Scaling (logistic regression) | Predicted CTR ≈ actual CTR |
| Calibration Metric | ECE, Brier score, reliability diagram | Calibration quality measurement |
| Evaluation | Normalized Entropy (NE) | Model improvement over base logistic regression |
| Embedding Store | Redis Cluster (sharded, 20 nodes) | Sub-1ms embedding lookup for serving |
| Inference Runtime | ONNX Runtime + TensorRT | C++ serving, GPU acceleration |
| Training Data | Parquet on S3 + PyTorch DataLoader | 1B examples/day |
| Experiment Platform | MLflow + shadow deployment | Safe model rollout |

---

## Tradeoffs and Alternatives

| Decision | Chosen | Alternative | Reasoning |
|----------|--------|-------------|-----------|
| Model | DeepFM | Wide & Deep, DLRM | DeepFM: FM component more parameter-efficient than wide linear; comparable to DLRM |
| Feature representation | Feature hashing | Vocabulary lookup table | Hashing: no vocabulary maintenance, handles new IDs day-zero; collision rate <0.01% at 2^24 |
| Calibration | Platt scaling | Isotonic regression, temperature scaling | Platt: monotonic, low variance, 2 parameters; isotonic needs more data, non-monotonic |
| Embedding storage | Redis Cluster | GPU HBM, in-process memory | Redis: 200GB+ tables too large for GPU; in-process requires replication per pod |
| Negative sampling | Subsample 99% negatives | Full dataset | 1B examples/day unsampled = 10TB Parquet; subsampling reduces to 10GB with calibration correction |
| Retraining frequency | Daily full + hourly fine-tune | Daily only | Hourly fine-tune on latest 3h data captures new campaign launches same day |

---

## Interview Discussion Points

**Why is calibration critical for an ad CTR model and how do you achieve it?**
Ad auctions use Vickrey-Clarke-Groves (second-price) mechanics. The winning advertiser pays the second-highest bid weighted by CTR (effective CPC = bid / CTR). If the model predicts 2% CTR but the true rate is 1%, the system charges half the correct price — destroying revenue. If it predicts 1% but true rate is 2%, advertisers are overcharged and reduce bids, reducing fill rate. Calibration is achieved by (1) training with proper loss (log-loss directly optimizes calibration), (2) applying Platt scaling on a held-out set to correct systematic over/under-confidence, and (3) monitoring calibration ratio hourly — alert if predicted/actual deviates beyond 5% in any score bucket.

**How does feature hashing work and what are its failure modes?**
Feature hashing maps a string feature value to an integer bucket via h = hash(field_name + str(value)) % bucket_count. Advantages: no vocabulary needed, handles arbitrary new values, constant memory regardless of cardinality. The failure mode is hash collisions: two different values mapping to the same bucket. At 2^24 = 16M buckets with 1M unique ad IDs, the birthday paradox gives collision probability of ~3% — acceptable because the embedding for the colliding pair is simply a noisy average of what the two individual embeddings would have been. Using the field name in the hash key prevents cross-field collisions (user_id:123 ≠ ad_id:123).

**How do you handle the latency requirement of 10ms at 1M QPS?**
The 10ms budget breaks down as: network + load balancing 1ms, feature hashing (in-memory, microseconds) <0.1ms, Redis embedding batch GET 1-2ms, ONNX GPU inference for batch of 100 ads 2-3ms, Platt calibration (scalar multiply, microseconds) <0.1ms, response serialization 0.5ms = ~5ms P50, 8ms P99. The critical optimizations are: (1) batch all ads in one auction together (one Redis call, one GPU kernel launch), (2) keep embeddings in Redis Cluster with consistent hashing so each key always hits the same shard (no cross-shard scatter-gather), (3) use ONNX INT8 quantization for the MLP — reduces inference from 3ms to 1.5ms with <0.1% NE degradation.

**How do you handle model staleness as new ad campaigns launch throughout the day?**
A campaign launched at 9 AM will not appear in training data until the nightly retrain, meaning predictions for its ads use only the hashed bucket embedding (which may collide with other ads). Strategy: (1) hourly fine-tuning on the latest 3 hours of impression data — new campaigns see their first gradient update within 1 hour. (2) Cold-start CTR: for ads with fewer than 100 impressions, blend the model prediction with the advertiser's historical average CTR (beta distribution conjugate prior), weighted by impression count. This provides a reasonable estimate for new campaigns before enough data exists to rely on the model.
