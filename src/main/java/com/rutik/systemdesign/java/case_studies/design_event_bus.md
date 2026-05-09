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
