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

### ADTK
**Short:** Anomaly Detection Toolkit: composable rule- and model-based detectors for time-series outliers.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

### Aequitas
**Short:** University of Chicago bias-audit toolkit producing report-style group fairness metrics for high-stakes models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @3

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

### Albumentations
**Short:** Fast image augmentation library that keeps masks, boxes and keypoints in sync with geometric transforms.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/labeling-and-synthetic-data @3

### alibi
**Short:** Python explainability library: anchors, counterfactuals, ALE plots and integrated gradients for black-box models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

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

### Anthropic Claude Haiku
**Short:** Anthropic's small, fast Claude model tier; the cheap worker model for narrow high-volume subtasks.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agent-framework @3

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

### BERTopic
**Short:** Topic-modeling library chaining embeddings, UMAP, HDBSCAN and c-TF-IDF; the modern default for short-text topics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3

### BertViz
**Short:** Interactive visualizer for transformer attention: per-head, per-layer and cross-attention heatmaps.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, applied-ml/nlp-and-text @3

### BLIP-2 OPT-2.7B
**Short:** Salesforce vision-language model pairing a frozen image encoder with OPT-2.7B via a Q-Former for captioning and VQA.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### BloombergGPT
**Short:** 50B-parameter LLM trained largely on Bloomberg's financial corpus for finance-domain NLP tasks.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

### captum
**Short:** PyTorch attribution library: Integrated Gradients, DeepLIFT, GradientSHAP, Occlusion and layer-level GradCAM.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### Cartesia Sonic
**Short:** Cartesia's low-latency streaming text-to-speech model, targeted at sub-100ms first-audio for voice agents.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### CausalML
**Short:** Uplift-modeling library: T/S/X-learners, causal forests and treatment-effect estimation for marketing use cases.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### CircuitsVis
**Short:** Embeddable interactive visualizations of attention patterns and neuron activations for interpretability notebooks.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### Claude 3.5 API
**Short:** Hosted Claude endpoint with strong vision and document understanding: OCR, charts, and layout-aware extraction.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/rag-and-document-processing @3, inference/model-server @3

### Claude 3.5 Sonnet
**Short:** Anthropic vision-language model noted for document OCR accuracy and structured extraction from images.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

### CLIP
**Short:** Contrastively trained image-text model placing pictures and captions in one embedding space for search.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

### CLIP ViT-L/14
**Short:** OpenAI's ViT-L/14 CLIP checkpoint producing joint image and text embeddings for retrieval and conditioning.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

### CoNLL-U
**Short:** Tab-separated file format for Universal Dependencies treebanks; read in Python via the conllu package.
**Kind:** spec
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, ml-lifecycle/evaluation-and-benchmarks @3

### ContextCite
**Short:** Attribution method that identifies which parts of the supplied context actually caused a model's statement.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, search-retrieval/rag-and-document-processing @2

### crepes
**Short:** Lightweight conformal-prediction library giving calibrated classifiers, regressors and Venn-Abers intervals.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### CRF++
**Short:** Classic C++ conditional random field trainer and decoder with template-based features for sequence labeling.
**Kind:** tech
**Lang:** cpp
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3

### daggity
**Short:** DAGitty: browser tool for drawing causal graphs and deriving valid adjustment sets for confounding.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### dalex
**Short:** Model-agnostic explanation and fairness auditing library with matching Python and R interfaces.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### DALL-E 3
**Short:** OpenAI text-to-image model, notable for close prompt adherence and in-prompt text rendering; called over the API.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Darts
**Short:** Name collision: the Darts forecasting library with a unified model API, and DARTS differentiable architecture search.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, ml-lifecycle/experiment-tracking-and-tuning @3

### DeepCTR
**Short:** Library of prebuilt CTR/CVR model layers (MMoE, PLE, ESMM, shared-bottom, DeepFM) for recommendation ranking.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1

### DeepCTR-Torch
**Short:** PyTorch library of prebuilt CTR/CVR ranking architectures: shared-bottom, MMoE, PLE, ESMM and deep-cross layers.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @3

### Deepgram Nova-2
**Short:** Deepgram's streaming speech-to-text model, tuned for low-latency real-time transcription in voice agents.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### DeepSeek-Coder
**Short:** Open-weight code LLM family (7B-33B) from DeepSeek; strong open-source code completion and generation.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agentic-environments @2

### DeepSeek-R1
**Short:** Open-weights reasoning LLM trained with RL to emit long chains of thought before its final answer.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

### DeepSeek-V2/V3
**Short:** Open-weight MoE LLM family whose multi-head latent attention compresses the KV cache by roughly 93% at long context.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, inference/quantization-and-compression @3, caching/semantic-and-llm-cache @3

### Detectron2
**Short:** Meta's PyTorch object-detection and segmentation library: Faster R-CNN, Mask R-CNN and Panoptic FPN baselines.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

### DETR
**Short:** DEtection TRansformer: end-to-end object detection with set prediction, no anchors or NMS.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### DGL
**Short:** Deep Graph Library for GNNs on PyTorch or TensorFlow; stronger than PyG for dynamic, heterogeneous graphs.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @2

### dice-ml
**Short:** Microsoft library generating diverse counterfactual explanations (random, genetic, gradient) for a model's decision.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### diffusers
**Short:** Hugging Face library for diffusion models: DDPM/DDIM schedulers, Stable Diffusion and ControlNet pipelines.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

### DINO-Det
**Short:** Transformer-based object detector from the DETR line, used as a strong detection backbone in vision pipelines.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### DINOv2
**Short:** Meta's self-supervised vision transformer checkpoints (ViT-S/B/L/g) giving strong general-purpose image features.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

### DistilBERT
**Short:** Distilled 6-layer BERT: 40% smaller, ~60% faster, most of BERT's accuracy; a cheap classification/NER workhorse.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, inference/quantization-and-compression @2

### DL Streamer
**Short:** GStreamer-based framework for building video-analytics pipelines with OpenVINO inference elements inline.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, inference/compiler-and-runtime-optimization @2

### doubleml
**Short:** Double/debiased machine learning library for treatment-effect estimation following Chernozhukov et al.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### DoWhy
**Short:** Causal inference library that makes you state a DAG, then identifies, estimates and refutes the effect.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### dtreeviz
**Short:** Python library drawing rich decision-tree visualizations with split distributions; clearer than sklearn's plot_tree.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

### EconML
**Short:** Microsoft library for heterogeneous treatment-effect estimation: Double ML, DR-learner and causal forests.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### EleutherAI sae library
**Short:** Open-source library for training sparse autoencoders (including TopK) on open models for interpretability.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

### ElevenLabs
**Short:** Commercial text-to-speech and voice-cloning API used for high-quality synthesized speech in voice agents.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Ember API
**Short:** Hosted API exposing sparse-autoencoder features and activation steering for open-weight models.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### ESPnet
**Short:** End-to-end speech research toolkit covering CTC/RNN-T/attention ASR, self-supervised speech models and TTS recipes.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### explainerdashboard
**Short:** Python library turning a fitted model into an interactive SHAP-based explanation dashboard for stakeholders.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### facebookresearch/blt
**Short:** Meta's Byte Latent Transformer reference code: entropy model, local encoder/decoder, tokenizer-free patches.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

### fairlearn
**Short:** Microsoft's fairness toolkit: MetricFrame group metrics plus reduction and threshold-optimizer mitigation algorithms.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @3

### fairseq
**Short:** Meta's sequence-modelling toolkit for training translation and language models with fast beam-search decoding.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @2, model-training/distributed-training @3

### fasttext
**Short:** Library for sub-word word embeddings and an ultra-fast linear n-gram text classifier that trains on CPU in seconds.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3, search-retrieval/ann-index-library @3

### FiftyOne
**Short:** Open-source tool for visualizing, curating and error-analysing image/video datasets and model predictions.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/labeling-and-synthetic-data @2, ml-lifecycle/evaluation-and-benchmarks @2

### fine-tuned encoders
**Short:** Small BERT-style encoders fine-tuned per task, the cheap millisecond alternative to an LLM for classification and NER.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, model-training/fine-tuning-and-peft @2

### FinGPT
**Short:** Open finance-domain LLM family fine-tuned for financial sentiment, entity and report analysis.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, model-training/fine-tuning-and-peft @3

### Fireflies.ai
**Short:** Hosted meeting-transcription and summarization assistant that joins calls and produces notes.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Flair
**Short:** NLP library combining contextual embeddings with BiLSTM-CRF for strong multilingual sequence labeling.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

### Florence-2-large
**Short:** Microsoft's compact vision-language model handling captioning, detection, grounding and OCR from one prompt interface.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Frontier hosted VLMs
**Short:** The current top-tier hosted vision-language models, used to describe charts and diagrams during document ingest.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/rag-and-document-processing @2

### Gemini 1.5 Pro
**Short:** Google's natively multimodal LLM handling text, image, audio and video with a very long (up to 1M token) context window.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/prompting-context-and-structured-output @3

### Gemini Diffusion
**Short:** Google's experimental diffusion text model, refining tokens in parallel passes rather than left to right.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

### Gemini Live
**Short:** Google's low-latency multimodal API for end-to-end speech-to-speech conversation with interruption handling.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/agent-framework @3

### Gemini Live API
**Short:** Google's bidirectional streaming endpoint carrying live audio and video to Gemini, for voice agents.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, apis-frameworks/rpc-graphql-and-streaming @2, llm-apps/agent-framework @3

### gensim
**Short:** Python library for classic word embeddings (word2vec, fastText, GloVe) and topic models (LDA, LSI, NMF).
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3, search-retrieval/ann-index-library @3

### GLiNER
**Short:** Compact zero-shot named-entity model that extracts arbitrary user-named entity types far cheaper than prompting an LLM.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/rag-and-document-processing @2

### GloVe
**Short:** Static word embeddings learned by factorizing a global co-occurrence matrix; downloadable pretrained vectors.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/ann-index-library @3

### GluonTS
**Short:** Probabilistic time-series forecasting library with DeepAR and Temporal Fusion Transformer on PyTorch backends.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/deep-learning-framework @3

### Goodfire
**Short:** Hosted interpretability API exposing sparse-autoencoder features and steering for open-weight models.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1, security/ai-safety-and-guardrails @3

### GoogleNews-vectors-negative300.bin
**Short:** Canonical pretrained word2vec embeddings: 3M words at 300 dimensions, roughly 3.5GB.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/ann-index-library @3

### GPT-2 byte-level BPE
**Short:** The byte-level BPE scheme that made subword vocabularies byte-safe; the tokenized baseline for comparison.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, runtime-systems/text-encoding-and-regex @3

### GPT-4o
**Short:** OpenAI's multimodal flagship: text, image and audio in one model, strong at multi-image reasoning.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

### GPT-4o API
**Short:** OpenAI's hosted multimodal endpoint accepting image and text input alongside text generation.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/llm-gateway-and-routing @2

### GPT-4V
**Short:** OpenAI's vision-capable GPT-4 endpoint: reasoning over one or many images alongside a text prompt.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/llm-gateway-and-routing @3

### GraphBolt
**Short:** Graph data-loading framework for TB-scale GNN training, replacing older neighbour-sampling loaders.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, data-movement/batch-and-distributed-compute @3

### grf
**Short:** Generalized Random Forests: the Athey lab R package for causal forests and heterogeneous treatment effects.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @2

### Grounding DINO
**Short:** Open-vocabulary object detector from IDEA-Research that localizes objects described by an arbitrary text prompt.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### HiFi-GAN
**Short:** GAN-based neural vocoder converting mel-spectrograms to waveform audio, the standard TTS synthesis backend.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### holisticai
**Short:** Multi-framework fairness toolkit measuring and mitigating bias across tabular, NLP and vision models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### HuggingFace diffusers
**Short:** HuggingFace library of diffusion pipelines and schedulers for image, audio and increasingly text generation.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

### HuggingFace tokenizers
**Short:** Rust-backed BPE/WordPiece/Unigram tokenizer training and inference, roughly 100x faster than pure Python.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, runtime-systems/text-encoding-and-regex @3

### HuggingFace transformers
**Short:** The standard Python library for loading, fine-tuning and generating with pretrained transformer, SSM and speech models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @2, model-training/fine-tuning-and-peft @2, applied-ml/vision-speech-and-multimodal @3, inference/inference-engine @3

### Hunspell
**Short:** Spell checker and morphological analyzer used for query correction and stemming in search pipelines.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/lexical-and-hybrid-search @2

### igraph
**Short:** Graph analysis library with a C core and Python bindings; community detection and centrality on large graphs.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, runtime-systems/collections-and-algorithms @2, data-stores/graph-db @3

### implicit
**Short:** Python library for implicit-feedback collaborative filtering: fast Cython/GPU ALS and BPR matrix factorization.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/classical-ml-and-boosting @3

### InstructBLIP
**Short:** Salesforce instruction-tuned vision-language model (BLIP-2 Q-Former plus a Vicuna LLM) for visual instruction following.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### InternVL2
**Short:** Open-weight vision-language model family from OpenGVLab; among the strongest open VLMs for image understanding.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### InternVL2-8B
**Short:** OpenGVLab's open-weights 8B vision-language model for image understanding, OCR and document QA.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### interpret
**Short:** InterpretML: the EBM glass-box GAM model plus one explain API and dashboard covering black-box explainers too.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @2

### Jasper
**Short:** Commercial AI writing platform for marketing teams: brand-voice-constrained copy generation at scale from templates.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @3

### Kaldi
**Short:** Long-standing C++ speech recognition toolkit of GMM-HMM and hybrid recipes; origin of the x-vector speaker model.
**Kind:** tech
**Lang:** cpp
**Roles:** applied-ml/vision-speech-and-multimodal @1

### KenLM
**Short:** Fast modified Kneser-Ney n-gram language model toolkit with quantized tries; standard in MT and ASR pipelines.
**Kind:** tech
**Lang:** cpp
**Roles:** applied-ml/nlp-and-text @1

### kjslag/spacebyte
**Short:** Reference implementation of SpaceByte, a byte-level tokenizer-free LM that patches at word boundaries.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

### Krisp
**Short:** AI noise and echo cancellation SDK for real-time voice; strips background noise before speech recognition.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Laplace
**Short:** PyTorch library adding post-hoc Bayesian uncertainty to a trained net via a Laplace posterior approximation.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### Leiden
**Short:** Community-detection algorithm improving on Louvain by guaranteeing well-connected clusters; the GraphRAG default.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/recommenders-and-graph-ml @1, runtime-systems/collections-and-algorithms @2, search-retrieval/rag-and-document-processing @3

### LeRobot
**Short:** Hugging Face library for robot policy training and deployment with pretrained ACT, diffusion and VLA checkpoints.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/fine-tuning-and-peft @2, model-training/alignment-and-rl @3

### librosa
**Short:** Python audio analysis library for loading waveforms and computing spectrograms, MFCCs and other features.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### LightFM
**Short:** Hybrid recommender combining matrix factorization with user/item feature embeddings for cold start; now dormant.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/classical-ml-and-boosting @3

### Lightly AI
**Short:** Self-supervised vision library (SimCLR, MoCo, BYOL, DINO, MAE) plus data-curation and active-learning selection.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/labeling-and-synthetic-data @2

### lime
**Short:** Local surrogate explainer for tabular, text and image models; unstable run to run, unmaintained since 2020.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### LLaDA
**Short:** Open 8B masked diffusion language model and reference implementation, the basis for open replication work.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, inference/inference-engine @3

### LLaMA 3.2 Vision
**Short:** Meta's open-weight 11B/90B vision-language models, self-hostable for image understanding and document Q&A.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### LLaVA
**Short:** Open visual instruction-tuned VLM (CLIP encoder plus an LLM); the standard baseline for open multimodal chat.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### LLaVA-1.5-13B
**Short:** Open 13B vision-language model pairing a CLIP encoder with an LLM for image question answering and captioning.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### LLaVA-1.5-7B
**Short:** Open 7B vision-language model pairing a CLIP encoder with a Vicuna LLM for image chat and VQA.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### LLaVA-1.6 Mistral
**Short:** Open 7B vision-language model pairing a CLIP encoder with Mistral for image question answering and captioning.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

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

### MABWiser
**Short:** Python multi-armed and contextual bandit library: epsilon-greedy, UCB1, Thompson sampling and LinUCB.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, ml-lifecycle/evaluation-and-benchmarks @3

### MALLET
**Short:** Java NLP toolkit whose optimized collapsed-Gibbs LDA often beats variational fits; driven from its CLI.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/nlp-and-text @1, model-training/classical-ml-and-boosting @3

### MaltParser
**Short:** Classic transition-based dependency parser; a historical baseline implementation for treebank parsing.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/nlp-and-text @1

### MAPIE
**Short:** scikit-learn-compatible conformal prediction library producing calibrated prediction intervals and sets.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @3

### Med-PaLM 2
**Short:** Google's medically tuned large language model for clinical question answering and documentation.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

### Mercury
**Short:** Commercial hosted diffusion language model, aimed at very low-latency code completion.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

### Merlin
**Short:** NVIDIA's GPU-accelerated recommender stack for ETL, embedding tables and training DLRM-style models at scale.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/distributed-training @3

### MiniCPM-V 2.6
**Short:** Compact open vision-language model from ModelBest/Tsinghua, sized to run image and video QA on-device.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### MMDetection
**Short:** OpenMMLab's PyTorch object-detection toolbox with a 50+ detector zoo under one training and eval config system.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/evaluation-and-benchmarks @3

### mmsegmentation
**Short:** OpenMMLab semantic-segmentation toolbox with 40+ reference models and a config-driven train/eval pipeline.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

### Model Card Toolkit
**Short:** Google library that assembles evaluation artifacts into a standard model card documenting intended use and limits.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/experiment-tracking-and-tuning @3, security/privacy-and-compliance @3

### ModernBERT
**Short:** Modernized BERT encoder with Flash Attention 2, unpadding and an 8192-token context for retrieval and tagging.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/ann-index-library @3

### MONAI
**Short:** PyTorch framework for medical imaging: DICOM/NIfTI transforms, 3D U-Net architectures and domain metrics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

### Moondream 2
**Short:** 1.86B-parameter Apache-2.0 vision-language model small enough to run captioning and VQA on edge hardware.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, inference/model-format-and-edge @2

### Moses
**Short:** Classic statistical MT toolkit whose tokenizer and detokenizer scripts are still the standard pre-subword step.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

### MoveIt
**Short:** ROS motion-planning and collision-checking framework, often the safety layer around a VLA's raw output.
**Kind:** tech
**Lang:** cpp
**Roles:** applied-ml/vision-speech-and-multimodal @1, security/ai-safety-and-guardrails @3

### MSTParser
**Short:** Historical graph-based dependency parser using maximum spanning trees; a reference baseline.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/nlp-and-text @1

### Neo4j GDS
**Short:** Neo4j Graph Data Science library: in-memory projections with PageRank, community detection and node embeddings.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/recommenders-and-graph-ml @1, data-stores/graph-db @2

### netcal
**Short:** Calibration library implementing temperature, Platt and isotonic scaling plus ECE-style calibration metrics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @2

### NeuralForecast
**Short:** GPU-ready deep forecasting library (NBEATS, TFT, DeepAR) behind a scikit-learn-style fit/predict API.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/deep-learning-framework @3

### Neuronpedia
**Short:** Web platform for browsing sparse-autoencoder features with top-activating examples and generated explanations.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### NLTK
**Short:** Classic Python NLP toolkit: tokenizers, WordNet, corpora, n-gram and parsing teaching implementations.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

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

### nnU-Net
**Short:** Self-configuring biomedical segmentation framework that derives its preprocessing and architecture per dataset.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/experiment-tracking-and-tuning @3

### NumPyro
**Short:** JAX-backed probabilistic programming library for Bayesian models via NUTS/MCMC and variational inference.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

### NVIDIA NeMo
**Short:** NVIDIA generative-AI toolkit: production ASR/TTS/speaker models plus LLM training and distillation pipelines.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/fine-tuning-and-peft @2, model-training/distributed-training @3, applied-ml/nlp-and-text @3

### OCTIS
**Short:** Framework for comparing topic models on standardized coherence and diversity metrics with hyperparameter search.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, ml-lifecycle/evaluation-and-benchmarks @2, ml-lifecycle/experiment-tracking-and-tuning @3

### OGB
**Short:** Open Graph Benchmark: standard graph datasets and evaluation protocol for node, link and graph tasks.
**Kind:** dataset
**Lang:** *
**Roles:** applied-ml/recommenders-and-graph-ml @1, ml-lifecycle/evaluation-and-benchmarks @2

### Open X-Embodiment dataset
**Short:** Cross-embodiment robot trajectory corpus (1M+ episodes, 22 embodiments) behind RT-X, OpenVLA and Octo.
**Kind:** dataset
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Open-weight VLMs
**Short:** Self-hostable open-weight vision-language models, used for cost-sensitive image understanding and indexing.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/rag-and-document-processing @2

### open_clip
**Short:** Open reproduction of CLIP with many pretrained image-text encoders (CLIP, SigLIP) for joint embeddings.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

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

### OpenAI reasoning models
**Short:** OpenAI models that spend hidden chain-of-thought before answering, with reasoning effort as the tuning knob.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agent-framework @3

### OpenAI TTS
**Short:** OpenAI's hosted text-to-speech endpoint returning streamed synthesized audio from text and a chosen voice.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

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

### OpenVLA
**Short:** The reference open 7B vision-language-action robotics model; the usual base for VLA fine-tuning research.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/fine-tuning-and-peft @3

### Opus 5
**Short:** Anthropic's frontier Claude model, used for deep reasoning and long agent trajectories with very large context windows.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @3, llm-apps/agentic-environments @3

### Otter.ai
**Short:** Commercial meeting transcription service producing speaker-attributed transcripts and summaries.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

### Penzai
**Short:** DeepMind's JAX library for building and surgically editing neural network internals with named-axis tensors.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

### pmdarima
**Short:** Wraps statsmodels in an sklearn API and adds auto_arima to search ARIMA/SARIMA orders automatically.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

### POPE
**Short:** Object-hallucination benchmark for vision-language models, built from negative existence questions about images.
**Kind:** dataset
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, ml-lifecycle/evaluation-and-benchmarks @2

### Prophet
**Short:** Additive forecasting library for business time series with trend, seasonality and holiday terms.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

### pyannote.audio
**Short:** Pretrained speaker-diarization and voice-activity pipelines built on PyTorch.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### pyLDAvis
**Short:** Interactive intertopic-distance and term-relevance visualizer, the standard way to inspect and label LDA topics.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, applied-ml/interpretability-fairness-and-causal @3

### PyOD
**Short:** Outlier-detection library with 40+ algorithms (ECOD, COPOD, HBOS, AutoEncoder, DeepSVDD) behind one API.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

### Pyro
**Short:** PyTorch-based probabilistic programming language for Bayesian models via variational inference and MCMC.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

### PyTorch Geometric
**Short:** The standard PyTorch library for graph neural networks: GNN layers, datasets, neighbour samplers.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @2

### pytorch-crf
**Short:** Drop-in conditional random field layer for PyTorch taggers: forward algorithm, Viterbi decode and masking.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

### pytorch-grad-cam
**Short:** Saliency-map library for CNNs and ViTs: Grad-CAM, Grad-CAM++, Score-CAM and Ablation-CAM.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, applied-ml/vision-speech-and-multimodal @2

### pyvene
**Short:** Stanford NLP library for declarative activation interventions - patching and steering - with reproducible configs.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### Qwen-VL-Chat
**Short:** Alibaba's open-weight vision-language chat model for image understanding, OCR and grounded visual dialogue.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Qwen3
**Short:** Alibaba's open-weight LLM family, including sparse MoE reasoning checkpoints split into Instruct and Thinking.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/llm-gateway-and-routing @2

### RankLib
**Short:** Java learning-to-rank library with reference implementations of LambdaMART, ListNet, RankNet and Coordinate Ascent.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/recommenders-and-graph-ml @1, search-retrieval/reranking @2, model-training/classical-ml-and-boosting @3

### RecBole
**Short:** Research framework with 100+ unified recommender implementations for benchmarking sequential and graph models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, ml-lifecycle/evaluation-and-benchmarks @2

### RepE
**Short:** Representation engineering: reading and steering model behaviour by manipulating activation directions.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1, security/ai-safety-and-guardrails @2

### responsibly
**Short:** Python toolkit to measure and mitigate bias in word embeddings and classifiers: WEAT effect sizes, Bolukbasi debiasing.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, applied-ml/nlp-and-text @2

### River
**Short:** Online machine-learning library that learns one sample at a time: streaming stats and Half-Space Trees.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1, model-training/classical-ml-and-boosting @2

### Ruptures
**Short:** Changepoint detection library for signals, with offline (Pelt, BinSeg) and online algorithms.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

### SAELens
**Short:** Library for training, loading and evaluating sparse autoencoders on model activations; pairs with TransformerLens.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/deep-learning-framework @3

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

### segment-anything
**Short:** Meta's official Segment Anything Model library: promptable zero-shot image segmentation from points, boxes or masks.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### SentencePiece
**Short:** Google tokenizer training BPE/Unigram subword vocabularies straight from raw text; used by LLaMA, T5 and Gemma.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, runtime-systems/text-encoding-and-regex @3

### SHAP
**Short:** Shapley-value feature attribution (TreeSHAP/KernelSHAP/DeepSHAP) explaining any model's individual predictions.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

### SHAP 0.44+
**Short:** Shapley-value attribution library; TreeSHAP computes exact per-feature contributions for any tree ensemble.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### shapash
**Short:** Python library producing turnkey, business-readable SHAP explanation dashboards and reports over a fitted model.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1

### SigLIP
**Short:** Google's sigmoid-loss image-text encoder; a CLIP alternative giving stronger joint image and text embeddings.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @2

### SigLIP SO400M
**Short:** Google's 400M-parameter sigmoid-loss image-text encoder; a stronger CLIP replacement for VLM vision towers.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, search-retrieval/ann-index-library @3

### Silero VAD
**Short:** Tiny open-source voice activity detector that marks speech versus silence before transcription or turn-taking.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### SimCSE
**Short:** Contrastive method for sentence embeddings that uses dropout as the augmentation; still a strong unsupervised baseline.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/ann-index-library @2, model-training/fine-tuning-and-peft @3

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

### solo-learn
**Short:** PyTorch Lightning library implementing 20+ self-supervised visual representation methods for research comparison.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @2

### spaCy
**Short:** Industrial NLP pipeline library: tokenization, sentence segmentation, POS, dependency parsing and NER on CPU.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, search-retrieval/rag-and-document-processing @3, security/privacy-and-compliance @3

### spacy-transformers
**Short:** spaCy plugin running transformer models inside its pipeline for production NER and classification.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

### SpeechBrain
**Short:** PyTorch-native speech toolkit with recipes for ASR, speaker recognition, diarization and enhancement.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Spektral
**Short:** Graph neural network library built on Keras/TensorFlow, the TF-ecosystem counterpart to PyTorch Geometric.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @2

### SRILM
**Short:** Classic research toolkit for n-gram language models (ngram-count, ngram) with many smoothing options.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1

### Stable Diffusion
**Short:** Open latent-diffusion text-to-image model family with a large ecosystem of fine-tunes, LoRAs and ControlNets.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### Stanford CoreNLP
**Short:** Java NLP pipeline with PCFG and shift-reduce constituency parsers, dependency parsing, NER and coref.
**Kind:** tech
**Lang:** java
**Roles:** applied-ml/nlp-and-text @1

### Stanza
**Short:** Stanford's neural NLP pipeline: biaffine Universal Dependencies parsing and tagging for 60+ languages.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

### StarCoder2
**Short:** Open-weight code generation model family trained on permissive source; a strong base for self-hosted completion.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agentic-environments @3

### statsforecast
**Short:** Nixtla's fast statistical forecasting library (ARIMA, ETS, Theta) also used for forecast-residual anomaly detection.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/timeseries-and-anomaly @1

### Stellargraph
**Short:** Graph neural network library built on TensorFlow/Keras for node classification and link prediction.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1

### StyleGAN2-ADA
**Short:** NVIDIA's StyleGAN2 reference implementation with adaptive augmentation for training generators on small datasets.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1

### subword-nmt
**Short:** The original BPE subword implementation from Sennrich 2016, still a reference for machine translation preprocessing.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

### supar
**Short:** PyTorch library of biaffine dependency and constituency parsers reproducing the standard research baselines.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

### Surprise
**Short:** Small Python recommender library for explicit-rating collaborative filtering (SVD, KNN); prototyping only.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/classical-ml-and-boosting @3

### TensorFlow Recommenders
**Short:** Google's TensorFlow library for recommenders, with built-in two-tower retrieval and ranking tasks.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/deep-learning-framework @3

### Themis-ML
**Short:** Early scikit-learn-compatible fairness library: reweighing and prejudice-remover discrimination-aware estimators.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, model-training/classical-ml-and-boosting @3

### tiktoken
**Short:** OpenAI's fast Rust byte-level BPE tokenizer, used to count and budget tokens before a call is made.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @2

### timm
**Short:** PyTorch image-model library: hundreds of pretrained ViT/Swin/CNN backbones with a uniform fine-tuning interface.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/fine-tuning-and-peft @3, model-training/deep-learning-framework @3

### tokenizers
**Short:** Hugging Face's fast Rust-backed tokenizer library: train and apply subword vocabularies, count tokens at speed.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, llm-apps/prompting-context-and-structured-output @3

### tomotopy
**Short:** Fast C++ Gibbs-sampling topic models (LDA, HDP, DTM, CTM) with Python bindings; far quicker than pure Python.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

### Top2Vec
**Short:** Topic modeling library that clusters joint document and word embeddings, so the topic count is discovered not set.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1

### torchaudio
**Short:** PyTorch audio library: I/O, resampling, STFT/mel/MFCC transforms, SpecAugment and RNN-T loss for speech models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

### TorchCRF
**Short:** PyTorch conditional random field layer for sequence tagging, with constrained Viterbi decoding.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

### TorchRec
**Short:** PyTorch domain library for recommenders: sharded, model-parallel embedding tables that outgrow a single GPU.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/recommenders-and-graph-ml @1, model-training/distributed-training @2

### torchtext
**Short:** PyTorch text utilities: vocabulary building, tokenization and dataset helpers for neural text models.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @3

### TorchUQ
**Short:** PyTorch toolkit for uncertainty quantification: calibration methods, UQ metrics and diagnostic plots.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @2

### torchvision
**Short:** PyTorch's vision package: pretrained model zoo, dataset loaders, image transforms and augmentation ops.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

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

### transformers
**Short:** Hugging Face library to load, fine-tune and run pretrained transformer models across text, vision and multimodal tasks.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/nlp-and-text @1, model-training/deep-learning-framework @2, applied-ml/vision-speech-and-multimodal @2, model-training/fine-tuning-and-peft @2

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

### Twilio Media Streams
**Short:** Twilio feature that forks live phone-call audio to your WebSocket endpoint, the telephony bridge for a voice agent.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, apis-frameworks/rpc-graphql-and-streaming @2

### Ultralytics YOLOv8
**Short:** Object-detection and segmentation package with pretrained YOLO checkpoints and a one-line train/predict/export workflow.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3, inference/model-format-and-edge @3

### Uncertainty Toolbox
**Short:** Library of uncertainty-quantification metrics, calibration methods and diagnostic plots for predictive intervals.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @2

### Universal Dependencies
**Short:** Cross-lingual treebank project: 200+ annotated corpora sharing one dependency scheme and the CoNLL-U format.
**Kind:** dataset
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, ml-lifecycle/evaluation-and-benchmarks @2

### Vapi
**Short:** Hosted platform for real-time voice agents: telephony, streaming STT, LLM turn-taking and TTS behind one API.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, llm-apps/agent-framework @2

### VISSL
**Short:** Meta's PyTorch library of self-supervised vision recipes: SimCLR, MoCo, BYOL, DINO and BarlowTwins.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/vision-speech-and-multimodal @1, model-training/deep-learning-framework @3

### WaveGlow
**Short:** Flow-based neural vocoder converting mel spectrograms to waveform audio in one pass for TTS pipelines.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### WEAT
**Short:** Word Embedding Association Test: an effect-size measure of social bias encoded in an embedding space.
**Kind:** concept
**Lang:** *
**Roles:** applied-ml/interpretability-fairness-and-causal @1, applied-ml/nlp-and-text @2, ml-lifecycle/evaluation-and-benchmarks @3

### WebRTC VAD
**Short:** Lightweight voice-activity detector from the WebRTC stack; trims silence and gates speech segments.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1

### What-If Tool
**Short:** Google's interactive TensorBoard/notebook widget for counterfactual probing and fairness-slice comparison.
**Kind:** tech
**Lang:** python
**Roles:** applied-ml/interpretability-fairness-and-causal @1, ml-lifecycle/evaluation-and-benchmarks @3

### Whisper
**Short:** OpenAI's multilingual speech-recognition model; high-accuracy batch transcription, though not truly real-time.
**Kind:** model
**Lang:** *
**Roles:** applied-ml/vision-speech-and-multimodal @1, applied-ml/nlp-and-text @3

### Zendesk AI
**Short:** Zendesk's built-in support AI: automated ticket triage, routing, summarization and resolution suggestions.
**Kind:** tech
**Lang:** *
**Roles:** applied-ml/nlp-and-text @1, llm-apps/agent-framework @3
