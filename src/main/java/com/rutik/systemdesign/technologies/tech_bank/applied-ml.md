# Applied ML — technology bank

<!-- tech-bank tier: applied-ml -->

The 246 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Applied ML** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### @mastra/voice-openai
**Short:** Mastra plugin wiring OpenAI speech-to-text and text-to-speech into a TypeScript agent for voice interaction.
**Kind:** tech
**Lang:** js
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/agent-framework @2

Mastra's voice layer is provider-shaped: an agent holds a voice object, and this package is the implementation backed by OpenAI's hosted speech endpoints -- transcription for incoming audio, synthesis for outgoing text -- so switching vendors is a constructor change rather than a rewrite of the agent.

Reach for it when a TypeScript Mastra agent needs to hear and talk and OpenAI is already the model provider. The limits are the hosted ones: each turn costs a network round trip on top of the model call, audio is billed separately from text, and the voice catalogue is fixed. A speech-to-speech realtime model removes the two extra hops when latency is the product.

### ADTK
**Short:** Anomaly Detection Toolkit: composable rule- and model-based detectors for time-series outliers.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

Detectors take a pandas Series or DataFrame with a `DatetimeIndex` and return a boolean flag per timestamp. The catalogue splits into simple rules (`ThresholdAD`, `QuantileAD`, `InterQuartileRangeAD`) and model-backed ones (`LevelShiftAD`, `PersistAD`, `SeasonalAD`, `AutoregressionAD`), and transformers plus a `Pipe`/`Pipenet` combinator let you compose them -- deseasonalize first, then flag level shifts.

It fits the case where you can describe what abnormal looks like -- a shift, a spike against local history, a break in weekly seasonality -- and want that stated declaratively without labels. It is single-series, unsupervised, and has seen little development for years. For multivariate outliers PyOD carries far more algorithms, and for per-event updates River is the better fit.

### Aequitas
**Short:** University of Chicago bias-audit toolkit producing report-style group fairness metrics for high-stakes models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @3

You hand it one row per decision -- model score, true label, protected attributes -- and it computes group rates (FPR, FNR, FDR, FOR, precision, recall) and then disparities as each group's rate divided by a reference group's, flagging any that falls outside a tolerance such as the four-fifths rule.

The output is an audit report, not a mitigation: it tells you which parity you fail and by how much, in a form a compliance reader can keep. Its fairness tree exists because you cannot satisfy every definition at once -- punitive interventions care about false positives, assistive ones about false negatives. To then change the model, fairlearn or aif360 carry the algorithms.

### AI
**Short:** Umbrella index entry, not a product; here it stands for EHR-integrated AI clinical documentation.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/nlp-and-text @3

### aif360
**Short:** IBM AI Fairness 360: the widest catalog of pre-, in- and post-processing bias-mitigation algorithms and metrics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

Everything runs through a dataset object carrying features, labels, protected attributes and a declared privileged group, and the algorithms are filed by where they intervene: pre-processing rewrites the data (Reweighing, Disparate Impact Remover, Optimized Preprocessing), in-processing changes training (Adversarial Debiasing, Prejudice Remover), post-processing adjusts decisions (Equalized Odds, Reject Option Classification).

Take it when you need the widest menu of published mitigations, or when a specific paper's method has to be reproduced. The cost is ergonomics: the dataset wrapper does not compose naturally with a scikit-learn pipeline, and the breadth means choosing among a dozen methods that optimize incompatible definitions. Fairlearn is the smaller, more idiomatic choice when group metrics plus one reduction is enough.

### Albumentations
**Short:** Fast image augmentation library that keeps masks, boxes and keypoints in sync with geometric transforms.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/labeling-and-synthetic-data @3

Transforms are composed into a pipeline and each call returns a dict, so one `A.Compose([...])` applied to `image=`, `mask=`, `bboxes=` and `keypoints=` puts the same random geometry through all of them. That synchronisation is the whole reason to use it for detection and segmentation, where a flipped image with an unflipped mask trains garbage silently rather than raising anything. Beyond flips and crops it carries the photometric and distortion transforms that matter for medical and satellite imagery -- CLAHE, GridDistortion, ElasticTransform, ShiftScaleRotate -- and it works on NumPy arrays with OpenCV underneath, so it runs on CPU inside the DataLoader worker. Reach for it in any PyTorch vision pipeline; if augmentation becomes the data-loading bottleneck, a GPU-side library such as DALI or Kornia is the alternative.

### alibi
**Short:** Python explainability library: anchors, counterfactuals, ALE plots and integrated gradients for black-box models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It covers the explanation methods SHAP does not: anchors, which search for a minimal rule of feature conditions under which the prediction holds with high precision; counterfactual generators, including prototype-guided and reinforcement-learning variants that keep the result on the data manifold; accumulated local effects, which fix partial dependence's bias under correlated features; and integrated gradients for differentiable models.

Reach for it when the stakeholder question is what would have to change, or which conditions guarantee this outcome, rather than how much each feature contributed. Counterfactual search is an optimization per instance, so it is slow and returns implausible points unless constrained by feature ranges and immutability. Its sibling alibi-detect handles drift and outliers, which this library deliberately does not.

### allennlp ConditionalRandomField
**Short:** AllenNLP CRF output layer for sequence tagging, with allowed-transition masks for constrained decoding.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

### Amazon RCF
**Short:** AWS Random Cut Forest anomaly detection, offered inside CloudWatch and Kinesis Data Analytics for streaming signals.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/timeseries-and-anomaly @1, platform-delivery/cloud-platform-and-cost @3

Random Cut Forest builds an ensemble of trees over random subsamples, each recursively cutting the bounding box at a dimension chosen with probability proportional to its range. A point's score comes from how much tree structure has to change to accommodate it -- isolated points sit shallow and displace many others -- and the structure supports incremental insert and delete, which is what makes it work on an unbounded stream.

Reach for it inside AWS when you want unsupervised anomaly scores on a metric stream with no labels and no training job of your own. It returns a score, not a verdict, so the threshold is still yours to choose, and it is weak on strongly seasonal signals unless you shingle the input into lagged windows first. Off AWS, Isolation Forest is the same idea in batch form.

### Anthropic Claude Haiku
**Short:** Anthropic's small, fast Claude model tier; the cheap worker model for narrow high-volume subtasks.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agent-framework @3

Haiku shares the API surface, tool-use protocol and system-prompt behaviour of the larger Claude tiers, so a prompt written against a bigger model runs unchanged and only price per token and time to first token differ. Prompt caching and batch submission cut the cost of a high-volume workload further, which is where this tier earns its place.

Route to it the steps that are narrow and well specified: classification, extraction into a schema, routing, summarizing one chunk, judging a single criterion. It gives up depth on multi-step reasoning and long agent trajectories, where a larger model is often cheaper overall because it needs fewer attempts. The usual shape is a cascade -- Haiku first, escalate when a validator or a confidence check says so.

### anthropic.tokenizer
**Short:** Anthropic SDK token counting used to size a prompt against the context window before sending it.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @2

### Berkeley Neural Parser
**Short:** Self-attentive constituency parser (~95 F1) that plugs into spaCy to produce syntactic parse trees.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

It produces constituency trees -- nested phrase structure -- rather than the dependency arcs most modern pipelines emit, using a self-attentive encoder that scores every labelled span with a chart decoder picking the highest-scoring consistent tree. It installs as a `benepar` component inside a spaCy or NLTK pipeline, so the parse hangs off sentences you already have annotated.

Reach for it when the task genuinely needs phrase structure: extracting noun phrases and subordinate clauses, sentence simplification, or linguistic analysis where a dependency arc is the wrong shape. It is far slower than spaCy's dependency parser and pulls in a transformer encoder, so run it over selected sentences rather than a whole corpus, and use dependencies when relations between words are all you need.

### BERTopic
**Short:** Topic-modeling library chaining embeddings, UMAP, HDBSCAN and c-TF-IDF; the modern default for short-text topics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3

Each stage is swappable: embed documents with any sentence encoder, reduce dimensions with UMAP, cluster with HDBSCAN, then describe each cluster with a class-based TF-IDF that treats all its documents as one document and scores words by how much they distinguish it. Because the clustering is density-based, the topic count is discovered rather than set, and documents fitting nowhere land in an outlier topic numbered -1.

It beats LDA on short, noisy text -- tickets, reviews, chat -- because the embedding carries meaning a bag of words cannot. Two costs bite in production: UMAP is stochastic, so runs differ unless you fix the seed, and the outlier bucket can swallow a large share of the corpus. Topics also drift as data grows, so pin a fitted model rather than refitting behind a dashboard.

### BertViz
**Short:** Interactive visualizer for transformer attention: per-head, per-layer and cross-attention heatmaps.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, applied-ml/nlp-and-text @3

It reads a HuggingFace model's `output_attentions` tensors and renders them as interactive notebook views: a head view drawing token-to-token lines for one layer, a model view tiling every layer and head at once, and a neuron view tracing the query-key products behind a single connection. The value is diagnostic speed, since noticing that a head has collapsed onto the first token, or that encoder-decoder cross-attention is not aligning source and target, is far quicker to see than to compute. Treat what you see as a hint rather than an explanation: attention weight is not a faithful account of what the model actually used, so a heatmap is not evidence of causal attribution.

### BLIP-2 OPT-2.7B
**Short:** Salesforce vision-language model pairing a frozen image encoder with OPT-2.7B via a Q-Former for captioning and VQA.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

The Q-Former is the whole idea: a small transformer holding a fixed set of learnable query embeddings that cross-attend to a frozen image encoder's patch features and emit a short fixed-length sequence, linearly projected into OPT's embedding space and prefixed to the text. Only the Q-Former trains, so aligning vision to language costs a fraction of training a multimodal model end to end.

Its lasting value is that recipe. As a deployable model it has been overtaken: the 2.7B OPT backbone is weak by current standards and it was never instruction-tuned, so it captions and answers short questions rather than following instructions or holding a conversation. Reach for a current small open VLM instead unless you are studying the architecture itself.

### BloombergGPT
**Short:** 50B-parameter LLM trained largely on Bloomberg's financial corpus for finance-domain NLP tasks.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

It is a decoder-only model trained from scratch on a roughly even mix of Bloomberg's internal financial archive -- filings, news, transcripts and market commentary accumulated over decades -- and general public text, on the argument that a domain model still needs broad fluency to be usable. It outperformed comparable open models of its era on financial sentiment, entity and question-answering tasks while staying competitive on general ones.

The weights were never released and the corpus is proprietary, so it is a case study rather than something to adopt. The lesson most teams take from it now runs the other way: since it appeared, fine-tuning or retrieval-augmenting a strong open base model has usually closed the domain gap for a tiny fraction of the pretraining bill.

### captum
**Short:** PyTorch attribution library: Integrated Gradients, DeepLIFT, GradientSHAP, Occlusion and layer-level GradCAM.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It implements gradient- and perturbation-based attribution behind one uniform `attribute()` call on any differentiable PyTorch model: Integrated Gradients, DeepLIFT, GradientSHAP, Saliency, Occlusion, and layer or neuron variants such as `LayerGradCam` and layer conductance. The output is per-input importance — which pixels, tokens, or features moved this prediction — and methods with a completeness property report a convergence delta so you can check the attribution actually sums to the prediction difference.

Reach for it to debug a specific wrong prediction, to sanity-check that a model is not keying on a leaked or spurious feature, or to produce a saliency overlay for a review. Be precise about what it is not: this is correlational input attribution, not mechanistic interpretability — it says which inputs mattered, never what a circuit inside the network computes.

### Cartesia Sonic
**Short:** Cartesia's low-latency streaming text-to-speech model, targeted at sub-100ms first-audio for voice agents.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

Sonic is built on a state-space backbone rather than a transformer, which is where its latency profile comes from: recurrent state means emitting the next chunk of audio does not get more expensive as the utterance grows, so speech streams out continuously while text is still arriving. It takes incremental text over a streaming connection and supports voice cloning from a short reference sample.

Reach for it in a spoken agent where time to first audio is the perceived quality bar and you want to start speaking before the language model finishes its sentence. It is a hosted commercial API, so audio leaves your infrastructure and you pay per character; a self-hosted vocoder-based TTS stack is the alternative when that is unacceptable, at real cost in latency engineering.

### CausalML
**Short:** Uplift-modeling library: T/S/X-learners, causal forests and treatment-effect estimation for marketing use cases.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

Uplift is the difference between what a user does when treated and when not, which is never observed for the same person, so the library estimates it indirectly with meta-learners built on any scikit-learn model: the S-learner adds treatment as a feature, the T-learner fits one model per arm, and the X- and R-learners correct those with residualization. Uplift trees instead split on divergence in treatment response directly.

Reach for it when budget is limited and you need to rank who is persuadable rather than prove the campaign worked on average. Evaluation is the hard part: with no per-person ground truth, Qini and AUUC curves depend on a randomized holdout, and without one every confounder in the assignment flows straight into the estimated uplift.

### CircuitsVis
**Short:** Embeddable interactive visualizations of attention patterns and neuron activations for interpretability notebooks.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It renders interpretability views -- attention patterns per head and layer, neuron activations coloured over the token stream -- as React components that embed in a notebook cell or a static web page, built from Python data you already hold. Because they are components rather than rendered images, the views stay interactive: hover a token to trace its attention, page through heads, scrub a long sequence.

Reach for it when writing up mechanistic-interpretability work you want a reader to explore rather than squint at. It draws activations and does not compute them, so it pairs with TransformerLens or NNsight for the hooks that produce the tensors. The standing caveat on every attention picture applies: a bright cell shows where weight went, not what the model computed with it.

### Claude 3.5 API
**Short:** Hosted Claude endpoint with strong vision and document understanding: OCR, charts, and layout-aware extraction.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/rag-and-document-processing @3, inference/model-server @3

Images travel in the same messages array as text, as content blocks carrying base64 data and a media type, so one request can interleave several pages with instructions about them. The model reads charts, tables, handwriting and layout directly instead of consuming OCR output, which is what lets it answer structural questions -- which column a figure sits in, what a callout points at -- that flattening to text destroys.

Reach for it when documents are messy enough that a rules-based extractor keeps breaking. Cost scales with image tokens, so resolution and page count drive the bill, and there is no bounding-box output, so you cannot cite a location on the page for review. Later Claude generations supersede this one at better accuracy and price; the request shape carries over unchanged.

### Claude 3.5 Sonnet
**Short:** Anthropic vision-language model noted for document OCR accuracy and structured extraction from images.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

This was the checkpoint that made schema extraction with a general-purpose vision model practical: given a scanned form and a description of the fields you want, it returns them filled in one call, holding accuracy on dense small text and multi-column layout where earlier models lost track of which value belonged to which label.

The pattern it established is still the one to copy -- describe the output as a schema, pass the page image, validate the parsed result, retry once on failure -- but the checkpoint itself is superseded by later Claude releases. Treat any per-page accuracy you remember from it as a floor rather than a spec, and re-measure on whichever model you actually deploy.

### CLIP
**Short:** Contrastively trained image-text model placing pictures and captions in one embedding space for search.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

Training is a symmetric contrastive objective over a batch of image-caption pairs: an image tower and a text tower project into one space, cosine similarities are scaled by a learned temperature, and cross-entropy pushes the matched diagonal up and every mismatched pair down. Trained on 400 million web pairs, that alone yields zero-shot classification -- embed a prompt per class and take the nearest.

It remains the default when you need one vector space spanning both modalities: cross-modal search, filtering, or a frozen vision tower for a larger model. Know the failure modes first -- a 77-token text limit, near-blindness to counting and spatial relations, and bag-of-words composition, so a red cube on a blue sphere matches the swap. SigLIP is the drop-in upgrade for new work.

### CLIP ViT-L/14
**Short:** OpenAI's ViT-L/14 CLIP checkpoint producing joint image and text embeddings for retrieval and conditioning.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

This is the specific OpenAI checkpoint downstream work standardized on: a Vision Transformer Large over 14-pixel patches at 224-pixel input, with a 336-pixel variant that quadruples the patch count for finer detail. It became the vision tower of the first generation of open VLMs and the text conditioner of the earliest Stable Diffusion releases, so a great deal of published tooling assumes exactly this embedding space.

Pick it when compatibility with that ecosystem matters -- reusing an existing index, a LoRA, or a published result measured against it. For a fresh system there is little reason to: SigLIP and the open CLIP reproductions retrieve better at comparable cost, and the low fixed input resolution is the binding constraint on anything involving small text or fine detail.

### CoNLL-U
**Short:** Tab-separated file format for Universal Dependencies treebanks; read in Python via the conllu package.
**Kind:** spec
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, ml-lifecycle/evaluation-and-benchmarks @3

Every token is one line of ten tab-separated fields -- id, form, lemma, universal POS, language-specific POS, morphological features, head id, relation, enhanced dependencies, misc -- with a blank line ending each sentence and `#` comment lines carrying the sentence id and its raw text. An underscore means unspecified, a range id like `1-2` introduces a multiword token, and a decimal id like `1.1` marks an empty node.

It is the interchange format of every Universal Dependencies treebank and the input and output of most parsers, so the evaluation scripts assume it. The traps are mechanical: the separator is a real tab, the head field is a one-based id where 0 means root, and splitting the file naively drops the multiword and empty-node rows that make detokenization and enhanced graphs correct.

### ContextCite
**Short:** Attribution method that identifies which parts of the supplied context actually caused a model's statement.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, search-retrieval/rag-and-document-processing @2

It attributes by ablation rather than by attention. The context is split into sources, many random subsets are held out, the model is re-run on each, and the log-probability of the original response is recorded; fitting a sparse linear surrogate from which sources were present to that likelihood gives every source a weight, and the large positive ones are what the statement actually depended on.

This catches the case an inline citation cannot: a passage that reads like the source but was not used, or an answer produced from parametric memory with the retrieved chunk contributing nothing. The cost is the ablations -- dozens of extra forward passes per response -- so it belongs in evaluation, periodic auditing of a traffic sample, or debugging one hallucination, never on the hot path.

### crepes
**Short:** Lightweight conformal-prediction library giving calibrated classifiers, regressors and Venn-Abers intervals.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

Conformal prediction turns any point predictor into one with a coverage guarantee: score the model's errors on a held-out calibration set, take the quantile of those nonconformity scores at your chosen error rate, and emit an interval or label set of that width. Crepes wraps a fitted scikit-learn model to do that, adds difficulty estimators so width varies with local uncertainty instead of being constant, and can return a full predictive distribution rather than one interval.

Reach for it when a number needs a defensible error bar and you would rather not rebuild the model as a Bayesian one. Two limits decide whether the guarantee means anything: coverage is marginal, holding on average rather than for any particular subgroup, and it assumes exchangeability, which both distribution shift and time series break.

### CRF++
**Short:** Classic C++ conditional random field trainer and decoder with template-based features for sequence labeling.
**Kind:** tech
**Lang:** cpp
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3

Features are declared in a template file rather than in code: a line like `U01:%x[-1,0]` expands into one feature per observed value of the token one position back, and the trainer materializes that full feature set from the corpus before maximizing the log-likelihood with L-BFGS. Data is column format -- one token per line, gold label last, blank line between sequences -- driven by the `crf_learn` and `crf_test` binaries.

It is fast, tiny and CPU-only, which still suits chunking a narrow domain with hand-built features and little training data. For anything broader the template language is a straitjacket and the project is long dormant: sklearn-crfsuite gives the same model with Python feature functions, and a fine-tuned encoder beats both once you have labels and a GPU.

### daggity
**Short:** DAGitty: browser tool for drawing causal graphs and deriving valid adjustment sets for confounding.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1

You draw the causal graph -- nodes for variables, arrows for assumed direct causes -- and mark which node is the exposure and which the outcome. It then applies d-separation and the back-door criterion mechanically to enumerate every minimal sufficient adjustment set, and lists the conditional independencies the graph implies, which are testable against your data as a check on the assumptions.

Its value is catching the two classic mistakes before any model is fitted: omitting a confounder, and controlling for a mediator or a collider, which introduces bias rather than removing it. The graph itself is an assumption you supply and it cannot verify, so garbage in still gives a confidently wrong adjustment set. An R package mirrors the browser tool for scripted work.

### dalex
**Short:** Model-agnostic explanation and fairness auditing library with matching Python and R interfaces.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

You wrap a fitted model plus its data in an explainer object, and every question is then a method on it: permutation-based variable importance, partial dependence and accumulated local effects for global shape, break-down and Shapley contributions for one observation, and a fairness module that computes group metrics and checks their ratios against a tolerance.

The distinctive property is that the Python and R packages implement the same interface, so a team split across both languages compares models on identical explanation output instead of arguing about tooling. Everything is permutation-based and model-agnostic, which makes it slow on wide data and blind to feature correlation -- correlated columns swap importance run to run. For tree models specifically, TreeSHAP is both exact and faster.

### DALL-E 3
**Short:** OpenAI text-to-image model, notable for close prompt adherence and in-prompt text rendering; called over the API.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

Its prompt adherence came from the training data rather than the sampler: the images were re-captioned with a captioning model to produce long, detailed synthetic descriptions, so the model learned to attend to every clause of a request instead of the two or three nouns a scraped alt-text teaches. In the chat product the user's short prompt is also rewritten into a longer caption before generation, which is why the same words behave differently through the API.

Reach for it when a scene must contain specific, listed elements and legible short text. The tradeoffs are that you do not control the rewrite, styles are pushed toward a recognizable house look, and the API offers generation only -- no inpainting or image conditioning. Later OpenAI image models supersede it, and Stable Diffusion is the choice when you need ControlNet-grade spatial control or local hosting.

### Darts
**Short:** Name collision: the Darts forecasting library with a unified model API, and DARTS differentiable architecture search.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, ml-lifecycle/experiment-tracking-and-tuning @3

The forecasting library puts classical models (ARIMA, exponential smoothing, Theta), tree-based ones and deep models (N-BEATS, TFT, TCN) behind a single `fit`/`predict` interface over a `TimeSeries` object, with backtesting, covariates and probabilistic forecasts built in, so swapping a baseline for a neural model is a one-line change instead of a rewrite.

The architecture-search method is unrelated work: it relaxes the discrete choice of operation inside a cell into a continuous mixture with learnable weights, optimizes weights and architecture in a bilevel loop, then discretizes the result — turning searches that cost thousands of GPU-days into roughly one. It is equally known for instability, tending to collapse toward parameter-free operations such as skip connections.

### DeepCTR
**Short:** Library of prebuilt CTR/CVR model layers (MMoE, PLE, ESMM, shared-bottom, DeepFM) for recommendation ranking.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1

The core abstraction is the feature column: `SparseFeat`, `DenseFeat` and `VarLenSparseFeat` declare each input's cardinality and embedding dimension, and the library builds the embedding tables, pooling for variable-length behaviour sequences, and the model graph from that declaration. On top sit Keras implementations of the published CTR architectures -- DeepFM, xDeepFM, DCN, AutoInt, DIN, DIEN -- plus the multi-task heads that share a bottom across objectives.

Reach for it to get a strong ranking baseline running in a TensorFlow shop without reimplementing a paper. What it is not is a serving system: there is no feature store, no embedding sharding, and no answer for tables that outgrow one machine, which is where TorchRec, Merlin or an in-house stack take over. Maintenance is light, so pin versions against your TensorFlow.

### DeepCTR-Torch
**Short:** PyTorch library of prebuilt CTR/CVR ranking architectures: shared-bottom, MMoE, PLE, ESMM and deep-cross layers.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @3

It is the PyTorch port of the same project, keeping the feature-column declaration and adding a scikit-learn-shaped `fit`/`predict` on top, so a ranking model is a few lines and trains with a chosen optimizer, loss and metric list without writing a loop. Embedding tables, sequence pooling and the interaction layers are built from the same column definitions as the TensorFlow original.

Take it when the shop is PyTorch and you want a published architecture as a baseline quickly. Its model zoo lags the TensorFlow version and both are lightly maintained, so read the source before trusting an exotic layer. It is single-process and single-GPU by design: once embedding tables no longer fit in one device's memory, TorchRec's sharding is the step up.

### Deepgram Nova-2
**Short:** Deepgram's streaming speech-to-text model, tuned for low-latency real-time transcription in voice agents.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

You hold a WebSocket open and push audio frames; the service returns interim hypotheses that get revised and then finalized, with utterance and endpointing events marking where a speaker stopped, which is the signal a voice agent uses to decide it is its turn. Options cover diarization, keyword or keyterm boosting for domain vocabulary, profanity and number formatting, and per-word timestamps and confidences.

Reach for it when transcription has to keep pace with a live call and partial text needs to appear as the person speaks. Batch accuracy is where a large offline model such as Whisper still wins, since it sees whole utterances rather than a growing prefix. Later Nova generations supersede this one; the streaming protocol and options are the part worth learning.

### DeepSeek-Coder
**Short:** Open-weight code LLM family (1.3B-33B) from DeepSeek; strong open-source code completion and generation.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agentic-environments @2

It was pretrained from scratch on a corpus that is overwhelmingly source code with a slice of natural language, and two data decisions explain its strength: repository-level packing, where files from one project are concatenated in dependency order so the model sees cross-file context, and a fill-in-the-middle objective that trains it to complete a span given both the prefix and the suffix -- which is what an editor actually asks for.

Reach for it when self-hosted completion inside an IDE is the requirement and code cannot leave the building. The instruct variants are weaker at open-ended chat than general models of the same size, and the context window is modest by current standards, so whole-repository reasoning needs retrieval rather than a longer prompt. Newer DeepSeek and Qwen code releases have since raised the bar.

### DeepSeek-R1
**Short:** Open-weights reasoning LLM trained with RL to emit long chains of thought before its final answer.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

It was trained by reinforcement learning against automatically checkable rewards -- does the maths answer match, does the code pass the tests, is the output in the required format -- rather than by imitating human reasoning traces. That alone made long chains of thought emerge, along with self-correction and backtracking; the released model adds a small supervised cold-start stage to fix the readability and language-mixing that pure RL produced. Its reasoning is emitted as visible text before the answer.

Reach for it when you need reasoning depth on open weights, or when the chain of thought itself must be inspectable rather than hidden behind an API. The costs are the reasoning tokens: latency and spend scale with how long it thinks, and on simple extraction or formatting work it burns budget for no gain. Smaller distilled variants trade some of that depth back for speed.

### DeepSeek-V2/V3
**Short:** Open-weight MoE LLM family whose multi-head latent attention compresses the KV cache by roughly 93% at long context.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, inference/quantization-and-compression @3, caching/semantic-and-llm-cache @3

Two architectural choices carry the family. Multi-head latent attention projects keys and values down to a single low-rank latent vector that is what gets cached, and reconstructs per-head keys and values on the fly, which is what shrinks KV memory so far at long context. The mixture of experts is fine-grained -- many small experts plus a few always-on shared ones -- so V3 activates roughly 37B of its 671B parameters per token, with load balancing handled by bias adjustment instead of an auxiliary loss.

Reach for it when you want frontier-class open weights and can serve MoE. That is the catch: total parameters, not active ones, set the memory you must provision, so inference needs a multi-GPU node even though each token is cheap. A dense model of the active size is far easier to host if throughput rather than quality is binding.

### Detectron2
**Short:** Meta's PyTorch object-detection and segmentation library: Faster R-CNN, Mask R-CNN and Panoptic FPN baselines.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

You register a dataset in COCO format, pick a config from the model zoo (Faster R-CNN, RetinaNet, Mask R-CNN, Panoptic FPN) and train with a `DefaultTrainer`; the code is organized as swappable backbone, neck and head modules, so a research change means overriding a component rather than forking the repository.

It is the natural pick for instance or panoptic segmentation, or a two-stage detector whose numbers must be comparable with published baselines. For an engineer who mainly wants a fast detector in production, a single-stage stack with a simpler training loop and export path gets there sooner — Detectron2's flexibility comes with a steep config system.

### DETR
**Short:** DEtection TRansformer: end-to-end object detection with set prediction, no anchors or NMS.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

A CNN backbone feeds a transformer encoder-decoder, and the decoder holds a fixed set of learned object queries, each emitting one class -- including a no-object class -- and one box. Training matches predictions to ground truth with the Hungarian algorithm, so exactly one query is responsible for each object and duplicates are penalised directly. That bipartite matching is what removes anchor boxes and non-maximum suppression: the whole detection pipeline becomes one differentiable model.

Its importance is conceptual; the original is a poor production choice, converging only after hundreds of epochs and performing badly on small objects because the queries start with no spatial prior. Deformable DETR, DINO and their successors fixed both by attending to sparse reference points and adding denoising training, and those are what you would actually deploy from this line.

### DGL
**Short:** Deep Graph Library for GNNs on PyTorch or TensorFlow; stronger than PyG for dynamic, heterogeneous graphs.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @2

A graph is a first-class object holding node and edge features, and computation is expressed as message passing: a message function on each edge, a reduce function on each destination node, run by `update_all` with fused built-in kernels for the common sum-of-copied-features case. Heterogeneous graphs are native -- typed nodes and typed edge relations in one object -- and relation-specific convolutions are composed over them rather than encoded by hand.

Reach for it when the graph has several node and edge types, as most real knowledge graphs and recommender graphs do, or when the model must run on a backend other than PyTorch. PyTorch Geometric has the larger community and more implemented layers, so for a plain homogeneous graph it is usually the faster path; DGL earns its place on the heterogeneous and industrial-scale end.

### dice-ml
**Short:** Microsoft library generating diverse counterfactual explanations (random, genetic, gradient) for a model's decision.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It generates a set of counterfactuals rather than one, optimizing three things at once: validity (the model flips its decision), proximity (few features change, and by little), and diversity (the alternatives differ from each other), so the user sees several genuine routes instead of a single arbitrary one. You declare which features are immutable and what ranges are legal, and pick a method -- random or genetic search for any model, gradient-based for a differentiable one.

Reach for it when the explanation must be actionable: the applicant wants to know what to change, not which feature weighed most. Two limits matter. A counterfactual is a statement about the model's decision boundary, not a promise about the world, so it is not causal advice. And with correlated features an unconstrained search will suggest changes that are impossible in combination.

### diffusers
**Short:** Hugging Face library for diffusion models: DDPM/DDIM schedulers, Stable Diffusion and ControlNet pipelines.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

The library separates three things that papers tangle together: models (a UNet or transformer denoiser, a VAE, a text encoder), schedulers (the noise schedule and the update rule -- DDPM, DDIM, DPM-Solver, Euler), and pipelines that wire them into a task such as text-to-image, image-to-image or inpainting. Because the scheduler is a swappable object, changing sampler and step count is a one-line edit against the same weights.

Reach for it whenever a diffusion checkpoint has to run, be fine-tuned, or be extended with LoRA, ControlNet or adapters. It is a research and development library rather than a serving stack: memory optimizations like attention slicing and CPU offload are opt-in, and for high-throughput generation a compiled or specialized runtime will beat the eager pipeline substantially.

### DINO-Det
**Short:** Transformer-based object detector from the DETR line, used as a strong detection backbone in vision pipelines.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

This is the detector in the DETR line whose name collides with Meta's self-supervised DINO -- unrelated work, same four letters. It fixes DETR's slow convergence with contrastive denoising training, where noised copies of ground-truth boxes are fed in as extra queries so the decoder learns to refine boxes instead of discovering them from scratch, along with mixed query selection that initialises queries from encoder features and a look-forward-twice box update.

Reach for it when you want end-to-end detection without anchors or non-maximum suppression at accuracy that competes with tuned two-stage detectors, and it is the natural backbone when the next step is open-vocabulary grounding. It remains heavier to train and slower per image than a single-stage YOLO-class detector, which is still the pick when the constraint is frames per second on modest hardware.

### DINOv2
**Short:** Meta's self-supervised vision transformer checkpoints (ViT-S/B/L/g) giving strong general-purpose image features.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

It is trained with no labels and no text: a student network is asked to match a teacher's output on different crops of the same image, with the teacher being an exponential moving average of the student, combined with a masked-image objective and a regularizer that spreads features apart to stop collapse. The training corpus was assembled by retrieval-based curation rather than scraped wholesale, and large models were distilled down into the smaller checkpoints.

The features are strong enough that a linear probe on frozen output is competitive on classification, depth and segmentation, which makes it the default frozen backbone when you have images and few labels. Unlike CLIP it has no text tower, so there is no zero-shot classification from label names and no text-to-image retrieval; use CLIP or SigLIP when the query is language.

### DistilBERT
**Short:** Distilled 6-layer BERT: 40% smaller, ~60% faster, most of BERT's accuracy; a cheap classification/NER workhorse.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, inference/quantization-and-compression @2

Distillation happened during pretraining, not after: a six-layer student initialised from every other layer of BERT-base was trained with three losses at once -- the usual masked-language objective, a KL term matching the teacher's soft output distribution, and a cosine term aligning the hidden states. The soft targets carry more information per example than a hard label, which is why the student keeps most of the teacher's quality at half the depth.

It is a sensible default when a classifier or tagger has to run on CPU at volume and the last point of accuracy is not worth the latency. Its ceiling is BERT-base's: a 512-token limit and 2018-era pretraining data. For new work a modern small encoder such as ModernBERT gives longer context and better quality at similar cost.

### DL Streamer
**Short:** GStreamer-based framework for building video-analytics pipelines with OpenVINO inference elements inline.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, inference/compiler-and-runtime-optimization @2

Inference becomes a GStreamer element. You build an ordinary pipeline -- source, decode, transform, sink -- and drop in `gvadetect`, `gvaclassify` or `gvainference` elements that run an OpenVINO model on each frame, with `gvatrack` for object tracking and `gvametaconvert`/`gvametapublish` to emit results as JSON to a file, MQTT or Kafka. Detections ride along as metadata attached to the buffer, so downstream elements can crop, count or draw without a second decode.

The reason to use it on Intel hardware is that decode, colour conversion and inference can all stay on the GPU through VAAPI and zero-copy surface sharing, so frames never round-trip to host memory -- which is what makes many concurrent camera streams feasible on one box. On NVIDIA hardware DeepStream is the direct equivalent; a hand-rolled OpenCV loop is simpler but loses the hardware decode path.

### doubleml
**Short:** Double/debiased machine learning library for treatment-effect estimation following Chernozhukov et al.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

Double machine learning estimates a treatment effect in two stages: predict the outcome from the covariates, predict the treatment from the covariates, and estimate the effect from the residuals of both. That Neyman-orthogonal score makes the estimate insensitive to first-order error in either nuisance model, and cross-fitting -- fitting nuisances on one fold and evaluating on another -- removes the overfitting bias that would otherwise creep in from using flexible learners.

Use it when confounders are numerous or their functional form is unknown, and you need a standard error and confidence interval rather than a point estimate. The whole guarantee rests on unconfoundedness, which no amount of machine learning supplies; and with weak overlap, where treatment is nearly deterministic given covariates, the inverse-propensity weights explode and the interval becomes useless.

### DoWhy
**Short:** Causal inference library that makes you state a DAG, then identifies, estimates and refutes the effect.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

The library enforces a four-step discipline. You model the problem by writing the causal graph explicitly; it identifies an estimand from that graph using the back-door, front-door or instrumental-variable criteria; it estimates the effect with a method you choose, including handing off to EconML for machine-learning estimators; and then it refutes -- replacing the treatment with a placebo, adding a random common cause, dropping a data subset, simulating an unobserved confounder -- and reports whether the estimate survives.

That fourth step is the reason to reach for it: refutation tests do not prove the answer right, but they catch the estimates that fall apart under trivial perturbation, which a regression coefficient never tells you. The graph is still an assumption you must defend, and a wrong graph yields a confidently identified, confidently wrong estimand.

### dtreeviz
**Short:** Python library drawing rich decision-tree visualizations with split distributions; clearer than sklearn's plot_tree.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

Instead of drawing a box of split statistics per node, it draws the data: each internal node shows the distribution of samples reaching it with the split point marked on the axis, and each leaf shows the class mix or target spread of what landed there. It can also trace one instance's path down the tree, highlighting the branch taken at every split.

That makes it the right tool for explaining a shallow tree to a non-specialist, or for spotting that a split is separating almost nothing. It does not scale: beyond a handful of levels the picture is unreadable, which is a property of trees rather than the library. For a deep ensemble, feature importance and SHAP are the tools that still say something.

### EconML
**Short:** Microsoft library for heterogeneous treatment-effect estimation: Double ML, DR-learner and causal forests.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It answers who to treat rather than whether the treatment worked on average. The estimators split the problem into nuisance models you supply from scikit-learn (predicting the outcome and the treatment assignment) and a final model for the effect itself, which is what makes double machine learning and doubly robust learners robust to moderate error in those nuisance models; causal forests instead partition the data to find subgroups whose effects differ.

Reach for it when a uniform intervention is wasteful and you want to target it, such as which users a discount actually moves. It does not rescue bad identification: with unmeasured confounders the estimates are as biased as any regression, so the design still has to be randomized or credibly unconfounded.

### EleutherAI sae library
**Short:** Open-source library for training sparse autoencoders (including TopK) on open models for interpretability.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

A sparse autoencoder reconstructs a model's internal activations through a much wider hidden layer with very few units active, on the hypothesis that the network superposes many meaningful features into fewer dimensions and an overcomplete sparse basis can pull them apart. This library trains them at scale on open models with a top-k activation that keeps exactly the k largest latents rather than an L1 penalty, which removes that penalty's shrinkage and makes sparsity a dial you set instead of a coefficient you tune.

Reach for it when you want your own dictionary over a specific model and layer. It is expensive: activations for billions of tokens must be generated and streamed, and a latent is only a direction until someone labels what it fires on. Loading pretrained SAEs through SAELens is far cheaper when one already exists for your model.

### ElevenLabs
**Short:** Commercial text-to-speech and voice-cloning API used for high-quality synthesized speech in voice agents.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

You send text and a voice id over an HTTP API and get audio back, with a streaming mode that starts returning bytes before the whole utterance is synthesized — which is what makes a spoken agent feel responsive instead of laggy. Voices come from a shared library, or you create one by cloning from a sample, and models cover many languages from the same voice.

Reach for it when the perceived quality of the voice is part of the product and you would rather buy that than train it. The constraints are the usual hosted-API ones plus one specific to voice: per-character billing, a network round trip inside your latency budget, and a consent and rights problem if you clone a real person. Where audio cannot leave your infrastructure, a self-hosted TTS model is the alternative.

### Ember API
**Short:** Hosted API exposing sparse-autoencoder features and activation steering for open-weight models.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It is the hosted interface to Goodfire's interpretability stack: rather than prompting a model, you inspect and manipulate its internals over HTTP. A request returns which sparse-autoencoder features activated on a given input, and a request can also pin a feature on or off, so behaviour is steered by editing an activation direction at inference rather than by fine-tuning or by adding instructions to the prompt.

The appeal is control that survives a jailbreak of the prompt, and a diagnostic that says what the model was representing when it went wrong. The constraints are real: it only works on models the provider has trained dictionaries for, activations leave your infrastructure, and a steering direction that is too strong degrades fluency. Self-hosting SAELens over open weights is the alternative when neither is acceptable.

### ESPnet
**Short:** End-to-end speech research toolkit covering CTC/RNN-T/attention ASR, self-supervised speech models and TTS recipes.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

It follows Kaldi's recipe structure -- a per-dataset directory with numbered stages you run in order for data preparation, feature extraction, training and decoding -- but the models are PyTorch. ASR is trained with a joint CTC and attention objective and decoded with both scores combined, which stabilises alignment; the encoders are Conformers, and the same framework carries transducer models, TTS such as FastSpeech and VITS, speech enhancement, separation and translation.

Reach for it to reproduce a published speech result or to train a domain ASR model where you control the data pipeline end to end. The cost is the recipe machinery: configuration is deep, the shell-and-YAML layers are hard to debug, and for simply transcribing audio a pretrained Whisper or a Hugging Face checkpoint is a fraction of the effort.

### explainerdashboard
**Short:** Python library turning a fitted model into an interactive SHAP-based explanation dashboard for stakeholders.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

Point it at a fitted scikit-learn-compatible model and a dataset and it builds a running web application: SHAP-based global importance, per-feature dependence, an individual-prediction page with contributions, what-if sliders, a confusion matrix and threshold explorer, and decision-tree views for ensembles. The whole thing is a couple of lines, and it can be exported as a static HTML file to attach to a review.

Reach for it when a model needs to be shown to people who will not run a notebook -- a risk reviewer, a domain expert sanity-checking the features. Treat it as a tool for tabular models of moderate width: it computes SHAP values up front, so wide data or a large sample makes startup slow, and it is not a monitored production surface.

### facebookresearch/blt
**Short:** Meta's Byte Latent Transformer reference code: entropy model, local encoder/decoder, tokenizer-free patches.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

The Byte Latent Transformer removes the tokenizer by making the segmentation dynamic: a small byte-level model measures the entropy of the next byte, and a patch boundary is cut wherever that entropy spikes, so predictable runs become long patches and hard positions get their own. A local encoder compresses each patch's bytes into one representation, a large global transformer runs over patches, and a local decoder expands back to bytes -- so compute is allocated by difficulty rather than by a fixed vocabulary.

The payoff is no vocabulary to choose, no out-of-vocabulary behaviour, robustness to noisy input and misspellings, and fair treatment of scripts a BPE vocabulary under-serves. This is a research reference implementation rather than a deployable stack: the three-model structure complicates serving, and the tooling ecosystem all assumes tokens.

### fairlearn
**Short:** Microsoft's fairness toolkit: MetricFrame group metrics plus reduction and threshold-optimizer mitigation algorithms.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @3

Two halves. Assessment is `MetricFrame`, which takes any metric function, a sensitive feature column, and computes the metric per group plus the difference and ratio between the best and worst -- so the question becomes which slice the model fails rather than what its average is. Mitigation offers reductions, which repeatedly re-weight and refit an unmodified estimator until a constraint such as demographic parity or equalized odds holds, and a post-processing threshold optimizer that picks per-group decision thresholds after the fact.

Reach for it as the default fairness library in a scikit-learn codebase: the objects compose with pipelines and the reduction approach leaves your model class alone. It cannot tell you which constraint is the right one -- that is a policy decision, and the definitions are mathematically incompatible -- and per-group thresholds may themselves be unlawful in some jurisdictions.

### fairseq
**Short:** Meta's sequence-modelling toolkit for training translation and language models with fast beam-search decoding.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @2, model-training/distributed-training @3

It is driven from the command line rather than as a library: preprocess a corpus into a binarized dataset, train with a named architecture and task, then generate with beam search. That structure is why published translation and language-model results ship as a fairseq config plus a checkpoint, and reproducing them is mostly a matter of running the recorded command.

Reach for it to reproduce or extend one of those recipes. New work generally starts in Hugging Face transformers instead, which has the wider model coverage and the more familiar Python API.

### fasttext
**Short:** Library for sub-word word embeddings and an ultra-fast linear n-gram text classifier that trains on CPU in seconds.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3, search-retrieval/ann-index-library @3

fastText does two related things. It learns word embeddings from character n-grams, so a word never seen in training still gets a vector by summing the n-grams it is made of, which is why it holds up on misspellings and morphologically rich languages. It also ships a supervised classifier that is a linear model over a bag of word and n-gram embeddings, with hierarchical softmax for large label sets, and it trains on millions of documents in seconds on CPU.

Reach for it as the baseline before a transformer: language identification, query intent, topic tagging and spam filtering are often solved well enough by it at a fraction of the serving cost. The upstream repository is archived, so pin the version you build against.

### FiftyOne
**Short:** Open-source tool for visualizing, curating and error-analysing image/video datasets and model predictions.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/labeling-and-synthetic-data @2, ml-lifecycle/evaluation-and-benchmarks @2

You load images or video with their ground-truth labels and model predictions into a `Dataset`, then interrogate it in the app: filter to low-confidence predictions, sort by per-sample error, view boxes and masks overlaid, and run detection or segmentation evaluation that reports mAP, IoU and a confusion matrix per sample instead of as one aggregate number.

This is the tool for the question of why mAP is 0.62, which usually turns out to be mislabeled data, a rare class, or systematic failure on one condition rather than a modelling problem. It also does curation through embeddings — near-duplicates, outliers — but it is an analysis surface, not a labeling platform or a pipeline runner.

### fine-tuned encoders
**Short:** Small BERT-style encoders fine-tuned per task, the cheap millisecond alternative to an LLM for classification and NER.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, model-training/fine-tuning-and-peft @2

An encoder reads the whole input bidirectionally in one forward pass and a small classification head on top of it emits a fixed label set -- no decoding loop, no sampling, no output parsing. Fine-tuning one takes a few thousand labelled examples and minutes on a single GPU, and the resulting model is a few hundred megabytes running in single-digit milliseconds on CPU, with a probability per class you can calibrate and threshold.

That is the right shape whenever the taxonomy is fixed and the volume is high: intent detection, routing, PII tagging, content classification, entity extraction. The price is labels, and a retrain every time the taxonomy changes. An LLM is the answer when no labels exist, when the categories keep moving, or when the task needs reasoning rather than pattern recognition -- and a common pattern is to use the LLM to label the data the encoder then learns from.

### FinGPT
**Short:** Open finance-domain LLM family fine-tuned for financial sentiment, entity and report analysis.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, model-training/fine-tuning-and-peft @3

It is deliberately the opposite bet from training a financial model from scratch: take an open base model and apply low-rank adapters on financial instruction data -- sentiment on news and filings, entity and relation extraction, report summarization -- so a domain model costs GPU-hours rather than a pretraining run. The project also ships the data pipelines, since its argument is that the value is in continuously refreshed financial text, and adapters can be re-fit cheaply as the market's language moves.

Reach for it as a reference for how to build a domain model on a budget, or as a starting adapter for financial sentiment. Treat the published numbers with care: financial sentiment datasets are small and noisy, and none of this addresses the actual production problems of numerical accuracy, citation to a source filing, or point-in-time correctness of the data.

### Fireflies.ai
**Short:** Hosted meeting-transcription and summarization assistant that joins calls and produces notes.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

A bot joins the meeting as a visible participant on Zoom, Meet or Teams, records the audio, and returns a speaker-attributed transcript with a summary, action items and topic markers, all searchable across the workspace and pushed into a CRM or ticketing tool. Because it attends as a guest rather than needing a desktop recorder, coverage does not depend on anyone remembering to hit record.

Reach for it when meeting notes are the deliverable and the calendar is the natural trigger. The costs are organizational more than technical: recordings and transcripts of every internal conversation now live with a third party, several jurisdictions require all-party consent to record, and diarization degrades badly on a crowded room with one shared microphone. A self-hosted Whisper plus a diarization model is the alternative when the audio cannot leave.

### Flair
**Short:** NLP library combining contextual embeddings with BiLSTM-CRF for strong multilingual sequence labeling.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

Its distinctive contribution is the contextual string embedding: a character-level bidirectional language model whose hidden state at a word's boundaries becomes that word's vector, so representations are built from characters and reflect the surrounding sentence -- which handles misspellings, rare inflections and unseen words without a subword vocabulary. Those stack with classical or transformer embeddings and feed a BiLSTM-CRF tagger.

The API is unusually direct: build a sentence object, call predict on a pretrained tagger, read the labels off the spans. Reach for it for multilingual or historical-text sequence labelling, or when a corpus is full of noisy word forms. It is slower than spaCy for bulk processing and the surrounding pipeline (rules, components, serialization) is thinner, so it suits the tagging task rather than a whole document pipeline.

### Florence-2-large
**Short:** Microsoft's compact vision-language model handling captioning, detection, grounding and OCR from one prompt interface.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

One sequence-to-sequence model handles every vision task by treating the task itself as part of the prompt: a special token selects captioning, object detection, dense region captioning, phrase grounding, segmentation or OCR, and coordinates are emitted as quantized location tokens in the output text, so a box is just more tokens rather than a separate head. It was trained on an automatically generated annotation corpus far larger than any hand-labelled detection dataset.

That makes it a very strong return on its size -- under a billion parameters covering work that would otherwise need a detector, a captioner and an OCR engine, at a footprint that runs on modest hardware. It follows a fixed set of task prompts rather than free-form instructions, so it is not a conversational VLM; when the requirement is reasoning about an image in dialogue, a chat-tuned model is the fit.

### Frontier hosted VLMs
**Short:** The current top-tier hosted vision-language models, used to describe charts and diagrams during document ingest.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/rag-and-document-processing @2

The mechanism that matters during ingest is that these models take images and text interleaved in a single request, so a page's chart or diagram can be sent with the surrounding prose and a prompt asking for a faithful description. That description is then chunked, embedded and indexed like any other text, which is the only way a purely lexical or text-embedding retriever ever finds the content of a figure.

Reach for them when documents carry meaning in pictures -- financial charts, architecture diagrams, screenshots, scanned tables -- and losing them would gut the index. The costs are per-image tokens that scale with resolution, a slow ingest stage, and non-determinism, so cache descriptions keyed on the image hash and treat them as a build artifact. A self-hosted open VLM is the fallback when documents cannot leave your boundary.

### Gemini 1.5 Pro
**Short:** Google's natively multimodal LLM handling text, image, audio and video with a very long (up to 1M token) context window.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/prompting-context-and-structured-output @3

Its two notable properties are a sparse mixture-of-experts architecture and a context window measured in the millions of tokens, with recall across it that held up under needle-in-a-haystack probing rather than degrading in the middle. Multimodality is native rather than adapter-bolted: video is sampled into frames and tokenized alongside audio and text, so hours of footage or an entire codebase can go into a single prompt.

That capability changed the shape of some problems -- you can put whole documents in the prompt instead of building a retrieval pipeline for them. The tradeoffs are cost and latency proportional to the context you actually send, so long-context prompting is the expensive answer where retrieval is the cheap one, and it should be reserved for cases where relevance genuinely cannot be narrowed in advance. Later Gemini generations supersede this release.

### Gemini Diffusion
**Short:** Google's experimental diffusion text model, refining tokens in parallel passes rather than left to right.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

It replaces left-to-right decoding with iterative refinement: a block of output tokens is initialised as noise and denoised over several passes, every position updated in parallel, so the number of sequential steps is set by the refinement schedule rather than by the length of the output. That also lets the model revise tokens it has already emitted, which autoregressive decoding cannot do -- a property that suits editing and code, where a later constraint should be able to change an earlier line.

Treat it as experimental rather than a production choice: availability is limited, quality on long-form reasoning trails frontier autoregressive models, and the tooling around streaming, tool calls and token budgeting all assumes sequential generation. The reason to track it is the latency ceiling it implies if diffusion decoding keeps closing the quality gap.

### Gemini Live
**Short:** Google's low-latency multimodal API for end-to-end speech-to-speech conversation with interruption handling.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/agent-framework @3

It is a speech-native model rather than a text model with speech bolted on: audio goes in and audio comes out without an intermediate transcription-and-resynthesis round trip, so prosody, pacing and emotion in the input survive into the response and the turn latency is one model hop instead of three. Interruption is handled in the model's turn logic, so a user talking over it cuts the response rather than queueing behind it.

Reach for it when the conversation must feel like a conversation -- interruptions, backchannels, natural pauses. The tradeoffs are that the intermediate text is no longer the source of truth you can log, filter and moderate before it is spoken, cost is billed in audio tokens rather than the cheaper text ones, and swapping model vendors means re-plumbing the whole audio path rather than one component.

### Gemini Live API
**Short:** Google's bidirectional streaming endpoint carrying live audio and video to Gemini, for voice agents.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, apis-frameworks/rpc-graphql-and-streaming @2, llm-apps/agent-framework @3

This is the transport: a persistent bidirectional connection over which the client streams audio and video chunks up and receives audio, text transcripts and tool-call events down, all as discrete messages on the same channel. Server-side voice activity detection decides when the user has stopped talking, function calls can fire mid-conversation, and the session carries conversational state so each turn is not a fresh request.

Reach for it when building a real-time voice or video agent on Gemini and you want turn-taking handled for you rather than assembled from a separate detector, recognizer and synthesizer. The engineering costs are the ones streaming always brings: sessions have time limits and must be resumed, a dropped socket loses in-flight audio, and browser and telephony audio formats need converting on the way in.

### gensim
**Short:** Python library for classic word embeddings (word2vec, fastText, GloVe) and topic models (LDA, LSI, NMF).
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3, search-retrieval/ann-index-library @3

Models train from a streamed corpus - you hand it an iterable of tokenized documents rather than a matrix in memory - so a corpus far larger than RAM trains fine, and the C-backed word2vec and fastText implementations use multiple threads. `KeyedVectors` loads pretrained vectors, including GloVe and word2vec text formats, and answers nearest-neighbour and analogy queries without the training machinery attached.

Topic modeling is the other half: LDA, LSI and NMF with `CoherenceModel`, which lets you pick the number of topics by a measured score instead of by eye. Be clear about what these embeddings are - static and context-free, one vector per word type - so for sentence-level semantics a transformer encoder wins outright. It still earns its place for topic models, for fast baselines, and for training domain-specific embeddings on a corpus where general-purpose vectors have never seen your vocabulary.

### GLiNER
**Short:** Compact zero-shot named-entity model that extracts arbitrary user-named entity types far cheaper than prompting an LLM.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/rag-and-document-processing @2

It reframes named-entity recognition as matching rather than classification. The entity type names you supply are embedded as text by the same bidirectional encoder that reads the input, and candidate spans are scored against those type embeddings, so a label set invented at inference time works without retraining -- and the model is a few hundred megabytes rather than several gigabytes, because it never has to generate.

Reach for it when the entity types change per customer or per document type and prompting an LLM for every record would be absurd on cost and latency. It runs comfortably on CPU and returns spans with offsets, which an LLM does not reliably do. Accuracy trails a model fine-tuned on your actual labels, and it degrades when the type name is ambiguous, so name the types carefully -- the label string is the prompt.

### GloVe
**Short:** Static word embeddings learned by factorizing a global co-occurrence matrix; downloadable pretrained vectors.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/ann-index-library @3

It fits vectors to corpus statistics rather than to a prediction task: build the global word-word co-occurrence matrix once, then learn word and context vectors whose dot product plus biases reproduces the log of each co-occurrence count, weighted so that very frequent pairs do not dominate. Because the counts are aggregated first, training is a matrix factorization over the whole corpus rather than a pass over sliding windows.

The pretrained releases -- several corpus sizes at 50 to 300 dimensions -- are still a fine way to get a semantic baseline or initialise an embedding layer with no GPU. The limits are those of any static embedding: one vector per word type regardless of sense, no way to embed a word that was not in the training vocabulary, and no sentence-level meaning. A transformer encoder wins outright for anything contextual.

### GluonTS
**Short:** Probabilistic time-series forecasting library with DeepAR and Temporal Fusion Transformer on PyTorch backends.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/deep-learning-framework @3

Every model outputs a distribution, not a number: you pick a distribution head appropriate to the data -- Student's t for continuous, negative binomial for counts -- or predict quantiles directly, and the forecast is a set of sample paths from which any interval is read. Models are global, trained across all series at once so a short series borrows strength from the population, and evaluation uses weighted quantile loss and MASE rather than plain error.

Reach for it when the decision downstream needs the upper tail rather than the mean -- inventory, capacity, staffing -- and when you have many related series. The costs are a rigid dataset abstraction that is fiddly to build, and the fact that a global deep model needs a real corpus of series to beat a per-series statistical baseline, which statsforecast will fit in a fraction of the time.

### Goodfire
**Short:** Hosted interpretability API exposing sparse-autoencoder features and steering for open-weight models.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1, security/ai-safety-and-guardrails @3

The company productizes sparse-autoencoder interpretability: dictionaries are trained over open-weight models so that internal activations decompose into named features, and those features are then exposed as something you can both read and write. Reading gives a per-request account of what concepts were active, which is a different signal from the output text. Writing means clamping a feature up or down so behaviour shifts at inference, with no fine-tuning and no prompt change.

The pitch is control and observability that a prompt cannot give: a safety property enforced in activation space is not talked out of the model the way an instruction is. In practice you are limited to the models they have dictionaries for, and steering hard enough to matter often costs coherence. Prompt-level guardrails and fine-tuning remain the mainstream tools; this is the frontier option.

### GoogleNews-vectors-negative300.bin
**Short:** Canonical pretrained word2vec embeddings: 3M words at 300 dimensions, roughly 3.5GB.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/ann-index-library @3

This is the original word2vec release: skip-gram with negative sampling trained on a very large news corpus, published as a binary file of three million entries at 300 dimensions. The vocabulary includes multi-word phrases joined with underscores, which is why it appears to know named entities, and the binary format loads directly as a keyed-vector store without any training machinery attached.

It is still the fastest way to get a semantic similarity baseline or to reproduce a classic result. Understand what you are loading: a frozen 2013 vocabulary with no subword fallback, so anything absent -- a new product name, a typo -- has no vector at all, and the file wants several gigabytes of RAM. For current work, fastText handles unseen words and a sentence encoder handles meaning in context.

### GPT-2 byte-level BPE
**Short:** The byte-level BPE scheme that made subword vocabularies byte-safe; the tokenized baseline for comparison.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, runtime-systems/text-encoding-and-regex @3

The base vocabulary is the 256 byte values rather than Unicode characters, so every possible input encodes and there is no unknown token -- the merge table is learned over bytes and reversibly mapped to printable characters so whitespace and control bytes stay visible. A regular expression splits text into words, numbers, punctuation and contractions before merging, which stops merges from spanning word boundaries, and the leading space is folded into the token, so a word at the start of a line and the same word mid-sentence are different tokens.

That design is why it became the baseline every tokenizer-free scheme is measured against. Its costs are the ones byte-level BPE always carries: languages outside the training mix are fragmented into many more tokens per word and therefore cost more, digits split unpredictably which hurts arithmetic, and the vocabulary bakes in the distribution of the corpus it was fitted on.

### GPT-4o
**Short:** OpenAI's multimodal flagship: text, image and audio in one model, strong at multi-image reasoning.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

The 'omni' part is that one model was trained across text, image and audio rather than wiring a speech recognizer and a synthesizer around a text model, which is what collapsed spoken-conversation latency to something near human turn-taking and let tone and background sound reach the model at all. Images are consumed as tokens in the same context as text, so a screenshot, a chart and a question interleave in one request.

It was for a long time the sensible default for multimodal work: good enough at vision, fast, and cheap relative to the frontier tier. Later OpenAI models supersede it on both quality and price, and for pure reasoning depth the reasoning-model line is the better spend. Its lasting relevance is as the point where multimodality stopped being a separate pipeline.

### GPT-4o API
**Short:** OpenAI's hosted multimodal endpoint accepting image and text input alongside text generation.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/llm-gateway-and-routing @2

Images are content parts in the same message array as text, supplied as a URL or a base64 data URI, and a detail setting controls whether the image is downscaled to a cheap fixed-cost thumbnail or tiled into high-resolution crops that each add tokens. That means image cost is predictable and controllable, and it is the first thing to tune when a document pipeline's bill is dominated by vision rather than text.

Reach for it when a request has to reason over both a picture and instructions and you would rather not stand up a model. The constraints are the ones every hosted multimodal endpoint has: images count against the context window, there is no coordinate output so you cannot draw a box back on the page, and rate limits are usually token-based, which images consume quickly.

### GPT-4V
**Short:** OpenAI's vision-capable GPT-4 endpoint: reasoning over one or many images alongside a text prompt.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/llm-gateway-and-routing @3

This was the vision capability added to GPT-4, and it established the interface everyone copied: images inline in the conversation, several at once, with the model reasoning across them and against the text around them rather than describing each in isolation. Comparing two screenshots, reading a whiteboard photo, or explaining what is wrong in a chart all became single prompts.

It also established the limits that still hold for general VLMs. There is no spatial output, so it cannot ground an answer to a region; small dense text and precise counting are unreliable; and it will describe something plausible rather than admit it cannot read a blurry field. For extraction that must be auditable, pair a VLM with a real OCR engine that returns coordinates and confidences.

### GraphBolt
**Short:** Graph data-loading framework for TB-scale GNN training, replacing older neighbour-sampling loaders.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, data-movement/batch-and-distributed-compute @3

It is DGL's rewrite of the data path, on the finding that large-scale GNN training is bottlenecked on sampling and feature fetching rather than on the convolutions. The graph is held in a compact compressed-sparse structure separate from the feature store, sampling can run on the GPU, and the loader is a pipeline that overlaps neighbour sampling, feature gathering and transfer with the previous batch's compute instead of serialising them.

Reach for it when the graph or its features exceed host memory and the GPU is sitting idle waiting for batches -- the regime where a naive loader spends most of its time in Python. For a graph that fits comfortably in memory the extra machinery buys little, and the classic samplers remain simpler to reason about. Expect to restructure the training script rather than swap one class.

### grf
**Short:** Generalized Random Forests: the Athey lab R package for causal forests and heterogeneous treatment effects.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @2

A forest here is not an ensemble of predictions but an adaptive weighting scheme: the trees are grown to split where the quantity of interest varies most, and the fraction of trees in which a training point lands in the same leaf as the target point becomes that point's weight in a local estimating equation. Honesty is enforced by using one subsample to choose splits and a different one to estimate values in the leaves, which is what makes the resulting confidence intervals valid rather than optimistic.

Reach for causal forests when you need heterogeneous treatment effects with inference attached -- an estimate plus an interval per subgroup, and a variable-importance ranking of what drives the heterogeneity. It is an R package, so it sits awkwardly in a Python stack, where EconML implements much the same family; and like every such method it assumes unconfoundedness that the data cannot verify.

### Grounding DINO
**Short:** Open-vocabulary object detector from IDEA-Research that localizes objects described by an arbitrary text prompt.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

It fuses language into a DETR-style detector at three points: a text encoder reads the prompt, a cross-modality feature enhancer mixes image and text features, the queries fed to the decoder are selected using the text, and the decoder attends to both. The prompt is a list of phrases rather than a fixed class list, so the model returns boxes together with the phrase each one matches, and new categories cost a prompt edit instead of a training run.

Reach for it when the label set is not known in advance, or as the first stage of a pipeline that turns a text prompt into masks by handing the boxes to a segmentation model. The costs: it is slow next to a closed-vocabulary detector, accuracy varies sharply with how a category is phrased, and long prompts with many phrases dilute performance -- so prompt wording becomes a thing you must tune and version.

### HiFi-GAN
**Short:** GAN-based neural vocoder converting mel-spectrograms to waveform audio, the standard TTS synthesis backend.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

The generator upsamples a mel spectrogram to waveform sample rate with transposed convolutions, each stage followed by parallel dilated residual blocks whose outputs are summed, so several receptive-field sizes are modelled at once. The discriminators are what make it work: one splits the waveform by period into 2D slices to catch the periodic structure of voiced speech, another looks at multiple time scales, and training adds feature-matching and mel-reconstruction losses on top of the adversarial one.

It is the default vocoder because it generates in a single non-autoregressive pass, runs faster than real time even on CPU, and sounds close to WaveNet at a fraction of the cost. It is only as good as the mel it is given, so artifacts from the acoustic model pass straight through, and a vocoder trained on one speaker or sample rate does not transfer without fine-tuning.

### holisticai
**Short:** Multi-framework fairness toolkit measuring and mitigating bias across tabular, NLP and vision models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

Its scope is what distinguishes it: bias metrics and mitigations are provided not only for binary classification but for regression, multiclass, clustering and recommender systems, where most fairness libraries stop -- exposure and equality of opportunity across items in a ranking are a different measurement problem from group error rates in a classifier. Mitigations are offered at the usual three stages, pre-, in- and post-processing, alongside modules for explainability, robustness and privacy risk.

Reach for it when the system being audited is a ranker or a segmentation, and the standard toolkits have nothing to say about it. For plain tabular classification, fairlearn and aif360 are more established, better documented and more likely to match what a reviewer expects to see. As with every such library, the metric choice is a policy decision it cannot make for you.

### HuggingFace diffusers
**Short:** HuggingFace library of diffusion pipelines and schedulers for image, audio and increasingly text generation.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

What makes the checkpoint ecosystem usable is that a repository id resolves to a whole pipeline -- denoiser, VAE, text encoder, scheduler config and preprocessing -- so loading a community fine-tune is one call and it comes wired correctly. On top of that sit the composition mechanisms: LoRA weights loaded into an existing pipeline, ControlNet and adapters attached for spatial conditioning, and components swapped between pipelines because they share interfaces.

Reach for it as the standard runtime for open image generation and for the training scripts behind LoRA and DreamBooth fine-tuning. The memory knobs -- attention slicing, VAE tiling, sequential CPU offload -- are opt-in, and forgetting them is the usual reason a pipeline exhausts a consumer GPU. For serving at volume, a compiled or quantized deployment path outruns the eager pipeline by a wide margin.

### HuggingFace tokenizers
**Short:** Rust-backed BPE/WordPiece/Unigram tokenizer training and inference, roughly 100x faster than pure Python.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, runtime-systems/text-encoding-and-regex @3

The whole pipeline -- normalizer, pre-tokenizer, model, post-processor and decoder -- is implemented in Rust with thin Python bindings, and it can train a vocabulary from a corpus as well as apply one. Crucially the encoding carries offset mappings back into the original string, which is what makes token-level NER, question answering and span highlighting align with the raw text.

You usually meet it through `transformers`, where the "fast" tokenizer classes are this library. Reach for it directly when you are training your own vocabulary or tokenizing a large corpus where the pure-Python path would dominate your preprocessing time.

### Hunspell
**Short:** Spell checker and morphological analyzer used for query correction and stemming in search pipelines.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/lexical-and-hybrid-search @2

A dictionary is two files: a word list where each stem carries flags, and an affix file declaring the prefix and suffix rules those flags refer to plus compounding rules. That indirection is why a morphologically rich language fits in a small dictionary -- inflected forms are generated by rule rather than enumerated -- and it is why the library can also analyse and stem a word, not just accept or reject it. Suggestions come from edit distance combined with phonetic and keyboard-adjacency tables.

In a search stack it powers did-you-mean correction and a stemming token filter, and it is embedded in most desktop spell checkers, so dictionaries exist for a very long tail of languages. It is a lexicon, not a model: it has no idea which correction fits the sentence, so ranking suggestions by context needs a language model on top, and domain vocabulary must be added explicitly or it will be flagged as misspelled.

### igraph
**Short:** Graph analysis library with a C core and Python bindings; community detection and centrality on large graphs.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, runtime-systems/collections-and-algorithms @2, data-stores/graph-db @3

The algorithms are implemented in C with thin Python and R bindings, and graphs are stored as compact indexed edge lists, so a graph with tens of millions of edges is a working object rather than a memory-planning exercise. The library is broad rather than specialized: centralities, shortest paths, flows, motifs, community detection including Louvain and Leiden, random graph generators and layout algorithms all ship in the same package.

Reach for it when NetworkX has become the bottleneck and the analysis is still classical graph theory rather than learned embeddings. The tradeoffs are ergonomic: vertices are integer indices, not arbitrary objects, and deleting a vertex renumbers the rest -- a genuine source of bugs when you hold indices across mutations. For graph neural networks, PyTorch Geometric or DGL are the right layer instead.

### implicit
**Short:** Python library for implicit-feedback collaborative filtering: fast Cython/GPU ALS and BPR matrix factorization.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/classical-ml-and-boosting @3

It solves the implicit-feedback case, where you have clicks, plays, and purchases rather than ratings: unobserved entries are treated as weak negatives with a confidence weight instead of missing values, which is the Hu-Koren-Volinsky formulation of ALS. Alongside ALS it offers BPR, logistic matrix factorization, and item-item nearest-neighbour models, all in Cython with optional GPU support, so factorizing a large sparse user-item matrix takes minutes rather than being a project.

The input is a sparse matrix and nothing else — no side features, no content signal, no answer for a brand-new user or item. Reach for it as the classical baseline candidate generator in a recommender, and as the honest bar that a neural two-tower model has to clear; the cold-start and feature-rich cases are exactly where you move on from it.

### InstructBLIP
**Short:** Salesforce instruction-tuned vision-language model (BLIP-2 Q-Former plus a Vicuna LLM) for visual instruction following.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

It keeps BLIP-2's frozen encoder plus Q-Former structure and adds one change with outsized effect: the instruction text is fed to the Q-Former as well as to the language model, so the queries extract image features conditioned on what is being asked rather than producing one generic summary of the picture. Training converted a large collection of existing vision-language datasets into instruction format, holding some out to measure genuine zero-shot behaviour.

Its historical role is as the bridge between captioning models and instruction-following VLMs. As something to deploy it has aged out: the underlying language models are old, resolution is fixed and low, and current open VLMs of comparable size handle documents, multiple images and dialogue that this cannot. Reach for it only to study the instruction-aware querying idea.

### InternVL2
**Short:** Open-weight vision-language model family from OpenGVLab; among the strongest open VLMs for image understanding.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

The family's distinguishing choice is a very large vision encoder trained alongside the language model rather than a small frozen CLIP tower, paired with dynamic resolution: an image is split into a variable number of fixed-size tiles according to its aspect ratio and size, with a thumbnail for global context, so a dense page keeps its detail instead of being squashed to a single low-resolution crop. Checkpoints span roughly a billion parameters to several tens of billions on different language backbones.

That tiling is why the family scores well on documents, charts and OCR-heavy work, and it is the main reason to pick it over a same-size alternative. It is also the cost: tokens per image grow with resolution, so a full page can consume more context than the question, and throughput drops accordingly. Cap the tile count for cost control before it surprises you in production.

### InternVL2-8B
**Short:** OpenGVLab's open-weights 8B vision-language model for image understanding, OCR and document QA.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

This is the size most teams actually run: enough capability for document question answering and OCR-ish extraction, small enough to serve on a single mainstream GPU in bfloat16 and comfortably on less when quantized. It inherits the family's tiled high-resolution handling, so a scanned page arrives as several detailed crops plus a global view rather than one blurry image.

Reach for it when pages cannot leave your infrastructure and a hosted frontier model is therefore not an option. Expect a real quality gap on hard reasoning and on very dense tables, and expect to bound the tile count, since image tokens dominate the context and directly set your throughput. Batch pages and cache extractions -- re-running a VLM over the same document is the most common source of avoidable GPU spend.

### interpret
**Short:** InterpretML: the EBM glass-box GAM model plus one explain API and dashboard covering black-box explainers too.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @2

Its centrepiece is the Explainable Boosting Machine: a generalized additive model with a bounded number of pairwise interactions, fitted by boosting on one feature at a time with a tiny learning rate over many rounds. The result is a learned shape function per feature that you can plot exactly -- not an approximation of the model, but the model itself -- while accuracy on tabular data lands close to gradient-boosted trees.

Reach for it when the model must be defensible line by line, in lending, insurance or clinical settings, and post-hoc attributions would only be an argument about the explainer. The costs are real: training is slower than LightGBM, it does not handle high-cardinality text or images, and interactions beyond pairs are outside the model class by design. The same package also wraps LIME, SHAP and partial dependence for black-box comparison.

### Jasper
**Short:** Commercial AI writing platform for marketing teams: brand-voice-constrained copy generation at scale from templates.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @3

It is a workflow product rather than a model: templates for ad copy, landing pages and email sequences, brand voice profiles and style guides that are injected into prompts automatically, campaign-level organisation, and seat-based collaboration for a marketing team. Underneath it orchestrates third-party language models, so its value is the guardrails and the shared brand context, not the generation itself.

Reach for it when non-engineers need consistent on-brand output and nobody is going to maintain a prompt library. The tradeoffs are the ones every wrapper carries: per-seat pricing on top of inference you could buy directly, no control over the model or the prompts, and output that still needs a human to fact-check. A team with engineering capacity gets more leverage building on an API with its own templates and evaluation.

### Kaldi
**Short:** Long-standing C++ speech recognition toolkit of GMM-HMM and hybrid recipes; origin of the x-vector speaker model.
**Kind:** tech
**Lang:** cpp
**Roles:** applied-ml/vision-speech-and-multimodal @1

Recognition is compiled into a single weighted finite-state transducer that composes the acoustic model's states, phonetic context, the pronunciation lexicon and the language model into one decoding graph, and decoding is a search over that graph. The acoustic model moved from Gaussian mixtures to neural networks trained with a sequence discriminative criterion, but the WFST framing stayed, and it is what gives Kaldi exact control over vocabulary, pronunciations and grammar.

That control is why it survives: forced alignment, speaker embeddings and constrained-vocabulary recognition are still done here. Everything else argues against it -- a shell-and-script recipe system, unusual data formats, C++ builds, and a learning curve measured in weeks. For a new transcription task, an end-to-end toolkit or a pretrained Whisper-class model gets a better result in an afternoon.

### KenLM
**Short:** Fast modified Kneser-Ney n-gram language model toolkit with quantized tries; standard in MT and ASR pipelines.
**Kind:** tech
**Lang:** cpp
**Roles:** applied-ml/nlp-and-text @1

Two things make it the standard. Estimation streams: modified Kneser-Ney counts are computed with external sorting, so a language model can be trained on a corpus far larger than RAM. Query is fast: the model is stored either as a probing hash table for speed or a compact trie with optional quantization for size, and the binary format is memory-mapped, so startup is instant and several processes share one copy in page cache.

Reach for it whenever an n-gram model has to score hypotheses at speed -- rescoring a CTC beam in speech recognition, or a shallow-fusion bias toward domain vocabulary. It has no notion of context beyond its order and cannot generalise across word forms, so it is a component in a pipeline, not a language model in the current sense; a neural rescorer is the accurate but far slower alternative.

### kjslag/spacebyte
**Short:** Reference implementation of SpaceByte, a byte-level tokenizer-free LM that patches at word boundaries.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

SpaceByte's observation is that you do not need a learned router to decide where a byte-level model should spend compute -- the text already tells you. Patch boundaries are placed at space-like bytes, meaning spaces and punctuation, so patches align with words for free, and the expensive global transformer blocks are applied only at those positions while cheaper local layers handle the bytes in between.

That makes it the simplest credible tokenizer-free design, and the reference implementation is small enough to read end to end, which is its main value. The heuristic is also its limit: languages written without spaces do not segment this way, and a compute-matched comparison against subword models is the only honest way to judge it. This is research code, not a serving stack.

### Krisp
**Short:** AI noise and echo cancellation SDK for real-time voice; strips background noise before speech recognition.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

It runs a small neural network on the audio stream to separate speech from everything else -- keyboard noise, traffic, background conversation -- plus echo cancellation, and it does this locally on the CPU with a few milliseconds of latency, so nothing is sent to a server. It integrates as an SDK inside an application or as a virtual audio device beneath one.

In a speech pipeline the reason to add it is upstream of accuracy: a denoised stream stops a voice activity detector triggering on noise and cuts recognition errors in a noisy room, which matters more than any tuning of the recognizer itself. The costs are a commercial licence and CPU per concurrent stream, and aggressive suppression can clip quiet speech. RNNoise and the WebRTC suppressor are the free, weaker alternatives.

### Laplace
**Short:** PyTorch library adding post-hoc Bayesian uncertainty to a trained net via a Laplace posterior approximation.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It adds Bayesian uncertainty after training rather than requiring you to train differently: keep the trained weights as the posterior mode, and approximate the posterior around them as a Gaussian whose covariance comes from a curvature estimate of the loss. You choose how much curvature to keep -- full, Kronecker-factored, or diagonal -- and over which weights, with last-layer-only being cheap enough to be nearly free and usually most of the benefit.

Reach for it when a deployed network needs calibrated confidence and retraining as an ensemble is too expensive. It also tunes the prior by maximizing marginal likelihood, so no validation split is spent on it. The approximation is local, so it captures uncertainty around one mode and will not represent a genuinely multi-modal posterior; a deep ensemble remains the stronger and much more expensive answer.

### Leiden
**Short:** Community-detection algorithm improving on Louvain by guaranteeing well-connected clusters; the GraphRAG default.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/recommenders-and-graph-ml @1, runtime-systems/collections-and-algorithms @2, search-retrieval/rag-and-document-processing @3

It fixes a specific defect in Louvain: Louvain can produce communities that are internally disconnected, because moving a node out of a community never checks whether what remains still hangs together. Leiden inserts a refinement phase that re-partitions each community into well-connected sub-communities before aggregating, and randomises node moves among improving options, which guarantees connected communities and converges to a partition where no subset wants to move.

A resolution parameter sets granularity, and because the algorithm aggregates level by level it naturally yields a hierarchy -- which is exactly what graph-based retrieval systems consume when they summarize a knowledge graph at several zoom levels. It is unsupervised and the resolution choice is yours, so validate the partition against something you know; and on a graph with weak community structure it will still return communities.

### LeRobot
**Short:** Hugging Face library for robot policy training and deployment with pretrained ACT, diffusion and VLA checkpoints.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/fine-tuning-and-peft @2, model-training/alignment-and-rl @3

It standardizes the three things robot learning otherwise reinvents per lab: a dataset format where episodes carry synchronized camera frames, joint states, actions and language instructions and are hosted like any other Hub dataset; implementations of the current imitation-learning policies, including action-chunking transformers and diffusion policies; and drivers plus teleoperation for inexpensive open-hardware arms, so you can record demonstrations yourself.

Reach for it to get from a physical arm to a trained policy without building the data plumbing. The honest caveats are that imitation policies generalize poorly beyond the demonstrated setup, that data collection by teleoperation is slow human work, and that everything is far less forgiving than a simulator -- a policy that looks fine in replay can still be unsafe on hardware, so keep a planner or a hard limit in the loop.

### librosa
**Short:** Python audio analysis library for loading waveforms and computing spectrograms, MFCCs and other features.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

It is the analysis half of an audio workflow in NumPy: load a file to a float array, resample, then compute short-time Fourier transforms, mel spectrograms, MFCCs, chroma and constant-Q representations, plus higher-level estimators for onset, beat and tempo, and matching display helpers for plotting them. The defaults are opinionated -- loading converts to mono and resamples to a fixed rate unless you say otherwise, which silently changes your features if you do not notice.

Reach for it for exploration, feature engineering and anything that is not inside a training loop. It is CPU-only, single-threaded and not differentiable, so using it as the front end of a deep model means precomputing features or paying for them in the data loader; torchaudio provides the same transforms as GPU modules that stay in the graph.

### LightFM
**Short:** Hybrid recommender combining matrix factorization with user/item feature embeddings for cold start; now dormant.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/classical-ml-and-boosting @3

Instead of learning an embedding per user id, it learns embeddings for user and item features and sums them, so an item with known features has a usable representation the moment it appears -- that is the cold-start property that made it popular. It trains on implicit feedback with ranking losses such as WARP and BPR rather than rating prediction.

Reach for it on small or medium implicit-feedback problems with useful side features and a need to explain the model. It is dormant -- the last release is years old -- so expect to build it yourself on a modern Python and to have no upstream fixes; a two-tower neural retriever is the actively maintained equivalent.

### Lightly AI
**Short:** Self-supervised vision library (SimCLR, MoCo, BYOL, DINO, MAE) plus data-curation and active-learning selection.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/labeling-and-synthetic-data @2

The open-source library supplies the pieces of a self-supervised run as composable PyTorch modules — the augmentation pipelines, the contrastive and distillation losses, the projection heads, the memory banks — so pretraining SimCLR or DINO on your own unlabelled images is a short script rather than a reimplementation from the paper. The commercial platform attacks the other half of the problem: choosing which of a large unlabelled pool is worth paying to annotate, using embedding diversity and model uncertainty instead of random sampling.

Reach for it when you have far more images than labels. If all you need is a pretrained backbone, downloading published weights is much cheaper than pretraining your own.

### lime
**Short:** Local surrogate explainer for tabular, text and image models; unstable run to run, unmaintained since 2020.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It explains one prediction by fitting a simple model nearby: perturb the instance -- mask words, toggle features, switch superpixels off -- ask the black box for predictions on those variants, weight them by similarity to the original, and fit a sparse linear model whose coefficients become the explanation. Nothing about the underlying model is assumed, which is the appeal.

The problems are structural rather than incidental. The perturbations produce points off the data manifold, the neighbourhood width is a free parameter that changes the answer, and the sampling is random, so two runs on the same instance can rank features differently -- which is fatal when the explanation is shown to a customer or a regulator. The project has been unmaintained for years; SHAP is the better-grounded and better-supported choice.

### LLaDA
**Short:** Open 8B masked diffusion language model and reference implementation, the basis for open replication work.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, inference/inference-engine @3

It is a masked diffusion language model: training masks a random fraction of the tokens and asks the model to predict all of them at once, and generation starts from a fully masked sequence and iteratively unmasks over a chosen number of steps, remasking the positions it is least confident about. Attention is bidirectional, so there is no causal mask and no KV cache to reuse -- every step re-reads the whole sequence.

Its significance is as an open, reproducible demonstration that a diffusion objective can reach instruction-following quality at meaningful scale, and it is the base most open replication work builds on. As a deployment target it is not competitive yet: without a KV cache the serving economics are unlike anything the existing inference stacks are optimized for, and quality still trails autoregressive models of the same size.

### LLaMA 3.2 Vision
**Short:** Meta's open-weight 11B/90B vision-language models, self-hostable for image understanding and document Q&A.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

Vision was added to an existing text model rather than trained jointly: a separately trained image encoder feeds cross-attention layers inserted into the frozen language model, so the text-only behaviour of the corresponding Llama release is preserved exactly and only the new layers learn. That makes the vision capability an additive module, which is also why its image reasoning is weaker than models trained multimodally from the start.

Reach for it when you want self-hosted image understanding and the surrounding stack is already Llama, so tooling, prompt format and fine-tuning recipes carry over. Read the licence before shipping -- the community licence carries use restrictions, including regional ones on the multimodal weights. For document-heavy work, a VLM with high-resolution tiling will usually read a dense page better.

### LLaVA
**Short:** Open visual instruction-tuned VLM (CLIP encoder plus an LLM); the standard baseline for open multimodal chat.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

The recipe is deliberately minimal: take a CLIP vision encoder, take an open chat model, and connect them with a projection layer that maps image patch embeddings into the language model's token embedding space so the picture arrives as a prefix of pseudo-tokens. Training is two stages -- align the projector on caption pairs with everything else frozen, then instruction-tune on multimodal conversations generated by a stronger model.

Its importance is that it made a competent VLM reproducible on a small budget, which is why nearly every open multimodal model since follows the same shape. The original checkpoints are superseded: fixed low input resolution, dated language backbones and weakness on dense text are all fixed in later releases. Reach for it as the reference architecture, not the deployment artifact.

### LLaVA-1.5-13B
**Short:** Open 13B vision-language model pairing a CLIP encoder with an LLM for image question answering and captioning.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

The 1.5 revision changed three things from the original: a two-layer MLP replaced the single linear projector, input resolution rose, and academic visual question-answering data was mixed into instruction tuning so the model answers in the short form those benchmarks expect. At 13B it is the larger of the pair, and the gain over the 7B shows up mainly in reasoning about a scene rather than in perception.

It served as the standard open baseline that later work reported against, which is its remaining value. For deployment the arithmetic is unattractive: 13B weights plus a vision tower need a serious GPU, and current open VLMs at 7B or 8B beat it on nearly everything, especially resolution-limited tasks such as reading a document.

### LLaVA-1.5-7B
**Short:** Open 7B vision-language model pairing a CLIP encoder with a Vicuna LLM for image chat and VQA.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

This is the small member of the 1.5 pair and the one most reproductions ran: a CLIP vision tower, an MLP projector, and a 7B chat model, trained with the two-stage alignment-then-instruction recipe that fits in hours on a single node. Its fixed input resolution means an image is downscaled to a few hundred pixels a side before it is ever tokenized.

That resolution ceiling is the thing to know: it handles scenes, objects and general questions, and fails on dense text, small chart labels and multi-column pages, because the detail was destroyed before the model saw it. Later releases fixed this with tiled high-resolution inputs. Reach for it as a lightweight baseline or a fine-tuning starting point, not as a document reader.

### LLaVA-1.6 Mistral
**Short:** Open 7B vision-language model pairing a CLIP encoder with Mistral for image question answering and captioning.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

The 1.6 generation's main change is dynamic high resolution: instead of one downscaled image, the input is split into a grid of crops at native resolution plus a global thumbnail, all encoded and concatenated, which is what finally made this family usable on text-in-image and chart tasks. This variant pairs that with a Mistral 7B backbone, one of several language models the release was built on.

Reach for it when you want a small self-hostable VLM with usable OCR-adjacent behaviour and a permissively licensed backbone. The cost of the tiling is tokens: a high-resolution page can consume several times the context of a plain image, so throughput and memory scale with the crop count, and capping it is the first knob to reach for.

### Logit
**Short:** statsmodels' logistic regression, fit for statistical inference: coefficients with p-values, CIs and AIC/BIC.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @2

### lucidrains/MEGABYTE-pytorch
**Short:** Community PyTorch implementation of MEGABYTE's local/global patch transformer for tokenizer-free byte-level modeling.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

MEGABYTE splits a byte sequence into fixed-size patches: a global transformer runs over patch embeddings, so its sequence length is the byte length divided by the patch size, and a small local transformer decodes the bytes inside each patch conditioned on the global output. Because attention cost is quadratic in sequence length, moving most of the depth to the shortened patch sequence is what makes million-byte contexts tractable without a tokenizer.

This repository is a community reimplementation, written to be read and experimented with rather than to reproduce a paper's numbers, and it ships no pretrained weights. Reach for it to understand the architecture or to prototype on a small corpus; fixed-size patches are also the design's weakness, since they cut mid-word, which is what SpaceByte and entropy-based patching later addressed.

### MABWiser
**Short:** Python multi-armed and contextual bandit library: epsilon-greedy, UCB1, Thompson sampling and LinUCB.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, ml-lifecycle/evaluation-and-benchmarks @3

A bandit is constructed from two choices: a learning policy -- epsilon-greedy, UCB1, Thompson sampling, softmax, or the contextual LinUCB and LinTS -- and optionally a neighbourhood policy that restricts learning to similar contexts by clustering or nearest neighbours. The interface is scikit-learn shaped, with `fit`, `predict` and a `partial_fit` for online updates, and arms can be added or removed as the catalogue changes.

Reach for it when the decision is which of a handful of options to show and you would rather learn from live feedback than run a fixed-horizon A/B test. Two cautions: a bandit optimizes the reward you define, so a click-based reward will happily learn clickbait, and delayed or sparse rewards break the update loop -- attribution has to close within a timescale the bandit can act on.

### MALLET
**Short:** Java NLP toolkit whose optimized collapsed-Gibbs LDA often beats variational fits; driven from its CLI.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3

Its topic modelling uses a collapsed Gibbs sampler with hyperparameter optimization, periodically re-estimating asymmetric document-topic priors rather than holding them fixed. That is why its topics are frequently sharper than a default variational fit: the priors adapt so that common, uninformative topics absorb the filler and the remaining topics stay specific. It is driven from the command line, importing a directory of text into its own binary format first.

Reach for it when topic quality on a static corpus matters more than integration -- it is still a strong LDA implementation, and it also carries classifiers and a CRF sequence tagger. The costs are a JVM dependency, a file-and-CLI workflow rather than a library API, and no embedding-based modelling at all; BERTopic is the modern choice for short or noisy text.

### MaltParser
**Short:** Classic transition-based dependency parser; a historical baseline implementation for treebank parsing.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/nlp-and-text @1

It parses by classification rather than search: the parser holds a stack and an input buffer, and a trained classifier repeatedly chooses the next action -- shift a token, attach it left, attach it right, reduce -- from features of the current configuration. Because each decision is made greedily, parsing is linear in sentence length, which was the point when the alternative was cubic chart parsing.

That greediness is also the flaw: an early wrong attachment cannot be revisited, and errors cascade down the sentence. It is a historical baseline now, useful for understanding transition-based parsing and for reproducing older results; a neural biaffine parser such as Stanza or supar is both more accurate and easier to run, and beam search plus dynamic oracles closed most of the gap even within the transition-based family.

### MAPIE
**Short:** scikit-learn-compatible conformal prediction library producing calibrated prediction intervals and sets.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @3

It applies conformal prediction to scikit-learn estimators: regressors get prediction intervals from the quantile of held-out residuals, with cross-conformal variants that reuse all the data instead of sacrificing a calibration split, and classifiers get prediction sets built by accumulating class scores until a calibrated mass is covered, so an uncertain input returns several labels rather than one confident wrong one.

Reach for it when a model already exists and a coverage guarantee has to be bolted on without retraining -- the wrapper takes the fitted estimator as-is. Read the guarantee carefully: it is marginal coverage at the chosen level, so a subgroup can be systematically under-covered while the overall number looks correct, and it assumes exchangeability, which drift breaks. Set widths on data that resembles what you will actually serve.

### Med-PaLM 2
**Short:** Google's medically tuned large language model for clinical question answering and documentation.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

It is a general large model adapted to medicine by domain fine-tuning plus prompting strategies that sample several reasoning paths and have the model refine an answer from them, and it was evaluated not only on multiple-choice medical exams but by physician panels rating long-form answers for reasoning, harm and omission -- which is the part of the work that generalizes, since exam accuracy says little about clinical safety.

It is not open and reaches customers only through controlled access, so its practical role is as evidence about what domain adaptation buys. For a real clinical documentation or question-answering system, the binding constraints are elsewhere: retrieval over an authoritative source, citation of that source, audit logging, and a clinician in the loop -- none of which a stronger base model removes.

### Mercury
**Short:** Commercial hosted diffusion language model, aimed at very low-latency code completion.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

It is a commercially hosted diffusion language model: instead of emitting tokens one at a time, it refines a block of output over a small number of parallel passes, so wall-clock latency is governed by the refinement steps rather than by the number of tokens produced. That profile is aimed squarely at code completion, where the answer is short, the user is waiting, and revision of already-written tokens is an advantage.

Reach for it, if at all, where interactive latency dominates and outputs are short and structured. The caution is the same as for the whole diffusion-text line: quality on long reasoning trails frontier autoregressive models, the ecosystem of streaming, tool calling and structured output assumes sequential generation, and it is a closed hosted service, so there is no self-hosted fallback.

### Merlin
**Short:** NVIDIA's GPU-accelerated recommender stack for ETL, embedding tables and training DLRM-style models at scale.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/distributed-training @3

It is a stack rather than a library. NVTabular does the feature engineering -- categorify, fill, normalize, target encode -- on GPU over datasets larger than memory, which matters because tabular preprocessing, not training, is usually the wall clock in a recommender. HugeCTR trains with the embedding tables distributed across GPUs and the dense layers replicated, with a hierarchical parameter server so tables larger than device memory spill to host and SSD. Merlin Systems then exports the preprocessing and the model together as a Triton ensemble.

Reach for it when embedding tables run to tens of gigabytes and the whole pipeline is on NVIDIA hardware. The cost is coupling: it is a set of interlocking components with their own formats and container images, and adopting one piece usually pulls in the next. TorchRec covers the sharded-embedding half alone if you would rather keep your own data pipeline.

### MiniCPM-V 2.6
**Short:** Compact open vision-language model from ModelBest/Tsinghua, sized to run image and video QA on-device.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

Around eight billion parameters, it gets multi-image and video question answering into that budget by compressing each image aggressively: a resampler turns the vision tower's patch features into a small fixed number of tokens per image, so several frames fit in context where a naive encoder would spend thousands of tokens on one. High-resolution pages are handled by slicing before encoding rather than by raising the per-image token count.

Reach for it when a vision model has to run on a laptop, a workstation GPU or a phone-class device -- quantized builds run under common local runtimes. The compression is the tradeoff: fine detail is discarded before the language model sees it, so very dense tables and small print are where it loses to a hosted model or to a tiling VLM run at full resolution.

### MMDetection
**Short:** OpenMMLab's PyTorch object-detection toolbox with a 50+ detector zoo under one training and eval config system.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/evaluation-and-benchmarks @3

Detection is decomposed into registered components -- backbone, neck, dense head, region head, assigner, sampler, loss -- and a model is a config file that names them, with configs inheriting from base configs so a variant is a short diff. That structure is what lets one training and evaluation harness cover both one-stage and two-stage detectors, and it is why the toolbox is where published detection numbers are reproduced.

Reach for it to compare detectors fairly or to fine-tune a published model on your own COCO-format data. The costs are the framework's: configuration is spread across inherited files and a registry indirection that is hard to trace, and it pins to specific versions of its own runtime libraries, which is the usual reason a fresh install fails. For one production detector, a self-contained implementation is easier to own.

### mmsegmentation
**Short:** OpenMMLab semantic-segmentation toolbox with 40+ reference models and a config-driven train/eval pipeline.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

Part of OpenMMLab, it decomposes semantic segmentation into swappable components -- backbone, neck, decode head, auxiliary head, loss, dataset, schedule -- all assembled from a Python config file, so moving from a ResNet-based PSPNet to a Swin-based UPerNet or to SegFormer is a config edit rather than new model code. It ships reference implementations and pretrained checkpoints for a large set of published architectures along with the standard datasets and their evaluation protocols, which is what makes it the usual place to reproduce a paper's mIoU. The cost is the framework itself: configs inherit from other configs, the registry indirection is hard to follow, and it pins to particular versions of its `mmcv` and `mmengine` runtime -- a version mismatch is the most common reason a fresh install will not run. Reach for it for research comparison and for fine-tuning a strong published model on your own masks; one production model is often better served by a plain PyTorch implementation you own outright.

### Model Card Toolkit
**Short:** Google library that assembles evaluation artifacts into a standard model card documenting intended use and limits.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/experiment-tracking-and-tuning @3, security/privacy-and-compliance @3

It treats a model card as a structured document rather than a prose file: a schema with sections for model details, intended use, factors, limitations, ethical considerations and quantitative analysis, filled programmatically and rendered to HTML from a template. In a TFX pipeline it can pull the quantitative sections straight from the evaluation and data-statistics artifacts already produced, so the numbers in the card are the numbers from the run rather than copied by hand.

Reach for it when documentation has to be produced for every model version and a hand-written markdown file will drift the moment anyone retrains. It writes the container, not the content -- the judgment about limitations and appropriate use is still yours, and an auto-filled card with an empty limitations section is worse than none, because it looks like diligence.

### ModernBERT
**Short:** Modernized BERT encoder with Flash Attention 2, unpadding and an 8192-token context for retrieval and tagging.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/ann-index-library @3

It is a BERT-shaped encoder rebuilt with everything learned since: rotary position embeddings instead of learned ones, alternating local sliding-window attention with periodic global layers so an 8192-token sequence does not cost full quadratic attention, GeGLU activations, no bias terms, and unpadding so batches of variable-length text spend no compute on padding at all. Pretraining used a much larger and more recent corpus, including code.

Reach for it wherever a BERT-family encoder would go -- classification, token tagging, retrieval and reranking -- and especially where documents exceed the old 512-token ceiling, which previously forced chunking and a merge step. It is an encoder, so it does not generate; and as a recent model, the ecosystem of task-specific fine-tunes is thinner than BERT's, so expect to fine-tune rather than find a checkpoint.

### MONAI
**Short:** PyTorch framework for medical imaging: DICOM/NIfTI transforms, 3D U-Net architectures and domain metrics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

Its transforms are dictionary-based, which is the detail that matters in medical imaging: the same random crop, flip or elastic deformation is applied to the image and its segmentation mask because both are keyed entries in one sample. On top of that it handles the domain's real problems -- reading DICOM and NIfTI with voxel spacing and orientation intact, resampling to a common spacing, caching deterministic preprocessing so epochs are not dominated by disk, and sliding-window inference for volumes too large to fit on a GPU in one pass.

Reach for it for any 3D volumetric segmentation or classification task, where its U-Net family and Dice-based losses and metrics are the expected baseline. It is a research and training framework, not a clinical product: regulatory validation, DICOM integration into a hospital system and human review remain entirely yours.

### Moondream 2
**Short:** 1.86B-parameter Apache-2.0 vision-language model small enough to run captioning and VQA on edge hardware.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, inference/model-format-and-edge @2

Under two billion parameters, it is small enough to run on a CPU or a modest edge device while still doing captioning, visual question answering, pointing at objects and reading short text, and it is released under a permissive licence, which makes it viable in commercial products where a restrictively licensed model is not.

Reach for it when a vision model must run on the device -- a camera, a kiosk, a robot -- or when per-image API cost at volume is the binding constraint. It answers short, specific questions well and is not a conversational model: complex multi-step reasoning about a scene, long dialogue and dense document layout are all beyond it, and that is where a hosted frontier model or a larger open VLM earns its cost.

### Moses
**Short:** Classic statistical MT toolkit whose tokenizer and detokenizer scripts are still the standard pre-subword step.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

It is the reference implementation of phrase-based statistical translation: word-align a parallel corpus, extract phrase pairs with their probabilities, and decode with a beam search over a log-linear combination of the phrase table, a language model and a reordering model, with the feature weights tuned on a development set. Every part of that pipeline is now obsolete for translation quality.

What survives is the preprocessing. Its tokenizer, detokenizer, punctuation normalizer, truecaser and corpus-cleaning scripts became the de facto standard step before subword segmentation, so published results and evaluation scripts assume them -- which is why a Python reimplementation of exactly these scripts is still a common dependency. Reach for them when reproducing a translation result; for the translation itself use a neural model.

### MoveIt
**Short:** ROS motion-planning and collision-checking framework, often the safety layer around a VLA's raw output.
**Kind:** tech
**Lang:** cpp
**Roles:** applied-ml/vision-speech-and-multimodal @1, security/ai-safety-and-guardrails @3

It is the motion-planning layer of a ROS system: it maintains a planning scene combining the robot's kinematic model with sensed and declared obstacles, solves inverse kinematics to turn a desired end-effector pose into joint values, calls a sampling-based planner to find a collision-free path through joint space, then time-parameterizes that path against velocity and acceleration limits before handing it to the controllers.

In a learned-control stack its role is the safety envelope: a policy proposes where to go, and this checks the request against joint limits and collision geometry and refuses or re-plans rather than executing something that would hit the table. The costs are configuration -- URDF, semantic description, controller plumbing -- and that sampling planners give no smoothness or optimality guarantee, so trajectories often need post-processing before they look deliberate.

### MSTParser
**Short:** Historical graph-based dependency parser using maximum spanning trees; a reference baseline.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/nlp-and-text @1

It scores every possible head-dependent arc in the sentence independently and then finds the highest-scoring spanning tree over that complete directed graph with the Chu-Liu-Edmonds algorithm. Because the tree is chosen globally rather than by a sequence of local decisions, an unlikely early attachment cannot poison the rest of the parse, and non-projective trees -- crossing dependencies common in freer word-order languages -- come out naturally.

That global-versus-greedy contrast with transition-based parsing is the reason it is still worth knowing; the tradeoff is that scoring all arcs is polynomial rather than linear, and first-order models cannot see interactions between arcs. Modern biaffine parsers are the same graph-based idea with neural arc scores, so this is a historical baseline rather than something to deploy.

### Neo4j GDS
**Short:** Neo4j Graph Data Science library: in-memory projections with PageRank, community detection and node embeddings.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/recommenders-and-graph-ml @1, data-stores/graph-db @2

Algorithms do not run against the transactional store. You first project a named graph -- a subset of labels, relationship types and properties -- into a compressed in-memory structure optimized for traversal, and then run PageRank, community detection, similarity, path finding or embeddings against that, choosing whether results are streamed back, written to the database, or mutated into the in-memory graph for the next algorithm in a chain.

That projection is both the reason it is fast and the thing to plan for: it is a snapshot that goes stale as the database changes, and it must fit in memory, so estimate before projecting a large graph. Reach for it when the data already lives in Neo4j and the analysis is classical graph analytics; for graph neural networks at scale, a dedicated library plus an exported edge list is the more flexible path.

### netcal
**Short:** Calibration library implementing temperature, Platt and isotonic scaling plus ECE-style calibration metrics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @2

Calibration is a post-hoc map from a model's scores to probabilities that actually match observed frequencies, fitted on held-out data. The library implements the standard family -- temperature scaling, which divides logits by one learned scalar and therefore cannot change the argmax or the accuracy, plus Platt, beta, isotonic and histogram binning, which can -- along with the measurement side: expected and maximum calibration error and reliability diagrams.

Reach for it whenever a downstream decision uses the probability rather than the label: a threshold on risk, an expected-value calculation, or routing to a human when confidence is low. Two rules make or break it. Fit on a validation split, never on training data, or you will measure the overfitting rather than remove it; and re-fit after any distribution shift, because calibration is a property of the data as much as the model.

### NeuralForecast
**Short:** GPU-ready deep forecasting library (NBEATS, TFT, DeepAR) behind a scikit-learn-style fit/predict API.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/deep-learning-framework @3

Data is one long dataframe of series id, timestamp and value, and models are global: one network is trained across every series so a short or new series inherits structure learned from the population, which is the opposite of fitting ARIMA per series. The zoo covers the published architectures behind one `fit`/`predict` interface, exogenous variables are declared as historic, future-known or static, and probabilistic output comes from quantile losses or distribution heads.

Reach for it when you have many related series with shared seasonality and enough history for a network to learn from, and when covariates such as price or promotions genuinely drive the target. Below that scale it loses to statistical baselines, and it brings GPU training, hyperparameters and far longer iteration cycles -- so measure against statsforecast before accepting the complexity.

### Neuronpedia
**Short:** Web platform for browsing sparse-autoencoder features with top-activating examples and generated explanations.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It is the browsable front end for sparse-autoencoder work: for a given model, layer and dictionary, every feature gets a page showing the dataset examples that activate it most strongly, its effect on output logits, and an automatically generated natural-language explanation with a score for how well that explanation predicts the activations. Everything is addressable by URL and exposed through an API, so a write-up can link to the exact feature it discusses.

Reach for it before training your own dictionary -- for the models it covers, the features already exist and the question is usually whether one matching your concept is there. Treat the auto-generated labels as hypotheses: an explanation derived from top activations routinely misses what the feature does in the rest of its range, and the highest-activating examples are the easiest ones to over-interpret.

### NLTK
**Short:** Classic Python NLP toolkit: tokenizers, WordNet, corpora, n-gram and parsing teaching implementations.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

It ships tokenizers, stemmers, the WordNet lemmatizer, POS taggers, chart and CFG parsers, n-gram language models in `nltk.lm`, and dozens of corpora pulled down through `nltk.download()`. Its implementations are written to be read: stepping through Viterbi tagging, Kneser-Ney smoothing, or CKY parsing in NLTK is how most people first understand them.

For a production preprocessing pipeline it is slow, and spaCy or a dedicated tokenizer library is the practical choice. Reach for NLTK when you want WordNet and lexical resources, a quick corpus for experimentation, or a readable reference implementation of a classical NLP algorithm.

### NLTK sent_tokenize
**Short:** NLTK's sentence splitter; a lightweight alternative to spaCy for chunking text at sentence boundaries.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/rag-and-document-processing @2

### NNsight
**Short:** Library for inspecting and editing internal activations of any PyTorch model, including huge ones run remotely.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

You write interventions as ordinary Python inside a tracing context: enter the trace, address any module by its path in the model, read its output by marking it to be saved, or assign to it to overwrite the activation before the forward pass continues. Those statements build an execution graph rather than running immediately, which is what lets the identical script execute locally on a small model or remotely against a very large one hosted elsewhere.

Reach for it when the model you want to inspect is either too big to hold or is an arbitrary PyTorch module that a specialized interpretability library does not support -- there is no requirement for a known transformer architecture. The costs are a deferred-execution model that takes getting used to, and remote runs that depend on someone else's hosted infrastructure and queue.

### nnU-Net
**Short:** Self-configuring biomedical segmentation framework that derives its preprocessing and architecture per dataset.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/experiment-tracking-and-tuning @3

It does no architecture search. It reads a dataset fingerprint -- image sizes, voxel spacings, intensity distributions, modality, class balance -- and applies fixed published rules to derive everything else: resampling target, normalization scheme, patch size and batch size chosen against a GPU memory budget, network depth and pooling derived from that patch size, and a fixed training schedule with deep supervision, heavy augmentation and cross-validation. It then picks among 2D, 3D and cascaded configurations by measured performance.

The result routinely wins segmentation challenges against hand-designed architectures, which is its real lesson: in medical segmentation, configuration dominates architecture. Reach for it as the baseline any new method must beat. The costs are a long training schedule per fold, an inflexible directory and naming convention, and a pipeline that is deliberately hard to modify -- changing it usually means giving up the guarantee that makes it work.

### NumPyro
**Short:** JAX-backed probabilistic programming library for Bayesian models via NUTS/MCMC and variational inference.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

You write a model as a Python function whose latent variables are `numpyro.sample` statements, then run inference over it — NUTS/HMC for full posterior sampling, stochastic variational inference when the model is too large for MCMC. Because everything compiles through JAX with JIT and vectorized chains, sampling that is painfully slow in pure-Python probabilistic frameworks becomes practical on a GPU or TPU.

Reach for it when uncertainty is the answer rather than a nice-to-have: small or hierarchical data, Bayesian causal models, calibrated predictive intervals. It costs modelling effort and compute a point-estimate model does not, and JAX's functional constraints — no in-place mutation, static shapes — shape how the model has to be written.

### NVIDIA NeMo
**Short:** NVIDIA generative-AI toolkit: production ASR/TTS/speaker models plus LLM training and distillation pipelines.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/fine-tuning-and-peft @2, model-training/distributed-training @3, applied-ml/nlp-and-text @3

NeMo is a set of PyTorch-based toolkits and pretrained checkpoints organised by domain: speech, covering ASR, TTS, speaker recognition and diarization with architectures such as Conformer, Citrinet and TitaNet, and large language and multimodal models. Everything is driven by YAML configuration rather than bespoke training scripts, and on the LLM side it wraps Megatron-style tensor, pipeline and sequence parallelism so a training or fine-tuning run scales across many GPUs and nodes without you writing the parallelism yourself. Checkpoints are published on NGC and Hugging Face, so the normal path is to take a pretrained model and fine-tune or distill it into a smaller student rather than train from scratch. Reach for it on NVIDIA hardware when you want a supported end-to-end path; the config-driven design is a strong constraint, and a small speech task is often better served by a single Hugging Face model.

### OCTIS
**Short:** Framework for comparing topic models on standardized coherence and diversity metrics with hyperparameter search.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, ml-lifecycle/evaluation-and-benchmarks @2, ml-lifecycle/experiment-tracking-and-tuning @3

It exists because topic-model comparisons are usually not comparisons: different preprocessing, different vocabularies, different coherence implementations. It fixes all three, providing preprocessed standard corpora, wrappers so classical and neural topic models share one interface, a consistent set of metrics including coherence variants, topic diversity and downstream classification, and Bayesian optimization over each model's hyperparameters so you compare tuned models rather than defaults.

Reach for it when choosing a topic model is a decision you have to defend, or when a new method needs an honest baseline. It is a research harness: nothing in it serves topics, updates incrementally, or scales to a corpus that does not fit in memory, and coherence itself correlates only loosely with whether a human finds the topics useful -- read the topics as well as the numbers.

### OGB
**Short:** Open Graph Benchmark: standard graph datasets and evaluation protocol for node, link and graph tasks.
**Kind:** dataset
**Lang:** *
**Roles:** applied-ml/recommenders-and-graph-ml @1, ml-lifecycle/evaluation-and-benchmarks @2

Each dataset arrives with a prescribed split and an evaluator object that computes the official metric, and the splits are deliberately not random: molecules split by scaffold, papers by publication year, proteins by species. That is the whole point -- a random split lets a graph model exploit near-duplicate neighbours and report accuracy that collapses on genuinely new data, and these splits measure the generalization you actually need.

Use it to validate that a graph method works rather than that it memorizes, and to make numbers comparable with the public leaderboard, with loaders provided for both major GNN frameworks. The caveat is the usual one for benchmarks: leaderboard positions on a fixed split reward tuning against it, and the largest datasets need real infrastructure to train on, so the reported hardware matters as much as the score.

### Open X-Embodiment dataset
**Short:** Cross-embodiment robot trajectory corpus (1M+ episodes, 22 embodiments) behind RT-X, OpenVLA and Octo.
**Kind:** dataset
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

It is a pooling of robot demonstration datasets contributed by many labs, converted into one episode format where each step carries camera images, proprioceptive state, an action and a language instruction. The difficulty it exposes is that the constituent robots do not share an action space -- different arms, grippers, control rates and frames -- so training across them requires mapping everything onto a common representation, typically a discretized end-effector delta, and accepting that some fidelity is lost.

Reach for it to pretrain a policy that transfers across embodiments, which is the finding it enabled: a model trained on the pool outperforms one trained on any single robot's data, including on that robot. Expect heterogeneous quality, camera setups and licensing across the constituent datasets, and expect fine-tuning on your own hardware to still be necessary.

### Open-weight VLMs
**Short:** Self-hostable open-weight vision-language models, used for cost-sensitive image understanding and indexing.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/rag-and-document-processing @2

Self-hosting changes the cost model rather than removing it: you stop paying per image and start paying for GPU hours, and the variable that dominates is the number of image tokens, which grows with resolution and any tiling scheme the model uses. Memory is the language model plus the vision tower plus a KV cache that image tokens fill quickly, so batch size -- and therefore throughput and unit cost -- is set by how aggressively you cap resolution.

Reach for them when volume is high enough that per-image pricing dominates, when documents cannot leave your boundary, or when you need to fine-tune on your own layouts. The gap to frontier models is narrowest on straightforward description and widest on dense text, charts and multi-step reasoning, so measure on your actual pages before committing -- and keep a hosted model as the escalation path for the hard tail.

### open_clip
**Short:** Open reproduction of CLIP with many pretrained image-text encoders (CLIP, SigLIP) for joint embeddings.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

open_clip is an open reproduction of CLIP's contrastive image-text training, plus a large catalogue of pretrained checkpoints — CLIP and SigLIP variants at different sizes, trained on openly documented datasets. One call gives you the image tower, the text tower and the matching preprocessing transform, and both towers emit vectors into the same embedding space, so a text query and an image can be compared with a dot product.

Use it for zero-shot classification (embed the label names as prompts and take the nearest), cross-modal retrieval, or as a frozen encoder feeding a downstream head. Its advantage over the original release is the breadth of checkpoints and known pretraining data; results vary a lot between them, so record which one you used.

### OpenAI GPT-5.6 series
**Short:** OpenAI's frontier reasoning model line, with a reasoning.effort control from minimal to high defaulting to medium.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @3

### OpenAI Realtime API
**Short:** Bidirectional streaming speech-to-speech endpoint over WebSocket/WebRTC, avoiding a separate STT and TTS hop.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, apis-frameworks/rpc-graphql-and-streaming @2, inference/model-server @3

The client holds a socket open and appends audio chunks to an input buffer as discrete events; the server detects when the user has stopped, runs the model, and streams audio and transcript deltas back on the same channel. Because the model is speech-native, no transcription and resynthesis hop sits between the user and the reply, and barge-in is handled by cancelling the in-flight response and truncating what has already been played.

Reach for it when conversational latency is the product and turn-taking has to feel natural. The costs are specific: audio tokens are billed at a much higher rate than text, sessions have length limits and must be resumed, and moderating or logging a spoken reply is harder when the text was never the intermediate artifact. A separate recognizer, model and synthesizer remain the choice when you need to inspect or filter each stage.

### OpenAI reasoning models
**Short:** OpenAI models that spend hidden chain-of-thought before answering, with reasoning effort as the tuning knob.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agent-framework @3

These models are trained with reinforcement learning to produce a long internal chain of thought before answering. The reasoning tokens are billed as output but not returned, so cost and latency scale with how hard the model decides the problem is, and the effort setting is the direct control over that tradeoff. They behave differently from instruction-tuned chat models: terse prompts work better than elaborate ones, and few-shot examples and step-by-step instructions often hurt, because they interfere with the reasoning the model would have done anyway.

Reach for them on maths, competitive coding, multi-step planning and any task where a wrong answer is expensive and an extra few seconds is not. They are poor value for extraction, formatting and classification, where a fast non-reasoning model is both cheaper and quicker -- routing by task type is what keeps the bill sane.

### OpenAI TTS
**Short:** OpenAI's hosted text-to-speech endpoint returning streamed synthesized audio from text and a chosen voice.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

You post text plus a voice name and an audio format to a speech endpoint and get audio back, with a streaming mode so playback can start before synthesis finishes. The voices are a fixed catalogue, and newer models in the line accept an instruction describing how a line should be delivered, which is the closest thing to prosody control on offer.

Reach for it when speech is a feature rather than the product and the stack is already OpenAI -- it is cheap, simple and needs no model hosting. The deliberate limitation is that there is no voice cloning, so a distinctive brand voice is not available here; that is where ElevenLabs or a self-hosted model comes in. Per-character billing and a network round trip inside your latency budget are the other costs.

### OpenCV BackgroundSubtractorMOG2
**Short:** OpenCV per-pixel Gaussian mixture background subtractor for real-time foreground detection in video.
**Kind:** api
**Lang:** python, cpp
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/timeseries-and-anomaly @3

### OpenNMT-py
**Short:** PyTorch neural machine translation toolkit used as a reproducible research baseline for seq2seq.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

It is a config-driven sequence-to-sequence trainer: a YAML file names the corpora, the vocabulary built from them, the model and the training schedule, and command-line entry points build the vocabulary, train, and then translate with beam search. Transformer and recurrent encoders, copy attention, coverage and shared embeddings are all options rather than forks, which is what made it a common baseline for research on translation and other seq2seq tasks.

Reach for it to reproduce or extend a published translation recipe, or when you want a training stack that ends in a fast inference export rather than a research checkpoint. For new work the centre of gravity has moved to Hugging Face transformers for training and to fine-tuning a multilingual pretrained model, which starts far ahead of a model trained from scratch on your parallel corpus.

### OpenVLA
**Short:** The reference open 7B vision-language-action robotics model; the usual base for VLA fine-tuning research.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/fine-tuning-and-peft @3

It is a vision-language model turned into a robot policy by a simple trick: each continuous action dimension is discretized into bins, and those bins are mapped onto rarely used tokens in the language model's vocabulary, so predicting the next action is literally next-token prediction and the whole pretrained model transfers unchanged. It was fine-tuned on a large cross-embodiment demonstration corpus, giving a single policy that controls several robot types.

Reach for it as the open base to fine-tune for your own arm -- low-rank adaptation on a modest GPU is the documented path, which is what made this line accessible outside large labs. The constraints are physical: inference must keep up with the control loop, discretization limits action precision, and a policy that generalizes across embodiments still needs demonstrations from yours. Keep a planner or hard limits between it and the hardware.

### Opus 5
**Short:** Anthropic's frontier Claude model, used for deep reasoning and long agent trajectories with very large context windows.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @3, llm-apps/agentic-environments @3

It is the top tier of the Claude line, the one to use when the task is judgment rather than throughput: long agentic trajectories where an error compounds over many steps, code changes that span a codebase, analysis where the answer depends on holding a large amount of context at once. It shares the API, tool-use protocol and prompt conventions of the smaller tiers, so moving work between them is a model-id change.

The cost is the reason not to use it everywhere: it is the most expensive per token and the slowest to first token, and on narrow, well-specified subtasks a smaller model produces the same answer for a fraction of the spend. The economical pattern is to reserve it for planning and hard steps and delegate mechanical work down the tier list.

### Otter.ai
**Short:** Commercial meeting transcription service producing speaker-attributed transcripts and summaries.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

It records and transcribes live, showing the transcript as the meeting happens with speaker labels, then produces a summary with action items and keeps everything searchable across the account. Capture comes either from a bot joining the call or from recording on the device, and a custom vocabulary list improves recognition of names, products and jargon the general model gets wrong.

Reach for it when a live transcript during the meeting is the point -- for accessibility, for participants joining late, or for a note-taker who is also in the conversation. The same cautions apply as to any hosted transcription service: recordings of internal conversations sit with a third party, all-party consent laws vary by jurisdiction, and accuracy falls off sharply with accents, crosstalk and a single shared room microphone.

### Penzai
**Short:** DeepMind's JAX library for building and surgically editing neural network internals with named-axis tensors.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

Models are built as plain, printable data structures rather than opaque objects: every layer is a dataclass in a tree you can inspect in a notebook, and tensors carry named axes so you index by meaning instead of by position. The consequence is surgery -- a selector picks out every instance of some sub-layer in a loaded model and replaces or wraps it, so inserting a probe, patching an attention head or swapping an activation is a transformation of the model tree, not a fork of its source.

Reach for it for interpretability and model-editing work in JAX where you need to modify internals you did not write. It is a niche library in a smaller ecosystem, so pretrained model coverage and community examples are thinner than the PyTorch equivalents; on PyTorch, TransformerLens or NNsight fill the same role.

### pmdarima
**Short:** Wraps statsmodels in an sklearn API and adds auto_arima to search ARIMA/SARIMA orders automatically.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

Its `auto_arima` automates the Box-Jenkins procedure: statistical tests choose how many non-seasonal and seasonal differences to take, then a stepwise search walks the autoregressive and moving-average orders, refitting and keeping whichever minimizes an information criterion, stopping when no neighbouring model improves. Around that sit the scikit-learn conveniences -- pipelines, transformers for differencing and Fourier terms, and cross-validation splitters that respect time order.

Reach for it when a single business series needs a defensible statistical forecast and you would rather not choose orders by staring at correlograms. It fits one series at a time and wraps statsmodels, so it is slow across thousands of series, and the project's maintenance has slowed to the point where dependency pins matter. Nixtla's statsforecast implements the same search far faster and is the actively developed option.

### POPE
**Short:** Object-hallucination benchmark for vision-language models, built from negative existence questions about images.
**Kind:** dataset
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/evaluation-and-benchmarks @2

Every item is a yes-or-no question of the form does this object appear in the image, generated from annotated images with half the answers true and half false. The negatives are sampled three ways -- uniformly at random, from globally frequent objects, and adversarially from objects that usually co-occur with what is actually present -- and that last split is the one that hurts, because it targets a model's prior rather than its perception. Scoring is accuracy, precision, recall and F1, plus the proportion of yes answers.

That yes-ratio is what a score alone hides: a model that answers yes to almost everything can look reasonable on accuracy while being useless. Treat it as a narrow probe of object-existence hallucination under a binary question. It says nothing about hallucinated attributes, relations or counts, and nothing about what a model invents in free-form description, which is the failure mode users actually encounter.

### Prophet
**Short:** Additive forecasting library for business time series with trend, seasonality and holiday terms.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

It fits a decomposable additive model — a piecewise trend with automatically placed changepoints, seasonality as Fourier terms at yearly, weekly, and daily periods, and holiday effects you supply as a table of dates — using Stan underneath, and returns a forecast with uncertainty intervals. Because the components are interpretable and the defaults are sane, a business series with strong calendar structure gets a usable forecast with essentially no tuning, and the parameters you do touch (changepoint prior scale, seasonality mode) map to intuitions about the data.

Reach for it as a fast, hard-to-embarrass baseline on daily or weekly business series, or to flag anomalies as large residuals against its fit. It models each series independently and uses exogenous inputs only as simple additive regressors, so once you have many related series or strong external drivers, gradient boosting on lag features or a global deep model will beat it.

### pyannote.audio
**Short:** Pretrained speaker-diarization and voice-activity pipelines built on PyTorch.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

Diarization is answered in stages rather than by one model: a segmentation network labels who speaks in short overlapping windows, which resolves simultaneous speech locally; a speaker-embedding model represents each local speaker; and clustering links those across the recording into consistent identities, producing a timeline of speaker turns. The pretrained pipelines are downloaded from the Hub behind a licence acceptance and a token.

Reach for it when a transcript needs to attribute lines to speakers -- meetings, calls, interviews -- since recognizers alone do not. The accuracy is very sensitive to conditions: crosstalk, similar voices and a distant shared microphone all raise the error rate, and the number of speakers either has to be supplied or estimated, which is itself a common source of mistakes. Run it on GPU; long recordings are slow otherwise.

### pyLDAvis
**Short:** Interactive intertopic-distance and term-relevance visualizer, the standard way to inspect and label LDA topics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, applied-ml/interpretability-fairness-and-causal @3

Two linked panels. On the left, topics are circles positioned by projecting the distances between their word distributions into two dimensions, with area proportional to how much of the corpus each covers, so overlapping circles mean topics that are not really distinct. On the right, a relevance slider reweights the top terms between a topic's own word probability and how much more likely a word is in this topic than in the corpus overall, which pulls the corpus-wide filler words out of the list and leaves the distinguishing ones.

That slider is why it remains the standard way to name topics: the highest-probability words in a topic are usually generic, and the distinguishing words are what a human recognises. It consumes fitted matrices rather than fitting anything, is slow and memory-hungry on large vocabularies, and only visualizes -- it will not tell you the model is wrong, only let you see that the topics overlap.

### PyOD
**Short:** Outlier-detection library with 40+ algorithms (ECOD, COPOD, HBOS, AutoEncoder, DeepSVDD) behind one API.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

Every detector shares one interface -- fit, then a continuous outlier score with a contamination rate turning it into labels -- across proximity methods such as KNN and local outlier factor, linear ones such as PCA and one-class SVM, probabilistic ones such as ECOD and COPOD, tree ensembles, and deep detectors including autoencoders. That uniformity is the point: swapping algorithms is a one-line change, so you can benchmark a dozen on the same data.

Reach for it for multivariate tabular anomaly detection where you have no labels. Start with the parameter-free distribution-based detectors, since they need no tuning and are hard to beat on tabular data; deep detectors rarely repay their complexity unless the data is high-dimensional. The unavoidable difficulty is validation -- with no labels, the contamination rate is a guess, and every method will happily return exactly that fraction of anomalies.

### Pyro
**Short:** PyTorch-based probabilistic programming language for Bayesian models via variational inference and MCMC.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

You write the generative model as an ordinary Python function whose random choices are `pyro.sample` statements, then hand it to an inference engine: stochastic variational inference, which fits an approximating guide by gradient descent and scales, or MCMC with NUTS when you can afford the samples and want the real posterior. Being built on PyTorch means the model may contain neural networks, which is how a Bayesian neural network or a deep generative model gets its uncertainty.

Reach for it when you need calibrated uncertainty or an explicit hierarchical or causal structure rather than a point prediction. Inference is far slower than fitting a discriminative model, and diagnosing a badly fitting guide takes genuine statistical judgement.

### PyTorch Geometric
**Short:** The standard PyTorch library for graph neural networks: GNN layers, datasets, neighbour samplers.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @2

A graph is a `Data` object holding node features and an `edge_index` in sparse COO form, and layers are message-passing modules - GCN, GAT, GraphSAGE, GIN - composed like any other `nn.Module`, with a `MessagePassing` base class for writing your own. Batching concatenates graphs block-diagonally so variable-sized graphs train together without padding.

The part that decides whether a model reaches production is sampling. Full-graph training does not fit for a large graph, so `NeighborLoader` samples a fixed-size neighborhood per hop into mini-batches - exactly the mechanism behind GraphSAGE and PinSage-style recommenders. Watch memory and receptive field together: each additional hop multiplies the sampled neighborhood, and deep GNNs also oversmooth, so two or three layers with good sampling usually beats a deeper stack.

### pytorch-crf
**Short:** Drop-in conditional random field layer for PyTorch taggers: forward algorithm, Viterbi decode and masking.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

It is a single module you place on top of a token classifier. Given per-token emission scores it learns a transition matrix between tags, computes the exact sequence log-likelihood with the forward algorithm for training, and decodes the highest-scoring path with Viterbi at inference, with a mask so padded positions are excluded. The effect is that tagging becomes a sequence decision rather than independent per-token softmaxes.

Reach for it when the label scheme has hard structure -- BIO tags where a continuation cannot follow a different entity's start -- and impossible sequences are showing up in the output. The cost is quadratic in the number of tags per step, so a large label set gets expensive, and with a strong pretrained encoder the gain is often small enough that constrained decoding at inference achieves the same thing for free.

### pytorch-grad-cam
**Short:** Saliency-map library for CNNs and ViTs: Grad-CAM, Grad-CAM++, Score-CAM and Ablation-CAM.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, applied-ml/vision-speech-and-multimodal @2

Grad-CAM weights the feature maps of a chosen layer by the average gradient of the target score with respect to them, sums, rectifies and upsamples, producing a coarse heatmap of where the evidence for that class was. The library adds the variants -- gradient-free ones that ablate channels instead, and eigen-decomposition methods -- supports transformers by reshaping the token sequence back to a spatial grid, and handles detection and segmentation targets as well as classification.

Reach for it to check whether a vision model is looking at the object or at a background correlate -- the watermark, the ruler in the frame, the hospital's scanner artifact. Two cautions: the map is only as meaningful as the layer you pick, since deep layers are semantic but blurry and shallow ones are sharp but meaningless, and a plausible-looking heatmap is not evidence of correct reasoning.

### pyvene
**Short:** Stanford NLP library for declarative activation interventions - patching and steering - with reproducible configs.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

An intervention is declared as configuration rather than written as a hook: which component, which token positions, which heads, and what to do -- replace an activation with one from another input, add a vector, or apply a learned rotation. The library wraps a model with that configuration and runs it with a base input and one or more source inputs, performing the swap at the right point. Because the configuration is serializable, an experiment is reproducible and shareable rather than a pile of forward hooks.

Reach for it for activation patching and causal-abstraction work where you need many intervention sites tested systematically, including trainable interventions that search for the subspace carrying a variable. The abstraction costs some directness -- for one quick patch, a manual hook is faster -- and it is aimed at models it can address structurally rather than arbitrary custom architectures.

### Qwen-VL-Chat
**Short:** Alibaba's open-weight vision-language chat model for image understanding, OCR and grounded visual dialogue.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

Its vision path compresses a large image encoder's output into a small fixed number of tokens through a single cross-attention resampler, which keeps context cost predictable, and it was trained with grounding and text-reading data so it can emit bounding boxes in the output and read both Chinese and English text in an image. That bilingual OCR ability is what set it apart from Western-trained VLMs of its generation.

Reach for it when the collection is Chinese-language or bilingual and self-hosting is required. It has been superseded within its own family by later releases that handle native dynamic resolution and video, and those are the better starting point for new work -- the fixed low resolution here is the same limit that constrains other models of its era on dense documents.

### Qwen3
**Short:** Alibaba's open-weight LLM family, including sparse MoE reasoning checkpoints split into Instruct and Thinking.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/llm-gateway-and-routing @2

The family spans small dense models up to large sparse mixtures of experts, all permissively licensed, and its notable design choice was making reasoning a mode rather than a separate model -- the same checkpoint could answer directly or think first. Later revisions split that back into distinct Instruct and Thinking checkpoints, on the finding that specializing each beats a single model toggling between them. Multilingual coverage is unusually broad, and context extends well beyond the native window with position-scaling.

Reach for it when you want open weights across a size ladder from an on-device model to a frontier-adjacent one, with one prompt format and tool-calling convention throughout, so routing between sizes is cheap. The MoE members need memory for all parameters even though few are active per token, which is what usually decides between them and a dense model of the same measured quality.

### RankLib
**Short:** Java learning-to-rank library with reference implementations of LambdaMART, ListNet, RankNet and Coordinate Ascent.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/recommenders-and-graph-ml @1, search-retrieval/reranking @2, model-training/classical-ml-and-boosting @3

RankLib trains on files in the LETOR format — one line per document with a relevance grade, a query id, and a numbered feature vector — and evaluates with ranking metrics such as NDCG@k and MAP directly, which is what separates it from a general classifier library. LambdaMART is the algorithm most people actually use from it, and it remains the baseline a neural reranker has to beat before it earns its latency cost.

Its practical role today is as a reference implementation and as a model producer for search engines: learning-to-rank plugins for Lucene-based engines can load a RankLib-trained model and apply it as a rescoring pass over the top N results. For training at production scale, the ranking objectives in LightGBM or XGBoost are the faster and better-maintained path.

### RecBole
**Short:** Research framework with 100+ unified recommender implementations for benchmarking sequential and graph models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, ml-lifecycle/evaluation-and-benchmarks @2

RecBole implements over a hundred recommenders — matrix factorization, BPR, NCF, LightGCN, SASRec, BERT4Rec, GRU4Rec — behind one configuration and data interface, holding the dataset splitting, negative sampling and metrics (Recall@K, NDCG@K, MRR) constant across all of them. That is the whole point: published numbers use different splits and sampling protocols and are not comparable with each other, whereas a RecBole sweep is.

Reach for it to choose a model family, or to check that a proposed architecture actually beats a well-tuned baseline before anyone builds a production version. It is a research harness rather than a serving stack — nothing in it trains at your data scale or serves online traffic.

### RepE
**Short:** Representation engineering: reading and steering model behaviour by manipulating activation directions.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1, security/ai-safety-and-guardrails @2

The method is empirical and cheap: run the model on pairs of prompts that differ in one property -- honest versus deceptive answers, harmful versus harmless requests -- collect the internal activations, and take the difference in means or the leading principal component as a direction representing that concept. Reading, you project a new activation onto that direction as a probe of whether the property is present. Controlling, you add or subtract the direction during the forward pass to strengthen or suppress the behaviour.

Reach for it as a fast diagnostic or a steering knob when fine-tuning is too slow and prompting is too easily circumvented -- the intervention lives below the text, so a jailbreak in the prompt does not remove it. The limits are that directions are specific to a model and layer, that strong steering degrades fluency, and that a probe correlating with a concept is not proof the model uses that direction to compute it.

### responsibly
**Short:** Python toolkit to measure and mitigate bias in word embeddings and classifiers: WEAT effect sizes, Bolukbasi debiasing.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, applied-ml/nlp-and-text @2

For embeddings it implements the association test that measures whether one set of target words sits systematically closer to one attribute set than another, reports it as an effect size with a permutation test, and provides the projection-based measures of how much of a word's geometry lies along a demographic direction. It also implements the classic neutralize-and-equalize debiasing, and the follow-up analysis showing that this mostly hides the bias -- debiased vectors still cluster by the protected attribute, so a downstream classifier can recover it.

Reach for it when auditing a static embedding or an embedding-derived feature, and treat that caveat as the main lesson rather than a footnote: a passing association test after debiasing does not mean the representation is fair. The methods were built for static word vectors and do not transfer cleanly to contextual embeddings, where the same word has no single vector to test.

### River
**Short:** Online machine-learning library that learns one sample at a time: streaming stats and Half-Space Trees.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/classical-ml-and-boosting @2

Every estimator learns from one observation at a time -- a dictionary in, an update out -- so there is no design matrix, no epoch and no retraining job; the model is always current and its memory footprint does not grow with the stream. Pipelines, feature extractors and running statistics all follow the same one-sample contract, and drift detectors plus adaptive trees let a model notice a distribution change and rebuild the affected part of itself.

Reach for it when data arrives as an unbounded stream, the distribution moves, and a nightly batch retrain is too slow -- fraud, monitoring, personalization on fresh behaviour. Evaluation must change with it: you score each sample before learning from it rather than holding out a test set. The costs are per-sample Python overhead, so throughput is far below a vectorized batch library, and on stationary data a tuned batch model usually wins on accuracy.

### Ruptures
**Short:** Offline changepoint detection for signals: pick a cost function, then a search method (Pelt, BinSeg, Dynp, Window).
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

A detection is assembled from two independent choices: a cost function describing what kind of change to look for -- a shift in mean, in variance, in the whole distribution via a kernel, or in a regression relationship -- and a search method that partitions the signal to minimize total cost. Exact dynamic programming is available when the number of breakpoints is known; a pruned search handles the penalized case in near-linear time; binary segmentation and bottom-up merging are the fast approximations.

Reach for it to segment a signal after the fact: finding when a sensor's regime changed, when a metric's baseline shifted, when to split a series before fitting per-segment models. The hard part is the penalty, which trades missed changes against spurious ones and depends on the noise scale, so calibrate it on a labelled stretch. This is offline analysis over a complete signal, not a streaming detector.

### SAELens
**Short:** Library for training, loading and evaluating sparse autoencoders on model activations; pairs with TransformerLens.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

It is the practical toolkit around sparse autoencoders: an activation store that streams a corpus through a hooked model and shuffles the resulting activations so training does not see them in document order, implementations of the main architectures including gated and top-k variants, and the evaluation that actually matters -- how many latents fire, how much variance is reconstructed, and how much of the model's loss survives when the reconstruction is spliced back in place of the real activation.

Reach for it first to load an existing dictionary, since a large set of pretrained autoencoders for open models can be pulled by name and browsed in a feature dashboard, and only train your own when nothing covers your model and layer. That training is the expensive part, and a trained autoencoder is a starting point rather than a result: interpreting what the latents mean is still manual work.

### scikit-learn TfidfVectorizer
**Short:** scikit-learn transformer turning a text corpus into sparse TF-IDF vectors; the cheap content-feature baseline.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, applied-ml/recommenders-and-graph-ml @2

### SEDD reference implementation
**Short:** Research code for Score Entropy Discrete Diffusion: the reference for its discrete score function and loss.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

Score entropy discrete diffusion adapts diffusion to text without pretending tokens are continuous. Rather than predicting a denoised distribution directly, the model learns the ratios between the probabilities of neighbouring discrete states -- the discrete analogue of a score -- trained with a denoising loss designed for that parameterization, and sampling inverts the corruption process using those ratios over a chosen number of steps.

This repository is the reference for that loss and sampler, and that is its value: the parameterization is the contribution, and it is easier to read here than to reconstruct from the paper. It is research code with no serving path, and diffusion text models generally remain behind autoregressive ones of comparable size, so treat it as the place to understand the method rather than a model to deploy.

### segment-anything
**Short:** Meta's official Segment Anything Model library: promptable zero-shot image segmentation from points, boxes or masks.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

The model splits into an expensive image encoder and a very cheap prompt decoder. The encoder runs once per image and produces an embedding; after that, each click, box or coarse mask is decoded into a mask in milliseconds, which is what makes interactive annotation feel instant. Ambiguous prompts return several masks with predicted quality scores, and a helper grids the image with automatic point prompts to segment everything in it.

Reach for it to accelerate mask annotation, to turn detection boxes into masks, or as a general-purpose segmenter where the categories are not fixed. The crucial limitation is that the masks are class-agnostic -- it finds objects but does not name them -- so semantics come from pairing it with a detector or classifier. Later versions extend the same idea to video with mask propagation.

### SentencePiece
**Short:** Google tokenizer training BPE/Unigram subword vocabularies straight from raw text; used by LLaMA, T5 and Gemma.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, runtime-systems/text-encoding-and-regex @3

SentencePiece treats the input as a raw character stream instead of pre-split words, escaping spaces as an ordinary symbol so detokenization is exactly reversible and languages written without spaces need no separate word segmenter first. It trains either a BPE or a unigram language-model vocabulary directly from raw text and ships the result as a single model file the runtime loads, which is what makes the same tokenizer reproducible across training and serving.

Reach for it when training a tokenizer for a new corpus or a multilingual model. Remember that changing the tokenizer changes the token count, so perplexity numbers stop being comparable across models that do not share one.

### SHAP
**Short:** Shapley-value feature attribution (TreeSHAP/KernelSHAP/DeepSHAP) explaining any model's individual predictions.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

A Shapley value is a feature's average marginal contribution to the prediction across all orderings in which features could be added, which is exponential to compute exactly, so the library ships model-specific fast paths: TreeSHAP is exact and polynomial for tree ensembles, KernelSHAP is model-agnostic but approximates by sampling and is slow, DeepSHAP backpropagates attributions through a network. The output is additive, so per-prediction contributions sum to the model output minus a baseline, which is what makes it usable for reason codes.

Reach for it when you have to justify individual decisions, such as an adverse-action notice, or when debugging a model that scores well but for the wrong reason. Two caveats matter: the attributions explain the model, not the world, so they are not causal effects, and correlated features share credit in ways that make a single feature look unimportant when the information is simply available elsewhere.

### SHAP 0.44+
**Short:** Shapley-value attribution library; TreeSHAP computes exact per-feature contributions for any tree ensemble.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

The modern interface collapses the old per-method classes into one explainer that inspects the model and dispatches to the right algorithm, returning an explanation object that carries values, base values, data and feature names together -- which is what lets the plotting functions be called on a result rather than on six positional arrays. For tree models there is a real choice underneath: the path-dependent estimator is fast and uses the tree's own coverage, while the interventional one needs a background dataset and gives values consistent with the observational-versus-interventional distinction that matters when features are correlated.

Reach for it as the default attribution library on tabular models. Keep the additivity check on -- when it fails, the model output does not equal the base value plus the contributions, which usually means the wrong background data or a mismatched model wrapper, and silently trusting those numbers is how a wrong reason code ships.

### shapash
**Short:** Python library producing turnkey, business-readable SHAP explanation dashboards and reports over a fitted model.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

It sits on top of SHAP and solves the presentation problem: given the fitted model, the data, the preprocessing encoder and a dictionary of human-readable names, it maps contributions back from encoded columns to original features, so a reviewer sees a named category rather than a one-hot column index. From that it builds an interactive web application and a standalone report, and offers a predictor object that returns a prediction with its local explanation attached for use in a service.

Reach for it when explanations are for business users or auditors rather than for the modelling team. It is bounded by what SHAP can do -- tabular models, with all the caveats about correlated features -- and it adds another dependency layer to keep in step with your model library. If your audience is technical, the underlying plots are enough.

### SigLIP
**Short:** Google's sigmoid-loss image-text encoder; a CLIP alternative giving stronger joint image and text embeddings.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @2

It replaces CLIP's softmax cross-entropy over a batch with an independent sigmoid loss on every image-text pair: each pair is simply a positive or a negative binary decision, with a learned bias to offset the overwhelming number of negatives. Because the loss no longer needs a normalization over all similarities in the batch, there is no all-gather across devices and no dependence on batch size for correctness, which makes small-batch training viable and large-batch training cheaper.

Reach for it over CLIP for any new joint image-text embedding work: at matched compute it retrieves and classifies better, and the checkpoints cover more sizes and resolutions. The embedding space is not CLIP's, so it is not a drop-in for an index or a downstream component already fitted to CLIP vectors -- switching means re-embedding the corpus.

### SigLIP SO400M
**Short:** Google's 400M-parameter sigmoid-loss image-text encoder; a stronger CLIP replacement for VLM vision towers.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

The interesting part is the shape: rather than taking a standard Large or Huge configuration, its width-to-depth ratio was chosen by a scaling-law search for the best quality at a fixed compute budget, yielding roughly four hundred million parameters that punch above that size. Combined with the sigmoid training objective, it became the default vision tower in a large number of open vision-language models.

Reach for it when you need one strong image encoder -- as a frozen tower for a VLM, or for retrieval where its embedding quality per unit of compute is the argument. It is still a fixed-resolution patch encoder, so very high-resolution documents need tiling around it, and adopting it means committing to its embedding space: anything already indexed with a different encoder has to be rebuilt.

### Silero VAD
**Short:** Tiny open-source voice activity detector that marks speech versus silence before transcription or turn-taking.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

It is a small neural network -- a couple of megabytes, shipped as a portable traced or ONNX graph -- that consumes fixed-size chunks of audio and returns a speech probability for each. Because it is learned rather than energy-based, it distinguishes speech from music, keyboard noise and background hum instead of firing on anything loud, and it still costs a fraction of a millisecond per chunk on one CPU thread, so it can run per stream at scale.

Reach for it at the front of any audio pipeline: trimming silence before transcription so you do not pay to recognise nothing, and detecting end-of-turn in a voice agent. The parameters that matter are the speech and silence duration thresholds, which trade a snappy turn against clipping someone who pauses mid-sentence -- that tuning, not the model, is what makes a voice agent feel patient or rude.

### SimCSE
**Short:** Contrastive method for sentence embeddings that uses dropout as the augmentation; still a strong unsupervised baseline.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/ann-index-library @2, model-training/fine-tuning-and-peft @3

Its unsupervised form is almost embarrassingly simple: encode the same sentence twice with dropout active, treat those two slightly different vectors as a positive pair, treat the other sentences in the batch as negatives, and train with a contrastive loss. Dropout is the augmentation, and it beats deletion or synonym substitution because it perturbs the representation without changing the meaning. The supervised form is stronger, using entailment pairs as positives and contradictions as hard negatives.

The problem it solves is that raw encoder outputs occupy a narrow cone where almost every pair looks similar, so cosine distance is nearly meaningless; the contrastive objective spreads them out. Reach for it to adapt an encoder to a domain with unlabelled text alone. Off-the-shelf sentence embedding models trained on far more data are usually better unless your domain vocabulary is genuinely unusual.

### sklearn export_text
**Short:** scikit-learn function printing a fitted decision tree as human-readable if/else rules for extraction or review.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

### sklearn OneClassSVM
**Short:** One-class SVM for novelty and anomaly detection; the nu parameter sets how tightly the boundary hugs the normal data.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/classical-ml-and-boosting @2

### sklearn permutation_importance
**Short:** Model-agnostic feature importance measuring the metric drop when one column is shuffled on held-out data.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

### sklearn plot_tree
**Short:** scikit-learn helper that draws a fitted decision tree with matplotlib; readable only for shallow trees.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

### sklearn-crfsuite
**Short:** scikit-learn wrapper over CRFsuite for linear-chain CRF sequence labeling with hand-built features on CPU.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @2

It wraps a fast C++ linear-chain CRF in a scikit-learn estimator, and the input is deliberately plain Python: each sentence is a list of feature dictionaries you write yourself -- word identity, suffixes, capitalization, shape, the neighbours' features -- and a list of labels. Training is L-BFGS with L1 and L2 penalties, hyperparameter search works through the usual scikit-learn machinery, and the learned transition and state weights can be read directly.

Reach for it when labelled data is scarce and the entities follow visible surface patterns -- part numbers, dosages, addresses -- where hand-written features encode knowledge a neural model would need thousands of examples to infer. Models are tiny and train in seconds on CPU with fully inspectable weights. Where data is plentiful and patterns are semantic rather than orthographic, a fine-tuned encoder wins clearly.

### sklearn.inspection
**Short:** scikit-learn module for model inspection: permutation_importance plus partial dependence and ICE plots.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### Sktime
**Short:** scikit-learn-compatible time-series toolkit: forecasting, classification and pipeline/transformer composition.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/classical-ml-and-boosting @3

Its organizing idea is reduction: a forecasting problem is converted into a supervised one by sliding a window over the series, so any scikit-learn regressor becomes a forecaster, and the same composition applies to classification and transformation. Around that sit pipelines, target transformers, and cross-validation splitters that expand or slide a window forward in time rather than shuffling, plus adapters that put statsmodels, Prophet and other libraries behind the same interface so a comparison is honest.

Reach for it when you want one API across forecasting, time-series classification and clustering, and when comparing many model families matters more than raw speed. The costs are the abstraction: several layers of composition to trace when something misbehaves, and an interface that has changed noticeably across versions. For high-volume statistical forecasting alone, the Nixtla libraries are simpler and much faster.

### solo-learn
**Short:** PyTorch Lightning library implementing 20+ self-supervised visual representation methods for research comparison.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @2

It implements the self-supervised visual methods -- contrastive, distillation-based, redundancy-reduction and masked-image approaches -- inside one PyTorch Lightning training loop, configured by files rather than by editing code. The detail that makes it useful in practice is the online evaluation: a linear probe and a nearest-neighbour check run alongside pretraining, so you can see representation quality improving instead of waiting for the run to finish before discovering it collapsed.

Reach for it to compare pretext tasks on your own data under identical augmentation, optimizer and schedule, which is the only way published differences between these methods survive contact with a new domain. It is a research harness, and the honest default remains downloading pretrained weights: self-supervised pretraining only pays off when your images are genuinely unlike anything public checkpoints have seen.

### spaCy
**Short:** Industrial NLP pipeline library: tokenization, sentence segmentation, POS, dependency parsing and NER on CPU.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/rag-and-document-processing @3, security/privacy-and-compliance @3

Calling the pipeline on a string runs a sequence of components over the text and returns a document whose tokens carry the part-of-speech tag, lemma, dependency head and entity label each component assigned. You choose the pipeline by size, from a small CPU-fast model to a transformer-backed one, and disable the components you do not need. It is built for throughput on ordinary hardware and batches documents through one call, which is why it remains the right tool for entity extraction, sentence segmentation or noun-phrase extraction over a large corpus where an LLM call per document would be absurd.

Two limits worth knowing: its tokenizer is a word tokenizer for linguistic annotation, not the subword tokenizer a language model needs, and the default English entity recognizer predicts a fixed label set with no email, phone or national-id categories — so PII detection needs patterns or a purpose-built library alongside it.

### spacy-transformers
**Short:** spaCy plugin running transformer models inside its pipeline for production NER and classification.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

It adds a transformer component to the pipeline whose output is shared: the model runs once per batch of documents, and the downstream tagger, parser, entity recognizer and text classifier all listen to that single output rather than each embedding the text themselves. During training gradients from every head flow back into the shared transformer, so the components are fine-tuned jointly, and the library handles aligning wordpiece boundaries to spaCy's tokens so span offsets stay correct.

Reach for it when a spaCy pipeline needs transformer-level accuracy and you want to keep the surrounding components, rules and serialization. The cost is throughput: it is an order of magnitude slower than a CPU pipeline and effectively requires a GPU for bulk work, so the usual pattern is a small model for volume and this one for the documents that matter.

### SpeechBrain
**Short:** PyTorch-native speech toolkit with recipes for ASR, speaker recognition, diarization and enhancement.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

A training run is a small class defining the forward pass and the loss, with everything else -- model objects, augmentations, optimizer, schedule -- instantiated directly from a YAML file, so the experiment configuration is the object graph rather than a pile of flags. For inference it ships pretrained interfaces that load a model from the Hub and transcribe, verify a speaker or separate a mixture in a few lines, without touching the training machinery.

Reach for it when you want to modify a speech model rather than only run one, and you would rather read PyTorch than a shell recipe -- it is markedly more approachable than the Kaldi-derived toolkits. The tradeoff is coverage: for the very latest published ASR results ESPnet usually has the recipe first, and for pure transcription a pretrained Whisper-class model needs no framework at all.

### Spektral
**Short:** Graph neural network library built on Keras/TensorFlow, the TF-ecosystem counterpart to PyTorch Geometric.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @2

Graph layers are ordinary Keras layers taking a node feature matrix and a sparse adjacency, so a graph network is built with the functional API and trained with `Model.fit` like anything else in TensorFlow. Its loaders handle the three ways graph data is batched -- a single large graph, many small graphs merged block-diagonally into one, or padded batches -- which is the part that trips people up when they first move from tabular to graph data.

Reach for it only when the stack is committed to TensorFlow and a graph model has to live inside it. The community, layer coverage and pace of development are all far behind PyTorch Geometric and DGL, which means missing recent architectures and thinner examples. If the framework is negotiable, that is the stronger reason to move than any feature comparison.

### SRILM
**Short:** Classic research toolkit for n-gram language models (ngram-count, ngram) with many smoothing options.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

It is the toolkit most of the n-gram vocabulary comes from: count and estimate models with a wide selection of smoothing methods, interpolate or merge several models, compute perplexity, rescore lattices and n-best lists, and build class-based, skip and cache variants that most other toolkits never implemented. Its plain-text ARPA output became the interchange format for n-gram models, which is what other tools, including KenLM, read.

Reach for it when you need a smoothing method or a model variant nothing else implements, or to reproduce an older speech or translation result. Its licence is research-only, which is exactly why production systems moved to KenLM -- that, plus KenLM's memory-mapped binary format and much faster queries. Treat this as the reference implementation and something else as the runtime.

### Stable Diffusion
**Short:** Open latent-diffusion text-to-image model family with a large ecosystem of fine-tunes, LoRAs and ControlNets.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

The word latent is the whole engineering story. An autoencoder compresses the image to a much smaller latent grid, the denoising network runs entirely in that compressed space conditioned on text embeddings through cross-attention, and only the final decode returns pixels -- which is what brought image generation from a datacentre onto a consumer GPU. Guidance scale controls how hard sampling is pushed toward the prompt, trading prompt fidelity against diversity and, pushed too far, against image quality.

Reach for it when you need control and local hosting: the ecosystem of fine-tunes, low-rank adapters, spatial conditioning through ControlNet and inpainting pipelines has no equivalent among hosted models. The costs are that prompt adherence out of the box trails the best hosted models, quality varies wildly between community checkpoints, and the release licences carry use restrictions worth reading before shipping.

### Stanford CoreNLP
**Short:** Java NLP pipeline with PCFG and shift-reduce constituency parsers, dependency parsing, NER and coref.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/nlp-and-text @1

It is a JVM pipeline configured by naming the annotators you want in order -- tokenize, sentence split, tag, lemmatize, recognise entities, parse, resolve coreference, extract open-domain relations -- each writing its results onto a shared annotation object. It runs embedded in a Java application or as an HTTP server that clients in other languages call, which is how most Python users reach it.

Its remaining draw is the pieces nothing else packages as well: coreference resolution and open information extraction. Everything else argues against it -- a JVM dependency, several gigabytes of memory for the heavier annotators, throughput far below spaCy, and a licence that requires a commercial agreement for closed-source use. For tokenization, tagging, parsing and entities, use spaCy or Stanza and call this only for what they lack.

### Stanza
**Short:** Stanford's neural NLP pipeline: biaffine Universal Dependencies parsing and tagging for 60+ languages.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

Its models are trained per language on Universal Dependencies treebanks, so tokenization, multiword expansion, tagging, morphological features and dependency parsing all follow the same annotation scheme across seventy-odd languages -- which is what makes cross-lingual work comparable rather than an exercise in reconciling tagsets. Parsing uses a biaffine scorer over all candidate head-dependent pairs followed by a maximum spanning tree, so the tree is chosen globally.

Reach for it when linguistic accuracy across many languages is the requirement, or when output must be in Universal Dependencies form for downstream tooling. The cost is speed: several neural models run per sentence, so it is far slower than spaCy and wants a GPU for corpus-scale work. It also includes a client for CoreNLP, which is the practical way to get coreference from Python.

### StarCoder2
**Short:** Open-weight code generation model family trained on permissive source; a strong base for self-hosted completion.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agentic-environments @3

Its distinguishing property is provenance. It was trained on a permissively licensed source corpus built from a software archive with licence filtering and a developer opt-out mechanism, and tooling exists to trace generated code back to similar training data -- which is the difference between a model a legal review will approve and one it will not. Technically it uses fill-in-the-middle training and grouped-query attention over a context long enough for multi-file prompts.

Reach for it when self-hosted code completion has to satisfy a provenance requirement, and for the smaller sizes when completion latency inside an editor is the constraint. Raw capability is where it loses: later open code models and general frontier models write better code, so this is a compliance-and-cost choice as much as a quality one.

### statsforecast
**Short:** Nixtla's fast statistical forecasting library (ARIMA, ETS, Theta) also used for forecast-residual anomaly detection.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

It is the same statistical models everyone already trusts -- automatic ARIMA and exponential smoothing order selection, Theta, seasonal naive baselines -- reimplemented with numba so they compile to machine code, then applied in parallel across a long dataframe of many series. The result is a difference of one to two orders of magnitude in wall clock, which changes what is feasible: per-SKU or per-store forecasting becomes a job that finishes rather than a distributed system.

Reach for it whenever the problem is many short-to-medium univariate series, and specifically as the baseline before any deep forecasting model, since a well-tuned automatic ARIMA is frequently not beaten. It also serves the anomaly case, where a point far outside the prediction interval is the signal. Complex exogenous drivers and cross-series learning are where a global neural model earns its cost instead.

### Stellargraph
**Short:** Graph neural network library built on TensorFlow/Keras for node classification and link prediction.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1

It provided Keras-based graph neural networks with an emphasis on the heterogeneous and link-prediction cases: inductive neighbourhood-sampling models for graphs with typed nodes, random-walk embedding methods, and generators plus edge splitters that construct the negative samples and train/test splits link prediction needs -- the plumbing that is easy to get subtly wrong and leak on.

Development stopped years ago, so it depends on old TensorFlow versions and receives no fixes; treat encounters with it as maintenance of existing code rather than a choice for new work. The link-prediction splitting discipline it enforced is worth carrying forward regardless: sampling negative edges and holding out edges rather than nodes is what makes a reported score mean anything. PyTorch Geometric or DGL are the current implementations.

### StyleGAN2-ADA
**Short:** NVIDIA's StyleGAN2 reference implementation with adaptive augmentation for training generators on small datasets.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

Adaptive discriminator augmentation solves GAN training on small datasets. Augmentations are applied to every image the discriminator sees, real and generated alike, through a differentiable and invertible pipeline, so the generator does not learn to reproduce the augmentations themselves -- the leakage that makes naive augmentation useless here. The augmentation probability is not a hyperparameter: it is adjusted during training from a running measure of how much the discriminator is overfitting.

That turned a technique needing tens of thousands of images into one that works with a few thousand, which is the regime most real datasets occupy. Reach for it when you need a high-fidelity unconditional image generator for a narrow domain and have limited data. For general text-conditioned generation, diffusion models have displaced GANs entirely; StyleGAN's remaining advantages are single-pass sampling speed and a well-understood latent space for editing.

### subword-nmt
**Short:** The original BPE subword implementation from Sennrich 2016, still a reference for machine translation preprocessing.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

It is byte-pair encoding in its original form: count symbol pair frequencies over a word-frequency table, greedily merge the most frequent pair, repeat for a set number of operations, and write out the ordered merge list. Applying it re-runs those merges on new text and marks split points with a continuation marker so detokenization is a string replacement. A vocabulary threshold option prevents emitting subwords at test time that were too rare in training.

Its assumption is the thing to understand: it operates on text that has already been tokenized into whitespace-separated words, so a separate tokenizer must run first and detokenization must undo both steps -- exactly the coupling SentencePiece removed by working on raw characters. Reach for it to reproduce older translation results; use SentencePiece or the Hugging Face tokenizers for new work.

### supar
**Short:** PyTorch library of biaffine dependency and constituency parsers reproducing the standard research baselines.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

It packages the modern syntactic parsers as trained models you can load by name: biaffine dependency parsing, where every candidate head-dependent pair is scored by a bilinear function over specialised representations and decoded to a well-formed tree, plus constituency and semantic dependency variants, and higher-order models that score interactions between arcs rather than each arc independently. Encoders range from recurrent networks to pretrained transformers.

Reach for it when you need research-grade parsing accuracy and the numbers must be comparable with published results on standard treebanks, or when you want to train a parser on your own treebank without reimplementing the decoding. It is a parsing library rather than a pipeline, so tokenization and downstream components are yours to supply; spaCy or Stanza are the choice when you want the whole annotation stack.

### Surprise
**Short:** Small Python recommender library for explicit-rating collaborative filtering (SVD, KNN); prototyping only.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/classical-ml-and-boosting @3

Surprise mirrors the scikit-learn shape — `Dataset`, an algorithm object such as `SVD` or `KNNBasic`, then `cross_validate` reporting RMSE and MAE — over the explicit-rating problem: a user gave an item a score, predict the missing scores. That framing is the limit as much as the appeal. It has no real story for implicit feedback (clicks, plays), side features, or top-N ranking metrics, and it is single-machine and in-memory.

Use it to learn or demonstrate matrix factorization and neighbourhood methods on a MovieLens-sized dataset. Once the signal is implicit or the catalogue is large, move to a library built for that — `implicit` or LightFM for classical approaches, a two-tower model for anything learned end to end.

### TensorFlow Recommenders
**Short:** Google's TensorFlow library for recommenders, with built-in two-tower retrieval and ranking tasks.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @3

TFRS supplies the pieces of the two-stage recommender that are tedious to write correctly. `tfrs.tasks.Retrieval` implements the in-batch sampled-softmax loss and the top-k evaluation over a candidate corpus for a two-tower model, `tfrs.tasks.Ranking` covers the scoring stage, and `tfrs.layers.factorized_top_k` turns the trained item tower into a brute-force or ScaNN index you can serve from. Everything is Keras layers, so a model is still `Model.fit`.

Reach for it when the stack is already TensorFlow and you want the retrieval-then-ranking pattern without reimplementing sampled softmax and candidate sampling. On PyTorch the equivalent is writing those pieces yourself or using TorchRec.

### Themis-ML
**Short:** Early scikit-learn-compatible fairness library: reweighing and prejudice-remover discrimination-aware estimators.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

It is an early attempt at making fairness interventions ordinary scikit-learn objects: estimators that apply discrimination-aware training and preprocessing behind the same fit and predict interface as any other model, plus metrics quantifying the difference in favourable outcomes between groups. The design argument -- that fairness methods must compose with existing pipelines or nobody will use them -- was right, and is the one the mainstream libraries later adopted.

Treat it as a historical reference rather than a dependency. It predates the established toolkits, implements a small subset of what they cover, and is not actively maintained. For current work, fairlearn is the idiomatic scikit-learn choice and aif360 the exhaustive one, and both will match what a reviewer expects to see cited.

### tiktoken
**Short:** OpenAI's fast Rust byte-level BPE tokenizer, used to count and budget tokens before a call is made.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @2

tiktoken is the byte-level BPE tokenizer OpenAI's models use, implemented in Rust with Python bindings, so encoding a large document costs milliseconds. In practice you call `encoding_for_model()` to get the right encoding and then take the length of `encode(text)` — that number is what the API bills and what the context window counts.

It exists so you can answer "will this fit and what will it cost" before you send anything: trimming a chat history to a budget, chunking documents on token rather than character boundaries, and estimating spend per request all need a local count. It only speaks OpenAI's encodings; other vendors tokenize differently, so a count from here is an approximation for them, not an answer.

### timm
**Short:** PyTorch image-model library: hundreds of pretrained ViT/Swin/CNN backbones with a uniform fine-tuning interface.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/fine-tuning-and-peft @3, model-training/deep-learning-framework @3

`timm.create_model("vit_base_patch16_224", pretrained=True, num_classes=10)` downloads the checkpoint and swaps in a fresh head in one call, and `timm.data.resolve_data_config` returns the exact preprocessing that checkpoint was trained with — image size, normalization statistics, interpolation. Skipping that second step is the usual explanation for a pretrained backbone that mysteriously underperforms.

Beyond the model zoo it carries the training recipe: RandAugment and Mixup/CutMix, layer-decay optimizer setups, EMA of weights, and schedulers, which is why published vision results are often reproducible from it directly. Reach for it whenever you need an image backbone to fine-tune; the checkpoints live on the Hugging Face Hub, and the catalogue keeps growing, so query `timm.list_models(pretrained=True)` against your pinned version rather than trusting a remembered count.

### tokenizers
**Short:** Hugging Face's fast Rust-backed tokenizer library: train and apply subword vocabularies, count tokens at speed.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @3

A tokenizer here is an explicit pipeline - normalizer, pre-tokenizer, model (BPE, WordPiece or Unigram), post-processor, decoder - which you can train on your own corpus or load from the Hub, and the Rust implementation batches and parallelizes so encoding never becomes the bottleneck in a data loader.

The `Encoding` it returns carries offset mappings back into the original string, and that is what makes span-level tasks work at all: named-entity recognition and extractive question answering label subword tokens but must return character spans of the source. For context budgeting, count with the tokenizer of the model you are actually calling - vocabularies differ enough between model families that an estimate borrowed from another tokenizer will be wrong by more than your safety margin.

### tomotopy
**Short:** Fast C++ Gibbs-sampling topic models (LDA, HDP, DTM, CTM) with Python bindings; far quicker than pure Python.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

The samplers are C++ with SIMD and multithreading behind a small Python API, which makes collapsed Gibbs sampling on a large corpus a matter of minutes rather than an overnight job -- and Gibbs sampling is often what you want, since it tends to produce sharper topics than a variational fit. The model list goes well beyond plain LDA: hierarchical Dirichlet process to infer the topic count, correlated topics, dynamic topics over time, supervised and labelled variants, and priors that pin chosen words to chosen topics.

Reach for it when you want classical probabilistic topic models at speed, especially the variants that answer a specific question -- how topics evolve, which topics co-occur, how many there really are. For short, noisy modern text an embedding-based approach such as BERTopic usually reads better; for long documents the probabilistic models remain competitive and are far cheaper to fit.

### Top2Vec
**Short:** Topic modeling library that clusters joint document and word embeddings, so the topic count is discovered not set.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

It embeds documents and words into one space, reduces the dimensionality, and clusters the documents by density; each dense region's centroid becomes a topic vector, and the words nearest that centroid are the topic's description. Because the clustering finds however many dense regions exist, the number of topics is an output rather than an input, and a hierarchical reduction step merges them afterwards if you want a coarser view.

Reach for it when you do not know how many topics a corpus holds and would rather not sweep the parameter. It shares its lineage and its weaknesses with the embedding-and-clustering family: results shift with the dimensionality-reduction seed, and documents in sparse regions are discarded as noise. BERTopic's term-weighting produces cleaner topic labels and the project is more active, which usually decides it.

### torchaudio
**Short:** PyTorch audio library: I/O, resampling, STFT/mel/MFCC transforms, SpecAugment and RNN-T loss for speech models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

torchaudio supplies the audio half of a PyTorch pipeline: loading and saving files, resampling, and the transforms models actually consume — STFT, mel spectrograms, MFCCs — implemented as `nn.Module`s so they run on the GPU inside the training loop and stay differentiable instead of being precomputed by a CPU library. It also carries pieces specific to speech, notably SpecAugment-style time and frequency masking for augmentation and an RNN-T loss for streaming transducers.

Reach for it whenever raw waveforms have to become features. Keep resampling and normalization byte-identical between training and inference — a sample-rate or scaling mismatch is the classic reason a speech model that scored well in evaluation collapses in production.

### TorchCRF
**Short:** PyTorch conditional random field layer for sequence tagging, with constrained Viterbi decoding.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

It is a conditional random field layer for PyTorch taggers: emission scores from your encoder plus a learned transition matrix, trained on the exact sequence log-likelihood and decoded with Viterbi so the output respects the transitions the data actually contains. Worth knowing that several similarly named packages implement this same layer with slightly different capitalization and interfaces, which is a genuine source of confusion when copying code between projects -- check which one your imports resolve to.

Reach for it when the tag scheme has hard constraints that a per-token softmax keeps violating. Cost is quadratic in the tag count per position, so a large label inventory is expensive, and with a strong pretrained encoder the accuracy gain is often marginal -- constrained decoding, which forbids illegal transitions at inference without any extra parameters, captures much of the benefit for free.

### TorchRec
**Short:** PyTorch domain library for recommenders: sharded, model-parallel embedding tables that outgrow a single GPU.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/distributed-training @2

It exists for one problem: embedding tables that do not fit on a GPU. A planner examines the tables, the model and the hardware and chooses a sharding strategy per table -- by row, by column, whole-table, or replicated -- then wraps the model so the sparse part is model-parallel with all-to-all exchanges while the dense layers stay data-parallel. Variable-length sparse inputs are carried in a jagged tensor format so no padding is materialized, and fused optimizers update embeddings in place.

Reach for it when a recommender's embedding tables run to tens or hundreds of gigabytes, which is where an ordinary data-parallel training script simply cannot start. Below that scale it is overhead: the sharding plan, the input format and the distributed launch all add complexity that a single-GPU model does not need.

### torchtext
**Short:** PyTorch text utilities: vocabulary building, tokenization and dataset helpers for neural text models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

It covered the plumbing between raw text and a PyTorch model: building a vocabulary with frequency and minimum-count rules, mapping tokens to indices, and padding or packing variable-length sequences so an RNN or a TextCNN sees a rectangular batch. Numericalization and the pad-versus-pack distinction are the parts worth understanding, because they are what actually change the loss you compute over padded positions.

Most current pipelines get this from the Hugging Face tokenizers and datasets libraries instead, which carry subword tokenizers matched to pretrained checkpoints. Reach for torchtext mainly when following older PyTorch text tutorials or maintaining code built around them.

### TorchUQ
**Short:** PyTorch toolkit for uncertainty quantification: calibration methods, UQ metrics and diagnostic plots.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @2

It works directly on PyTorch tensors, which is the practical difference from the numpy-based uncertainty libraries: recalibration and evaluation run on the GPU alongside the model rather than as a separate offline step over exported predictions. It organizes methods around the form a prediction takes -- a point, an interval, a set of quantiles, a full distribution, an ensemble -- with conversions between them, so a metric or a recalibration method can be applied whatever shape your model outputs.

Reach for it when uncertainty handling belongs inside the training and serving code rather than in an analysis notebook. It is a smaller and less widely used library than the established alternatives, so expect to read source when something is unclear; Uncertainty Toolbox is the better-documented choice for a one-off evaluation and report.

### torchvision
**Short:** PyTorch's vision package: pretrained model zoo, dataset loaders, image transforms and augmentation ops.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

It ships with PyTorch and versions in lockstep with it, which is most of the argument for using it: dataset loaders, image transforms, pretrained backbones and detection and segmentation models, plus the low-level operators those models need. The detail worth knowing is that each set of pretrained weights carries the exact preprocessing it was trained with, so the resize, crop and normalization come from the weights object rather than being remembered or guessed.

Reach for it when you want a standard backbone or a reference detection model with no dependency beyond PyTorch, or for its operators, which are the fast implementations of non-maximum suppression, box IoU and region alignment that everyone else calls anyway. For breadth of modern architectures and training recipes, timm carries far more; torchvision's zoo is deliberately conservative.

### torchvision.models
**Short:** torchvision's zoo of pretrained image backbones: ResNet, EfficientNet, MobileNet, ViT.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @2

### torchvision.models.detection
**Short:** torchvision's pretrained object-detection models (Faster R-CNN, SSD, FCOS) for quick baselines.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### torchvision.ops
**Short:** torchvision's low-level vision operators: NMS, RoI Align, box IoU and mask utilities used by detection heads.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

### torchvision.transforms.v2
**Short:** Modern torchvision augmentation pipeline (resize, MixUp, CutMix) with one API across images and boxes.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### torchvision.utils.make_grid
**Short:** torchvision helper that tiles a batch of image tensors into one grid tensor for quick visual inspection.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### TransformerLens
**Short:** Mechanistic-interpretability library wrapping open-weight transformers with named hook points for caching and patching.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

It loads an open-weight transformer into a hooked model whose every internal activation — each head's attention pattern, each residual-stream position, each MLP output — is exposed at a named hook point, and normalizes weights across architectures so one piece of analysis code runs on different model families. Running with a cache returns every activation from a single forward pass, and running with hooks lets you overwrite an activation mid-forward: that is the mechanism behind activation patching, where you substitute one component's output from a different input and measure how much of the answer moves.

Reach for it when the question is which part of the model performs a computation, not how well the model scores. It is built for models you can hold in memory and inspect, not for production serving.

### transformers
**Short:** Hugging Face library to load, fine-tune and run pretrained transformer models across text, vision and multimodal tasks.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @2, applied-ml/vision-speech-and-multimodal @2, model-training/fine-tuning-and-peft @2, inference/inference-engine @3

The library's organizing idea is that a model id resolves to both weights and the matching preprocessing: `AutoTokenizer.from_pretrained(id)` and `AutoModelForSequenceClassification.from_pretrained(id)` cannot drift apart, which is what makes thousands of community checkpoints interchangeable. `pipeline()` is the one-line inference path, `Trainer` a training loop with distributed, mixed-precision and checkpointing already wired, and `model.generate()` the decoding entry point supporting greedy, beam, top-k and nucleus sampling plus the logits processors that constrain output.

Reach for it for fine-tuning, evaluation, and anything where you need to reach into the model. Do not reach for it as a production serving stack: `generate()` batches statically and keeps a per-request KV cache, so throughput at concurrency is a fraction of what vLLM or TGI get from the same checkpoint with continuous batching and paged attention.

### transformers.AutoTokenizer
**Short:** Loads the exact tokenizer that belongs to a checkpoint by name, keeping vocabulary and special tokens tied to the model.
**Kind:** api
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, runtime-systems/text-encoding-and-regex @2

### tsfresh
**Short:** Automated time-series feature extraction computing ~800 statistical features with significance-based selection.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/classical-ml-and-boosting @3

It computes several hundred descriptors per series from a catalogue of calculators -- autocorrelations at many lags, spectral coefficients, entropy measures, peak and crossing counts, trend fits, distributional statistics -- then filters them statistically: each feature is tested individually against the target with a test appropriate to the variable types, and a false-discovery-rate correction is applied because testing hundreds of features guarantees spurious hits otherwise. That correction is the part that separates it from just generating features and hoping.

Reach for it for time-series classification or regression where you intend to feed a gradient-boosted tree and do not want to hand-design features. The costs are compute -- reduced parameter sets exist for exactly this reason -- and heavy correlation among the surviving features, which makes any importance ranking over them hard to read. For forecasting, lag and calendar features you design yourself remain more interpretable.

### Twilio Media Streams
**Short:** Twilio feature that forks live phone-call audio to your WebSocket endpoint, the telephony bridge for a voice agent.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, apis-frameworks/rpc-graphql-and-streaming @2

A verb in the call's markup, or an API call, forks the live audio to a WebSocket you host: the connection carries JSON events -- a start event with call metadata, then a stream of media events each holding a base64 payload of a short audio frame, then a stop -- and a bidirectional stream lets you send audio back into the call and clear whatever is buffered when the caller interrupts.

Reach for it to put a voice agent on a real phone number without building telephony. The constraint that shapes everything downstream is the audio itself: telephony is narrowband companded audio, so it must be decoded and resampled before most models will accept it, and recognition accuracy is measurably worse than on wideband microphone input. Budget for that gap rather than discovering it in production.

### Ultralytics YOLOv8
**Short:** Object-detection and segmentation package with pretrained YOLO checkpoints and a one-line train/predict/export workflow.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3, inference/model-format-and-edge @3

The whole workflow is a handful of calls: `YOLO("yolov8n.pt")` then `.train(data="data.yaml")`, `.predict()`, and `.export(format="onnx")` or TensorRT/CoreML for deployment. The dataset format is a YAML pointing at image directories plus one text file per image holding normalized class-and-box lines, and the n/s/m/l/x size ladder is the accuracy-versus-latency dial, which is what makes it a common choice for cameras and edge devices as well as servers.

Check the license before it reaches production: the package is AGPL-3.0, so shipping it inside a closed-source product requires a commercial license from Ultralytics. That is a licensing decision, not a technical one, and it is easier to make before the model is embedded in a release.

### Uncertainty Toolbox
**Short:** Library of uncertainty-quantification metrics, calibration methods and diagnostic plots for predictive intervals.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @2

It answers what to report about a predictive distribution and how. Metrics are grouped into average calibration, whether predicted intervals cover at their stated rate; adversarial group calibration, whether that still holds on the worst subgroup you can find, which is the check that catches a model calibrated only on average; sharpness, since a useless model can be perfectly calibrated by predicting enormous intervals; and proper scoring rules that combine both. Recalibration fits a monotone map from stated to observed coverage.

Reach for it when a regression model's intervals have to be defended -- in a report, a review, or a safety case -- and you want the standard set of numbers and plots rather than an ad hoc one. It is numpy-based and regression-focused, sitting after the model rather than inside it, and it evaluates uncertainty rather than producing it.

### Universal Dependencies
**Short:** Cross-lingual treebank project: 200+ annotated corpora sharing one dependency scheme and the CoNLL-U format.
**Kind:** dataset
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, ml-lifecycle/evaluation-and-benchmarks @2

It is one annotation scheme applied across languages: a fixed inventory of universal part-of-speech tags, dependency relations and morphological features, with the design decision that content words are heads and function words attach to them -- which is what makes the same relation label mean the same thing in languages that express it with a preposition, a case ending, or word order. Over two hundred treebanks follow it, released on a regular versioned schedule in one file format.

Reach for it to train or evaluate a parser, or as the target scheme when linguistic annotation must be comparable across languages. The caveats are practical: treebanks differ enormously in size, genre and annotation quality, so a parser's score on one language says little about another, and the scheme itself changes between releases, meaning results are only comparable within a version.

### Vapi
**Short:** Hosted platform for real-time voice agents: telephony, streaming STT, LLM turn-taking and TTS behind one API.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/agent-framework @2

It assembles the voice-agent loop as a managed service: a phone number or SIP trunk, streaming speech recognition, endpoint detection deciding when the caller has finished, the language model turn, and speech synthesis -- with each stage's provider swappable and the whole assistant configured as a JSON document rather than code. Tool calls during a conversation hit your webhook, so the agent can look something up mid-sentence.

Reach for it to get a working phone agent in days instead of building the pipeline yourself. The costs stack literally: you pay telephony, recognition, model and synthesis per minute plus the platform's own fee, and end-to-end latency is the sum of every hop, which is where a native speech-to-speech model wins. Self-hosted frameworks give the same orchestration if you would rather own the infrastructure and the audio.

### VISSL
**Short:** Meta's PyTorch library of self-supervised vision recipes: SimCLR, MoCo, BYOL, DINO and BarlowTwins.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

VISSL is configuration-driven: a YAML picks the pretext task, the augmentations, the backbone and the schedule, and the library supplies reference implementations of the standard self-supervised recipes together with the evaluation protocol — linear probe and k-NN on frozen features — that makes your numbers comparable with published ones. That evaluation harness is usually the stronger reason to use it than the losses, which are short to write yourself.

Check how actively it is maintained against the alternatives before starting new work on it, since this area moves quickly and much recent vision self-supervision ships in the authors' own repositories.

### WaveGlow
**Short:** Flow-based neural vocoder converting mel spectrograms to waveform audio in one pass for TTS pipelines.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

It is a normalizing flow: a stack of invertible transformations -- affine couplings and learned invertible mixing convolutions -- conditioned on the mel spectrogram, trained by directly maximizing the likelihood of real audio under the transformed Gaussian. There is no adversary and no autoregression, so training is stable and generation is a single parallel pass that inverts the flow from sampled noise, unlike the sample-by-sample recurrence that made earlier neural vocoders unusable in real time.

Its interest now is mostly conceptual, as a clean demonstration that likelihood-based flows can synthesize audio. In practice it is very large and slow to train for its quality, and adversarial vocoders reach comparable or better audio at a small fraction of the parameters and compute -- which is why production text-to-speech pipelines use those instead.

### WEAT
**Short:** Word Embedding Association Test: an effect-size measure of social bias encoded in an embedding space.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1, applied-ml/nlp-and-text @2, ml-lifecycle/evaluation-and-benchmarks @3

It is the embedding version of the implicit association test. You define two sets of target words and two sets of attribute words, measure how much closer each target word sits to one attribute set than the other by mean cosine similarity, and report the standardized difference between the two target sets as an effect size, with a permutation test over the word assignments giving significance.

Reach for it as a quick, quantitative check on a static embedding before it becomes a feature in a decision system. Three limits matter. The result is extremely sensitive to the specific word lists, so it is easy to produce whatever answer you want; it measures the representation, not any downstream harm; and it assumes one vector per word, so contextual embeddings need an adapted variant rather than this test as published.

### WebRTC VAD
**Short:** Lightweight voice-activity detector from the WebRTC stack; trims silence and gates speech segments.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

It classifies fixed 10, 20, or 30 millisecond frames of 8 to 48 kHz PCM as speech or not, using a Gaussian mixture model over sub-band energies, with an aggressiveness setting from 0 to 3 that trades clipped speech against false triggers on noise. It is tiny, CPU-only, and effectively free, which is why it sits at the very front of an audio pipeline: trimming silence before feature extraction so you do not pay to transcribe nothing, and detecting end-of-turn in a voice agent so the model knows when to reply.

Being energy-based, it fires on doors, keyboards, and background speech, and it tells you that sound is present rather than that a particular person is speaking. Reach for it when the pipeline is quiet and latency budget is tight; in noisy environments a neural VAD such as Silero is materially more robust for the same negligible cost.

### What-If Tool
**Short:** Google's interactive TensorBoard/notebook widget for counterfactual probing and fairness-slice comparison.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @3

It makes probing a model a direct-manipulation activity rather than a scripting one. Load a dataset and one or two models, and you can edit a datapoint's feature values and watch the prediction move, jump to the nearest datapoint that the model classifies differently, slice the data by any feature and compare confusion matrices between slices, and apply per-slice thresholds that optimize a chosen fairness criterion to see what it would cost in accuracy.

Reach for it when the audience is a domain expert who should be poking at the model rather than reading a summary of it -- that hands-on comparison surfaces problems a metrics table hides. It was built around structured and image data and TensorFlow-era serving conventions; for text and sequence models the successor tool in the same lineup is the better fit.

### Whisper
**Short:** OpenAI's multilingual speech-recognition model; high-accuracy batch transcription, though not truly real-time.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

It is an encoder-decoder trained on a very large weakly supervised multilingual corpus, and its input is a fixed thirty-second window turned into a log-mel spectrogram, shorter audio being padded to fill it. The decoder is prompted with special tokens that select the language, choose between transcription and translation into English, and switch timestamps on, which is why one model performs language identification, transcription and translation without separate heads.

Reach for it for accurate offline transcription across many languages with no training of your own. Know the failure modes: the fixed window makes true streaming awkward, long audio is stitched from sequential windows so a timestamp error propagates forward, and it will hallucinate fluent sentences over silence or music. In production, an optimized runtime plus voice activity detection and forced alignment is what makes it usable rather than the reference implementation.

### Zendesk AI
**Short:** Zendesk's built-in support AI: automated ticket triage, routing, summarization and resolution suggestions.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agent-framework @3

Its advantage is position rather than capability: it already sits on the ticket stream, the customer history and the help-centre content, so intent, language and sentiment detection can drive existing routing and triage rules, bots can answer from published articles, and agents get reply suggestions and thread summaries without an integration project or a data pipeline.

Reach for it when support runs on Zendesk and the goal is deflection and faster handling rather than a bespoke experience. The limits follow from the same position: pricing is per resolution or per add-on on top of seats, you do not control the model, the prompts or the retrieval, and answer quality is capped by how good and how current your help-centre articles are. Building on an LLM with retrieval over the same content is the option when that control matters.
