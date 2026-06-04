# Design a Dynamic Pricing System

> "Dynamic pricing is like a thermometer that adjusts the price dial in real time — but instead of measuring temperature, it measures supply-demand tension and willingness to pay."

**Key insight:** Dynamic pricing is not a prediction problem — it is a *decision* problem under uncertainty. The model predicts demand as a function of price; the optimizer then selects the price that maximizes revenue (or profit) subject to business constraints. Treating it purely as a regression problem (predict the best price) without an explicit optimization layer leads to myopic pricing that ignores margin, inventory, and competitive context.

Mental model: Think of pricing as moving along a demand curve in real time. At price $10, you sell 1,000 units; at $12, you sell 850; at $15, you sell 600. The revenue-maximizing price balances units sold against margin. Dynamic pricing continuously re-estimates this demand curve using live data and explores different price points to learn where the curve has shifted.

Why this system exists: A 1% improvement in pricing accuracy translates to a ~3% improvement in operating profit for most retailers (the McKinsey "1% rule"). At $1B GMV, that is $30M in incremental profit from better pricing alone — without selling a single additional unit.

---

## 1. Requirements Clarification

**Functional requirements:**
- Set prices for ~500k SKUs in real time, re-pricing every 15 minutes.
- Prices must respect hard constraints: floor price (cost + minimum margin), ceiling price (brand guidelines), and competitor price bands (stay within ±20% of median competitor price).
- Support multiple pricing objectives: revenue maximization, margin maximization, or sell-through (inventory clearance).
- Provide price elasticity estimates per SKU, category, and customer segment.
- A/B test infrastructure for evaluating new pricing strategies without global deployment. See [experimentation_and_online_evaluation.md](cross_cutting/experimentation_and_online_evaluation.md).

**Non-functional requirements:**
- Re-pricing latency: < 2 seconds to compute and push price updates for a batch of 10k SKUs.
- Model freshness: demand models retrained daily; price optimization runs every 15 minutes using the current model + live inventory signal.
- Availability: 99.9% — if the system fails, fall back to rule-based pricing (last computed price ± 5% drift cap).
- Audit trail: every price change must be logged with reason (model output, constraint binding, manual override).

**Out of scope:**
- Personalized pricing at the individual customer level (legal risk in regulated markets; separate system in B2B context).
- B2B contract pricing (negotiated, not algorithmic).
- Auction-based pricing (real-time bidding systems — covered by ads CTR prediction case study).

---

## 2. Scale Estimation

**SKU volume:** 500k active SKUs; 50k re-priced per 15-minute cycle (top-velocity SKUs; long tail re-priced daily).

**Re-pricing throughput:**
- 50k SKUs per 15 min = 3,333 SKUs/min = 55 SKUs/sec.
- Per-SKU computation: demand curve prediction (5ms) + optimizer (1ms) + constraint check (0.5ms) = ~7ms.
- Single-core throughput: 1,000/7ms ≈ 143 SKUs/sec.
- Required cores: ceil(55 / 143) = 1 core, but with 3× headroom = 3 cores.

**Demand model training:**
- Training data: 2 years of daily price × sales data per SKU = 365 × 2 × 500k = 365M rows.
- LightGBM demand model, 5k trees: ~20 minutes on 8 cores.
- Model size: ~25 MB per model × 50 category models (trained per category, not per SKU) = 1.25 GB.

**Event volume (for training data collection):**
- 5M daily page views with price exposure; 2% add-to-cart; 0.8% purchase.
- Impression events: 5M/day; purchase events: 40k/day.
- Storage: 5M × 200 bytes = 1 GB/day → 365 GB/year.

**Infrastructure cost:**
- Re-pricing service: 4 × c5.xlarge (99.9% HA with failover): $200/month.
- Demand model training: spot r5.4xlarge, 20 min/day = $18/month.
- Feature store: Redis (4 GB for live inventory + pricing signals): $60/month.
- Total: ~$278/month.

---

## 3. High-Level Architecture

```
 ┌──────────────────────────────────────────────────────────────────┐
 │                    SIGNAL INGESTION                              │
 │  Competitor prices    │   Inventory levels    │   Demand signals │
 │  (scrapers, APIs)     │   (WMS)               │   (clickstream)  │
 └──────────┬────────────┴──────────┬────────────┴─────────┬────────┘
            │                       │                      │
 ┌──────────▼───────────────────────▼──────────────────────▼────────┐
 │               FEATURE STORE (Redis + S3/Hive)                   │
 │  - Current price, competitor price, price gap                   │
 │  - Rolling demand: 1d/7d/28d sales velocity                     │
 │  - Inventory: stock level, days-of-supply, sell-through rate    │
 │  - Seasonality: day-of-week, holiday flags, campaign flags      │
 └──────────────────────────────┬───────────────────────────────────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         │                      │                      │
┌────────▼────────┐  ┌──────────▼──────────┐  ┌────────▼────────┐
│  DEMAND MODEL   │  │  RE-PRICING ENGINE  │  │  A/B TEST       │
│  (daily retrain)│  │  (every 15 min)     │  │  FRAMEWORK      │
│                 │  │                     │  │                 │
│  LightGBM per  │  │  1. Fetch features  │  │  Holdout SKUs  │
│  category       │  │  2. Predict demand  │  │  get shadow    │
│  Price elasti-  │  │     curve           │  │  prices; not   │
│  city output    │  │  3. Optimize price  │  │  pushed live   │
│                 │  │  4. Apply constraints│  │                 │
└────────┬────────┘  │  5. Push to catalog  │  └─────────────────┘
         │           └──────────┬──────────┘
         │                      │
┌────────▼────────┐  ┌──────────▼──────────┐
│  MODEL REGISTRY │  │  PRICE CATALOG      │
│  (MLflow)       │  │  (source of truth   │
└─────────────────┘  │   for all channels) │
                     └──────────┬──────────┘
                                │
                     ┌──────────▼──────────┐
                     │  DOWNSTREAM         │
                     │  Website/App/API    │
                     │  Email campaigns    │
                     │  Ads bid system     │
                     └─────────────────────┘
```

**Component inventory:**
- Signal ingestion: competitor price scraper (3rd-party API + in-house; 15-min cadence), WMS inventory feed (real-time webhook), clickstream demand signals (Kafka consumer).
- Feature store: Redis for real-time signals (< 1ms); S3/Hive for training-time features. See [feature_store_and_point_in_time_correctness.md](cross_cutting/feature_store_and_point_in_time_correctness.md).
- Demand model: LightGBM trained per product category; outputs demand curve (units as function of price).
- Re-pricing engine: Python service; for each SKU, queries demand model, runs gradient-based optimizer, applies constraints, writes to price catalog.
- Price catalog: PostgreSQL as master; cached in Redis for serving.

---

## 4. Component Deep Dives

### 4.1 Demand Model

The demand model predicts units_sold as a function of price and context features. A log-linear demand model is often sufficient and interpretable (the coefficient on log(price) is the price elasticity).

```python
import lightgbm as lgb
import numpy as np
import pandas as pd
from dataclasses import dataclass

@dataclass
class DemandFeatures:
    sku_id: str
    category: str
    current_price: float
    competitor_price: float
    price_gap_pct: float         # (our_price - comp_price) / comp_price
    inventory_level: int
    days_of_supply: float        # inventory / avg_daily_sales
    sell_through_rate_7d: float  # fraction of starting inventory sold in 7 days
    rolling_sales_7d: float
    rolling_sales_28d: float
    day_of_week: int             # 0=Monday
    is_holiday: bool
    is_campaign_active: bool
    log_price: float             # log(current_price) — key elasticity feature


def build_demand_training_data(
    price_history: pd.DataFrame,
    sales_history: pd.DataFrame,
) -> pd.DataFrame:
    """
    Join price observations with sales outcomes.
    CRITICAL: point-in-time join — sales for day T are joined with features
    observed at the *start* of day T, not at any later point.
    See feature_store_and_point_in_time_correctness.md.
    """
    merged = price_history.merge(
        sales_history,
        on=["sku_id", "date"],
        how="inner",
    )
    merged["log_price"] = np.log(merged["price"])
    merged["log_sales"] = np.log1p(merged["units_sold"])
    merged["price_gap_pct"] = (
        (merged["price"] - merged["competitor_price"]) / merged["competitor_price"]
    )
    return merged


def train_demand_model(
    training_data: pd.DataFrame,
    category: str,
) -> lgb.Booster:
    feature_cols = [
        "log_price", "price_gap_pct", "inventory_level", "days_of_supply",
        "sell_through_rate_7d", "rolling_sales_7d", "rolling_sales_28d",
        "day_of_week", "is_holiday", "is_campaign_active",
    ]
    X = training_data[feature_cols].values
    y = training_data["log_sales"].values  # log-transform for multiplicative errors

    dataset = lgb.Dataset(X, label=y, feature_name=feature_cols)
    params = {
        "objective": "regression",
        "metric": "rmse",
        "learning_rate": 0.05,
        "num_leaves": 31,
        "min_data_in_leaf": 50,
        "feature_fraction": 0.8,
        "verbose": -1,
    }
    return lgb.train(params, dataset, num_boost_round=300)


def predict_demand_at_price(
    model: lgb.Booster,
    features: DemandFeatures,
    candidate_price: float,
) -> float:
    """Predict expected units sold at a candidate price."""
    feat_vec = np.array([[
        np.log(candidate_price),
        (candidate_price - features.competitor_price) / features.competitor_price,
        features.inventory_level,
        features.days_of_supply,
        features.sell_through_rate_7d,
        features.rolling_sales_7d,
        features.rolling_sales_28d,
        features.day_of_week,
        float(features.is_holiday),
        float(features.is_campaign_active),
    ]])
    log_demand = model.predict(feat_vec)[0]
    return np.expm1(log_demand)  # reverse log1p transform
```

### 4.2 Price Optimizer

**Broken approach — naive argmax over discrete price grid:**

```python
# WRONG: searches over arbitrary price grid without respecting margin constraints
# and ignores that demand curve gradient provides a better search direction.

def optimize_price_naive(model, features, prices):
    revenues = []
    for p in prices:
        demand = predict_demand_at_price(model, features, p)
        revenues.append(p * demand)
    return prices[np.argmax(revenues)]  # BUG: ignores margin floor, inventory caps
```

**Correct approach — constrained gradient optimization:**

```python
from scipy.optimize import minimize_scalar
from dataclasses import dataclass

@dataclass
class PricingConstraints:
    floor_price: float          # cost + minimum margin
    ceiling_price: float        # brand maximum
    competitor_price: float
    max_competitor_gap_pct: float  # e.g., 0.20 = stay within ±20% of competitor
    inventory_level: int
    objective: str = "revenue"  # "revenue" | "margin" | "sell_through"


def optimize_price(
    model: lgb.Booster,
    features: DemandFeatures,
    constraints: PricingConstraints,
    cost_per_unit: float,
) -> tuple[float, dict]:
    """
    Find optimal price within [floor, ceiling] respecting competitor bands.
    Returns (optimal_price, diagnostics).
    """
    # Compute effective price bounds
    comp_lower = constraints.competitor_price * (1 - constraints.max_competitor_gap_pct)
    comp_upper = constraints.competitor_price * (1 + constraints.max_competitor_gap_pct)
    lower = max(constraints.floor_price, comp_lower)
    upper = min(constraints.ceiling_price, comp_upper)

    if lower > upper:
        # Constraints are infeasible: fall back to floor price
        return constraints.floor_price, {"binding_constraint": "infeasible_bounds"}

    def objective(price: float) -> float:
        demand = predict_demand_at_price(model, features, price)
        if constraints.objective == "revenue":
            return -(price * demand)  # minimize negative revenue
        elif constraints.objective == "margin":
            return -((price - cost_per_unit) * demand)
        else:  # sell_through: maximize units sold
            return -demand

    result = minimize_scalar(
        objective,
        bounds=(lower, upper),
        method="bounded",
        options={"xatol": 0.01},  # $0.01 price precision
    )

    optimal_price = round(result.x, 2)
    optimal_demand = predict_demand_at_price(model, features, optimal_price)
    diagnostics = {
        "optimal_price": optimal_price,
        "predicted_demand": optimal_demand,
        "expected_revenue": optimal_price * optimal_demand,
        "binding_constraint": "none",
        "lower_bound": lower,
        "upper_bound": upper,
    }
    # Flag which constraint is binding (useful for audit trail)
    if abs(optimal_price - lower) < 0.02:
        diagnostics["binding_constraint"] = "floor_or_competitor_lower"
    elif abs(optimal_price - upper) < 0.02:
        diagnostics["binding_constraint"] = "ceiling_or_competitor_upper"

    return optimal_price, diagnostics
```

### 4.3 Demand Curve Exploration with Contextual Bandits

The optimizer is only as good as the demand model. If the model has never observed a price point, its predictions are extrapolations. A contextual bandit layer allocates a small fraction of SKU × time windows to price exploration, allowing the demand model to learn from novel price points.

```python
import numpy as np
from dataclasses import dataclass, field

@dataclass
class PriceBanditState:
    sku_id: str
    base_price: float             # current optimal price
    exploration_radius_pct: float = 0.05  # explore ±5% around base price
    n_arms: int = 5               # number of candidate prices to try
    alpha: float = 1.0            # Thompson sampling beta distribution alpha
    beta_param: float = 1.0       # Thompson sampling beta distribution beta


def select_exploration_price(
    state: PriceBanditState,
    exploration_budget_pct: float = 0.02,  # 2% of SKUs get exploration prices
) -> tuple[float, bool]:
    """
    With probability exploration_budget_pct, return an exploration price.
    Otherwise return the base (exploit) price.
    Thompson sampling: sample from beta distribution to balance explore/exploit.
    """
    if np.random.random() > exploration_budget_pct:
        return state.base_price, False  # exploit

    # Generate candidate prices within exploration radius
    lower = state.base_price * (1 - state.exploration_radius_pct)
    upper = state.base_price * (1 + state.exploration_radius_pct)
    candidates = np.linspace(lower, upper, state.n_arms)

    # Thompson sampling: sample conversion rate estimate for each arm
    # Using Beta distribution as conjugate prior for conversion rate
    samples = np.random.beta(state.alpha, state.beta_param, size=state.n_arms)
    chosen_arm = np.argmax(samples)

    return candidates[chosen_arm], True  # explore


def update_bandit_state(
    state: PriceBanditState,
    price_used: float,
    units_sold: int,
    units_available: int,
) -> PriceBanditState:
    """
    Update Beta distribution parameters based on observed demand.
    Success = units_sold; failures = units_available - units_sold (impressions without purchase).
    """
    conversion_rate = units_sold / max(units_available, 1)
    # Beta-Binomial update
    state.alpha += units_sold
    state.beta_param += units_available - units_sold
    return state
```

### 4.4 Calibration and Demand Curve Validation

```python
def compute_price_elasticity(
    model: lgb.Booster,
    features: DemandFeatures,
    base_price: float,
    delta_pct: float = 0.01,
) -> float:
    """
    Numerical estimate of price elasticity at current price.
    Elasticity = % change in demand / % change in price.
    Typical values: -0.5 (inelastic) to -3.0 (elastic).
    Below -4.0 suggests model instability; above 0 is a bug.
    """
    demand_base = predict_demand_at_price(model, features, base_price)
    demand_up = predict_demand_at_price(model, features, base_price * (1 + delta_pct))
    elasticity = ((demand_up - demand_base) / demand_base) / delta_pct
    return elasticity


def validate_demand_model(
    model: lgb.Booster,
    held_out_data: pd.DataFrame,
) -> dict:
    """
    Validate demand model on hold-out time period.
    Key metric: MAPE on units sold; also check elasticity sign and magnitude.
    """
    features = held_out_data.drop(columns=["units_sold", "log_sales"])
    y_true = held_out_data["units_sold"].values
    y_pred = np.expm1(model.predict(features.values))

    mape = np.mean(np.abs(y_true - y_pred) / np.maximum(y_true, 1))
    return {
        "mape": mape,
        "rmse": np.sqrt(np.mean((y_true - y_pred) ** 2)),
        "median_error_pct": np.median(np.abs(y_true - y_pred) / np.maximum(y_true, 1)),
    }
```

---

## 5. Design Decisions & Tradeoffs

**Decision 1: Per-SKU model vs category-level model vs global model**

| Approach | Accuracy | Coverage (sparse SKUs) | Training cost | Cold start |
|----------|----------|----------------------|--------------|-----------|
| Per-SKU model | Highest | Poor (no model for new SKUs) | 500k models = infeasible | Severe |
| Per-category model | Good | Good (SKU inherits category) | 200 models, 20 min each | Mild |
| Global model with SKU embeddings | Good | Good (embedding similarity) | 1 model, 45 min | Mild |

Use per-category models for top-20 categories (80% of revenue), global model for the long tail. New SKUs inherit the category model immediately. See [model_selection_and_algorithm_choice](../model_selection_and_algorithm_choice/README.md).

**Decision 2: Revenue maximization vs margin maximization**

Revenue maximization is the wrong objective in most cases because it ignores COGS variation across SKUs. A high-COGS SKU priced to maximize revenue may still be sold at a loss. Production objective: maximize gross margin dollars = (price - cost) × predicted demand. Exception: clearance pricing (objective = minimize inventory × time, subject to floor = cost) and loss-leader pricing (objective = volume, used to drive basket growth for high-margin companion SKUs).

**Decision 3: Handling SUTVA in pricing A/B tests**

If you randomly assign SKUs to treatment/control (different price levels), customers can observe both prices and arbitrage or complain. SUTVA is violated. Solution: use geo-level A/B tests (treatment geographies get the new pricing strategy, control geographies keep current). This prevents within-session comparison. Alternatively, use holdback SKUs (test on 5% of SKUs in same category) with careful counterpart-matching to avoid cross-SKU effects. See [experimentation_and_online_evaluation.md](cross_cutting/experimentation_and_online_evaluation.md).

**Decision 4: Exploration rate**

Setting exploration_budget_pct too high reduces revenue (you're running suboptimal prices). Too low means the demand model is poorly calibrated for unobserved price points, leading to bad optimization decisions. Production recommendation: 2% of SKUs × 15-minute windows assigned to exploration. At 50k re-priced SKUs per cycle, this is 1,000 SKUs exploring at any time. The expected revenue loss from exploration is ~0.3% (price deviates ±5% for 2% of SKUs).

**Decision 5: Competitor price reaction**

Reactive pricing (always undercut competitor by X%) creates price wars and destroys industry margins. Production approach: set competitor price as a *constraint* (stay within ±20%), not an optimization target. Only react to sustained competitor price changes (price gap stable for > 2 hours), not momentary fluctuations (which may be errors). Rate-limit price changes: no more than 3 price changes per SKU per day to avoid customer frustration.

---

## 6. Real-World Implementations

**Amazon (1P Pricing):** Amazon's algorithmic pricing engine re-prices millions of SKUs per minute. Their core innovation is the "Buy Box" algorithm, which combines price, fulfillment speed, and seller rating into a single competitiveness score. For Amazon-owned inventory (1P), they use a demand model that incorporates cross-elasticity (raising the price of Product A increases demand for substitute Product B) — a complexity most retailers ignore. Their 2019 engineering post described using deep learning for demand curve estimation in high-velocity electronics categories, with simpler tree models for the long tail.

**Airbnb (Smart Pricing):** Airbnb's dynamic pricing (Smart Pricing) advises hosts on optimal nightly rates. Key challenge: hosts control the final price and often override Airbnb's suggestion, creating selection bias in the training data (observed prices are a mixture of algorithmic and human decisions). Their solution: use the algorithmic suggestion as an instrument in a two-stage demand estimation approach to correct for host-override selection bias.

**Uber (Surge Pricing):** Uber's surge pricing is a supply-demand balance mechanism: multiply the base price by a surge multiplier when demand > supply in a geohex. The pricing model estimates the supply response (how many additional drivers come online per $1 of surge) and the demand response (how many fewer riders request per $1 of surge), then finds the multiplier that clears the market. Key insight from Uber's research: supply response is stronger than demand response — drivers are highly price-responsive; riders are less elastic during peak demand.

**Booking.com (Accommodation Pricing):** Booking.com uses a combination of their own algorithmic pricing recommendations and hotel-controlled pricing. Their ML model predicts booking probability as a function of price, competitor rates, and remaining inventory. A key feature: "scarcity signal" — properties with < 3 remaining rooms receive a non-linear demand boost, justifying higher prices. They A/B tested this scarcity-aware model against a flat demand model and measured a 2.1% improvement in revenue per available room (RevPAR).

**Zalando (Fashion E-commerce):** Zalando runs markdown optimization — determining when and by how much to discount slow-moving inventory before the season ends. Their model balances two objectives: sell enough units before season end (minimize markdown waste) vs selling at the highest possible price (maximize margin). They model this as a stochastic dynamic programming problem over the remaining inventory × days-until-season-end state space, solving it via approximate DP. The system improved end-of-season sell-through by 8% and reduced markdown depth by 3pp.

---

## 7. Technologies & Tools

| Tool | Use case | Advantage | Limitation |
|------|----------|-----------|------------|
| LightGBM | Demand model (tabular features) | SHAP elasticity interpretation, handles missing data | Not designed for cross-elasticity between SKUs |
| `scipy.optimize` | Price optimization (single SKU) | Fast, no dependencies, handles bound constraints | Not parallelized for batch SKU optimization |
| Optuna | Hyperparameter tuning for demand model | Parallelizable TPE sampler | Overhead for daily retraining cycles |
| Kafka | Real-time inventory + demand signal ingestion | Low-latency event streaming | Ops complexity |
| Feast / Tecton | Feature store (competitor prices, inventory) | PIT correctness; Redis serving | Engineering investment |
| Statsmodels | Price elasticity regression (interpretable baseline) | OLS/log-log elasticity coefficients | Assumes linear demand; no interaction effects |

---

## 8. Operational Playbook

### Eval Pipeline
- **Revenue impact test (weekly):** Compare actual revenue for priced SKUs vs a matched control set with prices frozen at last week's levels. Measure revenue lift. Requires geo or holdout-SKU A/B test. See [experimentation_and_online_evaluation.md](cross_cutting/experimentation_and_online_evaluation.md).
- **Demand model validation (daily):** On a held-out time window (yesterday's unseen data), compute MAPE < 15% on units sold. Block model promotion if MAPE exceeds threshold.
- **Elasticity sanity check (per model update):** Price elasticities should be negative (-0.3 to -4.0 range) for 95%+ of SKUs. Flag any SKU where predicted elasticity > 0 (positive elasticity is a bug unless the SKU is a Giffen good, which is rare in practice).

### Observability
- Monitor distribution of price changes per 15-min cycle. If >10% of SKUs move by >5% in a single cycle, halt and alert (suggests a competitor price data feed error causing cascading price changes).
- Track price floor binding rate: if > 30% of SKUs are bound at the floor price, the model may be systematically over-predicting demand at higher prices.
- See [drift_monitoring_and_retraining.md](cross_cutting/drift_monitoring_and_retraining.md) for feature distribution monitoring.

### Incident Runbooks
1. **Price spiraling (rapid repeated price cuts):** Symptom: a SKU's price drops to floor in < 2 hours. Diagnosis: competitor is aggressively cutting price, system is reacting in a feedback loop. Mitigation: rate-limit price decreases to 1 per SKU per 4-hour window; apply minimum 2-hour lag before reacting to competitor price changes.
2. **Demand model returns extreme predictions (e.g., 10,000 units at $100 for a mid-range product):** Symptom: price optimizer drives to ceiling due to extreme demand prediction. Diagnosis: feature pipeline fed an outlier (inventory_level = 0 or a missing competitor price defaulting to 0). Mitigation: clamp demand predictions to [0, 5× historical max]; alert on-call. Resolution: fix feature pipeline null handling.
3. **Competitor price feed outage:** Symptom: all competitor_price features = NULL for 30+ minutes. Mitigation: substitute last known competitor price (cache in Redis with 24-hour TTL); widen constraint bands to ±30% to reduce sensitivity. Resolution: restore feed; re-run pricing cycle once feed is healthy.
4. **Regulatory escalation (personalized pricing complaint):** Mitigation: demonstrate that pricing is per-SKU × context, not per-customer — all users requesting the same SKU at the same time see the same price. Log every price change with features and output for compliance audit trail. See [responsible_ai_fairness_and_explainability.md](cross_cutting/responsible_ai_fairness_and_explainability.md).

---

## 9. Common Pitfalls & War Stories

**Pitfall 1: Training demand model without controlling for endogeneity.** A large retailer trained a demand model on historical price × sales data. The model found that higher prices correlated with *higher* sales for some SKUs — a positive elasticity. Root cause: SKUs that are popular (high demand) are also priced higher. The correlation was demand → price, not price → demand. The model learned the reverse causality. Fix: use instrumental variable regression or train on experimental price variation (randomized price tests) rather than historical observational data. The endogeneity-corrected model showed uniformly negative elasticity, and the new pricing strategy increased revenue by 4.2%.

**Pitfall 2: Price war from reactive pricing.** An electronics retailer configured their system to match competitors within 5 minutes of any price drop. Their competitors had the same system. The result: a race to the bottom on a popular laptop SKU — price went from $999 to $649 in 4 hours, destroying $3.2M in margin across the industry. Both companies eventually added reaction delays and minimum-margin floors, but the damage was done. Key lesson: reactive pricing policies must have dampening mechanisms (minimum reaction delay, maximum change per cycle).

**Pitfall 3: Exploration damaging customer trust.** A subscription SaaS company ran Thompson sampling on pricing plans, showing different prices to different user cohorts. A user on Twitter noticed that they were seeing $29/month while a colleague in the same city was seeing $25/month for the same plan. The resulting PR storm forced the company to homogenize pricing. Lesson: price exploration is best done at the channel/geography level, not individual user level. Contextual exploration (higher prices shown during high-demand periods) is perceived as fair; individual-level price discrimination is not.

**Pitfall 4: Ignoring cross-elasticity in large catalogs.** An e-commerce company optimized each SKU's price independently. A 15% price increase on a flagship headphone drove a 20% increase in revenue for that SKU — but overall headphone category revenue dropped because customers switched to substitute products. The per-SKU model had positive ROI when measured at the SKU level and negative ROI at the category level. Fix: include category-level demand signals as features (e.g., if your SKU's price increases 10%, include predicted demand shift to substitute SKUs). Category-level optimization increased total margin by 2.8% vs per-SKU optimization.

**Pitfall 5: Not accounting for inventory dynamics.** A fashion retailer deployed a revenue-maximizing pricing model without inventory awareness. The model priced slow-moving inventory at high prices (it predicted high demand because similar items had sold well at those prices). By end of season, 40% of slow-moving inventory remained unsold and had to be liquidated at 30% of cost. The fix: add days-of-supply as a feature and shift the objective to "sell-through prioritization" when days_of_supply < 30 and season_days_remaining < 45. This automatic markdown trigger eliminated 60% of end-of-season liquidation.

---

## 10. Capacity Planning

**Primary bottleneck:** Demand model prediction throughput for batch re-pricing.

```
Batch re-pricing: 50k SKUs per 15-min cycle
Model prediction: 10ms per SKU (feature fetch 7ms + inference 3ms)
Single-core throughput: 100 SKUs/sec
Required time for 50k SKUs: 500 seconds ≈ 8.3 min on 1 core
→ With 2 cores: 4.2 min; with 4 cores: 2.1 min (well within 15-min window)

Optimizer: 1ms per SKU (scipy minimize_scalar, bounded)
Total optimizer time: 50k × 1ms = 50 sec → not a bottleneck

Feature fetch: Redis GET for 20 features per SKU
Redis throughput: 50k/ms using pipeline(batch) → < 1 sec for 50k SKUs via pipelining
```

**Scaling to 5M SKUs (10× current):**
- Batch re-pricing: 500k SKUs per cycle. At 100 SKUs/sec per core: 5,000 sec on 1 core → parallelize across 50 cores (10 × r5.xlarge).
- Switch to vectorized batch prediction (LightGBM predict_proba on batch of 500k): 500k × 10ms → ~5 sec in batch mode.
- Redis: at 500k SKU feature fetches per 15 min, use cluster mode with 3 shards.
- Monthly infrastructure: $2,400 (10 × r5.xlarge for pricing service + expanded Redis).

---

## 11. Interview Discussion Points

**Q: What is price elasticity and how do you measure it in production?**
Price elasticity of demand is the ratio of percentage change in demand to percentage change in price. Elasticity = -1.5 means a 1% price increase causes a 1.5% demand decrease. In production, you measure elasticity empirically by training a log-linear demand model (log demand ~ log price + controls), where the coefficient on log price is the elasticity. The challenge is endogeneity: observed price correlates with demand for non-causal reasons (popular items are priced higher). Solution: train on randomized price experiments or use competitor prices as instruments. Validate that all estimated elasticities are negative and in the reasonable range (-0.3 for necessities to -4.0 for luxury/substitutable goods).

**Q: How do you design a pricing A/B test without SUTVA violations?**
You cannot randomly assign individual users to different prices for the same SKU — users will compare prices and complain, violating the stable unit treatment value assumption (SUTVA). Use one of: (1) geo-level A/B test (different pricing strategies in different geographies); (2) holdout SKUs (apply new strategy to 5% of SKUs, keep 5% at current prices, measure category-level revenue difference); (3) time-based switchback (alternate pricing strategies week-by-week in the same geographies). Geo-level is most common for pricing experiments. Switchback works for pricing rules that affect supply (e.g., surge pricing) where time-of-day matters more than geography.

**Q: How do you prevent a price war when competitors have the same reactive pricing system?**
Implement reaction dampening: (1) minimum delay — do not react to competitor price changes less than 2 hours old; (2) maximum change per cycle — cap price changes at ±8% per 15-minute window; (3) floor enforcement — never price below cost + minimum margin regardless of competitive signal; (4) trend detection — if competitor prices are monotonically decreasing, trigger human review rather than automatic reaction; (5) category-wide monitoring — if average category price drops > 10% in 24 hours, pause automated pricing and escalate.

**Q: How do you handle the cold-start problem for newly listed SKUs?**
New SKUs have no historical sales data to train a per-SKU demand model. Use the category model as the prior: new SKU inherits the demand curve shape of its category, using similar-attribute SKUs (same brand tier, price tier, product type) for initialization. After the first 7 days, blend the category model with the SKU-specific observed data using a weighted average (weight shifts to observed data as N increases). For the first 3 days, run at a conservative price (P50 of category price distribution) to gather elasticity data before optimizing.

**Q: How do you measure the ROI of dynamic pricing vs static pricing?**
Run a geo holdout experiment: assign 20% of geographies to a control group with static prices (frozen at the pre-deployment level), apply dynamic pricing to the remaining 80%. Measure revenue per available unit and gross margin over 8 weeks. Expected revenue lift in e-commerce: 2–5%; in travel/hospitality: 5–15%; in ride-sharing: 3–8%. Account for cannibalization: if dynamic pricing in one geo shifts demand to adjacent geos (customers shop in the cheaper geo), the holdout test will overstate the lift. Use geographically distant geographies as control to minimize spillover.

**Q: What are the fairness concerns with dynamic pricing?**
Three main concerns: (1) Price discrimination by income or location — if demand model features correlate with protected class membership (e.g., zip code correlates with race), the model can deliver different prices to different demographic groups. In the US, this is legal for most products but ethically problematic. Mitigation: audit price distributions by demographic proxy and test for disparate impact. (2) Essential goods pricing — dynamically pricing necessities (food, medicine) during emergencies is regulated or illegal in many jurisdictions. Design explicit caps. (3) Price gouging allegations — even legal dynamic pricing can generate severe PR risk. Implement maximum daily change caps (e.g., no more than 20% above baseline on any product in any day). See [responsible_ai_fairness_and_explainability.md](cross_cutting/responsible_ai_fairness_and_explainability.md).

**Q: How do you debug a demand model that predicts unreasonably high demand at high prices?**
Run a feature importance analysis: identify which features are most predictive, then sort historical data by those features and manually inspect whether the correlation is causal. Common root causes: (1) seasonal confounding — prices and demand are both high during holidays; the model learns price → demand when the causal direction is season → both; fix by adding seasonal features to the model; (2) selection bias — high-priced SKUs are only listed when inventory is scarce and demand is high; the model sees high price + high demand without understanding the inventory constraint; fix by adding days_of_supply as a feature; (3) data pipeline error — sales from a different period leaking into features via incorrect PIT join. Check [feature_store_and_point_in_time_correctness.md](cross_cutting/feature_store_and_point_in_time_correctness.md).

**Q: When should you use reinforcement learning instead of a demand model + optimizer?**
Use RL when: (1) prices have long-term effects that a myopic optimizer misses — e.g., subscription pricing where low acquisition price today drives high LTV revenue over 3 years; (2) the business has a complex multi-period objective (e.g., clear 80% of inventory by a deadline while maximizing cumulative revenue); (3) the decision frequency is high enough to make exploration computationally tractable (> 1,000 pricing decisions per SKU per week). RL is not worth the engineering overhead for: (1) SKUs with infrequent price changes, (2) situations where the demand curve is stable and well-estimated, (3) regulated categories where explainability of individual pricing decisions is required. For 90% of e-commerce SKUs, demand model + constrained optimizer outperforms RL at 1/10th the complexity.
