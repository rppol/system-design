# Logging & Log Aggregation

> Phase 6 — Observability & SRE · Difficulty: Intermediate

Logs are the **per-event, high-cardinality record of what a system did** — the pillar you reach for when metrics tell you *that* something broke and you need to know *exactly what and why*. Log aggregation collects logs from thousands of ephemeral containers into a central, searchable store. This module covers the two dominant architectures (index-everything EFK/ELK vs. label-and-store Loki), structured logging, parsing pipelines, and the retention/sampling discipline that keeps logging from bankrupting you.

---

## 1. Concept Overview

A log is a timestamped, often-textual record of a discrete event: a request served, an exception thrown, a config reloaded. In a distributed system running hundreds of ephemeral pods, you can't `ssh` and `tail` — pods die and their local logs vanish. **Log aggregation** ships every log line off-host to a central, durable, searchable store so you can query across the whole fleet.

A logging pipeline has four stages:

1. **Emit** — applications write **structured logs** (JSON key/value), ideally to `stdout`/`stderr` (the 12-factor convention) so the platform captures them.
2. **Collect** — a node-level agent (Fluent Bit, Fluentd, Vector, Promtail/Alloy, Filebeat) tails container log files, adds metadata (pod, namespace, node), and forwards.
3. **Process/parse** — parse unstructured lines into fields, enrich with labels, redact secrets/PII, drop/sample noise.
4. **Store + query** — an index/store (Elasticsearch/OpenSearch, Loki, ClickHouse, cloud log services) supports full-text or label-scoped search and retention.

Two architectural philosophies dominate. **EFK/ELK** (Elasticsearch + Fluent + Kibana) indexes the *full text* of every log, enabling powerful arbitrary search at high storage/compute cost. **Loki** indexes only a small set of *labels* (like Prometheus) and stores the log body compressed in object storage, then brute-force greps within a label-scoped time slice — far cheaper, with search constrained to label selectors plus regex.

The defining economic fact: logs are the most expensive observability pillar per unit of insight. Ingest scales linearly with traffic, indexing multiplies storage, and full-text indexes are RAM-hungry. **Retention, sampling, and what-you-index are cost-control decisions, not afterthoughts.**

---

## 2. Intuition

> **One-line analogy**: Metrics are the gauges on your car's dashboard (constant, cheap, "engine temp is rising"); logs are the black-box flight recorder (every event, expensive to store, indispensable when you need to reconstruct exactly what happened in the seconds before the crash).

**Mental model**: Think of logs as an append-only event stream flowing from many sources into one river. A collector is a pump on each machine; a parser turns muddy text into clean fields; the store is a reservoir you can search. EFK builds a full search index over the whole reservoir (find anything, pay for it); Loki just tags each bucket with labels and lets you grep within the tagged buckets you select (cheap, but you must know roughly where to look).

**Why it matters**: When a metric or trace points you at a failing service, logs are where the actual error message, stack trace, and offending payload live. They carry the high-cardinality detail (user IDs, request IDs, exact SQL) that you deliberately keep *out* of metrics (see [observability_metrics_prometheus](../observability_metrics_prometheus/)). Without aggregation, that evidence dies with the pod.

**Key insight**: The decision that dominates a logging system's cost and usability is **what you index versus what you merely store**. Index everything (ELK) and you get omnipotent search but a bill that scales with log volume and a cluster that needs babysitting. Index only labels (Loki) and you slash cost dramatically but trade away free-text-across-everything search. Pick deliberately, and pair logging with a `trace_id` so logs, metrics, and traces correlate.

---

## 3. Core Principles

1. **Structured over unstructured.** Emit JSON with consistent field names; structured logs are queryable without fragile regex parsing.
2. **Log to stdout/stderr; let the platform collect.** Apps shouldn't manage files, rotation, or shipping (12-factor).
3. **Index what you query, store the rest.** Indexing is the cost; minimize the indexed surface (labels) and compress the bodies.
4. **Correlate via IDs.** Inject `trace_id`/`span_id` into every log line so you can pivot logs ↔ traces ↔ metrics.
5. **Retention and sampling are first-class.** Tier hot/warm/cold, set TTLs, sample chatty success logs; keep all errors.
6. **Levels mean something.** ERROR = actionable failure, WARN = degraded, INFO = milestones, DEBUG = off in prod by default.
7. **Never log secrets/PII.** Redact at the source and in the pipeline; logs are widely accessible and long-lived.

---

## 4. Types / Architectures / Strategies

### Architecture comparison

| Dimension | EFK / ELK | Loki | ClickHouse-based |
|-----------|-----------|------|------------------|
| Indexing | Full-text (inverted index) | Labels only | Columnar, optional indexes |
| Storage cost | High (index ~= data size) | Low (compressed in object store) | Low–medium |
| Search power | Arbitrary full-text, fast | Label selector + regex grep | SQL, very fast aggregations |
| Operational load | High (ES cluster, shards, JVM heap) | Low–medium | Medium |
| Best for | Rich ad-hoc search, security/SIEM | High-volume, cost-sensitive, k8s | Analytics at scale |

### Collector options

| Collector | Notes |
|-----------|-------|
| Fluent Bit | Lightweight C agent (~1 vCPU low RAM), DaemonSet default; great for k8s node collection |
| Fluentd | Ruby, plugin-rich, heavier; aggregator tier |
| Vector | Rust, fast, vendor-neutral transforms (VRL), good routing/sampling |
| Promtail / Grafana Alloy | Loki-native collector, label-centric |
| Filebeat | Elastic's lightweight shipper into ELK |

### Logging strategies

| Strategy | Purpose |
|----------|---------|
| Sidecar | Per-pod log shipper (when DaemonSet can't see the source) |
| DaemonSet (node agent) | One collector per node tails all container logs — the standard k8s pattern |
| Direct-to-backend | App ships logs over the network (couples app to backend; avoid) |
| Aggregator tier | Node agents -> central Fluentd/Vector -> backend (buffering, transform, fan-out) |

---

## 5. Architecture Diagrams

```
Kubernetes node-agent log pipeline (DaemonSet)

  pods (stdout/stderr) -> /var/log/containers/*.log
                                |
                node: Fluent Bit / Vector DaemonSet  (tail + add k8s metadata)
                                |
                 parse JSON, enrich (pod/ns/node), redact PII, sample
                                |
               +----------------+-----------------+
               v                                  v
        Elasticsearch/OpenSearch            Loki (label index +
        (full-text index) + Kibana           chunks in S3) + Grafana
                                |
                         alerts / dashboards / search
                         correlate via trace_id -> Tempo/Jaeger


EFK vs Loki storage shape

  EFK:   [log line] --inverted index over every term--> fast any-search, big storage
  Loki:  [labels: {app, ns, level}] -> tiny index
         [compressed log chunk in S3] -> grep within selected labels+time window


Retention tiering (cost control)

  hot  (0-7d)   fast SSD, fully indexed/searchable        ~$$$
  warm (7-30d)  cheaper storage, slower queries           ~$$
  cold (30-365d) object storage / archive, restore-to-query ~$
  delete >TTL  (compliance-driven; e.g. 90d app, 1y audit)
```

---

## 6. How It Works — Detailed Mechanics

### Structured logging at the source

```python
# BROKEN: unstructured string log — needs fragile regex to parse, no fields to query on.
logger.info("user 8842 placed order 991 total 49.95 failed: card declined")

# FIX: structured JSON with stable field names + correlation IDs.
import structlog
log = structlog.get_logger()
log.error(
    "order_failed",
    user_id="8842",
    order_id="991",
    amount=49.95,
    reason="card_declined",
    trace_id="4bf92f3577b34da6a3ce929d0e0e4736",   # ties this log to the trace
    span_id="00f067aa0ba902b7",
)
# emits: {"event":"order_failed","level":"error","user_id":"8842",...,"trace_id":"4bf9..."}
```

App-side logging conventions (loggers, MDC, `trace_id` injection) are owned by [../../backend/observability_and_monitoring](../../backend/observability_and_monitoring); here we focus on the platform pipeline.

### Node collector config (Fluent Bit)

```ini
[INPUT]
    Name              tail
    Path              /var/log/containers/*.log
    Parser            cri
    Tag               kube.*
    Mem_Buf_Limit     50MB          # backpressure: cap memory before disk-buffering
    Refresh_Interval  5

[FILTER]
    Name                kubernetes    # enrich with pod/namespace/labels from k8s API
    Match               kube.*
    Merge_Log           On

[FILTER]
    Name    modify                   # redact a sensitive field
    Match   kube.*
    Remove  authorization

[OUTPUT]
    Name            es               # or 'loki'
    Match           kube.*
    Host            elasticsearch
    Logstash_Format On
    Retry_Limit     5
    storage.total_limit_size 5G      # disk buffer cap to survive backend outages
```

### Parsing unstructured logs into fields (Vector VRL)

```toml
# Vector transform: parse an nginx access line into structured fields, then sample 2xx.
[transforms.parse_nginx]
type = "remap"
inputs = ["nginx_logs"]
source = '''
  . = parse_regex!(.message, r'^(?P<ip>\S+) \S+ \S+ \[(?P<ts>[^\]]+)\] "(?P<method>\S+) (?P<path>\S+) [^"]+" (?P<status>\d+) (?P<bytes>\d+)')
  .status = to_int!(.status)
'''

[transforms.sample_success]            # keep all errors, sample 10% of 2xx
type = "sample"
inputs = ["parse_nginx"]
rate = 10                              # keep 1 in 10
exclude = '.status >= 400'             # never sample out errors
```

### Loki labels — the cardinality rule applies here too

```yaml
# BROKEN: high-cardinality Loki labels create millions of tiny streams -> index blows up.
#   labels: {app, user_id, request_id, path}     # user_id/request_id are unbounded!

# FIX: keep labels bounded; put high-cardinality detail in the log LINE (it's still searchable
# via LogQL regex), not in labels.
#   labels: {app="api", namespace="prod", level="error"}
#   line:   {"user_id":"8842","request_id":"r-91c2", ...}
```

```logql
# LogQL: select by labels (uses the index), then filter the body (greps the chunks).
{app="api", namespace="prod", level="error"} |= "card_declined" | json | amount > 40

# turn logs into a metric: error rate from logs
sum(rate({app="api"} |= "error" [5m]))
```

### Retention and lifecycle

```yaml
# Elasticsearch ILM (Index Lifecycle Management) — tier and delete automatically.
policy:
  phases:
    hot:    { actions: { rollover: { max_age: "1d", max_size: "50gb" } } }
    warm:   { min_age: "7d",  actions: { shrink: { number_of_shards: 1 }, forcemerge: { max_num_segments: 1 } } }
    cold:   { min_age: "30d", actions: { searchable_snapshot: { snapshot_repository: "s3-logs" } } }
    delete: { min_age: "90d", actions: { delete: {} } }   # app logs 90d; audit logs separate, 1y+
```

Rough cost intuition: at 10,000 logs/sec averaging 500 bytes, you ingest ~5 MB/s ≈ ~432 GB/day raw. Full-text indexing in ELK can roughly double on-disk size; Loki compresses bodies ~10x and indexes only labels, so the same volume costs a fraction. This is why volume sampling and `what-you-index` decisions dominate the bill.

---

## 7. Real-World Examples

- **Kubernetes default (EFK)**: a Fluent Bit DaemonSet tails `/var/log/containers/*.log`, enriches with pod/namespace metadata via the Kubernetes API, and forwards to Elasticsearch/OpenSearch with Kibana for search. The standard out-of-the-box cluster logging stack.
- **Grafana Labs / Loki adopters**: high-volume shops that moved off ELK to Loki cut log storage cost dramatically by indexing only labels and storing compressed chunks in S3 — accepting label-scoped search in exchange for ~10x lower cost.
- **Cloudflare / ClickHouse for logs**: very high-volume platforms use ClickHouse (columnar, SQL) for HTTP/request logs to get fast aggregations over hundreds of billions of rows at far lower cost than full-text indexing.
- **SIEM / security logging**: security teams keep full-text ELK (or Splunk) precisely because arbitrary search across all events is the requirement — the cost is justified by threat-hunting needs and long compliance retention.
- **Vector at scale**: companies put a Vector aggregator tier between node agents and backends to sample, redact PII, route by team, and buffer through backend outages.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Backend | ELK/EFK (full-text) | Loki (label-only) | Search power vs cost/ops |
| Index surface | Index everything | Index labels only | Query flexibility vs storage |
| Sampling | Keep 100% | Sample success logs | Completeness vs volume/cost |
| Collector | Fluent Bit (light) | Fluentd/Vector (rich) | Footprint vs transform power |
| Topology | Node agent direct | Node agent -> aggregator | Simplicity vs buffering/transform |
| Retention | Long, hot | Tiered hot/warm/cold + TTL | Instant search vs cost |
| Format | Free text | Structured JSON | Human-readable vs queryable |

---

## 9. When to Use / When NOT to Use

**Use centralized logging when:** you run distributed/ephemeral workloads (containers, serverless), need post-hoc debugging of specific events, must satisfy audit/compliance retention, or want to correlate detailed events with traces and metrics. It's mandatory in any non-trivial production system.

**Don't reach for logs when:** you need real-time trends, rates, or SLO alerting — metrics are cheaper and faster for aggregates ([observability_metrics_prometheus](../observability_metrics_prometheus/)); when you need to follow a single request across services — distributed tracing is purpose-built for that ([observability_tracing_and_otel](../observability_tracing_and_otel/)); or when you're tempted to compute counts/percentiles by parsing logs at query time — emit a metric instead. And never use logs as your only signal: parsing logs to drive alerts is slow and fragile compared to metrics. Be ruthless about *not* logging chatty success paths at full volume.

---

## 10. Common Pitfalls

**Pitfall 1 — Unstructured logs that can't be queried.**

```python
# BROKEN: free-text log; to find "all card declines over $40" you must regex-parse at query time.
logger.info(f"order {oid} for user {uid} failed: card declined, amount {amt}")
```

```python
# FIX: structured fields make it a trivial query (Kibana filter / LogQL | json | amount > 40).
log.error("order_failed", order_id=oid, user_id=uid, reason="card_declined", amount=amt,
          trace_id=current_trace_id())
```

**Pitfall 2 — Logging secrets and PII.** Authorization headers, tokens, full request bodies, emails, and PANs end up in logs that are broadly readable and retained for months — a compliance breach waiting to happen. FIX: redact at the source (don't log the field) and add a pipeline redaction stage (drop `authorization`, mask card numbers); restrict log access with RBAC and keep audit logs in a separate, tightly-scoped store.

**Pitfall 3 — No retention/sampling, so the bill (and cluster) explodes.** Keeping 100% of logs at full retention with full-text indexing means cost scales linearly with traffic and your ES cluster falls over during traffic spikes. FIX: sample chatty success logs (keep all errors), set ILM/TTL tiering (hot 7d → warm → cold → delete 90d), and cap collector buffers with backpressure so a backend outage doesn't OOM the node agent.

**Pitfall 4 — High-cardinality Loki labels.** Treating Loki labels like searchable fields (`user_id`, `request_id`) creates millions of tiny streams and destroys the very index efficiency Loki exists for. FIX: keep labels to a handful of bounded values (`app`, `namespace`, `level`); put high-cardinality detail in the log line and find it with LogQL filters/`| json`.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Elasticsearch / OpenSearch | Full-text indexed log store |
| Kibana / OpenSearch Dashboards | Search and visualization for ELK |
| Loki | Label-indexed, object-storage-backed log store |
| Grafana | Query/visualize Loki (LogQL) and correlate with metrics/traces |
| Fluent Bit | Lightweight node-level collector (k8s DaemonSet) |
| Fluentd | Plugin-rich collector/aggregator |
| Vector | Fast Rust collector with VRL transforms, sampling, routing |
| Promtail / Grafana Alloy | Loki-native collectors |
| Filebeat / Logstash | Elastic shippers/processors |
| ClickHouse | Columnar SQL store for very high-volume logs/analytics |
| Cloud log services | CloudWatch Logs, GCP Cloud Logging, Azure Monitor Logs |

---

## 12. Interview Questions with Answers

**Q1: Why do you need centralized log aggregation instead of reading logs on the host?**
In a distributed system, workloads run on ephemeral pods/VMs that are created and destroyed constantly, so local logs disappear when the container dies — exactly when you need them post-incident. Aggregation ships every line off-host to a durable, searchable central store so you can query across the whole fleet and retain logs past a pod's lifetime. It also enables cross-service correlation, which is impossible when logs are scattered per host.

**Q2: Compare ELK/EFK with Loki.**
ELK/EFK builds a full-text inverted index over every log line, enabling fast arbitrary search at the cost of high storage (index roughly the size of the data) and a heavy Elasticsearch cluster to operate. Loki indexes only a small set of labels (Prometheus-style) and stores compressed log bodies in object storage, then greps within a label-and-time-scoped slice — far cheaper and lower-ops, but search is constrained to label selectors plus LogQL regex. Choose ELK when arbitrary full-text search is a hard requirement (security/SIEM); choose Loki when you have high volume and want cost efficiency.

**Q3: What is structured logging and why does it matter?**
Structured logging emits each event as machine-parseable key/value data (typically JSON) with stable field names instead of a free-text sentence. It lets you query and aggregate by field (`status:500 AND amount>40`) without fragile, version-brittle regex parsing at query time. It also makes redaction, enrichment, and correlation (injecting `trace_id`) reliable, so it's the foundation of a usable logging pipeline.

**Q4: How do you correlate logs with traces and metrics?**
Inject the `trace_id` (and `span_id`) into every log line, so a log entry can be pivoted directly to its distributed trace and vice versa. Metrics carry the same dimensional labels (service, route), and exemplars can link a metric spike to a representative trace. The result is a three-pillar workflow: a metric alert points you at a service, you jump to the trace for the slow/failing request, then to the exact logs for that `trace_id` to read the error.

**Q5: What's the cost driver in logging, and how do you control it?**
The dominant cost is ingest volume multiplied by indexing and retention — full-text indexing can roughly double on-disk size, and keeping everything hot for months scales linearly with traffic. Control it by indexing only what you query (labels in Loki, selective fields in ELK), sampling chatty success logs while keeping all errors, and tiering retention (hot → warm → cold → delete via ILM/TTL). These are deliberate design choices, not afterthoughts, because logs are the most expensive observability pillar per unit of insight.

**Q6: Why log to stdout/stderr in containerized environments?**
The 12-factor convention treats logs as event streams the app writes to stdout/stderr, leaving collection, rotation, and shipping to the platform. The container runtime captures these to node files (`/var/log/containers/*.log`) where a DaemonSet collector tails and enriches them with pod/namespace metadata. This decouples the app from log infrastructure, avoids in-app file management and rotation bugs, and works uniformly across all workloads.

**Q7: How should you handle PII and secrets in logs?**
Never log them in the first place — don't include authorization headers, tokens, full payloads, or personal data in log statements. Add a defense-in-depth redaction stage in the pipeline (drop or mask fields like `authorization`, card numbers) and enforce RBAC on the log store since logs are widely readable and long-retained. Keep compliance/audit logs in a separate, tightly-scoped store with their own retention.

**Q8: What is log sampling and when is it safe?**
Sampling keeps only a fraction of logs to cut volume — for example, keep 1 in 10 successful 2xx responses while keeping 100% of errors and warnings. It's safe for high-volume, low-information success paths where you only need representative examples, but you must never sample out errors, security events, or audit records. Implement it in the collector/aggregator (Vector `sample`, Fluentd) with an explicit exclude for error severities.

**Q9: How does a node-agent (DaemonSet) log pipeline work in Kubernetes?**
A collector like Fluent Bit runs as a DaemonSet (one pod per node), tails the container log files under `/var/log/containers/`, and uses a Kubernetes filter to enrich each line with pod, namespace, and label metadata from the API. It then parses, redacts, optionally samples, and forwards to the backend (Elasticsearch or Loki), buffering to memory/disk with caps to survive backend outages. This is the standard pattern because one agent per node sees all containers' stdout without per-app configuration.

**Q10: When would you choose ClickHouse over ELK or Loki for logs?**
ClickHouse fits very high-volume logs where you mostly run aggregations and filtered scans (request logs, HTTP analytics) and want SQL with excellent compression and columnar speed over hundreds of billions of rows. It's cheaper than full-text ELK and faster for analytical queries than Loki's grep model, at the cost of not being a turnkey full-text search engine. Choose it when your access pattern is "aggregate and filter structured events at massive scale," not "free-text search everything."

**Q11: What log levels should you use and how in production?**
ERROR for actionable failures that need attention, WARN for degraded-but-handled conditions, INFO for significant business/lifecycle milestones, and DEBUG for verbose developer detail. In production keep DEBUG off by default (it's high-volume and costly) and make the level dynamically adjustable so you can temporarily raise verbosity during an incident. Consistent, meaningful levels let you sample and alert by severity and keep the signal-to-noise ratio sane.

**Q12: How do you build alerts or metrics from logs, and should you?**
You *can* compute log-derived metrics (e.g. Loki `sum(rate({app="api"} |= "error" [5m]))` or ELK threshold alerts), and it's useful when the only signal is in the log text. But prefer emitting a real metric at the source for anything you alert on, because metric pipelines are cheaper, faster, and more reliable than parsing/aggregating logs at query time. Use log-based alerts as a fallback or for one-off patterns, not as the backbone of your alerting (that belongs to [observability_metrics_prometheus](../observability_metrics_prometheus/)).

---

## 13. Best Practices

- **Emit structured JSON** with stable field names and a `trace_id`; log to stdout/stderr and let the platform collect.
- **Index only what you query** — labels in Loki, selective fields in ELK — and store bodies compressed.
- **Sample chatty success logs, keep all errors;** make log level dynamically adjustable for incidents.
- **Tier retention with ILM/TTL** (hot → warm → cold → delete); keep audit logs separate with longer retention.
- **Redact PII/secrets at the source and in the pipeline;** enforce RBAC on the log store.
- **Cap collector buffers with backpressure** so a backend outage never OOMs node agents.
- **Correlate the three pillars** — logs ↔ traces ↔ metrics — via shared IDs; don't build core alerting on log parsing.
- **Keep app-side logging conventions** consistent (see [../../backend/observability_and_monitoring](../../backend/observability_and_monitoring)).

---

## 14. Case Study

### Scenario: A logging bill quadruples and the ES cluster falls over during a traffic spike

A company runs EFK on Kubernetes. Apps log unstructured strings at INFO for every request, including full request bodies (which contain emails and tokens). As traffic grew to 12,000 logs/sec at ~600 bytes each (~620 GB/day raw, ~1.2 TB/day indexed), the monthly logging bill quadrupled, Kibana queries timed out, and during a marketing spike the Elasticsearch cluster red-flagged on heap pressure and dropped logs — exactly when engineers needed them.

```python
# BROKEN: unstructured, leaks PII, logs everything at full volume.
logger.info(f"request {req.path} body={req.body} auth={req.headers['Authorization']}")
#  -> 100% volume incl. success, PII + tokens in logs, no fields to query, ES heap melts
```

```python
# FIX 1: structured, redacted, correlated.
log.info("request_handled", route=req.route_template, status=resp.status,
         latency_ms=elapsed, trace_id=current_trace_id())   # no body, no auth header
log.error("request_failed", route=req.route_template, status=resp.status,
          reason=err.code, trace_id=current_trace_id())
```

```toml
# FIX 2: sample success in the Vector aggregator, keep all errors.
[transforms.sample]
type = "sample"
inputs = ["app_logs"]
rate = 10                       # keep 1 in 10
exclude = '.level == "error" || .level == "warn"'
```

```yaml
# FIX 3: tier retention so storage stops growing unbounded.
ilm_policy:
  hot:    { rollover: { max_age: "1d", max_size: "50gb" } }
  warm:   { min_age: "7d" }
  cold:   { min_age: "30d", searchable_snapshot: { repository: "s3-logs" } }
  delete: { min_age: "90d" }          # audit logs kept separately at 1y

# FIX 4 (optional, biggest lever): move non-security logs to Loki — index labels only,
# store compressed chunks in S3; ~10x cheaper, search via LogQL + trace_id.
```

After the changes: structured fields made Kibana/LogQL queries fast and precise (`reason:card_declined AND amount>40`), success-path sampling cut volume by ~85%, PII left the logs entirely, and tiered retention stopped unbounded storage growth. Moving bulk application logs to Loki (keeping ELK only for security/SIEM) dropped log storage cost by roughly 70%. During the next traffic spike, capped collector buffers shed load gracefully instead of OOMing, and no error logs were lost.

**Outcome:** logging cost fell ~70%, the ES cluster stopped red-flagging, queries became field-precise, and the PII compliance risk was eliminated — all from structured logging, sampling, retention tiering, and choosing the right index surface.

**Discussion questions:**
1. Why did unstructured logging plus full-body INFO logs cause both a cost and a compliance problem?
2. How does success-path sampling preserve debuggability while cutting volume, and what must you never sample out?
3. When would you keep ELK for some logs while moving the bulk to Loki, and what do you trade away?

---

**Cross-references:** [observability_metrics_prometheus](../observability_metrics_prometheus/) (metrics for aggregates/alerting; where high-cardinality belongs *not*), [observability_tracing_and_otel](../observability_tracing_and_otel/) (follow a single request; correlate via `trace_id`), [visualization_and_alerting](../visualization_and_alerting/) (Grafana/Kibana dashboards and log-based alerts), [secrets_management](../secrets_management/) (why secrets must never reach logs), [../../backend/observability_and_monitoring](../../backend/observability_and_monitoring) (application logging conventions, MDC, trace_id injection — owned there), [observability_logging — log pipeline in case studies](../case_studies/) (design_log_aggregation_pipeline).
