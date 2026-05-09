# Dependency Inversion Principle (DIP)

**Part of the SOLID series** | [Back to Overview](README.md)

---

## Definition and Intent

> "High-level modules should not depend on low-level modules. Both should depend on abstractions."
> "Abstractions should not depend on details. Details (concrete implementations) should depend on abstractions."
> — Robert C. Martin

**Intent:** Decouple high-level policy from low-level implementation details by inserting an abstraction layer (interface or abstract class) between them. Neither the high-level orchestrator nor the low-level worker depends directly on the other — both depend on the same contract.

DIP is the "D" in SOLID and is the foundational principle that enables Dependency Injection (DI) frameworks like Spring to work.

---

## Intuition

> **One-line analogy**: DIP is like building a house with standard outlets — your high-level system (house) defines the standard (outlet shape/voltage), and any appliance (low-level device) must conform to it. The house doesn't depend on specific appliances; appliances depend on the standard.

**Mental model**: Without DIP: `OrderService` directly instantiates `MySQLOrderRepository` — if you switch to PostgreSQL, you must change `OrderService` (high-level policy changes for low-level detail). With DIP: `OrderService` depends on `OrderRepository` (interface); `MySQLOrderRepository` implements it. `OrderService` never imports MySQL-specific code. Swap implementations without touching the service.

**Why it matters**: DIP is the foundation of testability — you can't write a unit test for `OrderService` if it's hardwired to MySQL. With an `OrderRepository` interface, inject a `FakeOrderRepository` in tests. Spring's dependency injection automates DIP — you declare dependencies as interfaces, Spring injects the implementation.

**Key insight**: DIP inverts the ownership of abstractions. Conventionally, low-level modules define their interfaces. DIP says high-level modules should define the interface that low-level modules implement — the high-level policy owns and defines the contract, not the details.

---

## The Two Rules in Plain Language

1. **High-level modules** — the ones containing business logic — should not import or instantiate low-level modules directly. If they do, the business logic becomes tightly coupled to infrastructure decisions.

2. **Abstractions should not leak implementation details** — an `EmailSender` interface should define `sendEmail(Message message)`, not `sendViaSmtp(String host, int port, Message message)`. The SMTP detail belongs in the implementation, not the contract.

---

## Problem It Solves

### Violation Example

```java
// LOW-LEVEL: concrete infrastructure class
public class MySQLUserRepository {
    public User findById(long id) {
        // Direct JDBC call to MySQL
        System.out.println("MySQL: SELECT * FROM users WHERE id = " + id);
        return new User(id, "John");
    }

    public void save(User user) {
        System.out.println("MySQL: INSERT/UPDATE user " + user.getId());
    }
}

// LOW-LEVEL: concrete notification class
public class SmtpEmailService {
    public void sendEmail(String to, String subject, String body) {
        System.out.println("SMTP: Sending email to " + to);
    }
}

// HIGH-LEVEL: business logic depends directly on low-level concretions
public class UserService {

    // Direct dependency on concrete classes — DIP violation
    private MySQLUserRepository userRepository = new MySQLUserRepository();
    private SmtpEmailService emailService = new SmtpEmailService();

    public void registerUser(String email, String name) {
        User user = new User(email, name);
        userRepository.save(user);                           // Coupled to MySQL
        emailService.sendEmail(email, "Welcome!", "Hi " + name); // Coupled to SMTP
    }
}
```

**What goes wrong:**
- To switch from MySQL to PostgreSQL, you must change `UserService` — the business logic class
- To switch from SMTP to SendGrid, you must change `UserService` again
- Unit testing `UserService.registerUser()` requires a running MySQL instance and SMTP server
- `UserService` has multiple reasons to change: business rules, DB technology, email technology

### Solution: Refactored Code (DIP Compliant)

```java
// ABSTRACTIONS: interfaces owned conceptually by the high-level module

public interface UserRepository {
    User findById(long id);
    void save(User user);
    boolean existsByEmail(String email);
}

public interface EmailService {
    void sendEmail(String to, String subject, String body);
}

// HIGH-LEVEL: business logic depends only on abstractions
public class UserService {

    private final UserRepository userRepository;   // abstraction
    private final EmailService emailService;        // abstraction

    // Dependencies are injected — UserService does not instantiate them
    public UserService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public void registerUser(String email, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User(email, name);
        userRepository.save(user);
        emailService.sendEmail(email, "Welcome!", "Hi " + name + ", welcome!");
    }
}

// LOW-LEVEL: concrete MySQL implementation — depends on the abstraction (implements it)
public class MySQLUserRepository implements UserRepository {
    @Override
    public User findById(long id) {
        System.out.println("MySQL: SELECT * FROM users WHERE id = " + id);
        return new User(id, "John");
    }

    @Override
    public void save(User user) {
        System.out.println("MySQL: INSERT/UPDATE user " + user.getEmail());
    }

    @Override
    public boolean existsByEmail(String email) {
        System.out.println("MySQL: SELECT COUNT(*) FROM users WHERE email = " + email);
        return false;
    }
}

// LOW-LEVEL: alternative implementation — swap without touching UserService
public class PostgreSQLUserRepository implements UserRepository {
    @Override
    public User findById(long id) {
        System.out.println("PostgreSQL: SELECT * FROM users WHERE id = " + id);
        return new User(id, "John");
    }

    @Override
    public void save(User user) {
        System.out.println("PostgreSQL: INSERT/UPDATE user " + user.getEmail());
    }

    @Override
    public boolean existsByEmail(String email) {
        System.out.println("PostgreSQL: EXISTS query for " + email);
        return false;
    }
}

// LOW-LEVEL: SMTP email implementation
public class SmtpEmailService implements EmailService {
    @Override
    public void sendEmail(String to, String subject, String body) {
        System.out.println("SMTP: Sending [" + subject + "] to " + to);
    }
}

// LOW-LEVEL: SendGrid implementation — drop-in replacement
public class SendGridEmailService implements EmailService {
    @Override
    public void sendEmail(String to, String subject, String body) {
        System.out.println("SendGrid API: Sending [" + subject + "] to " + to);
    }
}

// COMPOSITION ROOT: the one place that wires everything together
public class Application {
    public static void main(String[] args) {
        UserRepository repo = new MySQLUserRepository();    // or PostgreSQL
        EmailService email  = new SmtpEmailService();       // or SendGrid

        UserService userService = new UserService(repo, email);
        userService.registerUser("alice@example.com", "Alice");
    }
}
```

Now `UserService` has one reason to change: business rules. DB and email technology can change freely.

---

## DIP vs Dependency Injection

These are related but distinct concepts:

| Concept | What it is |
|---|---|
| **DIP** | A design principle — depend on abstractions, not concretions |
| **Dependency Injection** | A technique — pass dependencies from outside rather than constructing them internally |
| **IoC Container** | A framework that automates DI (Spring, Guice, CDI) |

DIP tells you *what* to depend on (abstractions). DI tells you *how* to supply those dependencies (injection). You can follow DIP without a container by using manual constructor injection, as shown above.

---

## Spring Example

```java
// Spring applies DIP through constructor injection and interface-based beans

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;

    // Spring injects concrete implementations at runtime
    public OrderService(OrderRepository orderRepository,
                        PaymentGateway paymentGateway,
                        NotificationService notificationService) {
        this.orderRepository      = orderRepository;
        this.paymentGateway       = paymentGateway;
        this.notificationService  = notificationService;
    }

    public Order placeOrder(Cart cart, PaymentDetails payment) {
        Order order = Order.from(cart);
        paymentGateway.charge(payment, order.getTotal());   // abstraction
        orderRepository.save(order);                        // abstraction
        notificationService.notify(order.getUserId(),       // abstraction
            "Order placed: " + order.getId());
        return order;
    }
}

// Interfaces define the contracts
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(long id);
}

public interface PaymentGateway {
    void charge(PaymentDetails payment, BigDecimal amount);
}

public interface NotificationService {
    void notify(long userId, String message);
}

// Spring beans — concrete implementations registered in the container
@Repository
public class JpaOrderRepository implements OrderRepository { /* JPA implementation */ }

@Component
public class StripePaymentGateway implements PaymentGateway { /* Stripe API */ }

@Component
public class PushNotificationService implements NotificationService { /* Firebase */ }

// Swapping implementations requires only changing the @Primary annotation or config
// OrderService code is never touched
```

---

## A Realistic Layered Architecture

```
+-----------------------------------+
|        Presentation Layer         |  (Controllers)
+-----------------------------------+
             depends on
+-----------------------------------+
|         Application Layer         |  (Use Cases / Services)
|    depends on interfaces only     |
+-----------------------------------+
             depends on
+-----------------------------------+
|        Domain Interfaces          |  (Repository, Gateway interfaces)
+-----------------------------------+
             implemented by
+-----------------------------------+
|       Infrastructure Layer        |  (JPA, REST clients, SMTP)
+-----------------------------------+
```

Arrows point inward. Infrastructure implements domain interfaces — the domain never imports infrastructure.

---

## Unit Testing Becomes Trivial

```java
// Without DIP: unit test requires real MySQL and SMTP
// With DIP: inject mocks freely

public class UserServiceTest {

    @Test
    void registerUser_savesUserAndSendsEmail() {
        // Arrange — pure in-memory test doubles, no external systems needed
        UserRepository mockRepo = new InMemoryUserRepository();
        List<String> sentEmails = new ArrayList<>();

        EmailService captureEmail = (to, subject, body) -> sentEmails.add(to);

        UserService service = new UserService(mockRepo, captureEmail);

        // Act
        service.registerUser("bob@example.com", "Bob");

        // Assert
        assertTrue(mockRepo.existsByEmail("bob@example.com"));
        assertEquals(1, sentEmails.size());
        assertEquals("bob@example.com", sentEmails.get(0));
    }

    @Test
    void registerUser_throwsWhenEmailAlreadyExists() {
        UserRepository repoWithExistingEmail = new UserRepository() {
            public boolean existsByEmail(String e) { return true; }
            public User findById(long id) { return null; }
            public void save(User u) {}
        };

        UserService service = new UserService(repoWithExistingEmail, (to, s, b) -> {});

        assertThrows(IllegalArgumentException.class,
            () -> service.registerUser("existing@example.com", "Existing"));
    }
}
```

---

## Real-World Analogies

**Power outlets:** Your laptop does not depend on a specific power plant. It depends on the standard 110V/220V interface. Power plants (concrete implementations) implement that interface. You can plug in anywhere in the world.

**Plug adapters vs rewiring:** DIP is the adapter — it inserts an abstraction layer between your device (high-level) and the wall socket type (low-level). Without it, every device would need to be rewired for each country.

**Job description vs person:** A team lead depends on a "Software Engineer" role (abstraction), not a specific person (concretion). When one engineer leaves, a new one fills the role without the team lead changing how they work.

---

## Common Violations in Enterprise Code

1. **`new` in the middle of business logic:**
   ```java
   public class ReportService {
       public Report generate(long userId) {
           UserRepository repo = new MySQLUserRepository();  // DIP violation
           // ...
       }
   }
   ```

2. **Depending on concrete service classes instead of interfaces:**
   ```java
   @Autowired
   private MySQLUserRepository userRepository;  // concrete class injected
   // Should be:
   @Autowired
   private UserRepository userRepository;        // interface injected
   ```

3. **Calling static utility methods from business logic:**
   ```java
   public class OrderService {
       public void processOrder(Order order) {
           EmailUtils.sendConfirmation(order);  // static call — untestable, unconfigurable
       }
   }
   ```

4. **Abstractions leaking implementation details:**
   ```java
   // BAD: abstraction knows about JDBC
   public interface UserRepository {
       User findById(long id, Connection connection);  // JDBC detail in the interface
   }
   // GOOD:
   public interface UserRepository {
       User findById(long id);
   }
   ```

---

## Code Smell Indicators

- `new ConcreteClass()` inside a service or use-case class
- `import com.mysql.*` or `import com.amazonaws.*` in a service class
- Static method calls to infrastructure utilities in business logic
- Field `private MySQLRepo repo` instead of `private UserRepository repo`
- Test requiring a real database or HTTP server to test a single business rule

---

## Pros and Cons

### Pros
- Business logic is decoupled from infrastructure — swap DB/email/payment without changing business code
- Unit testing is trivial — inject mocks or in-memory doubles
- Parallel development — teams can develop against the interface before the implementation exists
- Easier to comply with OCP — adding a new implementation requires no changes to existing code
- Enables feature flags and A/B testing — inject different implementations at runtime

### Cons
- More files and interfaces to maintain — every dependency gets an interface
- Indirection increases cognitive load — following code requires jumping through interface to implementation
- Over-engineering risk — not every class needs an interface; simple utilities can be concrete
- Without a DI container, composition roots can become large and verbose

---

## Tradeoffs: When Is It OK to Bend the Rule?

- **Stable, low-level utilities:** `java.util.ArrayList`, `java.time.LocalDate` — these are so stable that depending on them directly is fine.
- **Value objects and entities:** `User`, `Order` — these are not services; they hold data and behavior. Injecting them as abstractions adds no value.
- **Simple scripts and tools:** A one-off CLI utility does not need a DI architecture.
- **Diminishing returns:** A project with 3 classes should not have 3 interfaces. Apply DIP where you anticipate change or need testability.

---

## Relationship to Other Principles

| Principle | Relationship |
|---|---|
| SRP | DIP forces classes to focus — a class depending on an abstraction cannot be pulled in many directions by multiple concretions |
| OCP | DIP is how OCP is achieved in practice — adding new behavior means adding a new implementation, not modifying the abstraction |
| LSP | DIP relies on correct LSP — if substituting one implementation for another breaks the contract, DIP's promise is broken |
| ISP | ISP ensures the abstractions DIP depends on are narrow and focused — fat interfaces undermine the benefits of DIP |

---

## Cross-Perspective: HLD Connections

**HLD View — Where DIP Appears in Distributed Systems**

- **API contracts as abstractions** — Services depend on OpenAPI specs, Protobuf contracts, or AsyncAPI schemas — not on specific service implementations. The contract is the abstraction; the implementation can change without breaking dependents.
- **Dependency injection frameworks** — Spring's IoC container is DIP at framework scale: high-level business beans declare what interfaces they need; the framework wires concrete implementations. Swapping a `PaymentGateway` implementation requires only a config change, not code change.
- **Infrastructure abstraction layers** — Application code depends on `StoragePort` and `NotificationPort` interfaces (the abstractions), not on S3 SDK or Twilio SDK directly. Infrastructure adapters implement the ports. The application is insulated from infrastructure churn.
- **Event-driven decoupling** — In event-driven architecture, services depend on event schemas (abstractions), not on the concrete publishing service. Replacing the publisher requires only maintaining the event schema contract — DIP applied to asynchronous communication.

---

## Interview Questions and Answers

**Q: What is the Dependency Inversion Principle?**

A: DIP states that high-level modules should not depend on low-level modules — both should depend on abstractions. Additionally, abstractions should not depend on details; details should depend on abstractions. In practice, this means business logic classes should reference interfaces rather than concrete implementations, with concrete objects supplied via constructor injection.

---

**Q: What is the difference between DIP and Dependency Injection?**

A: DIP is a design principle about what to depend on — prefer abstractions over concretions. Dependency Injection is a technique for supplying those dependencies from outside the class rather than instantiating them inside. DI is the mechanism; DIP is the goal. You can use DI without following DIP (injecting concrete classes), and you can follow DIP without a DI framework (manual constructor injection).

---

**Q: Why should `new ConcreteClass()` inside a service be a red flag?**

A: Because it creates a hard coupling between the service and that specific implementation. The service can no longer be tested without the concrete class, cannot swap implementations without modifying the service, and gains an additional reason to change whenever the concrete class changes. Constructing dependencies is a separate concern from using them — DIP says delegate construction to a composition root.

---

**Q: What is a composition root?**

A: The single place in the application where all concrete implementations are wired to their abstractions — typically `main()`, an application context, or a DI container configuration. All `new ConcreteImplementation()` calls happen here. Everything else depends only on interfaces.

---

**Interview Tip:** Connect DIP to testability immediately. Say: "Without DIP, unit tests for business logic require real databases and external services. With DIP, I inject in-memory stubs and the tests run in milliseconds with no external dependencies." This shows you understand the practical value, not just the academic definition. Also mention that Spring's `@Autowired` with interface types is the canonical enterprise Java application of DIP.
