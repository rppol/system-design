# Tenant Isolation Patterns for LLM Applications

> Cross-cutting concern referenced by:
> [design_notion_ai](../design_notion_ai.md) |
> [design_llm_gateway](../design_llm_gateway.md) |
> [design_customer_support_bot](../design_customer_support_bot.md) |
> [design_ai_data_analyst](../design_ai_data_analyst.md) |
> [design_ai_code_review](../design_ai_code_review.md)

Related modules:
[Embeddings and Similarity Search](../../embeddings_and_similarity_search/README.md) |
[LLM Security](../../llm_security/README.md) |
[Deployment and MLOps](../../deployment_and_mlops/README.md)

---

## 1. Concept Overview

Tenant isolation in LLM applications is fundamentally harder than in traditional SaaS for four reasons that do not exist in relational-database multi-tenancy.

First, vector similarity search is not row-level. A shared HNSW index stores all tenant embeddings in the same graph structure. Even with a metadata filter, the approximate nearest-neighbor search traverses graph nodes that belong to other tenants before discarding them. A misconfigured or omitted filter silently returns cross-tenant results with no error. Worse, an adversary with query access can determine with ~75% probability whether a specific document is indexed (membership inference) because similar embeddings cluster regardless of tenant ownership.

Second, shared KV cache in LLM inference servers can expose system prompt content. vLLM's prefix caching re-uses the KV computation for identical prompt prefixes. If two tenants are routed to the same GPU worker and their system prompts share a prefix, vLLM may serve cached KV states computed from one tenant's context to another tenant's generation.

Third, LLM context windows create cross-tenant injection risk. If retrieved documents from tenant A are accidentally concatenated into tenant B's prompt, the LLM treats them as ground truth. The model has no concept of document ownership — it simply follows what is in the context.

Fourth, fine-tuning on one tenant's data can bleed behavioral patterns. A model fine-tuned on legal contracts from tenant A learns stylistic patterns (citation format, indemnity clause phrasing) that then surface in responses to tenant B, potentially exposing confidential strategies through behavioral fingerprinting.

---

## 2. Intuition

**One-line analogy**: Tenant isolation in LLM apps is like apartments in a building — you want noise isolation (noisy neighbor), separate keys (ACL), and soundproofing (no cross-tenant data in context windows); the vector DB is the shared hallway needing the most careful design.

**Mental model**: Think of the data plane as a series of gates. Every gate that a request passes through must independently validate tenant identity. A single missing gate — whether at the retrieval layer, the context assembly layer, or the KV cache layer — creates a data breach. Defense in depth means that even if one gate fails, the next gate catches the leak before it reaches the LLM context or the user response.

**Why it matters**: A cross-tenant data leak in a vector search result is silent. Unlike a SQL injection that returns an error, a vector DB that omits a tenant filter returns plausible-looking results that pass all downstream validation. The leak only surfaces when a user reports that the AI cited a document they should not have access to — typically after the breach has already occurred.

**Key insight**: The only fully safe isolation is architectural, not runtime configuration. A metadata filter is a software configuration that can be omitted by a bug. A dedicated collection or dedicated cluster makes cross-tenant access impossible at the API level — there is no code path that can return tenant A's data to tenant B even if every application-layer filter is removed.

---

## 3. Core Principles

**Deny by default**: Every retrieval query must include an explicit tenant boundary. The absence of a filter must not silently broaden the search scope — it must fail closed with a logged error.

**Isolation at the data layer, not the application layer**: Filter before retrieval, not after. Post-retrieval filtering (fetch 100 results, then discard 80 that belong to other tenants) has two failure modes: (1) it can still return cross-tenant results if the filter logic has a bug, and (2) it degrades recall because the retrieved 100 results are dominated by the larger tenant's data, pushing out the smaller tenant's relevant documents.

**Defense in depth**: Apply isolation at every layer — API authentication (reject unauthenticated), tenant context injection (embed tenant_id into every request), retrieval filter (enforce at vector DB), context assembly validation (strip cross-tenant chunks), and output filter (scan response for known-sensitive patterns from other tenants).

**Isolation overhead must be O(1) in the request critical path**: The cost of tenant isolation must not scale with the number of tenants. Iterating over all tenant ACL entries at query time is O(tenants); pushing the tenant_id filter into the vector DB index is O(1). Design ACL pushdown so that the enforcement happens in a single predicate evaluation, not a loop.

**Treat tenant_id as an immutable server-side claim**: Never trust tenant_id from the caller's request body or query string. Extract it from the authenticated JWT or session token on the server, inject it into TenantContext, and propagate it through the call stack. This prevents tenant impersonation.

---

## 4. Types / Architectures / Strategies

Four strategies exist for isolating tenants in a vector database, with fundamentally different security guarantees.

### Strategy 1: Namespace / Metadata Filter

All tenants share a single HNSW collection. Each document has a `tenant_id` metadata field. Every query appends a `{"tenant_id": {"$eq": ctx.tenant_id}}` filter. This is the cheapest model: one collection, one HNSW graph, one set of infrastructure costs.

The critical weakness: the filter is a runtime software configuration. A single code path that omits the filter exposes all tenants. The HNSW graph itself is shared — a high-QPS tenant degrades ANN search performance for all tenants. At cardinalities above 10,000 unique tenant values, the post-graph filter causes Qdrant and Weaviate to degrade toward O(n) full scans because the HNSW graph cannot efficiently route to a sparse subset of nodes.

### Strategy 2: Per-Tenant Collection

Each tenant gets their own named collection with a dedicated HNSW graph. A tenant router maps incoming requests to the correct collection name. Cross-tenant queries are impossible at the API level — there is no filter to omit because each collection only contains one tenant's data.

Cost: each collection requires its own HNSW graph in RAM. Qdrant uses approximately 50–100 MB of RAM per collection for the HNSW graph at 1M vectors. 1000 SMB tenants with 10k vectors each = ~50 GB RAM — feasible on a single large machine but not cost-effective.

### Strategy 3: Per-Tenant Cluster

Enterprise tenants get a dedicated Qdrant or Pinecone instance. No shared infrastructure at any layer. Supports data residency requirements (EU cluster for EU tenant, US cluster for US tenant). Enables per-tenant GPU assignment for inference. Minimum cost: ~$200/month per tenant at cloud list pricing, scaling with vector count and QPS.

### Strategy 4: Hybrid (Recommended)

SMB tenants (< 50k vectors, non-regulated data) share a namespace on a multi-tenant collection with metadata filter enforcement. Enterprise tenants (> 100k vectors, SOC2, HIPAA, or data residency requirements) get dedicated collections. Fortune 500 accounts with attorney-client privilege or PHI get dedicated clusters with separate VPCs.

### Comparison Table

| Dimension | Namespace / Filter | Per-Tenant Collection | Dedicated Cluster |
|-----------|-------------------|-----------------------|-------------------|
| Isolation strength | Low — filter can be omitted | High — API-level separation | Highest — infrastructure-level |
| Cost per tenant | < $1/month | ~$5–20/month (RAM) | ~$200–2000/month |
| Noisy-neighbor risk | High — shared HNSW graph | Medium — shared node, separate graph | None — dedicated hardware |
| GDPR deletion complexity | High — must scan all vectors for tenant_id | Low — drop collection | Low — destroy cluster |
| Data residency support | No — single region | No — single region | Yes — per-region cluster |
| HNSW build time impact | All tenants share build queue | Isolated per-tenant | Isolated |
| Membership inference risk | High — shared index | Low — collection-level API auth | None |

---

## 5. Architecture Diagrams

### Metadata Filter Risk: The Missing Gate

```
                      Request: user_B queries "merger terms"
                              |
                  +-----------+-----------+
                  |           |           |
           [Auth OK]   [Rate limit OK]  [Tenant B confirmed]
                  |
                  v
          vector_db.query(                        <--- MISSING FILTER
            query_vector=embed("merger terms"),
            limit=10
            # tenant_id filter omitted by bug
          )
                  |
                  v
    +-----------------------------------+
    |  Shared HNSW Index                |
    |  tenant_A: "Acme merger docs"     |  <--- These are returned
    |  tenant_B: "Globex contract v2"   |
    |  tenant_C: "Initech Q3 revenue"   |  <--- These are returned too
    +-----------------------------------+
                  |
                  v
    tenant_B user reads tenant_A's confidential M&A documents
```

### Per-Tenant Collection Routing (Safe Path)

```
    Authenticated Request
    JWT: { tenant_id: "acme", user_id: "u123" }
              |
              v
    +---------------------+
    |  TenantContextMiddleware |
    |  extracts tenant_id     |
    |  from JWT (server-side) |
    +---------------------+
              |
              v
    +---------------------+
    |  TenantRouter        |
    |  "acme" -> collection |
    |    "acme_legal_docs"  |
    +---------------------+
              |
              v
    +------------------------+
    | Qdrant Collection:     |
    | "acme_legal_docs"      |   Only acme's data
    | (isolated HNSW graph)  |   Cross-collection queries
    +------------------------+   blocked at API level
              |
              v
    +---------------------+
    |  sanitize_chunks()  |  Redundant safety gate:
    |  strips any chunk   |  confirms chunk.tenant_id
    |  != "acme"          |  == ctx.tenant_id
    +---------------------+
              |
              v
    Context assembled — LLM generates response
    using only acme's documents
```

### Defense-in-Depth Stack

```
Inbound Request
      |
      v
+------------------+
| 1. API Auth      |  JWT validation, API key check
| deny if missing  |  Extracts tenant_id claim
+------------------+
      |
      v
+------------------+
| 2. TenantContext |  Builds TenantContext from auth
| Injection        |  Sets tenant_id, plan_tier,
|                  |  allowed_collections, rate_limit
+------------------+
      |
      v
+------------------+
| 3. Rate Limiter  |  Per-tenant token bucket in Redis
| NoisyNeighbor   |  Raises TenantQuotaExceeded if burst
| Mitigation       |  exceeded (sliding window, 1s granularity)
+------------------+
      |
      v
+------------------+
| 4. Retrieval     |  Filter ALWAYS injected at this layer
| Filter Pushdown  |  Application layer cannot omit it
| (ACLPushdown     |  Collection-level or namespace-level
|  Retriever)      |
+------------------+
      |
      v
+------------------+
| 5. Context       |  sanitize_retrieved_chunks()
| Assembly         |  Verifies chunk.tenant_id == ctx.tenant_id
| Validation       |  Logs any mismatch as SECURITY_ALERT
+------------------+
      |
      v
+------------------+
| 6. Output Filter |  Scan for PII patterns from other tenants
| (optional)       |  Block if cross-tenant content detected
+------------------+
      |
      v
    Response
```

---

## 6. How It Works — Detailed Mechanics

### TenantContext Dataclass

```python
from dataclasses import dataclass, field
from enum import Enum

class PlanTier(str, Enum):
    SMB = "smb"
    PROFESSIONAL = "professional"
    ENTERPRISE = "enterprise"

@dataclass(frozen=True)
class TenantContext:
    """Immutable tenant context extracted from auth token — never trust caller input."""
    tenant_id: str
    user_id: str
    plan_tier: PlanTier
    allowed_collections: list[str]   # e.g. ["acme_legal_docs", "acme_hr_docs"]
    rate_limit_rps: int              # e.g. SMB=5, Professional=20, Enterprise=100
    data_region: str                 # e.g. "us-east-1", "eu-central-1"
    acl_teams: list[str] = field(default_factory=list)   # team IDs for ACL pushdown
```

### BROKEN: Retrieval Without Tenant Filter

```python
from qdrant_client import QdrantClient
from qdrant_client.models import ScoredPoint

client = QdrantClient("localhost", port=6333)

# BROKEN: omits tenant_id filter — returns results from ALL tenants
def retrieve_broken(query_vector: list[float], limit: int = 10) -> list[ScoredPoint]:
    results = client.search(
        collection_name="shared_knowledge_base",
        query_vector=query_vector,
        limit=limit
        # Missing: query_filter=Filter(must=[FieldCondition(...)])
    )
    return results
    # Returns docs from tenant_A, tenant_B, tenant_C indiscriminately
```

### FIX: TenantAwareRetriever

```python
from dataclasses import dataclass
from typing import Any
import logging

from qdrant_client import QdrantClient
from qdrant_client.models import (
    Filter,
    FieldCondition,
    MatchValue,
    ScoredPoint,
)

logger = logging.getLogger(__name__)

@dataclass
class Document:
    content: str
    metadata: dict[str, Any]
    score: float

class TenantAwareRetriever:
    """
    Always injects tenant_id filter at the retrieval layer.
    The filter is constructed inside this class and cannot be
    bypassed by the caller — the caller only provides query_vector
    and TenantContext.
    """

    def __init__(
        self,
        client: QdrantClient,
        collection_name: str,
        ef_search: int = 256,       # raised from default 128 to compensate for
    ) -> None:                      # ~3-8% recall loss from filter
        self._client = client
        self._collection_name = collection_name
        self._ef_search = ef_search

    def retrieve(
        self,
        query_vector: list[float],
        ctx: TenantContext,
        limit: int = 10,
    ) -> list[Document]:
        # Filter is always constructed server-side from TenantContext.
        # Caller cannot override or omit it.
        tenant_filter = Filter(
            must=[
                FieldCondition(
                    key="tenant_id",
                    match=MatchValue(value=ctx.tenant_id),
                )
            ]
        )

        results = self._client.search(
            collection_name=self._collection_name,
            query_vector=query_vector,
            query_filter=tenant_filter,
            limit=limit,
            search_params={"hnsw_ef": self._ef_search},  # compensate filter recall loss
            with_payload=True,
        )

        docs = [
            Document(
                content=r.payload.get("content", ""),
                metadata=r.payload,
                score=r.score,
            )
            for r in results
        ]

        logger.info(
            "tenant_retrieval",
            extra={
                "tenant_id": ctx.tenant_id,
                "results_count": len(docs),
                "collection": self._collection_name,
            },
        )
        return docs
```

### ACLPushdownRetriever for Notion-Style Permission Model

```python
from qdrant_client.models import Filter, FieldCondition, MatchAny, MatchValue

class ACLPushdownRetriever:
    """
    Enforces block-level ACL: a document is returned only if
    the requesting user is in allowed_users OR their team is in allowed_teams.
    Both checks are pushed into the Qdrant filter — no post-retrieval filtering.
    """

    def __init__(self, client: QdrantClient, collection_name: str) -> None:
        self._client = client
        self._collection_name = collection_name

    def retrieve(
        self,
        query_vector: list[float],
        ctx: TenantContext,
        limit: int = 10,
    ) -> list[Document]:
        # Compound filter: tenant_id AND (user_id in allowed_users OR team_id in allowed_teams)
        acl_filter = Filter(
            must=[
                FieldCondition(
                    key="tenant_id",
                    match=MatchValue(value=ctx.tenant_id),
                ),
            ],
            should=[
                FieldCondition(
                    key="allowed_users",
                    match=MatchAny(any=[ctx.user_id]),
                ),
                FieldCondition(
                    key="allowed_teams",
                    match=MatchAny(any=ctx.acl_teams),
                ),
            ],
            minimum_should_match=1,    # at least one `should` clause must match
        )

        results = self._client.search(
            collection_name=self._collection_name,
            query_vector=query_vector,
            query_filter=acl_filter,
            limit=limit,
            with_payload=True,
        )

        return [
            Document(
                content=r.payload.get("content", ""),
                metadata=r.payload,
                score=r.score,
            )
            for r in results
        ]
```

### NoisyNeighborRateLimiter

```python
import time
import redis
from dataclasses import dataclass

class TenantQuotaExceeded(Exception):
    def __init__(self, tenant_id: str, limit_rps: int) -> None:
        super().__init__(f"Tenant {tenant_id} exceeded {limit_rps} RPS quota")
        self.tenant_id = tenant_id

class NoisyNeighborRateLimiter:
    """
    Token bucket per tenant in Redis using a sliding window.
    Bucket refills at rate_limit_rps tokens/second.
    Burst capacity = rate_limit_rps * 2 (2-second burst).

    Redis keys: rl:{tenant_id}:tokens  (current token count, float)
               rl:{tenant_id}:last_ts  (last refill timestamp, float)
    """

    BUCKET_EXPIRY_SECONDS = 3600   # expire idle buckets after 1 hour

    def __init__(self, redis_client: redis.Redis) -> None:
        self._redis = redis_client

    def check_and_consume(
        self,
        tenant_id: str,
        rate_limit_rps: int,
        cost_tokens: int = 1,
    ) -> None:
        """
        Raises TenantQuotaExceeded if bucket does not have enough tokens.
        Atomically consumes cost_tokens from the bucket.
        """
        now = time.monotonic()
        burst_capacity = rate_limit_rps * 2

        tokens_key = f"rl:{tenant_id}:tokens"
        ts_key = f"rl:{tenant_id}:last_ts"

        pipe = self._redis.pipeline(transaction=True)
        pipe.get(tokens_key)
        pipe.get(ts_key)
        current_tokens_raw, last_ts_raw = pipe.execute()

        current_tokens = float(current_tokens_raw or burst_capacity)
        last_ts = float(last_ts_raw or now)

        # Refill tokens based on elapsed time
        elapsed = max(0.0, now - last_ts)
        refill = elapsed * rate_limit_rps
        current_tokens = min(burst_capacity, current_tokens + refill)

        if current_tokens < cost_tokens:
            raise TenantQuotaExceeded(tenant_id, rate_limit_rps)

        # Consume tokens atomically
        new_tokens = current_tokens - cost_tokens
        pipe = self._redis.pipeline(transaction=True)
        pipe.set(tokens_key, new_tokens, ex=self.BUCKET_EXPIRY_SECONDS)
        pipe.set(ts_key, now, ex=self.BUCKET_EXPIRY_SECONDS)
        pipe.execute()
```

### sanitize_retrieved_chunks: Redundant Safety Gate

```python
import logging
from typing import Any

security_logger = logging.getLogger("security")

def sanitize_retrieved_chunks(
    chunks: list[Document],
    ctx: TenantContext,
) -> list[Document]:
    """
    Redundant safety check: even after TenantAwareRetriever enforces
    the filter at the DB layer, this function strips any chunk whose
    stored tenant_id does not match the requesting tenant.

    A cross-tenant chunk reaching this function means one of:
    1. TenantAwareRetriever has a bug (filter not applied)
    2. The collection routing was wrong (wrong collection returned)
    3. Data was indexed with wrong tenant_id metadata

    Any of these cases is a SECURITY_ALERT — log it with full context.
    """
    clean: list[Document] = []
    for chunk in chunks:
        stored_tenant = chunk.metadata.get("tenant_id")
        if stored_tenant != ctx.tenant_id:
            security_logger.error(
                "CROSS_TENANT_LEAK_DETECTED",
                extra={
                    "requesting_tenant": ctx.tenant_id,
                    "requesting_user": ctx.user_id,
                    "stored_tenant": stored_tenant,
                    "chunk_id": chunk.metadata.get("chunk_id"),
                    "collection": chunk.metadata.get("collection"),
                },
            )
            # Do NOT include this chunk — drop it silently from user's perspective
            continue
        clean.append(chunk)
    return clean
```

**Concrete latency and recall numbers:**
- Qdrant metadata filter (`tenant_id` exact match): adds 2–5 ms for 1M vectors at ef=128
- Raising ef_search from 128 to 256 compensates for 3–8% recall loss from filtering: adds 1–3 ms
- Per-tenant Qdrant collection: ~50–100 MB RAM for HNSW graph per collection at 1M vectors (768-dim)
- Dedicated cluster minimum: ~$200/month on GCP for a 4-core/16 GB node running Qdrant
- Noisy neighbor: 1 tenant at 100 QPS on a shared collection degrades other tenants' p99 latency 15–40%
- Pinecone namespace overhead: ~0 ms additional latency vs no namespace (namespaces are native to Pinecone's index)
- Redis token bucket atomic pipeline: ~0.3–0.8 ms per rate limit check (single round trip)

---

## 7. Real-World Examples

**Glean** uses a hierarchical isolation model: org-level, team-level, and document-level permissions are all pushed into the retrieval layer. Every search query from an employee carries an ACL token encoding their team memberships and document-level access grants. Glean's vector index applies all three filter levels simultaneously — not sequentially — to avoid the recall problem of chained post-retrieval filtering.

**Notion AI** operates at workspace-level isolation as the primary boundary. Within a workspace, block-level permissions replicate Notion's existing permission graph into the vector metadata on ingestion. A user querying Notion AI sees only blocks they can view in the Notion UI. The ACL is kept in sync via a CDC pipeline from the Notion permission store to the vector DB metadata. Staleness: permission revocations propagate within ~30 seconds.

**Harvey AI** applies per-matter isolation for legal data. Each legal matter (case) is a separate collection. An attorney's query is routed only to the collections corresponding to matters they are assigned to. The isolation is enforced at the Harvey API gateway, which resolves matter assignments from a separate authorization service before routing to the correct collections. Cross-matter contamination would constitute an attorney-client privilege violation — a regulatory breach, not just a product bug.

**Intercom** segments each customer's knowledge base into a per-account collection. When a support agent queries Fin (Intercom's AI), the query is scoped to the account's articles and resolved tickets. Intercom cites this isolation as a key part of their SOC2 Type II compliance controls.

**Pinecone Namespaces** were introduced specifically to solve the noisy-neighbor problem in multi-tenant deployments. Pinecone's implementation gives each namespace its own segment of the index with independent compaction, making namespace-scoped queries independent of other namespaces' write load. The design document explains that namespaces are more than a metadata filter — they are a first-class index partitioning primitive.

---

## 8. Tradeoffs

### Isolation Strategy Comparison

| Dimension | Metadata Filter | Per-Tenant Collection | Dedicated Cluster |
|-----------|----------------|----------------------|-------------------|
| Isolation strength | Low (software) | High (API boundary) | Highest (infra) |
| Cost per 10k tenants | ~$50/month total | ~$50k–200k/month | ~$2M/month |
| Noisy-neighbor risk | High | Medium (shared node) | None |
| GDPR deletion speed | Hours (scan payload) | Seconds (drop collection) | Minutes (destroy cluster) |
| Data residency | Not supported | Not supported | Supported (per-region) |
| HNSW graph size | Single large graph | Many small graphs | Many small graphs |
| Filter recall vs no-filter | -3–8% | No difference | No difference |
| Membership inference risk | High | Low (collection-auth) | None |

### Isolation Strength vs Cost — Why the Default Is Hybrid

Plotting the three strategies on a cost axis shows the jumps are not linear — each step
up in isolation strength costs roughly an order of magnitude more per 10k tenants:

```
 isolation strength  ----------------------------------------------->
   Low (software)        High (API boundary)        Highest (infra)

   ~$50/mo               ~$50k-200k/mo               ~$2M/mo
      |                       |                          |
   [Metadata filter]     [Per-tenant collection]    [Dedicated cluster]
    membership-infer       drop-collection delete     per-region residency;
    risk; -3-8% recall     in seconds; no leakage      zero noisy-neighbor
      |                       |                          |
      +-------- ~1000x -------+--------- ~10-40x --------+--> cost / 10k tenants

 Hybrid (recommended): metadata filter for the long tail of small tenants;
 promote a tenant to its own collection past a size/compliance threshold;
 reserve a dedicated cluster only for the few with a residency contract -- so
 cluster-grade cost is paid only for the tenants that actually require it.
```

### Pre-Retrieval Filter vs Post-Retrieval Filter

| Aspect | Pre-retrieval (pushdown) | Post-retrieval (application layer) |
|--------|--------------------------|-----------------------------------|
| Security | Strong — no cross-tenant data traverses network | Weak — cross-tenant data reaches app; risk of logic bugs |
| Recall | -3–8% at high filter selectivity | Full recall (fetches more, discards later) |
| Latency | O(1) filter evaluation in DB | O(retrieved_count) discard loop in app |
| Noisy-neighbor impact | Reduced — DB only processes relevant shard | Full — DB scans all tenants' data |
| Implementation complexity | Requires DB-level filter support | Works with any DB |

---

## 9. When to Use / When NOT to Use

**Use dedicated cluster when:**
- Tenant data is subject to HIPAA, attorney-client privilege, or SOC2 Type II requirements
- Tenant has > 1M vectors (HNSW graph would consume >100 GB RAM in shared node)
- Tenant requires data residency (EU data must not leave EU infrastructure)
- Fortune 500 contract requires logical or physical isolation as a compliance control
- Tenant QPS is high enough to constitute a noisy neighbor (>50 QPS sustained)

**Use per-tenant collection when:**
- SaaS product with B2B customers who have sensitive but non-regulated data
- Tenant vector count is 10k–1M (50–100 MB RAM per collection is acceptable)
- GDPR deletion must be fast (drop collection is O(1))
- Noisy-neighbor isolation is needed but dedicated infrastructure is too expensive
- Membership inference attack is a concern (collection-level API auth prevents cross-collection queries)

**Use metadata filter (namespace) when:**
- SMB SaaS with non-sensitive data (no PII, no regulated content)
- Tenant vector count is < 10k (filter cardinality stays below performance cliff)
- Budget is the primary constraint
- Startup moving fast — plan to migrate to per-tenant collections as you grow
- Pinecone namespaces are used (native index partitioning, not pure metadata filter)

**Do NOT use metadata filter when:**
- Any tenant's data is subject to HIPAA, GDPR special categories, or legal privilege
- Tenant count exceeds 10,000 unique values in a shared Qdrant/Weaviate collection (HNSW degrades)
- You need to prove isolation to an enterprise security team (metadata filter is not auditable in the same way as API-level separation)
- Membership inference is a threat model (adversarial tenants can probe for document existence)

---

## 10. Common Pitfalls

**Pitfall 1: Pinecone noisy-neighbor performance cliff at high filter cardinality (2023)**

A SaaS platform serving 15,000 SMB tenants on a shared Pinecone index used the `tenant_id` metadata filter. At launch, p99 query latency was 120 ms. As the tenant count grew past 10,000, p99 climbed to 800 ms — a 6x degradation with no code change. Root cause: Pinecone's HNSW post-filter mechanism iterates through candidate nodes and discards those that do not match the filter. When 14,999 of 15,000 tenants' documents are discarded per query, the effective selectivity is 0.007% — the filter becomes nearly equivalent to a full scan. Fix: migrate to Pinecone namespaces (native index partitioning) so each tenant's query operates only on their namespace's graph segment. After migration, p99 returned to 130 ms across all tenants. Lesson: metadata filter is not a substitute for index partitioning at high cardinality.

**Pitfall 2: Qdrant scalar quantization breaking filter pushdown**

An engineering team enabled int8 scalar quantization on a 10M-vector Qdrant collection to reduce RAM usage by 4x (from 38 GB to 9.5 GB). Post-migration, filtered queries regressed 40% in recall and 3x in latency. Root cause: Qdrant's quantized index compresses vectors into uint8, but filter evaluation for payload conditions requires decompressing and re-scoring candidates from the full-precision payload. When the quantized HNSW graph returns 200 candidates and the tenant filter discards 195 of them, the effective search is re-running on only 5 full-precision vectors — far too few for good recall. Fix: set `ef_search` to 512 (up from 128) when using quantization with high-selectivity filters. Also enable `payload_index` for the `tenant_id` field to allow Qdrant to pre-filter using a separate payload index before entering the HNSW graph. After tuning, recall recovered to 92% of unquantized baseline.

**Pitfall 3: Vector DB membership inference attack**

A security researcher at a legal tech company demonstrated that a tenant with standard query access could determine with 73% confidence whether a specific document belonged to another tenant on the same Qdrant collection. The attack: generate an embedding for a known document phrase, query the collection with the tenant's metadata filter omitted (exploiting a bug), and observe the cosine similarity distribution. Documents from the target cluster around cosine similarity 0.92–0.97; random documents cluster around 0.55–0.65. The 73% confidence comes from the bimodal distribution being distinguishable even with filter enforcement, because the HNSW graph structure reflects document proximity regardless of tenant. Root cause: the shared HNSW graph encodes cross-tenant embedding relationships. Fix: per-tenant collections make cross-collection queries impossible at the Qdrant API level — the attacker cannot query another tenant's collection even if they know its name, because collection-level auth prevents it. Metadata filters do not protect against membership inference because they operate after graph traversal.

**Pitfall 4: LLM context cross-contamination from routing bug**

A customer support platform for 200 enterprise accounts had a cache key collision in its tenant router. When the tenant router cached the collection name mapping, a bug caused the cache to store `tenant_id -> collection_name` pairs with a shared Redis key prefix that did not include environment (staging vs production). On Black Friday, a staging test that used `tenant_id = 1001` evicted the production entry for a real tenant with the same ID. For 47 minutes, tenant 1001's queries were routed to the staging collection (containing synthetic test data) and the staging tenant's queries were routed to production (containing real customer contracts). Three tenant 1001 users received responses that cited production contract terms from another company. The company received a breach notification obligation under their enterprise agreements. Fix: (1) cache keys must always include environment, region, and collection type as separate segments; (2) `sanitize_retrieved_chunks()` as a redundant gate — it would have caught the cross-tenant chunk in the context before it reached the LLM; (3) add a canary assertion that logs a SECURITY_ALERT if any retrieved chunk's `tenant_id` does not exactly match `ctx.tenant_id`.

---

## 11. Technologies & Tools

### Vector Database Isolation Support

| Database | Namespace / Partition | Per-Collection | Payload Filter | Membership Inference Risk |
|----------|-----------------------|---------------|----------------|--------------------------|
| Qdrant | No native namespace; use collections | Yes — first-class | Yes — payload index | High (shared index) |
| Pinecone | Yes — namespaces (native partitioning) | No (single index per project) | Yes | Medium (namespace isolates graph) |
| Weaviate | Yes — multi-tenancy (isolated HNSW per tenant) | Yes | Yes | Low (isolated HNSW) |
| Chroma | Collections | Yes | Yes | High (shared HNSW) |
| pgvector | Row-level security (PostgreSQL RLS) | Schema per tenant | Yes (SQL WHERE) | Low (SQL isolation) |
| Redis Stack | Key prefix as "namespace" | No | Yes (FT.SEARCH FILTER) | Medium |
| Milvus | Partition key | Yes | Yes (partition isolation) | Medium |
| Elasticsearch kNN | Index per tenant | Yes | Yes (query filter) | Medium |

### Isolation Layer Tools

| Layer | Tool | Purpose |
|-------|------|---------|
| Auth / TenantContext | AWS Cognito, Auth0, custom JWT | Extract tenant_id from token |
| Rate limiting | Redis (token bucket), envoy rate limit | Per-tenant QPS enforcement |
| Retrieval filter | Qdrant Filter, Pinecone namespace | ACL pushdown at DB layer |
| ACL sync | CDC (Debezium), event-driven | Keep permission metadata current |
| Audit logging | OpenTelemetry + Loki | Track every retrieval with tenant_id |
| Output scanning | AWS Comprehend, Presidio | Detect PII from wrong tenant in response |

---

## 12. Interview Questions with Answers

**What is the difference between using a metadata filter and a per-tenant collection for tenant isolation in a vector DB?**
A metadata filter is a runtime software configuration applied at query time; it can be omitted by a bug, and a single omission exposes all tenants' data. A per-tenant collection is an architectural separation — cross-collection queries are impossible at the API level, so no single code bug can expose all tenants. The tradeoff is cost and operational complexity: per-tenant collections require ~50–100 MB RAM per collection at 1M vectors, while a shared collection with filters costs nothing extra. Use metadata filters for SMB SaaS with non-sensitive data at < 10k vectors per tenant; use per-tenant collections for enterprise tenants or regulated data.

**Why does HNSW performance degrade with high-selectivity tenant filters, and how do you compensate?**
HNSW builds a graph connecting similar vectors regardless of tenant. When a tenant filter requires discarding 99.9% of graph candidates, the search algorithm traverses many irrelevant nodes before finding enough matching results. This degrades from sub-linear to near-linear scan at extreme filter selectivity. Compensation options: (1) raise `ef_search` from 128 to 256–512 to explore more candidates before filtering, accepting 1–3 ms additional latency; (2) enable payload indexing on `tenant_id` to pre-filter using a B-tree index before HNSW traversal; (3) switch to per-tenant collections or Pinecone namespaces, which eliminate the cross-tenant traversal entirely. The Pinecone namespace model is the most effective because it partitions the graph itself.

**What is vector DB membership inference and how do you defend against it?**
Membership inference is an attack where a tenant with query access determines whether a specific document exists in the shared index by observing cosine similarity scores. Documents cluster at similarity 0.92–0.97 against their own embedding; random vectors cluster at 0.55–0.65. An attacker queries repeatedly with variants of a target phrase and uses the bimodal distribution to infer presence. Metadata filters do not defend against this because the HNSW graph structure encodes cross-tenant proximity — filter is applied after traversal. The only defense is architectural: per-tenant collections or dedicated clusters where the API itself blocks cross-tenant queries.

**How do you safely propagate tenant_id through a Python request stack without trusting the caller?**
Extract tenant_id from the validated JWT or session token in the authentication middleware — never from request body or query parameters. Store it in a frozen `TenantContext` dataclass and pass it explicitly as a parameter through every function that accesses tenant data. Alternatively, use a thread-local or contextvars context to store TenantContext for the duration of the request. Never re-derive tenant_id from user-supplied data downstream — an attacker who can inject a different tenant_id into a downstream function can access another tenant's data. The rule: tenant_id is a server-side claim, not a client-side claim.

**How do you implement noisy-neighbor mitigation for tenant QPS without introducing significant latency?**
Use a per-tenant token bucket in Redis. At the start of each request, atomically check and consume tokens using a Redis pipeline (two commands: GET tokens + GET timestamp, then SET new_tokens + SET timestamp). The round-trip adds 0.3–0.8 ms. Bucket parameters: rate = plan_tier_rps, burst = rate * 2 (allow 2-second burst). Raise `TenantQuotaExceeded` if tokens are insufficient. This approach is O(1) per request regardless of number of tenants, and Redis pipeline ensures atomic read-modify-write without distributed locking. For very high QPS tenants (> 500 RPS), move to a local token bucket with periodic Redis sync to reduce Redis round-trips.

**How do you handle GDPR Article 17 (right to erasure) for a tenant leaving the platform?**
With metadata filter: you must scan the entire collection for all vectors with `tenant_id == departing_tenant`, which is O(n) with n = total vectors across all tenants. For a 10M-vector shared collection, this can take 10–30 minutes and impacts other tenants' read performance during the scan. With per-tenant collection: drop the collection — O(1), completes in seconds, deletes all vectors atomically. With dedicated cluster: destroy the cluster and its storage volumes. The GDPR compliance argument strongly favors per-tenant collections or dedicated clusters over metadata filter for any data subject to right-to-erasure requests.

**What is ACL pushdown and why is it more secure than post-retrieval ACL filtering?**
ACL pushdown means encoding permission rules as vector DB filter expressions that execute inside the database before results are returned. Post-retrieval filtering fetches results across all permission levels and discards unauthorized ones in the application. Post-retrieval filtering has two failure modes: (1) a logic bug in the discard loop can return unauthorized results — the cross-tenant data has already traversed the network; (2) recall is biased toward the larger tenant's data because the "top 10" results before filtering are dominated by high-volume tenants. ACL pushdown with `MatchAny` on `allowed_users` and `allowed_teams` fields is more secure because unauthorized documents never leave the database server.

**How does shared KV cache in vLLM create cross-tenant exposure, and how do you defend against it?**
vLLM's prefix caching computes KV states for a prompt prefix once and reuses them for all subsequent requests that share that prefix. If tenant A and tenant B are both using the same system prompt prefix, vLLM may serve tenant A's cached KV states to tenant B's request — effectively embedding tenant A's prompt context in tenant B's generation. Defense: (1) use per-tenant prefix tokens that make no two tenants' prompts share a common prefix; (2) for strict isolation, disable prefix caching or use separate vLLM worker pools per enterprise tenant; (3) for GPU cost efficiency, accept prefix caching only for the public/shared system prompt portion, and ensure the tenant-specific portion (injected knowledge base context) always follows the cacheable prefix.

**How do you detect cross-tenant data leaks in production before users report them?**
Add a `sanitize_retrieved_chunks` gate before context assembly that compares each chunk's stored `tenant_id` against `ctx.tenant_id` and raises a structured SECURITY_ALERT log event for any mismatch. Route these logs to a high-priority alert channel (PagerDuty P1). Additionally, in output filtering, run a regex scan for known-sensitive patterns from neighboring tenants' data (company names, contract numbers) — this is a heuristic but catches routing bugs quickly. Set up a canary test suite that runs synthetic cross-tenant queries in production and asserts that zero cross-tenant results are returned; run this every 5 minutes and alert on any failure.

**What are the cost economics of hybrid isolation (SMB on namespace, enterprise on dedicated collection)?**
A platform with 10,000 SMB tenants and 100 enterprise tenants: SMB tenants on shared Qdrant cluster at $500/month total = $0.05/tenant/month. Enterprise tenants each on dedicated Qdrant collection, allocated on a shared node (10 per node at $500/month per node): $5/tenant/month. Fortune 500 accounts on dedicated clusters at $200–2000/month depending on vector count. Weighted average blended cost: ~$0.5/tenant/month — achievable while providing the isolation guarantee that closes enterprise deals. The revenue difference between SMB ($50/month) and enterprise ($5000/month) justifies the 100x isolation cost difference.

**How do you enforce data residency requirements in a vector DB deployment?**
Data residency requires that tenant data is stored and processed only within a specified geographic region. Metadata filters cannot enforce this — the shared index is in one region. Per-tenant collections on a shared node cannot enforce this — the node is in one region. Only dedicated clusters support residency: deploy a Qdrant or Pinecone cluster in eu-central-1 for EU tenants, us-east-1 for US tenants. The API gateway must route each request to the correct regional cluster based on the tenant's registered data region, stored in the TenantContext. Never route EU tenants' queries to US clusters even for failover — a cross-region failover for a GDPR-regulated tenant is itself a data transfer that requires legal justification.

**How do you handle the recall degradation from tenant filtering in Qdrant?**
Tenant filtering reduces recall by 3–8% in Qdrant because the HNSW graph traverses nodes that are then discarded by the filter. Compensate by: (1) raising `ef_search` from 128 to 256 — extends the beam search to explore more candidates; this adds 1–3 ms but recovers 2–5% recall; (2) enabling a payload index on the `tenant_id` field — Qdrant can use a secondary B-tree index to pre-filter the payload before HNSW traversal, reducing wasted traversal; (3) if recall is still insufficient, over-retrieve (limit=20, return top 10 after scoring) — but note this increases latency 1.5x. For per-tenant collections, there is no recall degradation because no cross-tenant filtering occurs.

**What is the minimum viable tenant isolation stack for a new LLM SaaS product?**
Start with: (1) TenantContext extracted from JWT in middleware — prevents tenant_id spoofing; (2) TenantAwareRetriever with metadata filter — enforces tenant boundary at retrieval; (3) sanitize_retrieved_chunks as a canary gate — catches filter bugs before LLM sees cross-tenant data; (4) structured SECURITY_ALERT log for any cross-tenant detection — gives early warning before users report leaks. This stack costs ~1–2 days to implement, adds < 10 ms latency overhead, and closes most common tenant isolation vulnerabilities. Upgrade to per-tenant collections when you sign your first enterprise customer or receive your first GDPR inquiry.

**How do you safely test tenant isolation without using production data?**
Create a synthetic test suite with three tenants (A, B, C) and known documents for each. For each retrieval code path, assert that querying as tenant A returns only tenant A's documents and returns exactly 0 results from B or C. Use pytest parameterization to test all (requesting_tenant, target_tenant) pairs. Run this suite in CI on every PR. Additionally, run it in production staging with synthetic tenants every 5 minutes as a canary. The canary should fire a P1 alert if any cross-tenant result is detected. This gives you both pre-deployment confidence and post-deployment detection coverage.

**How do fine-tuning or embedding model updates create cross-tenant isolation risks?**
When you fine-tune an embedding model on a new dataset, the embedding space shifts — cosine similarity between the same two texts changes. If tenant A fine-tuned on their legal corpus shifts the embedding space toward their terminology, queries from tenant B may now retrieve semantically "closer" results from tenant A's namespace than before the fine-tune, because the filter is based on exact tenant_id match but the retrieved content is selected by embedding similarity in the new space. This is not a tenant isolation bug in the traditional sense but can cause quality degradation masking as potential leakage in post-hoc analysis. Mitigation: re-index all tenants after embedding model updates, validate per-tenant retrieval quality before rolling out the new model, and use a model that was trained on diverse data rather than one tenant's corpus.

**How does Weaviate's multi-tenancy feature compare to Qdrant's collection-per-tenant approach?**
Weaviate's multi-tenancy (introduced in 1.20) creates an isolated HNSW graph per tenant within a single class (equivalent to a table), with separate segment files on disk. This is closer to Qdrant's per-collection model than to a metadata filter: queries for tenant A only traverse tenant A's HNSW graph. Weaviate's approach adds ~5 MB overhead per tenant (vs Qdrant's 50–100 MB per collection) because Weaviate shares the schema and class definition while only isolating the data segments. Weaviate also supports tenant activation/deactivation — inactive tenants' data is offloaded to object storage, freeing RAM. For a platform with 10,000 tenants and variable activity, Weaviate's active/inactive management is significantly more cost-effective than Qdrant's always-in-RAM collection model.

---

## 13. Best Practices

1. **Always enforce tenant_id at the retrieval layer, never rely solely on the application layer.** A single code path that omits the filter exposes all tenants' data. The retriever class must own filter construction — the filter must not be passed in from the caller.

2. **Add `sanitize_retrieved_chunks` as a redundant gate before context assembly.** This is defense in depth: if the retrieval filter fails (bug, misconfiguration, wrong collection routing), this gate catches cross-tenant chunks before they enter the LLM context. Log every detection as a SECURITY_ALERT and page on-call.

3. **Use per-tenant collections for any enterprise customer, regulated data, or GDPR right-to-erasure requirement.** Metadata filters cannot provide these guarantees architecturally. The 100x cost increase over metadata filters is justified by the compliance and enterprise revenue it enables.

4. **Extract tenant_id exclusively from the authenticated JWT or session token on the server.** Never trust tenant_id from request body, query string, or client-sent headers. Treat it as a server-side immutable claim. Propagate via `TenantContext` dataclass, not as a loose string parameter.

5. **Set `ef_search` to at least 256 when using metadata filters in Qdrant.** The default 128 loses 3–8% recall when filters are applied. Raising ef_search to 256 recovers 2–5% of this loss at the cost of 1–3 ms additional latency — an acceptable tradeoff for most use cases.

6. **Use Redis token buckets for noisy-neighbor rate limiting, not application-level counters.** Application-level counters are per-process and reset on restart. Redis token buckets persist across restarts and are shared across all replicas. The 0.3–0.8 ms overhead per request is negligible compared to typical LLM inference latency of 500–2000 ms.

7. **Plan GDPR deletion from day one.** If you use metadata filters, document the deletion procedure (scan + delete by payload) and test it with a 1M-vector collection to confirm it completes within your SLA. If deletion time exceeds 1 hour, migrate to per-tenant collections before you receive your first GDPR deletion request.

8. **Run a cross-tenant isolation canary in production every 5 minutes.** Use three synthetic tenants with known documents. Assert that querying as tenant A returns zero results from B or C. Alert on any failure. This catches routing bugs, misconfigured filters, and infrastructure changes that break isolation, before real tenants are affected.

9. **Disable vLLM prefix caching for enterprise tenants with strict isolation requirements, or ensure the cacheable prefix never includes tenant-specific data.** Prefix caching is only safe when the cached portion is truly shared across all tenants — public system prompt text, not injected knowledge base context.

10. **Test all (requesting_tenant, target_tenant) cross-product combinations in CI.** For N tenants in test fixtures, there are N*(N-1) possible cross-tenant retrieval combinations. Assert all return zero cross-tenant results. This parameterized test catches filter bugs that only surface for specific tenant ID combinations.

---

## 14. Case Study

### Notion AI — Workspace-Scoped RAG with Block-Level ACL Pushdown

Notion AI serves workspace-scoped RAG over user documents. The primary isolation boundary is the workspace: each workspace is a separate Qdrant collection. Within a workspace, block-level permissions replicate Notion's existing ACL graph into the vector metadata on ingestion. Every block (page, database, table row) carries an `allowed_users` list and an `allowed_teams` list stored as Qdrant payload arrays. When a user queries Notion AI, the `ACLPushdownRetriever` constructs a compound Qdrant filter: `tenant_id == workspace_id AND (user_id in allowed_users OR team_id in allowed_teams)`. This single filter expression evaluates atomically inside Qdrant — no post-retrieval discard loop. The ACL is kept in sync via a CDC pipeline from Notion's PostgreSQL permission store: when a page is shared or unshared, Debezium publishes the change event, and a Flink job updates the Qdrant payload within ~30 seconds. For the small fraction of blocks where ACL staleness matters (e.g., immediately after a share revocation), Notion's application layer adds a hard lookup against the live permission store before including any retrieved block in the LLM context.

### Harvey AI — Per-Matter Legal Data Isolation

Harvey AI provides AI-assisted legal research and drafting for law firms. The isolation model is per-matter (legal case): each matter is a separate Qdrant collection named `firm_id:matter_id`. An attorney can only query collections corresponding to matters they are assigned to — this assignment is resolved from Harvey's matter management system at query time, not at login. The tenant router fetches the attorney's matter list from a dedicated authorization service (< 5 ms cached, 20 ms uncached), constructs the allowed collection list, and routes the query to the correct collection. Cross-matter contamination would constitute an attorney-client privilege violation — a regulatory breach with bar association reporting obligations. Harvey's defense-in-depth stack adds `sanitize_retrieved_chunks` and an output filter that scans for opposing-counsel names and case numbers from matters the attorney is not assigned to. Any detection fires an immediate P0 alert and quarantines the response.

### LLM Gateway — Per-Tenant Prompt Injection Defense and Rate Limiting

An LLM gateway serving 500 enterprise tenants uses tenant isolation at the prompt level, not just the retrieval level. Each tenant's system prompt is stored in a per-tenant namespace in a secure vault (not in the LLM context cache). At request time, the gateway injects the system prompt from the vault, ensuring that even if a user submits a prompt injection attempt ("ignore previous instructions"), the injected system prompt is always the last authoritative instruction before the user turn. The gateway enforces per-tenant rate limits using the `NoisyNeighborRateLimiter`: SMB tenants at 5 RPS, professional at 20 RPS, enterprise at 100 RPS. During a DDoS simulation (one tenant at 500 RPS), other tenants' latency increased by only 8% — the token bucket absorbs burst spikes before they reach the shared LLM inference pool. The gateway also validates that retrieval results' `tenant_id` matches the requesting tenant before injecting them into the prompt, providing the `sanitize_retrieved_chunks` defense at the gateway layer rather than inside individual application services.

### AI Data Analyst — Per-User Sandbox and Dataset Isolation

An AI data analyst product allows enterprise users to upload CSV and database exports and ask natural language questions. Each uploaded dataset is stored in a per-user S3 prefix and indexed into a per-user Qdrant collection. The user's `TenantContext` carries both the `tenant_id` (company) and `user_id`, and the collection name includes both: `company_123:user_456:dataset_789`. Schema inference, semantic indexing, and query execution all operate within this triple-scoped isolation boundary. Cross-user dataset queries are impossible at the collection API level. For multi-user collaboration (shared datasets within a company), the system creates a separate shared collection with explicit team ACL pushdown — individual user collections never merge. The sandbox for code execution (Pandas, SQL) additionally runs in a gVisor container per request with no network access, ensuring that even if a user crafts a malicious prompt that generates `import os; os.environ["API_KEY"]`, the sandboxed container cannot exfiltrate data to an external endpoint.

---

*See also:*
- *[Embeddings and Similarity Search](../../embeddings_and_similarity_search/README.md) — HNSW internals, IVF, Matryoshka, filter recall*
- *[LLM Security](../../llm_security/README.md) — Prompt injection, data extraction, adversarial robustness*
- *[Deployment and MLOps](../../deployment_and_mlops/README.md) — vLLM KV cache, GPU cost, serving infrastructure*
