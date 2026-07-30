# Inference & optimization — technology bank

<!-- tech-bank tier: inference -->

The 94 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Inference & optimization** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### Anthropic API
**Short:** Hosted endpoint for Claude models with native tool use, streaming and cache_control prompt caching.
**Kind:** model
**Lang:** *
**Roles:** inference/model-server @1, llm-apps/tool-use-and-mcp @2, caching/semantic-and-llm-cache @2, applied-ml/nlp-and-text @3

### Anthropic APIs
**Short:** Hosted Claude model endpoints for chat, tool use and long-context prompting with no training code required.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, llm-apps/prompting-context-and-structured-output @3, llm-apps/tool-use-and-mcp @3

### AutoAWQ
**Short:** Toolkit that applies AWQ 4-bit activation-aware weight quantization to Hugging Face LLMs.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1

### AutoGPTQ
**Short:** GPTQ post-training quantization for Hugging Face transformer checkpoints, producing 4-bit GPU models.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, inference/inference-engine @3

### AWQ
**Short:** Activation-aware Weight Quantization: 4-bit post-training quantization protecting the ~1% salient channels, no backprop.
**Kind:** concept
**Lang:** *
**Roles:** inference/quantization-and-compression @1, inference/inference-engine @3

### Azure OpenAI
**Short:** Microsoft-hosted OpenAI model endpoints with Azure identity, networking, quota and compliance controls.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/cloud-platform-and-cost @2, llm-apps/llm-gateway-and-routing @3

### BentoML
**Short:** Python-native model serving framework that packages models into containerized inference services.
**Kind:** tech
**Lang:** python
**Roles:** inference/model-server @1, ml-lifecycle/ml-platform-and-pipelines @3

You describe a service in Python -- a class with an inference method and an API signature -- and BentoML builds a Bento, a versioned bundle of model, code and dependencies that it can turn into an OCI image for Kubernetes. At runtime it gives you adaptive request batching, independent scaling of the model runner from the API layer, and composition of several models behind one endpoint.

Reach for it when packaging and shipping the model is the hard part and the model is a normal Python one. For large-language-model throughput the serving is better done by a dedicated engine such as vLLM or TensorRT-LLM, with BentoML wrapping and deploying it rather than replacing it.

### BitsAndBytes
**Short:** Load-time 4-bit (NF4/FP4) and 8-bit quantization for PyTorch models; the layer QLoRA fine-tuning relies on.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, model-training/fine-tuning-and-peft @2

Quantization happens at load time: pass a `BitsAndBytesConfig` to `from_pretrained` and weights are stored as NF4, FP4 or INT8 while activations stay in a higher compute dtype, each tile dequantized on the fly for its matmul. That is what makes QLoRA work — a frozen 4-bit base model fits on a single consumer GPU while the trainable LoRA adapters stay in bf16.

Reach for it when memory is the binding constraint and you want a one-line change. It buys footprint rather than speed, since dequantizing costs work on every matmul, so an inference-oriented format such as AWQ, GPTQ or FP8 inside a serving engine will out-throughput it.

### Core ML
**Short:** Apple's on-device inference framework and .mlpackage model format, running models on the Neural Engine and GPU.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/inference-engine @3

### coremltools
**Short:** Apple's Python toolchain converting PyTorch/ONNX models to Core ML for on-device inference on iOS and macOS.
**Kind:** tech
**Lang:** python
**Roles:** inference/model-format-and-edge @1, inference/quantization-and-compression @3

### ctranslate2
**Short:** Fast C++ inference engine for transformer seq2seq and speech models with INT8/FP16 execution, driven from Python.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1, inference/quantization-and-compression @3, applied-ml/nlp-and-text @3

### DeepSparse
**Short:** Neural Magic's CPU inference runtime exploiting weight sparsity and quantization for GPU-class throughput.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1, inference/quantization-and-compression @2, inference/compiler-and-runtime-optimization @3

### Dynamo Triton
**Short:** NVIDIA's multi-framework inference server (formerly Triton): TF/PyTorch/ONNX/TensorRT backends, dynamic batching.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, inference/inference-engine @3, ml-lifecycle/ml-platform-and-pipelines @3

The server loads everything in a model repository directory, where each model carries a `config.pbtxt` declaring its backend, input and output tensors, how many instances to place on which GPUs, and dynamic-batching parameters; it then exposes HTTP and gRPC inference endpoints plus Prometheus metrics and health probes. Dynamic batching is the central feature: concurrent single requests are coalesced server-side into one GPU batch up to a queue delay you choose, which is what turns low per-request utilization into throughput. Ensembles chain preprocessing, model and postprocessing into one call, and several models can share a GPU through instance groups.

Reach for it when you serve many models of different frameworks on shared accelerators and want one operational surface for all of them. For a single large language model, a dedicated engine with continuous batching and paged KV cache is the better fit, because generation has a request shape that fixed-size batching serves poorly.

### EAGLE
**Short:** Speculative decoding method with lightweight feature-level draft layers trained for popular checkpoints.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1

### EAGLE-2
**Short:** Speculative decoding using feature-level autoregressive draft layers, with reference implementations.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1, inference/compiler-and-runtime-optimization @3

### EAGLE-3
**Short:** Speculative decoding whose draft head autoregresses over the target model's features, raising accepted tokens per step.
**Kind:** concept
**Lang:** *
**Roles:** inference/inference-engine @1

### ExecuTorch
**Short:** PyTorch's on-device runtime and .pte export format for running models on phones and embedded hardware.
**Kind:** tech
**Lang:** python
**Roles:** inference/model-format-and-edge @1, inference/inference-engine @3

### ExLlamaV2
**Short:** Fast quantized LLM inference runtime tuned for consumer GPUs; best speed/quality on GPTQ and EXL2 weights.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1, inference/quantization-and-compression @2

Its EXL2 weight format allows a different bit width per layer, so you can target an average bits-per-weight budget and spend the bits where the model is most sensitive, which is how it fits a larger model into a fixed amount of VRAM at better quality than a uniform quantization would. The kernels are written for single consumer cards; it is a library rather than a server, and TabbyAPI is the companion project that puts an OpenAI-shaped API in front of it.

Reach for it when the goal is the most tokens per second out of one desktop GPU. For multi-GPU serving with many concurrent users a throughput-oriented engine with continuous batching and paged attention is the better fit, and for CPU or Apple Silicon the GGUF ecosystem is where the support is.

### FlashInfer
**Short:** IO-aware attention kernel library for LLM serving; the attention backend inside vLLM and SGLang.
**Kind:** tech
**Lang:** cpp
**Roles:** inference/inference-engine @1, gpu/gpu-math-libraries @2, gpu/kernel-programming @3

### Gemma 2 2B
**Short:** Google's small open-weight model (2B and 9B) sized to run on a laptop or edge device under the Gemma terms of use.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

### GGUF
**Short:** Single-file model container used by llama.cpp: weights, tokenizer and metadata, usually quantized for local inference.
**Kind:** spec
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/quantization-and-compression @2, inference/inference-engine @3

### GGUF format
**Short:** Single-file quantized model container used by llama.cpp: weights, tokenizer and metadata in one mmap-friendly file.
**Kind:** spec
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/quantization-and-compression @2

### GGUF quantization comparison
**Short:** The perplexity-versus-size tradeoff table across GGUF quant levels, used to pick a local-model quantization.
**Kind:** concept
**Lang:** *
**Roles:** inference/quantization-and-compression @1, ml-lifecycle/evaluation-and-benchmarks @2, inference/model-format-and-edge @2

### GPTQ
**Short:** Weight-only 3/4-bit post-training quantization using approximate second-order error compensation; run via GPTQModel.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1

### GPTQModel
**Short:** Maintained GPTQ weight-only 3/4-bit post-training quantizer and kernels; the transformers GPTQ backend after auto-gptq.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, inference/inference-engine @3, model-training/fine-tuning-and-peft @3

GPTQ quantizes one layer at a time, going through the weight columns and using an approximate second-order update, derived from a Hessian estimated on a small calibration set, to compensate the error introduced so far in the columns not yet quantized. That compensation is why four-bit weights lose far less accuracy than naive rounding, and it is also why the calibration data matters: a few hundred samples that look nothing like your workload will produce a model that degrades on it.

It is weight-only quantization, so the win is memory footprint and memory bandwidth during decode, not arithmetic throughput during a compute-bound prefill. Reach for it to fit a model onto a smaller card; compare against AWQ on your own evaluation set, and on hardware with native low-precision support weigh it against FP8 or INT8 formats that speed up the matmuls themselves.

### H2O
**Short:** Heavy-Hitter Oracle: a KV-cache eviction policy keeping recent plus high-attention tokens to bound decode memory.
**Kind:** concept
**Lang:** python
**Roles:** inference/inference-engine @1, caching/semantic-and-llm-cache @2

### HuggingFace Inference API
**Short:** HuggingFace's hosted endpoint service for running any Hub model behind an HTTP API without managing GPUs.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, llm-apps/llm-gateway-and-routing @2

### HuggingFace TGI
**Short:** Text Generation Inference: Hugging Face's containerized LLM serving runtime with continuous batching and streaming.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, inference/model-server @2

### HuggingFace transformers GenerationConfig
**Short:** The object holding all decoding parameters (sampling, beam, contrastive search, typical_p) for generate().
**Kind:** api
**Lang:** python
**Roles:** inference/inference-engine @1, llm-apps/prompting-context-and-structured-output @3

### Intel Neural Compressor
**Short:** Intel's model-compression toolkit: post-training and aware quantization, pruning and distillation for CPU inference.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, inference/compiler-and-runtime-optimization @3

This is Intel's compression toolkit for models coming from PyTorch, TensorFlow or ONNX: post-training dynamic and static quantization, quantization-aware training, magnitude and structured pruning, and distillation, all driven from one configuration rather than framework-specific code. Its distinguishing feature is the accuracy-aware tuning loop, which tries quantization recipes — which operators to keep in higher precision, which calibration to use — until the metric drop lands inside the tolerance you declared, instead of leaving you to bisect by hand.

Reach for it when serving happens on Intel CPUs and int8 or bf16 is what gets you to the throughput you need, since it targets the VNNI and AMX instructions that make CPU int8 worthwhile. On NVIDIA GPUs, the vendor's own toolchain is the better path.
### Intel OpenVINO
**Short:** Intel's inference toolkit: graph conversion, INT8 quantization and optimized CPU/iGPU/NPU runtimes.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, inference/quantization-and-compression @2, inference/model-format-and-edge @2, inference/model-server @3

### KServe
**Short:** Kubernetes CRD-based model serving with a standard inference protocol, canary rollout and scale-to-zero.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/kubernetes-and-orchestration @2

You submit an `InferenceService` custom resource naming a model's storage URI and a serving runtime, and KServe builds the rest: a deployment running that runtime, a standard V2 inference protocol endpoint over HTTP and gRPC, request-driven autoscaling including scale-to-zero via Knative, and canary traffic splitting between revisions. Optional transformer and explainer containers sit in front of and beside the predictor so preprocessing and explanation are separate, independently scaled concerns.

Runtimes cover the usual frameworks plus dedicated LLM serving, and ModelMesh handles the many-small-models case by multiplexing them across a shared pool instead of one pod each. Reach for it when you already run Kubernetes and want a control plane around model servers rather than another model server; a single model behind a plain Deployment does not need this much machinery.

### LayerSkip
**Short:** Training recipe combining layer dropout with an early-exit head so a model can speculatively decode against itself.
**Kind:** concept
**Lang:** *
**Roles:** inference/inference-engine @1, model-training/fine-tuning-and-peft @2

### LLaMA 3.2 1B
**Short:** Meta's 1B-parameter small language model, sized for on-device and edge inference.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

### llama.cpp
**Short:** Minimal-dependency C++ LLM runtime for GGUF-quantized models on CPU, Metal or CUDA; rich sampler chain.
**Kind:** tech
**Lang:** cpp
**Roles:** inference/inference-engine @1, inference/quantization-and-compression @2, inference/model-format-and-edge @2, gpu/gpu-portability-and-precision @3

GGUF packs weights, quantization type, tokenizer and metadata into one file the runtime memory-maps, so deploying a model is copying a file rather than assembling a Python environment. The quantization schemes trade size against quality at a fine granularity, and the project ships a server with an OpenAI-compatible endpoint plus backends for CPU, CUDA, Metal and Vulkan, which is what makes the same build run on a laptop and on a small server.

Reach for it for local, edge or fully offline inference, for Apple Silicon, and when the deployment constraint is having no heavy dependency tree. On a datacentre GPU serving many concurrent users, an engine built around continuous batching and paged KV cache will get far more total throughput out of the same hardware.

### llama.cpp Android
**Short:** llama.cpp built for Android via JNI, running quantized GGUF models fully on-device.
**Kind:** tech
**Lang:** cpp
**Roles:** inference/model-format-and-edge @1, inference/inference-engine @2, inference/quantization-and-compression @3

### llama.cpp quantize
**Short:** llama.cpp CLI converting an FP16 GGUF checkpoint to a lower-bit GGUF variant from Q2 up to Q8.
**Kind:** tech
**Lang:** cpp
**Roles:** inference/quantization-and-compression @1, inference/model-format-and-edge @2

### llm-compressor
**Short:** vLLM-project library producing quantized checkpoints - AWQ, GPTQ, FP8, sparsity - via recipes run by oneshot().
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, inference/inference-engine @3

### llm-compressor, AutoAWQ, GPTQModel
**Short:** Offline quantization toolchains that produce FP8, AWQ and GPTQ checkpoints a serving engine can load.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1

### LM Studio
**Short:** Desktop app for downloading and chatting with local GGUF models, exposing an OpenAI-compatible local server.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, inference/model-format-and-edge @2

### MediaPipe LLM
**Short:** Google's MediaPipe LLM Inference API running small TFLite/LiteRT models fully on-device on Android, iOS and web.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/inference-engine @3

### MLC-LLM
**Short:** Compiles and runs LLMs on phones, browsers and laptops by generating device-specific kernels via TVM.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/inference-engine @2, inference/compiler-and-runtime-optimization @3

MLC-LLM compiles a model ahead of time through TVM into kernels for whichever backend the target device has — Metal, Vulkan, WebGPU, CUDA — so inference runs natively on a phone or inside a browser tab with no Python runtime and no server call. Weights are converted and quantized into its own format during that build step, which is why deployment here is a compile pipeline rather than an install command.

Reach for it when the model must run on the user's device for privacy, offline use or per-request cost. The price is that every target platform is another build, and shipping a new model means shipping a new artifact.

### Model Navigator
**Short:** NVIDIA tool converting a PyTorch/TF/ONNX model to optimized formats such as TensorRT and verifying accuracy.
**Kind:** tech
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, inference/model-format-and-edge @2, inference/model-server @3, ml-lifecycle/evaluation-and-benchmarks @3

### Model serving
**Short:** The serving layer that exposes a trained model as an endpoint: batching, versioning and replica autoscaling.
**Kind:** concept
**Lang:** *
**Roles:** inference/model-server @1

### Model-control sidecars
**Short:** A sidecar or init job calling Triton's model-control API, so which models a replica loads is decoupled from its image.
**Kind:** concept
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/kubernetes-and-orchestration @2

### model_analyzer
**Short:** Triton tool sweeping instance-group and batching configs, reporting the throughput/latency/memory Pareto frontier.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, observability/profiling-and-performance @2, platform-delivery/cloud-platform-and-cost @3

### NNCF
**Short:** Intel's compression framework for OpenVINO: post-training and accuracy-aware quantization, pruning, weight compression.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, inference/compiler-and-runtime-optimization @3

### NPU
**Short:** On-die neural accelerator (e.g. Core Ultra AI Boost) for sustained low-power INT8 inference beside the CPU and GPU.
**Kind:** concept
**Lang:** *
**Roles:** inference/model-format-and-edge @1, runtime-systems/memory-processes-and-os @3

### NVIDIA Triton
**Short:** Multi-framework inference server with dynamic batching, model ensembles and concurrent model instances.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, inference/inference-engine @3, ml-lifecycle/ml-platform-and-pipelines @3, platform-delivery/kubernetes-and-orchestration @3

### NVIDIA Triton OpenVINO backend
**Short:** Triton backend that executes models through Intel OpenVINO so CPU models serve inside the same Triton fleet.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, inference/compiler-and-runtime-optimization @2, inference/model-format-and-edge @3

### Ollama
**Short:** One-command local LLM runner: pulls GGUF models and exposes them over a local HTTP API on Mac, Linux or Windows.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, inference/model-format-and-edge @3

One command pulls a quantized model and starts a local server, so a laptop gets a working model endpoint without you choosing a runtime, a quantization format or a GPU flag. A Modelfile pins the weights, the system prompt and the sampling parameters into a named model you can share, and the server exposes an HTTP API on a local port including an OpenAI-compatible surface — which is what makes it a drop-in while developing against a hosted provider.

Reach for it for local development, offline work and privacy-sensitive prototyping. To serve many concurrent users you want an engine built for continuous batching and paged attention instead.

### ONNX
**Short:** Open interchange format for neural networks, letting a model trained in one framework be served or compiled by another.
**Kind:** spec
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/compiler-and-runtime-optimization @2, inference/model-server @3

### ONNX Runtime
**Short:** Cross-platform runtime that executes and optimizes ONNX graphs on CPU, GPU and edge, with quantization APIs.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, inference/model-format-and-edge @2, inference/quantization-and-compression @2

ONNX Runtime loads a model in the ONNX graph format, applies graph-level optimizations such as constant folding, operator fusion and layout transformation, then partitions the graph across execution providers — CPU, CUDA, TensorRT, OpenVINO, CoreML, DirectML, WebAssembly in a browser. That is the point of it: you export once from PyTorch and the same file runs on a server, a laptop, a phone and in a browser, so the training framework stops dictating the serving environment.

It also ships post-training quantization tooling for int8 dynamic and static quantization of those graphs. Reach for it for CPU and edge inference and for fleets with mixed hardware; for maximum throughput on data-centre GPUs a specialized engine wins. The usual friction is export: an unsupported or custom operator fails at conversion, before you ever get to run it.
### ONNX Runtime Mobile
**Short:** Trimmed ONNX Runtime build for iOS and Android: reduced binary size running prepacked ORT-format models.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/compiler-and-runtime-optimization @2

### ONNX Runtime — OpenVINO Execution Provider
**Short:** ORT plug-in that offloads supported subgraphs to OpenVINO, accelerating an existing ONNX pipeline on Intel hardware.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, inference/model-format-and-edge @2, inference/model-server @3

### ONNXRuntime
**Short:** Cross-platform ONNX inference runtime doing graph fusion, with CPU, GPU and NPU execution providers.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, inference/inference-engine @2, inference/model-format-and-edge @2, inference/model-server @3, inference/quantization-and-compression @3

### OpenAI API
**Short:** Hosted OpenAI model endpoint with decoding controls, automatic prompt caching, function calling and remote MCP support.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, caching/semantic-and-llm-cache @2, llm-apps/tool-use-and-mcp @3, ml-lifecycle/labeling-and-synthetic-data @3

An HTTP endpoint for chat and completion, embeddings, images, audio, and moderation, with streaming, function/tool calling, JSON-schema structured outputs, and a batch mode at a discount for work that tolerates latency. Decoding is controlled through `temperature`, `top_p`, presence and frequency penalties, `logit_bias`, and a best-effort `seed`.

Prompt caching applies automatically to a repeated prefix above a minimum length, which turns prompt layout into a cost decision: put the stable system instructions and tool definitions first and the varying user turn last, or you get no cache hits at all. Reach for it when you want frontier quality without hosting anything; the standing tradeoffs are per-token cost at volume, user data leaving your boundary, and models being retired on the vendor's schedule rather than yours.

### OpenAI Batch API
**Short:** OpenAI's asynchronous bulk inference endpoint: submit a JSONL job, collect results in 24h at a 50% discount.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/cloud-platform-and-cost @2

You upload a JSONL file whose every line carries a `custom_id` and the same request body you would send to the synchronous endpoint, create a batch over that file, poll its status, then download an output file whose lines carry the matching `custom_id` -- order is not guaranteed, so join on the id rather than on position. The bargain is a 24-hour completion window in exchange for half the per-token price and a separate, far larger rate-limit pool, so a big offline job stops competing with live traffic for quota. That fits embedding backfills, dataset labelling, offline evaluation and bulk summarisation, and fits nothing a user is waiting on. Failures are per line rather than per job, so plan to read the error file and resubmit a subset instead of treating a batch as all-or-nothing.

### OpenVINO
**Short:** Intel toolkit converting and optimizing models for Intel CPUs, iGPUs and NPUs, including INT8 quantization.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, inference/quantization-and-compression @2, inference/model-format-and-edge @2, inference/model-server @3

### Optimum
**Short:** Hugging Face umbrella for hardware-optimized inference: ONNX export, INT8 quantization and graph optimization backends.
**Kind:** tech
**Lang:** python
**Roles:** inference/model-format-and-edge @1, inference/quantization-and-compression @2, inference/compiler-and-runtime-optimization @2, applied-ml/nlp-and-text @3

It is a family of backend packages rather than one library - ONNX Runtime, Intel, NVIDIA, AWS Neuron and others - each exposing Transformers-shaped classes so an exported or quantized model keeps the `from_pretrained` and pipeline API the rest of your code already uses. The normal path is export the graph, apply graph-level optimizations such as operator fusion, then post-training quantization to INT8 for the target runtime.

Measure rather than assume: the speedup depends on the model, sequence length, batch size and hardware, and accuracy loss must be checked on your own evaluation set, not taken from a table. It is at its best for encoder-sized models and CPU or edge deployment; for serving large language models, vLLM or TensorRT-LLM are the targets built for that shape of workload.

### optimum-intel
**Short:** Hugging Face Optimum backend for Intel: exports and quantizes transformers to OpenVINO IR via optimum-cli.
**Kind:** tech
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, inference/quantization-and-compression @2, inference/model-format-and-edge @2

### OVMS on Kubernetes
**Short:** OpenVINO Model Server deployed via Helm chart or operator, exposing KServe-compatible inference endpoints.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/kubernetes-and-orchestration @2

### Phi-3 Mini
**Short:** Microsoft's 3.8B-parameter MIT-licensed small language model, sized to run on a laptop or an edge device.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

### Phi-3.5 Mini
**Short:** Microsoft's 3.8B-parameter MIT-licensed small language model, sized for on-device and edge inference.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1

### Phi-4
**Short:** Microsoft's 14B MIT-licensed small language model, strong on reasoning for its size and runnable on one GPU.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

### Qwen2.5
**Short:** Alibaba's Apache-2.0 open model family; the 0.5B-3B sizes are common on-device and edge choices.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @2

### Ray Serve
**Short:** Ray's model-serving layer: composable inference pipelines, autoscaling and multi-model multiplexing.
**Kind:** tech
**Lang:** python
**Roles:** inference/model-server @1, data-movement/batch-and-distributed-compute @3

A deployment is a Python class running as Ray actors, and deployments bind into a graph, so one request can fan through preprocessing, several models and a combiner with each stage scaled and resourced independently. It packs fractional GPUs, autoscales replicas on queue depth, and can multiplex many small models onto one replica, loading them on demand.

Reach for it when serving is a pipeline, when you host many models that are each too small to own a GPU, or when the rest of the workload already runs on Ray. A single model behind an HTTP endpoint does not need a distributed framework underneath it.

### rwkv.cpp
**Short:** C/C++ CPU inference runtime for RWKV models, exploiting their O(1) recurrent state for cheap long-context decoding.
**Kind:** tech
**Lang:** cpp
**Roles:** inference/inference-engine @1, inference/model-format-and-edge @2

### safetensors
**Short:** Tensor serialization format that loads weights zero-copy and cannot execute code, unlike pickled .bin/.pt files.
**Kind:** spec
**Lang:** *
**Roles:** inference/model-format-and-edge @1, security/supply-chain-and-runtime-security @2

### Seldon
**Short:** Kubernetes model-serving control plane: CRDs for inference graphs, ensembles, canary and autoscaling.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, ml-lifecycle/ml-platform-and-pipelines @2, platform-delivery/kubernetes-and-orchestration @2

You describe an inference graph as a Kubernetes custom resource -- a model, or a pipeline with transformers, a router doing A/B or a bandit, a combiner and an explainer -- and the controller creates the deployments, wiring, metrics and traffic split for you. It runs many runtimes underneath, including Triton, so it composes and routes rather than executing the model itself.

Reach for it when serving many models on Kubernetes should be declarative infrastructure reviewed like any other manifest. For a single model, a plain deployment of your model server is far less machinery, and the licence terms of newer releases are worth checking before you standardise on it.

### Seldon Core
**Short:** Kubernetes-native model serving: inference graphs of models and transformers as CRDs, with drift/outlier sidecars.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/kubernetes-and-orchestration @2, ml-lifecycle/drift-and-production-monitoring @3

### Sequoia
**Short:** Speculative-decoding method building a hardware-aware optimal token tree to raise accepted tokens per step.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1

### SGLang
**Short:** LLM serving runtime with RadixAttention KV-prefix reuse, fast constrained generation and speculative decoding.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, llm-apps/prompting-context-and-structured-output @2, caching/semantic-and-llm-cache @2

SGLang serves models with RadixAttention: cached KV blocks live in a radix tree keyed by token prefix, so any request sharing a prefix with an earlier one -- a long system prompt, a few-shot preamble, the growing history of an agent loop, the parallel branches of a self-consistency sample -- reuses that computation instead of recomputing it. On top of that sit continuous batching, tensor parallelism, quantised weights, and speculative decoding including EAGLE, where draft and target share the same radix KV tree. Its constrained-decoding path compiles a JSON schema or regex into a state machine that masks logits, so structured output costs far less than generate-and-retry. It exposes an OpenAI-compatible server plus a Python DSL for multi-call programs with explicit forking; reach for it when prompts share long prefixes or you need fast structured output at volume, and benchmark against vLLM rather than assuming, since prefix caching exists there too.

### SnapKV
**Short:** KV-cache compression method dropping low-attention tokens, with reference dynamic and static eviction implementations.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1, caching/semantic-and-llm-cache @2

### SparseGPT
**Short:** One-shot Hessian-based pruning that sparsifies an LLM to ~50% weights without retraining.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1

### SparseML
**Short:** Neural Magic's pruning, sparsification and quantization toolkit with recipe-driven sparse-transfer training.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, model-training/fine-tuning-and-peft @3

### TensorRT
**Short:** NVIDIA inference optimizer and runtime: layer fusion, kernel auto-tuning, FP16/INT8 calibration, serialized engines.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, inference/quantization-and-compression @2, inference/model-format-and-edge @3, inference/inference-engine @3

It compiles a graph - normally imported from ONNX - for one specific GPU: layers and tensors are fused, kernels are selected by actually timing candidate implementations on the device, and precision is lowered to FP16, INT8 or FP8 where calibration shows accuracy holds. The output is a serialized engine, and that artifact is the main operational constraint: it is tied to the GPU architecture, the TensorRT version and the shape profile it was built with, so it is rebuilt per deployment target rather than shipped like a checkpoint, and the build itself can take minutes.

Benchmark rather than quoting a speedup: the gain over eager PyTorch depends heavily on the model, the precision and whether the workload was ever kernel-bound. For transformer inference specifically, the TensorRT-LLM stack is the relevant entry point, since it adds paged KV cache and in-flight batching that generic graph optimization cannot provide.

### TensorRT-LLM
**Short:** NVIDIA LLM inference engine: compiled kernels, paged KV cache, FP8/INT8 quantization, speculative decoding.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, inference/quantization-and-compression @2, inference/compiler-and-runtime-optimization @2, inference/model-format-and-edge @3

You compile the model into a TensorRT engine ahead of time, targeting a specific GPU, batch size and sequence-length profile; the build fuses attention and GEMM kernels, wires in paged KV cache and in-flight batching, and applies FP8 or INT8 quantization from a calibration pass. In production it is usually served through Triton's TensorRT-LLM backend rather than on its own.

Reach for it when you own NVIDIA hardware and need the last increment of tokens per second per GPU. The costs are real: builds are slow, an engine is not portable across GPU generations or config, and support for a newly released architecture lags -- vLLM gives most of the throughput for a fraction of the operational effort.

### TF Serving
**Short:** TensorFlow's production model server: loads SavedModels, serves gRPC/REST, and handles versioning and A/B routing.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1

Point it at a directory whose subdirectories are numbered versions and it loads the highest automatically, keeping the outgoing version alive until in-flight requests drain; a serving config can instead pin explicit versions or attach labels such as stable and canary so traffic can be split by label. Both gRPC and REST predict endpoints are exposed from the same model.

Server-side batching is the setting that decides throughput: incoming requests are grouped up to a maximum batch size or timeout, trading a little latency for far better accelerator utilization. Reach for it when the models are already SavedModels and the deployment is TensorFlow end to end; a PyTorch or mixed-framework fleet is better served by a framework-agnostic server such as Triton, which also gives you one autoscaling and metrics story across models.

### TGI
**Short:** Hugging Face Text Generation Inference: a self-hosted, batched production serving stack for LLMs.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, inference/model-server @2

### TinyLlama
**Short:** 1.1B-parameter Apache-2.0 Llama-architecture model, small enough for edge devices and cheap experimentation.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

### torch.ao.quantization
**Short:** PyTorch's legacy PTQ/QAT API with eager and FX graph modes; new work is directed to torchao instead.
**Kind:** api
**Lang:** python
**Roles:** inference/quantization-and-compression @1

### torch.compile
**Short:** PyTorch 2 entry point that traces a model into a graph and JIT-compiles fused kernels for faster training and inference.
**Kind:** api
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, model-training/deep-learning-framework @2

### torch.cuda.make_graphed_callables
**Short:** PyTorch helper that captures a module's forward and backward into CUDA graphs to remove launch overhead.
**Kind:** api
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, gpu/kernel-programming @2

### torch.nn.utils.prune
**Short:** PyTorch's built-in pruning utilities; masks weights to zero without shrinking tensors, so speed needs more work.
**Kind:** api
**Lang:** python
**Roles:** inference/quantization-and-compression @1

### torchao
**Short:** PyTorch-native quantization and sparsity library: low-bit PTQ/QAT via quantize_ and the pt2e export path.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, model-training/fine-tuning-and-peft @3

torchao quantizes a model in place, swapping a linear layer's weights for a low-precision tensor subclass — int8 or int4 weight-only, dynamic activation quantization, float8 where the hardware supports it — so the model remains an ordinary module and `torch.compile` generates the fused kernels around it. It also covers quantization-aware training for the cases where post-training quantization loses too much accuracy, plus structured and semi-structured sparsity.

Being PyTorch-native, it composes with compile and distributed training in ways an external quantization library often does not. Which configuration is actually fast depends closely on your GPU generation, so measure rather than assuming lower precision always wins.

### TorchInductor
**Short:** PyTorch 2.x's default compiler backend: lowers the FX graph and emits fused Triton or C++ kernels.
**Kind:** tech
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, gpu/kernel-programming @3

### TorchServe
**Short:** PyTorch's model server: package a .mar, expose HTTP/gRPC inference endpoints, version and scale handlers.
**Kind:** tech
**Lang:** python
**Roles:** inference/model-server @1

Weights plus a handler class implementing preprocess, inference and postprocess are packaged into a `.mar` archive and registered with the server, which exposes separate inference, management and metrics ports. That split is the useful part: you can register a new model version, change the number of workers, or scale a single model up and down through the management API without restarting the process, and it hosts many models in one server with server-side dynamic batching.

The handler is where custom preprocessing and batching logic lives, which makes it flexible but also means the serving path is code you own and must test. Check the project's current maintenance status before adopting it for something new - for large language models the ecosystem has largely moved to vLLM, and for mixed-framework fleets to Triton.

### Transformer Engine
**Short:** NVIDIA library for FP8/FP4 transformer training and inference on Hopper and Blackwell, with drop-in te.Linear layers.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, gpu/gpu-portability-and-precision @2, model-training/distributed-training @3

FP8 is not merely a cast, because the format has too little dynamic range to hold activations and gradients directly. The library keeps per-tensor scaling factors, tracks a history of observed amax values and applies a delayed-scaling recipe, using E4M3 where precision matters and E5M2 where the wider exponent range does. It packages that behind drop-in modules such as `te.Linear`, `te.LayerNorm` and `te.TransformerLayer` plus an `fp8_autocast` context, so an existing PyTorch model can adopt it without a rewrite, and it brings fused attention kernels along with it. Reach for it on Hopper or Blackwell hardware where the low-precision tensor cores exist, since on older GPUs there is nothing to gain; NeMo and Megatron-LM already integrate it if you are training at that scale.

### Triton Inference Server
**Short:** NVIDIA's multi-framework model server: dynamic batching, model ensembles, versioning and TensorRT backends.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, inference/inference-engine @3, inference/compiler-and-runtime-optimization @3

Models live in a repository directory, each with a `config.pbtxt` declaring inputs, outputs, batching, and instance count, and the server exposes them over HTTP, gRPC, and an in-process C API. Backends cover TensorRT, ONNX Runtime, PyTorch, TensorFlow, Python, and LLM engines, so one server fronts a mixed fleet instead of one bespoke service per framework.

Throughput comes from two levers: dynamic batching queues arriving requests for a few milliseconds and fuses them into one GPU call, and multiple model instances per GPU overlap execution. Ensembles and business-logic scripting chain preprocess, model, and postprocess server-side so intermediate tensors never cross the network, and the model control API hot-swaps versions. Reach for it when GPU utilization and batching are what you are optimizing; a single small CPU model behind FastAPI does not need any of this.

### vLLM
**Short:** Self-hosted LLM serving engine: PagedAttention KV cache, continuous batching, prefix caching, LoRA serving.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, caching/semantic-and-llm-cache @2, inference/model-server @2, inference/quantization-and-compression @3, model-training/fine-tuning-and-peft @3

Its central idea is PagedAttention: the KV cache is held in fixed-size blocks and paged like virtual memory instead of one contiguous reservation per sequence, which removes the fragmentation that forced conservative batch sizes and lets sequences share blocks for a common prefix. Continuous batching then admits and retires requests at each decode step rather than waiting for a whole batch to finish, so a short request queued behind a long one is not stuck for its duration.

In practice it serves an OpenAI-compatible endpoint, does automatic prefix caching so a large shared system prompt is computed once, serves many LoRA adapters off one base model, and supports tensor and pipeline parallelism alongside quantized weights and KV cache. Reach for it when self-hosted throughput is the goal; a single-user local setup is better served by something lighter, and heavily latency-tuned proprietary stacks can still win on single-stream latency.

### vLLM SamplingParams
**Short:** vLLM's decoding config: temperature, top_p/top_k/min_p, repetition and presence penalties, seed.
**Kind:** api
**Lang:** python
**Roles:** inference/inference-engine @1, llm-apps/prompting-context-and-structured-output @3

### Wanda
**Short:** Pruning method scoring weights by magnitude times input activation norm, sparsifying an LLM without retraining.
**Kind:** concept
**Lang:** *
**Roles:** inference/quantization-and-compression @1

### XLA
**Short:** Google's linear-algebra graph compiler; fuses and lowers ML graphs to TPU, GPU and CPU code.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, model-training/deep-learning-framework @3
