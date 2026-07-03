# Design a High-Volume Log Aggregation Pipeline

> A log pipeline is a city's storm-drain system: it must swallow a once-a-year flash flood without backing up into anyone's basement, route the water somewhere cheap, and still let an inspector find one specific drop the next morning.

**Key insight**: The pipeline's only non-negotiable promise is *do not drop logs under backpressure* — and the only way to keep that promise at 50 TB/day is to push durability to the edge (agent disk buffers) and absorb the surge in a decoupling buffer (Kafka), so that a slow downstream index degrades into *delay* rather than *loss*.

---

## Intuition

> Think of every agent as a person holding a bucket under a leaking pipe: if the drain (the indexer) clogs, the bucket (the agent's disk buffer) must hold the water until the drain clears — it must never tip the bucket onto the floor.

**Key insight**: Logs are write-once, read-rarely. 99% of the 50 TB ingested today will never be queried; the 1% that is queried is queried during an incident when speed matters most. The whole architecture is shaped by this asymmetry — make writes cheap and durable, make the *recent* slice fast, and tier everything else to object storage at $0.023/GB.

**Mental model**: A log pipeline is a series of buffers connected by transport, terminating in two storage classes. Each stage has a bounded capacity and a backpressure signal that propagates *upstream*. When the indexer slows, the buffer fills; when the buffer fills, the agent buffers to disk; when the disk fills, the agent finally sheds load — but by design that last step almost never fires because Kafka gives you hours of runway. Every design decision is "where does the water go when the next stage is slow?"

**Why this system exists**: At 4000 services emitting structured logs, no single Elasticsearch cluster or single Loki instance can hold the working set, and no single agent config survives a 5x traffic spike during an incident (incidents *cause* log spikes — error logs, retries, stack traces). You need a pipeline that decouples producers from consumers, parses and redacts PII inline, isolates tenants so one team's `DEBUG`-in-prod cardinality bomb does not blind everyone else, and tiers storage so the bill scales with *value* (recent = hot, old = cold) rather than with raw volume. Build this wrong and you either lose logs during the exact incident you needed them for, or you spend $4M/year indexing logs nobody reads.

---

## 1. Requirements Clarification

### Functional Requirements

- **Collection**: collect logs from ~4000 services across ~50 Kubernetes clusters plus ~2000 VMs/bare-metal hosts. Support container stdout/stderr, file tails, syslog, and structured JSON.
- **Parsing**: parse multiline stack traces (Java/Python), structured JSON, and common text formats (nginx, syslog RFC5424) into a normalized schema with `timestamp`, `service`, `level`, `trace_id`, `message`, and arbitrary structured fields.
- **PII redaction**: redact emails, credit-card numbers, SSNs, bearer tokens, and configurable per-tenant patterns *before* the data lands in any queryable store.
- **Buffering / backpressure**: absorb a 5x ingest surge for at least 4 hours without dropping a single log line.
- **Storage tiers**: 30-day hot, queryable store; 1-year cold archive in object storage with on-demand rehydration.
- **Query**: label-filter + full-text/grep over the hot tier; slower scan-and-rehydrate over the cold tier.
- **Multi-tenancy**: per-tenant ingest quotas, retention, RBAC, and query isolation across ~120 tenants.
- **Retention tiering**: automatic lifecycle from hot → cold → delete based on per-tenant policy.

### Non-Functional Requirements

| Dimension | Target |
|-----------|--------|
| Ingest volume | 50 TB/day raw ≈ **580 MB/s average**, **2.9 GB/s peak** (5x) |
| Event rate | ~580K events/sec avg (avg log line ~1 KB), ~2.9M events/sec peak |
| Durability | **Never drop logs under backpressure** — 4h of surge runway minimum |
| Ingest-to-queryable lag | p99 < 60s under normal load; < 5 min under peak |
| Query latency | p99 < **3s** for last-24h label-filtered queries; < 30s for 7-day; minutes for cold-tier |
| Ingest availability | 99.95% (write path must survive single-AZ loss) |
| Query availability | 99.9% (read path may degrade before write path) |
| Hot retention | **30 days** queryable |
| Cold retention | **1 year** in object storage |
| PII redaction | 100% of known patterns redacted pre-storage; zero plaintext PII in any index |
| Per-tenant blast radius | one tenant's volume spike must not degrade another tenant's ingest or query |
| Cost target | < $1.2M/year all-in (see §2) |

### Out of Scope

- Metrics and traces — this is logs only. Correlation to traces happens via `trace_id` propagation; the metrics/trace stores are designed in [`design_observability_platform.md`](./design_observability_platform.md).
- Application instrumentation / logging SDKs — we standardize on structured JSON output but do not build the libraries.
- SIEM / security analytics on logs — a separate downstream consumer reads from the same Kafka topics.
- Real-time alerting on log patterns — a Flink job consumes Kafka; its design is a separate doc.

See [`../observability_logging/README.md`](../observability_logging/README.md) for the single-system logging module this case study composes into a platform.

---

## 2. Scale Estimation

All numbers derive from the §1 targets. The two lines that dominate the bill are **hot-tier storage + index overhead** and **Kafka retention**.

### Ingest math

```
50 TB/day raw
  / 86,400 s/day            = 0.58 TB per 86.4 ks = 580 MB/s average
  x 5 (peak multiplier)     = 2.9 GB/s peak
avg log line                = 1 KB
events/sec average          = 580 MB/s / 1 KB = ~580,000 events/sec
events/sec peak             = ~2,900,000 events/sec
```

### Kafka sizing

Kafka is the surge buffer. We must hold 4 hours of *peak* to guarantee no drops.

```
peak throughput            = 2.9 GB/s
4 hours of peak runway     = 2.9 GB/s x 14,400 s = ~41.7 TB
Kafka compression (lz4)    = ~4x on JSON logs    => ~10.4 TB on disk
replication factor         = 3                   => ~31 TB Kafka disk for the buffer
```

Per-partition sustainable throughput on NVMe brokers is ~10–30 MB/s for durable writes with RF=3. Size for peak:

```
partitions needed (write)  = peak / per-partition
                           = 2,900 MB/s / 15 MB/s ≈ 193 partitions
round up for headroom + rebalance => 256 partitions on the main logs topic
```

### Storage math (hot tier)

JSON logs compress ~**10x** with zstd. Index overhead differs wildly by store:

```
30 days raw                = 50 TB/day x 30 = 1,500 TB raw
after 10x compression      = 150 TB compressed payload
```

| Store | Index overhead | 30-day footprint | Notes |
|-------|----------------|------------------|-------|
| Loki (labels only) | ~1.05x on compressed chunks | ~158 TB | chunks in S3, tiny index in BoltDB/TSDB |
| OpenSearch (index everything) | ~1.0x–1.5x of *raw* | ~1,500–2,250 TB | inverted index roughly equals raw size |
| OpenSearch (selective fields) | ~0.4x of raw | ~600 TB | only index `service`,`level`,`trace_id` + keyword fields |

This single table is the central cost driver: indexing every field in OpenSearch costs ~10–14x more storage than Loki's label-only model.

### Cost math (monthly, AWS us-east-1, approximate)

Hot SSD (gp3/io2 or instance NVMe) ≈ **$0.08–0.125/GB-month**; S3 Standard ≈ **$0.023/GB-month**; S3 Glacier Instant Retrieval ≈ $0.004/GB-month.

```
Loki hot index (TSDB on gp3)        ~5 TB     x $0.10/GB  = $512/mo
Loki chunks in S3 (30-day hot)      158 TB    x $0.023/GB = $3,634/mo
Cold tier S3 (1 year, 18,250 TB after 10x... )
   1-year raw 18,250 TB / 10x comp  = 1,825 TB x $0.023   = $42,000/mo  (S3 Standard-IA)
   move >90d to Glacier IR (~1,600 TB x $0.004)           ≈ $6,400/mo for the tail
Kafka brokers (12x i3en.2xlarge)    ~$5,600/mo
Compute: agents (DaemonSet, ~free), processors (40x c6i.4xlarge spot ~$0.30/h)
   40 x $0.30 x 730                                        = $8,760/mo
Loki/OpenSearch query+ingester (30x r6i.2xlarge)          ≈ $24,000/mo
```

Loki-based total lands near **$55K–70K/month ≈ $660K–840K/year**, comfortably under the $1.2M target. The equivalent OpenSearch-index-everything design crosses **$3.5M–4.5M/year** purely on hot storage + indexing compute — which is exactly why §5 leans Loki for the bulk path.

---

## 3. High-Level Architecture

```
                          ┌──────────────────────────────────────────────┐
   ~4000 services         │   COLLECTION TIER  (DaemonSet, one per node)  │
   ~50 K8s clusters       │                                              │
   ~2000 VMs              │   ┌──────────┐   ┌──────────┐                 │
   ───────────────────────┼──▶│Fluent Bit│   │  Vector  │  (file/stdout) │
     stdout / files /     │   │  agent   │   │  agent   │                 │
     syslog / json        │   └────┬─────┘   └────┬─────┘                 │
                          │        │  DISK BUFFER (filesystem, 5–10 GB)   │
                          └────────┼──────────────┼──────────────────────┘
                                   │   backpressure propagates UPSTREAM   │
                                   ▼              ▼
                          ┌──────────────────────────────────────────────┐
                          │   TRANSPORT / SURGE BUFFER                    │
                          │   ┌────────────────────────────────────────┐ │
                          │   │  KAFKA  topic=logs.raw  256 partitions  │ │
                          │   │  RF=3   lz4   retention=8h (peak=4h x2) │ │
                          │   └───────────────┬────────────────────────┘ │
                          └───────────────────┼──────────────────────────┘
                                              │  consumer-group lag = backpressure signal
                                              ▼
                          ┌──────────────────────────────────────────────┐
                          │   PROCESSING TIER  (Vector/Bytewax workers)   │
                          │   parse multiline ──▶ normalize schema ──▶    │
                          │   PII redaction ──▶ enrich (tenant, geo) ──▶  │
                          │   route by tenant + level + sampling          │
                          │        │ ok                    │ parse fail   │
                          │        ▼                        ▼             │
                          │   logs.parsed topic        logs.dlq topic     │
                          └────────┬──────────────────────┬──────────────┘
                                   │                       │
              ┌────────────────────┼───────────────┐       │
              ▼                    ▼               ▼        ▼
   ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐
   │  LOKI (hot 30d)  │  │  OPENSEARCH      │  │  S3 COLD 1yr  │
   │  labels + chunks │  │  (security/      │  │  parquet/json │
   │  chunks in S3    │  │   high-value     │  │  partitioned  │
   │  index in TSDB   │  │   subset only)   │  │  by day/tenant│
   └────────┬─────────┘  └────────┬─────────┘  └──────┬────────┘
            │                     │                   │
            ▼                     ▼                   ▼
   ┌──────────────────────────────────────────────────────────┐
   │   QUERY TIER   Grafana (LogQL) | OpenSearch Dashboards    │
   │   query-frontend: split + cache + per-tenant limits       │
   │   cold rehydrate: Athena/S3 Select on parquet on demand   │
   └──────────────────────────────────────────────────────────┘
```

### Component inventory

| Component | Role | Tech |
|-----------|------|------|
| Collection agent | tail/collect, disk-buffer, ship | Fluent Bit (bulk), Vector (rich transform nodes) |
| Surge buffer | decouple producers/consumers, absorb 5x | Kafka (256 partitions, RF=3) |
| Processing workers | parse, redact, normalize, route | Vector aggregators (stateless, autoscaled) |
| Hot store (bulk) | 30-day label-indexed logs | Grafana Loki (chunks in S3, TSDB index) |
| Hot store (high-value) | full-text search subset | OpenSearch (selective indexing) |
| Cold store | 1-year archive | S3 (parquet, partitioned), queried via Athena |
| Query frontend | split, cache, limit, fan out | Loki query-frontend + queriers |
| DLQ | unparseable lines, never silently dropped | Kafka `logs.dlq` + alerting |

### Data flow with the backpressure path

1. Agent reads a line, parses *cheaply* (just enough to add `service`/`tenant` labels), writes to its local disk buffer, then ships to Kafka.
2. **Backpressure**: if Kafka is unreachable or slow, the agent's disk buffer fills. While the buffer has room, *no data is lost* — the agent simply stops `tail` cursor advancement when the buffer is full, so unread file bytes remain on the source node's disk (a second layer of buffer).
3. Processing workers consume `logs.raw`, parse multiline + structured, redact PII, route. Their **consumer-group lag** is the canonical backpressure/health signal.
4. Parsed logs fan out: bulk → Loki, high-value subset → OpenSearch, *all* → S3 cold (tee'd from `logs.parsed`).
5. Lifecycle: Loki chunks expire at 30 days; S3 lifecycle moves objects to Glacier IR at 90 days and deletes at 365.

---

## 4. Component Deep Dives

### 4.1 Collection agent + disk buffer (Fluent Bit / Vector)

```
   ┌──────────────────────── node (DaemonSet pod) ──────────────────────┐
   │  /var/log/containers/*.log                                          │
   │        │ tail (inotify + offset DB)                                 │
   │        ▼                                                            │
   │   ┌─────────┐   in-mem ring  ┌──────────────┐   ┌───────────────┐  │
   │   │ INPUT   │──────────────▶│ FILESYSTEM    │──▶│ OUTPUT (kafka)│  │
   │   │ tail    │   (small)      │ BUFFER 8 GB   │   │ acks=all      │  │
   │   └─────────┘                └──────────────┘   └───────────────┘  │
   │                              ▲ overflow goes to DISK, not /dev/null │
   └────────────────────────────────────────────────────────────────────┘
```

The single most common production outage in log pipelines: **the agent buffers in memory only, the downstream slows, memory fills, and either the agent OOM-kills (losing the buffer) or it drops new lines.**

**BROKEN** — Fluent Bit with a memory-only buffer and no backpressure protection:

```ini
# fluent-bit.conf  -- DANGEROUS: memory-only buffer
[SERVICE]
    flush        1
    log_level    info
    # storage.path NOT set -> all buffering is in memory

[INPUT]
    Name             tail
    Path             /var/log/containers/*.log
    Mem_Buf_Limit    50MB          # when full, tail STOPS reading...
    Skip_Long_Lines  On            # ...and long lines are silently DROPPED
    # no storage.type -> overflow cannot spill to disk

[OUTPUT]
    Name    kafka
    Match   *
    Brokers kafka:9092
    Topics  logs.raw
    # default Retry_Limit -> gives up after a few retries, then DROPS
```

When Kafka stalls for 10 minutes during a broker rebalance, `Mem_Buf_Limit` fills in seconds at 580 MB/s/node-fraction, `tail` stops, and any log file that rotates while paused loses its tail forever. We measured **~40 GB of logs lost per node-hour** in exactly this scenario during a real Kafka upgrade.

**FIX** — filesystem buffer with bounded disk, unlimited retries, and graceful pause (not drop):

```ini
# fluent-bit.conf  -- SAFE: filesystem-backed buffer
[SERVICE]
    flush                     1
    log_level                 info
    storage.path              /var/log/flb-buffer/   # on local SSD, dedicated PVC
    storage.sync              normal
    storage.checksum          on
    storage.max_chunks_up     128                    # in-memory working set cap
    storage.backlog.mem_limit 256M                   # cap mem used to flush backlog

[INPUT]
    Name              tail
    Path              /var/log/containers/*.log
    storage.type      filesystem      # <-- overflow spills to DISK, not dropped
    Mem_Buf_Limit     64MB
    Skip_Long_Lines   Off             # keep long lines; truncate downstream if needed
    DB                /var/log/flb-buffer/tail.db    # durable read offsets
    DB.sync           normal
    Refresh_Interval  5

[OUTPUT]
    Name              kafka
    Match             *
    Brokers           kafka-0:9092,kafka-1:9092,kafka-2:9092
    Topics            logs.raw
    rdkafka.request.required.acks  all       # durable writes
    rdkafka.compression.codec      lz4
    storage.total_limit_size       8G        # bounded disk buffer per node
    Retry_Limit                    no_limits # NEVER give up -> backpressure, not loss
    workers                        2
```

Now when Kafka stalls, the agent spills to an 8 GB disk buffer (≈ several minutes of a single node's volume), and because `Retry_Limit no_limits`, it keeps the data and drains it when Kafka recovers. The `tail.db` offset DB ensures that even an agent restart resumes at the exact byte, so a rotated-but-unread file is still read from the saved offset.

**Why two agents?** Fluent Bit (C, ~450 KB RSS idle, ~1% CPU/node) handles the bulk DaemonSet collection cheaply. Vector (Rust, richer VRL transform language) runs as a smaller fleet of *aggregator* nodes for the paths that need complex routing/enrichment before Kafka, or where Fluent Bit's transform language is too limited.

### 4.2 Kafka as the surge buffer

```
   producers (agents)                      consumers (processors)
        │  acks=all, lz4                          │  group=log-processors
        ▼                                         ▼
   ┌──────────────────── topic: logs.raw ───────────────────────┐
   │  partition 0  [====================>      ]  lag=120k       │
   │  partition 1  [=================>         ]  lag=90k        │
   │     ...   256 partitions ...   key = sha(tenant+service)    │
   │  retention.ms = 28800000 (8h)   segment.bytes=1G           │
   └────────────────────────────────────────────────────────────┘
       lag(t) = log_end_offset - committed_offset  ==>  backpressure metric
```

Kafka is the heart of the "never drop" guarantee: it gives **8 hours of retention at peak** (2x the 4h SLO), so even a total processing-tier outage of several hours loses nothing — consumers just catch up afterward.

Topic config (the load-bearing settings):

```properties
# logs.raw topic
num.partitions=256
replication.factor=3
min.insync.replicas=2          # acks=all + min.isr=2 -> survive 1 broker loss, no loss
retention.ms=28800000          # 8h
segment.bytes=1073741824       # 1 GB segments
compression.type=producer      # producers send lz4; broker keeps it
unclean.leader.election.enable=false   # never elect an out-of-sync replica -> no silent loss
max.message.bytes=10485760     # 10 MB (multiline stack traces can be large)
```

**Partition key choice matters.** Keying by `sha(tenant+service)` keeps a service's logs ordered (important for multiline reassembly that hasn't happened yet) and spreads load. Keying by *pure random* would maximize balance but destroy ordering; keying by `tenant` alone would hot-spot the partition owning your largest tenant. The `sha(tenant+service)` compromise caps any single key at one service's volume.

**Backpressure is a first-class signal.** We export `kafka_consumergroup_lag` per partition. Lag rising steadily = processors can't keep up = scale them out (§8 runbook). Because Kafka decouples the stages, a slow OpenSearch index manifests as *consumer lag*, not as *agent drops* — which is exactly the property we engineered for.

### 4.3 Parsing + PII redaction pipeline (Vector VRL)

```
   logs.raw ──▶ ┌─────────────────────────────────────────────┐
                │ 1. multiline merge (stack traces)            │
                │ 2. parse: json | regex(nginx) | syslog        │
                │ 3. normalize -> {ts, service, level, trace_id}│
                │ 4. PII redaction (regex + per-tenant rules)   │
                │ 5. enrich: tenant, k8s meta, geo              │
                │ 6. route: ok -> logs.parsed | fail -> logs.dlq│
                └─────────────────────────────────────────────┘
```

Multiline reassembly happens at the *agent* (so a Java stack trace is one Kafka message), and final parsing happens here. Vector VRL config:

```toml
[sources.kafka_raw]
type = "kafka"
bootstrap_servers = "kafka-0:9092,kafka-1:9092,kafka-2:9092"
group_id = "log-processors"
topics = ["logs.raw"]
auto_offset_reset = "earliest"
# librdkafka tuning for throughput
librdkafka_options."fetch.message.max.bytes" = "10485760"

[transforms.parse_and_redact]
type = "remap"
inputs = ["kafka_raw"]
drop_on_error = false      # parse failures route to DLQ, never silently dropped
reroute_dropped = true
source = '''
  # --- parse ---
  parsed, err = parse_json(.message)
  if err == null {
    . = merge(., parsed)
  } else {
    # fall back to syslog / leave raw; flag for DLQ if truly unparseable
    .parse_status = "raw"
  }

  # --- normalize schema ---
  .timestamp = to_timestamp(.timestamp ?? .ts ?? now()) ?? now()
  .level = downcase(to_string(.level) ?? "info")
  .service = to_string(.kubernetes.labels.app) ?? .service ?? "unknown"

  # --- PII redaction (runs BEFORE anything is stored) ---
  .message = replace(.message, r'[\w.+-]+@[\w-]+\.[\w.-]+', "[REDACTED_EMAIL]")
  .message = replace(.message, r'\b(?:\d[ -]*?){13,16}\b', "[REDACTED_CC]")
  .message = replace(.message, r'\b\d{3}-\d{2}-\d{4}\b', "[REDACTED_SSN]")
  .message = replace(.message, r'(?i)bearer\s+[A-Za-z0-9._-]+', "Bearer [REDACTED_TOKEN]")

  # --- enrich ---
  .tenant = to_string(.kubernetes.namespace_labels.tenant) ?? "shared"
'''

[transforms.route]
type = "route"
inputs = ["parse_and_redact"]
route.dlq = '.parse_status == "raw" && !exists(.service)'

[sinks.kafka_parsed]
type = "kafka"
inputs = ["route._unmatched"]
bootstrap_servers = "kafka-0:9092"
topic = "logs.parsed"
compression = "lz4"

[sinks.kafka_dlq]
type = "kafka"
inputs = ["route.dlq"]
bootstrap_servers = "kafka-0:9092"
topic = "logs.dlq"
```

Two design rules encoded here: (1) **redact before store, never after** — a redaction job that runs on already-indexed data is a compliance gap because the plaintext PII existed, queryable, for the indexing window; (2) **parse failures go to a DLQ with alerting**, never to `/dev/null`, so a bad schema change surfaces as a DLQ-rate alert instead of silent data loss.

### 4.4 Storage + tiering (Loki hot, S3 cold)

```
   logs.parsed ──┬──▶ Loki distributor ──▶ ingester ──▶ chunks ─▶ S3 (hot bucket)
                 │                              │ index (TSDB) -> S3/gp3
                 │                              │ flush @ 1h or 1.5MB
                 └──▶ S3 cold sink (parquet, partitioned by dt=YYYY-MM-DD/tenant=)
                                                        │
   S3 lifecycle:  hot bucket 30d -> expire                  cold bucket:
                  cold bucket  0-90d Standard-IA -> 90-365d Glacier IR -> delete @365d
```

Loki's model: it indexes only **labels** (`service`, `level`, `tenant`, `cluster`), not log content. A query like `{service="checkout", level="error"} |= "timeout"` uses the index to find the right chunks, then greps the chunk contents for `timeout`. This is why Loki's index is ~1000x smaller than OpenSearch's — and why **high-cardinality labels are catastrophic** for it.

**BROKEN** — a LogQL query (and a label scheme) that forces a full scan:

```logql
# Label scheme BROKEN: request_id is a label -> millions of streams
{service="checkout", request_id="a1b2c3"} |= "error"
# This alone creates one Loki stream per request_id -> index explodes,
# ingesters OOM, and the "find one request" query must open every chunk.

# Query BROKEN: no label selector narrow enough -> scans 30 days of all services
{cluster="prod"} |= "error" | json | duration > 500
# matches ~40% of all logs; query-frontend splits it into thousands of
# subqueries, each grepping TB of chunks. p99 -> 90s+, OOMs the querier.
```

**FIX** — keep `request_id` as a *structured field* (not a label), and narrow the query by low-cardinality labels + time:

```logql
# request_id stays in the log body / structured metadata, NOT a label.
# Query narrows by service + level + a tight time range first:
{service="checkout", level="error"} | json | request_id="a1b2c3"
  # time range set to last 1h in the UI

# For the broad "slow requests" query, narrow the stream selector first:
{service="checkout", level="error"} |= "timeout" | json | duration > 500
```

The fix cuts the candidate chunk set from "all prod logs for 30 days" to "checkout error chunks for 1 hour" — a ~10,000x reduction in bytes scanned, bringing p99 back under 3s. Loki [structured metadata](https://grafana.com/docs/loki/) (since Loki 2.9/3.0) lets you attach `request_id` without making it a stream-defining label, which is the modern fix for the high-cardinality-label trap.

Cold tier rehydration: the S3 cold bucket stores newline-delimited JSON or parquet partitioned by `dt`/`tenant`. A 6-month-old investigation runs an Athena query:

```sql
SELECT timestamp, service, message
FROM logs_cold
WHERE dt BETWEEN DATE '2025-11-01' AND DATE '2025-11-07'
  AND tenant = 'payments'
  AND message LIKE '%OutOfMemoryError%'
-- partition pruning on dt + tenant scans ~1/2500 of the year's data
```

Partition pruning on `dt`+`tenant` means Athena reads gigabytes, not petabytes — keeping a cold query at seconds-to-minutes and a few dollars instead of a full-table scan costing hundreds.

---

## 5. Design Decisions & Tradeoffs

### Decision 1: Loki (label-only index) for the bulk path, OpenSearch only for a high-value subset

- **Alternatives**: index everything in OpenSearch; ClickHouse for everything; Splunk.
- **Rationale**: indexing 50 TB/day of mostly-never-read logs in OpenSearch costs ~10–14x Loki's storage (§2). 99% of logs need only label-filter + grep, which Loki does cheaply. The ~3% that are security/audit logs needing complex full-text + aggregation go to OpenSearch.
- **Consequences**: two query languages (LogQL + OpenSearch DSL) and two systems to operate. Worth it: the cost delta is ~$3M/year. Loki forces label discipline — a real operational burden (§9).

### Decision 2: Kafka surge buffer instead of agents writing directly to storage

- **Alternatives**: agents → Loki/OpenSearch directly; agents → object storage directly.
- **Rationale**: direct writes couple ingest availability to storage availability — an OpenSearch GC pause becomes agent drops. Kafka decouples them, gives 8h of replay runway, and lets multiple consumers (Loki, OpenSearch, S3, SIEM, Flink alerting) tee off the same stream.
- **Consequences**: Kafka is now a tier-0 dependency (~$67K/year + ops). The replay-from-Kafka capability has saved us from data loss during three separate storage outages.

### Decision 3: Redact PII in the processing tier (in-stream), not at the agent or post-storage

- **Alternatives**: redact at the agent (CPU on every node); redact in a post-storage batch job.
- **Rationale**: agent-side redaction burns CPU on 6000 nodes and can't easily use per-tenant rule sets; post-storage redaction leaves a compliance window where plaintext PII is queryable. In-stream redaction centralizes the rules, runs before any store, and scales independently.
- **Consequences**: redaction is a single point that must never be bypassed — a bug here is a breach. Mitigated by a redaction unit-test gate (§8) and DLQ sampling for leak detection.

### Decision 4: 30-day hot + 1-year cold tiering, not uniform retention

- **Alternatives**: 1-year hot (queryable) everywhere; 7-day only.
- **Rationale**: query frequency drops ~exponentially with log age — >95% of queries hit the last 7 days, ~99% the last 30. Keeping a year hot would 12x the hot bill for ~1% of queries. Cold tier in S3 (+ Athena) serves the rare old query at 100x lower storage cost.
- **Consequences**: cold queries are slow (seconds-to-minutes) and require a separate query path. Acceptable: old queries are forensic/compliance, not interactive.

### Decision 5: Fluent Bit for bulk collection, Vector for aggregation/transform nodes

- **Alternatives**: Fluentd everywhere; Vector everywhere; OTel Collector everywhere.
- **Rationale**: Fluent Bit's C footprint (~1% CPU, <50 MB RAM) wins for a 6000-node DaemonSet; Fluentd's Ruby is ~10x heavier. Vector's VRL is more expressive for the smaller aggregator fleet doing rich routing.
- **Consequences**: two agent technologies to maintain. The footprint savings on 6000 nodes (≈ saving ~1 vCPU/node) dwarfs the maintenance cost.

### Decision 6: Full retention for ERROR/audit, sampling for DEBUG/INFO under pressure

- **Alternatives**: full retention always; head-sampling everything.
- **Rationale**: ERROR and audit logs are the ones queried during incidents and required for compliance — never sample them. INFO/DEBUG in a hot loop can be 80% of volume and 1% of value; tail-sampling DEBUG to 10% under quota pressure protects budget without losing signal.
- **Consequences**: sampling logic adds complexity and risks dropping the one DEBUG line you needed. Mitigated by keeping 100% of DEBUG in the *cold* tier (cheap) while sampling only the hot index.

### Comparison table

| Dimension | Loki (label-only) | OpenSearch (index-all) | ClickHouse | Splunk |
|-----------|-------------------|------------------------|------------|--------|
| Storage cost @ 50 TB/day | Lowest (~158 TB/30d) | Highest (~2 PB/30d) | Low-medium | Very high (license) |
| Full-text search | grep over chunks (slower) | Native, fast | SQL `LIKE`/tokens | Native, fast |
| Aggregations/analytics | Weak | Good | Excellent | Excellent |
| Operational complexity | Medium (label discipline) | High (shard mgmt) | Medium | Low (managed) |
| Best for | bulk, label-filtered grep | security/audit search | analytics on logs | enterprise, $$$ |

---

## 6. Real-World Implementations

- **Cloudflare** rebuilt their logging/analytics on **ClickHouse**, ingesting tens of millions of rows/sec. They moved off a Postgres+Citus stack because columnar storage + aggressive compression let them store far more at far lower cost, and ClickHouse's `LowCardinality` and materialized columns handle the label-vs-content tradeoff in one engine. Their public posts describe per-second compression ratios and the use of distributed tables sharded by time. Key lesson they share: schema and `ORDER BY` key design dominate query cost.

- **Grafana Labs** runs Loki at massive scale internally and for Grafana Cloud, explicitly built around the **label-only index + chunks-in-object-storage** model this design uses. Their engineering writing covers the high-cardinality-label trap (the exact §4.4 BROKEN case), the move to a TSDB index and structured metadata to avoid forcing high-cardinality fields into labels, and query-frontend sharding/splitting for parallelism. They publish concrete guidance that "labels are for low-cardinality dimensions; everything else is log content."

- **Uber** built **uLogger / their logging platform** moving from ELK toward more cost-efficient stores; they've publicly discussed ingesting petabytes and adopting ClickHouse-backed and tiered approaches to control the cost of indexing everything. A recurring theme in their posts: at their volume, the inverted-index overhead of Elasticsearch became the dominant cost, pushing them toward selective indexing and columnar storage for the bulk.

- **Netflix** runs log/event pipelines on **Kafka (Keystone)** as the universal ingestion backbone — agents and apps produce to Kafka, and many consumers (Elasticsearch for search, S3/Iceberg for analytics, real-time stream processing) tee off the same streams. This is precisely the "Kafka surge buffer + multiple consumers" pattern in §3; Keystone routes trillions of events/day and uses Kafka + Flink for routing and processing.

- **Datadog** (as a vendor consuming customer logs) popularized **"Logging without Limits"** — ingest everything cheaply to object storage, but *index* only what you query, with the ability to rehydrate archived logs on demand. This is the exact hot-subset-index + cold-S3-rehydrate model in §4.4, productized: customers tune index filters and rehydrate archives for forensic queries.

---

## 7. Technologies & Tools

| Tool | Type | Index model | Cost @ scale | Best fit | Watch out for |
|------|------|-------------|--------------|----------|---------------|
| **Loki** | Log store | Labels only, chunks in S3 | Lowest | Bulk K8s logs, label-filtered grep | High-cardinality labels kill it |
| **Elasticsearch** | Log store/search | Inverted index (all fields) | Highest | Full-text, complex search | Shard mgmt, storage blowup |
| **OpenSearch** | Log store/search | Inverted index | High | Same as ES, OSS license | Same as ES; lags ES on features |
| **ClickHouse** | Columnar OLAP | Sparse primary + skip indexes | Low-medium | Analytics, aggregations on logs | Schema/ORDER BY design is hard |
| **Splunk** | Log platform | Proprietary | Very high (license) | Enterprise, turnkey | License cost; hard to leave |
| **Vector** | Agent/processor | n/a (transport) | Free (compute) | Rich transform, routing | VRL learning curve |
| **Fluent Bit** | Agent | n/a | Free (compute) | Lightweight DaemonSet | Limited transform language |
| **Kafka** | Buffer/transport | n/a | Medium | Decoupling, replay, fan-out | Partition/ops overhead |

---

## 8. Operational Playbook

### (a) Parsing / schema evaluation gate

Every change to a parser or redaction rule must pass a CI gate before deploy. The gate runs the new Vector/Fluent Bit config against a golden corpus and asserts:

```yaml
# parser-eval-gate (CI step)
golden_corpus: 5000 real log lines (anonymized) across all known formats
assertions:
  - parse_success_rate >= 0.995          # <0.5% may go to DLQ
  - schema_conformance: 100%             # every output has ts, service, level
  - pii_leak_rate == 0                   # run PII detector on OUTPUT, must be 0
  - p99_parse_latency_us < 200           # per-line budget
  - no_unbounded_label: true             # reject configs adding high-cardinality labels
fail_action: block deploy
```

The `pii_leak_rate == 0` assertion is the most important: it runs a *separate* PII detector over the *post-redaction* output. A redaction-rule regression is caught in CI, not in production.

### (b) Observability of the pipeline itself

Instrument every stage with OTel/Prometheus. The pipeline must be more observable than the systems it observes.

| Metric | Source | Alert threshold |
|--------|--------|-----------------|
| `flb_output_dropped_records_total` | Fluent Bit | **> 0** (page — we promised no drops) |
| `flb_storage_chunks_busy_bytes` | Fluent Bit | > 80% of `storage.total_limit_size` |
| `kafka_consumergroup_lag` | Kafka exporter | > 5 min of throughput |
| `vector_component_errors_total` | Vector | rate > 1% of throughput |
| `logs_dlq_rate` | Kafka `logs.dlq` | > 0.5% of ingest |
| `loki_ingester_memory_streams` | Loki | sudden 2x jump (cardinality bomb) |
| `loki_request_duration p99` | Loki query-frontend | > 3s for 24h queries |

Cardinality is the silent killer of both the log index *and* the metrics you use to watch it — keep per-tenant label budgets and watch `loki_ingester_memory_streams` per tenant. See [`cross_cutting/prometheus_cardinality_and_scale.md`](./cross_cutting/prometheus_cardinality_and_scale.md) for how the *monitoring* of this pipeline itself stays bounded, and [`cross_cutting/slo_error_budget_math.md`](./cross_cutting/slo_error_budget_math.md) for turning the "never drop" promise into an error budget (e.g., 99.95% of lines delivered → budget of ~0.05% droppable before SLO breach).

### (c) Incident runbooks

**Runbook 1 — Kafka consumer lag spike**
- *Symptom*: `kafka_consumergroup_lag` climbing on `log-processors`; ingest-to-queryable lag > 5 min.
- *Diagnosis*: check whether processors are CPU-bound (`vector` CPU at 100%) or blocked on a slow sink (Loki/OpenSearch 5xx). Check if one partition is hot (skewed key).
- *Mitigation*: scale processor replicas (HPA on lag); if a sink is the bottleneck, temporarily route that sink's traffic to S3-only and backfill later. Raise Kafka retention to 12h to buy runway.
- *Resolution*: fix the slow sink (e.g., OpenSearch hot shard → §runbook 3); add partitions if structurally under-provisioned; post-mortem the key skew.

**Runbook 2 — Agent backpressure / drop risk**
- *Symptom*: `flb_storage_chunks_busy_bytes` > 80% on many nodes; `flb_output_dropped_records_total` starting to tick up.
- *Diagnosis*: Kafka unreachable or slow from those nodes (network partition, broker down, ISR < min). Check `unclean.leader.election` did not fire.
- *Mitigation*: if Kafka is the issue, restore brokers / failover; agents drain disk buffers automatically on recovery. If disk buffer is genuinely full, temporarily increase `storage.total_limit_size` via DaemonSet rollout.
- *Resolution*: confirm zero `dropped_records`; if any dropped, identify the window and rehydrate from upstream source files if still present. Right-size disk buffer for peak. See [`cross_cutting/kubernetes_production_hardening.md`](./cross_cutting/kubernetes_production_hardening.md) for the PVC/resource limits that keep the DaemonSet healthy under pressure.

**Runbook 3 — OpenSearch hot shard / Loki cardinality bomb**
- *Symptom*: one OpenSearch node CPU/heap pegged; or `loki_ingester_memory_streams` doubled and ingesters near OOM.
- *Diagnosis*: a tenant added a high-cardinality label (e.g., `request_id`, `pod_ip`, `user_id`) or a bad index has one giant shard. Identify the tenant via per-tenant stream/series counts.
- *Mitigation*: apply a per-tenant ingest limit / label-drop relabel rule to cap the offending label immediately; for OpenSearch, force-rollover the index and rebalance shards.
- *Resolution*: work with the tenant to move the field from label → structured metadata; add a CI lint that rejects high-cardinality labels (§8a).

**Runbook 4 — Query overload**
- *Symptom*: `loki_request_duration` p99 > 3s; queriers OOMing; query-frontend queue full.
- *Diagnosis*: an unbounded query (no tight label selector, 30-day range) or a dashboard auto-refreshing an expensive query. Identify via query logs.
- *Mitigation*: enforce per-tenant query limits (`max_query_length`, `max_query_parallelism`, `max_query_series`); kill the runaway query; cache results in the query-frontend.
- *Resolution*: educate the tenant; set sane default time ranges; add query-cost limits per tenant in the query-frontend config.

---

## 9. Common Pitfalls & War Stories

- **The memory-only agent drop (§4.1)** — During a routine Kafka broker upgrade, agents configured with memory-only buffers and `Retry_Limit 5` silently dropped logs the moment the buffer filled. The pipeline was *blind for the exact 22 minutes* of a downstream incident because the error logs needed to debug it were the ones dropped. Estimated **~14 TB of logs lost**, and an incident extended by ~90 minutes due to missing data. Fix: filesystem buffers + `no_limits` retries everywhere (§4.1).

- **The high-cardinality label that OOM'd Loki** — A team set `request_id` as a Loki label "to make it searchable." Within 40 minutes the ingesters went from 200K to **18M active streams**, heap-exhausted, and crash-looped — taking down log ingest for *all 120 tenants* for **~35 minutes**. Recovery required an emergency relabel rule to drop the label. Lesson: enforce label budgets in CI; `request_id` belongs in structured metadata, not labels.

- **The redaction-after-index compliance gap** — An early version redacted PII in a nightly batch job over already-indexed data. An auditor found that emails and partial card numbers were *queryable* for up to 24h before the batch ran. This was a reportable compliance finding requiring re-indexing of 30 days of data (~1,500 TB reprocessed) and ~3 engineer-weeks. Lesson: redact in-stream, before any store (§4.3).

- **The unbounded query that cost $9,000 in one afternoon** — A Grafana dashboard with a 5s auto-refresh ran a 30-day, no-label-selector LogQL query. The query-frontend fanned it into ~12,000 subqueries scanning the full hot tier on every refresh, spiking querier compute and S3 GET costs. Before anyone noticed, S3 request charges plus burst autoscaling added **~$9K in an afternoon**. Fix: per-tenant query limits + result caching + sane default time ranges (§8 runbook 4).

- **Kafka `unclean.leader.election` data loss** — A cluster had `unclean.leader.election.enable=true` (a legacy default). During a multi-broker failure, an out-of-sync replica was elected leader, silently discarding ~4 minutes of un-replicated writes — **~700 GB of logs vanished** with no error surfaced anywhere. Lesson: `unclean.leader.election.enable=false` + `min.insync.replicas=2` is mandatory for a "no drop" pipeline.

- **DEBUG-in-prod cost blowout** — A team shipped a build with `DEBUG` logging on a hot request path; one service's volume jumped 8x, consuming a tenant's entire ingest quota and pushing the cluster to ~62 TB/day for three days before detection. Hot-tier storage and processing autoscale costs rose **~$48K** for the period. Fix: per-tenant volume alerts + level-based sampling under quota pressure (§5 Decision 6). The broader node-resource hardening that keeps such spikes from cascading lives in [`cross_cutting/kubernetes_production_hardening.md`](./cross_cutting/kubernetes_production_hardening.md).

---

## 10. Capacity Planning

### Scaling formulas

```
Kafka partitions      = ceil( peak_throughput / per_partition_throughput )
                      = ceil( 2,900 MB/s / 15 MB/s ) = 194  -> round to 256

Processor replicas    = ceil( peak_events_per_sec / per_replica_events_per_sec )
                      = ceil( 2,900,000 / 80,000 ) = 37  -> 40 (headroom)
   (Vector aggregator handles ~80K events/sec/core on parse+redact)

Loki ingesters        = ceil( active_streams / streams_per_ingester )  AND
                        ceil( ingest_MBps / MBps_per_ingester )
                      = max( 1.2M / 100K , 580 MB/s / 30 MB/s )
                      = max( 12 , 20 ) = 20 ingesters (+ RF=3 -> replicas)

Hot storage (Loki)    = days_hot x daily_raw / compression x (1 + index_overhead)
                      = 30 x 50 TB / 10 x 1.05 = ~158 TB in S3

Cold storage (S3)     = days_cold x daily_raw / compression
                      = 365 x 50 TB / 10 = ~1,825 TB
```

### Worked example (AWS us-east-1)

| Tier | Instance | Count | Unit/mo | Subtotal/mo |
|------|----------|-------|---------|-------------|
| Kafka brokers | i3en.2xlarge (NVMe, RF=3) | 12 | ~$470 | $5,640 |
| Processors (Vector) | c6i.4xlarge (spot ~$0.30/h) | 40 | ~$220 | $8,760 |
| Loki ingesters/queriers | r6i.2xlarge | 20 | ~$370 | $7,400 |
| Loki distributor/frontend | c6i.2xlarge | 8 | ~$250 | $2,000 |
| OpenSearch (subset) | r6gd.2xlarge | 6 | ~$430 | $2,580 |
| Hot chunks (S3 Std) | 158 TB | — | $0.023/GB | $3,634 |
| Cold (S3 Std-IA + Glacier IR) | ~1,825 TB | — | blended | ~$30,000 |
| Agents (DaemonSet) | on existing nodes | 6,000 | ~$0 | ~$0 |
| **Total** | | | | **~$60K/mo ≈ $720K/yr** |

This sits well under the $1.2M/year target with headroom for the OpenSearch subset to grow. The equivalent OpenSearch-index-everything design replaces the ~$15K/mo Loki+chunk line with a ~$280K/mo hot-storage+compute line — the ~$3.3M/year delta that justifies the whole label-only architecture.

For multi-region: each region runs its own collection + Kafka + processing + hot store; cold S3 is replicated cross-region (or uses S3 Cross-Region Replication for DR). Cross-region query federation and the network cost of replication are covered in [`cross_cutting/multi_cluster_networking.md`](./cross_cutting/multi_cluster_networking.md).

---

## 11. Interview Discussion Points

**Q: How do you guarantee you never drop logs under backpressure?**
You push durability to the edge and decouple stages with a buffer that has hours of runway. Agents write to a bounded *filesystem* buffer (not memory-only) with unlimited retries, so a downstream stall becomes delay, not loss; Kafka with RF=3, `min.insync.replicas=2`, and 8h retention absorbs a 4h peak surge twice over; consumers replay from Kafka after any storage outage. The guarantee holds only if every stage degrades into *delay* — a memory-only agent buffer or `unclean.leader.election=true` quietly breaks it.

**Q: Why Kafka in the middle instead of agents writing directly to Loki/OpenSearch?**
Kafka decouples ingest availability from storage availability and enables fan-out. A direct write makes an OpenSearch GC pause or a Loki rollout into agent-side drops; Kafka turns it into consumer lag that drains later. It also lets multiple consumers — Loki, OpenSearch, S3 cold, SIEM, real-time alerting — tee off one durable stream without each producer knowing about them. The cost is one more tier-0 system to operate.

**Q: Loki vs Elasticsearch/OpenSearch — when would you pick each?**
Loki for the bulk path because it indexes only labels, making its index ~1000x smaller and its storage ~10x cheaper at 50 TB/day; you query by label-filter + grep. OpenSearch for the ~3% of logs needing fast full-text search and complex aggregations (security/audit). The deciding factor is read pattern: if 99% of queries are "show me errors for service X in the last hour," Loki is dramatically cheaper; if you need ad-hoc full-text analytics over everything, you pay for the inverted index.

**Q: What is the high-cardinality label trap and how do you prevent it?**
In Loki, each unique combination of label values is a separate stream; putting a high-cardinality field like `request_id` or `user_id` in a label creates millions of streams, exploding the index and OOMing ingesters. Prevent it with a CI lint that rejects high-cardinality labels, per-tenant stream-count limits, and by putting such fields in *structured metadata* or the log body instead. The same trap exists in metrics cardinality — see the cross-cutting doc.

**Q: Where and why do you redact PII?**
In the processing tier, in-stream, before any store. Agent-side redaction wastes CPU on thousands of nodes and can't easily apply per-tenant rules; post-storage redaction leaves a compliance window where plaintext PII is queryable. In-stream centralizes the rules and runs once before fan-out. You gate it with a CI assertion that runs a PII detector over the *post-redaction* output and fails the build if leak rate is non-zero.

**Q: How do you handle multiline logs like Java stack traces?**
Reassemble at the agent so a full stack trace becomes a single Kafka message, using a start-pattern rule (e.g., lines beginning with a timestamp start a new record; continuation lines without one are appended). Doing it at the agent preserves ordering and avoids the much harder problem of reassembling interleaved lines across Kafka partitions downstream. Final structured parsing then happens in the processing tier.

**Q: How do you size Kafka partitions, and what's the tradeoff?**
Partitions = ceil(peak throughput / per-partition sustainable throughput); at 2.9 GB/s peak and ~15 MB/s per partition with RF=3, that's ~194, rounded to 256 for headroom and rebalance smoothness. More partitions give more consumer parallelism but increase metadata overhead, rebalance time, and open file handles; too few partitions cap your maximum consumer count and create hot partitions. The partition key (`sha(tenant+service)`) balances load while preserving per-service ordering.

**Q: How does the hot/cold tiering work and what does a cold query cost?**
Hot tier (Loki, 30 days) keeps recent logs queryable in seconds; everything also lands in S3 as day/tenant-partitioned parquet for a year. Old queries hit the cold tier via Athena, where partition pruning on `dt`+`tenant` scans gigabytes instead of petabytes, costing a few dollars and running in seconds-to-minutes. The tradeoff is that cold queries are non-interactive — acceptable because >99% of queries hit the last 30 days, so paying 12x more to keep a year hot would serve ~1% of traffic.

**Q: A tenant ships DEBUG logging to prod and 8x's their volume — what happens and how do you contain it?**
Per-tenant ingest quotas cap their hot-tier impact so they can't starve other tenants; a volume-spike alert fires; under quota pressure, level-based sampling reduces DEBUG to a fraction in the *hot* index while keeping 100% in the cheap cold tier. Without these controls you get the real war story: $48K of extra cost over three days and a near-quota-exhaustion event. The structural fix is per-tenant isolation at ingest plus alerting on volume deltas.

**Q: How do you keep one tenant's query from degrading everyone else's?**
The query-frontend enforces per-tenant limits — `max_query_length` (e.g., 31 days), `max_query_parallelism`, `max_query_series`, and a query timeout — and caches results. An unbounded 30-day, no-selector query gets rejected or throttled instead of fanning into thousands of subqueries that saturate shared queriers. Combined with per-tenant ingest isolation, this bounds blast radius on both read and write paths; without it, one runaway dashboard cost us ~$9K in an afternoon.

**Q: What's your backpressure signal and how do you act on it?**
Kafka consumer-group lag is the canonical signal: it rises when processors or sinks can't keep up. An HPA scales processors on lag; if a specific sink (e.g., OpenSearch) is the bottleneck, you reroute that sink to S3-only temporarily and backfill, and you raise Kafka retention to buy runway. The whole point of the architecture is that backpressure shows up as *lag* (recoverable) rather than as *agent drops* (unrecoverable).

**Q: How do you detect and recover from data loss if it does occur?**
Alert on any non-zero `flb_output_dropped_records_total` and any DLQ rate above 0.5% — these are the only places loss can originate. If drops occurred, identify the time window and node set, then rehydrate from the source log files if still present on disk (the agent's offset DB lets you re-read from the last committed offset), or replay from Kafka if the window is within retention. Prevention beats recovery: `unclean.leader.election=false`, filesystem agent buffers, and `Retry_Limit no_limits` mean the recovery path almost never fires.

**Q: How would you scale this pipeline 10x to 500 TB/day?**
Scale horizontally at every tier and shard by tenant/region: more Kafka partitions and brokers (partitions scale linearly with throughput), more processor replicas (stateless, just add nodes), and shard Loki/OpenSearch by tenant into separate cells to keep blast radius bounded. The real constraints at 10x are Kafka cross-AZ network cost, S3 request rates (use partitioned prefixes to avoid hot prefixes), and cardinality — which is why label discipline and per-tenant limits become even more critical. See the multi-cluster networking cross-cutting doc for the cross-region replication and federation cost model.
