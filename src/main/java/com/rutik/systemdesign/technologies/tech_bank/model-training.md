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

You keep your own PyTorch training loop and let `Accelerator()` prepare the model, optimizer, scheduler, and dataloaders; the same script then runs on CPU, a single GPU, multi-GPU DDP, FSDP, DeepSpeed, or TPU depending on an `accelerate config` file and `accelerate launch` flags, with no `if distributed` branches, no manual `.to(device)`, and no hand-written sampler sharding. Mixed precision, gradient accumulation, and gradient clipping are flags on the same object.

It also handles loading a model too large for one GPU by sharding it across devices and CPU or disk offload. Reach for it when you want distributed training without adopting a whole framework or rewriting your loop as a Trainer subclass — it is the layer underneath the `transformers` Trainer and most diffusion training scripts, so understanding it explains their behaviour too.

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

The whole run lives in one YAML file: base model, dataset paths and format, whether this is continued pretraining or SFT, LoRA/QLoRA rank and target modules, sequence length and sample packing, and the DeepSpeed or FSDP config for multi-GPU. It wraps `transformers`, `peft`, and `trl` underneath, so you get their behaviour without writing a training loop, and the config file itself becomes the reproducibility record.

Its practical advantage is dataset flexibility — it understands a range of instruction and chat formats and applies the model's chat template for you, which is where hand-rolled fine-tunes most often go wrong. Reach for it when you want a repeatable fine-tune from a config; drop to `trl` or plain PyTorch when you need a custom loss, a nonstandard data pipeline, or RL-style training it does not express.

### CatBoost
**Short:** Gradient-boosted decision trees with ordered boosting and native categorical handling; strong on tabular data.
**Kind:** tech
**Lang:** python
**Roles:** model-training/classical-ml-and-boosting @1

Its signature idea is ordered target statistics: to encode a categorical value it uses only the rows preceding that one in a random permutation, so the encoding for a row never sees that row's own target. That is what stops the target leakage plain mean-encoding produces, and it is why you pass raw category columns through `cat_features` with no manual encoding step and no leakage-prone preprocessing to get wrong. Ordered boosting applies the same permutation trick to the gradient estimates.

Its trees are symmetric — every node at a given depth splits on the same feature and threshold — which acts as a regularizer and makes inference very fast, since scoring becomes an index computation rather than a branchy walk; missing values, text and embedding features are handled natively too. Reach for it first on tabular data with high-cardinality categoricals; LightGBM often trains faster on wide numeric data, and comparing the two is cheap enough to be worth doing.

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

It is a set of scikit-learn transformers for turning high-cardinality categorical columns into numbers without one-hot's width explosion: target, CatBoost, James-Stein and M-estimate encoders replace a category with a smoothed statistic of the target, while binary, base-N and hashing encoders compress the identity into a few columns. All follow the `fit`/`transform` contract and accept DataFrames, so they drop straight into a `Pipeline` and a `ColumnTransformer`. The trap is leakage -- any target-based encoding computed on rows that also train the model lets the label leak into a feature, and this library's `TargetEncoder` smooths with a sigmoid weighted by `min_samples_leaf` and does not cross-fit, so wrap it in a fold-aware wrapper such as `NestedCVWrapper` or fit it separately inside each CV fold. Reach for it when a categorical has thousands of levels and one-hot is impractical; for a handful of levels, plain one-hot is safer and simpler.

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

ZeRO removes the redundancy in data-parallel training by partitioning optimizer state across ranks at stage 1, gradients as well at stage 2, and the parameters themselves at stage 3, gathering each shard only when needed. That trades extra communication for memory, and the offload options push optimizer state or parameters to CPU RAM or NVMe when even the sharded copy will not fit.

Reach for it when the model does not fit the GPUs you have. PyTorch's own FSDP now covers much of the stage-3 ground natively, so the reasons to pick DeepSpeed specifically are the offload tiers, pipeline parallelism and its fused kernels.

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

Parameters, gradients and optimizer state are sharded across ranks; a unit's full parameters are all-gathered just before its forward and backward and freed immediately after, so peak memory falls roughly with world size in exchange for extra communication. That trade is what makes a model too large for one GPU trainable at all, and it is why the wrapping granularity matters: wrap too coarsely and the peak barely moves, too finely and the collectives dominate the step.

FSDP2 (`fully_shard`) applies bottom-up per transformer block and represents each shard as a DTensor, which lets it compose with tensor or pipeline parallelism, `torch.compile` and per-parameter mixed precision instead of fighting them. In practice it is paired with activation checkpointing and bf16, and the first thing to check when throughput disappoints is whether communication is overlapping with compute.

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

It was designed so that a single-GPU training script becomes distributed with a handful of lines: initialize, pin the process to a GPU by local rank, shard the dataset, broadcast the initial variables so every worker starts identical, and wrap the optimizer so gradients are averaged before the update. The averaging uses ring all-reduce over NCCL or MPI, where each worker exchanges only with its neighbours, so bandwidth per worker stays constant as the cluster grows instead of converging on one parameter server. Launch is through its own runner or through mpirun.

Reach for it for TensorFlow, or for a shop running more than one framework that wants a single distribution layer. For PyTorch specifically, distributed data parallel is native, uses the same collectives underneath, and is what most current code and documentation assume.

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

Wrapping a loaded model with `get_peft_model` freezes the base weights and injects small low-rank matrices into the targeted projection modules; only those train, so optimizer state and gradients shrink by orders of magnitude and the artifact you ship is an adapter of a few megabytes rather than a full checkpoint. That is what makes per-tenant or per-task variants practical - adapters can be stored, swapped and even served against one shared base model.

It composes with quantized loading through `prepare_model_for_kbit_training`, which is the QLoRA recipe, and `merge_and_unload()` folds the adapter back into the base weights so inference has no runtime overhead at all. The choices that decide quality are which modules to target and what rank and alpha to use; adapting only the attention projections is cheaper but consistently weaker than including the MLP projections.

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

It adds sampler objects following a scikit-learn-style `fit_resample` convention: oversamplers that synthesize minority points by interpolating between neighbours (`SMOTE`, `ADASYN`, and variants for categorical features), undersamplers that drop or clean majority points (`RandomUnderSampler`, `TomekLinks`, `NearMiss`), and ensembles such as `BalancedRandomForestClassifier` that resample inside each bootstrap.

The trap it helps with is also easy to fall into while using it: resampling must happen inside the cross-validation fold and never before the split, or synthetic points derived from validation rows leak and the reported score is fiction — which is exactly why you use `imblearn.pipeline.Pipeline` rather than scikit-learn's when a sampler is one of the steps. Try class weights and a tuned decision threshold first; on many problems they match resampling with far less machinery.

### Isaac Lab
**Short:** NVIDIA GPU robot-learning framework on Isaac Sim: thousands of parallel physics environments for RL.
**Kind:** tech
**Lang:** python
**Roles:** model-training/alignment-and-rl @1, ml-lifecycle/labeling-and-synthetic-data @2, applied-ml/vision-speech-and-multimodal @3

Everything stays on the GPU - physics stepping, observations, rewards and resets are batched tensors - so thousands of copies of the same robot advance in parallel and a policy collects orders of magnitude more experience per wall-clock second than a CPU simulator allows. Environments, robot assets and task templates ship with it, and it plugs into standard RL libraries rather than replacing them.

The purpose is sim-to-real: train under domain randomization over physics parameters, textures and sensor noise so the policy survives the gap to hardware, and generate synthetic demonstration data where real robot time is the scarce resource. It needs an NVIDIA GPU and the Isaac Sim stack, which is the practical barrier to trying it.

### JAX
**Short:** Functional array framework with composable grad/vmap/jit transforms, XLA-compiled to GPU and TPU.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, inference/compiler-and-runtime-optimization @2, gpu/kernel-programming @3

It is the NumPy API plus composable function transformations: `grad` differentiates a pure function, `vmap` adds a batch dimension without rewriting the code, `jit` compiles it through XLA, and `pmap` or `shard_map` spread it across devices. The functional constraint is real, since arrays are immutable, state is threaded explicitly and randomness takes an explicit key, and that is both what makes the transformations composable and reproducible and what makes the code feel foreign coming from PyTorch. Reach for it for research where you differentiate or vectorize unusual mathematics, and for TPU work where XLA is the native path. Neural-network layers come from Flax or Equinox, because JAX itself deliberately ships none.

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

Two design choices explain the speed: continuous features are bucketed into histograms once, so split-finding scans bins rather than sorted values, and trees grow leaf-wise, always splitting the leaf with the largest loss reduction, instead of level by level. Leaf-wise growth reaches a lower loss for the same number of leaves and overfits more readily on small data, which is why `num_leaves` and `min_data_in_leaf` are the parameters that matter most. It handles categorical features natively without one-hot encoding, offers `lambdarank` for learning-to-rank, and takes `scale_pos_weight` or `is_unbalance` for skewed classes. Treat it as the first model to try on tabular and ranking problems and as the baseline any deep-learning proposal has to beat.

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

It is a configuration layer over the standard stack — `transformers`, `peft`, `trl`, DeepSpeed — rather than a new training engine. You point a YAML file (or the web UI) at a base model and a dataset registered in one of its supported formats, choose the stage (continued pretraining, supervised fine-tuning, reward modelling, DPO or PPO) and the method (full fine-tune, LoRA, QLoRA), and it assembles the trainer, quantization, distributed setup and adapter merging.

Reach for it to get a fine-tune running today, and to compare methods or base models quickly without rewriting a script for each. The moment you need a custom loss, a non-standard data pipeline or a modification inside the training loop, drop down to `trl` and `peft` directly — the abstraction that saved you time becomes the thing you are fighting.

### llm-foundry
**Short:** MosaicML/Databricks training stack for pretraining and finetuning LLMs, with WSD schedules and built-in monitoring.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/fine-tuning-and-peft @2

llm-foundry is the training stack MosaicML built and Databricks now maintains: YAML-configured pretraining and finetuning runs for LLMs, using Composer for the training loop and callbacks, streaming datasets read directly from object storage so nodes do not need the corpus on local disk, FSDP for sharding, and deterministic resumption from checkpoints when a node dies mid-run.

Its warmup-stable-decay learning rate schedule is the part worth knowing: the rate stays flat through the bulk of training and only decays at the end, so a run can be extended or branched from the stable phase without having committed to a total step count in advance. Reach for it when you want a configuration-file path to a real multi-node run rather than assembling the loop yourself; for the largest models, stacks built around tensor and pipeline parallelism go further.
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

Megatron-LM is NVIDIA's reference implementation of the parallelism strategies that make training beyond a single GPU's memory possible. Tensor parallelism splits the weights of each matrix multiply across GPUs inside a node, where the interconnect is fastest. Pipeline parallelism assigns contiguous layer ranges to stages on different nodes and keeps them busy by streaming microbatches through, and sequence or context parallelism splits activation memory along the sequence dimension. Layered with ordinary data parallelism, these form the grid you tune so that memory fits and the interconnect never idles.

Reach for it for pretraining and continued pretraining at the scale where a single node is not an option — the 70B-and-above range this repository keeps pointing at. For a single-node finetune it is far heavier machinery than the job needs, and its parallelism now also ships as a library embedded inside other training frameworks.
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

It solves contact dynamics in generalized coordinates with a soft, convex contact model, which stays stable at large timesteps and runs far faster than real time. That speed is what makes reinforcement learning, which needs millions of environment steps, practical at all. Models are declared in MJCF XML, and the Gymnasium locomotion tasks built on it are the standard benchmark for continuous-control algorithms such as SAC, TD3 and PPO. It is equally the workhorse for sim-to-real, where randomizing masses, friction and actuation delay across many parallel simulations is how a policy survives contact with real hardware. It is Apache-2.0 open source and maintained by Google DeepMind, with an MJX variant for batched GPU rollouts.

### Nanotron
**Short:** HuggingFace's minimal LLM pre-training framework with clean 3D (data/tensor/pipeline) parallelism.
**Kind:** tech
**Lang:** python
**Roles:** model-training/distributed-training @1, model-training/fine-tuning-and-peft @2, model-training/deep-learning-framework @3

Nanotron is a small, readable implementation of 3D parallelism — data, tensor and pipeline — for pre-training language models, written to be read rather than only configured. The parallelism logic, the parameter sharding and the pipeline schedule are visible in the code, which is what makes it a reasonable base for understanding how a large training run is actually split across hundreds of GPUs, or for modifying that behaviour.

Reach for it when the goal is comprehension or a mid-scale pre-training run you want to be able to reason about. For very large production runs, the heavily optimized alternatives carry more accumulated kernel work and more operational scar tissue, which is worth more than readability once a run costs real money per hour.

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

The components of an RLHF loop - actor, critic, reward model, reference model and a vLLM engine doing rollout generation - are placed as separate Ray actors, so each can own its own GPUs and the generation step runs at inference-engine speed instead of inside the training framework. Generation is normally the bottleneck in PPO, so that separation is the reason the design exists rather than an implementation detail.

It implements the surrounding recipe too: supervised fine-tuning, reward modeling, DPO, and the on-policy algorithms PPO, GRPO and REINFORCE++. Reach for it when a run outgrows one machine and a single-process trainer stops fitting; for a small model on one node, TRL is dramatically less to configure and operate.

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

Models are built compositionally: a distribution is an object, a mixture is a set of distributions with weights, a hidden Markov model is a set of states each holding a distribution, and a Bayesian network is a graph of them, so a mixture of exponential and Poisson components is as easy to express as a Gaussian one. Fitting uses expectation-maximization, and the models tolerate missing data and out-of-core batches. It now sits on PyTorch tensors, which brings GPU execution and autograd along. Reach for it when you want a generative model whose structure and parameters you can read and defend, for anomaly detection or sequence segmentation, rather than the single Gaussian mixture scikit-learn offers.

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

In PyMC you write the generative story of your data directly — priors, deterministic transforms, and a likelihood — inside a model context block, and the library compiles that graph, derives gradients, and samples the posterior with NUTS, or approximates it variationally when sampling is too slow. What comes back is a distribution over every parameter rather than a point estimate, so uncertainty, credible intervals and hierarchical partial pooling across groups are first-class rather than bolted on.

Reach for it when data per group is thin and pooling helps, when you need calibrated uncertainty rather than a number, or when domain knowledge deserves to be an explicit prior — small-sample A/B tests, hierarchical forecasting, measurement models. The cost is inference time and the need to check convergence; it does not compete with gradient boosting for raw predictive accuracy on large tabular data.
### PyTorch
**Short:** Tensor and autograd framework with dynamic graphs; the default runtime for training and running deep models on GPU.
**Kind:** tech
**Lang:** python
**Roles:** model-training/deep-learning-framework @1, model-training/distributed-training @3, inference/compiler-and-runtime-optimization @3, runtime-systems/collections-and-algorithms @3

You write ordinary Python and the framework records a tape of the tensor operations that actually executed, so `loss.backward()` differentiates the graph that ran — there is no separate graph-definition phase, which is why a `print` statement or a breakpoint mid-model just works. Tensors carry a device, so `.to("cuda")` moves the work and every operation dispatches a GPU kernel, usually into cuBLAS or cuDNN; `torch.compile` traces and fuses the graph ahead of time to cut kernel-launch and memory-traffic overhead where eager execution leaves performance on the table.

Around that core sit the layers, losses and optimizers in `torch.nn` and `torch.optim`, `DataLoader` with pinned memory and non-blocking copies for feeding the GPU, and `DistributedDataParallel` and FSDP for multi-GPU and multi-node training. It is also the runtime that inference engines such as vLLM build on, which is why their wheels are pinned to a specific PyTorch version. Reach for it as the default for anything neural; for tabular problems a gradient-boosting library is usually both stronger and much cheaper.

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

You move the model, `training_step`, `validation_step` and optimizer configuration into a LightningModule, and the Trainer owns the loop: device placement, gradient accumulation, mixed precision, checkpointing, early stopping, logging, and the distributed strategy -- DDP, FSDP or DeepSpeed -- selected by a flag rather than by rewriting the script.

Reach for it to stop reimplementing the same loop per project and to go from one GPU to multi-node without touching model code. The cost is indirection: an unusual training scheme -- adversarial, multi-optimizer, custom backward -- fights the hidden loop, and debugging means knowing which hook runs when.

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

Everything follows one interface — `fit`, `predict`, `transform` — and that uniformity is what makes `Pipeline` and `ColumnTransformer` the most valuable things in the library rather than a convenience. Putting the scaler, imputer or vectorizer inside a pipeline means cross-validation fits them on training folds only, so leakage stops being something you must remember not to do and becomes structurally impossible.

The breadth covers linear models, trees and forests, SVMs, clustering, decomposition and the surrounding apparatus: `GridSearchCV` and `RandomizedSearchCV`, the splitters that matter for honest evaluation (`GroupKFold`, `TimeSeriesSplit`), probability calibration, and the metric functions that people use to score models trained in entirely different frameworks. Its boundaries are equally clear: single machine, in memory, CPU-first, and no deep learning — so it ends where the data outgrows RAM or the problem needs learned representations rather than engineered features.

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

Keras is the authoring API, tf.function traces Python into a graph that can be optimized and executed without the interpreter in the loop, tf.data builds input pipelines that prefetch and parallelize so the accelerator is not left idle, and SavedModel is the portable artifact everything downstream consumes. That artifact is the reason to choose it: the same export feeds TensorFlow Serving, the mobile and embedded runtime, and the browser, and TPU support is first-class.

Reach for it when you are working in an existing TensorFlow codebase, targeting TPUs, or need that mobile and edge deployment path. For new research and most new model code, PyTorch is where the ecosystem, the papers and the pretrained checkpoints are, so starting here is a deliberate choice rather than the default.

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

Given `--nproc_per_node`, torchrun starts one process per GPU and sets the environment the distributed backend expects -- `RANK`, `LOCAL_RANK`, `WORLD_SIZE`, `MASTER_ADDR`, `MASTER_PORT` -- so `init_process_group("nccl")` and `DistributedDataParallel` find each other without you threading ranks through your own code; the conventional first line of the script is setting the current CUDA device from `LOCAL_RANK`. Multi-node runs use a rendezvous backend rather than a hand-picked master, and it supports elastic membership and restarting the whole worker group on failure, which is why it superseded `torch.multiprocessing.spawn` and the older launch module. It is a launcher and nothing more: it starts processes and coordinates rendezvous, and it does nothing about data sharding -- that is `DistributedSampler`'s job, and forgetting it means every rank trains on identical batches. Reach for it for any multi-GPU PyTorch training; under a cluster scheduler, SLURM's `srun` or a Kubernetes job can fill the same role.

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

Its trainers subclass the HuggingFace `Trainer`, so datasets, `accelerate`, PEFT adapters and DeepSpeed integration all work unchanged. `SFTTrainer` does supervised fine-tuning; `DPOTrainer` aligns straight from a preference dataset with no reward model at all; `GRPOTrainer` and `RLOOTrainer` do online RL against a reward function; `RewardTrainer` trains the reward model when you do want one.

Reach for it after supervised fine-tuning when you need preference alignment and do not want to implement the objective yourself. Pin the version: the trainers listed above are the stable surface, while PPO and ORPO live in `trl.experimental` and can change in any release.

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

It patches the model implementation with hand-written Triton kernels and a manual backward pass for the adapter path, so the arithmetic is unchanged but each step uses far less memory and time; 4-bit QLoRA is supported directly, and it slots under the normal `SFTTrainer` and `DPOTrainer` you would use anyway, exporting merged or GGUF weights at the end.

Reach for it when you are fine-tuning on a single GPU and VRAM is the binding constraint -- that is the case it is built for and where the speedups are real. A large multi-node run belongs on FSDP or DeepSpeed instead, and each supported model family needs its own patch, so a brand-new architecture may not be covered yet.

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

It learns online, one example at a time, from a sparse text format and the hashing trick, so memory is bounded by the hash table rather than by vocabulary size and a dataset far larger than RAM streams off disk in a single pass. That design makes it the standard tool for production contextual bandits: it implements the exploration policies and the off-policy evaluation you need to score a candidate policy against logged traffic, so a ranker can learn from its own impressions instead of waiting for a nightly retrain. Reach for it for extreme-scale sparse text classification or a bandit-driven ranking loop; for ordinary batch tabular problems a boosted-tree library is more accurate and much easier to operate.

### XGBoost
**Short:** Gradient-boosted decision tree library; the default tabular and learning-to-rank baseline, with CPU/GPU hist training.
**Kind:** tech
**Lang:** python, java, cpp
**Roles:** model-training/classical-ml-and-boosting @1, applied-ml/recommenders-and-graph-ml @2, gpu/gpu-math-libraries @3

Trees are grown over histogram-binned features with second-order gradients, missing values are handled by learning a default branch direction at each split rather than requiring imputation, and overfitting is controlled by shrinkage, row and column subsampling and explicit L1/L2 penalties. That combination is why it remains hard to beat on tabular data without heavy tuning, and why it is the baseline any deep-learning approach on tables has to justify itself against.

The modern API is smaller than the folklore suggests: `tree_method="hist"` covers CPU and GPU with `device="cuda"` selecting the accelerator, categorical features are supported natively without one-hot encoding, and `early_stopping_rounds` is a constructor argument rather than a `fit()` keyword. Ranking objectives make it a standard learning-to-rank model too. Tune depth, learning rate and subsampling together with early stopping on a genuine validation split, and remember trees cannot extrapolate outside the range they were trained on.

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
