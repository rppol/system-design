# Prometheus Cardinality and Scale

> Cross-Cutting Primitive — DevOps Case Studies · Difficulty: Advanced

---

## 1. Concept Overview

Cardinality in Prometheus is the number of distinct time series a metric produces. A time series is uniquely identified by its metric name plus the full set of label key-value pairs: `http_requests_total{method="GET", status="200", handler="/api/users"}` is one series, and changing any single label value creates a brand-new, independently-stored series. The total series count for a metric equals the Cartesian product of its label-value cardinalities — a metric with `method` (5 values) × `status` (8 values) × `handler` (40 values) yields 1,600 series before you add a single new dimension.

This multiplicative behavior is the root of the cardinality explosion problem. The moment a label carries an unbounded value — `user_id`, `request_id`, `email`, `session_token`, a raw URL with query params, or a full error message — cardinality grows linearly with traffic and never plateaus. One `request_id` label on a service doing 10,000 requests/second generates 10,000 new series every second, each of which Prometheus must allocate memory for, index, persist to the WAL, and carry through every query.

Prometheus stores each active series in its in-memory head block at roughly 1-3 KB of resident memory (chunk buffer + label index entries + symbol table references). A million active series therefore costs on the order of 1-3 GB of RAM just to hold the head, before query overhead. Cardinality is the single dominant factor in Prometheus memory consumption, query latency, and TSDB stability — far more than scrape interval or retention duration.

This file is the shared reference for cardinality budgeting, measurement, mitigation (recording rules, relabeling, limits), and horizontal scale (Thanos, Mimir, Cortex, federation vs remote_write). It is linked from DevOps case studies that touch metrics pipelines. See also [observability_metrics_prometheus](../../observability_metrics_prometheus/README.md).

---

## 2. Intuition

> **One-line analogy**: A label is a drawer in a filing cabinet — bounded labels add a few drawers, but a `user_id` label tries to give every customer their own cabinet, and the warehouse runs out of floor.

**Mental model**: Every unique combination of label values is a separate, permanently-tracked counter in RAM. Adding a label doesn't add rows — it multiplies them. Think of total series as the volume of a box whose dimensions are the cardinalities of each label; a high-cardinality label stretches one dimension toward infinity.

**Why it matters**: Cardinality explosion is the number-one cause of Prometheus OOM kills and the most common production observability incident. A single bad label shipped in a deploy can take a monitoring server from 800k series to 12M series in minutes, crashing the very system you rely on to see the outage.

**Key insight**: **Labels are for dimensions you aggregate or filter by, never for identifiers you look up.** If a label value is unique per request, per user, or per session, it belongs in a log or a trace exemplar — not in a metric label. Metrics answer "how many / how fast across a group"; logs and traces answer "what happened to this one entity."

---

## 3. Core Principles

1. **Series = metric name × product of label-value cardinalities.** Bound every label's value set explicitly. A label with N possible values multiplies the series count of that metric by N.

2. **Bounded labels only.** Acceptable label values come from a small, enumerable set: HTTP method (~7), status class (5: `2xx`–`5xx`), region (~20), instance (~hundreds). Never `user_id`, `request_id`, `trace_id`, `email`, raw path, or unbounded enum.

3. **Memory is proportional to active series, not samples.** Retention and scrape interval affect disk and ingest rate; cardinality affects RAM. Roughly 1-3 KB head memory per active series. 1M series ≈ 1-3 GB; 10M series ≈ 10-30 GB and instability.

4. **Histograms multiply by bucket count.** A histogram with 12 buckets and 3 other labels at 50 combined combinations produces 50 × (12 + 2) = 700 series (`_bucket` per boundary + `_sum` + `_count`). High-cardinality labels on histograms are the fastest path to explosion.

5. **Measure before you scale.** `count({__name__=~".+"})` gives total series; `topk(20, count by (__name__)({__name__=~".+"}))` finds the worst offenders. Never guess.

6. **Pre-aggregate with recording rules.** If a dashboard queries a high-cardinality expression every 15s, compute it once per evaluation interval into a low-cardinality series and query that instead.

7. **Drop at ingest, not at query.** `metric_relabel_configs` removes labels or whole series before they enter the TSDB. Dropping at query time still pays the storage and memory cost.

8. **Enforce hard limits.** `sample_limit`, `target_limit`, and `label_limit` are circuit breakers that protect the server from a misbehaving target shipping millions of series.

---

## 4. Types / Architectures / Strategies

**Cardinality sources (ranked by danger):**
- **Unbounded identifier labels** — `user_id`, `request_id`, `session_id`. Catastrophic; grows with traffic.
- **High-cardinality enums** — `path` with IDs (`/users/12345`), `customer_name`, `error_message`. Grows with content.
- **Combinatorial fanout** — many moderate labels multiplying together (`pod` × `endpoint` × `status` × `method`).
- **Histogram bucket fanout** — buckets multiply every other label dimension.

**Mitigation strategies:**
- **Relabeling drop/replace** — strip bad labels at scrape time with `metric_relabel_configs`.
- **Recording rules** — pre-aggregate to a smaller series set.
- **Limits** — `sample_limit` / `target_limit` / `label_limit` as guardrails.
- **Path normalization** — collapse `/users/12345` → `/users/:id` in the app instrumentation layer.
- **Exemplars** — attach a `trace_id` as an exemplar (sampled pointer), not a label.

**Scale-out architectures:**
- **Vertical** — bigger box, more RAM. Works to ~3-5M active series per Prometheus.
- **Functional sharding** — one Prometheus per team/service domain.
- **Hashmod sharding** — split scrape targets across N Prometheis by `hashmod` on `__address__`.
- **Federation** — a global Prometheus scrapes pre-aggregated metrics from leaf Prometheis (pull). Good for rollups, bad for raw long-term.
- **remote_write to long-term store** — push every sample to Thanos Receive / Mimir / Cortex for centralized, horizontally-scaled, long-retention storage.
- **Thanos sidecar model** — sidecar uploads 2h TSDB blocks to object storage; Store Gateway + Querier + Compactor serve and downsample.

**Downsampling tiers (Thanos/Mimir).** Raw samples are kept short-term; the Compactor produces 5-minute-resolution blocks for medium-term queries and 1-hour-resolution blocks for long-range dashboards. A query over a year of data hits 1h-downsampled blocks (8,760 points/series) instead of raw 15s data (~2.1M points/series), a ~240x reduction in points scanned. Downsampling is a query-cost optimization orthogonal to cardinality — it reduces points-per-series, not series-count — but it is essential to make high-retention queries tractable.

**Decision order for a cardinality problem:** (1) measure with `count by (__name__)`; (2) if one label is unbounded, drop it via relabeling or move it to an exemplar; (3) if combinatorial fanout, pre-aggregate with recording rules; (4) if histograms dominate, switch to native histograms; (5) only after the above, if real demand still exceeds ~3-5M series, scale out with remote_write to Mimir/Thanos.

---

## 5. Architecture Diagrams

Cardinality multiplication:

```
metric: http_requests_total
  label method  : {GET, POST, PUT, DELETE, PATCH}        = 5
  label status  : {200,301,400,401,403,404,500,503}      = 8
  label handler : 40 routes                              = 40
  ---------------------------------------------------------------
  series so far = 5 × 8 × 40                              = 1,600   (OK)

  + add label user_id (1,000,000 users)
  series = 1,600 × 1,000,000 = 1,600,000,000             (EXPLOSION)
```

Thanos long-term storage topology:

```
   +------------------+   +------------------+   +------------------+
   | Prometheus A     |   | Prometheus B     |   | Prometheus C     |
   |  (2h local TSDB) |   |  (2h local TSDB) |   |  (2h local TSDB) |
   |  + Thanos sidecar|   |  + Thanos sidecar|   |  + Thanos sidecar|
   +--------+---------+   +--------+---------+   +--------+---------+
            | upload 2h blocks      | upload                | upload
            v                       v                       v
        +---------------------------------------------------------+
        |              Object Storage (S3 / GCS)                   |
        |   raw blocks  ->  Compactor  ->  5m + 1h downsampled     |
        +---------------------------------------------------------+
            ^                                       ^
            | (recent, via sidecar)                 | (historical, via Store Gateway)
        +---------------------------------------------------------+
        |                    Thanos Querier                       |
        |   fan-out + dedup across sidecars + Store Gateways      |
        +---------------------------------------------------------+
                                  ^
                                  | PromQL
                            Grafana / Alertmanager
```

TSDB head and WAL write path:

```
scrape (15s) -> append sample -> HEAD block (in-memory, mmap'd chunks)
                                   |  also append to WAL (crash recovery)
                                   v
                          every 2h: HEAD -> persisted block on disk
                                   |
                          compaction merges blocks; old WAL truncated
```

Where an identifier belongs (label vs exemplar vs log):

```
   dimension you AGGREGATE / FILTER by  ->  LABEL    (bounded: method, region)
   identifier you LOOK UP one entity by ->  EXEMPLAR (trace_id on a bucket)
   full detail / free text / payload    ->  LOG / TRACE (request body, stack)
   ---------------------------------------------------------------------------
   wrong:  http_requests_total{user_id="u-91823"}      (series per user)
   right:  http_requests_total{tier="pro"}             (3 values)
           + exemplar {trace_id="abc"} for drill-down
```

Sharding strategies side by side:

```
   functional shard:   [Prom-payments] [Prom-search] [Prom-infra]   (by domain)
   hashmod shard:      target -> hashmod(__address__, 3) -> {0,1,2} (by hash)
   global view:        Thanos Querier / Mimir   <- fan-out + dedup
```

---

## 6. How It Works — Detailed Mechanics

A Prometheus scrape returns text-format exposition. Each unique line is parsed into a series identified by a 64-bit fingerprint hashed from the sorted label set. The TSDB maintains:

- **Head block** — the active 2-hour window, in memory. Holds the open chunk (last ~120 samples) for every active series plus the inverted index (label → postings list of series IDs).
- **WAL (write-ahead log)** — every appended sample and every new series is logged to disk first, so a crash replays into the head on restart.
- **Persisted blocks** — every 2 hours the head is flushed to an immutable on-disk block (`chunks/`, `index`, `meta.json`). Default `--storage.tsdb.min-block-duration=2h`.

Default operational numbers worth memorizing:
- Scrape interval default: **15s** (`global.scrape_interval`).
- Block duration: **2h** head, compacted up to 31d max-block-duration.
- Retention: **15d** default (`--storage.tsdb.retention.time=15d`).
- Memory per active series: **~1-3 KB** resident in the head.

**Measuring cardinality with PromQL:**

```promql
# total active series across the whole server
count({__name__=~".+"})

# series count per metric name, worst 20 offenders
topk(20, count by (__name__)({__name__=~".+"}))

# which label on a metric is exploding cardinality
count(count by (user_id) (http_requests_total))

# ingest rate of new series (churn) over 5m
sum(rate(prometheus_tsdb_head_series_created_total[5m]))

# head series and head memory pressure
prometheus_tsdb_head_series
go_memstats_heap_inuse_bytes{job="prometheus"}
```

**Dropping a bad label at ingest with relabeling:**

```yaml
scrape_configs:
  - job_name: api
    static_configs:
      - targets: ['api:9090']
    metric_relabel_configs:
      # drop the user_id label from every metric (keep the series, lose the dimension)
      - regex: 'user_id'
        action: labeldrop
      # drop an entire metric that is hopeless
      - source_labels: [__name__]
        regex: 'app_debug_per_request_.*'
        action: drop
```

**Normalizing a path label in app instrumentation (Go):**

```go
// BROKEN normalization happens in the app, before the metric is recorded.
var reqDuration = prometheus.NewHistogramVec(
    prometheus.HistogramOpts{
        Name:    "http_request_duration_seconds",
        Buckets: prometheus.DefBuckets, // 11 buckets
    },
    []string{"method", "route"}, // route is the templated path, not raw URL
)

func normalize(rawPath string) string {
    // collapse /users/12345 -> /users/:id so the label set stays bounded
    return routePattern(rawPath) // returns the registered template, e.g. "/users/:id"
}

func observe(method, rawPath string, seconds float64) {
    reqDuration.WithLabelValues(method, normalize(rawPath)).Observe(seconds)
}
```

**Pre-aggregating with a recording rule:**

```yaml
groups:
  - name: api_aggregations
    interval: 30s
    rules:
      # collapse per-pod, per-route detail into a single rate per route+status class
      - record: job:http_requests:rate5m
        expr: |
          sum by (job, route, status_class) (
            rate(http_requests_total[5m])
          )
```

Dashboards then query `job:http_requests:rate5m` (a few hundred series) instead of `rate(http_requests_total[5m])` (potentially millions), turning a 4-second query into a 40-millisecond one.

**Enforcing limits:**

```yaml
scrape_configs:
  - job_name: api
    sample_limit: 50000     # reject the scrape if it exposes > 50k samples
    target_limit: 200       # cap number of targets from SD
    label_limit: 30         # cap labels per series
    label_value_length_limit: 2048
    static_configs:
      - targets: ['api:9090']
```

When `sample_limit` is exceeded, the entire scrape is dropped and `up{job="api"}=0` with `scrape_samples_post_metric_relabeling` flagged — a misbehaving target fails loudly instead of silently OOMing the server.

**TSDB head and WAL internals in numbers.** The head holds an open chunk per series; a chunk is sealed (made immutable, mmap'd) after ~120 samples or 2 hours, whichever comes first. At a 15s scrape, 120 samples is exactly 30 minutes, so each series cycles through ~4 chunks per 2h block. The WAL is segmented into 128 MB files under `wal/`; on restart Prometheus replays the WAL to rebuild the head, and replay time scales with active series — a 5M-series head can take several minutes to replay, which is downtime for alerting. This is why an OOM kill is doubly painful: you lose the unpersisted head data AND pay a long replay before alerting comes back.

**Why high cardinality slows queries.** A PromQL query first resolves label matchers against the inverted index to get a postings list of matching series IDs, then reads and decompresses chunks for each. A query like `sum(rate(http_requests_total[5m]))` over 4M matching series must touch 4M postings entries and decompress 4M chunk sets per evaluation step. Query latency is roughly linear in matched series, so a metric at 4M series turns a sub-second query into a multi-second one, and a dashboard with 12 such panels refreshing every 15s can saturate the query engine and starve rule evaluation.

**Native histograms (Prometheus 2.40+).** Classic histograms store one series per bucket boundary, so 11 buckets means 11 `_bucket` series per label combination. Native (sparse) histograms store the entire distribution in a single series with dynamically-sized exponential buckets, collapsing the bucket fanout from 11+ series to effectively 1. For a metric with 200 label combinations, this is 200 series instead of 200 × 13 = 2,600 — a ~13x reduction. They are the strategic fix for histogram cardinality where buckets, not the other labels, are the dominant multiplier.

---

## 7. Real-World Examples

- **GitLab** publicly documented hitting 1M+ series from a single `path` label that included project IDs; they normalized routes in the Rails instrumentation layer and cut series by ~90%.

- **Grafana Labs** built Mimir specifically because individual Prometheus servers cap out around 3-5M active series. Mimir shards series across ingesters by tenant + series hash and routinely runs at 1 billion+ active series across a cluster.

- **Cloudflare** runs functional sharding — hundreds of Prometheus servers split by datacenter and service, with Thanos providing a global query view. They reported that a single accidental `colo_id × ip` label combination once added tens of millions of series.

- **Uber's M3** was created because Prometheus single-node TSDB could not hold Uber's metric volume; M3DB is a purpose-built distributed TSDB ingesting billions of series.

- **A common SaaS postmortem pattern**: a developer adds `customer_email` to a histogram for "easier debugging." With 200k customers and a 12-bucket histogram, that single change adds 200k × 14 = 2.8M series. The Prometheus head doubles, hits the memory limit, OOM-kills, loses 2 hours of unpersisted head data, and alerting goes blind during the recovery.

- **Kubernetes `kube-state-metrics` + cAdvisor at scale**: a 500-node cluster running 15,000 pods generates millions of series purely from per-pod, per-container labels (`pod`, `container`, `namespace`, `uid`). Teams routinely drop the `id` and `name` cAdvisor labels and the `uid` label via `metric_relabel_configs` to cut this in half, because those labels are unbounded per-container identifiers rarely used in queries.

- **Worked memory estimate**: a service at 2.5M active series, at ~2 KB/series, costs ~5 GB just for the head. Add ~30-40% for query buffers, the symbol table, and Go heap fragmentation, and the realistic resident set is ~7 GB. Sizing a Prometheus pod at a 6 GB cgroup limit for this workload guarantees an OOM the first time a query fans out widely — the lesson is to size for head + query overhead, not head alone.

---

## 8. Tradeoffs

| Approach | Pro | Con | Use when |
|---|---|---|---|
| Vertical scale (bigger RAM) | Simple, no new components | Caps ~3-5M series; single point of failure | Series budget under a few million |
| Recording rules | Cheap, query speedup 10-100x | Loses raw dimension; eval cost | Hot dashboards / repeated queries |
| metric_relabel drop | Eliminates cost at source | Dimension gone permanently | Label is genuinely unbounded |
| Federation (pull) | Simple rollup of aggregates | Not for raw/long-term; scrape limits | Global view of pre-aggregated KPIs |
| remote_write to Mimir/Thanos | Horizontal, long retention, dedup | Operational complexity, object storage cost | >5M series or >30d retention |
| Thanos sidecar + Store | Reuses object storage; downsampling 5m/1h | Query fan-out latency; eventual block upload | Long-term, multi-cluster global query |
| Histograms | Quantiles + aggregatable | Bucket × label fanout | Latency SLIs with bounded labels |
| Native histograms (sparse) | Far fewer series than classic buckets | Newer; tooling maturing | Prometheus 2.40+ with high bucket counts |

---

## 9. When to Use / When NOT to Use

**Use a metric label when:**
- The value comes from a small, enumerable, stable set (method, status class, region, queue name).
- You will aggregate (`sum by`) or filter (`{region="us"}`) by it.
- The cardinality contribution is known and bounded at design time.

**Use recording rules when:**
- A dashboard or alert query is expensive and runs on every refresh/eval.
- You repeatedly compute the same aggregation over high-cardinality raw data.

**Use remote_write / Thanos / Mimir when:**
- Active series exceed ~3-5M, or retention must exceed 30-90 days.
- You need a single global query view across many clusters.
- You need horizontal HA with deduplication.

**Do NOT use a label for:**
- `user_id`, `request_id`, `trace_id`, `session_id`, `email`, `order_id` — these are identifiers; use logs/traces/exemplars.
- Raw URLs with embedded IDs or query strings.
- Free-text error messages or stack traces.
- Timestamps or epoch values.
- Anything whose value set grows with traffic or with your customer count.

**Do NOT reach for distributed storage** when the real problem is a single bad label — fix cardinality first; scaling out a cardinality bug just makes it more expensive.

---

## 10. Common Pitfalls

1. **High-cardinality label on a histogram.** The classic explosion. Buckets multiply every other dimension.

```go
// BROKEN: user_id on a 11-bucket histogram. With 1,000,000 users this is
// 1,000,000 × (11 buckets + _sum + _count) = 13,000,000 series.
var d = prometheus.NewHistogramVec(
    prometheus.HistogramOpts{Name: "req_seconds", Buckets: prometheus.DefBuckets},
    []string{"user_id"},
)
func obs(uid string, s float64) { d.WithLabelValues(uid).Observe(s) }

// FIX: drop the identifier from the label set; attach trace_id as an EXEMPLAR
// instead (sampled pointer, not a series). user_id stays in logs/traces.
var d2 = prometheus.NewHistogramVec(
    prometheus.HistogramOpts{Name: "req_seconds", Buckets: prometheus.DefBuckets},
    []string{"route", "status_class"}, // ~40 × 5 = 200 combos × 13 = 2,600 series
)
func obs2(route, statusClass, traceID string, s float64) {
    d2.WithLabelValues(route, statusClass).(prometheus.ExemplarObserver).
        ObserveWithExemplar(s, prometheus.Labels{"trace_id": traceID})
}
```

2. **Raw path as a label.** `/users/12345` and `/users/12346` are distinct series. Normalize to `/users/:id` at instrumentation time.

3. **Series churn from rolling deploys.** Pod names like `api-7d9f-x2k1` change every deploy; old series stay in the head for the chunk lifetime, doubling apparent cardinality during rollouts. Use stable labels and rely on `up`/`kube_pod` joins.

4. **Dropping labels at query time, not ingest time.** `sum without (user_id) (...)` still required Prometheus to store every `user_id` series. Drop with `metric_relabel_configs`.

5. **No `sample_limit`.** Without a circuit breaker, one bad deploy ships 10M series and OOM-kills the server, losing the unpersisted head and blinding alerting during the incident.

6. **Forgetting `_bucket`/`_sum`/`_count` triple.** Every histogram series count is `(buckets + 2) × other-label-combinations`. Easy to underestimate by 14x.

---

## 11. Technologies & Tools

| Tool | Role | Scale ceiling | Storage model | Notes |
|---|---|---|---|---|
| Prometheus (single) | Scrape + local TSDB | ~3-5M active series | Local disk, 2h blocks | 15s scrape, 15d retention default |
| Thanos | LT storage + global query | Effectively unbounded | Object storage (S3/GCS) | Sidecar, Store, Querier, Compactor; 5m/1h downsampling |
| Grafana Mimir | Horizontal LT storage | 1B+ series clusters | Object storage + ingesters | Tenant sharding; Cortex successor |
| Cortex | Horizontal multi-tenant | Very high | Object storage | Mimir forked from it |
| VictoriaMetrics | High-perf TSDB / remote_write | Very high per node | Local + clustered | Lower RAM/series than Prometheus |
| M3DB (Uber) | Distributed TSDB | Billions | Distributed cluster | Purpose-built for Uber scale |

Diagnostic tooling: `promtool tsdb analyze` (per-block cardinality report), the Prometheus `/tsdb-status` page (top series by metric and by label), and Grafana's built-in cardinality dashboard.

---

## 12. Interview Questions with Answers

**Q: What exactly is a time series in Prometheus, and what makes two series distinct?**
A time series is a stream of timestamped samples identified by its metric name plus its complete set of label key-value pairs. Two series are distinct if they differ in the metric name or in any single label value — `m{a="1"}` and `m{a="2"}` are two separate, independently-stored series. Internally each is a 64-bit fingerprint of the sorted label set, indexed in the head block. This is why adding label values multiplies, not adds, to your series count.

**Q: How do you calculate the cardinality of a metric?**
Multiply the cardinalities of each label's value set together. A metric with `method` (5), `status` (8), and `handler` (40) has 5 × 8 × 40 = 1,600 series. For a histogram, also multiply by `(bucket_count + 2)` to account for `_bucket` per boundary plus `_sum` and `_count`. The danger is that one unbounded label turns this finite product into something proportional to traffic or customer count.

**Q: Why is putting `user_id` on a label catastrophic?**
Because `user_id` is unbounded — it grows with your customer base and never plateaus, so series count grows linearly with users. With 1M users, a metric gains 1M× its base cardinality, and a histogram gains 14M+ series. Each series costs ~1-3 KB of head RAM, so you add gigabytes and eventually OOM the server. Identifiers belong in logs, traces, or exemplars, never in labels.

**Q: How much memory does an active series cost?**
Roughly 1-3 KB of resident memory in the head block — covering the open chunk buffer (~120 recent samples), the inverted index postings, and symbol table references. So 1M active series is on the order of 1-3 GB of RAM, and 10M series is 10-30 GB and approaching instability. Memory scales with active series count, not with retention or scrape frequency.

**Q: How do you measure total cardinality on a running Prometheus?**
Run `count({__name__=~".+"})` for the total series count, and `topk(20, count by (__name__)({__name__=~".+"}))` to find the worst metrics. To pinpoint the offending label, use `count(count by (suspect_label) (metric_name))`. The `/tsdb-status` page and `promtool tsdb analyze` on a block give the same breakdown offline. Always measure before scaling — guessing at cardinality is how you scale out a bug.

**Q: Why are histograms more expensive than counters or gauges?**
A histogram with B buckets produces B+2 series per label combination (one `_bucket` series per boundary, plus `_sum` and `_count`). So a histogram multiplies the cost of every other label by ~14 for the default 11-bucket layout. A counter with the same labels is one series per combination. This is why a high-cardinality label is far more dangerous on a histogram than on a counter — the explosion is amplified 14x.

**Q: What is the difference between `metric_relabel_configs` and `relabel_configs`?**
`relabel_configs` runs before the scrape, on the target's discovered label set, deciding which targets to scrape and how to label them. `metric_relabel_configs` runs after the scrape, on each individual sample, deciding which series to keep and which labels to drop. To kill a high-cardinality label or a runaway metric you use `metric_relabel_configs` with `labeldrop` or `drop`, because that removes the cost before the data ever enters the TSDB.

**Q: How do recording rules help with cardinality?**
They pre-compute an expensive, high-cardinality query into a small low-cardinality series on a fixed evaluation interval, so dashboards query the cheap pre-aggregated result. For example, `sum by (route, status_class) (rate(http_requests_total[5m]))` reduces millions of per-pod series to a few hundred, evaluated once every 30s instead of on every dashboard refresh. They reduce query cost, not storage cost — the raw series still exist unless you also drop them.

**Q: What is `sample_limit` and why use it?**
`sample_limit` rejects an entire scrape if the target exposes more than the configured number of samples, acting as a circuit breaker against a misbehaving target. If a bad deploy starts emitting 10M series, the scrape fails (`up=0`) loudly instead of silently OOM-killing the server. It is one of the most important production guardrails alongside `target_limit` and `label_limit`.

**Q: When do you outgrow a single Prometheus, and what comes next?**
Around 3-5M active series or when retention must exceed 30-90 days, a single Prometheus becomes unstable or insufficient. The next step is remote_write to a horizontally-scaled store — Thanos (sidecar + object storage), Grafana Mimir, Cortex, or VictoriaMetrics — which shard series across ingesters and store blocks in object storage. First, though, verify the series count is real demand and not a cardinality bug, because scaling out a bug just multiplies its cost.

**Q: Federation vs remote_write — when to use each?**
Federation has a global Prometheus pull pre-aggregated metrics from leaf Prometheis; it is good for rolling up a small set of KPIs but is bounded by scrape limits and is not for raw or long-term data. remote_write pushes every sample to a long-term store (Thanos Receive, Mimir, Cortex) for horizontal, long-retention, deduplicated storage. Use federation for lightweight global rollups, remote_write for full-fidelity centralized storage at scale.

**Q: How does the Thanos sidecar model achieve long-term storage?**
The sidecar runs next to each Prometheus, uploads completed 2h TSDB blocks to object storage, and serves recent in-memory data to the Querier. The Store Gateway reads historical blocks from object storage, the Compactor merges and downsamples them (5m and 1h resolutions), and the Querier fans out across sidecars and Store Gateways with deduplication. This decouples retention from local disk and gives a global PromQL view across clusters.

**Q: What is series churn and why does it inflate cardinality during deploys?**
Series churn is the rate at which old series stop receiving samples and new ones appear, measured by `rate(prometheus_tsdb_head_series_created_total[5m])`. Rolling deploys with ephemeral pod names create a wave of new series while old ones linger in the head for their chunk lifetime, so effective head cardinality spikes during rollouts. Mitigate by avoiding ephemeral identifiers in labels and joining against `kube_pod_info` for pod-level context.

**Q: How would you instrument a per-request latency metric without exploding cardinality?**
Use a histogram with only bounded labels — `route` (normalized to templates like `/users/:id`), `method`, and `status_class` — and attach the `trace_id` as an exemplar rather than a label. The exemplar is a sampled pointer stored alongside a bucket, letting you jump from a metric to a specific trace without creating a series per request. This keeps cardinality at a few thousand while preserving per-request drill-down through tracing.

**Q: A deploy just took Prometheus from 800k to 12M series. Walk through your response.**
First confirm and locate the offender with `topk(20, count by (__name__)(...))` and `count(count by (label)(metric))` to find the exploding metric and label. Immediately stop the bleeding with `metric_relabel_configs` `labeldrop`/`drop` on that label or metric and reload, or roll back the deploy. Add a `sample_limit` so the bad target fails loudly next time, then fix the root cause in instrumentation (normalize the path / remove the identifier) and add a CI check or `promtool tsdb analyze` gate to catch it pre-merge.

---

## 13. Best Practices

1. **Budget cardinality at design time.** For every new label, write down its value set size and multiply through. Reject any label whose value set is unbounded or grows with traffic/customers.

2. **Normalize paths in instrumentation.** Always record the route template (`/orders/:id`), never the raw URL. Do it in the HTTP middleware so it is consistent.

3. **Use exemplars for identifiers.** Attach `trace_id` as an exemplar, never a label. This preserves drill-down without per-request series.

4. **Set guardrails on every job.** `sample_limit`, `target_limit`, `label_limit`, and `label_value_length_limit` on each scrape config catch runaway targets before they OOM the server.

5. **Pre-aggregate hot queries with recording rules.** Any expression that runs on a dashboard or alert eval loop should be a recording rule named with the `level:metric:operation` convention.

6. **Drop bad labels at ingest, not query.** `metric_relabel_configs` removes cost; query-time aggregation does not.

7. **Monitor your monitoring.** Alert on `prometheus_tsdb_head_series` growth, churn rate, and `go_memstats_heap_inuse_bytes`. A cardinality alert should fire before an OOM.

8. **Gate cardinality in CI.** Run `promtool tsdb analyze` or scrape the target in a test and assert series-per-metric stays under budget before merge.

9. **Scale out only after fixing bugs.** Confirm series count reflects real demand before reaching for Thanos/Mimir; never scale out a cardinality bug.

---

## 14. Case Study

**Scenario**: A payments platform runs a single Prometheus scraping 120 API pods at 15s intervals, sitting comfortably at 900k active series and 6 GB RAM. A new feature ships a latency histogram intended to help debug slow checkout. Within 8 minutes Prometheus RAM climbs from 6 GB to 22 GB, hits the cgroup limit, gets OOM-killed, loses ~90 minutes of unpersisted head data, and on restart immediately starts climbing again. Alerting is blind during the incident and a real downstream outage goes unnoticed for 11 minutes.

Investigation with `topk(10, count by (__name__)({__name__=~".+"}))` shows `checkout_latency_seconds_bucket` at 9.8M series. Drilling in with `count(count by (merchant_id) (checkout_latency_seconds_count))` reveals 70,000 distinct `merchant_id` values on an 11-bucket histogram: 70,000 × (11 + 2) = 910,000 base, further multiplied by `region` (8) and `method` (2) → ~14.6M potential series.

The instrumentation looked like this:

```go
// BROKEN: merchant_id is unbounded (70k merchants) on an 11-bucket histogram,
// multiplied by region and method. ~14.6M series; OOMs a 16 GB Prometheus.
var checkoutLatency = prometheus.NewHistogramVec(
    prometheus.HistogramOpts{
        Name:    "checkout_latency_seconds",
        Buckets: prometheus.DefBuckets, // 11 buckets
    },
    []string{"merchant_id", "region", "method"},
)

func record(merchantID, region, method string, sec float64) {
    checkoutLatency.WithLabelValues(merchantID, region, method).Observe(sec)
}
```

The fix removes the unbounded identifier from the label set, keeps only bounded dimensions, and attaches the `merchant_id` and `trace_id` as exemplars for drill-down. A `merchant_tier` label (3 values: `free`/`pro`/`enterprise`) replaces raw `merchant_id` for the aggregation use case:

```go
// FIX: bounded labels only (region 8 × method 2 × merchant_tier 3 = 48 combos
// × 13 = 624 series). merchant_id/trace_id move to exemplars.
var checkoutLatency = prometheus.NewHistogramVec(
    prometheus.HistogramOpts{
        Name:    "checkout_latency_seconds",
        Buckets: prometheus.DefBuckets,
    },
    []string{"region", "method", "merchant_tier"},
)

func record(region, method, tier, merchantID, traceID string, sec float64) {
    checkoutLatency.
        WithLabelValues(region, method, tier).
        (prometheus.ExemplarObserver).
        ObserveWithExemplar(sec, prometheus.Labels{
            "merchant_id": merchantID,
            "trace_id":    traceID,
        })
}
```

A `sample_limit: 200000` was added to the scrape config as a circuit breaker, plus an alert on `rate(prometheus_tsdb_head_series_created_total[5m]) > 5000` to catch future churn spikes. A `promtool tsdb analyze` check was added to CI asserting no metric exceeds 50k series. Post-fix, `checkout_latency_seconds_bucket` sits at 624 series, total server cardinality returns to ~900k, RAM stabilizes at 6 GB, and engineers still jump from a slow-bucket exemplar straight to the offending merchant's trace.

**Lesson**: The instinct was to scale out to Thanos to "handle the load." The real fix was a single label removed at instrumentation time. Always diagnose cardinality before scaling — scaling out a cardinality bug just makes it more expensive to crash. See [observability_metrics_prometheus](../../observability_metrics_prometheus/README.md) for the broader metrics pipeline context.
