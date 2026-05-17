# Supervised Learning

## Deep Dive Files

| File | Topic | Q&As |
|------|-------|------|
| [linear_models.md](linear_models.md) | Linear & Logistic Regression, Regularization, Normal Equation | 15+ |
| [support_vector_machines.md](support_vector_machines.md) | SVM, Kernel Trick, Soft Margin, SVR | 15+ |
| [decision_trees.md](decision_trees.md) | CART, Gini/Entropy, Pruning, Feature Importance | 15+ |
| [bayesian_methods.md](bayesian_methods.md) | Naive Bayes, Laplace Smoothing, Bayesian Inference | 15+ |

---

## 1. Concept Overview

Supervised learning is a machine learning paradigm where a model learns a mapping from inputs X to outputs y using a labeled training dataset. Every training example consists of an input vector (features) and a corresponding target label. The model generalizes this mapping to make predictions on unseen data.

Supervised learning splits into two families based on the output type:
- **Regression** — predict a continuous value (house price, temperature)
- **Classification** — predict a discrete class label (spam/not-spam, digit 0-9)

---

## 2. Intuition

One-line analogy: a student learns from worked examples with answer keys, then applies that knowledge to an exam with unseen questions.

Mental model: the algorithm searches a hypothesis space H for the function h(x) that minimizes prediction error on training data while generalizing to new data.

Why it matters: supervised learning powers the majority of production ML systems — fraud detection, recommendation engines, medical diagnosis, search ranking, and churn prediction all rely on it.

Key insight: the quality of supervision (label accuracy, label coverage) often matters more than algorithm choice. A simple logistic regression on clean labels outperforms a neural network on noisy labels.

---

## 3. Core Principles

**Empirical Risk Minimization (ERM)**: find the hypothesis h that minimizes average loss over training data.

```
h* = argmin_h (1/n) * sum_i L(h(x_i), y_i)
```

**Generalization**: the true goal is low loss on unseen data (test set), not just on training data.

**Bias-Variance Tradeoff**:
- High bias (underfitting): model too simple to capture the true pattern
- High variance (overfitting): model memorizes noise in training data
- Target: balance that minimizes total error = Bias^2 + Variance + Irreducible Noise

**No Free Lunch Theorem**: no single algorithm is best across all problems. Algorithm selection must be guided by domain knowledge and empirical validation.

**Feature engineering** determines the upper bound on model performance. Algorithms can only extract signal that is present in the features.

**i.i.d. assumption**: training and test data are assumed to be independent and identically distributed samples from the same underlying distribution. Violation (distribution shift) causes silent failures in production.

---

## 4. Types / Architectures / Strategies

### 4.1 Algorithm Families

| Algorithm | Output | Key Assumption | Training Complexity | Inference Complexity |
|-----------|--------|----------------|--------------------|--------------------|
| Linear Regression | Continuous | Linear relationship | O(nd^2) closed-form or O(nd * iter) GD | O(d) |
| Logistic Regression | Binary / Multiclass | Log-odds linear in features | O(nd * iter) | O(d) |
| Support Vector Machine | Binary / Multiclass | Max-margin separator exists | O(n^2) to O(n^3) | O(n_sv * d) |
| Decision Tree | Both | Axis-aligned splits suffice | O(nd log n) | O(depth) |
| k-Nearest Neighbors | Both | Similar inputs have similar outputs | O(1) (lazy) | O(nd) |
| Naive Bayes | Classification | Feature conditional independence | O(nd) | O(d) |

n = number of samples, d = number of features, iter = number of iterations, n_sv = number of support vectors

### 4.2 Parametric vs Non-Parametric

**Parametric** (linear regression, logistic regression, SVM with linear kernel, Naive Bayes):
- Fixed number of parameters regardless of training set size
- Fast inference, compact model
- Strong assumptions about data distribution or decision boundary

**Non-Parametric** (k-NN, decision trees, kernel SVM):
- Number of parameters (or effective complexity) grows with data
- More flexible, fewer assumptions
- Higher memory / computation at inference or training

### 4.3 Discriminative vs Generative Models

**Discriminative** (logistic regression, SVM, decision trees):
- Learn P(y | x) directly — the decision boundary
- Generally higher predictive accuracy with enough data

**Generative** (Naive Bayes, LDA):
- Learn P(x | y) and P(y), then apply Bayes theorem
- Can generate synthetic samples
- Work better with very small datasets

---

## 5. Architecture Diagrams

### 5.1 Supervised Learning Training Pipeline

```
Raw Data
    |
    v
+------------------+
| Data Collection  |  (labeled examples: (x_i, y_i))
+------------------+
    |
    v
+------------------+
| Preprocessing    |  impute missing, encode categoricals, remove duplicates
+------------------+
    |
    v
+------------------+
| Feature Eng.     |  scaling, polynomial features, interactions, selection
+------------------+
    |
    v
+----------------------------+
| Train / Val / Test Split   |  typically 70/15/15 or 80/10/10
+----------------------------+
    |           |
    |      Validation set (hyperparameter tuning, early stopping)
    v
+------------------+
| Model Training   |  minimize loss L(y_hat, y) over training set
+------------------+
    |
    v
+------------------+
| Evaluation       |  accuracy, F1, AUC-ROC, RMSE on held-out test set
+------------------+
    |
    v
+------------------+
| Deployment       |  serve predictions, monitor for drift
+------------------+
```

### 5.2 Bias-Variance Decomposition

```
Total Error
    |
    +--- Bias^2        (systematic error — model underfits)
    |
    +--- Variance      (sensitivity to training set — model overfits)
    |
    +--- Irreducible   (noise in labels — unavoidable)

High Bias                          High Variance
   |   _______________                |   /\    /\
   |  /               \              |  /  \  /  \   training
   | /                 \             | /    \/    \
   |/___________________\____________|/____________\___
                                      ^^^^
                              test error >> train error
```

### 5.3 Decision Boundary Shapes by Algorithm

```
Linear (LR, SVM linear)     SVM RBF / kernel         Decision Tree

  o   o  |  x   x           o   o                     |
  o   o  |  x   x            o o                      +--- feature_1 <= 3?
  o   o  |  x   x          [o o o]                   /               \
         |                  o o o                  Yes                No
  straight line          curved boundary        ____+___           ___+____
                                               |        |         |        |
                                            f2<=2    f2>2      f1>5    f1<=5
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Complete Training Pipeline in Python

```python
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.datasets import make_classification, make_regression
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.linear_model import LogisticRegression, LinearRegression
from sklearn.svm import SVC
from sklearn.tree import DecisionTreeClassifier
from sklearn.neighbors import KNeighborsClassifier
from sklearn.naive_bayes import GaussianNB
from sklearn.metrics import (
    accuracy_score, f1_score, roc_auc_score,
    mean_squared_error, r2_score, classification_report,
)


def build_classification_benchmark(
    n_samples: int = 5_000,
    n_features: int = 20,
    random_state: int = 42,
) -> dict[str, float]:
    """
    Train multiple classifiers on synthetic data and return AUC-ROC scores.
    Demonstrates correct pipeline ordering to prevent data leakage.
    """
    X, y = make_classification(
        n_samples=n_samples,
        n_features=n_features,
        n_informative=10,
        n_redundant=5,
        random_state=random_state,
    )

    # Stratified split preserves class distribution in every fold
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=random_state
    )

    # Each pipeline: scaler fit ONLY on training data, then transforms both sets.
    # Fitting the scaler on all data before splitting is a common data leakage bug.
    classifiers: dict[str, Pipeline] = {
        "LogisticRegression": Pipeline([
            ("scaler", StandardScaler()),
            # max_iter=100 is sklearn default but often causes ConvergenceWarning.
            # Always use 1000+ in practice.
            ("clf", LogisticRegression(max_iter=1000, C=1.0, random_state=random_state)),
        ]),
        "SVM_RBF": Pipeline([
            ("scaler", StandardScaler()),
            # SVM is not scale-invariant — scaler is mandatory
            ("clf", SVC(kernel="rbf", C=1.0, gamma="scale", probability=True, random_state=random_state)),
        ]),
        "DecisionTree": Pipeline([
            # Decision trees do not require scaling, but including it does no harm
            ("clf", DecisionTreeClassifier(max_depth=6, min_samples_split=20, random_state=random_state)),
        ]),
        "kNN": Pipeline([
            ("scaler", StandardScaler()),
            # kNN is extremely sensitive to scale — scaler is mandatory
            ("clf", KNeighborsClassifier(n_neighbors=5)),
        ]),
        "NaiveBayes": Pipeline([
            ("clf", GaussianNB()),
        ]),
    }

    results: dict[str, float] = {}
    for name, pipeline in classifiers.items():
        pipeline.fit(X_train, y_train)
        y_proba = pipeline.predict_proba(X_test)[:, 1]
        auc = roc_auc_score(y_test, y_proba)
        results[name] = round(auc, 4)
        print(f"{name:25s}  AUC-ROC = {auc:.4f}")

    return results


def demonstrate_leakage_bug() -> None:
    """
    Shows the data leakage anti-pattern and then the correct version.
    """
    X, y = make_classification(n_samples=1000, n_features=10, random_state=0)

    # --- WRONG: scaler sees test data during fit ---
    scaler_bad = StandardScaler()
    X_scaled_bad = scaler_bad.fit_transform(X)          # leaks test stats into scaler
    X_train_bad, X_test_bad, y_train, y_test = train_test_split(
        X_scaled_bad, y, test_size=0.2, random_state=0
    )
    # Model trained on "contaminated" scaling — optimistically biased metrics

    # --- CORRECT: scaler fit only on training data ---
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=0
    )
    scaler_good = StandardScaler()
    X_train_scaled = scaler_good.fit_transform(X_train)
    X_test_scaled = scaler_good.transform(X_test)       # transform only, never fit
    # This is the honest evaluation


if __name__ == "__main__":
    print("=== Classification Benchmark ===")
    scores = build_classification_benchmark()
```

### 6.2 Cross-Validation for Hyperparameter Tuning

```python
from sklearn.model_selection import GridSearchCV

def tune_with_cv(X_train: np.ndarray, y_train: np.ndarray) -> LogisticRegression:
    """
    Correct pattern: cross-validation runs entirely inside the training set.
    The test set is never touched during tuning.
    """
    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(max_iter=1000, random_state=42)),
    ])

    param_grid = {
        "clf__C": [0.001, 0.01, 0.1, 1.0, 10.0, 100.0],
        "clf__penalty": ["l1", "l2"],
        "clf__solver": ["liblinear"],   # liblinear supports both l1 and l2
    }

    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    search = GridSearchCV(
        pipeline,
        param_grid,
        cv=cv,
        scoring="roc_auc",
        n_jobs=-1,
        verbose=1,
    )
    search.fit(X_train, y_train)
    print(f"Best params: {search.best_params_}")
    print(f"Best CV AUC: {search.best_score_:.4f}")
    return search.best_estimator_
```

---

## 7. Real-World Examples

**Credit Scoring (Logistic Regression)**: Banks use logistic regression for loan default prediction. Regulatory interpretability requirements (Basel III, SR 11-7) mandate that the model's decisions can be explained — logistic regression coefficients provide that transparency. Features include payment history, credit utilization, account age.

**Spam Detection (Naive Bayes)**: Email spam filters historically used Multinomial Naive Bayes on word frequency features. Despite the obviously false independence assumption (words co-occur), it works well in practice because class-conditional probabilities are usually well-calibrated across the vocabulary.

**Medical Imaging Triage (SVM)**: Before deep learning, SVM with RBF kernels was the state-of-the-art for classifying histopathology slides. SVMs generalize well from small labeled medical datasets (hundreds of samples), where deep learning would overfit.

**Fraud Detection (Decision Trees → Gradient Boosting)**: A single decision tree is easily auditable (compliance teams can trace every prediction path). In practice, gradient-boosted trees (XGBoost, LightGBM) dominate production fraud systems because they combine hundreds of weak trees into a strong model.

**Search Ranking (Gradient Boosted Trees)**: Web search ranking uses Learning-to-Rank with gradient boosted trees (Ranking SVM, LambdaMART). Features include TF-IDF, BM25, PageRank, query-document similarity.

---

## 8. Tradeoffs

### 8.1 Algorithm Comparison

| Property | Lin. Reg | Log. Reg | SVM | Decision Tree | k-NN | Naive Bayes |
|----------|----------|----------|-----|--------------|------|-------------|
| Interpretable | High | High | Low (kernel) | High | Very Low | High |
| Training speed | Fast | Fast | Slow (n>10k) | Fast | Instant | Fast |
| Inference speed | O(d) | O(d) | O(n_sv * d) | O(depth) | O(n*d) | O(d) |
| Handles non-linearity | No | No | Yes (kernel) | Yes | Yes | No |
| Requires scaling | Yes | Yes | Yes | No | Yes | No |
| Handles missing values | No | No | No | Yes (strategies) | No | Yes (NB variants) |
| Handles high dimensions | Yes | Yes | Good | Poor | Very poor | Good |
| Small dataset performance | Good | Good | Excellent | Poor | Fair | Excellent |
| Large dataset scalability | Excellent | Excellent | Poor | Excellent | Poor | Excellent |

### 8.2 Evaluation Metrics

| Task | Metric | When to Use |
|------|--------|-------------|
| Binary classification, balanced | Accuracy | Only when classes are balanced |
| Binary classification, imbalanced | F1 score, AUC-ROC | Fraud, medical diagnosis |
| Multi-class | Macro/Micro F1 | NLP, image classification |
| Regression | RMSE, MAE, R^2 | RMSE penalizes large errors more |
| Ranking | NDCG, MRR | Search, recommendation |
| Probability calibration | Brier score, log-loss | Risk scoring, click-through rate |

---

## 9. When to Use / When NOT to Use

### Use supervised learning when:
- You have labeled examples for your target concept
- Historical data is representative of future conditions (i.i.d. assumption holds)
- You need to predict a specific output (not just discover structure)
- You have enough labeled data to train and validate (minimum ~10x more samples than features for linear models; more for complex models)

### Algorithm selection guidance:
- **Linear regression** — continuous target, interpretability required, roughly linear relationship
- **Logistic regression** — binary/multiclass, needs probability calibration, regulatory constraints on interpretability
- **SVM** — small-to-medium dataset (<100k rows), high-dimensional sparse features (text), non-linear boundary with RBF
- **Decision tree** — need exact rule extraction, mixed feature types, missing values
- **k-NN** — very small dataset with low dimensionality, no training phase needed (streaming new classes)
- **Naive Bayes** — text classification, very fast baseline, limited labeled data

### Do NOT use supervised learning when:
- Labels are unavailable or prohibitively expensive to collect (use unsupervised or semi-supervised)
- The data distribution shifts continuously (concept drift invalidates the trained model)
- You need the model to explain causal relationships (supervised models find correlation, not causation)
- n << d with no regularization (guaranteed to overfit)

---

## 10. Common Pitfalls

**Pitfall 1 — Data leakage via wrong split order**
A team preprocessing pipeline fit the StandardScaler on the entire dataset, then split train/test. AUC-ROC on the test set was 0.94 during development, but 0.71 in production. The scaler had encoded test set statistics (mean and variance) into the training step. Fix: always use sklearn Pipeline so that fit happens only on training folds.

**Pitfall 2 — Target leakage**
A fraud model included the field `account_closure_date` as a feature. Accounts closed due to fraud had a non-null value; legitimate accounts had null. The model learned to predict fraud from the consequence of fraud rather than its causes. This produced 99% accuracy in testing and 50% accuracy in production (where the field is always null at prediction time). Fix: remove any feature that could be caused by the target or that is only known after the event.

**Pitfall 3 — sklearn default max_iter=100 causing silent non-convergence**
LogisticRegression with the default max_iter=100 often fails to converge on real datasets. sklearn emits a ConvergenceWarning but many teams ignore it or suppress warnings in notebooks. The model is returned in a partially fitted state — coefficients not at the optimum. Fix: always set max_iter=1000 or higher, and check that the warning is absent.

**Pitfall 4 — Not scaling before SVM or k-NN**
SVM with RBF kernel computes Euclidean distances in feature space. A feature measured in dollars (range 0–100,000) dominates features measured as ratios (range 0–1). The SVM effectively ignores the ratio features. Fix: StandardScaler before every distance-based model.

**Pitfall 5 — Class imbalance hidden by accuracy**
A model predicting rare adverse drug events (1% positive rate) achieved 99% accuracy by predicting "no event" for every sample. Recall on the positive class was 0%. Fix: use stratified splits, monitor F1 / AUC-ROC / recall, apply class_weight="balanced" or oversample with SMOTE.

**Pitfall 6 — Temporal data split with random shuffle**
Time-series customer behavior data was split with random shuffle: future data leaked into training, teaching the model patterns it could not know at prediction time. Fix: always use time-based split for temporal data (train on earlier months, test on later months).

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| scikit-learn | Algorithms, pipeline, cross-validation, metrics | Industry standard; consistent API |
| XGBoost | Gradient boosted trees (regression + classification) | Often best out-of-box accuracy |
| LightGBM | Gradient boosting, faster than XGBoost on large data | Histogram-based; handles categorical natively |
| CatBoost | Gradient boosting with native categorical support | Strong on tabular data with many categoricals |
| PyTorch | Deep learning classifiers, custom loss functions | Flexibility over convenience |
| Optuna / Hyperopt | Bayesian hyperparameter search | Faster than grid search at scale |
| MLflow | Experiment tracking, model registry | Log params, metrics, artifacts per run |
| pandas-profiling / ydata-profiling | Automated EDA | Quick leakage and distribution check |
| SHAP | Model explainability — feature attribution | Works with any sklearn-compatible model |
| imbalanced-learn | SMOTE, ADASYN, RandomUnderSampler | Handle class imbalance |

---

## 12. Interview Questions with Answers

**Q: What is the difference between regression and classification in supervised learning?**
Regression predicts a continuous numeric output (e.g., price, temperature), while classification predicts a discrete class label (e.g., spam/not-spam, digit 0-9). The key difference is the output space: regression uses loss functions like MSE or MAE; classification uses cross-entropy or hinge loss. Some algorithms (SVM, decision trees) handle both natively; logistic regression for classification is named misleadingly but outputs a probability via the sigmoid.

**Q: Explain the bias-variance tradeoff.**
Total expected test error decomposes into Bias^2 + Variance + Irreducible Noise. Bias measures systematic error (underfitting — model too simple). Variance measures sensitivity to training data fluctuations (overfitting — model too complex). Reducing one typically increases the other. Fix: regularization, ensemble methods (bagging reduces variance, boosting reduces bias), and cross-validation to find the sweet spot.

**Q: What is data leakage and how do you prevent it?**
Data leakage occurs when information from the test set (or from the future) influences model training, producing optimistic evaluation metrics that do not reflect production performance. The two main forms are feature leakage (features that encode the target) and preprocessing leakage (fitting scalers or encoders on all data before splitting). Prevention: use sklearn Pipeline so all preprocessing fits only on training folds; enforce temporal ordering for time-series data; audit every feature for causal impossibility.

**Q: When would you choose logistic regression over a more powerful model like gradient boosting?**
Choose logistic regression when interpretability is required (regulated industries, clinical decisions), when training data is small (logistic regression generalizes well with limited samples), when you need fast training and inference in a high-throughput system, or as a calibrated probability baseline. Gradient boosting wins when predictive accuracy matters most and interpretability is secondary.

**Q: How do you handle class imbalance in a classification problem?**
Use multiple complementary strategies: (1) choose appropriate metrics — AUC-ROC, F1, precision-recall curve instead of accuracy; (2) apply class_weight="balanced" in sklearn algorithms; (3) oversample the minority class with SMOTE or ADASYN; (4) undersample the majority class; (5) use threshold tuning on the predicted probability to trade precision for recall. The right balance depends on the cost ratio of false positives to false negatives in the business context.

**Q: What is the curse of dimensionality and how does it affect supervised learning?**
As feature dimensionality d increases, the volume of the space grows exponentially, so training points become sparse. Distance-based methods (k-NN, SVM RBF) suffer most: all points appear equidistant, destroying the neighborhood structure. Linear models are less affected but require n >> d to avoid overfitting. Solutions: feature selection, dimensionality reduction (PCA, UMAP), regularization (L1 sparsity), and collecting more data.

**Q: What is overfitting and what techniques prevent it?**
Overfitting means the model learns noise in training data rather than the true signal, resulting in low training error but high test error. Prevention techniques: regularization (L1, L2, dropout for neural nets), early stopping (monitor validation loss), data augmentation (increase effective training set size), reducing model complexity (shallow trees, fewer parameters), cross-validation to detect the gap between training and validation performance, and ensemble averaging (bagging).

**Q: Explain the no-free-lunch theorem and its practical implications.**
The NFL theorem states that averaged over all possible problems, no algorithm outperforms any other. In practice this means: do not have a single default algorithm — evaluate multiple algorithms on your specific data distribution. Domain knowledge, feature engineering, and empirical experimentation matter more than algorithm selection. Always run a simple baseline (logistic regression, decision stump) before investing in complex models.

**Q: What evaluation metric would you use for a fraud detection model with 0.1% positive rate?**
Accuracy is useless here (99.9% by predicting all negatives). The right metrics are: Precision-Recall AUC (focuses on the rare positive class, unaffected by the large number of true negatives), F1 score or F-beta score (beta > 1 to weight recall if missed fraud is costlier than false alarms), and a confusion matrix to understand false negative and false positive trade-offs at the chosen operating threshold.

**Q: What is cross-validation and why is it necessary?**
Cross-validation (CV) is a technique for estimating generalization performance using only the training set, by repeatedly splitting it into train and validation subsets. k-fold CV divides data into k equal parts, trains on k-1 folds, and evaluates on the held-out fold, rotating until every fold has served as validation. This is necessary because: a single train/val split is high variance (depends on which samples happened to land in each set), and it enables hyperparameter tuning without touching the held-out test set.

---

## 13. Best Practices

1. Always create a baseline before building a complex model. A majority-class classifier or logistic regression provides a sanity-check threshold.

2. Use sklearn Pipelines for every workflow. Pipelines prevent data leakage, simplify cross-validation, and make deployment straightforward (serialize the whole pipeline, not just the model).

3. Do exploratory data analysis before modeling. Check class distributions, missing value patterns, feature correlations, outliers, and data types. Problems discovered in EDA take 5 minutes to fix; problems discovered in production take weeks.

4. Split chronologically for time-series data. Random shuffle leaks future information into the past.

5. Tune hyperparameters with cross-validation on the training set only. Never use the test set to select hyperparameters — it becomes a second training signal.

6. Report multiple metrics. For classification: precision, recall, F1, AUC-ROC. For regression: RMSE, MAE, R^2. Reporting only accuracy or only R^2 hides important failure modes.

7. Calibrate probabilities when downstream decisions depend on exact probability values (risk scores, expected value calculations). Logistic regression is usually well-calibrated; SVM and decision trees often require Platt scaling or isotonic regression.

8. Monitor for distribution shift in production. Compute feature statistics and prediction distributions daily. Retrain when they diverge from training-time statistics.

9. Document the negative results. Failed experiments (wrong feature engineering, a model that overfit, a baseline that beat the complex model) are as valuable as successes.

10. Validate that your evaluation setup reflects production reality — same feature availability at prediction time, same population, same time horizon.

---

## 14. Case Study

**Problem**: a mid-size e-commerce company has 2 million customers and wants to predict 30-day churn (no purchase in 30 days after last activity). The current system is a simple rule: flag customers inactive for 14 days. Business impact: incorrectly flagging churned customers triggers unnecessary 20%-off discount emails, costing $4 per customer. Missing a churning customer costs $50 in lost revenue.

**Dataset**: 180 days of purchase history. Positive label: churned (no purchase in days 31-60 after observation window). Negative label: retained. Class ratio: 15% churned.

**Architecture**:
```
Raw Events (clickstream, purchases, support tickets)
    |
    v
Feature Store (Spark batch job, daily refresh)
    |--- recency: days since last purchase
    |--- frequency: purchases in last 30 days
    |--- monetary: total spend last 90 days
    |--- support_tickets: count last 30 days
    |--- category_diversity: distinct categories purchased
    |
    v
Training Pipeline (sklearn Pipeline)
    |--- StandardScaler
    |--- LogisticRegression (C=0.1, max_iter=1000, class_weight="balanced")
    |
    v
Evaluation (held-out 20% test set, temporal split)
    |--- AUC-ROC: 0.82
    |--- Precision at threshold 0.35: 0.61
    |--- Recall at threshold 0.35: 0.74
    |
    v
Business Calibration
    |--- cost matrix: FP=$4, FN=$50
    |--- optimal threshold: 0.35 (maximizes expected profit)
    |
    v
Batch Scoring (nightly, score all active customers)
    |--- store scores in feature store
    |--- trigger discount email for score > 0.35
```

**Outcome**: replacing the 14-day rule with the logistic regression model reduced unnecessary discounts by 38% (fewer false positives) while increasing detected churners by 22% (higher recall on true churners). Net revenue impact: +$1.2M annualized.

**Lessons**: the recency feature alone explained 60% of predictive power (confirmed by SHAP). The class_weight="balanced" parameter was essential — without it, the model achieved 85% accuracy but 0% recall on churned customers. Temporal split for evaluation revealed that the model trained on summer behavior needed retraining every quarter to account for seasonal purchasing patterns.
