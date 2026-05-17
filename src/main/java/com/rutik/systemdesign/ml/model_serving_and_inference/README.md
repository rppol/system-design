# Model Serving and Inference

## 1. Concept Overview

Model serving is the process of deploying a trained ML model into a production environment where it can receive requests, run inference, and return predictions. It bridges the gap between offline model training and online value delivery. Inference refers to the execution of a trained model on new input data to produce predictions.

Production serving demands more than correctness — it requires low latency, high throughput, scalability, versioning, graceful degradation, and observability. A model that achieves 95% accuracy in a notebook but takes 500ms per request or cannot handle 1,000 QPS is not production-ready.

Key responsibilities of a serving system:
- Accept prediction requests over HTTP (REST) or binary protocols (gRPC)
- Load model artifacts and manage model lifecycle (versioning, hot-swap)
- Batch requests to maximize hardware utilization
- Return predictions with acceptable latency (P99 under SLA)
- Expose health, readiness, and metrics endpoints

---

## 2. Intuition

Think of model serving like a restaurant kitchen. The trained model is the recipe. Serving is the kitchen operation — taking orders (requests), processing ingredients (features), cooking (inference), and plating the result (response). A single chef (single-threaded serving) can handle a handful of tables; you need parallelism, prep work (preprocessing pipelines), and batching (cooking multiple dishes at once in the oven) to handle a full restaurant at peak hours.

One-line analogy: Model serving is the production kitchen that turns a recipe (trained weights) into meals (predictions) at scale.

Why it matters: 90% of ML value is unrealized until a model serves real traffic. The serving layer determines latency, cost, and reliability.

Key insight: The bottleneck is almost never model accuracy — it is latency, throughput, and operational complexity.

---

## 3. Core Principles

**Separation of concerns**: Keep model inference logic separate from business logic, feature engineering, and serving infrastructure. Use well-defined interfaces.

**Idempotency**: Identical inputs must produce identical outputs. Serving systems must not have hidden mutable state that changes predictions.

**Graceful degradation**: If the primary model is unavailable, fall back to a simpler model, cached result, or rule-based system rather than returning errors.

**Observability**: Every request should emit latency, input shape, output distribution, and error rate. Blind serving is untestable.

**Horizontal scalability**: Serving instances must be stateless so they can be scaled out behind a load balancer. Model weights are loaded at startup from a shared artifact store.

**Hardware-model fit**: Not all models benefit equally from GPU. A 100-parameter logistic regression should serve on CPU; a 7B parameter LLM needs GPU or it will be unusably slow.

---

## 4. Types / Architectures / Strategies

### REST API Serving (Flask / FastAPI)
- Protocol: HTTP/1.1 or HTTP/2, JSON body
- Best for: prototyping, low-QPS internal services, teams with HTTP expertise
- Latency overhead: ~1–5ms serialization per request
- Tools: FastAPI (async, Pydantic validation), Flask (sync, simpler)

### gRPC Serving
- Protocol: HTTP/2, Protocol Buffers (binary)
- Best for: high-QPS production, microservice-to-microservice, latency-sensitive paths
- Speedup over REST: 2–10x faster serialization, persistent connections, bidirectional streaming
- Tools: grpcio, TorchServe gRPC endpoint, TF Serving gRPC

### TorchServe
- PyTorch-native model server
- Handler-based: custom Python handlers for preprocessing, inference, postprocessing
- Supports model versioning, dynamic batching (max_batch_size, batch_delay_ms), metrics
- Management API (port 8081) for model registration/deregistration

### TF Serving
- TensorFlow-native, SavedModel format
- Model versioning with automatic promotion of latest version
- A/B testing via traffic routing configuration
- gRPC and REST endpoints, warm model loading

### ONNX + ONNXRuntime
- Open Neural Network Exchange: cross-framework model interchange format
- Convert PyTorch/TF/sklearn → ONNX once, run everywhere
- ONNXRuntime: 2–5x speedup over PyTorch CPU; graph optimizations, operator fusion
- Execution providers: CPUExecutionProvider, CUDAExecutionProvider, TensorrtExecutionProvider

### Streaming / SSE
- Server-Sent Events or WebSocket for token-by-token output (generative models)
- Reduces time-to-first-token perception; user sees output immediately
- Required for LLM chat interfaces

### Batching Strategies
- Static batching: wait for exactly N requests, send together; simple but adds fixed latency
- Dynamic batching: wait up to max_wait_ms or until max_batch_size reached; adaptive
- Continuous batching (iteration-level): for LLMs, adds new requests mid-generation; maximizes GPU utilization

---

## 5. Architecture Diagrams

### Single-Model REST Serving

```
Client
  |
  | HTTP POST /predict  (JSON: {"features": [...]})
  v
+------------------+
|  Load Balancer   |  (nginx, AWS ALB, GCP GLB)
+------------------+
      |       |
      v       v
+----------+ +----------+
| Serving  | | Serving  |  (FastAPI / TorchServe replicas)
| Instance | | Instance |
+----------+ +----------+
      |
      v
+------------------+
| Model Artifact   |  (S3, GCS, NFS — loaded at startup)
| Store            |
+------------------+
```

### Batching Flow in Dynamic Batching

```
Request 1 ──┐
Request 2 ──┤ --> [Request Queue] --> Batcher --> [Batch of N] --> Model --> Results
Request 3 ──┤                          ^
   ...       |                         |
Request N ──┘             max_batch_size=32 OR max_wait_ms=5ms
```

### A/B Testing / Canary Deployment

```
Incoming Traffic (100%)
        |
        v
  +------------+
  |  Router    |  (5% → Model v2,  95% → Model v1)
  +------------+
      |       |
      v       v
  +-------+ +-------+
  | v1    | | v2    |
  | (95%) | | (5%)  |
  +-------+ +-------+
      |       |
      v       v
  Metrics collection → compare accuracy, latency, business KPIs
```

### ONNX Inference Pipeline

```
Training
  PyTorch Model
       |
       | torch.onnx.export()
       v
  model.onnx  ──────────────────────────────────────┐
                                                     |
Serving                                              v
  Input Tensor → ONNXRuntime Session → Output Tensor → Postprocess → Response
                    (CUDAExecutionProvider or CPUExecutionProvider)
```

---

## 6. How It Works — Detailed Mechanics

### FastAPI + ONNX Serving

```python
from __future__ import annotations

import numpy as np
import onnxruntime as ort
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import time
import logging

logger = logging.getLogger(__name__)

class PredictRequest(BaseModel):
    features: list[list[float]]  # batch of feature vectors

class PredictResponse(BaseModel):
    predictions: list[float]
    latency_ms: float

app = FastAPI(title="ML Model Server")

# Global session — loaded once at startup, thread-safe for inference
_session: ort.InferenceSession | None = None

@app.on_event("startup")
def load_model() -> None:
    global _session
    # Use CUDAExecutionProvider if GPU available, fall back to CPU
    providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]
    _session = ort.InferenceSession("model.onnx", providers=providers)
    logger.info("ONNX model loaded. Providers: %s", _session.get_providers())

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}

@app.get("/ready")
def ready() -> dict[str, str]:
    if _session is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    return {"status": "ready"}

@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest) -> PredictResponse:
    if _session is None:
        raise HTTPException(status_code=503, detail="Model not ready")

    t0 = time.perf_counter()
    input_array = np.array(req.features, dtype=np.float32)

    input_name = _session.get_inputs()[0].name
    output_name = _session.get_outputs()[0].name

    try:
        result = _session.run([output_name], {input_name: input_array})
    except Exception as exc:
        logger.error("Inference failed: %s", exc)
        raise HTTPException(status_code=500, detail="Inference error")

    latency_ms = (time.perf_counter() - t0) * 1000
    predictions = result[0].flatten().tolist()
    return PredictResponse(predictions=predictions, latency_ms=round(latency_ms, 2))
```

### Dynamic Batching with asyncio Queue

```python
import asyncio
from dataclasses import dataclass, field
from typing import Any

MAX_BATCH_SIZE = 32
MAX_WAIT_MS = 5.0

@dataclass
class BatchRequest:
    inputs: np.ndarray
    future: asyncio.Future = field(default_factory=asyncio.Future)

_queue: asyncio.Queue[BatchRequest] = asyncio.Queue()

async def batch_worker() -> None:
    """Background worker: drains queue into batches and runs inference."""
    while True:
        batch: list[BatchRequest] = []
        try:
            # Block until at least one item arrives
            first = await asyncio.wait_for(_queue.get(), timeout=1.0)
            batch.append(first)
        except asyncio.TimeoutError:
            continue

        deadline = asyncio.get_event_loop().time() + MAX_WAIT_MS / 1000
        while len(batch) < MAX_BATCH_SIZE:
            remaining = deadline - asyncio.get_event_loop().time()
            if remaining <= 0:
                break
            try:
                item = await asyncio.wait_for(_queue.get(), timeout=remaining)
                batch.append(item)
            except asyncio.TimeoutError:
                break

        # Run inference on the combined batch
        combined = np.stack([r.inputs for r in batch])
        results = run_inference(combined)  # your model call here

        for i, req in enumerate(batch):
            req.future.set_result(results[i])

async def predict_async(inputs: np.ndarray) -> Any:
    req = BatchRequest(inputs=inputs)
    await _queue.put(req)
    return await req.future
```

### Exporting PyTorch to ONNX

```python
import torch
import torch.nn as nn

class SimpleClassifier(nn.Module):
    def __init__(self, input_dim: int, num_classes: int) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(input_dim, 128),
            nn.ReLU(),
            nn.Linear(128, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)

def export_to_onnx(model: nn.Module, input_dim: int, path: str) -> None:
    model.eval()
    dummy_input = torch.randn(1, input_dim)  # batch_size=1 with dynamic axes
    torch.onnx.export(
        model,
        dummy_input,
        path,
        input_names=["features"],
        output_names=["logits"],
        dynamic_axes={"features": {0: "batch_size"}, "logits": {0: "batch_size"}},
        opset_version=17,
    )
    print(f"Exported to {path}")
```

---

## 7. Real-World Examples

**Uber Michelangelo**: Serves hundreds of models for ETA, surge pricing, fraud detection. Uses a custom gRPC serving layer with feature store integration. Dynamic batching reduces GPU cost by 40% at peak hours.

**Netflix recommendation serving**: REST API backed by TensorFlow Serving. A/B testing infrastructure routes 5% traffic to candidate models. Rollback is automatic if P95 latency exceeds SLA by 20%.

**Stripe fraud detection**: Low-latency (P99 < 10ms) requirement drives CPU-based ONNX serving. GPU would increase throughput but add cold-start latency unsuitable for synchronous payment flows.

**OpenAI ChatGPT**: Continuous batching in vLLM-style inference for generative models. Streaming via SSE so users see tokens as they are generated, masking actual inference time.

---

## 8. Tradeoffs

| Dimension | REST (JSON) | gRPC (Protobuf) | ONNX Runtime | Native Framework (PyTorch) |
|-----------|------------|-----------------|-------------|---------------------------|
| Latency | Higher (JSON parsing) | Lower (binary) | Lower (optimized kernels) | Higher (Python overhead) |
| Throughput | Moderate | High | High | Moderate |
| Portability | Universal | Requires stub gen | Universal | Framework-specific |
| Streaming | SSE / chunked | Native streaming | N/A | N/A |
| Debug ease | Easy (human-readable) | Harder | Moderate | Easy |
| Hardware support | CPU/GPU | CPU/GPU | CPU/GPU/Edge | CPU/GPU |

| Batching Strategy | Latency | Throughput | Complexity |
|-------------------|---------|------------|------------|
| No batching | Lowest | Lowest | Simplest |
| Static batching | Fixed overhead | High | Low |
| Dynamic batching | Adaptive | High | Medium |
| Continuous batching | Best for LLMs | Highest for LLMs | High |

---

## 9. When to Use / When NOT to Use

**Use REST + FastAPI when:**
- Prototyping or internal low-QPS service (< 100 RPS)
- Clients are diverse and cannot generate gRPC stubs
- Team has no gRPC experience

**Use gRPC when:**
- High QPS (> 1,000 RPS) between services
- Bidirectional streaming required (real-time scoring)
- Latency SLA is tight (< 20ms P99)

**Use ONNX Runtime when:**
- Need 2–5x speedup over PyTorch CPU with no GPU
- Cross-framework portability required (TF model serving in PyTorch ecosystem)
- Edge deployment with limited runtime

**Use TorchServe / TF Serving when:**
- Need production-grade model versioning, A/B testing out of the box
- Multi-model serving on the same instance
- Metrics and management API required without custom code

**Do NOT use GPU for:**
- Low-QPS (< 10 RPS) synchronous single-request services — GPU cold-start dominates
- Very small models (logistic regression, small decision trees) — CPU is faster end-to-end
- Cost-sensitive batch jobs that can run overnight on CPU

---

## 10. Common Pitfalls

**War story 1: The cold-start latency spike.** A team deployed a PyTorch model on GPU for a payment fraud endpoint. P99 latency was acceptable at steady state but spiked to 800ms after autoscaler added a new pod. Root cause: CUDA context initialization takes 300–500ms on first request. Fix: warm-up requests sent during pod startup in the readiness probe.

**War story 2: Thread-unsafe session.** An engineer created a new `ort.InferenceSession` per request to avoid shared state. Under 100 RPS load, memory grew 4GB in 10 minutes. Fix: create one session at startup, reuse it — ONNXRuntime inference sessions are thread-safe for concurrent `run()` calls.

**Broken pattern: Missing dynamic axes in ONNX export.**
```python
# BROKEN: fixed batch size baked into graph
torch.onnx.export(model, torch.randn(1, 128), "model.onnx")
# At serving time, batch of 16 raises: "Got inputs with shapes [16, 128] but expected [1, 128]"

# FIXED: declare batch_size as dynamic
torch.onnx.export(
    model, torch.randn(1, 128), "model.onnx",
    dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
)
```

**War story 3: JSON deserialization dominates latency.** A model took 2ms to run but P99 was 45ms. Profiling revealed that deserializing a 500-feature JSON array took 40ms in Python. Fix: switched to gRPC with Protobuf; deserialization dropped to 1ms.

**War story 4: No readiness probe; traffic before model loaded.** Kubernetes sent live traffic to a pod before `model.onnx` was downloaded from S3 (15 seconds). Result: 15 seconds of 500 errors on every deploy. Fix: added `/ready` endpoint that returns 503 until session is initialized; configured readiness probe in Kubernetes deployment spec.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| FastAPI | REST serving | Async, Pydantic, OpenAPI docs auto-generated |
| Flask | REST serving | Sync, simpler for prototypes |
| TorchServe | PyTorch serving | Handler-based, dynamic batching, metrics |
| TF Serving | TF serving | SavedModel, versioning, A/B routing |
| ONNX | Model format | Cross-framework interchange |
| ONNXRuntime | Inference engine | 2–5x speedup on CPU, GPU/NPU execution providers |
| TensorRT | NVIDIA optimization | INT8/FP16, layer fusion; ResNet-50: 7ms CPU → 1.5ms |
| Triton Inference Server | Multi-framework | NVIDIA, supports TF/PyTorch/ONNX/TensorRT, HTTP+gRPC |
| BentoML | Serving framework | Python-native, Docker/Kubernetes baked in |
| Ray Serve | Distributed serving | Composable pipelines, autoscaling, model multiplexing |
| Seldon Core | K8s-native serving | Inference graphs, drift detection sidecar |
| KServe (KFServing) | K8s CRD serving | Standardized inference protocol, canary built-in |

---

## 12. Interview Questions with Answers

**Q: What is the difference between REST and gRPC for model serving, and when would you choose each?**
REST uses HTTP/1.1 with JSON bodies; gRPC uses HTTP/2 with binary Protocol Buffers. gRPC serialization is 2–10x faster and connections are persistent, reducing overhead. For high-QPS internal microservice calls or latency-sensitive paths, gRPC is preferred. REST is better for external-facing APIs, diverse clients, or when human readability of payloads matters for debugging.

**Q: How does dynamic batching reduce cost while maintaining latency SLAs?**
Dynamic batching waits up to `max_wait_ms` (e.g., 5ms) or until `max_batch_size` (e.g., 32) requests accumulate before sending a single GPU kernel launch. A GPU running one sample at a time is 10–30x less efficient than running a full batch. By tolerating 5ms additional latency, throughput can increase 10x, cutting per-prediction GPU cost proportionally. The SLA is maintained because the maximum added latency is bounded by `max_wait_ms`.

**Q: Why might you choose ONNX Runtime over native PyTorch for CPU-based serving?**
ONNXRuntime applies graph-level optimizations (operator fusion, constant folding, memory layout optimization) that PyTorch's eager mode cannot. On CPU, this typically yields a 2–5x throughput improvement and reduced memory bandwidth. ONNX also enables cross-framework portability — a model trained in TensorFlow can be exported to ONNX and served in an ONNXRuntime-based PyTorch microservice.

**Q: Explain the cold-start problem in GPU-based model serving and how to mitigate it.**
When a new serving instance starts, the CUDA runtime must initialize (300–500ms), load model weights to GPU memory (100ms–several seconds for large models), and JIT-compile kernels on first input shape. Until this completes, requests fail or time out. Mitigation: send warm-up requests during pod startup; use Kubernetes readiness probes to hold traffic until the model is ready; use pre-built TensorRT engines that skip JIT compilation.

**Q: What is continuous batching and why is it important for LLM serving?**
Traditional static batching for LLMs waits until all sequences in a batch finish generation, wasting GPU cycles on idle sequences. Continuous batching (iteration-level scheduling) allows inserting new requests into the batch at each forward pass step, filling slots freed by completed sequences. This increases GPU utilization from ~40% (static) to ~80–90%, directly doubling throughput for the same hardware cost.

**Q: How do you implement A/B testing for model updates in production?**
Deploy both model versions as separate serving instances. Configure the load balancer or a feature flag system to route a small percentage of traffic (e.g., 5%) to the new version. Collect business and technical metrics (conversion rate, latency, accuracy on delayed labels) for both versions. After a statistically significant observation period, either promote the new version to 100% or roll back. Shadow mode (run both, compare offline without affecting users) is safer for high-stakes models.

**Q: What are the tradeoffs between serving on GPU vs CPU?**
GPU maximizes throughput for large models and high QPS but has high fixed cost, cold-start latency, and is harder to autoscale quickly. CPU has lower per-instance cost, near-zero cold-start, and scales easily, but is insufficient for large models (LLMs, large CNNs) at production QPS. Rule of thumb: use CPU for models under ~10M parameters at < 50 RPS; use GPU for large models or when throughput demands batch sizes > 8.

**Q: How do you handle model versioning in a production serving system?**
Store model artifacts in a versioned artifact store (S3 with versioning, GCS, MLflow artifact store). Assign semantic or timestamp-based version identifiers. Register models in a model registry (MLflow Model Registry) with stage labels (Staging, Production). Serving infrastructure loads the model pinned to the Production stage. Rolling updates swap the serving pointer atomically, keeping the previous version registered for instant rollback.

**Q: What observability signals should every model serving endpoint emit?**
Request latency (P50, P95, P99) per model version; request throughput (RPS); error rate (5xx, timeout); batch size distribution; input feature statistics (mean, std, null rate for drift detection); model output distribution (prediction score histogram); hardware utilization (GPU memory, CPU, memory). These should feed into Prometheus/Grafana with alerts on SLA breaches.

**Q: How does TorchServe's handler architecture work?**
TorchServe loads a model archive (.mar file) containing the serialized model and a handler Python class. The handler implements three methods: `preprocess` (raw request bytes → tensor), `inference` (tensor → tensor via model.forward), and `postprocess` (tensor → response bytes). The server manages concurrency, batching, and versioning; the handler is the only user-authored code. This separation allows infrastructure teams to own the server and ML engineers to own the handler.

**Q: What is shadow mode serving and when do you use it?**
Shadow mode runs a candidate model on live traffic alongside the production model, but only the production model's response is returned to users. The candidate's predictions are logged and compared offline. This is used when the model update is high-risk (medical, financial), when labeling is slow, or when you want to validate model behavior at real traffic distribution before any user is affected. It doubles inference cost during the shadow period.

---

## 13. Best Practices

- Export models to ONNX for CPU serving; benchmark against native framework before choosing
- Always define dynamic axes in ONNX export to support variable batch sizes
- Implement `/health` (liveness) and `/ready` (readiness) endpoints; configure K8s probes accordingly
- Load model once at process startup into a module-level variable; never reload per request
- Use async endpoints (FastAPI `async def`) only when the model call is truly async or when I/O overlaps with inference; for CPU/GPU-bound inference, use a thread pool
- Set `max_batch_size` and `max_wait_ms` based on measured latency budgets, not defaults
- Pin model version in serving config; never auto-promote `latest` without a gate
- Emit prediction score distributions as metrics; sudden shifts indicate silent model failures
- Test rollback procedure monthly — know the exact steps to revert to the previous version in under 5 minutes
- For gRPC, pre-generate stubs at build time and distribute via an internal package registry

---

## 14. Case Study

### Real-Time Product Recommendation Serving at an E-Commerce Platform

**Problem**: An e-commerce company needed to serve personalized product recommendations to 50,000 concurrent users with P99 < 30ms. The model was a 2-layer MLP with 5M parameters trained in PyTorch. The team initially deployed a Flask endpoint that was hitting P99 of 120ms at 10,000 RPS.

**Bottlenecks identified**:
1. JSON deserialization of 200-feature input vectors: 35ms
2. PyTorch model forward pass (CPU): 25ms
3. JSON serialization of 20 output scores: 15ms
4. Single-threaded Flask: requests queuing behind each other

**Solution architecture**:
```
Mobile/Web Client
      |
      | gRPC (protobuf)
      v
  FastAPI (async) + uvicorn workers (4 workers x 4 threads = 16 concurrent)
      |
      | ONNXRuntime (CPUExecutionProvider, 8 threads)
      v
  model.onnx (converted from PyTorch, dynamic axes enabled)
      |
      v
  Redis cache (feature lookup, 5ms TTL for hot users)
```

**Results after migration**:
- gRPC protobuf serialization: 35ms → 2ms
- ONNXRuntime vs PyTorch CPU: 25ms → 9ms (2.8x speedup)
- Dynamic batching (max_batch_size=16, max_wait_ms=3ms): throughput 3x at same CPU cost
- P99 latency: 120ms → 22ms (under 30ms SLA)
- CPU cost: 40 vCPUs → 14 vCPUs for the same traffic (65% reduction)

**Key decisions**:
- Chose CPU over GPU: recommendation QPS is bursty; GPU cold-start during autoscale would cause latency spikes inconsistent with P99 SLA
- Chose gRPC: client was a Go backend that could generate stubs easily; binary protocol eliminated JSON overhead
- Added feature caching in Redis: 30% of requests had hot users whose features were already computed upstream; cache hit rate 42%, reducing model calls proportionally
- Canary deployment: rolled out ONNX version to 5% traffic for 2 hours, confirmed P99 improvement, then promoted to 100%
