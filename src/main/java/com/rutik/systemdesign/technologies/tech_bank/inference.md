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

### BitsAndBytes
**Short:** Load-time 4-bit (NF4/FP4) and 8-bit quantization for PyTorch models; the layer QLoRA fine-tuning relies on.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, model-training/fine-tuning-and-peft @2

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

### OpenAI Batch API
**Short:** OpenAI's asynchronous bulk inference endpoint: submit a JSONL job, collect results in 24h at a 50% discount.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/cloud-platform-and-cost @2

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

### TensorRT-LLM
**Short:** NVIDIA LLM inference engine: compiled kernels, paged KV cache, FP8/INT8 quantization, speculative decoding.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, inference/quantization-and-compression @2, inference/compiler-and-runtime-optimization @2, inference/model-format-and-edge @3

### TF Serving
**Short:** TensorFlow's production model server: loads SavedModels, serves gRPC/REST, and handles versioning and A/B routing.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1

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

### Transformer Engine
**Short:** NVIDIA library for FP8/FP4 transformer training and inference on Hopper and Blackwell, with drop-in te.Linear layers.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, gpu/gpu-portability-and-precision @2, model-training/distributed-training @3

### Triton Inference Server
**Short:** NVIDIA's multi-framework model server: dynamic batching, model ensembles, versioning and TensorRT backends.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, inference/inference-engine @3, inference/compiler-and-runtime-optimization @3

### vLLM
**Short:** Self-hosted LLM serving engine: PagedAttention KV cache, continuous batching, prefix caching, LoRA serving.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, caching/semantic-and-llm-cache @2, inference/model-server @2, inference/quantization-and-compression @3, model-training/fine-tuning-and-peft @3

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
