# HLD Audit — 2026-08-25

## 0. Snapshot

`hld/` is the repo's system-design-interview spine and, by content quality, one of its
strongest sections: 41,455 lines across 18 modules and 23 case studies, 350 Mermaid
diagrams, 717 Q&As, and a factual-audit pass that visibly landed — Kafka 4.0's ZooKeeper
removal, KIP-848's opt-in scoping, RFC 9700/BCP 240, RFC 9562 UUIDv7, Envoy's
`enforcing_*` defaults, Figma's "not actually a CRDT", and Signal's SPQR Triple Ratchet
are all present and correct. The case studies are the section's crown jewels: 23 files
averaging 953 lines, every one structurally conformant to the 11-section template, with
real Java in §4 and quantified war stories in §9.

Its single biggest weakness is **structural inversion**: 23 case studies resting on only
18 concept modules, and six of those modules never adopted the 14-section template at all.
The concept layer has no home for six things a 2026 interview grades directly — capacity
and cost estimation, real-time push, contention/coordination, geospatial indexing, stream
processing, and blast-radius/cell design — so those ideas exist only *inside* case studies,
where the reader meets them as incidental detail rather than as a taught primitive, and
where `extract.py` deliberately excludes their Q&As from the quiz bank. The section
teaches 23 answers well and roughly 6 of the questions behind them not at all.

A second, quieter weakness: the four principal-exclusive modules average 861 lines / 17 Q&As
against the seven senior-exclusive modules' 1,157 / 24. That gap is real arithmetic, but on
full read it is **not** thinness — see §4; those modules are efficient, and their actual
defect is content currency (no passkeys, no Jaeger v2, no chaos engineering, no
GDPR-vs-immutable-log), which is far cheaper to fix than the line count suggests.

| Metric | Value |
|--------|-------|
| Modules | 18 |
| Case studies | 23 (+1 index) |
| Deep-dive sub-files | **0** (the only large section in the repo with none) |
| Total lines | 41,455 (modules 19,532 / case studies 21,923) |
| Total Q&As | 717 (modules 389 → bank; case studies 328 → excluded by design) |
| Mermaid fences | 350 (modules 202 / case studies 148) |
| Avg lines/module | 1,085 |
| Avg lines/case study | 953 |
| Avg Q&As/module | 21.6 (floor is 15; min is 16) |

---

## 1. Missing — proposed ADDITIONS

The strongest external signal is HelloInterview's "System Design in a Hurry" pattern
taxonomy — the de-facto 2026 prep canon. It names 8 patterns; `hld/` has a dedicated
module for 4 of them and **none** for *Pushing Realtime Updates*, *Dealing with
Contention*, *Proximity-Based Services*, or *Handling Large Blobs*. Its 31 problem
breakdowns overlap `hld/`'s 23 case studies on 15; the non-overlaps are the case-study
gap list below.

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `hld/capacity_estimation/capacity_estimation.md` | module | Exponent's 2026 guide: *"interviewers now grade cost and operations explicitly"* and the passing bar rose specifically on *"cost, failure modes, and operational judgment that used to be optional"* — [tryexponent.com](https://www.tryexponent.com/blog/system-design-interview-guide). SDI Vol 1 spends a whole chapter on it (`book/system_design_interview_vol_1/02_back_of_the_envelope_estimation`). `hld/` has **15 lines of README prose** and no module; "latency numbers every programmer should know" appears nowhere in the section. | **P0** | 900 |
| `hld/realtime_updates/realtime_updates.md` | module | HelloInterview pattern #1, *Pushing Realtime Updates* — [hellointerview.com/learn/system-design/in-a-hurry/patterns](https://www.hellointerview.com/learn/system-design/in-a-hurry/patterns). Currently taught only incidentally inside 13 case studies; `backend/websockets_and_sse` holds protocol mechanics but no architectural decision frame. | **P0** | 950 |
| `hld/coordination_and_contention/coordination_and_contention.md` | module | HelloInterview pattern #3, *Dealing with Contention*, and the whole flash-sale/ticketing class of question: *"thousands or millions of users simultaneously trying to buy limited inventory"* — [systemdesignhandbook.com](https://www.systemdesignhandbook.com/guides/system-design-interview-questions/). `hld/` has no module on distributed locks, leases, fencing, optimistic vs pessimistic concurrency, hold-with-TTL, singleflight, or virtual waiting rooms — fencing tokens live oddly inside `consensus_algorithms`. | **P0** | 1,000 |
| `hld/cell_based_architecture/cell_based_architecture.md` | module | **Zero occurrences of "cell-based" or "shuffle shard" anywhere in the section.** AWS ships an entire Well-Architected guide on it — [docs.aws.amazon.com/wellarchitected/latest/reducing-scope-of-impact-with-cell-based-architecture](https://docs.aws.amazon.com/wellarchitected/latest/reducing-scope-of-impact-with-cell-based-architecture/cell-deployment.html) — plus a repeated re:Invent track. This is the canonical principal-level blast-radius answer and the section cannot give it. | **P0** | 950 |
| `hld/multi_region_and_data_residency/multi_region_and_data_residency.md` | module | 2026 sovereignty guidance is explicit that *"'Multi-region active/active satisfies EU sovereignty' is wrong — region pinning solves geographic residency, it does not solve sovereignty"* — [buildmvpfast.com/blog/data-sovereignty-vs-residency-eu-procurement-2026](https://www.buildmvpfast.com/blog/data-sovereignty-vs-residency-eu-procurement-2026). `hld/` has scattered GDPR mentions in 8 files and no module on active-active vs active-passive, RPO/RTO, geo-partitioning, or follow-the-workload. | P1 | 950 |
| `hld/stream_processing/stream_processing.md` | module | HelloInterview ships a Flink deep-dive as a named key technology. Windows/watermarks/late data/exactly-once sinks/Lambda-vs-Kappa currently exist **only** inside `design_ad_click_aggregation.md`, whose Q&As are excluded from the quiz bank by design. | P1 | 900 |
| `hld/geospatial_indexing/geospatial_indexing.md` | module | HelloInterview pattern #8 + its *Proximity Search* deep dive. Geohash/quadtree/S2/H3 are re-derived three separate times (`design_uber`, `design_google_maps`, `design_proximity_service`) with no shared module — the only concept in the section taught three times and owned by none. | P1 | 850 |
| `hld/case_studies/design_distributed_job_scheduler.md` | case study | HelloInterview problem #22; dedicated 2026 guides at [systemdesignhandbook.com](https://www.systemdesignhandbook.com/guides/design-a-distributed-job-scheduler/) and algomaster. The core probe — *"if Scheduler A and Scheduler B both detect a job due at 9:00 AM, how do you prevent both executing it"* — is exactly the exactly-once/leader-election material `hld/` teaches but never applies. | P1 | 1,000 |
| `hld/case_studies/design_flash_sale_ticketing.md` | case study | HelloInterview problem #4 (Ticketmaster). `design_hotel_reservation.md` covers hold-with-TTL but not the flash-sale shape: virtual waiting room, admission queue, pre-loaded Redis inventory, isolated blast radius. | P1 | 1,000 |
| `hld/case_studies/design_youtube_upload_pipeline.md` | case study | HelloInterview problem #10; SDI Vol 1 ch.14 *Design YouTube* has no `hld/` counterpart. `design_netflix.md` is a VOD **delivery** study — it does not cover UGC ingest, chunked resumable upload, the transcoding DAG, or per-title encoding economics. | P1 | 1,000 |
| `hld/resilience_patterns/backpressure_and_load_shedding.md` | sub-file | Would give `hld/` its first deep-dive sub-file. Backpressure appears in 8 files as a word; load shedding in 3. Neither has an owned treatment: queue-depth signals, admission control, priority shedding, brownout, Little's Law budgeting. The 2026 framing — *"load shedding is which requests you drop first; backpressure is telling the sender 'I can't take any more'"* — is now standard interview vocabulary ([designgurus.io/system-design-interview](https://www.designgurus.io/system-design-interview)). | P2 | 600 |
| `hld/observability/continuous_profiling.md` | sub-file | OTel's profiles signal entered public alpha March 2026 targeting GA Q3 2026 — [opentelemetry.io/blog/2026/profiles-alpha](https://opentelemetry.io/blog/2026/profiles-alpha/). `design_metrics_monitoring.md` explicitly scopes profiling *out*; `observability.md` §11 has no row for it. The fourth signal currently has no home in the repo's HLD layer. | P2 | 500 |
| `hld/case_studies/design_live_streaming.md` | case study | Distinct from `design_netflix.md`: LL-HLS/LL-DASH, sub-3s glass-to-glass, ingest fan-in, chat fan-out. HelloInterview flags this as the pivot interviewers spring mid-question (*"watch for questions that pivot to live streaming halfway through, since that completely changes the design"*). | P2 | 950 |
| `hld/case_studies/design_top_k_heavy_hitters.md` | case study | HelloInterview problem #12 (YouTube Top K). Count-Min Sketch and heavy-hitter streaming appear only as passing mentions; `design_leaderboard.md` is exact-rank, not approximate-frequency. | P2 | 900 |
| `hld/case_studies/design_email_service.md`, `design_nearby_friends.md` | case study ×2 | The only two SDI Vol 2 chapters (ch.08, ch.02) with no `hld/` counterpart, in a section that otherwise mirrors Vol 2 completely. Completeness, not urgency. | P2 | 900 each |

### P0 detail

**`capacity_estimation`** — §1 why every design question is secretly an arithmetic
question; §2 the "one number you can anchor everything to" mental model; §3 the four
axes (QPS, storage, bandwidth, connections) and powers-of-two/ten discipline; §4 read-heavy
vs write-heavy vs fan-out estimation archetypes; §5 a latency-numbers ladder and a
cost-per-GB/GB-egress/vCPU-hour chart as Mermaid `xychart-beta`; §6 worked end-to-end
derivations (DAU→peak QPS with a 3× peak factor; bytes/row→TB/year; egress→$/month);
§7 how Twitter/Discord/Netflix actually publish their numbers; §8 precision vs speed —
when a 2× error is fine and when it is disqualifying; §9 estimate first vs design first;
§10 the classic pitfalls (average-QPS-not-peak, forgetting replication factor ×3,
forgetting index overhead, quoting storage without retention); §11 the reference tables
themselves; §12 20 Q&As drilling the arithmetic; §13 round early, carry units, state
assumptions aloud; §14 size a 10M-DAU photo service end to end and then re-size it at 10×.

**`realtime_updates`** — §1 the push problem; §2 "a phone call vs checking your mailbox";
§3 who holds state, who initiates, who pays for the idle connection; §4 short poll /
long poll / SSE / WebSocket / WebTransport, plus the pub-sub routing layer behind them;
§5 connection-fan-out topology and a Mermaid sequence per transport; §6 connection budget
math (2M/server Erlang-style vs 60k/server typical JVM), sticky routing vs a shared
Redis/Kafka bus, presence and heartbeat, reconnect-with-jitter; §7 WhatsApp, Discord,
Slack, Figma; §8 the transport comparison table; §9 when polling is genuinely correct;
§10 the thundering-reconnect-herd, the load balancer idle timeout that silently kills
connections, per-connection memory blowup, missed messages during reconnect; §11
WebSocket gateways, MQTT, Centrifugo, Ably/Pusher, ALB/NLB behaviour; §12 18 Q&As;
§13 always design the reconnect path first; §14 a live-comments fan-out at 1M viewers.

**`coordination_and_contention`** — §1 what breaks when two writers meet; §2 "a single
till vs numbered tickets"; §3 mutual exclusion, ordering, and the fact that a lock without
fencing is not a lock; §4 pessimistic locks, optimistic version columns, leases with
fencing tokens, hold-with-TTL reservations, idempotency keys, single-flight, queue-as-lock,
virtual waiting rooms; §5 a contention timeline diagram showing the GC-pause split-brain;
§6 the broken→fixed pair the section currently lacks: naive `SETNX` lock → lease + fencing
token enforced at the storage layer; §7 Ticketmaster, Amazon inventory, DynamoDB
conditional writes, S3 `If-None-Match`; §8 optimistic vs pessimistic under contention rate;
§9 when to remove contention instead of managing it (partition the resource); §10 lock
held across an RPC, TTL shorter than the work, retry storms amplifying contention,
compensating actions that are not commutative; §11 Redlock's caveats, etcd/ZooKeeper,
DynamoDB conditional expressions, Postgres advisory locks; §12 18 Q&As; §13 make the
storage layer the enforcement point; §14 a 50k-seat onsale at 500k concurrent buyers.

**`cell_based_architecture`** — §1 blast radius as a first-class design parameter; §2
"bulkheads in a ship's hull"; §3 a cell is a complete, independent replica of the stack;
§4 cell sizing, cell routing (thin router vs DNS vs client-side), shuffle sharding,
control plane vs data plane separation; §5 a cell-map diagram and a shuffle-sharding
combinatorics grid (the one place ASCII beats Mermaid here); §6 the arithmetic —
*n* cells cap worst-case impact at 1/*n*; shuffle sharding with 8 cells and 2 per tenant
gives 28 unique combinations so one poison tenant reaches 1/28 of peers; §7 AWS
(Route 53, S3, DynamoDB), Slack, Salesforce; §8 cell count vs per-cell efficiency vs
router complexity; §9 when a cell is overkill (single-region, <100k users); §10 the
router becoming the new SPOF, cross-cell chatter reintroducing coupling, a "global" table
quietly spanning cells, deployment waves that skip the bake; §11 Route 53 ARC, cell
routers, AWS Well-Architected guidance; §12 18 Q&As; §13 never let a request cross a cell;
§14 converting a single-region monolith to 12 cells with a phased-deployment plan.

---

## 2. Stale — verified CORRECTIONS

The 2026-08-04 audit was thorough; almost every version-sensitive claim I probed
(Kafka 4.0/KRaft, KIP-848 opt-in scoping, RFC 9700, RFC 9562/UUIDv7, Envoy `enforcing_*`
defaults, Hystrix maintenance mode, Signal SPQR, Figma-is-not-a-CRDT, S3 Dec-2020 strong
consistency, OpenAPI 3.2.0 dated 2025-09-19) checks out. Five items did not.

| File | Line/heading | What it says | What is true in Aug 2026 | Source URL |
|---|---|---|---|---|
| `caching/caching.md` (506, 513) **and** `case_studies/design_leaderboard.md` (§4.1, §5, §6, §7) | §11 Technologies / §7 Build-vs-Buy | "**Redis** — In-memory data structure store … De facto standard." / "**AWS ElastiCache** — Managed Redis/Memcached". In the leaderboard, §7's Build-vs-Buy verdict is flatly "Buy — Redis ZSET", and a grep for *Valkey*, *license*, *SSPL*, *RSAL*, *AGPL* across that file returns **zero hits**. | Redis 8.0 shipped under AGPLv3 (May 2025) after the Mar-2024 move off BSD. **Valkey** — BSD-3, Linux Foundation, release 9.1 (May 2026) — is now the **default engine provisioned by AWS ElastiCache and MemoryDB**, and by Google Memorystore. `rate_limiting.md:1351` already carries this correctly; these two files do not. The leaderboard is the worse case: its entire "buy" recommendation rests on Redis specifically, and "would you still pick Redis, or Valkey?" is exactly the 2026 follow-up it has no answer prepared for. | [redisvsmemcached.com/redis-license-timeline](https://redisvsmemcached.com/redis-license-timeline/), [cloudmagazin.com — Valkey 9](https://www.cloudmagazin.com/en/2026/04/10/valkey-9-redis-fork-cloud-cache-landscape/) |
| `case_studies/design_stock_exchange.md` | 655, 656 vs 660 (§6) | Nasdaq "sub-40 microseconds … fastest production deployment cited at ~14 microseconds" and NYSE Pillar's figures are stated bare, while the LSE row *is* hedged: "LSEG does not publish a current headline number, so treat any 'low tens of microseconds' claim for this platform as unsourced." | Not provably wrong — but the **hedging is inconsistent within one table**. Exchange latency headlines are vendor marketing that moves between platform generations. Either extend the LSE-style caveat to the Nasdaq and NYSE rows or source them. The existing LSE hedge is the model; it is good practice applied to only one of three rows. | — (internal inconsistency; no external source needed) |
| `case_studies/design_object_storage_s3.md` | §11, lines ~1006-1008 | On two concurrent `PUT`s to the same key: "the second `PUT` to complete wins" — presented as the only behaviour | That describes only the pre-Aug-2024 default. S3 conditional writes shipped `If-None-Match` (2024-08-20) and `If-Match` (2024-11-25), with bucket-level **enforcement** (Nov 2024), conditional deletes (2025-09-16) and conditional copy (2025-10-29). This is now the standard answer to the exact question §11 poses. The file also never mentions **S3 Express One Zone** despite enumerating every other storage class at line ~808. | [aws.amazon.com/about-aws/whats-new/2024/08/amazon-s3-conditional-writes](https://aws.amazon.com/about-aws/whats-new/2024/08/amazon-s3-conditional-writes), [docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html) |
| `security_and_auth/security_and_auth.md` | §4, §11 (whole module) | Auth methods enumerated as sessions / JWT / OAuth2+PKCE / mTLS / API keys. **Zero occurrences of "passkey", "WebAuthn" or "FIDO" in the entire `hld/` section.** | FIDO Alliance reported ~**5 billion passkeys in use** on World Passkey Day (2026-05-07); 75% of people have enabled one; **68% of organizations are rolling out or piloting** workforce passkeys. A 2026 auth module that does not name WebAuthn is missing the primary authentication story. | [businesswire — FIDO Alliance, World Passkey Day 2026](https://www.businesswire.com/news/home/20260506926067/en/FIDO-Alliance-Reports-Accelerating-Global-Passkey-Adoption-on-World-Passkey-Day-2026), [descope.com/blog/post/2026-fido-report](https://www.descope.com/blog/post/2026-fido-report) |
| `observability/observability.md` | §11 table (lines 571-579) | Four categories: Metrics, Dashboards, Logs, Tracing. No profiling row; no eBPF row. | OpenTelemetry **graduated in CNCF (May 2026)** and its **profiles signal entered public alpha (March 2026), targeting GA in Q3 2026** — profiling is now treated as a core signal alongside traces/metrics/logs. Pyroscope/Parca/Polar Signals and eBPF collectors (Pixie, Odigos, Cilium) are absent. | [opentelemetry.io/blog/2026/profiles-alpha](https://opentelemetry.io/blog/2026/profiles-alpha/), [cncf.io/blog/2026/07/24/opentelemetry-has-graduated-now-what](https://www.cncf.io/blog/2026/07/24/opentelemetry-has-graduated-now-what/) |
| `consensus_algorithms/consensus_algorithms.md` | 163 (§4.3 BFT) | Lists live BFT/consortium examples as "(e.g., Hyperledger Fabric ordering, Tendermint/Cosmos, **Diem**)" | **Diem is dead.** The Diem Association confirmed shutdown and sold its assets to Silvergate Bank for $182M on 2022-02-01, after the Federal Reserve declined to approve the arrangement; Silvergate itself then wound down in 2024. Citing Diem as a current BFT deployment is a four-year-stale reference. Replace with a live example. | [bankingdive.com/news/diem-to-end-crypto-project-sell-its-assets-to-silvergate-bank-for-182m](https://www.bankingdive.com/news/diem-to-end-crypto-project-sell-its-assets-to-silvergate-bank-for-182m/617839/), [coindesk.com/business/2022/01/31/silvergate-bank-confirms-diem-tech-acquisition](https://www.coindesk.com/business/2022/01/31/silvergate-bank-confirms-diem-tech-acquisition) |
| `observability/observability.md` | 468, 576 (§7, §11) | Names "Jaeger" generically alongside Zipkin as a tracing backend | **Jaeger v2 (Nov 2024) is a different architecture**: built on the OpenTelemetry Collector framework, natively speaking OTLP end to end with **no agent tier at all**. Jaeger **v1 was fully deprecated in January 2026** after its final release in Dec 2025. A module that discusses tracing topology cannot describe Jaeger without this — the agent tier it implies no longer exists. | [cncf.io/blog/2024/11/12/jaeger-v2-released-opentelemetry-in-the-core](https://www.cncf.io/blog/2024/11/12/jaeger-v2-released-opentelemetry-in-the-core/), [cncf.io/blog/2025/09/01/jaeger-at-10](https://www.cncf.io/blog/2025/09/01/jaeger-at-10-forged-in-community-reborn-in-opentelemetry/) |
| `microservices/microservices.md` | 1138 (§11 Service Mesh row) | "Istio is the most feature-rich but operationally heaviest; Linkerd is simpler to run; Cilium does it in eBPF with no per-pod sidecar" | Istio's **ambient (sidecarless) mode reached GA in v1.24, Nov 2024**, dropping memory overhead up to 70% and removing the per-pod sidecar — the very thing this row uses to distinguish Istio from Cilium. The Istio 2025-2026 roadmap treats ambient as the mainline data plane. | [istio.io/latest/blog/2024/ambient-reaches-ga](https://istio.io/latest/blog/2024/ambient-reaches-ga/), [istio.io/latest/blog/2025/roadmap](https://istio.io/latest/blog/2025/roadmap/) |

Two more that are **gaps rather than errors**, recorded here because a reader would call
them stale: `case_studies/design_proximity_service.md` uses "Elasticsearch / OpenSearch"
interchangeably throughout §4.2/§7 without acknowledging the 2021 Elastic License v2 split
and the divergence since; and `case_studies/design_distributed_message_queue.md` — the
cleanest file in the section on version-sensitive claims — omits **KIP-932 share groups**
(Kafka 4.0's queue-style alternative to consumer groups), which sits directly beside the
consumer-group material in its own §4.4. A third: `design_metrics_monitoring.md` §7 names
"Cortex/Mimir" as a pair and never mentions **Prometheus 3.0** (Nov 2024) or
**remote-write 2.0** — "Prometheus 3" has zero occurrences in the whole section — in a case
study whose entire subject is Prometheus-shaped.

### FALSE POSITIVES — verified correct, do NOT "fix" these

A profiler flagged each of the following as wrong. I web-verified all four and **every one is
right as written.** Recording them so a later execution pass does not "correct" accurate
content into inaccurate content — the exact failure mode the audit-owns-the-Short rule exists
to prevent.

| File | Flagged as | Actually |
|---|---|---|
| `security_and_auth.md:10` | "OWASP Top 10:2025 … A01 broken access control, A02 security misconfiguration, A03 software supply chain failures, A07 authentication failures" called a *fabricated citation*, because A02/A03 don't match the 2021 list | **Exactly correct.** OWASP Top 10:**2025** is published, and its order is A01 Broken Access Control, A02 Security Misconfiguration, A03 Software Supply Chain Failures, A04 Cryptographic Failures, A05 Injection, A06 Insecure Design, **A07 Authentication Failures**, A08 Software or Data Integrity Failures, A09 Security Logging and Alerting Failures, A10 Mishandling of Exceptional Conditions. The flag came from comparing against the superseded 2021 numbering. — [owasp.org/Top10/2025](https://owasp.org/Top10/2025/) |
| `distributed_transactions.md:449` | DynamoDB "100 unique items / 4 MB per transaction" needs re-verification | **Current.** `TransactWriteItems` groups up to 100 action requests and a transaction cannot exceed 4 MB; the 25→100 raise landed Sept 2022 and still stands. — [docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_TransactWriteItems.html](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_TransactWriteItems.html) |
| `event_sourcing_cqrs.md:454` | "KurrentDB" looked like a typo for EventStoreDB | **Correct and current** — EventStoreDB was renamed KurrentDB in 2024-25. A positive currency signal. |
| `consensus_algorithms.md:727` | KRaft/ZooKeeper claim | **Correct** — "stable in Kafka 3.3+, default in 4.0 … 4.0 drops ZooKeeper" matches the March 2025 Kafka 4.0 release. |
| `design_digital_wallet.md:614` | Venmo instant-transfer fee "1.75%, minimum $0.25, maximum $25" flagged as a live commercial figure at risk of drift | **Correct as written** in Aug 2026 — Venmo's published instant bank transfer fee is still 1.75%, min $0.25, max $25. — [help.venmo.com — Instant Bank Transfer FAQ](https://help.venmo.com/cs/articles/instant-bank-transfer-faq-vhel302) |

Spot-checks that **passed** and should not be re-opened: `design_payment_system.md:926`
correctly states PCI DSS **v4.0.1** with the revised SAQ A effective **1 April 2025** and
the Requirement 6.4.3 / 11.6.1 script-attack condition — precisely right and unusually
current. `design_ad_click_aggregation.md` §6's Druid/Pinot provenance dates and its Flink
`TumblingEventTimeWindows` / `allowedLateness()` API references are accurate.
`design_payment_system.md:674` cites PayPal's **2025 Form 10-K** (25.4B transactions).

Two items flagged by a profiler and **cleared on verification — do not "fix" them**:
`case_studies/design_distributed_unique_id.md:720` claims `UUID.ofEpochMillis(long)` was
"added in JDK 26" — this is **correct**; the method shipped in JDK 26 under JEP 522 and is
in the released Javadoc ([docs.oracle.com/en/java/javase/26/.../UUID.html](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/UUID.html)).
And `api_design.md:439`'s "OpenAPI 3.2 (released 2025-09-19)" is **correct and still
current** — 3.2.0 remains the latest published OAS; 4.0 "Moonwalk" is unreleased
([spec.openapis.org/oas/v3.2.0.html](https://spec.openapis.org/oas/v3.2.0.html)).

---

## 3. Reordering — SEQUENCE and TIER changes

The current order is a clean four-phase ramp and mostly right. Three things are wrong with
it: (a) `consensus_algorithms` and `event_sourcing_cqrs` sit at positions 17-18, *after*
`distributed_transactions` (13) which depends on both — a reader meets Saga before they
have been taught the event log or leader election; (b) `observability` and
`security_and_auth` are stranded at 14-15 between the distributed-data material and the
resilience material they have nothing to do with; (c) the two tiers are drawn on the wrong
axis — `caching`, `cdn` and `consistent_hashing` are excluded from Principal, but "which
cache tier, at what staleness budget, at what egress cost" *is* a principal question,
while `api_design` is in Principal chiefly because it is long.

| Module | Current position / tier | Proposed | Why |
|---|---|---|---|
| `consensus_algorithms` | 17, principal only | **9**, principal only | Prerequisite for `distributed_transactions` (2PC coordinator election), `database_sharding` (shard-map ownership), and `design_key_value_store`. Nothing after it depends on `cdn` or `consistent_hashing`, so it can move ahead of them freely. |
| `event_sourcing_cqrs` | 18, principal only | **14**, principal only | Immediately before `distributed_transactions`, whose Saga/outbox material assumes an append-only log the reader has not yet been shown. |
| `distributed_transactions` | 13, senior+principal | **15**, senior+principal | Follows `event_sourcing_cqrs`; unchanged in tier. |
| `observability` | 14, principal only | **17**, principal only | Belongs with "operate what you built", after resilience — and Exponent notes ops is now graded at senior level, so it should also **join Senior**. |
| `security_and_auth` | 15, principal only | **18**, principal only | Same regrouping; it is the one genuinely orthogonal module and reads best last. |
| `resilience_patterns` | 16, senior+principal | **16**, unchanged | Already correctly placed and dual-tier. |
| `caching` | 3, senior only | 3, **senior + principal** | Cache-tier choice, staleness budget, and egress cost is a judgment question; the module already carries the effective-latency and memory-sizing math to support it. |
| `cdn` | 9, senior only | 9, **senior + principal** | Same argument: origin-shield vs multi-CDN vs egress cost is a cost-judgment call, and the 2026 bar grades cost explicitly. |
| `message_queues` | 7, senior only | 7, **senior + principal** | Delivery-guarantee choice and the exactly-once/idempotency tradeoff is the most common principal follow-up in the section's own case studies. |
| `api_design` | 6, senior+principal | 6, **senior only** | Versioning and pagination are craft. The judgment half (contract evolution across teams) is thin here and better served by the proposed cross-link to `backend/rest_api_design`. |
| `observability` | principal only | **senior + principal** | Monitoring, deployment and rollback are now graded explicitly at senior level: *"if you finish a 45-minute design loop and never said the words monitoring, deployment, or rollback, you left points on the table"* ([designgurus.io/system-design-interview](https://www.designgurus.io/system-design-interview)). Excluding the only observability module from the Senior path directly contradicts the 2026 bar. |

Net effect: Senior goes 14 → 17 modules, Principal 11 → 13. Both tiers absorb the new P0
modules: `capacity_estimation` is **senior + principal** (it is the one module both tiers
need first), `realtime_updates` senior, `coordination_and_contention` senior + principal,
`cell_based_architecture` principal only.

### Proposed `STUDY_ORDER["hld"]`

```js
hld: [
  "hld/scalability","hld/capacity_estimation","hld/load_balancing","hld/caching","hld/database_design",
  "hld/cap_theorem","hld/api_design","hld/message_queues","hld/consensus_algorithms","hld/rate_limiting",
  "hld/cdn","hld/realtime_updates","hld/consistent_hashing","hld/database_sharding","hld/geospatial_indexing",
  "hld/microservices","hld/event_sourcing_cqrs","hld/stream_processing","hld/distributed_transactions",
  "hld/coordination_and_contention","hld/resilience_patterns","hld/cell_based_architecture",
  "hld/multi_region_and_data_residency","hld/observability","hld/security_and_auth",
],
```

25 modules. `capacity_estimation` at position 2 is deliberate — it is the tool every later
module's numbers are checked with. Note that `extract.py --strict` fails the Pages deploy
on any module in the bank missing from this array, and the `<!-- study-paths -->` block in
`hld/README.md` must be edited in the same commit as any new file.

---

## 4. Depth — THIN modules and BLOAT

Nothing here is *bloated*; nothing is under the 15-Q&A floor. The problems are template
non-conformance, one true content duplication, and a systemic broken→fix placement bug.

### Template conformance — the biggest structural defect

Only **6 of 18** modules use the exact `## 1.` … `## 14.` numbering the root CLAUDE.md
mandates. Six abandoned it entirely; six more drift.

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `scalability` | 1,034 | 24 | **All headings unnumbered**; a hyperlinked "Table of Contents" substitutes for numbering; `## Intuition` precedes `## Concept Overview`; extra `## Metrics and Monitoring` pushes Case Study to slot 15 | Renumber to the 14-section template; fold Metrics into §13; drop the TOC |
| `load_balancing` | 1,016 | 23 | Same pattern, identical shape | Same. Also add HTTP/3-QUIC load balancing (connection migration, UDP LB) and eBPF/XDP L4 (Katran, Cilium) — both entirely absent |
| `cdn` | 1,244 | 24 | Unnumbered; 16 ad-hoc headings (`What is a CDN?`, `CDN Metrics`, `Security`); Interview Questions sits before Best Practices | Renumber; map the ad-hoc headings onto §3/§4/§6/§11 |
| `consistent_hashing` | 1,293 | 24 | Unnumbered; **two separate `## Case Study` sections** straddling `## Interview Questions` | Renumber; merge the two case studies into a single §14 |
| `database_sharding` | 1,455 | 24 | Unnumbered; 16 ad-hoc headings | Renumber |
| `message_queues` | 1,241 | 24 | Unnumbered; 20 headings; two Case Study sections around Interview Questions | Renumber; merge case studies |
| `database_design` | 992 | 24 | **16** numbered sections — ACID/Normalization/Indexing/Replication promoted to top-level, shifting Case Study to §16 | Demote the four to `###` under §3/§4; renumber |
| `api_design` | 938 | 24 | 15 numbered; `## Intuition` unnumbered; §9 split into two; extra `## 14. Metrics & Monitoring`; a trailing unnumbered second Case Study | Renumber; merge the split; fold Metrics into §13 |
| `cap_theorem` | 937 | 24 | Identical to `api_design` | Same |
| `caching` | 930 | 24 | `## Intuition` unnumbered; extra `## 4. Eviction Policies` masks a one-off shift in §2/§3 | Renumber §2/§3; demote Eviction Policies to `###` |
| `microservices` | 1,560 | 24 | §4 and §5 transposed (How It Works before Architecture Diagrams); extra `## 6. Resilience, Tracing, Security` | Swap §4/§5; demote §6 |
| `rate_limiting` | 1,385 | 24 | `## Intuition` unnumbered; extra `## 4. Distributed Rate Limiting` | Renumber; demote to `###` |

Conformant already: `consensus_algorithms`, `distributed_transactions`, `event_sourcing_cqrs`,
`observability`, `resilience_patterns`, `security_and_auth` — the six most recently written.

### NOT thin — the three short modules are efficient, and the real defect is currency

I set out expecting `observability` (812/16), `event_sourcing_cqrs` (873/16) and
`security_and_auth` (867/16) — the three shortest modules, all **principal-exclusive** — to
be thin. **On full read they are not.** All three hit the complete 14-section template, carry
16 Q&As, use the same formula → symbol-table → worked-example decoder pattern as the long
modules, and treat every subsection they open to full depth. The line-count gap versus the
1,000+ line modules comes from having fewer §4 sub-strategies (security_and_auth has 6
subsections against resilience_patterns' 8), not from shallower treatment per subsection.
`consensus_algorithms` is in fact the most quantitatively rigorous file in the section.

So the tier asymmetry in §0 is real as an arithmetic fact but **misdiagnosed as thinness**.
The genuine defect in these three is **content currency**, and each gap is specific:

| Module | Gap | Note |
|---|---|---|
| `security_and_auth` | passkeys / WebAuthn / FIDO2 **entirely absent** (§2); no **RFC 8725** (JWT BCP) citation despite §6.1 and §10 covering exactly the vulnerabilities it addresses (`alg:none`, algorithm confusion); no post-quantum posture | The WhatsApp case study already carries excellent PQC material (PQXDH → SPQR) this module does not |
| `observability` | **Jaeger v2 / v1 deprecation** (§2); eBPF auto-instrumentation (Pixie, Beyla, Odigos) absent; continuous profiling as a fourth signal absent; no Prometheus 3.x anywhere; the "$" cost claims are never quantified and Netflix Atlas "billions of time series" is unsourced | |
| `event_sourcing_cqrs` | **GDPR right-to-be-forgotten vs an immutable log** — crypto-shredding, event redaction, tombstoning — absent, which is glaring given §14's case study is an explicitly regulated audit/compliance scenario. Also `:452`'s ING Bank / CapGemini Axon adoption claims are stated as bare fact with no source; hedge or cite them | Ties directly into the proposed `multi_region_and_data_residency` module |
| `resilience_patterns` | **Chaos engineering is absent entirely** — the standard practice for *validating* every pattern the module teaches. `backend/chaos_engineering/` already exists, so this is a cross-link plus a short §13 subsection, not new content. Alibaba **Sentinel** is also never named alongside Hystrix/Resilience4j | |
| `consensus_algorithms` | Q&A ordering opens definitional ("What is Raft and how does leader election work?") rather than with a gotcha, unlike the other five modules in its group — reorder §12 | Its §6 BROKEN/FIX pairs are the best in the section; use as the model for §4's placement fix |

Fixing those five rows is roughly 150-250 lines each — materially cheaper than the "add
250 lines of judgment material" I would have prescribed from line counts alone.

### The one true duplication

`database_design.md` §7 and `cap_theorem.md` §5 both fully derive the same N=3/W=2/R=2
quorum-overlap example — same numbers, same structure, same conclusion. Keep it in
`cap_theorem` (where it is load-bearing for the CP/AP argument) and replace the
`database_design` copy with a two-line summary plus a cross-link. Similarly,
`load_balancing.md` lines 104-140 re-derive consistent hashing in full, duplicating the
dedicated `consistent_hashing` module — reduce to a summary + link.

### Systemic broken→fix placement bug

Across all 21 files profiled in depth, the required broken→fix pair almost never appears
where the standard puts it. In case studies, §4 Component Deep Dives has no labelled pair
and everything sits in §9 War Stories — **14 of 15 profiled case studies**. The one exception
is `design_google_docs.md`, which walks the naive "apply in arrival order" divergence
inside §4.1 before introducing `transform()` as the fix, *and* carries three more pairs in
§9. In modules the same displacement runs the other way: §10 Common Pitfalls is prose-only
and the broken→fix code lives inside §14's case study.

The §9 and §14 material is genuinely excellent, and there are three models to copy from:
`design_google_docs.md` §4.1 (correct placement), `design_hotel_reservation.md` §9 War
Story 1 (the clearest pair in the section — a check-then-act race with a stale-SELECT
absolute-value `UPDATE`, fixed to an atomic `UPDATE … WHERE available_rooms >= 1`, plus a
genuinely non-obvious note that `SoldOutException` must extend `RuntimeException` because
Spring's default `@Transactional` rollback only fires on unchecked exceptions), and
`consensus_algorithms` §6 for modules. `design_payment_system.md` (double-charge, balance
race, `double`-vs-`BIGINT` currency drift) and `design_notification_system.md` (dedup race,
FCM batch-response mishandling, a DST/UTC-offset bug with SQL+Java before/after) are the
most extensive. This is a placement fix, not a content gap: add one labelled `*Broken:*` /
`*Fix:*` pair to each module's §10 and each case study's §4, lifted from the later section.

### Case-study code quality is uneven — one file fails the bar outright

The standard requires executable code in §4. Measured across the 8 profiled case studies:

| File | §4 code | Verdict |
|---|---|---|
| `design_whatsapp.md` | **zero labelled code fences of any language** (11 mermaid, 0 else) — schema and protocol examples are unlabelled plain-text | **Fails the bar.** Label and promote the Redis key layout, Cassandra table shape and XMPP stanza to real `sql`/`erlang` fences, and add a session/fan-out routine |
| `design_twitter.md` | SQL DDL ×4 + JSON ×1; Redis commands in unlabelled fences | Weak — DDL only, no business logic |
| `design_netflix.md` | SQL ×1 + JSON ×1 | Weakest after WhatsApp |
| `design_uber.md` | Python ×1, SQL ×2, Bash ×2 | Acceptable |
| `design_url_shortener.md` | Python ×2, Bash ×3, SQL ×2 | Good |
| `design_google_docs.md` | Java ×3 — a real OT `transform()` with all four dispatch branches, plus an RGA CRDT | Excellent |
| `design_notification_system.md` | Java ×4 (dedup, preference filter, token bucket, SMS worker) | Excellent |
| `design_payment_system.md` | Java ×6 (idempotency, ledger CAS, orchestrator, outbox relay, HMAC webhook verify) | Excellent |

The fix is scoped: WhatsApp needs real code added; Twitter and Netflix need business logic
alongside their DDL. The other five are at or above the bar.

### Content gaps inside otherwise-strong case studies

Six 2026-relevant surfaces are missing from case studies that are otherwise current. These
are **refresh edits to existing files**, not new files, and each is a likely interview probe:

| File | Missing surface |
|---|---|
| `design_netflix.md` | The **ad-supported tier** (launched Nov 2022) and its SSAI / ad-decisioning pipeline — a major infra surface entirely absent; also the password-sharing-detection architecture (2023) |
| `design_whatsapp.md` | **Channels** (2023 one-to-many broadcast, architecturally distinct from Communities and broadcast lists), Meta AI in-thread, and the WhatsApp Business Cloud API |
| `design_notification_system.md` | **RCS as a channel**, Android 13+ runtime notification permission (materially changes opt-in rates), iOS scheduled-summary delivery semantics |
| `design_payment_system.md` | **Real-time rails** (FedNow, UPI, Pix) as an alternative settlement path; PSD3/PSR |
| `design_google_docs.md` | **The AI co-editor as a concurrent actor** — does an LLM inserting large blocks get a `siteId` like any client, and does its op volume break §2's "tens of ops/sec per document" assumption the whole sharding design rests on? The sharpest live 2026 angle in the set |
| `design_twitter.md` | Algorithmic ranking post-open-sourcing, Community Notes, and the paid-API-tier effect on firehose/Earlybird economics |
| `design_digital_wallet.md` | **PSD2 / Strong Customer Authentication and UPI have zero occurrences.** KYC/AML is covered thoroughly (§4.6, tiered limits, velocity checks), but the EU's live SCA regime and the world's dominant real-time P2P rail are both absent from a 2026 wallet design |
| `design_stock_exchange.md` | **MiFID II, Reg NMS and LMAX all have zero occurrences.** Reg NMS's order-protection rule and MiFID II's algo-trading/best-execution obligations are the two things an interviewer builds on top of this matching engine; LMAX's disruptor/mechanical-sympathy architecture is the most-cited real-world matching engine and is missing from §6's otherwise-complete table |
| `design_ad_click_aggregation.md` | Privacy-regulation pressure on click/impression aggregation — cookie deprecation, on-device attribution, Privacy Sandbox/Topics-style aggregation APIs. The file stays purely on Lambda/Kappa streaming mechanics |
| `design_metrics_monitoring.md` | **OpenTelemetry Metrics** appears once, in a §7 tools-table cell, with no treatment of OTel's aggregation/pipeline model or its relationship to the Prometheus exposition format — thin for 2026 given OTel's CNCF graduation |
| `design_google_drive.md` | Client-side/E2E encryption as a *default* expectation (post-2022 Advanced Data Protection going mainstream) and AI/semantic search over Drive content. The E2E-vs-dedup tension itself is covered well |

### No deep-dive sub-files at all

`hld/` is the only large section in the repo with zero sub-files. That is defensible for a
deliberately overview-depth section, but it means every topic that outgrows a module has
nowhere to go except a new module. The two P2 sub-files proposed in §1 are the natural
first tests of the pattern here.

---

## 5. Cross-section notes

**The AI/LLM system-design question does NOT need a new `hld/` case study.** `llm/case_studies/`
already ships 29 of them including `design_chatgpt.md`, `design_rag_pipeline.md`,
`design_gpu_inference_platform.md`, `design_llm_gateway.md`, `design_ai_search_engine.md`
and `design_customer_support_bot.md`; `database/vector_databases/` covers the storage layer;
`ml/model_serving_and_inference/` covers serving. Writing another in `hld/` would duplicate
a stronger sibling.

What is genuinely missing is a **bridge**, and its absence is costly: Exponent's 2026 guide
says AI/LLM prompts have *"moved into general engineering loops"* and one widely-cited
figure puts ML-adjacent questions at roughly half of 2026 loops, up from one in ten in 2024.
A candidate preparing from `hld/` today will not discover any of it — `case_studies.md`'s
Interview Prep Shortcuts table has 29 rows and not one AI row. Fix, in one commit, no new
content:

1. Add an **"AI-Backed Systems"** group to `case_studies/case_studies.md` §Full Learning
   Path, linking `../../llm/case_studies/design_chatgpt.md`, `design_rag_pipeline.md`,
   `design_gpu_inference_platform.md` and `design_llm_gateway.md`.
2. Add four rows to the Interview Prep Shortcuts table: *Design ChatGPT / an LLM chat
   product*, *Design a RAG-backed Q&A system*, *Design an LLM inference platform*,
   *Design an AI API gateway* — each pointing into `llm/`.
3. Add a **Vector Databases** row to §Cross-Cutting / Shared Primitives pointing at
   `../../database/vector_databases/vector_databases.md`.
4. Add a vector-search branch to `database_design.md` §3's SQL-vs-NoSQL decision tree
   (pgvector vs a dedicated store) — currently the tree has no vector branch at all.

**Other cross-section items:**

- **`backend/`** owns the "how" and the line is mostly held well. Two leaks to fix:
  `scalability.md`'s full database-scaling mechanics belongs in `database_design` +
  `database/sharding_and_partitioning`; `load_balancing.md`'s consistent-hashing derivation
  belongs in `hld/consistent_hashing`. The proposed `realtime_updates` module must
  explicitly defer protocol mechanics to `backend/websockets_and_sse` and keep only the
  architectural choice — otherwise it becomes a third copy.
- **`database/`** — `hld/database_design` and `hld/database_sharding` correctly stay at
  overview depth; no change needed beyond the quorum de-duplication above.
- **`devops/`** — the proposed `cell_based_architecture` module overlaps
  `devops/disaster_recovery_and_resilience` on deployment waves. Split: `hld/` owns the
  *architecture* (cell boundary, routing, shuffle sharding); `devops/` owns the *pipeline*
  (wave scheduling, bake time, rollback). Cross-link both ways.
- **`book/`** — `hld/case_studies/` mirrors SDI Vol 1 + Vol 2 almost exactly; the proposed
  `design_email_service` and `design_nearby_friends` close the last two chapter gaps.
  Worth adding a line to `hld/README.md` naming that correspondence, since a reader
  working through `book/system_design_interview_vol_2/` has no signpost to the
  matching `hld/` case study.

---

## 6. Execution plan

Everything below is content work; none of it changes tooling. **Every wave that adds or
moves a file must, in the same commit, update `hld/README.md`'s `<!-- study-paths -->`
block, `STUDY_ORDER.hld` in `game/app.js`, the `hld/README.md` module table, `hld/CLAUDE.md`'s
module table, and the root `README.md` — then run `python3 game/extract.py --write-paths`
followed by the full `scripts/build_banks.sh`** (a green `extract.py --strict` alone does
not mean CI passes). Any new case study also requires a row in `case_studies/case_studies.md`'s
Full Learning Path, Dependency Map, and Interview Prep Shortcuts.

**Wave 1 — corrections and bridges (2-3 hours, fully parallel, zero new files).**
Highest value per hour in the whole plan.
1. Apply the seven verified §2 corrections: Valkey in `caching` §11 **and in
   `design_leaderboard` §4.1/§5/§6/§7, whose whole Build-vs-Buy verdict rests on Redis**;
   extend the LSE-style "unsourced, treat with caution" hedge to the Nasdaq and NYSE latency
   rows in `design_stock_exchange` §6; S3 conditional writes
   + Express One Zone in `design_object_storage_s3`; passkeys/WebAuthn in `security_and_auth`
   §4/§11; profiling + eBPF rows in `observability` §11; **Jaeger v2 / v1-deprecation** in
   `observability` §7/§11; **replace Diem** in `consensus_algorithms:163`; Istio ambient in
   `microservices` §11. **Each edit must re-read and update the `**Short:**` line above any
   answer it changes** — that is the one place a correct fix introduces a new defect. **And
   read §2's false-positive table first**: four flagged "errors" are correct as written.
2. Add the ES/OpenSearch licence-split note to `design_proximity_service`, the KIP-932
   share-groups note to `design_distributed_message_queue`, and the Prometheus 3.x /
   remote-write 2.0 note to `design_metrics_monitoring` §7.
3. Ship the four AI-bridge edits from §5 — one commit, no new content.
4. De-duplicate the quorum derivation (`database_design` → link to `cap_theorem`) and the
   consistent-hashing derivation (`load_balancing` → link to `consistent_hashing`).
5. Cross-link `resilience_patterns` → `backend/chaos_engineering/` and add a short §13
   subsection on validating the patterns; name Sentinel alongside Hystrix/Resilience4j in
   §11. Add RFC 8725 to `security_and_auth` §6.1. Hedge or source the ING Bank / CapGemini
   Axon claims in `event_sourcing_cqrs:452`. Reorder `consensus_algorithms` §12 so it opens
   on a gotcha rather than a definition.

**Wave 2 — template renumbering (6-8 hours, 12 independent files, fully parallel).**
Mechanical and low-risk, but touches 12 files, so do it before any content is layered on
top. Renumber the six unnumbered modules and fix the six drifting ones per §4's table;
merge the doubled Case Study sections in `consistent_hashing` and `message_queues`.
**Verify after each file that its Q&A count is unchanged** — the heading rename is exactly
the class of edit that has silently deleted Q&As from the bank in this repo before
(`lld`, 217 lost). Run `python3 game/extract.py` and diff the per-section count.

**Wave 3 — the four P0 modules (4 files × ~1 day each; fully parallel across authors).**
`capacity_estimation`, `realtime_updates`, `coordination_and_contention`,
`cell_based_architecture`. ~3,800 lines, 74+ Q&As, each with authored `**Short:**` lines
from the start. These are new files, so no audit-before-Short ordering problem applies.
Wave 3 can start concurrently with Wave 2 as long as the two do not touch the same files —
they do not.

**Wave 4 — reordering (1-2 hours, must follow Wave 3).**
Apply the §3 `STUDY_ORDER` array and the tier changes in the `<!-- study-paths -->` block,
regenerate the tier tables, and rewrite the "Recommended Learning Order" phase list and the
6-week Study Plan in `hld/README.md` to match. Must come after Wave 3 so the new modules
land in position rather than being appended and moved twice.

**Wave 5 — P1 modules and case studies (3 modules + 3 case studies, ~5 days, parallel).**
`multi_region_and_data_residency`, `stream_processing`, `geospatial_indexing`;
`design_distributed_job_scheduler`, `design_flash_sale_ticketing`,
`design_youtube_upload_pipeline`. ~5,700 lines. The three case studies depend on the Wave-3
modules they cross-reference (`coordination_and_contention` for flash sale,
`capacity_estimation` for all three), so schedule after Wave 3, not alongside it.

**Wave 6 — code, broken→fix, and case-study refresh (~5 days, highly parallel).**
Three independent tracks:
- **Code:** add real labelled code to `design_whatsapp.md` §4 (currently zero fences of any
  language) and business logic alongside the DDL in `design_twitter.md` and
  `design_netflix.md` §4. Use `design_payment_system.md` and `design_google_docs.md` as the
  bar.
- **Broken→fix placement:** add one labelled `*Broken:*` / `*Fix:*` pair to every module's
  §10 and every case study's §4 — 41 files, mechanical, and the single change that most
  raises the section against its own stated quality bar. Models:
  `consensus_algorithms` §6 (modules) and `design_google_docs` §4.1 (case studies).
- **Content refresh:** the eleven §4 gaps in existing case studies — Netflix's ad tier/SSAI,
  WhatsApp Channels, RCS in the notification system, real-time rails in payments, the AI
  co-editor in Google Docs, Twitter's post-open-sourcing ranking, PSD2/SCA and UPI in the
  wallet, MiFID II/Reg NMS/LMAX in the exchange, Privacy Sandbox in ad-click aggregation,
  OTel Metrics in metrics monitoring, and default-E2E plus semantic search in Google Drive.

**Wave 7 — P2 tail (optional, ~4 days).** The two P2 sub-files, then
`design_live_streaming`, `design_top_k_heavy_hitters`, `design_email_service`,
`design_nearby_friends`.

**Total effort: roughly 24-28 engineer-days**, of which Waves 1-2 (about 1.5 days) deliver
a disproportionate share of the value and carry almost no risk.

---

## 7. Confidence and gaps

**High confidence.** The structural findings are mechanically derived and reproducible:
the 14-section conformance table, the module/case-study line and Q&A counts, the tier-vs-depth
asymmetry (861/17 principal-exclusive vs 1,157/24 senior-exclusive), the zero-occurrence
searches for *cell-based*, *shuffle shard*, *passkey*, *WebAuthn*, *FIDO*, and
*back-of-the-envelope*, and the absence of any AI row in `case_studies.md`. The five §2
corrections were each web-verified against a primary or vendor source and are cited inline.

**Medium confidence.** The proposed reordering rests on a dependency reading of the module
prose rather than on any learner telemetry; the game records per-module progress, so if
completion or accuracy data exists for `hld`, it should override my ordering argument. The
tier moves (`caching`/`cdn`/`message_queues` into Principal, `api_design` out) are a
judgment call about where cost-and-tradeoff reasoning lives, and the owner's own framing —
"principal is usually the smaller list" — cuts mildly against adding three to it.

**Not verified — needs checking before acting.**
1. Three specific external limits I did not re-verify: `api_design.md:635` Stripe's
   "100 requests/second per account in live mode (25/second in a sandbox)";
   `api_design.md:350` GitHub's `X-GitHub-Api-Version: 2022-11-28` described as "currently";
   `caching.md:401-403` Netflix EVCache at "~30 million cache requests per second". All
   three are the kind of figure that ages without warning.
2. Two large sourced throughput claims in `design_distributed_message_queue.md` (Netflix
   Keystone "beyond the trillion events per day", LinkedIn "4,000+ brokers … 7 trillion
   messages/day") are attributed rather than asserted, but the underlying blog posts are
   several years old.
3. `design_search_autocomplete.md:791` "Google disclosed in 2024 that it handles more than
   5 trillion searches a year" — plausible, unverified against a 2026 figure.

**Verify subagent staleness flags before acting on them — the false-positive rate was high.**
Of the claims profilers flagged as wrong or at-risk, **five of seven I checked were actually
correct** (§2's false-positive table). One was flagged as an outright *fabricated citation*
(`security_and_auth.md`'s OWASP Top 10:2025 mapping) and is exactly right; the profiler had
compared it against the superseded 2021 numbering. This matters operationally: an execution
pass that trusts a flag without verifying will rewrite correct content into wrong content,
and because the `**Short:**` line above each answer must be updated in the same edit, one
bad "fix" ships as the *correct option* in the quiz. **Every §2 row in this report was
web-verified against a primary source before I wrote it; nothing else should be actioned
without the same check.**

**Coverage is now complete.** All six profiler batches returned: 18 modules and 15 of 23
case studies were read in full, the remaining 8 case studies covered by machine-derived
structure plus targeted claim-family checks. Two earlier caveats are resolved:
`case_studies.md` was verified section-by-section — all five required sections present, and
all 23 case studies appear in **both** the Full Learning Path and the Interview Prep
Shortcuts table, with all 23 as nodes in the Dependency Map. And the under-sampled claim
families I flagged (Redis licensing in the leaderboard, PSD2/SCA and UPI in the wallet,
MiFID II/Reg NMS/LMAX in the exchange) were all confirmed — each is an **absence**, and each
is now recorded in §2 or §4.

**The headline result of the full sweep: across all 21 files read in depth, not one
factual error was found beyond the seven §2 corrections.** Every other finding is an
absence — a topic a 2026 interviewer would raise that the file does not address. That is a
strong signal the 2026-08-04 factual audit did its job, and it is why this report's value
sits almost entirely in coverage, structure and ordering rather than in corrections.

**Residual uncertainty.** Three durable-looking figures I did not independently source:
`design_metrics_monitoring.md`'s paraphrase of Prometheus docs at "an average of only 1-2
bytes per sample" (long-standing, Gorilla-lineage, low risk); `observability.md`'s unsourced
Netflix Atlas "billions of time series"; and the `event_sourcing_cqrs.md:452` ING Bank /
CapGemini Axon adoption claims, which are stated as bare fact and should be sourced or
hedged. Also unverified: `api_design.md:635` Stripe's "100 requests/second live, 25 sandbox",
`api_design.md:350` GitHub's `X-GitHub-Api-Version: 2022-11-28` described as "currently", and
`caching.md:401` Netflix EVCache at "~30 million cache requests per second".
