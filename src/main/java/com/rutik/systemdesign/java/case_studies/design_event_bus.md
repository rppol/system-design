# Case Study: Design an Event Bus in Pure Java

## Problem Statement

Design an in-process event bus with:
- **Type-safe subscriptions**: `subscribe(OrderPlaced.class, handler)` — handlers typed to event
- **Synchronous and asynchronous publishing** options
- **Weak references** for subscribers — avoids memory leaks when subscriber is GC'd
- **Exception isolation** — one handler's exception doesn't affect others
- **Priority ordering** for handlers
- **Thread-safe** for concurrent publish from multiple threads

**Constraints**: Pure Java, no Spring/Guava. Production-ready.

---

## Key Java Concepts Used

| Concept | Module | Why Used |
|---------|--------|---------|
| `ConcurrentHashMap` | [Collections Internals](../collections_internals/README.md) | Type → handlers mapping, thread-safe |
| `CopyOnWriteArrayList` | [Collections Internals](../collections_internals/README.md) | Handler list: reads dominate; concurrent-safe iteration |
| `WeakReference` | [JVM Internals](../jvm_internals/README.md) | Subscribers GC'd when no strong ref — prevents memory leaks |
| `CompletableFuture` | [Concurrency](../concurrency/README.md) | Async dispatch, exception handling |
| `Executor` / virtual threads | [Concurrency](../concurrency/README.md) | Async event dispatch |
| Generics + type safety | [Generics & Type System](../generics_and_type_system/README.md) | Type-safe handler registration |
| Observer pattern | Design Patterns (LLD) | Core architectural pattern |
| `@FunctionalInterface` | [Java 8 Features](../java8_features/README.md) | `Consumer<T>` as event handler |

---

## Architecture

```
EventBus
  |-- handlers: ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<HandlerEntry>>
  |-- asyncExecutor: Executor (virtual thread executor in Java 21)

HandlerEntry
  |-- handlerRef: WeakReference<Consumer<Object>>  (allows GC)
  |-- priority: int (lower = higher priority)
  |-- async: boolean

Publishing model:
  sync:  post(event) -> handlers run in calling thread, in priority order
  async: postAsync(event) -> each handler wrapped in CompletableFuture

Subscription lifecycle:
  subscribe() -> store WeakReference in CopyOnWriteArrayList
  GC of subscriber -> WeakReference.get() returns null -> auto-purged on next publish
  unsubscribe() -> explicit removal (optional; GC handles it)
```

---

## Step-by-Step Design Decisions

### Decision 1: Handler storage — CopyOnWriteArrayList vs List + ReadWriteLock

**CopyOnWriteArrayList**: reads are lock-free (no synchronization), writes copy the array. Perfect for publish-heavy, subscribe-light patterns (subscribers register at startup; events flow constantly).

**List + ReadWriteLock**: more efficient writes (no full array copy), but reads acquire read lock.

**Choice**: `CopyOnWriteArrayList` — publish (read) path is the hot path; subscribe/unsubscribe is rare.

### Decision 2: WeakReference for subscriber memory safety

Without `WeakReference`: if an object subscribes to the event bus and later goes out of scope in the application, the event bus's strong reference to its handler keeps it alive — memory leak. Example: a ViewModel subscribes to events; the user navigates away; the ViewModel can't be GC'd because the event bus holds a reference.

With `WeakReference<Consumer<T>>`: when the subscriber has no other strong references, the JVM GCs it, and `weakRef.get()` returns `null`. The event bus detects this during dispatch and removes the dead entry.

### Decision 3: Exception isolation

Each handler is invoked in a try-catch. One handler's `RuntimeException` is caught, logged, and publishing continues to next handlers. Without this, a buggy handler silently kills all subsequent handlers for an event.

### Decision 4: Async dispatch model

Two options: (1) `CompletableFuture.runAsync(handler, executor)` per handler — full parallelism, order not guaranteed. (2) Single `CompletableFuture.runAsync(dispatchAll, executor)` — handlers run sequentially on a background thread, order preserved.

**Choice**: Option 2 for `postAsync()` — preserves handler priority order; avoids creating N futures per event. Option 1 available as `postParallel()` for fully independent handlers.

---

## Core Implementation

```java
public class EventBus {
    // Type -> list of handler entries (maintains priority order)
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<HandlerEntry>>
        handlers = new ConcurrentHashMap<>();

    private final Executor asyncExecutor;

    public EventBus() {
        // Java 21: virtual thread per task for async dispatch
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public EventBus(Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Subscribe to events of the given type.
     * Handler is stored as a WeakReference — if no other strong reference exists,
     * the handler will be GC'd and auto-removed from the bus.
     */
    public <T> Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        return subscribe(eventType, handler, 0); // default priority 0
    }

    @SuppressWarnings("unchecked")
    public <T> Subscription subscribe(Class<T> eventType, Consumer<T> handler, int priority) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(handler, "handler");

        CopyOnWriteArrayList<HandlerEntry> list = handlers.computeIfAbsent(
            eventType, k -> new CopyOnWriteArrayList<>()
        );

        HandlerEntry entry = new HandlerEntry(
            new WeakReference<>((Consumer<Object>) handler),
            priority
        );
        list.add(entry);
        // Sort by priority after add (CopyOnWriteArrayList: snapshot sort)
        handlers.compute(eventType, (k, v) -> {
            if (v == null) return null;
            List<HandlerEntry> sorted = new ArrayList<>(v);
            sorted.sort(Comparator.comparingInt(e -> e.priority));
            return new CopyOnWriteArrayList<>(sorted);
        });

        // Return Subscription for explicit unsubscribe
        return () -> list.remove(entry);
    }

    /**
     * Publish event synchronously. Handlers run in the calling thread, in priority order.
     * Exception in one handler is caught and logged; others still run.
     */
    public void post(Object event) {
        Objects.requireNonNull(event, "event");
        dispatch(event, false);
    }

    /**
     * Publish event asynchronously. Returns CompletableFuture that completes
     * when all handlers have run.
     */
    public CompletableFuture<Void> postAsync(Object event) {
        Objects.requireNonNull(event, "event");
        return CompletableFuture.runAsync(() -> dispatch(event, false), asyncExecutor);
    }

    /**
     * Dispatch to all handlers for the event's type and all supertypes.
     */
    private void dispatch(Object event, boolean async) {
        Class<?> eventType = event.getClass();
        List<HandlerEntry> deadEntries = new ArrayList<>();

        // Dispatch to handlers for this exact type and all supertypes/interfaces
        for (Class<?> type : getTypeHierarchy(eventType)) {
            CopyOnWriteArrayList<HandlerEntry> typeHandlers = handlers.get(type);
            if (typeHandlers == null) continue;

            for (HandlerEntry entry : typeHandlers) {
                Consumer<Object> handler = entry.handlerRef.get();
                if (handler == null) {
                    deadEntries.add(entry); // GC'd; schedule removal
                    continue;
                }
                try {
                    handler.accept(event);
                } catch (Exception e) {
                    System.err.println("Handler exception for event " +
                        eventType.getSimpleName() + ": " + e.getMessage());
                    // e.printStackTrace(); // or log properly
                }
            }
            // Clean up GC'd handlers
            if (!deadEntries.isEmpty()) {
                typeHandlers.removeAll(deadEntries);
                deadEntries.clear();
            }
        }
    }

    /**
     * Returns the type hierarchy: class itself, then superclasses, then interfaces.
     * Allows subscribing to a supertype and receiving all subtype events.
     */
    private List<Class<?>> getTypeHierarchy(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            hierarchy.add(c);
            addInterfaces(c, hierarchy);
            c = c.getSuperclass();
        }
        return hierarchy;
    }

    private void addInterfaces(Class<?> c, List<Class<?>> result) {
        for (Class<?> iface : c.getInterfaces()) {
            if (!result.contains(iface)) {
                result.add(iface);
                addInterfaces(iface, result);
            }
        }
    }

    /** Returns the number of registered handler entries (including GC'd). */
    public int handlerCount() {
        return handlers.values().stream()
            .mapToInt(List::size).sum();
    }

    // Internal entry
    private static class HandlerEntry {
        final WeakReference<Consumer<Object>> handlerRef;
        final int priority;

        HandlerEntry(WeakReference<Consumer<Object>> ref, int priority) {
            this.handlerRef = ref;
            this.priority = priority;
        }
    }
}

// Subscription: functional interface for unsubscription
@FunctionalInterface
public interface Subscription {
    void unsubscribe();
}
```

---

## Usage Examples

```java
EventBus bus = new EventBus();

// Inline lambda subscription (no strong reference outside bus -> GC risk!)
// Use strong reference in a field if the subscriber should live
OrderProcessor processor = new OrderProcessor();
Consumer<OrderPlaced> handler = processor::onOrderPlaced;  // strong ref in 'handler'

Subscription sub = bus.subscribe(OrderPlaced.class, handler, 1); // priority 1

// Subscribe to a supertype: receive all DomainEvent subtypes
bus.subscribe(DomainEvent.class, event ->
    auditLog.record(event.getClass().getSimpleName()));

// Publish
bus.post(new OrderPlaced("order-123", 99.99));
// -> auditor runs (DomainEvent handler), priority default 0
// -> processor runs, priority 1 (higher number = lower priority)

// Async publish
bus.postAsync(new OrderPlaced("order-456", 49.99))
   .exceptionally(ex -> { log.error("Async dispatch failed", ex); return null; });

// Explicit unsubscribe
sub.unsubscribe();
```

---

## WeakReference Memory Leak Prevention

```java
// PROBLEM without WeakReference:
class OrderView {
    OrderView() {
        bus.subscribe(OrderPlaced.class, this::onOrder);  // bus holds strong ref to lambda
    }
    void onOrder(OrderPlaced e) { ... }
}
// When OrderView goes out of scope: bus keeps it alive -> memory leak

// WITH WeakReference:
class EventBus {
    void subscribe(Class<?> type, Consumer<Object> handler) {
        var weakHandler = new WeakReference<>(handler);
        // store weakHandler -- when handler has no strong refs outside, GC can collect it
    }
}
// OrderView user must keep 'handler' as a field to prevent premature GC:
class OrderView {
    private final Consumer<OrderPlaced> handler = this::onOrder;  // strong ref
    OrderView() { bus.subscribe(OrderPlaced.class, handler); }
}
```

---

## Tradeoffs Considered

| Aspect | Choice | Alternative | Tradeoff |
|--------|--------|------------|---------|
| Handler storage | CopyOnWriteArrayList | List + ReadWriteLock | COWAL: faster read, slower write (full array copy) |
| Memory safety | WeakReference | Strong reference | WeakRef: leak-safe, but requires caller to hold strong ref |
| Exception handling | Per-handler try-catch | Propagate to publisher | Isolation: other handlers run; risk: bugs silently swallowed |
| Async executor | VirtualThreadPerTask | ForkJoinPool | VT: no thread starvation on I/O; FJP: CPU-bound batching |
| Supertype dispatch | Walk type hierarchy | Exact-type only | Hierarchy: flexible (subscribe to DomainEvent); slower dispatch |

---

## Interview Questions for This Case Study

**Q: Why use WeakReference for handler storage, and what's the caller's responsibility?**
Without WeakReference, the event bus holds a strong reference to every handler. If the subscriber (e.g., a ViewModel) goes out of scope, it can never be GC'd — the bus keeps it alive indefinitely. WeakReference allows GC to collect the handler when no other strong reference exists. The caller's responsibility: hold a strong reference to the handler (e.g., as a field) for the duration it should receive events. If only the bus holds the reference (via weak), the GC can collect it immediately, and the subscription silently stops working.

**Q: How does exception isolation work, and what's the risk of swallowing exceptions?**
Each handler invocation is wrapped in a `try-catch(Exception)`. If handler A throws, it's logged/recorded but publishing continues to handler B, C, etc. The risk: if no logging is configured, exceptions are silently swallowed — bugs in handlers become invisible. Best practice: log with full stack trace, or expose a configurable `ExceptionHandler` callback that the application provides. In testing, configure the exception handler to re-throw to make test failures visible.

**Q: Why use CopyOnWriteArrayList for the handler list?**
The publish operation reads the handler list on every event — the read path must be fast and non-blocking. `CopyOnWriteArrayList` provides lock-free reads (reads the current snapshot). Writes (subscribe/unsubscribe) copy the entire array — expensive, but rare. Alternative: `List` + `ReentrantReadWriteLock` — writes are cheaper (in-place modification), reads acquire a read lock. For an event bus where subscribe/unsubscribe happens at startup and events flow constantly, `CopyOnWriteArrayList` is optimal.

**Q: How does subscribing to a supertype work?**
When posting an event of type `OrderPlaced` (which implements `DomainEvent`), the bus walks the full type hierarchy: `OrderPlaced`, then its superclasses, then its interfaces (`DomainEvent`, etc.). For each type in the hierarchy, it invokes all registered handlers. This allows general handlers (`DomainEvent` → audit log) to receive all domain events without needing individual subscriptions per type. The `getTypeHierarchy()` method builds this list.

**Q: What is the thread safety model of this event bus?**
`ConcurrentHashMap` for the top-level type→handlers map: thread-safe concurrent access. `CopyOnWriteArrayList` for handler lists: lock-free reads, copy-on-write for modifications — safe for concurrent publish + subscribe. The `dispatch()` method is fully re-entrant (no locks held): multiple threads can publish simultaneously. The dead-entry cleanup (`removeAll`) is safe because `CopyOnWriteArrayList.removeAll()` is synchronized on write (but the check for dead handlers during iteration is on the read path, using the snapshot).

---

## Failure Scenarios

| Component | Failure | Symptom | Recovery | Time-to-Recovery |
|-----------|---------|---------|----------|------------------|
| Handler | Throws unchecked exception | Other handlers skipped (broken) | Per-handler try-catch + DLQ | immediate |
| Async executor | Pool saturated by slow handler | Latency spike, rejected tasks | Per-subscriber queue + circuit breaker | seconds |
| Dead-letter queue | Fills up | Memory growth | Bounded DLQ + drain/alert | until drained |
| Publish thread | Blocks on sync handler | Caller stalls | Timeout + move handler to async | immediate |

### Handler throws an unchecked exception

BROKEN — one bad handler aborts dispatch for everyone after it:

```java
// BROKEN: an unchecked exception escapes dispatch and kills the loop/thread
public void dispatch(Event e) {
    for (EventHandler<Event> h : handlersFor(e.getClass())) {
        h.handle(e);   // if handler #2 throws NPE, handlers #3..N never run
    }
}
```

FIX — isolate each handler; route failures to a dead-letter queue:

```java
// FIX: each handler invocation is isolated; failures captured, not propagated
public void dispatch(Event e) {
    for (EventHandler<Event> h : handlersFor(e.getClass())) {
        try {
            h.handle(e);
        } catch (Exception ex) {                       // catch Exception, not Throwable
            deadLetterQueue.offer(new DeadLetter(e, h, ex, Instant.now()));
            log.error("handler {} failed for {}", h, e, ex);
            // continue to the next handler
        }
    }
}
```

Recovery procedure: a background drainer retries dead letters with backoff (transient failures recover), and permanently failing entries are surfaced to an operator. Time-to-recovery for the dispatch path is immediate — the bus never stops delivering to healthy handlers because of one sick handler.

---

## Capacity Planning Math

### Thread pool sizing for async dispatch

```
Throughput:     50,000 events/sec
Fan-out:        8 subscribers per event
Invocations:    50,000 x 8 = 400,000 handler calls/sec
Per-handler:    0.1 ms CPU+IO  (= 0.0001 s)

Service demand: 400,000 x 0.0001 s = 40 handler-seconds of work per wall-clock second
=> need ~40 threads busy continuously just to keep up (utilization = 1.0)
```

You never run at utilization 1.0. Apply a headroom factor and the I/O-aware variant of the pool formula:

```
threads = cores * targetUtilization * (1 + waitTime/serviceTime)

If handlers are 50% I/O (waitTime == serviceTime), W/S = 1:
threads = 40 * (1 + 1) = 80 to absorb the same throughput with I/O waits.
```

So a bounded pool of ~80 threads with a bounded queue handles the load; size the queue for a burst budget (e.g., 1 second of slack = up to 50k queued events before rejection).

### Backpressure / memory budget

```
Bounded queue capacity 50,000 events x ~256 bytes/event ref+payload = ~12.8 MB headroom.
When full -> CallerRunsPolicy (publisher executes the task) applies natural backpressure
            instead of OOM from an unbounded queue.
```

---

## Benchmark Comparisons — Dispatch Strategy

JMH-style, single-event publish with fan-out, Java 21:

| Strategy | Throughput | Latency | Ordering | When to use |
|----------|-----------|---------|----------|-------------|
| Synchronous (chosen for ordering) | ~80k events/sec | 0.01 ms | Strict per-publisher | Audit/order-sensitive flows; simplest |
| Async bounded queue (ExecutorService) | ~400k events/sec | 0.5 ms | None across handlers | High fan-out, order-independent handlers |
| LMAX Disruptor ring buffer | ~6M events/sec | 0.05 ms | Strict (single producer) | Ultra-low-latency, mechanical-sympathy hot paths |

Synchronous dispatch was chosen as the default because it preserves per-publisher ordering and is trivial to reason about: when `publish()` returns, all handlers have run. Async multiplies throughput ~5x by decoupling the publisher from handler latency, at the cost of ordering and observability (failures happen off-thread). The Disruptor's ring buffer avoids per-event allocation and lock contention entirely (single-writer principle, cache-line padding), reaching millions/sec — reserve it for genuine hot paths where the complexity is justified.

---

## Production War Stories

### War story 1 — Subscriber registered N times causing duplicate payments

Symptom: every order produced 4 audit records and, worse, 4 payment captures.

BROKEN — a new controller instance subscribed on each construction, and the framework created the controller per request:

```java
// BROKEN: subscribe() runs in the constructor; controller is created repeatedly
public class CheckoutController {
    public CheckoutController(EventBus bus) {
        bus.subscribe(PaymentEvent.class, this::capturePayment); // accumulates duplicates
    }
    void capturePayment(PaymentEvent e) { gateway.capture(e.amount()); }
}
// After 4 requests -> 4 live handlers -> one PaymentEvent captured 4 times
```

FIX — de-duplicate by subscriber identity and warn on double registration:

```java
// FIX: subscribe is idempotent per (eventType, handlerIdentity); warn on dup
public <E extends Event> void subscribe(Class<E> type, EventHandler<E> h) {
    var list = handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
    if (list.stream().anyMatch(existing -> existing.identity().equals(h.identity()))) {
        log.warn("duplicate subscription ignored for {} by {}", type, h.identity());
        return;
    }
    list.add(h);
}
```

Better structural fix: subscribe singletons once at startup, not per-request objects; or have handlers carry a stable identity (class + method) used for de-duplication. Lesson: registration with side effects in per-request constructors silently multiplies handlers.

### War story 2 — Slow subscriber exhausting the shared async pool

Symptom: at only 100 events/sec the 20-thread async pool saturated and unrelated handlers (notifications, metrics) stopped firing.

BROKEN — all subscribers share one pool; a 200ms fraud handler hogs every thread:

```java
// BROKEN: one shared pool; slow handler starves fast handlers
ExecutorService pool = Executors.newFixedThreadPool(20);
void dispatchAsync(Event e) {
    for (var h : handlersFor(e.getClass())) {
        pool.submit(() -> h.handle(e));   // fraudHandler @200ms ties up threads
    }
}
// 100 events/sec x fraud 0.2s = 20 thread-seconds/sec -> pool fully consumed.
```

FIX — per-subscriber bounded queue + circuit breaker to shed slow subscribers:

```java
// FIX: isolate each subscriber on its own bounded executor; trip a breaker if it falls behind
class SubscriberChannel {
    final ExecutorService exec;            // dedicated, bounded queue
    final CircuitBreaker breaker;          // opens when reject-rate/latency exceeds threshold

    void submit(Event e, EventHandler<Event> h) {
        if (breaker.isOpen()) { deadLetterQueue.offer(new DeadLetter(e, h, "breaker open")); return; }
        try {
            exec.submit(() -> { long t=System.nanoTime(); h.handle(e); breaker.record(System.nanoTime()-t); });
        } catch (RejectedExecutionException rex) {
            breaker.onReject();            // queue full -> count toward tripping
            deadLetterQueue.offer(new DeadLetter(e, h, rex));
        }
    }
}
```

This is the bulkhead pattern: a slow fraud handler can fall behind or be temporarily disabled without affecting the notification or metrics handlers, each on its own channel. Lesson: never let independent subscribers share a single unbounded queue/pool.

---

## Evolution / Scalability at 10x Load

At 500k events/sec across many JVMs, an in-process bus no longer fits — events must cross process and machine boundaries:

```
   Producers (services)                   Consumers (services)
        |                                       ^
        v                                       |
   +-------------------------------------------------+
   |   Kafka (partitioned, replicated topics)        |
   |   partition key = aggregate id -> per-key order |
   +-------------------------------------------------+
        |                                       |
   +-----------+                          +-----------+
   |  Schema   |  validates on produce    | Consumer  |  offset commits,
   |  Registry |  + consume (compat rules)| groups    |  at-least-once delivery
   +-----------+                          +-----------+
```

1. Distributed event bus (Kafka) — replace in-JVM dispatch with partitioned topics. Per-partition ordering replaces per-publisher ordering; choose the partition key (e.g., aggregate id) to preserve the ordering you actually need. Delivery becomes at-least-once, so handlers must be idempotent (de-dupe by event id) — directly informed by war story 1.
2. Event sourcing — persist events as the source of truth; rebuild state by replay. Enables audit, time-travel, and rebuilding read models, at the cost of replay complexity and snapshotting.
3. Schema registry — version events with backward/forward-compatible schemas (Avro/Protobuf). Producers and consumers evolve independently; the registry rejects incompatible changes at publish time.

Technical debt to track: the in-process bus has no durability — a crash loses in-flight async events and the DLQ (unless persisted). Before going distributed, persist the DLQ and make handlers idempotent, since at-least-once delivery and redelivery are unavoidable in the Kafka world.

---

## Additional Interview Questions

**Q: How many threads do you need for 50k events/sec with 8 subscribers at 0.1ms each?**
Total work is `50,000 x 8 x 0.0001 s = 40` handler-seconds per wall-clock second, so you need roughly 40 fully-busy threads just to break even. Because you never run at 100% utilization and handlers often do I/O, apply the formula `cores * utilization * (1 + wait/service)` — for 50% I/O handlers that doubles to about 80 threads. Pair that with a bounded queue sized for a one-second burst and a `CallerRunsPolicy` for backpressure.

**Q: Synchronous vs async dispatch — what do you trade?**
Synchronous dispatch preserves per-publisher ordering and gives you a simple guarantee — when `publish()` returns, all handlers ran and any exception is on your thread — but it caps throughput (~80k/sec) and couples the publisher to the slowest handler. Async decouples them for ~5x throughput but loses ordering and moves failures off-thread, so you need a DLQ and idempotent handlers. Choose sync for audit/payment-style ordered flows and async for high-fan-out, order-independent work.

**Q: Why must distributed-bus handlers be idempotent?**
Kafka and most message buses guarantee at-least-once delivery, so a handler can legitimately see the same event twice (consumer rebalance, redelivery after a failed commit). If the handler captures a payment or writes an audit row without de-duplication, you get duplicates — exactly the in-process war story, now unavoidable. De-dupe by a stable event id (idempotency key) persisted in the handler's store.

**Q: How do you stop one slow subscriber from degrading the whole bus?**
Apply the bulkhead pattern: give each subscriber its own bounded executor/queue rather than a shared pool, so a 200ms fraud handler cannot starve fast handlers. Add a circuit breaker per subscriber that opens on rising latency or queue rejections, shedding events to the DLQ until the subscriber recovers. This isolates failure and latency to the misbehaving subscriber.

**Q: How do you evolve event schemas without breaking consumers?**
Use a schema registry with compatibility rules (backward/forward) and a binary format like Avro or Protobuf; the registry rejects incompatible producer changes at publish time. Add only optional fields with defaults for backward compatibility, and never repurpose field tags. This lets producers and consumers deploy independently, which is essential once they live in separate services.
