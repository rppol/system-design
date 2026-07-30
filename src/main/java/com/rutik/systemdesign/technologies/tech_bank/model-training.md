# Model training — technology bank

<!-- tech-bank tier: model-training -->

The 168 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Model training** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### accelerate
**Short:** Hugging Face wrapper that runs the same training loop on CPU/multi-GPU/TPU over DDP, FSDP or DeepSpeed.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @3

### Adan
**Short:** Adaptive Nesterov momentum optimizer (Xie et al. 2022) reporting faster convergence than Adam on vision and NLP.
**Kind:** concept
**Lang:** *
**Roles:** model-training/deep-learning-framework @1

### AdapterHub
**Short:** Hub and library for sharing and composing pretrained adapters (language + task) over a frozen base model.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1

### Alpaca-Cleaned
**Short:** Quality-filtered version of the 52K-example Alpaca instruction dataset, used for supervised fine-tuning.
**Kind:** dataset
**Lang:** *
**Roles:** model-training/fine-tuning-and-peft @1, ml-lifecycle/labeling-and-synthetic-data @2

### Apache Spark MLlib
**Short:** Spark's distributed machine-learning library: pipelines, classical models and ALS recommenders over cluster-scale data.
**Kind:** tech
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1, data-movement/batch-and-distributed-compute @2, applied-ml/recommenders-and-graph-ml @2

### AutoGluon
**Short:** AutoML library that trains and stack-ensembles a portfolio of tabular, text and vision models from presets.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, ml-lifecycle/experiment-tracking-and-tuning @2

### AWS SageMaker Training
**Short:** Managed training jobs on ephemeral AWS clusters, with the SageMaker model-parallel and data-parallel libraries.
**Kind:** tech
**Lang:** *
**Roles:** model-training/distributed-training @1, ml-lifecycle/ml-platform-and-pipelines @2, platform-delivery/cloud-platform-and-cost @3

### Axolotl
**Short:** YAML-configured LLM fine-tuning framework covering continued pretraining, SFT, LoRA and QLoRA.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, model-training/alignment-and-rl @3

### CatBoost
**Short:** Gradient-boosted decision trees with ordered boosting and native categorical handling; strong on tabular data.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### CatBoost 1.2+
**Short:** Gradient-boosted decision trees with ordered boosting, native categorical handling and symmetric (oblivious) trees.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### category_encoders
**Short:** sklearn-compatible categorical encoders (target, CatBoost, James-Stein, hashing, binary) that scikit-learn lacks.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, ml-lifecycle/ml-platform-and-pipelines @3

### CleanRL
**Short:** Single-file, dependency-light reference implementations of RL algorithms, aimed at learning not production.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1

### composer
**Short:** MosaicML's PyTorch training library packaging speed-up recipes, batch-size ramping and run monitoring callbacks.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/distributed-training @2, ml-lifecycle/experiment-tracking-and-tuning @3

### cuML
**Short:** RAPIDS GPU-accelerated scikit-learn-compatible ML library: k-means, PCA, UMAP and forests at 10-100x speedups.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, gpu/gpu-math-libraries @2

### DeepSpeed
**Short:** PyTorch training library whose ZeRO sharding and CPU/NVMe offload fit models too large for one GPU's memory.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/deep-learning-framework @3

### DeepSpeed ZeRO
**Short:** DeepSpeed's ZeRO optimizer sharding states, gradients and parameters across ranks to fit larger training jobs.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/fine-tuning-and-peft @2

### Distillation
**Short:** Training a small student model on a large teacher's outputs to keep most of the quality at a fraction of the cost.
**Kind:** concept
**Lang:** *
**Roles:** model-training/fine-tuning-and-peft @1, inference/quantization-and-compression @2

### DistributedDataParallel
**Short:** PyTorch wrapper that replicates a model per GPU and all-reduces gradients over NCCL each backward pass.
**Kind:** api
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2

### DoRA
**Short:** Weight-decomposed low-rank adaptation: splits LoRA updates into magnitude and direction for better quality per rank.
**Kind:** concept
**Lang:** *
**Roles:** model-training/fine-tuning-and-peft @1

### einops
**Short:** Readable tensor rearrangement: rearrange, reduce and repeat expressions replace error-prone reshape/permute chains.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, runtime-systems/collections-and-algorithms @3

### ElasticNet
**Short:** Linear regression regularized by a mix of L1 and L2 penalties, combining lasso sparsity with ridge stability.
**Kind:** concept
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1

### Flower
**Short:** Framework-agnostic federated learning framework: FedAvg/FedProx/FedAdam strategies with PyTorch, TF or JAX clients.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, security/privacy-and-compliance @2

### FSDP
**Short:** PyTorch-native ZeRO-3 sharding of parameters, gradients and optimizer state across GPUs; FSDP2 shards per parameter.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/deep-learning-framework @3, runtime-systems/memory-processes-and-os @3

### FullyShardedDataParallel
**Short:** PyTorch wrapper that shards parameters, gradients and optimizer state across ranks, the ZeRO-3 equivalent.
**Kind:** api
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2

### GitHub Code
**Short:** Large multi-language public code corpus used for continued pretraining of code-focused models.
**Kind:** dataset
**Lang:** *
**Roles:** model-training/fine-tuning-and-peft @1

### GPT-NeoX
**Short:** EleutherAI's GPU training framework for pretraining large autoregressive models with 3D parallelism.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/fine-tuning-and-peft @2

### GPyTorch
**Short:** GPU-accelerated Gaussian process library on PyTorch, used for calibrated uncertainty and Bayesian optimization.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/interpretability-fairness-and-causal @2, model-training/deep-learning-framework @3

### Gymnasium
**Short:** The standard RL environment API and reference environments (CartPole, Atari, MuJoCo); successor to OpenAI Gym.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1

### HDBSCAN
**Short:** Hierarchical density-based clustering finding variable-density clusters and labelling noise; no eps to tune.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/timeseries-and-anomaly @3

### HelpSteer2
**Short:** NVIDIA's human preference dataset with multi-attribute ratings, used to train reward models for RLHF.
**Kind:** dataset
**Lang:** *
**Roles:** model-training/alignment-and-rl @1, ml-lifecycle/evaluation-and-benchmarks @3

### higher
**Short:** PyTorch library making nn.Modules differentiable through their own optimizer steps, for meta-gradients.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, model-training/deep-learning-framework @2

### Horovod
**Short:** Uber's framework-agnostic ring-all-reduce library for data-parallel training across GPUs and hosts.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2

### Hugging Face PEFT
**Short:** Hugging Face library for parameter-efficient fine-tuning: LoRA, QLoRA, prefix tuning and adapter merging.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1

### Hugging Face Trainer
**Short:** Transformers' training loop handling optimization, evaluation, checkpointing and multi-GPU launch from a config.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/fine-tuning-and-peft @2, model-training/distributed-training @3

### Hugging Face Transformers
**Short:** The standard Python library for loading, fine-tuning and running pretrained transformer models from the HF Hub.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/fine-tuning-and-peft @2, applied-ml/nlp-and-text @2, inference/inference-engine @3, applied-ml/vision-speech-and-multimodal @3

### HuggingFace datasets
**Short:** Library and hub for loading, streaming, mapping and versioning training and evaluation corpora as Arrow tables.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, data-movement/data-quality-and-lineage @2, ml-lifecycle/evaluation-and-benchmarks @3

### HuggingFace PEFT
**Short:** Library for parameter-efficient fine-tuning: LoRA, QLoRA, adapters, prefix and prompt tuning on HF models.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, inference/quantization-and-compression @3

### HuggingFace Trainer
**Short:** The transformers training loop: model, dataset and TrainingArguments in, steps, eval and checkpoints out.
**Kind:** api
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, model-training/deep-learning-framework @2, model-training/distributed-training @3

### HuggingFace transformers config
**Short:** The config.json a checkpoint ships, declaring layer count, hidden size and KV head count used in memory sizing.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, inference/inference-engine @3

### imbalanced-learn
**Short:** scikit-learn-compatible resampling for class imbalance: SMOTE, ADASYN, Tomek links, balanced ensembles.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, ml-lifecycle/labeling-and-synthetic-data @2

### Isaac Lab
**Short:** NVIDIA GPU robot-learning framework on Isaac Sim: thousands of parallel physics environments for RL.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, ml-lifecycle/labeling-and-synthetic-data @2, applied-ml/vision-speech-and-multimodal @3

### JAX
**Short:** Functional array framework with composable grad/vmap/jit transforms, XLA-compiled to GPU and TPU.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, inference/compiler-and-runtime-optimization @2, gpu/kernel-programming @3

### JAX/Flax
**Short:** Google's functional array framework with jit/grad/vmap and XLA compilation, plus Flax layers; TPU-native.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/distributed-training @2, inference/compiler-and-runtime-optimization @3

### Keras
**Short:** High-level neural-network API for building and training models; reference impl of many published architectures.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/recommenders-and-graph-ml @3

### LabelSpreading
**Short:** scikit-learn graph-based semi-supervised learner that propagates a few labels across a kNN or RBF similarity graph.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, ml-lifecycle/labeling-and-synthetic-data @2

### Lasso
**Short:** L1-regularized linear regression that shrinks coefficients to exactly zero, giving built-in feature selection.
**Kind:** concept
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1

### LazyMergeKit
**Short:** Colab notebook wrapper around mergekit that merges several fine-tuned checkpoints from a YAML recipe.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1

### learn2learn
**Short:** PyTorch meta-learning library implementing MAML, ANIL, MetaSGD, Reptile and ProtoNets for few-shot adaptation.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/fine-tuning-and-peft @3

### LibMTL
**Short:** PyTorch multi-task learning library implementing GradNorm, DWA, PCGrad, CAGrad and MGDA loss balancing.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1

### libsvm
**Short:** The C++ support vector machine library underneath scikit-learn's SVC; used directly for custom kernels.
**Kind:** tech
**Lang:** cpp, python
**Roles:** model-training/classical-ml-and-boosting @1

### LightGBM
**Short:** Histogram-based gradient-boosted tree library; leaf-wise growth, native categoricals, lambdarank for ranking.
**Kind:** tech
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/recommenders-and-graph-ml @2, applied-ml/timeseries-and-anomaly @3

### LightGBM 4.0+
**Short:** Histogram-based gradient boosting library: leaf-wise growth, native categoricals, and the fastest CPU training.
**Kind:** tech
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1

### LightGBM MPI
**Short:** LightGBM's MPI build, splitting gradient-boosted tree training across machines by feature or data parallelism.
**Kind:** tech
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1, model-training/distributed-training @2

### LLaMA-Adapter
**Short:** Prefix-style parameter-efficient adaptation for LLaMA, extended to vision-language multimodal tuning.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, applied-ml/vision-speech-and-multimodal @3

### LLaMA-Factory
**Short:** All-in-one YAML/web-UI fine-tuning toolkit covering SFT, LoRA and DPO across many open model families.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, model-training/alignment-and-rl @2

### llm-foundry
**Short:** MosaicML/Databricks training stack for pretraining and finetuning LLMs, with WSD schedules and built-in monitoring.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/fine-tuning-and-peft @2

### LM-Cocktail
**Short:** Weight-merging method that blends a domain fine-tune back with the base model to keep general ability.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, search-retrieval/ann-index-library @3

### mamba-ssm
**Short:** Reference Mamba/Mamba-2 state-space model implementation with CUDA kernels for the selective parallel scan.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, gpu/kernel-programming @2

### Math-Shepherd
**Short:** Process reward model for mathematical reasoning that scores each intermediate step rather than only the final answer.
**Kind:** model
**Lang:** *
**Roles:** model-training/alignment-and-rl @1, ml-lifecycle/evaluation-and-benchmarks @3

### Megatron-LM
**Short:** NVIDIA's large-scale LLM training framework: tensor, pipeline and sequence parallelism for 70B+ models.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2, model-training/deep-learning-framework @3, model-training/fine-tuning-and-peft @3

### mergekit
**Short:** Toolkit for merging model checkpoints: SLERP, TIES, DARE, linear and passthrough recipes without retraining.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, inference/quantization-and-compression @3

### mlxtend EnsembleVoteClassifier
**Short:** mlxtend estimator combining fitted models by hard or soft voting, with optional per-model weights.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### mlxtend StackingClassifier
**Short:** mlxtend stacking ensemble that feeds base-model predictions or probabilities into a trained meta-classifier.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### Model training
**Short:** The training stage of an ML system: a framework, a boosting library, a tuner and a tracker used together.
**Kind:** concept
**Lang:** *
**Roles:** model-training/deep-learning-framework @1, ml-lifecycle/experiment-tracking-and-tuning @3

### MosaicML/LLM Foundry
**Short:** Recipe repo for large-scale LLM pretraining and continued pretraining with FSDP and streaming datasets.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, model-training/distributed-training @2, model-training/deep-learning-framework @3

### MuJoCo
**Short:** Fast rigid-body physics simulator; the standard continuous-control RL benchmark and sim-to-real training environment.
**Kind:** tech
**Lang:** cpp, python
**Roles:** model-training/alignment-and-rl @1, llm-apps/agentic-environments @3, applied-ml/vision-speech-and-multimodal @3

### Nanotron
**Short:** HuggingFace's minimal LLM pre-training framework with clean 3D (data/tensor/pipeline) parallelism.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/fine-tuning-and-peft @2, model-training/deep-learning-framework @3

### NeMo-RL
**Short:** NVIDIA's post-training RL library for GRPO/RLVR-style reinforcement learning on large models across many GPUs.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/distributed-training @2

### NVIDIA DALI
**Short:** NVIDIA's GPU data-loading and augmentation pipeline, staged through pinned buffers to keep the GPU fed.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/vision-speech-and-multimodal @2, gpu/kernel-programming @3

### NVIDIA FLARE
**Short:** NVIDIA's production federated learning orchestration framework for cross-silo training in healthcare and finance.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, security/privacy-and-compliance @2, ml-lifecycle/ml-platform-and-pipelines @3

### OpenDelta
**Short:** Library implementing many delta-tuning / PEFT methods behind one interface over pretrained transformers.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1

### OpenHermes 2.5
**Short:** Public instruction-tuning dataset of over a million curated examples, a common SFT starting point.
**Kind:** dataset
**Lang:** *
**Roles:** model-training/fine-tuning-and-peft @1, ml-lifecycle/labeling-and-synthetic-data @2

### OpenR
**Short:** Open-source LLM reasoning framework: MCTS search, process reward models and RL-based reasoning training.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, llm-apps/agent-framework @3

### OpenRLHF
**Short:** Ray-based large-scale RLHF framework: PPO, GRPO and REINFORCE++ with vLLM-accelerated rollout generation.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/distributed-training @2

### Optax
**Short:** JAX optimizer library built from composable gradient transformations chained into schedules and clipping.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1

### Pearl
**Short:** Meta's production RL agent library with contextual bandits, offline RL and safe-exploration components.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, applied-ml/recommenders-and-graph-ml @3

### PEFT
**Short:** HuggingFace library for parameter-efficient fine-tuning: LoRA, QLoRA, prefix and adapter tuning on far less memory.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1

### pomegranate
**Short:** Probabilistic modelling library for mixture models, hidden Markov models, Bayesian networks and naive Bayes.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/timeseries-and-anomaly @3

### Process Reward Models
**Short:** Reward models scoring each reasoning step instead of the final answer, a cheap value signal for search and RL.
**Kind:** concept
**Lang:** *
**Roles:** model-training/alignment-and-rl @1, llm-apps/agent-framework @2, ml-lifecycle/evaluation-and-benchmarks @3

### PubMed
**Short:** Biomedical literature corpus of 35M+ abstracts, with full text via PMC, used for medical domain adaptation.
**Kind:** dataset
**Lang:** *
**Roles:** model-training/fine-tuning-and-peft @1, applied-ml/nlp-and-text @3

### PyMC
**Short:** Probabilistic programming library for full Bayesian inference via MCMC and variational methods.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/interpretability-fairness-and-causal @2

### PyTorch
**Short:** Tensor and autograd framework with dynamic graphs; the default runtime for training and running deep models on GPU.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/distributed-training @3, inference/compiler-and-runtime-optimization @3, runtime-systems/collections-and-algorithms @3

### PyTorch 2.x
**Short:** The dominant tensor and autograd framework; 2.x adds torch.compile graph capture, Inductor codegen and FSDP.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, inference/compiler-and-runtime-optimization @2, model-training/distributed-training @2, gpu/kernel-programming @3

### PyTorch DDP
**Short:** PyTorch DistributedDataParallel: one process per GPU, gradients all-reduced via NCCL; the default data-parallel path.
**Kind:** api
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2

### PyTorch FSDP
**Short:** PyTorch fully sharded data parallel: parameters, gradients and optimizer state split across ranks to fit big models.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2

### PyTorch Lightning
**Short:** Structured training-loop framework over PyTorch: removes boilerplate and plugs in multi-GPU strategies and logging.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/distributed-training @2, ml-lifecycle/experiment-tracking-and-tuning @3

### PyTorch lr_scheduler
**Short:** PyTorch learning-rate schedules (CosineAnnealingLR, OneCycleLR, LinearLR, SequentialLR) driven per step or per epoch.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, ml-lifecycle/experiment-tracking-and-tuning @3

### PyTorch torch.optim
**Short:** PyTorch's optimizer package: SGD, Adam, AdamW, L-BFGS plus parameter groups and LR schedule inspection.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1

### Ray RLlib
**Short:** Ray's distributed reinforcement learning library: PPO, DQN, SAC and IMPALA scaled across a cluster.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, applied-ml/recommenders-and-graph-ml @3, model-training/distributed-training @3

### Ray Train
**Short:** Ray's distributed training layer that scales a PyTorch or HF loop across a cluster and handles placement.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, ml-lifecycle/ml-platform-and-pipelines @2, data-movement/batch-and-distributed-compute @3

### Regressor
**Short:** Generic label for a supervised model predicting a continuous value, e.g. scikit-learn's DecisionTreeRegressor.
**Kind:** concept
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1

### Ridge
**Short:** L2-regularized linear regression that shrinks coefficients to control variance and multicollinearity.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### Ring Attention
**Short:** Sequence-parallel attention sharding one long sequence across GPUs, rotating KV blocks in a ring to overlap comms.
**Kind:** concept
**Lang:** *
**Roles:** model-training/distributed-training @1, llm-apps/prompting-context-and-structured-output @2, gpu/multi-gpu-and-collectives @3

### RLlib
**Short:** Ray's reinforcement learning library: PPO, DQN, IMPALA and friends scaled across hundreds of rollout workers.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/distributed-training @2

### rotary-embedding-torch
**Short:** Small PyTorch package providing a reference rotary position embedding (RoPE) implementation to drop into attention.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1

### scikit-learn
**Short:** Python's general-purpose classical ML library: estimators, pipelines, cross-validation, metrics and calibration.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, ml-lifecycle/evaluation-and-benchmarks @2, ml-lifecycle/experiment-tracking-and-tuning @3, applied-ml/nlp-and-text @3, applied-ml/timeseries-and-anomaly @3

### scikit-learn Pipeline
**Short:** Chains transformers and an estimator so fitting happens only on training folds, making CV leakage-free.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, ml-lifecycle/ml-platform-and-pipelines @2, apis-frameworks/design-patterns-and-principles @3

### scikit-learn StackingClassifier
**Short:** scikit-learn ensemble that fits a meta-learner on K-fold out-of-fold predictions from base estimators.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### scikit-learn StackingRegressor
**Short:** sklearn ensemble that trains a meta-regressor on base-model out-of-fold predictions produced by cross_val_predict.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### scikit-multilearn
**Short:** Library of multi-label problem transformations: binary relevance, classifier chains and label powerset.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/nlp-and-text @2

### SEC EDGAR
**Short:** The SEC's full archive of public-company filings; the standard free corpus for financial-domain pretraining.
**Kind:** dataset
**Lang:** *
**Roles:** model-training/fine-tuning-and-peft @1, ml-lifecycle/evaluation-and-benchmarks @3

### SetFit
**Short:** Few-shot text classification recipe: contrastively fine-tune a sentence embedder, then fit a light classifier head.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, applied-ml/nlp-and-text @2

### ShareGPT
**Short:** Widely used multi-turn conversation corpus and the JSON conversation format most SFT toolkits accept.
**Kind:** dataset
**Lang:** *
**Roles:** model-training/fine-tuning-and-peft @1, ml-lifecycle/labeling-and-synthetic-data @2

### sklearn BernoulliNB
**Short:** Naive Bayes variant for binary features, modelling word presence/absence rather than counts.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/nlp-and-text @3

### sklearn CalibratedClassifierCV
**Short:** scikit-learn wrapper adding post-hoc probability calibration (Platt scaling or isotonic regression) to any classifier.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/interpretability-fairness-and-causal @2, ml-lifecycle/evaluation-and-benchmarks @3

### sklearn CategoricalNB
**Short:** scikit-learn Naive Bayes variant for discrete categorical features, modelling each feature's category counts directly.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn ComplementNB
**Short:** Complement Naive Bayes classifier; a stronger baseline than MultinomialNB on imbalanced text.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/nlp-and-text @2

### sklearn DecisionTreeClassifier
**Short:** scikit-learn's CART decision tree with MDI importances, cost-complexity pruning and export_text.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/interpretability-fairness-and-causal @3

### sklearn GaussianNB
**Short:** scikit-learn's Gaussian Naive Bayes classifier; supports partial_fit for incremental online learning.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn GradientBoostingClassifier
**Short:** scikit-learn's reference gradient boosted tree classifier; correct and simple but slow on large datasets.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn HistGradientBoostingClassifier
**Short:** scikit-learn histogram-based gradient boosting classifier: LightGBM-speed, native NaN and categorical handling.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn LabelPropagation
**Short:** scikit-learn graph-based transductive semi-supervised classifier spreading labels over a kNN or RBF similarity graph.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, ml-lifecycle/labeling-and-synthetic-data @2

### sklearn LinearRegression
**Short:** scikit-learn ordinary least squares estimator, the reference implementation and baseline for regression tasks.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn LinearSVC
**Short:** Linear support vector classifier on the liblinear backend; scales past 10k samples but has no predict_proba.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn LogisticRegression
**Short:** scikit-learn's linear classifier for binary and multiclass problems with L1, L2 or ElasticNet regularization.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn MultinomialNB
**Short:** scikit-learn multinomial naive Bayes, the fast baseline classifier for bag-of-words and TF-IDF count features.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/nlp-and-text @2

### sklearn SGDClassifier
**Short:** scikit-learn linear classifier trained by stochastic gradient descent; scales logistic regression/SVM to huge n.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn SVC
**Short:** scikit-learn's kernel support vector classifier over libsvm; soft margin, all kernels, O(n^2-n^3) fit.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn SVR
**Short:** scikit-learn support vector regression with epsilon-insensitive loss and a kernel choice for non-linear fits.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn.mixture.BayesianGaussianMixture
**Short:** Variational Dirichlet-process Gaussian mixture: set an upper bound on components and it prunes the unused ones.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

### sklearn.mixture.GaussianMixture
**Short:** scikit-learn EM-fitted Gaussian mixture with covariance options, BIC/AIC selection, scoring and sampling.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/timeseries-and-anomaly @3

### SkyRL
**Short:** RL library for long-horizon agentic training with verifiable rewards (RLVR) on the NVIDIA training stack.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, llm-apps/agent-framework @3, model-training/distributed-training @3

### Sophia
**Short:** Second-order optimizer for LLM pretraining that uses a cheap diagonal Hessian estimate to precondition steps.
**Kind:** concept
**Lang:** *
**Roles:** model-training/deep-learning-framework @1

### Stable-Baselines3
**Short:** Well-tested PyTorch implementations of PPO, SAC, DQN and friends with a consistent Gym-style API.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1

### Stan
**Short:** Probabilistic programming language with industrial-strength HMC/NUTS sampling, driven from R, Python or the CLI.
**Kind:** tech
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1, runtime-systems/collections-and-algorithms @3, applied-ml/interpretability-fairness-and-causal @3

### statsmodels OLS
**Short:** Ordinary least squares with full inference: coefficient p-values, confidence intervals, AIC/BIC.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/interpretability-fairness-and-causal @2, runtime-systems/collections-and-algorithms @3

### statsmodels VIF
**Short:** Variance Inflation Factor calculation that quantifies how strongly each predictor is explained by the others.
**Kind:** api
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/interpretability-fairness-and-causal @3

### TensorFlow
**Short:** Google's deep-learning framework with Keras training loops, GPU/TPU execution and SavedModel export for serving.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/distributed-training @3, inference/model-server @3, gpu/gpu-math-libraries @3

### TensorFlow MirroredStrategy
**Short:** TensorFlow's synchronous single-host multi-GPU strategy: mirrors variables and all-reduces gradients each step.
**Kind:** api
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2

### TensorFlow/Keras
**Short:** Google's deep-learning framework and its Keras layer API, including the standard loss and metric implementations.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1

### TextBrewer
**Short:** PyTorch knowledge-distillation toolkit with configurable loss/intermediate-layer matching for compressing NLP models.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, inference/quantization-and-compression @3

### TF
**Short:** TensorFlow, Google's deep-learning framework; used here for autoencoders and VAEs with custom architectures.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, inference/model-format-and-edge @3

### TF-Agents
**Short:** Google's official TensorFlow reinforcement learning library: environments, policies, replay buffers and standard agents.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/deep-learning-framework @3

### ThunderSVM
**Short:** GPU-accelerated SVM with a LibSVM-compatible API; fast but effectively dormant since 2019, so verify it still builds.
**Kind:** tech
**Lang:** cpp, python
**Roles:** model-training/classical-ml-and-boosting @1, gpu/gpu-math-libraries @3

### Tianshou
**Short:** Modular PyTorch reinforcement-learning library with strong offline-RL algorithm coverage.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/deep-learning-framework @3

### torch
**Short:** PyTorch - the tensor and autograd framework: nn modules, optimizers, CUDA execution, the standard training loop.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, gpu/gpu-math-libraries @3, inference/inference-engine @3, applied-ml/nlp-and-text @3

### torch.amp
**Short:** PyTorch automatic mixed precision: autocast runs ops in BF16/FP16 on Tensor Cores, with GradScaler for FP16 training.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, gpu/gpu-portability-and-precision @2, inference/quantization-and-compression @3

### torch.distributed
**Short:** PyTorch's process-group and collectives API; NCCL is the default GPU backend, Gloo the CPU fallback.
**Kind:** api
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2

### torch.func
**Short:** PyTorch's functional transforms - functional_call, grad, vmap - for treating parameters as arguments, as MAML needs.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, inference/compiler-and-runtime-optimization @3

### torch.nn.Embedding.from_pretrained
**Short:** Builds an embedding layer from a pretrained matrix such as GloVe or word2vec, optionally frozen, with a padding index.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/nlp-and-text @2

### torch.nn.GRU
**Short:** PyTorch gated recurrent unit layer: cheaper than LSTM with the same API, for sequence modelling and forecasting.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/nlp-and-text @3, applied-ml/timeseries-and-anomaly @3

### torch.nn.LSTM
**Short:** PyTorch's cuDNN-accelerated LSTM layer, with batch_first, bidirectional and multi-layer stacking options.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/timeseries-and-anomaly @3, applied-ml/nlp-and-text @3

### torch.nn.RNN
**Short:** PyTorch's vanilla Elman recurrent layer; the baseline that vanishing gradients push you off of toward LSTM/GRU.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/nlp-and-text @3

### torch.nn.Transformer
**Short:** PyTorch's built-in encoder-decoder transformer module; pass batch_first=True for the modern tensor layout.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/nlp-and-text @2

### torch.nn.utils.clip_grad_norm_
**Short:** PyTorch call that rescales gradients to a maximum global norm, the standard cure for exploding gradients.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1

### torch.nn.utils.rnn.pack_padded_sequence
**Short:** PyTorch helper packing a padded batch so an RNN skips padding steps instead of computing over them.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/nlp-and-text @2

### torch.nn.utils.rnn.pad_packed_sequence
**Short:** Unpacks a PackedSequence back to a padded tensor after an RNN/LSTM, the return leg of variable-length batching.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, applied-ml/nlp-and-text @3

### torch.optim.AdamW
**Short:** Adam with decoupled weight decay, the standard optimizer for transformer training.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1

### torch.optim.lr_scheduler
**Short:** PyTorch learning-rate schedulers (OneCycleLR, CosineAnnealingLR, warmup) that adjust the optimizer LR across training.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1

### torch.utils.data.DataLoader
**Short:** PyTorch iterator batching, shuffling and multi-process-loading a Dataset, with pinned memory for fast GPU copies.
**Kind:** api
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, runtime-systems/concurrency-and-async @3

### torchinfo
**Short:** Prints a Keras-style summary of a PyTorch model: per-layer output shapes, parameter counts and memory estimate.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, observability/profiling-and-performance @2

### TorchRL
**Short:** Meta's PyTorch-native RL library: environment wrappers, replay buffers, and composable policy/loss modules.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/deep-learning-framework @3

### torchrun
**Short:** PyTorch's distributed launcher: spawns one process per GPU and sets RANK/WORLD_SIZE/MASTER_ADDR for NCCL rendezvous.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, gpu/multi-gpu-and-collectives @2

### torchsummary
**Short:** Small library printing a Keras-style PyTorch model summary: per-layer output shapes and parameter counts.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, observability/profiling-and-performance @2

### torchtitan
**Short:** PyTorch-native reference platform for large-scale pretraining, composing FSDP, tensor and pipeline parallelism.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/deep-learning-framework @2

### torchtune
**Short:** Meta's PyTorch-native fine-tuning library with hackable single-file recipes for full, LoRA and QLoRA tuning.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, model-training/distributed-training @3

### TRL
**Short:** HuggingFace post-training library: SFT, DPO, GRPO, RLOO, KTO and reward-model trainers over transformers.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/fine-tuning-and-peft @2

### TRL GRPOTrainer
**Short:** Hugging Face TRL trainer implementing GRPO - group-relative policy optimization - the easiest entry to RL fine-tuning.
**Kind:** api
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/fine-tuning-and-peft @2

### TRL SFTTrainer
**Short:** HuggingFace TRL trainer for supervised fine-tuning; handles label masking, packing, multi-turn and PEFT configs.
**Kind:** api
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, model-training/alignment-and-rl @3

### UltraFeedback
**Short:** Preference dataset of 64K instructions with GPT-4-rated completions, widely used to train reward models and DPO.
**Kind:** dataset
**Lang:** *
**Roles:** model-training/alignment-and-rl @1

### umap-learn
**Short:** UMAP dimensionality reduction for embedding visualization and clustering, with a GPU path via cuML.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/interpretability-fairness-and-causal @3, search-retrieval/ann-index-library @3

### Unsloth
**Short:** Optimized LoRA/QLoRA fine-tuning library; fused kernels give roughly 2x faster training at far lower VRAM.
**Kind:** tech
**Lang:** python
**Roles:** model-training/fine-tuning-and-peft @1, inference/quantization-and-compression @3

### Unsloth GRPO
**Short:** Unsloth's memory-efficient GRPO trainer, letting a 7B model do QLoRA reinforcement learning on a single 24GB GPU.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/fine-tuning-and-peft @2

### veRL
**Short:** Production RL post-training framework with a hybrid FSDP/Megatron trainer and vLLM rollouts; used for DAPO.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, model-training/distributed-training @2, inference/inference-engine @3

### Vowpal Wabbit
**Short:** Fast out-of-core online learner; standard tool for streaming text classification and production contextual bandits.
**Kind:** tech
**Lang:** *
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/recommenders-and-graph-ml @2, model-training/alignment-and-rl @3

### XGBoost
**Short:** Gradient-boosted decision tree library; the default tabular and learning-to-rank baseline, with CPU/GPU hist training.
**Kind:** tech
**Lang:** python, java, cpp
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/recommenders-and-graph-ml @2, gpu/gpu-math-libraries @3

### XGBoost 2.0+
**Short:** Gradient-boosted tree library with GPU hist training, regularized objectives and multi-output support.
**Kind:** tech
**Lang:** python, java, cpp
**Roles:** model-training/classical-ml-and-boosting @1, gpu/gpu-math-libraries @3

### XGBoost Dask
**Short:** XGBoost's Dask integration for training boosted trees across a multi-node cluster (from xgboost import dask).
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1, model-training/distributed-training @2, data-movement/batch-and-distributed-compute @3
