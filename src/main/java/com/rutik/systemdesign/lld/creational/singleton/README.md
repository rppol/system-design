# Singleton Pattern

## 1. Pattern Name & Category

**Name:** Singleton
**Category:** Creational (GoF)
**GoF Classification:** Gang of Four — Creational Design Pattern
**Book Reference:** "Design Patterns: Elements of Reusable Object-Oriented Software" (Gamma et al., 1994)

---

## 2. Intent

Ensure a class has only one instance and provide a global point of access to it.

---

## Intuition

> **One-line analogy**: A Singleton is like the president of a country — there can only be one at a time, and everyone knows how to reach "the president" without needing a personal address.

**Mental model**: Some resources must be shared because creating multiple copies would be wasteful or incorrect — a database connection pool, a logger, a configuration manager. The Singleton ensures the class itself manages "there is only one of me," providing a single global access point without callers needing to know each other.

**Why it matters**: Without Singleton, code relying on shared state either passes the object everywhere (verbose) or creates multiple instances (bugs). The pattern solves the "unique shared resource" problem cleanly at the language level.

**Key insight**: The Singleton's main weakness is hidden global state — it makes code hard to test (can't swap out for a mock) and introduces implicit coupling. Prefer Dependency Injection when testability matters; reserve Singleton for truly global resources like logging.

---

## 3. Problem Statement

### The Problem
Sometimes you need exactly one object of a particular kind in your system — no more, no fewer. Consider these scenarios:

- A **database connection pool** that manages a fixed set of connections. If multiple parts of the application each create their own pool, you waste resources and risk inconsistency.
- A **configuration manager** that reads settings from a file. Multiple instances might read the file at different times and diverge in state.
- A **logging service** that writes to a file. Concurrent instances could corrupt the log file with interleaved writes.
- A **thread pool** or **cache** that must be shared uniformly across the application.

### The Scenario
Imagine you're building an e-commerce application. You have a `DatabaseConnectionPool` class that opens connections to the database. Each instance of this class opens its own set of connections. If the `OrderService`, `InventoryService`, and `UserService` each instantiate their own pool, you now have 3x the expected number of open connections, memory usage, and you've bypassed the pool's limit entirely.

You could pass a single instance around as a constructor argument everywhere (dependency injection), but in deeply nested code, or when dealing with legacy systems, you need a guaranteed single instance with a known access point.

### What We Need
1. The class itself should control that only one instance is ever created.
2. Any caller anywhere in the codebase should get back that same instance.
3. The initialization should be lazy (only when first needed) or eager (at class loading).

---

## 4. Solution

The Singleton pattern solves this with three steps:

1. **Make the constructor private** — prevents external instantiation with `new`.
2. **Hold a static reference** to the single instance inside the class itself.
3. **Expose a static factory method** (e.g., `getInstance()`) that returns the existing instance or creates it on the first call.

This gives the class full control over its lifecycle while providing a well-known global access point.

---

## 5. UML Structure

```
+---------------------------+
|        Singleton          |
+---------------------------+
| - instance: Singleton     |  <-- static field
| - Singleton()             |  <-- private constructor
+---------------------------+
| + getInstance(): Singleton|  <-- static factory method
| + businessMethod()        |
+---------------------------+
        |
        | (returns the single instance)
        |
   [Client Code]
```

**Relationships:**
- No inheritance or composition — the Singleton manages itself.
- Clients call `Singleton.getInstance()` instead of `new Singleton()`.

---

## 6. How It Works — Step by Step

1. **First call:** Client calls `Singleton.getInstance()`. The static `instance` field is `null`. The method creates a new `Singleton` object, stores it in `instance`, and returns it.
2. **Subsequent calls:** Client calls `Singleton.getInstance()` again. The static `instance` field is already set. The method returns the existing object — no new object is created.
3. **Thread safety (double-checked locking):** In multithreaded environments, two threads could both see `instance == null` and both create objects. The solution is to `synchronize` the creation block and double-check the null condition after acquiring the lock.
4. **Eager initialization:** Alternatively, initialize the instance at class-load time using a static initializer. The JVM guarantees this is thread-safe.
5. **Bill Pugh / Initialization-on-demand holder:** Uses a nested static class whose static field is initialized only when the holder class is loaded (which happens only when `getInstance()` is called). This is the preferred Java idiom.

---

## 7. Key Components

| Component | Role |
|-----------|------|
| `Singleton` class | The class itself — owns the single instance and controls its lifecycle |
| `private static instance` | The static field holding the one-and-only object |
| `private constructor` | Prevents external `new` calls |
| `getInstance()` | The public static factory method — the global access point |

---

## 8. When to Use

- **Shared resource managers:** Database connection pools, thread pools, socket connection managers.
- **Configuration:** Application-wide settings loaded once from a file or environment.
- **Logging:** A single logger that routes all application log entries to the same output.
- **Caches:** An in-memory cache that must be consistent across all callers.
- **Service locators:** A registry that maps interface types to their implementations.
- **Hardware access:** A class wrapping a serial port, GPU, or printer spooler — only one process should control it.
- **Event buses / message brokers:** A central pub-sub hub inside an application.

---

## 9. When NOT to Use

- **Unit testability is important:** Singletons are notoriously hard to mock. If you need to test classes in isolation, the Singleton's global state bleeds across tests.
- **The "single instance" rule is artificial:** If the constraint isn't intrinsic to the domain (e.g., you just want to reuse an object for performance), use a factory or dependency injection instead.
- **Multithreaded correctness is difficult to guarantee:** Naive implementations are not thread-safe. If you can't apply double-checked locking or static initialization correctly, avoid it.
- **Distributed systems:** In a cluster, each JVM/process has its own Singleton — it's not actually a global singleton across nodes. Use a distributed cache or coordination service instead.
- **When the class has mutable state:** Singletons with mutable state are a form of shared global mutable state — a well-known source of subtle bugs.

---

## 10. Pros

- **Controlled instantiation:** Guarantees exactly one instance — enforced by the class itself, not by convention.
- **Global access:** Provides a well-known access point. No need to thread the object through every method signature.
- **Lazy initialization:** The object is created only when first needed, saving resources if it's never used.
- **Memory efficiency:** A single instance means a single allocation. Useful for heavyweight objects like connection pools.
- **Consistent state:** All callers share the same object, ensuring a consistent view of state (e.g., all callers read the same configuration).
- **Easy to implement:** A few lines of code achieve the pattern.

---

## 11. Cons

- **Global state:** Essentially a global variable in disguise. Global mutable state is a root cause of many hard-to-trace bugs.
- **Tight coupling:** Classes that call `Singleton.getInstance()` are directly coupled to the Singleton class, making them hard to change independently.
- **Testability:** Cannot be easily mocked or replaced in unit tests. Tests can pollute each other through shared singleton state.
- **Hides dependencies:** When a class uses a Singleton internally, the dependency is invisible from the constructor signature, making the API misleading.
- **Concurrency pitfalls:** Naive implementations are not thread-safe. Double-checked locking is subtle and easy to get wrong in languages without proper memory models.
- **Violates Single Responsibility Principle:** The class manages both its business logic and its own lifecycle.
- **Difficult to subclass:** Because the constructor is private and the class controls its own instantiation, inheritance is effectively blocked.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Guaranteed single instance | Testability (hard to mock/reset) |
| Global access point | Dependency transparency |
| Lazy initialization | Easy subclassing |
| Consistent shared state | Freedom from global state issues |
| Simple resource management | Flexibility to have multiple instances later |

**The core tradeoff:** Singletons trade testability and flexibility for convenience and control. In production code with dependency injection frameworks (Spring, Guice), the DI container manages lifecycle, so explicit Singletons are rarely needed.

---

## 13. Common Pitfalls

1. **Not handling thread safety:** The simplest Singleton is not thread-safe. Two threads can simultaneously pass the `null` check and create two instances. Always use double-checked locking, static initialization, or an enum.

2. **Double-checked locking without `volatile`:** Before Java 5, double-checked locking was broken. You must declare the `instance` field as `volatile` to prevent the JVM from reordering instructions.

3. **Serialization breaks Singleton:** If the Singleton implements `Serializable`, deserialization creates a new instance, defeating the pattern. You must implement `readResolve()` to return the existing instance.

4. **Reflection breaks Singleton:** Using `java.lang.reflect`, any code can call the private constructor and create a second instance. Defend by throwing an exception inside the constructor if the instance already exists.

5. **Class loader issues:** In complex environments (OSGi, web containers), multiple class loaders can each load the Singleton class independently, creating multiple instances. Scope the Singleton to the right class loader.

6. **Treating Singleton as a substitute for dependency injection:** Singletons hide dependencies. Prefer injecting a single-scoped bean via a DI container over a static Singleton.

7. **Holding long-lived state in tests:** Tests sharing a Singleton's state will interfere with each other. Always reset or stub the Singleton in tests.

---

## 14. Real-World Usage

| Framework / Library | Usage |
|--------------------|-------|
| **`java.lang.Runtime`** | `Runtime.getRuntime()` returns the single Runtime instance for the JVM. |
| **Spring Framework** | By default, all Spring beans are singletons (scoped to the application context). |
| **`java.awt.Desktop`** | `Desktop.getDesktop()` returns the singleton desktop instance. |
| **Android `Application`** | The `Application` class is a process-level singleton managed by the Android runtime. |
| **`java.util.logging.Logger`** | Loggers are cached by name — effectively singletons per logger name. |
| **Hibernate `SessionFactory`** | Typically configured as a singleton — expensive to create, shared across the app. |
| **`java.lang.System`** | `System.out`, `System.err`, `System.in` are single global instances. |
| **Kotlin `object`** | Kotlin's `object` declaration is a language-level Singleton. |

---

## 15. Comparison with Similar Patterns

| Pattern | How It Differs from Singleton |
|---------|-------------------------------|
| **Monostate** | Multiple instances exist, but all share the same static state. Avoids the private-constructor trick but achieves similar behavior. |
| **Factory Method** | Controls which type of object is created; does not constrain to one instance. |
| **Prototype** | Creates new instances by cloning; opposite of Singleton's "one only" constraint. |
| **Flyweight** | Shares instances to save memory, but can have many shared instances keyed by data; Singleton has exactly one. |
| **Service Locator** | Often implemented as a Singleton but provides a registry of services rather than a single service. |

---

## 16. Interview Tips

**Common Interview Questions:**

1. **"What is the Singleton pattern and when would you use it?"**
   Answer: State the intent (one instance, global access), give a concrete example (connection pool, config), and immediately mention the trade-off (testability, global state). Show you know when NOT to use it.

2. **"How do you make a Singleton thread-safe in Java?"**
   Answer: Mention four approaches in order of preference:
   - Enum-based Singleton (best — JVM guarantees thread safety, handles serialization)
   - Initialization-on-demand holder (lazy + thread-safe + no synchronization overhead)
   - `synchronized getInstance()` (simple, but locks on every call)
   - Double-checked locking with `volatile` (good performance, but subtle)

3. **"How does an Enum Singleton work?"**
   Answer: Java guarantees each enum value is instantiated once by the classloader. It's serialization-safe and reflection-safe out of the box.

4. **"Can you break a Singleton?"**
   Answer: Yes — via reflection (calling private constructor), serialization (deserializing creates a new instance), or multiple class loaders. Explain the defenses.

5. **"What's wrong with Singletons?"**
   Answer: Global state, hidden dependencies, testability issues, SRP violation. Mention that modern apps use DI containers instead.

**Key Phrases to Use:**
- "Lazy vs. eager initialization"
- "Thread safety — double-checked locking, `volatile`"
- "Enum Singleton is the safest in Java"
- "Bill Pugh / Initialization-on-demand holder idiom"

---

## Cross-Perspective: HLD Connections

**HLD View — Where Singleton Appears in Distributed Systems**

- **Connection pools** — Every microservice has a singleton database connection pool (HikariCP, c3p0). Creating a new pool per request would exhaust DB connections; the singleton lifecycle matches the service lifetime.
- **API gateway rate-limiter registry** — The per-client token bucket store is a singleton: one shared structure tracks quotas across all request-handling threads. Backed by Redis in distributed deployments.
- **Config managers** — Distributed config clients (Consul, etcd client, Spring Cloud Config) are singletons that hold the live config snapshot and propagate changes to all in-process consumers.
- **Distributed caveat** — A JVM Singleton only guarantees one instance per process. Across N microservice replicas, there are N singletons. State that must be globally unique requires distributed coordination (ZooKeeper, Redis locks), not a local Singleton.

---

## 17. Best Practices

1. **Prefer enum-based Singleton in Java** — it's the simplest, safest (thread-safe, serialization-safe, reflection-safe) implementation.

2. **Use the Initialization-on-demand holder idiom** for lazy initialization without synchronization overhead:
   ```java
   private static class Holder {
       static final Singleton INSTANCE = new Singleton();
   }
   public static Singleton getInstance() { return Holder.INSTANCE; }
   ```

3. **Declare `instance` as `volatile`** if using double-checked locking — required since Java 5 for correct memory visibility.

4. **Implement `readResolve()`** if the Singleton is serializable — return the existing instance to prevent deserialization from creating a second object.

5. **Prefer dependency injection over static access** — let a DI container (Spring, Guice) manage the singleton scope. Your classes declare `@Autowired` dependencies instead of calling `getInstance()`.

6. **Document clearly that the class is a Singleton** — future maintainers need to understand lifecycle assumptions.

7. **Reset the Singleton in tests** using a package-private or reflection-based reset method — or better, inject the instance so it can be mocked.

8. **Do not use Singleton for objects with complex initialization that can fail** — failure inside a static initializer causes `ExceptionInInitializerError`, which is hard to recover from.
