# Spring Cloud Patterns — Gateway, Resilience, Service Discovery, Observability

---

## 1. Concept Overview

Spring Cloud provides a curated suite of tools for building distributed systems on top of Spring Boot. Where a monolith handles cross-cutting concerns (routing, fault tolerance, load balancing, observability) inside a single process, microservices must handle them at the network layer. Spring Cloud externalizes these concerns into composable, declaratively configured components.

This module covers the five pillars of production Spring Cloud deployments:

1. **Spring Cloud Gateway** — reactive API gateway; edge routing, filtering, rate limiting.
2. **Resilience4j** — fault tolerance library; circuit breaker, retry, rate limiter, bulkhead.
3. **Spring Cloud LoadBalancer** — client-side load balancing; replaces Netflix Ribbon.
4. **OpenFeign** — declarative HTTP client; integrates with LoadBalancer and Resilience4j.
5. **Service Discovery (Eureka)** — peer-to-peer service registry; self-registration, heartbeat, eviction.
6. **Micrometer Tracing** — distributed tracing; replaces Spring Cloud Sleuth; exports to Zipkin/Jaeger.

Together these components implement the patterns described in microservices literature (API Gateway, Circuit Breaker, Service Registry) using Spring-idiomatic configuration.

---

## 2. Intuition

One-line analogy: Spring Cloud Gateway is the reception desk of a hotel — every guest (request) checks in there and is directed to the correct floor (service); if an elevator is broken (service down), the circuit breaker redirects them to the stairs (fallback).

Mental model: in a distributed system, the failure of one service must not cascade to all callers. Resilience4j wraps outbound calls with state machines that detect failure and stop calling a broken dependency, giving it time to recover. The gateway enforces rate limits and authentication before requests reach business services, centralizing cross-cutting concerns.

Why it matters: without these patterns, a slow downstream service causes upstream thread pool exhaustion (the "thread starvation cascade"), a single unavailable service makes the entire application unresponsive, and debugging failures across 20 services without correlation IDs is effectively impossible.

Key insight: reactive programming (WebFlux/Project Reactor) is not optional for Spring Cloud Gateway — it is the foundation. The gateway is built on Netty and Project Reactor; all filters and predicates are reactive. A single event loop thread handles thousands of concurrent connections, which is why blocking code inside a Gateway filter is catastrophic.

---

## 3. Core Principles

**Edge enforcement.** Cross-cutting concerns (authentication, rate limiting, CORS, request logging) belong at the gateway, not in every downstream service.

**Fail fast, not slow.** A circuit breaker that opens immediately on repeated failures is better than a queue of threads waiting for a timeout. Fail fast preserves resources for healthy requests.

**Client-side load balancing.** Each service instance resolves the physical addresses of its dependencies from the service registry and balances requests locally, without a central load balancer becoming a bottleneck or SPOF.

**Declarative over imperative.** `@FeignClient`, `@CircuitBreaker`, `@Retry` express intent; the framework handles the mechanics. Imperative HTTP client code with manual retry loops is replaced with annotations.

**Observability as a first-class citizen.** Every request carries a trace ID and span ID. Every service propagates these headers. Aggregated traces reveal the full call graph for any request.

---

## 4. Types / Architectures / Strategies

### Spring Cloud Gateway Predicate Types

| Predicate | Example | Description |
|-----------|---------|-------------|
| Path | `Path=/orders/**` | Route by URL path |
| Host | `Host=*.example.com` | Route by Host header |
| Method | `Method=GET,POST` | Route by HTTP method |
| Header | `Header=X-Request-Id, \d+` | Route if header matches regex |
| Query | `Query=version, v2` | Route if query param matches |
| Weight | `Weight=group1, 8` | Weighted routing for canary deploys (80/20) |

### Spring Cloud Gateway Filter Types

| Filter | Type | Function |
|--------|------|----------|
| AddRequestHeader | GatewayFilter | Adds a header to upstream request |
| AddResponseHeader | GatewayFilter | Adds a header to client response |
| RewritePath | GatewayFilter | Rewrites URL path (regex replacement) |
| CircuitBreaker | GatewayFilter | Integrates Resilience4j CB at gateway level |
| RequestRateLimiter | GatewayFilter | Token bucket rate limiting (Redis-backed) |
| Retry | GatewayFilter | Retry on specified status codes |
| StripPrefix | GatewayFilter | Removes path prefix before forwarding |
| GlobalFilter | Global | Applies to all routes (e.g., auth, logging) |

### Resilience4j Patterns

| Pattern | Class | Purpose |
|---------|-------|---------|
| Circuit Breaker | `@CircuitBreaker` | Stop calls to a failing dependency |
| Retry | `@Retry` | Retry transient failures |
| Rate Limiter | `@RateLimiter` | Limit calls per time window |
| Bulkhead | `@Bulkhead` | Limit concurrent calls (thread or semaphore) |
| Time Limiter | `@TimeLimiter` | Timeout for async operations |

### Circuit Breaker States

```
CLOSED ----[failure rate >= threshold]----> OPEN
  ^                                           |
  |                               [wait-duration elapses]
  |                                           |
  |                                           v
  +---[test calls succeed]---------> HALF_OPEN
                                     (limited test calls)
  +---[test calls fail]-----------> back to OPEN
```

### Service Discovery Architectures

| Approach | Registration | Discovery | Example |
|----------|-------------|-----------|---------|
| Client-side (Eureka) | Service registers itself | Client fetches registry, balances locally | Spring Cloud Netflix Eureka |
| Server-side (AWS ALB) | External registrar | Central load balancer | AWS Application Load Balancer |
| DNS-based | Kubernetes DNS | DNS SRV records | Kubernetes Services |

---

## 5. Architecture Diagrams

### Spring Cloud Gateway Architecture

```
External Clients
       |
       v
+------------------------------------+
|       Spring Cloud Gateway         |
|            (Netty, WebFlux)        |
|                                    |
| [1] RoutePredicateHandlerMapping   |
|     -- match request to route      |
|                                    |
| [2] GlobalFilters (all routes):    |
|     - AuthenticationFilter         |
|     - LoggingFilter                |
|     - MetricsFilter                |
|                                    |
| [3] GatewayFilters (per route):    |
|     - RateLimiter (Redis token     |
|       bucket, e.g. 100 req/s)      |
|     - CircuitBreaker               |
|     - RewritePath                  |
|     - AddRequestHeader             |
+------------------------------------+
       |              |              |
       v              v              v
  order-service  payment-service  user-service
  (lb://order-   (lb://payment-   (lb://user-
   service)       service)         service)
       |
  [LoadBalancer resolves from Eureka registry]
       |
  +----------+  +----------+  +----------+
  | pod-1    |  | pod-2    |  | pod-3    |
  +----------+  +----------+  +----------+
```

### Resilience4j Circuit Breaker State Machine

```
                   failure rate >= 50%
                   (10 calls, count-based)
                   +-----------------------+
                   |                       |
              +----+----+             +----+----+
  requests -->| CLOSED  |   open      |  OPEN   |--> fallback
              |         |------------>|         |    returned
              | calls   |             | all     |    immediately
              | proceed |             | calls   |
              +---------+             | fail    |
                   ^                  | fast    |
                   |                  +----+----+
                   |                       |
                   |              wait 60s (waitDurationInOpenState)
                   |                       |
                   |                  +----v----+
        success    |                  |HALF_OPEN|
        rate >=    +------------------|         |
        success    (5 test calls)     | limited |
        threshold                     | calls   |
                                      +---------+
                   failure            |
                   +------------------+ (back to OPEN)
```

### Distributed Tracing with Micrometer Tracing

```
Client
  |
  | [1] Request arrives, no trace context
  v
Gateway (traceId=abc123, spanId=s1)
  | [2] Propagates B3 headers:
  |     X-B3-TraceId: abc123
  |     X-B3-SpanId: s2  (new span)
  |     X-B3-ParentSpanId: s1
  v
order-service (traceId=abc123, spanId=s2)
  | [3] Calls inventory-service
  |     X-B3-TraceId: abc123
  |     X-B3-SpanId: s3
  |     X-B3-ParentSpanId: s2
  v
inventory-service (traceId=abc123, spanId=s3)
  |
  | [4] All spans exported async to Zipkin/Jaeger
  v

Zipkin UI: reconstruct full trace tree for traceId=abc123
  Gateway(s1) --> order-service(s2) --> inventory-service(s3)
  Total: 145ms  |  120ms              |  80ms
```

### Eureka Service Discovery

```
Eureka Server (AP system)
+---------------------------+
| Registry:                 |
| order-service:            |
|   192.168.1.10:8081       |
|   192.168.1.11:8081       |
| payment-service:          |
|   192.168.1.20:8082       |
+---------------------------+
     ^                 ^
     | self-register   | self-register
     | heartbeat/30s   | heartbeat/30s
     |                 |
order-service      payment-service
(all instances)    (all instances)

order-service fetches registry at startup
(cached locally, refreshed every 30s by default)
Uses Spring Cloud LoadBalancer to pick:
  192.168.1.20:8082 (RoundRobin) for payment calls
```

---

## 6. How It Works — Detailed Mechanics

### Spring Cloud Gateway — Route Configuration

```yaml
# application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service-route
          uri: lb://order-service         # lb:// triggers LoadBalancer resolution
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1               # /api/orders/123 -> /orders/123
            - AddRequestHeader=X-Gateway-Source, spring-cloud-gateway
            - name: CircuitBreaker
              args:
                name: orderServiceCB
                fallbackUri: forward:/fallback/orders
            - name: RequestRateLimiter
              args:
                redis-rate-limiter:
                  replenishRate: 100      # tokens per second
                  burstCapacity: 200      # max burst
                  requestedTokens: 1
                key-resolver: "#{@ipKeyResolver}"

        - id: canary-route
          uri: lb://order-service-v2
          predicates:
            - Path=/api/orders/**
            - Weight=canary-group, 10     # 10% of traffic to v2
```

```java
// Custom GlobalFilter — authentication verification
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Skip public paths
        if (isPublicPath(request.getPath().value())) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.parseAndValidate(token);
            // Forward user identity downstream
            ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Roles", String.join(",", extractRoles(claims)))
                .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100; // Run before all other filters
    }
}

// Rate limiter key resolver — by IP address
@Bean
public KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(
        Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
               .getAddress().getHostAddress()
    );
}
```

### Resilience4j — Circuit Breaker Configuration

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      orderServiceCB:
        sliding-window-type: COUNT_BASED    # or TIME_BASED
        sliding-window-size: 10             # evaluate last 10 calls
        failure-rate-threshold: 50          # open if >= 50% fail
        slow-call-rate-threshold: 80        # slow calls also count as failure signal
        slow-call-duration-threshold: 2000ms
        wait-duration-in-open-state: 60s    # wait before entering HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 5
        minimum-number-of-calls: 5          # need at least 5 calls before evaluating
        automatic-transition-from-open-to-half-open-enabled: true

  retry:
    instances:
      orderServiceRetry:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
        ignore-exceptions:
          - com.example.ValidationException   # do not retry client errors

  ratelimiter:
    instances:
      orderServiceRL:
        limit-for-period: 20          # 20 calls per refresh period
        limit-refresh-period: 1s
        timeout-duration: 100ms       # wait this long for a permit before throwing

  bulkhead:
    instances:
      orderServiceBH:
        max-concurrent-calls: 10      # semaphore bulkhead: max 10 concurrent calls
        max-wait-duration: 50ms
```

```java
@Service
public class OrderClient {

    private final RestClient restClient;

    // Annotations compose left to right:
    // Retry wraps RateLimiter wraps Bulkhead wraps CircuitBreaker wraps method
    @CircuitBreaker(name = "orderServiceCB", fallbackMethod = "ordersFallback")
    @Retry(name = "orderServiceRetry")
    @Bulkhead(name = "orderServiceBH")
    @RateLimiter(name = "orderServiceRL")
    public List<Order> getOrders(String userId) {
        return restClient.get()
            .uri("http://order-service/orders?userId=" + userId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Order>>() {});
    }

    // Fallback signature must match original method + Throwable parameter
    public List<Order> ordersFallback(String userId, Throwable ex) {
        log.warn("Circuit breaker open for user={}: {}", userId, ex.getMessage());
        return Collections.emptyList(); // or cached data, or partial response
    }
}
```

### OpenFeign Client

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.example.clients")
public class Application { ... }
```

```java
@FeignClient(
    name = "inventory-service",           // must match spring.application.name in registry
    fallback = InventoryClientFallback.class,
    configuration = InventoryFeignConfig.class
)
public interface InventoryClient {

    @GetMapping("/inventory/{productId}")
    InventoryItem getInventory(@PathVariable String productId);

    @PostMapping("/inventory/reserve")
    ReservationResult reserve(@RequestBody ReservationRequest request);
}

// Fallback — called when circuit breaker is open or call throws
@Component
public class InventoryClientFallback implements InventoryClient {

    @Override
    public InventoryItem getInventory(String productId) {
        // Return cached or default data
        return InventoryItem.unavailable(productId);
    }

    @Override
    public ReservationResult reserve(ReservationRequest request) {
        throw new ServiceUnavailableException("Inventory service unavailable");
    }
}

// Custom error decoder — map HTTP error responses to domain exceptions
@Configuration
public class InventoryFeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 404) {
                return new ProductNotFoundException("Product not found");
            }
            if (response.status() == 503) {
                return new RetryableException(
                    response.status(), "Service unavailable",
                    Request.HttpMethod.GET, null, null);
            }
            return new FeignException.FeignClientException(
                response.status(), "Client error", null, null, null);
        };
    }
}
```

```yaml
# Feign timeout and Resilience4j integration
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true          # enables @CircuitBreaker annotation on Feign clients

feign:
  client:
    config:
      inventory-service:
        connect-timeout: 2000  # ms
        read-timeout: 5000     # ms
        logger-level: BASIC    # NONE, BASIC, HEADERS, FULL
```

### Spring Cloud LoadBalancer

```yaml
spring:
  cloud:
    loadbalancer:
      ribbon:
        enabled: false        # Ribbon is removed; LoadBalancer is the replacement
      configurations: default # default = RoundRobin; can be 'random'
      cache:
        ttl: 35s              # cache service instance list; should be > Eureka heartbeat interval (30s)
        capacity: 256
```

```java
// Custom load balancing strategy — prefer instances in same availability zone
@Configuration
@LoadBalancerClient(name = "order-service", configuration = ZoneAwareLoadBalancerConfig.class)
public class LoadBalancerConfig {}

public class ZoneAwareLoadBalancerConfig {

    @Bean
    public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new ZonePreferenceServiceInstanceListSupplier(
            loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
            environment
        );
    }
}
```

### Eureka Server and Client

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication { ... }
```

```yaml
# Eureka Server application.yml
server:
  port: 8761
eureka:
  instance:
    hostname: eureka-server
  client:
    register-with-eureka: false     # don't register itself
    fetch-registry: false
  server:
    enable-self-preservation: true  # do not evict instances during network partitions
    eviction-interval-timer-in-ms: 60000
```

```yaml
# Eureka Client (microservice) application.yml
eureka:
  instance:
    prefer-ip-address: true         # register with IP, not hostname (better for containers)
    instance-id: ${spring.application.name}:${server.port}:${random.uuid}
    lease-renewal-interval-in-seconds: 30   # heartbeat interval (default 30)
    lease-expiration-duration-in-seconds: 90 # removed from registry after 90s without heartbeat
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
    registry-fetch-interval-seconds: 30     # refresh local registry cache every 30s
    instance-info-replication-interval-seconds: 30
```

### Micrometer Tracing

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0      # 1.0 = 100% sampling (dev); use 0.1 in production
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
    # Output: INFO [order-service,abc123def,s2abc3] — trace/span visible in every log line
```

```java
// Manual span creation for custom instrumentation
@Service
public class OrderProcessingService {

    private final Tracer tracer;

    public Order processOrder(OrderRequest request) {
        Span span = tracer.nextSpan().name("order.validate").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            validateOrder(request);         // tagged to this span
        } finally {
            span.end();
        }
        return persistOrder(request);
    }
}
```

---

## 7. Real-World Examples

**API Gateway as the single entry point.** A payments platform routes all external traffic through a Spring Cloud Gateway deployment (3 replicas). The gateway handles TLS termination, JWT authentication (GlobalFilter), per-client rate limiting (Redis token bucket, 1000 req/min per API key), and path-based routing to 12 downstream services. Downstream services are fully internal; they trust the `X-User-Id` and `X-User-Roles` headers injected by the gateway.

**Circuit breaker preventing cascade failure.** An inventory service starts responding slowly (GC pause causing 5-second latency). Without Resilience4j, the order service's thread pool fills with threads waiting for the inventory response, causing the order service to become unresponsive within seconds. With a circuit breaker (failure threshold 50%, window 10 calls, wait 60 seconds), the breaker opens after 5 failed calls, returning the fallback (empty inventory assumption) immediately. Order throughput is maintained at full capacity. After 60 seconds, the circuit enters HALF_OPEN, probes inventory with 5 test calls, finds it healthy, and closes.

**Feign client with retry for idempotent operations.** A notification service calls an SMS gateway. The gateway occasionally returns 503 on transient overload. The Feign client is configured with 3 retries, 500ms exponential backoff, retrying only on `ConnectException` and 503. Read timeout is 10 seconds (SMS gateway can be slow). Non-retryable exceptions (400 Bad Request, 401 Unauthorized) are decoded to domain exceptions and surfaced immediately without retry.

**Eureka in a multi-datacenter deployment.** Two AWS regions (us-east-1, eu-west-1) each run a Eureka server cluster. Services in each region register with their local Eureka server. Eureka servers peer-replicate to each other. In a regional failure, local services continue serving from their local registry (AP characteristic) with stale entries — acceptable because the traffic fails over at DNS/Route53 level anyway.

**Distributed tracing for incident investigation.** A customer reports a slow checkout (4 seconds). The team queries Zipkin with the trace ID from the customer's HTTP response header (`X-B3-TraceId`). The trace shows: Gateway (10ms) -> order-service (3900ms) -> inventory-service (3850ms). Inventory service span shows a slow database query (3800ms). The trace isolates the root cause in under 2 minutes; without tracing, the investigation would have taken hours of log grepping.

---

## 8. Tradeoffs

### Spring Cloud Gateway vs Nginx/Kong

| Dimension | Spring Cloud Gateway | Nginx/Kong |
|-----------|---------------------|-----------|
| Language | Java, integrated with Spring | C (Nginx), Lua plugins (Kong) |
| Latency | ~1–2ms overhead (JVM) | ~sub-millisecond (C) |
| Integration | Native Spring Security, Resilience4j, Actuator | Plugin ecosystem |
| Custom logic | Java code, full Spring context | Lua scripts (Kong), limited (Nginx) |
| Operational familiarity | Java team can maintain | Requires Nginx/Kong expertise |

### Resilience4j vs Hystrix

| Dimension | Resilience4j | Hystrix (Netflix, EOL) |
|-----------|-------------|----------------------|
| Threading model | Semaphore (default), thread pool optional | Thread pool per command |
| Java 8+ | Yes, functional API | Partially |
| Maintenance | Active | EOL as of 2018 |
| Integration | Spring Boot 3, Micrometer | Spring Boot 2 only (via spring-cloud-netflix) |

### Eureka vs Kubernetes Service Discovery

| Dimension | Eureka | Kubernetes Services + CoreDNS |
|-----------|--------|-------------------------------|
| Platform-specific | No (works anywhere) | Yes (K8s only) |
| Client-side load balancing | Yes (Spring Cloud LoadBalancer) | Server-side (kube-proxy) |
| Health checks | Heartbeat-based (self-reported) | Readiness probes (K8s controlled) |
| Recommendation | Non-K8s deployments; mixed environments | Pure K8s deployments; prefer native |

---

## 9. When to Use / When NOT to Use

**Use Spring Cloud Gateway when:**
- You need a programmable gateway with custom Java logic (auth, request transformation, dynamic routing).
- You are already in the Spring ecosystem and want a single operational model.
- You need reactive backpressure propagation from gateway to upstream clients.

**Do NOT use Spring Cloud Gateway when:**
- You need sub-millisecond gateway overhead for ultra-low-latency trading systems (use Nginx or hardware load balancers).
- Your team lacks Java expertise; Nginx/Kong are operationally simpler for non-Java teams.
- You are on Kubernetes and Istio or an AWS API Gateway already handles your routing needs.

**Use Resilience4j circuit breaker when:**
- Any downstream service call has a non-trivial failure rate or latency variance.
- Thread pool exhaustion from slow dependencies is a concern.
- You need a fallback response for degraded operation.

**Do NOT use Resilience4j when:**
- The operation is not idempotent and a retry would cause duplicate side effects (configure `ignore-exceptions` for non-retryable calls, or disable retry).
- The circuit breaker threshold is so low that it opens constantly (misconfiguration) — causing more harm than the underlying failures.

**Use Eureka when:**
- Running services on bare VMs or non-K8s containers where platform-native discovery is unavailable.
- You need client-side load balancing with zone awareness.

---

## 10. Common Pitfalls

### Pitfall 1: Blocking code inside a Gateway filter (thread starvation)

```java
// BROKEN: blocking database call on a Netty event loop thread.
// Netty has a small, fixed thread pool (~2x CPU cores).
// A single blocking call can stall hundreds of concurrent requests.
@Component
public class ApiKeyFilter implements GlobalFilter {

    private final ApiKeyRepository repo; // JPA repository

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        // WRONG: repo.findByKey() is a blocking JDBC call
        ApiKeyEntity entity = repo.findByKey(apiKey);
        if (entity == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
```

```java
// FIXED: use reactive repository (R2DBC) or offload to a bounded scheduler
@Component
public class ApiKeyFilter implements GlobalFilter {

    private final ReactiveApiKeyRepository repo; // R2DBC reactive repository

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        return repo.findByKey(apiKey)
            .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid API key")))
            .flatMap(entity -> chain.filter(exchange))
            .onErrorResume(UnauthorizedException.class, ex -> {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            });
    }
}

// Alternative for truly unavoidable blocking calls:
// Mono.fromCallable(() -> blockingRepo.findByKey(apiKey))
//     .subscribeOn(Schedulers.boundedElastic())  // offload to dedicated thread pool
//     .flatMap(entity -> chain.filter(exchange))
```

### Pitfall 2: Circuit breaker open but no fallback defined

```java
// BROKEN: circuit breaker opens, but no fallback method exists.
// Resilience4j throws CallNotPermittedException with a 500 Internal Server Error.
// The caller gets no useful error information and cannot distinguish "service down"
// from "application bug".
@CircuitBreaker(name = "inventoryServiceCB")  // no fallbackMethod
public InventoryItem checkInventory(String productId) {
    return inventoryClient.getInventory(productId);
}
```

```java
// FIXED: always define a fallback; distinguish between circuit-open and other failures
@CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "inventoryFallback")
public InventoryItem checkInventory(String productId) {
    return inventoryClient.getInventory(productId);
}

public InventoryItem inventoryFallback(String productId, CallNotPermittedException ex) {
    // Circuit is open — return cached data or a safe default
    log.warn("Circuit open for inventory-service, productId={}", productId);
    return InventoryItem.defaultAvailable(productId); // assume available, verify at order time
}

public InventoryItem inventoryFallback(String productId, Throwable ex) {
    // General fallback for all other exceptions
    log.error("Inventory service error for productId={}: {}", productId, ex.getMessage());
    return InventoryItem.unknown(productId);
}
```

### Pitfall 3: Feign client not using LoadBalancer (hardcoded URL)

```java
// BROKEN: hardcoded URL bypasses service discovery and client-side load balancing.
// No circuit breaker, no retry, no LoadBalancer integration.
@FeignClient(name = "inventory-client", url = "http://192.168.1.20:8082")
public interface InventoryClient {
    @GetMapping("/inventory/{id}")
    InventoryItem getInventory(@PathVariable String id);
}
```

```java
// FIXED: use the service name registered in Eureka; Spring Cloud LoadBalancer resolves it
// The 'name' must match spring.application.name of the target service.
// Do NOT specify 'url'; let LoadBalancer resolve from the registry.
@FeignClient(
    name = "inventory-service",          // matches Eureka registration name
    fallback = InventoryClientFallback.class
)
public interface InventoryClient {
    @GetMapping("/inventory/{id}")
    InventoryItem getInventory(@PathVariable String id);
}

// Also ensure @EnableFeignClients is on the main application class
// and spring-cloud-starter-loadbalancer is on the classpath
```

### Pitfall 4: Circuit breaker threshold too sensitive — constant false opens

```yaml
# BROKEN: minimum-number-of-calls too low; circuit opens on the first failure,
# causing production services to flap between CLOSED and OPEN constantly.
resilience4j:
  circuitbreaker:
    instances:
      paymentServiceCB:
        sliding-window-size: 2         # only 2 calls in window -- too small
        failure-rate-threshold: 50     # 1 failure out of 2 = circuit opens
        minimum-number-of-calls: 1     # opens on the very first failure
```

```yaml
# FIXED: meaningful window size and minimum call count
resilience4j:
  circuitbreaker:
    instances:
      paymentServiceCB:
        sliding-window-size: 20        # evaluate last 20 calls
        failure-rate-threshold: 50     # open if >= 10 of last 20 calls fail
        minimum-number-of-calls: 10    # need at least 10 calls before any evaluation
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 5
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 3000ms
```

### Pitfall 5: Eureka self-preservation mode causing stale instances

```
# SCENARIO: Network partition between Eureka server and 5 out of 10 service instances.
# Eureka server stops receiving heartbeats from 5 instances.
# Self-preservation mode activates: Eureka refuses to evict instances even without heartbeats.
# Result: clients in the healthy partition continue receiving stale instance entries
# for the 5 unavailable instances, causing ~50% of requests to fail.

# This is the AP trade-off: Eureka chose availability (keeping entries) over consistency
# (removing stale entries). This is correct behavior for most network partitions
# (which are transient), but confusing in permanent failure scenarios.
```

```yaml
# For production environments where you prefer faster eviction (accept false negatives):
eureka:
  server:
    enable-self-preservation: false   # WARNING: may evict healthy instances during network blips
    eviction-interval-timer-in-ms: 5000

# Better approach: combine self-preservation with client-side circuit breakers.
# Eureka retains the entry (AP); Resilience4j circuit breaker detects the actual failure
# and stops routing to the bad instance. The two mechanisms complement each other.
```

### Pitfall 6: Missing trace propagation in async/parallel code

```java
// BROKEN: trace context is lost when crossing thread boundaries with CompletableFuture
@Service
public class OrderService {

    public OrderSummary getOrderSummary(String orderId) {
        // Both calls run on a common ForkJoinPool — trace context is NOT propagated
        CompletableFuture<Order> orderFuture =
            CompletableFuture.supplyAsync(() -> orderClient.getOrder(orderId));
        CompletableFuture<Payment> paymentFuture =
            CompletableFuture.supplyAsync(() -> paymentClient.getPayment(orderId));
        // The spans for these calls will appear disconnected in Zipkin
        return new OrderSummary(orderFuture.join(), paymentFuture.join());
    }
}
```

```java
// FIXED: use Micrometer's context-propagating executor wrapper
@Configuration
public class AsyncConfig {

    @Bean
    public Executor tracingExecutor(ObservationRegistry observationRegistry) {
        // ContextExecutorService propagates Micrometer context (including trace) across threads
        return ContextExecutorService.wrap(
            Executors.newFixedThreadPool(10),
            () -> ContextSnapshot.captureAll(observationRegistry)
        );
    }
}

@Service
public class OrderService {

    private final Executor tracingExecutor;

    public OrderSummary getOrderSummary(String orderId) {
        CompletableFuture<Order> orderFuture =
            CompletableFuture.supplyAsync(
                () -> orderClient.getOrder(orderId), tracingExecutor);  // propagates trace
        CompletableFuture<Payment> paymentFuture =
            CompletableFuture.supplyAsync(
                () -> paymentClient.getPayment(orderId), tracingExecutor);
        return new OrderSummary(orderFuture.join(), paymentFuture.join());
    }
}
```

---

## 11. Technologies & Tools

| Tool | Role | Notes |
|------|------|-------|
| spring-cloud-starter-gateway | API Gateway (reactive, WebFlux/Netty) | Requires Spring WebFlux; not compatible with Spring MVC |
| spring-cloud-starter-circuitbreaker-resilience4j | Circuit breaker, retry, rate limiter | Replaces spring-cloud-starter-netflix-hystrix |
| spring-cloud-starter-openfeign | Declarative HTTP client | Integrates with LoadBalancer and Resilience4j |
| spring-cloud-starter-loadbalancer | Client-side load balancing | Replaces Netflix Ribbon |
| spring-cloud-starter-netflix-eureka-server | Service registry server | AP system; peer-to-peer replication |
| spring-cloud-starter-netflix-eureka-client | Service registry client | Self-registration, heartbeat |
| micrometer-tracing-bridge-brave | Distributed tracing (Brave/B3) | Replaces spring-cloud-sleuth |
| zipkin-reporter-brave | Zipkin span exporter | Async UDP/HTTP export |
| micrometer-tracing-bridge-otel | OpenTelemetry bridge | Use for Jaeger/OTLP export |
| Redis | Rate limiter backend for Gateway | `spring-boot-starter-data-redis-reactive` required |
| Zipkin / Jaeger | Distributed trace UI and storage | Zipkin: simpler; Jaeger: more features, CNCF |

---

## 12. Interview Questions with Answers

**What is the role of Spring Cloud Gateway and how does it differ from a traditional reverse proxy like Nginx?**
Spring Cloud Gateway is a programmatic, reactive API gateway built on Spring WebFlux and Project Reactor. Unlike Nginx, which is configured with static declarative rules, Spring Cloud Gateway executes Java code in filters and predicates, enabling dynamic routing, deep Spring Security integration, service-discovery-aware load balancing via `lb://` URIs, and full access to the Spring application context. The tradeoff is performance: Nginx has lower latency (C vs JVM) for simple proxying. Gateway is preferred when routing logic requires custom Java code or deep Spring ecosystem integration.

**Explain the difference between GlobalFilter and GatewayFilter in Spring Cloud Gateway.**
A `GlobalFilter` applies to all routes defined in the gateway. It is implemented as a Spring bean and executes for every matched request. Common uses: authentication, correlation ID injection, request/response logging. A `GatewayFilter` applies only to specific routes where it is declared in the route configuration. Built-in `GatewayFilter` implementations (RewritePath, AddRequestHeader, CircuitBreaker, RequestRateLimiter) are configured per-route in `application.yml` or Java route definitions. Custom `GatewayFilter` implementations can be reused across routes by referencing them by name in the filter configuration.

**Describe the circuit breaker states and the role of the HALF_OPEN state.**
A Resilience4j circuit breaker has three states. CLOSED is the normal operating state: all calls proceed and results are measured against the sliding window. OPEN means the failure (or slow-call) rate has crossed the configured threshold: all calls fail immediately without reaching the downstream service, and the configured fallback is returned. After `waitDurationInOpenState` elapses, the circuit automatically transitions to HALF_OPEN. In HALF_OPEN, a limited number of test calls (`permittedNumberOfCallsInHalfOpenState`, e.g., 5) are allowed through. If the failure rate among test calls is below the threshold, the circuit closes; otherwise it returns to OPEN. HALF_OPEN implements the probe-and-recover pattern: it avoids thundering herd on a recovering service by limiting test traffic.

**How does Spring Cloud LoadBalancer work and what replaced Netflix Ribbon?**
Spring Cloud LoadBalancer is the official replacement for Netflix Ribbon (removed in Spring Cloud 2020.0). When a `RestClient`, `WebClient`, or Feign client uses a `lb://service-name` URI, Spring Cloud LoadBalancer intercepts the request, queries the local service instance cache (populated from Eureka, Consul, or Kubernetes), selects an instance using the configured strategy (default: RoundRobin), and substitutes the real host/port. The instance list is cached locally and refreshed from the registry at a configurable interval (default 35 seconds). Custom strategies (zone-aware, response-time-weighted) are implemented by providing a custom `ReactorLoadBalancer<ServiceInstance>` bean.

**How do you configure OpenFeign to integrate with Resilience4j circuit breaker?**
Set `spring.cloud.openfeign.circuitbreaker.enabled=true`. This wraps every `@FeignClient` method with a Resilience4j circuit breaker named by the pattern `<FeignClientName>#<methodName>(<paramTypes>)`. Override the default name by adding `@CircuitBreaker(name = "customName")` on the interface method. Provide a fallback by setting `fallback = FallbackClass.class` or `fallbackFactory = FallbackFactory.class` on the `@FeignClient` annotation. The fallback class must implement the Feign interface and be registered as a Spring bean. Timeouts are configured separately via `feign.client.config.<clientName>.connect-timeout` and `read-timeout`.

**Why is Eureka described as an AP system in CAP theorem terms?**
Eureka prioritizes Availability and Partition Tolerance over Consistency. Each Eureka server node maintains a full copy of the registry and replicates to peers. During a network partition, nodes do not stop serving registry data — they continue serving potentially stale information rather than refusing responses (which would sacrifice Availability). This is the correct trade-off for service discovery: a client that receives a stale (but mostly correct) registry and has a circuit breaker is more resilient than a client that gets no registry data at all. Eureka's self-preservation mode further reinforces the AP stance by refusing to evict instances when heartbeat loss exceeds a threshold, assuming a network issue rather than mass instance failure.

**What is the difference between Micrometer Tracing and Spring Cloud Sleuth?**
Spring Cloud Sleuth (Spring Cloud 2020.x and earlier) provided distributed tracing by auto-configuring Brave (B3 propagation) and automatically injecting trace and span IDs into log MDC and HTTP headers. From Spring Boot 3.0 and Spring Cloud 2022.0 onward, Sleuth is replaced by Micrometer Tracing. Micrometer Tracing is vendor-neutral: it supports both Brave (Zipkin/B3) and OpenTelemetry bridges. The API (`Tracer`, `Span`, `Observation`) is part of Micrometer core. Auto-configuration is handled by Spring Boot actuator auto-configuration, not a separate Spring Cloud module. Migration from Sleuth to Micrometer Tracing requires changing dependencies and replacing `spring.sleuth.*` properties with `management.tracing.*`.

**How does B3 propagation work and what headers are involved?**
B3 is a trace context propagation format originated by Zipkin. Four headers carry context across HTTP calls: `X-B3-TraceId` — a 64-bit or 128-bit hex string identifying the entire distributed trace (same across all services for one logical request); `X-B3-SpanId` — a 64-bit hex string identifying the current unit of work (changes at each service boundary); `X-B3-ParentSpanId` — the span ID of the caller (used to build the trace tree); `X-B3-Sampled` — 1 or 0, indicating whether this trace should be recorded. Every service must propagate these headers to all downstream calls; missing propagation creates disconnected traces. In async code (CompletableFuture, reactive pipelines), the trace context must be explicitly transferred to the new thread/scheduler.

**How would you implement weighted canary routing in Spring Cloud Gateway?**
Use the Weight predicate. Define two routes with the same path predicate but different URIs and weights:
```yaml
routes:
  - id: stable
    uri: lb://order-service-v1
    predicates:
      - Path=/api/orders/**
      - Weight=orders-group, 90
  - id: canary
    uri: lb://order-service-v2
    predicates:
      - Path=/api/orders/**
      - Weight=orders-group, 10
```
Gateway distributes 90% of traffic to v1 and 10% to v2. The weight is resolved per-request using a consistent random selection within the group. This enables incremental rollout: start at 5%, monitor error rates and latency via Micrometer metrics, increase to 50%, then 100%.

**What is a Bulkhead in Resilience4j and when would you use it over a circuit breaker?**
A Bulkhead limits the number of concurrent calls to a dependency. The semaphore bulkhead blocks callers that exceed the limit (up to a configurable wait duration) and rejects them with `BulkheadFullException`. The thread pool bulkhead provides isolation via a dedicated thread pool (similar to Hystrix). A circuit breaker is reactive — it opens after failures are detected. A bulkhead is proactive — it prevents thread pool exhaustion before failures occur. Use both together: the bulkhead prevents the thread pool from being consumed by slow calls; the circuit breaker stops calling a service that has started failing. For services with high latency variance, a bulkhead with a tight concurrency limit (10–20) combined with a circuit breaker provides defense in depth.

**How does the RequestRateLimiter filter work in Spring Cloud Gateway?**
The built-in `RequestRateLimiter` filter uses a token bucket algorithm backed by Redis (via `spring-boot-starter-data-redis-reactive`). Configuration: `replenishRate` (tokens added per second), `burstCapacity` (maximum tokens in the bucket), `requestedTokens` (tokens consumed per request, default 1). A `KeyResolver` bean determines the rate limit key (by IP, by user ID, by API key). The filter uses a Lua script in Redis to atomically check and decrement the token count. Requests that exceed the rate return 429 Too Many Requests. Redis ensures consistent rate limiting across multiple Gateway instances sharing the same Redis instance.

**What happens when a Feign client method throws an exception vs. returns an error HTTP response?**
By default, Feign maps HTTP 4xx/5xx responses to `FeignException` subclasses. This does NOT trigger Resilience4j retry by default because `FeignException` is not in the `retry-exceptions` list. To retry on 503, you must configure a custom `ErrorDecoder` that maps 503 to a `RetryableException`. Conversely, if the underlying HTTP connection throws a `SocketTimeoutException` (checked exception wrapping), Feign propagates it, and if `SocketTimeoutException` is in `retry-exceptions`, Resilience4j will retry. The gotcha: if you configure retry on `FeignException`, ALL Feign errors trigger retry, including 400 Bad Request — which is never retryable and will just waste resources. Always configure specific exception types and ignore client-error exceptions explicitly.

**How would you implement rate limiting per user rather than per IP in Spring Cloud Gateway?**
Implement a custom `KeyResolver` bean that extracts the user identifier from the request context. If the gateway validates JWT tokens (via a GlobalFilter), the user ID is available as a request attribute:
```java
@Bean
public KeyResolver userKeyResolver() {
    return exchange -> {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        // X-User-Id injected by the auth GlobalFilter after JWT validation
        return userId != null
            ? Mono.just(userId)
            : Mono.just("anonymous:" +
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    };
}
```
Configure the `RequestRateLimiter` filter to reference this bean via SpEL: `key-resolver: "#{@userKeyResolver}"`. Authenticated users get per-user limits (e.g., 100 req/s); anonymous requests get per-IP limits.

**How do you test a Spring Cloud Gateway route without starting a real downstream service?**
Use `@WebFluxTest` with `WebTestClient` and mock the downstream service using `WireMock` (via `spring-cloud-contract-wiremock`) or a mock bean. Spring Cloud Gateway supports `MockWebServer` (OkHttp) for unit tests of individual filters. For integration testing the full filter chain:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)  // starts WireMock on a random port
class GatewayRouteTest {
    @Test
    void orderRouteForwardsCorrectly() {
        stubFor(get(urlEqualTo("/orders/123")).willReturn(okJson("{\"id\":\"123\"}")));
        webTestClient.get().uri("/api/orders/123")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.id").isEqualTo("123");
    }
}
```

**Explain the concept of slow call circuit breaking and why it matters for production.**
In addition to failure rate thresholds, Resilience4j allows configuring a `slowCallRateThreshold` and `slowCallDurationThreshold`. A call is considered "slow" (and counted as a negative signal) if it exceeds the duration threshold (e.g., 2000ms) even if it returns a 200 OK. This matters because a dependency that consistently responds in 3 seconds is effectively a failure from the caller's perspective — it ties up resources, adds latency to the user response, and can exhaust thread pools just as a full failure would. By treating slow calls like failures, the circuit breaker opens before the downstream service fully fails, enabling the fallback to serve fast degraded responses rather than slow partial responses.

---

## 13. Best Practices

1. Never write blocking code inside a Spring Cloud Gateway filter. Use reactive repositories (R2DBC), `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`, or pre-compute and cache the blocking result.
2. Always define a fallback method for every `@CircuitBreaker` annotation. Use overloaded fallback signatures to distinguish between circuit-open exceptions (`CallNotPermittedException`) and other errors.
3. Set `minimum-number-of-calls` to at least 10 for circuit breakers in production. A circuit that opens on 1 failure causes more harm than a slow service.
4. Use `lb://service-name` URIs in Feign clients and Gateway routes. Never hardcode IP addresses or hostnames that bypass service discovery.
5. Configure `fail-fast` and meaningful `minimum-number-of-calls` values for circuit breakers based on actual traffic volume. Low-traffic services need smaller windows; high-traffic services need larger windows.
6. Use a `KeyResolver` that identifies users (not just IPs) for rate limiting in Gateway. Shared NAT IPs can cause many legitimate users to be rate-limited together.
7. Set sampling probability to 0.1–0.2 (10–20%) in production for Micrometer Tracing. 100% sampling at high traffic volumes can overwhelm the Zipkin/Jaeger collector. Use higher sampling rates for specific services under investigation.
8. Propagate trace context in all async code paths (CompletableFuture, virtual threads, reactive pipelines) using context-propagating executor wrappers.
9. Monitor circuit breaker state transitions as metrics. `resilience4j.circuitbreaker.state` exposed via Micrometer should trigger alerts when any circuit enters OPEN state.
10. Use `@LoadBalancerClient` configuration to implement zone-aware routing in multi-AZ deployments. This reduces cross-AZ network costs and latency.
11. In Kubernetes, consider replacing Eureka with Kubernetes service discovery (`spring-cloud-starter-kubernetes-discoveryclient`). The platform already maintains a service registry; running Eureka duplicates it.
12. Configure Gateway route timeouts explicitly. Without a timeout on the `CircuitBreaker` filter's `httpStatusCodes` configuration, a Gateway route that never times out can hold connections open indefinitely.
13. Use structured logging with trace ID and span ID in every log line. Configure `logging.pattern.level` to include `%X{traceId}` and `%X{spanId}` so logs are correlated with traces in Kibana.

---

## 14. Case Study

### Scenario: Building a Resilient API Gateway for a 20-Service E-Commerce Platform

**Context.** A mid-size e-commerce company has 20 microservices serving a mobile app and web frontend. Traffic peaks at 5,000 requests per second during sale events. The previous architecture had each frontend client calling services directly, resulting in: CORS configuration scattered across 20 services, JWT validation logic duplicated in 15 services, three production incidents in 6 months caused by a slow recommendation service cascading to the product service.

**Architecture decision.** Introduce Spring Cloud Gateway as the single entry point. All JWT validation and CORS handling moved to the gateway. Resilience4j circuit breakers added at the gateway level for high-risk dependencies. OpenFeign with Resilience4j used for inter-service calls. Eureka for service discovery. Micrometer Tracing with Zipkin for observability.

**Implementation.**

Gateway routing and circuit breaker for the recommendation service:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: productServiceCB
                fallbackUri: forward:/fallback/products
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 500
                redis-rate-limiter.burstCapacity: 1000
                key-resolver: "#{@userKeyResolver}"

        - id: recommendation-service
          uri: lb://recommendation-service
          predicates:
            - Path=/api/recommendations/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: recommendationServiceCB
                fallbackUri: forward:/fallback/recommendations
                statusCodes: 500, 503, 504

resilience4j:
  circuitbreaker:
    instances:
      recommendationServiceCB:
        sliding-window-size: 20
        failure-rate-threshold: 40        # lower threshold: recommendations are non-critical
        slow-call-rate-threshold: 60
        slow-call-duration-threshold: 1500ms  # 1.5s is already too slow for recommendations
        wait-duration-in-open-state: 30s
        minimum-number-of-calls: 10
```

Fallback controller on the gateway:
```java
@RestController
public class FallbackController {

    @GetMapping("/fallback/recommendations")
    public ResponseEntity<List<Object>> recommendationsFallback() {
        // Return empty list; frontend shows "no recommendations" gracefully
        return ResponseEntity.ok()
            .header("X-Fallback", "true")
            .body(Collections.emptyList());
    }

    @GetMapping("/fallback/products")
    public ResponseEntity<Map<String, String>> productsFallback() {
        return ResponseEntity.status(503)
            .body(Map.of("error", "Product service temporarily unavailable", "retryAfter", "30"));
    }
}
```

**Results.**

During a Black Friday sale event:
- Recommendation service became slow (GC pressure, p99 latency: 8 seconds)
- Gateway circuit breaker opened after 8 of 20 calls exceeded 1500ms (40% slow-call threshold)
- All recommendation requests returned empty list from fallback in < 5ms
- Product service and checkout service were completely unaffected (zero cascade)
- Circuit entered HALF_OPEN after 30 seconds; recommendation service had recovered; circuit closed
- Total impact on checkout flow: zero. Recommendation widget showed empty for 35 seconds.

Before this architecture, the equivalent incident took down the product listing page for 12 minutes due to thread exhaustion cascade. The circuit breaker contained the blast radius to a single widget.

**Observability outcome.** Zipkin trace tree showed the circuit-open pattern within 30 seconds of the incident starting. The on-call engineer identified the recommendation service as the root cause in 2 minutes by filtering Zipkin traces by service. Without distributed tracing, the investigation would have involved grep-searching logs across 20 services.

**Lesson learned.** Gateway-level circuit breakers (for external-facing routes) and service-level Resilience4j (for inter-service Feign calls) are complementary and both necessary. A circuit breaker at the gateway protects frontend users; a circuit breaker on the Feign client inside product-service protects product-service from its own downstream dependencies. Defense in depth requires circuit breakers at every layer.
