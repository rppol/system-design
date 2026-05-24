# Information Theory for ML

## 1. Concept Overview

Information theory, founded by Claude Shannon in 1948, provides the mathematical framework for quantifying information, uncertainty, and the cost of encoding messages. In machine learning, information theory concepts appear throughout: entropy measures the uncertainty in a label distribution, cross-entropy loss is the standard classification objective, KL divergence regularizes variational autoencoders and knowledge distillation, mutual information drives feature selection, and information gain is the splitting criterion in decision tree learning.

The core insight of information theory is that the more uncertain an outcome is, the more information its occurrence conveys. A coin flip (50/50) conveys 1 bit of information; a fair die provides log2(6) ≈ 2.58 bits; a certain outcome provides 0 bits (you already knew it would happen).

---

## 2. Intuition

> **One-line analogy**: Entropy is the average surprise of a probability distribution — a distribution that is always surprising (uniform) has maximum entropy; one that is never surprising (deterministic) has zero entropy.

**Mental model**: Imagine you are guessing the next word in a sentence. After "The cat sat on the ___", you are not very uncertain — "mat", "floor", "chair" are likely. After "I like ___", you are very uncertain — the next word could be almost anything. The latter context has higher conditional entropy H(next_word | context). Language models try to reduce this entropy by conditioning on longer, richer contexts.

**Why it matters**: Cross-entropy loss is not just a convenient differentiable loss — it is the information-theoretic optimal encoding cost when your predicted distribution is q and the true distribution is p. Minimizing it pushes q toward p. KL divergence measures the inefficiency of using q instead of p. Understanding these connections prevents common mistakes like using MSE for classification or misinterpreting model confidence scores.

**Key insight**: H(p, q) = H(p) + KL(p || q). Cross-entropy decomposes into the irreducible entropy of the true distribution (H(p)) plus the KL divergence penalty for using the wrong distribution (KL(p||q)). Since H(p) is fixed by the data, minimizing cross-entropy = minimizing KL divergence = making q as close to p as possible.

---

## 3. Core Principles

- **Self-information**: I(x) = -log p(x); the information content of a single outcome; rare events carry more information.
- **Entropy**: H(X) = -sum_x p(x) log p(x) = E[-log p(X)]; expected information content; measures uncertainty of a distribution.
- **Joint entropy**: H(X,Y) = -sum_{x,y} p(x,y) log p(x,y); uncertainty in two variables jointly.
- **Conditional entropy**: H(Y|X) = H(X,Y) - H(X); remaining uncertainty in Y after observing X.
- **Cross-entropy**: H(p,q) = -sum_x p(x) log q(x); cost of encoding samples from p using code optimized for q.
- **KL divergence**: KL(p||q) = sum_x p(x) log(p(x)/q(x)); always >= 0 (Gibbs inequality); not symmetric.
- **Mutual information**: I(X;Y) = H(X) - H(X|Y) = KL(p(x,y) || p(x)p(y)); shared information; 0 for independent variables.
- **Information gain**: IG = H(parent) - [weighted sum of H(children)]; used as split criterion in decision trees.
- **Log base convention**: log_2 gives bits; ln gives nats; log_10 gives hartleys. ML almost always uses ln (natural log) for gradient compatibility.

---

## 4. Types / Architectures / Strategies

### 4.1 Divergence Measures

| Measure | Formula | Symmetric? | Zero iff | ML Use |
|---------|---------|-----------|---------|--------|
| KL(p||q) | sum p log(p/q) | No | p = q | VAE, knowledge distillation (teacher to student) |
| KL(q||p) | sum q log(q/p) | No | p = q | Variational inference (minimize over q) |
| JS divergence | (KL(p||m) + KL(q||m))/2, m=(p+q)/2 | Yes | p = q | GAN training (original) |
| Total variation | (1/2) sum |p-q| | Yes | p = q | Theoretical analysis |
| Wasserstein | Earth Mover's Distance | Yes | p = q | WGAN; robust to support mismatch |

### 4.2 Entropy in Decision Trees

**Entropy criterion (ID3, C4.5)**: Split on the feature that maximizes information gain = H(Y) - H(Y | feature). Prefers balanced splits.

**Gini impurity (CART)**: G = 1 - sum p_i^2. Faster to compute (no logarithm). Empirically very similar results to entropy. sklearn's `DecisionTreeClassifier` uses Gini by default.

**For k classes**: Entropy is maximized (= log_2(k)) when all classes are equally probable. Gini is maximized (= 1 - 1/k) at the same point. Both are minimized (= 0) for pure nodes.

### 4.3 Information Theory in Neural Networks

**Cross-entropy loss**: standard for classification; connects to MLE under categorical distribution.

**KL divergence in VAEs**: ELBO = E[log p(x|z)] - KL(q(z|x) || p(z)); KL term penalizes divergence of learned posterior from Gaussian prior, encouraging disentanglement and preventing posterior collapse.

**Mutual information maximization**: InfoNCE loss (used in contrastive learning, SimCLR, CLIP) maximizes a lower bound on mutual information between views of the same data.

**Minimum Description Length (MDL)**: model selection principle — prefer the model that most compresses the data; connected to Kolmogorov complexity and Bayesian evidence.

---

## 5. Architecture Diagrams

### Entropy, Cross-Entropy, and KL Decomposition

```
True distribution p        Predicted distribution q
(from data labels)         (from model softmax)

         p                         q
    [0.7, 0.2, 0.1]          [0.6, 0.3, 0.1]

Entropy H(p):              Cross-Entropy H(p,q):
  = -(0.7*log(0.7)           = -(0.7*log(0.6)
     + 0.2*log(0.2)              + 0.2*log(0.3)
     + 0.1*log(0.1))             + 0.1*log(0.1))
  = 0.802 nats               = 0.897 nats

KL(p||q) = H(p,q) - H(p) = 0.897 - 0.802 = 0.095 nats
  (extra bits wasted by using q instead of p)

Training minimizes H(p,q); since H(p) is constant, this minimizes KL(p||q).
```

### Information Gain in Decision Tree

```
Root node:  60 positive, 40 negative  (100 total)
H(root) = -(0.6*log(0.6) + 0.4*log(0.4)) = 0.971 bits

Split on Feature A:
  Left:  50 positive, 5 negative   -> H = -(50/55*log(50/55) + 5/55*log(5/55)) = 0.439 bits
  Right: 10 positive, 35 negative  -> H = -(10/45*log(10/45) + 35/45*log(35/45)) = 0.781 bits

H(Y|Feature A) = 55/100 * 0.439 + 45/100 * 0.781 = 0.593 bits
IG(A) = H(root) - H(Y|A) = 0.971 - 0.593 = 0.378 bits

Split on Feature B:
  Left:  30 positive, 20 negative  -> H = 0.971 bits (no purity)
  Right: 30 positive, 20 negative  -> H = 0.971 bits
IG(B) = 0.971 - 0.971 = 0 bits  (useless split)

Choose Feature A (higher IG).
```

### KL Divergence Direction Matters

```
p = true distribution (narrow, concentrated)
q = approximate distribution (wider, diffuse)

KL(p||q): forward KL            KL(q||p): reverse KL
  minimized by q that            minimized by q that
  covers where p has mass        fits under p's mode
  -> q is diffuse (mean-seeking)  -> q is narrow (mode-seeking)

            p     q                  q    p
      .    |||  .....            .  ...  |||  .
    ..    |||||.......           ..  ... ||||  ..
   ....  |||||||.......          ....  .....||||  ..

Used in VAEs (forward KL       Used in VI (reverse KL)
via encoder for KL term)       avoids covering low-density regions
```

---

## 6. How It Works — Detailed Mechanics

### Entropy and Cross-Entropy Computation

```python
import numpy as np
from typing import Optional


def entropy(
    probs: np.ndarray,
    base: str = "nats"
) -> float:
    """
    Shannon entropy H(p) = -sum p(x) * log p(x).

    Args:
        probs: probability distribution, must sum to 1
        base: "bits" (log2), "nats" (ln), "hartleys" (log10)

    Returns:
        entropy value in the specified base
    """
    # Clip to avoid log(0) = -inf; 0 * log(0) = 0 by convention
    probs = np.clip(probs, 1e-12, 1.0)
    probs = probs / probs.sum()   # renormalize in case of floating point drift

    log_fn = np.log2 if base == "bits" else (np.log10 if base == "hartleys" else np.log)
    return float(-np.sum(probs * log_fn(probs)))


def cross_entropy(
    p_true: np.ndarray,
    q_pred: np.ndarray
) -> float:
    """
    Cross-entropy H(p, q) = -sum p(x) * log q(x).

    This is the cross-entropy loss in classification:
      p = one-hot label distribution [0, ..., 1, ..., 0]
      q = model softmax output

    For one-hot p with true class c: H(p,q) = -log q(c)
    (cross-entropy reduces to negative log probability of correct class)
    """
    q_pred = np.clip(q_pred, 1e-12, 1.0)
    return float(-np.sum(p_true * np.log(q_pred)))


def kl_divergence(
    p: np.ndarray,
    q: np.ndarray,
    eps: float = 1e-12
) -> float:
    """
    KL(p || q) = sum p(x) * log(p(x) / q(x)).

    Properties:
    - Always >= 0 (Gibbs inequality)
    - = 0 iff p == q
    - Asymmetric: KL(p||q) != KL(q||p) in general
    - Undefined (inf) if q(x) = 0 but p(x) > 0

    In ML: KL divergence is the "extra cost" of using q when the true dist is p.
    """
    p = np.clip(p, eps, 1.0)
    q = np.clip(q, eps, 1.0)
    # Only sum over indices where p > 0 (p=0 contributes 0 by convention)
    mask = p > eps
    return float(np.sum(p[mask] * np.log(p[mask] / q[mask])))


def mutual_information_from_joint(
    joint_probs: np.ndarray   # (|X|, |Y|) joint probability table
) -> float:
    """
    Mutual information I(X;Y) = H(X) + H(Y) - H(X,Y)
                               = KL(p(x,y) || p(x)*p(y))

    For discrete variables. For continuous variables, use
    k-NN estimators (sklearn.feature_selection.mutual_info_classif).
    """
    p_xy = joint_probs / joint_probs.sum()   # normalize
    p_x = p_xy.sum(axis=1)                    # marginal over Y
    p_y = p_xy.sum(axis=0)                    # marginal over X

    h_x = entropy(p_x)
    h_y = entropy(p_y)
    h_xy = entropy(p_xy.flatten())

    return float(h_x + h_y - h_xy)


def information_gain_split(
    y_parent: np.ndarray,       # class labels before split
    y_left: np.ndarray,         # class labels in left child
    y_right: np.ndarray         # class labels in right child
) -> float:
    """
    Information gain = H(parent) - weighted_avg(H(children)).
    Used in ID3 and C4.5 decision tree algorithms.
    """
    def node_entropy(y: np.ndarray) -> float:
        classes, counts = np.unique(y, return_counts=True)
        probs = counts / counts.sum()
        return entropy(probs, base="bits")

    h_parent = node_entropy(y_parent)
    n = len(y_parent)
    n_left, n_right = len(y_left), len(y_right)

    h_weighted_children = (
        (n_left / n) * node_entropy(y_left) +
        (n_right / n) * node_entropy(y_right)
    )

    return float(h_parent - h_weighted_children)


def gini_impurity(y: np.ndarray) -> float:
    """
    Gini impurity G = 1 - sum p_i^2.
    Used in CART (sklearn DecisionTreeClassifier default).
    Faster than entropy (no log); empirically similar splits.
    For binary classification: G_max = 0.5 at p=0.5; H_max = 1.0 bit at p=0.5.
    """
    classes, counts = np.unique(y, return_counts=True)
    probs = counts / counts.sum()
    return float(1.0 - np.sum(probs ** 2))
```

### Cross-Entropy Loss for Classification

```python
def cross_entropy_loss_batch(
    logits: np.ndarray,   # (batch_size, n_classes) raw pre-softmax scores
    labels: np.ndarray    # (batch_size,) integer class indices
) -> tuple[float, np.ndarray]:
    """
    Numerically stable cross-entropy loss with softmax.
    Returns (scalar_loss, gradient_of_logits).

    Numerical stability: subtract max(logits) before exp to prevent overflow.
    log-sum-exp trick: log(sum(exp(z))) = max(z) + log(sum(exp(z - max(z))))
    """
    batch_size = len(labels)

    # Numerically stable softmax
    # Subtract row-wise max before exp (does not change output of softmax)
    shifted = logits - logits.max(axis=1, keepdims=True)
    exp_z = np.exp(shifted)
    softmax_probs = exp_z / exp_z.sum(axis=1, keepdims=True)

    # Cross-entropy: -log(predicted prob of true class) for each sample
    true_class_probs = softmax_probs[np.arange(batch_size), labels]
    loss = -np.mean(np.log(true_class_probs + 1e-12))

    # Gradient: (softmax_probs - one_hot_labels) / batch_size
    # Derivation: d(CE)/d(logit_k) = softmax_k - 1[k==true_class]
    grad = softmax_probs.copy()
    grad[np.arange(batch_size), labels] -= 1.0
    grad /= batch_size

    return float(loss), grad


def kl_divergence_vae_loss(
    mu: np.ndarray,      # (batch, latent_dim) mean of q(z|x)
    log_var: np.ndarray  # (batch, latent_dim) log variance of q(z|x)
) -> float:
    """
    Analytical KL divergence for VAE: KL(N(mu, sigma^2) || N(0, 1)).
    Closed form: KL = -0.5 * sum(1 + log_var - mu^2 - exp(log_var))

    Derivation: for diagonal Gaussians, KL decomposes across latent dimensions.
    This is the regularization term in the ELBO that encourages the posterior
    q(z|x) = N(mu(x), sigma^2(x)) to stay close to the prior p(z) = N(0,I).
    """
    # Sum over latent dimensions, mean over batch
    kl_per_sample = -0.5 * np.sum(
        1 + log_var - mu ** 2 - np.exp(log_var),
        axis=1
    )
    return float(kl_per_sample.mean())
```

### Mutual Information for Feature Selection

```python
def select_features_by_mutual_information(
    X: np.ndarray,        # (n_samples, n_features) feature matrix
    y: np.ndarray,        # (n_samples,) class labels
    top_k: int = 10
) -> list[int]:
    """
    Select top-k features by mutual information with the target label.
    Uses scikit-learn's mutual_info_classif internally (k-NN based estimator
    for continuous features; handles both discrete and continuous X).

    Higher MI = feature carries more information about the class.
    MI = 0 = feature is independent of class (useless).
    """
    from sklearn.feature_selection import mutual_info_classif

    mi_scores = mutual_info_classif(X, y, random_state=42)
    # Sort in descending order and return top-k indices
    sorted_indices = np.argsort(mi_scores)[::-1]
    return list(sorted_indices[:top_k])
```

---

## 7. Real-World Examples

**Cross-entropy in LLM training**: GPT-style language models use cross-entropy loss at every position: the model predicts a probability distribution over the vocabulary (50,000 tokens); the loss is -log P(true_next_token). For a 50k vocabulary, random predictions give loss = log(50000) ≈ 10.8. A well-trained GPT-3 achieves perplexity ~20 (cross-entropy loss ≈ 3.0 nats) on English text, meaning it is as uncertain as choosing uniformly among 20 plausible next tokens.

**KL divergence in VAEs**: A variational autoencoder encodes an image x into a posterior distribution q(z|x) = N(mu(x), sigma^2(x)). The ELBO loss has two terms: reconstruction loss (cross-entropy or MSE) and KL(q(z|x) || N(0,I)). The KL term is analytically computable for Gaussians: -0.5 * sum(1 + log_sigma^2 - mu^2 - sigma^2). If the KL weight is too high, the model ignores the encoder (posterior collapse — KL = 0 trivially when sigma=1, mu=0 everywhere). Beta-VAE uses a multiplier beta > 1 on the KL term to encourage more disentangled representations at the cost of reconstruction quality.

**Information gain in random forests**: sklearn's `DecisionTreeClassifier` with `criterion='entropy'` uses information gain at each split. For the Iris dataset (3 balanced classes), the root entropy is log_2(3) = 1.585 bits. The best first split on petal_length reduces entropy to ~0.45 bits for IG ≈ 1.14 bits — the single most informative feature.

**Mutual information in CLIP**: The InfoNCE loss used in CLIP training maximizes a lower bound on mutual information I(image; text) for matching pairs while minimizing it for non-matching pairs. For a batch of N pairs, the loss for an image embedding i is -log(exp(sim(i, t_i)/tau) / sum_j exp(sim(i, t_j)/tau)) — exactly a cross-entropy over N "classes" where the correct class is the matching text.

---

## 8. Tradeoffs

| Loss Function | Distribution Assumption | For Regression | For Classification |
|-------------|------------------------|---------------|-------------------|
| MSE | Gaussian noise N(0, sigma^2) | Natural choice | Poor; penalizes calibration incorrectly |
| MAE | Laplace noise | Robust to outliers | Rarely used |
| Cross-entropy | Categorical/Bernoulli | Not used | Standard; connects to MLE |
| Focal loss | Modified categorical | Not used | For class imbalance (RetinaNet) |
| Huber loss | Gaussian + Laplace mixture | Between MSE/MAE | Not used |

| Split Criterion | Formula | Speed | Result Quality |
|----------------|---------|-------|---------------|
| Gini impurity | 1 - sum p_i^2 | Faster (no log) | Near-identical to entropy |
| Entropy (IG) | -sum p_i log p_i | Slightly slower | Slightly better on multi-class |
| Variance reduction | Var(parent) - weighted Var(children) | Fast | For regression trees |

---

## 9. When to Use / When NOT to Use

**Use cross-entropy loss when**: output is a probability distribution (classification, language modeling, image generation with categorical distribution). It is the information-theoretically correct loss — minimizing it minimizes the KL divergence between model and true distributions.

**Do NOT use MSE for classification**: MSE implicitly assumes Gaussian noise, which is wrong for probabilities bounded in [0,1]. MSE gradients for classification are zero when the model output is near 0 or 1 even if wrong, while cross-entropy gradient remains strong (because -log(q) -> inf as q -> 0). MSE-trained classifiers are poorly calibrated and slower to learn.

**Use KL(p||q) (forward KL) when**: you want your approximate distribution q to cover all regions where the true p is nonzero — it is mean-seeking. Used in VAE encoder via the analytical ELBO.

**Use KL(q||p) (reverse KL) when**: you want q to fit within the support of p — it is mode-seeking. Used in variational inference (VI) where you minimize KL from approximation to posterior; q tends to be narrow and can miss modes of p.

**Use mutual information for feature selection when**: you have many features and want to rank them by informativeness without assuming linear relationships. MI captures nonlinear dependencies that correlation-based selection misses.

**Do NOT use entropy splitting criterion exclusively in decision trees**: it is slightly biased toward features with many values (high cardinality). C4.5 corrects this with the gain ratio = IG / H(feature). Alternatively, use Gini which does not have this bias.

---

## 10. Common Pitfalls

**Pitfall 1 — Numeric instability in cross-entropy**: Computing `log(softmax(logits))` directly is numerically unstable — softmax first computes `exp(logits)` which overflows for logits > 88 (float32). The fix is log-sum-exp: compute `log_softmax(logits) = logits - logsumexp(logits)` in one pass. PyTorch's `nn.CrossEntropyLoss` does this correctly with `log_softmax` internally; writing your own cross-entropy without this trick will produce NaN for large logits.

```python
# Broken: numerical overflow for large logits
def broken_cross_entropy(logits: np.ndarray, labels: np.ndarray) -> float:
    probs = np.exp(logits) / np.exp(logits).sum(axis=1, keepdims=True)  # overflow
    return float(-np.mean(np.log(probs[np.arange(len(labels)), labels])))

# Fixed: numerically stable via log-sum-exp trick
def stable_cross_entropy(logits: np.ndarray, labels: np.ndarray) -> float:
    shifted = logits - logits.max(axis=1, keepdims=True)  # subtract max
    log_sum_exp = np.log(np.exp(shifted).sum(axis=1, keepdims=True))
    log_probs = shifted - log_sum_exp
    return float(-np.mean(log_probs[np.arange(len(labels)), labels]))
```

**Pitfall 2 — Posterior collapse in VAEs (KL vanishing)**: A team trained a VAE on text generation. The ELBO loss included the KL term and reconstruction cross-entropy. The KL term collapsed to nearly 0 within 100 steps — the encoder learned to output N(0,I) regardless of input, and the decoder ignored z entirely, turning the VAE into a regular autoregressive language model. Fixes: KL annealing (gradually increase KL weight from 0 to 1 over training), KL thresholding (do not penalize KL below a free-bits threshold of 0.5), or using a less powerful decoder.

**Pitfall 3 — Using cross-entropy with soft labels but wrong normalization**: A knowledge distillation pipeline computed the KL divergence between teacher and student soft outputs as `F.cross_entropy(student_logits, teacher_probs)`. But PyTorch's `F.cross_entropy` expects integer hard labels as the second argument, not probability vectors. For soft labels, use `F.kl_div(F.log_softmax(student, dim=-1), teacher_probs, reduction='batchmean')`. The bug produced a cross-entropy where the teacher's probability vector was interpreted as a float-encoded integer index, giving completely wrong gradients.

**Pitfall 4 — Entropy of continuous distributions without binning**: A team estimated the entropy of a continuous feature by computing `entropy(np.unique(feature))` — treating each unique float value as an atom of a discrete distribution. Since floats are nearly all unique, this returned log(n_samples) regardless of the actual distribution shape. For continuous features, either bin into quantiles first or use the differential entropy estimator (`scipy.stats.entropy` on a KDE histogram). The mutual information scores used for feature selection were all nearly equal, defeating the purpose of the selection step.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| NumPy | Manual entropy, KL, cross-entropy computation |
| SciPy stats | `entropy(p, q)` for KL divergence; `differential_entropy` (SciPy 1.6+) |
| scikit-learn | `mutual_info_classif`, `mutual_info_regression`; entropy split criterion in DecisionTree |
| PyTorch | `F.cross_entropy`, `F.kl_div`, `F.binary_cross_entropy_with_logits` (numerically stable) |
| TensorFlow/Keras | `tf.keras.losses.CategoricalCrossentropy`, `tf.keras.losses.KLDivergence` |
| dit (Python) | Information-theoretic quantities for discrete distributions |
| drv (discrete rv) | Mutual information, entropy rate for Markov chains |

---

## 12. Interview Questions with Answers

**Q: Why is cross-entropy the standard loss for classification rather than MSE?**
Cross-entropy is the negative log-likelihood under a categorical distribution, making it the MLE objective for classification. MSE is the MLE objective for Gaussian regression — it assumes the output is a continuous value with Gaussian noise, not a probability. Cross-entropy has a stronger gradient signal for misclassified examples (gradient = -(1-p) for a confident wrong prediction), while MSE gradient = 2*(p-1) saturates similarly. More practically, MSE does not penalize overconfidence in the wrong direction as strongly as cross-entropy, leading to poorly calibrated models.

**Q: What is the relationship between cross-entropy, KL divergence, and entropy?**
H(p, q) = H(p) + KL(p || q). Cross-entropy is the sum of the irreducible entropy H(p) (the best possible loss given the true label distribution) plus the KL divergence between the true and predicted distributions. Since H(p) is fixed by the data, minimizing cross-entropy is exactly minimizing KL(p || q) — pushing the model distribution q as close to p as possible. This is why cross-entropy is theoretically grounded: it is equivalent to minimizing the information-theoretic distance between model and reality.

**Q: Why is KL divergence asymmetric, and which direction should you use in ML?**
KL(p||q) = sum p log(p/q) is not equal to KL(q||p) because the two average over different distributions. KL(p||q) is large when p is large and q is small — it forces q to cover all high-probability regions of p (mean-seeking, diffuse q). KL(q||p) is large when q is large and p is small — it forces q to stay within the support of p (mode-seeking, narrow q). In VAEs, the encoder is trained with the analytical KL(q(z|x) || p(z)) which is the forward KL from the approximate posterior to the prior. In variational inference (ELBO), you also minimize forward KL in expectation.

**Q: What is entropy and what is its maximum value for a k-class distribution?**
Entropy H(X) = -sum p(x) log p(x) measures the average uncertainty (surprise) of a distribution. It is maximized when all outcomes are equally probable: H_max = log(k) nats = log_2(k) bits (for a k-class uniform distribution). It is minimized at 0 when one outcome has probability 1. For binary classification with p=0.5, H = log(2) = 1 bit = 0.693 nats. For MNIST (10 classes uniform): H_max = log_2(10) = 3.32 bits.

**Q: How is information gain used in decision trees, and what is its limitation?**
Information gain IG(feature) = H(parent) - weighted_average(H(children)) measures the reduction in label entropy achieved by splitting on a feature. The tree greedily selects the feature with highest IG at each node. Limitation: IG is biased toward high-cardinality features. A feature with a unique value per sample (like a user ID) gives perfect IG (completely pure leaves) but is useless for generalization. C4.5 corrects this with gain ratio = IG / H(feature), penalizing features with many values. CART uses Gini impurity which is less biased.

**Q: What is mutual information and how does it differ from correlation?**
Mutual information I(X;Y) = H(X) - H(X|Y) = H(Y) - H(Y|X) measures the total information X and Y share, including nonlinear dependencies. Pearson correlation measures only linear dependence and is zero for many nonlinear relationships (e.g., Y = X^2 with symmetric X has correlation 0 but high MI). MI is always >= 0 (0 iff X and Y are independent) and is symmetric. The main limitation is that estimating MI for continuous variables is hard; parametric or k-NN estimators are needed.

**Q: Explain the KL divergence term in the VAE ELBO.**
The VAE ELBO is E_{q(z|x)}[log p(x|z)] - KL(q(z|x) || p(z)). The first term maximizes reconstruction quality (how well the decoder reconstructs x from sampled z). The second term (negative KL) penalizes the posterior q(z|x) = N(mu(x), sigma^2(x)) for diverging from the prior p(z) = N(0, I). This KL term regularizes the latent space: it pushes the encoder to produce latent codes that are approximately standard Gaussian, ensuring the latent space is smooth and samples from the prior produce valid outputs. For diagonal Gaussians the KL is analytical: -0.5 * sum(1 + log_sigma^2 - mu^2 - sigma^2).

**Q: Why does minimizing cross-entropy give a well-calibrated model?**
Cross-entropy loss gradient for the correct class is -(1 - p_correct) — it penalizes under-confidence (driving p_correct toward 1). For the wrong classes, gradient is p_wrong — penalizes any probability mass on wrong classes. This symmetric pressure produces models where predicted probabilities reflect true likelihoods. In contrast, hinge loss (SVMs) does not penalize predictions beyond the margin, producing uncalibrated scores. Calibration can be further improved with temperature scaling: divide logits by T before softmax; T > 1 softens predictions (more uniform); T < 1 sharpens them.

**Q: What is the connection between information theory and compression?**
Shannon's source coding theorem states that the minimum expected code length for symbols from distribution p is H(p) bits. If you design an optimal code for distribution q but the true distribution is p, the expected code length is H(p, q) = H(p) + KL(p||q) bits — the extra KL(p||q) bits are wasted due to the mismatch. This is why KL divergence is also called "relative entropy." Cross-entropy loss in ML has the same interpretation: the model's predicted distribution q is a code for the true labels p; training minimizes the expected coding inefficiency.

**Q: How does focal loss relate to cross-entropy and when should you use it?**
Focal loss = -(1 - p_t)^gamma * log(p_t) where p_t is the predicted probability of the true class. When gamma=0, focal loss is standard cross-entropy. For correctly classified easy examples (p_t close to 1), the factor (1-p_t)^gamma is near 0, down-weighting their contribution to the loss. This focuses training on hard misclassified examples. Use focal loss when the training set is heavily class-imbalanced (many easy negatives) — as in object detection where background anchors vastly outnumber foreground objects. RetinaNet introduced it to match two-stage detectors by solving the foreground-background class imbalance problem.

**Q: What does perplexity measure in language models?**
Perplexity = exp(H) where H is the cross-entropy loss in nats, or equivalently 2^H in bits. It measures how many tokens the model is effectively choosing among at each position. A perplexity of 20 means the model is as uncertain as choosing uniformly among 20 tokens. Lower perplexity = better language model. GPT-2 (large) achieves perplexity ~18 on PTB; GPT-3 achieves ~9 on Penn Treebank. Perplexity is only comparable across models with the same tokenization and vocabulary — comparing perplexity across different tokenizers requires normalization by number of characters or words.

---

## 13. Best Practices

- Always use numerically stable implementations: `F.cross_entropy` (PyTorch) or `log_softmax` + `nll_loss` instead of `softmax` + `log` + sum.
- Clip predicted probabilities to [1e-7, 1 - 1e-7] before computing log to avoid log(0) = -inf.
- When computing KL divergence, handle zero probabilities carefully: KL is defined as 0 * log(0) = 0 by convention; numpy 0 * log(0) returns nan, not 0.
- Use `scipy.special.entr(p)` for numerically stable p * log(1/p) that handles 0 correctly.
- For feature selection with continuous features, use `mutual_info_classif` (k-NN based) from scikit-learn rather than binning manually.
- Anneal the KL weight in VAEs from 0 to 1 over the first 10,000 steps to prevent posterior collapse.
- Monitor the KL divergence term in VAE training separately from reconstruction loss; a sudden drop to near-0 indicates posterior collapse.
- For imbalanced classification, prefer focal loss or class-weighted cross-entropy over standard cross-entropy; log the per-class cross-entropy to see which classes are poorly learned.
- Report perplexity only when tokenization is fixed; to compare models across tokenizers, convert to bits-per-character.

---

## 14. Case Study

**Scenario:** A cloud ML platform (serving 400 production models across 200 enterprise clients) needs automated model distribution shift monitoring. The current approach of tracking accuracy on labelled ground-truth data has a 72-hour lag (labels collected via human review). The goal: implement KL divergence-based monitoring on unlabelled prediction distributions, detecting shift within 5 minutes with false positive rate <= 2% and false negative rate <= 8% for shifts exceeding 0.05 in KL divergence, serving 1,200 model scoring events per second with monitoring overhead < 3ms per event.

**Architecture:**
```
Production Model Serving (1200 scoring events/s)
  Model output: probability distribution over classes
  (e.g., fraud: [p_fraud, p_legitimate], credit: 10 score buckets)
         |
         v
Distribution Aggregator (5-minute tumbling windows)
  Collect prediction distributions over 5-min window
  Compute: empirical probability mass per output bucket
  Store: reference distribution (7-day rolling baseline)
         |
         v
KL Divergence Monitor (every 5 minutes)
  KL(P_current || P_reference) for each model
  Two-sided Jensen-Shannon divergence for symmetric alerting
  Chi-squared test for statistical significance of divergence
         |
         v
Alerting Engine
  KL > 0.05 AND chi2_p < 0.01 -> WARNING alert
  KL > 0.15 AND chi2_p < 0.001 -> CRITICAL alert + auto-retraining trigger
         |
         v
Dashboard (Grafana)
  Per-model KL time series, top-K drifting models, input feature PSI
```

**Step-by-step implementation:**

```python
from __future__ import annotations
import numpy as np
from scipy.stats import chi2_contingency, entropy as scipy_entropy
from scipy.special import rel_entr
from dataclasses import dataclass

@dataclass
class DistributionSnapshot:
    model_id: str
    window_start: float   # Unix timestamp
    window_end: float
    empirical_counts: np.ndarray   # raw count per bucket (not normalised)
    n_samples: int

def compute_kl_divergence(
    p_current: np.ndarray,
    p_reference: np.ndarray,
    epsilon: float = 1e-10,
) -> float:
    """KL(P_current || P_reference) = sum(P * log(P/Q)).
    
    KL is asymmetric: measures how P_current differs from P_reference.
    Returns float; undefined if Q[i]=0 and P[i]>0 (handled by epsilon).
    """
    p = p_current / p_current.sum() + epsilon
    q = p_reference / p_reference.sum() + epsilon
    p = p / p.sum()   # renormalise after epsilon addition
    q = q / q.sum()
    return float(np.sum(rel_entr(p, q)))   # rel_entr handles 0*log(0)=0

def compute_js_divergence(
    p_current: np.ndarray,
    p_reference: np.ndarray,
    epsilon: float = 1e-10,
) -> float:
    """Jensen-Shannon divergence: symmetric, bounded [0, log(2)] in nats.
    
    JSD = 0.5*KL(P||M) + 0.5*KL(Q||M) where M = (P+Q)/2
    JSD = 0 iff P = Q; JSD = log(2) iff P and Q have disjoint support.
    """
    p = p_current / p_current.sum() + epsilon
    q = p_reference / p_reference.sum() + epsilon
    p = p / p.sum()
    q = q / q.sum()
    m = 0.5 * (p + q)
    return float(0.5 * np.sum(rel_entr(p, m)) + 0.5 * np.sum(rel_entr(q, m)))

def compute_entropy(
    distribution: np.ndarray,
    epsilon: float = 1e-10,
) -> float:
    """Shannon entropy H(P) = -sum(P * log(P)) in nats."""
    p = distribution / distribution.sum() + epsilon
    p = p / p.sum()
    return float(-np.sum(p * np.log(p)))
```

```python
from scipy.stats import chi2 as chi2_dist
import warnings

def chi2_drift_test(
    current_counts: np.ndarray,   # raw counts, not normalised
    reference_counts: np.ndarray,
) -> tuple[float, float]:
    """Chi-squared goodness-of-fit test for distributional drift.
    
    H0: current distribution matches reference distribution.
    Returns (chi2_statistic, p_value). Low p-value -> reject H0 -> drift detected.
    """
    # Scale reference to match current sample size
    n_current = current_counts.sum()
    n_reference = reference_counts.sum()
    expected = reference_counts * (n_current / n_reference)

    # Suppress warning for zero expected counts (handled by merging sparse bins)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        chi2_stat, p_value, dof, _ = chi2_contingency(
            np.vstack([current_counts, expected.round()])
        )
    return float(chi2_stat), float(p_value)

def detect_drift(
    snapshot_current: DistributionSnapshot,
    snapshot_reference: DistributionSnapshot,
    kl_warning_threshold: float = 0.05,
    kl_critical_threshold: float = 0.15,
    chi2_p_warning: float = 0.01,
    chi2_p_critical: float = 0.001,
) -> dict[str, str | float | bool]:
    kl = compute_kl_divergence(
        snapshot_current.empirical_counts.astype(float),
        snapshot_reference.empirical_counts.astype(float),
    )
    jsd = compute_js_divergence(
        snapshot_current.empirical_counts.astype(float),
        snapshot_reference.empirical_counts.astype(float),
    )
    chi2_stat, chi2_p = chi2_drift_test(
        snapshot_current.empirical_counts,
        snapshot_reference.empirical_counts,
    )

    if kl >= kl_critical_threshold and chi2_p <= chi2_p_critical:
        severity = "CRITICAL"
    elif kl >= kl_warning_threshold and chi2_p <= chi2_p_warning:
        severity = "WARNING"
    else:
        severity = "OK"

    return {
        "model_id": snapshot_current.model_id,
        "kl_divergence": kl,
        "js_divergence": jsd,
        "chi2_statistic": chi2_stat,
        "chi2_p_value": chi2_p,
        "severity": severity,
        "alert": severity != "OK",
        "n_current": snapshot_current.n_samples,
        "n_reference": snapshot_reference.n_samples,
    }
```

```python
from collections import deque
import time

class RollingDistributionMonitor:
    def __init__(
        self,
        model_id: str,
        n_buckets: int,
        reference_window_minutes: int = 10080,  # 7 days = 10080 minutes
        monitoring_window_minutes: int = 5,
        min_samples_per_window: int = 100,
    ) -> None:
        self.model_id = model_id
        self.n_buckets = n_buckets
        self.reference_window_minutes = reference_window_minutes
        self.monitoring_window_minutes = monitoring_window_minutes
        self.min_samples_per_window = min_samples_per_window

        # Circular buffer of 5-minute snapshots
        max_snapshots = reference_window_minutes // monitoring_window_minutes
        self.snapshots: deque[DistributionSnapshot] = deque(maxlen=max_snapshots)
        self.current_window_counts = np.zeros(n_buckets, dtype=np.int64)
        self.current_window_start = time.time()

    def record_prediction(self, bucket_index: int) -> None:
        self.current_window_counts[bucket_index] += 1

    def flush_window(self) -> DistributionSnapshot | None:
        now = time.time()
        n_samples = int(self.current_window_counts.sum())

        if n_samples < self.min_samples_per_window:
            return None  # insufficient data for reliable distribution estimate

        snapshot = DistributionSnapshot(
            model_id=self.model_id,
            window_start=self.current_window_start,
            window_end=now,
            empirical_counts=self.current_window_counts.copy(),
            n_samples=n_samples,
        )
        self.snapshots.append(snapshot)
        self.current_window_counts[:] = 0
        self.current_window_start = now
        return snapshot

    def get_reference_distribution(self) -> DistributionSnapshot | None:
        if len(self.snapshots) < 2:
            return None
        combined_counts = sum(s.empirical_counts for s in self.snapshots)
        return DistributionSnapshot(
            model_id=self.model_id,
            window_start=self.snapshots[0].window_start,
            window_end=self.snapshots[-1].window_end,
            empirical_counts=combined_counts,
            n_samples=int(combined_counts.sum()),
        )
```

**Key pitfalls (3 with BROKEN->FIX):**

**Pitfall 1 - Computing KL divergence without epsilon leads to infinite divergence when reference has zero bins:**
```python
# BROKEN: if reference distribution has 0 counts in any bucket that current has > 0,
# KL diverges to infinity; one new prediction class -> infinite alert
def kl_broken(p, q):
    return float(np.sum(p * np.log(p / q)))   # q[i]=0 -> division by zero -> inf or NaN
# All fraud models alert immediately on new merchant category code

# FIX: add symmetric Laplace smoothing before computing KL
def kl_fixed(p_counts, q_counts, epsilon=1e-6):
    p = (p_counts + epsilon) / (p_counts.sum() + epsilon * len(p_counts))
    q = (q_counts + epsilon) / (q_counts.sum() + epsilon * len(q_counts))
    return float(np.sum(rel_entr(p, q)))   # never infinite
```

**Pitfall 2 - Using KL alone without statistical significance test causes spurious alerts from low sample size:**
```python
# BROKEN: threshold on raw KL value without accounting for window sample size
# With n=20 samples, KL of 0.08 from sampling noise triggers false alert
kl = compute_kl_divergence(snapshot_current.counts, snapshot_ref.counts)
if kl > 0.05:
    send_alert(model_id, kl)   # FPR = 18% at n=20 samples per window

# FIX: require both KL threshold AND statistical significance (chi2 p-value)
kl = compute_kl_divergence(...)
chi2_stat, chi2_p = chi2_drift_test(...)
if kl > 0.05 and chi2_p < 0.01:   # dual condition
    send_alert(model_id, kl)   # FPR drops to 0.8% at same n=20 window
```

**Pitfall 3 - Monitoring output distributions only misses input covariate shift before it propagates to outputs:**
```python
# BROKEN: monitor only prediction distribution; input shift detected only after
# model outputs degrade (lag of 1-3 hours for slow drift)
kl_output = compute_kl_divergence(output_probs_current, output_probs_ref)
# Input feature "income" distribution shifts due to data pipeline bug;
# model output barely changes (income weight = 0.03 in this model) -> missed

# FIX: monitor both input feature distributions and output distributions
# For top-20 features by importance, compute KL on discretised feature distributions
for feature in top_20_features:
    feature_kl = compute_kl_divergence(
        np.histogram(current_features[feature], bins=feature_bins[feature])[0],
        np.histogram(reference_features[feature], bins=feature_bins[feature])[0],
    )
    if feature_kl > 0.1:
        log_input_drift(feature, feature_kl)
```

**Metrics and results:**

| Metric | Accuracy-based monitoring | KL divergence monitoring |
|---|---|---|
| Alert latency (median) | 72 hr (label lag) | 5 min |
| False positive rate | 0.5% | 1.8% |
| False negative rate (KL > 0.05) | 31% (missed slow drift) | 7.2% |
| Monitoring overhead per event | 0.1ms | 2.7ms |
| Models covered (no labels needed) | 28% | 100% |
| Incidents caught before user impact | 3/month | 11/month |
| Mean time to detect drift | 72 hr | 12 min |
| Retraining triggers (true positives) | 3/month | 9/month |
| False retraining triggers | 0.2/month | 1.4/month |

**Interview discussion points:**

**Why is Jensen-Shannon divergence preferred over KL divergence for symmetric alerting?** KL divergence is asymmetric: KL(P||Q) != KL(Q||P). When the current distribution P has support in a bucket where the reference Q has zero probability, KL(P||Q) = infinity regardless of the epsilon correction's adequacy. JSD is symmetric (JSD(P,Q) = JSD(Q,P)), bounded in [0, log(2)] for nats, and handles support differences more gracefully because it uses the average distribution M = (P+Q)/2 as the reference point for both directions. For production alerting where the reference distribution also evolves over time, symmetry ensures that replacing P with Q in the comparison does not change the alert severity.

**What is the relationship between KL divergence, cross-entropy, and entropy?** KL(P||Q) = H(P, Q) - H(P), where H(P, Q) = -sum(P * log(Q)) is the cross-entropy and H(P) = -sum(P * log(P)) is the entropy of P. KL measures the extra bits needed to encode samples from P using a code optimised for Q. Cross-entropy is the total bits needed; entropy is the theoretical minimum for encoding P with its own optimal code. In the monitoring context, if the current distribution P has cross-entropy H(P, Q_ref) = 2.8 nats with the reference Q_ref and entropy H(P) = 2.3 nats, then KL = 0.5 nats, indicating the model has drifted by an amount requiring 0.5 extra nats per prediction to communicate under the reference code.

**How do you choose the number of buckets for discretising continuous model output scores?** Too few buckets (e.g., 5) miss subtle distribution shifts within bins; too many buckets (e.g., 1000) result in sparse counts per bucket, making KL estimates unreliable due to small-sample noise. The optimal choice balances resolution against reliability. A practical rule: ensure each bucket has at least 30 expected counts in both current and reference windows for the chi-squared test to be valid. With 1,200 events/second and 5-minute windows, n=360,000 events per window; with 100 buckets, expected count per bucket is 3,600 - well above the threshold of 30. Use quantile-based bins from the reference distribution to ensure uniform expected counts.

**What is the information gain interpretation of KL divergence in the context of model drift?** KL(P_current || P_reference) measures the expected additional bits of information contained in a prediction from P_current compared to what you would expect under P_reference. A KL of 0.05 nats means that, on average, each current prediction is 0.05 nats more surprising under the reference model than under the current model. In practice, KL = 0.05 corresponds roughly to the sensitivity threshold where human reviewers can begin to notice performance degradation in held-out metrics; KL = 0.15 corresponds to degradation noticeable to end users. These thresholds were calibrated empirically against 6 months of confirmed drift incidents.

**How would you extend this system to handle multivariate input drift using mutual information?** Mutual information I(X; Y) = H(X) + H(Y) - H(X, Y) measures statistical dependence between two variables. For detecting multivariate input drift, monitor the joint distribution of the top 5 most correlated input feature pairs: if I(feature_A_current, feature_B_current) drops significantly from the reference mutual information, it indicates that the correlation structure (not just marginal distributions) has changed - a deeper form of drift. Copula-based approaches estimate joint distributions non-parametrically. An alternative is to use the model's loss on a small labelled anchor dataset (100-500 labelled samples updated weekly) as a sensitive single-number drift signal combining all distributional changes.

**What is the computational cost of computing KL divergence for 400 models every 5 minutes?** With 400 models, each with output distributions over B=100 buckets, one KL computation requires 100 multiplications and 100 log evaluations: approximately 10 microseconds per model on a single CPU core. The chi-squared test requires an additional 200 multiplications: 15 microseconds per model. Total for 400 models: 400 * 25 microseconds = 10 milliseconds per monitoring cycle - negligible compared to the 5-minute (300,000ms) window. The primary cost is I/O: reading 5-minute count aggregates from Redis for all 400 models (400 * 100 * 8 bytes = 320 KB) at < 2ms round-trip. The total monitoring pipeline runs in under 50ms per 5-minute cycle, well within the 3ms per-event overhead budget when amortised.
