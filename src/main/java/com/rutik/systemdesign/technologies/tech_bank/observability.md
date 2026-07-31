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

The practice treats one agent run as a distributed trace: each planning step, tool invocation, retrieval and model call becomes a span carrying inputs, outputs, token counts and latency, nested to mirror the control flow. Because the spans are emitted through the same pipeline as ordinary service spans, a slow agent turn can be followed into the database query or downstream API behind it, and framework auto-instrumentation does most of the emitting.

Reach for it once agents run unattended and behaviour stops being deterministic, because the questions become why it looped, which tool returned garbage and what the turn cost. The real price is data: capturing full prompts and completions is what makes a trace useful and exactly what a privacy review objects to, on top of span volume. Plain request logging suffices for a single-shot prompt with no tools.

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

It wraps the running event loop so that every callback's execution time is measured, logging any that exceed your threshold together with the coroutine that owned it, and it can record where each Future was created so a task pending forever is traced back to its origin. The problem is structural: one blocking call inside a coroutine stalls every other task on the loop, and asyncio never says which one.

Reach for it when an async service shows latency no CPU profile explains, which is the signature of blocking I/O or a CPU-bound call landing on the loop. It names the slow callback rather than the line inside it, and asyncio's own debug mode covers similar ground more coarsely, so a sampling profiler such as Pyinstrument or yappi is the usual follow-up once you know which coroutine to inspect.

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

It drives the performance monitoring counters in Zen cores much as VTune does on Intel, sampling cycles, instructions, branch mispredictions and cache and memory events and attributing them to functions and source lines, with instruction-based sampling for more precise attribution. Additional views cover per-CCX and NUMA memory behaviour, thread concurrency, and power and thermal telemetry, driven either from the GUI or from a command-line collector suited to scripting.

Reach for it when tuning on EPYC or Ryzen and the question is micro-architectural: whether a loop is bound by memory bandwidth, by cache misses or by cross-die traffic. The limit is the obvious one, since it reads AMD counters and is not the tool on Intel hardware, where VTune plays the same role. Plain `perf` remains the portable answer when cycles, misses and a flame graph are all you need.

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

It attaches to a running JVM and opens an interactive shell whose commands instrument bytecode on the fly: `watch` prints a method's arguments, return value and exception as it is called, `trace` shows the call tree beneath it with per-node timing, `stack` shows who called it, and `jad` decompiles the loaded class so you can confirm what is actually running rather than what the repository says.

Reach for it when a bug reproduces only in production and adding a log line means a full deploy cycle. The cost is that instrumentation is real, so a `watch` on a hot method slows it and enhanced classes stay enhanced until reset, and the shell is a privileged remote-code surface that should not stay attached. For steady-state profiling rather than targeted interrogation, async-profiler or Flight Recorder are the right instruments.

### asgi-correlation-id
**Short:** ASGI middleware that generates or propagates a request correlation ID and injects it into every structlog line.
**Kind:** tech
**Lang:** python
**Roles:** observability/logging @1, apis-frameworks/aop-middleware-and-scheduling @2, observability/tracing-apm-and-llm-observability @3

Add the middleware and every request either adopts an incoming header or is given a fresh UUID; the value lives in a `ContextVar`, so it survives `await` boundaries and is readable anywhere in the request without being threaded through call signatures, and it is echoed back on the response. A bundled logging filter injects the id into every record, with helpers for carrying it into Celery tasks and outbound calls.

Reach for it as the cheapest way to make a FastAPI or Starlette service's logs joinable, since one search by id reconstructs a single request out of a hundred interleaved lines. It correlates logs and nothing else: timing, span nesting and cross-service topology remain OpenTelemetry's job, and if tracing is already in place, use the trace id as the correlation id rather than maintaining a second identifier.

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

The pattern is a collector such as Fluent Bit, Vector or Filebeat reading an append-only audit stream and delivering it to a SIEM, normally normalising each event into the schema the security team's detections expect. What separates it from ordinary log shipping is the guarantees around it: disk buffering so nothing is dropped during a downstream outage, and a delivery path the application being audited cannot rewrite or disable.

Reach for it when somebody must be able to answer, months later, which principal invoked which tool with which arguments, which is what both compliance regimes and incident forensics demand. The costs are retention volume and the discipline of keeping secrets and personal data out of the events in the first place. Ordinary application logging is not a substitute, being sampled, mutable and rotated away long before anyone asks.

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

Every AWS service publishes metrics into it with no setup, at one-minute granularity or one-second with detailed monitoring, while the agent adds the memory and disk figures the hypervisor cannot see and custom metrics arrive through the API or the embedded metric format. Logs land in log groups queried with Logs Insights, and alarms evaluate a metric or metric-math expression over several periods to notify SNS, scale a group or invoke a function.

Reach for it as the default on AWS, because it already collects most of what you need and is the only thing that sees inside the managed services. Billing follows custom metrics, alarms, ingested gigabytes and bytes scanned per query, so a high-cardinality dimension and unfiltered debug logging are where the bill comes from. Prometheus with Grafana is the alternative when PromQL or cross-cloud portability matters more.

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

It is a toolkit and a library at once: a collection of ready-made tracing programs plus Python and C++ bindings that compile a restricted C program with Clang, load it into the kernel as eBPF, attach it to a kprobe, uprobe, tracepoint or USDT probe, and read results back through maps. Because aggregation happens kernel-side, only summaries cross into user space, which is what keeps overhead acceptable on a production host.

Most of the value is the shipped tools, since `execsnoop`, `opensnoop`, `biolatency`, `tcpconnect` and `runqlat` each answer a specific question in one command with no change to the application. The cost is a runtime dependency on kernel headers and an LLVM toolchain, which is why bpftrace, a one-line language over the same machinery, is the usual choice for ad-hoc work and libbpf with CO-RE for tools you ship.

### blackbox_exporter
**Short:** Prometheus exporter that actively probes endpoints over HTTP, TCP, ICMP or DNS to produce synthetic SLO signals.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @2, runtime-systems/io-networking-and-syscalls @3

Configuration is a set of named modules in the exporter's own YAML giving protocol, timeout and expected response, while the target is supplied per scrape, so one exporter serves hundreds of endpoints. The distinction it embodies is black-box against white-box: instrumented services report their own internals, this reports what a client observes, and that is the only signal which survives the service being completely down.

That makes it the natural source of an availability SLI and of a paging alert, since `probe_success == 0` needs no interpretation. Keep the target list in service discovery rather than hand-written, run exporters in more than one location so a network partition does not read as an outage, and remember every probe is real traffic hitting a real endpoint on a schedule you chose.

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

It is an awk-shaped tracing language compiled to eBPF, so a single expression attaches a probe to a kernel function, aggregates in a kernel-side map keyed by process name or latency bucket, and prints the histogram on exit. Probe types span kprobes and kretprobes, uprobes in user binaries, tracepoints, USDT markers, software and hardware perf events, and timed intervals, all without touching the traced program.

Reach for it for the ad-hoc question no exporter answers: the latency distribution of one syscall, which process is issuing the writes, how long a specific function takes under load. Overhead is low and nothing is recompiled, but it needs root and a reasonably recent kernel, and the one-liner is throwaway by design, so a question you will ask repeatedly belongs in a compiled libbpf tool or an exporter.

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

Burrow consumes the internal offsets topic to follow every consumer group's committed offsets rather than being told which groups to watch, keeping a sliding window of recent commits per partition. Its evaluation rule is the interesting part: instead of comparing lag to a threshold, it asks whether offsets and lag are moving, marking a group unhealthy when it has stopped committing or lag grows monotonically, and healthy when it is behind but catching up.

That sidesteps the threshold problem, where a number tuned for a quiet topic pages constantly on a busy one. Reach for it to watch many groups without per-group configuration; it evaluates status and exposes it over HTTP, leaving notification to something else. Simpler exporters publishing raw lag as a Prometheus gauge remain the common alternative when you would rather write and own the alerting rule yourself.

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

It reads the kernel's cgroup accounting and container runtime metadata directly, so per-container CPU, memory working set, page faults, block I/O and network counters come from the same place the kernel enforces limits rather than from inside the container. The kubelet embeds it, which is why `container_cpu_usage_seconds_total` and `container_memory_working_set_bytes` appear on a node's cadvisor metrics endpoint with nothing deployed.

Those two series carry most Kubernetes capacity work, with throttling visible as `container_cpu_cfs_throttled_seconds_total` against the CPU limit and OOM risk as working set against the memory limit. It reports consumption only, so what the object was supposed to look like comes from kube-state-metrics and the useful alerts join both. Running a standalone cAdvisor per node is redundant when the kubelet already exposes the same data.

### Call stack profiler
**Short:** Profiler view of the live call stack, used to spot deep recursion and runaway frame allocation.
**Kind:** concept
**Lang:** *
**Roles:** observability/profiling-and-performance @1

The idea is to sample or snapshot the stack of every thread rather than to measure elapsed time: the frame list itself reveals depth, recursion, and whether the same chain appears in every sample. Walking a live stack shows what the program is doing at this instant, which is how a thread spinning in a loop and a thread parked on a lock are told apart when both look equally stuck from outside.

It answers where the program is, not what anything cost, and stack depth is the specific symptom it exposes: unbounded recursion approaching an overflow, or a deeply nested chain allocating a frame per level. Reach for it when a process hangs or dies with a stack overflow. For attributing CPU time across a window, a sampling profiler's aggregated flame graph answers a different question entirely.

### callgrind
**Short:** Valgrind tool that simulates caches and records a call graph for instruction-level profiling.
**Kind:** tech
**Lang:** cpp
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

It runs the program on Valgrind's synthetic CPU, counting every instruction executed and recording the full call graph with inclusive and exclusive cost per function and per source line, with a cache simulator modelling first-level and last-level hits and misses on top. Because the numbers are counted rather than sampled, two runs over the same input produce identical output and a difference of a few percent is real signal.

That determinism is exactly why you use it, since comparing two implementations without benchmark noise is otherwise hard. The price is roughly a fiftyfold slowdown and a simulated rather than real memory hierarchy, so it will not tell you what your actual processor does with prefetching and out-of-order execution. Read the output in KCachegrind, and confirm the wall-clock win on real hardware with `perf`.

### Capacity/forecast tools
**Short:** Umbrella entry for demand-forecasting tooling used in capacity planning against SLO headroom.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1, platform-delivery/cloud-platform-and-cost @2

The category covers anything that projects a resource series forward: Prometheus's own `predict_linear` over a disk-usage metric, a spreadsheet model of request growth against measured per-instance throughput, a cloud provider's usage forecaster, or a time-series forecasting library fed from the metrics store. The inputs are always historical utilisation plus a known limit, and the output is the date on which the two intersect.

Reach for it to turn capacity into an alert with lead time — disk full in four days, pool exhausted at next quarter's growth — rather than a page at the moment it happens. Forecasts assume the recent pattern continues, so they miss launches, seasonality and step changes, and the number is a planning prompt rather than a promise. Pair it with explicit headroom targets and load testing that establishes the real ceiling.

### Cassandra Exporter
**Short:** Sidecar that scrapes Cassandra JMX metrics and exposes them in Prometheus format.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/wide-column @3

Cassandra publishes its internals as JMX beans — per-table read and write latency histograms, pending compactions, hinted handoff, dropped messages, thread pool queues, SSTable counts — and the exporter republishes a selected subset in Prometheus format, running either as a separate process talking JMX or as a Java agent inside the node's own JVM. The agent form avoids the round trip and is the usual deployment.

Reach for it because the metrics that predict a Cassandra incident all live in JMX: rising pending compactions, dropped mutations, and per-table read latency at the tail. Watch cardinality, since a per-keyspace and per-table metric set multiplies fast on a cluster with many tables, so prefer an explicit allowlist. The generic JMX exporter does the same job with more configuration and none of the Cassandra-specific curation.

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

Each request is a row with status, initiator, size and a timing breakdown — queueing, stalled, DNS, TLS, request sent, waiting for the first byte, content download — which is what separates a slow server from a slow connection or a request that never left the queue. The protocol column shows whether it went over HTTP/1.1, h2 or h3, throttling profiles simulate a poor network, and disabling cache reproduces a first visit.

Reach for it first for any front-end performance or integration question: what the server actually returned, which asset blocks rendering, whether a preflight failed. It sees one browser on one machine, so it cannot tell you what the ninety-ninth percentile user experiences, which needs real-user monitoring, and the waterfall reflects local cache and installed extensions unless you clear them or use a clean profile.

### Cloud log services
**Short:** The managed log sinks of each cloud: CloudWatch Logs, GCP Cloud Logging and Azure Monitor Logs.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, platform-delivery/cloud-platform-and-cost @3

All three share a shape: an agent or the platform itself writes structured records into a named stream, retention is a per-stream setting, and a query language sits over the store — Logs Insights on AWS, the Logging query language on GCP, KQL on Azure Monitor. The advantage over anything self-hosted is that the platform's own control plane and managed services already log there, with no collection to build.

Reach for them when the workload is single-cloud and running a log store yourself is not worth the effort. The costs are ingestion per gigabyte, bytes scanned per query, and defaults that frequently never expire, so retention policy and dropping fields nobody searches are most of the tuning. Portability is the real tradeoff, since queries and dashboards do not move, which is the argument for Loki, Elastic or an OpenTelemetry pipeline.

### CloudTrail
**Short:** AWS service recording every API call in the account as an auditable event log for forensics and compliance.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, security/privacy-and-compliance @3, security/supply-chain-and-runtime-security @3

It records control-plane API calls — who, from which address, with which credentials, against which resource, and whether the call was denied — as JSON events delivered to an S3 bucket and optionally to CloudWatch Logs and EventBridge. Management events are recorded by default with ninety days of console history, while data events covering object-level access and function invocations are opt-in because their volume is far higher.

Reach for it whenever the question is what changed and who changed it, since an incident's root cause is often a console action nobody mentioned and compliance regimes require the trail regardless. Deliver to a locked bucket in a separate account with log file validation enabled, because stopping the trail is an attacker's first move. It is not application logging and it is not real time, so expect minutes of delay.

### Cortex
**Short:** Horizontally scalable, multi-tenant long-term storage behind Prometheus remote-write.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-stores/time-series @2

Cortex accepts Prometheus `remote_write` and splits the server into services that scale independently: distributors hash series onto ingesters, ingesters hold recent samples in memory and flush blocks to object storage, store gateways and queriers serve reads from the bucket, and compactors merge and downsample. Every series carries a tenant id, so one cluster serves many teams with their own limits and query isolation.

Reach for it when many Prometheus servers need a single query view and retention longer than local disk allows. The cost is that you now operate a distributed system with half a dozen roles instead of one binary. Grafana Mimir was forked from this codebase and is the more actively developed option today, while Thanos solves the same problem with a sidecar-and-bucket design rather than remote-write.

### Cost/latency observability
**Short:** Practice of instrumenting per-request cost and latency so the marginal-cost curve is visible before scale bites.
**Kind:** concept
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, platform-delivery/cloud-platform-and-cost @2

The practice is to emit cost and latency as first-class metrics on the request path you already instrument: compute units or tokens consumed, provider price applied, and the wall time of each external call, tagged by feature, tenant and model or instance type. Because the tags match the rest of your telemetry, a spend spike is attributable to an endpoint and a customer rather than discovered on next month's invoice.

Reach for it before scale rather than after, since the point is to see marginal cost per request while the design is still cheap to change. The discipline it demands is cardinality control — tenant is usually fine, individual user id is not — plus agreement on how cost is computed. A billing export gives you the total accurately and never tells you which code path caused it.

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

It means owning the instrumentation: initialising the SDK yourself, creating spans and metrics at the boundaries you care about, setting attributes, and configuring samplers and exporters, rather than dropping in a vendor agent that decides all of it. The payoff is spans that describe your domain — a job, an order, a retrieval step — emitted as plain OTLP, so the backend behind them is a configuration change.

Reach for it when the vendor agent does not understand your framework, when spans need business attributes, or when avoiding lock-in is worth the engineering time. The cost is ongoing: someone owns context propagation across threads, queues and async boundaries, plus sampling and attribute hygiene forever. Auto-instrumentation already covers HTTP, database and messaging spans well, so the usual answer is auto-instrumentation plus a few hand-written spans.

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

It is a product surface inside Datadog rather than a separate tool: instrument the application with its SDK or an OpenTelemetry pipeline and each request becomes a trace of nested spans for model calls, retrieval steps and tool invocations, carrying prompt and completion text, token counts, latency and computed cost. Because those spans share the platform's tags, they sit in the same trace as the HTTP request and the queries around it.

That correlation is the reason to choose it over a standalone LLM tracer, since a slow answer can be followed into the vector store query or the downstream service. It also means the platform's billing model applies, and prompt text is high-volume unstructured data you are now sending to a vendor. A self-hostable tool such as Langfuse is the alternative when that content cannot leave your own infrastructure.

### Datadog LLM Obs
**Short:** Datadog's LLM observability product: prompt/response traces, token cost and quality metrics in existing APM.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

Alongside the traces it can run automatic evaluations over sampled spans — quality and safety checks and sensitive-data scanning — and accepts custom scores submitted against a span, so a judged result sits beside the trace that produced it. Token counts per model roll up into cost and usage metrics you can break down by application, service, model and whatever tags the rest of your telemetry already carries.

Reach for it when Datadog is already the observability platform and you want LLM spend and quality on the same dashboards, monitors and alerts as everything else without adding a second vendor. The considerations are the platform's usual ones, since span volume, indexed retention and tag cardinality drive the bill, plus a deliberate decision about whether prompt and completion text may be transmitted at all.

### Datadog Logs
**Short:** Datadog's hosted log ingestion, indexing and search product, correlated with its metrics and traces.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, observability/alerting-and-incident-response @3

The same host agent that ships metrics and traces collects logs, pipelines parse them into structured attributes, and a deliberate two-tier model splits them: everything ingested can be archived to object storage and rehydrated later, while only what passes an index filter is indexed for fast search and kept for a chosen retention. Log patterns cluster similar lines automatically, which is how a new error type surfaces without a saved query.

The tie to traces is the point, since a line carrying a trace id links to the trace that produced it and to the host metrics of that moment. The cost model rewards discipline: ingestion is billed separately from indexed events, so exclusion filters, sampling and short retention on debug logs keep it affordable. A self-hosted Elastic or Loki stack is cheaper per gigabyte and far more work.

### Datadog/New Relic
**Short:** Commercial SaaS APM platforms combining distributed traces, metrics, logs, dashboards and alerting.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, observability/alerting-and-incident-response @3

They occupy the same slot: an agent per host or pod auto-instruments the mainstream runtimes, ships traces, metrics, logs and profiles to a hosted backend, and correlates them through shared tags so a slow endpoint links to its trace, its log lines and the host underneath. Both also ingest OpenTelemetry, so the instrumentation itself need not be vendor-specific even when the backend is.

Reach for one when buying the whole stack beats operating Prometheus, a trace store and a log store, which for a small team it usually does. What decides between them is the pricing shape — per host plus indexed spans and custom metrics against per ingested gigabyte plus users — and agent quality for your particular language. Both create lock-in through dashboards, monitors and query languages that do not port.

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

NVIDIA's Data Center GPU Manager reads driver telemetry, and the exporter turns a configurable subset of those fields into Prometheus metrics: SM and memory utilisation, framebuffer used and free, clocks, power draw, temperature, ECC error counts, PCIe and NVLink throughput, and XID errors. In Kubernetes it runs as a DaemonSet and can attach pod, namespace and container labels so a GPU metric joins to the workload using it.

Reach for it for anything GPU-adjacent, because request-level metrics cannot say whether a slow inference server is compute-bound, memory-bound or thermally throttled. Two caveats matter: some fields are profiling counters with a real sampling cost, so enable them deliberately, and under Multi-Instance GPU the values are reported per instance, which quietly makes whole-GPU dashboards wrong in both directions.

### DCGM exporter pairing
**Short:** Running NVIDIA DCGM's Prometheus exporter next to an inference server so GPU health sits beside request metrics.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, gpu/gpu-profiling-and-debugging @2

The arrangement is two scrape targets on one node feeding a single Prometheus: the serving process exposing request rate, queue depth, batch size and latency, and the DCGM exporter exposing SM utilisation, framebuffer occupancy, power and clocks for the GPUs that server owns. Matching labels for node, GPU index and, in Kubernetes, the pod are what let one dashboard put the two halves side by side.

It exists because either half alone is nearly useless during an incident. Rising latency with low SM utilisation is a batching or host-side bottleneck, rising latency with clocks dropping is thermal or power throttling, and growing framebuffer use at stable traffic is a leak or a cache that is never freed. The cost is a second exporter per node plus the label discipline that keeps the join working.

### DCGM visibility caveat
**Short:** Under MIG, DCGM reports per-slice GPU metrics, so whole-GPU dashboards silently show partial data.
**Kind:** concept
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, gpu/gpu-profiling-and-debugging @2

Under Multi-Instance GPU a physical card is partitioned into isolated instances with their own compute slices and memory, and telemetry follows that partitioning: figures are reported per instance, and several whole-device fields either go unpopulated or describe the entire card rather than the slice. A dashboard written against an unpartitioned fleet keeps drawing regardless, quietly presenting one slice's utilisation as though it were the GPU's.

The practical consequence is that capacity decisions taken from that dashboard are wrong in both directions, since a saturated card can look idle and a full one can look like it has headroom. Handle it by labelling metrics with the instance id and profile, aggregating across instances explicitly, and checking which fields the driver actually populates. Running GPUs whole avoids the problem entirely when per-slice accounting is not needed.

### depesz EXPLAIN
**Short:** Web tool that reformats a PostgreSQL EXPLAIN plan into a ranked tree so the expensive node is obvious.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

You paste an `EXPLAIN` or `EXPLAIN ANALYZE` plan into the form and it renders the node tree with columns Postgres does not print: exclusive time per node as well as inclusive, each node's percentage of total runtime, and the ratio between estimated and actual rows. Nodes are colour-ranked, so the slowest step and the worst estimate are visible immediately instead of being inferred from indentation.

Reach for it when a plan runs to more than a handful of nodes and you want the expensive one named. The estimate-against-actual ratio is the column that matters most, since a bad row estimate is what makes the planner pick a nested loop over a hash join. It is a hosted page, so treat a pasted plan as disclosure of your schema and query shapes; pev2 does the same job locally.

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

Both IDEs run the statement with plan collection enabled and render the result as an expandable tree showing per-node cost, estimated and actual rows, loop counts and timing, usually highlighting the heaviest nodes. It is the same plan the server would print, and the value is entirely in the presentation, because a deeply nested text plan hides which sibling actually consumed the time.

Reach for it while you are already iterating on the query in the editor, since the loop of edit, re-plan and compare costs a keystroke. Two cautions: `ANALYZE` executes the statement, so running it against an `UPDATE` or `DELETE` changes data unless wrapped in a transaction you roll back, and the IDE's session settings may differ from the application's. For sharing or archiving a plan, depesz or pev2 render it better.

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

Declaring an incident from chat or the web app runs a defined runbook: it opens the channel, assigns roles such as commander and communications lead, creates the tracking ticket, starts a timeline that records events and messages automatically, and drives the status page update. Severity and ownership come from a service catalogue, so an incident is attached to the components it affects and the teams that own them.

The automated timeline is the real product, because a retrospective written from somebody's reconstructed memory of the night is worth much less than one written from timestamps. Reach for it when incidents are frequent enough that consistency beats flexibility; below that a channel-naming convention and a document template cost nothing. Paging still belongs to a dedicated on-call tool, and none of it works unless people actually declare incidents.

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

Events flow through input, filter and output plugins with a tag deciding routing, and the buffering layer between filter and output — memory or file backed, with configurable chunk size, flush interval and retry backoff — is what lets delivery survive a downstream outage. Hundreds of community plugins exist, which is the real reason it persists: almost any source or sink already has one written.

It is a Ruby process with a C core, heavier than Fluent Bit and lighter than Logstash, so the common topology now puts Fluent Bit on every node and reserves Fluentd, if used at all, for a central aggregator doing routing and enrichment. Reach for it when you need a plugin that exists nowhere else. For a plain node collector, or a pipeline where transformation dominates, Fluent Bit and Vector are the current defaults.

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

Alloy is a distribution of the OpenTelemetry Collector with Grafana's own components added, configured in a declarative language where each block is a component and you wire one component's output into another's input. Prometheus-style scraping and relabeling, log tailing and processing, OTLP receivers and continuous-profile collection all compose in the same pipeline, and Prometheus service discovery means targets are found rather than listed.

It is Grafana's convergence point, superseding Promtail and the earlier agent modes, so a new deployment collecting more than one signal should start here rather than running three agents per node. Reach for it when one collector beats several. If you have no Grafana-specific needs, the upstream OpenTelemetry Collector is the more neutral choice and the thing you would migrate to anyway.

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

It is the maintained Python 3 port of the Guppy toolkit, and in practice you use one entry point: a heap facade whose census walks every object the garbage collector knows about and returns a set partitioned by type, showing count, total size and each class's share of the heap. Sets support subtraction, so two snapshots taken around an operation give exactly what that operation retained.

Reach for it when a long-running process grows and you need to know what is accumulating rather than where it was allocated. The walk is a stop-the-world census costing seconds and memory of its own, and it sees only Python objects, so a leak inside a C extension stays invisible. `tracemalloc` attributes growth to allocation sites instead, and memray covers native allocations that neither of them can see.

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

Heapy is the heap-analysis component of guppy3 rather than a separate package: its census groups live objects by type with per-group counts, cumulative bytes and percentage of the heap, and can regroup by referrer or by the attribute holding the reference, which is how you find what is keeping something alive. Marking a baseline first makes a later census report only what has been added since.

That baseline-and-diff loop is what makes it useful for retention hunting, since running the suspect operation between two censuses shows precisely what survived. It is a one-shot analysis expensive enough to belong in a debugging session rather than a running service, and it cannot see memory held by C extensions or lost to allocator fragmentation, which is the usual reason resident memory exceeds what the census reports.

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

It ingests the trace files the PyTorch profiler writes for every rank and answers job-scale questions the trace viewer cannot: a temporal breakdown of how each GPU's time splits between compute, communication and idle, a kernel breakdown by category and duration, idle time attributed to host gaps or to waiting on collectives, and rank comparison that identifies the straggler holding up a synchronous step.

Reach for it when a distributed training job's step time is worse than the sum of its kernels suggests, which is exactly the case a single-rank flame graph cannot explain. It analyses traces rather than collecting them, so profiling overhead and trace volume remain the profiler's problem and a handful of steady-state steps per rank is what you want. For a single-GPU kernel question, Nsight Systems is more direct.

### Honeycomb
**Short:** Managed OTLP-compatible tracing backend built for high-cardinality querying of production events.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/alerting-and-incident-response @3

The model is wide events rather than pre-aggregated metrics: instrumentation emits one span per unit of work carrying as many attributes as you like — user, tenant, build, feature flag, shard — and the backend stores them in a column store that groups and filters by any of them at query time. Nothing is declared in advance, which is why arbitrarily high cardinality is the selling point rather than the hazard.

BubbleUp is the workflow that follows: select the slow or failing region of a heatmap and it diffs attribute distributions inside against outside, naming the dimension that explains the anomaly. Reach for it when failures depend on some attribute combination nobody predicted. Billing is per event ingested, so sampling becomes a design decision, and the approach only repays the effort if your instrumentation is genuinely rich.

### Hubble
**Short:** Cilium's eBPF network observability layer: per-flow visibility, service maps and dropped-packet reasons.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, runtime-systems/io-networking-and-syscalls @2, platform-delivery/kubernetes-and-orchestration @3

Cilium already processes every packet in eBPF programs in the kernel, and Hubble exposes what those programs see: per-flow records with source and destination pod, namespace, service, port, verdict and, for a drop, the specific reason such as policy denial, missing route or connection tracking state. Because identity is Cilium's own pod identity rather than an address, a flow stays interpretable after the pod is rescheduled.

Reach for it when a connection fails in a cluster running network policy, because the answer is usually a policy that denied it and Hubble states that outright instead of leaving you to infer it from a timeout. A CLI, a service-map UI and a Prometheus exporter all read the same data. It requires Cilium as the CNI, and full flow export at high traffic costs CPU and storage, so scope it.

### Incident
**Short:** On-call and incident management tooling: paging schedules, incident channels, timelines and postmortems.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

The category covers the tooling that carries an outage from detection to write-up: a paging service holding schedules and escalation, a way to declare an incident that opens a channel and assigns a commander and a scribe, an automatically recorded timeline, a customer-facing status page, and a retrospective with tracked action items. Products differ mainly in which of those they own outright and which they integrate.

Reach for it once more than one person is on call, because the failure without it is not a missing tool but a missing role: nobody knows who is coordinating, and the timeline gets reconstructed from memory a week later. The cost is process overhead, which only repays itself if declaring an incident is cheap. No product supplies the part that matters, which is blameless review and finishing the action items.

### incident.io
**Short:** SaaS incident management: declare an incident, assign roles, run comms and drive the retrospective from chat.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

It is driven from chat: a slash command declares an incident, which creates the channel, sets severity and roles, posts a summary and begins capturing a timeline from messages, pins and integrations. Workflows are configurable automations that announce at a severity threshold, open a ticket, page a team or remind the lead to send an update, and the retrospective is generated pre-populated with the timeline it recorded.

Reach for it when the organisation already lives in Slack and you want process without asking responders to leave the channel they are working in. It also sells on-call scheduling and paging, so it can be the single tool rather than a layer over PagerDuty. The limit is common to the category: it records and coordinates but detects nothing, and adoption dies if declaring an incident feels heavyweight.

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

It samples hardware performance counters and call stacks with low enough overhead to run against a real workload, then presents analysis types rather than raw counters: hotspots for where time goes, microarchitecture exploration for the top-down breakdown of whether cycles are lost to bad speculation, front-end stalls or memory, memory access for cache and bandwidth behaviour, and threading for lock contention and imbalance.

The ITT API is what makes it usable on a large program, since annotating your own regions as named tasks attributes time to your operations instead of to library frames. Reach for it when you need to know why a CPU-bound loop is slow rather than which loop is slow. It is Intel-oriented, with only a subset of analyses working elsewhere, so AMD uProf or plain `perf` covers other hardware.

### Intel VTune Profiler
**Short:** Intel's micro-architecture profiler: memory access patterns, NUMA hot spots, pipeline stalls and false sharing.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

The memory-side analyses are its distinctive part. Memory access attributes cache misses and DRAM bandwidth to specific data structures and allocation sites rather than only to instructions, the top-down view apportions every cycle to front-end, back-end, bad speculation or retiring work, and on multi-socket machines it separates local from remote accesses so a NUMA placement problem becomes visible instead of merely suspected.

False sharing surfaces here too, as two threads writing distinct variables that share a cache line and generating coherency traffic out of all proportion to the work. Reach for it when a loop's throughput does not match its instruction count. Collection needs driver support or a permissive perf setting, and on non-Intel hardware `perf c2c` and AMD uProf answer the same questions with less depth.

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

JMC opens a Flight Recorder file and turns its event stream into pages: memory and allocation with the allocating stack traces, garbage collection phases and pause times, lock contention with the contended monitors, thread activity, exceptions, socket and file I/O, and JIT compilation. An automated analysis page runs rules over the whole recording first and lists scored findings, which is where to start rather than the raw event browser.

Reach for it as the reader for anything Flight Recorder produced, including a recording pulled from a live production process with `jcmd`. It is a desktop application shipped separately from the JDK and it analyses a file rather than watching a process, so it is post-mortem by nature. For continuous fleet-wide profiling you want a backend that ingests recordings instead of a human opening each one.

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

Native Memory Tracking must be armed at launch with `-XX:NativeMemoryTracking=summary` or `detail`, after which this command prints how much memory the JVM has reserved and committed per internal category — Java heap, metaspace, code cache, compiler, GC structures, thread stacks, symbols, internal — with thread and arena counts. The `detail` level additionally reports the call sites that requested each region.

Reach for it when a container's resident memory sits far above `-Xmx` and the heap is plainly not the problem, which is the classic Kubernetes kill with a healthy-looking heap; the answer is usually thread stacks, metaspace or code cache. Tracking costs a few percent of throughput and some footprint of its own, and it only sees allocations the JVM accounts for, so a library calling `malloc` directly stays invisible.

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

Its role in an operations workflow is narrow and specific: the retrospective produces action items, and each becomes an issue with an owner, a priority and a due date in the team's ordinary backlog, so remediation competes for planning attention alongside feature work instead of living in a document nobody reopens. Incident tooling generally creates these issues automatically and links them back to the incident record.

Reach for it because incident processes fail at follow-through far more often than at response, and a tracker with a review cadence is the only durable fix. Label incident-derived items so completion rate stays measurable, since otherwise they disappear into a large backlog. Any tracker the team already uses does this job equally well, and Linear is the common alternative; reviewing the list matters more than the product.

### JITWatch
**Short:** GUI that reads HotSpot compilation logs to show what the JIT compiled, inlined, or deoptimized and why.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/runtime-internals-and-types @1

Run the JVM with compilation logging enabled and it writes an XML record of every compilation decision; JITWatch parses that log alongside the bytecode and source and shows, per method, what the tiered compilers did — whether it compiled and at which tier, what it inlined, what it refused to inline and why, which branches were never taken, and where a deoptimisation sent execution back to the interpreter.

Reach for it when a microbenchmark result is inexplicable or a method that ought to be trivial refuses to get fast: the usual answers are a method too large to inline, a call site that stayed megamorphic, or repeated deoptimisation from an uncommon trap. It explains compiler decisions rather than where time goes, so pair it with a profiler, and treat it as a development tool since the log is verbose.

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

The systemd journal is a structured indexed binary store rather than a set of text files, and this is the query tool over it: filter by unit with `-u`, by boot with `-b`, by time with `--since`, by priority with `-p`, or follow live with `-f`. Because kernel messages and every unit's output land in the same journal, a kernel event and the service it killed appear in one interleaved stream.

Reach for it first when a process died and nobody knows why, since the OOM killer's verdict, a segfault, a failed unit's exit status and a driver complaint are all here with timestamps. Storage may be volatile depending on configuration and size limits, so an old incident may simply be gone and worth shipping onward for retention. Emit JSON with `-o json` when a log pipeline consumes it.

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

`jstat -gc <pid> 1000` reads the JVM's shared performance-counter file and prints a line per interval: eden, survivor and old capacity and usage, metaspace, and the count and cumulative time of young and full collections. Nothing is instrumented and nothing attaches, so the cost is effectively zero and it works against a JVM that was started with no diagnostic flags at all.

Reach for it for the first thirty seconds of a memory or garbage-collection question: whether old generation grows and is never reclaimed, whether full collections are frequent, whether metaspace is climbing. It gives counters over time and nothing more, with no object, allocation site or stack, so once the shape of the problem is clear you move to a heap dump, Flight Recorder or a profiler for the cause.

### jstat -gcutil
**Short:** JDK CLI printing live GC utilization per generation and cumulative GC time for a running JVM.
**Kind:** tech
**Lang:** java
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2, runtime-systems/runtime-internals-and-types @3

The `-gcutil` view drops absolute sizes and prints percentage utilisation of each space — both survivor spaces, eden, old, metaspace and compressed class — plus young and full collection counts and their cumulative seconds. That normalisation makes a repeating sample readable at a glance: you watch eden fill and reset while old climbs, and the last columns show how much of wall time the process spends collecting.

The diagnosis is in the trend. Old utilisation that never falls after a full collection is a leak or an undersized heap, full-collection time growing faster than young indicates a promotion problem, and a survivor space pinned at full means objects are being promoted prematurely. Reach for it as a live check; for pause distributions and the cause of a particular collection, garbage-collection logs or Flight Recorder are the record you analyse.

### Kafka Exporter
**Short:** Exporter that turns Kafka broker/consumer-group state and JMX metrics into Prometheus series for lag dashboards.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-movement/event-streaming-and-processing @2

It runs as a Kafka client and reports the cluster state that matters operationally: partition leaders, replica and in-sync replica counts, each partition's earliest and latest offsets, and every consumer group's committed offset, publishing the difference between latest and committed as lag per group, topic and partition. Broker-internal figures such as request handler idle ratio come from a JMX exporter running alongside it.

Reach for it because consumer lag is the health metric for a streaming system and the brokers expose no single number for it. Series count is the thing to watch, since partitions multiplied by consumer groups is a large product on a busy cluster and per-partition lag is rarely what you alert on. Burrow's trend-based evaluation is the alternative when static lag thresholds keep producing false pages.

### Kafka Lag Exporter
**Short:** Exports per-consumer-group Kafka offset lag, in messages and estimated seconds, as Prometheus metrics.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-movement/event-streaming-and-processing @2, observability/alerting-and-incident-response @3

Beyond message lag it estimates time lag: by interpolating a partition's offset-to-timestamp history it computes how many seconds behind a group's committed offset is, which is the number that means something to a human, because forty thousand messages behind matters very differently on a topic doing forty messages a second than on one doing four thousand. Both figures are exported per group and per partition.

Reach for it when lag alerts need a threshold you can defend, since seconds behind maps onto an SLA and raw message counts do not. The estimate depends on a reasonably steady production rate, so a bursty or idle topic yields a noisy figure. It is a JVM service to operate and, like any lag exporter, emits series proportional to groups multiplied by partitions, so aggregate before alerting.

### KCachegrind
**Short:** GUI viewer for callgrind/cachegrind output showing call graphs and instruction-level cache miss attribution.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @3

It reads the profile files callgrind and cachegrind write and makes them navigable: a sortable function list with inclusive and exclusive cost, a call graph you can walk in both directions, a caller and callee map drawn as nested rectangles proportional to cost, and annotated source and assembly showing per-line instruction counts and cache misses. Any event counter present in the file can be selected as the cost dimension.

Reach for it whenever you have run callgrind, because the command-line annotator is unreadable on anything larger than a toy program. It only visualises, so the slowdown and the simulated-CPU caveats belong to the collection step and the numbers remain simulated instruction counts rather than measured time. A Qt build exists for platforms without KDE, and converters let it read profiles produced by other languages.

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

Both wrap model calls and record what happened, but they arrive there differently: Helicone is normally a proxy, so changing a client's base URL captures every request with no code change and enables caching and rate limiting in the path, while Langfuse is normally an SDK or OpenTelemetry exporter, so nothing sits between you and the provider and traces carry your own nesting. Both compute token cost, latency and per-user attribution.

Reach for either when a dashboard of request counts cannot answer why one answer was wrong or what a feature costs. The tradeoff is the integration point, since a proxy is fastest to adopt and becomes an availability dependency, while an SDK is more work and stays out of the request path. Both are open source and self-hostable, which is usually what decides it when prompts contain regulated data.

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

Its distinguishing design was to analyse every span rather than a sampled subset: collector processes near the application hold recent traces while the backend computes latency histograms and error rates from the full stream, retaining the exemplar traces that explain the tail. Change intelligence works from that data, comparing a regression window against a baseline and naming the service, operation or attribute whose behaviour actually changed.

It ingests OTLP and its team drove much of OpenTelemetry itself, so instrumentation written for it stays portable, which matters because the product now ships as ServiceNow Cloud Observability and its direction follows that platform. Reach for it if the surrounding ServiceNow tooling is what you run; otherwise Honeycomb covers the high-cardinality analysis case and Grafana Tempo the self-hosted trace store.

### LIKWID
**Short:** HPC command-line suite reading hardware performance counters for bandwidth, cache and NUMA behaviour.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

It is a set of small command-line tools rather than a GUI: the counter tool reads hardware performance counters for a pinned process and reports derived groups such as memory bandwidth in gigabytes per second, cache miss ratios, floating-point rate and energy, instead of raw counter numbers, while companion tools control thread and process affinity and print the machine's cache and NUMA topology.

The derived groups are why high-performance-computing people reach for it, since bandwidth and floating-point rate place a kernel directly on a roofline plot, which is the question in numerical code. A marker API lets you instrument regions so counters apply to one loop rather than the whole run. It needs counter access permission, groups are architecture-specific, and call-graph attribution belongs to `perf` or VTune instead.

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

Its part in an operations workflow is the same as any tracker's: retrospective action items become issues with owners, created automatically by the incident tool so nothing depends on someone remembering. What differs is deliberate constraint, since cycles, a triage inbox and enforced states mean remediation work gets scheduled into an iteration rather than accumulating in an unbounded backlog nobody grooms.

Reach for it because incident processes fail at follow-through far more often than at response, and the durable fix is putting the work where the team already plans. Keep a label or project for incident-derived items so completion rate stays visible, otherwise they vanish among feature tickets. Jira does the same job, and which one you use matters far less than actually reviewing the list.

### Log4j 1.x
**Short:** The original Log4j logging implementation, end-of-life since 2015; migrate to Log4j 2 or Logback rather than using it.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1

It introduced the appender, layout and hierarchical logger model that everything after it copied, configured through a properties or XML file. Development stopped in 2012 and it reached end of life in 2015, leaving unpatched vulnerabilities of its own in the socket server, JMS appender and SMTP appender, and it is architecturally stuck as well, with synchronous appenders and a global lock that becomes a bottleneck under load.

It survives in the wild because a transitive dependency drags it in, not because anyone chooses it. The fix is a bridge — one artifact routes calls into Log4j 2, another routes them into SLF4J and Logback — which needs no source change in the offending library. Note that this is not Log4Shell, which was a Log4j 2 lookup feature, so a scanner flagging one says nothing about the other.

### Log4j2
**Short:** Java logging implementation with a plugin architecture and Disruptor-backed async loggers; source of Log4Shell.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1, runtime-systems/concurrency-and-async @3

The rewrite separates an API from the implementation and loads appenders, layouts, filters and lookups as plugins discovered on the classpath. Its performance argument is the async logger: backed by the LMAX Disruptor ring buffer, the calling thread hands the event off without taking a lock, and throughput under contention is far above a synchronous appender. Configuration is XML, JSON, YAML or properties, with automatic reload on change.

Log4Shell came from a feature of this version, message lookups performing JNDI resolution on logged data, which is why the 2.17.1 release became the accepted floor and why untrusted input inside a log message is worth thinking about at all. Reach for it when async throughput or the plugin model is the requirement; Logback, Spring Boot's default and by the author of Log4j 1.x, is otherwise equivalent.

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

Written by the author of Log4j 1.x as its successor, it implements the SLF4J API natively so a call passes straight through with no adapter. Configuration supports conditional blocks, variable substitution, includes and automatic reload; appenders cover console, rolling files with size and time policies and network sinks, and an async appender moves writes off the calling thread through a bounded queue that drops rather than blocks.

Filters are the feature worth knowing, since a turbo filter runs before the event is even created and can suppress or elevate specific loggers without touching levels. Spring Boot depends on it by default, which settles the choice for most Java services. Reach for Log4j 2 instead when its Disruptor-backed async throughput or plugin ecosystem is the requirement, and add a JSON encoder when logs must be machine-parseable.

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

It is built directly on OpenTelemetry — its SDK wraps the OTel SDK — so anything already instrumented for OTel is picked up and everything it records is standard OTLP. What it adds is Python ergonomics: spans created by decorator or context manager with message templates whose interpolated values become structured attributes, one-call instrumentation for FastAPI, HTTPX, SQLAlchemy and Pydantic validation, and first-class tracing of agent runs.

The backend stores spans in a columnar store queried with SQL, so analysis is an ordinary query rather than a bespoke language. Reach for it on a Python stack, particularly one already using Pydantic, where the alternative is assembling an SDK, a collector and a backend yourself. It is primarily a hosted product, so request content sent as span attributes lands there and deserves an explicit decision.

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

There is one pre-configured logger object and you add outputs to it: a file path with rotation, retention and compression handles file management the standard library needs a handler class for, a serialisation flag emits JSON, an enqueue flag makes writes process-safe, and diagnostic tracebacks render an exception with the variable values in each frame. Binding and context managers attach structured context to subsequent records.

Reach for it in a script, a small service, or anywhere the standard library's configuration ceremony is disproportionate to the need. The cost is that it is not the standard library, so a library logging through it imposes it on every consumer, and the diagnostic tracebacks that make it pleasant will happily print secrets held in local variables. Use `logging` with a JSON formatter or structlog where interoperability matters.

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

It runs the program under Valgrind and takes periodic snapshots of the heap, recording total bytes allocated and the allocation tree that produced them, then writes a file rendered as a graph of heap size over time with a detailed breakdown at the peak. Because it intercepts the allocator, attribution is exact and reaches every allocation in the process, including those made inside third-party libraries.

The peak snapshot is the payoff, naming which call sites hold the memory when usage is highest, which is precisely the question when a process is killed for exceeding a limit. It slows execution by an order of magnitude or more and measures the heap rather than resident memory, so it belongs in a test run on a representative workload; heaptrack and sampling profilers are the lighter alternatives.

### memory_profiler
**Short:** Python line-by-line memory profiler; useful but slows the traced function several times over.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1

Decorate a function and run it under the module's runner, and it reports line by line the process's resident memory before and after each statement plus the increment attributable to it; a separate mode charts total usage over time instead, which is how a slow leak becomes visible as a rising line rather than a single number.

It samples the process's memory rather than tracking allocations, so the figures include the interpreter, extensions and allocator behaviour, and a line can show zero increment simply because freed blocks were reused. Instrumented functions run considerably slower. Reach for it for a quick answer on a script; memray gives true allocation-level attribution including native extensions, and `tracemalloc` ships in the standard library at no install cost.

### memray
**Short:** Python memory profiler tracing every allocation including C/C++ extensions, with flame graphs and a live TUI.
**Kind:** tech
**Lang:** python
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

It installs itself as the allocator's tracker, so every allocation and free, native as well as Python-level, is recorded with the stack that requested it and written to a capture file. Reports are generated afterwards: a flame graph of allocations, a table of the largest live allocations at peak, a temporal view of memory over time, and a leaks report of everything that was never released.

Seeing into C and C++ extensions is what distinguishes it, since memory held by NumPy, a database driver or any compiled dependency is invisible to `tracemalloc` and appears here. A live view can attach to a long-running process, and a pytest plugin asserts allocation behaviour in tests. Tracking every allocation costs real runtime and produces large files, so scope the window; it runs on Linux and macOS only.

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

It is a small standalone library defining two abstractions: an accessor that knows how to read, set and clear one piece of thread-local state such as the tracing context or a logging context entry, and a snapshot that captures every registered accessor into a value restorable on another thread. Reactor and similar libraries hook it so that state is restored around operator execution instead of being lost at the first scheduler boundary.

The problem it solves is specific: trace ids and logging context live in thread locals, while reactive pipelines, executors and virtual threads run continuations on different carriers, so logs lose their correlation id and spans lose their parent. Reach for it whenever tracing is wanted in reactive or async code. Capture and restore happen per operator and are not free, and every context piece needs a registered accessor.

### Micrometer integration metrics
**Short:** Micrometer instrumentation for Spring Integration channels and handlers, exported to Prometheus and friends.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1

Enabling metrics in Spring Integration turns each channel, handler and gateway into a meter: a timer per handler recording execution count, total time and errors, counters for messages sent and for errors on a channel, and gauges for queue channel depth and pollable source activity. Names derive from component names, so naming your channels and endpoints in the flow definition is what makes the resulting dashboard legible.

Reach for it because an integration flow fails at a specific hop — a queue channel filling, one handler slow, an error channel receiving traffic — and application-level request metrics never show which. Cardinality is bounded by the number of components rather than by traffic, so the volume stays modest. For causal ordering across the flow rather than aggregate rates, add tracing through the Observation API instead.

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

It is the successor to Spring Cloud Sleuth, moved out of Spring and into Micrometer: your code and Spring's own instrumentation create observations or spans against Micrometer's `Tracer` interface, and a bridge on the classpath implements them with OpenTelemetry or with Brave. Propagation, baggage and the injection of trace and span ids into the logging context belong to the facade rather than to whichever implementation you chose.

The reason for the indirection is SLF4J's: instrumentation inside Spring Boot and inside libraries should not force a tracing vendor onto the application. Reach for it on any Spring Boot service that needs distributed tracing. Exactly one bridge may be present or the two implementations fight for the same context, and an application with no Spring involvement is usually better served by the OpenTelemetry SDK directly.

### Micrometer virtual thread metrics
**Short:** Micrometer binder exposing jvm.threads.virtual.* pinned-time and scheduler gauges for Loom workloads.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, runtime-systems/concurrency-and-async @2

The binder registers meters from the events and management beans the JVM exposes for virtual threads: cumulative pinned time and pinning occurrences, counts of threads submitted to and rejected by the scheduler, and the scheduler's parallelism, pool size and queue depth. Registered against the meter registry, they scrape exactly like any other Micrometer meter and need no separate collection path.

Pinned time is the metric that matters, because a virtual thread pinned to its carrier inside a native call — or, on older JDKs, a `synchronized` block — blocks a platform thread and quietly destroys the concurrency you adopted Loom for. Reach for it on any service that has switched to virtual threads. Aggregate counters say it is happening but not where, which is what the pinning JFR event carries.

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

It is a pushing registry rather than a scraped one: meters are batched and posted to Datadog's metrics intake over HTTPS on a configurable step interval, with the API key and step set through properties. Micrometer tags become Datadog tags, and the registry maps its meter types onto Datadog's own gauge, count and rate semantics, which is where a Micrometer timer's percentiles need explicit configuration to survive.

Reach for it when Datadog is the backend but the host agent cannot be in the path, such as a serverless function or a container with no DaemonSet. Otherwise pushing directly is usually the worse option, because the agent adds host tags, buffers through outages and handles retries, while direct submission bills every custom metric and gives the application an outbound dependency on the vendor's API.

### micrometer-registry-prometheus
**Short:** Micrometer registry that renders JVM and app metrics in Prometheus format at /actuator/prometheus.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1

Micrometer is the metrics facade in Spring Boot — counters, timers, gauges and distribution summaries recorded against a vendor-neutral API — and this artifact is the backend that renders them in Prometheus text format. Adding it exposes `/actuator/prometheus`, at which point the JVM, HTTP server, connection pool and cache instrumentation that auto-configuration already registered becomes scrapeable without writing any collection code.

Watch tag cardinality: a tag whose value is a user id or a raw URL path creates a distinct time series per value and is the standard way to overwhelm Prometheus, which is why Boot tags HTTP metrics with the templated route rather than the concrete URI. Percentiles need explicit configuration too — publish histogram buckets if you want a p99 that can be aggregated across instances rather than one computed per instance.

### micrometer-tracing-bridge-brave
**Short:** Micrometer Tracing bridge that implements spans with Brave and propagates B3 headers for Zipkin-native tracing.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1

It binds Micrometer Tracing's tracer interface to Brave, Zipkin's Java instrumentation library, so observations created by Spring Boot and by your own code become Brave spans. Brave's default propagation is B3, in either the multi-header or single-header form, which is what makes this the bridge to pick when the surrounding estate already speaks B3 rather than W3C trace context.

Pair it with a Zipkin reporter to send spans to a collector; trace and span ids also land in the logging context for correlation. Exactly one bridge may be on the classpath, so this and the OpenTelemetry bridge are mutually exclusive. For a new system prefer the OTel bridge, since OTLP reaches more backends and `traceparent` is the format everything else now propagates by default.

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

It polls the server at an interval and prints, per collection, the time the server spent in read and write operations during that interval, sorted by total. That is a different question from server-wide operation counters: this attributes time to a namespace, so a single hot collection stands out immediately instead of being hidden inside an aggregate that only says the instance is busy.

Reach for it as the first step when a MongoDB instance is loaded and you do not yet know which part of the schema is responsible, then follow with the current-operation view and the profiler to see which operations. It reports time rather than operation counts or index efficiency, so it cannot distinguish one unindexed scan from a million cheap reads. Percona Monitoring or Atlas dashboards give the same view continuously.

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

It is a storage engine whose tables are in-memory views over instrumentation compiled into the server: the statement digest summary aggregates every statement into a normalised fingerprint with counts, latency, rows examined against rows sent, and temporary table and sort usage, while wait, stage, lock and memory tables show where time inside a statement went. The `sys` schema is a set of readable views over these tables.

Reach for it instead of the slow query log when you want live aggregated data rather than a file to post-process, and note that rows examined against rows sent is the single most useful column for finding a missing index. Instrumentation costs memory and CPU, and the defaults enable only a subset, so consumers and instruments must be switched on deliberately; digest tables are bounded and can overflow into a catch-all row.

### MySQL pt-query-digest
**Short:** Percona tool that aggregates a MySQL slow query log into ranked query fingerprints by total time and rows examined.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

It reads a slow query log, general log, packet capture or Performance Schema output, normalises each statement into a fingerprint with literals replaced, and prints a ranked profile giving per fingerprint the count, total and percentile response time, rows examined and sent, and a representative example. Ranking is by total time by default, which is what surfaces the cheap query executed a million times.

Reach for it after an incident or as a periodic workload review, and set the slow-query threshold low enough during the capture window that the log is representative rather than only the disasters. The cost is the logging itself, since a very low threshold on a busy server writes substantial I/O, and the report describes a window rather than being continuous. Performance Schema digests answer similar questions live.

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

Atlas keeps operational time series in memory rather than on disk, and everything else follows from that decision: recent data answers queries in milliseconds at very large series counts, at the cost of retention, so older data is rolled off or moved to secondary storage. Queries are written in a stack-based language passed in the URL, composing operators over dimensional series, and the same expression defines a graph or an alert.

It was built for the case where dashboards must stay fast while millions of series are written, and it is open source. Reach for it if that scale and latency requirement are genuinely yours. The ecosystem around Prometheus — exporters, PromQL, Grafana integration, remote-write backends — is far larger, and the reverse-polish query language is a real adoption cost for a team that already knows PromQL.

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

It collects no telemetry of its own. You point it at the systems that already do, declare an SLO as a pair of queries or a threshold over a metric plus a target and a rolling or calendar window, and it computes the indicator, tracks error budget consumed and current burn rate, and alerts on burn rather than on the raw signal. Definitions are YAML applied through a CLI, so SLOs live in version control.

The value is one definition of reliability across teams whose metrics live in different tools, and a budget number product and engineering can both argue from. Reach for it when SLOs are an organisational practice rather than one team's dashboards. It is a paid hosted service, and Sloth or Pyrra generate equivalent Prometheus rules for nothing if everything you measure already lives in Prometheus.

### node_exporter
**Short:** Prometheus exporter publishing host-level metrics: CPU, memory, disk, filesystem and network counters.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, runtime-systems/memory-processes-and-os @3

It runs on the host and reads `/proc` and `/sys`, exposing collectors for CPU time per mode, load average, memory and swap, filesystem size and inode usage, disk I/O time and queue depth, network device counters, file descriptors, systemd unit states and hardware sensors. A textfile collector reads `.prom` files from a directory, which is the standard way to publish a cron job's result or a machine fact as a metric.

Reach for it on every machine, since it is the baseline for saturation and capacity alerting and `node_filesystem_avail_bytes` with `predict_linear` is the classic disk-full-in-four-hours rule. Run it on the host rather than in a container that cannot see the real namespaces, and enable collectors deliberately because some are expensive on large machines. It reports the host; per-container attribution comes from cAdvisor.

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

The Agents SDK emits traces by default: a run is wrapped in a trace and each model call, tool invocation, guardrail check and handoff becomes a nested span with its inputs, outputs and timing, uploaded to the platform where the dashboard renders the tree. Nothing needs configuring beyond an API key, and a run configuration flag or an environment variable disables the upload entirely.

Reach for it while building, because an agent that took the wrong path is unreadable from its final answer and legible from its span tree. Two limits decide whether it stays: the data sits on the provider's platform, which a privacy review will care about, and it is tied to that SDK rather than being general observability. A trace processor interface sends the same spans to an OTLP backend instead.

### OpenAI Tracing Dashboard
**Short:** Hosted run viewer for the OpenAI Agents SDK showing each turn, tool call and handoff in a trace.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, llm-apps/agent-framework @3

The view is per run: the workflow name and group id at the top, then the ordered spans — each turn's generation with its model and token usage, every tool call with arguments and result, handoffs between agents, and guardrail outcomes — so a multi-agent conversation reads as a timeline rather than a transcript. Grouping several traces under one identifier is what stitches a multi-turn session together into one view.

Reach for it to answer why an agent looped, which tool returned the bad value, or where the latency in a slow turn actually went. Retention and access follow your platform account, so it is not a substitute for the observability stack the rest of the service uses, and correlating an agent trace with the surrounding HTTP and database spans needs an exporter into a real tracing backend.

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

It took the Prometheus exposition format and specified it properly: metric families with an explicit type and unit, help, type and unit metadata lines, counters required to carry a `_total` suffix, defined text and protobuf encodings, and — the genuinely new part — exemplars, which attach a trace id and value to a histogram bucket sample so one latency bucket links to an example trace.

In practice it matters as a compatibility contract rather than as something you author, since instrumentation libraries emit it, servers negotiate it by content type, and exemplars are what make the metrics-to-traces jump in Grafana work at all. Its specification work has since been folded back into the Prometheus project, so a new system chooses between the Prometheus format and OTLP rather than treating this as a third option.

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

It defines Kubernetes-style YAML kinds for services, indicators, objectives and alert policies, so an objective, its indicator query, its window and its burn-rate alerting live in a file beside the service instead of clicked into a vendor console. The specification deliberately stops at declaring intent: it names the metric source and the good-and-total queries and leaves rule generation or platform configuration to tooling that reads it.

Reach for it when SLOs must be reviewable, diffable and portable, and when more than one observability backend is in play. The obvious limit is that a specification with no runtime does nothing on its own, so you still need a generator such as Sloth or a platform that consumes it, and cross-vendor adoption is uneven. Writing Prometheus rules directly is simpler when Prometheus is all you have.

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

It is a pipeline assembled from component types in YAML: receivers accept data over OTLP, Prometheus scrape, Jaeger, Zipkin, syslog or host metrics, processors transform it in flight with batching, memory limiting, attribute editing and redaction, tail sampling and filtering, connectors bridge one pipeline into another, and exporters ship results to one or many backends. The same binary runs as a per-node agent and as a central gateway.

The reason to run it rather than exporting straight from the SDK is that everything you will want to change later — sampling rate, redaction, which backend, sending to two during a migration — becomes configuration outside the application. The costs are another service to size and keep available, tail sampling requiring all spans of a trace to reach one instance, and a contrib distribution large enough to warrant trimming.

### OpenTelemetry for LLM Apps
**Short:** OpenTelemetry's GenAI semantic conventions, giving prompts, tool calls and agent hops standard span attributes.
**Kind:** spec
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1

The generative-AI semantic conventions fix the attribute names for this domain: the operation, the system and the model requested and returned, input and output token counts, request parameters such as temperature, and tool names and call identifiers, plus how prompt and completion content is recorded. Agent and workflow spans supply the nesting, so a span emitted by one library means the same thing as one from another.

Standard names are what let a backend build a cost or latency view without knowing which SDK produced the spans, and what stops every vendor inventing a private schema. Reach for them whenever instrumenting something LLM-shaped by hand. The caveat is maturity, since parts of these conventions are still moving and attribute names can change between versions, and content capture is opt-in precisely because it is sensitive.

### OpenTelemetry Logs
**Short:** The OpenTelemetry log data model and OTLP transport, correlating log records with trace and span ids.
**Kind:** spec
**Lang:** *
**Roles:** observability/logging @1, observability/tracing-apm-and-llm-observability @2

The log data model is a record with a timestamp, severity number and text, a body of arbitrary structure, resource and scope attributes, and — the point of the exercise — the trace and span id of the execution that emitted it. Existing frameworks are bridged rather than replaced, so an appender feeds records from the standard library, Logback or Log4j 2 into the OTel pipeline and they gain correlation for free.

The payoff is one transport and one resource model across all three signals, so a collector can filter, redact and route logs exactly as it does spans, and a backend can jump from a trace to the lines it produced. It arrived after tracing and metrics and its support is younger, so the pragmatic pattern is often a dedicated log shipper alongside OTLP for the other two signals.

### OpenTelemetry SDK/API
**Short:** Vendor-neutral instrumentation SDK generating spans, metrics and logs, auto or manual, exported over OTLP.
**Kind:** tech
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2, observability/logging @2

The split is the design. The API artifact holds the tracer, meter, logger and propagator interfaces, resolves to no-ops when nothing is installed, and is what a library depends on; the SDK holds the machinery an application configures, meaning resource detection, samplers, span processors, metric readers and views, and exporters. Auto-instrumentation agents install and configure the SDK from outside, which is how a service is traced without a code change.

Reach for the API in shared code and the SDK exactly once, in the application. What you own afterwards is sampling — head sampling in the SDK is cheap and blind, tail sampling in the collector sees the whole trace and costs infrastructure — and attribute cardinality, since unbounded values are what make a backend expensive. Manual spans are for business logic; protocol spans come from instrumentation packages.

### opentelemetry-exporter-otlp
**Short:** OpenTelemetry exporter shipping spans and metrics over OTLP to Jaeger, Tempo, Honeycomb or Datadog.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2

It serialises spans, metrics and log records into OTLP and sends them over gRPC or over HTTP with protobuf, depending on which artifact you configure, to an endpoint set by environment variable with headers for authentication. A production setup pairs it with a batch span processor so export happens on a background thread in batches, through a bounded queue that drops when the backend is slow rather than blocking the application.

Because the protocol is the vendor-neutral one, the same exporter reaches a collector, Tempo, Jaeger, Honeycomb or a commercial backend, and changing destination is an endpoint change. The usual advice is to export to a local collector rather than straight to a vendor, so retries, redaction and routing are not compiled into the application. Watch the queue and dropped-span counters, where an undersized export path shows up.

### opentelemetry-instrumentation-fastapi
**Short:** OpenTelemetry instrumentation that emits HTTP server spans for FastAPI with no code changes.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1

Instrumenting the app object, or running it under the auto-instrumentation launcher, adds ASGI middleware that starts a server span per request, extracts an incoming `traceparent` so the span joins an existing trace, and sets the standard HTTP attributes for method, route and status. Naming spans by the route template rather than the concrete path is what keeps span-name cardinality bounded on a busy service.

Reach for it as the first piece of tracing on a FastAPI service, and pair it with the client and database instrumentations, since a server span alone says a request was slow without saying which downstream call caused it. Excluding health and metrics endpoints is worth doing or probes dominate trace volume, and background tasks fall outside the request span unless you propagate context into them yourself.

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

It defines protobuf message shapes for traces, metrics and logs plus two transports for them: gRPC, and HTTP carrying either protobuf or JSON. The encoding is deliberately shared across signals and grouped by resource, so one export carries many spans sharing a service identity, and the specification covers retry and throttling behaviour so a backend can push back instead of being hammered by a client that will not slow down.

Its importance is as much political as technical, because a single wire format is what makes the instrumentation-and-backend decoupling real, and every serious backend now accepts it. Reach for it as the default egress from an application or a collector. It is a transport rather than a storage or query format, so a backend still stores whatever suits it and there is no query side to the protocol.

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

Both do the same three things: hold the schedule answering who is on call for this service right now, run an escalation policy that keeps notifying and then escalates when nobody acknowledges, and deduplicate a stream of alerts into incidents so a flapping check yields one page instead of a hundred. Monitoring systems integrate by posting events with a routing key and a deduplication key they choose.

Reach for one as soon as an alert must wake a specific human with a guarantee, which chat cannot provide. The design work is not in the tool: every page must be actionable and tied to a symptom, every alert needs a runbook link, and page volume itself deserves tracking, because a rotation that has learned to ignore the phone is the failure mode both products enable. Grafana OnCall is the self-hosted option.

### Per-layer profiling
**Short:** Reading per-operator execution timings from an inference runtime, e.g. OpenVINO's InferRequest.get_profiling_info().
**Kind:** concept
**Lang:** *
**Roles:** observability/profiling-and-performance @1, inference/compiler-and-runtime-optimization @3

Inference runtimes can report time spent in each operator of a compiled graph, returning per-layer wall time, the execution type actually chosen, and whether a layer was fused away or fell back to a slower implementation. Sorted by duration, that list is the model's own flame graph, and it is the only view that distinguishes a slow model from a model containing one badly handled operator.

Reach for it when a model runs slower than its arithmetic suggests, since the usual findings are an unsupported operator forcing a fallback, a layout conversion inserted between two kernels, or a fused block that did not fuse. Profiling adds per-layer instrumentation and slightly changes scheduling, so use the numbers comparatively. It says nothing about batching or data loading, which is where end-to-end latency often actually goes.

### Percona Monitoring
**Short:** Percona Monitoring and Management: dashboards and query analytics for MySQL, PostgreSQL and MongoDB fleets.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, observability/profiling-and-performance @2, data-stores/relational @3

It is a packaged stack rather than a new product: agents on each database host feed exporters into a bundled time-series store with Grafana dashboards on top, plus query analytics that collects normalised statements from Performance Schema, the slow log or `pg_stat_statements` and ranks them by total time with plans and examples. Advisor checks flag configuration and security problems against the same inventory.

The reason to run it instead of assembling exporters yourself is the curated dashboards and the query analytics, which are the two database-specific parts that take longest to build. Reach for it for a fleet of MySQL, PostgreSQL or MongoDB servers you operate. It is another stateful service to run and upgrade and it duplicates a metrics stack you may already have; pganalyze and the cloud insights products are hosted alternatives.

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

Paste a plan, ideally produced with analyze, buffers and JSON format, and it draws the node tree with each node's exclusive and inclusive duration, its share of the total, the ratio of actual to estimated rows, buffer hits and reads, and badges marking the slowest node, the worst estimate and the largest row source. It is a browser component, so it runs locally and can be self-hosted or embedded in a tool.

Reach for it over the hosted depesz renderer when the plan is confidential, since nothing has to leave your machine, or when you prefer the graphical tree to a table. It shows what is in the plan and no more: without buffers there is no I/O attribution, and without analyze there are only estimates. Reading it well still requires knowing why a row estimate went wrong.

### pg_activity
**Short:** top-style terminal dashboard over pg_stat_activity showing live PostgreSQL sessions, waits and blocking queries.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @2, observability/metrics-and-monitoring @3

It refreshes the activity and statistics views on an interval and renders them like `top`: running, idle-in-transaction and waiting sessions with their query text, duration, wait event and blocking backend, above a header showing transactions per second, cache hit ratio and I/O. Keys switch between running, waiting and blocking views, and a backend can be cancelled or terminated from the interface.

Reach for it while a database is misbehaving right now, because it shows the state and the culprit together: the long idle-in-transaction session holding back vacuum, the lock chain, the query that has been running for forty minutes. It is a live view with no history, so it says nothing about a problem that ended an hour ago; `pg_stat_statements` and a monitoring stack cover that ground.

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

A collector runs near the database, reads `pg_stat_statements`, the schema catalogues, activity views, logs and, where available, execution plans, and sends them to the hosted service. What that buys over a metrics dashboard is history per normalised query: plan changes over time, index and buffer behaviour, wait event breakdowns, log events correlated to statements, and an index advisor that proposes indexes from the real workload and explains its reasoning.

Reach for it when Postgres performance work is continuous rather than incidental and building the tooling around `pg_stat_statements` yourself is not a good use of time. It is a paid service and the collector sends query fingerprints and schema information off-site, which is the question a review will ask first. Percona Monitoring self-hosted, or the cloud providers' own insights views, cover part of the same ground.

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

Kubernetes defines aggregated metrics APIs, and this implements the custom and external ones: it registers as an API service, and a rules configuration maps a PromQL query onto an API resource, so when the horizontal pod autoscaler asks for a pod's requests per second the adapter runs the query and answers. Nothing else in the cluster needs to know the metric came from Prometheus.

Reach for it when CPU is the wrong scaling signal, which it usually is for a queue consumer or a latency-sensitive service that should scale on queue depth or requests per pod instead. The rules mapping is the fiddly part and silently produces no metric when the label joins are wrong. KEDA is the more common choice now for event-driven scaling and can use Prometheus as a scaler directly.

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

The division of labour is clean: Prometheus discovers targets, scrapes their metrics endpoints, stores samples locally and evaluates recording and alerting rules over them, while Grafana holds no data and issues PromQL queries to draw panels. Alerting can live on either side, as rules in Prometheus routed through Alertmanager or as Grafana-managed rules with their own routing, and picking one deliberately avoids two half-configured systems.

Reach for the pair as the default self-hosted metrics stack, since the exporter ecosystem means most infrastructure is already instrumented and in Kubernetes a single chart installs both alongside Alertmanager and the standard exporters. What you still own is cardinality discipline and, once retention or a cross-cluster view becomes the constraint, a long-term backend such as Thanos, Mimir or VictoriaMetrics underneath.

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

One call at startup adds the middleware and mounts the endpoint, registering request counts, a latency histogram and an in-progress gauge labelled by method, templated route and status, exposed for scraping. Extra metrics are added as small functions that receive each request's info, and the path label comes from the route template rather than the concrete URL, which is what keeps series count bounded.

Reach for it to get rate, error and duration signals on a FastAPI service in a line rather than hand-writing counters. Two operational details matter: under multiple worker processes each has its own registry, so you need multiprocess mode with a shared directory or per-worker scraping, and the default histogram buckets rarely match your latency profile, so set them or the resulting percentile will be useless.

### prometheus_client
**Short:** Official Python Prometheus client: counters, gauges, histograms and a /metrics endpoint for the pull model.
**Kind:** tech
**Lang:** python
**Roles:** observability/metrics-and-monitoring @1

It provides the four metric types as objects registered in a collector registry and rendered in the text exposition format by a bundled HTTP server or an ASGI and WSGI app. Decorators and context managers time a block, a labels call produces the child series for a label combination, and custom collectors let you expose numbers fetched at scrape time rather than tracked continuously.

Reach for it for any Python service Prometheus will scrape. Two things trip people up: multiprocess servers, where each worker holds its own counters and the multiprocess collector backed by a shared directory is required, and labels taken from user input, which create a series per value that never expires for the process's lifetime. A batch job with nothing to scrape pushes to a Pushgateway instead.

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

It is the Prometheus binary's companion CLI: config and rule checks validate files before a reload, a rule unit-test mode lets you declare input series over time and assert which alerts fire and with what labels, instant and range query subcommands run PromQL against a server, and a block analyser reports the highest-cardinality metrics and label values sitting in local storage.

Reach for it in CI, because alerting rules are code that is otherwise only tested by an outage, and a rule that never fires looks identical in review to one that works. The block analyser addresses the other recurring problem, finding which metric is responsible when memory climbs. It validates syntax and semantics rather than judgement, so it cannot tell you the alert was a bad idea.

### pt-duplicate-key-checker
**Short:** Percona tool finding duplicate and redundant MySQL indexes already covered by another index's leading columns.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-access/schema-and-migration @2, data-stores/relational @3

It reads table definitions from a live server or a dump and reports redundant indexes: one whose columns form a leftmost prefix of another, duplicates differing only in name, and indexes already covered by the primary key, printing the statements that would drop them. Foreign key constraints whose supporting index is duplicated elsewhere are reported in the same pass.

Redundant indexes are pure cost, since every write maintains them, they occupy buffer pool that could hold data, and they widen the optimiser's search space, so this is a cheap win on a schema that has accumulated years of ad-hoc additions. Read the output rather than applying it, because a prefix index can still be justified by covering or ordering needs and dropping one used by a rare critical query is a bad trade.

### pt-query-digest
**Short:** Percona tool that aggregates the MySQL slow query log into ranked query fingerprints.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, data-stores/relational @3

Aggregation into fingerprints is the whole idea: a thousand log entries differing only in their literal values collapse into one row whose numbers describe the pattern, so the output ranks query shapes rather than individual executions. Filters restrict the analysis to a user, database or time range, and two reports can be diffed to show exactly what changed across a release.

The column to read is rows examined against rows sent, since a large ratio means a missing or unusable index regardless of how fast the query currently runs on a warm cache. Reach for it whenever you have a slow log and no idea where to start. It analyses only what was logged, so anything below the threshold is invisible, and it ships in the Percona Toolkit rather than with MySQL.

### Pushgateway
**Short:** Prometheus staging component where short-lived batch jobs push metrics that a scrape would otherwise miss.
**Kind:** tech
**Lang:** *
**Roles:** observability/metrics-and-monitoring @1, data-movement/task-queue-and-jobs @3

It is a metrics cache with a push API: a job posts to a URL identified by a job name and grouping labels, the gateway holds those values in memory, and Prometheus scrapes the gateway as an ordinary target. The values persist until overwritten or explicitly deleted, because the gateway has no idea whether the job that pushed them still exists or finished successfully.

Its one legitimate use is the service-level outcome of a batch job, meaning last success timestamp, records processed and duration. Everything else is a trap: sample timestamps are the scrape's rather than the job's, stale groups linger forever until deleted, and it becomes a single point of failure for everything pushing to it. An ordinary long-lived service should expose an endpoint and be scraped directly.

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

It samples the interpreter's stack on a short interval and aggregates the samples into a call tree annotated with time and percentage, rendered as text, HTML or a speedscope file. It records wall time rather than CPU time, so a request blocked on a database call shows the blocking frame instead of vanishing, and it understands `async` frames well enough to keep an awaited chain attached to the coroutine that began it.

That async awareness is why it beats `cProfile` on a web service, where instrumentation both distorts the timings it measures and cannot follow a task across an `await`. Overhead is a few percent, low enough to enable per request behind a flag. The tradeoff is resolution, since a function faster than the sampling interval may never appear, so it finds the dominant cost rather than supporting micro-analysis.

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

Wrapping a step in the profiler with CPU and CUDA activities enabled records both the Python and operator-level calls and, through the vendor profiling interface, the kernels those calls launched, then correlates the two, so each operator shows its host time, its device time and the kernels it produced. A schedule controls warmup and active steps, and the result exports to TensorBoard or a timeline viewer.

The timeline is what makes it worth the trouble, exposing gaps where the GPU idles while the host prepares work, kernels too small to fill the device, avoidable copies, and whether collectives overlap with compute. Reach for it before buying more GPUs. Profiling perturbs timing and produces large traces, so record a handful of steady-state steps rather than a whole run; Nsight Systems shows the same picture below the framework.

### reactor-addons
**Short:** Companion modules for Project Reactor, including metrics instrumentation and extra operators for reactive pipelines.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, runtime-systems/concurrency-and-async @2

It is a small set of companion modules to Project Reactor rather than one library: extra operators and adapters useful enough to maintain but not to put in the core, alongside the metrics integration that publishes Micrometer meters for subscription counts, request and element throughput, and per-stage timing on a sequence you have explicitly named and tagged.

Naming is the requirement people miss, since unnamed sequences produce anonymous meters that tell you nothing, so instrument deliberately at the boundaries you care about. Reach for it when a reactive pipeline is slow and you need to know which stage, because a stack trace in a non-blocking chain rarely says. Much of what once lived here has migrated into Reactor core over time, so check the core API before adding it.

### redis-benchmark
**Short:** Redis's bundled load generator measuring throughput and latency for chosen commands, pipeline depth and clients.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, devtools/testing-and-mocking @2, caching/distributed-cache @3

It ships with Redis and generates load with a configurable client count, request count, payload size, key space and pipeline depth, reporting throughput and a latency distribution per command tested. Restricting it to specific commands, setting pipeline depth, and randomising keys across a range are the three flags that matter, the last because otherwise you are benchmarking a single hot key sitting in the CPU cache.

Use it to characterise the deployment rather than to prove Redis is fast: what the network round trip costs from your client host, how much pipelining buys, what a realistic value size does to throughput. The defaults are misleading, with tiny values, no key randomisation and everything local, so untuned results get quoted far more often than they deserve. It cannot model your access pattern, which needs a workload replay.

### redis-memory-analyzer
**Short:** Scans a Redis instance and reports memory usage by key pattern, type and encoding to find what is actually filling RAM.
**Kind:** tech
**Lang:** *
**Roles:** observability/profiling-and-performance @1, caching/distributed-cache @2, data-stores/key-value-and-embedded @3

It walks the keyspace with `SCAN`, samples keys by pattern, and for each pattern reports key count, the memory actually consumed by keys and values, average and extreme sizes, the internal encoding chosen, and how much of the pattern carries a TTL. Grouping by pattern is the point, giving one row for a session key family rather than three million individual keys nobody can read.

Reach for it when memory is high and the server's own summary only confirms the total, because the answer is usually one pattern with no expiry, or a hash grown large enough to leave its memory-efficient encoding. Scanning a large instance takes time and adds load and the numbers are sampled estimates, so run it against a replica. `MEMORY USAGE` answers for one key and the big-keys scan is the cheap first look.

### Rootly
**Short:** Incident management platform automating declaration, roles, comms, timelines and retrospectives from chat.
**Kind:** tech
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

Declaring an incident from chat triggers a configured workflow: the channel and call bridge are created, roles assigned, a ticket and status-page update opened, the timeline populated automatically from events and pinned messages, and the retrospective generated from that timeline with action items pushed into the tracker. Services, teams and ownership come from a catalogue, so an incident attaches to the right owners without anyone choosing them.

Reach for it when incident response must be repeatable across teams and you want the paperwork to happen without a human remembering it at three in the morning. The value is the recorded timeline and the follow-through rather than the declaration itself. Like everything in this category it detects nothing, competes closely with incident.io and FireHydrant on workflow ergonomics, and fails if declaring an incident feels expensive.

### Runbook tooling
**Short:** The category of tools holding diagnosis and mitigation guides, linked from each alert so the on-call has a first step.
**Kind:** concept
**Lang:** *
**Roles:** observability/alerting-and-incident-response @1

A runbook is the document an alert links to: what this alert means, what to check first, which dashboard and which query, the known causes, the mitigation and its blast radius, and who to escalate to. The tooling is whatever holds it addressably, whether a wiki, markdown in the repository beside the alert rule, or an executable runbook whose diagnostic steps are scripted and runnable from the incident channel.

It matters because alerts fire at three in the morning to somebody who did not write the service, and an alert with no runbook link is a page that begins with reading source code. The cost is maintenance, since a runbook describing a system two rewrites ago is worse than none, which is the argument for keeping it next to the code and revising it after every incident that used it.

### SDK
**Short:** The OpenTelemetry API/SDK split: libraries depend only on the stable API while the SDK and exporters stay swappable.
**Kind:** concept
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, apis-frameworks/design-patterns-and-principles @3

Two artifacts, one contract. Instrumentation depends only on the API, which is small, stable and resolves to no-ops when nothing else is present, while the SDK supplies the implementation — resource detection, samplers, span processors, metric views and exporters — and only the application installs and configures it. A library can therefore be instrumented without imposing an observability vendor, a version, or any runtime cost on its consumers.

The consequences in practice are that switching backends becomes configuration rather than a re-instrumentation project, and that an auto-instrumentation agent can install the SDK into a process it did not compile. The cost is a startup obligation: nothing is exported until the SDK is configured, which is why the classic failure is an application that emits no telemetry at all while looking correctly instrumented.

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

Its centre is error capture: an unhandled exception is caught by an integration for your framework and the event carries the full stack with local variable values, the request, user and release context, and breadcrumbs describing what happened beforehand. The server groups events into issues by a fingerprint derived from the stack, so ten thousand occurrences of one bug appear as one issue with a count and a first-seen release.

It also does performance tracing and profiling, but grouping and release tracking are the reason to use it, because a regression is attributed to the deploy that introduced it. Reach for it as the thing that tells you an exception happened at all, since logs require somebody to be looking. Sampling controls cost, and local variables and request bodies will capture secrets unless scrubbing is configured deliberately.

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

They cover two different halves of notification and are routinely confused. A paging tool holds schedules, escalates when nobody acknowledges, and can prove a human took the alert, while chat is a broadcast surface with no acknowledgement, no escalation and no guarantee anyone is looking. Alertmanager routes by label to both, sending the urgent route to the pager and the informational route to a channel.

The pattern that works is exactly that split, plus a dedicated channel per incident where the pager's incident, deploy events, dashboards and the discussion collect into one timestamped record for the retrospective. The failure mode is routing everything to chat, after which the channel becomes unreadable, people mute it, and the one alert that mattered scrolls past. Anything that must wake somebody belongs in the paging tool.

### SLF4J
**Short:** Java logging facade: code against slf4j-api and bind whichever backend (Logback, Log4j2) is on the classpath.
**Kind:** tech
**Lang:** java
**Roles:** observability/logging @1, apis-frameworks/design-patterns-and-principles @3

SLF4J ships an API and nothing else. Your code calls `LoggerFactory.getLogger(...)` against `slf4j-api`, and at runtime whichever binding is on the classpath — Logback, Log4j 2 through its adapter, java.util.logging — supplies the implementation. That is why every library logs through it: a library must not impose a logging backend on the application that embeds it, and this is the facade that lets the application decide. Bridge jars redirect calls made to older logging APIs into the same pipeline, so a dependency tree with four logging frameworks still produces one stream.

Always log with parameterized messages rather than string concatenation, so argument formatting is skipped entirely when the level is disabled. The classic startup problem is two bindings on the classpath, which SLF4J warns about and then picks one arbitrarily.

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

Configured as a connector in the collector, it sits between a traces pipeline and a metrics pipeline: every span passing through increments a request counter and observes a latency histogram, with dimensions taken from the span itself — service name, span name, kind, status code, and any attributes you list explicitly. The output is ordinary metrics, exported to Prometheus or anywhere else metrics go.

It exists so you can sample traces aggressively and still hold exact rate, error and duration figures, because sampled traces produce misleading counts while unsampled traces are expensive. Reach for it when tracing is already in place and instrumenting metrics separately is duplicated work. The hazard is dimensions, since adding a high-cardinality span attribute multiplies series and is the standard way a tracing pipeline overwhelms a metrics backend.

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

### spring-boot-actuator-autoconfigure
**Short:** Spring Boot module auto-configuring Actuator endpoints (health, metrics, env, info) from classpath and properties.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, apis-frameworks/dependency-injection-and-config @2, observability/alerting-and-incident-response @3

It is the conditional wiring layer: a large set of auto-configuration classes creating endpoint beans, health contributors and metric bindings only when their trigger is present, so a data source yields a database health check, a Micrometer Prometheus registry yields a scrape endpoint, and a migration tool on the classpath yields a migrations endpoint. Management properties then decide exposure, enablement and the base path.

It matters when something you expected did not appear, or something you did not want did. The debugging move is the auto-configuration report, which lists each candidate as matched or not with the reason, turning a mysteriously missing endpoint into a stated condition. You rarely depend on this artifact explicitly, since the starter brings it in alongside the actuator core and the metrics facade.

### spring-boot-starter-actuator
**Short:** Spring Boot starter exposing health, info, metrics and env endpoints and auto-configuring Micrometer instrumentation.
**Kind:** tech
**Lang:** java
**Roles:** observability/metrics-and-monitoring @1, observability/alerting-and-incident-response @3, observability/tracing-apm-and-llm-observability @3

The starter is a dependency aggregation: adding it brings the actuator core, its auto-configuration and the Micrometer core, at which point the application gains the management endpoints with health exposed and a meter registry already instrumenting HTTP server requests, the connection pool, caches, JVM memory, garbage collection and threads. Adding a registry artifact alongside it turns those meters into a scrape endpoint.

This is the dependency you actually add, one line for health probes and metrics, which is why liveness and readiness groups map onto Kubernetes probes with no code at all. Two defaults deserve a decision: only health is exposed over HTTP, so the exposure property is what you edit, and publishing that list on the application's own port puts privileged endpoints on the public listener.

### spring-modulith-observability
**Short:** Spring Modulith module that instruments cross-module calls and events so traces and metrics show the module boundaries.
**Kind:** tech
**Lang:** java
**Roles:** observability/tracing-apm-and-llm-observability @1, observability/metrics-and-monitoring @2

Spring Modulith derives an application's module boundaries from its package structure, and this module instruments the crossings: a call into another module's exposed component and an application event published across a boundary are wrapped in an observation, so a span appears named for the module and the meter registry gains per-module timings. The business code itself is untouched.

The point is that a modular monolith's traces otherwise look like one flat process, hiding exactly what separate services would have given you free — which module is slow, which calls which, whether an event listener is the latency. Reach for it while deciding whether a module should become a service. It measures boundaries only, so intra-module work still needs ordinary profiling, and every crossing now costs an observation.

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

A synthetic check is a scripted transaction run on a schedule from outside the system: an HTTP request with assertions, a multi-step API flow, or a headless browser walking login and checkout, measuring availability and duration from a fixed vantage point. Because the traffic is generated rather than observed, the signal exists at three in the morning when no real user is on the site, and it exercises paths that are critical but rare.

Reach for them for the outside-in availability indicator and for the journeys whose failure costs most, since they catch DNS, certificate and CDN problems internal instrumentation cannot see. The limits are coverage and honesty: a few scripted paths are not your user base, the checks are real traffic and real test data in production, and browser scripts break on UI changes. Real-user monitoring is the complement.

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

It is a single Go binary configured in TOML with four plugin classes: inputs that gather from system counters, databases, brokers, network devices and executed scripts, processors and aggregators that transform or roll up in flight, and outputs that write to a time-series database, Prometheus, Kafka or cloud services. Every point carries a measurement name, tags and fields, which is the InfluxDB data model.

The breadth of the plugin set is the reason to pick it, because an appliance speaking only SNMP or a database with no exporter usually already has an input written. Reach for it when the collection targets are heterogeneous or the backend is InfluxDB. Against Prometheus it is a push agent in a pull ecosystem, and the OpenTelemetry Collector now covers much of the same ground with a more standard data model.

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

OpenLLMetry is the open-source instrumentation half and this is the hosted backend for it: initialising the SDK emits OpenTelemetry spans for model calls, vector store queries, tool invocations and agent steps, and the platform stores them with token counts and computed cost, adding evaluation and monitoring built on the same span stream. Because the wire format is OTLP, the instrumentation is not tied to this backend.

That portability is the honest reason to start here, since you can adopt the instrumentation, point it at an existing tracing backend, and change your mind about the platform later. Reach for it when you want LLM-specific views without inventing a span schema. As with every LLM tracer the decision that matters is whether prompt and completion text is captured and where it lands; self-hosted Langfuse is the alternative.

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

It is a dynamic binary instrumentation framework rather than a single checker: the program is translated into an intermediate representation, a tool instruments it, and it is recompiled and run, which is why every memory access can be checked. Memcheck tracks the definedness and addressability of every byte and reports invalid reads and writes, uninitialised values, bad frees and leaks with stacks, while other tools find data races or profile.

The exactness is the point: it finds the out-of-bounds read that was harmless today and becomes a crash next month. The price is a slowdown of ten to fifty times and serialised execution, so it belongs in CI on a representative test rather than under production load. AddressSanitizer costs roughly twice the runtime instead and is the usual first line, with Valgrind kept for what compiled instrumentation misses.

### valgrind --tool=cachegrind
**Short:** Valgrind cache simulator reporting L1/LL miss rates per source line, for locality tuning without PMU counters.
**Kind:** tech
**Lang:** cpp
**Roles:** observability/profiling-and-performance @1, runtime-systems/memory-processes-and-os @2

Cachegrind simulates a first-level instruction and data cache and a last-level cache modelled on the machine's real geometry, counting every access, miss and branch prediction outcome and attributing them down to individual source lines. A companion annotator prints the marked-up source, and a diff tool compares two runs, which is how you demonstrate that a loop interchange or a data layout change genuinely improved locality.

Because it is a simulation the results are deterministic and reproducible, and they need no performance-counter permissions, which is useful in a container or on shared CI hardware where `perf` is unavailable. What it cannot model is the real processor, since prefetching, out-of-order execution and memory-level parallelism are absent, so treat the miss rates as relative evidence and confirm the wall-clock win on real hardware.

### Vector
**Short:** High-performance Rust observability pipeline collecting, transforming with VRL, sampling and routing telemetry.
**Kind:** tech
**Lang:** *
**Roles:** observability/logging @1, observability/metrics-and-monitoring @2

It models a pipeline as sources, transforms and sinks in a typed configuration, with a purpose-built remap language doing the transformation work: parsing, reshaping, enriching, redacting and dropping events in a small expression language that is compiled and checked before the pipeline starts, so a bad transform fails at boot rather than at 3am. It handles logs, metrics and traces, and buffers to disk with acknowledgement.

The reasons to pick it over Fluent Bit or Logstash are throughput per core and that up-front configuration validation. Reach for it as the aggregator where volume reduction happens — sampling, dropping noisy fields, routing cheap data to object storage and only what gets searched into an indexed store — which is where a logging platform's cost is decided. Its remap language is another thing to learn, and its plugin catalogue is smaller.

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

It records entry and exit for every function call rather than sampling, writing a trace file rendered in a bundled viewer as a timeline where each call is a bar on its thread or task row, so you see order and duration instead of aggregate totals. Argument and return values can be attached, and it understands threads, multiprocessing and async tasks, giving each its own row.

Reach for it when the question is sequencing — why did this happen before that, where is the gap, which task was waiting — which a flame graph of aggregated time cannot answer. Tracing every call costs runtime and produces large files quickly, so use filters, an entry limit or the sparse mode on anything long-running. For the hot function over a real workload, a sampling profiler is cheaper and more honest.

### W&B Weave
**Short:** Weights & Biases' LLM tracing, prompt-versioning and evaluation product.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @2, ml-lifecycle/experiment-tracking-and-tuning @2

Decorating a function marks it as a traced operation, after which every call is recorded with its inputs, outputs, exceptions, latency, token counts and cost, nested into a call tree that follows your code rather than a framework's abstractions, with the code version captured alongside. Common model clients are patched automatically, so raw provider calls are traced without decorating anything at all.

Reach for it when Weights and Biases already holds training runs and artifacts and you want LLM traces under the same project and access control. The evaluation side pairs a versioned dataset with scorers to produce comparable runs instead of impressions. It is a hosted platform, so prompts and outputs leave your infrastructure unless you have an enterprise deployment, and an OpenTelemetry tracer is the portable alternative.

### W3C Trace Context
**Short:** W3C standard defining the traceparent/tracestate HTTP headers that carry trace and span ids across service boundaries.
**Kind:** spec
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, apis-frameworks/data-formats-and-api-contracts @3

The recommendation defines two headers. The first has a fixed format carrying a version, a thirty-two-character trace id, a sixteen-character parent span id and flags whose low bit is the sampling decision; the second is a size-capped ordered list of vendor-keyed values letting each system carry its own opaque state without breaking the others. Equivalent definitions exist for other transports, so a message queue carries them the same way.

It exists because before it every tracing vendor had its own header and a request crossing between two of them simply lost its trace. Reach for it as the default propagation format, which OpenTelemetry already uses. All it asks of proxies, gateways, queue clients and thread pools is that they forward the headers, and a component that silently strips them is the usual reason a trace comes back in fragments.

### W3C traceparent
**Short:** W3C Trace Context header carrying trace id, span id and flags so a trace continues across service boundaries.
**Kind:** spec
**Lang:** *
**Roles:** observability/tracing-apm-and-llm-observability @1, apis-frameworks/data-formats-and-api-contracts @3

The value is four dash-separated fields: a two-digit version, the trace id shared by every span in the request, the span id of the caller which becomes the parent of the next span, and two hex digits of flags. A receiver that understands the header continues the trace, while one that does not simply starts a new one, which is why an uninstrumented hop yields two disconnected traces rather than an error.

The sampled flag is the subtle part, because it carries the caller's decision, so a downstream service that honours it keeps a trace complete while one that decides independently produces traces with holes. Set the propagation and sampling policy once at the edge. It is also worth verifying that gateways and CDNs forward the header, since a header allowlist drops it and breaks tracing invisibly.

### Weave
**Short:** Weights & Biases' LLM product: traces every agent call and scores traces against datasets and custom evaluators.
**Kind:** tech
**Lang:** python
**Roles:** observability/tracing-apm-and-llm-observability @1, ml-lifecycle/evaluation-and-benchmarks @2

Weave records LLM calls as a tree of operations with their inputs, outputs, latency, tokens and cost, and versions everything it stores — datasets, scorers, prompt objects and the code of the traced function — so a result is always attributable to a specific version of whatever produced it. Objects are content-addressed, which is what makes two evaluation runs directly comparable rather than merely adjacent.

That versioning is the distinguishing feature against a pure tracer, and the workflow follows from it: curate a dataset from production traces, define scorers, run an evaluation, compare it with the last one. Reach for it when prompt and model changes need evidence rather than an impression. It is hosted and tied to the Weights and Biases account model, which decides both the cost and where trace content lives.

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

It is a C-implemented deterministic profiler aware of the things `cProfile` is not: it profiles all threads rather than only the one that started it, and a wall-clock mode measures elapsed time as well as CPU time, so a coroutine blocked on I/O is attributed instead of disappearing. Coroutine profiling groups the separate resumptions of one `async` function into a single entry with its true total duration.

That is what makes it usable on a threaded or asyncio server, where `cProfile` gives a misleading picture of a single thread's CPU. The comparison worth running is CPU time against wall time per function, where a large gap means waiting and a small one means computation. It instruments every call, so overhead is substantial and it belongs in a load test; py-spy and Pyinstrument sample instead.

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

Brave produces finished spans and this artifact carries them to a collector: an asynchronous reporter buffers spans in a bounded queue and a background thread flushes them in batches through a sender, whether that posts JSON over HTTP to the collector's span endpoint or writes to Kafka, so the request thread never waits on the trace backend and a slow collector drops spans rather than the application stalling.

What matters operationally is the queue — its size, the message timeout, and the reporter's own dropped-span counters, which are the only place an undersized export path announces itself. Reach for it in an estate already running Brave and Zipkin, typically through the Micrometer Tracing Brave bridge. New systems generally use the OpenTelemetry SDK with an OTLP exporter, which Zipkin can still receive through a collector.
