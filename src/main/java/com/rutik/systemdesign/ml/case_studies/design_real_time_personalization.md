# Design a Real-Time Personalization System

> "Real-time personalization is like a concierge who reads your most recent request and instantly connects it with everything they know about your past visits — without making you wait."

**Key insight:** The core tension in real-time personalization is *freshness vs stability*. Historical features (user's 12-month purchase history) are stable but stale. Session features (what the user clicked in the last 5 minutes) are fresh but noisy. The best systems maintain a two-stream architecture: a slow, stable user representation updated daily, and a fast, session representation updated in milliseconds, with a fusion layer that blends both at serving time.

Mental model: Think of personalization as having two memory systems — long-term memory (who this user is historically) and working memory (what they are doing right now). Historical collaborative filtering captures the long-term; session-based sequence models capture the working memory. Neither alone is sufficient: long-term models miss intent shifts; session models have no prior for new users or new contexts.

Why this system exists: Netflix reports that 80% of streamed content is discovered via recommendations. Amazon attributes 35% of revenue to personalized recommendations. A 1% improvement in click-through rate on the homepage recommendation row translates to tens of millions in revenue at scale.

---

## 1. Requirements Clarification

**Functional requirements:**
- Personalize recommendations on homepage, product detail pages (related items), search results re-ranking, and email recommendations.
- Support cold-start: new users (no history) and new items (no interaction data).
- Real-time session context: incorporate the user's actions in the current session (clicks, searches, add-to-cart, dwell time) into recommendations without batch delay.
- Contextual signals: device type, time of day, location, active campaign.
- Exploration: expose users to novel items outside their known preferences to avoid filter-bubble effects.

**Non-functional requirements:**
- Homepage recommendations: 100ms p99 total latency from request to response (retrieval + scoring + re-ranking).
- Throughput: 50k recommendation requests/sec (peak).
- Feature freshness: session-level features must reflect actions < 1 second old; daily features must be < 24 hours old.
- Cold-start coverage: 100% of new users must receive recommendations on first visit (no empty state).
- Availability: 99.99% — a recommendation outage degrades the user experience for every active user.

**Out of scope:**
- Push notification personalization (separate delivery system).
- Ad auction personalization (covered by ads CTR case study).
- Conversational / LLM-augmented recommendations (extends this base system).

---

## 2. Scale Estimation

**Traffic:** 50k req/sec peak, 15k req/sec average. Monthly: 50k × 86,400 × 30 = 129.6B requests (peak). Realistic average: 38.9B/month.

**Item catalog:** 10M items total; 500k "active" items (interacted with in last 30 days).

**Users:** 200M registered users; 30M daily active users (DAU).

**Feature store sizing:**
- User historical features: 200M users × 100 features × 4 bytes = 80 GB → Redis cluster.
- Session features: 30M DAU × 10 session features × 4 bytes × average 5 active sessions = 6 GB → Redis.
- Item features: 500k items × 200 features × 4 bytes = 400 MB → fits in single Redis node.

**Retrieval (two-tower ANN search):**
- Item embedding index: 500k items × 128 floats × 4 bytes = 256 MB → fits in FAISS in-memory index per machine.
- ANN retrieval (HNSW, top-100 candidates): ~2ms.

**Scoring:** 50 candidates × 1ms per item = 50ms if serial; 5ms with vectorized batch inference.

**Infrastructure cost estimate:**
- Serving cluster (50k req/sec): 20 × c5.4xlarge = $1,200/month.
- Redis cluster (feature store): 3 × r5.4xlarge = $700/month.
- Model training (weekly): 4 × A100 4hr/week = $28/month.
- Total: ~$1,928/month.

---

## 3. High-Level Architecture

```
  ┌──────────────────────────────────────────────────────────────────┐
  │                    REQUEST ENTRY                                 │
  │  User: {user_id, session_id, page_context, device, location}    │
  └───────────────────────────────┬──────────────────────────────────┘
                                  │
  ┌───────────────────────────────▼──────────────────────────────────┐
  │                FEATURE ASSEMBLY (< 10ms)                        │
  │                                                                  │
  │  ┌────────────────────┐  ┌─────────────────┐  ┌───────────────┐ │
  │  │ Historical Features│  │ Session Features│  │ Context Feats │ │
  │  │ (Redis, user_id)   │  │ (Redis,         │  │ device, time, │ │
  │  │ - embedding vector │  │  session_id)    │  │ location      │ │
  │  │ - affinities       │  │ - last 5 clicks │  │               │ │
  │  │ - price tier       │  │ - search query  │  │               │ │
  │  └────────────────────┘  │ - cart contents │  └───────────────┘ │
  │                          └─────────────────┘                    │
  └───────────────────────────────┬──────────────────────────────────┘
                                  │
  ┌───────────────────────────────▼──────────────────────────────────┐
  │              RETRIEVAL — Two-Tower ANN (< 3ms)                  │
  │  Query: fuse(user_hist_embedding, session_embedding, context)    │
  │  FAISS HNSW: top-500 candidates from 500k active items          │
  └───────────────────────────────┬──────────────────────────────────┘
                                  │
  ┌───────────────────────────────▼──────────────────────────────────┐
  │         SCORING — Interaction Model (< 20ms)                    │
  │  Score 500 candidates with full feature vector                  │
  │  Model: LightGBM or shallow DNN (rank by P(engagement))         │
  └───────────────────────────────┬──────────────────────────────────┘
                                  │
  ┌───────────────────────────────▼──────────────────────────────────┐
  │         BUSINESS RULES + DIVERSITY LAYER (< 5ms)                │
  │  - Filter: already purchased, out-of-stock, region-restricted   │
  │  - Diversity: at most 2 items from same brand/category (MMR)    │
  │  - Exploration: inject ε=0.05 random items from cold pool       │
  │  - Sponsored items: inject at designated slots                  │
  └───────────────────────────────┬──────────────────────────────────┘
                                  │
  ┌───────────────────────────────▼──────────────────────────────────┐
  │         RESPONSE (top-20 ranked items)                          │
  └──────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │                    ASYNC EVENT PROCESSING                        │
  │                                                                  │
  │  Click/view/purchase events → Kafka → Session Feature Updater   │
  │    (Flink, sub-second latency → Redis session features)         │
  │                                                                  │
  │  Daily batch: recompute user historical embeddings (Spark)      │
  │  Weekly: retrain two-tower and scoring model                    │
  └──────────────────────────────────────────────────────────────────┘
```

**Component inventory:**
- Feature assembly: Redis GET pipeline (< 5ms for 3 key lookups).
- Two-tower retrieval: FAISS HNSW index in-process; query embedding computed at serving time.
- Scoring model: LightGBM for p99 stability; optional shallow DNN for accuracy.
- Session feature updater: Flink job consuming Kafka click events, updating Redis within 500ms.
- Historical feature pipeline: Spark job, daily run, writes to Redis.

Feature store management: see [feature_store_and_point_in_time_correctness.md](cross_cutting/feature_store_and_point_in_time_correctness.md).

---

## 4. Component Deep Dives

### 4.1 Session Feature Extraction

```python
from dataclasses import dataclass, field
from datetime import datetime
import json

@dataclass
class SessionState:
    session_id: str
    user_id: str
    events: list[dict] = field(default_factory=list)
    last_updated: datetime = field(default_factory=datetime.utcnow)

    def add_event(self, event_type: str, item_id: str, dwell_seconds: float = 0.0) -> None:
        self.events.append({
            "type": event_type,  # click | view | add_to_cart | purchase | search
            "item_id": item_id,
            "dwell_seconds": dwell_seconds,
            "timestamp": datetime.utcnow().isoformat(),
        })
        self.last_updated = datetime.utcnow()

    def to_features(self) -> dict:
        """Extract session-level features for use in retrieval and scoring."""
        recent_events = self.events[-10:]  # last 10 events
        click_items = [e["item_id"] for e in recent_events if e["type"] == "click"]
        cart_items = [e["item_id"] for e in recent_events if e["type"] == "add_to_cart"]
        avg_dwell = sum(e.get("dwell_seconds", 0) for e in recent_events) / max(len(recent_events), 1)

        return {
            "session_click_count": len(click_items),
            "session_cart_count": len(cart_items),
            "last_clicked_item": click_items[-1] if click_items else None,
            "avg_dwell_seconds": avg_dwell,
            "session_length_minutes": (
                (datetime.utcnow() - datetime.fromisoformat(self.events[0]["timestamp"])).total_seconds() / 60
                if self.events else 0
            ),
            "is_high_intent": len(cart_items) > 0,
        }
```

### 4.2 Two-Tower Retrieval with Session Context

The query tower fuses the historical user embedding with a session embedding to create a context-aware query for FAISS retrieval.

```python
import torch
import torch.nn as nn
import numpy as np

class SessionEncoder(nn.Module):
    """
    Encode the last K item interactions in the current session using
    a lightweight 2-layer GRU. Runs at < 2ms for K=10 items.
    """
    def __init__(self, item_embedding_dim: int = 128, hidden_dim: int = 64):
        super().__init__()
        self.gru = nn.GRU(
            input_size=item_embedding_dim,
            hidden_size=hidden_dim,
            num_layers=2,
            batch_first=True,
            dropout=0.1,
        )
        self.projection = nn.Linear(hidden_dim, item_embedding_dim)

    def forward(self, session_item_embeddings: torch.Tensor) -> torch.Tensor:
        # session_item_embeddings: (batch, seq_len, item_embedding_dim)
        _, hidden = self.gru(session_item_embeddings)
        return self.projection(hidden[-1])  # (batch, item_embedding_dim)


class QueryTower(nn.Module):
    """
    Fuse historical user embedding + session embedding + context features
    into a single query vector for FAISS retrieval.
    """
    def __init__(self, user_dim: int = 128, context_dim: int = 32, output_dim: int = 128):
        super().__init__()
        self.fusion = nn.Sequential(
            nn.Linear(user_dim + user_dim + context_dim, 256),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(256, output_dim),
            nn.LayerNorm(output_dim),
        )

    def forward(
        self,
        user_embedding: torch.Tensor,     # (batch, user_dim) — historical
        session_embedding: torch.Tensor,  # (batch, user_dim) — from SessionEncoder
        context_features: torch.Tensor,   # (batch, context_dim) — device, time, etc.
    ) -> torch.Tensor:
        combined = torch.cat([user_embedding, session_embedding, context_features], dim=-1)
        return self.fusion(combined)  # (batch, output_dim)


class RetrievalEngine:
    """Wrap FAISS index for ANN retrieval at serving time."""

    def __init__(self, item_embeddings: np.ndarray, item_ids: list[str]):
        import faiss
        self.item_ids = item_ids
        dimension = item_embeddings.shape[1]
        self.index = faiss.IndexHNSWFlat(dimension, 32)  # M=32 neighbors per layer
        self.index.hnsw.efConstruction = 200
        self.index.hnsw.efSearch = 64
        self.index.add(item_embeddings.astype(np.float32))

    def retrieve(
        self,
        query_embedding: np.ndarray,   # (1, dimension)
        top_k: int = 500,
        exclude_item_ids: set[str] | None = None,
    ) -> list[str]:
        _, indices = self.index.search(query_embedding, top_k + len(exclude_item_ids or []))
        results = []
        for idx in indices[0]:
            if idx >= 0:
                item_id = self.item_ids[idx]
                if exclude_item_ids and item_id in exclude_item_ids:
                    continue
                results.append(item_id)
                if len(results) == top_k:
                    break
        return results
```

### 4.3 Scoring Model

**Broken approach — using a global popularity score as proxy for personalization:**

```python
# WRONG: popularity-based ranking ignores personalization entirely.
# Users who have diverse or niche interests see the same top-10 popular items
# as everyone else. CTR for popularity-based ranking: ~1.2%.
# CTR for personalized ranking: ~3.8% (observed in A/B tests).

def rank_candidates_naive(candidates: list[str], item_stats: dict) -> list[str]:
    return sorted(candidates, key=lambda i: item_stats[i]["total_views"], reverse=True)
    # CTR: 1.2% — leaves 68% of personalization value on the table
```

**Correct approach — interaction scoring with user × item features:**

```python
import lightgbm as lgb
import numpy as np
import pandas as pd
from dataclasses import dataclass

@dataclass
class ScoringFeatures:
    # User features (historical)
    user_category_affinities: list[float]  # affinity scores for top-20 categories
    user_price_tier: float                  # 0=low, 1=mid, 2=high
    user_session_count_30d: int
    user_purchase_count_30d: int

    # Session features
    session_is_high_intent: bool
    session_last_category: str
    session_length_minutes: float

    # Item features
    item_category: str
    item_price: float
    item_avg_ctr_7d: float
    item_avg_conversion_rate_7d: float
    item_days_since_last_interaction: int
    item_novelty_score: float              # 1 - overlap with user's recent history

    # Context features
    device_type: str                       # mobile | desktop | tablet
    hour_of_day: int
    is_weekend: bool


def build_interaction_features(
    user_features: dict,
    session_features: dict,
    item_features: dict,
    context_features: dict,
) -> np.ndarray:
    """Assemble a flat feature vector for LightGBM scoring."""
    return np.array([
        user_features["price_tier"],
        user_features["session_count_30d"],
        user_features["purchase_count_30d"],
        float(session_features["is_high_intent"]),
        session_features["avg_dwell_seconds"],
        session_features["session_length_minutes"],
        item_features["avg_ctr_7d"],
        item_features["avg_conversion_rate_7d"],
        item_features["days_since_last_interaction"],
        item_features["novelty_score"],
        item_features["price"],
        float(context_features["is_weekend"]),
        context_features["hour_of_day"] / 24.0,
        # Category affinity: dot product of user category affinity vector with item category
        user_features["category_affinities"][item_features["category_idx"]],
    ], dtype=np.float32)


def score_candidates_batch(
    model: lgb.Booster,
    user_features: dict,
    session_features: dict,
    candidates: list[dict],  # list of item feature dicts
    context_features: dict,
) -> list[tuple[str, float]]:
    """Vectorized batch scoring of all candidates."""
    feature_matrix = np.array([
        build_interaction_features(user_features, session_features, item, context_features)
        for item in candidates
    ])
    scores = model.predict(feature_matrix)  # (N,) predicted P(engagement)
    item_ids = [item["item_id"] for item in candidates]
    return sorted(zip(item_ids, scores), key=lambda x: x[1], reverse=True)
```

### 4.4 Exploration via Epsilon-Greedy Item Injection

To prevent filter-bubble effects and improve item coverage, inject a small fraction of novel items into every response.

```python
import random
from typing import Optional

class PersonalizationExplorer:
    """
    Injects a fraction (epsilon) of exploration items into ranked results.
    Exploration items are sampled from: (a) recently popular items the user
    hasn't seen, (b) trending items in user's adjacent categories.
    """

    def __init__(
        self,
        epsilon: float = 0.05,    # 5% of slots → 1 exploration item in top-20
        exploration_pool_size: int = 1000,
    ):
        self.epsilon = epsilon
        self.exploration_pool_size = exploration_pool_size

    def inject_exploration(
        self,
        ranked_items: list[str],
        exploration_pool: list[str],
        user_seen_items: set[str],
        n_results: int = 20,
    ) -> list[str]:
        """
        Replace epsilon fraction of ranked items with novel exploration items.
        Exploration items are injected at non-prominent positions (not top-2).
        """
        n_explore = max(1, int(n_results * self.epsilon))
        n_exploit = n_results - n_explore

        # Exploit: top ranked items (exclude top-2 exploration slots)
        exploit_items = [i for i in ranked_items if i not in user_seen_items][:n_exploit]

        # Explore: sample from novel items not in ranked results
        novel_pool = [
            i for i in exploration_pool
            if i not in set(ranked_items) and i not in user_seen_items
        ]
        explore_items = random.sample(novel_pool, min(n_explore, len(novel_pool)))

        # Interleave: inject exploration items at positions 3, 7, 12 (non-prominent)
        result = exploit_items[:2]
        exploration_positions = [2, 6, 11]  # 0-indexed
        exploit_remaining = exploit_items[2:]

        for i in range(n_results - 2):
            if i in [pos - 2 for pos in exploration_positions] and explore_items:
                result.append(explore_items.pop(0))
            elif exploit_remaining:
                result.append(exploit_remaining.pop(0))

        return result[:n_results]
```

### 4.5 Cold Start: New Users and New Items

```python
import numpy as np
from typing import Optional

def get_cold_start_user_embedding(
    context_features: dict,
    segment_embeddings: dict[str, np.ndarray],
) -> np.ndarray:
    """
    For new users (no history), derive an embedding from:
    1. Device + location → market segment embedding
    2. Referral source → intent signal
    3. Time of day / day of week → behavioral prior

    Falls back to the global popularity embedding if no context available.
    """
    # Determine market segment from context
    market_segment = f"{context_features.get('country', 'us')}_{context_features.get('device_type', 'web')}"

    if market_segment in segment_embeddings:
        base_embedding = segment_embeddings[market_segment]
    else:
        base_embedding = segment_embeddings["global_average"]

    # Adjust for time-of-day signal (morning → news/productivity, evening → entertainment)
    hour = context_features.get("hour_of_day", 12)
    time_signal = np.zeros(len(base_embedding))
    if 6 <= hour < 12:       # morning: boost informational categories
        time_signal[0] = 0.1
    elif 18 <= hour < 23:    # evening: boost entertainment categories
        time_signal[1] = 0.1

    return base_embedding + time_signal


def get_cold_start_item_embedding(
    item_metadata: dict,
    category_embeddings: dict[str, np.ndarray],
) -> Optional[np.ndarray]:
    """
    For new items (no interactions), initialize embedding from:
    1. Category embedding (warm start from category centroid)
    2. Title/description embedding (text encoder, pre-computed offline)
    Returns None if no metadata available (item is hidden until indexed).
    """
    category = item_metadata.get("category")
    if category and category in category_embeddings:
        cat_embedding = category_embeddings[category]
        # Add text-based signal if description embedding is available
        if "text_embedding" in item_metadata:
            return 0.7 * cat_embedding + 0.3 * np.array(item_metadata["text_embedding"])
        return cat_embedding
    return None
```

---

## 5. Design Decisions & Tradeoffs

**Decision 1: Two-tower retrieval vs matrix factorization vs graph-based retrieval**

| Method | Cold start | Real-time session context | AUC | Retrieval latency |
|--------|-----------|--------------------------|-----|------------------|
| Matrix factorization (ALS) | Poor | No (static embedding) | 0.74 | 2ms |
| Two-tower (static) | Moderate | No | 0.79 | 2ms |
| Two-tower + session encoder | Good | Yes | 0.85 | 4ms |
| Graph-based (PinSage) | Good | Partial | 0.87 | 15ms |

Use two-tower with session encoder. Graph-based methods achieve +2pp AUC but require 4× longer retrieval latency and a graph infrastructure that is operationally expensive. See [model_selection_and_algorithm_choice](../model_selection_and_algorithm_choice/README.md).

**Decision 2: Feature freshness SLOs — when is "real-time" needed?**

Not all features need sub-second freshness. Tiered freshness reduces infrastructure cost:
- Session-level (sub-second): last 5 clicks, active cart, current search query. Required because user intent can shift within a session. Maintained via Flink → Redis write-through.
- Daily: purchase history, category affinities, price tier. Computed via Spark batch. 24-hour staleness is acceptable — a user's long-term preferences do not change in hours.
- Weekly: user segmentation (new/active/lapsed), item popularity scores, trending items. Acceptable staleness for stable background signals.

See [feature_store_and_point_in_time_correctness.md](cross_cutting/feature_store_and_point_in_time_correctness.md) for PIT correctness during model training.

**Decision 3: Scoring model — LightGBM vs deep neural network**

LightGBM with 200 trees achieves 0.82 AUC at 5ms p99 per request (batch of 500 candidates). A 2-layer DNN achieves 0.85 AUC at 20ms p99. Given the 100ms total budget with 70ms already allocated to retrieval + feature assembly, LightGBM is the right choice for the scoring layer. The DNN is used as a teacher to distill improved GBDT leaf values (offline, weekly).

**Decision 4: Exploration rate and placement**

5% exploration (1 item in top-20) is the standard starting point. Too much exploration (> 20%) hurts short-term CTR and users notice irrelevant items. Too little (< 1%) creates filter bubbles and reduces long-term engagement (users stop discovering new content). Exploration items are injected at positions 3, 7, and 12 (not 1 or 2, which are the highest CTR positions) to minimize CTR impact while still driving discovery.

**Decision 5: Handling SUTVA in A/B tests for personalization**

Personalization A/B tests face SUTVA violations because users in the control group are affected by users in the treatment group (e.g., if treatment users buy more of an item, that item's popularity score increases, which affects control users too). Use a user-level random split with a dilution correction: compare treatment vs control within the same popularity decile of items. Alternatively, use a holdout group (5% of users receive popularity-based recommendations with no personalization) as a stable long-term control. See [experimentation_and_online_evaluation.md](cross_cutting/experimentation_and_online_evaluation.md).

---

## 6. Real-World Implementations

**Netflix (Personalized Recommendation):** Netflix's recommendation system uses a three-stage pipeline: (1) candidate generation using a two-tower model with user history + session embeddings (retrieves ~500 candidates from a 100M item catalog using FAISS); (2) ranking using a wide-and-deep model that incorporates user context, item features, and user-item interaction features; (3) post-ranking business rules (freshness boost for recently released content, diversity enforcement, sponsored placement). Their 2016 engineering blog described the shift from matrix factorization to neural two-tower models as driven by the need to incorporate context features (device, time-of-day, current show being watched) that static user embeddings cannot capture.

**Pinterest (Homefeed Personalization):** Pinterest's homefeed uses a two-stage system similar to the design above, with a key innovation in the session encoder: they use a "PinSage" graph convolutional network to encode items using both their feature vectors and their co-engagement graph structure. This is particularly valuable for visual content (pins) where the image embedding alone misses the intent context (the same pin can appear in both a "wedding decoration" board and a "DIY project" board — the graph neighborhood captures which context is relevant). Pinterest reported a 40% improvement in fresh pin engagement after deploying PinSage.

**Spotify (Session-Based Recommendations):** Spotify's "Daily Mix" and "Discover Weekly" use different personalization strategies. Daily Mix is session-contextual (what mood are you in right now — working, exercising, relaxing?). Discover Weekly is historical (what have you loved over the past 30 days that you haven't heard in 6 months?). Key insight from Spotify's engineering: context-awareness dramatically matters for music but less for podcast recommendations. For podcasts, historical listening patterns are the dominant signal; for music, session context (first track listened to, time of day, playback speed) is as important as history.

**Taobao (Alibaba):** Alibaba's recommendation system uses the largest scale two-tower deployment described in public literature: 500M users, 1B items. Their 2019 SIGIR paper described their solution to the "exposure bias" problem: items that appear higher in the recommendation list are clicked more (not because they are more relevant, but because they are more visible). They train a position-debiased scoring model using IPW (inverse propensity weighting) based on observed position CTR, similar to the approach used in search ranking systems.

**DoorDash (Restaurant Personalization):** DoorDash's recommendation system personalizes restaurant rankings for each user-location pair. Key challenge: item availability changes in real time (restaurants open/close, menu items sell out). Their system maintains a real-time availability feed that filters candidates downstream of retrieval — the FAISS index is updated daily, but out-of-stock filtering happens at serving time using Redis availability flags. They found that availability-aware ranking reduced "item unavailable" order cancellations by 23%.

---

## 7. Technologies & Tools

| Tool | Use case | Advantage | Limitation |
|------|----------|-----------|------------|
| FAISS (Meta) | ANN retrieval (HNSW/IVF) | Industry-standard, in-process, sub-millisecond | Index must fit in RAM; real-time updates require index rebuild |
| ScaNN (Google) | ANN retrieval (asymmetric quantization) | 2× faster than FAISS for large-scale (> 50M items) | Google-specific; less community tooling |
| Redis | Session features, item availability flags | Sub-millisecond GET; Streams API for Flink integration | Memory cost; no native vector search without RediSearch |
| Apache Flink | Real-time session feature computation | Stateful streaming, event-time semantics, exactly-once | Operational complexity; cluster management overhead |
| LightGBM | Scoring model | SHAP, 5ms inference for 500 candidates, stable | No native sequential feature support |
| PyTorch + HuggingFace | Session encoder (GRU), training | Flexible model architecture; GPU training | Requires ONNX export for low-latency production serving |

---

## 8. Operational Playbook

### Eval Pipeline
- **Offline metric:** AUC on held-out user-item interaction pairs (chronological split — do not use random split). Maintain a golden test set of 100k interactions from 3 months ago.
- **Online metric:** CTR, add-to-cart rate, and purchase rate on recommendation modules. Run A/B test with 5% of traffic before full rollout.
- **Coverage metric:** fraction of users receiving at least 1 "fresh" item (interacted with < 7 days ago) in top-10 recommendations. Should exceed 80%.
- **Exploration metric:** fraction of clicks on exploration items (epsilon-greedy injected). Monitor that this is approximately ε × position_CTR.

### Observability
- Monitor: P50/P99 retrieval latency, scoring latency, and total pipeline latency separately.
- Track: cache hit rate for user and item features in Redis. Drop below 95% indicates Redis OOM or cold start spike.
- Alert on: recommendation diversity collapse (all top-10 items from same brand/category for > 10% of requests).

### Incident Runbooks
1. **FAISS index stale (item index > 48 hours old):** Symptom: newly launched items not appearing in any recommendations despite positive CTR. Diagnosis: FAISS index build job failed silently. Mitigation: fall back to popularity-based candidate generation. Resolution: re-run FAISS index build; monitor job completion in Airflow.
2. **Redis latency spike (> 20ms):** Symptom: total recommendation latency exceeds 100ms p99. Diagnosis: Redis memory pressure causing eviction + rehashing or network partition. Mitigation: pre-empt with Redis connection pooling and a local L1 cache (in-process LRU of 1k most frequent user features). Resolution: scale Redis cluster or reduce TTL on low-value features.
3. **Session feature staleness:** Symptom: users report recommendations don't respond to their in-session actions. Diagnosis: Flink consumer lag on Kafka click-event topic. Resolution: scale Flink parallelism; reduce consumer group lag. Alert trigger: consumer lag > 10,000 messages.
4. **Cold-start user receives empty recommendations:** Symptom: new user's first request returns < 5 items. Diagnosis: context features absent (anonymous user, no market segment match). Resolution: ensure fallback to `global_average` segment embedding is in place; never return fewer than 20 items (expand to top-popularity items if needed).

---

## 9. Common Pitfalls & War Stories

**Pitfall 1: Training the scoring model on biased position data.** An e-commerce company trained their ranking model on historical click data, treating clicks as positive labels. The model quickly learned to favor items that had appeared in position 1 historically — not because they were more relevant, but because position 1 items get 5× more clicks (position bias). The ranking model perpetuated this bias, creating a feedback loop where popular items displaced relevant niche items. Measured business impact: category diversity in top-10 dropped by 40%; users with niche preferences showed 18% lower 30-day retention. Fix: apply inverse propensity weighting (IPW) using the observed position → CTR mapping to debias the training labels.

**Pitfall 2: Session features introducing training-serving skew.** A startup trained their personalization model using session features computed at the end of each session (e.g., total session length, all items clicked in session). At serving time, the session is ongoing — you only have the current partial session. The model was trained on features that included future-session information, inflating AUC from 0.83 to 0.91 in offline evaluation. In production, AUC was 0.79. Fix: always compute session features as of the current event timestamp — use only the N events before the current prediction point, never including events that happen after. See [feature_store_and_point_in_time_correctness.md](cross_cutting/feature_store_and_point_in_time_correctness.md).

**Pitfall 3: Filter bubble causing long-term engagement collapse.** A content platform deployed a personalization system with ε = 0.01 (1% exploration). Short-term metrics were excellent: CTR +15%, session length +8%. At the 6-month mark, MAU declined 12%. Analysis revealed that users were being served the same narrow set of content categories repeatedly and eventually disengaging. Exploration at 1% was insufficient to introduce novel content fast enough to prevent boredom. Fix: increase ε to 0.08 for users whose category diversity score has been declining for > 14 days; add a "discovery module" row to the homepage that operates at 30% exploration.

**Pitfall 4: Item embedding space not updated to reflect new catalog items.** A fashion e-commerce company had a weekly FAISS index rebuild cycle. During a major sale event, 50k new items were added to the catalog. For 7 days (until the next index rebuild), these items had no embeddings and were invisible to the recommendation system. The sale underperformed revenue targets by $1.8M because new sale items were excluded from recommendations. Fix: run incremental FAISS index updates daily for high-velocity item additions; add a "new items" candidate pool that bypasses the FAISS index for items added in the last 24 hours.

**Pitfall 5: Ignoring item-item interaction effects in diversity enforcement.** A news platform's diversity rule stated "at most 2 articles from the same publication." A user who reads mostly political news was served 2 articles from New York Times and 2 from Washington Post — both covering the exact same political event from slightly different angles. The diversity rule was technically satisfied but semantically meaningless. Fix: enforce diversity on semantic topic clusters (computed from article embeddings) rather than publication source. Topic-level diversity rule increased long-session engagement by 9%.

---

## 10. Capacity Planning

**Primary bottleneck:** FAISS retrieval and feature assembly at peak 50k req/sec.

```
Target: 50k req/sec, 100ms p99 total budget

Feature assembly (Redis pipeline):
  3 key lookups (user, session, item stats) × 200μs each = 0.6ms
  At 50k req/sec: 50k × 0.6ms = 30,000ms of Redis work/sec
  Redis throughput: 1M ops/sec per node (single thread)
  Required Redis nodes: ceil(50k × 3 ops / 1M ops/node) = 1 node (with headroom: 3 nodes HA)

FAISS retrieval:
  2ms per query × 50k req/sec = 100,000ms of CPU/sec
  FAISS per-core throughput: ~2k queries/sec (HNSW ef=64, d=128)
  Required cores: ceil(50k / 2k) = 25 cores → 7 × c5.2xlarge (4 cores each) = 28 cores

Scoring (LightGBM, 500 candidates/request):
  5ms per batch × 50k req/sec = 250,000ms CPU/sec
  LightGBM per-core throughput: ~1k batches/sec
  Required cores: ceil(50k / 1k) = 50 cores → 13 × c5.2xlarge

Total serving cost: 20 × c5.4xlarge ≈ $1,200/month (combining retrieval + scoring)
```

**Scaling to 500k req/sec (10× — Super Bowl-level traffic):**
- Redis: 30 node cluster; activate local L1 cache (process-level LRU) to absorb hot user reads.
- FAISS: 70 × c5.2xlarge; use IVF index (partition-based) to reduce per-query latency to < 0.5ms.
- Scoring: 130 × c5.2xlarge or switch to GPU scoring (2 × A10G handles 500k scoring batches/sec).
- Estimated peak cost: $12,000/month — acceptable for a business generating $50M+ daily revenue.

---

## 11. Interview Discussion Points

**Q: Why does real-time session context matter for personalization?**
User intent changes within a session. A user who arrived at the site looking for running shoes (clicking on 3 running shoe product pages) has different immediate needs from their historical profile (a casual fashion buyer). Historical features predict long-term preferences; session features predict current intent. Combining both — a session-aware query vector fusing historical embedding and recent-click encoding — achieves significantly higher CTR than either alone. In Netflix's experiments, including session context improved recommendation CTR by 20–35% versus history-only recommendations.

**Q: How do you train the two-tower model without introducing future information?**
Training data for the two-tower model uses (user, session, item) triples where the session features are computed from events strictly before the interaction event. This is a PIT correctness requirement: if the training example is "user clicked item X at 3pm," the session features must only include actions before 3pm. Random shuffling of training examples and then computing session features from the full session would leak future-session information. Always generate training examples in chronological order and compute session features using only the preceding events.

**Q: How do you handle the cold-start problem for new users?**
New users have no history, so their historical embedding is undefined. Use a hierarchy of fallbacks: (1) if signup flow captured preferences (e.g., "select categories you're interested in"), use those to initialize a weighted average of category embeddings; (2) if referral source is available (user clicked a specific product link before signing up), use that product's category embedding as a warm start; (3) if no signals are available, use a segment-based prior (segment by country × device type) derived from historical conversion rates for similar users. After the user's 3rd interaction, transition to their personalized embedding. Cold-start users get 30% more exploration (higher ε) to accelerate preference discovery.

**Q: How do you A/B test a personalization change without SUTVA violations?**
User-level A/B tests for recommendation systems are valid for most changes (different ranking models, different feature sets). SUTVA violations occur when users in different groups interact with the same items (e.g., treatment-group users buy more of an item, which changes that item's popularity score, which then affects control-group rankings). Mitigation: (1) use a popularity-debiased ranking model so item popularity is not a direct input; (2) track per-group item popularity separately during the experiment window; (3) for changes that affect global state (trending algorithms, inventory), use geo-level A/B tests.

**Q: How do you measure whether your recommendation system is creating a filter bubble?**
Three metrics: (1) category diversity in top-10 recommendations — compute entropy over category distribution; (2) novelty rate — fraction of recommended items that the user has not seen in the past 30 days; (3) serendipity — fraction of items that are both surprising (outside historical preferences) and eventually clicked. Monitor these at the individual user level: identify users whose category entropy has been declining for > 14 days — these are filter-bubble risk users. Set an organizational target: median user should see items from at least 5 distinct categories in their daily top-20.

**Q: How do you serve FAISS at 50k requests per second reliably?**
Key techniques: (1) in-process FAISS index (no network hop — load the 256 MB index into each server process at startup); (2) HNSW rather than IVF for < 10M items (lower latency at the cost of slightly more memory); (3) pre-compute and cache frequently-queried user embeddings in Redis L1 — hot users (top 1% by request volume) account for 20% of queries; cache their query vectors for 5 minutes; (4) async index refresh: FAISS index updates are expensive — rebuild the full index weekly and deploy as a binary; for intra-week item additions, maintain a small supplementary ANN index that is merged at query time; (5) request coalescing: if two users have the same session context, batch their FAISS queries (rare but useful for anonymous users on the same page).

**Q: What is the difference between collaborative filtering and content-based filtering, and when do you use each?**
Collaborative filtering recommends items based on what similar users liked, without using item content features. Content-based filtering recommends items similar to what this user liked, using item features (genre, price, description). Collaborative filtering requires interaction history and suffers from cold start for new users and new items. Content-based filtering works for cold items (only needs item metadata) but creates a "similarity trap" (only recommends items similar to what you've already liked, no serendipity). Production systems use a hybrid: two-tower retrieval uses collaborative signals (user embeddings trained on co-engagement) with item content features incorporated in the item tower. This captures both similarity (content) and "users like you" effects (collaborative).

**Q: How do you prevent the scoring model from memorizing popular items?**
The scoring model receives item features including recent CTR and popularity. Without controls, it learns to uprank popular items because popularity correlates with clicks. Three mitigations: (1) include a "user has previously seen this item" feature so the model can discount already-shown items; (2) apply position debias (IPW): when training on historical clicks, weight each positive example by 1/P(click | position) to remove the position → click correlation; (3) cap popularity features at a percentile value (clip CTR at P95) to prevent extreme popularity from dominating the score. Monitor the fraction of top-10 slots occupied by items in the top-1% popularity — should not exceed 40%.

**Q: How do you evaluate personalization quality offline when user preferences are unobservable?**
Three offline metrics: (1) AUC-ROC on held-out interaction events (did the model rank clicked items higher than non-clicked items?); (2) NDCG@K (normalized discounted cumulative gain) on a test set of user sessions; (3) catalog coverage — what fraction of the active item catalog appears in at least one user's top-20 recommendations? Good personalization should use > 30% of the catalog; poor personalization concentrates on < 5%. Key caveat: offline metrics do not capture long-term engagement effects (a model that improves 7-day retention may show slightly lower immediate CTR because it introduces more exploration). Always run online experiments before concluding from offline metrics alone.
