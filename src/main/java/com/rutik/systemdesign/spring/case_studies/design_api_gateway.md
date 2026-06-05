# Design: API Gateway for Microservices with Spring Cloud Gateway

> "An API gateway is the post office for a microservices city — it accepts every incoming
> envelope, stamps it (auth, trace ID, rate-limit token), routes it to the right address,
> and returns the reply. The city runs fine without a post office, but nothing outside can
> reach it cleanly."

**Key insight:** A gateway's correctness guarantee is that it must never be the *cause* of a
request failure. Its dependencies (Redis for rate limiting, service discovery) must be
treated as optional accelerators, not required correctness paths. Fail-open with loud
alerts, not fail-closed with silent outages.

See also: [Resilience4j patterns](./cross_cutting/resilience4j_patterns.md),
[OTel observability for Spring](./cross_cutting/otel_observability_for_spring.md)

---

## 1. Requirements Clarification

**Functional requirements:**
- Single entry point routing to 20+ backend services based on path, host, and headers.
- JWT validation; forward user identity (`X-User-ID`, `X-User-Roles`) to backends.
- Rate-limit each client to 1,000 req/min (100 req/sec, burst 200) via Redis token bucket.
- Per-route circuit breakers with service-specific fallback responses.
- Structured request/response logging with timing; distributed trace propagation (B3/W3C).

**Non-functional requirements:**
- 50,000 req/sec peak; ≤10ms gateway overhead at P99.
- Single gateway failure must not cascade to backend services.
- Route changes without restart (`RefreshRoutesEvent`).
- Sub-30s recovery from Redis rate-limiter outage (fail-open).

**Constraints:** Spring Cloud Gateway 4.x (reactive, WebFlux/Netty), Redis 7 for rate
limiting, Resilience4j circuit breakers, Java 21 (virtual threads inapplicable — fully
reactive model).

**Out of scope:** API versioning strategy for backends, GraphQL gateway, service mesh
(Istio/Envoy) integration.

---

## 2. Scale Estimation

**Traffic math:**
```
Sustained:           20,000 req/sec
Peak (2.5×):         50,000 req/sec
Filter chain per request: 5 filters (tracing, auth, rate-limit, circuit-breaker, logging)

Netty event-loop threads: 2 × CPU cores
  -> 16-core node: 32 event-loop threads
  -> At 50,000 req/sec each filter must complete in < 1ms (non-blocking)
     otherwise 32 threads x 1ms = 32 concurrent requests -> queue backup
```

**Redis rate limiter sizing:**
```
Rate limit key per user: 1 request per token per Redis operation
Unique active users/sec: ~10,000 (20% of 50k req/sec are unique user windows)
Redis ops/sec for rate limiter: ~10,000 EVAL (Lua script) ops/sec
Redis single-thread throughput: ~100,000 ops/sec -> comfortable headroom

Key size: "rate_limit:{userId}:{windowMs}" ~60 bytes + counter (8 bytes) + expiry
Total Redis memory for limiter: 10,000 users × ~200 bytes = ~2 MB (negligible)
```

**Connection pool sizing:**
```
Required in-flight connections (Little's Law):
  throughput × downstream latency = 50,000 × 0.020s = 1,000 in-flight
Reactor Netty default upstream pool: 500 connections per pool → too small
Set: spring.cloud.gateway.httpclient.pool.max-connections=1500 (with headroom)
```

**Horizontal scaling:**
```
50,000 req/sec target at 50% CPU: 2 × 16-core nodes
  (Reactor Netty saturates ~50-100k req/sec per node when fully non-blocking)
Rate-limiter Redis: per-region cluster, NOT shared across regions (no cross-region latency on hot path)
```

---

## 3. High-Level Architecture

```
                          Internet
                              |
                    +---------+---------+
                    |    API Gateway     |
                    |  (Spring Cloud GW) |
                    |                   |
                    |  GlobalFilters:   |
                    |  1. TracingFilter  | <- injects trace/span IDs (order HIGHEST_PREC)
                    |  2. AuthFilter     | <- validates JWT (order 0)
                    |  3. MetricsFilter  | <- records counters/timers (order HIGHEST+1)
                    |  4. LoggingFilter  | <- times request, logs (order LOWEST_PREC)
                    |                   |
                    |  RouteFilters:    |
                    |  - RateLimiter    | <- Redis token bucket per user
                    |  - CircuitBreaker | <- Resilience4j per route
                    |  - RewritePath    | <- /api/v1/orders -> /orders
                    |  - AddHeader      | <- X-Gateway-Source
                    +---------+---------+
                              |
         +--------------------+--------------------+
         |                    |                    |
   [Order Service]    [User Service]    [Payment Service]
   lb://order-service  lb://user-service  lb://payment-service
         |                    |                    |
   [Inventory Svc]    [Product Svc]     [Notification Svc]

  Filter execution order (request):
    TracingFilter -> AuthFilter -> MetricsFilter -> [RouteFilters] -> backend
  Filter execution order (response):
    backend -> [RouteFilters] -> MetricsFilter.doFinally -> LoggingFilter.then
```

**Component inventory:**

| Component | Responsibility |
|---|---|
| `TracingFilter` (GlobalFilter, HIGHEST_PRECEDENCE) | Extract/inject B3 trace context into all downstream headers |
| `JwtAuthenticationFilter` (GlobalFilter, order=0) | Validate JWT; set `authenticated-user-id` attribute |
| `RequestLoggingFilter` (GlobalFilter, LOWEST_PRECEDENCE) | Record method/path/status/latency in structured logs |
| `GatewayRoutesConfig` | Route DSL: path/host predicates, per-route rate limiter and circuit breaker |
| `RateLimiterKeyResolver` | Compute rate-limit bucket key: `user:{id}` or `ip:{addr}` |
| `FallbackController` | Service-specific 503 responses with `retryAfterSeconds` hint |
| Resilience4j CB config (`application.yml`) | Per-route sliding-window, failure threshold, half-open probes |

---

## 4. Component Deep Dives

### 4.1 Route Configuration

```java
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder,
                                      RateLimiterKeyResolver keyResolver) {
        return builder.routes()
            .route("order-service", r -> r
                .path("/api/v1/orders/**")
                .filters(f -> f
                    .rewritePath("/api/v1/orders/(?<segment>.*)", "/orders/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(defaultRateLimiter())
                        .setKeyResolver(keyResolver)
                        .setDenyEmptyKey(false)
                        .setEmptyKeyStatus(HttpStatus.TOO_MANY_REQUESTS.name()))
                    .circuitBreaker(config -> config
                        .setName("order-service-cb")
                        .setFallbackUri("forward:/fallback/orders"))
                    .addRequestHeader("X-Gateway-Source", "api-gateway")
                    .retry(r2 -> r2.setRetries(2)
                        .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE)))
                .uri("lb://order-service"))

            .route("payment-service", r -> r
                .path("/api/v1/payments/**")
                .filters(f -> f
                    .rewritePath("/api/v1/payments/(?<segment>.*)", "/payments/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(strictRateLimiter())  // 20 req/sec, burst 30
                        .setKeyResolver(keyResolver))
                    .circuitBreaker(config -> config
                        .setName("payment-service-cb")
                        .setFallbackUri("forward:/fallback/payments")))
                .uri("lb://payment-service"))

            .route("user-service", r -> r
                .path("/api/v1/users/**")
                .filters(f -> f
                    .rewritePath("/api/v1/users/(?<segment>.*)", "/users/${segment}")
                    .circuitBreaker(config -> config
                        .setName("user-service-cb")
                        .setFallbackUri("forward:/fallback/users")))
                .uri("lb://user-service"))
            .build();
    }

    @Bean
    public RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(100, 200, 1); // 100 req/sec, burst 200
    }

    @Bean
    public RedisRateLimiter strictRateLimiter() {
        return new RedisRateLimiter(20, 30, 1);   // 20 req/sec, burst 30
    }
}
```

### 4.2 JWT Authentication Global Filter

```java
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/login", "/api/v1/auth/refresh", "/actuator/health");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) return chain.filter(exchange);

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing Authorization header");
        }

        try {
            Claims claims = parseJwt(authHeader.substring(7));
            String userId = claims.getSubject();
            String roles  = claims.get("roles", String.class);

            exchange.getAttributes().put("authenticated-user-id", userId);
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-User-ID", userId)
                .header("X-User-Roles", roles != null ? roles : "")
                .build();
            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return unauthorized(exchange, "Token expired");
        } catch (io.jsonwebtoken.JwtException e) {
            return unauthorized(exchange, "Invalid token");
        } catch (Exception e) {
            // Key service unavailable — fail open; backend services validate independently
            log.error("JWT validation error, failing open: {}", e.getMessage());
            return chain.filter(exchange);
        }
    }

    @Override public int getOrder() { return 0; }
}
```

### 4.3 BROKEN/FIX — Blocking Call in Reactive Filter

Spring Cloud Gateway runs on Netty's event loop: `2 × cores` threads handle all requests.
A single blocking call stalls an entire event-loop thread.

```java
// BROKEN: blocking JWKS fetch on the event-loop thread stalls all traffic
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String key = jwksClient.fetchKeyBlocking(kid); // blocks event-loop thread for ~50ms
    // At 50k req/sec: 32 threads × 50ms = 32 concurrent before queue backup
    return chain.filter(exchange);
}
```

```java
// FIX: reactive JWKS fetch with in-process key cache — never blocks event loop
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return jwksCache.getKey(kid)              // returns Mono, non-blocking, cached 5 min
        .flatMap(key -> {
            Claims claims = validateToken(exchange, key);
            exchange.getAttributes().put("authenticated-user-id", claims.getSubject());
            return chain.filter(exchange);
        });
}
// jwksCache: Caffeine with expireAfterWrite(5m), refreshAfterWrite(4m) to avoid TTL spike
```

The rule: every line inside a `GlobalFilter.filter()` must return a `Mono` or complete
synchronously in sub-millisecond time. Move all I/O to `Schedulers.boundedElastic()` at
minimum; key lookups must be cached in-process.

### 4.4 Rate Limiter Key Resolver

```java
@Component
public class RateLimiterKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String userId = exchange.getAttribute("authenticated-user-id");
        if (userId != null) return Mono.just("user:" + userId);

        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null) return Mono.just("ip:" + forwarded.split(",")[0].trim());
        var addr = exchange.getRequest().getRemoteAddress();
        return Mono.just("ip:" + (addr != null ? addr.getAddress().getHostAddress() : "unknown"));
    }
}
```

### 4.5 BROKEN/FIX — Rate Limiter Fail-Closed

```java
// BROKEN: Redis failure propagates and 503s ALL traffic (fail-closed)
@Bean
public RedisRateLimiter redisRateLimiter() {
    return new RedisRateLimiter(100, 200); // throws if Redis down -> 503 for every request
}
```

```java
// FIX: wrap limiter so Redis failure degrades to allow + alert (fail-open)
@Bean
public RateLimiter<RedisRateLimiter.Config> resilientRateLimiter(
        RedisRateLimiter delegate, MeterRegistry meters) {
    return new RateLimiter<>() {
        @Override
        public Mono<Response> isAllowed(String routeId, String id) {
            return delegate.isAllowed(routeId, id)
                .onErrorResume(ex -> {
                    meters.counter("gateway.ratelimit.failopen").increment();
                    log.warn("Rate limiter Redis unavailable, failing open", ex);
                    return Mono.just(new Response(true, Map.of()));
                });
        }
        // delegate remaining methods...
    };
}
```

### 4.6 Circuit Breaker Configuration and Fallback

```yaml
resilience4j:
  circuitbreaker:
    instances:
      order-service-cb:
        sliding-window-size: 10
        failure-rate-threshold: 50       # open when 50% of last 10 calls fail
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: true
      payment-service-cb:
        sliding-window-size: 10
        failure-rate-threshold: 30       # stricter for payment route
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 2
```

```java
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/orders") @PostMapping("/orders")
    public Mono<ResponseEntity<Map<String, Object>>> ordersFallback(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "ORDER_SERVICE_UNAVAILABLE",
            "message", "Order service is temporarily unavailable. Please retry in 30 seconds.",
            "retryAfterSeconds", 30,
            "timestamp", Instant.now().toString())));
    }

    @GetMapping("/payments") @PostMapping("/payments")
    public Mono<ResponseEntity<Map<String, Object>>> paymentsFallback(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "PAYMENT_SERVICE_UNAVAILABLE",
            "message", "Payment service is temporarily unavailable. Your payment has NOT been processed.",
            "retryAfterSeconds", 60,
            "timestamp", Instant.now().toString())));
    }
}
```

---

## 5. Design Decisions & Tradeoffs

| Decision | Choice | Alternative | Rationale |
|---|---|---|---|
| Gateway model | Reactive (WebFlux/Netty) | Servlet (Tomcat) | 50k req/sec on 32 event-loop threads vs 50k Tomcat threads; Reactor Netty achieves gateway overhead in microseconds not milliseconds |
| Auth failure mode | Fail-open (key service down → pass through) | Fail-closed → 401 | Backend services also validate JWTs; key-service blip should not cause full API outage |
| Rate-limiter failure mode | Fail-open → allow + alert | Fail-closed → 503 all traffic | Rate limiting is protective, not correctness; losing it briefly is safer than total outage |
| Redis vs in-memory rate limiting | Redis (shared) | Per-JVM counter | N gateway instances with per-JVM counters allow N×1000 req/min; Redis enforces the global limit |
| Route config | Code DSL | YAML | Java DSL enables conditional logic and type safety; YAML is simpler for ops but less composable |
| Route ordering | Most-specific first | Any order | First-match-wins semantics; wildcard before specific swallows the specific route silently |

### Spring Cloud Gateway vs Kong vs Nginx

| Gateway | Customization | Language | Spring Integration | Ops cost |
|---|---|---|---|---|
| Spring Cloud Gateway (chosen) | Excellent — Java code | Java | Native | Low — same JVM |
| Kong | Good — Lua plugins | Lua/Go | API-only | Medium |
| Nginx + OpenResty | Limited — Lua scripts | Lua | None | Low, very fast |
| AWS API Gateway | Config only | N/A | None | Zero ops |

---

## 6. Real-World Implementations

**Netflix (Zuul → Zuul2 → cloud-native):** Netflix built Zuul 1 as a servlet-based gateway and hit thread exhaustion under long-polling traffic. Zuul 2 migrated to Netty async I/O — the same architectural shift Spring Cloud Gateway embodies. Netflix runs Zuul 2 with per-route `FallbackHandler` instances and health-check-based route toggling, with JMX dynamically updating filter chains without restarts.

**Cloudflare Workers (edge gateway):** Cloudflare's API gateway runs Lua/V8 workers at the edge — closest analogue to Spring Cloud Gateway's `GlobalFilter` chain. Each worker is a stateless function; JWT validation uses cached public keys fetched from a regional key server, with fail-open for key-fetch failures (exactly the pattern in §4.3).

**Airbnb (SmartStack → Nginx → SCG):** Airbnb migrated from Nginx-based routing to a Spring Cloud Gateway-based internal API platform when they needed service-discovery-native routing (Eureka `lb://` URIs) and per-route Resilience4j circuit breakers without writing Lua scripts. The migration eliminated a separate Nginx process per service and unified gateway metrics in their Micrometer/Prometheus stack.

**Stripe API Gateway:** Uses a custom Go gateway with per-endpoint rate limiting stored in Redis with Lua scripts for atomic token bucket — conceptually identical to Spring Cloud Gateway's `RedisRateLimiter`. Stripe's gateway is notably more strict: rate-limit violations return 429 with `Retry-After` headers, and the gateway fails-closed for payment endpoints while failing-open for informational endpoints (exactly the two-tier approach in this design).

**AWS ALB + Lambda Authorizers:** In serverless architectures, AWS ALB plays the role of the gateway with JWT validation delegated to Lambda authorizers. The failure mode is fail-open by default — if the authorizer Lambda times out, ALB allows the request through to the backend. This matches the fail-open JWT approach in §4.2.

---

## 7. Technologies & Tools

| Component | Spring Cloud Gateway | Kong | Nginx + Lua | Traefik |
|---|---|---|---|---|
| Routing model | Reactive (Reactor/Netty) | Nginx-based | Nginx event loop | Go goroutines |
| Auth plugin | Custom `GlobalFilter` | Built-in JWT plugin | lua-resty-jwt | Forward Auth middleware |
| Rate limiting | Redis Lua via `RedisRateLimiter` | Redis + Lua | lua-resty-limit-traffic | Redis-based traefik plugin |
| Circuit breaker | Resilience4j integration | None built-in | None built-in | None built-in |
| Service discovery | Eureka/Consul `lb://` | DNS / Consul | Upstream DNS | Docker/K8s labels |
| Config hot-reload | `RefreshRoutesEvent` | Admin API | `nginx -s reload` (process restart) | K8s Ingress controller |
| Observability | Micrometer native | Kong Vitals plugin | Nginx logs + Prometheus | Micrometer |

---

## 8. Operational Playbook

### (a) Metrics to Monitor

- `gateway.request.duration` (timer, by route + status): alert if P99 > 50ms
- `gateway.request.rate_limited` (counter, by route): alert if >1% of traffic is 429
- `gateway.ratelimit.failopen` (counter): alert immediately — Redis limiter is degraded
- Resilience4j `resilience4j.circuitbreaker.state`: alert if any CB in OPEN state > 60s

### (b) Distributed Trace Propagation Check

Every request must have a `traceparent` (W3C) or `X-B3-TraceId` (Zipkin) header injected
by `TracingFilter`. Verify in Grafana Tempo/Jaeger: filter spans by `service.name=gateway`
and confirm every span has a downstream child span in the target service.

### (c) Incident Runbooks

**Runbook 1: Redis rate limiter degraded (failopen counter rising)**
- Symptom: `gateway.ratelimit.failopen` counter nonzero; all clients getting through regardless of configured limits
- Diagnose: `redis-cli ping` against rate-limit Redis; check `gateway.ratelimit.failopen` counter trend; check Redis Sentinel logs for leader election
- Mitigate: traffic is flowing (fail-open); downstream circuit breakers protect backends; notify Redis oncall; do NOT restart gateway (would lose existing token bucket state)
- Resolve: once Redis primary is healthy, verify limiter counter stops incrementing; spot-check 429 responses by triggering threshold with curl

**Runbook 2: Circuit breaker in OPEN state for critical route**
- Symptom: all requests to `/api/v1/payments` return 503 from fallback; `resilience4j.circuitbreaker.state{name=payment-service-cb} == OPEN`
- Diagnose: check payment service health endpoint directly (`curl lb://payment-service/actuator/health`); check `payment-service-cb` failure rate in last sliding window (10 calls)
- Mitigate: if payment service is recovering, wait for half-open probe (3 allowed calls after `wait-duration-in-open-state=60s`); if backends are healthy but gateway has wrong target, refresh routes via `POST /actuator/gateway/refresh`
- Resolve: verify CB transitions HALF_OPEN → CLOSED; confirm 200s from payment service

**Runbook 3: Route ordering regression (health checks returning 401)**
- Symptom: load balancer marks all gateway instances unhealthy; all `GET /actuator/health` return 401; upstream 503 storm
- Diagnose: `curl -v /actuator/health` from outside — 401 response with `X-Auth-Error` header indicates auth filter matched before the public-path exclusion
- Mitigate: immediate rollback to previous route configuration (GitOps); if rollback unavailable, add `/actuator/health` to `PUBLIC_PATHS` list and restart
- Resolve: add integration test asserting `GET /actuator/health` returns 200 without Authorization header; add to CI pipeline

**Runbook 4: Gateway OOM from response body buffering**
- Symptom: JVM OOM on gateway; heap dumps show `DataBuffer` or `byte[]` objects dominating; triggered by large file download traffic
- Diagnose: check if any filter decorates `ServerHttpResponse.writeWith()` without a size guard; use MAT or heap dump analysis for `DataBuffer` retention roots
- Mitigate: identify and disable the buffering filter; add a `contentLength > 8192` guard to any body-reading filter; set JVM `-Xmx` with 2-3× headroom above expected reactive steady-state heap
- Resolve: load test with file download traffic before re-enabling the filter

---

## 9. Common Pitfalls & War Stories

### War Story 1: Route Ordering Sends Health Checks Through Auth — Fleet Drained

A wildcard `/api/**` route with JWT auth was declared before the `/api/health` path in the route DSL. Spring Cloud Gateway uses first-match-wins. Health checks returned 401, the load balancer marked all instances unhealthy, and the entire fleet was pulled from rotation — a self-inflicted outage.

```yaml
# BROKEN: wildcard route declared first, matches /api/health -> 401
routes:
  - id: api
    uri: lb://backend
    predicates:
      - Path=/api/**           # matches EVERYTHING, including /api/health
    filters:
      - JwtAuthentication
  - id: health
    uri: lb://backend
    predicates:
      - Path=/api/health       # never reached
```

```yaml
# FIX: most-specific paths declared first
routes:
  - id: health                 # specific, no auth
    uri: lb://backend
    predicates:
      - Path=/api/health
  - id: api                    # wildcard last
    uri: lb://backend
    predicates:
      - Path=/api/**
    filters:
      - JwtAuthentication
```

**Impact:** 12 minutes of full outage. Resolution: route ordering fix + CI integration test asserting `GET /actuator/health` returns 200 without a token.

### War Story 2: Unbounded Response Buffering — OOM Takes Down All Routes

A custom logging filter accumulated the full `Flux<DataBuffer>` body for every response to log it. When a batch export API returned 50 MB ZIP files, the gateway heap filled with DataBuffer objects and JVM OOM-killed — taking all routes down simultaneously.

```java
// BROKEN: joins entire response body into heap for every request
@Override
public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
    return DataBufferUtils.join(Flux.from(body)) // buffers 50 MB in heap!
        .flatMap(buf -> { logBody(buf); return super.writeWith(Mono.just(buf)); });
}
```

```java
// FIX: only buffer small bodies; stream large ones untouched
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long contentLength = exchange.getRequest().getHeaders().getContentLength();
    if (contentLength < 0 || contentLength > 8_192) {
        return chain.filter(exchange); // do not buffer — stream through
    }
    // ... bounded buffering only for small bodies with DataBufferUtils.release()
    return chain.filter(exchange);
}
```

**Impact:** 8 minutes of outage during peak hours; 3 customer SLA violations. Resolution: size guard + `maxmemory` limit on DataBuffer pool.

### War Story 3: In-Memory Rate Limiting Across Multiple Gateway Instances

Early design used a per-JVM `AtomicLong` token bucket instead of Redis. With 4 gateway instances and a configured limit of 1,000 req/min, clients could actually send 4,000 req/min — each instance maintained its own independent counter. A scraping bot discovered this and hammered the product search endpoint, causing N+1 query storms in the database.

Fix: move to Redis-backed `RedisRateLimiter` which uses a Lua script to atomically decrement a shared counter. Per-instance counters are fundamentally broken for distributed rate limiting regardless of how precise they are locally.

---

## 10. Capacity Planning

### Filter latency budget

```
Event-loop threads:   2 x 16 cores = 32 threads
Target throughput:    50,000 req/sec
Time per request on event loop: 50,000 / 32 = 1,562 req/thread/sec = 0.64ms max per request

5 filters each must complete in < 0.13ms to stay under the 0.64ms budget.
Any filter doing I/O must be reactive (Mono-returning); blocking I/O stalls the thread.
```

### Throughput and thread math

```
Non-blocking request: occupies an event-loop thread only for nanoseconds of CPU per hop
Effective throughput: ~50,000-100,000 req/sec per 16-core node (TLS + JSON overhead)
Horizontal scale:     2-3 nodes for 50k req/sec target + HA failover
```

### Connection and memory budget

```
Upstream pool (Reactor Netty):  default 500 per pool
Required (Little's Law):        50,000 x 0.020s = 1,000 in-flight -> set max-connections=1500

JVM heap:                       2-4 GB handles 50k req/sec in reactive model
                                Watch for DataBuffer leaks (not heap size per se)

Redis memory for rate limiter:  10,000 active users x 200 bytes = ~2 MB (negligible)
Redis connections (Lettuce):    single multiplexed connection by default; use small pool
                                (8-16) for pipelined workloads at 50k+ ops/sec
```

---

## 11. Interview Discussion Points

**Q: How does the Redis rate limiter handle a Redis cluster failure?**

A: By default `RedisRateLimiter` throws and propagates the error as a 503. The fix is to wrap it with `onErrorResume` that returns an allow-response on Redis failure (fail-open), increment a `gateway.ratelimit.failopen` counter, and alert on it. Rate limiting is a protective measure, not a correctness requirement — a brief lapse is far less harmful than rejecting all traffic. Downstream circuit breakers and connection limits still protect services during the window.

**Q: How do you prevent circuit breakers from tripping on client errors (4xx)?**

A: Resilience4j's circuit breaker only counts exceptions (network errors, timeouts) as failures — HTTP 4xx responses are not exceptions in the reactive pipeline. To explicitly record only 5xx as failures, configure `recordHttpStatuses: SERVER_ERROR`. Also ignore 429 (expected behavior, not a service failure) via `ignoreExceptions: [TooManyRequestsException.class]`.

**Q: Why does filter latency arithmetic understate the real risk in a reactive gateway?**

A: Spring Cloud Gateway runs on Netty's event loop with `2 × cores` threads. Summing per-filter CPU time (5 × 0.1ms = 0.5ms) ignores the failure mode: a single blocking call occupies an event-loop thread for the full I/O duration, and at 50k req/sec the small thread pool saturates instantly, stalling all routes. The correct rule: every filter must be strictly non-blocking; unavoidable blocking work goes on `Schedulers.boundedElastic()`.

**Q: Why does route ordering matter, and how do you prevent ordering bugs?**

A: Route predicates are evaluated in declaration order; the first match wins. A broad wildcard (`/api/**`) declared before a specific path (`/api/health`) will match health checks and apply auth filters, returning 401 and causing the load balancer to drain the fleet. Always declare routes from most-specific to least-specific, and add an integration test asserting critical paths (`/actuator/health`) resolve to the intended route without auth.

**Q: How do you implement route discovery — loading routes from a database at runtime?**

A: Implement `RouteDefinitionRepository` backed by a database and call `context.publishEvent(new RefreshRoutesEvent(this))` after a route change. Spring Cloud Gateway reloads all routes from the repository without a restart. The `/actuator/gateway/refresh` endpoint triggers this from outside. Combined with `/actuator/gateway/routes` (lists all current routes) and `/actuator/gateway/routefilters` (available filter factories), this makes route changes a database update + HTTP call rather than a deployment.

**Q: How do you handle WebSocket upgrades through the gateway?**

A: Spring Cloud Gateway supports WebSocket proxying natively via `WebsocketRoutingFilter`. A route with a `ws://` URI handles the HTTP upgrade handshake. JWT validation in `JwtAuthenticationFilter` applies to the initial HTTP upgrade — the authenticated session is maintained for the WebSocket connection lifetime. Rate limiting applies to connection establishment, not individual frames.

**Q: How do you size the upstream connection pool?**

A: Use Little's Law: `in-flight = throughput × latency`. For 50,000 req/sec with 20ms downstream P99, in-flight is 50,000 × 0.020 = 1,000 connections. Add 50% headroom → set `max-connections=1500`. Reactor Netty defaults to 500 per pool, which bottlenecks this workload. Monitor `reactor.netty.connection.provider.pending.acquired.size`; rising pending-acquire counts signal either pool exhaustion or slow backends.

**Q: How does the architecture change for multi-region deployment?**

A: Front regional gateway fleets with a global anycast layer (AWS Global Accelerator) that routes clients to the nearest healthy region and fails over in tens of seconds. Each region runs an independent gateway with region-local rate-limiter Redis (no cross-region calls on the hot path), resolves `lb://` to region-local services only, and uses a globally replicated JWKS so JWT validation needs no cross-region traffic. Route configuration is GitOps-managed and identical per region. A failed region is drained at the global accelerator; clients reconverge on the next-nearest region.

**Q: Should the gateway strip the JWT before forwarding to backends, or keep it?**

A: It depends on the trust model. In a gateway-only auth model, the gateway validates and strips the JWT, forwarding only `X-User-ID` and `X-User-Roles` headers — backends trust these headers implicitly and skip their own JWT validation. This is simpler but creates a trust boundary: any service that can make internal network calls can forge `X-User-ID`. In a zero-trust model, the gateway forwards the original JWT and backends validate independently — more secure but every backend needs JWT validation logic. The design here uses gateway-only auth (strips JWT, adds headers) which is appropriate when internal network access is already controlled.

**Q: How do you debug why a specific request is being routed incorrectly?**

A: Enable `logging.level.org.springframework.cloud.gateway=TRACE` to log every predicate evaluation and route match decision. In production where TRACE is too verbose, add a `GlobalFilter` that logs the matched route ID (`exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)`) at INFO level. Hit `/actuator/gateway/routes` to inspect all configured routes and their predicates. Hit `/actuator/gateway/globalfilters` to see all active global filters and their execution order.
