# Causal Inference and ML

## 1. Concept Overview

Causal inference is the discipline of estimating the effect of interventions — not just predicting outcomes from observations. Standard ML models learn correlations: P(Y|X). Causal inference estimates counterfactuals: P(Y|do(X=x)) — what would happen if we forcefully set X to x, regardless of how X is usually determined.

This distinction is critical in production settings. A model predicting conversion rate may learn that users who receive a discount coupon convert at 40% vs. 10% for non-coupon users. But if coupons are given to already-intent-to-buy users (selection bias), the causal effect of the coupon might be near zero. Deploying discounts to all users based on this correlation wastes money.

Causal ML combines the flexibility of machine learning with the identification strategies of econometrics to estimate treatment effects robustly from observational data.

---

## 2. Intuition

One-line analogy: correlation tells you to carry an umbrella when it rains; causation tells you that carrying an umbrella does not make it rain.

Mental model: imagine two worlds — one where a user received the intervention (treated), one where they did not (control). We only observe one world per user. Causal inference estimates the difference between these two parallel worlds using statistical assumptions and design.

Why it matters: online experimentation (A/B testing) is the gold standard for causal estimation but is expensive, slow, and sometimes impossible (you cannot randomize who gets cancer). Causal ML provides tools to extract causal signals from observational data, enabling faster and broader business decisions.

Key insight: the fundamental problem of causal inference is that we cannot observe the same unit in both treated and untreated states. All causal methods are strategies for estimating the missing counterfactual, with different assumptions about what we must measure and control for.

---

## 3. Core Principles

**Potential outcomes framework (Rubin Causal Model):**
For binary treatment T in {0,1}:
- Y_i(1) = outcome if unit i receives treatment
- Y_i(0) = outcome if unit i does not receive treatment
- Individual Treatment Effect (ITE): tau_i = Y_i(1) - Y_i(0)
- Observed outcome: Y_i = T_i * Y_i(1) + (1-T_i) * Y_i(0)
- Fundamental problem: only one potential outcome is observed per unit.

**Average Treatment Effect (ATE):** E[Y(1) - Y(0)] — average over the entire population.

**ATT (Average Treatment Effect on the Treated):** E[Y(1) - Y(0) | T=1] — effect among those who actually received treatment. Relevant when treatment assignment is not random.

**CATE (Conditional ATE, Heterogeneous Treatment Effects):** tau(x) = E[Y(1) - Y(0) | X=x] — effect for a subpopulation characterized by covariates x. This is the target for uplift modeling.

**Identification assumptions (required for observational causal inference):**
1. SUTVA (Stable Unit Treatment Value Assumption): no interference between units; one version of treatment.
2. Consistency: Y_i = Y_i(T_i) — observed outcome equals potential outcome under observed treatment.
3. Positivity (overlap): 0 < P(T=1|X=x) < 1 for all x — every subgroup has some treated and control units.
4. Ignorability (unconfoundedness): Y(0), Y(1) ⊥ T | X — all confounders are measured and included in X.

**do-calculus (Pearl):** formal language for interventional queries. P(Y|do(X=x)) is the distribution of Y when X is forcefully set to x, breaking its natural causal parents. When ignorability holds: P(Y|do(X=x)) = integral P(Y|X=x, Z=z) P(Z=z) dz (adjustment formula over confounders Z).

---

## 4. Types / Architectures / Strategies

**Propensity Score Methods:**
- Propensity score e(x) = P(T=1|X=x) estimated via logistic regression or gradient boosting.
- Matching: pair each treated unit with a control unit of similar propensity. Estimate ATT as mean difference in matched pairs.
- IPW (Inverse Probability Weighting): weight outcomes by 1/e(x) for treated, 1/(1-e(x)) for control. Creates a pseudo-population where treatment is independent of covariates.
- Doubly robust estimator: combines outcome model and propensity model — consistent if either is correctly specified.

**Meta-learners (CATE estimation):**

| Learner | Description | Best For |
|---|---|---|
| S-learner | Single model with T as feature: mu(x, t) | Simple baseline; assumes smooth treatment effect |
| T-learner | Two separate models mu_1(x) and mu_0(x); CATE = mu_1 - mu_0 | Works well with large datasets |
| X-learner | Iteratively refine T-learner estimates using cross-residuals; weight by propensity | Imbalanced treatment groups |
| R-learner | Residualize Y and T on X, regress Y-residuals on T-residuals | Double ML style; handles confounding |
| DR-learner | Doubly robust CATE pseudo-outcome: tau_i_DR = IPW-adjusted estimate | Robust to model misspecification |

**Causal Forest (Wager & Athey 2018):**
Modification of random forests where splits maximize heterogeneity in treatment effects (not outcome variance). Each leaf estimates local CATE. Provides confidence intervals via infinitesimal jackknife. Implemented in EconML and grf R package.

**Double ML (Chernozhukov 2018):**
Partialling out confounders using cross-fitting:
1. Regress Y on X (using any ML model) to get residuals Y_tilde = Y - E[Y|X].
2. Regress T on X (using any ML model) to get residuals T_tilde = T - E[T|X].
3. Regress Y_tilde on T_tilde: theta = cov(Y_tilde, T_tilde) / var(T_tilde).
The estimate theta is the ATE, debiased via the orthogonalization step.

**Difference-in-Differences (DiD):**
Compare pre/post change in outcomes for treated vs. control groups. Assumes parallel trends: absent treatment, treated group would have trended like control. ATE_DiD = (Y_treated_post - Y_treated_pre) - (Y_control_post - Y_control_pre).

**Instrumental Variables (IV):**
When unobserved confounders exist, use instrument Z (affects T but has no direct effect on Y).
Two-stage least squares: (1) regress T on Z to get T_hat, (2) regress Y on T_hat. Estimates Local ATE (LATE): effect among compliers (units whose treatment changed because of Z).

---

## 5. Architecture Diagrams

```
Confounding — Why Naive ML Fails
==================================

  Naive: observe P(Y | T)

  Confounder U
      /      \
     v        v
     T ------> Y

  U causes both T and Y.
  P(Y|T=1) != P(Y|do(T=1)) because U is unblocked.

  Example: P(hospital_death | admitted=1) > P(hospital_death | admitted=0)
  Does hospital admission cause death? No — sicker people are admitted.
  U = sickness level confounds T (admission) and Y (death).


Backdoor Adjustment
====================

  X (covariates) blocks the backdoor path U -> T:

  X -> T -> Y    (causal path)
  X -> Y         (direct path, if any)
  X blocks U's influence when X includes all confounders

  P(Y|do(T)) = sum_x P(Y|T, X=x) P(X=x)  [adjustment formula]


Propensity Score Matching
===========================

  All units                 Treated (T=1)  Control (T=0)
  X1, X2, X3 ------->      e(x) = 0.7     e(x) = 0.69  <- matched pair
  Logistic Regression       e(x) = 0.3     e(x) = 0.31  <- matched pair
     |                      e(x) = 0.9     (no match)   <- trimmed
  Propensity e(x)
     |
  Match on e(x) (within caliper 0.05)
     |
  ATT = mean(Y_treated - Y_matched_control)


Uplift Modeling — T-Learner
=============================

  Training data split by treatment:

  Treated (T=1)         Control (T=0)
  X -> Y                X -> Y
  Train mu_1(x)         Train mu_0(x)

  CATE(x) = mu_1(x) - mu_0(x)

  Targeting:
  CATE > threshold  -> treat (persuadables)
  CATE ~ 0          -> skip (sure things or lost causes)
  CATE < 0          -> do not treat (sleeping dogs)


Double ML Pipeline
===================

  [Step 1: Nuisance estimation via cross-fitting (K=5 folds)]
    Fold 1: train on folds 2-5, predict fold 1 outcomes -> Y_hat
    ...repeat for all folds -> full out-of-sample Y_hat, T_hat

  [Step 2: Residualize]
    Y_tilde = Y - Y_hat   (outcome residual)
    T_tilde = T - T_hat   (treatment residual)

  [Step 3: Final regression]
    theta = regression(Y_tilde ~ T_tilde)
    -> ATE estimate with valid confidence intervals
```

---

## 6. How It Works — Detailed Mechanics

```python
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression, LinearRegression
from sklearn.ensemble import GradientBoostingClassifier, GradientBoostingRegressor
from sklearn.model_selection import cross_val_predict, KFold
from sklearn.preprocessing import StandardScaler
from typing import Tuple, Optional
import warnings
warnings.filterwarnings('ignore')


# ── Propensity Score Estimation and IPW ──────────────────────────────────────

def estimate_propensity_scores(
    X: np.ndarray,
    T: np.ndarray,
    model_type: str = 'logistic',
) -> np.ndarray:
    """
    Estimate e(x) = P(T=1 | X=x).
    Use out-of-fold predictions to avoid overfitting.
    """
    if model_type == 'logistic':
        model = LogisticRegression(C=1.0, max_iter=1000)
    else:
        model = GradientBoostingClassifier(n_estimators=200, max_depth=4)

    # Out-of-fold propensity scores (5-fold cross-fitting)
    propensities = cross_val_predict(model, X, T, cv=5, method='predict_proba')
    return propensities[:, 1]   # P(T=1|X)


def ipw_ate_estimate(
    Y: np.ndarray,
    T: np.ndarray,
    propensity: np.ndarray,
    trim_threshold: float = 0.05,
) -> Tuple[float, float]:
    """
    Inverse Probability Weighting (IPW) ATE estimator.
    Trim propensities outside [trim_threshold, 1 - trim_threshold] to
    avoid extreme weights (common support enforcement).
    """
    # Trim: enforce positivity
    mask = (propensity > trim_threshold) & (propensity < 1 - trim_threshold)
    Y, T, propensity = Y[mask], T[mask], propensity[mask]

    # IPW weights
    w = np.where(T == 1, 1 / propensity, 1 / (1 - propensity))

    # Normalized IPW (Hajek estimator — more stable than Horvitz-Thompson)
    treated_mask = T == 1
    control_mask = T == 0

    mu1_hat = np.sum(w[treated_mask] * Y[treated_mask]) / np.sum(w[treated_mask])
    mu0_hat = np.sum(w[control_mask] * Y[control_mask]) / np.sum(w[control_mask])

    ate = mu1_hat - mu0_hat
    n_trimmed = np.sum(~mask)
    return ate, n_trimmed


def propensity_score_matching(
    X: np.ndarray,
    T: np.ndarray,
    Y: np.ndarray,
    propensity: np.ndarray,
    caliper: float = 0.05,
    n_matches: int = 1,
) -> Tuple[float, int]:
    """
    Nearest-neighbor propensity score matching.
    Match each treated unit to n_matches control unit(s) within caliper.
    Returns ATT estimate and number of matched pairs.
    """
    treated_idx = np.where(T == 1)[0]
    control_idx = np.where(T == 0)[0]

    matched_diffs: list[float] = []
    for ti in treated_idx:
        e_ti = propensity[ti]
        # Find control units within caliper
        diffs = np.abs(propensity[control_idx] - e_ti)
        within_caliper = control_idx[diffs <= caliper]

        if len(within_caliper) == 0:
            continue  # no match found within caliper, discard

        # Take closest match(es)
        closest = within_caliper[np.argsort(diffs[diffs <= caliper])[:n_matches]]
        y_control_match = Y[closest].mean()
        matched_diffs.append(Y[ti] - y_control_match)

    att = float(np.mean(matched_diffs)) if matched_diffs else float('nan')
    return att, len(matched_diffs)


# ── T-Learner CATE estimator ─────────────────────────────────────────────────

class TLearner:
    """
    Two models: one for treated, one for control.
    CATE(x) = mu_1(x) - mu_0(x).
    """
    def __init__(self, n_estimators: int = 200, max_depth: int = 5) -> None:
        self.mu1 = GradientBoostingRegressor(n_estimators=n_estimators,
                                              max_depth=max_depth)
        self.mu0 = GradientBoostingRegressor(n_estimators=n_estimators,
                                              max_depth=max_depth)

    def fit(self, X: np.ndarray, T: np.ndarray, Y: np.ndarray) -> 'TLearner':
        self.mu1.fit(X[T == 1], Y[T == 1])
        self.mu0.fit(X[T == 0], Y[T == 0])
        return self

    def predict_cate(self, X: np.ndarray) -> np.ndarray:
        return self.mu1.predict(X) - self.mu0.predict(X)


# ── S-Learner ────────────────────────────────────────────────────────────────

class SLearner:
    """
    Single model with T as a feature.
    CATE(x) = mu(x, T=1) - mu(x, T=0).
    Risk: treatment effect gets regularized away if weak.
    """
    def __init__(self) -> None:
        self.model = GradientBoostingRegressor(n_estimators=200, max_depth=5)

    def fit(self, X: np.ndarray, T: np.ndarray, Y: np.ndarray) -> 'SLearner':
        # Concatenate T as a feature
        XT = np.column_stack([X, T])
        self.model.fit(XT, Y)
        return self

    def predict_cate(self, X: np.ndarray) -> np.ndarray:
        n = X.shape[0]
        X1 = np.column_stack([X, np.ones(n)])   # treated
        X0 = np.column_stack([X, np.zeros(n)])  # control
        return self.model.predict(X1) - self.model.predict(X0)


# ── Double ML (DML) for ATE ──────────────────────────────────────────────────

def double_ml_ate(
    X: np.ndarray,
    T: np.ndarray,
    Y: np.ndarray,
    n_folds: int = 5,
) -> Tuple[float, float]:
    """
    Double ML / Partial Linear Model.
    Returns (ATE estimate, standard error).
    """
    kf = KFold(n_splits=n_folds, shuffle=True, random_state=42)

    Y_tilde = np.zeros_like(Y, dtype=float)
    T_tilde = np.zeros_like(T, dtype=float)

    for train_idx, test_idx in kf.split(X):
        X_train, X_test = X[train_idx], X[test_idx]
        Y_train, Y_test = Y[train_idx], Y[test_idx]
        T_train, T_test = T[train_idx], T[test_idx]

        # Fit outcome model E[Y|X] on train, residualize test
        y_model = GradientBoostingRegressor(n_estimators=200)
        y_model.fit(X_train, Y_train)
        Y_tilde[test_idx] = Y_test - y_model.predict(X_test)

        # Fit treatment model E[T|X] on train, residualize test
        t_model = GradientBoostingClassifier(n_estimators=200)
        t_model.fit(X_train, T_train)
        T_tilde[test_idx] = T_test - t_model.predict_proba(X_test)[:, 1]

    # Final regression: Y_tilde ~ T_tilde (no intercept — both are residuals)
    # theta = cov(Y_tilde, T_tilde) / var(T_tilde)
    theta = np.dot(T_tilde, Y_tilde) / np.dot(T_tilde, T_tilde)

    # Standard error via influence function
    psi = T_tilde * (Y_tilde - theta * T_tilde)
    se = np.sqrt(np.mean(psi ** 2) / (np.mean(T_tilde ** 2) ** 2) / len(Y))

    return theta, se


# ── Causal Forest via EconML ─────────────────────────────────────────────────

def causal_forest_cate(
    X: np.ndarray,
    T: np.ndarray,
    Y: np.ndarray,
) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    Estimate CATE with confidence intervals using CausalForestDML from EconML.
    Returns (cate_point_estimates, lower_ci, upper_ci).
    """
    try:
        from econml.dml import CausalForestDML
        from sklearn.ensemble import GradientBoostingRegressor, GradientBoostingClassifier

        est = CausalForestDML(
            model_y=GradientBoostingRegressor(n_estimators=200),
            model_t=GradientBoostingClassifier(n_estimators=200),
            n_estimators=4000,         # forest trees
            min_samples_leaf=5,
            max_depth=None,
            inference=True,            # enable confidence intervals
            random_state=42,
        )
        est.fit(Y, T, X=X)

        cate = est.effect(X)
        cate_interval = est.effect_interval(X, alpha=0.05)  # 95% CI
        return cate, cate_interval[0], cate_interval[1]

    except ImportError:
        raise ImportError("Install EconML: pip install econml")


# ── Uplift model evaluation: Qini curve ──────────────────────────────────────

def qini_coefficient(
    Y: np.ndarray,
    T: np.ndarray,
    uplift_score: np.ndarray,
) -> float:
    """
    Qini coefficient: area between the model's Qini curve and random targeting.
    Higher = better at identifying persuadables.
    Theoretical max Qini = 0.5.
    """
    df = pd.DataFrame({'Y': Y, 'T': T, 'score': uplift_score})
    df = df.sort_values('score', ascending=False).reset_index(drop=True)

    n = len(df)
    n_treated = (df['T'] == 1).sum()
    n_control = (df['T'] == 0).sum()

    qini_values: list[float] = []
    for k in range(1, n + 1):
        top_k = df.iloc[:k]
        n_t = (top_k['T'] == 1).sum()
        n_c = (top_k['T'] == 0).sum()
        if n_t == 0 or n_c == 0:
            qini_values.append(0.0)
            continue
        # Incremental gains: treated conversions - scaled control conversions
        incr = top_k[top_k['T'] == 1]['Y'].sum() - \
               top_k[top_k['T'] == 0]['Y'].sum() * (n_t / n_c)
        qini_values.append(incr / n_treated)

    # Area between model curve and random (diagonal)
    qini_curve = np.array(qini_values)
    random_curve = np.linspace(0, qini_curve[-1], n)
    return float(np.trapz(qini_curve - random_curve) / n)
```

**Practical validation:** since true CATE is unobservable, use:
- Synthetic data with known treatment effects for algorithm validation
- Qini/AUUC curve for ranking validation (do top-scored users respond better?)
- A/B test the uplift model: treat top quintile of uplift scores vs. random, measure lift
- Placebos/falsification tests: treatment effects should be zero for pre-treatment outcomes

---

## 7. Real-World Examples

**Netflix — incrementality measurement:** Netflix cannot A/B test all pricing experiments (competitive sensitivity). They use DiD with geographic holdout groups as quasi-experiments. Synthetic control (weighted combination of control regions that mimics treated region's pre-period trend) is used when parallel trends assumption is questionable. Uncertainty is quantified via permutation testing (randomize which region was treated, compare actual DiD to null distribution).

**Uber — experiment platform Minerva:** Uber runs thousands of A/B tests simultaneously. For metrics where randomized experiments are infeasible (e.g., driver supply effects), they use IV with surge pricing zones as instruments. Instrumental variable: whether a driver is in a high-surge zone (affects supply/demand but not individual trip outcome directly). This allows causal estimation of pricing elasticity.

**LinkedIn — feed ranking uplift:** LinkedIn uses X-learner for estimating heterogeneous engagement effects of feed algorithm changes. They discovered that content from 1st-degree connections has 3x higher CATE than content from 3rd-degree connections — leading to a network-aware ranking policy. The X-learner outperformed T-learner in this imbalanced setting (95% organic impressions, 5% boosted content).

**Healthcare — EHR treatment effect estimation:** Observational EHR data to estimate effect of statins on cardiovascular events. Confounders: age, existing conditions, other medications (300+ covariates). High-dimensional propensity score estimation via LASSO logistic regression. Result: similar ATE to RCT gold standard, validating the methodology. Used to inform treatment guidelines for patient segments underrepresented in trials.

---

## 8. Tradeoffs

| Method | Assumption | Handles Unobserved Confounders | Scales to HTE | Confidence Intervals | Best For |
|---|---|---|---|---|---|
| IPW | Unconfoundedness, positivity | No | No (ATE only) | Bootstrap | Large samples, known confounders |
| Matching | Unconfoundedness | No | No (ATT) | Bootstrap | Interpretability |
| T-Learner | Unconfoundedness | No | Yes | Via conformal | Balanced treatment groups |
| X-Learner | Unconfoundedness | No | Yes | Via conformal | Imbalanced groups |
| Causal Forest | Unconfoundedness | No | Yes (local) | Asymptotic (IJ) | Best general CATE estimator |
| Double ML | Unconfoundedness | No | Partial (linear CATE) | Asymptotic | Regularized environments |
| DiD | Parallel trends | Partially (time-invariant) | No (ATT) | Clustered SE | Policy evaluation, geographic |
| IV | Instrument validity | Yes | No (LATE) | Delta method | When confounders unobservable |

**Positivity violation:** if e(x) is near 0 or 1 for some subgroups, those subgroups have no overlap and causal effects cannot be identified. IPW weights blow up, matching fails. Always trim and check positivity before inference.

---

## 9. When to Use / When NOT to Use

**Use causal inference when:**
- Decisions are interventional (who to send coupon to, who to treat)
- Retrospective analysis of past decisions where randomization was impossible
- Need to estimate heterogeneous treatment effects for targeting/personalization
- Regulatory environment requires demonstrating causation (clinical trials alternative)
- A/B testing is too slow, expensive, or ethically infeasible

**Do NOT use causal inference (observational) when:**
- You can run a randomized experiment — RCTs are always preferred
- Key confounders are unobservable and no valid instrument exists (bias cannot be removed)
- The overlap assumption is severely violated (no comparable control units)
- Sample sizes are too small for cross-fitting and variance reduction
- The goal is pure prediction, not decision-making

---

## 10. Common Pitfalls

**Pitfall 1 — Ignoring positivity violations:**
An e-commerce team estimated coupon effect using IPW. Some user segments (new users, age 18–22, mobile-only) had propensity score e(x) < 0.01 — these users almost never received coupons. IPW weights for these users reached 100x, dominating the estimate. Reported ATE: +8% conversion. True effect: +2%. Fix: always plot the propensity score distribution for treated and control groups. Trim units with e(x) < 0.05 or e(x) > 0.95, report trimming fraction, and bound sensitivity of results to the trimming threshold.

**Pitfall 2 — Unmeasured confounders (overconfidence in observational data):**
A media company estimated the effect of their recommendation algorithm on watch time using T-learner on historical data. The study concluded the algorithm increased watch time by 15%. Later A/B test showed 3% increase. The confounder: users who engaged with the algorithm's recommendations were already high-intent viewers — this was not captured in measured features. Fix: conduct sensitivity analysis (Rosenbaum bounds: how strong must an unmeasured confounder be to explain away the effect?). Report as "suggestive" rather than causal unless validated by experiment.

**Pitfall 3 — SUTVA violation in networked experiments:**
A social platform estimated the effect of a notification feature using individual-level randomization (50% users get notification, 50% do not). Friends of treated users saw their friends' activity increase, affecting control users' behavior via the social graph. The no-interference assumption of SUTVA was violated. Reported ATE was inflated 2x. Fix: use cluster-based randomization (randomize by social community, not individual). Or use ego-network designs where connected users are in the same treatment arm.

**Pitfall 4 — S-learner regularizing away small treatment effects:**
A team used gradient boosted trees as the S-learner for a marketing experiment. The treatment variable T was binary and had a moderate effect (2% conversion lift). The tree-based model regularized T's contribution because it had lower predictive importance than demographic features. CATE estimates were near zero for all users. Fix: prefer T-learner or X-learner for tree-based models. If using S-learner, ensure T is not dominated by other features (use a linear model for the treatment component, nonlinear for covariates — the R-learner does this properly).

**Pitfall 5 — Leaking post-treatment features:**
A team trained a CATE model to predict who would respond to a discount. They included features like "discount_used_last_90_days" — a post-treatment variable affected by past treatment. This created a feedback loop: users who used discounts before were estimated to have higher CATE, so they received more discounts, so their "discount_used" feature increased further. Fix: use only pre-treatment covariates (features measured before the treatment decision). Apply strict temporal cutoffs — features measured after treatment assignment are never valid covariates.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|---|---|---|
| EconML (Microsoft) | CATE estimation: CausalForestDML, DML, DR-Learner | Production-grade; best general library |
| CausalML (Uber) | Uplift modeling: T/S/X-Learner, CausalForest | Strong on marketing use cases |
| DoWhy (Microsoft) | Causal graph specification, identification, refutation | DAG-based; forces explicit assumptions |
| grf (R) | Generalized Random Forests (CausalForest) | R ecosystem, Athey lab original |
| doubleml (Python) | Double ML / PLM | Follows Chernozhukov et al. exactly |
| statsmodels | DiD, IV (2SLS), propensity models | Econometrics baseline |
| daggity | DAG drawing and identification analysis | Web tool; useful for confounding analysis |
| Pyro / NumPyro | Bayesian causal models | When uncertainty quantification is critical |

---

## 12. Interview Questions with Answers

**Q: What is the fundamental problem of causal inference?**
We can never observe the same unit in both treated and untreated states simultaneously. For each unit i, we observe either Y_i(1) or Y_i(0), never both. Y_i(1) - Y_i(0) is unobservable for any individual — it is the "missing potential outcome." All causal methods are strategies for estimating this missing counterfactual: randomized experiments balance confounders on average, propensity methods control for measured confounders, DiD controls for time-invariant confounders, and IV uses exogenous variation.

**Q: When does P(Y|T) equal P(Y|do(T))?**
When treatment T is assigned independently of confounders — i.e., in a perfectly randomized experiment, or when all confounders are measured and conditioning on X blocks all backdoor paths from T to Y. Formally: when ignorability holds (Y(0), Y(1) ⊥ T | X) and positivity holds (0 < P(T=1|X) < 1), then P(Y|do(T)) = sum_x P(Y|T, X=x) P(X=x). In observational settings with unmeasured confounders, P(Y|T) and P(Y|do(T)) will differ and no standard method can recover the causal effect.

**Q: What is a propensity score and why does it reduce confounding?**
The propensity score e(x) = P(T=1|X=x) is a balancing score: conditioning on e(x) is sufficient to balance all observed covariates X between treated and control groups. This is the dimensionality reduction property of propensity scores (Rosenbaum & Rubin 1983): instead of matching on 50 covariates, you can match on the single propensity score. Intuition: units with the same propensity score have, on average, the same distribution of covariates, so their outcome difference can be attributed to treatment.

**Q: What is the difference between ATE, ATT, and CATE?**
ATE (Average Treatment Effect) is the expected causal effect averaged over the entire population: E[Y(1) - Y(0)]. ATT (Average Treatment Effect on the Treated) conditions on those who actually received treatment: E[Y(1) - Y(0) | T=1]. When treatment is not random, ATT differs from ATE — a job training program may select participants who benefit most (ATT > ATE). CATE (Conditional ATE) estimates treatment effects as a function of covariates: tau(x) = E[Y(1) - Y(0) | X=x]. CATE enables heterogeneous treatment effect analysis and personalized targeting.

**Q: What is Double ML and why does cross-fitting matter?**
Double ML partialls out the effect of confounders X on both Y and T, then regresses the residuals. This removes bias from using flexible ML models to control for confounders. Cross-fitting (split data into K folds, train nuisance models on K-1 folds, predict on held-out fold) is critical because it prevents overfitting bias: if nuisance models are trained and evaluated on the same data, regularization causes residuals to be correlated with the treatment assignment, biasing the ATE estimate. Cross-fitting ensures out-of-sample residuals, restoring the Neyman orthogonality condition needed for valid inference.

**Q: How do you validate a CATE model when true treatment effects are unobservable?**
Multiple strategies: (1) Synthetic data — simulate data with known treatment effects, compare model estimates to ground truth. (2) Qini/AUUC curve — sort by estimated CATE, measure actual conversion lift vs. random targeting in retrospective data. Higher Qini = better ranking of uplift. (3) Held-out A/B test — run a small randomized experiment on the top and bottom CATE quintiles, verify that top-scored users respond significantly more. (4) Calibration — if CATE model estimates 10% uplift for a group, verify 10% ± noise in a held-out experiment. (5) Placebo tests — estimate treatment effect on pre-treatment outcomes (should be zero if model is correctly specified).

**Q: What is the parallel trends assumption in DiD and when does it fail?**
Parallel trends: absent the treatment, the treated and control groups would have followed the same time trend. DiD estimates ATE_DiD = (Y_treated_post - Y_treated_pre) - (Y_control_post - Y_control_pre). If control and treated groups were trending differently before treatment (e.g., one is in a declining market), parallel trends fails and DiD is biased. Diagnostic: plot pre-treatment trends for both groups — they should track closely. Event study design: include leads (pre-treatment period indicators) and test whether they are jointly zero. Synthetic control constructs a weighted combination of control units that best matches the treated unit's pre-period trend.

**Q: What is an instrumental variable and what are the three key conditions for validity?**
An instrument Z is a variable that (1) is relevant — correlated with treatment T (F-statistic > 10 in first stage), (2) is exclusive — affects outcome Y only through T (no direct path Z -> Y), and (3) is as-good-as-randomly assigned — not correlated with unmeasured confounders. IV estimates the Local Average Treatment Effect (LATE) — the ATE among compliers (units whose treatment changed in response to Z). Example: distance to college as instrument for education. Relevant: closer -> more likely to attend. Exclusive: distance does not directly affect wages (debatable). Random: distance assigned by geography, not choice.

**Q: How does CausalForest differ from a standard random forest?**
CausalForest modifies the splitting criterion: instead of maximizing variance reduction in Y, it maximizes heterogeneity in treatment effects. Each split separates units into subgroups with maximally different treatment responses. Predictions are made by estimating local CATE within each leaf using residualized outcomes (similar to Double ML). Confidence intervals are provided via the infinitesimal jackknife (IJ) variance estimator — valid for forest-based estimators under mild conditions. The forest is also honest: separate samples are used to select splits and to estimate leaf-level effects, preventing overfitting of treatment effect estimates.

**Q: How would you measure the effect of a recommendation algorithm change using observational data?**
This is a classic DiD or synthetic control problem. If the change was rolled out to some users (treated cohort) before others (control cohort): use cohort-level DiD with the pre-rollout period as baseline. Confounders: device type, account age, content preference (include as covariates in regression DiD). Parallel trends check: plot weekly engagement for both cohorts over the 8 weeks before rollout. If trends are parallel (p > 0.1 for pre-period coefficients), proceed. Apply synthetic control if only one treatment market/cohort exists. Report ATE with clustered standard errors at the user level. Sensitivity analysis: vary the pre-period window, check stability.

**Q: What is the difference between correlation, prediction, and causation in ML systems?**
Correlation: statistical association, X and Y tend to move together. Prediction: use X to minimize expected loss on Y (P(Y|X)). Causation: changing X causes Y to change (P(Y|do(X))). A predictive model can be highly accurate based on spurious correlations that do not generalize under intervention. Example: a model predicting ICU mortality might learn that fewer medications predict survival — because terminal patients stop receiving medications before death. This correlation is anti-causal and useless for decision-making. Causal models encode the mechanism, not the correlation, and remain valid under interventions and distribution shifts that change the correlation structure.

---

## 13. Best Practices

- Always start with the causal DAG — draw it explicitly with domain experts. Identify backdoor paths before choosing a method.
- Check positivity before any propensity-based method. Plot propensity histograms for treated and control groups. Trim or reweight if overlap is poor.
- Use cross-fitting (K=5 folds) for Double ML and any method involving ML nuisance models. Plain cross-validation is not sufficient.
- For CATE estimation, prefer CausalForestDML (EconML) as the default — it provides valid confidence intervals and handles high-dimensional X.
- Conduct sensitivity analysis: how large must unmeasured confounding be (Rosenbaum E-value) to explain away your estimate? Report this alongside the point estimate.
- Never include post-treatment variables as covariates — this opens collider bias.
- For uplift modeling, evaluate using both Qini curves (ranking quality) and a held-out A/B test (absolute calibration).
- Use doubly robust estimators (DR-Learner, AIPW) when you are uncertain whether the outcome model or propensity model is correctly specified — consistency under either alone.
- Report uncertainty: confidence intervals, not just point estimates. In high-stakes decisions, the confidence interval width matters as much as the point estimate.
- Document all assumptions explicitly (unconfoundedness, SUTVA, positivity) and stress-test each with domain experts.

---

## 14. Case Study

**Problem: Measuring the causal effect of email discounts on customer lifetime value (LTV)**

Context: an e-commerce company sends promotional email discounts (10–30% off) to ~20% of its customer base. Data: 500K customers, 12-month behavioral history, 85 features. Question: does sending a discount email increase 6-month LTV, and for which customer segments?

**Why naive ML fails:**
Customers selected for discounts are high-value customers identified by a propensity-to-churn model. They have lower recent purchase frequency (selection bias). Naive comparison: treated LTV = $180, control LTV = $220. This suggests discounts decrease LTV — but confounding (churners selected for treatment) creates the illusion.

```
Confounding structure:
  ChurnRisk (X) -> EmailDiscount (T)
  ChurnRisk (X) -> LTV (Y)
  EmailDiscount (T) -> LTV (Y)

  Without controlling for ChurnRisk, naive E[LTV|T=1] < E[LTV|T=0]
  Controlling for X recovers the positive causal effect.
```

**Solution pipeline:**

```
1. Feature set (pre-treatment, measured before email send):
   Days since last purchase, purchase frequency (30/90/180d),
   average order value, product categories, geographic region,
   account age, email open rate (historical), churn risk score.

2. Propensity score estimation:
   GradientBoostingClassifier, 5-fold cross-fitting
   E[T=1|X] distribution: treated ~ Beta(2,3), control ~ Beta(1,5)
   Overlap: reasonable; trim e(x) < 0.02 or > 0.98 (drops 3.2% of sample)

3. CATE estimation with CausalForestDML:
   model_y: GradientBoostingRegressor (predict LTV from X)
   model_t: GradientBoostingClassifier (predict T from X)
   4000 trees, min_samples_leaf=50, honest splitting
   Outcome: 6-month LTV post-email-send date

4. Evaluation: Qini coefficient on 30% held-out set.
```

**Results:**

| Segment | CATE (95% CI) | Interpretation |
|---|---|---|
| High churn risk, high AOV | +$42 (+32, +52) | Persuadables — core target |
| Low churn risk, high AOV | +$8 (+2, +14) | Sure things — marginal benefit |
| High churn risk, low AOV | +$3 (-5, +11) | Lost causes — do not discount |
| New customers (<90d) | +$28 (+18, +38) | High potential |

**Business impact:**
Deploying discounts to top-CATE customers (estimated CATE > $20) instead of random selection:
- Email volume reduced by 35% (cost savings)
- Total LTV uplift increased 22% (vs. random targeting)
- A/B holdout validation: CATE model quintile 1 vs. quintile 5 showed 3.1x difference in actual LTV lift (p < 0.001)

**Qini coefficient:** 0.31 (vs. 0.0 for random targeting, theoretical max 0.5).
