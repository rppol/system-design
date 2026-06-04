# ML Case Studies — Learning Path

Twenty-two end-to-end case studies covering the full spectrum of senior ML engineer and senior data scientist interview scenarios. Ten use the legacy 12-section template (rich, detailed). Twelve use the 11-section principal template (requirements → scale → architecture → deep dives → design decisions → real world → tools → playbook → pitfalls → capacity → interview). Five cross-cutting shared-primitive files in `cross_cutting/` cover infrastructure patterns referenced by multiple case studies.

---

## Quick Start

If you have time for three case studies before an interview, read these:

1. **[Recommendation Engine](design_recommendation_engine.md)** — covers two-tower retrieval, ANN search, feature store, ranking, and A/B testing: the canonical ML system design question at all top companies.
2. **[Churn Prediction](design_churn_prediction.md)** — covers the full DS lifecycle: feature engineering, temporal CV, calibration, uplift modeling, SHAP explainability; the most common senior DS case study question.
3. **[ML Platform](design_ml_platform.md)** — covers feature stores, training orchestration, model registry, serving infra, and experiment tracking; asked at Uber, Google, Meta for MLE infra roles.

---

## Full Learning Path

Studies are grouped by primary engineering concern, not product category.

### Group 1: Supervised Learning Fundamentals

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [Churn Prediction](design_churn_prediction.md) | Classification, calibration, uplift | Temporal CV, class imbalance, isotonic calibration, T-learner uplift, SHAP adverse-action |
| [Credit Risk Scoring](design_credit_risk_scoring.md) | Regulated ML, interpretability | WOE/scorecard, monotonic constraints, reject inference, ECOA/FCRA adverse-action, fairness audit |
| [Customer LTV Prediction](design_customer_ltv_prediction.md) | Survival analysis, censored regression | BG/NBD vs LightGBM Cox, censoring correction, cohort CV, P25/P75 confidence intervals, LTV-to-bid mapping |
| [Image Classification Pipeline](design_image_classification_pipeline.md) | Deep learning production | CNN + transfer learning, distributed training, model registry, serving pipeline |

### Group 2: Ranking and Retrieval

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [Search Ranking](design_search_ranking.md) | LTR, query understanding | BM25 + ML blend, LambdaMART, query features, online evaluation via interleaving |
| [Recommendation Engine](design_recommendation_engine.md) | Two-tower + ranking | Retrieval at scale (FAISS/ScaNN), candidate ranking, feature store, A/B experiment design |
| [Real-Time Personalization](design_real_time_personalization.md) | Session context + online serving | Two-tower with session encoder, GRU session model, FAISS at 50k req/s, epsilon-greedy exploration, cold-start |
| [Content Feed Ranking](design_content_feed_ranking.md) | Multi-objective ranking | Engagement prediction, exploration-exploitation, position bias correction, OEC design |
| [Ads Click Prediction](design_ads_click_prediction.md) | Sparse features, low-latency | Logistic regression, GBDT, DLRM, embedding tables, calibration at 1ms SLO |

### Group 3: Regression and Forecasting

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [ETA Prediction](design_eta_prediction.md) | Real-time regression, quantile loss | GBDT + quantile regression, cyclic features, real-time traffic pipeline, p90 coverage SLO |
| [Demand Forecasting](design_demand_forecasting.md) | Time series at scale | ARIMA vs LightGBM vs TFT, hierarchical forecasting, uncertainty quantification |
| [Marketplace Matching](design_marketplace_matching.md) | Multi-model composition | Demand/supply forecasting + LambdaRank + combinatorial optimization, switchback experiments |

### Group 4: Anomaly Detection and Fraud

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [Fraud Detection](design_fraud_detection.md) | Imbalanced classification, streaming | Real-time inference, class imbalance, concept drift, graph features, velocity counters |
| [Anomaly Detection](design_anomaly_detection.md) | Unsupervised + hybrid | Isolation forest, autoencoders, streaming detection, alert calibration |

### Group 5: Computer Vision and Perception

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [Image Classification Pipeline](design_image_classification_pipeline.md) | End-to-end CV | ResNet/EfficientNet, transfer learning, data augmentation, multi-GPU training |
| [Autonomous Driving Perception](design_autonomous_driving_perception.md) | Safety-critical ML | Multi-sensor fusion, object detection, BEV representation, latency budget under safety constraints |

### Group 6: NLP Engineering

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [NLP Classification Pipeline](design_nlp_classification_pipeline.md) | Text classification at scale | TF-IDF+LR baseline → DistilBERT cascade, active learning, knowledge distillation, serving BERT at < 25ms |
| [Semantic Search Engine](design_semantic_search_engine.md) | Dense retrieval + hybrid reranking | Bi-encoder (SBERT), FAISS IVF, RRF hybrid merge, cross-encoder reranking, hard negative mining, Matryoshka embeddings |
| [NER Pipeline](design_ner_pipeline.md) | BERT token classification | BIO tagging, BERT-CRF, span extraction, nested NER, active learning for annotation, streaming document processing |
| [Question Answering System](design_question_answering_system.md) | Extractive + open-domain QA | Span prediction (BERT), DPR retriever + reader, multi-hop QA, answer confidence calibration, latency/accuracy tradeoffs |

### Group 7: Product Data Science (DS Analytics)

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [Multi-Touch Attribution](design_multi_touch_attribution.md) | Causal channel credit | Markov chains, Shapley values (Monte Carlo), SUTVA violations in attribution, geo-holdout validation |
| [Dynamic Pricing](design_dynamic_pricing.md) | Demand optimization | Demand elasticity estimation, constrained price optimizer, contextual bandits, price war prevention |

### Group 8: ML Infrastructure

| Study | Primary Concern | What It Teaches |
|---|---|---|
| [ML Platform](design_ml_platform.md) | Feature stores, training, serving | End-to-end MLOps: feature store design, Kubeflow, model registry, A/B infra |

---

## Cross-Cutting Shared Primitives

Five infrastructure patterns appear across multiple studies. Read a cross-cutting file just-in-time when you encounter its reference in a case study.

| File | Topic | Recommended read timing |
|---|---|---|
| [Feature Store and PIT Correctness](cross_cutting/feature_store_and_point_in_time_correctness.md) | Online/offline stores, PIT joins, training-serving skew | Before Churn, Credit Risk, ETA, Marketplace |
| [Model Calibration and Thresholding](cross_cutting/model_calibration_and_thresholding.md) | Platt/isotonic, ECE, cost-sensitive threshold, calibration monitoring | Before Churn, Credit Risk, Fraud, Ads |
| [Responsible AI, Fairness, and Explainability](cross_cutting/responsible_ai_fairness_and_explainability.md) | Demographic parity, equalized odds, SHAP, GDPR | Before Credit Risk, Churn, Marketplace |
| [Experimentation and Online Evaluation](cross_cutting/experimentation_and_online_evaluation.md) | OEC/metric design, A/B testing, CUPED, switchback | Before any study with an A/B component |
| [Drift Monitoring and Retraining](cross_cutting/drift_monitoring_and_retraining.md) | PSI, KS test, champion/challenger, label latency | Before Fraud, Churn, ETA, Marketplace |

---

## Dependency Map

Studies that build on patterns from others — read prerequisites first.

```
[linear algebra + probability]
        |
        v
[Supervised Learning fundamentals]
 (churn_prediction → calibration, uplift)
 (credit_risk_scoring → WOE, monotonic, fairness)
        |
   +----+----+
   |         |
   v         v
[Ranking &  [Anomaly &
 Retrieval]   Fraud]
 search_ranking         fraud_detection
 recommendation_engine  anomaly_detection
 content_feed_ranking
 ads_click_prediction
   |
   v
[Multi-model Composition]
 eta_prediction ──────────────> marketplace_matching
 demand_forecasting ───────────> marketplace_matching

[Computer Vision]
 image_classification_pipeline
         |
         v
 autonomous_driving_perception

[Infrastructure anchor]
 ml_platform ──── referenced by all studies
                  (feature store, experiment tracking, registry)
```

**New principal-template studies (4) and their dependencies:**
```
cross_cutting/feature_store_and_pit_correctness  ──> design_churn_prediction
cross_cutting/model_calibration_and_thresholding ──> design_churn_prediction
                                                  └─> design_credit_risk_scoring
cross_cutting/responsible_ai_fairness            ──> design_credit_risk_scoring
cross_cutting/experimentation_and_online_eval    ──> design_churn_prediction
                                                  └─> design_eta_prediction
                                                  └─> design_marketplace_matching
cross_cutting/drift_monitoring_and_retraining    ──> all 4 principal-template studies
model_selection_and_algorithm_choice             ──> all 4 principal-template studies
```

---

## Interview Prep Shortcuts

Map from common "design X" interview question to the best case study.

| "Design a..." question | Best case study | Key topics covered |
|---|---|---|
| Recommendation system | [Recommendation Engine](design_recommendation_engine.md) | Two-tower, FAISS, ranking, feature store |
| Fraud detection system | [Fraud Detection](design_fraud_detection.md) | Imbalance, streaming, velocity features, drift |
| Search ranking system | [Search Ranking](design_search_ranking.md) | LTR, BM25+ML, interleaving, query understanding |
| Churn prediction model | [Churn Prediction](design_churn_prediction.md) | Calibration, uplift, SHAP, temporal CV |
| Credit scoring model | [Credit Risk Scoring](design_credit_risk_scoring.md) | WOE, monotonic, fairness, adverse-action |
| Ads CTR prediction | [Ads Click Prediction](design_ads_click_prediction.md) | Logistic, GBDT, DLRM, sparse features, calibration |
| ETA prediction | [ETA Prediction](design_eta_prediction.md) | Quantile regression, real-time traffic, cold-start |
| Marketplace matching | [Marketplace Matching](design_marketplace_matching.md) | Multi-model composition, combinatorial opt, switchback |
| Demand forecasting | [Demand Forecasting](design_demand_forecasting.md) | ARIMA, LightGBM, hierarchical, uncertainty |
| Anomaly detection | [Anomaly Detection](design_anomaly_detection.md) | Isolation forest, autoencoders, streaming, alerting |
| Content feed ranking | [Content Feed Ranking](design_content_feed_ranking.md) | Multi-objective, explore-exploit, position bias |
| Image classifier pipeline | [Image Classification Pipeline](design_image_classification_pipeline.md) | Transfer learning, distributed training, serving |
| Self-driving perception | [Autonomous Driving Perception](design_autonomous_driving_perception.md) | Sensor fusion, object detection, safety constraints |
| ML platform | [ML Platform](design_ml_platform.md) | Feature store, training DAG, model registry, A/B infra |
| Customer LTV prediction | [Customer LTV Prediction](design_customer_ltv_prediction.md) | BG/NBD vs Cox survival, censoring, cohort CV, bid optimization |
| Multi-touch attribution | [Multi-Touch Attribution](design_multi_touch_attribution.md) | Markov removal effects, Shapley Monte Carlo, geo holdout |
| Dynamic pricing system | [Dynamic Pricing](design_dynamic_pricing.md) | Demand elasticity, price optimizer, contextual bandits |
| Text classification pipeline | [NLP Classification Pipeline](design_nlp_classification_pipeline.md) | TF-IDF+LR → DistilBERT cascade, active learning, distillation |
| Real-time personalization | [Real-Time Personalization](design_real_time_personalization.md) | Session context, two-tower + GRU, FAISS at 50k req/s, exploration |
| Semantic search engine | [Semantic Search Engine](design_semantic_search_engine.md) | Bi-encoder FAISS IVF, RRF hybrid, cross-encoder reranking, hard negatives |
| Named entity recognition | [NER Pipeline](design_ner_pipeline.md) | BERT-CRF token classification, BIO tagging, span extraction, active learning |
| Question answering system | [Question Answering System](design_question_answering_system.md) | BERT extractive QA, DPR open-domain, retriever + reader, multi-hop |

---

## Build Manifest (Principal-Template Studies)

Track completion status across sessions.

| File | Status | Notes |
|---|---|---|
| `cross_cutting/feature_store_and_point_in_time_correctness.md` | done | 14-section, 15+ Q&As |
| `cross_cutting/model_calibration_and_thresholding.md` | done | 14-section, 15+ Q&As |
| `cross_cutting/responsible_ai_fairness_and_explainability.md` | done | 14-section, 14 Q&As |
| `cross_cutting/experimentation_and_online_evaluation.md` | done | 14-section, 14 Q&As |
| `cross_cutting/drift_monitoring_and_retraining.md` | done | 14-section, 12 Q&As |
| `cross_cutting/README.md` | done | Index table |
| `design_churn_prediction.md` | done | 11-section principal template, 11 Q&As |
| `design_credit_risk_scoring.md` | done | 11-section principal template, 11 Q&As |
| `design_eta_prediction.md` | done | 11-section principal template, 10 Q&As |
| `design_marketplace_matching.md` | done | 11-section principal template, 10 Q&As |
| `ml/model_selection_and_algorithm_choice/README.md` | done | 14-section module, 15 Q&As |
| `design_customer_ltv_prediction.md` | done | 11-section principal template, 10 Q&As |
| `design_multi_touch_attribution.md` | done | 11-section principal template, 10 Q&As |
| `design_dynamic_pricing.md` | done | 11-section principal template, 10 Q&As |
| `design_nlp_classification_pipeline.md` | done | 11-section principal template, 10 Q&As |
| `design_real_time_personalization.md` | done | 11-section principal template, 10 Q&As |
| `design_semantic_search_engine.md` | done | 11-section principal template, 10 Q&As |
| `design_ner_pipeline.md` | done | 11-section principal template, 10 Q&As |
| `design_question_answering_system.md` | done | 11-section principal template, 10 Q&As |
