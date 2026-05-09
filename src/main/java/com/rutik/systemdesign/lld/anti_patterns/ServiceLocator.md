# Anti-Pattern: Service Locator

## What It Is

The Service Locator pattern provides a centralized registry where services (dependencies) can be looked up by name or type at runtime. Any class can call the locator to obtain a dependency instead of receiving it through a constructor or setter.

While it was once considered a solution to dependency management, Service Locator is now widely considered an anti-pattern — most notably by Martin Fowler and Mark Seemann — because it hides dependencies, making code harder to understand, test, and maintain.

The contrast:
- **Service Locator**: Dependencies are pulled from a global registry inside the class.
- **Dependency Injection**: Dependencies are pushed into the class from outside.

---

## Intuition

> **One-line analogy**: Service Locator is like a warehouse where workers walk in and grab whatever tools they need without anyone tracking what each worker requires — you can't tell what anyone needs until they're mid-task.

**Mental model**: Instead of a class declaring its dependencies up front (via constructor injection), it sneaks off to a global registry and pulls them at runtime. The class looks simple from the outside — a two-field constructor — but internally it has hidden wires running to a global container. When you want to test it, you have to pre-populate the locator; when you want to understand it, you have to trace every `locator.get()` call inside.

**Why it matters**: Hidden dependencies are the root of test friction and debugging pain. If a class doesn't declare what it needs, you can't reason about it in isolation, mock it cleanly, or refactor it safely.

**Key insight**: The difference between Dependency Injection and Service Locator is *who is in control*. DI pushes dependencies in (explicit, testable). Service Locator pulls them out (implicit, hidden). Always prefer explicit over implicit.

---

## How to Recognize It

**Code smells:**
- A class contains calls like `ServiceLocator.get(OrderRepository.class)` or `ServiceLocator.resolve("emailService")`
- Dependencies are not visible in a class's constructor or method signatures
- A global `ServiceLocator`, `Registry`, `Container`, or `Context` class is used throughout business logic
- You cannot tell what a class depends on by reading its constructor

**Example — The Anti-Pattern:**

```java
// The Service Locator itself
public class ServiceLocator {

    private static final Map<Class<?>, Object> services = new HashMap<>();

    public static <T> void register(Class<T> type, T implementation) {
        services.put(type, implementation);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        T service = (T) services.get(type);
        if (service == null) {
            throw new IllegalStateException("No service registered for: " + type.getName());
        }
        return service;
    }
}

// Registration at startup
public class Application {
    public static void main(String[] args) {
        ServiceLocator.register(OrderRepository.class, new JpaOrderRepository());
        ServiceLocator.register(EmailService.class, new SmtpEmailService());
        ServiceLocator.register(PaymentGateway.class, new StripePaymentGateway());
        ServiceLocator.register(AuditLogger.class, new DatabaseAuditLogger());

        new OrderController().placeOrder(new OrderRequest());
    }
}

// Consumer: uses Service Locator to pull dependencies
public class OrderService {

    public Order placeOrder(OrderRequest request) {
        // Hidden dependencies — you cannot tell what this class needs
        // from its constructor signature alone
        OrderRepository repo = ServiceLocator.get(OrderRepository.class);
        EmailService email = ServiceLocator.get(EmailService.class);
        PaymentGateway payment = ServiceLocator.get(PaymentGateway.class);
        AuditLogger logger = ServiceLocator.get(AuditLogger.class);

        Order order = Order.create(request);
        payment.charge(request.getPaymentMethod(), order.getTotal());
        repo.save(order);
        email.sendConfirmation(order);
        logger.log("Order placed: " + order.getId());
        return order;
    }
}
```

**Testing problem:**

```java
// To test OrderService, you must configure the global ServiceLocator
// Any test that forgets to register a service will fail with a runtime error
class OrderServiceTest {

    @BeforeEach
    void setup() {
        // Must remember to register every single dependency
        // If a new dependency is added to OrderService, tests silently break at runtime
        ServiceLocator.register(OrderRepository.class, mock(OrderRepository.class));
        ServiceLocator.register(EmailService.class, mock(EmailService.class));
        ServiceLocator.register(PaymentGateway.class, mock(PaymentGateway.class));
        ServiceLocator.register(AuditLogger.class, mock(AuditLogger.class));
        // Forgot to register one? Runtime IllegalStateException, not a compile error
    }

    @Test
    void placeOrder_chargesPaymentAndSavesOrder() {
        OrderService service = new OrderService();
        // ...
    }
}
```

---

## Why It Happens

1. **Avoiding constructor parameter explosion**: When a class has many dependencies, developers avoid adding more constructor parameters and reach for a registry instead.
2. **Legacy framework design**: Older Java EE patterns (JNDI lookups, EJB home interfaces) used service location heavily. Developers carry these habits forward.
3. **Retrofitting DI**: Teams that want to decouple classes but don't have a DI framework use a hand-rolled Service Locator as a substitute.
4. **Plugin / extension points**: Some plugin architectures genuinely need late-bound service discovery — Service Locator feels natural here, even when it is not the best fit.

---

## Why It's Harmful

1. **Hidden dependencies (the core problem)**: A class's contract — what it needs to function — is invisible. You must read the entire method body to find out what it depends on.
2. **Runtime failures instead of compile-time failures**: If a service is not registered, you get an exception at runtime, possibly deep in a call stack. Constructor injection fails at object construction time or at container startup.
3. **Tight coupling to the locator itself**: Every class is coupled to `ServiceLocator`. This makes the locator a God Object and makes porting or testing classes in isolation impossible without the locator.
4. **Non-obvious test setup**: Tests must manually configure global state before each run. Adding a new dependency to a class does not break the compiler — it breaks tests at runtime.
5. **Harder to trace data flow**: IDEs cannot show you what implements `OrderRepository` when it is pulled from a map by type at runtime. Static analysis is undermined.
6. **Thread safety**: If services are registered at startup and the map is shared, concurrent registration can cause race conditions.

---

## How to Fix It

Replace Service Locator calls with **Constructor Injection**. All dependencies are declared in the constructor — they are explicit, compile-time-checked, and injectable with test doubles.

```java
// Refactored: constructor injection — dependencies are explicit and visible
public class OrderService {

    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final PaymentGateway paymentGateway;
    private final AuditLogger auditLogger;

    // Every dependency is declared here — no surprises
    public OrderService(OrderRepository orderRepository,
                        EmailService emailService,
                        PaymentGateway paymentGateway,
                        AuditLogger auditLogger) {
        this.orderRepository = orderRepository;
        this.emailService = emailService;
        this.paymentGateway = paymentGateway;
        this.auditLogger = auditLogger;
    }

    public Order placeOrder(OrderRequest request) {
        Order order = Order.create(request);
        paymentGateway.charge(request.getPaymentMethod(), order.getTotal());
        orderRepository.save(order);
        emailService.sendConfirmation(order);
        auditLogger.log("Order placed: " + order.getId());
        return order;
    }
}

// Wiring happens in one place — the composition root
public class Application {
    public static void main(String[] args) {
        OrderRepository repo = new JpaOrderRepository();
        EmailService email = new SmtpEmailService();
        PaymentGateway payment = new StripePaymentGateway();
        AuditLogger logger = new DatabaseAuditLogger();

        OrderService service = new OrderService(repo, email, payment, logger);
        // ...
    }
}

// Tests are clean — inject mocks directly, no global state
class OrderServiceTest {

    private OrderRepository mockRepo;
    private EmailService mockEmail;
    private PaymentGateway mockPayment;
    private AuditLogger mockLogger;
    private OrderService orderService;

    @BeforeEach
    void setup() {
        mockRepo = mock(OrderRepository.class);
        mockEmail = mock(EmailService.class);
        mockPayment = mock(PaymentGateway.class);
        mockLogger = mock(AuditLogger.class);
        // If OrderService gains a new dependency, this line fails at compile time
        orderService = new OrderService(mockRepo, mockEmail, mockPayment, mockLogger);
    }

    @Test
    void placeOrder_chargesPaymentGateway() {
        OrderRequest request = new OrderRequest(paymentMethod(), items());
        orderService.placeOrder(request);
        verify(mockPayment).charge(any(), any());
    }

    @Test
    void placeOrder_savesOrderToRepository() {
        orderService.placeOrder(new OrderRequest(paymentMethod(), items()));
        verify(mockRepo).save(any(Order.class));
    }
}
```

**Using Spring for wiring (the standard approach):**

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final PaymentGateway paymentGateway;

    // Spring injects these at startup — no ServiceLocator needed
    @Autowired
    public OrderService(OrderRepository orderRepository,
                        EmailService emailService,
                        PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.emailService = emailService;
        this.paymentGateway = paymentGateway;
    }
}
```

---

## Service Locator vs Dependency Injection — Side by Side

| Aspect | Service Locator | Dependency Injection |
|---|---|---|
| Dependencies visible in signature? | No — hidden inside method body | Yes — in constructor |
| Failure mode | Runtime exception | Compile error or startup failure |
| Testability | Requires global setup | Inject mocks directly |
| Coupling | Coupled to locator | Coupled only to interface |
| Traceability | Hard — dynamic lookup | Easy — static analysis works |
| When to use | Plugin discovery, dynamic resolution | Almost everything else |

---

## When Service Locator Is Acceptable

Service Locator is not always wrong. It is appropriate when:
- Building a plugin system where implementations are discovered at runtime from the classpath
- Implementing a framework (e.g., a servlet container locating request handlers)
- The consumer genuinely does not know at compile time what it will need

Even in these cases, the Service Locator should be used at the **boundary** of the system — not inside business logic classes.

---

## Real-World Examples

- **JNDI in Java EE**: `InitialContext.lookup("java:comp/env/jdbc/MyDS")` — the original Service Locator pattern in Java.
- **Android's `getSystemService()`**: Technically Service Locator, but appropriate here since Android's framework controls the lifecycle.
- **Spring's `ApplicationContext.getBean()`**: When used inside business logic (not the composition root), this is Service Locator abuse.
- **OSGi service registry**: Legitimate use — dynamic, runtime plugin discovery.

---

## Prevention Strategies

1. **Constructor injection as the default**: Always inject dependencies through constructors. This is enforced by most modern DI frameworks.
2. **Use a DI framework**: Spring, Guice, or Dagger eliminate the need for hand-rolled registries.
3. **Composition root discipline**: Service wiring belongs in one place — the application entry point or configuration class. Never inside domain/business logic.
4. **Code review**: Flag any call to a global registry inside a service or repository class.
5. **The test smell rule**: If setting up a unit test requires configuring global state, the production code has a design problem.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Service Locator Appears in Distributed Systems**

- **Hard-coded service URLs** — A service that calls `http://order-service.internal:8080/api/orders` instead of resolving the address via service discovery is a distributed Service Locator: dependencies are hidden in code rather than declared and injected via config.
- **Implicit service discovery** — When service addresses are resolved via a global registry without being declared as explicit dependencies (not in env vars, not in the service's declared dependency graph), operators can't reason about what a service calls — same hidden-dependency problem as the Service Locator pattern.
- **Opaque API gateway routing** — An API gateway that silently routes requests to backend services without publishing its routing table creates a Service Locator effect: clients can't reason about what calls what, making tracing, debugging, and incident response harder.
- **Static config mixed with service resolution** — Config files or environment variables that contain both static values and dynamically resolved service addresses without distinction create opaque dependency graphs — the distributed equivalent of `ServiceLocator.resolve()` calls scattered through business logic.

---

## Interview Relevance

**Common interview questions:**
- "What is the difference between Service Locator and Dependency Injection?" — Classic design question.
- "Why is Service Locator considered an anti-pattern?" — Tests whether you understand hidden dependencies.
- "How does constructor injection improve testability?" — Practical angle on the same concept.
- "What is the Dependency Inversion Principle?" — Service Locator violates DIP; DI upholds it.

**Key talking points:**
- The core problem is hidden dependencies — the class's needs are not visible from outside
- DI makes dependencies explicit, enabling compile-time checking and easy mocking
- Service Locator couples every class to the locator — DI decouples classes from each other
- Mention Martin Fowler's article "Inversion of Control Containers and the Dependency Injection pattern" as the authoritative source
