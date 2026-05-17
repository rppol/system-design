# Support Vector Machines — Deep Dive

## 1. Concept Overview

A Support Vector Machine (SVM) finds the hyperplane that separates two classes while maximizing the margin — the distance between the hyperplane and the nearest training points from each class. Those nearest points are called support vectors. The SVM objective combines geometric margin maximization with an optional tolerance for misclassification (soft margin), controlled by the regularization parameter C.

The kernel trick allows SVMs to operate implicitly in a high-dimensional (potentially infinite-dimensional) feature space without computing the mapping explicitly, enabling non-linear decision boundaries in the original input space at the computational cost of working in the original dimensionality.

SVMs generalize well from small labeled datasets, are robust in high-dimensional spaces, and have solid theoretical foundations (VC dimension, structural risk minimization). Their main weakness is training complexity: O(n^2) to O(n^3) in the number of training samples, making them impractical for n > 100,000 without approximations.

---

## 2. Intuition

One-line analogy: SVM is like finding the widest road between two neighborhoods — the road is the decision boundary and its width is the margin. Only the houses right on the edge of each neighborhood (support vectors) determine where the road goes.

Mental model: instead of minimizing training error (ERM), SVM minimizes model complexity (maximizes margin) subject to a constraint on training error. This is structural risk minimization — a tighter theoretical bound on generalization error.

Key insight: only the support vectors matter. After training, you can delete all non-support-vector training points and the model is identical. In practice, 5–20% of training points become support vectors.

Why the kernel trick works: the dual formulation of SVM only requires dot products between pairs of input vectors. Replacing x_i^T x_j with a kernel function K(x_i, x_j) = phi(x_i)^T phi(x_j) implicitly computes the dot product in the mapped space without ever computing phi(x).

---

## 3. Core Principles

**Hard margin SVM** (linearly separable data):
```
Minimize:   (1/2) ||w||^2
Subject to: y_i (w^T x_i + b) >= 1  for all i
```

**Soft margin SVM** (allow some misclassification via slack variables xi_i >= 0):
```
Minimize:   (1/2) ||w||^2 + C * sum_i xi_i
Subject to: y_i (w^T x_i + b) >= 1 - xi_i  and  xi_i >= 0
```
- C large: penalize misclassification heavily (small margin, risks overfitting)
- C small: accept more misclassification (large margin, more regularization)

**Dual formulation**: using Lagrange multipliers alpha_i >= 0:
```
Maximize:   sum_i alpha_i - (1/2) sum_{i,j} alpha_i alpha_j y_i y_j K(x_i, x_j)
Subject to: sum_i alpha_i y_i = 0  and  0 <= alpha_i <= C
```

At optimum: alpha_i > 0 only for support vectors. Prediction: f(x) = sign(sum_i alpha_i y_i K(x_i, x) + b).

**Hinge loss**: the soft-margin SVM is equivalent to minimizing: (1/2)||w||^2 + C * sum_i max(0, 1 - y_i f(x_i)). This is hinge loss with L2 regularization.

---

## 4. Types / Architectures / Strategies

### 4.1 Kernel Functions

| Kernel | Formula K(x, z) | Parameters | Best For |
|--------|----------------|-----------|----------|
| Linear | x^T z | None | High-dimensional sparse (text), linearly separable data |
| RBF (Gaussian) | exp(-gamma * ||x-z||^2) | gamma | General-purpose, unknown boundary shape |
| Polynomial | (gamma * x^T z + r)^d | gamma, d, r | Image classification, NLP (some tasks) |
| Sigmoid | tanh(gamma * x^T z + r) | gamma, r | Rarely used; behaves like RBF in practice |
| Chi-squared | sum_k (x_k - z_k)^2 / (x_k + z_k) | None | Histogram features (bag-of-words variants) |

**RBF gamma parameter**:
- gamma = "scale" (sklearn default): 1 / (n_features * X.var())
- gamma = "auto": 1 / n_features
- Large gamma: narrow Gaussian, complex boundary, risk of overfitting
- Small gamma: wide Gaussian, smoother boundary, risk of underfitting

### 4.2 Multi-Class SVMs

| Strategy | Description | Complexity |
|----------|-------------|-----------|
| One-vs-One (OvO) | K(K-1)/2 binary classifiers, majority vote | O(K^2) classifiers |
| One-vs-Rest (OvR) | K binary classifiers, highest score wins | O(K) classifiers |
| Crammer-Singer | Single multi-class objective | Slower, rarely used |

sklearn SVC defaults to OvO for multi-class problems.

### 4.3 SVM for Regression (SVR)

Support Vector Regression uses an epsilon-insensitive loss: ignore errors within epsilon of the prediction, penalize errors beyond epsilon.

```
Minimize:   (1/2)||w||^2 + C * sum_i (xi_i + xi_i*)
Subject to: y_i - (w^T x_i + b) <= epsilon + xi_i
            (w^T x_i + b) - y_i <= epsilon + xi_i*
            xi_i, xi_i* >= 0
```

### 4.4 One-Class SVM (Anomaly Detection)

Learns a boundary enclosing the training data (all assumed normal). Points outside the boundary are anomalies. Parameter nu controls the fraction of training points allowed to be outside the boundary (approximate upper bound on false positive rate).

---

## 5. Architecture Diagrams

### 5.1 SVM Margin and Support Vectors

```
Class -1 (circles)         Decision boundary        Class +1 (crosses)

  o   o                                                x   x
    o   o      <--- margin --->  w^T x + b = 0        x   x
      o  [o]   w^T x + b = -1                   [x]  x   x
               |   support vectors               |
               |     (bracketed)                 |
               |<---------- margin = 2/||w|| --->|
```

### 5.2 Effect of C Parameter

```
C = 0.01 (high regularization)     C = 100 (low regularization)

  o  o x                            o   o | x   x
 o o   x x                         o o    |x   x
o o  x   x                        o o  x x|  x x
 o  x o x                          o  x   | x
  o   o x                           o   o |x

Wide margin, some misclassified     Narrow margin, fewer misclassified
Fewer support vectors               More support vectors
Better generalization (maybe)       May overfit noise
```

### 5.3 Kernel Trick — RBF Lifting to Feature Space

```
Original 2D space             RBF feature space (infinite-dim)

  o   o   x x                   +--+--+--+--+
  o   x   o x        kernel     |  |  |  |  |
  o o x x   x    ------------> phi(x) ...
    o   o x x                 linear boundary in feature space
                               = non-linear boundary in input space
non-linearly separable         linearly separable
```

### 5.4 SMO Algorithm (Sequential Minimal Optimization)

```
While not converged:
    1. Select alpha_i (violates KKT conditions most)
    2. Select alpha_j (maximizes progress, heuristic)
    3. Optimize over alpha_i, alpha_j analytically (2-variable QP has closed form)
    4. Update b (bias)
    5. Check KKT conditions for all points

Advantage: avoids large matrix QP; each step is O(n) not O(n^2)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 SVM Classification with sklearn

```python
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.datasets import make_classification, make_moons
from sklearn.model_selection import train_test_split, GridSearchCV, StratifiedKFold
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.svm import SVC, SVR, OneClassSVM
from sklearn.metrics import (
    classification_report, roc_auc_score, mean_squared_error,
)


def svm_pipeline_example() -> None:
    """
    Correct SVM pipeline:
    1. StandardScaler is mandatory — SVM uses Euclidean distances
    2. Always tune C and gamma together (they interact)
    3. Use probability=True only if you need calibrated probabilities (adds Platt scaling cost)
    """
    # Non-linearly separable data — needs RBF kernel
    X, y = make_moons(n_samples=2000, noise=0.25, random_state=42)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        # probability=True adds significant overhead (5-fold internal CV for Platt scaling)
        # Only set True if you need predict_proba()
        ("svm", SVC(kernel="rbf", C=1.0, gamma="scale", probability=True, random_state=42)),
    ])
    pipeline.fit(X_train, y_train)

    y_pred = pipeline.predict(X_test)
    y_proba = pipeline.predict_proba(X_test)[:, 1]
    print(classification_report(y_test, y_pred))
    print(f"AUC-ROC: {roc_auc_score(y_test, y_proba):.4f}")


def tune_svm_hyperparameters(
    X_train: np.ndarray,
    y_train: np.ndarray,
) -> Pipeline:
    """
    Grid search over C and gamma.
    CRITICAL: C and gamma must be tuned jointly — they trade off against each other.
    Use logarithmic grid: small to large, powers of 10.
    """
    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("svm", SVC(kernel="rbf", probability=True, random_state=42)),
    ])

    param_grid = {
        "svm__C":     [0.01, 0.1, 1.0, 10.0, 100.0],
        "svm__gamma": [0.001, 0.01, 0.1, 1.0, 10.0],
    }

    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    search = GridSearchCV(
        pipeline,
        param_grid,
        cv=cv,
        scoring="roc_auc",
        n_jobs=-1,
        verbose=2,
    )
    search.fit(X_train, y_train)
    print(f"Best C={search.best_params_['svm__C']}, gamma={search.best_params_['svm__gamma']}")
    print(f"Best CV AUC: {search.best_score_:.4f}")
    return search.best_estimator_


def compare_kernels() -> None:
    """
    Compare linear, RBF, and polynomial kernels on non-linear data.
    Shows that linear kernel fails on non-linearly separable problems.
    """
    X, y = make_moons(n_samples=1000, noise=0.2, random_state=42)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    kernels: dict[str, dict] = {
        "linear":     {"kernel": "linear", "C": 1.0},
        "RBF":        {"kernel": "rbf", "C": 1.0, "gamma": "scale"},
        "polynomial": {"kernel": "poly", "C": 1.0, "degree": 3, "gamma": "scale"},
    }

    for name, params in kernels.items():
        svm = SVC(**params, random_state=42)
        svm.fit(X_train_s, y_train)
        acc = svm.score(X_test_s, y_test)
        n_sv = svm.n_support_.sum()
        print(f"{name:12s}  accuracy={acc:.4f}  support_vectors={n_sv}")
```

### 6.2 SVM for Regression (SVR)

```python
from sklearn.svm import SVR
from sklearn.datasets import make_regression


def svr_example() -> None:
    """
    SVR: epsilon-insensitive tube around the prediction.
    Points within epsilon of the prediction incur zero loss.
    C controls the penalty for points outside the tube.
    epsilon controls the tube width (larger = fewer support vectors).
    """
    X, y = make_regression(n_samples=500, n_features=5, noise=10.0, random_state=42)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    # Scale target too for SVR — often improves convergence
    from sklearn.preprocessing import StandardScaler as SS
    y_scaler = SS()
    y_train_s = y_scaler.fit_transform(y_train.reshape(-1, 1)).ravel()

    svr = SVR(kernel="rbf", C=10.0, epsilon=0.1, gamma="scale")
    svr.fit(X_train_s, y_train_s)

    y_pred_s = svr.predict(X_test_s)
    y_pred = y_scaler.inverse_transform(y_pred_s.reshape(-1, 1)).ravel()

    rmse = mean_squared_error(y_test, y_pred) ** 0.5
    print(f"SVR RMSE: {rmse:.4f}")
    print(f"Number of support vectors: {svr.n_support_.sum()}")
```

### 6.3 One-Class SVM for Anomaly Detection

```python
from sklearn.svm import OneClassSVM


def one_class_svm_anomaly() -> None:
    """
    One-Class SVM learns a boundary around normal training data.
    nu: upper bound on the fraction of training errors (outliers in training).
    nu = 0.05 means at most 5% of training points are outside the boundary.
    Prediction: +1 = normal, -1 = anomaly.
    """
    rng = np.random.default_rng(42)

    # Normal data: Gaussian blob
    X_normal = rng.multivariate_normal(mean=[0, 0], cov=[[1, 0.5], [0.5, 1]], size=500)

    # Anomaly data: scattered far from center
    X_anomaly = rng.uniform(low=-5, high=5, size=(50, 2))

    X_all = np.vstack([X_normal, X_anomaly])
    labels = np.array([1] * 500 + [-1] * 50)   # 1=normal, -1=anomaly (ground truth)

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_normal)     # fit on normal data ONLY
    X_all_s = scaler.transform(X_all)

    # nu controls the trade-off: lower nu = tighter boundary (more false negatives)
    ocsvm = OneClassSVM(kernel="rbf", nu=0.05, gamma="scale")
    ocsvm.fit(X_train_s)

    predictions = ocsvm.predict(X_all_s)   # +1 = normal, -1 = anomaly
    n_anomalies_detected = (predictions == -1).sum()
    print(f"Anomalies detected: {n_anomalies_detected} / 50")


def svm_feature_importance_via_permutation() -> None:
    """
    SVM has no built-in feature importance.
    For linear SVM: |w_j| = feature importance (w is recoverable).
    For kernel SVM: use permutation importance (model-agnostic).
    """
    from sklearn.inspection import permutation_importance
    from sklearn.datasets import load_breast_cancer

    data = load_breast_cancer()
    X, y = data.data, data.target
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("svm", SVC(kernel="linear", C=1.0, random_state=42)),
    ])
    pipeline.fit(X_train, y_train)

    # For linear kernel: direct coefficient access
    linear_svm = pipeline.named_steps["svm"]
    importances = np.abs(linear_svm.coef_[0])
    feature_names = data.feature_names
    top_features = sorted(
        zip(feature_names, importances), key=lambda x: x[1], reverse=True
    )[:5]
    print("Top 5 features (linear SVM):")
    for name, imp in top_features:
        print(f"  {name}: {imp:.4f}")
```

### 6.4 Scaling Issue Demonstration (Critical Bug)

```python
def demonstrate_scaling_bug() -> None:
    """
    SVM without scaling: features with large magnitude dominate.
    Shows the dramatic accuracy difference.
    """
    from sklearn.datasets import load_breast_cancer

    data = load_breast_cancer()
    X, y = data.data, data.target

    # Features have very different scales:
    # mean radius: ~10-30, mean area: ~140-2500, mean fractal dimension: ~0.05-0.1

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    # --- WRONG: No scaling ---
    svm_bad = SVC(kernel="rbf", C=1.0, gamma="scale", random_state=42)
    svm_bad.fit(X_train, y_train)
    acc_bad = svm_bad.score(X_test, y_test)
    print(f"SVM WITHOUT scaling: accuracy = {acc_bad:.4f}")

    # --- CORRECT: StandardScaler before SVM ---
    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    svm_good = SVC(kernel="rbf", C=1.0, gamma="scale", random_state=42)
    svm_good.fit(X_train_s, y_train)
    acc_good = svm_good.score(X_test_s, y_test)
    print(f"SVM WITH scaling:    accuracy = {acc_good:.4f}")
    # Typical result: without scaling ~0.63, with scaling ~0.97
```

---

## 7. Real-World Examples

**Text Classification (SVM + TF-IDF)**: Before transformer models, SVM with linear kernel was the dominant approach for document classification (news categorization, sentiment analysis). The high-dimensional sparse TF-IDF vectors (50,000+ dimensions) suit linear SVM perfectly — it finds a separating hyperplane in sparse space efficiently. sklearn's LinearSVC (based on liblinear) handles this in O(n * nnz) where nnz is the average number of non-zero TF-IDF values per document.

**Cancer Detection (SVM with RBF)**: Radiology studies from the early 2010s used SVM with RBF kernel on engineered features extracted from tissue samples (texture, gradient magnitude, compactness). With 200–500 labeled samples — too few for deep learning — SVM consistently outperformed neural networks due to its maximum-margin generalization guarantee.

**Handwritten Digit Recognition (SVM with RBF)**: The MNIST benchmark was first solved competitively with SVMs. An SVM with RBF kernel on raw pixels achieves ~0.98 accuracy on MNIST. The support vectors are the ambiguous digit images — the ones the classifier needs to memorize because they sit near the decision boundary.

**Anomaly Detection in Industrial Monitoring**: semiconductor fabs use One-Class SVM to detect equipment anomalies from sensor streams. Training data is exclusively normal operation (anomalies are rare and diverse, so cannot be labeled). One-class SVM learns the distribution of normal behavior; deviations trigger alerts. nu = 0.01 sets the expected false positive rate at 1%.

---

## 8. Tradeoffs

| Aspect | Linear SVM | RBF SVM | Polynomial SVM |
|--------|-----------|---------|---------------|
| Training complexity | O(n) via SGD | O(n^2)–O(n^3) | O(n^2)–O(n^3) |
| Inference complexity | O(d) | O(n_sv * d) | O(n_sv * d) |
| Interpretable | Yes (coef_) | No | No |
| Non-linear boundaries | No | Yes | Yes |
| Hyperparameters | C only | C, gamma | C, gamma, degree |
| Memory for large n | Low | High (kernel matrix) | High |
| Best dataset size | Up to 10M rows | Up to 100k rows | Up to 100k rows |

---

## 9. When to Use / When NOT to Use

**Use SVM with linear kernel when**:
- High-dimensional sparse features (text with TF-IDF, one-hot encoded categoricals)
- Need for interpretable coefficients (similar to logistic regression)
- n up to a few million (use LinearSVC for scalability beyond sklearn SVC)

**Use SVM with RBF kernel when**:
- Small to medium dataset (n < 100,000)
- Unknown or complex decision boundary shape
- Feature dimensionality is manageable (d < 1,000)
- More labeled data is expensive to acquire (medical, industrial)

**Use One-Class SVM when**:
- Only normal-class training data available
- Anomaly definition is "far from normal" rather than "belongs to class B"

**Do NOT use kernel SVM when**:
- n > 100,000: training becomes prohibitively slow (consider LinearSVC, gradient boosting, or neural networks)
- Very high-dimensional dense features (d > 10,000 with kernel SVM): kernel matrix dominates memory
- You need real-time online learning (SVM requires full retraining or approximate updates)
- Probability outputs are needed without paying the cost of Platt scaling (prefer logistic regression)

---

## 10. Common Pitfalls

**Pitfall 1 — Not scaling before SVM**
A production fraud detection team achieved 63% accuracy with RBF SVM. After investigation, the transaction_amount feature (range $0–$50,000) dominated the Euclidean distance computation, making the RBF kernel effectively measure only that one feature. Adding StandardScaler raised accuracy to 91%. This is the single most common SVM bug in production ML code. Distance-based models (SVM, k-NN) always require feature scaling.

**Pitfall 2 — Using SVC when n > 50,000**
An NLP team tried SVC with RBF kernel on 500,000 text documents. Training ran for 18 hours and never completed. The O(n^2)–O(n^3) complexity of the quadratic programming solver made it infeasible. Fix: use LinearSVC (via liblinear, O(n * iter)) or SGDClassifier with hinge loss (online, constant memory) for large-scale text.

**Pitfall 3 — Tuning C and gamma independently**
A team did a grid search: first tuned C (fixing gamma="scale"), then tuned gamma (fixing best C). This missed the interaction — at large C, small gamma performs differently than at small C. C and gamma must be tuned jointly in a 2D grid search. The optimal point (C=10, gamma=0.01) may be far from the result of sequential tuning.

**Pitfall 4 — probability=True overhead misunderstood**
SVC(probability=True) performs an internal 5-fold cross-validation to fit Platt scaling parameters. This multiplies training time by ~5x. A team set probability=True by default in all experiments even when they only needed the class prediction (not probability). Fix: set probability=False for all experiments where predict_proba is not needed. If calibrated probabilities are needed, consider CalibratedClassifierCV as a wrapper around SVC(probability=False) — more flexible.

**Pitfall 5 — One-Class SVM for structured anomalies**
A team used One-Class SVM to detect network intrusions but found 80% false negative rate on novel attack patterns. One-Class SVM works when anomalies are "far from normal." If anomalies cluster in a specific region of feature space (a known attack signature), a binary SVM (trained on labeled examples of normal and attack) outperforms the one-class variant. One-class SVM is the right tool only when anomalies are genuinely unknown and diverse.

**Pitfall 6 — Forgetting to scale the target in SVR**
SVR's epsilon parameter is in the units of the target variable. If the target has large variance (e.g., house prices in dollars, range $50k–$5M), a default epsilon=0.1 is meaningless. Scaling the target to zero mean, unit variance (then inverse-transforming predictions) makes epsilon meaningful and training more stable.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| sklearn SVC | Full SVM with all kernels, soft margin | Uses libsvm internally; O(n^2-n^3) |
| sklearn LinearSVC | SVM with linear kernel, liblinear backend | Much faster for n > 10k, no predict_proba |
| sklearn SGDClassifier(loss="hinge") | Online SVM approximation | Constant memory, handles n = billions |
| sklearn SVR | SVM regression | epsilon-insensitive loss |
| sklearn OneClassSVM | Anomaly detection | nu parameter controls boundary tightness |
| libsvm | C library underlying sklearn SVC | Direct access for custom kernels |
| ThunderSVM | GPU-accelerated SVM | 10-100x speedup for large datasets |
| sklearn CalibratedClassifierCV | Add probability calibration to SVC | Wraps SVC(probability=False) |

---

## 12. Interview Questions with Answers

**Q: What are support vectors and why do only they matter after training?**
Support vectors are the training points that lie on or within the margin boundaries — they have non-zero Lagrange multipliers (alpha_i > 0) in the dual formulation. After training, the decision function is f(x) = sum_i alpha_i y_i K(x_i, x) + b, which only involves the support vectors (all non-support points have alpha_i = 0). This means the trained model only needs to store and evaluate support vectors at inference time, not the entire training set.

**Q: Explain the kernel trick. Why is it computationally advantageous?**
The kernel trick exploits that the SVM dual formulation requires only pairwise dot products between training points: K(x_i, x_j) = phi(x_i)^T phi(x_j). By defining a kernel function K that computes this inner product without explicitly computing phi, we can operate in very high or infinite-dimensional feature spaces at O(d) cost per kernel evaluation (the original feature dimensionality). For example, the RBF kernel implicitly maps to an infinite-dimensional space but each evaluation costs O(d) not O(infinity).

**Q: What is the role of the C parameter in soft-margin SVM?**
C controls the trade-off between maximizing the margin and minimizing training error. Large C: heavily penalizes misclassification — small margin, model fits training data tightly, risks overfitting. Small C: allows more misclassification — large margin, more regularization, risks underfitting. C is the inverse of regularization strength: C = 1/lambda. Tuning C via cross-validation is essential; common range: 10^{-3} to 10^3 on a log scale.

**Q: What is the difference between the primal and dual formulation of SVM, and why is the dual used?**
The primal is a d-dimensional QP (d = feature dimension): minimize over w, b. The dual is an n-dimensional QP (n = samples): maximize over alpha. For kernel SVM with high or infinite-dimensional feature space (RBF), the primal formulation requires operating in that space, which may be computationally intractable. The dual formulation requires only kernel evaluations K(x_i, x_j), which cost O(d) regardless of the implicit feature space dimension. For n << d (small datasets, high dimensions), the primal can be preferable; for large n with kernels, the dual is necessary.

**Q: How does the RBF gamma parameter affect the decision boundary?**
Gamma controls the width of the Gaussian kernel: K(x, z) = exp(-gamma * ||x-z||^2). Large gamma: the Gaussian is narrow — each training point influences only its immediate neighborhood. The decision boundary becomes complex and wiggly, following individual training points closely — high variance, risk of overfitting. Small gamma: wide Gaussian — each point influences a broad region. The boundary is smooth and almost linear — high bias, risk of underfitting. gamma="scale" (default in sklearn) sets gamma = 1/(n_features * X.var()), which is a good starting point.

**Q: Why is SVM effective in high-dimensional spaces despite the curse of dimensionality?**
SVM maximizes the margin, which provides a generalization bound independent of the input dimensionality d. The VC dimension of a linear SVM is min(d, n) + 1, but the effective VC dimension (fat-shattering dimension) of the margin classifier is bounded by the margin, not d. In high-dimensional sparse spaces (text), the data is often linearly separable with a large margin — SVM exploits this. This contrasts with k-NN, where all distances collapse in high dimensions.

**Q: Compare SVM and logistic regression. When would you choose each?**
Both SVM (hinge loss + L2) and logistic regression (log-loss + L2) produce linear classifiers in their primal form and have similar generalization performance on large datasets. Choose SVM: small dataset where maximizing margin matters, kernel trick for non-linearity, sparse text features (LinearSVC). Choose logistic regression: when you need calibrated probabilities, interpretable coefficients, faster training on large data, or online learning. SVM does not natively output calibrated probabilities (Platt scaling is an approximation). On very large datasets, SGD-based logistic regression (sklearn SGDClassifier with log loss) wins on speed.

**Q: How does One-Class SVM differ from isolation forest for anomaly detection?**
One-Class SVM learns a boundary enclosing the training data in a kernel-induced feature space; it is sensitive to the kernel choice and gamma parameter, and scales as O(n^2). It works well when normal data is compact and anomalies are far from the center. Isolation Forest randomly partitions the feature space and assigns anomaly scores based on the average path length to isolate a point; it scales as O(n log n), handles high-dimensional data better, and is generally more robust to outliers in the training set. In practice, Isolation Forest outperforms One-Class SVM on most tabular anomaly detection benchmarks.

**Q: What is the SMO algorithm and why was it important for practical SVM training?**
Sequential Minimal Optimization (Platt 1998) decomposes the large n-variable QP of SVM dual into a series of minimal 2-variable sub-problems, each solvable analytically in closed form. This avoids storing the full n x n kernel matrix (O(n^2) memory) and avoids large matrix operations. SMO selects the two most violating variables at each step (heuristic), making progress toward the KKT optimum. SMO made SVM practical for datasets with tens of thousands of points — before it, only hundreds of points were feasible.

**Q: How do you use an SVM for multi-class classification?**
sklearn SVC uses One-vs-One (OvO) by default: for K classes, it trains K(K-1)/2 binary classifiers and uses majority vote. For 10 classes, that is 45 classifiers. LinearSVC uses One-vs-Rest (OvR): K classifiers, each separating one class from all others; assign the class with highest decision function value. OvO is typically more accurate because each binary classifier trains on a balanced subset; OvR is faster. For K > 10, OvR or the Crammer-Singer multi-class SVM formulation are preferred.

**Q: Why does SVM with probability=True slow training significantly?**
SVC(probability=True) adds Platt scaling: after the SVM is trained, it fits a sigmoid function A * f(x) + B = logit(P(y=1|x)) using another internal k-fold cross-validation (default 5-fold). This multiplies total training time by approximately 5-6x. The resulting probabilities are approximate and the sigmoid fitting can be unstable for small datasets or when class distributions are extreme. For better-calibrated probabilities, use CalibratedClassifierCV with cv=5 externally, giving more control over the calibration process.

**Q: What is the hinge loss and how does it relate to the SVM objective?**
Hinge loss for a single example: L = max(0, 1 - y * f(x)), where y in {-1, +1} and f(x) = w^T x + b. It is zero when the example is correctly classified with margin >= 1, and linear in the margin violation otherwise. The soft-margin SVM minimizes (1/2)||w||^2 + C * sum_i max(0, 1 - y_i f(x_i)), which is L2 regularization of the weight vector plus C times the sum of hinge losses. This is exactly a regularized ERM with hinge loss. Compared to logistic regression's log loss, hinge loss is zero beyond the margin (sparser gradient updates, fewer support vectors) but is not differentiable at margin = 1.

**Q: How would you debug an SVM that achieves near-random performance on a binary classification task?**
Systematic diagnosis: (1) check that features are scaled — unscaled features with high variance dominate RBF kernel; (2) print n_support_ — very high number (~50% of training data) indicates the model is underfitting (C too low or data is not separable with current kernel); (3) try LinearSVC first — if linear kernel fails, the boundary is genuinely non-linear; (4) reduce the dataset to 2 features and plot the decision boundary to visually confirm the kernel is learning; (5) check for label issues — if classes are mislabeled or highly imbalanced and class_weight is not set, the model predicts all-majority-class; (6) verify C and gamma range in grid search — if the optimal point is at the boundary of your grid, expand the search.

**Q: How does SVR differ from linear regression, and when would you prefer it?**
SVR minimizes ||w||^2 subject to all predictions being within epsilon of the true values — errors within the tube incur no loss (epsilon-insensitivity). Points outside the tube are penalized linearly. This produces a sparser solution than OLS (only out-of-tube points are support vectors) and is more robust to outliers (the epsilon tube absorbs small errors). SVR is preferred when: the target has significant noise that should be ignored (measured sensor readings with ±5% instrument error); outliers in y are possible and should not dominate the fit; a non-linear kernel gives better generalization than polynomial feature expansion. For most tabular regression tasks, gradient boosted trees dominate SVR in practice.

**Q: How do you handle very large datasets (n > 1 million) with SVMs?**
Kernel SVMs are impractical beyond ~100,000 samples due to O(n^2)–O(n^3) training complexity. Options: (1) LinearSVC or SGDClassifier(loss="hinge") — linear SVM via liblinear or online SGD, both O(n); (2) Nystrom approximation — approximate the kernel matrix with a low-rank factorization, reducing to a linear problem in the approximated feature space; (3) Random Fourier Features (sklearn RBFSampler) — approximate RBF kernel with random feature maps, then use linear SVM; (4) Mini-batch training with gradient-based solvers. In practice, for n > 1M, gradient boosted trees or neural networks are the standard choice.

**Q: What are the KKT conditions for SVM and what do they tell us about support vectors?**
The Karush-Kuhn-Tucker (KKT) conditions for the SVM dual are: (1) alpha_i >= 0; (2) y_i f(x_i) - 1 + xi_i >= 0; (3) alpha_i (y_i f(x_i) - 1 + xi_i) = 0; (4) mu_i xi_i = 0, where mu_i = C - alpha_i. From condition (3): if alpha_i > 0 (a support vector), then y_i f(x_i) = 1 - xi_i. Three regimes: alpha_i = 0 (non-support vector, correctly classified with margin > 1); 0 < alpha_i < C (on the margin boundary, xi_i = 0); alpha_i = C (inside or on the wrong side of the margin, xi_i > 0). The SMO algorithm uses KKT violation as its selection criterion for which alpha pairs to optimize next.

**Q: How do you extract feature importance from a kernel SVM?**
For linear SVM: the weight vector w = sum_i alpha_i y_i x_i is explicitly available as model.coef_. Feature importance is |w_j| — larger absolute values indicate stronger linear influence. For kernel SVM (RBF, polynomial): w is implicitly defined in the infinite-dimensional feature space and is not directly interpretable in the input space. Use model-agnostic methods: permutation importance (sklearn.inspection.permutation_importance) — randomly shuffle each feature and measure accuracy drop; SHAP kernel explainer (slower but theoretically grounded); or switch to a linear kernel for interpretability and accept the lower accuracy.

---

## 13. Best Practices

1. Always apply StandardScaler before SVM. Wrap in a sklearn Pipeline to prevent leakage. This is the single most impactful step for SVM performance.

2. Tune C and gamma jointly in a 2D grid search. Use a log-scale grid: C in [0.01, 0.1, 1, 10, 100], gamma in [0.001, 0.01, 0.1, 1, 10]. The optimal region is often a diagonal band in this 2D space.

3. For n > 50,000 with a linear kernel, use LinearSVC instead of SVC(kernel="linear"). LinearSVC uses the liblinear solver, which scales as O(n * iter) rather than O(n^2).

4. Set probability=False unless predict_proba is genuinely needed. The overhead of Platt scaling (5x training time) is not justified for experiments where only class predictions are needed.

5. For anomaly detection with labeled normal data only, use OneClassSVM with nu set to the expected contamination rate in training (e.g., nu=0.01 if you expect 1% of your training data to be mislabeled anomalies).

6. For SVR, scale both features and target. SVR's epsilon parameter is in target units; an unscaled target makes epsilon meaningless and training unstable.

7. When SVM performance is poor, check n_support_. If support vectors are > 30% of training set, the model is underfitting: try larger C, different kernel, or more features. If support vectors are < 5% and training error is near zero but test error is high, the model is overfitting: reduce C or increase gamma.

8. Use permutation importance or SHAP for feature importance with kernel SVMs. Never report model.coef_ for RBF or polynomial kernels — it does not exist in the input space.

---

## 14. Case Study

**Problem**: a bioinformatics team needs to classify cancer subtypes from gene expression microarray data. Dataset: 200 tumor samples, 20,000 gene expression features (d >> n), 3 cancer subtypes (multi-class). Constraint: need to identify the most discriminative genes for biological interpretation.

**Why SVM**: with n=200 and d=20,000, the feature space is extremely high-dimensional relative to sample count. Neural networks would overfit catastrophically. SVM's maximum-margin principle provides strong generalization guarantees even in this d >> n regime, especially with a linear kernel (the data may be linearly separable in gene expression space).

**Pipeline**:
```
Raw gene expression matrix (200 x 20,000)
    |
    v
Variance filtering (keep top 5,000 genes by variance across samples)
    |
    v
StandardScaler (fit on training folds only)
    |
    v
LinearSVC(C=0.01, multi_class="ovr", max_iter=5000)
    |
    v
5-fold cross-validation for C in [0.001, 0.01, 0.1, 1.0]
    |
    v
Optimal: C=0.01  (strong regularization appropriate for n=160 effective training)
    |
    v
Evaluation on held-out 20% test set (40 samples)
    |--- Accuracy: 0.88
    |--- Macro F1: 0.87
    |
    v
Feature extraction: model.coef_ shape (3, 5000) → |w_j| per subtype
```

**Results**:
- 88% accuracy on held-out test set (40 samples) across 3 cancer subtypes
- Baseline (majority class): 45%
- Top discriminative genes matched known biomarkers from literature for 2 of 3 subtypes, providing biological validation
- The sparse support vector set (12 support vectors out of 160 training samples) indicated a clean linear boundary in gene expression space

**Alternative considered**: RBF SVM scored 82% (lower — the linear kernel fits better in this very high-dimensional space, which is expected). Gradient boosting scored 79% due to insufficient data for ensemble methods.

**Biological insight from coefficients**: the top 20 genes by |w_j| for subtype 1 included 14 genes from the HER2 pathway, matching established HER2-amplified breast cancer biology. The linear SVM's interpretable coefficients were critical for publication and FDA regulatory review of the diagnostic claim.
