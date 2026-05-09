# Adapter Pattern

## 1. Pattern Name & Category

**Pattern:** Adapter (also known as Wrapper)
**Category:** Structural
**GoF Classification:** Structural Design Pattern (Gang of Four, "Design Patterns: Elements of Reusable Object-Oriented Software", 1994)

---

## 2. Intent

Convert the interface of a class into another interface that clients expect, enabling classes with incompatible interfaces to work together without modifying their source code.

---

## Intuition

> **One-line analogy**: An Adapter is like a power outlet converter — you plug your US device (client) into the adapter, which plugs into a European outlet (adaptee). The device works without modification; neither side needs to know about the other.

**Mental model**: You have existing code that uses interface A. You have a third-party library that provides interface B. You can't change either. An Adapter class implements A (what the client expects) but internally delegates to B (what the adaptee provides). The client sees interface A; the adapter translates calls to interface B. Two codebases that were incompatible now work together.

**Why it matters**: Adapters are the primary technique for integrating legacy systems, third-party libraries, and external APIs without modifying existing code. Every "wrapper" pattern you write is likely an Adapter. They enable the Open/Closed Principle — extend the system without modifying existing code.

**Key insight**: The Adapter doesn't add business logic — it only translates interfaces. If you're adding logic while adapting, you may be conflating Adapter with Decorator. Keep Adapters thin and focused purely on interface translation.

---

## 3. Problem Statement

### The Core Problem
You have existing client code that depends on a specific interface. You want to integrate a new class (or a third-party library) that provides the same functionality but exposes a completely different interface. You cannot modify either the client or the new class.

### Scenario: Legacy Payment System Integration
Your e-commerce platform has a `PaymentProcessor` interface with a `processPayment(double amount, String currency)` method. All checkout logic is built against this interface.

You now need to integrate a third-party payment gateway (e.g., Stripe SDK) which has a completely different API:
```
stripeClient.charge(long amountInCents, String currencyCode, String token)
```

Problems:
- The Stripe SDK is a third-party library — you cannot modify it.
- Rewriting all checkout code to use Stripe directly violates the Open/Closed Principle.
- You may switch payment providers again in the future.
- You might need multiple payment providers running simultaneously.

Without the Adapter pattern, you'd have messy conditional logic scattered throughout the checkout code, or you'd be forced to create a tight coupling to one specific vendor.

---

## 4. Solution

Introduce an **Adapter** class that:
1. Implements the `PaymentProcessor` interface (what the client expects).
2. Holds a reference to the `StripeClient` (the adaptee).
3. Translates calls from the client's interface into calls the adaptee understands.

The client only ever sees the `PaymentProcessor` interface. The adapter handles all translation — unit conversion, parameter reordering, data transformation — invisibly.

---

## 5. UML Structure

### Class Adapter (using inheritance)
```
+------------------+         +-------------------+
|   <<interface>>  |         |     Adaptee        |
|     Target       |         |   (StripeClient)   |
+------------------+         +-------------------+
| +request()       |         | +specificRequest() |
+------------------+         +-------------------+
        ^                              ^
        |                              |
        +----------+-------------------+
                   |
           +----------------+
           |    Adapter     |
           +----------------+
           | +request()     |  <-- calls specificRequest() internally
           +----------------+
```

### Object Adapter (using composition — preferred)
```
+------------------+        uses       +-------------------+
|   <<interface>>  |<---------+        |     Adaptee        |
|     Target       |          |        |   (StripeClient)   |
+------------------+          |        +-------------------+
| +request()       |          |        | +specificRequest() |
+------------------+          |        +-------------------+
        ^                     |                 ^
        |                     |                 | (holds reference)
+----------------+            |        +--------+---------+
|    Client      +------------+        |    Adapter        |
+----------------+                     +------------------+
| -target:Target |                     | -adaptee:Adaptee  |
| +doWork()      |                     | +request()        |
+----------------+                     +------------------+
```

### Two-Way (Bidirectional) Adapter
Implements both Target and Adaptee interfaces so objects from either side can use it.

---

## 6. How It Works

**Step-by-step mechanics:**

1. **Client calls** `target.request()` on what it believes is a normal `Target` object.
2. **The Adapter** receives this call. Its `request()` method is the entry point for translation.
3. **Translation happens** inside the adapter:
   - Parameter types are converted (e.g., `double` dollars → `long` cents).
   - Method names are mapped to the correct adaptee method.
   - Return types are converted back if needed.
4. **Adapter delegates** to `adaptee.specificRequest(translatedParams)`.
5. **The adaptee** executes its native logic and returns a result.
6. **The adapter translates** the return value back to the format the client expects.
7. **The client receives** a result in the format it understands — completely unaware of the adaptee.

The client is decoupled from the adaptee. Neither needs to know about the other. Only the adapter knows both interfaces.

---

## 7. Key Components

| Component | Role | Description |
|-----------|------|-------------|
| **Target** | Interface the client expects | Defines the domain-specific interface that Client uses |
| **Client** | Consumer of the Target interface | Collaborates with objects conforming to the Target interface |
| **Adaptee** | The class being adapted | Has a useful interface but incompatible with Target |
| **Adapter** | The translator | Implements Target, wraps Adaptee, translates calls |

**Two variants:**
- **Class Adapter:** Adapter extends Adaptee (uses multiple inheritance — only possible in languages supporting it like C++)
- **Object Adapter:** Adapter holds a reference to Adaptee (uses composition — preferred in Java)

---

## 8. When to Use

- **Integrating third-party libraries** whose interfaces don't match your domain model.
- **Legacy system integration** where old code has a different interface than new code.
- **When you want to reuse existing classes** but their interface doesn't match what's needed.
- **When you're building a library** and want to provide a standard interface that works with various implementations.
- **Multiple provider support** (e.g., multiple payment gateways, multiple logging frameworks) where each provider has a different API but you want a unified interface.
- **Migrating gradually** from an old API to a new one without a big-bang rewrite.

### Concrete Examples
- Adapting Java's `Enumeration` to `Iterator`
- Adapting a `Socket` to an `InputStream`
- Adapting XML parsers behind a common `DocumentParser` interface
- Adapting different database drivers behind JDBC's `Connection` interface

---

## 9. When NOT to Use

- **When you can modify the source** — if you own both classes, just refactor instead of wrapping.
- **When the interfaces are too different** — if adapting requires significant business logic, consider a Facade or a service layer.
- **Premature abstraction** — don't create adapters speculatively for providers you haven't integrated yet.
- **When the interface gap is trivial** — a one-line translation doesn't need a full adapter class; a lambda or method reference suffices.
- **When you need object identity** — the adapter is a different object than the adaptee; code checking `instanceof` Adaptee will fail.

---

## 10. Pros

- **Open/Closed Principle:** Add new adaptees without changing client code.
- **Single Responsibility:** Translation logic is isolated in the adapter class.
- **Testability:** Clients are tested against the Target interface; adapters can be mocked.
- **Flexibility:** Swap adaptees by swapping adapters — clients notice nothing.
- **Reusability:** Existing classes (even third-party ones) can be reused without modification.
- **Separation of concerns:** Interface conversion is separated from business logic.
- **Gradual migration:** Can introduce new APIs alongside old ones via adapters.

---

## 11. Cons

- **Increased complexity:** Adds extra classes and indirection for what is sometimes simple delegation.
- **Runtime overhead:** Each call goes through an extra layer of indirection.
- **Transparency issues:** Callers don't know they're talking to an adapter; debugging can be confusing.
- **Not a complete match:** Some features of the adaptee may be impossible to expose through the Target interface.
- **Proliferation risk:** Teams sometimes create adapters for every class, even when unnecessary, leading to an adapter explosion.
- **Adapter debt:** Over time, adapters can accumulate and become a maintenance burden if the underlying APIs change.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Decoupling from specific implementations | A thin extra layer of indirection |
| Ability to swap providers easily | Slight increase in class count |
| Consistency — all providers look the same to clients | Some features of the adaptee may not be expressible through Target |
| Testability via interface-based mocking | Object identity is broken (adapter != adaptee) |
| Open/Closed compliance | Developers must know adapters exist when debugging |

---

## 13. Common Pitfalls

1. **Leaking adaptee types:** Exposing `StripeClient`-specific exceptions or return types through the adapter, defeating the abstraction.
2. **Fat adapter:** Putting business logic inside the adapter instead of just translation. Adapters should be dumb translators.
3. **Ignoring bidirectionality:** In some protocols, both sides need to call each other; a one-way adapter breaks the contract.
4. **Not handling exceptions:** The adaptee may throw its own exception types; the adapter must catch and remap them to Target-compatible exceptions.
5. **Stateful adaptees:** If the adaptee is stateful, the adapter must carefully manage that state, especially in concurrent environments.
6. **Forgetting null safety:** When translating parameters, null values from the client may not map naturally to the adaptee's expected types.
7. **Over-adapting:** Creating adapters for every class "just in case" — this adds complexity without corresponding value.

---

## 14. Real-World Usage

### Java Standard Library
- `java.io.InputStreamReader` — adapts `InputStream` (byte stream) to `Reader` (character stream).
- `java.io.OutputStreamWriter` — adapts `OutputStream` to `Writer`.
- `java.util.Arrays.asList()` — adapts an array to the `List` interface.
- `Collections.list(Enumeration)` — adapts old `Enumeration` to `ArrayList`.
- `java.util.logging.Handler` adapters for SLF4J.

### Spring Framework
- `HandlerAdapter` in Spring MVC — adapts various handler types (`@Controller`, `HttpRequestHandler`, `Servlet`) to a common `ModelAndView handle()` interface, so `DispatcherServlet` doesn't care what type of handler it's calling.
- `MessageConverter` adapters for different media types (JSON, XML, etc.).

### Android SDK
- `RecyclerView.Adapter` — adapts arbitrary data sources (arrays, databases, network) to the `RecyclerView`'s item view mechanism.
- `CursorAdapter` — adapts a `Cursor` (database result) to a `ListView`.

### Hibernate / JPA
- `SessionFactory` adapters between different database dialects.
- Type adapters converting between Java types and SQL types.

### Apache Commons
- `IteratorUtils.asIterator(Enumeration)` — adapts old Java `Enumeration` to modern `Iterator`.

---

## 15. Comparison with Similar Patterns

| Pattern | Intent | Key Difference |
|---------|--------|----------------|
| **Adapter** | Make incompatible interfaces work together | Changes the interface of an existing object |
| **Decorator** | Add responsibilities to an object | Same interface in and out; wraps to add behavior |
| **Facade** | Provide a simplified interface to a subsystem | Defines a new simplified interface; doesn't make two existing ones compatible |
| **Proxy** | Control access to an object | Same interface; focus is access control, not interface translation |
| **Bridge** | Decouple abstraction from implementation | Separates a hierarchy into two dimensions designed to vary independently |

**Adapter vs. Decorator:** Adapter changes the interface; Decorator keeps the same interface but adds behavior.
**Adapter vs. Facade:** Facade simplifies a subsystem's interface from scratch; Adapter translates between two existing interfaces.
**Adapter vs. Proxy:** Proxy preserves the exact interface; Adapter changes it.

---

## 16. Interview Tips

### Common Questions

**Q: What is the Adapter pattern and when would you use it?**
A: The Adapter pattern converts one interface to another that a client expects. Use it when integrating third-party libraries or legacy systems whose interfaces differ from your domain model.

**Q: What's the difference between Class Adapter and Object Adapter?**
A: Class Adapter uses inheritance to extend the Adaptee and implement the Target — requires multiple inheritance (not directly supported in Java). Object Adapter uses composition, holding an Adaptee reference — the preferred approach in Java because it's more flexible and doesn't suffer from inheritance coupling.

**Q: How does Adapter differ from Facade?**
A: Adapter makes two existing incompatible interfaces work together. Facade creates a new simplified interface to a complex subsystem. Adapter is about compatibility; Facade is about simplification.

**Q: How does Adapter differ from Decorator?**
A: The key difference is the interface: Adapter changes it, Decorator preserves it. A Decorator wraps an object to add behavior while keeping the same interface; an Adapter wraps to translate between interfaces.

**Q: Give a real-world example from the Java SDK.**
A: `InputStreamReader` is a classic example — it adapts the byte-stream `InputStream` interface to the character-stream `Reader` interface, translating bytes to characters using a specified charset.

### What Interviewers Look For
- Clear articulation of the interface mismatch problem
- Understanding of composition over inheritance
- Ability to distinguish from Decorator, Facade, and Proxy
- Concrete real-world example (not just textbook)
- Awareness of exception translation as a real concern

---

## Cross-Perspective: HLD Connections

**HLD View — Where Adapter Appears in Distributed Systems**

- **Third-party API integration** — Wrapping a legacy payment gateway's SOAP/XML API behind a clean JSON interface that matches your internal `PaymentProvider` contract. The adapter absorbs the translation; your code never sees the external format.
- **Protocol adapters** — API gateways translate between REST (HTTP/JSON) and gRPC (HTTP/2 + Protobuf). The adapter wraps the gRPC stub and exposes a REST interface to external clients.
- **Event format normalization** — In event-driven systems, events from multiple third-party sources arrive in different schemas. An adapter normalizes each source to a canonical internal event format before publishing to the internal bus.
- **Storage abstraction** — A `BlobStorageAdapter` wraps AWS S3, GCS, and Azure Blob Storage behind a common `BlobStorage` interface. Switching cloud providers means deploying a different adapter, not touching application code.

---

## 17. Best Practices

1. **Prefer Object Adapter over Class Adapter** in Java — composition is more flexible than inheritance.
2. **Keep adapters thin** — only translate; no business logic inside.
3. **Translate exceptions** — don't let adaptee-specific exceptions leak through; catch and remap to domain exceptions.
4. **Name clearly** — use the `XxxAdapter` naming convention so the role is obvious.
5. **Consider interfaces on both sides** — program the adapter to the Target interface and the Adaptee's narrowest useful interface to minimize coupling.
6. **One adapter per adaptee** — don't try to make a single adapter translate multiple incompatible adaptees; use separate adapters.
7. **Handle null and edge cases** — translate empty results, null values, and error conditions explicitly.
8. **Document the translation** — comment why each translation step exists, especially non-obvious type conversions.
9. **Use dependency injection** — inject the adaptee into the adapter rather than constructing it internally, for testability.
10. **Consider using Factory or Registry** — when you have many adapters for different providers, use a factory to select the right adapter at runtime.
