# Spring Events and Scheduling

## 1. Concept Overview

Spring provides two complementary mechanisms for decoupling application logic:

**Application Events** — a publish/subscribe model within the Spring ApplicationContext:
- `ApplicationEventPublisher` — synchronous event publication
- `@EventListener` — annotation-driven listener methods
- `@TransactionalEventListener` — listener that fires at a transaction lifecycle phase (AFTER_COMMIT, BEFORE_COMMIT, AFTER_ROLLBACK)
- Async events via `@Async` + a configured `TaskExecutor`

**Scheduling** — time-based task execution:
- `@Scheduled` — cron, fixed-rate, fixed-delay, and initial-delay scheduling
- `@EnableScheduling` — activates the scheduling infrastructure
- `ThreadPoolTaskScheduler` — configurable thread pool for scheduled tasks
- `TaskExecutor` / `ThreadPoolTaskExecutor` — async task execution
- `ShedLock` — distributed locking to prevent multiple instances from running the same scheduled task concurrently

Spring Boot 3.2+ ships with virtual thread integration: `spring.threads.virtual.enabled=true` makes `@Async` and `@Scheduled` use virtual threads by default.

---

## 2. Intuition

> Application events are like a radio station: the publisher broadcasts a signal; anyone who has tuned to the right frequency receives it — the publisher does not know (or care) how many listeners exist or what they do.

**Key insight for `@TransactionalEventListener`:** Events published inside a `@Transactional` method do not fire until the transaction commits. This makes them safe for "post-commit side effects" (send email, push notification, fire Kafka message). But listeners cannot roll back the original transaction — they are late-binding callbacks, not part of the original unit of work.

**Why this matters:** The "publish event on commit" pattern is the simplest form of the outbox-without-a-table pattern. It avoids coupling the main service to auxiliary effects while ensuring those effects only happen if the core change persisted. The tradeoff is that listener failures do not roll back the original transaction — understanding this is a senior-level distinction.

---

## 3. Core Principles

1. **Events are synchronous by default**: `publishEvent()` blocks until all synchronous listeners complete.
2. **`@Async` makes listeners fire on a separate thread**: the publisher unblocks immediately; the listener runs in a `TaskExecutor` thread.
3. **`@TransactionalEventListener` binds to transaction lifecycle**: defaults to `AFTER_COMMIT`. If no transaction is active, the event is discarded (use `fallbackExecution=true` to fire anyway).
4. **`@Scheduled` requires `@EnableScheduling`**: all `@Scheduled` methods run in a single-threaded scheduler by default. Configure `ThreadPoolTaskScheduler` to allow concurrent scheduled tasks.
5. **Distributed scheduling needs a lock**: without a distributed lock, all pods in a cluster run `@Scheduled` tasks simultaneously. `ShedLock` (or Quartz) prevents this.
6. **`@Async` + `@Transactional` do NOT compose automatically**: an async method gets a new thread with no inherited transaction context. Transactions must be re-opened explicitly in the async method.

---

## 4. Types / Architectures / Strategies

### 4.1 Event Listener Types

| Annotation | When It Fires | Transaction | Thread |
|---|---|---|---|
| `@EventListener` | Synchronously, inline in publisher's call stack | Inherits publisher's transaction | Publisher's thread |
| `@Async @EventListener` | After publisher returns, on executor thread | None — new transaction must be opened explicitly | Executor thread |
| `@TransactionalEventListener(AFTER_COMMIT)` | After the surrounding transaction commits | None by default; use `@Transactional(REQUIRES_NEW)` | Publisher's thread (by default) |
| `@TransactionalEventListener(BEFORE_COMMIT)` | Just before the surrounding transaction commits | Participates in the same transaction | Publisher's thread |
| `@TransactionalEventListener(AFTER_ROLLBACK)` | After the transaction rolls back | None | Publisher's thread |

### 4.2 Scheduling Configuration Options

| Option | Annotation | Notes |
|---|---|---|
| Fixed delay | `@Scheduled(fixedDelay=5000)` | N ms after previous execution completes |
| Fixed rate | `@Scheduled(fixedRate=5000)` | Every N ms regardless of completion (overlaps if slow) |
| Cron | `@Scheduled(cron="0 0 2 * * *")` | Spring cron: `s m h d M weekday`; `?` not supported in Spring, use `*` |
| Initial delay | `@Scheduled(initialDelay=10000, fixedRate=5000)` | Wait 10s before first run |
| Duration string | `@Scheduled(fixedDelayString="${batch.delay}")` | Property-driven; supports `PT5S` (ISO-8601) |

### Decoding fixedDelay vs fixedRate

The two options anchor the interval to different events, which is the whole difference:

```
  fixedDelay:  next_start = previous_END   + N
  fixedRate:   next_start = previous_START + N     (deferred if the thread is busy)
```

**Put simply.** "`fixedDelay` guarantees a gap between runs; `fixedRate` guarantees a
cadence of start times." Only one of them can be honoured when a run overruns `N`, and
knowing which one you asked for is the difference between a task that quietly slips and
a task that quietly overlaps.

| Symbol | What it is |
|--------|------------|
| `N` | The configured interval in ms. `5000` in both rows above |
| `previous_END` | Wall-clock time the prior invocation returned — the `fixedDelay` anchor |
| `previous_START` | Wall-clock time the prior invocation began — the `fixedRate` anchor |
| `initialDelay` | Offset before the first run only. `10000` above; does not repeat |
| pool size `1` | Spring's default. Forces `fixedRate` to defer rather than overlap |

**Walk one example.** A task that takes 7s, configured with `N = 5000` and
`initialDelay = 10000`, on the default single-thread scheduler:

```
  fixedDelay = 5000        anchored to END, so the period is constant
    run 1   start 10,000   end 17,000
    run 2   start 22,000   end 29,000      (17,000 + 5,000)
    run 3   start 34,000   end 41,000      (29,000 + 5,000)
    period  = 7,000 + 5,000 = 12,000 ms, forever. Never overlaps.

  fixedRate = 5000         anchored to START, but 7,000 > 5,000 so it cannot keep up
    nominal starts: 10,000  15,000  20,000  25,000
    run 1   nominal 10,000   actual 10,000   slip     0   end 17,000
    run 2   nominal 15,000   actual 17,000   slip 2,000   end 24,000
    run 3   nominal 20,000   actual 24,000   slip 4,000   end 31,000
    run 4   nominal 25,000   actual 31,000   slip 6,000   end 38,000
    slip grows by (7,000 - 5,000) = 2,000 ms every run, without bound.
```

The `fixedDelay` schedule is stable at a 12-second period. The `fixedRate` schedule
falls further behind on every iteration — 2 seconds per run, and once saturated the runs
go back-to-back every 7,000 ms, so an hour fits `3,600,000 / 7,000 = 514` runs and the
slip reaches `514 x 2,000 = 1,028,000 ms`, about 17 minutes of drift. You asked for a
cadence the task cannot sustain.

**What the default pool size of 1 is actually protecting you from.** Raise the scheduler
to `setPoolSize(5)` and that `fixedRate` task stops deferring: run 2 launches at its
nominal 15,000 while run 1 is still executing until 17,000, so two invocations overlap
for 2 seconds. For a stateful task — one holding a cursor, writing a file, or draining a
queue — that overlap is a correctness bug, not a performance win. This is why the
guidance is `fixedDelay` for anything that must not overlap, regardless of pool size.

---

## 5. Architecture Diagrams

### Event Publication and Listener Dispatch

```mermaid
sequenceDiagram
    participant S as OrderService (@Transactional)
    participant DB as Database
    participant M as Event Multicaster
    participant L1 as @EventListener (emailService)
    participant L2 as @TransactionalEventListener(AFTER_COMMIT)
    participant L3 as @Async @EventListener

    S->>DB: repo.save(order)
    S->>M: publisher.publishEvent(OrderCreatedEvent)
    M->>L1: dispatch (synchronous, same TX)
    L1-->>S: sendConfirmation() completes in-TX
    Note over S,DB: transaction commits here
    M->>L2: dispatch (AFTER_COMMIT — no rollback possible)
    L2-->>L2: notificationService.push(), kafkaTemplate.send() (fire-and-forget)
    M->>L3: dispatch (on TaskExecutor thread — no TX context)
```

### Distributed Scheduling with ShedLock

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    pod1["Pod-1 @Scheduled"] --> lock["shedlock table (PostgreSQL/Redis)\nINSERT ... WHERE lock_until < NOW()"]
    pod2["Pod-2 @Scheduled"] --> lock
    pod3["Pod-3 @Scheduled"] --> lock
    lock -->|"only ONE pod succeeds"| winner["Pod-1 wins: runs the task"]
    lock -->|"lock acquisition fails"| skip["Pod-2, Pod-3: skip this run"]

    class pod1,pod2,pod3 req
    class lock base
    class winner train
    class skip lossN
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Basic Application Events

```java
// Event class — a plain POJO (or extend ApplicationEvent for classic style)
public record OrderCreatedEvent(Order order, Instant publishedAt) {}

// Publisher
@Service
@RequiredArgsConstructor
public class OrderService {
    private final ApplicationEventPublisher publisher;
    private final OrderRepository repo;

    @Transactional
    public Order createOrder(CreateOrderRequest req) {
        Order order = repo.save(req.toEntity());
        publisher.publishEvent(new OrderCreatedEvent(order, Instant.now()));
        return order;
    }
}

// Listener (synchronous, runs in same transaction as publisher)
@Component
public class AuditListener {
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        auditRepo.save(new AuditEntry(event.order().id(), "ORDER_CREATED"));
        // Runs in the same transaction — if this throws, the whole TX rolls back
    }
}
```

### 6.2 @TransactionalEventListener — Post-Commit Side Effects

```java
// BROKEN: side effect fires even if the transaction rolls back
@EventListener
public void sendEmail(OrderCreatedEvent event) {
    emailService.send(event.order().email(), "Your order was created");
    // If createOrder() throws AFTER publishing, this may fire before rollback
    // (actually Spring defers synchronous @EventListener to after listener registration
    // but the semantics are complex — use @TransactionalEventListener for safety)
}

// FIX: only fire after the transaction commits
@Component
public class EmailNotificationListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendConfirmationEmail(OrderCreatedEvent event) {
        // Guaranteed: createOrder() transaction has committed; order IS in DB
        emailService.send(event.order().email(), "Order confirmed: " + event.order().id());
    }
}

// If listener needs its own transaction (e.g., updates another table):
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void notifyAnalytics(OrderCreatedEvent event) {
    analyticsRepo.save(new AnalyticsEvent("order_created", event.order().id()));
    // REQUIRES_NEW opens a fresh transaction after the original commit.
    // If THIS transaction fails, the original order is NOT rolled back — it already committed.
    // Use the outbox pattern for true atomicity.
}
```

### 6.3 Async Events with @Async

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-event-");
        executor.initialize();
        return executor;
    }
}

// Async listener — fire-and-forget, publisher unblocks immediately
@Component
public class PushNotificationListener {

    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
                                 fallbackExecution = true)
    public void sendPush(OrderCreatedEvent event) {
        // Runs on asyncExecutor thread; no transaction context from publisher
        pushGateway.send(event.order().userId(), "Your order is confirmed!");
    }
}
// fallbackExecution=true: fires even if no transaction was active (e.g., in tests or scripts)
```

### 6.4 @Scheduled Configuration

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {

    // Replace the default single-threaded scheduler with a pool
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        return scheduler;
    }
}

@Component
public class ReportGenerationTask {

    @Scheduled(cron = "0 0 1 * * MON-FRI")   // 1:00 AM, Mon–Fri
    public void generateDailyReport() {
        reportService.generate(LocalDate.now());
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void healthCheck() {
        // Runs 30s after previous run completes; first run 10s after startup
        externalApi.ping();
    }
}
```

### 6.5 Distributed Locking with ShedLock

```java
// pom.xml: net.javacrumbs.shedlock:shedlock-spring:7.10.1
//          net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")  // max 10 min lock
public class ShedLockConfig {
    @Bean
    public LockProvider lockProvider(DataSource ds) {
        return new JdbcTemplateLockProvider(ds,
            JdbcTemplateLockProvider.Configuration.builder()
                .withTableName("shedlock")
                .usingDbTime()  // use DB clock, not JVM clock (avoids clock skew)
                .build());
    }
}

// Required table:
// CREATE TABLE shedlock (
//   name VARCHAR(64) NOT NULL PRIMARY KEY,
//   lock_until TIMESTAMP NOT NULL,
//   locked_at TIMESTAMP NOT NULL,
//   locked_by VARCHAR(255) NOT NULL
// );

@Scheduled(cron = "0 0 3 * * *")
@SchedulerLock(name = "nightly_cleanup",
               lockAtLeastFor = "PT5M",    // hold lock min 5 min (prevent thundering herd)
               lockAtMostFor = "PT30M")    // release lock after 30 min even if dead
public void nightlyCleanup() {
    dataService.deleteExpiredRecords();
}
```

### 6.6 Virtual Thread Executor for @Async (Spring Boot 3.2+)

```yaml
# application.yaml
spring:
  threads:
    virtual:
      enabled: true    # all @Async and @Scheduled methods use virtual threads
```

```java
// Or configure explicitly:
@Bean
public Executor asyncExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
// @Async methods no longer need a bounded thread pool; each invocation
// gets its own virtual thread — blocking I/O does not pin OS threads.
```

### 6.7 Preventing @Scheduled From Running During Application Startup

```java
// Problem: @Scheduled fires at startup if fixedDelay=0 or cron matches immediately
@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)  // wait 60s before first run

// Or: keep scheduling off in tests with a property guard on the config class
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig { }
// test: @TestPropertySource(properties = "app.scheduling.enabled=false")

// Or: use a profile
@Profile("!test")
@Scheduled(cron = "...")
public void productionOnlyTask() { ... }
```

### 6.8 Trace and MDC Propagation to @Async

The current Micrometer `Observation` (and with it the trace id, span id, and the `traceId`
MDC entries that Micrometer Tracing writes) lives in `ThreadLocal`s on the caller's thread.
An `@Async` method runs on an executor thread, so by default its logs carry no trace id
and any HTTP call it makes starts a new trace:

```
  caller thread                              executor thread (default)
  -------------                              -------------------------
  Observation: http.server.requests          Observation: none
  MDC: traceId=4bf9..., spanId=00f0...       MDC: (empty)
  orderService.place() --@Async submit-->    emailService.send()  -> log line with no traceId
                                                                  -> RestClient call = new trace

  with ContextPropagatingTaskDecorator:
  submit() captures a ContextSnapshot  -->   decorator restores it around run()
                                             MDC: traceId=4bf9..., spanId=...
```

Spring Framework 6.1 added `ContextPropagatingTaskDecorator`, which captures a Micrometer
`ContextSnapshot` of every registered `ThreadLocalAccessor` at submit time and restores it
around the task. Boot 4.1 registers it as a bean only when you opt in:

```yaml
spring:
  task:
    execution:
      propagate-context: true   # off by default
```

Boot applies every `TaskDecorator` bean to the auto-configured `applicationTaskExecutor`
and to executors built from its `ThreadPoolTaskExecutorBuilder` or
`SimpleAsyncTaskExecutorBuilder`, so declaring the bean yourself has the same effect as the
property. An executor created with `new` gets neither and needs the decorator set by hand:

```java
@Bean
ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
    return new ContextPropagatingTaskDecorator();
}

@Bean
ThreadPoolTaskExecutor reportExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(8);
    ex.setTaskDecorator(new ContextPropagatingTaskDecorator());   // not applied automatically here
    return ex;
}
```

Propagation covers what has a `ThreadLocalAccessor` registered with Micrometer's
`ContextRegistry` (the observation, and therefore tracing and its MDC fields). It does not
make the async method join the caller's transaction, and `SecurityContextHolder` still
needs `DelegatingSecurityContextAsyncTaskExecutor` unless a security accessor is
registered. `@Scheduled` runs have no caller to inherit from; since Framework 6.1 each run
gets its own `tasks.scheduled.execution` observation and therefore its own trace.

---

## 7. Real-World Examples

### 7.1 E-Commerce Order Confirmation Flow

An order service uses `@TransactionalEventListener(AFTER_COMMIT)` to publish order confirmation events. The listener sends a push notification and emails an invoice. If either fails, the order is not rolled back — it's already committed. A separate compensation job retries failed notifications by scanning for orders without confirmation timestamps.

### 7.2 Nightly ETL with ShedLock (Multi-Pod Kubernetes Deployment)

A financial reporting service runs a nightly data reconciliation at 2:00 AM. With 5 pods in production, without ShedLock all 5 would run the job simultaneously, generating 5 duplicate reports and 5 database writes. ShedLock on PostgreSQL ensures only one pod acquires the lock; the other 4 skip silently. `lockAtMostFor = "PT1H"` prevents a dead pod from holding the lock forever.

### 7.3 Cache Warm-up on Startup with @EventListener

```java
@Component
public class CacheWarmer {
    @EventListener(ApplicationReadyEvent.class)
    public void warmCaches() {
        // Fires after all beans are initialised and the app is ready to serve requests
        productCatalog.preloadTopCategories();
    }
}
// ApplicationReadyEvent fires after embedded server starts — safer than @PostConstruct
// which fires during context refresh (before embedded server is ready)
```

---

## 8. Tradeoffs

| Approach | Decoupling | Transactional Safety | Failure Handling | When to Use |
|---|---|---|---|---|
| `@EventListener` (sync) | Good | Same TX as publisher | Rolls back publisher TX | In-process side effects that should be part of same TX |
| `@TransactionalEventListener(AFTER_COMMIT)` | Good | Post-commit; no rollback | Failure does not roll back original | Post-commit side effects; send email, push notification |
| `@Async @EventListener` | Excellent | None — new thread | Silent failure unless logged | Fire-and-forget side effects; publisher must not depend on result |
| Kafka/RabbitMQ | Excellent | At-least-once; idempotency required | DLQ for retry | Cross-service events; durable, ordered, replay |
| Outbox pattern | Excellent | Atomic with original TX | Poller retries | Exactly-once cross-service events |

---

## 9. When to Use / When NOT to Use

### Use `@TransactionalEventListener` when:
- The side effect (email, notification, analytics write) should only happen after the main transaction commits
- The side effect does not need to be part of the same atomic unit of work
- Failure of the side effect is acceptable (or handled by a retry/compensation job)

### Use `@Async @EventListener` when:
- The side effect is slow (HTTP call, email SMTP) and must not block the main request
- You accept eventual delivery (no guarantee if the JVM crashes between commit and listener execution)

### Use outbox pattern (not `@TransactionalEventListener`) when:
- The event must be delivered exactly once, even across JVM crashes
- Downstream consumers are event-sourcing or require strong ordering guarantees

### Use ShedLock when:
- Running `@Scheduled` tasks in a multi-instance deployment (Kubernetes, cloud)
- The task is not idempotent (generating a report twice causes problems)

### Do NOT use `@Async` + `@Transactional` naively:
- Async methods run in a new thread with no inherited `SecurityContext` or transaction
- Explicitly re-open a transaction with `@Transactional(propagation = REQUIRES_NEW)` if needed
- Propagate `SecurityContext` manually via `DelegatingSecurityContextExecutor`

---

## 10. Common Pitfalls

### Pitfall 1: @TransactionalEventListener fires when there is no transaction
```java
// BROKEN: calling a @TransactionalEventListener-annotated method outside a transaction
// (e.g., from a test, a scheduled job, or a non-@Transactional service method)
// The event is silently discarded by default.

// FIX: add fallbackExecution = true to fire even without a transaction
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void handleEvent(MyEvent event) { ... }
```

### Pitfall 2: @Async listener swallows exceptions silently
```java
// BROKEN: exception in async listener disappears; publisher never knows
@Async
@EventListener
public void process(OrderEvent event) {
    throw new RuntimeException("failed");  // silently logged only if UEH is configured
}

// FIX: configure AsyncUncaughtExceptionHandler
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Async error in {}: {}", method, ex.getMessage());
    }
}
```

### Pitfall 3: Single-threaded scheduler — @Scheduled tasks blocking each other
```java
// BROKEN: all @Scheduled methods share one thread by default
// If task A takes 30s and task B is fixed-rate 10s, task B will miss runs
// until task A completes — they are queued on the single scheduler thread.

// FIX: configure ThreadPoolTaskScheduler with multiple threads
@Bean
public TaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setPoolSize(10);
    return s;
}
```

### Pitfall 4: ShedLock lock held after pod crash
If a pod crashes while holding a ShedLock lock, the `lock_until` column prevents other pods from running until the lock expires. Set `lockAtMostFor` to a safe upper bound (e.g., twice the expected task duration). Use `usingDbTime()` to avoid JVM clock drift causing premature lock release.

### Pitfall 5: @Scheduled + @Transactional self-invocation proxy bypass
A `@Scheduled` method calling another `@Transactional` method on `this` bypasses the proxy. Use `ApplicationContext.getBean()` or `@Autowired` self-reference, or restructure into a separate `@Service` bean.

---

## 11. Technologies & Tools

| Tool / Feature | Version | Purpose |
|---|---|---|
| `ApplicationEventPublisher` | Spring 3.0+ | Synchronous in-process event publication |
| `@EventListener` | Spring 4.2+ | Annotation-driven listener method |
| `@TransactionalEventListener` | Spring 4.2+ | Binds listener to transaction lifecycle |
| `@Async` | Spring 3.0+ | Executes annotated method asynchronously in an executor |
| `@EnableAsync` | Spring 3.1+ | Activates `@Async` processing |
| `@Scheduled` | Spring 3.0+ | Annotation-driven scheduling |
| `@EnableScheduling` | Spring 3.1+ | Activates scheduling infrastructure |
| `ThreadPoolTaskExecutor` | Spring 3.0+ | Configurable thread pool for `@Async` |
| `ThreadPoolTaskScheduler` | Spring 3.0+ | Configurable thread pool for `@Scheduled` |
| `ShedLock` | `net.javacrumbs.shedlock:shedlock-spring:7.x` | Distributed scheduling lock (JDBC/Redis/Mongo) |
| `Quartz` | Library | Full-featured scheduler with job persistence, cluster support |
| `Spring Cloud Task` | Spring Cloud | Short-lived task execution with job history |
| `ApplicationReadyEvent` | Spring Boot | Fires after embedded server is ready (use instead of `@PostConstruct` for post-startup work) |
| `spring.threads.virtual.enabled=true` | Spring Boot 3.2 | @Async and @Scheduled use virtual threads |
| `ContextPropagatingTaskDecorator` | Spring 6.1+ | Restores the caller's Micrometer context (observation, trace, MDC) on the executor thread |
| `spring.task.execution.propagate-context=true` | Spring Boot 4.1 | Opt-in context propagation for the auto-configured `@Async` executor |

---

## 12. Interview Questions with Answers

**Q: Log lines from an `@Async` method have no `traceId`, and its outbound HTTP calls show up as separate traces. Why, and how do you fix it in Boot 4.1?**
**Short:** The observation and MDC are ThreadLocal on the caller; set spring.task.execution.propagate-context=true or register a ContextPropagatingTaskDecorator.
Micrometer stores the current `Observation` in a `ThreadLocal`, and Micrometer Tracing derives the span and the `traceId`/`spanId` MDC fields from it. An `@Async` method runs on an executor thread where none of that is set, so its logs lack the trace id and a `RestClient` call it makes opens a new root span. Spring Framework 6.1's `ContextPropagatingTaskDecorator` snapshots every registered `ThreadLocalAccessor` at submit time and restores it around the task. In Boot 4.1, `spring.task.execution.propagate-context=true` (off by default) registers it as a bean, and Boot applies `TaskDecorator` beans to the auto-configured executor and to executors built from its executor builders; an executor you construct with `new` needs `setTaskDecorator(...)` by hand. It does not carry the caller's transaction, and the security context still needs `DelegatingSecurityContextAsyncTaskExecutor` unless a security accessor is registered.

**Q: What is the difference between `@EventListener` and `@TransactionalEventListener`?**
**Short:** `@EventListener` fires inline regardless of transaction outcome, while `@TransactionalEventListener` waits for commit by default.

`@EventListener` fires synchronously inline within the publisher's call stack, participating in the same transaction (if any). `@TransactionalEventListener` binds the listener to a transaction lifecycle phase — defaulting to `AFTER_COMMIT`. If the publisher's transaction rolls back, `@TransactionalEventListener(AFTER_COMMIT)` listeners never fire; `@EventListener` listeners would have already fired and cannot be rolled back. Use `@TransactionalEventListener` for any side effect that must only happen if the primary operation persisted — sending an email after a successful order, pushing an event to Kafka after a DB write.

**Q: Why does `@TransactionalEventListener` silently not fire in some situations, and how do you fix it?**
**Short:** It needs an active transaction to bind to, so an event published outside one is silently dropped unless `fallbackExecution = true`.

`@TransactionalEventListener` requires an active transaction to bind to. If the event is published outside a transaction (from a test, a scheduler, or a non-`@Transactional` method), the event is silently discarded. Diagnosis: add `fallbackExecution = true` to the annotation — this makes it fire even without a surrounding transaction. Alternatively, ensure the publishing code is always called within a `@Transactional` method. The silent discard is a frequent source of "event listener not called" bugs in test environments where the service method is called without the transaction proxy active.

**Q: What happens when an `@Async` listener throws an exception?**
**Short:** By default it is logged at WARN and silently swallowed, so the publisher never learns the async side effect failed.

By default, async exceptions are logged by Spring's `SimpleAsyncUncaughtExceptionHandler` at `WARN` level and silently swallowed — the publisher's thread never sees them. This can cause data inconsistencies: the primary transaction committed, the async listener failed, and the side effect never happened. Fix: implement `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` to return a handler that alerts on failure, retries, or writes to a DLQ. For critical side effects, consider using `@TransactionalEventListener` with a persistent message broker (Kafka, RabbitMQ) instead of `@Async`.

**Q: How does ShedLock prevent multiple instances from running a scheduled task, and what are `lockAtLeastFor` and `lockAtMostFor`?**
**Short:** ShedLock uses an atomic lock-row write so only one instance runs a task; `lockAtMostFor` frees the lock if that instance crashes.

ShedLock uses an `INSERT ... WHERE lock_until < NOW()` statement (or equivalent) in the backing store (JDBC table, Redis key, MongoDB document). Only one JVM instance succeeds in inserting/updating the lock row; all others get a constraint violation and skip the task for that scheduled run. `lockAtLeastFor` is the minimum duration the lock is held — even if the task finishes quickly, other instances cannot start until this duration expires (prevents near-simultaneous re-runs from two pods). `lockAtMostFor` is the safety valve: if the lock-holder JVM crashes without releasing the lock, the lock is released after this duration so other instances can take over. Always set `lockAtMostFor` to prevent permanent lock-up.

**Q: Can you use `@Transactional` on an `@Async` method, and what are the pitfalls?**
**Short:** Yes, but the async method's transaction is fully independent of the caller's, so its failure never rolls back the caller.

Yes, but with important caveats. An `@Async` method runs on a different thread with no transaction context from the caller. Adding `@Transactional` on the async method opens a new transaction on that thread — the async method's transaction is completely independent. Pitfall 1: the caller's transaction commits before the async method starts, so the async method sees the committed state (usually desired). Pitfall 2: if the async method's transaction fails, the caller's transaction is NOT rolled back — they are independent. Pitfall 3: `SecurityContextHolder` (which is `ThreadLocal`) is not automatically propagated to the async thread — configure `DelegatingSecurityContextAsyncTaskExecutor` to propagate security context.

**Q: What is the default scheduling thread pool size, and why does it matter?**
**Short:** Spring defaults to a single scheduling thread, so every `@Scheduled` method runs sequentially and one slow task delays the rest.

Spring's default scheduler uses a single thread (`ScheduledThreadPoolExecutor` with `corePoolSize=1`). This means all `@Scheduled` methods share one thread and execute sequentially. If one task is long-running or slow, it delays all other tasks. Configure a `ThreadPoolTaskScheduler` bean with `setPoolSize(N)` to allow N tasks to run concurrently. Choose N based on expected concurrent scheduled tasks — typically 5–20 for a service with a dozen scheduled jobs. Without explicit configuration, a slow nightly job can delay a critical 30-second health check.

**Q: What is `ApplicationReadyEvent` and why should it be preferred over `@PostConstruct` for post-startup work?**
**Short:** It fires only after the full context is refreshed and the server accepts requests, unlike `@PostConstruct` which runs mid-refresh.

`@PostConstruct` fires during the Spring context refresh, before the embedded server (Tomcat/Netty) starts accepting requests. Code in `@PostConstruct` that takes a long time delays server startup. More importantly, if `@PostConstruct` code makes HTTP calls or accesses beans that haven't been initialised yet, it fails silently. `ApplicationReadyEvent` fires after the full application context is refreshed, all beans are initialised, and the embedded server is accepting requests. It is the correct hook for: cache pre-loading, connectivity checks, background initialisation threads, or any work that should start after the application is genuinely "ready."

**Q: How would you test a `@TransactionalEventListener` to ensure it fires only on commit?**
**Short:** Spy on the real listener with `@MockitoSpyBean` and assert it ran after a real commit but never ran after a rollback.

Drive a real `@Transactional` service method in a `@SpringBootTest` and assert the listener ran after a successful commit and did not run after a rollback.
```java
@SpringBootTest
class OrderEventTest {
    @Autowired OrderService orderService;
    @MockitoSpyBean EmailNotificationListener emailListener;

    @Test
    void listenerFiresAfterCommit() {
        orderService.createOrder(testRequest());  // opens + commits TX

        // @TransactionalEventListener fires AFTER commit, so it has fired by now
        verify(emailListener).sendConfirmationEmail(any(OrderCreatedEvent.class));
    }

    @Test
    void listenerDoesNotFireOnRollback() {
        assertThrows(RuntimeException.class,
            () -> orderService.createOrderThatFails());

        verify(emailListener, never()).sendConfirmationEmail(any());
    }
}
```
Use `@MockitoSpyBean` (not `@MockitoBean`) to wrap the real listener in a Mockito spy — the spy delegates to the real instance, so the `@TransactionalEventListener` method is still registered and still executes, while `@MockitoBean` would replace it with a stub that does nothing and never proves the commit-phase wiring works. Call the real service method (not a test fixture) so the full transaction lifecycle fires.

**Q: Explain the `fixedRate` vs `fixedDelay` difference and when an overlap can occur.**
**Short:** `fixedDelay` waits N ms after the previous run finishes, while `fixedRate` targets N ms between start times and can overlap on a pooled scheduler.

`fixedDelay = N` starts the next run N milliseconds after the previous run completes — if a run takes 8 seconds and `fixedDelay=5000`, the next run starts at 8+5=13s after the first started. `fixedRate = N` tries to maintain N milliseconds between start times — if a run takes longer than N, the next run starts immediately after the previous finishes (no overlap, but the schedule slips). If the scheduler has multiple threads (via `ThreadPoolTaskScheduler`), `fixedRate` can actually run multiple instances simultaneously if a run takes longer than the rate. This can cause concurrent modification issues in stateful tasks. Use `fixedDelay` for tasks that must not overlap; use `fixedRate` with a pool size of 1 (default) if you want constant-rate scheduling with sequential execution.

**Q: How does Spring's `@Async` propagate security context to async methods?**
**Short:** Wrap the executor in `DelegatingSecurityContextAsyncTaskExecutor`, which copies the caller's `SecurityContext` onto the async thread.

`SecurityContextHolder` is `ThreadLocal`-based — the async thread has a fresh, empty security context by default. To propagate the caller's `Authentication`: configure a `DelegatingSecurityContextAsyncTaskExecutor` wrapping your task executor. Spring Security provides this out of the box:
```java
@Bean
public Executor asyncExecutor() {
    return new DelegatingSecurityContextAsyncTaskExecutor(
        new ThreadPoolTaskExecutor()  // your underlying executor
    );
}
```
The `DelegatingSecurityContextAsyncTaskExecutor` captures the `SecurityContext` at submission time and sets it in the async thread before running the task, then clears it afterwards. Without this, async methods calling `SecurityContextHolder.getContext()` see `AnonymousAuthenticationToken`.

**Q: What is the difference between publishing a Spring event and sending a message to Kafka/RabbitMQ?**
**Short:** A Spring event is in-process and lost on crash, while Kafka or RabbitMQ messages are durable and survive across process failure.

Spring application events are in-process, in-memory, synchronous (or async via `@Async`) — they live and die with the JVM. If the application crashes between publishing and a listener firing, the event is lost. Kafka/RabbitMQ messages are durable, cross-process, and replayed after failure. Use Spring events for: in-process decoupling, cache invalidation, audit logging, and side effects that do not need durability. Use Kafka/RabbitMQ for: cross-service communication, durable event sourcing, at-least-once delivery guarantees, and fan-out to multiple services. The outbox pattern bridges the gap: write the event to a DB table in the same transaction, then a relay process publishes it to Kafka — giving transactional guarantees + durability.

**Q: How would you make a `@Scheduled` task conditional (e.g., only run in production)?**
**Short:** Guard the scheduled bean with `@Profile` or `@ConditionalOnProperty` so it is never registered outside the intended environment.

Three approaches: (1) `@Profile("!test")` on the class — scheduling is not registered in the `test` profile; (2) `@ConditionalOnProperty(name = "tasks.enabled", havingValue = "true")` on the bean — disable via property; (3) `SchedulingConfigurer` to programmatically register tasks with conditions. The simplest production pattern is a `@ConditionalOnProperty` guard combined with `spring.tasks.enabled=false` in `application-test.yaml`. For feature-flag-style control at runtime, use `ScheduledFuture` + manual reschedule, though this adds complexity. ShedLock inherently makes tasks conditional on lock acquisition — which handles the multi-instance case.

**Q: What happens if a `@TransactionalEventListener` listener method opens a new transaction (`REQUIRES_NEW`) and that transaction fails?**
**Short:** The original transaction stays committed while only the new `REQUIRES_NEW` transaction rolls back, leaving a partial final state.

The new transaction's failure does not affect the original transaction — it has already committed. The new transaction rolls back atomically. The result is a partial state: the original data is committed, but the secondary update (e.g., analytics insert) is not. This is the fundamental tradeoff of `@TransactionalEventListener` — it provides post-commit fire guarantee but cannot participate in the original atomicity. For cases where both changes must succeed or fail together, use an explicit `@Transactional` service that performs both writes in one transaction, or use the outbox pattern + a reliable message relay to ensure the secondary effect eventually happens (at-least-once, with idempotent consumer).

**Q: How does `spring.threads.virtual.enabled=true` affect `@Async` and `@Scheduled` in Spring Boot 3.2?**
**Short:** Boot swaps in a virtual-thread `SimpleAsyncTaskExecutor` and `SimpleAsyncTaskScheduler`, so each `@Async` call and `@Scheduled` run gets its own virtual thread.

With `spring.threads.virtual.enabled=true` (Java 21+), the auto-configured `AsyncTaskExecutor` behind `@Async` is a `SimpleAsyncTaskExecutor` that starts one virtual thread per task, in place of the default `ThreadPoolTaskExecutor`. For `@Scheduled`, the scheduler is a `SimpleAsyncTaskScheduler` backed by virtual threads, and it ignores the `spring.task.scheduling.pool.*` properties. This means: (1) `@Async` has no pool queue; the executor is unbounded unless you set `spring.task.execution.simple.concurrency-limit` (unset by default, which means no limit), which is how you stop a burst from opening thousands of DB connections; (2) blocking I/O in async methods does not pin OS threads; (3) `@Scheduled` tasks no longer share a fixed thread pool; each scheduled run gets a virtual thread. Important: what still holds a carrier is a **native frame**: a scheduled task that blocks inside a JNI method or a Foreign Function & Memory downcall cannot unmount, and with the carrier pool sized to the CPU count a handful of such tasks starve everything else. Monitor with the `jdk.VirtualThreadPinned` JFR event, which is enabled by default at a 20 ms threshold and names the blocking operation and carrier thread. `synchronized` is not a pinning reason: JEP 491 (Java 24) removed monitor pinning, so a lock held across I/O now costs you serialisation, not a lost carrier.

**Q: Describe the outbox pattern and how it solves the dual-write problem for events.**
**Short:** Writing the event to an outbox table in the same DB transaction, then relaying it separately, makes the write and publish atomic.

The dual-write problem: after a DB write, publishing a Kafka message in a separate network call is non-atomic. If the app crashes between the commit and the publish, the event is lost. The outbox pattern solves this: (1) within the same DB transaction, write both the business entity and an `outbox_events` row; (2) a separate relay process (Debezium CDC, Quartz job, or `@Scheduled`) reads from `outbox_events` and publishes to Kafka; (3) on successful Kafka publish, delete or mark the outbox row as processed. Both writes are in one DB transaction — they succeed or fail atomically. The relay retries on failure — providing at-least-once delivery. Consumers must be idempotent (use event ID deduplication). Spring Batch's chunk-oriented model is well-suited for the relay: read pending outbox rows, publish, write completion — with restart-on-failure built in.

---

## 13. Best Practices

1. **Use `@TransactionalEventListener(AFTER_COMMIT)` for all post-commit side effects** — email, push, Kafka, audit — rather than in-transaction `@EventListener`.
2. **Add `fallbackExecution = true`** when the event can be published outside a transaction (test environments, scripts).
3. **Always configure `AsyncUncaughtExceptionHandler`** for `@Async` — never let async exceptions disappear silently.
4. **Replace the default single-thread scheduler** with a `ThreadPoolTaskScheduler` (pool size 5–20) for services with multiple `@Scheduled` methods.
5. **Use ShedLock** for all `@Scheduled` tasks in multi-instance deployments; set both `lockAtLeastFor` and `lockAtMostFor`.
6. **Prefer `ApplicationReadyEvent`** over `@PostConstruct` for post-startup cache loading or connectivity probes.
7. **Use `@Async` + virtual threads** (`spring.threads.virtual.enabled=true`) in Spring Boot 3.2+ — avoids thread pool sizing headaches for I/O-bound async work.
8. **Do not mix `@Async` with `@Transactional` without understanding isolation** — the async thread has no transaction context from the caller; open a new transaction explicitly if needed.
9. **Use `fixedDelay` for tasks that must not overlap**; only use `fixedRate` if overlap is acceptable or the pool size is 1.
10. **For exactly-once event delivery**, use the outbox pattern — Spring events + `@Async` are at-most-once and not suitable for critical financial or compliance events.
11. **Turn on `spring.task.execution.propagate-context=true`** (Boot 4.1) or register a `ContextPropagatingTaskDecorator`, so `@Async` log lines and outbound calls keep the request's trace id.

---

## 14. Case Study

**Scenario: the nightly payout job that ran twice during a rolling deploy**

See the full design in the Spring case study:
[Design Spring Boot Event-Driven Microservice](../case_studies/design_event_driven_microservice.md).
This section covers one incident from the scheduling half of that design.

**Context.** A marketplace settlement service pays out to 42,000 sellers. At 02:00 every
night, `@Scheduled(cron = "0 0 2 * * *")` selects the day's cleared transactions, writes a
NACHA payout file, and hands it to the bank's SFTP drop. The job runs for roughly 40
seconds. The service runs 6 pods on Kubernetes, and ShedLock guards the schedule:

```java
@Scheduled(cron = "0 0 2 * * *", zone = "Europe/London")
@SchedulerLock(name = "nightly-payout", lockAtMostFor = "PT10M")
public void generatePayouts() { ... }
```

That configuration ran without incident for nine months.

### The incident

A routine rolling deploy was scheduled through a change window that happened to overlap
02:00. Two pods of the new version came up while two pods of the old version were still
draining. The bank rejected the morning's submission for a duplicate batch id, and
reconciliation found **two payout files for the same date, 4.3 seconds apart, totalling
£1.87M of duplicate instructions**. A little over £200k of it had already settled before the
bank's own duplicate check stopped the rest.

```mermaid
sequenceDiagram
    autonumber
    participant P1 as Pod-1 (old, clock +0.0s)
    participant DB as shedlock table
    participant P4 as Pod-4 (new, clock -4.1s)
    P1->>DB: 02:00:00 UPDATE shedlock WHERE lock_until is in the past
    DB-->>P1: acquired, lock_until = 02:10:00
    Note over P1: generatePayouts() runs for 40s
    P1->>DB: 02:00:40 release, lock_until = now()
    Note over DB: lockAtLeastFor was never set, so the lock<br/>is freed the moment the task returns
    P4->>DB: 02:00:44 its own 02:00 tick fires late
    DB-->>P4: lock_until has passed, acquired
    Note over P4: generatePayouts() runs a SECOND time<br/>same cleared transactions, new batch id
```

**Root cause, in two parts.**

1. **`lockAtLeastFor` was never set**, so it defaulted to zero and the lock was released as
   soon as the task returned — 40 seconds after it was taken, not 10 minutes.
2. **The pods' clocks disagreed.** Pod-4 had just started on a node whose NTP sync was
   4.1 seconds behind. Its scheduler fired its own `02:00:00` tick at what Pod-1 already
   considered `02:00:44`. By then the lock row said the window was over.

Neither fault is sufficient alone. With a correct `lockAtLeastFor`, the skewed clock is
harmless. With perfectly synchronised clocks, the missing `lockAtLeastFor` is harmless.
The rolling deploy supplied the second fault by introducing a freshly booted node — which
is exactly why the schedule survived nine months of steady state and failed on the first
deploy that touched 02:00.

`lockAtMostFor = "PT10M"` did nothing here. It is the crash valve — the upper bound after
which a dead lock-holder stops blocking everyone — not the lower bound that defends
against skew. Reading it as "the lock is held for 10 minutes" is the trap.

### The fix

```java
@Scheduled(cron = "0 0 2 * * *", zone = "Europe/London")
@SchedulerLock(
        name         = "nightly-payout",
        lockAtMostFor  = "PT10M",   // upper bound: a dead pod frees the lock after 10 min
        lockAtLeastFor = "PT5M")    // lower bound: nobody else may run within 5 min
public void generatePayouts() { ... }
```

```java
@Bean
LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .withTableName("shedlock")
            .usingDbTime()          // every lock comparison uses the DATABASE clock
            .build());
}
```

`usingDbTime()` is the structural half of the fix and the more important one.
Without it, each pod writes `lock_until` from its own `System.currentTimeMillis()`, so the
lock's validity is only as good as the worst clock in the cluster. With it, every
comparison and every timestamp comes from a single PostgreSQL `now()`, and node clock skew
stops being able to affect correctness at all. `lockAtLeastFor` then covers the remaining
case — two pods whose *scheduler triggers* fire at different wall-clock moments.

Sizing rule the team adopted: **`lockAtLeastFor` ≥ the worst tolerated clock skew, and
`lockAtMostFor` ≥ 2 × the p99 task duration.** Here: 5 minutes and 10 minutes against a
40-second job.

The change-window policy changed too — deploys are blocked for 15 minutes either side of
any cron in the service. A distributed lock makes double-firing unlikely; it does not make
it impossible, and a rolling deploy is the moment when the assumptions behind it are
weakest.

### Why ShedLock and not Quartz or an advisory lock

The post-incident review asked whether the whole mechanism was wrong. It was not, but the
alternatives are worth knowing, because each fails differently:

| | ShedLock + `@Scheduled` | Quartz clustered | Postgres advisory lock |
|---|---|---|---|
| Who owns the trigger | Every pod's own scheduler | The cluster, via the JDBC job store | Every pod's own scheduler |
| Extra tables | 1 (`shedlock`) | 11 (`QRTZ_*`) | 0 |
| Missed-fire recovery | None — a skipped tick is simply skipped | Misfire policies, replays a missed trigger | None |
| Dynamic schedule changes | Redeploy | At runtime, through the API | Redeploy |
| Failure mode to fear | Clock skew and a too-short lock (this incident) | Misfire storms after a long GC or a cluster-wide pause | The lock dying with the connection mid-task |
| Setup cost | ~15 lines | A schema, a config file, and an operational model | ~5 lines |

The advisory-lock option is the tempting one — `SELECT pg_try_advisory_lock(74112)` needs
no library and no table. Its catch is that a session-scoped advisory lock is released when
the connection is returned to the pool or the connection drops, which for a 40-second job
holding a pooled connection is a genuinely narrow guarantee. `pg_advisory_xact_lock` is
safer but ties the lock's lifetime to a transaction you probably do not want open for the
whole job.

Quartz was rejected on cost, not capability: 11 tables and a misfire model to operate, to
solve a problem that two annotation attributes solve. The team's rule of thumb — **if the
schedule is static and a missed run is recoverable on the next tick, ShedLock is enough;
if a missed run must be replayed, you need a real scheduler.**

**Cross-links:**
- [Spring Transactions](../spring_transactions/spring_transactions.md) — `@TransactionalEventListener` transaction phases
- [Spring Messaging](../spring_messaging/spring_messaging.md) — Kafka integration + outbox pattern
- [Spring Batch](../spring_batch/spring_batch.md) — restartability when a scheduled job is a batch job
- [Concurrency Control & Locking](../../database/concurrency_control_and_locking/concurrency_control_and_locking.md) — advisory locks and lease semantics underneath ShedLock

---

## Related / See Also

- [Spring Messaging](../spring_messaging/spring_messaging.md) — async event vs Kafka
- [Spring Transactions](../spring_transactions/spring_transactions.md) — @TransactionalEventListener
- [Spring Batch](../spring_batch/spring_batch.md) — scheduling batch jobs
- [LLD: Observer Pattern](../../lld/behavioral/observer/observer.md) — the GoF pattern that `ApplicationEvent`/`ApplicationListener` implements
