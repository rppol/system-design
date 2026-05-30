# OpenTelemetry for LLM Applications

---

## 1. Concept Overview

Standard distributed tracing assumes that each unit of work is a synchronous request/response pair: a service receives an HTTP request, does some work, and returns a response within a bounded time. This model breaks down for LLM applications in four concrete ways.

First, LLM calls are streaming: a single inference request may stream 500 tokens over 8 seconds, producing output token-by-token rather than as a single response body. Standard span semantics (start time, end time, single status) do not capture latency-to-first-token (TTFT), inter-token latency, or token-level event sequences.

Second, LLM calls carry business metadata that standard HTTP spans do not: input token count, output token count, model name, finish reason (`stop`, `length`, `content_filter`), and per-call cost. Without these attributes on every span, cost attribution and quality monitoring require joining data from external billing APIs — a painful operational step.

Third, LLM requests fan out. A single user query may trigger a retrieval span (vector search), an embedding span, an LLM span, and one or more tool-call spans (web search, code execution, database lookup). These spans form a tree, and breaking that tree loses causal relationships between steps.

Fourth, LLM agents span service and process boundaries. An orchestrator agent running in one container may delegate to a sub-agent running in another, passing context via HTTP or message queue. Without explicit trace context propagation, traces fragment into unrelated orphan spans.

OpenTelemetry (OTel) version 1.27+ (2024) introduced the **GenAI Semantic Conventions** (`gen_ai.*` attribute namespace) through the OpenTelemetry Generative AI SIG. These conventions define a standard vocabulary for LLM spans: which attributes to record, how to model streaming responses as span events, and how to propagate context across agent boundaries using W3C TraceContext headers. They are the shared observability primitive that makes LLM traces comparable across providers, frameworks, and tooling vendors.

**Current specification status**: OpenTelemetry GenAI SIG semantic conventions are in `experimental` maturity as of OpenTelemetry specification v1.30 (2025). The `opentelemetry-instrumentation-openai` package implements them.

---

## 2. Intuition

**One-line analogy**: OTel for LLM apps is like adding a flight recorder to every AI call — you capture not just whether the plane landed, but airspeed, altitude, and fuel consumption at every moment of the flight.

**Mental model**: Every LLM request is a tree of nested work units. The root span is the user-facing HTTP request. It contains child spans: a retrieval span, an embedding span, an LLM span. The LLM span contains span events, one per streaming token chunk. The tool-call spans are siblings of the LLM span, linked by parent-child relationships. Each span carries attributes: model name, token counts, cost, finish reason. This tree is what OTel preserves and ships to your backend.

**Why it matters**: Without standard instrumentation, every LLM application vendor (LangSmith, Langfuse, Arize, Honeycomb) uses a proprietary schema. Switching vendors requires re-instrumenting your entire codebase. With `gen_ai.*` semantic conventions, your instrumentation code is vendor-neutral — you write it once and route to any backend that speaks OTLP.

**Key insight**: The span is the unit of cost. Every `gen_ai.usage.input_tokens` and `gen_ai.usage.output_tokens` attribute on an LLM span is the raw material for cost attribution. Aggregate these attributes by `gen_ai.request.model`, tenant ID, and feature name using your backend's query language — no secondary billing API join required.

---

## 3. Core Principles

**Trace = request tree**: A trace is the complete execution tree for a single user request. It groups all spans that share the same `trace_id`. In an LLM application, one user query produces one trace containing all retrieval, embedding, LLM, and tool-call spans.

**Span = one unit of work**: A span is a named, timed operation with a parent. Each span has a `span_id`, `trace_id`, `parent_span_id`, start time, end time, status (OK / ERROR / UNSET), and a set of key-value attributes. For LLM calls, the span covers the entire inference call from request dispatch to final token receipt.

**Attributes carry business metadata**: Attributes are key-value pairs on a span. The `gen_ai.*` namespace defines which attributes LLM spans must carry: `gen_ai.system` (the provider: `openai`, `anthropic`, `google`), `gen_ai.request.model`, `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `gen_ai.response.finish_reason`. These are the inputs to cost calculations and quality dashboards.

**Span events capture streaming**: A span event is a timestamped log entry attached to a span. For streaming LLM responses, each token chunk arrival is a span event with `gen_ai.content.completion` and a timestamp. This lets you compute TTFT (time from span start to first event) and inter-token latency from the event sequence.

**Sampling keeps cost manageable**: Recording every span for a 10 million request/day application at P99 = 8 seconds produces 80 million span-seconds of data per day. Sampling strategies (head-based: decide at trace root; tail-based: decide after seeing full trace; adaptive: vary rate by endpoint or error status) reduce data volume while preserving the traces you care about.

**Baggage propagates context across service boundaries**: OTel Baggage is a key-value store propagated alongside trace context in HTTP headers. Use it to carry tenant ID, user ID, and feature name across service hops without re-reading from a database at each hop.

---

## 4. Types / Architectures / Strategies

### Instrumentation Approaches

| Approach | How it works | Effort | Coverage |
|----------|-------------|--------|----------|
| Auto-instrumentation (`opentelemetry-instrumentation-openai`) | Monkey-patches the OpenAI Python SDK; wraps `client.chat.completions.create` | Near-zero code changes | OpenAI only; limited attribute set |
| Manual SDK instrumentation | Explicit `tracer.start_as_current_span()` calls in your code | Medium | Full control; any provider |
| Proxy-level tracing (LLM gateway) | Gateway intercepts all LLM calls; adds spans without touching app code | Zero app-side code | All providers; no per-app setup |
| Framework-native tracing (LangSmith, Langfuse) | Framework emits OTel-compatible traces automatically | Near-zero with framework | Framework-specific; OTLP export supported |

### Sampling Strategies

**Head-based sampling**: The sampling decision is made at the root span, before any child spans are created. Simple to implement; low overhead. Downside: errors and slow traces are sampled at the same rate as fast ones, so you may drop the most interesting 1% of traces.

**Tail-based sampling**: The OTel Collector buffers complete traces and decides after seeing all spans. A tail-based sampling rule can say "keep all traces with ERROR status or P99 latency > 5s; sample 1% of the rest." Requires more collector memory (buffer of ~30 seconds of traces).

**Adaptive sampling**: Vary sampling rate by endpoint or model. Dev/staging: 100% sampling. Production high-traffic path (`/chat`): 5-10% sampling. Production error path: 100% sampling. Production low-traffic path (`/admin`): 100% sampling.

### Streaming Span Handling

Three patterns for streaming LLM responses:

1. **Single span with events** (recommended): Open one span for the entire LLM call. Emit one span event per token chunk with `gen_ai.content.completion` attribute and chunk timestamp. Close the span after the final chunk. TTFT = timestamp of first event minus span start time.

2. **Single span with final attributes only**: Open one span. Do not emit per-token events. Close the span after final chunk, recording total tokens and finish reason. Simpler but loses TTFT data.

3. **Per-chunk spans** (not recommended): One span per streaming chunk. Produces thousands of tiny spans for a single LLM call, overwhelming the backend and making traces unreadable.

---

## 5. Architecture Diagrams

### Span Hierarchy for a RAG Request

```
Trace: user-query-a3f2b1
|
+-- [HTTP span] POST /api/chat  (root, P50: 2.1s)
    |   trace_id: a3f2b1...
    |   user_id: user-456
    |   tenant_id: acme-corp
    |
    +-- [retrieval span] vector_store.search  (120ms)
    |   |   db.system: chroma
    |   |   db.query: "explain transformer attention"
    |   |   retrieval.k: 8
    |   |   retrieval.result_count: 8
    |   |
    |   +-- [embedding span] embed_query  (35ms)
    |           gen_ai.system: openai
    |           gen_ai.request.model: text-embedding-3-small
    |           gen_ai.usage.input_tokens: 12
    |
    +-- [reranking span] cohere.rerank  (95ms)
    |       cohere.model: rerank-english-v3.0
    |       reranking.input_count: 8
    |       reranking.output_count: 3
    |
    +-- [LLM span] openai.chat  (1.85s)
            gen_ai.system: openai
            gen_ai.request.model: gpt-4o
            gen_ai.usage.input_tokens: 3142
            gen_ai.usage.output_tokens: 287
            gen_ai.response.finish_reason: stop
            gen_ai.cost_usd: 0.0204
            |
            event[0]: gen_ai.content.completion (t+310ms, first token)
            event[1]: gen_ai.content.completion (t+345ms)
            ...
            event[N]: gen_ai.content.completion (t+1850ms, final token)
```

### Multi-Agent Trace Tree

```
Trace: agent-run-7c9d4e
|
+-- [orchestrator span] agent.run  (orchestrator service)
    |   trace_id: 7c9d4e...
    |   agent.name: research-orchestrator
    |   agent.run_id: run-8821
    |
    +-- [LLM span] openai.chat (planning call)  (450ms)
    |       gen_ai.request.model: gpt-4o
    |       gen_ai.usage.input_tokens: 892
    |
    +-- [tool span] tool.web_search  (340ms)
    |       tool.name: web_search
    |       tool.input: "transformer attention mechanism 2024"
    |
    +-- [sub-agent span] sub_agent.call  (HTTP to sub-agent service)
    |   |   traceparent: 00-7c9d4e...-child123-01  (propagated W3C header)
    |   |
    |   +-- [LLM span] anthropic.chat  (sub-agent service)  (820ms)
    |   |       gen_ai.system: anthropic
    |   |       gen_ai.request.model: claude-opus-4-6
    |   |       gen_ai.usage.input_tokens: 4201
    |   |
    |   +-- [tool span] tool.code_exec  (sub-agent service)  (1.2s)
    |           tool.name: python_repl
    |           tool.exit_code: 0
    |
    +-- [LLM span] openai.chat (synthesis call)  (670ms)
            gen_ai.request.model: gpt-4o
            gen_ai.usage.input_tokens: 6104
            gen_ai.usage.output_tokens: 512
```

### Data Pipeline: App to Backend

```
  Application Process
  +---------------------------+
  | LLM App Code              |
  |  tracer.start_span(...)   |
  |  span.set_attribute(...)  |
  |  span.add_event(...)      |
  +---------------------------+
             |
             | OTLP/gRPC (port 4317) or OTLP/HTTP (port 4318)
             | batch export, default 5-second flush interval
             v
  +---------------------------+
  | OTel Collector            |
  |  receivers: otlp          |
  |  processors:              |
  |    - batch (512 spans)    |
  |    - memory_limiter       |
  |    - tail_sampler         |
  |    - attributes (redact   |
  |      gen_ai.prompt)       |
  |  exporters:               |
  |    - jaeger / tempo       |
  |    - langfuse (OTLP)      |
  |    - prometheus (metrics) |
  +---------------------------+
       |              |
       v              v
  +----------+   +-----------+
  | Jaeger / |   | Langfuse  |
  | Tempo    |   | (eval +   |
  | (traces) |   |  traces)  |
  +----------+   +-----------+
```

---

## 6. How It Works — Detailed Mechanics

### OTel Tracer Setup for an LLM Application

```python
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource, SERVICE_NAME

def setup_otel_tracing(service_name: str, otlp_endpoint: str) -> trace.Tracer:
    """
    Configure the global OTel tracer for an LLM application.
    BatchSpanProcessor: default max_queue_size=2048, max_export_batch_size=512,
    schedule_delay_millis=5000 (5-second flush interval).
    """
    resource = Resource.create({
        SERVICE_NAME: service_name,
        "service.version": "1.0.0",
        "deployment.environment": "production",
    })

    provider = TracerProvider(resource=resource)

    # OTLP/gRPC exporter — collector at port 4317
    exporter = OTLPSpanExporter(
        endpoint=otlp_endpoint,  # e.g., "http://otel-collector:4317"
        insecure=True,           # use TLS in production
    )

    provider.add_span_processor(
        BatchSpanProcessor(
            exporter,
            max_queue_size=2048,
            max_export_batch_size=512,
            schedule_delay_millis=5000,
        )
    )

    trace.set_tracer_provider(provider)
    return trace.get_tracer(service_name)

# Application startup
tracer = setup_otel_tracing(
    service_name="llm-rag-api",
    otlp_endpoint="http://otel-collector:4317",
)
```

### `@trace_llm_call` Decorator with `gen_ai.*` Attributes

```python
import functools
import time
from typing import Any, Callable, TypeVar
from opentelemetry import trace
from opentelemetry.trace import StatusCode

F = TypeVar("F", bound=Callable[..., Any])

# Per-token pricing table (USD per token) — update when providers change rates
PRICING: dict[str, dict[str, float]] = {
    "gpt-4o":                 {"input": 5e-6,   "output": 15e-6},
    "gpt-4o-mini":            {"input": 0.15e-6, "output": 0.6e-6},
    "claude-opus-4-6":        {"input": 15e-6,  "output": 75e-6},
    "claude-sonnet-4-6":      {"input": 3e-6,   "output": 15e-6},
    "text-embedding-3-small": {"input": 0.02e-6, "output": 0.0},
}

def trace_llm_call(
    system: str,          # "openai" | "anthropic" | "google"
    operation: str = "chat",
) -> Callable[[F], F]:
    """
    Decorator that wraps any LLM call function and records gen_ai.* attributes.

    Usage:
        @trace_llm_call(system="openai")
        def call_openai(model: str, messages: list, **kwargs):
            return openai_client.chat.completions.create(model=model, messages=messages, **kwargs)
    """
    def decorator(fn: F) -> F:
        @functools.wraps(fn)
        def wrapper(*args, **kwargs):
            model = kwargs.get("model", "unknown")
            with tracer.start_as_current_span(
                f"gen_ai.{operation}",
                kind=trace.SpanKind.CLIENT,
            ) as span:
                # Required gen_ai.* semantic convention attributes
                span.set_attribute("gen_ai.system", system)
                span.set_attribute("gen_ai.operation.name", operation)
                span.set_attribute("gen_ai.request.model", model)

                # Optional request attributes — truncate prompt to avoid 140KB OTLP limit
                messages = kwargs.get("messages", [])
                if messages:
                    prompt_preview = str(messages)[:500]  # 500-char preview only
                    span.set_attribute("gen_ai.request.message_preview", prompt_preview)

                try:
                    response = fn(*args, **kwargs)

                    # Record usage — these drive cost attribution
                    if hasattr(response, "usage") and response.usage:
                        input_tokens = response.usage.prompt_tokens or 0
                        output_tokens = response.usage.completion_tokens or 0
                        span.set_attribute("gen_ai.usage.input_tokens", input_tokens)
                        span.set_attribute("gen_ai.usage.output_tokens", output_tokens)

                        # Cost attribution: tokens × per-token price
                        pricing = PRICING.get(model, {"input": 0.0, "output": 0.0})
                        cost_usd = (
                            input_tokens * pricing["input"]
                            + output_tokens * pricing["output"]
                        )
                        span.set_attribute("gen_ai.cost_usd", round(cost_usd, 8))

                    # Finish reason — critical for detecting truncation or safety filters
                    if hasattr(response, "choices") and response.choices:
                        finish_reason = response.choices[0].finish_reason or "unknown"
                        span.set_attribute("gen_ai.response.finish_reason", finish_reason)

                    span.set_status(StatusCode.OK)
                    return response

                except Exception as exc:
                    span.set_status(StatusCode.ERROR, str(exc))
                    span.record_exception(exc)
                    raise

        return wrapper  # type: ignore[return-value]
    return decorator
```

### Streaming Span Handling — Recording Token Events

```python
import time
from opentelemetry import trace
from opentelemetry.trace import StatusCode

def call_openai_streaming(
    model: str,
    messages: list[dict],
    *,
    tracer: trace.Tracer,
    tenant_id: str,
    feature: str,
) -> str:
    """
    Stream a chat completion while recording token events within a single span.
    TTFT = timestamp of first event minus span start time.
    """
    with tracer.start_as_current_span(
        "gen_ai.chat",
        kind=trace.SpanKind.CLIENT,
    ) as span:
        span.set_attribute("gen_ai.system", "openai")
        span.set_attribute("gen_ai.operation.name", "chat")
        span.set_attribute("gen_ai.request.model", model)
        span.set_attribute("tenant_id", tenant_id)
        span.set_attribute("feature", feature)

        span_start_ns = time.time_ns()
        first_token_recorded = False
        output_parts: list[str] = []
        total_input_tokens = 0
        total_output_tokens = 0

        try:
            stream = openai_client.chat.completions.create(
                model=model,
                messages=messages,
                stream=True,
                stream_options={"include_usage": True},  # get usage in final chunk
            )

            for chunk in stream:
                if not chunk.choices and chunk.usage:
                    # Final chunk carries usage data
                    total_input_tokens = chunk.usage.prompt_tokens or 0
                    total_output_tokens = chunk.usage.completion_tokens or 0
                    continue

                if not chunk.choices:
                    continue

                delta = chunk.choices[0].delta
                content = delta.content or ""
                finish_reason = chunk.choices[0].finish_reason

                if content:
                    output_parts.append(content)
                    chunk_ts_ns = time.time_ns()

                    if not first_token_recorded:
                        # Time-to-first-token in milliseconds
                        ttft_ms = (chunk_ts_ns - span_start_ns) / 1_000_000
                        span.set_attribute("gen_ai.ttft_ms", round(ttft_ms, 1))
                        first_token_recorded = True

                    # One span event per chunk — carries content and timestamp
                    # Limit content to 200 chars to stay within OTLP span event limits
                    span.add_event(
                        "gen_ai.content.completion",
                        attributes={"gen_ai.content.completion": content[:200]},
                        timestamp=chunk_ts_ns,
                    )

                if finish_reason:
                    span.set_attribute("gen_ai.response.finish_reason", finish_reason)

            # Record final usage and cost
            span.set_attribute("gen_ai.usage.input_tokens", total_input_tokens)
            span.set_attribute("gen_ai.usage.output_tokens", total_output_tokens)

            pricing = PRICING.get(model, {"input": 0.0, "output": 0.0})
            cost_usd = (
                total_input_tokens * pricing["input"]
                + total_output_tokens * pricing["output"]
            )
            span.set_attribute("gen_ai.cost_usd", round(cost_usd, 8))
            span.set_status(StatusCode.OK)

            return "".join(output_parts)

        except Exception as exc:
            span.set_status(StatusCode.ERROR, str(exc))
            span.record_exception(exc)
            raise
```

### Agent Trace Propagation — Broken vs Fixed

A common production failure is delegating to a sub-agent via HTTP without forwarding the OTel `traceparent` header. The sub-agent's spans become orphan traces in the backend, breaking the causal chain.

```python
# BROKEN: sub-agent call loses trace context
import httpx

def call_sub_agent_broken(task: str, sub_agent_url: str) -> dict:
    # No trace context forwarded — sub-agent spans are orphaned
    response = httpx.post(
        f"{sub_agent_url}/run",
        json={"task": task},
        # Missing: W3C traceparent/tracestate headers
    )
    return response.json()
```

```python
# FIX: inject W3C TraceContext headers so sub-agent spans attach to the parent trace
import httpx
from opentelemetry import trace, propagate
from opentelemetry.propagators.textmap import DefaultSetter

class _DictSetter(DefaultSetter):
    """Adapts a plain dict to work with the OTel propagator inject API."""
    def set(self, carrier: dict, key: str, value: str) -> None:
        carrier[key] = value

def call_sub_agent(task: str, sub_agent_url: str) -> dict:
    # Build headers dict and inject current trace context into it
    headers: dict[str, str] = {}
    propagate.inject(headers, setter=_DictSetter())
    # headers now contains: {"traceparent": "00-<trace_id>-<span_id>-01", "tracestate": ""}

    with tracer.start_as_current_span(
        "sub_agent.call",
        kind=trace.SpanKind.CLIENT,
    ) as span:
        span.set_attribute("sub_agent.url", sub_agent_url)
        span.set_attribute("sub_agent.task_preview", task[:200])

        # Re-inject with updated span context (child span's span_id as parent)
        headers = {}
        propagate.inject(headers, setter=_DictSetter())

        response = httpx.post(
            f"{sub_agent_url}/run",
            json={"task": task},
            headers=headers,  # traceparent header links sub-agent spans
        )
        span.set_attribute("sub_agent.status_code", response.status_code)
        return response.json()
```

On the sub-agent side, extract context from the incoming `traceparent` header using `propagate.extract(dict(request.headers))` and attach it with `context.attach(ctx)` before creating child spans. Detach in a `finally` block to avoid context leaks.

### Cost Attribution Function

Accumulate cost per `(tenant_id, feature, model)` from span attributes after each LLM call returns. Emit the running total as an OTel `Counter` metric so the OTel Collector's `spanmetrics` processor can publish it to Prometheus. Typical numbers: 1000 calls/day × (3142 input + 287 output tokens) × GPT-4o pricing ($5/$15 per 1M tokens) = $20/day per tenant feature. Alert when the 24-hour counter for any tenant exceeds $50.

```python
from collections import defaultdict

_cost_by_key: dict[tuple[str, str, str], float] = defaultdict(float)

def record_llm_cost(
    tenant_id: str, feature: str, model: str,
    input_tokens: int, output_tokens: int,
) -> None:
    pricing = PRICING.get(model, {"input": 0.0, "output": 0.0})
    cost = input_tokens * pricing["input"] + output_tokens * pricing["output"]
    _cost_by_key[(tenant_id, feature, model)] += cost
    # Emit to OTel meter for Prometheus scraping + Grafana alerting
    llm_cost_counter.add(
        cost,
        attributes={"tenant_id": tenant_id, "feature": feature, "model": model},
    )
```

---

## 7. Real-World Examples

**LangSmith**: LangChain's observability platform emits traces in a format compatible with the OTel GenAI semantic conventions. Each LangChain Runnable step becomes a span. The `on_llm_end` callback captures `prompt_tokens`, `completion_tokens`, and model name, which LangSmith stores as span attributes. In 2024, LangSmith added OTLP export — you can forward LangSmith traces to Jaeger or Grafana Tempo by configuring an OTLP exporter in your LangSmith settings. This demonstrates that even proprietary LLM observability tools converge on OTel as the transport layer.

**Arize Phoenix**: Phoenix instruments LLM calls using OpenInference, a schema closely aligned with OTel GenAI semantic conventions. Phoenix spans carry `llm.token_count.prompt`, `llm.token_count.completion`, `llm.model_name`, and `retrieval.documents` attributes. Arize supports OTLP ingest, enabling Phoenix traces to flow into any OTLP-compatible backend. A Phoenix trace for a RAG pipeline shows the full tree: retrieval span with document scores, embedding span with latency, LLM span with token counts and cost.

**Anthropic Claude API**: The Claude API response object carries `usage.input_tokens` and `usage.cache_read_input_tokens` (for prompt caching). A well-instrumented wrapper records both on the span: `gen_ai.usage.input_tokens` (total input) and `gen_ai.usage.cache_read_input_tokens` (from cache at 10% of standard price). The difference, `gen_ai.usage.cache_miss_tokens`, is what you pay full price for. Tracking the cache hit rate per tenant reveals whether long system prompts are being effectively cached — a ratio below 80% on a high-volume path indicates a caching configuration problem.

**Cost attribution dashboard design**: A production cost dashboard for a multi-tenant LLM platform queries the trace backend for spans with `gen_ai.cost_usd` attributes, groups by `tenant_id`, `feature`, and `gen_ai.request.model`, and aggregates over rolling 24-hour windows. Alert thresholds: tenant daily cost > $50 triggers a Slack alert; model cost per call > $0.10 triggers a review (indicates unexpectedly long prompts). The dashboard also shows TTFT P50/P95/P99 per model, broken down by streaming vs non-streaming requests.

---

## 8. Tradeoffs

### OTel vs Proprietary LLM Observability Vendors

| Dimension | OTel (self-managed) | LangSmith | Langfuse | Arize Phoenix |
|-----------|---------------------|-----------|----------|---------------|
| Setup effort | High (infra + schema) | 2 env vars | 2 env vars | Library install |
| Vendor lock-in | None | High (LangChain-specific) | Low (OTLP export) | Low (OTLP export) |
| Cost at 10M spans/day | Collector + backend infra | $500-2000/month SaaS | $200-1000/month SaaS | $500-2000/month SaaS |
| LLM-specific features | Requires custom attributes | LLM-as-judge eval built-in | Eval, datasets, prompts | Root cause analysis, drift |
| Provider support | Any (manual instrumentation) | OpenAI, Anthropic, LangChain | Any | Any |
| Prompt content storage | Your choice (OTel Collector redacts) | Stored in LangSmith (PII risk) | Configurable masking | Configurable masking |
| Correlation with evals | Manual join | Native | Native | Native |

### Head-Based vs Tail-Based Sampling for LLM Apps

| Dimension | Head-based | Tail-based |
|-----------|-----------|-----------|
| Decision point | Trace root (before any spans) | After full trace is assembled |
| Latency overhead | Near-zero | 10-30s buffer in collector |
| Keeps slow traces | No (sampled at root) | Yes (rule: keep P99 > 5s) |
| Keeps error traces | No | Yes (rule: keep ERROR status) |
| Collector memory | Low (no buffering) | High (30s of in-flight traces) |
| Implementation | SDK-side sampler | OTel Collector tail_sampling processor |
| Recommended for LLM | Dev/staging (100%), prod low-traffic | Prod high-traffic paths |

---

## 9. When to Use / When NOT to Use

**Use 100% sampling when:**
- Development and staging environments — every trace is needed for debugging
- Low-traffic production paths (< 1000 requests/day) — overhead is negligible
- Any new LLM feature in its first 2 weeks of production — full trace coverage surfaces bugs that sampling would miss
- After a production incident — temporarily increase sampling to 100% on the affected path during root cause analysis

**Use adaptive/tail-based sampling when:**
- High-traffic production paths (> 100,000 requests/day) — 10M spans/day at 1KB each = 10GB/day; at 10% sampling = 1GB/day
- Cost is the primary constraint — OTLP ingest fees from cloud vendors scale linearly with span volume

**Do NOT log raw prompt content in span attributes when:**
- Any user-supplied content may contain PII — patient records, financial data, legal documents, personal communications
- Your LLM application processes enterprise confidential data — code, internal documents, trade secrets
- Operating under GDPR, HIPAA, or SOC 2 — span data in backends is often indexed, searchable, and retained for 30-90 days; raw prompts in that index is a compliance violation
- Instead: store a hashed prompt ID (`sha256(prompt)[:16]`) on the span and keep the full prompt in an encrypted, access-controlled prompt store with appropriate retention policies

**Use proxy-level tracing (gateway) when:**
- You have 10+ microservices each making LLM calls — per-app instrumentation becomes a maintenance burden
- You want to enforce a uniform attribute policy (always add `tenant_id`, always redact prompts) without trusting each team to do it correctly
- Cross-references: `../../llm_observability_and_monitoring/README.md` for monitoring strategy; `../design_llm_gateway.md` for the gateway architecture

---

## 10. Common Pitfalls

**Pitfall 1: Prompt content in span attributes triggering GDPR violations**

In 2023, Samsung engineers accidentally pasted proprietary chip design schematics into ChatGPT prompts. This became the canonical enterprise example: the fear is not ChatGPT storing data (that can be opted out) — it is that the observability layer stores it permanently. A team at a European bank instrumented their LLM RAG pipeline with full prompt content in `gen_ai.request.prompt` span attributes. Langfuse retained spans for 90 days. A routine audit found patient-adjacent PII (derived from HR queries) in trace data. The fix required: (1) OTel Collector attribute processor to drop `gen_ai.request.prompt` and `gen_ai.content.completion` in production, (2) replace with a hashed prompt ID referencing an encrypted sidecar store, (3) retroactive deletion of 90 days of span data from the backend. The incident cost 3 engineering weeks and a compliance audit. Rule: never log raw prompt or completion content in span attributes in production — use a hashed prompt ID from day one.

**Pitfall 2: Missing TTFT data due to single span without events**

A team recorded one span per streaming LLM call with only final token counts and end time. The P99 latency metric looked fine (8.2 seconds). Users were complaining about "slow responses." The actual problem: TTFT P99 was 4.1 seconds — users waited 4 seconds before seeing the first word. Without per-chunk span events, TTFT was invisible. The fix: add `span.add_event("gen_ai.content.completion", timestamp=time.time_ns())` on the first token chunk and record `gen_ai.ttft_ms` as a span attribute. After the fix, the dashboard showed TTFT and total latency separately, revealing that 90% of perceived latency was TTFT — a retrieval pre-processing step was the bottleneck, not the LLM.

**Pitfall 3: Baggage not propagated across Celery/Redis worker queues**

A multi-step agent used Celery for async tool execution. The orchestrator created a trace, dispatched a Celery task, and the worker picked it up minutes later. The worker's spans had no parent — orphan traces. The root cause: `contextvar` based OTel context is process-local. When Celery serializes a task to Redis and a worker deserializes it, the context is gone. The fix: serialize the trace context as a string in the Celery task payload.

```python
# In orchestrator: serialize trace context into task args
from opentelemetry import propagate

def dispatch_tool_task(tool_name: str, tool_args: dict) -> str:
    carrier: dict[str, str] = {}
    propagate.inject(carrier)  # {"traceparent": "00-...", "tracestate": ""}
    # Pass carrier as part of the task
    result = celery_app.send_task(
        "tasks.run_tool",
        kwargs={"tool_name": tool_name, "tool_args": tool_args, "otel_ctx": carrier},
    )
    return result.id

# In Celery worker: restore context from task args
@celery_app.task(name="tasks.run_tool")
def run_tool(tool_name: str, tool_args: dict, otel_ctx: dict):
    ctx = propagate.extract(otel_ctx)
    token = context.attach(ctx)
    try:
        with tracer.start_as_current_span("tool.execute") as span:
            span.set_attribute("tool.name", tool_name)
            return execute_tool(tool_name, tool_args)
    finally:
        context.detach(token)
```

**Pitfall 4: OTLP span attribute size limit causing silent trace truncation**

The OTLP default maximum attribute value length is 4096 bytes. The maximum span size is approximately 140KB in most collectors. A team stored the full 8000-token system prompt (about 32KB of text) in `gen_ai.request.system_prompt`. The collector silently dropped the attribute without an error — the span arrived at the backend with the attribute missing. The team debugged missing cost data for 2 days before discovering that `gen_ai.usage.input_tokens` was also being dropped (it was near the end of a very large span). Fix: truncate any prompt-derived attribute to 500 characters maximum; store the full prompt in a separate content store keyed by `sha256(prompt)[:16]`; configure the OTel Collector attribute processor with explicit size limits to log a warning rather than silently truncate.

---

## 11. Technologies & Tools

| Tool | Category | Key Facts |
|------|----------|-----------|
| `opentelemetry-sdk` (Python) | Core SDK | TracerProvider, BatchSpanProcessor, OTLP exporter; install: `opentelemetry-sdk opentelemetry-exporter-otlp` |
| `opentelemetry-instrumentation-openai` | Auto-instrumentation | Patches OpenAI Python SDK; emits `gen_ai.*` spans automatically; version: 0.1.x (2024) |
| OTel Collector | Telemetry pipeline | Receives OTLP, applies processors (batch, tail-sample, attribute-redact), exports to backends; run as sidecar or daemonset |
| Langfuse | LLM-native observability | OTel-compatible ingest; built-in eval datasets, LLM-as-judge, prompt versioning; open-source (self-host) or cloud |
| Arize Phoenix | LLM observability | OpenInference schema; root cause analysis; supports embedding drift visualization; open-source |
| Jaeger | Distributed tracing backend | OTLP ingest; good for trace search and service maps; free, open-source; 16GB storage for 7 days at 100K spans/day |
| Grafana Tempo | Distributed tracing backend | OTLP ingest; integrates with Grafana dashboards and Loki logs; cheap object storage (S3); recommended for high volume |
| Honeycomb | Cloud observability | OTLP ingest; excellent query language for trace analysis; $0.10/GB ingested; tail-based sampling built-in |
| Prometheus + Grafana | Metrics | OTel SDK emits OTLP metrics; Prometheus scrapes via OTel Collector Prometheus exporter; alert on `gen_ai_cost_usd_total > 50` |

### OTel Collector Configuration for LLM Apps

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: "0.0.0.0:4317"
      http:
        endpoint: "0.0.0.0:4318"

processors:
  batch:
    send_batch_size: 512
    timeout: 5s

  memory_limiter:
    check_interval: 1s
    limit_mib: 512

  # Redact sensitive LLM content from span attributes
  attributes/redact_llm_content:
    actions:
      - key: gen_ai.request.messages
        action: delete
      - key: gen_ai.response.text
        action: delete
      # Keep: gen_ai.system, gen_ai.request.model, gen_ai.usage.*, gen_ai.cost_usd

  tail_sampling:
    decision_wait: 30s
    num_traces: 100000
    policies:
      - name: keep-errors
        type: status_code
        status_code: {status_codes: [ERROR]}
      - name: keep-slow
        type: latency
        latency: {threshold_ms: 5000}
      - name: probabilistic-sample
        type: probabilistic
        probabilistic: {sampling_percentage: 10}

exporters:
  otlp/tempo:
    endpoint: "http://tempo:4317"
    tls:
      insecure: true
  otlp/langfuse:
    endpoint: "https://cloud.langfuse.com/api/public/otel"
    headers:
      Authorization: "Basic <base64(pk:sk)>"

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, attributes/redact_llm_content, tail_sampling]
      exporters: [otlp/tempo, otlp/langfuse]
```

---

## 12. Interview Questions with Answers

**Q: What are the OpenTelemetry GenAI semantic conventions and why were they introduced?**
The GenAI semantic conventions (`gen_ai.*` attribute namespace) were introduced by the OpenTelemetry Generative AI SIG (2024) to standardize how LLM calls are represented as spans. Before them, every observability vendor used a different schema: LangSmith used `prompts`/`completions`; Arize used `llm.token_count.prompt`; custom setups used arbitrary keys. The conventions define required attributes — `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `gen_ai.response.finish_reason` — so that any OTel-compatible backend can interpret LLM traces correctly without vendor-specific parsers.

**Q: How do you design a span for a streaming LLM response?**
Open one span for the entire streaming call at the start of inference. As token chunks arrive, emit one span event per chunk with the `gen_ai.content.completion` attribute and the current `time.time_ns()` timestamp. Record `gen_ai.ttft_ms` on the span when the first chunk arrives. After the final chunk, set `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `gen_ai.response.finish_reason`, and `gen_ai.cost_usd` as span attributes, then close the span. This produces one span with N events (one per chunk), from which you can compute TTFT, inter-token latency, and total duration — all from the event timestamps.

**Q: What is the W3C TraceContext header and why does it matter for LLM agents?**
The W3C TraceContext specification defines the `traceparent` HTTP header format: `00-<trace_id>-<span_id>-<flags>`. When a service makes an HTTP call to another service, it injects the current span's `trace_id` and `span_id` into this header. The receiving service extracts the header and creates its spans as children of the referenced parent span. For LLM agents, this is critical: when an orchestrator delegates to a sub-agent over HTTP, the `traceparent` header links sub-agent spans into the orchestrator's trace. Without it, sub-agent spans appear as unrelated orphan traces, making multi-hop agent debugging impossible.

**Q: How do you propagate trace context across a Celery or Kafka message queue boundary?**
OTel context is stored in Python `contextvars`, which are process-local. When a Celery task is serialized to Redis or a Kafka message is published, the context is not automatically included. The fix: before publishing, call `propagate.inject(carrier)` to serialize the current trace context into a dict; include that dict in the message payload or metadata. On the consumer side, call `propagate.extract(carrier)` to reconstruct the context and `context.attach(ctx)` to restore it before creating child spans.

**Q: What is tail-based sampling and when is it preferable to head-based sampling for LLM apps?**
Head-based sampling decides at the trace root before any child spans exist — it is simple but drops errors and slow traces at the same rate as fast, successful traces. Tail-based sampling buffers a complete trace for 30 seconds and decides after seeing all spans, enabling rules like "keep all ERROR traces and all traces with TTFT > 2 seconds; sample 5-10% of the rest." For high-traffic LLM production paths, tail-based is preferable because the most interesting traces (errors, slow responses, `content_filter` finish reasons) are guaranteed to be kept, while routine fast traces are aggressively sampled down.

**Q: What is the OTel OTLP span attribute size limit and how does it affect LLM instrumentation?**
The OTLP default maximum attribute value length is 4096 bytes (configurable); the maximum recommended span size is around 140KB. LLM prompts can be tens of thousands of tokens, easily exceeding these limits. Exceeding the limit causes the OTel Collector or SDK to silently truncate or drop the attribute — the span arrives at the backend with the attribute missing but no error is logged by default. Mitigations: (1) never store raw prompt content in span attributes — use a hashed prompt ID instead; (2) set explicit `AttributeLengthLimit` in the SDK: 500 characters for any string attribute; (3) configure the OTel Collector attribute processor to log a warning when truncation occurs.

**Q: How do you attribute LLM cost per tenant across multiple services?**
Instrument every LLM span with `tenant_id` from the request context (typically from a JWT claim or session). Use OTel Baggage to propagate `tenant_id` across service boundaries without reading from a database at each hop. Record `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, and `gen_ai.cost_usd` on every LLM span. In the backend (Tempo, Jaeger, Langfuse), query for spans grouped by `tenant_id` and summed by `gen_ai.cost_usd`. Alert when a tenant's rolling 24-hour cost exceeds a threshold. Typical numbers: GPT-4o at 3000 input / 300 output tokens per call × $5/1M input + $15/1M output = $0.0195/call; 1000 calls/day = $19.50/day per tenant.

**Q: What is the difference between OTLP and Zipkin as trace export formats?**
Zipkin is an older trace format with a simpler JSON schema; it predates the OpenTelemetry specification. OTLP (OpenTelemetry Protocol) is the native OTel wire format, using Protocol Buffers over gRPC (port 4317) or HTTP (port 4318). OTLP supports traces, metrics, and logs in one protocol; Zipkin only supports traces. OTLP preserves all span attributes, events, and links; Zipkin has limited attribute cardinality. For LLM apps, use OTLP: the `gen_ai.*` semantic convention attributes require OTLP's arbitrary key-value attribute model, which Zipkin does not support. Most modern backends (Tempo, Jaeger 1.35+, Honeycomb) accept OTLP natively.

**Q: How do you configure the OTel Collector batch processor to avoid data loss under load?**
The `BatchSpanProcessor` has three key parameters: `max_queue_size` (default 2048), `max_export_batch_size` (default 512), and `schedule_delay_millis` (default 5000ms). At 10,000 spans/second, the queue fills in 0.2 seconds if the exporter cannot keep up. Tuning: set `max_queue_size` to 20,000 for high-throughput services, reduce `schedule_delay_millis` to 1000ms, and ensure the exporter endpoint has < 200ms round-trip latency. Monitor `otelcol_processor_dropped_spans` — any nonzero value indicates back-pressure requiring horizontal Collector scaling.

**Q: How do you debug missing traces in a multi-service LLM application?**
Step 1: verify the `traceparent` header is present on all inter-service HTTP calls — add a middleware that logs the `traceparent` header on every inbound request. Step 2: check the OTel Collector's `otelcol_receiver_accepted_spans` and `otelcol_exporter_sent_spans` metrics — if accepted > sent, the tail sampler is dropping traces. Step 3: check sampling configuration — if head-based at 10%, 90% of traces are dropped at the root. Step 4: check the `max_queue_size` and `otelcol_processor_dropped_spans` for back-pressure drops. Step 5: verify the service's OTel SDK is initialized before any application code runs — a common mistake is calling `setup_otel_tracing()` after the first request handler is registered.

**Q: How do you correlate OTel traces with LLM evaluation scores?**
Add the evaluation score as a span attribute after evaluation completes. For synchronous evals (LLM-as-judge running inline): set `eval.faithfulness_score: 0.87` on the root span before it closes. For asynchronous evals running minutes later: store `trace_id` in your eval results table and join against the trace backend's API — Langfuse and Arize Phoenix both support adding scores to existing traces via API using `trace_id` as the lookup key, creating a unified view of latency, cost, and quality on the same record.

**Q: What is OTel Baggage and how does it differ from span attributes?**
OTel Baggage is a key-value store propagated alongside the `traceparent` header in the `baggage` HTTP header. Every downstream service receives it automatically without a database lookup. It is designed for low-cardinality context needed by all services: `tenant_id`, `user_id`, `feature_flag_variant`. Span attributes are local to one span — they are not forwarded. The pattern: inject `tenant_id` into Baggage at the API gateway; every downstream LLM span reads `baggage.get("tenant_id")` and sets it as a local span attribute for cost attribution.

**Q: How does PII in span attributes violate GDPR and what is the correct architecture?**
GDPR Article 17 (right to erasure) requires that you can delete all personal data for a user on request. Span data in Jaeger, Tempo, or Honeycomb is typically not individually addressable — you cannot delete one user's spans without deleting all spans for that time window. If raw prompts containing user names, medical history, or financial details are stored in span attributes, you cannot honor a deletion request. The correct architecture: (1) assign each prompt a `prompt_id = sha256(prompt)[:16]`; (2) store the full prompt in an encrypted content store (S3 + KMS) keyed by `prompt_id`, with per-user deletion support; (3) store only `prompt_id` on the span; (4) configure the OTel Collector to drop `gen_ai.request.messages` and `gen_ai.content.completion` attributes before export.

**Q: How do you alert on LLM quality regression using OTel telemetry?**
Define quality as a numeric span attribute: `eval.faithfulness_score` (0.0–1.0) recorded by an inline LLM-as-judge. Export this attribute as an OTel metric via the Collector's `spanmetrics` connector, which converts span attributes to Prometheus histograms. Create a Grafana alert: `histogram_quantile(0.5, eval_faithfulness_score_bucket) < 0.75` triggers a PagerDuty alert. The advantage over threshold-based latency alerts: you are alerting on answer quality degradation, not just slowness. A model routing change or a bad prompt change that degrades quality but not latency will be caught by this alert within the next evaluation window (typically 5 minutes if running inline evals on 10% of traffic).

**Q: What is the correct OTel span kind for an LLM call and why?**
Use `SpanKind.CLIENT` for LLM inference calls — the application is the client making a request to an external LLM API server. `SpanKind.CLIENT` signals to the backend that this span is an outbound call, which affects how service maps are rendered (the LLM provider appears as a downstream dependency). Use `SpanKind.SERVER` for spans created at your API gateway receiving inbound user requests. Use `SpanKind.INTERNAL` for spans representing internal computation (reranking, cost calculation). Use `SpanKind.PRODUCER`/`CONSUMER` for message queue producer/consumer spans (Celery tasks, Kafka messages). Misclassifying span kinds breaks service dependency maps and can cause the backend to miscalculate error rates.

**Q: How does the OTel `spanmetrics` connector enable cost monitoring dashboards without a dedicated billing pipeline?**
The `spanmetrics` connector in the OTel Collector converts span attributes into Prometheus metrics in real time. Configure it to extract `gen_ai.cost_usd` from every LLM span and emit a `gen_ai_cost_usd_total` counter metric labeled by `tenant_id`, `feature`, and `gen_ai.request.model`. Prometheus scrapes this counter from the Collector's metrics endpoint; Grafana queries it with `sum by (tenant_id) (rate(gen_ai_cost_usd_total[1h]))` to produce a real-time cost-per-tenant chart. Alert rule: `sum by (tenant_id) (increase(gen_ai_cost_usd_total[24h])) > 50` fires when any tenant exceeds $50/day. This eliminates the need for a separate billing pipeline — the trace data and the cost metric come from the same OTel instrumentation.

---

## 13. Best Practices

1. **Never log raw prompt or completion content in span attributes in production.** Prompt content in spans creates GDPR, HIPAA, and SOC 2 compliance exposure. Use a hashed prompt ID (`sha256(prompt)[:16]`) on the span and store the full content in an encrypted, access-controlled content store with per-user deletion support.

2. **Record `gen_ai.usage.input_tokens` and `gen_ai.usage.output_tokens` on every LLM span without exception.** These two attributes are the inputs to your cost allocation model. Missing them on even 5% of spans makes your cost dashboard unreliable and tenant billing potentially inaccurate.

3. **Record `gen_ai.ttft_ms` as a span attribute on every streaming LLM span.** Streaming P99 latency and TTFT P99 are different metrics and require separate monitoring. Users perceive TTFT as "response time." A 4-second TTFT with fast streaming thereafter feels slower than a 2-second TTFT with slower total throughput.

4. **Always propagate trace context across service and queue boundaries using W3C TraceContext.** For HTTP: use `propagate.inject(headers)`. For message queues (Celery, Kafka, SQS): serialize the carrier dict into message metadata. Without propagation, multi-agent traces fragment into unrelated orphan spans.

5. **Set an explicit attribute value length limit of 500 characters on LLM spans.** Configure `AttributeLengthLimit=500` in the SDK to prevent accidental large-attribute storage that hits the 4096-byte OTLP limit or 140KB span size limit, either of which causes silent attribute or span truncation.

6. **Use tail-based sampling in the OTel Collector for high-traffic production paths.** Keep 100% of error traces and traces with TTFT > 2 seconds. Sample 5-10% of successful, fast traces. This preserves the traces you need for debugging while reducing storage and ingest costs by 90%.

7. **Emit `gen_ai.cost_usd` as both a span attribute and an OTel metric counter.** The span attribute enables per-request analysis. The metric counter (via the Collector's `spanmetrics` connector) enables real-time Grafana dashboards and Prometheus alerting without querying the trace backend for aggregates.

8. **Configure the OTel Collector's `attributes` processor to redact `gen_ai.request.messages` and `gen_ai.content.completion` before export.** Make this a Collector-level policy, not an application-level policy. Application teams will forget; the Collector enforces uniformly across all services.

9. **Add `tenant_id` and `feature` to OTel Baggage at the API gateway and read them from Baggage in every downstream service.** This eliminates the need to re-extract tenant context from JWTs at each service hop, and ensures that every LLM span carries the attribution metadata required for cost reporting.

10. **Set up the `spanmetrics` connector alert `eval.faithfulness_score P50 < 0.75` before launching any LLM feature to production.** Quality alerts catch model changes, prompt regressions, and retrieval degradation that latency and error-rate alerts miss entirely.

---

## 14. Case Study

### LLM Gateway — Provider Routing and Cost Attribution

The `../design_llm_gateway.md` case study describes a gateway that routes LLM requests across OpenAI, Anthropic, and Google based on model capability, cost, and availability. OpenTelemetry is the foundation of the gateway's cost attribution system. Every inbound request creates a root `gateway.route` span carrying `tenant_id`, `feature`, and `request_model` (the model the tenant requested). The gateway's routing logic creates a child `gateway.select_provider` span that records `selected_model`, `routing_reason` (cost / capability / availability), and the candidate models that were considered. The downstream LLM call span carries the full `gen_ai.*` attribute set. After the call returns, the gateway reads `gen_ai.cost_usd` from the child span and emits it as a Prometheus counter labeled by tenant and feature. The Grafana dashboard shows actual cost vs budget per tenant in real time. The key design decision: cost attribution happens in the gateway's OTel span, not in a post-processing billing pipeline — this reduces billing lag from hours to seconds.

### ChatGPT-Scale — Tracing Streaming Responses and TTFT

The `../design_chatgpt.md` case study shows how a ChatGPT-scale system measures and optimizes time-to-first-token across 100 million daily requests. Every streaming inference call creates an OTel span with span events for each token chunk. The TTFT metric is extracted from these events in the OTel Collector's `spanmetrics` processor and emitted as a Prometheus histogram. The P99 TTFT alert threshold is 1.5 seconds — above that, on-call engineers investigate the inference cluster's KV cache hit rate, queuing delay, and GPU utilization. The OTel trace links the user-facing HTTP span to the inference span to the streaming event sequence, so engineers can drill into any high-latency request and see exactly where the delay occurred: pre-processing, queue wait, KV cache miss, or decode step. Without per-token span events, this drill-down is impossible.

### Autonomous SWE Agent — 50+ Tool Calls Across a Single Trace

The `../design_ai_coding_assistant.md` case study (the closest existing study to an autonomous SWE agent) shows the observability challenge of multi-step coding agents. A single agent run may invoke 50+ tool calls over 10-15 minutes: file reads, code execution, web searches, compiler runs. Without OTel trace propagation, each tool-call service produces an orphan trace. With OTel, the orchestrator creates a root `agent.run` span, and every sub-call propagates the `traceparent` header so all tool spans attach as descendants. The resulting trace tree — visible in Jaeger or Langfuse — shows the complete execution sequence with durations. Post-incident analysis for a "agent loop" bug (the agent invoked `read_file` 47 times in a cycle) was traced back to a specific tool span where a file-not-found error was swallowed silently. The `gen_ai.cost_usd` attributes across all LLM spans in the trace summed to $1.47 for that single agent run — surfaced immediately in the cost dashboard rather than discovered in the monthly billing statement.

### AI Search Engine — Retrieval-Rerank-Generate Pipeline

The `../design_ai_search_engine.md` case study uses OTel to profile a retrieval-rerank-generate (RRG) pipeline serving 500,000 daily search queries. The pipeline produces four spans per request: `vector_search` (embedding + ANN retrieval), `rerank` (Cohere reranker), `llm.chat` (answer synthesis), and the root `search.request` HTTP span. The OTel Collector's `spanmetrics` processor extracts P50/P95/P99 latency for each span type and emits them as separate Prometheus metrics. This revealed that reranking (Cohere API latency P95 = 380ms) was the pipeline bottleneck, not the LLM (GPT-4o-mini P95 = 310ms). Without per-span latency metrics, the dashboard showed only end-to-end latency, which masked the reranker as the source of variance. After switching to a local reranking model (latency P95 = 45ms), end-to-end P95 latency dropped from 920ms to 580ms — a 37% improvement discovered directly from the OTel span data.
