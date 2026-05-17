# Design a Recommendation Engine (Netflix/YouTube Scale)

## Problem Statement

Design a real-time recommendation engine for a video streaming platform. For every user session, return 10 personalized video recommendations within 100ms P99. The system must serve 200M registered users across 10M videos, processing 1B events per day (watches, clicks, skips, ratings). Handle cold start for new users (no history) and new videos (no interactions). Support A/B testing of different recommendation strategies.

Constraints:
- 200M users, 10M items
- 1B events/day ingested (12K events/sec average, 50K peak)
- 100ms P99 end-to-end recommendation latency
- Recall@100 > 95% from retrieval stage
- NDCG@10 > 0.45 after ranking
- Cold start: new user gets genre-based popular content within session

---

## Architecture Overview

```
                         OFFLINE PIPELINE (daily/hourly)
 ┌─────────────────────────────────────────────────────────────────┐
 │                                                                 │
 │  User Events (Kafka)                                            │
 │       │                                                         │
 │       v                                                         │
 │  Spark ETL  ──────────────────────────────────────────────────  │
 │       │              │                    │                     │
 │       v              v                    v                     │
 │  Interaction     Feature Store        Training Data             │
 │  Matrix (Hive)   (Hive offline)       (positive pairs +        │
 │       │                               negative samples)        │
 │       │                                    │                   │
 │       v                                    v                   │
 │  ALS Collab.    Two-Tower Model       LightGBM Ranker           │
 │  Filtering      (PyTorch, GPU)        (200 features)            │
 │       │               │                    │                   │
 │       v               v                    v                   │
 │  User/Item       User Embeddings      Model Registry            │
 │  Embeddings      Item Embeddings      (MLflow)                  │
 │  (256-dim)       (256-dim)                                      │
 │       └───────────────┘                                         │
 │               │                                                 │
 │               v                                                 │
 │         FAISS IVF Index                                         │
 │         (IVF4096,PQ32)                                          │
 └─────────────────────────────────────────────────────────────────┘
                         ONLINE SERVING (<100ms)
 ┌─────────────────────────────────────────────────────────────────┐
 │                                                                 │
 │  Client Request (user_id, context)                              │
 │       │                                                         │
 │       v                                                         │
 │  API Gateway ──> Feature Server                                 │
 │       │          (Redis <10ms lookup)                           │
 │       │               │                                         │
 │       v               v                                         │
 │  Retrieval Stage  [Two-Tower Query Encoder]                     │
 │  (FAISS ANN)      user_embedding → top-K=500 candidates         │
 │       │           (25ms budget)                                 │
 │       │                                                         │
 │       v                                                         │
 │  Ranking Stage    [LightGBM Ranker]                             │
 │  500 candidates → 20 ranked items                               │
 │  (200 features, 30ms budget)                                    │
 │       │                                                         │
 │       v                                                         │
 │  Re-ranking       [Business Rules]                              │
 │  MMR diversity + freshness boost + geo/rating filters           │
 │  (5ms budget)                                                   │
 │       │                                                         │
 │       v                                                         │
 │  Response: top-10 video IDs + scores                            │
 └─────────────────────────────────────────────────────────────────┘

  Feature Store
  ┌─────────────────────────────────────────────────────────────────┐
  │  Online (Redis)         │  Offline (Hive/S3)                   │
  │  - user_embedding       │  - user watch history (90d)          │
  │  - item_embedding       │  - item metadata                     │
  │  - user_7d_genres       │  - training labels                   │
  │  TTL: 24h               │  - feature snapshots                 │
  └─────────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

**Two-stage retrieval + ranking**: Exhaustive similarity search over 10M items is infeasible at 100ms. ANN retrieval narrows to 500 candidates in 25ms, then a richer LightGBM model (200 features, interaction terms) re-ranks. This is the standard industry pattern (YouTube DNN, Pinterest Pixie).

**Sampled softmax for two-tower training**: Full softmax over 10M items is prohibitive. Sample 1000 negatives per positive. Use in-batch negatives (treat other items in minibatch as negatives) — cheap and effective. Correction for popularity bias: subtract log(item_frequency) from logits.

**FAISS IVF with PQ compression**: IVF (Inverted File Index) partitions embedding space into 4096 Voronoi cells. At query time, search only nprobe=64 cells. PQ (Product Quantization) compresses 256-dim float32 (1KB/vector) to 32 bytes. 10M items: 10M * 32B = 320MB in RAM vs 2.5GB uncompressed. Recall@100 > 95% with nprobe=64.

**LightGBM over deep ranking**: LightGBM trains in hours (vs days for deep models), handles missing features gracefully, is interpretable (feature importance), and achieves comparable NDCG on tabular ranking features. NDCG@10 = 0.47 vs 0.49 for DNN — within 4%, but 10x faster to iterate.

**Feature store split (online/offline)**: Redis holds real-time features with 24h TTL for sub-10ms lookup. Hive holds historical features for training. Avoids training-serving skew by computing features with the same logic in both pipelines.

**Negative sampling strategy**: Random negatives are too easy (item never seen by user). Use popularity-weighted sampling: P(item as negative) proportional to sqrt(item_frequency). Also use "hard negatives" — items user saw but did not click in the same session.

---

## Implementation

### Two-Tower Retrieval Model (PyTorch)

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
import numpy as np
from typing import Optional


class UserTower(nn.Module):
    """Encodes user features into a fixed-dim embedding."""

    def __init__(
        self,
        num_users: int,
        user_embedding_dim: int = 64,
        genre_vocab_size: int = 50,
        output_dim: int = 256,
    ) -> None:
        super().__init__()
        self.user_embedding = nn.Embedding(
            num_users, user_embedding_dim, padding_idx=0
        )
        self.genre_embedding = nn.Embedding(genre_vocab_size, 16, padding_idx=0)
        # Dense layers: concat(user_emb, genre_emb_pool, dense_features)
        # user_emb=64, genre_pool=16, dense_features=8 (age_bucket, country_id, etc.)
        input_dim = user_embedding_dim + 16 + 8
        self.mlp = nn.Sequential(
            nn.Linear(input_dim, 256),
            nn.ReLU(),
            nn.LayerNorm(256),
            nn.Dropout(0.1),
            nn.Linear(256, 256),
            nn.ReLU(),
            nn.LayerNorm(256),
            nn.Linear(256, output_dim),
        )

    def forward(
        self,
        user_ids: torch.Tensor,             # (B,)
        genre_history: torch.Tensor,        # (B, max_genres)  padded
        dense_features: torch.Tensor,       # (B, 8)
    ) -> torch.Tensor:                      # (B, output_dim) L2-normalized
        u = self.user_embedding(user_ids)   # (B, 64)
        g = self.genre_embedding(genre_history)  # (B, max_genres, 16)
        # Mean-pool non-padding positions
        mask = (genre_history != 0).float().unsqueeze(-1)  # (B, T, 1)
        g = (g * mask).sum(dim=1) / mask.sum(dim=1).clamp(min=1)  # (B, 16)
        x = torch.cat([u, g, dense_features], dim=-1)
        out = self.mlp(x)
        return F.normalize(out, p=2, dim=-1)


class ItemTower(nn.Module):
    """Encodes item features into a fixed-dim embedding."""

    def __init__(
        self,
        num_items: int,
        item_embedding_dim: int = 64,
        genre_vocab_size: int = 50,
        output_dim: int = 256,
    ) -> None:
        super().__init__()
        self.item_embedding = nn.Embedding(
            num_items, item_embedding_dim, padding_idx=0
        )
        self.genre_embedding = nn.Embedding(genre_vocab_size, 16, padding_idx=0)
        input_dim = item_embedding_dim + 16 + 8  # 8 dense: duration, release_year, etc.
        self.mlp = nn.Sequential(
            nn.Linear(input_dim, 256),
            nn.ReLU(),
            nn.LayerNorm(256),
            nn.Dropout(0.1),
            nn.Linear(256, 256),
            nn.ReLU(),
            nn.LayerNorm(256),
            nn.Linear(256, output_dim),
        )

    def forward(
        self,
        item_ids: torch.Tensor,
        item_genres: torch.Tensor,
        item_dense: torch.Tensor,
    ) -> torch.Tensor:
        it = self.item_embedding(item_ids)
        g = self.genre_embedding(item_genres)
        mask = (item_genres != 0).float().unsqueeze(-1)
        g = (g * mask).sum(dim=1) / mask.sum(dim=1).clamp(min=1)
        x = torch.cat([it, g, item_dense], dim=-1)
        out = self.mlp(x)
        return F.normalize(out, p=2, dim=-1)


class TwoTowerModel(nn.Module):
    def __init__(self, num_users: int, num_items: int) -> None:
        super().__init__()
        self.user_tower = UserTower(num_users)
        self.item_tower = ItemTower(num_items)
        self.temperature = nn.Parameter(torch.ones(1) * 0.07)  # learnable temp

    def forward(
        self,
        user_ids: torch.Tensor,
        genre_history: torch.Tensor,
        user_dense: torch.Tensor,
        item_ids: torch.Tensor,
        item_genres: torch.Tensor,
        item_dense: torch.Tensor,
    ) -> torch.Tensor:
        u_emb = self.user_tower(user_ids, genre_history, user_dense)  # (B, D)
        i_emb = self.item_tower(item_ids, item_genres, item_dense)     # (B, D)

        # In-batch negatives: (B, B) similarity matrix
        # Diagonal = positive pairs, off-diagonal = negatives
        logits = torch.matmul(u_emb, i_emb.T) / self.temperature  # (B, B)
        labels = torch.arange(u_emb.size(0), device=u_emb.device)

        # Popularity bias correction: subtract log(item_freq) from logits
        # item_freq shape: (B,) — frequency of each item in training corpus
        # Omitted here for brevity; add as: logits - log_item_freq.unsqueeze(0)

        loss = F.cross_entropy(logits, labels)
        return loss


def train_two_tower(
    model: TwoTowerModel,
    dataloader: DataLoader,
    epochs: int = 10,
    lr: float = 3e-4,
) -> None:
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer, T_max=epochs * len(dataloader)
    )
    model.train()
    for epoch in range(epochs):
        total_loss = 0.0
        for batch in dataloader:
            optimizer.zero_grad()
            loss = model(**batch)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()
            total_loss += loss.item()
        print(f"Epoch {epoch+1}: loss={total_loss/len(dataloader):.4f}")
```

### FAISS Index Build and Query

```python
import faiss
import numpy as np
from pathlib import Path


class FaissRetrievalIndex:
    """IVF index with PQ compression for 10M item embeddings at 100ms budget."""

    def __init__(
        self,
        dim: int = 256,
        n_cells: int = 4096,    # sqrt(10M) ~ 3162, round up to power of 2
        pq_bytes: int = 32,     # 256-dim / 8 = 32 sub-vectors, 8 bits each
        nprobe: int = 64,       # probe 64 / 4096 cells — 1.5% of index
    ) -> None:
        self.dim = dim
        self.nprobe = nprobe
        # IVF with PQ compression: IVF4096,PQ32
        quantizer = faiss.IndexFlatIP(dim)  # L2-normalized vecs → inner product = cosine
        self.index = faiss.IndexIVFPQ(quantizer, dim, n_cells, pq_bytes, 8)
        self.item_ids: np.ndarray = np.array([], dtype=np.int64)

    def build(self, item_embeddings: np.ndarray, item_ids: np.ndarray) -> None:
        """Train index on embeddings and add all item vectors.

        item_embeddings: (N, dim) float32, already L2-normalized
        item_ids:        (N,) int64
        """
        assert item_embeddings.dtype == np.float32
        assert item_embeddings.shape[1] == self.dim
        # Training requires ~256K representative vectors
        train_size = min(256_000, len(item_embeddings))
        train_vecs = item_embeddings[:train_size]
        print(f"Training IVF index on {train_size} vectors...")
        self.index.train(train_vecs)
        self.index.add(item_embeddings)
        self.item_ids = item_ids
        print(f"Index built: {self.index.ntotal} vectors, {self.index.ntotal * 32 / 1e6:.1f} MB")

    def search(
        self, query_embedding: np.ndarray, top_k: int = 500
    ) -> list[tuple[int, float]]:
        """Return top_k (item_id, score) pairs for a single query embedding."""
        self.index.nprobe = self.nprobe
        q = query_embedding.reshape(1, -1).astype(np.float32)
        scores, indices = self.index.search(q, top_k)
        results = [
            (int(self.item_ids[idx]), float(scores[0][i]))
            for i, idx in enumerate(indices[0])
            if idx != -1
        ]
        return results

    def save(self, path: str) -> None:
        faiss.write_index(self.index, path)
        np.save(path + ".ids.npy", self.item_ids)

    @classmethod
    def load(cls, path: str, nprobe: int = 64) -> "FaissRetrievalIndex":
        obj = cls.__new__(cls)
        obj.index = faiss.read_index(path)
        obj.index.nprobe = nprobe
        obj.item_ids = np.load(path + ".ids.npy")
        obj.nprobe = nprobe
        return obj
```

### LightGBM Ranking with Cross-Features

```python
import lightgbm as lgb
import numpy as np
import pandas as pd
from sklearn.model_selection import GroupShuffleSplit


def build_ranking_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Construct 200 ranking features from user, item, context, and cross-features.
    df columns: user_id, item_id, query_user_embedding (256-dim stored separately),
                user_watch_count_7d, user_avg_watch_pct, user_top_genre,
                item_popularity_7d, item_avg_rating, item_age_days, item_genre,
                retrieval_score, hour_of_day, day_of_week, country_code
    """
    features = df.copy()

    # Cross-features (critical for ranking quality)
    features["genre_match"] = (
        features["user_top_genre"] == features["item_genre"]
    ).astype(int)
    features["popularity_x_retrieval"] = (
        features["item_popularity_7d"] * features["retrieval_score"]
    )
    features["freshness_score"] = np.exp(-features["item_age_days"] / 30.0)
    features["user_engagement_x_item_rating"] = (
        features["user_avg_watch_pct"] * features["item_avg_rating"]
    )

    # Bucketized time features
    features["is_prime_time"] = features["hour_of_day"].between(19, 23).astype(int)
    features["is_weekend"] = features["day_of_week"].isin([5, 6]).astype(int)

    feature_cols = [
        "user_watch_count_7d", "user_avg_watch_pct",
        "item_popularity_7d", "item_avg_rating", "item_age_days",
        "retrieval_score", "genre_match", "popularity_x_retrieval",
        "freshness_score", "user_engagement_x_item_rating",
        "is_prime_time", "is_weekend",
        # ... up to 200 features including embedding similarity dimensions
    ]
    return features[feature_cols]


def train_lightgbm_ranker(
    df: pd.DataFrame,
    label_col: str = "relevance",  # 0=skip, 1=click, 2=watch>50%, 3=watch>90%
    group_col: str = "user_id",
) -> lgb.Booster:
    """Train LambdaRank with NDCG@10 objective."""
    X = build_ranking_features(df)
    y = df[label_col].values
    groups = df.groupby(group_col).size().values  # items per user query

    splitter = GroupShuffleSplit(n_splits=1, test_size=0.1, random_state=42)
    train_idx, val_idx = next(splitter.split(X, y, groups=df[group_col]))

    train_data = lgb.Dataset(
        X.iloc[train_idx], label=y[train_idx],
        group=groups[:len(train_idx)],  # approximate; use proper group split
    )
    val_data = lgb.Dataset(
        X.iloc[val_idx], label=y[val_idx],
        group=groups[len(train_idx):],
        reference=train_data,
    )

    params = {
        "objective": "lambdarank",
        "metric": "ndcg",
        "ndcg_eval_at": [5, 10],
        "learning_rate": 0.05,
        "num_leaves": 127,
        "max_depth": 7,
        "min_data_in_leaf": 100,
        "feature_fraction": 0.8,
        "bagging_fraction": 0.8,
        "bagging_freq": 5,
        "lambda_l1": 0.1,
        "lambda_l2": 0.1,
        "label_gain": [0, 1, 3, 7],  # gain per relevance level
        "verbose": -1,
    }

    model = lgb.train(
        params,
        train_data,
        num_boost_round=500,
        valid_sets=[val_data],
        callbacks=[
            lgb.early_stopping(stopping_rounds=50),
            lgb.log_evaluation(period=50),
        ],
    )
    return model


def mmr_rerank(
    candidates: list[dict],
    top_n: int = 10,
    lambda_diversity: float = 0.3,
) -> list[dict]:
    """Maximal Marginal Relevance: balance relevance and diversity.

    candidates: list of dicts with keys: item_id, score, genre, embedding (np.ndarray)
    lambda_diversity: 0 = pure diversity, 1 = pure relevance
    """
    selected: list[dict] = []
    remaining = candidates.copy()

    while len(selected) < top_n and remaining:
        if not selected:
            # Pick highest-relevance item first
            best = max(remaining, key=lambda x: x["score"])
        else:
            selected_embs = np.stack([s["embedding"] for s in selected])

            def mmr_score(item: dict) -> float:
                relevance = item["score"]
                sims = selected_embs @ item["embedding"]  # cosine (L2-normalized)
                max_sim = float(sims.max())
                return lambda_diversity * relevance - (1 - lambda_diversity) * max_sim

            best = max(remaining, key=mmr_score)

        selected.append(best)
        remaining.remove(best)

    return selected
```

---

## ML Components Used

| Component | Technology | Role |
|-----------|-----------|------|
| Collaborative Filtering | Spark MLlib ALS | User/item embeddings from interaction matrix |
| Retrieval Model | PyTorch Two-Tower | Query-time user embedding generation |
| ANN Index | FAISS IVF4096,PQ32 | Sub-25ms candidate retrieval over 10M items |
| Ranking Model | LightGBM LambdaRank | NDCG@10 ranking over 500 candidates |
| Feature Store (online) | Redis Cluster | <10ms feature lookup during serving |
| Feature Store (offline) | Apache Hive / S3 | Training feature computation |
| Event Streaming | Apache Kafka | 1B events/day ingestion |
| Batch Processing | Apache Spark | Offline feature pipeline |
| Experiment Tracking | MLflow | Model registry, metric comparison |
| Diversity | MMR (Maximal Marginal Relevance) | Post-ranking genre diversity |

---

## Tradeoffs and Alternatives

| Decision | Chosen | Alternative | Why Chosen |
|----------|--------|-------------|-----------|
| Retrieval model | Two-tower neural | ALS matrix factorization | Two-tower handles cold start via features; ALS needs interaction history |
| ANN library | FAISS IVF+PQ | ScaNN, Hnswlib | FAISS: GPU training support, battle-tested at Meta/Google scale |
| Ranker | LightGBM | Deep ranking (DNN) | 10x faster iteration, comparable NDCG, handles missing features natively |
| Negative sampling | Popularity-weighted | Uniform random | Easy negatives degrade model; popularity-weighted surfaces hard negatives |
| Feature freshness | 24h TTL on Redis | Real-time computation | Sub-10ms lookup vs 30-50ms compute; 24h acceptable for most features |
| Diversity | MMR post-processing | Diversified training objective | MMR is runtime-controllable without retraining |

---

## Interview Discussion Points

**How do you handle the cold start problem for new users?**
New users have no watch history, so collaborative filtering is useless. Strategy: (1) Onboarding flow asking genre preferences (3-5 clicks), which seeds a genre-based embedding via the item tower. (2) During first session, use popularity-within-genre as proxy ranker. (3) After 5 interactions, enough signal exists for two-tower retrieval. New items get item-tower embedding from metadata alone; ALS score is set to the item's global average until enough interactions accumulate (warm-up threshold: 50 interactions).

**How do you prevent popularity bias where the model keeps recommending already-popular items?**
Three mitigations: (1) Popularity bias correction in two-tower training — subtract log(item_frequency) from logits so rare items are not penalized. (2) Exploration budget: 10-15% of recommendations are sampled from less-popular content matching user's genre preferences. (3) Freshness boost in re-ranking: items under 7 days old receive a +0.05 score boost, encouraging new content discovery.

**How do you evaluate recommendation quality offline vs online?**
Offline: NDCG@10 on held-out sessions, Recall@100 from retrieval, coverage (fraction of catalog recommended at least once). Online A/B test: watch time per session (primary), CTR, session length, 30-day retention. Offline NDCG correlates moderately with online watch time (Pearson ~0.6) but is not a perfect proxy — always A/B test before full rollout.

**What happens if the FAISS index becomes stale as new videos are added?**
New videos added to the catalog need to be indexed immediately or they will never be retrieved. Strategy: (1) Near-real-time index updates — new items are added to a small in-memory HNSW shard. At query time, search both FAISS IVF (existing items) and HNSW shard (new items), merge top-K from both. (2) Full index rebuild nightly to consolidate. This ensures new videos get retrieval exposure within minutes of upload.
