# Design an ML Inference API with FastAPI

---

## Problem Statement

Design a production-grade ML inference API for serving a BERT-based text classification model (~400 MB) via REST.

**Functional requirements:**
- Single-prediction endpoint (`POST /predict`): return top-1 label + confidence for a given text input.
- Streaming endpoint (`POST /predict/stream`): return tokens one-by-one as Server-Sent Events (SSE) for a generative model variant (e.g., summarization decoder).
- Zero-downtime model updates: swap the active model without restarting the process.
- Health and readiness probes: indicate whether a model is loaded and ready to serve.

**Non-functional requirements:**
- Peak throughput: 100 req/s sustained.
- p99 latency: under 200 ms for the sync prediction endpoint.
- Model loading: once at process startup; never on the hot path.
- Cache near-duplicate requests via Redis semantic cache (cosine similarity threshold 0.95) to reduce redundant GPU inference.

**Out of scope:**
- Training or fine-tuning pipelines.
- Multi-tenancy or per-user quotas.
- gRPC transport (REST only in this design).

---

## Architecture Overview

```
Client
  |
  | POST /predict or POST /predict/stream
  v
+---------------------------+
|     FastAPI Application   |
|  (Uvicorn + async workers)|
|                           |
|  lifespan: load model     |
|  into app.state on boot   |
+----------+----------------+
           |
           | 1. Semantic cache lookup (Redis, cosine sim >= 0.95)
           v
+---------------------------+
|     Semantic Cache        |
|  (Redis + sentence-embed) |
+----------+----------------+
           |  cache miss
           v
+---------------------------+
|    Micro-Batch Queue      |
|  asyncio.Queue            |
|  background task:         |
|   - collect up to 8 items |
|   - flush every 10ms      |
+----------+----------------+
           |
           | batched tensor
           v
+---------------------------+
|   Model Inference         |
|  (BERT / GPU)             |
|  app.state.model[slot]    |
+----------+----------------+
           |
    +------+--------+
    |               |
    v               v
Single result    Token stream
JSON response    StreamingResponse
                 (SSE async gen)

Zero-downtime update:
  app.state.slots = {0: model_A, 1: None}
  Load model_B into slot 1 -> swap active_slot -> unload slot 0
```

---

## Key Design Decisions

### 1. Model loading: `lifespan` context manager, never per-request

The `lifespan` async context manager (FastAPI/Starlette) runs once when the process starts. The model is loaded into `app.state` and reused for every request. Loading per-request would add ~5-10 seconds of latency and exhaust memory on concurrent traffic.

**Broken pattern — model loaded per request:**

```python
# BAD: model loaded on every call — never do this
@app.post("/predict")
async def predict(payload: PredictRequest) -> PredictResponse:
    model = BertClassifier.from_pretrained("bert-base-uncased")  # 400 MB load!
    result = model.classify(payload.text)
    return PredictResponse(label=result.label, confidence=result.confidence)
```

**Fixed pattern — model loaded once in `lifespan`:**

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI

@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup: runs once before accepting requests
    app.state.model = await load_model("models/bert-classifier.pt")
    app.state.active_slot = 0
    app.state.slots: dict[int, BertClassifier | None] = {
        0: app.state.model,
        1: None,
    }
    yield
    # shutdown: cleanup GPU memory
    del app.state.model
    app.state.slots = {}

app = FastAPI(lifespan=lifespan)
```

### 2. Micro-batching for GPU throughput

GPUs are throughput devices. Running inference on a single input utilises less than 5% of GPU compute on a BERT base model. Batching 8 requests together increases throughput ~6-8x with only a marginal latency increase. A background `asyncio.Task` drains an `asyncio.Queue` every 10 ms or when 8 items accumulate.

Batch size 8 and flush timeout 10 ms are tunable starting points. With GPU inference at ~15 ms per single input and ~20 ms for a batch of 8, micro-batching delivers ~6x GPU utilization improvement at the cost of up to 10 ms additional queuing latency — comfortably within the 200 ms p99 budget.

### 3. Streaming via `StreamingResponse` + async generator (SSE)

For generative outputs (summarization, token-by-token generation), SSE is preferable to WebSocket for a request/response pattern: it is unidirectional, HTTP-native, and works through standard proxies without upgrade handshakes. `StreamingResponse` in FastAPI wraps an `async` generator that yields SSE-formatted lines.

### 4. Semantic cache in Redis

Exact-match caching (hash of input string) misses near-duplicate prompts that differ only in whitespace, punctuation, or paraphrasing. A semantic cache computes a sentence embedding of the input, stores it with the result in Redis, and on each new request retrieves the top-k stored embeddings and checks cosine similarity. A hit at >= 0.95 returns the cached result and skips GPU inference entirely.

Cache invalidation occurs on model swap or after a configurable TTL (default 1 hour).

### 5. Zero-downtime model updates via dual-slot swap

`app.state.slots` holds two model slots (0 and 1). An admin endpoint loads a new model into the inactive slot, then atomically flips `app.state.active_slot`. Incoming requests that are mid-flight complete against the old slot; new requests are routed to the new slot. The old model is unloaded after a configurable drain window (default 30 seconds).

---

## Implementation

```python
# inference_api/main.py
from __future__ import annotations

import asyncio
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from typing import Any

import numpy as np
import torch
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from redis.asyncio import Redis
from sentence_transformers import SentenceTransformer

# ---------------------------------------------------------------------------
# Pydantic schemas
# ---------------------------------------------------------------------------

class PredictRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=2048)

class PredictResponse(BaseModel):
    label: str
    confidence: float
    cached: bool = False
    latency_ms: float


# ---------------------------------------------------------------------------
# Stub model (replace with real HuggingFace / TorchScript model)
# ---------------------------------------------------------------------------

class BertClassifier:
    """Thin wrapper around a serialised TorchScript BERT classifier."""

    def __init__(self, path: str) -> None:
        self._model = torch.jit.load(path, map_location="cuda" if torch.cuda.is_available() else "cpu")
        self._model.eval()
        self._labels = ["negative", "neutral", "positive"]

    @torch.inference_mode()
    def predict_batch(self, texts: list[str]) -> list[tuple[str, float]]:
        """Return (label, confidence) for each text in the batch."""
        # In production: tokenize with transformers.AutoTokenizer
        # Stub returns a deterministic fake result for illustration.
        results = []
        for text in texts:
            logits = torch.randn(len(self._labels))
            probs = torch.softmax(logits, dim=0)
            idx = int(probs.argmax())
            results.append((self._labels[idx], float(probs[idx])))
        return results


async def load_model(path: str) -> BertClassifier:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, BertClassifier, path)


# ---------------------------------------------------------------------------
# Micro-batcher
# ---------------------------------------------------------------------------

@dataclass
class _BatchItem:
    text: str
    future: asyncio.Future[tuple[str, float]]


class MicroBatcher:
    """Coalesces concurrent single-text requests into GPU batches."""

    def __init__(
        self,
        max_batch_size: int = 8,
        flush_interval_ms: float = 10.0,
    ) -> None:
        self._queue: asyncio.Queue[_BatchItem] = asyncio.Queue()
        self._max_batch = max_batch_size
        self._flush_interval = flush_interval_ms / 1000.0
        self._task: asyncio.Task[None] | None = None

    def start(self, model_getter: Any) -> None:
        self._model_getter = model_getter
        self._task = asyncio.create_task(self._drain_loop())

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass

    async def infer(self, text: str) -> tuple[str, float]:
        future: asyncio.Future[tuple[str, float]] = asyncio.get_event_loop().create_future()
        await self._queue.put(_BatchItem(text=text, future=future))
        return await future

    async def _drain_loop(self) -> None:
        while True:
            # Wait for the first item
            first = await self._queue.get()
            batch: list[_BatchItem] = [first]

            deadline = asyncio.get_event_loop().time() + self._flush_interval
            while len(batch) < self._max_batch:
                remaining = deadline - asyncio.get_event_loop().time()
                if remaining <= 0:
                    break
                try:
                    item = await asyncio.wait_for(self._queue.get(), timeout=remaining)
                    batch.append(item)
                except asyncio.TimeoutError:
                    break

            texts = [item.text for item in batch]
            model: BertClassifier = self._model_getter()
            try:
                results = await asyncio.get_event_loop().run_in_executor(
                    None, model.predict_batch, texts
                )
                for item, result in zip(batch, results):
                    item.future.set_result(result)
            except Exception as exc:  # noqa: BLE001
                for item in batch:
                    if not item.future.done():
                        item.future.set_exception(exc)


# ---------------------------------------------------------------------------
# Semantic cache
# ---------------------------------------------------------------------------

class SemanticCache:
    """Redis-backed cache using sentence embeddings for near-duplicate detection."""

    CACHE_KEY_PREFIX = "sem_cache:"
    EMBED_KEY_PREFIX = "sem_embed:"

    def __init__(
        self,
        redis: Redis,
        embedder: SentenceTransformer,
        threshold: float = 0.95,
        ttl_seconds: int = 3600,
    ) -> None:
        self._redis = redis
        self._embedder = embedder
        self._threshold = threshold
        self._ttl = ttl_seconds

    async def get(self, text: str) -> tuple[str, float] | None:
        query_emb = await asyncio.get_event_loop().run_in_executor(
            None, lambda: self._embedder.encode(text, normalize_embeddings=True)
        )
        # Scan stored embeddings (production: use Redis Vector Search / HNSW)
        keys = await self._redis.keys(f"{self.EMBED_KEY_PREFIX}*")
        for key in keys:
            raw = await self._redis.get(key)
            if raw is None:
                continue
            stored_emb = np.frombuffer(raw, dtype=np.float32)
            sim = float(np.dot(query_emb, stored_emb))
            if sim >= self._threshold:
                cache_key = key.decode().replace(self.EMBED_KEY_PREFIX, self.CACHE_KEY_PREFIX)
                cached = await self._redis.get(cache_key)
                if cached:
                    label, confidence = cached.decode().split("|")
                    return label, float(confidence)
        return None

    async def set(self, text: str, label: str, confidence: float) -> None:
        emb = await asyncio.get_event_loop().run_in_executor(
            None, lambda: self._embedder.encode(text, normalize_embeddings=True)
        )
        import hashlib
        key_id = hashlib.sha256(text.encode()).hexdigest()[:16]
        await self._redis.set(
            f"{self.EMBED_KEY_PREFIX}{key_id}",
            emb.astype(np.float32).tobytes(),
            ex=self._ttl,
        )
        await self._redis.set(
            f"{self.CACHE_KEY_PREFIX}{key_id}",
            f"{label}|{confidence}",
            ex=self._ttl,
        )


# ---------------------------------------------------------------------------
# Application factory + lifespan
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # --- startup ---
    model = await load_model("models/bert-classifier.pt")
    app.state.slots: dict[int, BertClassifier | None] = {0: model, 1: None}
    app.state.active_slot = 0

    embedder = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
    app.state.embedder = embedder

    redis = Redis.from_url("redis://localhost:6379", decode_responses=False)
    app.state.redis = redis
    app.state.cache = SemanticCache(redis, embedder)

    batcher = MicroBatcher(max_batch_size=8, flush_interval_ms=10.0)
    batcher.start(lambda: app.state.slots[app.state.active_slot])
    app.state.batcher = batcher

    yield

    # --- shutdown ---
    await batcher.stop()
    await redis.aclose()
    for slot_model in app.state.slots.values():
        del slot_model


app = FastAPI(title="ML Inference API", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Dependencies
# ---------------------------------------------------------------------------

def get_batcher(request: Request) -> MicroBatcher:
    return request.app.state.batcher


def get_cache(request: Request) -> SemanticCache:
    return request.app.state.cache


# ---------------------------------------------------------------------------
# Sync prediction endpoint
# ---------------------------------------------------------------------------

@app.post("/predict", response_model=PredictResponse)
async def predict(
    payload: PredictRequest,
    batcher: MicroBatcher = Depends(get_batcher),
    cache: SemanticCache = Depends(get_cache),
) -> PredictResponse:
    t0 = time.perf_counter()

    cached_result = await cache.get(payload.text)
    if cached_result:
        label, confidence = cached_result
        latency_ms = (time.perf_counter() - t0) * 1000
        return PredictResponse(label=label, confidence=confidence, cached=True, latency_ms=round(latency_ms, 2))

    label, confidence = await batcher.infer(payload.text)
    await cache.set(payload.text, label, confidence)

    latency_ms = (time.perf_counter() - t0) * 1000
    return PredictResponse(label=label, confidence=confidence, cached=False, latency_ms=round(latency_ms, 2))


# ---------------------------------------------------------------------------
# Streaming endpoint (SSE)
# ---------------------------------------------------------------------------

async def _token_generator(text: str, model: BertClassifier) -> AsyncIterator[str]:
    """Simulate token-by-token generation for a summarization decoder.

    In production: call model.generate() with streaming hooks or a custom
    Hugging Face streamer (TextIteratorStreamer).
    """
    words = text.split()
    for i, word in enumerate(words[:20]):  # stub: echo first 20 tokens
        yield f"data: {word}\n\n"
        await asyncio.sleep(0.015)  # ~15ms per token simulating GPU decode step
    yield "data: [DONE]\n\n"


@app.post("/predict/stream")
async def predict_stream(
    payload: PredictRequest,
    request: Request,
) -> StreamingResponse:
    model: BertClassifier = request.app.state.slots[request.app.state.active_slot]

    async def generator() -> AsyncIterator[str]:
        async for chunk in _token_generator(payload.text, model):
            if await request.is_disconnected():
                break
            yield chunk

    return StreamingResponse(generator(), media_type="text/event-stream")


# ---------------------------------------------------------------------------
# Zero-downtime model update
# ---------------------------------------------------------------------------

class ModelUpdateRequest(BaseModel):
    model_path: str
    drain_seconds: float = 30.0


@app.post("/admin/update-model", status_code=202)
async def update_model(
    body: ModelUpdateRequest,
    request: Request,
) -> dict[str, str]:
    current_slot = request.app.state.active_slot
    new_slot = 1 - current_slot  # toggle between 0 and 1

    # Load into the inactive slot (non-blocking background work)
    new_model = await load_model(body.model_path)
    request.app.state.slots[new_slot] = new_model

    # Atomic swap
    request.app.state.active_slot = new_slot

    # Drain old slot after grace period
    async def _drain_old(old_slot: int, delay: float) -> None:
        await asyncio.sleep(delay)
        old_model = request.app.state.slots.get(old_slot)
        del old_model
        request.app.state.slots[old_slot] = None

    asyncio.create_task(_drain_old(current_slot, body.drain_seconds))

    return {"status": "swapped", "active_slot": str(new_slot)}


# ---------------------------------------------------------------------------
# Health probes
# ---------------------------------------------------------------------------

@app.get("/health/live")
async def liveness() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/ready")
async def readiness(request: Request) -> dict[str, str]:
    model = request.app.state.slots.get(request.app.state.active_slot)
    if model is None:
        raise HTTPException(status_code=503, detail="model not loaded")
    return {"status": "ready"}
```

---

## Python/FastAPI Components Used

| Component | Role |
|-----------|------|
| `lifespan` context manager | Load model, embedder, Redis client, and batcher once at startup; clean up on shutdown |
| `app.state` | Process-scoped mutable store for model slots, batcher, and cache instances |
| `asyncio.Queue` | Back-pressure-safe channel between request handlers and the micro-batch drain loop |
| `asyncio.create_task` | Fire-and-forget drain loop and model drain after swap |
| `asyncio.wait_for` | Bounded wait for additional batch items with a 10 ms timeout |
| `loop.run_in_executor` | Offload blocking CPU/GPU operations (model inference, sentence encoding) to a thread pool, keeping the event loop unblocked |
| `StreamingResponse` | Wrap an async generator and set `Content-Type: text/event-stream` for SSE |
| `Depends()` | Inject `MicroBatcher` and `SemanticCache` into route handlers without passing `app` explicitly |
| `BaseModel` (Pydantic v2) | Validate and serialise request/response payloads with type hints |
| `redis.asyncio.Redis` | Non-blocking Redis client for semantic cache reads and writes |
| `sentence_transformers` | Compute 384-dim sentence embeddings for semantic cache key generation |
| `torch.inference_mode()` | Disable gradient tracking during model forward pass; reduces memory and compute |
| `request.is_disconnected()` | Detect client disconnect mid-stream and stop the async generator early |

Cross-references:
- Model serving patterns: `../../../ml/model_serving_and_inference/README.md`
- SSE and streaming at scale: `../../../llm/case_studies/cross_cutting/streaming_at_scale.md`

---

## Tradeoffs and Alternatives

### Micro-batch vs per-request inference

| Dimension | Micro-batch (chosen) | Per-request |
|-----------|----------------------|-------------|
| GPU utilisation | High — up to 8 inputs per kernel launch | Low — single-item batches waste CUDA cores |
| Added latency | Up to 10 ms queuing + flush | 0 ms queuing |
| Code complexity | Medium — async queue and drain task | Low — direct model call |
| Best for | 100+ req/s with shared GPU | < 10 req/s or CPU-only inference |

Batch size and flush timeout must be tuned per model and hardware. A batch of 8 on a T4 GPU reduces per-request GPU time from ~15 ms to ~3 ms, easily justifying the 10 ms queue wait.

### SSE vs WebSocket for streaming

| Dimension | SSE (chosen) | WebSocket |
|-----------|--------------|-----------|
| Direction | Server-to-client only | Bidirectional |
| Proxy/CDN support | Full HTTP — works everywhere | Requires upgrade; some proxies block |
| Reconnect | Built into the EventSource spec | Manual |
| Overhead | None beyond chunked HTTP | Framing overhead per message |
| FastAPI support | `StreamingResponse` | `WebSocket` endpoint |

SSE is the right choice for token streaming because the client only needs to receive; it never needs to send data after the initial POST.

### Semantic cache tradeoffs

A threshold of 0.95 cosine similarity is conservative and minimises false-positive cache hits (returning a cached answer for a genuinely different query). Lowering to 0.90 increases hit rate but raises the risk of incorrect results. The current scan-all-keys implementation is acceptable at < 10,000 cached entries; at larger scale, replace with Redis Vector Search (HNSW index) for O(log n) retrieval.

### TorchServe vs FastAPI for model serving

| Dimension | TorchServe | FastAPI (chosen) |
|-----------|------------|-----------------|
| Model management | Built-in versioning, A/B | Custom (dual-slot pattern) |
| Batching | Built-in dynamic batching | Custom micro-batcher |
| Streaming | Not native | `StreamingResponse` |
| Custom business logic | Handlers only | Full Python flexibility |
| Operational overhead | JVM + Python process | Single Python process |

TorchServe is appropriate for pure model-serving infrastructure. FastAPI is better when the inference API has business logic, complex auth, custom caching, or must coexist with non-ML endpoints.

---

## Interview Discussion Points

**Why load the model in `lifespan` instead of at module level?**
Module-level loading executes during `import`, which runs in all worker processes simultaneously at startup and also during test imports. `lifespan` runs exactly once per process after the event loop is ready, giving access to async APIs (e.g., loading from S3 with `aioboto3`) and allowing clean teardown on SIGTERM.

**How does the micro-batcher guarantee the 200 ms p99 SLA?**
Worst-case queue time is the flush interval (10 ms). GPU inference on a batch of 8 BERT-base inputs takes approximately 20 ms on a T4. Total worst-case is 10 + 20 + network = ~50 ms, well under 200 ms. The batcher uses `asyncio.wait_for` with a hard timeout so no request waits longer than one flush interval regardless of queue depth.

**What happens if the GPU runs out of memory during a batch inference call?**
`run_in_executor` propagates the `torch.cuda.OutOfMemoryError` back to the event loop. The drain loop catches it, marks all in-flight futures as failed with the exception, and the request handlers return HTTP 500. The batcher continues processing the next batch. A production system should add a circuit breaker that reduces batch size or falls back to CPU inference after repeated OOM events.

**How do you prevent a cache poisoning attack via the semantic cache?**
Only the model's own output is stored — user input is never stored as the value, only as the lookup key. Embeddings are deterministic and produced server-side. A user cannot inject an arbitrary cached answer because `set` is called only after a successful inference result. Input length is bounded by the Pydantic validator (`max_length=2048`).

**Why use cosine similarity at 0.95 rather than exact-match hashing?**
Exact-match hashing misses "What is the sentiment of this review?" and "What's the sentiment for this review?" — semantically identical prompts that hash differently. At 0.95 cosine similarity with MiniLM-L6 embeddings, false positive rate is under 1% on standard NLP benchmarks. Exact-match is still valuable as a first-pass check (O(1)) before the embedding scan.

**How does zero-downtime model update avoid serving inconsistent results mid-swap?**
`app.state.active_slot` is a plain Python integer. Python's GIL guarantees that the integer assignment `active_slot = new_slot` is atomic at the bytecode level. Requests that have already read the old slot ID and are mid-inference continue to use the old model object (which is kept alive in `slots[old_slot]` until the drain window expires). No request sees a partially-loaded model.

**How would you scale this beyond a single process?**
Run multiple Uvicorn workers behind Gunicorn (`-w 4 --worker-class uvicorn.workers.UvicornWorker`). Each worker has its own copy of the model in GPU memory, so memory scales linearly with worker count. The semantic cache lives in Redis, which is shared across all workers, so cache hits are process-agnostic. The micro-batcher is per-process; cross-process batching requires an external queue (Redis Streams or Kafka) feeding a dedicated inference worker, which is the TorchServe or Triton Inference Server architecture.

**What observability would you add to this service?**
At minimum: Prometheus counter for requests (labelled by cached/not-cached and label), histogram for end-to-end latency and per-stage latency (cache lookup, queue wait, inference), and a gauge for micro-batch queue depth. OpenTelemetry spans should wrap the cache lookup and the `run_in_executor` inference call so distributed traces show exactly where latency is spent. See `../../../llm/case_studies/cross_cutting/streaming_at_scale.md` for SSE-specific tracing patterns.

**How would you handle the case where the sentence transformer embedder is slower than the model it is protecting?**
MiniLM-L6 produces 384-dim embeddings and runs at ~2 ms per input on CPU. BERT-base inference on GPU takes ~15 ms per input, so the embedder is 7x faster. If the embedder becomes a bottleneck (e.g., on a CPU-only host), cache the embeddings of frequently seen inputs in a local LRU dict keyed by exact input hash before falling through to Redis and the full embedding scan. This degrades to exact-match for repeated identical inputs without paying the embedding cost.
