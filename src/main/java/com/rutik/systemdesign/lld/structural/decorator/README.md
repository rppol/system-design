# Decorator Pattern

## 1. Pattern Name & Category

**Pattern:** Decorator (also known as Wrapper)
**Category:** Structural
**GoF Classification:** Structural Design Pattern (Gang of Four, "Design Patterns: Elements of Reusable Object-Oriented Software", 1994)

---

## 2. Intent

Attach additional responsibilities to an object dynamically at runtime by wrapping it in decorator objects that share the same interface, providing a flexible alternative to subclassing for extending functionality.

---

## Intuition

> **One-line analogy**: Decorator is like adding toppings to a pizza — you start with a base pizza, add cheese (wraps it), add mushrooms (wraps that), add peppers (wraps that). Each layer adds to the previous without changing the interface; the result "is-a" pizza at every step.

**Mental model**: You want to add features to an object without subclassing (which creates a class explosion: LoggedSortedFilteredList, SortedFilteredList, LoggedFilteredList...). Instead, you wrap the object: `new LoggingList(new SortedList(new FilteredList(original)))`. Each wrapper implements the same interface as the wrapped object, adding behavior before/after delegation. You can compose any combination at runtime.

**Why it matters**: Decorator enables open/closed compliance — you add behavior by wrapping, not by modifying. Java's I/O streams (BufferedInputStream, GZIPInputStream) are the canonical example: wrap any InputStream with any combination of compression, buffering, encryption. Spring's AOP, Python's @decorators, and middleware patterns all implement this idea.

**Key insight**: The key requirement is that the Decorator and the wrapped component share the same interface — this is what enables transparent composition. A client using the wrapped object doesn't know or care how many layers of decoration exist.

---

## 3. Problem Statement

### The Core Problem
You want to add behavior to individual objects without affecting other objects of the same class. Using inheritance to add every combination of features creates a subclass explosion. Behaviors need to be mixed, matched, and stacked in various combinations, often at runtime.

### Scenario: I/O Stream Enhancement
You are building a data processing pipeline. You need streams with various optional capabilities:
- Basic file reading
- Buffering (for performance)
- GZIP decompression
- AES encryption/decryption
- Base64 encoding/decoding
- Progress tracking
- Checksum validation

With inheritance, every combination needs its own class:
```
FileInputStream
BufferedFileInputStream
GzipFileInputStream
EncryptedFileInputStream
BufferedGzipFileInputStream
BufferedEncryptedFileInputStream
GzipEncryptedFileInputStream
BufferedGzipEncryptedFileInputStream
... (2^n combinations for n features)
```

With 7 features, you'd need up to 128 subclasses. Adding a single new feature requires up to 64 new subclasses for every existing combination. This is clearly untenable.

Additionally:
- You can't mix features at runtime with inheritance.
- Each combination class duplicates the logic of its parents.
- You can't add a feature to just one stream instance — it applies to all objects of that class.

---

## 4. Solution

Define a **Decorator base class** that:
1. Implements the same interface as the component it wraps.
2. Holds a reference to a component.
3. Delegates all calls to the wrapped component, optionally adding behavior before or after.

Concrete Decorators extend the base decorator and add specific behavior. You compose them by wrapping:
```java
InputStream in = new ChecksumInputStream(
                     new GzipInputStream(
                         new BufferedInputStream(
                             new FileInputStream("data.gz"))));
```

Each wrapper adds one layer of behavior. The chain is transparent — everything still looks like an `InputStream`.

---

## 5. UML Structure

```
         +----------------------+
         |   <<interface>>      |
         |     Component        |
         +----------------------+
         | +operation()         |
         +----------------------+
                  ^
        __________|______________
       |                         |
+--------------+       +-------------------+
| ConcreteComp |       |  Decorator        |  (abstract)
| (FileStream) |       +-------------------+
+--------------+       | -wrapped: Component|
| +operation() |       | +operation()       |  delegates to wrapped.operation()
+--------------+       +-------------------+
                                ^
               _________________|_________________
              |                 |                  |
  +-------------------+ +-------------------+ +-------------------+
  | ConcreteDecorator | | ConcreteDecorator | | ConcreteDecorator |
  |   (Buffered)      | |   (Gzip)          | |   (Checksum)      |
  +-------------------+ +-------------------+ +-------------------+
  | +operation()      | | +operation()      | | +operation()      |
  +-------------------+ +-------------------+ +-------------------+

Client call flow: Checksum.op() -> Gzip.op() -> Buffered.op() -> File.op()
```

**The decorator's `operation()` can:**
- Add behavior **before** delegating: `preprocess(); wrapped.operation();`
- Add behavior **after** delegating: `result = wrapped.operation(); postprocess(result);`
- Do both: `pre(); result = wrapped.operation(); post(result);`
- Conditionally delegate: `if (condition) wrapped.operation(); else fallback();`

---

## 6. How It Works

**Step-by-step mechanics:**

1. **Client wraps the base component** in one or more decorators: `new GzipDecorator(new BufferedDecorator(new FileStream()))`.
2. **Client calls `operation()`** on the outermost decorator.
3. **Outermost decorator** adds its pre-behavior, then calls `wrapped.operation()`.
4. **Next decorator** in the chain adds its pre-behavior, delegates further.
5. **Innermost concrete component** executes the base operation.
6. **Results bubble back up** through the chain; each decorator adds post-behavior to the result as it returns.
7. **Client receives** the result after all decorators have processed it.

The key insight: every object in the chain sees the same interface. Decorators are stackable because they wrap and expose the same interface. The chain length is variable and chosen at runtime.

---

## 7. Key Components

| Component | Role | Description |
|-----------|------|-------------|
| **Component** | Common interface | Defines the interface for objects that can have responsibilities added dynamically |
| **Concrete Component** | Base object | The object to which additional responsibilities can be attached; provides basic behavior |
| **Decorator** | Abstract wrapper | Implements Component and holds a reference to a Component; delegates all operations |
| **Concrete Decorator** | Feature wrapper | Extends Decorator; adds specific behavior before/after delegating to wrapped component |

---

## 8. When to Use

- **Adding behaviors individually to objects** without affecting others of the same class.
- **Mix-and-match feature sets** — when you need arbitrary combinations of features at runtime.
- **Alternative to subclassing** when subclassing would cause a class explosion.
- **Cross-cutting concerns** — logging, metrics, caching, retries, authentication checks that can be applied to any operation.
- **Open/Closed compliance** — extend behavior without modifying existing classes.
- **Pipeline processing** — when data flows through a sequence of transformations.

### Concrete Examples
- Java I/O streams (`BufferedInputStream`, `GZIPInputStream`, `CipherInputStream`)
- HTTP middleware/filters (each filter wraps the next in the chain)
- GUI component embellishment (scrollable view, border, shadow)
- Coffee/order customization (base coffee + milk + sugar + flavor)
- Spring Security filter chain

---

## 9. When NOT to Use

- **Simple, stable feature sets** — if combinations are known and fixed, subclasses or configuration is simpler.
- **When order matters critically** — decorators are order-sensitive; if callers might apply them in the wrong order, the results can be wrong (e.g., encrypt then compress vs. compress then encrypt gives different sizes).
- **Many decorators on the same object** — deeply nested chains are hard to debug; a long chain can make stack traces cryptic.
- **When you need object identity** — `decorator != concreteComponent`; `instanceof` checks on the concrete type fail on the wrapped object.
- **When decorators share significant state** — if decorators need to share data, the chain design becomes awkward; consider a different pattern.
- **AOP frameworks are available** — in enterprise Java, Spring AOP handles cross-cutting concerns more cleanly than manual decorator chains.

---

## 10. Pros

- **Single Responsibility** — each decorator does one thing well.
- **Open/Closed Principle** — add new behavior by writing new decorators; no existing code changes.
- **Runtime composition** — feature combinations are decided at runtime, not compile-time.
- **No class explosion** — n features = n decorators, not 2^n subclasses.
- **Transparent to clients** — the decorated object is still a Component; client code needs no changes.
- **Stackable** — decorators can be layered in any order, any number of times.
- **Reversible** — (with careful design) decorators can be removed from the chain.

---

## 11. Cons

- **Order sensitivity** — the order in which decorators are applied affects the outcome; callers must know the correct order.
- **Many small objects** — each decorator is a separate object; a deeply decorated object is many objects in memory.
- **Debugging complexity** — stack traces through a long decorator chain are hard to read.
- **Identity problem** — the decorated object is not `instanceof` the concrete component; identity-based comparisons fail.
- **Interface proliferation** — the Component interface must declare all operations upfront; adding new operations later requires changing all decorators.
- **Initialization verbosity** — constructing a deeply nested decorator chain is verbose: `new A(new B(new C(new D(base))))`.
- **Not great for removing behaviors** — once wrapped, removing a specific decorator from the middle of a chain requires rebuilding the chain.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Runtime flexibility — add/compose behaviors dynamically | Order dependency — callers must apply decorators in the right sequence |
| Elimination of subclass explosion | More objects in memory per decorated instance |
| Single Responsibility for each decorator | Harder debugging — deep chains produce complex stack traces |
| Open/Closed compliance | Object identity breaks — the wrapper is not the wrappee |
| Transparent to clients | Component interface must be stable — adding operations breaks all decorators |

---

## 13. Common Pitfalls

1. **Wrong decoration order:** Encrypting after compressing vs. before compressing produces different (often broken) results. Document the required order.
2. **Forgetting to delegate:** A concrete decorator that handles a method but forgets to call `super.method()` or `wrapped.method()` silently breaks the chain.
3. **State in decorators:** Stateful decorators in multi-threaded environments need synchronization; each decorator instance is a shared mutable object.
4. **Too many decorators on one object:** Twenty-layer decorator chains are a design smell; consider a Builder that applies them in a configured sequence.
5. **Interface bloat:** Adding unrelated methods to the Component interface to support one specific decorator forces all decorators to implement no-op stubs.
6. **Equals/hashCode:** Since the decorator wraps the component, `equals()` on the decorator does not equal `equals()` on the component unless you implement delegation explicitly.
7. **Resource leak in chains:** If one decorator in a chain manages a resource (e.g., a file handle), closing the outermost decorator must propagate the `close()` call all the way down. Forgetting this leaks resources.

---

## 14. Real-World Usage

### Java Standard Library — The Canonical Example
The `java.io` package is entirely built on the Decorator pattern:
- `InputStream` is the Component interface.
- `FileInputStream`, `ByteArrayInputStream` are Concrete Components.
- `FilterInputStream` is the abstract Decorator.
- `BufferedInputStream`, `DataInputStream`, `PushbackInputStream`, `CipherInputStream` (from `javax.crypto`) are Concrete Decorators.

```java
InputStream in = new DataInputStream(
                     new BufferedInputStream(
                         new FileInputStream("file.dat")));
```

Similarly for `OutputStream`, `Reader`, `Writer`.

### Java Collections
- `Collections.unmodifiableList(list)` — wraps a list to make it unmodifiable.
- `Collections.synchronizedList(list)` — wraps a list to make it thread-safe.
- `Collections.checkedList(list, type)` — wraps a list to enforce type checking.

### Spring Framework
- **Spring Security** `FilterChainProxy` — the security filter chain is a decorator chain where each security filter wraps the next.
- **Spring AOP** — `@Transactional`, `@Cacheable`, `@Retryable` are effectively decorator patterns applied via proxies.
- **`TransactionAwareDataSourceProxy`** — wraps a `DataSource` to add transaction awareness.

### Jakarta EE / Servlets
- `HttpServletRequestWrapper` and `HttpServletResponseWrapper` — designed for subclassing to decorate request/response objects in filters.

### Lombok
- `@Delegate` annotation essentially auto-generates the delegation boilerplate of a Decorator.

---

## 15. Comparison with Similar Patterns

| Pattern | Intent | Key Difference |
|---------|--------|----------------|
| **Decorator** | Add responsibilities dynamically | Same interface in and out; wraps a single object to add behavior |
| **Adapter** | Make incompatible interfaces work together | Changes the interface; not about adding behavior |
| **Composite** | Represent part-whole hierarchies | Tree structure (1-to-many children); not about adding behavior to a single object |
| **Proxy** | Control access to an object | Same interface; focus is on access control/lazy loading, not behavior extension |
| **Strategy** | Define interchangeable algorithms | Replaces behavior; Decorator adds to behavior |
| **Chain of Responsibility** | Pass request along a chain | Not all handlers in the chain process the request; focus on handler selection |

**Decorator vs. Proxy:** Both wrap an object and implement the same interface. The intent differs: Proxy controls access (lazy init, security, remote); Decorator adds behavior. In practice, AOP proxies in Spring blend both.

**Decorator vs. Strategy:** Strategy replaces the core algorithm; Decorator adds behavior around an algorithm. You can have decorators wrapping strategies.

**Decorator vs. Composite:** Composite has zero-to-many children; Decorator has exactly one wrapped object. Both use recursive composition.

---

## 16. Interview Tips

### Common Questions

**Q: What is the Decorator pattern and how does it differ from inheritance?**
A: Decorator adds behavior to an object instance at runtime by wrapping it in another object with the same interface. Inheritance adds behavior to all instances of a class at compile-time. Decorator avoids subclass explosion by composing behaviors rather than inheriting them, and allows mixing and matching at runtime.

**Q: How does Decorator differ from Proxy?**
A: Both wrap an object implementing the same interface. The intent differs: Decorator adds new behavior (I/O buffering, encryption); Proxy controls access (lazy loading, security, remote delegation). In practice the structural difference is identical; intent is what separates them.

**Q: Give a real example of Decorator in the Java SDK.**
A: The `java.io` package. `BufferedInputStream` and `DataInputStream` are both decorators wrapping `InputStream`. `new DataInputStream(new BufferedInputStream(new FileInputStream("f")))` is three layers: file reading, buffering, and typed data reading, each a separate decorator.

**Q: What's the difference between Decorator and Composite?**
A: Composite represents part-whole hierarchies (tree structure; a composite has many children). Decorator wraps exactly one object to add behavior. Both use recursive composition and the same interface, but their structural and semantic intent are different.

**Q: What's the order problem with Decorator?**
A: The order in which decorators are applied matters. For example, in I/O: compressing an unencrypted stream is more efficient than encrypting a compressed stream, because encrypted data doesn't compress. Callers must apply decorators in the correct order, and this dependency is not enforced by the type system.

### What Interviewers Look For
- Clear articulation of the subclass explosion problem
- Real Java I/O example — this is expected for senior roles
- Distinction from Proxy (intent vs. structure)
- Order-sensitivity awareness
- Understanding of the interface stability requirement

---

## Cross-Perspective: HLD Connections

**HLD View — Where Decorator Appears in Distributed Systems**

- **Middleware pipeline** — HTTP server middleware is Decorator at the framework level: auth, rate limiting, request logging, distributed tracing, and compression each wrap the core handler as a decorator, adding behavior before and after the delegate call.
- **HTTP client interceptors** — Client-side interceptors (OkHttp `Interceptor`, gRPC `ClientInterceptor`) decorate outbound requests: adding auth headers, correlation IDs, retry logic, and metrics without modifying callers.
- **Repository caching** — A `CachingRepository` decorator wraps the real repository, checking a cache layer before delegating to the database. The service layer depends only on the `Repository` interface and is unaware of the caching layer.
- **Feature flag injection** — A `FeatureFlagDecorator` wraps a service and routes calls to either the old or new implementation based on a feature flag — adding behavior toggleability without modifying the wrapped service.

---

## 17. Best Practices

1. **Keep decorators focused** — each decorator should do exactly one thing (single responsibility).
2. **Delegate everything by default** — the base Decorator class should delegate all methods to the wrapped component; concrete decorators override only the methods they enhance.
3. **Use abstract Decorator base class** — avoid duplicating delegation boilerplate in every concrete decorator.
4. **Make Component interface stable** — adding new methods to the interface requires updating all decorators; minimize the interface.
5. **Document required order** — when decorator order matters, document it clearly at the interface and factory level.
6. **Use Builder or Factory for complex chains** — `new A(new B(new C(new D(base))))` is hard to read; provide a fluent builder.
7. **Propagate resource management** — if any decorator manages a resource, ensure `close()/release()` propagates through the chain via delegation.
8. **Avoid stateful decorators where possible** — state in decorators creates threading and lifecycle complications; keep decorators as stateless transformers.
9. **Consider AOP for cross-cutting concerns** — in Spring applications, `@Transactional`, `@Cacheable` are cleaner than manual decorator chains for cross-cutting concerns.
10. **Test each decorator in isolation** — because decorators implement the same interface, they can be tested with a mock wrapped component.
