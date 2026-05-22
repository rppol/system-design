# Factory Method Pattern

## 1. Pattern Name & Category

**Name:** Factory Method (also called Virtual Constructor)
**Category:** Creational (GoF)
**GoF Classification:** Gang of Four — Creational Design Pattern
**Book Reference:** "Design Patterns: Elements of Reusable Object-Oriented Software" (Gamma et al., 1994)

---

## 2. Intent

Define an interface for creating an object, but let subclasses decide which class to instantiate. Factory Method lets a class defer instantiation to subclasses.

---

## Intuition

> **One-line analogy**: Factory Method is like a staffing agency — you tell the agency "I need a worker for this role," and they handle finding and sending the right person, without you managing the hiring process.

**Mental model**: When your code needs an object but doesn't (or shouldn't) know which concrete class to use, you delegate the creation decision to a factory method. The client asks for a "Creator" and calls `createProduct()` — the concrete Creator subclass decides what to create. This separates "what you do with an object" from "how you create the object."

**Why it matters**: Factory Method is foundational to extensibility. Adding a new product type means adding a new Creator subclass, not modifying existing code (Open/Closed Principle). Frameworks use this pattern extensively to let users extend behavior without modifying framework internals.

**Key insight**: The power isn't in the factory itself — it's in the inversion: the high-level code depends on abstractions, while concrete classes handle instantiation details. This is the basis for most plugin/extension architectures.

---

## 3. Problem Statement

### The Problem
You're writing a framework or base class that needs to create objects, but you don't know upfront which exact class to instantiate — that decision depends on the context provided by subclasses or the configuration of the running application.

### The Scenario
Imagine you're building a cross-platform notification system. The base `NotificationSender` class has a `send()` method that sends a message. But the concrete type of notification — `EmailNotification`, `SMSNotification`, `PushNotification` — depends on how the app is deployed or configured. You can't hard-code `new EmailNotification()` in the base class because:

1. It tightly couples the base class to a concrete implementation.
2. Changing to a different type requires modifying the base class (violates Open/Closed Principle).
3. A library author can't know which notification type a user will need.

You need a way to say: "I need A notification object — let whoever extends me decide what kind."

### What We Need
1. A stable interface or abstract class for the product (`Notification`).
2. A way for subclasses to specify the concrete product to create.
3. The base class should orchestrate the process of sending without knowing which concrete product it works with.

---

## 4. Solution

The Factory Method pattern introduces:
1. A **Product interface** (`Notification`) that all products implement.
2. A **Creator abstract class** (`NotificationSender`) with a `createNotification()` factory method — abstract or with a default.
3. **Concrete Creators** (`EmailSender`, `SMSSender`) that override the factory method to return specific products.
4. **Concrete Products** (`EmailNotification`, `SMSNotification`) that implement the Product interface.

The Creator calls its own factory method to get the product — it never uses `new ConcreteProduct()` directly.

---

## 5. UML Structure

```
+----------------------------+         +----------------------+
|       <<interface>>        |         |     <<interface>>    |
|         Creator            |         |       Product        |
+----------------------------+         +----------------------+
| + factoryMethod(): Product |<------->| + operation()        |
| + someOperation()          |         +----------------------+
+----------------------------+                   ^
            ^                                    |
            |                          +---------+---------+
  +---------+----------+               |                   |
  |                    |    creates    |                   |
ConcreteCreatorA  ConcreteCreatorB  ConcreteProductA  ConcreteProductB
  |                    |               |                   |
  | +factoryMethod()   | +factoryMethod()| +operation()   | +operation()
  | returns ProductA   | returns ProductB
  +--------------------+
```

**Relationships:**
- `Creator` depends on `Product` (via factory method return type).
- `ConcreteCreator` creates `ConcreteProduct` — the only place where `new` is called.
- Client talks to `Creator` and `Product` interfaces only — no coupling to concrete classes.

---

## 6. How It Works — Step by Step

1. **Client** obtains a `ConcreteCreator` (e.g., from config, DI, or by instantiating it directly).
2. **Client** calls `creator.someOperation()` — a method on the base Creator.
3. **`someOperation()`** internally calls `this.factoryMethod()` to get a product.
4. Because `factoryMethod()` is overridden in the ConcreteCreator, the correct ConcreteProduct is returned.
5. **`someOperation()`** uses the product via the `Product` interface — no knowledge of concrete type.
6. The ConcreteProduct does its work and returns the result up the chain.

Key insight: `someOperation()` is the template. `factoryMethod()` is the hook that subclasses fill in.

---

## 7. Key Components

| Component | Role |
|-----------|------|
| `Product` | Interface/abstract class that all products implement |
| `ConcreteProduct` | A specific product class (e.g., `EmailNotification`) |
| `Creator` | Abstract class with the factory method and optionally a default implementation |
| `ConcreteCreator` | Overrides the factory method to return a specific ConcreteProduct |

---

## 8. When to Use

- **Framework design:** A framework must create objects but can't know the exact class — let framework users subclass and override the factory method.
- **Pluggable implementations:** Different deployments need different concrete types (email vs. SMS vs. push notifications).
- **When construction logic varies:** Different products have different setup procedures, and centralizing construction in one if-else block becomes unwieldy.
- **When you want to follow Open/Closed Principle:** New product types should be addable without modifying existing code — just add a new ConcreteCreator/ConcreteProduct pair.
- **Testing:** Subclass the Creator in tests to override the factory method and return mock products.
- **Logging, parsers, UI components:** Any context where the exact type to produce varies by subclass or configuration.

---

## 9. When NOT to Use

- **When you have only one product type:** Using Factory Method adds complexity (extra class hierarchy) for no benefit.
- **When the variation is simple:** If you only need to choose between 2-3 implementations and that won't change, a simple `if-else` or static factory method is cleaner.
- **When you don't control the class hierarchy:** Factory Method requires subclassing the Creator, which may not be possible with final classes or third-party libraries.
- **Performance-critical instantiation:** The extra polymorphic dispatch is negligible in most cases, but relevant in extremely tight loops.

---

## 10. Pros

- **Open/Closed Principle:** Add new product types by adding new ConcreteCreator/ConcreteProduct pairs — without touching existing code.
- **Loose coupling:** Creator and client code depend only on the Product interface; never on concrete product classes.
- **Single Responsibility:** Object creation logic is isolated in concrete creators, separate from business logic in the base creator.
- **Flexibility:** Subclasses decide exactly what to create and how to configure it.
- **Testability:** Override the factory method in tests to inject mock/fake products without a DI container.
- **Framework extensibility:** Library provides the Creator; users provide ConcreteCreators to plug in their types.

---

## 11. Cons

- **Class proliferation:** Every new product type requires a new ConcreteProduct and a new ConcreteCreator — the class count grows.
- **Inheritance-based:** Forces you to subclass Creator, which ties you to the class hierarchy. Composition is often preferred over inheritance.
- **Indirection:** Creation logic is spread across multiple classes, making it harder to follow at a glance compared to direct instantiation.
- **Complexity for simple cases:** For trivial use cases, a static factory method in the product class achieves the same result with far less structure.
- **Rigid hierarchy:** Once the Creator hierarchy is established, restructuring it is refactoring work.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Open/Closed extensibility | One extra class per product type |
| Decoupling from concrete classes | Inheritance coupling (must subclass Creator) |
| Isolated creation logic | Increased indirection and verbosity |
| Testability via override | Harder to trace "what gets created" at a glance |
| Framework extensibility | Complexity for simple single-product scenarios |

---

## 13. Common Pitfalls

1. **Confusing Factory Method with Simple Factory:** A Simple Factory (a static method with if-else) is NOT the Factory Method pattern. Factory Method uses inheritance and polymorphism — the factory method is overridden by subclasses.

2. **Overusing it:** Not every object creation needs a factory method. Use it when there is a genuine need for extensibility or when the base class must defer instantiation.

3. **Returning concrete types from the factory method:** The factory method should return the Product interface, not the concrete type. Returning a concrete type defeats the decoupling purpose.

4. **Putting too much logic in the factory method:** The factory method should just create and return the product. Complex configuration should be done elsewhere (e.g., a builder or the product's constructor).

5. **Mixing Factory Method with Abstract Factory:** Factory Method deals with ONE product family dimension (one type of object). Abstract Factory creates FAMILIES of related objects. They are different patterns.

---

## 14. Real-World Usage

### Production Scenario: JDBC DriverManager and High-Throughput Connection Pools

`java.sql.DriverManager.getConnection()` is the canonical Factory Method in the Java platform.
The caller provides a JDBC URL string; the DriverManager determines which registered driver
can handle it and delegates construction of the `Connection` to that driver's `connect()` method.
At no point does the caller reference `MySQLConnection`, `PgConnection`, or any concrete class.

HikariCP — the most widely used JDBC connection pool (default in Spring Boot) — uses this pattern
to create new physical connections when the pool is exhausted. At a throughput of 10,000
transactions per second with a pool size of 10, the factory is called rarely (only on pool growth),
but each call must return a correctly configured, authenticated connection in under 50ms.

```
Application (10,000 TPS)
       |
       v
HikariCP Connection Pool (default size: 10 connections)
       |
       | pool exhausted? create new connection
       v
+-----------------------------------------------+
|   DriverManager.getConnection(jdbcUrl, props) |  <-- Factory Method
+-----------------------------------------------+
       |
       | DriverManager iterates registered drivers
       | first driver where driver.acceptsURL(url) == true wins
       v
+----------------------------+    +---------------------------+
| com.mysql.cj.jdbc.Driver   |    | org.postgresql.Driver     |
| .connect(url, props)       |    | .connect(url, props)      |
+----------------------------+    +---------------------------+
       |                                  |
       v                                  v
MySQLConnection (concrete)       PgConnection (concrete)
  both returned as java.sql.Connection (interface)
```

### Famous Codebase Usages

| Framework / Library | Class / Method | Pattern Role |
|--------------------|---------------|-------------|
| `java.sql.DriverManager` | `getConnection(url, props)` | Creator; delegates to `Driver.connect()` factory method | Java 1.1+ |
| `java.util.Collection` | `iterator()` — declared in Collection, overridden in ArrayList, LinkedList, HashSet | Factory Method; returns concrete iterator | Java 2+ |
| `javax.xml.parsers.DocumentBuilderFactory` | `newDocumentBuilder()` — abstract; Xerces/Crimson override it | GoF textbook Factory Method | Java 1.4+ |
| Spring Framework 6 | `FactoryBean<T>.getObject()` — bean implements this to produce another bean via factory method | Factory Method for bean construction | Spring 6.0+ |
| Hibernate 6.x | `SessionFactory.openSession()` — returns `SessionImpl`; caller sees `Session` interface | Factory Method for session lifecycle | Hibernate 6.0+ |
| `java.nio.charset.Charset` | `newDecoder()` / `newEncoder()` — each Charset subclass returns its own coder | Factory Method for codec creation | Java 1.4+ |
| Android SDK | `Fragment.onCreateView()` — framework calls; subclass returns the Fragment's view | Framework-level Factory Method | Android API 11+ |

### Production-Grade Code: JDBC Factory Method with HikariCP (Java 17 LTS, Spring Boot 3.2+)

```java
// Java 17 LTS — demonstrating how DriverManager.getConnection() acts as a factory method.
// This is the actual pattern HikariCP uses internally when growing the pool.
// See: com.zaxxer.hikari.pool.PoolBase#newConnection() in HikariCP 5.x source.

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// AbstractConnectionCreator = Creator
// JDBC Driver implementations = ConcreteCreators
// java.sql.Connection = Product interface
// MySQLConnection / PgConnection = ConcreteProducts (never referenced by caller)

public abstract class AbstractConnectionCreator {
    // Template Method that calls the Factory Method
    public Connection createAndValidate() throws SQLException {
        Connection conn = createConnection(); // factory method call
        if (!conn.isValid(2)) {
            conn.close();
            throw new SQLException("Connection validation failed within 2s");
        }
        return conn;
    }

    // Factory Method — subclasses decide which concrete Connection to produce
    protected abstract Connection createConnection() throws SQLException;
}

// ConcreteCreator A — MySQL via DriverManager (delegates to mysql.cj.jdbc.Driver internally)
public class MysqlConnectionCreator extends AbstractConnectionCreator {
    private final String url;
    private final Properties props;

    public MysqlConnectionCreator(String host, int port, String db, String user, String pass) {
        this.url = String.format("jdbc:mysql://%s:%d/%s?useSSL=true&serverTimezone=UTC", host, port, db);
        this.props = new Properties();
        this.props.setProperty("user", user);
        this.props.setProperty("password", pass);
        this.props.setProperty("connectTimeout", "5000");
        this.props.setProperty("socketTimeout", "30000");
    }

    @Override
    protected Connection createConnection() throws SQLException {
        // DriverManager.getConnection is itself a factory method: it picks the right Driver
        return DriverManager.getConnection(url, props);
    }
}

// ConcreteCreator B — PostgreSQL
public class PostgresConnectionCreator extends AbstractConnectionCreator {
    private final String url;
    private final Properties props;

    public PostgresConnectionCreator(String host, int port, String db, String user, String pass) {
        this.url = String.format("jdbc:postgresql://%s:%d/%s?ssl=true", host, port, db);
        this.props = new Properties();
        this.props.setProperty("user", user);
        this.props.setProperty("password", pass);
        this.props.setProperty("loginTimeout", "5");
        this.props.setProperty("socketTimeout", "30");
    }

    @Override
    protected Connection createConnection() throws SQLException {
        return DriverManager.getConnection(url, props);
    }
}
```

### Anti-Pattern 1: Using `new` Instead of Factory — Breaks Dependency Injection and Testability

```java
// BROKEN: hard-coded new inside business logic
// This was the dominant pattern in pre-Spring enterprise Java (2000-2005).
// Still seen in legacy codebases and junior code.
public class OrderService {
    public void processOrder(Order order) {
        // Direct instantiation: OrderService is now coupled to MySQLConnection forever.
        // You cannot test OrderService without a live MySQL instance.
        // Switching to PostgreSQL requires editing OrderService.
        Connection conn = new com.mysql.cj.jdbc.ConnectionImpl(host, port, props); // WRONG
        // ... use conn
    }
}
```

```java
// FIX: accept the factory method (or a DataSource) via constructor injection
// Now OrderService is testable with any Connection implementation.
public class OrderService {
    private final AbstractConnectionCreator connectionCreator;

    // Java 17: injection via constructor (preferred over field injection)
    public OrderService(AbstractConnectionCreator connectionCreator) {
        this.connectionCreator = connectionCreator;
    }

    public void processOrder(Order order) throws SQLException {
        try (Connection conn = connectionCreator.createAndValidate()) {
            // business logic — completely decoupled from MySQL/PostgreSQL
        }
    }
}

// In production:
OrderService svc = new OrderService(new MysqlConnectionCreator(host, port, db, user, pass));

// In unit tests:
OrderService testSvc = new OrderService(new FakeConnectionCreator()); // no database needed
```

### Anti-Pattern 2: Static if-else "Factory" — Violates Open/Closed Principle

```java
// BROKEN: static factory with if-else — adding a new database type requires editing this class.
// Every new database type forces a modification. This is O(n) maintenance pain.
public class BadConnectionFactory {
    public static Connection create(String dbType, String host, int port) throws SQLException {
        if ("mysql".equals(dbType)) {
            return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/app");
        } else if ("postgres".equals(dbType)) {
            return DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/app");
        } else if ("oracle".equals(dbType)) {
            return DriverManager.getConnection("jdbc:oracle:thin:@" + host + ":" + port + ":app");
        }
        // Adding SQLite requires editing this class — violates Open/Closed Principle
        throw new IllegalArgumentException("Unknown DB type: " + dbType);
    }
}
```

```java
// FIX: Factory Method via polymorphism — adding SQLite = adding one new class, not editing existing.
public class SqliteConnectionCreator extends AbstractConnectionCreator {
    private final String filePath;

    public SqliteConnectionCreator(String filePath) {
        this.filePath = filePath;
    }

    @Override
    protected Connection createConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + filePath);
    }
}
// Zero changes to AbstractConnectionCreator, MysqlConnectionCreator, or OrderService.
```

### Anti-Pattern 3: Factory Method Returns a Concrete Type

```java
// BROKEN: factory method leaks concrete type — callers now depend on MySQLConnection
public class BadMysqlCreator {
    public com.mysql.cj.jdbc.ConnectionImpl createConnection() throws SQLException { // WRONG return type
        return (com.mysql.cj.jdbc.ConnectionImpl) DriverManager.getConnection(url, props);
    }
    // Callers that import ConnectionImpl cannot switch to PostgreSQL without a rewrite.
}
```

```java
// FIX: always return the Product interface (java.sql.Connection)
public class CorrectMysqlCreator extends AbstractConnectionCreator {
    @Override
    protected Connection createConnection() throws SQLException { // returns interface
        return DriverManager.getConnection(url, props);
    }
}
```

### Performance Numbers

- HikariCP 5.x pool growth (factory method call per new physical connection): 15–50ms typical,
  under 200ms including TLS handshake to a remote DB — acceptable because pool growth is rare.
- `Collection.iterator()` factory method dispatch: ~1–2 ns — JIT inlines the virtual call after
  10,000 invocations (C2 compiler threshold), eliminating dispatch overhead at steady state.
- `FactoryBean.getObject()` in Spring Boot 3.2+: called once at startup per singleton-scoped
  FactoryBean; result is cached. Prototype-scoped FactoryBeans pay the factory method cost per
  `getBean()` call — typically < 1 microsecond for simple construction.

### Migration Story: Moving from `new` to Factory Method for Testability

A common migration path in enterprise Java teams (2018-2023):

Phase 1 (legacy, common before 2010): `new ConcreteClass()` scattered throughout service classes.
Integration tests require a running database; unit tests do not exist or use in-memory H2 with
schema compatibility bugs.

Phase 2 (extract factory method): Identify all `new` calls that produce swappable objects.
Extract to a protected factory method in the same class. Subclass in tests to return fakes.
This is the minimal refactoring — no new classes required.

Phase 3 (introduce Creator hierarchy): If multiple strategies are genuinely needed in production
(MySQL in prod, SQLite for embedded analytics, H2 for testing), formalize AbstractConnectionCreator.
At this point, dependency injection (Spring Boot 3.x `@Bean` method = factory method) is usually
the cleaner path.

Phase 4 (move to DI framework): Once the team has Spring Boot 3.x, `@Bean` methods on
`@Configuration` classes ARE factory methods: they are overridable in test slices via
`@TestConfiguration`, satisfying Open/Closed without any hand-rolled Creator hierarchy.

---

## 15. Comparison with Similar Patterns

| Pattern | How It Differs from Factory Method |
|---------|------------------------------------|
| **Abstract Factory** | Creates families of related objects; Factory Method creates one type of object. Abstract Factory often uses Factory Methods internally. |
| **Template Method** | Factory Method IS a specialization of Template Method — the factory method is the "hook" step. |
| **Simple Factory** | Not a GoF pattern. A static method with conditionals. No subclassing, no polymorphism — just a helper. |
| **Builder** | Constructs a complex object step-by-step; Factory Method creates it in one call. Builder focuses on the construction process, Factory Method on which type to create. |
| **Prototype** | Creates a new object by cloning an existing one; Factory Method creates fresh. |

---

## 16. Interview Tips

**Common Interview Questions:**

1. **"Explain Factory Method and how it differs from Abstract Factory."**
   Answer: Factory Method creates one product type, uses inheritance (subclass overrides the method). Abstract Factory creates families of related products, uses composition (the factory object is passed in or referenced). State: "Factory Method is about one dimension; Abstract Factory is about multiple related dimensions."

2. **"Why not just use `new` or a static if-else?"**
   Answer: `new` couples the caller to the concrete type. A static if-else violates Open/Closed — adding a new type means editing the factory. Factory Method lets you add new creators without changing existing code.

3. **"Is `java.util.Iterator` an example of Factory Method?"**
   Answer: Yes — `Collection.iterator()` is declared in the Collection interface, and each implementation (ArrayList, LinkedList, HashSet) overrides it to return its own concrete Iterator.

4. **"What's the relationship between Factory Method and Template Method?"**
   Answer: Factory Method IS Template Method specialized for object creation. The factory method is the "primitive operation" (hook) that subclasses override; the creator's main business method is the "template" that calls it.

**Key Phrases:**
- "Defers instantiation to subclasses"
- "Open/Closed Principle — open for extension, closed for modification"
- "Product interface, ConcreteProduct, Creator, ConcreteCreator"
- "Virtual constructor"

---

## Cross-Perspective: HLD Connections

**HLD View — Where Factory Method Appears in Distributed Systems**

- **Cloud SDK clients** — AWS SDK uses factories to create service clients (`S3Client.create()`, `DynamoDbClient.builder().build()`). The factory hides region resolution, credential chain loading, and HTTP client configuration from callers.
- **Database connection factories** — Connection pools use a factory to create new physical connections when the pool is exhausted. The factory encapsulates driver class loading, URL parsing, and SSL certificate setup.
- **Service mesh upstream handlers** — Envoy's filter chain uses factory methods to instantiate per-request filter instances, allowing per-request state while sharing filter configuration.
- **Load balancer endpoint objects** — When backends register with a load balancer, a factory creates backend endpoint objects with the correct health check type, circuit breaker config, and connection pool settings.

---

## 17. Best Practices

1. **Always return the Product interface** from the factory method — never a concrete type. This preserves the decoupling.

2. **Give the factory method a meaningful name** — `createTransport()`, `buildParser()`, `makeButton()` — not just `create()`.

3. **Provide a default implementation** in the Creator if there is a sensible default product, making it optional to override.

4. **Keep the factory method focused** — it should just create and return the product, not configure or use it. Let `someOperation()` do the orchestration.

5. **Consider a static factory method as a simpler alternative** for cases where you don't need the full subclass-override flexibility.

6. **Document what the factory method is expected to return** — especially the invariants the returned Product must satisfy.

7. **Avoid returning null** from a factory method. Prefer a Null Object pattern or throw a meaningful exception.

8. **Pair with an interface for the product** — even if you only have one concrete product today, using an interface leaves the door open for extensibility.
