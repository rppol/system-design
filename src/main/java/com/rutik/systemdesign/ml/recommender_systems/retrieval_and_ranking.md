# Retrieval and Ranking

## 1. Concept Overview

The retrieval and ranking pipeline is the production architecture used by every large-scale recommendation system. It addresses a fundamental tension: a rich scoring model needs hundreds of features and milliseconds per candidate, but a catalog of 10 million items cannot be scored exhaustively per request. The solution is a two-stage pipeline — retrieval generates a small candidate set with high recall, and ranking scores that set with high precision.

Retrieval (also called candidate generation) selects 100–1000 items from millions using a fast approximate method, optimizing for recall@100 > 95%. Ranking scores those candidates with an expensive model using cross-features, user-item interaction history, and contextual signals, optimizing for NDCG@10. Re-ranking applies business rules, diversity, and freshness constraints as a final post-processing step.

---

## 2. Intuition

One-line analogy: Retrieval is casting a wide net to catch fish, ranking is sorting the catch by freshness and taste, re-ranking is putting the best-looking fish in the front of the display case.

Mental model: Imagine a librarian who first runs to a section of the library (retrieval — fast, by section sign), pulls 100 books off the shelf (candidates), then reads the back cover of each one (ranking — slower, by content), then arranges the top 10 in a display case ensuring not all are the same author (re-ranking — diversity).

Why recall matters more than precision in retrieval: if the retrieval stage misses a great item, no downstream model can recover it. A false negative at retrieval is permanent. A false positive (irrelevant item in candidates) is corrected by ranking. Therefore, retrieval optimizes recall aggressively, accepting some imprecision.

Key insight for position bias: measured CTR is not a clean signal of item relevance. Items shown at position 1 on a page receive ~10x more clicks than position 10 items of identical quality. Training a ranking model on raw clicks teaches it "was this item shown at position 1?" not "is this item good?".

---

## 3. Core Principles

**Recall-Precision Tradeoff**: Retrieval maximizes recall (catch all good items); ranking maximizes precision (rank good items first). These are complementary, not competing — the pipeline achieves both.

**Approximate Nearest Neighbor (ANN)**: Exact nearest neighbor search over 10M 256-dim vectors takes ~2 seconds. ANN (FAISS IVF, HNSW) achieves ~95% recall in <10ms by searching a subset of the index.

**Learning-to-Rank (LTR)**: Ranking models are trained with specialized losses that directly optimize ranking metrics. Three families: pointwise (treat each item independently, predict score), pairwise (relative ordering of pairs), listwise (optimize the full list ordering).

**Position Bias Correction**: Users click items at higher positions more frequently regardless of quality. Inverse propensity weighting (IPW) corrects for this by upweighting clicks at lower positions.

**Diversity Injection**: A list of 10 items all from the same category provides lower utility than 10 items from different categories. Maximal Marginal Relevance (MMR) balances relevance and diversity in re-ranking.

**Cascade Architecture**: Each stage reduces the candidate set size and increases model complexity. The cascade is designed so the most expensive model (deep ranker) sees only a small, pre-filtered candidate set.

---

## 4. Types / Architectures / Strategies

### 4.1 Retrieval Strategies

**Two-Tower ANN**: Pre-compute item vectors, serve user vector at request time, ANN search. High recall, low latency. Main production approach.

**BM25 (Text-Based)**: Keyword matching retrieval for text-heavy catalogs. Fast, interpretable. Used alongside neural retrieval in hybrid systems.

**Item-Item CF**: Given user's recent items, retrieve items similar to each. Fast offline pre-computation. Useful for "because you watched X" retrieval.

**Multiple Sources (Multi-Source Retrieval)**: Run several retrieval systems in parallel (two-tower, trending items, items from followed creators, re-engagement items) and merge candidates. Each source is quota-allocated.

### 4.2 Ranking Approaches

**Gradient Boosted Decision Trees (GBDT — XGBoost, LightGBM)**: Fast inference (<5ms for 1000 candidates), handles mixed feature types, feature importance interpretable. Industry standard for ranking.

**Deep Neural Ranking**: Wide & Deep, DeepFM for higher feature interaction capacity. Slower than GBDT but captures non-linear interactions. Often ensembled with GBDT.

**Pointwise LTR**: Binary cross-entropy on click/no-click. Simple, widely deployed. Ignores relative ordering within the candidate set.

**Pairwise LTR**: BPR, LambdaRank — for each pair (item_i, item_j), the model must rank item_i above item_j when item_i is more relevant. Directly optimizes pairwise ordering.

**Listwise LTR**: LambdaMART, ListNet — optimize the full list ranking. LambdaMART uses gradient boosting with LambdaRank gradients weighted by NDCG delta; it is the algorithm behind Bing and Yahoo! Learning to Rank winners.

### 4.3 Re-Ranking Strategies

**MMR (Maximal Marginal Relevance)**: Iteratively add items that maximize relevance(item) - lambda * max_similarity(item, already_added_items). Lambda controls diversity-relevance tradeoff.

**Determinantal Point Process (DPP)**: Probabilistic model for diverse subset selection. More principled than MMR but computationally heavier.

**Rule-Based Re-Ranking**: Business rules: max 3 items per category, freshness boost (published < 24h ago gets +0.1 score), sponsored items inserted at fixed positions.

---

## 5. Architecture Diagrams

### 5.1 Full Retrieval-Ranking-Reranking Pipeline

```
        USER REQUEST (user_id, context)
                    |
        +-----------v-----------+
        |    FEATURE FETCH      |
        |  User features: Redis  |
        |  Context: request      |
        +-----------+-----------+
                    |
        +-----------v-----------+
        |    RETRIEVAL STAGE    |
        |                       |
        |  Source 1: Two-Tower  |  500 candidates, recall@100 ~95%
        |  Source 2: Item-Item  |  200 candidates (based on recent)
        |  Source 3: Trending   |  100 candidates
        |                       |
        |  Merge + Deduplicate  |  ~700 unique candidates
        |  Score: retrieval_score|
        +-----------+-----------+
                    |
        +-----------v-----------+
        |     RANKING STAGE     |
        |                       |
        |  Features per candidate:|
        |  - user x item cross  |
        |  - item quality score |
        |  - user-item history  |
        |  - context (time, geo)|
        |                       |
        |  Model: LightGBM or   |
        |  Wide & Deep          |
        |  Latency: <15ms       |
        |  Output: top-50 scored|
        +-----------+-----------+
                    |
        +-----------v-----------+
        |    RE-RANKING STAGE   |
        |                       |
        |  Diversity (MMR)      |
        |  Freshness boost      |
        |  Business rules       |
        |  Deduplication        |
        |                       |
        |  Output: top-10       |
        +-----------+-----------+
                    |
              FINAL RESULTS
```

### 5.2 Position Bias Effect

```
Position:   1       2       3       5       10
CTR:        8%      4.5%    3%      1.5%    0.8%

True quality:  All items of identical quality = 2% CTR if shown at position 5

Naive model trained on raw clicks:
  Learns: "position 1 items are 10x better than position 10 items"
  Result: always recommends items that were historically at position 1

Debiased model (IPW):
  Upweights click at position 10 by factor 10 (relative to position 1)
  Learns true item quality independent of historical exposure position
```

### 5.3 LambdaMART Gradient Illustration

```
For user u with items [A, B, C, D] ranked by model:
  Model rank:  [A=0.9, C=0.7, B=0.5, D=0.3]
  True relevance: [A=high, B=high, C=low, D=low]

Error: B (high relevance) should rank above C (low relevance)

LambdaRank gradient for pair (B, C):
  lambda_BC = |delta_NDCG(B, C)| * sigmoid(score_C - score_B)

  delta_NDCG: how much does swapping B and C improve NDCG?
  sigmoid term: confidence that B and C are mis-ranked

  Large |delta_NDCG| + large sigmoid = large gradient correction
  Items that matter most for NDCG get the largest corrections
```

---

## 6. How It Works — Detailed Mechanics

```python
from __future__ import annotations

import numpy as np
import faiss
from dataclasses import dataclass
from typing import Optional
import time


# ─────────────────────────────────────────────────────────────────────────────
# FAISS IVF Retrieval
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class FAISSConfig:
    n_lists: int = 256        # IVF: number of Voronoi cells (clusters)
    nprobe: int = 32          # number of cells to search at query time
    dimension: int = 256      # embedding dimension


class ANNRetrieval:
    """Approximate Nearest Neighbor retrieval using FAISS IVF.

    IVF (Inverted File) index:
    1. Training: K-means clusters all item vectors into n_lists centroids
    2. Storage: each item assigned to nearest centroid
    3. Search: for query vector, find nprobe nearest centroids,
               search only items in those centroids
    Tradeoff: nprobe=1 is fastest (low recall); nprobe=n_lists is exact (slow)
    """

    def __init__(self, cfg: FAISSConfig) -> None:
        self.cfg = cfg
        self.index: Optional[faiss.Index] = None
        self.item_ids: Optional[np.ndarray] = None

    def build_index(
        self,
        item_vectors: np.ndarray,  # (N, D) float32
        item_ids: np.ndarray,      # (N,) int64
    ) -> None:
        N, D = item_vectors.shape
        assert D == self.cfg.dimension, f"Expected dim {self.cfg.dimension}, got {D}"
        assert item_vectors.dtype == np.float32, "FAISS requires float32"

        # IVF index with inner product (cosine sim if L2-normalized)
        quantizer = faiss.IndexFlatIP(D)
        self.index = faiss.IndexIVFFlat(quantizer, D, self.cfg.n_lists, faiss.METRIC_INNER_PRODUCT)

        print(f"Training IVF index on {N} vectors...")
        t0 = time.time()
        self.index.train(item_vectors)   # K-means to find n_lists centroids
        self.index.add(item_vectors)     # assign each item to nearest centroid
        print(f"Index built in {time.time() - t0:.2f}s  ({N} items, {D}-dim)")

        self.index.nprobe = self.cfg.nprobe
        self.item_ids = item_ids

    def search(
        self,
        user_vector: np.ndarray,  # (D,) float32, L2-normalized
        top_k: int = 500,
        filter_item_ids: Optional[set[int]] = None,
    ) -> list[tuple[int, float]]:
        """Return top-k nearest items with their similarity scores."""
        assert self.index is not None, "Call build_index() first"
        query = user_vector.reshape(1, -1).astype(np.float32)

        # Over-fetch to account for filtered items
        fetch_k = top_k + (len(filter_item_ids) if filter_item_ids else 0) + 50
        fetch_k = min(fetch_k, len(self.item_ids))

        scores, indices = self.index.search(query, fetch_k)  # (1, fetch_k)
        scores, indices = scores[0], indices[0]

        results: list[tuple[int, float]] = []
        for idx, score in zip(indices, scores):
            if idx == -1:  # FAISS returns -1 for missing results
                continue
            item_id = int(self.item_ids[idx])
            if filter_item_ids and item_id in filter_item_ids:
                continue   # filter already-interacted items
            results.append((item_id, float(score)))
            if len(results) >= top_k:
                break

        return results


# ─────────────────────────────────────────────────────────────────────────────
# Learning-to-Rank: Pointwise, Pairwise, Listwise
# ─────────────────────────────────────────────────────────────────────────────

import torch
import torch.nn as nn
import torch.nn.functional as F


class PointwiseRanker(nn.Module):
    """Pointwise ranking: predict click probability per (user, item) pair."""

    def __init__(self, n_features: int, hidden_dims: tuple[int, ...] = (256, 128)) -> None:
        super().__init__()
        layers: list[nn.Module] = []
        in_dim = n_features
        for h in hidden_dims:
            layers += [nn.Linear(in_dim, h), nn.ReLU(), nn.Dropout(0.1)]
            in_dim = h
        layers.append(nn.Linear(in_dim, 1))
        self.net = nn.Sequential(*layers)

    def forward(self, features: torch.Tensor) -> torch.Tensor:
        return torch.sigmoid(self.net(features)).squeeze(-1)  # (B,) CTR probability

    def loss(self, pred: torch.Tensor, labels: torch.Tensor) -> torch.Tensor:
        return F.binary_cross_entropy(pred, labels)


def pairwise_bpr_loss(
    pos_scores: torch.Tensor,   # (B,) scores for positive items
    neg_scores: torch.Tensor,   # (B,) scores for negative items
) -> torch.Tensor:
    """Bayesian Personalized Ranking pairwise loss.

    Maximize P(pos_score > neg_score) = sigmoid(pos - neg).
    Loss = -mean(log(sigmoid(pos - neg)))
    """
    return -F.logsigmoid(pos_scores - neg_scores).mean()


def lambda_rank_loss(
    scores: torch.Tensor,       # (B, n_items) model scores for each user
    relevances: torch.Tensor,   # (B, n_items) ground truth relevance (0/1 or graded)
    eps: float = 1e-10,
) -> torch.Tensor:
    """Simplified LambdaRank loss (listwise, NDCG-optimizing).

    LambdaRank reweights pairwise gradients by |delta_NDCG| for each swap.
    This implementation approximates the weighting for illustration.
    """
    B, n_items = scores.shape

    # Compute ideal DCG for normalization
    sorted_rel, _ = torch.sort(relevances, dim=-1, descending=True)
    positions = torch.arange(1, n_items + 1, dtype=torch.float32, device=scores.device)
    discounts = 1.0 / torch.log2(positions + 1)
    idcg = (sorted_rel * discounts).sum(dim=-1)  # (B,)

    # For each pair (i, j) where rel_i > rel_j, compute lambda_ij
    total_loss = torch.tensor(0.0, device=scores.device)
    for b in range(B):
        score_b = scores[b]        # (n_items,)
        rel_b = relevances[b]      # (n_items,)

        # All pairs where i is more relevant than j
        rel_diff = rel_b.unsqueeze(1) - rel_b.unsqueeze(0)  # (n_items, n_items)
        valid_pairs = rel_diff > 0   # (n_items, n_items)

        score_diff = score_b.unsqueeze(0) - score_b.unsqueeze(1)  # (n_items, n_items)
        # Lambda = sigmoid(-score_diff) * |delta_NDCG| (simplified: use |rel_diff| as proxy)
        lambda_weights = torch.abs(rel_diff) / (idcg[b] + eps)
        pair_loss = F.binary_cross_entropy_with_logits(
            score_diff[valid_pairs],
            torch.ones(valid_pairs.sum(), device=scores.device),
            weight=lambda_weights[valid_pairs],
        )
        total_loss += pair_loss

    return total_loss / B


# ─────────────────────────────────────────────────────────────────────────────
# Position Bias Correction via Inverse Propensity Weighting
# ─────────────────────────────────────────────────────────────────────────────

def estimate_position_propensity(
    click_logs: list[dict],   # [{"item_id": x, "position": p, "clicked": 0/1}]
    n_positions: int = 20,
) -> np.ndarray:
    """Estimate propensity P(observed | position) from randomized traffic.

    Requires a fraction of traffic (5-10%) where items are shown at random
    positions regardless of their ranked order (randomization experiment).
    On randomized traffic, CTR differences across positions = pure position effect.
    """
    position_clicks = np.zeros(n_positions)
    position_impressions = np.zeros(n_positions)

    for log in click_logs:
        p = log["position"] - 1   # 0-indexed
        if p < n_positions:
            position_impressions[p] += 1
            position_clicks[p] += log["clicked"]

    # Propensity = CTR at each position (relative to position 1)
    ctr_per_position = position_clicks / (position_impressions + 1e-9)
    propensity = ctr_per_position / (ctr_per_position[0] + 1e-9)
    return propensity   # propensity[0] = 1.0 (reference); propensity[9] ~= 0.1


def ipw_weighted_loss(
    pred_ctr: torch.Tensor,          # (B,) predicted CTR
    clicked: torch.Tensor,           # (B,) binary click label
    positions: torch.Tensor,         # (B,) 0-indexed positions (0=top)
    propensities: torch.Tensor,      # (n_positions,) estimated propensity per position
) -> torch.Tensor:
    """IPW-corrected binary cross-entropy loss.

    Upweights clicks at low-propensity (lower) positions.
    Correctly attributes click to item quality, not position.
    """
    pos_propensity = propensities[positions]           # (B,) propensity for each example
    weights = 1.0 / (pos_propensity + 1e-6)            # IPW weights
    weights = weights / weights.mean()                  # normalize to mean 1
    bce = F.binary_cross_entropy(pred_ctr, clicked, reduction="none")  # (B,)
    return (bce * weights).mean()


# ─────────────────────────────────────────────────────────────────────────────
# Maximal Marginal Relevance (MMR) Re-Ranking
# ─────────────────────────────────────────────────────────────────────────────

def mmr_rerank(
    candidates: list[tuple[int, float]],   # [(item_id, relevance_score), ...] sorted by score
    item_embeddings: dict[int, np.ndarray],  # item_id -> embedding vector
    top_k: int = 10,
    lambda_mmr: float = 0.5,               # 0=max diversity, 1=max relevance
) -> list[tuple[int, float]]:
    """Maximal Marginal Relevance iterative re-ranking.

    At each step, select the item maximizing:
      MMR(i) = lambda * relevance(i) - (1 - lambda) * max_j_in_selected sim(i, j)

    Higher lambda = more relevance-focused; lower lambda = more diversity.
    Typical lambda: 0.5 for homepage; 0.7 for search results.
    """
    if not candidates:
        return []

    selected: list[tuple[int, float]] = []
    remaining = list(candidates)

    while remaining and len(selected) < top_k:
        best_item_id: Optional[int] = None
        best_mmr_score = float("-inf")
        best_relevance = 0.0

        for item_id, relevance in remaining:
            if item_id not in item_embeddings:
                mmr_score = lambda_mmr * relevance   # no embedding: use relevance only
            elif not selected:
                mmr_score = relevance   # first item: pure relevance
            else:
                # Max similarity to already-selected items
                item_vec = item_embeddings[item_id]
                item_norm = np.linalg.norm(item_vec) + 1e-9
                max_sim = max(
                    float(np.dot(item_vec, item_embeddings[sel_id]) /
                          (item_norm * np.linalg.norm(item_embeddings[sel_id]) + 1e-9))
                    for sel_id, _ in selected
                    if sel_id in item_embeddings
                )
                mmr_score = lambda_mmr * relevance - (1 - lambda_mmr) * max_sim

            if mmr_score > best_mmr_score:
                best_mmr_score = mmr_score
                best_item_id = item_id
                best_relevance = relevance

        if best_item_id is None:
            break
        selected.append((best_item_id, best_relevance))
        remaining = [(iid, s) for iid, s in remaining if iid != best_item_id]

    return selected
```

---

## 7. Real-World Examples

**Bing Search Ranking (LambdaMART)**: Microsoft used LambdaMART (gradient boosted trees with LambdaRank gradients) as the primary ranking algorithm for Bing search results. LambdaMART directly optimizes NDCG — it computes the gradient contribution of each pairwise swap weighted by the NDCG improvement that swap would cause. Yahoo! Learning to Rank Challenge (2010) winner used LambdaMART. LightGBM's lambdarank loss is a production-ready implementation.

**YouTube Multi-Source Retrieval**: YouTube's ranking system draws candidates from multiple retrieval sources with quotas: personalized two-tower (majority of candidates), trending videos (10-15%), subscribed channels (10-15%), re-engagement (videos started but not completed). Each source runs independently; candidates are merged and de-duplicated before ranking. This ensures diversity in the candidate pool before ranking optimizes for individual relevance.

**Airbnb Position Bias Study**: Airbnb's search team ran a randomization experiment (10% of searches showed listings in random order) and found CTR for position 1 was 7.3x higher than position 10 for listings of identical underlying booking rate. They used IPW to correct training labels for their ranking model, resulting in +7% booking rate improvement because the model stopped over-ranking listings that had merely been shown at position 1 historically.

**Pinterest MMR for Diversity**: Pinterest applies MMR in their re-ranking stage to prevent the homepage from showing 10 pins all from the same category. lambda = 0.6 (slight diversity emphasis). They found that users who received diverse recommendations had 15% higher 30-day retention than users receiving purely relevance-maximizing recommendations, even if the latter had higher initial CTR.

---

## 8. Tradeoffs

| Ranking Method | Training | Inference | NDCG Quality | Features Supported |
|---------------|----------|-----------|-------------|-------------------|
| Pointwise (BCE) | Fast | Very Fast | Baseline | All |
| Pairwise (BPR) | Medium | Same as pointwise | Better | All |
| LambdaMART | Medium | Very Fast (GBDT) | Best for tabular | Tabular only |
| Neural Ranker | Slow | Medium | Best with deep features | All + interactions |

| ANN Index | Recall@100 | QPS (1M vecs, D=256) | Memory | Notes |
|-----------|------------|---------------------|--------|-------|
| Brute Force (Flat) | 100% | ~500 | Low | Exact; scales linearly |
| FAISS IVF | ~95% | ~50K | Low | Good balance |
| FAISS HNSW | ~98% | ~30K | High (graph) | Best recall, higher RAM |
| ScaNN | ~95-97% | ~100K | Medium | Google's; fastest QPS |
| Annoy | ~90% | ~20K | Low | Simple; Spotify |

| nprobe (IVF) | Recall@100 | Latency (ms) |
|--------------|------------|--------------|
| 8 | ~85% | 1 |
| 32 | ~93% | 3 |
| 64 | ~96% | 6 |
| 128 | ~98% | 12 |
| 256 (= n_lists) | ~100% (exact) | 25 |

---

## 9. When to Use / When NOT to Use

**Use two-tower ANN retrieval when:**
- Catalog > 100K items (brute force is too slow)
- You have rich user/item features for embedding
- Latency budget allows 5-10ms for retrieval

**Use BM25 retrieval when:**
- Catalog is text-heavy (articles, job listings, products with descriptions)
- Users express intent via explicit queries
- Interpretability of "why this candidate" is required

**Use LambdaMART for ranking when:**
- Feature set is primarily tabular (no image/text deep features)
- Low latency budget (<5ms for ranking 500 candidates)
- Team has gradient boosting expertise
- Baseline GBDT already competitive

**Use neural ranker when:**
- Feature interactions are complex and non-linear
- You have dense features (embeddings from item towers)
- Training data is abundant (>100M labeled examples)

**Use IPW position bias correction when:**
- Your training data includes logged rankings (position of each shown item is known)
- You have randomized traffic (even 5%) to estimate propensities
- You observe that your model over-indexes on items that were historically position 1

**Do NOT use MMR when:**
- Catalog is highly diverse already (diversity is free)
- Ranking quality is the primary metric (MMR sacrifices relevance for diversity)
- lambda_mmr is not tuned — a wrong lambda can hurt both relevance and diversity

---

## 10. Common Pitfalls

**Pitfall 1 — Training ranking model on biased logs without position correction**: A team trained a LightGBM ranker on 6 months of click logs. After deployment, the model consistently recommended items that had previously been shown at position 1 in the old system. The model had learned historical position as a feature. Diagnosis: feature importance showed "avg_historical_position" was the #1 feature. Fix: remove historical position from features; implement IPW weighting using a 5% randomized traffic holdout; retrain. Click-through rate on positions 5-10 increased by 40% after debiasing.

**Pitfall 2 — nprobe too low causing recall collapse**: FAISS IVF was configured with nprobe=8 (optimized for latency). Recall@100 was 82% — meaning 18% of truly relevant items never reached the ranking stage. The ranking model improved NDCG@10 by 20% in offline evaluation, but online A/B test showed zero improvement because the items the model wanted to rank were missing from candidates. Fix: benchmark nprobe vs. recall curve for your data; set nprobe to achieve >93% recall@100; latency is usually acceptable.

**Pitfall 3 — Re-ranking diversity removing all high-quality items**: lambda_mmr was accidentally set to 0.1 (nearly pure diversity). The top-10 recommendations were maximally diverse but maximally irrelevant — one item from 10 different categories, none matching user preferences. Fix: default lambda_mmr = 0.5–0.7; always A/B test diversity changes before deploying; monitor both CTR and diversity as separate metrics.

**Pitfall 4 — Feature leakage in ranking feature engineering**: A ranking feature was "item was clicked by the user in the past 24h" — a feature that included future interactions relative to the training example timestamp. The model learned to recommend items users had already interacted with (because the label was computed from data after the training timestamp). Fix: enforce strict temporal cutoffs in feature computation — all features for a training example must use only data available before the example's timestamp.

**Pitfall 5 — Retrieval recall at 95% but ranking at wrong items**: The retrieval stage achieved 95% recall@100, but the ranking stage's NDCG@10 remained poor. Investigation showed the 5% of missed items were disproportionately the most relevant ones — the items users clicked on most were also the most popular, and the IVF index clustered them together in centroid regions not searched by nprobe=32. Fix: analyze which items are most frequently in ground truth but not in retrieved candidates; if they cluster in specific categories, increase n_lists (more fine-grained clusters) or add those items as a separate "always-include" source.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| FAISS | ANN retrieval | IVF, HNSW, PQ; by Facebook; GPU support |
| ScaNN | ANN retrieval | Highest QPS; by Google; used in Google Search |
| Annoy | ANN retrieval | Simple tree-based; used by Spotify; no GPU |
| Hnswlib | ANN retrieval | Pure HNSW; best recall; higher RAM |
| Weaviate / Pinecone | Managed vector DB | Managed ANN with filtering; cloud-native |
| LightGBM | Ranking | lambdarank loss; fastest GBDT inference |
| XGBoost | Ranking | Mature; cross-platform; slightly slower than LightGBM |
| PyTorch | Neural ranking | Custom listwise losses |
| RankLib | LTR research | Reference implementations of LambdaMART, ListNet |
| Vowpal Wabbit | Online LTR | Streaming learning, contextual bandits |

---

## 12. Interview Questions with Answers

**Q: Why does a recommendation system use a two-stage retrieval-ranking architecture instead of ranking all items directly?**
Ranking all items with a rich model is computationally infeasible at scale. For 10M items and a 10ms latency budget, you have 1 microsecond per item — not enough even for a single linear model evaluation. The two-stage design solves this: retrieval uses a dot product ANN search (~5ms for 10M items) to reduce to 500 candidates, and ranking then applies a deep model to just those 500 candidates (<10ms). The retrieval stage accepts some imprecision (recall@100 ~95%) in exchange for speed; the ranking stage uses that precision budget to optimize final ordering.

**Q: What is position bias and how do you correct for it?**
Position bias is the observation that users click items at higher positions more frequently regardless of item quality — position 1 receives roughly 10x the clicks of position 10 for identical items. If you train a ranking model on raw click data, it learns that "being shown at position 1" is a proxy for quality, leading to a feedback loop where historically top-ranked items dominate recommendations. Correction: inverse propensity weighting (IPW). Run a small fraction (5-10%) of randomized traffic where items are shown at random positions; estimate propensity P(click | position) from this data; upweight clicks at lower positions by 1/propensity during training. This recovers the unbiased estimate of true item quality.

**Q: What is LambdaMART and how does it optimize NDCG?**
LambdaMART is gradient boosted decision trees where the gradient (lambda) at each step is the LambdaRank gradient rather than the standard MSE gradient. LambdaRank computes, for each pair of items (i, j) where i is more relevant than j, the gradient contribution as: lambda_ij = sigma(-score_diff) * |delta_NDCG(i, j)|, where delta_NDCG is the improvement in NDCG from swapping i and j in the ranked list. Items whose swap would cause the largest NDCG improvement receive the largest gradients — NDCG is directly baked into the training signal. MART (Multiple Additive Regression Trees) is gradient boosting; combining with LambdaRank gives LambdaMART, winner of multiple learning-to-rank competitions.

**Q: What FAISS index type would you choose for 10M items at 95% recall and <10ms latency?**
FAISS IVFFlat with n_lists = 1024-4096 (rule of thumb: sqrt(N) to 4*sqrt(N) for N items) and nprobe tuned to achieve 95% recall. At 10M items with D=256, IVF256 with nprobe=64 typically achieves ~93-95% recall at ~5ms latency on CPU. For higher recall (98%) with acceptable latency, HNSW achieves this but consumes more RAM (graph structure ~4 bytes per edge per vector). If throughput is the priority, ScaNN (Google) achieves higher QPS than FAISS IVF at equivalent recall. For a 10M item catalog, a single FAISS IVF index fits in ~10GB RAM (float32) or ~2.5GB with product quantization (PQ, slight recall drop).

**Q: How do you handle multiple retrieval sources in a candidate generation pipeline?**
Each source runs independently and generates candidates with a relevance score. Common sources: personalized two-tower (majority share, ~500 candidates), trending/popular items (10-15%), subscribed creators' content, re-engagement items (videos 50% watched but abandoned). After each source runs, candidates are merged and de-duplicated (same item from two sources: keep the higher score or blend). Each source gets a quota (minimum number of candidates it contributes) to ensure diversity even if the two-tower dominates. The merged ~700 candidates then pass to the ranking stage. The key principle: each source captures a different signal (personalization, novelty, social), and the ranking model learns to weigh them.

**Q: What is Maximal Marginal Relevance (MMR) and when do you use it?**
MMR is a greedy re-ranking algorithm that balances relevance and diversity. At each step, it selects the next item maximizing: lambda * relevance(i) - (1 - lambda) * max_j_selected similarity(i, j). Lambda = 1 means pure relevance ordering; lambda = 0 means pure diversity (farthest-first traversal). MMR is used in re-ranking when you want to prevent the final list from containing 10 items from the same category. It is O(K * M) per recommendation list (K = final list size, M = candidate set size), which is acceptable for small K and M. For larger sets, Determinantal Point Processes (DPP) give a more principled probabilistic approach but are computationally heavier.

**Q: What is the difference between pointwise, pairwise, and listwise learning-to-rank?**
Pointwise LTR treats each (user, item) pair independently, predicting a relevance score or click probability — standard binary cross-entropy. Simple but ignores the ranking context (an item's optimal score depends on the other items in the list). Pairwise LTR (BPR, RankNet) considers pairs: for each pair (item_i, item_j), the model should rank item_i above item_j if item_i is more relevant. Directly optimizes pairwise ordering but not the list-level metric. Listwise LTR (LambdaMART, ListNet, SoftRank) optimizes the full list ordering — the gradient accounts for the effect of each item's score on the list-level metric (NDCG, MAP). Listwise achieves the best NDCG but is hardest to implement.

**Q: How would you tune nprobe in a FAISS IVF index for production?**
Run a benchmark over a representative sample of 10K user queries. For each nprobe value from 8 to n_lists, measure: (1) recall@100 vs. exact brute-force ground truth, (2) latency (p50, p95, p99). Plot the recall-latency curve. Select the nprobe at the "elbow" — the point of diminishing recall return before latency grows steeply. Typical elbow: nprobe = n_lists/4 to n_lists/8. Example: for n_lists=256, nprobe=32-64 achieves ~93-96% recall at 3-6ms. Set a hard latency SLA (e.g., p99 < 10ms) and choose the highest nprobe satisfying it. Re-benchmark quarterly as the catalog changes.

**Q: Describe how you would implement a recall@K evaluation for a retrieval system.**
Temporal split: train retrieval model on interactions before date T. For each user in the test set (interactions at T+1 to T+7): run retrieval to get top-K candidates; compute recall@K = |retrieved ∩ actual_interactions| / |actual_interactions|. Average across all test users. Implementation pitfalls: ensure retrieved candidates do not include already-interacted items; use the same filtering as production (otherwise offline recall inflates). Target: recall@100 > 93% for good downstream ranking. Also measure recall@10, @50, @200 to understand the recall curve. Report separately for cold-start users (fewer than 5 historical interactions) vs. warm users.

**Q: How would you detect and address a feedback loop in a recommendation system?**
Detection signals: (1) item concentration — top-100 recommended items have decreasing entropy week-over-week; (2) catalog coverage declining (fewer unique items recommended); (3) long-tail item impressions dropping; (4) recommendation diversity (ILD) declining. Address feedback loops: (1) epsilon-greedy exploration — show 5-10% of impressions randomly; (2) popularity discount in ranking — divide item scores by log(1 + popularity); (3) novelty constraints in re-ranking (force fraction of recommendations to be items the user has not seen recently); (4) UCB or Thompson Sampling bandit for exploration-aware ranking. Monitor diversity metrics as first-class production metrics alongside CTR.

**Q: What is the hardest engineering challenge in deploying a retrieval-ranking pipeline at scale?**
Feature freshness consistency is the hardest challenge. The ranking model uses dozens of features: some are pre-computed offline (item quality score, category), some are near-real-time (item click rate in last hour), some are real-time (user's last 3 clicks this session). Stale features cause silent degradation — the model was trained with fresh features but served with stale ones. Production solution: tiered feature stores. Redis for session-level real-time features (TTL 1 hour). A stream processing layer (Flink, Kafka Streams) for near-real-time aggregations (last-hour click rate, updated every 5 min). Batch feature store (Spark → Parquet) for slow-changing features (updated nightly). The feature vector assembled at serving time must match the exact feature distribution the model was trained on — data skew between training and serving is the #1 cause of model underperformance in production.

**Q: How do you handle the cold-start problem specifically in the retrieval stage?**
Two-tower ANN requires a user vector — but new users have no history to compute one from. Strategies: (1) fallback source — include a "popular items" retrieval source that always contributes 100-200 popular candidates for all users, including new ones; (2) content-based initialization — use any available user signals (country, device, referral source) in the user tower to compute an approximate vector even without interaction history; (3) demographic cluster centroids — cluster the user population into K demographic groups; assign new user to a cluster based on observable attributes; use the cluster centroid as user vector. As interactions accumulate (threshold: ~5 interactions), switch to full personalized retrieval. Track cold-start recall@100 separately from warm user recall — the delta quantifies how much new users are underserved.

**Q: What is the role of the ranking stage's latency budget in system design?**
The ranking stage must score all retrieved candidates within the overall latency SLA. If end-to-end SLA is 100ms and retrieval takes 10ms, feature fetch takes 20ms, and network overhead is 10ms, the ranking model has ~60ms. For 500 candidates, that is 0.12ms per candidate — achievable with GBDT (LightGBM) which can score 1000 candidates in ~2ms. A deep neural ranker may take 20-50ms for 500 candidates with batched GPU inference, still within budget. Design principles: score all candidates in a single batched forward pass (not sequentially); pre-cache static item features to avoid per-candidate feature fetches; use quantized models (INT8) for neural rankers to halve inference time. Always benchmark the ranking model latency under the peak QPS load, not average — p99 latency under load is the binding constraint.

**Q: How do you prevent feature leakage in ranking model training?**
Feature leakage occurs when a training feature was computed using information that would not be available at the time of prediction. Common leakage patterns: (1) item statistics computed over the full dataset including the future (e.g., total item clicks including the training example's click); fix: compute item statistics with a time offset — use item stats from 24h before the training example timestamp; (2) user features that include the interaction being predicted (e.g., "user's average rating" including the current rating); fix: exclude the current interaction from all user aggregations; (3) joint features computed from test period signals leaking into training; fix: strict point-in-time correct feature computation using feature stores with temporal joins. Test for leakage: a model with suspiciously high offline metrics but poor online performance is a red flag — investigate feature importances for any feature correlated with the label beyond causal expectation.

**Q: What metrics do you monitor in production for a retrieval-ranking system?**
Retrieval metrics: recall@100 (sample-tested against brute force weekly), candidate set size distribution, retrieval latency p50/p95/p99. Ranking metrics: NDCG@10 on offline test set (run daily), ranking model inference latency. End-to-end metrics: CTR by position (watch for position 1/position 2 ratio — should be stable), session engagement rate, conversion rate, return rate. Health metrics: candidate diversity (unique categories in retrieved set), item coverage (unique items recommended across all users in 24h), long-tail item exposure fraction (items with < 1000 total clicks). Alert thresholds: if recall@100 drops more than 3% week-over-week (IVF index staleness), if CTR at any position drops more than 10% day-over-day (potential ranking bug), if catalog coverage drops more than 15% (feedback loop forming).

---

## 13. Best Practices

1. Set nprobe for FAISS IVF to achieve at least 93% recall@100 before worrying about latency optimization. Low recall at retrieval cannot be recovered downstream.
2. Always implement at least one non-personalized retrieval source (trending, popular) — purely personalized retrieval fails for cold-start users.
3. Use temporal train/test split with a gap (e.g., train on day 1-80, validate day 81-90, test day 91-100) to prevent leakage.
4. Apply IPW position bias correction from the beginning of the project — retroactively debiasing training data is difficult.
5. Monitor recall@100 weekly by running a sample of user queries against exact brute-force ground truth. ANN index can drift as catalog grows.
6. Keep the candidate set to 500-1000 items before ranking. Larger sets increase ranking latency without proportional recall gain.
7. Separate retrieval and ranking model training — do not jointly train end-to-end; the objectives conflict (recall vs. precision).
8. Implement diversity (MMR or category cap) in re-ranking by default. Pure relevance optimization creates filter bubbles.
9. Log positions for all impressions, not just clicks — position information is required for IPW debiasing.
10. Use a cascade of increasingly expensive models: two-tower → GBDT ranker → neural re-ranker. Add complexity only when simpler stages saturate.

---

## 14. Case Study

**Problem**: A job platform (10M users, 3M job listings, 30M applications/month) wants to build a retrieval-ranking pipeline for personalized job recommendations. New listings added daily (5K new jobs/day). 20% of users are new to the platform.

**Pipeline Design**:

```
RETRIEVAL (multiple sources):
  Source 1: Two-Tower ANN
    User tower: [user_id, skills, job_history, location] -> 256-dim
    Item tower: [job_id, title_embedding (SBERT), company, location, salary] -> 256-dim
    FAISS IVFFlat: n_lists=512, nprobe=64, recall@100 ~95%
    Returns: 400 candidates

  Source 2: Skills-Keyword Match (BM25)
    User's stated skills matched against job requirements
    Returns: 150 candidates

  Source 3: New Jobs (freshness source)
    All jobs posted in last 48h matching user's location + title
    Returns: 50 candidates

  Merge + Deduplicate: ~500 unique candidates

RANKING (LightGBM, lambdarank loss):
  Features (per candidate):
    user: embedding, seniority level, avg salary expectation
    item: match score, company prestige rank, days since posted
    cross: location_match, skills_overlap_count, salary_fit_score
    context: day_of_week, device, time_since_last_application
  IPW: position propensities estimated from 8% randomized traffic
  Latency: 8ms for 500 candidates
  Output: top-30

RE-RANKING:
  Max 5 jobs per company (prevents one employer dominating)
  Freshness boost: jobs < 24h old get +0.05 score
  Final output: top-15

COLD START:
  New users: BM25 source + skills from signup form
  Skills-based two-tower initialization (use mean of similar users' vectors)
  Separate NDCG tracking for new vs. returning users

RESULTS:
  Application rate: +25% vs. keyword-search-only baseline
  Cold-start application rate: +12% (vs. popularity fallback)
  Time-to-first-application (new users): -40% (better cold start)
  Recall@100 two-tower: 94.7% (validated weekly against brute force)
```
