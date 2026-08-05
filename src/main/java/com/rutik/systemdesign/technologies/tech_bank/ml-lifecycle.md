# MLOps & evaluation — technology bank

<!-- tech-bank tier: ml-lifecycle -->

The 177 tools whose PRIMARY role — the first, best-weighted one — sits in
the **MLOps & evaluation** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### adk eval
**Short:** Google ADK CLI that replays an evalset of test conversations against trajectory and response-match criteria.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @2

An evalset is a stored file of sessions, each a sequence of user turns paired with the tool
calls the agent was expected to make and a reference final response. Replaying it scores two
things independently: whether the tool trajectory matched, and how close the final text is to
the reference. Separating them is the useful part, because an agent that reaches the right
answer down the wrong path is a latent bug.

Wire it into CI so a prompt or tool-schema edit fails the build rather than being judged on
three manual examples. Its limits follow from exact trajectory matching: when several tool
orders are equally valid it produces false failures, and response matching is a similarity
threshold, not comprehension. For rubric-graded open-ended output, a general eval framework
with LLM judges fits better.

### Agent Platform Feature Store
**Short:** Managed cloud feature store providing offline training features and low-latency online serving for production models.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1

Google Cloud's feature store, renamed from Vertex AI Feature Store. BigQuery stays the source
of truth rather than data being copied into a proprietary store: you register a feature group
over a table or view, define a feature view selecting the columns and entity key, and a
scheduled or continuous sync materializes it into a Bigtable-backed online store for
millisecond lookups. Training reads the BigQuery table directly, so there is one definition.

Reach for it on Google Cloud when features already land in BigQuery, which makes setup nearly
free. Two things to plan: sync frequency is your freshness budget and continuous sync costs
more, and embeddings are served by Vector Search rather than here. The legacy `featurestores`
API and optimized online serving are on a published sunset schedule, so check which generation
a tutorial targets.

### Agent Platform Pipelines
**Short:** Google Cloud's managed Kubeflow Pipelines runner, formerly Vertex AI Pipelines; serverless DAG execution with no cluster to operate.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, data-movement/workflow-and-durable-execution @2

It executes pipelines authored with the KFP SDK on Google-managed infrastructure, so the DAG definition is portable Kubeflow while the control plane, scheduling and autoscaling are not yours to run. Steps are containers, artifacts and lineage are tracked between them, and it integrates with the rest of the platform's registry and metadata services.

Reach for it when the team already writes KFP and the operational cost of a self-managed Kubeflow installation is the actual problem — that installation is substantial, and this removes it. The trade is the usual managed one: you inherit the platform's quota model and its version of the runtime rather than pinning your own, and portability holds at the SDK level rather than the deployment level.

### AgentBench
**Short:** Benchmark suite that evaluates LLMs acting as agents across interactive environments; single-agent, not multi-agent.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @3

Eight environments grouped by grounding type: an operating-system shell, a database, a
knowledge graph, a digital card game, lateral-thinking puzzles, an ALFWorld household
simulator, web shopping and web browsing. Each keeps its own native metric, and the headline
overall figure is not a percentage but a per-task score normalized across the evaluated models
and then averaged, which ranks systems rather than measuring capability.

Read it as history. Its leaderboard has been cold since 2025 and its model roster is entirely
superseded. What survives is the design lesson that a cross-environment average hides which
grounding a system is bad at, which is why later rosters keep the families separate. For a
current number pick a per-axis benchmark: OSWorld for GUI work, Terminal-Bench for the shell,
the tau-bench family for policy dialogue.

### AgentEval
**Short:** AutoGen-originated framework for multi-dimensional task-utility assessment of agent runs; community-maintained.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @3

Rather than a success flag, it derives the yardstick first: a critic model reads the task
description and proposes the dimensions a good solution would have, then a quantifier model
scores each run against each dimension. The output is a utility profile across several
criteria instead of one number, which is what open-ended agent tasks need when there is no
reference output to match against.

Reach for it when a binary check is meaningless and you still want something more structured
than reading transcripts. Freeze the generated criteria before you start comparing runs, or
the yardstick moves with the measurement. Both stages are model calls, so the scores inherit
judge bias and cost tokens per run. Where the task has a checkable end state, state-based
scoring beats any of this.

### Alibi Detect
**Short:** Broad drift, outlier and adversarial detection library: MMD, KS, chi-squared and classifier drift, TF and PyTorch.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, applied-ml/timeseries-and-anomaly @2, security/ai-safety-and-guardrails @3

Detectors share one shape: fit on a reference batch, then `predict()` on a live batch returns
a drift verdict and a p-value. Continuous features get Kolmogorov-Smirnov, categoricals
chi-squared, and high-dimensional inputs such as images or embeddings get maximum-mean-
discrepancy or a learned classifier detector that trains a model to tell reference from
current and reports whether it can. Dimensionality reduction before the multivariate tests is
part of the documented workflow. Outlier and adversarial detectors reuse the same interface.

Reach for it when drift is over unstructured inputs, where per-column tests do not apply. Two
costs: multivariate tests need a sizeable reference sample, and statistical significance is
not the same as mattering, so pair the alert with a performance signal where labels exist. For
tabular columns and a report a human reads, Evidently is less work.

### Alpaca farm
**Short:** Stanford simulation framework for instruction following: LLM-simulated preference feedback plus an evaluation suite.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/labeling-and-synthetic-data @2, model-training/alignment-and-rl @3

The simulator replaces the expensive half of a preference-learning loop. Instead of human
annotators ranking response pairs, prompted API models do it, with noise deliberately injected
so their agreement rate matches human inter-annotator agreement rather than being
unrealistically clean. Around that sit reference implementations of the learning methods and a
fixed instruction evaluation set, so a new method is compared under a fixed protocol at a
fraction of the cost.

Reach for it as a research harness for iterating on preference-learning methods before
spending on people. The limit is structural: the annotator is a model, so its biases become
the reward model's biases systematically, and the framework validates the ranking of methods
rather than absolute quality. A shipping product still needs human preference data at the end.

### API-Bank
**Short:** Benchmark grading whether a model calls an API when it should, picks the right one, and fills arguments correctly.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/tool-use-and-mcp @2

It separates the three abilities that a single pass-rate normally blurs together: deciding a call is needed at all, retrieving the right tool from a catalogue too large to fit in the prompt, and planning a sequence of calls. Each is scored against annotated dialogues with a defined set of tools, so a failure is attributable rather than just a lower number.

Reach for it when you need to know which stage of your tool pipeline is failing - a low score on retrieval and a low score on argument filling call for completely different fixes. It is a graded benchmark, not a live environment, so it says nothing about latency, partial failure or recovery.

### ARC-AGI-2
**Short:** Abstract reasoning benchmark of novel grid puzzles used to gauge frontier model generalization, not agent skill.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

Each task shows a few input-output grid pairs and asks for the output grid of a held-out
input; there are no tools and no natural language, so the transformation rule must be inferred
from the demonstrations alone. The set is split into public training and evaluation portions
plus semi-private and private ones, which is what keeps a leaderboard number measured on tasks
the solver never saw. Every task was solved by at least two people in a large human study,
with median human solve time far above the previous generation's.

Its distinguishing feature is that cost per task sits on the axis next to accuracy, which
makes it the sharpest public evidence that a scaffold can move a fixed model by tens of points
for tens of dollars. It measures novel rule composition and nothing else, so quoting it as an
agent result is a category error.

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

It runs the models itself rather than aggregating vendor claims, and publishes the
configuration alongside the number: the harness, the sandbox, the repeat count, the reasoning
effort setting. The output is three axes at once rather than a quality score alone, adding
measured output speed and time-to-first-token per hosting provider and price per million
tokens. That third dimension matters because the same open-weights model is served at very
different latency and cost depending on who hosts it.

Reach for it for a first-pass shortlist and for choosing a provider for an open-weights model.
Its limits are a public benchmark's: the tasks can leak into training data, the composite
quality index weights tasks in a way that may not match yours, and latency measured from their
client is not latency from your region. Decide with your own eval on your own traffic.

### Auto-sklearn
**Short:** Tabular AutoML that searches sklearn pipelines with meta-learning warm-start, SMAC Bayesian tuning and ensembling.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @2, ml-lifecycle/ml-platform-and-pipelines @3

It treats the entire scikit-learn pipeline as one joint configuration space -- imputation,
encoding, scaling, feature preprocessing, estimator and hyperparameters -- and searches it
with SMAC, a Bayesian optimizer over a random-forest surrogate that handles conditional and
categorical parameters. Two additions carry it: meta-learning warm-starts the search with
configurations that worked on similar datasets, and instead of discarding the trained
candidates it builds a weighted ensemble from them.

Reach for it as a strong tabular baseline under a fixed time budget when the alternative is
hand-tuning. The costs: the winner is an ensemble that is slow to serve and hard to explain,
its dependency pins are awkward and Linux-oriented, and a carefully tuned gradient-boosted
tree usually matches it for far less compute.

### AWS SageMaker
**Short:** AWS end-to-end ML platform: managed notebooks, distributed training jobs, model registry and autoscaling endpoints.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, inference/model-server @2, model-training/distributed-training @2, platform-delivery/cloud-platform-and-cost @3

It is a family of services behind one SDK rather than a single product. A training job is a
container image plus an S3 input channel and an instance type, run on hardware AWS provisions
and tears down, writing artifacts back to S3; a model is then registered and deployed as a
real-time, serverless or asynchronous endpoint, or run as a batch transform. Pipelines,
processing jobs and the feature store reuse the same job abstraction.

Reach for it when you are already on AWS and want managed training and serving without running
a cluster. The costs are real: endpoint instances bill continuously whether traffic arrives or
not, everything moves through S3 and containers so local iteration slows, and you still choose
instance types and volume sizes. For a single model, a container on ECS or a GPU VM is cheaper
and simpler.

### Ax
**Short:** Meta's adaptive experimentation platform: Bayesian optimization and bandit-driven A/B allocation over a BoTorch backend.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/evaluation-and-benchmarks @2, applied-ml/recommenders-and-graph-ml @3

It sits on BoTorch and adds the experiment bookkeeping around Bayesian optimization:
parameters with types and constraints, trials, an outcome per trial, and a generation strategy
that starts with quasi-random Sobol exploration before switching to a Gaussian-process model.
Because trials are records rather than function calls, it supports the ask-tell pattern where
results arrive days later -- an online A/B arm, a hardware measurement -- as well as an
in-process loop, and it handles multi-objective and outcome-constrained problems natively.

Reach for it when each evaluation is expensive and slow, which is where the experiment model
earns its complexity. For fast in-process hyperparameter search over a training loop, Optuna
is lighter, prunes bad trials and needs less ceremony; Ax's value is the trial bookkeeping and
the multi-objective machinery, not convenience.

### Azure Machine Learning
**Short:** Microsoft's managed ML platform; uses MLflow as its native tracking and model-packaging format.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, ml-lifecycle/experiment-tracking-and-tuning @2

The distinguishing choice is that it did not invent its own tracking API. The workspace exposes
an MLflow-compatible tracking URI, so code written against MLflow logs into it unchanged, and a
model in MLflow format deploys to a managed endpoint with no scoring script because the
platform reads the signature and the environment out of the manifest.

Around that sit the pieces a hosted platform is expected to bring: compute clusters, pipelines,
a managed feature surface, batch and online endpoints, and identity through the cloud's own
directory rather than a bolted-on plugin. Reach for it when the organisation is already on
Azure and the alternative is operating a tracking server, a database and a bucket yourself.
The tradeoff is the usual one, portability against convenience, softened here because the
artifact format stays open.

### baal
**Short:** PyTorch active-learning library using MC-dropout and BALD to pick the most informative samples to label.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/interpretability-fairness-and-causal @2

It wraps a trained PyTorch model so dropout stays active at inference and runs each input
several times; the spread across those stochastic passes approximates the model's epistemic
uncertainty without training an ensemble. Acquisition functions -- BALD, entropy, variation
ratios -- turn that per-example uncertainty into a ranking, and the loop labels the top batch,
retrains, and repeats.

Reach for it when labelling is the budget constraint and unlabelled data is plentiful. Two
costs: Monte Carlo dropout needs many forward passes per candidate, so scoring a large pool is
expensive, and pure uncertainty sampling picks near-duplicates unless the acquisition accounts
for batch diversity. Always run a random-selection baseline -- active learning fails to beat
it more often than the literature suggests.

### back-translation
**Short:** Text augmentation translating a sentence to another language and back, yielding a paraphrase for consistency training.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/nlp-and-text @3

Translate the sentence into a pivot language with one model and back with another; the round
trip loses the exact surface form but keeps the meaning, so the result is a paraphrase
produced with no paraphrase-labelled data. Sampling rather than greedy decoding on the return
leg, or chaining two pivots, widens the variation. The same trick runs the other way in
machine translation itself, where target-side monolingual text is translated backwards to
synthesize source-side pairs.

Reach for it to expand a small classification set or to build consistency-training pairs where
the model must predict the same label for both versions. The failure mode is label drift:
negation, named entities and numbers survive the round trip badly, so a "not covered" comes
back as "covered" and quietly poisons the label. Spot-check a sample, and expect noise rather
than paraphrase for low-resource pivots.

### BEIR benchmark
**Short:** Zero-shot information-retrieval benchmark of 18 datasets with a standardized nDCG@10 evaluation protocol.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/lexical-and-hybrid-search @2

It bundles existing retrieval datasets spanning fact checking, question answering,
duplicate-question detection, citation prediction, argument retrieval and biomedical search --
deliberately heterogeneous -- and forbids training on any of them. A model is indexed and
evaluated as-is, so the score measures transfer rather than fit, and everything reports
nDCG@10 against the provided relevance judgments so the numbers are comparable across the set.

Reach for it when picking an off-the-shelf embedding or reranking model with no in-domain
labelled data yet. Its known weaknesses: several sub-datasets have shallow judgments that
penalize a model for surfacing an unjudged relevant document, the average hides that a model
is excellent on one task and poor on yours, and it is old enough to sit inside training
corpora. Build a small in-domain judgment set before committing.

### benchmark_app
**Short:** OpenVINO's ground-truth throughput and latency CLI; reports stream count, batch shape and per-request timings.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, observability/profiling-and-performance @2, inference/compiler-and-runtime-optimization @3

It loads a compiled model and drives it in a loop with random or supplied input, but the value
is that it configures the runtime the way production would and then tells you what it chose:
the number of inference streams, the batch shape, how many infer requests are in flight, and
both throughput and per-request latency percentiles. Because it compiles through the same
`ov::Core` path your application uses, its number is the ceiling your code should approach --
a gap between them is your code's problem, not the model's.

Run it before optimizing anything, and run it under both the latency and throughput
performance hints, which pick very different stream counts. Remember what sits outside the
measurement: image decode, preprocessing and host-device copies are excluded, and in a real
pipeline those often dominate.

### bert_score
**Short:** Python package computing BERTScore, an embedding-similarity generation metric; use rescale_with_baseline for scale.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

It embeds the candidate and the reference with a contextual encoder, greedily matches each
candidate token to its most similar reference token by cosine similarity, and averages into
precision, recall and F1. Because matching happens on contextual embeddings rather than
surface n-grams, a legitimate paraphrase scores well where BLEU or ROUGE would not. Optional
IDF weighting downweights function words that match trivially.

Raw scores sit in a narrow band near the top of the scale and are not interpretable across
models or languages, which is exactly what `rescale_with_baseline` fixes by subtracting an
empirical baseline -- use it, and report the encoder checkpoint and layer, since changing
either changes the number. It measures semantic similarity to a reference and cannot judge
factuality; for a claim-level check use an entailment model or an LLM judge.

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

The construction is the whole trick. A human browses until they find an obscure but verifiable
fact, then writes the question backwards from that answer, layering constraints until it is
hard to search for; the question is discarded if a strong model answers it with or without
browsing, or if the answer shows up in top search results. The result is hard to solve and
trivial to grade, because the answer is a short string matched against a reference with no
judge in the loop.

What it measures is persistence rather than knowledge -- the large jumps come from scaffolds
that reformulate, follow leads sideways and backtrack, not from a bigger model. The inverted-
question method is worth stealing for your own harness: you get an automatic grader and a
guaranteed tool-use requirement in one step. Contamination profile: the live web cannot be
memorized, but the published question-answer pairs can be.

### calibration-library
**Short:** Post-hoc probability calibration tooling: expected calibration error, reliability diagrams, temperature/Platt scaling.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/interpretability-fairness-and-causal @2

Calibration is a separate property from accuracy: a model can rank correctly and still say 0.9
where it is right 60% of the time. The measurement bins predictions by confidence and compares
each bin's mean confidence against its observed accuracy; the weighted average of those gaps
is expected calibration error, and the reliability diagram plots the same data against the
diagonal. The fixes are post-hoc -- Platt scaling fits a logistic on the scores, temperature
scaling divides logits by one learned scalar, isotonic regression fits a monotone step
function.

Fit the calibrator on a held-out split, never on training or test data, or you measure the
calibrator. Reach for calibration whenever a probability feeds a threshold, an expected-cost
calculation or a downstream decision; if only ranking matters, AUC is unchanged and this buys
nothing. ECE itself depends on the bin count, so report the binning.

### Chatbot Arena
**Short:** Crowd-sourced LLM benchmark ranking models by blind pairwise human preference votes into Elo-style ratings.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

A visitor types a prompt, sees two anonymous responses, votes, and only then learns which
models produced them; votes feed a Bradley-Terry-style rating fitted over all pairings, which
is what places models that never faced each other on one scale. Anonymity is the mechanism
that removes brand preference, and the prompts are whatever real users bring rather than a
curated set.

It is the best available signal for subjective general helpfulness and the worst for anything
checkable -- there is no ground truth anywhere in the loop, and voters reward length,
formatting and confident tone, which is why style-controlled variants exist. Treat it as a
prior over which models to try, then decide with a task-specific eval. A gap of a few rating
points is inside the confidence interval.

### checklist
**Short:** Behavioral testing library for NLP: capability-sliced invariance and directional tests beyond aggregate metrics.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

It borrows unit-testing structure for NLP. A minimum functionality test checks one narrow
capability with simple constructed examples; an invariance test perturbs an input in a way
that must not change the prediction, such as swapping a name or adding a typo; a directional
expectation test perturbs it in a way that must move the prediction a known direction, such as
appending a negative clause. Templates and masked-language-model fills generate many instances
per test, and the report is a failure rate per capability rather than one accuracy number.

Reach for it when a model with good aggregate accuracy keeps failing on a specific pattern and
you need that failure visible and regression-tested. It costs authoring effort and it will
never find a failure mode you did not think to write down. The idea outlived the library --
the same three test shapes are easy to express in plain pytest.

### clean-fid
**Short:** Corrected FID implementation that fixes the image-resizing bugs which make generative-model scores incomparable.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

FID depends on how images were resized before Inception features were computed, and the common
libraries resize differently: an antialiased PIL filter versus raw bilinear or nearest
interpolation in a tensor library produce different aliasing, and the resulting scores can
differ by enough to reorder two models. This implementation pins the resizing path, the JPEG
handling and the feature extractor so numbers computed in different codebases are actually
comparable, and it records which configuration produced a score.

Use it whenever a FID will be compared against a published one; the difference is not noise.
It does not repair FID's other problems -- the covariance estimate is badly biased below
roughly ten thousand images so the score drifts with sample count, and Inception features
encode an ImageNet notion of content, so FID on faces, line art or medical images measures
less than it appears to.

### cleanlab
**Short:** Confident-learning library that finds mislabeled examples and bad pseudo-labels in a training set.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, ml-lifecycle/evaluation-and-benchmarks @3

Cleanlab takes out-of-fold predicted probabilities from a model you already trained and applies confident learning: where the model is confidently sure of a class other than the recorded label, that example is flagged and ranked by how likely the label is wrong. Because it consumes predictions rather than a specific model, it works with any classifier and needs no clean ground truth beyond the noisy labels you have.

The practical uses follow from that ranking. Spend a limited relabeling budget on the top of the list instead of sampling at random; audit a test set before trusting an accuracy number, since errors in the test set cap measured performance and mislead model selection; and filter pseudo-labels in a semi-supervised loop, where a confidently wrong label would otherwise be trained on and reinforced.

### ClearML
**Short:** Open-source MLOps suite combining experiment tracking, a model registry, pipelines and remote execution agents.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @2

It occupies the space between a tracker and a platform. Alongside run tracking and a registry it
ships agents that pull queued tasks onto machines you register, so a logged experiment can be
cloned, edited and rerun elsewhere from the web interface. That remote-execution loop is the
feature people actually adopt it for, and it is the thing a pure tracker deliberately does not
have.

The cost of that scope is that you are adopting an orchestration model as well as a metadata
store, which competes with whatever scheduler you already run. Reach for it when a small team
wants one self-hostable system for tracking, queueing and pipelines and has no existing
orchestrator to defend. If the orchestrator is already chosen, a tracker that refuses to own
execution fights it less.

### Comet ML
**Short:** Experiment tracking platform logging runs, metrics, code snapshots and diffs, with a model registry.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

The client patches the common training libraries so a run captures parameters, metrics, source
code, dependency list, git state and system metrics without explicit logging calls, then
streams them into a project where runs are compared. Beyond the shared tracker feature set,
the parts that distinguish it are the code and environment diff between two runs -- which
turns "why is this one worse" into an answer rather than an archaeology exercise -- and a
model registry with lineage back to the producing run.

Reach for it once several people run experiments and comparing them from memory has stopped
working. The choice against MLflow, Weights and Biases or Neptune usually turns on hosting,
retention and price rather than capability. It is a commercial service with a self-hosted
option; where runs cannot leave the network and budget is tight, MLflow is the default
fallback.

### Common Crawl
**Short:** Monthly open web crawl of ~2B pages (~345 TiB per snapshot); the raw corpus most LLM pretraining data is filtered from.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/rag-and-document-processing @3

Each monthly crawl is published as WARC files holding the raw HTTP responses, with WAT
metadata and WET plain-text derivatives and an index that lets you fetch only the pages you
want rather than the whole snapshot. It is genuinely raw: boilerplate, navigation, spam,
near-duplicates, adult content and a heavy English skew are all present, and because it
honours robots.txt, large parts of the web are simply absent from it.

Nobody trains on it directly. The work is the filtering pipeline -- language identification,
quality classification, document and paragraph deduplication, blocklists -- and that pipeline
is what distinguishes every corpus derived from it. Reach for it when you need web-scale text
and intend to build or adopt that pipeline; if you do not, start from an already-filtered
release and spend the effort elsewhere.

### conll18 scorer
**Short:** Official CoNLL 2018 script scoring Universal Dependencies parses: UAS, LAS, MLAS and BLEX.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

The 2018 shared task ran from raw text, so a system's tokenization and sentence segmentation
need not match the gold file at all; the scorer's first job is aligning the two by character
offsets before anything can be compared, which means an upstream tokenization error propagates
into the parsing score by design. On top of unlabeled and labeled attachment score it reports
MLAS, which additionally requires the morphological features and part-of-speech to be right,
and BLEX, which requires the lemma.

Use it rather than a hand-written attachment counter whenever a number will sit beside
published Universal Dependencies results -- the alignment rules and the definition of a match
are precisely where homemade metrics diverge. It scores dependency structure only; for
constituency trees the equivalent is evalb's bracketing F1, and the two are not
interchangeable.

### custom drift detectors
**Short:** Hand-written checks comparing serving feature and prediction distributions against training data.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1

The minimum viable version is a scheduled job. Store a reference profile of the training data
-- per-feature histograms or quantile sketches, category frequencies, null rates -- compute
the same profile over a recent window of serving traffic, and compare: population stability
index or a Kolmogorov-Smirnov statistic per numeric column, chi-squared or top-category share
for categoricals. Emit the results as ordinary metrics and alert on a threshold. Give the
prediction distribution the same treatment, since it moves before any label arrives.

This is usually the right first step: a few hundred lines, running where the data already is,
and it forces you to decide what a meaningful shift actually is. What you give up is what the
platforms sell -- multivariate drift over embeddings, automatic cohort slicing, and a
dashboard somebody else maintains. Adopt Evidently or NannyML once the per-column job alerts
on shifts nobody can act on.

### custom split infrastructure
**Short:** An in-house A/B assignment service that buckets users into experiment arms and keeps the assignment sticky.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

The core is a deterministic hash: bucket a user by hashing their id together with a
per-experiment salt, compare the bucket against each arm's allocation range, and any service
can compute the same assignment with no lookup and no chance of a user flipping arms between
requests. The salt is what stops two concurrent experiments correlating their assignments.
Around it you need an exposure log -- one event when the user actually saw the treatment, not
when they were merely eligible -- because analysis joins on exposure.

Build it when assignment has to happen inside systems a vendor SDK cannot reach, or when the
data must not leave. The parts teams underestimate are not the bucketing but the guardrails:
sample-ratio-mismatch detection, sequential testing so nobody peeks their way to significance,
and multiple-comparison handling. Statsig, Eppo and Optimizely exist mostly to supply those.

### CVAT
**Short:** Open-source annotation tool for labeling images and video with boxes, polygons, masks and tracks.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/vision-speech-and-multimodal @3

A task is a set of frames plus a label schema assigned to annotators, and the editor supports
boxes, polygons, polylines, points, cuboids and masks. What separates it from image-only tools
is the video model: an object is a track with keyframes and shapes are interpolated between
them, so annotating a hundred frames of a moving object costs a handful of keyframes.
Model-assisted labelling lets a detector pre-annotate for humans to correct, and exports cover
COCO, YOLO, Pascal VOC and its own XML.

Reach for it for detection, segmentation and tracking data that must stay on your
infrastructure -- it self-hosts with Docker. The cost is that you operate it, including
storage and the review workflow, and its multi-user project and quality-assurance features are
thinner than a dedicated labelling-operations platform's. For a small one-off set, a hosted
tool starts faster.

### Cybench
**Short:** Cybersecurity agent benchmark of 40 real CTF tasks scored on exact flag match, making it hard to game.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/ai-safety-and-guardrails @2, llm-apps/agentic-environments @3

The agent gets a container, the challenge files and a shell, and must recover a flag -- a
random string that exists only inside the solved challenge, so there is no partial credit, no
rubric and nothing for a judge to be lenient about. The tasks come from real capture-the-flag
competitions and span cryptography, web exploitation, reverse engineering, forensics,
exploitation and miscellaneous categories, and a subset ships with guided subtasks so a run
that never finds the flag still reports how far along the intended path it reached.

Reach for it as the capability half of a safety evaluation: it measures what a model can do,
where a refusal benchmark measures what it will do, and one without the other misleads. The
flag-match idea generalizes -- wherever your domain has an artifact only a completed action
could produce, score on that rather than on a judge. The tasks are public, so contamination
grows over time.

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

The benchmark half fixes everything except the data: a standardized training recipe, model
scale and evaluation suite, plus a large pool of unfiltered web text, so competing submissions
differ only in their filtering and deduplication choices and the comparison is genuinely about
curation rather than modelling. The DCLM-Baseline corpus is the output of the winning recipe,
released so others can train on it directly.

Reach for it when the open question is which filtering pipeline to run -- it is the rare setup
where a data decision gets an isolated, measurable answer instead of an argument. If you
simply need pretraining tokens, take the released corpus and skip the competition. Its
conclusions are tied to the scales and eval suite it fixes, so a filter that wins there is not
guaranteed to win at your scale.

### Deepchecks
**Short:** Open-source test suites for data and model validation: train/test/production comparison, drift and integrity checks.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, data-movement/data-quality-and-lineage @2, ml-lifecycle/evaluation-and-benchmarks @3

A suite is a collection of individual checks, each returning a value plus a pass/fail
condition, run over one dataset or a train/test pair: label and data leakage, train-test
distribution mismatch, duplicate and conflicting rows, feature-label correlation that is
suspiciously high, string mismatches, and model performance broken out by segment. Output is
an HTML report for a human and a machine-readable result so a pipeline can fail on it.

Reach for it as a gate between data preparation and training, where it catches the boring bugs
that cost the most -- a leaked target column, a split that overlaps, a category present only
at serving time. It is a battery of heuristics, so expect to disable checks that do not apply
and tune conditions; left at defaults it emits enough warnings to be ignored. For ongoing
column drift reporting in production, Evidently overlaps heavily.

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

A pipeline is a graph of steps: a generation step calls an LLM over an input dataset, a task
shapes the prompt and parses the response into structured columns, and later steps filter,
judge or pair the results into preference tuples. Because the LLM is an injectable component,
the same pipeline runs against different providers, and steps are batched, cached and executed
with configurable concurrency so a large run resumes instead of restarting.

The point is that synthetic data generation becomes a reproducible artifact -- a pipeline
definition rather than a notebook someone ran once -- which matters when the dataset's
provenance will be questioned later. Reach for it when building instruction or preference sets
at scale. The limits are synthetic data's own: the generator's output distribution is narrower
than real data, judge filtering inherits judge bias, and unfiltered self-generated text
degrades diversity fast.

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

It writes metrics, parameters and plots as plain files inside the repository rather than to a
tracking server, so an experiment becomes a Git commit: comparison reads those files across
commits, and the code, the data pointer and the numbers move together by construction. Inside
a DVC pipeline stage it also populates the stage's declared metrics and plots outputs, so the
same files drive both the pipeline and the comparison.

Reach for it when DVC already versions your data and you want experiment comparison with
nothing to host and nothing leaving the repository. The tradeoffs are scale and ergonomics: a
few hundred runs committed to Git is unpleasant, there is no live dashboard while a job is
running, and cross-branch comparison is clumsy. MLflow or a hosted tracker takes over once run
volume grows.

### Eleuther Harness
**Short:** EleutherAI's lm-evaluation-harness: the standard runner for open LLM benchmark suites.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

Its core abstraction is that a benchmark is a request type rather than a prompt. Multiple-
choice tasks are scored by comparing the model's log-likelihood of each candidate
continuation, while generative tasks call a generate-until request with stop sequences and
then apply a filter and a metric. That split is what lets one task definition run unchanged
against a local Hugging Face model, a vLLM server or a hosted API and still produce comparable
numbers.

Reach for it whenever a score has to be reproducible by someone else, and always report the
harness version, the task version and the few-shot count alongside it -- prompt formatting
differences move results by several points and account for most of the disagreement between
published tables. It measures a base model on public tasks, which are contaminable and say
nothing about your product.

### Epoch AI
**Short:** Research group publishing independent, harness-disclosed re-runs of model and agent benchmarks.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

A research organization whose output is data rather than software: a maintained database of
notable models with training compute, parameter counts, hardware and release dates; trend
analysis over those series; and independent benchmark runs with the harness and settings
published so a number can be reproduced. They also commission benchmarks of their own for
domains where existing sets have saturated.

Reach for them when a design document or a capability argument needs a figure that was not
produced by the model's own vendor -- the disclosed methodology is the product, not a better
score. Their training-compute figures are inferred from public information and carry stated
uncertainty, so treat them as well-argued estimates rather than measurements, and cite the
retrieval date because the series move.

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

It implements PARSEVAL: convert each tree to a set of labeled brackets, intersect the system's
set with the gold set, and report precision, recall and F1 plus exact-match sentence rate and
crossing-bracket counts. What actually counts is governed by a parameter file -- the standard
one deletes punctuation and certain labels and treats a set of category pairs as equivalent --
so two runs with different parameter files are not comparable numbers at all.

Use it, and name the parameter file, whenever a constituency-parsing score will sit beside
published work; those deletions are the difference between numbers that match the literature
and numbers that do not. Its blind spot is that bracketing F1 rewards getting the skeleton
right and is insensitive to which attachment errors hurt downstream. For dependency parses,
use the CoNLL scorers instead.

### Evals
**Short:** Generic name for a custom task-evaluation harness that scores model outputs against a fixed set of cases.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

The shape is the same whatever you build it on: a fixed set of cases with inputs and either a
reference output or a checkable property, a grader per case, and a runner producing per-case
results plus an aggregate. The engineering that matters is not the runner but the discipline
around it -- versioning the case set so a score means the same thing next month, keeping
per-case results rather than only the average, and diffing two runs case by case, because an
aggregate that improved routinely hides newly broken cases.

Build one as soon as prompts or models change more than occasionally; without it, a change is
judged on whichever three examples someone tried. Grade deterministically wherever the
property is checkable and reserve a model judge for the rest, since a judge costs tokens,
drifts when you swap it and needs calibrating against human labels. Promptfoo, DeepEval and
Inspect AI supply the runner so you only write cases.

### evaluate
**Short:** Hugging Face library giving one API over metrics such as BLEU, ROUGE, BERTScore and seqeval, loaded by name.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

Loading a metric by name fetches a versioned module from the Hub and returns a uniform
interface, so switching from ROUGE to BERTScore is a string change rather than a new
dependency with a new call signature. Metrics accumulate through an add-batch call and finish
with compute, which is what lets them be driven from inside a training loop, and a distributed
mode gathers partial state across processes before computing so a multi-GPU job does not
report rank zero's slice.

Reach for it to avoid re-implementing standard metrics and to keep numbers comparable across
projects. Two caveats: most modules are thin wrappers whose defaults may not match what a
paper reported -- for BLEU in particular, use sacrebleu directly and quote its signature --
and a metric loaded by name still needs its tokenization and configuration stated when you
publish the score.

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

It starts from a small seed set and rewrites each instruction with an LLM under a fixed menu
of operators: in-depth evolution adds constraints, deepens the question, makes it more
concrete, increases the reasoning steps or complicates the input, while in-breadth evolution
generates a new instruction in the same domain but a different direction. A response is then
generated for each evolved instruction and an elimination step drops the failures -- refusals,
information loss, near-duplicates of the parent. Survivors seed the next round, so difficulty
compounds.

Reach for it when you have a strong teacher model, a small seed set and a need for
instruction-tuning data spanning a difficulty range that human-written instructions do not
cover. The costs are distillation's: the student inherits the teacher's errors and style,
later rounds drift toward convoluted rather than harder, and the teacher's licence may forbid
your use. Filter, and read a sample by hand.

### Feast
**Short:** Open-source feature store: point-in-time-correct offline feature joins plus a low-latency online store for serving.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, applied-ml/recommenders-and-graph-ml @3, data-movement/data-quality-and-lineage @3

Feature definitions are declared in Python and registered once. Feast then materializes them into an online store such as Redis or DynamoDB for millisecond lookups at serving time, while `get_historical_features` reads the same definitions from the offline store and joins them as of your label timestamps. That second call is the real reason it exists, because it is what prevents training-serving skew and the specific temporal leakage of joining a feature value computed after the event you are predicting. It is primarily a registry and serving layer rather than a compute engine, so the batch and streaming jobs that produce the feature values are still yours to build, schedule and monitor.

### feature-engine
**Short:** scikit-learn-compatible feature engineering transformers: outlier capping, lag features, cyclic and rare-label encoding.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, model-training/classical-ml-and-boosting @2, applied-ml/timeseries-and-anomaly @3

Every transformer follows the scikit-learn fit/transform contract but operates on pandas
DataFrames and preserves column names, and each one takes the list of variables it applies to,
so a single object encodes three columns and leaves the rest alone. That removes most of the
`ColumnTransformer` plumbing a mixed-type table normally demands. Coverage runs from
imputation and outlier capping through rare-label grouping, target and ordinal encoding,
discretization, and datetime, lag and window features.

Reach for it when the data is tabular pandas and you want preprocessing inside a scikit-learn
`Pipeline`, so that every learned parameter -- a mean, a target encoding, a bin edge -- is
fitted on training folds only, which is what prevents leakage. The same fitted object at
serving reproduces training exactly. Recent scikit-learn releases absorb several of these
transformers natively, so check before adding the dependency.

### featuretools
**Short:** Automated feature engineering by deep feature synthesis, generating aggregates across related tables.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, model-training/classical-ml-and-boosting @3

You describe your tables as an entity set with the relationships between them, and deep
feature synthesis walks those relationships applying primitives: aggregation primitives
summarize a child table into its parent -- count, mean, max, time since last -- while
transform primitives operate within a table. Stacking them to a chosen depth generates
features such as the mean of the sum of a customer's order line totals, automatically, and a
per-row cutoff time restricts each calculation to data available before that moment.

Reach for it on relational data with an obvious parent-child structure, where hand-writing
hundreds of aggregations is the actual work, and use the cutoff-time mechanism, which is the
main defence against leakage here. The cost is a combinatorial explosion of mostly useless
columns needing selection afterwards, and features whose names describe them but whose
business meaning nobody can explain. A dozen expert-designed features often win.

### Fiddler AI
**Short:** Commercial ML observability platform: production drift and performance monitoring with explainability and fairness.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/drift-and-production-monitoring @1, applied-ml/interpretability-fairness-and-causal @2, observability/alerting-and-incident-response @3

Beyond drift and performance monitoring, its distinguishing surface is the compliance one:
attributions computed and stored per prediction at inference time, so an individual decision
can be explained months later, and fairness metrics across protected groups -- demographic
parity, equal opportunity, disparate impact -- as standing monitors rather than a one-off
offline analysis.

Reach for it where a model's decisions must be defended to somebody outside engineering:
credit, insurance, hiring, anything with a regulator or an appeals process. That is also its
cost profile -- a commercial platform, an ingestion path for every prediction, and storage for
the explanations. Where nobody will ever ask why one record scored the way it did, a scheduled
drift job plus performance dashboards covers the operational need for far less.

### Flower Datasets
**Short:** Library that partitions standard datasets into reproducible non-IID client splits for federated learning experiments.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/privacy-and-compliance @2

Federated experiments need data split across simulated clients in a way that is realistic --
meaning not identically distributed -- and reproducible, or results cannot be compared. The
library takes a standard dataset and applies a partitioner: IID as a baseline, a Dirichlet
partitioner whose concentration parameter controls how skewed each client's label mix is, a
pathological partitioner giving each client only a few classes, or a natural partition by an
existing column such as writer or user id.

Reach for it whenever a federated result is reported, and always state the partitioner and its
parameter -- non-IID severity dominates the accuracy of every federated algorithm, so an
unspecified split makes the number meaningless. It only handles partitioning; the federation,
the aggregation strategy and any privacy mechanism come from the framework around it. For
genuinely natural client boundaries, LEAF's datasets are the alternative.

### Frontier LLM API
**Short:** Shorthand for calling a top-tier hosted model (GPT/Claude/Gemini) as an annotator or teacher rather than a product.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, inference/model-server @3

The pattern is using a hosted top-tier model as a component of the data pipeline rather than
as the product: it labels examples that would otherwise go to humans, generates synthetic
training instances, judges outputs inside an eval, or acts as the teacher whose responses a
smaller open model is fine-tuned on. Prompt, schema and temperature become dataset parameters,
and the model version becomes part of the dataset's provenance.

Reach for it when labelling cost or turnaround is the bottleneck and the task is one a strong
model does reliably -- then verify against a human-labelled sample before trusting the rest.
Four costs: the labels inherit the model's blind spots systematically rather than randomly,
quality shifts when the provider updates the model so pin a version, your data leaves your
infrastructure, and provider terms may forbid training a competing model on the outputs.

### FS-Mol
**Short:** Microsoft few-shot molecular property prediction benchmark; a real drug-discovery task distribution for meta-learning.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/recommenders-and-graph-ml @3

The tasks are real drug-discovery assays drawn from a public bioactivity database, each asking
whether a compound is active against one protein target and each carrying only tens to low
hundreds of labelled compounds -- which is the actual situation when a new target appears.
Framed as N-way K-shot episodes, a method meta-trains across many historical assays and is
meta-tested on held-out ones, so the question is transfer across targets rather than fit to
one.

Reach for it when evaluating few-shot or meta-learning methods and you want a task
distribution with real structure instead of the synthetic episode splits of image benchmarks.
It is domain-specific: results depend on the molecular representation as much as the
meta-learning algorithm, and they do not transfer to vision or text. Related assays also share
chemistry, so held-out tasks are less independent than the episode framing implies.

### GAIA
**Short:** General-assistant benchmark for agents; Gaia2 runs tasks asynchronously against a clock in Meta's ARE platform.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

Questions are conceptually simple for a person and hard for a model because they require
actually doing several things -- searching, opening a PDF or spreadsheet, running a
calculation -- and answers are short strings scored by exact match after normalization, so
grading is binary and needs no judge. The 466 tasks split unevenly across three levels by the
number of steps and tools required, and human annotators scored around 92% overall against
single digits for tool-less models at release.

The trap is the headline number: it is a weighted average and the middle level is more than
half the benchmark, so a plain mean of the three per-level rates misreports it by points.
Report per level. The published question-answer pairs are contaminable, and the task waits
patiently while the agent thinks -- a real assistant's world does not, which is what the Gaia2
successor addresses.

### Gaia2
**Short:** Meta's general-assistant agent benchmark, run asynchronously against a wall clock inside the ARE platform.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @3

It runs inside a simulated mobile universe -- applications with tools the agent acts through
-- rather than posing web questions, and its scenarios are grouped by the capability they
stress: execution, search, adaptability, time and ambiguity, plus agent-to-agent and noise
conditions. The structural change is that scenarios run asynchronously against a clock. The
world keeps moving while the model thinks, so an agent that reaches the right answer too late
fails, and a slow accurate scaffold can score below a fast approximate one.

That makes it the benchmark to reach for when latency is part of correctness, a failure mode
structurally invisible wherever the task waits. It reports per-capability scores and
normalizes against LLM calls and output tokens, so the result is a frontier of accuracy
against spend rather than a leaderboard row -- read it that way, and expect the environment
itself to be the expensive part of running it.

### GDPval
**Short:** OpenAI benchmark of economically valuable expert tasks, used as frontier-model context rather than an agent test.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

Tasks are real professional deliverables -- a slide deck, a legal memo, an engineering
drawing, a financial model -- authored by experienced practitioners across dozens of
occupations in the largest US GDP-contributing sectors, with a subset open-sourced. Grading is
not accuracy: expert graders compare the model's deliverable against a human professional's in
a blinded pairwise comparison, so a score is a win-or-tie rate against a person.

Reach for it as the closest available proxy for economic value rather than for capability, and
state the scoring model when quoting it, because a number here is not comparable to an
accuracy anywhere else. Its limits are the cost and subjectivity of expert grading, and the
fact that a one-shot deliverable omits the iteration, context and accountability a real job
carries -- a strong result does not imply the work can be automated end to end.

### Google Vertex AI AutoML
**Short:** Vertex AI's managed AutoML for tabular and image models: upload data, get a trained and deployable model.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, ml-lifecycle/experiment-tracking-and-tuning @3, model-training/classical-ml-and-boosting @3

You supply a labelled dataset and a target column, choose a budget in node-hours, and the
service searches architectures and hyperparameters and hands back a trained model that deploys
to an endpoint or exports for edge use. For tabular data it evaluates several model families
and ensembles them; for image and text it fine-tunes managed backbones. Feature attributions
and evaluation metrics come attached to the result.

Reach for it as a fast credible baseline when nobody on the team is a modelling specialist and
the dataset is standard-shaped -- it usually beats a first hand-built attempt and tells you
quickly whether the signal exists at all. Past that the tradeoffs bite: node-hour billing on a
search you do not control, no visibility into the resulting model, and limited portability.
Once the problem matters, a gradient-boosted tree you own is cheaper to train, serve and
debug.

### GQA
**Short:** Visual question-answering benchmark for compositional spatial reasoning over scene graphs.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

Questions are generated programmatically from scene graphs -- objects, their attributes and
the relations between them -- using functional programs, so every question carries a known
reasoning chain and the distribution can be balanced to suppress the language priors that let
a model answer without looking at the image. Alongside accuracy it reports consistency, which
checks whether answers to logically related questions agree, plus validity, plausibility and
grounding.

Reach for it when the target is compositional spatial reasoning rather than general visual
question answering; the consistency metric in particular catches a model that is right by
association rather than by reasoning. The cost of the generated construction is templated,
unnatural language that a model can learn as a distribution in its own right, and the dataset
is old enough to sit well inside training corpora.

### H2O AutoML
**Short:** AutoML engine that searches tabular model families and builds stacked ensembles, with a Java scoring artifact.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @2, ml-lifecycle/ml-platform-and-pipelines @3

Rather than searching one enormous space, it trains a fixed menu of model families under a
time or model-count budget -- generalized linear models, random forests, extremely randomized
trees, gradient boosting, XGBoost, a feedforward network -- with a random hyperparameter
search over the tree models, then fits two stacked ensembles on the cross-validated
predictions: one over everything and one over the best of each family. A leaderboard ranks all
of them by your chosen metric.

Its real advantage is the deployment artifact: a MOJO is a self-contained scoring object a JVM
service loads with no H2O cluster behind it, which matters in Java shops where a Python model
is an operational problem. The costs are a cluster to run training, memory-hungry ingestion,
and a winning ensemble that scores slowly and explains badly -- the best single model on the
leaderboard is often the better production choice.

### HELM (Stanford)
**Short:** Stanford's Holistic Evaluation of Language Models: one harness running many models over many scenarios on a fixed metric set.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

Its argument is that a single accuracy number hides most of what matters, so it runs every model across a matrix of scenarios and reports a fixed set of metrics for each cell - accuracy alongside calibration, robustness to perturbation, fairness, bias, toxicity and efficiency. Because every model meets the same prompts under the same conditions, the comparison is like-for-like in a way that collecting published per-paper scores never is, and the per-cell results stay inspectable rather than collapsing into one figure.

Reach for it when you need a defensible breadth claim about a model, or when the question is a non-accuracy axis such as calibration or robustness that most leaderboards do not report at all. It is expensive to run in full and its scenario set lags the frontier, so for a specific capability a targeted benchmark answers faster. Unrelated to Helm, the Kubernetes package manager, which shares only the name.

### HLE
**Short:** Humanity's Last Exam: a very hard closed-ended reasoning benchmark used as frontier model context.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

Roughly 2,500 closed-book questions across a hundred-plus subjects, written and filtered by
domain experts specifically so that frontier models could not answer them at release. Formats
are short-answer and multiple-choice, some with images, and every answer is checkable against
an unambiguous reference so grading needs no rubric. There are no tools, no environment and no
trajectory anywhere in it.

Reach for it as a reference on raw closed-book expert knowledge and reasoning, and nothing
else. Scores climbed from low single digits at release into the low fifties within about a
year, which is a capability trend line rather than a statement about any deployed system.
Citing it to justify an agent architecture is the common error -- it says the model knows
things, not that a scaffold will notice a failed tool call and re-plan around it.

### HoneyHive
**Short:** LLM evaluation and observability platform: tracing, multi-dimensional rubrics, annotation queues and drift detection.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, observability/tracing-apm-and-llm-observability @2, ml-lifecycle/drift-and-production-monitoring @3

An SDK traces the application -- spans for retrieval, prompt, tool call and generation -- and
those traces become the raw material for everything else: a bad production run is promoted
into a dataset, evaluators run over that dataset as deterministic assertions or model-graded
rubrics scoring several dimensions rather than one number, and anything needing a person goes
into an annotation queue where human labels come back attached to the same records.

That loop -- production trace to dataset to evaluator to human review -- is what a homemade
harness usually lacks, and it is the reason to pay for a platform rather than write a script.
Reach for it when LLM changes ship often enough to need a regression gate plus a review
surface for non-engineers. It is commercial and hosted, so weigh it against Braintrust,
Langfuse and self-hosted Phoenix where traces cannot leave your infrastructure.

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

The libraries are separable and compose. `transformers` supplies model and tokenizer classes

behind a uniform loader, `datasets` memory-maps corpora as Arrow tables, `tokenizers` is the

fast Rust implementation underneath, `accelerate` handles device placement and distributed

launch, `peft` adds adapter fine-tuning, `trl` the preference-training loops, `optimum`

exports to ONNX Runtime, OpenVINO and TensorRT, and `safetensors` is the weight format that

loads without executing pickled Python.

The value is that a checkpoint, its tokenizer and its preprocessing config travel together, so

swapping models is a string change. The costs are equally concrete: `trust_remote_code=True`

executes arbitrary code from the repository, minor releases change behaviour often enough that

pinning is mandatory, and the convenience layer hides dtype and memory decisions that matter

at scale. For serving, an inference engine such as vLLM replaces `transformers` outright.

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

Each of the 164 problems gives a function signature and a docstring and asks the model to
write the body; the completion is executed against hidden unit tests, so correctness is
functional rather than textual. The standard metric is pass@k, estimated without bias by
generating n samples, counting the c that pass, and computing the probability that a random k
of them contains at least one -- which is why a reported pass@1 and pass@10 come from the same
run.

It is effectively saturated at the top and thoroughly contaminated: the problems and their
canonical solutions have been public since 2021 and sit in every pretraining corpus. Its tests
are also shallow, which is what HumanEval+ exists to fix by adding many more. Use it as a
smoke test, never as evidence -- for a current measurement, use a repository-level benchmark
with real tests.

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

An eval is four composable pieces: a Dataset of samples, a Solver chain turning a sample into
a model interaction -- a prompt template, a reasoning step, or a full tool-using agent loop --
a Scorer that grades the result, and a Task tying them together. Agent evals run their tools
inside a sandbox, a container per sample, so a model can be handed a shell without being
handed yours, and every run writes a structured log the bundled viewer replays turn by turn.

Reach for it for capability and safety evaluations where the trajectory matters and you need
to see exactly what happened rather than only the score; the log viewer is the feature people
stay for. It is MIT-licensed and self-hosted with no service behind it. For a lightweight
prompt regression gate in CI, promptfoo or DeepEval is considerably less machinery.

### jiwer
**Short:** Computes word and character error rate for speech transcripts with configurable text normalization.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

Word error rate is the Levenshtein distance between reference and hypothesis word sequences --
substitutions plus deletions plus insertions over the reference length -- and this computes it
quickly, returning the individual counts alongside so you can see whether a system deletes or
hallucinates. What actually decides the number is the transform pipeline composed before
alignment: lowercasing, punctuation removal, whitespace normalization, expansion of numbers
and contractions.

Report the normalization with the number or it means nothing; the same transcripts differ by
many points depending on whether casing and punctuation were stripped. Two properties to keep
in mind: WER is unbounded above because insertions can exceed the reference length, and it
weights every word equally, so a system that mangles every proper noun can beat one that drops
filler. For error analysis, read the alignment rather than the scalar.

### krippendorff
**Short:** Small Python package computing Krippendorff's alpha inter-annotator agreement on ordinal or ragged data.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/labeling-and-synthetic-data @2

Alpha is one minus the ratio of observed disagreement to disagreement expected by chance, so 1
is perfect agreement, 0 is chance level, and negative values indicate systematic disagreement.
The reason to prefer it over Cohen's kappa is generality: any number of coders, missing values
where not every coder rated every item, and different measurement levels -- nominal, ordinal,
interval, ratio -- handled through the distance function inside the disagreement calculation.

Reach for it when reporting annotation quality with more than two raters or ragged coverage,
which is the normal shape of a real labelling job. Read a low value as a signal about the
guidelines rather than the people: it usually means the label definitions are ambiguous, and
no volume of extra data rescues a taxonomy two careful raters read differently. The familiar
0.8 threshold is a convention, not a law.

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

The repeatable version has stages and each one is a decision. Sampling: random gives an
unbiased evaluation set while uncertainty or error-driven sampling gives training value at the
cost of a biased distribution, so do not draw both from the same pool. Then a written
guideline with worked edge cases, assignment of a slice to more than one annotator so
agreement is measurable, adjudication of the disagreements, and a versioned export recording
who labelled what under which guideline revision.

Build it when labels are produced continuously rather than once, and instrument agreement from
the first batch -- it is the only early signal that a taxonomy is broken, and relabelling
later costs far more than getting the guideline right. The usual failure is treating labelling
as a one-off procurement: the guideline drifts, the annotator pool turns over, and a model
trained on the new batch mysteriously regresses.

### LEAF
**Short:** Federated-learning benchmark suite with realistic non-IID client partitions (FEMNIST, Shakespeare, Sent140).
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, security/privacy-and-compliance @2

Its datasets come with natural client boundaries rather than an artificial split: handwritten
characters partitioned by writer, dialogue by speaking role, tweets by user -- so each
client's distribution differs the way real device data does, with different volumes, different
label mixes, and some clients holding a handful of examples. It ships reference
implementations and metrics reporting the per-client accuracy distribution rather than only
the mean.

Reach for it when a federated result needs a partition nobody can accuse you of choosing, and
report the tail of the client distribution: a method that lifts mean accuracy while making the
worst clients worse has failed at the thing federation is for. It is an older suite tied to
small models and short vision or text tasks; for flexible partitioning over arbitrary
datasets, Flower Datasets is the modern option.

### LLM Perf Leaderboard
**Short:** Public leaderboard benchmarking model throughput, latency and memory per hardware target and serving backend.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, inference/inference-engine @3

Its value is holding the workload fixed while varying the thing under test: the same model run
across serving backends and quantization settings on named hardware, reported as throughput,
latency and peak memory rather than quality. That answers what a model costs to serve rather
than how good it is, and because the harness and hardware are stated, two rows are comparable
in a way two vendors' claims never are.

Reach for it for first-pass sizing -- will this fit on this GPU, roughly what throughput to
expect from which backend -- and then stop trusting it. Its input and output lengths, batch
size and concurrency are not yours, and throughput at fixed sequence lengths bears little
relation to a real mix of prompt sizes. Benchmark your own traffic shape before committing to
hardware.

### LM-Eval Harness
**Short:** EleutherAI's standard harness for running language-model benchmarks with consistent prompting and scoring.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

Its job is to remove the free variables that make two published scores incomparable: how
few-shot examples are formatted and separated, whether the answer is chosen by scoring
candidate continuations or by generating text and matching, whether log-likelihoods are
length-normalized, and how results aggregate. Everything a task needs lives in one versioned
definition, so the same evaluation runs unchanged against a local model, a served endpoint or
an API.

It is the machinery behind most open-model leaderboards, which is why matching a published
number means matching the version and settings rather than merely the task name. Treat what it
produces as a comparison between models on public tasks -- those tasks are in pretraining
corpora, several are saturated, and none of them is your product. Keep a private eval for
decisions and use this one as the common yardstick.

### lm-evaluation-harness
**Short:** EleutherAI's standard harness for running LLM benchmarks (MMLU, HellaSwag, ARC) reproducibly across backends.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

It standardizes the parts that quietly change a score: prompt assembly, whether scoring uses
log-likelihood over candidate options or generation plus matching, length normalization, and
which few-shot examples are drawn. Backends cover local Hugging Face models, vLLM and hosted
APIs -- and API models can only run generative tasks, because log-probabilities over arbitrary
continuations are not exposed, which silently changes what is being measured.

Use it to compare models, not to estimate real-world quality. A published score is meaningful
only with the harness commit, the task revision and the shot count attached; the same model
and task can move several points between versions after a template fix. For a product
decision, run your own dataset -- the public suites are in every pretraining corpus by now.

### LongBench
**Short:** Standardized bilingual benchmark of long-context tasks measuring how models degrade as input length grows.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

It bundles long-input tasks -- single- and multi-document question answering, summarization,
few-shot learning, synthetic retrieval, code completion -- in English and Chinese, each with a
standard prompt and metric, so a model's degradation can be attributed to a task type rather
than to length alone. Because inputs are grouped by length, the interesting reading is the
slope: how far a score falls as the same task's input grows, which no advertised context
window tells you.

Reach for it when choosing a long-context model or validating a KV-cache compression or
eviction scheme, where the question is what long context actually costs in quality. Its
weakness is that averages hide catastrophic per-query failures -- a scheme that evicts the
tokens holding the answer scores fine on average and fails totally on the queries that matter
-- so pair it with a targeted recall eval on your own documents.

### math-verify
**Short:** Library that symbolically checks whether a model's math answer equals the reference, for grading and RL reward signals.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/alignment-and-rl @2

String comparison fails on mathematics for boring reasons: one-half written as a fraction, a
decimal or LaTeX are the same answer, and a model that is right gets scored wrong. This parses
the model's output -- extracting the answer from LaTeX or a boxed expression -- and the
reference into symbolic form, then checks mathematical equivalence rather than textual
identity, handling sets, intervals, matrices and numeric tolerance.

Reach for it as the grader in a maths eval and, more importantly, as the reward function in
verifiable-reward reinforcement learning, where every false negative is a wrong gradient
rather than merely a wrong score. The limits are parsing: an answer buried in prose with no
clear final form may not be extracted, and equivalence checking on complicated symbolic
expressions can be slow or inconclusive. Ask the model for a boxed final answer.

### Meta-Dataset
**Short:** Google's 10-dataset cross-domain few-shot classification benchmark, built to stress generalization beyond miniImageNet.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/fine-tuning-and-peft @3, applied-ml/vision-speech-and-multimodal @3

Instead of episodes drawn from one dataset it samples them across ten sources of very
different visual character -- natural images, handwritten characters, aircraft, birds,
textures, sketches, fungi, flowers, traffic signs and scene photographs -- and the episodes
themselves vary: the number of classes and the number of shots are sampled rather than fixed
at the usual five-way five-shot. Held-out sources make cross-domain generalization the actual
test.

Reach for it when a few-shot method claims generality rather than a number on one benchmark;
methods that look strong on a single fine-grained dataset routinely collapse here. Keep its
central finding in mind -- a well-trained feature extractor with a simple classifier on top is
a hard baseline for elaborate meta-learners. It is heavy to set up, with a nontrivial data
preparation pipeline before the first experiment.

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

It separates search from training code: a trial is your script reading hyperparameters through
the API and reporting intermediate and final metrics, a tuner proposes configurations, an
assessor stops unpromising trials early, and a training-service abstraction runs those trials
locally, across SSH machines or on Kubernetes. The architecture-search side reuses the same
loop with one-shot methods, and a model-compression toolkit for pruning and quantization sits
alongside.

The repository is archived, which is the decisive fact: no fixes, no support for current
framework versions, and dependency rot against modern PyTorch. Read it for the design -- the
tuner, assessor and training-service split is a clean way to think about distributed search --
and pick Optuna or Ray Tune for anything you intend to actually run.

### MLE-bench
**Short:** OpenAI benchmark of 75 Kaggle competitions measuring end-to-end ML engineering agents, with cheating detectors.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

The agent gets a competition description, the training data and a compute budget, and must
produce a submission file -- meaning it does the whole loop: read the task, build features,
train, validate, decide when to stop. Scoring goes against the competition's real historical
leaderboard, so the bronze, silver and gold thresholds are the ones thousands of humans were
actually measured against, and the headline is the fraction of competitions where the agent
medalled.

That inherited human distribution is the reason to run it: a medal is a calibrated unit rather
than an invented rubric, and it is a pattern worth copying anywhere your domain already has a
scored human population. The repository also ships rule-violation and plagiarism detectors,
because an agent with a shell and a network will fetch the winning notebook -- any benchmark
granting internet access has to check for that explicitly.

### MLflow
**Short:** Open-source ML experiment tracking, model registry and packaging; logs params, metrics and artifacts per run.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @2

Beyond tracking, the piece that earns its keep is the model format. A logged model is a
directory with an `MLmodel` file naming one or more flavors, so the same artifact loads as a
native scikit-learn object or through a generic `pyfunc` interface, packaged with the
environment it needs. That is what turns "serve this run's model" into a command rather than a
rewrite, and what lets a registry reference be handed to a deployment target that knows
nothing about the training framework.

The evaluation API extends the same idea to scoring, running a model against a dataset and
logging metrics and diagnostic artifacts into the run. Reach for the packaging layer when
models cross a team boundary and the receiving side should not have to reconstruct an
environment. It remains tracking and packaging -- something else orchestrates -- and the
tracking server needs a real database and artifact store once more than one person uses it.

### MLflow Model Registry
**Short:** MLflow's model lifecycle store: versions, aliases like @champion/@challenger, tags and promotion history.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @3

A registered model is a name with numbered versions, and each version points back at the MLflow run that produced it, so anything in production traces to the code, parameters, dataset, and metrics behind it. Aliases such as `@champion` and `@challenger` are movable pointers that a serving job resolves at load time, which makes promotion a metadata change rather than a redeploy, and tags carry approval or validation state alongside.

It needs the tracking server backed by a real database and an artifact store, not the local-file mode. Reach for it when "what model is live, who approved it, and how was it trained" has to be answerable months later; if you deploy one model from one pipeline, a versioned artifact path in object storage may be all the registry you need.

### MLflow Projects
**Short:** MLflow's packaging format for runnable code: an MLproject file declaring entry points, parameters and an environment.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1

An `MLproject` file names entry points, their typed parameters and an environment, and
`mlflow run` executes one against a local path or a git URI at a pinned commit. It solved a real
2018 problem, when containerising a Python ML job was genuinely difficult and reproducing a
colleague's script meant reconstructing their environment by hand.

Adoption today is near zero and the honest advice is not to start here. The environment problem
is solved by an image and the execution problem by an orchestrator, and every orchestrator
brings retries, scheduling, resource requests, secrets and observability that Projects never
had. MLflow itself moved on: Recipes, the successor abstraction, was removed in MLflow 3. What
survives is the useful half, passing the run and experiment ids into a job's environment so it
logs into a run its scheduler created.

### MLflow Tracking
**Short:** Open-source run tracking: logs params, metrics and artifacts to a SQL backend, with a linked model registry.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1

Storage splits in two, and understanding the split is most of operating it. The backend store
is a database holding runs, parameters, metrics and tags; the artifact store is object storage
holding files -- models, plots, datasets. Clients resolve a tracking URI to the server, but
artifacts are usually written directly from the client to object storage, which is why a
client with database access but no bucket credentials fails halfway through a logging call.

Run it against a real database and a bucket as soon as more than one person logs runs; the
default local-file mode has no concurrency story and cannot be shared. Reach for tracking the
moment experiments outnumber what you can hold in your head. It records what happened and runs
nothing itself -- no scheduling, no resource management -- so pair it with whatever executes
the jobs.

### MMBench
**Short:** Multilingual, multi-domain VQA benchmark with circular-evaluation scoring for vision-language models.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

Its contribution is the scoring rather than the questions. Circular evaluation asks the same
multiple-choice question several times with the answer options rotated and counts it correct
only if the model answers correctly in every rotation, which removes the two cheap wins in
multimodal benchmarking: a positional bias toward a particular option letter, and a lucky
guess. Questions are bilingual and organized into a defined hierarchy of perception and
reasoning abilities.

Reach for it when comparing vision-language models and you suspect option-order sensitivity is
inflating somebody's number; the rotation idea is also worth applying to your own
multiple-choice evals, where it costs only extra inference. The format's limits still apply --
multiple choice with a fixed option set is easier than open-ended generation, so a strong
score does not predict quality on free-form answers.

### MMMU
**Short:** College-level multi-discipline visual question answering benchmark spanning science, engineering and medicine figures.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

The questions come from college examinations, quizzes and textbooks across six broad
disciplines and dozens of subjects, and the images are not photographs -- they are diagrams,
charts, chemical structures, circuits, medical scans, musical scores and maps. Answering
requires reading the figure and applying subject knowledge together, which is what separates
models that describe images well from models that reason over them.

Reach for it when the application involves technical or scientific imagery rather than natural
scenes, since a model strong on general visual question answering can be weak here. Two
caveats: it measures domain knowledge as much as perception, so a low score may mean the model
does not know the chemistry rather than cannot see the diagram; and being drawn from public
exam material makes contamination plausible, with some questions answerable from text alone.

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

The benchmark runs one embedding model over many datasets grouped by task type -- retrieval,
reranking, classification, clustering, pair classification, semantic similarity,
summarization, bitext mining -- and the leaderboard shows per-task-type averages beside the
overall mean, along with model size, embedding dimension and maximum sequence length, which
are the numbers that actually decide serving cost.

Read the task-type column matching your use rather than the overall average; for retrieval-
augmented generation that is retrieval, and a model can top the aggregate while being mediocre
there. The failure mode is well documented: the datasets are public, the leaderboard is
competitive, and models are trained toward it, so small ranking gaps are noise plus
overfitting. Use it to pick three candidates, then measure recall on your own queries and
documents.

### mup
**Short:** Maximal Update Parametrization library so hyperparameters tuned on a small model transfer to a large one.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/deep-learning-framework @2, model-training/distributed-training @3

Under standard parametrization the optimal learning rate shifts as a model gets wider, so
hyperparameters tuned on a small proxy are wrong at scale and the tuning has to be repeated
where it is least affordable. Maximal update parametrization rescales initialization variance,
per-layer learning rates and output multipliers as a function of width so that activation and
update magnitudes stay stable as the model grows. The consequence is transfer: the optimum
found on a narrow model is the optimum on the wide one.

Reach for it before a large pretraining run, where tuning at a fraction of the width and
transferring is the difference between one sweep and none. Two costs: it must be applied
correctly across every layer including embeddings and the output head, and a subtle mistake
silently returns you to standard parametrization; and it transfers across width, not reliably
across depth, data or batch size. Verify that the small and large loss curves coincide early.

### NannyML
**Short:** Estimates a deployed model's performance without labels (CBPE); a covariate-shift tool, not a concept-drift one.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/drift-and-production-monitoring @1

Its confidence-based performance estimation builds an expected confusion matrix from the
model's predicted probabilities on unlabelled production data and derives an estimated AUC or
F1 from it, so a performance chart exists before any label arrives. Alongside that it runs
univariate drift per feature and multivariate drift by fitting PCA on the reference data and
watching reconstruction error on new data, which catches a change in the relationship between
features that no single column reveals.

Two conditions decide whether the estimate is trustworthy and both are explicit: the model's
probabilities must be well calibrated, and it assumes covariate shift only. Under concept
drift -- the relationship between features and target has changed -- its own documentation
says it does not work, and it will cheerfully report healthy performance while the model is
wrong. Use it to buy time until labels arrive, never as a replacement for them.

### NAS-Bench-201
**Short:** Tabular benchmark of pre-trained architectures so neural architecture search methods compare without retraining.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/experiment-tracking-and-tuning @2

It fixes a tiny cell search space -- a small directed graph whose edges each take one of five
operations -- yielding 15,625 architectures, then trains every one of them on three image
datasets and records accuracy at every epoch alongside training time and parameter counts. A
search algorithm therefore trains nothing: it queries the table, and an experiment that would
cost thousands of GPU-hours runs in seconds on a laptop.

Reach for it to compare search strategies fairly, since everyone queries the same numbers and
differences cannot come from training tricks or unequal budgets. The limitation is the flip
side of the design: the space is small and the results are tied to those datasets and one
training protocol, so a method that wins here is not proven at real scale. Random search being
a strong baseline on it is itself the lesson.

### NATS-Bench
**Short:** Tabular neural-architecture-search benchmark: pre-computed results for every architecture so NAS runs compare fairly.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

It extends the tabular idea along a second axis. One search space varies topology -- which
operation sits on each edge of a fixed-size cell -- and the other varies size, holding the
topology fixed while changing channel widths, which is the dimension a topology-only benchmark
cannot evaluate at all. Both spaces are exhaustively trained on the same datasets with full
training curves recorded, so a search queries results instead of training anything.

Reach for it when the method under test tunes width or capacity rather than only connectivity,
where a topology-only benchmark cannot separate it from a baseline. The same caveats apply as
to any tabular benchmark: small fixed spaces, one training protocol, small datasets, and
conclusions that do not automatically survive at production scale.

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

Built on Omniverse with a physics engine for rigid-body contact and ray-traced rendering, it
produces scenes whose ground truth is known by construction -- the simulator emits semantic
and instance segmentation, depth, surface normals, 2D and 3D bounding boxes and object poses
for every frame at no annotation cost. Its randomization layer varies textures, lighting,
camera pose and object placement so a generated set spans variation a real capture never
would, and a ROS bridge lets one robot stack drive simulated and physical hardware.

Reach for it when the data you need is dangerous, rare or expensive to capture -- failure
cases, unusual lighting, a production line that does not exist yet -- and for training
policies that would break real hardware while learning. The cost is the reality gap: a purely
simulated model degrades on real sensors, so plan on randomization plus real data for
fine-tuning, an RTX-class GPU, and real scene-authoring effort.

### Once-for-All
**Short:** Train one supernet, then extract per-device sub-networks without retraining; a NAS approach for edge deployment.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, inference/quantization-and-compression @2, inference/model-format-and-edge @3

One supernet is trained to contain many sub-networks varying in depth, channel width, kernel
size and input resolution, using progressive shrinking: train the largest configuration first,
then progressively admit smaller ones while distilling from the full network, so that every
sub-network is usable without further training. Deployment becomes a search -- given a
device's latency budget, an accuracy predictor and a measured latency table select the
sub-network, which is extracted directly.

Reach for it when the same model must ship to many hardware targets with different budgets and
the alternative is training and tuning a separate model per device. The costs concentrate up
front: supernet training is far more expensive than training one model, and the accuracy
predictor and per-device latency tables are extra machinery. For a single target, training one
right-sized model and then pruning and quantizing it is simpler and usually as good.

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

A study is a persistent object rather than a function call: back it with a relational database

and the search resumes after a crash, several machines attach to the same study and search it

concurrently, and the trial history stays queryable afterwards as a DataFrame. On top of that

sit multi-objective optimization returning a Pareto front rather than one winner, CMA-ES for

continuous spaces, user-defined constraints, and built-in plots for optimization history,

parameter importance and slices.

Parameter importance is the underused part -- it tells you which knobs mattered, so the next

search drops the ones that did not and spends the budget where it counts. Reach for the

database-backed setup whenever a search spans machines or more than a few hours. On a single

box with a handful of cheap parameters, an in-memory study or plain grid search is less to

think about.

### OSWorld
**Short:** Benchmark of real desktop GUI tasks in live Ubuntu VMs, used to evaluate computer-use agents end to end.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @2

Every task is a triple: a setup script putting the virtual machine into a known state, a
natural-language instruction, and a success function -- an actual program that inspects the
filesystem and application state afterwards. The agent sees screenshots or the accessibility
tree and emits mouse and keyboard actions with no API shortcut available; if an application
has no command line, it clicks. That execution-based verification is what makes the score
trustworthy compared with grading a transcript.

Two operational details matter when quoting a number. A handful of tasks have setup problems
and are commonly excluded, so runs are reported on slightly different denominators -- check
which. And the widely used verified split has largely stopped discriminating, with top entries
clustered within a couple of points of each other. For a score that still separates systems,
use the long-horizon successor.

### OSWorld 2.0
**Short:** Long-horizon successor to the OSWorld computer-use benchmark: real desktop VM tasks, still far from saturated.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @2

The same machinery -- real virtual machines, execution-based post-condition scripts -- with
the task length changed: around a hundred long-horizon workflows taking a median of roughly an
hour and a half of human time, where agents average several hundred steps against roughly
thirty in the original. It deliberately targets streaming interaction, environments that
change while the agent works, reasoning across sources, and state the agent must infer rather
than observe.

That length change is the point, and it reopened a gap the earlier version had closed: the
best reported agents sit around a fifth of tasks completed here while clustering in the
mid-eighties on the original, with nothing about the models different between the two numbers.
Read partial completion and tool-call counts alongside the binary score, because cost per task
varies several-fold between scaffolds at similar accuracy.

### Penn Treebank
**Short:** Classic annotated English corpus (~1M tokens) used as a parsing and language-modelling benchmark.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

Wall Street Journal articles annotated by hand with part-of-speech tags and full constituency
parse trees, plus function tags and traces marking movement and empty categories. Its
conventions became the field's defaults: sections 02-21 for training, 22 for development and
23 for test, evaluation by bracketing F1, and a separately preprocessed language-modelling
version with a ten-thousand-word vocabulary, everything lowercased and rare words collapsed to
a single unknown token.

Reach for it when comparing against decades of published parsing or perplexity numbers -- that
continuity is essentially its remaining value. Otherwise the limits are severe: small, one
genre of 1989 newswire, licensed rather than open, and a language-modelling preprocessing that
discards case, numbers and punctuation so its perplexity barely resembles modern language
modelling. Universal Dependencies treebanks and modern web corpora are what current work uses.

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

It extracts activations from a specific Inception v3 layer for the real and generated sets,
fits a multivariate Gaussian to each by taking means and covariances, and returns the Frechet
distance between those two Gaussians -- one number combining how far the mean feature moved
and how differently the two sets are spread. Lower is better, it has no absolute meaning, and
it only compares sets whose features were computed identically.

Two things break it silently. Too few images: the covariance estimate is badly biased below
roughly ten thousand samples and the score drifts with sample count, so report it. And
resizing: different interpolation paths change the number enough to reorder models, which is
what clean-fid exists to fix. It also inherits an ImageNet notion of content, so on faces,
line art or medical images it measures something only loosely related to quality.

### pytrec_eval
**Short:** Python wrapper around trec_eval computing nDCG, MAP, MRR and other offline retrieval metrics from qrels.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/lexical-and-hybrid-search @3

It binds the original C implementation, so the metric definitions are exactly the ones
published papers use, and exposes them as a Python call over dictionaries: relevance judgments
mapping query to document to grade, and a run mapping query to document to score. Because it
returns per-query values rather than only the aggregate, you can run a paired significance
test between two systems instead of comparing two averages and hoping.

Reach for it when a retrieval number has to line up with the literature, where the subtleties
-- how unjudged documents are treated, how score ties break, how nDCG discounts and normalizes
-- are exactly what a homemade implementation gets wrong. It only scores; producing the ranked
run is your job. For a friendlier API with built-in significance testing and rank fusion, ranx
covers the same metrics.

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

The evaluation loop is compiled, so scoring millions of query-document pairs across many
metrics takes seconds rather than minutes, which is what makes it usable inside a tuning loop.
Judgments and runs are first-class objects loading from TREC files, JSON or DataFrames;
evaluation takes a list of metric strings such as `ndcg@10`; and a comparison call runs
several systems at once with paired significance testing and multiple-comparison correction,
printing a table with the significance markers already applied.

It also implements rank fusion -- reciprocal rank fusion, CombSUM and relatives with score
normalization -- and can optimize fusion weights on a training split, which is the everyday
need in hybrid lexical-plus-dense retrieval. Reach for it during iteration and for tuning
hybrid search; when a number must match a published TREC result exactly, verify against
trec_eval, which remains the reference implementation.

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

A very large multilingual web corpus assembled from many crawl snapshots, whose design
decision is to ship unfiltered text alongside precomputed quality signals -- dozens of
per-document metrics covering natural-language heuristics, repetition, classifier scores and
deduplication information -- rather than one already-filtered corpus. You pick the thresholds
and produce the subset you want, which is the difference between inheriting somebody else's
curation and doing your own.

Reach for it when the filtering recipe is part of what you are researching, or when your
domain needs a different quality bar than a general-purpose filter applies. The cost is that
the raw pool is enormous and unusable as-is: expect substantial compute for filtering and
deduplication before a single training step, and expect worse quality than a curated release
if you take a naive threshold.

### Reward Bench
**Short:** Benchmark scoring how well a reward model ranks chosen over rejected responses across chat, safety and reasoning.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/alignment-and-rl @2

Each item is a prompt with a chosen and a rejected response; the reward model scores both and
the metric is simply how often it ranks the chosen one higher. Items are grouped into
categories -- general chat, deliberately hard chat pairs where the worse answer looks better,
safety, and reasoning including code and mathematics -- because a reward model can be near
perfect on easy chat pairs and near chance on exactly the ones that decide alignment outcomes.

Reach for it before spending on a preference-optimization run: a reward model that cannot rank
held-out pairs will not produce a better policy, and this check is far cheaper than
discovering it after training. Read the per-category numbers, not the average. Its limit is
that ranking accuracy is not the same as being a good optimization target -- a model can rank
well and still be trivially exploitable by a policy hunting its blind spots.

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

Robustness numbers were unreliable because authors evaluated with weak or badly tuned attacks
and reported inflated results. This standardizes the attack instead: models are evaluated with
AutoAttack, an ensemble of parameter-free attacks needing no per-model tuning, at a fixed
perturbation budget per threat model -- bounded in one norm, bounded in another, and common
corruptions. Submitted checkpoints go into a model zoo so results can be re-run rather than
trusted.

Reach for it as the sanity check on any claimed defence: a large gap between a paper's number
and its AutoAttack number is the classic signature of gradient masking rather than real
robustness. Its scope is the limit -- fixed norm-bounded perturbations on a few image datasets
is not the threat model of most deployed systems, and robust accuracy is bought with a
substantial drop in clean accuracy the leaderboard shows alongside.

### rouge_score
**Short:** Google's pure-Python ROUGE implementation for summarization overlap metrics; fast and dependency-light.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/nlp-and-text @2

ROUGE is recall-oriented n-gram overlap against a reference summary: unigram and bigram match
counts, plus a longest-common-subsequence variant so word order counts without requiring
contiguity. This implementation is pure Python with no Perl script to install, offers optional
Porter stemming, and distinguishes the plain subsequence variant from the summary-level one,
which splits on newlines and computes the subsequence per sentence -- forgetting to insert
those newlines is the usual reason a reported number comes out mysteriously low.

Reach for it because everyone reports it and comparability matters, not because it measures
quality. It cannot see a correct paraphrase and cannot see a fluent hallucination, so a high
score against a single reference mostly rewards copying the reference's wording. Pair it with
an embedding-based metric or a factuality check, and state the variant, the stemming setting
and the reference count.

### RULER
**Short:** Synthetic long-context benchmark with multi-hop and aggregation tasks; far more rigorous than needle-in-a-haystack.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/prompting-context-and-structured-output @2

Its tasks are generated rather than collected, so the same task can be instantiated at any
input length with difficulty held constant -- which means a score curve across lengths
isolates the effect of context size from everything else. Beyond retrieving a planted value it
includes multi-key and multi-value retrieval with distractors, variable tracking that chains
references across the document, and aggregation tasks requiring the model to collect or count
across the whole input, none of which a single-needle probe touches.

The number to take from it is effective context length -- the longest input at which a model
still clears a threshold -- which is routinely a fraction of the advertised window. Reach for
it when evaluating a long-context claim or a cache compression scheme. Being synthetic is also
its weakness: real long-document work is reasoning over prose, not tracking planted tokens, so
pair it with a natural benchmark.

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

A feature group is defined by a record identifier and an event-time column, and every write
lands in both stores at once: an online store keyed by identifier for single-digit-millisecond
lookups, and an offline store landing in S3 as Parquet, partitioned and catalogued so it is
queryable through Athena. Time travel and point-in-time-correct training sets are SQL against
that offline table, using the event-time column to exclude values that did not exist yet.

Reach for it when you are on AWS and want the two stores without building the plumbing. Be
clear about what it does not do: it stores and serves features, it does not compute them --
the transformation jobs stay yours -- and the point-in-time join is a query you write rather
than an API call. The online store bills per read and write and storing everything twice adds
up, so restrict it to features that genuinely need low-latency lookup.

### SageMaker Model Registry
**Short:** AWS registry versioning trained models into groups with approval status that gates deployment pipelines.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @2

Models are organized into package groups, and each registered version records the training
image, the artifact location, the inference container and the metrics from the job that
produced it. What makes it more than a catalogue is the approval status: a version sits
pending until approved, the approval change emits an event, and a deployment pipeline
subscribes to that event -- so promotion is an auditable state change rather than somebody
running a deploy command.

Reach for it when a human or an automated quality gate must sign off before a model reaches
production, and when an auditor will later ask who approved what. It is tightly coupled to the
rest of SageMaker, which is fine if you are already there and awkward if models are trained
elsewhere. For one model deployed from one pipeline, a versioned artifact path plus a metadata
file is enough.

### Scale AI
**Short:** Managed data-annotation vendor supplying human labels and preference data at scale.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1

A managed labelling vendor rather than a tool: you supply raw data and a specification, and it
supplies the workforce, the tooling, the multi-pass review and the quality metrics, returning
labels against an agreed accuracy bar. The work spans the ranges an internal team struggles to
staff -- sensor fusion and 3D annotation for autonomous driving, and expert preference and
demonstration data for model alignment, where the annotator has to be a domain professional.

Reach for it when volume, turnaround or required expertise exceeds what you can hire, and
treat the specification and the gold set as your responsibility rather than theirs -- quality
tracks guideline clarity more than it tracks the vendor. The costs are price per label, the
lead time of a taxonomy change, and sending data outside. For a few thousand labels, a small
internal team with Label Studio or Prodigy is faster and cheaper.

### scikit-optimize
**Short:** Bayesian optimization library for expensive black-box tuning; sequential model-based search over hyperparameters.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @3

It fits a surrogate model to the points evaluated so far -- a Gaussian process by default, or
a random forest or gradient-boosted trees for spaces with categorical and integer dimensions
-- and picks the next point by maximizing an acquisition function such as expected improvement
or lower confidence bound, trading exploration of uncertain regions against exploitation of
good ones. A drop-in search wrapper exposes that loop behind the same interface as
`GridSearchCV`.

Reach for it when each evaluation is expensive and the space is small and continuous, which is
the regime where a Gaussian-process surrogate is genuinely informative. Two limits: releases
have been infrequent enough that compatibility with current scikit-learn and NumPy needs
checking, and there is no early stopping of hopeless runs. Optuna covers the same ground with
pruning and active maintenance.

### scikit-plots
**Short:** Plotting helpers for scikit-learn results: ROC/PR curves, confusion matrices, calibration and lift charts.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/experiment-tracking-and-tuning @3

These are thin wrappers over matplotlib that take a fitted estimator or a pair of label and
score arrays and produce the standard diagnostic figures in one call -- ROC and
precision-recall curves, a confusion matrix, a calibration curve, cumulative gain and lift
charts, feature importances. Nothing is computed that scikit-learn does not already provide;
the value is purely in not rewriting plotting boilerplate.

Check what you are installing: the original package stopped receiving updates years ago and a
separately maintained fork carries the work forward, so pin deliberately. Reach for it for
quick exploratory evaluation. For anything that ships, scikit-learn's own display classes such
as `RocCurveDisplay` and `ConfusionMatrixDisplay` cover most of this with no extra dependency,
and yellowbrick offers a richer visualizer API for model selection.

### SEED-Bench
**Short:** 19K-question multimodal benchmark spanning 12 dimensions of image and video understanding.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

Roughly nineteen thousand multiple-choice questions with human-verified answers spanning a
dozen evaluation dimensions across both images and video -- scene understanding, instance
identity, attributes, spatial relations, counting, text in images, and for video, action
recognition, action prediction and procedure understanding. The multiple-choice format lets a
model be scored by comparing the likelihood it assigns each option, which sidesteps the
answer-parsing problems that plague open-ended multimodal evaluation.

Reach for it for a capability profile rather than one number -- the per-dimension breakdown is
the useful output, and it typically shows a model strong on scene-level questions and weak on
counting and spatial relations. The format's limits apply: likelihood-scored multiple choice
is easier than generation, and option ordering biases results unless you rotate the options as
circular evaluation does.

### Self-Instruct
**Short:** Bootstrapping method where an LLM generates and filters its own instruction-following training data from seed tasks.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/fine-tuning-and-peft @3

The loop starts from a small pool of human-written seed tasks. The model samples a few as
in-context examples and generates new instructions; a classification step decides whether each
is a classification task, which changes how the instance is produced -- input first for
generation tasks, output first for classification so the label distribution does not collapse
onto one class -- and then the input and output are generated. Filtering carries the method: a
new instruction survives only if its overlap with every existing one is below a threshold, and
survivors join the pool for the next round.

Reach for it when a base model must be turned into an instruction follower and no human
dataset exists. The cost is quality: generated instances are noisier than human ones, the
errors are systematic rather than random, and diversity is bounded by the generator's own
distribution however many rounds you run. Where a stronger teacher is available and the
licence permits, distilling from it produces better data for the same effort.

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

You write labelling functions over parsed documents -- gazetteer lookups, regular expressions,
heuristics over part-of-speech and dependency structure, an existing model's output -- each
producing candidate spans and each individually unreliable. A generative model, a hidden
Markov model over the token sequence, then estimates how accurate each source is from the
pattern of agreements and disagreements alone, with no gold labels, and emits one aggregated
annotation that trains an ordinary sequence tagger.

Reach for it when domain entities are describable by rules and lists but nobody will
hand-annotate thousands of documents; the aggregation is what turns contradictory heuristics
into usable labels. Two limits: the label model assumes source errors are largely independent,
so several functions built on the same wrong gazetteer reinforce each other confidently, and
coverage gaps are invisible -- an entity type no function fires on is silently absent. A small
gold set is still needed to evaluate.

### small-text
**Short:** Active learning library for text classification that picks the next examples to label, with transformers support.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, applied-ml/nlp-and-text @2

An active-learning loop over text classification with the pieces kept separate: a classifier
wrapper for scikit-learn, PyTorch or a transformer fine-tune, a query strategy that ranks the
unlabelled pool, and a loop that retrains after each labelled batch. Strategies span
uncertainty measures such as least confidence and prediction entropy, expected gradient
length, and embedding-based approaches like core-set selection that choose a diverse batch
rather than a redundant cluster of similar uncertain examples.

Reach for it when annotation budget is the binding constraint and unlabelled text is
plentiful. Two costs decide whether it pays: every round re-scores the pool and retrains,
which with a transformer makes batch size a real decision, and the resulting labelled set is
deliberately not a random sample, so it is biased and must never double as your test set. Hold
out a random test set and always compare against random selection.

### Snorkel
**Short:** Weak supervision: write labeling functions, then fit a label model denoising their votes into training labels.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1

A labelling function is a small Python function that votes a label or abstains -- a keyword, a
regular expression, a distant-supervision lookup, another model's prediction. Applied to
unlabelled data they produce a matrix of noisy, overlapping, conflicting votes, and the label
model estimates each function's accuracy and their correlations from the agreement structure
alone, with no ground truth, combining the votes into a probabilistic label per example. A
normal discriminative model trained on those labels generalizes past what the rules covered.

Reach for it when domain knowledge exists as rules and an expert's hour is better spent
writing twenty functions than labelling ten thousand rows -- and when the label definition is
still moving, since relabelling the corpus is just rerunning the functions. The assumption to
watch is conditional independence of errors; correlated functions make the label model
overconfident. A small hand-labelled development set is still mandatory.

### Snorkel Flow
**Short:** Weak-supervision platform: write labeling functions, denoise them with a label model, emit probabilistic labels.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, ml-lifecycle/ml-platform-and-pipelines @3

The commercial platform around the same idea, supplying the parts a library leaves to you: a
UI where subject experts author and inspect labelling functions with immediate coverage,
conflict and estimated-accuracy feedback, suggested functions mined from a handful of
annotated examples, a managed label model and training run, and an error-analysis view
pointing at the slice where the current functions are weakest so the next one is targeted
rather than guessed.

The pitch is iteration speed for people who are not engineers, plus an auditable path from
rule to label to model. It is an enterprise product with the pricing and deployment that
implies, so reach for it when programmatic labelling is an ongoing organizational process
rather than one project. For a single dataset labelled by engineers, the open-source library
in a notebook does the same work.

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

A computer-vision data platform where the annotation editor is one component among many: it
handles images, video, 3D point clouds and medical volumes, and everything else -- importers,
format converters, quality-assurance passes, model training and inference for pre-labelling --
is packaged as apps operating on the same project and dataset objects, with a Python SDK
exposing the same operations programmatically.

Reach for it when a vision project needs more than boxes on images: LiDAR annotation, medical
imaging, or a labelling operation with review stages and per-annotator statistics. The costs
are the usual hosted ones -- imagery goes to their infrastructure unless you run the
on-premise deployment -- and the app model is more machinery than a small project wants. For
straightforward image and video labelling that must stay in-house, CVAT is lighter.

### SWE-bench
**Short:** Benchmark of 2,294 real GitHub issues from 12 Python repos, scored by whether the agent's patch passes the repo tests.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @3

An instance is a real merged pull request reduced to its inputs: the repository at the parent
commit plus the issue text. The agent returns a patch and grading runs the repository's own
tests in two sets -- tests that failed before and must now pass, and tests that passed before
and must still pass -- so the score is binary and unarguable. Splits matter more than the name
suggests: the verified subset was filtered to well-specified issues and is deliberately
easier, and the same system reports several points apart depending on which split it ran.

The verified split was retired as a frontier metric in early 2026 for two compounding reasons:
task decay, with a majority of remaining failures traced to under-specified or wrong tests
rather than difficulty, and contamination -- models could reproduce gold patches from the task
identifier alone, with no issue text in the prompt. That generalizes to every static benchmark
whose answers live on GitHub. Quote the split, the scaffold and who ran it.

### SWE-bench Pro
**Short:** Long-horizon repository-repair benchmark: 731 public and 276 private real-world issues; successor to SWE-bench Verified.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @3

Its construction is a direct answer to contamination. The public set is restricted to
strong-copyleft repositories, so including them in a training corpus carries legal exposure,
and the private set comes from proprietary codebases that were never public at all -- a
held-out half that cannot have been memorized. The tasks are also longer: hours to days of
expert time, with patches spanning multiple files rather than the single-file fixes that
dominate the older set.

Reach for it as the current coding-agent measurement rather than the retired verified split.
Frontier systems scored dramatically lower here than on that split in the same period, and
that gap is roughly what removing the answer key from pretraining costs. The private set means
you cannot fully self-host the evaluation, and long multi-file tasks make runs expensive, so
budget scaffold cost as well as model cost and report the harness.

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

A task is a folder: a natural-language instruction, a Dockerfile building the environment
fresh for each attempt, and a test script verifying the end state. The agent gets a terminal
session and issues commands while the harness reads state back; nothing about its reasoning is
scored, only whether the tests pass. Because the action space is a whole shell, the failures
it surfaces are environment failures -- a failed install, a service that never started, a
wrong dependency version -- which dominate real deployments and which a patch-only benchmark
cannot see.

Every leaderboard row is an agent-and-model pair rather than a model, which makes the
scaffold's contribution explicit: the same model appears many times at very different scores,
and a neutral harness exists so you can hold it fixed. Quote the harness, the sandbox and the
repeat count with any score, or the comparison means nothing.

### TextVQA
**Short:** VQA benchmark whose questions can only be answered by reading text inside the image, so it tests OCR grounding.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

The images are chosen so the answer is written somewhere in the scene -- a street sign, a
product label, a book spine, a destination board -- so a model must detect the text, read it,
and then relate it to the question rather than merely recognizing objects. Answers are
open-ended short strings scored against ten human answers with the standard visual-question-
answering accuracy, which gives partial credit when only some annotators agree.

Reach for it when the application involves documents, receipts, signage or product photos,
where OCR grounding is the whole task and general visual question answering scores predict
nothing. Two things to know: a model with a strong external OCR pipeline can score well
without genuinely reasoning over the text, and the ten-answer metric is forgiving of spelling
and formatting differences in a way a production check would not be.

### The Pile
**Short:** EleutherAI's 825GB curated open text corpus for LM pretraining and for anti-forgetting mixes during fine-tuning.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/fine-tuning-and-peft @3

Rather than a single crawl it is a deliberate mixture of twenty-two sources of very different
character -- academic papers, code, legal filings, medical abstracts, books, subtitles,
mathematics, question-and-answer sites and a filtered web component -- each weighted and some
upsampled, on the argument that domain diversity matters more than raw volume for downstream
capability. Per-source provenance is preserved, so a mixture can be reconstructed or
reweighted.

Reach for it when a training run needs a domain mix rather than generic web text, and as the
anti-forgetting component in a fine-tune, where a slice of pretraining-style data stops a
model collapsing onto the new task. Two caveats: several constituent sources have faced
licensing and copyright challenges, so check before commercial use, and by current standards
its scale is small -- modern pretraining corpora are orders of magnitude larger.

### ToolAlpaca
**Short:** Smaller tool-use corpus generated by simulating API responses, aimed at giving compact models tool ability cheaply.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/tool-use-and-mcp @3

Its construction is the interesting part: rather than calling live services, a language model plays the API and produces plausible responses, so a multi-turn tool-use corpus can be generated across hundreds of tool specifications without credentials, rate limits or flaky endpoints. Fine-tuning small models on it produces a measurable jump in generalisation to tools never seen in training.

Reach for it as training data when you want tool-calling ability in a compact model and cannot afford real API traffic. The simulation is also the limit: responses are well-formed and cooperative in a way real APIs are not, so error handling, pagination and rate-limit behaviour have to be taught elsewhere.

### ToolBench
**Short:** Large-scale benchmark and dataset for tool-using LLMs, built from thousands of real REST APIs with multi-step call chains.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/tool-use-and-mcp @2

Instructions are generated over a large collection of public REST APIs, and solution paths are searched with a decision tree rather than a single greedy rollout, so the reference answers include multi-tool and multi-step chains rather than one call. Evaluation is pass-rate and win-rate against a reference model, judged automatically, which is what makes it runnable at scale.

Reach for it to compare tool-selection and argument-filling ability across models rather than to predict production behaviour. Two caveats govern its use: the underlying APIs drift and die, so scores across time are not comparable, and an automatic judge inherits its own model's bias.

### Torchmeta
**Short:** PyTorch library of episodic N-way K-shot dataloaders for meta-learning benchmarks such as Omniglot and miniImageNet.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/deep-learning-framework @2

Meta-learning needs data delivered as episodes rather than batches: each episode is a task
with its own small support and query set, sampled N classes at a time with K examples each.
The library wraps the classic benchmarks in dataloaders that emit exactly that structure with
the usual meta-train, meta-validation and meta-test class splits. It also provides a module
variant that accepts an external parameter dictionary, which is what makes gradient-based
methods like MAML expressible without hand-managing inner-loop weights.

Reach for it when implementing or reproducing episodic meta-learning and you want the data
plumbing and higher-order gradient mechanics to match published setups. It targets the small
classic vision benchmarks, so a real task distribution means writing your own dataset anyway,
and the second-order gradients MAML needs are memory-hungry enough that first-order
approximations are the common compromise.

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

A single codebase implementing the consistency-regularization family under one training loop
and one set of conventions: pseudo-labelling, Mean Teacher, MixMatch, ReMixMatch, UDA,
FixMatch and FlexMatch. The shared idea is that an unlabelled example is augmented weakly and
strongly, the weak view's confident prediction becomes the target for the strong view, and a
confidence threshold decides which unlabelled examples contribute -- FlexMatch's contribution
being to make that threshold per-class and adaptive rather than one global constant.

Its value is comparability. Semi-supervised results are notoriously sensitive to augmentation,
optimizer and training length, so a comparison across papers is usually a comparison of
implementations. Reach for it to get a fair baseline before claiming a new method helps. It
focuses on image classification with older backbones and very long schedules; the successor
benchmark extends the same setup across vision, text and audio with pretrained backbones and
far less compute.

### TPOT
**Short:** Tabular AutoML that searches pipelines by genetic programming and exports the winner as plain scikit-learn code.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, model-training/classical-ml-and-boosting @2

It represents a whole scikit-learn pipeline -- preprocessors, feature selectors, an estimator
and their hyperparameters -- as a tree and evolves a population of them with genetic
programming: crossover swaps subtrees between pipelines, mutation changes an operator or a
parameter, and cross-validated score drives selection across generations. The output is not a
black box; it exports the winning pipeline as readable Python you can inspect, edit and
commit.

That exported script is the reason to pick it over other AutoML tools -- you keep an ordinary
scikit-learn pipeline with no runtime dependency on the search tool. The cost is compute: an
evolutionary search evaluates thousands of pipelines with cross-validation, so budget hours
and set the population and generation limits deliberately. It can also overfit the
cross-validation score with elaborate pipelines, so validate the exported winner on data the
search never saw.

### trec_eval
**Short:** The standard TREC CLI scoring a ranked run against qrels for MAP, nDCG, P@k and recall.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/lexical-and-hybrid-search @2

The reference implementation, and effectively the definition, of the standard offline
retrieval metrics: give it a judgments file and a run file of ranked results in TREC format
and it prints per-query and aggregate values for mean average precision, nDCG at various
cutoffs, precision and recall at k, reciprocal rank and more. The details it pins down are the
ones implementations disagree on -- how unjudged documents are treated, how score ties break,
how nDCG discounts and normalizes.

Use it as the arbiter whenever a retrieval number will sit beside published results; a
homemade metric that looks right is the most common source of an unreproducible score. It is a
command-line C program working on files, which is awkward inside a Python loop -- pytrec_eval
binds the same code and ranx offers a faster API with significance testing, but both should
agree with this where it matters.

### TruLens
**Short:** LLM/RAG evaluation library scoring the RAG triad: context relevance, groundedness and answer relevance.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, search-retrieval/rag-and-document-processing @2, observability/tracing-apm-and-llm-observability @3

You wrap an application so its inputs, retrieved contexts and outputs are recorded, then attach feedback functions that score each record. The triad it is built around localizes failure rather than producing one opaque quality number: low context relevance means retrieval brought back the wrong material, low groundedness means the model asserted things the context did not support, and low answer relevance means it answered a different question.

Reach for it during iteration, when you are comparing prompt, chunking or retriever variants and need to know which stage a change actually improved. As with any judge-based evaluation, the scores are only as trustworthy as the judge, so check a sample of them against your own reading before treating a number as a gate.

### Unity Catalog
**Short:** Databricks' governance layer for data and models; a model is a three-level catalog.schema.name entity with grants and lineage.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, ml-lifecycle/experiment-tracking-and-tuning @2

What it changes for models is the identity. A registered model is not a flat name in a
per-workspace registry but a three-level `catalog.schema.model` object governed by the same
grants, audit log and lineage graph as the tables that trained it, so "who may promote this"
and "which table produced this" are answered by one system rather than three.

That also explains a discontinuity worth knowing: Unity Catalog-backed registries never
supported the older stage field, only aliases and version tags, so migration guidance that
still mentions stages does not apply. Reach for it when governance is a requirement rather than
a preference. The obvious limit is that it is Databricks-only, so a portability-first
architecture keeps the artifact format open and treats the catalog as one deployment target.

### USB
**Short:** Unified Semi-supervised learning Benchmark: reference FixMatch/FlexMatch/UDA/Mean Teacher baselines for fair comparison.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, ml-lifecycle/labeling-and-synthetic-data @2, model-training/deep-learning-framework @3

A semi-supervised benchmark that fixes the experimental protocol rather than proposing a
method: the same algorithms, augmentation policy, optimizer settings and evaluation across
vision, natural language and audio tasks, with several label budgets per dataset. Its
practical contribution is using pretrained backbones instead of training from scratch, which
cuts the compute of a full comparison by more than an order of magnitude and makes reproducing
the table feasible outside a large lab.

Reach for it to check whether a semi-supervised method's reported gain survives a controlled
setup -- many do not, and several appear only at very small label counts or with one specific
augmentation. It also answers the question that actually decides adoption: whether
semi-supervised training still beats simply fine-tuning a pretrained model on the labelled
subset, which on several tasks it does not.

### Vals AI
**Short:** Independent evaluation service publishing standardized model and agent benchmark re-runs with disclosed harnesses.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1

An independent evaluation service whose product is the methodology as much as the numbers:
models are run by a third party rather than their vendor, the harness, prompts and repeat
counts are disclosed, and the benchmarks lean toward professional domains where the public
sets are saturated or absent and where a domain expert has to author both the task and the
reference answer.

Reach for it when a model choice must be defended to somebody who will reasonably discount a
vendor's self-reported figure -- the independence and the disclosed harness are the point, not
a better score. The limits: a third-party number is still measured on their task distribution,
expert-authored professional benchmarks are small enough that a few points can be noise, and
nothing external substitutes for an eval on your own traffic.

### Vertex AI
**Short:** Google Cloud's managed ML platform: training jobs, pipelines, model registry, endpoints and hosted foundation models.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1, inference/model-server @2, platform-delivery/cloud-platform-and-cost @2, ml-lifecycle/experiment-tracking-and-tuning @3

Its unifying idea is that every artifact has a managed resource behind it -- a dataset, a
model, an endpoint, a pipeline job, an experiment -- with lineage recorded between them, so a
prediction traces back through the endpoint and model version to the pipeline run and the data
that produced it. Serving supports splitting traffic across model versions on one endpoint,
which makes a canary a configuration change, and request and response payloads can be logged
to BigQuery for later monitoring.

Reach for it when the organizational requirement is traceability and managed operation rather
than the lowest possible cost. The bill is the main surprise: endpoint replicas are
provisioned and charged continuously, a minimum replica count means an idle model still costs
money, and accelerator quota is per-region and slow to raise. A low-traffic model is cheaper
on Cloud Run; a busy training fleet is cheaper on your own cluster.

### Vertex AI Feature Store
**Short:** Google Cloud's managed feature store serving the same feature definitions online for inference and offline for training.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/ml-platform-and-pipelines @1

The current design keeps BigQuery as the source of truth rather than copying data into a
proprietary store: register a feature group over a table or view, define a feature view
selecting the columns and entity key, and a scheduled or continuous sync materializes it into
an online serving store for low-latency lookups. Training reads the BigQuery table directly,
so there is one copy of the feature logic and no separate offline store to keep in step.

Reach for it when your features already land in BigQuery, which is when the setup cost is
close to zero. Two things to plan: sync frequency is your freshness budget and continuous sync
costs more, and embeddings are served by Vector Search rather than here. An older generation
of this product is on a published sunset schedule, so check which API a tutorial is using
before following it.

### Vertex AI Model Registry
**Short:** Google Cloud registry that versions trained models and their lineage, and hands them to Vertex endpoints.
**Kind:** tech
**Lang:** *
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/ml-platform-and-pipelines @2, platform-delivery/cloud-platform-and-cost @3

A registered model is a resource with numbered versions and aliases, each version recording
the container image it serves with, its artifact location, the evaluation metrics attached to
it, and lineage back to the training pipeline run and dataset. Deployment reads from the
registry -- a version is deployed to an endpoint, and traffic can split across two deployed
versions of the same registered model, so promotion and rollback become traffic changes rather
than rebuilds.

Reach for it when "what is serving right now and what produced it" has to be answerable months
later, and when models cross a team boundary. It is Google Cloud specific and coupled to
Vertex's serving path, so it is the wrong choice when models deploy elsewhere. For a single
model shipped by one pipeline, a versioned artifact in Cloud Storage plus its metrics file is
enough registry.

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

The mechanism that matters is one definition serving two very different access patterns: a
training query reads the underlying table with the event-time column used to exclude values
that did not exist at label time, while a serving call fetches the latest row for one entity
key from the materialized online store in milliseconds. Because both derive from the same
feature view, the model at inference receives features computed exactly as they were during
training.

That correspondence is the reason to adopt a feature store at all -- training-serving skew and
temporal leakage are the two failures it exists to prevent, and both are silent. Reach for it
when your feature pipelines already end in BigQuery. It stores and serves rather than
computes: the jobs producing feature values remain yours to build and schedule, and the sync
interval is a freshness limit you have to design around.

### VQAv2
**Short:** Visual question answering benchmark of open-ended questions over natural images, balanced against language priors.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, applied-ml/vision-speech-and-multimodal @2

Its defining property is balancing: for most questions the dataset contains two similar images
with different correct answers, so a model that ignores the image and answers from the
language prior is wrong half the time by construction. Answers are open-ended short strings,
and accuracy is computed against ten human answers with credit scaled by how many agreed,
which tolerates legitimate disagreement rather than demanding one exact string.

Reach for it as the general visual-question-answering reference point and for comparability
with a long literature. It is old and saturated, its questions are short and largely
perceptual, and its answers come from a narrow vocabulary, so a strong score says little about
reasoning over diagrams, reading text in images, or following long instructions. Use MMMU,
TextVQA or a task-specific set for those.

### W&B
**Short:** Weights & Biases: logs runs, metrics, gradients and artifacts, and runs hyperparameter sweeps with a hosted dashboard.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, ml-lifecycle/evaluation-and-benchmarks @3, observability/metrics-and-monitoring @3

Beyond scalar metrics it captures what usually explains a bad run: GPU utilization, memory and
power sampled automatically, parameter and gradient histograms per layer when you ask it to
watch the model, and arbitrary media -- sample predictions, images, audio, tables -- logged
per step, so you can look at what the model actually produced at epoch 30 rather than at a
curve that flattened.

Reach for it when several people run training jobs and comparison from memory has failed, or
when you need to show somebody outside the team what happened. The gradient and system logging
costs a little overhead and a lot of storage if left on for every run, so enable it
deliberately. It is a commercial hosted service; where data cannot leave the network, the
self-hosted offering or MLflow is the alternative.

### W&B Artifacts
**Short:** Weights & Biases feature that versions datasets and model files and records the lineage graph between them.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, data-movement/data-quality-and-lineage @2

An artifact is a named, versioned collection of files with a type -- dataset, model,
evaluation output -- and uploads are content-addressed, so re-logging a set where one file
changed stores only that file and creates a new version rather than a second copy. Runs
declare which artifacts they consume and produce, and those declarations build a lineage
graph: from a model version you can walk back to the exact dataset version and run behind it.
Aliases such as `latest` or `production` are movable pointers a job resolves by name.

Reach for it when a production model must be traceable to its inputs, or when a dataset
changes often enough that "the training data" is ambiguous. For large data use reference
artifacts, which record checksums and point at your existing object storage rather than
uploading -- otherwise you pay to store a second copy of everything, which is the usual reason
teams abandon it.

### wandb
**Short:** Weights & Biases: hosted experiment tracking with live loss/grad-norm/LR charts, sweeps, artifacts and a model registry.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/experiment-tracking-and-tuning @1, observability/metrics-and-monitoring @3

The client buffers to a local run directory and syncs from a background process, so logging
does not block the training step, and a job on a machine with no outbound network can run in
offline mode and be uploaded afterwards. A sweep is a declarative search space and strategy
registered with the server; agents started on your own machines pull configurations from it
and report results back, so distributed hyperparameter search needs no scheduler of your own.

Reach for it when runs happen on machines you do not control -- a cluster, spot instances,
several people's workstations -- and must land in one comparable place. Watch two things:
logging inside a tight inner loop is real overhead, and step counts get confusing when several
call sites log independently, so log against a fixed step key. Resuming a preempted run
requires an explicit run id.

### WebArena
**Short:** Benchmark of 812 web-navigation tasks in realistic self-hosted sites, scored on backend state rather than text.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agentic-environments @2

Five self-hosted sites with real backends -- an e-commerce storefront and its admin portal, a
forum, a source-hosting instance and a map service -- plus a calculator, a scratchpad and an
offline encyclopedia, so the agent operates real applications rather than mock pages. Scoring
inspects the backend afterwards: was the issue actually closed, was the item actually added to
the cart. That functional check, rather than reading the agent's transcript, is the
contribution every serious environment benchmark went on to adopt.

As a number it has stopped being informative -- frontier scaffolds now sit close to the human
reference reported in the paper, so it no longer separates systems. Reach for it as a
self-hostable environment to develop against, since it runs on your own machines and the tasks
are inspectable, and quote OSWorld or BrowseComp when you need a score that still
discriminates.

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

Drawn from Wikipedia articles that passed the Good or Featured review and concatenated into a
continuous stream, so long-range context genuinely exists across paragraphs and articles.
Unlike the older Penn Treebank preprocessing it keeps case, punctuation and numbers, and its
vocabulary is the roughly quarter-million words appearing at least three times with the rest
mapped to an unknown token -- which makes perplexity on it a harder and more realistic number
than on the heavily normalized alternatives.

Reach for it as a quick language-modelling sanity check or for comparability with a large body
of published perplexity results, particularly on long-context and memory-augmented
architectures. It is small by current standards and single-domain encyclopedic prose, and
word-level perplexity is not comparable across tokenizers -- a subword model's number must be
normalized per word before it can sit beside the classic results.

### WizardLM Evol-Instruct
**Short:** Method that iteratively rewrites seed prompts into harder, more complex instructions for SFT data.
**Kind:** concept
**Lang:** *
**Roles:** ml-lifecycle/labeling-and-synthetic-data @1, model-training/fine-tuning-and-peft @2

The recipe alternates two rewriting moves over several generations. Deepening makes an
existing instruction harder -- add constraints, require reasoning steps, complicate the input,
demand a rarer output format -- while breadth generates a sibling instruction in the same
domain, keeping the topic distribution from collapsing as difficulty rises. Each surviving
instruction is answered by the same teacher model and failed evolutions are dropped, so the
final corpus carries an intentional difficulty gradient rather than the flat distribution one
generation pass gives.

Reach for it when a small seed set must become a fine-tuning corpus containing genuinely hard
examples, since human-written instructions cluster at easy. Watch two things: complexity is
not quality, and later generations drift toward convoluted prompts nobody would write; and the
corpus inherits the teacher's style so strongly the student learns its formatting habits. Hold
out a human-written test set, because evaluating an evolved model on evolved data flatters it.

### yellowbrick
**Short:** scikit-learn-compatible visualizers for model selection: elbow and silhouette plots, ROC, confusion matrices.
**Kind:** tech
**Lang:** python
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, model-training/classical-ml-and-boosting @2

Each visualizer is a scikit-learn-style object with fit, score and show methods, so a
diagnostic drops into the same code shape as an estimator and can live inside a pipeline. The
set is organized by the decision it supports: classification report heatmaps, ROC and
precision-recall curves and class-prediction-error plots for classifiers; residual and
prediction-error plots for regressors; elbow and silhouette plots for choosing a cluster
count; feature importance, recursive feature elimination, and validation and learning curves
for model selection.

Reach for it during model selection, when the question is which model or which hyperparameter
rather than how to draw something -- the learning curve in particular answers whether more
data or a different model is the right next move, which no single accuracy number can. It is a
matplotlib layer, so a heavily customized figure means dropping to matplotlib anyway, and
scikit-learn's own display classes now cover several of the simpler plots.

### τ²-bench
**Short:** Agent benchmark for policy-following dialogue: dual control with an LM user simulator, scored by pass^k at k=1-4.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @3

In the original benchmark the agent alone holds tools, so the world changes only when it acts.
Here the simulated user has tools too -- toggling a device setting, restarting hardware,
reading an error message back -- which the agent cannot operate itself, making it formally two
actors mutating one environment. The new failure mode is divergence: the agent issues a
correct instruction, the user executes it wrong or reports back inaccurately, and the agent's
belief about the world stops matching the world. Recovering requires verifying rather than
assuming.

Reach for it when the product is a human-in-the-loop agent that must instruct a person and
then check, which almost no other public benchmark contains. Read pass^k rather than a single
attempt: every run reaches a real customer and there is no selection oracle, so reliability
across repeats is the deployable quantity, and a domain that looks strong at one attempt can
be unusable by the fourth.

### τ³-bench
**Short:** Agent benchmark for policy-following dialogue: dual control, an LM user simulator, and pass^k scoring at k=1-4.
**Kind:** dataset
**Lang:** *
**Roles:** ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/agent-framework @3

The third generation keeps dual control and the language-model user simulator and extends the
family along two new axes: domains where the agent must ground in a body of knowledge rather
than only in a tool-backed database, and a voice channel, where transcription errors and
turn-taking become part of the task instead of an idealized text transcript. Scoring stays
state-based against the final database and is reported as pass^k across repeats.

The reason to look at it is that the added axes are where scores collapse -- reported
knowledge and voice results sit far below text results for the same models -- which is a
useful corrective if a deployment plan involves either. Never quote one number for the family:
text, knowledge and voice measure different things, and the domain predicts the score better
than the model does.
