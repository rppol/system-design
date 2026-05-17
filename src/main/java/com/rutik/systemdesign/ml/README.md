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

Thirty-two modules organized across eight learning phases take you from linear algebra through production MLOps. Five topics contain deep-dive sub-files (22 sub-files total) that follow the same 14-section template used throughout this repository. Ten end-to-end case studies — each covering a real-world ML system — demonstrate how individual concepts combine into full system designs. The section is structured so it can be read sequentially as a learning curriculum or used as a reference when preparing for a specific interview topic.

---

## All Topics (32 Modules)

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
| 16 | [Natural Language Processing](natural_language_processing/README.md) | Text preprocessing, word2vec, GloVe, sentiment analysis, NER, classical NLP pipelines | 4 — Domain Specializations | Intermediate |
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

---

## Sub-Files Index

Twenty-two deep-dive sub-files provide topic-level detail beyond what a single README can cover. Each follows the full 14-section template with a minimum of 15 interview Q&As.

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

### ml_system_design/ (5 sub-files)

| File | Topic | Key Concepts |
|------|-------|-------------|
| [design_framework.md](ml_system_design/design_framework.md) | ML Design Framework | Problem framing, metric selection, data strategy, training/serving split, iterative rollout |
| [feature_store_design.md](ml_system_design/feature_store_design.md) | Feature Store Design | Online vs offline stores, point-in-time correctness, Feast, Tecton, Hopsworks, freshness SLOs |
| [training_pipeline_design.md](ml_system_design/training_pipeline_design.md) | Training Pipeline Design | DAG orchestration, Airflow, Kubeflow, data versioning, reproducibility, resource scheduling |
| [ab_testing_for_ml.md](ml_system_design/ab_testing_for_ml.md) | A/B Testing for ML | Treatment/control design, minimum detectable effect, p-values, Bonferroni, interleaving, switchback |
| [latency_and_throughput_optimization.md](ml_system_design/latency_and_throughput_optimization.md) | Latency and Throughput Optimization | Batching strategies, caching predictions, model cascades, early exit, p99 vs p50, SLO budgeting |

---

## Case Studies (10)

All case studies are in `case_studies/`. Each follows the case study template: Problem Statement, Architecture Overview (ASCII diagram), Key Design Decisions, Implementation, Components Used, Tradeoffs and Alternatives, Interview Discussion Points.

| # | Case Study | Core ML Concepts | Link |
|---|-----------|-----------------|------|
| 1 | Design a Recommendation Engine | Two-tower retrieval, ANN, ranking, A/B testing, feature store | [design_recommendation_engine](case_studies/design_recommendation_engine/README.md) |
| 2 | Design a Fraud Detection System | Supervised learning, class imbalance, real-time inference, concept drift, graph features | [design_fraud_detection](case_studies/design_fraud_detection/README.md) |
| 3 | Design a Search Ranking System | Learning to rank, query understanding, feature engineering, online evaluation, BM25 + ML blend | [design_search_ranking](case_studies/design_search_ranking/README.md) |
| 4 | Design an Image Classification Pipeline | CNN, transfer learning, data augmentation, distributed training, model registry, serving | [design_image_classification_pipeline](case_studies/design_image_classification_pipeline/README.md) |
| 5 | Design an Ads Click Prediction System | Logistic regression, GBDT, DLRM, sparse embeddings, calibration, low-latency serving | [design_ads_click_prediction](case_studies/design_ads_click_prediction/README.md) |
| 6 | Design an Anomaly Detection System | Unsupervised baselines, isolation forest, autoencoders, streaming detection, alerting | [design_anomaly_detection](case_studies/design_anomaly_detection/README.md) |
| 7 | Design a Demand Forecasting System | Time series, ARIMA, Prophet, LightGBM, hierarchical forecasting, uncertainty quantification | [design_demand_forecasting](case_studies/design_demand_forecasting/README.md) |
| 8 | Design a Content Feed Ranking System | Engagement prediction, multi-objective ranking, explore-exploit, position bias correction | [design_content_feed_ranking](case_studies/design_content_feed_ranking/README.md) |
| 9 | Design Autonomous Driving Perception | Multi-sensor fusion, object detection, BEV representation, safety constraints, latency budget | [design_autonomous_driving_perception](case_studies/design_autonomous_driving_perception/README.md) |
| 10 | Design an ML Platform | Feature store, training orchestration, model registry, serving infrastructure, experiment tracking | [design_ml_platform](case_studies/design_ml_platform/README.md) |

---

## Learning Path

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
  unsupervised_learning
  feature_engineering
  model_evaluation_and_selection
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
  natural_language_processing
  recommender_systems  -->  [sub-files: collaborative_filtering, deep_learning_recommenders,
                             retrieval_and_ranking, content_and_hybrid, online_learning_and_bandits]
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
        |
        v
Phase 8 — Interview Consolidation
  ml_interview_patterns
```

For interview preparation specifically, prioritize:
- Phase 2 (classical ML) — appears in almost every ML interview screen
- Phase 5 (ML system design) — the dominant format at senior levels
- Phase 6 (production ML) — distinguishes senior from mid-level candidates
- Phase 8 (interview patterns) — use as a final review and cheat sheet

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
