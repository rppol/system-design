# Observer Pattern

## 1. Pattern Name & Category

**Pattern:** Observer
**Category:** Behavioral
**GoF Classification:** Behavioral Design Pattern (Gang of Four)
**Also Known As:** Publish-Subscribe (Pub/Sub), Event-Listener, Dependents

---

## 2. Intent

Define a one-to-many dependency between objects so that when one object (the Subject) changes state, all its dependents (Observers) are notified and updated automatically.

---

## Intuition

> **One-line analogy**: Observer is like a newsletter subscription — when the publisher posts a new issue, all subscribers receive it automatically. Publishers don't know who's subscribed; subscribers don't know about other subscribers.

**Mental model**: An object (Subject/Observable) has a list of dependents (Observers). When the Subject changes state, it iterates the list and notifies each Observer. Observers register/unregister themselves. The Subject never needs to know what Observers do with the notification — they're decoupled. This is the foundation of event-driven programming, reactive systems, and the publish-subscribe pattern.

**Why it matters**: Observer is one of the most widely used patterns in software — Java's EventListener, React's useState hooks, Redux reducers, Kafka consumers, DOM event listeners, and Spring's ApplicationEventPublisher all implement Observer semantics. Event-driven architecture at scale is built on this pattern.

**Key insight**: The key tradeoff is decoupling vs. unpredictability. Observers are decoupled from the Subject, but the order of notification is undefined, and observers can have cascading side effects. In complex systems, use Observable/reactive frameworks (RxJava, Project Reactor) instead of raw Observer implementations.

---

## 3. Problem Statement

### The Core Problem
You have an object whose state changes affect multiple other objects, but you don't want to hard-code those dependencies. The object that changes state (Subject) should not need to know which specific objects depend on it, how many there are, or how they react to the change.

### Concrete Scenario
Consider a **stock price ticker**. A stock price object holds the current price of a stock. Several UI components depend on it: a chart panel, a price label, a buy/sell alert system, and a logging module. Every time the price changes, all four must be updated.

Without a pattern, the stock price object would call each UI component directly:
```
price.setPrice(150.0);
chartPanel.update(150.0);
priceLabel.update(150.0);
alertSystem.check(150.0);
logger.log(150.0);
```
Now the stock price object is coupled to 4 concrete classes. Adding a 5th observer (e.g., a portfolio calculator) requires modifying the stock price class — violating the Open/Closed Principle.

### What Goes Wrong Without the Pattern
- The Subject is coupled to every Observer class it must notify.
- Adding/removing observers requires modifying the Subject.
- The Subject and Observers form a web of hard-coded dependencies that is hard to test.
- Observers cannot be composed or changed at runtime.

---

## 4. Solution

Define two abstractions:
- **Subject (Observable)** — maintains a list of Observers. Provides `attach()`, `detach()`, and `notify()` methods.
- **Observer** — defines an `update()` method that the Subject calls when state changes.

Concrete Subjects hold state. Concrete Observers react to notifications. Neither knows the concrete type of the other — they communicate through the Subject and Observer interfaces.

---

## 5. UML Structure

```
         +------------------+          +-------------------+
         |    <<interface>> |          |  <<interface>>    |
         |     Subject      |1       * |     Observer      |
         |------------------|--------->|-------------------|
         | +attach(o)       |          | +update()         |
         | +detach(o)       |          +-------------------+
         | +notifyObservers()|                  ^
         +------------------+                  |
                  ^                            |
                  |                +-----------+----------+
         +------------------+      |                      |
         | ConcreteSubject  |  ConcreteObserverA   ConcreteObserverB
         |------------------|      |                      |
         | -state           |  +update()             +update()
         | +getState()      |
         | +setState()      |
         +------------------+
```

**Push vs. Pull Model:**
- **Push model:** Subject sends the changed data to `update(data)` directly.
- **Pull model:** Subject sends only a reference to itself; Observers call `subject.getState()` to pull what they need.

---

## 6. How It Works — Step-by-Step

1. **Observers register** — each Observer calls `subject.attach(this)`, adding itself to the Subject's internal list.
2. **Subject state changes** — some code calls `subject.setState(newValue)`.
3. **Subject notifies** — `setState()` calls `notifyObservers()` internally.
4. **Each Observer is called** — `notifyObservers()` iterates the list and calls `observer.update()` on each.
5. **Observers react** — each Observer reads the relevant state (either from the argument or by calling `getState()`) and updates itself.
6. **Subject is unaware of specifics** — the Subject only knows it has a list of `Observer` objects; it doesn't know their concrete types.

---

## 7. Key Components

| Role | Responsibility |
|---|---|
| **Subject (Observable)** | Maintains observer list; notifies on state change |
| **ConcreteSubject** | Holds actual state; triggers notifications on change |
| **Observer** | Interface with `update()` method |
| **ConcreteObserver** | Reacts to Subject notifications; maintains reference to Subject if using pull model |

---

## 8. When to Use

- **Event systems** — GUI events (button clicks, text changes) where many components react to one event source.
- **MVC architecture** — the Model is the Subject; Views are Observers. Model state changes propagate to all views automatically.
- **Reactive data binding** — spreadsheet cells that recalculate when a source cell changes.
- **Notification services** — push notifications, email alerts, SMS alerts triggered by a single domain event.
- **Logging and monitoring** — multiple monitoring systems (metrics, logs, traces) need to react to the same application events.
- **Pub/Sub systems** — any broadcast messaging where one publisher has many unknown subscribers.
- **Domain events** — Domain-Driven Design uses domain events (Observer pattern) to decouple aggregates.
- **Change propagation** — any time you have "when X changes, Y and Z should update."

---

## 9. When NOT to Use

- **When you have only one observer** — plain dependency injection and a direct call is simpler.
- **When notification order matters critically** — the Observer pattern does not guarantee notification order by default.
- **When the Subject and Observer are in a tight feedback loop** — an Observer that modifies the Subject during `update()` can cause infinite loops or unexpected re-entrant calls.
- **When observers need transactional guarantees** — if some observers must succeed and others must not be notified on failure, you need a more robust event bus with rollback support.
- **When observers are in different threads** — naive Observer implementations are not thread-safe. Consider using a proper event bus (Guava EventBus, RxJava) instead.

---

## 10. Pros

- **Loose coupling** — Subject and Observers are decoupled; they interact only through interfaces.
- **Open/Closed Principle** — new Observers can be added without modifying the Subject.
- **Dynamic relationships** — Observers can attach/detach at runtime.
- **Broadcast communication** — one state change notifies all registered observers simultaneously.
- **Supports MVC** — the backbone of the Model-View-Controller architecture.
- **Composable** — Observers can themselves be Subjects, enabling event chains.
- **Testable** — mock Observers can verify that the Subject notifies correctly.

---

## 11. Cons

- **Unexpected update order** — Observers are notified in registration order by default; relying on this is fragile.
- **Memory leaks (Lapsed Listener Problem)** — if an Observer is not detached before being garbage-collected (or goes out of scope), it leaks. Common in Java with anonymous inner classes.
- **Cascading updates** — an Observer modifying the Subject triggers another round of notifications, which can cause infinite loops or hard-to-trace update storms.
- **Performance overhead** — notifying many Observers on every state change can be costly, especially if some Observers don't care about most changes.
- **Debugging difficulty** — tracing why an Observer received a notification requires understanding the full Subject/Observer graph, which can be complex.
- **No context in basic `update()`** — Observers may not know *what* changed, only that *something* changed, leading to unnecessary recomputation.

---

## 12. Tradeoffs

| You Gain | You Lose |
|---|---|
| Loose coupling between Subject and Observers | Predictable, explicit call order |
| Extensibility without modifying Subject | Easy debuggability of notification chains |
| Runtime-dynamic observer registration | Simple memory management (lapsed listener risk) |
| Broadcast semantics | Performance on high-frequency changes |

---

## 13. Common Pitfalls

1. **Lapsed Listener (memory leak)** — an Observer that is no longer needed is not detached. It continues to receive updates and cannot be GC'd because the Subject holds a reference to it. Fix: always detach in the Observer's cleanup/destroy lifecycle.

2. **Notifying inside a setter without guard** — calling `notifyObservers()` every time a setter is called, even when the value didn't change. Always check if the value actually changed before notifying.

3. **Thread safety** — iterating the observer list while another thread is calling `attach()` or `detach()` causes `ConcurrentModificationException`. Use a `CopyOnWriteArrayList` for the observer list.

4. **Circular notifications** — Observer A updates Subject B; Subject B notifies Observer C which updates Subject A, creating a cycle. Guard against re-entrant notification with a flag.

5. **God Subject** — dumping every possible event into one Subject/Observer channel. Use typed events or separate channels for separate concerns.

6. **Not passing enough context** — calling `update()` with no arguments forces Observers to pull state, but they then receive the *current* state, not the state *at the time of the event*. Use event objects with enough context.

---

## 14. Real-World Usage

| Framework / Library | Usage |
|---|---|
| **Java SDK** | `java.util.Observable` / `java.util.Observer` (deprecated in Java 9, but the canonical reference). `PropertyChangeListener` / `PropertyChangeSupport` in JavaBeans is an Observer implementation. |
| **Java Swing/AWT** | `ActionListener`, `MouseListener`, `KeyListener` — all Observer pattern implementations. Every `addXxxListener()` method is `attach()`. |
| **Spring Framework** | `ApplicationEventPublisher` / `ApplicationListener` — Spring's event system is Observer. `@EventListener` annotation. |
| **Android** | `LiveData` + `Observer` in Android Architecture Components. `BroadcastReceiver` is a system-level Observer. |
| **RxJava / Reactor** | `Observable`/`Flowable` (RxJava) and `Flux`/`Mono` (Reactor) are reactive extensions of the Observer pattern with backpressure support. |
| **JavaScript** | `EventEmitter` (Node.js), `addEventListener` in the DOM, Redux store subscriptions. |
| **JPA / Hibernate** | `@EntityListeners`, `@PostPersist`, `@PreUpdate` — entity lifecycle callbacks. |
| **Guava** | `EventBus` — a type-safe, annotation-driven Observer implementation that decouples subscriber registration from event dispatch. |

---

## 15. Comparison with Similar Patterns

| Pattern | Similarity | Key Difference |
|---|---|---|
| **Mediator** | Both decouple objects | Mediator centralizes all communication through one object; Observer distributes notification directly from Subject to all Observers. |
| **Command** | Both support action triggering | Command encapsulates a specific action with parameters; Observer is about broadcasting state-change notifications. |
| **Event Bus / Pub-Sub** | Both broadcast events | Pub-Sub adds an intermediary broker; Subject and Subscriber are fully decoupled (don't know about each other). Observer has a direct Subject reference. |
| **Chain of Responsibility** | Both pass information to multiple objects | CoR passes a request along a chain until one handler handles it; Observer notifies all registered observers. |

---

## 16. Interview Tips

**Q: Explain the Observer pattern.**
A: One Subject maintains a list of Observers. When the Subject changes state, it notifies all Observers by calling their `update()` method. This decouples the Subject from knowing which specific objects depend on it. Classic example: MVC where the Model is the Subject and Views are Observers.

**Q: What is the lapsed listener problem?**
A: When an Observer registers with a Subject but is never detached, the Subject holds a strong reference to it. Even if no other code references the Observer, it cannot be GC'd. This is a memory leak. Fix: always call `detach()` in the Observer's cleanup method.

**Q: Push vs. Pull model — which do you prefer?**
A: Pull is generally safer. With push, the Subject may send stale data if the observer list is iterated asynchronously. With pull, the Observer reads state when it's ready. However, pull requires the Observer to hold a reference to the Subject, which adds coupling. The right choice depends on whether Observers need the exact state at the moment of notification.

**Q: How do you make Observer thread-safe?**
A: Use `CopyOnWriteArrayList` for the observer list (snapshot on write, lock-free reads), synchronize `attach`/`detach`, and be careful about re-entrant calls. Alternatively, dispatch notifications on a single-threaded event loop (like Android's main thread handler).

**Q: How is Observer different from Pub/Sub?**
A: In Observer, the Subject knows its Observers (holds references). In Pub/Sub, there's an intermediary broker; publishers and subscribers don't know about each other. Pub/Sub scales better across processes/machines.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Observer Appears in Distributed Systems**

- **Event-driven microservices** — Kafka, SNS/SQS, and RabbitMQ implement Observer at infrastructure scale. Services publish domain events (OrderPlaced, UserRegistered) without knowing who consumes them; consumers subscribe independently. This enables loose coupling and independent deployability.
- **Service health monitoring** — Health check systems observe service state. When a service's health check fails, observer subscribers (load balancer, alert manager, auto-scaler) react without the health check system knowing about each subscriber.
- **Config change propagation** — Distributed config systems (Consul, etcd, Spring Cloud Config) push config changes to all service instances as observer notifications. Services update their live config without restarting.
- **WebSocket / SSE push** — Real-time feeds (stock prices, sports scores, order tracking) subscribe clients as observers. When underlying data changes, all subscribed clients receive the push — Observer at the client-facing transport layer.

---

## 17. Best Practices

1. **Use `CopyOnWriteArrayList`** for the observer list to avoid `ConcurrentModificationException` when observers add/remove themselves during notification.

2. **Always provide `detach()`** and document that callers are responsible for calling it when an Observer goes out of scope.

3. **Use typed event objects** instead of a generic `update()` — `update(StockPriceChangedEvent event)` is far more descriptive and allows Observers to filter events.

4. **Check for value equality before notifying** — `if (!newValue.equals(this.state)) { this.state = newValue; notifyObservers(); }`.

5. **Consider weak references** for long-lived Subjects with short-lived Observers to mitigate lapsed listener leaks.

6. **Prefer Spring's `ApplicationEventPublisher`** or Guava's `EventBus` in production code over rolling your own Observer implementation.

7. **Avoid notifying from a constructor** — Observers attached before the Subject is fully initialized may receive notifications in an inconsistent state.

8. **Document the notification contract** — specify which thread notifications occur on, the order guarantees (if any), and whether re-entrant calls are safe.
