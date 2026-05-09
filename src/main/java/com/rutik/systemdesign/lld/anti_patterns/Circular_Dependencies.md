# Circular Dependencies Anti-Pattern

## Overview

Circular Dependencies occur when two or more modules, packages, or classes form a dependency cycle — A depends on B, B depends on C, and C depends back on A. The result is a tightly coupled cluster that behaves as a single indivisible unit, even though it appears to be modular. The anti-pattern is insidious because the cycle is not always obvious from reading a single file; it emerges from the aggregate of import statements and constructor parameters across the system. Circular dependencies destroy modularity: no part of the cycle can be compiled, deployed, or tested without the entire cycle being present. In Java specifically, they cause class initialization ordering problems, Spring bean creation failures, and unit test impossibility. At the architecture level, circular dependencies between packages or services are a signal that the system's boundaries are incorrectly drawn and that shared concepts have not been properly identified and extracted.

---

## Intuition

> **One-line analogy**: Circular dependencies are like two people each waiting for the other to arrive first — nothing can start without the other, so neither starts.

**Mental model**: `OrderService` needs `UserService` to look up the customer. `UserService` needs `OrderService` to list a user's orders. Neither can be created without the other. Spring throws `BeanCurrentlyInCreationException`. Tests can't instantiate either class cleanly. The two classes have merged into one logical blob wearing two names — all the disadvantages of a God Object with none of the obvious warning signs.

**Why it matters**: Cycles collapse modularity. You cannot compile, test, or deploy one side of the cycle independently. Every change to either class forces consideration of the other. Deployment ordering becomes undefined.

**Key insight**: Circular dependencies are a design boundary problem, not a coding problem. The fix is almost always to extract the shared concept that both classes depend on into a third module — breaking the cycle by introducing the abstraction that was missing.

---

## How to Spot It

**Warning Signs and Code Smells**

- Class A's import list includes a class from package B, and class B's import list includes a class from package A
- Spring application context fails to start with `BeanCurrentlyInCreationException` — a direct symptom of circular bean dependencies
- Unit tests for class A require instantiating class B, which requires class A — a test setup loop with no clean entry point
- Two (or more) services that seem to "know too much" about each other: `UserService` has methods that clearly belong to `OrderService` and vice versa
- Architects describe the design as "everything connects to everything" or draw dependency diagrams with bidirectional arrows
- Deployment ordering cannot be determined: "which service do we start first?"
- A refactor in `ClassA` always forces a change in `ClassB` and then back in `ClassA`
- SonarQube, JDepend, or IntelliJ's dependency analysis reports a cycle
- The `@Lazy` annotation is used on Spring bean injections to "fix" startup failures (a workaround that masks the underlying cycle)
- Methods in class A take a parameter of type B and methods in class B take a parameter of type A

---

## Dependency Diagrams

**Before: Circular Dependency (problematic)**

```
  +─────────────────+          +─────────────────+
  │   UserService   │ ───────> │  OrderService   │
  │                 │          │                 │
  │ getOrderCount() │          │ getUser()       │
  │ notifyUser()    │ <─────── │ createOrder()   │
  +─────────────────+          +─────────────────+
           ^                            |
           |                            |
           +──────────────+             |
                          |             v
                  +─────────────────────────────+
                  │      LoyaltyService         │
                  │  awardPoints(userId, order) │
                  +─────────────────────────────+
                  (depends on both UserService and OrderService,
                   which depend on each other — a 3-node cycle)
```

**After: Acyclic Dependency (correct)**

```
  +────────────────+     +────────────────+     +────────────────+
  │   UserService  │     │  OrderService  │     │ LoyaltyService │
  │                │     │                │     │                │
  │ getUser()      │     │ createOrder()  │     │ awardPoints()  │
  │ updateUser()   │     │ getOrders()    │     │                │
  +────────────────+     +────────────────+     +────────────────+
         ^                      ^                       ^
         |                      |                       |
         +──────────────────────+───────────────────────+
                                |
                    +───────────────────────+
                    │   OrderEventPublisher │  (shared abstraction)
                    │                       │
                    │ publish(OrderEvent)   │
                    +───────────────────────+
                    (services communicate via events,
                     not direct method calls — cycle eliminated)
```

---

## Java Violation Example

```java
// =========================================================================
// CIRCULAR DEPENDENCY EXAMPLE
// UserService <-> OrderService form a hard cycle.
// Neither can be instantiated or tested without the other.
// =========================================================================

// -------------------------------------------------------------------------
// com.example.user.UserService
// -------------------------------------------------------------------------
package com.example.user;

import com.example.order.OrderService;  // UserService imports OrderService

@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrderService orderService;  // Direct dependency on OrderService

    // Spring will fail to create this bean because OrderService also requires UserService.
    // Error: "The dependencies of some of the beans in the application context
    // form a cycle: userService -> orderService -> userService"
    public UserService(UserRepository userRepository, OrderService orderService) {
        this.userRepository = userRepository;
        this.orderService = orderService;
    }

    public User getUser(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }

    // This method calls back into OrderService — it's the reason the cycle exists.
    // Business logic: "Get the order count to decide the user's tier."
    // The question is: why does UserService need to know about orders?
    public UserTier getUserTier(String userId) {
        int orderCount = orderService.getOrderCount(userId);  // calls OrderService
        if (orderCount >= 10) return UserTier.GOLD;
        if (orderCount >= 3)  return UserTier.SILVER;
        return UserTier.BRONZE;
    }

    // Another cycle-causing method: notifying a user after an order is placed.
    // This should not live in UserService — it belongs in a notification or order handler.
    public void notifyUserOfOrderConfirmation(String userId, String orderId) {
        User user = getUser(userId);
        // ... send email
        System.out.println("Notifying " + user.getEmail() + " about order " + orderId);
    }
}

// -------------------------------------------------------------------------
// com.example.order.OrderService
// -------------------------------------------------------------------------
package com.example.order;

import com.example.user.UserService;  // OrderService imports UserService — the cycle

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;  // Direct dependency on UserService

    public OrderService(OrderRepository orderRepository, UserService userService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
    }

    // This method causes the cycle: it needs to look up the user before creating an order.
    // The user lookup belongs OUTSIDE the order creation, at the application/controller layer.
    public Order createOrder(String userId, List<OrderItem> items) {
        User user = userService.getUser(userId);  // calls UserService — the cycle entry point

        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        Order order = new Order(userId, items);
        orderRepository.save(order);

        // After saving, it calls BACK into UserService to trigger notification.
        // This makes the cycle bidirectional and creates a call graph loop.
        userService.notifyUserOfOrderConfirmation(userId, order.getId());

        return order;
    }

    public int getOrderCount(String userId) {
        return orderRepository.countByUserId(userId);
    }
}

// =========================================================================
// ADDITIONAL CYCLE EXAMPLE: Circular static initialization
// This causes a harder-to-diagnose problem at class loading time.
// =========================================================================

public class ConfigA {
    // When ConfigA is loaded, it accesses ConfigB.VALUE.
    // If ConfigB hasn't finished loading yet, this reads the default (0), not the intended value.
    public static final int TIMEOUT = ConfigB.BASE_TIMEOUT * 2;
}

public class ConfigB {
    // When ConfigB is loaded, it accesses ConfigA.TIMEOUT.
    // ClassLoader deadlock or incorrect value (0) depending on load order.
    public static final int BASE_TIMEOUT = 100;
    public static final int EXTENDED = ConfigA.TIMEOUT + 500;  // reads 0 if ConfigA not loaded
}

// =========================================================================
// PACKAGE-LEVEL CYCLE (Architecture violation)
// =========================================================================

// com.example.user.User references com.example.order.Order
public class User {
    private List<Order> recentOrders;  // User knows about Order — package cycle
}

// com.example.order.Order references com.example.user.User
public class Order {
    private User customer;  // Order knows about User — package cycle completes
}
// Result: the 'user' package and 'order' package cannot be compiled or deployed independently.
```

**What is wrong with this code:**
- `UserService` and `OrderService` each require the other in their constructor — Spring cannot resolve the initialization order and throws `BeanCurrentlyInCreationException`
- The bidirectional call graph (UserService calls OrderService, which calls back into UserService) can create runtime loops
- No unit test can instantiate `UserService` without a real or mock `OrderService`, and vice versa — mocking both creates a circular mock setup
- `getUserTier()` lives in `UserService` but is really a derived fact computed from `OrderService` data — the responsibility is misplaced
- `notifyUserOfOrderConfirmation()` in `UserService` is an event handler that was placed in the wrong class
- Static initialization cycle in `ConfigA`/`ConfigB` causes incorrect constant values depending on class load order — a class of bug that is nearly impossible to reproduce in tests

---

## Why It's Harmful

**Spring Bean Initialization Failure**
Constructor injection with circular dependencies causes an immediate application startup failure. Field injection or `@Lazy` can mask it, but the underlying design problem remains — and lazy initialization defers the failure to runtime.

**Unit Testing Impossibility**
To unit test `UserService.getUserTier()`, you need a mock `OrderService`. To construct a mock `OrderService`, you may need a reference to `UserService`. Clean unit testing requires a dependency tree (DAG), not a dependency cycle.

**Deployment Ordering Cannot Be Defined**
In a microservices or modular architecture, if Service A depends on Service B which depends on Service A, there is no valid startup order. Both services must be started simultaneously with retry logic, adding operational complexity.

**Compilation Coupling**
At the package level, cycles mean that changing any class in the cycle requires recompiling all other classes in the cycle. This defeats the purpose of modularization and slows large codebases significantly.

**Change Amplification**
A change to `UserService` propagates to `OrderService` and potentially back to `UserService`. Every modification in the cycle requires reviewing all nodes in the cycle, regardless of which class the change targets.

**Hidden Semantic Problems**
Cycles are often symptoms of misplaced responsibilities. If `UserService` needs to call `OrderService`, someone put logic in the wrong class. The cycle is a signal, not just a technical constraint.

---

## Refactored Solution

```java
// =========================================================================
// SOLUTION 1: Introduce a shared event/mediator to break the cycle
// UserService and OrderService both become independent.
// Communication happens via a domain event — no direct dependency.
// =========================================================================

// -------------------------------------------------------------------------
// Shared domain event (no dependency on either service)
// -------------------------------------------------------------------------
public record OrderCreatedEvent(String orderId, String userId, Money total) {}

// -------------------------------------------------------------------------
// OrderService — no longer depends on UserService
// -------------------------------------------------------------------------
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;  // Spring's event bus

    public OrderService(OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    // userId is passed in from the caller (controller/application layer),
    // who already validated the user exists. OrderService doesn't need UserService.
    public Order createOrder(String userId, List<OrderItem> items) {
        Order order = new Order(userId, items);
        orderRepository.save(order);

        // Publish event — OrderService does not know who handles it
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), userId, order.getTotal()));

        return order;
    }

    public int getOrderCount(String userId) {
        return orderRepository.countByUserId(userId);
    }
}

// -------------------------------------------------------------------------
// UserService — no longer depends on OrderService
// -------------------------------------------------------------------------
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUser(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }

    public void updateUser(User user) {
        userRepository.save(user);
    }
}

// -------------------------------------------------------------------------
// UserNotificationHandler — listens for order events, handles notification
// Depends on UserService only (no cycle)
// -------------------------------------------------------------------------
@Component
public class UserNotificationHandler {

    private final UserService userService;
    private final EmailService emailService;

    public UserNotificationHandler(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        User user = userService.getUser(event.userId());
        emailService.sendOrderConfirmation(user.getEmail(), event.orderId());
    }
}

// -------------------------------------------------------------------------
// UserTierService — computes user tier by coordinating UserService + OrderService
// This is a new, focused class at a higher layer — it CAN depend on both
// because nothing depends back on it (no cycle introduced).
// -------------------------------------------------------------------------
@Service
public class UserTierService {

    private final UserService userService;
    private final OrderService orderService;

    public UserTierService(UserService userService, OrderService orderService) {
        this.userService = userService;
        this.orderService = orderService;
    }

    public UserTier getTierForUser(String userId) {
        userService.getUser(userId); // validates user exists
        int orderCount = orderService.getOrderCount(userId);
        if (orderCount >= 10) return UserTier.GOLD;
        if (orderCount >= 3)  return UserTier.SILVER;
        return UserTier.BRONZE;
    }
}

// =========================================================================
// SOLUTION 2: Introduce an interface to invert the dependency
// When event-based decoupling is not appropriate, invert with an interface.
// =========================================================================

// Interface defined in the 'user' package — OrderService depends on the interface,
// not on the concrete UserService. The cycle is broken.
public interface UserLookup {
    User findById(String userId);
}

// UserService implements the interface
@Service
public class UserService implements UserLookup {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User findById(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }
}

// OrderService depends on the interface, not on UserService
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserLookup userLookup;  // interface, not UserService

    public OrderService(OrderRepository orderRepository, UserLookup userLookup) {
        this.orderRepository = orderRepository;
        this.userLookup = userLookup;
    }

    public Order createOrder(String userId, List<OrderItem> items) {
        userLookup.findById(userId); // validates existence; throws if not found
        Order order = new Order(userId, items);
        return orderRepository.save(order);
    }
}
```

**What changed:**
- `UserService` and `OrderService` are now fully independent — each can be instantiated, tested, and deployed without the other
- Communication happens via `ApplicationEventPublisher` — `OrderService` publishes events, `UserNotificationHandler` consumes them
- `getUserTier()` moved to `UserTierService`, a coordinator class that lives at a higher layer and can depend on both without creating a cycle
- Dependency diagram is now a DAG (directed acyclic graph): `UserTierService` -> `UserService`, `OrderService`; `UserNotificationHandler` -> `UserService`; no cycles

---

## Prevention Strategies

**1. Use Dependency Analysis Tools in CI**

JDepend can be run as part of your build to detect package-level cycles:

```java
// JUnit test that fails the build if package cycles exist
@Test
public void noCyclicPackageDependenciesShouldExist() throws IOException {
    JDepend jdepend = new JDepend();
    jdepend.addDirectory("target/classes");
    jdepend.analyze();
    assertFalse("Cyclic dependencies found!", jdepend.containsCycles());
}
```

SonarQube's "Package Tangle Index" and "Cyclic Dependencies" rules provide continuous monitoring.

**2. Apply the Dependency Inversion Principle (DIP)**
High-level modules should not depend on low-level modules. Both should depend on abstractions. When two services need to communicate, define an interface in the shared or upstream module and have the downstream module implement it.

**3. Enforce the Acyclic Dependencies Principle (ADP)**
Robert C. Martin's ADP states: the dependency graph of packages must have no cycles. Make this a documented and enforced architectural rule. Include it in architecture fitness functions (e.g., ArchUnit tests).

```java
// ArchUnit: enforce no cyclic dependencies between packages
@AnalyzeClasses(packages = "com.example")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule noCycles =
        slices().matching("com.example.(*)..").should().beFreeOfCycles();
}
```

**4. Use Domain Events for Cross-Boundary Communication**
Services that need to react to each other's state changes should communicate via events (Spring Events, Kafka, RabbitMQ) rather than direct method calls. This eliminates the most common source of service-level cycles.

**5. Identify and Extract Shared Concepts**
When two modules form a cycle, it is often because they both know about a concept that belongs in a third, shared module. Extract the shared concept (a domain entity, an interface, a value object) into a `core` or `common` module that both depend on.

**6. Visualize the Dependency Graph Regularly**
Use IntelliJ IDEA's "Analyze > Dependencies" or "Analyze > Module Dependencies" to generate a visual dependency graph. Schedule quarterly architecture reviews where the graph is examined for emerging cycles.

**7. Structure Packages by Feature, Not Layer**
Vertical slicing (package by feature: `com.example.order`, `com.example.user`) makes cycles visible at a glance. Horizontal slicing (package by layer: `com.example.service`, `com.example.repository`) hides cycles because all services live in the same package.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Circular Dependencies Appear in Distributed Systems**

- **Service circular calls** — Service A calls Service B to complete a request; Service B calls Service A for data it needs. This creates a distributed deadlock under concurrent load: both services wait for each other, requests time out, and the cycle cascades into a full outage.
- **Deployment ordering impossibility** — When Service A depends on Service B and Service B depends on Service A at startup (to register with each other, exchange configs, etc.), deployment ordering is undefined. Blue-green deployments, rolling restarts, and failovers all fail to find a valid startup sequence.
- **Circular event subscriptions** — Service A publishes `OrderPlaced` events; Service B consumes them and publishes `InventoryReserved`; Service A consumes `InventoryReserved` and publishes another `OrderPlaced`. Without careful deduplication, this creates infinite event loops that flood the message bus.
- **Database circular foreign keys** — Table A has a foreign key to Table B; Table B has a foreign key to Table A. Insertions require both rows to exist simultaneously; deletions require both to be absent simultaneously — creating referential integrity deadlocks that require nullable columns or application-level workarounds.

---

## Real-World Consequences

**Scenario 1: The Spring Boot Startup Failure**
A fintech startup added a new feature that required `PaymentService` to look up account details via `AccountService`, and `AccountService` to validate payment status via `PaymentService`. The application context failed to start in the next CI run with `BeanCurrentlyInCreationException`. The team "fixed" it by adding `@Lazy` to the `AccountService` injection in `PaymentService`. This deferred the creation of `AccountService` to first use — which happened during application warm-up — resulting in intermittent `NullPointerExceptions` in production under high load conditions, where beans were being accessed before lazy initialization completed.

**Scenario 2: The Microservices Deployment Deadlock**
A company decomposed their monolith into microservices but preserved the circular dependencies between the `User Service` and `Order Service`. Both services called each other's REST APIs on startup for health checks and configuration. In production, restarting both services simultaneously caused both to fail their health checks (because each was trying to call the other, which was also restarting) and both entered a crash loop. The deployment window stretched from 30 minutes to 4 hours while the team manually sequenced restarts.

**Scenario 3: The Untestable Core Module**
A logistics company's core domain model had package-level cycles between `shipment`, `warehouse`, and `carrier` packages — each imported types from the others. The result was that no class in any of the three packages could be instantiated in a unit test without bringing in all three packages. A test that should have been 10 lines required 200 lines of setup. Test coverage for the core domain dropped below 30% because writing tests was too laborious. When a calculation error in shipment cost was introduced, it was not caught by tests and reached production.

**Scenario 4: The Class Loading Race Condition**
A system had two configuration classes with a static initialization cycle similar to the example above. In single-threaded testing, the classes always loaded in the same order and constants had the correct values. In a multi-threaded production environment, the class loading order was non-deterministic. Approximately 1 in 200 application starts resulted in one constant being initialized to `0` instead of its intended value, causing a specific timeout to be set to 0 milliseconds (immediate timeout). This manifested as a mysterious "connection refused" error that appeared roughly once per week, took three months to diagnose, and was fixed in 2 minutes once the root cause was identified.

---

## Quick Reference Summary

| Dimension | Details |
|---|---|
| **Anti-Pattern Name** | Circular Dependencies |
| **Also Known As** | Dependency Cycle, Circular Reference, Cyclic Coupling |
| **Root Cause** | Misplaced responsibilities, missing abstractions, lack of architectural enforcement |
| **Primary Symptom** | A depends on B, B depends on A (or longer chains); Spring BeanCurrentlyInCreationException |
| **Key Code Smells** | Bidirectional imports between packages, `@Lazy` used to "fix" Spring startup, cannot test class A without class B |
| **Main Harm** | Application startup failures, unit test impossibility, deployment ordering deadlocks, class loading race conditions |
| **Detection Tools** | JDepend, SonarQube Cyclic Dependencies, ArchUnit, IntelliJ Dependency Analysis |
| **Fix Strategy** | Introduce domain events (publisher/subscriber), extract shared interface, move shared concept to a core module |
| **Prevention** | Acyclic Dependencies Principle, ArchUnit fitness functions, package-by-feature structure, design reviews |
| **Effort to Fix** | Medium to High — requires identifying root responsibility mismatch, not just moving imports |
