# Spring Boot Actuator

<!-- study-paths
senior: spring_boot_actuator.md
principal: spring_boot_actuator.md
files this module contributes to each curated path; omit a tier to leave it out
-->
## 1. Concept Overview

Spring Boot Actuator adds production-ready features to Spring Boot applications: health checks, metrics, HTTP request tracing, bean introspection, logger configuration, thread dumps, and more — all accessible via HTTP endpoints or JMX. Actuator integrates with Micrometer for dimensional metrics and supports Kubernetes liveness/readiness probes out of the box.

---

## 2. Intuition

Think of Actuator as the diagnostic dashboard of a modern aircraft. The pilot (operations team) doesn't need to look inside the engine — the dashboard shows fuel level (heap usage), engine temperature (CPU), whether all systems are green (health), and complete sensor logs (metrics). The aircraft still flies the same way; the dashboard only provides observation capability.

**One-line analogy:** Actuator is an always-on diagnostic API for your running application, exposing operational data without modifying business logic.

**Key insight:** Actuator endpoints expose sensitive data (env vars, heap dumps, bean definitions, thread stacks) — securing them in production is not optional. A publicly accessible `/actuator/heapdump` can expose credentials stored in memory.

---

## 3. Core Principles

1. **Non-intrusive:** Actuator adds observation capability; business logic is untouched.
2. **Extensible:** Custom health indicators, info contributors, and custom endpoints integrate seamlessly.
3. **Security-first:** Endpoints must be secured; expose only what operations needs.
4. **Micrometer integration:** All metrics go through Micrometer's `MeterRegistry` abstraction — swap backends (Prometheus, Datadog, CloudWatch) without code changes.
5. **Kubernetes-native:** Liveness and readiness probes have first-class support via `/actuator/health/liveness` and `/actuator/health/readiness`.

---

## 4. Types / Architectures / Strategies

### Built-in Endpoints

`health` is the **only** endpoint exposed over HTTP by default — `management.endpoints.web.exposure.include` defaults to `health`. Everything else needs explicit exposure, and `shutdown` additionally needs `management.endpoint.shutdown.access=unrestricted`.

| Endpoint | Default | Description |
|----------|---------|-------------|
| `/actuator/health` | Exposed | Application health (UP/DOWN/OUT_OF_SERVICE/UNKNOWN) |
| `/actuator/info` | Not exposed | Arbitrary application info |
| `/actuator/metrics` | Not exposed | Micrometer metrics (requires explicit exposure) |
| `/actuator/env` | Not exposed | `Environment` property sources |
| `/actuator/beans` | Not exposed | All Spring beans and their dependencies |
| `/actuator/conditions` | Not exposed | Auto-configuration condition evaluation report |
| `/actuator/loggers` | Not exposed | View and change log levels at runtime |
| `/actuator/threaddump` | Not exposed | Thread dump (stack traces of all threads) |
| `/actuator/heapdump` | Not exposed | Heap dump as HPROF file |
| `/actuator/httpexchanges` | Not exposed | Last 100 HTTP exchanges (needs an `HttpExchangeRepository` bean) |
| `/actuator/shutdown` | `access=none` | Gracefully shut down application |
| `/actuator/startup` | Not exposed | ApplicationContext startup steps with timing |
| `/actuator/scheduledtasks` | Not exposed | Scheduled tasks details |

### Health Check Components

| Component | Indicator | Checks |
|-----------|-----------|--------|
| Database | `DataSourceHealthIndicator` | `Connection.isValid()`, or a configured validation query |
| Redis | `DataRedisHealthIndicator` | `INFO` (`CLUSTER INFO` on a cluster connection) |
| RabbitMQ | `RabbitHealthIndicator` | Opens a channel, reads the broker `version` property |
| Disk space | `DiskSpaceHealthIndicator` | Free disk space threshold |
| Liveness | `LivenessStateHealthIndicator` | Application is not in broken state |
| Readiness | `ReadinessStateHealthIndicator` | Application is ready to serve traffic |

Spring Boot ships no Kafka health indicator — for a Kafka dependency you write your own `HealthIndicator` over `AdminClient.describeCluster()`.

---

## 5. Architecture Diagrams

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    A(["HTTP GET /actuator/health"]) --> B["DispatcherServlet\n(or dedicated management port)"]
    B --> C["Spring Security filter\n(if configured)"]
    C --> D["HealthEndpoint.health()"]
    D --> E["CompositeHealthContributor"]
    E --> F["DataSourceHealthIndicator"]
    E --> G["RedisHealthIndicator"]
    E --> H["Custom HealthIndicator"]
    F --> I{"UP / DOWN"}
    G --> I
    H --> I
    I --> J(["CompositeHealth: status UP\ncomponents: db (PostgreSQL), redis,\norderService (pendingOrders: 42)"])

    class A req
    class B,C frozen
    class D,E base
    class F,G,H train
    class I mathOp
    class J io
```

Each contributor reports its own status, and `CompositeHealthContributor` rolls them up into one aggregate `CompositeHealth` — the worst status among the leaves determines the overall health.

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    A["Application Code\nCounter.increment()\nTimer.record(duration)\nGauge.register(supplier)"] --> B["MeterRegistry\n(abstraction layer)"]
    B --> C
    B --> D["Datadog\n(push agent)"]
    B --> E
    B --> F["Graphite\n(push agent)"]

    C@{ icon: "logos:prometheus", form: "square", label: "Prometheus<br/>(scrape /metrics)", pos: "b", h: 44 }
    E@{ icon: "logos:aws-cloudwatch", form: "square", label: "CloudWatch<br/>(push agent)", pos: "b", h: 44 }

    class A train
    class B base
    class D,F io
```

`MeterRegistry` is the one abstraction application code talks to; swapping the backend (Prometheus, Datadog, CloudWatch, Graphite) is a dependency change, not a code change.

---

## 6. How It Works — Detailed Mechanics

### Configuration

```properties
# Expose specific endpoints
management.endpoints.web.exposure.include=health,info,metrics,loggers,startup
management.endpoints.web.exposure.exclude=heapdump,shutdown,env

# Show health details (always | never | when-authorized)
management.endpoint.health.show-details=when-authorized
management.endpoint.health.show-components=always

# Kubernetes probes (on by default; set false to turn the groups off)
management.endpoint.health.probes.enabled=true
# Creates: /actuator/health/liveness and /actuator/health/readiness

# Custom management port (separate from app port, not exposed by load balancer)
management.server.port=8081

# Permit the shutdown endpoint (access is none by default)
management.endpoint.shutdown.access=unrestricted

# Health group for Kubernetes readiness (all must be UP)
management.endpoint.health.group.readiness.include=readinessState,db,redis
management.endpoint.health.group.liveness.include=livenessState
```

### Custom Health Indicator

```java
@Component
public class ExternalApiHealthIndicator implements HealthIndicator {
    private final ExternalApiClient client;

    public ExternalApiHealthIndicator(ExternalApiClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            ResponseEntity<String> response = client.ping();
            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                    .withDetail("url", client.getBaseUrl())
                    .withDetail("responseTime", "< 100ms")
                    .build();
            }
            return Health.down()
                .withDetail("url", client.getBaseUrl())
                .withDetail("status", response.getStatusCode())
                .build();
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("url", client.getBaseUrl())
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### Micrometer Metrics

```java
@Service
public class OrderService {
    private final Counter orderCounter;
    private final Timer orderTimer;
    private final AtomicInteger activeOrders;

    public OrderService(MeterRegistry registry) {
        // Counter: monotonically increasing count
        this.orderCounter = Counter.builder("orders.placed")
            .description("Total orders placed")
            .tag("region", "us-east-1")
            .register(registry);

        // Timer: duration and count
        this.orderTimer = Timer.builder("orders.processing.time")
            .description("Order processing duration")
            .publishPercentiles(0.50, 0.95, 0.99)  // p50, p95, p99
            .publishPercentileHistogram()  // for Prometheus histogram
            .register(registry);

        // Gauge: current value (e.g., queue size)
        this.activeOrders = registry.gauge("orders.active",
            new AtomicInteger(0));  // gauge tracks the AtomicInteger's value
    }

    public Order placeOrder(OrderRequest request) {
        activeOrders.incrementAndGet();
        return orderTimer.record(() -> {
            Order order = processOrder(request);
            orderCounter.increment();
            return order;
        });
    }

    private void completeOrder(Order order) {
        activeOrders.decrementAndGet();
    }
}
```

### Custom Info Contributor

```java
@Component
public class BuildInfoContributor implements InfoContributor {
    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("build", Map.of(
            "version", getClass().getPackage().getImplementationVersion(),
            "timestamp", System.getenv("BUILD_TIMESTAMP"),
            "commit", System.getenv("GIT_COMMIT")
        ));
    }
}

// application.properties to auto-expose build info from Maven:
// spring.info.build.location=classpath:META-INF/build-info.properties
// management.info.build.enabled=true
// management.info.git.mode=full  (if git.properties present)
```

### Custom @Endpoint

```java
// Custom endpoint accessible at /actuator/cache-stats
@Component
@Endpoint(id = "cache-stats")
public class CacheStatsEndpoint {
    private final CacheManager cacheManager;

    public CacheStatsEndpoint(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @ReadOperation  // HTTP GET
    public Map<String, Object> cacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            // Get Caffeine stats if using CaffeineCacheManager
            stats.put(name, Map.of("name", name, "present", true));
        });
        return stats;
    }

    @WriteOperation  // HTTP POST
    public void clearCache(@Selector String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    @DeleteOperation  // HTTP DELETE
    public void clearAllCaches() {
        cacheManager.getCacheNames()
            .forEach(name -> {
                Cache c = cacheManager.getCache(name);
                if (c != null) c.clear();
            });
    }
}
```

### Securing Actuator Endpoints

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public health/info for load balancer
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Require ACTUATOR_ADMIN role for sensitive endpoints
                .requestMatchers("/actuator/**").hasRole("ACTUATOR_ADMIN")
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());  // or JWT for prod
        return http.build();
    }
}
```

---

## 7. Real-World Examples

**Kubernetes liveness and readiness probes:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
```
Liveness failing → Kubernetes restarts the pod. Readiness failing → Kubernetes removes the pod from the load balancer (no traffic sent until ready).

**Runtime log level change:** During a production incident, change the log level of `com.example.payment` from INFO to DEBUG without restarting: `curl -X POST /actuator/loggers/com.example.payment -H "Content-Type: application/json" -d '{"configuredLevel":"DEBUG"}'`. Change back to INFO after debugging.

**Prometheus metrics scraping:** Prometheus scrapes `/actuator/prometheus` every 15 seconds. JVM GC pause times, HikariCP pool utilization, active HTTP threads, and business-level order counters are all available in Grafana dashboards. Alerting rules trigger on p99 order processing time exceeding 2 seconds.

---

## 8. Tradeoffs

| Endpoint | Risk If Exposed | Benefit |
|----------|----------------|---------|
| `/health` | Low | Load balancer/Kubernetes integration |
| `/info` | Low | Service identification |
| `/metrics` | Low-Medium | Performance monitoring |
| `/loggers` | Medium | Runtime debugging |
| `/env` | High (exposes properties) | Troubleshooting configuration |
| `/heapdump` | Critical (exposes heap memory) | Memory leak debugging |
| `/threaddump` | Medium | Deadlock debugging |
| `/shutdown` | Critical | Emergency shutdown |

---

## 9. When to Use / When NOT to Use

**Enable and secure in production:**
- `/actuator/health` — always; for load balancer and Kubernetes
- `/actuator/metrics` and `/actuator/prometheus` — for monitoring
- `/actuator/loggers` — for runtime debugging (secured)
- `/actuator/info` — for deployment tracking

**Enable only in development/staging:**
- `/actuator/beans` — useful for debugging wiring
- `/actuator/conditions` — verify auto-configuration
- `/actuator/env` — troubleshoot configuration

**Never expose without strict auth:**
- `/actuator/heapdump` — contains full heap including secrets
- `/actuator/shutdown` — can kill the application
- `/actuator/env` — exposes all properties

---

## 10. Common Pitfalls

### Pitfall 1: Exposing Sensitive Endpoints Without Authentication

```properties
# BROKEN: exposes all endpoints including heapdump, env, shutdown
management.endpoints.web.exposure.include=*

# /actuator/heapdump -> anyone can download heap and extract passwords from memory
# /actuator/env -> anyone can see SPRING_DATASOURCE_PASSWORD (even masked, value visible in heap)
# /actuator/shutdown -> anyone can kill the application

# FIXED: explicit whitelist + secure with Spring Security
management.endpoints.web.exposure.include=health,info,metrics,prometheus
# Add SecurityFilterChain requiring authentication for /actuator/**
```

### Pitfall 2: Health Check Hitting Slow External Services on Every Probe

```java
// BROKEN: health indicator makes HTTP call on every /actuator/health probe
// Kubernetes probes /health every 10 seconds → 6 HTTP calls/minute to external service
@Component
public class SlowExternalServiceHealth implements HealthIndicator {
    @Override
    public Health health() {
        ResponseEntity<?> r = restTemplate.getForEntity("https://slow-api.com/ping", Void.class);
        // Takes 500ms each call...
        return Health.up().build();
    }
}

// FIXED: cache health check result with TTL
@Component
public class CachedExternalServiceHealth implements HealthIndicator {
    private volatile Health cachedHealth = Health.unknown().build();
    private volatile long lastCheckTime = 0;
    private static final long CACHE_TTL_MS = 30_000;  // re-check every 30s

    @Override
    public Health health() {
        if (System.currentTimeMillis() - lastCheckTime > CACHE_TTL_MS) {
            cachedHealth = checkExternal();
            lastCheckTime = System.currentTimeMillis();
        }
        return cachedHealth;
    }
}
```

### Pitfall 3: Micrometer Tag Explosion

```java
// BROKEN: using user ID or request ID as a tag
// creates millions of unique time series (cardinality explosion)
Timer.builder("http.requests")
    .tag("userId", userId)  // potentially millions of unique values!
    .tag("requestId", requestId)  // unique per request!
    .register(registry);

// FIXED: use low-cardinality tags only
Timer.builder("http.requests")
    .tag("uri", request.getRequestURI())       // limited set of endpoints
    .tag("method", request.getMethod())         // GET/POST/PUT/DELETE
    .tag("status", String.valueOf(status))      // 200/400/500
    .register(registry);
```

---

## 11. Technologies & Tools

| Component | Role |
|-----------|------|
| `spring-boot-actuator` | Core actuator endpoints |
| `spring-boot-actuator-autoconfigure` | Auto-configuration for actuator |
| `micrometer-core` | Metrics abstraction layer |
| `micrometer-registry-prometheus` | Prometheus metrics format |
| `micrometer-registry-datadog` | Datadog metrics push |
| `micrometer-tracing` | Distributed tracing facade; bridged by `micrometer-tracing-bridge-otel` or `-brave` |
| `HealthIndicator` | Interface for custom health checks |
| `InfoContributor` | Interface for custom `/info` contributions |
| `@Endpoint` | Annotation to define custom actuator endpoints |

---

## 12. Interview Questions with Answers

**Q: What is the difference between liveness and readiness probes in Spring Boot Actuator?**
**Short:** A failing liveness probe gets the pod restarted; a failing readiness probe only pulls it from the load balancer.

Liveness probe (`/actuator/health/liveness`) indicates whether the application is in a broken state that requires a restart. If liveness fails, Kubernetes kills and restarts the pod. Readiness probe (`/actuator/health/readiness`) indicates whether the application is ready to receive traffic. If readiness fails, Kubernetes removes the pod from the load balancer but does not restart it — it waits for readiness to recover. Liveness should only fail for truly unrecoverable states (deadlock, corrupted state). Readiness should fail during startup (before all beans are ready) and when dependent services are unavailable.

**Q: What is Micrometer and how does it relate to Spring Boot Actuator?**
**Short:** Micrometer is the vendor-neutral metrics API that Actuator auto-configures a `MeterRegistry` for, exposing it via `/actuator/metrics`.

Micrometer is a metrics instrumentation library that provides a vendor-neutral API for recording application metrics. Spring Boot Actuator auto-configures a `MeterRegistry` based on what's on the classpath. Micrometer supports multiple backends (Prometheus, Datadog, CloudWatch, Graphite, InfluxDB) — switching backends requires only changing the registry dependency, not the application code. Actuator exposes `/actuator/metrics` (JSON) and `/actuator/prometheus` (Prometheus text format) endpoints backed by Micrometer.

**Q: What are the four main Micrometer meter types?**
**Short:** Counter only increases, Timer records count and duration, Gauge moves up and down, and DistributionSummary records non-duration values.

`Counter` is a monotonically increasing value (total requests, errors). `Timer` records both count and duration with percentile support (request processing time). `Gauge` represents a value that can go up and down (active connections, queue size, cache size). `DistributionSummary` is similar to Timer but records values that aren't durations (request body size, payload size). All meters support tags (dimensions) for slicing metrics by region, service, status code. Tags must be low-cardinality — avoid user IDs, request IDs, or other high-cardinality values.

**Q: How do you secure Spring Boot Actuator endpoints in production?**
**Short:** Whitelist safe endpoints, require authentication for `/actuator/**`, and run management endpoints on a separate, unexposed port.

Configure Spring Security to require authentication for `/actuator/**`, expose only safe endpoints, and use a separate management port. Set `management.endpoints.web.exposure.include=health,info,metrics` as a whitelist. Require `ACTUATOR_ADMIN` role for sensitive operations like `/actuator/loggers` and `/actuator/beans`. Use a separate management port (`management.server.port=8081`) not exposed by the load balancer. Never expose `/actuator/heapdump`, `/actuator/env`, or `/actuator/shutdown` without strong authentication and audit logging.

**Q: What is the CompositeHealth structure in Actuator?**
**Short:** `CompositeHealth` reports the worst status among all `HealthIndicator` beans, ranked by a configurable severity order.

`CompositeHealth` aggregates the results of multiple `HealthIndicator` or `HealthContributor` beans. The overall status is the worst status among all contributors, using the default severity order DOWN, OUT_OF_SERVICE, UP, UNKNOWN — so UNKNOWN is the *least* severe and never drags an otherwise-UP application down. Aggregation also **ignores any status not present in that order list**, so a custom `Health.status("DEGRADED")` is silently discarded unless you add it via `management.endpoint.health.status.order`. Individual components are shown under the `components` key when `show-components=always`. Health groups allow separate health endpoints with different subsets of indicators — for example, a `readiness` group including only `readinessState`, `db` and `redis` but not slow external APIs.

**Q: How would you implement a custom Actuator endpoint?**
**Short:** Annotate a bean `@Endpoint` and define `@ReadOperation`/`@WriteOperation`/`@DeleteOperation` methods to back GET/POST/DELETE.

Annotate a Spring bean with `@Endpoint(id="my-endpoint")`. Define `@ReadOperation` methods (HTTP GET, returns JSON-serializable objects), `@WriteOperation` methods (HTTP POST), and `@DeleteOperation` methods. Use `@Selector` parameter annotation for path variables (`/actuator/my-endpoint/{name}`). The endpoint is automatically accessible at `/actuator/my-endpoint`. Expose it via `management.endpoints.web.exposure.include=my-endpoint`. For web-specific operations, use `@WebEndpoint` (HTTP only) or `@JmxEndpoint` (JMX only) instead of `@Endpoint` (both).

**Q: What does /actuator/startup show and how is it useful?**
**Short:** It shows a timed breakdown of `ApplicationContext` startup steps, revealing which beans are slow to initialize.

`/actuator/startup` shows a timeline of ApplicationContext startup steps with timing for each bean initialization. It requires a `BufferingApplicationStartup` configured on `SpringApplication`. This endpoint reveals which beans are slow to initialize (e.g., a `@PostConstruct` making HTTP calls) and their contribution to total startup time. Essential for diagnosing slow startup in Kubernetes environments where readiness probe timeout must be met. `GET /actuator/startup` returns a non-destructive snapshot; `POST /actuator/startup` returns the timeline **and drains** the buffer, which is how you free the retained steps after analysis.

**Q: What is the risk of exposing /actuator/heapdump?**
**Short:** A heap dump captures every in-memory object, so an unauthenticated download can leak passwords, tokens, and PII.

A heap dump contains a full snapshot of the JVM heap including all object instances in memory. Sensitive data stored as Java objects — database passwords from `DataSource` configuration, JWT tokens from `SecurityContext`, user PII from cached entities — is captured in the dump. Anyone with access to `/actuator/heapdump` can download a HPROF file and use tools like Eclipse Memory Analyzer (MAT) to extract plaintext credentials. Always restrict this endpoint to authenticated operators via a separate management network. In production, prefer using `jmap` or `jcmd` from an operator console rather than exposing it via HTTP.

**Q: How do you configure HikariCP pool metrics in Actuator?**
**Short:** Spring Boot auto-attaches a `MicrometerMetricsTrackerFactory` to `HikariDataSource` once a `MeterRegistry` bean exists, no config needed.

Spring Boot binds a `MicrometerMetricsTrackerFactory` onto the `HikariDataSource` automatically once a `MeterRegistry` bean exists. The auto-configuration class is `DataSourcePoolMetricsAutoConfiguration`, and it only attaches the tracker if the pool has no `metricRegistry` or `metricsTrackerFactory` of its own. Metrics include: `hikaricp.connections.active` (connections in use), `hikaricp.connections.idle` (available connections), `hikaricp.connections.pending` (threads waiting for a connection), `hikaricp.connections.timeout` (a counter of connection-acquisition timeouts), `hikaricp.connections.acquire` (a timer of how long callers waited for a connection), and `hikaricp.connections.usage` (a timer of how long a connection was held before being returned). Pool saturation — `active` approaching `maximum-pool-size` with `pending` above zero — is the first signal of a database throughput problem.

**Q: How does /actuator/loggers work and why is it valuable in production?**
**Short:** It reads and changes logger levels at runtime via GET/POST, letting you turn on DEBUG mid-incident without a restart.

`GET /actuator/loggers` returns all configured loggers and their current levels. `GET /actuator/loggers/{name}` shows the level for a specific logger. `POST /actuator/loggers/{name}` with body `{"configuredLevel": "DEBUG"}` changes the level at runtime without restart. This is invaluable during production incidents: switch a specific package to DEBUG to capture detailed trace without restarting (which would clear in-flight requests and change timing). Changes are in-memory only and reset on restart, so there is no permanent side effect. The endpoint should require authentication because excessive DEBUG logging can expose sensitive data.

**Q: What is the `Info` endpoint, how do you populate it, and what value does it provide in an automated deploy pipeline?**
**Short:** `/actuator/info` exposes build and Git metadata so a deploy dashboard can confirm exactly which commit is running in production.

`/actuator/info` returns arbitrary application information as JSON. Populate it via: (1) `management.info.git.mode=full` — injects Git commit hash, branch, commit time from `git.properties` (generated by `git-commit-id-plugin`). (2) `management.info.build.enabled=true` — injects build version, artifact ID from `META-INF/build-info.properties` (generated by Spring Boot Maven/Gradle plugins). (3) Custom `InfoContributor` beans. In an automated pipeline, `/actuator/info` lets monitoring systems, deployment dashboards, and support staff verify exactly which commit hash and build version is running — critical for correlating a production incident with the deployment that caused it.

**Q: How does Micrometer's `@Timed` annotation work and when should you use programmatic recording instead?**
**Short:** `@Timed` wraps a method in a `Timer` via AOP, but use programmatic recording when tags depend on the return value or outcome.

`@Timed("my.operation")` on a Spring bean method (or class, to instrument all methods) instruments it via a `TimedAspect`, which starts and stops a `Timer` around the invocation. The aspect is only auto-configured when `management.observations.annotations.enabled=true` and AspectJ is on the classpath, so `@Timed` on a plain bean is a silent no-op until you set that property. Static tags come from `extraTags`; argument-derived tags come from `@MeterTag` on a parameter; a fully custom scheme means constructing `TimedAspect` yourself with a `Function<ProceedingJoinPoint, Iterable<Tag>>`. Use programmatic recording (`registry.timer("my.op", "tag", value).record(() -> doWork())`) when the tag depends on the return value, when you need partial durations inside a method, or when you want success and failure recorded as distinct tag values. `@Timed` is convenient for coarse-grained external API latency; programmatic timers are necessary for internal business logic with rich context.

**Q: What is Micrometer Tracing and how does it integrate with Actuator?**
**Short:** It is a vendor-neutral tracing facade over Brave or OpenTelemetry that auto-instruments requests and correlates spans with logs.

Micrometer Tracing is a vendor-neutral tracing facade over concrete implementations, either Brave/Zipkin or OpenTelemetry. Spring Boot auto-configures tracing when `micrometer-tracing-bridge-otel` or `micrometer-tracing-bridge-brave` is on the classpath; `spring-boot-starter-opentelemetry` pulls the OTel bridge, `micrometer-registry-otlp` and the OTLP exporter in one dependency. It provides: (1) `Observation` API — a unified abstraction over metrics + tracing that records both a `Timer` and a distributed trace span in one call. (2) Auto-instrumentation — `@Observed` on beans, `RestClient`/`WebClient`/`RestTemplate` interceptors, Spring MVC/WebFlux server filters, Kafka listeners. (3) `TraceId`/`SpanId` injection into MDC for structured logging correlation. The `/actuator/health` endpoint propagates trace context when `management.tracing.sampling.probability=1.0`.

**Q: What is the difference between `HealthIndicator` and `HealthContributor`, and when do you use `CompositeHealthContributor`?**
**Short:** `HealthIndicator` is a leaf health check, while `CompositeHealthContributor` groups several related indicators under one named entry.

`HealthIndicator` is the simple interface: implement `health()` returning a `Health` object with status and optional details. Spring auto-discovers all `HealthIndicator` beans and aggregates them. `HealthContributor` is a marker interface for both `HealthIndicator` (leaf contributor, returns a health result directly) and `CompositeHealthContributor` (named group of sub-contributors). Use `CompositeHealthContributor` when you want to group multiple related checks under a named hierarchy: e.g., a `DatabaseHealthContributor` composed of separate `ReadReplicaHealthIndicator` and `PrimaryHealthIndicator`. Each sub-contributor gets its own named entry under `components` in the health response, giving fine-grained visibility into which specific component is unhealthy.

**Q: How do you configure a management server on a different port and why is this the recommended production pattern?**
**Short:** Set `management.server.port=8081` so sensitive actuator endpoints stay unreachable from outside the cluster's trust boundary.

Set `management.server.port=8081` (and optionally `management.server.address=127.0.0.1` to bind to localhost only). With a separate port: the load balancer / API gateway exposes only port 8080 (business traffic) to the internet; port 8081 is only reachable from within the cluster or through an internal VPN. This means `/actuator/heapdump`, `/actuator/env`, and `/actuator/beans` (which expose configuration, credentials, and class structure) are never reachable from outside the trust boundary. In Kubernetes, the liveness/readiness probes are configured to hit port 8081 directly on the pod IP — the probes bypass the service load balancer and check each pod individually. If you would rather not open the management port to the kubelet at all, set `management.endpoint.health.probes.add-additional-paths=true`: the liveness and readiness groups then get a second mapping at `/livez` and `/readyz` on the **main** server port (8080), while every other actuator endpoint stays on 8081. Point the probes at those two paths and the management port never leaves the pod.

---

## 13. Best Practices

1. **Enable `/actuator/health/liveness` and `/actuator/health/readiness`** for all Kubernetes deployments.
2. **Expose only whitelisted endpoints** — never use `*` in production.
3. **Use a separate management port** not accessible from the public internet.
4. **Secure actuator with Spring Security** — require `ACTUATOR_ADMIN` role for sensitive endpoints.
5. **Instrument business metrics** (orders placed, payments processed) alongside technical metrics.
6. **Use low-cardinality tags only** — avoid user IDs, request IDs, or UUIDs as metric tags.
7. **Cache slow health indicators** — external API health checks should not run on every probe.
8. **Configure percentile timers** (`publishPercentiles(0.5, 0.95, 0.99)`) for meaningful latency SLAs.
9. **Add build/git info** to `/actuator/info` for deployment traceability (which commit is deployed).
10. **Use `/actuator/startup`** during development to identify slow bean initialization.

---

## 14. Case Study

### Scenario: Observable Payment Microservice on Kubernetes

A payments microservice runs on Spring Boot 4.1 / Java 25 across a Kubernetes cluster. Scale and topology:

- 40 pods, ~12,000 req/sec aggregate, p99 budget 250 ms
- Liveness and readiness probes wired to Actuator health groups
- Prometheus scrapes `/actuator/prometheus` every 15s; Grafana dashboards and alerting on top
- A downstream payment gateway whose outages must be visible without taking the whole pod down
- A Resilience4j circuit breaker whose state must be inspectable in production

```
per-pod rate = lambda / N
L            = lambda x W          <- Little's Law, requests in flight fleet-wide
per-pod L    = L / N
```

**What this actually says.** "Forty pods, twelve thousand requests a second, and a quarter-second tail budget are not three separate facts — multiply and divide them and you get the per-pod concurrency the JVM must sustain, which is what actually sizes the thread pool and the connection pool."

Fleet-level numbers mean nothing to a single pod. The two derived figures below are what you tune against.

| Symbol | What it is |
|--------|------------|
| `λ` (lambda) | Aggregate arrival rate — **12,000 req/sec** across the fleet |
| `W` | Time budget per request — the **250 ms** p99 target |
| `N` | Pod count — **40** |
| `λ / N` | Per-pod request rate |
| `L = λ x W` | Requests in flight fleet-wide (Little's Law) |
| scrape interval | **15 s** — sets metric freshness and per-pod scrape cost |

**Walk one example.** Turn the three scale figures into per-pod requirements:

```
  Per-pod arrival rate:
    12,000 req/sec / 40 pods = 300 req/sec per pod

  In-flight requests (Little's Law, at the p99 budget):
    L = 12,000 x 0.250 sec = 3,000 requests in flight fleet-wide
    per pod: 3,000 / 40    =    75 concurrent requests

  -> a Tomcat pool of 200 threads/pod covers 75 with room; a HikariCP pool
     of 10 does NOT, so requests queue on the pool, not on the CPU.

  Losing pods (the rolling-deploy case):
    40 -> 36 pods:  12,000 / 36 = 333 req/sec per pod   (+11%)
    40 -> 30 pods:  12,000 / 30 = 400 req/sec per pod   (+33%)

  Scrape cost:
    3,600 sec/hour / 15 sec = 240 scrapes per pod per hour
    240 x 40 pods           = 9,600 scrapes per hour fleet-wide
```

The 75-concurrent-requests figure is the one that matters. It is why `hikaricp.connections.pending` is called out later as the first signal of trouble: at a default pool of 10 connections against 75 in-flight requests, 65 of them are waiting on the pool, and the p99 you measure is queue time rather than query time.

**Why the 15-second scrape interval bounds your alerting.** Prometheus cannot detect anything faster than it samples. A p99 breach that lasts 10 seconds may be entirely invisible; an alert configured `for: 1m` needs four consecutive scrapes to fire, so the floor on detection latency is roughly 60-75 seconds regardless of how the rule is written. Tightening the scrape to 5 s triples the sample volume for the same fleet — 720 scrapes per pod per hour — which is the real cost of faster detection.

The team had two recurring problems: 503 spikes during rolling deploys (probes passing before the context was ready) and a security finding that the `env` endpoint had leaked database credentials.

### Architecture Overview

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Prom -->|"scrape /actuator/prometheus (15s)"| Act
    K8s -->|"GET /actuator/health/readiness"| Act
    K8s -->|"GET /actuator/health/liveness"| Act

    subgraph Pod["Payment Pod"]
        Act["Actuator\nhealth group readiness = db, gateway\n/prometheus, custom @Endpoint"]
    end

    Act --> GW["PaymentGateway HealthIndicator"]
    Act --> CB["Resilience4j CB state endpoint"]

    Prom -->|"PromQL"| Graf

    Prom@{ icon: "logos:prometheus", form: "square", label: "Prometheus", pos: "b", h: 44 }
    Graf@{ icon: "logos:grafana", form: "square", label: "Grafana<br/>(dashboards)", pos: "b", h: 44 }
    K8s@{ icon: "logos:kubernetes", form: "square", label: "K8s kubelet", pos: "b", h: 44 }

    class Act base
    class GW,CB train
```

### Implementation

A custom `HealthIndicator` reports downstream gateway health into the readiness group, so a pod with a dead gateway is pulled from the load balancer instead of failing live requests.

```java
@Component("gateway")
public class PaymentGatewayHealthIndicator implements HealthIndicator {
    private final PaymentGatewayClient client;
    PaymentGatewayHealthIndicator(PaymentGatewayClient c) { this.client = c; }

    @Override
    public Health health() {
        try {
            Duration rtt = client.ping();                 // cheap /status call, 1s timeout
            return rtt.toMillis() < 500
                ? Health.up().withDetail("rttMs", rtt.toMillis()).build()
                : Health.status("DEGRADED").withDetail("rttMs", rtt.toMillis()).build();
        } catch (Exception e) {
            return Health.down(e).build();                // exception message only, no secrets
        }
    }
}
```

Business metrics use Micrometer `Counter` and `Timer`; Prometheus scrapes them and Grafana renders rate and p99.

```java
@Service
public class PaymentService {
    private final Counter approved;
    private final Counter declined;
    private final Timer latency;

    public PaymentService(MeterRegistry registry) {
        this.approved = Counter.builder("payments_total").tag("result", "approved").register(registry);
        this.declined = Counter.builder("payments_total").tag("result", "declined").register(registry);
        this.latency  = Timer.builder("payment_processing_seconds")
                             .publishPercentileHistogram()   // enables Prometheus histogram p99
                             .register(registry);
    }

    public PaymentResult charge(PaymentRequest req) {
        return latency.record(() -> {
            PaymentResult r = gateway.charge(req);
            (r.isApproved() ? approved : declined).increment();
            return r;
        });
    }
}
```

A custom `@Endpoint` exposes circuit breaker state for on-call inspection, and `HealthContributor` composition groups multiple sub-checks.

```java
@Component
@Endpoint(id = "circuitbreakers")
public class CircuitBreakerEndpoint {
    private final CircuitBreakerRegistry registry;
    CircuitBreakerEndpoint(CircuitBreakerRegistry r) { this.registry = r; }

    @ReadOperation
    public Map<String, String> states() {
        return registry.getAllCircuitBreakers().stream()
            .collect(Collectors.toMap(CircuitBreaker::getName,
                                      cb -> cb.getState().name()));
    }
}
```

```properties
# Expose only what is needed; never wildcard in production
management.endpoints.web.exposure.include=health,info,prometheus,circuitbreakers
management.endpoint.health.probes.enabled=true
management.endpoint.health.group.readiness.include=readinessState,db,gateway
management.endpoint.health.show-details=when_authorized
management.endpoint.health.show-components=when_authorized

# Without this, the aggregator FILTERS OUT the custom DEGRADED status entirely
# and a degraded gateway aggregates as UP. Unmapped statuses answer 200, so
# DEGRADED keeps the pod in rotation while staying visible in the payload.
# Do NOT add status.http-mapping entries here: any entry REPLACES the default
# map wholesale, and DOWN silently stops answering 503.
management.endpoint.health.status.order=DOWN,OUT_OF_SERVICE,DEGRADED,UP,UNKNOWN
```

```yaml
# deployment.yaml
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 6
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3
```

### Metrics

| Metric | Before | After |
|--------|--------|-------|
| 503s per rolling deploy | ~5% of traffic, 30s | 0 |
| Mean time to detect gateway outage | 8 min (user reports) | 20 s (readiness flip) |
| Exposed actuator endpoints | all (`*`) | 4 explicit |
| Secrets leaked via `env` | yes | none (endpoint not exposed) |
| Grafana p99 metric gaps | frequent | none |

### Common Pitfalls

**Pitfall 1 — exposing all endpoints leaks secrets via `env`.**

```properties
# BROKEN: /actuator/env dumps every property, including spring.datasource.password
management.endpoints.web.exposure.include=*
```

```properties
# FIX: expose only the endpoints you operate on; protect the rest behind auth
management.endpoints.web.exposure.include=health,info,prometheus
```

**Pitfall 2 — health details expose DB credentials to anonymous callers.**

```properties
# BROKEN: anyone hitting /actuator/health sees jdbc URL, validation query, etc.
management.endpoint.health.show-details=always
```

```properties
# FIX: only show component details to authenticated/authorized principals
management.endpoint.health.show-details=when_authorized
management.endpoint.health.roles=ACTUATOR_ADMIN
```

**Pitfall 3 — Prometheus scrape interval mismatch causes gaps and bad rates.**

```yaml
# BROKEN: scrape every 60s but alert on rate(...[30s]) -> empty windows, flapping alerts
scrape_interval: 60s
```

```yaml
# FIX: scrape faster than the smallest rate window; rate window >= 4x interval
scrape_interval: 15s          # then use rate(payments_total[1m]) in Grafana/alerts
```

### Interview Discussion Points

**What is the difference between liveness and readiness probes, and how does Actuator support them?** Liveness answers "is the JVM healthy enough to keep running" — failing it restarts the pod; readiness answers "can it serve traffic right now" — failing it removes the pod from the Service endpoints without a restart. Actuator exposes `/actuator/health/liveness` and `/actuator/health/readiness` as health groups, and you compose business checks (db, caches, downstream gateway) into the readiness group so a not-yet-warm pod stays out of rotation.

**How do you keep a transient downstream outage from restarting pods?** Put the downstream check in the readiness group, not liveness. A failing gateway makes the readiness group aggregate DOWN, so `/actuator/health/readiness` answers 503 and Kubernetes stops routing traffic while leaving the pod running; when the gateway recovers the pod rejoins automatically. Tying it to liveness instead would restart healthy pods on every blip, amplifying the outage.

**Why is `show-details=when_authorized` the right default?** The health endpoint's component details can include JDBC URLs, validation queries, disk paths, and downstream addresses. `when_authorized` returns only `{"status":"UP"}` to anonymous probes (enough for Kubernetes) while exposing the diagnostic detail to authenticated operators, closing an information-disclosure hole without losing observability.

**How do Micrometer `Counter` and `Timer` map to Prometheus, and how do you get p99?** A `Counter` becomes a monotonically increasing Prometheus counter you query with `rate(...)`; a `Timer` with `publishPercentileHistogram()` emits histogram buckets that Prometheus aggregates with `histogram_quantile(0.99, ...)` across pods. Computing percentiles server-side from buckets (rather than per-instance) is what makes cluster-wide p99 meaningful.

**When would you write a custom `@Endpoint` instead of a `HealthIndicator`?** Use a `HealthIndicator` when the signal is a binary up/down/degraded that should influence health and probes. Use a custom `@Endpoint` (with `@ReadOperation`/`@WriteOperation`) when you need to surface rich operational state or actions that are not health decisions — like dumping circuit breaker states or triggering a cache refresh — exposed under its own actuator path and secured independently.

**How do you secure actuator endpoints without disabling them?** Expose only the endpoints you operate (`exposure.include` allowlist), keep management on a separate port or behind the same Spring Security filter chain with a dedicated `ACTUATOR_ADMIN` role, and use `when_authorized` for detail-bearing endpoints. Probes that Kubernetes calls (`health/liveness`, `health/readiness`) can stay anonymous because they return no sensitive detail.

---

## Related / See Also

- [Observability & Tracing](../observability_and_tracing/observability_and_tracing.md) — Micrometer + OTLP
- [Spring Boot Auto-Configuration](../spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) — actuator auto-config
- [Case Study: OTel Observability](../case_studies/cross_cutting/otel_observability_for_spring.md) — production tracing
- [Prometheus Metrics](../../devops/observability_metrics_prometheus/observability_metrics_prometheus.md) — scrape configs, PromQL, alerting rules behind `/actuator/prometheus`
- [Observability & Monitoring](../../backend/observability_and_monitoring/observability_and_monitoring.md) — the broader monitoring stack Actuator health/metrics feed into
