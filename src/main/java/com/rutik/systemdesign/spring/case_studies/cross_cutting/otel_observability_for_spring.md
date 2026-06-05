# OpenTelemetry Observability for Spring Services

> **You cannot fix what you cannot see.**  
> A Spring service in production emits three observability signals — metrics, traces, and logs.
> OpenTelemetry provides the vendor-neutral protocol (OTLP) and semantic conventions that
> unify all three into a correlated view: the same trace ID appears in the trace span, the
> metric exemplar, and the structured log line.

---

## 1. Concept Overview

OpenTelemetry (OTel) is a CNCF project that defines:
- **API** — language interfaces for creating spans, metrics, and log records
- **SDK** — configurable implementations (samplers, exporters, propagators)
- **Protocol (OTLP)** — gRPC/HTTP protocol for exporting signals to a collector or backend
- **Semantic Conventions** — standard attribute names (`http.method`, `db.system`, etc.)

In the Spring ecosystem, observability is layered:
- **Micrometer** — metrics abstraction (timers, counters, gauges) — maps to OTel metrics
- **Micrometer Tracing** — tracing abstraction — wraps OTel SDK or Brave (Zipkin)
- **Micrometer Observation API** (Spring Boot 3.2+) — unified API that emits metrics + traces +
  log events from a single `Observation` object
- **Spring Boot Actuator** — exposes `/actuator/metrics`, health endpoints, and wires
  Micrometer into the app

---

## 2. Intuition

Think of observability as a **flight recorder** for your service. Three tapes:

- **Metrics tape** — aggregated numbers over time: "2,345 requests/minute, P99 = 48ms"
- **Traces tape** — per-request journey: "request 7a3f... took 231ms; 180ms in DB query"
- **Logs tape** — text events with context: "ERROR processing order 456 [traceId=7a3f...]"

Without correlation (the `traceId` in all three), you know *something* is wrong from metrics,
but you cannot find *which* request or *why* from traces, and the log lines are unsearchable
noise. W3C `traceparent` is the shared key that links all three tapes.

**Key insight:** Instrumentation that does not propagate `traceparent` across async boundaries
(Kafka messages, `@Async` tasks, `CompletableFuture`) creates orphaned traces — spans that
float unconnected. Every async handoff must explicitly carry the context.

---

## 3. Core Principles

### 3.1 The three pillars and their data models

| Signal | Model | Spring/OTel class | Backend storage |
|--------|-------|------------------|-----------------|
| **Metrics** | Instruments: Counter, Gauge, Histogram, Summary | `MeterRegistry`, `Counter`, `Timer`, `DistributionSummary` | Prometheus, OTel Metrics OTLP |
| **Traces** | Spans forming a tree; parent-child via `traceparent` | `Tracer`, `Span`, `SpanContext`, `Baggage` | Jaeger, Zipkin, OTel OTLP |
| **Logs** | Structured records with `traceId` + `spanId` correlation | SLF4J MDC (`traceId`, `spanId`) + JSON appender | Loki, Elasticsearch, OTel Logs OTLP |

### 3.2 W3C `traceparent` header

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
             ^^  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^  ^^
             ver         trace-id (128-bit hex)     parent-id (64-bit)  flags
```

Every service that receives an HTTP request extracts `traceparent`, creates a child span with
the same `traceId`, and propagates the updated header to downstream calls. This chains the
entire distributed request into a single trace tree.

### 3.3 Exemplars — linking metrics to traces

An exemplar is a specific trace ID embedded in a metric data point. When Prometheus scrapes
a histogram bucket that includes an exemplar, Grafana can show a button "View trace" next to
any P99 spike — jumping directly from the aggregate metric anomaly to the specific slow request.

---

## 4. Dependencies and Configuration

### 4.1 Maven dependencies (Spring Boot 3.2+)

```xml
<!-- Micrometer Observation + OTel bridge -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OTel SDK + OTLP exporter -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- Prometheus metrics endpoint -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Logback structured logging with traceId/spanId in MDC -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### 4.2 `application.yaml`

```yaml
management:
  tracing:
    sampling:
      probability: 0.1           # 10% sample rate in production; 1.0 in dev
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics
  prometheus:
    metrics:
      export:
        enabled: true             # /actuator/prometheus endpoint
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics

spring:
  application:
    name: order-service           # becomes service.name in OTel spans

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### 4.3 Logback JSON configuration

```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- Include traceId + spanId from MDC in every log line -->
            <provider class="net.logstash.logback.composite.loggingevent.MdcJsonProvider"/>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>
</configuration>
```

Micrometer Tracing automatically populates MDC with `traceId` and `spanId` when a span is
active. Every log statement inside a traced request carries the correlation IDs automatically.

---

## 5. Architecture Diagrams

### OTel signals flow in a Spring service

```
Inbound HTTP request (traceparent: 00-abc...-def...-01)
        |
        v
+--------------------------------------+
|  Spring MVC DispatcherServlet        |
|  OTel instrumentation auto-creates   |
|  child Span(traceId=abc, parentId=def|
+--------------------------------------+
        |
        v
+--------------------------------------+   +------------------+
|  Service Layer                       |   |  MDC             |
|  @Observed / Observation.start()     |-->|  traceId=abc...  |
|  Emits: timer, span, log events      |   |  spanId=xyz...   |
+--------------------------------------+   +------------------+
        |                  |
        v                  v
+-------------+    +--------------------+
|  DB call    |    |  Kafka produce     |
|  OTel JDBC  |    |  traceparent in    |
|  auto-spans |    |  message headers   |
+-------------+    +--------------------+
        |
        v
+--------------------------------------------------+
|  OTel SDK: BatchSpanProcessor                    |
|  → OTLP exporter → OTel Collector               |
+--------------------------------------------------+
             |                 |
             v                 v
       Jaeger/Tempo         Prometheus/Grafana
       (trace spans)        (metrics + exemplars)
```

### Trace tree for a distributed order request

```
[order-service] HTTP POST /orders  (231ms)
  ├── [order-service] OrderService.createOrder  (229ms)
  │     ├── [order-service] SELECT FROM inventory  (12ms)  <- JDBC auto-span
  │     ├── [order-service] INSERT INTO orders  (18ms)
  │     └── [order-service] Kafka produce order.created  (2ms)
  │
  └── [payment-service] (started by Kafka consumer)  (195ms)
        ├── [payment-service] PaymentService.charge  (193ms)
        │     └── [payment-service] HTTP POST /stripe/charges  (180ms)
        └── [payment-service] Kafka produce payment.completed  (3ms)
```

This tree is assembled by Jaeger/Tempo using the shared `traceId` propagated through Kafka
message headers.

---

## 6. How It Works — Detailed Mechanics

### 6.1 `@Observed` — zero-boilerplate span + metric creation

```java
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @Observed(
        name = "order.creation",           // metric name: order.creation.seconds (timer)
        contextualName = "create-order",   // span name in traces
        lowCardinalityKeyValues = {
            "payment.provider", "stripe"   // span tag + metric label (low cardinality)
        }
    )
    public Order createOrder(CreateOrderRequest request) {
        // Micrometer Observation wraps this method call:
        // 1. Starts a span (inherits current traceId from MDC)
        // 2. Starts a Timer.Sample
        // 3. On return: records Timer, closes span
        // 4. On exception: marks span with error tag + closes span
        return doCreate(request);
    }
}
```

`@Observed` requires `ObservedAspect` bean:
```java
@Configuration
public class ObservabilityConfig {
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
```

---

### 6.2 Manual `Observation` — for complex multi-step operations

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Service
public class PaymentService {
    private final ObservationRegistry registry;

    public ChargeResult charge(PaymentRequest request) {
        Observation obs = Observation.createNotStarted("payment.charge", registry)
            .lowCardinalityKeyValue("gateway", "stripe")
            .highCardinalityKeyValue("order.id", request.orderId())  // NOT a metric label
            .start();

        try (obs) {
            ChargeResult result = stripeClient.charge(request);
            obs.lowCardinalityKeyValue("status", result.status());
            return result;
        } catch (PaymentException e) {
            obs.error(e);     // marks span as error + records exception event
            throw e;
        }
    }
}
```

**Low vs high cardinality key values:**
- `lowCardinalityKeyValue` — safe as metric labels (bounded set: "success", "failure", "timeout")
- `highCardinalityKeyValue` — added to trace span only (unbounded: order IDs, user IDs)

Mixing them causes label explosion in Prometheus — a `Counter` with 10M distinct label
combinations creates 10M time series and crashes Prometheus.

---

### 6.3 Propagating trace context across async boundaries

**Broken — context lost in `@Async`:**
```java
// BROKEN: Spring @Async creates a new thread; traceId not propagated
@Async
public void processEventAsync(Event event) {
    // This method runs in a ThreadPoolTaskExecutor thread.
    // Micrometer's ThreadLocal-based trace context is NOT carried over.
    // Log lines here have no traceId — orphaned from the parent request.
    log.info("Processing event {}", event.id());   // traceId = empty!
}
```

**Fixed — context propagated via `ContextExecutorService`:**
```java
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Autowired
    private ObservationRegistry observationRegistry;

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        executor.initialize();

        // Wrap the executor to propagate OTel context to async threads
        return new ContextPropagatingTaskDecorator(executor);
    }
}

// ContextPropagatingTaskDecorator (available in Micrometer Tracing 1.2+):
public class ContextPropagatingTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture current OTel context (traceId, spanId, Baggage) in calling thread
        Context currentContext = Context.current();
        return () -> {
            // Restore context in the async thread
            try (Scope ignored = currentContext.makeCurrent()) {
                runnable.run();
            }
        };
    }
}
```

---

### 6.4 Propagating trace context through Kafka

```java
// Producer: inject traceparent into Kafka message headers
@Component
public class OrderEventPublisher {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final Tracer tracer;

    public void publish(OrderEvent event) {
        ProducerRecord<String, OrderEvent> record =
            new ProducerRecord<>("order.events", event.orderId(), event);

        // Inject OTel context as W3C traceparent header
        TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
        propagator.inject(Context.current(), record.headers(),
            (headers, key, value) -> headers.add(key, value.getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(record);
    }
}

// Consumer: extract traceparent and create child span
@Component
public class OrderEventConsumer {
    private final Tracer tracer;

    @KafkaListener(topics = "order.events", groupId = "payment-service")
    public void consume(ConsumerRecord<String, OrderEvent> record) {
        // Extract OTel context from Kafka headers
        TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
        Context extractedContext = propagator.extract(Context.current(), record.headers(),
            (headers, key) -> {
                Header header = headers.lastHeader(key);
                return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
            });

        // Start child span under extracted parent
        Span span = tracer.spanBuilder("order.event.consume")
            .setParent(extractedContext)
            .setAttribute("messaging.kafka.topic", "order.events")
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            processEvent(record.value());
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

Spring Kafka 3.x with `io.micrometer:micrometer-tracing-bridge-otel` auto-instruments Kafka
consumers — the above manual propagation is only needed for custom or legacy listener code.

---

### 6.5 Custom Micrometer metrics with exemplars

```java
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<PrometheusMeterRegistry> prometheusCustomizer() {
        return registry -> {
            registry.config()
                .commonTags("service", "order-service", "region", "us-east-1")
                // Enable exemplars (links metrics → traces in Grafana)
                .onMeterAdded(meter -> {
                    if (meter instanceof Timer timer) {
                        timer.record(() -> {}); // warm up
                    }
                });
        };
    }
}

// Service code: record histogram with automatic exemplar
@Service
public class OrderMetrics {
    private final Timer orderProcessingTimer;

    public OrderMetrics(MeterRegistry registry) {
        this.orderProcessingTimer = Timer.builder("order.processing.duration")
            .description("Time to process an order end-to-end")
            .tag("service", "order")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()   // enables OTel histogram + Prometheus native histograms
            .register(registry);
    }

    public void recordOrderDuration(Runnable orderTask) {
        orderProcessingTimer.record(orderTask);
        // Micrometer Tracing automatically attaches current traceId as exemplar
        // to this histogram observation if a span is active
    }
}
```

In Grafana, a histogram panel with exemplars shows each data point as a dot; clicking the
dot opens the associated trace in Tempo/Jaeger — navigating from "P99 spike at 14:32" directly
to "the specific slow request".

---

## 7. Real-World Examples

### Netflix — Unified observability with OTel Collector

Netflix migrated from vendor-specific tracing libraries (Zipkin) to OpenTelemetry in 2022,
routing all signals (traces, metrics, logs) through an OTel Collector gateway. The collector
applies sampling decisions, enriches spans with fleet metadata (region, AZ, instance type),
and fans out to Jaeger for traces and Prometheus for metrics. Key benefit: a single
`traceparent` header in each gRPC call across 1,000+ microservices enables end-to-end request
attribution. Reference: Netflix Engineering blog, "Netflix's Journey to OTel" (2023).

### Shopify — Structured logging with traceId correlation

Shopify's payment platform uses structured JSON logging with `traceId` from W3C `traceparent`
as a mandatory field on every log line. During their 2020 Black Friday incident (payment
processing slowdown), engineers correlated P99 latency spikes in Prometheus to specific
`traceId` values appearing in slow Kafka consumer log lines — finding the root cause
(a single hot partition) in 4 minutes instead of the typical 45-minute search through
unstructured logs. Reference: Shopify Engineering blog, "Surviving Black Friday 2020" (2021).

### Zalando — OTel Java Agent for zero-code instrumentation

Zalando uses the OpenTelemetry Java Agent (`-javaagent:opentelemetry-javaagent.jar`) for
automatic instrumentation of Spring Boot services without code changes. The agent auto-instruments
Spring MVC, JDBC, Kafka consumers/producers, and HTTP clients via bytecode manipulation.
This zero-code approach reduced instrumentation effort from "2 days per service" to "3 minutes"
during their 2021 observability platform migration. Reference: Zalando Engineering blog (2022).

### Stripe — Sampling strategy for high-volume services

Stripe's payment API handles 1M+ requests/minute; recording every span would overwhelm storage.
They implement head-based sampling with `tracestate` priority hints: 100% sampling for error
requests (status >= 500), 100% for slow requests (>500ms, detected via dynamic sampler), and
1% for normal requests. This yields ~10,000 traces/minute with 100% coverage of anomalies.
Implementation: OTel `ParentBasedSampler` with a custom `TraceIdRatioBased` sampler for normal
requests and a `RuleBasedSampler` that force-samples on error status and high latency.

### Grafana Labs — Exemplars linking metrics to Loki logs and Tempo traces

Grafana's own observability stack (Loki for logs, Tempo for traces, Mimir for metrics) uses
exemplars to create a tripartite correlation: from a Mimir histogram P99 spike, click the
exemplar to open the Tempo trace; from the Tempo trace, click the `traceId` label to open
the correlated Loki log query. This "jump from metric to trace to log" workflow is the primary
incident investigation flow for Grafana's own production services. The traceId is the key:
the same 32-char hex appears in all three backends.

---

## 8. Tradeoffs

| Approach | Pros | Cons | When to use |
|----------|------|------|-------------|
| Micrometer Observation API | Single API for all signals; auto-exemplars; Spring Boot 3 native | New (Spring Boot 3.2+); ObservedAspect required | New Spring Boot 3 services |
| Manual OTel SDK | Full control; works with any framework | Verbose; requires SDK knowledge | Kafka consumers, non-Spring code |
| OTel Java Agent | Zero code changes; auto-instruments 100+ libraries | Black-box; harder to debug instrumentation | Existing services, quick migration |
| Zipkin/Brave bridge | Zipkin compatibility | No OTel native; cannot use OTLP directly | Services already committed to Zipkin |
| Prometheus + no tracing | Simple; Prometheus is battle-tested | No per-request trace; can't debug slow individual requests | Internal batch jobs, low-traffic services |
| Sampling: tail-based | Records errors/slow requests; storage efficient | Requires OTel Collector with tail sampling processor; latency in sampling decision | High-volume APIs |
| Sampling: head-based | Simple; low overhead | May miss intermittent slow requests if sampled away | Low-to-medium traffic |

---

## 9. When to Use / When NOT to Use

### Use full OTel instrumentation when:
- The service is user-facing and request-level debugging is important
- Multiple services are involved in request handling (distributed tracing essential)
- Incident MTTR matters — correlated logs + traces cut investigation time 10×
- SLAs are monitored at P99 — exemplars link SLA breaches to specific traces

### Minimal instrumentation (metrics only) when:
- Internal batch jobs with no user-facing latency requirement
- Services where all work is in a single process (no distributed tracing benefit)
- Very high throughput (> 100k req/s) where even 1% sampling overhead is measurable

### Avoid OTel agent when:
- The service uses security managers or restricted classloaders (agent can't attach)
- The service binary size is tightly constrained (edge/embedded)
- You need precise control over span boundaries (agent's auto-spans may be coarser)

---

## 10. Common Pitfalls

### Pitfall 1 — High-cardinality metric labels

**Broken:**
```java
// DANGEROUS: orderId has unbounded cardinality — 10M+ time series in Prometheus
Timer.builder("order.processing.duration")
    .tag("order.id", request.orderId())  // unbounded cardinality!
    .register(registry);
```

**Fixed:**
```java
// Low cardinality labels only; order.id goes into span as high-cardinality key
Observation.createNotStarted("order.processing", registry)
    .lowCardinalityKeyValue("payment.method", request.paymentMethod())  // few values
    .highCardinalityKeyValue("order.id", request.orderId())              // trace only
    .observe(() -> processOrder(request));
```

**Impact:** Prometheus with 10M+ time series crashes at query time (OOM in query engine). A
Shopify incident in 2021 caused a 4-hour Prometheus outage from 50M time series created by
a rogue user-ID label.

---

### Pitfall 2 — Missing context propagation in `@Async`

The most common observability bug in Spring services. See §6.3 for the fix using
`ContextPropagatingTaskDecorator`. Without it, all async log lines have no `traceId`,
making log search by trace ID miss 30–50% of log lines for async-heavy services.

---

### Pitfall 3 — Sampling at 100% in production

100% sampling on a 10,000 req/s service generates 10,000 spans/s. At ~1 KB per span serialised
to OTLP: 10 MB/s of trace data → 864 GB/day. Even at 10× compression: 86 GB/day of storage.
At cloud storage costs, that's $2,500/month for traces alone. Use 1–10% head-based sampling in
production; force-sample all error requests via `TraceStateBasedSampler`.

---

### Pitfall 4 — Not closing Spans on exception paths

```java
// BROKEN: exception escapes; span never closed → memory leak in OTel BatchSpanProcessor
Span span = tracer.spanBuilder("order.charge").startSpan();
span.makeCurrent();
processPayment();   // throws PaymentException
span.end();         // NEVER REACHED
```

**Fixed:**
```java
Span span = tracer.spanBuilder("order.charge").startSpan();
try (Scope scope = span.makeCurrent()) {
    processPayment();
} catch (Exception e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR, e.getMessage());
    throw e;
} finally {
    span.end();   // always closed
}
```

---

### Pitfall 5 — `ObservationRegistry.NOOP` in tests

If you don't configure an `ObservationRegistry` bean in test slices (`@WebMvcTest`,
`@DataJpaTest`), Spring auto-configures `ObservationRegistry.NOOP` — your tests pass but
you get no signal that observability code actually works. Add a real `ObservationRegistry`
bean in test configuration and assert that expected spans/metrics are emitted.

---

## 11. Technologies & Tools

| Tool | Role | Notes |
|------|------|-------|
| Micrometer 1.12+ | Metrics API + Observation API | Spring Boot 3.x auto-configures |
| Micrometer Tracing 1.2+ | Tracing abstraction (OTel or Brave) | `micrometer-tracing-bridge-otel` for OTel |
| OpenTelemetry Java SDK 1.30+ | OTel SDK implementation | Auto-configured by Spring Boot with `opentelemetry-exporter-otlp` |
| OTel Java Agent | Zero-code auto-instrumentation | `opentelemetry-javaagent.jar` on classpath |
| OTel Collector | Pipeline: receive → transform → export | Run as sidecar or centrally; supports tail-based sampling |
| Prometheus | Metrics storage + alerting | `micrometer-registry-prometheus` exposes `/actuator/prometheus` |
| Grafana Tempo | Distributed trace backend | OTLP-native; integrates with Prometheus exemplars |
| Grafana Loki | Log aggregation | Correlates logs via `traceId` label |
| Jaeger | Trace UI + storage | OTLP-native since Jaeger 1.35 |
| logstash-logback-encoder | JSON log formatting with MDC | Includes `traceId`/`spanId` from Micrometer Tracing MDC |
| Spring Boot Actuator | Health + metrics endpoints | Auto-configures `/actuator/prometheus`, `/actuator/health` |

---

## 12. Interview Questions with Answers

**Q1. What is OpenTelemetry and how does it relate to Micrometer in a Spring Boot application?**
OpenTelemetry is a vendor-neutral CNCF standard for observability that defines APIs, SDKs, and
the OTLP protocol for metrics, traces, and logs. Micrometer is Spring's own abstraction for
metrics (since Spring Boot 1.5) and tracing (since Spring Boot 3.0). In Spring Boot 3.x,
Micrometer acts as the application-level API, and its OTel bridge (`micrometer-tracing-bridge-otel`)
delegates to the OTel Java SDK for actual span creation and OTLP export. Applications use
Micrometer's `ObservationRegistry` or `@Observed` annotation; the OTel SDK handles propagation,
sampling, and export. This separation means you can swap OTel for Brave (Zipkin) without
changing application code.

**Q2. Explain the W3C `traceparent` header format and how it enables distributed tracing.**
The `traceparent` header is a standardised format for propagating trace context across HTTP
service boundaries. It has the format `00-<trace-id>-<parent-span-id>-<flags>`, where
`trace-id` is a 128-bit hex value identifying the entire distributed trace, `parent-span-id`
is the 64-bit hex identifier of the calling span, and `flags` is a bitmask (currently only
the sampling flag). Any service that receives this header creates a child span with the same
`trace-id`, making the calling span the parent. The `trace-id` is then propagated to all
downstream calls, Kafka messages, and log MDC — creating a queryable key that links all
signals for a single user request across any number of services.

**Q3. What is the Micrometer Observation API and what does it provide over using `Timer` directly?**
The Observation API (Spring Boot 3.2+, Micrometer 1.12+) is a unified abstraction that
emits metrics (Timer), trace spans, and log events from a single `Observation.start()/stop()`
call. Using `Timer` directly only records a duration metric. `Observation` additionally: starts
and closes a span (inheriting the current trace context), populates MDC with `traceId`/`spanId`
for log correlation, records exception details on error, attaches exemplars to histograms, and
applies both `ObservationHandler` and `ObservationFilter` pipelines for cross-cutting concerns
like sampling and tag enrichment. Use `@Observed` for simple method-level instrumentation;
use manual `Observation` when you need to add event markers or high-cardinality span attributes.

**Q4. How do you propagate trace context through a `@Async` method in Spring?**
By default, `@Async` methods run on a `ThreadPoolTaskExecutor` thread that does not inherit the
calling thread's OTel context (stored in `ThreadLocal`). Fix: configure the executor with a
`TaskDecorator` that captures the current OTel `Context` before the task is submitted and
restores it on the executor thread. The Micrometer Tracing 1.2+ `ContextPropagatingTaskDecorator`
does this automatically — inject it into any `ThreadPoolTaskExecutor` used for `@Async`.
For `CompletableFuture` chains, use `OpenTelemetry.noop()` or explicitly capture
`Context.current()` before the lambda and restore it inside with `context.makeCurrent()`.
Without propagation, async log lines have no `traceId`, breaking log correlation for all
async operations.

**Q5. What is the difference between low-cardinality and high-cardinality key values in Micrometer Observation?**
Low-cardinality key values have a bounded, small set of possible values (e.g., HTTP method: GET/POST/DELETE,
payment status: success/failure/timeout) and are safe to use as metric labels because they create
a bounded number of time series in Prometheus. High-cardinality key values have unbounded value sets
(e.g., order IDs, user IDs, email addresses) and must not be used as metric labels — doing so
creates millions of time series and crashes Prometheus with OOM. In Micrometer Observation, add
high-cardinality values via `highCardinalityKeyValue()`: they are recorded as span attributes
in traces (where per-request values are expected) but are never added to metric dimensions.
A practical rule: if a value can appear more than 100 distinct times per day, treat it as high
cardinality.

**Q6. How do exemplars link Prometheus metrics to distributed traces?**
An exemplar is a single trace ID (and optionally timestamp + value) embedded in a histogram
bucket's data point when that observation is recorded while a span is active. When Prometheus
scrapes the `/actuator/prometheus` endpoint and sees a histogram bucket with an exemplar,
it stores the trace ID alongside the metric value. Grafana can then display exemplar dots on
time-series panels; clicking a dot opens the trace in Tempo/Jaeger for that specific request.
This creates a direct navigation path from "P99 latency spike at 14:32 UTC" to "the specific
request that was slow." Micrometer Tracing automatically attaches exemplars to `Timer` and
`DistributionSummary` recordings when a span is active — no additional code required if
`micrometer-tracing-bridge-otel` is on the classpath.

**Q7. Describe a sampling strategy for a service handling 50,000 requests/minute.**
At 50,000 req/min, 100% sampling generates 50,000 spans/min ≈ 833 spans/sec. At ~1 KB/span,
that's 50 MB/min of trace data — likely acceptable for a day or two but expensive long-term.
A practical strategy: (1) Head-based sampling at 5% for normal requests: record 2,500 traces/min.
(2) Force-sample (100%) all requests resulting in HTTP 5xx errors — critical for debugging.
(3) Force-sample all requests taking > P99 latency threshold (e.g., >500ms) using a dynamic
sampler that examines response time. (4) Force-sample all requests with `tracestate: priority=1`
(propagated from a client that wants its request traced — e.g., a support engineer reproducing
a bug). Implementation: OTel `ParentBasedSampler` wrapping a `TraceIdRatioBasedSampler(0.05)`
with a custom `SamplerCompositeRule` for the force-sample conditions.

**Q8. How do you add observability to a Kafka consumer without losing trace context?**
Kafka messages do not carry HTTP headers natively, but OTel supports `TextMapPropagator` over
arbitrary carrier types. The producer injects `traceparent` and `tracestate` as Kafka record
headers (byte arrays). The consumer extracts these headers via `TextMapPropagator.extract()`,
reconstructs the parent `Context`, and starts a child span with `setParent(extractedContext)`.
Spring Kafka 3.x with `micrometer-tracing-bridge-otel` auto-propagates OTel context through
`@KafkaListener` methods when the bridge is on the classpath — manual propagation is needed
only for custom `ConsumerFactory` wiring or pre-3.x Spring Kafka versions. Log MDC is populated
automatically once the child span is set as current, so all log statements inside the listener
method carry the original HTTP request's `traceId`.

**Q9. What are the risks of using the OTel Java Agent vs manual Micrometer instrumentation?**
The OTel Java Agent instruments via bytecode manipulation at startup, requiring no code changes
and auto-covering 100+ libraries. Risks: (1) Version conflicts — the agent bundles its own OTel
SDK version that may clash with explicit OTel dependencies in the app, causing
`ClassCastException` between `io.opentelemetry.api.trace.Span` instances from different class
loaders. Fix: exclude explicit OTel dependencies and use only the agent. (2) Coarser span
boundaries — the agent creates spans at framework entry points (servlet boundary, JDBC
`executeQuery`) but cannot create spans inside business logic without `@Observed` or manual
SDK calls. (3) Startup overhead — agent applies 200–500ms of extra startup latency for bytecode
scanning; problematic for Lambda/FaaS. (4) Debugging difficulty — agent-injected code does not
appear in source, making span naming opaque. Use the agent for legacy services with many
dependencies; use manual Micrometer for new services where explicit control is preferred.

**Q10. How would you validate that observability instrumentation works in a test?**
Use `@SpringBootTest` with a `TestObservationRegistry` (Micrometer 1.12+) that captures all
observations and assert against them:

```java
@SpringBootTest
class OrderServiceObservabilityTest {
    @Autowired TestObservationRegistry observationRegistry;
    @Autowired OrderService orderService;

    @Test
    void createOrder_shouldEmitObservation() {
        orderService.createOrder(testRequest());

        TestObservationRegistryAssert.then(observationRegistry)
            .hasObservationWithNameEqualTo("order.creation")
            .that()
            .hasLowCardinalityKeyValue("payment.provider", "stripe")
            .hasBeenStarted()
            .hasBeenStopped();
    }
}
```

For span assertions, use `OpenTelemetry`'s `InMemorySpanExporter` in tests:
configure an `InMemorySpanExporter` bean in test configuration; after calling the service,
assert that the exporter captured a span with the expected name, attributes, and status.

---

## 13. Best Practices

- **Propagate trace context across ALL async boundaries** — `@Async`, Kafka producers/consumers,
  `CompletableFuture`, `@Scheduled` tasks. Missing propagation creates orphaned traces.
- **Use only low-cardinality labels in metrics** — never add IDs, emails, or unbounded enums
  as metric tags. Put them in spans only.
- **Sample errors at 100%** — never sample away error requests; they are the most valuable
  traces for debugging.
- **Set `management.tracing.sampling.probability=0.1`** in production; `1.0` in dev/staging.
- **Include `traceId` in HTTP error responses** — return it in the response body or a
  `X-Trace-Id` header so clients can reference it in support tickets.
- **Use OTel Collector in front of backends** — decouples service instrumentation from backend
  choice; enables backend migration without redeployment.
- **Name spans with the operation, not the class** — `order.charge` not `PaymentService.charge`.
  Spans are cross-service; class names are local.
- **Test observability in CI** — use `TestObservationRegistry` assertions; ensure span
  names and tags match Grafana dashboard expectations before deploying.

---

## 14. Case Study

### Adding end-to-end observability to design_event_driven_microservice.md

Reference case study: [../design_event_driven_microservice.md](../design_event_driven_microservice.md)

The order → payment → inventory saga involves 3 services and 4 Kafka topics. Without distributed
tracing, a failed payment that should have triggered inventory rollback is diagnosed by searching
logs for an order ID across 3 services — typically 20–40 minutes.

**Instrumented flow:**
1. `OrderService.createOrder()` annotated `@Observed(name="order.saga.start")` — creates root span
2. `OutboxEventPublisher` injects `traceparent` into outbox event before publishing to Kafka
3. `PaymentEventConsumer` extracts `traceparent` from Kafka headers → child span
4. `SagaCompensationService.compensate()` uses the same `traceId` — parent span from compensation event

**Result:** A failed payment shows in Jaeger as a complete tree:
```
[order-service] order.saga.start (200ms)
  ├── [order-service] OutboxEventPublisher.publish (2ms)
  └── [payment-service] payment.charge (185ms)  [ERROR: CARD_DECLINED]
        └── [payment-service] SagaCompensationService.compensate (12ms)
              └── [inventory-service] inventory.release (8ms)
```

The `traceId` appears in logs from all three services. A Prometheus alert fires on
`order.saga.compensation.rate > 0.05` (>5% of orders need compensation). The Grafana exemplar
on that alert links to a representative trace of a declined payment — engineers see the full
saga in one click, reducing MTTR from 40 minutes to under 5 minutes.
