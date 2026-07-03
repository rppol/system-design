# Creational Patterns — Master Index

---

## 1. Concept Overview

Creational patterns abstract the instantiation process, decoupling client code from how objects are created, composed, and represented. When a system depends on hard-coded `new ConcreteClass()` calls, it becomes rigid: changing the implementation requires changing every call site. Creational patterns centralize and parameterize the creation decision.

| Pattern | Link | Purpose |
|---------|------|---------|
| Singleton | [singleton/](singleton/) | Ensure a class has only one instance globally |
| Factory Method | [factory_method/](factory_method/) | Let subclasses decide which class to instantiate |
| Abstract Factory | [abstract_factory/](abstract_factory/) | Create families of related objects without specifying concrete classes |
| Builder | [builder/](builder/) | Construct complex objects step by step with a fluent API |
| Prototype | [prototype/](prototype/) | Copy existing objects rather than creating from scratch |

---

## 2. Intuition

**One-line analogy:** "Creational patterns are the manufacturing plant behind your code — they decide what gets built, who builds it, and how the parts fit together, while keeping the shop floor hidden from the rest of the system."

**Mental model:** Every creational pattern answers the question: "Who is responsible for creating this object, and how do we isolate that decision?" The patterns differ in *how much* of the creation process they abstract and *how many* object types they can produce.

- **Singleton** isolates the decision of *how many* instances exist (answer: exactly one).
- **Prototype** isolates the decision of *where the initial state comes from* (answer: an existing object).
- **Builder** isolates the decision of *how a complex object is assembled* (answer: step by step, deferred to `build()`).
- **Factory Method** isolates the decision of *which concrete type to create* (answer: ask a subclass).
- **Abstract Factory** isolates the decision of *which family of types to create* (answer: ask a factory object configured at runtime).

---

## 3. Patterns at a Glance — Full Comparison Table

| Pattern | Intent | Real Java Example | When to Use | Key Pitfall |
|---------|--------|-------------------|-------------|-------------|
| Singleton | One instance, globally accessible | `Runtime.getRuntime()`, `Collections.emptyList()` | Shared resource (connection pool, config, logger) | Global mutable state; untestable without DI; multi-classloader breaks it |
| Factory Method | Define creation interface; subclasses decide | `Collection.iterator()`, `DocumentBuilderFactory.newDocumentBuilder()` | Creating one of several related types; subclass controls concrete type | "Factory explosion" — too many subclasses for each type |
| Abstract Factory | Create families of related, compatible objects | `DocumentBuilderFactory`, `SSLContext` | Cross-platform UIs, pluggable infrastructures | Adding a new product type requires modifying all factories |
| Builder | Construct complex object step by step | `StringBuilder`, `HttpRequest.newBuilder()`, `Stream.Builder` | Many optional constructor parameters; immutable objects with optional fields | Builder itself becomes complex when the object has required/optional mix |
| Prototype | Copy existing object as creation mechanism | `Object.clone()`, copy constructors, `ArrayList(Collection)` | Expensive initialization (DB query, parsing); config templates | `Cloneable` is broken (shallow copy by default); deep copy is manual |

---

## 4. Decision Flowchart (ASCII)

```
Need to CREATE an object?
  |
  +-- Only ONE instance should ever exist across the JVM?
  |     YES -> Singleton (enum preferred; or Holder idiom; avoid DCL unless Java 5+)
  |
  +-- Copying an EXISTING object is cheaper than creating from scratch?
  |     YES -> Prototype (copy constructor preferred over Cloneable)
  |
  +-- Object requires MANY parameters, some optional?
  |     YES -> Builder (eliminates telescoping constructors; enables immutability)
  |
  +-- Need to create ONE OF SEVERAL related types, subclass decides which?
  |     YES -> Factory Method (define creation interface; subclass overrides)
  |
  +-- Need to create FAMILIES of related, compatible objects?
        YES -> Abstract Factory (factory of factories; ensures compatibility)
```

---

## 5. Commonly Confused Patterns

| Confusion | Resolution |
|-----------|-----------|
| Factory Method vs Abstract Factory | Factory Method creates ONE product type; subclasses decide the concrete type. Abstract Factory creates FAMILIES of related products, ensuring they're compatible (e.g., Linux Button + Linux Checkbox, not Windows Button + Linux Checkbox). |
| Factory Method vs static factory method (Effective Java Item 1) | Static factory method is a coding idiom (e.g., `LocalDate.of()`), not the GoF Factory Method pattern. GoF Factory Method requires subclassing to vary the product type. |
| Builder vs constructor | Use Builder when you have 4+ parameters OR optional parameters OR want immutability. A constructor is simpler and should be preferred for 3 or fewer non-optional parameters. |
| Prototype vs copy constructor | Both copy objects. `Cloneable`/`clone()` is the GoF Prototype mechanism but is considered broken (shallow by default, checked exception, covariant return workaround needed). A copy constructor (`new Foo(Foo other)`) is the idiomatic Java alternative. |
| Singleton vs static class | Singleton can implement interfaces, be injected, be subclassed, and be lazily initialized. A static utility class cannot. Use Singleton when the object needs to be an instance (e.g., injectable). |

---

## 6. Spring's Use of Creational Patterns

| Spring Concept | Creational Pattern | Notes |
|---------------|-------------------|-------|
| `BeanFactory` / `ApplicationContext` | Factory Method + Registry | `getBean()` is a factory call; context is the registry |
| `@Scope("prototype")` | Prototype | Each `getBean()` call returns a new copy |
| `@Bean` methods | Factory Method | The method is the factory; Spring calls it to create the bean |
| `FactoryBean<T>` | Abstract Factory | Returns objects of a different type than the bean itself |
| Singleton scope (default) | Singleton | Spring manages one instance per container |
| Builder pattern in `WebClient`, `RestTemplate` | Builder | Fluent API for complex HTTP client configuration |

---

## 7. Complexity and Flexibility Trade-offs

```
                     FLEXIBILITY (runtime variation)
                     LOW                         HIGH
                      |                            |
COMPLEXITY  LOW       | Static factory method       | Factory Method
(boilerplate)         |                            |
                      |                            |
            HIGH      | Builder                    | Abstract Factory
                      |                            |
                      |            Prototype        |
                      | (complexity depends on      |
                      |  object graph depth)        |
```

- **Singleton** sits outside this grid — its dimension is *instance count*, not creation complexity or runtime variation.
- **Factory Method** is the lowest-overhead way to add runtime variation: one interface method, one subclass.
- **Abstract Factory** adds flexibility for entire product families but multiplies the number of classes linearly with each new product type.
- **Builder** adds complexity (extra class, fluent API) but not runtime variation — the type is known at compile time.
- **Prototype** complexity scales with object graph depth; flat objects are trivial to copy, deeply nested graphs require manual recursive deep copy.

---

## 8. Cross-References

| Pattern | See Also |
|---------|---------|
| Singleton | `../concurrency_patterns/ThreadSafeSingleton_README.md` — thread-safe implementations |
| Builder | `../../java/core_language/` — records as immutable value objects (alternative to Builder) |
| Factory Method | `../../spring/ioc_container/` — Spring `BeanFactory` as Factory Method at scale |
| Abstract Factory | `../../spring/spring_boot_autoconfiguration/` — autoconfiguration selects factory implementations |
| Prototype | `../../java/concurrency/` — `ThreadLocal` as per-thread prototype |

---

## 9. Pattern Skeletons — Minimal Java Code

These are the smallest correct implementations of each pattern. Compare them side by side to see what each pattern adds.

### Singleton (enum — preferred)

```java
public enum AppConfig {
    INSTANCE;

    private final String dbUrl = System.getenv("DB_URL");

    public String getDbUrl() { return dbUrl; }
}
// Usage: AppConfig.INSTANCE.getDbUrl()
```

### Singleton (Holder idiom — lazy, no enum)

```java
public final class ConnectionPool {
    private ConnectionPool() {}

    private static final class Holder {
        static final ConnectionPool INSTANCE = new ConnectionPool();
    }

    public static ConnectionPool getInstance() { return Holder.INSTANCE; }
}
```

### Factory Method

```java
// Creator defines the factory method; subclass decides the product
public abstract class Notification {
    public abstract Message createMessage();          // factory method

    public void send(String body) {
        Message msg = createMessage();               // uses factory method
        msg.deliver(body);
    }
}

public class EmailNotification extends Notification {
    @Override
    public Message createMessage() { return new EmailMessage(); }
}
```

### Abstract Factory

```java
public interface WidgetFactory {
    Button  createButton();
    Checkbox createCheckbox();
}

public class WindowsWidgetFactory implements WidgetFactory {
    public Button   createButton()   { return new WindowsButton(); }
    public Checkbox createCheckbox() { return new WindowsCheckbox(); }
}

public class LinuxWidgetFactory implements WidgetFactory {
    public Button   createButton()   { return new LinuxButton(); }
    public Checkbox createCheckbox() { return new LinuxCheckbox(); }
}
// Client receives WidgetFactory at construction — never knows which OS
```

### Builder

```java
public final class ApiRequest {
    private final String url;
    private final String method;
    private final Duration timeout;  // optional

    private ApiRequest(Builder b) {
        this.url     = Objects.requireNonNull(b.url);
        this.method  = b.method;
        this.timeout = b.timeout;
    }

    public static final class Builder {
        private final String url;
        private String method   = "GET";
        private Duration timeout = Duration.ofSeconds(30);

        public Builder(String url) { this.url = url; }
        public Builder method(String m)   { this.method  = m; return this; }
        public Builder timeout(Duration t){ this.timeout = t; return this; }
        public ApiRequest build()        { return new ApiRequest(this); }
    }
}
// Usage: new ApiRequest.Builder("https://api.example.com").method("POST").build()
```

### Prototype (copy constructor — preferred over Cloneable)

```java
public final class QueryTemplate {
    private final String sql;
    private final List<Object> params;   // mutable — must deep copy

    public QueryTemplate(String sql, List<Object> params) {
        this.sql    = sql;
        this.params = new ArrayList<>(params);   // defensive copy
    }

    // Copy constructor — explicit deep copy
    public QueryTemplate(QueryTemplate other) {
        this.sql    = other.sql;
        this.params = new ArrayList<>(other.params);
    }
}
// Usage: QueryTemplate copy = new QueryTemplate(template);
```

---

## 12. Interview Q&As

Questions ordered by interview frequency — gotchas and traps first, then internals, then edge cases.

---

**Q: Why is Singleton considered an anti-pattern in modern Java?**
Singleton introduces global mutable state: any class can access it without declaring the dependency, making code hard to test (can't inject a test double), hard to reason about (hidden coupling), and thread-unsafe if state is mutable. The fix is dependency injection — inject the single instance via constructor, letting the DI container manage its lifecycle. The instance is still effectively singleton in scope, but the dependency is now explicit and injectable.

**Q: Thread-safe Singleton: DCL + volatile, enum, or Holder idiom — which is correct?**
All three are correct in Java 5+, but enum is the preferred idiom (Effective Java Item 3): it handles serialization automatically, prevents reflection-based instantiation, and requires no volatile. The Holder idiom (`static class Holder { static final X INSTANCE = new X(); }`) achieves lazy initialization via class-loading semantics without synchronization overhead. DCL + volatile is correct but verbose. Never use DCL without volatile — without it, the JVM's instruction reordering can publish a partially-constructed instance.

**Q: What is the "Cloneable problem" in Java, and how does Prototype avoid it?**
`Cloneable` is a marker interface that changes the behavior of `Object.clone()` — but `clone()` is defined on `Object`, not `Cloneable`, so there's no type contract. `clone()` performs a shallow copy by default (references are copied, not objects), throws `CloneNotSupportedException` (a checked exception on a `protected` method), and requires covariant return type overriding to be usable. The idiomatic Java fix: use a copy constructor (`new Foo(Foo other)`) or a static factory (`Foo.copyOf(Foo other)`). Both give explicit control over deep vs shallow copy without the `Cloneable` contract problems.

**Q: Factory Method vs Abstract Factory — when does a factory of factories make sense?**
Use Abstract Factory when you need to create families of related objects that must be compatible with each other. Example: a UI library with `Button`, `Checkbox`, and `Dialog` — you want either all Windows-style or all Linux-style widgets, never a mix. Factory Method creates ONE type of product; the subclass decides the concrete class. Abstract Factory creates MULTIPLE types of products that belong to the same family. Abstract Factory is often implemented by composing multiple Factory Methods.

**Q: Builder vs telescoping constructors — what's the Effective Java argument?**
Effective Java Item 2: when a class has many optional parameters, the telescoping constructor pattern (one constructor per combination of parameters) is hard to read and easy to get wrong (two adjacent parameters of the same type are silently swappable). The JavaBeans pattern (no-arg constructor + setters) is readable but leaves the object in an inconsistent intermediate state and prevents immutability. Builder gives you: readable construction, validation at `build()` time, and an immutable product. Cost: more verbose code (extra Builder class); worth it for 4+ parameters.

**Q: What is the static factory method idiom (Effective Java Item 1) and how does it differ from GoF Factory Method?**
Static factory method (Item 1) is naming a static method `of()`, `from()`, `valueOf()`, `newInstance()`, etc. to create instances — `LocalDate.of(2024, 1, 1)`, `Optional.of(x)`, `List.of(...)`. Benefits: descriptive names, ability to return cached instances, can return a subtype. GoF Factory Method is a class-hierarchy pattern: a base class defines a `createProduct()` method that subclasses override to vary the concrete product type. These are unrelated patterns that share a naming similarity.

**Q: How does Spring use the Prototype pattern?**
Spring beans are singleton-scoped by default. With `@Scope("prototype")`, every `getBean()` call returns a new instance — the bean is "prototyped" from its definition. This is used for stateful beans that must not be shared (e.g., a per-request helper with mutable state). Gotcha: injecting a prototype bean into a singleton bean breaks the prototype behavior — the singleton holds one reference, so the prototype is only created once. Fix: inject `ApplicationContext` and call `getBean()` each time, or use `@Lookup` method injection.

**Q: Abstract Factory vs Strategy — both select an implementation at runtime. What's different?**
Strategy selects an algorithm (behavior). Abstract Factory selects a family of object creators (construction). A Strategy doesn't create objects — it performs an operation on objects that already exist. An Abstract Factory creates objects but doesn't define what to do with them. The confusion arises because Abstract Factory is often selected at runtime based on configuration (e.g., "Linux" vs "Windows") — which looks like Strategy's algorithm selection — but the output is factories, not computed results.

**Q: When does a Builder become over-engineering?**
When the object has 2–3 non-optional parameters and no future extensibility requirement. A plain constructor is more readable and avoids the boilerplate of a separate Builder class. Builder shines for: 4+ parameters (especially optional ones), immutable objects, objects requiring validation across fields at construction time, and objects where construction order matters. Lombok's `@Builder` annotation generates the Builder class automatically, which makes it low-overhead when using Lombok.

**Q: How does the Prototype pattern enable the "object pool" pattern?**
An object pool maintains a collection of pre-initialized objects (prototypes) and leases them to callers. When the caller returns the object, the pool resets it (restores to prototype state) and returns it to the pool. `HikariCP` connection pooling, thread pool reuse, and Netty ByteBuf pooling all apply this principle. Prototype provides the initial template; the pool manages the lifecycle of copies.

**Q: What is the Holder idiom for Singleton and why does it work without synchronization?**

```java
public class Singleton {
    private Singleton() {}
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}
```

The JVM guarantees that static initializers run exactly once, in a thread-safe manner, when the class is first loaded. `Holder` is only loaded when `getInstance()` is called — providing lazy initialization. The initialization happens inside the class loader's lock, so no explicit synchronization is needed. This is the preferred Singleton implementation after enum.

**Q: Prototype deep copy vs shallow copy — what breaks with shallow?**
Shallow copy copies references, not the referenced objects. If the original and the copy share a `List<Order>` reference, modifying one's list modifies both. This is a classic defensive copy failure. Fix: implement deep copy manually — clone each mutable field recursively, or serialize/deserialize the object. Immutable fields (String, Integer, LocalDate) are safe to share. Mutable fields (List, Map, Date, arrays) must be deep-copied. The alternative: make the shared state immutable by design, making shallow copy safe.

**Q: How does Java's enum-based Singleton handle serialization?**
`Enum` serialization is handled by the JVM itself — the serialized form is just the enum name, and deserialization always returns the existing instance via `Enum.valueOf()`. You never get a second instance through deserialization. With a traditional Singleton, you must implement `readResolve()` to return the existing instance after deserialization, or a deserialized copy breaks the single-instance guarantee. Enum eliminates this requirement entirely.

**Q: When would you choose Abstract Factory over a dependency injection framework?**
Abstract Factory is useful when: (a) the factory itself must be swappable at runtime (e.g., switching between a mock factory in tests and a real factory in production), (b) the creation logic is complex and must stay in the domain layer (not the DI container's scope), or (c) the system has no DI framework. In practice, most modern Java applications use Spring's DI container as the Abstract Factory — `@Profile("test")` selects the test factory, `@Profile("prod")` selects the prod factory. Pure Abstract Factory code is more common in library design (where you can't assume a DI framework).

**Q: Why does adding a new product type require modifying all Abstract Factories?**
Abstract Factory defines the set of products it creates (e.g., `createButton()`, `createCheckbox()`). Adding a new product (e.g., `createDialog()`) requires adding that method to the factory interface AND to every concrete factory. This is the "Open/Closed Principle violation" in Abstract Factory — it's closed to modification (you can add new concrete factories freely) but not open to new product types without modifying the interface. Mitigation: use a generic factory (`create(Class<T> type)`) or accept that product type extension is rare and the modification cost is justified.

---

## 13. Best Practices

- Prefer **enum Singleton** over DCL or synchronized methods — serialization-safe, reflection-safe, concise.
- Prefer **copy constructors** over `Cloneable` for Prototype — explicit, type-safe, deep-copy-friendly.
- Use **Builder** when you have 4+ parameters; prefer Java records for simple value objects with no optional fields.
- Treat **Factory Method** as the default choice when you need one layer of variation; escalate to **Abstract Factory** only when compatibility between product families is a hard requirement.
- Avoid letting **Abstract Factory** interfaces grow unbounded — each new product type breaks all existing concrete factories. Design the product set upfront or use a generic `create(Class<T>)` escape hatch.
- In Spring applications, prefer `@Bean` factory methods and `@Profile` over hand-rolled Abstract Factory implementations — the container is already a factory registry.
- Document the **copy depth** (shallow vs deep) on any Prototype implementation — treat it as part of the public contract.
