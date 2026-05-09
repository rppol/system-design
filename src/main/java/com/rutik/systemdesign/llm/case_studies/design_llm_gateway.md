# Case Study: Design an LLM Gateway

## Intuition

> **Design intuition**: An LLM Gateway is an API proxy + intelligent router — like an API gateway but with LLM-specific features: model routing by capability/cost, semantic caching (identical-meaning requests get the same cached response), and observability over token usage. It's the "middleware layer" that abstracts provider diversity from application teams.

**Key insight for this design**: Semantic caching is the highest-leverage optimization — unlike HTTP caching (exact URL match), semantic caching matches similar-meaning requests, potentially saving 20-40% of API costs for applications with repetitive query patterns. The routing logic (which model for which request) determines quality/cost balance.

---

## 1. Requirements Clarification

### Functional Requirements
- Unified API facade across multiple LLM providers (OpenAI, Anthropic, Google, self-hosted)
- Intelligent routing: route requests to appropriate model based on cost, latency, capability
- Automatic failover: if primary provider fails, route to secondary
- Semantic caching: cache and return cached responses for similar queries
- Rate limiting and quota management per tenant/API key
- Cost tracking and budget enforcement per client/project
- Request/response logging for audit, debugging, and evaluation
- Prompt injection detection and content filtering
- A/B testing: route traffic percentages to different models

### Non-Functional Requirements
- **Latency overhead**: < 10ms added latency (gateway should be transparent)
- **Availability**: 99.99% (gateway must be more reliable than individual providers)
- **Scale**: 100K req/sec peak; 1B tokens/day
- **Multi-tenant**: 500 enterprise clients with isolated quotas and logs
- **Compliance**: GDPR (log retention), SOC 2, optional HIPAA

---

## 2. Scale Estimation

### Traffic Estimates
```
Peak QPS: 100,000 req/sec
Average input: 500 tokens
Average output: 300 tokens
Daily tokens: 1B input + 600M output = 1.6B tokens/day

Provider distribution (example):
  OpenAI (GPT-4o): 40% of traffic
  Anthropic (Claude 3.5): 30%
  Google (Gemini 1.5 Pro): 15%
  Self-hosted (Llama 3): 15%

Request rate per provider at peak:
  OpenAI: 40,000 req/sec
  Anthropic: 30,000 req/sec
  Google: 15,000 req/sec
  Self-hosted: 15,000 req/sec
```

### Storage Estimates
```
Request logs:
  Per request: 2KB (prompt + response + metadata)
  Daily: 10M requests × 2KB = 20GB/day
  Retention: 90 days → 1.8TB total log storage

Semantic cache:
  Cache entry: ~3KB (query embedding + response text + metadata)
  Cache size target: 10M entries = 30GB
  Hit rate target: 20% → saves $0.20M/day at $0.01/request average

Metrics time series:
  100K metrics/sec → InfluxDB / Prometheus
  Cardinality: tenant × model × endpoint = 500 × 6 × 10 = 30,000 series
  Storage: 30K series × 90 days × ~500KB/series = 1.35TB
```

---

## 3. High-Level Architecture

```
Clients (Apps, Services)
    |
    v
[DNS / Load Balancer]
  - Anycast routing (global edge PoPs)
  - Health check failover
    |
    v
[LLM Gateway Cluster] (stateless; horizontally scalable)
  ┌───────────────────────────────────────┐
  │                                       │
  │  [Auth & Rate Limiting]               │
  │   - Validate API key → tenant         │
  │   - Check rate limit (Redis)          │
  │   - Check budget quota (Redis)        │
  │                                       │
  │  [Request Preprocessing]              │
  │   - Normalize API format              │
  │   - Prompt injection detection        │
  │   - Content safety check             │
  │   - PII detection + redaction         │
  │                                       │
  │  [Semantic Cache Check]               │
  │   - Embed query                       │
  │   - Check cache (Redis + FAISS)       │
  │   - If hit: return cached response    │
  │                                       │
  │  [Routing Engine]                     │
  │   - Model selection rules             │
  │   - A/B test assignment               │
  │   - Failover logic                    │
  │                                       │
  │  [Provider Adapters]                  │
  │   - OpenAI adapter                    │
  │   - Anthropic adapter                 │
  │   - Google adapter                    │
  │   - Self-hosted adapter (vLLM)        │
  │                                       │
  │  [Response Processing]                │
  │   - Normalize response format         │
  │   - Token counting + cost calculation │
  │   - Output safety filter             │
  │   - Cache write (if cacheable)        │
  │                                       │
  └───────────────────────────────────────┘
    |
    ├──→ [Async Logging Service]
    │       Kafka → S3 + ClickHouse
    │
    ├──→ [Metrics Service]
    │       Prometheus → Grafana
    │
    └──→ Response to client
```

---

## 4. Component Deep Dives

### 4.1 Routing Engine

```
Routing decisions are made hierarchically:

Level 1: Explicit model request
  If client specifies model_id in request → use that model
  Skip routing logic

Level 2: Routing policy (admin-configured per tenant)
  Tenant policies stored in PostgreSQL, cached in Redis:
  {
    "tenant_id": "acme_corp",
    "routing_policy": {
      "default_model": "gpt-4o",
      "fallback_model": "claude-3-5-sonnet",
      "cost_limit_per_request": 0.10,
      "latency_mode": "balanced",  // "fast" | "balanced" | "quality"
      "blocked_models": [],
      "a_b_tests": [
        {"name": "sonnet_vs_gpt4", "model": "claude-3-5-sonnet", "percent": 20}
      ]
    }
  }

Level 3: Smart routing based on request characteristics
  Rule-based:
    if request.has_images → vision_capable_models only
    if request.max_tokens > 100000 → long_context_models only
    if request.requires_tool_use → function_calling_models only
    if request.latency_sla == "fast" → tier_1_models only

  Cost-based routing:
    Estimate cost: (input_tokens × input_price + est_output_tokens × output_price)
    If estimate > threshold → route to cheaper model (Claude Haiku, Gemini Flash)

  Load-based routing:
    Monitor provider health metrics (latency p95, error rate)
    If provider latency > 3× baseline → shift traffic to alternatives
    Circuit breaker: if error rate > 5% → open circuit, route away

Routing example:
  Request: {model_hint: "any", task: "summarize", tokens_in: 2000, max_tokens: 200}
  → No vision, no long context needed
  → Cost estimate: $0.01 (within budget)
  → A/B test: 80% → GPT-4o, 20% → Claude 3.5 Sonnet
  → Load check: GPT-4o p95=800ms (normal) → route to GPT-4o
```

### 4.2 Semantic Caching

```
Semantic cache enables reusing responses for similar (not identical) queries.

Architecture:
  [Incoming query] → embed(query) → q_vec (1536-dim)
      |
      v
  FAISS index (10M cached query embeddings)
      |
  ANN search: nearest neighbor with similarity > 0.95 threshold
      |
  ┌──── Cache HIT (similarity > 0.95) ────┐
  │                                        │
  │  Return cached response                │
  │  Log: cache_hit=True                   │
  │  Update: cache hit count              │
  └────────────────────────────────────────┘
  OR
  ┌──── Cache MISS (similarity < 0.95) ────┐
  │                                         │
  │  Forward to LLM provider               │
  │  Store: {q_vec, response, metadata}    │
  │  Index: add q_vec to FAISS             │
  └─────────────────────────────────────────┘

Cache entry:
  {
    query_embedding: float[1536],
    original_query: str,           // for debugging
    response: str,
    model_used: str,
    tokens_used: int,
    created_at: timestamp,
    ttl: int,                      // seconds; 0 = permanent
    tenant_id: str                 // isolation
  }

Cache TTL strategy:
  Factual queries (low staleness risk): 24 hours
  News/current events: 30 minutes
  Code generation: no cache (personalized to context)
  Template-based queries: 7 days

Cache invalidation:
  Manual: admin can flush by tenant or query pattern
  TTL expiry: automatic via Redis TTL
  Semantic similarity: can't invalidate similar-but-different (by design)

When NOT to cache:
  Requests with temperature > 0.7 (high randomness → different responses expected)
  Requests with user-specific context in system prompt
  Requests with current time/date dependencies
  Requests flagged as "no-cache" in metadata

Expected hit rate: 15-25% for enterprise use cases
  (many repeat analytical queries: "summarize this month's reports")
```

### 4.3 Failover and Circuit Breaker

```
Provider health monitoring (per provider, per region):
  Metrics tracked (last 60 seconds):
    - Request success rate
    - P50, P95, P99 latency
    - Token throughput
    - Error types: timeout, rate_limit, server_error, auth_error

Circuit breaker states:
  CLOSED (normal):
    All requests forwarded normally
    Monitor metrics window

  OPEN (provider down):
    Triggered when: error_rate > 5% OR p95_latency > 3× baseline for 30s
    All requests immediately routed to fallback
    No requests sent to failed provider
    Re-check after 60 seconds → move to HALF_OPEN

  HALF_OPEN (testing recovery):
    Send 10% of traffic to primary provider
    If success → move to CLOSED
    If still failing → move back to OPEN

Failover chain (configurable per tenant):
  Primary: gpt-4o (OpenAI)
  Secondary: claude-3-5-sonnet (Anthropic)
  Tertiary: gemini-1.5-pro (Google)
  Last resort: llama-3-70b (self-hosted)

Format translation on failover:
  OpenAI format:  {"messages": [{"role": "user", "content": "..."}]}
  Anthropic format: {"messages": [{"role": "user", "content": "..."}],
                     "max_tokens": 1024}
  Gateway handles translation transparently
  Client sees uniform response format regardless of provider used

Retry strategy:
  On timeout: retry once with same provider (network hiccup)
  On rate limit: immediate failover (don't retry limited provider)
  On server error (500): retry once, then failover
  Max latency budget: respect client timeout header
```

### 4.4 Rate Limiting and Budget Enforcement

```
Two types of limits:

1. Rate limiting (requests per time window):
   Implementation: Token bucket algorithm in Redis
   Buckets per tenant:
     requests_per_minute: 1,000 (configurable)
     tokens_per_minute: 1,000,000
     requests_per_day: 100,000

   Redis implementation:
     lua_script = """
       local key = KEYS[1]
       local limit = tonumber(ARGV[1])
       local current = redis.call('INCR', key)
       if current == 1 then
         redis.call('EXPIRE', key, 60)
       end
       if current > limit then
         return 0
       end
       return 1
     """
   Atomic: rate limit check and increment in single atomic operation

2. Budget enforcement (cost per period):
   Track running cost per tenant in Redis:
     budget:{tenant_id}:{month} → cumulative cost
   On each request:
     estimated_cost = estimate_tokens(request) × model_price
     If running_cost + estimated_cost > monthly_budget:
       Return 429: {"error": "Monthly budget exceeded", "budget": 100, "used": 99.87}
   On response:
     actual_cost = actual_tokens × model_price
     redis.incrby(budget_key, actual_cost_in_microcents)

   Budget alerts:
     50% used → email alert
     80% used → email + Slack alert
     95% used → block non-critical requests, notify admins
     100% used → block all requests until next period
```

### 4.5 Observability Pipeline

```
Every request generates rich telemetry:

Request log entry:
  {
    request_id: uuid,
    tenant_id: "acme_corp",
    api_key_id: "key_abc123",
    timestamp: "2024-03-15T10:30:00Z",
    model_requested: "gpt-4o",
    model_used: "gpt-4o",       // may differ if routed
    provider: "openai",
    input_tokens: 523,
    output_tokens: 287,
    total_tokens: 810,
    cost_usd: 0.0153,
    latency_ms: 1240,
    ttft_ms: 320,               // time to first token
    cache_hit: false,
    routing_reason: "default",
    error: null,
    // content logged only if tenant opts in:
    request_hash: "sha256:...", // hash for de-duplication
    safety_flags: [],
    a_b_variant: "control"
  }

Pipeline:
  Gateway → Kafka topic: llm-requests (throughput: 100K events/sec)
        ↓
  Kafka consumer → ClickHouse (analytical queries, cost reports)
  Kafka consumer → S3 (raw log archive, 90-day retention)
  Kafka consumer → Real-time metrics aggregator → Prometheus

Dashboards (Grafana):
  - Request volume (by tenant, model, time)
  - Cost burn rate (daily/monthly actual vs budget)
  - Latency percentiles (P50/P95/P99 by provider, model)
  - Cache hit rate (cost savings from caching)
  - Error rate (by provider, error type)
  - Token distribution (histogram of request sizes)
  - Model split (traffic share per model)

Alerting:
  Error rate > 1%: page on-call
  Provider latency degradation: Slack alert
  Tenant budget > 80%: email tenant admin
  Cache hit rate drops 5%+ week-over-week: investigate
```

### 4.6 Prompt Injection Detection

```
Gateway as defense-in-depth layer against prompt injection:

Detection patterns:
  1. System prompt override attempts:
     Patterns: "Ignore previous instructions", "Forget your system prompt",
               "You are now DAN", "Your new instructions are"
     Action: block + log

  2. Indirect injection in user-provided content (RAG context, emails):
     Content contains LLM instructions trying to alter behavior
     Pattern matching + small classifier model
     Action: sanitize or flag for review

  3. Context window attack:
     Extremely long inputs trying to push system prompt out of context
     Detection: input_tokens > configured_limit
     Action: truncate or reject

Implementation:
  Option A: Regex patterns (fast, < 1ms, catches known patterns)
  Option B: Llama Guard (ML classifier, 10-30ms, catches novel attacks)
  Combined: regex first, then ML classifier for ambiguous cases

  For enterprise HIPAA tenants: all inputs scanned
  For general API: scan 10% of requests (cost vs risk trade-off)

false positive management:
  Alert on: high false positive rate (>2%)
  Tuning: maintain tenant-specific allowlists
  Bypass: admins can mark specific request patterns as safe
```

---

## 5. API Design

```
Unified API (OpenAI-compatible for drop-in replacement):

POST /v1/chat/completions
Headers:
  Authorization: Bearer {gateway_api_key}
  X-Tenant-ID: acme_corp      (optional; derived from API key)
  X-Gateway-Options: {...}    (routing hints, cache behavior)

Request body (OpenAI-compatible):
{
  "model": "gpt-4o",          // or "auto" for smart routing
  "messages": [...],
  "stream": true,
  "max_tokens": 1000,
  // Gateway extensions:
  "x_gateway": {
    "fallback_models": ["claude-3-5-sonnet"],
    "cache": true,
    "budget_limit_usd": 0.10
  }
}

Response (same as OpenAI format):
{
  "id": "chatcmpl-...",
  "model": "gpt-4o",          // actual model used (may differ from requested)
  "choices": [...],
  "usage": {
    "prompt_tokens": 500,
    "completion_tokens": 300,
    "total_tokens": 800,
    // Gateway extensions:
    "x_gateway": {
      "cost_usd": 0.0153,
      "cache_hit": false,
      "routing_reason": "a_b_test",
      "provider": "openai"
    }
  }
}

Streaming: SSE format, compatible with OpenAI streaming
  No format change; gateway streams tokens directly as received
```

---

## 6. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| API format | OpenAI-compatible | Custom format | Drop-in replacement; minimal client changes |
| Semantic cache | Embedding similarity | Exact match hash | ~20% hit rate vs 2%; semantic queries benefit |
| Cache similarity threshold | 0.95 | Lower (0.85) | 0.95 prevents wrong cached responses; 0.85 too risky |
| Rate limiting | Redis token bucket | DB-based | Microsecond latency; atomic operations |
| Circuit breaker | Per-provider | Per-model | Providers fail as a whole; models within same provider stay up |
| Routing | Rule-based hybrid | ML-based | Interpretable; debuggable; ML adds complexity for marginal gain |
| Logs | Kafka + ClickHouse | Direct DB write | Async logging; no latency impact; analytical DB for queries |

---

## 7. Cost Impact Analysis

```
Benefits of the gateway:

1. Semantic caching (20% hit rate):
   Daily cost without cache: 10M requests × $0.015 avg = $150,000/day
   With 20% cache hit: 10M × 80% × $0.015 = $120,000/day
   Savings: $30,000/day = $10.95M/year

2. Smart routing (route 60% to cheaper models):
   Without routing: all requests → GPT-4o ($0.020 avg)
   With routing: 40% GPT-4o + 60% Claude Haiku ($0.002 avg)
   Cost: 10M × (40% × $0.020 + 60% × $0.002) = $92,000/day
   Savings vs all-GPT-4o: $108,000/day = $39.4M/year

3. Budget enforcement:
   Prevents runaway costs from bugs, prompt injection, excessive usage
   Estimated: saves 5-10% from unexpected usage spikes

Total gateway ROI: well above gateway operating cost
Gateway cost to run: ~$5,000/day (infrastructure, team)
Annual savings: ~$50M → strong positive ROI
```

---

## 8. Interview Discussion Points

**The 10ms latency requirement is challenging.** Every synchronous check in the request path (auth, rate limit, cache, safety filter) adds latency. The solution: run fast checks synchronously (Redis lookup: < 1ms), async checks where possible (logging, detailed safety analysis), and use connection pooling to LLM providers to amortize connection setup.

**Why not just use LiteLLM or Portkey?** Open-source gateways (LiteLLM) handle basic routing and format translation. Building in-house makes sense when you need: custom routing logic tightly integrated with your business rules, HIPAA/compliance requirements (data never leaves your VPC), custom semantic caching tuned to your query patterns, or deep integration with your observability stack.

**Semantic cache cache poisoning.** If a cached response is wrong (hallucination) and many similar queries hit the cache, the wrong answer spreads. Mitigation: TTL ensures old responses expire, A/B test cache vs. fresh to measure quality, allow users to "thumbs down" which invalidates the cache entry.

**The failover latency penalty.** Detecting failure + switching provider takes 100-200ms. Combined with retry logic, some requests might take 2-5× normal latency during provider incidents. For real-time applications, pre-warm connections to all providers and use shadow traffic (send 1% of requests to backup provider) to keep connections warm.

**Budget enforcement granularity.** Per-tenant monthly budget is the minimum. Production systems also need per-project, per-application, per-user granularity. Hierarchical budgets: tenant limit > project limit > user limit. Each level can set its own limit (must be ≤ parent limit).
