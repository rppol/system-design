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

---

## Failure Scenarios and Recovery

### Failure 1: MMOE Expert Collapse During Multi-Task Training

**What failed:** The MMOE ranker was trained jointly on engagement, dwell time, and content quality tasks. After a model update that removed content quality labels from 40% of training examples (labeling pipeline bug), two of the 8 shared expert networks collapsed: they learned to specialize exclusively on engagement prediction and stopped contributing meaningfully to dwell time or quality tasks. The result: the quality gate in re-ranking was producing near-constant scores (expert outputs flat), so all content passed the quality filter. Low-quality bait posts (high engagement, low dwell time, low quality) flooded the feed. Report rate (users reporting content as low quality) increased 22% over 2 weeks.

**Detection:** Expert utilization monitoring: track the variance of each expert network's output across a 10,000-sample diagnostic batch. Two experts had output variance < 0.001 (effectively constant). Alert triggered. Time-to-detect: 4 days (monitoring ran weekly).

**Recovery steps:**
1. Fixed labeling pipeline: restored content quality labels, validated label completeness as >95% before next training run.
2. Added expert diversity loss: auxiliary loss penalizing low variance in expert outputs, added to main training loss with weight 0.01.
3. Retrained MMOE model; expert outputs recovered full variance within 2 epochs.
4. Changed expert utilization monitoring from weekly to per-training-run.

**Prevention:** Require all training data labels to have completeness > 95% before starting training. Validate in CI/CD preprocessing step; abort training run if completeness is below threshold.

---

### Failure 2: Bloom Filter False Positive Eliminating Freshly-Published Content

**What failed:** The candidate filter used a Bloom filter to exclude already-seen posts from the feed. The Bloom filter was initialized with 10M items per user (7-day seen history) at 0.1% false positive rate (well-designed for that capacity). However, the platform grew rapidly and the number of posts per user grew to 25M items without resizing the Bloom filter. The false positive rate climbed to 3.2%, meaning 3.2% of new posts were incorrectly classified as already-seen and excluded from retrieval. For posts published in the last 2 hours (freshness-critical), 3.2% false positive exclusion was significant — popular new posts were invisible to some users. Content creator trust dropped; several large accounts threatened to leave.

**Detection:** Creator analytics team noticed specific new posts had dramatically lower reach than expected despite high engagement rates on the posts that did surface. Post-hoc analysis showed Bloom filter capacity mismatch. Time-to-detect: 3 weeks.

**Recovery steps:**
1. Expanded Bloom filter to 50M items per user (target false positive rate 0.1% at that capacity).
2. Added Bloom filter health monitoring: compute actual false positive rate weekly by sampling 10,000 items from the "not-seen" history and checking if they are wrongly classified as seen.
3. For posts in the freshness shard (< 2 hours old), bypass the Bloom filter and use a Redis SET for exact deduplication instead.

**Prevention:** Bloom filter capacity alert: if current_item_count > 0.6 × design_capacity, trigger capacity planning review. Automatic resize on next deployment cycle.

---

### Failure 3: Multi-Objective Weight Misconfiguration During Weekend Deploy

**What failed:** A configuration change (via feature flag) updated the multi-objective weighting from (engage=0.4, dwell=0.3, quality=0.2, fresh=0.1) to (engage=0.5, dwell=0.4, quality=0.0, fresh=0.1) — the quality weight was accidentally set to 0 in a config file. The deploy happened on Friday at 6 PM. By Saturday morning, quality bait content dominated the feed: clickbait headlines, misleading thumbnails. Session length increased 8% (engagement optimization working) but report rate increased 45% and unfollow rate increased 12%. The signal that something was wrong came from creator feedback, not automated monitoring.

**Detection:** Quality monitoring dashboard: fraction of posts in top-10 feed with quality_score < 0.3 spiked from 5% to 38%. Alert was set to fire at > 15% but the metric was only computed hourly, so the alert fired 1 hour after deploy. Time-to-detect: 1 hour (automated), 12 hours (stakeholder awareness).

**Recovery steps:**
1. Rolled back config flag within 30 minutes of alert.
2. Wrote automated test that validates config weights sum to 1.0 and no individual weight is 0.0.
3. Added pre-deploy validation: shadow mode must show quality_score distribution within 10% of production before config flag deployment.

**Prevention:** Configuration changes to ranking weights require a shadow validation step (minimum 1 hour) before any live traffic is affected. Config values with quality_weight=0 are blocked by validation schema.

---

## Capacity Planning

### Data Volume Projections

```
Year 0 (current):
  Users: 500M registered, 100M DAU
  Posts: 10M new posts/day ingested for ranking
  Requests: 100K QPS × 86400 = 8.64B feed requests/day
  Per-request candidates: 2000 posts × 500 bytes feature vector = 1MB per request
  Feature log: 100K QPS × 100 posts ranked × 300 bytes = 3GB/sec → 259TB/day (sample 1%)
  Sampled event log: 2.59TB/day
  User engagement events (Kafka): 100M DAU × 20 events = 2B events/day = ~1TB/day

Year 1 (20% user growth):
  600M users, 120M DAU, 120K QPS
  Sampled feature log: 3.1TB/day
  Engagement events: 2.4B/day

Year 3 (2x growth + 2 new regions):
  1B users, 200M DAU, 200K QPS
  Kafka throughput for events: 4B events/day = 46K events/sec sustained
  Model training data (30-day): ~78TB Parquet
  User embedding store (Redis): 1B users × 256 dims × 4 bytes = 1TB Redis cluster
```

### Training Compute Requirements

```
MMOE Ranker (PyTorch, weekly retrain):
  Dataset: 500M (user, post) labeled examples (30-day rolling)
  Labels: engagement=Bernoulli, dwell=continuous, quality=categorical
  Hardware: 8× A100 40GB (p4d.24xlarge), 3 epochs
  Duration: 12 hours
  Cost: $32.77/hr × 12hr = $393/week = $1,703/month

Two-Tower Retrieval (bi-weekly retrain):
  Dataset: 2B positive (user, post) interaction pairs
  Hardware: 4× A100, 8 hours
  Cost: $32.77/hr × 8hr × 2 = $524/month

LightGBM Engagement Ranker (daily, for A/B experiments):
  Dataset: 50M examples/day (fast iteration model)
  Hardware: c5.4xlarge (16 vCPU)
  Duration: 2 hours
  Cost: $0.68/hr × 2hr × 30 = $41/month

Total monthly training cost: ~$2,268
```

### Serving Infrastructure

```
MMOE Inference (100K QPS × 800 candidates):
  Total inference calls: 100K × 800 = 80M model forward passes/sec
  MMOE model: 8 experts + 3 gates, ~2M parameters
  GPU throughput: 1 A10G processes 100K candidates/sec (batch 256)
  GPUs needed: 80M / 100K = 800 GPUs — clearly infeasible for 100K QPS

  In practice: NOT 80M full model passes.
  Stage-2 ranking is 800 candidates per user, batched together.
  100K user requests/sec × 800 candidates = 80M feature vectors assembled,
  but only 100K MMOE forward passes of batch-800.
  MMOE of batch 800: ~5ms on A10G.
  Each node handles 200 requests/sec → 100K / 200 = 500 GPU node-equivalents.
  Use 50× multi-GPU nodes (8× A10G per node): 50 × 8 = 400 A10Gs.
  Cost: 50× p4d.24xlarge equivalent: ~$1.64/hr × 50 = $82/hr = ~$59,040/month
  (In practice, use G5.48xlarge at $16.29/hr × 100 nodes = ~$1.17M/month — too expensive)
  Optimization: quantize MMOE to INT8 → 3x speedup, 20 nodes at $23,400/month.

Two-Tower ANN Retrieval:
  FAISS IVF+PQ over 10M posts, per-user query
  100K QPS × 8ms = 800K GPU-ms/sec per retrieval node
  FAISS CPU: 100 nodes (c5.2xlarge at $0.34/hr)
  Cost: 100 × $0.34 × 720 = ~$24,480/month

Redis User Embeddings:
  500M users × 256-dim × 4B = 500GB Redis cluster
  20-node Redis Cluster (r5.4xlarge 128GB RAM): ~$15,000/month

Total monthly serving infrastructure: ~$62,880 (before INT8 optimization)
Post-INT8 optimization: ~$40,000/month
```

---

## Additional War Stories

**War Story 1 — DPP Kernel Matrix Numerical Instability for Homogeneous Candidates:**

```python
# BROKEN: DPP kernel matrix becomes nearly singular when all candidates are very similar
# (e.g., user follows 50 accounts all in the same niche topic)
# Cholesky decomposition fails with "matrix is not positive definite" error

import numpy as np
from typing import Optional


def dpp_greedy_map_broken(
    embeddings: np.ndarray,    # (n_candidates, embed_dim) L2-normalized
    scores: np.ndarray,        # (n_candidates,) relevance scores
    k: int = 10,
) -> list[int]:
    """Broken: no regularization for near-singular kernel matrix."""
    # Kernel: L_ij = score_i * cosine_similarity(e_i, e_j) * score_j
    similarity = embeddings @ embeddings.T               # (n, n)
    L = np.outer(np.sqrt(scores), np.sqrt(scores)) * similarity  # (n, n)
    # BUG: if all embeddings are nearly identical, L is near-singular
    # np.linalg.cholesky(L) raises LinAlgError
    try:
        L_chol = np.linalg.cholesky(L)
    except np.linalg.LinAlgError:
        # Silent fallback to pure score ranking — loses diversity entirely
        return list(np.argsort(-scores)[:k])

    selected: list[int] = []
    remaining = list(range(len(scores)))
    for _ in range(k):
        gains = [L[i, i] - (L[selected, i] ** 2).sum() if selected else L[i, i]
                 for i in remaining]
        best = remaining[int(np.argmax(gains))]
        selected.append(best)
        remaining.remove(best)
    return selected


# FIX: Regularize the kernel matrix to ensure positive definiteness
# Add epsilon * I to the diagonal before Cholesky decomposition

def dpp_greedy_map_correct(
    embeddings: np.ndarray,
    scores: np.ndarray,
    k: int = 10,
    epsilon: float = 1e-4,  # regularization ensures positive definiteness
) -> list[int]:
    """
    DPP with kernel regularization.
    epsilon adds a small identity component, ensuring L is strictly positive definite
    even when all embeddings are nearly identical.
    epsilon=1e-4 is negligible for typical embeddings (cosine sim range [0, 1])
    but prevents numerical failures.
    """
    n = len(scores)
    similarity = embeddings @ embeddings.T  # (n, n)
    L = np.outer(np.sqrt(scores), np.sqrt(scores)) * similarity
    L += epsilon * np.eye(n)   # regularization

    selected: list[int] = []
    remaining = list(range(n))
    L_selected_cache: Optional[np.ndarray] = None  # cache selected rows/cols

    for _ in range(k):
        if not remaining:
            break
        if not selected:
            gains = np.array([L[i, i] for i in remaining])
        else:
            L_sel = L[np.ix_(selected, remaining)]  # (|selected|, |remaining|)
            gains = np.array([
                L[remaining[j], remaining[j]] - (L_sel[:, j] ** 2).sum()
                for j in range(len(remaining))
            ])
        gains = np.maximum(gains, 0)  # numerical safety: clip negative gains
        best_j = int(np.argmax(gains))
        best_i = remaining[best_j]
        selected.append(best_i)
        remaining.pop(best_j)

    return selected
```

**War Story 2 — Feature Store Race Condition Serving Stale User Embeddings:**

```python
# BROKEN: User embedding update and read racing without versioning
# New embedding write (background job) is partially complete when serving reads it
# Result: 128-dim vector read when 256-dim vector is mid-write → shape mismatch

import redis
import numpy as np
import json


class UserEmbeddingStoreBroken:
    def __init__(self, redis_client: redis.Redis) -> None:
        self.redis = redis_client

    def write_embedding(self, user_id: str, embedding: np.ndarray) -> None:
        key = f"user_emb:{user_id}"
        # BUG: Non-atomic multi-chunk write — reader can see partial state
        chunks = [embedding[i:i+64] for i in range(0, len(embedding), 64)]
        for i, chunk in enumerate(chunks):
            self.redis.hset(key, f"chunk_{i}", chunk.tobytes())
        # If reader reads after chunk_0 but before chunk_1 completes → wrong dim

    def read_embedding(self, user_id: str) -> np.ndarray:
        key = f"user_emb:{user_id}"
        chunks = self.redis.hgetall(key)
        if not chunks:
            return np.zeros(256, dtype=np.float32)
        data = b"".join(v for _, v in sorted(chunks.items()))
        return np.frombuffer(data, dtype=np.float32)  # may be wrong length!


# FIX: Atomic write using single Redis SET with versioned key
# Write new key, then atomically swap the pointer

class UserEmbeddingStoreCorrect:
    EMBED_DIM = 256

    def __init__(self, redis_client: redis.Redis) -> None:
        self.redis = redis_client

    def write_embedding(self, user_id: str, embedding: np.ndarray, version: int) -> None:
        """Atomic write: store under versioned key, then update pointer atomically."""
        assert embedding.shape == (self.EMBED_DIM,), f"Expected dim {self.EMBED_DIM}"
        versioned_key = f"user_emb:{user_id}:v{version}"
        pointer_key = f"user_emb:{user_id}:ptr"

        pipe = self.redis.pipeline()
        pipe.set(versioned_key, embedding.astype(np.float32).tobytes(), ex=172800)  # 48h TTL
        pipe.set(pointer_key, str(version), ex=172800)
        pipe.execute()  # both SET operations sent atomically

    def read_embedding(self, user_id: str) -> np.ndarray:
        pointer_key = f"user_emb:{user_id}:ptr"
        version_raw = self.redis.get(pointer_key)
        if version_raw is None:
            return np.zeros(self.EMBED_DIM, dtype=np.float32)

        versioned_key = f"user_emb:{user_id}:v{version_raw.decode()}"
        raw = self.redis.get(versioned_key)
        if raw is None:
            return np.zeros(self.EMBED_DIM, dtype=np.float32)

        arr = np.frombuffer(raw, dtype=np.float32)
        if arr.shape[0] != self.EMBED_DIM:
            return np.zeros(self.EMBED_DIM, dtype=np.float32)  # defensive
        return arr
```

---

## Monitoring and Drift Detection Deep-Dive

### Features That Drift Fastest

```
Feature                               Drift rate  Reason
───────────────────────────────────────────────────────────────────────────
post_engagement_density (likes/hr)    Very high   Viral posts spike; trending topics shift hourly
creator_follower_growth_rate          High        Platform growth; major events drive spikes
user_active_session_count_7d          High        School/work schedules; seasonality
content_topic_distribution            High        News cycles; trending topics change daily
post_age_hours                        High        Every request ages every post by request time
user_genre_preference (top-3)         Medium      Evolves over weeks with content exposure
two_tower_retrieval_score             Low         Model fixed between retrains
creator_avg_quality_score             Low         Changes slowly as creator builds track record
```

### PSI and Distribution Monitoring

```python
# Monitoring targets for content feed ranking
MONITORING_TARGETS = {
    "engagement_rate_by_position": {
        # Expected CTR curve: pos1=8%, pos2=5%, pos3=3%, pos10=1%
        # Alert if any position deviates >20% from expected ratio
        "alert_position_ratio_change": 0.20,
    },
    "creator_diversity_gini": {
        # Gini coefficient of creator share in top-20 recommendations
        # Target: < 0.4 (low inequality). Alert if > 0.5
        "alert_gini_upper": 0.5,
    },
    "new_creator_impression_share": {
        # Fraction of impressions going to creators with < 1000 followers
        # Must be >= 5% to prevent new creator starvation
        "alert_lower": 0.05,
    },
    "quality_score_distribution": {
        # PSI of ranked content quality score vs baseline
        "alert_psi": 0.15,
    },
    "dwell_time_median": {
        # Median dwell time per session (seconds)
        "alert_regression_threshold_pct": 0.05,  # alert if drops > 5%
    },
}
```

### Retraining Triggers and Cadence

```
Cadence        Trigger                                    Action
──────────────────────────────────────────────────────────────────────────────
Weekly         Scheduled                                   MMOE full retrain
Bi-weekly      Scheduled                                   Two-Tower retrieval retrain
Daily          Scheduled                                   LightGBM engagement model update
Triggered      Content quality PSI > 0.15                  Alert + MMOE quality head investigation
Triggered      New creator impression share < 5%           Increase new creator boost factor
Triggered      Creator Gini coefficient > 0.5              Tighten per-creator impression cap
Triggered      Any MMOE expert variance < 0.01             Expert collapse detected; retrain immediately
Triggered      Report rate increases > 10% (rolling 24h)  Quality emergency: increase quality weight
Monthly        Scheduled                                   Multi-objective weight Pareto analysis
                                                            with A/B test validation
Quarterly      Scheduled                                   Full bias audit (demographic fairness)
```

---

## Additional Interview Questions

**How does MMoE (Multi-gate Mixture of Experts) differ from a simple shared-bottom multi-task network?**
A shared-bottom network has one shared feature extractor (bottom MLP) that feeds all task-specific heads. All tasks use identical shared representations, which is problematic when tasks conflict: optimizing for engagement might learn representations that hurt dwell time prediction. MMoE introduces n expert networks (each a separate MLP) and k gating networks (one per task). Each task's gating network learns a softmax mixture over the experts, producing a task-specific weighted combination of expert outputs. This means different tasks can specialize to different experts: engagement may rely heavily on experts 1-3 (interaction pattern experts), while quality relies on experts 5-7 (content semantic experts). Expert specialization is learned, not hard-coded. The improvement over shared-bottom is typically 3-8% on secondary task metrics in production systems.

**How do you define and enforce content diversity in a feed ranking system?**
Diversity has multiple dimensions: (1) topic/content diversity — avoiding 10 consecutive posts about the same news event; (2) creator diversity — avoiding one creator monopolizing the feed; (3) format diversity — mix of video, images, text posts; (4) sentiment diversity — not all negative news. Enforcement mechanisms: (1) Hard constraints in re-ranking: max 3 posts from same creator, max 4 posts on same topic cluster (BERTopic assignment) in top-20. (2) DPP diversity: probabilistic subset selection that explicitly maximizes diversity via kernel matrix. (3) Weighted diversity score in multi-objective: diversity_score = 1 - top_creator_share × top_topic_concentration, added as 4th optimization objective. (4) Evaluation: Gini coefficient of creator share in served feeds; topic entropy (higher = more diverse); intra-list distance (average cosine distance between adjacent feed items).

**What is the exploration-exploitation tradeoff in feed ranking and how do you handle it?**
Exploitation: always show the highest-ranked content based on current model predictions — maximizes short-term engagement but starves new content and new creators who have insufficient data for accurate ranking. Exploration: occasionally show content that might not rank highest but provides learning signal. Mechanisms: (1) epsilon-greedy: 5% of slots randomly sample from top-500 candidates uniformly. (2) Thompson sampling: model CTR as a Beta distribution per (user, content) pair; sample from the distribution, giving high variance (new) content more exploration probability. (3) UCB (Upper Confidence Bound): boost score by C/sqrt(impressions), where C controls exploration intensity; new content with 0 impressions gets maximum exploration boost. In practice, epsilon-greedy is simplest and sufficient for feed ranking. Thompson sampling provides better sample efficiency but requires distributional modeling of each user-content pair's engagement probability.

**How do you prevent filter bubbles and echo chambers in content ranking?**
Filter bubbles form when users see only content confirming their existing beliefs, driven by optimization for engagement (confirmation bias correlates with high engagement). Four mechanisms: (1) Topic exposure quotas: if a user's last 50 served posts are >70% from one topic cluster, force 30% of the next feed from orthogonal topics, even at engagement cost. (2) Perspective diversity signals: for political/news content, use a political spectrum classifier to ensure the feed contains viewpoints from multiple perspectives (measured by Gini coefficient of political leaning). (3) Downweight engagement bait: content with high CTR but low dwell time (< 5 seconds) or high report rate gets quality score penalized — this disproportionately removes emotionally provocative content. (4) Long-term satisfaction monitoring: 30-day and 90-day retention, measured separately by user's initial topic concentration, to detect users who leave due to filter bubble fatigue.

**How would you design the system to handle a sudden surge to 300K QPS from a viral event?**
The system must auto-scale faster than the viral event ramps. Three mechanisms: (1) Predictive scaling: viral event detection (spike in post creation rate, external news API signals) triggers pre-scaling 5 minutes before traffic peaks. Scale from 100K to 300K QPS capacity requires adding 200% more serving infrastructure — this takes 3-5 minutes with pre-provisioned spare capacity pools (AWS auto-scaling groups with warm pools). (2) Graceful degradation: if scaling has not completed and P99 latency exceeds 120ms, degrade to a faster Stage-2 ranker — replace MMOE (80ms) with a simpler XGBoost ranker (15ms), accepting lower ranking quality. Engagement-only mode: if latency exceeds 150ms, skip re-ranking entirely and serve Stage-1 retrieval results directly with a popularity boost. (3) Feed caching: during viral events, cache the top-100 feed for user segments (similar users, similar location) for up to 60 seconds — most users in the same segment see identical trending content anyway. This reduces unique model calls by 40-60% during peak events.
