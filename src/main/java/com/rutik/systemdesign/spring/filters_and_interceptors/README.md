# Filters and Interceptors in Spring

---

## 1. Concept Overview

Spring web applications process HTTP requests through two distinct interception mechanisms that operate at different layers of the stack:

- **Servlet Filters** (`javax.servlet.Filter` / `jakarta.servlet.Filter`) are part of the servlet container (Tomcat, Jetty, Undertow). They wrap the entire request/response cycle and execute before the DispatcherServlet ever sees the request. Filters can intercept every request regardless of whether Spring handles it.

- **HandlerInterceptors** (`org.springframework.web.servlet.HandlerInterceptor`) are a Spring MVC construct. They run inside the DispatcherServlet, after routing has resolved the target handler. They have fine-grained hooks — `preHandle`, `postHandle`, and `afterCompletion` — and have access to the resolved `HandlerMethod`.

- **OncePerRequestFilter** is a Spring convenience base class for filters that guarantees exactly one execution per logical request, even when the servlet container dispatches the same request multiple times (forwards, includes, async dispatches).

Understanding which layer to use — and the exact order of execution — is critical for building correct authentication, logging, CORS, and auditing pipelines.

---

## 2. Intuition

One-line analogy: Filters are airport security checkpoints that every traveler passes before entering the terminal; interceptors are the gate agents who check your boarding pass right before you board a specific flight.

Mental model: A filter owns the raw `ServletRequest`/`ServletResponse` bytes. An interceptor owns the Spring MVC handler resolution result.

Why it matters: Choosing the wrong layer causes subtle bugs — security checks placed in interceptors can be bypassed by direct servlet mappings; logging placed in filters cannot access Spring MVC metadata like controller name or `@RequestMapping` path variables.

Key insight: Spring Security deliberately uses filters (via `FilterChainProxy`) because it must intercept requests before Spring MVC resolves any handler. If a request is rejected for authentication, there is no handler to resolve.

---

## 3. Core Principles

**Servlet Container vs. Framework Layer**
Filters are managed by the servlet container. The container constructs them at startup, calls `init()`, routes every matching request through `doFilter()`, and calls `destroy()` at shutdown. Spring has no special visibility into filter internals unless the filter is also a Spring bean.

**Chain of Responsibility**
Both filters and interceptors implement the Chain of Responsibility pattern. Each node in the chain must explicitly invoke the next node (`chain.doFilter(...)` for filters; return `true` from `preHandle` for interceptors) or the chain is terminated.

**Orthogonal Concerns**
Filters are orthogonal to Spring MVC routing. An interceptor is always tied to a handler mapping — you register it against specific URL patterns and it only fires when DispatcherServlet successfully resolves a handler.

**Order Matters**
Multiple filters and multiple interceptors each have an explicit order. Lower order numbers run first on the way in, last on the way out (like a stack). Undefined ordering leads to non-deterministic behavior in production.

**Exception Propagation**
A filter wraps the entire `chain.doFilter()` call, so it can catch any exception from any layer below it. `HandlerInterceptor.postHandle()` is NOT called when the handler throws an exception — use `afterCompletion()` for guaranteed cleanup.

---

## 4. Types / Architectures / Strategies

### 4.1 Servlet Filter

```java
public interface Filter {
    void init(FilterConfig filterConfig) throws ServletException;
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException;
    void destroy();
}
```

Registered via:
- `@Component` (Spring Boot auto-registers all `Filter` beans)
- `FilterRegistrationBean<MyFilter>` — explicit registration with URL patterns, order, and init params
- `@WebFilter` + `@ServletComponentScan` — legacy servlet annotation style

### 4.2 HandlerInterceptor

```java
public interface HandlerInterceptor {
    default boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                               Object handler) throws Exception { return true; }

    default void postHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler, ModelAndView modelAndView) throws Exception {}

    default void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                  Object handler, Exception ex) throws Exception {}
}
```

Registered via `WebMvcConfigurer.addInterceptors()`.

### 4.3 OncePerRequestFilter

Base class that uses a request attribute to track whether the filter has already been invoked for this logical request. Subclass and implement `doFilterInternal()`.

```java
public abstract class OncePerRequestFilter extends GenericFilterBean {
    protected abstract void doFilterInternal(HttpServletRequest request,
        HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException;

    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return false; // override to skip certain paths
    }
}
```

### 4.4 ContentCachingRequestWrapper / ContentCachingResponseWrapper

`HttpServletRequest` body is a stream — once read (by `@RequestBody` deserialization) it cannot be read again. These wrappers cache the body bytes, enabling filters and interceptors to read the body without consuming it for downstream processing.

### 4.5 AsyncHandlerInterceptor

Extends `HandlerInterceptor` with `afterConcurrentHandlingStarted()`, which fires when the handler starts an async operation (e.g., returns `DeferredResult` or `CompletableFuture`). The normal `postHandle` and `afterCompletion` are then called on the async thread.

### 4.6 Spring Security FilterChainProxy

`FilterChainProxy` is a single filter registered in the servlet container that internally delegates to a list of `SecurityFilterChain` instances. Each chain matches a request pattern and contains security filters in a fixed order (e.g., `UsernamePasswordAuthenticationFilter`, `BasicAuthenticationFilter`, `ExceptionTranslationFilter`, `FilterSecurityInterceptor`). The fixed internal order is defined by `FilterOrderRegistration` — do not rely on `@Order` for security filters inside the chain.

---

## 5. Architecture Diagrams

### Full Request/Response Flow

```
HTTP Request
     |
     v
+--------------------+
|  Servlet Container |
|  (Tomcat/Jetty)    |
|                    |
|  +-------------+  |
|  |   Filter 1  |  |   <-- @Order(1) or FilterRegistrationBean order 1
|  |  doFilter() |  |
|  +------+------+  |
|         |         |
|  +------v------+  |
|  |   Filter 2  |  |   <-- Spring Security FilterChainProxy is here
|  |  doFilter() |  |
|  +------+------+  |
|         |         |
+---------|----------+
          |
          v
+--------------------+
|  DispatcherServlet |
|                    |
|  Handler Mapping   |
|  (resolves route)  |
|         |          |
|  +------v---------+|
|  | Interceptor 1   ||  preHandle()
|  | Interceptor 2   ||  preHandle()
|  +------+----------+|
|         |           |
|  +------v----------+|
|  |   Handler        ||  (@RestController method)
|  |   (execution)    ||
|  +------+----------+|
|         |           |
|  +------v----------+|
|  | Interceptor 2   ||  postHandle()
|  | Interceptor 1   ||  postHandle()
|  +------+----------+|
|         |           |
|  +------v----------+|
|  | View Resolution  ||
|  +------+----------+|
|         |           |
|  +------v----------+|
|  | Interceptor 2   ||  afterCompletion()
|  | Interceptor 1   ||  afterCompletion()
|  +------------------+|
+--------------------+
          |
          v
  Return through Filter 2, then Filter 1 (reverse order)
          |
          v
     HTTP Response
```

### Exception Handling Flow

```
Handler throws RuntimeException
          |
          v
   postHandle() SKIPPED  <-- critical pitfall
          |
          v
   ExceptionResolver resolves error
          |
          v
   afterCompletion() called with non-null Exception  <-- always runs
          |
          v
   @ExceptionHandler / BasicErrorController
```

### OncePerRequestFilter Dispatch Detection

```
Incoming Dispatch
      |
      v
Check request attribute:
  "com.example.MyFilter.FILTERED"
      |
      +-- attribute present? --> skip, call chain.doFilter() directly
      |
      +-- attribute absent?  --> set attribute, run doFilterInternal()
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Filter Registration Order

```java
// Option A: @Component — order is undefined without @Order
@Component
@Order(1)
public class LoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("Request {} {} completed in {}ms, status={}",
                request.getMethod(), request.getRequestURI(),
                elapsed, response.getStatus());
        }
    }
}

// Option B: FilterRegistrationBean — explicit control
@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<LoggingFilter> loggingFilter() {
        FilterRegistrationBean<LoggingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new LoggingFilter());
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);  // runs early
        reg.setName("loggingFilter");
        return reg;
    }
}
```

### 6.2 HandlerInterceptor Registration

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private LocaleInterceptor localeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**", "/actuator/**")
                .order(1);

        registry.addInterceptor(localeInterceptor)
                .addPathPatterns("/**")
                .order(2);
    }
}
```

### 6.3 HandlerInterceptor with Handler Metadata

```java
@Component
public class AuditInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (handler instanceof HandlerMethod hm) {
            // Access to controller class and method — NOT available in filters
            String controllerName = hm.getBeanType().getSimpleName();
            String methodName = hm.getMethod().getName();
            RequiresAudit audit = hm.getMethodAnnotation(RequiresAudit.class);
            if (audit != null) {
                request.setAttribute("auditStart", System.currentTimeMillis());
                request.setAttribute("auditContext",
                    controllerName + "." + methodName);
            }
        }
        return true;  // returning false halts the chain
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        Long start = (Long) request.getAttribute("auditStart");
        if (start != null) {
            long elapsed = System.currentTimeMillis() - start;
            String ctx = (String) request.getAttribute("auditContext");
            auditService.record(ctx, elapsed, response.getStatus(), ex);
        }
    }
}
```

### 6.4 Reading the Request Body in a Filter

```java
// Approach: wrap the request early in the filter chain
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest =
            new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse =
            new ContentCachingResponseWrapper(response);

        chain.doFilter(wrappedRequest, wrappedResponse);

        // Body is now available AFTER chain completes (it was cached during read)
        byte[] requestBody = wrappedRequest.getContentAsByteArray();
        byte[] responseBody = wrappedResponse.getContentAsByteArray();
        // log, audit, etc.

        // CRITICAL: copy response body back or the client gets an empty response
        wrappedResponse.copyBodyToResponse();
    }
}
```

### 6.5 Concrete Numbers

- Tomcat default thread pool: 200 max threads (`server.tomcat.threads.max=200`)
- Tomcat default acceptCount: 100 (queue before rejecting)
- Each filter in the chain adds one method call overhead — negligible, typically under 1 microsecond per filter for simple pass-through
- `ContentCachingRequestWrapper` allocates a `ByteArrayOutputStream` that grows 32 bytes at a time initially — for a 10 KB JSON body, expect ~40 KB of heap allocated (original stream + cached copy + possible deserialization)
- Spring Security `FilterChainProxy` internally maintains a list of ~15 filters in a standard Spring Boot Security setup

---

## 7. Real-World Examples

### 7.1 CORS Filter (Filter Layer — Correct Approach)

CORS preflight requests (`OPTIONS`) must be handled before Spring MVC routing. A CORS `HandlerInterceptor` would fail for `OPTIONS` requests that are rejected by DispatcherServlet before reaching any interceptor.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin",
            getAllowedOrigin(request));
        response.setHeader("Access-Control-Allow-Methods",
            "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers",
            "Authorization, Content-Type, X-Request-Id");
        response.setHeader("Access-Control-Max-Age", "3600");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;  // do NOT call chain.doFilter — preflight is done
        }
        chain.doFilter(request, response);
    }
}
```

### 7.2 JWT Authentication Filter (Filter Layer)

Authentication belongs in filters because it must run before any handler mapping, including Spring Security's authorization checks.

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService tokenService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        try {
            String username = tokenService.extractUsername(token);
            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (tokenService.isValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource()
                        .buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (JwtException e) {
            // Invalid token — continue without setting auth context
            // Spring Security will reject the request via authorization rules
        }
        chain.doFilter(request, response);
    }
}
```

### 7.3 Performance Metrics Interceptor

```java
@Component
public class MetricsInterceptor implements HandlerInterceptor {

    private final MeterRegistry meterRegistry;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute("timerSample",
            Timer.start(meterRegistry));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        Timer.Sample sample = (Timer.Sample) request.getAttribute("timerSample");
        if (sample != null && handler instanceof HandlerMethod hm) {
            sample.stop(Timer.builder("http.server.requests")
                .tag("method", request.getMethod())
                .tag("controller", hm.getBeanType().getSimpleName())
                .tag("status", String.valueOf(response.getStatus()))
                .register(meterRegistry));
        }
    }
}
```

---

## 8. Tradeoffs

| Dimension | Filter | HandlerInterceptor |
|-----------|--------|--------------------|
| Execution layer | Servlet container (before DispatcherServlet) | Spring MVC (inside DispatcherServlet) |
| Access to resolved handler | No | Yes (`HandlerMethod`, annotations) |
| Access to `ModelAndView` | No | Yes (in `postHandle`) |
| Works for non-Spring requests | Yes (static resources, actuator, error paths) | Only for DispatcherServlet-mapped requests |
| Can stop the chain | Yes (don't call `chain.doFilter`) | Yes (return `false` from `preHandle`) |
| Exception handling | Wraps entire chain — catches all exceptions | `postHandle` skipped on exception; `afterCompletion` always runs |
| Spring bean access | Yes (if registered as bean or via `DelegatingFilterProxy`) | Yes (full Spring context) |
| Request body access | Needs `ContentCachingRequestWrapper` | Same constraint — body already read by this point |
| Ordering mechanism | `@Order`, `FilterRegistrationBean.setOrder()` | `InterceptorRegistration.order()` |
| Use for Spring Security | Mandatory | Cannot replace security filters |
| Complexity | Lower — single `doFilter` method | Higher — three lifecycle hooks |

---

## 9. When to Use / When NOT to Use

### Use a Filter when:
- Implementing cross-cutting concerns that must apply before DispatcherServlet: authentication, CORS, request ID injection, rate limiting
- The concern must apply to all requests including static files, actuator endpoints, and error pages
- Implementing Spring Security customizations (always filter-based)
- You need to wrap/replace the request or response object (e.g., `ContentCachingRequestWrapper`)
- Logging raw HTTP details: method, URI, headers, status code, response time

### Use a HandlerInterceptor when:
- You need access to the resolved `HandlerMethod` or its annotations (e.g., `@RequiresPermission`)
- Implementing locale/theme resolution
- Injecting model attributes before every view render
- Tracking handler-specific metrics tied to controller name or operation name
- Implementing pre/post logic that should only apply to Spring MVC endpoints, not static resources

### Do NOT use a HandlerInterceptor when:
- Implementing authentication or authorization — Spring Security operates in filters; mixing layers creates security gaps
- You need to handle `OPTIONS` preflight requests — these may not reach an interceptor
- The concern must apply outside of Spring MVC (error controller, actuator endpoints with separate servlet mappings)
- You need to modify the raw byte stream of the request or response

### Do NOT use a Filter when:
- You need access to Spring MVC metadata (`HandlerMethod`, path variables post-routing)
- You need `ModelAndView` modifications for Thymeleaf/JSP rendering

---

## 10. Common Pitfalls

### Pitfall 1: postHandle not called when handler throws an exception

**Broken:**
```java
@Component
public class ResourceCleanupInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        DatabaseContext.setTenantId(request.getHeader("X-Tenant-Id"));
        return true;
    }

    // BUG: if handler throws an exception, postHandle is NEVER called
    // DatabaseContext.clear() is never invoked, causing tenant leak
    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler, ModelAndView mv) {
        DatabaseContext.clear();  // ThreadLocal cleanup
    }
}
```

**Fixed:**
```java
@Component
public class ResourceCleanupInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        DatabaseContext.setTenantId(request.getHeader("X-Tenant-Id"));
        return true;
    }

    // afterCompletion is ALWAYS called, even when handler throws
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        DatabaseContext.clear();  // always cleaned up
    }
}
```

**Production war story:** A financial services team stored a tenant identifier in a `ThreadLocal` in `preHandle` and cleared it in `postHandle`. A downstream service occasionally threw a `ServiceUnavailableException`. On those requests, the thread was returned to the Tomcat pool with a stale `ThreadLocal` value. The next request on that thread processed with the previous tenant's database connection, causing data from one tenant to appear in another tenant's response. The bug was silent in testing (exceptions were rare) and took three weeks to reproduce in production via log correlation.

---

### Pitfall 2: Consuming the request body in a filter before @RequestBody

**Broken:**
```java
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // BUG: reads the InputStream — it's now exhausted
        String body = new String(request.getInputStream().readAllBytes());
        log.info("Request body: {}", body);
        chain.doFilter(request, response);
        // @RequestBody deserialization now fails with HttpMessageNotReadableException
        // "Required request body is missing"
    }
}
```

**Fixed:**
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // must be FIRST to wrap request before any other filter reads it
public class BodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest =
            new ContentCachingRequestWrapper(request);
        chain.doFilter(wrappedRequest, response);
        // Body is available AFTER chain completes because ContentCachingRequestWrapper
        // caches bytes as they are read by downstream (including Jackson/HttpMessageConverter)
        byte[] body = wrappedRequest.getContentAsByteArray();
        if (body.length > 0) {
            log.info("Request body: {}", new String(body, wrappedRequest.getCharacterEncoding()));
        }
    }
}
```

---

### Pitfall 3: Forgetting to copy the response body back when using ContentCachingResponseWrapper

**Broken:**
```java
chain.doFilter(request, wrappedResponse);
byte[] responseBody = wrappedResponse.getContentAsByteArray();
log.info("Response: {}", new String(responseBody));
// BUG: response body is in the wrapper's buffer but never sent to client
// Client receives an empty 200 response
```

**Fixed:**
```java
chain.doFilter(request, wrappedResponse);
byte[] responseBody = wrappedResponse.getContentAsByteArray();
log.info("Response: {}", new String(responseBody));
wrappedResponse.copyBodyToResponse();  // flushes the buffer to the actual response
```

---

### Pitfall 4: Registering a Filter as a @Component without controlling URL patterns

**Broken:**
```java
@Component  // Spring Boot auto-registers this for ALL requests, including /actuator, /error
public class ExpensiveAuditFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        // runs for every single request including health checks from load balancer
        // 10 req/sec health checks * 5ms audit overhead = 50ms/sec CPU wasted
    }
}
```

**Fixed:**
```java
// Do NOT annotate with @Component — use FilterRegistrationBean instead
public class ExpensiveAuditFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) { ... }
}

@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<ExpensiveAuditFilter> auditFilter() {
        FilterRegistrationBean<ExpensiveAuditFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ExpensiveAuditFilter());
        reg.addUrlPatterns("/api/v1/*", "/api/v2/*");  // explicit patterns
        reg.setOrder(10);
        return reg;
    }
}
```

---

### Pitfall 5: Using an Interceptor for security when a public endpoint bypasses DispatcherServlet

**Broken:**
```java
// Team adds BasicAuthInterceptor, excludes /public/**
// A new /health endpoint is added under /actuator with a separate servlet mapping
// /actuator/health bypasses DispatcherServlet entirely — interceptor never fires
// The endpoint is exposed with no authentication despite the team thinking it's secured
```

**Fixed:** Always use Spring Security filters for authentication and authorization. Interceptors do not provide a complete security boundary.

---

## 11. Technologies & Tools

| Technology | Role |
|------------|------|
| `javax.servlet.Filter` (Jakarta EE 8 and below) | Servlet filter API |
| `jakarta.servlet.Filter` (Jakarta EE 9+, Spring Boot 3.x) | Servlet filter API (namespace change) |
| `org.springframework.web.filter.OncePerRequestFilter` | Base class guaranteeing single filter execution |
| `org.springframework.web.filter.CommonsRequestLoggingFilter` | Built-in Spring filter for request logging; configurable |
| `org.springframework.web.filter.ShallowEtagHeaderFilter` | Adds ETag support with response caching |
| `org.springframework.web.filter.CorsFilter` | Spring's built-in CORS filter |
| `org.springframework.web.servlet.HandlerInterceptor` | Spring MVC interceptor interface |
| `org.springframework.web.servlet.AsyncHandlerInterceptor` | Interceptor with async support |
| `org.springframework.web.util.ContentCachingRequestWrapper` | Caches request body for re-reads |
| `org.springframework.web.util.ContentCachingResponseWrapper` | Caches response body for inspection |
| `org.springframework.security.web.FilterChainProxy` | Spring Security's composite filter |
| `org.springframework.web.filter.DelegatingFilterProxy` | Bridge between servlet container and Spring bean filters |
| `io.micrometer.core.instrument.Timer` | Metrics instrumentation for interceptors |

---

## 12. Interview Questions with Answers

**Q: What is the difference between a Servlet Filter and a Spring HandlerInterceptor?**
A Filter operates at the servlet container level, before DispatcherServlet processes the request, and can intercept any HTTP request regardless of whether Spring handles it. A HandlerInterceptor operates inside DispatcherServlet, after Spring has resolved the target handler, giving access to `HandlerMethod` and its annotations. Filters use the `doFilter(request, response, chain)` method; interceptors use three hooks: `preHandle`, `postHandle`, and `afterCompletion`. Use filters for authentication and CORS; use interceptors when you need controller-level metadata.

**Q: Why does Spring Security use filters instead of interceptors?**
Spring Security must intercept requests before any handler resolution happens — including before any Spring MVC route is matched. If an unauthenticated request reaches DispatcherServlet, Spring MVC might expose information through error handling before the interceptor even fires. Additionally, filters apply uniformly to all requests (static files, actuator, error pages), while interceptors only apply to DispatcherServlet-mapped requests. `FilterChainProxy` is a single filter that manages a complete security filter chain independently of Spring MVC.

**Q: When is postHandle NOT called?**
`postHandle` is not called when the handler method throws an exception. Spring MVC skips `postHandle` and jumps directly to `afterCompletion`. This means any cleanup or resource release that relies on `postHandle` will be silently skipped on error paths. Always use `afterCompletion` for cleanup code, checking the `Exception ex` parameter for null to detect whether the request succeeded.

**Q: What is OncePerRequestFilter and why is it needed?**
`OncePerRequestFilter` guarantees that a filter's logic executes exactly once per logical HTTP request, even if the servlet container dispatches the request multiple times (e.g., via `RequestDispatcher.forward()` or `RequestDispatcher.include()`, or async dispatch). It achieves this by setting a boolean attribute on the request and checking it at the start of `doFilter`. Without this guard, a filter that performs authentication or request ID injection could execute two or three times on a single logical request.

**Q: How do you control the order of multiple filters in a Spring Boot application?**
Three mechanisms exist: (1) Implement `Ordered` or annotate with `@Order(n)` on a filter bean — lower numbers execute first; (2) Use `FilterRegistrationBean.setOrder(n)` for explicit registration without the `@Component` annotation — this also allows URL pattern restrictions; (3) For Spring Security filters, use `HttpSecurity.addFilterBefore/After/At()` to position filters relative to existing security filter positions. Avoid mixing `@Order` and `FilterRegistrationBean` for the same filter — the `FilterRegistrationBean` order takes precedence.

**Q: How do you read the request body in a filter without breaking @RequestBody deserialization?**
Wrap the request in a `ContentCachingRequestWrapper` and pass the wrapper to `chain.doFilter()`. The wrapper intercepts reads by downstream components (including Jackson's `HttpMessageConverter`) and caches the bytes. After `chain.doFilter()` returns, call `wrapper.getContentAsByteArray()` to read the cached body. The key insight is that the body is available for inspection only after the chain completes, not before, because the wrapper caches bytes as they are read — not upfront. If you need the body before the chain executes, you must read the original stream yourself and create a new `HttpServletRequestWrapper` that replays those bytes.

**Q: What happens if you call chain.doFilter() with a ContentCachingResponseWrapper but forget to call copyBodyToResponse()?**
The client receives an empty response body. `ContentCachingResponseWrapper` intercepts writes to the response output stream and stores them in an internal buffer instead of writing them to the actual response. `copyBodyToResponse()` flushes that buffer to the real response. This is a silent bug — the status code is correct, Content-Type is set, but the body is empty. Load balancer health checks will often still pass (HTTP 200 with no body), masking the bug in monitoring.

**Q: How does DelegatingFilterProxy work?**
`DelegatingFilterProxy` is a `javax.servlet.Filter` registered with the servlet container that looks up a Spring bean by name from the `ApplicationContext` and delegates all `doFilter()` calls to it. This bridges the lifecycle gap: the servlet container initializes filters at startup before the Spring context is ready, but `DelegatingFilterProxy` defers the actual bean lookup to the first request. Spring Security's `FilterChainProxy` is registered through `DelegatingFilterProxy` under the bean name `"springSecurityFilterChain"`.

**Q: Can an interceptor's preHandle method access path variables?**
Not directly from the `HttpServletRequest`. Path variables are extracted by Spring MVC after routing and stored in a request attribute: `request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)`. In `preHandle`, this attribute is already populated because handler mapping has completed before the interceptor fires. Cast the result to `Map<String, String>` to access individual path variable values.

**Q: How do you exclude specific URL patterns from a HandlerInterceptor?**
Use `InterceptorRegistration.excludePathPatterns()` in `WebMvcConfigurer.addInterceptors()`. The exclusion patterns use Ant-style path matching: `"/api/public/**"` excludes all sub-paths, `"/api/v1/health"` excludes a specific path. Exclusion is evaluated before `addPathPatterns`, so excluded paths are always skipped regardless of include patterns. For complex conditional logic, implement the check inside `preHandle` itself using `shouldNotFilter` semantics.

**Q: What is the difference between preHandle returning false vs. throwing an exception?**
Returning `false` from `preHandle` halts the handler chain — DispatcherServlet stops processing and returns the response as-is (typically empty 200 if you haven't written anything, or whatever you wrote in `preHandle`). You are responsible for writing a meaningful error response before returning `false`. Throwing an exception triggers Spring MVC's exception handling pipeline (`@ExceptionHandler`, `@ControllerAdvice`) and results in a structured error response. For API error responses with proper JSON bodies and status codes, throw an appropriate exception rather than returning `false`.

**What is `OncePerRequestFilter` and why is it preferred over implementing `Filter` directly?**
`OncePerRequestFilter` (Spring's abstract class) guarantees the filter executes exactly once per request, even when the request is forwarded internally via `RequestDispatcher.forward()`. A plain `javax.servlet.Filter` is called on every dispatch — including `FORWARD` and `INCLUDE` dispatches within the same request, which means it runs multiple times for a request involving `RequestDispatcher`. `OncePerRequestFilter` achieves once-per-request by marking the request with a request attribute on first execution and skipping if the attribute is already set. Override `shouldNotFilter(HttpServletRequest)` to exclude specific requests (e.g., static resources). Almost all Spring Security filters extend `OncePerRequestFilter` for this reason.

**How do you measure and record per-request latency in a `Filter` without blocking the response body write?**
Timing in a filter wraps `chain.doFilter(request, response)` with `System.nanoTime()` measurements. However, for streaming responses, the response body write happens after `doFilter` returns — the `afterCompletion` timing in an interceptor or a `ContentCachingResponseWrapper` is needed for body size. For simple request latency (time to send the last byte of headers + body): use a `ContentCachingResponseWrapper` in the filter to buffer the response, record `nanoTime()` before and after `doFilter`, then write the cached response. For real production latency instrumentation, use Micrometer's `WebMvcMetricsFilter` (auto-configured by Spring Boot Actuator) which uses `HttpServletResponse.setStatus()` callback via `HandlerInterceptor.afterCompletion` to accurately attribute time.

**What happens to a Spring Security filter that is ordered too early in the security filter chain?**
Spring Security's `SecurityFilterChain` is itself a `Filter` registered in the Servlet filter chain at `SecurityProperties.DEFAULT_FILTER_ORDER` (-100 by default). Within the security chain, filters have a fixed order defined by `FilterOrderRegistration`. If you insert a custom filter too early (e.g., `addFilterBefore(myFilter, UsernamePasswordAuthenticationFilter.class)`) when your filter depends on the `SecurityContext` being populated, the context will not be set yet — `SecurityContextHolder.getContext().getAuthentication()` returns null. The correct pattern: know the security filter order, place authentication-dependent filters after `SecurityContextHolderFilter` (which restores the context from the session). Use `HttpSecurity.addFilterAfter` / `addFilterBefore` with precise reference filters.

**How does `AsyncContext` and async servlet processing affect `HandlerInterceptor.afterCompletion`?**
When a controller starts async processing (`DeferredResult`, `Callable`, `@Async` with `SseEmitter`), the servlet container thread that handled the request is released before the response is committed. Spring MVC's `HandlerInterceptor.afterCompletion` is called when the response is finally committed — but on a *different* thread than `preHandle` and `postHandle`. Implication: any thread-local state set in `preHandle` (e.g., in `MDC`) is not automatically available in `afterCompletion` unless you use `CallableProcessingInterceptor` or `DeferredResultProcessingInterceptor`, which provide explicit callbacks for the async processing lifecycle. For accurate async request timing, instrument `DeferredResult.setResult()` or the `Callable` body directly rather than relying on `afterCompletion` timing.

---

## 13. Best Practices

**Use OncePerRequestFilter as the base class for all custom filters.** It eliminates double-execution bugs from forwards/includes and provides a clean `doFilterInternal` method with `HttpServletRequest`/`HttpServletResponse` (already cast from `ServletRequest`/`ServletResponse`).

**Register filters with FilterRegistrationBean instead of @Component when you need URL pattern restrictions.** Attaching `@Component` auto-registers the filter for all `/*`, including actuator health checks and static assets. `FilterRegistrationBean` lets you scope to `/api/*` precisely.

**Put all cleanup logic in afterCompletion, not postHandle.** `afterCompletion` is the finally block of the interceptor lifecycle — it runs even when the handler throws. Pass-through the `Exception ex` parameter to distinguish success from error paths.

**Keep filters lean.** Tomcat's default 200 threads can each be held inside your filter. A filter that does synchronous I/O (database call, HTTP call to another service) reduces throughput proportionally. If you must do I/O in a filter, consider offloading to a queue or use non-blocking I/O.

**Always call copyBodyToResponse() when using ContentCachingResponseWrapper.** Make this the last operation in the `finally` block to ensure it runs even if logging throws.

**Use request attributes to pass state from preHandle to afterCompletion.** This is thread-safe because each request has its own `HttpServletRequest` object. Avoid `ThreadLocal` variables in interceptors unless you are certain to clean them up in `afterCompletion`.

**Do not use HandlerInterceptor for authentication.** Authentication belongs in Spring Security filters. If you authenticate in an interceptor, requests that trigger Spring Security's authorization checks before reaching your interceptor will bypass your logic.

**Test filter ordering explicitly.** Write an integration test (`@SpringBootTest` + `TestRestTemplate`) that verifies the execution order by injecting an ordered list of executed filter names into a request attribute.

**Set filter names explicitly with FilterRegistrationBean.setName().** Auto-detected filter names default to the bean name, which can collide when multiple `FilterRegistrationBean` beans exist or when the filter class name changes.

---

## 14. Case Study

### Building a Distributed Request Tracing System

**Context:** A microservices platform with 12 services handles 50,000 requests/second. The operations team cannot correlate logs across services because each service generates its own request IDs. The goal is to inject a trace ID on ingress, propagate it through all downstream HTTP calls, include it in all log statements, and return it in the response header — without modifying any business logic code.

**Architecture:**

```
External Client
     |
     | POST /api/orders  (no X-Trace-Id header)
     v
+----------------------------------+
|  TraceIdFilter (Order: -100)     |
|  - Extract X-Trace-Id if present |
|  - Generate UUID if absent       |
|  - Store in MDC: "traceId"       |
|  - Set request attribute         |
+----------------------------------+
     |
     v
+----------------------------------+
|  Spring Security FilterChain     |
|  (Order: -100 Spring Security    |
|   auto-order, after tracing)     |
+----------------------------------+
     |
     v
+----------------------------------+
|  DispatcherServlet               |
+----------------------------------+
     |
     v
+----------------------------------+
|  AuditInterceptor.preHandle()    |
|  - read traceId from request     |
|  - open audit record             |
+----------------------------------+
     |
     v
+----------------------------------+
|  OrderController.createOrder()   |
|  - calls InventoryService via    |
|    WebClient (injects X-Trace-Id)|
|  - calls PaymentService via      |
|    WebClient (injects X-Trace-Id)|
+----------------------------------+
     |
     v
+----------------------------------+
|  AuditInterceptor.afterCompletion|
|  - close audit record            |
|  - record outcome + duration     |
+----------------------------------+
     |
     v
+----------------------------------+
|  TraceIdFilter (response path)   |
|  - set X-Trace-Id response header|
|  - clear MDC                     |
+----------------------------------+
     |
     v
  HTTP Response with X-Trace-Id: <uuid>
```

**Implementation:**

```java
@Component
@Order(-100)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, traceId);
        request.setAttribute(MDC_KEY, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            response.setHeader(TRACE_HEADER, traceId);
            MDC.remove(MDC_KEY);  // CRITICAL: prevents MDC leak into next request on same thread
        }
    }
}
```

```java
// WebClient configuration to propagate trace ID automatically
@Bean
public WebClient webClient() {
    return WebClient.builder()
        .filter((request, next) -> {
            String traceId = MDC.get("traceId");
            ClientRequest traced = ClientRequest.from(request)
                .header("X-Trace-Id", traceId != null ? traceId : "unknown")
                .build();
            return next.exchange(traced);
        })
        .build();
}
```

**Logback configuration** to include traceId in every log line automatically:
```xml
<pattern>%d{ISO8601} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
```

**Outcome:** After deployment, the operations team can take any `X-Trace-Id` from a client error report, search all 12 service logs simultaneously, and reconstruct the complete call graph for that request. Mean time to diagnose production incidents dropped from 45 minutes to under 3 minutes. The implementation required zero changes to any controller or service class.

**Key lessons:**
- The filter must be ordered before Spring Security (`Order: -100`) so that MDC is populated before Spring Security logs authentication events for the same request
- MDC cleanup in `finally` is mandatory — Tomcat thread pools reuse threads; without cleanup, thread 47 processing request 1001 will log with the trace ID from request 1000
- The `afterCompletion` approach in the interceptor works for audit records; the filter's `finally` block works for cross-cutting concerns tied to the raw response

---

**Additional war stories and interview Q&As:**

**Pitfall: Filter executing twice for error dispatch.** A logging filter records every request — but for an error, Spring dispatches the request twice (first to the original URL, then to `/error`). The filter fires both times, producing duplicate log entries.

```java
// BROKEN: filter fires for both REQUEST and ERROR dispatches
@Component
public class RequestLogFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        log.info("Request: {}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }
    // Logs "GET /orders" then "GET /error" for every exception response
}

// FIX: override shouldNotFilterErrorDispatch to skip ERROR dispatch
@Override
protected boolean shouldNotFilterErrorDispatch() {
    return true;  // only logs the original REQUEST dispatch
}
```

**Pitfall: HandlerInterceptor's preHandle returning false without a response body.** Returning `false` from `preHandle` halts the handler chain, but if the interceptor wrote nothing to the response, the client receives an empty 200 response — confusing and hard to debug.

```java
// BROKEN: returns false, client gets empty 200
@Override
public boolean preHandle(HttpServletRequest req, HttpServletResponse res,
                         Object handler) {
    if (!isAuthenticated(req)) return false;  // empty response!
    return true;
}

// FIX: write explicit error status before returning false
@Override
public boolean preHandle(HttpServletRequest req, HttpServletResponse res,
                         Object handler) throws IOException {
    if (!isAuthenticated(req)) {
        res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        return false;
    }
    return true;
}
```

**Pitfall: Ordering filters via @Order vs Spring Security's filter chain.** Filters registered with `@Component` + `@Order` are added to the embedded servlet container's filter chain. Spring Security's `SecurityFilterChain` is a separate chain managed by `DelegatingFilterProxy`. A logging filter at `@Order(1)` runs before Spring Security — meaning it logs requests that Security will eventually reject. Usually correct for logging, wrong for auth-gating.

**Additional interview Q&As:**

**What is the difference between OncePerRequestFilter and GenericFilterBean?** `OncePerRequestFilter` guarantees single execution per request, even during forwards, includes, or error dispatches (with `shouldNotFilterErrorDispatch`). `GenericFilterBean` fires on every dispatch. Use `OncePerRequestFilter` for any filter that must not double-execute (logging, authentication, rate limiting). Use `GenericFilterBean` only when you explicitly need to intercept all dispatch types.

**How do you pass data from a Filter to a Controller?** Set a request attribute: `request.setAttribute("key", value)`. In the controller, inject `HttpServletRequest` and call `request.getAttribute("key")`. Alternatively, put the data in the `SecurityContext` (for auth principal) or in an MDC thread-local (for trace IDs). Never use static fields or application-scope singletons for request-scoped data.

**When would you choose HandlerInterceptor over a Filter?** Use `HandlerInterceptor` when you need access to Spring MVC concepts: the matched `HandlerMethod`, model attributes, or `ModelAndView`. Use a Filter when you need to intercept at the servlet level before Spring MVC: for raw request/response manipulation (GZIP, logging raw bytes), for requests that may not reach a Spring handler (static resources, error pages), or for security filters that must run before DispatcherServlet.

---

## Related / See Also

- [Request Handling](../request_handling/README.md) — @ControllerAdvice
- [Spring Security Architecture](../spring_security_architecture/README.md) — security filter chain
- [Case Study: Rate Limiter](../case_studies/design_distributed_rate_limiter_spring.md) — OncePerRequestFilter
- [LLD: Chain of Responsibility](../../lld/behavioral/chain_of_responsibility/README.md) — the GoF pattern the Spring filter chain implements
