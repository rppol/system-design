# API Gateway for Microservices Platform with Spring Cloud Gateway

## Problem Statement

Design an API gateway that serves as the single entry point for a microservices platform with 20+ backend services. The gateway must:

- Route requests to the correct backend service based on path, host, and headers
- Validate JWT tokens and forward user identity to backend services (no per-service auth)
- Rate-limit each client to 1,000 requests per minute using Redis-backed counting
- Apply circuit breakers per route to prevent cascade failures
- Log every request and response with timing information
- Propagate distributed traces to all downstream services using Micrometer/OpenTelemetry
- Handle 50,000 req/sec at peak with sub-10ms gateway overhead

Constraints: Spring Cloud Gateway 4.x (reactive, WebFlux-based), Redis 7 for rate limiting, Resilience4j for circuit breakers, Java 21 virtual threads are not applicable here because the gateway is fully reactive (Project Reactor).

---

## Architecture Overview

```
                          Internet
                              |
                    +---------+---------+
                    |    API Gateway     |
                    |  (Spring Cloud GW) |
                    |                   |
                    |  GlobalFilters:   |
                    |  1. TracingFilter  | <- injects trace/span IDs
                    |  2. AuthFilter    | <- validates JWT
                    |  3. LoggingFilter | <- times request, logs
                    |                   |
                    |  RouteFilters:    |
                    |  - RateLimiter    | <- Redis token bucket per user
                    |  - CircuitBreaker | <- Resilience4j per route
                    |  - RewritePath    | <- strips /api/v1/orders -> /orders
                    +---------+---------+
                              |
         +--------------------+--------------------+
         |                    |                    |
   [Order Service]    [User Service]    [Product Service]
   :8081              :8082              :8083
         |                    |                    |
   [Payment Svc]      [Inventory Svc]   [Notification Svc]
   :8084              :8085              :8086

  Rate Limit Store:
  [Redis Cluster]
  - key: rate_limit:{clientId}:{window}
  - value: request count
  - TTL: 60s rolling window
```

---

## Key Design Decisions

### 1. Global Filters vs Route-Specific Filters

Authentication (JWT validation) and distributed tracing apply to every route, so they are implemented as `GlobalFilter` beans. Rate limiting and circuit breakers vary by route (admin routes may have higher limits, payment routes have stricter circuit breakers), so they are configured as `GatewayFilter` factories on individual routes.

### 2. Redis Token Bucket for Rate Limiting

Spring Cloud Gateway ships with `RequestRateLimiterGatewayFilterFactory` which uses a Redis Lua script to implement the token bucket algorithm. The Lua script executes atomically on the Redis side, avoiding race conditions from distributed counters. The key resolver determines how to bucket requests — by authenticated user ID for logged-in requests, by IP for unauthenticated requests.

### 3. Fail-Open vs Fail-Closed for JWT Validation

If the JWT secret key service is unavailable (key rotation endpoint down), the gateway fails open — requests pass through to let the backend services make their own auth decisions. This was chosen over fail-closed because the backend services also validate JWTs, providing a second layer of defense. Fail-closed would cause a full outage if the key service has a brief blip.

### 4. Reactive Pipeline for Sub-10ms Overhead

Spring Cloud Gateway is built on WebFlux (Project Reactor) and Netty. All filters are non-blocking. The Redis rate limiter calls are reactive. This means one gateway instance can handle 50,000 req/sec with ~8 threads, versus a servlet-based gateway which would need ~50,000 threads in the worst case.

### 5. Per-Route Circuit Breakers with Separate Fallbacks

Each backend service gets its own circuit breaker instance so that a failing payment service does not affect routing to the user service. The fallback for each circuit breaker is configured to return a service-specific error response (not a generic 503) so clients can differentiate between "payment service down" and "inventory service down."

---

## Implementation

### Gateway Application Main Class

```java
package com.rutik.systemdesign.spring.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

### Route Configuration (Java DSL)

```java
package com.rutik.systemdesign.spring.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class GatewayRoutesConfig {

    private final RateLimiterKeyResolver rateLimiterKeyResolver;

    public GatewayRoutesConfig(RateLimiterKeyResolver rateLimiterKeyResolver) {
        this.rateLimiterKeyResolver = rateLimiterKeyResolver;
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()

            // Order Service Route
            .route("order-service", r -> r
                .path("/api/v1/orders/**")
                .filters(f -> f
                    .rewritePath("/api/v1/orders/(?<segment>.*)", "/orders/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(rateLimiterKeyResolver)
                        .setDenyEmptyKey(false)
                        .setEmptyKeyStatus(HttpStatus.TOO_MANY_REQUESTS.name()))
                    .circuitBreaker(config -> config
                        .setName("order-service-cb")
                        .setFallbackUri("forward:/fallback/orders"))
                    .addRequestHeader("X-Gateway-Source", "api-gateway")
                    .retry(retryConfig -> retryConfig
                        .setRetries(2)
                        .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE)))
                .uri("lb://order-service"))

            // Payment Service Route — stricter circuit breaker
            .route("payment-service", r -> r
                .path("/api/v1/payments/**")
                .filters(f -> f
                    .rewritePath("/api/v1/payments/(?<segment>.*)", "/payments/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(paymentRateLimiter())  // stricter limits
                        .setKeyResolver(rateLimiterKeyResolver))
                    .circuitBreaker(config -> config
                        .setName("payment-service-cb")
                        .setFallbackUri("forward:/fallback/payments")))
                .uri("lb://payment-service"))

            // User Service — also matches by Host header for partner API
            .route("user-service-partner", r -> r
                .host("partners.api.example.com")
                .and()
                .path("/api/v1/users/**")
                .filters(f -> f
                    .rewritePath("/api/v1/users/(?<segment>.*)", "/users/${segment}")
                    .addRequestHeader("X-Partner-Request", "true")
                    .circuitBreaker(config -> config
                        .setName("user-service-cb")
                        .setFallbackUri("forward:/fallback/users")))
                .uri("lb://user-service"))

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
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter redisRateLimiter() {
        // 100 requests per second, burst of 200
        // replenishRate=100, burstCapacity=200, requestedTokens=1
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(100, 200, 1);
    }

    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter paymentRateLimiter() {
        // More restrictive for payment endpoints: 20 req/sec, burst 30
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(20, 30, 1);
    }
}
```

### Rate Limiter Key Resolver

```java
package com.rutik.systemdesign.spring.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimiterKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        // Primary: use authenticated user ID from JWT (set by AuthFilter upstream)
        String userId = exchange.getAttribute("authenticated-user-id");
        if (userId != null) {
            return Mono.just("user:" + userId);
        }

        // Fallback: use client IP address
        String clientIp = getClientIp(exchange);
        return Mono.just("ip:" + clientIp);
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take only the first IP (client IP), not intermediate proxies
            return forwardedFor.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return (remoteAddress != null) ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
```

### JWT Authentication Global Filter

```java
package com.rutik.systemdesign.spring.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // Paths that do not require authentication
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/actuator/health"
    );

    private final JwtProperties jwtProperties;

    public JwtAuthenticationFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders()
                                    .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parseJwt(token);
            String userId = claims.getSubject();
            String roles = claims.get("roles", String.class);

            // Store user info as exchange attributes so downstream filters and rate limiter can use it
            exchange.getAttributes().put("authenticated-user-id", userId);
            exchange.getAttributes().put("authenticated-user-roles", roles);

            // Forward user identity to backend services via headers
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-ID", userId)
                .header("X-User-Roles", roles != null ? roles : "")
                // Remove the original JWT — backends don't need it if they trust the gateway headers
                // In zero-trust architectures, keep the JWT and let backends verify independently
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return unauthorizedResponse(exchange, "Token expired");
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return unauthorizedResponse(exchange, "Invalid token");
        } catch (Exception e) {
            // Key service unavailable — fail open (see design decision #3)
            log.error("JWT validation error — failing open: {}", e.getMessage());
            return chain.filter(exchange);
        }
    }

    private Claims parseJwt(String token) {
        Key key = Keys.hmacShaKeyFor(
            jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                   .setSigningKey(key)
                   .build()
                   .parseClaimsJws(token)
                   .getBody();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", message);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        // Run before rate limiter (order 1) but after tracing filter (order -1)
        return 0;
    }
}
```

### Request/Response Logging Global Filter with Timing

```java
package com.rutik.systemdesign.spring.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String START_TIME_ATTR = "gateway.start.time";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        exchange.getAttributes().put(START_TIME_ATTR, Instant.now().toEpochMilli());

        String userId = exchange.getAttribute("authenticated-user-id");
        String traceId = exchange.getRequest().getHeaders().getFirst("X-B3-TraceId");

        log.info("GATEWAY_REQUEST method={} path={} userId={} traceId={} clientIp={}",
                 request.getMethod(),
                 request.getURI().getPath(),
                 userId != null ? userId : "anonymous",
                 traceId,
                 getClientIp(request));

        return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> logResponse(exchange)));
    }

    private void logResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        Long startTime = exchange.getAttribute(START_TIME_ATTR);
        long durationMs = (startTime != null)
                          ? Instant.now().toEpochMilli() - startTime
                          : -1;

        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        String userId = exchange.getAttribute("authenticated-user-id");
        String traceId = exchange.getRequest().getHeaders().getFirst("X-B3-TraceId");

        if (durationMs > 5000) {
            log.warn("GATEWAY_SLOW_RESPONSE method={} path={} status={} durationMs={} userId={} traceId={}",
                     exchange.getRequest().getMethod(),
                     exchange.getRequest().getURI().getPath(),
                     statusCode,
                     durationMs,
                     userId,
                     traceId);
        } else {
            log.info("GATEWAY_RESPONSE method={} path={} status={} durationMs={} userId={} traceId={}",
                     exchange.getRequest().getMethod(),
                     exchange.getRequest().getURI().getPath(),
                     statusCode,
                     durationMs,
                     userId,
                     traceId);
        }
    }

    private String getClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null) return forwarded.split(",")[0].trim();
        var addr = request.getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        // Run last (highest order number) so timing captures entire filter chain
        return Ordered.LOWEST_PRECEDENCE;
    }
}
```

### Distributed Tracing Filter (Micrometer/B3 Propagation)

```java
package com.rutik.systemdesign.spring.gateway;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TracingFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;
    private final Propagator propagator;

    public TracingFilter(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Extract existing trace context from incoming request headers (if any)
        Propagator.Getter<ServerHttpRequest> getter =
            (request, key) -> request.getHeaders().getFirst(key);

        Span span = propagator.extract(exchange.getRequest(), getter)
                              .name("gateway.route")
                              .tag("http.method", exchange.getRequest().getMethod().name())
                              .tag("http.path", exchange.getRequest().getURI().getPath())
                              .start();

        // Inject trace headers into the forwarded request
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        propagator.inject(span.context(),
                          requestBuilder,
                          (builder, key, value) -> builder.header(key, value));

        ServerHttpRequest mutatedRequest = requestBuilder.build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                    .doFinally(signalType -> {
                        span.tag("http.status",
                                 String.valueOf(exchange.getResponse().getStatusCode()));
                        span.end();
                    });
    }

    @Override
    public int getOrder() {
        // Must run first — before auth filter — so trace context is available to all filters
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

### Circuit Breaker Fallback Controller

```java
package com.rutik.systemdesign.spring.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @GetMapping("/orders")
    @PostMapping("/orders")
    public Mono<ResponseEntity<Map<String, Object>>> ordersFallback(ServerWebExchange exchange) {
        log.warn("Circuit breaker open for order-service, returning fallback");
        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "ORDER_SERVICE_UNAVAILABLE",
                "message", "Order service is temporarily unavailable. Please try again in 30 seconds.",
                "timestamp", Instant.now().toString(),
                "retryAfterSeconds", 30
            )));
    }

    @GetMapping("/payments")
    @PostMapping("/payments")
    public Mono<ResponseEntity<Map<String, Object>>> paymentsFallback(ServerWebExchange exchange) {
        log.warn("Circuit breaker open for payment-service, returning fallback");
        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "PAYMENT_SERVICE_UNAVAILABLE",
                "message", "Payment service is temporarily unavailable. Your payment has NOT been processed.",
                "timestamp", Instant.now().toString(),
                "retryAfterSeconds", 60
            )));
    }

    @GetMapping("/users")
    public Mono<ResponseEntity<Map<String, Object>>> usersFallback(ServerWebExchange exchange) {
        log.warn("Circuit breaker open for user-service, returning fallback");
        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "USER_SERVICE_UNAVAILABLE",
                "message", "User service is temporarily unavailable.",
                "timestamp", Instant.now().toString()
            )));
    }
}
```

### Resilience4j Circuit Breaker Configuration

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      order-service-cb:
        sliding-window-size: 10
        failure-rate-threshold: 50       # Open when 50% of last 10 calls fail
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: true
      payment-service-cb:
        sliding-window-size: 10
        failure-rate-threshold: 30       # Stricter: open at 30% failure rate
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 2
        register-health-indicator: true
      user-service-cb:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s

spring:
  cloud:
    gateway:
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
      globalcors:
        cors-configurations:
          "[/**]":
            allowed-origins: "https://app.example.com"
            allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"
            allowed-headers: "*"
            max-age: 3600
  data:
    redis:
      host: redis-cluster.internal
      port: 6379
      lettuce:
        pool:
          max-active: 100
          max-idle: 20
```

### JWT Properties

```java
package com.rutik.systemdesign.spring.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long expirationSeconds = 3600;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getExpirationSeconds() { return expirationSeconds; }
    public void setExpirationSeconds(long expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }
}
```

### Micrometer Custom Metrics for Gateway

```java
package com.rutik.systemdesign.spring.gateway;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
public class MetricsFilter implements GlobalFilter, Ordered {

    private final MeterRegistry meterRegistry;

    public MetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant start = Instant.now();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // Tag route for the metric
        String routeTag = resolveRoute(path);

        return chain.filter(exchange)
                    .doFinally(signal -> {
                        HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
                        int statusCode = (status != null) ? status.value() : 0;
                        long durationMs = Duration.between(start, Instant.now()).toMillis();

                        // Record latency histogram
                        Timer.builder("gateway.request.duration")
                             .tag("route", routeTag)
                             .tag("method", method)
                             .tag("status", String.valueOf(statusCode))
                             .register(meterRegistry)
                             .record(Duration.ofMillis(durationMs));

                        // Increment request counter
                        Counter.builder("gateway.request.total")
                               .tag("route", routeTag)
                               .tag("status", String.valueOf(statusCode))
                               .register(meterRegistry)
                               .increment();

                        // Track 4xx and 5xx separately for alerting
                        if (statusCode >= 500) {
                            Counter.builder("gateway.request.errors")
                                   .tag("route", routeTag)
                                   .tag("error_type", "server_error")
                                   .register(meterRegistry)
                                   .increment();
                        } else if (statusCode == 429) {
                            Counter.builder("gateway.request.rate_limited")
                                   .tag("route", routeTag)
                                   .register(meterRegistry)
                                   .increment();
                        }
                    });
    }

    private String resolveRoute(String path) {
        if (path.startsWith("/api/v1/orders")) return "order-service";
        if (path.startsWith("/api/v1/payments")) return "payment-service";
        if (path.startsWith("/api/v1/users")) return "user-service";
        return "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
```

---

## Spring Components Used

| Spring Component | Purpose |
|---|---|
| `GlobalFilter` | Cross-cutting concerns applied to every route: JWT auth, logging, tracing, metrics |
| `RouteLocatorBuilder` | Java DSL for defining routes with predicates and per-route filters |
| `RequestRateLimiterGatewayFilterFactory` | Per-route rate limiting using Redis token bucket via Lua script |
| `RedisRateLimiter` | Configures token bucket parameters (replenishRate, burstCapacity) |
| `KeyResolver` | Reactive interface that computes the rate-limit bucket key per request |
| `CircuitBreakerFilterFactory` | Integrates Resilience4j per route with configurable fallback URI |
| `Ordered` | Controls global filter execution order (tracing first, logging last) |
| `@ConfigurationProperties` | Binds `jwt.*` YAML to `JwtProperties` bean |
| `MeterRegistry` (Micrometer) | Records request duration timers and counters per route and status |
| `Tracer` / `Propagator` (Micrometer Tracing) | Extracts and injects B3/W3C trace context across service boundaries |

---

## Tradeoffs and Alternatives

### Spring Cloud Gateway vs Kong vs Nginx

| Gateway | Customization | Language | Operational Cost | Spring Integration |
|---|---|---|---|---|
| Spring Cloud Gateway (chosen) | Excellent — Java code | Java | Low — same JVM | Native |
| Kong | Good — Lua plugins | Lua/Go | Medium — separate process | API-only |
| Nginx + OpenResty | Limited — Lua scripts | Lua | Low — very fast | None |
| AWS API Gateway | Limited — config only | N/A | Zero ops | None |

Spring Cloud Gateway was chosen because the team is Java-centric, it integrates natively with Spring Boot service discovery (Eureka/Consul), and custom filters are plain Java — no Lua or plugin APIs to learn.

### Redis Rate Limiting vs In-Memory Rate Limiting

In-memory rate limiting (token bucket per JVM instance) does not work correctly with multiple gateway instances — each instance would allow 1,000 req/min independently, resulting in up to N*1,000 req/min across N instances. Redis provides a shared counter that all instances read and write atomically via a Lua script. The tradeoff is a Redis network round-trip per request (~0.5ms in the same datacenter). A hybrid approach (local counter with a periodic Redis sync) is possible but complex and not necessary at this scale.

### Fail-Open vs Fail-Closed for JWT Validation

Fail-open was chosen for JWT key fetch failures because: (1) backend services also validate JWTs, providing defense in depth; (2) a key service outage should not cause a full API outage. In a zero-trust architecture where the gateway is the only auth boundary, fail-closed would be the correct choice. The decision depends on whether backends are trusted to validate independently.

### Synchronous vs Asynchronous Logging

Structured JSON logging is synchronous within the reactive pipeline's `doFinally` hook. This adds ~0.1ms per request. An alternative is to emit log entries to a ring buffer and flush asynchronously. Given that the logging filter runs outside the hot path (inside `then()`, after the response is sent), synchronous structured logging was chosen for simplicity.

---

## Interview Discussion Points

**Q: How does the Redis rate limiter handle a Redis cluster failure?**

A: By default, if the Redis call fails, Spring Cloud Gateway's `RedisRateLimiter` returns a response indicating the request is allowed (fail-open). The `setDenyEmptyKey(false)` setting also controls this — when the key cannot be resolved, the request is allowed. In production you should configure a fallback strategy: either fail-open (chosen here for availability) or rate-limit based on a local in-memory counter for the duration of the Redis outage.

**Q: How do you prevent the circuit breaker from tripping on client errors (4xx) rather than server errors (5xx)?**

A: Resilience4j's circuit breaker can be configured with `recordExceptions` and `ignoreExceptions`. By default, only exceptions (network errors, timeouts) trip the circuit breaker — HTTP 4xx responses do not because they are not exceptions in the reactive pipeline. Set `recordHttpStatuses: SERVER_ERROR` in the Resilience4j configuration to explicitly record only 5xx as failures. 429 Too Many Requests should also be ignored since it is expected behavior, not a service failure.

**Q: How do you implement route discovery — loading routes from a database instead of code?**

A: Implement a `RouteDefinitionRepository` that reads route definitions from a database or config server. Spring Cloud Gateway ships with `InMemoryRouteDefinitionRepository` by default. A `DatabaseRouteDefinitionRepository` would query a `routes` table and return `RouteDefinition` objects. Combined with `RefreshRoutesEvent`, routes can be reloaded at runtime by calling `context.publishEvent(new RefreshRoutesEvent(this))` after updating the database — no gateway restart required.

**Q: How do you handle WebSocket upgrades through the gateway?**

A: Spring Cloud Gateway supports WebSocket proxying natively. A route with a `ws://` or `wss://` URI prefix handles the upgrade handshake. The `WebsocketRoutingFilter` handles the WebSocket frames after the initial HTTP upgrade. JWT validation in the `JwtAuthenticationFilter` works for the initial HTTP upgrade request — the authenticated connection is then maintained for the WebSocket session lifetime. Rate limiting applies to the connection establishment, not individual frames.

**Q: How do you debug why a specific request is being routed incorrectly?**

A: Enable `logging.level.org.springframework.cloud.gateway=TRACE`. This logs every route predicate evaluation and which route matched. Alternatively, hit the `/actuator/gateway/routes` endpoint to see all configured routes with their predicates. The `/actuator/gateway/routefilters` endpoint shows all available filter factories. In a production environment where TRACE logging is too verbose, add a `GlobalFilter` that logs the matched route ID (available via `ServerWebExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)`) at INFO level.

**Q: How does the gateway handle request body modification — for example, injecting a request ID into the body?**

A: The reactive HTTP model streams the body as a `Flux<DataBuffer>`. Modifying the body requires buffering it fully in memory (losing streaming benefits) or using `ServerRequest` from WebFlux. Spring Cloud Gateway's `ModifyRequestBodyGatewayFilterFactory` handles this: it reads the body, applies a transformation function, and writes the modified body. The tradeoff is memory usage for large request bodies. For body injection of small fields (request ID, tenant ID), adding a header is preferred over body modification.

---

## Failure Scenarios and Recovery

The gateway is the single ingress for every downstream service, so a gateway failure is a total outage. The highest-stakes decisions are how the gateway behaves when its own dependencies (Redis for rate limiting, the discovery registry) fail.

### Failure: Rate-Limiter Redis Goes Down

Spring Cloud Gateway's `RequestRateLimiter` filter uses Redis to store token-bucket counters. If Redis becomes unreachable, the default `RedisRateLimiter` throws, and every request that hits a rate-limited route fails — turning a non-critical dependency (rate limiting) into a critical one. This is a fail-closed default, and it converts a Redis blip into a full traffic outage.

```
Redis down, default behavior (fail-closed):
  Client --> [ Gateway ] --RequestRateLimiter--X (Redis unreachable)
                                |
                          503 for ALL traffic
```

The decision: fail-open (allow traffic through when the limiter cannot make a decision) versus fail-closed (reject everything). For an API gateway, availability of the core proxy function usually outranks perfect rate-limit enforcement, so fail-open with a loud alert is the right default. Rate limiting is a protective measure, not a correctness requirement — losing it briefly is far less harmful than dropping all traffic.

```java
// BROKEN: limiter failure propagates and 503s all requests (fail-closed)
@Bean
public RedisRateLimiter redisRateLimiter() {
    return new RedisRateLimiter(100, 200); // throws if Redis is down
}
```

```java
// FIX: wrap the rate limiter so Redis failure degrades to allow + alert (fail-open)
@Bean
public RateLimiter<RedisRateLimiter.Config> resilientRateLimiter(RedisRateLimiter delegate,
                                                                 MeterRegistry meters) {
    return new RateLimiter<>() {
        @Override
        public Mono<Response> isAllowed(String routeId, String id) {
            return delegate.isAllowed(routeId, id)
                .onErrorResume(ex -> {
                    meters.counter("gateway.ratelimit.failopen").increment();
                    log.warn("Rate limiter Redis unavailable, failing open", ex);
                    // allow the request through; tokensRemaining headers omitted
                    return Mono.just(new Response(true, Map.of()));
                });
        }
        @Override public Map<String, Config> getConfig() { return delegate.getConfig(); }
        @Override public Class<Config> getConfigClass() { return delegate.getConfigClass(); }
        @Override public Map<String, Config> getMap() { return delegate.getMap(); }
    };
}
```

Recovery: when Redis Sentinel/cluster promotes a replica (typically ~30s), the limiter resumes enforcing. During the gap, traffic flows unthrottled, protected only by downstream service circuit breakers and connection limits — which is why those backstops must always be configured independently of the gateway limiter. Time-to-recovery for limiter enforcement equals Redis failover time (~30s); time-to-recovery for client traffic is zero because fail-open never dropped it.

### Failure: Service Discovery Registry Unreachable

If the gateway resolves routes via Eureka/Consul and the registry is unreachable, lb:// routes cannot resolve targets. Mitigation: enable registry client caching (`eureka.client.registry-fetch-interval-seconds`) so the last-known-good service list is retained, and the gateway keeps routing to cached instances. Recovery is automatic once the registry returns; the cache bridges the gap.

---

## Capacity Planning

### Filter Latency Budget

The gateway adds latency on every request, and filters run in sequence. The budget is unforgiving at high throughput.

```
Throughput:        100,000 req/sec
Filters per route: 5 (auth, rate-limit, header-rewrite, tracing, logging)

BROKEN (blocking filters, 0.1ms each but blocking the event loop):
  5 filters x 0.1ms = 0.5ms CPU per request, BUT if any blocks the
  Netty event-loop thread, effective added latency balloons.
  A single 10ms blocking call on an event-loop thread at 100k req/sec
  saturates the (cores x 2) event-loop threads almost instantly.
```

The constraint is not the arithmetic sum of filter times — it is that Spring Cloud Gateway runs on Netty's event loop, which has only `2 x cores` threads. Any blocking I/O on a filter (a synchronous Redis call, a blocking JWT key fetch) stalls the entire event loop.

```java
// BROKEN: blocking call inside a filter starves the Netty event loop
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String key = jwksClient.fetchKeyBlocking(kid); // blocks event-loop thread!
    return chain.filter(exchange);
}
```

```java
// FIX: all filter work must be non-blocking; fetch keys reactively with caching
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return jwksCache.getKey(kid)                 // returns Mono, non-blocking
        .flatMap(key -> { validate(exchange, key); return chain.filter(exchange); });
}
```

Target: keep each filter under 1ms and strictly non-blocking, so 5 filters add well under 5ms p99 and the event loop never stalls. Move any unavoidable blocking work to a bounded elastic scheduler (`publishOn(Schedulers.boundedElastic())`).

### Throughput and Thread Math

```
Netty event-loop threads:  2 x cores. On a 16-core node = 32 event-loop threads.
Each non-blocking request: occupies a thread only for microseconds of CPU per hop.
Sustained throughput:      ~50,000-100,000 req/sec per 16-core node when fully
                           non-blocking, limited by CPU for TLS + serialization.
Horizontal scale:          100,000 req/sec target -> 2-3 nodes behind an LB
                           plus headroom for failover.
```

### Connection and Memory Budget

```
Upstream connection pool:  Reactor Netty default 500 connections per pool.
                           For 100k req/sec with 20ms downstream latency:
                           concurrency = 100,000 x 0.020 = 2,000 in-flight.
                           -> raise max-connections to ~2,500 with headroom.
Heap:                      Reactive, low per-request footprint. ~2-4 GB heap
                           handles 100k req/sec; watch DataBuffer leaks instead.
```

---

## Additional Production War Stories

### War Story 1: Route Ordering Sent Health Checks Through Auth

A catch-all `/api/**` route with a JWT auth filter was declared before the unauthenticated `/api/health` route. Spring Cloud Gateway evaluates routes in declared order and uses the first match, so health checks hit the auth route and returned 401. The load balancer marked every instance unhealthy and pulled the entire fleet out of rotation — a self-inflicted outage.

```yaml
# BROKEN: wildcard route matches /api/health first -> 401 on health checks
spring:
  cloud:
    gateway:
      routes:
        - id: api
          uri: lb://backend
          predicates:
            - Path=/api/**          # matches EVERYTHING under /api, including /api/health
          filters:
            - JwtAuthentication
        - id: health
          uri: lb://backend
          predicates:
            - Path=/api/health      # never reached
```

```yaml
# FIX: order routes by specificity — most specific paths FIRST
spring:
  cloud:
    gateway:
      routes:
        - id: health                # specific, no auth — declared first
          uri: lb://backend
          predicates:
            - Path=/api/health
        - id: api                   # wildcard last
          uri: lb://backend
          predicates:
            - Path=/api/**
          filters:
            - JwtAuthentication
```

The rule: route predicate evaluation is first-match-wins in declaration order, so always order from most specific to least specific. Add an integration test that asserts `/api/health` returns 200 without a token to prevent regressions.

### War Story 2: Unbounded Response Buffering Caused OOM

A custom filter that logged response bodies cached the full `Flux<DataBuffer>` into memory for every response, including large file downloads. Under traffic the gateway heap filled with buffered multi-megabyte responses and the JVM OOM-killed, taking down all routes at once.

```java
// BROKEN: buffers the entire response body in memory for every request
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return DataBufferUtils.join(Flux.from(body)) // joins ENTIRE body in heap
                .flatMap(buf -> { logBody(buf); return super.writeWith(Mono.just(buf)); });
        }
    };
    return chain.filter(exchange.mutate().response(decorated).build());
}
```

```java
// FIX: only log small bodies under a size cap; stream large ones untouched
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long contentLength = exchange.getRequest().getHeaders().getContentLength();
    if (contentLength > 8_192) {
        return chain.filter(exchange); // do not buffer large/streamed responses
    }
    // ... bounded buffering only for small bodies, with DataBufferUtils.release()
    return chain.filter(exchange);
}
```

---

## Multi-Region Considerations

For a global API the gateway is deployed per region, fronted by a global anycast layer that routes users to the nearest healthy region.

```
                    [ AWS Global Accelerator / anycast IPs ]
                                   |
              +--------------------+--------------------+
              |                    |                    |
     [ Gateway us-east-1 ]  [ Gateway eu-west-1 ]  [ Gateway ap-south-1 ]
              |                    |                    |
     [ US services ]        [ EU services ]      [ APAC services ]
```

Design changes for multi-region:
- A global load balancer such as AWS Global Accelerator (or Cloudflare/Akamai anycast) advertises a single anycast IP and routes each client to the lowest-latency healthy region at the network layer, with sub-minute failover when a region's gateway health checks fail.
- Each region runs an independent gateway fleet with region-local rate-limiter Redis. Token buckets are per-region, not global; if you need a global quota, aggregate counts asynchronously rather than doing a synchronous cross-region Redis call on the hot path.
- JWT validation uses a globally replicated JWKS so any region can verify tokens without cross-region calls.
- Routes are configured identically per region (GitOps-managed), but lb:// targets resolve to region-local service instances only — the gateway never proxies across regions on the hot path.
- Health checks are region-scoped: a failed region is drained at the Global Accelerator level, and clients reconverge on the next-nearest region. Time-to-failover is the anycast health-check interval, typically tens of seconds.

---

## Additional Interview Questions

**Q: Should an API gateway rate limiter fail open or fail closed when Redis is down, and why?**

A: For most API gateways, fail open with a loud alert. Rate limiting is a protective measure, not a correctness requirement, so a brief lapse in enforcement is far less harmful than rejecting all traffic and causing a full outage. Failing closed converts a non-critical dependency (the limiter's Redis) into a critical one, meaning a 30-second Redis failover becomes a 30-second total outage. The mitigation for the fail-open window is independent backstops: downstream circuit breakers and connection limits still protect services from overload while the limiter is unavailable. Endpoints with strict abuse or cost constraints (e.g., expensive AI inference) may selectively fail closed.

**Q: Why does filter latency arithmetic understate the real risk in Spring Cloud Gateway?**

A: Spring Cloud Gateway runs on Netty's event loop, which has only about `2 x cores` threads. Summing per-filter CPU time (five filters at 0.1ms = 0.5ms) ignores the real failure mode: a single blocking call inside any filter occupies an event-loop thread for the duration of the I/O, and at 100k req/sec the small pool of event-loop threads saturates almost instantly, stalling all routes. The correct design rule is that every filter must be strictly non-blocking; unavoidable blocking work is offloaded to a bounded elastic scheduler. Latency is gated by event-loop availability, not the arithmetic sum.

**Q: Why does route ordering matter, and how do you prevent ordering bugs?**

A: Spring Cloud Gateway evaluates route predicates in declaration order and uses the first match (first-match-wins). A broad wildcard like `/api/**` declared before a specific `/api/health` will swallow the specific path, so the specific route is never reached. The classic failure is an authenticated wildcard intercepting unauthenticated health checks, returning 401, and causing the load balancer to drain the whole fleet. Always declare routes from most specific to least specific, and add an integration test asserting that critical paths (health, metrics) resolve to the intended route without auth.

**Q: How do you size the upstream connection pool for the gateway?**

A: Use Little's Law: required in-flight connections equal `throughput x downstream latency`. For 100,000 req/sec with 20ms downstream latency, concurrency is `100,000 x 0.020 = 2,000` in-flight requests, so the Reactor Netty connection pool needs at least ~2,000 connections plus headroom (set ~2,500). Reactor Netty defaults to 500 per pool, which would bottleneck this workload. Monitor pool acquisition latency and pending-acquire counts; rising pending acquisitions signal the pool is too small or downstream services are slow.

**Q: How does the architecture change for a global, multi-region deployment?**

A: Front the regional gateway fleets with a global anycast layer such as AWS Global Accelerator that routes each client to the nearest healthy region at the network layer and fails over in tens of seconds. Each region runs an independent gateway with region-local rate-limiter Redis (per-region quotas, no synchronous cross-region calls on the hot path) and resolves lb:// targets only to region-local services. JWT verification uses a globally replicated JWKS so any region validates tokens without cross-region traffic. Route configuration is GitOps-managed and identical per region, and failed regions are drained at the global accelerator so clients reconverge on the next-nearest region.
