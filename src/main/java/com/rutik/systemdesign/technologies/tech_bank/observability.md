# Observability — technology bank

<!-- tech-bank tier: observability -->

The 281 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Observability** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### @Endpoint
**Short:** Spring Boot Actuator annotation for defining a custom management endpoint exposed over HTTP or JMX.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/web-framework-and-http-client @3

### @Observed
**Short:** Micrometer annotation that wraps a method in an Observation, emitting a span and timer without manual code.
**Kind:** api
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, apis-frameworks/aop-middleware-and-scheduling @3

### Agent Observability
**Short:** Enterprise APM-style tracing of agent runs, auto-instrumented and correlated with infrastructure signals.
**Kind:** concept
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

### agents.tracing.processors
**Short:** OpenAI Agents SDK module for plugging in custom trace exporters to ship agent spans to Datadog, Honeycomb etc.
**Kind:** api
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, llm-apps/agent-framework @3

### aiodebug
**Short:** Toolkit that logs asyncio callbacks slower than a threshold and traces Future creation to find event-loop stalls.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

### Alertmanager
**Short:** Prometheus companion that groups, dedupes, silences, inhibits and routes alerts to on-call destinations.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

Prometheus decides when an alert fires; this decides what a human is told. Grouping collapses the two hundred alerts one outage produces into a single notification, inhibition suppresses the downstream symptoms while a parent alert is firing, silences mute known noise for a window, and a routing tree matches alert labels to receivers such as a pager, a chat channel or a webhook, with repeat intervals and escalation built in.

Reach for it as soon as you have alerting rules at all, because ungrouped alerts are how an on-call rotation learns to ignore its pager. It holds only alert state rather than your metrics, so it is cheap to run, but run several instances gossiping to each other: they deduplicate between themselves, and a single instance is a single point of failure for every notification you depend on.

### AMD uProf
**Short:** AMD's micro-architecture profiler for Ryzen/EPYC: hardware counters, cache and pipeline analysis.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### Arize Phoenix
**Short:** Open-source OTel-based tracing and eval app for LLM/RAG pipelines: span exploration, judge metrics, embedding drift.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @2, ml-lifecycle/drift-and-production-monitoring @2, search-retrieval/rag-and-document-processing @3

It runs where your code runs, in a notebook or a container, and collects OpenTelemetry spans emitted by instrumentation for the common LLM frameworks and raw SDK calls, so one request shows up as a tree of retrieval, reranking and generation steps with the actual prompts and documents attached. On top of the collected traces it runs LLM-judge evaluations and projects embeddings into two dimensions, which is how clusters of failing queries and drift away from your evaluation set become visible rather than inferred.

Reach for it when a RAG answer is wrong and the first question is whether retrieval or generation failed, since the span tree answers that directly. Being local and open source makes it easy to put in front of an unshippable prototype; a hosted platform is the alternative when you need retention, alerting and access control over the same data.

### Arthas
**Short:** Alibaba's live JVM diagnostic console: watch method arguments and return values, trace hot paths, decompile in place.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/runtime-internals-and-types @3

### asgi-correlation-id
**Short:** ASGI middleware that generates or propagates a request correlation ID and injects it into every structlog line.
**Kind:** tech
**Lang:** python
**Roles:** observability/logging @1, apis-frameworks/aop-middleware-and-scheduling @2, observability/tracing-apm-and-llm-observability @3

### async-profiler
**Short:** Low-overhead JVM CPU, allocation, lock and wall-clock profiler that emits flame graphs safely in production.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1

Most JVM samplers can only capture a stack at a safepoint, which biases the result toward whatever code happens to poll one; async-profiler samples using the JVM's asynchronous call-trace interface driven by performance counters, so the stacks it collects are where the CPU genuinely was, including JIT-compiled and native frames.

The mode is the decision. CPU mode finds hot computation, allocation mode attributes allocation pressure by sampling TLAB events, lock mode finds contention, and wall-clock mode is the one to use when the application is slow but idle, because a thread blocked on I/O never appears in a CPU profile at all. Overhead is low enough to attach to a production process for a minute, and the output is a flame graph or a JFR recording you read rather than a table of totals.

### Atlassian Statuspage
**Short:** Hosted public status page for communicating incidents, maintenance and component health to customers.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

You define components such as API, dashboard and background jobs, then post incident updates with a severity and an impacted-component list; subscribers get email, SMS, Slack or webhook notifications and history stays public afterwards. It runs outside your own infrastructure on purpose, so it is still reachable during the outage it is describing.

Reach for it to own the customer-facing half of an incident and take that load off support. It detects nothing on its own -- monitoring and alerting stay in Prometheus, Grafana or your paging tool, and someone still has to write the updates.

### Audit log shippers
**Short:** Category of agents that forward tool-call and audit logs from an application into a SIEM for security review.
**Kind:** concept
**Lang:** *
**Roles:** observability/logging @1, security/supply-chain-and-runtime-security @2

### auto_explain
**Short:** PostgreSQL extension that automatically logs the EXPLAIN plan of any query exceeding a duration threshold.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, observability/logging @2

Load the module -- via `shared_preload_libraries` for the whole cluster, or `LOAD` in one session -- and set `auto_explain.log_min_duration`; any statement exceeding that duration has its execution plan written to the PostgreSQL log automatically, including statements inside functions when nested logging is on. That closes the gap plain `EXPLAIN` leaves: you get the plan for the slow execution that actually happened, with the real parameter values and the real data distribution, instead of one you reconstructed afterwards with different parameters and a warm cache. `auto_explain.log_analyze` adds actual row counts and timings, which is what exposes a bad estimate, but it instruments execution and can cost real overhead, so pair it with timing disabled or a sample rate on a busy system. Reach for it when queries are slow only sometimes; keep the threshold high and feed the log to a report tool rather than reading it by hand.

### AWS CloudWatch
**Short:** AWS managed metrics, logs and alarms service with dashboards and metric-based alerting.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/logging @2, observability/alerting-and-incident-response @2

### AWS X-Ray
**Short:** AWS managed distributed tracing service; OTLP-compatible backend for service maps and latency analysis.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

Services emit spans through the OpenTelemetry SDK or the AWS Distro for OpenTelemetry collector; X-Ray stitches them into end-to-end traces by trace id and derives a service map showing which service calls which and where latency and errors concentrate. Sampling is configured centrally, because tracing every request at high throughput costs far more than it teaches.

It is the low-friction option when the system already runs on AWS, since Lambda, API Gateway and the SDKs propagate the trace header for you. Choose a vendor-neutral backend such as Jaeger or Tempo when you are multi-cloud or want the trace store to outlive the platform decision.

### bcc
**Short:** BPF Compiler Collection: eBPF-based Linux tracing tools for low-overhead kernel and application observability.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/io-networking-and-syscalls @2, runtime-systems/memory-processes-and-os @3

### Blackbox exporter
**Short:** Prometheus exporter that probes endpoints over HTTP, TCP, ICMP or DNS to produce black-box availability SLIs.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1

### blackbox_exporter
**Short:** Prometheus exporter that actively probes endpoints over HTTP, TCP, ICMP or DNS to produce synthetic SLO signals.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @2, runtime-systems/io-networking-and-syscalls @3

### Blameless
**Short:** SRE platform for incident response and blameless postmortem authoring with learning analytics across incidents.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### bpftrace
**Short:** eBPF tracing language and CLI for ad-hoc, low-overhead kernel and application probes in production.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2, runtime-systems/io-networking-and-syscalls @3

### browser DevTools
**Short:** The browser's built-in inspector: network, console, performance profiling and live WebSocket/frame inspection.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/io-networking-and-syscalls @2

The Network panel shows every request's timing, headers and payload -- and for a WebSocket, the individual frames, which is how you read live STOMP traffic without any extra tooling. Console runs JavaScript and surfaces errors, Performance records a flame chart of main-thread work, and Application inspects cookies, storage, service workers and cache.

Reach for it as the first stop for any client-side symptom: whether a request was actually sent, what the server really returned, which asset blocks rendering. It is always present and always current with the browser, so there is nothing to install and nothing to keep in sync.

### Burrow
**Short:** LinkedIn's Kafka consumer-lag monitor: per-group lag exported to Prometheus with trend-based health evaluation.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-movement/event-streaming-and-processing @2, observability/alerting-and-incident-response @3

### c x N
**Short:** Not a technology: a table shorthand for per-call cost times call count, i.e. total added latency against a budget.
**Kind:** concept
**Lang:** *
**Roles:** observability/profiling-and-performance @3

### cAdvisor
**Short:** Google's container advisor exporting per-container CPU, memory, filesystem and network metrics for Prometheus.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, platform-delivery/container-and-image @2

### Call stack profiler
**Short:** Profiler view of the live call stack, used to spot deep recursion and runaway frame allocation.
**Kind:** concept
**Lang:** *
**Roles:** observability/profiling-and-performance @1

### callgrind
**Short:** Valgrind tool that simulates caches and records a call graph for instruction-level profiling.
**Kind:** tech
**Lang:** cpp
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### Capacity/forecast tools
**Short:** Umbrella entry for demand-forecasting tooling used in capacity planning against SLO headroom.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, platform-delivery/cloud-platform-and-cost @2

### Cassandra Exporter
**Short:** Sidecar that scrapes Cassandra JMX metrics and exposes them in Prometheus format.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/wide-column @3

### channelz
**Short:** gRPC's built-in introspection service exposing live channel, subchannel and socket state for debugging.
**Kind:** api
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/rpc-graphql-and-streaming @2

### Chrome DevTools Network tab
**Short:** Browser panel showing every request's protocol version, headers, payload and waterfall timing breakdown.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/io-networking-and-syscalls @2, apis-frameworks/web-framework-and-http-client @3

### Cloud log services
**Short:** The managed log sinks of each cloud: CloudWatch Logs, GCP Cloud Logging and Azure Monitor Logs.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, platform-delivery/cloud-platform-and-cost @3

### CloudTrail
**Short:** AWS service recording every API call in the account as an auditable event log for forensics and compliance.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, security/privacy-and-compliance @3, security/supply-chain-and-runtime-security @3

### CloudWatch
**Short:** AWS's native observability service: metrics, log groups, dashboards and alarms across every AWS resource.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/logging @2, observability/alerting-and-incident-response @2

### Cortex
**Short:** Horizontally scalable, multi-tenant long-term storage behind Prometheus remote-write.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @2

### Cost/latency observability
**Short:** Practice of instrumenting per-request cost and latency so the marginal-cost curve is visible before scale bites.
**Kind:** concept
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, platform-delivery/cloud-platform-and-cost @2

### cProfile
**Short:** CPython's built-in deterministic function profiler reporting per-call counts and cumulative time.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1

cProfile instruments every function call and return, so its numbers are exact counts rather than samples: total time is the time spent in a function's own body, cumulative time includes everything it called, and sorting by each answers a different question — who is slow versus who is responsible for the slowness. Write the output to a file and read it with `pstats` or a flame-graph viewer instead of the default text dump, which is unreadable past a few dozen functions.

The instrumentation multiplies runtime and it only profiles the thread that started it, so for production, threaded or async code a sampling profiler is the right instrument. cProfile is for a reproducible local script.

### Custom OTel
**Short:** Hand-rolled OpenTelemetry instrumentation instead of a vendor SDK: full control, any backend, more work.
**Kind:** concept
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @3

### Datadog
**Short:** Commercial SaaS observability platform: metrics, APM traces, logs, profiling, dashboards and alerting.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/tracing-apm-and-llm-observability @1, observability/logging @2, observability/alerting-and-incident-response @2, observability/profiling-and-performance @3

An agent on each host or as a DaemonSet in the cluster collects metrics, traces and logs and ships them to the platform, where they share tags so a slow trace links to the host metrics and to the log lines from the same request. Auto-instrumentation covers the mainstream runtimes with little code change, and the continuous profiler closes the last gap by attributing CPU and memory to specific functions in production.

Reach for it when you would rather buy the whole observability stack than operate Prometheus, a tracing backend and a log store yourself. The consideration that dominates in practice is cost, because billing follows hosts, custom metrics, ingested and indexed log volume and span volume: a high-cardinality tag such as a user id or a request id multiplies custom metrics, and unfiltered debug logging is expensive, so indexing filters and cardinality discipline are part of running it.

### Datadog APM
**Short:** Commercial distributed tracing and APM: cross-service spans, slow-query and N+1 detection, service maps.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/profiling-and-performance @2, observability/metrics-and-monitoring @3

Per-language tracer libraries auto-instrument common frameworks, HTTP clients and database drivers, propagate trace context across service boundaries, and ship spans through the host agent to the SaaS backend, where a request becomes a flame graph, a service map and per-endpoint latency percentiles. Because database spans carry the obfuscated statement, a request issuing three hundred nearly identical queries is visible as an N+1 rather than as an endpoint that is merely slow.

The reason teams pay for it is correlation: a trace links to the logs emitted during it and to the host metrics at that moment, so triage stops being three tools and a timestamp. The thing to manage is cost — billing is per host plus indexed spans, so sampling rules and retention filters are a design decision, not an afterthought.
### Datadog LLM
**Short:** Datadog's LLM observability product: auto-instrumented prompt/response traces correlated with infrastructure APM.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @3

### Datadog LLM Obs
**Short:** Datadog's LLM observability product: prompt/response traces, token cost and quality metrics in existing APM.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

### Datadog Logs
**Short:** Datadog's hosted log ingestion, indexing and search product, correlated with its metrics and traces.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, observability/alerting-and-incident-response @3

### Datadog/New Relic
**Short:** Commercial SaaS APM platforms combining distributed traces, metrics, logs, dashboards and alerting.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, observability/alerting-and-incident-response @3

### datasource-proxy
**Short:** Java DataSource wrapper that logs each SQL statement with bound parameters, timings and query counts.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1, data-access/drivers-and-connection-pooling @2, observability/profiling-and-performance @3

You wrap the real DataSource in a ProxyDataSource with listeners, so every statement passes through your code before it reaches the driver. That gives you the bound parameter values -- Hibernate's `show_sql` prints only `?` -- plus execution time and, most usefully, a count of how many queries one request issued, which is how an N+1 pattern becomes visible instead of merely slow.

Reach for it in development and in tests, where you can assert that an endpoint issues at most N queries and fail the build when a lazy association regresses. Logging every statement is expensive, so leave it off or heavily sampled in production.

### db.currentOp
**Short:** MongoDB command listing in-progress operations, used to find long-running or blocked queries and then kill them.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-access/transactions-and-consistency @3

### DCGM exporter
**Short:** Prometheus exporter that publishes DCGM GPU metrics (utilization, framebuffer, temperature, ECC) for scraping.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, gpu/gpu-profiling-and-debugging @2

### DCGM exporter pairing
**Short:** Running NVIDIA DCGM's Prometheus exporter next to an inference server so GPU health sits beside request metrics.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, gpu/gpu-profiling-and-debugging @2

### DCGM visibility caveat
**Short:** Under MIG, DCGM reports per-slice GPU metrics, so whole-GPU dashboards silently show partial data.
**Kind:** concept
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, gpu/gpu-profiling-and-debugging @2

### depesz EXPLAIN
**Short:** Web tool that reformats a PostgreSQL EXPLAIN plan into a ranked tree so the expensive node is obvious.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

### Eclipse MAT
**Short:** Heap dump analyzer for the JVM: dominator tree, leak suspects, OQL queries over retained memory.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

You feed it an `.hprof` heap dump -- from `jmap`, `jcmd GC.heap_dump`, or `-XX:+HeapDumpOnOutOfMemoryError` -- and it builds the object graph offline, so the analysis costs your machine rather than the sick JVM. The dominator tree is the reason to reach for it: it attributes retained heap, everything that would be freed if an object went away, up to the single object keeping a subtree alive, which turns "the heap is full of byte arrays" into "this one cache field retains 1.4 GB". Leak Suspects produces that report automatically, and OQL lets you query the dump in SQL-like syntax once you know what to look for. Use it for a leak or an OOM post-mortem; for allocation-rate problems a sampling profiler such as async-profiler is the right tool, because a dump is one instant and not a trend.

### ELK Stack
**Short:** Elasticsearch plus Logstash and Kibana: ships, indexes, searches and dashboards centralized application logs.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, search-retrieval/lexical-and-hybrid-search @3, observability/alerting-and-incident-response @3

Beats or Logstash collect and parse log lines into structured JSON documents, Elasticsearch indexes them for full-text search and aggregation, and Kibana provides the query UI, dashboards, and alerting. The payoff is interactive search: an arbitrary substring across terabytes of logs returns in seconds because everything is in an inverted index.

That index is also the bill. Storing logs in Elasticsearch costs several times what the raw text costs, so index lifecycle management, tiering to cheaper nodes, and dropping fields you never query are operational necessities rather than tuning. Reach for it when engineers genuinely search logs ad hoc; if you mostly filter by a few known labels and grep within, Loki or a columnar store like ClickHouse holds the same volume far cheaper.

### EXPLAIN
**Short:** SQL command that prints a query's execution plan, and with ANALYZE its actual row counts, timing and I/O.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2, data-access/orm-and-data-mapping @3

### EXPLAIN ANALYZE
**Short:** SQL command that runs a query and prints the execution plan with real row counts and per-node timing.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2

### EXPLAIN ANALYZE in DBeaver/DataGrip
**Short:** Database IDE feature rendering an EXPLAIN ANALYZE plan as a visual tree with per-node cost and row estimates.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, devtools/version-control-and-workbench @3

### Filebeat
**Short:** Lightweight Elastic log shipper that tails files and forwards lines to Logstash/Elasticsearch.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1

A harvester per file tails it line by line and records the read offset in a local registry, so a restart resumes exactly where it stopped instead of replaying or losing lines; the output is backpressure-aware, slowing the harvesters when Logstash or Elasticsearch cannot keep up rather than dropping data. Modules ship prebuilt parsing pipelines for common sources like nginx, and multiline patterns stitch a Java stack trace back into one event.

It is deliberately dumber and far lighter than Logstash — the design is to run one small Go binary per host or as a Kubernetes DaemonSet and push heavy parsing into Elasticsearch ingest pipelines or a central Logstash. Reach for it when the job is "get these files off this box reliably"; anything requiring substantial enrichment belongs downstream.

### FireHydrant
**Short:** Incident-management platform automating declaration, role assignment, comms channels and retrospectives.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### Fluent Bit
**Short:** Lightweight C log and metrics forwarder; runs as a Kubernetes DaemonSet tailing files and shipping upstream.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1

A small C agent with a footprint measured in megabytes, it tails container and systemd logs, parses them, enriches Kubernetes records with pod and namespace metadata, and forwards to Elasticsearch, Loki, S3, Kafka or an aggregator. Buffering can spill to disk so a downstream outage does not lose lines.

Reach for it as the per-node collector, where Fluentd or Logstash would cost too much memory on every host. Keep expensive parsing, routing and enrichment downstream in an aggregator; the node agent should stay cheap.

### Fluentd
**Short:** Plugin-rich log collector and aggregator that parses, buffers and forwards events to any storage backend.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1

### Flux.metrics
**Short:** Reactor operator publishing per-operator throughput and latency metrics from a Flux into Micrometer.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, runtime-systems/concurrency-and-async @2

### Grafana
**Short:** Dashboard and alerting front-end that queries metrics, logs and traces from Prometheus, Loki, Tempo and friends.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, observability/metrics-and-monitoring @1, observability/tracing-apm-and-llm-observability @3, observability/logging @3

Grafana stores no telemetry of its own: you configure data sources -- Prometheus, Loki, Tempo, Elasticsearch, PostgreSQL, CloudWatch and dozens more -- and each panel issues a query in that source's own language, which is why a single dashboard can put a PromQL latency graph beside a LogQL error count and a trace lookup. Dashboards are JSON and can be provisioned from git, so they become reviewable artifacts rather than clicked-together state, and template variables turn one dashboard into a per-service or per-region view. Grafana Alerting evaluates rules against any data source and routes them through contact points and notification policies, replacing what used to be separate per-source alerting. Reach for it as the single viewing surface over a metrics, logs and traces stack -- and remember a dashboard is only as good as the query behind it, because Grafana will faithfully draw a misleading average.

### Grafana Alloy
**Short:** Grafana's unified OpenTelemetry-compatible collector that scrapes metrics and ships logs, traces and profiles.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, observability/metrics-and-monitoring @2, observability/tracing-apm-and-llm-observability @3

### Grafana Loki
**Short:** Log store that indexes only labels, never log text, keeping compressed chunks in object storage and querying them with LogQL.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, data-stores/object-and-file-storage @3

It stores compressed chunks of log lines in object storage and indexes only the label set that identifies each stream, never the log text. That is why ingestion is cheap and there is no large inverted index to maintain, and it is also the tradeoff: a LogQL query first selects streams by label, then brute-force filters the matching chunks, so a narrow label selector over a short window is fast while a full-text search across everything for a week is a lot of scanning. The label model is Prometheus's, so the same selectors move between metrics and logs in Grafana.

Reach for it when log volume makes a full-text engine expensive and most of your queries already know which service, namespace and level they want. Keep label cardinality low, since a label carrying a request id, a user id or a trace id creates a stream per value and is the standard way people bring Loki down; put those high-cardinality fields in the log line, where the query filters on them after selection.

### Grafana Mimir
**Short:** Horizontally scalable, multi-tenant Prometheus remote-write backend for long-term metric storage and global query.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @2

Mimir accepts Prometheus `remote_write` and stores the series in object storage, splitting the work across components — distributors, ingesters, store gateways, queriers, compactors — so ingest and query scale independently and retention stops being limited by a single Prometheus server's disk. A query frontend splits large queries by time, caches results and shards them across queriers, which is what keeps a year-long range query answerable.

It is natively multi-tenant: every series carries a tenant id with its own limits and its own query isolation, so one team cannot exhaust another's capacity. Reach for it when many Prometheus instances need one global view and long retention. A single team with one Prometheus and short retention needs none of this and should not pay the operational cost.
### Grafana OnCall
**Short:** Open-source on-call scheduling, escalation and paging tool integrated with Grafana alerting.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

The model is schedules plus escalation chains: rotations (with overrides and time zones) determine who is on call, routes match an incoming alert to a chain, and each step of the chain notifies someone and waits — if nobody acknowledges within the configured window it escalates to the next step and eventually to a backup. Alerts arrive from Grafana Alerting, Prometheus Alertmanager or a generic webhook, and notification goes out over push, SMS, phone call or chat.

Reach for it to keep paging in the same stack as your dashboards and alert rules rather than running a separate SaaS for it. As with any paging tool, the configuration that matters is the escalation timeout and the routing: an alert with no owning chain reaches nobody, and a chain with no final backup step ends in silence at 3am.

### Grafana Tempo
**Short:** Object-storage-backed distributed trace store with TraceQL and cheap long retention; ingests OTLP.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, data-stores/object-and-file-storage @3

It deliberately builds no full-text index. Traces are written as blocks to object storage keyed by trace ID, so retention costs roughly what the bucket costs and lookup by ID is immediate; TraceQL then filters spans by attribute by scanning the blocks in the selected time range. That inversion is the whole design: cheap to keep everything, more expensive to search it by arbitrary attribute.

The intended workflow is not to browse traces at all, but to arrive with an ID already: a slow request found in a dashboard via metric exemplars, or a log line whose trace ID links straight through. Pair it with Prometheus and Loki so all three share the same labels, and think twice if attribute-heavy trace search - rather than ID lookup - is your primary access pattern.

### guppy3
**Short:** Python heap analysis toolkit taking a one-shot census of live objects grouped by type and retainer.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### HealthIndicator
**Short:** Spring Boot Actuator interface for contributing a custom check to the aggregated /health endpoint.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @3

### heapy
**Short:** Guppy3's heap analyzer producing a one-shot census of live Python objects grouped by type, for tracking down retention.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### Helicone
**Short:** Proxy-based LLM observability: request logging, cost attribution and prompt management with no code changes.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, llm-apps/llm-gateway-and-routing @2, platform-delivery/cloud-platform-and-cost @3, llm-apps/prompting-context-and-structured-output @3

Integration is a base-URL change: point the OpenAI or Anthropic client at the Helicone endpoint and every request is proxied and logged with its prompt, response, token counts, latency and computed cost, attributed to whatever user or session id you pass in a header. Sitting in the request path also lets it cache identical requests, rate-limit per key and serve prompt versions, none of which needs application code. That is the appeal and the caveat together, because a proxy outage becomes your outage; the asynchronous logging integration avoids that at the cost of the proxy-side features. Reach for it when you want per-feature cost attribution quickly without instrumenting every call site.

### Hibernate generate_statistics
**Short:** Hibernate property that records query counts, cache hit ratios and timings, the usual way to catch N+1 selects.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, data-access/orm-and-data-mapping @2, observability/metrics-and-monitoring @3

### Hibernate Statistics
**Short:** Hibernate's built-in counters for query count, cache hits and load times, used to catch N+1 and cache misses.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, data-access/orm-and-data-mapping @2, observability/metrics-and-monitoring @3

### hikaricp_* metrics
**Short:** Micrometer gauges HikariCP publishes for active, idle, pending and timed-out connections.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, data-access/drivers-and-connection-pooling @2

### Holistic Trace Analysis
**Short:** PyTorch library analyzing profiler traces: kernel breakdowns, host-GPU gaps and straggler analysis across ranks.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, gpu/gpu-profiling-and-debugging @2, model-training/distributed-training @3

### Honeycomb
**Short:** Managed OTLP-compatible tracing backend built for high-cardinality querying of production events.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/alerting-and-incident-response @3

### Hubble
**Short:** Cilium's eBPF network observability layer: per-flow visibility, service maps and dropped-packet reasons.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, runtime-systems/io-networking-and-syscalls @2, platform-delivery/kubernetes-and-orchestration @3

### Incident
**Short:** On-call and incident management tooling: paging schedules, incident channels, timelines and postmortems.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### incident.io
**Short:** SaaS incident management: declare an incident, assign roles, run comms and drive the retrospective from chat.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### INFO memory
**Short:** Redis INFO section reporting used and peak memory, eviction counts and the fragmentation ratio for diagnosis.
**Kind:** api
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, caching/distributed-cache @2

### InfoContributor
**Short:** Spring Boot Actuator interface adding custom entries (build data, git sha) to the /actuator/info payload.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/dependency-injection-and-config @3

### IntegrationGraphServer
**Short:** Spring Integration bean exposing the live message-flow graph (channels, endpoints, links) for monitoring.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/aop-middleware-and-scheduling @3

### Intel VTune
**Short:** Intel's CPU profiler: hotspot, microarchitecture and memory analysis, with ITT markers attributing time to operations.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @3

### Intel VTune Profiler
**Short:** Intel's micro-architecture profiler: memory access patterns, NUMA hot spots, pipeline stalls and false sharing.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### io.micrometer.core.instrument.Timer
**Short:** Micrometer meter recording call latency distributions and counts; the standard way to time a filter or handler.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1

### ITT
**Short:** Intel Instrumentation and Tracing API: emit named task markers so VTune attributes time to your own regions.
**Kind:** api
**Lang:** cpp
**Roles:** observability/profiling-and-performance @1

### Jaeger
**Short:** Open-source distributed tracing backend and UI; ingests OTLP spans and stores them in Cassandra or Elasticsearch.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

Collectors accept OTLP spans, a storage backend holds them — Cassandra or Elasticsearch/OpenSearch for real deployments, an embedded store for a single node — and the query service and UI reassemble the spans of one trace into a waterfall, so you can see which service and which call actually consumed the latency. Comparing a slow trace against a fast one for the same operation is usually faster than reading any dashboard.

Its remote-sampling support lets the backend push per-service sampling rates down to instrumented applications, which is how you keep trace volume affordable without hard-coding a rate into every service and redeploying to change it. Reach for it as the self-hosted trace backend when you already emit OpenTelemetry; plan retention and sampling before rollout, because storage — not the collector — is where the cost and the operational work live.

### Java Flight Recorder
**Short:** Low-overhead JVM event recorder for continuous production profiling: allocation, locks, GC and I/O events.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1

It is built into the JVM, so it is enabled with a flag at launch or attached to a running process with `jcmd`, and it writes typed events - allocation samples, GC phases, monitor contention, thread parks, exceptions, socket and file IO, JIT compilations - into a compact binary recording. Overhead is low enough to leave running continuously in production, which is the point: the recording of the incident already exists when you go looking.

Open the file in JDK Mission Control, or parse it programmatically, and the questions it answers well are "which allocation sites produced this garbage", "which monitor was contended", "where did this thread block". It samples, so it characterizes a window rather than reconstructing one exact request - for that you want distributed tracing - and streaming the events live is available for building always-on collection.

### Java Mission Control
**Short:** Desktop GUI for analyzing JDK Flight Recorder recordings: allocation, GC, locks and hot methods.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1

### java.util.logging
**Short:** The JDK's bundled logging API: no extra dependency, weak ecosystem, usually bridged to SLF4J instead.
**Kind:** api
**Lang:** java
**Roles:** observability/logging @1

### jcmd
**Short:** JVM diagnostic CLI: thread dumps, heap info, VM flags and JFR recording control on a live process.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/runtime-internals-and-types @2

`jcmd <pid> <command>` talks to a running JVM over the attach mechanism, so nothing has to be enabled in advance and nothing restarts. The commands that earn their place in a runbook: `Thread.print` for a deadlock or an exhausted thread pool, `GC.heap_info` and `GC.class_histogram` for what is filling the heap, `VM.flags` and `VM.system_properties` to see the configuration the JVM actually got rather than the one you believe you set, and `JFR.start`/`JFR.dump` to capture a Flight Recorder profile from a process that is misbehaving right now.

It must run as the same user on the same host or in the same container as the target, which is why production images often ship a JDK rather than a JRE. `jcmd` is the single entry point that supersedes the older one-purpose tools such as `jstack` and `jmap`.

### jcmd <pid> VM.native_memory
**Short:** JDK command that prints the JVM's native memory breakdown by category; needs NativeMemoryTracking enabled at startup.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### jcmd <pid> VM.native_memory summary.diff
**Short:** Native Memory Tracking diff report showing growth in JVM-internal native memory; blind to raw JNI malloc.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### jcmd VM.stringtable
**Short:** jcmd diagnostic printing JVM string-table statistics: entry count, bucket count and histogram.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/text-encoding-and-regex @2, runtime-systems/memory-processes-and-os @2

### JConsole
**Short:** JDK JMX client giving a live view of a JVM's heap, threads, GC and MBeans, locally or remotely.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, observability/profiling-and-performance @2

JConsole ships with the JDK and attaches over JMX to a running JVM, local or remote, giving live charts of heap and non-heap usage, thread and class counts, and GC activity, plus an MBean browser for reading attributes and invoking operations on any exposed management bean. Its thread view will detect deadlocks on demand, which is the fastest possible answer to a hung application.

It is the quick look, not the analysis: sampling is coarse and there is no allocation attribution. Use it to confirm a hypothesis in seconds, then reach for a profiler or a heap dump when you need to know which code is responsible.

### jdk.VirtualThreadPinned JFR event
**Short:** JFR event, on by default, that reports where a virtual thread pinned its carrier and for how long.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

### Jeli
**Short:** Incident-analysis platform for authoring postmortems and mining review data for recurring contributing factors.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### JFR
**Short:** Java Flight Recorder: low-overhead, production-safe JVM event recording built into the JDK since 11.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, observability/tracing-apm-and-llm-observability @3

Java Flight Recorder is an event engine inside the JVM: allocation, GC phases, lock contention, exceptions, thread parks, JIT activity and I/O are emitted as structured events into a ring buffer and dumped to a `.jfr` file you open in JDK Mission Control or parse with `jfr print`. Overhead on the default profile is small enough that it is meant to be left on in production, and `jcmd <pid> JFR.start` attaches to a live process without a restart.

Reach for it when a JVM is slow or leaking and you need evidence instead of a guess — which allocation site, which lock, which GC phase. It sees only what the JVM knows, so kernel-level questions such as page faults, syscalls and CPU cache misses still belong to `perf`.

### JFR jdk.VirtualThreadPinned
**Short:** Flight Recorder event fired when a virtual thread is pinned past 20 ms; replaces jdk.tracePinnedThreads.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

### JFR jdk.VirtualThreadPinned event
**Short:** JDK Flight Recorder event firing when a virtual thread stays pinned to its carrier past 20 ms; on by default.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

### Jira
**Short:** Atlassian issue tracker; in ops workflows it is where postmortem action items are filed and chased to done.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### JITWatch
**Short:** GUI that reads HotSpot compilation logs to show what the JIT compiled, inlined, or deoptimized and why.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/runtime-internals-and-types @1

### jmap
**Short:** JDK CLI that attaches to a live JVM to print a heap histogram or write a full heap dump for analysis.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

A live histogram prints a class-by-class count and byte total of reachable objects, which is frequently enough to name a leak in one command, and a heap dump writes the whole live set to a file for offline analysis in a tool like Eclipse MAT. Both force a full GC and pause the application while the heap is walked, so dumping a large heap is a multi-second stall and produces a file roughly the size of the live set — plan for the disk and for the outage.

The modern entry point for both operations is `jcmd`, whose `GC.class_histogram` and `GC.heap_dump` commands do the same work through the supported diagnostic-command interface.

### jmap -clstats <pid>
**Short:** jmap invocation printing per-classloader class counts and bytes, which quantifies a suspected classloader leak.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2, runtime-systems/runtime-internals-and-types @3

### jmap -dump:format=b,file=heap.dmp
**Short:** JDK command that writes a binary heap dump of a live JVM for offline leak analysis in MAT or VisualVM.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### JMH
**Short:** Java Microbenchmark Harness: forks a JVM and runs warmup iterations so microbenchmarks survive JIT effects.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, devtools/testing-and-mocking @2

A benchmark is a `@Benchmark`-annotated method; JMH generates a harness around it, forks a fresh JVM per trial, runs warmup iterations until the JIT has compiled and profiles have settled, then measures -- which is why its numbers so often disagree with a hand-written `System.nanoTime()` loop. The hard problem it solves is the optimiser deleting your work: a JIT that proves a result is unused removes the computation you meant to time, so you return the value or feed it to a `Blackhole`, and take inputs from `@State` objects rather than constants the compiler can fold. Modes cover throughput, average time and sampled percentile distributions, and `@Fork(jvmArgs = ...)` lets you compare GC or flag settings within one run. Reach for it for any micro-scale claim -- collection choice, string handling, a stream versus a loop -- while remembering that a microbenchmark measures a method in isolation and cannot tell you whether that method matters.

### journalctl
**Short:** systemd journal query CLI: kernel ring buffer and unit logs, where OOM kills and driver errors surface.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, runtime-systems/memory-processes-and-os @3

### jstack
**Short:** JDK CLI that dumps JVM thread stacks to diagnose deadlocks, hangs and thread-pool exhaustion.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

`jstack <pid>` attaches to a live JVM and prints every thread's stack with its state — RUNNABLE, BLOCKED on a monitor, WAITING or TIMED_WAITING, parked — plus the locks each thread holds and the one it is waiting for, and it detects and reports Java-level deadlocks outright. `jcmd <pid> Thread.print` produces the same dump and is the tool that is guaranteed present.

The technique is more useful than the command: take three dumps a few seconds apart, and threads sitting at the same frame in all three are the hang, while a pool whose threads are uniformly BLOCKED or waiting on one downstream resource is exhaustion — which is what a request queue backing up actually looks like from inside. Reach for it first on a hung or unresponsive service; it tells you nothing about which method burns CPU, and that is a profiler's job.

### jstack <pid>
**Short:** JDK command that dumps every thread's stack for a running JVM; the first tool for deadlocks and BLOCKED threads.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

### jstat
**Short:** JDK CLI that samples a live JVM's GC, heap generation and class-loading counters without attaching a profiler.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2, observability/metrics-and-monitoring @3

### jstat -gcutil
**Short:** JDK CLI printing live GC utilization per generation and cumulative GC time for a running JVM.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2, runtime-systems/runtime-internals-and-types @3

### Kafka Exporter
**Short:** Exporter that turns Kafka broker/consumer-group state and JMX metrics into Prometheus series for lag dashboards.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-movement/event-streaming-and-processing @2

### Kafka Lag Exporter
**Short:** Exports per-consumer-group Kafka offset lag, in messages and estimated seconds, as Prometheus metrics.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-movement/event-streaming-and-processing @2, observability/alerting-and-incident-response @3

### KCachegrind
**Short:** GUI viewer for callgrind/cachegrind output showing call graphs and instruction-level cache miss attribution.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @3

### Kibana
**Short:** Elastic's UI for searching logs, building dashboards and managing indices and ILM policies.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, observability/logging @2, search-retrieval/lexical-and-hybrid-search @3

Discover searches raw documents with KQL, Lens and dashboards build aggregations visually, the Dev Tools console sends raw Elasticsearch requests, and index management edits mappings and index-lifecycle policies that roll indices from hot to warm to delete. Alerting rules can watch a query and notify when it crosses a threshold.

Reach for it when Elasticsearch is your log and search store. It speaks only to the Elastic backend — an OpenSearch cluster is served by OpenSearch Dashboards, the fork of Kibana, not by Kibana itself — so if you also run Prometheus or Loki, Grafana is the tool that puts all of them on one dashboard.

### kube-state-metrics
**Short:** Exporter that turns Kubernetes object state - deployments, pods, replicas - into Prometheus-scrapable metrics.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, platform-delivery/kubernetes-and-orchestration @2

It watches the API server and exports the declared and observed state of objects - desired versus available replicas, pod phase and container restart counts, job success and failure, cron job schedules, PVC status, and every container's resource requests and limits. That is a different dataset from cAdvisor and kubelet metrics, which report what a container is actually consuming; the useful alerts almost always join the two.

Right-sizing comes from comparing requested CPU and memory against real usage, and the classic reliability alerts - deployment stuck below desired replicas, containers in CrashLoopBackOff, jobs failing repeatedly - come from this exporter alone. It holds no state and performs no aggregation, so it is cheap to run, but series count grows with the number of objects in the cluster, which is why very large clusters shard it.

### Langfuse
**Short:** Open-source, self-hostable LLM observability: traces, cost per call, prompt versioning, scores and eval runs.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @2, llm-apps/prompting-context-and-structured-output @2

Instrument an application -- through its SDKs, an OpenTelemetry exporter, or a drop-in wrapper around an OpenAI-compatible client -- and every call becomes a trace of nested observations carrying the exact prompt, the completion, token counts, latency and computed cost. On top of that sit the pieces a team actually needs: prompt management with versions and labels the app fetches at runtime, datasets and dataset runs for evaluation, LLM-as-judge and custom scores, and human annotation queues for cases a judge cannot settle.

Because the core is open source and self-hostable, it is the default choice when traces contain data that cannot leave your infrastructure. Reach for it once you need to answer two questions a metrics dashboard cannot: why did this particular answer happen, and what did last week cost per feature.

### Langfuse/Helicone
**Short:** LLM observability platforms capturing prompt/response traces, cost and latency, with evaluation on top.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @3, llm-apps/llm-gateway-and-routing @3

### LangSmith
**Short:** LangChain's hosted tracing and evaluation platform for LLM/agent runs: traces, datasets, prompts, annotation.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @1, llm-apps/prompting-context-and-structured-output @2, ml-lifecycle/labeling-and-synthetic-data @3

Every chain, agent step, retriever call, and raw model call becomes a nested run carrying its inputs, outputs, token counts, latency, and cost, so debugging an agent that went wrong is reading the actual step tree instead of guessing from the final answer. Setting the tracing environment variable is enough for LangChain and LangGraph; anything else instruments through the SDK or a decorator.

Around the traces sit the parts that make it an eval platform: datasets curated from real production traces, offline evaluation runs using heuristics or an LLM judge, annotation queues for human review, and prompt versioning. Reach for it when agent behaviour is the thing you cannot see. It is a hosted service by default, so if traces will contain user content, check where that data lands before you switch it on.

### Lightstep
**Short:** Managed OTLP-compatible distributed tracing and APM backend, now ServiceNow Cloud Observability.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

### LIKWID
**Short:** HPC command-line suite reading hardware performance counters for bandwidth, cache and NUMA behaviour.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### line_profiler
**Short:** Line-by-line CPU profiler for Python functions; shows per-line hit counts and time at 3-5x slowdown.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1

Decorate the function with `@profile` and run the script under `kernprof`; the report gives per-line hit counts, total time, and percentage of that function's runtime. That is the granularity `cProfile` cannot reach — `cProfile` tells you which function is hot, this tells you which line inside it, which is what you need when the hot function is fifty lines of your own code rather than a library call.

Instrumented functions run roughly three to five times slower, so the workflow is to find the hot function with a whole-program profiler first and point `line_profiler` at only that one. It reports wall time per line and cannot see inside a C extension, so a line that calls into NumPy shows the total, not the breakdown; memory behaviour needs a separate tool entirely.

### Linear
**Short:** Issue tracker used to carry postmortem action items and engineering work to completion.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, devtools/version-control-and-workbench @3

### Log4j 1.x
**Short:** The original Log4j logging implementation, end-of-life since 2015; migrate to Log4j 2 or Logback rather than using it.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1

### Log4j2
**Short:** Java logging implementation with a plugin architecture and Disruptor-backed async loggers; source of Log4Shell.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1, runtime-systems/concurrency-and-async @3

### Log4j2 JsonTemplateLayout
**Short:** Log4j2 layout that emits structured JSON logs from a template, with built-in ECS, GELF and Logstash formats.
**Kind:** api
**Lang:** java
**Roles:** observability/logging @1

### Logback
**Short:** The SLF4J-native logging implementation and Spring Boot's default backend.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1

### Logback Filter
**Short:** Logback accept/deny/neutral chain evaluated before an event is written, to suppress noise without level changes.
**Kind:** api
**Lang:** java
**Roles:** observability/logging @1, apis-frameworks/design-patterns-and-principles @2

### Logfire
**Short:** Pydantic's OpenTelemetry-based observability platform with first-class tracing of Python and LLM agent runs.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/logging @2, observability/metrics-and-monitoring @3

### logging
**Short:** Python's standard logging module: hierarchical loggers, handlers and formatters configured through dictConfig.
**Kind:** api
**Lang:** python
**Roles:** observability/logging @1

### Logstash
**Short:** Log ingestion pipeline that collects, parses and transforms events before shipping them to Elasticsearch.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, data-movement/batch-and-distributed-compute @3

A Logstash pipeline is three plugin stages: inputs (files, Beats, Kafka, syslog), filters that parse and enrich — `grok` or `dissect` to pull fields out of unstructured lines, `mutate`, `date`, `geoip`, lookups — and outputs, usually Elasticsearch. A persistent queue on disk lets it absorb a downstream outage without dropping events, and dead-letter queues capture documents Elasticsearch rejected instead of losing them silently.

The cost is that it is a JVM process with a substantial memory footprint, and `grok` regular expressions are CPU-hungry enough to become the bottleneck of a logging pipeline. The common architecture reflects this: a lightweight shipper collects on each host and Logstash is reserved for the heavy transformation — or dropped entirely in favour of Elasticsearch ingest pipelines or a lighter collector when the parsing is simple.

### logstash-logback-encoder
**Short:** Logback encoder that emits structured JSON log events, with ECS-compatible field sets for log shipping.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1

Set it as the encoder on a Logback appender and each event becomes one JSON object per line - timestamp, level, logger, thread, message, stack trace, and every MDC entry as its own field - so a log pipeline indexes fields instead of regex-parsing free text. `StructuredArguments` attaches typed key-value fields to a single statement without polluting the message string, and MDC is what carries a request or trace ID onto every line emitted while handling that request.

Configure the field set deliberately rather than accepting defaults. Full stack traces and a large MDC make events big enough to matter at volume, the shortened-throwable converter exists for exactly that, and field names have to match the schema your backend expects - ECS, or your own - or the index that was supposed to make logs queryable does not.

### loguru
**Short:** Opinionated Python logging library with a single sink API, built-in rotation and rich tracebacks.
**Kind:** tech
**Lang:** python
**Roles:** observability/logging @1

### m * T
**Short:** Not a tool: the overhead-multiplier times baseline runtime, i.e. what users actually feel while a profiler is on.
**Kind:** concept
**Lang:** *
**Roles:** observability/profiling-and-performance @1

### management.tracing.sampling.probability
**Short:** Spring Boot property setting the head-based trace sampling rate from 0.0 to 1.0.
**Kind:** api
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1, apis-frameworks/dependency-injection-and-config @3

### massif
**Short:** Valgrind heap profiler charting C-level allocation over time; 10-50x slowdown, so test-time only.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### memory_profiler
**Short:** Python line-by-line memory profiler; useful but slows the traced function several times over.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1

### memray
**Short:** Python memory profiler tracing every allocation including C/C++ extensions, with flame graphs and a live TUI.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### Micrometer
**Short:** Vendor-neutral JVM metrics facade; one instrumentation API exporting to Prometheus, Datadog, CloudWatch and others.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, observability/tracing-apm-and-llm-observability @2, apis-frameworks/design-patterns-and-principles @3

You instrument against one API (`Counter`, `Timer`, `Gauge`, `DistributionSummary` on a `MeterRegistry`) and a registry implementation exports to Prometheus, Datadog, CloudWatch and others, so changing monitoring vendor is a dependency swap rather than a rewrite of every instrumented call site. Spring Boot Actuator wires it in automatically and already ships meters for HTTP requests, JDBC pools, caches, JVM memory and GC, so most of what you need exists before you write a line. The dimension to watch is tag cardinality, because a tag carrying a user id or a raw URL path multiplies time series until the backend falls over, and that is the classic production incident. Micrometer Observation extends the same idea to tracing, so one call can emit both a metric and a span.

### Micrometer CompositeMeterRegistry
**Short:** Micrometer registry that fans every meter out to several backing registries, useful during a backend migration.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/design-patterns-and-principles @2

### Micrometer context-propagation
**Short:** Java library bridging ThreadLocal/MDC context across reactive operators and virtual threads so trace context survives.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1, runtime-systems/concurrency-and-async @2, observability/logging @2

### Micrometer integration metrics
**Short:** Micrometer instrumentation for Spring Integration channels and handlers, exported to Prometheus and friends.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1

### Micrometer MeterRegistry
**Short:** Micrometer's vendor-neutral metrics facade: instrument once with counters/timers, pick the exporter per environment.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/design-patterns-and-principles @3

### Micrometer Observation
**Short:** Micrometer API that records one instrumented operation once and fans it out to both metrics and tracing backends.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, observability/tracing-apm-and-llm-observability @2

### Micrometer registry implementations
**Short:** Per-backend MeterRegistry adapters (Prometheus, Datadog, OTLP) behind one instrumentation API, so you instrument once.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/design-patterns-and-principles @2

### Micrometer Tracing
**Short:** Micrometer's vendor-neutral JVM tracing facade, bridging to OpenTelemetry or Brave for span propagation.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1

### Micrometer virtual thread metrics
**Short:** Micrometer binder exposing jvm.threads.virtual.* pinned-time and scheduler gauges for Loom workloads.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, runtime-systems/concurrency-and-async @2

### micrometer-core
**Short:** Vendor-neutral JVM metrics facade: meters and registries that export to Prometheus, Datadog and others.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, observability/tracing-apm-and-llm-observability @2

Micrometer is to metrics what SLF4J is to logging: you record against a meter registry using counters, gauges, timers and distribution summaries, and a registry implementation exports them to Prometheus, Datadog, CloudWatch or whatever the platform runs, with none of your instrumentation changing. Meters are dimensional — a name plus tags — and that is also the trap, since a tag whose value is a user id or a raw URL path multiplies into one time series per value and will overwhelm the backend.

Spring Boot Actuator wires it up and instruments HTTP, JDBC, caches and the JVM for you. Its companion observation API emits a metric and a trace span from a single instrumentation point, which is how tracing context reaches gRPC and messaging boundaries.

### micrometer-registry-datadog
**Short:** Micrometer registry that pushes JVM and application meters directly to the Datadog metrics intake.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1

### micrometer-registry-prometheus
**Short:** Micrometer registry that renders JVM and app metrics in Prometheus format at /actuator/prometheus.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1

Micrometer is the metrics facade in Spring Boot — counters, timers, gauges and distribution summaries recorded against a vendor-neutral API — and this artifact is the backend that renders them in Prometheus text format. Adding it exposes `/actuator/prometheus`, at which point the JVM, HTTP server, connection pool and cache instrumentation that auto-configuration already registered becomes scrapeable without writing any collection code.

Watch tag cardinality: a tag whose value is a user id or a raw URL path creates a distinct time series per value and is the standard way to overwhelm Prometheus, which is why Boot tags HTTP metrics with the templated route rather than the concrete URI. Percentiles need explicit configuration too — publish histogram buckets if you want a p99 that can be aggregated across instances rather than one computed per instance.

### micrometer-tracing
**Short:** Vendor-neutral tracing facade for Spring apps, bridged at runtime to OpenTelemetry or Brave.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1

### micrometer-tracing-bridge-brave
**Short:** Micrometer Tracing bridge that implements spans with Brave and propagates B3 headers for Zipkin-native tracing.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1

### micrometer-tracing-bridge-otel
**Short:** Spring/Micrometer Tracing bridge that emits OpenTelemetry spans over OTLP to Jaeger, Tempo or a collector.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1

Micrometer Tracing is a facade over tracing implementations the way SLF4J is over logging, and this bridge binds it to OpenTelemetry. With it on the classpath, Spring Boot's instrumented components — `RestClient` and `WebClient` calls, controller handling, `@Scheduled` tasks, messaging listeners, and anything you wrap in an `Observation` — emit OpenTelemetry spans and propagate W3C `traceparent` headers across services; add `opentelemetry-exporter-otlp` and they ship to a Collector, Tempo, or Jaeger.

It also injects trace and span ids into the logging MDC, which is what lets a log line be correlated back to the trace that produced it. Pick exactly one bridge — this or the Brave/Zipkin one — because two tracing implementations on the classpath conflict, and remember that sampling is configured on the Spring side, so a fully sampled service in production is a bill and a load problem, not just noise.

### Microsoft Teams
**Short:** Chat platform used as the incident war room and notification channel, with bots wiring alerts into threads.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

In an incident process the useful property is that a channel is both the coordination surface and the record: declaring an incident opens a dedicated channel, responders work there, and the timestamped transcript becomes the raw material for the postmortem timeline instead of somebody reconstructing it from memory. Alerting tools reach it through incoming webhooks or a bot, so a Grafana or PagerDuty notification lands as a card in the right channel with links back to the dashboard, and a meeting attached to the channel gives you a voice bridge without leaving the tool. The failure mode is routing everything into one channel until people mute it -- keep the urgent page on a pager and leave chat for the informational stream and the coordination. Reach for it when the organisation already runs on Teams; the value comes from the discipline of channel-per-incident, not from the product.

### mongotop
**Short:** MongoDB CLI reporting per-collection time spent on reads and writes; finds the hot collection in a busy instance.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/document @2

### MONITOR
**Short:** Redis command streaming every executed command live; a debugging aid that must never run in production.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/key-value-and-embedded @2

### MySQL EXPLAIN FORMAT=JSON
**Short:** MySQL query-plan output in JSON, exposing cost estimates and filtering percentages the tabular form hides.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2

### MySQL Performance Schema
**Short:** MySQL's built-in instrumentation schema exposing statement digests, waits and lock contention in real time.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2, observability/metrics-and-monitoring @3

### MySQL pt-query-digest
**Short:** Percona tool that aggregates a MySQL slow query log into ranked query fingerprints by total time and rows examined.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

### MySQL sys.statement_analysis
**Short:** MySQL sys-schema view ranking normalized statements by total execution time, rows examined and errors.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

### Netflix Atlas
**Short:** Netflix's in-memory dimensional time-series database and query language for operational metrics at scale.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @2

### New Relic
**Short:** Commercial SaaS observability platform: APM traces, metrics, logs, continuous profiling and alerting in one place.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, observability/logging @2, observability/profiling-and-performance @3, observability/alerting-and-incident-response @3

New Relic ingests traces, metrics, logs and custom events into a single store queried with NRQL, its SQL-like query language, fed by language agents that auto-instrument the application or by an OpenTelemetry pipeline. Because everything lands in one place, a slow page can be followed from browser timing through the distributed trace to the specific database statement, without correlating across products by hand.

Alongside APM it covers real-user and mobile monitoring, synthetic checks, infrastructure, continuous profiling and alerting. It occupies the same slot as Datadog, and the choice usually comes down to pricing shape and agent quality for your stack rather than feature lists; here billing follows ingested data volume plus billable users, so the lever you control is what you send and how long you keep it.
### Nobl9
**Short:** Managed SLO platform pulling SLIs from many observability sources and tracking error budgets and burn rate.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, observability/metrics-and-monitoring @3

### node_exporter
**Short:** Prometheus exporter publishing host-level metrics: CPU, memory, disk, filesystem and network counters.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, runtime-systems/memory-processes-and-os @3

### Observation API
**Short:** Micrometer abstraction where one instrumented block emits metrics, a span and correlated log context together.
**Kind:** api
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2

### OpenAI Traces dashboard
**Short:** OpenAI's hosted trace viewer for Agents SDK runs; tracing is on by default and viewed on the platform site.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

### OpenAI Tracing Dashboard
**Short:** Hosted run viewer for the OpenAI Agents SDK showing each turn, tool call and handoff in a trace.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, llm-apps/agent-framework @3

### OpenLLMetry
**Short:** Open-source OpenTelemetry auto-instrumentation for LLM libraries, emitting standard spans for prompts, tools and agents.
**Kind:** tech
**Lang:** python, js
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @3

It is a set of OpenTelemetry instrumentation packages plus an SDK that initializes them: after one call at startup, requests into model SDKs, vector stores and orchestration frameworks emit spans following the OTel GenAI semantic conventions, carrying model name, token counts, latency, tool calls and — if you enable it — the prompt and completion text. A multi-step agent run therefore appears as a normal trace, with each LLM call and each tool invocation as a span you can see nested and timed.

Because the output is ordinary OTLP, it lands in whatever tracing backend you already run rather than requiring a vendor's dashboard, and it sits alongside your service spans instead of in a parallel universe. Decide prompt capture deliberately: recording full prompts and completions is what makes a trace useful for debugging a bad answer, and it is exactly what a privacy review will ask about.

### OpenMetrics
**Short:** CNCF standardization of the Prometheus text exposition format for scraping counters, gauges, histograms and exemplars.
**Kind:** spec
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1

### OpenSearch Dashboards
**Short:** Visualization and management UI for OpenSearch: log dashboards, index lifecycle management and alerting.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, observability/logging @2, search-retrieval/lexical-and-hybrid-search @3

Day to day it is three surfaces in one app. Discover runs ad-hoc queries against an index pattern and is where an incident actually starts; saved visualizations compose into dashboards for the recurring views; and the operational plugins do the cluster housekeeping that would otherwise be curl against the REST API - index state management moving indices through hot/warm/cold/delete on age or size, alerting built from monitors that run a saved query on a schedule and fire triggers into Slack or PagerDuty, and scheduled snapshots to object storage.

It is the fork of Kibana taken at the 7.10 Apache-licensed point and has diverged since, so Kibana plugins and newer Elastic features do not carry over - the reason to run it is that your cluster is OpenSearch, not a feature comparison. Remember it is only a UI over that cluster and inherits its limits: a dashboard that takes 40 seconds over billions of documents is fixed by index patterns, shard sizing and rollups, never by anything in this app.

### OpenSLO
**Short:** Vendor-neutral YAML specification for declaring service level objectives, error budgets and alert policies as code.
**Kind:** spec
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### OpenTelemetry
**Short:** Vendor-neutral instrumentation SDKs plus a collector that emit traces, metrics and logs to any backend.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, observability/logging @3

One specification, one wire protocol (OTLP), and SDKs plus auto-instrumentation agents for every major language produce traces, metrics, and logs; the Collector then receives them, batches, filters, redacts, samples, and fans them out to Jaeger, Tempo, Prometheus, or a commercial backend. Context propagation through the W3C `traceparent` header is the piece that stitches one request across service, queue, and gateway hops into a single trace.

The reason it became the default is decoupling: instrumentation lives in your code, backend choice lives in Collector configuration, so switching vendors stops being a re-instrumentation project. What you still own is discipline — sampling strategy, and keeping metric label and span attribute cardinality bounded, because unbounded values are what make an observability bill explode.

### OpenTelemetry API
**Short:** Vendor-neutral instrumentation API for traces, metrics and logs, bridged to a swappable SDK and exporters.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, apis-frameworks/design-patterns-and-principles @3

The API ships as a separate artifact from the SDK on purpose: a library instruments against the `Tracer`, `Meter` and `Logger` interfaces, and with no SDK installed those calls resolve to a no-op implementation -- so instrumenting a shared library costs its consumers nothing and forces no vendor dependency on them. The application, not the library, chooses the SDK, the sampler and the exporters, which is what lets an organisation change observability backends without touching instrumented code. Context propagation is part of the API surface: the active span lives in a context attached to the current thread or coroutine and is injected into outbound request headers, which is how one trace survives a service boundary. Reach for it in any code other teams consume; inside an application you will pull in the SDK anyway, and most ecosystems give you HTTP, database and messaging spans automatically through instrumentation agents rather than hand-written calls.

### OpenTelemetry Collector
**Short:** Vendor-neutral agent/gateway that receives, batches, samples and exports traces, metrics and logs over OTLP.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, observability/logging @2

### OpenTelemetry for LLM Apps
**Short:** OpenTelemetry's GenAI semantic conventions, giving prompts, tool calls and agent hops standard span attributes.
**Kind:** spec
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

### OpenTelemetry Logs
**Short:** The OpenTelemetry log data model and OTLP transport, correlating log records with trace and span ids.
**Kind:** spec
**Lang:** *
**Roles:** observability/logging @1, observability/tracing-apm-and-llm-observability @2

### OpenTelemetry SDK/API
**Short:** Vendor-neutral instrumentation SDK generating spans, metrics and logs, auto or manual, exported over OTLP.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, observability/logging @2

### opentelemetry-exporter-otlp
**Short:** OpenTelemetry exporter shipping spans and metrics over OTLP to Jaeger, Tempo, Honeycomb or Datadog.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2

### opentelemetry-instrumentation-fastapi
**Short:** OpenTelemetry instrumentation that emits HTTP server spans for FastAPI with no code changes.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1

### opentelemetry-sdk
**Short:** Python OpenTelemetry SDK implementing the tracing/metrics API - samplers, processors and OTLP exporters.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @3

`opentelemetry-api` is what libraries import; this package is the implementation an application installs and configures, giving you a `TracerProvider` with samplers, span processors (batched in production) and OTLP exporters aimed at a collector or a backend. Because the wire format is OTLP and context propagates as a W3C `traceparent` header, a trace crosses service and language boundaries and the backend stays swappable without touching instrumentation. In practice you rarely construct spans by hand, since the auto-instrumentation packages cover FastAPI, requests, SQLAlchemy and the rest, and manual spans are for your own business logic. Choose the sampler deliberately: recording every request is fine at low volume and ruinous at high.

### OpsGenie
**Short:** Atlassian on-call scheduling and escalation service that turns alerts into pages and tracks acknowledgement.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

OpsGenie takes an alert and figures out who should actually be woken. You define schedules and rotations per team, then escalation policies — page the primary, and if nobody acknowledges within five minutes page the secondary, then the manager — and it delivers through push, SMS and phone calls until someone acknowledges or the policy runs out. Routing and deduplication rules collapse a flapping check into one alert instead of ten pages.

Reach for it, or an equivalent paging service, as soon as on-call involves more than one person and an email filter. Two things decide whether it works: alerts must carry enough context to act on, and every page must be actionable, because a rotation that learns to ignore the phone is worse than no paging at all.
### OTLP
**Short:** OpenTelemetry's vendor-neutral wire protocol over gRPC or HTTP carrying traces, metrics and logs.
**Kind:** spec
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, observability/logging @2, apis-frameworks/data-formats-and-api-contracts @3

### OtlpMeterRegistry
**Short:** Micrometer registry exporting meters over OTLP, so instrumentation written once survives a change of monitoring vendor.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/design-patterns-and-principles @3

### P6Spy
**Short:** JDBC driver proxy that logs every SQL statement with bound parameters and timings; debugs ORM-generated SQL.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1, data-access/drivers-and-connection-pooling @2, observability/profiling-and-performance @2

P6Spy sits between the application and the real JDBC driver, so every statement passes through it and is logged with the bound parameters substituted in — the difference between a template full of question marks that you cannot run and the actual SQL you can paste into a console. It also records execution time per statement, which makes it the fastest way to expose an ORM's N+1: the log shows the same query repeated once per row of the parent result.

Enable it in development and test, and add a stack-trace formatter so each statement names the repository method that emitted it. Leaving it on in production costs throughput and fills the log with parameter values you may not be permitted to store.

### PagerDuty
**Short:** On-call scheduling, escalation and incident-response workflow platform that turns alerts into pages.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

Monitoring systems send events to a service; an escalation policy decides who gets paged, and who gets paged next when nobody acknowledges; schedules with rotations and overrides define who is on call right now; and notification rules pick push, SMS, or a phone call per person. Deduplication and event rules collapse a flapping alert into one incident rather than a hundred pages, and each incident carries a timeline, responders, and the notes a postmortem is written from.

Reach for it when an alert has to reliably wake a specific human and escalate if it does not. The value is mostly the discipline it forces: page only on symptoms that need action now, route everything else to a ticket or dashboard, and treat page volume itself as a metric — an on-call rotation that is woken for things nobody acts on stops reacting to the one that matters.

### PagerDuty/Opsgenie
**Short:** On-call and incident-response platforms: alert routing, escalation policies, schedules and incident timelines.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### Per-layer profiling
**Short:** Reading per-operator execution timings from an inference runtime, e.g. OpenVINO's InferRequest.get_profiling_info().
**Kind:** concept
**Lang:** *
**Roles:** observability/profiling-and-performance @1, inference/compiler-and-runtime-optimization @3

### Percona Monitoring
**Short:** Percona Monitoring and Management: dashboards and query analytics for MySQL, PostgreSQL and MongoDB fleets.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/profiling-and-performance @2, data-stores/relational @3

### perf
**Short:** Linux sampling profiler over hardware PMU events: cycles, cache misses, branch misses, context switches.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

`perf` samples the CPU's performance monitoring counters, so instead of guessing why code is slow you measure it: `perf stat` reports cycles, instructions, IPC, branch misses and cache misses for a command, and `perf record` plus `perf report` attribute those samples to functions and source lines. The counters are in hardware, so overhead is low enough to run against a production process, and `perf record -p <pid>` attaches to a live one.

It is how a memory-hierarchy claim becomes evidence — the same loop traversed row-major versus column-major shows up directly as a cache-miss rate — and how you tell whether threads are running or fighting over a lock, via context-switch counts. It observes the machine rather than a language runtime, so JVM or Python frames need matching symbol support or a runtime-aware profiler alongside it.

### perf c2c
**Short:** perf subcommand that reports cache-line contention between cores, the way to prove false sharing.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### perf sched
**Short:** Linux perf subcommand recording scheduler events to expose wakeup and run-queue latency per task.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### perf stat -e dTLB-load-misses
**Short:** Linux perf counter measuring data TLB load misses; a high rate says shrink the working set or use huge pages.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### pev2
**Short:** Browser-based PostgreSQL plan visualizer that turns EXPLAIN ANALYZE output into an annotated node tree.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1

### pg_activity
**Short:** top-style terminal dashboard over pg_stat_activity showing live PostgreSQL sessions, waits and blocking queries.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2, observability/metrics-and-monitoring @3

### pg_stat_activity
**Short:** PostgreSQL system view listing live sessions: running query, wait event, blocking PID and transaction age.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-access/transactions-and-consistency @2, data-stores/relational @3

### pg_stat_bgwriter
**Short:** PostgreSQL statistics view exposing checkpoint and background-writer activity; shows checkpoint pressure on writes.
**Kind:** api
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/relational @2, observability/profiling-and-performance @3

### pg_stat_progress_vacuum
**Short:** PostgreSQL view showing live VACUUM progress phase by phase, so you can tell if a big table is nearly done.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2

### pg_stat_statements
**Short:** PostgreSQL extension aggregating per-statement call counts and timings so you can rank the slowest queries.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

This PostgreSQL extension normalizes every executed statement into a fingerprint with the literal values stripped, then accumulates per fingerprint the call count, total and mean execution time, rows returned and shared-buffer hits versus reads. It has to be loaded via `shared_preload_libraries` and enabled with `CREATE EXTENSION`, which means a restart, so it is worth doing before you need it.

It converts "the database is slow" into a ranked list. Order by total execution time rather than mean, because the real cost is usually a fast query executed a million times — the classic N+1 — not the slow report you were already suspicious of. Reset the counters before a measurement window so the numbers describe that window, and remember the view is capped at a configured number of fingerprints, so unparameterized SQL with inlined literals can evict everything useful.
### pg_stat_user_indexes
**Short:** PostgreSQL catalog view of per-index scan counts; the standard way to find indexes nothing ever reads.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

### pg_stat_user_tables
**Short:** PostgreSQL catalog view of per-table activity: dead tuples, autovacuum timing and scan counts for bloat triage.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2

### pg_stat_wal, pg_waldump
**Short:** PostgreSQL WAL introspection: a cumulative stats view plus a decoder attributing write volume to record types.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2, data-access/replication-ha-and-backup @3

### pganalyze
**Short:** SaaS PostgreSQL performance monitoring: query plan history, index advice, wait events and log analysis.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, observability/metrics-and-monitoring @2, data-stores/relational @3

### pgBadger
**Short:** PostgreSQL log analyzer that turns server logs into slow-query and workload reports.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, observability/logging @2

It parses PostgreSQL's own logs and produces a self-contained HTML report: the slowest individual queries, the most time-consuming normalized query patterns with their counts and cumulative duration, hourly distributions, error and lock-wait summaries, checkpoint and temporary-file activity, and connection behaviour. Because it works from logs there is nothing to install on the server and no extension to load, but the report is only as good as the logging configuration -- `log_min_duration_statement`, `log_checkpoints`, `log_lock_waits`, `log_temp_files` and a machine-parseable `log_line_prefix` must be set before the window you want to analyse. Its most useful ranking is by aggregate time rather than worst case: a 20 ms query run two million times outranks a one-off eight-second report, and that is the ordering that tells you where an index is actually worth adding. Reach for it for periodic workload review and after an incident; for continuously available statistics the `pg_stat_statements` extension covers similar ground live.

### pgstatindex
**Short:** pgstattuple function reporting a PostgreSQL index's real page fill and bloat on your own data.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2, data-access/schema-and-migration @3

### pgstattuple
**Short:** PostgreSQL extension that measures real table and index bloat: dead-tuple percentage and page fill factor.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2

`SELECT * FROM pgstattuple('mytable')` scans the relation and returns measured numbers — live and dead tuple counts and their percentages, free space, and for a B-tree `pgstatindex` adds average leaf density. That is a different thing from the estimates in `pg_stat_user_tables`, and it is what turns "the table feels bloated" into a decision about whether a `VACUUM FULL` or `REINDEX CONCURRENTLY` will actually reclaim anything.

The cost is in the word "scans": it reads the whole relation, so on a large table it is heavy I/O and belongs off-peak. `pgstattuple_approx` samples instead, giving a usable estimate for heap tables at a fraction of the cost, which is the right default when you are surveying many tables rather than investigating one.

### PROFILE
**Short:** Neo4j Cypher prefix that runs a query and returns its execution plan with real row counts and db hits.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/graph-db @2

### Profile API
**Short:** Elasticsearch/OpenSearch API returning a per-shard breakdown of where a query's time actually went.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, search-retrieval/lexical-and-hybrid-search @2

### Prometheus
**Short:** Pull-based metrics server and time-series DB with PromQL, recording rules and alert rules feeding Alertmanager.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @2, observability/alerting-and-incident-response @2, ml-lifecycle/drift-and-production-monitoring @3

It scrapes HTTP endpoints exposing a simple text format and stores each unique metric name plus label set as a time series; everything else - dashboards, alert rules, SLO burn-rate calculations, recording rules - is PromQL over those series. Pulling means targets are discovered rather than configured (Kubernetes, Consul, EC2, files), and a target that dies is visibly `up == 0` instead of merely silent; short-lived batch jobs, which have nothing to scrape, need a Pushgateway.

The constraint that shapes every design decision is cardinality. Each distinct label combination is a separate series, so a user ID, request ID or raw URL in a label will exhaust memory - which is why latency is recorded as a histogram with bounded buckets and why recording rules pre-aggregate expensive queries. A server is single-node and stores locally by default; long retention and a global query view come from Thanos, Mimir or Cortex layered on top.

### Prometheus Adapter
**Short:** Serves Prometheus queries through the Kubernetes custom and external metrics APIs so an HPA can scale on app metrics.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, platform-delivery/kubernetes-and-orchestration @2

### Prometheus alerting rules
**Short:** PromQL expressions with a for-duration that fire alerts to Alertmanager when a condition holds long enough.
**Kind:** api
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, observability/metrics-and-monitoring @2

### Prometheus and Grafana
**Short:** The standard metrics pair: Prometheus scrapes and stores series, Grafana dashboards and alerts on them.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @2

### Prometheus Operator
**Short:** Kubernetes operator managing Prometheus via CRDs: ServiceMonitor/PodMonitor scrape config and PrometheusRule alerts.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, platform-delivery/kubernetes-and-orchestration @2, observability/alerting-and-incident-response @3

It installs CRDs that turn monitoring into Kubernetes objects: a Prometheus resource describes the server, a ServiceMonitor or PodMonitor selects targets by label, and a PrometheusRule holds alerting and recording rules. The operator watches those objects, regenerates the scrape configuration and reloads Prometheus, so a team adds monitoring by shipping a manifest alongside their application instead of filing a change to a central config file.

It is the core of the widely used kube-prometheus-stack chart, which bundles it with Grafana, Alertmanager and node exporters. Reach for it whenever Prometheus runs in Kubernetes; hand-managed scrape config in a cluster with churn goes stale immediately.

### prometheus-fastapi-instrumentator
**Short:** ASGI middleware that auto-instruments FastAPI request latency and counts, exposing a /metrics endpoint.
**Kind:** tech
**Lang:** python
**Roles:** observability/metrics-and-monitoring @1

### prometheus_client
**Short:** Official Python Prometheus client: counters, gauges, histograms and a /metrics endpoint for the pull model.
**Kind:** tech
**Lang:** python
**Roles:** observability/metrics-and-monitoring @1

### PrometheusMeterRegistry
**Short:** Micrometer registry implementation that renders recorded meters in Prometheus scrape format.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/design-patterns-and-principles @3

### Promtail
**Short:** Loki's log collection agent: tails files, adds labels and ships lines to a Loki aggregator.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1

Promtail runs on each node, discovers targets the way Prometheus does (Kubernetes service discovery, file globs), tails log files, applies a pipeline of stages to parse and relabel lines, and pushes them to Loki. The labels are what matter: Loki indexes only labels and stores the log body compressed, so a high-cardinality label such as a request id explodes the index while a small set (namespace, app, level) keeps queries fast.

It is the natural collector when Loki is the store and the deployment is already Prometheus-shaped. Grafana now points new deployments at Alloy, which does the same job alongside metrics and traces, so a fresh install collecting more than logs should start there.

### promtool
**Short:** Prometheus CLI that validates config, unit-tests alerting/recording rules and checks metric cardinality before deploy.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @2, devtools/static-analysis-and-linting @3

### pt-duplicate-key-checker
**Short:** Percona tool finding duplicate and redundant MySQL indexes already covered by another index's leading columns.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-access/schema-and-migration @2, data-stores/relational @3

### pt-query-digest
**Short:** Percona tool that aggregates the MySQL slow query log into ranked query fingerprints.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

### Pushgateway
**Short:** Prometheus staging component where short-lived batch jobs push metrics that a scrape would otherwise miss.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-movement/task-queue-and-jobs @3

### py-spy
**Short:** Sampling CPU profiler that attaches to a running Python process at ~1% overhead; py-spy dump gives live stacks.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1

py-spy reads the memory of a running CPython process from the outside and reconstructs its call stacks, so it needs no code change, no import, no restart and no cooperation from the target. That is what makes it a production tool: you attach to a pid that is already misbehaving. `py-spy top` gives a live view of where time is going, `record` writes a flame graph, and `dump` prints the current stack of every thread, which is how you find what a hung process is actually blocked on.

Overhead is around one percent because it samples rather than instruments. Two caveats: by default it shows Python frames only, so time inside a C extension appears as the calling frame until you pass `--native`, and attaching needs ptrace permission, which containers commonly drop — you may need to add SYS_PTRACE or run it in the same pid namespace.
### py-spy record --gil
**Short:** py-spy mode recording only stacks that hold the GIL, showing which code owns the lock in a live process.
**Kind:** api
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

### Pyinstrument
**Short:** Low-overhead Python statistical profiler that understands await boundaries and groups by async call stack.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @3

### pympler
**Short:** Python memory analyser whose asizeof walks references to give the true recursive size of an object graph.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

The standard `sys.getsizeof` reports only an object's own header and slots, so a dict of lists comes back at a few hundred bytes no matter what it holds. Pympler's `asizeof` walks the references instead and returns the true size of the object graph, deduplicating objects it meets more than once. Alongside it, a full object enumeration grouped by type turns a before-and-after snapshot into a list of what accumulated during a request, and a class tracker follows chosen types over time.

Use it to answer where the memory went in a long-running process. It is a one-shot analysis tool, not something to leave enabled, because walking every live object is expensive.

### Pyrra
**Short:** Generates Prometheus recording rules and multi-window burn-rate alerts from a declarative SLO spec.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, observability/metrics-and-monitoring @3

You declare an SLO — the objective, the window, and the Prometheus queries counting good and total events — and Pyrra generates the recording rules that pre-aggregate the ratio plus the multi-window, multi-burn-rate alerting rules the SRE workbook prescribes, along with a UI showing error budget remaining and current burn rate.

Those alert rules are the reason it exists: hand-writing short-and-long-window burn-rate pairs for every SLO is fiddly and easy to get subtly wrong, producing alerts that page on noise or stay silent while the budget drains. It only generates rules, so Prometheus still evaluates them and Alertmanager still routes them.

### python-json-logger
**Short:** Minimal JSON formatter that drops into stdlib logging to emit machine-parseable log lines.
**Kind:** tech
**Lang:** python
**Roles:** observability/logging @1

Attach its formatter to a handler and every record is emitted as a single JSON object; the reserved LogRecord attributes and anything you pass as `extra={...}` become fields, so a collector can index them directly instead of pulling them out of a message with a regex that breaks the next time someone edits the string.

Reach for it whenever logs land in Elasticsearch, Loki or CloudWatch and you want to filter by user id, request id or latency. Add correlation ids through a logging filter so they appear on every line without each call site remembering, and keep plain text only where a human tailing a terminal is the sole consumer.

### PyTorch Profiler
**Short:** PyTorch's built-in profiler correlating Python ops with CUDA kernels to expose host gaps and slow layers.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, gpu/gpu-profiling-and-debugging @2, model-training/deep-learning-framework @3

### reactor-addons
**Short:** Companion modules for Project Reactor, including metrics instrumentation and extra operators for reactive pipelines.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, runtime-systems/concurrency-and-async @2

### redis-benchmark
**Short:** Redis's bundled load generator measuring throughput and latency for chosen commands, pipeline depth and clients.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, devtools/testing-and-mocking @2, caching/distributed-cache @3

### redis-memory-analyzer
**Short:** Scans a Redis instance and reports memory usage by key pattern, type and encoding to find what is actually filling RAM.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, caching/distributed-cache @2, data-stores/key-value-and-embedded @3

### Result metrics
**Short:** The metric series a load or performance test emits so results are analysed alongside normal system signals.
**Kind:** concept
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, devtools/testing-and-mocking @3

### Rootly
**Short:** Incident management platform automating declaration, roles, comms, timelines and retrospectives from chat.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### Runbook tooling
**Short:** The category of tools holding diagnosis and mitigation guides, linked from each alert so the on-call has a first step.
**Kind:** concept
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### SDK
**Short:** The OpenTelemetry API/SDK split: libraries depend only on the stable API while the SDK and exporters stay swappable.
**Kind:** concept
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, apis-frameworks/design-patterns-and-principles @3

### SELECT * FROM pg_stat_activity
**Short:** PostgreSQL catalog view showing every live backend, its state, wait event and current query - the server-side pool view.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-access/drivers-and-connection-pooling @2, observability/metrics-and-monitoring @3

### Sentry AsyncioIntegration
**Short:** Sentry SDK integration capturing unhandled asyncio task exceptions and emitting a span per task.
**Kind:** api
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/logging @2, runtime-systems/concurrency-and-async @3

### sentry-sdk
**Short:** Sentry client that captures unhandled exceptions with stack traces and request context, and groups them into issues.
**Kind:** tech
**Lang:** python
**Roles:** observability/alerting-and-incident-response @1, observability/tracing-apm-and-llm-observability @2, observability/logging @3

### SHOW ENGINE INNODB STATUS
**Short:** InnoDB status output: buffer pool hit rate, redo log usage, lock waits and the last deadlock.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-access/transactions-and-consistency @2, data-stores/relational @3

### SHOW PROCESSLIST
**Short:** MySQL statement listing current connections and the query each is running; used to spot pool leaks and stalls.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-access/drivers-and-connection-pooling @2, data-stores/relational @3

### Slack
**Short:** Team chat used as the incident war room and the channel alert bots and non-urgent notifications land in.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

In an operations context it plays two roles: the destination alerting systems route non-urgent notifications to, and the place an incident is coordinated. The practice that makes the second useful is a dedicated channel per incident, which keeps the discussion out of the general channels and leaves a timestamped record to build the postmortem from, with bots posting alert context, deploy events and runbook links into the same channel.

What it is not is an alerting system. Grouping, deduplication, escalation and the guarantee that someone acknowledged the page belong in Alertmanager and a paging tool; a message in a channel can be muted, missed or scrolled past, so treat chat alone as insufficient for anything that must wake a human.

### Slack/PagerDuty
**Short:** Chat and on-call paging destinations for alerts: notification routing, escalation and incident acknowledgement.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### SLF4J
**Short:** Java logging facade: code against slf4j-api and bind whichever backend (Logback, Log4j2) is on the classpath.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1, apis-frameworks/design-patterns-and-principles @3

SLF4J ships an API and nothing else. Your code calls `LoggerFactory.getLogger(...)` against `slf4j-api`, and at runtime whichever binding is on the classpath — Logback, Log4j 2 through its adapter, java.util.logging — supplies the implementation. That is why every library logs through it: a library must not impose a logging backend on the application that embeds it, and this is the facade that lets the application decide. Bridge jars redirect calls made to older logging APIs into the same pipeline, so a dependency tree with four logging frameworks still produces one stream.

Always log with parameterized messages rather than string concatenation, so argument formatting is skipped entirely when the level is disabled. The classic startup problem is two bindings on the classpath, which SLF4J warns about and then picks one arbitrarily.
### SLF4J API
**Short:** Java logging facade letting a library log while the application picks Logback or Log4j 2 at deploy time.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1, apis-frameworks/design-patterns-and-principles @2

### SLF4J LoggerFactory.getLogger
**Short:** SLF4J's factory returning the cached logger for a name; the standard way every Java class obtains its logger.
**Kind:** api
**Lang:** java
**Roles:** observability/logging @1, apis-frameworks/design-patterns-and-principles @2

### Sloth
**Short:** Generates Prometheus SLO recording rules and multi-window burn-rate alerts from a simple SLO spec.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, observability/metrics-and-monitoring @3

You write a short YAML spec — the service, the objective such as 99.9%, and the Prometheus queries that count total events and error events — and Sloth expands it into the full rule set: recording rules for the error ratio over several windows, SLO metadata metrics, and multi-window multi-burn-rate alerts where a fast burn pages and a slow burn opens a ticket. The generated rules follow the SRE workbook's structure, which is fiddly to write correctly by hand and drifts between services when each team writes their own.

It generates rules and stops there — Prometheus still evaluates them and Alertmanager still routes them — and it pairs with dashboards that read the metrics it defines. Reach for it once you have more than a couple of SLOs, so that error budget and burn-rate mean the same thing across every service.
### spanmetrics connector
**Short:** OpenTelemetry Collector connector that derives RED metrics - request rate, errors, duration - from the span stream.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/tracing-apm-and-llm-observability @2

### Splunk
**Short:** Enterprise log aggregation and search platform with dashboards and alerting over indexed machine data.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, observability/alerting-and-incident-response @2, observability/metrics-and-monitoring @3

It ingests essentially any text stream through forwarders, indexes it by time with a schema applied at search time rather than at write time, and queries with SPL — a piped language whose statistical, correlation, and lookup commands make most log-analytics questions a single line. Alerts, dashboards, and long-retention archives are built on the same searches.

Its reputation follows its pricing, historically tied to daily indexed volume, which is why mature deployments filter, sample, and route aggressively before data ever reaches an indexer. Reach for it in enterprises already invested in it, especially where security teams depend on its correlation searches; a greenfield logging stack usually starts cheaper on Elastic, Loki, or a columnar store.

### Spring Boot Actuator
**Short:** Spring Boot module exposing health, metrics, config-refresh and migration-history endpoints over HTTP/JMX.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/dependency-injection-and-config @3, observability/alerting-and-incident-response @3

Actuator adds management endpoints under `/actuator`: `health` with per-component contributors and liveness and readiness groups that map straight onto Kubernetes probes, `metrics` and `prometheus` backed by the Micrometer registry, `loggers` for changing a log level at runtime without a restart, `env`, `info`, `threaddump` and `heapdump`, plus endpoints contributed by starters such as `flyway` and `liquibase`, which report applied migrations, and `refresh` when Spring Cloud Config is in play.

Only `health` is exposed over HTTP by default; the rest you opt into explicitly, and that default is deliberate. Treat these as privileged surface — `env` prints configuration and `heapdump` hands over everything in memory — so bind them to a separate management port, or put them behind authentication, and never expose them to the internet.
### Spring Boot Actuator /actuator/caches
**Short:** Actuator endpoint listing every registered cache and supporting eviction over HTTP DELETE.
**Kind:** api
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, caching/in-process-cache @2

### spring-boot-actuator
**Short:** Spring Boot module exposing health, metrics, env and other production endpoints over HTTP or JMX.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @3, apis-frameworks/dependency-injection-and-config @3

### spring-boot-actuator-autoconfigure
**Short:** Spring Boot module auto-configuring Actuator endpoints (health, metrics, env, info) from classpath and properties.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/dependency-injection-and-config @2, observability/alerting-and-incident-response @3

### spring-boot-starter-actuator
**Short:** Spring Boot starter exposing health, info, metrics and env endpoints and auto-configuring Micrometer instrumentation.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @3, observability/tracing-apm-and-llm-observability @3

### spring-modulith-observability
**Short:** Spring Modulith module that instruments cross-module calls and events so traces and metrics show the module boundaries.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2

### StatsD
**Short:** Simple UDP line protocol and daemon for pushing counters, gauges and timers from an app to a metrics backend.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1

The protocol is one line per metric -- `page.views:1|c` for a counter, `|g` for a gauge, `|ms` for a timer, with an optional `|@0.1` sample rate -- sent over UDP, so the application fires and forgets: no connection, no blocking, and a dead collector loses metrics instead of stalling the process. A daemon aggregates those lines over a flush interval and forwards summaries to a backend, which is why timers arrive as percentiles the daemon computed rather than as raw observations you can re-slice later. That push model is the mirror image of Prometheus's scrape and suits short-lived jobs and processes nothing can scrape; the costs are UDP's silent loss and aggregation happening before you get to choose the dimensions. Most deployments today run a tagged dialect -- DogStatsD, or the Telegraf and Prometheus statsd exporters -- because the original protocol had no labels at all.

### Statuspage
**Short:** Atlassian hosted public status page for communicating incidents, degradations and maintenance to customers.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

Statuspage is a hosted page where you publish component status and incident updates — investigating, identified, monitoring, resolved — and schedule maintenance windows, while subscribers receive email, SMS, Slack or webhook notifications and the page keeps a public uptime history. It exists so that during an outage your customers and your support queue learn what is happening from one authoritative place instead of from your engineers.

Two operational rules matter more than the tool. Host it on infrastructure independent of the product it reports on, since a status page that shares your failure domain goes down exactly when it is needed. And decide the update cadence and who writes updates before an incident, because during one nobody has spare attention to negotiate it.
### structlog
**Short:** Structured logging for Python: a processor pipeline producing JSON events, async-safe with ContextVars.
**Kind:** tech
**Lang:** python
**Roles:** observability/logging @1

structlog treats a log entry as a dictionary of key-value pairs passed through a configurable chain of processors — add a timestamp, merge context variables, format the exception, drop or rename keys — with a renderer at the end deciding whether the output is coloured key-value text for a terminal or one JSON object per line for a log pipeline. Binding values to a logger returns a new logger carrying that context, and the context-variable integration binds them for the whole task, so every line emitted during a request has the request id without you threading it through the call stack.

Configure it to wrap the standard library's logging so third-party libraries flow through the same processors and land in the same format, rather than producing a second, unparseable stream.

### synthetics
**Short:** Scripted probes exercising a service from outside on a schedule, producing client-side SLIs of user experience.
**Kind:** concept
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @2

### sys.getsizeof
**Short:** Python builtin reporting an object's shallow byte size, used to compare __slots__ against normal instances.
**Kind:** api
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### Telegraf
**Short:** InfluxData's plugin-driven agent gathering metrics from hosts and services into a time-series backend.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @2, data-movement/event-streaming-and-processing @3

### Thanos
**Short:** Prometheus extension giving long-term object-storage retention, global query across clusters, HA dedup and downsampling.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @2, data-stores/object-and-file-storage @3

A sidecar beside each Prometheus uploads its immutable TSDB blocks to object storage; a Querier fans a PromQL query out across sidecars and Store Gateways, deduplicating identical series produced by an HA Prometheus pair; and a Compactor merges blocks in the bucket and builds 5-minute and 1-hour downsampled copies so a query over a year does not read raw samples. A Ruler evaluates recording and alerting rules globally, across clusters.

The net effect is unlimited retention on cheap object storage and one global query view, without changing PromQL, exporters, or how Prometheus scrapes. Reach for it when local retention or per-cluster silos are the limit you have hit; Cortex and Mimir solve the same problem with a push-based multi-tenant architecture instead, which is the fork in the road.

### time.process_time() vs perf_counter
**Short:** Comparing CPU time to wall time in Python; a low ratio is the first sign threads are blocked on the GIL or I/O.
**Kind:** api
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

### timeit
**Short:** Python stdlib micro-benchmark harness that times a small snippet over many repetitions.
**Kind:** api
**Lang:** python
**Roles:** observability/profiling-and-performance @1

### Traceloop
**Short:** LLM observability platform built on OpenLLMetry, tracing prompts, tool calls and cost as OpenTelemetry spans.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

### tracemalloc
**Short:** CPython stdlib module that traces allocation sites per line so memory growth can be attributed to code.
**Kind:** api
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### TurboFilter
**Short:** Logback global filter returning ACCEPT/DENY/NEUTRAL before an event is created, suppressing noise without level edits.
**Kind:** api
**Lang:** java
**Roles:** observability/logging @1, apis-frameworks/design-patterns-and-principles @3

### USE INDEX
**Short:** SQL optimizer hint forcing a chosen index; MySQL supports it natively, PostgreSQL only via an extension.
**Kind:** api
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2

### valgrind
**Short:** Dynamic binary instrumentation suite for memory errors, leaks, races and heap profiling; 10-50x slowdown.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2, devtools/testing-and-mocking @3

### valgrind --tool=cachegrind
**Short:** Valgrind cache simulator reporting L1/LL miss rates per source line, for locality tuning without PMU counters.
**Kind:** tech
**Lang:** cpp
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

### Vector
**Short:** High-performance Rust observability pipeline collecting, transforming with VRL, sampling and routing telemetry.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, observability/metrics-and-monitoring @2

### VictoriaMetrics
**Short:** Prometheus-compatible time-series database aimed at high ingestion rates, long retention and low memory use.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @1

It accepts Prometheus remote-write (plus Influx, Graphite and OTLP line protocols) and answers queries in MetricsQL, a superset of PromQL, so existing dashboards, recording rules and alerts keep working when it replaces or backs a Prometheus. Its storage engine compresses time series aggressively and keeps per-active-series memory low, which is where the reduced disk and RAM footprint comes from at high cardinality.

It ships as a single binary for the simple case and as a cluster of separate insert, storage and select components when ingestion or retention outgrows one machine. Reach for it as the long-term storage and scale answer once a single Prometheus starts struggling with retention or cardinality — the migration is unusually cheap precisely because the query language and ingestion protocol are the ones you already use.

### VirtualThreadSchedulerMXBean
**Short:** Java 24+ JMX bean exposing the virtual-thread scheduler's parallelism, pool size, and mounted and queued thread counts.
**Kind:** api
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2, runtime-systems/runtime-internals-and-types @3

### VisualVM
**Short:** Desktop JVM monitoring and sampling profiler: JMX metrics, CPU/memory sampling, heap dumps; dev-time not production.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @3, observability/metrics-and-monitoring @3

It attaches to a local JVM through the attach API or a remote one over JMX and shows heap, threads, classes and MBeans live; from there you can sample CPU or memory to attribute time and allocations to methods, trigger a heap dump, and walk the dominator tree to find what is retaining a leaking object. Plugins add views such as the GC visualizer and the startup profiler.

Sampling has noticeable overhead and instrumenting profiling far more, and the attach model expects an interactive session, which is why it belongs in development and staging while Java Flight Recorder is what you leave running in production. It ships separately from the JDK now, so it is a download rather than something already on the machine.

### VizTracer
**Short:** Low-overhead Python tracing profiler producing an interactive timeline of function calls and arguments.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1

### W&B Weave
**Short:** Weights & Biases' LLM tracing, prompt-versioning and evaluation product.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @2, ml-lifecycle/experiment-tracking-and-tuning @2

### W3C Trace Context
**Short:** W3C standard defining the traceparent/tracestate HTTP headers that carry trace and span ids across service boundaries.
**Kind:** spec
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, apis-frameworks/data-formats-and-api-contracts @3

### W3C traceparent
**Short:** W3C Trace Context header carrying trace id, span id and flags so a trace continues across service boundaries.
**Kind:** spec
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, apis-frameworks/data-formats-and-api-contracts @3

### Weave
**Short:** Weights & Biases' LLM product: traces every agent call and scores traces against datasets and custom evaluators.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @2

### Weights & Biases Weave
**Short:** W&B's LLM tracing and evaluation layer, sitting beside experiment tracking for prompt and agent runs.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @2, ml-lifecycle/experiment-tracking-and-tuning @2

Decorating a function marks it as an operation, and every call is then recorded with its inputs, outputs, latency, token counts and cost, nested into a call tree that follows the structure of your code rather than a framework's abstractions. On the evaluation side, a dataset plus a set of scorers gives a repeatable run whose results are versioned, so a prompt or model change can be compared against the previous version instead of judged by impression.

Reach for it when you already use Weights and Biases for training runs and want LLM traces and evaluations in the same workspace with the same access control. If you are not in that ecosystem, an OpenTelemetry-based tracer keeps you portable across backends.

### yappi
**Short:** Async-aware Python profiler measuring coroutine wall time as well as CPU time, per thread and per task.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/concurrency-and-async @2

### Zipkin
**Short:** Distributed tracing backend and UI: collects, stores and visualizes spans across services.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

Instrumented services emit spans — one per unit of work, each carrying a trace id, a parent span id, timestamps and tags — to a collector that stores them and reassembles each request as a timeline, so you can see which hop actually consumed the latency instead of guessing from per-service dashboards. The trace id travels between services in propagation headers, so anything that drops them silently splits one trace into disconnected fragments; a message queue or a thread pool that does not carry context across the boundary is the usual culprit.

It is the simpler option beside Jaeger, and instrumentation today is normally written against OpenTelemetry with Zipkin as one possible backend rather than against Zipkin's own client libraries.

### zipkin-reporter-brave
**Short:** Bridges Brave-instrumented spans to a Zipkin collector, reporting them asynchronously over HTTP or UDP.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1
