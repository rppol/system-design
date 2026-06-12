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

### Production Anchor: Resilient HTTP Client Stack

A backend service makes ~100k HTTP calls/day to a flaky third-party payments API. The team needs retries with exponential backoff, a circuit breaker to fail fast during outages, Prometheus metrics on every call, and a mutating header injector for the auth token. Each concern must be independently testable, removable, and orderable — and a junior engineer should be able to disable retries in dev without touching production code. Decorator stack: `MetricsClient -> CircuitBreakerClient -> RetryClient -> AuthClient -> BaseHttpClient`. After deployment, the retry decorator dropped the user-visible error rate from 2.0% to 0.10% (transient 502s now recovered); the circuit breaker bounded outage blast radius from 30s of timeouts to 5s of fast-fail.

```
              call(req)
                |
                v
       +----------------+
       | MetricsClient  |  records latency, status code
       +-------+--------+
               | call(req)
               v
       +-------------------+
       | CircuitBreakerCli |  short-circuits when open
       +-------+-----------+
               | call(req)
               v
       +----------------+
       | RetryClient    |  3 tries, exp backoff
       +-------+--------+
               | call(req)
               v
       +----------------+
       | AuthClient     |  adds Authorization header
       +-------+--------+
               | call(req)
               v
       +----------------+
       | BaseHttpClient |  real network I/O (java.net.http)
       +----------------+
```

```java
public interface HttpClient {
    HttpResponse call(HttpRequest req);
}

// Concrete component — the actual network call
public final class BaseHttpClient implements HttpClient {
    private final java.net.http.HttpClient jdk = java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2)).build();

    @Override public HttpResponse call(HttpRequest req) {
        try {
            var jdkReq = java.net.http.HttpRequest.newBuilder(URI.create(req.url()))
                .timeout(Duration.ofSeconds(5))
                .method(req.method(), BodyPublishers.ofByteArray(req.body()))
                .build();
            var resp = jdk.send(jdkReq, BodyHandlers.ofByteArray());
            return new HttpResponse(resp.statusCode(), resp.body());
        } catch (IOException | InterruptedException e) {
            throw new HttpFailure(e);
        }
    }
}

// Abstract decorator
public abstract class HttpClientDecorator implements HttpClient {
    protected final HttpClient delegate;
    protected HttpClientDecorator(HttpClient delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }
    // Anti-pattern fix #1: delegate identity to the wrapped object
    @Override public boolean equals(Object o)   { return delegate.equals(o); }
    @Override public int hashCode()             { return delegate.hashCode(); }
    @Override public String toString()          { return getClass().getSimpleName() + " -> " + delegate; }
}
```

```java
public final class RetryClient extends HttpClientDecorator {
    private final int maxAttempts; private final Duration baseBackoff;
    public RetryClient(HttpClient d, int maxAttempts, Duration baseBackoff) {
        super(d); this.maxAttempts = maxAttempts; this.baseBackoff = baseBackoff;
    }
    @Override public HttpResponse call(HttpRequest req) {
        HttpFailure last = null;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                HttpResponse r = delegate.call(req);
                if (!retriable(r.status())) return r;
            } catch (HttpFailure f) { last = f; }
            sleepBackoff(i);
        }
        throw last != null ? last : new HttpFailure("retries exhausted");
    }
    private boolean retriable(int s) { return s == 502 || s == 503 || s == 504; }
    private void sleepBackoff(int attempt) {
        long ms = baseBackoff.toMillis() * (1L << attempt) + ThreadLocalRandom.current().nextLong(50);
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

public final class MetricsClient extends HttpClientDecorator {
    private final MeterRegistry reg;
    public MetricsClient(HttpClient d, MeterRegistry reg) { super(d); this.reg = reg; }
    @Override public HttpResponse call(HttpRequest req) {
        Timer.Sample s = Timer.start(reg);
        try {
            HttpResponse r = delegate.call(req);
            s.stop(reg.timer("http.client", "status", String.valueOf(r.status())));
            return r;
        } catch (RuntimeException e) {
            s.stop(reg.timer("http.client", "status", "exception"));
            throw e;
        }
    }
}
```

```java
// Builder enforces the correct decorator ORDER (anti-pattern fix #2)
public final class ResilientHttpClientBuilder {
    private MeterRegistry registry; private int retries = 3;
    private Duration cbWait = Duration.ofSeconds(5); private Supplier<String> tokenSupplier;

    public ResilientHttpClientBuilder withRetries(int n) { this.retries = n; return this; }
    public ResilientHttpClientBuilder withMetrics(MeterRegistry r) { this.registry = r; return this; }
    public ResilientHttpClientBuilder withAuth(Supplier<String> t) { this.tokenSupplier = t; return this; }
    public ResilientHttpClientBuilder withCircuitBreaker(Duration w) { this.cbWait = w; return this; }

    public HttpClient build() {
        // ORDER MATTERS — auth must be innermost (applied per request);
        // retry wraps auth so each retry re-adds a fresh token;
        // circuit breaker wraps retry so total time is bounded;
        // metrics wraps everything to time the whole call.
        HttpClient c = new BaseHttpClient();
        if (tokenSupplier != null)  c = new AuthClient(c, tokenSupplier);
        c = new RetryClient(c, retries, Duration.ofMillis(100));
        c = new CircuitBreakerClient(c, cbWait);
        if (registry != null)       c = new MetricsClient(c, registry);
        return c;
    }
}
```

### Famous Codebase Usages

- `java.io.InputStream` chain — `new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream("data.gz"))))`. `FilterInputStream` is the abstract decorator base; `BufferedInputStream`, `CipherInputStream` are concrete decorators.
- `java.util.Collections.synchronizedList(list)` — returns `SynchronizedList`, a thread-safe decorator.
- `java.util.Collections.unmodifiableList(list)` — returns `UnmodifiableList`, which throws on mutation.
- `Collections.checkedList(list, Class)` — enforces element type at runtime.
- `jakarta.servlet.http.HttpServletRequestWrapper` / `HttpServletResponseWrapper` — designed for decoration in `Filter` implementations (e.g., a request that reads the body twice by buffering it on first read).
- Spring Security `FilterChainProxy` invokes a chain of `Filter` decorators; each filter conditionally calls `chain.doFilter(req, resp)` to delegate to the next.
- Spring's `TransactionAwareDataSourceProxy` decorates a `DataSource` to participate in Spring-managed transactions.
- Caffeine's `LoadingCache.get(key, loader)` effectively decorates a backing loader with caching, eviction, and stats.

### Anti-patterns

**1. Decorator breaking identity equality**
```java
// BROKEN — Set membership silently fails
Set<HttpClient> seen = new HashSet<>();
HttpClient base = new BaseHttpClient();
seen.add(base);
HttpClient wrapped = new MetricsClient(base, registry);
seen.contains(wrapped);   // false — decorator has default Object.equals
// Callers expecting the decorator to "behave like" the wrapped client get surprising results.

// FIX — delegate equals/hashCode in the abstract decorator
public abstract class HttpClientDecorator implements HttpClient {
    protected final HttpClient delegate;
    @Override public boolean equals(Object o) { return delegate.equals(o); }
    @Override public int hashCode()           { return delegate.hashCode(); }
}
```

**2. Wrong decorator order, silently incorrect behavior**
```java
// BROKEN — retry wraps metrics; metrics records each retry as a separate call,
// blowing up call counts and skewing dashboards. Worse: auth token cached
// in AuthClient is reused across retries even after it expires mid-burst.
HttpClient c = new BaseHttpClient();
c = new MetricsClient(c, registry);                // innermost
c = new RetryClient(c, 3, Duration.ofMillis(100));
c = new AuthClient(c, tokenSupplier);              // outermost — token added once, never refreshed on retry

// FIX — document and enforce order via builder (see ResilientHttpClientBuilder above).
// Auth innermost (per attempt), retry next, circuit breaker outside retry, metrics outermost.
```

**3. Decorator depth turning stack traces into a nightmare**
```java
// BROKEN — 8 single-purpose decorators stacked
HttpClient c = new BaseHttpClient();
c = new RequestIdInjector(c);
c = new UserAgentDecorator(c);
c = new AcceptEncodingDecorator(c);
c = new ContentTypeDecorator(c);
c = new TimingHeaderDecorator(c);
c = new TracingDecorator(c);
c = new AuthClient(c, tokenSupplier);
c = new RetryClient(c, 3, Duration.ofMillis(100));
// Stack traces span 40 frames; toString() is unreadable; debugging takes 3x longer.

// FIX — group cohesive concerns into a single decorator
public final class StandardHeadersDecorator extends HttpClientDecorator {   // merges 5 of the above
    public HttpResponse call(HttpRequest req) {
        HttpRequest enriched = req.toBuilder()
            .header("X-Request-Id", UUID.randomUUID().toString())
            .header("User-Agent", "payments-svc/1.0")
            .header("Accept-Encoding", "gzip")
            .header("Content-Type", "application/json")
            .header("X-Request-Start", String.valueOf(System.currentTimeMillis()))
            .build();
        return delegate.call(enriched);
    }
}
// Plus: implement toString() walking the chain — "Metrics -> CB -> Retry -> Auth -> Base" — for fast triage.
```

### Performance and Correctness Numbers

- Per-call decorator overhead (5-layer stack): ~3µs total — negligible vs. typical 50-200ms HTTP round trips.
- Retry decorator: dropped p50 error rate from 2.0% to 0.10% on a flaky upstream; cost 1.2x extra outbound bandwidth during incident windows.
- Circuit breaker (sliding window of 100 calls, open at 50% failure rate, half-open after 5s): cut p99 latency during a downstream outage from 30s (timeout) to 5ms (fast-fail) — a 6000x improvement that prevented thread-pool exhaustion in the upstream service.
- Metrics decorator: emits 4 timer samples and 1 counter per call; ~800ns total overhead with Micrometer + Prometheus registry.

### Migration Story

The original implementation embedded retry and metrics directly inside `BaseHttpClient` — about 200 LoC of mixed concerns. When the team needed to add the circuit breaker, the inline approach would have pushed `BaseHttpClient` past 400 LoC and made unit testing the breaker logic require mocking the network. The refactor extracted each concern into a decorator (1 day per concern, including tests), introduced the abstract `HttpClientDecorator` with identity delegation, and replaced the constructor-spaghetti at call sites with the `ResilientHttpClientBuilder`. A surprising win: a dev-mode flag now disables retries with `.withRetries(0)`, making it possible to reproduce upstream failures locally without exponential-backoff hiding them.

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

**Q: Give a concrete numeric example of the subclass explosion Decorator avoids.**
A: Suppose a `Coffee` can independently have Milk, Sugar, and Whip added. With inheritance, you'd need a subclass for every combination — `Coffee`, `CoffeeWithMilk`, `CoffeeWithSugar`, `CoffeeWithWhip`, `CoffeeWithMilkAndSugar`, `CoffeeWithMilkAndWhip`, ... — for 3 optional add-ons that's 2^3 = 8 classes, and a 4th add-on doubles it to 16. With Decorator, you write 1 base `Coffee` class plus 3 decorator classes (`MilkDecorator`, `SugarDecorator`, `WhipDecorator`) — 4 classes total — and any combination is achieved by wrapping at runtime (`new WhipDecorator(new MilkDecorator(new Coffee()))`), with a 4th add-on only adding 1 more class (5 total). The exponential-vs-linear framing (2^n vs n+1) is the sharpest way to make this point in an interview, mirroring the multiplicative argument used for Bridge but driven by combinations rather than two independent axes.

**Q: Does wrapping an object in a Decorator break `equals()` and `hashCode()` semantics?**
A: Yes, by default — if `Decorator` doesn't override `equals()`/`hashCode()`, it inherits `Object`'s identity-based implementation, so a decorated object will never be `.equals()` to the underlying object it wraps, and two different decorator instances wrapping equal underlying objects won't be equal to each other either. This becomes a real bug when decorated objects are placed in a `HashSet`/`HashMap` or compared after passing through different decoration paths — e.g., caching a `Service` instance keyed by equality, then later receiving a freshly-wrapped `LoggingServiceDecorator` around an equal underlying service, gets treated as a different key. The fix, if identity-through-wrapping matters, is to override `equals()`/`hashCode()` on the decorator to delegate to the wrapped component's `equals()`/`hashCode()` (and ensure all decorators in the chain do the same) — but this is easy to forget, so the practical guidance is to avoid relying on equality across decorated and undecorated forms of the same object unless you've explicitly designed for it.

**Q: Name Decorator examples in `java.util.Collections` beyond the I/O package.**
A: `Collections.unmodifiableList(list)`, `unmodifiableMap`, `unmodifiableSet`, etc. return a decorator that wraps the given collection and throws `UnsupportedOperationException` on any mutating call while delegating all read operations to the wrapped collection — adding a "read-only" responsibility without changing the underlying type. `Collections.synchronizedList(list)` similarly wraps a `List` and adds a synchronization responsibility by wrapping every method with a `synchronized` block on an internal lock, delegating the actual work to the wrapped list. Both are textbook Decorators: same `List`/`Collection` interface as what they wrap, composable (you can do `synchronizedList(unmodifiableList(list))`), and each adds exactly one orthogonal responsibility. These are useful answers when an interviewer asks for Decorator examples "beyond `java.io`," since they show the pattern isn't limited to streams.

**Q: How do you unit-test a chain of decorators?**
A: Test each decorator in isolation using a mock or stub of the wrapped `Component` interface, verifying that the decorator both delegates correctly to the mock and adds its specific behavior (e.g., a `LoggingDecorator` test verifies the underlying method is called exactly once AND that a log line was produced). Separately, write a small number of integration tests on realistic chains (e.g., `new EncryptionDecorator(new CompressionDecorator(new RawChannel()))`) to verify the composed behavior end-to-end, since per-decorator unit tests can't catch ordering bugs (like the compression-after-encryption issue) that only manifest when decorators interact. A common pitfall is testing only the fully-assembled chain, which makes it hard to pinpoint which decorator introduced a regression — the practical guidance is unit tests per decorator for correctness-in-isolation, plus a handful of chain-level tests for ordering and interaction correctness.

**Q: What happens if you add a new method to the `Component` interface after several Decorators already exist?**
A: Every concrete `Decorator` subclass that doesn't already extend a base `Decorator` implementing all `Component` methods by delegation will fail to compile (for abstract methods) or silently provide the default `Object`/inherited behavior for the new method (if using a default method), and any decorator that forgets to override the new method will not delegate to it — calls will either fail or hit the wrong implementation. This is why the standard Decorator structure includes an abstract `BaseDecorator` (or `ComponentDecorator`) that implements `Component` by delegating every method to a wrapped `Component` field; adding a new interface method only requires updating that one base class, and concrete decorators only need to override methods whose behavior they actually change. The broader lesson is the same fragile-interface problem seen with Bridge's `Implementor` — design the `Component` interface to be as stable as possible, since every interface change ripples through every decorator and every class implementing the original component.

**Q: How does Decorator relate to the Open/Closed Principle?**
A: Decorator is one of the canonical implementations of OCP — you extend an object's behavior (open for extension) by wrapping it in a new decorator class, without modifying the original component's source code (closed for modification). Contrast this with adding a boolean flag and an `if` branch inside the original class to support a new behavior variant, which requires modifying and re-testing the existing class for every new variant. The OCP framing is a strong way to justify Decorator when an interviewer asks "why not just add a parameter/flag to the existing class" — the answer is that flags accumulate, create combinatorial branching inside one class, and require re-touching tested code, whereas decorators are additive, independently testable, and never require modifying the base.

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
