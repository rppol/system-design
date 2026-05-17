# Time Series Forecasting

---

## 1. Concept Overview

Time series forecasting predicts future values of a sequentially ordered, time-indexed variable. Unlike cross-sectional prediction, observations are not independent — the value at time t depends on values at t-1, t-2, ..., t-k. Forecasting methods must respect temporal order, handle non-stationarity, and account for repeating seasonal patterns.

Applications: demand planning, financial price prediction, energy load forecasting, anomaly detection in metrics, product sales attribution, capacity planning.

---

## 2. Intuition

One-line analogy: Forecasting is like predicting tomorrow's weather — you look at what happened yesterday (trend), the same day last year (seasonality), and unexpected events (residuals), then combine them into a single estimate.

Mental model: Every time series = trend + seasonality + noise. STL decomposition separates these components. Statistical models (ARIMA) capture linear temporal dependencies. Deep learning models (LSTM, Temporal Fusion Transformer) capture non-linear dependencies and multiple covariates simultaneously.

Why it matters: Amazon reported a 10% reduction in forecast error reduces inventory cost by ~$1B. Netflix uses forecasting to pre-scale CDN nodes before major releases. Incorrect energy load forecasting causes grid instability or expensive spot purchases.

Key insight: The best forecasting model is not the most complex one — it is the one that best captures the dominant signal in your specific series. A well-tuned Prophet model beats a misconfigured LSTM 80% of the time on business time series.

---

## 3. Core Principles

1. Stationarity is a prerequisite for most statistical models — mean and variance must not change over time. Test with ADF; fix with differencing or log transformation.
2. Respect temporal order in all cross-validation — never shuffle time series data. Use walk-forward (expanding window) or sliding window validation.
3. Lag features encode temporal memory — the value at t-1, t-7, t-365 is often the strongest predictor of t.
4. Seasonal patterns have multiple periods — retail demand has daily, weekly, and yearly cycles simultaneously. Models must handle multiple seasonal periods.
5. Uncertainty quantification matters — a point estimate without confidence interval is misleading for planning. Use probabilistic forecasting (DeepAR, quantile regression) for inventory and capacity decisions.

---

## 4. Types / Architectures / Strategies

### 4.1 Classical Statistical Models

| Model | Parameters | Best For |
|-------|-----------|---------|
| Naive (last value) | 0 | Baseline; benchmark against this first |
| Simple Exponential Smoothing | alpha | No trend, no seasonality |
| Holt-Winters | alpha, beta, gamma | Trend + seasonality, short series |
| ARIMA(p,d,q) | 3 | Stationary after differencing, univariate |
| SARIMA(p,d,q)(P,D,Q,s) | 7 | ARIMA + seasonal component |
| VAR (Vector AR) | p | Multivariate, capturing cross-series dependencies |

### 4.2 Machine Learning Approaches

| Method | Input Features | Notes |
|--------|---------------|-------|
| LightGBM / XGBoost | Lag features, rolling stats, calendar | Fast, handles missing values, very competitive |
| Linear regression + features | Same as above | Interpretable; strong baseline |
| ElasticNet | High-dim lag matrix | Regularized; handles many correlated lags |

### 4.3 Deep Learning Models

| Model | Key Idea | Horizon | Probabilistic |
|-------|---------|---------|--------------|
| LSTM / GRU | Sequential hidden state | Short-medium | No (add quantile loss) |
| Temporal CNN (WaveNet) | Dilated causal convolutions | Long | No |
| DeepAR (Amazon) | Autoregressive LSTM, Gaussian/NB output | Medium | Yes |
| N-BEATS | Pure MLP, residual stacks | Medium | No |
| Temporal Fusion Transformer | Attention + LSTM + static covariates | Long | Yes |
| PatchTST | Patched transformer, channel independence | Long | No |

### 4.4 Feature Engineering for Time Series

| Feature Type | Examples | Notes |
|-------------|---------|-------|
| Lag features | t-1, t-7, t-14, t-365 | Most predictive; domain-specific lag selection |
| Rolling statistics | rolling_mean(7), rolling_std(30) | Capture local trend and volatility |
| Calendar features | day_of_week, month, is_holiday, week_of_year | Essential for retail, energy |
| Fourier features | sin/cos transforms of period | Smooth seasonality encoding |
| Target encoding | Mean sales by product category | For grouped hierarchical series |

---

## 5. Architecture Diagrams

### STL Decomposition

```
Original Series y_t
      |
      v
[STL Decomposition]
      |
      +--------> Trend (T_t)       -- long-range direction (linear, polynomial)
      |
      +--------> Seasonality (S_t) -- repeating pattern (weekly, yearly)
      |
      +--------> Residual (R_t)    -- noise, anomalies, unexplained variance
      |
  y_t = T_t + S_t + R_t   (additive)
  y_t = T_t * S_t * R_t   (multiplicative; use when variance scales with level)
```

### ARIMA Model

```
Original series y_t  -->  [Difference d times]  -->  Stationary series y'_t
                                                              |
                              +---------------------------------+
                              |
                     AR(p): y'_t = phi_1*y'_{t-1} + ... + phi_p*y'_{t-p}
                              +
                     MA(q): ... + theta_1*e_{t-1} + ... + theta_q*e_{t-q} + e_t
                              |
                     [Invert differencing to get y_hat_t]
```

### Prophet Additive Model

```
y(t) = g(t) + s(t) + h(t) + epsilon

g(t):  piecewise linear or logistic growth trend
         changepoints automatically detected (or manually specified)

s(t):  Fourier series seasonality
         yearly: N=10 Fourier terms
         weekly: N=3 Fourier terms

h(t):  holiday effect (user-provided calendar)

epsilon: Gaussian noise
```

### PyTorch LSTM Forecaster

```
Input: (batch, seq_len=168, features=8)
          |
     [LSTM: hidden=256, layers=2, dropout=0.2]
          |
     Last hidden state: (batch, 256)
          |
     [Linear: 256 -> forecast_horizon=24]
          |
     Output: (batch, 24)  -- next 24 hours
```

### Walk-Forward Validation

```
|<-- train -->|<-val->|                      fold 1
|<--- train ---->|<-val->|                   fold 2
|<----- train ----->|<-val->|               fold 3
|<------- train ------->|<-val->|           fold 4
                                  ^
                          val window slides forward
                          Never use future data to train
```

---

## 6. How It Works — Detailed Mechanics

### Stationarity Check and ARIMA with pmdarima

```python
import pmdarima as pm
from pmdarima.arima import ndiffs
from statsmodels.tsa.stattools import adfuller
import pandas as pd
import numpy as np
from typing import Tuple

def check_stationarity(series: pd.Series, alpha: float = 0.05) -> Tuple[bool, float]:
    """
    Augmented Dickey-Fuller test.
    H0: series has a unit root (non-stationary).
    Reject H0 if p-value < alpha -> series is stationary.
    Returns (is_stationary, p_value).
    """
    result = adfuller(series.dropna(), autolag="AIC")
    p_value = result[1]
    is_stationary = p_value < alpha
    print(f"ADF p-value: {p_value:.4f} | Stationary: {is_stationary}")
    return is_stationary, p_value


def fit_auto_arima(
    train: pd.Series,
    seasonal: bool = True,
    m: int = 12,           # seasonal period: 12 for monthly, 7 for daily, 52 for weekly
    max_p: int = 5,
    max_q: int = 5,
    information_criterion: str = "aic",
) -> pm.ARIMA:
    """
    auto_arima exhaustively searches over (p,d,q)(P,D,Q,m) combinations
    and selects the model minimizing AIC (or BIC).
    Typical best models for monthly business series: ARIMA(1,1,1)(0,1,1,12).
    """
    model = pm.auto_arima(
        train,
        seasonal=seasonal,
        m=m,
        max_p=max_p,
        max_q=max_q,
        information_criterion=information_criterion,
        stepwise=True,     # stepwise search: ~100x faster than exhaustive grid
        trace=True,
        error_action="ignore",
        suppress_warnings=True,
    )
    print(f"Best model: {model.order} x {model.seasonal_order}")
    return model


def evaluate_forecast(
    actuals: np.ndarray,
    predictions: np.ndarray,
) -> dict[str, float]:
    mae = np.mean(np.abs(actuals - predictions))
    rmse = np.sqrt(np.mean((actuals - predictions) ** 2))
    # SMAPE: handles near-zero actuals better than MAPE
    smape = 100 * np.mean(
        2 * np.abs(actuals - predictions) / (np.abs(actuals) + np.abs(predictions) + 1e-8)
    )
    return {"MAE": mae, "RMSE": rmse, "SMAPE": smape}
```

### Prophet with Changepoints and Regressors

```python
from prophet import Prophet
from prophet.diagnostics import cross_validation, performance_metrics
import pandas as pd
import numpy as np
from typing import Optional

def build_prophet_model(
    df: pd.DataFrame,            # columns: ds (datetime), y (target)
    holidays_df: Optional[pd.DataFrame] = None,
    changepoint_prior_scale: float = 0.05,   # flexibility of trend; 0.001=rigid, 0.5=very flexible
    seasonality_prior_scale: float = 10.0,
    extra_regressors: list[str] | None = None,
) -> Prophet:
    """
    Prophet defaults: yearly_seasonality=auto, weekly_seasonality=auto.
    changepoint_prior_scale: most important hyperparameter.
      - Too low: underfits trend changes (missing sales spikes)
      - Too high: overfits noise as changepoints
    """
    model = Prophet(
        holidays=holidays_df,
        changepoint_prior_scale=changepoint_prior_scale,
        seasonality_prior_scale=seasonality_prior_scale,
        seasonality_mode="multiplicative",   # use when variance scales with level
        yearly_seasonality=True,
        weekly_seasonality=True,
        daily_seasonality=False,
    )

    # Add additional Fourier seasonality (e.g., monthly pattern in daily data)
    model.add_seasonality(name="monthly", period=30.5, fourier_order=5)

    if extra_regressors:
        for regressor in extra_regressors:
            model.add_regressor(regressor)

    return model


def run_prophet_cv(
    model: Prophet,
    df: pd.DataFrame,
    initial: str = "730 days",    # training window for first fold
    period: str = "180 days",     # spacing between folds
    horizon: str = "90 days",     # forecast horizon to evaluate
) -> pd.DataFrame:
    """Walk-forward cross-validation using Prophet's built-in CV."""
    fitted = model.fit(df)
    cv_df = cross_validation(fitted, initial=initial, period=period, horizon=horizon)
    metrics = performance_metrics(cv_df)
    print(metrics[["horizon", "mae", "rmse", "smape"]].head(10))
    return metrics
```

### PyTorch LSTM Forecaster

```python
import torch
import torch.nn as nn
import numpy as np
import pandas as pd
from torch.utils.data import Dataset, DataLoader
from typing import Tuple

class TimeSeriesDataset(Dataset):
    def __init__(
        self,
        series: np.ndarray,        # shape: (n_timesteps, n_features)
        seq_len: int = 168,        # lookback window (e.g., 168 hours = 1 week)
        horizon: int = 24,         # steps to forecast
    ) -> None:
        self.series = torch.tensor(series, dtype=torch.float32)
        self.seq_len = seq_len
        self.horizon = horizon

    def __len__(self) -> int:
        return len(self.series) - self.seq_len - self.horizon + 1

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor]:
        x = self.series[idx : idx + self.seq_len]          # (seq_len, n_features)
        y = self.series[idx + self.seq_len : idx + self.seq_len + self.horizon, 0]  # target only
        return x, y


class LSTMForecaster(nn.Module):
    def __init__(
        self,
        input_size: int,
        hidden_size: int = 256,
        num_layers: int = 2,
        dropout: float = 0.2,
        forecast_horizon: int = 24,
    ) -> None:
        super().__init__()
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            dropout=dropout,
            batch_first=True,
        )
        self.fc = nn.Linear(hidden_size, forecast_horizon)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (batch, seq_len, input_size)
        out, _ = self.lstm(x)          # out: (batch, seq_len, hidden_size)
        last = out[:, -1, :]           # (batch, hidden_size) -- last timestep
        return self.fc(last)           # (batch, forecast_horizon)


def train_lstm(
    model: LSTMForecaster,
    train_loader: DataLoader,
    val_loader: DataLoader,
    epochs: int = 50,
    lr: float = 1e-3,
    device: str = "cuda" if torch.cuda.is_available() else "cpu",
) -> LSTMForecaster:
    model = model.to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, patience=5, factor=0.5, min_lr=1e-5
    )
    criterion = nn.HuberLoss(delta=1.0)   # robust to outliers vs MSE

    best_val_loss = float("inf")
    for epoch in range(epochs):
        model.train()
        train_losses = []
        for x_batch, y_batch in train_loader:
            x_batch, y_batch = x_batch.to(device), y_batch.to(device)
            optimizer.zero_grad()
            pred = model(x_batch)
            loss = criterion(pred, y_batch)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)  # prevent exploding gradients
            optimizer.step()
            train_losses.append(loss.item())

        model.eval()
        val_losses = []
        with torch.no_grad():
            for x_val, y_val in val_loader:
                x_val, y_val = x_val.to(device), y_val.to(device)
                pred_val = model(x_val)
                val_losses.append(criterion(pred_val, y_val).item())

        val_loss = np.mean(val_losses)
        scheduler.step(val_loss)
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            torch.save(model.state_dict(), "best_lstm.pt")
        if epoch % 10 == 0:
            print(f"Epoch {epoch}: train={np.mean(train_losses):.4f} val={val_loss:.4f}")

    model.load_state_dict(torch.load("best_lstm.pt"))
    return model
```

### Lag Feature Engineering

```python
import pandas as pd
import numpy as np

def create_lag_features(
    df: pd.DataFrame,
    target_col: str,
    lags: list[int] | None = None,
    rolling_windows: list[int] | None = None,
) -> pd.DataFrame:
    """
    Create lag and rolling window features for tree-based forecasting.
    Lags must be chosen based on domain: retail uses [1,7,14,28,365];
    hourly energy uses [1,24,168] (1 hour, 1 day, 1 week back).
    """
    if lags is None:
        lags = [1, 7, 14, 28]
    if rolling_windows is None:
        rolling_windows = [7, 14, 28]

    df = df.copy()
    for lag in lags:
        df[f"lag_{lag}"] = df[target_col].shift(lag)

    for window in rolling_windows:
        df[f"rolling_mean_{window}"] = (
            df[target_col].shift(1).rolling(window).mean()  # shift(1) prevents leakage
        )
        df[f"rolling_std_{window}"] = (
            df[target_col].shift(1).rolling(window).std()
        )

    # Calendar features
    df["day_of_week"] = df.index.dayofweek
    df["month"] = df.index.month
    df["week_of_year"] = df.index.isocalendar().week.astype(int)
    df["is_weekend"] = (df.index.dayofweek >= 5).astype(int)

    return df.dropna()
```

---

## 7. Real-World Examples

**Amazon demand forecasting:** Uses DeepAR across millions of product-region-warehouse combinations simultaneously. Each series shares a global model while having item-specific embeddings. Outputs a probability distribution over future demand, enabling inventory optimization at specific service levels (e.g., stock enough for 95th-percentile demand). Reduces stockouts by 15% versus per-series ARIMA models.

**Uber surge pricing:** LSTM-based models predict ride demand 30 minutes ahead at 250m hexagonal grid cells. Features: historical demand, events, weather, time-of-day, day-of-week. Prediction accuracy directly impacts driver positioning and surge multiplier calculation. Models retrain nightly; drift detection triggers emergency retraining if 7-day RMSE exceeds 20% above baseline.

**Electricity load forecasting (ENTSO-E):** National transmission operators use SARIMA and Gradient Boosting with weather covariates (temperature is the dominant feature — a 1C deviation causes ~1% load change in winter). Hierarchical forecasting reconciles national-level and regional-level forecasts to ensure they add up consistently.

**Meta (Facebook) Prophet in production:** Originally developed for business metric forecasting at scale (KPIs, ad revenue, engagement). Key design goal: non-statisticians can tune it via interpretable hyperparameters (changepoint_prior_scale, seasonality_mode). Handles multiple seasonalities and holiday effects out of the box. Scales to 1M rows in under 30 seconds on a single core.

---

## 8. Tradeoffs

| Dimension | ARIMA | Prophet | LSTM | LightGBM + lags | DeepAR |
|-----------|-------|---------|------|-----------------|--------|
| Training time | Seconds | <1 min | Hours | Minutes | Hours |
| Data required | 50+ points | 100+ points | 1K+ points | 500+ points | 1K+ per series |
| Multivariate | No (VAR) | Yes (regressors) | Yes | Yes | Yes |
| Probabilistic | Via ARCH | No (CI via Bootstrap) | No (quantile loss) | No | Yes (native) |
| Seasonality handling | SARIMA | Automatic | Manual encoding | Lag features | Learned |
| Interpretability | High | High | None | Medium | None |
| OOV series | No | No | No | No | Yes (global model) |

### Evaluation Metrics Comparison

| Metric | Formula | When to Use | Caution |
|--------|---------|-------------|---------|
| MAE | mean(|y - y_hat|) | Same-unit, robust to outliers | Scale-dependent |
| RMSE | sqrt(mean((y - y_hat)^2)) | Penalizes large errors | Sensitive to outliers |
| MAPE | mean(|y - y_hat| / y) * 100 | Scale-free comparison | Undefined when y=0; asymmetric |
| SMAPE | mean(2|y-y_hat|/(|y|+|y_hat|)) * 100 | Near-zero actuals | Bounded 0-200% |
| Pinball (quantile) | asymmetric L1 | Probabilistic forecasting | Different per quantile level |

---

## 9. When to Use / When NOT to Use

### When to Use ARIMA/SARIMA

- Short stationary univariate series with fewer than 1K observations
- Monthly or quarterly business KPIs with clear annual seasonality
- When model interpretability and coefficient confidence intervals are required
- When no compute budget is available for model training

### When to Use Prophet

- Business time series with multiple seasonalities and holiday effects
- Non-technical users who need to tune forecasts without statistical expertise
- Missing values or outliers (Prophet is robust to both)
- Rapid prototyping across many different series

### When to Use LSTM/Deep Learning

- High-frequency data (hourly, sub-hourly) with complex non-linear patterns
- Multiple input covariates (weather, promotions, competitor prices)
- Series length > 5K observations
- GPU infrastructure available

### When NOT to Use Deep Learning for Time Series

- Fewer than 500 observations per series (use statistical or tree-based methods)
- When inference latency must be under 10ms (LSTM forward pass is 5-20ms on CPU)
- When clear causal interpretation is required
- When series undergo structural breaks (LSTM cannot extrapolate beyond training distribution)

---

## 10. Common Pitfalls

### Pitfall 1: Shuffling time series before train/test split

```python
# BROKEN: random split leaks future data into training set
from sklearn.model_selection import train_test_split
X_train, X_test = train_test_split(X, test_size=0.2, random_state=42)  # WRONG

# FIXED: chronological split — last 20% of time is test set
split_idx = int(len(df) * 0.8)
train = df.iloc[:split_idx]
test = df.iloc[split_idx:]
```

Production incident: A retail demand forecast showed MAPE of 8% during development. Live MAPE was 31%. Root cause: lag features for t+7 were built from the full series before splitting, so test samples' lag features included their own future values.

### Pitfall 2: Using MAPE when actuals are near zero

Series with intermittent demand (many zeros) have undefined or infinite MAPE. A product selling 0 units on Monday and 2 on Tuesday: MAPE on Monday = (|0 - 0.5| / 0) = infinity. Use SMAPE or MAE for intermittent demand. Alternatively, add 1 to all actuals if the business allows (but this distorts the metric interpretation).

### Pitfall 3: Not scaling features for LSTM

```python
# BROKEN: LSTM on raw sales values (range 0-100,000)
# Hidden state gradients vanish on large input scales

# FIXED: MinMaxScaler or StandardScaler on training data only
from sklearn.preprocessing import MinMaxScaler
scaler = MinMaxScaler()
train_scaled = scaler.fit_transform(train_values.reshape(-1, 1))
test_scaled = scaler.transform(test_values.reshape(-1, 1))   # use train scaler
# Reverse at prediction time
predictions_original_scale = scaler.inverse_transform(predictions_scaled)
```

### Pitfall 4: Ignoring structural breaks in ARIMA

ARIMA assumes a fixed data-generating process. During COVID-19, retail series underwent irreversible structural breaks. An ARIMA model trained on 2015-2019 data predicted 2020 demand using pre-COVID seasonality coefficients, resulting in 200% overforecast for travel and 400% underforecast for cleaning supplies. Fix: detect changepoints (Ruptures library) and retrain post-break only; or use Prophet's changepoint mechanism.

### Pitfall 5: Horizon mismatch between training and inference

Training an LSTM with seq_len=30, horizon=1 and then iterating predictions to forecast 30 steps ahead (multi-step with recursive strategy) causes error accumulation. Each prediction is fed back as input; errors compound multiplicatively. Fix: train with the exact forecast horizon you need at inference time (direct multi-output strategy), accepting that you need separate models for different horizons.

---

## 11. Technologies & Tools

| Tool | Use Case | Notes |
|------|---------|-------|
| pmdarima | auto_arima, SARIMA fitting | Wraps statsmodels with sklearn API |
| statsmodels | ARIMA, VAR, STL, ADF test | Full statistical testing suite |
| Prophet (Meta) | Business time series | pip install prophet; Stan backend |
| NeuralForecast (Nixtla) | Deep learning models (NBEATS, TFT, DeepAR) | GPU-optimized, sklearn API |
| Darts | Unified API for all model types | Research-oriented |
| GluonTS (Amazon) | DeepAR, Temporal Fusion Transformer | MXNet/PyTorch backends |
| Sktime | ML pipeline for time series | sklearn-compatible |
| Ruptures | Changepoint detection | Offline and online algorithms |
| tsfresh | Automated feature extraction | Extracts ~800 features; use with tree models |
| LightGBM | Tree-based forecasting with lag features | Fastest non-neural approach |

---

## 12. Interview Questions with Answers

**Q: What is stationarity and why does ARIMA require it?**
A stationary time series has constant mean, variance, and autocorrelation structure over time. ARIMA's AR and MA components are defined by linear relationships between time-shifted versions of the series; these relationships are only stable and estimable when the process is stationary. Non-stationary series have trends (drifting mean) or heteroscedasticity (changing variance) that would cause ARIMA coefficients to be non-constant across time, making the model invalid. The ADF test checks for a unit root (the most common form of non-stationarity); differencing d times removes it.

**Q: How do you select p, d, q parameters in ARIMA?**
d is the number of differences needed to achieve stationarity — use the ADF test iteratively (d=0,1,2). p (AR order) is selected from the Partial ACF (PACF) plot: p is where the PACF cuts off sharply. q (MA order) is selected from the ACF plot: q is where the ACF cuts off. In practice, auto_arima from pmdarima automates this search using AIC/BIC model selection, making manual inspection of ACF/PACF less necessary except for diagnosis.

**Q: What is the difference between additive and multiplicative seasonality?**
In additive seasonality, seasonal fluctuations have constant absolute magnitude regardless of the trend level (e.g., always +100 units in December). In multiplicative seasonality, seasonal fluctuations scale with the level (e.g., December is always 30% above the annual average). Use additive when variance is constant over time; use multiplicative when variance grows with the series level (common in retail and finance). Prophet uses seasonality_mode="multiplicative" for revenue series. An easy diagnostic: if a log transformation makes the seasonal variation look constant, the original series is multiplicative.

**Q: How does Prophet model trend and seasonality?**
Prophet models y(t) = g(t) + s(t) + h(t) + epsilon. g(t) is a piecewise linear (or logistic) trend with automatically detected changepoints — locations where the trend slope changes. s(t) is a Fourier series: for yearly seasonality with N=10 terms, it has 20 parameters (sin and cos at each frequency). h(t) is a user-provided holiday effect represented as a window function centered on each holiday date. All components are fit jointly using Stan's L-BFGS optimizer. The key hyperparameter is changepoint_prior_scale (default 0.05), which controls trend flexibility.

**Q: What is walk-forward validation and why is it necessary for time series?**
Walk-forward validation simulates how a forecasting model would perform in production: the model is trained on all data up to time t and evaluated on t+1 through t+horizon, then the training window advances and the process repeats. This is necessary because: (1) standard k-fold would allow future data to appear in training folds; (2) temporal autocorrelation means shuffled splits produce optimistically biased estimates; (3) model performance typically degrades as forecast horizon increases, and walk-forward captures this degradation. Using sklearn's TimeSeriesSplit achieves expanding-window walk-forward.

**Q: When would you choose DeepAR over a per-series ARIMA model?**
DeepAR trains a single global LSTM model across thousands of related time series simultaneously. It is superior to per-series ARIMA when: (1) individual series are short (fewer than 100 observations) but the full dataset contains millions of (series, time) tuples — global information transfers across series; (2) multiple covariates (promotions, prices, weather) must be incorporated; (3) probabilistic outputs are required (DeepAR natively outputs a Gaussian or negative binomial distribution over future values). ARIMA is preferred when series are long (500+ points), completely independent, and interpretability of coefficients is required.

**Q: How do you handle hierarchical time series (national -> regional -> store)?**
Three approaches: (1) Bottom-up: forecast each leaf series independently and aggregate upward — preserves granularity but aggregation may not match top-level constraints; (2) Top-down: forecast the aggregate and disaggregate using historical proportions — smooth but loses local signals; (3) Optimal reconciliation (MinTrace): forecast all levels independently, then apply a linear projection that makes forecasts coherent (consistent across levels) while minimizing total variance. The statsforecast and sktime libraries implement MinTrace. For large hierarchies (10K+ nodes), bottom-up with shared global models (one LightGBM per level) is practical.

**Q: What is the difference between one-step-ahead and multi-step-ahead forecasting?**
One-step-ahead predicts t+1 given all observations up to t. Multi-step-ahead predicts t+1 through t+h simultaneously. Recursive strategy: iteratively predict t+1, append to history, predict t+2, etc. — errors accumulate at each step. Direct strategy: train h separate models, each predicting a specific future step — no error accumulation but h times the model count. MIMO (Multiple Input Multiple Output) strategy: a single model with h outputs trained jointly — balances accuracy and efficiency. LSTMs naturally implement MIMO; ARIMA naturally uses recursive strategy.

**Q: How do you detect and handle concept drift in a production forecasting system?**
Concept drift means the data-generating process has changed (e.g., market structure shift, new competitor, COVID). Detection: monitor rolling RMSE/SMAPE on a sliding window; alert when rolling metric exceeds 2 standard deviations above historical baseline. Two drift types: abrupt (sudden structural break — retrain from post-break data only) and gradual (slow evolution — use exponentially weighted training samples to give recent data higher weight). Ruptures library (PELT algorithm) detects changepoints in historical series. In production, automated retraining pipelines should trigger when drift score exceeds threshold, verified by human review before promoting the new model.

**Q: Why is gradient clipping important when training LSTMs for time series?**
LSTM hidden states can accumulate large magnitudes across long sequences, causing gradient norms to explode during backpropagation through time (BPTT). An exploding gradient update makes model weights jump to NaN or extreme values in a single step. Gradient clipping (nn.utils.clip_grad_norm_ with max_norm=1.0) caps the L2 norm of the gradient vector before the optimizer step, preventing instability without slowing training. The vanishing gradient problem in LSTMs is mitigated by the cell state and gating mechanism (unlike vanilla RNNs), but exploding gradients still occur on long sequences (seq_len > 100).

**Q: What are Fourier features and why are they used for seasonality encoding?**
Fourier features represent periodic patterns as sums of sine and cosine functions at specific frequencies. For a weekly period of 7 days, Fourier features are sin(2*pi*t/7), cos(2*pi*t/7), sin(4*pi*t/7), cos(4*pi*t/7), etc. Using K pairs captures the first K harmonics of the pattern. Advantages over one-hot day-of-week encoding: (1) smooth — Sunday and Monday are adjacent in Fourier space; (2) compact — K=3 pairs (6 features) vs 7 one-hot features; (3) handles any period (not just integer periods). Prophet uses Fourier features internally for both weekly and yearly seasonality.

---

## 13. Best Practices

1. Always establish a naive baseline (last observed value, or last year's same period) before fitting any model. Beat this baseline by at least 10% before claiming model value.
2. Use walk-forward cross-validation with at least 5 folds. Never use train/test split as the sole evaluation — it does not estimate variance across forecast periods.
3. Match the forecast horizon at training time to the horizon required at inference. For a 4-week demand forecast, train LSTM with horizon=28, not horizon=1 iterated 28 times.
4. Scale all features before feeding to LSTM or other gradient-based models. Fit the scaler on training data only; apply the same scaler to validation and test without refitting.
5. Use HuberLoss (delta=1.0) instead of MSE for LSTM training when the series contains outliers. Huber loss behaves like MSE for small errors but like MAE for large errors, preventing outlier-driven gradient explosions.
6. For Prophet, sweep changepoint_prior_scale over [0.001, 0.01, 0.05, 0.1, 0.5] using walk-forward CV. This single hyperparameter has the largest impact on forecast quality.
7. Add clip_grad_norm_(parameters, max_norm=1.0) to all LSTM training loops. This single line prevents the most common training instability.
8. When evaluating across multiple series with different scales, use scale-independent metrics (SMAPE, MASE) rather than MAE/RMSE. A low RMSE on a high-volume product can mask catastrophic errors on low-volume products.
9. Log-transform right-skewed series (e.g., sales counts, web traffic) before fitting ARIMA. Log transform stabilizes variance and often induces stationarity without differencing.
10. For production systems, implement automatic retraining triggers based on rolling metric monitoring. Batch-retrain monthly at minimum; event-driven retraining (on detected drift) is preferable.

---

## 14. Case Study

### Problem: Retail Sales Forecasting at SKU-Store Level

**Context:** A grocery retailer operates 500 stores, 20K SKUs per store — 10M time series. Goal: forecast daily unit sales 28 days ahead for replenishment ordering. Constraint: forecasts must be generated for all series in under 2 hours nightly.

**Approach Selection:**

Per-series ARIMA is infeasible (10M models * 10 seconds each = 115 days). Pure deep learning (LSTM per SKU) is too slow to retrain nightly. Solution: LightGBM with lag features as a global model — one model covers all SKUs.

**Feature Engineering:**

```
Lag features:   lag_1, lag_7, lag_14, lag_28, lag_365
Rolling stats:  rolling_mean_7, rolling_mean_28, rolling_std_7
Calendar:       day_of_week, month, week_of_year, is_holiday, days_until_holiday
Store features: store_id (categorical), store_size, region
SKU features:   category (categorical), subcategory, price, is_on_promotion
```

**Walk-Forward Validation Results:**

| Model | SMAPE | Training Time | Notes |
|-------|-------|--------------|-------|
| Naive (last week) | 28.4% | 0s | Baseline |
| ARIMA per SKU (sample) | 19.2% | 10s/series | Not scalable |
| LightGBM global | 14.7% | 8 min | Scales to 10M series |
| LightGBM + Prophet residuals | 13.1% | 20 min | Blend: LGB for trend, Prophet for holidays |

**Production Pipeline:**

```
Nightly 2AM:
  [Data pull from DWH: 90 days history per series]
       |
  [Feature engineering: pandas, 15 min]
       |
  [LightGBM inference: 28-day forecast per series, 40 min on 32-core VM]
       |
  [Reconciliation: store-level sums match regional forecasts]
       |
  [Write to replenishment system: 5 min]
       |
  [Drift monitor: compute SMAPE on last 7 days actual vs previous forecast]
       |
  [Alert if SMAPE > 25% on >5% of SKUs -> page on-call]
```

**Key Decisions:**
- LightGBM over LSTM: 10x faster inference, no GPU required, better on sparse intermittent series
- Global model over per-series: shared patterns (day-of-week, holidays) transfer across 10M series
- Rolling SMAPE monitoring: catches concept drift before ordering errors reach stores
- 28-day direct horizon: separate LightGBM output node per forecast day to avoid error accumulation
