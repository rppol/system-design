# Program to Interface, Not Implementation

## Origins

The **first design principle** stated in the Gang of Four's "Design Patterns: Elements of Reusable Object-Oriented Software" (1994):

> "Program to an interface, not an implementation."

The GoF observed that designs which depend on concrete classes are inherently rigid — they cannot be changed without modifying the code that depends on them. Designs that depend on abstractions remain flexible because the abstraction (the contract) can be satisfied by any number of concrete implementations.

---

## Intuition

> **One-line analogy**: Programming to an interface is like using standard electrical outlets — you plug in any device that follows the standard, not just devices from one manufacturer.

**Mental model**: `List<String> names = new ArrayList<>()` declares names as `List` (interface) even though you create `ArrayList` (concrete). Later, if you need a `LinkedList` (for frequent insertions), you change one line — the creation — not every usage. The code that uses `names` doesn't know or care whether it's an ArrayList or LinkedList; it just knows it's a `List` and can call list operations.

**Why it matters**: Code that depends on concrete classes is tightly coupled to implementation details — changing the implementation requires changing all the code that depends on it. Code that depends on interfaces is coupled only to contracts — implementation changes are invisible to dependent code. This is what makes large systems maintainable and testable.

**Key insight**: This principle is what makes Dependency Injection work — Spring injects the concrete implementation at runtime, while your code declares dependencies as interface types. It's also why `Mockito.mock(UserRepository.class)` works — mock implements the interface, satisfying all dependencies without real database connections.

---

## Definition

When writing code, declare variables, parameters, and return types using **interface or abstract class types**, not concrete class types. Depend on **what something does** (its contract), not **what something is** (its concrete class).

More broadly: the objects you collaborate with should be known to you only through their interface — the set of messages they respond to — not through their concrete implementation details.

---

## Motivation

- **Flexibility:** if code depends on `List`, you can pass `ArrayList`, `LinkedList`, `CopyOnWriteArrayList`, or any future `List` implementation. If it depends on `ArrayList`, you are locked in.
- **Testability:** you can substitute a mock or stub that implements the interface in tests, isolating the class under test.
- **Replaceability:** swap one implementation for another without changing the client code.
- **Separation of concerns:** the client doesn't need to know (and shouldn't care) about the concrete behavior details of its collaborators.

---

## Java Violation Example: The Simple Case

```java
// Violation: declaring with concrete type
ArrayList<String> names = new ArrayList<>();
names.add("Alice");

// Every method that receives this variable must also accept ArrayList,
// locking the caller into knowing about the concrete type.
public void processNames(ArrayList<String> names) { ... }
```

```java
// Compliant: declaring with interface type
List<String> names = new ArrayList<>();
names.add("Alice");

// The method accepts any List implementation
public void processNames(List<String> names) { ... }
```

The concrete type (`ArrayList`) is mentioned once, at the point of construction. Everywhere else, only the abstraction (`List`) is used.

---

## Java Violation Example: The Bigger Problem

The simple case above is a minor concern. The **real, consequential violation** is when service classes directly instantiate their dependencies:

```java
// OrderService.java — directly depends on concrete EmailNotificationService
public class OrderService {

    // Violation: hardcoded dependency on concrete class
    private EmailNotificationService notificationService = new EmailNotificationService();
    private MySQLOrderRepository repository = new MySQLOrderRepository("jdbc:mysql://localhost/orders");

    public void placeOrder(Order order) {
        repository.save(order);
        notificationService.sendConfirmation(order);
    }
}
```

**Problems:**
- Cannot test `OrderService` in isolation — it always creates real email and database connections.
- Cannot swap `MySQLOrderRepository` for a `PostgreSQLOrderRepository` without modifying `OrderService`.
- Cannot add a `SlackNotificationService` alongside email without modifying `OrderService`.
- `OrderService` depends on the concrete construction details of its collaborators.

---

## Compliant Example: Dependency Injection + Interfaces

```java
// Define interfaces — the contracts
public interface NotificationService {
    void sendConfirmation(Order order);
}

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(String id);
}

// Concrete implementations
public class EmailNotificationService implements NotificationService {
    public void sendConfirmation(Order order) {
        // Send email
    }
}

public class SlackNotificationService implements NotificationService {
    public void sendConfirmation(Order order) {
        // Send Slack message
    }
}

public class MySQLOrderRepository implements OrderRepository {
    public void save(Order order) { /* MySQL */ }
    public Optional<Order> findById(String id) { /* MySQL */ }
}

// OrderService depends ONLY on interfaces — never on concrete classes
public class OrderService {

    private final NotificationService notificationService;
    private final OrderRepository repository;

    // Dependencies are injected — not created here
    public OrderService(NotificationService notificationService, OrderRepository repository) {
        this.notificationService = notificationService;
        this.repository = repository;
    }

    public void placeOrder(Order order) {
        repository.save(order);
        notificationService.sendConfirmation(order);
    }
}

// In a test:
OrderService service = new OrderService(
    mock(NotificationService.class),  // no real emails in tests
    mock(OrderRepository.class)        // no real database in tests
);

// In production with Spring:
// @Autowired automatically injects the registered implementation
```

`OrderService` has zero knowledge of what concrete class satisfies its dependencies. Any class implementing the interface will work.

---

## Benefits in Detail

### Testability

With interface-based dependencies:
- Replace real implementations with mocks/stubs in tests.
- Test `OrderService` logic without a database or email server.
- Verify that `notificationService.sendConfirmation(order)` was called without actually sending an email.

### Flexibility — Swapping Implementations

```java
// Switch from MySQL to PostgreSQL: one line changes at composition root
OrderRepository repository = new PostgreSQLOrderRepository(dataSource);
OrderService service = new OrderService(notificationService, repository);
// OrderService code: unchanged
```

### Extensibility (Open/Closed Principle)

Add a new notification channel without modifying `OrderService`:
```java
// Composite notification — notifies via multiple channels
public class CompositeNotificationService implements NotificationService {
    private final List<NotificationService> services;

    public CompositeNotificationService(List<NotificationService> services) {
        this.services = services;
    }

    public void sendConfirmation(Order order) {
        services.forEach(s -> s.sendConfirmation(order));
    }
}
// OrderService: unchanged
```

---

## Interface vs Abstract Class: Decision Guide

| Use Interface When | Use Abstract Class When |
|-------------------|------------------------|
| You want multiple unrelated classes to implement the contract | You have a partial implementation to share |
| You need to implement multiple contracts (Java's multiple interface implementation) | You want to enforce a template algorithm (Template Method) |
| The contract is purely behavioral — no shared state | Subclasses share state or common initialization |
| You're defining a capability that cuts across the hierarchy | There is a genuine IS-A relationship in a closed hierarchy |
| You want the most flexibility for future change | You're designing an extension point in a framework |

In practice: **default to interfaces**. Reach for abstract classes only when you have shared implementation to offer.

---

## How This Principle Enables Design Patterns

Nearly every design pattern depends on this principle:

| Pattern | How it uses Program to Interface |
|---------|----------------------------------|
| **Strategy** | Client holds a `Strategy` interface; concrete strategies are interchangeable. |
| **Observer** | Subject holds a list of `Observer` interfaces; any observer can be registered. |
| **Decorator** | Decorator implements the same interface as the wrapped component. |
| **Factory Method** | Returns an interface type; the concrete class created is hidden. |
| **Command** | Invoker holds `Command` interface; any command can be executed or queued. |
| **Proxy** | Proxy implements the same interface as the real subject. |
| **Composite** | Leaf and Composite both implement the same `Component` interface. |

Without programming to interfaces, none of these patterns achieve their flexibility. The interface IS the seam that makes patterns work.

---

## Related Principles

- **Dependency Inversion Principle (SOLID-D):** "High-level modules should not depend on low-level modules. Both should depend on abstractions." DIP is the formal SOLID statement of this principle.
- **Dependency Injection:** the mechanism by which you provide concrete implementations to code that depends on interfaces. DI frameworks (Spring, Guice) automate the wiring.
- **Open/Closed Principle (SOLID-O):** once you program to interfaces, you can add new implementations (extend) without modifying the client code (closed for modification).

---

## Cross-Perspective: HLD Connections

**HLD View — Where Program to Interface Appears in Distributed Systems**

- **OpenAPI / Protobuf contracts** — Services depend on API specifications, not on specific service implementations. The contract (interface) is the artifact; the implementation can change without breaking consumers — the foundational principle of microservice interoperability.
- **Storage port abstraction** — Hexagonal architecture: application code depends on `StoragePort` and `MessagePort` interfaces; infrastructure adapters implement them. Swapping from MySQL to DynamoDB means writing a new adapter, not touching business logic.
- **Feature toggle abstraction** — Code depends on a `FeatureFlag` interface rather than a specific feature-flag SDK (LaunchDarkly, Split). Migrating SDKs means writing a new adapter — the application code is unchanged.
- **Cloud-agnostic infrastructure** — CDK/Pulumi abstractions let infrastructure code program to `Bucket`, `Queue`, and `Function` interfaces. Cloud-specific implementations are injected per deployment environment — program to the abstraction, not the AWS/GCP SDK directly.

---

## Quick Summary

| Aspect | Summary |
|--------|---------|
| Core idea | Depend on what something does (interface), not what it is (concrete class) |
| Simple case | `List<String>` not `ArrayList<String>` |
| Big case | Inject `NotificationService` interface, not `new EmailNotificationService()` |
| Benefits | Testability (mocks), flexibility (swap impl), extensibility (new impl without change) |
| Interface vs abstract class | Default to interface; use abstract class for shared implementation |
| Enables | Every structural and behavioral design pattern |
| Related | Dependency Inversion Principle, Dependency Injection, Open/Closed Principle |
