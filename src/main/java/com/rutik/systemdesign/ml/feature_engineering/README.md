# Feature Engineering

## 1. Concept Overview

Feature engineering is the process of transforming raw data into representations that machine learning algorithms can learn from effectively. It encompasses encoding categorical variables, transforming numeric distributions, imputing missing values, selecting informative features, and constructing new features from existing ones. The quality of features often matters more than the choice of algorithm — a linear model with well-engineered features routinely outperforms a gradient boosted tree with raw inputs.

Core tasks:
- **Categorical encoding**: convert categories to numbers without introducing false ordinal relationships
- **Numeric transformations**: correct skew, stabilize variance, bring features to comparable scales
- **Missing value imputation**: fill gaps without leaking target information
- **Feature selection**: identify and retain only features that add predictive signal
- **Feature construction**: create interaction terms, ratio features, and domain-derived attributes
- **Feature stores**: manage, version, and serve features at scale in production

---

## 2. Intuition

One-line analogy: feature engineering is like translating a handwritten letter into a format a computer can parse — the underlying meaning stays the same, but the representation becomes machine-readable and comparable.

Mental model:
- Encoding = giving the algorithm a language to read categories
- Scaling = making sure all words in that language are the same font size
- Imputation = filling in torn pages without guessing the wrong content
- Feature selection = removing filler words that add noise but no meaning

Why it matters: most algorithms operate on numeric vectors. A raw pandas DataFrame with strings, nulls, and mismatched scales will either crash or produce garbage predictions. Thoughtful feature engineering directly controls the signal-to-noise ratio the model sees.

Key insight: data leakage is the most dangerous feature engineering mistake — applying transformations that use test-set or target information during training produces optimistic metrics that collapse in production.

---

## 3. Core Principles

1. **No leakage**: all transformers must be fit on training data only, then applied to validation/test sets. Use sklearn Pipelines to enforce this mechanically.
2. **Preserve distribution shape awareness**: know whether a transformation assumes normality (Box-Cox requires positive values), handles zeros (log1p vs log), or is sensitive to outliers (StandardScaler vs RobustScaler).
3. **High cardinality requires special handling**: one-hot encoding a column with 500 unique values creates 500 sparse binary columns — curse of dimensionality applies immediately.
4. **Missing values carry signal**: always consider adding a binary `feature_was_missing` indicator before imputing; the fact that a value is absent is often informative (e.g., "no prior purchase" vs "purchase amount unknown").
5. **Feature selection is regularization**: irrelevant features add noise, inflate computation, and can cause multicollinearity. Select aggressively, then add back if model performance drops.

---

## 4. Types / Architectures / Strategies

### Categorical Encoding

| Method               | When to use                                  | Cardinality | Risk |
|----------------------|----------------------------------------------|-------------|------|
| One-hot encoding     | Nominal, low cardinality (< 15 categories)   | Low         | Curse of dimensionality for high-card |
| Ordinal encoding     | Ordered categories (cold < warm < hot)       | Any         | Wrong order = wrong signal |
| Label encoding       | Tree-based models only, ordinal-ish          | Any         | Linear models interpret numbers as ordered |
| Target encoding      | High cardinality (> 15–50)                   | High        | Data leakage without CV folds |
| Binary / Hash encoding| High cardinality, space-efficient           | Very high   | Hash collisions |
| Embedding (neural)   | Very high cardinality (IDs, zip codes)       | 1M+         | Requires neural model |

### Numeric Transformations

| Transform         | Purpose                           | Requirement         |
|-------------------|-----------------------------------|---------------------|
| log / log1p       | Right-skewed distributions        | Values >= 0 (log1p handles zeros) |
| sqrt              | Moderate right skew, count data   | Values >= 0         |
| Box-Cox           | Optimal power transform (parameterized) | Values > 0    |
| Yeo-Johnson       | Box-Cox extended to negatives     | Any                 |
| StandardScaler    | Zero mean, unit variance          | Approx. normal distribution |
| MinMaxScaler      | Scale to [0, 1]                   | Bounded data; sensitive to outliers |
| RobustScaler      | Scale using median and IQR        | Outlier-heavy data  |

### Missing Value Imputation

| Method               | Best for                          | Risk |
|----------------------|-----------------------------------|------|
| Mean imputation      | Symmetric continuous, few nulls   | Distorts variance |
| Median imputation    | Skewed continuous, outliers       | Ignores feature correlations |
| Mode imputation      | Categorical features              | Overrepresents most frequent |
| KNN imputer          | Correlated features, small n      | Slow on large n |
| Iterative imputer    | Complex multivariate relationships | Slow, can overfit |
| Constant fill        | Categorical "unknown" class       | Creates new category |
| Indicator + impute   | Missing mechanism is informative  | Doubles feature count |

### Feature Selection

| Strategy   | Method                              | Pros               | Cons |
|------------|-------------------------------------|--------------------|------|
| Filter     | Pearson correlation, mutual info, chi-squared | Fast, model-agnostic | Ignores interactions |
| Wrapper    | RFE, RFECV                          | Finds feature subsets | Expensive |
| Embedded   | L1 penalty, tree feature importance | Efficient, integrated | Model-specific |
| Permutation| Permutation importance on held-out set | Reliable for any model | Requires trained model |

---

## 5. Architecture Diagrams

### sklearn Pipeline (prevents leakage)

```
Training data (X_train, y_train)
          |
          v
  ColumnTransformer
  +------------------+---------------------------+
  | Numeric pipeline |  Categorical pipeline      |
  | SimpleImputer    |  SimpleImputer(strategy=   |
  |   (median)       |    "most_frequent")        |
  | StandardScaler   |  OneHotEncoder(handle_     |
  |                  |    unknown="ignore")        |
  +------------------+---------------------------+
          |
          v
  (Optional) SelectFromModel / RFECV
          |
          v
  Estimator (LogisticRegression, XGBoost, etc.)
          |
          v
  pipeline.fit(X_train, y_train)      <-- only training data sees fit()
  pipeline.predict(X_test)            <-- test data only sees transform()
```

### Target Encoding with Out-of-Fold (prevents leakage)

```
Training fold (4/5 of train)
    |
    v
Compute mean(target) per category
    |
    v
Apply to validation fold (1/5 of train)   <-- never seen during encoding fit
    |
    v
Repeat for all 5 folds
    |
    v
Final test set: encode using full train set mean(target)
```

### Feature Store Architecture

```
Raw data sources (DB, events, APIs)
          |
    [Offline pipeline — batch]
          |
          v
    Feature computation
    (e.g., 30-day rolling avg spend)
          |
          v
    Feature Store
    +---------------------------+
    | Feature Registry          |  <-- metadata: name, version, owner
    | Offline Store (parquet)   |  <-- training data
    | Online Store (Redis/DDB)  |  <-- low-latency serving (<5ms)
    +---------------------------+
          |                |
     Training jobs     Prediction service
     (batch fetch)     (point lookup by entity_id)
```

---

## 6. How It Works — Detailed Mechanics

```python
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.feature_selection import (
    RFECV,
    SelectFromModel,
    SelectKBest,
    mutual_info_classif,
)
from sklearn.impute import IterativeImputer, KNNImputer, SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import StratifiedKFold, cross_val_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import (
    MinMaxScaler,
    OneHotEncoder,
    OrdinalEncoder,
    PowerTransformer,
    RobustScaler,
    StandardScaler,
)
from typing import Any


# ── Target Encoding (out-of-fold to prevent leakage) ──────────────────────────

class TargetEncoder(BaseEstimator, TransformerMixin):
    """
    Mean-target encoding with smoothing and out-of-fold fitting.

    Formula per category c:
        encoded(c) = (n_c * mean_c + m * global_mean) / (n_c + m)

    where m (smoothing) controls how much we trust the global mean
    vs the category-level mean. m=10 is a common starting point.

    High cardinality (> ~50 unique values): use this instead of one-hot.
    CRITICAL: fit only on training fold, never on full dataset before split.
    """

    def __init__(self, smoothing: float = 10.0, min_samples_leaf: int = 1) -> None:
        self.smoothing = smoothing
        self.min_samples_leaf = min_samples_leaf
        self.encoding_map_: dict[str, dict[Any, float]] = {}
        self.global_mean_: float = 0.0

    def fit(self, X: pd.DataFrame, y: pd.Series) -> "TargetEncoder":
        self.global_mean_ = float(y.mean())
        for col in X.columns:
            stats = y.groupby(X[col]).agg(["mean", "count"])
            # Smoothed encoding: pull low-count categories toward global mean
            smoother = 1 / (1 + np.exp(-(stats["count"] - self.min_samples_leaf) / self.smoothing))
            encoded = smoother * stats["mean"] + (1 - smoother) * self.global_mean_
            self.encoding_map_[col] = encoded.to_dict()
        return self

    def transform(self, X: pd.DataFrame) -> np.ndarray:
        result = X.copy()
        for col in X.columns:
            # Unseen categories get global mean (avoids NaN at inference)
            result[col] = X[col].map(self.encoding_map_[col]).fillna(self.global_mean_)
        return result.values.astype(float)


# ── sklearn Pipeline (the canonical leakage-free pattern) ─────────────────────

def build_pipeline(
    numeric_cols: list[str],
    low_card_cat_cols: list[str],   # < 15 unique values
    high_card_cat_cols: list[str],  # >= 15 unique values
) -> Pipeline:
    """
    Numeric: median imputation + RobustScaler (handles outliers better than Standard).
    Low-card categorical: mode imputation + one-hot.
    High-card categorical: TargetEncoder (must be fit inside CV fold, not here directly
        — use TransformedTargetRegressor or custom CV for target-encoded columns).
    """
    numeric_transformer = Pipeline([
        ("impute", SimpleImputer(strategy="median")),
        ("scale", RobustScaler()),
    ])

    low_cat_transformer = Pipeline([
        ("impute", SimpleImputer(strategy="most_frequent")),
        ("encode", OneHotEncoder(handle_unknown="ignore", sparse_output=True)),
    ])

    # For demo purposes — in production wrap with CV-aware target encoding
    high_cat_transformer = Pipeline([
        ("impute", SimpleImputer(strategy="most_frequent")),
        ("encode", OrdinalEncoder(handle_unknown="use_encoded_value", unknown_value=-1)),
    ])

    preprocessor = ColumnTransformer([
        ("num", numeric_transformer, numeric_cols),
        ("low_cat", low_cat_transformer, low_card_cat_cols),
        ("high_cat", high_cat_transformer, high_card_cat_cols),
    ], remainder="drop")

    pipeline = Pipeline([
        ("preprocess", preprocessor),
        ("clf", GradientBoostingClassifier(n_estimators=200, random_state=42)),
    ])
    return pipeline


# ── Missing value indicator pattern ───────────────────────────────────────────

def add_missing_indicators(df: pd.DataFrame, cols: list[str]) -> pd.DataFrame:
    """
    For columns where missingness is informative (e.g., credit_score absent
    often means no credit history), add binary flag before imputing.
    """
    for col in cols:
        if df[col].isna().any():
            df[f"{col}_was_missing"] = df[col].isna().astype(int)
    return df


# ── Feature selection strategies ──────────────────────────────────────────────

def select_features_filter(
    X: np.ndarray,
    y: np.ndarray,
    k: int = 20,
) -> tuple[np.ndarray, np.ndarray]:
    """
    Filter method: SelectKBest with mutual information.
    Mutual info captures non-linear dependencies (unlike Pearson).
    Does NOT consider interactions between features.
    """
    selector = SelectKBest(score_func=mutual_info_classif, k=k)
    X_selected = selector.fit_transform(X, y)
    selected_indices = selector.get_support(indices=True)
    print(f"Selected {len(selected_indices)} features out of {X.shape[1]}")
    return X_selected, selected_indices


def select_features_rfecv(
    X: np.ndarray,
    y: np.ndarray,
    estimator: Any = None,
    cv_folds: int = 5,
) -> tuple[np.ndarray, np.ndarray]:
    """
    RFECV: wrapper method.
    Repeatedly fits estimator, removes weakest feature by coefficient/importance.
    Cross-validates at each step to find optimal subset.
    Expensive but gives the most reliable subset.
    Use a fast estimator (LogisticRegression, LinearSVC) to keep runtime manageable.
    """
    if estimator is None:
        estimator = LogisticRegression(max_iter=1000, C=1.0)

    rfecv = RFECV(
        estimator=estimator,
        step=1,
        cv=StratifiedKFold(n_splits=cv_folds),
        scoring="roc_auc",
        min_features_to_select=5,
        n_jobs=-1,
    )
    X_selected = rfecv.fit_transform(X, y)
    print(f"Optimal number of features: {rfecv.n_features_}")
    print(f"CV score at optimal: {rfecv.cv_results_['mean_test_score'][rfecv.n_features_-1]:.4f}")
    return X_selected, rfecv.get_support(indices=True)


def select_features_l1(
    X: np.ndarray,
    y: np.ndarray,
    C: float = 0.1,
) -> tuple[np.ndarray, np.ndarray]:
    """
    L1-based embedded selection.
    L1 (Lasso) penalty drives weak feature coefficients to exactly zero.
    C=0.1 is moderately aggressive; lower C removes more features.
    """
    lasso = LogisticRegression(penalty="l1", solver="liblinear", C=C, max_iter=500)
    selector = SelectFromModel(lasso, threshold="mean")
    X_selected = selector.fit_transform(X, y)
    selected_indices = selector.get_support(indices=True)
    print(f"L1 selected {len(selected_indices)} features")
    return X_selected, selected_indices


# ── Numeric transformations ───────────────────────────────────────────────────

def transform_numeric(
    df: pd.DataFrame,
    right_skewed_cols: list[str],
    scale_cols: list[str],
) -> pd.DataFrame:
    """
    Log1p for right-skewed non-negative features (income, page views, amount).
    log1p(x) = log(x + 1) — handles zero values gracefully.
    StandardScaler after transformation for gradient-based models.
    """
    df = df.copy()
    for col in right_skewed_cols:
        assert (df[col] >= 0).all(), f"{col} has negative values; use Yeo-Johnson instead"
        df[col] = np.log1p(df[col])

    # Yeo-Johnson works on negative values too
    pt = PowerTransformer(method="yeo-johnson", standardize=True)
    # pt.fit_transform(df[scale_cols])  # would apply in pipeline context

    return df


# ── Interaction features ───────────────────────────────────────────────────────

def create_interaction_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Manual interaction features often beat polynomial expansion for interpretability.
    Ratio features encode domain relationships (e.g., debt-to-income).
    """
    df = df.copy()
    if "debt" in df.columns and "income" in df.columns:
        df["debt_to_income"] = df["debt"] / (df["income"] + 1e-6)
    if "clicks" in df.columns and "impressions" in df.columns:
        df["ctr"] = df["clicks"] / (df["impressions"] + 1)
    if "total_spend" in df.columns and "num_orders" in df.columns:
        df["avg_order_value"] = df["total_spend"] / (df["num_orders"] + 1)
    return df
```

---

## 7. Real-World Examples

**Credit scoring (target encoding + indicator):** A fintech startup had a `zip_code` column with 12,000 unique values. One-hot encoding created a 12,000-column sparse matrix that made gradient boosting 20x slower with no accuracy gain. Replacing with smoothed target encoding (mean default rate per zip, smoothed toward global mean) reduced feature count by 99.9% while improving AUC from 0.782 to 0.791.

**E-commerce churn (log transform):** Customer purchase amounts spanned 4 orders of magnitude ($1 to $50,000). A logistic regression trained on raw `purchase_amount` converged poorly and gave large weight to extreme purchases. After `log1p` transform, residuals normalized and coefficient for purchase_amount became interpretable (one unit log increase correlated with 12% reduced churn probability).

**Healthcare (KNN imputation):** Patient lab results had 15% random missing values across 8 correlated biomarkers (CRP correlated 0.73 with ESR). KNN imputer (k=5) on normalized values preserved feature correlations better than mean imputation, improving downstream mortality prediction F1 by 0.04. Mean imputation had collapsed the CRP-ESR correlation from 0.73 to 0.41 by filling both independently.

**Ad click prediction (L1 selection):** RTB model had 342 engineered features. L1 logistic regression with C=0.05 eliminated 280 features (82%). Remaining 62 features produced same AUC (0.764) on held-out week as all 342 features. Model inference latency dropped from 8ms to 1.2ms — critical for sub-100ms ad auction response time requirement.

---

## 8. Tradeoffs

| Encoding         | Pros                                  | Cons                               |
|------------------|---------------------------------------|------------------------------------|
| One-hot          | No ordinal assumption, interpretable  | Explosion for high cardinality     |
| Target encoding  | Handles high cardinality, informative | Leakage if not done with CV folds  |
| Ordinal encoding | Single column, tree-friendly          | Implies order that may not exist   |
| Embedding        | Rich representation, handles 1M+ cats | Requires neural model, black box   |

| Imputer          | Pros                              | Cons                            |
|------------------|-----------------------------------|---------------------------------|
| Mean/Median      | Fast, simple                      | Ignores correlations            |
| KNN              | Correlation-aware                 | O(n^2) memory for large n       |
| Iterative (MICE) | Best statistical properties       | Slow, hyperparameter-sensitive  |
| Indicator + fill | Preserves missingness signal      | Doubles features                |

| Scaler           | Handles outliers | Output range       | Use when |
|------------------|------------------|--------------------|----------|
| StandardScaler   | No               | Unbounded          | Approx. normal, few outliers |
| MinMaxScaler     | No               | [0, 1]             | Bounded data, neural networks |
| RobustScaler     | Yes (IQR-based)  | Unbounded           | Skewed data, many outliers |
| MaxAbsScaler     | No               | [-1, 1]            | Sparse data (preserves zeros) |

---

## 9. When to Use / When NOT to Use

**One-hot encoding — use when:** nominal categorical, <= 15 unique values, tree or linear model.
**One-hot encoding — do NOT use when:** > 50 unique values (use target encoding or hashing), ordinal data (use ordinal encoder).

**Target encoding — use when:** high cardinality (zip codes, product IDs, user IDs), gradient boosted trees where mean target per category is a strong signal.
**Target encoding — do NOT use when:** very small datasets (< 1,000 rows per category mean is noisy), applying without cross-validation guard (produces leakage).

**StandardScaler — use when:** logistic regression, SVM, neural networks, PCA.
**RobustScaler — use when:** data has meaningful outliers you want to keep (fraud amounts, sensor spikes).
**MinMaxScaler — use when:** neural network inputs expected in [0,1], image pixel normalization.
**No scaling needed when:** tree-based models (decision trees, random forest, XGBoost) — they are invariant to monotonic feature transformations.

**KNN imputer — do NOT use when:** n > 100,000 (quadratic time/memory); use iterative imputer or simple imputer with indicator instead.

**RFECV — use when:** you need the most reliable feature subset and have compute budget. Expect O(n_features * cv_folds) model fits.

---

## 10. Common Pitfalls

**Pitfall 1: Scaling before train/test split (the most common leakage bug).**
A team fit `StandardScaler` on the entire dataset, then split into train and test.

```python
# BROKEN — test statistics leak into training through the scaler
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)           # uses test set mean/std!
X_train, X_test, y_train, y_test = train_test_split(X_scaled, y, test_size=0.2)

# FIXED — fit scaler only on training data
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2)
scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train)   # fit on train only
X_test_scaled = scaler.transform(X_test)         # transform test with train stats
# Better: wrap in a Pipeline so this is enforced mechanically
```

The model appeared to perform well (test AUC 0.84) but degraded to 0.79 in production — the gap was entirely explained by mean/std leakage from the 100,000-row test set.

**Pitfall 2: Target encoding without out-of-fold protection.**
A data scientist computed `mean(conversion_rate) per user_city` on the entire training set, then used it as a feature in cross-validation. Validation folds saw their own target values incorporated into the feature — validation AUC was 0.88 vs actual test AUC of 0.74. Fix: use cross-validated target encoding (encode fold k using the mean from folds 1..k-1, k+1..n).

**Pitfall 3: Imputing before train/test split.**
KNN imputer fit on full dataset shares missingness patterns from test rows. Fix: imputer always goes inside the Pipeline.

**Pitfall 4: One-hot encoding unseen categories at inference.**
Model trained on `OneHotEncoder` saw categories A, B, C in training. At inference, category D appeared and the encoder raised `ValueError`. Fix: set `handle_unknown="ignore"` in sklearn's `OneHotEncoder` — unseen categories produce an all-zero row.

**Pitfall 5: Ignoring missing value mechanism (MCAR vs MAR vs MNAR).**
A fraud team imputed missing `device_age` with the median. Missing device_age was actually MNAR — fraudsters deliberately withheld device info 3x more often than legitimate users. Imputing destroyed this signal. Fix: add `device_age_was_missing` indicator column before imputing.

**Pitfall 6: Log transforming zero or negative values.**
`np.log(x)` on a column with zeros produces `-inf`; on negatives, `nan`. A pipeline silently passed NaN through to the model, which then predicted garbage.

```python
# BROKEN
df["amount_log"] = np.log(df["amount"])   # blows up for amount == 0

# FIXED — log1p for non-negative with zeros
df["amount_log"] = np.log1p(df["amount"])  # log(1 + amount), safe for 0
# OR — Yeo-Johnson for data with negatives
from sklearn.preprocessing import PowerTransformer
pt = PowerTransformer(method="yeo-johnson")
df["amount_transformed"] = pt.fit_transform(df[["amount"]])
```

---

## 11. Technologies & Tools

| Tool                         | Purpose                                   | Notes |
|------------------------------|-------------------------------------------|-------|
| scikit-learn Pipeline        | Leakage-free feature transformation       | Production standard |
| category_encoders (pip)      | TargetEncoder, BinaryEncoder, HashingEncoder | Richer than sklearn encoders |
| feature-engine (pip)         | Outlier cappers, lag features, cyclic encoding | Time-series friendly |
| featuretools (pip)            | Automated deep feature synthesis          | Relational data |
| Feast                        | Open-source feature store                 | Offline + online serving |
| Tecton                       | Managed feature platform                  | Real-time streaming features |
| Hopsworks                    | Open feature store                        | Spark + Python |
| pandas + numpy               | Custom transformations                    | Always needed |
| Great Expectations           | Feature validation / data quality         | Catch drift pre-pipeline |

---

## 12. Interview Questions with Answers

**Q: What is data leakage in feature engineering and how do you prevent it?**
Data leakage occurs when information from outside the training set (including the test set or future data) contaminates the training process, producing overly optimistic validation metrics that fail in production. Common forms include: scaling on the full dataset before splitting, target encoding without cross-validation, and including future-derived features (e.g., 30-day average computed using future rows). Prevention: always use sklearn `Pipeline` so all transformers are fit only on training data, and use time-based splits for temporal data.

**Q: When would you use target encoding versus one-hot encoding?**
Use target encoding when a categorical feature has more than ~15–50 unique values (high cardinality) and the category has a meaningful relationship with the target. One-hot becomes impractical at high cardinality due to dimensionality explosion. Target encoding requires cross-validation guards to prevent leakage — fit the encoder on k-1 folds and apply to the kth fold. For very low cardinality (2–15 categories) with no natural order, one-hot is simpler and transparent.

**Q: Explain the target encoding formula with smoothing.**
The smoothed target encoding for category c is: `(n_c * mean_c + m * global_mean) / (n_c + m)` where `n_c` is the count of observations with category c, `mean_c` is the mean target for category c, `global_mean` is the overall target mean, and `m` is the smoothing parameter. When `n_c` is small (few samples for that category), the estimate pulls strongly toward the global mean (regularization). When `n_c` is large, the category-level mean dominates. `m=10` is a common default.

**Q: What is the difference between StandardScaler, MinMaxScaler, and RobustScaler?**
`StandardScaler` subtracts the mean and divides by standard deviation; output is unbounded with mean 0 and variance 1; sensitive to outliers because outliers inflate the standard deviation. `MinMaxScaler` scales to [0, 1]; sensitive to outliers (one extreme value compresses all others). `RobustScaler` uses median and interquartile range (IQR) instead of mean and std — outliers have minimal effect. Use RobustScaler when data has meaningful outliers that you want to keep, StandardScaler for roughly normal distributions, MinMaxScaler for neural networks expecting bounded inputs.

**Q: How would you handle a categorical feature with 50,000 unique user IDs?**
One-hot would create 50,000 columns — infeasible. Options: (1) target encoding with out-of-fold CV (effective if IDs have distinct behavior patterns), (2) embedding layer if using a neural network (represent each ID as a dense 32-dim vector learned during training), (3) hashing trick (hash IDs to a fixed-size space of, e.g., 1,000 buckets — fast but loses some information via collisions), (4) aggregate features at the user level (mean spend, count of actions) to replace the raw ID.

**Q: When should you add a missing indicator column instead of just imputing?**
Add a missing indicator when the fact that a value is absent carries predictive signal (missing not at random, MNAR). Examples: a missing `credit_score` field often means no credit history (different risk than a low score); a missing `response_time` in an API log often means the request timed out (different from a fast response). Always add the indicator before imputing so the signal is preserved regardless of what imputation fills in.

**Q: What is the difference between filter, wrapper, and embedded feature selection methods?**
Filter methods (correlation, mutual information) rank features independently of the model — fast but ignore feature interactions. Wrapper methods (RFE, RFECV) train the model repeatedly with different feature subsets — expensive but find interaction-aware subsets. Embedded methods (L1 penalty, tree feature importance) perform selection during model training itself — efficient and model-specific. For production, use embedded methods (L1 or tree importance) for initial reduction, then RFECV to fine-tune the final subset.

**Q: How does RFECV work and what estimator should you use inside it?**
RFECV (Recursive Feature Elimination with Cross-Validation) starts with all features, trains the estimator, removes the feature with the lowest importance/coefficient magnitude, and repeats. At each step it cross-validates to measure performance, ultimately selecting the feature count that maximizes CV score. Use a fast linear estimator (`LogisticRegression`, `LinearSVC`) to keep runtime manageable — avoid `GradientBoosting` inside RFECV on large datasets (O(n_features * cv_folds) fits, each potentially slow).

**Q: What is the curse of dimensionality and how does feature selection address it?**
In high dimensions, data points become equidistant from each other — distance-based algorithms (k-NN, SVM with RBF kernel, k-means) lose discriminative power. Feature selection removes irrelevant and redundant dimensions, concentrating signal. Additionally, models with many irrelevant features overfit (high variance) because the optimizer assigns weight to noise. L1 regularization and tree importance-based selection are the most efficient mitigations.

**Q: How do you prevent the one-hot encoder from crashing on unseen categories at inference?**
Set `handle_unknown="ignore"` in sklearn's `OneHotEncoder`. Unseen categories produce an all-zero row for that feature (as if the category does not exist), which is the safest fallback for most models. Alternatively, add an "other" category during training to catch rare categories, and map any unseen inference value to "other". Always wrap encoders inside a Pipeline so the same fitted encoder is used at inference as during training.

**Q: What are polynomial features and when are they risky?**
Polynomial features create all combinations of input features up to a specified degree (e.g., degree=2 adds x1^2, x2^2, x1*x2 for every pair). They allow linear models to fit non-linear decision boundaries. Risks: with d features and degree=2, output is O(d^2) features — 100 input features become ~5,000; with degree=3, ~170,000. This dramatically increases overfitting risk and training time. Prefer tree-based models (which find interactions natively) or domain-driven manual interaction features over automated polynomial expansion above degree=2.

---

## 13. Best Practices

1. Always build a sklearn `Pipeline` wrapping all transformers and the estimator — this mechanically prevents all leakage.
2. Fit scalers, imputers, and encoders exclusively on training data; call `.transform()` on validation and test sets.
3. For categorical features with > 15 unique values, try target encoding with out-of-fold CV before one-hot.
4. Add `feature_was_missing` binary indicator before imputing whenever missingness is informative (MNAR data).
5. Use `log1p` for right-skewed non-negative features; `PowerTransformer(method="yeo-johnson")` for features with negatives.
6. Use `RobustScaler` when outliers are present and meaningful; `StandardScaler` otherwise; `MinMaxScaler` for neural nets.
7. Run `SelectFromModel` (L1 / tree importance) first for fast reduction, then RFECV on the surviving subset for final selection.
8. Check Pearson correlation matrix and remove features with |corr| > 0.95 (one of the pair adds no new information).
9. Validate the pipeline end-to-end on a temporal hold-out (most recent 20% of data) to detect time-based leakage.
10. Use `category_encoders` library for richer encoding options (binary, hash, James-Stein) that sklearn lacks.

---

## 14. Case Study

**Problem:** A consumer lending company needs to predict 90-day loan default on 2M applications. Raw data: 45 columns — mix of numeric (income, loan_amount, credit_score), categorical (loan_purpose, employment_type, state — 51 values), and binary (has_mortgage, is_self_employed). Missing rate: 8% on income, 12% on credit_score, 2% on employment_type.

**Step 1 — Exploratory analysis:**
- `income`: right-skewed (median $62k, mean $89k, max $4.2M) — apply `log1p`
- `credit_score`: roughly normal (600–850) — `StandardScaler`
- `state`: 51 unique values — target encoding, not one-hot
- `loan_purpose`: 9 values — one-hot
- `employment_type`: 4 values — one-hot
- Missing `credit_score` correlates with default rate 2.1x vs present — add indicator

**Step 2 — Pipeline design:**

```python
from sklearn.pipeline import Pipeline
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import (
    FunctionTransformer, StandardScaler, OneHotEncoder
)
import numpy as np

numeric_log_cols = ["income", "loan_amount", "monthly_debt"]
numeric_std_cols = ["credit_score", "tenure_months", "num_accounts"]
low_cat_cols = ["loan_purpose", "employment_type"]
high_cat_col = ["state"]  # handled separately with TargetEncoder

log_pipe = Pipeline([
    ("impute", SimpleImputer(strategy="median")),
    ("log1p", FunctionTransformer(np.log1p)),
    ("scale", StandardScaler()),
])

std_pipe = Pipeline([
    ("impute", SimpleImputer(strategy="median")),
    ("scale", StandardScaler()),
])

cat_pipe = Pipeline([
    ("impute", SimpleImputer(strategy="most_frequent")),
    ("encode", OneHotEncoder(handle_unknown="ignore", sparse_output=True)),
])

preprocessor = ColumnTransformer([
    ("log_num", log_pipe, numeric_log_cols),
    ("std_num", std_pipe, numeric_std_cols),
    ("low_cat", cat_pipe, low_cat_cols),
], remainder="drop")
```

**Step 3 — Missing indicators:** Add `credit_score_was_missing` and `income_was_missing` binary columns before preprocessing.

**Step 4 — Feature selection:** After pipeline transforms, 62 features remain (42 one-hot + 20 numeric). Run `SelectFromModel(LogisticRegression(C=0.1, penalty="l1"))` — retains 38 features. RFECV on 38 features (5-fold, LogisticRegression estimator) selects 31 final features.

**Step 5 — Target encoding for state:** TargetEncoder fit on 4/5 folds, applied to 1/5 fold repeatedly. Global default rate = 4.1%; high-default states (MS, WV) encode near 7%; low-default (SD, ND) near 2%.

**Step 6 — Results:** Pipeline + GBM final model: AUC 0.831 on temporal hold-out (most recent 6 months). Baseline (raw features, no engineering): AUC 0.784. Gain attributable to log transforms (+0.018), missing indicators (+0.012), target encoding of state (+0.009), L1 feature selection (+0.008). Total gain: +0.047 AUC points from feature engineering alone.
