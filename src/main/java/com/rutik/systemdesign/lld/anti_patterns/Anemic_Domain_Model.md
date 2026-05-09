# Anti-Pattern: Anemic Domain Model

## What It Is

The Anemic Domain Model (ADM) anti-pattern describes a domain model where the domain objects (entities) contain only data — fields and getters/setters — with no business logic. All business logic is pushed into a separate layer of service classes that operate on these data containers.

The term was coined by Martin Fowler, who described it as "the anti-pattern of creating a domain model with no behavior" and noted it directly contradicts the fundamental principles of object-oriented design.

The contrast:
- **Anemic Domain Model**: `Order` has fields. `OrderService` has all the business logic that operates on `Order`.
- **Rich Domain Model**: `Order` knows how to place itself, cancel itself, calculate its total, and enforce its own invariants.

---

## Intuition

> **One-line analogy**: Anemic Domain Model is like a recipe book where the ingredients have no instructions — data sits in one place, logic sits elsewhere, and nothing is self-contained.

**Mental model**: You have an `Order` class with 15 fields but zero methods beyond getters/setters. All the "what can an order do?" logic lives in `OrderService`, `OrderValidator`, `OrderCalculator`. The object is a dumb data bag. This violates OOP's core promise: objects should combine data and behavior. When you split them, you end up with procedural code wearing an OOP costume.

**Why it matters**: Anemic models lead to duplicated business logic scattered across service classes, no single source of truth for domain rules, and objects that can be put into invalid states because they don't enforce their own invariants.

**Key insight**: Ask "can this object tell me if it's valid?" and "can it transition itself to the next state?" If the answer is always "no, a service does that," you have an anemic model. The fix is to move behavior back into the entity where it belongs.

---

## How to Recognize It

**Code smells:**
- Domain classes contain only fields, getters, and setters (essentially POJOs / data transfer objects)
- Service classes are named after verbs: `OrderService`, `UserService`, `InvoiceService`, `AccountService`
- Service methods take a domain object as a parameter and manipulate it externally
- Domain invariants (e.g., "an order cannot be cancelled after it ships") live in a service, not in the entity
- The domain model looks like a database schema mapped to Java classes

**Example — The Anti-Pattern:**

```java
// Pure data container — no behavior
public class Order {
    private Long id;
    private Long customerId;
    private List<OrderItem> items;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime placedAt;
    private LocalDateTime shippedAt;
    private String shippingAddress;

    // Only getters and setters — no intelligence
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    // ... more getters/setters
}

// All the intelligence lives here instead
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;

    // Business logic that belongs in Order lives here
    public Order placeOrder(Long customerId, List<OrderItem> items, String address) {
        // Domain invariant check — should be inside Order
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }

        // Total calculation — should be inside Order
        BigDecimal total = items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setItems(new ArrayList<>(items));
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(total);
        order.setPlacedAt(LocalDateTime.now());
        order.setShippingAddress(address);

        paymentService.authorize(customerId, total);
        inventoryService.reserve(items);
        return orderRepository.save(order);
    }

    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found"));

        // Cancellation policy — should be inside Order
        if (order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel an order that has already shipped");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        inventoryService.release(order.getItems());
        orderRepository.save(order);
    }

    public void addItem(Long orderId, OrderItem newItem) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found"));

        // Another invariant in the wrong place
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Can only add items to pending orders");
        }

        order.getItems().add(newItem);
        // Recalculate total — again, belongs in Order
        BigDecimal newTotal = order.getItems().stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(newTotal);
        orderRepository.save(order);
    }
}
```

Problems visible above:
- `Order` cannot protect its own invariants — any code can call `setStatus(SHIPPED)` directly
- The total calculation is duplicated — it appears in both `placeOrder` and `addItem`
- Business rules ("cannot cancel after shipping") are scattered across service methods
- `Order` is just a data bag — it provides no encapsulation

---

## Why It Happens

1. **ORM influence**: JPA/Hibernate entities need a no-arg constructor and getter/setter pairs. Developers treat them as pure data structures.
2. **Procedural habits in OO clothing**: Developers with a procedural background write procedures (services) that operate on data structures (entities).
3. **Transaction Script pattern as default**: Many tutorials show the Transaction Script pattern — one method per use case — which naturally produces anemic models.
4. **Misunderstanding MVC**: "Model" in MVC is sometimes interpreted as just data, not a rich behavioral object.
5. **Fear of "fat models"**: Developers overcompensate against God Objects and strip all logic from entities.

---

## Why It's Harmful

1. **No encapsulation**: `setStatus()` is public — any code can put an Order into an illegal state. The entity cannot protect itself.
2. **Duplicated business logic**: Total calculation, validation, and invariant checks end up copy-pasted across multiple service methods.
3. **Business logic is hard to find**: "Where is the order cancellation rule?" — it could be in any of several service classes.
4. **Violates Tell Don't Ask**: Instead of telling `order.cancel()`, you ask `order.getStatus()`, check it, then call `order.setStatus(CANCELLED)`. The object does not participate in the operation.
5. **Services become God Objects**: With no behavior in the model, all logic must go somewhere — services grow large.
6. **Harder to test domain logic**: Testing `OrderService.cancelOrder()` requires setting up the full service with all its dependencies. Testing `order.cancel()` requires only an `Order`.

---

## How to Fix It

Move behavior into the domain object. The entity should enforce its own invariants and expose meaningful operations rather than raw setters.

```java
// Rich Domain Model: Order knows its own rules
public class Order {

    private final Long id;
    private final Long customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final LocalDateTime placedAt;
    private LocalDateTime shippedAt;
    private final String shippingAddress;

    // Factory method — enforces creation invariants
    public static Order place(Long customerId, List<OrderItem> items, String shippingAddress) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        if (shippingAddress == null || shippingAddress.isBlank()) {
            throw new IllegalArgumentException("Shipping address is required");
        }
        return new Order(null, customerId, new ArrayList<>(items),
                         OrderStatus.PENDING, LocalDateTime.now(), shippingAddress);
    }

    // Business operation — enforces its own rules
    public void cancel() {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                "Cannot cancel order " + id + ": already " + status);
        }
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order " + id + " is already cancelled");
        }
        this.status = OrderStatus.CANCELLED;
    }

    // Business operation — encapsulates the rule about when items can be added
    public void addItem(OrderItem item) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot add items to order " + id + " with status " + status);
        }
        items.add(item);
    }

    // Behavior lives here — calculation is encapsulated and never duplicated
    public BigDecimal calculateTotal() {
        return items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void markShipped() {
        if (status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed orders can be shipped");
        }
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = LocalDateTime.now();
    }

    // No setStatus() — state transitions are explicit, named operations
    // Getters are fine for reading state
    public Long getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public BigDecimal getTotal() { return calculateTotal(); }
}

// Service is now thin — coordinates infrastructure, does not own business rules
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;

    public Order placeOrder(Long customerId, List<OrderItem> items, String address) {
        // Business rule enforcement is in Order.place()
        Order order = Order.place(customerId, items, address);

        // Service coordinates external systems
        paymentService.authorize(customerId, order.getTotal());
        inventoryService.reserve(items);
        return orderRepository.save(order);
    }

    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found"));

        // Business rule is in order.cancel() — service just coordinates
        order.cancel();
        inventoryService.release(order.getItems());
        orderRepository.save(order);
    }
}

// Domain logic is now easy to unit test — no service dependencies needed
class OrderTest {

    @Test
    void cancel_throwsIfOrderAlreadyShipped() {
        Order order = Order.place(1L, sampleItems(), "123 Main St");
        order.confirm();
        order.markShipped();

        assertThrows(IllegalStateException.class, order::cancel);
    }

    @Test
    void calculateTotal_sumsItemPricesCorrectly() {
        List<OrderItem> items = List.of(
            new OrderItem("Widget", BigDecimal.valueOf(10.00), 2),
            new OrderItem("Gadget", BigDecimal.valueOf(25.00), 1)
        );
        Order order = Order.place(1L, items, "123 Main St");
        assertEquals(new BigDecimal("45.00"), order.calculateTotal());
    }
}
```

---

## Real-World Examples

- **JPA Entity classes with all getters/setters**: Almost every beginner Spring tutorial produces anemic entities.
- **DTO objects promoted to domain model**: Developers use DTOs as their domain objects because it is simpler — but DTOs are meant for data transfer, not domain logic.
- **Generated code from database schema**: Tools that reverse-engineer a DB schema into Java classes produce purely anemic models by construction.
- **CRUD applications**: Simple CRUD apps can get away with anemic models, but the pattern becomes harmful when business logic grows.

---

## Prevention Strategies

1. **Identify domain invariants first**: Before writing a class, list the rules it must enforce. Those rules belong inside the class.
2. **Use factory methods instead of public constructors**: `Order.place(...)` forces creation through a validated path.
3. **Replace setters with named operations**: Instead of `setStatus()`, have `cancel()`, `confirm()`, `markShipped()`.
4. **Tell, Don't Ask principle**: If you find yourself asking an object for its state to decide what to do, consider if the object should make that decision itself.
5. **Test domain logic without services**: If your domain logic can only be tested through services, it is in the wrong place.
6. **Domain-Driven Design**: DDD's concept of Aggregates, Value Objects, and Entities directly addresses this — study it.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Anemic Domain Model Appears in Distributed Systems**

- **Pure CRUD microservices** — A service that exposes only `GET /orders`, `POST /orders`, `PUT /orders/{id}` with no domain logic is an Anemic Microservice. All the "what are the rules for placing an order?" logic lives in the API layer or in a client — scattered and duplicated.
- **God Service emergence** — When domain entities are anemic, all logic must live somewhere. It concentrates in one "orchestration service" that calls all other services and implements all business rules — this God Service becomes a bottleneck and a deployment hazard.
- **Tight inter-service coupling** — Anemic services that expose raw data force consuming services to implement business rules about that data. Service A must understand Order's invariants to work with Order Service's raw data — coupling that proper domain modeling would eliminate.
- **Event sourcing mismatch** — Event sourcing requires entities that know how to apply events and validate transitions. Anemic entities can't enforce invariants before emitting events — invalid state changes leak into the event log, corrupting the system history.

---

## Interview Relevance

**Common interview questions:**
- "What is the Anemic Domain Model and why is it considered an anti-pattern?" — Direct question, common in senior engineering interviews.
- "What is the Tell Don't Ask principle?" — ADM is the canonical violation.
- "How would you design an Order entity in a rich domain model?" — Practical design question.
- "What is the difference between a domain object and a DTO?" — Tests understanding of where behavior belongs.

**Key talking points:**
- Cite Martin Fowler — this is his term, and name-dropping the source shows depth
- Contrast with rich domain model — the fix is not a new class, it is moving logic into the existing entity
- Explain encapsulation: the entity should protect its own invariants
- Mention testability: domain logic in the entity is testable without infrastructure
- Note the "Tell Don't Ask" principle as the guiding heuristic
