# Spring AOP

## 1. Concept Overview

Aspect-Oriented Programming (AOP) is a programming paradigm that separates cross-cutting concerns — logging, security, transactions, metrics, retries — from business logic. Spring AOP implements this by wrapping beans in proxies that intercept method calls and execute advice code before, after, or around the target method.

Spring AOP is proxy-based (runtime weaving), which means it only works on Spring-managed beans and only intercepts method executions. It is not full AspectJ, which supports field access, constructor execution, and compile-time/load-time weaving.

---

## 2. Intuition

Think of AOP like airport security checks. Every passenger (method call) entering the terminal (bean) must pass through security (advice). The security process is written once and applied to all passengers without modifying the passenger's itinerary (business logic). When security rules change, only the checkpoint changes — no itinerary is modified.

**One-line analogy:** AOP lets you bolt behavior onto any method call without touching the method's source code.

**Why it matters:** `@Transactional`, `@Cacheable`, `@Async`, `@Retryable`, Spring Security's `@PreAuthorize`, and distributed tracing (Micrometer) are all implemented as AOP advice. Understanding AOP explains all their behaviors and limitations.

**Key insight:** Spring AOP joins points are limited to method executions on Spring beans. Any limitation (self-invocation, private methods, final methods) comes directly from the proxy-based implementation.

---

## 3. Core Principles

1. **Aspect:** A module encapsulating a cross-cutting concern (e.g., logging, timing).
2. **Join Point:** A point in program execution — in Spring AOP, always a method execution.
3. **Pointcut:** An expression selecting which join points an advice applies to.
4. **Advice:** The action taken at a join point (before, after, around).
5. **Weaving:** Applying aspects to create an advised object. Spring AOP uses runtime proxy weaving.
6. **Target Object:** The original bean being proxied.
7. **Proxy:** The object created by Spring that wraps the target and applies advice.

---

## 4. Types / Architectures / Strategies

### Advice Types

| Advice | Annotation | When It Runs |
|--------|------------|--------------|
| Before | `@Before` | Before the method executes; cannot stop execution (throw exception to abort) |
| After Returning | `@AfterReturning` | After method returns normally; has access to return value |
| After Throwing | `@AfterThrowing` | After method throws exception; has access to exception |
| After (finally) | `@After` | After method, regardless of outcome (like finally block) |
| Around | `@Around` | Wraps method; has full control over execution, return value, and exceptions |

### Pointcut Expression Types

| Designator | Matches |
|------------|---------|
| `execution(...)` | Method execution (most common) |
| `within(...)` | All methods within a type or package |
| `@annotation(...)` | Methods annotated with a specific annotation |
| `args(...)` | Methods with specific argument types |
| `@args(...)` | Methods where arguments are annotated |
| `bean(...)` | Bean with a specific name |
| `target(...)` | Target object is an instance of a type |
| `@target(...)` | Target class is annotated |
| `@within(...)` | Methods in a class annotated with a specific annotation |

### Spring AOP vs AspectJ

| Aspect | Spring AOP | AspectJ |
|--------|------------|---------|
| Join points | Method execution only | Method, field, constructor, static initializer |
| Weaving | Runtime (proxy) | Compile-time (ajc), post-compile, load-time |
| Spring bean requirement | Yes (only Spring beans) | No (any Java object) |
| Performance | Proxy overhead per call | Near-zero after weaving |
| Private method interception | No | Yes (compile/load-time) |
| Field access interception | No | Yes |
| Setup complexity | Zero (built into Spring) | Requires AspectJ compiler or LTW agent |

---

## 5. Architecture Diagrams

```
AOP Proxy Advice Chain
=======================

  Caller
    |
    v
  +-------------------------------------------------+
  |  AOP Proxy (CGLIB or JDK)                      |
  |                                                  |
  |  @Around advice start                            |
  |    |                                             |
  |    v                                             |
  |  @Before advice                                  |
  |    |                                             |
  |    v                                             |
  |  target.method()  (real bean method)             |
  |    |                                             |
  |    v                                             |
  |  @AfterReturning / @AfterThrowing               |
  |    |                                             |
  |    v                                             |
  |  @After (finally)                                |
  |    |                                             |
  |    v                                             |
  |  @Around advice end (return / rethrow)           |
  +-------------------------------------------------+
    |
    v
  Caller receives result
```

```
Pointcut Expression: execution() Breakdown
==========================================

  execution(modifiers? return-type declaring-type? method-name(params) throws?)

  execution(* com.example.service.*.*(..))
             |  |                    | |
             |  package              | any params
             |                       |
             any return type        any method name
             (including void)

  execution(public * com.example..UserService+.find*(Long, ..))
                     |                          |      |
                     any return type          find*   first param Long, rest any
                     .. = any subpackage      includes subclasses (+)

  @annotation(org.springframework.transaction.annotation.Transactional)
  -> matches any method annotated with @Transactional
```

---

## 6. How It Works — Detailed Mechanics

### Basic Aspect Definition

```java
@Aspect
@Component  // Must be a Spring-managed bean
public class ExecutionTimeAspect {

    // Pointcut declaration — reusable, named
    @Pointcut("execution(* com.example.service..*(..))")
    public void serviceLayer() {}  // empty method body, name used in advice

    @Pointcut("@annotation(com.example.annotation.Timed)")
    public void timedAnnotation() {}

    // Combine pointcuts
    @Pointcut("serviceLayer() || timedAnnotation()")
    public void monitoredMethods() {}

    // @Before advice
    @Before("serviceLayer()")
    public void logBefore(JoinPoint jp) {
        System.out.println("Calling: " + jp.getSignature().getName());
        System.out.println("Args: " + Arrays.toString(jp.getArgs()));
        System.out.println("Target: " + jp.getTarget().getClass().getSimpleName());
    }

    // @AfterReturning — access return value
    @AfterReturning(pointcut = "serviceLayer()", returning = "result")
    public void logAfterReturning(JoinPoint jp, Object result) {
        System.out.println("Returned: " + result);
    }

    // @AfterThrowing — access thrown exception
    @AfterThrowing(pointcut = "serviceLayer()", throwing = "ex")
    public void logException(JoinPoint jp, Exception ex) {
        System.out.println("Exception in " + jp.getSignature() + ": " + ex.getMessage());
    }

    // @Around — full control
    @Around("monitoredMethods()")
    public Object measureExecutionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();  // call the real method
            long elapsed = System.currentTimeMillis() - start;
            System.out.println(pjp.getSignature() + " took " + elapsed + "ms");
            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            System.out.println(pjp.getSignature() + " failed after " + elapsed + "ms");
            throw ex;  // re-throw — don't swallow exceptions in @Around
        }
    }
}
```

### @Around with Modified Return Value

```java
@Aspect
@Component
public class RetryAspect {

    @Around("@annotation(retryable)")
    public Object retry(ProceedingJoinPoint pjp, Retryable retryable) throws Throwable {
        int maxAttempts = retryable.maxAttempts();  // annotation attribute
        long delay = retryable.delayMs();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return pjp.proceed();  // success: return result immediately
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    System.out.println("Attempt " + attempt + " failed, retrying...");
                    Thread.sleep(delay * attempt);  // exponential backoff
                }
            }
        }
        throw lastException;  // all attempts failed
    }
}

// Custom annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {
    int maxAttempts() default 3;
    long delayMs() default 100;
}

// Usage
@Service
public class PaymentService {
    @Retryable(maxAttempts = 3, delayMs = 200)
    public PaymentResult charge(Payment payment) {
        return stripeClient.charge(payment);  // retried up to 3 times on failure
    }
}
```

### Advice Ordering with @Order

```java
// Multiple aspects applied to the same method — order matters
@Aspect
@Component
@Order(1)  // Runs first (outermost proxy layer)
public class SecurityAspect {
    @Before("serviceLayer()")
    public void checkPermissions(JoinPoint jp) {
        // runs first before method
    }
}

@Aspect
@Component
@Order(2)  // Runs second
public class TransactionAspect {
    @Around("serviceLayer()")
    public Object withTransaction(ProceedingJoinPoint pjp) throws Throwable {
        // begin transaction
        Object result = pjp.proceed();
        // commit transaction
        return result;
    }
}

@Aspect
@Component
@Order(3)  // Runs last (innermost before actual method call)
public class LoggingAspect {
    @Around("serviceLayer()")
    public Object log(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("Calling " + pjp.getSignature());
        return pjp.proceed();
    }
}

// Execution order for a method call:
// SecurityAspect.before -> TransactionAspect.begin -> LoggingAspect.log ->
//   target.method() ->
// LoggingAspect.return -> TransactionAspect.commit -> SecurityAspect.after
```

### Accessing Annotation Attributes in Advice

```java
@Aspect
@Component
public class AuditAspect {
    // Bind the annotation instance to a parameter
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        String action = auditable.action();  // access annotation attribute directly
        String resource = auditable.resource();

        auditLog.record(action, resource, SecurityContextHolder.getContext());
        Object result = pjp.proceed();
        auditLog.recordSuccess(action);
        return result;
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
    String resource() default "unknown";
}
```

### @EnableAspectJAutoProxy

```java
// Required to enable Spring AOP processing
@Configuration
@EnableAspectJAutoProxy  // registers AnnotationAwareAspectJAutoProxyCreator
public class AopConfig { }

// With proxyTargetClass (CGLIB for all)
@EnableAspectJAutoProxy(proxyTargetClass = true)

// Spring Boot auto-configures this via AopAutoConfiguration when:
// - spring-aop is on classpath
// - spring.aop.auto=true (default)
// spring.aop.proxy-target-class=true (default in Boot)
```

---

## 7. Real-World Examples

**Distributed tracing:** A single `@Around` aspect injects trace IDs into MDC and propagates them to HTTP headers, applied to all service and controller methods via `execution(* com.example..*(..))`. All log statements automatically include the trace ID without any code change in business logic.

**@Transactional is AOP:** Spring's `@Transactional` is implemented as an `@Around` advice by `TransactionInterceptor`. Understanding this explains why it doesn't work on private methods, self-invocations, or non-Spring-managed objects.

**Metrics collection:** A `@Around("serviceLayer()")` advice wraps every service method with a Micrometer `Timer`, recording method name, outcome (success/exception), and duration. One aspect, applied to 200 service methods, with zero code changes to business logic.

**Spring Retry (`@Retryable`):** `@Retryable` from `spring-retry` is an AOP aspect that retries annotated methods on specific exceptions. Internally identical to the custom `RetryAspect` shown above.

---

## 8. Tradeoffs

| Aspect | Benefit | Drawback |
|--------|---------|----------|
| Centralization | Cross-cutting logic in one place | Can be hard to trace execution flow |
| Separation of concerns | Business logic is clean | Debugging requires understanding proxy chain |
| Declarative | `@Transactional` vs manual transaction code | Magic behavior; silent failures (private methods) |
| Runtime weaving | Zero build step | Proxy overhead per call; limited to Spring beans |
| Pointcut expressions | Powerful pattern matching | Can match unintended methods if too broad |

---

## 9. When to Use / When NOT to Use

**Use Spring AOP for:**
- Logging, metrics, auditing applied uniformly across layers
- Transaction management (`@Transactional`)
- Caching (`@Cacheable`)
- Retry logic (`@Retryable`)
- Security checks (`@PreAuthorize`)
- Rate limiting annotations

**Do NOT use Spring AOP for:**
- Logic that must apply to non-Spring objects
- Field access interception (use AspectJ compile-time weaving)
- Constructor interception
- Private or final method interception
- Very hot paths where proxy overhead is measurable (tight inner loops called billions of times)

---

## 10. Common Pitfalls

### Pitfall 1: Broad Pointcut Matching Infrastructure Beans

```java
// BROKEN: matches everything including Spring infrastructure beans
@Pointcut("execution(* com..*(..))")  // way too broad!
public void everything() {}

// This matches:
// - Your service beans (intended)
// - Spring BeanPostProcessors (causes recursive proxy creation)
// - Repository proxy beans (may cause issues)

// FIXED: be specific
@Pointcut("execution(* com.example.service..*(..))")
public void serviceLayer() {}

// Or use within() for a cleaner package-level scope
@Pointcut("within(com.example.service..*)")
public void withinService() {}
```

### Pitfall 2: @Around Not Calling proceed() — Method Silently Skipped

```java
// BROKEN: forgot to call pjp.proceed()
@Around("serviceLayer()")
public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("Before: " + pjp.getSignature());
    // BUG: forgot pjp.proceed() — method never executes!
    // Returns null for all methods
    return null;
}

// FIXED: always call proceed() and return its result
@Around("serviceLayer()")
public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("Before: " + pjp.getSignature());
    Object result = pjp.proceed();  // must call this!
    System.out.println("After: " + pjp.getSignature());
    return result;  // must return the result
}
```

### Pitfall 3: Swallowing Exceptions in @Around

```java
// BROKEN: swallowing exception — caller thinks method succeeded
@Around("serviceLayer()")
public Object safeExecute(ProceedingJoinPoint pjp) {
    try {
        return pjp.proceed();
    } catch (Throwable e) {
        log.error("Error in " + pjp.getSignature(), e);
        return null;  // BUG: @Transactional never sees the exception, doesn't rollback!
    }
}

// FIXED: re-throw after logging
@Around("serviceLayer()")
public Object safeExecute(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (Throwable e) {
        log.error("Error in " + pjp.getSignature(), e);
        throw e;  // re-throw so callers and other advice (@Transactional) see it
    }
}
```

### Pitfall 4: Aspect Not Applied — Missing @EnableAspectJAutoProxy

```java
// BROKEN: @Aspect bean defined but AOP not enabled
@Configuration
// Missing: @EnableAspectJAutoProxy
public class AppConfig { }

@Aspect
@Component
public class LoggingAspect { ... }  // never invoked

// FIXED: enable AOP
@Configuration
@EnableAspectJAutoProxy
public class AppConfig { }
// OR: just use Spring Boot (AopAutoConfiguration enables it automatically)
```

---

## 11. Technologies & Tools

| Component | Role |
|-----------|------|
| `@EnableAspectJAutoProxy` | Enables Spring AOP / registers `AnnotationAwareAspectJAutoProxyCreator` |
| `@Aspect` | Marks a class as an aspect |
| `@Pointcut` | Declares a reusable pointcut expression |
| `ProceedingJoinPoint` | Passed to `@Around` advice; call `.proceed()` to invoke target |
| `JoinPoint` | Passed to non-Around advice; access target, args, signature |
| `AspectJExpressionPointcut` | Programmatic pointcut creation |
| `spring-retry` | `@Retryable` / `@Recover` AOP-based retry |
| `spring-aspects` | AspectJ-based weaving for `@Configurable` and domain objects |
| `aspectjweaver` | AspectJ weaver (required for load-time weaving) |

---

## 12. Interview Questions with Answers

**What is the difference between Spring AOP and AspectJ?**
Spring AOP is proxy-based runtime weaving limited to method execution on Spring-managed beans. AspectJ is a full AOP framework supporting compile-time, post-compile, and load-time weaving, applicable to any Java object, including constructors, field access, and static initializers. Spring AOP is simpler (zero setup) but limited to Spring beans and public methods. AspectJ requires the AspectJ compiler or load-time weaving agent but can intercept anything. Most Spring applications use Spring AOP; AspectJ is reserved for cases requiring field interception or non-Spring object advice.

**What are the five types of advice in Spring AOP?**
`@Before` runs before the method (cannot prevent execution unless throwing an exception). `@AfterReturning` runs after normal return (can access return value). `@AfterThrowing` runs after an exception (can access the exception). `@After` runs always, like a finally block. `@Around` wraps the entire method, calling `pjp.proceed()` to invoke the target — most powerful and most error-prone. For most cases `@Around` is preferred because it handles both normal and exceptional flows in one place.

**How do you write a pointcut expression for all public methods in a service package?**
`execution(public * com.example.service..*(..))` — matches: public methods (any return type, any method name) in any class within the `com.example.service` package and sub-packages, with any parameters. Alternatively, `within(com.example.service..*)` matches all method executions within those classes regardless of visibility (though Spring AOP only intercepts methods that can be overridden). `@annotation(com.example.Timed)` matches methods annotated with `@Timed` regardless of package.

**What is a JoinPoint and what information can you access from it?**
`JoinPoint` is passed to non-`@Around` advice and provides: `getTarget()` (the target bean, not the proxy), `getThis()` (the proxy), `getArgs()` (method arguments as `Object[]`), `getSignature()` (method signature including name, declaring type, parameter types), `getKind()` (always "method-execution" in Spring AOP). For `@Around` advice, `ProceedingJoinPoint` extends `JoinPoint` with `proceed()` (invoke target with original args) and `proceed(Object[])` (invoke with modified args).

**How does @Transactional use Spring AOP?**
`@Transactional` is implemented by `TransactionInterceptor`, which is registered as an AOP `@Around` advice via `AnnotationTransactionAttributeSource`. When the container detects a `@Transactional` bean, `AnnotationAwareAspectJAutoProxyCreator` wraps it in a proxy. On each method call, `TransactionInterceptor.invoke()` checks the method's transaction attributes, starts/joins/suspends a transaction, calls `proceed()`, then commits or rolls back. This is why `@Transactional` on private methods is silently ignored — the proxy cannot intercept them.

**How do you control the order in which multiple aspects apply to the same method?**
Use `@Order(n)` on the aspect class — lower numbers have higher priority and run as the outermost wrapper in `@Around` advice (their `before` logic runs first; their `after` logic runs last). If no `@Order` is specified, order is undefined and may differ across JVM runs. For Spring's built-in aspects: Security (highest priority) → Transaction (middle) → Application aspects (varies). Use `Ordered.HIGHEST_PRECEDENCE` and `Ordered.LOWEST_PRECEDENCE` for clear semantics.

**What is the difference between @Before and the "before" part of @Around?**
`@Before` runs before the method and cannot prevent execution (only throwing an exception aborts). The "before" part of `@Around` (code before `pjp.proceed()`) can prevent execution by not calling `proceed()`, by calling `proceed()` with modified arguments, or by returning a short-circuit value. `@Around` has full control: it can modify arguments, modify the return value, catch exceptions and return a fallback, or retry the call. Use `@Before` for simple pre-invocation logic; use `@Around` when you need control over execution.

**Can a pointcut match beans in other Spring contexts (parent/child)?**
Spring AOP only operates within a single `ApplicationContext`. A `BeanPostProcessor` in the child context (servlet context) creates proxies for beans in the child context. Beans in the parent context (root context) are proxied by post-processors in the parent context. An aspect declared in the child context does NOT apply to parent context beans. This is why aspects for service beans should be defined in the root context, while aspects for MVC components are in the servlet context.

**What happens if @Around advice does not call proceed()?**
The target method is never invoked. The advice return value (or null if no return statement) is returned to the caller. No exception from the method propagates. This silently breaks the application — all methods matching the pointcut return null and execute no business logic. This is a dangerous bug with no compile-time or startup warning. Always call `pjp.proceed()` in `@Around` advice unless intentionally short-circuiting (e.g., cache hit, circuit breaker open, circuit is returning cached response).

**How does Spring AOP handle exceptions thrown by @Around advice?**
If `@Around` advice calls `pjp.proceed()` and the target throws an exception, and the advice does not catch it (or re-throws), the exception propagates normally to the caller. Other advice types (`@AfterThrowing`, `@After`) are also triggered. If the `@Around` advice catches the exception and does not re-throw, the caller sees a normal return (or the advice's return value). `@AfterThrowing` advice only runs if the exception propagates past all `@Around` advice. To ensure proper `@Transactional` rollback, never swallow exceptions in `@Around` advice.

**What is the `@within` pointcut designator and how does it differ from `within`?**
`within(com.example.service..*)` matches all method executions in classes within the specified package. `@within(com.example.annotation.Monitored)` matches all method executions in classes that are annotated with `@Monitored`. The difference: `within` matches by package/class name; `@within` matches by annotation on the class. Similarly, `@annotation` matches by annotation on the method. Use `@within` when you want to apply advice to all methods of annotated classes (e.g., all methods in `@RestController` classes).

**How do you get access to the annotation instance's attributes inside advice?**
Bind the annotation type as a parameter to the advice method and reference it in the pointcut. For `@Around("@annotation(myAnnotation)")` where `myAnnotation` is the parameter name (must match!), Spring binds the actual annotation instance to that parameter. Then `myAnnotation.attribute()` accesses the value. This is how `@Retryable(maxAttempts=3)` works — the retry aspect reads `maxAttempts` directly from the annotation instance passed to `@Around`.

**What is the performance cost of Spring AOP?**
Spring AOP adds two costs: proxy creation (one-time at startup, typically negligible) and per-call proxy dispatch overhead. For CGLIB proxies, `MethodProxy.invokeSuper()` is faster than reflection. For JDK proxies, `Method.invoke()` uses reflection. Measured overhead is typically 1-10 microseconds per call depending on JIT optimization. For hot paths (millions of calls/second), this can matter. Pointcut evaluation is also a cost — complex `execution()` expressions evaluated on every method call can add up. Mitigation: use `@Pointcut` method-level caching (Spring does this), or use AspectJ compile-time weaving (near-zero overhead).

**How would you implement method-level audit logging using AOP?**
Define a custom `@Auditable` annotation with attributes for action and resource. Write an `@Aspect` with `@Around("@annotation(auditable)")`. In the advice: extract the annotation's action/resource attributes, capture the authenticated user from `SecurityContextHolder`, record the attempt, call `pjp.proceed()`, record success (including return value summary), catch exceptions to record failure. Annotate service methods with `@Auditable(action="CREATE_ORDER", resource="orders")`. This produces a complete audit trail for any method with zero change to business logic.

**When should you prefer AspectJ weaving over Spring AOP?**
Use AspectJ when: (1) you need to advise non-Spring objects (domain objects, value objects); (2) you need to intercept field access or constructor execution; (3) private method interception is required; (4) per-call performance overhead of proxy dispatch is unacceptable (tight loops). AspectJ compile-time weaving requires the AspectJ compiler (`ajc`) in the build. Load-time weaving uses a JVM agent (`-javaagent:aspectjweaver.jar`). For `@Configurable` (DI into non-Spring domain objects), AspectJ load-time weaving with `@EnableSpringConfigured` is the standard approach.

**What is the @EnableAspectJAutoProxy annotation and what does it do?**
`@EnableAspectJAutoProxy` registers `AnnotationAwareAspectJAutoProxyCreator` as a `BeanPostProcessor`. This post-processor inspects every bean during initialization and, if any declared `@Aspect` matches the bean via a pointcut, creates a proxy (CGLIB or JDK) for that bean. Without this annotation, `@Aspect` classes are created as regular beans but their advice is never applied. Spring Boot's `AopAutoConfiguration` auto-configures this when `spring-aop` is on the classpath and `spring.aop.auto=true` (the default), so explicit `@EnableAspectJAutoProxy` is rarely needed in Boot applications.

---

## 13. Best Practices

1. **Use `@Around` for most advice** — it handles both normal and exception paths in one method.
2. **Always call `pjp.proceed()` in `@Around`** — forgetting it silently drops all method calls.
3. **Never swallow exceptions in `@Around`** — always re-throw to allow `@Transactional` and other advice to see them.
4. **Name pointcuts clearly** — `@Pointcut("execution(* com.example.service..*(..))") public void serviceLayer(){}` is readable; anonymous inline expressions are not.
5. **Keep pointcuts narrow** — overly broad expressions match infrastructure beans and cause unexpected behavior.
6. **Use `@Order` for deterministic advice ordering** when multiple aspects apply to the same methods.
7. **Prefer `@annotation(myAnnotation)` over broad `execution()` pointcuts** for better control and performance.
8. **Profile AOP overhead** on hot paths — for methods called > 1M times/second, measure and consider AspectJ.
9. **Test aspects independently** with `AspectJProxyFactory` or an `ApplicationContext` slice test.
10. **Document aspects prominently** — code readers often don't realize a method has advice applied; comments in the aspect and on target methods improve maintainability.

---

## 14. Case Study

### Problem: Adding Distributed Tracing to 200 Service Methods Without Modifying Business Logic

**Context:** A microservices platform has 200 service methods. Operations needs trace IDs in all log lines to correlate logs across services.

**Naive approach (rejected):** Add `log.info("[traceId={}]", MDC.get("traceId"), ...)` to every log statement in every method. 200+ methods to modify, high error rate, ongoing maintenance burden.

**AOP solution:**

```java
// Step 1: Aspect to inject trace ID and log method entry/exit
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // outermost — wraps everything
public class TracingAspect {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";

    @Pointcut("within(@org.springframework.stereotype.Service *) || " +
              "within(@org.springframework.web.bind.annotation.RestController *)")
    public void applicationLayer() {}

    @Around("applicationLayer()")
    public Object traceMethodExecution(ProceedingJoinPoint pjp) throws Throwable {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);
        }

        String methodName = pjp.getSignature().toShortString();
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            // Metric recorded via Micrometer (not shown)
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[traceId={}] {} FAILED after {}ms: {}",
                     traceId, methodName, elapsed, e.getMessage());
            throw e;
        } finally {
            // Only clear MDC at the outermost call level
            // (Check thread-local to avoid clearing in nested calls)
        }
    }
}

// Step 2: HTTP filter to extract trace ID from incoming requests
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        String traceId = req.getHeader("X-Trace-ID");
        if (traceId == null) traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        res.setHeader("X-Trace-ID", traceId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}

// Step 3: logback.xml uses MDC in pattern
// %d{HH:mm:ss} [%X{traceId}] %-5level %logger{36} - %msg%n
```

**Result:**
- Zero changes to 200 service methods
- All log lines automatically include `[traceId=abc12345]`
- Method durations tracked as Micrometer metrics
- Trace IDs propagated from HTTP headers
- New service methods automatically traced

**Lesson:** AOP is the right tool when a behavior must apply uniformly across many methods without polluting business logic. The key is defining a precise pointcut (`@Service` and `@RestController` classes), using `@Around` for full control, and ordering the aspect at `HIGHEST_PRECEDENCE` so it wraps all other advice.
