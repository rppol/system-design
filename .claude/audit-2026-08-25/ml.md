# ML Audit — 2026-08-25

## 0. Snapshot

`ml/` is the most *mechanically* healthy section in the repo. Every one of its 84 module
files carries all fourteen template headings and clears the 15-Q&A floor — a scripted sweep
found exactly **one** structurally thin file in the whole section. Version pins are live and
verified (PyTorch 2.13.0, scikit-learn 1.9, XGBoost 3.3, LightGBM 4.7, MLflow 3.15.1, GX 1.x
fluent API), and `monitoring_and_drift_detection.md` is genuinely exemplary — it names
WhyLabs' corporate shutdown, Alibi Detect's 2024 relicensing to BSL 1.1, and NannyML's
covariate-shift-only limitation. `pytorch_deep_dive.md` and `gpu_and_hardware_optimization.md`
are gold-standard current through Blackwell and FSDP2.

The weakness is not quality — it is **weight distribution and silence**. The section is a
superb 2021 ML curriculum that has been meticulously version-patched into 2026 without being
*re-weighted* for it. Classical NLP gets 14,206 lines across 12 files (n-gram language
modelling, CKY parsing, pLSA/LDA topic modelling, linear-chain CRFs) — **45% more content than
the entire MLOps and production cluster's 9,799 lines** — in a section whose immediate
neighbour is a 54-module `llm/` guide and whose 2026 interview signal is dominated by
production, serving and MLOps ([Exponent 2026 guide](https://www.tryexponent.com/blog/machine-learning-system-design-interview-guide),
[PracHub MLOps interview guide](https://prachub.com/resources/machine-learning-engineering-interview-guide-mlops-ai-2026)).
And where the field genuinely moved in the last eighteen months, the section is *silent*
rather than wrong: **zero** mentions of time-series foundation models, **zero** of generative
recommenders / semantic IDs / HSTU, **zero** of FSDP2, **zero** of Muon or any post-AdamW
optimizer, **zero** of state-space models, **zero** of open-vocabulary detection.

Two suspicions the brief raised are **refuted by the evidence**: the 24 case studies are
genuinely diverse (all four suspected near-duplicate clusters differentiate on a real
mechanism), and the NLP cluster is not structurally bloated — its excess is four *specific*
derivations (BM25, RRF/NDCG, FAISS IVF, the LTR progression) re-taught from scratch in three
or four `ml/` files, which is a one-day refactor rather than a rewrite. The single biggest
weakness stands: **the section is weighted toward the material the 2026 reader least needs,
and the five topics that moved most in 2025-26 have no coverage at all.**

| Metric | Value |
|---|---|
| Modules | 47 |
| Case studies | 24 (+ 5 cross-cutting shared primitives + 2 index files) |
| Deep-dive sub-files | 37 |
| Module files total | 84 |
| Total lines (modules) | 90,117 |
| Total lines (case studies) | 20,895 |
| Total lines (section) | 111,012 |
| Q&As (modules, in the quiz bank) | 1,591 |
| Q&As (incl. case studies, excluded from bank) | 1,893 |
| Mermaid diagrams | 511 |
| Avg lines / module file | 1,073 |
| Avg Q&As / module file | 18.9 |
| Files missing a template heading | **0** |
| Files under the 15-Q&A floor | **0** |

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `ml/time_series_forecasting/time_series_foundation_models.md` | sub-file | `time_series_forecasting/` has **zero** mentions of Chronos, TimesFM, Moirai, Lag-Llama or TimeGPT (verified by grep). Chronos-2 (Oct 2025) tops fev-bench, GIFT-Eval and Chronos Benchmark II zero-shot; Moirai-2 shipped Jan 2026; TimesFM runs in Google production at 300+ forecasts/s/GPU. "Zero-shot TSFM vs. tuned LightGBM" is now the first question in any forecasting design round. [MLM 2026 toolkit](https://machinelearningmastery.com/the-2026-time-series-toolkit-5-foundation-models-for-autonomous-forecasting/), [amazon-science/chronos-forecasting](https://github.com/amazon-science/chronos-forecasting) | **P0** | 800 |
| `ml/recommender_systems/generative_recommenders.md` | sub-file | The recsys cluster stops at SASRec/BERT4Rec (2018-19). It has **zero** mentions of HSTU, semantic IDs, TIGER or generative retrieval. Meta's HSTU reframed RecSys as a generative objective at trillion-parameter scale; Shopify's 2026 commerce engine is built on it and is moving item-ID space to semantic-ID token space; Snapchat published its semantic-ID design choices. [NVIDIA: How Generative Recommenders Are Redefining RecSys at Scale](https://developer.nvidia.com/blog/how-generative-recommenders-are-redefining-recsys-at-scale), [Shopify Engineering 2026](https://shopify.engineering/generative-recommendations), [Snap semantic IDs](https://arxiv.org/pdf/2604.03949) | **P0** | 850 |
| `ml/ml_system_design/classical_ml_vs_llm.md` | **expand existing** | 321 lines — the only structurally thin file in the section. §7 Real-World Examples = 7 lines, §10 Common Pitfalls = 8 lines, §14 Case Study = **2 lines**. This is the *most-asked 2026 framing question*: "roughly half of ML engineers are now doing fine-tuning, eval-harness design or RAG plumbing alongside their classical training pipelines". [doit.software 2026 MLE skills](https://doit.software/blog/machine-learning-engineer-skills), [Exponent 2026](https://www.tryexponent.com/blog/machine-learning-system-design-interview-guide) | **P0** | +500 |
| `ml/case_studies/design_llm_hybrid_support_triage.md` | case study | No case study anywhere combines a classical model and an LLM in one product, despite `classical_ml_vs_llm.md` arguing the hybrid is "usually the real answer". "Many interview loops now include a generative AI component" — and the hybrid is where the classical-ML section's judgment actually gets tested. [Exponent 2026](https://www.tryexponent.com/blog/machine-learning-system-design-interview-guide) | **P0** | 950 |
| `ml/case_studies/design_experimentation_platform.md` | case study | The section has `ab_testing_for_ml.md`, `causal_inference_and_ml.md` and `cross_cutting/experimentation_and_online_evaluation.md` — three modules of theory and **no case study that builds the platform**. Experimentation-platform design is a standard senior/staff prompt at every consumer company. | P1 | 950 |
| `ml/recurrent_neural_networks/state_space_models.md` | sub-file | Zero mentions of Mamba, S4, SSM or xLSTM in the RNN module — the only "what came after RNNs besides Transformers" story. Production SSM deployments concentrate in **long-context retrieval, time-series and audio** — all `ml/` territory, not `llm/`. Calibrate: hybrids are ~8% of new open-weight releases, so this is a sub-file, not a module. [AI21 on hybrid LLMs](https://www.ai21.com/blog/rise-of-hybrid-llms/), [Nemotron-H](https://arxiv.org/pdf/2504.03624) | P1 | 700 |
| `ml/computer_vision/open_vocabulary_and_vision_foundation.md` | sub-file | The 5-file CV cluster has **zero** mentions of Grounding DINO, OWL-ViT, open-vocabulary detection, DETR/RT-DETR, and only 3 passing mentions of SAM. Promptable segmentation and zero-shot detection are how CV products are actually built in 2026; the cluster teaches YOLO + Faster R-CNN + Mask R-CNN as if it were 2021. | P1 | 750 |
| `ml/optimization_theory/` §4 + §11 insert | in-place addition | Zero mentions of Muon, Lion, Sophia, Shampoo, 8-bit Adam or muP across `optimization_theory/` and `training_deep_networks/`. **PyTorch 2.9 ships `torch.optim.Muon` natively** — the repo pins PyTorch 2.13 — and Kimi K2, GLM-4.5 and INTELLECT-3 trained on it in 2025. An optimization module that stops at AdamW is now missing a stdlib optimizer. [PyTorch blog: Muon with DeepSpeed](https://pytorch.org/blog/using-muon-optimizer-with-deepspeed/), [NVIDIA Megatron + Muon](https://blockchain.news/news/nvidia-megatron-muon-llm-training) | P1 | +250 |
| `ml/case_studies/design_document_ai_extraction.md` | case study | No case study covers document AI / OCR / structured extraction from forms and invoices — one of the largest applied-ML product categories, and the natural home for the LLM-vs-fine-tuned-encoder tradeoff on a *structured output* task. | P1 | 950 |
| `ml/data_pipelines_and_processing/` §4 + §11 update | in-place addition | Delta Lake is presented as the default table format with a single passing mention of Iceberg. The industry converged on **Iceberg as the interoperability standard** — every cloud ships managed Iceberg, AWS S3 Tables is Iceberg-native, Iceberg v3 closed the feature gap (deletion vectors, row lineage, VARIANT), and UniForm/XTable now translate between formats. Also absent: data contracts, Polars, DuckDB. [Lakehouse table formats in 2026](https://dev.to/alexmercedcoder/lakehouse-table-formats-in-2026-iceberg-delta-lake-hudi-paimon-and-ducklake-how-they-work-p1k) | P1 | +200 |
| `ml/case_studies/design_edge_ml_deployment.md` | case study | Nothing covers on-device / edge inference end-to-end — quantization budgets, model update over-the-air, per-device drift, privacy. `model_compression_and_efficiency/` teaches the techniques with no system to hang them on. | P2 | 900 |
| `ml/reinforcement_learning/offline_rl.md` | sub-file | Only 2 passing hits for offline RL / CQL / IQL / behaviour cloning. Offline RL from logged data is how RL actually gets applied in recsys, pricing and marketplace allocation — three case studies the section already has. | P2 | 700 |
| `ml/mlops_and_ci_cd/` + `model_serving_and_inference/` §§4/8/11 | in-place | Grep-verified gaps across all four production modules: **`serverless inference` 0 hits, `data contract` 0 hits, model-registry `governance` 0 hits, `cost per prediction` 1 hit.** Cost-per-prediction and registry governance are exactly the principal-tier judgment material the section is meant to carry, and it teaches neither. GPU autoscaling and on-call runbooks *are* covered (8 and 10 hits), so this is a narrow insert, not a module. | P1 | +350 |

### Case-study coverage gaps, in full

Profiled against the standard 2026 ML-system-design prompt list. **Covered:** feature store
build (`design_ml_platform` + two supporting modules). **Partial:** experimentation platform
(concept module only, no study); vector-search infra (FAISS appears as a component in 5+
studies but never as the system under design); hierarchical forecasting (MinT is one paragraph
inside `design_demand_forecasting`); LLM+classical hybrid (`design_nlp_classification_pipeline`
touches an LLM few-shot tier as the cascade's last stage but is not built around the hybrid).
**Absent entirely:** ads auction mechanics and budget pacing (distinct from
`design_ads_click_prediction`, which models the CTR *feeding* the auction); incident/reliability
ML; on-device / edge ML; document AI / OCR extraction; speech or voice product (the
`speech_and_audio_ml` module has no case study); multimodal product; model-quality regression
testing and pre-deploy eval gates; ML fleet cost optimisation; inventory/replenishment
optimisation beyond the forecast. The table above proposes studies for the four highest-yield
of these; the rest are recorded for a later pass.

### What each P0 would contain

**`time_series_foundation_models.md`** — §1 the zero-shot forecasting shift; §2 "a forecaster
pretrained on a million series is to ARIMA what BERT was to a bag of words"; §3 pretraining on
synthetic + real corpora, patching, probabilistic heads, covariate conditioning; §4 Chronos-2 /
TimesFM / Moirai-2 / Lag-Llama / TimeGPT compared on licence, covariate support, context length;
§5 xychart of zero-shot vs fine-tuned vs tuned-LightGBM WQL on GIFT-Eval; §6 a runnable
`chronos-forecasting` zero-shot call plus the fine-tune path and the fallback to `statsforecast`;
§7 Google's TimesFM in production, Amazon's Chronos on SageMaker, ML6's grid-imbalance
backtest; §8 TSFM vs GBDT-with-lags vs classical, on cold-start, covariates, cost, latency;
§9 use zero-shot for cold-start and long-tail series, do NOT use it when you have millions of
rows of one series with rich exogenous features; §10 the "zero-shot beat my baseline on MASE
but lost on the business metric" trap, context-window truncation silently dropping seasonality,
leakage through pretraining corpora that contain your public series; §11 chronos-forecasting,
`transformers`, GluonTS, Nixtla `statsforecast`/`neuralforecast`, fev-bench, GIFT-Eval;
§12 18 Q&As; §13 always run the classical baseline; §14 demand forecasting for 40k SKUs where
the TSFM wins on the 30k cold-start tail and loses on the 200 head SKUs.

**`generative_recommenders.md`** — §1 recsys reframed from "score candidates" to "generate the
next item token"; §2 "a catalogue as a vocabulary"; §3 item tokenization, the generative
objective, scaling laws for recommendation, target-aware attention; §4 semantic IDs via RQ-VAE
(TIGER) vs learned atomic IDs; HSTU vs two-tower + ranker; retrieval-as-decoding; §5 a
side-by-side flow of the classical two-stage funnel against the generative one; §6 an RQ-VAE
tokenizer trained on item embeddings, then a small decoder generating semantic-ID sequences,
with beam search over the ID tree; §7 Meta HSTU, Shopify's commerce engine, Snapchat's semantic
IDs, Google's TIGER; §8 embedding-table memory vs vocabulary size, cold-start behaviour,
serving cost, interpretability, incrementality of the migration; §9 use when the catalogue is
huge and cold-start-heavy and you can afford decode latency; do NOT use it to replace a
well-tuned ranker on a small stable catalogue; §10 tokenizer collapse (many items sharing an ID
prefix), the tokenizer drifting when item embeddings are retrained, offline-metric wins that do
not survive A/B; §11 RQ-VAE, HSTU reference impls, NVIDIA Merlin, FAISS for the hybrid path;
§12 18 Q&As; §13 ship it as a candidate source beside the two-tower before replacing it;
§14 migrating a 50M-item marketplace retrieval stage to semantic IDs.

**`classical_ml_vs_llm.md` expansion** — the existing §1-§6 and §12 are good and should not be
touched. Fill §7 with five named-company examples of both directions (a company that replaced
a fine-tuned encoder with a prompted LLM and one that distilled back down); §10 with six real
pitfalls (per-call cost modelled at list price and blown by retries; the LLM prototype that
became the production path because nobody budgeted the distillation; prompt drift after a model
version bump with no eval harness; an LLM in a regulated adverse-action path; latency p99 from
a provider outage with no classical fallback; evaluation by vibes because there were never
labels); and §14 with a full worked case — a 2M-ticket/month support triage system where the
LLM handles the long tail, a distilled DistilBERT handles the head, and a router decides,
with the cost table that justifies the split.

**`design_llm_hybrid_support_triage.md`** — 11-section principal template. Requirements: 2M
tickets/month, 200ms p99 for the routing decision, 40 categories with a long tail of 300 rare
ones, no labels for the tail. Scale estimation of both cost curves. Architecture: LLM labels
the tail and bootstraps a training set, a distilled encoder serves the head, a confidence
router with a conformal abstention threshold sends low-confidence traffic to the LLM, and human
review closes the loop. Deep dives on the label-distillation pipeline, the router's calibration,
the eval harness that catches prompt drift. Tradeoffs: cost per ticket, latency, the retraining
cadence, what happens when the provider deprecates the model. Named implementations. Operational
playbook including the "provider is down" runbook. Pitfalls with quantified impact. Capacity
planning across both paths. 12 discussion Q&As.

---

## 2. Stale — verified CORRECTIONS

| File | Line / heading | What it says | What is true in Aug 2026 | Source URL |
|---|---|---|---|---|
| `distributed_training/distributed_training.md` | §6, §11, §14 | Teaches only the legacy wrapper API: `torch.distributed.fsdp.FullyShardedDataParallel`, `ShardingStrategy.FULL_SHARD`. Zero mentions of FSDP2, `fully_shard` or `DTensor` (grep-verified). | **FSDP1 is deprecated as of PyTorch 2.11.0+.** The current frontend is `torch.distributed.fsdp.fully_shard`, which converts `model.parameters()` to per-parameter `DTensor` in place instead of flattening into wrapper buckets. The section's own `pytorch_deep_dive.md` already documents FSDP2 as current — this is an internal contradiction, not a judgement call. | https://docs.pytorch.org/tutorials/intermediate/FSDP_tutorial.html |
| `ml_system_design/feature_store_design.md` | §4 table, line 67 | `\| Tecton \| Startup \| Redis / DynamoDB \| S3 + Parquet \| No (managed) \|` and line 730 "Managed platform from the team that originally built Feast, but its own SDK". `ml_system_design.md:468` "Feast is open-source; Tecton is managed". 18 mentions across the section, none naming Databricks. | **Tecton is a Databricks product.** Databricks announced the acquisition in Aug 2025 and closed it in Sept 2025; Tecton's real-time feature serving now backs Databricks' agent stack. Presenting Tecton as an independent startup alternative *to* Databricks Feature Store is the wrong build-vs-buy framing for a 2026 answer. | https://databricks.com/blog/tecton-joining-databricks-power-real-time-data-personalized-ai-agents |
| `README.md` (line 62 module table), `CLAUDE.md` (module list), `mlops_and_ci_cd/mlops_and_ci_cd.md:891` | Module table "Key Concepts" column; one Mermaid node label `FastAPI + TorchServe, 400 RPS` | TorchServe named as a serving technology for the section. | **TorchServe is no longer actively maintained** — no planned updates, bug fixes, features or security patches. Note the *content* file `model_serving_and_inference.md` is already correct (its §11 lists TF Serving, ONNX Runtime, TensorRT, Dynamo Triton, BentoML, Ray Serve, Seldon Core, KServe, and correctly omits TorchServe). Only the index metadata and one diagram label lag. | https://docs.pytorch.org/serve/ |
| `distributed_training/distributed_training.md` §8, `training_deep_networks/training_deep_networks.md` §9 | Precision/hardware tables cap at `A100/H100/TPU` and "Volta/Turing/Ampere or newer" | Hopper and Blackwell absent. The sibling `gpu_and_hardware_optimization.md` in the same section already carries B200 (2,250 TFLOP/s BF16, 8 TB/s HBM3e), H200, and FP8/FP4 on Blackwell. Same internal-contradiction class as the FSDP finding. Also: neither file mentions FP8 training or the Transformer Engine. | (internal cross-file inconsistency; hardware figures verified against the section's own `gpu_and_hardware_optimization.md`) |
| `data_pipelines_and_processing/data_pipelines_and_processing.md` | §3, §4, §11 | Delta Lake is the default table format throughout ("DVC, Delta Lake", "Delta Lake provides schema enforcement", "Delta Lake `OPTIMIZE`"); Iceberg appears once, in a code comment. | The industry converged on **Iceberg as the interoperability standard**: every major cloud ships managed Iceberg, AWS S3 Tables is Iceberg-native, Snowflake and Databricks both read/write it, Iceberg v3 closed the gap with deletion vectors, row lineage and a VARIANT type. Delta remains right *inside* Databricks/Fabric, with UniForm emitting Iceberg metadata. The file is not wrong, but its default is now the minority answer outside Databricks. | https://dev.to/alexmercedcoder/lakehouse-table-formats-in-2026-iceberg-delta-lake-hudi-paimon-and-ducklake-how-they-work-p1k |
| `README.md` | line 21; line 81; "All Topics" table | Line 21: "**Forty-six** modules… (37 sub-files total)". Line 81: "**Thirty-six** deep-dive sub-files". The All Topics table numbers 47 rows as 1-46 because **`14` is used twice** (pytorch_deep_dive and generative_models). | There are 47 modules and 37 sub-files (84 module files on disk, verified). Lines 21 and 81 contradict each other on the sub-file count, and the duplicated row number means every module number after 14 in that table is off by one — while the generated tier tables number by `STUDY_ORDER` position, so `model_selection_and_algorithm_choice` is "#33" in one table and "#47" in another. | (internal; verified by `find`/`wc`) |
| `natural_language_processing/text_representation_and_retrieval.md` | §4.2 dense-retrieval table, lines 271-279 | The dense-retrieval model comparison is `SBERT msmarco-MiniLM-L6-cos-v5` / `msmarco-bert-base-dot-v5` / `E5-small` / `E5-large` / `ColBERT-v2` / `ms-marco-MiniLM-L6-v2`, with `bge-large-en-v1.5` in the following paragraph. | **A three-generation-old roster, contradicted by this repo's own `llm/rag_fundamentals/embedding_models.md`**, which names `text-embedding-3-small/large`, Cohere `embed-v4.0`, `Qwen3-Embedding`, `gemini-embedding-2`, `voyage-4` and `EmbeddingGemma` (verified by direct read of both files). The ml table is *labelled* as an MS MARCO MRR@10 benchmark, so it is not literally false — but a reader choosing an embedding model from it in 2026 picks a superseded one. Fix by adding a forward pointer to the `llm/` roster, or by refreshing the table. Same repo, two embedding eras, three folders apart. | (internal contradiction; both files read directly) |
| `natural_language_processing/natural_language_processing.md`, `syntactic_parsing.md` | line 591; lines 946-950 | `"en_core_web_lg 3.8: ~382MB, 514K 300d word vectors + NER"`; `"spaCy en_core_web_sm (transition) \| 92.1 \| 90.0 \| ~8,000 sent/s"`, `"en_core_web_trf ... 95.4 \| 93.9"` | Pinned spaCy model versions, sizes and benchmark figures with no citation year. Low stakes, but these drift silently. Add the measurement date or cite the source, as `ensemble_methods.md` already does for its sklearn/XGBoost defaults. | — |
| `experiment_tracking_and_versioning/experiment_tracking_and_versioning.md` | §11 tools table | Lists `Hyperopt` ("TPE-based, older but widely used") and `Pachyderm` ("Kubernetes-native, strong lineage") as live options. | Flagged as *needs verification before edit*, not verified here — see §7. Both are plausible removal candidates but I did not confirm their 2026 maintenance status, so do not act on this row without checking. | — |

**Not a finding, recorded so it is not re-raised:** the version pins throughout this section
are correct. PyTorch 2.13 is current as of Aug 2026 ([pytorch.org/newsletter/august-2026](https://pytorch.org/newsletter/august-2026/));
`data_pipelines_and_processing.md` already uses the Great Expectations 1.x fluent API and says
so explicitly; `monitoring_and_drift_detection.md` §11 already documents WhyLabs' shutdown and
Alibi Detect's BSL relicensing. The EU AI Act deadline that moved this summer (Annex III
high-risk deferred from 2 Aug 2026 to 2 Dec 2027 by the Digital Omnibus, in force 27 Jul 2026)
does **not** touch this section: `fairness_and_responsible_ai.md` deliberately states no dates
and cross-links `llm/ai_regulations_and_compliance/` for them. That cross-link is now
load-bearing — flag it to the `llm/` auditor.

---

## 3. Reordering — SEQUENCE and TIER changes

### The single most important move

`model_selection_and_algorithm_choice` sits at **position 47 — dead last**, after
`ml_interview_patterns`. It is the section's "which algorithm, when and why" decision matrix,
described in the README itself as "Cross-Cutting Reference — consult any phase" and
"referenced by every case study". A reader following the Full Path meets the decision framework
*after* they have already read all 46 modules it was meant to help them choose between. Move it
to **position 11**, immediately after `imbalanced_data_and_leakage_traps` closes Phase 2 — the
first point at which its content (linear vs trees vs ensembles vs unsupervised, with the
data-size and constraint regimes) is fully earned, and before Phase 3 onward, where every module
is a candidate the matrix is supposed to arbitrate.

### Sequence

| Module | Current position | Proposed | Why |
|---|---|---|---|
| `model_selection_and_algorithm_choice` | 47 (last) | **11** (end of Phase 2) | See above. The decision framework must precede the choices it arbitrates. |
| `information_retrieval_and_search` | 20 | 20 (unchanged) | Correctly placed beside recsys. |
| `ml_interview_patterns` | 46 | 47 (last) | Becomes last once #47 moves out; correct as the consolidation capstone. |

### Tiers

| Module | Current tier | Proposed | Why |
|---|---|---|---|
| `pytorch_deep_dive` | senior **+ principal** | senior only | Stride arithmetic, caching-allocator OOM debugging and `torch.compile` guard invalidation are the *definition* of craft ("can you debug it at 3am"). Nothing in it is a which-approach-at-what-cost judgment call. It is in principal because it is advanced — exactly the promotion the repo's own tier rule warns against. |
| `model_serving_and_inference` | senior only | senior **+ principal** | "Which serving stack at what cost, what SLO, what failure modes, what you tell a team not to build" is the canonical principal question and this module answers it (KServe vs Seldon vs Ray Serve vs Triton vs BentoML, batching, canary, shadow). Its absence from principal is the clearest tier gap. |
| `time_series_forecasting` | **neither tier** | senior | Demand forecasting is one of the two or three most-asked ML design prompts and the section has a case study for it. A module in neither curated path is invisible to every reader who is not doing the full 47. |
| `anomaly_detection` | **neither tier** | senior | Same argument: fraud/anomaly is a top-3 interview prompt, the section has two case studies on it (`design_fraud_detection`, `design_anomaly_detection`), and the module backing them is on no curated path. |
| `model_evaluation_and_selection` | senior only | senior **+ principal** | Metric selection under a business objective — "why AUC is the wrong metric for this product" — is judgment, not craft, and it is the failure mode principal candidates are actually screened on. |
| `computer_vision` (5 files, 4,516 lines) | **neither tier** | leave in neither, but say so | Defensible for a generalist MLE track; recorded here so the omission reads as a decision rather than an oversight. Revisit only if the open-vocabulary sub-file lands. |

### Proposed `STUDY_ORDER["ml"]`

Only one element moves (`ml/model_selection_and_algorithm_choice`, from last to position 11).

```js
ml: [
  "ml/linear_algebra_and_calculus","ml/probability_and_statistics","ml/optimization_theory","ml/information_theory",
  "ml/supervised_learning","ml/ensemble_methods","ml/unsupervised_learning","ml/feature_engineering","ml/model_evaluation_and_selection","ml/imbalanced_data_and_leakage_traps","ml/model_selection_and_algorithm_choice",
  "ml/neural_network_fundamentals","ml/convolutional_neural_networks","ml/recurrent_neural_networks","ml/training_deep_networks","ml/pytorch_deep_dive","ml/generative_models",
  "ml/computer_vision","ml/natural_language_processing","ml/recommender_systems","ml/information_retrieval_and_search","ml/speech_and_audio_ml","ml/multi_task_and_multi_objective_learning","ml/time_series_forecasting","ml/anomaly_detection","ml/reinforcement_learning",
  "ml/ml_system_design","ml/data_pipelines_and_processing","ml/distributed_training","ml/experiment_tracking_and_versioning","ml/mlflow_deep_dive","ml/gpu_and_hardware_optimization","ml/active_learning_and_weak_supervision",
  "ml/model_serving_and_inference","ml/model_compression_and_efficiency","ml/monitoring_and_drift_detection","ml/mlops_and_ci_cd",
  "ml/graph_neural_networks","ml/self_supervised_and_contrastive_learning","ml/meta_learning_and_few_shot","ml/causal_inference_and_ml","ml/adversarial_ml_and_robustness","ml/privacy_preserving_ml","ml/interpretability_and_explainability","ml/fairness_and_responsible_ai","ml/uncertainty_quantification_and_conformal_prediction",
  "ml/ml_interview_patterns",
],
```

The README's "Recommended Learning Order" ASCII block must move
`model_selection_and_algorithm_choice` out of the `[Cross-Cutting Reference]` footer and into
Phase 2 in the same commit, and the tier tables must be regenerated with
`python3 game/extract.py --write-paths` after the `<!-- study-paths -->` tier edits above.

---

## 4. Depth — THIN modules and BLOAT

### Thin

A scripted sweep of all 84 module files (every `## N.` section measured in lines, flagging any
file with three or more of sections 1/3/4/6/7/8/9/10/13/14 under 12 lines) returned exactly
three files, and only one of them is genuinely thin.

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `ml_system_design/classical_ml_vs_llm.md` | **321** | 16 | §1=5 lines, §3=7, §7=**7**, §9=7, §10=**8**, §13=8, §14=**3**. The framework (§2, §4, §5, §6) and the Q&As are good; the evidence, the war stories and the case study are stubs. | Expand to ~800 (see §1 P0 above). Do **not** rewrite §1-§6 or §12. |
| `computer_vision/object_detection.md` | 916 | 17 | §1=10, §7=11, §9=11 — borderline, not a defect on its own. Real gap is content: no DETR/RT-DETR, no open-vocabulary detection. | Covered by the P1 open-vocabulary sub-file; leave the file alone otherwise. |
| `time_series_forecasting/time_series_forecasting.md` | 1,248 | 18 | §1=7, §3=9, §7=11 — the file is long and dense; the short sections are framing sections. Real gap is the TSFM absence. | Covered by the P0 sub-file. |

Everything else clears the bar. **No module page or sub-file in this section is missing a
template heading, and none is under 15 Q&As.**

### Case studies — diverse, but the quality bar is mis-set

**The "24 is a lot — are they variations of one shape?" suspicion is REFUTED.** A full
shape-profile of all 24 tested the four most plausible near-duplicate clusters and found
genuine differentiation in every one:

- **Four two-tower studies** (`recommendation_engine` / `video_recommendation` /
  `content_feed_ranking` / `real_time_personalization`) share a retrieval skeleton by design —
  `recommendation_engine` is the deliberately undifferentiated baseline the other three diverge
  from. Their distinct lessons are the *objective function* (watch-time-weighted logistic
  regression, Covington 2016), *diversity and feedback-loop control* (DPP re-ranking, popularity
  spirals), and *freshness/staleness fusion* (session GRU vs daily batch tower). Only real
  overlap: `content_feed_ranking` and `video_recommendation` each re-derive MMoE. Converge on
  one MMoE explainer if a fifth ranking study is ever added.
- **Three retrieval studies** are three different output types: a ranked catalogue against
  business objectives; a bi-encoder/cross-encoder architecture tradeoff; and span extraction
  where retrieval *recall* — not ranking relevance — is the binding failure mode. Keep all three.
- **The "four tabular binary classifiers"** is mis-specified by the suspicion itself:
  `customer_ltv_prediction` is censored survival regression (BG/NBD vs Cox), not classification.
  The true trio each teach a mechanism the others cannot — uplift/intervention economics;
  regulatory-constrained modelling (the only study where *law* picks the algorithm); and
  real-time streaming at 0.1% positives under a 50ms SLO with adversarial drift.
- **The three text studies** likewise: `ner_pipeline` is token-level sequence labelling, a
  different problem entirely. `nlp_classification_pipeline` and `harmful_content_detection` both
  build a cascade for *opposite* reasons — cost optimisation vs recall under active adversarial
  evasion. If anything ever needs cutting, `nlp_classification_pipeline`'s cascade is the most
  generic argument of the 24.

**What IS wrong is the bar, not the diversity.**

| Group | Count | Lines | Q&As | Template |
|---|---|---|---|---|
| "Legacy" (studies 1-10) | 10 | 915–1,104 | **4–10** | 12 unnumbered headings: Problem Statement / Architecture Overview / Key Design Decisions / Implementation / ML Components Used / Tradeoffs and Alternatives / Interview Discussion Points / Failure Scenarios / Capacity Planning / Additional War Stories / Monitoring Deep-Dive / Additional Interview Questions |
| "Principal" (studies 11-24) | 14 | **498–752** | 8–14 | the correct 11-section numbered template |

**Every one of the 14 principal-template studies is below the 900–1,100-line bar; none reaches
800.** That is not fourteen thin files — it is a bar that was set from the *legacy* cohort's
actual range (915–1,104) and never re-derived for the 11-section template, which is a tighter
form. Density confirms it: `credit_risk_scoring` at 535 lines runs 93 chars/line with 11 Q&As
and is the densest file in the directory; `churn_prediction`, `eta_prediction` and
`harmful_content_detection` are likewise concise, not thin. **Recommendation: re-set the
principal case-study bar to ~500–800 lines for the 11-section template in root `CLAUDE.md`,
rather than inflating fourteen good files by 40%.**

Genuinely below bar, by Q&A count (exact `^**Q:` counts):

| File | Lines | Q&As | Issue |
|---|---|---|---|
| `design_ml_platform.md` | 927 | **4** | The single clearest quality failure in the directory — under half the 10+ floor. |
| `design_search_ranking.md` | 922 | 8 | |
| `design_semantic_search_engine.md` | 550 | 8 | |
| `design_dynamic_pricing.md` | 583 | 8 | Weakest of the principal cohort. |
| `design_image_classification_pipeline.md` | 932 | 8 | |
| `design_ads_click_prediction.md`, `design_autonomous_driving_perception.md`, `design_content_feed_ranking.md`, `design_demand_forecasting.md`, `design_fraud_detection.md`, `design_recommendation_engine.md`, `design_nlp_classification_pipeline.md`, `design_real_time_personalization.md` | — | 9 | One short of the floor each. |

Also: the ten legacy studies lack Intuition, Requirements Clarification, Scale Estimation,
Operational Playbook and Technologies & Tools as named sections, and root `CLAUDE.md` states
the 11-section template is "Standard for ALL case studies" — so they are formally
non-conforming. And the five `cross_cutting/` files use the **14-section module template**
inside `case_studies/`, which is arguably correct (they are concept modules, not case studies)
but is undocumented and reads as an inconsistency; add one line to `case_studies/case_studies.md`
saying so.

Recommendation, in priority order: (a) re-set the line bar in root `CLAUDE.md`; (b) bring the
13 sub-10-Q&A studies to 10+, starting with `design_ml_platform` at 4 — cheap and high yield;
(c) migrate the ten legacy studies to the 11-section template last — the content largely maps
(Problem Statement → §1, Capacity Planning → §10, Interview Discussion Points → §11), so it is
re-heading plus writing Intuition, Scale Estimation and Technologies & Tools, ~2h/file and the
least reader-visible work in this plan.

### Bloat

| Cluster | Files | Lines | Share of section |
|---|---|---|---|
| `natural_language_processing/` | 12 | **14,206** | 15.8% |
| MLOps + production (9 files across 8 modules) | 9 | 9,799 | 10.9% |
| `ml_system_design/` | 7 | 6,072 | 6.7% |
| `ensemble_methods/` | 5 | 5,691 | 6.3% |
| `recommender_systems/` | 6 | 5,677 | 6.3% |
| `supervised_learning/` | 5 | 4,938 | 5.5% |
| `computer_vision/` | 5 | 4,516 | 5.0% |

Classical NLP outweighs the entire production cluster by 45%, in a repo where `llm/` has 54
modules. **But a full profile of all 12 NLP files shows the cluster is NOT structurally
bloated** — every file carries all 14 headings, clears the Q&A floor, and mostly teaches
material nothing else in the repo covers. Ten of the twelve are genuinely interview-relevant
in 2026, either as living techniques (TF-IDF baselines, BM25, calibration, tokenization) or as
*correctly labelled* historical foundations that interviewers still probe (`word_embeddings.md`
explicitly says static embeddings were superseded by contextual models and explains why).

The real bloat is **narrow and targeted: four sub-topics re-derived from scratch in three or
four different `ml/` files.**

| Duplicated derivation | Re-derived independently in |
|---|---|
| BM25 with k1/b and a worked numeric example | `nlp/text_representation_and_retrieval.md` **and** `information_retrieval_and_search.md` (near-identical structure, different numbers) |
| RRF formula + `k=60` rationale + worked example | same two files |
| NDCG / MRR / MAP worked example | same two files (§4.2.1 and §6.5, structurally near-identical walks) |
| RankNet → LambdaRank → LambdaMART progression + flowchart | `information_retrieval_and_search.md` **and** `recommender_systems/retrieval_and_ranking.md` |
| FAISS IVF `nlist`/`nprobe` math | **three** `ml/` files, plus a dedicated `llm/faiss_deep_dive/` |

Fix (a targeted refactor, not a rewrite — ~1 day):
1. Make `information_retrieval_and_search.md` the **canonical home** for BM25, RRF, NDCG/MRR/MAP,
   ANN and the LTR progression. It is the most complete, most current and most tool-accurate of
   the four.
2. Trim `nlp/text_representation_and_retrieval.md` to what is genuinely unique — the ColBERT
   MaxSim derivation and Sentence-BERT siamese-training mechanics — replacing its BM25/RRF/NDCG
   sections with cross-links, and fixing the stale embedding table (see §2).
3. Trim `recommender_systems/retrieval_and_ranking.md`'s RankNet/LambdaRank/LambdaMART section
   to a cross-link, keeping only recsys-unique material (position bias, IPW, MMR/DPP, two-tower
   for user-item). This file already cross-references `information_retrieval_and_search.md`
   rather than pretending to be self-contained — that is the right pattern; extend it.
4. Leave `llm/rag_fundamentals/*` untouched. It earns its existence on RAG-specific concerns
   (chunking interaction, the current embedding roster, metadata pre/post-filtering) and is not
   merely re-teaching classical IR.

One further cross-section duplicate: `nlp/tokenization_deep_dive.md` and
`llm/tokenization_and_embeddings/tokenization_and_embeddings.md` **both derive BPE from
scratch**, both explain WordPiece's likelihood criterion and the `##` marker, and both explain
the byte-level 256-alphabet trick, with the merge-count framing appearing near-identically in
each. The ml file's unique contribution is its fertility-tax-across-languages worked math; the
llm file's is the current tiktoken vocab tables. Worth a coordinated trim with the `llm/`
auditor, but lower priority than the intra-`ml/` retrieval triangle.

Lowest-ROI files in the section, recorded rather than actioned: **`syntactic_parsing.md`**
(1,077 lines of CFG/PCFG, CKY, Eisner, UAS/LAS) is the one file that reads as pure historical
background — it rarely surfaces outside academic or relation-extraction-specialist interviews.
`topic_modeling.md` (1,247 lines) is a distant second. Both are correct, well-built, unique
content, and the Full Path is explicitly "exhaustive by design", so **do not delete them**.
Keep them Full-Path-only (they already are) and direct no further investment into them until
the production and currency gaps above are closed.

**No module or case study in this section should be deleted.**

---

## 5. Cross-section notes

- **`llm/` — the EU AI Act dates.** `ml/fairness_and_responsible_ai.md` deliberately carries no
  dates and cross-links `llm/ai_regulations_and_compliance/` four times for the risk-tier
  structure and compliance timeline. That makes the `llm/` file the single source of truth for
  a timeline that **changed six days before its old deadline**: the Digital Omnibus entered
  into force 27 Jul 2026, deferring Annex III high-risk obligations from 2 Aug 2026 to
  **2 Dec 2027** (Annex I to 2 Aug 2028), while Article 50 transparency obligations *did* take
  effect 2 Aug 2026. Flag to the `llm/` auditor as a P0 correction on their side.
  ([Gibson Dunn](https://www.gibsondunn.com/eu-ai-act-omnibus-agreement-postponed-high-risk-deadlines-and-other-key-changes/))
- **`llm/` — retrieval is the one genuinely double-covered zone, and it is mostly an
  intra-`ml/` problem.** BM25 appears in 15 `ml/` files and 29 `llm/` files; HNSW 12 vs 21;
  cross-encoder reranking 10 vs 21; RRF 9 vs 15. Elsewhere the boundary is clean and correctly
  inverted — `ml/` owns feature stores (40 files vs 1), drift (18 vs 1) and conformal prediction
  (8 vs 0); `llm/` owns LoRA (39 vs 7) and vector databases (36 vs 9). Do **not** merge across
  sections: `llm/rag_fundamentals/*` earns its existence on RAG-specific concerns. The fix is
  the intra-`ml/` de-duplication in §4. The one genuine cross-section duplicate is BPE, derived
  from scratch in both `ml/nlp/tokenization_deep_dive.md` and
  `llm/tokenization_and_embeddings/` — coordinate a trim.
- **`llm/` — the embedding-model roster is inconsistent across sections.**
  `ml/nlp/text_representation_and_retrieval.md` §4.2 and
  `llm/rag_fundamentals/embedding_models.md` name two different generations of embedding model.
  The `llm/` side is the current one; the `ml/` side needs a forward pointer or a refresh
  (see §2). Whoever fixes it should not fork a third roster.
- **`llm/` — state-space models.** If `llm/` already carries Mamba/SSM material, the proposed
  `ml/recurrent_neural_networks/state_space_models.md` should scope itself to the *sequence-
  modelling mechanism* and its time-series and audio applications, and cross-link `llm/` for the
  LLM-scale hybrids. Coordinate before writing.
- **`llm/` — DPO/GRPO.** `ml/reinforcement_learning/` has zero mentions of DPO or GRPO. That is
  probably correct division of labour (`llm/alignment_and_rlhf/`, `llm/reasoning_models/`), but
  the RL module should carry a one-line forward link so a reader who arrives asking "how is RL
  used in 2026" is not left with PPO on CartPole.
- **`devops/` — `ml/mlops_and_ci_cd/` and `devops/ml_platform_and_gpu_infrastructure/`** already
  cross-link per `ml/CLAUDE.md`. The GPU-autoscaling and cost-per-prediction material proposed
  for the MLOps cluster should land wherever the `devops/` auditor is not already putting it;
  coordinate to avoid a third copy.
- **`technologies/` — `ml/mlflow_deep_dive/`** is a per-product deep dive living in `ml/` while
  `technologies/` exists for exactly that shape. Not worth moving (it is well-integrated and
  cross-linked), but worth a note if `technologies/` is being consolidated.

---

## 6. Execution plan

**Wave 1 — corrections (all parallel, ~1 day total).** Nothing here needs a new file,
`STUDY_ORDER` entry or `<!-- study-paths -->` line.

1. `distributed_training.md` — migrate §6/§11/§14 from FSDP1 to FSDP2 (`fully_shard`, `DTensor`),
   keeping one clearly-labelled legacy snippet only if it serves a migration Q&A. Update §8's
   precision table to include H200/B200 and add FP8/Transformer Engine. Re-read and update every
   `**Short:**` line above a changed answer. **~4h.**
2. `training_deep_networks.md` §9 — replace "Volta/Turing/Ampere or newer" with the Hopper/
   Blackwell-inclusive list already used in `gpu_and_hardware_optimization.md`. **~1h.**
3. Tecton → Databricks across 18 mentions; rewrite `feature_store_design.md:67` and `:730` and
   `ml_system_design.md:468` so the build-vs-buy framing is Feast / Databricks-Tecton /
   Hopsworks / cloud-native. **~2h.**
4. TorchServe out of `README.md:62`, `CLAUDE.md`'s module list, and the Mermaid node label in
   `mlops_and_ci_cd.md:891`. `model_serving_and_inference.md` needs no change. **~30min.**
5. `data_pipelines_and_processing.md` §3/§4/§11 — Iceberg as the interop default, Delta as the
   Databricks-native answer, add UniForm/XTable, data contracts, Polars/DuckDB to §11. **~3h.**
6. `README.md` bookkeeping — "Forty-seven modules", "Thirty-seven deep-dive sub-files", fix the
   duplicated row number 14 and renumber the All Topics table 1-47. **~30min.**
7. Verify and then act on the `Hyperopt` / `Pachyderm` rows in
   `experiment_tracking_and_versioning.md` §11 (see §7). **~1h including verification.**
8. `nlp/text_representation_and_retrieval.md` §4.2 — add a forward pointer to
   `llm/rag_fundamentals/embedding_models.md` for the current roster, or refresh the table to
   match it. Date-stamp the spaCy figures in `natural_language_processing.md:591` and
   `syntactic_parsing.md:946-950`. **~1h.**
9. Root `CLAUDE.md` — re-set the principal case-study line bar from "900–1100 lines" to
   "~500–800 for the 11-section template", so fourteen good files stop reading as failures.
   **~15min.**

**Wave 2 — reordering (sequential, must land as one commit, ~2h).** Depends on nothing;
can run in parallel with Wave 1 but must not be split across commits.

10. Move `ml/model_selection_and_algorithm_choice` to position 11 in `STUDY_ORDER["ml"]` in
    `game/app.js`; move it out of the `[Cross-Cutting Reference]` footer into Phase 2 of the
    README's "Recommended Learning Order" ASCII block.
11. Tier edits in the README's `<!-- study-paths -->` block: drop `principal` from
    `pytorch_deep_dive.md`; add `principal` to `model_serving_and_inference.md` and
    `model_evaluation_and_selection.md`; add `senior` to `time_series_forecasting.md` and
    `anomaly_detection.md`.
12. `python3 game/extract.py --write-paths`, then `bash scripts/build_banks.sh`. The generated
    tier tables and the "Not in this path" lists change; do not hand-edit them.

**Wave 2b — retrieval de-duplication (~1 day, one author, after Wave 1 item 8).**

13. Execute the four-step refactor in §4: canonicalise BM25 / RRF / NDCG / ANN / LTR in
    `information_retrieval_and_search.md`; replace the duplicate derivations in
    `nlp/text_representation_and_retrieval.md` and `recommender_systems/retrieval_and_ranking.md`
    with cross-links. Q&As stay where they are — only the derivations move — so the bank count
    should not drop. Re-run `python3 game/extract.py` and confirm.

**Wave 3 — P0 additions (fully parallel across four authors, ~5 days).** Each new sub-file
needs a `<!-- study-paths -->` line in `ml/README.md` (no `STUDY_ORDER` entry — sub-files group
under their parent). The new case study needs a `<!-- study-paths -->` line in the
`case_studies` entry plus updates to `case_studies/case_studies.md` (phase, dependency map,
interview-prep row), the README case-study table, and root `CLAUDE.md`.

14. `time_series_forecasting/time_series_foundation_models.md` — 800 lines. **~1.5 days.**
15. `recommender_systems/generative_recommenders.md` — 850 lines. **~1.5 days.**
16. Expand `ml_system_design/classical_ml_vs_llm.md` §7/§10/§14 — +500 lines. **~1 day.**
17. `case_studies/design_llm_hybrid_support_triage.md` — 950 lines. **~1.5 days.**

**Wave 4 — P1 (parallel, ~5 days).**

18. `recurrent_neural_networks/state_space_models.md` — coordinate with the `llm/` auditor first.
19. `computer_vision/open_vocabulary_and_vision_foundation.md`.
20. Muon / Lion / 8-bit Adam / muP into `optimization_theory/` §4 and §11, and
    `training_deep_networks/` §6 (`torch.optim.Muon` is in the pinned PyTorch).
21. `case_studies/design_experimentation_platform.md`.
22. `case_studies/design_document_ai_extraction.md`.
23. Case-study Q&A floor: bring the 13 sub-10-Q&A studies to 10+, starting with
    `design_ml_platform.md` (4 → 10). **~1.5 days for all thirteen.**

24. Serverless inference, data contracts, model-registry governance and cost-per-prediction
    into `mlops_and_ci_cd/` and `model_serving_and_inference/` §§4/8/11 — all four sit at 0-1
    grep hits today. **~1 day.**

**Wave 5 — P2 (low priority, ~1 week).**

25. `reinforcement_learning/offline_rl.md`.
26. `case_studies/design_edge_ml_deployment.md`; `case_studies/design_ads_auction_and_pacing.md`
    (auction mechanics and budget pacing are a distinct gap from `design_ads_click_prediction`,
    which models the CTR feeding the auction, not the auction).
27. Coordinated BPE trim between `nlp/tokenization_deep_dive.md` and
    `llm/tokenization_and_embeddings/`.
28. Migrate the 10 legacy case studies to the 11-section template. ~2h each, **~3 days**, lowest
    reader-visible payoff — do it last.

**Total: ~4 weeks of one author's time, or ~1.5 weeks with four parallel authors.**
Waves 1 and 2 alone (~1.5 days) close every verified correctness defect.

**Files needing coordination outside `ml/`:** root `README.md` (module and case-study counts, if
Wave 3 lands), root `CLAUDE.md` (ML module table and case-study list), `game/app.js`
(`STUDY_ORDER["ml"]`, Wave 2 only).

---

## 7. Confidence and gaps

**High confidence** (grep-verified against the files, plus a cited external source where the
claim is about the world): the FSDP1/FSDP2 correction, the Tecton acquisition, the TorchServe
residue, the embedding-roster contradiction (both files read directly), the absence of TSFMs /
generative recommenders / SSMs / post-AdamW optimizers / open-vocabulary detection, the
template-compliance sweep (0 files non-conforming), the thin-section sweep (1 file), all line
and Q&A counts, the case-study template split and its exact Q&A counts, the case-study
diversity refutation, the retrieval-triangle duplication map, the cluster-size figures, and the
`ml`/`llm` overlap counts.

**Medium confidence:** the tier reassignments are judgment calls against the repo's own stated
senior-vs-principal rule, not measurements. The Iceberg finding is a "the default moved"
argument, not a "this is wrong" one — a reviewer could reasonably keep Delta-first.

**Could not verify — do not act without checking:**

1. **`Hyperopt` and `Pachyderm`** in `experiment_tracking_and_versioning.md` §11. Both look like
   removal candidates but I did not confirm their 2026 maintenance status. Check before editing.
2. **Redundancy quantification in the MLOps and system-design clusters.** Three profiling
   subagents were lost to a sustained model rate-limit and could not be re-run within the
   window. I probed the specific *gaps* by grep (which is what §1 and §2 rest on), but I do not
   have section-by-section overlap percentages for: `experiment_tracking_and_versioning` vs
   `mlflow_deep_dive`; `mlops_and_ci_cd` vs `model_serving_and_inference` on
   canary/shadow/rollback; and how many times the "6-step design framework" is restated across
   `ml_system_design.md`, `design_framework.md` and `ml_interview_patterns.md`. **Each is a
   plausible merge candidate and none is confirmed.** Re-run that profiling before any merge.
   Two of the pairs I did get answers on came back *negative*: `cross_cutting/drift_monitoring_and_retraining.md`
   overlaps `monitoring_and_drift_detection/` but is retraining-loop-focused where the module is
   observability-focused, and `cross_cutting/responsible_ai_fairness_and_explainability.md` is
   explicitly scope-disambiguated by `fairness_and_responsible_ai.md` ("not the applied
   governance checklist for a specific case study"). The `feature_store_design.md` vs
   `cross_cutting/feature_store_and_point_in_time_correctness.md` pair is the one real overlap
   and lacks such a disambiguation note — add one rather than merging.
3. **Causal inference is NOT a gap — this was checked and came back clean.** Grep confirms
   coverage of double machine learning (13 hits), EconML/CausalML (10), S/T/X/R meta-learners
   (23), causal forests (5), difference-in-differences (4), synthetic control (5), instrumental
   variables (4), switchback/interference/SUTVA (20), CUPED (50), sequential testing (13) and
   propensity scores (44). Only **geo experiments** (0 hits) and DoWhy (1) are light. An earlier
   draft of this report proposed a `double_machine_learning.md` sub-file; **that proposal is
   withdrawn.** The causal/experimentation cluster is one of the strongest in the section.
4. **Whether `llm/` already covers Mamba/SSM.** Determines the scope of proposal 18. Likewise
   whether `llm/` covers open-vocabulary vision (it has `vision_language_models/` and
   `multimodal_models/`) — determines the scope of proposal 19.
5. **The `ml/`↔`llm/` overlap counts in §5 are file-hit counts from grep, not content
   measurements.** A file that mentions BM25 once counts the same as one that derives it. They
   establish direction, not magnitude. The retrieval-triangle table in §4 *is* content-verified.

**Refuted, and recorded so it is not re-raised:** the brief's suspicion that 24 case studies
are several variations of the same shape. All four suspected near-duplicate clusters were
tested against the actual files and every one is genuinely differentiated (§4). The `llm`-boundary
suspicion was also mostly refuted: the boundary is clean and correctly inverted except for
retrieval, and the retrieval problem is mostly *inside* `ml/`, not across the two sections.
