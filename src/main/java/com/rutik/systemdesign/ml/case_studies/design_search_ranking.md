# Design a Search Ranking System (E-commerce Scale)

## Problem Statement

Design a search ranking system for an e-commerce platform. Given a text query (e.g., "red running shoes size 10"), return the top 20 most relevant products within 100ms P99. The system must balance three objectives: relevance (match user intent), conversion (surface products users actually buy), and business objectives (margin, inventory clearance). The system processes 50K queries per second across 100M users and 10M product listings. Click data is abundant but biased toward top positions; purchase signal is sparse but high-quality.

Constraints:
- 100M users, 10M products, 50K QPS
- 100ms P99 end-to-end latency
- NDCG@10 > 0.55 (offline), CTR improvement > 5% over BM25 baseline (online)
- Support real-time index updates (new products live within 5 minutes)
- Click data: 500M clicks/day; purchase data: 5M purchases/day

---

## Architecture Overview

```
  User Query: "red running shoes size 10"
        │
        v
  ┌─────────────────────────────────────────────────────────────────┐
  │  QUERY UNDERSTANDING (10ms)                                     │
  │                                                                 │
  │  ┌─────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
  │  │ Spell Check │  │ Query Intent │  │ Query Expansion       │  │
  │  │ (symspell)  │  │ Classifier   │  │ (BERT synonym gen)    │  │
  │  │ "sheos"     │  │ navigational │  │ "shoes" → "footwear,  │  │
  │  │ → "shoes"   │  │ transactional│  │ sneakers, trainers"   │  │
  │  │ <1ms        │  │ informational│  │ top-5 synonyms        │  │
  │  └─────────────┘  └──────────────┘  └───────────────────────┘  │
  │                                                                 │
  │  Output: normalized_query, intent, expanded_terms              │
  └────────────────────────────┬────────────────────────────────────┘
                               │
                               v
  ┌─────────────────────────────────────────────────────────────────┐
  │  RETRIEVAL / CANDIDATE GENERATION (25ms)                        │
  │                                                                 │
  │  ┌──────────────────┐    ┌──────────────────────────────────┐   │
  │  │  BM25 (Lexical)  │    │  Dense Retrieval (Semantic)      │   │
  │  │  ElasticSearch   │    │  Two-Tower: query encoder +      │   │
  │  │  top-1000        │    │  item encoder, FAISS IVF index   │   │
  │  │  inverted index  │    │  top-500                         │   │
  │  │  + filters       │    │  (BERT-mini, 66M params)         │   │
  │  └────────┬─────────┘    └──────────────┬───────────────────┘   │
  │           │                             │                        │
  │           └──────────────┬──────────────┘                        │
  │                          v                                       │
  │              RRF Fusion (Reciprocal Rank)                        │
  │              top-500 candidates                                  │
  └────────────────────────────┬────────────────────────────────────┘
                               │
                               v
  ┌─────────────────────────────────────────────────────────────────┐
  │  LEARNING TO RANK (40ms)                                        │
  │                                                                 │
  │  LambdaMART (XGBoost + LambdaRank gradients)                   │
  │  500 features per (query, product) pair:                        │
  │  - Query-product: BM25 score, dense score, exact match          │
  │  - Product: CTR_7d, conversion_rate, avg_rating, review_count   │
  │  - User: user_category_affinity, price_sensitivity              │
  │  - Context: device_type, hour_of_day, session_depth             │
  │  - Cross: price_vs_user_avg, category_match                     │
  │                                                                 │
  │  Output: 20 ranked products                                     │
  └────────────────────────────┬────────────────────────────────────┘
                               │
                               v
  ┌─────────────────────────────────────────────────────────────────┐
  │  BUSINESS RE-RANKING (5ms)                                      │
  │  - Personalization boost (user history affinity)                │
  │  - Inventory rules (out-of-stock demotion)                      │
  │  - Margin rules (promoted products +0.02 score boost)           │
  │  - Brand diversity (max 3 per brand in top-10)                  │
  └────────────────────────────┬────────────────────────────────────┘
                               │
                               v
                     Top-20 products returned

  TRAINING PIPELINE (offline, daily):
  ┌──────────────────────────────────────────────────────────────────┐
  │  Click logs (Kafka) → Spark ETL → Position Bias Correction       │
  │  → LTR training data (query, product, relevance_label)           │
  │  → LambdaMART training → MLflow → A/B deploy                    │
  │                                                                  │
  │  Label construction:                                             │
  │  purchase = 3, add_to_cart = 2, long_click (>30s) = 1, click = 0.5 │
  │  Position bias: multiply by IPW = 1/P(click|position)           │
  └──────────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

**Hybrid retrieval (BM25 + dense)**: BM25 handles exact keyword matching well ("iPhone 14 Pro 256GB") but fails on semantic queries ("phone with good camera under $500"). Dense retrieval handles semantics but misses rare exact terms. RRF fusion exploits both without needing to tune a combination weight.

**LambdaMART for LTR**: LambdaMART (MART = gradient boosted trees with LambdaRank gradients targeting NDCG) is the industry standard for search ranking. It optimizes NDCG directly via approximated gradients, handles mixed feature types and missing values, and is interpretable via feature importance. Comparable to deep learning LTR (DLRM) on tabular features but trains in 2 hours vs 12 hours.

**Position bias debiasing**: Users click on position 1 items 5-10x more than position 5 items regardless of relevance. Training directly on click counts produces a ranker that learns to rank what was already ranked highly. IPW (Inverse Propensity Weighting) corrects this: each click is weighted by 1/P(examined|position). P(examined|position) is estimated from randomization experiments (swap positions for 1% of traffic, observe CTR ratio).

**Dense retrieval model choice**: BERT-mini (66M params, 4 layers) provides 80% of BERT-large quality at 10x inference speed. Query encoder runs on CPU in 8ms; item encoders are precomputed and stored in FAISS. Fine-tuned on in-domain (query, product title) pairs with in-batch negatives.

**Query expansion with caution**: Synonym expansion ("shoes" → "footwear, sneakers") increases recall but risks precision loss (expanding "apple" in a grocery context to "iPhone" is a failure mode). Expansion is applied only to BM25 retrieval, not dense retrieval (dense already captures semantics). Expansion candidates are filtered by co-occurrence in the product catalog.

---

## Implementation

### LambdaMART with XGBoost

```python
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.model_selection import GroupShuffleSplit
from sklearn.preprocessing import StandardScaler


def build_ltr_features(
    df: pd.DataFrame,
) -> pd.DataFrame:
    """
    Build 500 LTR features from (query, product, user, context) tuple.
    df columns: query_id, product_id, user_id,
                bm25_score, dense_score,
                product_ctr_7d, product_conversion_rate, product_avg_rating,
                product_review_count, product_price, product_inventory_count,
                user_avg_order_value, user_category_affinity (dict stored as cols),
                device_type (mobile/desktop), hour_of_day, session_depth
    """
    f = df.copy()

    # Query-product match features
    f["combined_retrieval_score"] = (
        0.6 * f["bm25_score"] + 0.4 * f["dense_score"]
    )
    f["score_product"] = f["bm25_score"] * f["dense_score"]
    f["score_difference"] = f["bm25_score"] - f["dense_score"]

    # Product quality features (log-transform to handle long tails)
    f["log_review_count"] = np.log1p(f["product_review_count"])
    f["log_ctr_7d"] = np.log1p(f["product_ctr_7d"])
    f["inventory_available"] = (f["product_inventory_count"] > 0).astype(int)
    f["low_stock"] = f["product_inventory_count"].between(1, 5).astype(int)

    # User-product cross features (critical for personalization)
    f["price_vs_user_avg"] = f["product_price"] / (f["user_avg_order_value"] + 1.0)
    f["price_below_user_avg"] = (f["product_price"] < f["user_avg_order_value"]).astype(int)

    # Context features
    f["is_mobile"] = (f["device_type"] == "mobile").astype(int)
    f["is_prime_shopping_hour"] = f["hour_of_day"].between(19, 22).astype(int)
    f["deep_session"] = (f["session_depth"] > 5).astype(int)

    # Quality-weighted retrieval
    f["quality_weighted_score"] = (
        f["combined_retrieval_score"] *
        (1 + 0.1 * f["product_avg_rating"]) *
        (1 + 0.05 * np.log1p(f["product_review_count"]))
    )

    feature_cols = [
        "bm25_score", "dense_score", "combined_retrieval_score",
        "score_product", "score_difference", "quality_weighted_score",
        "product_ctr_7d", "log_ctr_7d", "product_conversion_rate",
        "product_avg_rating", "log_review_count",
        "product_price", "inventory_available", "low_stock",
        "price_vs_user_avg", "price_below_user_avg",
        "is_mobile", "is_prime_shopping_hour", "deep_session",
    ]
    return f[feature_cols]


def train_lambdamart(
    df: pd.DataFrame,
    label_col: str = "relevance_label",
    group_col: str = "query_id",
) -> xgb.XGBRanker:
    """Train LambdaMART ranker optimizing NDCG@10."""
    X = build_ltr_features(df)
    y = df[label_col].values.astype(np.float32)

    # Group by query: each query is one "group" for pairwise ranking
    group_sizes = df.groupby(group_col).size().values

    splitter = GroupShuffleSplit(n_splits=1, test_size=0.1, random_state=42)
    train_idx, val_idx = next(splitter.split(X, y, groups=df[group_col]))

    ranker = xgb.XGBRanker(
        objective="rank:ndcg",
        ndcg_exp_gain=True,
        lambdarank_pair_method="topk",  # focus gradients on top-K positions
        lambdarank_num_pair_per_sample=8,
        eval_metric=["ndcg@5", "ndcg@10"],
        n_estimators=1000,
        max_depth=8,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.7,
        min_child_weight=10,
        reg_alpha=0.1,
        reg_lambda=1.0,
        random_state=42,
        n_jobs=-1,
        tree_method="hist",
    )

    ranker.fit(
        X.iloc[train_idx], y[train_idx],
        group=group_sizes[:len(set(df.iloc[train_idx][group_col]))],
        eval_set=[(X.iloc[val_idx], y[val_idx])],
        eval_group=[group_sizes[len(set(df.iloc[train_idx][group_col])):]],
        verbose=100,
    )
    return ranker
```

### Reciprocal Rank Fusion (Hybrid Retrieval)

```python
from collections import defaultdict


def reciprocal_rank_fusion(
    ranked_lists: list[list[str]],
    k: int = 60,  # standard RRF constant, dampens effect of outlier ranks
    top_n: int = 500,
) -> list[tuple[str, float]]:
    """
    Combine multiple ranked lists via RRF.
    RRF score = sum(1 / (k + rank_i)) for each list i.

    ranked_lists: each element is a list of product_ids in rank order
    Returns: top_n (product_id, score) sorted descending
    """
    scores: dict[str, float] = defaultdict(float)
    for ranked_list in ranked_lists:
        for rank, product_id in enumerate(ranked_list, start=1):
            scores[product_id] += 1.0 / (k + rank)

    sorted_items = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return sorted_items[:top_n]


def hybrid_retrieve(
    query: str,
    bm25_results: list[str],    # product_ids from ElasticSearch, ordered
    dense_results: list[str],   # product_ids from FAISS, ordered
    top_k: int = 500,
) -> list[str]:
    """Fuse BM25 and dense retrieval results."""
    fused = reciprocal_rank_fusion([bm25_results, dense_results], top_n=top_k)
    return [product_id for product_id, _ in fused]
```

### Position Bias Debiasing with IPW

```python
import numpy as np
import pandas as pd
from scipy.optimize import minimize


def estimate_examination_probability(
    clicks_df: pd.DataFrame,
    method: str = "regression_em",
) -> np.ndarray:
    """
    Estimate P(examined | position) from click logs.

    clicks_df: columns [position, clicked, query_id, product_id, relevance_label]
    method: 'regression_em' uses EM algorithm assuming click = examine * relevance
            'swap_experiment' requires dedicated randomization experiment data

    Returns: array of shape (max_position,) with examination probabilities
    """
    if method == "regression_em":
        # EM algorithm: click = Bernoulli(theta_q_d * gamma_k)
        # theta_q_d = relevance of document d for query q
        # gamma_k = P(examined | position k)
        # Iteratively update theta and gamma until convergence
        max_pos = int(clicks_df["position"].max()) + 1
        gamma = np.ones(max_pos)  # examination probabilities, init uniform
        theta = np.full(len(clicks_df), 0.5)  # relevance estimates

        for iteration in range(20):  # typically converges in 10-15 iterations
            # E-step: update theta given gamma
            for idx, row in clicks_df.iterrows():
                pos = int(row["position"])
                clicked = int(row["clicked"])
                if clicked:
                    theta[idx] = 1.0
                else:
                    # P(theta=1 | not clicked) = theta * (1 - gamma_k) / (1 - theta * gamma_k)
                    old_theta = theta[idx]
                    old_gamma = gamma[pos]
                    theta[idx] = old_theta * (1 - old_gamma) / max(
                        1 - old_theta * old_gamma, 1e-9
                    )

            # M-step: update gamma given theta
            for k in range(max_pos):
                pos_mask = clicks_df["position"] == k
                if pos_mask.sum() == 0:
                    continue
                pos_theta = theta[pos_mask]
                pos_clicked = clicks_df.loc[pos_mask, "clicked"].values
                # gamma_k = sum(clicks) / sum(theta)
                gamma[k] = pos_clicked.sum() / max(pos_theta.sum(), 1e-9)
                gamma[k] = np.clip(gamma[k], 0.01, 1.0)

        return gamma

    raise ValueError(f"Unknown method: {method}")


def apply_ipw_weights(
    df: pd.DataFrame,
    examination_probs: np.ndarray,
    min_weight: float = 0.1,
    max_weight: float = 10.0,
) -> pd.Series:
    """
    Compute IPW sample weights for training data debiasing.
    Weight = 1 / P(examined | position).
    """
    positions = df["position"].clip(0, len(examination_probs) - 1).astype(int)
    weights = 1.0 / examination_probs[positions].clip(min=0.01)
    return pd.Series(weights.clip(min_weight, max_weight), index=df.index)


def build_debiased_training_data(
    clicks_df: pd.DataFrame,
) -> pd.DataFrame:
    """Full pipeline: estimate bias, compute weights, label construction."""
    exam_probs = estimate_examination_probability(clicks_df)
    ipw_weights = apply_ipw_weights(clicks_df, exam_probs)

    # Multi-level relevance labels from user behavior
    clicks_df = clicks_df.copy()
    clicks_df["relevance_label"] = 0.0
    clicks_df.loc[clicks_df["clicked"] == 1, "relevance_label"] = 0.5
    clicks_df.loc[clicks_df["dwell_time_sec"] > 30, "relevance_label"] = 1.0
    clicks_df.loc[clicks_df["add_to_cart"] == 1, "relevance_label"] = 2.0
    clicks_df.loc[clicks_df["purchased"] == 1, "relevance_label"] = 3.0

    clicks_df["sample_weight"] = ipw_weights
    return clicks_df
```

---

## ML Components Used

| Component | Technology | Role |
|-----------|-----------|------|
| Spell Correction | symspell (Python) | Query normalization, <1ms |
| Query Intent | Fine-tuned BERT classifier | navigational / transactional / informational |
| Query Expansion | BERT masked LM + catalog co-occurrence | Synonym generation for BM25 |
| Lexical Retrieval | ElasticSearch BM25 | Exact term matching, inverted index |
| Dense Retrieval | Two-Tower (BERT-mini) + FAISS | Semantic matching |
| Result Fusion | Reciprocal Rank Fusion (RRF) | Hybrid BM25 + dense combination |
| Learning to Rank | XGBoost LambdaMART (rank:ndcg) | NDCG@10 optimization over 500 features |
| Position Bias | IPW with EM estimation | Click data debiasing for training labels |
| Experiment Platform | A/B test framework + interleaving | Online evaluation (CTR, conversion) |

---

## Tradeoffs and Alternatives

| Decision | Chosen | Alternative | Reasoning |
|----------|--------|-------------|-----------|
| Retrieval fusion | RRF | Linear score combination | RRF requires no weight tuning; robust across query types |
| LTR model | LambdaMART (XGBoost) | Neural LTR (DNN, DLRM) | LambdaMART: 2h training, interpretable, same NDCG as DNN on tabular features |
| Bias correction | IPW / regression EM | Randomization experiments | EM requires no traffic sacrifice; swap experiments give cleaner estimates but costly |
| Query expansion | Catalog-filtered BERT synonyms | WordNet / thesaurus | Catalog-filtered prevents off-domain expansions |
| Dense retrieval encoder | BERT-mini (66M) | BERT-base (110M) | BERT-mini: 10x faster, 80% quality; acceptable for real-time retrieval |
| Label source | Click + purchase + dwell time | Purchase only | Purchase signal is too sparse; blending improves coverage at cost of noise |

---

## Interview Discussion Points

**How do you deal with position bias when training on click data?**
Click data is the most abundant training signal but heavily biased: position 1 receives 5-10x more clicks than position 5 regardless of product quality. Training a ranker directly on click counts teaches it to replicate the existing ranking, not improve it. IPW correction weights each click by 1/P(examined|position), estimated via the EM algorithm on historical click-through rates. An alternative is interleaving experiments where the candidate model and production model are interleaved in results, then observed — this is less biased than A/B tests for ranking evaluation.

**How do you balance relevance, conversion, and business objectives in ranking?**
Three-layer approach: (1) LambdaMART optimizes a combined relevance label (click=0.5, long dwell=1, cart=2, purchase=3) capturing both relevance and conversion signals in one model. (2) Post-ranking business rules apply constraints: out-of-stock items are demoted, promoted listings receive a bounded boost (cap at +3 positions to avoid quality degradation), brand diversity enforced via cap-3-per-brand. (3) Separate margin-weighted ranking model for "sponsored" slots. This separation keeps the organic ranker clean while allowing business control.

**How do you evaluate the ranking system? When do you trust offline metrics vs online experiments?**
Offline NDCG@10 on a held-out click dataset measures ranking quality but is biased by the same position bias present in training data. Use a randomization experiment (5% of traffic with fully randomized results) to collect an unbiased offline evaluation set. Online A/B tests measure CTR, conversion rate, and revenue per search. The correlation between offline NDCG and online CTR is typically 0.7-0.8 — sufficient to use offline metrics for fast iteration and A/B tests for final validation before full rollout. Never skip A/B testing: a model with +2% offline NDCG has failed to improve online CTR in practice.
