# Linear and Logistic Regression — Deep Dive

## 1. Concept Overview

Linear models are the foundation of supervised machine learning. They assume the target is either a linear combination of the input features (regression) or that the log-odds of the target is a linear combination of features (logistic regression). Despite their simplicity, linear models are competitive in many production settings because of their interpretability, training speed, and well-understood regularization behavior.

**Linear Regression** learns a weight vector w and bias b such that the prediction y_hat = w^T x + b minimizes the sum of squared residuals over training data.

**Logistic Regression** passes the linear combination through a sigmoid function to produce a probability: P(y=1 | x) = sigma(w^T x + b), where sigma(z) = 1 / (1 + exp(-z)).

---

## 2. Intuition

One-line analogy: linear regression fits the best ruler through your data points; logistic regression fits the best S-curve to separate two clouds of points.

Mental model for regularization: think of the weight vector as a rubber band attached to the origin. L2 (Ridge) applies uniform tension, shrinking all weights toward zero but never to exactly zero. L1 (Lasso) applies tension that can collapse individual weights to exactly zero, performing automatic feature selection.

Key insight: logistic regression does NOT assume the features are Gaussian or independent (that is Naive Bayes). It only assumes the log-odds are linear in the features — a much weaker assumption.

---

## 3. Core Principles

**Ordinary Least Squares (Linear Regression)**:
```
Loss = (1/n) * sum_i (y_i - w^T x_i)^2
Optimal solution (closed form): w* = (X^T X)^{-1} X^T y
```
Existence of closed form requires X^T X to be invertible (no perfect multicollinearity and n >= d).

**Cross-Entropy Loss (Logistic Regression)**:
```
Loss = -(1/n) * sum_i [y_i * log(p_i) + (1-y_i) * log(1-p_i)]
```
where p_i = sigma(w^T x_i). This is the negative log-likelihood of a Bernoulli model. No closed-form solution — requires iterative optimization.

**Maximum Likelihood Estimation**: both OLS (under Gaussian noise) and logistic regression (under Bernoulli likelihood) are instances of MLE. The squared error loss IS the negative log-likelihood under Gaussian noise.

**Convexity**: both loss functions are convex in w, guaranteeing that gradient descent finds the global optimum.

---

## 4. Types / Architectures / Strategies

### 4.1 Solving Linear Regression

| Method | Complexity | Use When |
|--------|-----------|----------|
| Normal Equation (closed form) | O(nd^2 + d^3) | d < 10,000 and n fits in memory |
| Batch Gradient Descent | O(nd * iter) | Large d, full-batch feasible |
| Stochastic Gradient Descent | O(d * iter * n) | Very large n, online learning |
| Mini-Batch Gradient Descent | O(d * batch * iter) | Most practical; default in deep learning |

The d^3 term in the Normal Equation comes from inverting the d x d matrix X^T X. For d > 10,000 this becomes prohibitive.

### 4.2 Regularization Variants

| Method | Penalty | Effect | Hyperparameter |
|--------|---------|--------|----------------|
| Ridge (L2) | lambda * ||w||^2 | Shrinks all weights; handles multicollinearity | alpha (sklearn), C=1/alpha (LogisticRegression) |
| Lasso (L1) | lambda * ||w||_1 | Sparse weights; automatic feature selection | alpha |
| ElasticNet | a * ||w||_1 + b * ||w||^2 | Combines L1 and L2; groups correlated features | l1_ratio, alpha |

**C parameter in sklearn LogisticRegression**: C = 1 / lambda. Smaller C = stronger regularization. Default C=1.0.

### 4.3 Logistic Regression Variants

**Binary logistic regression**: sigmoid output, binary cross-entropy loss.

**Multinomial (softmax) regression**: for K classes, learn K weight vectors. Prediction:
```
P(y=k | x) = exp(w_k^T x) / sum_j exp(w_j^T x)
```

**One-vs-Rest (OvR)**: train K binary classifiers, predict class with highest probability. Default for most sklearn solvers.

**Ordinal logistic regression**: for ordered categories (low/medium/high), uses proportional odds model.

---

## 5. Architecture Diagrams

### 5.1 Linear Regression — Forward Pass and Loss

```
Input x = [x1, x2, ..., xd]
        |
        v  (dot product + bias)
    w^T x + b = y_hat (scalar)
        |
        v  (squared error)
    L = (y - y_hat)^2
        |
        v  (gradient)
    dL/dw = -2(y - y_hat) * x
        |
        v  (weight update)
    w <- w - lr * dL/dw
```

### 5.2 Logistic Regression — Sigmoid and Cross-Entropy

```
Input x = [x1, x2, ..., xd]
        |
        v  (linear combination)
    z = w^T x + b    (logit / log-odds)
        |
        v  (sigmoid activation)
    p = 1 / (1 + exp(-z))     (probability, range 0..1)
        |
        v  (cross-entropy loss)
    L = -[y * log(p) + (1-y) * log(1-p)]
        |
        v  (gradient — surprisingly clean)
    dL/dw = (p - y) * x
```

### 5.3 Regularization Effect on Coefficients

```
No regularization      L2 Ridge               L1 Lasso

w1 = 12.4              w1 = 3.2               w1 = 4.1
w2 = -8.7              w2 = -2.1              w2 = 0.0   <- zeroed
w3 = 31.0              w3 = 7.8               w3 = 0.0   <- zeroed
w4 = -0.1              w4 = -0.1              w4 = 0.0   <- zeroed
w5 = 0.9               w5 = 0.4               w5 = 0.3

(large weights,         (all shrunk,           (sparse: automatic
 potential overfit)      no zeroing)            feature selection)
```

### 5.4 Multicollinearity Visualization

```
Without multicollinearity     With multicollinearity
(X^T X invertible)            (X^T X near-singular)

Loss landscape:               Loss landscape:
       *                             ~~~~
      ***    single well             ~~~~~~~~~~~
       *                             ~~~   ~~~
    clear minimum                   elongated valley
                                 many near-optimal w vectors
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Normal Equation vs Gradient Descent

```python
from __future__ import annotations

import numpy as np
from sklearn.datasets import make_regression
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LinearRegression, Ridge, Lasso, ElasticNet
from sklearn.metrics import mean_squared_error, r2_score


def normal_equation(X: np.ndarray, y: np.ndarray) -> np.ndarray:
    """
    Closed-form OLS solution: w = (X^T X)^{-1} X^T y
    Adds a bias column of ones to X.
    O(nd^2 + d^3) — impractical for d > 10,000.
    """
    X_b = np.column_stack([np.ones(len(X)), X])   # prepend bias column
    # lstsq is numerically stable (SVD internally); never use np.linalg.inv directly
    w, _, _, _ = np.linalg.lstsq(X_b, y, rcond=None)
    return w   # w[0] is bias, w[1:] are feature weights


def gradient_descent_linear(
    X: np.ndarray,
    y: np.ndarray,
    lr: float = 0.01,
    n_iter: int = 1000,
) -> tuple[np.ndarray, float]:
    """
    Batch gradient descent for linear regression.
    Returns (weight_vector, bias).
    """
    n, d = X.shape
    w = np.zeros(d)
    b = 0.0

    for i in range(n_iter):
        y_hat = X @ w + b
        residuals = y_hat - y
        grad_w = (2 / n) * X.T @ residuals
        grad_b = (2 / n) * residuals.sum()
        w -= lr * grad_w
        b -= lr * grad_b

        if i % 100 == 0:
            loss = np.mean(residuals ** 2)
            print(f"Iter {i:4d}  MSE={loss:.4f}")

    return w, b


def compare_regularization(
    n_samples: int = 500,
    n_features: int = 30,
    noise: float = 10.0,
) -> None:
    """
    Compare Ridge, Lasso, and ElasticNet on the same dataset.
    Demonstrates that Lasso produces sparse coefficients.
    """
    X, y, true_coef = make_regression(
        n_samples=n_samples,
        n_features=n_features,
        n_informative=10,       # only 10 of 30 features actually matter
        noise=noise,
        coef=True,
        random_state=42,
    )

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    models: dict[str, object] = {
        "OLS":        LinearRegression(),
        "Ridge":      Ridge(alpha=1.0),
        "Lasso":      Lasso(alpha=0.1, max_iter=5000),
        "ElasticNet": ElasticNet(alpha=0.1, l1_ratio=0.5, max_iter=5000),
    }

    for name, model in models.items():
        model.fit(X_train_s, y_train)
        y_pred = model.predict(X_test_s)
        rmse = mean_squared_error(y_test, y_pred) ** 0.5
        n_zero = int(np.sum(np.abs(model.coef_) < 1e-6))
        print(f"{name:12s}  RMSE={rmse:7.2f}  zero_coefs={n_zero}/{n_features}")
```

### 6.2 Logistic Regression — sklearn with Correct Settings

```python
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.metrics import classification_report, roc_auc_score
from sklearn.datasets import make_classification


def logistic_regression_full_example() -> None:
    """
    Correct logistic regression setup:
    - StandardScaler before LR (features on same scale)
    - max_iter=1000 (default 100 causes ConvergenceWarning on real data)
    - class_weight="balanced" for imbalanced classes
    """
    X, y = make_classification(
        n_samples=10_000,
        n_features=20,
        weights=[0.85, 0.15],   # imbalanced: 15% positive
        random_state=42,
    )

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    # --- WRONG: default max_iter=100 causes ConvergenceWarning ---
    bad_model = LogisticRegression(max_iter=100)   # do not do this
    import warnings
    with warnings.catch_warnings(record=True) as w_list:
        warnings.simplefilter("always")
        bad_model.fit(X_train, y_train)
        if w_list:
            print(f"WARNING caught: {w_list[0].category.__name__}")

    # --- CORRECT ---
    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("lr", LogisticRegression(
            C=1.0,
            penalty="l2",
            max_iter=1000,
            class_weight="balanced",
            solver="lbfgs",
            random_state=42,
        )),
    ])
    pipeline.fit(X_train, y_train)

    y_pred = pipeline.predict(X_test)
    y_proba = pipeline.predict_proba(X_test)[:, 1]

    print(classification_report(y_test, y_pred, target_names=["Negative", "Positive"]))
    print(f"AUC-ROC: {roc_auc_score(y_test, y_proba):.4f}")


def multinomial_logistic_regression() -> None:
    """
    Multi-class logistic regression using softmax.
    """
    from sklearn.datasets import load_iris

    iris = load_iris()
    X, y = iris.data, iris.target

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("lr", LogisticRegression(
            multi_class="multinomial",  # softmax instead of OvR
            solver="lbfgs",
            max_iter=1000,
            C=1.0,
        )),
    ])
    pipeline.fit(X_train, y_train)
    print(f"Iris test accuracy: {pipeline.score(X_test, y_test):.4f}")
```

### 6.3 Multicollinearity Detection via VIF

```python
import pandas as pd
from statsmodels.stats.outliers_influence import variance_inflation_factor


def compute_vif(X_df: pd.DataFrame) -> pd.DataFrame:
    """
    Variance Inflation Factor (VIF) measures multicollinearity.
    VIF = 1          : no correlation with other features
    VIF = 1..5       : moderate, acceptable
    VIF = 5..10      : high, investigate
    VIF > 10         : severe multicollinearity — drop or combine the feature
    """
    vif_data = pd.DataFrame()
    vif_data["feature"] = X_df.columns
    vif_data["VIF"] = [
        variance_inflation_factor(X_df.values, i)
        for i in range(X_df.shape[1])
    ]
    return vif_data.sort_values("VIF", ascending=False)


def handle_multicollinearity_demo() -> None:
    """
    Demonstrates that Ridge is robust to multicollinearity while OLS is not.
    """
    np.random.seed(42)
    n = 200

    # Create two nearly identical features: x1 and x2 = x1 + tiny noise
    x1 = np.random.randn(n)
    x2 = x1 + np.random.randn(n) * 0.01   # nearly perfectly correlated
    x3 = np.random.randn(n)               # independent feature
    X = np.column_stack([x1, x2, x3])
    y = 2 * x1 + 3 * x3 + np.random.randn(n) * 0.5

    from sklearn.linear_model import LinearRegression, Ridge

    ols = LinearRegression().fit(X, y)
    ridge = Ridge(alpha=10.0).fit(X, y)

    print("OLS coefficients:  ", np.round(ols.coef_, 2))
    # OLS: wildly unstable large +/- values for x1 and x2
    print("Ridge coefficients:", np.round(ridge.coef_, 2))
    # Ridge: sensible small values, stability restored
```

### 6.4 Coefficient Interpretation

```python
def interpret_logistic_coefficients(
    model: LogisticRegression,
    feature_names: list[str],
) -> pd.DataFrame:
    """
    Logistic regression coefficients represent change in log-odds.
    exp(coef) = odds ratio: for a 1-unit increase in feature,
    the odds of the positive class are multiplied by exp(coef).

    Example: coef=0.5 for 'age' means odds ratio = exp(0.5) = 1.65
    — each additional year of age multiplies fraud odds by 1.65x.
    """
    coefs = model.coef_[0]   # shape (n_features,) for binary
    odds_ratios = np.exp(coefs)

    df = pd.DataFrame({
        "feature": feature_names,
        "coefficient": coefs,
        "odds_ratio": odds_ratios,
    }).sort_values("odds_ratio", ascending=False)

    return df
```

---

## 7. Real-World Examples

**Credit Card Approval (Logistic Regression)**: FICO scoring models are logistic regression under the hood. Features: payment history (35%), credit utilization (30%), account age (15%), credit mix (10%), new inquiries (10%). Coefficients are fixed globally and updated quarterly. Regulators require the bank to provide the top 4 reasons for any rejection — logistic regression coefficient signs make this trivial.

**House Price Prediction (Ridge Regression)**: Zillow's Zestimate (in its early form) used Ridge regression with hundreds of property features. L2 regularization was critical because many features (square footage, lot size, number of rooms) are correlated. Ridge prevents individual correlated features from getting absurdly large compensating weights.

**A/B Test Uplift Modeling (Logistic Regression)**: E-commerce companies estimate the causal effect of promotional emails using logistic regression with an interaction term between treatment assignment and user features. The coefficients on the interaction terms are the estimated heterogeneous treatment effects.

---

## 8. Tradeoffs

| Aspect | Linear Regression | Logistic Regression | Ridge | Lasso |
|--------|------------------|--------------------|----|------|
| Output type | Continuous | Probability (0,1) | Continuous | Continuous |
| Closed form | Yes | No | Yes (w = (X^TX + lambdaI)^{-1}X^Ty) | No |
| Feature selection | No | No | No | Yes (sparse) |
| Handles multicollinearity | Poorly | Poorly | Well | Moderately |
| Interpretability | High | High | High | High (+ sparsity) |
| Computational cost | O(nd^2 + d^3) | O(nd*iter) | O(nd^2 + d^3) | O(nd*iter) |

---

## 9. When to Use / When NOT to Use

**Use linear regression when**:
- Target is continuous and the linear assumption is reasonable
- Interpretability is required (regulatory, clinical)
- Training data is limited (low variance estimator)
- Features are already informative (not needing complex transformations)

**Use logistic regression when**:
- Binary or multiclass output
- Probability calibration is important (output is used in downstream expected-value calculations)
- Sparse, high-dimensional features (text: use L1 / L2 penalty)
- Model must be auditable

**Do NOT use linear models when**:
- The decision boundary is highly non-linear (complex feature interactions)
- Features have strong non-linear relationships with the target that cannot be captured by polynomial features without exploding dimensionality
- Text or image raw inputs without learned embeddings

---

## 10. Common Pitfalls

**Pitfall 1 — sklearn LogisticRegression default max_iter=100**
Real datasets commonly require 500–2000 iterations to converge. The default 100 leaves the optimizer at a sub-optimal point and raises a ConvergenceWarning. Many pipelines suppress all warnings in Jupyter notebooks with `warnings.filterwarnings("ignore")`, masking this bug entirely. The "model" deployed is not the optimal logistic regression — it has worse AUC and miscalibrated probabilities. Fix: always set max_iter=1000 or higher; check that no ConvergenceWarning appears.

**Pitfall 2 — Not scaling features before logistic regression**
When features have wildly different scales (income in thousands vs. age in decades), the gradient update for the large-scale feature has a much larger magnitude, causing oscillation and slow convergence. StandardScaler before fitting solves this. Decision trees do not need scaling; logistic regression and SVM always do.

**Pitfall 3 — Interpreting logistic regression coefficients as linear effects on probability**
A positive coefficient does NOT mean the feature linearly increases the predicted probability. It means the log-odds increase linearly. The probability effect is non-linear (S-shaped, compressed near 0 and 1). Never tell a business stakeholder "a one-unit increase in feature X increases churn probability by 0.3" — that is only true at one specific baseline. Correct statement: "it multiplies the odds of churn by exp(0.3) = 1.35."

**Pitfall 4 — Using OLS for a binary target**
Some analysts run linear regression on a 0/1 outcome (Linear Probability Model). It can produce predictions outside [0, 1] and is heteroscedastic by construction. It is occasionally acceptable as a fast approximation but always replace with logistic regression for production systems.

**Pitfall 5 — Ignoring multicollinearity**
A team built a credit risk model with features: total_credit_limit, credit_utilization, outstanding_balance (= total_credit_limit * credit_utilization). Perfect multicollinearity. OLS assigned total_credit_limit a coefficient of +50,000 and credit_utilization a coefficient of -50,000 — meaningless individually but summing to the correct prediction. Any small change in training data caused the coefficients to flip signs. VIF was 8,500 for both features. Fix: remove one of the three correlated features or use Ridge.

**Pitfall 6 — Using Lasso when features are correlated**
Lasso has a tendency to arbitrarily select one feature from a group of correlated features and zero out the rest. This produces unstable feature selection — a different random seed drops a different feature. Use ElasticNet (l1_ratio=0.5 to 0.9) when you want sparsity but the features may be correlated.

---

## 11. Technologies & Tools

| Tool | Use |
|------|-----|
| sklearn LinearRegression / Ridge / Lasso / ElasticNet | Standard implementations |
| sklearn LogisticRegression | Binary and multiclass; supports L1, L2, ElasticNet penalties |
| statsmodels OLS / Logit | Full statistical inference: p-values, confidence intervals, AIC/BIC |
| scipy.stats | Correlation tests, hypothesis tests for coefficient significance |
| statsmodels VIF | Variance Inflation Factor for multicollinearity detection |
| sklearn CalibratedClassifierCV | Post-hoc probability calibration (Platt scaling, isotonic regression) |
| sklearn SGDClassifier | Online / stochastic gradient descent for logistic regression at scale |

---

## 12. Interview Questions with Answers

**Q: Derive the gradient of binary cross-entropy loss with respect to w in logistic regression.**
The loss for a single example is L = -[y log(sigma(z)) + (1-y) log(1 - sigma(z))], where z = w^T x. Using the chain rule: dL/dw = dL/dz * dz/dw. The key identity is d(sigma(z))/dz = sigma(z)(1 - sigma(z)). Working through the chain rule, dL/dz = sigma(z) - y = p - y. And dz/dw = x. Therefore dL/dw = (p - y) * x. This beautiful result means the gradient is simply the prediction error times the input.

**Q: Why does L1 regularization produce sparse models while L2 does not?**
Geometrically, the L1 constraint region is a diamond (in 2D) with corners on the axes. The optimal constrained solution (where the loss contour first touches the constraint region) is very likely to land at a corner, where one or more weights are exactly zero. The L2 constraint is a sphere — smooth everywhere — so the touching point is almost never at a zero. Algebraically, L1's subdifferential at zero includes zero, so the optimizer can stay at zero; L2's gradient at zero is zero, but any perturbation pulls the weight away.

**Q: What is the difference between Ridge and the Normal Equation, and how does Ridge handle multicollinearity?**
The Normal Equation is w = (X^T X)^{-1} X^T y. When features are collinear, X^T X becomes singular (or near-singular), making the inverse numerically unstable and the coefficients explode. Ridge adds a regularization term: w = (X^T X + alpha * I)^{-1} X^T y. Adding alpha to the diagonal makes the matrix strictly positive definite and always invertible, producing stable, shrunk coefficients. This is the closed-form Ridge solution.

**Q: When would you choose Lasso over Ridge?**
Choose Lasso when you believe only a small subset of features are truly predictive and you want automatic feature selection — Lasso will zero out irrelevant coefficients, producing a sparse model that is easier to interpret and faster at inference. Choose Ridge when features are correlated (Lasso arbitrarily picks one of a correlated pair) or when you want all features to contribute with small weights rather than hard zeros.

**Q: What is the VIF and what threshold indicates a problem?**
Variance Inflation Factor (VIF) for feature j is 1 / (1 - R^2_j), where R^2_j is the R-squared from regressing feature j on all other features. VIF = 1 means no correlation with other features. VIF = 5 is the soft warning threshold; VIF > 10 indicates severe multicollinearity and requires action (drop the feature, combine features, or switch to Ridge). VIF above 100 means near-perfect multicollinearity.

**Q: How do you interpret a logistic regression coefficient?**
The coefficient w_j represents the change in log-odds of the positive class per unit increase in feature x_j, holding all other features constant. Equivalently, exp(w_j) is the odds ratio: a one-unit increase in x_j multiplies the odds by exp(w_j). For binary features, exp(w_j) is the odds ratio comparing the two groups. Do not interpret coefficients as changes in probability — the probability effect is non-linear.

**Q: What is the difference between the sigmoid and softmax functions?**
Sigmoid is used for binary classification: it maps a single scalar to (0, 1), representing P(y=1). Softmax is used for multi-class classification with K classes: it maps a K-dimensional vector of logits to a K-dimensional probability vector summing to 1. Sigmoid is a special case of softmax with K=2. For multi-label classification (multiple independent binary outputs), apply sigmoid independently to each output — do not use softmax.

**Q: Why does logistic regression require iterative optimization while linear regression has a closed-form solution?**
Linear regression with MSE loss produces a quadratic function of w, so setting the gradient to zero yields a linear system of equations solvable in closed form (Normal Equation). Logistic regression's cross-entropy loss involves log(sigma(w^T x)), which is non-linear in w. Setting the gradient to zero does not produce a linear system — the equation has no closed-form solution. However, the loss is convex, so iterative methods (gradient descent, L-BFGS, Newton's method) are guaranteed to find the global optimum.

**Q: What is regularization path and how is it used for feature selection?**
The regularization path is the sequence of coefficient values as the regularization strength (alpha or 1/C) varies from very weak to very strong. For Lasso, as alpha increases, coefficients drop to zero one by one — the last ones to drop are the most important features. Plotting the path reveals feature importance and helps select the number of features to retain. sklearn's LassoCV automatically finds the optimal alpha via cross-validation across the path.

**Q: How do you handle a categorical feature with 1,000 distinct values in logistic regression?**
One-hot encoding 1,000 categories adds 999 binary features (dropping one for identifiability). With high cardinality, this leads to sparsity and potential overfitting. Better approaches: (1) target encoding — replace each category with its mean target value, then add regularization to prevent leakage; (2) frequency encoding — replace with log frequency; (3) embedding via a neural network if the dataset is large enough; (4) grouping rare categories into an "Other" bucket. Always apply Lasso or Ridge after any of these to handle the resulting sparsity or collinearity.

**Q: What is ElasticNet and when is it preferred over pure L1 or L2?**
ElasticNet combines L1 and L2 penalties: alpha * [l1_ratio * ||w||_1 + (1 - l1_ratio) * ||w||^2]. It handles correlated features better than pure Lasso (which arbitrarily selects one from a correlated group) while still producing sparse solutions. Preferred when: features are correlated AND you still want some sparsity; n << d (very high-dimensional data with correlated features, such as genomics). The l1_ratio parameter controls the mix: 0 = Ridge, 1 = Lasso.

**Q: How would you detect and handle non-linearity in a linear regression problem?**
Detection: plot residuals vs. fitted values — systematic patterns (U-shape, funnel) indicate non-linearity or heteroscedasticity. Also plot each feature vs. the target residuals. Handling options: (1) polynomial features (x^2, x^3, x1*x2 interactions) — sklearn PolynomialFeatures; (2) log/sqrt transformation of skewed features; (3) spline regression (piecewise polynomial, smooth at knots); (4) generalized additive models (GAMs) via pyGAM library. If non-linearity is pervasive and unstructured, switch to gradient boosted trees or a neural network.

**Q: What solver should you use for sklearn LogisticRegression and why?**
Solver choice depends on the dataset: lbfgs is the default and handles L2 penalty well for small-to-medium datasets; it supports multinomial (softmax). liblinear is fast for small datasets and supports L1 penalty but is limited to OvR multi-class. saga supports L1, L2, ElasticNet, is faster on large datasets (stochastic gradient), and supports multinomial. sag (without A) supports only L2 but is faster on very large datasets. Always check that your chosen penalty is supported by the chosen solver — sklearn raises an error if incompatible.

**Q: How does probability calibration work and when is it necessary?**
A model is well-calibrated if predicted probability 0.8 corresponds to an 80% empirical event rate. Logistic regression is generally well-calibrated; SVM and boosted trees are not — they tend to push probabilities toward 0 and 1. Calibration is necessary whenever the downstream system uses the raw probability (expected value calculations, risk scoring, multi-model ensemble). Fix with sklearn CalibratedClassifierCV: use Platt scaling (sigmoid fit) for small datasets or isotonic regression for larger ones.

**Q: Explain the difference between L-BFGS and gradient descent for optimizing logistic regression.**
Gradient descent makes small steps in the direction of the negative gradient, requiring O(1/epsilon^2) iterations for epsilon-accuracy. L-BFGS (Limited-memory Broyden–Fletcher–Goldfarb–Shanno) approximates the inverse Hessian (second-order information) using a fixed-size history of gradient vectors, achieving superlinear convergence — O(1/epsilon) iterations or better. L-BFGS uses more memory per iteration but converges in far fewer iterations. For logistic regression, L-BFGS (solver="lbfgs") is typically 5-20x faster than gradient descent in wall time.

**Q: How do you choose C (regularization strength) for logistic regression in production?**
Use k-fold cross-validation (typically 5-fold or 10-fold, stratified for imbalanced data) with a logarithmic grid: C in [0.0001, 0.001, 0.01, 0.1, 1, 10, 100]. sklearn's LogisticRegressionCV does this efficiently by exploiting the warm-start property of the solver. Optimize for your business metric — AUC-ROC for ranking, F1 for balanced precision/recall, log-loss for probability calibration. Once C is chosen, retrain on the full training set (train + validation) before final evaluation on the held-out test set.

---

## 13. Best Practices

1. Always use sklearn Pipeline so the scaler is fit exclusively on training data. This prevents data leakage and makes the pipeline directly serializable for deployment.

2. Set max_iter=1000 or higher for LogisticRegression. The default of 100 is too low for most real datasets. Check for ConvergenceWarning explicitly — do not suppress all warnings.

3. Use StandardScaler for logistic regression. Standardization puts features on the same scale, accelerates convergence, and makes regularization penalties comparable across features. Min-max scaling is an alternative but StandardScaler is generally preferable.

4. Detect multicollinearity with VIF before fitting. VIF > 10 means Ridge is likely better than OLS/Lasso. Report VIF alongside model coefficients to stakeholders.

5. Use statsmodels for statistical inference. sklearn does not report p-values or confidence intervals on coefficients. statsmodels Logit / OLS gives standard errors, z-scores, p-values, and AIC/BIC — critical for scientific and regulatory contexts.

6. Calibrate probabilities if using SVM or tree-based outputs downstream in logistic pipelines. Use CalibratedClassifierCV with method="sigmoid" (Platt) for smaller calibration sets and method="isotonic" for larger ones.

7. Apply class_weight="balanced" for imbalanced binary problems or pass sample_weight manually. This reweights the cross-entropy loss proportional to inverse class frequency, preventing the model from predicting all-negative.

8. Interpret coefficients in terms of odds ratios (exp(coef)) for binary logistic regression. Provide confidence intervals on the odds ratios — use statsmodels or bootstrap.

9. Use ElasticNet when features are correlated and you still want sparsity. Pure Lasso's arbitrary feature selection among correlated groups is a reproducibility problem (different seeds select different features).

10. Version and track every training run (feature set, hyperparameters, train/test split seed, metric results) in MLflow or a similar experiment tracker. This enables reproducibility and rollback.

---

## 14. Case Study

**Problem**: an insurance company needs a model to predict whether a submitted claim is fraudulent (binary classification). Approximately 3% of submitted claims are fraudulent. Regulatory requirement: the model must be explainable — every rejection must cite the top contributing features. Inference must complete in under 5ms per claim.

**Dataset**: 800,000 historical claims over 3 years. Features include: claim amount, claim type (11 categories), days since policy start, number of prior claims, geographic region, adjuster ID, time between incident and filing, relationship of claimant to policy holder.

**Pipeline**:
```
Raw claim JSON
    |
    v
Feature Engineering
    |--- log(claim_amount)              (right-skewed: log normalizes)
    |--- days_since_policy_start        (continuous)
    |--- prior_claims_count             (count feature, capped at 10)
    |--- filing_delay_days              (days between incident and filing)
    |--- claim_type (OHE, 11 categories)
    |--- region (target-encoded, 50 states)
    |
    v
StandardScaler (fit on 80% training portion of 2-year window)
    |
    v
LogisticRegression(
    C=0.01,           # strong regularization — 50+ features, prevent overfit
    penalty="l2",
    max_iter=1000,
    class_weight="balanced",  # 3% positive rate
    solver="lbfgs",
)
    |
    v
Threshold calibration: 0.30 (custom threshold based on cost matrix:
    FP cost = $200 investigator time; FN cost = $4,500 average fraud loss)
```

**Results**:
- AUC-ROC: 0.87 on held-out test set (temporal split: train on years 1-2, test on year 3)
- Precision at threshold 0.30: 0.42
- Recall at threshold 0.30: 0.81
- Expected value gain vs. no model: +$3.1M per quarter

**Explainability**: top-3 features by absolute coefficient magnitude: filing_delay_days (+ risk if > 30 days), log_claim_amount (+ risk if > $15k), prior_claims_count (+ risk if > 2). Every rejected claim generates a human-readable explanation citing these factors — satisfying the regulatory requirement.

**Inference latency**: the entire pipeline (feature engineering + scaler transform + logistic regression forward pass) runs in 1.8ms on a single CPU core. Deployed as a FastAPI microservice. The simplicity of logistic regression was decisive — no GPU, no model server, no batching complexity.

**Lesson**: the interpretability constraint eliminated gradient boosting (which would have scored AUC 0.91 but requires SHAP for post-hoc explanation, which does not satisfy the regulation). Logistic regression at AUC 0.87 with native coefficient interpretability was the correct production choice.
