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

You push records into a dataset -- prompts, model outputs, response pairs, spans to tag -- humans label or rank them in a web UI, and you pull the curated result back as a dataset for training or evaluation. It keeps who labelled what, supports several annotators on the same record, and exposes the disagreement set so a third person can adjudicate.

Typical jobs are collecting preference pairs for DPO, filtering synthetic data, and running two-rater workflows where inter-annotator agreement is the quality signal. Reach for it when human feedback has to be repeatable and auditable; a handful of examples does not justify standing it up.

### Arize
**Short:** SaaS ML/LLM observability: production drift detection, performance tracing and SHAP-based explanation logging.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, observability/tracing-apm-and-llm-observability @2, applied-ml/interpretability-fairness-and-causal @3

You log production predictions with their features, and later the ground-truth labels when they arrive; Arize joins them, compares the live feature and prediction distributions against a training or prior-window baseline to surface drift, and lets you slice performance by cohort to find the segment where the model actually broke. It stores SHAP-style attribution values alongside, so a drifting feature can be checked against how much the model was leaning on it.

Its open-source companion, Phoenix, covers the LLM side — OpenTelemetry-based tracing of spans, plus evaluation runs over them — and can be self-hosted. Reach for it when models are in production long enough that silent degradation is the real risk; at small scale a scheduled job computing PSI against a stored baseline covers most of the value.

### Arize AI
**Short:** Commercial ML and LLM observability platform: feature/prediction drift, tracing, explainability, retraining triggers.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, observability/tracing-apm-and-llm-observability @2, ml-lifecycle/evaluation-and-benchmarks @3

You log inference records -- features, predictions, and the ground-truth labels later, when they arrive -- and Arize compares production distributions against a training or prior-window baseline to surface feature drift, prediction drift, and performance decay once labels land. Its practical value is slice analysis: instead of one aggregate accuracy number it finds the cohorts where the model degraded, which is what turns an alert into an actionable retraining decision. Arize Phoenix is the open-source, self-hostable companion aimed at LLM tracing and evaluation, and runs without the hosted platform. Reach for it when a model is in production and label delay makes drift your only early signal; for pure LLM tracing, weigh Phoenix against alternatives like Langfuse.

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

The workflow is: version a dataset of inputs and expected outputs, write scorers (deterministic checks, or LLM-as-judge functions), then run an experiment that scores every row. The reason to use a platform rather than a script is the experiment diff -- it shows which individual rows a prompt change fixed and which it broke, and LLM changes are not monotonic, so an aggregate score that moved up two points routinely hides a set of newly failing cases. It also ingests production traces, so a bad real-world case can be promoted into the eval dataset instead of being described in a ticket. Reach for it when prompt or model changes ship often enough to need a CI gate; it is a commercial hosted product, so compare against self-hostable options when traces cannot leave your infrastructure.

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

Cleanlab takes out-of-fold predicted probabilities from a model you already trained and applies confident learning: where the model is confidently sure of a class other than the recorded label, that example is flagged and ranked by how likely the label is wrong. Because it consumes predictions rather than a specific model, it works with any classifier and needs no clean ground truth beyond the noisy labels you have.

The practical uses follow from that ranking. Spend a limited relabeling budget on the top of the list instead of sampling at random; audit a test set before trusting an accuracy number, since errors in the test set cap measured performance and mislead model selection; and filter pseudo-labels in a semi-supervised loop, where a confidently wrong label would otherwise be trained on and reinforced.

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

A loaded dataset is an Arrow table memory-mapped from disk, so a corpus larger than RAM is usable directly and `map()` transformations are cached to disk and skipped on a rerun - which is what makes iterating on a tokenization step tolerable. Streaming mode goes further and iterates a remote dataset without downloading it at all. The API is column-oriented: `map` with `batched=True` for tokenization, `filter`, `train_test_split`, and `push_to_hub` to share the result.

The one behaviour to understand is fingerprinting: the cache key is derived from the function you pass, so a changed closure variable can either reuse a stale cache or force a full recompute, and non-picklable functions disable caching silently. Reach for it for benchmark corpora and training-data preparation; for small in-memory tabular work, pandas or polars is simpler and less surprising.

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

Evaluations are written as tests: build an `LLMTestCase` with input, actual output and retrieval context, then assert it against metric objects with thresholds. Running under pytest means evals live in CI, fail a pull request the way a unit test does, and produce per-metric scores rather than a vibe. The RAG-specific metrics - faithfulness, answer relevancy, contextual precision and recall - are what let you tell a retrieval regression from a generation regression, and G-Eval lets you define a bespoke criterion in natural language.

Most metrics are LLM-as-judge, so they cost tokens and carry their own variance. Pin the judge model and its version, keep a fixed dataset, and treat a threshold that suddenly moves as possible judge drift rather than a product regression. Deterministic assertions are still better wherever the property can be checked in code.

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

Assignment and analysis are deliberately separated: your application or feature-flag SDK writes which variant each user saw into your own warehouse, and Eppo computes results by querying that data in place, so metric definitions sit beside the rest of your analytics and the raw event data never leaves. On top of that it runs the statistics teams most often get wrong unaided, including sequential and CUPED-adjusted tests, multiple-comparison handling, and sample-ratio-mismatch checks that catch a broken assignment before anyone reads the result. Reach for it once experiment volume outgrows hand-written SQL and a spreadsheet; a handful of tests a year does not need a platform.

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

You give it a reference dataset and a current one, and it computes drift per column -- choosing a statistical test appropriate to the column's type and cardinality -- plus data-quality checks (nulls, ranges, unseen categories) and, when labels are present, model performance metrics. Results come back as a report you can render to HTML for a human or dump as JSON to feed a metrics backend, and the same computations can be expressed as pass/fail tests so a batch pipeline gates on them. It is a library rather than a service: it runs wherever your job runs and stores nothing on its own, which is what makes it easy to add to an existing pipeline. Note that the report and preset API was reworked in the 0.7 line, so older snippets do not run unmodified.

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

Feature definitions are declared in Python and registered once. Feast then materializes them into an online store such as Redis or DynamoDB for millisecond lookups at serving time, while `get_historical_features` reads the same definitions from the offline store and joins them as of your label timestamps. That second call is the real reason it exists, because it is what prevents training-serving skew and the specific temporal leakage of joining a feature value computed after the event you are predicting. It is primarily a registry and serving layer rather than a compute engine, so the batch and streaming jobs that produce the feature values are still yours to build, schedule and monitor.

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

Fiddler ingests production inference logs alongside the training baseline and monitors the distance between them — feature drift, prediction drift, data-integrity violations — then attributes a change back to the features responsible using Shapley-style attributions computed per prediction. Because those explanations are stored at inference time, an individual scored record can be pulled up months later and justified, which is what regulated decisions such as credit or insurance pricing require.

Reach for it when the operational question is model quality rather than service health: the endpoint is up, the latency is fine, and the model has quietly stopped being right. Note the name collides with an unrelated HTTP debugging proxy.

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

The core object is a feature group -- a table of features registered with a primary key and an event-time column, written from Spark, Flink or plain Python -- materialised to both an offline store for training and a low-latency online store for serving. A feature view then selects features across groups and produces point-in-time-correct training data, which is the mechanism that stops a training set absorbing values that were not yet known at prediction time. Because the same view backs both paths, an online lookup returns the features the model was actually trained on, which attacks training/serving skew directly rather than by convention. Reach for it when several models share features and skew is a real risk; a single model with a handful of features does not need a feature store, and managed options cover the same ground with less to operate.

### Hugging Face
**Short:** The model and dataset hub plus its Python ecosystem; the default source for pretrained weights and export tooling.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, ml-lifecycle/experiment-tracking-and-tuning @2, applied-ml/nlp-and-text @3, model-training/deep-learning-framework @3

The Hub is a hosting service where models, datasets and demo Spaces live as git repositories with large files in LFS, each with a model card, a licence and revision history. Around it sits the Python ecosystem that pulls from it: `transformers` for model and tokenizer classes, `datasets` for loading and streaming corpora, `peft` for adapter finetuning, `accelerate` for device placement and distributed launch, `optimum` for exporting to inference runtimes.

In practice it is both where you get a pretrained checkpoint and where you publish your finetune. Two things matter in production: pin a revision, because a repository can change under you between deploys, and read the licence on the model card rather than assuming, since it varies per model and some forbid commercial use.
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

Every model, dataset and Space is a Git repository with large files in LFS, which means a checkpoint has commits, branches and tags like code. `from_pretrained("org/name", revision=...)` resolves the repo, downloads only the files that framework needs, caches them locally, and can pin to an exact commit; the `huggingface_hub` library and `hf` CLI expose the same operations for uploading, and repos can be private or gated behind accepting a license.

It is where LoRA adapters, fine-tuned checkpoints and evaluation datasets are published and versioned, and the safetensors format is preferred there because it loads without executing pickled Python. In production, pin a revision and mirror the artifacts into your own storage: a container that downloads weights from the public internet at startup has made someone else's availability your availability.

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

You describe the search space with distributions such as `hp.uniform` and `hp.choice`, hand `fmin` an objective that returns a loss, and its tree-structured Parzen estimator models which regions produced good losses and samples where improvement looks likely — so trials concentrate near promising configurations instead of walking a grid. A `Trials` object records every evaluation for later inspection, and the search can be spread across workers through a shared store.

It is a reasonable choice for a model with a modest number of continuous or conditional hyperparameters. Newer tuners add early pruning of hopeless runs and better dashboards, so pick Hyperopt when its API is already what your codebase uses.

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

Kubeflow is a family of Kubernetes operators rather than one product. Pipelines compiles a Python-defined DAG into a workflow whose steps run as pods; the Training Operator turns a distributed PyTorch or TensorFlow job into a single custom resource that creates the worker pods and handles their rendezvous; Katib runs hyperparameter searches as jobs; notebooks are provisioned per user. Because everything is a custom resource, ML workloads schedule through the same quotas, node selectors and GPU allocation as the rest of the cluster.

Adopt it when you already operate Kubernetes and want ML work to live there too. If you do not, the operational surface is large and a managed platform will cost less of your time than running this one.

### Kubeflow Pipelines
**Short:** Kubernetes-native ML pipeline orchestrator: containerized DAG steps for training and batch inference.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, data-movement/workflow-and-durable-execution @2

A pipeline is Python that composes containerized components into a DAG; the SDK compiles it to a spec, the backend runs each step as a pod on Kubernetes, and inputs, outputs and artifacts are tracked per run so a result can be traced back to the code and data that produced it. Step-level caching skips work whose inputs have not changed, which matters when only the last stage of a long training pipeline is being iterated on.

Reach for it when training and batch inference already live on Kubernetes and you want reproducible, parameterized runs rather than a notebook someone re-executes. It brings the entire Kubernetes operational surface with it, so for a small team a lighter orchestrator is usually the better trade.

### Label Studio
**Short:** Open-source multi-modal annotation platform: labeling UIs, multi-rater assignment, agreement reporting and adjudication.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, ml-lifecycle/evaluation-and-benchmarks @3, applied-ml/vision-speech-and-multimodal @3

The labeling interface is declared in a small XML-style config, so a bounding-box task, span tagging, audio segmentation or a side-by-side preference comparison for LLM outputs is a template change rather than a different tool. Tasks are imported from cloud storage or the API, assigned to several annotators, and exported in the formats trainers expect - COCO, YOLO, CoNLL, JSON. An ML backend hook lets a model pre-annotate so humans correct rather than start from blank.

The features that matter beyond the UI are the ones around disagreement: multi-rater assignment, inter-annotator agreement, and an adjudication queue for the items raters split on. Reach for it when label quality is the bottleneck on model quality, which is more often than teams assume - a taxonomy that two careful raters interpret differently will not be rescued by more data.

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

You write a flow as a Python class whose steps are methods; anything you assign to `self` becomes a versioned artifact, so every run is inspectable afterwards and a failed run can be resumed from the step that broke instead of from the top. Decorators move a single step onto a bigger machine, a container image or a Kubernetes or Batch backend without changing the code.

Reach for it when data scientists need reproducible, scalable pipelines without learning the infrastructure underneath. It is a framework for data and ML workflows, not a general scheduler for services, and it assumes a cloud account it can store artifacts and run steps in.

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

A run records parameters, metrics, tags and artifacts against an experiment, and autologging hooks the common libraries so a scikit-learn or XGBoost fit captures its hyperparameters, metrics and the fitted model with no extra code. The Model Registry then gives a trained artifact a name, versions and aliases such as `champion`, so what gets deployed is a registry reference rather than a file path somebody remembers.

Reach for it as soon as there are more than a handful of experiments — the value is being able to answer months later which configuration produced a given number. It is a tracking and packaging layer, not an orchestrator or a feature store; something else still has to run the pipeline.

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

A registered model is a name with numbered versions, and each version points back at the MLflow run that produced it, so anything in production traces to the code, parameters, dataset, and metrics behind it. Aliases such as `@champion` and `@challenger` are movable pointers that a serving job resolves at load time, which makes promotion a metadata change rather than a redeploy, and tags carry approval or validation state alongside.

It needs the tracking server backed by a real database and an artifact store, not the local-file mode. Reach for it when "what model is live, who approved it, and how was it trained" has to be answerable months later; if you deploy one model from one pipeline, a versioned artifact path in object storage may be all the registry you need.

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

You log metrics, parameters, artifacts and environment metadata from inside the training script, and each run becomes a queryable record you can compare against every other — which configuration produced which curve, on which data, at which commit. The payoff arrives months later, when a model in production has to be traced back to the exact run and inputs that produced it.

It competes with MLflow and Weights and Biases for the same job, so the choice usually turns on hosting model, retention and price rather than capability. Note the unrelated Amazon Neptune graph database shares the name.

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

An eval is a dataset of samples in JSONL plus a registry entry naming a template. Basic templates check the completion programmatically, by exact, inclusion or fuzzy match against a reference, while model-graded templates hand the completion and the criteria to a model and use its judgement. That split is the important idea: cheap deterministic checks where the answer is checkable, a judge only where it is not.

Reach for it as a reference for how to structure your own eval harness, and for the community registry when you want an off-the-shelf task. Most teams outgrow it, because the evals that actually catch regressions are specific to their product's failure modes and need their own graders and reporting.

### Optimizely
**Short:** Commercial experimentation platform for online A/B tests, feature rollout and result analysis.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, platform-delivery/ci-cd-and-release @2

You define an experiment with variations and a primary metric; the SDK assigns each visitor deterministically by hashing their id against the traffic allocation, so the same user always sees the same variation without a server round trip, and events flow back for significance analysis in the results view. The same targeting and rollout machinery drives feature flags, which means a flag, a staged rollout and an A/B test are one mechanism rather than three.

Reach for it when product managers and marketers need to run experiments without an engineer per test. What decides whether the results mean anything is outside the tool: a primary metric chosen before launch, a sample size computed rather than guessed, randomization at the right unit, and the discipline not to stop the test the first time the dashboard shows a win.

### Optuna
**Short:** Hyperparameter optimization framework: TPE/Bayesian search, Hyperband pruning, distributed studies over any loop.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @3

It is define-by-run: your objective function calls `trial.suggest_float` and friends as it executes, so the search space can branch with ordinary Python control flow -- only sample the tree depth when the trial chose a tree model. A sampler, TPE by default, proposes the next configuration from the history, and a pruner kills unpromising trials early using the intermediate values you report each epoch. A study can persist to a database so many workers search it in parallel.

Reach for it as soon as one trial is expensive enough that grid or random search wastes real money. For a handful of cheap parameters, a grid search is simpler and needs no extra dependency.

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

Prodigy is a scriptable annotation tool from the spaCy team that runs on your own machines. You write a recipe — a Python generator of examples plus a choice of interface such as manual span labelling, text classification or a binary accept/reject — and it serves a keyboard-driven UI tuned for speed, where most decisions are one keystroke. Because the recipe is code, the loop can be active learning: a model in the loop scores the unlabeled pool and shows you the examples it is least sure about first, so a fixed annotation budget buys more signal.

Reach for it when a small team needs a few thousand high-quality labels quickly and the data cannot leave your infrastructure. It is commercial and single-team by nature; a large outsourced labelling workforce is better served by a managed platform with reviewer workflows.
### Promptfoo
**Short:** CLI eval and red-team runner for prompts and models; declarative YAML assertions run in CI across model matrices.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/ai-safety-and-guardrails @2, devtools/testing-and-mocking @3

A YAML config names the prompts, the providers to run them against, and test cases with assertions — substring and regex checks, JSON schema validation, a JavaScript or Python function, or an LLM-graded rubric — and the CLI runs the full matrix, printing a pass/fail grid per prompt per model with the diffs visible. Because the config is a file in the repository, a prompt change arrives in review with its eval results attached instead of being merged on someone's impression.

It also has a red-team mode that generates adversarial inputs for injection, jailbreak and data-extraction attempts. Set it up early: the harness costs an afternoon, and without one you cannot tell whether a prompt edit was an improvement or a regression.

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

It scores a RAG pipeline from the tuple of question, retrieved contexts, generated answer and optionally a ground-truth answer, using an LLM judge for the parts that need reading comprehension. The metrics separate the failure modes, which is the point: faithfulness asks whether each claim in the answer is supported by the retrieved contexts, context precision and recall grade retrieval independently of generation, and answer relevance grades whether the response addressed the question at all. It can also synthesize question-answer pairs from your own documents so you have a test set before anyone has labelled one.

Reach for it early, when you need to know whether a bad answer came from retrieval or from generation. Treat the numbers as defaults to be calibrated: the judge model is itself a source of error, judged scores drift when you change the judge, and both cost and latency scale with the number of samples you evaluate.

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

A trial is a training function that reports metrics back; Tune places trials across a Ray cluster and a scheduler decides their fate, with ASHA and HyperBand killing weak trials early so compute flows to promising configurations, and Population Based Training mutating hyperparameters mid-run instead of restarting from scratch. Early stopping is where the win comes from, since a search that abandons bad trials after a few epochs explores far more of the space for the same GPU-hours. Search strategies come from plugged-in libraries such as Optuna, HyperOpt and BayesOpt, and results log to MLflow, Weights and Biases or TensorBoard. The Ray dependency is worth it when tuning spans machines; on a single box Optuna alone is simpler.

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

Roboflow covers the unglamorous half of a vision project: uploading images, annotating boxes or masks, splitting train and validation sets, applying preprocessing and augmentation as a saved recipe, and exporting the result in whatever layout your trainer expects — YOLO directories, COCO JSON, TFRecord and others. A dataset version is immutable and carries its augmentation settings with it, so a training run can be traced back to exactly the data and transformations that produced it.

Reach for it when labelling and dataset management are the bottleneck rather than modelling. Your images leave your infrastructure, which is the usual reason teams decide against it.

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

BLEU is not comparable across implementations, because the score depends on how the text was tokenized before scoring — the same translations can differ by several points. sacrebleu fixes that by owning the tokenization and the settings itself: you feed it raw detokenized hypotheses and references, and it prints a version signature alongside the score that says exactly which configuration produced it, so a published number can be reproduced.

It also implements chrF and TER for the same reason. Use corpus-level scoring rather than averaging sentence-level BLEU, which is a different and non-comparable quantity: n-gram precision behaves badly on a single short sentence, and the average of per-sentence scores is not the corpus score.
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

Token-level accuracy flatters a NER model because most tokens are `O`; seqeval scores whole entity spans instead, counting a prediction correct only when the type and both boundaries match, and reports precision, recall and F1 overall and per entity type. You feed it lists of tag sequences in BIO or BIOES form, one list per sentence.

Reach for it as the reported metric for any span-labelling task -- NER, chunking, slot filling -- including through the HuggingFace `evaluate` wrapper around it. A model that looks excellent on token accuracy and mediocre here is getting boundaries wrong, which is the error users actually notice.

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

The SDK evaluates flags and experiment assignments against a rule configuration and logs an exposure event each time, and the platform joins those exposures to your metrics to produce lifts with confidence intervals. Because assignment and analysis live in the same system, the common experiment mistakes it removes are analysing users who were never actually exposed and computing significance in a spreadsheet after the fact.

Reach for it when you want feature flags and experiment readouts in one place rather than exporting assignment logs into a notebook. The parts that remain your responsibility are choosing metrics that are not trivially gameable, sizing the experiment, and resisting the urge to stop it the first time a chart looks good.

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

You declare a feature as a transformation over a batch, streaming or request-time source, and Tecton compiles and operates the pipelines behind it: the backfill, the scheduled batch job, the streaming aggregation and the materialization into an online store, with the same definition producing point-in-time-correct training data. Streaming aggregations are the differentiator against a registry-only feature store, because a feature like "count of transactions in the last thirty minutes" is maintained for you, and that is exactly the shape fraud and real-time personalization models need. It is a commercial platform, so the tradeoff against an open-source store is buying operated pipelines and low-latency serving instead of building and running them.

### TensorBoard
**Short:** Visualization UI for training runs: loss curves, learning rate, gradient norms, activation histograms, embeddings.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

Training code writes event files through a `SummaryWriter` -- scalars, histograms, images, text, the graph -- and the server reads that directory and serves an interactive UI, so runs written into sibling subdirectories are overlaid on the same axes for comparison. Beyond loss curves the parts that earn their keep are the histogram and distribution views of weights and gradients, where a gradient norm collapsing toward zero or exploding is visible long before the loss says anything, plus the HParams dashboard for sweeps, the projector for embeddings with PCA and t-SNE, and the profiler for input-pipeline and GPU-utilisation bottlenecks. It works with PyTorch as well as TensorFlow and needs no account or hosted service. Reach for it as the zero-friction local option; it has no experiment database, no team sharing and no artifact versioning, which is exactly where hosted trackers take over.

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

Each metric is an `nn.Module` with `update()`, `compute()`, and `reset()` that accumulates state on the correct device and, under distributed training, all-gathers that state across ranks before computing. That is the correctness argument for using it: averaging per-batch F1 or AUROC is not the epoch's F1 or AUROC, and a hand-rolled metric in a DDP job silently reports only rank zero's slice.

It covers classification, regression, retrieval, segmentation, calibration such as expected calibration error, and text metrics, with `MetricCollection` to compute and log a group at once and per-task variants for multi-task heads. Reach for it in any Lightning or plain PyTorch loop instead of re-implementing metrics or moving tensors to the CPU for scikit-learn on every batch.

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

You wrap an application so its inputs, retrieved contexts and outputs are recorded, then attach feedback functions that score each record. The triad it is built around localizes failure rather than producing one opaque quality number: low context relevance means retrieval brought back the wrong material, low groundedness means the model asserted things the context did not support, and low answer relevance means it answered a different question.

Reach for it during iteration, when you are comparing prompt, chunking or retriever variants and need to know which stage a change actually improved. As with any judge-based evaluation, the scores are only as trustworthy as the judge, so check a sample of them against your own reading before treating a number as a gate.

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

You package training code as a container image or a Python distribution, submit a custom job describing the machine types and accelerators you want, and Vertex provisions the workers, runs the job, streams logs, writes checkpoints and artifacts to Cloud Storage, then tears the cluster down. For multi-worker runs it injects the cluster specification into each replica's environment, so a standard PyTorch or TensorFlow distributed launch works without you managing VMs or networking. Hyperparameter tuning jobs run the same container repeatedly under a search strategy.

Reach for it on Google Cloud when you need a burst of GPUs or TPUs for hours rather than a standing cluster, and when checkpoints must survive a preempted node. The tradeoffs are per-second accelerator pricing and non-trivial job startup time, which makes it a poor fit for a tight edit-run loop.
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

A run starts with `wandb.init()`, config and metrics are logged as the training loop proceeds, and the results land in a hosted project where runs can be compared, filtered and grouped -- with system metrics such as GPU utilisation, memory and power captured automatically, which is usually the first place a stalled job explains itself. Sweeps are the other half: you declare a search space and a strategy (grid, random or Bayesian) and agents on your own machines pull configurations and report back, so hyperparameter search is distributed without you writing a scheduler. Artifacts version datasets, checkpoints and evaluation outputs with lineage, so a model traces back to the exact data and code that produced it, and reports turn a set of runs into a shareable writeup. Reach for it when many runs or several people need comparing; it is a commercial hosted service, so if runs cannot leave your network, MLflow is the usual alternative.

### whylogs
**Short:** Data-logging library emitting statistical profiles of datasets for drift detection; vendor is winding down.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, data-movement/data-quality-and-lineage @2

Instead of shipping raw records, it computes a profile of each batch: quantile sketches of every numeric distribution, cardinality estimates, null and type counts, frequent items for categoricals. Profiles are small, privacy-preserving, and — the design point — mergeable, so you can profile per hour, merge to a day, merge across Spark partitions, and then detect drift by comparing two profiles rather than scanning two datasets.

That makes it usable where full data logging is impossible: monitoring features in a streaming pipeline, or comparing production inputs against a stored training baseline. Its commercial backer, WhyLabs, has announced it is winding down operations, so treat it as a pattern worth understanding — profile-and-compare rather than log-everything — rather than a dependency to adopt for new work.

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
