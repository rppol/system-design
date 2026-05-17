# Design a Social Media Content Feed Ranking System (Twitter/LinkedIn Scale)

## Problem Statement

Design a content feed ranking system for a social media platform with 500 million users and
100K requests per second. Given a user session, select and rank 100 posts from thousands of
candidates (posts from followed accounts, algorithmically recommended content, and ads). The
system must optimize for multiple objectives simultaneously: engagement (likes, comments, shares),
dwell time (meaningful content consumption), and content quality/diversity (avoid echo chambers
and low-quality bait). The system must run end-to-end within 150ms and must not exhibit
popularity bias that starves new creators.

### Functional Requirements
- Rank feed for each of 500M users within 150ms
- Retrieve candidates from: followed accounts (last 24h), algorithm recommendations, ads
- Optimize multi-objective: engagement + dwell time + content quality + creator diversity
- Enforce business constraints: ads must be 5-10% of feed, no 3 consecutive posts from same author
- Support online A/B testing: new ranking models must be testable on 1-5% traffic slices

### Non-Functional Requirements
- Latency: end-to-end < 150ms at P99 (10ms candidate retrieval + 80ms ranking + 60ms re-ranking)
- Throughput: 100K QPS sustained, 300K QPS peak (viral content events)
- Freshness: posts from last 2 minutes must be eligible for ranking within 5 minutes
- Fairness: new creator posts must receive at least 200 impressions before being ranked down
- Serving infrastructure: 99.99% uptime (ranking failure degrades to engagement-only fallback)

### Out of Scope
- Content moderation and safety filtering (upstream service, run before ranking)
- Ad auction pricing (separate system; ad service provides winning ad + bid price)
- Social graph storage and follower/following retrieval

---

## Architecture Overview

```
User opens feed (100K QPS)
          |
          v
+-------------------+
|    API Gateway    |
|  rate limiting    |
|  auth, routing    |
+-------------------+
          |
          v
+----------------------------+
|    Ranking Orchestrator    |
|  coordinates all stages    |
|  150ms total budget        |
+----------------------------+
          |
     +----+----+
     |         |
     v         v
[Candidate  [User Feature
 Retrieval]  Fetcher]
 10ms         5ms
     |         |
     +----+----+
          |
          v
+----------------------------------------------+
|         Candidate Pool (~2000 posts)          |
|  - Follower posts (graph traversal, 500-800)  |
|  - Two-tower ANN retrieval (600-800)          |
|  - Ad candidates from auction service (50)    |
+----------------------------------------------+
          |
          v
+----------------------------------------------+
|         Stage 1: Lightweight Filter          |
|  - Remove already-seen posts (Bloom filter)  |
|  - Hard business rules (adult content flags) |
|  - Dedup same post from multiple sources     |
|  -> 2000 -> 800 candidates                   |
+----------------------------------------------+
          |
          v
+----------------------------------------------+
|         Stage 2: MMOE Ranker (80ms)          |
|  Multi-gate Mixture of Experts               |
|  - Shared bottom: user x content features    |
|  - Expert networks: 8 shared, 4 per task     |
|  - Task towers: engagement, dwell, quality   |
|  - Output: score per task per post           |
|  -> Produces 800 ranked posts                |
+----------------------------------------------+
          |
          v
+----------------------------------------------+
|    Stage 3: Multi-Objective Aggregation      |
|  - Weighted sum: 0.4*engage + 0.3*dwell      |
|                + 0.2*quality + 0.1*fresh     |
|  - Weights tuned via Pareto optimization     |
|  - Ads injected at positions 4, 12, 25, 40   |
|  -> Top 150 posts selected                   |
+----------------------------------------------+
          |
          v
+----------------------------------------------+
|       Stage 4: DPP Diversity Re-ranking      |
|  - Determinantal Point Process re-ranking    |
|  - Penalize same-author consecutive posts    |
|  - Topic diversity (NLP topic clustering)    |
|  -> Final 100 posts in ranked order          |
+----------------------------------------------+
          |
          v
+-------------------+
|    Feed Response  |
|  100 ranked posts |
|  + metadata       |
+-------------------+


Offline Training Pipeline:
  User Actions (click, like, share, dwell, skip)
         |
         v
  [Kafka event stream]
         |
         v
  [Flink: label construction]
  (join actions with served impressions, 48h window)
         |
         v
  [Feature Store (online: Redis, offline: Hive)]
         |
         v
  [MMOE Training (PyTorch, 4xA100, 6h/week)]
         |
         v
  [Model Registry (MLflow)] -> [TorchServe]
```

---

## Key Design Decisions

### 1. Multi-gate Mixture of Experts (MMOE) for Multi-Task Learning

Naive multi-task learning with a shared bottom network suffers from the "seesaw effect": improving
engagement task degrades dwell time task because the gradient directions conflict. MMOE solves this
with expert networks (specialized sub-networks) and gating networks (one gate per task). Each task's
gating network learns to selectively use the experts most relevant to that task. The engagement gate
might rely on experts that learned short-form viral content signals, while the dwell gate relies on
experts that learned long-form quality signals.

### 2. Two-Tower Model for Algorithmic Candidate Retrieval

For non-followed content (recommendations), exact scoring of all 10 billion posts per user is
impossible at 100K QPS. Two-tower (dual encoder): user tower encodes user embedding, content tower
encodes post embedding. Similarity = dot product. Both towers are pre-computed — user embeddings
updated every 5 minutes, post embeddings at index time. FAISS HNSW index enables approximate
nearest neighbor search in < 10ms for 10B posts.

### 3. Determinantal Point Processes for Diversity

Maximizing engagement alone produces filter bubbles and author monopolization (top 5 creators
dominate every feed). DPP models diversity explicitly: the probability of selecting a set of posts
is proportional to the determinant of the kernel matrix, which is large when posts are both
high-quality (diagonal entries = relevance scores) and diverse (off-diagonal entries = similarity,
penalized). DPP naturally prevents the same author from appearing 5 times consecutively.

### 4. Delayed Negative Sampling

Naive training labels: shown post that was not clicked = negative example. But a user may not
have scrolled to that post yet. Training on these false negatives produces a model that penalizes
content in the lower feed. Solution: delayed negative sampling. Wait 48 hours before labeling a
shown post as negative. If the user re-opens the app and clicks the post within 48 hours, it
becomes a positive label.

### 5. Feedback Loop Mitigation

A pure ranking model trained on engagement creates feedback loops: viral content becomes more viral
because the model shows it more, generating more signals. Mitigations:
- Exploration budget: 5% of feed positions reserved for under-explored content (epsilon-greedy)
- Creator fairness: new posts from new creators receive a minimum 200 impressions before ranking
  data is used (warm-up period)
- Position bias correction: inverse propensity scoring (IPS) de-biases training labels for
  position effects (post at position 1 gets 3x more clicks than position 5, not because it is 3x
  better)

---

## Implementation

```python
from __future__ import annotations

import numpy as np
import torch
import torch.nn as nn
from dataclasses import dataclass
from typing import Optional


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class Post:
    post_id: str
    author_id: str
    content_embedding: np.ndarray    # 256-dim
    topic_vector: np.ndarray         # 64-dim topic distribution (from LDA or BERTopic)
    age_seconds: float               # seconds since post was created
    engagement_count: int            # likes + comments + shares at retrieval time


@dataclass
class RankedPost:
    post: Post
    engagement_score: float
    dwell_score: float
    quality_score: float
    final_score: float


# ---------------------------------------------------------------------------
# MMOE: Multi-gate Mixture of Experts
# ---------------------------------------------------------------------------

class ExpertNetwork(nn.Module):
    """Single expert: a 2-layer MLP processing the shared input."""

    def __init__(self, input_dim: int, hidden_dim: int, output_dim: int) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, output_dim),
            nn.ReLU(),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)


class GatingNetwork(nn.Module):
    """
    Softmax gate: learns which experts to weight for a given task.
    Output: probability distribution over n_experts.
    """

    def __init__(self, input_dim: int, n_experts: int) -> None:
        super().__init__()
        self.gate = nn.Linear(input_dim, n_experts)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return torch.softmax(self.gate(x), dim=-1)


class TaskTower(nn.Module):
    """Per-task prediction head operating on the gated expert mixture."""

    def __init__(self, expert_output_dim: int, hidden_dim: int) -> None:
        super().__init__()
        self.tower = nn.Sequential(
            nn.Linear(expert_output_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, 1),
            nn.Sigmoid(),              # output in [0, 1] — probability of positive label
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.tower(x).squeeze(-1)


class MMOE(nn.Module):
    """
    Multi-gate Mixture of Experts for multi-task feed ranking.

    Input: concatenated user + content feature vector
    Tasks: engagement (click/like/share), dwell_time, content_quality

    Architecture:
      - Shared bottom: linear projection to expert input space
      - n_shared_experts expert networks (shared across tasks)
      - One gating network per task (learns task-specific expert weighting)
      - One task tower per task

    Training:
      - Joint training on all tasks (weighted sum of task losses)
      - Task weights tuned to balance gradient magnitudes
      - BCE loss for binary tasks (engagement, quality)
      - MSE loss for regression task (dwell time in seconds)
    """

    def __init__(
        self,
        input_dim: int = 512,          # user features (256) + content features (256)
        expert_input_dim: int = 256,
        expert_hidden_dim: int = 128,
        expert_output_dim: int = 64,
        n_experts: int = 8,
        task_hidden_dim: int = 32,
        n_tasks: int = 3,              # engagement, dwell, quality
    ) -> None:
        super().__init__()

        # Shared bottom projection
        self.input_projection = nn.Sequential(
            nn.Linear(input_dim, expert_input_dim),
            nn.ReLU(),
        )

        # Expert networks (shared across all tasks)
        self.experts = nn.ModuleList([
            ExpertNetwork(expert_input_dim, expert_hidden_dim, expert_output_dim)
            for _ in range(n_experts)
        ])

        # Gating networks (one per task)
        self.gates = nn.ModuleList([
            GatingNetwork(expert_input_dim, n_experts)
            for _ in range(n_tasks)
        ])

        # Task towers
        self.towers = nn.ModuleList([
            TaskTower(expert_output_dim, task_hidden_dim)
            for _ in range(n_tasks)
        ])

    def forward(self, x: torch.Tensor) -> list[torch.Tensor]:
        """
        x: (batch_size, input_dim) — concatenated user + content features
        Returns: list of task predictions, each (batch_size,)
          [engagement_pred, dwell_pred, quality_pred]
        """
        shared = self.input_projection(x)                    # (B, expert_input_dim)

        # Compute all expert outputs
        expert_outputs = torch.stack(
            [expert(shared) for expert in self.experts], dim=1
        )                                                     # (B, n_experts, expert_output_dim)

        # Per-task: gate -> weighted sum of experts -> tower
        task_preds = []
        for gate, tower in zip(self.gates, self.towers):
            gate_weights = gate(shared).unsqueeze(-1)        # (B, n_experts, 1)
            gated_output = (expert_outputs * gate_weights).sum(dim=1)  # (B, expert_output_dim)
            pred = tower(gated_output)                        # (B,)
            task_preds.append(pred)

        return task_preds


def build_feature_vector(
    user_features: np.ndarray,    # 256-dim user embedding (pre-computed from user tower)
    post: Post,
    user_history_topics: np.ndarray,   # 64-dim topic distribution from user's last 50 interactions
) -> np.ndarray:
    """
    Concatenate user and content features into model input vector.
    Total: 256 + 256 = 512 dimensions.

    Content features (256-dim):
      - content_embedding (256-dim from content encoder)
      - age_score: log-decay freshness score
      - engagement_density: engagement_count / (age_seconds / 3600 + 1)
      - topic_user_affinity: dot product of post topic with user topic history
    """
    age_score = 1.0 / (1.0 + np.log1p(post.age_seconds / 3600.0))  # log decay over hours
    engagement_density = post.engagement_count / (post.age_seconds / 3600.0 + 1.0)
    topic_affinity = float(post.topic_vector @ user_history_topics)

    # Append scalar features to content embedding; truncate/pad to 256
    scalars = np.array([age_score, engagement_density, topic_affinity, 0.0])  # 4 scalars
    content_features = np.concatenate([post.content_embedding[:252], scalars])  # 252 + 4 = 256

    return np.concatenate([user_features, content_features])  # 512-dim


# ---------------------------------------------------------------------------
# DPP Diversity Re-ranking
# ---------------------------------------------------------------------------

class DPPReranker:
    """
    Determinantal Point Process re-ranking for feed diversity.

    DPP models a distribution over subsets where the probability of a set S is:
        P(S) proportional to det(L_S)
    where L is the kernel matrix: L_ij = q_i * q_j * k(i, j)
      q_i = quality/relevance score of item i (from MMOE)
      k(i, j) = similarity kernel between items i and j (cosine similarity of topic vectors)

    High det(L_S) = high-quality items that are diverse (low pairwise similarity).

    We use greedy MAP inference (greedy set augmentation) as exact DPP is O(n^3).
    Greedy MAP is O(k * n) where k = desired set size.

    Additional hard constraints applied after DPP:
      - Max 2 consecutive posts from same author
      - Max 30% of final feed from any single topic
    """

    def __init__(
        self,
        target_size: int = 100,
        diversity_weight: float = 0.3,   # trade-off: 0 = pure relevance, 1 = pure diversity
    ) -> None:
        self.target_size = target_size
        self.diversity_weight = diversity_weight

    def _build_kernel_matrix(
        self,
        scores: np.ndarray,              # (n,) relevance scores in [0, 1]
        topic_vectors: np.ndarray,       # (n, topic_dim) topic distributions
    ) -> np.ndarray:
        """L-ensemble kernel matrix. L_ij = sqrt(q_i) * k(i,j) * sqrt(q_j)"""
        n = len(scores)
        # Normalize topic vectors for cosine similarity
        norms = np.linalg.norm(topic_vectors, axis=1, keepdims=True) + 1e-9
        normalized = topic_vectors / norms

        # Similarity kernel: k(i, j) = cosine_similarity(topic_i, topic_j)
        similarity_matrix = normalized @ normalized.T   # (n, n), values in [-1, 1]
        # Shift to [0, 1]
        similarity_matrix = (similarity_matrix + 1.0) / 2.0

        # L-ensemble: L_ij = sqrt(q_i * q_j) * k(i, j)
        quality_outer = np.outer(np.sqrt(scores), np.sqrt(scores))
        L = quality_outer * (
            (1 - self.diversity_weight) * np.eye(n)
            + self.diversity_weight * similarity_matrix
        )
        return L

    def rerank(
        self,
        posts: list[RankedPost],
    ) -> list[RankedPost]:
        """
        Greedy MAP inference for DPP.
        Returns target_size posts maximizing DPP objective.
        """
        n = len(posts)
        if n <= self.target_size:
            return posts

        scores = np.array([p.final_score for p in posts])
        topic_vectors = np.vstack([p.post.topic_vector for p in posts])

        L = self._build_kernel_matrix(scores, topic_vectors)

        # Greedy MAP: iteratively select item that maximally increases log det(L_S)
        selected_indices: list[int] = []
        remaining = list(range(n))

        for _ in range(self.target_size):
            if not remaining:
                break

            best_gain = -float("inf")
            best_idx = remaining[0]

            for candidate in remaining:
                candidate_indices = selected_indices + [candidate]
                L_sub = L[np.ix_(candidate_indices, candidate_indices)]
                # Log det via Cholesky for numerical stability
                try:
                    chol = np.linalg.cholesky(L_sub + 1e-6 * np.eye(len(candidate_indices)))
                    log_det = 2.0 * np.sum(np.log(np.diag(chol)))
                except np.linalg.LinAlgError:
                    log_det = -float("inf")

                if log_det > best_gain:
                    best_gain = log_det
                    best_idx = candidate

            selected_indices.append(best_idx)
            remaining.remove(best_idx)

        # Apply hard constraints: enforce author diversity
        result = [posts[i] for i in selected_indices]
        result = self._enforce_author_diversity(result)
        return result

    def _enforce_author_diversity(self, posts: list[RankedPost]) -> list[RankedPost]:
        """
        Ensure no more than 2 consecutive posts from the same author.
        Swaps offending posts with the nearest post from a different author.
        """
        result = list(posts)
        for i in range(2, len(result)):
            if (result[i].post.author_id == result[i-1].post.author_id
                    == result[i-2].post.author_id):
                # Find next post from different author to swap in
                for j in range(i + 1, len(result)):
                    if result[j].post.author_id != result[i].post.author_id:
                        result[i], result[j] = result[j], result[i]
                        break
        return result


# ---------------------------------------------------------------------------
# Multi-Objective Score Aggregation
# ---------------------------------------------------------------------------

def aggregate_scores(
    ranked_posts: list[tuple[Post, list[float]]],   # (post, [engage, dwell, quality])
    weights: tuple[float, float, float, float] = (0.4, 0.3, 0.2, 0.1),
    freshness_decay_hours: float = 24.0,
) -> list[RankedPost]:
    """
    Aggregate multi-task scores into a single final score.

    weights = (engagement_weight, dwell_weight, quality_weight, freshness_weight)
    Sum of weights must equal 1.0.

    Freshness score: exponential decay. Posts older than freshness_decay_hours
    receive 50% freshness credit.
    """
    assert abs(sum(weights) - 1.0) < 1e-6, "Weights must sum to 1.0"
    w_engage, w_dwell, w_quality, w_fresh = weights

    result = []
    for post, (engage, dwell, quality) in ranked_posts:
        freshness = np.exp(-post.age_seconds / (freshness_decay_hours * 3600.0))
        final = (
            w_engage * engage
            + w_dwell * dwell
            + w_quality * quality
            + w_fresh * float(freshness)
        )
        result.append(RankedPost(
            post=post,
            engagement_score=float(engage),
            dwell_score=float(dwell),
            quality_score=float(quality),
            final_score=float(final),
        ))

    return sorted(result, key=lambda r: r.final_score, reverse=True)


# ---------------------------------------------------------------------------
# Position Bias Correction (Inverse Propensity Scoring)
# ---------------------------------------------------------------------------

def ips_corrected_label(
    label: float,             # raw binary label (1 = clicked, 0 = not clicked)
    position: int,            # 0-indexed position in feed where post was served
    propensity_scores: list[float],   # P(click | position) for each position (from observation)
) -> float:
    """
    IPS-corrected label for training data de-biasing.

    Without correction: posts shown at position 0 get 3x more clicks than position 5.
    Training on raw labels makes the model learn position effects, not content quality.

    IPS correction: weight each training example by 1/P(shown at that position).
    P(click | position) estimated from randomized exposure experiments (1% traffic bucket
    where positions are assigned randomly).

    For negative labels (label=0), IPS weight = 1 (no propensity bias for non-clicks).
    For positive labels (label=1), IPS weight = 1 / propensity_scores[position].
    """
    if label == 0:
        return 0.0
    propensity = propensity_scores[position] if position < len(propensity_scores) else 1.0
    return label / (propensity + 1e-9)
```

---

## ML Components Used

| Component | Purpose | Key Parameters |
|-----------|---------|----------------|
| Two-Tower Model | Candidate retrieval for non-followed content | 256-dim embeddings, FAISS HNSW index |
| MMOE | Multi-task ranking: engagement + dwell + quality | 8 experts, 3 tasks, joint training |
| DPP (Greedy MAP) | Diversity re-ranking, author diversity | diversity_weight=0.3, greedy O(k*n) |
| Bloom Filter | De-duplicate seen posts at O(1) per post | 10M bits, 5 hash functions, 1% FPR |
| IPS Correction | De-bias training labels for position effects | propensity from 1% randomized bucket |
| Delayed Negative Sampling | Prevent false negatives from unviewed posts | 48-hour label assignment window |
| Kafka | User action event stream (clicks, likes, dwells) | 16 partitions, 7-day retention |
| Flink | Training label construction (join actions with impressions) | 48h join window |
| Redis | Feature store online serving (user embeddings) | 5-min TTL, <5ms read |
| TorchServe | MMOE model serving | dynamic batching, 4xA100 GPU |

---

## Tradeoffs and Alternatives

| Decision | Chosen Approach | Alternative | Reason |
|----------|----------------|-------------|--------|
| Multi-task architecture | MMOE | Shared-bottom MTL | MMOE eliminates seesaw effect between conflicting tasks; simple shared-bottom fails for engagement vs dwell conflict |
| Diversity | DPP re-ranking | Greedy MMR | DPP: principled probabilistic diversity; MMR simpler but only considers pairwise similarity to already-selected set |
| Candidate retrieval | Two-tower + FAISS | BM25 text retrieval | Two-tower: captures semantic similarity beyond keyword overlap; BM25 good for text search but misses visual/social signals |
| Training labels | Delayed negatives | Immediate negatives | Delayed negatives: eliminates false negatives for unseen posts; immediate labels cause model to penalize lower-feed content |
| Position bias | IPS correction | No correction | IPS: de-biases gradient; without it, model learns position > content quality |
| Diversity constraint | Hard author limit + DPP | Pure DPP | Hard limits enforce business rules deterministically; DPP provides soft diversity across topics |

---

## Interview Discussion Points

**Q: How do you handle the feedback loop where popular content becomes more popular?**
A: Three mechanisms. First, exploration budget: 5% of feed positions use epsilon-greedy random
selection from the top-500 candidates instead of top-ranked. This gives under-explored posts
click data. Second, creator fairness: new creator posts receive a minimum 200 impressions with
boosted ranking before their organic engagement data drives their score. Third, feature normalization:
the engagement_density feature (engagement per hour) prevents viral posts from dominating because
a post with 100K likes over 24 hours has the same density as one with 4K likes over 1 hour.

**Q: How do you evaluate the multi-objective ranking model when objectives conflict?**
A: Pareto front analysis. Run A/B tests varying objective weights. For each weight combination,
measure engagement rate, median dwell time, and creator diversity index (Gini coefficient of
creator share). Plot the tradeoff surface. Business stakeholders select the weight combination
on the Pareto front that meets minimum thresholds for each objective. The key insight: there is
no single "best" weight — it is a business decision about which tradeoff is acceptable. Online
metrics (A/B test) are the source of truth; offline NDCG metrics are used only for fast iteration.

**Q: How do you prevent the DPP re-ranking from being too slow at 100K QPS?**
A: Three optimizations. First, greedy MAP instead of exact DPP: O(k*n) = O(100 * 800) = 80,000
operations per request, feasible in < 10ms. Second, topic dimension reduction: 64-dim topic
vectors (from BERTopic) instead of full embedding for the kernel matrix, reducing memory and compute.
Third, hardware: DPP runs on CPU (no GPU required for matrix operations on 800x800 matrices).
At 100K QPS, this requires ~50 CPU cores. For further speedup: approximate DPP via random
sketching reduces the 800-item pool to a quality-stratified sample of 200 before DPP.

**Q: How do you A/B test a new ranking model without degrading the full user base?**
A: Shadow mode first, then canary, then full rollout. Shadow mode: new model runs for 5% of
requests but results are discarded — only used to compare offline metrics (logging scores vs served
scores). After shadow validation, canary: 1% traffic sees new model results. Monitor engagement
rate, session length, and unfollows (leading indicator of feed dissatisfaction). If P99 latency
and business metrics stay within 5% of control after 48 hours, roll to 10%, then 100%. Rollback
trigger: any 10% degradation in session engagement rate or any latency regression above 20ms P99.
