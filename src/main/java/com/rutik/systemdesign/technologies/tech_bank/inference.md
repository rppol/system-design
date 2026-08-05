# Inference & optimization — technology bank

<!-- tech-bank tier: inference -->

The 93 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Inference & optimization** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### Anthropic API
**Short:** Hosted endpoint for Claude models with native tool use, streaming and cache_control prompt caching.
**Kind:** model
**Lang:** *
**Roles:** inference/model-server @1, llm-apps/tool-use-and-mcp @2, caching/semantic-and-llm-cache @2, applied-ml/nlp-and-text @3

The Messages endpoint takes a system prompt and an alternating list of user and assistant turns, and returns content as a list of typed blocks, so a tool call arrives as structured data rather than as text to be parsed. Tools are declared as JSON schemas, the model emits a tool-use block, and you reply with a matching tool-result block in the next user turn. Streaming is server-sent events at block granularity rather than one flat token stream.

Prompt caching is explicit: you place a `cache_control` breakpoint at the end of the stable prefix, and everything before it is cached, so long system instructions and tool definitions are written once and read cheaply afterwards. Reach for it when you want frontier capability without hosting anything; the standing costs are per-token pricing at volume, data leaving your boundary, and a model roster that changes on the vendor's schedule.

### Anthropic APIs
**Short:** Hosted Claude model endpoints for chat, tool use and long-context prompting with no training code required.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, llm-apps/prompting-context-and-structured-output @3, llm-apps/tool-use-and-mcp @3

Around the core chat endpoint sits the rest of the surface: a batch mode for work that tolerates latency, token counting so a prompt can be sized before it is sent, file and document handling, and long context windows that change the shape of a request, since whole documents and codebases go into the prompt rather than through a retrieval layer. The same models are also offered through cloud marketplaces, which matters when data residency or an existing cloud contract decides where inference may run.

Reach for it when the work is text and reasoning and you want no training or serving infrastructure at all. Design around the two things you do not control: rate limits, which are per-organization and shape how much concurrency you can actually use, and model retirement, which means pinning a model identifier and keeping an evaluation set you can re-run whenever you have to move.

### AOTAutograd
**Short:** The torch.compile stage that traces forward and backward together into one ATen graph, then partitions it back into two.
**Kind:** tech
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, model-training/deep-learning-framework @2

TorchDynamo hands it an FX graph of the forward only. AOTAutograd re-traces that graph ahead of time to build a joint forward-and-backward graph lowered to ATen operators, then splits it into a forward graph and a backward graph for Inductor to compile. This is why `torch.compile` speeds up training and not just inference, and why the first backward pass is slow as well as the first forward.

The part worth knowing operationally is the partitioner: it decides which intermediate values the forward saves and which the backward recomputes, which is automatic, targeted activation checkpointing. That is the reason compiled peak memory can differ from eager in either direction, and the reason a memory comparison between eager and compiled is not a like-for-like measurement.

### AOTInductor
**Short:** Compiles an ExportedProgram into a self-contained .pt2 archive of precompiled kernels that runs with no Python at runtime.
**Kind:** tech
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, inference/model-format-and-edge @2, inference/model-server @3

It is the ahead-of-time half of the PyTorch 2 compiler stack: where `torch.compile` is a JIT that compiles on first call and needs a live Python interpreter, AOTInductor takes the `ExportedProgram` produced by `torch.export` and emits a packaged artifact with the kernels already built. `torch._inductor.aoti_compile_and_package` writes it and `torch._inductor.aoti_load_package` loads it back, and the same archive is what NVIDIA Triton's `torch_aoti` platform consumes.

Reach for it when a warm-up cliff or a Python runtime is unacceptable — a latency SLO, a C++ service, an edge target. The trap is that everything `torch.export` refuses, AOTInductor never sees: get the model correct and fast under `torch.compile` first, because an export failure is much harder to diagnose than a graph break.

### AutoAWQ
**Short:** Toolkit that applies AWQ 4-bit activation-aware weight quantization to Hugging Face LLMs.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1

The toolkit runs the AWQ procedure over a Hugging Face checkpoint: a small calibration set is pushed through the model to record per-input-channel activation magnitudes, the channels carrying the largest activations are protected by folding a scale into the preceding operation, and the weights are then rounded to four bits and packed with per-group scales and zero points. What comes out is an ordinary checkpoint directory that a serving engine loads directly.

Reach for it to cut weight memory roughly fourfold with little accuracy loss and no gradient computation, since quantizing a mid-sized model is minutes on a single GPU. Two practical notes: calibration data should resemble the traffic you will serve, and the project's own kernels have largely been overtaken, with vLLM and llm-compressor now the maintained path for producing and serving AWQ weights.

### AutoGPTQ
**Short:** GPTQ post-training quantization for Hugging Face transformer checkpoints, producing 4-bit GPU models.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, inference/inference-engine @3

It wrapped the GPTQ algorithm for Hugging Face models: layer by layer, columns of the weight matrix are quantized in turn while the error is compensated into the columns not yet processed, using an inverse-Hessian estimate built from a small calibration set. The package handled the packing format, the CUDA and Triton dequantize-and-matmul kernels, and the loading path that made a four-bit checkpoint behave like an ordinary model to the rest of the code.

It is no longer the maintained implementation. GPTQModel took over as the transformers backend and llm-compressor covers the same ground with a wider set of recipes, so treat a dependency on it as a signal to migrate. Existing checkpoints still load, but kernels for newer GPU architectures and support for recent model families land in the successors rather than here.

### AWQ
**Short:** Activation-aware Weight Quantization: 4-bit post-training quantization protecting the ~1% salient channels, no backprop.
**Kind:** concept
**Lang:** *
**Roles:** inference/quantization-and-compression @1, inference/inference-engine @3

The observation behind it is that weight importance is decided by the activations the weights meet, not by weight magnitude: a small fraction of input channels carry consistently large activations, and quantization error in those channels dominates the output error. Rather than keeping them in higher precision, which would break the packed layout, the method scales those channels up before rounding and folds the inverse scale into the preceding layer, so the arithmetic is unchanged and the error is pushed away from what matters.

It needs only forward passes over a calibration set, with no backpropagation, and it tends to generalize better across domains than methods fitted more tightly to their calibration data. It is weight-only, so the win is memory footprint and decode bandwidth rather than prefill arithmetic. Compare it against GPTQ on your own evaluation set rather than on published tables, because which one wins varies by model.

### Azure OpenAI
**Short:** Microsoft-hosted OpenAI model endpoints with Azure identity, networking, quota and compliance controls.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/cloud-platform-and-cost @2, llm-apps/llm-gateway-and-routing @3

The unit of use is a deployment: you place a named deployment of a model version in a region, and requests carry the deployment name rather than the model name, which is what lets you pin a version and swap it without touching client code. Capacity is either provisioned throughput, reserved and predictable, or pay-as-you-go tokens per minute, and both are regional quota you request rather than an elastic pool you draw from.

The reason to choose it over the vendor's own endpoint is the surrounding platform: directory-based identity and managed identities instead of API keys, private endpoints so traffic never crosses the public internet, customer-managed keys, and configurable content filtering. The costs are that model availability lags by region, that quota is a real operational obstacle, and that the API version is an explicit parameter you must track.

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

A model ships as an `.mlpackage` holding the program representation, its weights and metadata, which Xcode compiles into a runtime bundle at build time. At execution the framework partitions the graph across the Neural Engine, the GPU and the CPU according to the compute-units preference and what each unit supports, and it generates a typed Swift class so inputs and outputs are checked rather than passed as dictionaries. Weight compression, including palettization, linear quantization and sparsity, is part of the format itself.

It is the path for shipping a model inside an iOS or macOS application: inference is on-device, so there is no per-request cost, no network dependency and no user data leaving the phone, and the Neural Engine runs it at very low power. The frictions are conversion, since an unsupported operation blocks the export, and opacity, because the placement decision that determines performance is a heuristic you influence rather than control.

### coremltools
**Short:** Apple's Python toolchain converting PyTorch/ONNX models to Core ML for on-device inference on iOS and macOS.
**Kind:** tech
**Lang:** python
**Roles:** inference/model-format-and-edge @1, inference/quantization-and-compression @3

Conversion runs through a single convert call on a traced or exported PyTorch model or a TensorFlow graph, lowering it into an intermediate representation and then into the Core ML program, with input types and shapes declared explicitly, including enumerated or ranged shapes when an input is not fixed. The same package carries the optimization toolkit: post-training palettization, linear weight quantization and pruning, plus training-time variants for when the post-training loss is too large.

Expect the conversion step to be where the work is. An unsupported operator stops the export, and the fix is a composite operator assembled from available primitives or a change to the model itself. Verify twice: that numeric outputs match the source framework within tolerance, and that the placement is what you intended, since one unsupported operation can push an entire subgraph off the Neural Engine onto the CPU.

### ctranslate2
**Short:** Fast C++ inference engine for transformer seq2seq and speech models with INT8/FP16 execution, driven from Python.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1, inference/quantization-and-compression @3, applied-ml/nlp-and-text @3

It is a purpose-built C++ engine rather than a graph runtime. Models are converted offline into its own format, and the executor uses its own fused kernels, layer fusion and weight quantization to INT8, INT16 or FP16, chosen per device at load time. It runs on CPU with the appropriate vector instructions and on GPU, supports batched decoding with dynamic shapes, and reuses pooled memory so repeated calls do not reallocate.

Its natural workloads are encoder-decoder translation and speech models, and it is the engine underneath faster-whisper and several translation services, where CPU throughput and low memory matter more than serving many concurrent chat users. The tradeoff is coverage: an architecture has to be explicitly supported by a converter, so a newly released model family is not simply loaded and run.

### DeepSparse
**Short:** Neural Magic's CPU inference runtime exploiting weight sparsity and quantization for GPU-class throughput.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1, inference/quantization-and-compression @2, inference/compiler-and-runtime-optimization @3

Its idea was that a CPU's advantage is cache rather than bandwidth. Instead of sweeping the whole network layer by layer, it executed depth-wise over tiles of the network that stay resident in cache, and combined that with unstructured weight sparsity and INT8 so the skipped multiplies were genuinely skipped rather than multiplied by zero. Models arrived pruned and quantized through SparseML recipes and ran with no GPU involved at all.

It mattered where an accelerator was unavailable or uneconomic, on edge boxes, existing CPU fleets and cost-sensitive batch inference. The project's centre of gravity has since moved, with Neural Magic's work now aimed at vLLM and llm-compressor, so treat this as the older path and check its maintenance status before adopting it for anything new.

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

Ordinary speculative decoding runs a smaller model to draft tokens. This method drafts inside the target model's own feature space instead: a single lightweight autoregressive head takes the target's last-layer hidden states, shifted by one position and concatenated with the embedding of the token actually sampled, and predicts the next feature, from which the target's own output head produces a token. Feeding the sampled token back is what resolves the ambiguity that makes plain feature prediction unreliable.

The draft head is small and trained against the target model's outputs, so it must be trained per checkpoint, but it needs no separate serving path and no second model kept in sync. Verification stays exact, so output quality is unchanged. Reach for it when decode latency dominates for a stable set of models; if the served checkpoint changes every few weeks, the retraining is the cost to weigh.

### EAGLE-2
**Short:** Speculative decoding using feature-level autoregressive draft layers, with reference implementations.
**Kind:** tech
**Lang:** python
**Roles:** inference/inference-engine @1, inference/compiler-and-runtime-optimization @3

The change is the draft tree. The first version verified a fixed tree of candidate continuations, the same shape at every step, which wastes budget when the draft is confident and truncates it when the draft is unsure. This version builds the tree dynamically instead: the draft head's own confidence scores approximate acceptance probability well enough to expand the promising branches and prune the rest, so the same verification budget yields more accepted tokens.

Nothing about the target model or the verification changes, so the output remains identical to plain sampling and the whole gain is throughput per step. It is worth the extra machinery when traffic is decode-bound and prompts vary enough that a static tree is a poor fit. The cost lands in the serving engine, which needs a more complex scheduler, and that is why support is engine-by-engine rather than universal.

### EAGLE-3
**Short:** Speculative decoding whose draft head autoregresses over the target model's features, raising accepted tokens per step.
**Kind:** concept
**Lang:** *
**Roles:** inference/inference-engine @1

Two changes distinguish it. The draft head stops predicting the target's final-layer feature and instead consumes a fusion of low, middle and high layer representations, which removes the constraint that the draft must reproduce one specific hidden state and gives it richer signal to work from. Training also adds a simulated multi-step decoding loop, so the head is fitted under the error accumulation it will actually face rather than only on one-step-ahead targets.

The effect is more accepted tokens per verification pass, which is the number that decides the speedup, and the gains scale with the amount of draft training data rather than saturating early. As in the earlier versions the head is per-checkpoint and verification is exact, so quality is unchanged; the decision is whether the training effort and the engine support are worth the latency you buy.

### ExecuTorch
**Short:** PyTorch's on-device runtime and .pte export format for running models on phones and embedded hardware.
**Kind:** tech
**Lang:** python
**Roles:** inference/model-format-and-edge @1, inference/inference-engine @3

The pipeline is export-first: `torch.export` captures the model as a graph, that graph is lowered to a small core operator set, partitioned so supported subgraphs are delegated to a backend, and serialized into a `.pte` file. At run time a small C++ runtime loads the file and executes it with no Python and no dynamic dispatch, with memory planned ahead of time so allocation is static, which is what makes it viable on a microcontroller as well as a phone.

Backends cover XNNPACK for CPU, Core ML and Metal on Apple hardware, Vulkan, and vendor NPUs. Reach for it when a model must run inside a mobile or embedded application and the training side is PyTorch. It is younger than the alternatives, so expect gaps in operator and backend coverage, and expect the export step rather than the runtime to be where the work goes.

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

It is built for the shapes a serving engine actually has. The KV cache is paged, so a sequence's keys and values sit in scattered fixed-size blocks described by a block table; requests in a batch have different lengths and are held as ragged tensors rather than padded; and requests sharing a prefix share the underlying blocks. Its kernels take those structures directly, and it splits work into a plan step that inspects the batch and schedules it, and a run step reused across layers.

It also generates kernels for a specific page size, head configuration, mask and attention variant rather than shipping one generic path, compiling and caching on first use. That is why vLLM and SGLang adopt it as an attention backend: prefill, decode and prefix-shared attention each get a specialized kernel. Direct use is rare, since you normally get it by selecting a backend inside the engine.

### Gemma 2 2B
**Short:** Google's small open-weight model (2B and 9B) sized to run on a laptop or edge device under the Gemma terms of use.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

The small members of this family were not simply trained from scratch at that size; they were trained with knowledge distillation from a larger teacher, which is why they land above what their parameter count would suggest. The architecture interleaves local sliding-window attention with global attention layers to keep the KV cache small, and uses grouped-query attention and logit soft-capping. Weights are published openly, and at four bits the model fits comfortably on a phone or a laptop.

Reach for it when inference must be local, for classification, extraction, summarization or an on-device assistant, or when a small model is a cheap first stage in front of a larger one. It is released under Gemma terms of use rather than a standard open-source licence, so read them before shipping a product, and calibrate expectations: multi-step reasoning and long-tail factual recall are where a model this size gives way.

### GGUF
**Short:** Single-file model container used by llama.cpp: weights, tokenizer and metadata, usually quantized for local inference.
**Kind:** spec
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/quantization-and-compression @2, inference/inference-engine @3

The file opens with a header of key-value metadata covering architecture, hyperparameters, the full tokenizer with its vocabulary and merges, the chat template and the quantization types, followed by tensor descriptors and then the tensor data, aligned so the whole file can be memory-mapped and the weights used in place. Nothing is executed on load, unlike a pickled checkpoint, and because the tokenizer and template travel inside the file there is no accompanying directory of JSON to keep in sync.

It is the format llama.cpp and everything built on it consume, which is why a local model is a single file to download and run. The tradeoffs are that it is a distribution and inference format rather than a training one, so weights are converted into it rather than produced in it, and that every new architecture needs explicit support in both the converter and the runtime before any file can exist.

### GGUF quantization comparison
**Short:** The perplexity-versus-size tradeoff table across GGUF quant levels, used to pick a local-model quantization.
**Kind:** concept
**Lang:** *
**Roles:** inference/quantization-and-compression @1, ml-lifecycle/evaluation-and-benchmarks @2, inference/model-format-and-edge @2

The comparison is the same model quantized every available way, plotted as file size against a quality proxy, usually perplexity on a held-out corpus and sometimes a small benchmark suite. The shape of the curve is what matters more than any individual row: from eight bits down to roughly four the loss is small, and below four it turns steeply, so all the interesting decisions sit inside a narrow band. Within a bit width, the variants that spend extra bits on sensitive tensors beat the uniform ones at a modest size cost.

Use it to pick the largest model that fits rather than the least quantized one, because a larger model at four bits generally beats a smaller model at eight in the same memory. Two cautions: perplexity is a weak proxy for instruction following and code generation, so confirm on your own task, and remember the KV cache has to fit too, which at long context can rival the weights.

### GPTQ
**Short:** Weight-only 3/4-bit post-training quantization using approximate second-order error compensation; run via GPTQModel.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1

The method descends from optimal brain quantization: a weight matrix is quantized column by column, and after each column the remaining columns are updated to absorb the error just introduced, using an inverse Hessian approximated from the layer's inputs on a small calibration set. Working layer by layer against that layer's own inputs keeps the problem tractable, and quantizing all rows in the same column order lets the Hessian be factorized once and reused, which is why a large model takes hours rather than days.

It is weight-only, typically to three or four bits with per-group scales, so it buys memory footprint and decode bandwidth rather than prefill arithmetic. Calibration data matters more than people expect, since a few hundred sequences unlike your workload produce a model that degrades on it. Run it through GPTQModel or llm-compressor; the original packaging is no longer the maintained path.

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

The observation is that attention is concentrated: over a long generation a small set of positions receives most of the attention mass from all later queries, and the rest contribute almost nothing. The policy keeps a fixed budget of cache entries split between those heavy hitters, selected by accumulated attention score, and a window of the most recent tokens, evicting everything else greedily as decoding proceeds. Decode memory then stays bounded instead of growing with sequence length.

It is an approximation, unlike a paged or losslessly compressed cache, so the risk is a token that mattered being evicted before it was needed, which shows up on retrieval-style tasks that reach far back into the context. Reach for it when decode memory is the binding constraint and the workload is generation-heavy; where exactness matters, prefix caching and paged attention cut cost without changing the result.

### HuggingFace Inference API
**Short:** HuggingFace's hosted endpoint service for running any Hub model behind an HTTP API without managing GPUs.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, llm-apps/llm-gateway-and-routing @2

There are two shapes behind the name. The serverless surface routes a request for a Hub model to a provider, either the Hub's own capacity or a third-party inference partner, with no instance to create, which makes it the fastest way to call a model but leaves cold starts, capacity and provider differences outside your control. Dedicated inference endpoints are the other shape: you pick a model, a cloud region and an instance type and get an autoscaling deployment of your own, optionally scaling to zero.

Reach for the serverless path for prototypes, demos and low-volume calls, and for the dedicated path when a model must be always available, private to your network, or built on a custom container and handler. At sustained volume both cost more per token than running the same model yourself, and the serverless one is convenience rather than a latency guarantee.

### HuggingFace TGI
**Short:** Text Generation Inference: Hugging Face's containerized LLM serving runtime with continuous batching and streaming.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, inference/model-server @2

The process splits in two: a Rust router owning the HTTP and gRPC surface, request validation, tokenization and the continuous-batching scheduler, and Python shards holding the model and executing forward passes, one per tensor-parallel rank. Keeping the scheduler out of Python is why it holds throughput under high concurrency. Tokens stream back over server-sent events as they are produced, and grammar or JSON-schema constraints are applied during sampling rather than by retrying.

It is packaged as a container that takes a Hub model id, which makes it a short path from checkpoint to Kubernetes endpoint with metrics and health probes already wired. Weigh it against vLLM and SGLang, which have moved faster on KV-cache reuse and speculative decoding. Check the licence of the version you intend to run as well, since the project's terms changed once and were later reverted, and older commentary is misleading on the point.

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

The recipe changes training so a model can check itself. Layer dropout is applied at a rate that increases with depth, and an early-exit loss trains the shared output head to produce usable predictions from intermediate layers rather than only from the last one. At inference the first few layers act as the draft, generating tokens cheaply, and the full stack verifies them in one pass, reusing the same weights and the same KV cache because the draft is a prefix of the model.

The appeal over ordinary speculative decoding is that there is no second model and no extra memory: one set of weights serves both roles, which matters when the deployment is memory-bound. The cost is that the model must be trained or fine-tuned this way, so it cannot be applied to an arbitrary checkpoint, and the accuracy of the early exit is what decides how many drafted tokens survive verification.

### LLaMA 3.2 1B
**Short:** Meta's 1B-parameter small language model, sized for on-device and edge inference.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

The small models in this generation were not trained from scratch at that size: they were produced by pruning a larger sibling and then recovering quality through knowledge distillation from bigger models in the same family, which is why they hold up better than the parameter count suggests. The architecture is the usual decoder with grouped-query attention and a long context window, and the tokenizer and chat template are shared with the rest of the family.

Reach for it for on-device assistants, summarization and rewriting, tool routing, and as a draft model for speculative decoding against a larger sibling, since the shared tokenizer makes that pairing straightforward. Quantized to four bits it fits in a phone's memory. It is released under the Llama community licence rather than a standard open-source one, and at this size expect competent instruction following alongside weak multi-step reasoning and thin factual recall.

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

The runtime is portable C++ with no Python and no heavy dependencies, so an Android build is the NDK toolchain compiling the same sources into a shared library plus a JNI layer exposing model load, tokenize and generate to Kotlin or Java. Kernels use NEON and, on newer cores, the dot-product and matrix instructions, and a Vulkan or OpenCL backend can offload to the phone's GPU, though on many devices the CPU path with a small quantized model is competitive.

The constraints are the device's. Memory decides which model fits once the KV cache is counted, and sustained generation is thermally limited, so a phone throttles partway through a long response in a way a laptop does not. Reach for it when inference must be fully offline or private; for a production application, MediaPipe's LLM API or ExecuTorch offer more integration with vendor NPUs.

### llama.cpp quantize
**Short:** llama.cpp CLI converting an FP16 GGUF checkpoint to a lower-bit GGUF variant from Q2 up to Q8.
**Kind:** tech
**Lang:** cpp
**Roles:** inference/quantization-and-compression @1, inference/model-format-and-edge @2

The tool takes a GGUF converted at full or half precision and rewrites each tensor in a chosen scheme, from two bits up to eight and the k-quant variants in between, writing a new self-contained file. An importance matrix, collected beforehand by running calibration text through the model and recording per-channel activation statistics, can be supplied so that bits are allocated where the model is most sensitive, which is what makes the very low bit widths usable at all.

Run it once per target size from the original conversion and keep that source file, because quantizing again from an already-quantized model compounds the error. Choose the level by what fits in memory alongside the KV cache, and verify on your own prompts rather than on a perplexity table, since instruction following and code generation degrade noticeably before perplexity moves much.

### llm-compressor
**Short:** vLLM-project library producing quantized checkpoints - AWQ, GPTQ, FP8, sparsity - via recipes run by oneshot().
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, inference/inference-engine @3

Work is expressed as a recipe of modifiers, covering GPTQ, AWQ, SmoothQuant, plain quantization and sparsity, applied by a single `oneshot` call over a calibration dataset or by a training loop when the recipe needs one. The output is saved in the compressed-tensors format, which records the scheme, group size and scales inside the checkpoint itself, so a serving engine loads it without being told separately how it was quantized.

It is the vLLM project's own quantization path, which is the main reason to prefer it: the formats it emits are the ones that engine has fast kernels for, including weight-only INT4, INT8 and FP8 with static or dynamic activation scales, and 2:4 sparsity. Pick the scheme by hardware rather than by reputation, since FP8 needs Hopper or later while INT8 works across a much wider range of cards.

### LM Studio
**Short:** Desktop app for downloading and chatting with local GGUF models, exposing an OpenAI-compatible local server.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1, inference/model-format-and-edge @2

It bundles the pieces a local model needs behind a desktop interface: a browser over Hub repositories that flags which quantizations fit the machine's memory, a downloader, a chat window exposing the sampling parameters and system prompt, and a local server speaking the OpenAI chat and completions API on a port. Underneath are llama.cpp and, on Apple silicon, MLX, with a GPU-offload control deciding how many layers leave the CPU.

Reach for it to try models without assembling a toolchain, and as a stand-in endpoint while developing against a hosted provider, since pointing an existing client at localhost is usually a one-line change. It is a desktop application rather than a serving stack: single machine, closed source, and without the batching a multi-user deployment needs, which is where a headless daemon or a real engine takes over.

### MediaPipe LLM
**Short:** Google's MediaPipe LLM Inference API running small TFLite/LiteRT models fully on-device on Android, iOS and web.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/inference-engine @3

The API packages an on-device generation loop behind a small platform surface: a bundle file carries the converted weights, tokenizer and metadata, and the runtime handles the prefill and decode loop, sampling parameters and streaming callbacks on Android, iOS and the web. Execution goes through the GPU delegate where the device has one and falls back to CPU otherwise, and LoRA adapters can be loaded over a base model so one set of base weights serves several behaviours.

Reach for it when a mobile or web application needs generation with no server and the model is one of the small supported families, since conversion is per architecture rather than general. The device sets the ceiling in every direction: memory decides the model size, the context window is short by server standards, and sustained generation is throttled by heat well before it is throttled by compute.

### Medusa (speculative decoding)
**Short:** Speculative decoding without a draft model: extra prediction heads propose several next tokens and tree attention verifies them in one pass.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1

It bolts several extra prediction heads onto an existing model so a single forward pass proposes tokens for the next few positions at once, then tree attention verifies many candidate continuations together instead of one sequence at a time. Because the heads live on the base model there is no second model to load, schedule or keep in sync - the usual operational cost of draft-and-verify speculative decoding disappears.

The price is training: the heads are fitted to one specific base model, so every checkpoint you serve needs its own. Reach for it when a single model dominates your traffic and decode latency is the bottleneck; prefer a draft model when the served checkpoint changes often. Unrelated to the `Medusa` Cassandra backup tool, which shares only the name.

### MLC-LLM
**Short:** Compiles and runs LLMs on phones, browsers and laptops by generating device-specific kernels via TVM.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-format-and-edge @1, inference/inference-engine @2, inference/compiler-and-runtime-optimization @3

MLC-LLM compiles a model ahead of time through TVM into kernels for whichever backend the target device has — Metal, Vulkan, WebGPU, CUDA — so inference runs natively on a phone or inside a browser tab with no Python runtime and no server call. Weights are converted and quantized into its own format during that build step, which is why deployment here is a compile pipeline rather than an install command.

Reach for it when the model must run on the user's device for privacy, offline use or per-request cost. The price is that every target platform is another build, and shipping a new model means shipping a new artifact.

### MLflow Models
**Short:** MLflow's model packaging format: a directory with an MLmodel manifest naming flavours, signature and environment.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-format-and-edge @1, ml-lifecycle/experiment-tracking-and-tuning @2

The manifest is the whole idea. A logged model is a directory containing the weights in
whatever format the training framework prefers, plus an `MLmodel` YAML file naming one or more
flavours, a type signature for inputs and outputs, and three renderings of the dependency set.
A consumer that has never seen the training code reads that one file and knows how to load the
object, what shape to feed it, and what environment to build.

Every model carries the universal `python_function` flavour alongside its native one, so a
generic `predict` call works regardless of framework, which is what serving runtimes, Spark
UDFs and container builders all target. Reach for it whenever a model crosses a team boundary.
Its honest limit is that dependency capture pins the Python distributions it saw imported and
cannot see CUDA, system libraries or the base image, so pair the directory with a pinned
container digest rather than treating it as a complete environment.

### Model Navigator
**Short:** NVIDIA tool converting a PyTorch/TF/ONNX model to optimized formats such as TensorRT and verifying accuracy.
**Kind:** tech
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, inference/model-format-and-edge @2, inference/model-server @3, ml-lifecycle/evaluation-and-benchmarks @3

Rather than making you pick a conversion path, it takes a model and a dataloader and tries the paths in parallel, across TorchScript, ONNX at several opset levels and TensorRT at several precisions, then verifies each result against the source within a tolerance you set and profiles the ones that survive. What comes back is a package of working formats with their measured latency and throughput, and the failures reported with a reason rather than silently dropped.

That is the value: the usual manual loop of export, hit an unsupported operator, adjust, re-export, then discover the fast variant is numerically wrong, is automated. Reach for it when preparing a model for Triton or TensorRT deployment. It is a build-time tool tied to the NVIDIA stack, and the artifacts it produces, rather than the tool itself, are what you deploy.

### Model-control sidecars
**Short:** A sidecar or init job calling Triton's model-control API, so which models a replica loads is decoupled from its image.
**Kind:** concept
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/kubernetes-and-orchestration @2

A model server that can load and unload models at run time turns model membership into an API call rather than a property of the container image. The pattern puts that call in a separate process: an init container loads the set a replica should serve before it reports ready, or a sidecar watches a configuration source and issues load and unload requests as it changes, while the server container stays a stock image with no per-fleet customization.

The payoff is that adding a model, rolling one back or rebalancing which replica serves what stops requiring a redeploy, which matters most for fleets of many small models. The costs are that readiness must genuinely reflect what is loaded, that memory is now driven by something outside the pod spec so a load can push a replica into an out-of-memory kill, and that two sources of truth can drift apart.

### model_analyzer
**Short:** Triton tool sweeping instance-group and batching configs, reporting the throughput/latency/memory Pareto frontier.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, observability/profiling-and-performance @2, platform-delivery/cloud-platform-and-cost @3

It searches the configuration space empirically. For a model in a repository it generates candidate configurations, varying instance count per GPU, maximum batch size and dynamic-batching queue delay, runs each under a load generator at a series of concurrency levels, and records throughput, latency percentiles and GPU memory. The output is a report with the Pareto frontier and a recommended configuration file, and a quick mode narrows the sweep when the full one is too slow.

Reach for it before deciding capacity, because the answers are not guessable: more instances per GPU help until memory or SM contention reverses it, and a longer queue delay raises throughput while pushing out the tail. Give it a load profile that resembles production, since a sweep run at the wrong concurrency cheerfully optimizes for traffic you do not have.

### NNCF
**Short:** Intel's compression framework for OpenVINO: post-training and accuracy-aware quantization, pruning, weight compression.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, inference/compiler-and-runtime-optimization @3

It is the compression side of the OpenVINO toolchain, applied either to an OpenVINO model or upstream to PyTorch or ONNX. A quantize call runs post-training quantization over a calibration dataset, inserting fake-quantize operations the runtime later folds into integer kernels, while a separate weight-compression path handles the INT8 and INT4 weight-only modes used for language models, where activations stay in floating point and the win is memory. Accuracy-aware modes revert the layers that cost the most accuracy until a declared tolerance is met.

Beyond quantization it covers structured pruning, filter sparsity and quantization-aware training for cases post-training methods cannot reach. Reach for it when the target is Intel hardware, because the schemes it emits are the ones OpenVINO has fast kernels for, and a generic INT8 export is not the same thing as one shaped for the runtime that will execute it.

### NPU
**Short:** On-die neural accelerator (e.g. Core Ultra AI Boost) for sustained low-power INT8 inference beside the CPU and GPU.
**Kind:** concept
**Lang:** *
**Roles:** inference/model-format-and-edge @1, runtime-systems/memory-processes-and-os @3

The unit is a fixed-function matrix and vector engine with its own local memory, sitting on the same die as the CPU and GPU and sharing system memory. Its design point is sustained efficiency rather than peak throughput: it runs quantized integer models at a fraction of the power the GPU would draw, and without contending for the GPU that the display and other applications need. Work reaches it through a vendor runtime and a compiled model rather than through a general kernel language.

Reach for it for continuously running on-device inference, such as background transcription, camera effects, wake-word detection and always-on vision, where battery and thermals are the real constraint. The limits follow from the design: the operator set is narrow so an unsupported layer pushes its subgraph back to CPU, the model usually has to be quantized to integer precision, and the toolchain is vendor-specific rather than portable.

### NVIDIA Triton
**Short:** NVIDIA's multi-framework model server: dynamic batching, model ensembles, versioning and TensorRT backends.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, inference/inference-engine @3, ml-lifecycle/ml-platform-and-pipelines @3, platform-delivery/kubernetes-and-orchestration @3, inference/compiler-and-runtime-optimization @3

Models live in a repository directory, each with a `config.pbtxt` declaring inputs, outputs, batching, and instance count, and the server exposes them over HTTP, gRPC, and an in-process C API. Backends cover TensorRT, ONNX Runtime, PyTorch, TensorFlow, Python, and LLM engines, so one server fronts a mixed fleet instead of one bespoke service per framework.

Throughput comes from two levers: dynamic batching queues arriving requests for a few milliseconds and fuses them into one GPU call, and multiple model instances per GPU overlap execution. Ensembles and business-logic scripting chain preprocess, model, and postprocess server-side so intermediate tensors never cross the network, and the model control API hot-swaps versions. Reach for it when GPU utilization and batching are what you are optimizing; a single small CPU model behind FastAPI does not need any of this. Unrelated to OpenAI Triton, the GPU kernel DSL, which shares only the name.

### NVIDIA Triton OpenVINO backend
**Short:** Triton backend that executes models through Intel OpenVINO so CPU models serve inside the same Triton fleet.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, inference/compiler-and-runtime-optimization @2, inference/model-format-and-edge @3

The backend embeds the OpenVINO runtime inside the server process, so a model directory holding IR files, or an ONNX file it converts on load, is executed on the CPU by the same server that runs TensorRT and PyTorch models on the GPUs. Plugin parameters in the model configuration map onto OpenVINO's own settings, including the performance hint, the number of streams and the inference thread count, which is how you stop CPU models from oversubscribing the cores the rest of the server needs.

The point is fleet uniformity: one API, one metrics surface and one deployment story for models whose ideal hardware differs. Reach for it when small CPU-appropriate models sit alongside GPU models and belong in the same ensemble, such as preprocessing steps, classifiers and embedding models. If the whole workload is CPU-bound, OpenVINO Model Server on its own is considerably less machinery.

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

A model is a protobuf message: a graph of nodes referencing operators by name and domain, typed tensors on the edges, initializers holding the weights, and an opset version pinning what each operator means. That versioning is the load-bearing part, because an operator's semantics are frozen per opset, so a consumer written against one opset can still read a model exported at that opset years later. Extensions cover quantization, control flow, sequences and custom operator domains.

The value is decoupling: export once from PyTorch and the same file feeds ONNX Runtime, TensorRT, OpenVINO, Core ML conversion and browser runtimes, so the training framework stops dictating the deployment. The friction is always the export itself. Dynamic control flow, an unsupported operator, or a shape the tracer bakes in as a constant will either fail at conversion or yield a graph that is subtly wrong for other inputs, so verify numerics afterwards.

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

The size reduction comes from two decisions. Models are pre-converted into a flatbuffers-based runtime format with the graph optimizations already applied, so the runtime ships no optimizer at all, and the runtime itself is built with operator reduction: the build reads the models you intend to ship and compiles in only the operator kernels and data types they actually use, discarding a kernel registry that would otherwise dominate the binary.

Reach for it when application size is a genuine constraint on iOS or Android and the model set is fixed at build time. That last condition is the catch, since adding a model later can require a new runtime build, which does not fit an application that downloads models at run time. The execution providers available are the mobile ones, covering XNNPACK, Core ML and NNAPI, rather than the server set.

### ONNX Runtime — OpenVINO Execution Provider
**Short:** ORT plug-in that offloads supported subgraphs to OpenVINO, accelerating an existing ONNX pipeline on Intel hardware.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, inference/model-format-and-edge @2, inference/model-server @3

Execution providers let ONNX Runtime hand parts of a graph to another backend. This one asks OpenVINO which nodes it can take, gives it those subgraphs to compile and execute, and keeps the remainder on the default CPU provider, stitching the results back together at the boundaries. The device is chosen through the provider's options, covering CPU, GPU, NPU and an automatic mode, and compiled graphs can be cached so process start does not repeat the compile.

Reach for it when an application is already built on ONNX Runtime and the deployment is Intel hardware: you get OpenVINO's kernels and INT8 paths by adding a provider rather than rewriting against a second API. Watch the partitioning, because a graph fragmented into many small supported islands pays to cross between providers repeatedly and can end up slower than the plain CPU path.

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

The runtime's throughput model is asynchronous: you create several inference requests against one compiled model and submit them with callbacks, so the device pipeline stays full while the application does other work, and the number of requests in flight matters more than raw batch size on CPU. The compiled model object is safe to share across threads, and weights are memory-mapped so several processes do not each pay for their own copy of them.

This is the stack behind Intel-based edge deployments, on cameras, kiosks, industrial boxes and laptops where the accelerator is whatever the CPU package already includes. Reach for it there, and for CPU serving of encoder-sized models where it usually beats a generic runtime. For generative models the GenAI layer above it adds the tokenizer, sampler and KV-cache loop that the base runtime does not provide.

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

It is the Intel backend of Optimum, and its main path is an export command that converts a Transformers or Diffusers checkpoint into OpenVINO IR and optionally applies NNCF weight compression to INT8 or INT4 in the same step. The generated model classes then load that directory through the same `from_pretrained` call as the original checkpoint, so pipeline and tokenizer code around it does not change at all.

It also wraps Intel Neural Compressor for quantization aimed at the PyTorch CPU path and IPEX for optimized eager execution. Reach for it when the deployment is Intel hardware and the model comes from the Hub, since it removes the conversion scripting entirely. Verify accuracy after weight compression on your own evaluation, because the INT4 group size and the choice of which layers to leave in higher precision both matter.

### OVMS on Kubernetes
**Short:** OpenVINO Model Server deployed via Helm chart or operator, exposing KServe-compatible inference endpoints.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/kubernetes-and-orchestration @2

The server is a container that reads models from a mounted volume or an object store and serves them over both the TensorFlow Serving gRPC and REST interfaces and the KServe v2 protocol, so an existing client usually needs no change. On Kubernetes it is deployed by a Helm chart or by the operator, which also lets an inference-service resource select it as a serving runtime, and a configuration file mapping model names to storage paths and versions can be reloaded without restarting the pod.

Reach for it when the cluster's inference is Intel CPU work such as encoder models, classifiers and embeddings, and you want OpenVINO's kernels behind a standard endpoint. It also supports server-side DAG pipelines for chaining preprocessing with a model. For GPU-bound generative workloads this is the wrong layer, and a dedicated LLM engine belongs there instead.

### Phi-3 Mini
**Short:** Microsoft's 3.8B-parameter MIT-licensed small language model, sized to run on a laptop or an edge device.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

The model's premise is data rather than scale: heavily filtered web content plus synthetic textbook-style material, which is what puts a model this small near the quality of far larger ones on reasoning benchmarks while staying quantizable onto a phone. It ships in a short-context and a long-context variant built from the same base, and the architecture is a standard decoder, so every runtime supported it immediately on release.

The MIT licence is a practical reason to choose it, since it places no conditions on commercial use. Reach for it for on-device assistants, extraction and classification, and as a cheap first stage in a cascade. The known weakness follows directly from the training mix: factual breadth is limited, so it is weaker on long-tail world knowledge than a model trained on more raw web text and should be paired with retrieval wherever facts matter.

### Phi-3.5 Mini
**Short:** Microsoft's 3.8B-parameter MIT-licensed small language model, sized for on-device and edge inference.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1

It is a refresh of the same architecture rather than a new one: continued training with more multilingual data, a long context window in the base model rather than as a separate variant, and further instruction tuning. Because the architecture and tokenizer are unchanged, existing runtimes, quantized builds and serving configurations carry over, so swapping it in is usually just a different checkpoint path.

Treat it as the version to prefer over the original unless you have a reason to pin, and evaluate rather than assuming, since a point release can regress on one specific task while improving on average. The size still sets the ceiling: it is a good extractor, classifier, rewriter and router, and it is not the model for multi-step reasoning or specialized domain knowledge.

### Phi-4
**Short:** Microsoft's 14B MIT-licensed small language model, strong on reasoning for its size and runnable on one GPU.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

At this size it fits comfortably on a single modern GPU, and quantized to four bits on considerably less. The training continues the family's approach of using a large fraction of curated synthetic data, generated and filtered to target reasoning rather than scraped for breadth, which is why its strength is concentrated in mathematics, logic and code rather than in recall of facts about the world.

The MIT licence and the size make it a practical self-hosted default when a small model is not enough and a frontier model is either unaffordable or not permitted to leave the network. Pair it with retrieval for anything fact-dependent, and evaluate on your own task: benchmark scores for this family run ahead of general-purpose behaviour, so the gap between a leaderboard and your workload is wider than usual.

### Qwen2.5
**Short:** Alibaba's open-weight model family; licence varies by size, and the 0.5B-3B members are common on-device choices.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @2

The family spans roughly half a billion to seventy-odd billion parameters with the same architecture and tokenizer throughout, which is what makes it useful as a family rather than as individual models: a small member can be the draft model for a large one under speculative decoding, or the cheap first stage of a cascade, with no tokenizer mismatch to work around. Grouped-query attention and a long context window are standard across the sizes, and the Coder and Math variants are trained from the same bases.

The small sizes are common on-device choices and popular fine-tuning bases, since a half-billion or one-and-a-half-billion parameter model trains on modest hardware. Check the licence per size rather than assuming the family is uniform, because the terms differ between members, and note that later Qwen generations have superseded this one for new work, so pin a version deliberately rather than by habit.

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

RWKV replaces attention with a linear recurrence, so decoding carries a fixed-size state forward instead of a KV cache that grows with the sequence. That is what this runtime is built around: memory per sequence stays constant however long the context becomes, and each new token costs the same as the first, which is a very different cost curve from a transformer on the same hardware. It builds on ggml, so weights are quantized and it runs on CPU with no heavy dependency tree.

Reach for it when long contexts and constant memory matter more than peak quality on a CPU-only or embedded target. The tradeoffs are the architecture's rather than the implementation's: a fixed state is a lossy summary of everything seen, so precise recall from far back is weaker than attention's, and the ecosystem of checkpoints, tooling and fine-tunes is far smaller than the transformer world's.

### safetensors
**Short:** Tensor serialization format that loads weights zero-copy and cannot execute code, unlike pickled .bin/.pt files.
**Kind:** spec
**Lang:** *
**Roles:** inference/model-format-and-edge @1, security/supply-chain-and-runtime-security @2

The layout is deliberately dull: a length prefix, a JSON header mapping each tensor name to its dtype, shape and byte range, then the raw tensor data. Loading memory-maps the file and constructs tensors as views over those ranges, so nothing is deserialized and nothing is copied, and pages fault in as they are touched, which is also what makes lazy loading of individual tensors possible. Parsing the header cannot execute anything, because it is data and the format has no callbacks.

That last property is why it became the default. A `.pt` or `.bin` file is a Python pickle, and unpickling runs code by design, so downloading a checkpoint from an untrusted source was arbitrary code execution. Prefer it for anything you publish or consume, and convert legacy checkpoints once rather than loading pickles repeatedly in production.

### Seldon Core
**Short:** Kubernetes-native model serving: inference graphs of models and transformers as CRDs, with drift/outlier sidecars.
**Kind:** tech
**Lang:** *
**Roles:** inference/model-server @1, platform-delivery/kubernetes-and-orchestration @2, ml-lifecycle/drift-and-production-monitoring @3

The first generation modelled everything as one deployment resource describing an inference graph of components, including models, transformers, routers, combiners and explainers, which the controller expanded into deployments and wired together. The second generation splits that into separate model, pipeline, server and experiment resources, with pipeline steps connected through Kafka topics, so a step's inputs and outputs are streams that can be audited, replayed and consumed by other systems.

That dataflow is the distinguishing feature and also the operational cost, since it means running Kafka in order to serve models. Reach for it when serving is genuinely a graph, when experiments and traffic splitting should be declarative, or when inference payloads belong on a bus for monitoring. Check the licence of the version you intend to run before standardising on it, as the terms changed between generations.

### Sequoia
**Short:** Speculative-decoding method building a hardware-aware optimal token tree to raise accepted tokens per step.
**Kind:** tech
**Lang:** *
**Roles:** inference/inference-engine @1

Tree-based speculative decoding verifies many candidate continuations in one pass, and the open question is what tree to verify. This method constructs it in two stages: a dynamic program picks the tree shape that maximizes expected accepted tokens for a given node budget under a model of the draft's acceptance probabilities, and a hardware-aware step chooses the budget itself by measuring where the target model's verification pass stops being effectively free, which is where the batch leaves the memory-bound regime.

Its sampling scheme is designed to stay exact and to remain robust as temperature rises, rather than degrading the way some tree schemes do at high temperature. Reach for it when decode latency dominates and you are already running speculative decoding. The gain over a well-tuned static tree comes largely from not having to tune it per model and per GPU, which is where the effort otherwise goes.

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

The insight is that which prompt positions matter can be read off the end of the prompt: attention from the last few dozen tokens, an observation window, predicts well which earlier positions the generated tokens will attend to. The method scores prefix positions by that attention, pools the scores across neighbouring positions so a contiguous span is retained rather than isolated tokens, selects a per-head budget, and discards the rest of the prompt's cache entries before generation begins.

Because compression happens once at the end of prefill, decoding then runs against a smaller cache with no per-step selection cost, which is what separates it from eviction policies that run continuously. It suits long-prompt, short-answer workloads such as document question answering and summarization. It is lossy, so a question that reaches into a part of the document the observation window did not anticipate is exactly where it fails.

### SparseGPT
**Short:** One-shot Hessian-based pruning that sparsifies an LLM to ~50% weights without retraining.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1

It is the pruning counterpart of GPTQ and shares its machinery: layer by layer, weights are removed in column order while the remaining weights in that layer are updated to compensate, using an inverse-Hessian approximation built from a small calibration set, so the layer's output on that data stays close to the original. Because the update is solved rather than learned, the whole procedure takes hours on one GPU and needs no gradients and no retraining.

It reaches roughly half the weights removed on large models with modest quality loss, and larger models tolerate it better than small ones. The catch is that unstructured sparsity does not make a GPU faster by itself, since the zeros still occupy the tensor unless a runtime exploits them, so pair it with the 2:4 pattern for tensor-core support, or with a sparsity-aware CPU runtime, or treat it as a step before quantization rather than as a speedup in its own right.

### SparseML
**Short:** Neural Magic's pruning, sparsification and quantization toolkit with recipe-driven sparse-transfer training.
**Kind:** tech
**Lang:** python
**Roles:** inference/quantization-and-compression @1, model-training/fine-tuning-and-peft @3

Work is described by a recipe, a YAML file of modifiers and the epochs they apply over, covering gradual magnitude pruning to a target sparsity, quantization-aware training and distillation from a dense teacher, applied to an existing training loop by wrapping the optimizer so the model code does not change. Sparse transfer is the shortcut it is built around: start from an already-sparsified checkpoint of the same architecture and fine-tune on your data with the sparsity pattern held fixed, avoiding a repeat of the expensive pruning search.

It was the front end for producing models for DeepSparse, and that pairing is the context to read it in, because unstructured sparsity paid off there specifically since the runtime exploited it. Neural Magic's work has since moved to vLLM and llm-compressor, so treat this as the older toolchain and check what your serving stack can actually accelerate before pruning for speed.

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

### TinyLlama
**Short:** 1.1B-parameter Apache-2.0 Llama-architecture model, small enough for edge devices and cheap experimentation.
**Kind:** model
**Lang:** *
**Roles:** inference/model-format-and-edge @1, applied-ml/nlp-and-text @3

It uses the Llama architecture and tokenizer unchanged at 1.1B parameters, trained on far more tokens than is compute-optimal for that size, on the reasoning that for a model meant to be deployed cheaply, inference cost matters more than training efficiency. Sharing the architecture means every Llama runtime, quantization tool and fine-tuning script works on it with no changes at all, which is a large part of why it spread.

Its practical uses are as a speculative-decoding draft model for a larger Llama, as a base for cheap fine-tuning experiments, and as a stand-in when testing serving infrastructure without occupying a real GPU. Do not expect general assistant quality at this size; it is a component and a research artifact, and newer sub-2B models from the Llama, Qwen and Gemma families are stronger choices for actual on-device use.

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

### torch.export
**Short:** PyTorch's ahead-of-time whole-graph capture, producing a serializable ExportedProgram with no graph breaks allowed.
**Kind:** api
**Lang:** python
**Roles:** inference/model-format-and-edge @1, inference/compiler-and-runtime-optimization @2

`export(model.eval(), example_args, dynamic_shapes=...)` returns an `ExportedProgram` you can save with `torch.export.save`, feed to AOTInductor for a `.pt2` archive, or lower to ExecuTorch. `Dim` ranges name which dimensions may vary, and `Dim.AUTO` and `Dim.DYNAMIC` let export infer them instead. `torch.onnx.export` is now built on it as well.

The behavioural difference from `torch.compile` is the one to say out loud: export **refuses rather than falling back**. There are no graph breaks, so anything the tracer cannot capture — a data-dependent shape is the usual one — is an error, remedied with `torch.cond`, a `torch._check()` bound, or a `Dim` range. Note also that `strict` now defaults to `False`, using a `__torch_function__`-based tracer that accepts far more real-world code than the original Dynamo-strict path; opt into `strict=True` when you want the stronger soundness guarantee.

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

### TorchDynamo
**Short:** The CPython frame-evaluation hook behind torch.compile: it captures an FX graph plus guards straight from bytecode.
**Kind:** tech
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, devtools/compiler-toolchain-and-codegen @2

Dynamo installs a PEP 523 frame hook, symbolically executes your function's bytecode, and emits an FX graph of the tensor operations together with a set of guards — cheap runtime predicates covering dtype, device, rank, contiguity, `requires_grad`, the values of Python scalars and strings it baked in, and object identities. When a guard fails it compiles a new specialization; when it cannot trace a piece of bytecode it takes a graph break, runs that piece in Python, and starts a new graph.

Recompilation is the failure mode, and the modern cause is not the one most advice names. `automatic_dynamic_shapes` is on by default, so ten distinct tensor shapes cost two compilations, not ten, and the same promotion now applies to Python `int` and `float` arguments. What still storms is anything that cannot be made symbolic: a `str` argument recompiles once per distinct value, and so does dtype churn, device, rank and layout. Past `recompile_limit` — 8 per frame — Dynamo gives up and runs eager forever, with a warning nobody reads, so develop with `fullgraph=True` and diagnose with `TORCH_LOGS`.

### TorchInductor
**Short:** PyTorch 2.x's default compiler backend: lowers the FX graph and emits fused Triton or C++ kernels.
**Kind:** tech
**Lang:** python
**Roles:** inference/compiler-and-runtime-optimization @1, gpu/kernel-programming @3

It is the backend `torch.compile` uses by default. Dynamo captures an FX graph, AOTAutograd splits forward from backward and lowers to ATen operators, and Inductor takes it from there: it decomposes into a small internal representation, fuses elementwise and reduction operations into as few kernels as possible, plans buffer reuse, and generates code, Triton for GPU and C++ with OpenMP for CPU, which is compiled and cached on disk so later runs skip the work.

The gain comes mostly from fusion and from removing per-operation framework overhead, so memory-bound models benefit most and a model already dominated by large GEMMs benefits least. What to watch for is recompilation: a change in input shape, a dtype or a Python branch triggers a new graph, and a loop that recompiles every step is slower than eager. Turning on the recompile logs is how you find it.

### TorchScript
**Short:** PyTorch's legacy JIT and serialization format via torch.jit.script and trace; deprecated in 2.13 in favour of torch.compile and torch.export.
**Kind:** tech
**Lang:** python, cpp
**Roles:** inference/compiler-and-runtime-optimization @1, inference/model-format-and-edge @2

It compiled a module into a statically typed IR that could be saved and loaded from C++ with no Python present, which for years made it the standard way to ship a PyTorch model. Both entry points now emit a deprecation warning pointing at `torch.compile` or `torch.export`. Existing artifacts still load and still serve, including under Triton Inference Server, so nothing is broken — but nothing new should be written against it.

Two characteristic failures are why it was replaced. Scripting demanded that your Python fit a restricted typed subset, so real models needed rewriting to be accepted at all. Tracing recorded a single execution, silently baking in the trace-time shapes and taking only the branch that ran, so a traced model could be quietly wrong on inputs it was never traced with — wrong output, no error. `torch.export` was designed to remove exactly that class of surprise by refusing to capture rather than capturing something incomplete.

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

The score is weight magnitude multiplied by the norm of the corresponding input activation, collected from a small calibration set, so a small weight fed by a large activation is kept while a large weight that never sees much signal is not. The comparison is made per output row rather than across the whole layer, which keeps pruning balanced across neurons, and no weight is updated afterwards: the survivors are exactly the original values.

That is the appeal against Hessian-based methods, since it needs one forward pass, no matrix inverse and no weight update, and runs in minutes on a model that would otherwise take hours, at comparable quality around half sparsity. As with any unstructured pruning, zeros do not make a GPU faster on their own, so target the 2:4 pattern or a sparsity-aware runtime when speed rather than a research result is the goal.

### XLA
**Short:** Google's linear-algebra graph compiler; fuses and lowers ML graphs to TPU, GPU and CPU code.
**Kind:** tech
**Lang:** *
**Roles:** inference/compiler-and-runtime-optimization @1, model-training/deep-learning-framework @3

It compiles a whole-graph representation rather than dispatching operations one at a time: operations are fused into kernels, buffers are assigned and reused under a static memory plan, layouts are chosen for the target, and collectives are scheduled to overlap with computation. Because everything is planned ahead, shapes must be static, so a new input shape means a new compilation, which is why the first call is slow and why dynamic shapes are padded into buckets.

It is the only path to TPUs and the compiler underneath JAX and TensorFlow, with a bridge for PyTorch. Reach for it when the target is a TPU, or when a JAX program should run the same way on GPU. On NVIDIA hardware the comparison is against `torch.compile` and TensorRT, and the practical friction is identical in all of them: anything that changes the graph forces a recompile you have to design around.
