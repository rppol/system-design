# Proxy Pattern

## 1. Pattern Name & Category

**Pattern:** Proxy
**Category:** Structural (GoF)
**GoF Classification:** Structural Design Pattern — Chapter 4 of "Design Patterns: Elements of Reusable Object-Oriented Software" by Gamma, Helm, Johnson, Vlissides.

---

## 2. Intent

Provide a surrogate or placeholder for another object to control access to it — adding behavior such as lazy initialization, access control, logging, caching, or remote communication without the real object or client knowing.

---

## Intuition

> **One-line analogy**: A Proxy is like a secretary who intercepts calls to the boss — screening callers, scheduling, or handling simple requests without disturbing the boss. The caller thinks they're talking to the boss; the secretary controls access.

**Mental model**: You want to control access to an object — delay creation until needed (virtual proxy), check permissions before delegating (protection proxy), log or cache calls (smart proxy), or communicate with remote objects (remote proxy). The Proxy implements the same interface as the real object, so clients don't know they're using a proxy. All control logic lives in the proxy.

**Why it matters**: Proxies are ubiquitous in frameworks — Spring's `@Transactional` and `@Cacheable` use dynamic proxies; Hibernate's lazy loading uses proxies to defer database queries; React's virtual DOM is a proxy for the real DOM. Understanding proxies explains how many "magic" framework features work.

**Key insight**: Proxy and Decorator are structurally identical (both wrap and delegate), but their intent differs. Proxy controls access to the real subject; Decorator adds new behavior. A proxy always has a reference to the real object it controls; a Decorator may be composed without knowing the final component.

---

## 3. Problem Statement

### The Problem
Sometimes you need to control access to an object because:
- The object is **expensive to create** and should be instantiated lazily.
- The object is on a **remote server** and network calls need to be abstracted.
- Access to the object needs to be **restricted** based on permissions.
- You need to **log, cache, or count** access without modifying the real object.
- The object's interface needs to be preserved while **adding pre/post-processing**.

Directly modifying the target object to add these concerns violates the Single Responsibility Principle and Open/Closed Principle.

### Scenario
Your application loads high-resolution images for display. Each image is 5–10 MB and takes 2 seconds to load from disk. The user opens a document with 50 embedded images. Loading all 50 images on startup takes 100 seconds and uses 500 MB of memory, but the user may only view 3 images during the session. The real `HighResImage` object is too expensive to create eagerly — but you still need an object to represent each image in the document's image list.

---

## 4. Solution

Create a **Proxy** class that implements the same interface as the real object (`Image`). The proxy holds a reference to the real object but starts with it uninitialized. When the client first accesses a method that requires the real object (e.g., `display()`), the proxy instantiates the real object and delegates the call. For subsequent calls, the real object is already loaded. The client holds a reference to `Image` — it cannot tell whether it has a proxy or the real object.

---

## 5. UML Structure

```
Client
  |
  | uses
  v
+-------------------+           (implements same interface)
|   <<interface>>   |
|     Subject       |
|-------------------|
| + request()       |
+-------------------+
        ^                   ^
        |                   |
+---------------+   +-----------------------+
|  RealSubject  |   |        Proxy          |
|---------------|   |-----------------------|
| + request()   |   | - realSubject: ref    |
+---------------+   | - [access state]      |
        ^            |-----------------------|
        |            | + request()           |
        |  creates / holds reference         |
        +------------------------------------+

Proxy wraps RealSubject and mediates all calls.
```

**Proxy Types Structural Variants:**

```
Virtual Proxy:   Proxy creates RealSubject lazily on first call
Remote Proxy:    Proxy handles network communication to a remote RealSubject
Protection Proxy: Proxy checks permissions before forwarding
Caching Proxy:   Proxy stores results and returns cache on repeated calls
Logging Proxy:   Proxy logs before/after delegating
Smart Reference: Proxy performs ref-counting, locking, or loading
```

---

## 6. How It Works — Step-by-Step

### Virtual Proxy (Lazy Loading)
1. Client is given an `ImageProxy` that implements `Image`. The real `HighResImage` is NOT loaded yet.
2. `imageProxy.display()` is called.
3. Proxy checks: is `realImage == null`? Yes — proxy creates `new HighResImage(filename)` (expensive load happens here, on demand).
4. Proxy calls `realImage.display()` and returns the result.
5. On subsequent calls to `display()`, `realImage != null`, so the proxy skips creation and delegates directly.

### Protection Proxy (Access Control)
1. Client calls `proxy.sensitiveOperation()`.
2. Proxy checks the caller's role/permissions.
3. If authorized: delegates to `realSubject.sensitiveOperation()`.
4. If not authorized: throws `AccessDeniedException` or returns an error — `realSubject` is never touched.

### Caching Proxy
1. Client calls `proxy.fetchData(key)`.
2. Proxy checks its internal cache for `key`.
3. Cache hit: return cached result immediately — `realSubject` is never called.
4. Cache miss: delegate to `realSubject.fetchData(key)`, store result in cache, return.

---

## 7. Key Components

| Role | Description |
|------|-------------|
| **Subject (interface/abstract)** | The common interface implemented by both RealSubject and Proxy. The client programs to this interface. |
| **RealSubject** | The actual object doing the work. May be expensive, remote, or sensitive. |
| **Proxy** | Implements Subject. Holds a reference to RealSubject. Controls access, adds behavior, and delegates to RealSubject. |
| **Client** | Uses the Subject interface. Is unaware of whether it holds a Proxy or RealSubject. |

---

## 8. When to Use

### Virtual Proxy
- Lazy loading of expensive resources (images, database connections, large files, API responses).
- Defer object creation until first access.

### Remote Proxy
- Representing an object that lives in a different process, JVM, or server (RMI, gRPC stubs, REST client wrappers).

### Protection Proxy
- Implementing access control without modifying the real object (RBAC, authentication checks).
- Different clients need different levels of access to the same service.

### Caching Proxy
- Caching results of expensive operations without modifying the real object.
- Memoization at the access layer.

### Logging / Monitoring Proxy
- Adding observability (logging, metrics, tracing) to an existing service without touching its code.
- AOP (Aspect-Oriented Programming) proxies for cross-cutting concerns.

### Smart Reference
- Adding reference counting, lock management, or copy-on-write semantics.

---

## 9. When NOT to Use

- **When direct access is fine**: If the object is cheap, always available, and access control is not needed, a proxy just adds indirection with no benefit.
- **When you can modify the class**: If you control the source code and can add the needed behavior directly (without violating SRP), do that instead.
- **When performance is critical in a tight loop**: Each proxy call adds a method dispatch. In hot paths with millions of calls per second, this overhead may matter.
- **When it leads to proxy proliferation**: If you need 10 different proxies for the same object, consider AOP or a decorator chain instead.

---

## 10. Pros

- **Lazy initialization**: Expensive objects are created only when needed, saving startup time and memory.
- **Access control without modifying the real object**: Security/authorization logic lives in the proxy, keeping RealSubject clean.
- **Transparent to clients**: Clients use the same interface regardless of whether they have a proxy or real object.
- **Open/Closed Principle**: New behavior (caching, logging) is added via a new proxy class without modifying existing code.
- **Single Responsibility**: Access control, caching, and logging concerns are separated from the real object's domain logic.
- **Remote access abstraction**: Remote proxies hide the complexity of network communication from clients.
- **Testability**: Proxy interface makes it easy to swap real objects with test doubles.

---

## 11. Cons

- **Increased number of classes**: Each proxied interface requires a new Proxy class — this proliferates in large codebases.
- **Response time latency**: The extra indirection adds latency. For remote proxies, the overhead can be significant (network serialization).
- **Complexity**: The pattern is simple in concept but can become complex when proxies are chained or when the Subject interface is large.
- **Interface explosion**: If the Subject has many methods, the Proxy must implement (and delegate) all of them — even if it only cares about one.
- **Dynamic proxy maintenance**: When methods are added to the Subject interface, all Proxies must be updated.
- **Debugging difficulty**: Stack traces pass through the proxy layer, which can obscure where logic actually executes.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Lazy/controlled access to expensive objects | Additional indirection on every call |
| Separation of cross-cutting concerns | More classes to maintain |
| Access control without modifying RealSubject | All interface methods must be delegated |
| Transparent caching/logging/monitoring | Harder to debug — stack traces include proxy frames |
| Remote access abstraction | Network latency cost for remote proxies |

---

## 13. Common Pitfalls

1. **Proxy does not implement the full Subject interface**: If the client casts to a specific method not in the interface, the illusion breaks. Always implement every method of the Subject interface.
2. **Circular initialization in Virtual Proxy**: The proxy creates the real object inside a method called during construction. Ensure lazy initialization only triggers on actual use, not during proxy creation.
3. **Not handling thread safety in lazy initialization**: `if (realObject == null) { realObject = new ... }` is a data race in multi-threaded code. Use double-checked locking with `volatile` or `AtomicReference`.
4. **Caching Proxy returning stale data**: A caching proxy must have a cache invalidation strategy. Without it, clients see stale results indefinitely.
5. **Protection Proxy checking wrong context**: Security checks must happen at the right level. Checking permissions in the proxy but not in the real object means direct-access bypasses the check entirely.
6. **Confusing Proxy with Decorator**: The proxy controls access to a specific real object; the Decorator adds new behavior and can wrap objects of any compatible type. Proxy focuses on access control and delegation; Decorator focuses on behavioral extension.

---

## 14. Real-World Usage

### Java Standard Library
- **`java.lang.reflect.Proxy`** — Java's built-in dynamic proxy mechanism creates proxy classes at runtime that implement specified interfaces and delegate to an `InvocationHandler`.
- **`java.rmi.Remote`** — RMI (Remote Method Invocation) uses remote proxies (stubs) to represent remote objects locally.

### Spring Framework
- **Spring AOP** — Spring's AOP is implemented entirely using JDK dynamic proxies (for interface-based beans) and CGLIB proxies (for class-based beans). Every `@Transactional`, `@Cacheable`, `@Async`, and `@Secured` annotation creates a proxy around the target bean.
- **`@Transactional`** — The transactional proxy intercepts method calls, starts/commits/rolls back transactions transparently.
- **`@Cacheable`** — The caching proxy intercepts method calls, checks the cache, and skips the real call on a cache hit.
- **`@Lazy` bean injection** — Spring injects a proxy for lazily-initialized beans so they are not created until first use.
- **`ProxyFactoryBean`** — Spring's explicit API for creating AOP proxies.

### Hibernate / JPA
- **Lazy-loaded associations** — `@OneToMany(fetch = FetchType.LAZY)` returns a Hibernate proxy collection that loads from the database only when iterated.
- **Entity proxies** — `session.getReference()` returns a proxy; the database is not hit until a field is accessed.

### gRPC & REST Clients
- Generated gRPC stubs are remote proxies that serialize/deserialize calls over the network.
- Feign clients in Spring Cloud are dynamic proxies over HTTP REST endpoints.

---

## 15. Comparison with Similar Patterns

| Pattern | Purpose | Key Difference |
|---------|---------|----------------|
| **Decorator** | Adds behavior to an object dynamically | Decorator adds *new functionality*; Proxy *controls access* to existing functionality. Decorator is applied to behavior; Proxy is applied to access/lifecycle. |
| **Adapter** | Converts one interface to another | Adapter changes the interface; Proxy preserves the same interface. |
| **Facade** | Simplifies access to a *subsystem* | Facade wraps many classes; Proxy wraps *one* object. |
| **Flyweight** | Shares objects to save memory | Flyweight focuses on memory optimization; Proxy focuses on access control/interception. |

---

## 16. Interview Tips

**Q: What is the Proxy pattern and what are its types?**
A: Give the one-line intent, then list the 5 types: Virtual, Remote, Protection, Caching, Logging. Give one concrete example per type. Spring AOP covers Logging/Caching/Protection; Hibernate covers Virtual; RMI/gRPC covers Remote.

**Q: How does Spring implement `@Transactional`?**
A: Spring creates a proxy (JDK dynamic proxy or CGLIB proxy) around the bean. When a `@Transactional` method is called, the proxy intercepts it, opens a transaction, delegates to the real method, and commits or rolls back based on the outcome. This is the Proxy pattern in practice.

**Q: What is the difference between Proxy and Decorator?**
A: Both wrap an object with the same interface. The key difference is intent: Proxy controls *access* (lazy init, auth, remote, caching); Decorator *adds new behavior* (additional formatting, logging in a chain). A Decorator is applied from the outside by the client to add capability; a Proxy is often transparent and managed by a framework.

**Q: How do you implement a thread-safe Virtual Proxy in Java?**
A: Use double-checked locking with a `volatile` field, or use `AtomicReference` with `compareAndSet`, or use `Supplier<T>` with lazy initialization (`Lazy<T>`).

**Q: Can you use Java's built-in dynamic proxy?**
A: Yes — `java.lang.reflect.Proxy.newProxyInstance()` creates a proxy at runtime. You provide an `InvocationHandler` that intercepts all method calls. The limitation is that it only works for interfaces, not concrete classes (use CGLIB for class-based proxies).

---

## Cross-Perspective: HLD Connections

**HLD View — Where Proxy Appears in Distributed Systems**

- **Service mesh sidecars** — Envoy and Linkerd proxies are the Proxy pattern at infrastructure scale: they intercept all inbound and outbound traffic for a service pod, adding retries, timeouts, mTLS, load balancing, and distributed tracing — completely transparent to the service code.
- **Circuit breaker** — A circuit breaker wraps service calls as a Proxy: in CLOSED state it passes through, in OPEN state it fails fast, in HALF-OPEN state it probes recovery. Resilience4j and Hystrix implement this as a proxy layer.
- **CDN edge node** — A CDN edge is a caching proxy: clients connect to the edge address; the edge either serves cached content or delegates to the origin. The URL is identical — the client has no knowledge of the proxy layer.
- **Spring AOP** — Spring's `@Transactional`, `@Cacheable`, and `@Async` annotations create JDK dynamic proxies or CGLIB subclass proxies that intercept method calls to add transaction management, caching, and async execution.

---

## 17. Best Practices

1. **Always program to the Subject interface**: Both the client and the proxy should use the interface, never the concrete class. This keeps the proxy transparent.
2. **Use Java dynamic proxies or CGLIB for large interfaces**: Manually delegating 20 methods in a proxy class is error-prone. Use `java.lang.reflect.Proxy` or AOP frameworks for interface-heavy proxies.
3. **Thread-safe lazy initialization**: Use `volatile` + double-checked locking or `AtomicReference` in virtual proxies. A non-thread-safe lazy proxy in a web application will cause race conditions.
4. **Cache invalidation strategy for caching proxies**: Always define TTL or event-based invalidation. An eternal caching proxy is a bug waiting to happen.
5. **Fail-fast in protection proxies**: Check permissions at the beginning of the method before any side effects. Never partially execute a privileged operation and then reject.
6. **Keep the proxy thin**: The proxy should add one concern (caching OR logging OR access control). Stacking multiple concerns into one proxy class violates SRP — chain multiple proxies or use AOP instead.
7. **Test the proxy and the real object independently**: Write unit tests for the RealSubject alone, and separate tests that verify the proxy's specific behavior (caching, auth) using a mock RealSubject.
