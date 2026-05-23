# Testing — JUnit 5 & Mockito

## 1. Concept Overview

Testing in Java is a discipline built on three interacting tools: **JUnit 5** (the test framework — lifecycle, assertions, parameterization), **Mockito** (the mocking framework — replacing collaborators with controllable doubles), and design principles that make code testable in the first place.

JUnit 5 (released 2017, composed of JUnit Platform + JUnit Jupiter + JUnit Vintage) replaced JUnit 4 with a more extensible architecture. Mockito 4/5 provides deep integration via `@ExtendWith(MockitoExtension.class)`. Together they enable the test pyramid strategy: a large base of fast, isolated unit tests supplemented by fewer integration and end-to-end tests.

---

## 2. Intuition

> **One-line analogy**: Unit tests are automated regression guards — every test is a machine that says "this method must behave this way forever, and will alert you the moment it doesn't."

**Mental model**: Think of each test as a specification: "Given THIS setup, when THIS method is called, it SHOULD produce THIS result." The Arrange-Act-Assert (AAA) structure makes that specification explicit. Mocks replace real collaborators (database, HTTP client, clock) with controllable fakes so tests verify the unit's behavior in isolation, independent of external systems.

**Why it matters**: Untested code is a liability that compounds over time. A 10-minute test run that catches regressions before deployment is worth weeks of production debugging. Mockito lets you test code that depends on databases and services without running any of them.

**Key insight**: The purpose of `@Mock` vs `@Spy` is often misunderstood. `@Mock` creates a complete stub — all methods do nothing unless you configure them. `@Spy` wraps a real object — methods execute their real implementation unless stubbed. Use mocks when you want to isolate; use spies when you want to verify behavior on a real object.

---

## 3. Core Principles

- **AAA pattern**: Arrange (set up), Act (invoke), Assert (verify) — one conceptual block per test.
- **Test isolation**: Each test runs independently; one test's state must not affect another.
- **Test double taxonomy**: Dummy, Fake, Stub, Spy, Mock — each has a different purpose.
- **Test pyramid**: Many unit tests → fewer integration tests → very few E2E tests. Ratio roughly 70/20/10.
- **Fail fast**: Tests should fail immediately with a clear message when a contract is violated.
- **Avoid brittle tests**: Test behavior, not implementation. If refactoring without changing behavior breaks a test, the test was testing the wrong thing.

---

## 4. Types / Architectures / Strategies

### 4.1 JUnit 5 Annotations

| Annotation | Purpose |
|-----------|---------|
| `@Test` | Marks a method as a test |
| `@BeforeEach` | Runs before each test method |
| `@AfterEach` | Runs after each test method |
| `@BeforeAll` | Runs once before all tests in class (must be static unless `@TestInstance(PER_CLASS)`) |
| `@AfterAll` | Runs once after all tests in class |
| `@DisplayName` | Human-readable name for test in reports |
| `@Nested` | Groups related tests in inner classes |
| `@ParameterizedTest` | Runs test multiple times with different arguments |
| `@RepeatedTest(n)` | Runs test n times |
| `@Tag("tagname")` | Categorize tests for filtering |
| `@Disabled` | Skip a test with an optional reason |
| `@ExtendWith` | Register extensions (e.g., MockitoExtension) |
| `@TestInstance(PER_CLASS)` | One instance per class instead of per-method |

### 4.2 Parameterized Test Sources

| Annotation | Data Source |
|-----------|-------------|
| `@ValueSource(ints = {1, 2, 3})` | Array of a single type |
| `@CsvSource({"1,true", "2,false"})` | Inline CSV rows |
| `@CsvFileSource(resources = "/test.csv")` | CSV from classpath file |
| `@MethodSource("provideArguments")` | Static factory method returning `Stream<Arguments>` |
| `@EnumSource(Status.class)` | All or subset of enum values |
| `@NullSource` / `@EmptySource` | Null and/or empty argument |

### 4.3 Mockito Test Double Taxonomy (Meszaros)

| Double Type | What It Does | When to Use |
|-------------|-------------|-------------|
| **Dummy** | Passed but never used — satisfies compiler | Method takes a parameter you don't care about |
| **Fake** | Working but simplified implementation | In-memory `Map`-backed repository |
| **Stub** | Returns canned answers to calls | `when(repo.findById(1)).thenReturn(user)` |
| **Spy** | Wraps a real object, records calls | Verify a real object's interactions |
| **Mock** | Pre-programmed with expectations | Verify behavior AND stub responses |

---

## 5. Architecture Diagrams

### Test Lifecycle (per test method)
```
@BeforeAll (once, static) ─────────────────────────────────┐
                                                             |
  @BeforeEach ──> @Test (test method 1) ──> @AfterEach     |
  @BeforeEach ──> @Test (test method 2) ──> @AfterEach     |
  @BeforeEach ──> @Test (test method 3) ──> @AfterEach     |
                                                             |
@AfterAll (once, static) ──────────────────────────────────┘

Default: new instance per test method (maximizes isolation)
@TestInstance(PER_CLASS): reuse one instance (needed for @BeforeAll on non-static)
```

### Mockito @InjectMocks Injection Strategy
```
@InjectMocks tries (in this order):
  1. Constructor injection:
     largest constructor with all mocks available
  2. Setter injection:
     setters matching mock type/name
  3. Field injection:
     fields matching mock type/name (least preferred)

If none works: creates instance with no-arg constructor, fields remain null
— always check your mocks are actually injected (NPE at runtime = silent injection failure)
```

### AAA Structure
```
@Test
void shouldReturnUserById() {
    // ARRANGE — set up state and stubs
    User expected = new User(1L, "Alice");
    when(userRepo.findById(1L)).thenReturn(Optional.of(expected));

    // ACT — call the unit under test
    User result = userService.findById(1L);

    // ASSERT — verify outcome
    assertThat(result.name()).isEqualTo("Alice");
    verify(userRepo).findById(1L);  // verify interaction
}
```

---

## 6. How It Works — Detailed Mechanics

### JUnit 5 Core Assertions

```java
import static org.junit.jupiter.api.Assertions.*;

// Basic assertions
assertEquals(42, result);
assertEquals(42, result, "message on failure");
assertNotNull(object);
assertNull(object);
assertTrue(condition);
assertFalse(condition);
assertArrayEquals(expected, actual);

// assertAll: groups assertions, all run even if some fail
assertAll("user fields",
    () -> assertEquals("Alice", user.name()),
    () -> assertEquals(30, user.age()),
    () -> assertNotNull(user.email())
);
// Without assertAll, the first failure stops checking the rest.
// With assertAll, you see ALL failures in one run.

// assertThrows: verifies exception thrown
IllegalArgumentException ex = assertThrows(
    IllegalArgumentException.class,
    () -> service.findById(-1L)
);
assertEquals("ID must be positive", ex.getMessage());

// assertTimeout: verifies completion within time limit
assertTimeout(Duration.ofMillis(100), () -> {
    computeExpensiveResult();  // must finish within 100ms
});
// assertTimeoutPreemptively: aborts the test if it exceeds the limit

// Assumptions: skip test if condition not met (not a failure)
assumeTrue(System.getenv("CI") != null, "Only runs in CI");
```

### Parameterized Tests

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "\t", "\n"})
@DisplayName("Should return true for blank strings")
void isBlank_shouldReturnTrueForBlankStrings(String input) {
    assertTrue(StringUtils.isBlank(input));
}

@ParameterizedTest
@CsvSource({
    "PENDING, false",
    "ACTIVE,  true",
    "EXPIRED, false"
})
void isActive(String status, boolean expected) {
    assertEquals(expected, Status.valueOf(status).isActive());
}

@ParameterizedTest
@MethodSource("provideInvalidInputs")
void rejectsInvalidInput(String input, String expectedError) {
    Exception ex = assertThrows(IllegalArgumentException.class,
        () -> validator.validate(input));
    assertThat(ex.getMessage()).contains(expectedError);
}

// The method source factory — must be static, return Stream<Arguments>
private static Stream<Arguments> provideInvalidInputs() {
    return Stream.of(
        Arguments.of(null, "input is null"),
        Arguments.of("",   "input is empty"),
        Arguments.of("ab", "too short")
    );
}
```

### Mockito Core Patterns

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepo;    // fully stubbed; all methods return defaults

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;    // Mockito injects mocks into this

    @Captor
    private ArgumentCaptor<String> emailCaptor;

    @Test
    void sendWelcomeEmail_shouldEmailWithCorrectAddress() {
        // Arrange
        User user = new User(1L, "Alice", "alice@example.com");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        // Act
        userService.sendWelcomeEmail(1L);

        // Assert
        verify(emailService).send(emailCaptor.capture(), anyString());
        assertEquals("alice@example.com", emailCaptor.getValue());
    }

    @Test
    void findById_whenNotFound_shouldThrow() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
            () -> userService.findById(99L));
    }
}
```

### Mock vs Spy

```java
// @Mock: complete stub — all methods do nothing (return null/0/false/empty)
@Mock
List<String> mockList;

@Test
void mockExample() {
    mockList.add("hello");             // does nothing
    when(mockList.size()).thenReturn(5);
    assertEquals(5, mockList.size());  // returns stubbed value
    verify(mockList).add("hello");     // verify the call happened
}

// @Spy: wraps a real object — real methods execute unless stubbed
@Spy
List<String> spyList = new ArrayList<>();

@Test
void spyExample() {
    spyList.add("hello");             // REAL add — actually adds
    assertEquals(1, spyList.size());  // REAL size — actually 1

    doReturn(99).when(spyList).size(); // stub only size()
    assertEquals(99, spyList.size()); // stubbed
    assertEquals("hello", spyList.get(0)); // still real
}

// KEY RULE: for spies, use doReturn().when() NOT when().thenReturn()
// when(spy.method()) invokes the REAL method during setup — may cause NPE
// doReturn(value).when(spy).method() is safe
```

### ArgumentCaptor — Beyond verify()

```java
// verify() checks that a method was called but doesn't inspect arguments
verify(emailService).send(anyString(), anyString());  // was it called? yes

// ArgumentCaptor checks WHAT was passed
ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
verify(emailService).send(captor.capture());
EmailMessage sent = captor.getValue();
assertEquals("Welcome!", sent.subject());
assertEquals("alice@example.com", sent.to());
assertEquals(3, sent.attachments().size());
// ArgumentCaptor lets you assert on complex objects passed to a mock
// without modifying the subject under test
```

### Mockito Advanced — InOrder, doThrow, doAnswer

```java
// Verify call ORDER
InOrder inOrder = inOrder(repo, cache);
inOrder.verify(repo).save(any());
inOrder.verify(cache).invalidate(anyString());
// Fails if save is called after invalidate

// Stub to throw exception
doThrow(new DatabaseException("connection lost"))
    .when(repo).save(any(Order.class));

// Complex answer (return value depends on input)
when(repo.findById(anyLong())).thenAnswer(invocation -> {
    Long id = invocation.getArgument(0);
    return id > 0 ? Optional.of(new User(id, "user" + id)) : Optional.empty();
});

// Verify exact number of invocations
verify(emailService, times(2)).send(any(), any());
verify(cache, never()).invalidate(any());
verify(repo, atLeast(1)).save(any());
verify(repo, atMost(3)).findById(anyLong());
```

### Testing Concurrent Code

```java
@Test
void counterIsThreadSafe() throws InterruptedException {
    ThreadSafeCounter counter = new ThreadSafeCounter();
    int threads = 10;
    int incrementsPerThread = 1000;

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done  = new CountDownLatch(threads);

    for (int i = 0; i < threads; i++) {
        new Thread(() -> {
            try {
                start.await();  // all threads start simultaneously
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.increment();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }).start();
    }

    start.countDown();  // release all threads at once
    done.await(5, TimeUnit.SECONDS);  // wait for completion

    assertEquals(threads * incrementsPerThread, counter.get());
}
```

### Testing Time-Dependent Code

```java
// WRONG: depends on real clock — non-deterministic, slow
class SubscriptionService {
    boolean isExpired(Subscription sub) {
        return sub.expiryDate().isBefore(LocalDate.now());  // untestable!
    }
}

// RIGHT: inject Clock — fully testable
class SubscriptionService {
    private final Clock clock;
    SubscriptionService(Clock clock) { this.clock = clock; }

    boolean isExpired(Subscription sub) {
        return sub.expiryDate().isBefore(LocalDate.now(clock));
    }
}

// Test:
@Test
void expiredSubscription_returnsTrue() {
    Clock fixedClock = Clock.fixed(
        Instant.parse("2025-06-01T00:00:00Z"), ZoneOffset.UTC);
    SubscriptionService service = new SubscriptionService(fixedClock);
    Subscription sub = new Subscription(LocalDate.of(2025, 1, 1));
    assertTrue(service.isExpired(sub));
}
// No mocking needed — just inject a fixed clock.
```

---

## 7. Real-World Examples

- **Spring's `@DataJpaTest`**: Bootstraps an in-memory H2 database with JPA configured, uses `@MockBean` for service layer — integration test without a real database.
- **Mockito's `ArgumentCaptor` for asynchronous verification**: When a service submits work to an `ExecutorService`, capture the `Runnable` with a captor, run it synchronously in the test, then assert on its side effects.
- **Testcontainers**: Spins up real Docker containers (PostgreSQL, Kafka) per test — integration tests against the real technology rather than a fake. Complements `@Spy` and `@Fake` for true integration tests.

---

## 8. Tradeoffs

| Approach | Benefit | Risk |
|----------|---------|------|
| `@Mock` (full stub) | Fast, isolated, no real I/O | Can give false confidence — tests pass even if wiring is wrong |
| `@Spy` (partial stub) | Tests real behavior | Slower, real side effects in tests |
| Fake (in-memory impl) | More realistic than mock, faster than real DB | Maintenance overhead of keeping fake in sync |
| Integration test | Tests real wiring and real DB | Slow, requires external resources, flaky |
| `@ParameterizedTest` | Covers many cases concisely | Can obscure intent if too many parameters |

---

## 9. When to Use / When NOT to Use

**Use `@Mock`** when: you want to isolate the unit from its collaborators; you don't care what the collaborator does, only that the unit uses it correctly.

**Use `@Spy`** when: you need to verify behavior on a real object but stub only a few methods; avoid overusing — tests that rely on real implementations are harder to isolate.

**Do NOT mock** value objects, simple data holders, or truly immutable objects — they're safe to use directly. Do NOT mock the class you're testing.

**Use `@ParameterizedTest`** when: you have multiple similar inputs that should produce similar outcomes. Do not use it to combine unrelated cases.

**Use `assertAll()`** when: a test logically validates multiple aspects of a single result — you want to see all failures, not just the first.

---

## 10. Common Pitfalls

### War Story 1: `@InjectMocks` silently fails injection
A developer used `@Mock UserRepository repo` and `@InjectMocks UserService service`. The service had no setter and no matching constructor — `@InjectMocks` fell through to field injection, which requires the field name to match the mock name. The field was named `repository` but the mock was named `repo`. Injection silently failed; the field was null. The first test method threw NPE deep inside the service. **Fix**: Use constructor injection in production code — `@InjectMocks` always succeeds with constructor injection if all parameters have matching mocks. Name mocks to match fields exactly for field injection.

### War Story 2: Testing implementation, not behavior
A developer wrote a test that verified `verify(cache).put(any(), any())` to check that caching worked. After refactoring to use a different cache method (`putIfAbsent()`), the test broke even though caching still worked correctly. **Fix**: Test observable behavior (subsequent calls return cached value) not internal implementation (which method was called on the cache). Mock what crosses system boundaries, not internal collaborators.

### War Story 3: `when(spy.method())` causes NPE
A developer used `@Spy List<String> spy = new ArrayList<>()` and wrote `when(spy.size()).thenReturn(99)`. The `when(spy.size())` call invokes the REAL `size()` method on the spy first — which returned 0 (fine in this case). But for a spy wrapping a service that calls a database in its real method: `when(spy.realMethod()).thenReturn(...)` would invoke the real method, causing a database call or NPE in the test. **Fix**: Always use `doReturn(99).when(spy).size()` for spies — this does NOT invoke the real method.

### War Story 4: Concurrent test with data race between test threads
A team tested a concurrent queue by creating threads in `@Test`, having them `add` to the queue, and asserting size at the end. They forgot to `join()` the threads before asserting. The assertion ran before all threads finished. Intermittently failing test. **Fix**: Use `CountDownLatch` to synchronize test threads; always `latch.await()` before asserting on shared state.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| JUnit 5 (Jupiter) | Test framework — lifecycle, assertions, parameterization |
| Mockito 5 | Mocking framework — stubs, spies, verification |
| AssertJ | Fluent assertions (`assertThat(list).hasSize(3).contains("a")`) |
| Testcontainers | Real Docker containers in integration tests |
| Surefire plugin | Maven plugin that discovers and runs tests |
| Jacoco | Code coverage reporting |
| `@SpringBootTest` | Spring context integration test |

---

## 12. Interview Questions with Answers

**Q1: What is the difference between `@Mock` and `@Spy` in Mockito?**
`@Mock` creates a complete mock — all methods return defaults (null, 0, false, empty) unless explicitly stubbed. The real class is never called. `@Spy` wraps a real object — all methods execute their real implementation unless stubbed with `doReturn()`. Use `@Mock` when you want to fully control a collaborator and don't need its real behavior. Use `@Spy` when you want to call the real implementation but override specific methods or verify interactions. Rule: if you find yourself stubbing most methods of a spy, use a mock instead.

**Q2: How does `@InjectMocks` choose an injection strategy?**
Mockito tries three strategies in order: (1) Constructor injection — finds the largest constructor whose parameters can be satisfied by available mocks and spies; injects them. (2) Setter injection — calls setters that match mock types and names. (3) Field injection — sets fields directly via reflection that match mock types and names. If constructor injection succeeds, setters and fields are skipped. If none succeeds, an instance is created with the no-arg constructor and injection silently fails (fields remain null). This is a common source of NPEs — prefer constructor injection in production code so `@InjectMocks` always uses the explicit constructor.

**Q3: When should you NOT use mocks?**
Do not mock: (1) Value objects and simple data classes — use real instances, they have no side effects. (2) The class you're testing (don't mock the subject under test). (3) Standard JDK classes like `String`, `List`, `Optional` — they're simple and safe to use directly. (4) Collaborators where you'd end up mocking the majority of their behavior — that's a sign the design is too tightly coupled; use a fake instead. (5) Third-party APIs you don't own — you might mock them incorrectly; use integration tests or a test server.

**Q4: What does the test pyramid recommend?**
The test pyramid (Martin Fowler) recommends: large base of fast, isolated unit tests (~70%); smaller layer of integration tests that test interactions between components (~20%); tiny top of slow, comprehensive end-to-end tests (~10%). Unit tests are cheap to write, fast to run, and pinpoint failures. Integration tests verify wiring. E2E tests verify user journeys. The anti-pattern "ice cream cone" (mostly E2E) means slow CI, flaky tests, and poor feedback. The anti-pattern "testing trophy" adds a larger layer of integration tests vs unit tests — valid for highly integrated code.

**Q5: How do you test concurrent code without races in the tests themselves?**
Use `CountDownLatch` to synchronize thread start and completion: (1) Create a start latch initialized to 1; all worker threads call `start.await()` before doing work. (2) Create a done latch initialized to N (number of threads); each calls `done.countDown()` when finished. (3) Test thread calls `start.countDown()` to release all workers simultaneously, then `done.await(timeout)` to wait for completion. This maximizes contention (all threads start at the same time) and ensures assertions run only after all threads complete. Also assert with timeout to avoid hanging on deadlocks.

**Q6: What is the difference between a Stub and a Mock (Meszaros taxonomy)?**
Stub: an object that provides canned answers to calls made during the test. The test makes no assertions about how the stub was used. Example: `when(repo.findById(1L)).thenReturn(user)` — you only care that when asked, it returns the right thing. Mock: a pre-programmed object with expectations about which calls it will receive. The test fails if the mock wasn't called as expected. Example: `verify(emailService).send(...)` — you're asserting the mock was called. In Mockito, every mock can act as both stub and mock: stub with `when()`, verify with `verify()`. Best practice: stub for state verification (check the return value), verify for behavior verification (check interactions).

**Q7: How do you test time-dependent code?**
The key is to never call `Instant.now()`, `LocalDate.now()`, or `System.currentTimeMillis()` directly in production code. Instead, inject a `java.time.Clock` dependency. In production, pass `Clock.systemUTC()`. In tests, pass `Clock.fixed(someInstant, someZone)`. This makes tests fully deterministic and removes any real-time dependency. The `Clock.tick()` variant lets you simulate a clock that advances at a controlled rate. Alternative for legacy code: extract to a thin wrapper interface and mock that wrapper.

**Q8: What does `ArgumentCaptor` provide that `verify()` doesn't?**
`verify(mock).method(arg)` checks that a method was called with a specific argument — but `arg` must be a known value or an `ArgumentMatcher`. `ArgumentCaptor<T>.capture()` records the actual argument passed during the real call and makes it available after verification via `.getValue()` (or `.getAllValues()` for multiple calls). Use `ArgumentCaptor` when: the argument is a complex object created inside the method under test (you can't predict its exact instance), and you need to assert multiple fields of that object. Example: capturing an `EmailMessage` built inside `service.sendWelcome(userId)` to assert its subject, recipients, and attachments.

**Q9: What is `assertAll()` and when should you use it?**
`assertAll(heading, Executable... executables)` runs all the provided assertions even if some fail, then reports ALL failures at once. Regular `assertEquals()` stops at the first failure. Use `assertAll()` when a single test logically validates multiple aspects of one result — e.g., asserting multiple fields of a returned object. Without `assertAll()`, you'd need one test per field or accept that only the first failure is reported. Do not overuse: if the assertions are logically independent, they belong in separate tests. `assertAll()` is for "this single result must satisfy ALL these conditions simultaneously."

**Q10: How do you handle unchecked exceptions thrown inside `ExecutorService` tasks?**
When a `Runnable` submitted to an `ExecutorService` throws an unchecked exception, it is silently swallowed — the task dies, the exception is stored in the `Future`, and nothing else happens. To surface it: (1) Use `Future.get()` — it throws `ExecutionException` wrapping the original. (2) Set a `Thread.UncaughtExceptionHandler` on the `ThreadFactory`: `factory.newThread(r)` wraps `r` in a try-catch that logs/reports. (3) Use `CompletableFuture.runAsync()` — unhandled exceptions complete the CF exceptionally, accessible via `.exceptionally()` or `.handle()`. Always set an uncaught exception handler when using thread pools with `Runnable`s.

**Q11: What is `@Nested` and when is it useful?**
`@Nested` defines an inner class inside a test class, with its own lifecycle and grouping. It's useful for expressing hierarchical test structure: group tests by method being tested, or by pre-condition. Example: `UserServiceTest` has `@Nested class FindById` with `@Nested class WhenUserExists` and `@Nested class WhenUserDoesNotExist`. Each nested class can have its own `@BeforeEach` setup. The outer class's `@BeforeEach` runs first, then the nested class's — providing additive setup layers. This makes tests more readable and reflects the structure of the domain.

**Q12: What is `@RepeatedTest` useful for and how does it differ from `@ParameterizedTest`?**
`@RepeatedTest(n)` runs the same test method n times with no argument variation. Use it for: (1) Tests with random inputs (property-based testing style) where you want to exercise many random values. (2) Tests for eventually consistent behavior where you want to verify it passes consistently. (3) Performance tests or flakiness detection runs. `@ParameterizedTest` is for explicit different inputs — each invocation gets a distinct argument combination. `@RepeatedTest` is for running the same test repeatedly. Each repetition has a `RepetitionInfo` parameter you can inject to track which repetition is running.

---

## 13. Best Practices

1. **One logical assertion per test** — tests should fail for one reason; `assertAll()` is the exception for multi-field verification.
2. **Use constructor injection** in production code — makes `@InjectMocks` reliable and explicit.
3. **Name tests descriptively**: `methodName_given_when_should` format reads as a spec.
4. **Use `@DisplayName`** for human-readable test names in CI reports.
5. **Prefer `@MethodSource`** over `@CsvSource` for complex parameter objects — type-safe, refactor-friendly.
6. **Inject `Clock`** in any class that uses current time — never hardcode `Instant.now()`.
7. **Use `assertThrows()`** to test exception behavior — never rely on `@Test(expected=...)` (JUnit 4 style, too coarse).
8. **Use `ArgumentCaptor`** for complex objects instead of `eq()` matchers with manually constructed expected objects.
9. **Don't verify trivial interactions** — verifying a getter was called adds no value; test the behavior, not implementation.
10. **Run tests with mutation testing** (PIT) occasionally — catches tests that always pass regardless of code correctness.

---

## 14. Case Study

### Unit-Testing a PaymentService Against Three External Dependencies

**Scenario.** A `PaymentService` orchestrates three collaborators that must never run in a unit test: a `PaymentGateway` (external HTTP, costs real money), an `AuditRepository` (a database), and a `FraudDetector` (a remote ML service, ~200ms/call). The team enforces a **test pyramid of roughly 80% unit / 15% integration / 5% end-to-end**; this class is the unit tier, so all three collaborators are mocked with Mockito and the suite runs **~1,200 unit tests in under 8 seconds** on Java 17. The load-bearing assertion is an `ArgumentCaptor` check that the *exact* audit record (amount, status, fraud score) was written — a previous incident shipped a transposed amount/score with no test to catch it.

```
            +------------------- unit under test -------------------+
   test --> |  PaymentService.charge(req)                           |
            |     |-> fraudDetector.score(req)    [@Mock, stubbed]   |
            |     |-> gateway.charge(req)         [@Mock, stubbed]   |
            |     |-> auditRepo.save(record)      [@Mock, captured]  |
            +-------------------------------------------------------+
   pyramid:   unit 80% (here) | integration 15% | e2e 5%
```

### The Test Class

```java
@ExtendWith(MockitoExtension.class)           // JUnit 5 wiring; strict stubbing by default
class PaymentServiceTest {

    @Mock private PaymentGateway gateway;
    @Mock private AuditRepository auditRepo;
    @Mock private FraudDetector fraudDetector;
    @InjectMocks private PaymentService service;   // constructor-injects the 3 mocks

    @Captor private ArgumentCaptor<AuditRecord> auditCaptor;

    @Test
    @DisplayName("Approved charge writes an audit record with the exact amount and score")
    void charge_approved_writesExactAuditRecord() {
        // Arrange
        ChargeRequest req = new ChargeRequest("acct-1", 10_000L, "USD");
        when(fraudDetector.score(req)).thenReturn(0.12);          // below threshold
        when(gateway.charge(req)).thenReturn(Receipt.approved("txn-9"));

        // Act
        ChargeResult result = service.charge(req);

        // Assert outcome
        assertEquals(Status.APPROVED, result.status());

        // Assert the EXACT record written (load-bearing: catches transposed fields)
        verify(auditRepo).save(auditCaptor.capture());
        AuditRecord rec = auditCaptor.getValue();
        assertAll("audit record",
            () -> assertEquals(10_000L, rec.amountCents()),
            () -> assertEquals("txn-9", rec.transactionId()),
            () -> assertEquals(0.12, rec.fraudScore(), 1e-9),
            () -> assertEquals(Status.APPROVED, rec.status()));

        // Order: fraud check must precede the (costly) gateway call
        InOrder order = inOrder(fraudDetector, gateway);
        order.verify(fraudDetector).score(req);
        order.verify(gateway).charge(req);
    }

    @Test
    @DisplayName("High fraud score blocks the charge and never calls the gateway")
    void charge_highFraud_skipsGateway() {
        ChargeRequest req = new ChargeRequest("acct-2", 50_000L, "USD");
        when(fraudDetector.score(req)).thenReturn(0.97);          // above threshold

        ChargeResult result = service.charge(req);

        assertEquals(Status.BLOCKED, result.status());
        verify(gateway, never()).charge(any());                  // negative verification
        verify(auditRepo).save(any());                           // still audited
    }

    @ParameterizedTest
    @CsvSource({"0, USD", "-1, USD", "100, ''"})
    @DisplayName("Invalid requests fail fast before any collaborator is touched")
    void charge_invalidInput_failsFast(long cents, String currency) {
        ChargeRequest bad = new ChargeRequest("acct-3", cents, currency);
        assertThrows(IllegalArgumentException.class, () -> service.charge(bad));
        verifyNoInteractions(fraudDetector, gateway, auditRepo);
    }
}
```

### Common Pitfalls

**Mocking the class under test.** Spying or mocking `PaymentService` itself tests the mock's stubbed behavior, not the real logic.
```java
// BROKEN: this asserts nothing about real code
PaymentService svc = mock(PaymentService.class);
when(svc.charge(req)).thenReturn(approved);   // you stubbed the answer you assert
// FIX: instantiate the real service with mocked DEPENDENCIES (@InjectMocks above)
```

**`@InjectMocks` silently not injecting.** With constructor injection, if the constructor signature does not match the available mocks, Mockito quietly falls back to field/setter injection and may leave a dependency `null`, hiding a wiring bug until an NPE at runtime.
```java
// FIX: prefer explicit constructor wiring in the test so a mismatch fails loudly
service = new PaymentService(gateway, auditRepo, fraudDetector);
```

**`verify()` without `times()` means exactly 1, not "at least once".** `verify(gateway).charge(req)` fails if the gateway was called twice. If a retry can legitimately call it more than once, state it: `verify(gateway, atLeastOnce()).charge(req)` or `verify(gateway, times(2)).charge(req)`.

**Mocking a `final` class or method needs `mockito-inline`.** Plain Mockito cannot subclass a `final` class, so `mock(FinalGateway.class)` throws. Enable the inline mock maker (`mockito-inline` artifact, or the `org.mockito.plugins.MockMaker` resource set to `mock-maker-inline`) — but prefer extracting an interface so you mock an abstraction instead.

### Interview Discussion Points

**Why mock the gateway, repository, and fraud detector instead of using real ones?** A unit test must be fast, deterministic, and free of side effects; the real gateway charges money, the repository needs a database, and the fraud service adds 200ms and network flakiness — mocking isolates `PaymentService` logic so a failure points at that class, not at infrastructure.

**When do you use `ArgumentCaptor` versus argument matchers in `verify()`?** Use matchers (`eq`, `any`) when you only need to assert the call happened with values matching a predicate; use a captor when you must inspect a complex object's internal fields after the fact — as with the audit record, where the bug was a transposed field that a simple `eq` would not have surfaced.

**What is strict stubbing and why does `MockitoExtension` enable it?** Strict stubbing fails the test if a `when(...).thenReturn(...)` is never used (`UnnecessaryStubbingException`), catching dead setup and tests that drifted from the code they exercise; it pushes you toward stubbing only what the path under test actually calls.

**How do you verify an ordering constraint between mocks?** Use `InOrder` over the relevant mocks and call `verify` on it in sequence; this proved the fraud check runs before the costly gateway call, a business rule that a set of unordered `verify` calls would not enforce.

**Why does the test pyramid put 80% of tests at the unit level?** Unit tests are the cheapest to run and the most precise at localizing failures; integration and e2e tests are slower and flakier, so you keep them few and reserve them for wiring and contract coverage that mocks cannot prove — inverting the ratio (the "ice-cream cone") yields slow, brittle suites.
