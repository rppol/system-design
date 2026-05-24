# Design a Demand Forecasting System (Retail Scale)

## Problem Statement

Design a demand forecasting system for a large retailer with 1 million products (SKUs) across 10,000
stores — producing 10 billion individual time series. Forecasts feed three downstream systems:
inventory ordering (weekly horizon, ordered 2 weeks in advance), logistics planning (daily horizon,
truck routing and warehouse staffing), and dynamic pricing (hourly horizon, markdown decisions).
The system must handle strong seasonality (Christmas, Black Friday, back-to-school), promotional
lift (a 50% price cut can 10x demand), holiday effects, new product cold start (no history), and
product discontinuations. Target: Mean Absolute Error (MAE) below 15% and weighted RMSSE below 1.0
(Kaggle M5 competition benchmark).

### Functional Requirements
- Produce weekly unit-sales forecasts for all 1M SKU × 10K store combinations
- Support promotional scenarios: what-if forecasting for planned promotions
- Handle cold start: new products with < 4 weeks of sales history
- Hierarchical consistency: store forecasts sum to district, district to national
- Batch processing: all forecasts refreshed weekly; high-priority SKUs daily

### Non-Functional Requirements
- Scale: 10 billion time series (1M SKUs * 10K stores), practical via global model
- Latency: full weekly refresh in < 4 hours (batch), single SKU forecast < 200ms (API)
- Accuracy: WMAE < 15%, WRMSSE < 1.0 on held-out test set
- Coverage: 99.9% of SKU-store pairs receive a forecast even without history (cold start)
- Retraining: weekly full retrain + daily incremental update for new sales data

### Out of Scope
- Price optimization (uses forecasts as input)
- Store replenishment logic (consumes forecasts)
- Real-time point-of-sale integration (daily batch suffices)

---

## Architecture Overview

```
Data Sources
  POS Systems (daily sales)  |  Promotions Calendar  |  External (weather, holidays)
           |                           |                          |
           v                           v                          v
  +------------------------------------------------------------------+
  |              Data Lakehouse  (Databricks / Spark)               |
  |   Bronze: raw POS events    Silver: cleaned sales grain          |
  |   Gold: feature-engineered training table (Parquet, S3)         |
  +------------------------------------------------------------------+
                           |
              +------------+------------+
              |                         |
              v                         v
   +--------------------+    +----------------------+
   | Feature Engineering|    |  Hierarchy Manager   |
   | Lag features: t-7  |    |  SKU-store -> district|
   | Rolling stats      |    |  -> national mapping  |
   | Time encodings     |    |  MinT reconciliation  |
   | Promo flags        |    +----------------------+
   | Price elasticity   |
   +--------------------+
              |
              v
   +-----------------------------+
   |    Global LightGBM Model   |
   |  All SKU-store pairs in    |
   |  one model (cross-learning)|
   |  500 trees, lr=0.05        |
   |  ~72M training rows        |
   +-----------------------------+
              |
    +---------+-----------+
    |                     |
    v                     v
  [Quantile         [Point Forecast]
  Forecasts]         P10/P50/P90
  (inventory         for inventory
  safety stock)      ordering

              |
              v
   +-----------------------------+
   |  Hierarchical Reconciliation|
   |  MinT: ensure store sums   |
   |  == national forecast       |
   +-----------------------------+
              |
              v
   +-----------------------------+
   |    Forecast Store           |
   |  - DynamoDB (SKU lookups)   |
   |  - S3 Parquet (bulk export) |
   |  - REST API (<200ms)        |
   +-----------------------------+
              |
    +---------+---------+---------+
    |         |         |         |
    v         v         v         v
 Inventory  Logistics  Pricing   Reporting
 Planning   Planning   Engine    Dashboards


Training Pipeline (weekly):
  Databricks Spark -> Feature Table -> LightGBM (multi-node) -> MLflow Model Registry -> Deploy
  Duration: ~3 hours for 10B series via global model (one model, not 10B individual models)
```

---

## Key Design Decisions

### 1. Global Model vs Per-SKU Models

The naive approach trains one model per SKU-store pair (10 billion models). This is computationally
infeasible and fails for cold start (new products have no model). The global model trains a single
LightGBM on all SKU-store pairs simultaneously. The model sees features identifying which SKU and
which store it is predicting for. Cross-learning transfers demand patterns across similar products
(all yogurt SKUs share seasonal patterns). One weekly retrain on ~72M rows (2 years * 52 weeks *
all pairs sampled) runs in ~2 hours on a 64-core cluster.

### 2. Temporal Cross-Validation (Walk-Forward)

Standard k-fold CV is invalid for time series (data leakage: future informs past). Walk-forward
validation uses expanding windows: train on weeks 1-52, validate on weeks 53-56; train on weeks
1-56, validate on 57-60. Three validation folds minimum. LightGBM early stopping monitors WMAE
on the most recent validation fold to prevent overfitting.

### 3. Feature Engineering as Core IP

The model is only as good as its features. Key feature groups:
- Lag features: sales at t-7, t-14, t-28, t-56 (capture weekly seasonality)
- Rolling statistics: 4-week, 13-week, 52-week rolling mean/std (capture trend)
- Calendar features: day_of_week, week_of_year, is_holiday, days_to_next_holiday, days_since_holiday
- Promotion features: is_on_promotion, promotion_discount_pct, promotion_type (BOGO, % off)
- Price features: current_price, price_change_vs_last_week, relative_price_vs_category_median
- Store features: store_state, store_size_tier (S/M/L), urban_rural_flag
- Product features: category, subcategory, brand, price_tier, is_perishable

### 4. Quantile Forecasting for Inventory Safety Stock

Point forecasts (P50) drive ordering quantities. Quantile forecasts (P10 for understocking risk,
P90 for overstocking risk) drive safety stock calculations. LightGBM supports quantile regression
natively via `objective='quantile'`. Three separate models trained for P10, P50, P90.
Inventory team uses P90 for high-margin products (stockout cost > overstock cost) and P50 for
low-margin commodities.

### 5. Hierarchical Reconciliation via MinT

Store-level forecasts are generated independently and will not sum exactly to the national forecast.
MinT (Minimum Trace Reconciliation) adjusts all hierarchy levels simultaneously to be internally
consistent while minimizing total forecast error. This is critical: the logistics team plans
national truck movements from the national forecast; the store team orders from store forecasts.
Inconsistency creates phantom inventory or shortages.

### 6. Cold Start for New Products

New products with < 4 weeks of history use a category-level proxy:
1. Find the 5 most similar products (cosine similarity on product feature vector: category,
   price tier, brand tier, package size)
2. Average their demand histories normalized by product price ratio
3. Apply the new product's promotional calendar
After 4 weeks, the global model begins including the new product in training with exponential
weighting (recent weeks weighted 2x vs older weeks).

---

## Implementation

```python
from __future__ import annotations

import numpy as np
import pandas as pd
import lightgbm as lgb
from dataclasses import dataclass
from typing import Optional
from sklearn.preprocessing import LabelEncoder


# ---------------------------------------------------------------------------
# Feature Engineering
# ---------------------------------------------------------------------------

def create_lag_features(
    df: pd.DataFrame,
    target_col: str = "units_sold",
    lags: list[int] = [7, 14, 21, 28, 56],
    group_cols: list[str] = ["sku_id", "store_id"],
) -> pd.DataFrame:
    """
    Create lag features for time series data.

    df must have columns: group_cols + ["date", target_col]
    Lags are in days. For weekly data, lag=7 = same day last week.

    IMPORTANT: df must be sorted by group_cols + date before calling this.
    """
    df = df.sort_values(group_cols + ["date"]).copy()

    for lag in lags:
        col_name = f"{target_col}_lag_{lag}d"
        df[col_name] = (
            df.groupby(group_cols)[target_col]
            .shift(lag)
        )

    return df


def create_rolling_features(
    df: pd.DataFrame,
    target_col: str = "units_sold",
    windows: list[int] = [7, 28, 91, 365],
    group_cols: list[str] = ["sku_id", "store_id"],
) -> pd.DataFrame:
    """
    Rolling mean, std, min, max over specified windows (in days).
    Uses shift(1) to avoid look-ahead bias: rolling stats computed from data
    up to (but not including) current day.
    """
    df = df.sort_values(group_cols + ["date"]).copy()

    for window in windows:
        grouped = df.groupby(group_cols)[target_col]
        df[f"rolling_mean_{window}d"] = (
            grouped.transform(lambda x: x.shift(1).rolling(window, min_periods=1).mean())
        )
        df[f"rolling_std_{window}d"] = (
            grouped.transform(lambda x: x.shift(1).rolling(window, min_periods=1).std().fillna(0))
        )
        df[f"rolling_max_{window}d"] = (
            grouped.transform(lambda x: x.shift(1).rolling(window, min_periods=1).max())
        )

    return df


def create_calendar_features(df: pd.DataFrame, date_col: str = "date") -> pd.DataFrame:
    """
    Create time-based features from the date column.
    Cyclical encoding (sin/cos) preserves periodicity:
      day_of_week=6 and day_of_week=0 are adjacent (Saturday -> Sunday).
    """
    df = df.copy()
    dates = pd.to_datetime(df[date_col])

    df["day_of_week"] = dates.dt.dayofweek
    df["month"] = dates.dt.month
    df["week_of_year"] = dates.dt.isocalendar().week.astype(int)
    df["quarter"] = dates.dt.quarter
    df["year"] = dates.dt.year
    df["is_weekend"] = (df["day_of_week"] >= 5).astype(int)

    # Cyclical encoding of day_of_week and month
    df["dow_sin"] = np.sin(2 * np.pi * df["day_of_week"] / 7)
    df["dow_cos"] = np.cos(2 * np.pi * df["day_of_week"] / 7)
    df["month_sin"] = np.sin(2 * np.pi * df["month"] / 12)
    df["month_cos"] = np.cos(2 * np.pi * df["month"] / 12)

    return df


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Full feature engineering pipeline. df must contain:
    sku_id, store_id, date, units_sold, price, is_on_promotion,
    promotion_discount_pct, store_state, store_size_tier, category, brand.
    """
    df = create_lag_features(df, lags=[7, 14, 21, 28, 56, 91, 365])
    df = create_rolling_features(df, windows=[7, 28, 91, 365])
    df = create_calendar_features(df)

    # Price features
    df["price_lag_7d"] = df.groupby(["sku_id", "store_id"])["price"].shift(7)
    df["price_change_pct"] = (df["price"] - df["price_lag_7d"]) / (df["price_lag_7d"] + 1e-9)

    # Category median price (relative positioning)
    cat_median = df.groupby(["category", "date"])["price"].transform("median")
    df["relative_price_vs_category"] = df["price"] / (cat_median + 1e-9)

    # Promotion interaction
    df["promo_x_discount"] = df["is_on_promotion"] * df["promotion_discount_pct"]

    # Drop rows with NaN lag features (first N days per SKU-store)
    # In production: keep these rows but use cold-start model
    df = df.dropna(subset=[c for c in df.columns if "_lag_" in c or "rolling_" in c])

    return df


# ---------------------------------------------------------------------------
# Global LightGBM Model
# ---------------------------------------------------------------------------

FEATURE_COLS = [
    # Lag features
    "units_sold_lag_7d", "units_sold_lag_14d", "units_sold_lag_21d",
    "units_sold_lag_28d", "units_sold_lag_56d", "units_sold_lag_91d",
    "units_sold_lag_365d",
    # Rolling statistics
    "rolling_mean_7d", "rolling_std_7d", "rolling_max_7d",
    "rolling_mean_28d", "rolling_std_28d", "rolling_max_28d",
    "rolling_mean_91d", "rolling_std_91d",
    "rolling_mean_365d",
    # Calendar
    "dow_sin", "dow_cos", "month_sin", "month_cos",
    "week_of_year", "quarter", "year", "is_weekend",
    # Price
    "price", "price_change_pct", "relative_price_vs_category",
    # Promotion
    "is_on_promotion", "promotion_discount_pct", "promo_x_discount",
    # Categorical (LightGBM handles natively)
    "store_state", "store_size_tier", "category", "brand",
]

CATEGORICAL_FEATURES = ["store_state", "store_size_tier", "category", "brand"]

TARGET_COL = "units_sold"


def train_lightgbm_global(
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    quantile: Optional[float] = None,      # None = point forecast (MSE), else quantile
    n_estimators: int = 2000,
    learning_rate: float = 0.05,
    num_leaves: int = 127,
    min_child_samples: int = 20,
) -> lgb.Booster:
    """
    Train a global LightGBM model on all SKU-store pairs.

    Training data: 2 years of daily sales across all stores (72M rows after feature engineering).
    Walk-forward validation: train on weeks 1-100, validate on weeks 101-104.

    quantile=None  -> point forecast (objective=mse, metric=mae)
    quantile=0.9   -> P90 for safety stock (objective=quantile, alpha=0.9)
    """
    # Encode categorical features
    le_dict: dict[str, LabelEncoder] = {}
    for cat_col in CATEGORICAL_FEATURES:
        le = LabelEncoder()
        train_df[cat_col] = le.fit_transform(train_df[cat_col].astype(str))
        val_df[cat_col] = le.transform(val_df[cat_col].astype(str).map(
            lambda x: x if x in le.classes_ else le.classes_[0]
        ))
        le_dict[cat_col] = le

    X_train = train_df[FEATURE_COLS]
    y_train = train_df[TARGET_COL].clip(lower=0)   # demand is non-negative
    X_val = val_df[FEATURE_COLS]
    y_val = val_df[TARGET_COL].clip(lower=0)

    dtrain = lgb.Dataset(
        X_train, label=y_train,
        categorical_feature=CATEGORICAL_FEATURES,
        free_raw_data=False,
    )
    dval = lgb.Dataset(
        X_val, label=y_val,
        categorical_feature=CATEGORICAL_FEATURES,
        reference=dtrain,
        free_raw_data=False,
    )

    if quantile is None:
        objective = "regression"
        metric = "mae"
    else:
        objective = "quantile"
        metric = "quantile"

    params: dict = {
        "objective": objective,
        "metric": metric,
        "num_leaves": num_leaves,
        "learning_rate": learning_rate,
        "min_child_samples": min_child_samples,
        "feature_fraction": 0.8,      # subsample 80% of features per tree
        "bagging_fraction": 0.8,      # subsample 80% of data per tree
        "bagging_freq": 1,
        "reg_alpha": 0.1,             # L1 regularization
        "reg_lambda": 0.1,            # L2 regularization
        "verbose": -1,
        "n_jobs": -1,
    }
    if quantile is not None:
        params["alpha"] = quantile    # quantile level

    booster = lgb.train(
        params,
        dtrain,
        num_boost_round=n_estimators,
        valid_sets=[dval],
        callbacks=[
            lgb.early_stopping(stopping_rounds=50, verbose=False),
            lgb.log_evaluation(period=100),
        ],
    )

    return booster


# ---------------------------------------------------------------------------
# Walk-Forward Cross-Validation
# ---------------------------------------------------------------------------

def walk_forward_cv(
    df: pd.DataFrame,
    n_folds: int = 3,
    val_weeks: int = 4,
    gap_weeks: int = 0,          # weeks between train end and val start (avoids leakage)
) -> list[dict]:
    """
    Walk-forward (expanding window) cross-validation for time series.
    Returns list of dicts with fold metrics.

    Example with n_folds=3, val_weeks=4:
      Fold 1: train=[week 1..48], val=[week 49..52]
      Fold 2: train=[week 1..52], val=[week 53..56]
      Fold 3: train=[week 1..56], val=[week 57..60]
    """
    df = df.copy()
    dates = pd.to_datetime(df["date"])
    all_weeks = sorted(df["week_of_year"].unique())   # simplified: assumes contiguous weeks
    results = []

    total_weeks = len(all_weeks)
    # Start such that last fold ends at last available week
    first_val_end = total_weeks
    first_val_start = first_val_end - val_weeks - gap_weeks

    for fold in range(n_folds - 1, -1, -1):
        val_start_week_idx = first_val_start - fold * val_weeks
        val_end_week_idx = val_start_week_idx + val_weeks
        train_end_week_idx = val_start_week_idx - gap_weeks

        if val_start_week_idx <= 0 or train_end_week_idx <= 0:
            break

        train_weeks = set(all_weeks[:train_end_week_idx])
        val_weeks_set = set(all_weeks[val_start_week_idx:val_end_week_idx])

        train_df = df[df["week_of_year"].isin(train_weeks)]
        val_df = df[df["week_of_year"].isin(val_weeks_set)]

        if len(train_df) == 0 or len(val_df) == 0:
            continue

        booster = train_lightgbm_global(train_df.copy(), val_df.copy())
        preds = booster.predict(val_df[FEATURE_COLS])
        actuals = val_df[TARGET_COL].values

        # WMAE: weight by actual sales (high-volume items count more)
        weights = actuals + 1.0   # +1 avoids zero weight for zero-sales weeks
        wmae = float(np.average(np.abs(preds - actuals), weights=weights))
        mae = float(np.mean(np.abs(preds - actuals)))

        results.append({
            "fold": fold,
            "train_rows": len(train_df),
            "val_rows": len(val_df),
            "mae": mae,
            "wmae": wmae,
            "best_iteration": booster.best_iteration,
        })

    return results


# ---------------------------------------------------------------------------
# Cold Start: New Product Forecasting
# ---------------------------------------------------------------------------

@dataclass
class ProductFeatures:
    sku_id: str
    category: str
    brand: str
    price: float
    package_size_ml: float
    price_tier: str   # "budget", "mid", "premium"


def cold_start_forecast(
    new_product: ProductFeatures,
    catalog: list[ProductFeatures],
    historical_sales: dict[str, pd.Series],  # sku_id -> daily sales series
    n_similar: int = 5,
) -> pd.Series:
    """
    Forecast demand for a new product using k-nearest-neighbor proxy.
    Similarity computed on product feature vector (cosine similarity).

    Steps:
    1. Encode all catalog products as feature vectors
    2. Find k=5 most similar products by cosine similarity
    3. Average their normalized demand histories
    4. Scale by price ratio (price elasticity adjustment)

    Returns: pd.Series of daily demand forecasts (next 28 days)
    """
    def product_to_vector(p: ProductFeatures) -> np.ndarray:
        # Simple one-hot encoding for categorical + numeric
        tier_map = {"budget": 0, "mid": 1, "premium": 2}
        return np.array([
            hash(p.category) % 1000 / 1000,   # category hash as float in [0,1]
            hash(p.brand) % 1000 / 1000,
            p.price / 100.0,                   # normalize price by $100
            p.package_size_ml / 2000.0,        # normalize by 2L
            tier_map.get(p.price_tier, 1) / 2.0,
        ])

    new_vec = product_to_vector(new_product)
    catalog_vecs = np.vstack([product_to_vector(p) for p in catalog])

    # Cosine similarity
    new_norm = np.linalg.norm(new_vec)
    catalog_norms = np.linalg.norm(catalog_vecs, axis=1)
    similarities = catalog_vecs @ new_vec / (catalog_norms * new_norm + 1e-9)

    top_k_indices = np.argsort(similarities)[-n_similar:][::-1]
    similar_products = [catalog[i] for i in top_k_indices]

    # Average normalized demand histories of similar products
    proxy_series = []
    for prod in similar_products:
        if prod.sku_id not in historical_sales:
            continue
        hist = historical_sales[prod.sku_id]
        # Normalize by product price (demand is price-elastic)
        price_ratio = new_product.price / (prod.price + 1e-9)
        # Rough price elasticity: 10% price increase -> 5% demand decrease (elasticity -0.5)
        demand_multiplier = price_ratio ** (-0.5)
        normalized = hist * demand_multiplier
        proxy_series.append(normalized)

    if not proxy_series:
        # Last resort: flat forecast at category median
        return pd.Series([10.0] * 28)   # placeholder

    avg_proxy = pd.concat(proxy_series, axis=1).mean(axis=1)
    # Return last 28 days of proxy as forecast for new product
    return avg_proxy.tail(28).reset_index(drop=True)


# ---------------------------------------------------------------------------
# Hierarchical Reconciliation (MinT — simplified)
# ---------------------------------------------------------------------------

def mint_reconciliation(
    base_forecasts: dict[str, float],      # level -> forecast value
    summing_matrix: np.ndarray,            # maps bottom-level to all levels
    covariance_matrix: np.ndarray,         # forecast error covariance
) -> dict[str, float]:
    """
    MinT (Minimum Trace) reconciliation for hierarchical forecasts.

    Ensures: sum of store forecasts == national forecast.
    MinT adjusts all levels simultaneously to be coherent while minimizing total error.

    Full implementation uses: reconciled = S @ (S.T @ W_inv @ S)^-1 @ S.T @ W_inv @ base
    where W is the covariance matrix of base forecast errors and S is the summing matrix.

    Simplified version shown here; production uses hts library or statsforecast.
    """
    S = summing_matrix
    W_inv = np.linalg.pinv(covariance_matrix)
    base = np.array(list(base_forecasts.values()))

    # MinT reconciliation formula
    reconciled = S @ np.linalg.pinv(S.T @ W_inv @ S) @ S.T @ W_inv @ base

    keys = list(base_forecasts.keys())
    return dict(zip(keys, reconciled.tolist()))
```

---

## ML Components Used

| Component | Purpose | Key Parameters |
|-----------|---------|----------------|
| LightGBM | Global demand forecasting model | n_estimators=2000, lr=0.05, num_leaves=127, early_stopping=50 |
| LightGBM Quantile | P10/P50/P90 for safety stock | objective=quantile, alpha=0.1/0.5/0.9 |
| Walk-Forward CV | Time-series cross-validation (no leakage) | 3 folds, 4-week validation windows |
| STL (optional) | Detrending for residual modeling | Used in exploratory analysis |
| MinT Reconciliation | Hierarchical coherence | statsforecast.models.MinT |
| MLflow | Experiment tracking, model registry | Log WMAE, WRMSSE, feature importance |
| Databricks + Spark | Feature engineering at 10B series scale | 64 workers, Parquet I/O |
| DynamoDB | Online forecast serving (SKU lookups) | < 5ms P99, provisioned 50K RCU |
| S3 Parquet | Bulk forecast export for downstream systems | Partitioned by date + store |

---

## Tradeoffs and Alternatives

| Decision | Chosen Approach | Alternative | Reason |
|----------|----------------|-------------|--------|
| Model architecture | Global LightGBM | 10B per-SKU ARIMA | Global model: 2h training vs computationally infeasible; cross-learning improves cold-start |
| Temporal model | LightGBM (tabular) | Temporal Fusion Transformer | LightGBM: 10x faster, more interpretable, easier to debug; TFT better for very long sequences |
| Lag features | Manual lag engineering | Learned temporal patterns (LSTM) | Manual lags: interpretable, no sequence padding overhead, easier to add new features |
| Cold start | KNN proxy | Category average | KNN uses product similarity features; category average ignores price positioning |
| Hierarchical consistency | MinT reconciliation | Bottom-up aggregation | MinT minimizes total error; bottom-up propagates store-level errors to national |
| Validation | Walk-forward CV | Random k-fold | Walk-forward prevents temporal leakage; k-fold overestimates accuracy by ~20% |
| Quantile forecasting | 3 separate models (P10, P50, P90) | Distributional forecasting | 3 separate models: simpler, independently tunable; distributional models require parametric assumption |

---

## Interview Discussion Points

**Q: Why use a global model instead of one model per SKU-store pair?**
A: Three reasons. First, 10 billion models are computationally infeasible to train weekly.
Second, cold start: a product with 2 weeks of history has no model; the global model handles it
with lag features as NaN (median-imputed) and learns from similar products. Third, cross-learning:
all strawberry yogurt SKUs share the same seasonal patterns; the global model learns this shared
structure from millions of examples. The global model does not overfit to any single SKU because
the regularization effect of 72M training rows dominates any single SKU's signal.

**Q: How do you prevent promotional uplift from corrupting the baseline forecast?**
A: Promotion features are explicit model inputs (is_on_promotion, promotion_discount_pct,
promo_x_discount interaction term). The model learns the expected lift from promotions and can
produce two forecasts: one with promotion flags set (promoted scenario) and one without
(baseline scenario). The difference is the promo lift estimate. For planning, inventory systems
use the promoted scenario forecast for weeks with planned promotions.

**Q: How do you measure forecast accuracy given that demand is unobservable (stockouts bias actuals)?**
A: Censored demand problem. When stock runs out, actual sales = stock level, not true demand.
Naive MAE on censored actuals underestimates error for high-demand items. Three mitigations:
1. Exclude stockout days from MAE calculation (filter where on-hand inventory dropped to 0).
2. Use unconstrained demand estimates from MCMC (Bayesian demand estimation from censored data).
3. Track service level separately from forecast error: a 95% service level means 95% of store-weeks
had no stockout regardless of forecast accuracy.

**Q: How do you handle the cold start problem for a brand-new store opening?**
A: Store-level cold start is harder than product cold start. Approach: cluster new store by
demographic features (population density, median income, nearby competitor density, store size).
Use the 3 most similar existing stores as proxies. Apply new store forecast = proxy store average
* (new store size / proxy size) * regional demand index. After 8 weeks of actual sales, the global
model begins incorporating the new store with exponential upweighting of recent data (recent weeks
weighted 4x). The new store flag remains a feature until 26 weeks of history accumulate.

---

## Failure Scenarios and Recovery

### Failure 1: Promotional Feature Leakage Causing 40% Forecast Inflation

**What failed:** The promotions team added future planned promotions to the training data with the promotion flag already set for weeks that had not yet occurred. The global LightGBM model learned that is_on_promotion=1 was correlated with high demand regardless of actual promotion timing. When this model was used for baseline forecasting (no promotions planned), it still produced inflated forecasts because the promotion_x_discount_pct cross-feature had been computed using future promotion data. National inventory for the spring quarter was over-ordered by 38%, resulting in $180M in excess inventory write-downs.

**Detection:** Walk-forward cross-validation caught WMAE degradation of 22% on the validation set before full deployment — but the sign of the error (overestimation vs underestimation) was not inspected. The overestimation was masked by the overall WMAE figure. Time-to-detect: 6 weeks post-deployment (quarterly inventory review).

**Recovery steps:**
1. Rebuilt training pipeline with strict temporal join: promotions table joined only on dates strictly before the forecast origin date.
2. Added prediction sign bias monitoring: weekly check of mean(forecast - actual) per category. Systematic overestimation triggers an alert.
3. Retrained model on corrected data; re-forecasted the remaining 8 weeks of the quarter with corrected model.
4. Implemented "forecast vs actuals" dashboard showing rolling 4-week mean error per category for immediate bias detection.

**Prevention:** Automated data pipeline test: for each training example at time t, assert that no features use data with timestamp > t. Implemented as a Great Expectations suite running in CI/CD before each training run.

---

### Failure 2: MinT Reconciliation Causing Negative Forecasts for Zero-Demand SKUs

**What failed:** MinT (Minimum Trace) hierarchical reconciliation optimally adjusts store-level forecasts to sum to national-level forecasts. When applied to SKUs with very sparse demand (0 units sold in 8 of 12 past weeks), MinT's matrix algebra produced negative store-level forecasts for some SKU-store pairs (e.g., forecast = -0.3 units). Inventory ordering systems did not validate for negative values and placed orders for int(max(0, -0.3)) = 0 units, which was correct but triggered a systematic under-ordering pattern: sparse SKUs were ordered to 0 units across all stores even when some stores had positive demand.

**Detection:** Inventory analyst noticed a pattern: specific SKU-store pairs had persistent stockouts despite non-zero historical demand. Traced to consistently zero-order quantities. Compared forecasts to raw model output and found MinT had produced negative values. Time-to-detect: 4 weeks.

**Recovery steps:**
1. Applied hard non-negativity constraint after MinT: clip all store-level forecasts to max(0, forecast).
2. Added upper scaling to preserve the hierarchical constraint after clipping: if clipping increases the sum of store forecasts above the national forecast, proportionally reduce non-clipped stores.
3. Added a monitoring check: fraction of store-level forecasts that are negative before clipping — alert if > 0.1% (currently near 0 for non-sparse SKUs).

**Prevention:** Evaluate MinT reconciliation on a held-out set of sparse SKUs specifically. Include sparse SKU recall (fraction of non-zero demand weeks correctly forecasted as non-zero) as a model acceptance criterion.

---

### Failure 3: Lag Feature Computation During Real-Time Single-SKU API Calls

**What failed:** The REST API endpoint (single SKU forecast, <200ms) recomputed lag features on-the-fly from the PostgreSQL sales database. For SKUs with 2 years of daily sales history, the lag feature computation (t-7, t-14, t-28, rolling 4/8/12-week averages) required a table scan of ~730 rows per SKU and 40 aggregate queries. Under peak load (1,000 concurrent single-SKU requests during inventory planning), PostgreSQL IOPS saturated at 12,000 IOPS, causing query latency to exceed 3 seconds (target: 200ms). The API returned HTTP 504 timeouts for 45% of requests.

**Detection:** API latency alert fired when P99 exceeded 500ms. Time-to-detect: 3 minutes.

**Recovery steps:**
1. Pre-materialized feature table: Spark batch job computes lag features for all 10B SKU-store pairs nightly; stores in DynamoDB (key: sku_id + store_id + week_number, value: JSON feature vector).
2. API now reads features from DynamoDB (< 5ms single-item lookup) rather than computing from PostgreSQL.
3. Added feature staleness indicator: features computed as of "last_updated_at" timestamp; API returns this with forecast so callers can judge freshness.

**Prevention:** Load test the API at 2,000 concurrent requests before each major quarterly planning cycle. Feature computation must not be on the critical serving path for any latency-sensitive API.

---

## Capacity Planning

### Data Volume Projections

```
Year 0 (current):
  Scope: 1M SKUs × 10K stores = 10B SKU-store pairs
  Weekly training table: 72M rows (8 weeks history × 9M active SKU-store pairs)
  Row size: ~200 bytes (50 features + metadata)
  Training table: 72M × 200B = 14.4GB (fits in memory on r5.4xlarge)
  Parquet on S3 (52 weeks history): 10B × 52 × 200B = 104TB
  DynamoDB features: 10B × 1KB feature vector = 10TB (stored as S3 Parquet, DynamoDB for hot)

Year 1 (10% SKU growth):
  1.1M SKUs × 10K stores = 11B pairs
  Training table: ~15.8GB
  S3 historical: 114TB

Year 3 (50% growth + 2 new countries):
  1.5M SKUs × 15K stores = 22.5B pairs
  Training table: 36GB → may need distributed Spark training (GBM on Spark MLlib)
  Forecast store: DynamoDB at 22.5B × 500B forecasts = 11.25TB per week
  S3 forecast archive: 11.25TB × 52 = 585TB/year
```

### Training Compute Requirements

```
Global LightGBM Weekly Full Retrain:
  Dataset: 72M rows × 50 features = fits in 15GB RAM
  Hardware: r5.4xlarge (16 vCPU, 128GB RAM)
  LightGBM: 500 trees, max_depth=7, num_leaves=127
  Training time: 3 hours (parallel 16-thread)
  Cost: r5.4xlarge at $1.008/hr × 3hr = $3.02/week = $157/month

Quantile Models (P10, P50, P90):
  3 separate LightGBM models, same dataset
  Training time: 9 hours total (3 × 3hr)
  Cost: $9.07/week = $472/month

Daily Incremental Update:
  7M new rows (1 day of sales) added to rolling window
  Warm-start from previous model (100 additional trees)
  Hardware: r5.2xlarge (8 vCPU, 64GB RAM)
  Duration: 45 minutes
  Cost: $0.504/hr × 0.75hr = $0.38/run × 7 = $2.65/week = $138/month

Forecast Serving (batch scoring, weekly):
  Score 10B SKU-store pairs using LightGBM predict()
  Spark on EMR: 20-node cluster (r5.2xlarge), each node processes 500M rows
  Duration: 4 hours (parallel Spark UDF for LightGBM scoring)
  Cost: 20 × $0.504/hr × 4hr = $40/run = $209/month

Total monthly training + scoring cost: ~$976
```

### Serving Infrastructure

```
DynamoDB Feature Store (online API, <5ms):
  Capacity: 10B items × 1KB = 10TB (too large for hot DynamoDB at standard cost)
  Strategy: DynamoDB stores only "active" SKU-store pairs (2M pairs with sales in last 4 weeks)
  Active pairs: 2M × 1KB = 2GB — 10-node DynamoDB on-demand with 5ms read
  Cost: DynamoDB on-demand for 1M reads/day: ~$150/month + storage $0.25/GB = ~$155/month

REST API Service (single-SKU forecasting):
  Peak: 1,000 concurrent requests during planning cycles
  Each request: DynamoDB lookup 5ms + LightGBM inference 2ms = 7ms
  6× c5.xlarge (4 vCPU) API servers
  Cost: 6 × $0.17/hr = ~$733/month

Forecast Output Store (DynamoDB for inventory system consumption):
  10B forecasts refreshed weekly, ~100M reads/week from inventory system
  DynamoDB provisioned: 1,000 RCU (read capacity units) sustained
  Cost: 1,000 RCU × $0.00013/RCU-hr × 720hr = ~$94/month + storage = ~$250/month

S3 Storage (forecast archive, training data):
  Training data (52-week rolling): 104TB × $0.023/GB = $2,392/month
  Forecast archive (2-year retention): 585TB × $0.023/GB = $13,455/month
  Compressed 5:1: $2,392/month training + $2,691/month archive = ~$5,083/month

Total monthly infrastructure: ~$6,221
```

---

## Additional War Stories

**War Story 1 — Walk-Forward Cross-Validation With Data Leakage:**

```python
# BROKEN: Using simple k-fold cross-validation for time series evaluation
# Fold 3 (training) contains week 40; fold 3 (validation) contains week 35
# Model has "future knowledge" when predicting week 35 via week 40 training data

import numpy as np
import pandas as pd
import lightgbm as lgb
from sklearn.model_selection import KFold


def evaluate_forecast_broken(
    df: pd.DataFrame,
    feature_cols: list[str],
    label_col: str = "units_sold",
) -> dict[str, float]:
    """BROKEN: K-fold ignores time ordering — massive temporal leakage."""
    X = df[feature_cols].values
    y = df[label_col].values

    kf = KFold(n_splits=5, shuffle=True, random_state=42)  # BUG: shuffled
    maes = []
    for train_idx, val_idx in kf.split(X):
        model = lgb.LGBMRegressor(n_estimators=200, learning_rate=0.05)
        model.fit(X[train_idx], y[train_idx])
        preds = model.predict(X[val_idx])
        mae = np.abs(preds - y[val_idx]).mean()
        maes.append(mae)

    # Reported WMAE: 8.2% — looks great!
    # Actual production WMAE: 19.4% — 2.4x worse than reported
    return {"mae": np.mean(maes)}


# FIX: Walk-forward expanding window validation
# Train on weeks 1-40, validate on weeks 41-44
# Train on weeks 1-44, validate on weeks 45-48
# Etc. — never train on future data

def evaluate_forecast_correct(
    df: pd.DataFrame,
    feature_cols: list[str],
    label_col: str = "units_sold",
    week_col: str = "week_number",
    n_folds: int = 4,
    validation_weeks: int = 4,
) -> dict[str, float]:
    """
    Walk-forward cross-validation with expanding training window.
    Each fold trains on all data before validation period.
    """
    max_week = df[week_col].max()
    first_val_week = max_week - (n_folds * validation_weeks)

    all_maes: list[float] = []
    for fold in range(n_folds):
        val_start = first_val_week + fold * validation_weeks
        val_end = val_start + validation_weeks

        train_mask = df[week_col] < val_start  # strictly before validation window
        val_mask = (df[week_col] >= val_start) & (df[week_col] < val_end)

        X_train = df.loc[train_mask, feature_cols].values
        y_train = df.loc[train_mask, label_col].values
        X_val = df.loc[val_mask, feature_cols].values
        y_val = df.loc[val_mask, label_col].values

        if len(X_train) == 0 or len(X_val) == 0:
            continue

        model = lgb.LGBMRegressor(n_estimators=500, learning_rate=0.05)
        model.fit(X_train, y_train)
        preds = model.predict(X_val)
        mae = float(np.abs(preds - y_val).mean())
        all_maes.append(mae)
        print(f"Fold {fold}: val weeks [{val_start},{val_end}), MAE={mae:.4f}")

    return {"wmae": float(np.mean(all_maes))}
```

**War Story 2 — Promotion Lift Prediction Using Event-Window Instead of Causal Modeling:**

```python
# BROKEN: Computing promotion lift by comparing promoted weeks to adjacent weeks
# Adjacent weeks are affected by stock-up (pre-promotion) and hangover (post-promotion)
# This contamination makes the lift estimate 30-50% too low

import pandas as pd
import numpy as np


def estimate_promo_lift_broken(
    sales_df: pd.DataFrame,
    sku_id: str,
    promo_week: int,
) -> float:
    """BROKEN: Compare promo week to adjacent weeks — contaminated baseline."""
    sku_data = sales_df[sales_df["sku_id"] == sku_id].set_index("week")

    promo_sales = sku_data.loc[promo_week, "units_sold"]

    # BUG: week promo_week-1 has stock-up effect; week promo_week+1 has hangover
    # Both adjacent weeks are suppressed by the promotion itself
    adjacent_weeks = [promo_week - 2, promo_week - 1, promo_week + 1, promo_week + 2]
    baseline = sku_data.loc[
        [w for w in adjacent_weeks if w in sku_data.index], "units_sold"
    ].mean()

    return float(promo_sales / baseline) - 1.0  # lift as fraction


# FIX: Use matched control SKUs (never promoted during same period) as counterfactual
# Plus a regression discontinuity approach for the pre/post comparison

def estimate_promo_lift_correct(
    sales_df: pd.DataFrame,
    sku_id: str,
    promo_week: int,
    control_sku_ids: list[str],  # similar SKUs never promoted in this window
    pre_weeks: int = 4,
    post_weeks: int = 2,
) -> dict[str, float]:
    """
    Difference-in-Differences: compare treated vs control SKU trends.
    Accounts for pre-promotion stock-up and post-promotion hangover.
    """
    sku_data = sales_df[sales_df["sku_id"] == sku_id].set_index("week")
    control_data = sales_df[sales_df["sku_id"].isin(control_sku_ids)].groupby("week")["units_sold"].mean()

    pre_period = range(promo_week - pre_weeks - post_weeks, promo_week - post_weeks)
    # Exclude post_weeks immediately before promotion (potential stock-up)
    pure_baseline_period = range(promo_week - pre_weeks - post_weeks, promo_week - post_weeks)

    sku_pre_avg = sku_data.loc[
        [w for w in pure_baseline_period if w in sku_data.index], "units_sold"
    ].mean()
    control_pre_avg = control_data.loc[
        [w for w in pure_baseline_period if w in control_data.index]
    ].mean()

    sku_promo = sku_data.loc[promo_week, "units_sold"]
    control_promo = control_data.loc[promo_week] if promo_week in control_data.index else control_pre_avg

    # DiD: (treated_after - treated_before) - (control_after - control_before)
    did_lift = (sku_promo - sku_pre_avg) - (control_promo - control_pre_avg)
    relative_lift = did_lift / max(sku_pre_avg, 1e-6)

    return {
        "absolute_lift_units": float(did_lift),
        "relative_lift_pct": float(relative_lift * 100),
        "baseline_units": float(sku_pre_avg),
    }
```

---

## Monitoring and Drift Detection Deep-Dive

### Features That Drift Fastest

```
Feature                              Drift rate   Reason
──────────────────────────────────────────────────────────────────────────
is_on_promotion / discount_pct       Very high    Promotions change weekly by definition
rolling_4wk_avg_units                High         Demand itself shifts (trend + seasonality)
lag_7d_units (last week's sales)     High         Captures recent demand shocks
price_vs_category_median             Medium       Price changes across category shift median
competitor_out_of_stock_flag         Medium       Supply chain events; competitor launches
season_indicator (Christmas, etc.)   Predictable  Pre-known annual seasonality
product_age_weeks                    Low          Linear time variable; no surprise drift
```

### Forecast Bias Monitoring

```python
from dataclasses import dataclass
from typing import Optional
import numpy as np


@dataclass
class ForecastBiasReport:
    """Weekly bias report comparing forecast to actuals."""
    category: str
    week: int
    mean_error: float           # positive = overforecast, negative = underforecast
    wmae: float                 # Weighted Mean Absolute Error
    bias_ratio: float           # mean_forecast / mean_actual; should be 1.0 ± 0.05
    stockout_rate: float        # fraction of SKU-store weeks with stockout (actual=0)
    overstocked_rate: float     # fraction where forecast > 2× actual


def compute_forecast_bias(
    forecasts: np.ndarray,   # shape (N,) predicted units
    actuals: np.ndarray,     # shape (N,) actual units sold
    weights: Optional[np.ndarray] = None,  # optional sales-volume weights
) -> ForecastBiasReport:
    if weights is None:
        weights = np.ones(len(actuals))

    errors = forecasts - actuals
    wmae = float(np.average(np.abs(errors), weights=weights))
    mean_error = float(np.mean(errors))
    bias_ratio = float(forecasts.mean() / max(actuals.mean(), 0.01))
    stockout_rate = float((actuals == 0).mean())  # actual demand but zero sales
    overstocked_rate = float((forecasts > 2 * actuals).mean())

    return ForecastBiasReport(
        category="all",
        week=-1,
        mean_error=mean_error,
        wmae=wmae,
        bias_ratio=bias_ratio,
        stockout_rate=stockout_rate,
        overstocked_rate=overstocked_rate,
    )

# Alert thresholds
BIAS_ALERTS = {
    "bias_ratio_bounds": (0.95, 1.05),    # ±5% systematic bias triggers alert
    "wmae_regression_threshold": 0.03,    # WMAE increase > 3 percentage points
    "stockout_rate_increase": 0.05,       # stockout rate up > 5 percentage points
    "overstocked_rate_increase": 0.10,   # overstock rate up > 10 percentage points
}
```

### Retraining Triggers and Cadence

```
Cadence        Trigger                                    Action
──────────────────────────────────────────────────────────────────────────────
Weekly         Scheduled (Monday 2 AM)                    Full LightGBM retrain + MinT reconciliation
Daily          Scheduled (nightly)                        Incremental update (100 trees warm-start)
Triggered      bias_ratio outside (0.95, 1.05)            Retrain with feature audit
Triggered      WMAE degrades > 3pp vs trailing 4-week avg Emergency retrain + hyperparameter search
Triggered      New promotional event type (3-for-2, BOGO) Add new promo feature; retrain
Triggered      New store opens                            Update store metadata; include in next retrain
Triggered      New SKU launches in high-volume category   Prototypical forecast via KNN; monitor 8wks
Quarterly      Scheduled                                  Walk-forward cross-validation audit; hyperparameter tuning
```

---

## Additional Interview Questions

**How do you forecast for a product launched by a competitor that directly impacts your own demand?**
Competitor cannibalization is captured via external data signals: (1) Web scraping or third-party retail data (Nielsen, IRI) provides competitor pricing and promotional activity. (2) Category-level demand index: if total category sales drop 15% in a week coinciding with a competitor launch, the global model learns to attribute this to the category_demand_index feature rather than treating it as noise. (3) Causal inference: estimate the causal impact of the competitor's launch using a synthetic control method — identify a set of "donor" weeks and SKUs not affected by the competitor, construct a weighted average that matches pre-launch trends, use as counterfactual. The forecast for affected SKUs is then adjusted by the difference between observed and counterfactual. This requires a causal inference module separate from the main forecasting model.

**What is hierarchical reconciliation, and why is MinT preferred over proportional or bottom-up aggregation?**
Hierarchical reconciliation adjusts independently-produced forecasts at each level (SKU, category, store, region, national) so they are consistent — store forecasts sum to regional, regional to national. Bottom-up aggregation: use only the lowest-level forecasts, aggregate to all higher levels. Problem: lower-level forecasts have higher noise; errors accumulate upward. Top-down aggregation: use national forecast, distribute using historical proportions. Problem: ignores store-level patterns. Proportional reconciliation (middle ground): similar issues. MinT (Minimum Trace): computes a reconciled forecast vector that minimizes the total variance of the forecasting error across all levels simultaneously, using the covariance structure of the base forecast errors. MinT requires estimating the error covariance matrix (often approximated as diagonal for scalability). Result: 8-12% WMAE improvement vs bottom-up in typical retail applications, particularly at intermediate levels (store, region).

**How would you extend the system to produce probabilistic forecasts (full distribution, not just P10/P50/P90)?**
Three approaches: (1) Quantile regression forest: LightGBM supports quantile loss, producing P10/P50/P90 as separate models. Extending to 19 quantiles (P5 to P95 in 5% steps) requires 19 models but provides the full distribution. (2) Conformal prediction: train a point forecast model, then compute residuals on a calibration set. Conformalize the prediction intervals: for a target coverage of 90%, find the 90th percentile of calibrated residuals. This is distribution-free and computationally cheap. (3) DeepAR (Amazon): autoregressive RNN that models the full demand distribution via parametric families (Negative Binomial for count data, Student-t for continuous). DeepAR natively produces full predictive distributions but requires GPU training and is harder to interpret than LightGBM. For inventory safety stock optimization (requires the full distribution, not just percentiles), DeepAR or conformal prediction provide the necessary uncertainty estimates.

**How does the demand forecasting system integrate with dynamic pricing to create a feedback loop risk?**
The pricing engine uses demand forecasts to set prices: higher forecasted demand → higher price; lower demand → markdown. This creates a potential feedback loop: a forecast overestimation leads to higher pricing, which reduces actual demand, which appears to "validate" the lower demand expectation if the model is not careful. Breaking the loop: (1) Price elasticity features must be included in the forecasting model — the model must predict demand given a specific price, not just recent demand trends. (2) Training data must include the actual price as a feature, not just volume, so the model learns the price-demand relationship. (3) Monitor for Simpson's paradox: if overall demand appears stable while prices are rising, the model may be failing to account for price-induced demand suppression. (4) Periodically run price sensitivity experiments (randomly vary prices 5-10% for a short window) to estimate true elasticity independent of the model's learned behavior.

**How do you handle extreme outlier events (a product going viral on social media, causing 100x demand spike) in the training data?**
Extreme viral events violate the stationarity assumption of the forecasting model: they are one-time, nearly unpredictable occurrences that the model cannot generalize from. Including them in training data without handling damages the model for all similar products that did not go viral. Three approaches: (1) Outlier detection and capping: use a rolling z-score; if weekly sales exceed mean + 3 × std for that SKU, cap the training label at mean + 3 × std. This prevents the model from overfit to the outlier. (2) Separate event features: add a viral_event_flag feature when social media monitoring detects an anomalous spike. The model then learns the "viral event effect" explicitly, not as a baseline. (3) For forecasting purposes: when a viral event is occurring, the short-term forecast is overridden by a rule-based system that extrapolates the spike trajectory for 2 weeks, then decays back to baseline. The long-term forecast is unaffected.
