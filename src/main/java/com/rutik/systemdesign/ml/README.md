# Machine Learning — Complete Educational Guide

This section provides a structured, senior-engineer-level guide to Machine Learning — from mathematical foundations through production MLOps. It covers classical algorithms, deep learning architectures, ML system design, and everything needed to pass ML design interviews at top-tier companies. The content is deliberately scoped to core ML concepts and infrastructure; it does not overlap with the LLM section, which handles transformer architecture, fine-tuning, RAG, agents, and LLM-specific deployment. Together the two sections form a complete AI/ML reference.

---

## Intuition

Machine Learning is the discipline of building systems that improve from experience — the way a fraud analyst gets better at spotting scams after reviewing thousands of transactions, ML systems improve by fitting patterns in data rather than following hand-written rules.

Mental model: **data + objective + optimization = a model**. Every ML algorithm is a specific instantiation of that triplet. Understanding which triplet to pick — and why — is the core ML engineering skill.

Why it matters: nearly every production software system built today has at least one ML component, whether it is a search ranker, a recommendation engine, a fraud filter, or an anomaly detector. Engineers who understand ML from math through deployment build more reliable, cost-effective systems.

Key insight: the majority of ML failures are not algorithm failures — they are data pipeline failures, feature distribution failures, or evaluation methodology failures. System thinking matters as much as model knowledge.

---

## What This Section Covers

Forty modules organized across eight learning phases take you from linear algebra through production MLOps. Nine topics contain deep-dive sub-files (36 sub-files total), and `case_studies/cross_cutting/` contains five shared-primitive files, all following the same 14-section template used throughout this repository. Twenty-two end-to-end case studies — each covering a real-world ML system — demonstrate how individual concepts combine into full system designs. A dedicated algorithm-selection module (#33) consolidates the "which algorithm, when and why" rationale referenced by every case study. The section is structured so it can be read sequentially as a learning curriculum or used as a reference when preparing for a specific interview topic.

---

## All Topics (45 Modules)

| # | Topic | Key Concepts | Phase | Difficulty |
|---|-------|-------------|-------|------------|
| 1 | [Linear Algebra and Calculus](linear_algebra_and_calculus/README.md) | Vectors, matrices, eigendecomposition, gradients, chain rule, Jacobians, Hessians | 1 — Mathematical Foundations | Beginner |
| 2 | [Probability and Statistics](probability_and_statistics/README.md) | Distributions, Bayes theorem, MLE, MAP, hypothesis testing, confidence intervals | 1 — Mathematical Foundations | Beginner |
| 3 | [Optimization Theory](optimization_theory/README.md) | Gradient descent, SGD, Adam, convexity, saddle points, learning rate schedules | 1 — Mathematical Foundations | Intermediate |
| 4 | [Information Theory](information_theory/README.md) | Entropy, KL divergence, mutual information, cross-entropy loss, compression | 1 — Mathematical Foundations | Intermediate |
| 5 | [Supervised Learning](supervised_learning/README.md) | Linear/logistic regression, SVMs, decision trees, Naive Bayes, k-NN | 2 — Classical ML | Beginner |
| 6 | [Ensemble Methods](ensemble_methods/README.md) | Bagging, boosting, random forests, gradient boosting, XGBoost, LightGBM, stacking | 2 — Classical ML | Intermediate |
| 7 | [Unsupervised Learning](unsupervised_learning/README.md) | k-means, DBSCAN, hierarchical clustering, PCA, t-SNE, UMAP, autoencoders | 2 — Classical ML | Intermediate |
| 8 | [Feature Engineering](feature_engineering/README.md) | Encoding, scaling, imputation, feature selection, interaction terms, embeddings | 2 — Classical ML | Intermediate |
| 9 | [Model Evaluation and Selection](model_evaluation_and_selection/README.md) | Cross-validation, AUC-ROC, precision-recall, calibration, bias-variance, hyperparameter tuning | 2 — Classical ML | Intermediate |
| 10 | [Neural Network Fundamentals](neural_network_fundamentals/README.md) | Perceptrons, activation functions, backpropagation, weight initialization, batch norm | 3 — Deep Learning Foundations | Beginner |
| 11 | [Convolutional Neural Networks](convolutional_neural_networks/README.md) | Convolution, pooling, ResNet, EfficientNet, depthwise separable, feature maps | 3 — Deep Learning Foundations | Intermediate |
| 12 | [Recurrent Neural Networks](recurrent_neural_networks/README.md) | RNN, LSTM, GRU, vanishing gradients, sequence modeling, CTC loss | 3 — Deep Learning Foundations | Intermediate |
| 13 | [Training Deep Networks](training_deep_networks/README.md) | Regularization, dropout, gradient clipping, mixed precision, distributed training basics | 3 — Deep Learning Foundations | Intermediate |
| 14 | [Generative Models](generative_models/README.md) | VAEs, GANs, normalizing flows, diffusion models, mode collapse, FID score | 3 — Deep Learning Foundations | Advanced |
| 15 | [Computer Vision](computer_vision/README.md) | Object detection, segmentation, vision transformers, self-supervised vision | 4 — Domain Specializations | Intermediate |
| 16 | [Natural Language Processing](natural_language_processing/README.md) | Text preprocessing, word2vec, GloVe, sentiment analysis, NER, classical NLP pipelines — 11 sub-files: BERT, attention/seq2seq, retrieval, evaluation, tokenization, word embeddings, sequence labeling/CRF, text classification, topic modeling, language modeling, syntactic parsing | 4 — Domain Specializations | Intermediate |
| 17 | [Recommender Systems](recommender_systems/README.md) | Collaborative filtering, content-based, matrix factorization, deep recommenders, ranking | 4 — Domain Specializations | Intermediate |
| 18 | [Time Series Forecasting](time_series_forecasting/README.md) | ARIMA, Prophet, temporal CNNs, LSTMs for time series, anomaly detection in streams | 4 — Domain Specializations | Intermediate |
| 19 | [Reinforcement Learning](reinforcement_learning/README.md) | MDP, Q-learning, policy gradients, PPO, actor-critic, reward shaping, exploration | 4 — Domain Specializations | Advanced |
| 20 | [ML System Design](ml_system_design/README.md) | Design framework, feature stores, training pipelines, A/B testing, latency optimization | 5 — ML Systems & Infrastructure | Advanced |
| 21 | [Data Pipelines and Processing](data_pipelines_and_processing/README.md) | Spark, Flink, Kafka, feature computation, data validation, schema evolution | 5 — ML Systems & Infrastructure | Intermediate |
| 22 | [Distributed Training](distributed_training/README.md) | Data parallelism, model parallelism, AllReduce, Horovod, PyTorch DDP, FSDP | 5 — ML Systems & Infrastructure | Advanced |
| 23 | [Experiment Tracking and Versioning](experiment_tracking_and_versioning/README.md) | MLflow, DVC, model registry, artifact lineage, reproducibility, metadata stores | 5 — ML Systems & Infrastructure | Intermediate |
| 24 | [GPU and Hardware Optimization](gpu_and_hardware_optimization/README.md) | CUDA, memory bandwidth, tensor cores, multi-GPU topology, TPUs, cost modeling | 5 — ML Systems & Infrastructure | Advanced |
| 25 | [Model Serving and Inference](model_serving_and_inference/README.md) | REST vs gRPC, batching, TorchServe, Triton, latency SLOs, shadow mode | 6 — Production ML | Intermediate |
| 26 | [Model Compression and Efficiency](model_compression_and_efficiency/README.md) | Pruning, quantization (INT8/FP16), knowledge distillation, ONNX export, TensorRT | 6 — Production ML | Advanced |
| 27 | [Monitoring and Drift Detection](monitoring_and_drift_detection/README.md) | Data drift, concept drift, PSI, KL divergence monitoring, alerting, retraining triggers | 6 — Production ML | Intermediate |
| 28 | [MLOps and CI/CD](mlops_and_ci_cd/README.md) | Model pipelines, automated retraining, canary deployments, rollback, governance | 6 — Production ML | Intermediate |
| 29 | [Graph Neural Networks](graph_neural_networks/README.md) | GCN, GAT, GraphSAGE, message passing, link prediction, node classification | 7 — Advanced Topics | Advanced |
| 30 | [Self-Supervised and Contrastive Learning](self_supervised_and_contrastive_learning/README.md) | SimCLR, MoCo, BYOL, masked autoencoders, representation learning, downstream tasks | 7 — Advanced Topics | Advanced |
| 31 | [Causal Inference and ML](causal_inference_and_ml/README.md) | Potential outcomes, DAGs, propensity scores, uplift modeling, A/B test analysis | 7 — Advanced Topics | Advanced |
| 32 | [ML Interview Patterns](ml_interview_patterns/README.md) | End-to-end design framework, common question archetypes, anti-patterns, cheat sheets | 8 — Interview Consolidation | Intermediate |
| 33 | [Model Selection and Algorithm Choice](model_selection_and_algorithm_choice/README.md) | Algorithm decision matrix, problem-type → algorithm mapping, data-size vs complexity regimes, constraint-driven elimination (latency, interpretability, regulatory), baseline discipline | Cross-Cutting Reference | Advanced |
| 34 | [Active Learning and Weak Supervision](active_learning_and_weak_supervision/README.md) | Uncertainty/diversity sampling, query-by-committee, BALD, Snorkel labeling functions, label model, weak supervision, data-centric AI | 5 — ML Systems & Infrastructure | Advanced |
| 35 | [Adversarial ML and Robustness](adversarial_ml_and_robustness/README.md) | Evasion (FGSM/PGD/C&W), data poisoning, backdoors, model extraction, membership inference, adversarial training, randomized smoothing | 7 — Advanced Topics | Advanced |
| 36 | [Uncertainty Quantification and Conformal Prediction](uncertainty_quantification_and_conformal_prediction/README.md) | Aleatoric vs epistemic uncertainty, MC dropout, deep ensembles, calibration (ECE), conformal prediction sets/intervals, CQR | 7 — Advanced Topics | Advanced |
| 37 | [Interpretability and Explainability](interpretability_and_explainability/README.md) | SHAP (KernelSHAP/TreeSHAP), LIME, integrated gradients, Grad-CAM, permutation importance, PDP/ICE, counterfactuals | 7 — Advanced Topics | Advanced |
| 38 | [Privacy-Preserving ML](privacy_preserving_ml/README.md) | Differential privacy, DP-SGD, federated learning (FedAvg), secure aggregation, PATE, membership inference | 7 — Advanced Topics | Advanced |
| 39 | [Multi-Task and Multi-Objective Learning](multi_task_and_multi_objective_learning/README.md) | Shared-bottom, MMoE, PLE, uncertainty weighting, PCGrad, Pareto optimization, multi-objective ranking | 4 — Domain Specializations | Advanced |
| 40 | [Anomaly Detection](anomaly_detection/README.md) | Isolation Forest, One-Class SVM, LOF, autoencoders, EVT thresholds, streaming detection, PR-AUC evaluation | 4 — Domain Specializations | Intermediate |
| 41 | [Imbalanced Data and Leakage Traps](imbalanced_data_and_leakage_traps/README.md) | Class imbalance (SMOTE, class weights, focal loss), PR-AUC vs ROC-AUC, threshold moving; data leakage (target/temporal/group), leaky CV, fit-inside-fold discipline | 2 — Classical ML | Intermediate |
| 42 | [Information Retrieval and Search](information_retrieval_and_search/README.md) | Inverted index, BM25, dense/hybrid retrieval (HNSW, RRF), cross-encoder reranking, learning-to-rank (LambdaMART), NDCG/MRR/MAP | 4 — Domain Specializations | Advanced |
| 43 | [Speech and Audio ML](speech_and_audio_ml/README.md) | Spectrograms/MFCC, ASR (CTC, RNN-T, Whisper, wav2vec2), speaker ID/diarization, TTS, WER, SpecAugment | 4 — Domain Specializations | Advanced |
| 44 | [Meta-Learning and Few-Shot](meta_learning_and_few_shot/README.md) | N-way K-shot, Prototypical Networks, MAML/Reptile, episodic training, metric vs optimization-based | 7 — Advanced Topics | Advanced |
| 45 | [Fairness and Responsible AI](fairness_and_responsible_ai/README.md) | Fairness definitions + impossibility, disparate impact, pre/in/post-processing mitigation, proxies, model cards, EU AI Act | 7 — Advanced Topics | Advanced |

---

## Sub-Files Index

Thirty-six deep-dive sub-files provide topic-level detail beyond what a single README can cover. Each follows the full 14-section template with a minimum of 15 interview Q&As.

### supervised_learning/ (4 sub-files)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [linear_models.md](supervised_learning/linear_models.md) | Linear and Logistic Regression | OLS, ridge, lasso, elastic net, logistic loss, sigmoid, softmax, multicollinearity |
| [support_vector_machines.md](supervised_learning/support_vector_machines.md) | SVMs and Kernel Methods | Margin maximization, kernel trick, RBF, polynomial, SVC vs SVR, soft margin |
| [decision_trees.md](supervised_learning/decision_trees.md) | Decision Trees | Gini impurity, information gain, pruning, CART, ID3, C4.5, overfitting |
| [bayesian_methods.md](supervised_learning/bayesian_methods.md) | Bayesian Methods | Naive Bayes, Bayesian networks, MAP estimation, conjugate priors, Gaussian processes |

### ensemble_methods/ (4 sub-files)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [random_forests.md](ensemble_methods/random_forests.md) | Random Forests | Bagging, feature subsampling, out-of-bag error, feature importance, bias-variance |
| [gradient_boosting.md](ensemble_methods/gradient_boosting.md) | Gradient Boosting Machines | Additive trees, MART, shrinkage, tree depth, loss functions, pseudo-residuals |
| [xgboost_lightgbm.md](ensemble_methods/xgboost_lightgbm.md) | XGBoost and LightGBM | Second-order gradients, GOSS, EFB, histogram-based splits, GPU training, hyperparameters |
| [stacking_and_blending.md](ensemble_methods/stacking_and_blending.md) | Stacking and Blending | Meta-learners, cross-val stacking, blending, model diversity, leakage prevention |

### computer_vision/ (4 sub-files)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [object_detection.md](computer_vision/object_detection.md) | Object Detection | YOLO, Faster R-CNN, SSD, anchor boxes, NMS, mAP, two-stage vs one-stage |
| [image_segmentation.md](computer_vision/image_segmentation.md) | Image Segmentation | Semantic vs instance vs panoptic, U-Net, Mask R-CNN, DeepLab, SAM |
| [vision_transformers.md](computer_vision/vision_transformers.md) | Vision Transformers | ViT, DeiT, Swin Transformer, patch embeddings, positional encodings, attention maps |
| [self_supervised_vision.md](computer_vision/self_supervised_vision.md) | Self-Supervised Vision | MAE, DINO, SimCLR for vision, contrastive pretraining, linear probing, fine-tuning |

### recommender_systems/ (5 sub-files)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [collaborative_filtering.md](recommender_systems/collaborative_filtering.md) | Collaborative Filtering | User-based, item-based, matrix factorization, SVD++, ALS, implicit feedback |
| [deep_learning_recommenders.md](recommender_systems/deep_learning_recommenders.md) | Deep Learning Recommenders | Wide & Deep, DeepFM, DLRM, two-tower models, embedding tables, feature crosses |
| [retrieval_and_ranking.md](recommender_systems/retrieval_and_ranking.md) | Retrieval and Ranking | ANN retrieval, FAISS, ScaNN, pointwise/pairwise/listwise ranking, LambdaRank, LambdaMART |
| [content_and_hybrid.md](recommender_systems/content_and_hybrid.md) | Content-Based and Hybrid | TF-IDF features, item embeddings, hybrid architectures, cold-start, exploration-exploitation |
| [online_learning_and_bandits.md](recommender_systems/online_learning_and_bandits.md) | Online Learning and Bandits | Multi-armed bandits, epsilon-greedy, UCB, Thompson sampling, contextual bandits, LinUCB |

### natural_language_processing/ (11 sub-files)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [bert_and_pretrained_models.md](natural_language_processing/bert_and_pretrained_models.md) | BERT and Pre-trained Models | MLM, NSP, WordPiece, fine-tuning, RoBERTa, DeBERTa, ALBERT, DistilBERT, ModernBERT |
| [attention_and_seq2seq.md](natural_language_processing/attention_and_seq2seq.md) | Attention and Seq2Seq | Bahdanau, Luong attention, encoder-decoder transformers, beam search, nucleus sampling |
| [text_representation_and_retrieval.md](natural_language_processing/text_representation_and_retrieval.md) | Text Representation and Retrieval | BM25 derivation, inverted index, Sentence-BERT, FAISS, ColBERT, RRF hybrid search |
| [nlp_evaluation_and_metrics.md](natural_language_processing/nlp_evaluation_and_metrics.md) | NLP Evaluation and Metrics | BLEU, ROUGE, METEOR, BERTScore, entity-level F1, ECE, text augmentation |
| [tokenization_deep_dive.md](natural_language_processing/tokenization_deep_dive.md) | Tokenization Deep Dive | BPE, WordPiece, Unigram LM, SentencePiece, byte-level BPE, vocabulary sizing, fertility, train/inference parity |
| [word_embeddings.md](natural_language_processing/word_embeddings.md) | Word Embeddings | word2vec (skip-gram/CBOW/negative sampling), GloVe, fastText subword, analogies, embedding bias |
| [sequence_labeling_and_crf.md](natural_language_processing/sequence_labeling_and_crf.md) | Sequence Labeling and CRFs | HMM, MEMM (label bias), linear-chain CRF, Viterbi, forward-backward, BiLSTM-CRF, BIO tagging |
| [text_classification.md](natural_language_processing/text_classification.md) | Text Classification | Naive Bayes, logistic regression, linear SVM, fastText, TextCNN, multi-label, class imbalance |
| [topic_modeling.md](natural_language_processing/topic_modeling.md) | Topic Modeling | LSA, pLSA, LDA (Gibbs/variational), NMF, coherence metrics, BERTopic |
| [language_modeling.md](natural_language_processing/language_modeling.md) | Language Modeling | n-gram LMs, Kneser-Ney smoothing, backoff/interpolation, perplexity, neural LMs, transformer bridge |
| [syntactic_parsing.md](natural_language_processing/syntactic_parsing.md) | Syntactic Parsing | CFG/PCFG, CKY, dependency parsing (transition- and graph-based), Eisner, UAS/LAS |

### ml_system_design/ (5 sub-files)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [design_framework.md](ml_system_design/design_framework.md) | ML Design Framework | Problem framing, metric selection, data strategy, training/serving split, iterative rollout |
| [feature_store_design.md](ml_system_design/feature_store_design.md) | Feature Store Design | Online vs offline stores, point-in-time correctness, Feast, Tecton, Hopsworks, freshness SLOs |
| [training_pipeline_design.md](ml_system_design/training_pipeline_design.md) | Training Pipeline Design | DAG orchestration, Airflow, Kubeflow, data versioning, reproducibility, resource scheduling |
| [ab_testing_for_ml.md](ml_system_design/ab_testing_for_ml.md) | A/B Testing for ML | Treatment/control design, minimum detectable effect, p-values, Bonferroni, interleaving, switchback |
| [latency_and_throughput_optimization.md](ml_system_design/latency_and_throughput_optimization.md) | Latency and Throughput Optimization | Batching strategies, caching predictions, model cascades, early exit, p99 vs p50, SLO budgeting |

### unsupervised_learning/ (1 sub-file)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [gaussian_mixtures_and_em.md](unsupervised_learning/gaussian_mixtures_and_em.md) | Gaussian Mixtures and EM | GMM latent-variable model, EM derivation (ELBO/Jensen), responsibilities, covariance types, BIC/AIC, k-means as hard-EM limit |

### model_evaluation_and_selection/ (1 sub-file)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [automl_and_nas.md](model_evaluation_and_selection/automl_and_nas.md) | AutoML and NAS | AutoML frameworks, multi-fidelity HPO (Successive Halving/ASHA/Hyperband/BOHB), NAS (DARTS/ENAS/one-shot), hardware-aware search |

### active_learning_and_weak_supervision/ (1 sub-file)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [semi_supervised_learning.md](active_learning_and_weak_supervision/semi_supervised_learning.md) | Semi-Supervised Learning | SSL assumptions, pseudo-labeling, consistency regularization, FixMatch/MixMatch, label propagation, co-training, UDA |

### case_studies/cross_cutting/ (5 shared-primitive files)

Infrastructure patterns referenced by multiple case studies. Read just-in-time when a case study references the topic.

| File | Topic | Key Concepts | Referenced by |
|------|-------|-------------|--------------|
| [feature_store_and_point_in_time_correctness.md](case_studies/cross_cutting/feature_store_and_point_in_time_correctness.md) | Feature Store and PIT Correctness | Online/offline stores, PIT joins, training-serving skew detection, Feast, PSI/KS skew alerts | Churn, Credit Risk, ETA, Marketplace, QA |
| [model_calibration_and_thresholding.md](case_studies/cross_cutting/model_calibration_and_thresholding.md) | Model Calibration and Thresholding | Platt scaling, isotonic regression, ECE, reliability diagrams, cost-sensitive threshold optimization | Churn, Credit Risk, Fraud, Ads, NER, QA |
| [responsible_ai_fairness_and_explainability.md](case_studies/cross_cutting/responsible_ai_fairness_and_explainability.md) | Responsible AI, Fairness, and Explainability | Demographic parity, equalized odds, SHAP TreeExplainer, LIME, DiCE counterfactuals, GDPR Art. 22 | Credit Risk, Churn, Marketplace |
| [experimentation_and_online_evaluation.md](case_studies/cross_cutting/experimentation_and_online_evaluation.md) | Experimentation and Online Evaluation | OEC, guardrail/counter-metrics, CUPED, sequential testing (mSPRT), SRM detection, switchback | Churn, ETA, Marketplace, QA |
| [drift_monitoring_and_retraining.md](case_studies/cross_cutting/drift_monitoring_and_retraining.md) | Drift Monitoring and Retraining | Data/concept drift, PSI thresholds, champion/challenger, label latency, retraining triggers | Fraud, Churn, ETA, Marketplace, Semantic Search, NER, QA |

See [`case_studies/cross_cutting/README.md`](case_studies/cross_cutting/README.md) for the full index.

---

## Case Studies (22)

All case studies are in `case_studies/`. Studies #1–10 use the legacy 12-section template. Studies #11–22 use the 11-section principal template (same format as LLM case studies). See [`case_studies/README.md`](case_studies/README.md) for the full learning-path index with dependency map and interview prep shortcuts.

| # | Case Study | Core ML Concepts | Template | Link |
|---|-----------|-----------------|----------|------|
| 1 | Design a Recommendation Engine | Two-tower retrieval, ANN, ranking, A/B testing, feature store | Legacy | [design_recommendation_engine](case_studies/design_recommendation_engine.md) |
| 2 | Design a Fraud Detection System | Supervised learning, class imbalance, real-time inference, concept drift, graph features | Legacy | [design_fraud_detection](case_studies/design_fraud_detection.md) |
| 3 | Design a Search Ranking System | Learning to rank, query understanding, feature engineering, online evaluation, BM25 + ML blend | Legacy | [design_search_ranking](case_studies/design_search_ranking.md) |
| 4 | Design an Image Classification Pipeline | CNN, transfer learning, data augmentation, distributed training, model registry, serving | Legacy | [design_image_classification_pipeline](case_studies/design_image_classification_pipeline.md) |
| 5 | Design an Ads Click Prediction System | Logistic regression, GBDT, DLRM, sparse embeddings, calibration, low-latency serving | Legacy | [design_ads_click_prediction](case_studies/design_ads_click_prediction.md) |
| 6 | Design an Anomaly Detection System | Unsupervised baselines, isolation forest, autoencoders, streaming detection, alerting | Legacy | [design_anomaly_detection](case_studies/design_anomaly_detection.md) |
| 7 | Design a Demand Forecasting System | Time series, ARIMA, Prophet, LightGBM, hierarchical forecasting, uncertainty quantification | Legacy | [design_demand_forecasting](case_studies/design_demand_forecasting.md) |
| 8 | Design a Content Feed Ranking System | Engagement prediction, multi-objective ranking, explore-exploit, position bias correction | Legacy | [design_content_feed_ranking](case_studies/design_content_feed_ranking.md) |
| 9 | Design Autonomous Driving Perception | Multi-sensor fusion, object detection, BEV representation, safety constraints, latency budget | Legacy | [design_autonomous_driving_perception](case_studies/design_autonomous_driving_perception.md) |
| 10 | Design an ML Platform | Feature store, training orchestration, model registry, serving infrastructure, experiment tracking | Legacy | [design_ml_platform](case_studies/design_ml_platform.md) |
| 11 | Design a Churn Prediction System | Temporal CV, GBDT vs survival vs uplift, T-learner, calibration for budgeting, SHAP adverse-action | Principal | [design_churn_prediction](case_studies/design_churn_prediction.md) |
| 12 | Design a Credit Risk Scoring System | WOE/scorecard, monotonic constraints, reject inference, ECOA/FCRA adverse-action, fairness audit | Principal | [design_credit_risk_scoring](case_studies/design_credit_risk_scoring.md) |
| 13 | Design an ETA Prediction System | Quantile regression, cyclic geo features, real-time traffic pipeline, p90 coverage SLO | Principal | [design_eta_prediction](case_studies/design_eta_prediction.md) |
| 14 | Design a Marketplace Matching System | Demand/supply forecasting, LambdaRank scoring, Hungarian assignment, switchback experiments | Principal | [design_marketplace_matching](case_studies/design_marketplace_matching.md) |
| 15 | Design a Customer LTV Prediction System | BG/NBD vs LightGBM Cox, censored survival labels, cohort CV, P25/P75 intervals, bid optimization | Principal | [design_customer_ltv_prediction](case_studies/design_customer_ltv_prediction.md) |
| 16 | Design a Multi-Touch Attribution System | Markov removal effects, Shapley Monte Carlo, SUTVA violations, geo-holdout incrementality | Principal | [design_multi_touch_attribution](case_studies/design_multi_touch_attribution.md) |
| 17 | Design a Dynamic Pricing System | Demand elasticity estimation, constrained optimizer, contextual bandits, price war prevention | Principal | [design_dynamic_pricing](case_studies/design_dynamic_pricing.md) |
| 18 | Design an NLP Classification Pipeline | TF-IDF+LR → DistilBERT cascade, active learning, knowledge distillation, class imbalance | Principal | [design_nlp_classification_pipeline](case_studies/design_nlp_classification_pipeline.md) |
| 19 | Design a Real-Time Personalization System | Session encoder (GRU), two-tower + FAISS at 50k req/s, exploration/exploitation, cold-start | Principal | [design_real_time_personalization](case_studies/design_real_time_personalization.md) |
| 20 | Design a Semantic Search Engine | Bi-encoder (SBERT), FAISS IVF, RRF hybrid merge, cross-encoder reranking, hard negative mining, Matryoshka | Principal | [design_semantic_search_engine](case_studies/design_semantic_search_engine.md) |
| 21 | Design a Named Entity Recognition Pipeline | BERT-CRF, BIO tagging, span extraction, subword alignment, domain fine-tuning, active learning | Principal | [design_ner_pipeline](case_studies/design_ner_pipeline.md) |
| 22 | Design a Question Answering System | DPR dual-encoder, BERT-large extractive reader, SQuAD 2.0 null-score, multi-hop retrieval, hybrid RRF | Principal | [design_question_answering_system](case_studies/design_question_answering_system.md) |

---

## Recommended Learning Order

```
Phase 1 — Mathematical Foundations
  linear_algebra_and_calculus
  probability_and_statistics
  optimization_theory
  information_theory
        |
        v
Phase 2 — Classical ML
  supervised_learning  -->  [sub-files: linear_models, svms, decision_trees, bayesian_methods]
  ensemble_methods     -->  [sub-files: random_forests, gradient_boosting, xgboost_lightgbm, stacking]
  unsupervised_learning  -->  [sub-file: gaussian_mixtures_and_em]
  feature_engineering
  model_evaluation_and_selection  -->  [sub-file: automl_and_nas]
  imbalanced_data_and_leakage_traps
        |
        v
Phase 3 — Deep Learning Foundations
  neural_network_fundamentals
  convolutional_neural_networks
  recurrent_neural_networks
  training_deep_networks
  generative_models
        |
        v
Phase 4 — Domain Specializations
  computer_vision      -->  [sub-files: object_detection, image_segmentation, vision_transformers, self_supervised_vision]
  natural_language_processing  -->  [sub-files: bert_and_pretrained_models, attention_and_seq2seq,
                               text_representation_and_retrieval, nlp_evaluation_and_metrics,
                               tokenization_deep_dive, word_embeddings, sequence_labeling_and_crf,
                               text_classification, topic_modeling, language_modeling, syntactic_parsing]
  recommender_systems  -->  [sub-files: collaborative_filtering, deep_learning_recommenders,
                             retrieval_and_ranking, content_and_hybrid, online_learning_and_bandits]
  multi_task_and_multi_objective_learning
  anomaly_detection
  time_series_forecasting
  reinforcement_learning
        |
        v
Phase 5 — ML Systems & Infrastructure
  ml_system_design     -->  [sub-files: design_framework, feature_store_design, training_pipeline_design,
                             ab_testing_for_ml, latency_and_throughput_optimization]
  data_pipelines_and_processing
  distributed_training
  experiment_tracking_and_versioning
  gpu_and_hardware_optimization
  active_learning_and_weak_supervision  -->  [sub-file: semi_supervised_learning]
        |
        v
Phase 6 — Production ML
  model_serving_and_inference
  model_compression_and_efficiency
  monitoring_and_drift_detection
  mlops_and_ci_cd
        |
        v
Phase 7 — Advanced Topics
  graph_neural_networks
  self_supervised_and_contrastive_learning
  causal_inference_and_ml
  adversarial_ml_and_robustness
  privacy_preserving_ml
  interpretability_and_explainability
  uncertainty_quantification_and_conformal_prediction
        |
        v
Phase 8 — Interview Consolidation
  ml_interview_patterns

[Cross-Cutting Reference — consult any phase]
  model_selection_and_algorithm_choice  ← "which algorithm, when & why"
  case_studies/cross_cutting/           ← feature store, calibration, fairness,
                                           experimentation, drift (read just-in-time)
```

For interview preparation specifically, the curated subset in the next section is what actually gets probed.

---

## Learning Paths

This section is exhaustive by design — 45 modules from the math spine to production ML and the research frontier. That is the right depth for a reference and the wrong shape for someone two weeks from an interview. So there are **two ways through it**; the browser learning game's **Study** view surfaces both as a **Full / Interview** toggle (Full is the default).

### Full Path (45 modules)

The complete curriculum in the order above — see [Recommended Learning Order](#recommended-learning-order). Use it for genuine mastery: the math foundations, every domain (CV, time series, RL, GNNs), the advanced-research row (causal, SSL, privacy, adversarial, conformal), and every infrastructure deep-dive. Nothing is dropped.

### Interview-Specific Path (25 modules)

A ruthless cut to what a **senior ML / MLE interview** actually probes, anchored on the phases this section already flags as highest-yield (classical ML, ML system design, production ML, interview consolidation). Same learning order, ~40% fewer modules. Each group below says why it earns interview time.

| Phase | Modules | Why it's tested |
|-------|---------|-----------------|
| Foundations | [Probability & Statistics](probability_and_statistics/README.md), [Optimization Theory](optimization_theory/README.md), [Information Theory](information_theory/README.md) | Bias-variance, MLE/MAP, "derive gradient descent", cross-entropy/KL — the math you actually reason about aloud |
| Classical ML | [Supervised Learning](supervised_learning/README.md), [Ensemble Methods](ensemble_methods/README.md), [Unsupervised Learning](unsupervised_learning/README.md), [Feature Engineering](feature_engineering/README.md), [Model Evaluation & Selection](model_evaluation_and_selection/README.md), [Imbalanced Data & Leakage Traps](imbalanced_data_and_leakage_traps/README.md) | XGBoost, regularization, PCA/k-means, ROC-vs-PR, and the leakage/imbalance gotchas in almost every screen |
| Deep Learning | [Neural Network Fundamentals](neural_network_fundamentals/README.md), [Convolutional Neural Networks](convolutional_neural_networks/README.md), [Recurrent Neural Networks](recurrent_neural_networks/README.md), [Training Deep Networks](training_deep_networks/README.md) | Backprop by hand, conv arithmetic, LSTM gating, dropout/batch-norm/vanishing-gradient fixes |
| Applied | [Natural Language Processing](natural_language_processing/README.md), [Recommender Systems](recommender_systems/README.md) | Classical NLP + embeddings and recsys/ranking — the two most common "design an ML feature" prompts |
| ML Systems | [ML System Design](ml_system_design/README.md), [Data Pipelines & Processing](data_pipelines_and_processing/README.md), [Experiment Tracking & Versioning](experiment_tracking_and_versioning/README.md) | The dominant senior format: the 6-step design framework, feature/training pipelines, reproducibility |
| Production | [Model Serving & Inference](model_serving_and_inference/README.md), [Model Compression & Efficiency](model_compression_and_efficiency/README.md), [Monitoring & Drift Detection](monitoring_and_drift_detection/README.md), [MLOps & CI/CD](mlops_and_ci_cd/README.md) | Latency/batching, quantization/distillation, drift + retraining triggers — what separates senior from mid |
| Explainability | [Interpretability & Explainability](interpretability_and_explainability/README.md) | SHAP/LIME and "why did the model predict this" — near-universal at senior and in regulated domains |
| Consolidation | [ML Interview Patterns](ml_interview_patterns/README.md), [Model Selection & Algorithm Choice](model_selection_and_algorithm_choice/README.md) | The design framework, anti-patterns, and the "which algorithm, when & why" matrix — final review |

**Deliberately deferred to the Full Path** (valuable, lower interview yield): linear algebra & calculus (a prerequisite, rarely probed directly), generative models, computer vision, multi-task learning, time series forecasting, anomaly detection, reinforcement learning, distributed training, GPU/hardware optimization, active learning, graph neural networks, self-supervised/contrastive learning, causal inference, adversarial robustness, privacy-preserving ML, and uncertainty/conformal prediction. A niche flagged in an interview (e.g. "have you worked with GNNs?") is a bonus, not a gate — reach for these once the 25 above are solid.

---

## Knowledge-Question Map

The highest-frequency ML *knowledge* questions mapped to the file that answers them. For *system design* ("design X") questions, use the interview-prep shortcuts in [case_studies/README.md](case_studies/README.md).

| Interview question | Where the answer lives |
|--------------------|------------------------|
| Explain the bias-variance tradeoff and how it guides model choice. | [Model Evaluation & Selection](model_evaluation_and_selection/README.md) |
| Why can 99% accuracy be useless? PR-AUC vs ROC-AUC under imbalance? | [Imbalanced Data & Leakage Traps](imbalanced_data_and_leakage_traps/README.md) |
| What is data leakage and how do you prevent it (target/temporal/group)? | [Imbalanced Data & Leakage Traps](imbalanced_data_and_leakage_traps/README.md) |
| Derive gradient descent. SGD vs Adam vs AdamW — when each? | [Optimization Theory](optimization_theory/README.md) |
| L1 vs L2 regularization — what does each do geometrically? | [Supervised Learning](supervised_learning/README.md) |
| How does gradient boosting work? Bagging vs boosting. | [Ensemble Methods](ensemble_methods/README.md) |
| Walk through backpropagation for a 2-layer network. | [Neural Network Fundamentals](neural_network_fundamentals/README.md) |
| Why convolutions and pooling? Parameter sharing intuition. | [Convolutional Neural Networks](convolutional_neural_networks/README.md) |
| What causes vanishing gradients, and how do LSTM/GRU gates fix it? | [Recurrent Neural Networks](recurrent_neural_networks/README.md) |
| What do dropout and batch norm actually do, and why? | [Training Deep Networks](training_deep_networks/README.md) |
| Define cross-entropy and KL divergence; how are they related? | [Information Theory](information_theory/README.md) |
| Precision/recall/F1 — which to optimize for which problem? | [Model Evaluation & Selection](model_evaluation_and_selection/README.md) |
| k-fold vs time-series vs group CV — when does each matter? | [Model Evaluation & Selection](model_evaluation_and_selection/README.md), [Imbalanced Data & Leakage Traps](imbalanced_data_and_leakage_traps/README.md) |
| How does target encoding leak, and how do you do it safely? | [Feature Engineering](feature_engineering/README.md) |
| Collaborative vs content-based filtering; the cold-start problem. | [Recommender Systems](recommender_systems/README.md) |
| Design a retrieval + ranking recommender (two-tower). | [Recommender Systems](recommender_systems/README.md), [ML System Design](ml_system_design/README.md) |
| Walk me through designing an ML system end to end. | [ML System Design](ml_system_design/README.md), [ML Interview Patterns](ml_interview_patterns/README.md) |
| A/B testing for ML — offline vs online metrics, guardrails. | [ML System Design](ml_system_design/README.md) |
| Data drift vs concept drift — how do you detect each (PSI/KS)? | [Monitoring & Drift Detection](monitoring_and_drift_detection/README.md) |
| Quantization vs pruning vs distillation — tradeoffs. | [Model Compression & Efficiency](model_compression_and_efficiency/README.md) |
| SHAP vs LIME; global vs local explanations. | [Interpretability & Explainability](interpretability_and_explainability/README.md) |
| Which algorithm would you pick for problem X, and why? | [Model Selection & Algorithm Choice](model_selection_and_algorithm_choice/README.md) |

---

## Study Plan

A 6-week plan over the Interview-Specific Path. Each week pairs modules with one case study to rehearse the "design X" format.

| Week | Focus | Modules | Case study |
|------|-------|---------|------------|
| 1 | Math + Classical ML I | Probability & Statistics, Optimization Theory, Information Theory, Supervised Learning | skim [Churn Prediction](case_studies/design_churn_prediction.md) |
| 2 | Classical ML II + traps | Ensemble Methods, Unsupervised Learning, Feature Engineering, Model Evaluation & Selection, Imbalanced Data & Leakage Traps | [Fraud Detection](case_studies/design_fraud_detection.md) (imbalance + leakage) |
| 3 | Deep Learning | Neural Network Fundamentals, Convolutional Neural Networks, Recurrent Neural Networks, Training Deep Networks | [Image Classification Pipeline](case_studies/design_image_classification_pipeline.md) |
| 4 | Applied ML | Natural Language Processing, Recommender Systems | [Recommendation Engine](case_studies/design_recommendation_engine.md), [Search Ranking](case_studies/design_search_ranking.md) |
| 5 | ML Systems + Production | ML System Design, Data Pipelines & Processing, Experiment Tracking & Versioning, Model Serving & Inference, Model Compression & Efficiency, Monitoring & Drift Detection, MLOps & CI/CD | [ML Platform](case_studies/design_ml_platform.md) |
| 6 | Explainability + drills | Interpretability & Explainability, ML Interview Patterns, Model Selection & Algorithm Choice | 3-4 principal case studies via the [interview shortcuts](case_studies/README.md) |

---

## Non-Overlap with LLM Section

The LLM section (`llm/`) covers a separate but complementary body of knowledge. The table below clarifies which section owns each overlapping area.

| Topic Area | ML Section Coverage | LLM Section Coverage |
|-----------|--------------------|--------------------|
| Neural networks | Fundamentals, backprop, CNNs, RNNs, training dynamics | Transformers, self-attention, scaling laws (see `llm/foundations_and_architecture/`) |
| Embeddings | Word2Vec, GloVe, learned embeddings for tabular/vision | Sentence embeddings, contextual embeddings, MTEB benchmarks (see `llm/embeddings_and_similarity_search/`) |
| Quantization | INT8/FP16/INT4 for classical and vision models, ONNX | GPTQ, AWQ, GGUF, LLM-specific quantization (see `llm/optimization_and_quantization/`) |
| Distributed training | Data parallelism, AllReduce, PyTorch DDP, Horovod | Tensor/pipeline parallelism, ZeRO, FSDP at LLM scale (see `llm/training_infrastructure/`) |
| Model serving | TorchServe, Triton, REST/gRPC, latency budgets | vLLM, TensorRT-LLM, PagedAttention, KV cache (see `llm/inference_engines/`) |
| Monitoring | Data drift, PSI, concept drift, model degradation | Hallucination monitoring, output quality, LLM-specific observability (see `llm/llm_observability_and_monitoring/`) |
| Generative models | VAEs, GANs, diffusion model foundations | Stable Diffusion architecture, multimodal LLMs, image generation at scale (see `llm/multimodal_models/`) |
| Recommender systems | Full recommender system design (this section) | LLM-augmented recommendations, conversational recommenders — not covered separately in LLM section |
| NLP | Classical NLP pipelines, feature-based text ML | Modern LLM-based NLP, fine-tuning, RAG, agents (LLM section comprehensively) |
| Fine-tuning | Transfer learning principles, domain adaptation basics | LoRA, QLoRA, RLHF, instruction tuning, DPO (see `llm/fine_tuning/`, `llm/alignment_and_rlhf/`) |
| Reinforcement learning | RL fundamentals (MDP, Q-learning, PPO, policy gradients) | RLHF specifically for LLM alignment (see `llm/alignment_and_rlhf/`) |
| A/B testing | Statistical design, significance, interleaving for ML systems | Not covered separately in LLM section |
| Causal inference | Uplift modeling, propensity scores, DAGs | Not covered in LLM section |

---

## Cross-References

Key links to related LLM section modules where a concept is covered in greater depth for the LLM context:

- Transformer architecture and self-attention: [`llm/foundations_and_architecture/README.md`](../llm/foundations_and_architecture/README.md)
- Tokenization and vocabulary design: [`llm/tokenization_and_embeddings/README.md`](../llm/tokenization_and_embeddings/README.md)
- Sentence embeddings and ANN search: [`llm/embeddings_and_similarity_search/README.md`](../llm/embeddings_and_similarity_search/README.md)
- Fine-tuning (LoRA, QLoRA, PEFT): [`llm/fine_tuning/README.md`](../llm/fine_tuning/README.md)
- RLHF and alignment: [`llm/alignment_and_rlhf/README.md`](../llm/alignment_and_rlhf/README.md)
- RAG fundamentals: [`llm/rag_fundamentals/README.md`](../llm/rag_fundamentals/README.md)
- LLM inference engines: [`llm/inference_engines/README.md`](../llm/inference_engines/README.md)
- LLM-scale distributed training: [`llm/training_infrastructure/README.md`](../llm/training_infrastructure/README.md)
- LLM quantization and optimization: [`llm/optimization_and_quantization/README.md`](../llm/optimization_and_quantization/README.md)
- LLM observability and monitoring: [`llm/llm_observability_and_monitoring/README.md`](../llm/llm_observability_and_monitoring/README.md)
- Multimodal models and diffusion: [`llm/multimodal_models/README.md`](../llm/multimodal_models/README.md)
- Evaluation and benchmarks: [`llm/evaluation_and_benchmarks/README.md`](../llm/evaluation_and_benchmarks/README.md)
