# Collaborative Filtering

## 1. Concept Overview

Collaborative filtering (CF) is a recommendation paradigm that predicts a user's preferences based solely on the collective behavior of all users, without requiring any knowledge of item content. The central hypothesis is: users who have interacted similarly in the past will interact similarly in the future. CF is the backbone of most large-scale commercial recommendation systems and was the dominant technique validated by the Netflix Prize (2009).

Two major families exist: memory-based CF (similarity computed directly from the interaction matrix) and model-based CF (a compact model is learned from the matrix, most importantly matrix factorization). Modern systems use implicit feedback (clicks, purchases, dwell time) rather than explicit ratings.

---

## 2. Intuition

One-line analogy: If you and a stranger both loved the same 50 obscure films, you will probably love the 51st film they loved — without either of you reading a plot summary.

Mental model: Imagine a giant spreadsheet where rows are users and columns are items. Most cells are blank. CF finds users (or items) with similar fill patterns and uses their data to fill in your blanks. The insight is that structure in this sparse matrix — clusters of users with similar taste — carries more predictive signal than any individual item's metadata.

Why matrix factorization outperforms neighborhood methods: instead of storing all pairwise similarities, MF compresses the entire matrix into two low-rank matrices capturing the underlying latent structure (genres, moods, price sensitivity) that drives preferences.

Key insight: ALS (Alternating Least Squares) for implicit feedback solves the problem that clicks have no explicit negatives — the confidence weighting c_ui = 1 + alpha * r_ui (alpha = 40 typical) treats high-interaction items with high confidence and treats all unobserved items as weakly negative.

---

## 3. Core Principles

**User-User CF**: Identify the K most similar users to the target user (cosine similarity or Pearson correlation on interaction vectors). Aggregate their ratings for candidate items, weighted by similarity.

**Item-Item CF**: Identify the K most similar items to each item the target user has liked. Aggregate similarity-weighted scores for candidates. More stable than user-user CF because item similarities change slowly; Amazon patented and deployed this at scale in 2003.

**Matrix Factorization**: Factorize R (n_users x n_items) into U (n_users x k) and V (n_items x k). Prediction: R_hat[u, i] = U[u] · V[i]. Latent dimension k = 50–200 typical. Regularization prevents overfitting on sparse data.

**Implicit vs Explicit Feedback**: Explicit ratings (1–5 stars) are sparse and biased. Implicit signals (clicks, purchases, play counts) are abundant. ALS for implicit treats all unobserved interactions as weakly negative rather than missing, which is the correct Bayesian treatment.

**Confidence Weighting**: c_ui = 1 + alpha * r_ui. A single click (r_ui = 1) gives confidence 41 (with alpha=40). Zero interactions gives confidence 1. High-confidence items dominate the ALS update.

**BPR (Bayesian Personalized Ranking)**: Pairwise loss — for each user u, observed item i, and unobserved item j: maximize P(u prefers i over j). Directly optimizes ranking rather than rating prediction. Better than MSE for implicit feedback evaluation by ranking metrics.

---

## 4. Types / Architectures / Strategies

### 4.1 Memory-Based: User-User CF

```
For target user u:
1. Compute cosine_similarity(u, all_other_users) on interaction vectors
2. Select top-K similar users (K = 20-100)
3. For each item i not seen by u:
   score(u, i) = sum over k-neighbors: sim(u, k) * rating(k, i) / sum(|sim(u, k)|)
4. Recommend top-N items by score
```

Weakness: O(U * I) computation per request; does not scale past ~1M users without pre-computation.

### 4.2 Memory-Based: Item-Item CF

```
Pre-compute offline:
  item_sim[i, j] = cosine_similarity(R[:, i], R[:, j])
  Store top-K similar items per item

At request time for user u:
  For each item i not interacted by u:
    score(u, i) = sum over items j in history(u): item_sim[i, j] * rating(u, j)
```

Advantage: similarities pre-computed offline; request-time lookup is O(|history| * K). Used by Amazon "Customers Also Bought."

### 4.3 Matrix Factorization — SVD (Explicit)

Minimizes: sum over observed (u, i): (R[u, i] - U[u] · V[i])^2 + lambda * (||U[u]||^2 + ||V[i]||^2)

Solved via SGD or Alternating Least Squares. For explicit ratings, sklearn TruncatedSVD or Surprise SVD work well. Does not scale gracefully to implicit feedback.

### 4.4 Matrix Factorization — ALS (Implicit)

Loss: sum over all (u, i): c_ui * (p_ui - U[u] · V[i])^2 + lambda * (||U||^2 + ||V||^2)

where p_ui = 1 if r_ui > 0 else 0 (binary preference), c_ui = 1 + alpha * r_ui (confidence).

ALS alternates closed-form updates:
- Fix V, solve for each U[u] independently (parallelizable)
- Fix U, solve for each V[i] independently (parallelizable)

Convergence: ~10–20 iterations. Closed-form solution per user/item: linear system solve (k x k matrix, fast).

### 4.5 BPR

Loss: -sum over (u, i, j): log(sigma(U[u] · V[i] - U[u] · V[j])) + regularization

where i is observed, j is uniformly sampled unobserved. Gradient ascent via SGD on triplets.

---

## 5. Architecture Diagrams

### 5.1 ALS Alternating Update Loop

```mermaid
%%{init: {'flowchart': {'curve': 'basis', 'nodeSpacing': 45, 'rankSpacing': 55}}}%%
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    init(["Init U, V — small random\nk latent factors"]) --> uu["User update — parallel over users\nA_u = Vᵀ·C_u·V + λI\nU_u = solve(A_u, b_u)"]
    uu --> iu["Item update — parallel over items\nA_i = Uᵀ·C_i·U + λI\nV_i = solve(A_i, b_i)"]
    iu --> conv{"Converged?\n~10-20 iterations"}
    conv -->|"no"| uu
    conv -->|"yes"| out(["Trained factors\nscore(u,i) = U_u · V_i"])

    class init,out io
    class uu train
    class iu train
    class conv mathOp
```

ALS freezes one factor matrix and solves the other in closed form, then swaps: each user and each item update is an independent k×k linear solve, so the whole step parallelizes cleanly across Spark workers. Convergence typically takes 10-20 alternations, and pre-computing VᵀV (or UᵀU) once per half-step keeps every per-entity solve cheap.

### 5.2 Collaborative Filtering Similarity

```
User-Item Matrix R (sparse):
         i1  i2  i3  i4  i5
   u1  [  5   ?   3   ?   1 ]
   u2  [  ?   4   ?   2   ? ]
   u3  [  1   ?   ?   5   3 ]
   u4  [  ?   2   4   ?   ? ]

User similarity = cosine between ROWS     -> sim(u1,u3) high if overlapping items rated alike
Item similarity = cosine between COLUMNS  -> sim(i1,i3) high if raters of i1 rate i3 alike
? = unobserved (99%+ of cells in real systems)
```

The matrix keeps its ASCII form because row/column alignment is the point: user-user similarity compares rows, item-item similarity compares columns.

### 5.3 Matrix Factorization — Latent Factors

```mermaid
%%{init: {'flowchart': {'curve': 'basis', 'nodeSpacing': 45, 'rankSpacing': 55}}}%%
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    R(["Sparse R\nusers × items · ~99% missing"]) --> mf["Matrix Factorization\nminimize Σ (R_ui - U_u·V_i)² + λ·regularization"]
    mf --> U(["U — user factors\nusers × k"])
    mf --> V(["V — item factors\nitems × k"])
    U --> dot(("dot\nproduct"))
    V --> dot
    dot --> pred(["Predicted R_hat_ui\ntop-K unseen items per user"])

    class R,U,V,pred io
    class mf train
    class dot mathOp
```

Factorization compresses the sparse matrix into two dense low-rank matrices whose product reconstructs it; each user and item becomes a k-dimensional vector (k = 50-200), and a predicted score is just the dot product of the two vectors. The latent dimensions emerge on their own — genre, mood, price sensitivity — none of them supplied as features.

### 5.4 Cold-Start Routing

```mermaid
%%{init: {'flowchart': {'curve': 'basis', 'nodeSpacing': 45, 'rankSpacing': 55}}}%%
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    nu([New / sparse user]) --> n{"How many interactions?"}
    n -->|"fewer than 3"| pop["Popularity fallback\ntop items in user's region"]
    n -->|"3 to 10"| fold["ALS folding-in\n+ content-based genre priors"]
    n -->|"more than 10"| full["Full ALS personalization\nlearned latent vector"]
    ni([New item · no interactions]) --> content["Content-based vector\ntext · category · price"]
    content --> full

    class nu,ni io
    class n mathOp
    class pop base
    class fold train
    class full train
    class content frozen
```

Cold start is a routing decision on interaction volume: too few signals fall back to popularity, a handful get a fast folding-in solve seeded by content priors, and rich histories earn full ALS personalization. New items enter through a content-based vector until enough collaborative signal accrues to place them in the learned factor space.

---

## 6. How It Works — Detailed Mechanics

```python
from __future__ import annotations

import numpy as np
from scipy.sparse import csr_matrix
from scipy.sparse.linalg import spsolve
from dataclasses import dataclass, field
from typing import Optional
import time


# ─────────────────────────────────────────────────────────────────────────────
# ALS for Implicit Feedback (from scratch, NumPy)
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class ALSConfig:
    n_factors: int = 128          # latent dimension k
    n_iterations: int = 15        # ALS alternating steps
    regularization: float = 0.01  # L2 regularization lambda
    alpha: float = 40.0           # confidence scaling factor
    random_state: int = 42


class ImplicitALS:
    """Alternating Least Squares for implicit feedback collaborative filtering.

    Based on Hu, Koren, Volinsky (2008) — Collaborative Filtering for Implicit
    Feedback Datasets.
    """

    def __init__(self, config: ALSConfig) -> None:
        self.config = config
        self.user_factors: Optional[np.ndarray] = None   # (n_users, k)
        self.item_factors: Optional[np.ndarray] = None   # (n_items, k)

    def fit(
        self,
        interactions: np.ndarray,  # (n_users, n_items) raw counts (clicks, plays)
    ) -> "ImplicitALS":
        cfg = self.config
        rng = np.random.default_rng(cfg.random_state)
        n_users, n_items = interactions.shape

        # Confidence matrix: C[u, i] = 1 + alpha * r[u, i]
        # preference matrix: P[u, i] = 1 if r[u,i] > 0 else 0
        C = 1.0 + cfg.alpha * interactions           # (n_users, n_items)
        P = (interactions > 0).astype(np.float32)    # (n_users, n_items)

        # Initialize factor matrices randomly
        self.user_factors = rng.normal(0, 0.01, (n_users, cfg.n_factors)).astype(np.float32)
        self.item_factors = rng.normal(0, 0.01, (n_items, cfg.n_factors)).astype(np.float32)

        lambda_I = cfg.regularization * np.eye(cfg.n_factors, dtype=np.float32)

        for iteration in range(cfg.n_iterations):
            t0 = time.time()

            # ── Update user factors ──────────────────────────────────────────
            # For each user u:
            #   A_u = V.T @ diag(C[u]) @ V + lambda * I
            #   b_u = V.T @ diag(C[u]) @ P[u]
            #   user_factors[u] = inv(A_u) @ b_u
            V = self.item_factors                    # (n_items, k)
            VtV = V.T @ V                            # (k, k)  pre-compute

            for u in range(n_users):
                c_u = C[u]       # (n_items,)
                p_u = P[u]       # (n_items,)
                # Only non-zero confidences differ from VtV
                # Efficient: VtV + V.T @ diag(c_u - 1) @ V
                # For simplicity here we use dense; in practice use sparse C
                diag_cu = np.diag(c_u - 1.0)
                A_u = VtV + V.T @ diag_cu @ V + lambda_I
                b_u = V.T @ (c_u * p_u)
                self.user_factors[u] = np.linalg.solve(A_u, b_u)

            # ── Update item factors ──────────────────────────────────────────
            U = self.user_factors                    # (n_users, k)
            UtU = U.T @ U                            # (k, k)

            for i in range(n_items):
                c_i = C[:, i]    # (n_users,)
                p_i = P[:, i]    # (n_users,)
                diag_ci = np.diag(c_i - 1.0)
                A_i = UtU + U.T @ diag_ci @ U + lambda_I
                b_i = U.T @ (c_i * p_i)
                self.item_factors[i] = np.linalg.solve(A_i, b_i)

            elapsed = time.time() - t0
            print(f"Iteration {iteration + 1}/{cfg.n_iterations}  ({elapsed:.2f}s)")

        return self

    def recommend(
        self,
        user_id: int,
        top_k: int = 10,
        filter_interacted: bool = True,
        interactions: Optional[np.ndarray] = None,
    ) -> list[tuple[int, float]]:
        """Return top-K item recommendations for a user."""
        assert self.user_factors is not None, "Call fit() first"
        user_vec = self.user_factors[user_id]               # (k,)
        scores = self.item_factors @ user_vec               # (n_items,)

        if filter_interacted and interactions is not None:
            interacted_mask = interactions[user_id] > 0
            scores[interacted_mask] = -np.inf

        top_indices = np.argpartition(scores, -top_k)[-top_k:]
        top_indices = top_indices[np.argsort(scores[top_indices])[::-1]]
        return [(int(idx), float(scores[idx])) for idx in top_indices]

    def similar_items(self, item_id: int, top_k: int = 10) -> list[tuple[int, float]]:
        """Find similar items using item factor cosine similarity."""
        assert self.item_factors is not None
        query = self.item_factors[item_id]
        norms = np.linalg.norm(self.item_factors, axis=1)
        query_norm = np.linalg.norm(query)
        similarities = (self.item_factors @ query) / (norms * query_norm + 1e-9)
        similarities[item_id] = -np.inf  # exclude self
        top_indices = np.argpartition(similarities, -top_k)[-top_k:]
        top_indices = top_indices[np.argsort(similarities[top_indices])[::-1]]
        return [(int(idx), float(similarities[idx])) for idx in top_indices]


# ─────────────────────────────────────────────────────────────────────────────
# SVD via sklearn for Explicit Ratings
# ─────────────────────────────────────────────────────────────────────────────

from sklearn.decomposition import TruncatedSVD


def svd_recommend_explicit(
    rating_matrix: np.ndarray,   # (n_users, n_items), NaN for missing
    user_id: int,
    n_components: int = 50,
    top_k: int = 10,
) -> list[tuple[int, float]]:
    """SVD-based recommendation for explicit rating matrix.

    Note: fills NaN with 0 — a known approximation; use ALS for better results.
    """
    filled = np.nan_to_num(rating_matrix, nan=0.0)
    svd = TruncatedSVD(n_components=n_components, random_state=42)
    U_reduced = svd.fit_transform(filled)   # (n_users, k)
    V_reduced = svd.components_             # (k, n_items)

    # Reconstruct predicted ratings for this user
    predicted = U_reduced[user_id] @ V_reduced   # (n_items,)

    # Mask already rated items
    rated_mask = ~np.isnan(rating_matrix[user_id])
    predicted[rated_mask] = -np.inf

    top_indices = np.argpartition(predicted, -top_k)[-top_k:]
    top_indices = top_indices[np.argsort(predicted[top_indices])[::-1]]
    return [(int(idx), float(predicted[idx])) for idx in top_indices]


# ─────────────────────────────────────────────────────────────────────────────
# BPR (Bayesian Personalized Ranking) — SGD training
# ─────────────────────────────────────────────────────────────────────────────

class BPR:
    """Bayesian Personalized Ranking for implicit feedback.

    Loss: -E[log sigma(x_uij)] + regularization
    where x_uij = U[u] · (V[i] - V[j])
    i = observed item, j = uniformly sampled unobserved item
    """

    def __init__(
        self,
        n_users: int,
        n_items: int,
        n_factors: int = 64,
        learning_rate: float = 0.01,
        regularization: float = 0.01,
        n_epochs: int = 20,
        random_state: int = 42,
    ) -> None:
        rng = np.random.default_rng(random_state)
        self.U = rng.normal(0, 0.01, (n_users, n_factors)).astype(np.float32)
        self.V = rng.normal(0, 0.01, (n_items, n_factors)).astype(np.float32)
        self.lr = learning_rate
        self.reg = regularization
        self.n_epochs = n_epochs
        self.n_items = n_items

    def _sample_negative(
        self,
        user_id: int,
        positive_items: set[int],
        rng: np.random.Generator,
    ) -> int:
        """Sample a negative item (not interacted by user)."""
        while True:
            j = int(rng.integers(0, self.n_items))
            if j not in positive_items:
                return j

    def fit(
        self,
        interactions: dict[int, set[int]],   # {user_id: set of item_ids}
        n_samples_per_epoch: int = 100_000,
    ) -> "BPR":
        rng = np.random.default_rng(42)
        user_ids = list(interactions.keys())

        for epoch in range(self.n_epochs):
            total_loss = 0.0
            for _ in range(n_samples_per_epoch):
                # Sample user
                u = int(rng.choice(user_ids))
                pos_items = interactions[u]
                if not pos_items:
                    continue
                # Sample positive item
                i = int(rng.choice(list(pos_items)))
                # Sample negative item
                j = self._sample_negative(u, pos_items, rng)

                # Compute BPR loss gradient
                x_uij = float(np.dot(self.U[u], self.V[i] - self.V[j]))
                sigmoid = 1.0 / (1.0 + np.exp(-x_uij))
                # Gradient of log(sigmoid(x_uij)) w.r.t. x_uij = 1 - sigmoid
                grad_coef = 1.0 - sigmoid

                # Update factors
                self.U[u] += self.lr * (grad_coef * (self.V[i] - self.V[j]) - self.reg * self.U[u])
                self.V[i] += self.lr * (grad_coef * self.U[u] - self.reg * self.V[i])
                self.V[j] += self.lr * (-grad_coef * self.U[u] - self.reg * self.V[j])

                total_loss -= np.log(sigmoid + 1e-9)

            avg_loss = total_loss / n_samples_per_epoch
            print(f"Epoch {epoch + 1}/{self.n_epochs}  BPR loss: {avg_loss:.4f}")

        return self

    def recommend(self, user_id: int, top_k: int = 10) -> list[tuple[int, float]]:
        scores = self.V @ self.U[user_id]
        top_indices = np.argpartition(scores, -top_k)[-top_k:]
        top_indices = top_indices[np.argsort(scores[top_indices])[::-1]]
        return [(int(idx), float(scores[idx])) for idx in top_indices]


# ─────────────────────────────────────────────────────────────────────────────
# Cold Start Fallback
# ─────────────────────────────────────────────────────────────────────────────

def popularity_fallback(
    interaction_counts: np.ndarray,   # (n_items,) total interactions per item
    user_interacted: set[int],
    top_k: int = 10,
) -> list[tuple[int, float]]:
    """Return most popular items the user has not interacted with.

    Used as cold-start fallback when user has fewer than 5 interactions.
    """
    scores = interaction_counts.copy().astype(float)
    for item_id in user_interacted:
        scores[item_id] = -np.inf
    top_indices = np.argpartition(scores, -top_k)[-top_k:]
    top_indices = top_indices[np.argsort(scores[top_indices])[::-1]]
    return [(int(idx), float(scores[idx])) for idx in top_indices]
```

---

## 7. Real-World Examples

**Netflix Prize**: The 2009 winning solution combined over 100 models, but pure matrix factorization (SVD++) achieved ~8.5% RMSE improvement by itself. SVD++ extended standard MF by incorporating implicit feedback signals (which movies a user rated, regardless of the rating value) into the user vector. This was a key insight: the act of rating something tells you something about the user even if the rating is negative.

**Amazon Item-Item CF**: Amazon's 2003 paper "Amazon.com Recommendations: Item-to-Item Collaborative Filtering" described computing item-item similarities from the co-purchase graph. The key insight was that item-item similarities are more stable over time than user-user similarities (new users arrive constantly; item relationships change slowly). This allows pre-computing the full similarity table offline, making real-time recommendations O(1) lookups.

**Spotify "implicit" ALS**: Spotify used ALS on implicit play-count data to power Discover Weekly's initial artist-level recommendations. The confidence parameter alpha=40 was found empirically — too low and the model treats all items equally, too high and popular items dominate at the expense of long-tail discovery.

**Last.fm scrobble data**: One of the classic implicit CF benchmarks. Users' listening histories (scrobble counts) fed into ALS produce latent factor spaces where distance correlates strongly with musical genre and style — emergent behavior not provided as input features.

---

## 8. Tradeoffs

| Method | Training Complexity | Prediction Complexity | Quality | Cold Start |
|--------|--------------------|-----------------------|---------|------------|
| User-User CF | O(U^2 * I) | O(K * I) per request | Medium | Poor |
| Item-Item CF | O(I^2 * U) offline | O(history * K) | Medium-High | User: OK |
| SVD (explicit) | O(U * I * k) | O(k) | Good | Poor |
| ALS (implicit) | O(iters * (U+I) * k^2) | O(k) | Very Good | Poor |
| BPR | O(epochs * samples) | O(k) | Good (ranking) | Poor |

| Parameter | Recommended Range | Effect if Too Low | Effect if Too High |
|-----------|------------------|------------------|--------------------|
| n_factors (k) | 64–256 | Underfits; misses subtle taste | Overfits; slow |
| alpha | 20–60 | Ignores interaction frequency | Over-weights popular items |
| regularization | 0.001–0.1 | Overfits to observed cells | Underfits, factors collapse to 0 |
| n_iterations | 10–20 | Converges poorly | Diminishing returns |

---

## 9. When to Use / When NOT to Use

**Use user-user CF when:**
- Catalog is small and static (<50K items)
- Interpretability is required ("users like you also liked...")
- You want a quick baseline without training

**Use item-item CF when:**
- Catalog is static and item counts are manageable
- User histories are short (few interactions) — item similarities are more reliable
- Real-time serving is required with minimal infrastructure

**Use ALS for implicit feedback when:**
- You have click/purchase logs without explicit ratings
- You need a trained model that generalizes to new users' interaction histories
- Catalog is large but manageable (up to ~10M items with distributed ALS)

**Use BPR when:**
- Your offline evaluation metric is a ranking metric (NDCG, MRR), not RMSE
- You have implicit feedback and want to directly optimize ranking
- Training data is dense enough for efficient triplet sampling

**Do NOT use memory-based CF when:**
- User base > 5M (pairwise similarity computation is prohibitive)
- Catalog changes rapidly (new items invalidate pre-computed similarities)
- Latency requirements are strict (<50ms) without pre-computation infrastructure

**Do NOT use SVD for implicit feedback:**
- SVD minimizes squared error on observed ratings only; it ignores the unobserved cells that ALS treats as weak negatives. This leads to poor ranking quality for implicit data.

---

## 10. Common Pitfalls

**Pitfall 1 — Wrong loss function for implicit feedback**: A team used SVD minimizing MSE on click counts (treating 0 as a known negative rating). The model learned to recommend only non-clicked items because the vast majority of zeros dominated the gradient. The fix: use ALS with binary preference P[u,i] = 1 if r[u,i] > 0 and confidence weighting, so unobserved interactions are treated as weakly negative, not strongly negative.

**Pitfall 2 — Not filtering already-interacted items at serving time**: In production, ALS scores all items including those the user has already purchased. Users received recommendations for items they bought last week. The fix: maintain a per-user interacted set in Redis; filter at serving time before returning recommendations. This is a filter_interacted=True flag — always enable it.

**Pitfall 3 — Alpha parameter too high causing popularity collapse**: alpha=400 was accidentally configured in a production ALS job. The confidence for a single click became 401 vs. 1 for unobserved — so even one interaction created an enormous gradient. Result: after 5 iterations, the model recommended the same 50 most popular items to all users regardless of history. Fix: use alpha=40 as default, validate with diversity metric ILD; if ILD drops significantly between iterations, reduce alpha.

**Pitfall 4 — Training on raw play counts without log transformation**: A music service fed raw play counts (0–5000 plays per song) into ALS. Songs a user played 1000 times dominated the latent factors. Fix: apply log transformation r_ui = log(1 + r_ui) before computing confidence, so the difference between 100 and 1000 plays is less dramatic than between 1 and 2 plays.

**Pitfall 5 — Not parallelizing ALS user/item updates**: ALS was run serially (one user at a time on a single core). 5M users x 128 factors x 15 iterations took 18 hours. Fix: the user updates are fully independent — distribute across Spark workers. With 200 cores, same job runs in ~7 minutes. The `implicit` library's ALS implementation handles this automatically with Cython/GPU acceleration.

---

## 11. Technologies & Tools

| Tool | Type | Notes |
|------|------|-------|
| `implicit` (Python) | ALS + BPR | Fast Cython/GPU ALS, production-grade, used by Spotify |
| `LightFM` | Hybrid CF+CB | Supports user/item features alongside CF; useful for cold start |
| `Surprise` | SVD, KNN | Research/prototyping, not production scale |
| `RecBole` | Research framework | 70+ models, unified interface for benchmarking |
| `PyTorch` | Custom MF/BPR | Full control for non-standard loss functions |
| `Apache Spark MLlib` | Distributed ALS | Production-scale; handles 100M+ users via Spark workers |
| `FAISS` | ANN for item retrieval | After ALS, store item vectors in FAISS for fast nearest neighbor |

---

## 12. Interview Questions with Answers

**Q: What is the difference between user-user and item-item collaborative filtering?**
User-user CF finds the K users most similar to the target user (by cosine similarity on their interaction vectors) and aggregates their ratings for unseen items. Item-item CF finds, for each item the target user has interacted with, the K most similar items, and scores candidates by weighted similarity. Item-item is preferred in practice because item similarities are more stable over time (user preferences shift; item relationships do not), and the similarity table can be pre-computed offline, making serving O(1).

**Q: How does ALS handle the fact that implicit feedback has no true negatives?**
ALS for implicit (Hu, Koren, Volinsky 2008) introduces a binary preference matrix P[u,i] = 1 if r[u,i] > 0, and a confidence matrix C[u,i] = 1 + alpha * r[u,i]. For unobserved interactions, P[u,i] = 0 and C[u,i] = 1 — a low but nonzero confidence that the user dislikes the item. For observed interactions, confidence scales with frequency. The model learns to satisfy high-confidence preferences strongly while only weakly satisfying low-confidence (unobserved) preferences. This avoids the pathology of treating all unobserved items as equally irrelevant.

**Q: What is the role of the confidence parameter alpha in ALS?**
Alpha scales the raw interaction counts into confidence values: c_ui = 1 + alpha * r_ui. With alpha = 40, a user who clicked an item once has confidence 41 vs. confidence 1 for a non-clicked item. Alpha controls how much weight positive interactions receive relative to unobserved items. Typical values: 40 for e-commerce click data, 1 for binary (clicked or not) data. Too high: popular items dominate and diversity collapses. Too low: the model cannot distinguish between items the user loves and items they have never seen.

**Q: Why does BPR outperform ALS for ranking tasks?**
ALS minimizes a weighted squared error loss — it optimizes rating prediction. BPR directly optimizes a pairwise ranking loss: for each user, the model is penalized whenever a non-interacted item is scored higher than an interacted item. Since the downstream evaluation metric (NDCG, MRR) cares about ranking order, BPR's training objective is more aligned with the evaluation. Empirically, BPR achieves better NDCG and MRR at equivalent factor dimensionality, though ALS can achieve better RMSE.

**Q: Explain the cold start problem in collaborative filtering and your mitigation strategy.**
User cold start: a new user has no interaction history, so their latent vector cannot be computed. Item cold start: a new item has no interactions, so its latent vector is unknown. Mitigations for user cold start: show popular or trending items; use an onboarding quiz to infer initial preferences; or use a content-based fallback using any available demographic/context features. Mitigations for item cold start: compute item embeddings from content features (text, category, price) using a content-based model; inject new items into ALS using the item embedding from content as initialization; run more frequent ALS updates (hourly batch) to incorporate early signals quickly.

**Q: How would you evaluate a collaborative filtering model offline?**
Use a temporal train/test split: train on interactions before date T, test on interactions after T. Metrics: Recall@K (fraction of test interactions recovered in top-K recommendations), NDCG@K (ranking quality), MRR. Avoid random train/test splits — they cause data leakage because future interactions of the same user inform the model about that user's preferences. Also evaluate diversity (ILD) to detect popularity bias, and measure coverage (fraction of catalog that appears in recommendations for at least one user).

**Q: What happens if you set regularization too low in matrix factorization?**
With regularization lambda close to 0, user and item latent vectors can grow arbitrarily large to minimize training loss on observed cells. This causes severe overfitting: the model perfectly predicts observed interactions but fails on unobserved ones. In practice, you see training loss near zero but poor Recall@K on the test set. The fix: set lambda in the range 0.001–0.1 (empirically tuned), or use cross-validation on a held-out validation split to select lambda.

**Q: How does SVD++ extend standard matrix factorization?**
SVD++ augments the user latent vector with an implicit feedback component: user_vector_u = user_factor_u + (1/sqrt(|N(u)|)) * sum over j in N(u) of implicit_factor_j, where N(u) is the set of items user u has interacted with (regardless of rating value) and implicit_factor_j is a learned vector for each item. This captures the notion that which items a user chose to interact with (not just how they rated them) is informative. SVD++ won the Netflix Prize and typically outperforms standard MF by 2–5% RMSE.

**Q: What is the complexity of item-item CF pre-computation and how would you scale it?**
Naive item-item similarity computation requires O(I^2 * U) time (for all item pairs, compute cosine similarity on user interaction vectors of length U). For 1M items, this is 10^12 operations — infeasible. Scalable approaches: (1) use MinHash/LSH to approximate nearest items in O(I * U) with a false-negative tradeoff; (2) compute similarities only for item pairs that share at least one user (co-occurrence filtering, sparse matrix multiply); (3) use approximate nearest neighbor on item embeddings from a trained ALS model (FAISS). The `implicit` library computes item similarities via the ALS item factor dot products, which is O(I^2 * k) but k is small (128) vs. U (millions).

**Q: What is the sparsity problem in collaborative filtering and why does it matter?**
The user-item interaction matrix for a large platform (200M users, 10M items) contains 2 * 10^15 cells. With 50M total interactions, sparsity is 1 - 50M/(2*10^15) > 99.999%. Algorithms that operate on the dense matrix (naive SVD) fail because most entries are unknown. Matrix factorization addresses this by optimizing only over observed cells (plus weak signals for unobserved in ALS). Neighborhood methods fail because most user pairs share zero common items, making cosine similarity undefined. Remedies: use product graph (items frequently co-purchased) to find similar items even with sparse direct interactions; use session data (within-session views) to fill in co-occurrence signal.

**Q: Compare ALS and SGD for training matrix factorization.**
ALS alternates between closed-form updates for user factors (holding item factors fixed) and item factors (holding user factors fixed). Each update is an exact solution to a linear system, so convergence per iteration is reliable. Parallelizes perfectly — each user/item update is independent. Best for implicit feedback with the confidence weighting structure. SGD (stochastic gradient descent) processes one (user, item) pair at a time, updating both factors by gradient step. More flexible (any loss function), lower memory, but requires careful learning rate scheduling. ALS converges in 10–20 iterations and is preferred for large-scale implicit CF; SGD is used for explicit feedback and custom losses like BPR.

**Q: How would you handle new users arriving between ALS training runs?**
Strategy 1: folding in. Given a new user's interaction vector r_new (length n_items), compute their user factor analytically using the fixed item factor matrix V: u_new = solve((V.T @ diag(c_new) @ V + lambda*I), V.T @ (c_new * p_new)). This is a single linear solve with pre-computed V.T @ V. No retraining required. Limitation: the new user's interactions do not influence item factors until the next training run. Strategy 2: real-time online updates via SGD — process the new user's interactions as they arrive. Strategy 3: content-based cold start — use available features (age group, country, onboarding preferences) to select an initial user factor from the learned factor space (nearest centroid assignment).

**Q: What is item frequency bias in ALS and how do you correct it?**
Frequently interacted items accumulate high confidence weights across many users. Their item factors are trained on far more data and become large-magnitude vectors. When computing dot products for ranking, high-magnitude item factors dominate even for users who have never interacted with those items. Correction: L2-normalize item factors before computing recommendation scores; or apply popularity-based score discounting: score(u,i) = U[u] · V[i] / log(1 + popularity(i)). The `implicit` library includes a popularity-based filter option for this reason.

**Q: Describe how you would A/B test a new collaborative filtering model.**
Design: split users randomly into control (current model) and treatment (new ALS model) groups. Ensure the split is at the user level (not request level) to avoid interference. Run for at least 2 weeks to capture weekly seasonal patterns. Primary metrics: click-through rate, session watch/listen time, conversion rate. Secondary metrics: diversity (ILD), long-tail coverage, return rate (did users come back?). Statistical test: two-sample t-test or Mann-Whitney U for continuous metrics; chi-squared for binary. Guard against novelty effect: users may click on new recommendations simply because they are different, not because they are better. Require sustained improvement over the full test period.

**Q: Why does collaborative filtering suffer from popularity bias?**
Popular items have more interactions and thus more evidence in the training data. Their latent factors are trained on thousands of data points while long-tail items are trained on tens. This makes popular items' factors more accurate and their dot product scores more extreme. At serving time, the model consistently ranks popular items higher. This creates a feedback loop: popular items get recommended more, receive more clicks, become more popular. Corrections: (1) downweight popular items in the confidence matrix (c_ui = 1 + alpha * log(1 + r_ui)); (2) apply re-ranking diversity constraints; (3) evaluate with coverage and novelty metrics alongside NDCG.

**Q: Why does filling unobserved cells with zero and running SVD hurt implicit-feedback recommendations?**
Zero-filling tells SVD that every unobserved pair is a confirmed negative rating, so the millions of zeros dominate the squared-error loss and the model is pulled toward predicting zero everywhere. Implicit feedback has no true negatives — a missing click may mean the user never saw the item — so treating absence as a hard zero is simply wrong. ALS fixes this with a binary preference plus confidence weighting, so unobserved cells are only weakly negative rather than strongly negative.

**Q: Why apply a log transformation to raw interaction counts before ALS?**
Raw counts let a song played 1000 times dominate the latent factors, so log1p compression makes the gap between 100 and 1000 plays far smaller than the gap between 1 and 2 plays. Power users and binge-listened items otherwise skew the confidence weights and collapse diversity toward a few heavy-tail items. The transform r = log(1 + count) preserves ordering while damping the tail, which is often more impactful than tuning n_factors or lambda.

**Q: What is the difference between memory-based and model-based collaborative filtering?**
Memory-based CF computes similarities directly from the interaction matrix at prediction time, while model-based CF learns a compact model — usually latent factors — offline and predicts from it. Memory-based (user-user, item-item) is simple and interpretable but scales poorly and needs the full matrix at serving; model-based (matrix factorization, ALS, BPR) compresses the matrix into low-rank factors that generalize better on sparse data and serve as O(k) dot products. Most large systems use model-based factors, often keeping item-item neighborhoods for the "similar items" rail.

**Q: How does negative sampling work in BPR and why does the sampling strategy matter?**
For each observed (user, item) pair BPR draws an unobserved item as a negative and trains the user to score the positive above it, so the sampler's distribution directly shapes what the model learns. Uniform sampling is cheap but wastes most updates on easy negatives the model already ranks low; popularity-based or dynamic hard-negative sampling speeds convergence and sharpens tail ranking, at the risk of drawing false negatives (items the user would actually like). Tune the sampler alongside the learning rate, and always exclude the user's known positives from the negative pool.

---

## 13. Best Practices

1. Use ALS with implicit feedback rather than SVD for click/purchase data — ALS treats unobserved interactions as weak negatives, which is the correct assumption.
2. Always apply log transformation to raw interaction counts before computing confidence: r = log(1 + count). Raw counts heavily skew toward power users.
3. Set alpha between 20 and 60; validate with ILD (intra-list diversity) — if diversity collapses, reduce alpha.
4. Use temporal train/test split with at least 7 days of test data. Never use random split for sequential user behavior.
5. Filter already-interacted items at serving time — always. Store per-user interaction sets in Redis with TTL matching your catalog update frequency.
6. Pre-compute item-item similarity table for "similar items" use cases; do not recompute per request.
7. For cold-start users (fewer than 5 interactions), fall back to popularity-based recommendations — CF models perform worse than popularity for very sparse users.
8. Monitor item coverage (fraction of catalog recommended to at least one user) weekly. Coverage below 20% indicates severe popularity bias.
9. Implement "folding in" for new users between training runs — one linear solve gives a good user vector without full retraining.
10. Use distributed ALS (Apache Spark MLlib or `implicit` with GPU) for catalogs > 1M items — serial ALS will not complete in acceptable wall-clock time.

---

## 14. Case Study

**Problem**: A podcast platform (8M users, 300K shows, 200M listen events/month) wants to power a personalized homepage with "Shows You'll Love" recommendations. 35% of users are new with fewer than 3 listen events.

**Approach**:

```
DATA PIPELINE (Apache Spark, nightly):
  Input: listen_events table (user_id, show_id, seconds_listened, timestamp)
  Preprocessing:
    r_ui = log(1 + seconds_listened / show_duration)  # normalized listen fraction
    Filter: only events from last 90 days
    Filter: shows with < 10 total listeners removed (too sparse)

ALS TRAINING:
  n_factors = 128
  alpha     = 40
  lambda    = 0.01
  n_iters   = 15
  Runtime:  ~45 minutes on 50 Spark executors (8M users x 300K shows)

SERVING:
  User factors (8M x 128) → Redis (TTL 24h), ~400MB with float16
  Item factors (300K x 128) → FAISS IVF256 index, ~150MB
  Request time:
    1. Fetch user_factor from Redis (1ms)
       If missing: popularity_fallback()  [35% of traffic]
    2. FAISS ANN search: top-100 candidates (2ms)
    3. Filter listened shows (Redis set lookup, 1ms)
    4. Return top-20

COLD START HANDLING:
  < 3 listens: popularity-based (top 50 shows in user's country)
  3-10 listens: ALS folding-in with content-based genre priors
  > 10 listens: full ALS personalization

RESULTS after 4 weeks:
  Listen-through rate (>50% of episode): +22% vs. popularity baseline
  Weekly active users: +8% (discovery leading to retention)
  Long-tail show listens (rank > 1000): +41% (ALS surfaces niche content)
  Cold-start CTR (popularity fallback): +6% vs. previous random baseline
```

The key lesson: the log transformation of listen duration was more impactful than the choice of hyperparameters. A user who listened to 100% of a 1-hour episode should have slightly more signal than one who listened to 100% of a 3-minute episode — the log normalization captures this without letting episode length dominate the confidence weighting.
