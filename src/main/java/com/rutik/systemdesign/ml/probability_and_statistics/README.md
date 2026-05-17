# Probability and Statistics for ML

## 1. Concept Overview

Probability and statistics underpin every aspect of machine learning: how we model uncertainty in data, how we define and fit model parameters, how we compare model performance, and how we make decisions under uncertainty. ML is fundamentally about learning a probability distribution over data, parameters, or predictions.

Maximum Likelihood Estimation (MLE) answers: given a dataset, what parameters make this data most probable? Bayesian inference answers: given data and a prior belief about parameters, what should our updated belief be? Hypothesis testing answers: is the improvement I observe in an A/B experiment real or noise? Every classification model outputs probabilities; every regression model has an implicit noise model; every regularizer encodes a prior.

---

## 2. Intuition

> **One-line analogy**: Probability theory is the grammar of uncertainty — it tells you how to reason consistently about events you cannot predict exactly.

**Mental model**: Imagine you are estimating whether a coin is fair. You flip it 100 times and see 60 heads. MLE says: the parameter theta (probability of heads) that makes 60 heads most likely is theta=0.60. MAP with a Beta(10, 10) prior (encoding your belief the coin is probably fair) pulls the estimate toward 0.55. The posterior distribution over theta quantifies your remaining uncertainty. This is the same reasoning as a logistic regression: the model parameters are chosen to maximize the likelihood of the observed labels.

**Why it matters**: Cross-entropy loss is not an arbitrary choice — it is the negative log-likelihood of a categorical distribution. MSE loss is the negative log-likelihood of a Gaussian. Understanding this connection tells you when to use which loss, what the model implicitly assumes about noise, and how regularization (L2 = Gaussian prior, L1 = Laplace prior) modifies the estimate.

**Key insight**: When you minimize cross-entropy loss, you are doing MLE. When you add L2 regularization, you are doing MAP with a Gaussian prior. The connection between optimization objectives and probabilistic models makes loss function selection principled rather than heuristic.

---

## 3. Core Principles

- **Probability axioms**: 0 <= P(A) <= 1; P(Omega) = 1; P(A or B) = P(A) + P(B) for disjoint A, B.
- **Conditional probability**: P(A|B) = P(A and B) / P(B); probability of A given B occurred.
- **Independence**: P(A and B) = P(A) * P(B); knowing B gives no information about A.
- **Bayes theorem**: P(A|B) = P(B|A) * P(A) / P(B); inverts conditioning.
- **Expected value**: E[X] = sum x * P(X=x); the probability-weighted average outcome.
- **Variance**: Var(X) = E[(X - E[X])^2] = E[X^2] - E[X]^2; spread of a distribution.
- **Covariance**: Cov(X,Y) = E[(X-E[X])(Y-E[Y])]; how two variables move together.
- **Correlation**: rho = Cov(X,Y) / (std(X) * std(Y)); normalized covariance in [-1, +1].
- **Law of Total Probability**: P(A) = sum_i P(A|B_i) P(B_i) for a partition {B_i}.
- **Central Limit Theorem (CLT)**: For iid samples with mean mu and variance sigma^2, the sample mean X_bar converges to N(mu, sigma^2/n) as n -> inf. This is why Gaussian assumptions work even when individual data points are not Gaussian.

---

## 4. Types / Architectures / Strategies

### 4.1 Key Probability Distributions in ML

| Distribution | Parameters | Use Case | Mean | Variance |
|-------------|-----------|---------|------|---------|
| Gaussian N(mu, sigma^2) | mu, sigma^2 | Continuous data, noise model, weight priors | mu | sigma^2 |
| Bernoulli(p) | p | Binary outcomes, binary classification | p | p(1-p) |
| Binomial(n,p) | n, p | Count of successes in n trials | np | np(1-p) |
| Categorical(p) | p (vector) | Multi-class labels | - | - |
| Poisson(lambda) | lambda | Count events per time interval | lambda | lambda |
| Exponential(lambda) | lambda | Time between events | 1/lambda | 1/lambda^2 |
| Beta(alpha, beta) | alpha, beta | Prior for probability; Beta(1,1) = Uniform | a/(a+b) | ab/((a+b)^2(a+b+1)) |
| Dirichlet(alpha) | alpha (vector) | Prior for categorical distribution | alpha_i/sum | - |
| Laplace(mu, b) | mu, b | Robust regression; L1 prior | mu | 2b^2 |

### 4.2 Parameter Estimation Methods

**MLE (Maximum Likelihood Estimation)**: Find theta that maximizes P(data | theta). For a Gaussian: mu_MLE = sample mean; sigma^2_MLE = (1/n) * sum (x_i - mu)^2 (biased). No prior knowledge incorporated.

**MAP (Maximum A Posteriori)**: Find theta maximizing P(theta | data) = P(data | theta) * P(theta). Equivalent to MLE + log prior term. MAP with Gaussian prior on weights = L2 regularization. MAP with Laplace prior = L1 regularization.

**Full Bayesian Inference**: Compute entire posterior P(theta | data) = P(data | theta) * P(theta) / P(data). Intractable for most models; approximate via MCMC, Variational Inference, or Laplace approximation.

### 4.3 Hypothesis Testing

**Null hypothesis (H0)**: The default assumption (no effect, no difference).
**p-value**: Probability of observing data at least as extreme as yours, assuming H0 is true. NOT the probability that H0 is true.
**Type I error (alpha)**: Reject H0 when it is true (false positive). Controlled by significance level alpha = 0.05.
**Type II error (beta)**: Fail to reject H0 when it is false (false negative). Power = 1 - beta.
**Confidence interval**: 95% CI = [x_bar - 1.96 * se, x_bar + 1.96 * se] where se = sigma / sqrt(n).

---

## 5. Architecture Diagrams

### Bayes Theorem in ML

```
                    Prior
                    P(theta)
                       |
                       v
Data X -----> Likelihood P(X|theta) ----> Posterior P(theta|X)
                       |                       |
                       v                       v
                   (update)              MAP estimate:
                                         argmax P(theta|X)
                                        = argmax log P(X|theta) + log P(theta)
                                        = MLE loss + regularization
```

### MLE vs MAP Connection to Loss Functions

```
Gaussian noise model (regression):
  P(y|x, w) = N(y; w^T x, sigma^2)
  log P(y|x, w) = -||y - w^T x||^2 / (2 sigma^2) + const
  MLE = minimize MSE loss

Categorical model (classification):
  P(y|x, w) = Categorical(softmax(Wx))
  log P(y|x, w) = log softmax(Wx)[y] = log predicted prob of true class
  MLE = minimize cross-entropy loss

Adding L2 regularization:
  MAP with Gaussian prior P(w) = N(0, sigma_w^2 I)
  = minimize MSE/cross-entropy + (lambda) * ||w||_2^2
  lambda = sigma^2 / sigma_w^2  (noise variance over prior variance)
```

### Hypothesis Test Decision Boundaries

```
Observed test statistic z
          |
     |----|----|----|----|----|
    -3   -2   -1    0    1    2    3
                               |
                           z_alpha = 1.96  (for alpha=0.05, two-tailed)

If |z| > 1.96: reject H0 (p-value < 0.05)
If |z| < 1.96: fail to reject H0

p-value = P(|Z| >= |z_observed| | H0)
```

---

## 6. How It Works — Detailed Mechanics

### MLE for Gaussian Distribution

```python
import numpy as np
from scipy import stats
from typing import NamedTuple


class GaussianMLE(NamedTuple):
    mu: float
    sigma_sq_biased: float    # MLE (biased by 1/n)
    sigma_sq_unbiased: float  # Bessel-corrected (1/(n-1))


def fit_gaussian_mle(data: np.ndarray) -> GaussianMLE:
    """
    MLE estimates for Gaussian distribution parameters.

    mu_MLE = (1/n) sum x_i  -- same as sample mean, unbiased
    sigma^2_MLE = (1/n) sum (x_i - mu)^2  -- biased (underestimates true variance)
    sigma^2_unbiased = (1/(n-1)) sum (x_i - mu)^2  -- Bessel's correction
    """
    n = len(data)
    mu = data.mean()
    # MLE variance uses n (biased)
    sigma_sq_biased = ((data - mu) ** 2).mean()
    # Unbiased uses n-1 (Bessel's correction)
    sigma_sq_unbiased = data.var(ddof=1)
    return GaussianMLE(
        mu=float(mu),
        sigma_sq_biased=float(sigma_sq_biased),
        sigma_sq_unbiased=float(sigma_sq_unbiased)
    )


def log_likelihood_gaussian(data: np.ndarray, mu: float, sigma_sq: float) -> float:
    """
    Log-likelihood of data under N(mu, sigma^2).
    log L = -n/2 * log(2 pi sigma^2) - 1/(2 sigma^2) * sum (x_i - mu)^2
    Minimizing MSE = maximizing Gaussian log-likelihood (when sigma fixed).
    """
    n = len(data)
    log_l = (
        -n / 2 * np.log(2 * np.pi * sigma_sq)
        - 1 / (2 * sigma_sq) * np.sum((data - mu) ** 2)
    )
    return float(log_l)
```

### Hypothesis Testing and p-values

```python
def two_sample_t_test(
    group_a: np.ndarray,
    group_b: np.ndarray,
    alpha: float = 0.05
) -> dict:
    """
    Two-sample Welch t-test (does not assume equal variances).
    H0: mean_a == mean_b
    H1: mean_a != mean_b  (two-tailed)

    Test statistic: t = (mean_a - mean_b) / sqrt(var_a/n_a + var_b/n_b)
    """
    n_a, n_b = len(group_a), len(group_b)
    mean_a, mean_b = group_a.mean(), group_b.mean()
    var_a, var_b = group_a.var(ddof=1), group_b.var(ddof=1)

    # Standard error of the difference
    se = np.sqrt(var_a / n_a + var_b / n_b)
    t_stat = (mean_a - mean_b) / se

    # Welch-Satterthwaite degrees of freedom
    df = (var_a / n_a + var_b / n_b) ** 2 / (
        (var_a / n_a) ** 2 / (n_a - 1) + (var_b / n_b) ** 2 / (n_b - 1)
    )

    # Two-tailed p-value
    p_value = 2 * stats.t.sf(np.abs(t_stat), df=df)

    # 95% confidence interval for the difference
    t_crit = stats.t.ppf(1 - alpha / 2, df=df)
    ci_low = (mean_a - mean_b) - t_crit * se
    ci_high = (mean_a - mean_b) + t_crit * se

    return {
        "t_statistic": float(t_stat),
        "p_value": float(p_value),
        "degrees_of_freedom": float(df),
        "reject_null": p_value < alpha,
        "mean_difference": float(mean_a - mean_b),
        "confidence_interval_95": (float(ci_low), float(ci_high)),
        "effect_size_cohen_d": float((mean_a - mean_b) / np.sqrt((var_a + var_b) / 2))
    }


def minimum_sample_size(
    effect_size: float,   # Cohen's d: small=0.2, medium=0.5, large=0.8
    alpha: float = 0.05,  # significance level
    power: float = 0.80   # 1 - Type II error rate
) -> int:
    """
    Compute minimum sample size per group for a two-sample t-test.
    Uses normal approximation (valid for large n).

    n = 2 * ((z_alpha/2 + z_beta) / effect_size)^2
    """
    z_alpha = stats.norm.ppf(1 - alpha / 2)   # 1.96 for alpha=0.05
    z_beta = stats.norm.ppf(power)              # 0.842 for power=0.80
    n = 2 * ((z_alpha + z_beta) / effect_size) ** 2
    return int(np.ceil(n))


def mutual_information_discrete(
    x: np.ndarray,
    y: np.ndarray
) -> float:
    """
    Compute mutual information I(X;Y) for discrete variables.
    I(X;Y) = sum_{x,y} P(x,y) * log(P(x,y) / (P(x) * P(y)))
    = H(X) + H(Y) - H(X,Y)
    Used for feature selection: high MI = feature is informative about label.
    """
    n = len(x)
    # Joint distribution
    joint_counts: dict = {}
    for xi, yi in zip(x, y):
        joint_counts[(xi, yi)] = joint_counts.get((xi, yi), 0) + 1

    x_counts: dict = {}
    y_counts: dict = {}
    for xi in x:
        x_counts[xi] = x_counts.get(xi, 0) + 1
    for yi in y:
        y_counts[yi] = y_counts.get(yi, 0) + 1

    mi = 0.0
    for (xi, yi), count in joint_counts.items():
        p_xy = count / n
        p_x = x_counts[xi] / n
        p_y = y_counts[yi] / n
        if p_xy > 0:
            mi += p_xy * np.log(p_xy / (p_x * p_y))

    return mi
```

### Bayesian Inference with Beta-Bernoulli Model

```python
def bayesian_coin_inference(
    n_heads: int,
    n_tails: int,
    prior_alpha: float = 1.0,  # Beta(1,1) = Uniform prior
    prior_beta: float = 1.0
) -> dict:
    """
    Conjugate Bayesian update for Bernoulli likelihood with Beta prior.
    Posterior is Beta(alpha + n_heads, beta + n_tails).
    MLE: n_heads / (n_heads + n_tails)
    MAP: (alpha + n_heads - 1) / (alpha + beta + n_heads + n_tails - 2)
    Posterior mean: (alpha + n_heads) / (alpha + beta + n_heads + n_tails)
    """
    posterior_alpha = prior_alpha + n_heads
    posterior_beta = prior_beta + n_tails
    n = n_heads + n_tails

    mle = n_heads / n
    posterior_mean = posterior_alpha / (posterior_alpha + posterior_beta)
    map_estimate = (posterior_alpha - 1) / (posterior_alpha + posterior_beta - 2)

    # 95% credible interval from posterior Beta distribution
    ci_low, ci_high = stats.beta.ppf(
        [0.025, 0.975], posterior_alpha, posterior_beta
    )

    return {
        "mle": mle,
        "map": map_estimate,
        "posterior_mean": posterior_mean,
        "posterior_95_credible_interval": (float(ci_low), float(ci_high)),
        "posterior_alpha": posterior_alpha,
        "posterior_beta": posterior_beta
    }
```

---

## 7. Real-World Examples

**Cross-entropy loss is MLE**: Training a 10-class image classifier with softmax output and cross-entropy loss is equivalent to maximum likelihood estimation under a categorical noise model. The ground-truth label is "what we observed"; the model's softmax output is its estimate of the class probabilities; minimizing cross-entropy = maximizing log P(labels | images, parameters).

**L2 regularization is Gaussian prior MAP**: Weight decay in neural networks (add lambda * ||W||^2 to loss) is MAP estimation with a Gaussian prior N(0, 1/lambda). This is why weight decay keeps weights small but rarely exactly zero, while L1 regularization (Laplace prior) produces sparse weights.

**A/B testing is hypothesis testing**: A product team changed a recommendation algorithm and measured click-through rate on 50,000 users per group. CTR_A = 0.032, CTR_B = 0.035. Two-proportion z-test gives p=0.003 < 0.05, so they rejected H0 and shipped the change. Without statistical testing they would not know whether the 0.3% improvement was real or sampling noise.

**CLT enables batch training**: Stochastic gradient descent works because mini-batch gradient estimates are approximately unbiased estimates of the true gradient. By CLT, with batch size 256, the mean of 256 gradient samples converges to a Gaussian around the true gradient. Larger batches reduce gradient variance but cost more compute.

---

## 8. Tradeoffs

| Method | Pros | Cons |
|--------|------|------|
| MLE | Simple, no prior needed, consistent | Overfits with small data; ignores prior knowledge |
| MAP | Incorporates prior; regularizes | Prior choice matters; still point estimate (no uncertainty) |
| Full Bayes | Quantifies uncertainty; best calibration | Intractable for large models; requires MCMC or VI |
| Frequentist testing | No prior needed; widely understood | p-value misinterpreted; no probability statement about hypothesis |
| Bayesian testing | Direct probability statements; no p-value | Requires prior; less familiar to stakeholders |

| Distribution | Good for | Bad for |
|-------------|---------|---------|
| Gaussian | Symmetric continuous data | Heavy tails; counts; bounded data |
| Poisson | Event counts (rare events) | Overdispersed data (use Negative Binomial) |
| Beta | Probability/proportion modeling | Multimodal priors (use mixture) |
| Laplace | Robust regression (heavy-tailed noise) | When Gaussian is truly correct |

---

## 9. When to Use / When NOT to Use

**Use Gaussian distribution when**: data is approximately symmetric and unimodal; features are continuous real-valued; noise in regression is symmetric. The CLT justifies it for aggregated measurements even if individual samples are not Gaussian.

**Do NOT use Gaussian when**: modeling counts (use Poisson), probabilities (use Beta), always-positive data (use log-normal or Gamma), or heavy-tailed data (use Student-t with low degrees of freedom).

**Use MLE when**: you have a lot of data relative to parameters and no strong prior knowledge; computational budget is limited (optimization problem vs sampling).

**Use MAP when**: you have domain knowledge that can be encoded as a prior (e.g., weights should be small = Gaussian prior) and you want regularized point estimates without the cost of full Bayesian inference.

**Use hypothesis testing when**: you need to make a binary decision (ship/don't ship) with controlled Type I error; results must be defensible to stakeholders who understand p-values.

**Do NOT run an A/B test without a power analysis first**: determine minimum sample size before collecting data; peeking at p-values before the planned sample size inflates Type I error.

---

## 10. Common Pitfalls

**Pitfall 1 — p-value misinterpretation**: A product team ran an A/B test, got p=0.04, and reported "there is a 4% probability the null hypothesis is true." This is wrong. p-value = P(data | H0), not P(H0 | data). The probability that H0 is true (Bayesian posterior) requires a prior. The correct statement: "If there were no effect, we would see data this extreme or more extreme 4% of the time." The team should not make probability statements about H0 without a prior.

**Pitfall 2 — Biased MLE variance with small samples**: A team estimated Gaussian parameters from n=10 samples for a data quality check. They used `np.var(data)` (divides by n), which returns the biased MLE estimate. For small n, this systematically underestimates true variance. The 95% confidence intervals were too narrow and flagged real data as outliers. Fix: always use `np.var(data, ddof=1)` for sample variance.

```python
# Broken: biased variance for small samples
sigma_sq = np.var(data)            # divides by n, underestimates

# Fixed: Bessel-corrected unbiased estimate
sigma_sq = np.var(data, ddof=1)   # divides by n-1
```

**Pitfall 3 — Multiple comparisons inflate false positive rate**: An ML team evaluated their model on 20 different subgroups and reported all p < 0.05 comparisons as real improvements. With 20 independent tests at alpha=0.05, the expected number of false positives is 1. Apply Bonferroni correction (alpha/20 = 0.0025) or Benjamini-Hochberg FDR control.

**Pitfall 4 — Assuming independence in CLT application**: A batch gradient is an average of per-sample gradients, and the CLT assumption is that samples are iid. If training data has temporal correlation (time series) or cluster correlation (same user), samples within a batch are not independent. Gradient estimates are still unbiased but variance is underestimated, so the effective batch size is smaller than it appears.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| NumPy | Random sampling, basic statistics, empirical distributions |
| SciPy stats | Probability distributions, hypothesis tests, confidence intervals |
| statsmodels | OLS, GLMs, ANOVA, time series tests, detailed statistical output |
| PyMC | Full Bayesian inference via MCMC, variational inference |
| scikit-learn | Cross-validation, calibration, model comparison utilities |
| Pingouin | User-friendly hypothesis tests, effect sizes, power analysis |
| bootstrapped (Shopify) | Bootstrap confidence intervals for arbitrary metrics |

---

## 12. Interview Questions with Answers

**Q: What is the relationship between MLE and cross-entropy loss?**
Minimizing cross-entropy loss is equivalent to maximum likelihood estimation under a categorical distribution. The log-likelihood of n iid categorical samples with true labels y_i and predicted probabilities p_i is sum_i log p_i(y_i). Negating this gives the cross-entropy loss. This connection means using cross-entropy for classification is not arbitrary — it is the principled MLE objective for the model.

**Q: Why does L2 regularization correspond to a Gaussian prior in MAP estimation?**
MAP estimation maximizes log P(theta | data) = log P(data | theta) + log P(theta). If the prior is P(theta) = N(0, sigma_w^2 I), then log P(theta) = -||theta||^2 / (2 sigma_w^2) + const. Adding this to the log-likelihood gives loss + (1/(2 sigma_w^2)) * ||theta||^2, which is exactly L2-regularized MLE with lambda = 1/(2 sigma_w^2). A stronger prior (smaller sigma_w^2) corresponds to larger lambda.

**Q: What is the Central Limit Theorem and why does it matter for ML?**
The CLT states that the mean of n iid random variables with mean mu and variance sigma^2 converges in distribution to N(mu, sigma^2/n) as n -> infinity. It matters in ML because: (1) mini-batch gradient estimates are approximately Gaussian, justifying their use as noisy gradient descent; (2) model performance metrics (accuracy, AUROC) averaged over test samples have approximately Gaussian sampling distributions, enabling confidence intervals; (3) it justifies Gaussian assumptions in many models even when individual data points are not Gaussian.

**Q: What is the difference between a confidence interval and a credible interval?**
A 95% confidence interval is a frequentist concept: if you repeated the experiment many times and computed a CI each time, 95% of those intervals would contain the true parameter. It says nothing about the probability that the true parameter is in any single computed interval. A 95% Bayesian credible interval contains the true parameter with 95% posterior probability (given data and prior). The credible interval is the natural intuitive statement; the confidence interval is not a probability statement about the parameter.

**Q: What is the p-value and what are its limitations?**
The p-value is the probability of observing data at least as extreme as the actual data, assuming the null hypothesis is true. Limitations: (1) it does not tell you the probability that H0 is true; (2) with large samples almost any real effect is statistically significant even if practically meaningless — always report effect size; (3) with small samples, you can miss real effects (low power); (4) multiple comparisons inflate false positive rate; (5) it depends on the sampling plan (stopping rule matters in frequentist testing).

**Q: How does the Poisson distribution differ from the Binomial, and when should you use each?**
The Binomial(n, p) models the count of successes in n fixed independent trials. The Poisson(lambda) models the count of events in a fixed interval when events occur independently at a constant rate. Poisson is the limit of Binomial as n -> inf and p -> 0 with np = lambda constant — it applies when events are rare and the total number of possible events is very large. Use Binomial for "number of users who click a button out of 1000 shown it"; use Poisson for "number of errors per hour in a service."

**Q: What is overfitting from a probabilistic perspective?**
Overfitting is when the model maximizes likelihood on training data by memorizing noise rather than signal. A model with more parameters than data points can achieve perfect likelihood (loss = 0) on training data by explaining each data point individually, but the parameters encode noise specific to those samples. Regularization addresses this by incorporating a prior that penalizes complex parameter configurations (large weights), effectively performing MAP instead of MLE. Early stopping is approximately equivalent to an exponential prior on the number of gradient descent steps.

**Q: What is covariance and how does it differ from correlation?**
Covariance Cov(X,Y) = E[(X-mu_X)(Y-mu_Y)] measures how two variables move together. If X and Y tend to both be above/below their means simultaneously, covariance is positive. Correlation rho = Cov(X,Y) / (std(X) * std(Y)) normalizes covariance to [-1, +1], making it scale-invariant. Correlation is a pure measure of linear relationship; covariance retains units (e.g., height in cm covariance with weight in kg has units cm*kg). Pearson correlation assumes linearity; Spearman rank correlation is robust to monotone nonlinear relationships.

**Q: Why does a Gaussian prior on weights in a neural network not produce exact zeros?**
The Gaussian prior N(0, sigma^2) has its density concentrated near zero but never exactly at zero; the gradient of the prior term -||w||^2/(2sigma^2) pushes weights toward zero proportionally to their magnitude, so large weights shrink faster but small weights are never zeroed out exactly. A Laplace prior (L1 regularization) has a sharp peak at zero with non-differentiable kink; the subgradient is constant for nonzero weights (not proportional to magnitude), which creates a "pull toward exactly zero" that can take small weights all the way to zero.

**Q: What is the Beta distribution and why is it used as a prior for probabilities?**
The Beta(alpha, beta) distribution is supported on [0,1], making it a natural prior for any probability parameter. Beta(1,1) is Uniform(0,1) — a non-informative prior. Beta(10,10) encodes a belief that the probability is near 0.5 with moderate confidence. It is the conjugate prior for the Bernoulli/Binomial likelihood: if prior is Beta(alpha, beta) and you observe h heads and t tails, the posterior is Beta(alpha+h, beta+t), allowing cheap analytic updates without MCMC.

**Q: How do you design an A/B test to detect a 5% relative improvement in conversion rate?**
First determine the baseline conversion rate (say p=0.10) and the minimum detectable effect (MDE = 5% relative = 0.005 absolute). Choose alpha=0.05 and power=0.80. Compute the required sample size: n = 2 * (z_alpha/2 + z_beta)^2 * p*(1-p) / delta^2 where delta=0.005; this gives approximately 28,000 per group. Run the test without peeking until the target n is reached. Report both p-value and 95% CI on the absolute difference; if CI lower bound > 0, the result is practically significant.

---

## 13. Best Practices

- Always report effect size (Cohen's d, relative risk, odds ratio) alongside p-value; statistical significance is not practical significance.
- Use Bessel's correction (`ddof=1`) for sample variance to get unbiased estimates, especially with n < 30.
- Perform a power analysis before collecting data to determine minimum sample size; never run tests indefinitely until significance is achieved.
- Apply multiple comparison corrections (Bonferroni, Benjamini-Hochberg) when testing more than one hypothesis simultaneously.
- Verify distributional assumptions before applying parametric tests; use Shapiro-Wilk or Q-Q plots to check normality; use Levene's test for equal variance.
- Use bootstrapping for confidence intervals on non-standard metrics (AUROC, P@K, NDCG) where parametric formulas do not exist.
- When using MLE with small datasets (n < 100 per parameter), switch to MAP or hierarchical Bayesian models to prevent overfitting.
- Log-transform right-skewed data (user session durations, revenue) before fitting Gaussian models; or use a log-normal distribution directly.
- Stratified sampling in train/test splits preserves class distribution; critical for imbalanced datasets where random splits may put all minority class examples in train.

---

## 14. Case Study

**Problem**: An e-commerce platform ran an A/B test of a new product page layout across 200,000 users (100k per group). Metric: purchase conversion rate. The data science team needs to determine if the new layout is significantly better and estimate the true effect size for a business impact projection.

```python
import numpy as np
from scipy import stats


def analyze_ab_test(
    n_a: int,
    n_b: int,
    conversions_a: int,
    conversions_b: int,
    alpha: float = 0.05
) -> dict:
    """
    Two-proportion z-test for A/B conversion experiment.
    H0: p_A == p_B (no difference)
    H1: p_B > p_A (new layout improves conversion) -- one-tailed
    """
    p_a = conversions_a / n_a
    p_b = conversions_b / n_b
    relative_lift = (p_b - p_a) / p_a

    # Pooled proportion under H0
    p_pool = (conversions_a + conversions_b) / (n_a + n_b)
    se_pool = np.sqrt(p_pool * (1 - p_pool) * (1 / n_a + 1 / n_b))

    z_stat = (p_b - p_a) / se_pool
    # One-tailed p-value (testing p_b > p_a)
    p_value = stats.norm.sf(z_stat)

    # 95% CI for the difference (two-tailed for estimation)
    se_diff = np.sqrt(p_a * (1 - p_a) / n_a + p_b * (1 - p_b) / n_b)
    z_crit = stats.norm.ppf(1 - alpha / 2)
    ci_low = (p_b - p_a) - z_crit * se_diff
    ci_high = (p_b - p_a) + z_crit * se_diff

    # Business impact: daily conversions * estimated lift * avg order value
    daily_users = 50_000
    avg_order_value = 85.0
    daily_lift_conversions = daily_users * (p_b - p_a)
    daily_revenue_impact = daily_lift_conversions * avg_order_value

    return {
        "conversion_rate_a": p_a,
        "conversion_rate_b": p_b,
        "absolute_lift": p_b - p_a,
        "relative_lift_pct": relative_lift * 100,
        "z_statistic": float(z_stat),
        "p_value": float(p_value),
        "reject_null": p_value < alpha,
        "ci_95_absolute_difference": (float(ci_low), float(ci_high)),
        "daily_revenue_impact_usd": daily_revenue_impact
    }


# Observed data
result = analyze_ab_test(
    n_a=100_000, n_b=100_000,
    conversions_a=3_100, conversions_b=3_420
)
# p_a=0.031, p_b=0.0342, lift=+10.3%, p=0.0001, daily revenue impact=$13,600
```

**Findings**: Conversion rate increased from 3.10% to 3.42% (relative lift +10.3%, p=0.0001, 95% CI: [0.0019, 0.0045]). The null hypothesis is rejected. The 95% CI excludes zero, confirming the effect is statistically and practically significant. At 50,000 daily visitors and $85 average order value, the projected daily revenue impact is $13,600 — approximately $5M annualized. The team shipped the new layout.

**Lesson**: Reporting only the p-value would have led to "it's significant, ship it." Reporting the 95% CI translated statistical significance into a business-legible range ($1.6M to $7.6M annual impact range), enabling a better resource allocation decision.
