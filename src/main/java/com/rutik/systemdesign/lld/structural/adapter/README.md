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

### Production Anchor: Legacy LDAP -> OAuth2 Migration

A mid-size SaaS platform has 40 call sites scattered across 12 microservices invoking an in-house `LdapAuthService` running against an aging Active Directory cluster. Scale: ~500 auth requests/sec at peak, p99 latency 80ms, 99.95% availability requirement. Leadership decides to migrate to Okta OAuth2 over 6 months, but a flag-day cutover is impossible — different services have different release trains. Solution: introduce a new `AuthProvider` interface; build `OAuth2AuthProvider` (new) and `LdapAuthProviderAdapter` (wraps the legacy service). Callers depend only on `AuthProvider`; a feature flag routes between the two. Net result: zero changes to the 40 call sites during migration; rollback is a config flip.

```
                +----------------------+
                |     CallSites (40)   |
                +----------+-----------+
                           |
                           v
                +----------------------+
                |    AuthProvider      |  <-- target interface
                |    (interface)       |
                +----+-----------+-----+
                     |           |
        +------------+           +-------------+
        |                                      |
        v                                      v
+----------------+                  +-------------------------+
| OAuth2Provider |                  | LdapAuthProviderAdapter |
| (new code)     |                  |  - translates calls     |
+--------+-------+                  |  - maps exceptions      |
         |                          +-----------+-------------+
         v                                      |
   Okta / IdP                                   v
                                       +------------------+
                                       | LdapAuthService  |  (legacy, untouched)
                                       +------------------+
```

```java
// Target interface — what callers depend on
public interface AuthProvider {
    AuthResult authenticate(String username, String password) throws AuthException;
    UserPrincipal lookup(String username) throws AuthException;
}

// Adapter — wraps legacy LdapAuthService behind the new interface
public final class LdapAuthProviderAdapter implements AuthProvider {
    private final LdapAuthService legacy;          // adaptee
    private final MeterRegistry metrics;

    public LdapAuthProviderAdapter(LdapAuthService legacy, MeterRegistry metrics) {
        this.legacy = Objects.requireNonNull(legacy);
        this.metrics = metrics;
    }

    @Override
    public AuthResult authenticate(String username, String password) throws AuthException {
        Timer.Sample s = Timer.start(metrics);
        try {
            LdapBindResult r = legacy.bind(username, password.toCharArray());
            return new AuthResult(r.getDn(), r.getGroups(), Instant.now().plusSeconds(3600));
        } catch (LdapException e) {
            // anti-pattern fix: do NOT leak LdapException to callers
            throw mapException(e);
        } finally {
            s.stop(metrics.timer("auth.ldap.adapter"));
        }
    }

    @Override
    public UserPrincipal lookup(String username) throws AuthException {
        try {
            LdapEntry entry = legacy.searchByUid(username);
            return new UserPrincipal(entry.getUid(), entry.getEmail(), entry.getGroups());
        } catch (LdapException e) {
            throw mapException(e);
        }
    }

    private AuthException mapException(LdapException e) {
        if (e.getResultCode() == 49) return new InvalidCredentialsException(e);   // LDAP 49 = invalidCredentials
        if (e.getResultCode() == 32) return new UserNotFoundException(e);         // LDAP 32 = noSuchObject
        return new AuthException("LDAP failure", e);
    }
}
```

```java
// Wiring — feature flag selects implementation; callers never know
@Configuration
class AuthConfig {
    @Bean
    AuthProvider authProvider(@Value("${auth.provider}") String which,
                              LdapAuthService ldap, OktaClient okta, MeterRegistry m) {
        return switch (which) {
            case "ldap"   -> new LdapAuthProviderAdapter(ldap, m);
            case "oauth2" -> new OAuth2AuthProvider(okta, m);
            default       -> throw new IllegalStateException("Unknown provider: " + which);
        };
    }
}
```

### Famous Codebase Usages

- `java.io.InputStreamReader` — adapts a byte `InputStream` to a character `Reader` using a `Charset`. Constructor: `new InputStreamReader(in, StandardCharsets.UTF_8)`.
- `java.io.OutputStreamWriter` — symmetric `OutputStream` to `Writer` adapter.
- `java.util.Arrays.asList(T...)` — adapts a varargs array to a fixed-size `List` view backed by the array (mutations write through; `add`/`remove` throw `UnsupportedOperationException`).
- `Collections.list(Enumeration)` and `Collections.enumeration(Collection)` — bidirectional adapters between the legacy `Enumeration` and modern `Iterator`/`Collection`.
- `org.slf4j.impl.Log4jLoggerAdapter` — adapts an Apache Log4j `Logger` to the SLF4J `Logger` interface so application code can stay on SLF4J while the backend is Log4j.
- Spring MVC `org.springframework.web.servlet.HandlerAdapter` with implementations `RequestMappingHandlerAdapter`, `HttpRequestHandlerAdapter`, `SimpleControllerHandlerAdapter` — lets `DispatcherServlet` invoke any handler type through one `handle(req, resp, handler)` method.
- Android `RecyclerView.Adapter` — adapts arbitrary backing data (List, Cursor, Room paging source) to `RecyclerView`'s `onBindViewHolder` contract.

### Anti-patterns

**1. Adapter doing business logic, not translation**
```java
// BROKEN — adapter validates, charges fees, logs audit events
public class LegacyPaymentAdapter implements PaymentGateway {
    public PaymentResult charge(Money amount, Card card) {
        if (amount.isNegative()) throw new IllegalArgumentException();    // validation
        Money withFee = amount.add(amount.multiply(0.029));               // business logic
        auditLog.record("CHARGE", card.last4(), withFee);                 // cross-cutting
        return legacy.doCharge(card.pan(), withFee.cents());
    }
}
// FIX — adapter ONLY translates the interface; business stays where it belongs
public class LegacyPaymentAdapter implements PaymentGateway {
    private final LegacyPaymentClient legacy;
    public PaymentResult charge(Money amount, Card card) {
        LegacyTxn t = legacy.doCharge(card.pan(), amount.cents());
        return new PaymentResult(t.id(), t.status() == 0 ? OK : DECLINED);
    }
}
// Validation, fees, and auditing belong in PaymentService, which calls the adapter.
```

**2. Two-way adapter creating a circular dependency**
```java
// BROKEN — one class implements BOTH interfaces and translates each way
public class BiDirAdapter implements NewApi, OldApi {
    private NewApi newSide; private OldApi oldSide;     // both sides depend on this class
    // Now NewApi callers and OldApi callers both transit the same hub; any change cascades both ways.
}
// FIX — two one-way adapters plus a pure mapper
public class OldToNewAdapter implements NewApi { private final OldApi target; ... }
public class NewToOldAdapter implements OldApi { private final NewApi target; ... }
public final class PayloadMapper { static NewDto toNew(OldDto o) {...} static OldDto toOld(NewDto n) {...} }
```

**3. Adapter leaking adaptee exceptions**
```java
// BROKEN — LdapException propagates; every caller now transitively depends on the LDAP SDK
public AuthResult authenticate(String u, String p) throws LdapException {   // <- leak
    return new AuthResult(legacy.bind(u, p.toCharArray()).getDn());
}
// FIX — catch adaptee-specific exceptions, rethrow as domain exceptions
public AuthResult authenticate(String u, String p) throws AuthException {
    try { return new AuthResult(legacy.bind(u, p.toCharArray()).getDn()); }
    catch (LdapException e) { throw mapException(e); }
}
```

### Performance and Correctness Numbers

- Adapter call overhead in the LDAP scenario: 1.2µs added per call (one virtual dispatch + exception mapping table lookup) — negligible vs. the 80ms LDAP bind latency.
- The 40 call sites required zero edits during migration; the cutover PR changed only `application.yml` (`auth.provider: oauth2`).
- Post-migration, removing the adapter and the legacy service was a 1-day cleanup with no behavior risk because the new provider had been exercised in production for 8 weeks behind the flag.

### Migration Story

Month 1: introduce `AuthProvider` interface; ship `LdapAuthProviderAdapter` as the default — production behavior unchanged. Month 2-3: add `OAuth2AuthProvider`; enable for 1% of internal users via flag; compare results in shadow mode (call both, log mismatches). Month 4: ramp OAuth2 to 50%, then 100%. Month 5: legacy LDAP routes deprecated, alarms removed. Month 6: delete adapter, delete `LdapAuthService`, remove `org.springframework.ldap` dependency. The Adapter pattern made the migration reversible at every step — a property worth far more than the 1.2µs of overhead.

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

**Q: How does Adapter differ from Bridge — they both wrap an object behind an interface?**
A: The structural shape is similar (an object holding a reference to another and delegating), but the intent and timing differ. Adapter is retrofitted after the fact to make an already-existing, incompatible interface work with code that wasn't designed with it in mind — the Adaptee's interface is fixed and "wrong" from the client's perspective. Bridge is designed upfront, before either hierarchy has incompatible code, specifically so an abstraction hierarchy and an implementation hierarchy can each evolve and vary independently. In short: reach for Adapter when integrating something that already exists and doesn't fit; reach for Bridge when you're designing two axes of variation from day one.

**Q: What is a two-way (bidirectional) adapter and when is it needed?**
A: A two-way adapter implements both the Target and Adaptee interfaces so objects on either side can be passed to code expecting the other interface, without either side knowing an adapter is involved. It's needed when two systems need to call into each other symmetrically — for example, adapting between `java.util.Iterator` and an older `Enumeration`-based API where code on both sides may hand objects back and forth. The risk is that a single class implementing both interfaces becomes a hub that couples both hierarchies together, so changes to either interface can ripple through the shared adapter. In practice, prefer two separate one-way adapter classes (`OldToNewAdapter` and `NewToOldAdapter`) plus a shared mapper, unless the bidirectional case is simple and stable.

**Q: How would you adapt a single-method interface using a lambda instead of writing a full adapter class?**
A: When the Target is a functional interface (a single abstract method), you can often skip the adapter class entirely and use a lambda or method reference that calls the adaptee directly. For example, if `Target` is `interface Validator { boolean isValid(String s); }` and the adaptee is a third-party `LegacyStringChecker` with a `check(String)` method, you can write `Validator v = legacyChecker::check` instead of a dedicated `LegacyCheckerAdapter` class. This works because Java's functional interfaces let any compatible method reference or lambda serve as an implicit adapter at the call site. Use this lightweight approach for one-off, single-method translations; switch to a full adapter class once you need to adapt multiple methods, hold state, or translate exceptions.

**Q: How does the Adapter pattern relate to the Dependency Inversion Principle?**
A: Adapter is one of the primary mechanisms for satisfying DIP in practice — high-level code depends on an abstraction (the Target interface) that it owns, and the Adapter, not the high-level code, depends on the low-level concrete adaptee. Without the adapter, the high-level checkout logic would directly depend on `StripeClient`, a low-level detail, inverting the desired dependency direction. With the adapter, `PaymentProcessor` is owned by the domain layer, and `StripeAdapter` implements it while depending downward on the Stripe SDK — dependencies point toward the abstraction, not away from it. This is why Adapter is so common at architectural boundaries (ports and adapters / hexagonal architecture): the "port" is the Target interface and the "adapter" is literally this pattern.

**Q: What other adapter examples exist in the Java standard library besides `InputStreamReader`?**
A: `java.util.Arrays.asList(T...)` adapts a fixed-size array to the `List<T>` interface — it's a classic Object Adapter where mutations to the list write through to the backing array, but structural operations like `add`/`remove` throw `UnsupportedOperationException` because the array can't be resized. `Executors.callable(Runnable)` adapts a `Runnable` (which returns nothing) to a `Callable<Object>` (which returns a value), letting a `Runnable` be submitted where a `Callable` is required. Historically, the JDBC-ODBC bridge driver (`sun.jdbc.odbc.JdbcOdbcDriver`, removed in Java 8) adapted the JDBC `Driver`/`Connection` interfaces to ODBC calls, letting Java code talk to databases that only exposed ODBC drivers. These examples illustrate that adapters are everywhere once you start treating "wrap a thing in an interface my code expects" as the pattern's signature.

**Q: How do you unit test an adapter in isolation?**
A: Test the adapter against a mock or stub of the Adaptee, asserting that calls to the Target interface translate into the correct Adaptee calls with correctly transformed parameters, and that Adaptee responses (including exceptions) are correctly translated back. For the `LdapAuthProviderAdapter` example, you'd mock `LdapAuthService` to throw an `LdapException` with result code 49 and assert the adapter throws `InvalidCredentialsException`, without needing a real LDAP server. Because the adapter's only job is translation, its tests should be pure unit tests with no I/O — if a test needs a real database or network call, that logic probably belongs in the adaptee or a higher layer, not the adapter. Keep a separate, smaller suite of integration tests that exercise the adapter against the real adaptee to catch interface drift (e.g., the third-party SDK changing its method signatures).

**Q: Does the extra indirection layer in an Adapter cause a meaningful performance hit?**
A: In almost all cases, no — the cost is one additional virtual method call plus whatever lightweight transformation (unit conversion, field mapping) the adapter performs, typically sub-microsecond. In the LDAP-to-OAuth2 migration example, the adapter added about 1.2 microseconds per call against an 80ms LDAP bind latency — utterly negligible. The exception is adapters that perform expensive translation on every call (e.g., full object graph serialization/deserialization, reflection-based mapping per invocation) — in those cases, cache the mapping logic or precompute reusable converters rather than rebuilding them per call. As a practical guideline, profile before assuming the adapter is a bottleneck; the interface mismatch it solves is almost always worth far more than the translation cost.

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
