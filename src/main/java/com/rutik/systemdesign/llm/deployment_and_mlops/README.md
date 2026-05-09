# Deployment & MLOps

## 1. Concept Overview

Deploying LLMs in production requires solving problems that don't exist with traditional software or even traditional ML: massive GPU memory requirements, extreme inference latency variability, multi-dimensional cost optimization (compute vs. API costs), and the challenge of monitoring outputs that are free-form text rather than discrete predictions.

LLM MLOps encompasses model serving infrastructure, cost management, monitoring for quality regressions, model routing, A/B testing, and the "data flywheel" — using production data to continuously improve the model. It's where the boundary between software engineering and ML engineering blurs most heavily.

---

## Intuition

> **One-line analogy**: LLM deployment is like running a restaurant — you need to manage capacity (GPU servers), quality (model outputs), cost (GPU hours), and freshness (model updates) all simultaneously.

**Mental model**: Deploying a model isn't just starting a server. You need GPU instances (expensive, hard to scale quickly), a load balancer routing to multiple model replicas, monitoring for quality regressions (LLM outputs are text, not simple metrics), cost attribution (which user/team is consuming tokens), fallback routing (if main model fails, route to backup), and a pipeline to continuously improve the model from production data. Each of these is a solved problem in traditional software but requires LLM-specific adaptations.

**Why it matters**: A model that works in development can fail in production due to distribution shift, adversarial inputs, or cost overruns. MLOps for LLMs is the discipline that keeps production systems reliable, cost-effective, and continuously improving. Without it, even great models fail at scale.

**Key insight**: LLM monitoring is fundamentally different from traditional ML monitoring — you can't compute accuracy on free-form text outputs. LLM-as-judge, user feedback signals, and embedding drift detection replace traditional accuracy metrics.

---

## 2. Core Principles

- **GPU cost dominates**: For self-hosted LLMs, GPU compute is typically 60-80% of total cost. Every optimization decision flows from this.
- **Observability is non-negotiable**: LLM outputs are non-deterministic and hard to validate. You need extensive logging to understand failures.
- **Latency SLAs are user-facing**: Users notice latency. TTFT (Time to First Token) < 1 second is a hard requirement for conversational applications.
- **Gradual rollout**: LLMs can silently degrade (sycophancy, capability regression, safety issues). Always use A/B testing for major changes.
- **Prompts are code**: Treat prompt changes with the same rigor as code changes: version control, review, staged rollout.

---

## 3. Serving Architecture Patterns

### 3.1 API Gateway Pattern

A dedicated gateway handles all LLM traffic before reaching models:

```
Client Requests
     |
     v
[LLM Gateway]
  ├── Authentication & Authorization
  ├── Rate Limiting (per user/org/tier)
  ├── Request Validation (length, content filtering)
  ├── Prompt Template Injection (add system prompts)
  ├── Model Routing (route to appropriate model)
  ├── Caching (exact match cache, semantic cache)
  ├── Logging & Tracing
  └── Cost Tracking (tokens/$ per user)
     |
     v
[Model Serving Tier]
  ├── GPT-4o (complex queries)
  ├── GPT-4o-mini (simple queries)
  ├── Self-hosted 7B (high-volume, low-stakes)
  └── Specialized models (code, embedding, etc.)
```

### 3.2 Model Routing

Route queries to the appropriate model based on complexity:

```python
def route_request(query: str, user_tier: str) -> str:
    # Cost-aware routing
    if user_tier == "free":
        return "gpt-4o-mini"  # Cheap model for free tier

    # Complexity estimation
    complexity = estimate_complexity(query)

    if complexity < 0.3:
        return "gpt-4o-mini"   # Simple Q&A
    elif complexity < 0.7:
        return "gpt-4o"        # Medium complexity
    else:
        return "o1"            # Complex reasoning

def estimate_complexity(query: str) -> float:
    # Options:
    # 1. Length-based: longer = more complex (crude but fast)
    # 2. Keyword-based: "step by step", "prove", "analyze" → high complexity
    # 3. Small classifier model: 10ms latency, route based on predicted complexity
    # 4. Token confidence from cheap model: low confidence → escalate to expensive model
```

### 3.3 Semantic Caching

Cache LLM responses by semantic similarity of queries:

```python
class SemanticCache:
    def __init__(self, similarity_threshold=0.95):
        self.cache = {}  # query_embedding → response
        self.vector_store = VectorStore()
        self.threshold = similarity_threshold

    def get(self, query: str) -> Optional[str]:
        embedding = embed(query)
        similar = self.vector_store.search(embedding, top_k=1)
        if similar and similar[0].score > self.threshold:
            return self.cache[similar[0].id]
        return None

    def put(self, query: str, response: str):
        embedding = embed(query)
        key = self.vector_store.insert(embedding)
        self.cache[key] = response
```

Hit rates for common applications:
- FAQ chatbots: 40-60% cache hit rate (highly repetitive queries)
- General Q&A: 10-20% cache hit rate
- Code generation: <5% cache hit rate (unique code inputs)

---

## 4. Architecture Diagrams

### Full LLM Production Stack
```
                    Client Applications
                    (Web, Mobile, API)
                           |
                           v
                    [Load Balancer]
                    (Nginx / AWS ALB)
                           |
                           v
                    [LLM Gateway Cluster]
                    Auth | Rate Limit | Cache
                    Route | Log | Cost Track
                           |
              +------------+------------+
              |            |            |
              v            v            v
       [vLLM Cluster] [OpenAI API] [Embedding]
       Self-hosted     (overflow)   (Dedicated)
       models                       Servers
              |
       [GPU Pool]
       A100/H100
       Auto-scaling
              |
              v
     [Monitoring Stack]
     Latency | Cost | Quality
     Drift | Error rates
```

### Deployment Pipeline
```
Development
  └── Prompt iteration (LangSmith)
      |
      v
Staging
  └── Automated evaluation suite
      │── MMLU / domain benchmarks
      │── Safety tests
      │── Regression tests vs. current production
      Pass threshold (e.g., no regression > 2%)
      |
      v
Production Canary (5% traffic)
  └── Monitor: latency, error rate, user satisfaction
      Run for 24-48 hours
      |
      v
Production Rollout (50% → 100%)
  └── Gradual traffic shift
      Automatic rollback if degradation detected
```

---

## 5. How It Works — Detailed Mechanics

### Cost Estimation and Optimization

```
Self-hosted model cost breakdown:
  GPU cost:           60-80% (H100 at $3-4/hr)
  Storage (model):    5-10% (SSD for model weights)
  Network egress:     5-15% (output tokens sent to clients)
  CPU/memory:         5-10% (gateway, preprocessing)

Cost per token estimation:
  H100 80GB @ $3/hr
  Throughput: 1000 tokens/sec (7B model, continuous batching)
  Cost: $3/hr / (1000 tokens/sec × 3600 sec) = $0.00000083 per token
  = $0.00083 per 1000 tokens

  Compare to OpenAI gpt-4o-mini: $0.15/1M input, $0.60/1M output
  Self-hosted 7B: ~$0.83 per 1M tokens = 40% cheaper for output
  But GPT-4o-mini is much higher quality than 7B

  Sweet spot: use self-hosted models where quality is sufficient,
              API models where quality is critical
```

### Monitoring LLM Quality

Traditional ML metrics (accuracy, F1) don't apply to free-form LLM output. Use:

```
1. Human feedback (gold standard):
   Thumbs up/down, ratings, corrections
   Expensive but most reliable
   Sample 1-2% of production traffic

2. LLM-as-judge (automated):
   Use GPT-4 to score responses on:
   - Helpfulness (1-5)
   - Accuracy (1-5)
   - Safety (0 or 1)
   - Groundedness (for RAG: 0 or 1)
   Cost: ~$0.01 per evaluation
   Suitable for: large-scale automated evaluation

3. Task-specific metrics:
   Code: execution rate, test pass rate
   SQL: execution success, result correctness
   Summarization: ROUGE, BERTScore
   RAG: faithfulness, answer relevance (RAGAS)

4. Behavioral metrics:
   Refusal rate (are we refusing too much or too little?)
   Response length distribution (shift indicates prompt regression)
   Tool call success rate (for agents)
   Hallucination rate (for RAG, check against sources)
```

### Auto-Scaling Strategy

```
Metric-based scaling:
  Scale up trigger: GPU utilization > 80% for 3 consecutive minutes
  Scale down trigger: GPU utilization < 30% for 10 minutes

Queue-based scaling:
  Scale up: request queue depth > 50
  Scale down: request queue depth = 0 for 5 minutes

Scheduled scaling:
  Pre-scale for known traffic patterns (weekday 9am, product launches)

Cold start problem:
  LLM model loading: 30s for 7B, 3min for 70B
  Solutions:
    Keep minimum 1 replica always running
    Pre-warm with dummy requests
    Use model caching on persistent volumes (avoid re-download)
```

### Observability Stack

```
Request tracing (OpenTelemetry):
  trace_id → span for each component
  LLM call: input_tokens, output_tokens, model, latency, cost
  Cache: hit/miss, latency savings
  Routing: which model selected, routing reason

Metrics (Prometheus + Grafana):
  request_count, error_rate, latency_p50/p99
  cost_per_request, tokens_per_second
  gpu_utilization, gpu_memory_used
  cache_hit_rate

Logs (structured JSON):
  { "trace_id": "...", "model": "gpt-4o", "user_id": "...",
    "input_tokens": 500, "output_tokens": 150, "latency_ms": 1200,
    "cost_usd": 0.0015, "cache_hit": false, "safety_flag": false }

Quality dashboard:
  Daily: helpfulness score (LLM-as-judge), refusal rate, error rate
  Weekly: regression tests vs. baseline, human eval sample
  Monthly: A/B test results, model upgrade candidates
```

---

## 6. Real-World Examples

### OpenAI's Infrastructure
- Thousands of H100s across Azure regions
- Custom model routing: trivial queries → smaller cached model; complex → full model
- Semantic caching for common prompts at scale
- Real-time cost tracking per API key; rate limiting by tier
- Dashboard shows per-model latency and utilization in real time

### Anthropic's Claude Deployment
- Multi-region for latency (US, EU, APAC)
- Progressive rollouts for new Claude versions (internal → beta → production)
- Extensive safety monitoring: harmful output rate, refusal calibration
- A/B testing of system prompt changes across user cohorts

### Netflix LLM Platform
- Internal LLM gateway for all ML teams
- Model catalog: approved models + their cost/quality characteristics
- Shared observability: all teams' LLM usage in one dashboard
- Chargeback by team: each team sees their LLM cost
- Fine-tuned models for specific use cases (content recommendation copy, A/B test variants)

---

## 7. Tradeoffs

| Decision | Self-Hosted | Managed API |
|----------|------------|-------------|
| Cost at scale | Low (amortized GPU) | High ($0.01-0.10/1K tokens) |
| Setup complexity | High | None |
| Latency | Low (no external calls) | Variable (network + queuing) |
| Model quality | Limited to open models | Best models available |
| Data privacy | Full control | Vendor dependency |
| Scaling | Manual/complex | Auto (pay per use) |

| Serving Strategy | Throughput | Latency | Cost |
|-----------------|-----------|---------|------|
| Single replica | Low | Low | Medium |
| Horizontal scale | High | Low | High |
| Semantic cache | Medium | Very low (cache hits) | Low |
| Model routing | Medium | Low | Low (uses cheap model) |

---

## 8. When to Use / When NOT to Use

### Self-Host When:
- Processing >10M tokens/day (economies of scale justify GPU cost)
- Data privacy requirements prevent external API usage
- Need model customization beyond API capabilities
- Need guaranteed SLAs not available from API providers

### Use Managed API When:
- <1M tokens/day (API is cheaper than idle GPU time)
- Need cutting-edge model quality (GPT-4o, Claude 3.5)
- Don't have ML infrastructure expertise
- Fast time-to-market is the priority

---

## 9. Common Pitfalls

1. **No prompt versioning**: Changing system prompts without version control makes it impossible to diagnose regressions.
2. **Ignoring TTFT**: Optimizing throughput but not latency; users perceive TTFT as the response time.
3. **No cost budgets**: Allowing runaway API costs from buggy agents or large batches.
4. **Monitoring only technical metrics**: Tracking p99 latency but not output quality; a model can be fast and wrong.
5. **Cold start in auto-scaling**: Scaling to zero to save money but 70B models take 3+ minutes to load. Set minimum replicas = 1.
6. **No rate limiting per user**: One user floods the system with requests, degrading experience for others.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **LangSmith** | LLM observability | Traces, evaluations, prompt management |
| **Langfuse** | Open-source observability | Self-hostable alternative to LangSmith |
| **Helicone** | LLM proxy + analytics | Drop-in proxy; zero code change |
| **Arize Phoenix** | ML + LLM monitoring | Good for RAG evaluation |
| **Prometheus + Grafana** | Metrics | GPU utilization, latency, throughput |
| **OpenTelemetry** | Distributed tracing | Trace across gateway → model → tools |
| **LiteLLM** | Multi-provider gateway | Route between OpenAI, Anthropic, local |
| **Portkey** | LLM gateway | Routing, caching, fallbacks |
| **Modal** | Serverless GPU | Auto-scale LLM inference on demand |
| **Ray Serve** | Model serving | Multi-model, auto-scaling |

---

## 11. Interview Questions with Answers

**Q: How would you design an LLM gateway for a large enterprise?**
A: Key components: (1) Authentication — API key management per team/user with rate limits; (2) Request routing — complexity-based routing to appropriate model (cheap for simple, expensive for complex); (3) Semantic caching — cache responses for similar queries; (4) Cost tracking — per-team, per-user cost attribution and budgets; (5) Observability — structured logging of every request/response with cost, latency, model; (6) Guardrails — input/output filtering before/after LLM; (7) Fallback — if primary model is down/slow, route to fallback. Deploy as a horizontal service with load balancing.

**Q: How do you monitor LLM output quality in production?**
A: Multi-layered approach: (1) Automated metrics — LLM-as-judge scoring helpfulness/safety on 1-5% sample; task-specific metrics (code execution rate, SQL validity); (2) User signals — thumbs up/down, session continuation, correction edits; (3) Regression benchmarks — run standard benchmarks (MMLU, domain-specific) on every model/prompt change; (4) Behavioral monitors — track refusal rate, response length distribution, hallucination rate for RAG. Alert when metrics deviate >2σ from baseline.

**Q: What is the data flywheel for LLM products?**
A: The data flywheel: production use → more user data (conversations, feedback) → better training data → better model → better product → more users → more data. Specifically: collect user feedback (explicit ratings, implicit signals like edits/regenerations) → filter for high-quality examples → use for fine-tuning or RLHF → deploy better model → repeat. This compounds over time; companies with more users get better data and faster improvement cycles.

**Q: How would you implement model A/B testing for LLMs?**
A: (1) Traffic splitting — route N% of users to model B using consistent hashing on user_id; (2) Metric definition — define primary metric (task completion, user satisfaction score, cost efficiency) and guardrails (no safety regression, no latency increase >20%); (3) Sample size — use statistical power analysis to determine minimum sample size; (4) Duration — run for at least 1 week to capture weekly patterns; (5) Analysis — compare primary metric with statistical significance test (Mann-Whitney for non-normal distributions); (6) Rollback trigger — define automatic rollback if guardrail breached.

---

## 12. Best Practices

1. **Version control all prompts** — treat system prompts as code; use semantic versioning.
2. **A/B test every major change** — prompt changes, model upgrades, parameter tuning.
3. **Set per-user rate limits** — protect the system from noisy neighbors.
4. **Pre-warm instances before peak traffic** — scale up 15 minutes before known traffic spikes.
5. **Log everything, retain 30 days** — LLM debugging requires historical context.
6. **Define and track quality SLIs** — e.g., "95% of responses score ≥ 4/5 on helpfulness."
7. **Alert on quality metrics, not just infrastructure metrics** — fast but wrong responses are silent failures.

---

## 13. Case Study: Production LLM Platform for 10M Users

**Context:** Consumer app has 10M monthly active users, 100K daily active users using an LLM feature (writing assistant).

**Traffic profile:**
- Peak: 500 requests/min (10am-2pm weekdays)
- Average: 150 requests/min
- Query complexity: 60% simple, 30% medium, 10% complex
- Average input: 500 tokens; average output: 300 tokens
- Context: 80% of queries don't require chat history

**Architecture:**
```
Traffic: 500 req/min × 800 avg tokens = 400K tokens/min

Model routing:
  60% (simple) → gpt-4o-mini: $0.60/1M output tokens
  30% (medium) → gpt-4o: $15/1M output tokens
  10% (complex) → o1-mini: $12/1M output tokens

Semantic cache (FAQ questions):
  Estimated 25% cache hit rate → 25% cost reduction

Monthly cost estimate:
  Simple (300K output tokens/min × 60% × 43,200 min/month × 0.25 missed cache):
    = 195M tokens × $0.60/1M = $117
  Medium:
    = 97.2M tokens × $15/1M = $1,458
  Complex:
    = 32.4M tokens × $12/1M = $389
  Total: ~$1,964/month

Observability:
  LangSmith: traces for debugging
  Prometheus + Grafana: latency (p50=1.2s, p99=3.5s), cost, error rate
  LLM-as-judge: 5% random sample evaluated daily
  Automated alerts: quality score < 3.8/5 for 30 consecutive evals
```
