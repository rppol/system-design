# Recommender Systems

## Deep Dive Files

| Sub-File | Topic | Q&As |
|----------|-------|------|
| [collaborative_filtering.md](collaborative_filtering.md) | Memory-based CF, Matrix Factorization, ALS, BPR | 15+ |
| [deep_learning_recommenders.md](deep_learning_recommenders.md) | Two-tower, NCF, Wide & Deep, DeepFM, SASRec | 15+ |
| [retrieval_and_ranking.md](retrieval_and_ranking.md) | Candidate generation, LTR, position bias, re-ranking | 15+ |
| [content_and_hybrid.md](content_and_hybrid.md) | Content-based, hybrid, session-based, graph-based | 15+ |
| [online_learning_and_bandits.md](online_learning_and_bandits.md) | Multi-armed bandits, UCB, Thompson Sampling, contextual bandits | 15+ |

---

## 1. Concept Overview

A recommender system predicts the utility of items for a given user and surfaces the most relevant subset from a corpus that is orders of magnitude larger than what any person could browse manually. The canonical formulation: given a set of users U, a set of items I, and a partially observed interaction matrix R (|U| x |I|), predict R[u, i] for all unobserved pairs and return the top-K items per user.

Modern industrial systems decompose this into a multi-stage pipeline:

```
All Items (10M+)
     |
     v
[Retrieval / Candidate Generation]  <-- recall-focused, ~100-1000 candidates
     |
     v
[Ranking / Scoring]                 <-- precision-focused, expensive model
     |
     v
[Re-ranking]                        <-- diversity, freshness, business rules
     |
     v
[Presentation Layer]                <-- layout, A/B test, UI constraints
     |
     v
Final Recommendations (10-50 items)
```

---

## 2. Intuition

One-line analogy: A recommender is a librarian who has watched every patron's reading history and can hand you the three books you did not know you wanted — before you even ask.

Mental model: The system solves two sub-problems simultaneously. First, it must find the needle (relevant items) in a haystack of 10 million possibilities quickly — this is retrieval, optimized for recall. Second, it must rank the 500 needles it found by how much you personally will enjoy each one — this is ranking, optimized for precision.

Why it matters: Netflix attributes more than 80% of watched content to recommendations. Amazon reports 35% of revenue driven by its recommendation engine. Spotify's Discover Weekly generates ~3 billion streams per month.

Key insight: Collaborative filtering exploits the wisdom of crowds — users who agreed in the past will agree in the future — without needing to understand item content at all. The surprising implication: a recommender can surface a documentary you will love even though you have never expressed interest in documentaries, because 50 users with identical viewing history loved it.

---

## 3. Core Principles

**Collaborative Filtering (CF)**: Leverage the collective behavior of all users. Two users who rated the same items similarly will rate future items similarly. No item content features required.

**Content-Based Filtering (CBF)**: Build an explicit item profile (genre, keywords, director) and match to user profile built from liked items. Can recommend new items; cannot leverage cross-user signals.

**Implicit vs Explicit Feedback**: Explicit = star ratings (rare, biased toward extreme opinions). Implicit = clicks, purchases, dwell time (abundant, noisy, no true negatives — absence of click does not mean dislike).

**The Sparsity Problem**: A user-item matrix for Netflix (200M users x 6000 movies) has 1.2 trillion cells; fewer than 0.01% are observed. Algorithms must generalize from this sparse signal.

**The Cold Start Problem**: New users have no history (user cold start). New items have no interactions (item cold start). Content-based and popularity fallbacks are standard mitigations.

**Feedback Loops**: Recommending item A causes more clicks on A, which causes the model to recommend A even more. Left unchecked, this creates filter bubbles and popularity bias. Exploration (bandits, random injection) counteracts this.

**Evaluation Offline vs Online**: Offline metrics (NDCG, Recall@K) measure held-out interaction prediction. Online metrics (CTR, session length, revenue) measure real user behavior. They frequently disagree — a model with better NDCG can have lower real-world CTR.

---

## 4. Types / Architectures / Strategies

### 4.1 Collaborative Filtering

**Memory-based CF**: Compute user-user or item-item similarity directly from the interaction matrix. Simple but does not scale beyond ~1M items.

**Model-based CF — Matrix Factorization**: Decompose R into U (|users| x k) and V (|items| x k) such that R ≈ U @ V.T. SVD for explicit; ALS / SGD for implicit. k = 50–200 typical.

**BPR (Bayesian Personalized Ranking)**: Pairwise loss — for each user, rank observed item above unobserved item. Optimized for ranking rather than rating prediction.

### 4.2 Content-Based Filtering

TF-IDF or sentence embeddings for text features. Cosine similarity between user profile vector (mean of liked item vectors) and candidate items.

### 4.3 Deep Learning Recommenders

**Two-tower**: Separate neural networks for user and item; dot product at retrieval time. Serves as the modern backbone of YouTube, Pinterest, Twitter.

**Wide & Deep**: Wide linear model (memorization of rare feature combinations) + Deep MLP (generalization). Google Play serves 10B+ recommendations/day.

**DeepFM**: Factorization Machines + deep MLP, automatically models pairwise feature interactions without manual feature engineering.

**Sequential Models**: SASRec, BERT4Rec — self-attention over the user's recent interaction sequence.

### 4.4 Hybrid Approaches

Weighted hybrid, switching hybrid, cascade hybrid, feature augmentation hybrid. Most production systems combine multiple signals.

### 4.5 Bandit-Based Online Learning

Epsilon-greedy, UCB, Thompson Sampling for explore-exploit in real-time recommendation streams. Contextual bandits (LinUCB) condition on user/item features.

---

## 5. Architecture Diagrams

### 5.1 Two-Stage Industrial RecSys Pipeline

```
                          USER REQUEST
                               |
                    +----------v----------+
                    |   Feature Store     |  <-- user features, context
                    +----------+----------+
                               |
            +------------------v------------------+
            |         RETRIEVAL LAYER             |
            |                                     |
            |  User Tower          Item Tower     |
            |  [user_id, age,  ]   [item_id, cat] |
            |  [history, ctx   ]   [price, text  ]|
            |       |                    |        |
            |   [Embed 256]         [Embed 256]   |
            |       |                    |        |
            |   user_vec            item_vecs     |
            |       |_____ dot prod _____|        |
            |                                     |
            |   ANN Search (FAISS IVF)            |
            |   returns top-500 candidates        |
            |   recall@100 ~ 95%                  |
            +------------------+------------------+
                               |
            +------------------v------------------+
            |          RANKING LAYER              |
            |                                     |
            |  Input: user x candidate features   |
            |  Model: GBDT or DNN                 |
            |  Features: cross, historical, ctx   |
            |  Output: score per candidate        |
            |  Latency budget: <20ms              |
            |  Returns top-50                     |
            +------------------+------------------+
                               |
            +------------------v------------------+
            |         RE-RANKING LAYER            |
            |                                     |
            |  Diversity (MMR)                    |
            |  Freshness boost                    |
            |  Business rules (sponsored items)   |
            |  Deduplication                      |
            |  Returns top-10 to present          |
            +------------------+------------------+
                               |
                    +----------v----------+
                    |  PRESENTATION LAYER |
                    |  Layout, A/B tests  |
                    +---------------------+
```

### 5.2 User-Item Interaction Matrix

```
         Item1  Item2  Item3  Item4  Item5
User1  [  5      ?      3      ?      1  ]
User2  [  ?      4      ?      2      ?  ]
User3  [  1      ?      ?      5      3  ]
User4  [  ?      2      4      ?      ?  ]

? = unobserved (99%+ of cells in real systems)
Goal: predict all ? and return top-K per user
```

---

## 6. How It Works — Detailed Mechanics

```python
from __future__ import annotations

import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class InteractionMatrix:
    """Sparse user-item interaction matrix with basic CF utilities."""

    n_users: int
    n_items: int
    # {user_id: {item_id: rating_or_count}}
    data: dict[int, dict[int, float]] = field(default_factory=dict)

    def add_interaction(self, user_id: int, item_id: int, value: float = 1.0) -> None:
        self.data.setdefault(user_id, {})[item_id] = value

    def to_dense(self) -> np.ndarray:
        matrix = np.zeros((self.n_users, self.n_items), dtype=np.float32)
        for u, items in self.data.items():
            for i, v in items.items():
                matrix[u, i] = v
        return matrix

    def sparsity(self) -> float:
        observed = sum(len(v) for v in self.data.values())
        total = self.n_users * self.n_items
        return 1.0 - observed / total


def item_item_cf(
    matrix: np.ndarray,
    user_id: int,
    top_k: int = 10,
    n_similar: int = 20,
) -> list[tuple[int, float]]:
    """Item-item collaborative filtering recommendation.

    For each item the user has NOT interacted with, compute its similarity to
    items the user HAS interacted with and return weighted average score.
    """
    user_row = matrix[user_id]          # shape: (n_items,)
    interacted = np.where(user_row > 0)[0]
    not_interacted = np.where(user_row == 0)[0]

    if len(interacted) == 0:
        return []  # cold start: no history

    # Compute item-item cosine similarity matrix (items x items)
    # In practice pre-computed and cached offline
    item_sim = cosine_similarity(matrix.T)  # (n_items, n_items)

    scores: list[tuple[int, float]] = []
    for candidate in not_interacted:
        # Similarity between candidate and each interacted item
        sims = item_sim[candidate, interacted]
        ratings = user_row[interacted]
        # Weighted average: sum(sim * rating) / sum(|sim|)
        denom = np.abs(sims).sum()
        if denom == 0:
            continue
        score = float(np.dot(sims, ratings) / denom)
        scores.append((int(candidate), score))

    scores.sort(key=lambda x: x[1], reverse=True)
    return scores[:top_k]


def ndcg_at_k(
    recommended: list[int],
    relevant: set[int],
    k: int = 10,
) -> float:
    """Normalized Discounted Cumulative Gain at K."""
    dcg = 0.0
    for rank, item_id in enumerate(recommended[:k], start=1):
        if item_id in relevant:
            dcg += 1.0 / np.log2(rank + 1)

    # Ideal DCG: all relevant items at top positions
    ideal_hits = min(len(relevant), k)
    idcg = sum(1.0 / np.log2(rank + 1) for rank in range(1, ideal_hits + 1))

    return dcg / idcg if idcg > 0 else 0.0


def recall_at_k(
    recommended: list[int],
    relevant: set[int],
    k: int = 100,
) -> float:
    """Recall@K: fraction of relevant items captured in top-K recommendations."""
    hits = len(set(recommended[:k]) & relevant)
    return hits / len(relevant) if relevant else 0.0


# Example usage
if __name__ == "__main__":
    np.random.seed(42)
    n_users, n_items = 1000, 5000
    matrix_dense = np.random.choice(
        [0, 0, 0, 0, 1, 2, 3, 4, 5],
        size=(n_users, n_items),
        p=[0.97, 0.0, 0.0, 0.0, 0.01, 0.005, 0.005, 0.005, 0.005],
    ).astype(np.float32)

    recs = item_item_cf(matrix_dense, user_id=0, top_k=10)
    print("Top-10 item-item CF recommendations:", [item for item, _ in recs])

    relevant_items = {recs[i][0] for i in range(3)}
    print(f"NDCG@10: {ndcg_at_k([r[0] for r in recs], relevant_items):.4f}")
    print(f"Recall@100: N/A for 10-item demo")
```

---

## 7. Real-World Examples

**Netflix Prize (2009)**: A $1M competition to improve Netflix's Cinematch algorithm by 10% RMSE. The winning solution (BellKor's Pragmatic Chaos) combined 107 models including matrix factorization, RBMs, and neighborhood methods. Matrix factorization alone achieved ~8.5% improvement. Key insight: SVD++ (incorporating implicit feedback) outperformed plain SVD. Netflix never deployed the winning ensemble — latency was impractical.

**YouTube Two-Tower (2016)**: Google published the landmark paper on using deep neural networks for YouTube recommendation. Candidate generation network: user watch history embedded via average pooling of video embeddings, then a DNN producing a user vector. Retrieval via nearest neighbor search over all video vectors. Ranking network: a separate deeper DNN with hundreds of features. This architecture became the blueprint for modern RecSys.

**TikTok For You Page**: A reinforcement learning system that treats each user session as an episodic interaction. The model updates continuously from watch-time signals (not just binary clicks). Short videos allow extremely high-frequency feedback loops. Cold start handled by showing viral/trending content to new users.

**Spotify Discover Weekly**: Combines collaborative filtering (users with similar listening history) with NLP on playlist titles and track metadata. 30-song playlist generated every Monday. Launched 2015, within weeks generated 1.7 billion streams. The key innovation: treating playlists as "documents" and songs as "words" — word2vec on playlists gives song embeddings that capture musical relationships.

**Amazon "Customers Also Bought"**: Item-item CF computed offline. Stable and fast because item similarities change slowly compared to user preferences. Scales to Amazon's full catalog because each item only needs its K most similar neighbors stored.

---

## 8. Tradeoffs

| Approach | Recall | Precision | Cold Start | Scalability | Interpretability |
|----------|--------|-----------|------------|-------------|-----------------|
| User-User CF | Medium | Medium | Poor | Poor (O(U^2)) | High |
| Item-Item CF | Medium | Medium | Item: Poor; User: OK | OK (offline) | High |
| Matrix Factorization | Good | Good | Poor | Good | Low |
| Two-Tower Neural | Excellent | Good | OK (with features) | Excellent | Very Low |
| Content-Based | Medium | Medium | Excellent | Good | High |
| Hybrid | Excellent | Excellent | Good | Depends | Medium |

| Feedback Type | Pros | Cons |
|--------------|------|------|
| Explicit (ratings) | Clean signal, true preference | Rare, selection bias, effort required |
| Implicit (clicks) | Abundant, natural behavior | Noisy, no true negatives, position bias |

| Retrieval Method | Throughput | Recall@100 | Setup Complexity |
|-----------------|------------|------------|-----------------|
| Brute-force dot product | Low (O(N)) | 100% | Trivial |
| FAISS IVF | Very High | ~95% | Medium |
| FAISS HNSW | High | ~98% | Medium |
| ScaNN | Highest | ~95-97% | High |

---

## 9. When to Use / When NOT to Use

**Use collaborative filtering when:**
- You have a substantial interaction history (at least thousands of user-item pairs)
- Items are hard to describe with features (movies, music)
- Cross-user discovery is valuable (serendipity)
- You can tolerate poor cold-start performance

**Use content-based filtering when:**
- Items have rich metadata (news articles, job listings, products)
- Cold start is frequent (news: new articles every hour)
- User privacy constraints prevent sharing cross-user data
- Transparency/explainability is required

**Use two-tower neural retrieval when:**
- Catalog size > 1M items (brute-force is too slow)
- You have diverse user and item features beyond IDs
- Training data exceeds ~10M interactions
- Infrastructure supports FAISS/ScaNN deployment

**Do NOT use a single-stage ranking model when:**
- Catalog size > 100K items (too slow to score all items)
- Latency budget is under 100ms end-to-end

**Do NOT use pure collaborative filtering when:**
- Cold start rate is high (>20% of traffic is new users)
- Regulatory requirements mandate feature-based explanations

---

## 10. Common Pitfalls

**Pitfall 1 — Popularity bias loop**: A team deployed ALS recommendations and noticed after two weeks that the top-100 recommended items were identical for 60% of users. Root cause: popular items had more interactions, received higher latent factor magnitudes, dominated dot product scores. Fix: apply popularity debiasing (divide item score by log(1 + interaction_count)), inject random exploration, measure diversity (ILD) in monitoring.

**Pitfall 2 — Data leakage in offline evaluation**: Engineers used random train/test split on the interaction log. The model appeared to achieve NDCG@10 of 0.45. In online A/B test, CTR improvement was zero. Root cause: random split lets the model see "future" interactions during training (a user's Tuesday click used to predict their Monday behavior). Fix: always use temporal split — train on interactions before date T, evaluate on interactions after T.

**Pitfall 3 — Serving stale embeddings**: Item embeddings computed in a nightly batch job. New items added during the day had no embeddings. Users saw a "No recommendations available" fallback for 30% of catalog. Fix: implement an online embedding service that computes embeddings for new items using content features (a content-based fallback tower) until collaborative signal accumulates.

**Pitfall 4 — Position bias inflating CTR for top slots**: A model trained on raw click data learned to recommend items that had historically appeared in position 1, not items that were genuinely good. Diagnostic: items in position 1 had 10x CTR of items in position 10 with identical intrinsic quality. Fix: inverse propensity weighting (IPW) during training, or randomize position for a small fraction of traffic and train on that unbiased sample.

**Pitfall 5 — Ignoring session context**: Recommending winter coats to a user who just purchased a winter coat. The model saw "user likes winter coats" and kept recommending more. Fix: add a recency decay or a "diversity from purchased items" constraint in re-ranking.

---

## 11. Technologies & Tools

| Category | Tools |
|----------|-------|
| Matrix Factorization | implicit (Python ALS), Surprise, LightFM |
| Deep Learning RecSys | PyTorch, TensorFlow Recommenders (TFRS), RecBole |
| ANN / Vector Search | FAISS, ScaNN (Google), Annoy, Hnswlib, Weaviate, Pinecone |
| Feature Stores | Feast, Tecton, Hopsworks |
| Serving / Inference | TorchServe, TF Serving, Triton Inference Server |
| Data Pipeline | Apache Spark, Apache Flink, Kafka |
| Experiment Tracking | MLflow, Weights & Biases |
| A/B Testing | Optimizely, statsig, custom split infrastructure |
| Monitoring | Grafana, Prometheus, custom drift detectors |

---

## 12. Interview Questions with Answers

**Q: What is the difference between the retrieval and ranking stages in a recommendation pipeline?**
Retrieval (candidate generation) selects ~100-1000 items from a corpus of millions, optimizing for recall (recall@100 > 95% is the target). Ranking then scores those candidates with a heavier model, optimizing for precision (NDCG@10). The two-stage design exists because scoring all 10M items with a deep model per request would take seconds; a simple dot product ANN search takes milliseconds. Retrieval sacrifices a little recall to enable feasible ranking.

**Q: How does matrix factorization work for collaborative filtering?**
Matrix factorization decomposes the sparse user-item interaction matrix R (n_users x n_items) into two low-rank matrices: U (n_users x k) and V (n_items x k), such that R ≈ U @ V.T. Each user and item gets a k-dimensional latent vector (k = 50–200 typically). For explicit feedback, the loss is MSE on observed ratings; for implicit feedback, ALS with confidence weighting is preferred. The Netflix Prize showed SVD-based MF achieving ~8.5% RMSE improvement over the baseline.

**Q: What is implicit feedback and how does it differ from explicit feedback?**
Explicit feedback is a direct user rating (1–5 stars). Implicit feedback is inferred from behavior: clicks, purchases, dwell time, skips. Implicit is far more abundant but carries no true negatives — if a user did not click an item, they may not have seen it, or it may not have been shown. Algorithms like ALS (with confidence weighting) and BPR (pairwise ranking) are specifically designed for implicit feedback, treating unobserved interactions as weakly negative rather than missing.

**Q: Explain the cold start problem and how you would mitigate it.**
Cold start occurs when a new user has no interaction history (user cold start) or a new item has no interactions (item cold start). Mitigations: for new users, show popularity-based or editorially curated content, then use onboarding questions to build an initial profile; for new items, use content-based features (text embeddings, category, price) to generate an item vector that can be placed into the existing ANN index without needing interactions. A hybrid two-tower model handles this naturally — content features allow scoring even without historical signals.

**Q: What is position bias in recommender systems and how do you correct for it?**
Position bias is the tendency for users to click items shown at higher positions regardless of intrinsic quality. Items at position 1 can receive 10x the CTR of items at position 10 with identical quality. If you train on raw clicks, the model learns position as a proxy for quality. Correction methods: inverse propensity weighting (IPW) — upweight clicks at lower positions by 1/P(position), where P is estimated from randomization experiments; or use a separate position bias model trained on randomized traffic to debias labels.

**Q: How does the Wide & Deep model work?**
Wide & Deep (Google, 2016) combines a wide linear model with a deep neural network, trained jointly. The wide part memorizes specific feature combinations (e.g., "user installed app X and searched for Y" → install Z) using manually crafted cross features. The deep part generalizes to unseen feature combinations via dense embeddings through multiple MLP layers. The intuition: memorization handles rare but reliable patterns; generalization handles novel combinations. Google Play uses this to serve over 10 billion recommendations per day.

**Q: What metrics would you use to evaluate a recommender system offline?**
NDCG@K (Normalized Discounted Cumulative Gain) — measures ranking quality, giving higher weight to relevant items at top positions. Recall@K — fraction of relevant items captured in top-K, important for the retrieval stage. MRR (Mean Reciprocal Rank) — 1/rank of first relevant item, important when finding any relevant item quickly matters. For diversity: ILD (Intra-List Diversity) — average pairwise distance among recommended items. For novelty: mean inverse log popularity of recommended items. Critical caveat: always use temporal train/test split, never random split.

**Q: What is the explore-exploit tradeoff in recommendation?**
Exploitation means recommending items the model is confident the user will like (high expected reward). Exploration means recommending items the model is uncertain about (potentially discovering better options). Pure exploitation leads to feedback loops and filter bubbles. Pure exploration leads to poor user experience. Bandit algorithms (epsilon-greedy, UCB, Thompson Sampling) balance this explicitly. In practice, most systems inject a small exploration fraction (5–10% of impressions) using bandit or random strategies, while exploiting for the rest.

**Q: How would you scale a recommendation system to 1 billion users and 10 million items?**
First, pre-compute item embeddings offline (batch job, runs nightly or streaming). Store item vectors in a distributed ANN index (FAISS, ScaNN) sharded across machines — each machine holds a subset of items, results merged. User embeddings can be computed at request time from a user tower model (fast inference, ~5ms). Feature stores (Redis for online features, Spark for batch) provide fresh user features. Ranking: a lighter model (GBDT or small DNN) scores 500 candidates in <10ms. Horizontal scaling of serving infrastructure handles throughput. Caching of user embeddings (TTL ~1 hour) reduces recomputation.

**Q: Describe the SASRec model for sequential recommendation.**
SASRec (Self-Attentive Sequential Recommendation, 2018) applies the Transformer self-attention mechanism to a user's sequence of recently interacted items. The input is the last N items (N = 50 typical), each represented by a learned embedding. Positional encodings capture order. Multiple self-attention blocks let the model learn which past interactions are most predictive of the next action. Causal masking ensures position i can only attend to positions 1..i. The output at the last position predicts the next item. SASRec outperforms GRU4Rec and vanilla MF on most sequential recommendation benchmarks.

---

## 13. Best Practices

1. Always use temporal train/test split — random split causes severe data leakage in sequential user behavior.
2. Monitor diversity (ILD) and novelty alongside accuracy metrics — a highly accurate model can create filter bubbles.
3. Implement position bias correction (IPW) from day one — retrospective correction is hard.
4. Separate retrieval and ranking models — do not try to rank all items with a heavy model; brute-force does not scale past ~100K items.
5. Use content features as a cold-start fallback in both retrieval and ranking towers.
6. Log all impressions, not just clicks — you need the denominator for unbiased evaluation.
7. Run offline and online evaluations — NDCG improvements do not always translate to CTR improvements.
8. Decouple model training frequency from serving — models can be trained nightly and served in real-time.
9. Set a minimum interaction threshold before personalizing — below 5 interactions, popularity-based fallback outperforms personalization.
10. Implement a feedback loop monitor — if the recommended set entropy decreases week-over-week, exploration is insufficient.

---

## 14. Case Study

**Problem**: A mid-size e-commerce platform (5M users, 500K products, 50M clicks/month) wants to add personalized product recommendations to the homepage and product detail pages.

**Constraints**: <100ms end-to-end latency; 40% of users are new (cold start); team of 3 ML engineers.

**Solution Architecture**:

```
OFFLINE (nightly batch)
  Spark job reads 90-day click/purchase log
  Trains ALS model (implicit feedback, k=128, alpha=40, 15 iterations)
  Exports item embeddings (500K x 128) to FAISS IVF index
  Exports user embeddings (5M x 128) to Redis (TTL 24h)

ONLINE (request time)
  1. Fetch user embedding from Redis (<1ms)
     If missing (new user): use content-based embedding from product category average
  2. ANN search in FAISS: top-200 candidates (recall@100 ~95%)  (~5ms)
  3. XGBoost ranker: score 200 candidates using user x item features (~10ms)
  4. Re-rank: apply diversity constraint (max 3 items per category) + business rules
  5. Return top-20 recommendations
```

**Results after 3 months**: Homepage CTR +18%, add-to-cart rate +12%, revenue per session +9%. Cold start users (fallback to content-based) showed +6% CTR vs previous popularity baseline. Position bias correction via IPW reduced ranking model's tendency to favor historically top-placed items, improving bottom-of-page CTR by 31%.
