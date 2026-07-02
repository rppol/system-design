# LLM Caching

## 1. Concept Overview

LLM caching is the practice of storing and reusing model inputs or outputs to reduce latency and
cost. Unlike traditional caching where exact bit-for-bit matches are sufficient, LLM systems
require a taxonomy of caching strategies because inputs are rarely byte-identical and outputs are
probabilistic. The five distinct caching layers in an LLM production stack — exact-match response,
semantic, provider prompt (prefix KV), self-hosted KV-prefix, and embedding — each address
different cost-latency tradeoffs and have different invalidation semantics.

**Cost anchor:** A 100k-token context at 10 requests/second with a 70% prompt cache hit rate saves
roughly 700k cached input tokens/second. At GPT-4o pricing ($2.50/1M), that is $1.75/second —
$6,300/hour — recovered by caching stable prefixes. At 1M requests/day, a 60% semantic cache hit
rate on FAQ-type queries at an average of 500 tokens/query saves $750/day at GPT-4o-mini pricing.
Full pricing math lives in [Token Economics & Cost Optimization](../token_economics_and_cost_optimization/README.md).

---

## 2. Intuition

**One-line analogy:** LLM caching is like a multi-level CPU cache — L1 is exact-match (fastest,
most restrictive), L2 is semantic (fast, approximate), L3 is prefix KV (GPU-resident,
architectural) — each level trades hit rate for generality.

**Mental model:** Before spending compute on a model call, check each cache layer in order. Exact
match: have you seen this exact byte sequence before? Semantic match: have you seen a semantically
equivalent question? Prompt prefix: have you already computed the KV tensors for the stable part
of this context? Only if all caches miss do you pay the full inference cost.

**Why it matters:** For FAQ-heavy workloads (customer support, documentation Q&A), 40-70% of
questions are semantically equivalent to previously answered questions. For multi-user agents
sharing a common system prompt, 60-90% of input tokens are identical prefix tokens that can be
cache-hit at 90%+ discount. Caching is the highest-ROI optimization in most LLM production systems.

**Key insight:** The right cache for your workload depends on query distribution. Power-law query
distributions (a few questions asked many times) favor exact and semantic caching. Token-repeat
distributions (same system prompt, different user messages) favor KV-prefix caching. Both often
apply simultaneously.

---

## 3. Core Principles

**Caching is a quality risk, not just an optimization.** Stale cached responses can mislead users.
Define TTLs and invalidation triggers based on how frequently the underlying facts change, not just
on cost savings.

**Cache at the right granularity.** Caching at the response level (full output) is simple but
inflexible. Caching at the prefix KV level (computed attention tensors) is invisible to the
application but architecturally more powerful.

**Semantic cache requires threshold tuning.** Cosine similarity thresholds for semantic cache are
workload-specific. Too tight: low hit rate, no savings. Too loose: semantically different queries
get the same answer (quality regression). Tune on production query logs, not synthetic data.

**Separate cache by model and prompt version.** A cache entry valid for gpt-4o is not valid for
gpt-4o-mini. A response cached for prompt_v1 must be invalidated when prompt_v2 deploys. Make
model name + version + prompt version part of the cache key.

**Never cache outputs that should not be reused.** Do not cache: responses containing timestamps
("as of today..."), user-personalized content, or outputs from tools that have side effects.

---

## 4. Types / Cache Taxonomy

### 4.1 Exact-match response cache

Stores the full model output keyed on the exact input string (or hash). A cache hit returns the
stored output instantly, bypassing the model entirely.

| Property | Value |
|----------|-------|
| Hit rate | Low-medium (5-30% conversational; 30-60% template-driven) |
| Latency benefit | Maximum: 0ms model call |
| Quality risk | Low if TTL is correct |
| Implementation | Redis / Memcached with SHA-256(model+prompt+messages) as key |
| Best for | Repeated identical queries: report generation, templated emails, FAQ |

### 4.2 Semantic cache

Embeds the query, searches a vector index for a similar past query above a cosine threshold, and
returns the cached response for that similar query.

| Property | Value |
|----------|-------|
| Hit rate | Higher (20-60% on FAQ workloads) |
| Latency benefit | 10-50ms (embedding + vector search) vs 200-5,000ms (full inference) |
| Quality risk | Medium: false positives when similar queries need different answers |
| Implementation | text-embedding-3-small + pgvector/Qdrant; threshold ~0.92 |
| Best for | Customer support Q&A, documentation search, FAQ bots with paraphrased queries |

### 4.3 Provider prompt caching (prefix KV)

The model provider stores the KV-attention tensors for a fixed context prefix. Subsequent requests
with the same prefix skip the attention computation for the cached portion.

| Provider | Feature | Discount | Min cacheable tokens |
|----------|---------|----------|---------------------|
| Anthropic | `cache_control: {"type": "ephemeral"}` | 90% on cached tokens | 1,024 |
| OpenAI | Automatic (no API change needed) | 50% on cached tokens | 1,024 |
| Google Gemini | `context_caching` API | Variable | 32,768 |

### 4.4 Self-hosted KV-prefix caching

vLLM and SGLang maintain a GPU-resident LRU cache of KV tensors indexed by the prefix hash. When
a new request shares a prefix with a cached entry, the saved tensors are used directly, skipping
prefill computation for that prefix. Transparent to the application — no API change needed. See
[vLLM Deep Dive](../vllm_deep_dive/README.md) for the PagedAttention block structure these caches
build on.

| Engine | Feature | Benefit |
|--------|---------|---------|
| vLLM | Automatic Prefix Caching (APC) | TTFT reduction for shared prefixes |
| SGLang | RadixAttention | Multi-level cache; structured generation |

### 4.5 Embedding cache

Caches computed text embeddings to avoid re-embedding the same text on every request. Critical for
[RAG pipelines](../rag_fundamentals/README.md) where document embeddings are computed once at
index time.

| Use case | Cache strategy |
|----------|---------------|
| Document embeddings | Pre-computed at index time; invalidate on document update |
| Query embeddings | Short-lived; rarely worth caching across requests |
| Embeddings for semantic cache | Cached by input hash; TTL matches data freshness |

---

## 5. Architecture Diagrams

```
Multi-Layer LLM Cache Architecture
=====================================

          Incoming LLM request
                 |
                 v
       +--------------------+
       | L1: Exact-Match    |
       | Key: SHA-256(msgs) |
       | Store: Redis       |
       | TTL: configurable  |
       +--------------------+
          HIT /       \ MISS
         /               \
    Return              |
    cached               v
    response   +--------------------+
               | L2: Semantic Cache |
               | Embed query ->     |
               | vector search      |
               | threshold: 0.92    |
               +--------------------+
                 HIT /       \ MISS
                /               \
           Return               |
           similar response      v
                       +--------------------+
                       | L3: Prompt Cache   |
                       | (Provider KV)      |
                       | Anthropic/OpenAI   |
                       | auto prefix cache  |
                       +--------------------+
                                  |
                                  v
                       Full LLM inference
                                  |
                                  v
                       Store in L1, L2


Anthropic Prompt Caching — Prefix Layout
==========================================

Request:
|
+--[0]------[3,000]  System prompt
|                    cache_control: {"type": "ephemeral"}  <-- cache this
|
+--[3,000]--[5,000]  Tool definitions
|                    cache_control: {"type": "ephemeral"}  <-- cache this
|
+--[5,000]--[7,000]  Few-shot examples
|                    cache_control: {"type": "ephemeral"}  <-- cache this
|
+--[7,000]--[14,000] Retrieved context    (NOT cached — dynamic)
|
+--[14,000]-[14,200] User message         (NOT cached — dynamic)

Billing:
  tokens [0-7,000] cache write: $3.75/1M (first time)
  tokens [0-7,000] cache read:  $0.30/1M (subsequent, 90% discount)
  tokens [7,000-14,200]: full input price $3.00/1M


Semantic Cache — False-Positive Risk
=======================================

Query A: "What is the refund policy?"         sim=0.97  HIT  (correct)
Query B: "How do I request a refund?"         sim=0.94  HIT  (correct)
Query C: "What is the refund policy in Japan?" sim=0.93 HIT  (WRONG!)
                                                              ^ Japan policy differs

Fix: Use metadata filters as hard equality constraints in vector search:
     search WHERE country_code = request.country AND similarity > threshold
```

---

## 6. How It Works — Detailed Mechanics

### Exact-match cache (Redis)

```python
import hashlib, json, redis
from openai import OpenAI

_client = OpenAI()
_cache  = redis.Redis(host="localhost", port=6379, db=0)

def cached_completion(
    messages: list[dict],
    model: str = "gpt-4o",
    ttl_seconds: int = 3_600,
    no_cache: bool = False,
) -> str:
    if no_cache:
        return _call_model(messages, model)

    key = (
        f"llm:{model}:"
        + hashlib.sha256(
            json.dumps(messages, sort_keys=True).encode()
        ).hexdigest()
    )

    if cached := _cache.get(key):
        return cached.decode()

    result = _call_model(messages, model)
    _cache.setex(key, ttl_seconds, result)
    return result

def _call_model(messages: list[dict], model: str) -> str:
    return _client.chat.completions.create(
        model=model, messages=messages
    ).choices[0].message.content
```

### Semantic cache (pgvector)

```python
from openai import OpenAI
import psycopg2

_client = OpenAI()
_DSN    = "dbname=llmcache host=localhost"

def _embed(text: str) -> list[float]:
    return _client.embeddings.create(
        model="text-embedding-3-small", input=text
    ).data[0].embedding

def semantic_lookup(query: str, threshold: float = 0.92) -> str | None:
    emb = _embed(query)
    with psycopg2.connect(_DSN) as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT response, 1 - (embedding <=> %s::vector) AS sim
            FROM semantic_cache
            ORDER BY embedding <=> %s::vector
            LIMIT 1
            """,
            (emb, emb),
        )
        row = cur.fetchone()
        if row and row[1] >= threshold:
            return row[0]
    return None

def semantic_store(query: str, response: str) -> None:
    emb = _embed(query)
    with psycopg2.connect(_DSN) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO semantic_cache (query, embedding, response) "
            "VALUES (%s, %s::vector, %s)",
            (query, emb, response),
        )
        conn.commit()
```

### Anthropic prompt caching

```python
import anthropic

_client = anthropic.Anthropic()

def call_with_prompt_cache(
    system: str,
    tool_defs: list[dict],
    user_message: str,
) -> str:
    response = _client.messages.create(
        model="claude-opus-4-5",
        max_tokens=1_024,
        system=[
            {
                "type": "text",
                "text": system,
                "cache_control": {"type": "ephemeral"},   # cache system prompt
            }
        ],
        tools=[
            {**tool, "cache_control": {"type": "ephemeral"}}   # cache tool defs
            for tool in tool_defs
        ],
        messages=[{"role": "user", "content": user_message}],
    )
    # response.usage.cache_creation_input_tokens  -- tokens written to cache
    # response.usage.cache_read_input_tokens      -- tokens served from cache
    return response.content[0].text
```

---

## 7. Real-World Examples

**Perplexity AI** reports over 60% of inference requests hit the semantic cache for popular search
queries. The cache is partitioned by query language and time window (queries from the last 24h are
cached; older entries have a lower TTL for freshness). This alone reduces inference costs by
approximately 40%.

**Customer support bots (Intercom, Zendesk AI)** observe that the top 1,000 query clusters account
for 60-70% of all questions. An exact-match cache on normalized queries achieves 25-35% hit rate;
a semantic cache above that achieves 55-65% combined. The majority of queries are served without
a model call.

**Cursor editor** uses Anthropic prompt caching for code context. The repository index is placed in
a cached prefix block. Across a coding session, 80% of input tokens are served from the KV cache,
reducing per-query inference cost by ~70%.

---

## 8. Tradeoffs

| Cache type | Hit rate | Latency savings | Quality risk | Implementation cost |
|------------|----------|-----------------|-------------|---------------------|
| Exact match | Low-medium | Maximum | Minimal | Low |
| Semantic | Medium | High | Medium (threshold-sensitive) | Medium |
| Provider prompt (prefix KV) | High for shared prefixes | High (cost savings) | None | Very low |
| Self-hosted KV prefix (vLLM/SGLang) | High for shared prefixes | High (TTFT) | None | Infrastructure |
| Embedding cache | Very high for reused docs | Medium | None | Low |

---

## 9. When to Use / When NOT to Use

**Use caching when:**
- *Exact cache:* template-driven or FAQ workloads with repeating identical queries.
- *Semantic cache:* conversational Q&A where users paraphrase the same question.
- *Prompt cache:* any system with a stable system prompt >1,024 tokens shared across many users.
- *Embedding cache:* RAG pipelines with large document corpora that do not change frequently.

**Do NOT cache when:**
- Output contains time-sensitive data ("as of today", "current price", "live status").
- User-personalized content that must not be shared across users.
- Tool calls with side effects (writes, emails, payments) — never cache these.
- Semantic cache: queries where slight semantic similarity hides important differences (legal
  jurisdiction, medical context, user role).

---

## 10. Common Pitfalls

**Pitfall 1 — Sharing cache entries across users.** A semantic cache hit serves user A's past
answer to user B, potentially leaking personalized or sensitive information. Fix: partition the
cache by user ID or tenant; never share cache entries across security boundaries.

**Pitfall 2 — Stale semantic cache entries.** A FAQ answer cached 3 months ago no longer reflects
the current return policy. Fix: set TTLs based on data change frequency; for live product/policy
information, use short TTLs (hours) or event-triggered invalidation.

**Pitfall 3 — Semantic cache threshold too low.** Threshold of 0.80 causes semantically different
questions ("price in the US?" and "price in Germany?") to return the same answer. Fix: tune the
threshold on production query logs; use metadata filters as secondary hard keys.

**Pitfall 4 — Prompt cache miss from unstable prefix.** Adding a timestamp or user ID to the
system prompt defeats the KV-prefix cache because every request has a unique prefix. At 10
requests/second and a 100k-token context, this wastes $1.75/second in caching potential
($6,300/hour at GPT-4o rates). Fix: move all dynamic content to the user turn; the system prompt
must be byte-identical across all requests using the same cached prefix.

```python
# BROKEN: dynamic values in the system prompt — unique prefix on every request
system = f"You are a support bot. Today is {datetime.now()}. User: {user_id}."

# FIX: byte-identical system prompt; dynamic values move to the user turn
system = "You are a support bot."
user_msg = f"[date: {today}] [user_tier: {tier}]\n{question}"
```

**Pitfall 5 — Cache poisoning via prompt injection.** An attacker crafts a query that gets cached
with a malicious response; subsequent users asking a similar question receive the attacker's output.
Fix: never cache responses to unvalidated user inputs; run an output safety check before storing
any entry in the semantic cache.

**Pitfall 6 — Not measuring cache hit rate.** Team assumes the cache is working but hit rate is 2%
because the key format changed after a refactor. Fix: expose cache hit rate as a first-class metric
(hits/total, per cache type); alert if hit rate drops below expected baseline.

---

## 11. Technologies & Tools

| Tool | Type | Key Feature |
|------|------|-------------|
| Redis | Exact-match store | Low-latency key-value; TTL support; cluster mode |
| pgvector | Semantic cache store | PostgreSQL extension; cosine/L2 distance |
| Qdrant | Semantic cache store | Standalone; filtering; high-throughput |
| LiteLLM | Proxy with built-in cache | Drop-in OpenAI-compatible; exact + semantic cache |
| GPTCache | Semantic cache library | Open source; multiple backends and embedding models |
| Anthropic API | Provider prompt caching | cache_control blocks; 90% discount on cached tokens |
| OpenAI API | Provider prompt caching | Automatic; 50% discount; 1,024-token minimum |
| vLLM | Self-hosted KV prefix cache | Automatic prefix caching (APC); TTFT reduction |
| SGLang | Self-hosted KV prefix cache | RadixAttention; multi-level cache |
| Memcached | Exact-match store | Simpler than Redis; horizontal scale; no persistence |

---

## 12. Interview Questions with Answers

**What are the five layers of LLM caching and what does each optimize?**
(1) Exact-match response cache: returns a stored response when the exact input repeats; maximizes
savings for identical queries. (2) Semantic cache: returns a cached response when the input is
semantically similar above a cosine threshold; handles paraphrased queries. (3) Provider prompt
caching (Anthropic cache_control, OpenAI automatic): caches KV-attention tensors for a stable
context prefix; saves 50-90% on cached input tokens for shared system prompts. (4) Self-hosted
KV-prefix caching (vLLM APC, SGLang RadixAttention): GPU-resident LRU cache of KV tensors;
reduces time-to-first-token for requests sharing a prefix. (5) Embedding cache: avoids re-embedding
unchanged documents; critical for RAG performance.

**How do you design the cache key for a multi-model, multi-prompt-version system?**
Include all dimensions that affect the output: model name, model version, prompt version (or hash),
and the input content hash. A minimal key: `{model_name}:{model_version}:{prompt_hash}:{sha256(sorted_messages)}`.
Omitting any dimension causes incorrect cache hits. User-specific content must either be excluded
from cacheable content or used as an additional key segment to prevent cross-user sharing.

**What is the false-positive problem in semantic caching and how do you tune the threshold?**
A false positive occurs when two queries have high cosine similarity but require different answers
— e.g., "refund policy in the US" and "refund policy in Germany." Tune the threshold by: sampling
production query pairs with known different correct answers; computing their cosine similarity;
setting the threshold above the 95th percentile of that distribution. In practice, 0.90-0.95 is
the typical range. For high-stakes domains, use metadata filters as hard secondary keys rather than
relying on similarity alone.

**Why can provider prompt caching only cache a prefix, never a middle or suffix segment?**
KV tensors are position-dependent: each token's keys and values are computed from all preceding
tokens through causal attention, so a cached segment is only valid if every byte before it is
identical. Changing one character at position 0 invalidates everything after it, and a stable
block placed after dynamic content can never hit. This is why prompt structure is an architectural
decision: system prompt and tool definitions first, retrieved context next, user message last —
ordered from most to least stable. Audit prompt-assembly code for anything dynamic (timestamps,
request IDs, shuffled few-shot examples) that sneaks in before the intended cache breakpoint.

**Does response caching break sampling semantics when temperature > 0?**
Yes — a cached response replays a single draw from the output distribution, making the endpoint
deterministic for repeated queries even though callers requested sampled diversity. For FAQ
answers this is usually desirable (consistency builds trust); for brainstorming or creative
endpoints it is a bug users notice ("it gives the identical answer every time"). Include
temperature and other sampling parameters in the cache key, and skip response caching entirely
for endpoints where output diversity is part of the product. Prompt (KV) caching has no such
problem — it reuses input computation while the model still samples fresh output.

**How does Anthropic prompt caching work and how do you maximize hit rate?**
Anthropic caches the KV-attention tensors for any content block marked with
`cache_control: {"type": "ephemeral"}`. The minimum cacheable prefix is 1,024 tokens; the TTL is
5 minutes. To maximize hit rate: place the system prompt and tool definitions in cached blocks at
the front of every request; ensure these blocks are byte-identical across requests — no timestamps,
user IDs, or dynamic content; track `cache_read_input_tokens` vs `cache_creation_input_tokens` in
usage metadata. The discount is 90% on cached input tokens ($3.00 → $0.30/1M for Claude 3.5
Sonnet).

**How does vLLM automatic prefix caching (APC) work?**
vLLM's APC maintains a GPU-resident LRU cache of KV tensors keyed by the SHA-256 of the token
sequence of each block (typically 16-32 tokens per block). When a new request shares a prefix with
a cached entry, vLLM skips the prefill computation for the cached blocks, reducing time-to-first-
token by up to 60-80% for long shared prefixes. APC provides no benefit when every request has a
unique prefix.

**How do you prevent cache poisoning in a semantic cache?**
(1) Validate and sanitize all responses before caching — run through the same guardrail pipeline
used for production outputs. (2) Only cache responses to queries that pass input classification
(not jailbreaks or adversarial inputs). (3) Add a staleness timestamp and cap cache entry lifetime
so poisoned entries expire. (4) Monitor cache hit responses with a quality sampler to detect
anomalies.

**How do you handle cache invalidation for a RAG system where the knowledge base updates?**
Use event-driven invalidation: when a document is updated, identify cached queries whose
top-retrieved document includes the changed document, and evict those entries. This requires
provenance logging (tracking which documents contributed to each cached response). For simpler
systems, use TTL-based invalidation calibrated to the update frequency (daily updates → TTL of
23h). For real-time data, do not use semantic caching at all.

**What is the difference between prompt caching and response caching?**
Provider prompt caching saves the computation of processing input tokens — the KV tensors are
reused, reducing TTFT and input token cost; the model still generates a fresh output each time.
Response caching saves the output — the same generated text is returned without any model call.
Response caching is cheaper (zero inference cost for hits) but deterministic. Use response caching
for truly static, idempotent queries; use prompt caching for all requests with shared system
prompts regardless of whether the output is dynamic.

**How would you instrument a multi-layer cache to understand its effectiveness?**
Track per-layer metrics: (1) hit rate per layer; (2) latency distribution (p50/p95/p99) for hits
vs misses; (3) cost savings in dollars per day (tokens served from cache * price delta); (4)
false-positive rate for semantic cache (sampled LLM-as-judge: "is this cached response correct
for this query?"). Alert on hit rate drop >10pp (suggests key schema change) and false-positive
rate >2% (threshold needs tightening).

**How do you handle user-personalized responses and caching safely?**
Personalized responses must not be shared across users. Options: (1) strip personalization from
the prompt and inject it post-generation (cache the generic response, then string-substitute);
(2) use per-user cache namespaces (key includes user ID) — hit rate is lower but safe; (3) do not
cache personalized responses at all — only cache generic portions. The semantic cache must never
serve user A's personalized response to user B; add a user_id metadata filter as a hard equality
constraint in the vector search.

**What happens to an Anthropic cache entry after the 5-minute TTL, and how do you keep it warm?**
Each cache read refreshes the 5-minute TTL, so steady traffic (more than one request per 5 minutes
per unique prefix) keeps the entry alive indefinitely; a traffic gap lets it expire, and the next
request pays the write price again. Writes carry a 25% premium over normal input ($3.75 vs
$3.00/1M for Sonnet-class models), so caching pays for itself as soon as a prefix is reused even
once within the TTL (break-even at ~1.3 uses). For low-traffic but cost-sensitive prefixes, a
keep-alive ping (a max_tokens=1 request every ~4 minutes) costs far less than repeated cache
re-writes of a 10k-token prefix; Anthropic also offers a 1-hour TTL tier at a higher write premium.
Track spikes in `cache_creation_input_tokens` as the signal that your prefix is churning or your
traffic has gaps.

**How does SGLang's RadixAttention differ from vLLM's automatic prefix caching?**
vLLM's APC hashes fixed-size token blocks (16-32 tokens) and reuses KV tensors for exact
block-aligned prefix matches. RadixAttention instead organizes cached prefixes in a radix tree
over token sequences, so requests can share any common prefix at token granularity, and the tree
makes multi-branch sharing (one system prompt, many few-shot variants, many user turns) explicit
and LRU-evictable per node. RadixAttention shines for structured workloads — agent loops,
tree-of-thought search, batched evals — where many requests share deep, branching prefixes. For
plain chat traffic with one shared system prompt, both give similar wins; the difference shows up
when prefixes branch.

**How do you cache effectively in multi-turn conversations where the context grows every turn?**
Exact and semantic response caches are nearly useless mid-conversation (each turn's context is
unique), but KV-prefix caching is ideal: the conversation history is an append-only prefix, so
turn N reuses everything computed for turns 1..N-1. With Anthropic, move the cache breakpoint
forward each turn (up to 4 cache_control breakpoints per request) so the newest turns get cached
for the next request; with vLLM/SGLang this happens automatically as long as the session lands on
the same replica — which makes session-affinity routing a caching feature, not just a
load-balancing choice. Budget for the growing prefix: a 50-turn conversation still pays cache-read
price on the full history every turn, which is why history summarization or truncation remains
necessary beyond the cache.

**Where does caching fit when responses are streamed?**
For cache hits there is no token stream — only a stored string — so either return it at once (a
different UX than token-by-token rendering) or replay it as a synthetic stream for visual
consistency. On the write side, buffer the full streamed response and insert it into the cache
only after the stream completes successfully and passes output validation — caching a truncated
stream (client disconnect, timeout) poisons the entry for every future hit. Provider prompt
caching is unaffected by streaming since it operates on input processing, not output delivery.
Practical pattern: wrap the stream in a tee that accumulates chunks and commits to cache only on
a clean end-of-stream event.

---

## 13. Best Practices

- Implement caching as a layered pipeline: exact match first, semantic second, prompt cache third.
  Each layer catches what the previous misses.
- Partition all application-level caches by model name + version + prompt version; a cache entry
  is only valid for the exact combination that generated it.
- Never cache responses containing time-sensitive data, PII, or outputs from tool calls with side
  effects; mark these no-cache at the call site.
- Tune semantic cache threshold on production query logs; start at 0.92 and adjust based on
  measured false-positive rate.
- For provider prompt caching, ensure the stable prefix is byte-identical across all requests;
  move all dynamic content to after the cached prefix.
- Track cache hit rate, false-positive rate, and cost savings as first-class production metrics;
  alert on hit rate drops.
- Use event-driven cache invalidation for knowledge bases that update frequently.
- Add a cache-bypass mechanism (header or flag) for debugging and canary testing.
- Scan cached responses quarterly for PII leakage and cross-tenant data exposure, especially in
  semantic caches.

---

## 14. Case Study

**Problem Statement**

A B2B SaaS company runs an AI customer support chatbot handling 500,000 requests/day. Monthly
inference cost is $45,000 (GPT-4o). Analysis: 65% of queries are from a shared pool of ~2,000 FAQ
topics; system prompt is 4,000 tokens; average context is 6,000 tokens. No caching is in place.

**Architecture Overview**

```
Three-Layer Cache Architecture
================================

L1: Exact-match cache (Redis)
  Key: SHA-256(model + messages)
  TTL: 24 hours
  Expected hit rate: 18%  (identical FAQ wording)

L2: Semantic cache (Qdrant + text-embedding-3-small)
  Threshold: 0.92
  Metadata filter: product_line, language
  TTL: 6 hours
  Expected hit rate on L1 misses: ~40%  (= 33% of total)

L3: Anthropic prompt cache
  Cached prefix: 4,000-token system prompt
  Cached tokens per request: 4,000 of 6,000 input tokens on cache reads
  Expected hit rate: ~80%  (most requests share same prefix)
  Discount: 90% on cached portion


Cost model (per 1,000 requests, Claude 3.5 Sonnet $3/$15 per 1M):
  Without cache:   6,000 input tokens * $3/1M * 1,000 = $18.00

  After L1 (18% hit, 0 cost):
    820 requests pass through  -> cost $14.76

  After L2 (33% of 820 = 270 hits, ~$0.05 embedding cost):
    550 requests pass through  -> cost $9.90

  After L3 (80% of 550 = 440 cache reads at 90% discount):
    440 * 4,000 * $0.30/1M = $0.53   (vs $5.28 without L3)
    110 full-prefix requests: 110 * 6,000 * $3/1M = $1.98

  Total with cache: ~$2.51  vs  $18.00 without
  Savings: 86%
```

**Key Design Decisions**

Cache partitioning: L2 semantic cache uses metadata filters (product_line, language) as hard
equality constraints to prevent serving US product answers to Germany queries. TTL set to 6 hours
(product info updates weekly; 6h provides freshness safety margin). Anthropic prompt cache: system
prompt is the sole cached block; no user ID or timestamp in the system prompt.

**Tradeoffs and Alternatives**

LiteLLM proxy was evaluated as an all-in-one solution but the team needed custom metadata filtering
in the semantic cache that LiteLLM did not support. A single Redis-only approach was prototyped but
the 18% exact hit rate left too much on the table — the semantic cache was worth the additional
complexity.

**Interview Discussion Points**

- Monthly cost: $45,000 → $6,750 (savings: $38,250). Cache infra cost: $400/month. ROI: day 1.
- Three false-positive incidents in the first month prompted lowering the threshold from 0.90 to
  0.92 for product-pricing queries, dropping hit rate by 3pp but eliminating false positives.
- The cache hit rate drops predictably every Monday morning when users ask about weekend policy
  changes before the TTL refreshes — this is acceptable; the team adds a webhook from the policy
  CMS to invalidate the semantic cache on policy update events.
