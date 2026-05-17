# Linear Algebra and Calculus for ML

## 1. Concept Overview

Linear algebra and calculus form the mathematical backbone of every machine learning algorithm. Linear algebra provides the language for representing data (vectors, matrices, tensors) and the operations that transform it (dot products, matrix multiplication, decompositions). Calculus provides the tools for measuring how a function changes (derivatives, gradients) and for minimizing loss functions (gradient descent, backpropagation).

A neural network is, mechanically, a sequence of matrix multiplications and nonlinear activations. Training that network is the process of computing gradients of a scalar loss with respect to millions of weight matrices, then updating those weights. Without linear algebra there is no forward pass; without calculus there is no backward pass.

---

## 2. Intuition

> **One-line analogy**: Linear algebra is the geometry of data transformations; calculus tells you which direction to nudge each transformation to improve your answer.

**Mental model**: Imagine data as points in high-dimensional space. A matrix is a machine that rotates, scales, and shears that space. Eigendecomposition reveals the "natural axes" of a matrix — the directions it stretches and by how much. SVD generalizes this to non-square matrices, which is how recommendation systems discover latent factors hidden in user-item rating tables.

**Why it matters**: Every convolution in a CNN is a batched dot product. Every attention score in a transformer is QK^T / sqrt(d_k). Every weight update in gradient descent requires d(Loss)/d(Weight). Misunderstanding shapes, broadcasting rules, or gradient flow leads to bugs that are silent — the code runs but the model diverges.

**Key insight**: Backpropagation is just the chain rule applied systematically to a computation graph. The chain rule connects local Jacobians; automatic differentiation libraries (PyTorch autograd, JAX) make this mechanical.

---

## 3. Core Principles

- **Vectors**: ordered lists of numbers; represent data points, parameters, or directions in n-dimensional space.
- **Matrices**: 2D arrays; represent linear transformations, datasets (rows = samples, cols = features), or weight layers.
- **Dot product**: a . b = sum(a_i * b_i) = ||a|| ||b|| cos(theta); measures similarity between vectors.
- **Matrix multiplication**: (AB)_{ij} = sum_k A_{ik} B_{kj}; composition of linear transformations; not commutative.
- **Transpose**: A^T_{ij} = A_{ji}; flips rows and columns; (AB)^T = B^T A^T.
- **Inverse**: A^{-1} exists only when A is square and full rank; AA^{-1} = I; computing it is O(n^3).
- **Norms**: measure magnitude; L1 = sum|x_i|; L2 = sqrt(sum x_i^2); Frobenius = sqrt(sum A_{ij}^2).
- **Gradient**: vector of partial derivatives; gradient of scalar f w.r.t. vector x is a vector pointing toward steepest ascent.
- **Chain rule**: d(f(g(x)))/dx = f'(g(x)) * g'(x); extends to vectors via Jacobian matrices.
- **Jacobian**: J_{ij} = df_i/dx_j; m x n matrix for a function mapping R^n -> R^m.
- **Hessian**: H_{ij} = d^2f/(dx_i dx_j); n x n matrix of second-order partial derivatives; encodes curvature.

---

## 4. Types / Architectures / Strategies

### 4.1 Matrix Decompositions

**Eigendecomposition**: A = Q Lambda Q^T for symmetric positive semi-definite matrices. Lambda is diagonal with eigenvalues. Q is orthogonal with eigenvectors as columns. Tells you the principal directions of a transformation.

**Singular Value Decomposition (SVD)**: A = U Sigma V^T. Always exists for any m x n matrix. U (m x m) and V (n x n) are orthogonal; Sigma (m x n) is diagonal with non-negative singular values in decreasing order. Rank-k approximation uses only the top-k singular values; minimizes Frobenius norm error.

**LU Decomposition**: A = LU; used to solve linear systems efficiently (O(n^3) one-time factorization, then O(n^2) per solve).

**QR Decomposition**: A = QR; numerically stable for solving least squares; used in Gram-Schmidt orthogonalization.

### 4.2 Gradient Computation Strategies

**Symbolic differentiation**: exact closed-form derivatives; computationally expensive for large graphs.

**Numerical differentiation**: (f(x+h) - f(x)) / h; approximate; used for gradient checking only; O(n) evaluations per parameter.

**Automatic differentiation (autodiff)**: exact derivatives via operator overloading; forward mode for few inputs, reverse mode (backpropagation) for few outputs (scalar loss); O(1) overhead factor over the forward pass.

### 4.3 Norm Types and Their ML Role

| Norm | Formula | ML Usage |
|------|---------|----------|
| L0 | count of nonzeros | exact sparsity; non-differentiable |
| L1 | sum of abs values | Lasso; promotes sparse solutions |
| L2 | sqrt(sum of squares) | Ridge; promotes small weights |
| L-inf | max abs value | robust optimization |
| Frobenius | sqrt(sum of all element squares) | matrix regularization |
| Nuclear | sum of singular values | matrix completion; low-rank reg |

---

## 5. Architecture Diagrams

### Matrix Multiplication Shape Flow

```
Input:    X (batch=32, features=784)
Weight:   W (features=784, hidden=256)
Bias:     b (hidden=256)

Forward pass:
  X @ W + b -> H (32, 256)
  H shape = (batch, hidden)

Gradient of loss L w.r.t. W:
  dL/dW = X^T @ dL/dH    shape: (784, 256)
  dL/dX = dL/dH @ W^T    shape: (32, 784)
  dL/db = sum(dL/dH, axis=0)  shape: (256,)
```

### SVD Rank-k Approximation

```
Original matrix A (m x n):
  A  =  U    Sigma    V^T
     (m x m)(m x n)(n x n)

Rank-k approximation A_k:
  Keep only top-k columns of U, top-k singular values, top-k rows of V^T

  A_k = U[:,0:k]  @  diag(sigma[0:k])  @  V^T[0:k,:]
       (m x k)         (k x k)             (k x n)

Energy retained = sum(sigma[0:k]^2) / sum(sigma^2)
```

### Backpropagation Chain Rule

```
Loss L
  |
  v
z_3 = W_3 @ a_2 + b_3    dL/dW_3 = dL/dz_3 @ a_2^T
  |
  v  (relu)
a_2 = relu(z_2)           dL/dz_2 = dL/da_2 * (z_2 > 0)
  |
  v
z_2 = W_2 @ a_1 + b_2    dL/dW_2 = dL/dz_2 @ a_1^T
  |
  ... (chain continues)
```

---

## 6. How It Works — Detailed Mechanics

### Eigendecomposition and PCA

```python
import numpy as np
from numpy.linalg import eigh, svd

def pca_via_eigen(X: np.ndarray, n_components: int) -> np.ndarray:
    """
    PCA using eigendecomposition of the covariance matrix.

    Args:
        X: (n_samples, n_features) data matrix
        n_components: number of principal components to keep

    Returns:
        X_reduced: (n_samples, n_components) projected data
    """
    # Center the data
    X_centered = X - X.mean(axis=0)

    # Covariance matrix: (n_features, n_features)
    # Divides by n-1 for unbiased estimate
    cov = np.cov(X_centered, rowvar=False)  # rowvar=False -> rows are samples

    # eigh for symmetric matrices: returns eigenvalues in ascending order
    # eig (general) is numerically less stable for symmetric matrices
    eigenvalues, eigenvectors = eigh(cov)

    # Sort in descending order (eigh returns ascending)
    idx = np.argsort(eigenvalues)[::-1]
    eigenvalues = eigenvalues[idx]
    eigenvectors = eigenvectors[:, idx]

    # Variance explained
    explained_variance_ratio = eigenvalues / eigenvalues.sum()
    print(f"Variance explained by {n_components} components: "
          f"{explained_variance_ratio[:n_components].sum():.3f}")

    # Project onto top-k eigenvectors
    components = eigenvectors[:, :n_components]          # (n_features, n_components)
    X_reduced = X_centered @ components                   # (n_samples, n_components)
    return X_reduced


def pca_via_svd(X: np.ndarray, n_components: int) -> np.ndarray:
    """
    PCA using SVD — numerically more stable than eigendecomposition for
    tall matrices (n_samples >> n_features).

    SVD of X_centered: X = U Sigma V^T
    Principal components = V (right singular vectors)
    Projections = U Sigma = X V

    Singular values relate to eigenvalues: sigma_i^2 = lambda_i * (n-1)
    """
    X_centered = X - X.mean(axis=0)

    # full_matrices=False: economy/thin SVD; shapes (n,k), (k,), (k,p) for k=min(n,p)
    U, sigma, Vt = svd(X_centered, full_matrices=False)

    # Top-k components; V^T rows are the principal directions
    components = Vt[:n_components, :].T    # (n_features, n_components)
    X_reduced = X_centered @ components    # (n_samples, n_components)
    return X_reduced
```

### SVD for Recommendation Systems (Latent Factor Model)

```python
def svd_recommend(
    ratings: np.ndarray,
    n_factors: int = 50
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    Low-rank matrix factorization via SVD.
    ratings: (n_users, n_items), NaN for unobserved.
    Returns user factors, singular values, item factors.
    """
    # Fill missing ratings with row means (simple imputation)
    row_means = np.nanmean(ratings, axis=1, keepdims=True)
    ratings_filled = np.where(np.isnan(ratings), row_means, ratings)

    # Mean-center
    ratings_centered = ratings_filled - row_means

    U, sigma, Vt = svd(ratings_centered, full_matrices=False)

    # Keep top n_factors
    U_k = U[:, :n_factors]              # (n_users, n_factors)
    sigma_k = sigma[:n_factors]          # (n_factors,)
    Vt_k = Vt[:n_factors, :]            # (n_factors, n_items)

    # Predicted ratings (low-rank reconstruction)
    predicted = (U_k * sigma_k) @ Vt_k + row_means
    return U_k, sigma_k, Vt_k
```

### Gradient Computation and Jacobian

```python
def manual_gradient_linear_layer(
    X: np.ndarray,      # (batch, in_features)
    W: np.ndarray,      # (in_features, out_features)
    b: np.ndarray,      # (out_features,)
    dL_dout: np.ndarray # (batch, out_features)  gradient from upstream
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    Backpropagation through a linear layer: out = X @ W + b

    Chain rule:
      dL/dW = X^T @ dL/dout      shape: (in_features, out_features)
      dL/dX = dL/dout @ W^T      shape: (batch, in_features)
      dL/db = sum over batch      shape: (out_features,)
    """
    dL_dW = X.T @ dL_dout           # (in_features, out_features)
    dL_dX = dL_dout @ W.T           # (batch, in_features)
    dL_db = dL_dout.sum(axis=0)     # (out_features,)
    return dL_dW, dL_dX, dL_db


def numerical_gradient_check(
    f,
    params: np.ndarray,
    h: float = 1e-5
) -> np.ndarray:
    """
    Finite-difference gradient approximation for gradient checking.
    Only use for debugging — O(n) forward passes, n = number of params.
    Central difference: (f(x+h) - f(x-h)) / 2h  (more accurate than one-sided)
    """
    grad = np.zeros_like(params)
    flat = params.flatten()
    for i in range(len(flat)):
        flat_plus = flat.copy()
        flat_plus[i] += h
        flat_minus = flat.copy()
        flat_minus[i] -= h
        grad.flat[i] = (f(flat_plus.reshape(params.shape)) -
                        f(flat_minus.reshape(params.shape))) / (2 * h)
    return grad
```

---

## 7. Real-World Examples

**Transformers — attention is matrix multiplication**: The attention operation computes scores = QK^T / sqrt(d_k), then output = softmax(scores) @ V. For a layer with d_model=4096, d_k=128, and sequence length 2048: the QK^T computation is (2048, 128) @ (128, 2048) = (2048, 2048) matrix — this is why attention is quadratic in sequence length.

**PCA for image compression**: MNIST digits are 28x28 = 784-dimensional. PCA to 50 components retains ~85% of variance. SVD on the 60000 x 784 training matrix (economy SVD) produces 50 components in seconds. Reconstruction error = ||X - X_k||_F^2 / ||X||_F^2 ≈ 15%.

**SVD for recommendation**: Netflix prize winning solution used SVD++ with 50 latent factors on a 480,000 user x 17,770 movie rating matrix. Singular values decayed exponentially — the top 50 captured most signal; remaining were noise.

**Gradient exploding in RNNs**: When multiplying the same weight matrix W repeatedly during BPTT (backpropagation through time), eigenvalues > 1 cause exponential gradient growth; eigenvalues < 1 cause vanishing. The spectral radius (largest eigenvalue magnitude) of W must be <= 1 for stable RNN training — this is why LSTMs use gating to control gradient flow.

---

## 8. Tradeoffs

| Operation | Time Complexity | Space Complexity | Notes |
|-----------|----------------|-----------------|-------|
| Matrix multiply (n x n) | O(n^3) | O(n^2) | Strassen: O(n^2.807) in theory |
| Matrix inverse | O(n^3) | O(n^2) | Never explicitly compute in practice; solve system instead |
| Full SVD (m x n) | O(min(m,n) * m * n) | O(m*n) | Economy SVD cheaper |
| Eigendecomposition (n x n) | O(n^3) | O(n^2) | eigh faster for symmetric |
| Gradient of scalar loss | O(forward pass) | O(activations stored) | Reverse-mode autodiff |

| Norm Type | Promotes | Disadvantage |
|-----------|----------|-------------|
| L1 | Sparsity (Lasso) | Non-differentiable at zero |
| L2 | Small but nonzero weights | Doesn't produce exact zeros |
| Nuclear | Low-rank matrices | Expensive to compute (needs SVD) |

---

## 9. When to Use / When NOT to Use

**Use eigendecomposition when**:
- Matrix is symmetric (covariance matrices, Laplacian matrices in graph ML)
- You need the principal directions of variance (PCA)
- Analyzing spectral properties of weight matrices

**Use SVD when**:
- Matrix is non-square (most real-world matrices)
- Doing dimensionality reduction (safer than eigendecomposition on non-PSD matrices)
- Matrix factorization for recommendations
- Computing pseudoinverse: A^+ = V Sigma^+ U^T

**Do NOT use matrix inverse explicitly**: Solving Ax = b via A^{-1}b is numerically unstable and twice as expensive as LU decomposition. Use `np.linalg.solve(A, b)` instead.

**Use L1 norm for regularization when**: you suspect many features are irrelevant and want a sparse model (feature selection implicit in Lasso).

**Use L2 norm for regularization when**: all features likely contribute and you want to prevent any single weight from dominating (Ridge/weight decay in neural networks).

---

## 10. Common Pitfalls

**Pitfall 1 — Shape errors in batched matrix ops**: A team trained a model where the gradient update silently applied to the wrong axis. The code was `W += lr * grad` where grad had shape (out, in) but W had shape (in, out). NumPy broadcast made it "work" but optimized in the wrong direction. Always assert shapes explicitly:

```python
# Broken: silent wrong-axis update
W += lr * grad   # may broadcast incorrectly if shapes mismatch

# Fixed: assert before update
assert grad.shape == W.shape, f"Gradient shape {grad.shape} != W shape {W.shape}"
W += lr * grad
```

**Pitfall 2 — Using eig instead of eigh for covariance matrices**: `np.linalg.eig` on a symmetric matrix can return complex eigenvalues due to floating point errors. `np.linalg.eigh` exploits symmetry, is faster (O(n^3) but with smaller constant), and always returns real eigenvalues. Production PCA code was using `eig` on a 10,000 x 10,000 covariance matrix, received complex eigenvectors, and the downstream classifier had NaN losses.

**Pitfall 3 — Not centering data before PCA**: PCA via SVD on un-centered data computes the first principal component as roughly the mean direction, not the direction of maximum variance. A data pipeline skipped `X -= X.mean(axis=0)` because the features "looked small." The top principal component was uninformative, and models trained on the components performed no better than random on the validation set.

**Pitfall 4 — Gradient accumulation shape mismatch with broadcasting**: In a model with bias term `b` of shape `(out_features,)`, the gradient `dL/db = dL_dout.sum(axis=0)` must sum over the batch axis. A bug that forgot the sum resulted in `dL/db` having shape `(batch, out_features)` — NumPy broadcast the addition `b += lr * dL_db` by expanding b along the batch axis, updating b to shape `(batch, out_features)`. The model appeared to train (loss decreased on batch 0) but crashed on batch 1 with a shape error.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| NumPy | Core linear algebra: `np.linalg.svd`, `eigh`, `solve`, `norm` |
| SciPy | Sparse SVD (`scipy.sparse.linalg.svds`), advanced decompositions |
| PyTorch | Autograd for gradients; `torch.linalg` mirrors NumPy API |
| JAX | Functional autograd (`jax.grad`, `jax.jacobian`); JIT compilation |
| LAPACK | Underlying Fortran library behind NumPy/SciPy linear algebra |
| cuBLAS | GPU-accelerated BLAS (Basic Linear Algebra Subprograms); used internally by PyTorch |
| scikit-learn | `TruncatedSVD`, `PCA`, `LinearRegression` (uses LAPACK internally) |

---

## 12. Interview Questions with Answers

**Q: Why is matrix multiplication not commutative (AB ≠ BA)?**
Matrix multiplication is a composition of linear transformations, and the order in which you apply transformations matters — rotating then scaling is different from scaling then rotating. Additionally, AB may not even be defined if shapes don't match, while BA is defined only when the inner dimensions work out the other way. In neural networks, the order of weight matrices determines which transformation is applied first.

**Q: Why is computing a matrix inverse O(n^3), and when should you avoid it?**
Gaussian elimination, LU decomposition, and most inversion algorithms require O(n^3) floating point operations because each elimination step processes one row but affects all remaining rows. In practice, you almost never want the inverse explicitly: to solve Ax = b, `np.linalg.solve(A, b)` uses LU decomposition and is twice as fast and more numerically stable than computing `A^{-1} @ b`. The exception is when you need to solve the same system for many different right-hand sides — then it may be worth factorizing once.

**Q: What is the relationship between eigendecomposition and PCA?**
PCA finds the directions of maximum variance in a dataset. The covariance matrix C = X^T X / (n-1) is symmetric PSD, so eigendecomposition gives C = Q Lambda Q^T where eigenvectors (columns of Q) are the principal directions and eigenvalues are the corresponding variances. Projecting data onto the top-k eigenvectors gives the best rank-k linear dimensionality reduction in terms of minimizing reconstruction error.

**Q: Why does SVD always exist but eigendecomposition does not?**
Eigendecomposition A = Q Lambda Q^{-1} requires n linearly independent eigenvectors, which fails for defective matrices (repeated eigenvalues with insufficient eigenvectors). SVD uses two separate orthogonal bases (U and V) rather than a single basis, and the singular values are always real and non-negative. This means SVD exists for any real matrix, including non-square matrices, making it universally applicable.

**Q: What is the chain rule in the context of backpropagation?**
The chain rule states d(f(g(x)))/dx = f'(g(x)) * g'(x). In a neural network with layers L1, L2, ... Ln, the gradient of loss w.r.t. weights in layer L1 is the product of Jacobians from Ln back to L1. Reverse-mode autodiff computes these products efficiently in one backward pass because the loss is a scalar — each intermediate gradient is a vector, not a full Jacobian matrix.

**Q: What is the difference between a Jacobian and a Hessian?**
The Jacobian of a vector-valued function f: R^n -> R^m is the m x n matrix of first-order partial derivatives J_{ij} = df_i/dx_j. The Hessian of a scalar function f: R^n -> R is the n x n matrix of second-order partial derivatives H_{ij} = d^2f/(dx_i dx_j). The Hessian encodes curvature and is used in Newton's method. In deep learning, the Hessian is n x n where n can be billions — computing it exactly is infeasible, so diagonal approximations (Adam's second moment) or rank-1 approximations (L-BFGS) are used.

**Q: What does a singular value of zero in SVD tell you about the matrix?**
A zero singular value means the matrix does not span the full space — the rank of the matrix equals the number of non-zero singular values. If a weight matrix in a neural network has many near-zero singular values, its effective rank is low, indicating redundancy. This motivates LoRA (Low-Rank Adaptation) for fine-tuning: the weight update delta W is parameterized as AB (two low-rank matrices) because the fine-tuning updates tend to be low-rank.

**Q: How is the L1 norm related to sparse solutions in Lasso regression?**
The L1 ball (unit sphere in L1 norm) has corners at the coordinate axes. When you project the unconstrained minimum onto the L1 ball (or equivalently, minimize loss + lambda * ||w||_1), the optimum tends to land on a corner where many coordinates are exactly zero. This is a geometric argument: corners are the points on the L1 ball closest to most unconstrained optima. L2 balls are smooth spheres with no corners, so the projected optimum has all non-zero (but small) coordinates.

**Q: Why do transformers use scaled dot-product attention (dividing by sqrt(d_k))?**
Without scaling, the dot products QK^T grow in magnitude as d_k increases because each of the d_k dimensions contributes to the sum. For d_k = 64, the standard deviation of the dot product (for unit-variance Q and K) is sqrt(64) = 8. Passing large values through softmax pushes the output into regions of near-zero gradient (saturation), making training slow. Dividing by sqrt(d_k) normalizes the variance back to 1, keeping softmax in its informative gradient regime.

**Q: What is the Frobenius norm and when is it used in ML?**
The Frobenius norm of a matrix A is ||A||_F = sqrt(sum_{i,j} A_{ij}^2) = sqrt(trace(A^T A)) = sqrt(sum of squared singular values). It generalizes the L2 vector norm to matrices. In ML it appears in: weight decay regularization (||W||_F^2), measuring reconstruction error in SVD approximation (||A - A_k||_F^2), and as a loss function for matrix factorization problems.

**Q: How does the condition number of a matrix affect numerical stability?**
The condition number kappa(A) = sigma_max / sigma_min (ratio of largest to smallest singular value). A high condition number (ill-conditioned matrix) means small perturbations in the input cause large changes in the output — numerical errors are amplified. In linear systems Ax = b with high kappa(A), even double-precision arithmetic may give wrong answers. Feature scaling (normalizing inputs) reduces the condition number of the data matrix X, which is why standardization improves the convergence of gradient descent.

---

## 13. Best Practices

- Always assert tensor shapes at layer boundaries during development; remove assertions in production with a flag.
- Use `np.linalg.solve(A, b)` instead of `np.linalg.inv(A) @ b` for linear systems.
- Prefer `eigh` over `eig` for symmetric matrices (faster, numerically stable, always real eigenvalues).
- Use economy/thin SVD (`full_matrices=False`) for large matrices; full SVD unnecessarily computes zero singular values.
- Center data before PCA; failure to center is one of the most common PCA bugs in data pipelines.
- Run gradient checks (numerical vs analytical) at the start of a new model implementation; check relative error < 1e-5.
- Monitor the singular value spectrum of weight matrices during training; rapid collapse (near-zero singular values) indicates rank collapse and can precede training instability.
- For batched matrix operations, use einsum for clarity: `np.einsum('bi,ij->bj', X, W)` is unambiguous about which axes are contracted.
- Normalize gradients by batch size when accumulating gradients across micro-batches to keep effective learning rate consistent.

---

## 14. Case Study

**Problem**: A recommendation team has a user-item rating matrix with 100,000 users, 50,000 items, and 2% density (most entries are missing). They need to predict ratings for unseen user-item pairs to power a recommendation engine. Training a deep neural network is too slow for their 4-hour daily retraining window.

**Solution — Truncated SVD collaborative filtering**:

```python
import numpy as np
from scipy.sparse import csr_matrix
from scipy.sparse.linalg import svds

def build_svd_recommender(
    user_ids: np.ndarray,        # shape (n_ratings,)
    item_ids: np.ndarray,        # shape (n_ratings,)
    ratings: np.ndarray,         # shape (n_ratings,)
    n_users: int,
    n_items: int,
    n_factors: int = 50
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """
    Sparse truncated SVD for collaborative filtering.
    Uses scipy.sparse.linalg.svds — O(n_factors * nnz) instead of O(n_users * n_items).
    """
    # Build sparse rating matrix
    R = csr_matrix((ratings, (user_ids, item_ids)), shape=(n_users, n_items))

    # Mean-center by user (subtract each user's average rating)
    user_means = np.array(R.sum(axis=1) / (R != 0).sum(axis=1)).flatten()
    # Only subtract mean where ratings exist
    R_centered = R.copy().astype(float)
    rows, cols = R.nonzero()
    R_centered[rows, cols] -= user_means[rows]

    # Truncated SVD: only compute top n_factors singular values
    # svds returns in ascending order — reverse for descending
    U, sigma, Vt = svds(R_centered, k=n_factors)
    U = U[:, ::-1]
    sigma = sigma[::-1]
    Vt = Vt[::-1, :]

    return U, sigma, Vt, user_means


def predict_rating(
    user_idx: int,
    item_idx: int,
    U: np.ndarray,
    sigma: np.ndarray,
    Vt: np.ndarray,
    user_means: np.ndarray
) -> float:
    """Predict a single user-item rating."""
    # User latent vector: U[user] * sigma  (element-wise)
    user_vec = U[user_idx, :] * sigma         # (n_factors,)
    item_vec = Vt[:, item_idx]                 # (n_factors,)
    predicted = float(np.dot(user_vec, item_vec)) + user_means[user_idx]
    return float(np.clip(predicted, 1.0, 5.0))
```

**Results**: Truncated SVD with 50 factors ran in 8 minutes on CPU (vs 4+ hours for matrix ALS). RMSE on held-out ratings: 0.89. Full SVD would have taken 3 hours and given the same top-50-factor reconstruction. The key insight was using `scipy.sparse.linalg.svds` which operates on the sparse matrix directly, avoiding materializing the dense 100k x 50k matrix (40 GB for float64).

**Lesson**: Choosing the right decomposition (sparse truncated SVD vs full dense SVD) reduced wall-clock time by 22x with no accuracy loss, enabling daily retraining within the operational window.
