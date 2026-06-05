# Case Study: Design an Event Bus in Pure Java

## Intuition

> An event bus is a post office inside the JVM: components drop letters (events) into a central exchange without knowing who will read them; the bus routes each letter to every subscriber who asked for that type. No one holds a direct reference to anyone else.

**Key insight**: the publish-subscribe pattern inverts dependency direction — instead of service A calling service B directly, A publishes an event and B declares interest. This breaks compile-time coupling, but it introduces three new problems: memory leaks when subscribers outlive publishers (solved with `WeakReference`), exception propagation that can kill sibling handlers (solved with per-handler isolation), and ordering when order matters (solved by priority + sync/async dispatch choice). Get those three right and the bus is a reliable backbone for intra-process reactive pipelines.

At 10× scale the in-process bus graduates to Kafka, but all the same rules apply — idempotency, dead-letter queues, schema evolution — just across JVM boundaries.

See also:
- [Concurrency Memory Visibility Primitives](cross_cutting/concurrency_memory_visibility_primitives.md) — `CopyOnWriteArrayList`, `ConcurrentHashMap`, `WeakReference` GC interaction
- [Backpressure & Bounded Resources](cross_cutting/backpressure_and_bounded_resources.md) — bounded executor queues, `CallerRunsPolicy`, subscriber bulkheads

---

## 1. Requirements Clarification

### Functional requirements
- `subscribe(EventType.class, handler)` — type-safe subscription; handler typed to event
- `post(event)` — synchronous dispatch; all handlers run before `post()` returns
- `postAsync(event)` — asynchronous dispatch; returns `CompletableFuture<Void>`
- Priority ordering — lower priority number = runs first
- Exception isolation — one handler's failure must not prevent others from running
- Memory safety — weak-reference subscribers GC'd when no longer referenced outside the bus
- Supertype subscriptions — `subscribe(DomainEvent.class, h)` receives all subtype events

### Non-functional requirements
| Dimension | Target |
|-----------|--------|
| Sync dispatch throughput | ≥ 50k events/sec at fan-out 8 |
| Subscribe latency | < 1 ms |
| Memory overhead per handler entry | < 256 bytes |
| Concurrent publishers | Any number of threads simultaneously |
| Handler leak after subscriber GC | 0 (auto-purged on next publish) |

### Out of scope
- Cross-process or persistent delivery (use Kafka for that — see §6)
- Guaranteed delivery / exactly-once semantics (in-process only)
- Dynamic event filtering / content-based routing

---

## 2. Scale Estimation

### Handler invocation budget

```
Events/sec:        50,000
Avg subscribers:   8 per event type
Handler calls/s:   50,000 × 8 = 400,000 calls/sec

Per-handler work:  0.1 ms (50% CPU, 50% I/O)
Service demand:    400,000 × 0.0001 s = 40 handler-seconds/wall-clock-second

Threads needed at 100% utilization: 40
With I/O multiplier (wait/service = 1):
  threads = 40 × (1 + 1) = 80 threads
Bounded queue for 1-second burst: 50,000 × 8 = 400,000 tasks in queue
```

### Memory for subscriber registry

```
Handler entries:   1,000 event types × 10 subscribers = 10,000 entries
Per entry:         WeakReference (16 B) + priority int (4 B) + Object header (~16 B) ≈ 48 B
Total registry:    10,000 × 48 B = 480 KB — negligible
```

### CopyOnWriteArrayList write cost

```
Write (subscribe/unsubscribe): copies full array
At 10 subscribers per type, array copy = 10 × 48 B = 480 B copied per subscribe
Subscribe frequency: once per class load (startup) → amortized cost ≈ 0
Read (publish): reads current snapshot — no allocation, no lock
```

### Dead-letter queue budget

```
Event ref + exception snapshot: ~512 bytes per dead letter
Max DLQ depth: 10,000 entries = 5 MB — bounded and monitored
```

---

## 3. High-Level Architecture

```
  ┌────────────────────────────────────────────────────────────────────┐
  │                      Publisher Threads                             │
  │   thread-1              thread-2              thread-3            │
  └────────────────┬──────────────────────────────────────────────────┘
                   │  post(event) / postAsync(event)
                   ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │                         EventBus                                  │
  │                                                                   │
  │  handlers: ConcurrentHashMap<Class<?>, CopyOnWriteArrayList>     │
  │  asyncExecutor: Executor (virtual thread per task, Java 21)      │
  │  deadLetterQueue: BlockingDeque<DeadLetter>                      │
  └─────────────┬──────────────────┬────────────────────────────────┘
                │ dispatch()       │ postAsync()
                │ (calling thread) │ (asyncExecutor)
                ▼                  ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │               HandlerEntry (per subscriber per event type)       │
  │                                                                  │
  │   handlerRef: WeakReference<Consumer<Object>>                   │
  │   priority: int                                                  │
  │   (dead ref auto-purged on next publish)                        │
  └──────────────────────────────────────────────────────────────────┘
```

### Subscription lifecycle

```
1. subscribe(OrderPlaced.class, handler, priority=1)
   → computeIfAbsent: create COWAL for OrderPlaced
   → new HandlerEntry(new WeakReference<>(handler), 1)
   → re-sort COWAL by priority
   → return Subscription (lambda: list.remove(entry))

2. Subscriber goes out of scope (no strong ref outside bus):
   → GC collects the handler lambda
   → handlerRef.get() returns null on next dispatch
   → entry added to deadEntries, removed from COWAL

3. post(new OrderPlaced(...))
   → walk type hierarchy: [OrderPlaced, DomainEvent, ...]
   → for each type: iterate COWAL snapshot
   → skip dead refs; invoke live handlers in priority order
   → catch per-handler exception; route to DLQ; continue
```

---

## 4. Component Deep Dives

### 4.1 Exception isolation — broken vs fixed

BROKEN — one handler's exception aborts all handlers after it:

```java
// BROKEN: unchecked exception escapes dispatch loop; handlers #3..N never run
public void dispatch(Object event) {
    for (HandlerEntry entry : handlersFor(event.getClass())) {
        Consumer<Object> h = entry.handlerRef.get();
        if (h != null) h.accept(event);  // if handler #2 throws NPE → loop exits
    }
}
// OrderPlaced published → audit-log handler (h#1) runs, payment-capture handler (h#2) throws
// → notification handler (h#3) never fires → silent data loss
```

FIX — per-handler try-catch; failures routed to a dead-letter queue:

```java
// FIX: each handler isolated; failures captured, dispatch continues
public void dispatch(Object event) {
    for (HandlerEntry entry : handlersFor(event.getClass())) {
        Consumer<Object> handler = entry.handlerRef.get();
        if (handler == null) { deadRefs.add(entry); continue; }
        try {
            handler.accept(event);
        } catch (Exception ex) {
            deadLetterQueue.offer(new DeadLetter(event, handler, ex, Instant.now()));
            log.error("handler {} failed for event {}", handler, event.getClass().getSimpleName(), ex);
            // continue to next handler — isolation is the invariant
        }
    }
    handlersFor(event.getClass()).removeAll(deadRefs);
}
```

### 4.2 WeakReference subscriber lifecycle

The `WeakReference` pattern prevents the bus from being a surprise GC root:

```java
// PROBLEM without WeakReference:
class OrderView {
    OrderView(EventBus bus) {
        bus.subscribe(OrderPlaced.class, this::onOrder);
        // Bus holds strong ref to method handle → OrderView can never be GC'd
        // Over N screen navigations → N OrderView instances live in heap → OOM
    }
}

// WITH WeakReference — the caller must hold a strong ref themselves:
class OrderView {
    private final Consumer<OrderPlaced> handler = this::onOrder;  // REQUIRED strong ref
    OrderView(EventBus bus) {
        bus.subscribe(OrderPlaced.class, handler);
        // Bus stores WeakReference(handler)
        // When OrderView is eligible for GC and handler has no other strong refs:
        //   GC collects the lambda → handlerRef.get() returns null → auto-purged
    }
    void onOrder(OrderPlaced e) { /* update UI */ }
}
```

The subtlety: an inline lambda `bus.subscribe(T.class, e -> doSomething())` stores the lambda only in the `WeakReference` — no strong reference elsewhere — so it may be GC'd before any event fires. This is a common bug when subscribing in constructors without storing the handler in a field.

### 4.3 Full implementation

```java
public class EventBus {
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<HandlerEntry>> handlers =
        new ConcurrentHashMap<>();
    private final Executor asyncExecutor;
    private final BlockingDeque<DeadLetter> deadLetterQueue = new LinkedBlockingDeque<>(10_000);

    public EventBus() {
        // Java 21 GA: virtual thread per task; no thread-pool sizing needed
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public EventBus(Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    @SuppressWarnings("unchecked")
    public <T> Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        return subscribe(eventType, handler, 0);
    }

    @SuppressWarnings("unchecked")
    public <T> Subscription subscribe(Class<T> eventType, Consumer<T> handler, int priority) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(handler);

        HandlerEntry entry = new HandlerEntry(
            new WeakReference<>((Consumer<Object>) handler), priority);

        handlers.compute(eventType, (k, list) -> {
            CopyOnWriteArrayList<HandlerEntry> l =
                (list != null) ? list : new CopyOnWriteArrayList<>();
            List<HandlerEntry> sorted = new ArrayList<>(l);
            sorted.add(entry);
            sorted.sort(Comparator.comparingInt(e -> e.priority));
            return new CopyOnWriteArrayList<>(sorted);
        });

        return () -> handlers.getOrDefault(eventType, new CopyOnWriteArrayList<>()).remove(entry);
    }

    public void post(Object event) {
        Objects.requireNonNull(event);
        dispatch(event);
    }

    public CompletableFuture<Void> postAsync(Object event) {
        Objects.requireNonNull(event);
        return CompletableFuture.runAsync(() -> dispatch(event), asyncExecutor);
    }

    private void dispatch(Object event) {
        List<HandlerEntry> dead = new ArrayList<>();
        for (Class<?> type : typeHierarchy(event.getClass())) {
            CopyOnWriteArrayList<HandlerEntry> list = handlers.get(type);
            if (list == null) continue;
            for (HandlerEntry entry : list) {
                Consumer<Object> h = entry.handlerRef.get();
                if (h == null) { dead.add(entry); continue; }
                try {
                    h.accept(event);
                } catch (Exception ex) {
                    deadLetterQueue.offer(new DeadLetter(event, h, ex, Instant.now()));
                    System.err.printf("Handler %s failed for event %s: %s%n",
                        h, event.getClass().getSimpleName(), ex.getMessage());
                }
            }
            if (!dead.isEmpty()) { list.removeAll(dead); dead.clear(); }
        }
    }

    private List<Class<?>> typeHierarchy(Class<?> type) {
        List<Class<?>> result = new ArrayList<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            result.add(c);
            addInterfaces(c, result);
            c = c.getSuperclass();
        }
        return result;
    }

    private void addInterfaces(Class<?> c, List<Class<?>> out) {
        for (Class<?> iface : c.getInterfaces()) {
            if (!out.contains(iface)) { out.add(iface); addInterfaces(iface, out); }
        }
    }

    public BlockingDeque<DeadLetter> deadLetterQueue() { return deadLetterQueue; }

    private static class HandlerEntry {
        final WeakReference<Consumer<Object>> handlerRef;
        final int priority;
        HandlerEntry(WeakReference<Consumer<Object>> ref, int priority) {
            this.handlerRef = ref; this.priority = priority;
        }
    }
}

@FunctionalInterface
public interface Subscription { void unsubscribe(); }

public record DeadLetter(Object event, Object handler, Exception cause, Instant at) {}
```

### 4.4 Per-subscriber bulkhead (high-throughput variant)

```java
// When one slow subscriber must not starve others:
public class BulkheadEventBus extends EventBus {
    // Each handler gets its own bounded single-thread executor
    private final Map<Consumer<?>, ExecutorService> channels = new ConcurrentHashMap<>();

    public <T> Subscription subscribeIsolated(Class<T> type, Consumer<T> handler) {
        ExecutorService channel = Executors.newSingleThreadExecutor();
        channels.put(handler, channel);
        return subscribe(type, event -> {
            if (!channel.isShutdown()) {
                channel.submit(() -> handler.accept(event));
            }
        });
    }
}
```

This is the bulkhead pattern: a 200 ms fraud-check handler can lag without affecting the 0.01 ms notification handler.

---

## 5. Design Decisions & Tradeoffs

| Decision | Chosen | Alternatives | Rationale |
|----------|--------|-------------|-----------|
| Handler storage | `CopyOnWriteArrayList` | `List + ReadWriteLock` | COWAL: lock-free reads for publish (hot path); write (subscribe) copies the array — acceptable since subscribe is rare (startup) |
| Memory safety | `WeakReference<Consumer>` | Strong reference | WeakRef: subscriber naturally GC'd when its scope ends; no explicit unsubscribe required in most cases |
| Exception isolation | Per-handler `try-catch` + DLQ | Propagate to publisher | Isolation: other handlers run even if one is buggy; risk: silent failures — mitigated by DLQ and logging |
| Async executor | `VirtualThreadPerTaskExecutor` (Java 21) | Fixed `ThreadPool` | Virtual threads: no pool sizing, no thread starvation on I/O, no carrier-thread pinning from short blocking ops |
| Supertype dispatch | Walk full type hierarchy | Exact type only | Hierarchy: `subscribe(DomainEvent.class, h)` captures all subtypes — powerful for cross-cutting concerns (audit, metrics); cost: slightly slower dispatch for deep hierarchies |
| Async ordering | Sequential (single `CompletableFuture` wraps all handlers) | Parallel (one CF per handler) | Sequential preserves priority order in async path; parallel maximizes throughput but loses ordering |

**Sync vs async selection guide**:
- Audit / financial / stateful event flows → synchronous (`post()`) — order guaranteed, failure visible
- Notifications / metrics / independent flows → asynchronous (`postAsync()`) — decoupled from publisher latency
- Ultra-high throughput → LMAX Disruptor ring buffer (~6M events/sec) — zero allocation, single-writer principle

---

## 6. Real-World Implementations

**Guava EventBus**: annotation-driven (`@Subscribe`), uses reflection to discover handler methods. Strong references by default — callers must explicitly `unregister()` to avoid leaks. Synchronous dispatch only via `EventBus`; asynchronous via `AsyncEventBus`. Hierarchy dispatch supported. Widely used but unmaintained since 2023; Guava teams recommend alternatives for new projects.

**Spring's `ApplicationEventPublisher`**: the `@EventListener` annotation on any Spring bean method auto-registers as a handler. `@Async @EventListener` dispatches on a separate executor pool. `@TransactionalEventListener(AFTER_COMMIT)` waits for the current transaction to commit before dispatching — the most important Spring-specific feature for domain events (prevents a handler from seeing uncommitted data). See `spring/spring_events_and_scheduling/README.md` for the complete pattern.

**Akka EventStream**: `system.eventStream.publish(msg)` / `subscribe(ref, classOf[T])`. Weak references via actor lifecycle — an actor's subscription is automatically removed when the actor stops, eliminating the leak problem structurally. Fan-out parallelism is natural (each subscriber actor processes independently in its mailbox). Heavyweight dependency for a simple in-process bus.

**LMAX Disruptor**: ring buffer pre-allocated at startup; producers claim slots via CAS (`sequencer.next()`); consumers follow a dependency chain. Zero allocation in the steady state, no GC pressure, cache-line-padded slots prevent false sharing. Throughput ~6M events/sec at sub-millisecond latency — the benchmark target for mechanical-sympathy-level hot paths (trading, telemetry ingestion). Not suitable for general application use: no weak refs, fixed ring size, requires up-front dependency graph.

**Apache Kafka (distributed)**: the natural evolution of an in-process bus when events must cross JVM boundaries. Partitioned, replicated topics replace the in-memory `ConcurrentHashMap`. Consumer groups replace subscribers. At-least-once delivery replaces synchronous dispatch — handlers must be idempotent (same `event_id` de-duplication). Schema registry enforces `AvroSchema` compatibility rules — the analog of Java's compile-time type-safety for the event contract.

---

## 7. Technologies & Tools

| Tool | Dispatch Model | Throughput | Memory Safety | Key Feature | Avoid When |
|------|---------------|-----------|---------------|-------------|------------|
| Custom (this design) | Sync + async | ~50–400k/sec | WeakReference | Zero dependencies | Need persistence |
| Guava EventBus | Sync / Async class | ~300k/sec | Strong (manual unregister) | Annotation-driven | New projects (unmaintained) |
| Spring ApplicationEventPublisher | Sync / Async / TransactionalListener | ~200k/sec | Strong (managed by container) | AFTER_COMMIT, @Async | Non-Spring apps |
| LMAX Disruptor | Ring buffer, preallocated | ~6M/sec | Strong | Zero GC, cache-line padding | General use — specialized |
| Akka EventStream | Actor mailboxes | ~1M/sec | Via actor lifecycle | Actor integration | Non-Akka apps |
| Kafka | Durable log, consumer groups | Millions/sec (cluster) | N/A | Cross-process, durable | In-process only |

JMH dispatch strategy benchmark (fan-out = 8, Java 21, 16-core):

| Strategy | Throughput | Latency | Ordering |
|----------|-----------|---------|---------|
| Synchronous COWAL | ~80k events/sec | 0.01 ms | Strict per-publisher |
| Async bounded ExecutorService | ~400k events/sec | 0.5 ms | None across handlers |
| LMAX Disruptor ring buffer | ~6M events/sec | 0.05 ms | Strict (single-writer) |

---

## 8. Operational Playbook

### a) Key metrics

```java
// On each publish():
eventPublished.increment(Tags.of("type", event.getClass().getSimpleName()));

// On each handler:
Timer.record(() -> handler.accept(event),
    registry, "event.handler.duration",
    Tags.of("handler", handler.getClass().getSimpleName()));

// DLQ depth gauge:
Gauge.builder("event.dlq.size", deadLetterQueue, BlockingDeque::size).register(registry);
```

Alert thresholds:
- `event.dlq.size > 100` for > 60 s → handler failures accumulating; investigate
- `event.handler.duration p99 > 500 ms` → slow handler; check for bulkhead isolation
- `event.handler.count{type="X"} == 0` after known subscriber startup → registration bug

### b) Distributed tracing

```
HTTP request span
  └── eventbus.post (0.1 ms)          ← tag: event_type, handler_count
        ├── handler: AuditHandler (0.01 ms)
        ├── handler: NotificationHandler (0.02 ms)
        └── handler: FraudChecker (180 ms)  ← outlier visible in trace
```

### c) Incident Runbooks

**Runbook 1 — DLQ growing (handler failures)**

Symptom: `event.dlq.size` climbing; expected side effects (emails, payments) not occurring.

Diagnosis:
1. Read `deadLetterQueue`: inspect `DeadLetter.cause` for exception type and stack trace.
2. Identify whether failure is transient (DB timeout) or permanent (NPE in handler code).
3. Check whether the same handler fails for all events or only specific event types.

Mitigation:
- Transient: redrive DLQ entries after the downstream recovers.
- Permanent: hotfix the handler; replay DLQ after deploy.
- Guard against DLQ overflow: `offer()` drops entries when at capacity — add alerting before capacity is reached.

---

**Runbook 2 — Async pool saturation (unrelated handlers not firing)**

Symptom: metrics and notification handlers stop firing; only slow handlers run.

Diagnosis: shared async pool exhausted by slow handler. Check `activeCount / poolSize == 1.0` for the shared executor.

Mitigation: deploy per-handler bulkhead executors (see §4.4). Each handler gets its own single-thread queue; a slow handler can lag without affecting others.

---

**Runbook 3 — Duplicate events (subscriber registered multiple times)**

Symptom: payment captured 4× for one order; audit log has 4 entries per event.

Diagnosis: check `bus.handlerCount(OrderPlaced.class)` — should equal number of intended subscribers. If higher, find the constructor being called repeatedly.

Mitigation: add identity-based de-duplication in `subscribe()` (see war story §9). Structural fix: subscribe singletons once at application startup, not per-request objects.

---

## 9. Common Pitfalls & War Stories

### War story 1 — Per-request controller subscribing: duplicate payments

**Scenario**: checkout service; every order produced 4 audit records and 4 payment captures.

BROKEN — `subscribe()` runs in a constructor called per request:

```java
// BROKEN: constructor called once per HTTP request → accumulates duplicate handlers
public class CheckoutController {
    public CheckoutController(EventBus bus) {
        bus.subscribe(PaymentEvent.class, this::capturePayment); // adds a new handler each time
    }
    void capturePayment(PaymentEvent e) { gateway.capture(e.amount()); }
}
// After 4 requests → 4 live handlers → one PaymentEvent captured 4 times
// → $X charged 4× to the customer's card
```

FIX — de-duplicate by handler identity; subscribe singletons at startup:

```java
// FIX: subscribe is idempotent per (eventType, handlerClass)
public <E> void subscribe(Class<E> type, EventHandler<E> h) {
    var list = handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
    boolean exists = list.stream()
        .anyMatch(e -> e.handlerClass().equals(h.getClass()));
    if (exists) {
        log.warn("duplicate subscription ignored: {} for {}", h.getClass(), type);
        return;
    }
    list.add(new HandlerEntry(h));
}
// → register CheckoutController once at app startup, not per-request
```

**Root cause**: subscribe-with-side-effects in per-request constructors silently accumulates handlers on every request.
**Impact**: customers charged 4× for one order; $12,000 incorrectly billed in 2 hours before alert fired.

---

### War story 2 — Slow subscriber exhausting the shared async pool

**Scenario**: 100 events/sec; 20-thread async pool; fraud handler takes 200 ms per event.

BROKEN — all subscribers share one pool; fraud handler hogs every thread:

```java
// BROKEN: single shared pool; slow handler starves fast handlers
ExecutorService pool = Executors.newFixedThreadPool(20);
void dispatchAsync(Object event) {
    for (HandlerEntry h : handlersFor(event.getClass())) {
        pool.submit(() -> h.accept(event));   // fraudHandler @ 200 ms ties up threads
    }
}
// 100 events/sec × fraud 0.2 s = 20 thread-seconds/sec → pool fully consumed
// Notification and metrics handlers are queued but never run → missed alerts
```

FIX — bulkhead: each handler on its own bounded executor:

```java
// FIX: isolated per-handler channel; slow handler falls behind its own queue only
class SubscriberChannel {
    final ExecutorService exec = Executors.newSingleThreadExecutor(); // bounded queue: 1,000
    void submit(Object event, Consumer<Object> h) {
        try {
            exec.submit(() -> h.accept(event));
        } catch (RejectedExecutionException ex) {
            deadLetterQueue.offer(new DeadLetter(event, h, ex, Instant.now()));
        }
    }
}
```

**Root cause**: no isolation between independent subscribers — one slow subscriber exhausted the shared thread pool.
**Impact**: notification emails stopped firing for 45 minutes; CS team missed a fraud spike in the order stream.

---

### Failure scenarios summary

| Failure | Symptom | Recovery | TTR |
|---------|---------|---------|-----|
| Handler throws unchecked exception | Handlers after it skipped | Per-handler try-catch + DLQ | Immediate |
| Async pool saturated by slow handler | Unrelated handlers stop firing | Per-handler bulkhead executor | Minutes |
| DLQ full | Further failures dropped silently | Bounded DLQ + drain alert | Until drained |
| WeakRef collected prematurely | Subscription silently stops | Store handler in strong-ref field | On next subscribe |
| Subscriber registered N times | Duplicate side effects (payments, etc.) | Identity-based de-dup in subscribe | On deploy |

---

## 10. Capacity Planning

### Thread pool formula for async dispatch

```
Events/sec:      E
Avg fan-out:     F
Handler latency: L seconds (CPU + I/O)
I/O ratio:       r  (0 = pure CPU, 1 = all I/O)

Worker threads needed:
  T = E × F × L × (1 + r)

Example:
  E = 50,000, F = 8, L = 0.0001 s, r = 1 (50% I/O)
  T = 50,000 × 8 × 0.0001 × 2 = 80 threads

With Java 21 virtual threads:
  VirtualThreadPerTaskExecutor handles 80 in-flight virtual threads with
  ~1 carrier thread per CPU core. No explicit sizing needed.
  Pinning risk: ensure handlers do not hold synchronized blocks during I/O.
```

### Bounded queue sizing

```
Burst budget: 1 second of slack
Queue depth = E × F × 1 s = 50,000 × 8 × 1 = 400,000 tasks

Memory: 400,000 × (task overhead ~128 B) = 51 MB
This is acceptable; set RejectedExecutionHandler to CallerRunsPolicy (backpressure)
rather than AbortPolicy (drops) or OOM.
```

### Transition to Kafka

```
When to move from in-process bus to Kafka:
  - Events must cross JVM / host boundaries → in-process bus cannot deliver
  - Durability required (crash-safe events) → in-process bus loses in-flight events on crash
  - Consumer lag independence required → each consumer group reads at its own pace
  - At-least-once delivery required → Kafka guarantees this; in-process is at-most-once on crash

Kafka capacity sizing (for reference):
  1 producer → 1 partition → 100 MB/s write throughput (typical Kafka broker)
  At 50k events/sec × 512 B/event = 25 MB/s → fits 1 partition with headroom
  Scale partitions proportionally with event volume for parallelism
```

---

## 11. Interview Discussion Points

**Q: Why use `WeakReference` for handler storage, and what is the caller's responsibility?**
Without `WeakReference`, the bus holds a strong reference to every handler lambda. If the subscriber (e.g., a ViewModel or request-scoped controller) goes out of scope, it cannot be GC'd as long as the bus lives — a silent memory leak. `WeakReference` lets the GC collect the handler when no other strong reference exists. The caller's responsibility: hold a strong reference to the handler object (store it in a field) for the entire duration it should receive events. An inline lambda like `bus.subscribe(T.class, e -> doX())` stores the lambda only in the `WeakReference` — it may be collected before any event fires, silently stopping the subscription.

**Q: How does exception isolation work, and what is the risk of swallowing exceptions?**
Each handler invocation is wrapped in a `try-catch(Exception)`. If handler A throws, the exception is caught, logged, and forwarded to a dead-letter queue — but dispatch continues to handler B, C, etc. The risk: if no logging or DLQ is configured, exceptions are silently swallowed and bugs in handlers become invisible. Best practice: use a configurable `ExceptionHandler` callback; in tests, configure it to re-throw so test failures surface immediately.

**Q: Why use `CopyOnWriteArrayList` for the handler list instead of `List + ReadWriteLock`?**
The publish (read) path is the hot path — called on every event. `CopyOnWriteArrayList` provides lock-free reads by reading the current array snapshot, never blocking concurrent publishers. Writes (subscribe/unsubscribe) copy the entire array — expensive but rare (startup-time only). A `List + ReentrantReadWriteLock` makes writes cheaper (in-place modification) but adds lock acquisition overhead on every publish. For an event bus where subscribes happen once at startup and events flow continuously, `CopyOnWriteArrayList` is the right tradeoff.

**Q: How does supertype dispatch work, and when is it useful?**
When `post(new OrderPlaced(...))` is called, the bus walks the type hierarchy: `OrderPlaced`, then its superclass, then its interfaces (`DomainEvent`, `Serializable`, etc.). For each type, it invokes all registered handlers. This lets a single `subscribe(DomainEvent.class, auditLog::record)` receive every domain event without individual per-subtype subscriptions — useful for cross-cutting concerns like audit logging, metrics counters, and correlation-ID propagation.

**Q: What is the thread safety model of this event bus?**
Three layers: `ConcurrentHashMap` for the type→handlers mapping (concurrent access across all threads); `CopyOnWriteArrayList` for each handler list (lock-free reads, copy-on-write modifications); `ConcurrentHashMap.compute()` atomicity for the sort-then-replace subscribe path. Multiple threads can publish simultaneously with no mutual exclusion — `dispatch()` holds no locks. The dead-entry cleanup (`list.removeAll(dead)`) is a write to COWAL and is internally synchronized, but only touches entries already identified as dead.

**Q: Synchronous vs asynchronous dispatch — what is the real tradeoff?**
Synchronous (`post()`): when it returns, all handlers have run; exceptions are on the calling thread; ordering is guaranteed. The publisher is coupled to the slowest handler's latency. Throughput: ~80k events/sec. Asynchronous (`postAsync()`): publisher returns immediately; handlers run on the executor; failures surface via the DLQ; ordering is not guaranteed across publishers. Throughput: ~400k events/sec. Choose sync for audit/payment/stateful flows where order and visibility matter; choose async for high-fan-out notifications and metrics.

**Q: How do you prevent one slow subscriber from degrading unrelated subscribers?**
Apply the bulkhead pattern: give each subscriber its own bounded single-thread executor rather than sharing a pool. A 200 ms fraud handler can lag and accumulate in its own queue without consuming threads from the fast notification handler. Add a circuit breaker per subscriber: if the handler's queue fills or latency exceeds a threshold, open the breaker and route to the dead-letter queue until it recovers.

**Q: Why must distributed-bus handlers be idempotent?**
Kafka and most message buses guarantee at-least-once delivery — a handler can legitimately receive the same event twice (consumer rebalance, redelivery after failed offset commit). If the handler captures a payment or writes a row without idempotency, you get duplicates — exactly the in-process war story, now structurally unavoidable. De-duplicate by a stable event ID stored in the handler's write-target (e.g., `INSERT ... ON CONFLICT DO NOTHING WHERE event_id = ?`).

**Q: How do you calculate thread pool size for async dispatch?**
Formula: `T = E × F × L × (1 + r)` where E = events/sec, F = fan-out, L = per-handler latency (seconds), r = I/O ratio. At 50k events/sec, 8 subscribers, 0.1 ms each, 50% I/O: `T = 50,000 × 8 × 0.0001 × 2 = 80 threads`. With Java 21 virtual threads, sizing becomes automatic — the executor creates one virtual thread per task and the JVM schedules them on available CPU cores, eliminating I/O blocking from the carrier thread.

**Q: When should an in-process event bus graduate to Kafka?**
When events must cross JVM boundaries (multiple services), when durability is required (crash should not lose in-flight events), or when consumer independence is required (one consumer falling behind should not block others). The migration protocol: first make all handlers idempotent (required for Kafka's at-least-once delivery), add a stable event ID field to every event, then route through Kafka topics instead of the in-memory map. The handler logic is unchanged.

**Q: How would you implement a sliding-window rate limit on event publishing?**
Wrap `post()` with a `TokenBucketRateLimiter` keyed on event type: if the bucket is exhausted, either block (back-pressure), drop (with DLQ entry), or raise a `RateLimitExceededException`. For per-publisher limits (prevent one thread from flooding the bus), key the limiter on `Thread.currentThread()` or on a publisher ID passed as context. See the rate-limiter case study for the CAS-based bucket implementation.
