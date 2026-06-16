# ML Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/ml/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 36 Modules

| Module Directory | Phase | Key Concepts | Sub-files |
|-----------------|-------|-------------|-----------|
| `linear_algebra_and_calculus/` | 1 | Vectors, matrices, eigendecomposition, SVD, gradients, chain rule | — |
| `probability_and_statistics/` | 1 | Distributions, Bayes, MLE, MAP, hypothesis testing, CLT | — |
| `optimization_theory/` | 1 | SGD, momentum, Adam, AdamW, LR schedules, convexity, saddle points | — |
| `information_theory/` | 1 | Entropy, cross-entropy, KL divergence, mutual information | — |
| `supervised_learning/` | 2 | Linear/logistic regression, SVM, decision trees, KNN, Naive Bayes | linear_models, support_vector_machines, decision_trees, bayesian_methods |
| `ensemble_methods/` | 2 | Random Forest, XGBoost, LightGBM, CatBoost, stacking, blending | random_forests, gradient_boosting, xgboost_lightgbm, stacking_and_blending |
| `unsupervised_learning/` | 2 | k-means, DBSCAN, hierarchical, PCA, t-SNE, UMAP | — |
| `feature_engineering/` | 2 | Encoding, scaling, imputation, target encoding, feature selection | — |
| `model_evaluation_and_selection/` | 2 | Cross-validation, AUC-ROC/PR, calibration, bias-variance, Optuna | — |
| `neural_network_fundamentals/` | 3 | MLPs, backprop, activations, weight init, batch norm, dropout | — |
| `convolutional_neural_networks/` | 3 | Conv2D, pooling, ResNet, EfficientNet, transfer learning | — |
| `recurrent_neural_networks/` | 3 | LSTM, GRU, vanishing gradients, seq2seq, teacher forcing | — |
| `training_deep_networks/` | 3 | LR warmup, gradient clipping, mixed precision, augmentation | — |
| `generative_models/` | 3 | VAEs, GANs, Diffusion (DDPM), mode collapse, FID | — |
| `computer_vision/` | 4 | Object detection, segmentation, ViT, CLIP, SSL | object_detection, image_segmentation, vision_transformers, self_supervised_vision |
| `natural_language_processing/` | 4 | Word2Vec, GloVe, TF-IDF, NER, CRF, topic modeling | bert_and_pretrained_models, attention_and_seq2seq, text_representation_and_retrieval, nlp_evaluation_and_metrics |
| `recommender_systems/` | 4 | Two-tower, collaborative filtering, LTR, bandits | collaborative_filtering, deep_learning_recommenders, retrieval_and_ranking, content_and_hybrid, online_learning_and_bandits |
| `time_series_forecasting/` | 4 | ARIMA, Prophet, DeepAR, Temporal Fusion Transformer | — |
| `reinforcement_learning/` | 4 | MDP, Q-learning, DQN, PPO, actor-critic, RLHF | — |
| `ml_system_design/` | 5 | 6-step framework, feature stores, A/B testing, latency | design_framework, feature_store_design, training_pipeline_design, ab_testing_for_ml, latency_and_throughput_optimization |
| `data_pipelines_and_processing/` | 5 | PySpark, Great Expectations, DVC, schema evolution | — |
| `distributed_training/` | 5 | DDP, FSDP, DeepSpeed ZeRO, gradient accumulation | — |
| `experiment_tracking_and_versioning/` | 5 | MLflow, W&B, Optuna, DVC, reproducibility | — |
| `gpu_and_hardware_optimization/` | 5 | CUDA, tensor cores, profiling, gradient checkpointing | — |
| `model_serving_and_inference/` | 6 | TorchServe, ONNX, dynamic batching, canary, shadow mode | — |
| `model_compression_and_efficiency/` | 6 | PTQ, QAT, pruning, knowledge distillation, TensorRT | — |
| `monitoring_and_drift_detection/` | 6 | Data/concept drift, PSI, KS test, SHAP monitoring | — |
| `mlops_and_ci_cd/` | 6 | MLflow Registry, Kubeflow Pipelines, canary, rollback | — |
| `graph_neural_networks/` | 7 | GCN, GraphSAGE, GAT, GIN, message passing, PyG | — |
| `self_supervised_and_contrastive_learning/` | 7 | NT-Xent, BYOL, ELECTRA, graph SSL, tabular SSL | — |
| `causal_inference_and_ml/` | 7 | Potential outcomes, propensity scores, uplift, CausalForest | — |
| `ml_interview_patterns/` | 8 | Design framework, debug checklist, tradeoff templates | — |
| `model_selection_and_algorithm_choice/` | Cross-cutting | Algorithm decision matrix, problem-type → algorithm mapping, data-size vs complexity regimes | — |
| `active_learning_and_weak_supervision/` | 5 | Uncertainty/diversity sampling, QBC, BALD, Snorkel labeling functions, label model, data-centric AI | — |
| `adversarial_ml_and_robustness/` | 7 | FGSM/PGD/C&W evasion, poisoning, backdoors, model extraction, membership inference, adversarial training, randomized smoothing | — |
| `uncertainty_quantification_and_conformal_prediction/` | 7 | Aleatoric vs epistemic, MC dropout, deep ensembles, ECE/temperature scaling, conformal sets/intervals, CQR | — |

---

## Sub-Files — 27 Topic Sub-Files

| Module | Sub-files |
|--------|-----------|
| `supervised_learning/` | linear_models, support_vector_machines, decision_trees, bayesian_methods |
| `ensemble_methods/` | random_forests, gradient_boosting, xgboost_lightgbm, stacking_and_blending |
| `computer_vision/` | object_detection, image_segmentation, vision_transformers, self_supervised_vision |
| `natural_language_processing/` | bert_and_pretrained_models, attention_and_seq2seq, text_representation_and_retrieval, nlp_evaluation_and_metrics, tokenization_deep_dive |
| `recommender_systems/` | collaborative_filtering, deep_learning_recommenders, retrieval_and_ranking, content_and_hybrid, online_learning_and_bandits |
| `ml_system_design/` | design_framework, feature_store_design, training_pipeline_design, ab_testing_for_ml, latency_and_throughput_optimization |

---

## Case Studies — 22 Total

`case_studies/` directory. Learning-path index: `case_studies/README.md` (mandatory; update with every new case study).

**Legacy 12-section (10):** design_recommendation_engine, design_fraud_detection, design_search_ranking, design_image_classification_pipeline, design_ads_click_prediction, design_anomaly_detection, design_demand_forecasting, design_content_feed_ranking, design_autonomous_driving_perception, design_ml_platform

**Principal 11-section (12):** design_churn_prediction, design_credit_risk_scoring, design_eta_prediction, design_marketplace_matching, design_customer_ltv_prediction, design_multi_touch_attribution, design_dynamic_pricing, design_nlp_classification_pipeline, design_real_time_personalization, design_semantic_search_engine, design_ner_pipeline, design_question_answering_system

---

## Cross-Cutting Shared Primitives — 5 Files

`case_studies/cross_cutting/` — all use the 14-section template:

| File | When Relevant |
|------|--------------|
| `feature_store_and_point_in_time_correctness/` | Any case study with training/serving feature pipelines |
| `model_calibration_and_thresholding/` | Any classification case study |
| `responsible_ai_fairness_and_explainability/` | Any case study with regulatory or fairness concerns |
| `experimentation_and_online_evaluation/` | Any case study with A/B testing or bandit strategies |
| `drift_monitoring_and_retraining/` | Any production ML case study |

---

## Cross-Reference Map

| ML Module | See Also (other sections) |
|-----------|--------------------------|
| `neural_network_fundamentals/` | `../../llm/foundations_and_architecture/` — how transformers build on MLP theory |
| `distributed_training/` | `../../llm/training_infrastructure/` — LLM-scale distributed training; `../../devops/ml_platform_and_gpu_infrastructure/` |
| `model_serving_and_inference/` | `../../llm/inference_engines/` — vLLM, TensorRT-LLM; `../../llm/deployment_and_mlops/` |
| `model_compression_and_efficiency/` | `../../llm/optimization_and_quantization/` — GPTQ, AWQ, Flash Attention |
| `computer_vision/` | `../../llm/multimodal_models/` — VLMs, vision encoders |
| `model_evaluation_and_selection/` | `../../llm/evaluation_and_benchmarks/` — MMLU, LLM-as-judge, RAGAs |
| `experiment_tracking_and_versioning/` | `../../llm/llm_observability_and_monitoring/` — Langfuse, Arize Phoenix |
| `natural_language_processing/` (tokenization, retrieval) | `../../llm/tokenization_and_embeddings/` — BPE/WordPiece/SentencePiece at LLM scale; `../../llm/rag_fundamentals/` — dense retrieval reused by RAG |
| `adversarial_ml_and_robustness/` | `../../llm/llm_security/` — prompt injection/jailbreaks; `../../llm/guardrails_and_content_safety/` |
| `uncertainty_quantification_and_conformal_prediction/` | `case_studies/cross_cutting/model_calibration_and_thresholding/`; `../../llm/evaluation_and_benchmarks/` — abstention/uncertainty signals |
| `active_learning_and_weak_supervision/` | `../../llm/data_flywheels_and_continuous_learning/`; `../../llm/synthetic_data_generation/` |

---

## LLM Non-Overlap Boundary

This section covers: classical ML algorithms, deep learning fundamentals, computer vision, NLP pre-transformer, recommender systems, MLOps for non-LLM models, distributed training fundamentals.

The `llm/` section covers: transformers, fine-tuning LLMs, RAG, agents, LLM inference engines, LLM-specific evaluation, prompt engineering.

Overlap zone (cover in both with cross-references): distributed training, model serving, monitoring/drift, evaluation metrics.

---

## Content Rules (ML-specific)

- All code in Python with type hints (3.10+ style)
- Use numpy, sklearn, PyTorch, or relevant library — no pseudocode
- Minimum 10 Q&As per module; 15+ for sub-files

## Adding a New ML Module

1. Create `<module_name>/README.md` — 14-section template; minimum 10 Q&As (15+ for sub-files)
2. All code in Python with type hints (3.10+ style)
3. Update `README.md` module table
4. Update root `README.md` ML phase table
5. Update root `CLAUDE.md` ML module table

## Adding a New ML Case Study

1. Write the case study — 11-section principal template for new studies
2. Update `case_studies/README.md` — add to correct phase, update dependency map, add interview prep row
3. Update `README.md` case study count and list
4. Update root `CLAUDE.md` case study list

---

## Visual Intuition Diagrams

Section 5 (Architecture Diagrams) and any hard-to-picture concept should use an
**ASCII visual intuition diagram** that makes an abstract relationship visible
(constraint grid, before/after-with-delta, stacked flow, routing fan-out, bar
chart, or curve/sketch). Generate and validate them with the
`/visual-intuition-diagrams` skill. The full archetype catalog, conventions
(ASCII only, no tabs, no emojis, widest line <= 100 cols, caption every diagram),
and the `diagram_tools.py` validator live in root `CLAUDE.md` -> "Visual Intuition
Diagrams".
