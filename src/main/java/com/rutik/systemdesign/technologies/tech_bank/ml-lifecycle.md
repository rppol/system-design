# MLOps & evaluation — technology bank

<!-- tech-bank tier: ml-lifecycle -->

The 184 tools whose PRIMARY role — the first, best-weighted one — sits in
the **MLOps & evaluation** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### adk eval
**Short:** Google ADK CLI that replays an evalset of test conversations against trajectory and response-match criteria.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @2

### Agent Platform Feature Store
**Short:** Managed cloud feature store providing offline training features and low-latency online serving for production models.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1

### AgentBench
**Short:** Benchmark suite that evaluates LLMs acting as agents across interactive environments; single-agent, not multi-agent.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @3

### AgentEval
**Short:** AutoGen-originated framework for multi-dimensional task-utility assessment of agent runs; community-maintained.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @3

### Alibi Detect
**Short:** Broad drift, outlier and adversarial detection library: MMD, KS, chi-squared and classifier drift, TF and PyTorch.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, applied-ml/timeseries-and-anomaly @2, security/ai-safety-and-guardrails @3

### Alpaca farm
**Short:** Stanford simulation framework for instruction following: LLM-simulated preference feedback plus an evaluation suite.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/labeling-and-synthetic-data @2, model-training/alignment-and-rl @3

### ARC-AGI-2
**Short:** Abstract reasoning benchmark of novel grid puzzles used to gauge frontier model generalization, not agent skill.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Argilla
**Short:** Open-source annotation and dataset-curation platform for human feedback, preference labeling and adjudication.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/alignment-and-rl @3, ml-lifecycle/evaluation-and-benchmarks @3

### Arize
**Short:** SaaS ML/LLM observability: production drift detection, performance tracing and SHAP-based explanation logging.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, observability/tracing-apm-and-llm-observability @2, applied-ml/interpretability-fairness-and-causal @3

### Arize AI
**Short:** Commercial ML and LLM observability platform: feature/prediction drift, tracing, explainability, retraining triggers.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, observability/tracing-apm-and-llm-observability @2, ml-lifecycle/evaluation-and-benchmarks @3

### Arthur
**Short:** Commercial ML/LLM monitoring platform with drift and performance tracking plus a real-time model firewall.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, security/ai-safety-and-guardrails @2, observability/tracing-apm-and-llm-observability @3

### Artificial Analysis
**Short:** Independent benchmarking service publishing standardized quality, latency and price comparisons across models.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Auto-sklearn
**Short:** Tabular AutoML that searches sklearn pipelines with meta-learning warm-start, SMAC Bayesian tuning and ensembling.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @2, ml-lifecycle/ml-platform-and-pipelines @3

### AWS SageMaker
**Short:** AWS end-to-end ML platform: managed notebooks, distributed training jobs, model registry and autoscaling endpoints.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, inference/model-server @2, model-training/distributed-training @2, platform-delivery/cloud-platform-and-cost @3

### Ax
**Short:** Meta's adaptive experimentation platform: Bayesian optimization and bandit-driven A/B allocation over a BoTorch backend.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/evaluation-and-benchmarks @2, applied-ml/recommenders-and-graph-ml @3

### baal
**Short:** PyTorch active-learning library using MC-dropout and BALD to pick the most informative samples to label.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/interpretability-fairness-and-causal @2

### back-translation
**Short:** Text augmentation translating a sentence to another language and back, yielding a paraphrase for consistency training.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/nlp-and-text @3

### BEIR benchmark
**Short:** Zero-shot information-retrieval benchmark of 18 datasets with a standardized nDCG@10 evaluation protocol.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/lexical-and-hybrid-search @2

### benchmark_app
**Short:** OpenVINO's ground-truth throughput and latency CLI; reports stream count, batch shape and per-request timings.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, observability/profiling-and-performance @2, inference/compiler-and-runtime-optimization @3

### Benchmarks
**Short:** Umbrella entry for tool-use benchmark suites such as ToolBench, API-Bank and ToolAlpaca.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/tool-use-and-mcp @3

### bert_score
**Short:** Python package computing BERTScore, an embedding-similarity generation metric; use rescale_with_baseline for scale.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### Braintrust
**Short:** Commercial LLM eval and observability platform: dataset versioning, scorers, traces, experiment diffs.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, observability/tracing-apm-and-llm-observability @2, ml-lifecycle/experiment-tracking-and-tuning @3, llm-apps/prompting-context-and-structured-output @3

### BrowseComp
**Short:** Benchmark of 1,266 hard web-research questions with short verifiable answers, so no LLM judge is needed.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @3

### calibration-library
**Short:** Post-hoc probability calibration tooling: expected calibration error, reliability diagrams, temperature/Platt scaling.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/interpretability-fairness-and-causal @2

### Chatbot Arena
**Short:** Crowd-sourced LLM benchmark ranking models by blind pairwise human preference votes into Elo-style ratings.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### checklist
**Short:** Behavioral testing library for NLP: capability-sliced invariance and directional tests beyond aggregate metrics.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### clean-fid
**Short:** Corrected FID implementation that fixes the image-resizing bugs which make generative-model scores incomparable.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### cleanlab
**Short:** Confident-learning library that finds mislabeled examples and bad pseudo-labels in a training set.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, ml-lifecycle/evaluation-and-benchmarks @3

### Comet ML
**Short:** Experiment tracking platform logging runs, metrics, code snapshots and diffs, with a model registry.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

### Common Crawl
**Short:** Monthly open web crawl of ~2B pages (~345 TiB per snapshot); the raw corpus most LLM pretraining data is filtered from.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/rag-and-document-processing @3

### conll18 scorer
**Short:** Official CoNLL shared-task scoring script for parsing metrics such as UAS/LAS and bracketing F1.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### custom drift detectors
**Short:** Hand-written checks comparing serving feature and prediction distributions against training data.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1

### custom split infrastructure
**Short:** An in-house A/B assignment service that buckets users into experiment arms and keeps the assignment sticky.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### CVAT
**Short:** Open-source annotation tool for labeling images and video with boxes, polygons, masks and tracks.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/vision-speech-and-multimodal @3

### Cybench
**Short:** Cybersecurity agent benchmark of 40 real CTF tasks scored on exact flag match, making it hard to game.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/ai-safety-and-guardrails @2, llm-apps/agentic-environments @3

### datasets
**Short:** Hugging Face library that downloads, memory-maps and preprocesses benchmark corpora like GLUE, SQuAD and CoNLL-2003.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @3, ml-lifecycle/ml-platform-and-pipelines @3

### DCLM
**Short:** DataComp for Language Models: a data-curation benchmark plus the DCLM-Baseline filtered Common Crawl corpus.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Deepchecks
**Short:** Open-source test suites for data and model validation: train/test/production comparison, drift and integrity checks.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, data-movement/data-quality-and-lineage @2, ml-lifecycle/evaluation-and-benchmarks @3

### DeepEval
**Short:** pytest-style LLM evaluation framework with dozens of built-in judge metrics (hallucination, toxicity, relevance).
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, devtools/testing-and-mocking @2

### Distilabel
**Short:** HuggingFace framework for modular synthetic-data pipelines: generation, LLM-as-judge filtering and preference pairs.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1

### dspy.Evaluate
**Short:** DSPy's evaluation driver: runs a metric over a dev set with threading, producing the optimizer's score.
**Kind:** api
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/prompting-context-and-structured-output @2

### DVCLive
**Short:** Lightweight metric/param logger that writes run results into DVC pipelines for Git-tracked experiment comparison.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, data-movement/data-quality-and-lineage @3

### Eleuther Harness
**Short:** EleutherAI's lm-evaluation-harness: the standard runner for open LLM benchmark suites.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Epoch AI
**Short:** Research group publishing independent, harness-disclosed re-runs of model and agent benchmarks.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Eppo
**Short:** Warehouse-native experimentation and feature-flag platform for running and analysing A/B tests.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, platform-delivery/ci-cd-and-release @3

### evalb
**Short:** Standard parser evaluation program computing bracketing precision, recall and F1 against gold constituency trees.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### Evals
**Short:** Generic name for a custom task-evaluation harness that scores model outputs against a fixed set of cases.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### evaluate
**Short:** Hugging Face library giving one API over metrics such as BLEU, ROUGE, BERTScore and seqeval, loaded by name.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### Evidently AI
**Short:** Open-source library producing data-drift, data-quality and model/LLM evaluation reports for deployed models.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, ml-lifecycle/evaluation-and-benchmarks @2, observability/tracing-apm-and-llm-observability @3

### Evol-Instruct
**Short:** Synthetic-data method that iteratively rewrites seed instructions into harder variants to build SFT corpora.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/fine-tuning-and-peft @3

### Feast
**Short:** Open-source feature store: point-in-time-correct offline feature joins plus a low-latency online store for serving.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, applied-ml/recommenders-and-graph-ml @3, data-movement/data-quality-and-lineage @3

### Feature engineering
**Short:** Constructing model input features from raw data; Spark or dbt for batch, Flink for streaming computation.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, data-movement/batch-and-distributed-compute @2

### Feature store
**Short:** The online/offline feature layer itself, not a product: Redis for serving, S3/Hive for training.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1

### feature-engine
**Short:** scikit-learn-compatible feature engineering transformers: outlier capping, lag features, cyclic and rare-label encoding.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, model-training/classical-ml-and-boosting @2, applied-ml/timeseries-and-anomaly @3

### featuretools
**Short:** Automated feature engineering by deep feature synthesis, generating aggregates across related tables.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, model-training/classical-ml-and-boosting @3

### Fiddler
**Short:** SaaS ML observability platform logging production explanations and monitoring SHAP and feature drift.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, applied-ml/interpretability-fairness-and-causal @2

### Fiddler AI
**Short:** Commercial ML observability platform: production drift and performance monitoring with explainability and fairness.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, applied-ml/interpretability-fairness-and-causal @2, observability/alerting-and-incident-response @3

### Flower Datasets
**Short:** Library that partitions standard datasets into reproducible non-IID client splits for federated learning experiments.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/privacy-and-compliance @2

### Frontier LLM API
**Short:** Shorthand for calling a top-tier hosted model (GPT/Claude/Gemini) as an annotator or teacher rather than a product.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, inference/model-server @3

### FS-Mol
**Short:** Microsoft few-shot molecular property prediction benchmark; a real drug-discovery task distribution for meta-learning.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/recommenders-and-graph-ml @3

### GAIA
**Short:** General-assistant benchmark for agents; Gaia2 runs tasks asynchronously against a clock in Meta's ARE platform.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### GAIA benchmark
**Short:** 466-task benchmark for generalist agents across three difficulty levels, scoring multi-step tool-use reasoning.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Gaia2
**Short:** Meta's general-assistant agent benchmark, run asynchronously against a wall clock inside the ARE platform.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @3

### GDPval
**Short:** OpenAI benchmark of economically valuable expert tasks, used as frontier-model context rather than an agent test.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Google Vertex AI
**Short:** Google Cloud's managed ML platform: training, pipelines, registry, endpoints and hosted Gemini and partner models.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, inference/model-server @2, llm-apps/llm-gateway-and-routing @2, platform-delivery/cloud-platform-and-cost @3

### Google Vertex AI AutoML
**Short:** Vertex AI's managed AutoML for tabular and image models: upload data, get a trained and deployable model.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, ml-lifecycle/experiment-tracking-and-tuning @3, model-training/classical-ml-and-boosting @3

### GQA
**Short:** Visual question-answering benchmark for compositional spatial reasoning over scene graphs.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### H2O AutoML
**Short:** AutoML engine that searches tabular model families and builds stacked ensembles, with a Java scoring artifact.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @2, ml-lifecycle/ml-platform-and-pipelines @3

### HLE
**Short:** Humanity's Last Exam: a very hard closed-ended reasoning benchmark used as frontier model context.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### HoneyHive
**Short:** LLM evaluation and observability platform: tracing, multi-dimensional rubrics, annotation queues and drift detection.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, observability/tracing-apm-and-llm-observability @2, ml-lifecycle/drift-and-production-monitoring @3

### Hopsworks
**Short:** Open-source feature store and ML platform with Spark and Python feature pipelines plus online serving.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1

### Hugging Face
**Short:** The model and dataset hub plus its Python ecosystem; the default source for pretrained weights and export tooling.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, ml-lifecycle/experiment-tracking-and-tuning @2, applied-ml/nlp-and-text @3, model-training/deep-learning-framework @3

### huggingface
**Short:** The Hugging Face ecosystem: the model/dataset hub plus the Python libraries that download, run and share those models.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/deep-learning-framework @2, applied-ml/vision-speech-and-multimodal @2, applied-ml/nlp-and-text @2

### HuggingFace Hub
**Short:** Hosted registry for downloading, versioning and sharing models, LoRA adapters and datasets.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/fine-tuning-and-peft @2, inference/model-format-and-edge @3

### HumanEval
**Short:** Benchmark of 164 hand-written Python programming problems with unit tests; the classic code-generation metric.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Hyperopt
**Short:** Bayesian hyperparameter search library using TPE; older but widely used alternative to grid search.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

### Inspect AI
**Short:** UK AI Security Institute's MIT-licensed eval framework: Dataset/Task/Solver/Scorer, sandboxed agent evals, log viewer.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/ai-safety-and-guardrails @3, llm-apps/agentic-environments @3

### jiwer
**Short:** Computes word and character error rate for speech transcripts with configurable text normalization.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### krippendorff
**Short:** Small Python package computing Krippendorff's alpha inter-annotator agreement on ordinal or ragged data.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/labeling-and-synthetic-data @2

### Kubeflow
**Short:** Kubernetes-native ML platform: Pipelines, Training Operator for distributed jobs, Katib HPO and KServe serving.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, data-movement/workflow-and-durable-execution @2, model-training/distributed-training @3, platform-delivery/kubernetes-and-orchestration @3

### Kubeflow Pipelines
**Short:** Kubernetes-native ML pipeline orchestrator: containerized DAG steps for training and batch inference.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, data-movement/workflow-and-durable-execution @2

### Label Studio
**Short:** Open-source multi-modal annotation platform: labeling UIs, multi-rater assignment, agreement reporting and adjudication.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, ml-lifecycle/evaluation-and-benchmarks @3, applied-ml/vision-speech-and-multimodal @3

### labeling pipelines
**Short:** The repeatable process turning raw production data into labelled training/eval sets: sampling, annotation, adjudication.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1

### LEAF
**Short:** Federated-learning benchmark suite with realistic non-IID client partitions (FEMNIST, Shakespeare, Sent140).
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/privacy-and-compliance @2

### LLM Perf Leaderboard
**Short:** Public leaderboard benchmarking model throughput, latency and memory per hardware target and serving backend.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, inference/inference-engine @3

### LM-Eval Harness
**Short:** EleutherAI's standard harness for running language-model benchmarks with consistent prompting and scoring.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### lm-eval-harness
**Short:** EleutherAI harness that runs a language model over standard benchmarks such as ARC, GSM8K, MATH and HumanEval.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### lm-evaluation-harness
**Short:** EleutherAI's standard harness for running LLM benchmarks (MMLU, HellaSwag, ARC) reproducibly across backends.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### LongBench
**Short:** Standardized bilingual benchmark of long-context tasks measuring how models degrade as input length grows.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Managed training
**Short:** Cloud-run training jobs such as SageMaker Training or Vertex AI Training, where the provider owns the cluster.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, model-training/distributed-training @3

### math-verify
**Short:** Library that symbolically checks whether a model's math answer equals the reference, for grading and RL reward signals.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/alignment-and-rl @2

### Meta-Dataset
**Short:** Google's 10-dataset cross-domain few-shot classification benchmark, built to stress generalization beyond miniImageNet.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/fine-tuning-and-peft @3, applied-ml/vision-speech-and-multimodal @3

### Metaflow
**Short:** Netflix ML workflow framework: Python-decorated steps with versioned artifacts and cloud-scaled execution.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, data-movement/workflow-and-durable-execution @2

### Microsoft NNI
**Short:** Archived Microsoft toolkit for hyperparameter tuning and neural architecture search: ASHA, PBT, DARTS, ENAS.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/deep-learning-framework @3

### MLE-bench
**Short:** OpenAI benchmark of 75 Kaggle competitions measuring end-to-end ML engineering agents, with cheating detectors.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### MLflow
**Short:** Open-source ML experiment tracking, model registry and packaging; logs params, metrics and artifacts per run.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @2

### MLflow 2.10+
**Short:** Experiment tracking, model registry and packaging platform, with autologging for scikit-learn and GBDT runs.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @2, inference/model-server @3

### MLflow Model Registry
**Short:** MLflow's model lifecycle store: versions, aliases like @champion/@challenger, tags and promotion history.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @3

### MLflow Tracking
**Short:** Open-source run tracking: logs params, metrics and artifacts to a SQL backend, with a linked model registry.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

### MMBench
**Short:** Multilingual, multi-domain VQA benchmark with circular-evaluation scoring for vision-language models.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### MMMU
**Short:** College-level multi-discipline visual question answering benchmark spanning science, engineering and medicine figures.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### Monitoring
**Short:** Watching a deployed model in production for drift and quality decay, with tools such as Evidently, Arize or WhyLogs.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1

### Most large tech companies
**Short:** Not a technology: a table cell referring to the in-house ML platforms large tech companies build for themselves.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @3

### MTEB Leaderboard
**Short:** Massive Text Embedding Benchmark leaderboard: compare embedding models by retrieval and other task scores.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/ann-index-library @3

### mup
**Short:** Maximal Update Parametrization library so hyperparameters tuned on a small model transfer to a large one.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/deep-learning-framework @2, model-training/distributed-training @3

### NannyML
**Short:** Estimates a deployed model's performance without labels (CBPE); a covariate-shift tool, not a concept-drift one.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1

### NAS-Bench-201
**Short:** Tabular benchmark of pre-trained architectures so neural architecture search methods compare without retraining.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/experiment-tracking-and-tuning @2

### NATS-Bench
**Short:** Tabular neural-architecture-search benchmark: pre-computed results for every architecture so NAS runs compare fairly.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Neptune
**Short:** Hosted experiment tracker logging runs, metrics, params and artifacts, with a model registry for ML teams.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

### NVIDIA Isaac Sim
**Short:** Photorealistic robotics simulator on Omniverse, used to generate synthetic training data and do sim-to-real.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/vision-speech-and-multimodal @2

### Once-for-All
**Short:** Train one supernet, then extract per-device sub-networks without retraining; a NAS approach for edge deployment.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, inference/quantization-and-compression @2, inference/model-format-and-edge @3

### OpenAI Evals
**Short:** Open-source eval framework and registry: YAML/JSONL eval definitions with programmatic and model-graded templates.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Optimizely
**Short:** Commercial experimentation platform for online A/B tests, feature rollout and result analysis.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, platform-delivery/ci-cd-and-release @2

### Optuna
**Short:** Hyperparameter optimization framework: TPE/Bayesian search, Hyperband pruning, distributed studies over any loop.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @3

### Optuna 3.3+
**Short:** Define-by-run hyperparameter optimization framework with TPE/CMA-ES samplers and pruning of unpromising trials.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

### OSWorld
**Short:** Benchmark of real desktop GUI tasks in live Ubuntu VMs, used to evaluate computer-use agents end to end.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @2

### OSWorld 2.0
**Short:** Long-horizon successor to the OSWorld computer-use benchmark: real desktop VM tasks, still far from saturated.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @2

### Penn Treebank
**Short:** Classic annotated English corpus (~1M tokens) used as a parsing and language-modelling benchmark.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### Problem formulation
**Short:** Design-stage step of turning a business goal into a modelling target, label and metric before picking an algorithm.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, model-training/classical-ml-and-boosting @3

### Prodigy
**Short:** Scriptable human-in-the-loop annotation tool from the spaCy team, with built-in active-learning example ordering.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1

### Promptfoo
**Short:** CLI eval and red-team runner for prompts and models; declarative YAML assertions run in CI across model matrices.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/ai-safety-and-guardrails @2, devtools/testing-and-mocking @3

### pytorch-fid
**Short:** Reference implementation for computing Frechet Inception Distance between real and generated image sets.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### pytrec_eval
**Short:** Python wrapper around trec_eval computing nDCG, MAP, MRR and other offline retrieval metrics from qrels.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/lexical-and-hybrid-search @3

### RAGAS
**Short:** RAG evaluation library scoring faithfulness, context recall/precision and answer relevance; can synthesize QA sets.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/rag-and-document-processing @2, ml-lifecycle/labeling-and-synthetic-data @3

### RAGAS faithfulness
**Short:** RAGAS metric checking each generated claim is supported by retrieved context, catching hallucination without labels.
**Kind:** api
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/rag-and-document-processing @2

### ranx
**Short:** Fast IR evaluation library computing nDCG, MAP and MRR with statistical significance tests and rank fusion.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/lexical-and-hybrid-search @2

### Ray Tune
**Short:** Distributed hyperparameter search on Ray with ASHA, HyperBand and PBT schedulers; plugs into most trackers.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/distributed-training @3

### RedPajama-v2
**Short:** Together AI's open 30T-token web corpus with quality signals, used for LLM pre-training.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/fine-tuning-and-peft @3

### Requirements
**Short:** Not a technology: the requirements-clarification step of an ML system design interview; no tooling involved.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @3

### Reward Bench
**Short:** Benchmark scoring how well a reward model ranks chosen over rejected responses across chat, safety and reasoning.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/alignment-and-rl @2

### Roboflow
**Short:** Hosted computer-vision dataset platform: annotation, augmentation, versioning and export to YOLO/COCO formats.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/vision-speech-and-multimodal @2

### RobustBench
**Short:** Standardized adversarial robustness leaderboard plus a model zoo of pretrained robust checkpoints.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/ai-safety-and-guardrails @2

### rouge_score
**Short:** Google's pure-Python ROUGE implementation for summarization overlap metrics; fast and dependency-light.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### RULER
**Short:** Synthetic long-context benchmark with multi-hop and aggregation tasks; far more rigorous than needle-in-a-haystack.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/prompting-context-and-structured-output @2

### RULER benchmark
**Short:** Synthetic long-context benchmark: 13 tasks in 4 categories evaluated at increasing lengths from 4K upward.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### sacrebleu
**Short:** Reference implementation of BLEU/chrF with fixed tokenization, so translation scores are reproducible.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### SageMaker Feature Store
**Short:** AWS-managed feature store pairing an online low-latency store with an offline S3/Athena store.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, platform-delivery/cloud-platform-and-cost @3

### SageMaker Model Registry
**Short:** AWS registry versioning trained models into groups with approval status that gates deployment pipelines.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @2

### Scale AI
**Short:** Managed data-annotation vendor supplying human labels and preference data at scale.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1

### scikit-optimize
**Short:** Bayesian optimization library for expensive black-box tuning; sequential model-based search over hyperparameters.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @3

### scikit-plots
**Short:** Plotting helpers for scikit-learn results: ROC/PR curves, confusion matrices, calibration and lift charts.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/experiment-tracking-and-tuning @3

### SEED-Bench
**Short:** 19K-question multimodal benchmark spanning 12 dimensions of image and video understanding.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### Self-Instruct
**Short:** Bootstrapping method where an LLM generates and filters its own instruction-following training data from seed tasks.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/fine-tuning-and-peft @3

### seqeval
**Short:** Python library computing entity-level (span) precision/recall/F1 for BIO/BIOES sequence-labeling tasks such as NER.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### sklearn SelfTrainingClassifier
**Short:** scikit-learn wrapper adding pseudo-labels from a base estimator's confident predictions each round.
**Kind:** api
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/classical-ml-and-boosting @2

### sklearn TargetEncoder
**Short:** scikit-learn's cross-fitted target encoder for high-cardinality categoricals; use fit_transform to avoid leakage.
**Kind:** api
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, model-training/classical-ml-and-boosting @2

### skweak
**Short:** Weak-supervision library for NLP that aggregates noisy labelling functions into NER labels via an HMM label model.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/nlp-and-text @2

### small-text
**Short:** Active learning library for text classification that picks the next examples to label, with transformers support.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/nlp-and-text @2

### Snorkel
**Short:** Weak supervision: write labeling functions, then fit a label model denoising their votes into training labels.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1

### Snorkel Flow
**Short:** Weak-supervision platform: write labeling functions, denoise them with a label model, emit probabilistic labels.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, ml-lifecycle/ml-platform-and-pipelines @3

### Statsig
**Short:** Experimentation platform for feature flags and A/B tests with automated metric readouts.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, platform-delivery/ci-cd-and-release @2

### Supervisely
**Short:** Cloud computer-vision annotation platform supporting semantic and instance segmentation plus dataset management.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/vision-speech-and-multimodal @2

### SWE-bench
**Short:** Benchmark of 2,294 real GitHub issues from 12 Python repos, scored by whether the agent's patch passes the repo tests.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @3

### SWE-bench Pro
**Short:** Long-horizon repository-repair benchmark: 731 public and 276 private real-world issues; successor to SWE-bench Verified.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @3

### Tecton
**Short:** Managed feature platform that computes streaming and batch features and serves point-in-time-correct joins.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, data-movement/event-streaming-and-processing @3

### TensorBoard
**Short:** Visualization UI for training runs: loss curves, learning rate, gradient norms, activation histograms, embeddings.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

### Terminal-Bench
**Short:** Benchmark of 89 containerized terminal tasks for coding agents, run through the neutral Terminus harness.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @2

### TextVQA
**Short:** VQA benchmark whose questions can only be answered by reading text inside the image, so it tests OCR grounding.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### The Pile
**Short:** EleutherAI's 825GB curated open text corpus for LM pretraining and for anti-forgetting mixes during fine-tuning.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/fine-tuning-and-peft @3

### Torchmeta
**Short:** PyTorch library of episodic N-way K-shot dataloaders for meta-learning benchmarks such as Omniglot and miniImageNet.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/deep-learning-framework @2

### torchmetrics
**Short:** PyTorch metric library with distributed-safe accuracy, F1, AUROC and calibration implementations.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/deep-learning-framework @3

### TorchSSL
**Short:** PyTorch library of semi-supervised algorithms (FixMatch, FlexMatch, MixMatch) for learning from mostly unlabeled data.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/deep-learning-framework @3

### TPOT
**Short:** Tabular AutoML that searches pipelines by genetic programming and exports the winner as plain scikit-learn code.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @2

### trec_eval
**Short:** The standard TREC CLI scoring a ranked run against qrels for MAP, nDCG, P@k and recall.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/lexical-and-hybrid-search @2

### TruLens
**Short:** LLM/RAG evaluation library scoring the RAG triad: context relevance, groundedness and answer relevance.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/rag-and-document-processing @2, observability/tracing-apm-and-llm-observability @3

### USB
**Short:** Unified Semi-supervised learning Benchmark: reference FixMatch/FlexMatch/UDA/Mean Teacher baselines for fair comparison.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/labeling-and-synthetic-data @2, model-training/deep-learning-framework @3

### Vals AI
**Short:** Independent evaluation service publishing standardized model and agent benchmark re-runs with disclosed harnesses.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

### Vertex AI
**Short:** Google Cloud's managed ML platform: training jobs, pipelines, model registry, endpoints and hosted foundation models.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, inference/model-server @2, platform-delivery/cloud-platform-and-cost @2, ml-lifecycle/experiment-tracking-and-tuning @3

### Vertex AI Feature Store
**Short:** Google Cloud's managed feature store serving the same feature definitions online for inference and offline for training.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1

### Vertex AI Model Registry
**Short:** Google Cloud registry that versions trained models and their lineage, and hands them to Vertex endpoints.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @2, platform-delivery/cloud-platform-and-cost @3

### Vertex AI Training
**Short:** Google Cloud's managed training service: submit custom or distributed training jobs on managed CPU/GPU/TPU pools.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, model-training/distributed-training @2, platform-delivery/cloud-platform-and-cost @3

### Vertex Feature Store
**Short:** Google Cloud's managed feature store serving the same features online and offline with point-in-time correctness.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1

### VQAv2
**Short:** Visual question answering benchmark of open-ended questions over natural images, balanced against language priors.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

### W&B
**Short:** Weights & Biases: logs runs, metrics, gradients and artifacts, and runs hyperparameter sweeps with a hosted dashboard.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/evaluation-and-benchmarks @3, observability/metrics-and-monitoring @3

### W&B Artifacts
**Short:** Weights & Biases feature that versions datasets and model files and records the lineage graph between them.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, data-movement/data-quality-and-lineage @2

### wandb
**Short:** Weights & Biases: hosted experiment tracking with live loss/grad-norm/LR charts, sweeps, artifacts and a model registry.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, observability/metrics-and-monitoring @3

### WebArena
**Short:** Benchmark of 812 web-navigation tasks in realistic self-hosted sites, scored on backend state rather than text.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @2

### Weights & Biases
**Short:** Hosted experiment tracker: logs runs, loss curves and artifacts, runs hyperparameter sweeps, versions models.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/evaluation-and-benchmarks @2, observability/tracing-apm-and-llm-observability @3, ml-lifecycle/ml-platform-and-pipelines @3

### whylogs
**Short:** Data-logging library emitting statistical profiles of datasets for drift detection; vendor is winding down.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, data-movement/data-quality-and-lineage @2

### WikiText-103
**Short:** Standard ~100M-token Wikipedia language-modelling corpus used for perplexity benchmarks at realistic scale.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

### WizardLM Evol-Instruct
**Short:** Method that iteratively rewrites seed prompts into harder, more complex instructions for SFT data.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/fine-tuning-and-peft @2

### yellowbrick
**Short:** scikit-learn-compatible visualizers for model selection: elbow and silhouette plots, ROC, confusion matrices.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/classical-ml-and-boosting @2

### τ²-bench
**Short:** Agent benchmark for policy-following dialogue: dual control with an LM user simulator, scored by pass^k at k=1-4.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @3

### τ³-bench
**Short:** Agent benchmark for policy-following dialogue: dual control, an LM user simulator, and pass^k scoring at k=1-4.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @3
