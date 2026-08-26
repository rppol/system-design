# Backend Audit — 2026-08-25

## 0. Snapshot

`backend/` is the repo's "engineering discipline" section — 35 modules across 9 phases, from OSI
layers through microservices, plus 5 case studies. It is in genuinely good shape on the axes the
2026-08-04 pass measured: **every module has all 14 template headings, every module clears the
15-Q&A floor, `**Short:**` coverage is complete, and the factual audit visibly held** (OWASP is on
the 2025 edition with the correct A01–A10 remap; `http_protocols` cites W3Techs July 2026 figures;
`rest_api_design` correctly describes the Idempotency-Key IETF draft as expired-and-not-an-RFC;
`event_sourcing_and_cqrs` explicitly reasons about Axon 4 vs 5 and the EventStoreDB→KurrentDB
rename). Real strengths: `rabbitmq_deep_dive` and `rate_limiting_in_depth` are the best
implementation-level writing in the repo, and `distributed_transactions_and_consistency` is a
genuinely principal-grade module.

**The single biggest weakness is that the section's shape is frozen in ~2021.** It is organised
around request/response HTTP, brokers, and Kubernetes — and it has *zero* coverage of the three
things a 2026 backend engineer is actually hired for: serving AI workloads (streaming, admission
control, token budgets, GPU queueing), outbound event delivery (webhooks), and API contract
governance (AsyncAPI, spec linting, breaking-change gates). A topic-presence sweep across all
30,907 lines returns **zero hits** for CloudEvents, Connect RPC, Buf Schema Registry, passkeys,
WebAuthn, SPIFFE, OpenFGA, SpiceDB, Cedar, RFC 9421, Kubernetes Gateway API, Istio ambient/ztunnel,
Prometheus native histograms, OpenFeature, Kafka share groups, tiered storage, Redpanda, Temporal,
Restate, Universal Scalability Law, GCRA, Chaos Mesh, pgroll, ECH, post-quantum TLS, and Valkey.
The secondary weakness is structural: **4 of 5 case studies do not use the mandated 11-section
principal template at all**, and the section has **0 deep-dive sub-files** across 35 modules while
`llm/` has 85.

| Modules | Case studies | Sub-files | Total lines | Total Q&As | Avg lines/module |
|---|---|---|---|---|---|
| 35 | 5 | **0** | 30,907 (incl. README/CLAUDE/index) | 576 module + 22 case-study | 762 |

Distribution: smallest module 540 (`performance_profiling`), largest 1,713 (`rabbitmq_deep_dive`).
Q&As per module: 15 min, 45 max, median 16. Mermaid diagrams: 189 across the section (median 5.5).

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `backend/serving_ai_workloads/serving_ai_workloads.md` | module | 2026 backend job posts explicitly require "LLM integration … streaming, structured outputs, function calling"; the Kubernetes **Gateway API Inference Extension is GA**, introducing `InferencePool`/`InferenceModel` because "LLM inference sessions are often long-running, resource-intensive, and partially stateful" — a backend-layer problem, not a model problem. Section has **0 mentions** of LLM/inference/token-streaming. [kubernetes.io](https://kubernetes.io/blog/2025/06/05/introducing-gateway-api-inference-extension/), [talent500](https://talent500.com/blog/backend-developer-skills-2026/) | **P0** | 800 |
| `backend/webhooks_and_event_delivery/webhooks_and_event_delivery.md` | module | Webhooks are the default integration surface for every SaaS and the **Standard Webhooks** spec plus **RFC 9421 HTTP Message Signatures** (published Feb 2024) are now the reference designs; Spring Security has an open issue to support RFC 9421. The whole section contains **5 incidental mentions** of "webhook" and no module. [rfc-editor](https://www.rfc-editor.org/info/rfc9421/), [webhookify 2026 guide](https://webhookify.app/guides/complete-guide-to-webhooks) | **P0** | 750 |
| `backend/api_contracts_and_schema_governance/api_contracts_and_schema_governance.md` | module | **OpenAPI 3.2.0** shipped 2025-09-19 with first-class streaming media types; **AsyncAPI 3.1.0** shipped 2026-01-31 and is in production at Salesforce/SAP/Adidas/Deutsche Telekom. Section-wide: AsyncAPI appears in **1 file**, JSON Schema / Arazzo / Spectral / Buf Schema Registry in **none**. `rest_api_design` §11 lists 8 tools, all doc-render or test clients — no linter, no diff tool, no registry. [spec.openapis.org](https://spec.openapis.org/oas/), [asyncapi.com](https://www.asyncapi.com/blog/release-notes-3.1.0) | **P0** | 700 |
| `backend/durable_execution_and_workflows/durable_execution_and_workflows.md` | module | "Durable execution stopped being a niche in 2025, with Temporal, Restate, Inngest, Hatchet, and DBOS crowding into the category"; it is now the default answer to the multi-step-saga question the section teaches by hand. Section names **Cadence** (2017) but **never Temporal, Restate or DBOS**. [kai-waehner.de](https://www.kai-waehner.de/blog/2025/06/05/the-rise-of-the-durable-execution-engine-temporal-restate-in-an-event-driven-architecture-apache-kafka/) | **P1** | 700 |
| `backend/multi_tenancy_and_isolation/multi_tenancy_and_isolation.md` | module | "Most production SaaS platforms in 2026 run a hybrid isolation model"; noisy-neighbour and pool/bridge/silo are now standard senior interview ground, with dedicated 2026 question banks. Section has **9 incidental mentions**, no module. [algoroq multi-tenancy interview bank](https://www.algoroq.io/interview-questions/multi-tenancy/), [techinterview.org](https://www.techinterview.org/post/3233460870/system-design-multi-tenant-saas/) | **P1** | 700 |
| `auth_and_authorization_systems/passkeys_and_authn_frontier.md` | sub-file | Module covers OAuth 2.1 / PKCE / DPoP well but has **zero** on passkeys, WebAuthn L3, FIDO2, CIBA, SD-JWT, SCIM, PAR (RFC 9126), RAR (RFC 9396), token exchange (RFC 8693). Passwordless is the live 2026 auth story. | **P0** | 450 |
| `auth_and_authorization_systems/fine_grained_authorization.md` | sub-file | Module spends its authorization budget on RBAC vs ABAC and never names **Zanzibar, OpenFGA, SpiceDB, Permify, or Cedar** — the actual 2026 answer to "how do you do per-object permissions at scale". OPA/Rego is present; ReBAC is absent. | **P0** | 450 |
| `observability_and_monitoring/modern_otel_stack.md` | sub-file | **Prometheus native histograms became stable in v3.8.0** and shipped in the **v3.13.0 LTS (1 July 2026)**; Prometheus 3 ingests OTLP directly. The module teaches classic bucket histograms exclusively and never mentions native histograms, exemplars, OTel semconv stability, the profiling signal, Grafana Alloy, eBPF auto-instrumentation, or tail-based sampling. [prometheus.io](https://prometheus.io/docs/specs/native_histograms/) | **P0** | 500 |
| `grpc_and_protobuf/connect_and_browser_rpc.md` | sub-file | **Connect RPC** now speaks Connect + gRPC + gRPC-Web simultaneously and is in production at Anthropic; the module covers gRPC-Web's trailers problem but never names the thing that solved it, nor the Buf Schema Registry, nor gRPC over HTTP/3. [buf.build](https://buf.build/blog/connect-a-better-grpc), [kmcd.dev](https://kmcd.dev/posts/connectrpc-where-is-it-now/) | **P1** | 400 |
| `service_mesh_and_service_discovery/ambient_mesh_and_workload_identity.md` | sub-file | Istio **ambient mode** (ztunnel + waypoint) is GA and is the 2025–2026 Istio roadmap's stated migration target, cutting mesh memory overhead ~70%; the module is sidecar-only and also lacks SPIFFE/SPIRE, Cilium mesh, and the Kubernetes Gateway API. [istio.io dataplane modes](https://istio.io/latest/docs/overview/dataplane-modes/) | **P1** | 450 |
| `api_gateway_patterns/gateway_api_and_modern_proxies.md` | sub-file | §11 lists Spring Cloud Gateway, Kong, Nginx Plus and managed clouds; **the Kubernetes Gateway API is GA and is Ingress's successor**, and the Inference Extension extends "Envoy Gateway, kgateway, and GKE Gateway" into inference gateways. Envoy Gateway, APISIX, Traefik 3 and Kong AI Gateway all absent. [kubernetes.io](https://kubernetes.io/blog/2025/06/05/introducing-gateway-api-inference-extension/) | **P1** | 450 |
| `rabbitmq_deep_dive/rabbitmq_operations.md` | sub-file | Pure split: the 1,713-line page is the section's only file over 1,200 lines and the section has zero sub-files. Move §"Policies vs queue arguments", "Virtual hosts", "Flow control", "Resource alarms", "Sharding a hot queue" out. No new content required. | **P2** | (split) |
| `http_protocols/transport_security_frontier.md` | sub-file | **X25519MLKEM768 is the default in Chrome 131+/Firefox 132+/Edge 131+ and ~95% of Cloudflare's post-quantum connections**; 49.3% of domains now offer hybrid key exchange. Neither `http_protocols` nor `udp_and_quic` mentions post-quantum TLS, ECH, or QUIC multipath. [cloudflare PQC docs](https://developers.cloudflare.com/ssl/post-quantum-cryptography/) | **P1** | 400 |
| `case_studies/design_llm_serving_api/design_llm_serving_api.md` | case study | Capstone for `serving_ai_workloads`; "design an inference batching system for a single GPU handling up to 100 inputs per batch while users wait synchronously" is a named recurring 2026 interview prompt. [tryexponent](https://www.tryexponent.com/blog/ai-engineer-interview-questions) | **P0** | 950 |
| `case_studies/design_webhook_delivery_service/design_webhook_delivery_service.md` | case study | Capstone for the webhooks module; the delivery engine (retries, per-endpoint circuit breaking, signing, redrive) is a complete standalone design problem. | **P0** | 950 |
| `case_studies/design_rate_limiter_service/design_rate_limiter_service.md` | case study | "Design a rate limiter" is a top-5 backend design prompt; `rate_limiting_in_depth` is 1,101 lines with **no case study** and the case-study index has **no row** for it. | **P1** | 950 |
| `case_studies/design_multi_tenant_saas_backend/design_multi_tenant_saas_backend.md` | case study | Capstone for the multi-tenancy module; covers the pool/bridge/silo decision end-to-end. | **P1** | 950 |
| `case_studies/design_realtime_chat_backend/design_realtime_chat_backend.md` | case study | `websockets_and_sse` has no capstone, and "design a chat/presence service" is the standard WebSocket-scaling prompt (connection sharding, presence, fan-out, reconnect, LB idle timeouts). | **P2** | 950 |

### What each P0 would contain (14 sections, one line each)

**`serving_ai_workloads`** — 1: the backend layer between a client and a model, and why it is not a
normal request/response service. 2: a checkout queue where every customer takes 8 seconds and you
must show them progress. 3: streaming-first, admission control over queueing, budget as a
first-class resource, cancellation is a correctness requirement. 4: SSE vs NDJSON vs WebSocket vs
gRPC server-streaming; sync-batching vs continuous batching; per-tenant queues vs global. 5: the
request lifecycle from HTTP accept through queue depth to token emission, plus a queue-depth vs
TTFT curve. 6: an SSE endpoint with heartbeats, `Last-Event-ID` resume, client-disconnect → abort
propagation, a bounded admission queue with 429-on-full, and a token-budget middleware — all with
numbers (LB idle timeout 60s vs a 300s generation; TTFT vs TPOT budgets). 7: OpenAI/Anthropic
streaming API semantics, GKE Inference Gateway KV-cache-aware routing, vLLM's queue. 8: SSE vs
WebSocket vs polling; queue-and-shed vs autoscale; per-request vs per-tenant budgets. 9: use when
p99 is seconds and work is GPU-bound; do NOT use for sub-100ms CRUD. 10: the 200-already-sent
problem (you cannot return an error status mid-stream), buffering proxies that break SSE, retry
storms against a saturated GPU pool, unbounded queues that convert a throughput problem into a
timeout problem. 11: Gateway API Inference Extension, Envoy ext_proc, vLLM, KEDA, Redis semantic
cache. 12: 16+ Q&As. 13: always bound the queue, always propagate cancellation, always meter tokens
not requests. 14: a streaming gateway that fell over at 3× traffic because its queue was unbounded.

**`webhooks_and_event_delivery`** — 1: outbound event delivery as a durable, at-least-once
distributed problem you own on behalf of someone else's unreliable endpoint. 2: posting a letter to
an address that may be a wall. 3: at-least-once + consumer idempotency, signed payloads, bounded
retry, per-endpoint isolation. 4: fan-out models (direct, queue-per-endpoint, shared queue with
per-endpoint concurrency), push vs pull (polling APIs), CloudEvents envelope. 5: producer → outbox →
dispatcher → per-endpoint queue → retry ladder → DLQ/redrive. 6: HMAC-SHA256 signing with timestamp
replay window, RFC 9421 signature construction, an exponential retry ladder (1s/5s/30s/…/3 days),
per-endpoint circuit breaking, delivery-attempt records, replay tooling — with numbers. 7: Stripe,
GitHub, Shopify signature schemes and their retry windows; Standard Webhooks. 8: ordering vs
throughput; queue-per-endpoint vs shared; at-least-once vs effectively-once. 9: use for
cross-organisation integration; do NOT use where you control both ends (use a broker). 10: a slow
subscriber head-of-line-blocking every other subscriber, secret rotation with no overlap window,
SSRF via user-supplied endpoint URLs, replaying a redrive without dedup. 11: Svix, Hookdeck,
EventBridge API destinations, RabbitMQ delayed-message, Temporal. 12: 16+ Q&As. 13: sign everything,
cap retries, isolate per endpoint, expose a delivery log. 14: a webhook fleet where one dead
customer endpoint stalled global delivery for 40 minutes.

**`api_contracts_and_schema_governance`** — 1: the contract as the deployable artifact. 2: the
contract is the API; the code is an implementation of it. 3: contract-first, additive-only,
machine-checked, versioned. 4: OpenAPI 3.1/3.2 vs AsyncAPI 3.1 vs Protobuf/Buf vs GraphQL SDL;
code-first vs spec-first. 5: the governance pipeline — author → lint → diff → publish → generate.
6: an OpenAPI 3.2 document using the new streaming media types, an AsyncAPI 3.1 Kafka channel doc,
`spectral lint` with a custom ruleset, `oasdiff`/`buf breaking` gating a PR, SDK generation, and
`Deprecation`/`Sunset` header emission — all runnable. 7: Stripe's date-versioning, GitHub's
`X-GitHub-Api-Version`, Buf Schema Registry. 8: spec-first vs code-first; registry vs repo;
strict vs advisory linting. 9: use governance from the first external consumer; do NOT gate an
internal single-consumer API on it. 10: a generated spec that drifts from the handler, a "non-
breaking" enum addition that breaks strict clients, versioning the URL but not the events. 11:
Spectral, Redocly, oasdiff, Buf/BSR, Arazzo, Optic, springdoc. 12: 16+ Q&As. 13: lint in CI, diff
against main, never hand-edit a generated spec. 14: an API whose OpenAPI doc had silently diverged
for 14 months.

**`passkeys_and_authn_frontier`** (sub-file) — WebAuthn L3 ceremony (attestation/assertion),
discoverable credentials, synced vs device-bound passkeys, the account-recovery problem, CIBA,
SD-JWT selective disclosure, SCIM provisioning, PAR/RAR/token-exchange, and how agent/MCP
authorization reuses OAuth. 15+ Q&As.

**`fine_grained_authorization`** (sub-file) — Zanzibar's relation tuples and zookies, OpenFGA vs
SpiceDB vs Permify, Cedar and AWS Verified Permissions, the check-latency/consistency tradeoff,
list-objects vs check, and when RBAC is still the right answer. 15+ Q&As.

**`modern_otel_stack`** (sub-file) — native histograms vs classic buckets (cardinality math),
exemplars, OTel semantic-convention stability tiers, the profiling signal, OTLP direct ingest into
Prometheus 3, Grafana Alloy, eBPF auto-instrumentation, head vs tail sampling and its cost model,
multiwindow multi-burn-rate SLO alerts. 15+ Q&As.

**`design_llm_serving_api`** / **`design_webhook_delivery_service`** — full 11-section principal
template, 900–1,100 lines, 4+ cross-section references, executable §4, named companies in §6,
quantified §9, 10+ Q&As in §11.

---

## 2. Stale — verified CORRECTIONS

The 2026-08-04 pass held on the big-ticket items (OWASP 2025, HTTP/3 W3Techs figures, IETF draft
statuses, Axon 4-vs-5, KurrentDB). What remains is version drift *between* files and a handful of
tool-version pins the audit did not reach.

| File | Line/heading | What it says | What is true in Aug 2026 | Source URL |
|---|---|---|---|---|
| `README.md` (repo root) | 501, Backend Phase 2 table | "REST constraints, resource modeling, versioning, idempotency, cursor pagination, **RFC 7807**" | RFC 7807 was obsoleted by **RFC 9457** (July 2023). The module itself, `backend/README.md` and `backend/CLAUDE.md` all correctly say RFC 9457 — only the root README is stale. | https://www.rfc-editor.org/rfc/rfc9457 |
| `backend_testing_strategies/backend_testing_strategies.md` | 222 | `confluentinc/cp-kafka:7.4.0` in the Testcontainers example | CP 7.4 maps to Kafka ~3.4 (2023). `backend/CLAUDE.md`'s own Version Matrix pins the section to **Kafka 4.2+ (KRaft-only)**; Kafka 4.2.0 shipped 2026-02-17. The example contradicts the section's declared baseline. | https://kafka.apache.org/blog/2026/02/17/apache-kafka-4.2.0-release-announcement/ |
| `backend_testing_strategies/backend_testing_strategies.md` (216), `database_migrations/database_migrations.md` (327) | Testcontainers images | `postgres:15-alpine`, `postgres:15` | **PostgreSQL 18.6 (2026-08-13)** is current; PG 18 shipped Sept 2025. `database/` teaches PostgreSQL 18 in 15 places. PG 15 is still supported but is three majors behind the repo's own baseline. | https://www.postgresql.org/about/news/postgresql-183-179-1613-1517-and-1422-released-3246/ |
| `query_optimization/` (117, 404, 712), `database_internals_and_indexing/` (113, 406, 624) | baseline version prose | "PostgreSQL 16" ×6 as the stated baseline | Same as above — `database/` section is on PG 18 (async I/O, `uuidv7()`, skip scan). The two sections disagree on which Postgres they teach. | https://www.postgresql.org/about/news/postgresql-18-released-3142/ |
| `kafka_deep_dive/kafka_deep_dive.md` | 107, 625, 814, 869, 967 | Highest version referenced is **Kafka 4.0**; no mention of share groups, tiered storage, or diskless topics | **Kafka 4.2.0 (2026-02-17)** made **share groups / Queues for Kafka (KIP-932) production-ready**; **KIP-1150 diskless topics was accepted 2026-03-02**. The section's own Version Matrix already claims "Kafka 4.2+", so the module is behind its own declared pin. | https://www.confluent.io/blog/kafka-queue-semantics-share-consumer-ga/ , https://www.automq.com/blog/kip-1150-explained-diskless-topics-kafka-future |
| `observability_and_monitoring/observability_and_monitoring.md` | §6 (133–141), §8 (442), §11 | Teaches `publishPercentileHistogram()` classic buckets exclusively; §11 lists 12 tools; "Larger cardinality in Prometheus" is presented as the unavoidable cost of server-side histograms | **Native histograms became stable in Prometheus v3.8.0** and ship in the **v3.13.0 LTS (2026-07-01)** — one series regardless of bucket count, which is precisely the cardinality problem the module states as unsolvable. Prometheus 3 also ingests OTLP natively without a Collector. | https://prometheus.io/docs/specs/native_histograms/ |
| `caching_strategies_deep_dive/` §11, `rate_limiting_in_depth/` §11 | Technologies tables | Redis-only (Standalone / Cluster / Sentinel / Jedis / Lettuce / Redisson); **Valkey appears 0 times in the entire backend section** | **AWS ElastiCache and Google Memorystore now provision Valkey — not Redis — for new clusters**; Valkey 9.1 (May 2026) is the default cache package on Ubuntu 24.04+, Debian 13, Fedora 40+. The repo already covers Valkey in `database/redis_internals`, `database/key_value_stores` and `hld/rate_limiting`; only `backend/` missed it. | https://www.cloudmagazin.com/en/2026/04/10/valkey-9-redis-fork-cloud-cache-landscape/ |
| `graphql/graphql.md` | §4, §6, §11 | Federation described in v1 terms only (`@key`/`@external`, gateway composition) | Federation 2 (`@shareable`/`@override`/`@interfaceObject`) is the deployed shape, and the vendor-neutral **GraphQL Composite Schemas spec** is in active GraphQL Foundation WG development with meetings scheduled monthly through Dec 2026. Not "wrong", but a full major version behind. | https://graphql.github.io/composite-schemas-spec/draft/ |
| `chaos_engineering/` §11 | Technologies table | Lists Chaos Toolkit, Chaos Monkey, AWS FIS, Gremlin, LitmusChaos, ChaosBlade | **Chaos Mesh** (the other CNCF-incubating Kubernetes chaos project) and Steadybit are absent while older tools are present. Not a factual error — an inventory gap in a §11 table. | https://chaos-mesh.org/ |

**Documentation-consistency defects (not "the world moved" — always wrong):**

| File | What it says | What is actually true |
|---|---|---|
| `backend/CLAUDE.md`, "Learning Paths" para | "Membership is declared ONCE per module, in a `<!-- study-paths -->` block **in that module's own page** (`<module>.md`)" | Directly contradicts the **HARD RULE at the bottom of the same file** and the actual repo state: the block lives in `backend/README.md`, and content files carry no structural metadata (owner-set 2026-08-05). |
| `backend/CLAUDE.md`, same para | "Case studies are tiered the same way from a block in `case_studies/case_studies.md`" | The case-study tier lines are in `backend/README.md`'s `<!-- study-paths -->` block. `case_studies/case_studies.md` has no block. |
| `backend/README.md` module table, "Q&As" column | 23 of 35 rows disagree with the file on disk | e.g. `chaos_engineering` says 10 (real: 16), `rate_limiting_in_depth` says 12 (16), `database_migrations` 12 (16), `graphql` 12 (16), `websockets_and_sse` 12 (16), `rabbitmq_deep_dive` 42 (45), `query_optimization` 15 (18), `database_types_deep_dive` 18 (16). `backend/CLAUDE.md`'s copy of the same table is wrong for 5. Two hand-maintained copies of a derivable number. |
| `distributed_transactions_and_consistency/` §12 | Uses `**Q1:` … `**Q15:` | Every other module in the section uses `**Q: `, the convention standardized repo-wide in `ae92f65`. Cosmetic — `extract.py` strips both — but it is the only module out of step. |

---

## 3. Reordering — SEQUENCE and TIER changes

### Tier changes

| Module | Current tier | Proposed | Why |
|---|---|---|---|
| `rabbitmq_deep_dive` | senior | **Full only** | 1,713 lines and 45 Q&As of AMQP object-model minutiae (`x-match`, direct reply-to, Erlang credit flow, consistent-hash sharding) sit in the *senior interview cut* while Kafka — the broker interviews actually ask about — gets 18 Q&As. The content is excellent and earns its length; it does not earn 8% of a 22-module interview path. **This is the single most important reordering.** |
| `kafka_deep_dive` | senior | senior **+ principal** | Share groups vs consumer groups, tiered vs diskless storage, and Kafka vs Pulsar vs NATS are architecture-selection calls, not craft. |
| `rate_limiting_in_depth` | senior | senior **+ principal** | "Design a rate limiter" is a top-5 design prompt and the principal content (per-tenant fairness, cost-based limiting, adaptive concurrency, where to enforce) is judgment, not implementation. |
| `async_and_concurrency_patterns` | senior | senior **+ principal** | "Virtual threads or reactive?" is a staff-level architecture decision with a migration cost attached. |
| `backend_testing_strategies` | principal | **senior** + principal | Testing is craft before it is judgment; a senior path that omits contract testing and Testcontainers is not a senior path. |
| `event_driven_fundamentals` | principal | senior **+ principal** | The senior path currently jumps straight into `kafka_deep_dive` with no events-vs-commands / choreography-vs-orchestration vocabulary. |
| `graphql` | Full only | **senior** (after the Federation 2 fix) | GraphQL npm downloads hit 2.8M/week in early 2026 and enterprise adoption is up 340% since 2023 — it is mature, not dead. Gate the promotion on the Federation-2 content fix, or it promotes a v1-only page. |
| `container_and_deployment_patterns` | senior | **Full only** | ~70% overlap with `devops/` (`containers_and_docker`, `kubernetes_workloads_and_objects`, `kubernetes_scheduling_and_autoscaling`, `deployment_strategies`). A backend engineer should read the devops version. |
| `database_types_deep_dive` | principal | **Full only** | 779 lines surveying 8 database families that the 31-module `database/` section covers at principal depth. It is a duplicate index, not a judgment module. |
| `serving_ai_workloads` (new) | — | **senior + principal** | The gap that most distinguishes a 2026 backend hire. |
| `webhooks_and_event_delivery` (new) | — | **senior + principal** | |
| `api_contracts_and_schema_governance` (new) | — | **senior + principal** | |
| `durable_execution_and_workflows` (new) | — | **principal** | Pure "which approach at what cost" — the judgment cut. |
| `multi_tenancy_and_isolation` (new) | — | **principal** | Same. |

Net: senior 22 → 23; principal 18 → 24. Principal becoming the larger list is unusual for this repo,
but it follows from where the new material lands (all five additions are architecture-selection
topics) — and the two demotions (`database_types_deep_dive`, `container_and_deployment_patterns`)
both come out of principal/senior for duplication reasons, not depth reasons.

### Sequence changes

Insert the five new modules at their phase positions; no existing module moves relative to another.

Proposed `STUDY_ORDER["backend"]` (40 entries):

```js
backend: [
  // Phase 1 - Networking Fundamentals
  "osi_model_and_networking",
  "tcp_ip_deep_dive",
  "udp_and_quic",
  "http_protocols",
  // Phase 2 - API Design & Protocols
  "rest_api_design",
  "api_contracts_and_schema_governance",   // NEW - contract follows the resource model
  "grpc_and_protobuf",
  "graphql",
  "websockets_and_sse",
  "webhooks_and_event_delivery",           // NEW - outbound counterpart to inbound APIs
  // Phase 3 - Performance Engineering
  "performance_profiling",
  "connection_pooling_deep_dive",
  "caching_strategies_deep_dive",
  "async_and_concurrency_patterns",
  "serving_ai_workloads",                  // NEW - streaming/queueing is a latency problem
  // Phase 4 - Database Engineering
  "database_internals_and_indexing",
  "query_optimization",
  "database_migrations",
  "distributed_transactions_and_consistency",
  "database_types_deep_dive",
  // Phase 5 - Resilience & Reliability
  "fault_tolerance_patterns",
  "rate_limiting_in_depth",
  "multi_tenancy_and_isolation",           // NEW - noisy neighbour is a resilience problem
  "observability_and_monitoring",
  // Phase 6 - Security
  "backend_security_owasp",
  "auth_and_authorization_systems",
  // Phase 7 - Testing & Quality
  "backend_testing_strategies",
  "load_and_performance_testing",
  "chaos_engineering",
  // Phase 8 - Event-Driven Architecture
  "event_driven_fundamentals",
  "kafka_deep_dive",
  "rabbitmq_deep_dive",
  "event_sourcing_and_cqrs",
  "durable_execution_and_workflows",       // NEW - the modern answer to hand-rolled sagas
  "messaging_patterns",
  // Phase 9 - Microservices Architecture
  "microservices_fundamentals",
  "api_gateway_patterns",
  "service_mesh_and_service_discovery",
  "distributed_system_operational_patterns",
  "container_and_deployment_patterns",
],
```

Note `messaging_patterns` moves after `durable_execution_and_workflows` (was #30, now #35) so the
phase reads: vocabulary → brokers → state → orchestration → the delivery patterns that glue them.

---

## 4. Depth — THIN modules and BLOAT

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `observability_and_monitoring` | 608 | 16 | Only **8 sub-headings** for the section's broadest operational topic. No native histograms, exemplars, OTel semconv stability, profiling signal, OTLP-into-Prometheus, Grafana Alloy, eBPF auto-instrumentation, tail-based sampling, observability cost control, multiwindow burn-rate alerts. §11 lists 12 tools, none newer than 2023. | Add the `modern_otel_stack.md` sub-file (§1) and replace the classic-histogram cardinality claim in §8. ~1.5 days. |
| `performance_profiling` | 540 | 16 | Smallest module in the section; **2 mermaid diagrams** (section median 5.5). No eBPF continuous profiling (Parca, Grafana Pyroscope), no OTel profiling signal, no JFR event streaming, no async-profiler 4.x. | Grow §5 to 4–5 diagrams; add a continuous-profiling sub-section to §4/§6. ~1 day. |
| `backend_testing_strategies` | 541 | 16 | No fuzzing (Jazzer/OSS-Fuzz), no Spring Cloud Contract (only Pact, despite the module cross-linking `spring_testing`), no flaky-test *management* practice, no ephemeral/preview environments, no AI-assisted test generation. Stale container pins (§2). | Add §4 sub-sections + fix pins. ~1 day. |
| `load_and_performance_testing` | 551 | 16 | No Universal Scalability Law (Little's Law is covered twice, USL not at all), no load-testing of gRPC/WebSocket/SSE despite both being sibling modules, no traffic-replay mechanics, Vegeta/oha absent, wrk2 and Locust appear only in a tradeoff row and not in §11. **Also near-duplicates `devops/performance_and_load_testing`** (both cover k6, Locust, Little's Law, open vs closed models, CI gates). | Draw the line: backend owns *what to measure* (coordinated omission, percentile math, USL, protocol-specific harnesses); devops owns *running it in CI/K8s*. Add USL + streaming-protocol load testing. ~1.5 days. |
| `rest_api_design` | 589 | 15 | In **both** tiers yet has the section's thinnest §5 (1 mermaid, 2 ASCII) and only 2 real-world examples. §11 lists 8 doc/test tools and no linter, differ, registry, or AsyncAPI. No JSON Schema, no Arazzo, no request signing. | The new `api_contracts_and_schema_governance` module absorbs the governance half; grow §5 and §7 here. ~1 day. |
| `graphql` | 671 | 16 | Federation v1 only; no Composite Schemas spec, no `@defer`/`@stream` (still Stage 2, so P2), "persisted queries" not framed as "trusted documents". | Rewrite §4 Federation to v2 + Composite Schemas. ~1 day. |
| `messaging_patterns` | 702 | 16 | **~35% duplicated.** Its Outbox/Inbox/CDC content restates `distributed_transactions_and_consistency` §4.4/§4.5/§6.1/§6.3, and 5 of its 16 Q&As near-duplicate that module's Q4/Q5/Q8/Q11/Q13. Its Avro schema-evolution content restates `event_driven_fundamentals`'s compatibility section and `kafka_deep_dive`'s Schema Registry section. | **Do NOT delete** — it owns genuinely unique material nothing else covers: the **claim-check pattern** (2 Q&As), safe **DLQ replay procedure**, poison-pill retry policy, inbox-table retention, and hot-partition-vs-ordering. Repurpose it as *Message Delivery & Schema Patterns*: cede Outbox+Inbox implementation to `distributed_transactions_and_consistency` (which owns them structurally), keep DLQ / poison pill / claim check / ordering / schema evolution, and add CloudEvents. ~1 day. |
| `database_types_deep_dive` | 779 | 16 | Not thin — **redundant**. Surveys 8 database families that `database/` covers across 31 modules at principal depth. | Demote to Full-only (§3), cut to ~400 lines as a *selection guide* that routes to `database/`, or delete outright. Owner's call. ~0.5 day either way. |
| `rabbitmq_deep_dive` | 1,713 | 45 | **Not bloat — earned.** 44 sub-headings, all specific and non-repeating (prefetch arithmetic, Raft write path, `x-match`, DLX's four reasons, Erlang credit flow). The defect is *shape*, not quality: it is the section's only 1,200+ line file and the section has **0 sub-files** while the authoring model explicitly supports them. | Split operations content into `rabbitmq_operations.md` (§1) and drop the module from the senior tier (§3). ~0.5 day. |
| **Case studies (4 of 5)** | 362–889 | 0–5 | `design_booking_system`, `design_feed_service`, `design_event_driven_order_system`, `design_microservices_migration` use a **7-heading legacy format** (Problem Statement / Architecture Overview / Key Design Decisions / Implementation / Technologies Used / Tradeoffs and Alternatives / Interview Discussion Points) — **not the mandated 11-section principal template**. Missing from all four: `## Intuition`, `## 2. Scale Estimation`, `## 8. Operational Playbook`, `## 9. Common Pitfalls & War Stories`, `## 10. Capacity Planning`. All four have **0 cross-section references** (bar: 4+). `design_event_driven_order_system` (431 lines) and `design_microservices_migration` (362) have **zero `**Q:`-formatted questions**. Only `design_payment_processor` (1,316 lines, 13 Q&As, 8 cross-refs) conforms. | Convert all four to the 11-section template. The two short ones are effectively rewrites. **Biggest single block of work in the section.** ~6 days total. |

**Merge/delete recommendations**

- **Merge, do not delete, `messaging_patterns`** — see above. The unique 65% is worth keeping; the
  duplicated 35% should move to `distributed_transactions_and_consistency`.
- **Demote or shrink `database_types_deep_dive`** — a 779-line survey competing with a 31-module
  section. Recommend shrinking to a routing/selection guide rather than deleting, so the phase
  retains a "which database" landing page.
- **No other merges.** The Phase 8 overlap between `event_driven_fundamentals` and the two broker
  deep dives is the intended fundamentals→deep-dive shape, not duplication.

---

## 5. Cross-section notes

| Backend module | Other section | Overlap and where the line should be |
|---|---|---|
| `load_and_performance_testing` | **`devops/performance_and_load_testing`** | Near-identical names and content (k6, Locust, Little's Law, open vs closed, CI gating). **Backend owns the measurement science** (coordinated omission, percentile math, USL, protocol-specific harnesses for gRPC/WS/SSE); **devops owns the pipeline** (distributed k6 on K8s, CI gates, environment parity). Add a reciprocal cross-link; both sections currently pretend the other does not exist. |
| `observability_and_monitoring` | `devops/observability_metrics_prometheus`, `devops/observability_tracing_and_otel` (+ its `opentelemetry_deep_dive.md` sub-file) | **Backend owns instrumentation** (Micrometer, MDC, span authorship, SLI definition from the service's own signals); **devops owns the platform** (Prometheus operation, storage, Alloy, collector topology). The proposed `modern_otel_stack.md` sub-file must cite `devops/observability_tracing_and_otel/opentelemetry_deep_dive.md` rather than restate it. |
| `container_and_deployment_patterns` | `devops/containers_and_docker`, `kubernetes_workloads_and_objects`, `kubernetes_scheduling_and_autoscaling`, `deployment_strategies` | ~70% duplicate. **Backend should keep only what the application code must do**: graceful shutdown, connection draining, readiness-vs-liveness semantics from the app's side, 12-factor config, and the JVM-in-a-container memory story. Everything else (Dockerfile layers, HPA, PDB, blue-green mechanics) belongs to devops. Demote to Full-only. |
| `chaos_engineering` | `devops/disaster_recovery_and_resilience`, `devops/sre_principles_and_slos` | Moderate overlap on GameDays and error budgets. Backend owns application-level fault injection; devops owns infrastructure/DR drills. |
| `backend_security_owasp` §A03 Software Supply Chain Failures | `devops/devsecops_and_supply_chain_security` | A03 is new in OWASP 2025 and is the devops module's entire subject. Backend should state the risk and link out rather than grow an SBOM/SLSA/sigstore section. |
| `database_*` (5 modules, 3,847 lines) | **`database/` (31 modules)** | Phase 4 is explicitly framed as "overview; `database/` has the deep-dives" and the README already links out — but the two sections have now **drifted on Postgres version** (backend says 16, database says 18) and on Valkey. Any Phase 4 edit should sync against `database/`. |
| `rate_limiting_in_depth` | `hld/rate_limiting` | `hld/` covers Valkey; `backend/` does not. Backend owns the algorithms and Redis Lua; hld owns the placement/topology framing. |
| `microservices_fundamentals`, `api_gateway_patterns`, `service_mesh_and_service_discovery` | `hld/microservices`, `hld/api_design` | Already cross-linked in `backend/CLAUDE.md`. No change needed. |
| **`serving_ai_workloads` (new)** | `llm/`, `devops/ml_platform_and_gpu_infrastructure` | **Draw this line explicitly in the new module's §1**: `llm/` owns the model and inference internals (KV cache, batching algorithms, speculative decoding); `devops/ml_platform_and_gpu_infrastructure` owns the GPU fleet (scheduling, DRA, node pools); **`backend/` owns the HTTP boundary** — streaming transport, admission control, cancellation, per-tenant budgets, and the queue in front of the model. Without this sentence the module will be re-audited as duplication. |
| **`durable_execution_and_workflows` (new)** | `llm/agents_and_tool_use/durable_long_running_agents.md` | That sub-file already exists and covers durable execution for *agents*. The backend module should own the general engine comparison and cross-link it. |
| `auth_and_authorization_systems` | `spring/spring_security_*` | Already cross-linked. The two new sub-files (passkeys, ReBAC) are framework-agnostic and do not overlap Spring. |

---

## 6. Execution plan

**Wave 0 — cheap corrections (0.5 day, one commit, fully parallel)**

1. Fix root `README.md` line 501: RFC 7807 → RFC 9457. *(5 min)*
2. Fix `backend/CLAUDE.md`'s "Learning Paths" paragraph: the `<!-- study-paths -->` block lives in
   `backend/README.md`, not the module page; case-study tiers likewise. Two contradictions in one
   paragraph against the HARD RULE lower in the same file. *(15 min)*
3. Regenerate the Q&A column in `backend/README.md`'s module table (23 of 35 rows wrong) and
   `backend/CLAUDE.md`'s copy (5 wrong). Consider deriving it rather than hand-maintaining a third
   copy. *(1 hr)*
4. Bump the Testcontainers pins: `confluentinc/cp-kafka:7.4.0` → a 4.2-era image;
   `postgres:15-alpine` / `postgres:15` → `postgres:18-alpine`. *(30 min)*
5. Normalise `distributed_transactions_and_consistency` §12 from `**Q1:`…`**Q15:` to `**Q: `. *(15 min)*

*No `STUDY_ORDER` or `study-paths` impact. Root README updated.*

**Wave 1 — verified content corrections (2 days, parallel across 4 files)**

6. `observability_and_monitoring` §6/§8/§11: add native histograms, correct the "larger cardinality
   is the cost" claim, add OTLP-direct-ingest, refresh §11. *(0.5 day)*
7. `kafka_deep_dive`: bring to 4.2 — share groups (KIP-932, GA in 4.2), tiered storage (KIP-405),
   diskless topics (KIP-1150, accepted 2026-03-02), and a Redpanda/WarpStream/AutoMQ row in §11.
   *(0.75 day)*
8. `caching_strategies_deep_dive` + `rate_limiting_in_depth` §11 and §4: add Valkey (and the
   ElastiCache/Memorystore default switch), sync with `database/redis_internals`. *(0.25 day)*
9. `query_optimization` + `database_internals_and_indexing`: move the stated baseline from
   PostgreSQL 16 to 18, syncing against `database/`. **Re-read every `**Short:**` line above a
   changed answer** — the audit owns the Short line. *(0.5 day)*
10. `graphql` §4/§6/§11: Federation v1 → v2 + Composite Schemas spec. *(0.5 day)*
11. `chaos_engineering` §11: add Chaos Mesh, Steadybit. *(15 min)*

*No `STUDY_ORDER` impact. No `study-paths` impact.*

**Wave 2 — the case-study rewrite (6 days; the two short ones can run parallel to the two long ones)**

12. Convert `design_booking_system` (889 → ~950) to the 11-section template: add `## Intuition`,
    `## 2. Scale Estimation`, `## 8. Operational Playbook`, `## 9. Common Pitfalls & War Stories`,
    `## 10. Capacity Planning`; add 4+ cross-section refs; grow §11 from 5 to 10+ Q&As. *(1.5 days)*
13. Same for `design_feed_service` (781 → ~950), §11 from 4 to 10+ Q&As. *(1.5 days)*
14. **Rewrite** `design_event_driven_order_system` (431 → ~950). Currently has **zero** `**Q:`
    questions. *(1.5 days)*
15. **Rewrite** `design_microservices_migration` (362 → ~950). Same. *(1.5 days)*
16. Update `case_studies/case_studies.md` dependency map and Interview Prep Shortcuts to match.

*No `STUDY_ORDER` impact. `study-paths` unchanged (no new files). Root README case-study table
unchanged.*

**Wave 3 — P0 additions (7 days; all five items independent, fully parallel)**

17. `serving_ai_workloads/serving_ai_workloads.md` (~800 lines). *(2 days)*
18. `webhooks_and_event_delivery/webhooks_and_event_delivery.md` (~750). *(2 days)*
19. `api_contracts_and_schema_governance/api_contracts_and_schema_governance.md` (~700). *(1.5 days)*
20. `auth_and_authorization_systems/passkeys_and_authn_frontier.md` (~450) and
    `auth_and_authorization_systems/fine_grained_authorization.md` (~450). *(1.5 days)*
21. `observability_and_monitoring/modern_otel_stack.md` (~500). *(1 day)*

**Requires**: 3 new entries in `STUDY_ORDER["backend"]`; **7 new lines** in the
`<!-- study-paths -->` block (3 module pages + 4 sub-files, each tagged with its tiers — a sub-file
cannot join a tier its module page skips); `backend/README.md` module table + phase diagram;
`backend/CLAUDE.md` module list; **root `README.md` Backend phase table**. Then
`python3 game/extract.py --write-paths` followed by `scripts/build_banks.sh`.

**Wave 4 — P0 case studies (4 days, parallel)**

22. `case_studies/design_llm_serving_api/` (~950). *(2 days)*
23. `case_studies/design_webhook_delivery_service/` (~950). *(2 days)*
24. Update `case_studies/case_studies.md` (Quick Start, Full Learning Path, Cross-Cutting table,
    Dependency Map, Interview Prep Shortcuts) — same commit, no exceptions.

**Requires**: 2 new lines in the `study-paths` block under `case_studies`; `backend/README.md` and
`backend/CLAUDE.md` case-study tables; root README.

**Wave 5 — restructure and P1 (6 days; 25–27 are independent, 28–30 are independent)**

25. Apply all tier moves from §3 (edit the `study-paths` block only, then `--write-paths`). *(0.5 day)*
26. Split `rabbitmq_deep_dive` → `rabbitmq_deep_dive.md` + `rabbitmq_operations.md`. *(0.5 day)*
27. Repurpose `messaging_patterns`; move Outbox/Inbox implementation into
    `distributed_transactions_and_consistency`; add CloudEvents. *(1 day)*
28. `durable_execution_and_workflows/` module (~700). *(1.5 days)*
29. `multi_tenancy_and_isolation/` module (~700). *(1.5 days)*
30. `grpc_and_protobuf/connect_and_browser_rpc.md`,
    `service_mesh_and_service_discovery/ambient_mesh_and_workload_identity.md`,
    `api_gateway_patterns/gateway_api_and_modern_proxies.md`,
    `http_protocols/transport_security_frontier.md` sub-files. *(2 days)*
    Also add OpenFeature + progressive delivery (Argo Rollouts, Flagger) to
    `distributed_system_operational_patterns` §4/§11 — a 2-hour edit, no new file.

**Requires**: 2 more `STUDY_ORDER` entries; many `study-paths` lines; both READMEs; root README.

**Wave 6 — thin-module depth and cross-section hygiene (4 days, parallel)**

31. `performance_profiling` — grow §5, add continuous profiling. *(1 day)*
32. `backend_testing_strategies` — fuzzing, Spring Cloud Contract, flaky management, ephemeral envs. *(1 day)*
33. `load_and_performance_testing` — USL, streaming-protocol harnesses; add the reciprocal
    cross-link to `devops/performance_and_load_testing` and state the boundary. *(1 day)*
34. `rest_api_design` — grow §5 and §7. *(0.5 day)*
35. `database_types_deep_dive` — shrink to a selection guide routing to `database/`. *(0.5 day)*
36. Add the missing intra-section cross-links flagged by the Phase 1/2 profile: **none of
    `osi`/`tcp_ip`/`udp_and_quic`/`http_protocols`/`rest_api_design`/`grpc`/`graphql` link to each
    other**, despite obvious pairs (tcp↔quic, http↔quic for HTTP/3, grpc↔http for HTTP/2 framing,
    rest↔http for idempotent methods). *(0.5 day)*

**Wave 7 — P2 (3 days)**

37. `case_studies/design_rate_limiter_service/`, `design_multi_tenant_saas_backend/`,
    `design_realtime_chat_backend/`.

**Total: ~33 working days.** Waves 0–2 (8.5 days) are the highest value-per-day — they fix things
that are demonstrably wrong today. Wave 3 (7 days) is the highest value-per-line — it closes the
2026 gap. Waves 0/1 and 2 can run fully concurrently; Wave 4 depends on Wave 3; Waves 5–7 are
independent of everything after Wave 3.

**Global checklist for any wave that adds a file**: `STUDY_ORDER["backend"]` entry (modules only,
not sub-files) → `<!-- study-paths -->` line in `backend/README.md` with tiers → `backend/README.md`
module/case-study table and the Phase mermaid diagram → `backend/CLAUDE.md` module list →
root `README.md` Backend table → `python3 game/extract.py --write-paths` → **`scripts/build_banks.sh`**
(not just `extract.py --strict`; the script's own verifier runs after it and CI runs the whole thing).

---

## 7. Confidence and gaps

**High confidence.** The topic-presence sweep (§0, §1) is a literal grep across all 36 module files
and is not a judgement call — CloudEvents, Connect RPC, passkeys, SPIFFE, Gateway API, ambient mesh,
share groups, Temporal, Valkey and the rest genuinely appear zero times. The case-study template
finding is a heading diff and is certain. The Q&A-count drift, the two `backend/CLAUDE.md`
self-contradictions, and the root-README RFC 7807 line are all direct file comparisons. Every entry
in §2 carries a 2026 source I fetched.

**Medium confidence.**
- **Tier proposals.** Senior-vs-principal is an editorial judgement about a specific reader. My
  strongest claim is the `rabbitmq_deep_dive` demotion (a 45-Q&A AMQP module in a 22-module
  interview cut is disproportionate on any reading); the rest are arguable and the owner may
  disagree, particularly `graphql` → senior.
- **Line estimates** for new modules are calibrated against the section's 762-line median, not
  measured.
- **`messaging_patterns` at "~35% duplicated"** comes from a Q&A-level and sub-heading-level
  comparison against `distributed_transactions_and_consistency`, not a sentence-level diff. The
  direction is certain; the number is approximate.

**Could not verify — flagged, not claimed.**
- **`devops` overlap percentages** (`container_and_deployment_patterns` ~70%, and the
  `load_and_performance_testing` duplication) are based on the devops module *names* plus a heading
  read of `devops/performance_and_load_testing`. I read one devops module in full. Before acting on
  §5, someone should read `devops/containers_and_docker`, `kubernetes_workloads_and_objects`,
  `kubernetes_scheduling_and_autoscaling` and `deployment_strategies` end to end. The devops
  auditor's report should be reconciled against this section before either demotion lands.
- **Two profiling batches did not complete** (repeated API rate-limiting during this run):
  `database_migrations` / `distributed_transactions_and_consistency` / `database_types_deep_dive` /
  `fault_tolerance_patterns` / `rate_limiting_in_depth` / `observability_and_monitoring`, and
  `api_gateway_patterns` / `service_mesh_and_service_discovery` /
  `distributed_system_operational_patterns` / `container_and_deployment_patterns`. I profiled these
  ten myself via sub-heading inventory, §11 extraction, Q&A listing and targeted reads — enough for
  every finding above — but I did **not** read all ten front to back. A second pass could surface
  additional thin-module items in them. The §11 gaps I *did* verify directly in those ten:
  - `api_gateway_patterns` §11 has Spring Cloud Gateway, Kong, Nginx Plus, AWS/Azure/Apigee, Istio
    Ingress Gateway, Apollo Router. **Absent: Envoy Gateway, the Kubernetes Gateway API (now GA and
    the successor to Ingress), the Gateway API Inference Extension, Apache APISIX, Traefik 3, Kong
    AI Gateway.** It does correctly cite OpenAPI 3.2.0 (Sept 2025) and is the section's one
    AsyncAPI mention.
  - `service_mesh_and_service_discovery` §11 has Istio, Envoy, Kiali, Linkerd, Consul, Eureka,
    Spring Cloud LoadBalancer, K8s Services. **Absent: ambient mode / ztunnel / waypoint, Cilium
    mesh, SPIFFE/SPIRE, Gateway API.** (Eureka's presence is *not* a staleness bug — it is still
    actively maintained and ships in the current Spring Cloud train; it is a de-emphasis question,
    not a correction.)
  - `distributed_system_operational_patterns` §11 has Unleash and LaunchDarkly. **Absent:
    OpenFeature** (the CNCF vendor-neutral flag SDK spec that both now implement), Flagsmith, and
    progressive delivery (Argo Rollouts, Flagger).
- **`draft-ietf-httpapi-ratelimit-headers` revision 11** (cited in `rest_api_design` line 497) —
  I confirmed the sibling Idempotency-Key draft's status but the rate-limit-headers search was
  rate-limited before it returned. The module's framing ("still a working-group draft, not an RFC")
  is almost certainly still right; only the revision number is unverified.
- **`Netty | experimental QUIC support`** (`udp_and_quic` §11) — flagged by the Phase 1 profile as a
  possible stale qualifier; not verified.
- **`graphql` §14** presents a 800ms→180ms result with no "illustrative composite" disclaimer, while
  `grpc_and_protobuf` and `http_protocols` both disclaim theirs. Either the number has a source or
  it needs the same framing — I could not determine which.
