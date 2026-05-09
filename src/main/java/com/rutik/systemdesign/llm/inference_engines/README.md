# Inference Engines

## 1. Concept Overview

Inference engines are specialized software systems optimized for running LLM inference efficiently in production. While you can run an LLM with just Hugging Face Transformers' `model.generate()`, this approach leaves most GPU performance on the table. Production inference engines implement continuous batching, KV cache management, optimized CUDA kernels, quantization, and distributed serving to achieve 10-50× better throughput than naive approaches.

The landscape has exploded: vLLM dominates cloud serving; TensorRT-LLM is NVIDIA's highly-optimized offering; llama.cpp enables CPU and consumer GPU inference; SGLang introduces structural caching; Ollama makes local deployment trivial. Each engine makes different trade-offs between ease of use, performance, hardware requirements, and supported models.

---

## Intuition

> **One-line analogy**: Inference engines are like optimized car engines — the same fuel (model weights) produces 10-50× more horsepower (tokens/second) with engineering optimization than a stock implementation.

**Mental model**: Hugging Face `model.generate()` works but leaves GPU resources massively underutilized. Inference engines implement continuous batching (no wasted GPU slots), PagedAttention (no KV cache fragmentation), fused CUDA kernels (fewer memory operations), and quantization (smaller weights = faster loads). vLLM is like a highly tuned racing engine — the same 70B model goes from 50 tokens/sec to 600+ tokens/sec with the same hardware.

**Why it matters**: Inference is where 90%+ of LLM compute costs occur after a model is trained. A 10× throughput improvement means 10× cost reduction or serving 10× more users with the same hardware. Choosing the right inference engine is one of the most impactful engineering decisions in production LLM systems.

**Key insight**: The bottleneck during LLM decoding is memory bandwidth (loading weights from GPU HBM), not compute. Batching amortizes this load; quantization reduces data volume. Both are fundamental to efficient inference.

---

## 2. Core Principles

- **PagedAttention**: Efficient KV cache memory management — the key innovation that made continuous batching practical.
- **Continuous batching**: Serve many users efficiently by dynamically adding/removing requests from batches.
- **Kernel fusion**: Custom CUDA kernels that fuse multiple operations (avoiding HBM round-trips).
- **Quantization support**: INT4/INT8/FP8 to reduce memory bandwidth requirements.
- **OpenAI-compatible API**: Most engines expose `/v1/completions` and `/v1/chat/completions` endpoints — drop-in replacement for OpenAI SDK.

---

## 3. Engines

### 3.1 vLLM

**The de facto standard for open-source LLM serving.**

**Key innovations:**
- PagedAttention: virtual memory management for KV cache (eliminates fragmentation)
- Continuous batching: maximize GPU utilization across concurrent requests
- OpenAI-compatible API
- Wide model support: LLaMA, Mistral, Mixtral, Qwen, DeepSeek, etc.

```bash
# Installation and startup
pip install vllm

python -m vllm.entrypoints.openai.api_server \
    --model meta-llama/Meta-Llama-3-8B-Instruct \
    --tensor-parallel-size 2 \
    --max-model-len 8192 \
    --gpu-memory-utilization 0.9
```

```python
# Client code (OpenAI-compatible)
from openai import OpenAI

client = OpenAI(base_url="http://localhost:8000/v1", api_key="none")
response = client.chat.completions.create(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    messages=[{"role": "user", "content": "Hello!"}],
    stream=True
)
```

**Performance (8B model, A100 80GB):**
- Throughput: ~3000-4000 tokens/sec
- Concurrent users: 50-200 depending on context length
- TTFT: 100-500ms for typical inputs

**Best for:** Production serving of large open-source models on cloud GPUs.

### 3.2 TensorRT-LLM (NVIDIA)

NVIDIA's inference optimization library for H100/A100. Highest raw performance but more complex setup.

**Key features:**
- Quantization: INT4, INT8, FP8 with auto-calibration
- Custom CUDA/TensorRT kernels (faster than PyTorch ops)
- In-flight batching (equivalent to continuous batching)
- Multi-GPU with tensor parallelism
- Triton Inference Server integration

```python
# Build engine (one-time compilation step)
trtllm-build \
    --checkpoint_dir ./llama-3-70b \
    --output_dir ./llama-3-70b-engine \
    --dtype bfloat16 \
    --use_inflight_batching \
    --paged_kv_cache enable \
    --max_batch_size 256

# Serve with Triton
tritonserver --model-repository=./triton_models
```

**Performance advantage over vLLM:** ~15-40% higher throughput on H100 due to custom kernels.

**Best for:** Maximum throughput in NVIDIA data center environments; enterprise deployments.

### 3.3 llama.cpp

CPU-first inference engine with Metal (Apple Silicon), CUDA, and OpenCL backends. Enables running LLMs on consumer hardware.

**Key features:**
- GGUF quantized format: Q4_K_M (4-bit with mixed precision)
- Apple Silicon Metal GPU acceleration
- CPU SIMD optimizations (AVX-512, NEON)
- Low memory: 4-bit quantized 7B fits in 4.5GB RAM
- Minimal dependencies: just C/C++

```bash
# Build and run
git clone https://github.com/ggerganov/llama.cpp
cmake -B build && cmake --build build --config Release

./build/bin/llama-cli \
    -m ./models/llama-3.1-8b-instruct-Q4_K_M.gguf \
    --color -ngl 35 -n 512 \
    -p "You are a helpful assistant."

# As server
./build/bin/llama-server -m ./models/llama-3.1-8b-Q4_K_M.gguf --port 8080
```

**Performance (8B Q4_K_M on M3 Pro):**
- ~40 tokens/sec on Apple Silicon M3 Pro
- <5GB RAM usage

**Best for:** Local inference, privacy-sensitive applications, edge devices, development.

### 3.4 SGLang (Stanford)

**Structural caching** innovation: caches KV computations across requests that share prefixes.

**Key features:**
- RadixAttention: cache KV for shared prefixes (e.g., system prompts reused across all users)
- Constraint decoding: force JSON/regex output format efficiently
- Multi-modal support
- Better for multi-turn conversations (reuse context from previous turns)

```python
import sglang as sgl

@sgl.function
def multi_turn_chat(s, messages):
    s += sgl.system("You are a helpful assistant.")
    for msg in messages:
        s += sgl.user(msg["content"])
        s += sgl.assistant(sgl.gen("response", max_tokens=200))
    return s

# RadixAttention reuses KV for the system prompt across all requests
# Massive speedup when many users share the same system prompt
```

**Performance vs vLLM:**
- For multi-turn conversations with shared prefixes: 2-5× faster
- For single-turn with no shared context: roughly equivalent

**Best for:** Multi-turn chat systems, constrained generation (JSON mode), multi-modal.

### 3.5 Ollama

Easiest way to run LLMs locally. One-command download and run.

```bash
# Install
curl -fsSL https://ollama.com/install.sh | sh

# Pull and run
ollama run llama3.1

# Use as API
ollama serve  # starts server on localhost:11434

curl http://localhost:11434/api/chat -d '{
  "model": "llama3.1",
  "messages": [{"role": "user", "content": "Hello!"}]
}'
```

**Features:**
- Automatic hardware detection (CPU/GPU/Metal)
- Model library: 100+ models at ollama.com/library
- OpenAI-compatible API (via `ollama serve`)
- Model management: pull, list, delete

**Best for:** Development, demos, personal use, testing models locally.

### 3.6 HuggingFace TGI (Text Generation Inference)

Production inference server from HuggingFace. Tight integration with HuggingFace Hub.

```bash
docker run --gpus all \
    -p 8080:80 \
    -v $PWD:/data \
    ghcr.io/huggingface/text-generation-inference:2.0 \
    --model-id meta-llama/Meta-Llama-3-8B-Instruct \
    --max-input-length 4096 \
    --max-total-tokens 8192
```

**Features:**
- Continuous batching, flash attention
- HuggingFace Hub model loading (gated models via token)
- Tensor parallelism
- Speculation decoding
- AWQ/GPTQ quantization support
- Streaming

**Best for:** HuggingFace ecosystem, teams already using HuggingFace Hub.

---

## 4. Architecture Diagrams

### vLLM Serving Architecture
```
                    HTTP API (/v1/chat/completions)
                              |
                    [Request Handler]
                              |
              ┌───────────────┼───────────────┐
              |          [Scheduler]          |
              |    Continuous batching logic  |
              |    PagedAttention KV manager  |
              └───────────────┬───────────────┘
                              |
                    [Model Executor]
                    GPU(s) running forward passes
                    Custom CUDA kernels
                              |
                    [Token Sampler]
                    Temperature, top-p, top-k
                              |
                    [Streaming Response]
                    Server-Sent Events → client
```

### Engine Selection Decision Tree
```
What hardware do you have?
  |
  +-- Apple Silicon (M-series) → Ollama or llama.cpp (Metal backend)
  |
  +-- Consumer NVIDIA GPU (4080, 4090) → Ollama or vLLM (small models)
  |
  +-- Data center NVIDIA (A100, H100):
  |     |
  |     +-- Need max throughput?  → TensorRT-LLM
  |     |
  |     +-- Need flexibility?     → vLLM
  |     |
  |     +-- Multi-turn / shared prefix? → SGLang
  |
  +-- CPU only → llama.cpp
  |
  +-- Want HuggingFace Hub integration? → TGI

What's your use case?
  Development / testing → Ollama
  Production cloud      → vLLM or TensorRT-LLM
  Edge / privacy        → llama.cpp
  Structured outputs    → SGLang
```

---

## 5. How It Works — Detailed Mechanics

### Quantization Formats

```
GGUF (llama.cpp):
  Q4_0:   4-bit, simple (fastest, worse quality)
  Q4_K_M: 4-bit, mixed precision key layers (best 4-bit quality/speed)
  Q5_K_M: 5-bit mixed (better quality, more memory)
  Q8_0:   8-bit (near full quality)

AWQ (vLLM, TGI):
  Activation-aware weight quantization
  INT4 with better calibration than GPTQ
  Similar quality to FP16 at 4× memory reduction

GPTQ (vLLM, ExLlamaV2):
  Post-training quantization using Hessian information
  INT4, INT8 variants
  Slightly lower quality than AWQ at same bit-width

FP8 (TensorRT-LLM on H100):
  Requires H100 hardware
  Best quality at 8-bit; hardware-supported
  Near-BF16 quality at 2× memory savings
```

### GPU Memory Planning

```
Example: Serving LLaMA 3 70B, max 4096 context, 50 concurrent users on 2× H100 80GB

Model weights (BF16):  70B × 2 bytes = 140GB (72GB per GPU with TP=2)
Activations:           ~2GB per GPU (small)
KV cache per user:     70B model: 2 × 80 layers × 8 KV heads × 128 dim × 4096 tokens × 2 bytes = 660MB
50 users KV cache:     50 × 660MB = 33GB → 16.5GB per GPU

Total per GPU:
  Weights:    70GB
  KV cache:   16.5GB
  Overhead:   3GB
  ─────────────
  Total:      89.5GB ← exceeds 80GB!

Fix options:
  1. Reduce max users to 32: KV = 32 × 660MB / 2 = 10.5GB → fits
  2. Reduce context to 2048: KV = 330MB × 32 → fits with ~55 users
  3. Quantize to INT4: weights = 35GB → more room for KV cache
  4. Add a 3rd GPU (TP=3)
```

---

## 6. Real-World Examples

### Together AI (vLLM-based)
- Serves 50+ open-source models via API
- vLLM as the core serving engine
- Custom extensions for their multi-tenant environment
- Continuously batches across thousands of users

### Anyscale (vLLM)
- AnyScale Endpoints built on vLLM
- Ray Serve for request routing and scaling
- Auto-scaling: 0 → N replicas based on traffic

### Mistral AI
- Uses TensorRT-LLM for Mistral model serving (their own models)
- Custom optimizations for Mistral architecture (sliding window attention)
- La Plateforme API serves millions of requests/day

### Local AI Community
- llama.cpp runs on everything from Raspberry Pi to M3 MacBooks
- Ollama has 1M+ downloads; most popular way to run models locally
- LM Studio: GUI wrapper around llama.cpp for non-technical users

---

## 7. Tradeoffs

| Engine | Throughput | Ease of Use | Hardware | Model Support | License |
|--------|-----------|-------------|---------|---------------|---------|
| vLLM | Excellent | Good | NVIDIA | Wide | Apache 2.0 |
| TensorRT-LLM | Best | Complex | NVIDIA only | Medium | Apache 2.0 |
| llama.cpp | Good (CPU/edge) | Easy | Any | Wide (GGUF) | MIT |
| SGLang | Excellent (prefix) | Medium | NVIDIA | Good | Apache 2.0 |
| Ollama | Good | Easiest | Any | Good | MIT |
| TGI | Good | Medium | NVIDIA | Wide (HF Hub) | Apache 2.0 |

---

## 8. When to Use / When NOT to Use

### Use vLLM When:
- Production cloud serving of open-source models
- Need continuous batching for many concurrent users
- Need OpenAI-compatible API as drop-in replacement

### Use TensorRT-LLM When:
- Maximum throughput on NVIDIA H100/A100 is the primary goal
- Enterprise with NVIDIA DGX infrastructure
- Willing to invest in longer build/compilation time

### Use llama.cpp When:
- Consumer hardware (MacBook, gaming PC)
- Privacy-first: no cloud, everything local
- Edge deployment (limited memory)

### Use Ollama When:
- Local development and testing
- Non-technical users who want LLMs easily
- Quick model experimentation

---

## 9. Common Pitfalls

1. **Underestimating KV cache memory**: Calculating model weights but forgetting KV cache leads to OOM in production.
2. **Not setting max_model_len**: vLLM defaults to model's max sequence length; if that's 128K tokens, KV cache preallocated for 128K → OOM.
3. **Wrong quantization for hardware**: GGUF Q4 on NVIDIA GPU is slower than AWQ; use appropriate quantization for your hardware.
4. **Ignoring tensor parallel vs pipeline parallel**: TP requires NVLink (within node); PP for across nodes. Wrong choice → slow.
5. **Not benchmarking**: "vLLM is fast" doesn't mean it's fast for YOUR model and workload. Always benchmark with production-representative traffic.

---

## 10. Technologies & Tools

| Tool | Notes |
|------|-------|
| **vLLM** | pip install vllm; industry standard |
| **TensorRT-LLM** | Complex setup; max performance on NVIDIA |
| **llama.cpp** | C++; minimal deps; CPU/Metal/CUDA |
| **SGLang** | Radix attention; constrained gen |
| **Ollama** | One-command local LLMs |
| **HuggingFace TGI** | Docker image; HF Hub integration |
| **LM Studio** | GUI for local models |
| **ExLlamaV2** | Fast GPTQ; consumer GPUs |
| **MLC-LLM** | Mobile/browser inference |
| **ONNX Runtime** | Cross-platform inference |

---

## 11. Interview Questions with Answers

**Q: What is vLLM and what makes it efficient?**
A: vLLM is an open-source LLM inference engine known for two key innovations: (1) PagedAttention — manages KV cache like OS virtual memory, using fixed-size pages to eliminate fragmentation and enable near-zero waste; (2) Continuous batching — dynamically adds/removes requests from batches at each step, so fast requests complete quickly and slow ones don't hold GPU slots. Together these give 10-24× higher throughput than naive HuggingFace inference.

**Q: When would you use llama.cpp vs vLLM?**
A: llama.cpp is designed for CPU and consumer-grade GPU inference — it runs quantized GGUF models on MacBooks, gaming PCs, and even Raspberry Pi. It prioritizes low memory usage and broad hardware support. vLLM is designed for data center GPU (A100, H100) serving with many concurrent users — it prioritizes maximum throughput and efficient GPU utilization. Use llama.cpp for local, edge, or privacy-sensitive deployments; use vLLM for cloud production serving.

**Q: What is the OpenAI-compatible API and why does it matter?**
A: Most inference engines expose endpoints like `POST /v1/chat/completions` and `POST /v1/completions` with request/response formats identical to OpenAI's API. This means any application using the OpenAI SDK can switch from the OpenAI API to a self-hosted model by just changing the base_url. It matters because it eliminates vendor lock-in — you can run GPT-4-equivalent open models without changing application code.

**Q: What is RadixAttention in SGLang?**
A: RadixAttention stores KV cache in a radix tree (trie) indexed by the token sequence. When multiple requests share the same prefix (e.g., the same system prompt), SGLang reuses the already-computed KV cache blocks for that prefix — no recomputation. For a chat application where all 1000 users share a system prompt, this means computing the system prompt KV cache once and reusing it across all users, dramatically reducing prefill cost and TTFT.

---

## 12. Best Practices

1. **Set gpu_memory_utilization carefully** — vLLM's default 0.9 (90% of GPU for model + KV cache) is aggressive; start with 0.85 to leave headroom.
2. **Set max_model_len explicitly** — don't let the engine default to model's maximum; set it to your actual max input + output.
3. **Enable tensor parallelism across your GPUs** — multi-GPU almost always worth it for batch throughput.
4. **Monitor queue depth** — if requests are queuing, add replicas; if GPU utilization is low, reduce replicas.
5. **Use quantization in production** — INT4/AWQ reduces cost 4× with <5% quality loss; almost always worth it.
6. **Run load tests before launch** — find your throughput ceiling before users hit it.

---

## 13. Case Study: Migrating from OpenAI API to Self-Hosted vLLM

**Problem:** SaaS startup spending $50K/month on OpenAI API for their writing assistant. Want to reduce costs and eliminate vendor dependency.

**Assessment:**
- Traffic: 10M tokens/day input, 15M tokens/day output
- Latency requirement: TTFT < 1s, TPOT < 50ms
- Quality requirement: ~GPT-3.5-turbo quality (not GPT-4)
- Privacy: no PII in prompts

**Model choice:** Mistral 7B Instruct → meets quality bar for writing tasks

**Infrastructure:**
```
4× NVIDIA A100 80GB (on-demand: ~$12/hr)
vLLM with:
  --model mistralai/Mistral-7B-Instruct-v0.3
  --tensor-parallel-size 1  (7B fits on one GPU)
  --max-model-len 8192
  --gpu-memory-utilization 0.85
  --max-num-seqs 256  (concurrent requests)

4 separate vLLM instances, load-balanced by Nginx
```

**Cost comparison:**
```
OpenAI (gpt-3.5-turbo):
  10M input × $0.001/1K = $10/day
  15M output × $0.002/1K = $30/day
  Total: $40/day = $1200/month

Self-hosted vLLM:
  4× A100 at $12/hr × 24hr = $1152/day
  Wait — that's MORE expensive!

Key insight: Use spot/reserved instances
  Reserved 1-year: ~$4/hr per GPU
  4× A100 × $4 × 24 = $384/day = $11,520/month

Still more? Use fewer GPUs with better utilization:
  1× A100 handles 25M tokens/day = $4/hr × 24 = $96/day = $2,880/month
  Add 1 standby = $5,760/month total

Final: $5,760/month vs $36,000/month → 84% cost reduction
Quality: Acceptable (7B Mistral vs GPT-3.5-turbo)
```
