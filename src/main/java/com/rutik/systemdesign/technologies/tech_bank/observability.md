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

### Atlassian Statuspage
**Short:** Hosted public status page for communicating incidents, maintenance and component health to customers.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

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

### Datadog APM
**Short:** Commercial distributed tracing and APM: cross-service spans, slow-query and N+1 detection, service maps.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/profiling-and-performance @2, observability/metrics-and-monitoring @3

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

### ELK Stack
**Short:** Elasticsearch plus Logstash and Kibana: ships, indexes, searches and dashboards centralized application logs.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, search-retrieval/lexical-and-hybrid-search @3, observability/alerting-and-incident-response @3

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

### Grafana Alloy
**Short:** Grafana's unified OpenTelemetry-compatible collector that scrapes metrics and ships logs, traces and profiles.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, observability/metrics-and-monitoring @2, observability/tracing-apm-and-llm-observability @3

### Grafana Loki
**Short:** Log aggregation store that indexes only labels, not log content, making it cheap compared with ELK.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1

### Grafana Mimir
**Short:** Horizontally scalable, multi-tenant Prometheus remote-write backend for long-term metric storage and global query.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @2

### Grafana OnCall
**Short:** Open-source on-call scheduling, escalation and paging tool integrated with Grafana alerting.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### Grafana Tempo
**Short:** Object-storage-backed distributed trace store with TraceQL and cheap long retention; ingests OTLP.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, data-stores/object-and-file-storage @3

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

### Java Flight Recorder
**Short:** Low-overhead JVM event recorder for continuous production profiling: allocation, locks, GC and I/O events.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1

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

### kube-state-metrics
**Short:** Exporter that turns Kubernetes object state - deployments, pods, replicas - into Prometheus-scrapable metrics.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, platform-delivery/kubernetes-and-orchestration @2

### Langfuse
**Short:** Open-source, self-hostable LLM observability: traces, cost per call, prompt versioning, scores and eval runs.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @2, llm-apps/prompting-context-and-structured-output @2

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

### logstash-logback-encoder
**Short:** Logback encoder that emits structured JSON log events, with ECS-compatible field sets for log shipping.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1

### loguru
**Short:** Opinionated Python logging library with a single sink API, built-in rotation and rich tracebacks.
**Kind:** tech
**Lang:** python
**Roles:** observability/logging @1

### Loki
**Short:** Grafana's log aggregation system: indexes only labels and keeps compressed chunks in object storage, queried with LogQL.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, data-stores/object-and-file-storage @3

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

### Microsoft Teams
**Short:** Chat platform used as the incident war room and notification channel, with bots wiring alerts into threads.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

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

### OpenTelemetry API
**Short:** Vendor-neutral instrumentation API for traces, metrics and logs, bridged to a swappable SDK and exporters.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, apis-frameworks/design-patterns-and-principles @3

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

### OpsGenie
**Short:** Atlassian on-call scheduling and escalation service that turns alerts into pages and tracks acknowledgement.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

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

### PagerDuty
**Short:** On-call scheduling, escalation and incident-response workflow platform that turns alerts into pages.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

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

### Pyrra
**Short:** Generates Prometheus recording rules and multi-window burn-rate alerts from a declarative SLO spec.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, observability/metrics-and-monitoring @3

### python-json-logger
**Short:** Minimal JSON formatter that drops into stdlib logging to emit machine-parseable log lines.
**Kind:** tech
**Lang:** python
**Roles:** observability/logging @1

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

### Spring Boot Actuator
**Short:** Spring Boot module exposing health, metrics, config-refresh and migration-history endpoints over HTTP/JMX.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/dependency-injection-and-config @3, observability/alerting-and-incident-response @3

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

### Statuspage
**Short:** Atlassian hosted public status page for communicating incidents, degradations and maintenance to customers.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

### structlog
**Short:** Structured logging for Python: a processor pipeline producing JSON events, async-safe with ContextVars.
**Kind:** tech
**Lang:** python
**Roles:** observability/logging @1

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

### zipkin-reporter-brave
**Short:** Bridges Brave-instrumented spans to a Zipkin collector, reporting them asynchronously over HTTP or UDP.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1
