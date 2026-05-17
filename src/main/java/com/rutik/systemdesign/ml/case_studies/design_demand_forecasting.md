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
