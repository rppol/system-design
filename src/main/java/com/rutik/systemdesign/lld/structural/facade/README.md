# Facade Pattern

## 1. Pattern Name & Category

**Pattern:** Facade
**Category:** Structural (GoF)
**GoF Classification:** Structural Design Pattern — Chapter 4 of "Design Patterns: Elements of Reusable Object-Oriented Software" by Gamma, Helm, Johnson, Vlissides.

---

## 2. Intent

Provide a unified, simplified interface to a complex subsystem of classes, making the subsystem easier to use without exposing its internal complexity.

---

## Intuition

> **One-line analogy**: A Facade is like a hotel concierge — instead of navigating a complex city yourself (booking tickets, finding restaurants, arranging transport), you make one call to the concierge who coordinates everything on your behalf.

**Mental model**: You have a complex subsystem with many interdependent classes and initialization sequences. Instead of making clients learn all that complexity, you create a Facade class with a few simple, high-level methods that coordinate the subsystem internally. The Facade doesn't add functionality — it simplifies the interface. Clients use the Facade; the subsystem remains available for advanced users who need direct access.

**Why it matters**: Facades reduce cognitive load for clients and enforce layering — the Facade is the "public API" of a module; internal classes are implementation details. Spring Boot starters are Facades (one dependency, preconfigured subsystem). AWS SDK's high-level clients (S3 Client) facade complex HTTP/auth mechanics.

**Key insight**: A Facade doesn't prevent access to the subsystem (it doesn't wrap it in the Decorator sense) — it just provides a simpler path. The subsystem classes remain available for direct use when needed, making Facade a "convenience" pattern that improves usability without restricting capability.

---

## 3. Problem Statement

### The Problem
Large systems are composed of many classes that interact in complex ways. When a client needs to use such a system, it must understand the intricate relationships, initialization order, and protocols between all those classes. This creates tight coupling between the client and the subsystem internals — any change in the subsystem forces changes in the client.

### Scenario
You are building a home theater application. To watch a movie, a user must:
1. Turn on the projector and set it to DVD mode
2. Lower the projection screen
3. Turn on the amplifier, set it to DVD input, set volume
4. Turn on the DVD player and start the disc
5. Dim the lights
6. Turn on the popcorn popper

Without a facade, every client that wants to "watch a movie" must know and invoke all these steps in the correct order. If the home theater hardware changes (e.g., a streaming device replaces the DVD player), every client must be updated. The subsystem is powerful but cumbersome to use directly.

---

## 4. Solution

Introduce a **Facade** class that knows the subsystem and provides high-level methods like `watchMovie(movie)` and `endMovie()`. The facade delegates to the appropriate subsystem classes in the right sequence. Clients only talk to the facade — they are decoupled from the subsystem internals. The subsystem classes still exist and can be used directly by sophisticated clients if needed.

---

## 5. UML Structure

```
Client
  |
  | uses
  v
+-------------------+
|     Facade        |
|-------------------|
| - projector       |---> ProjectorSubsystem
| - amplifier       |---> AmplifierSubsystem
| - dvdPlayer       |---> DvdPlayerSubsystem
| - lights          |---> LightsSubsystem
|-------------------|
| + watchMovie()    |
| + endMovie()      |
+-------------------+

Subsystems (each has its own complex interface):
+---------------------+   +---------------------+   +---------------------+
|  ProjectorSubsystem |   | AmplifierSubsystem  |   |  DvdPlayerSubsystem |
|---------------------|   |---------------------|   |---------------------|
| + on()              |   | + on()              |   | + on()              |
| + setInput()        |   | + setDvd()          |   | + play()            |
| + wideScreenMode()  |   | + setVolume()       |   | + stop()            |
| + off()             |   | + off()             |   | + off()             |
+---------------------+   +---------------------+   +---------------------+
```

The client only sees and depends on `Facade`. All subsystem arrows are internal.

---

## 6. How It Works — Step-by-Step

1. **Client calls `facade.watchMovie("Inception")`.**
2. The Facade holds references to all subsystem objects (injected or created internally).
3. The Facade executes the necessary sequence of subsystem calls:
   - `lights.dim(10)`
   - `screen.down()`
   - `projector.on(); projector.setInput(dvdPlayer); projector.wideScreenMode()`
   - `amplifier.on(); amplifier.setDvd(dvdPlayer); amplifier.setVolume(5)`
   - `dvdPlayer.on(); dvdPlayer.play(movie)`
4. The client receives a ready theater with zero knowledge of the internals.
5. When `facade.endMovie()` is called, the facade reverses the sequence in the correct teardown order.

---

## 7. Key Components

| Role | Description |
|------|-------------|
| **Facade** | The main class clients interact with. Knows which subsystem classes handle each request and delegates work. Does NOT implement subsystem logic itself. |
| **Subsystem Classes** | The actual workers. They handle the real work assigned by the facade. They have no reference back to the facade. |
| **Client** | Uses only the facade. Is shielded from subsystem complexity. |
| **Optional: Additional Facades** | For very large subsystems, you can layer facades (a facade of facades). |

---

## 8. When to Use

- **Simplifying a complex API**: When you want to provide a simple interface to a complex body of code (e.g., a library, a framework module, a legacy system).
- **Layered architecture**: Use facades to define entry points to each layer (e.g., service layer facade hides DAOs and domain objects from the presentation layer).
- **Reducing dependencies**: When you want to decouple client code from a subsystem so the subsystem can evolve independently.
- **Wrapping legacy systems**: Wrap a poorly-designed or legacy API behind a clean facade without rewriting it.
- **Third-party library isolation**: Wrap a third-party library so swapping it out later only requires changing the facade, not all callers.
- **Testing convenience**: A facade can be mocked in tests, hiding an entire complex subsystem behind a single mock.

---

## 9. When NOT to Use

- **When clients need fine-grained control**: If different clients need to customize the subsystem behavior in incompatible ways, a single facade becomes bloated with parameters or overloaded methods.
- **When you need to expose the full API**: A facade is a simplification — it hides features. Don't use it if clients routinely need capabilities the facade omits.
- **Do not use as a God Object**: A facade should delegate, not implement logic. If the facade grows to have its own complex business logic, it has become a God Object anti-pattern.
- **When there is no complexity to hide**: Adding a facade to a simple, already-clean API just adds an unnecessary indirection layer.

---

## 10. Pros

- **Simplicity**: Reduces the complexity clients must deal with — one class with a few high-level methods replaces dozens of low-level calls.
- **Decoupling**: Clients are decoupled from subsystem internals. The subsystem can be refactored without breaking clients.
- **Promotes layered architecture**: Enforces clear boundaries between system layers.
- **Easier testing**: Clients that depend on a facade interface are easy to test by mocking the facade.
- **Rapid onboarding**: New developers can use the system quickly without understanding every detail of the subsystem.
- **Single Responsibility**: The facade groups related use cases, giving them a dedicated place to live.
- **Encapsulation of initialization order**: Complex setup sequences are encoded once in the facade, not scattered across every caller.

---

## 11. Cons

- **Possible God Object**: If not disciplined, the facade accumulates logic and becomes a bloated, untestable monolith.
- **Incomplete API exposure**: Legitimate use cases may require bypassing the facade, leading to inconsistent usage patterns.
- **False sense of simplicity**: The subsystem complexity does not disappear; it is merely hidden. Debugging issues still requires understanding the subsystem.
- **Tight facade-to-subsystem coupling**: The facade itself is tightly coupled to the subsystem. Subsystem interface changes require facade updates.
- **Can become a bottleneck**: In large teams, every new feature requires a facade update — it becomes a merge conflict hotspot.
- **Hides useful errors**: Facades sometimes swallow or wrap exceptions too aggressively, making debugging harder.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Simplified client code | Direct fine-grained access to subsystem |
| Reduced coupling between client and subsystem | Full visibility into what the subsystem is doing |
| Single place to enforce subsystem usage contracts | Flexibility for power users needing low-level control |
| Easier to swap or upgrade the subsystem | Transparency — problems are harder to trace through the facade |
| Faster time-to-productivity for new developers | One additional indirection layer in the call stack |

---

## 13. Common Pitfalls

1. **Turning Facade into a God Object**: Every method of every subsystem class gets a delegation method in the facade. The facade explodes in size and defeats its own purpose.
2. **Putting business logic in the facade**: The facade should orchestrate subsystem calls, not contain domain logic. Domain logic belongs in the domain layer.
3. **Not using an interface for the Facade**: If the facade is a concrete class with no interface, it is difficult to mock in tests and impossible to swap implementations.
4. **Forgetting that subsystems can still be used directly**: Some developers enforce that all access must go through the facade via access modifiers or package-private visibility. This is fine intentionally, but accidentally preventing direct access removes a useful escape hatch.
5. **Creating a facade for every class**: Facade is for simplifying a complex *subsystem*, not individual classes. Wrapping single classes is just indirection for no benefit.
6. **Ignoring thread safety**: The facade often orchestrates stateful subsystem calls across multiple objects. If subsystems are not thread-safe, neither is the facade.

---

## 14. Real-World Usage

### Production Anchor: AWS SDK v2 S3Client

A data platform ingests ~50k S3 operations/day across 20 microservices: object puts (event logs), multipart uploads (backups), pre-signed URL generation (user uploads), and bucket listings. The AWS SDK v2 `S3Client` is a Facade over a complex subsystem: HTTP transport (Apache or Netty), Sigv4 request signing, CRC32C checksum computation, automatic retry with exponential backoff, multipart upload coordination, XML/JSON error parsing, and endpoint discovery. A caller writes `s3.putObject(req, body)` — one line that internally coordinates ~200 LoC of subsystem work. Quantified impact: feature teams previously wrote 400+ LoC of S3 integration code per use case; with the Facade, integrations average 40 LoC. p99 latency for a small put is 80ms (network-bound); facade overhead is <0.5ms.

```
                     +---------------------------+
                     |     S3Client (Facade)     |
                     |  putObject / getObject /  |
                     |  createMultipartUpload    |
                     +-------------+-------------+
                                   |
       +---------------+-----------+----------+----------------+
       |               |           |          |                |
       v               v           v          v                v
  +---------+   +-----------+  +--------+ +---------+   +-----------+
  | HTTP    |   | Sigv4     |  | Retry  | | CRC32C  |   | XML/JSON  |
  | (Netty) |   | Signer    |  | Policy | | Checksum|   | ErrorParse|
  +---------+   +-----------+  +--------+ +---------+   +-----------+
       |               |           |          |                |
       +---------+-----+-----+-----+----------+----------------+
                 |           |
                 v           v
            Endpoint    Credentials
            Resolver    Provider Chain
```

```java
// Caller-side: one line. The Facade hides the entire subsystem.
S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build();
PutObjectRequest req = PutObjectRequest.builder()
    .bucket("ingest-events").key("2026/05/23/events.json.gz")
    .contentType("application/gzip").build();
PutObjectResponse resp = s3.putObject(req, RequestBody.fromBytes(payload));
String etag = resp.eTag();
```

```java
// Our own internal Facade — wraps S3Client to expose a domain-shaped API
public final class EventArchiveFacade {
    private final S3Client s3;
    private final String bucket;
    private final Clock clock;
    // subsystems intentionally PRIVATE — anti-pattern fix #1
    private final Compressor compressor;
    private final ChecksumVerifier verifier;
    private final MeterRegistry meters;

    public EventArchiveFacade(S3Client s3, String bucket, Clock clock,
                              Compressor c, ChecksumVerifier v, MeterRegistry m) {
        this.s3 = s3; this.bucket = bucket; this.clock = clock;
        this.compressor = c; this.verifier = v; this.meters = m;
    }

    /** High-level operation: archive a batch of events for the current hour. */
    public ArchiveReceipt archive(List<Event> events) {
        Timer.Sample sample = Timer.start(meters);
        byte[] compressed = compressor.gzip(serialize(events));
        String key = keyFor(clock.instant());
        try {
            PutObjectResponse resp = s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType("application/gzip")
                    .checksumAlgorithm(ChecksumAlgorithm.CRC32_C)
                    .build(),
                RequestBody.fromBytes(compressed));
            verifier.verify(resp.checksumCRC32C(), compressed);
            return new ArchiveReceipt(key, resp.eTag(), events.size());
        } catch (S3Exception e) {
            throw new ArchiveFailure("S3 put failed for key=" + key, e);
        } finally {
            sample.stop(meters.timer("archive.put", "bucket", bucket));
        }
    }

    /** High-level operation: produce a pre-signed URL for a one-time upload. */
    public URI presignUpload(String userId, Duration ttl) {
        // ...uses S3Presigner internally; caller never touches it.
    }

    // Subsystems stay hidden — no getters that leak S3Client, Compressor, etc.
    private String keyFor(Instant t) {
        return DateTimeFormatter.ofPattern("yyyy/MM/dd/HH").withZone(ZoneOffset.UTC).format(t)
             + "/events-" + UUID.randomUUID() + ".json.gz";
    }
}
```

```java
// Splitting a fat Facade into focused ones (anti-pattern fix #2)
public final class OrderFulfillmentFacade {
    public OrderId placeOrder(NewOrder o) { /* payment + inventory + shipping */ }
    public void cancelOrder(OrderId id)   { /* refund + restock + notify */ }
    public ShipmentStatus ship(OrderId id) { /* carrier API + label printing */ }
}
public final class OrderQueryFacade {                          // separate facade, separate concerns
    public Optional<Order> findById(OrderId id) { ... }
    public Page<OrderSummary> search(OrderQuery q, Pageable p) { ... }
    public List<OrderEvent> history(OrderId id) { ... }
}
// Each facade has < 10 methods, one reason to change, and one team's worth of test surface.
```

### Famous Codebase Usages

- **AWS SDK v2** — `S3Client`, `DynamoDbClient`, `LambdaClient`, `SqsClient`. Each is a Facade over signing, retry, transport, marshalling, and endpoint resolution.
- **SLF4J `LoggerFactory.getLogger(Class)`** — Facade over backend binding (Logback/Log4j2/JUL) and logger lookup.
- **Spring `JdbcTemplate.queryForObject(sql, RowMapper, args)`** — Facade over connection acquisition, prepared statement, parameter binding, result set iteration, exception translation, and connection release.
- **Spring `RestTemplate` / `WebClient`** — Facades over HTTP transport, message conversion, error handling.
- **Hibernate `Session`** — Facade over connection pool, first-level cache, dirty tracking, flush, transaction coordination.
- **`java.net.URL.openStream()`** — Facade over DNS, socket, TLS, HTTP protocol handling.
- **Apache Commons `FileUtils.copyFile(src, dst)`** — Facade over NIO file channels, buffer management, atomic-move semantics.

### Anti-patterns

**1. Facade exposing subsystem internals**
```java
// BROKEN — getter leaks the underlying S3Client; callers now depend on AWS SDK directly
public class EventArchiveFacade {
    private final S3Client s3;
    public S3Client getS3Client() { return s3; }              // <-- leak
}
// Caller now does: archive.getS3Client().listBuckets(...) — defeating the abstraction.
// Migrating off AWS later requires touching every caller that grabbed the raw client.

// FIX — Facade exposes ONLY high-level domain operations
public final class EventArchiveFacade {
    private final S3Client s3;                                 // package-private at most
    public ArchiveReceipt archive(List<Event> events) { ... }
    public URI presignUpload(String userId, Duration ttl) { ... }
    // no S3Client getter; no Bucket getter; no Region getter
}
```

**2. God-Object Facade with 80 methods**
```java
// BROKEN — one OrderFacade does everything: place, cancel, search, ship, refund,
// notify, audit, recommend, score for fraud, generate invoices...
public class OrderFacade {
    public OrderId placeOrder(...) {...}
    public void cancelOrder(...) {...}
    public List<Order> search(...) {...}
    public ShipmentStatus ship(...) {...}
    public Refund refund(...) {...}
    public Invoice generateInvoice(...) {...}
    public FraudScore scoreFraud(...) {...}
    // ...75 more methods
}
// File is 4,000 LoC, three teams contend on it, every PR has merge conflicts.

// FIX — split by bounded context (see OrderFulfillmentFacade + OrderQueryFacade above).
// Each Facade has cohesive responsibilities and a single owning team.
```

**3. Facade bypassed by developers calling subsystems directly**
```java
// BROKEN — subsystem classes are public; teams skip the facade for "performance"
@Service public class PaymentService { public void charge(...) {...} }        // public
@Service public class InventoryService { public void reserve(...) {...} }     // public
@Service public class OrderFacade { public void placeOrder(...) {...} }       // public
// Team A uses the facade. Team B injects PaymentService and InventoryService directly,
// gets the ordering wrong, and bills customers for out-of-stock items.

// FIX — make subsystem classes package-private; only the Facade is public
package com.example.order;
class PaymentService { void charge(...) {...} }              // package-private; not exported
class InventoryService { void reserve(...) {...} }           // package-private
public class OrderFacade { public void placeOrder(...) {...} }   // the ONLY public entry point
// Or with JPMS: don't `exports` the internal package; only `exports` the facade package.
```

### Performance and Correctness Numbers

- `S3Client.putObject` Facade overhead: <0.5ms (signing + checksum computation), vs. 80ms p99 network round-trip — 0.6% of total. Invisible.
- Integration LoC for a new S3 use case: 40 LoC with facade, vs. 400 LoC raw (transport setup, signer, retry policy, error parsing, multipart coordination). 10x productivity multiplier confirmed across 12 feature teams.
- Splitting the 80-method `OrderFacade` into 4 focused facades: PR throughput on the order module rose from 8/week to 22/week (3x), measured over the quarter after the split.
- Making subsystem services package-private caught 3 misuses at compile time during the migration that would otherwise have been data-corruption bugs.

### Migration Story

A 5-year-old order service had grown an `OrderManager` god-object (3,800 LoC, 78 public methods). The team introduced bounded-context facades (`OrderFulfillmentFacade`, `OrderQueryFacade`, `OrderReportingFacade`) in front of it without modifying `OrderManager`. Callers were migrated one PR at a time over 6 weeks. Once the last direct call to `OrderManager` was gone, the legacy class was made package-private; the next quarter, it was sharded into focused services aligned with the facades. Net: no big-bang refactor, two outages avoided (one in week 2: a fraud-scoring path was missing a transaction; the focused facade made the gap obvious), and onboarding time for new engineers fell from 3 weeks to 1.

---

## 15. Comparison with Similar Patterns

| Pattern | Purpose | Key Difference |
|---------|---------|----------------|
| **Adapter** | Converts an interface into another interface a client expects | Adapter makes *incompatible* interfaces work together; Facade *simplifies* an existing interface |
| **Mediator** | Centralizes complex communication between many objects | Mediator is about *object communication*; Facade is about simplifying *client access to a subsystem* |
| **Decorator** | Adds behavior to objects dynamically | Decorator wraps *one* object to add behavior; Facade wraps *many* objects to simplify access |
| **Abstract Factory** | Creates families of related objects | Abstract Factory produces objects; Facade coordinates existing objects |
| **Proxy** | Controls access to a single object | Proxy wraps *one* object; Facade wraps an entire *subsystem* of objects |

---

## 16. Interview Tips

**Q: What is the Facade pattern and when would you use it?**
A: Explain the intent (simplified interface to a complex subsystem) and give a concrete example — JdbcTemplate or SLF4J are ideal because interviewers know them.

**Q: What is the difference between Facade and Adapter?**
A: Adapter is about *compatibility* — making incompatible interfaces work together. Facade is about *simplicity* — hiding complexity. The Adapter changes the interface signature; the Facade provides a higher-level interface on top of an existing one.

**Q: Can a Facade hurt you? What are the risks?**
A: Yes. It can become a God Object if business logic leaks in. It can hide important subsystem APIs. It can become a merge conflict hotspot in large teams. Show you understand the tradeoffs.

**Q: How do you make a Facade testable?**
A: Extract an interface for the Facade. In tests, provide a mock implementation. Alternatively, inject subsystem dependencies via constructor so the subsystem can be mocked individually.

**Q: Is SLF4J a Facade?**
A: Yes — this is a classic follow-up. SLF4J's name literally contains "Facade" and it demonstrates the pattern perfectly: a clean API that delegates to any logging backend.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Facade Appears in Distributed Systems**

- **API gateway** — The canonical HLD Facade: clients call one endpoint; the gateway internally routes to auth service, user service, order service, and product service, aggregates results, and returns a unified response. The backend complexity is completely hidden.
- **BFF (Backend for Frontend)** — A mobile BFF presents a single, mobile-optimized API that internally calls multiple microservices and aggregates/transforms data for the mobile client's specific needs — a Facade tailored per client type.
- **SDK clients** — AWS SDK, Stripe SDK, and Twilio SDK are Facades: they hide authentication, request signing, retry logic, response parsing, and error mapping behind simple `client.sendMessage(params)` calls.
- **Read-model service** — In CQRS, the read-model service is a Facade over the underlying materialized views and search indexes, presenting a unified query API that hides the denormalized storage structure.

---

## 17. Best Practices

1. **Always define a Facade interface**: `interface HomeTheaterFacade` with a concrete `HomeTheaterFacadeImpl`. This enables mocking and alternative implementations.
2. **Inject subsystem dependencies**: Pass subsystem objects into the Facade constructor rather than creating them inside. This makes the Facade testable.
3. **Keep the Facade focused**: Each Facade should cover one coherent subsystem. Multiple smaller Facades are better than one giant Facade.
4. **Do not replicate the entire subsystem API**: Expose only what clients actually need. If you find yourself adding a delegation method for every subsystem method, reconsider the design.
5. **Document what the Facade hides**: Comments in the Facade methods should describe the sequence of operations being performed, so developers who need to debug can understand the orchestration.
6. **Let advanced users bypass the Facade**: Do not use access modifiers to force all access through the Facade unless there is a strict security reason. Provide the subsystem classes as well for power users.
7. **Version and evolve the Facade**: When the subsystem changes, update the Facade in one place rather than hunting down all clients.
