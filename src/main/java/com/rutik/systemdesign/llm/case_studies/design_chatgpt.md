# Case Study: Design ChatGPT

## Intuition

> **Design intuition**: ChatGPT is a conversational interface over a streaming LLM API — the complexity lies not in the model calls but in the serving infrastructure: continuous batching to handle millions of concurrent users, KV cache management for conversation history, and the real-time streaming architecture that makes responses feel instant.

**Key insight for this design**: Multi-turn conversation requires keeping the full history in the context window, which grows linearly per turn. At scale, conversation history management, token cost optimization, and KV cache efficiency are the dominant engineering challenges — not the model itself.

---

## 1. Requirements Clarification

### Functional Requirements
- Users can send text messages and receive AI-generated responses in a conversational interface
- Maintain multi-turn conversation context (chat history)
- Support streaming responses (tokens appear as generated)
- User authentication, conversation management (save, load, delete)
- Multiple model tiers (GPT-3.5 for free, GPT-4 for paid)
- Plugin/tool support (browsing, code execution, image generation)
- Image input support (GPT-4 Vision)

### Non-Functional Requirements
- **Latency**: TTFT (time to first token) < 1s; streaming at human-readable pace (~30 tokens/sec)
- **Availability**: 99.9% uptime (8.7 hours downtime/year)
- **Scale**: 100M+ daily active users; 10M concurrent conversations at peak
- **Throughput**: 1B+ tokens generated per day
- **Cost**: Efficient inference to keep costs manageable at massive scale

### Out of Scope
- Model training pipeline (only inference)
- Safety training / RLHF pipeline
- Internal tooling and admin interfaces

---

## 2. Scale Estimation

### Traffic Estimates
```
Daily Active Users: 100M
Messages per user per day: 5 (avg)
Total messages per day: 500M
Peak QPS: 500M / 86,400 × 3 (peak factor) ≈ 17,000 req/sec

Token estimates per request:
  Average input: 500 tokens
  Average output: 300 tokens
  Total: 800 tokens per request

Daily tokens processed: 500M × 800 = 400B tokens/day
Peak token throughput: 17,000 req/sec × 800 tokens = 13.6M tokens/sec
```

### Storage Estimates
```
Conversation history per user:
  100 messages × 1KB average = 100KB per conversation
  10 conversations per user = 1MB per user
  100M users = 100TB of conversation data

Vector embeddings for semantic search:
  Each message → 1536-dim float32 embedding = 6KB
  500M messages/day = 3TB of embeddings/day (not retained long-term)
```

### Infrastructure Estimates
```
GPU requirements for inference:
  GPT-4 (1.76T parameter MoE, ~220B active):
    1 inference request: ~500ms (A100 80GB)
    Need: 17,000 req/sec × 0.5s = 8,500 concurrent requests

  A100 GPU handles: ~50 concurrent requests with batching
  Total A100 GPUs: ~170 for GPT-4 (paid tier, ~20% of traffic)

  GPT-3.5 (175B dense):
    1 inference: ~100ms on A100
    For 80% of traffic: 13,600 req/sec × 0.1s = 1,360 concurrent
    A100 handles ~200 concurrent
    GPT-3.5 GPUs: ~7 GPUs... but with PagedAttention, batch efficiently

  Realistic estimate: 1,000+ A100s total (with redundancy, routing, batching)
```

---

## 3. High-Level Architecture

```
Client (Web/Mobile/API)
         |
         v
[CDN / Edge] (static assets, geographic distribution)
         |
         v
[API Gateway / Load Balancer]
  - Authentication (JWT tokens)
  - Rate limiting (free: 20 msg/hr, paid: unlimited)
  - Request routing to services
         |
    _____|_____
   |           |
   v           v
[Auth         [Chat
 Service]      Service]
 - Login       - Session management
 - OAuth2      - Message history
 - JWT         - Conversation CRUD
   |           |
   |           v
   |    [Context Builder]
   |    - Retrieve conversation history
   |    - Apply context window limits
   |    - Format system prompt + history
   |           |
   |           v
   |    [Model Router]
   |    - Select model (GPT-3.5 / GPT-4 / GPT-4V)
   |    - Apply system prompts
   |    - Queue management
   |           |
   |           v
   |    [Inference Cluster]
   |    - vLLM with PagedAttention
   |    - Multi-GPU model sharding
   |    - Streaming token generation
   |           |
   |           v
   |    [Output Processing]
   |    - Content filtering (safety)
   |    - Markdown formatting
   |    - Token counting + billing
   |           |
   +-----+-----+
         |
         v
[Streaming Response via SSE/WebSocket]
         |
         v
      Client
```

---

## 4. Component Deep Dives

### 4.1 Context Builder

```
Conversation History Management:

User sends: "What was the capital of France I asked about?"
         |
         v
[Retrieve conversation from DB]
  messages = fetch_last_n_messages(conversation_id, n=50)
         |
         v
[Apply context window limit]
  model_context = 128,000 tokens (GPT-4)
  reserve_output = 2,000 tokens
  available = 126,000 tokens

  Truncation strategy:
  1. Always include system prompt (1,000 tokens)
  2. Always include last 10 messages
  3. Fill remaining budget with oldest → newest messages
  4. If still over limit: summarize older context with LLM
         |
         v
[Format messages]
  [
    {"role": "system", "content": "You are ChatGPT..."},
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi! How can I help?"},
    ...
    {"role": "user", "content": "What was the capital..."}
  ]
```

### 4.2 Streaming Architecture

```
ChatGPT uses Server-Sent Events (SSE) for streaming:

Server → Client: SSE stream
  data: {"choices": [{"delta": {"content": "The"}}]}
  data: {"choices": [{"delta": {"content": " capital"}}]}
  data: {"choices": [{"delta": {"content": " of"}}]}
  ...
  data: [DONE]

Implementation:
  1. Inference engine generates tokens one by one
  2. Each token pushed immediately to SSE buffer
  3. Client renders token as it arrives
  4. No waiting for complete response

Benefit: Perceived latency drops from 5-10s (full response) to 0.5s (first token)

vLLM streaming:
  async for token in llm.astream(prompt, **sampling_params):
    yield f"data: {json.dumps({'token': token})}\n\n"
```

### 4.3 Model Router

```
Routing Logic:
  - Free tier → GPT-3.5-turbo
  - Paid tier → GPT-4o (default) or GPT-4 (legacy)
  - Image in request → GPT-4V
  - Plugin active → GPT-4 with tools
  - Code Interpreter active → GPT-4 in sandboxed environment

Load balancing across inference nodes:
  - Consistent hashing by conversation_id (sticky sessions for KV cache reuse)
  - Fallback to least-loaded node on miss
  - Health-check sidecar monitors GPU utilization and queue depth

Priority queues:
  - API tier (enterprise): highest priority
  - ChatGPT Plus (paid): high priority
  - Free tier: standard priority
  - Burst limiting during peak: free tier gets queued
```

### 4.4 Inference Engine

```
ChatGPT uses proprietary inference stack; open-source equivalent: vLLM

Key optimizations:
  1. PagedAttention: KV cache in non-contiguous pages (like OS virtual memory)
     - No memory fragmentation
     - 2-4× more concurrent requests vs naive KV cache

  2. Continuous batching: dynamic batch composition
     - Don't wait for slowest request
     - Add new requests when a slot frees
     - Vs. static batching: 23× higher throughput

  3. Speculative decoding (GPT-4 + GPT-3.5 drafter):
     - Small model (GPT-3.5) drafts 5 tokens ahead
     - Large model (GPT-4) verifies in parallel
     - If all verified: skip 5 × decode steps
     - Net speedup: 2-3× for common token sequences

  4. Tensor parallelism: split GPT-4 across 8 A100 GPUs
     - Each GPU holds 1/8 of each transformer layer
     - All-reduce between GPUs at each attention layer
     - Latency: 8 × compute + cross-GPU communication overhead
```

### 4.5 Tool/Plugin Architecture

```
Function calling flow:

User: "What's the weather in Paris?"
         |
         v
[Model generates tool call]
  {
    "tool_calls": [{
      "name": "get_weather",
      "arguments": {"location": "Paris", "unit": "celsius"}
    }]
  }
         |
         v
[Tool Executor]
  - Validate function against allowed tool list
  - Execute function (call weather API)
  - Return result to model context
         |
         v
[Second inference pass]
  Context: [conversation + tool result]
  Model generates final response with weather data
         |
         v
User: "It's 18°C and partly cloudy in Paris."

Security:
  - Sandboxed execution environment (container per request)
  - Resource limits: CPU 2 cores, 512MB RAM, 5s timeout
  - No network access for code interpreter (except whitelisted APIs)
  - Output sanitized before injection into context
```

---

## 5. Data Storage Design

```
Conversation Storage:
  PostgreSQL (primary):
    conversations: (id, user_id, title, model, created_at, updated_at)
    messages: (id, conversation_id, role, content, tokens, tool_calls, created_at)

  Redis (hot cache):
    Active conversations: conversation:{id} → last 20 messages
    TTL: 1 hour after last message

  S3 (cold storage):
    Old conversations → serialized JSON → S3 after 30 days
    Retrieved on demand

User Account Storage:
  PostgreSQL:
    users: (id, email, subscription_tier, usage_tokens_month, created_at)

  Redis:
    Rate limiting: rate:{user_id} → token count with 1-hour TTL

Message Queue:
  Kafka:
    inference-requests topic: pending requests with priority
    inference-results topic: completed responses for async clients
    billing-events topic: token counts for billing service
```

---

## 6. Safety Architecture

```
Two-layer safety:

Layer 1: Input filtering (before inference)
  - Classifier: is this request harmful? (70ms classifier)
    Categories: CSAM, self-harm, terrorism, hate speech
  - Fast path: block immediately, return safety message

Layer 2: Output filtering (stream-time)
  - Stream output through safety classifier
  - Token-level filtering for known harmful patterns
  - Can interrupt stream mid-response and substitute

Layer 3: Moderation API (async, logged for review)
  - All conversations sampled at 1-5% for human review
  - Flagged conversations → moderation queue
  - Bad actor patterns → account suspension

Prompt injection defense:
  - User content always injected in "user" role, not "system"
  - System prompt cannot be overridden by user
  - Instruction hierarchy: system > developer > user
```

---

## 7. Cost Optimization

```
Token cost breakdown (GPT-4o, ~$5/1M input tokens, $15/1M output):
  Average request: 500 input + 300 output tokens
  Cost per request: (500 × $5 + 300 × $15) / 1M = $0.007
  Daily: 500M requests × $0.007 = $3.5M/day ... clearly unrealistic for free tier

  Reality: OpenAI charges $20/month (ChatGPT Plus) to subsidize GPU costs
  GPT-3.5 costs ~$0.0005/1K tokens — 10× cheaper → used for free tier

Cost optimization strategies:
  1. Semantic caching: cache responses to similar questions
     - Embed incoming query
     - Search cache (cosine similarity > 0.95 → return cached)
     - 15-20% cache hit rate on common queries

  2. KV cache sharing: if many users start conversations with same system prompt
     - Pre-fill system prompt KV cache
     - Reuse across requests
     - Anthropic prompt caching: 90% cost reduction on cached tokens

  3. Model routing: detect "easy" queries → route to GPT-3.5
     - Intent classifier: simple factual → 3.5, complex reasoning → 4
     - Free users always → 3.5 unless capacity allows

  4. Speculative execution: run draft model in parallel
     - Only pay for verification tokens, not full generation
```

---

## 8. Availability and Reliability

```
Failure modes and mitigations:

1. GPU node failure:
   - Kubernetes pod restart: <30 seconds
   - Load balancer health checks: remove failed node in <5 seconds
   - Active requests: failed → client retry with exponential backoff

2. Model OOM (out of memory):
   - PagedAttention: reject new requests before OOM, not after
   - Graceful degradation: queue requests when near capacity

3. Database failure:
   - PostgreSQL read replicas (3 replicas across AZs)
   - Failover: automatic via connection pooler (PgBouncer)

4. Global outage mitigation:
   - Multi-region: US, EU, Asia-Pacific
   - Traffic splits: 60% US, 30% EU, 10% APAC
   - Cross-region failover: DNS-based with <5 minute failover

SLA: 99.9% = 8.7 hours downtime/year
  Reality: ChatGPT has had several notable outages during peak demand
  Mitigation: capacity planning at 150% of projected peak
```

---

## 9. Trade-offs and Design Decisions

| Decision | Chosen Approach | Alternative | Reason |
|----------|----------------|-------------|--------|
| Streaming | SSE (Server-Sent Events) | WebSocket, polling | SSE simpler for unidirectional; lower overhead |
| Context management | Truncate oldest messages | Summarize old context | Truncation is free; summarization adds latency |
| Model routing | Rule-based (free vs paid) | ML-based complexity routing | Simpler, predictable billing |
| KV cache | PagedAttention (non-contiguous) | Static KV cache | 2-4× better GPU utilization |
| Batching | Continuous batching | Static batching | 23× higher throughput |
| Storage | PostgreSQL + Redis | Pure NoSQL | ACID guarantees for billing; Redis for speed |

---

## 10. Interview Discussion Points

**Scaling bottleneck:** The GPU inference cluster is the primary scaling constraint. Unlike web servers that scale horizontally trivially, adding more GPU capacity costs $15-30K per A100 GPU.

**Key insight — context window as a resource:** The KV cache for a 128K context uses 2GB+ of GPU memory per request. This is why PagedAttention is critical — naive allocation would mean each A100 can only serve ~10-20 concurrent long-context requests.

**Trade-off question: Why not cache all responses?**
Conversations are personalized and contextual — the same question "what should I do next?" has completely different answers depending on conversation history. Only context-independent queries (factual lookups) are cacheable. Semantic caching applies only to a subset.

**Follow-up: How would you handle a 10× traffic spike?**
Short term: queue requests, lower context limits, route more to GPT-3.5. Long term: predictive auto-scaling (expand GPU cluster 30 minutes before expected peak based on historical patterns).

**Cost vs. quality trade-off:** The fundamental tension is that better models cost more to run. ChatGPT monetizes this gap: free tier subsidizes costs, paid tier generates revenue. The system must correctly route requests to appropriately-tiered models without degrading user experience.
