# Observability and Monitoring

---

## 1. Concept Overview

Observability is the ability to understand what a system is doing internally by examining its external
outputs. For a FastAPI service those outputs fall into three pillars: **logs** (discrete events),
**metrics** (aggregated numbers over time), and **traces** (causal chains of work across components).
Monitoring is the practice of collecting, storing, and alerting on those signals.

Together the three pillars answer different questions:

| Pillar | Question answered | Example tool |
|--------|------------------|--------------|
| Logs | What happened and why? | structlog, python-json-logger, Loki |
| Metrics | How often / how fast? | Prometheus, Grafana |
| Traces | Where did latency come from? | OpenTelemetry → Jaeger / Tempo |

A FastAPI application without observability is a black box. When production breaks at 3 AM you need
to answer three questions in under two minutes: is there an error spike, which endpoint or dependency
is slow, and which specific request triggered the failure. Logs, metrics, and traces each carry one
third of that answer.

Python version scope: all examples target Python 3.11 / 3.12 and use `X | None` union syntax.

---

## 2. Intuition

> Running a service without observability is like flying a commercial aircraft with all instruments
> disabled — you only discover the problem when you hit the ground.

**Mental model.** Imagine every HTTP request as a baton in a relay race. A trace follows the baton
from start to finish, recording each hand-off (span). Metrics are the race results posted on a
scoreboard — total laps, average time, failure count. Logs are the per-runner commentary: "dropped
the baton at metre 42 because of a wet grip."

**Why it matters.** Mean-time-to-resolution (MTTR) in production incidents is dominated by
*diagnosis* time, not fix time. Teams with structured logs + metrics dashboards + distributed traces
resolve incidents in under 15 minutes on average; teams relying on `print()` debugging measure MTTR
in hours.

**Key insight.** The three pillars are only valuable when they share a common identifier — the
correlation / trace ID. Without it you have three isolated datasets. With it, a single string
instantly joins the error log, the latency histogram bucket, and the distributed trace into one
coherent picture of a failing request.

---

## 3. Core Principles

**Structured logging over plain text.** Every log line should be a JSON object. Fields like
`request_id`, `user_id`, `endpoint`, `status_code`, and `duration_ms` become queryable dimensions in
Loki, CloudWatch, or Elasticsearch. `logger.info("order %d failed", order_id)` is a string nobody
can aggregate. `logger.info("order.failed", order_id=order_id, reason="payment_timeout")` is a
metric-quality datum.

**Correlation ID as the spine.** Generate a `uuid4()` at the entry point of every request, inject it
into every log line via `contextvars.ContextVar`, propagate it in HTTP headers (`X-Request-ID`,
`traceparent`), and echo it back in the response. Every downstream service carries the same ID.

**High-cardinality labels belong in logs, not metrics.** Prometheus metrics cannot handle unlimited
label values — a label `user_id` with millions of values will exhaust memory. Put high-cardinality
identifiers in logs and traces; use only low-cardinality dimensions (method, route, status class) as
metric labels.

**Health probes are not metrics.** `/health` and `/ready` endpoints answer a binary question for the
orchestrator (restart me? send traffic to me?). They must respond in under 50 ms and must not touch
Prometheus counters or produce log lines — otherwise a K8s liveness loop creates unbounded log noise.

**Sampling is necessary at scale.** At 10 000 req/s, storing 100% of traces costs ~$3 000/month on
Jaeger-backed S3 at typical span sizes. Head-based sampling at 1–5% with tail-based sampling for
errors and slow requests (p99 > 1 s) is the production-proven approach.

---

## 4. Types / Architectures / Strategies

### 4.1 Logging architectures

| Approach | Library | Output | Use case |
|----------|---------|--------|----------|
| Standard library only | `logging` | Configurable | Simple services, lambda |
| Structured + standard | `python-json-logger` | JSON to stdout | Moderate complexity |
| Structured + async-safe | `structlog` | JSON / key=value | High-throughput FastAPI |
| Centralised shipping | Fluentd / Vector sidecar | Aggregated to Loki/ES | Multi-service / K8s |

### 4.2 Metrics collection models

**Pull model (Prometheus default).** Prometheus scrapes `/metrics` every 15 s. The app maintains
in-memory counters; prometheus-fastapi-instrumentator registers the endpoint automatically. Zero
external dependency at runtime.

**Push model (StatsD, InfluxDB line protocol).** The app sends UDP datagrams per event. Lower
latency, no scrape endpoint needed. Harder to debug — if the UDP sink is down, data silently drops.

### 4.3 Tracing propagation models

**Auto-instrumentation.** `opentelemetry-instrumentation-fastapi` wraps the ASGI middleware layer,
creating a span per request with attributes populated from the request. Zero code changes needed for
standard HTTP/DB spans.

**Manual spans.** `with tracer.start_as_current_span("llm.call") as span: span.set_attribute(...)`.
Necessary for business logic, LLM calls, external queue operations — anything the auto-instrumentor
cannot see.

**W3C TraceContext.** The `traceparent` HTTP header carries `{version}-{trace_id}-{parent_id}-{flags}`
across service boundaries. OTel handles propagation automatically when you configure a
`TraceContextPropagator`.

### 4.4 Health probe taxonomy

| Probe | K8s type | Purpose | Failure action |
|-------|----------|---------|----------------|
| `/health` | liveness | Is the process alive and not deadlocked? | Restart pod |
| `/ready` | readiness | Are DB/Redis/dependencies connected? | Remove from load-balancer |
| `/startup` | startupProbe | Has the app finished initialising? | Block liveness checks until true |

---

## 5. Architecture Diagrams

### 5.1 Three-pillar observability stack

```
  FastAPI Process
  ┌────────────────────────────────────────────────────────┐
  │  CorrelationIdMiddleware                                │
  │  ┌──────────────────────────────────────────────────┐  │
  │  │  OTel ASGI Middleware  (auto-span per request)   │  │
  │  │  ┌────────────────────────────────────────────┐  │  │
  │  │  │  Route handler                             │  │  │
  │  │  │  ├── structlog (JSON logs to stdout)       │  │  │
  │  │  │  ├── prometheus_client (in-memory metrics) │  │  │
  │  │  │  └── tracer.start_as_current_span(...)     │  │  │
  │  │  └────────────────────────────────────────────┘  │  │
  │  └──────────────────────────────────────────────────┘  │
  └────────────────────────────────────────────────────────┘
        │                │                     │
        ▼                ▼                     ▼
   stdout/stderr    /metrics (HTTP)     OTLP gRPC :4317
        │                │                     │
        ▼                ▼                     ▼
   Vector/Fluentd   Prometheus scrape    OTel Collector
        │                │                     │
        ▼                ▼                     ▼
      Loki            Grafana            Jaeger / Tempo
```

### 5.2 Span hierarchy for a single request

```
  HTTP POST /orders  (trace_id=abc123)
  ├── [span] http.server          0ms ─────────────── 120ms
  │   ├── [span] db.query (INSERT orders)   5ms ─ 18ms
  │   ├── [span] http.client → payment-svc  20ms ────── 90ms
  │   │   └── [span] db.query (payment-svc) 25ms ── 45ms
  │   └── [span] redis.set (cache order)    95ms ─ 98ms
  └── (response sent)
```

### 5.3 Correlation ID propagation

```
  Client
    │  X-Request-ID: req-7f2e  ──────────────►  FastAPI
    │                                              │
    │  ◄── X-Request-ID: req-7f2e  ───────────────┤
    │                                              │  Log: {"request_id":"req-7f2e","event":"order.created"}
    │                                              │
    │                                              │  ──► Payment service  (X-Request-ID: req-7f2e)
    │                                              │       Log: {"request_id":"req-7f2e","event":"charge.ok"}
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Python logging module hierarchy

The standard `logging` module organises loggers in a dot-separated hierarchy. A logger named
`app.orders` propagates records to `app`, then to the root logger unless `propagate=False` is set.
Handlers (StreamHandler, FileHandler) and formatters are attached at any level.

```python
import logging
import sys

def configure_logging(level: str = "INFO") -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(
        logging.Formatter(
            fmt="%(asctime)s %(name)s %(levelname)s %(message)s",
            datefmt="%Y-%m-%dT%H:%M:%S",
        )
    )
    root = logging.getLogger()
    root.setLevel(level)
    root.addHandler(handler)

logger = logging.getLogger(__name__)   # "app.orders" if module is app/orders.py
```

`logging.getLogger(__name__)` is the canonical pattern: the logger name mirrors the module path,
making it easy to adjust verbosity per subsystem.

### 6.2 Structured logging with structlog

`structlog` wraps the standard library and transforms log calls into pipeline-processed JSON objects.
Processors run in order: timestamp, log level, context extraction from ContextVar, final JSON render.

```python
import structlog
import logging
import sys
from contextvars import ContextVar

request_id_var: ContextVar[str] = ContextVar("request_id", default="")

def add_request_id(
    logger: object, method: str, event_dict: dict[str, object]
) -> dict[str, object]:
    rid = request_id_var.get()
    if rid:
        event_dict["request_id"] = rid
    return event_dict

def configure_structlog() -> None:
    structlog.configure(
        processors=[
            structlog.stdlib.add_log_level,
            structlog.stdlib.add_logger_name,
            structlog.processors.TimeStamper(fmt="iso"),
            add_request_id,                        # custom processor reads ContextVar
            structlog.processors.JSONRenderer(),   # final output: one JSON line
        ],
        wrapper_class=structlog.stdlib.BoundLogger,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )
    logging.basicConfig(stream=sys.stdout, level=logging.INFO)

log = structlog.get_logger()
```

Every `log.info("order.created", order_id=42, amount=99.95)` now emits:
```json
{"level":"info","logger":"app.orders","timestamp":"2024-01-15T10:22:31Z",
 "request_id":"req-7f2e","event":"order.created","order_id":42,"amount":99.95}
```

### 6.3 Correlation ID middleware

```python
import uuid
from contextvars import ContextVar
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

request_id_var: ContextVar[str] = ContextVar("request_id", default="")

class CorrelationIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: object) -> Response:
        request_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
        token = request_id_var.set(request_id)
        try:
            response: Response = await call_next(request)
            response.headers["X-Request-ID"] = request_id
            return response
        finally:
            request_id_var.reset(token)   # always restore — prevents leak into next request
```

The `finally` block is mandatory. Without `reset()`, the ContextVar value from request A can leak
into request B on the same event-loop iteration — a subtle and hard-to-reproduce bug.

### 6.4 Prometheus metrics

```python
from prometheus_client import Counter, Histogram, Gauge
from prometheus_fastapi_instrumentator import Instrumentator

# Auto-instrumentation: registers starlette_requests_total and
# starlette_request_duration_seconds with method/handler/status labels
def setup_metrics(app: object) -> None:
    Instrumentator().instrument(app).expose(app, endpoint="/metrics")

# Custom business metrics
orders_created = Counter(
    "orders_created_total",
    "Total orders created",
    ["payment_method"],   # low-cardinality only: "card", "wallet", "crypto"
)
order_value = Histogram(
    "order_value_dollars",
    "Order value in USD",
    buckets=[1, 5, 10, 25, 50, 100, 250, 500, 1000],
)
active_connections = Gauge(
    "active_db_connections",
    "Number of live DB connections in pool",
)

# Usage in route handler
orders_created.labels(payment_method="card").inc()
order_value.observe(99.95)
active_connections.set(pool.size())
```

### 6.5 OpenTelemetry setup

```python
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.sqlalchemy import SQLAlchemyInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.sdk.resources import Resource

def configure_tracing(service_name: str, otlp_endpoint: str) -> trace.Tracer:
    resource = Resource.create({"service.name": service_name, "service.version": "1.0.0"})
    provider = TracerProvider(resource=resource)
    exporter = OTLPSpanExporter(endpoint=otlp_endpoint, insecure=True)
    provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(provider)

    FastAPIInstrumentor().instrument()       # auto-spans for every HTTP request
    SQLAlchemyInstrumentor().instrument()    # auto-spans for every SQL query
    HTTPXClientInstrumentor().instrument()   # auto-spans for every outbound HTTP call

    return trace.get_tracer(service_name)

tracer = configure_tracing("order-service", "http://otel-collector:4317")

# Manual span for business logic
async def charge_payment(order_id: int, amount: float) -> str:
    with tracer.start_as_current_span("payment.charge") as span:
        span.set_attribute("order.id", order_id)
        span.set_attribute("payment.amount_usd", amount)
        result = await payment_client.charge(order_id, amount)
        span.set_attribute("payment.provider_tx_id", result.tx_id)
        return result.tx_id
```

### 6.6 Health and readiness probes

```python
from fastapi import FastAPI, status
from sqlalchemy.ext.asyncio import AsyncEngine

app = FastAPI()

@app.get("/health", status_code=status.HTTP_200_OK, include_in_schema=False)
async def liveness() -> dict[str, str]:
    # Process is alive. No DB call — a crashed DB must not kill the pod.
    return {"status": "ok"}

@app.get("/ready", status_code=status.HTTP_200_OK, include_in_schema=False)
async def readiness(engine: AsyncEngine) -> dict[str, str]:
    # Verify dependencies are reachable before accepting traffic.
    try:
        async with engine.connect() as conn:
            await conn.execute(text("SELECT 1"))
        return {"status": "ready"}
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"DB unavailable: {exc}",
        )
```

K8s probe configuration (values tuned for a FastAPI + SQLAlchemy service):

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8000
  initialDelaySeconds: 10
  periodSeconds: 15
  failureThreshold: 3      # restart after 3 × 15s = 45s of failure

readinessProbe:
  httpGet:
    path: /ready
    port: 8000
  initialDelaySeconds: 5
  periodSeconds: 10
  failureThreshold: 2      # remove from LB after 2 × 10s = 20s of failure
```

---

## 7. Real-World Examples

**Stripe.** Stripe runs structured JSON logs through a Kafka pipeline into Elasticsearch. Every log
line carries a `request_id` that links to a trace in their internal tracing system. Engineers can
pivot from a 5xx error in Kibana to the Jaeger trace in one click. Stripe publishes their structured
logging conventions as part of the Sorbet/Stripe engineering blog.

**Uber.** Uber's M3 system handles 100 million metric data points per second. They use Prometheus at
the edge (per-service scrape) and aggregate into M3DB for long-term storage. Request-scoped context
is propagated through their internal service mesh via the `x-uber-trace-id` header.

**Cloudflare.** Cloudflare processes 55 million HTTP requests per second. They use tail-based
sampling for distributed traces: only 0.1% of successful requests are stored in full; 100% of error
requests and 5% of slow requests (p99 > 500 ms) are sampled. This reduces trace storage by ~95%
while preserving all actionable signals.

**Netflix.** Netflix's Atlas metrics platform stores 1.2 billion time-series. They enforce that every
metric label has at most 100 unique values — violating this causes the offending metric to be
silently dropped. The rule is enforced at the SDK level.

**Shopify.** Shopify uses OpenTelemetry with a custom sampling strategy that bumps trace sampling
rate to 100% for any merchant in their top-100 by GMV. Business-critical paths always have complete
traces regardless of overall sampling rate.

---

## 8. Tradeoffs

### 8.1 Logging approaches

| Approach | Pros | Cons |
|----------|------|------|
| `print()` | No setup | No level filtering, no structured fields, lost in prod log aggregators |
| `logging` stdlib | Built-in, zero deps | Verbose config, not structured by default |
| `python-json-logger` | Lightweight JSON | Less powerful pipeline than structlog |
| `structlog` | Processor pipeline, async-safe, rich context | Extra dependency, slight learning curve |

### 8.2 Metrics: pull vs push

| Dimension | Pull (Prometheus) | Push (StatsD/InfluxDB) |
|-----------|-------------------|----------------------|
| Reliability | App survives collector outage | Data lost if collector down |
| Cardinality enforcement | Yes — scrape fails before OOM | No — sink may accept then drop |
| Setup complexity | Scrape config per service | Simpler per-service config |
| Latency | Scrape interval (15 s default) | Near-real-time |
| K8s fit | Native (ServiceMonitor CRD) | Requires DaemonSet agent |

### 8.3 Sampling strategies

| Strategy | When to use | Risk |
|----------|-------------|------|
| Head-based 1% | High-volume healthy paths | Misses rare errors |
| Tail-based (keep errors) | Error-heavy workloads | Higher collector CPU |
| 100% sampling | Development / staging | Storage explosion in prod |
| Adaptive (Jaeger) | Variable traffic | Complex operator config |

---

## 9. When to Use / When NOT to Use

### Use structured logging when:
- Running in any environment where logs are shipped to an aggregator (ECS, K8s, Cloud Run).
- You need to query logs by field values (e.g., `request_id = "abc"` or `user_id = 123`).
- More than two developers work on the service — plain-text grep does not scale.

### Do NOT use structured logging when:
- Writing a one-off script or CLI tool where human-readable output matters more than queryability.
- The log aggregator cannot parse JSON (legacy syslog pipelines) — use python-json-logger's
  compatibility mode or a plain formatter.

### Use OpenTelemetry traces when:
- You have two or more services that call each other (microservices, async workers, LLM chains).
- You need to answer "where did latency come from?" faster than reading log timestamps.
- You use an LLM pipeline — cross-reference
  `../../../llm/case_studies/cross_cutting/opentelemetry_for_llm_apps.md` for OTel span conventions
  specific to LLM calls.

### Do NOT add manual spans for:
- In-process computation that takes under 1 ms — span overhead (~5–10 µs) is noise.
- Every function call — a trace with 10 000 spans is unreadable.

### Use Prometheus metrics when:
- You need aggregated percentiles (p50/p95/p99 latency) across thousands of requests.
- You need to set SLO-based alerts (error rate > 1% for 5 minutes).

### Do NOT use Prometheus for:
- Per-request debugging — use logs and traces instead.
- High-cardinality labels (user ID, order ID, IP address) — use Loki labels or trace attributes.

---

## 10. Common Pitfalls

### Pitfall 1: Logging without correlation ID

```python
# BROKEN: logging request context without correlation ID
# — can't trace a single request across logs
import logging
logger = logging.getLogger(__name__)

async def process_order(order_id: int) -> None:
    logger.info("Processing order")      # Which request? Which user? No context.
    logger.error("Payment failed")       # Impossible to correlate with the request log above.

# In production with 500 req/s, these two lines could be thousands of lines apart in the log file.
# There is no way to know they belong to the same request.
```

```python
# FIX: inject correlation ID via middleware + contextvars
from contextvars import ContextVar
import uuid
import structlog
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

request_id_var: ContextVar[str] = ContextVar("request_id", default="")

class CorrelationIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: object):
        request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        token = request_id_var.set(request_id)
        try:
            response = await call_next(request)
            response.headers["X-Request-ID"] = request_id
            return response
        finally:
            request_id_var.reset(token)   # critical: prevent context leak to next request

def add_request_id(logger: object, method: str, event_dict: dict) -> dict:
    rid = request_id_var.get()
    if rid:
        event_dict["request_id"] = rid
    return event_dict

# structlog processor reads from ContextVar automatically;
# every log.info/error call in the codebase now carries request_id
log = structlog.get_logger()

async def process_order(order_id: int) -> None:
    log.info("order.processing", order_id=order_id)
    log.error("order.payment_failed", order_id=order_id, reason="timeout")
    # Both lines emit: {"request_id":"req-7f2e","event":"order.processing","order_id":42}
```

### Pitfall 2: High-cardinality Prometheus labels causing OOM

```python
# BROKEN: using user_id as a Prometheus label
from prometheus_client import Counter

requests_counter = Counter(
    "http_requests_total",
    "HTTP requests",
    ["method", "path", "user_id"],   # user_id has millions of values
)

@app.get("/orders/{order_id}")
async def get_order(order_id: int, user_id: int):
    requests_counter.labels(method="GET", path="/orders", user_id=str(user_id)).inc()
    # Each unique user_id creates a new time series in Prometheus memory.
    # 1M users × ~256 bytes per series = 256 MB just for this counter.
    # Prometheus will OOM and crash.
```

```python
# FIX: keep only low-cardinality labels in metrics; put user_id in logs/traces
from prometheus_client import Counter

requests_counter = Counter(
    "http_requests_total",
    "HTTP requests",
    ["method", "status_class"],   # "2xx", "4xx", "5xx" — 3 possible values
)

@app.get("/orders/{order_id}")
async def get_order(order_id: int, user_id: int):
    # user_id goes into the structured log and the trace span, not the metric
    log.info("order.fetched", order_id=order_id, user_id=user_id)
    with tracer.start_as_current_span("get_order") as span:
        span.set_attribute("user.id", user_id)   # high-cardinality is fine in traces
    requests_counter.labels(method="GET", status_class="2xx").inc()
```

### Pitfall 3: Liveness probe that calls the database

```python
# BROKEN: /health checks the database
@app.get("/health")
async def health(engine: AsyncEngine):
    await engine.execute(text("SELECT 1"))   # If DB is down, pod restarts endlessly.
    return {"status": "ok"}                  # This defeats pod restart as a recovery mechanism.

# FIX: /health is process-only; /ready checks dependencies
@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}   # Always 200 if process is alive

@app.get("/ready")
async def readiness(engine: AsyncEngine) -> dict[str, str]:
    async with engine.connect() as conn:
        await conn.execute(text("SELECT 1"))
    return {"status": "ready"}   # 503 removes pod from LB; DB recovers, pod rejoins
```

### Pitfall 4: Missing span context in background tasks

```python
# BROKEN: trace context not propagated to background task
from fastapi import BackgroundTasks

@app.post("/orders")
async def create_order(bg: BackgroundTasks):
    with tracer.start_as_current_span("create_order"):
        bg.add_task(send_confirmation_email)   # background task runs outside the span context

async def send_confirmation_email():
    with tracer.start_as_current_span("send_email"):
        pass   # This span has no parent — it appears as a separate root trace in Jaeger.

# FIX: capture context and restore it in the background task
from opentelemetry import context as otel_context

@app.post("/orders")
async def create_order(bg: BackgroundTasks):
    with tracer.start_as_current_span("create_order"):
        ctx = otel_context.get_current()   # capture active context
        bg.add_task(send_confirmation_email, ctx)

async def send_confirmation_email(ctx: object) -> None:
    token = otel_context.attach(ctx)   # restore parent context
    try:
        with tracer.start_as_current_span("send_email"):
            pass   # now correctly parented under create_order span
    finally:
        otel_context.detach(token)
```

---

## 11. Technologies & Tools

| Tool | Category | Strengths | Weaknesses |
|------|----------|-----------|------------|
| `structlog` | Logging | Processor pipeline, ContextVar integration, async-safe | Extra dependency |
| `python-json-logger` | Logging | Minimal, extends stdlib | No processor pipeline |
| `prometheus_client` | Metrics | Official Python client, pull model, histograms | In-memory only, no remote write built-in |
| `prometheus-fastapi-instrumentator` | Metrics | Auto-instruments FastAPI ASGI | Limited customisation without subclassing |
| `opentelemetry-sdk` | Tracing | Vendor-neutral, W3C TraceContext, rich ecosystem | Config verbosity |
| `opentelemetry-instrumentation-fastapi` | Tracing | Zero-code HTTP spans | Requires patching at import time |
| Jaeger | Trace backend | Open source, UI, adaptive sampling | Self-hosted ops burden |
| Grafana Tempo | Trace backend | Integrates with Grafana, cheap storage (object store) | Less mature UI than Jaeger |
| Loki | Log aggregation | Label-based like Prometheus, Grafana-native | Not full-text search; limited to label queries |
| Grafana | Visualisation | Unified dashboards (metrics + logs + traces), alerting | Alertmanager config complexity |

---

## 12. Interview Questions with Answers

**Q1: What is the difference between logs, metrics, and traces? When does each answer a different question?**
Logs are discrete timestamped events ("payment failed for order 42"); they answer "what happened and
why?" Metrics are aggregated numbers over time (request rate, p99 latency); they answer "how often
and how fast?" Traces are causal chains across components; they answer "where did latency come from?"
In practice: you alert on metrics, investigate with traces, and root-cause with logs — always linking
all three via a shared correlation ID.

**Q2: Why is `print()` wrong in production and what should replace it?**
`print()` bypasses the Python logging hierarchy, cannot be filtered by level, has no structured
fields, is not thread-safe by default, and its output cannot be dynamically silenced or redirected
without monkey-patching. Replace it with `logging.getLogger(__name__)` at minimum, or `structlog`
for structured JSON output. Structured logs are queryable in aggregators like Loki or Elasticsearch;
plain `print` output is a string that requires regex parsing.

**Q3: What is a ContextVar and why is it preferred over a global variable for request-scoped state?**
`contextvars.ContextVar` stores a value per-execution-context. In asyncio, each `asyncio.Task` has
its own context copy, so two concurrent requests cannot overwrite each other's `request_id` stored in
a ContextVar. A module-level global variable has one value shared across all concurrent requests —
request A's ID would overwrite request B's ID, producing incorrect correlation. ContextVar is the
correct primitive for request-scoped data in async Python.

**Q4: What is the difference between a liveness probe and a readiness probe? What happens if you
conflate them?**
A liveness probe answers "is the process deadlocked or crashed?" — failure triggers a pod restart.
A readiness probe answers "are external dependencies ready?" — failure removes the pod from the
load-balancer without restarting it. Conflating them by putting a database check in `/health` causes
pod restart loops when the database is temporarily down: K8s restarts the pod, the pod tries to
reconnect to a still-down database, fails, gets restarted again — a thundering herd that exacerbates
the outage. The correct split: `/health` returns 200 if the process is alive (zero external calls);
`/ready` returns 503 until all dependencies respond.

**Q5: Why can you not use user_id or order_id as a Prometheus label?**
Prometheus stores every unique label combination as a separate in-memory time-series. With 1 million
users, the label `user_id` creates 1 million time-series for a single counter. At ~256 bytes per
series, that is 256 MB for one metric. Prometheus is designed for low-cardinality labels (method,
status class, endpoint) with at most a few hundred unique values. High-cardinality identifiers belong
in logs (queryable by field) and traces (span attributes have no cardinality limit).

**Q6: Describe the W3C TraceContext header format and why it matters.**
The `traceparent` header format is `{version}-{trace_id}-{parent_span_id}-{trace_flags}`, for
example `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`. It provides a vendor-neutral
standard for propagating trace context across HTTP service boundaries. When every service in your
stack reads and writes `traceparent`, all spans across all services join into a single distributed
trace regardless of which tracing backend each service uses. Without it, traces fragment at service
boundaries and you cannot see cross-service latency.

**Q7: What is tail-based sampling and when is it better than head-based sampling?**
Head-based sampling makes the keep/drop decision at trace ingestion (before the trace completes) — it
is fast but cannot preferentially keep slow or erroneous traces. Tail-based sampling buffers all
spans for a trace until the trace completes, then applies rules: keep all traces with errors, keep
all traces with p99 > 1 s, sample 1% of healthy fast traces. This preserves 100% of actionable
signals at a fraction of the storage cost. The downside: the tail-based collector must buffer
in-flight spans in memory, requiring more collector resources.

**Q8: How does `prometheus-fastapi-instrumentator` work and what does it instrument automatically?**
It wraps the FastAPI/Starlette ASGI application as a middleware and hooks into the request/response
lifecycle. On each request it records start time; on each response it computes duration and increments
`starlette_requests_total` (labels: method, handler, status) and observes
`starlette_request_duration_seconds` (histogram with default latency buckets). It also exposes a
`/metrics` endpoint that Prometheus scrapes. It does not instrument background tasks, outbound HTTP
calls, or database queries — those require OTel auto-instrumentation or manual instrumentation.

**Q9: What is the `BatchSpanProcessor` and when would you use `SimpleSpanProcessor` instead?**
`BatchSpanProcessor` queues spans in memory and flushes them to the exporter in batches on a
background thread — default batch size 512, flush interval 5 s. This minimises the latency impact of
exporting on the request path. `SimpleSpanProcessor` exports each span synchronously before returning
— it adds the exporter's network RTT to every request. Use `SimpleSpanProcessor` only in development
or testing where you want immediate span visibility. In production, always use `BatchSpanProcessor`.

**Q10: How do you propagate OpenTelemetry trace context into a Celery or asyncio background task?**
The OTel context is stored per-`asyncio.Task` via ContextVar. When you spawn a background task (via
`asyncio.create_task`, `BackgroundTasks`, or Celery), the new execution context does not automatically
inherit the parent's OTel context. The fix is to capture the context with `otel_context.get_current()`
before spawning the task, pass it as an argument, and restore it with `otel_context.attach(ctx)` at
the start of the task — followed by `otel_context.detach(token)` in a `finally` block. For Celery,
OpenTelemetry provides a `CeleryInstrumentor` that handles propagation via task headers.

**Q11: What alerting thresholds are industry-standard for a web API?**
SRE teams commonly use: 5xx error rate > 1% over 5 minutes as a page-level alert; p99 latency >
500 ms over 5 minutes as a warn-level alert; p99 latency > 2 s over 2 minutes as a page-level alert.
Availability SLO of 99.9% (43 min downtime/month) is typical for non-critical APIs; 99.95% for
customer-facing paths. These are Prometheus recording rules evaluated by Alertmanager with a
`for: 5m` clause to prevent noise from transient spikes.

**Q12: How do you prevent the `/metrics` endpoint from being publicly accessible?**
Three common approaches: (1) Serve `/metrics` on a separate port (e.g., 9090) that is not exposed
via the public load-balancer ingress, only reachable from within the cluster; (2) Add an IP
allowlist middleware that only permits requests from the Prometheus scraper IP range; (3) Use mutual
TLS between Prometheus and the service. Option 1 is simplest in K8s — add a second `ContainerPort`
and a separate `Service` of type `ClusterIP`. FastAPI supports multiple ASGI apps or a secondary
`prometheus_client.start_http_server(port=9090)` call.

---

## 13. Best Practices

1. **One JSON line per log record.** Multi-line logs break log shippers. Format exceptions as
   `"exception": {"type": "ValueError", "message": "...", "traceback": "..."}` within the JSON
   object.

2. **Log at the boundary, not inside loops.** A loop that calls `log.info(...)` on every iteration
   at 10 000 iterations/request produces 10 000 log lines per request. Log once at entry and once at
   exit with a count: `log.info("batch.processed", record_count=10000, duration_ms=42)`.

3. **Never log secrets or PII.** Correlation IDs, order IDs, and status codes are safe. Passwords,
   API keys, full credit card numbers, and SSNs must be scrubbed before logging. Use a structlog
   processor to redact known-sensitive field names.

4. **Use recording rules for expensive metric queries.** A Prometheus query that computes
   `rate(http_requests_total[5m])` across 50 label combinations runs every 15 s per dashboard panel.
   Pre-compute it as a recording rule: `record: job:http_requests_total:rate5m`.

5. **Set histogram buckets to your SLO.** Default Prometheus histogram buckets are
   `[0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10]` seconds. If your SLO is 200 ms,
   add a bucket at `0.2` so you can compute the fraction of requests meeting the SLO exactly, without
   linear interpolation error.

6. **Add `service.name` and `service.version` as OTel resource attributes.** These appear in every
   span and log record, enabling instant service identification in multi-service Jaeger UIs.

7. **Test observability in CI.** Write a test that calls a route, then asserts the Prometheus counter
   incremented and a log line with `request_id` was emitted. `prometheus_client.REGISTRY` is
   queryable in tests; structlog has a `capture_logs` context manager for assertions.

8. **Gate deployments on error-rate SLO.** In CI/CD, after a canary deployment, wait 5 minutes and
   query Prometheus: if `rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m])
   > 0.01` return non-zero, roll back automatically. This is the observability-driven deployment
   pattern used by Google's SRE teams.

9. **Use `include_in_schema=False` on probe endpoints.** Prevents `/health` and `/ready` from
   cluttering the OpenAPI docs and being indexed by API discovery tools.

10. **Instrument the OTel exporter's failure.** `BatchSpanProcessor` silently drops spans if the
    OTLP endpoint is unreachable. Add a `SpanExporterMetrics` or monitor the processor's
    `_dropped_spans` counter — exporter failures are invisible otherwise.

---

## 14. Case Study

### Multi-tenant SaaS API: full observability stack

A B2B SaaS API with 200 tenants, 5 000 req/min at peak, p99 latency SLO of 300 ms, error-rate SLO
of 99.9%. Deployed on K8s with 3 replicas. The team wants: correlation IDs in every log, per-tenant
latency breakdown in Grafana, distributed traces to Jaeger, and automated canary roll-back.

#### Architecture

```
  Client
    │
    ▼
  Nginx Ingress   (passes X-Request-ID downstream)
    │
    ▼
  FastAPI Pod (×3)
  ├── CorrelationIdMiddleware    → sets request_id_var
  ├── OTel ASGI Middleware       → creates root HTTP span, propagates traceparent
  ├── Route handlers
  │   ├── structlog JSON logs    → stdout → Promtail → Loki
  │   ├── prometheus_client      → /metrics:9090 → Prometheus → Grafana
  │   └── manual OTel spans      → BatchSpanProcessor → OTel Collector → Jaeger
  ├── /health   (liveness)
  └── /ready    (readiness, checks PG + Redis)
```

#### Full application setup

```python
import uuid
import structlog
import logging
import sys
from contextvars import ContextVar
from fastapi import FastAPI, Depends, HTTPException, status
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from prometheus_client import Counter, Histogram
from prometheus_fastapi_instrumentator import Instrumentator
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk.resources import Resource

# ── ContextVar for correlation ID ─────────────────────────────────────────────
request_id_var: ContextVar[str] = ContextVar("request_id", default="")
tenant_id_var: ContextVar[str] = ContextVar("tenant_id", default="unknown")

# ── structlog processors ──────────────────────────────────────────────────────
def inject_context(_logger: object, _method: str, event_dict: dict) -> dict:
    event_dict["request_id"] = request_id_var.get() or "none"
    event_dict["tenant_id"] = tenant_id_var.get() or "unknown"
    return event_dict

structlog.configure(
    processors=[
        structlog.stdlib.add_log_level,
        structlog.processors.TimeStamper(fmt="iso"),
        inject_context,
        structlog.processors.JSONRenderer(),
    ],
    logger_factory=structlog.stdlib.LoggerFactory(),
    cache_logger_on_first_use=True,
)
logging.basicConfig(stream=sys.stdout, level=logging.INFO)
log = structlog.get_logger()

# ── OpenTelemetry setup ───────────────────────────────────────────────────────
_resource = Resource.create({"service.name": "order-api", "service.version": "2.1.0"})
_provider = TracerProvider(resource=_resource)
_provider.add_span_processor(
    BatchSpanProcessor(OTLPSpanExporter(endpoint="http://otel-collector:4317", insecure=True))
)
trace.set_tracer_provider(_provider)
tracer = trace.get_tracer("order-api")

# ── FastAPI app ───────────────────────────────────────────────────────────────
app = FastAPI(title="Order API")

# OTel auto-instrumentation must be called before app starts receiving requests
FastAPIInstrumentor.instrument_app(app)

# ── Middleware ────────────────────────────────────────────────────────────────
class CorrelationIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        request_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
        token = request_id_var.set(request_id)
        try:
            response = await call_next(request)
            response.headers["X-Request-ID"] = request_id
            return response
        finally:
            request_id_var.reset(token)   # always reset — prevents context leak

app.add_middleware(CorrelationIdMiddleware)

# ── Prometheus metrics ────────────────────────────────────────────────────────
Instrumentator().instrument(app).expose(app, endpoint="/metrics", include_in_schema=False)

orders_created = Counter(
    "orders_created_total",
    "Orders created",
    ["tenant_id", "payment_method"],   # tenant_id: max 200 values — acceptable cardinality
)
order_value_hist = Histogram(
    "order_value_dollars",
    "Order value distribution",
    buckets=[1, 10, 25, 50, 100, 250, 500, 1000, 5000],
)

# ── Health probes ─────────────────────────────────────────────────────────────
@app.get("/health", status_code=200, include_in_schema=False)
async def liveness() -> dict[str, str]:
    return {"status": "ok"}

@app.get("/ready", status_code=200, include_in_schema=False)
async def readiness() -> dict[str, str]:
    # In real code: inject AsyncEngine and Redis client via Depends
    return {"status": "ready"}

# ── Route handler ─────────────────────────────────────────────────────────────
@app.post("/orders", status_code=201)
async def create_order(
    order: dict,
    tenant_id: str = "tenant-1",   # simplified; real: extracted from JWT
) -> dict:
    tenant_id_var.set(tenant_id)   # inject into logging context

    with tracer.start_as_current_span("order.create") as span:
        span.set_attribute("tenant.id", tenant_id)
        span.set_attribute("order.amount_usd", order.get("amount", 0))

        log.info("order.create.started", amount=order.get("amount"))
        orders_created.labels(
            tenant_id=tenant_id,
            payment_method=order.get("payment_method", "unknown"),
        ).inc()
        order_value_hist.observe(float(order.get("amount", 0)))

        order_id = uuid.uuid4().hex
        log.info("order.create.complete", order_id=order_id)
        span.set_attribute("order.id", order_id)

    return {"order_id": order_id, "status": "created"}
```

#### BROKEN → FIX: silently swallowing exceptions breaks traces and metrics

```python
# BROKEN: catching all exceptions without recording them in the span or metrics
@app.post("/payments")
async def process_payment(payment: dict) -> dict:
    try:
        result = await payment_gateway.charge(payment)
        return result
    except Exception:
        return {"status": "error"}   # span shows success (no exception recorded)
                                     # metrics show 200, not 5xx
                                     # logs show nothing
                                     # impossible to diagnose in production

# FIX: record exception in span + log + let FastAPI exception handler return 500
@app.post("/payments")
async def process_payment(payment: dict) -> dict:
    with tracer.start_as_current_span("payment.process") as span:
        try:
            result = await payment_gateway.charge(payment)
            span.set_attribute("payment.status", "success")
            return result
        except Exception as exc:
            span.record_exception(exc)               # attaches stack trace to span
            span.set_status(trace.Status(trace.StatusCode.ERROR, str(exc)))
            log.exception("payment.failed", error=str(exc))   # logs full traceback
            raise HTTPException(status_code=502, detail="Payment gateway error")
            # FastAPI's exception handler returns 502 → Prometheus records 5xx → alert fires
```

#### Discussion questions

1. The SLO is p99 < 300 ms. You observe p99 = 450 ms in Grafana. Walk through the debugging steps
   using only the three observability pillars.

2. A tenant reports that 5% of their orders fail silently. Metrics show overall error rate < 0.1%.
   Explain why per-tenant metrics with `tenant_id` labels are essential here and what query you
   would write in PromQL to isolate the affected tenant.

3. The OTel Collector crashes. What happens to in-flight traces? How does `BatchSpanProcessor`
   behave? What monitoring would detect the exporter failure before it becomes an incident?

4. You are asked to add per-user response time tracking. Why can you not add `user_id` as a
   Prometheus histogram label, and what alternative approach provides the same analytical capability?

---

*Cross-references:*
- [Production Deployment and Scaling](../production_deployment_and_scaling/README.md) — K8s probe
  configuration, Gunicorn/Uvicorn worker tuning, graceful shutdown
- [Async Database SQLAlchemy](../async_database_sqlalchemy/README.md) — SQLAlchemy
  `SQLAlchemyInstrumentor` for automatic DB span generation
- [OpenTelemetry for LLM Apps](../../llm/case_studies/cross_cutting/opentelemetry_for_llm_apps.md)
  — OTel span conventions for LLM token usage, model latency, and prompt tracing
