# Multi-Region LLM Topology

---

## 1. Concept Overview

Multi-region deployment for stateless web services is a solved problem: put instances behind a global load balancer, let it pick the nearest region, and the request lands. LLM applications break every assumption that makes this simple.

GPU scarcity distorts geography. H100 supply is not uniformly available: as of late 2024 roughly 60% of publicly available cloud H100 capacity sits in us-east-1 and us-east-2, 25% in eu-west-1 and eu-central-1, and 15% in ap-northeast-1 and ap-southeast-1. Deploying to three equal-sized regions means the APAC region can only absorb 15% of global traffic before exhausting capacity, making naive "nearest region" routing dangerous.

KV cache is per-GPU and non-shareable. When a model processes the first turn of a 20-turn conversation, it caches the key-value matrices for every token in that prefix on the specific GPU that ran the request. The second turn must land on the same GPU — or recompute the entire prefix from scratch. For a system prompt of 4,000 tokens and an 8,000-token accumulated conversation, a cache miss means recomputing 12,000 tokens of attention. At a compute cost of roughly 2 ms per 1,000 tokens on an H100, that is 24 ms of wasted GPU time per cache miss, per turn. For a 20-turn conversation with 100% miss rate, this is 10× the compute cost of a sticky-routed equivalent.

Model weights are large. A 70B parameter model in bfloat16 occupies 140 GB. A 405B model (LLaMA 3.1 scale) occupies 810 GB. Replicating these across regions is not instantaneous: S3 cross-region replication at a sustained 10 Gbps takes 112 seconds for a 140 GB checkpoint, and most links see 2–5 Gbps in practice, pushing transfer time to 4–10 minutes. LoRA adapters at 300 MB replicate in under 30 seconds, which is why adapter-per-tenant architectures are operationally easier in multi-region settings.

Streaming responses need connection continuity. A user streaming a 2,000-token response at 40 tokens/second is connected for 50 seconds. Mid-stream region failover is not transparent: the HTTP/2 or WebSocket connection must be re-established, the client must know where to reconnect, and the new region must know how many tokens were already delivered. Stateless HTTP load balancers have no mechanism for this. Sidecar proxies with in-flight request state or client-side resume protocols are required.

Cross-region latency is not negligible. us-east-1 to eu-west-1 is approximately 85 ms RTT. us-east-1 to ap-northeast-1 is approximately 170 ms RTT. For a non-streaming request, this adds directly to time-to-first-byte. For streaming, it adds to time-to-first-token (TTFT) — the most user-visible latency metric. Same-region latency between availability zones is under 2 ms. Intra-cluster GPU-to-GPU (NVLink) is sub-microsecond. The latency hierarchy shapes all routing decisions.

---

## 2. Intuition

**One-line analogy**: Multi-region LLM serving is like routing airline passengers to gates where their luggage (KV cache) is already loaded — sending them to the wrong terminal means starting over.

**Mental model**: Think in two layers. The routing layer is global and cheap: it makes the "which region?" and "which pod?" decisions based on geography, health, and session affinity. The serving layer is local and expensive: it runs GPU inference, manages KV cache, and streams tokens. The routing layer must preserve GPU-level stickiness across its global view; the serving layer must never care about geography. These two concerns must not bleed into each other.

**Why it matters**: Multi-region LLM serving is the infrastructure primitive that enables global SLAs, regulatory data residency compliance, and disaster recovery. The six case studies in this repo that reference it — ChatGPT, the LLM gateway, the GPU inference platform, the real-time translation service, the customer support bot, and the AI coding assistant — each depend on getting this topology right before any application-level optimization matters.

**Key insight**: For LLM apps, "session affinity" is not a nicety — it is a performance primitive. A system that ignores KV cache locality and routes purely by load will burn 3–10× more GPU compute than necessary. GPU compute at $2–8/hour/H100 makes this a direct cost multiplier, not a theoretical concern.

---

## 3. Core Principles

**Latency hierarchy drives routing priority order**: Same PoP (point of presence) < same region (different AZ, <2 ms) < cross-region same continent (20–90 ms) < cross-region different continent (85–200 ms). Routing decisions should resolve in this order: first check if a sticky pod is alive in the user's closest PoP; then check the same region; only cross region as a last resort or for failover.

**KV cache stickiness is a first-class concern**: Consistent hashing on session_id (not user_id, not IP address) maps each conversation to a stable pod. One user can have five concurrent conversations; hashing on user_id would force all five to one pod and violate load distribution. Session_id gives finer granularity with better spread.

**Async replication for user state; synchronous handling for in-flight requests**: User conversation history (the source of truth for resuming a session) must be durably stored in a cross-region replicated store (Redis Cluster with cross-region replication, or DynamoDB Global Tables). Async replication with a lag of 100–500 ms is acceptable because the primary serving path reads from the local replica. In-flight streaming responses must never be asynchronously replicated — they must be terminated and restarted on failover.

**Active-active wastes capacity by design**: In a two-region active-active setup, each region must be provisioned to handle 100% of peak traffic in case the other region fails. This means 2× the GPU fleet for 1.0× of actual peak traffic. The capacity efficiency ratio is 50%. Active-passive with DNS failover achieves ~66% efficiency (one region at 100% normal load, standby at 33% warm idle). Single region with CDN edge achieves ~95% efficiency but loses regional availability.

**Model version consistency must be region-coordinated**: A phased rollout that deploys v3.1 in us-east-1 while eu-west-1 runs v3.0 will produce different outputs for identical prompts. Users whose sessions migrate across regions during failover see inconsistent behavior. Blue-green deployments must be coordinated across all regions before any traffic is shifted.

---

## 4. Types / Architectures / Strategies

### Strategy 1 — Single Active Region with CDN Edge

One GPU cluster (us-east-1 or the highest-capacity region). All inference happens in that region. CDN (Cloudflare, CloudFront) handles auth token validation, rate limiting, and static asset delivery at edge PoPs globally. Streaming inference responses bypass the CDN and go directly to the origin.

### Strategy 2 — Active-Active with Anycast Routing

GPU clusters in 3+ regions. Anycast BGP or Cloudflare Load Balancing routes requests to the nearest healthy region. Session affinity (consistent hashing on session_id at the routing layer) ensures KV cache locality. All regions actively serve traffic. Failover is automatic: when a region's health probes fail, the anycast network re-routes to the next closest region within one BGP convergence interval (typically 30–90 seconds for managed anycast like Cloudflare, faster with AWS Global Accelerator).

### Strategy 3 — Active-Passive with DNS Failover

Primary region handles all traffic. Secondary region is warm (model loaded, a fraction of GPUs active) but receives no user traffic. DNS TTL set to 60 seconds. On primary failure, Route 53 or Cloudflare DNS health-check-based failover updates the DNS record. RTO is 60–120 seconds (DNS TTL propagation + client retry). RPO is 0 for conversation history stored in cross-region replicated databases; the in-flight response at the moment of failure is lost.

### Strategy 4 — Regional Sharding by User Geography (GDPR-Driven)

EU users are permanently assigned to EU regions; US users to US regions. This is enforced at the auth layer, not the routing layer: the JWT or session token is minted with a `home_region` claim, and the routing proxy rejects cross-region delivery of EU-tagged sessions regardless of GPU availability. Cross-region failover for GDPR-scoped users is disabled or requires manual operator approval with audit logging. This adds operational complexity but is the only correct architecture when data residency is a hard legal requirement.

### Comparison Table

| Strategy | RTO | RPO | Cost Multiplier | Complexity | GPU Utilization | Data Residency |
|----------|-----|-----|-----------------|------------|-----------------|----------------|
| Single region + CDN | >10 min (region failure) | 0 (history in DB) | 1.0× | Low | ~90% | None |
| Active-active anycast | 30–90 s | 0 | 2–3× | High | ~50% | Configurable |
| Active-passive DNS | 60–120 s | 0 | 1.4–1.6× | Medium | ~65% | Configurable |
| Regional sharding | 30–90 s (within region) | 0 | 1.5–2× | High | ~70% | Hard guarantee |

---

## 5. Architecture Diagrams

### Diagram 1 — Global Anycast Topology (3 Regions)

```
                         Users (global)
                              |
              +---------------+----------------+
              |               |                |
          EU User          US User          APAC User
              |               |                |
              v               v                v
     +------------------Anycast / Cloudflare LB-----------------+
     |  Selects nearest healthy region; honors X-Session-ID      |
     +-----------------------------------------------------------+
              |               |                |
              v               v                v
     +----------------+ +----------------+ +----------------+
     |  eu-west-1     | |  us-east-1     | | ap-northeast-1 |
     |                | |                | |                |
     | [Envoy Proxy]  | | [Envoy Proxy]  | | [Envoy Proxy]  |
     |  session hash  | |  session hash  | |  session hash  |
     |       |        | |       |        | |       |        |
     | +----+----+   | | +----+----+   | | +----+----+   |
     | |pod0|pod1|   | | |pod0|pod1|   | | |pod0|pod1|   |
     | |GPU |GPU |   | | |GPU |GPU |   | | |GPU |GPU |   |
     | |KV  |KV  |   | | |KV  |KV  |   | | |KV  |KV  |   |
     | |cach|cach|   | | |cach|cach|   | | |cach|cach|   |
     | +----+----+   | | +----+----+   | | +----+----+   |
     |               | |               | |               |
     | [Redis]       | | [Redis]       | | [Redis]       |
     | (session hist)| | (session hist)| | (session hist)|
     | cross-region  | | cross-region  | | cross-region  |
     | replication   | | replication   | | replication   |
     +----------------+ +----------------+ +----------------+
              |                   |                |
              +-------------------+----------------+
                      Cross-region Redis replication
                      (async, lag ~100-300 ms)
```

### Diagram 2 — KV Cache Stickiness via Consistent Hashing

```
  Conversation: session_id = "sess-7f3a"

  Turn 1                    Turn 2                    Turn 3
     |                         |                         |
     v                         v                         v
  Routing Proxy             Routing Proxy             Routing Proxy
  hash("sess-7f3a")         hash("sess-7f3a")         hash("sess-7f3a")
       = 83                      = 83                      = 83
       |                         |                         |
       v                         v                         v
  pod index 83 % 8 = 3     pod index 83 % 8 = 3     pod index 83 % 8 = 3
       |                         |                         |
       v                         v                         v
  +----------+              +----------+              +----------+
  |  Pod 3   |              |  Pod 3   |              |  Pod 3   |
  |  H100    |              |  H100    |              |  H100    |
  | KV cache:|              | KV cache:|              | KV cache:|
  | [sys_p]  |  +turn1+-->  | [sys_p]  |  +turn2+-->  | [sys_p]  |
  |          |              | [turn1]  |              | [turn1]  |
  |          |              |          |              | [turn2]  |
  +----------+              +----------+              +----------+
  Cache grows across turns; prefix never recomputed.
  System prompt (4K tokens) + turn history cached.
  Savings: ~24 ms GPU compute per cache hit on 12K-token context.
```

### Diagram 3 — Region Failover Sequence

```
  t=0   us-east-1 health probe fails (GPU OOM, network partition)
  |
  t=5s  Envoy detects unhealthy upstream, ejects us-east-1
  |
  t=10s Cloudflare anycast stops routing new requests to us-east-1
  |
  t=15s Active streaming connections to us-east-1 pods receive RST
  |     Clients retry with exponential backoff + jitter
  |
  t=20s Client retry reaches routing layer with same X-Session-ID header
  |
  t=25s Routing layer: us-east-1 ejected, next ring slot = eu-west-1
  |     eu-west-1 Envoy receives request with session_id
  |     Consistent hash maps session_id to pod 3 in eu-west-1
  |
  t=30s eu-west-1 pod 3 has NO KV cache for this session (cache miss)
  |     Reads full conversation history from cross-region Redis
  |     Recomputes KV cache from conversation history (~500 ms)
  |     Resumes generation from token N+1 (if client sent last_token_id)
  |
  t=31s Client receives first new token — failover complete
  |     Conversation continues in eu-west-1 for duration of session

  Total observable interruption: ~15 seconds (connection reset + retry)
  RTO for new requests: 10–30 seconds
  RTO for in-flight streams: 15–31 seconds
  KV cache warm-up cost on failover: 1 recompute per migrated session
```

---

## 6. How It Works — Detailed Mechanics

### Envoy Proxy — Session Affinity Configuration

```yaml
# envoy.yaml — upstream cluster for LLM pods within one region
static_resources:
  clusters:
    - name: llm_pods
      type: STRICT_DNS
      lb_policy: RING_HASH          # consistent hashing
      ring_hash_lb_config:
        minimum_ring_size: 1024     # 1024 virtual nodes for even distribution
      health_checks:
        - timeout: 2s
          interval: 5s
          unhealthy_threshold: 2    # eject after 2 consecutive failures
          healthy_threshold: 1
          http_health_check:
            path: /health
      load_assignment:
        cluster_name: llm_pods
        endpoints:
          - lb_endpoints:
              - endpoint:
                  address:
                    socket_address: { address: llm-pod-0, port_value: 8000 }
              - endpoint:
                  address:
                    socket_address: { address: llm-pod-1, port_value: 8000 }
              # ... pods 2-7

  listeners:
    - name: listener_0
      address:
        socket_address: { address: 0.0.0.0, port_value: 80 }
      filter_chains:
        - filters:
            - name: envoy.filters.network.http_connection_manager
              typed_config:
                route_config:
                  virtual_hosts:
                    - routes:
                        - match: { prefix: /v1/chat }
                          route:
                            cluster: llm_pods
                            # Hash on X-Session-ID header for KV cache stickiness
                            hash_policy:
                              - header:
                                  header_name: X-Session-ID
                              # Fallback: hash on source IP if no session header
                              - connection_properties:
                                  source_ip: true
                                terminal: true
```

### Python RegionRouter — Geo + Health + Capacity Selection

```python
import hashlib
import time
from dataclasses import dataclass, field
from typing import Optional
import httpx

@dataclass
class RegionStatus:
    name: str               # "us-east-1"
    endpoint: str           # "https://us-east-1.llm.internal"
    gpu_capacity_pct: float # 0.0-1.0; fraction of GPUs not yet at max batch
    p50_latency_ms: float   # recent 50th-percentile inference latency
    healthy: bool           # health probe result
    gdpr_zone: str          # "EU" | "US" | "APAC" | "ANY"

REGIONS: list[RegionStatus] = [
    RegionStatus("us-east-1",     "https://us-east-1.llm.internal",  0.0, 0.0, True, "US"),
    RegionStatus("eu-west-1",     "https://eu-west-1.llm.internal",  0.0, 0.0, True, "EU"),
    RegionStatus("ap-northeast-1","https://ap-ne-1.llm.internal",    0.0, 0.0, True, "APAC"),
]

class RegionRouter:
    """
    Selects the optimal region for an LLM request.

    Priority order:
    1. Data residency hard constraint (GDPR)
    2. Existing session stickiness (same region as session's home)
    3. Nearest healthy region with available GPU capacity
    4. Least-loaded fallback across any region
    """

    CAPACITY_HEADROOM = 0.85   # reject a region if >85% GPU capacity used
    LATENCY_CEILING_MS = 500   # do not prefer regions above this p50 latency

    def __init__(self, regions: list[RegionStatus]):
        self.regions = regions
        self._last_health_check = 0.0

    def select(
        self,
        user_geo_zone: str,           # "EU" | "US" | "APAC"
        session_home_region: Optional[str],  # where session was created
        user_data_residency: Optional[str],  # hard constraint: "EU" | None
    ) -> RegionStatus:
        candidates = self._healthy_with_capacity()

        if not candidates:
            # Last resort: any healthy region, ignore capacity
            candidates = [r for r in self.regions if r.healthy]
            if not candidates:
                raise RuntimeError("All regions unhealthy — total outage")

        # Step 1: enforce data residency
        if user_data_residency:
            residency_candidates = [r for r in candidates
                                    if r.gdpr_zone == user_data_residency
                                    or r.gdpr_zone == "ANY"]
            if residency_candidates:
                candidates = residency_candidates
            # If no residency-compliant region is healthy: raise — never route
            # EU data to a non-EU region silently.
            else:
                raise RuntimeError(
                    f"No healthy region for data residency zone "
                    f"'{user_data_residency}' — cannot serve request"
                )

        # Step 2: prefer the session's home region (KV cache locality)
        if session_home_region:
            home = next((r for r in candidates
                         if r.name == session_home_region), None)
            if home:
                return home

        # Step 3: prefer nearest geo match with acceptable latency
        geo_match = [r for r in candidates
                     if r.gdpr_zone == user_geo_zone
                     and r.p50_latency_ms < self.LATENCY_CEILING_MS]
        if geo_match:
            return min(geo_match, key=lambda r: r.p50_latency_ms)

        # Step 4: global fallback — least loaded
        return min(candidates, key=lambda r: r.gpu_capacity_pct)

    def _healthy_with_capacity(self) -> list[RegionStatus]:
        return [
            r for r in self.regions
            if r.healthy and r.gpu_capacity_pct < self.CAPACITY_HEADROOM
        ]
```

### Session Stickiness — BROKEN Version (Missing Session Affinity)

```python
# BROKEN: routes by least-connections only, ignores session
# Every turn of a conversation may land on a different pod.
# The KV cache built for turn 1 is on pod-A; turn 2 lands on pod-B.
# pod-B recomputes the entire prefix from scratch on every turn.

import random

class BrokenRouter:
    def __init__(self, pods: list[str]):
        self.pods = pods
        self.connection_counts = {pod: 0 for pod in pods}

    def route(self, request: dict) -> str:
        # Picks least-loaded pod — no awareness of session_id
        pod = min(self.connection_counts, key=self.connection_counts.get)
        self.connection_counts[pod] += 1
        return pod
        # Result: KV cache thrashing.
        # 50-turn conversation with 10 pods:
        #   Expected cache hits: ~10% (only if random happens to pick same pod)
        #   GPU compute wasted: ~9x vs sticky routing
        #   Monthly GPU cost impact at 100K daily users: +$40,000–80,000
```

```python
# FIX: consistent hash on session_id ensures pod affinity
import hashlib

class StickyRouter:
    def __init__(self, pods: list[str]):
        self.pods = pods
        # Build virtual ring: 150 virtual nodes per pod
        self.ring: list[tuple[int, str]] = []
        for pod in pods:
            for i in range(150):
                key = f"{pod}:{i}"
                h = int(hashlib.md5(key.encode()).hexdigest(), 16)
                self.ring.append((h, pod))
        self.ring.sort(key=lambda x: x[0])

    def route(self, session_id: str, exclude_pods: set[str] | None = None) -> str:
        """
        Returns the pod for this session_id via consistent hashing.
        exclude_pods: set of pod names known to be unhealthy this request.
        """
        h = int(hashlib.md5(session_id.encode()).hexdigest(), 16)
        exclude = exclude_pods or set()
        for ring_hash, pod in self.ring:
            if ring_hash >= h and pod not in exclude:
                return pod
        # Wrap around ring
        for ring_hash, pod in self.ring:
            if pod not in exclude:
                return pod
        raise RuntimeError("No healthy pods in ring")
        # Result: same session_id always maps to same pod.
        # KV cache hit rate: ~100% for healthy pods.
        # Pod failure: exclude the failed pod, next ring entry takes over.
        # Only one cache miss per conversation at failover, not every turn.
```

### Cross-Region Failover — Mid-Stream Resume Decision

```python
from enum import Enum

class FailoverStrategy(Enum):
    RESUME = "resume"   # client sends last_token_id; new region generates from N+1
    RESTART = "restart" # discard partial response; new region regenerates from scratch

def decide_failover_strategy(
    tokens_delivered: int,
    total_estimated_tokens: int,
    session_history_bytes: int,
) -> FailoverStrategy:
    """
    Decide whether to resume or restart after a region failure mid-stream.

    Resume is preferred when:
    - Most of the response has been delivered (>70% complete)
    - Conversation history is short (cheap to recompute KV cache)

    Restart is preferred when:
    - Early in the response (< 30% delivered) — user barely noticed
    - History is long (resume requires loading and recomputing large context)
    """
    completion_fraction = tokens_delivered / max(total_estimated_tokens, 1)
    history_is_large = session_history_bytes > 50_000  # ~12K tokens

    if completion_fraction > 0.70 and not history_is_large:
        return FailoverStrategy.RESUME
    elif completion_fraction < 0.30:
        return FailoverStrategy.RESTART
    else:
        # Mid-stream: default to restart for simplicity; resume if client supports it
        return FailoverStrategy.RESTART
```

### Model Weight Sync — Cross-Region Replication Strategy

```python
"""
Model weight replication strategy.

Base model weights: replicate once on cluster bootstrap.
  - 70B model (140 GB bfloat16): S3 cross-region at ~5 Gbps = ~224 seconds
  - 405B model (810 GB): S3 cross-region at ~5 Gbps = ~1300 seconds (~22 min)
  - Schedule during off-peak; pre-warm before traffic shift

LoRA adapters (per customer fine-tune): replicate on write.
  - Typical LoRA adapter: 50-300 MB
  - S3 cross-region at 5 Gbps: <1 second for 300 MB
  - Use S3 Cross-Region Replication with RTC (Replication Time Control)
    for guaranteed 15-minute SLA on all objects — fine for adapter updates

Rollout coordination:
  - Never allow regions to diverge on base model version during active traffic
  - Blue-green: deploy new weights to all regions before any DNS weight shift
  - Use a global deployment lock (DynamoDB conditional write) to prevent
    two regions from simultaneously deploying different model versions
"""

import boto3
from datetime import datetime

s3 = boto3.client("s3")

def replicate_lora_adapter(
    adapter_s3_key: str,
    source_bucket: str,
    dest_buckets: dict[str, str],  # {"eu-west-1": "bucket-eu", ...}
) -> dict[str, str]:
    """
    Trigger cross-region replication for a LoRA adapter.
    Returns: mapping of region -> ETag of replicated object.
    """
    results = {}
    for region, bucket in dest_buckets.items():
        s3_regional = boto3.client("s3", region_name=region)
        # S3 batch replication for large objects; for <1 GB, direct copy
        response = s3_regional.copy_object(
            CopySource={"Bucket": source_bucket, "Key": adapter_s3_key},
            Bucket=bucket,
            Key=adapter_s3_key,
            MetadataDirective="COPY",
        )
        results[region] = response["CopyObjectResult"]["ETag"]
        # Verify checksum before marking as ready
    return results
```

---

## 7. Real-World Examples

**OpenAI**: OpenAI operates GPU clusters in multiple Microsoft Azure regions (East US 2, West Europe, Southeast Asia). Public signals from the status.openai.com incident history show that November 2023 outages cascaded because session token validation ran as a single-region service — when that service experienced elevated latency, all global inference endpoints became degraded even though GPU capacity was fine. Post-incident, OpenAI distributed authentication to regional replicas. Their architecture uses latency-based routing with session affinity; the ChatGPT mobile client sends a `conversation_id` header that maps to a specific inference cluster.

**Anthropic**: Anthropic's Claude API serves requests globally with primary infrastructure in us-east-1 and eu-west-1. The API documentation notes that requests are routed to the nearest available region based on the client's geographic location. During eu-west-1 maintenance windows, traffic is redirected to us-east-1 with increased TTFT (typically 85–95 ms additional latency for EU users). Anthropic publishes region availability in their API status page and exposes a `claude-region` response header for debugging.

**Together AI**: Together AI operates a multi-region GPU fleet across AWS and CoreWeave. Their routing layer selects models across regions based on real-time queue depth and GPU availability. For open-source models (Llama 3, Mistral), they maintain identical weight copies in each region and use consistent hashing on request_id (not session_id, since they primarily serve stateless batch requests). For stateful fine-tuned endpoints, they route by customer-assigned cluster affinity.

**Cloudflare Workers AI**: Cloudflare's AI Gateway runs inference at Cloudflare's global PoP network (300+ cities). Smaller models (under 7B parameters) are replicated to most PoPs; larger models run only at Tier 1 PoPs (roughly 20 locations). The routing uses anycast BGP: a user's DNS query resolves to the anycast IP, and BGP routing delivers the TCP connection to the nearest PoP that has GPU capacity for the requested model. KV cache stickiness is limited — Workers AI is designed for stateless inference; multi-turn conversation state must be managed by the application layer using the AI Gateway's `cf-aig-cache-key` header for exact-match prompt caching.

---

## 8. Tradeoffs

### Deployment Strategy Comparison

| Dimension | Single Region | Active-Active | Active-Passive | Regional Sharding |
|-----------|--------------|---------------|----------------|-------------------|
| TTFT for distant users | +85-170 ms | <20 ms | +85-170 ms when failed | <20 ms in home region |
| Availability SLA | 99.9% | 99.99% | 99.95% | 99.95% per region |
| GPU cost multiplier | 1.0× | 2.0-3.0× | 1.4-1.6× | 1.5-2.0× |
| Operational complexity | Low | High | Medium | Very High |
| Data residency | Not guaranteed | Configurable | Configurable | Hard guarantee |
| GPU utilization | ~90% | ~45-50% | ~65% | ~70% |
| KV cache hit rate | ~95% | ~90% (same-region) | ~95% (pre-failover) | ~95% |
| Model version risk | Low | High (skew risk) | Low | Medium |

### Conversation Stickiness vs Load Distribution

| Approach | KV Cache Hit Rate | GPU Load Balance | Operational Risk |
|----------|------------------|------------------|-----------------|
| No stickiness (random LB) | ~10% (10 pods) | Perfect | Low — no state |
| Session-level stickiness | ~95% | Good (hash distribution) | Pod failures cause 1 miss |
| User-level stickiness | ~95% | Poor (hot users) | Power users monopolize pods |
| IP-based stickiness | ~70% (NAT breaks it) | Good | CDN/proxy changes break it |

---

## 9. When to Use / When NOT to Use

**Use active-active multi-region when:**
- SLA commitment is 99.99% or higher — single-region cannot achieve this
- EU and US users constitute significant traffic fractions (>20% each) and TTFT SLA is under 500 ms
- GDPR or data sovereignty regulations prohibit cross-border data transfer, making regional isolation mandatory
- User base exceeds 10 million DAU — at this scale, a regional outage affects millions of users simultaneously
- GPU cost budget exceeds $100,000/month — below this, the 2× multiplier from active-active is often cheaper than the engineering cost of maintaining the topology

**Use active-passive with DNS failover when:**
- SLA commitment is 99.9%–99.95% — 60–120 second RTO is acceptable
- Traffic is predominantly from one geographic region (>80%)
- GPU budget is under $100,000/month but availability beyond single-region is required
- Team has fewer than 5 infrastructure engineers — active-passive is manageable; active-active is not

**Use single region with CDN edge when:**
- User base is under 1 million DAU
- GPU budget is under $50,000/month
- Startup or early-growth phase — operational simplicity outweighs availability guarantees
- Product does not have 99.99% SLA commitments
- The 85–170 ms cross-region latency penalty is acceptable for the use case (batch processing, non-real-time)

**Do NOT use active-active when:**
- The cost of 2–3× GPU capacity exceeds the revenue impact of downtime
- The engineering team cannot maintain globally coordinated model deployments
- All users are in one geographic region — active-active adds cost with no user benefit
- GDPR requires strict regional isolation — active-active with failover across regions is non-compliant without explicit per-request residency enforcement at every routing layer

---

## 10. Common Pitfalls

**Pitfall 1: Single-region auth service in a multi-region GPU fleet**
This was the root cause of OpenAI's November 2023 cascading outage. The GPU serving infrastructure was distributed across regions. The JWT validation service that every request had to call before reaching inference was deployed in a single region. When that region experienced elevated latency (not even an outage — just 200 ms extra), all inference globally stalled behind authentication timeouts. Fix: replicate stateless auth services to every region where inference runs; never make inference depend on a single-region RPC call in the hot path. Validate JWTs locally using public key caching (JWKS endpoint cached with 1-hour TTL).

**Pitfall 2: KV cache stickiness ignored — 10× GPU waste**
A team built a chat application serving 100,000 daily users with an average of 30 turns per conversation and a 4,000-token system prompt. They used a round-robin load balancer for simplicity. Measuring GPU utilization two weeks post-launch, they found it was running at 8× the projected compute cost. Root cause: every turn of every conversation was recomputing 4,000 tokens of system prompt plus all prior turns on a random pod with no KV cache. Switching to consistent-hash routing on session_id reduced GPU compute by 87% and monthly cost from $180,000 to $23,000 at 100,000 DAU.

**Pitfall 3: GDPR violation via failover routing**
A European SaaS product deployed active-active across eu-west-1 and us-east-1. During an eu-west-1 partial outage affecting 30% of pods, the anycast router started sending EU user traffic to us-east-1 pods. Conversation history containing PII (names, addresses, email content) was processed in the United States. The company's DPA (Data Processing Agreement) with EU customers prohibited this. The incident triggered a GDPR Article 32 investigation. Fix: tag every session JWT with `data_residency: EU`; enforce in the routing proxy that EU-tagged sessions never reach non-EU pods; disable cross-region failover for GDPR-scoped users (serve a 503 instead); notify users of degradation rather than silently violating residency.

**Pitfall 4: Model version skew between regions during phased rollout**
A team rolled out a new fine-tuned model version to us-east-1 (50% of traffic) while eu-west-1 remained on the previous version. A user who chatted from the US airport lounge and then resumed from their EU office got inconsistent behavior — the model's personality, refusals, and formatting conventions changed mid-conversation. Customer complaints described the AI as "confused" and "contradicting itself." Fix: treat model version as a globally coordinated deployment; use blue-green rollout that deploys new weights to all regions simultaneously before shifting any traffic. Use a global deployment lock in DynamoDB: a conditional write on `model_deployment_in_progress = false` ensures only one deployment proceeds at a time.

**Pitfall 5: DNS TTL too long during active-passive failover**
A team configured their DNS records with a 300-second TTL (5 minutes) to reduce DNS query load. When the primary region failed, DNS failover updated the record — but clients that had recently resolved the old IP cached it for up to 5 more minutes. Effective RTO was not 60 seconds (health check interval + DNS update) but 360 seconds. Many clients and CDNs ignore DNS TTL and cache even longer. Fix: set DNS TTL to 60 seconds for records that participate in health-check-based failover. Use AWS Global Accelerator or Cloudflare's anycast-based failover instead of DNS-based failover for sub-30-second RTO — these operate at the network layer, not the DNS layer.

---

## 11. Technologies & Tools

| Tool | Category | Role in Multi-Region LLM |
|------|----------|--------------------------|
| AWS Global Accelerator | Anycast routing | Static anycast IPs, sub-30s failover via BGP; routes to nearest healthy AWS region |
| Cloudflare Load Balancing | Anycast + health checks | Geo-steering, health-check-based failover, session affinity via cookie or header |
| Envoy Proxy | Sidecar/edge proxy | Ring hash load balancing within a region; per-pod health checks; retry/circuit breaker |
| Consul | Service registry | Cross-region service discovery; health check aggregation; KV store for routing config |
| Redis Cluster | Session state store | Cross-region conversation history; async replication between regional clusters |
| DynamoDB Global Tables | Global state | Strongly consistent session metadata; deployment locks; GDPR residency tags |
| AWS S3 + CRR | Model weight storage | Cross-region replication for model checkpoints and LoRA adapters |
| vLLM | Inference engine | PagedAttention for KV cache memory efficiency; continuous batching; see [vLLM Deep Dive](../../vllm_deep_dive/README.md) |
| Terraform | IaC | Multi-region GPU cluster provisioning; region parity enforcement via shared modules |
| Datadog / Grafana | Observability | Cross-region latency dashboards; per-region GPU utilization; cache hit rate tracking |

### Anycast vs DNS Failover vs Application-Layer Routing

| Approach | Failover Time | Requires Code Change | Handles Mid-Stream | Cost |
|----------|--------------|---------------------|-------------------|------|
| BGP Anycast (Global Accelerator) | 10–30 s | No | No (RST + retry) | $0.025/GB + $2.50/accelerator/hr |
| Cloudflare Load Balancing | 15–60 s | No | No | $5–200/month |
| DNS health-check failover (Route 53) | 60–180 s | No | No | $0.50/health check/month |
| Application-layer routing (custom) | 0–5 s | Yes | Yes (if designed for it) | Engineering cost |

Related: [Deployment and MLOps](../../deployment_and_mlops/README.md) for serving infrastructure patterns; [LLM Routing and Model Selection](../../llm_routing_and_model_selection/README.md) for application-level routing across models.

---

## 12. Interview Questions with Answers

**Q: Why does standard round-robin load balancing fail for LLM applications?**
Round-robin distributes requests evenly across pods but ignores KV cache locality. Each LLM inference pod caches the key-value tensors for processed tokens on its local GPU memory. When a follow-up turn in a conversation lands on a different pod, the new pod has no cache and must recompute all prior tokens from scratch. For a 20-turn conversation with a 4,000-token system prompt and 200 tokens per turn, recomputing every turn wastes approximately 24 ms of H100 GPU compute per cache miss — 10× the cost of a cache hit. At scale, this translates to a 5–10× GPU fleet size increase for the same user-facing throughput. Use consistent hashing on session_id at the load balancer layer.

**Q: Why hash on session_id for pod affinity rather than user_id or IP address?**
User_id is too coarse: one user may have 5 concurrent chat sessions that should be distributed across pods for load balance; hashing on user_id forces all 5 to the same pod, creating hotspots. IP address is unreliable: corporate NAT gateways share one IP across thousands of users; conversely, mobile users change IPs across WiFi/LTE transitions, breaking affinity mid-conversation. Session_id is the right granularity — it is unique per conversation, stable for the conversation's lifetime, and provides even hash distribution across the pod ring. Always include the session_id in the JWT or as a dedicated header (X-Session-ID) and validate it before routing.

**Q: What is the trade-off between active-active and active-passive multi-region?**
Active-active provides lower RTO (30–90 seconds vs 60–120 seconds), lower latency for geographically distributed users (requests served from local region vs all requests to primary), and higher availability SLA (99.99% vs 99.95%). The cost is 2–3× the GPU fleet because each region must handle 100% of peak traffic as a failover target, plus the engineering complexity of coordinating model deployments, session state replication, and GDPR routing enforcement globally. Active-passive is cheaper (1.4–1.6× multiplier) and simpler to operate, but the 60–120 second RTO is unacceptable for 99.99% SLA commitments and distant users always suffer the cross-region latency penalty.

**Q: What is the RTO for a streaming LLM response when a region fails mid-stream?**
RTO for in-flight streaming responses is 15–35 seconds in a well-designed active-active setup: approximately 5 seconds for the health probe to detect the failure, 10 seconds for anycast re-routing to propagate, and 5–20 seconds for the client to retry, reach the new region, load conversation history from cross-region Redis, recompute the KV cache, and resume generation. The KV cache cold start on the new region adds 100–500 ms per 1,000 tokens of conversation history. For active-passive with DNS failover, the RTO is 60–180 seconds. The in-flight response is always lost at the network layer — the question is only whether to resume from the last delivered token or restart generation.

**Q: How would you enforce GDPR data residency in a multi-region routing layer?**
Mint a JWT at authentication time that includes a `data_residency` claim (e.g., `"EU"` for users subject to GDPR). The routing proxy (Envoy, Nginx, or custom) validates this claim before forwarding the request and rejects any routing decision that would send an EU-tagged session to a non-EU pod. Critically, disable cross-region failover for EU-tagged sessions: serve a 503 with a `Retry-After` header rather than silently routing to us-east-1. Log every routing decision with the session's residency tag for audit purposes. Test failover paths explicitly: simulate eu-west-1 failure in staging and verify that EU user requests return 503 rather than reaching us-east-1.

**Q: How do you replicate model weights across regions without impacting serving availability?**
Use S3 Cross-Region Replication with Replication Time Control (RTC), which guarantees 99.99% of objects replicate within 15 minutes. For base model weights (140 GB for a 70B model), replicate once during cluster bootstrap before any traffic is served. For LoRA adapters (50–300 MB), trigger replication immediately on write and wait for confirmation before serving requests with the new adapter in the destination region. Use a deployment lock (DynamoDB conditional write) to prevent serving requests with the new adapter until replication is confirmed. Never hot-swap weights under active inference load: use blue-green deployment with a staging pod group that loads new weights while the current group continues serving.

**Q: How does GPU regional availability affect capacity planning?**
As of 2024, roughly 60% of publicly available cloud H100 capacity is in US regions, 25% in EU regions, and 15% in APAC. This means an active-active topology with equal-sized regional clusters will overprovision APAC (only 15% of global demand, but the cluster must handle 33% of traffic when other regions fail) and underprovision US regions relative to actual demand. Plan regional cluster sizes proportional to regional traffic plus failover headroom: if APAC is 15% of normal traffic but must absorb 33% of traffic when US and EU fail simultaneously, APAC needs 2.2× its normal traffic capacity. This is only economically viable if APAC-specific H100 quota is available — in 2024, this often required 6+ month reserved instance commitments.

**Q: What happens to a user's conversation if the pod serving it crashes mid-generation?**
The streaming HTTP/2 connection is reset (RST). The client should implement retry with exponential backoff (50 ms base, 2× multiplier, max 10 seconds, with jitter) and resend the request with the same session_id header and a `last_token_index` indicating how many tokens were received. The routing proxy, using consistent hashing, maps the session_id to the next healthy pod in the ring (the failed pod has been ejected from the health pool). The new pod reads the full conversation history from Redis, recomputes the KV cache, and if `last_token_index` is provided, resumes generation from token N+1. The resume strategy is only worthwhile if >70% of the response was delivered; otherwise restart generation from scratch to avoid incoherent partial sentences.

**Q: How do you handle model version skew across regions during a phased rollout?**
Use a global deployment gate: before shifting any traffic to a new model version, require that all regions have successfully loaded the new weights and passed inference health checks. Implement a DynamoDB Global Table entry with `current_model_version` and `deploying_model_version` fields. A region only marks itself as deployment-complete after loading weights and passing smoke tests. The traffic shift (DNS weight change or anycast policy update) is a single atomic operation that occurs only after all regions confirm readiness. Monitor for version skew during rollout using a Datadog metric `llm.model_version` tagged by region — alert if any two regions serving traffic differ by version.

**Q: What is the cost of cross-region session state replication and how do you minimize it?**
Redis cross-region replication costs approximately $0.02/GB transferred per replication event plus the egress fee ($0.09/GB cross-region on AWS). For a 50-turn conversation with 200 tokens per turn (≈800 bytes per turn after serialization), total session state is approximately 40 KB. At 100,000 active sessions replicated across 3 regions: 40 KB × 100,000 sessions × 2 replications = 8 GB/day of replication traffic = $0.72/day in transfer fees, which is negligible. The actual cost driver is the Redis cluster itself (3 shards × 3 regions × $200/month/shard = $1,800/month). Minimize Redis state size by storing only the last N turns (N=10 is typically sufficient for KV cache recomputation) rather than the full conversation history.

**Q: How does Cloudflare Workers AI achieve low-latency global LLM serving?**
Cloudflare runs inference at its Tier 1 PoPs (approximately 20 major cities globally) that have GPU hardware. BGP anycast routes each user's TCP connection to the nearest PoP with available model capacity. For models under 7B parameters (Llama 3.2 3B, Gemma 2 2B), Cloudflare replicates weights to most PoPs because the storage cost (3–6 GB) is low enough. For larger models (70B, 405B), weights are only at Tier 1 PoPs. The practical result: a Tokyo user accessing a 7B model gets inference at the Tokyo PoP (<5 ms routing overhead); the same user accessing a 70B model might route to the Singapore or Tokyo Tier 1 PoP. KV cache stickiness is limited because Workers AI is designed for stateless inference — persistent multi-turn stickiness requires the application to use the AI Gateway's session routing feature.

**Q: When should you disable cross-region failover for certain user segments?**
Cross-region failover should be disabled for: (1) GDPR-regulated EU users when no compliant EU region is available — serve 503 rather than route to us-east-1; (2) Users with active financial transactions where conversation continuity is required for compliance audit trails; (3) Users in regulated industries (healthcare, finance) where data locality is contractually required. Implement a `failover_policy` field in the session JWT: `"allow_cross_region"`, `"same_continent_only"`, or `"no_failover"`. The routing proxy enforces this policy; when failover is blocked, return a structured error with `Retry-After: 30` to prompt the client to wait for the home region to recover rather than attempting cross-region routing.

**Q: How do you measure whether your multi-region routing is working correctly?**
Track four key metrics per region: (1) KV cache hit rate — computed as `(requests where pod had prior session cache) / total requests`; target >90% in steady state; a sudden drop indicates sticky routing failure; (2) TTFT (time-to-first-token) by user_geo_zone — cross-region TTFT should be 85–170 ms higher than same-region; if cross-region TTFT equals same-region, routing is not geo-aware; (3) Failover activation rate — how often a region ejects pods and re-routes; sustained >5% ejection rate indicates capacity or stability problems; (4) Session migration count — how many sessions moved regions; a sudden spike indicates a region failure or routing bug. Instrument all metrics with `region_origin` and `region_served` tags to detect cross-region routing events.

**Q: What is the minimum viable multi-region architecture for a startup with $50K/month GPU budget?**
Single active region (us-east-1) with Redis session state stored in a managed service with automatic cross-region backup (ElastiCache Global Datastore or Upstash Redis). DNS health-check failover to a warm standby in eu-west-1 with 20% of normal capacity (enough to serve reduced traffic during an emergency). Total cost: primary region $40K/month GPU + standby $10K/month GPU = $50K/month. RTO: 90–120 seconds. This achieves ~99.9% availability without the engineering overhead of active-active. Upgrade to active-active when: user base crosses 5M DAU, SLA commitment requires 99.99%, or EU users exceed 20% of traffic (at which point cross-region TTFT is the primary pain point).

**Q: How do you handle a LoRA adapter update that needs to be available in all regions within 5 minutes?**
Use S3 Cross-Region Replication with Replication Time Control (RTC) which guarantees 99.99% of objects replicate within 15 minutes — too slow for a 5-minute SLA. Instead, use parallel direct uploads: when a new adapter is committed, trigger simultaneous S3 PutObject calls to all regional buckets from a central coordination service. At 5 Gbps (typical inter-region link), a 300 MB adapter uploads in under 5 seconds. The coordination service waits for all regional PutObject responses before publishing a "adapter ready" event to each region's inference pods via SNS. Each region's pod pool loads the adapter from local S3 (no cross-region read required post-upload). Total latency: upload + propagation + pod hot-load = typically 30–60 seconds. This is the pattern used by multi-tenant fine-tune serving platforms to provide rapid adapter deployment without model restarts.

---

## 13. Best Practices

1. **Hash on session_id, not user_id, for GPU pod stickiness.** One user can have multiple concurrent conversations. Hashing on user_id routes all of a user's conversations to one pod, creating hotspots and poor load distribution. Session_id gives conversation-level granularity with even hash distribution.

2. **Provision each region's GPU fleet to handle 100% of peak traffic, not 100%/N.** In an N-region active-active topology, the failure of N-1 regions must not degrade the surviving region. Size each region for full peak load, not a fraction. The economic model: 2× GPU cost buys 99.99% SLA; the business must decide if that SLA commitment justifies the cost.

3. **Store conversation history in a cross-region replicated database, not in the serving pod.** Pod memory (KV cache) is a performance cache, not the source of truth. Pods crash, scale down, and are replaced. Conversation history must survive pod lifecycle events. Redis Cluster with async cross-region replication (ElastiCache Global Datastore) or DynamoDB Global Tables are the standard choices.

4. **Coordinate model version deployments globally before shifting traffic.** Never allow two regions to serve different model versions simultaneously under active user traffic. Use a global deployment lock and require all regions to confirm weight load before updating any routing policy. A 10-minute globally coordinated deployment window is cheaper than the customer support cost of inconsistent behavior.

5. **Enforce data residency at the JWT layer, not only at the routing proxy.** Routing proxy configuration can drift. The JWT's `data_residency` claim must be validated at every layer — routing proxy, application server, and database query — to prevent a misconfigured proxy from silently violating residency. Treat residency violation as a critical alert, not a warning.

6. **Set DNS TTL to 60 seconds for records participating in health-check failover.** The common misconfiguration of 300-second TTL adds 4 extra minutes to RTO. For sub-60-second RTO, use anycast (AWS Global Accelerator, Cloudflare) which operates at the BGP layer rather than the DNS layer.

7. **Track KV cache hit rate as a first-class infrastructure metric.** A healthy multi-region deployment with sticky routing should see >90% cache hit rate in steady state. Instrument this metric per region and per model. A sudden drop in cache hit rate (e.g., after a rolling pod restart or routing configuration change) indicates that session affinity has broken and GPU costs will spike within hours.

8. **Implement client-side retry with last_token_index for streaming resilience.** Clients should track how many tokens they have received from a streaming response. On connection reset, retry with `X-Last-Token-Index: N` so the new region can resume generation from token N+1 rather than restarting. This halves the user-visible disruption for mid-stream failures. Without this, every regional failover event produces a visible "response disappeared and restarted" experience.

9. **Use LoRA adapters instead of full fine-tuned weights for multi-tenant multi-region serving.** A 70B base model (140 GB) replicated to 3 regions costs 420 GB of storage plus ~6 minutes of replication time. Ten customer LoRA adapters at 300 MB each total 3 GB — replicating all ten simultaneously takes under 30 seconds. Design adapter-per-tenant architectures that hot-swap adapters at inference time rather than maintaining separate full-model copies per customer.

10. **Test failover paths quarterly with production traffic.** Netflix's Chaos Engineering principle applies here: if you have not tested your failover, you do not have a failover. Run planned regional failure drills during low-traffic windows. Verify: (a) RTO matches the target, (b) GDPR-tagged sessions return 503 rather than routing cross-region, (c) conversation history is intact after failover, (d) GPU costs in the surviving region match the expected 2× surge.

---

## 14. Case Study

### ChatGPT — Conversation Stickiness at Scale

The ChatGPT architecture (see [design_chatgpt.md](../design_chatgpt.md)) operates at a scale where multi-region topology is non-negotiable: hundreds of millions of conversations per day across a global user base. The key architectural challenge is that ChatGPT's UX is fundamentally multi-turn — users engage in 10–50 turn conversations — which means KV cache locality directly determines GPU fleet size.

OpenAI's public incident reports (status.openai.com) reveal that their routing layer uses a combination of Microsoft Azure's anycast infrastructure (via Azure Front Door) and application-layer session affinity. Each ChatGPT conversation receives a stable `conversation_id` at creation time, analogous to the session_id pattern described in this document. Azure Front Door routes requests to the nearest healthy region; within the region, an Envoy-based proxy layer performs consistent hashing on `conversation_id` to select the specific inference pod. The November 2023 outage demonstrated the critical dependency on regional auth service availability — a lesson that directly informed the distributed authentication architecture described in Section 10's Pitfall 1.

The conversation stickiness design means that during a phased rollout of a new GPT-4 version, ChatGPT must simultaneously coordinate weight deployment across all active regions before shifting traffic. This is why GPT-4 version transitions are visible to users as sudden global changes rather than gradual A/B rollouts — the globally coordinated deployment gate prevents the model version skew described in Section 10's Pitfall 4.

### LLM Gateway — Cross-Region Provider Routing

The LLM gateway design (see [design_llm_gateway.md](../design_llm_gateway.md)) adds a layer of complexity absent from single-model deployments: it routes across multiple LLM providers (OpenAI, Anthropic, Cohere, self-hosted), each with their own regional availability, pricing, and latency characteristics. The multi-region topology for a gateway must solve routing at two levels: which region of the gateway to use (closest to the user), and which provider endpoint within that region to use (based on cost, latency, model capability, and provider health).

The gateway's regional deployment follows the active-active pattern described in Section 4. Each gateway region maintains its own provider health metrics, latency measurements, and circuit breaker state. Provider health information is shared across gateway regions via a lightweight gossip protocol (Consul or a custom Pub/Sub implementation) so that if Anthropic's eu-west-1 endpoint is degraded, all gateway regions know to prefer the us-east-1 Anthropic endpoint or fall back to OpenAI. The session stickiness challenge is simpler for the gateway than for self-hosted serving: most provider APIs are stateless (conversation history is sent in each request), so the gateway does not need KV cache locality — it needs only to maintain sticky routing for rate-limit accounting (to avoid double-counting tokens against per-key quotas across regions).

### Real-Time Translation — Latency-Critical Routing

The real-time translation case study (see [design_real_time_translation.md](../design_real_time_translation.md)) represents the most latency-sensitive workload in this repo: users expect sub-300 ms end-to-end latency for live speech translation. At this latency budget, the routing decision itself must complete in under 5 ms, and cross-region RTT (85–170 ms) consumes more than half the budget before inference even begins.

The real-time translation architecture is therefore forced into the strictest form of geographic routing: a user's requests must always land in the nearest region, with no cross-region fallback allowed (the 85+ ms penalty makes translation unusable). Regional isolation is enforced at the DNS layer with very short TTLs (30 seconds) and at the application layer via the `home_region` JWT claim. GPU capacity planning for this use case must assume that regional failures will result in service degradation (translation becomes unavailable in that geography) rather than cross-region failover — the latency cost of failover exceeds the usability threshold. This is the edge case where the analysis in Section 9's "When NOT to use active-active" applies: the multi-region topology cannot actually rescue the user experience when latency is this tight.

### GPU Inference Platform — Multi-Tenant Regional Distribution

A multi-tenant GPU inference platform (the design_gpu_inference_platform case study, referenced from the LLM gateway and deployment modules) must solve an additional problem absent from single-tenant architectures: different tenants have different SLA tiers, data residency requirements, and model versions. A Tier 1 enterprise tenant with EU data residency and a custom fine-tuned model must be served by EU-region pods running that specific LoRA adapter. A free-tier tenant with no residency requirement should route to whichever region has available capacity, possibly absorbing excess capacity in less-loaded regions.

The platform implements a three-level routing hierarchy: (1) hard constraints (data residency, model version availability), (2) soft preferences (tenant SLA tier determines priority in the queue, with Tier 1 tenants preempting Tier 3 in capacity-constrained scenarios), and (3) optimization objectives (minimize GPU waste, maximize cache hit rate). This hierarchy is evaluated in the routing proxy in under 2 ms using pre-computed tenant routing tables cached in local memory with a 60-second refresh from Consul. The LoRA adapter replication strategy described in Section 6 and Best Practice 9 is central to this design: maintaining one 140 GB base model per region with hot-swappable per-tenant adapters is operationally and economically feasible; maintaining one full fine-tuned model per tenant per region is not.

---

*Cross-references: [vLLM Deep Dive](../../vllm_deep_dive/README.md) for PagedAttention and KV cache internals; [Deployment and MLOps](../../deployment_and_mlops/README.md) for serving infrastructure patterns; [LLM Routing and Model Selection](../../llm_routing_and_model_selection/README.md) for application-layer model routing; [Inference Engines](../../inference_engines/README.md) for vLLM, TensorRT-LLM, and SGLang regional deployment.*
