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

### Production Scenario: Payment Processing Fan-Out with Guava EventBus (50k events/sec)

A payment processing service at fintech scale handles 50,000 payment events per second at peak.
Each payment event must fan out to 8 downstream subscribers: audit log, fraud check, ledger update,
notification service, analytics pipeline, compliance recorder, retry queue, and dead-letter handler.
The naive approach calls each subscriber inline on the payment thread, so a 300ms fraud-check timeout
blocks the payment response and causes P99 latency to spike to 2.4 seconds.

The solution uses Guava `EventBus` (synchronous, single-threaded) for in-process fan-out in
development/staging, then promotes to `AsyncEventBus` backed by a bounded `ThreadPoolExecutor`
for production. The payment thread posts the event and returns immediately — each subscriber
processes asynchronously within its own executor thread.

**Scale numbers:**
- 50,000 payment events/sec peak throughput
- Guava `EventBus` (sync, no I/O): ~900,000 dispatches/sec on a single core
- `AsyncEventBus` with 16-thread pool: payment thread post latency drops from 800ms to <1ms
- Bounded queue (capacity 10,000): back-pressure prevents OOM during subscriber lag spikes
- Memory: each `@Subscribe` registration costs ~80 bytes (method handle + WeakReference wrapper)
- Fraud subscriber SLA: 200ms; async executor isolates timeout from payment thread

```
Production Architecture — Payment Event Fan-Out via AsyncEventBus
==================================================================

  [ Payment API Thread ]
         |
         | paymentEventBus.post(PaymentEvent)   <-- returns in < 1ms
         |
  +------+------+
  | AsyncEventBus|  (Guava, backed by BoundedExecutor, 16 threads, queue=10000)
  +------+------+
         |
    fan-out to 8 @Subscribe handlers (each runs on executor thread pool)
         |
   +-----+------+------+------+------+------+------+------+
   |     |      |      |      |      |      |      |      |
 Audit Fraud Ledger Notify Analyt Comply Retry  DLQ
  Log  Check Update  Svc  Pipeline Rcrdr Queue Handler
  (DB) (HTTP)(DB)  (SMTP) (Kafka) (S3)  (Redis)(SQS)
```

```java
// Java 17 LTS — Guava 32.x AsyncEventBus with bounded executor
// Production payment event fan-out

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public record PaymentEvent(
    String paymentId,
    String merchantId,
    long amountCents,
    String currency,
    String status,       // AUTHORISED, SETTLED, DECLINED, REFUNDED
    long epochMillis
) {}

// --- EventBus configuration (Spring @Configuration bean) ---
@Configuration
public class PaymentEventBusConfig {

    @Bean
    public AsyncEventBus paymentEventBus() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            8,                              // corePoolSize
            16,                             // maximumPoolSize
            60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10_000),  // bounded — back-pressure
            new ThreadPoolExecutor.CallerRunsPolicy()  // slow payment thread if queue full
        );
        return new AsyncEventBus("payment-bus", executor);
    }
}

// --- Payment service posts events ---
@Service
public class PaymentService {

    private final AsyncEventBus eventBus;

    public PaymentService(AsyncEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public PaymentResult process(PaymentRequest req) {
        PaymentResult result = chargeProcessor(req);
        // post() returns immediately — all 8 subscribers run on executor threads
        eventBus.post(new PaymentEvent(
            result.id(), req.merchantId(), req.amountCents(),
            req.currency(), result.status(), System.currentTimeMillis()
        ));
        return result;  // response returned before any subscriber finishes
    }
}

// --- One of 8 subscribers (fraud check) ---
@Component
public class FraudCheckSubscriber {

    private final FraudClient fraudClient;

    public FraudCheckSubscriber(AsyncEventBus eventBus, FraudClient fraudClient) {
        this.fraudClient = fraudClient;
        eventBus.register(this);  // explicit registration — must match explicit unregister
    }

    @Subscribe
    public void onPayment(PaymentEvent event) {
        // Runs on AsyncEventBus executor thread, never on payment API thread
        fraudClient.score(event.paymentId(), event.amountCents());
    }

    @PreDestroy
    public void destroy(AsyncEventBus eventBus) {
        eventBus.unregister(this);  // prevents memory leak
    }
}
```

### Famous Codebase Usages

- **Guava `EventBus` / `AsyncEventBus`** (`com.google.common.eventbus`): annotation-driven subscriber
  registration via `@Subscribe`; `AsyncEventBus` wraps any `Executor`; used extensively in Android apps
  and backend services for in-process event decoupling.
- **Spring `ApplicationEventPublisher`**: `AbstractApplicationContext.publishEvent()` dispatches
  `ApplicationEvent` objects to all `ApplicationListener<E>` beans; `@TransactionalEventListener`
  guarantees delivery only after the surrounding transaction commits (eliminates phantom events).
- **Java Swing `EventListenerList`** (`javax.swing.event`): stores `(type, listener)` pairs in a flat
  array for O(1) iteration; `fireStateChanged()` in `AbstractButton` iterates this list directly.
- **Java `PropertyChangeSupport`** (`java.beans`): `firePropertyChange(name, old, new)` is the JDK's
  built-in observer for JavaBeans; used by Swing `JComponent` for bound properties.
- **RxJava `Observable.subscribe()`**: reactive push-based observer with backpressure, error channels,
  and completion signals; `Subject` classes (`PublishSubject`, `BehaviorSubject`) act as both
  Observable and Observer.

---

### Anti-Pattern 1: Synchronous Observer Exception Blocks Subject Thread

```java
// BROKEN — Java 17 LTS
// If FraudCheckObserver throws a RuntimeException, the payment thread propagates it
// and the remaining 7 subscribers (ledger, notification, analytics...) never run.

public class PaymentSubject {
    private final List<PaymentObserver> observers = new ArrayList<>();

    public void notifyObservers(PaymentEvent event) {
        for (PaymentObserver observer : observers) {
            observer.onPayment(event);  // throws => loop aborts, partial notification
        }
    }
}
```

```java
// FIX — defensive notification with per-observer try/catch and structured logging
// Java 17 LTS

public class PaymentSubject {
    private final List<PaymentObserver> observers = new CopyOnWriteArrayList<>();
    private static final Logger log = LoggerFactory.getLogger(PaymentSubject.class);

    public void notifyObservers(PaymentEvent event) {
        for (PaymentObserver observer : observers) {
            try {
                observer.onPayment(event);
            } catch (Exception e) {
                // Log and continue — one broken subscriber must not block others
                log.error("Observer {} failed for payment {}: {}",
                    observer.getClass().getSimpleName(), event.paymentId(), e.getMessage(), e);
            }
        }
    }
}
// Better yet: use AsyncEventBus — subscriber exceptions are caught internally by
// EventBus.SubscriberExceptionHandler, isolated per-subscriber, never propagated to poster.
```

---

### Anti-Pattern 2: Observer Registered but Never Unregistered (Memory Leak)

```java
// BROKEN — observer registered in constructor, never deregistered
// The EventBus holds a strong reference; the subscriber object is never GC'd
// even after the screen/component it belongs to is "gone".
// In a payment service redeploying config at runtime, this leaks 8 MB/hour.

@Component
public class AnalyticsSubscriber {
    public AnalyticsSubscriber(EventBus bus) {
        bus.register(this);  // strong reference stored in EventBus.subscribers map
        // no corresponding bus.unregister(this) anywhere
    }
}
```

```java
// FIX Option A — explicit lifecycle with @PreDestroy (Spring beans)
@Component
public class AnalyticsSubscriber {
    private final EventBus bus;

    public AnalyticsSubscriber(EventBus bus) {
        this.bus = bus;
        bus.register(this);
    }

    @PreDestroy
    public void cleanup() {
        bus.unregister(this);
    }
}

// FIX Option B — WeakReference subscriber wrapper for non-Spring contexts
// Guava does NOT do this automatically; you must wrap if lifecycle is uncontrolled.
// The safest production approach: always pair register() with an explicit unregister()
// in a try-finally or AutoCloseable pattern.
public class WeakSubscriberWrapper implements AutoCloseable {
    private final WeakReference<Object> ref;
    private final EventBus bus;

    public WeakSubscriberWrapper(EventBus bus, Object subscriber) {
        this.bus = bus;
        this.ref = new WeakReference<>(subscriber);
        bus.register(subscriber);
    }

    @Override
    public void close() {
        Object sub = ref.get();
        if (sub != null) bus.unregister(sub);
    }
}
```

---

### Anti-Pattern 3: Stale Snapshot — Observer Reads Current State Instead of Event State

```java
// BROKEN — pull model: observer captures a reference to the subject,
// then calls subject.getStatus() in the handler.
// Between the event fire and the handler execution (async gap), the subject
// status may have changed: SETTLED -> REFUNDED.
// The fraud observer records the wrong state.

public class FraudObserver implements PaymentObserver {
    private final PaymentSubject subject;  // reference to live object

    @Override
    public void onPayment() {
        // WRONG: reads *current* state, not state at event-fire time
        String status = subject.getStatus();
        fraudClient.record(subject.getPaymentId(), status);
    }
}
```

```java
// FIX — push model: carry all required state inside the event object (record/value object).
// The observer receives a snapshot of state at event-fire time; no subject reference needed.

public record PaymentEvent(
    String paymentId,
    String status,         // state AT THE MOMENT OF PUBLISHING — immutable snapshot
    long amountCents,
    long epochMillis
) {}

public class FraudObserver {
    @Subscribe
    public void onPayment(PaymentEvent event) {
        // event.status() is the status when the event was published — always correct
        fraudClient.record(event.paymentId(), event.status());
    }
    // No subject reference, no stale-read risk, fully testable with just a record instance.
}
```

---

### Performance and Correctness Numbers

| Approach | Post latency | Subscriber isolation | Memory per subscriber |
|---|---|---|---|
| Synchronous EventBus (no I/O) | ~1 us | None — exception aborts loop | ~80 bytes |
| Synchronous EventBus (with DB write) | 200-800 ms | None | ~80 bytes |
| AsyncEventBus, 16-thread pool | < 1 ms | Full — exception caught by handler | ~80 bytes + thread stack |
| Spring @TransactionalEventListener | < 1 ms (post-commit) | Full per-listener | ~200 bytes (proxy) |
| RxJava Observable.subscribe() | < 1 ms | Full per stream | ~300 bytes (subscription) |

### Migration Story

**Move TO Observer when:**
- A single domain event triggers 3 or more downstream actions and you want to add/remove
  subscribers without touching the publisher.
- You need transactional event delivery (Spring `@TransactionalEventListener`).
- Fan-out count grows over time — each new subscriber should be a separate class, not another
  method call added to a growing `processPayment()` method.

**Move AWAY FROM Observer (to a message broker) when:**
- Subscribers are in different processes or services — use Kafka or RabbitMQ instead of
  in-process EventBus.
- You need at-least-once delivery guarantees across restarts — in-process EventBus loses
  events on JVM crash; use a durable queue.
- The number of event types exceeds ~20 — Guava EventBus uses runtime type dispatch which
  becomes hard to audit; switch to explicit Kafka topics with schema registry.

---

        v
  [ OrderService ]
        |
        | ApplicationEventPublisher.publishEvent(OrderPlacedEvent)
        |
        v
  [ Spring ApplicationContext Event Bus ]
        |
   +----+----+----+----+
   |    |    |    |    |
   v    v    v    v    v
[Inv] [Bil] [Email] [Ship] [Analytics]
 svc   svc   svc     svc    svc
  |     |     |       |       |
  DB   PSP  SMTP    3PL     DWH

 @TransactionalEventListener(phase=AFTER_COMMIT)
 ensures observers fire only after TX commits
 — no phantom events on rollback
```

### Code 1: Spring `@TransactionalEventListener` (Java 17 LTS, Spring Boot 3.x)

```java
// Event record (Java 16+ record, immutable value object)
public record OrderPlacedEvent(
    String orderId,
    String customerId,
    BigDecimal amount,
    String currency,
    Instant occurredAt
) {}

// Publisher — OrderService fires the event INSIDE the transaction
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order placeOrder(PlaceOrderRequest request) {
        Order order = Order.create(request);
        orderRepository.save(order);  // persisted but TX not yet committed

        // Event published to ApplicationContext; AFTER_COMMIT listeners
        // will not receive this if the TX rolls back.
        eventPublisher.publishEvent(new OrderPlacedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getAmount(),
            order.getCurrency(),
            Instant.now()
        ));

        return order;
    }
}

// Inventory listener — fires AFTER DB commit, so order is visible to all readers
@Component
@Slf4j
public class InventoryReservationListener {

    private final InventoryService inventoryService;

    public InventoryReservationListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("orderEventExecutor")   // off the main request thread
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Reserving inventory for order={}", event.orderId());
        inventoryService.reserve(event.orderId());
    }
}

// Analytics listener — also AFTER_COMMIT, separate thread pool
@Component
public class AnalyticsListener {

    private final AnalyticsClient analyticsClient;

    public AnalyticsListener(AnalyticsClient analyticsClient) {
        this.analyticsClient = analyticsClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("analyticsExecutor")
    public void onOrderPlaced(OrderPlacedEvent event) {
        analyticsClient.track("order_placed", Map.of(
            "order_id", event.orderId(),
            "amount",   event.amount(),
            "currency", event.currency()
        ));
    }
}

// Async executor configuration
@Configuration
public class AsyncConfig {

    @Bean("orderEventExecutor")
    public Executor orderEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("order-event-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### Code 2: Guava `EventBus` with `@Subscribe` and Dead-Event Handling

```java
// Guava EventBus (guava 32.x) — synchronous in-process event bus
// Use AsyncEventBus for async dispatch to an Executor
@Configuration
public class EventBusConfig {

    @Bean
    public EventBus orderEventBus() {
        return new EventBus(
            // Dead-event handler: fires when no subscriber handles an event type
            (exception, context) -> log.error(
                "EventBus exception in subscriber={} on event={}",
                context.getSubscriber(),
                context.getEvent(),
                exception
            )
        );
    }
}

// Subscriber — register with the EventBus
@Component
public class BillingSubscriber {

    private final BillingService billingService;
    private final EventBus eventBus;

    public BillingSubscriber(BillingService billingService, EventBus eventBus) {
        this.billingService = billingService;
        this.eventBus = eventBus;
        // Register on construction; Spring manages lifecycle
        this.eventBus.register(this);
    }

    @PreDestroy
    public void destroy() {
        // Critical: unregister on bean destruction to avoid memory leak
        eventBus.unregister(this);
    }

    @Subscribe  // Guava routes events by parameter type
    public void onOrderPlaced(OrderPlacedEvent event) {
        billingService.charge(event.orderId(), event.amount(), event.currency());
    }

    // Dead-event handler: catches any unhandled event type
    @Subscribe
    public void onDeadEvent(DeadEvent deadEvent) {
        log.warn("No subscriber for event type={}", deadEvent.getEvent().getClass().getName());
    }
}
```

### Famous Codebase Usages

| Framework / Library | Class / Method | Notes |
|---|---|---|
| **Java SDK** | `java.beans.PropertyChangeSupport` | `firePropertyChange()` notifies all `PropertyChangeListener`s; used in JavaBeans/Swing data binding |
| **Java Swing/AWT** | `AbstractButton.addActionListener()` | Every `addXxxListener()` = `attach()`; listener list = `EventListenerList` (thread-safe) |
| **Spring Framework** | `SimpleApplicationEventMulticaster.multicastEvent()` | Synchronous default; becomes async if `taskExecutor` is set |
| **RxJava** | `Observable.subscribe(Observer)` | Reactive Observer with `onNext`, `onError`, `onComplete`; adds backpressure via `Flowable` |
| **Reactor** | `Flux.subscribe(CoreSubscriber)` | Project Reactor's reactive Observer; `publishOn` / `subscribeOn` control thread dispatch |
| **Guava** | `EventBus.register()` / `EventBus.post()` | Routes by event type; `AsyncEventBus` posts to an `Executor` |
| **Hibernate** | `@EntityListeners`, `@PostPersist` | Entity lifecycle Observer; used in auditing (`@CreatedDate`, `@LastModifiedDate`) |
| **Android** | `LiveData.observe(LifecycleOwner, Observer)` | Lifecycle-aware Observer; auto-unregisters on DESTROYED to prevent leaks |

### Anti-Patterns with Broken and Fix

**Anti-Pattern 1: Observer Never Unregistered (Memory Leak)**

```java
// BROKEN: Subject holds a strong reference to Observer.
// If Observer is a UI component or short-lived bean, it will never be GC'd
// as long as Subject (long-lived singleton) holds the reference.
public class MetricsSubject {
    private final List<MetricsObserver> observers = new ArrayList<>();

    public void addObserver(MetricsObserver o) {
        observers.add(o);   // strong reference — Observer is never GC'd
    }
    // No removeObserver() method provided
}

// FIX 1: Provide removeObserver() and call it in the component's lifecycle hook
public class MetricsSubject {
    private final List<MetricsObserver> observers = new CopyOnWriteArrayList<>();

    public void addObserver(MetricsObserver o)    { observers.add(o); }
    public void removeObserver(MetricsObserver o) { observers.remove(o); }
}

// In the Observer (Spring bean):
@Component
public class DashboardWidget implements MetricsObserver, DisposableBean {
    private final MetricsSubject subject;

    public DashboardWidget(MetricsSubject subject) {
        this.subject = subject;
        subject.addObserver(this);
    }

    @Override
    public void destroy() {
        subject.removeObserver(this);  // explicit cleanup on Spring context shutdown
    }
}

// FIX 2: WeakReference in the observer list so GC can collect unreachable Observers
private final List<WeakReference<MetricsObserver>> observers = new CopyOnWriteArrayList<>();

public void notifyAll(Metric m) {
    observers.removeIf(ref -> ref.get() == null);  // purge dead refs
    observers.stream()
             .map(WeakReference::get)
             .filter(Objects::nonNull)
             .forEach(o -> o.onMetric(m));
}
```

**Anti-Pattern 2: Observer Modifies Subject State Mid-Notification (`ConcurrentModificationException`)**

```java
// BROKEN: notifyObservers iterates observers list; one Observer calls
// subject.removeObserver(itself) inside update() => ConcurrentModificationException
public void notifyObservers() {
    for (Observer o : observers) {   // plain ArrayList — not thread-safe
        o.update(this);              // Observer calls removeObserver() here => CME
    }
}

// FIX: Copy the list before iterating (snapshot), or use CopyOnWriteArrayList
// Option A — snapshot copy
public void notifyObservers() {
    List<Observer> snapshot = new ArrayList<>(observers);  // O(n) copy
    for (Observer o : snapshot) {
        o.update(this);   // safe: iterating the snapshot, not the live list
    }
}

// Option B — CopyOnWriteArrayList (zero-copy read, O(n) write)
// Best when reads (notifications) far outnumber writes (register/unregister)
private final List<Observer> observers = new CopyOnWriteArrayList<>();
// No change needed in notifyObservers(); COW guarantees safe iteration
```

**Anti-Pattern 3: Synchronous Observer Doing Slow I/O Blocks Notification Thread**

```java
// BROKEN: Email observer sends SMTP inline; a 500ms SMTP handshake stalls
// every other observer and blocks the caller's thread for the duration.
@Component
public class EmailObserver implements OrderObserver {
    @Override
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailService.sendConfirmation(event.customerId()); // ~500ms blocking SMTP call
    }
}

// FIX: Use @Async so the email dispatch runs in a separate thread pool.
// The main notification loop returns in <1ms; email is fire-and-forget.
@Component
public class EmailObserver implements OrderObserver {

    @Async("orderEventExecutor")   // Spring Boot 3.x thread pool bean
    @Override
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailService.sendConfirmation(event.customerId());
        // runs in orderEventExecutor thread pool; caller returns immediately
    }
}
// Result: notification latency drops from ~800ms to ~2ms
// Email failures do not affect inventory or billing observers
```

### Performance Numbers

| Measurement | Value | Notes |
|---|---|---|
| Guava `EventBus.post()` throughput | ~1,000,000 events/sec | Single-threaded, no I/O, synthetic benchmark |
| Spring `ApplicationEventPublisher.publishEvent()` | ~500,000 events/sec | Synchronous multicaster, no async executor |
| `@Async` dispatch overhead | ~2ms | Thread pool handoff latency at low contention |
| Synchronous SMTP observer latency | ~800ms | Blocks entire notification chain |
| `CopyOnWriteArrayList` read (no lock) | ~10ns | Best for read-heavy observer lists |
| `CopyOnWriteArrayList` write (copy) | O(n) | ~1µs for 100 observers — acceptable for infrequent register/unregister |
| `WeakReference.get()` overhead | ~1ns | Negligible; use when Observers may be GC'd |

### Migration Story

**Adopt Observer when:**
- 2 or more independent consumers react to the same state change (e.g., inventory + billing + email all care about "order placed")
- The publisher should not know the identity of its consumers
- You need to add consumers at runtime without modifying the publisher

**Migrate away from Observer when:**
- Consumers need guaranteed ordering, exactly-once delivery, or durability — use a message broker (Kafka, RabbitMQ) instead
- Event fan-out crosses process boundaries — replace `EventBus` with an outbox pattern + async messaging
- Observer graph becomes cyclic (A notifies B, B notifies A) — switch to a mediator or event-driven state machine

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
